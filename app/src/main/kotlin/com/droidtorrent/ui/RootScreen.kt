package com.droidtorrent.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidtorrent.MainViewModel
import com.droidtorrent.SortBy
import com.droidtorrent.TorrentItem
import com.droidtorrent.TorrentStatus
import com.droidtorrent.formatSpeed

@Composable
fun RootScreen(vm: MainViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(onBack = { showSettings = false })
    } else {
        TorrentsScreen(vm, onSettings = { showSettings = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TorrentsScreen(vm: MainViewModel, onSettings: () -> Unit) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var searching by remember { mutableStateOf(false) }
    var fabOpen by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var toRemove by remember { mutableStateOf<TorrentItem?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) context.contentResolver.openInputStream(uri)?.use { vm.addFile(it.readBytes()) }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }

    val active = items.count { it.status == TorrentStatus.Downloading }
    val summary = if (active > 0) {
        "$active downloading - ${formatSpeed(items.sumOf { it.rateDown })}"
    } else {
        "${items.size} torrents"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("DroidTorrent") },
                subtitle = { Text(summary) },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) vm.query.value = ""
                    }) {
                        Icon(if (searching) Icons.Default.Close else Icons.Default.Search, "Search")
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabOpen,
                button = {
                    ToggleFloatingActionButton(checked = fabOpen, onCheckedChange = { fabOpen = it }) {
                        Icon(if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add, "Add torrent")
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = { fabOpen = false; showAdd = true },
                    text = { Text("Magnet link") },
                    icon = { Icon(Icons.Default.Link, null) },
                )
                FloatingActionButtonMenuItem(
                    onClick = { fabOpen = false; picker.launch(arrayOf("*/*")) },
                    text = { Text(".torrent file") },
                    icon = { Icon(Icons.Default.FileOpen, null) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (searching) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        singleLine = true,
                        shape = CircleShape,
                        placeholder = { Text("Search torrents") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val options = SortBy.entries
                    options.forEachIndexed { i, option ->
                        ToggleButton(
                            checked = sort == option,
                            onCheckedChange = { vm.sort.value = option },
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) { Text(option.name) }
                    }
                }
            }
            items(items, key = { it.id }) { torrent ->
                TorrentCard(
                    torrent,
                    onClick = { selectedId = torrent.id },
                    onToggle = {
                        if (torrent.status == TorrentStatus.Stopped) vm.start(torrent.id) else vm.stop(torrent.id)
                    },
                    onRemove = { toRemove = torrent },
                )
            }
        }
    }

    if (showAdd) AddDialog(vm, onDismiss = { showAdd = false })

    toRemove?.let { t ->
        var withData by remember(t.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remove torrent") },
            text = {
                Column {
                    Text(t.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { withData = !withData },
                    ) {
                        Checkbox(checked = withData, onCheckedChange = { withData = it })
                        Text("Delete downloaded files")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.remove(t.id, withData); toRemove = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancel") } },
        )
    }

    items.firstOrNull { it.id == selectedId }?.let { t ->
        DetailsSheet(vm, t, onDismiss = { selectedId = null })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TorrentCard(t: TorrentItem, onClick: () -> Unit, onToggle: () -> Unit, onRemove: () -> Unit) {
    val downloading = t.status == TorrentStatus.Downloading
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (downloading) scheme.primaryContainer else scheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
        ) {
            if (t.status == TorrentStatus.Checking) {
                LoadingIndicator(Modifier.size(48.dp))
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(if (downloading) scheme.primary else scheme.secondaryContainer),
                ) {
                    Icon(
                        when (t.status) {
                            TorrentStatus.Downloading -> Icons.Default.ArrowDownward
                            TorrentStatus.Seeding -> Icons.Default.ArrowUpward
                            else -> Icons.Default.Pause
                        },
                        contentDescription = t.status.name,
                        tint = if (downloading) scheme.onPrimary else scheme.onSecondaryContainer,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    t.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (downloading) {
                    LinearWavyProgressIndicator(
                        progress = { t.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { t.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                Text(
                    buildString {
                        append("%.1f%%".format(t.progress * 100))
                        if (t.size > 0) append(" of ${formatBytes(t.size)}")
                        when (t.status) {
                            TorrentStatus.Downloading ->
                                append(" - ${formatSpeed(t.rateDown)} - ${formatEta(t.etaSeconds)}")
                            TorrentStatus.Seeding -> append(" - up ${formatSpeed(t.rateUp)}")
                            else -> append(" - ${t.status.name}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (t.error.isNotEmpty()) {
                    Text(t.error, color = scheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalIconButton(onClick = onToggle) {
                    Icon(
                        if (t.status == TorrentStatus.Stopped) Icons.Default.PlayArrow else Icons.Default.Pause,
                        "Start or pause",
                    )
                }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove") }
            }
        }
    }
}

@Composable
private fun AddDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add magnet link") },
        text = {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("Magnet link") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = link.isNotBlank(),
                onClick = { vm.addMagnet(link); onDismiss() },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
