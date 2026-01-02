package com.example.meshchat.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String, // Cached name
    val targetId: String? = null, // New: Who is this for? (null for Broadcast/Income where we might not know)
    val content: String,
    val timestamp: Long,
    val isSelf: Boolean,
    val status: MessageStatus = MessageStatus.SENDING
)

enum class MessageStatus {
    SENDING, SENT, DELIVERED, FAILED
}
