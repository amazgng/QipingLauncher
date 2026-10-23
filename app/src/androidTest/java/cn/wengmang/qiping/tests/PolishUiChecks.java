package cn.wengmang.qiping.tests;

import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import java.io.*;import java.lang.reflect.*;import java.util.*;

/** Android 9 interaction and lifecycle checks. Run only on the isolated test emulator. */
final class PolishUiChecks {
    private final Instrumentation test;private final Activity activity;private final StringBuilder report;
    private PolishUiChecks(Instrumentation test,Activity activity,StringBuilder report){this.test=test;this.activity=activity;this.report=report;}
    static void run(Instrumentation test,Activity activity,StringBuilder report)throws Exception{new PolishUiChecks(test,activity,report).checks();}
    static void soak(Instrumentation test,Activity activity,StringBuilder report)throws Exception{new PolishUiChecks(test,activity,report).soakChecks();}
    private void check(boolean value,String name){if(!value)throw new AssertionError(name);report.append("PASS ").append(name).append('\n');}
    private void ui(Runnable run){test.runOnMainSync(run);test.waitForIdleSync();}
    private static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new RuntimeException(e);}}
    private static Object call(Object object,String name,Class<?>[] types,Object...args){try{Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}catch(Exception e){throw new RuntimeException(e);}}
    private static Object call(Object object,String name){return call(object,name,new Class<?>[0]);}
    private void invoke(final String method){ui(new Runnable(){public void run(){call(activity,method);}});settle();}
    private void settle(){SystemClock.sleep(260);test.waitForIdleSync();}
    private Dialog dialog(){return (Dialog)field(activity,"currentDialog");}
    private View find(View view,String text){if(String.valueOf(view.getContentDescription()).equals(text))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View result=find(((ViewGroup)view).getChildAt(i),text);if(result!=null)return result;}return null;}
    private void choose(final String text){final Dialog dialog=dialog();final View view=find(dialog.getWindow().getDecorView(),text);check(view!=null,"diagnostics action available: "+text);ui(new Runnable(){public void run(){view.requestFocus();}});key(KeyEvent.KEYCODE_DPAD_CENTER);}
    private void key(int code){TestKeys.press(test,code);settle();}
    private void scene(final int slot){ui(new Runnable(){public void run(){call(field(activity,"scene"),"showScene",new Class<?>[]{int.class,boolean.class},slot,false);}});waitScene(slot);}
    private void waitScene(int slot){for(int i=0;i<80;i++){final Object s=field(activity,"scene");if(((Integer)field(s,"currentIndex"))==slot&&field(s,"current")!=null)return;SystemClock.sleep(100);test.waitForIdleSync();}throw new AssertionError("wallpaper did not load scene "+slot);}
    private void waitContrast(boolean bright){for(int i=0;i<80;i++){float strength=(Float)call(field(activity,"scene"),"contrastStrength");if(bright?strength>.98f:strength<.01f)return;SystemClock.sleep(100);test.waitForIdleSync();}throw new AssertionError("contrast did not update");}
    private File wallpaper(int slot){return (File)call(field(activity,"wallpapers"),"file",new Class<?>[]{int.class},slot);}
    private static byte[] read(File file)throws IOException{if(!file.exists())return null;ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream in=new FileInputStream(file);byte[] b=new byte[8192];try{int n;while((n=in.read(b))!=-1)out.write(b,0,n);}finally{in.close();}return out.toByteArray();}
    private void writeWallpaper(int slot,int color)throws IOException{File f=wallpaper(slot);f.getParentFile().mkdirs();Bitmap b=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888);b.eraseColor(color);FileOutputStream out=new FileOutputStream(f);try{b.compress(Bitmap.CompressFormat.JPEG,90,out);}finally{out.close();b.recycle();}}
    private void shot(String name)throws IOException{Bitmap b=test.getUiAutomation().takeScreenshot();if(b==null)return;File f=new File(activity.getExternalFilesDir(null),name+".png");FileOutputStream out=new FileOutputStream(f);try{b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{out.close();b.recycle();}}
    private void checks()throws Exception{
        final File[] files=new File[6];final byte[][] old=new byte[6][];for(int i=0;i<6;i++){files[i]=wallpaper(i);old[i]=read(files[i]);}
        final SharedPreferences prefs=activity.getSharedPreferences("desktop",0);final int oldFont=prefs.getInt("font_size",0),oldMargin=prefs.getInt("margin",1);
        try{
            writeWallpaper(0,Color.WHITE);ui(new Runnable(){public void run(){call(field(activity,"scene"),"reloadWallpapers");}});waitContrast(true);
            check((Float)call(field(activity,"scene"),"contrastStrength")>.98f,"white wallpaper enables adaptive shading");
            int tint=(Integer)call(field(activity,"scene"),"adaptiveTint",new Class<?>[]{int.class},0x54171c25);check((tint>>>24)>=170&&(tint>>>24)<255,"bright glass stays translucent with stronger contrast");
            final int[] headerColor=new int[1];ui(new Runnable(){public void run(){View view=(View)field(activity,"scene");Bitmap frame=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(frame));headerColor[0]=frame.getPixel((int)(frame.getWidth()*.84f),(int)(frame.getHeight()*.08f));frame.recycle();}});
            check(Color.red(headerColor[0])<120&&Color.green(headerColor[0])<120,"white wallpaper header shade keeps clock readable");shot("polish-white-home");
            invoke("showDiagnostics");key(KeyEvent.KEYCODE_DPAD_DOWN);check(String.valueOf(dialog().getCurrentFocus().getContentDescription()).equals("查看最近记录"),"D-pad moves through diagnostics menu");
            choose("查看当前状态");Dialog detail=dialog();android.widget.ScrollView scroll=(android.widget.ScrollView)field(detail,"bodyScroll");key(KeyEvent.KEYCODE_DPAD_DOWN);check(scroll.getScrollY()>0,"diagnostics text scrolls with remote");shot("polish-diagnostics");key(KeyEvent.KEYCODE_BACK);
            choose("查看最近记录");check(dialog()!=null&&dialog().isShowing(),"bounded journal opens");key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);check(dialog()==null,"Back closes diagnostics to original page");
            writeWallpaper(0,0xff10151d);ui(new Runnable(){public void run(){call(field(activity,"scene"),"reloadWallpapers");}});waitContrast(false);
            check((Integer)call(field(activity,"scene"),"adaptiveTint",new Class<?>[]{int.class},0x54171c25)==0x54171c25,"dark wallpaper preserves original glass tint");shot("polish-dark-home");
            for(int i=1;i<6;i++)writeWallpaper(i,Color.WHITE);ui(new Runnable(){public void run(){call(field(activity,"scene"),"reloadWallpapers");}});scene(5);waitContrast(true);
            ui(new Runnable(){public void run(){call(activity,"go",new Class<?>[]{int.class},2);}});settle();shot("polish-white-settings");
            ui(new Runnable(){public void run(){call(activity,"go",new Class<?>[]{int.class},1);}});settle();waitScene(4);
            final int[] captionColor=new int[1];ui(new Runnable(){public void run(){View view=(View)field(activity,"scene");Bitmap frame=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(frame));captionColor[0]=frame.getPixel((int)(frame.getWidth()*.87f),(int)(frame.getHeight()*.50f));frame.recycle();}});
            check(Color.red(captionColor[0])<120&&Color.green(captionColor[0])<120,"bright right-edge app captions have shaded background");shot("polish-white-apps");
            final int before=(Integer)field(activity,"scanGeneration");ui(new Runnable(){public void run(){for(int i=0;i<100;i++)call(activity,"refreshApps");}});
            for(int i=0;i<100&&(Boolean)field(activity,"scanRunning");i++)SystemClock.sleep(100);
            check((Integer)field(activity,"scanGeneration")-before<=2,"100 refresh requests coalesce into at most two scans");check(!(Boolean)field(activity,"loading"),"coalesced scan completes");
            final Object diagnostics=field(activity,"diagnostics");String log=(String)call(diagnostics,"snapshot");check(log.contains("前台内存")&&log.contains("桌面创建"),"runtime and lifecycle records available");
            check(!log.contains("http://")&&!log.contains("配对码")&&!log.contains("file://"),"journal excludes transfer credentials and photo paths");
            invoke("showDiagnostics");choose("导出诊断文件");
            SystemClock.sleep(600);key(KeyEvent.KEYCODE_BACK);
            for(int wait=0;wait<20&&dialog()==null;wait++)SystemClock.sleep(100);
            check(field(activity,"pendingDiagnosticReport")==null,"cancelled document export releases report snapshot");
            if(dialog()!=null)key(KeyEvent.KEYCODE_BACK);
            invoke("showDiagnostics");choose("清空诊断记录");key(KeyEvent.KEYCODE_BACK);
            check((Integer)call(diagnostics,"count")>0,"cancel clearing retains diagnostic records");
            choose("清空诊断记录");choose("确认清空");SystemClock.sleep(500);
            check((Integer)call(diagnostics,"count")==0,"confirmed clear removes bounded journal");key(KeyEvent.KEYCODE_BACK);

            ui(new Runnable(){public void run(){call(activity,"onTrimMemory",new Class<?>[]{int.class},10);}});check((Integer)call(field(activity,"scene"),"cachedSceneCount")<=1,"memory notification trims scene cache");
            ui(new Runnable(){public void run(){prefs.edit().putInt("font_size",2).putInt("margin",2).commit();call(activity,"go",new Class<?>[]{int.class},4);}});settle();
            final View diagnostic=(View)((Map<?,?>)field(activity,"controls")).get("device_diagnostics");
            check(diagnostic!=null&&diagnostic.isShown(),"diagnostics entry visible at largest font and margin");
            ui(new Runnable(){public void run(){diagnostic.requestFocus();}});key(KeyEvent.KEYCODE_DPAD_CENTER);shot("polish-large-diagnostics");
            choose("查看当前状态");key(KeyEvent.KEYCODE_DPAD_DOWN);check(dialog().getWindow().getDecorView().getHeight()<=activity.getWindow().getDecorView().getHeight()*.9f,"large-font diagnostics fits television safe area");key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);

        }finally{
            ui(new Runnable(){public void run(){call(activity,"dismissDialog");prefs.edit().putInt("font_size",oldFont).putInt("margin",oldMargin).commit();}});for(int i=0;i<6;i++){if(old[i]==null)files[i].delete();else{files[i].getParentFile().mkdirs();FileOutputStream out=new FileOutputStream(files[i]);out.write(old[i]);out.close();}}
            ui(new Runnable(){public void run(){call(field(activity,"scene"),"reloadWallpapers");call(activity,"go",new Class<?>[]{int.class},0);}});
        }
    }
    private long javaBytes(){Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    private void soakChecks()throws Exception{
        final long started=SystemClock.elapsedRealtime();long baseline=0,last=0;final Object scene=field(activity,"scene");
        final int[] initialGlasses=new int[1];ui(new Runnable(){public void run(){initialGlasses[0]=(Integer)call(scene,"registeredGlassCount");}});
        for(int round=0;round<12;round++){
            for(int slot=0;slot<6;slot++)scene(slot);
            for(int repeat=0;repeat<5;repeat++){invoke("showDiagnostics");key(KeyEvent.KEYCODE_BACK);}
            final long[] cache=new long[1];final int[] glasses=new int[1];ui(new Runnable(){public void run(){cache[0]=(Long)call(scene,"cachedBytes");glasses[0]=(Integer)call(scene,"registeredGlassCount");}});
            check(cache[0]<=(Long)field(field(activity,"performance"),"sceneBytes"),"cache within budget after cycle "+round);
            check(glasses[0]==initialGlasses[0],"dialog glass references released after cycle "+round);
            check((Integer)call(scene,"queuedLoads")<=1,"decode queue bounded after cycle "+round);
            if(round%2==0){
                final Intent intent=new Intent(Intent.ACTION_MAIN).setClassName("cn.wengmang.qiping.tests","cn.wengmang.qiping.tests.ProbeActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ui(new Runnable(){public void run(){activity.startActivity(intent);}});TestKeys.awaitPackage(test,"cn.wengmang.qiping.tests");SystemClock.sleep(450);
                final boolean[] released={false};for(int attempt=0;attempt<60;attempt++){ui(new Runnable(){public void run(){released[0]=field(scene,"current")==null&&((Map<?,?>)field(activity,"icons")).isEmpty();}});if(released[0])break;SystemClock.sleep(100);}
                check(released[0],"external app releases wallpaper/icons cycle "+round);
                check(!(Boolean)field(activity,"resumed"),"background sampling paused cycle "+round);
                key(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(test,"cn.wengmang.qiping");settle();
            }
            System.gc();SystemClock.sleep(100);last=javaBytes();if(round==2)baseline=last;
            Debug.MemoryInfo info=new Debug.MemoryInfo();Debug.getMemoryInfo(info);report.append("MEASURE cycle=").append(round).append(" java=").append(last).append(" native=").append(Debug.getNativeHeapAllocatedSize()).append(" pssKB=").append(info.getTotalPss()).append('\n');
            Bundle progress=new Bundle();progress.putString("stream","Stability cycle "+(round+1)+" / 12\n");test.sendStatus(0,progress);
        }
        check(last-baseline<16*1024*1024,"Java heap does not grow by 16 MiB after warmup");
        // Leave the real foreground timer running through its 5-minute sample boundary.
        while(SystemClock.elapsedRealtime()-started<330000){SystemClock.sleep(5000);test.waitForIdleSync();}
        String log=(String)call(field(activity,"diagnostics"),"snapshot");check(log.contains("前台内存"),"real foreground memory timer records a sample");
        check((Integer)call(field(activity,"diagnostics"),"count")<=100,"journal remains bounded during repeated lifecycle changes");
        report.append("MEASURE stability elapsed_ms=").append(SystemClock.elapsedRealtime()-started).append('\n');
    }
}
