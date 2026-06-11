package com.statusave.app

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            StatuSaveTheme {
                App()
            }
        }
    }
}

@Composable
fun StatuSaveTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF00A884), secondary = Color(0xFF25D366))
        else -> lightColorScheme(primary = Color(0xFF008069), secondary = Color(0xFF25D366))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private data class PreviewRequest(val items: List<StatusItem>, val index: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 = all, 1 = photos, 2 = videos
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showSettings by remember { mutableStateOf(false) }
    var showDestDialog by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PreviewRequest?>(null) }
    var pendingSave by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    var toDelete by remember { mutableStateOf<List<StatusItem>>(emptyList()) }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }

    val currentItems = remember(state.statuses, state.saved, tab, filter) {
        val base = if (tab == 0) state.statuses else state.saved
        when (filter) {
            1 -> base.filter { !it.isVideo }
            2 -> base.filter { it.isVideo }
            else -> base
        }
    }
    val selectionMode = selected.isNotEmpty()
    val selectedItems = currentItems.filter { it.uri.toString() in selected }

    LaunchedEffect(tab) { selected = emptySet() }

    val statusesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) vm.onStatusesTreePicked(uri)
    }

    val destPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            vm.onDestTreePicked(uri)
            if (pendingSave.isNotEmpty()) vm.saveMany(pendingSave)
        }
        pendingSave = emptyList()
    }

    val trySaveMany: (List<StatusItem>) -> Unit = { items ->
        if (items.isNotEmpty()) {
            if (state.destConfigured) {
                vm.saveMany(items)
            } else {
                pendingSave = items
                showDestDialog = true
            }
            selected = emptySet()
        }
    }

    val toggleSelect: (StatusItem) -> Unit = { item ->
        val key = item.uri.toString()
        selected = if (key in selected) selected - key else selected + key
    }

    // Reload when coming back to the app (e.g. after viewing statuses in WhatsApp).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { selected = currentItems.map { it.uri.toString() }.toSet() }
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                        if (tab == 0) {
                            IconButton(onClick = { trySaveMany(selectedItems) }) {
                                Icon(Icons.Default.Download, contentDescription = "Save selected")
                            }
                        } else {
                            IconButton(onClick = { toDelete = selectedItems }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("StatuSave") },
                    actions = {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Statuses") })
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Saved (${state.saved.size})") },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("All") })
                FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("Photos") })
                FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("Videos") })
            }
            when (tab) {
                0 -> StatusesTab(
                    state = state,
                    items = currentItems,
                    imageLoader = imageLoader,
                    selectedKeys = selected,
                    selectionMode = selectionMode,
                    onGrantWhatsApp = {
                        statusesPicker.launch(StatusRepository.buildInitialUri(business = false))
                    },
                    onGrantBusiness = {
                        statusesPicker.launch(StatusRepository.buildInitialUri(business = true))
                    },
                    onPreview = { index -> preview = PreviewRequest(currentItems, index) },
                    onToggleSelect = toggleSelect,
                    onSave = { trySaveMany(listOf(it)) },
                )

                1 -> SavedTab(
                    state = state,
                    items = currentItems,
                    imageLoader = imageLoader,
                    selectedKeys = selected,
                    selectionMode = selectionMode,
                    onPreview = { index -> preview = PreviewRequest(currentItems, index) },
                    onToggleSelect = toggleSelect,
                    onDelete = { toDelete = listOf(it) },
                )
            }
        }
    }

    if (showDestDialog) {
        AlertDialog(
            onDismissRequest = { showDestDialog = false; pendingSave = emptyList() },
            title = { Text("Destination folder") },
            text = {
                Text(
                    "Choose the folder where statuses will be saved. " +
                        if (state.folderName.isBlank()) ""
                        else "The \"${state.folderName}\" folder will be created inside it if it doesn't exist."
                )
            },
            confirmButton = {
                TextButton(onClick = { showDestDialog = false; destPicker.launch(null) }) {
                    Text("Choose folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDestDialog = false; pendingSave = emptyList() }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onChangeDest = { destPicker.launch(null) },
            onChangeStatuses = {
                statusesPicker.launch(StatusRepository.buildInitialUri(business = false))
            },
            onFolderName = { vm.setFolderName(it) },
        )
    }

    state.update?.let { update ->
        val progress = state.downloadProgress
        AlertDialog(
            onDismissRequest = { if (progress == null) vm.dismissUpdate() },
            title = { Text("Update available") },
            text = {
                if (progress != null) {
                    Column {
                        Text("Downloading StatuSave ${update.versionName}… ${(progress * 100).toInt()}%")
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Column(
                        Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "StatuSave ${update.versionName} is available " +
                                "(you have ${BuildConfig.VERSION_NAME})."
                        )
                        if (update.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("What's new:", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(update.notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = progress == null,
                    onClick = { vm.downloadAndInstallUpdate() },
                ) { Text("Update") }
            },
            dismissButton = {
                TextButton(
                    enabled = progress == null,
                    onClick = { vm.dismissUpdate() },
                ) { Text("Later") }
            },
        )
    }

    if (toDelete.isNotEmpty()) {
        val items = toDelete
        AlertDialog(
            onDismissRequest = { toDelete = emptyList() },
            title = { Text("Delete") },
            text = {
                Text(
                    if (items.size == 1) "Delete \"${items.first().name}\" from the saved folder?"
                    else "Delete ${items.size} files from the saved folder?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteMany(items)
                        toDelete = emptyList()
                        selected = emptySet()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = emptyList() }) { Text("Cancel") }
            },
        )
    }

    preview?.let { request ->
        PreviewPager(
            items = request.items,
            startIndex = request.index,
            imageLoader = imageLoader,
            onDismiss = { preview = null },
            onSave = if (tab == 0) {
                { item -> trySaveMany(listOf(item)); preview = null }
            } else null,
        )
    }
}

@Composable
private fun StatusesTab(
    state: UiState,
    items: List<StatusItem>,
    imageLoader: ImageLoader,
    selectedKeys: Set<String>,
    selectionMode: Boolean,
    onGrantWhatsApp: () -> Unit,
    onGrantBusiness: () -> Unit,
    onPreview: (Int) -> Unit,
    onToggleSelect: (StatusItem) -> Unit,
    onSave: (StatusItem) -> Unit,
) {
    when {
        !state.hasStatusAccess -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "To see statuses, grant access to WhatsApp's .Statuses folder.\n\n" +
                    "The system file picker will open directly at the folder: " +
                    "just tap \"Use this folder\" and then \"Allow\".",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onGrantWhatsApp) { Text("Grant access (WhatsApp)") }
            TextButton(onClick = onGrantBusiness) { Text("I use WhatsApp Business") }
        }

        state.loading && items.isEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        items.isEmpty() -> Box(
            Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No statuses available.\n\nOpen WhatsApp and view some statuses, " +
                    "then come back here and tap refresh.",
                textAlign = TextAlign.Center,
            )
        }

        else -> MediaGrid(
            items = items,
            imageLoader = imageLoader,
            selectedKeys = selectedKeys,
            selectionMode = selectionMode,
            onPreview = onPreview,
            onToggleSelect = onToggleSelect,
            onAction = onSave,
            actionIsDelete = false,
        )
    }
}

@Composable
private fun SavedTab(
    state: UiState,
    items: List<StatusItem>,
    imageLoader: ImageLoader,
    selectedKeys: Set<String>,
    selectionMode: Boolean,
    onPreview: (Int) -> Unit,
    onToggleSelect: (StatusItem) -> Unit,
    onDelete: (StatusItem) -> Unit,
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "You haven't saved any status yet.\n\n" +
                    if (state.destConfigured) "Destination folder: ${state.destDisplay}"
                    else "You'll choose the destination folder when saving your first status.",
                textAlign = TextAlign.Center,
            )
        }
    } else {
        MediaGrid(
            items = items,
            imageLoader = imageLoader,
            selectedKeys = selectedKeys,
            selectionMode = selectionMode,
            onPreview = onPreview,
            onToggleSelect = onToggleSelect,
            onAction = onDelete,
            actionIsDelete = true,
        )
    }
}

