package com.imagine.martinhost;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** One-time OAuth sign-in for direct Yandex Music playback. */
public final class YandexAuthActivity extends Activity {
    private static final String CLIENT_ID="23cabbbdc6cd418abb4b39c32c41195d";
    private WebView web;
    private TextView status;
    private boolean captured;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF0B0C10);root.setPadding(18,18,18,18);
        status=new TextView(this);status.setText("Подключение Яндекс Музыки. Войдите в свой аккаунт Яндекса. Пароль остаётся на странице Яндекса и приложение его не получает.");status.setTextColor(Color.WHITE);status.setTextSize(16);status.setPadding(8,8,8,14);root.addView(status);
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);s.setSafeBrowsingEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){super.onPageStarted(view,url,favicon);tryCapture(url);}
            @Override public void onPageFinished(WebView view,String url){super.onPageFinished(view,url);tryCapture(url);view.evaluateJavascript("location.href",value->{if(value!=null){String u=value.replaceAll("^\\\"|\\\"$","").replace("\\u003D","=").replace("\\u0026","&");tryCapture(u);}});}
        });
        web.loadUrl("https://oauth.yandex.ru/authorize?response_type=token&client_id="+CLIENT_ID);
    }

    private void tryCapture(String url){
        if(captured||url==null)return;
        int i=url.indexOf("access_token=");if(i<0)return;
        String tail=url.substring(i+13);int amp=tail.indexOf('&');if(amp>=0)tail=tail.substring(0,amp);int hash=tail.indexOf('#');if(hash>=0)tail=tail.substring(0,hash);
        String token=URLDecoder.decode(tail,StandardCharsets.UTF_8).trim();if(token.isBlank())return;
        captured=true;YandexMusicClient client=YandexMusicClient.get(this);client.setToken(token);status.setText("Аккаунт подключён. Проверяю доступ к музыке…");
        client.validateToken(new YandexMusicClient.Callback(){
            public void onStarted(YandexMusicClient.TrackInfo t){}
            public void onFinished(YandexMusicClient.TrackInfo t){runOnUiThread(()->{status.setText("Яндекс Музыка подключена. Можно запускать музыку и викторину.");setResult(RESULT_OK);new android.os.Handler(getMainLooper()).postDelayed(YandexAuthActivity.this::finish,700);});}
            public void onError(String e){runOnUiThread(()->{captured=false;status.setText("Не удалось подтвердить аккаунт: "+e+"\nПовторите вход.");client.clearToken();});}
        });
    }

    @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
