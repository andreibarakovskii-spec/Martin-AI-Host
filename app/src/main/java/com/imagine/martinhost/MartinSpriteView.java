package com.imagine.martinhost;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.View;
import java.io.*;

/** Visual host matching the approved neon Martin concept while Godot 3D is integrated. */
public final class MartinSpriteView extends View {
    public enum State { IDLE, LISTENING, THINKING, TALKING, GAME, TOAST, DJ, HAPPY, SLEEPING }
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap portrait; private State state=State.IDLE; private long epoch=System.currentTimeMillis();
    private float speechLevel=0f; private boolean running=true;
    private final Runnable tick=new Runnable(){@Override public void run(){if(!running)return;invalidate();postDelayed(this,33);}};
    public MartinSpriteView(Context c){super(c);init();} public MartinSpriteView(Context c, AttributeSet a){super(c,a);init();}
    private void init(){setLayerType(View.LAYER_TYPE_SOFTWARE,null);setBackground(new ColorDrawable(Color.TRANSPARENT));loadPortrait();post(tick);}
    private void loadPortrait(){try{InputStream in=getContext().getAssets().open("martin_portrait.b64");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);byte[] jpg=Base64.decode(out.toString("UTF-8"),Base64.DEFAULT);portrait=BitmapFactory.decodeByteArray(jpg,0,jpg.length);}catch(Exception ignored){portrait=null;}}
    public void setState(State s){state=s==null?State.IDLE:s;epoch=System.currentTimeMillis();invalidate();}
    public State getState(){return state;} public void setSpeechLevel(float v){speechLevel=Math.max(0f,Math.min(1f,v));invalidate();}
    @Override protected void onDetachedFromWindow(){running=false;removeCallbacks(tick);super.onDetachedFromWindow();}

    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();if(w<10||h<10)return;float t=(System.currentTimeMillis()-epoch)/1000f;
        int[] glow=state==State.LISTENING?new int[]{0xAA2E5BFF,0x663B2BFF,0x00101018}:state==State.THINKING?new int[]{0xAA7B35FF,0x553B1B8F,0x00101018}:state==State.TALKING||state==State.GAME||state==State.TOAST?new int[]{0xAAC12BFF,0x66702BFF,0x00101018}:new int[]{0x88702BFF,0x553B2B66,0x00101018};
        p.setShader(new RadialGradient(w*.5f,h*.43f,Math.min(w,h)*.56f,glow,null,Shader.TileMode.CLAMP));c.drawRoundRect(new RectF(0,0,w,h),40,40,p);p.setShader(null);
        float bob=(float)Math.sin(t*(state==State.DJ?5.5:1.7))*h*.006f;float pulse=1f+(float)Math.sin(t*2.0)*.008f+(state==State.TALKING?speechLevel*.015f:0f);
        if(portrait!=null){float side=Math.min(w*.92f,h*.78f);RectF dst=new RectF((w-side)/2f,h*.04f+bob,(w+side)/2f,h*.04f+side+bob);c.save();c.scale(pulse,pulse,w*.5f,dst.centerY());p.setAlpha(state==State.SLEEPING?120:255);c.drawBitmap(portrait,null,dst,p);p.setAlpha(255);c.restore();}
        else {p.setColor(0xFF242131);c.drawCircle(w*.5f,h*.42f,Math.min(w,h)*.28f,p);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(w*.06f);c.drawText("MARTIN",w*.5f,h*.43f,p);}
        // state caption and animated waveform like approved concept
        String label=state==State.LISTENING?"Слушаю…":state==State.THINKING?"Думаю…":state==State.TALKING||state==State.GAME||state==State.TOAST?"Говорю…":state==State.HAPPY?"Отлично!":"Готов к вечеринке";
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(w*.052f);p.setColor(Color.WHITE);c.drawText(label,w*.5f,h*.86f,p);
        stroke.setStrokeWidth(4);stroke.setStrokeCap(Paint.Cap.ROUND);stroke.setColor(0xFF8F48FF);float cy=h*.91f, start=w*.25f,end=w*.75f;for(int i=0;i<34;i++){float x=start+(end-start)*i/33f;float amp=(float)(8+26*Math.abs(Math.sin(t*5+i*.58)))*(state==State.LISTENING||state==State.TALKING?1f:.35f);c.drawLine(x,cy-amp*.5f,x,cy+amp*.5f,stroke);}
        if(state==State.LISTENING){stroke.setColor(0x556DCBFF);stroke.setStrokeWidth(5);float r=Math.min(w,h)*(.19f+(t%1.2f)/1.2f*.08f);c.drawCircle(w*.5f,h*.43f,r,stroke);} }
}
