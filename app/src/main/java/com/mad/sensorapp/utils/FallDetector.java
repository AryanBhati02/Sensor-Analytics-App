package com.mad.sensorapp.utils;

import android.hardware.SensorEvent;

/**
 * Detects free-fall + impact pattern using accelerometer.
 * Free-fall: total acceleration near 0g for >= 100ms
 * Impact: spike to >= 3g within 500ms after free-fall
 */
public class FallDetector {

    public interface OnFallListener {
        void onFallDetected();
    }

    private static final float FREE_FALL_THRESHOLD = 0.5f;  // g-force below this = free fall
    private static final float IMPACT_THRESHOLD    = 3.0f;  // g-force above this = impact
    private static final long  FREE_FALL_MIN_MS    = 80L;
    private static final long  IMPACT_WINDOW_MS    = 500L;

    private final OnFallListener listener;
    private long freeFallStartTime = -1;
    private boolean freeFallDetected = false;

    public FallDetector(OnFallListener listener) {
        this.listener = listener;
    }

    public void onSensorChanged(SensorEvent event) {
        float gX = event.values[0] / android.hardware.SensorManager.GRAVITY_EARTH;
        float gY = event.values[1] / android.hardware.SensorManager.GRAVITY_EARTH;
        float gZ = event.values[2] / android.hardware.SensorManager.GRAVITY_EARTH;
        double totalG = Math.sqrt(gX * gX + gY * gY + gZ * gZ);
        long now = System.currentTimeMillis();

        if (totalG < FREE_FALL_THRESHOLD) {
            if (freeFallStartTime == -1) freeFallStartTime = now;
            if (!freeFallDetected && (now - freeFallStartTime) >= FREE_FALL_MIN_MS) {
                freeFallDetected = true;
            }
        } else {
            if (freeFallDetected) {
                if (totalG >= IMPACT_THRESHOLD) {
                    if (listener != null) listener.onFallDetected();
                }
                // Reset after impact window
                if (now - freeFallStartTime > IMPACT_WINDOW_MS) {
                    reset();
                }
            } else {
                freeFallStartTime = -1;
            }
        }
    }

    private void reset() {
        freeFallStartTime = -1;
        freeFallDetected  = false;
    }
}
