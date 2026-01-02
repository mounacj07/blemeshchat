package com.example.meshchat.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey val nodeId: String, // 4-byte hex string or UUID
    val lastSeenTimestamp: Long,
    val hopCount: Int = 0,
    val isDirect: Boolean = false,
    val name: String? = null
)
