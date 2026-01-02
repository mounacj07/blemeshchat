package com.example.meshchat.ble

import android.util.LruCache

object PacketCache {
    private const val CACHE_SIZE = 100
    // Key: MessageID (Int), Value: Timestamp
    private val cache = LruCache<Int, Long>(CACHE_SIZE)

    fun hasSeen(messageId: Int): Boolean {
        synchronized(cache) {
            return cache.get(messageId) != null
        }
    }

    fun markSeen(messageId: Int) {
        synchronized(cache) {
            cache.put(messageId, System.currentTimeMillis())
        }
    }
}
