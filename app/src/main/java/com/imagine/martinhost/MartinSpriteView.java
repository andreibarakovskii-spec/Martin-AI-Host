package com.imagine.martinhost;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.View;

/**
 * Always-visible native Martin renderer used while the Godot Surface is being
 * embedded. Unlike the old WebView/SVG path this cannot silently render blank
 * on vendor WebView implementations. It also exposes the same state API so the
 * NPC/audio pipeline does not need to change when Godot replaces this view.
 */
public final class MartinSpriteView extends View {
    public enum State { IDLE, LISTENING, THINKING, TALKING, GAME, TOAST, DJ, HAPPY, SLEEPING }

    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private State state=State.IDLE;
    private long epoch=System.currentTimeMillis();
    private float speechLevel=0f;
    private boolean running=true;
    private final Runnable tick=new Runnable(){@Override public void run(){if(!running)return;invalidate();postDelayed(this,33);}};

    public MartinSpriteView(Context c){super(c);init();}
    public MartinSpriteView(Context c, AttributeSet a){super(c,a);init();}
    private void init(){setLayerType(View.LAYER_TYPE_SOFTWARE,null);setBackground(new ColorDrawable(Color.TRANSPARENT));post(tick);}
    public void setState(State s){state=s==null?State.IDLE:s;epoch=System.currentTimeMillis();invalidate();}
    public State getState(){return state;}
    public void setSpeechLevel(float v){speechLevel=Math.max(0f,Math.min(1f,v));invalidate();}
    @Override protected void onDetachedFromWindow(){running=false;removeCallbacks(tick);super.onDetachedFromWindow();}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight();if(w<10||h<10)return;
        float t=(System.currentTimeMillis()-epoch)/1000f;
        float s=Math.min(w,h)*.88f;
        float bob=(float)Math.sin(t*(state==State.DJ?6.5:1.7))*s*(state==State.DJ?.018f:.006f);
        float breathe=1f+(float)Math.sin(t*2.0)*.012f;
        c.save();c.translate(w*.5f,h*.52f+bob);c.scale(breathe,1f-(breathe-1f)*.3f);

        // layered glow gives depth and keeps silhouette readable on OLED black
        p.setShader(new RadialGradient(0,-s*.05f,s*.52f,new int[]{0x663B2BFF,0x22270D43,0x00101018},null,Shader.TileMode.CLAMP));
        c.drawCircle(0,-s*.02f,s*.52f,p);p.setShader(null);

        float hr=s*.225f, hy=-s*.13f;
        // tail
        stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeCap(Paint.Cap.ROUND);stroke.setStrokeWidth(s*.055f);stroke.setColor(0xFF575761);
        c.drawArc(new RectF(s*.03f,s*.05f,s*.43f,s*.46f),210,250,false,stroke);

