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

        // FIX: For chunked messages, TTL encodes chunk number (totalChunks in upper 4 bits).
        // For non-chunked messages (totalChunks = 0), use only messageId to prevent duplicates from relays.
        val totalChunks = (packet.ttl.toInt() shr 4) and 0x0F
        val isChunked = totalChunks > 0
        
        val cacheKey = if (isChunked) {
            // Chunked: include TTL (which has chunk number) to allow each chunk through
            (packet.messageId.toInt() shl 8) or (packet.ttl.toInt() and 0xFF)
        } else {
            // Non-chunked: use only messageId to deduplicate relays with different TTLs
            packet.messageId.toInt()
        }
        
        if (PacketCache.hasSeen(cacheKey)) {
            return
        }
        PacketCache.markSeen(cacheKey)

        val isForMe = packet.targetId == myId || packet.targetId == 0.toShort()

        if (isForMe) {
            onMessageReceived(packet)
        }

        // Relay logic:
        // 1. If it's a broadcast (targetId == 0), we ALWAYS relay it (if TTL > 0), even if we processed it.
        // 2. If it's a direct message (targetId != 0), we relay it ONLY if it's NOT for us.
        // 3. EXCEPTION: Don't relay DISCOVERY packets - they broadcast frequently and cause issues.
        // NOTE: SOS is now relayed. For chunked packets (including SOS), TTL encodes chunk info
        //       and must NOT be decremented to avoid corrupting chunk data and causing duplicate notifications.
        val isBroadcast = packet.targetId == 0.toShort()
        val isDiscovery = packet.type == BleConstants.PACKET_TYPE_DISCOVERY
        val shouldRelay = (isBroadcast || !isForMe) && !isDiscovery

        if (shouldRelay) {
            // For chunked packets, preserve TTL (it contains chunk encoding, not hop count)
            // For non-chunked packets, decrement TTL as normal
            val relayedPacket = if (isChunked) {
                packet // Preserve TTL for chunked to maintain chunk info
            } else {
                packet.copy(ttl = (packet.ttl - 1).toByte())
            }
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
            Log.d("MeshRouter", "Sending priority message packet ${packet.messageId}")
            // Use priority advertising for messages too - jumps ahead of discovery/relay
            // SOS still has highest priority (2000ms duration and arrives after user trigger)
            advertiser.startPriorityAdvertising(packet, 500)
        }
    }
}
