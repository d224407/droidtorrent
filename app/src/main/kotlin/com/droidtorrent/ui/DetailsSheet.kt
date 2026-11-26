package com.droidtorrent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droidtorrent.MainViewModel
import com.droidtorrent.TorrentEngine
import com.droidtorrent.TorrentFile
import com.droidtorrent.TorrentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailsSheet(vm: MainViewModel, t: TorrentItem, onDismiss: () -> Unit) {
    val files by produceState(emptyList<TorrentFile>(), t.id) {
        while (true) {
            value = withContext(Dispatchers.IO) { TorrentEngine.files(t.id) }
            delay(2000)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(t.name, style = MaterialTheme.typography.headlineSmallEmphasized) }
            item {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Info("Size", formatBytes(t.size))
                        Info("Downloaded", formatBytes(t.downloaded))
                        Info("Uploaded", formatBytes(t.uploaded))
                        Info("Peers", t.peers.toString())
                        Info("Location", t.location)
                        Info("Pieces", "${t.pieceCount} x ${formatBytes(t.pieceSize.toLong())}")
                        if (t.creator.isNotEmpty()) Info("Creator", t.creator)
                        if (t.comment.isNotEmpty()) Info("Comment", t.comment)
                        Info("Private", if (t.isPrivate) "Yes" else "No")
                    }
                }
            }
            item { Text("Files", style = MaterialTheme.typography.titleMediumEmphasized) }
            items(files, key = { it.index }) { f ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                        Checkbox(checked = f.wanted, onCheckedChange = { vm.setWanted(t.id, listOf(f.index), it) })
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val progress = if (f.length > 0) f.completed.toFloat() / f.length else 1f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            )
                            Text(
                                "${formatBytes(f.length)} - %.0f%%".format(progress * 100),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item { Column(Modifier.padding(bottom = 24.dp)) {} }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
