package com.mad.sensorapp.utils;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Lightweight accelerometer-based activity recognizer.
 * Uses variance of recent samples to classify motion state.
 */
public class ActivityRecognizer {

    public enum Activity {
        STATIONARY, WALKING, RUNNING, DRIVING, UNKNOWN
    }

    public interface OnActivityChangedListener {
        void onActivityChanged(Activity activity, float confidence);
    }

    private static final int   WINDOW_SIZE      = 50;   // ~2.5s at SENSOR_DELAY_NORMAL
    private static final float WALK_VAR_MIN     = 0.5f;
    private static final float WALK_VAR_MAX     = 5.0f;
    private static final float RUN_VAR_MIN      = 5.0f;
    private static final float DRIVE_VAR_MIN    = 0.1f;
    private static final float DRIVE_VAR_MAX    = 0.5f;
    private static final float STATIONARY_VAR   = 0.1f;

    private final Deque<Float> magnitudes = new ArrayDeque<>();
    private final OnActivityChangedListener listener;
    private Activity lastActivity = Activity.UNKNOWN;

    public ActivityRecognizer(OnActivityChangedListener listener) {
        this.listener = listener;
    }

    public void onSensorChanged(float x, float y, float z) {
        float mag = (float) Math.sqrt(x * x + y * y + z * z);
        magnitudes.addLast(mag);
        if (magnitudes.size() > WINDOW_SIZE) magnitudes.pollFirst();
        if (magnitudes.size() < WINDOW_SIZE / 2) return;

        float variance = computeVariance();
        Activity detected = classify(variance);
        float confidence   = computeConfidence(variance, detected);

        if (detected != lastActivity) {
            lastActivity = detected;
            if (listener != null) listener.onActivityChanged(detected, confidence);
        }
    }

    private float computeVariance() {
        float sum = 0, sumSq = 0;
        for (float v : magnitudes) { sum += v; sumSq += v * v; }
        int n = magnitudes.size();
        float mean = sum / n;
        return (sumSq / n) - (mean * mean);
    }

    private Activity classify(float variance) {
        if (variance < STATIONARY_VAR)                                  return Activity.STATIONARY;
        if (variance >= DRIVE_VAR_MIN && variance < DRIVE_VAR_MAX)      return Activity.DRIVING;
        if (variance >= WALK_VAR_MIN  && variance < WALK_VAR_MAX)       return Activity.WALKING;
        if (variance >= RUN_VAR_MIN)                                     return Activity.RUNNING;
        return Activity.UNKNOWN;
    }

    private float computeConfidence(float variance, Activity activity) {
        switch (activity) {
            case STATIONARY: return Math.max(0, 1f - variance / STATIONARY_VAR * 0.5f);
            case WALKING:    return Math.min(1f, (variance - WALK_VAR_MIN) / (WALK_VAR_MAX - WALK_VAR_MIN));
            case RUNNING:    return Math.min(1f, (variance - RUN_VAR_MIN) / 5f);
            default:         return 0.5f;
        }
    }

    public String getEmoji(Activity activity) {
        switch (activity) {
            case STATIONARY: return "🧍";
            case WALKING:    return "🚶";
            case RUNNING:    return "🏃";
            case DRIVING:    return "🚗";
            default:         return "❓";
        }
    }
}
