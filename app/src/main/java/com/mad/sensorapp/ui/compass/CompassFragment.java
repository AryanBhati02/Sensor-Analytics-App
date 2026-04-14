package com.mad.sensorapp.ui.compass;

import android.content.Context;
import android.hardware.*;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.google.android.material.tabs.TabLayout;
import com.mad.sensorapp.R;
import com.mad.sensorapp.views.*;

/**
 * Compass screen – 4 tabs: Compass | 3D Cube | Gyro Ball | Bubble
 * Features 6, 16, 18, 20, 22
 */
public class CompassFragment extends Fragment implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accel, mag, proxSensor;

    // Low-pass filter state
    private final float[] gravity     = new float[3];
    private final float[] geomagnetic = new float[3];
    private final float[] rotMat  = new float[9];
    private final float[] incMat  = new float[9];
    private final float[] orient  = new float[3];
    private static final float LP = 0.25f; // Increased for better responsiveness
    private boolean hasGravity = false, hasMag = false;

    // Views
    private TabLayout       tabLayout;
    private CompassView     compassView;
    private OrientationCubeView cubeView;
    private GyroscopeBallView   ballView;
    private BubbleView      bubbleView;
    private TextView        tvAzimuth, tvPitch, tvRoll, tvCardinal;
    private View panelCompass, panelCube, panelBall, panelBubble;

    private float proxMax = 10f;
    private int   currentTab = 0;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_compass, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle s) {
        super.onViewCreated(root, s);

        // Bind views with null safety
        tabLayout    = root.findViewById(R.id.compassTabLayout);
        compassView  = root.findViewById(R.id.compassView);
        cubeView     = root.findViewById(R.id.cubeView);
        ballView     = root.findViewById(R.id.ballView);
        bubbleView   = root.findViewById(R.id.bubbleView);
        tvAzimuth    = root.findViewById(R.id.tvAzimuth);
        tvPitch      = root.findViewById(R.id.tvPitch);
        tvRoll       = root.findViewById(R.id.tvRoll);
        tvCardinal   = root.findViewById(R.id.tvCardinal);
        panelCompass = root.findViewById(R.id.panelCompass);
        panelCube    = root.findViewById(R.id.panelCube);
        panelBall    = root.findViewById(R.id.panelBall);
        panelBubble  = root.findViewById(R.id.panelBubble);

        // Init sensors
        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        accel     = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mag       = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        proxSensor= sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        if (proxSensor != null) proxMax = proxSensor.getMaximumRange();

        // Warn if magnetometer missing
        if (mag == null) {
            Toast.makeText(requireContext(),
                    "No magnetometer found — compass will use accelerometer only",
                    Toast.LENGTH_SHORT).show();
        }

        // Tab listener
        if (tabLayout != null) {
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab t) {
                    currentTab = t.getPosition();
                    showPanel(currentTab);
                }
                @Override public void onTabUnselected(TabLayout.Tab t) {}
                @Override public void onTabReselected(TabLayout.Tab t) {}
            });
        }

        showPanel(0);
    }

    private void showPanel(int idx) {
        View[] panels = {panelCompass, panelCube, panelBall, panelBubble};
        for (int i = 0; i < panels.length; i++) {
            if (panels[i] == null) continue;
            if (i == idx) {
                panels[i].setVisibility(View.VISIBLE);
                panels[i].setAlpha(0f);
                panels[i].animate().alpha(1f).setDuration(200)
                        .setInterpolator(new FastOutSlowInInterpolator()).start();
            } else {
                panels[i].setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity[0] = LP * event.values[0] + (1-LP) * gravity[0];
            gravity[1] = LP * event.values[1] + (1-LP) * gravity[1];
            gravity[2] = LP * event.values[2] + (1-LP) * gravity[2];
            hasGravity = true;

            // Gyro ball always needs gravity
            if (ballView != null) ballView.setTilt(gravity[0], gravity[1]);
        }

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic[0] = LP * event.values[0] + (1-LP) * geomagnetic[0];
            geomagnetic[1] = LP * event.values[1] + (1-LP) * geomagnetic[1];
            geomagnetic[2] = LP * event.values[2] + (1-LP) * geomagnetic[2];
            hasMag = true;
        }

        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            if (bubbleView != null) bubbleView.setProximity(event.values[0], proxMax);
        }

        // Sensor fusion – compute orientation when both accel+mag available
        if (hasGravity && hasMag) {
            boolean ok = SensorManager.getRotationMatrix(rotMat, incMat, gravity, geomagnetic);
            if (ok) {
                // Remap coordinate system to handle device tilt/orientation correctly
                float[] remappedRotMat = new float[9];
                // Using AXIS_X, AXIS_Y as default, but this can be improved based on display rotation
                SensorManager.remapCoordinateSystem(rotMat, SensorManager.AXIS_X, SensorManager.AXIS_Y, remappedRotMat);
                
                SensorManager.getOrientation(remappedRotMat, orient);
                float az = (float) Math.toDegrees(orient[0]);
                float pt = (float) Math.toDegrees(orient[1]);
                float rl = (float) Math.toDegrees(orient[2]);
                
                // Normalize azimuth to 0-360
                az = (az + 360f) % 360f;

                if (compassView != null) compassView.setBearing(az);
                if (cubeView    != null) cubeView.setOrientation(az, pt, rl);
                if (tvAzimuth   != null) tvAzimuth.setText(String.format("Az %6.1f°", az));
                if (tvPitch     != null) tvPitch.setText(String.format("Pt %+6.1f°", pt));
                if (tvRoll      != null) tvRoll.setText(String.format("Rl %+6.1f°", rl));
                if (tvCardinal  != null) tvCardinal.setText(getCardinal(az));
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    @Override public void onResume() {
        super.onResume();
        if (accel != null)
            sensorManager.registerListener(this, accel,     SensorManager.SENSOR_DELAY_GAME);
        if (mag   != null)
            sensorManager.registerListener(this, mag,       SensorManager.SENSOR_DELAY_GAME);
        if (proxSensor != null)
            sensorManager.registerListener(this, proxSensor,SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    private String getCardinal(float d) {
        String[] dirs = {"N","NNE","NE","ENE","E","ESE","SE","SSE",
                         "S","SSW","SW","WSW","W","WNW","NW","NNW"};
        return dirs[(int) Math.round(d / 22.5f) % 16];
    }
}
