package com.droidtorrent.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TorrentsScreen(vm: MainViewModel, onSettings: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var toRemove by remember { mutableStateOf<TorrentItem?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { vm.query.value = it },
                            singleLine = true,
                            placeholder = { Text("Search") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("DroidTorrent")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) vm.query.value = ""
                    }) {
                        Icon(if (searching) Icons.Default.Close else Icons.Default.Search, "Search")
                    }
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortBy.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                onClick = { vm.sort.value = option; sortMenu = false },
                            )
                        }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Add torrent") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { torrent ->
                TorrentRow(
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

@Composable
private fun TorrentRow(t: TorrentItem, onClick: () -> Unit, onToggle: () -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        if (t.status == TorrentStatus.Stopped) Icons.Default.PlayArrow else Icons.Default.Pause,
                        "Start or pause",
                    )
                }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove") }
            }
            LinearProgressIndicator(
                progress = { t.progress },
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            )
            Text(
                buildString {
                    append("%.1f%%".format(t.progress * 100))
                    if (t.size > 0) append(" of ${formatBytes(t.size)}")
                    append("  ${t.status.name}")
                    if (t.status == TorrentStatus.Downloading) {
                        append("  ${formatSpeed(t.rateDown)}  ETA ${formatEta(t.etaSeconds)}")
                    } else if (t.status == TorrentStatus.Seeding) {
                        append("  up ${formatSpeed(t.rateUp)}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (t.error.isNotEmpty()) {
                Text(t.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AddDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var link by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { vm.addFile(it.readBytes()) }
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add torrent") },
        text = {
            Column {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Magnet link") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Choose .torrent file") }
            }
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
