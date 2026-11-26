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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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

@OptIn(ExperimentalMaterial3Api::class)
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Text(t.name, style = MaterialTheme.typography.titleMedium) }
            item { Info("Size", formatBytes(t.size)) }
            item { Info("Downloaded", formatBytes(t.downloaded)) }
            item { Info("Uploaded", formatBytes(t.uploaded)) }
            item { Info("Peers", t.peers.toString()) }
            item { Info("Location", t.location) }
            item { Info("Pieces", "${t.pieceCount} x ${formatBytes(t.pieceSize.toLong())}") }
            if (t.creator.isNotEmpty()) item { Info("Creator", t.creator) }
            if (t.comment.isNotEmpty()) item { Info("Comment", t.comment) }
            item { Info("Private", if (t.isPrivate) "Yes" else "No") }
            item { Text("Files", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp)) }
            items(files, key = { it.index }) { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = f.wanted, onCheckedChange = { vm.setWanted(t.id, listOf(f.index), it) })
                    Column(Modifier.weight(1f)) {
                        Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val pct = if (f.length > 0) f.completed * 100.0 / f.length else 100.0
                        Text(
                            "${formatBytes(f.length)} - %.0f%%".format(pct),
                            style = MaterialTheme.typography.bodySmall,
                        )
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
