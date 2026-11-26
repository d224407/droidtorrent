package com.droidtorrent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** True while at least one torrent is downloading; drives the background service. */
fun activeDownloads(): Flow<Boolean> =
    TorrentEngine.torrents
        .map { list -> list.any { it.status == TorrentStatus.Downloading } }
        .distinctUntilChanged()
