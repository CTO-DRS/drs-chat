package drs.chat.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * DrsChatForegroundService — keeps the WebSocketTransport alive when the
 * app is backgrounded so the user keeps receiving messages. The service
 * is sticky: Android restarts it if killed. foregroundServiceType=dataSync
 * is required on API 34+ (ADR-0003).
 *
 * V1 beta: shows a persistent notification with the connection state.
 * Does NOT actually start the WebSocketTransport yet — that requires a
 * real relay URL + identity reload path (M5+).
 */
class DrsChatForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("drs chat")
                .setContentText("Connected · waiting for messages")
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setOngoing(true)
                .build()
        startForeground(NOTIF_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "drs chat — connection",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Persistent connection status" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "drs-chat-connection"
        private const val NOTIF_ID = 1
    }
}
