package com.imagine.martinhost;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Stable avatar surface used by all screens. It deliberately does not depend on WebView.
 * When Cubism Core + renderer are available, this class can delegate drawing to Live2D.
 * Until then it renders a guaranteed-visible native fallback and preserves the same API.
 */
public final class AvatarHostView extends View implements AvatarBackend {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private AvatarState state = AvatarState.IDLE;
    private float lip = 0f, lookX = 0f, lookY = 0f, phase = 0f;
    private ValueAnimator animator;
    private boolean live2dAvailable;

    public AvatarHostView(Context c){ super(c); init(); }
    public AvatarHostView(Context c, AttributeSet a){ super(c,a); init(); }

    private void init(){
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        live2dAvailable = classExists("com.live2d.sdk.cubism.core.Live2DCubismCore");
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2400);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> { phase = (float)a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    private boolean classExists(String name){
        try { Class.forName(name); return true; } catch(Throwable ignored){ return false; }
    }

    @Override public void setState(AvatarState s){ state=s==null?AvatarState.IDLE:s; invalidate(); }
    @Override public void setLipSync(float value){ lip=Math.max(0f,Math.min(1f,value)); invalidate(); }
    @Override public void setLook(float x,float y){ lookX=Math.max(-1f,Math.min(1f,x)); lookY=Math.max(-1f,Math.min(1f,y)); invalidate(); }
    @Override public boolean isLive2D(){ return live2dAvailable; }
    @Override public String backendName(){ return live2dAvailable?"Cubism Core detected":"Native fallback"; }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        // Live2D renderer will replace this branch after Core/model are added.
        drawFallback(c);
    }

