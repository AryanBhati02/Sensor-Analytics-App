package com.mad.sensorapp.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

/**
 * Bubble Visualization
 * Proximity value shown as an animated expanding/shrinking bubble
 * with concentric ripple rings, glow, and a live value label.
 */
public class BubbleView extends View {

    // ── Paints ────────────────────────────────────────────────────────────────
    private final Paint paintBubble  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGlow    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRipple  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLabel   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintUnit    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStatus  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── State ──────────────────────────────────────────────────────────────────
    private float displayRadius = 0f;
    private float targetRadius  = 0f;
    private float maxProx       = 10f;   // sensor max (cm)
    private float currentProx   = 0f;
    private ValueAnimator radiusAnim;

    // ── Ripple animation ──────────────────────────────────────────────────────
    private float rippleRadius  = 0f;
    private float rippleAlpha   = 0f;
    private ValueAnimator rippleAnim;

    private float cx, cy, maxRadius;
    private int accentColor = 0xFF00E676;

    public BubbleView(Context c) { super(c); init(); }
    public BubbleView(Context c, AttributeSet a) { super(c, a); init(); }
    public BubbleView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);

        paintBubble.setStyle(Paint.Style.FILL);

        paintGlow.setStyle(Paint.Style.FILL);
        paintGlow.setMaskFilter(new BlurMaskFilter(40f, BlurMaskFilter.Blur.OUTER));

        paintRipple.setStyle(Paint.Style.STROKE);
        paintRipple.setStrokeWidth(3f);

        paintLabel.setTextAlign(Paint.Align.CENTER);
        paintLabel.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        paintLabel.setColor(0xFFEAEEF5);

        paintUnit.setTextAlign(Paint.Align.CENTER);
        paintUnit.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        paintUnit.setColor(0x88EAEEF5);

        paintStatus.setTextAlign(Paint.Align.CENTER);
        paintStatus.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        startRippleLoop();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cx = w / 2f; cy = h / 2f;
        maxRadius = Math.min(w, h) * 0.40f;
        paintLabel.setTextSize(maxRadius * 0.38f);
        paintUnit.setTextSize(maxRadius  * 0.18f);
        paintStatus.setTextSize(maxRadius * 0.17f);
    }

    /** Update proximity. maxRange = sensor's max range in cm. */
    public void setProximity(float cm, float maxRange) {
        currentProx = cm;
        maxProx     = maxRange > 0 ? maxRange : 10f;

        // FAR = small bubble, NEAR = big bubble (inverted for visual effect)
        float pct = 1f - Math.min(1f, cm / maxProx);
        float target = maxRadius * (0.25f + pct * 0.75f);

        animateRadius(target);
    }

    private void animateRadius(float target) {
        targetRadius = target;
        if (radiusAnim != null) radiusAnim.cancel();
        radiusAnim = ValueAnimator.ofFloat(displayRadius, target);
        radiusAnim.setDuration(400);
        radiusAnim.setInterpolator(new FastOutSlowInInterpolator());
        radiusAnim.addUpdateListener(a -> {
            displayRadius = (float) a.getAnimatedValue();
            updateColors();
            invalidate();
        });
        radiusAnim.start();
    }

    private void updateColors() {
        float pct = maxRadius > 0 ? displayRadius / maxRadius : 0;
        // near (pct→1) = red, far (pct→0) = green
        accentColor = blendColor(0xFF00E676, 0xFFFF3B3B, pct);

        float r = Math.max(1f, displayRadius);
        paintBubble.setShader(new RadialGradient(cx, cy - r * 0.2f,
                r,
                new int[]{lighten(accentColor), accentColor, darken(accentColor)},
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP));
        paintGlow.setColor((accentColor & 0x00FFFFFF) | 0x33000000);
        paintRipple.setColor((accentColor & 0x00FFFFFF) | ((int)(rippleAlpha * 255) << 24));
    }

    private void startRippleLoop() {
        rippleAnim = ValueAnimator.ofFloat(0f, 1f);
        rippleAnim.setDuration(1800);
        rippleAnim.setRepeatCount(ValueAnimator.INFINITE);
        rippleAnim.setRepeatMode(ValueAnimator.RESTART);
        rippleAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            rippleRadius = displayRadius + t * maxRadius * 0.55f;
            rippleAlpha  = (1f - t) * 0.6f;
            invalidate();
        });
        rippleAnim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xFF0A0E1A);

        if (displayRadius <= 0) return;

        // Concentric guide rings
        Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(1f);
        guidePaint.setColor(0x111E2A3A);
        for (int i = 1; i <= 4; i++) {
            canvas.drawCircle(cx, cy, maxRadius * i / 4f, guidePaint);
        }

        // Glow
        paintGlow.setColor((accentColor & 0x00FFFFFF) | 0x22000000);
        canvas.drawCircle(cx, cy, displayRadius * 1.3f, paintGlow);

        // Ripple ring
        updateColors();
        canvas.drawCircle(cx, cy, rippleRadius, paintRipple);

        // Second ripple (offset)
        float r2 = displayRadius + ((rippleRadius - displayRadius) * 0.5f);
        paintRipple.setAlpha((int)(rippleAlpha * 200));
        canvas.drawCircle(cx, cy, r2, paintRipple);

        // Main bubble
        canvas.drawCircle(cx, cy, displayRadius, paintBubble);

        // Specular highlight
        float highlightR = Math.max(1f, displayRadius);
        Paint spec = new Paint(Paint.ANTI_ALIAS_FLAG);
        spec.setStyle(Paint.Style.FILL);
        spec.setShader(new RadialGradient(
                cx - highlightR * 0.3f, cy - highlightR * 0.3f,
                highlightR * 0.5f,
                new int[]{0x55FFFFFF, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, displayRadius, spec);

        // Value label
        boolean isNear = currentProx < maxProx * 0.3f;
        paintLabel.setColor(0xFFEAEEF5);
        canvas.drawText(String.format("%.1f", currentProx),
                cx, cy + paintLabel.getTextSize() * 0.38f, paintLabel);
        paintUnit.setColor(0x88EAEEF5);
        canvas.drawText("cm", cx, cy + paintLabel.getTextSize() * 0.9f, paintUnit);

        // NEAR / FAR badge
        paintStatus.setColor(isNear ? 0xFFFF3B3B : 0xFF00E676);
        canvas.drawText(isNear ? "NEAR" : "FAR",
                cx, cy - displayRadius - 20f, paintStatus);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (rippleAnim != null) rippleAnim.cancel();
        if (radiusAnim != null) radiusAnim.cancel();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private int blendColor(int c1, int c2, float t) {
        int r = (int)(Color.red(c1)   * (1-t) + Color.red(c2)   * t);
        int g = (int)(Color.green(c1) * (1-t) + Color.green(c2) * t);
        int b = (int)(Color.blue(c1)  * (1-t) + Color.blue(c2)  * t);
        return Color.argb(200, r, g, b);
    }
    private int lighten(int c) {
        return Color.argb(200,
                Math.min(255, Color.red(c)+80),
                Math.min(255, Color.green(c)+80),
                Math.min(255, Color.blue(c)+80));
    }
    private int darken(int c) {
        return Color.argb(220,
                Color.red(c)/2, Color.green(c)/2, Color.blue(c)/2);
    }
}
