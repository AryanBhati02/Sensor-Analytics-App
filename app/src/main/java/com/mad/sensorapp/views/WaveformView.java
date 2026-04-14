package com.mad.sensorapp.views;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Oscilloscope-style waveform for accelerometer X/Y/Z axes.
 * Scrolls right-to-left as new data arrives.
 */
public class WaveformView extends View {

    private static final int MAX_POINTS = 200;

    // ── Data buffers ──────────────────────────────────────────────────────────
    private final Deque<Float> bufferX = new ArrayDeque<>(MAX_POINTS);
    private final Deque<Float> bufferY = new ArrayDeque<>(MAX_POINTS);
    private final Deque<Float> bufferZ = new ArrayDeque<>(MAX_POINTS);

    // ── Paints ─────────────────────────────────────────────────────────────────
    private final Paint paintX    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintY    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintZ    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintZero = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLabel= new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Paths ──────────────────────────────────────────────────────────────────
    private final Path pathX = new Path();
    private final Path pathY = new Path();
    private final Path pathZ = new Path();

    // ── Config ────────────────────────────────────────────────────────────────
    private float valueRange = 20f; // ±20 m/s² default
    private boolean showX = true, showY = true, showZ = true;

    public WaveformView(Context c) { super(c); init(); }
    public WaveformView(Context c, AttributeSet a) { super(c, a); init(); }
    public WaveformView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null); // GPU acceleration

        paintX.setStyle(Paint.Style.STROKE);
        paintX.setStrokeWidth(3f);
        paintX.setColor(0xFF00E5FF);
        paintX.setStrokeCap(Paint.Cap.ROUND);
        paintX.setStrokeJoin(Paint.Join.ROUND);

        paintY.setStyle(Paint.Style.STROKE);
        paintY.setStrokeWidth(3f);
        paintY.setColor(0xFF00E676);
        paintY.setStrokeCap(Paint.Cap.ROUND);
        paintY.setStrokeJoin(Paint.Join.ROUND);

        paintZ.setStyle(Paint.Style.STROKE);
        paintZ.setStrokeWidth(3f);
        paintZ.setColor(0xFFFFB300);
        paintZ.setStrokeCap(Paint.Cap.ROUND);
        paintZ.setStrokeJoin(Paint.Join.ROUND);

        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(1f);
        paintGrid.setColor(0x221E2A3A);
        paintGrid.setPathEffect(new DashPathEffect(new float[]{8f, 8f}, 0));

        paintZero.setStyle(Paint.Style.STROKE);
        paintZero.setStrokeWidth(1.5f);
        paintZero.setColor(0x33EAEEF5);

        paintGlow.setStyle(Paint.Style.STROKE);
        paintGlow.setStrokeWidth(6f);
        paintGlow.setMaskFilter(new BlurMaskFilter(10f, BlurMaskFilter.Blur.OUTER));

        paintLabel.setTextSize(28f);
        paintLabel.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        paintLabel.setTextAlign(Paint.Align.LEFT);
    }

    /** Push new accelerometer values – call from onSensorChanged */
    public void addValues(float x, float y, float z) {
        if (bufferX.size() >= MAX_POINTS) { bufferX.pollFirst(); bufferY.pollFirst(); bufferZ.pollFirst(); }
        bufferX.addLast(x);
        bufferY.addLast(y);
        bufferZ.addLast(z);
        autoScale();
        postInvalidateOnAnimation();
    }

    private void autoScale() {
        float maxAbs = 1f;
        for (float v : bufferX) maxAbs = Math.max(maxAbs, Math.abs(v));
        for (float v : bufferY) maxAbs = Math.max(maxAbs, Math.abs(v));
        for (float v : bufferZ) maxAbs = Math.max(maxAbs, Math.abs(v));
        valueRange = maxAbs * 1.3f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        float midY = h / 2f;

        // ── Grid ──────────────────────────────────────────────────────────────
        for (int lines = 1; lines <= 3; lines++) {
            float yPos = midY - (h / 2f) * (lines / 4f);
            canvas.drawLine(0, yPos, w, yPos, paintGrid);
            canvas.drawLine(0, midY + (h/2f)*(lines/4f), w, midY + (h/2f)*(lines/4f), paintGrid);
        }
        // Vertical grid
        for (int vl = 1; vl <= 7; vl++) {
            canvas.drawLine(w * vl / 8f, 0, w * vl / 8f, h, paintGrid);
        }

        // Zero line
        canvas.drawLine(0, midY, w, midY, paintZero);

        // ── Build paths ───────────────────────────────────────────────────────
        drawBuffer(canvas, bufferX, w, h, midY, paintX, paintGlow, 0x4400E5FF, showX);
        drawBuffer(canvas, bufferY, w, h, midY, paintY, paintGlow, 0x4400E676, showY);
        drawBuffer(canvas, bufferZ, w, h, midY, paintZ, paintGlow, 0x44FFB300, showZ);

        // ── Legend ────────────────────────────────────────────────────────────
        float[] lastX = getLastValues();
        drawLegend(canvas, w, lastX);
    }

    private void drawBuffer(Canvas canvas, Deque<Float> buffer, int w, int h,
                            float midY, Paint linePaint, Paint glowPaint,
                            int glowColor, boolean visible) {
        if (!visible || buffer.size() < 2) return;

        Float[] values = buffer.toArray(new Float[0]);
        int n = values.length;
        Path path = new Path();

        float xStep = (float) w / MAX_POINTS;
        float xOffset = w - n * xStep;

        for (int i = 0; i < n; i++) {
            float px = xOffset + i * xStep;
            float py = midY - (values[i] / valueRange) * (h * 0.44f);
            if (i == 0) path.moveTo(px, py);
            else        path.lineTo(px, py);
        }

        glowPaint.setColor(glowColor);
        canvas.drawPath(path, glowPaint);
        canvas.drawPath(path, linePaint);
    }

    private float[] getLastValues() {
        float lx = bufferX.isEmpty() ? 0f : ((ArrayDeque<Float>)bufferX).peekLast();
        float ly = bufferY.isEmpty() ? 0f : ((ArrayDeque<Float>)bufferY).peekLast();
        float lz = bufferZ.isEmpty() ? 0f : ((ArrayDeque<Float>)bufferZ).peekLast();
        return new float[]{lx, ly, lz};
    }

    private void drawLegend(Canvas canvas, int w, float[] last) {
        int[] colors = {0xFF00E5FF, 0xFF00E676, 0xFFFFB300};
        String[] labels = {"X", "Y", "Z"};
        boolean[] visible = {showX, showY, showZ};

        float yBase = getHeight() - 18f;
        float xBase = 16f;
        for (int i = 0; i < 3; i++) {
            if (!visible[i]) continue;
            paintLabel.setColor(colors[i]);
            paintLabel.setAlpha(180);
            canvas.drawText(String.format("%s:%.1f", labels[i], last[i]),
                    xBase + i * (w / 3f), yBase, paintLabel);
        }
    }

    public void setShowX(boolean s) { showX = s; invalidate(); }
    public void setShowY(boolean s) { showY = s; invalidate(); }
    public void setShowZ(boolean s) { showZ = s; invalidate(); }
}
