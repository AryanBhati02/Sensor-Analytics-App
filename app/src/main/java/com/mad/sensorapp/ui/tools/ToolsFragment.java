package com.mad.sensorapp.ui.tools;

import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.hardware.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.work.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.mad.sensorapp.R;
import com.mad.sensorapp.data.*;
import com.mad.sensorapp.server.SensorHttpServer;
import com.mad.sensorapp.service.ScheduledRecordingWorker;
import com.mad.sensorapp.utils.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ToolsFragment extends Fragment implements SensorEventListener {

    // Sensors
    private SensorManager sm;
    private Sensor accel, light, proxSensor;

    // Server
    private SensorHttpServer httpServer;
    private boolean serverRunning = false;

    // Simulator
    private SensorSimulator simulator;
    private boolean simActive = false;

    // Benchmark
    private SensorBenchmark benchmark;
    private boolean benchRunning = false;

    // Trigger
    private TriggerActionsManager triggerActions;
    private ShakeDetector shakeDetector;

    // Gesture
    private GestureRecognizer gestureRecognizer;
    private boolean recognising = false;

    // Bluetooth
    private BluetoothExporter btExporter;

    // DB
    private SensorDatabase db;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    // Console lines
    private final LinkedList<String> console = new LinkedList<>();
    private static final int MAX_CONSOLE = 50;

    // Live sensor values
    private float lastX=0, lastY=0, lastZ=0, lastLight=0, lastProx=0;

    // ── Views ─────────────────────────────────────────────────────────────────
    // Server
    private MaterialButton btnServer;
    private TextView tvServerUrl, tvServerStatus, tvServerInfo;
    // Sim
    private MaterialButton btnSim;
    private Spinner spinSim;
    private TextView tvSimStatus;
    // Benchmark
    private MaterialButton btnBench;
    private TextView tvBenchResults;
    // Trigger
    private SwitchMaterial swFlash, swSilence;
    private TextView tvTriggerStatus;
    // Gesture
    private MaterialButton btnGestureRecord, btnGestureRecognise, btnGestureClear;
    private TextView tvGestureStatus, tvGestureTemplates;
    // Console
    private TextView tvConsole;
    private ScrollView scrollConsole;
    // Schedule
    private MaterialButton btnSchedule;
    private TextView tvScheduleStatus;
    // Bluetooth
    private MaterialButton btnBluetooth;
    private TextView tvBluetoothStatus;
    // PDF
    private MaterialButton btnPdf;
    private TextView tvPdfStatus;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_tools, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle s) {
        super.onViewCreated(root, s);
        bindViews(root);
        initComponents();
        setupListeners();
    }

    private void bindViews(View root) {
        btnServer       = root.findViewById(R.id.btnServerToggle);
        tvServerUrl     = root.findViewById(R.id.tvServerUrl);
        tvServerStatus  = root.findViewById(R.id.tvServerStatus);
        tvServerInfo    = root.findViewById(R.id.tvServerInfo);
        btnSim          = root.findViewById(R.id.btnSimToggle);
        spinSim         = root.findViewById(R.id.spinnerSimMode);
        tvSimStatus     = root.findViewById(R.id.tvSimStatus);
        btnBench        = root.findViewById(R.id.btnBenchmark);
        tvBenchResults  = root.findViewById(R.id.tvBenchResults);
        swFlash         = root.findViewById(R.id.switchFlashlight);
        swSilence       = root.findViewById(R.id.switchSilence);
        tvTriggerStatus = root.findViewById(R.id.tvTriggerStatus);
        btnGestureRecord    = root.findViewById(R.id.btnRecordGesture);
        btnGestureRecognise = root.findViewById(R.id.btnToggleRecognize);
        btnGestureClear     = root.findViewById(R.id.btnGestureClear);
        tvGestureStatus     = root.findViewById(R.id.tvGestureStatus);
        tvGestureTemplates  = root.findViewById(R.id.tvGestureTemplates);
        tvConsole       = root.findViewById(R.id.tvConsole);
        scrollConsole   = root.findViewById(R.id.scrollConsole);
        btnSchedule     = root.findViewById(R.id.btnSchedule);
        tvScheduleStatus= root.findViewById(R.id.tvScheduleStatus);
        btnBluetooth    = root.findViewById(R.id.btnBluetooth);
        tvBluetoothStatus = root.findViewById(R.id.tvBluetoothStatus);
        btnPdf          = root.findViewById(R.id.btnPdf);
        tvPdfStatus     = root.findViewById(R.id.tvPdfStatus);
    }

    private void initComponents() {
        sm        = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        accel     = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        light     = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        proxSensor= sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        db        = SensorDatabase.getInstance(requireContext());
        btExporter= new BluetoothExporter(requireContext());

        // ── Simulator ──────────────────────────────────────────────────────────
        simulator = new SensorSimulator(new SensorSimulator.SimulatorListener() {
            @Override public void onSimulatedAccelerometer(float x, float y, float z) {
                lastX=x; lastY=y; lastZ=z;
                if (httpServer!=null) { httpServer.accelX=x; httpServer.accelY=y; httpServer.accelZ=z;
                    httpServer.accelMag=(float)Math.sqrt(x*x+y*y+z*z); }
                if (tvSimStatus!=null)
                    tvSimStatus.post(()->tvSimStatus.setText(
                        String.format("SIM ▶  X:%.1f Y:%.1f Z:%.1f", x,y,z)));
                appendConsole(String.format("SIM  X:%.2f Y:%.2f Z:%.2f",x,y,z));
            }
            @Override public void onSimulatedLight(float lux) {
                lastLight=lux;
                if (httpServer!=null) httpServer.light=lux;
            }
            @Override public void onSimulatedProximity(float cm) { lastProx=cm; }
            @Override public void onSimulatedStep() { appendConsole("SIM step"); }
        });

        if (spinSim != null) {
            String[] modes = {"🧍 Stationary","🚶 Walking","🏃 Running","🚗 Driving","📳 Shake","⬇️ Free Fall"};
            spinSim.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, modes));
        }

        // ── Benchmark ──────────────────────────────────────────────────────────
        benchmark = new SensorBenchmark(requireContext(), new SensorBenchmark.BenchmarkCallback() {
            @Override public void onProgress(String name, int samples) {
                if(tvBenchResults!=null)
                    tvBenchResults.post(()->tvBenchResults.setText("Testing " + name + "… " + samples + " samples"));
            }
            @Override public void onComplete(List<SensorBenchmark.BenchmarkResult> results) {
                StringBuilder sb = new StringBuilder();
                for (SensorBenchmark.BenchmarkResult r : results)
                    sb.append(String.format("%-14s %5.0f Hz  %s\n", r.sensorName+":", r.achievedHz, r.rating));
                if(tvBenchResults!=null)
                    tvBenchResults.post(()->{
                        tvBenchResults.setText(sb.toString().trim());
                        if(btnBench!=null){btnBench.setEnabled(true);btnBench.setText("⚡ Run Benchmark");}
                    });
                benchRunning=false;
            }
            @Override public void onError(String m) {
                if(tvBenchResults!=null) tvBenchResults.post(()->tvBenchResults.setText("Error: "+m));
                benchRunning=false;
            }
        });

        // ── Trigger Actions ────────────────────────────────────────────────────
        triggerActions = new TriggerActionsManager(requireContext(),
            new TriggerActionsManager.TriggerCallback() {
                @Override public void onFlashlightToggled(boolean on) {
                    if(tvTriggerStatus!=null)
                        tvTriggerStatus.post(()->tvTriggerStatus.setText(on?"🔦 Flashlight ON":"🔦 Flashlight OFF"));
                }
                @Override public void onAudioSilenced(boolean s) {
                    if(tvTriggerStatus!=null)
                        tvTriggerStatus.post(()->tvTriggerStatus.setText(s?"🔇 Audio silenced":"🔔 Audio restored"));
                }
            });

        shakeDetector = new ShakeDetector(count -> {
            if (triggerActions != null) {
                triggerActions.onShakeDetected();
            }
        });

        // ── Gesture ────────────────────────────────────────────────────────────
        gestureRecognizer = new GestureRecognizer(new GestureRecognizer.GestureListener() {
            @Override public void onGestureRecognized(String name, float conf) {
                if(tvGestureStatus!=null)
                    tvGestureStatus.post(()->tvGestureStatus.setText(
                        "✅ Detected: " + name + "  (" + (int)(conf*100) + "% confidence)"));
                appendConsole("Gesture: " + name + " " + (int)(conf*100) + "%");
            }
            @Override public void onGestureRecordingDone(String name) {
                if(tvGestureStatus!=null)
                    tvGestureStatus.post(()->{
                        tvGestureStatus.setText("⏺ Recorded: " + name);
                        updateGestureTemplateList();
                    });
            }
            @Override public void onError(String msg) {
                if(tvGestureStatus!=null)
                    tvGestureStatus.post(()->tvGestureStatus.setText("⚠️ " + msg));
            }
        });
        updateGestureTemplateList();
    }

    private void setupListeners() {
        // ── Server ─────────────────────────────────────────────────────────────
        if (btnServer!=null) btnServer.setOnClickListener(v -> {
            if (!serverRunning) startServer(); else stopServer();
        });

        // ── Simulator ──────────────────────────────────────────────────────────
        if (btnSim!=null) btnSim.setOnClickListener(v -> {
            if (!simActive) {
                int idx = spinSim!=null ? spinSim.getSelectedItemPosition() : 0;
                SensorSimulator.SimMode[] modes = SensorSimulator.SimMode.values();
                SensorSimulator.SimMode mode = idx < modes.length ? modes[idx] : SensorSimulator.SimMode.WALKING;
                simulator.start(mode);
                simActive = true;
                btnSim.setText("⏹ Stop Simulation");
                appendConsole("Simulation started: " + mode.label);
            } else {
                simulator.stop();
                simActive = false;
                btnSim.setText("▶ Start Simulation");
                if(tvSimStatus!=null) tvSimStatus.setText("Simulation stopped");
                appendConsole("Simulation stopped");
            }
        });

        // ── Benchmark ──────────────────────────────────────────────────────────
        if (btnBench!=null) btnBench.setOnClickListener(v -> {
            if (benchRunning) return;
            benchRunning = true;
            btnBench.setEnabled(false);
            btnBench.setText("Testing…");
            if(tvBenchResults!=null) tvBenchResults.setText("Starting benchmark (12 seconds)…");
            appendConsole("Benchmark started");
            benchmark.start();
        });

        // ── Triggers ───────────────────────────────────────────────────────────
        if (swFlash!=null) swFlash.setOnCheckedChangeListener((b,on) -> {
            triggerActions.shakeFlashlightEnabled = on;
            appendConsole("Shake→Flashlight: " + (on?"ON":"OFF"));
        });
        if (swSilence!=null) swSilence.setOnCheckedChangeListener((b,on) -> {
            triggerActions.proximitySilenceEnabled = on;
            appendConsole("Proximity→Silence: " + (on?"ON":"OFF"));
        });

        // ── Gesture ────────────────────────────────────────────────────────────
        if (btnGestureRecord!=null) btnGestureRecord.setOnClickListener(v -> {
            EditText et = new EditText(requireContext());
            et.setHint("e.g. Shake, Tilt Left, Flip");
            et.setPadding(40,20,40,20);
            new AlertDialog.Builder(requireContext())
                .setTitle("🤌 Record New Gesture")
                .setMessage("Perform the gesture for ~2 seconds after tapping Record.")
                .setView(et)
                .setPositiveButton("⏺ Record", (d,w)->{
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) {
                        gestureRecognizer.startRecording(name);
                        if(tvGestureStatus!=null)
                            tvGestureStatus.setText("⏺ Recording \"" + name + "\"\nPerform...");
                        appendConsole("Recording gesture: " + name);
                    }
                })
                .setNegativeButton("Cancel",null).show();
        });

        if (btnGestureRecognise!=null) btnGestureRecognise.setOnClickListener(v -> {
            recognising = !recognising;
            gestureRecognizer.setRecognising(recognising);
            btnGestureRecognise.setText(recognising ? "⏹ Stop Recognising" : "🎯 Start Recognising");
            if(tvGestureStatus!=null && !recognising)
                tvGestureStatus.setText("Recognition paused");
            appendConsole("Gesture recognition: " + (recognising?"ON":"OFF"));
        });

        if (btnGestureClear!=null) btnGestureClear.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                .setTitle("Clear Gestures")
                .setMessage("Delete all recorded gesture templates?")
                .setPositiveButton("Delete", (d,w)->{
                    for (GestureRecognizer.GestureTemplate t : gestureRecognizer.getTemplates())
                        gestureRecognizer.deleteTemplate(t.name);
                    updateGestureTemplateList();
                    if(tvGestureStatus!=null) tvGestureStatus.setText("All templates cleared");
                })
                .setNegativeButton("Cancel",null).show();
        });

        // ── Schedule ───────────────────────────────────────────────────────────
        if (btnSchedule!=null) btnSchedule.setOnClickListener(v -> showScheduleDialog());

        // ── Bluetooth ──────────────────────────────────────────────────────────
        if (btnBluetooth!=null) btnBluetooth.setOnClickListener(v -> showBluetoothDialog());

        // ── PDF ────────────────────────────────────────────────────────────────
        if (btnPdf!=null) btnPdf.setOnClickListener(v -> exportPdf());
    }

    // ── HTTP Server ────────────────────────────────────────────────────────────
    private void startServer() {
        try {
            httpServer = new SensorHttpServer();
            httpServer.start();
            serverRunning = true;
            String ip = getLocalIp();
            if(tvServerUrl!=null)  tvServerUrl.setText("http://" + ip + ":" + SensorHttpServer.PORT);
            if(tvServerStatus!=null) {
                tvServerStatus.setText("● ONLINE");
                tvServerStatus.setTextColor(0xFF00E676);
            }
            if(tvServerInfo!=null)
                tvServerInfo.setText("Open this URL in your PC browser (same WiFi).\nShows live sensor data that auto-refreshes.");
            if(btnServer!=null) btnServer.setText("⏹ Stop Server");
            appendConsole("HTTP server started → http://" + ip + ":" + SensorHttpServer.PORT);
        } catch (Exception e) {
            if(tvServerStatus!=null) tvServerStatus.setText("Failed: " + e.getMessage());
        }
    }

    private void stopServer() {
        if(httpServer!=null) { httpServer.stop(); httpServer=null; }
        serverRunning=false;
        if(tvServerUrl!=null) tvServerUrl.setText("—");
        if(tvServerStatus!=null){ tvServerStatus.setText("● OFFLINE"); tvServerStatus.setTextColor(0xFF4A5568); }
        if(btnServer!=null) btnServer.setText("▶ Start Server");
    }

    private String getLocalIp() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                // Ignore loopback, down, and virtual interfaces
                if (intf.isLoopback() || !intf.isUp() || intf.getName().startsWith("dummy")) continue;
                
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) return sAddr;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    // ── Schedule ───────────────────────────────────────────────────────────────
    private void showScheduleDialog() {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_schedule, null);
        EditText et = v.findViewById(R.id.etScheduleDuration);
        new AlertDialog.Builder(requireContext())
            .setTitle("⏰ Schedule Recording")
            .setMessage("Auto-record every hour. Enter duration in seconds:")
            .setView(v)
            .setPositiveButton("Schedule", (d,w)->{
                String s = et.getText().toString().trim();
                int sec = s.isEmpty() ? 60 : Integer.parseInt(s);
                OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(ScheduledRecordingWorker.class)
                    .setInputData(new Data.Builder().putInt(ScheduledRecordingWorker.KEY_DURATION_SEC, sec).build())
                    .build();
                WorkManager.getInstance(requireContext()).enqueue(req);
                if(tvScheduleStatus!=null)
                    tvScheduleStatus.setText("⏰ Recording for "+sec+"s scheduled");
                appendConsole("Scheduled "+sec+"s recording");
            })
            .setNegativeButton("Cancel",null).show();
    }

    // ── Bluetooth ──────────────────────────────────────────────────────────────
    private void showBluetoothDialog() {
        if (!btExporter.isAvailable()) {
            Toast.makeText(requireContext(),"Bluetooth not available/enabled",Toast.LENGTH_SHORT).show();
            return;
        }
        List<BluetoothDevice> devices = btExporter.getPairedDevices();
        if (devices.isEmpty()) {
            Toast.makeText(requireContext(),"No paired devices.\nPair a device in system Bluetooth settings first.",Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = devices.stream().map(BluetoothExporter::deviceLabel).toArray(String[]::new);
        new AlertDialog.Builder(requireContext())
            .setTitle("📶 Select Paired Device")
            .setItems(names, (d,idx)->{
                if(tvBluetoothStatus!=null) tvBluetoothStatus.setText("Connecting to " + names[idx] + "…");
                appendConsole("BT connecting to " + names[idx]);
                exec.execute(()->{
                    List<SensorReading> data = db.sensorDao().getSession(getLatestSession());
                    if (data.isEmpty()) {
                        requireActivity().runOnUiThread(()->
                            Toast.makeText(requireContext(),"No session data. Record some data first.",Toast.LENGTH_SHORT).show());
                        return;
                    }
                    btExporter.exportToCsv(devices.get(idx), data, new BluetoothExporter.ExportCallback(){
                        @Override public void onDevicesFound(List<BluetoothDevice> d2){}
                        @Override public void onProgress(int s, int t){
                            int pct = t>0 ? s*100/t : 0;
                            requireActivity().runOnUiThread(()->
                                tvBluetoothStatus.setText("Sending " + pct + "%…"));
                        }
                        @Override public void onComplete(){
                            requireActivity().runOnUiThread(()->{
                                tvBluetoothStatus.setText("✅ Transfer complete!");
                                appendConsole("BT export done");
                            });
                        }
                        @Override public void onError(String msg){
                            requireActivity().runOnUiThread(()->{
                                tvBluetoothStatus.setText("❌ " + msg);
                                appendConsole("BT error: " + msg);
                            });
                        }
                    });
                });
            })
            .setNegativeButton("Cancel",null).show();
    }

    // ── PDF ────────────────────────────────────────────────────────────────────
    private void exportPdf() {
        if(tvPdfStatus!=null) tvPdfStatus.setText("Generating PDF…");
        exec.execute(()->{
            String sid = getLatestSession();
            List<SensorReading> data = db.sensorDao().getSession(sid);
            PdfReportGenerator.generate(requireContext(), sid.isEmpty()?"NoData":sid, data,
                new PdfReportGenerator.PdfCallback(){
                    @Override public void onSuccess(java.io.File f){
                        requireActivity().runOnUiThread(()->{
                            if(tvPdfStatus!=null) tvPdfStatus.setText("✅ " + f.getName());
                            sharePdf(f);
                        });
                    }
                    @Override public void onError(String m){
                        requireActivity().runOnUiThread(()->
                            tvPdfStatus.setText("❌ " + m));
                    }
                });
        });
    }

    private void sharePdf(java.io.File f) {
        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
            requireContext(), requireContext().getPackageName()+".provider", f);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i,"Share PDF Report"));
    }

    // ── Console ────────────────────────────────────────────────────────────────
    private void appendConsole(String line) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(()->{
            String ts = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                    .format(new java.util.Date());
            console.addFirst("[" + ts + "] " + line);
            if (console.size() > MAX_CONSOLE) console.removeLast();
            if (tvConsole!=null) {
                StringBuilder sb = new StringBuilder();
                for (String l : console) sb.append(l).append('\n');
                tvConsole.setText(sb.toString());
            }
        });
    }

    private void updateGestureTemplateList() {
        if (tvGestureTemplates==null) return;
        List<GestureRecognizer.GestureTemplate> ts = gestureRecognizer.getTemplates();
        StringBuilder sb = new StringBuilder();
        sb.append("Templates (").append(ts.size()).append("):\n");
        for (GestureRecognizer.GestureTemplate t : ts)
            sb.append("  • ").append(t.name).append('\n');
        tvGestureTemplates.setText(sb.toString().trim());
    }

    private String getLatestSession() {
        try {
            List<SessionInfo> s = db.sensorDao().getSessionSummaries();
            return s.isEmpty() ? "" : s.get(0).sessionId;
        } catch (Exception e) { return ""; }
    }

    // ── SensorEventListener ───────────────────────────────────────────────────
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                lastX=event.values[0]; lastY=event.values[1]; lastZ=event.values[2];
                gestureRecognizer.addSample(lastX,lastY,lastZ);
                shakeDetector.onSensorChanged(event);
                if(httpServer!=null){
                    httpServer.accelX=lastX; httpServer.accelY=lastY; httpServer.accelZ=lastZ;
                    httpServer.accelMag=(float)Math.sqrt(lastX*lastX+lastY*lastY+lastZ*lastZ);
                    httpServer.broadcastSensorUpdate();
                }
                break;
            case Sensor.TYPE_LIGHT:
                lastLight=event.values[0];
                if(httpServer!=null) httpServer.light=lastLight;
                break;
            case Sensor.TYPE_PROXIMITY:
                lastProx=event.values[0];
                if(httpServer!=null) httpServer.proximity=lastProx;
                float maxR = proxSensor!=null ? proxSensor.getMaximumRange() : 10f;
                triggerActions.onProximityChanged(lastProx, maxR);
                break;
        }
    }
    @Override public void onAccuracyChanged(Sensor s, int a){}

    @Override public void onResume(){
        super.onResume();
        if(accel!=null)     sm.registerListener(this,accel,    SensorManager.SENSOR_DELAY_GAME);
        if(light!=null)     sm.registerListener(this,light,    SensorManager.SENSOR_DELAY_NORMAL);
        if(proxSensor!=null)sm.registerListener(this,proxSensor,SensorManager.SENSOR_DELAY_NORMAL);
    }
    @Override public void onPause(){
        super.onPause();
        sm.unregisterListener(this);
    }
    @Override public void onDestroyView(){
        super.onDestroyView();
        stopServer();
        if(simActive){simulator.stop();simActive=false;}
        triggerActions.release();
        exec.shutdown();
    }
}
