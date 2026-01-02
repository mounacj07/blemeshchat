package com.example.meshchat.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.LinkedList

class BleAdvertiserHelper(private val adapter: BluetoothAdapter) {

    private var advertiser: BluetoothLeAdvertiser? = adapter.bluetoothLeAdvertiser
    private var callback: AdvertiseCallback? = null
    @Volatile private var isAdvertising = false
    private val handler = Handler(Looper.getMainLooper())
    private val advertisingQueue: LinkedList<Pair<MeshPacket, Long>> = LinkedList()

    fun startAdvertising(packet: MeshPacket, duration: Long) {
        advertisingQueue.addLast(packet to duration)
        processQueue()
    }

    fun startPriorityAdvertising(packet: MeshPacket, duration: Long) {
        advertisingQueue.addFirst(packet to duration)
        if (isAdvertising) {
            stopAdvertising(forceNext = true)
        } else {
            processQueue()
        }
    }

    private fun processQueue() {
        if (isAdvertising || advertisingQueue.isEmpty()) return

        val (packet, duration) = advertisingQueue.poll() ?: return

        if (!adapter.isEnabled) {
            Log.w("BleAdvertiser", "Bluetooth is not enabled, cannot advertise.")
            advertisingQueue.clear()
            return
        }

        val dataBytes = packet.toBytes()

        if (dataBytes.size > 27) { // 31 byte limit - 2 (AD overhead) - 2 (manufacturer ID)
            Log.e("BleAdvertiser", "Packet payload too large for manufacturer data: ${dataBytes.size} bytes")
            processQueue()
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true) // FIX: Set to true to allow scan responses
            .build()

        // FIX: The main advertising packet MUST include the Service UUID to be discoverable.
        val mainAdvertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()

        // FIX: The actual data goes into the Scan Response packet.
        val scanResponseData = AdvertiseData.Builder()
            .addManufacturerData(BleConstants.MANUFACTURER_ID, dataBytes)
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                isAdvertising = true
                Log.d("BleAdvertiser", "Advertising started with Scan Response for packet ${packet.messageId}.")
                handler.postDelayed({ stopAdvertising(forceNext = true) }, duration)
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("BleAdvertiser", "Advertising failed: $errorCode")
                isAdvertising = false
                processQueue()
            }
        }

        try {
            // FIX: Pass both the main advertisement data and the scan response data.
            advertiser?.startAdvertising(settings, mainAdvertiseData, scanResponseData, callback)
        } catch (e: SecurityException) {
            Log.e("BleAdvertiser", "SecurityException on advertising: Missing permissions?", e)
            isAdvertising = false
        } catch (e: Exception) {
            Log.e("BleAdvertiser", "Error starting advertising", e)
            isAdvertising = false
        }
    }

    private fun stopAdvertising(forceNext: Boolean) {
        handler.removeCallbacksAndMessages(null)
        if (!isAdvertising) {
            if (forceNext) processQueue()
            return
        }

        try {
            advertiser?.stopAdvertising(callback)
        } catch (e: SecurityException) {
             Log.e("BleAdvertiser", "SecurityException on stopping advertise: Missing permissions?", e)
        } catch (e: Exception) {
            Log.e("BleAdvertiser", "Error stopping advertising", e)
        }

        isAdvertising = false
        Log.d("BleAdvertiser", "Advertising stopped.")
        if (forceNext) processQueue()
    }

    fun clearQueue() {
        advertisingQueue.clear()
        stopAdvertising(forceNext = false)
    }
}