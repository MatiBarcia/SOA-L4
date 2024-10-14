package com.soa.l4.tp2

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.navigation.NavigationBarView
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var navBar: NavigationBarView
    private lateinit var searchDevicesButton: Button
    private lateinit var devicesDropDownMenu: Spinner
    private lateinit var connectButton: Button
    private lateinit var alarmButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var normaText: TextView
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private lateinit var btManager: BluetoothManager
    private lateinit var btAdapter: BluetoothAdapter
    private var handler: Handler = Handler(Looper.getMainLooper(), Handler.Callback { message ->
        when (message.what) {
            Constants.MESSAGE_READ -> {
                val bytes: ByteArray? = message.obj as? ByteArray
                normaText.text = String(bytes!!, 0, message.arg1)
                return@Callback true
            }

            Constants.MESSAGE_TOAST -> {
                Toast.makeText(this, message.data.getString("toast"), Toast.LENGTH_SHORT).show()
                return@Callback true
            }
        }
        false
    })
    private var btSocket: BluetoothSocket? = null
    private var connectedThread: ConnectedThread? = null

    companion object {
        val BT_MODULE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var selectedBluetoothDevice: String? = null
    private var bluetoothDevicesList: ArrayList<String> = ArrayList()
    private var bluetoothDevicesMACAddress: HashMap<String, String> = HashMap()
    private val activityResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Result
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(this, "Activa Bluetooth para usar la aplicacion", Toast.LENGTH_LONG)
                .show()
        } else {
            searchDevices()
        }
    }
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Boolean
            if (!isGranted) {
                Toast.makeText(
                    this,
                    "Se necesitan permisos de Bluetooth para usar la aplicacion",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent?.action
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        try {
                            bluetoothDevicesList.add(device.name)
                            bluetoothDevicesMACAddress[device.name] = device.address
                        } catch (e: SecurityException) {
                            Log.e(null, "Error de permisos", e)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            Toast.makeText(this, "Bluetooth no disponible", Toast.LENGTH_LONG).show()
            finish()
            return
        } else {
            btManager = getSystemService(BluetoothManager::class.java)
            btAdapter = btManager.adapter
        }

        deviceAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, bluetoothDevicesList)
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        navBar = findViewById(R.id.bottomNavigationView)
        searchDevicesButton = findViewById(R.id.idButtonSearchBluetooth)
        devicesDropDownMenu = findViewById(R.id.idBluetoothDevicesList)
        connectButton = findViewById(R.id.idConnectBluetooth)
        alarmButton = findViewById(R.id.idButtonAlarm)
        disconnectButton = findViewById(R.id.idButtonDisconnect)
        normaText = findViewById(R.id.idNormaText)

        devicesDropDownMenu.adapter = deviceAdapter

        searchDevicesButton.setOnClickListener {
            searchDevices()
        }

        connectButton.setOnClickListener {
            connectToDevice()
        }

        alarmButton.setOnClickListener {
            switchAlarm()
        }

        disconnectButton.setOnClickListener {
            if (btSocket != null) {
                try {
                    btSocket!!.close()
                } catch (e: IOException) {
                    Log.e(null, "Error al cerrar el socket", e)
                }
            }
        }

        devicesDropDownMenu.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedBluetoothDevice = bluetoothDevicesList[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (connectedThread != null) {
            connectedThread!!.close()
        }
        unregisterReceiver(receiver)
    }

    private fun searchDevices() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        ) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (!btAdapter.isEnabled) {
                val btEnableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                activityResultLauncher.launch(btEnableIntent)
            } else {
                val pairedDevices = btAdapter.bondedDevices
                if (pairedDevices.isNotEmpty()) {
                    bluetoothDevicesList.clear()
                    for (device in pairedDevices) {
                        bluetoothDevicesList.add(device.name)
                        bluetoothDevicesMACAddress[device.name] = device.address
                    }
                    deviceAdapter.notifyDataSetChanged()
                }
                btAdapter.startDiscovery()
            }
        }
    }

    private fun connectToDevice() {
        if (selectedBluetoothDevice == null) {
            Toast.makeText(this, "Seleccione un dispositivo HC-05", Toast.LENGTH_SHORT).show()
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }

            btAdapter.cancelDiscovery()
            val btDevice =
                btAdapter.getRemoteDevice(bluetoothDevicesMACAddress[selectedBluetoothDevice])

            btSocket = btDevice.createRfcommSocketToServiceRecord(BT_MODULE_UUID)
            if (btSocket != null) {
                thread {
                    try {
                        btSocket!!.connect()
                        connectedThread = ConnectedThread(btSocket!!, handler)
                        connectedThread!!.start()
                    } catch (e: IOException) {
                        Log.e(null, "No se pudo conectar a " + btDevice.name, e)
                    }
                }
            }
        }
    }

    private fun switchAlarm() {
        if (connectedThread != null) {
            connectedThread!!.write("a".toByteArray())
        }
    }
}