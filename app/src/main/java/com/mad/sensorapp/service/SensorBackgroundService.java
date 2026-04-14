package com.mad.sensorapp.service;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.hardware.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import com.mad.sensorapp.MainActivity;
import com.mad.sensorapp.R;

/**
 * Foreground service that keeps sensor monitoring alive when app is in background.
 * Background Sensor Monitoring.
 */
public class SensorBackgroundService extends Service implements SensorEventListener {

    private static final String CHANNEL_ID = "sensor_bg_channel";
    private static final int    NOTIF_ID   = 1001;

    private SensorManager sensorManager;
    private Sensor accel, light, prox;

    // Latest readings (broadcast-ready)
    private float lastAccelX, lastAccelY, lastAccelZ;
    private float lastLight;
    private float lastProximity;

    // Notification update throttle
    private final Handler notifHandler = new Handler(Looper.getMainLooper());
    private final Runnable notifUpdater = this::updateNotification;
    private static final long NOTIF_UPDATE_MS = 1000L;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Initialising sensors…"));

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        prox  = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        if (accel != null) sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);
        if (light != null) sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
        if (prox  != null) sensorManager.registerListener(this, prox,  SensorManager.SENSOR_DELAY_NORMAL);

        notifHandler.postDelayed(notifUpdater, NOTIF_UPDATE_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        notifHandler.removeCallbacks(notifUpdater);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                lastAccelX = event.values[0];
                lastAccelY = event.values[1];
                lastAccelZ = event.values[2];
                break;
            case Sensor.TYPE_LIGHT:
                lastLight = event.values[0];
                break;
            case Sensor.TYPE_PROXIMITY:
                lastProximity = event.values[0];
                break;
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ── Notification ──────────────────────────────────────────────────────────
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Sensor Monitor",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Live sensor data in background");
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sensor_notif)
                .setContentTitle("SensorPro — Live")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification() {
        String text = String.format("G:%.1f  L:%.0flx  P:%.0fcm",
                Math.sqrt(lastAccelX*lastAccelX + lastAccelY*lastAccelY + lastAccelZ*lastAccelZ),
                lastLight, lastProximity);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(text));
        notifHandler.postDelayed(notifUpdater, NOTIF_UPDATE_MS);
    }
}
