package cn.wengmang.qiping.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Rendering behavior: real category navigation changes the glass pigment, preserving focus. */
final class GlassThemeChecks {
    private final Instrumentation test;private final Activity activity;private final StringBuilder report;
    private GlassThemeChecks(Instrumentation t,Activity a,StringBuilder r){test=t;activity=a;report=r;}
    static void run(Instrumentation t,Activity a,StringBuilder r)throws Exception{new GlassThemeChecks(t,a,r).run();}
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);report.append("PASS glass: ").append(text).append('\n');}
    private View control(String key)throws Exception{return (View)((Map<?,?>)field(activity,"controls")).get(key);}
    private void focus(final String key)throws Exception{final View v=control(key);check(v!=null,"control "+key);test.runOnMainSync(new Runnable(){public void run(){v.requestFocus();}});test.waitForIdleSync();}
    private void waitForScene(final int index)throws Exception {
        final Object scene=field(activity,"scene");final boolean[] ready={false};
        long end=SystemClock.uptimeMillis()+12000;
        while(SystemClock.uptimeMillis()<end){test.runOnMainSync(new Runnable(){public void run(){try{ready[0]=(Integer)field(scene,"currentIndex")==index&&field(scene,"current")!=null&&(Float)field(scene,"blend")>=1f;}catch(Exception e){throw new RuntimeException(e);}}});if(ready[0])break;SystemClock.sleep(100);}
        check(ready[0],"wallpaper decoded for scene "+index);SystemClock.sleep(500);test.waitForIdleSync();
    }
    private int glyphPixel(final View view){final int[] color={0};test.runOnMainSync(new Runnable(){public void run(){Bitmap frame=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(frame));color[0]=frame.getPixel(frame.getWidth()/2,frame.getHeight()/2);frame.recycle();}});return color[0];}
    private void run()throws Exception {
        check(test.getTargetContext().getApplicationInfo().minSdkVersion==28,"installed package requires Android 9 / API 28");
        focus("dock0");waitForScene(0);int cool= glyphPixel(control("dock0"));
        for(int i=1;i<5;i++){TestKeys.press(test,KeyEvent.KEYCODE_DPAD_RIGHT);waitForScene(i);check(control("dock"+i).hasFocus(),"D-pad focus retained on category "+i);if(i==3){int warm=glyphPixel(control("dock0"));check(cool!=warm,"same play glyph changes pigment with wallpaper");check(Color.blue(cool)>Color.red(cool)&&Color.red(warm)>Color.blue(warm),"glass inherits cool coast and warm dune hues ("+Integer.toHexString(cool)+" / "+Integer.toHexString(warm)+")");}}
        focus("home_settings");TestKeys.press(test,KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(500);test.waitForIdleSync();
        Object dialog=field(activity,"currentDialog");final View all=(View)((Map<?,?>)field(dialog,"controls")).get("quick_all");
        check(all!=null,"glass quick settings available");test.runOnMainSync(new Runnable(){public void run(){all.requestFocus();all.performClick();}});waitForScene(5);
        check(control("setting0")!=null,"full settings uses dedicated mist wallpaper");
        TestKeys.press(test,KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);test.waitForIdleSync();
        check(field(activity,"currentDialog")!=null,"Back from full settings restores quick settings");
        TestKeys.press(test,KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);test.waitForIdleSync();
        check(field(activity,"currentDialog")==null,"Back closes quick settings before the next test");
        focus("dock0");waitForScene(0);
    }
}
