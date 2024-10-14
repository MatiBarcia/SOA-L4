package com.soa.l4.tp2

import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class ConnectedThread(private var mmSocket: BluetoothSocket, private val handler: Handler) :Thread(){
    private var mmInStream:InputStream = mmSocket.inputStream
    private var mmOutStream:OutputStream = mmSocket.outputStream
    private var valueRead:String? = null

    override fun run() {
        val mmBuffer: ByteArray= ByteArray(1024)
        var numBytes:Int = 0

        while (true) {
            numBytes = try {
                mmInStream.read(mmBuffer)
            } catch(e:IOException) {
                Log.d(null, "No se pudo leer del dispositivo bluetooth", e)
                break
            }
            if(mmBuffer[numBytes] == "\n".toByte()) {
                handler.obtainMessage(Constants.MESSAGE_READ, numBytes,-1, mmBuffer).sendToTarget()
            }

        }
    }

    fun write(bytes:ByteArray) {
        try {
            mmOutStream.write(bytes)
        } catch(e:IOException) {
            Log.e(null, "No se pudo escribir el mensaje", e)

            val writeErrorMsg = handler.obtainMessage(Constants.MESSAGE_TOAST)
            val bundle = Bundle().apply {
                putString("toast", "No se pudo enviar el mensaje")
            }
            writeErrorMsg.data = bundle
            handler.sendMessage(writeErrorMsg)
            return
        }
    }

    fun close() {
        try {
            mmSocket.close()
        } catch (e:IOException) {
            Log.e(null, "No se pudo cerrar la conexion", e)
        }
    }
}