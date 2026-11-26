package com.droidtorrent

import android.app.Application

class TorrentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TorrentEngine.start(this)
    }
}
