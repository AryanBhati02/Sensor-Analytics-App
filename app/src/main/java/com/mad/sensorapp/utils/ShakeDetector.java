package com.mad.sensorapp.utils;

import android.hardware.SensorEvent;

/**
 * Feature 5 – Shake Detector
 * Lower threshold (2.0g) for better sensitivity.
 */
public class ShakeDetector {

    public interface OnShakeListener {
        void onShake(int shakeCount);
    }

    // Lowered from 2.0 → 1.8 for better real-world sensitivity
    private static final float SHAKE_THRESHOLD_GRAVITY = 1.8f;
    private static final int   SHAKE_SLOP_TIME_MS      = 400;
    private static final int   SHAKE_COUNT_RESET_MS    = 3000;

    private final OnShakeListener listener;
    private long  shakeTimestamp;
    private int   shakeCount;

    public ShakeDetector(OnShakeListener listener) {
        this.listener = listener;
    }

    public void onSensorChanged(SensorEvent event) {
        float gX = event.values[0] / android.hardware.SensorManager.GRAVITY_EARTH;
        float gY = event.values[1] / android.hardware.SensorManager.GRAVITY_EARTH;
        float gZ = event.values[2] / android.hardware.SensorManager.GRAVITY_EARTH;
        double gForce = Math.sqrt(gX*gX + gY*gY + gZ*gZ);

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            long now = System.currentTimeMillis();
            if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) return;
            if (shakeTimestamp + SHAKE_COUNT_RESET_MS < now) shakeCount = 0;
            shakeTimestamp = now;
            shakeCount++;
            if (listener != null) listener.onShake(shakeCount);
        }
    }
}
