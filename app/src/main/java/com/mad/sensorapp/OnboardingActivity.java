package com.mad.sensorapp;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private View[] dots;
    private Button btnNext, btnSkip;

    private static final String PREF_ONBOARDING_DONE = "onboarding_done";

    private final int[] titles = {
        R.string.onboard_title_1, R.string.onboard_title_2, R.string.onboard_title_3
    };
    private final int[] descriptions = {
        R.string.onboard_desc_1, R.string.onboard_desc_2, R.string.onboard_desc_3
    };
    private final int[] icons = {
        R.drawable.ic_onboard_sensors, R.drawable.ic_onboard_charts, R.drawable.ic_onboard_insights
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.viewPager);
        btnNext  = findViewById(R.id.btnNext);
        btnSkip  = findViewById(R.id.btnSkip);

        dots = new View[]{
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3)
        };

        viewPager.setAdapter(new OnboardingAdapter());
        viewPager.setOffscreenPageLimit(3);

        // Add depth page transformer
        viewPager.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            page.setAlpha(1f - (absPos * 0.5f));
            page.setScaleX(1f - (absPos * 0.1f));
            page.setScaleY(1f - (absPos * 0.1f));
            page.setTranslationX(page.getWidth() * -position * 0.1f);
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                if (position == 2) {
                    btnNext.setText(R.string.onboard_get_started);
                    btnSkip.setVisibility(View.GONE);
                } else {
                    btnNext.setText(R.string.onboard_next);
                    btnSkip.setVisibility(View.VISIBLE);
                }
            }
        });

        btnNext.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < 2) {
                viewPager.setCurrentItem(current + 1, true);
            } else {
                finishOnboarding();
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());

        updateDots(0);
    }

    private void updateDots(int activeIndex) {
        for (int i = 0; i < dots.length; i++) {
            float targetScale = (i == activeIndex) ? 1.4f : 0.8f;
            float targetAlpha = (i == activeIndex) ? 1.0f : 0.35f;
            ObjectAnimator.ofFloat(dots[i], View.SCALE_X, targetScale)
                .setDuration(300).start();
            ObjectAnimator.ofFloat(dots[i], View.SCALE_Y, targetScale)
                .setDuration(300).start();
            ObjectAnimator.ofFloat(dots[i], View.ALPHA, targetAlpha)
                .setDuration(300).start();
        }
    }

    private void finishOnboarding() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(PREF_ONBOARDING_DONE, true).apply();
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    // ─── Adapter ────────────────────────────────────────────────────────────────
    private class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_onboarding_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.icon.setImageResource(icons[position]);
            holder.title.setText(titles[position]);
            holder.desc.setText(descriptions[position]);

            // Stagger entrance animation
            holder.icon.setAlpha(0f);
            holder.icon.setScaleX(0.5f);
            holder.icon.setScaleY(0.5f);
            holder.title.setAlpha(0f);
            holder.title.setTranslationY(40f);
            holder.desc.setAlpha(0f);
            holder.desc.setTranslationY(30f);

            holder.icon.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(500).setInterpolator(new FastOutSlowInInterpolator()).start();
            holder.title.animate().alpha(1f).translationY(0f)
                    .setStartDelay(120).setDuration(500).setInterpolator(new FastOutSlowInInterpolator()).start();
            holder.desc.animate().alpha(1f).translationY(0f)
                    .setStartDelay(240).setDuration(500).setInterpolator(new FastOutSlowInInterpolator()).start();
        }

        @Override public int getItemCount() { return 3; }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView title, desc;
            VH(@NonNull View v) {
                super(v);
                icon  = v.findViewById(R.id.onboardIcon);
                title = v.findViewById(R.id.onboardTitle);
                desc  = v.findViewById(R.id.onboardDesc);
            }
        }
    }
}
