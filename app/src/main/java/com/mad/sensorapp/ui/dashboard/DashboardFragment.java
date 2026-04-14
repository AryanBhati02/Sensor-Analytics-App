package com.mad.sensorapp.ui.dashboard;

import android.animation.*;
import android.content.Context;
import android.hardware.*;
import android.os.*;
import android.view.*;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mad.sensorapp.R;
import com.mad.sensorapp.utils.ActivityRecognizer;
import com.mad.sensorapp.utils.FallDetector;
import com.mad.sensorapp.utils.ShakeDetector;
import com.mad.sensorapp.utils.TriggerActionsManager;

/**
 * Dashboard Fragment – Features 2, 5, 7, 8, 13, 17, 26, 33, 34, 35, 36
 * Live sensor cards with real-time stats, animations, shake/fall/activity detection.
 */
public class DashboardFragment extends Fragment implements SensorEventListener {

    // ── Sensors ───────────────────────────────────────────────────────────────
    private SensorManager sensorManager;
    private Sensor accel, light, proximity, stepCounter;

    // ── Utils ──────────────────────────────────────────────────────────────────
    private ShakeDetector        shakeDetector;
    private FallDetector         fallDetector;
    private ActivityRecognizer   activityRecognizer;
    private TriggerActionsManager triggerActions;

    // ── Stats tracking ────────────────────────────────────────────────────────
    private float accelMin=Float.MAX_VALUE, accelMax=-Float.MAX_VALUE, accelSum=0f;
    private float lightMin=Float.MAX_VALUE, lightMax=-Float.MAX_VALUE, lightSum=0f;
    private float proxMin =Float.MAX_VALUE, proxMax =-Float.MAX_VALUE;
    private int   accelCount=0, lightCount=0;
    private static final float ANOMALY_MULTIPLIER = 3.0f;
    private boolean anomalyArmed = false;

    // ── Pulse animators ────────────────────────────────────────────────────────
    private ValueAnimator pulseAccel, pulseLight, pulseProx;

    // ── Views – Accelerometer card ────────────────────────────────────────────
    private MaterialCardView cardAccel;
    private TextView tvAccelX, tvAccelY, tvAccelZ;
    private TextView tvAccelMin, tvAccelMax, tvAccelAvg;
    private View dotAccel;

    // ── Views – Light card ────────────────────────────────────────────────────
    private MaterialCardView cardLight;
    private TextView tvLightVal, tvLightMin, tvLightMax, tvLightAvg;
    private View dotLight;
    private LinearProgressIndicator progressLight;

    // ── Views – Proximity card ────────────────────────────────────────────────
    private MaterialCardView cardProx;
    private TextView tvProxVal, tvProxMin, tvProxMax;
    private View dotProx;

    // ── Views – Status bar ────────────────────────────────────────────────────
    private TextView tvActivity, tvSteps, tvShake, tvStatus;

