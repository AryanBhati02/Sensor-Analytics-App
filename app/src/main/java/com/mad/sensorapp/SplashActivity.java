package com.mad.sensorapp;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.preference.PreferenceManager;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION = 2400L;
    private static final String PREF_ONBOARDING_DONE = "onboarding_done";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme preference
        boolean isDark = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(this).getBoolean("dark_mode", true);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                isDark ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                       : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View logoContainer = findViewById(R.id.logoContainer);
        View logoIcon = findViewById(R.id.logoIcon);
        TextView appTitle = findViewById(R.id.appTitle);
        TextView appSubtitle = findViewById(R.id.appSubtitle);
        View loadingBar = findViewById(R.id.loadingBar);
        View scanLine = findViewById(R.id.scanLine);

        // Initial state
        logoContainer.setAlpha(0f);
        logoContainer.setScaleX(0.3f);
        logoContainer.setScaleY(0.3f);
        appTitle.setAlpha(0f);
        appTitle.setTranslationY(40f);
        appSubtitle.setAlpha(0f);
        appSubtitle.setTranslationY(30f);
        loadingBar.setScaleX(0f);

        // Logo pop-in animation
        ObjectAnimator logoScale = ObjectAnimator.ofPropertyValuesHolder(
                logoContainer,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.3f, 1.1f, 1.0f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.3f, 1.1f, 1.0f),
                android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f, 1f)
        );
        logoScale.setDuration(700);
        logoScale.setInterpolator(new FastOutSlowInInterpolator());

        // Title slide up
        AnimatorSet titleAnim = new AnimatorSet();
        ObjectAnimator titleAlpha = ObjectAnimator.ofFloat(appTitle, View.ALPHA, 0f, 1f);
        ObjectAnimator titleTranslate = ObjectAnimator.ofFloat(appTitle, View.TRANSLATION_Y, 40f, 0f);
        titleAnim.playTogether(titleAlpha, titleTranslate);
        titleAnim.setDuration(500);
        titleAnim.setStartDelay(500);
        titleAnim.setInterpolator(new DecelerateInterpolator());

        // Subtitle slide up
        AnimatorSet subtitleAnim = new AnimatorSet();
        ObjectAnimator subtitleAlpha = ObjectAnimator.ofFloat(appSubtitle, View.ALPHA, 0f, 1f);
        ObjectAnimator subtitleTranslate = ObjectAnimator.ofFloat(appSubtitle, View.TRANSLATION_Y, 30f, 0f);
        subtitleAnim.playTogether(subtitleAlpha, subtitleTranslate);
        subtitleAnim.setDuration(500);
        subtitleAnim.setStartDelay(700);
        subtitleAnim.setInterpolator(new DecelerateInterpolator());

        // Loading bar fill
        ObjectAnimator barAnim = ObjectAnimator.ofFloat(loadingBar, View.SCALE_X, 0f, 1f);
        barAnim.setDuration(1200);
        barAnim.setStartDelay(900);
        barAnim.setInterpolator(new AccelerateDecelerateInterpolator());

        // Scan line pulse (on the icon)
        ValueAnimator scanAnim = ValueAnimator.ofFloat(-80f, 80f);
        scanAnim.setDuration(1000);
        scanAnim.setStartDelay(200);
        scanAnim.setRepeatMode(ValueAnimator.RESTART);
        scanAnim.setRepeatCount(ValueAnimator.INFINITE);
        scanAnim.addUpdateListener(anim -> {
            if (scanLine != null) {
                scanLine.setTranslationY((float) anim.getAnimatedValue());
            }
        });

        // Logo icon rotation (subtle continuous spin)
        ObjectAnimator iconRotate = ObjectAnimator.ofFloat(logoIcon, View.ROTATION, 0f, 360f);
        iconRotate.setDuration(6000);
        iconRotate.setRepeatCount(ValueAnimator.INFINITE);
        iconRotate.setInterpolator(new AccelerateDecelerateInterpolator());

        // Play all
        AnimatorSet masterSet = new AnimatorSet();
        masterSet.playTogether(logoScale, titleAnim, subtitleAnim, barAnim, scanAnim, iconRotate);
        masterSet.start();

        // Navigate after delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean onboardingDone = prefs.getBoolean(PREF_ONBOARDING_DONE, false);

            Intent intent;
            if (onboardingDone) {
                intent = new Intent(this, MainActivity.class);
            } else {
                intent = new Intent(this, OnboardingActivity.class);
            }
            startActivity(intent);

            // Smooth exit
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, SPLASH_DURATION);
    }
}
