package com.aarav.chatapplication.data.model

import android.media.AudioTimestamp

data class Presence(
    val online: Boolean = false,
    val lastSeen: Long = 0L
)