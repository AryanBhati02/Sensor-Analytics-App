package com.mad.sensorapp.service;

import android.content.Context;
import android.hardware.*;
import android.os.*;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.mad.sensorapp.data.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Scheduled Recording via WorkManager.
 * Records sensor data for a fixed duration, then saves to Room DB.
 */
public class ScheduledRecordingWorker extends Worker {

    public static final String KEY_DURATION_SEC = "duration_sec";
    public static final String KEY_SESSION_ID   = "session_id";
    public static final String WORK_TAG         = "scheduled_recording";
    private static final int   DEFAULT_DURATION = 60;

    // Sensor registration MUST happen on Main thread
    private Handler            mainHandler;
    private SensorManager      sensorManager;
    private SensorDatabase     db;
    private String             sessionId;
    private final List<SensorReading> buffer = Collections.synchronizedList(new ArrayList<>());

    // Latch to block Worker thread until recording finishes
    private final CountDownLatch latch = new CountDownLatch(1);

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            String type;
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER: type = "ACCELEROMETER"; break;
                case Sensor.TYPE_LIGHT:         type = "LIGHT";         break;
                case Sensor.TYPE_PROXIMITY:     type = "PROXIMITY";     break;
                default: return;
            }
            float y = event.values.length > 1 ? event.values[1] : 0f;
            float z = event.values.length > 2 ? event.values[2] : 0f;
            buffer.add(new SensorReading(type, event.values[0], y, z,
                    System.currentTimeMillis(), sessionId));
        }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    public ScheduledRecordingWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        int durationSec = getInputData().getInt(KEY_DURATION_SEC, DEFAULT_DURATION);
        sessionId = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + "_sched";
        db = SensorDatabase.getInstance(getApplicationContext());

        // Register sensors on main thread (required by Android)
        mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            sensorManager = (SensorManager) getApplicationContext()
                    .getSystemService(Context.SENSOR_SERVICE);
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            Sensor prox  = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

            if (accel != null) sensorManager.registerListener(sensorListener, accel,
                    SensorManager.SENSOR_DELAY_NORMAL);
            if (light != null) sensorManager.registerListener(sensorListener, light,
                    SensorManager.SENSOR_DELAY_NORMAL);
            if (prox  != null) sensorManager.registerListener(sensorListener, prox,
                    SensorManager.SENSOR_DELAY_NORMAL);

            // Unregister after duration
            mainHandler.postDelayed(() -> {
                sensorManager.unregisterListener(sensorListener);
                latch.countDown();
            }, durationSec * 1000L);
        });

        // Block worker thread until recording finishes
        try {
            latch.await(durationSec + 15L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure();
        }

        // Batch-insert all collected readings
        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(() -> {
            for (SensorReading r : buffer) db.sensorDao().insert(r);
        });
        exec.shutdown();
        try { exec.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        return Result.success(new Data.Builder()
                .putString(KEY_SESSION_ID, sessionId)
                .putInt("readings", buffer.size())
                .build());
    }
}
