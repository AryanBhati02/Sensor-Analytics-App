package com.mad.sensorapp.views;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class GyroscopeBallView extends View {

    private final Paint paintGrid   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBall   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGlow   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTrail  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Physics state
    private float ballX, ballY;          // current ball centre
    private float velX = 0f, velY = 0f; // velocity
    private float gravX = 0f, gravY = 0f; // from sensor

    // Trail
    private static final int TRAIL_LEN = 20;
    private final float[] trailX = new float[TRAIL_LEN];
    private final float[] trailY = new float[TRAIL_LEN];
    private int trailIdx = 0;

    private float cx, cy, halfW, halfH, ballRadius;
    private static final float DAMPING = 0.88f;
    private static final float ACCEL   = 0.4f;

    private final Runnable physicsLoop = this::updatePhysics;

    public GyroscopeBallView(Context c) { super(c); init(); }
    public GyroscopeBallView(Context c, AttributeSet a) { super(c, a); init(); }
    public GyroscopeBallView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(1f);
        paintGrid.setColor(0xFF1E2A3A);
        paintGrid.setPathEffect(new DashPathEffect(new float[]{6f,6f}, 0));

        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(2.5f);
        paintBorder.setColor(0xFF1E2A3A);

        paintGlow.setStyle(Paint.Style.FILL);
        paintGlow.setMaskFilter(new BlurMaskFilter(24f, BlurMaskFilter.Blur.OUTER));

        paintTrail.setStyle(Paint.Style.FILL);

        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cx = w / 2f; cy = h / 2f;
        halfW = w * 0.42f; halfH = h * 0.42f;
        ballRadius = Math.min(w, h) * 0.10f;
        ballX = cx; ballY = cy;
        java.util.Arrays.fill(trailX, cx);
        java.util.Arrays.fill(trailY, cy);

        // Ball radial gradient
        updateBallPaint();
    }

    private void updateBallPaint() {
        if (ballRadius <= 0) return;
        paintBall.setShader(new RadialGradient(
                ballX - ballRadius * 0.3f, ballY - ballRadius * 0.3f, ballRadius,
                new int[]{0xFFAAEEFF, 0xFF00E5FF, 0xFF0077AA},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();

        // ── Grid ──────────────────────────────────────────────────────────────
        for (int i = -3; i <= 3; i++) {
            float gx = cx + halfW * i / 3f;
            float gy = cy + halfH * i / 3f;
            canvas.drawLine(gx, cy - halfH, gx, cy + halfH, paintGrid);
            canvas.drawLine(cx - halfW, gy, cx + halfW, gy, paintGrid);
        }
        // Cross-hairs
        paintGrid.setColor(0x3300E5FF);
        paintGrid.setPathEffect(null);
        canvas.drawLine(cx - halfW, cy, cx + halfW, cy, paintGrid);
        canvas.drawLine(cx, cy - halfH, cx, cy + halfH, paintGrid);
        paintGrid.setColor(0xFF1E2A3A);
        paintGrid.setPathEffect(new DashPathEffect(new float[]{6f,6f}, 0));

        // Border oval
        RectF bounds = new RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
        canvas.drawOval(bounds, paintBorder);

        // ── Trail ─────────────────────────────────────────────────────────────
        for (int i = 0; i < TRAIL_LEN; i++) {
            int idx = (trailIdx - 1 - i + TRAIL_LEN) % TRAIL_LEN;
            float alpha = (float)(TRAIL_LEN - i) / TRAIL_LEN;
            float r     = ballRadius * alpha * 0.7f;
            paintTrail.setColor(Color.argb((int)(60 * alpha), 0, 229, 255));
            canvas.drawCircle(trailX[idx], trailY[idx], r, paintTrail);
        }

        // ── Ball glow ─────────────────────────────────────────────────────────
        paintGlow.setColor(0x2200E5FF);
        canvas.drawCircle(ballX, ballY, ballRadius * 1.6f, paintGlow);

        // ── Ball ──────────────────────────────────────────────────────────────
        updateBallPaint();
        canvas.drawCircle(ballX, ballY, ballRadius, paintBall);

        // Specular highlight
        Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlight.setStyle(Paint.Style.FILL);
        highlight.setColor(0x88FFFFFF);
        canvas.drawCircle(ballX - ballRadius * 0.3f, ballY - ballRadius * 0.3f,
                ballRadius * 0.25f, highlight);

        // Centre dot indicator
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0x44EAEEF5);
        dot.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, 6f, dot);
    }

    private void updatePhysics() {
        if (!isAttachedToWindow()) return;
        // Apply tilt force
        velX = (velX + gravX * ACCEL) * DAMPING;
        velY = (velY + gravY * ACCEL) * DAMPING;

        ballX += velX;
        ballY += velY;

        // Bounce off walls
        if (ballX - ballRadius < cx - halfW) { ballX = cx - halfW + ballRadius; velX =  Math.abs(velX) * 0.5f; }
        if (ballX + ballRadius > cx + halfW) { ballX = cx + halfW - ballRadius; velX = -Math.abs(velX) * 0.5f; }
        if (ballY - ballRadius < cy - halfH) { ballY = cy - halfH + ballRadius; velY =  Math.abs(velY) * 0.5f; }
        if (ballY + ballRadius > cy + halfH) { ballY = cy + halfH - ballRadius; velY = -Math.abs(velY) * 0.5f; }

        // Record trail
        trailX[trailIdx] = ballX;
        trailY[trailIdx] = ballY;
        trailIdx = (trailIdx + 1) % TRAIL_LEN;

        invalidate();
        postDelayed(physicsLoop, 16); // ~60fps
    }

    /** Called from sensor: x=lateral tilt, y=forward tilt */
    public void setTilt(float x, float y) {
        gravX =  x;  // positive x = tilt right = ball goes right
        gravY = -y;  // positive y = tilt back  = ball goes up
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(physicsLoop);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(physicsLoop);
    }
}
