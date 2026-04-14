package com.mad.sensorapp.views;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/**
 * Heatmap View
 * Renders sensor intensity as a scrolling color-gradient grid.
 * Each column = one time sample. Each row = X, Y, Z axis.
 * Color: blue (low) → cyan → green → yellow → red (high).
 */
public class HeatmapView extends View {

    private static final int COLUMNS    = 120;
    private static final int ROWS       = 3;   // X, Y, Z
    private static final float MAX_VAL  = 20f; // m/s² normalisation

    // Ring buffer columns × rows
    private final float[][] data   = new float[COLUMNS][ROWS];
    private int writeHead = 0;
    private boolean full  = false;

    private final Paint cellPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Y-axis labels
    private final String[] rowLabels = {"X", "Y", "Z"};
    private final int[]    rowColors = {0xFF00E5FF, 0xFF00E676, 0xFFFFB300};

    private float cellW, cellH;
    private static final float LABEL_W = 36f;

    public HeatmapView(Context c) { super(c); init(); }
    public HeatmapView(Context c, AttributeSet a) { super(c, a); init(); }
    public HeatmapView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        labelPaint.setTextSize(26f);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(0.5f);
        gridPaint.setColor(0x1100E5FF);

        axisPaint.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cellW = (w - LABEL_W) / (float) COLUMNS;
        cellH = h / (float) ROWS;
        labelPaint.setTextSize(cellH * 0.42f);
    }

    /** Push new accelerometer sample. Call from onSensorChanged. */
    public void addSample(float x, float y, float z) {
        data[writeHead][0] = x;
        data[writeHead][1] = y;
        data[writeHead][2] = z;
        writeHead = (writeHead + 1) % COLUMNS;
        if (writeHead == 0) full = true;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        canvas.drawColor(0xFF0A0E1A);

        int totalCols = full ? COLUMNS : writeHead;
        if (totalCols == 0) return;

        // Draw cells from oldest → newest, left → right
        for (int col = 0; col < COLUMNS; col++) {
            int bufIdx = full ? (writeHead + col) % COLUMNS : col;
            if (!full && col >= writeHead) break;

            float left = LABEL_W + col * cellW;
            for (int row = 0; row < ROWS; row++) {
                float val  = Math.abs(data[bufIdx][row]);
                float norm = Math.min(1f, val / MAX_VAL);
                cellPaint.setColor(heatColor(norm));
                canvas.drawRect(left, row * cellH, left + cellW, (row + 1) * cellH, cellPaint);
            }
        }

        // Grid lines
        for (int row = 1; row < ROWS; row++) {
            canvas.drawLine(LABEL_W, row * cellH, w, row * cellH, gridPaint);
        }

        // Y-axis label column
        for (int row = 0; row < ROWS; row++) {
            // Dark bg for label strip
            axisPaint.setColor(0xFF0D131F);
            canvas.drawRect(0, row * cellH, LABEL_W, (row + 1) * cellH, axisPaint);

            // Color accent line
            axisPaint.setColor(rowColors[row]);
            canvas.drawRect(0, row * cellH, 4f, (row + 1) * cellH, axisPaint);

            // Label text
            labelPaint.setColor(rowColors[row]);
            float ty = row * cellH + cellH * 0.5f + labelPaint.getTextSize() * 0.38f;
            canvas.drawText(rowLabels[row], LABEL_W * 0.65f, ty, labelPaint);
        }

        // Scan line at write head
        float scanX = LABEL_W + ((full ? COLUMNS - 1 : writeHead - 1) % COLUMNS) * cellW;
        Paint scanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scanPaint.setColor(0xAAFFFFFF);
        scanPaint.setStrokeWidth(2f);
        canvas.drawLine(scanX, 0, scanX, h, scanPaint);
    }

    /** Maps 0→1 normalised value to heat colour: blue → cyan → green → yellow → red */
    private int heatColor(float t) {
        // 4-stop gradient
        float[] stops = {0f, 0.25f, 0.5f, 0.75f, 1f};
        int[] colors  = {0xFF0A0E1A, 0xFF00E5FF, 0xFF00E676, 0xFFFFB300, 0xFFFF3B3B};
        for (int i = 0; i < stops.length - 1; i++) {
            if (t >= stops[i] && t <= stops[i + 1]) {
                float local = (t - stops[i]) / (stops[i + 1] - stops[i]);
                return blendColor(colors[i], colors[i + 1], local);
            }
        }
        return colors[colors.length - 1];
    }

    private int blendColor(int c1, int c2, float t) {
        int r = (int)(Color.red(c1)   * (1 - t) + Color.red(c2)   * t);
        int g = (int)(Color.green(c1) * (1 - t) + Color.green(c2) * t);
        int b = (int)(Color.blue(c1)  * (1 - t) + Color.blue(c2)  * t);
        return Color.argb(255, r, g, b);
    }

    public void reset() {
        for (float[] row : data) java.util.Arrays.fill(row, 0f);
        writeHead = 0; full = false; invalidate();
    }
}
