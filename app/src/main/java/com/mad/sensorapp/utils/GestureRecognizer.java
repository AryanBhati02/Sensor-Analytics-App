package com.mad.sensorapp.utils;

import java.util.*;

/**
 * Feature 37 – ML-based Gesture Recognition
 *
 * Architecture:
 *  • Records a 2-second accelerometer window (sliding buffer)
 *  • Extracts feature vector: [mean_x, mean_y, mean_z, var_x, var_y, var_z,
 *                              max_x, max_y, max_z, zero_crossings, energy]
 *  • Compares against learned gesture templates using Euclidean distance
 *    (1-NN classifier – same concept as TFLite gesture model inference)
 *  • Users can RECORD new gestures (saved as templates)
 *  • Users can RECOGNISE against saved templates
 *
 * To upgrade to actual TFLite:
 *  Replace recognise() with:
 *    Interpreter tflite = new Interpreter(loadModelFile());
 *    tflite.run(featureVector, output);
 */
public class GestureRecognizer {

    public interface GestureListener {
        void onGestureRecognized(String name, float confidence);
        void onGestureRecordingDone(String name);
        void onError(String message);
    }

    // ── Data structures ───────────────────────────────────────────────────────
    public static class GestureTemplate {
        public final String  name;
        public final float[] features;
        GestureTemplate(String name, float[] features) {
            this.name = name; this.features = features;
        }
    }

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int   WINDOW_SAMPLES = 40;   // ~2s at 20Hz
    private static final float CONFIDENCE_THR = 0.70f;
    private static final int   FEAT_DIM       = 11;
    private static final long  COOLDOWN_MS    = 1500; // Prevent spamming detections

    // ── State ──────────────────────────────────────────────────────────────────
    private final Deque<float[]> window    = new ArrayDeque<>(WINDOW_SAMPLES);
    private final List<GestureTemplate> templates = new ArrayList<>();
    private final GestureListener       listener;

    private volatile boolean recording     = false;
    private volatile boolean recognising   = false;
    private          String  recordingName = "";
    private          long    lastDetectionTime = 0;

