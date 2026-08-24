package com.imagine.martinhost;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.View;
import java.util.Locale;

public final class MartinCharacterView extends View {
    public enum State { IDLE, LISTENING, THINKING, TALKING, GAME, TOAST, DJ, HAPPY, SLEEPING }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private State state = State.IDLE;
    private long started = System.currentTimeMillis();
    private boolean running = true;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, 33);
        }
    };

    public MartinCharacterView(Context c) { super(c); init(); }
    public MartinCharacterView(Context c, AttributeSet a) { super(c,a); init(); }
    private void init(){ setLayerType(View.LAYER_TYPE_SOFTWARE,null); setBackground(new ColorDrawable(Color.TRANSPARENT)); post(ticker); }

    public void setState(State s){ if(s==null)s=State.IDLE; state=s; started=System.currentTimeMillis(); invalidate(); }
    public State getState(){ return state; }

    @Override protected void onDetachedFromWindow(){ running=false; removeCallbacks(ticker); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(), h=getHeight(); if(w<=0||h<=0)return;
        float t=(System.currentTimeMillis()-started)/1000f;
        float breathe=(float)Math.sin(t*2.2f)*0.015f;
        float bounce = state==State.DJ ? (float)Math.sin(t*7f)*0.025f : (float)Math.sin(t*1.7f)*0.008f;
        c.save(); c.translate(w/2f,h/2f + h*bounce); c.scale(1f+breathe,1f-breathe*.35f);

        float s=Math.min(w,h)*0.9f;
        float headR=s*.22f;
        float headY=-s*.13f;
        float bodyTop=headY+headR*.75f;

        // soft party glow
        p.setShader(new RadialGradient(0,0,s*.5f,new int[]{0x443F2BFF,0x00101824},null,Shader.TileMode.CLAMP));
        c.drawCircle(0,0,s*.48f,p); p.setShader(null);

        // tail
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(s*.055f); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setColor(0xFF55545C);
        RectF tail=new RectF(s*.08f,bodyTop+s*.08f,s*.42f,bodyTop+s*.42f); c.drawArc(tail,210,250,false,stroke);

        // body / hoodie
        p.setColor(0xFF1A1B21); RectF body=new RectF(-s*.22f,bodyTop,-s*-.22f,bodyTop); // reset below
        body.set(-s*.23f,bodyTop,s*.23f,s*.40f); c.drawRoundRect(body,s*.10f,s*.10f,p);
        p.setColor(0xFF77747D); c.drawOval(new RectF(-s*.16f,bodyTop-s*.03f,s*.16f,s*.28f),p);
        p.setColor(0xFF17181D); c.drawRoundRect(new RectF(-s*.18f,bodyTop+s*.02f,s*.18f,s*.36f),s*.07f,s*.07f,p);

        // ears
        Path le=new Path(); le.moveTo(-headR*.82f,headY-headR*.55f); le.lineTo(-headR*.46f,headY-headR*1.42f); le.lineTo(-headR*.12f,headY-headR*.72f); le.close();
        Path re=new Path(); re.moveTo(headR*.82f,headY-headR*.55f); re.lineTo(headR*.46f,headY-headR*1.42f); re.lineTo(headR*.12f,headY-headR*.72f); re.close();
        p.setColor(0xFF5A5962); c.drawPath(le,p); c.drawPath(re,p); p.setColor(0xFFE39A91); c.save(); c.scale(.65f,.65f,-headR*.45f,headY-headR*.9f); c.drawPath(le,p); c.drawPath(re,p); c.restore();

        // head
        p.setColor(0xFF62616A); c.drawCircle(0,headY,headR,p);
        p.setColor(0xFF8D8B93); c.drawOval(new RectF(-headR*.72f,headY-headR*.2f,headR*.72f,headY+headR*.74f),p);

        // eyes
        float eyeOpen = state==State.SLEEPING ? .06f : (state==State.THINKING ? .72f : 1f);
        float eyeY=headY-headR*.12f, eyeX=headR*.42f;
        drawEye(c,-eyeX,eyeY,headR*.25f,eyeOpen,t,state==State.LISTENING);
        drawEye(c, eyeX,eyeY,headR*.25f,eyeOpen,t,state==State.LISTENING);

        // nose
        p.setColor(0xFFE99A9B); Path nose=new Path(); nose.moveTo(-headR*.12f,headY+headR*.22f); nose.lineTo(headR*.12f,headY+headR*.22f); nose.lineTo(0,headY+headR*.34f); nose.close(); c.drawPath(nose,p);

        // mouth / lip sync feel
        float mouthOpen = (state==State.TALKING || state==State.GAME || state==State.TOAST) ? .18f + .12f*(float)Math.abs(Math.sin(t*10f)) : (state==State.HAPPY ? .18f : .04f);
        p.setColor(0xFF27191C); RectF mouth=new RectF(-headR*.19f,headY+headR*.39f,headR*.19f,headY+headR*(.39f+mouthOpen)); c.drawOval(mouth,p);
        if(mouthOpen>.12f){ p.setColor(0xFFF2B4B6); c.drawOval(new RectF(-headR*.11f,headY+headR*(.48f+mouthOpen*.25f),headR*.11f,headY+headR*(.52f+mouthOpen*.55f)),p); }

        // whiskers
        stroke.setColor(0xFFD8D6DB); stroke.setStrokeWidth(s*.006f); for(int i=-1;i<=1;i++){ float yy=headY+headR*(.28f+i*.14f); c.drawLine(-headR*.55f,yy,-headR*1.18f,yy-headR*.10f*i,stroke); c.drawLine(headR*.55f,yy,headR*1.18f,yy-headR*.10f*i,stroke); }

        // paws / gesture
        p.setColor(0xFF77767F); float pawY=bodyTop+s*.17f;
        c.drawCircle(-s*.20f,pawY,s*.055f,p); c.drawCircle(s*.20f,pawY,s*.055f,p);
        if(state==State.GAME){ c.drawCircle(s*.28f,pawY-s*.09f,s*.055f,p); }

        // MARTIN logo
        p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD)); p.setTextSize(s*.055f); c.drawText("MARTIN",0,bodyTop+s*.19f,p);

        // accessories by state
        if(state==State.DJ) drawHeadphones(c,s,headY,headR,t);
        if(state==State.TOAST) drawGlass(c,s,pawY);
        if(state==State.GAME) drawMic(c,s,pawY);
        if(state==State.THINKING) drawThought(c,s,headY,headR,t);
        if(state==State.LISTENING) drawSoundWaves(c,s,headY,t);

        c.restore();
    }

    private void drawEye(Canvas c,float x,float y,float r,float open,float t,boolean focus){
        p.setColor(0xFFF4F4F4); RectF e=new RectF(x-r,y-r*open,x+r,y+r*open); c.drawOval(e,p);
        if(open>.15f){ float look=focus?(float)Math.sin(t*1.3f)*r*.16f:0; p.setColor(0xFF86C85A); c.drawCircle(x+look,y,r*.55f,p); p.setColor(0xFF101013); c.drawCircle(x+look,y,r*.28f,p); p.setColor(Color.WHITE); c.drawCircle(x+look-r*.12f,y-r*.16f,r*.10f,p); }
    }
    private void drawHeadphones(Canvas c,float s,float y,float r,float t){ stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(s*.035f); stroke.setColor(0xFF7D4CFF); RectF a=new RectF(-r*1.05f,y-r*1.1f,r*1.05f,y+r*.9f); c.drawArc(a,200,140,false,stroke); p.setColor(0xFF22232A); c.drawRoundRect(new RectF(-r*1.15f,y-r*.2f,-r*.82f,y+r*.52f),s*.03f,s*.03f,p); c.drawRoundRect(new RectF(r*.82f,y-r*.2f,r*1.15f,y+r*.52f),s*.03f,s*.03f,p); }
    private void drawGlass(Canvas c,float s,float pawY){ stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(s*.008f); stroke.setColor(0xFFF1D3A0); RectF cup=new RectF(s*.20f,pawY-s*.18f,s*.32f,pawY-s*.04f); c.drawOval(cup,stroke); c.drawLine(s*.26f,pawY-s*.04f,s*.26f,pawY+s*.05f,stroke); c.drawLine(s*.21f,pawY+s*.05f,s*.31f,pawY+s*.05f,stroke); }
    private void drawMic(Canvas c,float s,float pawY){ p.setColor(0xFF202127); c.drawCircle(s*.29f,pawY-s*.13f,s*.04f,p); stroke.setColor(0xFF4E4F58); stroke.setStrokeWidth(s*.025f); c.drawLine(s*.27f,pawY-s*.10f,s*.20f,pawY,stroke); }
    private void drawThought(Canvas c,float s,float y,float r,float t){ p.setColor(0xCCFFFFFF); c.drawCircle(r*1.10f,y-r*.9f,s*.018f,p); c.drawCircle(r*1.35f,y-r*1.15f,s*.027f,p); c.drawCircle(r*1.68f,y-r*1.45f,s*.055f,p); p.setColor(0xFF5C3CFF); p.setTextSize(s*.055f); p.setTextAlign(Paint.Align.CENTER); c.drawText("?",r*1.68f,y-r*1.42f,p); }
    private void drawSoundWaves(Canvas c,float s,float y,float t){ stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(s*.008f); stroke.setColor(0xFF8C5CFF); for(int i=0;i<3;i++){ float rr=s*(.13f+i*.035f)+(float)Math.sin(t*4f+i)*s*.005f; c.drawArc(new RectF(-rr,y-rr,rr,y+rr),205,130,false,stroke); } }
}
