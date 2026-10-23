package cn.wengmang.qiping;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional trusted catalog. No notification scraping, polling service, APK downloads or fabricated updates. */
final class UpdateRepository {
    static final class Record {
        final String packageName,versionName;
        final long versionCode;
        final int minSdk;
        Record(String pkg,long code,String name,int min){packageName=pkg;versionCode=code;versionName=name;minSdk=min;}
    }
    interface Listener {void onUpdatesChanged();}
    private final Handler main=new Handler();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final SharedPreferences cache;
    private final Listener listener;
    private Map<String,Record> records=new LinkedHashMap<String,Record>();
    private volatile boolean closed;
    private volatile HttpURLConnection connection;
    private String feedUrl="",storePackage="",storeTemplate="",sourceName="";
    private boolean configured,configError,checking,failed,loaded;
    private long checkedAt,lastAttempt;

    UpdateRepository(Context context,Listener listener) {
        this.listener=listener;cache=context.getSharedPreferences("update_catalog",Context.MODE_PRIVATE);
        try {
            InputStream in=context.getAssets().open("update-source.json");String body;
            try {body=read(in,16384,0);}finally {in.close();}
            JSONObject config=new JSONObject(body);feedUrl=config.optString("feedUrl","").trim();
            if(feedUrl.length()==0)return;
            sourceName=config.getString("name").trim();storePackage=config.getString("storePackage");storeTemplate=config.getString("storeUriTemplate");
            configured=sourceName.length()>0&&sourceName.length()<=48&&UpdatePolicy.https(feedUrl)
                    &&UpdatePolicy.packageName(storePackage)&&UpdatePolicy.storeTemplate(storeTemplate);
            configError=!configured;
        }catch(Exception error){configError=true;}
    }
    boolean configured(){return configured;}
    boolean checking(){return checking;}
    long checkedAt(){return checkedAt;}
    String sourceName(){return sourceName;}
    String status(int count) {
        if(configError)return "更新服务配置不可用";
        if(!configured)return "尚未接入更新服务";
        if(checking)return "正在检查应用更新…";
        if(failed)return UpdatePolicy.fresh(checkedAt,System.currentTimeMillis())?"检查失败，暂时保留上次的更新提醒":"暂时无法检查更新，请稍后重试";
        if(!UpdatePolicy.fresh(checkedAt,System.currentTimeMillis()))return "等待检查应用更新";
        return count>0?"发现 "+count+" 个可更新应用 · "+sourceName:"此更新服务暂未报告适合本机的新版本";
    }
    String emptyMessage() {
        if(!configured)return "可在应用管理菜单中前往应用商店，或使用应用自带的更新功能。";
        if(checking)return "检查在后台进行，可以继续使用遥控器浏览。";
        return "这里只显示已接入此更新服务的应用；其他应用仍可在应用商店中检查。";
    }
    Record available(AppEntry app) {
        if(app==null||!UpdatePolicy.fresh(checkedAt,System.currentTimeMillis()))return null;
        Record record=records.get(app.packageName);
        return record!=null&&UpdatePolicy.newer(app.versionCode,record.versionCode,record.minSdk,Build.VERSION.SDK_INT)?record:null;
    }
    Intent storeIntent(String packageName) {
        if(!configured||!UpdatePolicy.packageName(packageName))return null;
        return new Intent(Intent.ACTION_VIEW,Uri.parse(storeTemplate.replace("{package}",packageName))).setPackage(storePackage);
    }
    private String identity(){return feedUrl+"\n"+storePackage+"\n"+storeTemplate;}
    void check(final boolean force) {
        if(closed||!configured||checking)return;
        final long now=System.currentTimeMillis();
        if(loaded&&!force&&((lastAttempt>0&&now>=lastAttempt&&now-lastAttempt<UpdatePolicy.RETRY_INTERVAL)
                ||(UpdatePolicy.fresh(checkedAt,now)&&now-checkedAt<UpdatePolicy.CHECK_INTERVAL)))return;
        checking=true;lastAttempt=now;listener.onUpdatesChanged();
        worker.execute(new Runnable(){@Override public void run(){
            try {
                if(!loaded) {
                    final String storedBody=cache.getString("body","");final long storedAt=cache.getLong("checked_at",0);
                    if(identity().equals(cache.getString("source",""))&&UpdatePolicy.fresh(storedAt,now)) {
                        final Map<String,Record> stored=parse(storedBody);
                        if(!force&&now-storedAt<UpdatePolicy.CHECK_INTERVAL){publish(stored,storedAt,false);return;}
                        main.post(new Runnable(){@Override public void run(){if(!closed){records=stored;checkedAt=storedAt;listener.onUpdatesChanged();}}});
                    }
                }
            }catch(Exception ignored){/* A bad cache never prevents a fresh check. */}
            try {
                String body=download();Map<String,Record> fresh=parse(body);long at=System.currentTimeMillis();
                if(closed)return;
                cache.edit().putString("source",identity()).putString("body",body).putLong("checked_at",at).commit();
                publish(fresh,at,false);
            }catch(Exception error){publish(null,0,true);}
        }});
    }
    private void publish(final Map<String,Record> result,final long at,final boolean error) {
        main.post(new Runnable(){@Override public void run(){
            if(closed)return;
            loaded=true;checking=false;failed=error;
            if(result!=null){records=result;checkedAt=at;}
            listener.onUpdatesChanged();
        }});
    }
    private String download()throws Exception {
        URL url=new URL(feedUrl);long deadline=SystemClock.elapsedRealtime()+15000;
        for(int redirect=0;redirect<3;redirect++) {
            if(closed||SystemClock.elapsedRealtime()>deadline)throw new java.io.IOException("Cancelled or timed out");
            if(!UpdatePolicy.https(url.toString()))throw new java.io.IOException("HTTPS required");
            HttpURLConnection current=(HttpURLConnection)url.openConnection();connection=current;
            current.setConnectTimeout(5000);current.setReadTimeout(5000);current.setInstanceFollowRedirects(false);
            current.setRequestProperty("Accept","application/json");current.setRequestProperty("Accept-Encoding","identity");
            try {
                int code=current.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308) {
                    String target=current.getHeaderField("Location");if(target==null)throw new java.io.IOException("No redirect target");
                    url=new URL(url,target);continue;
                }
                if(code!=200||current.getContentLength()>524288)throw new java.io.IOException("Invalid catalog response");
                InputStream in=current.getInputStream();try{return read(in,524288,deadline);}finally{in.close();}
            }finally{current.disconnect();connection=null;}
        }
        throw new java.io.IOException("Too many redirects");
    }
    private static String read(InputStream in,int limit,long deadline)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] chunk=new byte[4096];int n;
        while((n=in.read(chunk))!=-1) {
            if(Thread.currentThread().isInterrupted()||(deadline>0&&SystemClock.elapsedRealtime()>deadline))throw new java.io.IOException("Cancelled or timed out");
            if(out.size()+n>limit)throw new java.io.IOException("Catalog too large");out.write(chunk,0,n);
        }
        return new String(out.toByteArray(),"UTF-8");
    }
    static Map<String,Record> parse(String body)throws Exception {
        JSONObject json=new JSONObject(body);if(!"1".equals(json.get("schemaVersion").toString()))throw new IllegalArgumentException("Unsupported schema");
        JSONArray array=json.getJSONArray("apps");if(array.length()>5000)throw new IllegalArgumentException("Too many entries");
        Map<String,Record> parsed=new LinkedHashMap<String,Record>();
        for(int i=0;i<array.length();i++) {
            JSONObject row=array.getJSONObject(i);String pkg=row.getString("packageName"),codeText=row.get("versionCode").toString();
            if(!UpdatePolicy.packageName(pkg)||!codeText.matches("[0-9]{1,19}")||parsed.containsKey(pkg))throw new IllegalArgumentException("Invalid app entry");
            long code=Long.parseLong(codeText);String minText=row.get("minSdk").toString();
            if(!minText.matches("[0-9]{1,3}"))throw new IllegalArgumentException("Invalid minSdk");
            int min=Integer.parseInt(minText);String name=row.optString("versionName",codeText).replaceAll("[\\p{Cntrl}]","");
            if(code<=0||min<1||name.length()>80)throw new IllegalArgumentException("Invalid version");
            if(name.length()==0)name=codeText;
            parsed.put(pkg,new Record(pkg,code,name,min));
        }
        return parsed;
    }
    void close(){closed=true;HttpURLConnection current=connection;if(current!=null)current.disconnect();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
