package com.wafaa.quran;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.net.Uri;
import android.content.Intent;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
  WebView web;
  ExecutorService pool=Executors.newFixedThreadPool(2);
  File audioDir;
  String safe(String id){return id.matches("[0-9]{1,10}_[0-9]{3}")?id:null;}
  class Bridge {
    @JavascriptInterface public String readQuran(){try(InputStream in=getAssets().open("quran.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}catch(Exception e){return "";}}
    @JavascriptInterface public boolean exists(String id){return safe(id)!=null&&new File(audioDir,id+".mp3").length()>1024;}
    @JavascriptInterface public void download(String id,String url){if(safe(id)==null||!url.startsWith("https://"))return;pool.execute(()->{
      File part=new File(audioDir,id+".part"),done=new File(audioDir,id+".mp3");
      try{
        URL current=new URL(url);String host=current.getHost().toLowerCase(java.util.Locale.ROOT);
        if(!host.endsWith("mp3quran.net")&&!host.endsWith("mp3quran.com"))throw new IOException("Untrusted audio host");
        HttpURLConnection c=(HttpURLConnection)current.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(true);
        if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());
        int total=c.getContentLength();long got=0;byte[] b=new byte[16384];int n;
        try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part)){while((n=in.read(b))!=-1){out.write(b,0,n);got+=n;if(total>0&&got%(512*1024)<16384)notifyJs(id,"progress",(int)(got*100/total));}}
        if(got<1024||!part.renameTo(done))throw new IOException("Incomplete download");notifyJs(id,"done",100);
      }catch(Exception e){part.delete();notifyJs(id,"error",0);}
    });}
    @JavascriptInterface public void delete(String id){if(safe(id)!=null)new File(audioDir,id+".mp3").delete();}
  }
  void notifyJs(String id,String status,int progress){runOnUiThread(()->web.evaluateJavascript("window.onNativeDownload("+JSONObject.quote(id)+","+JSONObject.quote(status)+","+progress+")",null));}
  @Override public void onCreate(Bundle state){super.onCreate(state);audioDir=new File(getFilesDir(),"audio");audioDir.mkdirs();web=new WebView(this);setContentView(web);
    WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);s.setAllowFileAccess(true);web.addJavascriptInterface(new Bridge(),"WafaaAndroid");
    web.setWebChromeClient(new WebChromeClient());web.setWebViewClient(new WebViewClient(){
      @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){Uri u=request.getUrl();if("offline.wafaa.local".equals(u.getHost())&&u.getPath()!=null&&u.getPath().startsWith("/audio/")){
        String id=u.getLastPathSegment();if(id==null||!id.endsWith(".mp3"))return null;id=id.substring(0,id.length()-4);if(safe(id)==null)return null;
        File f=new File(audioDir,id+".mp3");if(!f.exists())return null;
        try{java.util.Map<String,String> headers=new java.util.HashMap<>();headers.put("Access-Control-Allow-Origin","*");headers.put("Accept-Ranges","bytes");headers.put("Content-Type","audio/mpeg");String range=request.getRequestHeaders().get("Range");long start=0,end=f.length()-1;int code=200;String reason="OK";
          if(range!=null&&range.startsWith("bytes=")){String[] parts=range.substring(6).split("-",2);start=Long.parseLong(parts[0]);if(parts.length>1&&!parts[1].isEmpty())end=Math.min(end,Long.parseLong(parts[1]));code=206;reason="Partial Content";headers.put("Content-Range","bytes "+start+"-"+end+"/"+f.length());}
          if(start<0||start> end)return null;FileInputStream in=new FileInputStream(f);long remaining=start;while(remaining>0){long skipped=in.skip(remaining);if(skipped<=0)break;remaining-=skipped;}headers.put("Content-Length",String.valueOf(end-start+1));final long length=end-start+1;return new WebResourceResponse("audio/mpeg",null,code,reason,headers,new java.io.FilterInputStream(in){long left=length;@Override public int read()throws IOException{if(left<=0)return -1;int x=super.read();if(x>=0)left--;return x;}@Override public int read(byte[] b,int o,int l)throws IOException{if(left<=0)return -1;int x=super.read(b,o,(int)Math.min(l,left));if(x>0)left-=x;return x;}});
        }catch(Exception e){return null;}
      }return super.shouldInterceptRequest(v,request);}
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){String scheme=r.getUrl().getScheme();if("http".equals(scheme)||"https".equals(scheme)){startActivity(new Intent(Intent.ACTION_VIEW,r.getUrl()));return true;}return false;}
    });web.loadUrl("file:///android_asset/index.html");
  }
  @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
  @Override protected void onDestroy(){pool.shutdownNow();web.destroy();super.onDestroy();}
}
