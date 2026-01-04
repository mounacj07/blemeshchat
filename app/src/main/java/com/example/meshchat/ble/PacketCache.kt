package com.example.meshchat.ble

import android.util.LruCache

object PacketCache {
    private const val CACHE_SIZE = 100
    private const val EXPIRATION_MS = 30000L // 30 seconds - prevents stale entries after restart
    // Key: MessageID (Int), Value: Timestamp
    private val cache = LruCache<Int, Long>(CACHE_SIZE)

    /**
     * Atomically checks if a message has been seen and marks it if not.
     * Returns true if the message was ALREADY seen (should be dropped).
     * Returns false if this is the FIRST time seeing it (should be processed).
     */
    fun hasSeenAndMark(messageId: Int): Boolean {
        synchronized(cache) {
            val now = System.currentTimeMillis()
            val timestamp = cache.get(messageId)
            
            if (timestamp != null) {
                // Expire entries older than 30 seconds
                if (now - timestamp > EXPIRATION_MS) {
                    cache.remove(messageId)
                    // Expired, treat as new - mark and return false
                    cache.put(messageId, now)
                    return false
                }
                // Already seen and not expired
                return true
            }
            
            // First time seeing this - mark and return false
            cache.put(messageId, now)
            return false
        }
    }
}
