package com.imagine.martinhost;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Lightweight voice visualizer used instead of the archived 3D avatar.
 * The same level input is fed by microphone RMS while listening and by TTS
 * amplitude while the assistant is speaking, so the UI always feels alive.
 */
public final class VoiceOrbView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float targetLevel = 0f;
    private float level = 0f;
    private float energy = .55f;
    private float[] spectrum;
    public void setSpectrum(float[] values){spectrum=values.clone();postInvalidateOnAnimation();}
    private String state = "idle";
    private String emotion = "neutral";
    private long startedNs = System.nanoTime();

    public VoiceOrbView(Context context) { super(context); init(); }
    public VoiceOrbView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        bar.setStyle(Paint.Style.STROKE);
        bar.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setLevel(float value) {
        targetLevel = clamp(value);
        postInvalidateOnAnimation();
    }

    public void setEnergy(float value) {
        energy = clamp(value);
        postInvalidateOnAnimation();
    }

    public void setState(String value) {
        state = value == null ? "idle" : value;
        if(!state.equals("talking")) spectrum=null;
        postInvalidateOnAnimation();
    }

    public void setEmotion(String value) {
        emotion = value == null ? "neutral" : value;
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float dt = 0.16f;
        level += (targetLevel - level) * dt;

        float w = getWidth();
        float h = getHeight();
        float cx = w * .5f;
        float cy = h * .50f;
        float min = Math.min(w, h);
        float base = min * .145f;
        float now = (System.nanoTime() - startedNs) / 1_000_000_000f;
        float idlePulse = .5f + .5f * (float)Math.sin(now * 2.1f);
        float active = Math.max(level, state.equals("thinking") ? .24f : .08f);
        float pulse = base * (1f + .10f * active + .025f * idlePulse);

        int accent = accentColor();
        int accentSoft = withAlpha(accent, 120);

        // Soft halo.
        float haloR = pulse * (2.05f + .32f * active);
        fill.setShader(new RadialGradient(cx, cy, haloR,
                new int[]{withAlpha(accent, 70), withAlpha(accent, 22), Color.TRANSPARENT},
                new float[]{0f, .50f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, haloR, fill);
        fill.setShader(null);

        // Expanding speech waves.
        stroke.setColor(accentSoft);
        stroke.setStrokeWidth(Math.max(2f, min * .004f));
        for (int i = 0; i < 3; i++) {
            float phase = (now * (.42f + active * .38f) + i / 3f) % 1f;
            float r = pulse * (1.22f + phase * (1.20f + active * .55f));
            stroke.setAlpha((int)(120f * (1f - phase) * (.45f + active * .55f)));
            canvas.drawCircle(cx, cy, r, stroke);
        }

        // Circular spectrum from audible TTS PCM. Listening uses its RMS envelope;
        // thinking is an explicitly animated state, not simulated speech.
        int bars = 72;
        float inner = pulse * 1.34f;
        bar.setStrokeWidth(Math.max(2f, min * .0062f));
        bar.setColor(accent);
        for (int i = 0; i < bars; i++) {
            double a = Math.PI * 2.0 * i / bars - Math.PI / 2.0;
            float harmonic = .38f
                    + .26f * (float)Math.sin(i * .61f + now * 4.0f)
                    + .18f * (float)Math.sin(i * .23f - now * 2.6f);
            float amp = Math.max(.10f, harmonic) * (.20f + active * 1.10f);
            if (state.equals("talking")) amp = spectrum==null ? active*.4f : spectrum[(i*24/bars)%spectrum.length]*1.5f;
            if (state.equals("thinking")) amp = .24f + .20f * (float)Math.sin(i * .35f + now * 3.2f);
            float len = pulse * (.10f + amp * .38f);
            float x1 = cx + (float)Math.cos(a) * inner;
            float y1 = cy + (float)Math.sin(a) * inner;
            float x2 = cx + (float)Math.cos(a) * (inner + len);
            float y2 = cy + (float)Math.sin(a) * (inner + len);
            bar.setAlpha(145 + (int)(100 * Math.min(1f, amp)));
            canvas.drawLine(x1, y1, x2, y2, bar);
        }

        // Main orb body.
        float orbR = pulse;
        fill.setShader(new RadialGradient(cx - orbR * .25f, cy - orbR * .30f, orbR * 1.45f,
                new int[]{0xFFF9F7FF, accent, darken(accent)},
                new float[]{0f, .52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, orbR, fill);
        fill.setShader(null);

        // Glass highlight.
        fill.setColor(0x55FFFFFF);
        canvas.drawCircle(cx - orbR * .28f, cy - orbR * .31f, orbR * .20f, fill);

        // Thin outer ring keeps the visual crisp on low-end phones.
        stroke.setColor(withAlpha(Color.WHITE, 155));
        stroke.setStrokeWidth(Math.max(1.5f, min * .003f));
        stroke.setAlpha(155);
        canvas.drawCircle(cx, cy, orbR * 1.02f, stroke);

        // Animate only while attached; no timers/threads are created.
        if(isShown()) postInvalidateDelayed(33L);
    }

    private int accentColor() {
        String e = emotion.toLowerCase();
        if (e.contains("happy") || e.contains("excited") || e.contains("playful")) return 0xFFFFB24A;
        if (e.contains("warm")) return 0xFFFF8E67;
        if (e.contains("focused") || e.contains("curious")) return 0xFF53D6FF;
        if (state.equals("thinking")) return 0xFF8C68FF;
        if (state.equals("talking")) return 0xFF7A5CFF;
        return 0xFF6E5CFF;
    }

    private static int darken(int color) {
        int r = (int)(Color.red(color) * .46f);
        int g = (int)(Color.green(color) * .46f);
        int b = (int)(Color.blue(color) * .56f);
        return Color.rgb(r, g, b);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
