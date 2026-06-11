package com.statusave.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val hasStatusAccess: Boolean = false,
    val statuses: List<StatusItem> = emptyList(),
    val saved: List<StatusItem> = emptyList(),
    val loading: Boolean = false,
    val destConfigured: Boolean = false,
    val destDisplay: String = "",
    val folderName: String = "StatuSave",
    val message: String? = null,
    val update: UpdateInfo? = null,
    /** null = no download in progress; 0f..1f = downloading. */
    val downloadProgress: Float? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
        checkForUpdates()
    }

    private fun context(): Application = getApplication()

    private fun hasPersistedPermission(uriString: String?, write: Boolean): Boolean {
        uriString ?: return false
        val uri = Uri.parse(uriString)
        return context().contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && (!write || it.isWritePermission)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val ctx = context()
            val statusesUri = prefs.statusesTreeUri
                ?.takeIf { hasPersistedPermission(it, write = false) }
                ?.let(Uri::parse)
            val destUri = prefs.destTreeUri
                ?.takeIf { hasPersistedPermission(it, write = true) }
                ?.let(Uri::parse)
            val folderName = prefs.folderName

            val (statuses, saved) = withContext(Dispatchers.IO) {
                val savedList: List<StatusItem> = destUri?.let { base ->
                    runCatching {
                        StatusRepository.findOrCreateDestDir(ctx, base, folderName)
                            ?.let { StatusRepository.listSaved(ctx, it) }
                    }.getOrNull()
                } ?: emptyList()

                // Los estados ya guardados solo se muestran en la pestaña Saved.
                val savedNames = savedList.map { it.name }.toSet()
                val statusList: List<StatusItem> = statusesUri?.let { tree ->
                    runCatching { StatusRepository.listStatuses(ctx, tree) }.getOrNull()
                }?.filter { it.name !in savedNames } ?: emptyList()

                statusList to savedList
            }

            _state.update {
                it.copy(
                    loading = false,
                    hasStatusAccess = statusesUri != null,
                    statuses = statuses,
                    saved = saved,
                    destConfigured = destUri != null,
                    destDisplay = destUri?.let { uri -> readableTreePath(uri, folderName) } ?: "",
                    folderName = folderName,
                )
            }
        }
    }

    private fun readableTreePath(treeUri: Uri, folderName: String): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return treeUri.toString()
        val path = docId.substringAfter(':', missingDelimiterValue = "").ifBlank { "Storage" }
        return if (folderName.isBlank()) path else "$path/$folderName"
    }

    fun onStatusesTreePicked(uri: Uri) {
        runCatching {
            context().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        prefs.statusesTreeUri = uri.toString()
        refresh()
    }

    fun onDestTreePicked(uri: Uri) {
        runCatching {
            context().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.destTreeUri = uri.toString()
        refresh()
    }

    fun setFolderName(name: String) {
        prefs.folderName = name
        refresh()
    }

    fun saveMany(items: List<StatusItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val ctx = context()
            val destUriStr = prefs.destTreeUri
            if (destUriStr == null || !hasPersistedPermission(destUriStr, write = true)) {
                _state.update { it.copy(message = "Choose a destination folder first") }
                return@launch
            }
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val destDir = StatusRepository.findOrCreateDestDir(
                        ctx, Uri.parse(destUriStr), prefs.folderName,
                    ) ?: return@runCatching 0
                    items.count { StatusRepository.saveStatus(ctx, it, destDir) }
                }.getOrDefault(0)
            }
            val message = when {
                saved == 0 -> "Could not save"
                items.size == 1 -> "Status saved"
                else -> "$saved of ${items.size} statuses saved"
            }
            _state.update { it.copy(message = message) }
            refresh()
        }
    }

    fun deleteMany(items: List<StatusItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                items.count { StatusRepository.deleteDocument(context(), it.uri) }
            }
            val message = when {
                deleted == 0 -> "Could not delete"
                items.size == 1 -> "File deleted"
                else -> "$deleted files deleted"
            }
            _state.update { it.copy(message = message) }
            refresh()
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            val latest = UpdateChecker.fetchLatest() ?: return@launch
            if (UpdateChecker.isNewer(latest.versionName, BuildConfig.VERSION_NAME)) {
                _state.update { it.copy(update = latest) }
            }
        }
    }

    fun dismissUpdate() {
        _state.update { it.copy(update = null) }
    }

    fun downloadAndInstallUpdate() {
        val update = _state.value.update ?: return
        viewModelScope.launch {
            _state.update { it.copy(downloadProgress = 0f) }
            var lastPercent = -1
            val file = UpdateChecker.downloadApk(context(), update.apkUrl) { progress ->
                val percent = (progress * 100).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    _state.update { it.copy(downloadProgress = progress) }
                }
            }
            _state.update { it.copy(downloadProgress = null) }
            if (file != null) {
                UpdateChecker.installApk(context(), file)
                _state.update { it.copy(update = null) }
            } else {
                _state.update { it.copy(message = "Could not download the update") }
            }
        }
    }
}
