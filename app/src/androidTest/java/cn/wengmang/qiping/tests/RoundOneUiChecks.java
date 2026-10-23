package cn.wengmang.qiping.tests;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Test-emulator regression for the first visual round. Restores the original display preferences. */
final class RoundOneUiChecks {
    private final Instrumentation runner;
    private final Activity activity;
    private final StringBuilder report;
    private RoundOneUiChecks(Instrumentation runner,Activity activity,StringBuilder report){this.runner=runner;this.activity=activity;this.report=report;}
    static void run(Instrumentation runner,Activity activity,StringBuilder report)throws Exception{new RoundOneUiChecks(runner,activity,report).run();}
    private Object get(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);report.append("PASS ").append(message).append('\n');}
    private View control(String key)throws Exception{return (View)((Map<?,?>)get(activity,"controls")).get(key);}
    private void settle(){SystemClock.sleep(600);runner.waitForIdleSync();}
    private void render(final int screen,final String focus){runner.runOnMainSync(new Runnable(){@Override public void run(){try{
        Field s=activity.getClass().getDeclaredField("screen");s.setAccessible(true);s.setInt(activity,screen);
        Method m=activity.getClass().getDeclaredMethod("render",String.class);m.setAccessible(true);m.invoke(activity,focus);
    }catch(Exception e){throw new RuntimeException(e);}}});settle();}
    private void run()throws Exception {
        final SharedPreferences prefs=runner.getTargetContext().getSharedPreferences("desktop",0);
        final int oldMargin=prefs.getInt("margin",1);final boolean oldLarge=prefs.getBoolean("large_text",false),oldMotion=prefs.getBoolean("reduce_motion",false);
        try {
            for(int margin=0;margin<3;margin++)for(int large=0;large<2;large++) {
                prefs.edit().putInt("margin",margin).putBoolean("large_text",large==1).putBoolean("reduce_motion",true).commit();
                render(0,"dock0");
                final int m=margin,l=large;
                runner.runOnMainSync(new Runnable(){@Override public void run(){try{
                    for(int i=0;i<5;i++){
                        View v=control("dock"+i);View parent=(View)v.getParent();
                        check(v.getWidth()>v.getHeight()&&v.getLeft()>=0&&v.getRight()<=parent.getWidth(),"horizontal dock fits: margin="+m+", large="+l+", entry="+i);
                    }
                    TextView hero=(TextView)get(activity,"heroTitle");
                    check(hero.getLayout()!=null&&hero.getLayout().getHeight()<=hero.getHeight(),"hero text fits: margin="+m+", large="+l);
                }catch(Exception e){throw new RuntimeException(e);}}});
                render(2,"setting2");
                runner.runOnMainSync(new Runnable(){@Override public void run(){try{
                    for(int i=0;i<9;i++){
                        TextView title=(TextView)get(control("setting"+i),"title");
                        check(title.getLayout()!=null&&title.getLayout().getEllipsisCount(0)==0&&title.getLayout().getHeight()<=title.getHeight(),"settings heading fits: margin="+m+", large="+l+", entry="+i);
                    }
                }catch(Exception e){throw new RuntimeException(e);}}});
                TestKeys.press(runner,KeyEvent.KEYCODE_DPAD_CENTER);settle();
                final Dialog dialog=(Dialog)get(activity,"currentDialog");
                check(dialog!=null&&dialog.isShowing(),"margin dialog opens in display combination "+m+"/"+l);
                runner.runOnMainSync(new Runnable(){@Override public void run(){try{
                    View decor=dialog.getWindow().getDecorView();int[] xy=new int[2];decor.getLocationOnScreen(xy);
                    View root=activity.getWindow().getDecorView();float safe=new float[]{.03f,.05f,.07f}[m];
                    check(xy[0]>=root.getWidth()*safe-2&&xy[1]>=root.getHeight()*safe-2
                        &&xy[0]+decor.getWidth()<=root.getWidth()*(1-safe)+2&&xy[1]+decor.getHeight()<=root.getHeight()*(1-safe)+2,"dialog stays in safe area "+m+"/"+l);
                    check(!activity.hasWindowFocus()&&((Float)get(control("setting2"),"focusAmount"))==0f,"background focus clears while dialog is active "+m+"/"+l);
                }catch(Exception e){throw new RuntimeException(e);}}});
                TestKeys.press(runner,KeyEvent.KEYCODE_BACK);settle();
                check(control("setting2").isFocused()&&activity.hasWindowFocus(),"dialog returns focus "+m+"/"+l);
            }
            prefs.edit().putBoolean("reduce_motion",false).commit();render(0,"dock0");
            TestKeys.press(runner,KeyEvent.KEYCODE_DPAD_UP);settle();
            check(((Float)get(control("dock0"),"focusAmount"))==0f,"selected category does not keep a second focus ring in toolbar");
        } finally {
            prefs.edit().putInt("margin",oldMargin).putBoolean("large_text",oldLarge).putBoolean("reduce_motion",oldMotion).commit();render(0,"dock0");
        }
    }
}
