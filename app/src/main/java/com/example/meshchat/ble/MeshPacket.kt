package com.example.meshchat.ble

import android.util.Log
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

// FIX: Reduced header size from 10 to 8 bytes.
// Total Header Size: 8 bytes
data class MeshPacket(
    val type: Byte,       // 1 byte
    val ttl: Byte,        // 1 byte
    val messageId: Short, // 2 bytes
    val senderId: Short,  // 2 bytes
    val targetId: Short,  // 2 bytes
    val payload: String
) {
    fun toBytes(): ByteArray {
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        // FIX: Allocate for an 8-byte header, not 10.
        val buffer = ByteBuffer.allocate(8 + payloadBytes.size)

        // FIX: Removed two redundant protocol header bytes.
        // These were making the advertisement packets too large with longer user names,
        // causing them to fail silently. The Manufacturer ID is sufficient for filtering.
        buffer.put(type)
        buffer.put(ttl)
        buffer.putShort(messageId)
        buffer.putShort(senderId)
        buffer.putShort(targetId)
        buffer.put(payloadBytes)

        return buffer.array()
    }

    companion object {
        fun fromBytes(data: ByteArray): MeshPacket? {
            return try {
                // FIX: Check against the new 8-byte header size.
                if (data.size < 8) return null

                val buffer = ByteBuffer.wrap(data)
                
                // FIX: Removed reading of the two redundant header bytes.
                val type = buffer.get()
                val ttl = buffer.get()
                val msgId = buffer.getShort()
                val senderId = buffer.getShort()
                val targetId = buffer.getShort()

                val remaining = data.size - buffer.position()
                if (remaining < 0) return null

                val payloadB = ByteArray(remaining)
                buffer.get(payloadB)

                MeshPacket(type, ttl, msgId, senderId, targetId, String(payloadB, StandardCharsets.UTF_8))
            } catch (e: Exception) {
                Log.e("MeshPacket", "Error parsing byte array", e)
                null
            }
        }
    }
}