    // ── Entrance anim guard ───────────────────────────────────────────────────
    private boolean entranceAnimDone = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        bindViews(root);
        initSensors();
        initUtils();
        startPulseAnimations();
        if (!entranceAnimDone) { runEntranceAnim(); entranceAnimDone = true; }
    }

    // ── View binding ──────────────────────────────────────────────────────────
    private void bindViews(View root) {
        cardAccel   = root.findViewById(R.id.cardAccel);
        tvAccelX    = root.findViewById(R.id.tvAccelX);
        tvAccelY    = root.findViewById(R.id.tvAccelY);
        tvAccelZ    = root.findViewById(R.id.tvAccelZ);
        tvAccelMin  = root.findViewById(R.id.tvAccelMin);
        tvAccelMax  = root.findViewById(R.id.tvAccelMax);
        tvAccelAvg  = root.findViewById(R.id.tvAccelAvg);
        dotAccel    = root.findViewById(R.id.dotAccel);

        cardLight   = root.findViewById(R.id.cardLight);
        tvLightVal  = root.findViewById(R.id.tvLightVal);
        tvLightMin  = root.findViewById(R.id.tvLightMin);
        tvLightMax  = root.findViewById(R.id.tvLightMax);
        tvLightAvg  = root.findViewById(R.id.tvLightAvg);
        dotLight    = root.findViewById(R.id.dotLight);
        progressLight = root.findViewById(R.id.progressLight);

        cardProx    = root.findViewById(R.id.cardProx);
        tvProxVal   = root.findViewById(R.id.tvProxVal);
        tvProxMin   = root.findViewById(R.id.tvProxMin);
        tvProxMax   = root.findViewById(R.id.tvProxMax);
        dotProx     = root.findViewById(R.id.dotProx);

        tvActivity  = root.findViewById(R.id.tvActivity);
        tvSteps     = root.findViewById(R.id.tvSteps);
        tvShake     = root.findViewById(R.id.tvShake);
        tvStatus    = root.findViewById(R.id.tvStatus);
    }

    // ── Sensor init ────────────────────────────────────────────────────────────
    private void initSensors() {
        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        accel       = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        light       = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        proximity   = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }

    private void initUtils() {
        // Feature 26 – Trigger Actions (shake → flashlight, proximity → silence)
        triggerActions = new TriggerActionsManager(requireContext(),
                new TriggerActionsManager.TriggerCallback() {
                    @Override public void onFlashlightToggled(boolean on) {
                        requireActivity().runOnUiThread(() ->
                            tvStatus.setText(on ? "🔦 Flashlight ON" : "● LIVE  ·  All sensors active"));
                    }
                    @Override public void onAudioSilenced(boolean silenced) {
                        requireActivity().runOnUiThread(() ->
                            tvStatus.setText(silenced ? "🔇 Audio silenced" : "● LIVE  ·  All sensors active"));
                    }
                });

        // Pre-warm camera torch (eliminates first-shake delay)
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                android.hardware.camera2.CameraManager cm =
                    (android.hardware.camera2.CameraManager)
                    requireContext().getSystemService(android.content.Context.CAMERA_SERVICE);
                if (cm != null && cm.getCameraIdList().length > 0) {
                    // Touch torch briefly to initialize pipeline (flash off = safe)
                    cm.setTorchMode(cm.getCameraIdList()[0], false);
                }
            } catch (Exception ignored) {}
        }, 500);

        // Feature 5 – Shake Detector
        shakeDetector = new ShakeDetector(count -> requireActivity().runOnUiThread(() -> {
            tvShake.setText("SHAKE #" + count + " !");
            triggerActions.onShakeDetected();   // Feature 26: trigger flashlight
            animateShakeCard();
            vibrateShort();
            // Auto-clear after 2s
            tvShake.postDelayed(() -> tvShake.setText(""), 2000);
        }));

        // Feature 34 – Fall Detector
        fallDetector = new FallDetector(() -> requireActivity().runOnUiThread(() -> {
            tvStatus.setText("⚠️  FALL DETECTED");
            animateFallAlert();
            vibrateLong();
            new Handler(Looper.getMainLooper()).postDelayed(
                () -> tvStatus.setText("● LIVE  ·  All sensors active"), 4000);
        }));

        // Feature 33 – Activity Recognizer
        activityRecognizer = new ActivityRecognizer((activity, confidence) ->
            requireActivity().runOnUiThread(() ->
                tvActivity.setText(activityRecognizer.getEmoji(activity)
                        + " " + capitalize(activity.name())
                        + " (" + (int)(confidence*100) + "%)")));
    }

    // ── Entrance animation ─────────────────────────────────────────────────────
    private void runEntranceAnim() {
        View[] cards = {cardAccel, cardLight, cardProx};
        for (int i = 0; i < cards.length; i++) {
            View card = cards[i];
            card.setAlpha(0f);
            card.setTranslationY(80f);
            card.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(i * 120L).setDuration(500)
                .setInterpolator(new FastOutSlowInInterpolator()).start();
        }
    }

    // ── Pulse dot animations ───────────────────────────────────────────────────
    private void startPulseAnimations() {
        pulseAccel = buildPulse(dotAccel);
        pulseLight = buildPulse(dotLight);
        pulseProx  = buildPulse(dotProx);
        pulseAccel.start(); pulseLight.start(); pulseProx.start();
    }

    private ValueAnimator buildPulse(View dot) {
        ValueAnimator va = ValueAnimator.ofFloat(0.3f, 1.0f);
        va.setDuration(900);
        va.setRepeatMode(ValueAnimator.REVERSE);
        va.setRepeatCount(ValueAnimator.INFINITE);
        va.setInterpolator(new FastOutSlowInInterpolator());
        va.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            dot.setAlpha(v);
            dot.setScaleX(v);
            dot.setScaleY(v);
        });
        return va;
    }

    // ── Card animations ────────────────────────────────────────────────────────
    private void animateShakeCard() {
        ObjectAnimator.ofFloat(cardAccel, "translationX",
                0f, -18f, 18f, -12f, 12f, -6f, 6f, 0f)
                .setDuration(450).start();
    }

    private void animateFallAlert() {
        View[] cards = {cardAccel, cardLight, cardProx};
        for (View c : cards) {
            ObjectAnimator.ofArgb(c, "cardBackgroundColor",
                    0xFF131929, 0xFF3A0000, 0xFF131929)
                    .setDuration(900).start();
        }
    }

    // ── Vibration ──────────────────────────────────────────────────────────────
    private void vibrateShort() {
        Vibrator v = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator())
            v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    private void vibrateLong() {
        Vibrator v = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator())
            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 200, 100, 200}, -1));
    }

    // ── SensorEventListener ───────────────────────────────────────────────────
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {

            case Sensor.TYPE_ACCELEROMETER: {
                float x = event.values[0], y = event.values[1], z = event.values[2];
                float mag = (float) Math.sqrt(x*x + y*y + z*z);

                // Feature 5 – shake detection
                shakeDetector.onSensorChanged(event);
                // Feature 34 – fall detection
                fallDetector.onSensorChanged(event);
                // Feature 33 – activity recognition
                activityRecognizer.onSensorChanged(x, y, z);

                // Feature 13 – live stats
                accelMin = Math.min(accelMin, mag); accelMax = Math.max(accelMax, mag);
                accelSum += mag; accelCount++;

                // Feature 17 – anomaly detection
                float avg = accelCount > 0 ? accelSum / accelCount : 0f;
                if (anomalyArmed && mag > avg * ANOMALY_MULTIPLIER) {
                    requireActivity().runOnUiThread(() ->
                        tvStatus.setText("⚡ ANOMALY – spike " + String.format("%.1f", mag) + " m/s²"));
                    new Handler(Looper.getMainLooper()).postDelayed(
                        () -> tvStatus.setText("● LIVE  ·  All sensors active"), 2000);
                }
                anomalyArmed = (accelCount > 50);

                tvAccelX.setText(String.format("X  %+.3f", x));
                tvAccelY.setText(String.format("Y  %+.3f", y));
                tvAccelZ.setText(String.format("Z  %+.3f", z));
                tvAccelMin.setText(String.format("%.1f", accelMin));
                tvAccelMax.setText(String.format("%.1f", accelMax));
                tvAccelAvg.setText(String.format("%.1f", accelCount > 0 ? accelSum / accelCount : 0f));
                break;
            }

            case Sensor.TYPE_LIGHT: {
                float lux = event.values[0];
                lightMin = Math.min(lightMin, lux); lightMax = Math.max(lightMax, lux);
                lightSum += lux; lightCount++;

                tvLightVal.setText(String.format("%.0f lx", lux));
                tvLightMin.setText(String.format("%.0f", lightMin));
                tvLightMax.setText(String.format("%.0f", lightMax));
                tvLightAvg.setText(String.format("%.0f", lightCount > 0 ? lightSum / lightCount : 0f));
                int prog = (int) Math.min(100, lux / 5f);
                if (progressLight != null) progressLight.setProgressCompat(prog, true);
                break;
            }

            case Sensor.TYPE_PROXIMITY: {
                float cm = event.values[0];
                proxMin = Math.min(proxMin, cm); proxMax = Math.max(proxMax, cm);
                String label = cm < 2f ? "  [NEAR]" : "  [FAR]";
                tvProxVal.setText(String.format("%.1f cm%s", cm, label));
                tvProxMin.setText(String.format("%.1f", proxMin));
                tvProxMax.setText(String.format("%.1f", proxMax));

                // Feature 26 – proximity silence trigger
                Sensor proxSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
                float maxRange = proxSensor != null ? proxSensor.getMaximumRange() : 10f;
                triggerActions.onProximityChanged(cm, maxRange);
                break;
            }

            case Sensor.TYPE_STEP_COUNTER: {
                tvSteps.setText("👟 " + (int) event.values[0] + " steps");
                break;
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    @Override public void onResume() {
        super.onResume();
        if (accel       != null) sensorManager.registerListener(this, accel,      SensorManager.SENSOR_DELAY_GAME);
        if (light       != null) sensorManager.registerListener(this, light,      SensorManager.SENSOR_DELAY_NORMAL);
        if (proximity   != null) sensorManager.registerListener(this, proximity,  SensorManager.SENSOR_DELAY_NORMAL);
        if (stepCounter != null) sensorManager.registerListener(this, stepCounter,SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (pulseAccel != null) pulseAccel.cancel();
        if (pulseLight != null) pulseLight.cancel();
        if (pulseProx  != null) pulseProx.cancel();
        if (triggerActions != null) triggerActions.release();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
