package com.example.meshchat.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStatus(status: MessageStatus): String {
        return status.name
    }

    @TypeConverter
    fun toStatus(value: String): MessageStatus {
        return try {
            MessageStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            MessageStatus.FAILED
        }
    }
}
