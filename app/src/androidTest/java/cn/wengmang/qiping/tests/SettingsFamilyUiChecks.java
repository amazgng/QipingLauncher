package cn.wengmang.qiping.tests;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

/** Disposable Android 9 emulator only: real keypad, local rules and external app boundaries. */
final class SettingsFamilyUiChecks {
    private final Instrumentation runner;private final Activity a;private final StringBuilder report;
    private SettingsFamilyUiChecks(Instrumentation r,Activity a,StringBuilder out){runner=r;this.a=a;report=out;}
    static void run(Instrumentation r,Activity a,StringBuilder out)throws Exception{new SettingsFamilyUiChecks(r,a,out).run();}
    static void maintenance(Instrumentation r,Activity a,StringBuilder out)throws Exception{new SettingsFamilyUiChecks(r,a,out).runMaintenance();}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);report.append("PASS FAMILY ").append(text).append('\n');}
    private Object get(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    private void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    private void ui(Runnable r){runner.runOnMainSync(r);SystemClock.sleep(220);runner.waitForIdleSync();}
    private void key(int code){TestKeys.press(runner,code);SystemClock.sleep(220);runner.waitForIdleSync();}
    private Dialog dialog(){return (Dialog)get(a,"currentDialog");}
    private View root(){Dialog d=dialog();return d==null?a.getWindow().getDecorView():d.getWindow().getDecorView();}
    private View find(View root,String label){String d=String.valueOf(root.getContentDescription());if(d.equals(label)||d.equals(label+"  ✓")||d.startsWith(label+"，"))return root;if(root instanceof ViewGroup){ViewGroup v=(ViewGroup)root;for(int i=0;i<v.getChildCount();i++){View found=find(v.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private View find(String label){return find(root(),label);}
    private String texts(View root){StringBuilder s=new StringBuilder();if(root instanceof TextView)s.append(((TextView)root).getText()).append('\n');if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++)s.append(texts(g.getChildAt(i)));}return s.toString();}
    private void choose(final String label){final View v=find(label);if(v==null)throw new AssertionError("Missing control "+label);ui(new Runnable(){public void run(){v.requestFocus();}});key(KeyEvent.KEYCODE_DPAD_CENTER);}
    private View control(String key){return (View)((Map<?,?>)get(a,"controls")).get(key);}
    private void open(final String key){final View v=control(key);if(v==null)throw new AssertionError("Missing key "+key);ui(new Runnable(){public void run(){v.requestFocus();}});key(KeyEvent.KEYCODE_DPAD_CENTER);}
    private void render(final int screen,final String key){ui(new Runnable(){public void run(){call(a,"dismissDialog",new Class<?>[0]);set(a,"screen",screen);call(a,"render",new Class<?>[]{String.class},key);}});}
    private void waitText(String value)throws Exception{long end=SystemClock.uptimeMillis()+90000;while(SystemClock.uptimeMillis()<end){final boolean[] found={false};runner.runOnMainSync(new Runnable(){public void run(){found[0]=texts(root()).contains(value);}});if(found[0])return;SystemClock.sleep(100);}throw new AssertionError("UI did not reach "+value);}
    private void input(String digits)throws Exception{for(int i=0;i<digits.length();i++)choose(digits.substring(i,i+1));choose("确定");}
    private void auth()throws Exception{input("628417");waitText("未成年模式 · 家长设置");}
    private void shot(String name)throws Exception{SystemClock.sleep(350);Bitmap b=runner.getUiAutomation().takeScreenshot();File d=new File(a.getFilesDir(),"verification-019");d.mkdirs();try(FileOutputStream f=new FileOutputStream(new File(d,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}finally{b.recycle();}}
    private void shell(String command)throws Exception{ParcelFileDescriptor fd=runner.getUiAutomation().executeShellCommand(command);try{InputStream in=new FileInputStream(fd.getFileDescriptor());byte[] buf=new byte[1024];while(in.read(buf)!=-1){}}finally{fd.close();}}
    private byte[] read(InputStream in)throws Exception{try{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toByteArray();}finally{in.close();}}
    private void runMaintenance()throws Exception{
        final SharedPreferences parent=a.getSharedPreferences("parental_control",0);
        final Object store=get(a,"parental");
        final File sentinel=new File(a.getFilesDir(),"maintenance-sentinel.txt");
        final Uri exported=Uri.parse("content://cn.wengmang.qiping.tests.export/backup");
        File backup=null,fallback=null;
        try{
            parent.edit().clear().commit();
            Method prepare=store.getClass().getDeclaredMethod("prepare",String.class);prepare.setAccessible(true);
            Object credentials=prepare.invoke(null,"628417");
            check(Boolean.TRUE.equals(call(store,"saveCredentials",new Class<?>[]{credentials.getClass()},credentials)),"maintenance credentials saved with Android preferences");
            Class<?>[] authTypes={String.class,boolean.class,long.class,long.class,String.class};
            long wall=System.currentTimeMillis();
            for(int i=0;i<5;i++)call(store,"authenticate",authTypes,"000000",false,wall,100000L,"maintenance-old");
            check(Integer.valueOf(2).equals(call(store,"authenticate",authTypes,"628417",false,wall-86400000,500L,"maintenance-new")),"clock rollback plus reboot retains initial lock");
            check(Long.valueOf(20000).equals(call(store,"remaining",new Class<?>[]{long.class,long.class,String.class},wall-86400000,10500L,"maintenance-new")),"Android persisted cooldown decreases with monotonic time");
            check(Integer.valueOf(0).equals(call(store,"authenticate",authTypes,"628417",false,wall-86400000,30500L,"maintenance-new")),"Android stored lock expires after bounded 30 seconds");

            ui(new Runnable(){public void run(){
                parent.edit().putBoolean("enabled",true).putBoolean("schedule",true).putString("allowed","cn.wengmang.qiping.tests").putString("start","invalid").putString("end","invalid").commit();
                call(a,"endParentSession",new Class<?>[0]);
            }});
            render(0,"child_parent");
            check(control("app0")==null&&texts(root()).contains("时间设置异常"),"damaged rule hides app entries and explains repair");
            open("child_parent");auth();choose("允许打开的时段：时间设置异常，请家长重新设置");
            check(texts(root()).contains("重新设置开始和结束时间"),"parent schedule page explains invalid rule");
            choose("开始时间：时间无效");input("2100");waitText("重新设置结束时间");
            check(parent.getAll().get("start") instanceof String&&parent.getAll().get("end") instanceof String,"first repair step never saves half a window");
            key(KeyEvent.KEYCODE_BACK);waitText("重新设置开始时间");key(KeyEvent.KEYCODE_BACK);
            check(Boolean.FALSE.equals(call(store,"validSchedule",new Class<?>[0])),"canceling repair preserves closed gate");
            choose("结束时间：时间无效");input("2100");waitText("重新设置结束时间");
            input("2100");check(texts(root()).contains("开始与结束不能相同"),"repair rejects zero-length window");
            input("2200");waitText("开始时间：21:00");
            check(parent.getInt("start",-1)==1260&&parent.getInt("end",-1)==1320&&parent.getBoolean("schedule",false),"remote repair saves both times and keeps schedule enabled");
            check(Boolean.TRUE.equals(call(store,"validSchedule",new Class<?>[0])),"repaired Android rule validates");
            ui(new Runnable(){public void run(){call(a,"endParentSession",new Class<?>[0]);parent.edit().putBoolean("enabled",false).commit();}});
            render(0,"dock0");

            backup=(File)call(get(a,"configStore"),"backup",new Class<?>[0]);
            final String name=backup.getName();byte[] expected=read(new FileInputStream(backup));
            try(FileOutputStream out=new FileOutputStream(sentinel)){out.write("KEEP_ORIGINAL".getBytes("UTF-8"));}
            for(final Uri bad:new Uri[]{Uri.fromFile(sentinel),Uri.parse("file:///data/data/cn.wengmang.qiping/../cn.wengmang.qiping/files/maintenance-sentinel.txt"),Uri.parse("https://example.invalid/backup"),Uri.parse("content:/no-authority"),Uri.parse("content:opaque")}){
                ui(new Runnable(){public void run(){set(a,"pendingBackupName",name);call(a,"onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},701,Activity.RESULT_OK,new Intent().setData(bad));}});
                waitText("请选择系统文件保存位置");
                check("KEEP_ORIGINAL".equals(new String(read(new FileInputStream(sentinel)),"UTF-8")),"external export rejects "+bad.getScheme()+" destination without writing private file");
            }
            ui(new Runnable(){public void run(){set(a,"pendingBackupName",name);call(a,"onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},701,Activity.RESULT_OK,new Intent().setData(exported));}});
            waitText("备份文件已导出");
            check(Arrays.equals(expected,read(a.getContentResolver().openInputStream(exported))),"valid content provider export preserves exact backup bytes");
            final Uri local=Uri.fromFile(backup);
            ui(new Runnable(){public void run(){call(a,"readConfig",new Class<?>[]{Uri.class},local);}});
            waitText("预览恢复内容");check(texts(root()).contains("备份时间"),"trusted internal file backup still opens restore preview");
            key(KeyEvent.KEYCODE_BACK);
            final File selected=backup;
            ui(new Runnable(){public void run(){call(a,"exportConfigDirectory",new Class<?>[]{File.class},selected);}});
            waitText("已导出到本机目录");
            fallback=new File(new File(a.getExternalFilesDir(null),"backups"),backup.getName());
            check(Arrays.equals(expected,read(new FileInputStream(fallback))),"no-picker fallback writes only fixed app backup directory");
        }finally{
            ui(new Runnable(){public void run(){call(a,"endParentSession",new Class<?>[0]);parent.edit().clear().commit();}});
            sentinel.delete();if(backup!=null)backup.delete();if(fallback!=null)fallback.delete();a.getContentResolver().delete(exported,null,null);
            render(0,"dock0");
        }
    }
    private void run()throws Exception{
        final SharedPreferences desktop=a.getSharedPreferences("desktop",0),parent=a.getSharedPreferences("parental_control",0),startup=a.getSharedPreferences("startup",0);
        int originalFont=desktop.getInt("font_size",0),originalMargin=desktop.getInt("margin",1);String originalBinding=desktop.getString("category_1","");boolean oldMotion=desktop.getBoolean("reduce_motion",false);
        long ready=SystemClock.uptimeMillis()+30000;while((Boolean)get(a,"loading")&&SystemClock.uptimeMillis()<ready)SystemClock.sleep(100);
        check(!(Boolean)get(a,"loading"),"installed app scan is ready");
        try{
            render(0,"home_settings");open("home_settings");check(find("个性化")==null&&find("显示字号")==null&&find("界面动效")==null,"quick panel no longer duplicates appearance controls");
            choose("全部设置");
            String[] names={"开机与桌面","家庭与配置","网络设置","系统设置","其他桌面","设备与性能","应用更新","个性设置","使用说明"};
            for(int i=0;i<9;i++)check(names[i].equals(String.valueOf(control("setting"+i).getContentDescription())),"settings card "+i+" is "+names[i]);
            check(control("settings_personal")==null&&control("settings_family")==null&&control("settings_updates")==null&&!texts(root()).contains("第一版常用"),"old toolbar/footer and legacy favorites entry removed");
            open("setting7");check(dialog()!=null&&texts(root()).contains("个性设置"),"original favorites slot opens consolidated settings");
            choose("界面动效");check(desktop.getBoolean("reduce_motion",false),"motion switch updates existing preference");choose("界面动效");
            choose("屏幕边距");choose("较大 · 7%");key(KeyEvent.KEYCODE_BACK);check(texts(root()).contains("个性设置")&&desktop.getInt("margin",0)==2,"margin returns to consolidated parent");
            choose("显示字号");choose("特大");key(KeyEvent.KEYCODE_BACK);check(desktop.getInt("font_size",0)==2,"font updates and returns to consolidated parent");
            for(String label:new String[]{"首页排列与隐藏","全部应用整理","桌面空闲屏保","本地壁纸"}){choose(label);check(dialog()!=null,"existing destination opens: "+label);key(KeyEvent.KEYCODE_BACK);check(texts(root()).contains("个性设置"),"existing destination returns to consolidated parent: "+label);}
            shot("personal-large");key(KeyEvent.KEYCODE_BACK);check(dialog()==null&&a.getCurrentFocus()==control("setting7"),"Back restores original card focus");shot("settings-large");
            ui(new Runnable(){public void run(){desktop.edit().putInt("font_size",0).putInt("margin",1).commit();}});render(2,"setting1");open("setting1");
            check(find("未成年模式")!=null&&find("桌面配置")!=null&&find("长辈布局")!=null,"family card contains existing features plus minor mode");
            choose("未成年模式");choose("设置家长密码");key(KeyEvent.KEYCODE_BACK);check(!parent.contains("pin_hash"),"canceling setup does not create credentials");
            choose("设置家长密码");
            // Real D-pad movement inside the keypad; no IME or touch injection.
            key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_DOWN);check("5".equals(String.valueOf(dialog().getCurrentFocus().getContentDescription())),"numeric pad supports predictable two-axis navigation");
            input("628417");waitText("再次输入密码");input("628417");waitText("请保存家长恢复码");
            final String[] recovered={null};ui(new Runnable(){public void run(){Matcher m=Pattern.compile("([0-9]{4})  ([0-9]{4})  ([0-9]{4})").matcher(texts(root()));if(m.find())recovered[0]=m.group(1)+m.group(2)+m.group(3);}});
            check(recovered[0]!=null,"recovery code shown for parent confirmation");choose("已保存，验证恢复码");input(recovered[0]);waitText("未成年模式 · 家长设置");
            check(!parent.getBoolean("enabled",false)&&parent.contains("pin_hash"),"setup saves credentials but leaves mode disabled");
            check(!parent.getAll().toString().contains("628417")&&!parent.getAll().toString().contains(recovered[0]),"credentials are not saved as plaintext");
            choose("未成年模式：已关闭");check(texts(root()).contains("先选择允许的应用"),"empty allowlist prevents enable");key(KeyEvent.KEYCODE_BACK);
            choose("允许的应用（0 个）");choose("○  Qiping Test App");check(parent.getString("allowed","").equals("cn.wengmang.qiping.tests"),"remote selects an installed allowed application");key(KeyEvent.KEYCODE_BACK);
            choose("允许打开的时段：全天可打开");choose("开始时间：19:00");input("2460");check(texts(root()).contains("请输入有效时间"),"invalid time is rejected by entry UI");input("1930");waitText("开始时间：19:30");key(KeyEvent.KEYCODE_BACK);
            choose("未成年模式：已关闭");choose("开启模式");check(parent.getBoolean("enabled",false)&&control("child_parent")!=null,"enable enters child home");
            check(((List<?>)get(a,"gridApps")).size()==1&&control("app0")!=null&&control("home_settings")==null&&control("home_network")==null,"child home exposes only approved app and parent gate");shot("child-home");
            key(KeyEvent.KEYCODE_BACK);check(!a.isFinishing()&&parent.getBoolean("enabled",false),"Back never exits mode");key(KeyEvent.KEYCODE_MENU);check(dialog()!=null&&texts(root()).contains("家长验证"),"Menu key cannot open normal settings without authentication");
            input("000000");waitText("输入不正确");check(!(Boolean)get(a,"parentAuthorized"),"incorrect PIN does not authorize");key(KeyEvent.KEYCODE_BACK);
            open("app0");TestKeys.awaitPackage(runner,"cn.wengmang.qiping.tests");check(!(Boolean)get(a,"resumed"),"approved app opens normally");key(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(runner,"cn.wengmang.qiping");check(control("child_parent")!=null&&parent.getBoolean("enabled",false),"return from application retains child home");
            final boolean[] rejected={false};ui(new Runnable(){public void run(){try{a.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}catch(SecurityException expected){rejected[0]=true;}}});check(rejected[0],"central launch gate rejects a system settings intent");
            // Expired window: launch permission is checked at use time, not just when drawing.
            final Calendar now=Calendar.getInstance();final int minute=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);final int start=(minute+60)%1440,end=(minute+120)%1440;
            ui(new Runnable(){public void run(){parent.edit().putBoolean("schedule",true).putInt("start",start).putInt("end",end).commit();call(a,"updateChildHome",new Class<?>[0]);}});
            check(control("app0")==null&&texts(root()).contains("现在是休息时间"),"closed window changes to rest screen");shot("child-rest");
            ui(new Runnable(){public void run(){Object entry=call(a,"byPackage",new Class<?>[]{String.class},"cn.wengmang.qiping.tests");Object launched=call(a,"launch",new Class<?>[]{entry.getClass()},entry);rejected[0]=Boolean.FALSE.equals(launched);}});check(rejected[0]&&(Boolean)get(a,"resumed"),"stale app reference cannot bypass the time gate");
            // Auto startup records a skip instead of queueing a launch until hours reopen.
            ui(new Runnable(){public void run(){desktop.edit().putString("category_1","cn.wengmang.qiping.tests").commit();startup.edit().putBoolean("live_enabled",true).putString("handled_boot","count:old").commit();call(a,"checkStartup",new Class<?>[0]);}});
            check(startup.getString("status","").contains("未成年模式")&&get(a,"startupTarget")==null,"cold startup respects parental restriction");
            ui(new Runnable(){public void run(){Object wake=get(a,"wakeState");call(wake,"setEnabled",new Class<?>[]{boolean.class,long.class},true,SystemClock.elapsedRealtime());Class<?> boot;try{boot=Class.forName("cn.wengmang.qiping.BootSession");Method m=boot.getDeclaredMethod("current",Context.class);m.setAccessible(true);String token=(String)m.invoke(null,a);call(wake,"screenOff",new Class<?>[]{String.class,long.class},token,SystemClock.elapsedRealtime());call(wake,"screenOn",new Class<?>[]{String.class,long.class},token,SystemClock.elapsedRealtime()+1);}catch(Exception e){throw new RuntimeException(e);}call(a,"checkStartup",new Class<?>[0]);}});
            check(startup.getString("wake_status","").contains("未成年模式")&&get(a,"startupTarget")==null,"wake startup respects the same restriction");
            open("child_parent");auth();check((Boolean)get(a,"parentAuthorized"),"correct PIN grants a local parent settings session");
            // Ordinary configuration storage has no access to the separate parental preference file.
            ui(new Runnable(){public void run(){try{Object store=get(a,"configStore");call(store,"backup",new Class<?>[0]);}catch(RuntimeException e){throw e;}}});check(parent.getBoolean("enabled",false)&&parent.contains("pin_hash"),"desktop backup preserves parental state");
            key(KeyEvent.KEYCODE_BACK);open("child_parent");check(texts(root()).contains("家长验证"),"leaving parent settings discards authorization");
            choose("忘记密码 · 使用恢复码");input(recovered[0]);waitText("设置家长密码");key(KeyEvent.KEYCODE_BACK);waitText("未成年模式 · 家长设置");check((Boolean)get(a,"parentAuthorized"),"recovery code can authorize password recovery");
            choose("未成年模式：已开启");choose("退出模式");check(!parent.getBoolean("enabled",true)&&control("dock0")!=null,"parent exit restores standard home");
            check(parent.contains("pin_hash")&&parent.getString("allowed","").contains("cn.wengmang.qiping.tests"),"exit retains reusable rules and credential");
            ui(new Runnable(){public void run(){desktop.edit().putInt("font_size",2).putInt("margin",2).commit();}});render(2,"setting1");open("setting1");choose("未成年模式");
            check(dialog().getWindow().getDecorView().getHeight()<=a.getWindow().getDecorView().getHeight()*.88f+2,"PIN dialog fits maximum font and margin");
            key(KeyEvent.KEYCODE_BACK);
        }finally{
            ui(new Runnable(){public void run(){call(a,"endParentSession",new Class<?>[0]);parent.edit().clear().commit();startup.edit().putBoolean("live_enabled",false).putBoolean("wake_live_enabled",false).putBoolean("wake_pending",false).putBoolean("wake_armed",false).commit();desktop.edit().putInt("font_size",originalFont).putInt("margin",originalMargin).putBoolean("reduce_motion",oldMotion).putString("category_1",originalBinding).commit();}});
            render(0,"dock0");
        }
    }
}
