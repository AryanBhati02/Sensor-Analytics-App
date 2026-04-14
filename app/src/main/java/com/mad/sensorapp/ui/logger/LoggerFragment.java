package com.mad.sensorapp.ui.logger;

import android.content.Context;
import android.content.Intent;
import android.hardware.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.mad.sensorapp.R;
import com.mad.sensorapp.data.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class LoggerFragment extends Fragment implements SensorEventListener {

    private boolean isRecording  = false;
    private String  sessionId    = "";
    private long    startTime    = 0L;
    private int     readingCount = 0;

    private final Handler  timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTick    = this::tick;

    private SensorManager sm;
    private Sensor accel, light, prox;

    private SensorDatabase db;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    // Views
    private MaterialButton btnRecord;
    private TextView tvTimer, tvCount, tvSessionId, tvReadingRate;
    private View fabExport;
    private RecyclerView rvHistory;
    private TextView tvEmptyHistory;
    private SessionAdapter adapter;

    private static final SimpleDateFormat SDF_ID   = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
    private static final SimpleDateFormat SDF_DISP = new SimpleDateFormat("MMM dd  HH:mm:ss", Locale.US);

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_logger, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle s) {
        super.onViewCreated(root, s);

        btnRecord      = root.findViewById(R.id.btnRecord);
        tvTimer        = root.findViewById(R.id.tvTimer);
        tvCount        = root.findViewById(R.id.tvCount);
        tvSessionId    = root.findViewById(R.id.tvSessionId);
        tvReadingRate  = root.findViewById(R.id.tvReadingRate);
        fabExport      = root.findViewById(R.id.fabExport);
        rvHistory      = root.findViewById(R.id.rvHistory);
        tvEmptyHistory = root.findViewById(R.id.tvEmptyHistory);

        sm    = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        light = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        prox  = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        db = SensorDatabase.getInstance(requireContext());

        adapter = new SessionAdapter();
        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvHistory.setAdapter(adapter);

        btnRecord.setOnClickListener(v -> toggleRecording());
        if (fabExport != null) fabExport.setOnClickListener(v -> exportLatestSession());

        loadSessions();
    }

    private void toggleRecording() {
        if (!isRecording) startRec(); else stopRec();
    }

    private void startRec() {
        isRecording  = true;
        sessionId    = SDF_ID.format(new Date());
        startTime    = System.currentTimeMillis();
        readingCount = 0;

        btnRecord.setText("⏹  Stop Recording");
        tvSessionId.setText("Session: " + sessionId);
        tvCount.setText("0 readings");
        tvReadingRate.setText("—");

        if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);
        if (light != null) sm.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
        if (prox  != null) sm.registerListener(this, prox,  SensorManager.SENSOR_DELAY_NORMAL);

        timerHandler.post(timerTick);
    }

    private void stopRec() {
        isRecording = false;
        sm.unregisterListener(this);
        timerHandler.removeCallbacks(timerTick);
        btnRecord.setText("⏺  Start Recording");
        loadSessions();
    }

    private void tick() {
        if (!isRecording || !isAdded()) return;
        long ms = System.currentTimeMillis() - startTime;
        long sec = (ms/1000)%60, min = (ms/60000)%60, hr = ms/3600000;
        tvTimer.setText(String.format(Locale.US, "%02d:%02d:%02d", hr, min, sec));
        tvCount.setText(readingCount + " readings");
        float secs = ms / 1000f;
        if (secs > 1f) tvReadingRate.setText(String.format(Locale.US, "%.1f r/s", readingCount / secs));
        timerHandler.postDelayed(timerTick, 500);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isRecording) return;
        String type;
        float y = event.values.length > 1 ? event.values[1] : 0f;
        float z = event.values.length > 2 ? event.values[2] : 0f;
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER: type = "ACCELEROMETER"; break;
            case Sensor.TYPE_LIGHT:         type = "LIGHT";         break;
            case Sensor.TYPE_PROXIMITY:     type = "PROXIMITY";     break;
            default: return;
        }
        readingCount++;
        SensorReading r = new SensorReading(type, event.values[0], y, z,
                System.currentTimeMillis(), sessionId);
        exec.execute(() -> db.sensorDao().insert(r));
    }
    @Override public void onAccuracyChanged(Sensor s, int a) {}

    private void loadSessions() {
        exec.execute(() -> {
            List<SessionInfo> list = db.sensorDao().getSessionSummaries();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                adapter.setSessions(list);
                tvEmptyHistory.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                rvHistory.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void exportLatestSession() {
        String sid = adapter.getLatestSessionId();
        if (sid == null) {
            Toast.makeText(requireContext(), "No session to export", Toast.LENGTH_SHORT).show();
            return;
        }
        exportSession(sid);
    }

    private void exportSession(String sid) {
        exec.execute(() -> {
            List<SensorReading> data = db.sensorDao().getSession(sid);
            if (data.isEmpty()) { return; }
            try {
                File dir = requireContext().getExternalFilesDir(null);
                if (dir == null) dir = requireContext().getFilesDir();
                File csv = new File(dir, "sensor_" + sid + ".csv");
                try (FileWriter fw = new FileWriter(csv)) {
                    fw.write("id,type,x,y,z,timestamp\n");
                    for (SensorReading r : data)
                        fw.write(r.id+","+r.sensorType+","+r.x+","+r.y+","+r.z+","+r.timestamp+"\n");
                }
                requireActivity().runOnUiThread(() -> shareCsv(csv));
            } catch (IOException e) {
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "Export failed: "+e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void shareCsv(File f) {
        Uri uri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".provider", f);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Export CSV"));
    }

    private void showOptions(SessionInfo si) {
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle(si.sessionId)
            .setMessage(si.count + " readings  ·  " + SDF_DISP.format(new Date(si.firstTs)))
            .setPositiveButton("Export CSV", (d,w) -> exportSession(si.sessionId))
            .setNegativeButton("Delete",     (d,w) -> {
                exec.execute(() -> {
                    db.sensorDao().deleteSession(si.sessionId);
                    requireActivity().runOnUiThread(this::loadSessions);
                });
            })
            .setNeutralButton("Cancel", null)
            .show();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (isRecording) stopRec();
    }

    // ── Adapter ────────────────────────────────────────────────────────────────
    private class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.VH> {
        private final List<SessionInfo> sessions = new ArrayList<>();

        void setSessions(List<SessionInfo> s) {
            sessions.clear(); sessions.addAll(s); notifyDataSetChanged();
        }
        String getLatestSessionId() {
            return sessions.isEmpty() ? null : sessions.get(0).sessionId;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_session, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            SessionInfo si = sessions.get(pos);
            h.tvName.setText(si.sessionId);
            h.tvCount.setText(si.count + " readings");
            h.tvDate.setText(SDF_DISP.format(new Date(si.firstTs)));
            long dur = Math.max(0, (si.lastTs - si.firstTs)) / 1000;
            h.tvDuration.setText(dur + "s");
            h.card.setOnClickListener(v -> showOptions(si));
        }

        @Override public int getItemCount() { return sessions.size(); }

        class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView tvName, tvCount, tvDate, tvDuration;
            VH(@NonNull View v) {
                super(v);
                card       = v.findViewById(R.id.sessionCard);
                tvName     = v.findViewById(R.id.tvSessionName);
                tvCount    = v.findViewById(R.id.tvSessionCount);
                tvDate     = v.findViewById(R.id.tvSessionDate);
                tvDuration = v.findViewById(R.id.tvSessionDuration);
            }
        }
    }
}
