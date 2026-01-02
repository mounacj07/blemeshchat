package com.example.meshchat.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.meshchat.R
import com.example.meshchat.data.ChatRepository
import com.example.meshchat.data.UserPreferences
import com.example.meshchat.service.MeshService

class MainActivity : AppCompatActivity() {

    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(ChatRepository(applicationContext))
    }

    private var targetIdString: String? = null
    private var targetIdShort: Short = 0
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MeshService.LocalBinder
            val meshService = binder.getService()
            chatViewModel.setMeshService(meshService)
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    private val requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            startAndBindService()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required to use Mesh Chat", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Add back button

        checkAndRequestPermissions()

        targetIdString = intent.getStringExtra("TARGET_ID")
        val targetName = intent.getStringExtra("TARGET_NAME") ?: "Unknown"
        targetIdShort = targetIdString?.toShortOrNull() ?: 0

        supportActionBar?.title = "Chat with $targetName"

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val adapter = ChatAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        chatViewModel.allMessages.observe(this) { messages ->
            val myId = UserPreferences(this).getUserId().toString()
            val filtered = messages.filter {
                if (it.isSelf) {
                    it.targetId == targetIdString
                } else {
                    it.senderId == targetIdString
                }
            }
            adapter.submitList(filtered)
            if (filtered.isNotEmpty()) recyclerView.scrollToPosition(filtered.size - 1)
        }

        val editText = findViewById<EditText>(R.id.editTextMessage)
        findViewById<Button>(R.id.buttonSend).setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotEmpty()) {
                chatViewModel.sendMessage(text, targetIdShort)
                editText.text.clear()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            startAndBindService()
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, MeshService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
}
