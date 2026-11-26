package com.droidtorrent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/** Runs only while something is downloading; stops itself when the last download ends. */
class TorrentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            TorrentEngine.shutdown()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            exitProcess(0)
        }

        // Repeated start commands must not stack collectors.
        scope.coroutineContext.cancelChildren()

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Service", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Downloads completed", NotificationManager.IMPORTANCE_DEFAULT))

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification("Running in the background..."),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        scope.launch {
            activeDownloads().collectLatest { active ->
                if (!active) {
                    delay(STOP_DELAY_MS)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        scope.launch {
            TorrentEngine.torrents.collect { list ->
                val active = list.count { it.status == TorrentStatus.Downloading }
                val down = list.sumOf { it.rateDown }
                val text = if (active > 0) "$active downloading - ${formatSpeed(down)}" else "Finishing..."
                nm.notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
        scope.launch {
            TorrentEngine.completed.collect { name ->
                nm.notify(
                    name.hashCode(),
                    NotificationCompat.Builder(this@TorrentService, CHANNEL_DONE)
                        .setSmallIcon(R.drawable.ic_stat_name)
                        .setContentTitle("Download completed")
                        .setContentText(name)
                        .setAutoCancel(true)
                        .build())
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val exit = PendingIntent.getService(
            this, 1, Intent(this, TorrentService::class.java).setAction(ACTION_EXIT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("DroidTorrent")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Exit", exit)
            .build()
    }

    companion object {
        private const val CHANNEL_SERVICE = "service"
        private const val CHANNEL_DONE = "done"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_EXIT = "com.droidtorrent.EXIT"
        private const val STOP_DELAY_MS = 3000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TorrentService::class.java))
        }
    }
}

fun formatSpeed(bytesPerSecond: Int): String {
    if (bytesPerSecond < 1024) return "$bytesPerSecond B/s"
    val kb = bytesPerSecond / 1024.0
    return if (kb < 1024) "%.0f KB/s".format(kb) else "%.1f MB/s".format(kb / 1024)
}
