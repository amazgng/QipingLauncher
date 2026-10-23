package cn.wengmang.qiping.tests;

import android.app.Instrumentation;
import android.os.Build;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;

/** Instrumentation's ordinary injector is limited to the target UID. UI automation also
 * permits the test to press Back in its separately packaged probe app (API 18+). */
final class TestKeys {
    private TestKeys() {}
    static android.app.Activity target;
    private static void awaitInputWindow(final Instrumentation runner){
        final android.app.Activity current=target;if(current==null||android.os.Looper.myLooper()==android.os.Looper.getMainLooper())return;
        final boolean[] ready={false};final String[] state={"unknown"};long deadline=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<deadline){
            runner.runOnMainSync(new Runnable(){public void run(){try{
                java.lang.reflect.Field resumed=current.getClass().getDeclaredField("resumed");resumed.setAccessible(true);
                if(!(Boolean)resumed.get(current)){ready[0]=true;state[0]="external window";return;}
                java.lang.reflect.Field field=current.getClass().getDeclaredField("currentDialog");field.setAccessible(true);android.app.Dialog dialog=(android.app.Dialog)field.get(current);
                android.view.View decor=dialog==null?current.getWindow().getDecorView():dialog.getWindow().getDecorView();
                state[0]=(dialog==null?"activity":"dialog showing="+dialog.isShowing())+", windowFocus="+decor.hasWindowFocus();
                ready[0]=(dialog==null||dialog.isShowing())&&decor.hasWindowFocus()&&!decor.isLayoutRequested();
            }catch(Exception error){throw new RuntimeException(error);}}});
            if(ready[0])return;SystemClock.sleep(80);
        }
        throw new AssertionError("Input focus not ready: "+state[0]);
    }
    static void awaitPackage(Instrumentation runner,String name) {
        if(Build.VERSION.SDK_INT<18){SystemClock.sleep(1000);return;}
        long deadline=SystemClock.uptimeMillis()+8000;
        try {
            Object automation=Instrumentation.class.getMethod("getUiAutomation").invoke(runner);
            while(SystemClock.uptimeMillis()<deadline) {
                Object node=automation.getClass().getMethod("getRootInActiveWindow").invoke(automation);
                if(node!=null) {
                    String actual=String.valueOf(node.getClass().getMethod("getPackageName").invoke(node));
                    node.getClass().getMethod("recycle").invoke(node);
                    if(name.equals(actual)){SystemClock.sleep(150);return;}
                }
                SystemClock.sleep(100);
            }
            throw new AssertionError("Timed out waiting for active package: "+name);
        } catch(Exception error){throw new RuntimeException("Cannot wait for the active test window",error);}
    }
    static void press(Instrumentation runner,int code) {
        awaitInputWindow(runner);
        long down=SystemClock.uptimeMillis();
        send(runner,new KeyEvent(down,down,KeyEvent.ACTION_DOWN,code,0));
        send(runner,new KeyEvent(down,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,code,0));
    }
    static void send(Instrumentation runner,KeyEvent event) {
        event.setSource(InputDevice.SOURCE_KEYBOARD);
        if(Build.VERSION.SDK_INT<18){runner.sendKeySync(event);return;}
        try {
            Object automation=Instrumentation.class.getMethod("getUiAutomation").invoke(runner);
            Object accepted=automation.getClass().getMethod("injectInputEvent",InputEvent.class,boolean.class).invoke(automation,event,true);
            if(!Boolean.TRUE.equals(accepted))throw new AssertionError("UI automation did not inject key "+event.getKeyCode());
        } catch(Exception error){throw new RuntimeException("UI automation key injection failed",error);}
    }
}
