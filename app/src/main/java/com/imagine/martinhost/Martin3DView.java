package com.imagine.martinhost;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Martin 1:1 concept renderer.
 *
 * The approved concept portrait is the source of truth for Martin's face, hoodie,
 * microphone and neon-purple visual language.  We animate the exact artwork
 * instead of trying to recreate it with procedural geometry, so the character
 * shown to the user stays visually identical to the approved concept.
 *
 * The public API intentionally matches the old OpenGL view so the rest of the
 * voice/AI pipeline does not need to change.
 */
public final class Martin3DView extends View {
    public enum State { IDLE, LISTENING, THINKING, TALKING, HAPPY, GAME, TOAST, DJ, SLEEPING }

    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint fxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap concept;
    private volatile State state = State.IDLE;
    private volatile float speech = 0f;
    private boolean running = true;
    private long startedAt = System.nanoTime();

    public Martin3DView(Context c) { super(c); init(); }
    public Martin3DView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        concept = loadApprovedConcept();
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    private Bitmap loadApprovedConcept() {
        Bitmap b = loadBase64Asset("martin_portrait_valid.b64");
        if (b != null) return b;

        // CI also decodes the same approved asset to drawable/martin_idle.
        // Resolve it dynamically so a missing generated resource can never break compilation.
        try {
            int id = getResources().getIdentifier("martin_idle", "drawable", getContext().getPackageName());
            if (id != 0) return BitmapFactory.decodeResource(getResources(), id);
        } catch (Throwable ignored) { }
        return null;
    }

