package com.mad.sensorapp.utils;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Feature 26 – Trigger Actions
 * Shake → toggle flashlight
 * Proximity NEAR → silence audio / vibrate mode
 * Proximity FAR  → restore audio
 */
public class TriggerActionsManager {

    private static final String TAG = "TriggerActions";

    public interface TriggerCallback {
        void onFlashlightToggled(boolean on);
        void onAudioSilenced(boolean silenced);
    }

    private final Context       context;
    private final CameraManager cameraManager;
    private final AudioManager  audioManager;
    private final TriggerCallback callback;

    private boolean flashOn       = false;
    private boolean audioSilenced = false;
    private int     savedRingerMode = -1;
    private int     savedMediaVolume = -1;
    private AudioFocusRequest focusRequest;
    private long    lastFlashMs   = 0L;
    private static final long FLASH_DEBOUNCE_MS = 400L;

    // Per-feature toggles (can be set from ToolsFragment or Settings)
    public boolean shakeFlashlightEnabled  = true;
    public boolean proximitySilenceEnabled = true;

    public TriggerActionsManager(Context context, TriggerCallback callback) {
        this.context       = context.getApplicationContext();
        this.callback      = callback;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.audioManager  = (AudioManager)  context.getSystemService(Context.AUDIO_SERVICE);
        // Initialize as -1 to signal we haven't captured a state yet
        this.savedRingerMode = -1;
        this.savedMediaVolume = -1;
    }

    // ── Shake → Flashlight ────────────────────────────────────────────────────
    public void onShakeDetected() {
        if (!shakeFlashlightEnabled) return;
        long now = System.currentTimeMillis();
        if (now - lastFlashMs < FLASH_DEBOUNCE_MS) return;
        lastFlashMs = now;
        toggleFlashlight();
    }

    private void toggleFlashlight() {
        try {
            String[] ids = cameraManager.getCameraIdList();
            if (ids.length == 0) return;
            flashOn = !flashOn;
            cameraManager.setTorchMode(ids[0], flashOn);
            if (callback != null) callback.onFlashlightToggled(flashOn);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
            Log.w(TAG, "Flashlight error: " + e.getMessage());
            flashOn = false;
        }
    }

    public void setFlashlight(boolean on) {
        try {
            String[] ids = cameraManager.getCameraIdList();
            if (ids.length == 0) return;
            flashOn = on;
            cameraManager.setTorchMode(ids[0], on);
            if (callback != null) callback.onFlashlightToggled(on);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
            Log.w(TAG, "Flashlight error: " + e.getMessage());
        }
    }

    // ── Proximity → Silence ───────────────────────────────────────────────────
    /**
     * @param cm       current proximity reading in cm
     * @param maxRange sensor's max range in cm (usually 5 or 10)
     */
    public void onProximityChanged(float cm, float maxRange) {
        if (!proximitySilenceEnabled || audioManager == null) return;
        
        // Threshold: trigger if distance is < 1cm OR < 40% of max range
        // This handles binary (0/5) and continuous sensors.
        boolean near = cm < 1.0f || (maxRange > 0 && cm <= maxRange * 0.4f);
        
        if (near && !audioSilenced) {
            silenceAudio();
        } else if (!near && audioSilenced) {
            restoreAudio();
        }
    }

    private void silenceAudio() {
        try {
            Log.d(TAG, "Silencing audio...");

            // 1. Pause external media (Spotify, YouTube, etc.)
            audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));

            // 2. Transient focus request to ensure other apps pause
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .build();
            }
            audioManager.requestAudioFocus(focusRequest);

            // 3. Mute stream as fallback
            if (savedMediaVolume == -1) {
                savedMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            }
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);

            // 4. Ringer to Vibrate
            if (savedRingerMode == -1) {
                savedRingerMode = audioManager.getRingerMode();
            }
            audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);

            audioSilenced = true;
            if (callback != null) callback.onAudioSilenced(true);
        } catch (Exception e) {
            Log.e(TAG, "Silence error: " + e.getMessage());
        }
    }

    private void restoreAudio() {
        try {
            Log.d(TAG, "Restoring audio...");

            // 1. Resume media if it was paused (Optional, some users prefer it stays paused)
            // audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
            // audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY));

            // 2. Abandon focus
            if (focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            }

            // 3. Restore Media Volume
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            if (savedMediaVolume != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMediaVolume, 0);
                savedMediaVolume = -1;
            }

            // 4. Restore Ringer
            if (savedRingerMode != -1) {
                audioManager.setRingerMode(savedRingerMode);
                savedRingerMode = -1;
            }

            audioSilenced = false;
            if (callback != null) callback.onAudioSilenced(false);
        } catch (Exception e) {
            Log.e(TAG, "Restore error: " + e.getMessage());
        }
    }

    /** Must call when fragment/activity is destroyed */
    public void release() {
        if (flashOn)       setFlashlight(false);
        if (audioSilenced) restoreAudio();
    }

    public boolean isFlashOn()       { return flashOn; }
    public boolean isAudioSilenced() { return audioSilenced; }
}
