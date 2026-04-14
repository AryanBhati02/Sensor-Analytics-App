package com.mad.sensorapp.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

/**
 * Renders a 3D cube that mirrors the device orientation
 * using a basic orthographic projection with depth shading.
 */
public class OrientationCubeView extends View {

    // ── Cube geometry (unit cube centred at origin) ───────────────────────────
    private static final float[][] VERTICES = {
        {-1, -1, -1}, { 1, -1, -1}, { 1,  1, -1}, {-1,  1, -1}, // back face (0-3)
        {-1, -1,  1}, { 1, -1,  1}, { 1,  1,  1}, {-1,  1,  1}  // front face (4-7)
    };

    // Each face: 4 vertex indices
    private static final int[][] FACES = {
        {4,5,6,7}, // front  (Z+)
        {1,0,3,2}, // back   (Z-)
        {0,4,7,3}, // left   (X-)
        {5,1,2,6}, // right  (X+)
        {3,7,6,2}, // top    (Y-)
        {0,1,5,4}  // bottom (Y+)
    };

    private static final int[] FACE_COLORS = {
        0xFF1A3A5C, // front  – blue
        0xFF1A2A1A, // back   – dark green
        0xFF3A1A1A, // left   – dark red
        0xFF2A2A3A, // right  – dark purple
        0xFF1A3A3A, // top    – dark cyan
        0xFF3A3A1A  // bottom – dark amber
    };

    private static final int[] FACE_STROKE_COLORS = {
        0xFF00E5FF, 0xFF00E676, 0xFFFF3B3B,
        0xFFAA77FF, 0xFF00E5FF, 0xFFFFB300
    };

    // ── Paints ─────────────────────────────────────────────────────────────────
    private final Paint facePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── State ──────────────────────────────────────────────────────────────────
    private float pitch = 0f, roll = 0f, azimuth = 0f;
    private float targetPitch = 0f, targetRoll = 0f, targetAzimuth = 0f;
    private ValueAnimator animator;

    private float cx, cy, scale;
    private final float[] projected2D = new float[VERTICES.length * 2];

