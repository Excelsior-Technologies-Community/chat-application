package com.aarav.chatapplication.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {

    const val CHANNEL_ID_MESSAGES = "chat_messages"
    const val CHANNEL_ID_SERVICE = "chat_service"
    private const val CHANNEL_NAME_MESSAGES = "Chat Messages"
    private const val CHANNEL_NAME_SERVICE = "Chat Service Status"
    private const val CHANNEL_DESC_MESSAGES = "Notifications for new chat and group messages"
    private const val CHANNEL_DESC_SERVICE = "Shows when the app is monitoring for new messages"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService<NotificationManager>() ?: return

            val messageChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                CHANNEL_NAME_MESSAGES,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_MESSAGES
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 100, 250)
                setShowBadge(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                CHANNEL_NAME_SERVICE,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC_SERVICE
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(messageChannel)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }
}
