package com.aarav.chatapplication.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.aarav.chatapplication.MainActivity
import com.aarav.chatapplication.R

object NotificationHelper {

    private const val EXTRA_NOTIFICATION_TYPE = "notification_type"
    const val EXTRA_CHAT_RECEIVER_ID = "chat_receiver_id"
    const val EXTRA_CHAT_RECEIVER_NAME = "chat_receiver_name"
    const val EXTRA_GROUP_ID = "group_id"
    const val EXTRA_GROUP_NAME = "group_name"

    const val TYPE_CHAT = "chat"
    const val TYPE_GROUP = "group"

    fun showChatNotification(
        context: Context,
        senderName: String,
        messageText: String,
        receiverId: String,
        receiverName: String
    ) {
        val notificationId = receiverId.hashCode()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NOTIFICATION_TYPE, TYPE_CHAT)
            putExtra(EXTRA_CHAT_RECEIVER_ID, receiverId)
            putExtra(EXTRA_CHAT_RECEIVER_NAME, receiverName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(messageText)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.notify(notificationId, notification)
    }

    fun showGroupNotification(
        context: Context,
        groupName: String,
        senderName: String,
        messageText: String,
        groupId: String
    ) {
        val notificationId = groupId.hashCode()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NOTIFICATION_TYPE, TYPE_GROUP)
            putExtra(EXTRA_GROUP_ID, groupId)
            putExtra(EXTRA_GROUP_NAME, groupName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(groupName)
            .setContentText("$senderName: $messageText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$senderName: $messageText")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.notify(notificationId, notification)
    }

    fun cancelNotification(context: Context, id: String) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.cancel(id.hashCode())
    }
}
