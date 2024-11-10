package com.example.abuelitometro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.IOException;
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
    private boolean isConnected = false;  // Variable para mantener el estado de conexión

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

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
        bottomNavigationView.setSelectedItemId(R.id.nav_home);
        menu = bottomNavigationView.getMenu();
        graphMenuItem = menu.findItem(R.id.nav_graph);
        graphMenuItem.setEnabled(false);

        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spinnerDeviceNames);
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDeviceList.setAdapter(deviceAdapter);

        searchButton.setOnClickListener(v -> searchDevices());

        connectButton.setOnClickListener(v -> connectionHandler());
        sendLetterButton.setOnClickListener(v -> {
            if (btSocket != null && btSocket.isConnected()) {
                sendLetterToDevice();
            } else {
                showToast("No está conectado a un dispositivo.");
            }
        });


        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                // Permanecer en MainActivity
                return true;
            } else if (itemId == R.id.nav_graph) {
                // Ir a SecondActivity
                Intent intent = new Intent(MainActivity.this, SecondActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });
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

        // Configura un receptor para manejar los dispositivos encontrados - No estoy seguro de que funcione
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
                boolean disconnected = disconnectFromDevice();

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

    private boolean disconnectFromDevice() {
        try {
            btSocket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }


/*    private void connectionHandler() {
        showToast("Conectando...");
        new Thread(() -> {
            boolean isConnected = connectToDevice();

            runOnUiThread(() -> {
                if (isConnected) {
                    connectButton.setText("Desconectar");
                    sendLetterButton.setVisibility(View.VISIBLE);
                    graphMenuItem.setEnabled(true);
                } else {
                    connectButton.setText("Conectar");
                }
            });
        }).start();
    }*/

    @SuppressLint("MissingPermission")
    private boolean connectToDevice() {
        int selectedIndex = spinnerDeviceList.getSelectedItemPosition();
        if (selectedIndex >= 0 && selectedIndex < spinnerDeviceNames.size()) {
            String selectedDeviceInfo = btDeviceNames.get(selectedIndex);
            String deviceAddress = selectedDeviceInfo.split("\n")[1];
            Log.d(TAG, "MAC Seleccionada: " + deviceAddress);
            btDevice = btAdapter.getRemoteDevice(deviceAddress);
            Log.d(TAG, "Device Seleccionado: " + btDevice);

            // Cancelar búsqueda si está en proceso
            if (btAdapter.isDiscovering()) {
                btAdapter.cancelDiscovery();
                Log.d(TAG, "Cancelo busqueda");
            }

            try {
                btSocket = btDevice.createRfcommSocketToServiceRecord(btDevice.getUuids()[0].getUuid());
                btSocket.connect();
                outputStream = btSocket.getOutputStream();
                inputStream = btSocket.getInputStream();
                startReceivingData();
                runOnUiThread(() -> showToast("Conectado"));
                Log.d(TAG, "Conectado a la MAC: " + deviceAddress);
                return true;
            } catch (IOException e) {
                runOnUiThread(() -> showToast("Error de conexión"));
                //showToast("Error de conexión");
                Log.e(TAG, "No Conectado a la MAC: " + deviceAddress);
                return false;
            }
        } else {
            showToast("Seleccione un dispositivo para conectar");
            return false;
        }
    }

    private void sendLetterToDevice() {
        try {
            if (outputStream == null) {
                outputStream = btSocket.getOutputStream();
            }
            outputStream.write((byte) 'a');
            outputStream.flush();
            Log.d(TAG, "Letra enviada: " + 'a');
        } catch (IOException e) {
            Log.e(TAG, "Error al enviar la letra", e);
        }
    }

    private void startReceivingData() {
        receiveThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        // Por ahora solo los logueo -> Hay que enviarlos a la otra actividad.
                        String receivedData = new String(buffer, 0, bytes);
                        Log.d(TAG, "Datos recibidos: " + receivedData);
                        runOnUiThread(() -> updateUIWithReceivedData(receivedData));  // Actualiza la UI
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error al recibir datos", e);
                    break;
                }
            }
        });
        receiveThread.start();
    }

    private void updateUIWithReceivedData(String data) {
        TextView receivedDataTextView = findViewById(R.id.receivedDataTextView);
        receivedDataTextView.setText(data);
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