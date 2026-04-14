package com.mad.sensorapp.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.*;
import android.os.IBinder;
import android.view.*;
import android.widget.TextView;
import com.mad.sensorapp.R;

/**
 * Displays a compact draggable floating HUD showing live sensor values
 */
public class FloatingHudService extends Service implements SensorEventListener {

    private WindowManager windowManager;
    private View hudView;
    private SensorManager sensorManager;
    private Sensor accel, light;

    private TextView tvHudAccel, tvHudLight;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        hudView = LayoutInflater.from(this).inflate(R.layout.layout_floating_hud, null);
        tvHudAccel = hudView.findViewById(R.id.tvHudAccel);
        tvHudLight = hudView.findViewById(R.id.tvHudLight);

        int layoutFlag = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 16; params.y = 200;

        windowManager.addView(hudView, params);

        // Make HUD draggable
        hudView.setOnTouchListener(new View.OnTouchListener() {
            int initX, initY; float initTouchX, initTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x; initY = params.y;
                        initTouchX = e.getRawX(); initTouchY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initX + (int)(e.getRawX() - initTouchX);
                        params.y = initY + (int)(e.getRawY() - initTouchY);
                        windowManager.updateViewLayout(hudView, params);
                        return true;
                }
                return false;
            }
        });

        // Long press to dismiss HUD
        hudView.setOnLongClickListener(v -> {
            stopSelf();
            return true;
        });

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (accel != null) sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);
        if (light != null) sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (hudView != null) windowManager.removeView(hudView);
        sensorManager.unregisterListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float mag = (float) Math.sqrt(
                    event.values[0]*event.values[0] +
                    event.values[1]*event.values[1] +
                    event.values[2]*event.values[2]);
            tvHudAccel.setText(String.format("G %.2f", mag));
        } else if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            tvHudLight.setText(String.format("☀%.0f", event.values[0]));
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
