package cn.wengmang.qiping.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.graphics.Bitmap;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Disposable emulator only. Uses real Android sleep/wake shell keys, not fake broadcasts. */
final class WakeUiChecks {
    private final Instrumentation runner;private final Activity activity;private final StringBuilder report;
    private WakeUiChecks(Instrumentation r,Activity a,StringBuilder out){runner=r;activity=a;report=out;}
    static void run(Instrumentation r,Activity a,StringBuilder out)throws Exception{new WakeUiChecks(r,a,out).run();}
    private void check(boolean value,String text){if(!value)throw new AssertionError(text);report.append("PASS WAKE ").append(text).append('\n');}
    private Object get(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new RuntimeException(e);}}
    private void set(Object object,String name,Object value){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object object,String name,Class<?>[] types,Object... args){try{Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}catch(Exception e){throw new RuntimeException(e);}}
    private void ui(Runnable r){runner.runOnMainSync(r);SystemClock.sleep(250);runner.waitForIdleSync();}
    private void press(int key){TestKeys.press(runner,key);SystemClock.sleep(350);runner.waitForIdleSync();}
    private View view(String key){return (View)((Map<?,?>)get(activity,"controls")).get(key);}
    private void focus(final String key){ui(new Runnable(){public void run(){View v=view(key);if(v==null)throw new AssertionError("missing "+key);v.requestFocus();}});}
    private void render(final int screen,final String key){ui(new Runnable(){public void run(){call(activity,"dismissDialog",new Class<?>[0]);set(activity,"screen",screen);call(activity,"render",new Class<?>[]{String.class},key);}});}
    private boolean interactive(){return ((PowerManager)activity.getSystemService(Context.POWER_SERVICE)).isInteractive();}
    private void shell(String command)throws Exception{ParcelFileDescriptor fd=runner.getUiAutomation().executeShellCommand(command);try{InputStream in=new FileInputStream(fd.getFileDescriptor());byte[] buf=new byte[1024];while(in.read(buf)!=-1){}}finally{fd.close();}}
    private void power(boolean awake)throws Exception{
        shell("input keyevent "+(awake?224:223));long end=SystemClock.uptimeMillis()+15000;
        while(interactive()!=awake&&SystemClock.uptimeMillis()<end)SystemClock.sleep(100);
        check(interactive()==awake,awake?"actual Android interactive state wakes":"actual Android interactive state sleeps");
        if(!awake)SystemClock.sleep(800);
    }
    private void countdown()throws Exception{
        long end=SystemClock.uptimeMillis()+15000;boolean found=false;
        while(SystemClock.uptimeMillis()<end){final boolean[] yes={false};runner.runOnMainSync(new Runnable(){public void run(){yes[0]=get(activity,"startupTarget")!=null&&(Boolean)get(activity,"wakeCountdown");}});if(yes[0]){found=true;break;}SystemClock.sleep(100);}
        check(found,"visible HOME enters wake countdown");
    }
    private void homeRemains(String label){SystemClock.sleep(4200);runner.waitForIdleSync();check(activity.hasWindowFocus()&&(Boolean)get(activity,"resumed")&&get(activity,"startupTarget")==null,label);}
    private long sequence(){return (Long)call(get(activity,"wakeState"),"sequence",new Class<?>[0]);}
    private String status(){return (String)call(get(activity,"wakeState"),"status",new Class<?>[0]);}
    private void shot(String name)throws Exception{SystemClock.sleep(600);Bitmap bitmap=runner.getUiAutomation().takeScreenshot();File dir=new File(activity.getFilesDir(),"verification-018");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    private void run()throws Exception{
        final SharedPreferences desktop=activity.getSharedPreferences("desktop",0),startup=activity.getSharedPreferences("startup",0);
        final int font=desktop.getInt("font_size",0),margin=desktop.getInt("margin",1);final String binding=desktop.getString("category_1","");
        try{
            long ready=SystemClock.uptimeMillis()+15000;while((Boolean)get(activity,"loading")&&SystemClock.uptimeMillis()<ready)SystemClock.sleep(100);
            check(!(Boolean)get(activity,"loading"),"application scan completes before configuration");
            check((Boolean)call(activity,"isDefaultHome",new Class<?>[0]),"test launcher is the real default HOME");
            render(3,"startup3");focus("startup3");press(KeyEvent.KEYCODE_DPAD_DOWN);
            check(activity.getCurrentFocus()==view("startup_wake"),"Down reaches wake switch from second card row");
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            check(get(activity,"currentDialog")!=null&&!startup.getBoolean("wake_live_enabled",false),"unbound target prompts selection without enabling");
            press(KeyEvent.KEYCODE_BACK);
            ui(new Runnable(){public void run(){desktop.edit().putString("category_1","cn.wengmang.qiping.tests").commit();}});
            render(3,"startup_wake");focus("startup_wake");press(KeyEvent.KEYCODE_DPAD_CENTER);
            check(startup.getBoolean("wake_live_enabled",false),"remote enables independent wake option");
            check(!startup.getBoolean("live_enabled",false),"wake option preserves cold boot preference");
            check(get(activity,"startupTarget")==null,"enabling does not start an immediate countdown");
            shot("wake-settings");
            for(final int size:new int[]{1,2}){
                ui(new Runnable(){public void run(){desktop.edit().putInt("font_size",size).putInt("margin",2).commit();}});render(3,"startup_wake");
                for(String key:new String[]{"startup_wake","startup_status"}){View v=view(key);int[] p=new int[2];v.getLocationOnScreen(p);check(p[1]>=0&&p[1]+v.getHeight()<=activity.getWindow().getDecorView().getHeight(),"large font "+size+" keeps "+key+" inside display");}
                focus("startup_wake");press(KeyEvent.KEYCODE_DPAD_DOWN);check(activity.getCurrentFocus()==view("startup_status"),"Down reaches diagnostic footer at font "+size);
                if(size==2)shot("wake-settings-large");
            }
            ui(new Runnable(){public void run(){desktop.edit().putInt("font_size",0).putInt("margin",1).commit();}});render(0,"dock1");
            final long before=sequence();power(false);power(true);countdown();
            TestKeys.awaitPackage(runner,"cn.wengmang.qiping.tests");check(!(Boolean)get(activity,"resumed"),"real sleep/wake opens bound application");
            check(sequence()==before+1,"one sleep/wake pair increments sequence once");
            power(false);power(true);TestKeys.awaitPackage(runner,"cn.wengmang.qiping.tests");
            check(sequence()==before+1,"sleep/wake inside live keeps that app and does not arm HOME relaunch");
            press(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(runner,"cn.wengmang.qiping");homeRemains("Back from live stays on desktop without relaunch");
            check(status().contains("保持原应用"),"external-app wake decision appears in diagnostics");
            ui(new Runnable(){public void run(){activity.startActivity(new Intent().setClassName("cn.wengmang.qiping.tests","cn.wengmang.qiping.tests.ProbeActivity"));}});
            TestKeys.awaitPackage(runner,"cn.wengmang.qiping.tests");press(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(runner,"cn.wengmang.qiping");homeRemains("ordinary app round trip is not a new wake");
            check(sequence()==before+1,"ordinary resume leaves wake sequence unchanged");
            power(false);power(true);countdown();press(KeyEvent.KEYCODE_BACK);homeRemains("Back during wake countdown cancels only this attempt");
            check(status().contains("已取消"),"cancellation is diagnosed");
            power(false);power(true);countdown();TestKeys.awaitPackage(runner,"cn.wengmang.qiping.tests");
            check(sequence()==before+3,"next remote power cycle works after cancellation");press(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(runner,"cn.wengmang.qiping");
            render(3,"startup_wake");focus("startup_wake");press(KeyEvent.KEYCODE_DPAD_CENTER);
            check(!startup.getBoolean("wake_live_enabled",true),"remote disables wake option");render(0,"dock1");
            long disabled=sequence();power(false);power(true);homeRemains("disabled wake option keeps HOME visible");check(sequence()==disabled,"disabled mode does not arm a launch");
        }finally{
            if(!interactive())power(true);
            ui(new Runnable(){public void run(){startup.edit().putBoolean("wake_live_enabled",false).putBoolean("wake_armed",false).putBoolean("wake_pending",false).commit();desktop.edit().putInt("font_size",font).putInt("margin",margin).putString("category_1",binding).commit();}});
        }
    }
}
