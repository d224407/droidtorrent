package com.droidtorrent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortBy { Added, Progress, Size }

class MainViewModel : ViewModel() {
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(SortBy.Added)
    val message = MutableStateFlow<String?>(null)

    val items: StateFlow<List<TorrentItem>> =
        combine(TorrentEngine.torrents, query, sort) { list, q, s ->
            val filtered = list.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
            when (s) {
                SortBy.Added -> filtered.sortedByDescending { it.addedDate }
                SortBy.Progress -> filtered.sortedByDescending { it.progress }
                SortBy.Size -> filtered.sortedByDescending { it.size }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun report(block: () -> AddResult) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching(block)
                .onSuccess { if (it == AddResult.Duplicate) message.value = "Torrent already added" }
                .onFailure { message.value = it.message ?: "Failed to add torrent" }
        }
    }

    fun addMagnet(link: String) = report { TorrentEngine.add(link.trim()) }

    fun addFile(bytes: ByteArray) = report { TorrentEngine.add(bytes) }

    fun start(id: Int) = io { TorrentEngine.start(id) }

    fun stop(id: Int) = io { TorrentEngine.stop(id) }

    fun remove(id: Int, withData: Boolean) = io { TorrentEngine.remove(id, withData) }

    fun setWanted(id: Int, indices: List<Int>, wanted: Boolean) =
        io { TorrentEngine.setFilesWanted(id, indices, wanted) }

    private fun io(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }
}
