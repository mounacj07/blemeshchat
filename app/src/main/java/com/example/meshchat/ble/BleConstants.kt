package com.example.meshchat.ble

import java.util.UUID

object BleConstants {
    // This is a custom UUID for our service.
    // It's used by the advertiser to broadcast its presence and by the scanner to discover it.
    val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

    const val PACKET_TYPE_MESSAGE: Byte = 1
    const val PACKET_TYPE_DISCOVERY: Byte = 2
    const val PACKET_TYPE_SOS: Byte = 3
    const val PACKET_TYPE_CHUNK: Byte = 4 // For message chunks

    const val MANUFACTURER_ID = 0xABCD // Replace with your company's registered ID
    const val PROTOCOL_HEADER_HIGH: Byte = 0x01
    const val PROTOCOL_HEADER_LOW: Byte = 0x02
}
