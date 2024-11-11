package com.example.abuelitometro.services;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";
    private final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final String ACTION_DATA_RECEIVED = "com.example.abuelitometro.ACTION_DATA_RECEIVED";
    public static final String EXTRA_DATA = "com.example.abuelitometro.EXTRA_DATA";

    private final IBinder binder = new BluetoothBinder();
    private BluetoothSocket btSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Thread receiveThread;

    public class BluetoothBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @SuppressLint("MissingPermission")
    public boolean connect(BluetoothDevice device) {
        try {
            btSocket = device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
            btSocket.connect();
            outputStream = btSocket.getOutputStream();
            inputStream = btSocket.getInputStream();
            receiveData();
            Log.d(TAG, "Connected to device: " + device.getAddress());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Connection failed", e);
            return false;
        }
    }

    public boolean disconnect() {
        try {
            btSocket.close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Desconexión fallida", e);
            return false;
        }
    }

    public void sendData(byte data) {
        try {
            if (outputStream != null) {
                outputStream.write(data);
                outputStream.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "Envio de data fallido", e);
        }
    }

    private void receiveData() {
        receiveThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String receivedData = new String(buffer, 0, bytes);
                        Intent intent = new Intent(ACTION_DATA_RECEIVED);
                        intent.putExtra(EXTRA_DATA, receivedData);
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Receiving data failed", e);
                    break;
                }
            }
        });
        receiveThread.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
    }
}
