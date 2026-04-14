package com.mad.sensorapp;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tvAccelerometer, tvLight, tvProximity;
    private SensorManager sensorManager;
    private Sensor accelerometer, lightSensor, proximity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Link Java variables to XML IDs
        tvAccelerometer = findViewById(R.id.tvAccelerometer);
        tvLight = findViewById(R.id.tvLight);
        tvProximity = findViewById(R.id.tvProximity);

        // Initialize SensorManager
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register listeners when app is in foreground
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        if (lightSensor != null) sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        if (proximity != null) sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister to save battery when app is closed/minimized
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            tvAccelerometer.setText("X: " + event.values[0] + "\nY: " + event.values[1] + "\nZ: " + event.values[2]);
        } else if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            tvLight.setText(event.values[0] + " lx");
        } else if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            tvProximity.setText(event.values[0] + " cm");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for this assignment
    }
}