package com.imagine.martinhost;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Lightweight voice visualizer: no avatar/GPU scene, only a reactive orb + equalizer contour. */
public final class VoiceOrbView extends View {
    public enum Mode { IDLE, LISTENING, THINKING, SPEAKING }
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG), line=new Paint(Paint.ANTI_ALIAS_FLAG), glow=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float level=.06f, phase=0f; private Mode mode=Mode.IDLE;
    private final ValueAnimator ticker;
    public VoiceOrbView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);ticker=ValueAnimator.ofFloat(0f,1f);ticker.setDuration(1800);ticker.setRepeatCount(ValueAnimator.INFINITE);ticker.setInterpolator(new LinearInterpolator());ticker.addUpdateListener(a->{phase=(float)a.getAnimatedValue()*(float)(Math.PI*2);invalidate();});ticker.start();}
    public void setLevel(float v){level=Math.max(.02f,Math.min(1f,v));invalidate();}
    public void setMode(Mode m){mode=m==null?Mode.IDLE:m;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f;float base=Math.min(w,h)*.22f;float pulse=(float)Math.sin(phase*1.15f)*base*.025f;float reactive=base*(.03f+.16f*level);float r=base+pulse+reactive;
        int core=mode==Mode.LISTENING?0xFF6D55FF:mode==Mode.THINKING?0xFF9B4DFF:mode==Mode.SPEAKING?0xFF7B39FF:0xFF4E42B8;
        glow.setColor(core);glow.setAlpha(42);glow.setMaskFilter(new BlurMaskFilter(base*.30f,BlurMaskFilter.Blur.NORMAL));c.drawCircle(cx,cy,r*1.28f,glow);glow.clearShadowLayer();
        RadialGradient rg=new RadialGradient(cx-r*.25f,cy-r*.3f,r*1.4f,new int[]{0xFFB98CFF,core,0xFF16132D},new float[]{0f,.48f,1f},Shader.TileMode.CLAMP);fill.setShader(rg);c.drawCircle(cx,cy,r,fill);fill.setShader(null);
        line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(Math.max(2f,w*.006f));line.setStrokeCap(Paint.Cap.ROUND);line.setColor(0xFFE2D4FF);Path p=new Path();int n=96;for(int i=0;i<=n;i++){float a=(float)(Math.PI*2*i/n);float wave=(float)(Math.sin(a*7+phase*2.2)+.55*Math.sin(a*13-phase*1.4));float amp=base*(.025f+.11f*level);if(mode==Mode.IDLE)amp*=.38f;if(mode==Mode.THINKING)amp*=.65f;float rr=r*1.10f+wave*amp;float x=cx+(float)Math.cos(a)*rr,y=cy+(float)Math.sin(a)*rr;if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}p.close();c.drawPath(p,line);
        line.setStrokeWidth(Math.max(1f,w*.003f));line.setColor(0x557E6CFF);c.drawCircle(cx,cy,r*1.36f+(float)Math.sin(phase)*base*.025f,line);
    }
    @Override protected void onDetachedFromWindow(){ticker.cancel();super.onDetachedFromWindow();}
}
