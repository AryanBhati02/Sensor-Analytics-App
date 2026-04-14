package com.mad.sensorapp.utils;

import android.hardware.SensorEvent;
import android.os.Handler;
import android.os.Looper;
import java.util.Random;

/**
 * Feature 40 – Sensor Simulator
 * Generates realistic mock sensor events for emulator testing.
 * Simulates: walking, running, stationary, driving patterns.
 */
public class SensorSimulator {

    public interface SimulatorListener {
        void onSimulatedAccelerometer(float x, float y, float z);
        void onSimulatedLight(float lux);
        void onSimulatedProximity(float cm);
        void onSimulatedStep();
    }

    public enum SimMode {
        STATIONARY("🧍 Stationary"),
        WALKING("🚶 Walking"),
        RUNNING("🏃 Running"),
        DRIVING("🚗 Driving"),
        SHAKE("📳 Shaking"),
        FREEFALL("⬇️ Free Fall");

        public final String label;
        SimMode(String label) { this.label = label; }
    }

    private final SimulatorListener listener;
    private final Handler           handler = new Handler(Looper.getMainLooper());
    private final Random            rng     = new Random();
    private volatile boolean        running = false;
    private SimMode                 mode    = SimMode.WALKING;

    // Simulation state
    private float walkPhase   = 0f;
    private int   stepCounter = 0;
    private float lightVal    = 300f;
    private float proxVal     = 10f;
    private int   tick        = 0;

    public SensorSimulator(SimulatorListener listener) {
        this.listener = listener;
    }

    public void start(SimMode mode) {
        this.mode = mode;
        running   = true;
        tick      = 0;
        scheduleNext();
    }

    public void setMode(SimMode mode) { this.mode = mode; }

    public void stop() { running = false; handler.removeCallbacksAndMessages(null); }

    private void scheduleNext() {
        if (!running) return;
        handler.postDelayed(this::simulate, 50); // 20Hz
    }

    private void simulate() {
        if (!running) return;
        tick++;

        float x = 0, y = 0, z = 9.81f;
        float noiseScale = 0.05f;

        switch (mode) {
            case STATIONARY:
                x = noise(0f, 0.08f);
                y = noise(0f, 0.08f);
                z = 9.81f + noise(0f, 0.05f);
                // Slow light drift
                lightVal = 300f + (float)(Math.sin(tick * 0.01) * 30);
                proxVal  = 10f;
                break;

            case WALKING: {
                walkPhase += 0.25f;
                float swing = (float)(Math.sin(walkPhase) * 3.5f);
                float bob   = (float)(Math.abs(Math.sin(walkPhase * 2)) * 2.0f);
                x = swing  + noise(0f, 0.3f);
                y = bob    + noise(0f, 0.3f);
                z = 9.81f  + noise(0f, 0.2f);
                // Step detection: every ~half-cycle
                if (walkPhase % (float)(Math.PI) < 0.3f) { stepCounter++; listener.onSimulatedStep(); }
                lightVal = 500f + noise(0f, 50f);
                proxVal  = 10f;
                break;
            }

            case RUNNING: {
                walkPhase += 0.5f;
                float swing = (float)(Math.sin(walkPhase) * 7f);
                float bob   = (float)(Math.abs(Math.sin(walkPhase * 2)) * 5f);
                x = swing  + noise(0f, 0.8f);
                y = bob    + noise(0f, 0.8f);
                z = 9.81f  + noise(0f, 0.5f);
                if (walkPhase % (float)(Math.PI) < 0.55f) { stepCounter++; listener.onSimulatedStep(); }
                lightVal = 600f + noise(0f, 80f);
                break;
            }

            case DRIVING: {
                // Low-frequency rumble
                float rumble = (float)(Math.sin(tick * 0.08) * 1.2f + Math.sin(tick * 0.19) * 0.7f);
                x = rumble + noise(0f, 0.15f);
                y = noise(0f, 0.2f);
                z = 9.81f + noise(0f, 0.1f);
                // Occasional bump
                if (tick % 80 == 0) { x += 3f; y += 2f; }
                lightVal = 800f + noise(0f, 100f);
                break;
            }

            case SHAKE:
                x = noise(0f, 12f);
                y = noise(0f, 12f);
                z = noise(9.81f, 12f);
                break;

            case FREEFALL:
                x = noise(0f, 0.1f);
                y = noise(0f, 0.1f);
                z = noise(0f, 0.1f); // near-zero g
                // Impact after 40 ticks
                if (tick > 40) { z = 25f; mode = SimMode.STATIONARY; }
                break;
        }

        listener.onSimulatedAccelerometer(x, y, z);
        listener.onSimulatedLight(Math.max(0, lightVal));

        // Proximity oscillation
        proxVal = (float)(5f + Math.sin(tick * 0.03) * 4.5f);
        listener.onSimulatedProximity(Math.max(0, proxVal));

        scheduleNext();
    }

    private float noise(float base, float amplitude) {
        return base + (rng.nextFloat() * 2 - 1) * amplitude;
    }

    public SimMode getMode()    { return mode; }
    public int    getSteps()    { return stepCounter; }
    public boolean isRunning()  { return running; }
}
