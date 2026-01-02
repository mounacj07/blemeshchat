package com.example.meshchat.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.meshchat.data.ChatRepository
import com.example.meshchat.data.db.MessageEntity
import com.example.meshchat.service.MeshService

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    val allMessages: LiveData<List<MessageEntity>> = repository.allMessages

    fun setMeshService(service: MeshService){
        repository.setMeshService(service)
    }

    fun sendMessage(content: String, targetId: Short) {
        if (content.isNotBlank()) {
            repository.sendMessage(content, targetId)
        }
    }
}

class ChatViewModelFactory(private val repository: ChatRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
