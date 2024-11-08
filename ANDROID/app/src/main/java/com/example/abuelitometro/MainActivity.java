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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
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
    //private ConnectedThread connectionBt;
    private ArrayList<String> deviceNames = new ArrayList<>();
    private ArrayAdapter<String> deviceAdapter;  // Adaptador para mostrar dispositivos en el Spinner

    Button searchButton, connectButton;
    Spinner spinnerDeviceList;

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        checkBluetoothPermissions();

        btAdapter = BluetoothAdapter.getDefaultAdapter();

        btLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        showToast("Bluetooth activado");
                    } else {
                        showToast("Bluetooth no activado");
                    }
                }
        );


        searchButton = findViewById(R.id.searchButton);
        connectButton = findViewById(R.id.connectButton);
        spinnerDeviceList = findViewById(R.id.deviceList);

        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDeviceList.setAdapter(deviceAdapter);

        searchButton.setOnClickListener(v -> checkPermissionsAndSearchDevices());
        connectButton.setOnClickListener(v -> connectToDevice());

        requestBluetoothActivation();
    }

    // Solicita al usuario activar Bluetooth si no está habilitado
    private void requestBluetoothActivation() {
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            btLauncher.launch(enableBtIntent);
        }
    }

    @SuppressLint("MissingPermission")
    private void searchDevices() {
        if (btAdapter != null && btAdapter.isEnabled()) {
            deviceNames.clear();
            deviceAdapter.notifyDataSetChanged();

            // Agrega dispositivos ya emparejados
            for (BluetoothDevice pairedDevice : btAdapter.getBondedDevices()) {
                deviceNames.add(pairedDevice.getName());
            }
            deviceAdapter.notifyDataSetChanged();

            // Inicia la búsqueda de dispositivos Bluetooth
            btAdapter.startDiscovery();

            showToast("Iniciando búsqueda de nuevos dispositivos");

            // Configura un receptor para manejar los dispositivos encontrados
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "TEST");

                    String action = intent.getAction();
                    if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                        if (device != null) {
                            deviceNames.add(device.getName() + "\n" + device.getAddress());  // Agrega el dispositivo a la lista
                            deviceAdapter.notifyDataSetChanged();
                            Log.d(TAG, "Dispositivo encontrado: " + device.getName() + " [" + device.getAddress() + "]");
                        }

                    }

                }
            }, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        } else {
            showToast("Bluetooth no está habilitado");
        }
    }

    // Intenta conectar al dispositivo seleccionado en el spinner
    @SuppressLint("MissingPermission")
    private void connectToDevice() {
        int selectedIndex = spinnerDeviceList.getSelectedItemPosition();
        if (selectedIndex >= 0 && selectedIndex < deviceNames.size()) {
            String selectedDeviceInfo = deviceNames.get(selectedIndex);
            String deviceAddress = selectedDeviceInfo.split("\n")[1];  // Extrae la dirección MAC
            btDevice = btAdapter.getRemoteDevice(deviceAddress);  // Obtiene el dispositivo Bluetooth

            try {
                btSocket = btDevice.createRfcommSocketToServiceRecord(btDevice.getUuids()[0].getUuid());
                btSocket.connect();  // Intenta la conexión
                showToast("Conectado a " + btDevice.getName());
            } catch (IOException e) {
                showToast("Error de conexión");
            }
        } else {
            showToast("Seleccione un dispositivo para conectar");
        }
    }


    private void checkPermissions() {
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

        showToast("Valide permsisos");
    }

    private void checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_SCAN,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                }, 1);
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    private void checkPermissionsAndSearchDevices() {
        if (btAdapter != null && btAdapter.isEnabled()) {
            // Verifica si los permisos ya están concedidos
            if (hasBluetoothPermissions()) {
                searchDevices(); // Si ya tienes permisos, inicia la búsqueda
            } else {
                checkPermissions(); // Si no tienes permisos, solicita
                showToast("no tengo permisos, solicito");
            }
        } else {
            showToast("Bluetooth no está habilitado");
        }
    }

    // Método para verificar si los permisos ya están concedidos
    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }



    // Sobrescribe este método para manejar la respuesta del usuario al solicitar permisos
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    showToast("Permisos de Bluetooth y ubicación necesarios");
                    return;
                }
            }
            // Si todos los permisos fueron otorgados, inicia la búsqueda de dispositivos
            searchDevices();
        }
    }

    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }


}