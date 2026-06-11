package com.statusave.app

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

data class StatusItem(
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val lastModified: Long,
    val isSaved: Boolean = false,
)

/**
 * Acceso a los estados de WhatsApp y a la carpeta destino mediante Storage Access Framework.
 *
 * WhatsApp guarda los estados ya vistos en:
 *  - Android/media/com.whatsapp/WhatsApp/Media/.Statuses          (WhatsApp normal, versiones modernas)
 *  - Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses (WhatsApp Business)
 * En Android 11+ esa ruta solo es accesible si el usuario la concede con el selector de SAF
 * (ACTION_OPEN_DOCUMENT_TREE); el permiso se persiste con takePersistableUriPermission.
 */
object StatusRepository {

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val WHATSAPP_PATH = "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
    private const val WHATSAPP_BUSINESS_PATH = "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"

    /** URI inicial para que el selector de carpetas abra directamente en .Statuses. */
    fun buildInitialUri(business: Boolean): Uri {
        val path = if (business) WHATSAPP_BUSINESS_PATH else WHATSAPP_PATH
        return DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:$path")
    }

    private data class Child(
        val docId: String,
        val name: String,
        val mime: String,
        val lastModified: Long,
    )

    private fun listChildren(resolver: ContentResolver, anyTreeUri: Uri, parentDocId: String): List<Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(anyTreeUri, parentDocId)
        val result = mutableListOf<Child>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    Child(
                        docId = cursor.getString(0),
                        name = cursor.getString(1) ?: "",
                        mime = cursor.getString(2) ?: "",
                        lastModified = cursor.getLong(3),
                    )
                )
            }
        }
        return result
    }

    private fun toMediaItems(anyTreeUri: Uri, children: List<Child>): List<StatusItem> =
        children
            .filter { it.mime.startsWith("image/") || it.mime.startsWith("video/") }
            .filter { !it.name.startsWith(".") }
            .map {
                StatusItem(
                    uri = DocumentsContract.buildDocumentUriUsingTree(anyTreeUri, it.docId),
                    name = it.name,
                    mime = it.mime,
                    isVideo = it.mime.startsWith("video/"),
                    lastModified = it.lastModified,
                )
            }
            .sortedByDescending { it.lastModified }

    /** Lista los estados (fotos y videos) de la carpeta .Statuses concedida. */
    fun listStatuses(context: Context, treeUri: Uri): List<StatusItem> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        return toMediaItems(treeUri, listChildren(context.contentResolver, treeUri, rootId))
    }

    /**
     * Devuelve el URI de la carpeta destino final. Si [folderName] no está vacío busca esa
     * subcarpeta dentro de la base elegida por el usuario y, si no existe, la crea.
     */
    fun findOrCreateDestDir(context: Context, baseTreeUri: Uri, folderName: String): Uri? {
        val resolver = context.contentResolver
        val baseDocId = DocumentsContract.getTreeDocumentId(baseTreeUri)
        if (folderName.isBlank()) {
            return DocumentsContract.buildDocumentUriUsingTree(baseTreeUri, baseDocId)
        }
        val existing = listChildren(resolver, baseTreeUri, baseDocId)
            .firstOrNull { it.name == folderName && it.mime == DocumentsContract.Document.MIME_TYPE_DIR }
        if (existing != null) {
            return DocumentsContract.buildDocumentUriUsingTree(baseTreeUri, existing.docId)
        }
        val baseDocUri = DocumentsContract.buildDocumentUriUsingTree(baseTreeUri, baseDocId)
        return DocumentsContract.createDocument(
            resolver, baseDocUri, DocumentsContract.Document.MIME_TYPE_DIR, folderName,
        )
    }

    /** Lista los archivos ya guardados en la carpeta destino. */
    fun listSaved(context: Context, destDirUri: Uri): List<StatusItem> {
        val docId = DocumentsContract.getDocumentId(destDirUri)
        return toMediaItems(destDirUri, listChildren(context.contentResolver, destDirUri, docId))
            .map { it.copy(isSaved = true) }
    }

    /** Copia un estado a la carpeta destino. Devuelve true si se guardó o ya existía. */
    fun saveStatus(context: Context, item: StatusItem, destDirUri: Uri): Boolean {
        val resolver = context.contentResolver
        val destDocId = DocumentsContract.getDocumentId(destDirUri)
        val alreadyExists = listChildren(resolver, destDirUri, destDocId).any { it.name == item.name }
        if (alreadyExists) return true

        val newDoc = DocumentsContract.createDocument(resolver, destDirUri, item.mime, item.name)
            ?: return false
        val input = resolver.openInputStream(item.uri) ?: return false
        input.use { inStream ->
            val output = resolver.openOutputStream(newDoc) ?: return false
            output.use { outStream -> inStream.copyTo(outStream) }
        }
        return true
    }

    /** Elimina un archivo guardado. */
    fun deleteDocument(context: Context, uri: Uri): Boolean =
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {
            false
        }
}
