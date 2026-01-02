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

        if (PacketCache.hasSeen(packet.messageId.toInt())) {
            return
        }
        PacketCache.markSeen(packet.messageId.toInt())

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
            Log.d("MeshRouter", "Sending priority packet ${packet.messageId}")
            advertiser.startPriorityAdvertising(packet, 10000) // Advertise SOS for 10 seconds
        } else {
            Log.d("MeshRouter", "Queueing packet ${packet.messageId}")
            advertiser.startAdvertising(packet, 500)
        }
    }
}