        // torso + hoodie with simple shaded layers
        p.setColor(0xFF64636C);c.drawRoundRect(new RectF(-s*.22f,hy+hr*.72f,s*.22f,s*.39f),s*.12f,s*.12f,p);
        p.setShader(new LinearGradient(-s*.18f,0,s*.18f,s*.36f,0xFF202127,0xFF0F1015,Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(-s*.185f,hy+hr*.92f,s*.185f,s*.36f),s*.07f,s*.07f,p);p.setShader(null);

        // ears
        Path le=new Path();le.moveTo(-hr*.82f,hy-hr*.55f);le.lineTo(-hr*.48f,hy-hr*1.45f);le.lineTo(-hr*.10f,hy-hr*.72f);le.close();
        Path re=new Path();re.moveTo(hr*.82f,hy-hr*.55f);re.lineTo(hr*.48f,hy-hr*1.45f);re.lineTo(hr*.10f,hy-hr*.72f);re.close();
        p.setColor(0xFF5D5C65);c.drawPath(le,p);c.drawPath(re,p);
        p.setColor(0xFFD78D91);Path li=new Path();li.moveTo(-hr*.67f,hy-hr*.66f);li.lineTo(-hr*.47f,hy-hr*1.22f);li.lineTo(-hr*.24f,hy-hr*.72f);li.close();c.drawPath(li,p);
        Path ri=new Path();ri.moveTo(hr*.67f,hy-hr*.66f);ri.lineTo(hr*.47f,hy-hr*1.22f);ri.lineTo(hr*.24f,hy-hr*.72f);ri.close();c.drawPath(ri,p);

        // head with highlight to avoid flat cartoon disc
        p.setShader(new RadialGradient(-hr*.35f,hy-hr*.45f,hr*1.5f,new int[]{0xFF81808A,0xFF56555E,0xFF3E3E47},null,Shader.TileMode.CLAMP));
        c.drawCircle(0,hy,hr,p);p.setShader(null);
        p.setColor(0x668F8E96);c.drawOval(new RectF(-hr*.70f,hy+hr*.10f,hr*.70f,hy+hr*.78f),p);

        float eyeY=hy-hr*.13f, eyeX=hr*.42f;
        float blink=1f;if(state==State.SLEEPING)blink=.06f;else if(((int)(t*2.2f))%17==0)blink=.16f;
        drawEye(c,-eyeX,eyeY,hr*.26f,blink,t);drawEye(c,eyeX,eyeY,hr*.26f,blink,t);

        // nose
        p.setColor(0xFFE89B9D);Path n=new Path();n.moveTo(-hr*.12f,hy+hr*.22f);n.lineTo(hr*.12f,hy+hr*.22f);n.lineTo(0,hy+hr*.35f);n.close();c.drawPath(n,p);

        float talk=(state==State.TALKING||state==State.GAME||state==State.TOAST)?Math.max(.12f,speechLevel*.42f):state==State.HAPPY?.15f:.035f;
        p.setColor(0xFF24181C);c.drawOval(new RectF(-hr*.20f,hy+hr*.40f,hr*.20f,hy+hr*(.47f+talk)),p);
        if(talk>.1f){p.setColor(0xFFF0AEB2);c.drawOval(new RectF(-hr*.11f,hy+hr*(.52f+talk*.25f),hr*.11f,hy+hr*(.58f+talk*.42f)),p);}

        // whiskers
        stroke.setStrokeWidth(s*.006f);stroke.setColor(0xFFE3E1E5);for(int i=-1;i<=1;i++){float yy=hy+hr*(.30f+i*.14f);c.drawLine(-hr*.55f,yy,-hr*1.22f,yy+i*hr*.08f,stroke);c.drawLine(hr*.55f,yy,hr*1.22f,yy+i*hr*.08f,stroke);}

        // paws; states alter gesture rather than only face
        p.setColor(0xFF73727B);float py=s*.17f;c.drawCircle(-s*.19f,py,s*.052f,p);c.drawCircle(s*.19f,py,s*.052f,p);
        if(state==State.GAME){stroke.setStrokeWidth(s*.045f);stroke.setColor(0xFF73727B);c.drawLine(s*.18f,py,s*.30f,py-s*.12f,stroke);c.drawCircle(s*.31f,py-s*.13f,s*.05f,p);}
        if(state==State.HAPPY){stroke.setStrokeWidth(s*.045f);stroke.setColor(0xFF73727B);c.drawLine(-s*.18f,py,-s*.27f,py-s*.12f,stroke);c.drawLine(s*.18f,py,s*.27f,py-s*.12f,stroke);}
        if(state==State.TOAST)drawGlass(c,s,py);
        if(state==State.DJ)drawHeadphones(c,s,hy,hr);
        if(state==State.LISTENING)drawWaves(c,s,hy,t);
        if(state==State.THINKING)drawThought(c,s,hy,hr);

        p.setShader(null);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(s*.055f);c.drawText("MARTIN",0,s*.20f,p);
        c.restore();
    }

    private void drawEye(Canvas c,float x,float y,float r,float open,float t){p.setColor(0xFFF6F6F7);c.drawOval(new RectF(x-r,y-r*open,x+r,y+r*open),p);if(open>.2f){float target=state==State.LISTENING?(float)Math.sin(t*.9f)*r*.18f:0f;p.setShader(new RadialGradient(x+target-r*.1f,y-r*.12f,r*.62f,new int[]{0xFFB6F36B,0xFF55A82F,0xFF17360D},null,Shader.TileMode.CLAMP));c.drawCircle(x+target,y,r*.56f,p);p.setShader(null);p.setColor(0xFF0A0C0B);c.drawCircle(x+target,y,r*.28f,p);p.setColor(Color.WHITE);c.drawCircle(x+target-r*.14f,y-r*.18f,r*.10f,p);}}
    private void drawHeadphones(Canvas c,float s,float y,float r){stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(s*.032f);stroke.setColor(0xFF8656FF);c.drawArc(new RectF(-r*1.05f,y-r*1.12f,r*1.05f,y+r*.92f),198,144,false,stroke);p.setColor(0xFF171820);c.drawRoundRect(new RectF(-r*1.15f,y-r*.22f,-r*.83f,y+r*.52f),s*.025f,s*.025f,p);c.drawRoundRect(new RectF(r*.83f,y-r*.22f,r*1.15f,y+r*.52f),s*.025f,s*.025f,p);}
    private void drawGlass(Canvas c,float s,float py){stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(s*.008f);stroke.setColor(0xFFF0D19A);RectF cup=new RectF(s*.20f,py-s*.19f,s*.32f,py-s*.05f);c.drawOval(cup,stroke);c.drawLine(s*.26f,py-s*.05f,s*.26f,py+s*.04f,stroke);c.drawLine(s*.21f,py+s*.04f,s*.31f,py+s*.04f,stroke);}
    private void drawWaves(Canvas c,float s,float y,float t){stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(s*.008f);stroke.setColor(0xFF9065FF);for(int i=0;i<3;i++){float rr=s*(.13f+i*.035f)+(float)Math.sin(t*4+i)*s*.005f;c.drawArc(new RectF(-rr,y-rr,rr,y+rr),205,130,false,stroke);}}
    private void drawThought(Canvas c,float s,float y,float r){p.setColor(0xDDFFFFFF);c.drawCircle(r*1.08f,y-r*.85f,s*.018f,p);c.drawCircle(r*1.36f,y-r*1.12f,s*.028f,p);c.drawCircle(r*1.70f,y-r*1.45f,s*.055f,p);p.setColor(0xFF6940FF);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(s*.055f);c.drawText("?",r*1.70f,y-r*1.42f,p);}
}
