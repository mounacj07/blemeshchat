package com.example.meshchat.ble

import android.util.Log
import kotlinx.coroutines.CoroutineScope

class MeshRouter(
    private val advertiser: BleAdvertiserHelper,
    private val scope: CoroutineScope,
    private val myId: Short, // FIX: Added myId to identify packets for this device
    private val onMessageReceived: (MeshPacket) -> Unit
) {

    fun processReceivedPacket(packet: MeshPacket) {
        // Packets with TTL 0 should not be processed or relayed further.
        if (packet.ttl <= 0) return

        // FIX: Create a unique cache key combining messageId AND ttl.
        // For chunked messages, the TTL field encodes the chunk number,
        // so each chunk of the same message will have a unique key.
        // This prevents chunks 2, 3, 4 from being incorrectly dropped as "duplicates".
        val cacheKey = (packet.messageId.toInt() shl 8) or (packet.ttl.toInt() and 0xFF)
        
        if (PacketCache.hasSeen(cacheKey)) {
            return
        }
        PacketCache.markSeen(cacheKey)

        val isForMe = packet.targetId == myId || packet.targetId == 0.toShort()

        if (isForMe) {
            onMessageReceived(packet)
        }

        // Relay logic:
        // 1. If it's a broadcast (targetId == 0), we ALWAY relay it (if TTL > 0), even if we processed it.
        // 2. If it's a direct message (targetId != 0), we relay it ONLY if it's NOT for us.
        val isBroadcast = packet.targetId == 0.toShort()
        val shouldRelay = isBroadcast || !isForMe

        if (shouldRelay) {
            val relayedPacket = packet.copy(ttl = (packet.ttl - 1).toByte())
            Log.d("MeshRouter", "Relaying packet ${relayedPacket.messageId} for ${relayedPacket.targetId} with TTL ${relayedPacket.ttl}")
            advertiser.startAdvertising(relayedPacket, 500) // Relay for a short burst
        }
    }

    fun sendParams(packet: MeshPacket) {
        // Use priority advertising for SOS, regular for others.
        if (packet.type == BleConstants.PACKET_TYPE_SOS) {
            Log.d("MeshRouter", "Sending priority SOS packet ${packet.messageId}")
            // FIX: Reduced from 10000ms to 2000ms per chunk.
            // SOS messages with location URLs get chunked (~4 chunks).
            // 10s per chunk = 40s total; 2s per chunk = 8s total.
            // Priority is maintained via startPriorityAdvertising().
            advertiser.startPriorityAdvertising(packet, 2000)
        } else {
            Log.d("MeshRouter", "Queueing packet ${packet.messageId}")
            advertiser.startAdvertising(packet, 500)
        }
    }
}
