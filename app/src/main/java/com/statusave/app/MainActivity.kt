package com.statusave.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF00A884), secondary = Color(0xFF25D366))
    } else {
        lightColorScheme(primary = Color(0xFF008069), secondary = Color(0xFF25D366))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showDestDialog by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<StatusItem?>(null) }
    var pendingSave by remember { mutableStateOf<StatusItem?>(null) }
    var toDelete by remember { mutableStateOf<StatusItem?>(null) }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }

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
            pendingSave?.let { vm.save(it) }
        }
        pendingSave = null
    }

    val trySave: (StatusItem) -> Unit = { item ->
        if (state.destConfigured) {
            vm.save(item)
        } else {
            pendingSave = item
            showDestDialog = true
        }
    }

    // Recargar al volver a la app (p. ej. después de ver estados en WhatsApp).
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
            when (tab) {
                0 -> StatusesTab(
                    state = state,
                    imageLoader = imageLoader,
                    onGrantWhatsApp = {
                        statusesPicker.launch(StatusRepository.buildInitialUri(business = false))
                    },
                    onGrantBusiness = {
                        statusesPicker.launch(StatusRepository.buildInitialUri(business = true))
                    },
                    onPreview = { preview = it },
                    onSave = trySave,
                )

                1 -> SavedTab(
                    state = state,
                    imageLoader = imageLoader,
                    onPreview = { preview = it },
                    onDelete = { toDelete = it },
                )
            }
        }
    }

    if (showDestDialog) {
        AlertDialog(
            onDismissRequest = { showDestDialog = false; pendingSave = null },
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
                TextButton(onClick = { showDestDialog = false; pendingSave = null }) {
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

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete file") },
            text = { Text("Delete \"${item.name}\" from the saved folder?") },
            confirmButton = {
                TextButton(onClick = { vm.delete(item); toDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel") }
            },
        )
    }

    preview?.let { item ->
        PreviewDialog(
            item = item,
            imageLoader = imageLoader,
            onDismiss = { preview = null },
            onSave = if (!item.isSaved && tab == 0) {
                { toSave -> trySave(toSave); preview = null }
            } else null,
        )
    }
}

@Composable
private fun StatusesTab(
    state: UiState,
    imageLoader: ImageLoader,
    onGrantWhatsApp: () -> Unit,
    onGrantBusiness: () -> Unit,
    onPreview: (StatusItem) -> Unit,
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

        state.loading && state.statuses.isEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        state.statuses.isEmpty() -> Box(
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
            items = state.statuses,
            imageLoader = imageLoader,
            onPreview = onPreview,
            onAction = onSave,
            actionIsDelete = false,
        )
    }
}

@Composable
private fun SavedTab(
    state: UiState,
    imageLoader: ImageLoader,
    onPreview: (StatusItem) -> Unit,
    onDelete: (StatusItem) -> Unit,
) {
    if (state.saved.isEmpty()) {
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
            items = state.saved,
            imageLoader = imageLoader,
            onPreview = onPreview,
            onAction = onDelete,
            actionIsDelete = true,
        )
    }
}

@Composable
private fun MediaGrid(
    items: List<StatusItem>,
    imageLoader: ImageLoader,
    onPreview: (StatusItem) -> Unit,
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
        items(items, key = { it.uri.toString() }) { item ->
            MediaCell(
                item = item,
                imageLoader = imageLoader,
                onClick = { onPreview(item) },
                onAction = { onAction(item) },
                actionIsDelete = actionIsDelete,
            )
        }
    }
}

@Composable
private fun MediaCell(
    item: StatusItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onAction: () -> Unit,
    actionIsDelete: Boolean,
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
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

@Composable
private fun SettingsDialog(
    state: UiState,
    onDismiss: () -> Unit,
    onChangeDest: () -> Unit,
    onChangeStatuses: () -> Unit,
    onFolderName: (String) -> Unit,
) {
    var folderName by remember(state.folderName) { mutableStateOf(state.folderName) }

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
private fun PreviewDialog(
    item: StatusItem,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onSave: ((StatusItem) -> Unit)?,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (item.isVideo) {
                VideoPlayer(uri = item.uri, modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = item.uri,
                    imageLoader = imageLoader,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding(),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            if (onSave != null) {
                FloatingActionButton(
                    onClick = { onSave(item) },
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