    public OrientationCubeView(Context c) { super(c); init(); }
    public OrientationCubeView(Context c, AttributeSet a) { super(c, a); init(); }
    public OrientationCubeView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        facePaint.setStyle(Paint.Style.FILL);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(2.5f);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setMaskFilter(new BlurMaskFilter(16f, BlurMaskFilter.Blur.OUTER));
        glowPaint.setStrokeWidth(4f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cx = w / 2f;
        cy = h / 2f;
        scale = Math.min(w, h) * 0.28f;
        labelPaint.setTextSize(scale * 0.3f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // ── Build rotation matrices ────────────────────────────────────────────
        float[][] rot = buildRotationMatrix(azimuth, pitch, roll);

        // ── Project all vertices ───────────────────────────────────────────────
        float[] pv = new float[VERTICES.length * 2];
        float[] depth = new float[VERTICES.length];
        for (int i = 0; i < VERTICES.length; i++) {
            float[] rv = multiplyMV(rot, VERTICES[i]);
            // Isometric-like projection with slight perspective
            float z = rv[2];
            float px = cx + (rv[0] - z * 0.15f) * scale;
            float py = cy + (rv[1] - z * 0.15f) * scale;
            pv[i * 2]     = px;
            pv[i * 2 + 1] = py;
            depth[i] = z;
        }

        // ── Sort faces by average depth (painter's algorithm) ─────────────────
        Integer[] faceOrder = {0,1,2,3,4,5};
        java.util.Arrays.sort(faceOrder, (a, b) -> {
            float da = 0, db = 0;
            for (int vi : FACES[a]) da += depth[vi];
            for (int vi : FACES[b]) db += depth[vi];
            return Float.compare(da, db); // back-to-front
        });

        // ── Draw faces ─────────────────────────────────────────────────────────
        Path path = new Path();
        for (int fi : faceOrder) {
            int[] face = FACES[fi];

            // Backface culling via normal
            float ax = pv[face[1]*2]   - pv[face[0]*2];
            float ay = pv[face[1]*2+1] - pv[face[0]*2+1];
            float bx = pv[face[2]*2]   - pv[face[0]*2];
            float by = pv[face[2]*2+1] - pv[face[0]*2+1];
            if ((ax * by - ay * bx) > 0) continue; // facing away

            path.reset();
            path.moveTo(pv[face[0]*2], pv[face[0]*2+1]);
            for (int k = 1; k < face.length; k++)
                path.lineTo(pv[face[k]*2], pv[face[k]*2+1]);
            path.close();

            // Depth-based brightness
            float avgDepth = 0;
            for (int vi : face) avgDepth += depth[vi];
            avgDepth /= face.length;
            float brightness = 0.5f + avgDepth * 0.3f;
            brightness = Math.max(0.2f, Math.min(1f, brightness));

            // Face fill with brightness tint
            int baseColor = FACE_COLORS[fi];
            int r = (int)(Color.red(baseColor)   * brightness);
            int g = (int)(Color.green(baseColor) * brightness);
            int b = (int)(Color.blue(baseColor)  * brightness);
            facePaint.setColor(Color.argb(220, r, g, b));
            canvas.drawPath(path, facePaint);

            // Edge glow
            glowPaint.setColor((FACE_STROKE_COLORS[fi] & 0x00FFFFFF) | 0x33000000);
            canvas.drawPath(path, glowPaint);

            // Edge stroke
            edgePaint.setColor(FACE_STROKE_COLORS[fi]);
            edgePaint.setAlpha((int)(180 * brightness));
            canvas.drawPath(path, edgePaint);
        }

        // ── Axis labels ─────────────────────────────────────────────────────────
        drawAxisLabel(canvas, rot, 1.5f, 0, 0, "X", 0xFFFF3B3B, pv);
        drawAxisLabel(canvas, rot, 0, 1.5f, 0, "Y", 0xFF00E676, pv);
        drawAxisLabel(canvas, rot, 0, 0, 1.5f, "Z", 0xFF00E5FF, pv);
    }

    private void drawAxisLabel(Canvas canvas, float[][] rot, float vx, float vy, float vz,
                                String label, int color, float[] pv) {
        float[] rv = multiplyMV(rot, new float[]{vx, vy, vz});
        float lx = cx + (rv[0] - rv[2] * 0.15f) * scale;
        float ly = cy + (rv[1] - rv[2] * 0.15f) * scale + labelPaint.getTextSize() * 0.4f;
        labelPaint.setColor(color);
        canvas.drawText(label, lx, ly, labelPaint);
    }

    /** Set orientation from sensor fusion values (degrees). Animates to target. */
    public void setOrientation(float azimuthDeg, float pitchDeg, float rollDeg) {
        // Simple smoothing using low-pass filter to reduce sensitivity
        float alpha = 0.15f;
        float nextAz = (float) Math.toRadians(azimuthDeg);
        float nextPt = (float) Math.toRadians(pitchDeg);
        float nextRl = (float) Math.toRadians(rollDeg);

        // Handle azimuth wrap-around for smoothing
        float diff = nextAz - azimuth;
        if (diff > Math.PI) diff -= 2 * Math.PI;
        if (diff < -Math.PI) diff += 2 * Math.PI;
        azimuth += alpha * diff;

        pitch = pitch + alpha * (nextPt - pitch);
        roll  = roll  + alpha * (nextRl - roll);

        invalidate();
    }

    // ── Math helpers ──────────────────────────────────────────────────────────
    private float[][] buildRotationMatrix(float az, float pt, float rl) {
        // Rx(pt) * Ry(rl) * Rz(az)
        // This order usually matches standard Android sensor orientation
        float cp = (float)Math.cos(pt), sp = (float)Math.sin(pt);
        float cr = (float)Math.cos(rl), sr = (float)Math.sin(rl);
        float ca = (float)Math.cos(az), sa = (float)Math.sin(az);

        float[][] Rx = {{ 1, 0, 0 }, { 0, cp, -sp }, { 0, sp, cp }};
        float[][] Ry = {{ cr, 0, sr }, { 0, 1, 0 }, { -sr, 0, cr }};
        float[][] Rz = {{ ca, -sa, 0 }, { sa, ca, 0 }, { 0, 0, 1 }};

        // Standard aerospace sequence: Z-Y-X or X-Y-Z depending on frame
        // For visual orientation, Rx * Ry usually maps Pitch and Roll correctly
        return mul3(mul3(Rx, Ry), Rz);
    }

    private float[][] mul3(float[][] A, float[][] B) {
        float[][] C = new float[3][3];
        for (int i=0;i<3;i++) for (int j=0;j<3;j++) for (int k=0;k<3;k++)
            C[i][j] += A[i][k]*B[k][j];
        return C;
    }

    private float[] multiplyMV(float[][] m, float[] v) {
        return new float[]{
            m[0][0]*v[0]+m[0][1]*v[1]+m[0][2]*v[2],
            m[1][0]*v[0]+m[1][1]*v[1]+m[1][2]*v[2],
            m[2][0]*v[0]+m[2][1]*v[1]+m[2][2]*v[2]
        };
    }
}