    private Bitmap loadBase64Asset(String name) {
        try (InputStream in = getContext().getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            String encoded = out.toString(StandardCharsets.UTF_8.name()).trim();
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void setState(State s) {
        state = s == null ? State.IDLE : s;
        invalidate();
    }

    public void setSpeechLevel(float v) {
        speech = Math.max(0f, Math.min(1f, v));
        invalidate();
    }

    public void onResume() {
        running = true;
        startedAt = System.nanoTime();
        postInvalidateOnAnimation();
    }

    public void onPause() {
        running = false;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        startedAt = System.nanoTime();
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        final float w = getWidth();
        final float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float t = (System.nanoTime() - startedAt) / 1_000_000_000f;
        drawBackdrop(c, w, h, t);
        if (concept != null) drawConcept(c, w, h, t);
        else drawEmergencyFallback(c, w, h);
        drawStateFx(c, w, h, t);

        if (running) postInvalidateOnAnimation();
    }

    private void drawBackdrop(Canvas c, float w, float h, float t) {
        fxPaint.setShader(new LinearGradient(
                0, 0, 0, h,
                new int[]{0xFF070812, 0xFF120D22, 0xFF070811},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, fxPaint);
        fxPaint.setShader(null);

        float pulse = .96f + .04f * (float)Math.sin(t * 1.2f);
        float radius = Math.max(w, h) * .58f * pulse;
        fxPaint.setShader(new RadialGradient(
                w * .50f, h * .42f, radius,
                new int[]{0x735B22D8, 0x39301170, 0x00100622},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(w * .50f, h * .42f, radius, fxPaint);
        fxPaint.setShader(null);

        // Soft side glows reproduce the purple/blue light of the approved concept.
        fxPaint.setColor(0x235C29FF);
        c.drawCircle(w * .08f, h * .34f, w * .30f, fxPaint);
        fxPaint.setColor(0x1EEA28FF);
        c.drawCircle(w * .93f, h * .26f, w * .27f, fxPaint);
    }

    private void drawConcept(Canvas c, float w, float h, float t) {
        final float bw = concept.getWidth();
        final float bh = concept.getHeight();

        // FIT_CENTER is intentional: never crop Martin's ears, hoodie or microphone.
        float scale = Math.min((w * .985f) / bw, (h * .995f) / bh);
        float sw = bw * scale;
        float sh = bh * scale;
        float left = (w - sw) * .5f;
        float top = (h - sh) * .5f;

        float bob = (float)Math.sin(t * 2.15f) * h * .0045f;
        float rot = 0f;
        float zoom = 1f;

        switch (state) {
            case LISTENING:
                bob *= .45f;
                zoom = 1.006f;
                break;
            case THINKING:
                rot = (float)Math.sin(t * 1.25f) * .55f;
                zoom = 1.004f;
                break;
            case TALKING:
                bob = (float)Math.sin(t * 5.4f) * h * (.0025f + .0025f * speech);
                zoom = 1.004f + speech * .006f;
                break;
            case HAPPY:
                bob = Math.abs((float)Math.sin(t * 3.5f)) * -h * .008f;
                zoom = 1.008f;
                break;
            case DJ:
                rot = (float)Math.sin(t * 5.2f) * .75f;
                bob = (float)Math.sin(t * 6.2f) * h * .004f;
                zoom = 1.009f;
                break;
            case SLEEPING:
                bob = 0;
                rot = .35f;
                break;
            default:
                zoom = 1f + .0025f * (float)Math.sin(t * 1.65f);
                break;
        }

        c.save();
        c.rotate(rot, w * .5f, h * .52f);
        c.scale(zoom, zoom, w * .5f, h * .52f);
        imagePaint.setAlpha(state == State.SLEEPING ? 185 : 255);
        c.drawBitmap(concept, null, new RectF(left, top + bob, left + sw, top + sh + bob), imagePaint);
        imagePaint.setAlpha(255);
        c.restore();
    }

    private void drawStateFx(Canvas c, float w, float h, float t) {
        linePaint.setStrokeWidth(Math.max(2.5f, w * .006f));
        linePaint.setColor(0xFFB45DFF);

        if (state == State.LISTENING) {
            float pulse = (float)((Math.sin(t * 3.2f) + 1f) * .5f);
            fxPaint.setStyle(Paint.Style.FILL);
            fxPaint.setColor(0x286E36FF);
            c.drawCircle(w * .50f, h * .43f, w * (.23f + .025f * pulse), fxPaint);
            for (int i = 0; i < 3; i++) {
                linePaint.setAlpha(145 - i * 34);
                c.drawCircle(w * .50f, h * .43f, w * (.18f + i * .055f + pulse * .012f), linePaint);
            }
            linePaint.setAlpha(255);
        }

        if (state == State.THINKING) {
            fxPaint.setColor(0xE8FFFFFF);
            float baseX = w * .76f;
            float baseY = h * .14f;
            for (int i = 0; i < 3; i++) {
                float r = w * (.010f + i * .004f);
                float y = baseY - i * h * .035f + (float)Math.sin(t * 2f + i) * h * .003f;
                c.drawCircle(baseX + i * w * .045f, y, r, fxPaint);
            }
        }

        if (state == State.TALKING) {
            // Sound energy is drawn beside the microphone; Martin's face itself stays untouched.
            float level = Math.max(.12f, speech);
            float cx = w * .70f;
            float cy = h * .50f;
            for (int i = 0; i < 3; i++) {
                linePaint.setAlpha(205 - i * 48);
                float r = w * (.045f + i * .035f + level * .018f);
                c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), -48, 96, false, linePaint);
            }
            linePaint.setAlpha(255);
        }

        if (state == State.HAPPY) {
            fxPaint.setColor(0xD8FFFFFF);
            for (int i = 0; i < 6; i++) {
                double a = t * .7 + i * Math.PI / 3.0;
                float x = w * .5f + (float)Math.cos(a) * w * .37f;
                float y = h * .42f + (float)Math.sin(a) * h * .25f;
                float r = w * (.006f + .004f * (1f + (float)Math.sin(t * 4f + i)));
                c.drawCircle(x, y, r, fxPaint);
            }
        }

        if (state == State.DJ) {
            float bottom = h * .94f;
            for (int i = 0; i < 11; i++) {
                float x = w * (.18f + i * .064f);
                float amp = .5f + .5f * (float)Math.sin(t * 6.5f + i * .72f);
                float bh = h * (.025f + .055f * amp);
                fxPaint.setColor(i % 2 == 0 ? 0xB4B35CFF : 0xA8527CFF);
                c.drawRoundRect(new RectF(x, bottom - bh, x + w * .025f, bottom), w * .012f, w * .012f, fxPaint);
            }
        }

        if (state == State.SLEEPING) {
            fxPaint.setColor(0x40000000);
            c.drawRect(0, 0, w, h, fxPaint);
            fxPaint.setColor(0xDFFFFFFF);
            fxPaint.setTextAlign(Paint.Align.CENTER);
            fxPaint.setTextSize(w * .055f);
            c.drawText("z  Z  z", w * .79f, h * .18f, fxPaint);
        }
    }

    private void drawEmergencyFallback(Canvas c, float w, float h) {
        fxPaint.setColor(0xFF171322);
        c.drawCircle(w * .5f, h * .43f, Math.min(w, h) * .28f, fxPaint);
        fxPaint.setColor(Color.WHITE);
        fxPaint.setTextAlign(Paint.Align.CENTER);
        fxPaint.setFakeBoldText(true);
        fxPaint.setTextSize(w * .08f);
        c.drawText("MARTIN", w * .5f, h * .45f, fxPaint);
        fxPaint.setFakeBoldText(false);
        fxPaint.setTextSize(w * .034f);
        fxPaint.setColor(0xFFCDBAF7);
        c.drawText("concept asset missing", w * .5f, h * .51f, fxPaint);
    }
}
