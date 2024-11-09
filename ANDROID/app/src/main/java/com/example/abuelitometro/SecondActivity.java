package com.example.abuelitometro;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class SecondActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private TextView sensorTitle;
    private TextView arduinoValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_graph);

        // Inicializar las vistas
        sensorTitle = findViewById(R.id.sensorTitle);  // TextView para mostrar el valor del acelerómetro
        arduinoValue = findViewById(R.id.arduinoValue);  // En caso de tener un valor de Arduino

        // Inicializar el SensorManager
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Obtener el acelerómetro del dispositivo
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // Registrar el listener para el sensor
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                // Permanecer en MainActivity
                Intent intent = new Intent(SecondActivity.this, MainActivity.class);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.nav_graph) {
                // Ir a SecondActivity
                return true;
            }
            return false;
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Verificar que es el acelerómetro el que ha cambiado
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Obtener las lecturas de los tres ejes
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            // Calcular la norma del vector (magnitud)
            float norm = (float) Math.sqrt(x * x + y * y + z * z);

            // Actualizar el TextView para mostrar el valor de la norma
            sensorTitle.setText(String.format("%.2f", norm));  // Actualiza el valor de la norma en el TextView
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No es necesario en este caso
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Desregistrar el listener cuando la actividad esté en pausa para ahorrar batería
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Volver a registrar el listener cuando la actividad se reanude
        if (sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }
}
