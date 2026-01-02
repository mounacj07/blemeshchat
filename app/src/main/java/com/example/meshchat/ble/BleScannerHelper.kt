package com.example.meshchat.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log

class BleScannerHelper(
    private val adapter: BluetoothAdapter,
    private val onPacketReceived: (MeshPacket) -> Unit
) {

    private var scanner: BluetoothLeScanner? = adapter.bluetoothLeScanner
    private var scanCallback: ScanCallback? = null
    @Volatile private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    
    private val RESTART_INTERVAL_MS = 30000L

    fun startScanning() {
        if (isScanning) return
        if (!adapter.isEnabled) {
            Log.w("BleScanner", "Bluetooth is not enabled, cannot scan.")
            return
        }
        if (scanner == null) {
            Log.e("BleScanner", "BluetoothLeScanner is not available.")
            return
        }
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // FIX: Create a hardware filter to scan specifically for our Service UUID.
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.scanRecord?.let { record ->
                    try {
                        // FIX: The data is now in the manufacturer-specific data of the scan response.
                        // The hardware filter on the UUID ensures this callback only triggers for our devices.
                        val bytes = record.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID)
                        bytes?.let {
                            MeshPacket.fromBytes(it)?.let { packet ->
                                onPacketReceived(packet)
                            }
                        }
                    } catch(e: Exception) {
                        Log.w("BleScanner", "Error processing scan result", e)
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BleScanner", "Scan failed with error code: $errorCode")
                isScanning = false
            }
        }

        try {
            // FIX: Start scan with the hardware filter for the Service UUID.
            scanner?.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            Log.d("BleScanner", "Scan started with hardware filter for Service UUID.")
            
            handler.postDelayed({ restartScan() }, RESTART_INTERVAL_MS)
        } catch (e: SecurityException) {
            Log.e("BleScanner", "SecurityException: Missing permissions for scanning.", e)
            isScanning = false
        } catch (e: Exception) {
            Log.e("BleScanner", "Error starting scan", e)
            isScanning = false
        }
    }

    private fun restartScan() {
        if (isScanning) {
            Log.d("BleScanner", "Restarting scan...")
            stopScanning()
            handler.postDelayed({ startScanning() }, 500) // Brief pause before restart
        }
    }

    fun stopScanning() {
        handler.removeCallbacksAndMessages(null) // Stop any pending restarts
        if (!isScanning) return
        isScanning = false
        
        if (!adapter.isEnabled || scanner == null) {
            Log.w("BleScanner", "Bluetooth not available, cannot stop scan.")
            return
        }

        try {
            scanCallback?.let {
                scanner?.stopScan(it)
                Log.d("BleScanner", "Scan stopped.")
            }
        } catch (e: SecurityException) {
            Log.e("BleScanner", "SecurityException: Missing permissions to stop scan.", e)
        } catch (e: Exception) {
            Log.e("BleScanner", "Error stopping scan", e)
        }
    }
}