@Composable
private fun MediaGrid(
    items: List<StatusItem>,
    imageLoader: ImageLoader,
    selectedKeys: Set<String>,
    selectionMode: Boolean,
    onPreview: (Int) -> Unit,
    onToggleSelect: (StatusItem) -> Unit,
    onAction: (StatusItem) -> Unit,
    actionIsDelete: Boolean,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, item -> item.uri.toString() }) { index, item ->
            MediaCell(
                item = item,
                imageLoader = imageLoader,
                selected = item.uri.toString() in selectedKeys,
                selectionMode = selectionMode,
                onClick = { if (selectionMode) onToggleSelect(item) else onPreview(index) },
                onLongClick = { onToggleSelect(item) },
                onAction = { onAction(item) },
                actionIsDelete = actionIsDelete,
            )
        }
    }
}

/** Cache so video durations are only extracted once per file. */
private val durationCache = ConcurrentHashMap<String, String>()

@Composable
private fun rememberVideoDuration(item: StatusItem): String? {
    if (!item.isVideo) return null
    val context = LocalContext.current
    var duration by remember(item.uri) { mutableStateOf(durationCache[item.uri.toString()]) }
    LaunchedEffect(item.uri) {
        if (duration == null) {
            val computed = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, item.uri)
                        val ms = retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: 0L
                        val totalSeconds = ms / 1000
                        "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
                    } finally {
                        retriever.release()
                    }
                }.getOrNull()
            }
            if (computed != null) {
                durationCache[item.uri.toString()] = computed
                duration = computed
            }
        }
    }
    return duration
}

