package com.example.meshchat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.meshchat.ble.BleAdvertiserHelper
import com.example.meshchat.ble.BleConstants
import com.example.meshchat.ble.BleScannerHelper
import com.example.meshchat.ble.MeshPacket
import com.example.meshchat.ble.MeshRouter
import com.example.meshchat.data.UserPreferences
import com.example.meshchat.data.db.AppDatabase
import com.example.meshchat.data.db.MessageEntity
import com.example.meshchat.data.db.MessageStatus
import com.example.meshchat.data.db.NodeEntity
import com.example.meshchat.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MeshService : Service() {

    private val binder = LocalBinder()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var scannerHelper: BleScannerHelper
    private lateinit var advertiserHelper: BleAdvertiserHelper
    private lateinit var meshRouter: MeshRouter
    private lateinit var database: AppDatabase
    private lateinit var userPrefs: UserPreferences
    private data class ChunkSession(val timestamp: Long, val chunks: MutableMap<Byte, String>)
    private val incomingChunks = ConcurrentHashMap<Short, ChunkSession>()
    
    // Cleanup incomplete chunks every 60 seconds
    private val CLEANUP_INTERVAL = 60000L

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF -> stopBleOperations()
                    BluetoothAdapter.STATE_ON -> startBleOperations()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        userPrefs = UserPreferences(this)
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            stopSelf()
            return
        }

        startForegroundService()
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        if (bluetoothAdapter!!.isEnabled) {
            startBleOperations()
        }
    }

    fun startScanning() {
        if (::scannerHelper.isInitialized) scannerHelper.startScanning()
    }

    private fun startBleOperations() {
        advertiserHelper = BleAdvertiserHelper(bluetoothAdapter!!)
        meshRouter = MeshRouter(advertiserHelper, scope, userPrefs.getUserId()) { packet -> handleIncomingMessage(packet) }
        scannerHelper = BleScannerHelper(bluetoothAdapter!!) { packet -> meshRouter.processReceivedPacket(packet) }
        startDiscoveryBroadcast()
    }

    private fun stopBleOperations() {
        if (::scannerHelper.isInitialized) scannerHelper.stopScanning()
        if (::advertiserHelper.isInitialized) advertiserHelper.clearQueue()
    }

    private fun startDiscoveryBroadcast() {
        scope.launch {
            // Start cleanup job
            launch {
                while(true) {
                    delay(CLEANUP_INTERVAL)
                    cleanupIncompleteChunks()
                }
            }

            while (true) {
                val name = userPrefs.getUserName()
                val userId = userPrefs.getUserId()
                val packet = MeshPacket(
                    type = BleConstants.PACKET_TYPE_DISCOVERY,
                    ttl = 3.toByte(),
                    messageId = (System.currentTimeMillis() and 0xFFFF).toShort(),
                    senderId = userId,
                    targetId = 0.toShort(),
                    // FIX: Truncate name to 14 chars.
                    // 31 (Max) - 3 (Flags) - 4 (ManData Header) - 8 (Mesh Header) = 16 bytes max.
                    // Using 14 to be safe.
                    payload = name.take(14)
                )
                if (::advertiserHelper.isInitialized) {
                    advertiserHelper.startAdvertising(packet, 750) 
                }
                delay(1500) 
            }
        }
    }

    private fun cleanupIncompleteChunks() {
        val now = System.currentTimeMillis()
        val expiredIds = incomingChunks.filter { (now - it.value.timestamp) > 60000 }.keys
        expiredIds.forEach { incomingChunks.remove(it) }
        if (expiredIds.isNotEmpty()) {
            Log.d("MeshService", "Cleaned up ${expiredIds.size} incomplete message sessions.")
        }
    }

    private fun handleIncomingMessage(packet: MeshPacket) {
        val myId = userPrefs.getUserId()
        if (packet.senderId == myId) return 
        
        // DISCOVERY packets are never chunked, process them directly
        if (packet.type == BleConstants.PACKET_TYPE_DISCOVERY) {
            scope.launch { processCompleteMessage(packet) }
            return
        }

        // For MESSAGE and SOS, handle chunking
        val totalChunks = (packet.ttl.toInt() shr 4) and 0x0F
        val chunkNum = packet.ttl.toInt() and 0x0F

        // If totalChunks is 0, this is NOT a chunked message (normal TTL)
        if (totalChunks == 0) {
            scope.launch { processCompleteMessage(packet) }
            return
        }

        // This is a chunked message
        val session = incomingChunks.getOrPut(packet.messageId) { 
            ChunkSession(System.currentTimeMillis(), ConcurrentHashMap()) 
        }

        scope.launch {
            session.chunks[chunkNum.toByte()] = packet.payload

            if (session.chunks.size == totalChunks) {
                val fullPayload = (1..totalChunks).map { session.chunks[it.toByte()] }.joinToString("")
                val finalPacket = packet.copy(payload = fullPayload, ttl = 1)
                processCompleteMessage(finalPacket)
                incomingChunks.remove(packet.messageId)
            }
        }
    }

    private suspend fun processCompleteMessage(packet: MeshPacket) {
        when (packet.type) {
            BleConstants.PACKET_TYPE_MESSAGE -> {
                val senderName = withContext(Dispatchers.IO) { database.nodeDao().getNodeById(packet.senderId.toString())?.name ?: "Unknown User" }
                val entity = MessageEntity(senderId = packet.senderId.toString(), senderName = senderName, targetId = packet.targetId.toString(), content = packet.payload, timestamp = System.currentTimeMillis(), isSelf = false, status = MessageStatus.DELIVERED)
                database.messageDao().insertMessage(entity)
            }
            BleConstants.PACKET_TYPE_DISCOVERY -> {
                val existingNode = database.nodeDao().getNodeById(packet.senderId.toString())
                if (existingNode != null) {
                    database.nodeDao().updateNode(packet.senderId.toString(), System.currentTimeMillis(), 0, true)
                    if (existingNode.name != packet.payload) {
                        database.nodeDao().updateNodeName(packet.senderId.toString(), packet.payload)
                    }
                } else {
                    val node = NodeEntity(nodeId = packet.senderId.toString(), lastSeenTimestamp = System.currentTimeMillis(), hopCount = 0, isDirect = true, name = packet.payload)
                    database.nodeDao().insertNode(node)
                }
            }
            BleConstants.PACKET_TYPE_SOS -> {
                val senderName = withContext(Dispatchers.IO) { database.nodeDao().getNodeById(packet.senderId.toString())?.name ?: "SOS User" }
                val entity = MessageEntity(senderId = packet.senderId.toString(), senderName = senderName, targetId = null, content = "SOS: ${packet.payload}", timestamp = System.currentTimeMillis(), isSelf = false, status = MessageStatus.DELIVERED)
                database.messageDao().insertMessage(entity)
                showSosNotification(packet.senderId.toString(), senderName, packet.payload)
            }
        }
    }

    private fun showSosNotification(senderId: String, from: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sos_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SOS Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("TARGET_ID", senderId)
            putExtra("TARGET_NAME", from)
        }
        val pendingIntent = PendingIntent.getActivity(this, System.currentTimeMillis().toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SOS Alert from $from")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(senderId.hashCode(), notification)
    }

    private fun startForegroundService() {
        val channelId = "MeshChatChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Mesh Chat Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Mesh Chat Active")
            .setContentText("Ready to scan for devices...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)
    }

    private fun sendPacket(packet: MeshPacket) {
        if (::meshRouter.isInitialized) meshRouter.sendParams(packet)
    }

    private fun splitAndSend(originalPacket: MeshPacket) {
        val MAX_PAYLOAD_SIZE = 14 
        if (originalPacket.payload.length <= MAX_PAYLOAD_SIZE) {
            sendPacket(originalPacket)
            return
        }

        val chunks = originalPacket.payload.chunked(MAX_PAYLOAD_SIZE)
        val totalChunks = chunks.size
        if (totalChunks > 15) {
            Log.e("MeshService", "Message too long to be chunked.")
            return
        }

        chunks.forEachIndexed { index, chunk ->
            val chunkNum = index + 1
            val ttl = ((totalChunks shl 4) or chunkNum).toByte()
            val chunkPacket = originalPacket.copy(payload = chunk, ttl = ttl)
            sendPacket(chunkPacket)
        }
    }

    fun sendMessage(content: String, targetId: Short) {
        val senderId = userPrefs.getUserId()
        val messageId = (System.currentTimeMillis() and 0xFFFF).toShort()
        
        scope.launch {
            val entity = MessageEntity(senderId = senderId.toString(), senderName = "Me", targetId = targetId.toString(), content = content, timestamp = System.currentTimeMillis(), isSelf = true, status = MessageStatus.SENT)
            database.messageDao().insertMessage(entity)
        }

        val packet = MeshPacket(type = BleConstants.PACKET_TYPE_MESSAGE, ttl = 3.toByte(), messageId = messageId, senderId = senderId, targetId = targetId, payload = content)
        splitAndSend(packet)
    }

    fun sendSOS(locationPayload: String) {
         val senderId = userPrefs.getUserId()
        val messageId = (System.currentTimeMillis() and 0xFFFF).toShort()
        val packet = MeshPacket(type = BleConstants.PACKET_TYPE_SOS, ttl = 5.toByte(), messageId = messageId, senderId = senderId, targetId = 0.toShort(), payload = locationPayload)
        splitAndSend(packet)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBleOperations()
        unregisterReceiver(bluetoothStateReceiver)
        scope.cancel()
    }
    
    override fun onBind(intent: Intent): IBinder = binder

    inner class LocalBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }
}
