package com.mad.sensorapp.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

public class GaugeView extends View {

    private static final float START_ANGLE = 135f;
    private static final float SWEEP_ANGLE = 270f;

    private final Paint paintArcBg    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintArcFill  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintArcGlow  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintNeedle   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHub      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintValue    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintUnit     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMinMax   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTick     = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float currentValue   = 0f;
    private float displayedValue = 0f;
    private float minValue = 0f;
    private float maxValue = 1000f;
    private String unit    = "lx";
    private int    accentColor = 0xFFFFB300;

    private RectF arcOval = new RectF();
    private float cx, cy, radius;
    private ValueAnimator anim;

    public GaugeView(Context c) { super(c); init(); }
    public GaugeView(Context c, AttributeSet a) { super(c, a); init(); }
    public GaugeView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);

        paintArcBg.setStyle(Paint.Style.STROKE);
        paintArcBg.setStrokeWidth(18f);
        paintArcBg.setStrokeCap(Paint.Cap.ROUND);
        paintArcBg.setColor(0xFF1E2A3A);

        paintArcFill.setStyle(Paint.Style.STROKE);
        paintArcFill.setStrokeWidth(18f);
        paintArcFill.setStrokeCap(Paint.Cap.ROUND);

        paintArcGlow.setStyle(Paint.Style.STROKE);
        paintArcGlow.setStrokeWidth(28f);
        paintArcGlow.setStrokeCap(Paint.Cap.ROUND);
        paintArcGlow.setMaskFilter(new BlurMaskFilter(20f, BlurMaskFilter.Blur.OUTER));

        paintNeedle.setStyle(Paint.Style.STROKE);
        paintNeedle.setStrokeWidth(3f);
        paintNeedle.setStrokeCap(Paint.Cap.ROUND);

        paintHub.setStyle(Paint.Style.FILL);

        paintValue.setTextAlign(Paint.Align.CENTER);
        paintValue.setTypeface(Typeface.create("monospace", Typeface.BOLD));

        paintUnit.setTextAlign(Paint.Align.CENTER);

        paintMinMax.setTextAlign(Paint.Align.CENTER);
        paintMinMax.setColor(0x664A5568);

        paintTick.setStyle(Paint.Style.STROKE);
        paintTick.setStrokeWidth(1.5f);
        paintTick.setColor(0x331E2A3A);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cx     = w / 2f;
        cy     = h * 0.54f;
        radius = Math.min(w, h) * 0.38f;
        arcOval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        paintArcBg.setStrokeWidth(radius * 0.12f);
        paintArcFill.setStrokeWidth(radius * 0.12f);
        paintArcGlow.setStrokeWidth(radius * 0.22f);
        paintValue.setTextSize(radius * 0.42f);
        paintUnit.setTextSize(radius * 0.18f);
        paintUnit.setColor(0x88EAEEF5);
        paintMinMax.setTextSize(radius * 0.13f);
        paintNeedle.setStrokeWidth(radius * 0.025f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float pct = (maxValue > minValue) ?
                Math.max(0f, Math.min(1f, (displayedValue - minValue) / (maxValue - minValue))) : 0f;
        float sweepFill = SWEEP_ANGLE * pct;

        // ── Ticks ─────────────────────────────────────────────────────────────
        for (int i = 0; i <= 10; i++) {
            float angle = (float) Math.toRadians(START_ANGLE + (SWEEP_ANGLE * i / 10f));
            float inner = radius * 0.80f;
            float outer = radius * 0.94f;
            canvas.drawLine(cx + (float)Math.cos(angle)*inner, cy + (float)Math.sin(angle)*inner,
                            cx + (float)Math.cos(angle)*outer, cy + (float)Math.sin(angle)*outer, paintTick);
        }

        // ── Background arc ────────────────────────────────────────────────────
        canvas.drawArc(arcOval, START_ANGLE, SWEEP_ANGLE, false, paintArcBg);

        // ── Colour gradient arc ───────────────────────────────────────────────
        if (sweepFill > 0) {
            // Gradient: cyan → amber → red based on value
            SweepGradient sg = new SweepGradient(cx, cy,
                    new int[]{0xFF00E5FF, 0xFFFFB300, 0xFFFF3B3B, 0xFFFF3B3B, 0xFF00E5FF},
                    new float[]{0f, 0.4f, 0.75f, 1f, 1f});
            Matrix m = new Matrix();
            m.preRotate(START_ANGLE, cx, cy);
            sg.setLocalMatrix(m);
            paintArcFill.setShader(sg);

            paintArcGlow.setColor((accentColor & 0x00FFFFFF) | 0x44000000);
            canvas.drawArc(arcOval, START_ANGLE, sweepFill, false, paintArcGlow);
            canvas.drawArc(arcOval, START_ANGLE, sweepFill, false, paintArcFill);
        }

        // ── Needle ────────────────────────────────────────────────────────────
        float needleAngle = (float) Math.toRadians(START_ANGLE + sweepFill);
        float needleLen   = radius * 0.72f;
        float nx = cx + (float)Math.cos(needleAngle) * needleLen;
        float ny = cy + (float)Math.sin(needleAngle) * needleLen;
        paintNeedle.setColor(accentColor);
        canvas.drawLine(cx, cy, nx, ny, paintNeedle);

        // ── Hub ───────────────────────────────────────────────────────────────
        paintHub.setColor(0xFF0A0E1A);
        canvas.drawCircle(cx, cy, radius * 0.08f, paintHub);
        paintHub.setColor(accentColor);
        canvas.drawCircle(cx, cy, radius * 0.04f, paintHub);

        // ── Value text ────────────────────────────────────────────────────────
        paintValue.setColor(0xFFEAEEF5);
        canvas.drawText(String.format("%.0f", displayedValue), cx, cy + radius * 0.18f, paintValue);
        paintUnit.setColor(accentColor);
        canvas.drawText(unit, cx, cy + radius * 0.38f, paintUnit);

        // ── Min / Max labels ──────────────────────────────────────────────────
        float minAngle = (float)Math.toRadians(START_ANGLE);
        float maxAngle = (float)Math.toRadians(START_ANGLE + SWEEP_ANGLE);
        float lblR     = radius * 1.15f;
        paintMinMax.setTextSize(radius * 0.13f);
        canvas.drawText(String.valueOf((int)minValue),
                cx + (float)Math.cos(minAngle)*lblR, cy + (float)Math.sin(minAngle)*lblR, paintMinMax);
        canvas.drawText(String.valueOf((int)maxValue),
                cx + (float)Math.cos(maxAngle)*lblR, cy + (float)Math.sin(maxAngle)*lblR, paintMinMax);
    }

    public void setValue(float value) {
        currentValue = value;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(displayedValue, value);
        anim.setDuration(500);
        anim.setInterpolator(new FastOutSlowInInterpolator());
        anim.addUpdateListener(a -> { displayedValue = (float)a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    public void setRange(float min, float max) { minValue = min; maxValue = max; invalidate(); }
    public void setUnit(String u)              { unit = u; invalidate(); }
    public void setAccentColor(int color) {
        accentColor = color;
        paintArcFill.setShader(null);
        invalidate();
    }
}
