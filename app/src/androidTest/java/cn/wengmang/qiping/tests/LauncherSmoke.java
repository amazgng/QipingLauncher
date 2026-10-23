package cn.wengmang.qiping.tests;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.Map;

/** Explicitly run on a test emulator: clears category bindings, never external applications. */
public final class LauncherSmoke extends Instrumentation {
    private Activity activity;
    private String suite="settings-family";
    private final StringBuilder report=new StringBuilder();
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null)suite=args.getString("suite","settings-family");start();}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);report.append("PASS ").append(text).append('\n');}
    private void settle(){SystemClock.sleep(420);waitForIdleSync();}
    private View find(View root,String description) {
        String actual=String.valueOf(root.getContentDescription());
        if(description.equals(actual)||(description+"  ✓").equals(actual)||actual.startsWith(description+"，"))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++) {
            View found=find(((ViewGroup)root).getChildAt(i),description);if(found!=null)return found;
        }
        return null;
    }
    private Dialog activeDialog(){try{Object value=field(activity,"currentDialog");return value instanceof Dialog&&((Dialog)value).isShowing()?(Dialog)value:null;}catch(Exception error){throw new AssertionError(error);}}
    private View find(final String label){final View[] value=new View[1];runOnMainSync(new Runnable(){public void run(){Dialog dialog=activeDialog();value[0]=find(dialog==null?activity.getWindow().getDecorView():dialog.getWindow().getDecorView(),label);}});return value[0];}
    private String focused(){final String[] value=new String[1];runOnMainSync(new Runnable(){public void run(){Dialog dialog=activeDialog();View v=dialog==null?activity.getCurrentFocus():dialog.getCurrentFocus();value[0]=v==null?"":String.valueOf(v.getContentDescription());}});return value[0];}
    private void focus(final String label){final View v=find(label);check(v!=null,"control exists: "+label);runOnMainSync(new Runnable(){public void run(){v.requestFocus();}});settle();}
    private void press(int key){TestKeys.press(this,key);settle();}
    private void open(String label){focus(label);press(KeyEvent.KEYCODE_DPAD_CENTER);}
    private void openKey(final String key)throws Exception {
        final View view=(View)((Map<?,?>)field(activity,"controls")).get(key);
        check(view!=null,"control exists: "+key);
        runOnMainSync(new Runnable(){public void run(){view.requestFocus();view.performClick();}});settle();
    }
    private void findAcrossPages(String label)throws Exception {
        for(int attempt=0;find(label)==null&&attempt<100;attempt++) {
            int page=(Integer)field(activity,"page");
            final View edge=(View)((Map<?,?>)field(activity,"controls")).get("app"+(page*12+5));
            if(edge==null)break;
            runOnMainSync(new Runnable(){public void run(){edge.requestFocus();}});
            press(KeyEvent.KEYCODE_DPAD_RIGHT);if(page==(Integer)field(activity,"page"))break;
        }
    }
    private Object field(final Object object,final String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void home(){press(KeyEvent.KEYCODE_BACK);if(activeDialog()!=null)press(KeyEvent.KEYCODE_BACK);check(activeDialog()==null&&find("影视")!=null,"Back returns to home");}
    private void openFullSettings(){open("桌面设置");open("全部设置");}
    private boolean hasCaptionInDock(View root,int height) {
        if(root instanceof TextView) {
            String t=((TextView)root).getText().toString();int[] xy=new int[2];root.getLocationOnScreen(xy);
            if(xy[1]>height*.7&&(t.equals("影视")||t.equals("直播")||t.equals("音乐")||t.equals("相册")||t.equals("全部应用")))return true;
        }
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)if(hasCaptionInDock(((ViewGroup)root).getChildAt(i),height))return true;
        return false;
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            if("settings-family".equals(suite)||"all".equals(suite))getTargetContext().getSharedPreferences("parental_control",0).edit().clear().commit();
            SharedPreferences prefs=getTargetContext().getSharedPreferences("desktop",0);
            SharedPreferences startup=getTargetContext().getSharedPreferences("startup",0);
            startup.edit().putBoolean("live_enabled",false).putBoolean("wake_live_enabled",false).putBoolean("wake_pending",false).putBoolean("wake_armed",false).commit();
            SharedPreferences.Editor reset=prefs.edit().putInt("selected_category",0).putBoolean("reduce_motion",false).putInt("performance_mode",0);
            for(int i=0;i<4;i++)reset.remove("category_"+i);reset.commit();
            Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName("cn.wengmang.qiping","cn.wengmang.qiping.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(launch);TestKeys.target=activity;settle();SystemClock.sleep(1200);waitForIdleSync();
            check(find("影视")!=null&&find("相册")!=null,"five-category home starts on API "+android.os.Build.VERSION.SDK_INT);
            if("maintenance".equals(suite)){SettingsFamilyUiChecks.maintenance(this,activity,report);result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);return;}
            if("settings-family".equals(suite)||"all".equals(suite)){SettingsFamilyUiChecks.run(this,activity,report);result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);return;}
            if("wake".equals(suite)){WakeUiChecks.run(this,activity,report);result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);return;}
            if("crash".equals(suite)){new Thread(new Runnable(){public void run(){throw new IllegalStateException("PRIVATE_IMAGE_TOKEN_MUST_NOT_APPEAR");}},"qiping-test-crash").start();SystemClock.sleep(3000);throw new AssertionError("expected Android crash handler to terminate test process");}
            if("polish".equals(suite)){PolishUiChecks.run(this,activity,report);result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);return;}
            if("soak".equals(suite)){PolishUiChecks.soak(this,activity,report);result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);return;}
            if("phone".equals(suite)){PhoneWallpaperUiChecks.run(this,activity,report);result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);return;}
            if("round2".equals(suite)){RoundTwoUiChecks.run(this,activity,report);result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);return;}
            if(!"regression".equals(suite)){PersonalizationUiChecks.run(this,activity,report);Bundle stage=new Bundle();stage.putString("stream","Personalization checks completed\n");sendStatus(0,stage);}
            if("personal".equals(suite)){result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);return;}
            GlassThemeChecks.run(this,activity,report);
            RoundOneUiChecks.run(this,activity,report);
            RemoteUiChecks.run(this,activity,report);
            RoundTwoUiChecks.run(this,activity,report);
            RoundThreeUiChecks.run(this,activity,report);
            RoundFourUiChecks.run(this,activity,report);
            final boolean[] captions=new boolean[1];runOnMainSync(new Runnable(){public void run(){View root=activity.getWindow().getDecorView();captions[0]=hasCaptionInDock(root,root.getHeight());}});
            check(!captions[0],"dock contains no visible app-name captions");
            focus("影视");press(KeyEvent.KEYCODE_DPAD_RIGHT);check("直播".equals(focused()),"D-pad moves to Live TV");
            press(KeyEvent.KEYCODE_DPAD_RIGHT);check("音乐".equals(focused()),"D-pad moves to Music");
            press(KeyEvent.KEYCODE_DPAD_RIGHT);check("相册".equals(focused()),"D-pad moves to Album");
            SystemClock.sleep(800);check(((Integer)field(activity,"selected"))==3,"hero tracks selected category");
            Object scene=field(activity,"scene");check(((Integer)field(scene,"currentIndex"))==3,"wallpaper matches selected category");
            press(KeyEvent.KEYCODE_DPAD_UP);check("桌面设置".equals(focused()),"Up reaches settings");
            press(KeyEvent.KEYCODE_DPAD_DOWN);check("相册".equals(focused()),"Down restores previous dock focus");
            press(KeyEvent.KEYCODE_DPAD_DOWN);check(find("刷新应用")!=null,"Down opens all applications");home();
            check("相册".equals(focused()),"Back restores selected category");
            open("影视");check(find("刷新应用")!=null,"unbound category opens application picker");
            // The test package exposes a deterministic launchable probe activity.
            findAcrossPages("Qiping Test App");
            open("Qiping Test App");check(find("影视")!=null,"binding returns to home");
            check("cn.wengmang.qiping.tests".equals(prefs.getString("category_0","")),"category binding is persisted");
            open("影视");TestKeys.awaitPackage(this,"cn.wengmang.qiping.tests");check(!activity.hasWindowFocus(),"bound application launches");press(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(this,"cn.wengmang.qiping");
            check(activity.hasWindowFocus(),"returning app restores launcher");
            focus("相册");
            for(int i=0;i<8;i++){TestKeys.press(this,KeyEvent.KEYCODE_DPAD_LEFT);TestKeys.press(this,KeyEvent.KEYCODE_DPAD_RIGHT);}
            settle();SystemClock.sleep(900);check(((Integer)field(field(activity,"scene"),"currentIndex"))==((Integer)field(activity,"selected")).intValue(),"rapid navigation keeps final wallpaper in sync");
            openFullSettings();
            int oldMargin=prefs.getInt("margin",1);String[] margins={"较小 · 3%","标准 · 5%","较大 · 7%"};
            open("屏幕边距");check(focused().startsWith(margins[oldMargin]),"margin dialog focuses saved choice");
            press(KeyEvent.KEYCODE_BACK);check(prefs.getInt("margin",1)==oldMargin,"Back cancels without changing margin");
            check("屏幕边距".equals(focused()),"dialog dismissal restores preference focus");
            int nextMargin=(oldMargin+1)%3;open("屏幕边距");open(margins[nextMargin]);
            check(prefs.getInt("margin",1)==nextMargin&&"屏幕边距".equals(focused()),"choice applies and preserves focus after layout rebuild");
            open("屏幕边距");open(margins[oldMargin]);
            int oldFont=prefs.getInt("font_size",prefs.getBoolean("large_text",false)?1:0);String[] fonts={"标准","大号","特大"};
            open("显示字号");open(fonts[(oldFont+1)%3]);press(KeyEvent.KEYCODE_BACK);
            check(prefs.getInt("font_size",-1)==(oldFont+1)%3&&"显示字号".equals(focused()),"font choice persists and keeps focus");
            open("设备与性能");check(find("刷新设备信息")!=null,"device screen exposes refresh");
            openKey("device1");check(activeDialog()!=null,"Android version details are available");press(KeyEvent.KEYCODE_BACK);
            openKey("device5");open("轻量模式 · 减少图片缓存");
            check(prefs.getInt("performance_mode",0)==1,"light preference persists");
            check((Boolean)field(field(activity,"performance"),"light"),"manual light policy is effective on this device");
            open("刷新设备信息");check(prefs.getInt("performance_mode",0)==1,"refresh preserves chosen mode");
            open("使用说明");
            android.widget.ScrollView description=(android.widget.ScrollView)field(activeDialog(),"bodyScroll");
            press(KeyEvent.KEYCODE_DPAD_DOWN);check(description.getScrollY()>0,"long help content scrolls with Down");
            press(KeyEvent.KEYCODE_DPAD_RIGHT);check("知道了".equals(focused()),"Right reaches long-help action without touching");
            press(KeyEvent.KEYCODE_DPAD_CENTER);check("使用说明".equals(focused()),"usage dismissal restores focus");
            press(KeyEvent.KEYCODE_BACK);check("设备与性能".equals(focused()),"device Back restores settings tile");
            open("显示字号");open(fonts[oldFont]);press(KeyEvent.KEYCODE_BACK);check(prefs.getInt("font_size",-1)==oldFont,"font choice restores original preference");
            open("开机与桌面");check(find("默认桌面")!=null&&find("立即试播")!=null,"startup settings expose configuration and manual test");
            open("启动状态");check(activeDialog()!=null,"startup diagnostic is accessible with remote selection");
            press(KeyEvent.KEYCODE_BACK);check("启动状态".equals(focused()),"diagnostic closes and restores focus");
            open("开机自动打开直播");check(activeDialog()!=null,"missing live target prompts selection");
            press(KeyEvent.KEYCODE_BACK);check(!startup.getBoolean("live_enabled",false),"cancel does not enable unconfigured startup");
            open("直播应用");press(KeyEvent.KEYCODE_BACK);
            check("".equals(prefs.getString("category_1",""))&&"直播应用".equals(focused()),"picker cancel preserves binding and focus");
            open("直播应用");
            findAcrossPages("Qiping Test App");
            open("Qiping Test App");check("直播应用".equals(focused()),"live picker returns to startup settings");
            check("cn.wengmang.qiping.tests".equals(prefs.getString("category_1","")),"startup shares home live binding");
            open("开机自动打开直播");check(startup.getBoolean("live_enabled",false),"startup enable persists");
            check(startup.getString("handled_boot","").length()>0,"enabling consumes current boot");
            SystemClock.sleep(3500);check(activity.hasWindowFocus(),"enabling does not immediately open target");
            open("立即试播");TestKeys.awaitPackage(this,"cn.wengmang.qiping.tests");
            check(!activity.hasWindowFocus(),"manual live test launches selected application");
            SystemClock.sleep(900);waitForIdleSync();
            final Object hiddenScene=field(activity,"scene");
            check(!(Boolean)field(hiddenScene,"active"),"hidden launcher has paused background work");
            check(field(hiddenScene,"current")==null&&((Map<?,?>)field(hiddenScene,"cache")).isEmpty(),"hidden launcher releases wallpaper references");
            check(((Map<?,?>)field(activity,"icons")).isEmpty(),"hidden launcher drops icon cache");
            press(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(this,"cn.wengmang.qiping");check(activity.hasWindowFocus()&&find("直播应用")!=null,"test returns to startup settings without relaunch");
            open("开机自动打开直播");check(!startup.getBoolean("live_enabled",false),"startup can be disabled");
            press(KeyEvent.KEYCODE_BACK);check("开机与桌面".equals(focused()),"startup Back restores settings tile focus");
            open("界面动效");home();
            check(prefs.getBoolean("reduce_motion",false),"reduced motion is saved");
            openFullSettings();open("界面动效");home();
            focus("音乐");final Activity old=activity;runOnMainSync(new Runnable(){public void run(){old.finish();}});settle();
            activity=startActivitySync(launch);TestKeys.target=activity;settle();SystemClock.sleep(900);check("音乐".equals(focused()),"selected category survives restart");
            check("cn.wengmang.qiping.tests".equals(prefs.getString("category_0","")),"binding survives restart");
            check((Boolean)field(field(activity,"performance"),"light"),"performance preference survives restart");
            openFullSettings();open("设备与性能");openKey("device5");open("自动适配（推荐）");
            check(prefs.getInt("performance_mode",-1)==0,"auto mode can be restored");
            result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);
        } catch(Throwable error){java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream",report.toString()+"FAIL "+trace.toString());finish(Activity.RESULT_CANCELED,result);}
    }
}
