package com.mad.sensorapp.server;

import android.util.Log;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Serves live sensor JSON on port 8080.
 * Access from PC browser on same WiFi: http://<phone-ip>:8080
 *
 * Uses plain Java ServerSocket (no external deps) for maximum compatibility.
 * Endpoints:
 *   GET /          → HTML dashboard with auto-refresh
 *   GET /sensors   → JSON snapshot
 *   GET /history   → JSON array of last 50 readings
 */
public class SensorHttpServer {

    private static final String TAG  = "SensorHttpServer";
    public  static final int    PORT = 8080;

    // Live sensor values – updated by ToolsFragment
    public volatile float  accelX = 0, accelY = 0, accelZ = 0, accelMag = 0;
    public volatile float  light  = 0;
    public volatile float  proximity = 0;
    public volatile String activity  = "Unknown";
    public volatile int    stepCount = 0;

    // History ring buffer
    private final LinkedList<String> history    = new LinkedList<>();
    private static final int         MAX_HISTORY = 50;

    // Server state
    private ServerSocket           serverSocket;
    private ExecutorService        threadPool;
    private volatile boolean       running = false;
    private int                    connectedClients = 0;

    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    public void start() throws IOException {
        serverSocket = new ServerSocket(PORT);
        serverSocket.setSoTimeout(1000); // 1s accept timeout so we can check running flag
        threadPool   = Executors.newCachedThreadPool();
        running      = true;

        threadPool.submit(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    connectedClients++;
                    threadPool.submit(() -> handleClient(client));
                } catch (SocketTimeoutException ignored) {
                    // Normal – loop to check running flag
                } catch (IOException e) {
                    if (running) Log.w(TAG, "Accept error: " + e.getMessage());
                }
            }
        });
        Log.d(TAG, "HTTP server started on port " + PORT);
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        if (threadPool != null) threadPool.shutdownNow();
    }

    public int getClientCount() { return connectedClients; }

    /** Call this whenever sensor values update to append to history */
    public void broadcastSensorUpdate() {
        String json = buildSensorJson();
        synchronized (history) {
            history.addFirst(json);
            if (history.size() > MAX_HISTORY) history.removeLast();
        }
    }

    // ── HTTP request handler ─────────────────────────────────────────────────
    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(3000);
            BufferedReader  in  = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter     out = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(client.getOutputStream())));

            String requestLine = in.readLine();
            if (requestLine == null) { client.close(); return; }

            // Read remaining headers (discard)
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) { /* skip */ }

            // Parse path
            String path = "/";
            String[] parts = requestLine.split(" ");
            if (parts.length >= 2) path = parts[1];

            String body, contentType;
            switch (path) {
                case "/sensors":
                case "/api/sensors":
                    body        = buildSensorJson();
                    contentType = "application/json";
                    break;
                case "/history":
                case "/api/history":
                    body        = buildHistoryJson();
                    contentType = "application/json";
                    break;
                default:
                    body        = buildDashboardHtml();
                    contentType = "text/html; charset=utf-8";
                    break;
            }

            out.print("HTTP/1.1 200 OK\r\n");
            out.print("Content-Type: " + contentType + "\r\n");
            out.print("Content-Length: " + body.getBytes("UTF-8").length + "\r\n");
            out.print("Access-Control-Allow-Origin: *\r\n");
            out.print("Connection: close\r\n");
            out.print("\r\n");
            out.print(body);
            out.flush();

        } catch (IOException e) {
            Log.w(TAG, "Client error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
            connectedClients = Math.max(0, connectedClients - 1);
        }
    }

    // ── Builders ──────────────────────────────────────────────────────────────
    private String buildSensorJson() {
        return String.format(Locale.US,
            "{\"ts\":\"%s\",\"accel\":{\"x\":%.3f,\"y\":%.3f,\"z\":%.3f,\"mag\":%.3f}," +
            "\"light\":%.1f,\"proximity\":%.1f,\"activity\":\"%s\",\"steps\":%d}",
            sdf.format(new Date()), accelX, accelY, accelZ, accelMag,
            light, proximity, activity, stepCount);
    }

    private String buildHistoryJson() {
        StringBuilder sb = new StringBuilder("[");
        synchronized (history) {
            List<String> snap = new ArrayList<>(history);
            for (int i = 0; i < snap.size(); i++) {
                sb.append(snap.get(i));
                if (i < snap.size()-1) sb.append(",");
            }
        }
        return sb.append("]").toString();
    }

    private String buildDashboardHtml() {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<title>SensorPro Live Dashboard</title>" +
            "<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>" +
            "<style>*{box-sizing:border-box;margin:0;padding:0}" +
            "body{background:#0A0E1A;color:#EAEEF5;font-family:'Segoe UI',Tahoma,sans-serif;padding:20px;line-height:1.6}" +
            "header{display:flex;justify-content:space-between;align-items:center;margin-bottom:30px;border-bottom:1px solid #1E2A3A;padding-bottom:15px}" +
            "h1{color:#00E5FF;font-size:1.5rem;letter-spacing:1px} .sub{color:#4A5568;font-size:0.8rem}" +
            ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:20px}" +
            ".card{background:#131929;border:1px solid #1E2A3A;border-radius:16px;padding:20px;box-shadow:0 4px 20px rgba(0,0,0,0.3)}" +
            ".label{font-size:0.7rem;color:#4A5568;letter-spacing:0.1em;margin-bottom:12px;text-transform:uppercase;font-weight:bold}" +
            ".val{font-size:1.8rem;font-weight:bold;font-family:monospace;margin-bottom:15px}" +
            ".chart-container{height:180px;position:relative}" +
            ".cyan{color:#00E5FF} .amber{color:#FFB300} .green{color:#00E676} .pink{color:#FF4081}" +
            ".status-bar{margin-top:30px;padding:15px;background:#0D1221;border-radius:8px;font-size:0.8rem;color:#4A5568;display:flex;gap:20px;align-items:center}" +
            "#dot{width:10px;height:10px;border-radius:50%;background:#00E676;animation:pulse 1.5s infinite}" +
            "@keyframes pulse{0%{box-shadow:0 0 0 0 rgba(0,230,118,0.7)}70%{box-shadow:0 0 0 10px rgba(0,230,118,0)}100%{box-shadow:0 0 0 0 rgba(0,230,118,0)}}" +
            "a{color:#00E5FF;text-decoration:none} a:hover{text-decoration:underline}</style></head><body>" +
            "<header><div><h1>⬡ SENSORPRO LIVE</h1><div class='sub'>REMOTE SENSOR MONITORING SYSTEM</div></div>" +
            "<div><span id='conn-status' style='color:#00E676'>● SYSTEM ONLINE</span></div></header>" +
            "<div class='grid'>" +
            "  <div class='card'><div class='label'>Accelerometer (m/s²)</div><div class='val cyan' id='acc-val'>0.0 / 0.0 / 0.0</div>" +
            "    <div class='chart-container'><canvas id='accChart'></canvas></div></div>" +
            "  <div class='card'><div class='label'>Light Intensity (lx)</div><div class='val amber' id='lx-val'>0</div>" +
            "    <div class='chart-container'><canvas id='lightChart'></canvas></div></div>" +
            "  <div class='card'><div class='label'>Device Proximity</div><div class='val green' id='prox-val'>—</div>" +
            "    <div class='chart-container'><canvas id='proxChart'></canvas></div></div>" +
            "  <div class='card'><div class='label'>Activity & Pedometer</div>" +
            "    <div style='display:flex;justify-content:space-between;margin-top:10px'>" +
            "      <div><div class='label' style='margin-bottom:4px'>CURRENT MOTION</div><div class='val' id='act-val' style='font-size:1.4rem'>Stationary</div></div>" +
            "      <div style='text-align:right'><div class='label' style='margin-bottom:4px'>TOTAL STEPS</div><div class='val green' id='steps-val' style='font-size:1.4rem'>0</div></div>" +
            "    </div></div>" +
            "</div>" +
            "<div class='status-bar'><div id='dot'></div><span>Receiving real-time telemetry</span>" +
            "<span>API: <a href='/sensors'>/sensors</a></span>" +
            "<span>History: <a href='/history'>/history</a></span></div>" +
            "<script>" +
            "const MAX_POINTS = 30; const dataLog = { t:[], ax:[], ay:[], az:[], lx:[], px:[] };" +
            "function createChart(id, label, color, fill=false) {" +
            "  return new Chart(document.getElementById(id), { type:'line', data: { labels:[], datasets:[{ label:label, data:[], borderColor:color, backgroundColor:color+'22', fill:fill, tension:0.4, borderWidth:2, pointRadius:0 }] }," +
            "    options: { responsive:true, maintainAspectRatio:false, plugins:{legend:{display:false}}, scales:{ x:{display:false}, y:{grid:{color:'#1E2A3A'},ticks:{color:'#4A5568',font:{size:10}}} } } });" +
            "}" +
            "const accChart = new Chart(document.getElementById('accChart'), { type:'line', data: { labels:[], datasets:[" +
            "  {label:'X', data:[], borderColor:'#00E5FF', tension:0.4, borderWidth:2, pointRadius:0}," +
            "  {label:'Y', data:[], borderColor:'#00E676', tension:0.4, borderWidth:2, pointRadius:0}," +
            "  {label:'Z', data:[], borderColor:'#FFB300', tension:0.4, borderWidth:2, pointRadius:0}] }," +
            "  options: { responsive:true, maintainAspectRatio:false, plugins:{legend:{display:false}}, scales:{ x:{display:false}, y:{grid:{color:'#1E2A3A'},ticks:{color:'#4A5568',font:{size:10}}} } } });" +
            "const lxChart = createChart('lightChart', 'Lux', '#FFB300', true);" +
            "const pxChart = createChart('proxChart', 'Prox', '#00E676', true);" +
            "function update() { fetch('/sensors').then(r=>r.json()).then(d=>{" +
            "  document.getElementById('acc-val').textContent = `${d.accel.x.toFixed(1)} / ${d.accel.y.toFixed(1)} / ${d.accel.z.toFixed(1)}`;" +
            "  document.getElementById('lx-val').textContent = Math.round(d.light);" +
            "  document.getElementById('prox-val').textContent = d.proximity < 5 ? 'NEAR' : 'FAR ('+d.proximity+'cm)';" +
            "  document.getElementById('act-val').textContent = d.activity;" +
            "  document.getElementById('steps-val').textContent = d.steps;" +
            "  const now = new Date().toLocaleTimeString();" +
            "  [accChart, lxChart, pxChart].forEach(c => { if(c.data.labels.length > MAX_POINTS) { c.data.labels.shift(); c.data.datasets.forEach(ds=>ds.data.shift()); } c.data.labels.push(now); });" +
            "  accChart.data.datasets[0].data.push(d.accel.x); accChart.data.datasets[1].data.push(d.accel.y); accChart.data.datasets[2].data.push(d.accel.z);" +
            "  lxChart.data.datasets[0].data.push(d.light); pxChart.data.datasets[0].data.push(d.proximity);" +
            "  accChart.update('none'); lxChart.update('none'); pxChart.update('none');" +
            "}).catch(e=> { document.getElementById('conn-status').textContent='● CONNECTION LOST'; document.getElementById('conn-status').style.color='#FF4081'; }); }" +
            "setInterval(update, 1000); update();" +
            "</script></body></html>";
    }

    public boolean isRunning() { return running; }
}