private fun formatItemTime(millis: Long): String {
    if (millis <= 0) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "dd MMM"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

@Composable
private fun OverlayChip(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 10.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCell(
    item: StatusItem,
    imageLoader: ImageLoader,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: () -> Unit,
    actionIsDelete: Boolean,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            ),
    ) {
        AsyncImage(
            model = item.uri,
            imageLoader = imageLoader,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "Video",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val time = remember(item.lastModified) { formatItemTime(item.lastModified) }
            if (time.isNotEmpty()) OverlayChip(time)
            rememberVideoDuration(item)?.let { OverlayChip(it) }
        }
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            )
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp),
            )
        }
        if (!selectionMode) {
            val (icon, description, color) = if (actionIsDelete) {
                Triple(Icons.Default.Delete, "Delete", MaterialTheme.colorScheme.errorContainer)
            } else {
                Triple(Icons.Default.Download, "Save", MaterialTheme.colorScheme.primaryContainer)
            }
            FilledIconButton(
                onClick = onAction,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(34.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = color),
            ) {
                Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    state: UiState,
    onDismiss: () -> Unit,
    onChangeDest: () -> Unit,
    onChangeStatuses: () -> Unit,
    onFolderName: (String) -> Unit,
) {
    var folderName by remember(state.folderName) { mutableStateOf(state.folderName) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Text(
                    if (state.destConfigured) "Destination folder: ${state.destDisplay}"
                    else "Destination folder: not set",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onDismiss(); onChangeDest() }) {
                    Text("Change destination folder")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Subfolder to create (empty = none)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { onDismiss(); onChangeStatuses() }) {
                    Text("Change WhatsApp statuses folder")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "StatuSave v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/Nerodacles/statusave/releases/latest"),
                                )
                            )
                        }
                        .padding(vertical = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onFolderName(folderName); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun PreviewPager(
    items: List<StatusItem>,
    startIndex: Int,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onSave: ((StatusItem) -> Unit)?,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val item = items[page]
                if (item.isVideo && pagerState.settledPage == page) {
                    VideoPlayer(uri = item.uri, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = item.uri,
                            imageLoader = imageLoader,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (item.isVideo) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = "Video",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.align(Alignment.Center).size(56.dp),
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding(),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            Text(
                "${pagerState.currentPage + 1} / ${items.size}",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp),
            )
            if (onSave != null) {
                FloatingActionButton(
                    onClick = { onSave(items[pagerState.currentPage]) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Save")
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
        update = { it.player = player },
        modifier = modifier,
    )
}
