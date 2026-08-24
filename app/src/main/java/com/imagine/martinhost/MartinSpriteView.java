package com.imagine.martinhost;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** Offline animated Martin avatar powered by local SVG/CSS. */
public final class MartinSpriteView extends WebView {
    public enum State { IDLE, LISTENING, THINKING, TALKING, GAME, TOAST, DJ, HAPPY, SLEEPING }
    private State state=State.IDLE;
    private boolean ready=false;

    public MartinSpriteView(Context c){super(c);init();}
    public MartinSpriteView(Context c, AttributeSet a){super(c,a);init();}

    private void init(){
        setBackgroundColor(Color.TRANSPARENT);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        WebSettings s=getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        setWebViewClient(new android.webkit.WebViewClient(){
            @Override public void onPageFinished(WebView v,String url){ready=true;applyState();}
        });
        loadUrl("file:///android_asset/martin_avatar.html");
    }

    public void setState(State s){state=s==null?State.IDLE:s;applyState();}
    public State getState(){return state;}
    public void setSpeechLevel(float v){if(!ready)return;float x=Math.max(0f,Math.min(1f,v));evaluateJavascript("setLevel("+x+")",null);}
    private void applyState(){if(!ready)return;evaluateJavascript("setState('"+state.name().toLowerCase()+"')",null);}
}
