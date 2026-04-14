package com.mad.sensorapp.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import android.content.SharedPreferences;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.mad.sensorapp.R;
import com.mad.sensorapp.data.SensorDatabase;
import com.mad.sensorapp.service.FloatingHudService;
import com.mad.sensorapp.service.SensorBackgroundService;
import java.util.concurrent.Executors;

/**
 * Settings screen – Features 11 (Dark/Light theme), 25 (BG service),
 * 43 (Floating HUD), 45 (Always-On).
 */
public class SettingsFragment extends Fragment {

    private SharedPreferences prefs;

    private SwitchMaterial switchDarkMode, switchBackgroundService,
                           switchFloatingHud, switchAlwaysOn;
    private Slider   sliderSamplingRate;
    private TextView tvSamplingLabel, tvVersion;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_settings, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle s) {
        super.onViewCreated(root, s);
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        switchDarkMode          = root.findViewById(R.id.switchDarkMode);
        switchBackgroundService = root.findViewById(R.id.switchBackgroundService);
        switchFloatingHud       = root.findViewById(R.id.switchFloatingHud);
        switchAlwaysOn          = root.findViewById(R.id.switchAlwaysOn);
        sliderSamplingRate      = root.findViewById(R.id.sliderSamplingRate);
        tvSamplingLabel         = root.findViewById(R.id.tvSamplingLabel);
        tvVersion               = root.findViewById(R.id.tvVersion);

        // ── Restore saved states ──────────────────────────────────────────────
        // Dark mode: default true (app is dark by default)
        boolean isDark = prefs.getBoolean("dark_mode", true);
        if (switchDarkMode != null) switchDarkMode.setChecked(isDark);

        float sampHz = prefs.getFloat("sampling_hz", 5f);
        if (sliderSamplingRate != null) sliderSamplingRate.setValue(sampHz);
        if (tvSamplingLabel    != null) tvSamplingLabel.setText(String.format("%.0f Hz", sampHz));

        if (switchBackgroundService != null)
            switchBackgroundService.setChecked(prefs.getBoolean("bg_service", false));
        if (switchFloatingHud != null)
            switchFloatingHud.setChecked(prefs.getBoolean("floating_hud", false));
        if (switchAlwaysOn != null)
            switchAlwaysOn.setChecked(prefs.getBoolean("always_on", false));

        // Version info
        try {
            String vn = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            if (tvVersion != null) tvVersion.setText("v" + vn + "  ·  SensorPro");
        } catch (Exception ignored) {}

        // ── Listeners ─────────────────────────────────────────────────────────

        // Feature 11 – Dark / Light Theme toggle
        if (switchDarkMode != null) {
            switchDarkMode.setOnCheckedChangeListener((btn, checked) -> {
                prefs.edit().putBoolean("dark_mode", checked).apply();
                // Apply immediately — recreates all activities
                AppCompatDelegate.setDefaultNightMode(
                        checked ? AppCompatDelegate.MODE_NIGHT_YES
                                : AppCompatDelegate.MODE_NIGHT_NO);
            });
        }

        // Sampling rate slider
        if (sliderSamplingRate != null) {
            sliderSamplingRate.addOnChangeListener((slider, value, fromUser) -> {
                prefs.edit().putFloat("sampling_hz", value).apply();
                if (tvSamplingLabel != null)
                    tvSamplingLabel.setText(String.format("%.0f Hz", value));
            });
        }

        // Feature 25 – Background Service
        if (switchBackgroundService != null) {
            switchBackgroundService.setOnCheckedChangeListener((btn, checked) -> {
                prefs.edit().putBoolean("bg_service", checked).apply();
                Intent intent = new Intent(requireContext(), SensorBackgroundService.class);
                if (checked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(intent);
                    } else {
                        requireContext().startService(intent);
                    }
                } else {
                    requireContext().stopService(intent);
                }
            });
        }

        // Feature 43 – Floating HUD
        if (switchFloatingHud != null) {
            switchFloatingHud.setOnCheckedChangeListener((btn, checked) -> {
                prefs.edit().putBoolean("floating_hud", checked).apply();
                if (checked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && !Settings.canDrawOverlays(requireContext())) {
                        Intent pi = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + requireContext().getPackageName()));
                        startActivity(pi);
                        switchFloatingHud.setChecked(false);
                    } else {
                        requireContext().startService(
                                new Intent(requireContext(), FloatingHudService.class));
                    }
                } else {
                    requireContext().stopService(
                            new Intent(requireContext(), FloatingHudService.class));
                }
            });
        }

        // Feature 45 – Always-On Display
        if (switchAlwaysOn != null) {
            switchAlwaysOn.setOnCheckedChangeListener((btn, checked) -> {
                prefs.edit().putBoolean("always_on", checked).apply();
                if (requireActivity().getWindow() != null) {
                    if (checked)
                        requireActivity().getWindow().addFlags(
                                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else
                        requireActivity().getWindow().clearFlags(
                                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        // Clear data button
        View btnClear = root.findViewById(R.id.btnClearData);
        if (btnClear != null) {
            btnClear.setOnClickListener(v ->
                new android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Clear All Data")
                    .setMessage("This will permanently delete all recorded sensor sessions. Continue?")
                    .setPositiveButton("Delete All", (d, w) ->
                        Executors.newSingleThreadExecutor().execute(() -> {
                            SensorDatabase.getInstance(requireContext()).sensorDao().deleteAll();
                            requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "All data cleared",
                                        Toast.LENGTH_SHORT).show());
                        }))
                    .setNegativeButton("Cancel", null)
                    .show()
            );
        }
    }
}
