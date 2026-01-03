package com.example.meshchat.ble

import android.util.LruCache

object PacketCache {
    private const val CACHE_SIZE = 100
    private const val EXPIRATION_MS = 30000L // 30 seconds - prevents stale entries after restart
    // Key: MessageID (Int), Value: Timestamp
    private val cache = LruCache<Int, Long>(CACHE_SIZE)

    fun hasSeen(messageId: Int): Boolean {
        synchronized(cache) {
            val timestamp = cache.get(messageId) ?: return false
            // Expire entries older than 30 seconds
            if (System.currentTimeMillis() - timestamp > EXPIRATION_MS) {
                cache.remove(messageId)
                return false
            }
            return true
        }
    }

    fun markSeen(messageId: Int) {
        synchronized(cache) {
            cache.put(messageId, System.currentTimeMillis())
        }
    }
}
