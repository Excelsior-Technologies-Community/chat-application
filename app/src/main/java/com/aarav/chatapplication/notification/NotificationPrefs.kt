package com.aarav.chatapplication.notification

import android.content.Context
import android.content.SharedPreferences

class NotificationPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    fun getLastSeenTimestamp(conversationId: String): Long {
        return prefs.getLong("last_timestamp_$conversationId", 0L)
    }

    fun saveLastSeenTimestamp(conversationId: String, timestamp: Long) {
        prefs.edit().putLong("last_timestamp_$conversationId", timestamp).apply()
    }
}
