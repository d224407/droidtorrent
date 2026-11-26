package com.droidtorrent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidtorrent.Settings
import com.droidtorrent.TorrentEngine

private enum class Field { Dir, Queue, Port, Down, Up }

/** Rounded corners that are large on the outer edges of a group and small between items. */
private fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val big = 28.dp
    val small = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s by TorrentEngine.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Field?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val rows = listOf<@Composable () -> Unit>(
                { Row("Download folder", s.downloadDir) { editing = Field.Dir } },
                {
                    Row("Simultaneous downloads",
                        if (s.queueSize > 0) s.queueSize.toString() else "Unlimited") { editing = Field.Queue }
                },
                { Row("Peer port", s.port.toString()) { editing = Field.Port } },
                {
                    Row("Limit download speed", "${s.limitDown} KB/s",
                        trailing = {
                            Switch(s.limitDownEnabled,
                                { v -> TorrentEngine.updateSettings { it.copy(limitDownEnabled = v) } })
                        }) { editing = Field.Down }
                },
                {
                    Row("Limit upload speed", "${s.limitUp} KB/s",
                        trailing = {
                            Switch(s.limitUpEnabled,
                                { v -> TorrentEngine.updateSettings { it.copy(limitUpEnabled = v) } })
                        }) { editing = Field.Up }
                },
            )
            rows.forEachIndexed { i, row ->
                Surface(
                    shape = groupShape(i, rows.size),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) { row() }
            }
        }
    }

    editing?.let { field ->
        val (title, initial) = when (field) {
            Field.Dir -> "Download folder" to s.downloadDir
            Field.Queue -> "Simultaneous downloads (0 = unlimited)" to s.queueSize.toString()
            Field.Port -> "Peer port" to s.port.toString()
            Field.Down -> "Download limit (KB/s)" to s.limitDown.toString()
            Field.Up -> "Upload limit (KB/s)" to s.limitUp.toString()
        }
        var text by remember(field) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = field == Field.Dir,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field == Field.Dir) KeyboardType.Text else KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    apply(field, text)
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

private fun apply(field: Field, text: String) {
    val n = text.trim().toIntOrNull()
    TorrentEngine.updateSettings { s: Settings ->
        when (field) {
            Field.Dir -> if (text.isNotBlank()) s.copy(downloadDir = text.trim()) else s
            Field.Queue -> s.copy(queueSize = (n ?: s.queueSize).coerceAtLeast(0))
            Field.Port -> s.copy(port = (n ?: s.port).coerceIn(1024, 65535))
            Field.Down -> s.copy(limitDown = (n ?: s.limitDown).coerceAtLeast(1))
            Field.Up -> s.copy(limitUp = (n ?: s.limitUp).coerceAtLeast(1))
        }
    }
}

@Composable
private fun Row(title: String, value: String, trailing: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