    private void drawFallback(Canvas c){
        float w=getWidth(), h=getHeight();
        if(w<=0||h<=0)return;
        c.drawColor(Color.TRANSPARENT);
        float bob=(float)Math.sin(phase*Math.PI*2)*h*.012f;
        float cx=w*.5f, cy=h*.49f+bob;

        // neon halo
        p.setShader(new RadialGradient(cx,cy,Math.min(w,h)*.46f,new int[]{0x664B28FF,0x22371A78,0x00000000},null,Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,Math.min(w,h)*.46f,p); p.setShader(null);

        // body / hoodie
        p.setColor(0xFF171922); RectF body=new RectF(w*.22f,h*.56f,w*.78f,h*.96f);
        c.drawRoundRect(body,w*.12f,w*.12f,p);
        stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(Math.max(3f,w*.009f));stroke.setColor(0xFF7B47DB);c.drawRoundRect(body,w*.12f,w*.12f,stroke);

        // head
        p.setColor(0xFF777783); c.drawOval(new RectF(w*.23f,h*.16f,w*.77f,h*.67f),p);
        p.setColor(0xFF63636E); Path le=new Path();le.moveTo(w*.27f,h*.24f);le.lineTo(w*.34f,h*.04f);le.lineTo(w*.44f,h*.21f);le.close();c.drawPath(le,p);
        Path re=new Path();re.moveTo(w*.56f,h*.21f);re.lineTo(w*.66f,h*.04f);re.lineTo(w*.73f,h*.24f);re.close();c.drawPath(re,p);
        p.setColor(0xFFDB8E96); Path li=new Path();li.moveTo(w*.315f,h*.20f);li.lineTo(w*.345f,h*.09f);li.lineTo(w*.405f,h*.20f);li.close();c.drawPath(li,p);Path ri=new Path();ri.moveTo(w*.595f,h*.20f);ri.lineTo(w*.655f,h*.09f);ri.lineTo(w*.685f,h*.20f);ri.close();c.drawPath(ri,p);

        // eyes
        float ex=lookX*w*.012f, ey=lookY*h*.008f;
        p.setColor(Color.WHITE); c.drawOval(new RectF(w*.31f,h*.31f,w*.45f,h*.47f),p); c.drawOval(new RectF(w*.55f,h*.31f,w*.69f,h*.47f),p);
        p.setColor(0xFF72D93E); c.drawCircle(w*.38f+ex,h*.39f+ey,w*.035f,p); c.drawCircle(w*.62f+ex,h*.39f+ey,w*.035f,p);
        p.setColor(0xFF101113); c.drawCircle(w*.38f+ex,h*.39f+ey,w*.014f,p); c.drawCircle(w*.62f+ex,h*.39f+ey,w*.014f,p);

        // nose + mouth/lip sync
        p.setColor(0xFFFF9BA5); Path nose=new Path();nose.moveTo(w*.47f,h*.49f);nose.lineTo(w*.53f,h*.49f);nose.lineTo(w*.50f,h*.53f);nose.close();c.drawPath(nose,p);
        float open=(state==AvatarState.TALKING||state==AvatarState.GAME||state==AvatarState.TOAST)?Math.max(.25f,lip):.12f;
        p.setColor(0xFF281921); c.drawOval(new RectF(w*.44f,h*.545f,w*.56f,h*(.565f+.055f*open)),p);

        // hoodie title
        p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(w*.09f); c.drawText("MARTIN",cx,h*.79f,p);

        // state accessories
        if(state==AvatarState.DJ) drawHeadphones(c,w,h);
        if(state==AvatarState.GAME) drawMic(c,w,h);
        if(state==AvatarState.TOAST) drawGlass(c,w,h);
        if(state==AvatarState.THINKING) drawThought(c,w,h);
        if(state==AvatarState.LISTENING) drawWaves(c,w,h);
        if(state==AvatarState.SLEEPING){ p.setTextSize(w*.07f);p.setColor(Color.WHITE);c.drawText("z Z z",w*.76f,h*.22f,p); }
    }

    private void drawHeadphones(Canvas c,float w,float h){stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(w*.035f);stroke.setColor(0xFFA75BFF);c.drawArc(new RectF(w*.25f,h*.18f,w*.75f,h*.56f),190,160,false,stroke);p.setColor(0xFF1A1B23);c.drawRoundRect(new RectF(w*.20f,h*.31f,w*.29f,h*.49f),w*.03f,w*.03f,p);c.drawRoundRect(new RectF(w*.71f,h*.31f,w*.80f,h*.49f),w*.03f,w*.03f,p);}
    private void drawMic(Canvas c,float w,float h){p.setColor(0xFF20212B);c.drawCircle(w*.79f,h*.67f,w*.055f,p);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(w*.028f);stroke.setColor(0xFF8F56FF);c.drawLine(w*.76f,h*.70f,w*.66f,h*.88f,stroke);}
    private void drawGlass(Canvas c,float w,float h){p.setColor(0x99F2B84B);c.drawRoundRect(new RectF(w*.73f,h*.64f,w*.87f,h*.78f),w*.025f,w*.025f,p);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(w*.012f);stroke.setColor(Color.WHITE);c.drawLine(w*.80f,h*.78f,w*.80f,h*.88f,stroke);c.drawLine(w*.75f,h*.89f,w*.85f,h*.89f,stroke);}
    private void drawThought(Canvas c,float w,float h){p.setColor(0xDDFFFFFF);c.drawCircle(w*.78f,h*.22f,w*.018f,p);c.drawCircle(w*.84f,h*.16f,w*.028f,p);c.drawCircle(w*.91f,h*.09f,w*.05f,p);p.setColor(0xFF7443FF);p.setTextSize(w*.07f);p.setTextAlign(Paint.Align.CENTER);c.drawText("?",w*.91f,h*.115f,p);}
    private void drawWaves(Canvas c,float w,float h){stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(w*.008f);stroke.setColor(0xFFA55AFF);float pulse=.02f*(float)Math.sin(phase*Math.PI*4);for(int i=0;i<3;i++){float r=w*(.11f+i*.04f+pulse);c.drawCircle(w*.5f,h*.41f,r,stroke);}}

    @Override protected void onDetachedFromWindow(){ if(animator!=null)animator.cancel(); super.onDetachedFromWindow(); }
}
