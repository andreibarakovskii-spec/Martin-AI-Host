package com.imagine.martinhost;

import android.app.*;import android.content.*;import android.graphics.Color;import android.net.Uri;import android.os.*;import android.view.*;import android.webkit.*;import android.widget.*;

/** Sandboxed in-app Yandex Music web session. No JavaScript bridge or credential access. */
public final class YandexMusicWebActivity extends Activity {
 private WebView web;private ProgressBar progress;private TextView address;
 @Override public void onCreate(Bundle b){super.onCreate(b);build();String q=getIntent().getStringExtra("query");load(q==null||q.isBlank()?"https://music.yandex.ru/home":"https://music.yandex.ru/search?text="+Uri.encode(q));}
 private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF080A12);LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);Button back=new Button(this);back.setText("‹ Сергей");back.setAllCaps(false);back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(-2,-2));address=new TextView(this);address.setText("Яндекс Музыка • защищённая страница");address.setTextColor(Color.WHITE);address.setPadding(12,0,4,0);bar.addView(address,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);root.addView(progress,new LinearLayout.LayoutParams(-1,6));web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSafeBrowsingEnabled(true);s.setSupportMultipleWindows(false);CookieManager cm=CookieManager.getInstance();cm.setAcceptCookie(true);cm.setAcceptThirdPartyCookies(web,true);
  web.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView v,int n){progress.setProgress(n);progress.setVisibility(n>=100?View.GONE:View.VISIBLE);}});
  web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){Uri u=r.getUrl();if(isYandex(u)){v.loadUrl(u.toString());return true;}try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}return true;}@Override public void onPageFinished(WebView v,String url){address.setText(url.contains("passport")?"Вход выполняется на странице Яндекса":"Яндекс Музыка • аккаунт сохраняется");CookieManager.getInstance().flush();}});
 }
 private static boolean isYandex(Uri u){if(!"https".equalsIgnoreCase(u.getScheme()))return false;String h=u.getHost();return h!=null&&(h.equals("ya.ru")||h.endsWith(".ya.ru")||h.equals("yandex.ru")||h.endsWith(".yandex.ru")||h.equals("yandex.com")||h.endsWith(".yandex.com")||h.equals("yandex.net")||h.endsWith(".yandex.net"));}
 private void load(String url){if(isYandex(Uri.parse(url)))web.loadUrl(url);}
 @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.setWebChromeClient(null);web.setWebViewClient(null);web.destroy();}super.onDestroy();}
}
