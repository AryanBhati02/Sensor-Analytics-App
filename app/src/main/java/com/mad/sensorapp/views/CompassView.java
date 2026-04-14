package com.mad.sensorapp.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

public class CompassView extends View {

    // ── Paints ────────────────────────────────────────────────────────────────
    private final Paint paintBezel       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBezelStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTick        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMajorTick   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCardinal    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintDegree      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintNeedleN     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintNeedleS     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintNeedleHub   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGlow        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHeading     = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── State ─────────────────────────────────────────────────────────────────
    private float currentAngle  = 0f;   // displayed rotation (degrees)
    private float targetAngle   = 0f;   // target from sensor
    private ValueAnimator animator;

    // ── Dimensions (set in onSizeChanged) ────────────────────────────────────
    private float cx, cy, radius;
    private RectF glowOval = new RectF();

    public CompassView(Context context) { super(context); init(); }
    public CompassView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public CompassView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle); init(); }

    private void init() {
        // Bezel gradient fill
        paintBezel.setStyle(Paint.Style.FILL);

        // Bezel stroke
        paintBezelStroke.setStyle(Paint.Style.STROKE);
        paintBezelStroke.setStrokeWidth(3f);
        paintBezelStroke.setColor(0xFF1E2A3A);

        // Minor ticks
        paintTick.setStyle(Paint.Style.STROKE);
        paintTick.setStrokeWidth(1.5f);
        paintTick.setColor(0x554A5568);

        // Major ticks (every 30°)
        paintMajorTick.setStyle(Paint.Style.STROKE);
        paintMajorTick.setStrokeWidth(2.5f);
        paintMajorTick.setColor(0xAA00E5FF);

        // Cardinal labels
        paintCardinal.setTextAlign(Paint.Align.CENTER);
        paintCardinal.setTextSize(36f);
        paintCardinal.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        // Degree labels
        paintDegree.setTextAlign(Paint.Align.CENTER);
        paintDegree.setTextSize(18f);
        paintDegree.setColor(0x88EAEEF5);

        // North needle
        paintNeedleN.setStyle(Paint.Style.FILL);
        paintNeedleN.setColor(0xFFFF3B3B);

        // South needle
        paintNeedleS.setStyle(Paint.Style.FILL);
        paintNeedleS.setColor(0xFFEAEEF5);

        // Needle hub
        paintNeedleHub.setStyle(Paint.Style.FILL);
        paintNeedleHub.setColor(0xFF0A0E1A);

        // Glow
        paintGlow.setStyle(Paint.Style.STROKE);
        paintGlow.setMaskFilter(new BlurMaskFilter(24f, BlurMaskFilter.Blur.OUTER));

        // Heading text
        paintHeading.setTextAlign(Paint.Align.CENTER);
        paintHeading.setTextSize(32f);
        paintHeading.setTypeface(Typeface.create("monospace", Typeface.BOLD));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        cx = w / 2f;
        cy = h / 2f;
        radius = Math.min(w, h) * 0.44f;
        glowOval.set(cx - radius - 10, cy - radius - 10, cx + radius + 10, cy + radius + 10);

        // Bezel radial gradient
        paintBezel.setShader(new RadialGradient(cx, cy, radius,
                new int[]{0xFF1A2535, 0xFF0D1623, 0xFF0A0E1A},
                new float[]{0f, 0.7f, 1f},
                Shader.TileMode.CLAMP));

        paintCardinal.setTextSize(radius * 0.18f);
        paintDegree.setTextSize(radius * 0.09f);
        paintHeading.setTextSize(radius * 0.15f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // ── Background circle ─────────────────────────────────────────────────
        canvas.drawCircle(cx, cy, radius, paintBezel);

        // Outer glow ring
        paintGlow.setColor(0x2200E5FF);
        paintGlow.setStrokeWidth(8f);
        canvas.drawCircle(cx, cy, radius + 4, paintGlow);

        paintBezelStroke.setColor(0xFF1E2A3A);
        canvas.drawCircle(cx, cy, radius, paintBezelStroke);

        // ── Ticks & label ─────────────────────
        canvas.save();
        canvas.rotate(-currentAngle, cx, cy);

        for (int deg = 0; deg < 360; deg += 5) {
            boolean isMajor   = (deg % 30 == 0);
            boolean isCardinal= (deg % 90 == 0);
            float tickOuter   = radius * 0.95f;
            float tickInner   = isMajor ? radius * 0.82f : radius * 0.88f;
            float angleRad    = (float) Math.toRadians(deg);
            float sinA = (float) Math.sin(angleRad);
            float cosA = (float) Math.cos(angleRad);

            Paint tickPaint = isMajor ? paintMajorTick : paintTick;
            if (isCardinal) tickPaint.setColor(0xCC00E5FF);
            else if (isMajor) tickPaint.setColor(0xAA00E5FF);
            canvas.drawLine(cx + sinA * tickInner, cy - cosA * tickInner,
                            cx + sinA * tickOuter, cy - cosA * tickOuter, tickPaint);
            if (isMajor) tickPaint.setColor(0xAA00E5FF); // reset

            // Cardinal labels
            if (isCardinal) {
                String label;
                int c = paintCardinal.getColor();
                switch (deg) {
                    case 0:   label = "N"; paintCardinal.setColor(0xFFFF3B3B); break;
                    case 90:  label = "E"; paintCardinal.setColor(0xFF00E5FF); break;
                    case 180: label = "S"; paintCardinal.setColor(0xFFEAEEF5); break;
                    case 270: label = "W"; paintCardinal.setColor(0xFF00E5FF); break;
                    default:  label = ""; break;
                }
                if (!label.isEmpty()) {
                    float lx = cx + sinA * (radius * 0.62f);
                    float ly = cy - cosA * (radius * 0.62f) + paintCardinal.getTextSize() * 0.38f;
                    canvas.drawText(label, lx, ly, paintCardinal);
                    paintCardinal.setColor(c);
                }
            }

            // 30° degree labels (non-cardinal)
            if (isMajor && !isCardinal) {
                float lx = cx + sinA * (radius * 0.62f);
                float ly = cy - cosA * (radius * 0.62f) + paintDegree.getTextSize() * 0.38f;
                canvas.drawText(String.valueOf(deg), lx, ly, paintDegree);
            }
        }
        canvas.restore();

        // ── Needle (always points up = North) ────────────────────────────────
        canvas.save();
        canvas.rotate(currentAngle, cx, cy);  // rotate needle with sensor

        float needleLen = radius * 0.55f;
        float needleWidth = radius * 0.06f;

        Path northNeedle = new Path();
        northNeedle.moveTo(cx, cy - needleLen);
        northNeedle.lineTo(cx - needleWidth, cy);
        northNeedle.lineTo(cx + needleWidth, cy);
        northNeedle.close();

        Path southNeedle = new Path();
        southNeedle.moveTo(cx, cy + needleLen * 0.65f);
        southNeedle.lineTo(cx - needleWidth, cy);
        southNeedle.lineTo(cx + needleWidth, cy);
        southNeedle.close();

        // Needle glow
        Paint needleGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        needleGlow.setStyle(Paint.Style.FILL);
        needleGlow.setColor(0x44FF3B3B);
        needleGlow.setMaskFilter(new BlurMaskFilter(12f, BlurMaskFilter.Blur.OUTER));
        canvas.drawPath(northNeedle, needleGlow);

        canvas.drawPath(northNeedle, paintNeedleN);
        canvas.drawPath(southNeedle, paintNeedleS);

        // Hub circle
        canvas.drawCircle(cx, cy, needleWidth * 0.9f, paintNeedleHub);
        paintNeedleHub.setColor(0xFF00E5FF);
        canvas.drawCircle(cx, cy, needleWidth * 0.45f, paintNeedleHub);
        paintNeedleHub.setColor(0xFF0A0E1A);

        canvas.restore();

        // ── Heading text ──────────────────────────────────────────────────────
        int heading = ((int) currentAngle + 360) % 360;
        paintHeading.setColor(0xFFEAEEF5);
        canvas.drawText(heading + "°", cx, cy + radius * 1.12f, paintHeading);

        paintHeading.setTextSize(radius * 0.12f);
        paintHeading.setColor(0x8800E5FF);
        canvas.drawText(getDirection(heading), cx, cy + radius * 1.25f, paintHeading);
        paintHeading.setTextSize(radius * 0.15f);
    }

    /** Smooth animated bearing update */
    public void setBearing(float bearing) {
        targetAngle = bearing;
        if (animator != null) animator.cancel();

        // Shortest path rotation
        float diff = targetAngle - currentAngle;
        while (diff > 180f)  diff -= 360f;
        while (diff < -180f) diff += 360f;
        float destination = currentAngle + diff;

        animator = ValueAnimator.ofFloat(currentAngle, destination);
        animator.setDuration(400);
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.addUpdateListener(a -> {
            currentAngle = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private String getDirection(int deg) {
        String[] dirs = {"N","NE","E","SE","S","SW","W","NW"};
        return dirs[(int) Math.round(deg / 45.0) % 8];
    }
}
