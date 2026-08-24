package com.imagine.martinhost;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.View;
import android.view.animation.LinearInterpolator;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Stable native avatar surface. Uses the concept Martin image already embedded in assets,
 * so the hero can never become an empty WebView. Cubism/Rive can replace only the backend later. */
public final class AvatarHostView extends View implements AvatarBackend {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG), stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private AvatarState state=AvatarState.IDLE;
    private float lip=0f, lookX=0f, lookY=0f, phase=0f;
    private ValueAnimator animator;
    private Bitmap concept;
    private boolean live2dAvailable;

    public AvatarHostView(Context c){super(c);init();}
    public AvatarHostView(Context c,AttributeSet a){super(c,a);init();}

    private void init(){
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        live2dAvailable=classExists("com.live2d.sdk.cubism.core.Live2DCubismCore");
        concept=loadConceptFromHtml();
        animator=ValueAnimator.ofFloat(0f,1f);animator.setDuration(2600);animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());animator.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});animator.start();
    }

    private boolean classExists(String n){try{Class.forName(n);return true;}catch(Throwable t){return false;}}

    private Bitmap loadConceptFromHtml(){
        try(InputStream in=getContext().getAssets().open("martin_avatar.html")){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))>0)out.write(buf,0,n);
            String s=out.toString(StandardCharsets.UTF_8.name());
            int a=s.indexOf("data:image/jpeg;base64,");if(a<0)return null;a+="data:image/jpeg;base64,".length();
            int b=s.indexOf('"',a);if(b<0)b=s.indexOf('\'',a);if(b<=a)return null;
            byte[] data=Base64.decode(s.substring(a,b),Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data,0,data.length);
        }catch(Throwable t){return null;}
    }

    @Override public void setState(AvatarState s){state=s==null?AvatarState.IDLE:s;invalidate();}
    @Override public void setLipSync(float v){lip=Math.max(0f,Math.min(1f,v));invalidate();}
    @Override public void setLook(float x,float y){lookX=Math.max(-1f,Math.min(1f,x));lookY=Math.max(-1f,Math.min(1f,y));invalidate();}
    @Override public boolean isLive2D(){return live2dAvailable;}
    @Override public String backendName(){return concept!=null?"Concept native renderer":"Native fallback";}

    @Override protected void onDraw(Canvas c){super.onDraw(c);if(concept!=null)drawConcept(c);else drawFallback(c);}

    private void drawConcept(Canvas c){
        float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
        c.drawColor(Color.TRANSPARENT);
        // backdrop glow
        p.setShader(new RadialGradient(w*.5f,h*.43f,Math.min(w,h)*.58f,new int[]{0x665C2CFF,0x22311A78,0x00000000},null,Shader.TileMode.CLAMP));
        c.drawCircle(w*.5f,h*.43f,Math.min(w,h)*.58f,p);p.setShader(null);

        float bw=concept.getWidth(),bh=concept.getHeight();
        float scale=Math.max(w/bw,h/bh);float sw=bw*scale,sh=bh*scale;
        float dx=(w-sw)/2f,dy=(h-sh)/2f;
        float bob=(float)Math.sin(phase*Math.PI*2)*h*.008f;
        float rot=0f;
        if(state==AvatarState.THINKING)rot=(float)Math.sin(phase*Math.PI*2)*.7f;
        if(state==AvatarState.DJ)rot=(float)Math.sin(phase*Math.PI*4)*1.1f;
        float extra=(state==AvatarState.LISTENING?1.012f:state==AvatarState.TALKING?1.018f:state==AvatarState.DJ?1.025f:1f);
        c.save();c.rotate(rot,w*.5f,h*.52f);c.scale(extra,extra,w*.5f,h*.52f);c.translate(0,bob);
        RectF dst=new RectF(dx,dy,dx+sw,dy+sh);p.setAlpha(state==AvatarState.SLEEPING?165:255);c.drawBitmap(concept,null,dst,p);p.setAlpha(255);c.restore();

        // blink every ~5 seconds, short enough to feel alive
        float blink=((phase>.972f&&phase<.995f)||(state==AvatarState.SLEEPING))?1f:0f;
        if(blink>0)drawBlink(c,w,h);
        drawStateFx(c,w,h);
    }

    private void drawBlink(Canvas c,float w,float h){
        // Positions correspond to the concept portrait after center-crop; soft translucent lids avoid a cut-out look.
        p.setColor(0xCC5A5662);p.setStyle(Paint.Style.FILL);
        float y=h*.185f;float ew=w*.105f,eh=h*.035f;
        c.drawOval(new RectF(w*.365f-ew,y-eh,w*.365f+ew,y+eh),p);
        c.drawOval(new RectF(w*.595f-ew,y-eh,w*.595f+ew,y+eh),p);
    }

    private void drawStateFx(Canvas c,float w,float h){
        stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(Math.max(3f,w*.006f));stroke.setColor(0xFFB65CFF);
        if(state==AvatarState.LISTENING){
            float pulse=.02f*(float)Math.sin(phase*Math.PI*4);for(int i=0;i<3;i++)c.drawCircle(w*.5f,h*.39f,w*(.12f+i*.045f+pulse),stroke);
        }
        if(state==AvatarState.THINKING){p.setColor(0xEEFFFFFF);for(int i=0;i<3;i++)c.drawCircle(w*(.76f+i*.055f),h*(.16f-i*.045f),w*(.012f+i*.008f),p);}
        if(state==AvatarState.TALKING){
            float v=Math.max(.18f,lip);p.setColor(0x884A22FF);c.drawRoundRect(new RectF(w*.16f,h*.80f,w*.84f,h*.86f),h*.03f,h*.03f,p);
            p.setColor(0xFFB65CFF);c.drawRoundRect(new RectF(w*.16f,h*.80f,w*(.16f+.68f*v),h*.86f),h*.03f,h*.03f,p);
        }
        String badge=null;if(state==AvatarState.GAME)badge="🎮 Игра";else if(state==AvatarState.TOAST)badge="🥂 Тост";else if(state==AvatarState.DJ)badge="🎧 DJ";
        if(badge!=null){p.setColor(0xD9111420);c.drawRoundRect(new RectF(w*.68f,h*.78f,w*.94f,h*.85f),h*.03f,h*.03f,p);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(w*.034f);c.drawText(badge,w*.81f,h*.827f,p);}
        if(state==AvatarState.SLEEPING){p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(w*.06f);c.drawText("z Z z",w*.80f,h*.18f,p);}
    }

    private void drawFallback(Canvas c){
        float w=getWidth(),h=getHeight();c.drawColor(0xFF0A0B14);p.setColor(0xFF7B47DB);c.drawCircle(w*.5f,h*.42f,Math.min(w,h)*.28f,p);
        p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(w*.08f);c.drawText("MARTIN",w*.5f,h*.47f,p);
        p.setTextSize(w*.035f);p.setColor(0xFFDAD4FF);c.drawText("визуальный ресурс восстанавливается",w*.5f,h*.54f,p);
    }

    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
}
