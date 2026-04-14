package com.mad.sensorapp.utils;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import com.mad.sensorapp.data.SensorReading;

/**
 * Container class for sensor utility helpers.
 * Features: PocketDetector (35), StepCounterHelper (7), NoiseDetector (36), CsvExporter (4/31)
 */
public class SensorUtils {

    // ─────────────────────────────────────────────────────────────────────────
    //  Pocket Detector  (feature 35)  proximity near + light dark = in pocket
    // ─────────────────────────────────────────────────────────────────────────
    public static class PocketDetector {

        public interface OnPocketChangedListener {
            void onPocketStateChanged(boolean inPocket);
        }

        private static final float PROX_THRESHOLD  = 3.0f;
        private static final float LIGHT_THRESHOLD = 10.0f;

        private final OnPocketChangedListener listener;
        private float lastProximity = Float.MAX_VALUE;
        private float lastLight     = Float.MAX_VALUE;
        private boolean inPocket    = false;

        public PocketDetector(OnPocketChangedListener listener) { this.listener = listener; }

        public void onProximityChanged(float value) { lastProximity = value; evaluate(); }
        public void onLightChanged(float value)     { lastLight = value;     evaluate(); }

        private void evaluate() {
            boolean nowInPocket = lastProximity < PROX_THRESHOLD && lastLight < LIGHT_THRESHOLD;
            if (nowInPocket != inPocket) {
                inPocket = nowInPocket;
                if (listener != null) listener.onPocketStateChanged(inPocket);
            }
        }
        public boolean isInPocket() { return inPocket; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Step Counter Helper  (feature 7)
    // ─────────────────────────────────────────────────────────────────────────
    public static class StepCounterHelper {

        public interface OnStepListener { void onStep(int totalSteps); }

        private final OnStepListener listener;
        private int baseSteps  = -1;
        private int totalSteps = 0;

        public StepCounterHelper(OnStepListener listener) { this.listener = listener; }

        public void onStepCountChanged(float rawStepCount) {
            int raw = (int) rawStepCount;
            if (baseSteps == -1) baseSteps = raw;
            totalSteps = raw - baseSteps;
            if (listener != null) listener.onStep(totalSteps);
        }

        public void reset()               { baseSteps = -1; totalSteps = 0; }
        public int  getTotalSteps()       { return totalSteps; }
        public float getCalories()        { return totalSteps * 0.04f; }
        public float getDistanceMetres()  { return totalSteps * 0.762f; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Noise Detector  (feature 36)
    // ─────────────────────────────────────────────────────────────────────────
    public static class NoiseDetector {

        public interface OnNoiseUpdateListener { void onNoiseLevel(double dB); }

        private final OnNoiseUpdateListener listener;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private AudioRecord audioRecord;
        private Thread pollingThread;
        private volatile boolean running = false;

        private static final int SAMPLE_RATE = 44100;
        private static final int CHANNEL     = AudioFormat.CHANNEL_IN_MONO;
        private static final int ENCODING    = AudioFormat.ENCODING_PCM_16BIT;

        public NoiseDetector(OnNoiseUpdateListener listener) { this.listener = listener; }

        public void start() {
            if (running) return;
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
            try {
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE, CHANNEL, ENCODING, bufferSize);
                audioRecord.startRecording();
                running = true;
                pollingThread = new Thread(this::pollAudio);
                pollingThread.setDaemon(true);
                pollingThread.start();
            } catch (SecurityException e) { /* Permission not granted */ }
        }

        public void stop() {
            running = false;
            if (audioRecord != null) {
                try { audioRecord.stop(); audioRecord.release(); } catch (Exception ignored) {}
            }
        }

        private void pollAudio() {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
            short[] buffer = new short[bufferSize];
            while (running) {
                int read = audioRecord.read(buffer, 0, bufferSize);
                if (read > 0) {
                    double sum = 0;
                    for (int i = 0; i < read; i++) sum += (double)buffer[i] * buffer[i];
                    double rms = Math.sqrt(sum / read);
                    double dB  = rms > 0 ? 20 * Math.log10(rms) + 94 : 0;
                    mainHandler.post(() -> { if (listener != null) listener.onNoiseLevel(dB); });
                }
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CSV Exporter  (feature 4, 31)
    // ─────────────────────────────────────────────────────────────────────────
    public static class CsvExporter {

        private static final SimpleDateFormat SDF =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

        public static void export(List<SensorReading> readings, String filePath) throws IOException {
            try (FileWriter fw = new FileWriter(filePath)) {
                fw.write("id,sensor_type,x,y,z,timestamp\n");
                for (SensorReading r : readings) {
                    fw.write(r.id + "," + r.sensorType + "," + r.x + "," + r.y + "," + r.z + ","
                            + SDF.format(new Date(r.timestamp)) + "\n");
                }
            }
        }

        public static String toJson(List<SensorReading> readings) {
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < readings.size(); i++) {
                SensorReading r = readings.get(i);
                sb.append("  {");
                sb.append("\"sensor\":\"").append(r.sensorType).append("\",");
                sb.append("\"x\":").append(r.x).append(",");
                sb.append("\"y\":").append(r.y).append(",");
                sb.append("\"z\":").append(r.z).append(",");
                sb.append("\"ts\":").append(r.timestamp);
                sb.append("}");
                if (i < readings.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]");
            return sb.toString();
        }
    }
}