    // Pre-built templates for common gestures
    public GestureRecognizer(GestureListener listener) {
        this.listener = listener;
        addBuiltInTemplates();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Feed live accelerometer samples. Call from onSensorChanged. */
    public void addSample(float x, float y, float z) {
        synchronized (window) {
            if (window.size() >= WINDOW_SAMPLES) window.pollFirst();
            window.addLast(new float[]{x, y, z});
        }

        if (window.size() < WINDOW_SAMPLES) return;

        if (recording) {
            // Capture this window as the template
            float[] feat = extractFeatures();
            templates.add(new GestureTemplate(recordingName, feat));
            recording = false;
            if (listener != null) listener.onGestureRecordingDone(recordingName);
        } else if (recognising) {
            long now = System.currentTimeMillis();
            if (now - lastDetectionTime > COOLDOWN_MS) {
                recognise();
            }
        }
    }

    /** Start recording a new gesture with given name. */
    public void startRecording(String gestureName) {
        if (gestureName == null || gestureName.trim().isEmpty()) {
            if (listener != null) listener.onError("Gesture name cannot be empty");
            return;
        }
        synchronized (window) {
            window.clear();
        }
        recordingName = gestureName.trim();
        recording     = true;
    }

    /** Enable continuous recognition. */
    public void setRecognising(boolean on) { 
        recognising = on;
        if (on) lastDetectionTime = 0; 
    }

    /** Delete a template by name. */
    public void deleteTemplate(String name) {
        templates.removeIf(t -> t.name.equals(name));
    }

    public List<GestureTemplate> getTemplates() { return Collections.unmodifiableList(templates); }
    public boolean isRecording()    { return recording; }
    public boolean isRecognising()  { return recognising; }

    // ── Core ML logic ──────────────────────────────────────────────────────────

    private void recognise() {
        if (templates.isEmpty()) return;
        float[] feat = extractFeatures();

        float   bestDist = Float.MAX_VALUE;
        String  bestName = "";

        for (GestureTemplate t : templates) {
            float dist = cosineSimilarity(feat, t.features);
            // Cosine similarity: 1.0 is perfect match, -1.0 is opposite.
            // We want the HIGHEST similarity.
            if (dist > (bestName.isEmpty() ? -2f : bestDist)) { 
                bestDist = dist; 
                bestName = t.name; 
            }
        }

        // Convert cosine similarity (typically 0.7 to 1.0 for matches) to confidence
        float confidence = Math.max(0f, (bestDist + 1f) / 2f); 

        if (bestDist > 0.85f) { // Strong similarity threshold
            lastDetectionTime = System.currentTimeMillis();
            final String n = bestName; final float cf = confidence;
            if (listener != null) listener.onGestureRecognized(n, cf);
        }
    }

    /**
     * Feature extraction from window.
     * Returns 11-dim vector: mean_x, mean_y, mean_z, var_x, var_y, var_z,
     *   max_x, max_y, max_z, zero_crossings, energy
     */
    private float[] extractFeatures() {
        float sumX=0,sumY=0,sumZ=0,sumX2=0,sumY2=0,sumZ2=0;
        float maxX=-Float.MAX_VALUE, maxY=-Float.MAX_VALUE, maxZ=-Float.MAX_VALUE;
        float energy=0, zeroCross=0;
        float prevMag=0;
        int n;
        
        List<float[]> snapshot;
        synchronized (window) {
            snapshot = new ArrayList<>(window);
            n = snapshot.size();
        }

        for (float[] s : snapshot) {
            sumX += s[0]; sumY += s[1]; sumZ += s[2];
            sumX2 += s[0]*s[0]; sumY2 += s[1]*s[1]; sumZ2 += s[2]*s[2];
            maxX = Math.max(maxX, Math.abs(s[0]));
            maxY = Math.max(maxY, Math.abs(s[1]));
            maxZ = Math.max(maxZ, Math.abs(s[2]));
            float mag = (float) Math.sqrt(s[0]*s[0]+s[1]*s[1]+s[2]*s[2]);
            energy += mag * mag;
            if (prevMag != 0 && ((mag - 9.81f) * (prevMag - 9.81f) < 0)) zeroCross++;
            prevMag = mag;
        }
        float mx = sumX/n, my = sumY/n, mz = sumZ/n;
        return new float[]{
            mx, my, mz,
            (float)Math.sqrt(Math.max(0, sumX2/n - mx*mx)), // Use StdDev instead of Var for better scaling
            (float)Math.sqrt(Math.max(0, sumY2/n - my*my)),
            (float)Math.sqrt(Math.max(0, sumZ2/n - mz*mz)),
            maxX, maxY, maxZ,
            zeroCross/n, (float)Math.sqrt(energy/n) // Root Energy
        };
    }

    private float cosineSimilarity(float[] a, float[] b) {
        float dot = 0, mA = 0, mB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            mA += a[i] * a[i];
            mB += b[i] * b[i];
        }
        if (mA == 0 || mB == 0) return 0;
        return dot / (float) (Math.sqrt(mA) * Math.sqrt(mB));
    }

    private float euclidean(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            float d = a[i] - b[i]; sum += d*d;
        }
        return (float) Math.sqrt(sum);
    }

    /** Seed with 3 built-in gesture templates (synthetic feature vectors). */
    private void addBuiltInTemplates() {
        // Updated synthetic templates to work better with Cosine Similarity
        // SHAKE: high variance all axes, high energy
        templates.add(new GestureTemplate("Shake",
            new float[]{0f, 0f, 9.81f, 5f, 5f, 5f, 15f, 15f, 15f, 0.8f, 25f}));
        // TILT LEFT: negative X mean, low variance
        templates.add(new GestureTemplate("Tilt Left",
            new float[]{-8f, 0f, 5f, 0.5f, 0.5f, 0.5f, 8f, 1f, 6f, 0.05f, 10f}));
        // FLIP OVER: negative Z
        templates.add(new GestureTemplate("Flip",
            new float[]{0f, 0f, -9.81f, 1f, 1f, 1f, 2f, 2f, 10f, 0.1f, 12f}));
    }
}
