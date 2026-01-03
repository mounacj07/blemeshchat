package com.example.meshchat.data.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?
}

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes ORDER BY name ASC")
    fun getAllNodes(): Flow<List<NodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: NodeEntity)
    
    @Query("SELECT * FROM nodes WHERE nodeId = :id LIMIT 1")
    suspend fun getNodeById(id: String): NodeEntity?

    @Query("UPDATE nodes SET lastSeenTimestamp = :t, hopCount = :hops, isDirect = :direct WHERE nodeId = :id")
    suspend fun updateNode(id: String, t: Long, hops: Int, direct: Boolean)

    @Query("UPDATE nodes SET name = :name WHERE nodeId = :id")
    suspend fun updateNodeName(id: String, name: String)
    
    @Query("DELETE FROM nodes")
    suspend fun clearAll()
}
