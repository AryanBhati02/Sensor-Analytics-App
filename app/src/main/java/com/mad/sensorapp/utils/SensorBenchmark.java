package com.mad.sensorapp.utils;

import android.content.Context;
import android.hardware.*;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/**
 * Feature 38 – Sensor Benchmark Mode
 * Measures max sampling rate for each sensor over 3 seconds each.
 * All sensor registration happens on Main thread as required by Android.
 */
public class SensorBenchmark {

    public static class BenchmarkResult {
        public final String sensorName;
        public final float  achievedHz;
        public final int    totalSamples;
        public final long   durationMs;
        public final String rating;

        BenchmarkResult(String name, float hz, int samples, long ms) {
            sensorName   = name;
            achievedHz   = hz;
            totalSamples = samples;
            durationMs   = ms;
            rating       = ratingLabel(hz);
        }
    }

    public interface BenchmarkCallback {
        void onProgress(String sensorName, int samplesCollected);
        void onComplete(List<BenchmarkResult> results);
        void onError(String message);
    }

    private static final long   BENCH_MS    = 3000L;
    private static final String[] NAMES     = {"Accelerometer", "Gyroscope", "Light", "Magnetometer"};
    private static final int[]    TYPES     = {
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_MAGNETIC_FIELD
    };

    private final Context          ctx;
    private final BenchmarkCallback callback;
    private final Handler          mainHandler = new Handler(Looper.getMainLooper());
    private final List<BenchmarkResult> results = new ArrayList<>();

    private SensorManager      sm;
    private int                currentIdx   = 0;
    private volatile int       sampleCount  = 0;
    private volatile boolean   measuring    = false;

    private final SensorEventListener listener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            if (measuring) sampleCount++;
        }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    public SensorBenchmark(Context context, BenchmarkCallback callback) {
        this.ctx      = context.getApplicationContext();
        this.callback = callback;
    }

    /** Start benchmark – MUST be called from any thread; internally uses Main thread. */
    public void start() {
        results.clear();
        currentIdx = 0;
        mainHandler.post(this::benchNext);
    }

    private void benchNext() {
        if (currentIdx >= TYPES.length) {
            // All done
            callback.onComplete(new ArrayList<>(results));
            return;
        }

        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sm.getDefaultSensor(TYPES[currentIdx]);
        final String name = NAMES[currentIdx];

        if (sensor == null) {
            results.add(new BenchmarkResult(name, 0f, 0, 0));
            currentIdx++;
            mainHandler.postDelayed(this::benchNext, 200);
            return;
        }

        sampleCount = 0;
        measuring   = true;
        long startMs = System.currentTimeMillis();

        // Register at fastest rate – on main thread
        try {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST);
        } catch (Exception e) {
            // Fallback to GAME delay if FASTEST fails (e.g. permission issues on some ROMs)
            try {
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME);
            } catch (Exception e2) {
                results.add(new BenchmarkResult(name, 0f, 0, 0));
                currentIdx++;
                mainHandler.postDelayed(this::benchNext, 200);
                return;
            }
        }

        mainHandler.postDelayed(() -> {
            measuring = false;
            sm.unregisterListener(listener);
            long elapsed = System.currentTimeMillis() - startMs;
            float hz = elapsed > 0 ? sampleCount / (elapsed / 1000f) : 0f;
            results.add(new BenchmarkResult(name, hz, sampleCount, elapsed));
            callback.onProgress(name, sampleCount);
            currentIdx++;
            mainHandler.postDelayed(this::benchNext, 300); // short gap between sensors
        }, BENCH_MS);
    }

    public static String ratingLabel(float hz) {
        if (hz > 200) return "Excellent ⭐⭐⭐";
        if (hz > 50)  return "Good ⭐⭐";
        if (hz > 10)  return "Average ⭐";
        if (hz > 0)   return "Low";
        return "Not available";
    }
}
