package com.example.abuelitometro.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.abuelitometro.R;
import com.example.abuelitometro.services.BluetoothService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_CONNECT_PERMISSION = 3;
    private static final int REQUEST_FINE_LOCATION_PERMISSION = 2;

    private ActivityResultLauncher<Intent> btLauncher;
    private BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;
    private BluetoothDevice btDevice;
    private OutputStream outputStream;
    private ArrayList<String> btDeviceNames = new ArrayList<>();
    private ArrayList<String> spinnerDeviceNames = new ArrayList<>();
    private ArrayAdapter<String> deviceAdapter;

    Button searchButton, connectButton, sendLetterButton;
    Spinner spinnerDeviceList;
    BottomNavigationView bottomNavigationView;
    Menu menu;
    MenuItem homeMenuItem, graphMenuItem;

    private InputStream inputStream;
    private Thread receiveThread;
    private boolean isConnected = false;

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    private BluetoothService bluetoothService;
    private boolean isBound = false;
    private boolean isAlertActive = false;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.BluetoothBinder binder = (BluetoothService.BluetoothBinder) service;
            bluetoothService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bluetoothService = null;
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        checkAndRequestPermissions();

        btAdapter = BluetoothAdapter.getDefaultAdapter();

        btLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        showToast("Bluetooth activado");
                        searchDevices();
                    } else {
                        showToast("Bluetooth no activado");
                    }
                }
        );

        searchButton = findViewById(R.id.searchButton);
        connectButton = findViewById(R.id.connectButton);
        spinnerDeviceList = findViewById(R.id.deviceList);
        sendLetterButton = findViewById(R.id.sendLetterButton);
        sendLetterButton.setVisibility(View.GONE);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        menu = bottomNavigationView.getMenu();
        graphMenuItem = menu.findItem(R.id.nav_graph);
        graphMenuItem.setEnabled(false);

        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spinnerDeviceNames);
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDeviceList.setAdapter(deviceAdapter);

        searchButton.setOnClickListener(v -> searchDevices());

        connectButton.setOnClickListener(v -> connectionHandler());
        sendLetterButton.setOnClickListener(v -> {
            if (isConnected) {
                sendLetterToDevice();
            } else {
                showToast("No está conectado a un dispositivo.");
            }
        });


        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                return true;
            } else if (itemId == R.id.nav_graph) {
                Intent intent = new Intent(MainActivity.this, SecondActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });

        Intent serviceIntent = new Intent(this, BluetoothService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        IntentFilter filter = new IntentFilter(BluetoothService.ACTION_DATA_RECEIVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String data = intent.getStringExtra(BluetoothService.EXTRA_DATA);
            Log.d(TAG, "Received data: " + data);
            if(data.toLowerCase().contains("inicio")) {
                sendLetterButton.setText("Desactivar Alerta de Caída");
            } else if (data.toLowerCase().contains("desactivo")) {
                sendLetterButton.setText("Activar Alerta de Caída");
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
    }

    private void requestBluetoothActivation() {
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            btLauncher.launch(enableBtIntent);
        }
    }

    @SuppressLint("MissingPermission")
    private void searchDevices() {
        if (!btAdapter.isEnabled()) {
            requestBluetoothActivation();
            return;
        }

        spinnerDeviceNames.clear();
        deviceAdapter.notifyDataSetChanged();

        // Agrega dispositivos ya emparejados
        for (BluetoothDevice pairedDevice : btAdapter.getBondedDevices()) {
            spinnerDeviceNames.add(pairedDevice.getName());
            btDeviceNames.add(pairedDevice.getName() + "\n" + pairedDevice.getAddress());
        }
        deviceAdapter.notifyDataSetChanged();

        // Inicia la búsqueda de dispositivos Bluetooth
        btAdapter.startDiscovery();

        showToast("Buscando...");

        // Configura un receptor para manejar los dispositivos encontrados
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "TEST");

                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    if (device != null) {
                        spinnerDeviceNames.add(device.getName());
                        btDeviceNames.add(device.getName() + "\n" + device.getAddress());
                        deviceAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Dispositivo encontrado: " + device.getName() + " [" + device.getAddress() + "]");
                    }

                }

            }
        }, new IntentFilter(BluetoothDevice.ACTION_FOUND));

    }


    private void connectionHandler() {
        if (isConnected) {
            showToast("Desconectando...");
            new Thread(() -> {
                boolean disconnected = bluetoothService.disconnect();

                runOnUiThread(() -> {
                    if (disconnected) {
                        isConnected = false;
                        connectButton.setText("Conectar");
                        sendLetterButton.setVisibility(View.GONE);
                        graphMenuItem.setEnabled(false);
                    } else {
                        showToast("Error al desconectar.");
                    }
                });
            }).start();
        } else {
            showToast("Conectando...");
            new Thread(() -> {
                boolean isConnectedNow = connectToDevice();

                runOnUiThread(() -> {
                    if (isConnectedNow) {
                        isConnected = true;
                        connectButton.setText("Desconectar");
                        sendLetterButton.setVisibility(View.VISIBLE);
                        graphMenuItem.setEnabled(true);
                    } else {
                        connectButton.setText("Conectar");
                        showToast("Error al conectar.");
                    }
                });
            }).start();
        }
    }

    @SuppressLint("MissingPermission")
    private boolean connectToDevice() {
        int selectedIndex = spinnerDeviceList.getSelectedItemPosition();
        if (selectedIndex >= 0 && selectedIndex < spinnerDeviceNames.size()) {
            String selectedDeviceInfo = btDeviceNames.get(selectedIndex);
            String deviceAddress = selectedDeviceInfo.split("\n")[1];
            Log.d(TAG, "MAC Seleccionada: " + deviceAddress);
            btDevice = btAdapter.getRemoteDevice(deviceAddress);
            Log.d(TAG, "Device Seleccionado: " + btDevice);

            boolean connected = bluetoothService.connect(btDevice);
            if(connected) {
                runOnUiThread(() -> showToast("Conectado"));
                Log.d(TAG, "Conectado a la MAC: " + deviceAddress);
                return true;
            } else {
                runOnUiThread(() -> showToast("Error de conexión"));
                Log.e(TAG, "No Conectado a la MAC: " + deviceAddress);
                return false;
            }
        } else {
            showToast("Seleccione un dispositivo para conectar");
            return false;
        }
    }

    private void sendLetterToDevice() {
        if(!isAlertActive) {
            bluetoothService.sendData((byte) 'a');
            Log.d(TAG, "Alerta activada");
            sendLetterButton.setText("Desactivar Alerta de Caída");
            isAlertActive = true;
        } else {
            bluetoothService.sendData((byte) 'a');
            Log.d(TAG, "Alerta desactivada");
            sendLetterButton.setText("Activar Alerta de Caída");
            isAlertActive = false;
        }
    }

    private void checkAndRequestPermissions() {
        // Lista de permisos requeridos en Android 12+
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // Android 12 y superior
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {  // Android 11 e inferiores
            if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissionsNeeded.isEmpty()) {
            requestPermissions(permissionsNeeded.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    showToast("La aplicación no funciona sin permisos de Bluetooth y ubicación");
                    searchButton.setEnabled(false);
                    connectButton.setEnabled(false);
                    return;
                }
            }
        }
    }

    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

}