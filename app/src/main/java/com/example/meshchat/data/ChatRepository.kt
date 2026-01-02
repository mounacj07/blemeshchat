package com.example.meshchat.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.example.meshchat.data.db.AppDatabase
import com.example.meshchat.data.db.MessageEntity
import com.example.meshchat.service.MeshService

class ChatRepository(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    val allMessages: LiveData<List<MessageEntity>> = db.messageDao().getAllMessages().asLiveData()

    private var meshService: MeshService? = null

    fun setMeshService(service: MeshService) {
        this.meshService = service
    }

    fun sendMessage(content: String, targetId: Short) {
        meshService?.sendMessage(content, targetId)
    }
}
