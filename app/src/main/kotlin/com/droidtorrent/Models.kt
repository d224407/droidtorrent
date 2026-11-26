package com.droidtorrent

enum class TorrentStatus { Stopped, Checking, Downloading, Seeding }

enum class AddResult { Added, Duplicate }

data class TorrentItem(
    val id: Int,
    val name: String,
    val progress: Float,
    val status: TorrentStatus,
    val size: Long,
    val rateDown: Int,
    val rateUp: Int,
    val downloaded: Long,
    val uploaded: Long,
    val etaSeconds: Long,
    val peers: Int,
    val error: String,
    val location: String,
    val addedDate: Long,
    val magnet: String,
    val pieceCount: Int,
    val pieceSize: Int,
    val comment: String,
    val creator: String,
    val isPrivate: Boolean,
)

data class TorrentFile(
    val index: Int,
    val name: String,
    val length: Long,
    val completed: Long,
    val wanted: Boolean,
)

data class Settings(
    val downloadDir: String = "",
    val queueSize: Int = 5,
    val port: Int = 51413,
    val limitDownEnabled: Boolean = false,
    val limitUpEnabled: Boolean = false,
    val limitDown: Int = 100, // KB/s
    val limitUp: Int = 100, // KB/s
    val accent: Int = 0, // ARGB seed, 0 = default colors
)
