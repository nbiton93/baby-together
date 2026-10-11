package com.natibiton.babytogether;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.AtomicFile;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final String SCOPE="https://www.googleapis.com/auth/drive.file";
    private static final int AUTH=4101, EXPORT=4102, MAX_BYTES=16*1024*1024;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private WebView web;
    private AtomicFile store;
    private String authId, exportId, exportData;
    private volatile String token;
    private long tokenUntil;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved); store=new AtomicFile(new File(getNoBackupFilesDir(),"journal.json"));
        web=new WebView(this); web.setBackgroundColor(Color.rgb(247,246,242));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setDomStorageEnabled(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        final WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri u=request.getUrl();
                if("https".equals(u.getScheme())&&"appassets.androidplatform.net".equals(u.getHost())&&Arrays.asList("/assets/index.html","/assets/core.js","/assets/cloud.js","/assets/bridge.js","/assets/app.js","/assets/style.css").contains(u.getPath()))return loader.shouldInterceptRequest(u);
                return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
            }
        });
        web.addJavascriptInterface(new Bridge(),"BabyNative");setContentView(web);
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){
                web.evaluateJavascript("(function(){const m=document.querySelector('#modal');if(m&&m.open){m.dispatchEvent(new Event('cancel',{cancelable:true}))&&m.close();return true;}return false;})()",result->{if(!"true".equals(result))finish();});
            }
        });
        if(android.os.Build.VERSION.SDK_INT>=30){web.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});}
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }
    private class Bridge {
        @JavascriptInterface public void postMessage(String message){
            if(message.length()>MAX_BYTES)return;
            try{JSONObject request=new JSONObject(message);String id=request.getString("id"),method=request.getString("method");JSONObject args=request.optJSONObject("args");
                if(!id.matches("[0-9]{1,12}"))return;
                if("authorize".equals(method)){runOnUiThread(()->authorize(id,args));return;}
                if("export".equals(method)){runOnUiThread(()->export(id,args));return;}
                io.execute(()->{try{
                    Object value;
                    switch(method){
                        case "uuid": value=UUID.randomUUID().toString();break;
                        case "config": value=new JSONObject().put("platform","android").put("googleConfigured",BuildConfig.GOOGLE_CONFIGURED);break;
                        case "read": try{value=new String(store.readFully(),StandardCharsets.UTF_8);}catch(FileNotFoundException missing){value=JSONObject.NULL;}break;
                        case "write":
                            byte[] data=args.getString("data").getBytes(StandardCharsets.UTF_8);if(data.length>MAX_BYTES)throw new IOException("היומן גדול מדי");new JSONObject(args.getString("data"));
                            FileOutputStream stream=null;try{stream=store.startWrite();stream.write(data);store.finishWrite(stream);}catch(Exception e){if(stream!=null)store.failWrite(stream);throw e;}value=true;break;
                        case "drive": value=drive(args);break;
                        case "forget": token=null;tokenUntil=0;value=true;break;
                        default: throw new IOException("פעולה לא נתמכת");
                    }
                    reply(id,true,value);
                }catch(Exception e){reply(id,false,safeMessage(e));}});
            }catch(Exception ignored){/* malformed bridge messages have no authority */}
        }
    }
    private void authorize(String id,JSONObject args){
        if(!BuildConfig.GOOGLE_CONFIGURED){reply(id,false,"חיבור Google עדיין לא הוגדר בגרסה הזאת");return;}
        if(authId!=null){reply(id,false,"כבר מתבצע חיבור ל־Google");return;}
        boolean picker=args.optBoolean("picker"),interactive=args.optBoolean("interactive");
        if(!picker&&token!=null&&System.currentTimeMillis()<tokenUntil){reply(id,true,new JSONObject());return;}
        authId=id;
        AuthorizationRequest.Builder b=AuthorizationRequest.builder().setRequestedScopes(Collections.singletonList(new Scope(SCOPE))).setOptOutIncludingGrantedScopes(true);
        if(picker)b.setPrompt(AuthorizationRequest.Prompt.CONSENT).addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER,"true").addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_ALLOW_MULTIPLE,"false").addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_MIMETYPES,"application/json");
        Identity.getAuthorizationClient(this).authorize(b.build()).addOnSuccessListener(result->{
            if(result.hasResolution()){
                if(!interactive){finishAuth(false,"נדרש חיבור מחדש ל־Google דרך מסך ביחד");return;}
                try{startIntentSenderForResult(Objects.requireNonNull(result.getPendingIntent()).getIntentSender(),AUTH,null,0,0,0);}catch(Exception e){finishAuth(false,"לא ניתן לפתוח את חלון Google");}
            }else acceptAuthorization(result);
        }).addOnFailureListener(e->finishAuth(false,"לא ניתן להתחבר ל־Google. בדקו את חיבור האינטרנט והגדרות האפליקציה"));
    }
    private void acceptAuthorization(AuthorizationResult result){
        if(result.getAccessToken()==null||!result.getGrantedScopes().contains(SCOPE)){finishAuth(false,"לא ניתנה הרשאת גישה ליומן ב־Drive");return;}
        token=result.getAccessToken();tokenUntil=System.currentTimeMillis()+45*60*1000;
        JSONArray picked=new JSONArray();Bundle params=result.getTokenResponseParams();String ids=params==null?null:params.getString("picked_file_ids");if(ids!=null)for(String id:ids.split(","))if(!id.isEmpty())picked.put(id);
        try{finishAuth(true,new JSONObject().put("pickedFileIds",picked));}catch(Exception e){finishAuth(false,"תשובת Google אינה תקינה");}
    }
    private void finishAuth(boolean ok,Object value){String id=authId;authId=null;if(id!=null)reply(id,ok,value);}
    private JSONObject drive(JSONObject args)throws Exception{
        String path=args.getString("path"),method=args.getString("method");
        if(!path.matches("/(?:upload/)?drive/v3/files(?:/[A-Za-z0-9_-]+)?(?:/permissions(?:/[A-Za-z0-9_-]+)?)?"))throw new IOException("נתיב Drive לא תקין");
        if(!Arrays.asList("GET","POST","PATCH","DELETE").contains(method)||token==null)throw new IOException("נדרש חיבור ל־Google");
        Uri.Builder url=Uri.parse("https://www.googleapis.com"+path).buildUpon();JSONObject query=args.optJSONObject("query");if(query!=null){Iterator<String> keys=query.keys();while(keys.hasNext()){String k=keys.next();url.appendQueryParameter(k,query.getString(k));}}
        HttpURLConnection conn=(HttpURLConnection)new URL(url.build().toString()).openConnection();
        try{
            conn.setConnectTimeout(15000);conn.setReadTimeout(20000);conn.setInstanceFollowRedirects(false);conn.setRequestMethod(method);conn.setRequestProperty("Authorization","Bearer "+token);conn.setRequestProperty("Accept","application/json");
            if(!args.isNull("body")){byte[] bytes=args.getString("body").getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("היומן גדול מדי");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");conn.setFixedLengthStreamingMode(bytes.length);try(OutputStream os=conn.getOutputStream()){os.write(bytes);}}
            int status=conn.getResponseCode();InputStream input=status>=400?conn.getErrorStream():conn.getInputStream();String body="";
            if(input!=null)try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("קובץ Drive גדול מדי");out.write(buffer,0,n);}body=out.toString(StandardCharsets.UTF_8.name());}
            if(status==401){String old=token;token=null;tokenUntil=0;if(old!=null)runOnUiThread(()->Identity.getAuthorizationClient(this).clearToken(ClearTokenRequest.builder().setToken(old).build()));}
            return new JSONObject().put("status",status).put("text",body);
        }finally{conn.disconnect();}
    }
    private void export(String id,JSONObject args){
        if(exportId!=null){reply(id,false,"כבר פתוח חלון שמירת גיבוי");return;}
        exportId=id;exportData=args.optString("data");Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"baby-together-backup.json");
        try{startActivityForResult(intent,EXPORT);}catch(Exception e){reply(exportId,false,"לא ניתן לפתוח שמירת קובץ");exportId=null;exportData=null;}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==AUTH){if(result!=RESULT_OK){finishAuth(false,"החיבור בוטל");return;}try{acceptAuthorization(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data));}catch(Exception e){finishAuth(false,"החיבור ל־Google לא הושלם");}}
        if(request==EXPORT){String id=exportId,content=exportData;exportId=null;exportData=null;if(id==null)return;if(result!=RESULT_OK||data==null||data.getData()==null){reply(id,false,"שמירת הגיבוי בוטלה");return;}Uri uri=data.getData();io.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();out.write(content.getBytes(StandardCharsets.UTF_8));reply(id,true,true);}catch(Exception e){reply(id,false,"לא ניתן לשמור את הגיבוי");}});}
    }
    private String safeMessage(Exception e){return e instanceof IOException&&e.getMessage()!=null&&e.getMessage().matches(".*[א-ת].*")?e.getMessage():"הפעולה לא הושלמה. הרישומים הקודמים נשמרו.";}
    private void reply(String id,boolean ok,Object value){runOnUiThread(()->{if(isFinishing()||web==null)return;String json=new JSONArray().put(id).put(ok).put(value).toString();web.evaluateJavascript("window.BTReply.apply(null,"+json+");",null);});}
    @Override protected void onDestroy(){token=null;io.shutdown();if(web!=null){web.removeJavascriptInterface("BabyNative");web.destroy();web=null;}super.onDestroy();}
}
