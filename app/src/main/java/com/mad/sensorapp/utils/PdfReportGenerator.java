package com.mad.sensorapp.utils;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import com.mad.sensorapp.data.SensorReading;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Feature 31 – Share as PDF Report
 * Generates a multi-page PDF with:
 *  Page 1: Cover – session title, date, stats summary
 *  Page 2: Accelerometer data table
 *  Page 3: Light + Proximity data table
 */
public class PdfReportGenerator {

    public interface PdfCallback {
        void onSuccess(File pdfFile);
        void onError(String message);
    }

    private static final int PAGE_W = 595; // A4 pt
    private static final int PAGE_H = 842;
    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    // Colors
    private static final int COL_BG    = Color.parseColor("#0A0E1A");
    private static final int COL_CARD  = Color.parseColor("#131929");
    private static final int COL_CYAN  = Color.parseColor("#00E5FF");
    private static final int COL_AMBER = Color.parseColor("#FFB300");
    private static final int COL_GREEN = Color.parseColor("#00E676");
    private static final int COL_TEXT  = Color.parseColor("#EAEEF5");
    private static final int COL_MUTED = Color.parseColor("#4A5568");

    public static void generate(Context context, String sessionId,
                                List<SensorReading> readings, PdfCallback callback) {
        new Thread(() -> {
            try {
                PdfDocument doc = new PdfDocument();

                // ── Page 1: Cover ──────────────────────────────────────────
                PdfDocument.PageInfo info1 =
                        new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create();
                PdfDocument.Page page1 = doc.startPage(info1);
                drawCoverPage(page1.getCanvas(), sessionId, readings);
                doc.finishPage(page1);

                // ── Page 2: Accel Table ────────────────────────────────────
                PdfDocument.PageInfo info2 =
                        new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 2).create();
                PdfDocument.Page page2 = doc.startPage(info2);
                drawDataPage(page2.getCanvas(), "ACCELEROMETER", "ACCEL",
                        readings, "X (m/s²)", "Y (m/s²)", "Z (m/s²)", COL_CYAN);
                doc.finishPage(page2);

                // ── Page 3: Light + Prox ──────────────────────────────────
                PdfDocument.PageInfo info3 =
                        new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 3).create();
                PdfDocument.Page page3 = doc.startPage(info3);
                drawDataPage(page3.getCanvas(), "LIGHT & PROXIMITY", null,
                        readings, "Light (lx)", "Prox (cm)", "—", COL_AMBER);
                doc.finishPage(page3);

                // Save to file
                File outDir = context.getExternalFilesDir(null);
                if (outDir == null) outDir = context.getFilesDir();
                String fname = "SensorReport_" + sessionId + ".pdf";
                File outFile = new File(outDir, fname);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    doc.writeTo(fos);
                }
                doc.close();
                callback.onSuccess(outFile);

            } catch (IOException e) {
                callback.onError("PDF error: " + e.getMessage());
            }
        }).start();
    }

    private static void drawCoverPage(Canvas c, String sessionId, List<SensorReading> readings) {
        Paint bg = new Paint(); bg.setColor(COL_BG); c.drawRect(0, 0, PAGE_W, PAGE_H, bg);

        // Header band
        Paint band = new Paint(); band.setColor(COL_CARD);
        c.drawRect(0, 0, PAGE_W, 180, band);

        // Accent line
        Paint accent = new Paint(); accent.setColor(COL_CYAN);
        c.drawRect(0, 176, PAGE_W, 180, accent);

        // Title
        Paint title = textPaint(48, Typeface.BOLD, COL_CYAN);
        c.drawText("SENSORPRO", 48, 80, title);
        Paint sub = textPaint(16, Typeface.NORMAL, COL_MUTED);
        c.drawText("SENSOR DATA REPORT", 48, 110, sub);

        // Session info box
        Paint box = new Paint(); box.setColor(COL_CARD);
        c.drawRoundRect(new RectF(40, 210, PAGE_W - 40, 380), 12, 12, box);

        Paint key   = textPaint(13, Typeface.NORMAL, COL_MUTED);
        Paint val   = textPaint(15, Typeface.BOLD,   COL_TEXT);
        Paint acnt  = textPaint(15, Typeface.BOLD,   COL_CYAN);

        String date = readings.isEmpty() ? "—" : SDF.format(new Date(readings.get(0).timestamp));
        String end  = readings.isEmpty() ? "—" : SDF.format(new Date(readings.get(readings.size()-1).timestamp));

        drawKV(c, 64, 250, "SESSION ID",    sessionId,    key, acnt);
        drawKV(c, 64, 286, "START TIME",    date,         key, val);
        drawKV(c, 64, 322, "END TIME",      end,          key, val);
        drawKV(c, 64, 358, "TOTAL READINGS",String.valueOf(readings.size()), key, acnt);

        // Stats
        float[] stats = computeStats(readings, "ACCELEROMETER");
        Paint statsTitle = textPaint(14, Typeface.BOLD, COL_CYAN);
        c.drawText("ACCELEROMETER STATISTICS", 48, 420, statsTitle);
        drawStatRow(c, 48, 460, stats, COL_CYAN);

        float[] lstats = computeStats(readings, "LIGHT");
        Paint lt = textPaint(14, Typeface.BOLD, COL_AMBER);
        c.drawText("LIGHT SENSOR STATISTICS", 48, 520, lt);
        drawStatRow(c, 48, 560, lstats, COL_AMBER);

        // Footer
        Paint footer = textPaint(11, Typeface.NORMAL, COL_MUTED);
        c.drawText("Generated by SensorPro v3.0  ·  " + SDF.format(new Date()),
                48, PAGE_H - 40, footer);
        Paint fline = new Paint(); fline.setColor(COL_CARD);
        c.drawRect(0, PAGE_H - 56, PAGE_W, PAGE_H - 55, fline);
    }

    private static void drawDataPage(Canvas c, String title, String filterType,
                                     List<SensorReading> all,
                                     String col1, String col2, String col3, int accent) {
        Paint bg = new Paint(); bg.setColor(COL_BG); c.drawRect(0, 0, PAGE_W, PAGE_H, bg);

        // Header
        Paint band = new Paint(); band.setColor(COL_CARD);
        c.drawRect(0, 0, PAGE_W, 100, band);
        Paint acl = new Paint(); acl.setColor(accent);
        c.drawRect(0, 97, PAGE_W, 100, acl);
        Paint hdr = textPaint(22, Typeface.BOLD, accent);
        c.drawText(title, 40, 64, hdr);

        // Column headers
        float[] xCols = {40f, 180f, 310f, 440f, 530f};
        String[] hdrs = {"TIMESTAMP", col1, col2, col3, "TYPE"};
        Paint colH = textPaint(10, Typeface.BOLD, COL_MUTED);
        Paint divP = new Paint(); divP.setColor(COL_CARD); divP.setStrokeWidth(1f); divP.setStyle(Paint.Style.STROKE);

        float y = 130f;
        for (int i = 0; i < hdrs.length; i++) c.drawText(hdrs[i], xCols[i], y, colH);
        y += 8f;
        c.drawLine(40, y, PAGE_W - 40, y, divP); y += 16f;

        // Data rows
        Paint rowPaint = textPaint(9, Typeface.NORMAL, COL_TEXT);
        Paint altBg = new Paint(); altBg.setColor(COL_CARD);
        SimpleDateFormat rf = new SimpleDateFormat("HH:mm:ss.S", Locale.US);
        int rowH = 20, maxRows = (int)((PAGE_H - y - 50) / rowH);
        int count = 0;

        for (SensorReading r : all) {
            if (filterType != null && !r.sensorType.equals(filterType)) continue;
            if (count >= maxRows) break;
            float ry = y + count * rowH;
            if (count % 2 == 0) c.drawRect(40, ry - 14, PAGE_W - 40, ry + 6, altBg);
            c.drawText(rf.format(new Date(r.timestamp)), xCols[0], ry, rowPaint);
            c.drawText(String.format("%.3f", r.x), xCols[1], ry, rowPaint);
            c.drawText(String.format("%.3f", r.y), xCols[2], ry, rowPaint);
            c.drawText(String.format("%.3f", r.z), xCols[3], ry, rowPaint);
            c.drawText(r.sensorType.substring(0, Math.min(5, r.sensorType.length())), xCols[4], ry, rowPaint);
            count++;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static void drawKV(Canvas c, float x, float y, String k, String v, Paint kp, Paint vp) {
        c.drawText(k + ": ", x, y, kp);
        c.drawText(v, x + 140, y, vp);
    }

    private static void drawStatRow(Canvas c, float x, float y, float[] s, int color) {
        Paint p = textPaint(13, Typeface.NORMAL, color);
        if (s != null)
            c.drawText(String.format("MIN: %.2f   MAX: %.2f   AVG: %.2f   σ: %.2f",
                    s[0], s[1], s[2], s[3]), x, y, p);
        else c.drawText("No data", x, y, p);
    }

    private static float[] computeStats(List<SensorReading> readings, String type) {
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE, sum = 0, sumSq = 0;
        int n = 0;
        for (SensorReading r : readings) {
            if (!r.sensorType.equals(type)) continue;
            float mag = (float) Math.sqrt(r.x*r.x + r.y*r.y + r.z*r.z);
            min = Math.min(min, mag); max = Math.max(max, mag);
            sum += mag; sumSq += mag * mag; n++;
        }
        if (n == 0) return null;
        float avg = sum / n;
        float std = (float) Math.sqrt(sumSq / n - avg * avg);
        return new float[]{min, max, avg, std};
    }

    private static Paint textPaint(float size, int style, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, style));
        p.setColor(color);
        return p;
    }
}
