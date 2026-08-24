package com.imagine.martinhost;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import java.util.Random;

public final class MartinSpriteView extends View {
    public enum State { IDLE, LISTENING, THINKING, TALKING, GAME, TOAST, DJ, HAPPY, SLEEPING }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap idle, listen, talk, dj;
    private State state = State.IDLE;
    private float phase = 0f;
    private float speechLevel = 0f;
    private long lastBlink = System.currentTimeMillis();
    private boolean blink = false;
    private final Random random = new Random();
    private ValueAnimator animator;

    public MartinSpriteView(Context c){ super(c); init(); }
    public MartinSpriteView(Context c, AttributeSet a){ super(c,a); init(); }
    private void init(){
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        idle = load(R.drawable.martin_idle); listen = load(R.drawable.martin_listen);
        talk = load(R.drawable.martin_talk); dj = load(R.drawable.martin_dj);
        animator = ValueAnimator.ofFloat(0f,1f); animator.setDuration(1200); animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a->{ phase=(float)a.getAnimatedValue(); updateBlink(); invalidate(); }); animator.start();
    }
    private Bitmap load(int id){ return BitmapFactory.decodeResource(getResources(),id); }
    public void setState(State s){ state=s==null?State.IDLE:s; invalidate(); }
    public State getState(){ return state; }
    public void setSpeechLevel(float v){ speechLevel=Math.max(0f,Math.min(1f,v)); invalidate(); }
    private void updateBlink(){ long now=System.currentTimeMillis(); if(!blink&&now-lastBlink>1800+random.nextInt(2800)){blink=true;lastBlink=now;} else if(blink&&now-lastBlink>120){blink=false;lastBlink=now;} }
    private Bitmap bitmapForState(){ switch(state){case LISTENING:return listen;case TALKING:case GAME:case TOAST:case HAPPY:return talk;case DJ:return dj;default:return idle;} }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c); Bitmap b=bitmapForState(); if(b==null)return; float w=getWidth(),h=getHeight();
        float pulse=state==State.TALKING?1f+0.018f*(float)Math.sin(phase*Math.PI*8):1f+0.007f*(float)Math.sin(phase*Math.PI*2);
        float bob=state==State.DJ?(float)Math.sin(phase*Math.PI*8)*h*.008f:(float)Math.sin(phase*Math.PI*2)*h*.004f;
        float side=state==State.LISTENING?(float)Math.sin(phase*Math.PI*2)*w*.006f:0f;
        glow.setShader(new RadialGradient(w*.5f,h*.52f,Math.min(w,h)*.48f,new int[]{0x553B22FF,0x002A0E56},null,Shader.TileMode.CLAMP));
        c.drawCircle(w*.5f,h*.5f,Math.min(w,h)*.49f,glow); glow.setShader(null);
        float fit=Math.min(w/b.getWidth(),h/b.getHeight())*pulse,dw=b.getWidth()*fit,dh=b.getHeight()*fit;
        RectF dst=new RectF((w-dw)/2f+side,(h-dh)/2f+bob,(w+dw)/2f+side,(h+dh)/2f+bob); c.drawBitmap(b,null,dst,paint);
        if(state==State.THINKING)drawThinking(c,w,h); if(state==State.LISTENING)drawListening(c,w,h);
        if(state==State.TALKING||state==State.GAME||state==State.TOAST)drawVoiceBars(c,w,h);
        if(blink&&state!=State.SLEEPING)drawSoftBlink(c,w,h); if(state==State.SLEEPING)drawSleep(c,w,h);
    }
    private void drawListening(Canvas c,float w,float h){ Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(4f,w*.008f));p.setColor(0xFF8F57FF);for(int i=0;i<3;i++){float r=w*(.12f+i*.035f)+(float)Math.sin(phase*Math.PI*4+i)*4f;c.drawArc(new RectF(w*.5f-r,h*.08f-r*.3f,w*.5f+r,h*.08f+r*1.7f),200,140,false,p);} }
    private void drawThinking(Canvas c,float w,float h){ Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(0xCCFFFFFF);c.drawCircle(w*.74f,h*.18f,w*.02f,p);c.drawCircle(w*.80f,h*.13f,w*.03f,p);c.drawCircle(w*.87f,h*.08f,w*.05f,p);p.setColor(0xFF9A64FF);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(w*.065f);c.drawText("?",w*.87f,h*.105f,p); }
    private void drawVoiceBars(Canvas c,float w,float h){ Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(0xFF9A64FF);float base=h*.92f,center=w*.5f;float level=Math.max(.25f,speechLevel>0?speechLevel:(.35f+.35f*(float)Math.abs(Math.sin(phase*Math.PI*6))));for(int i=-5;i<=5;i++){float bh=h*.025f*(1f+(5-Math.abs(i))*.18f)*level;c.drawRoundRect(center+i*w*.018f-w*.004f,base-bh,center+i*w*.018f+w*.004f,base+bh,w*.004f,w*.004f,p);} }
    private void drawSoftBlink(Canvas c,float w,float h){ Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(0x88200E2E);c.drawRoundRect(w*.25f,h*.30f,w*.75f,h*.36f,w*.03f,w*.03f,p); }
    private void drawSleep(Canvas c,float w,float h){ Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(0xFFE8DFFF);p.setTextSize(w*.06f);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("z Z z",w*.72f,h*.20f,p); }
    @Override protected void onDetachedFromWindow(){ if(animator!=null)animator.cancel(); super.onDetachedFromWindow(); }
}
