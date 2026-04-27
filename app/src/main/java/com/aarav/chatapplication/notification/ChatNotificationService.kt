package com.aarav.chatapplication.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aarav.chatapplication.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ChatNotificationService : Service() {

    @Inject
    lateinit var notificationManager: MessageNotificationManager

    companion object {
        private const val EXTRA_USER_ID = "extra_user_id"
        private const val SERVICE_NOTIFICATION_ID = 99

        fun start(context: Context, userId: String) {
            val intent = Intent(context, ChatNotificationService::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ChatNotificationService::class.java)
            context.stopService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val userId = intent?.getStringExtra(EXTRA_USER_ID)

        if (userId != null) {
            startForegroundService(userId)
            notificationManager.startListening(userId)
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    private fun startForegroundService(userId: String) {
        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Chat Service")
            .setContentText("Monitoring for new messages...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        notificationManager.stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
