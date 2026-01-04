package com.example.meshchat.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.meshchat.R
import com.example.meshchat.data.UserPreferences
import com.example.meshchat.data.db.AppDatabase
import com.example.meshchat.data.db.NodeEntity
import com.example.meshchat.service.MeshService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

class DeviceListViewModel(private val database: AppDatabase) : ViewModel() {
    val allNodes = database.nodeDao().getAllNodes().asLiveData()

    fun clearNodes() {
        viewModelScope.launch {
            database.nodeDao().clearAll()
        }
    }
}

class DeviceListViewModelFactory(private val database: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DeviceListViewModel(database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class DeviceListActivity : AppCompatActivity() {

    private lateinit var meshService: MeshService
    private var isBound = false
    private var isScanFlowActive = false
    private lateinit var textPrompt: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var myNameTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: AppDatabase

    private val viewModel: DeviceListViewModel by viewModels {
        DeviceListViewModelFactory(AppDatabase.getDatabase(this))
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d("DeviceListActivity", "Service Connected")
            val binder = service as MeshService.LocalBinder
            meshService = binder.getService()
            isBound = true
            if (isScanFlowActive) {
                executeScan()
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.d("DeviceListActivity", "Service Disconnected")
            isBound = false
        }
    }

    private val requestBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            if (isScanFlowActive) executeScan() // Step 3: BT is on, now scan
        } else {
            Toast.makeText(this, "Bluetooth is required to scan.", Toast.LENGTH_SHORT).show()
            isScanFlowActive = false
        }
    }

    private val requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            // Permissions granted - start service if not bound, then continue with scan flow if active
            if (!isBound) startAndBindService()
            if (isScanFlowActive) checkBluetoothEnabled()
        } else {
            Toast.makeText(this, "All permissions are required.", Toast.LENGTH_SHORT).show()
            isScanFlowActive = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        database = AppDatabase.getDatabase(this)

        textPrompt = findViewById(R.id.text_prompt)
        recyclerView = findViewById(R.id.recyclerViewDevices)
        myNameTextView = findViewById(R.id.text_my_name)

        val userPrefs = UserPreferences(this)
        myNameTextView.text = "Device Name: ${userPrefs.getUserName()}"

        val deviceAdapter = DeviceAdapter { node ->
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("TARGET_ID", node.nodeId)
                putExtra("TARGET_NAME", node.name)
            }
            startActivity(intent)
        }

        recyclerView.apply {
            adapter = deviceAdapter
            layoutManager = LinearLayoutManager(this@DeviceListActivity)
        }

        viewModel.allNodes.observe(this) { nodes ->
            textPrompt.visibility = if (nodes.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (nodes.isEmpty()) View.GONE else View.VISIBLE
            deviceAdapter.submitList(nodes)
        }

        findViewById<Button>(R.id.button_scan).setOnClickListener {
            initiateScanFlow()
        }

        findViewById<Button>(R.id.button_sos).setOnLongClickListener {
            handleSos()
            true // Consume the long click
        }

        findViewById<Button>(R.id.button_add_device).setOnClickListener {
            showAddDeviceDialog()
        }

        // FIX: Check permissions first before starting service (required for Android 12+)
        checkAndRequestPermissionsForService()
    }

    private fun showAddDeviceDialog() {
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
        // Use a simple programmatic layout for the dialog
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        val idInput = EditText(this).apply {
            hint = "Device ID (e.g. 12345)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val nameInput = EditText(this).apply {
            hint = "Name (optional)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        container.addView(idInput)
        container.addView(nameInput)

        AlertDialog.Builder(this)
            .setTitle("Add Device by ID")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val idText = idInput.text.toString().trim()
                val nameText = nameInput.text.toString().trim().ifEmpty { "Device $idText" }
                if (idText.isNotEmpty()) {
                    viewModel.viewModelScope.launch {
                        val node = NodeEntity(
                            nodeId = idText,
                            lastSeenTimestamp = System.currentTimeMillis(),
                            hopCount = 1, // Mark as indirect (via mesh)
                            isDirect = false,
                            name = nameText
                        )
                        database.nodeDao().insertNode(node)
                    }
                    Toast.makeText(this, "Added $nameText", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a device ID", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkAndRequestPermissionsForService() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ needs POST_NOTIFICATIONS for SOS alerts
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val permissionsToRequest = requiredPermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            // Permissions already granted, start service
            startAndBindService()
        }
    }

    private fun initiateScanFlow() {
        Log.d("DeviceListActivity", "Step 1: Scan flow initiated.")
        isScanFlowActive = true
        viewModel.clearNodes()
        checkAndRequestPermissions() // Start the sequential check
    }

    @SuppressLint("MissingPermission")
    private fun handleSos() {
        if (!isBound) {
            Toast.makeText(this, "Service not ready, please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val locationString = if (location != null) {
                "http://maps.google.com/maps?q=${location.latitude},${location.longitude}"
            } else {
                "Location not available"
            }
            meshService.sendSOS(locationString)
            
            // Vibrate for feedback
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(500)
            }

            Toast.makeText(this, "SOS Broadcasted!", Toast.LENGTH_LONG).show()
        }.addOnFailureListener {
             Toast.makeText(this, "Could not get location for SOS.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12 and above, background location is NOT required for foreground scanning.
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            // For Android 11 and below.
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val permissionsToRequest = requiredPermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("DeviceListActivity", "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestMultiplePermissions.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d("DeviceListActivity", "Permissions already granted.")
            checkBluetoothEnabled() // Permissions are good, check bluetooth state
        }
    }

    private fun checkBluetoothEnabled() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            isScanFlowActive = false
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.d("DeviceListActivity", "Bluetooth is disabled, requesting to enable.")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        } else {
            Log.d("DeviceListActivity", "Bluetooth is already enabled.")
            executeScan() // BT is good, execute scan
        }
    }

    private fun executeScan() {
        if (!isScanFlowActive) return

        if (isBound) {
            Log.d("DeviceListActivity", "Executing scan now.")
            meshService.startScanning()
        } else {
            Log.d("DeviceListActivity", "Service not bound yet, scan will start upon connection.")
            // Scan will be triggered by onServiceConnected
        }
        isScanFlowActive = false // Reset the flag, the flow is complete or pending service connection
    }
	
    private fun startAndBindService() {
        if (isBound) return
        Intent(this, MeshService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
