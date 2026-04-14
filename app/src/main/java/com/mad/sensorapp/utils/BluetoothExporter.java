package com.mad.sensorapp.utils;

import android.bluetooth.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.mad.sensorapp.data.SensorReading;
import java.io.*;
import java.util.*;

/**
 * Feature 29 – Bluetooth Export
 * Sends CSV data over Bluetooth RFCOMM to a paired device.
 */
public class BluetoothExporter {

    private static final String TAG      = "BluetoothExporter";
    private static final UUID   SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public interface ExportCallback {
        void onDevicesFound(List<BluetoothDevice> devices);
        void onProgress(int sent, int total);
        void onComplete();
        void onError(String message);
    }

    private final Context          context;
    private final BluetoothAdapter adapter;
    private BluetoothSocket        socket;
    private volatile boolean       exporting = false;

    public BluetoothExporter(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager bm = (BluetoothManager)
                context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = (bm != null) ? bm.getAdapter() : null;
    }

    /** Returns true if Bluetooth is available and enabled. */
    public boolean isAvailable() {
        return adapter != null && adapter.isEnabled();
    }

    /** Returns list of already-paired devices (handles Android 12+ permissions). */
    public List<BluetoothDevice> getPairedDevices() {
        if (!isAvailable()) return Collections.emptyList();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return Collections.emptyList();
            }
        }
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            return bonded != null ? new ArrayList<>(bonded) : Collections.emptyList();
        } catch (SecurityException e) {
            Log.w(TAG, "Bluetooth permission: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Connect to device and send readings as CSV. Runs on background thread. */
    public void exportToCsv(BluetoothDevice device, List<SensorReading> readings,
                             ExportCallback callback) {
        if (!isAvailable()) { callback.onError("Bluetooth not available or not enabled"); return; }
        if (readings.isEmpty()) { callback.onError("No data to export"); return; }

        exporting = true;
        new Thread(() -> {
            try {
                // Create RFCOMM socket
                // Try secure socket first, fall back to insecure
                try {
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                } catch (IOException e1) {
                    socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                }
                try { adapter.cancelDiscovery(); } catch (SecurityException ignored) {}
                try {
                    socket.connect();
                } catch (IOException e1) {
                    // Fallback: use reflection to get socket on API 26+
                    try {
                        java.lang.reflect.Method m = device.getClass().getMethod(
                            "createRfcommSocket", int.class);
                        socket = (android.bluetooth.BluetoothSocket) m.invoke(device, 1);
                        socket.connect();
                    } catch (Exception e2) {
                        throw new IOException("All connection methods failed: " + e1.getMessage());
                    }
                }

                // Build CSV payload
                StringBuilder sb = new StringBuilder("id,type,x,y,z,timestamp\n");
                for (SensorReading r : readings) {
                    sb.append(r.id).append(',').append(r.sensorType).append(',')
                      .append(r.x).append(',').append(r.y).append(',')
                      .append(r.z).append(',').append(r.timestamp).append('\n');
                }
                byte[] data = sb.toString().getBytes("UTF-8");

                OutputStream out = socket.getOutputStream();
                int chunkSize = 512, sent = 0;
                while (sent < data.length && exporting) {
                    int len = Math.min(chunkSize, data.length - sent);
                    out.write(data, sent, len);
                    out.flush();
                    sent += len;
                    final int s = sent, t = data.length;
                    callback.onProgress(s, t);
                }
                out.close();
                callback.onComplete();

            } catch (SecurityException e) {
                callback.onError("Bluetooth permission denied — grant BLUETOOTH_CONNECT in settings");
            } catch (IOException e) {
                callback.onError("Connection failed: " + e.getMessage());
            } finally {
                close();
                exporting = false;
            }
        }).start();
    }

    public void cancel() { exporting = false; close(); }

    private void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public static String deviceLabel(BluetoothDevice d) {
        try {
            String name = d.getName();
            return (name != null && !name.isEmpty()) ? name : d.getAddress();
        } catch (SecurityException e) {
            return d.getAddress();
        }
    }
}
