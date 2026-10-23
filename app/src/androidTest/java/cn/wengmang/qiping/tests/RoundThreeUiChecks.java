package cn.wengmang.qiping.tests;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Side-panel navigation, persisted display values and real layout bounds on a disposable emulator. */
final class RoundThreeUiChecks {
    private final Instrumentation runner;private final Activity activity;private final StringBuilder report;
    private RoundThreeUiChecks(Instrumentation r,Activity a,StringBuilder out){runner=r;activity=a;report=out;}
    static void run(Instrumentation r,Activity a,StringBuilder out){new RoundThreeUiChecks(r,a,out).run();}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);report.append("PASS R3 ").append(text).append('\n');}
    private Object get(Object obj,String key){try{Field f=obj.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(obj);}catch(Exception e){throw new RuntimeException(e);}}
    private void set(Object obj,String key,Object value){try{Field f=obj.getClass().getDeclaredField(key);f.setAccessible(true);f.set(obj,value);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object obj,String name,Class<?>[] types,Object... args){try{Method m=obj.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(obj,args);}catch(Exception e){throw new RuntimeException(e);}}
    private void settle(){SystemClock.sleep(330);runner.waitForIdleSync();}
    private void ui(Runnable r){runner.runOnMainSync(r);settle();}
    private void key(int code){TestKeys.press(runner,code);settle();}
    private Dialog dialog(){Object d=get(activity,"currentDialog");return d instanceof Dialog&&((Dialog)d).isShowing()?(Dialog)d:null;}
    private View control(Object owner,String key){return (View)((Map<?,?>)get(owner,"controls")).get(key);}
    private String focus(){return String.valueOf(get(dialog()==null?activity:dialog(),"focusedKey"));}
    private int page(){return (Integer)get(dialog(),"page");}
    private int screen(){return (Integer)get(activity,"screen");}
    private void focus(final String key){ui(new Runnable(){public void run(){View v=control(dialog()==null?activity:dialog(),key);if(v==null)throw new AssertionError("missing "+key);v.requestFocus();}});}
    private void open(String name){focus(name);key(KeyEvent.KEYCODE_DPAD_CENTER);}
    private void render(final int screen,final String key){ui(new Runnable(){public void run(){set(activity,"screen",screen);call(activity,"render",new Class<?>[]{String.class},key);}});}
    private boolean textsFit(View root,boolean horizontal) {
        if(root instanceof TextView) {
            TextView t=(TextView)root;
            if(t.getText().length()>0) {
                if(t.getLayout()==null||t.getLayout().getHeight()>t.getHeight()-t.getPaddingTop()-t.getPaddingBottom()+1)return false;
                if(horizontal&&(t.getLayout().getEllipsisCount(0)>0||t.getPaint().measureText(t.getText().toString())>t.getWidth()-t.getPaddingLeft()-t.getPaddingRight()+1))return false;
            }
        }
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)if(!textsFit(((ViewGroup)root).getChildAt(i),horizontal))return false;
        return true;
    }
    private void verifyPanel(final int font,final int margin) {
        ui(new Runnable(){public void run(){
            View decor=dialog().getWindow().getDecorView(),root=activity.getWindow().getDecorView();int[] xy=new int[2];decor.getLocationOnScreen(xy);
            float safe=new float[]{.03f,.05f,.07f}[margin];
            check(xy[0]>=root.getWidth()*safe-2&&xy[0]+decor.getWidth()<=root.getWidth()*(1-safe)+2&&xy[1]>=root.getHeight()*safe-2&&xy[1]+decor.getHeight()<=root.getHeight()*(1-safe)+2,"panel safe bounds "+font+"/"+margin);
            check(xy[0]>root.getWidth()/2&&textsFit(decor,true),"right panel labels fit "+font+"/"+margin);
            View last=control(dialog(),"quick_all");int[] bottom=new int[2];last.getLocationOnScreen(bottom);
            check(last.isShown()&&bottom[1]+last.getHeight()<=xy[1]+decor.getHeight()-8,"all six rows visible "+font+"/"+margin);
        }});
    }
    @SuppressWarnings("unchecked") private void restore(SharedPreferences prefs,Map<String,?> values) {
        SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> item:values.entrySet()) {
            Object v=item.getValue();String k=item.getKey();
            if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);
            else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);
            else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof java.util.Set)e.putStringSet(k,(java.util.Set<String>)v);
        }e.commit();
    }
    private void run() {
        final SharedPreferences prefs=activity.getSharedPreferences("desktop",0);
        final Map<String,?> original=prefs.getAll(),startup=activity.getSharedPreferences("startup",0).getAll(),organizer=activity.getSharedPreferences("app_organizer",0).getAll();
        try {
            ui(new Runnable(){public void run(){prefs.edit().remove("font_size").putBoolean("large_text",false).putInt("margin",1).putBoolean("reduce_motion",false).commit();}});
            render(0,"dock2");open("home_settings");
            check(dialog()!=null&&page()==0&&focus().equals("quick_font"),"gear opens panel focused on font row");
            check(!activity.hasWindowFocus()&&(Float)get(control(activity,"dock2"),"focusAmount")==0f,"panel alone holds active focus");
            key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_RIGHT);check(focus().equals("quick_font"),"Left and Right stay inside panel");
            for(int i=0;i<8;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);check(focus().equals("quick_all"),"Down reaches and stays at All Settings");
            for(int i=0;i<8;i++)key(KeyEvent.KEYCODE_DPAD_UP);check(focus().equals("quick_back"),"Up reaches panel Back without escaping");
            // Release/repeat from a key held before the panel appeared must not select its first row.
            focus("quick_font");ui(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();Dialog d=dialog();d.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,4));d.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER,0));}});
            check(page()==0,"orphan repeat and release do not activate panel row");
            key(KeyEvent.KEYCODE_BACK);check(dialog()==null&&focus().equals("home_settings")&&(Integer)get(activity,"selected")==2,"closing restores gear and selected category");
            open("home_settings");open("quick_font");check(page()==1&&focus().equals("font0"),"font page starts on actual saved value");
            float standard=((TextView)get(dialog(),"preview")).getTextSize();key(KeyEvent.KEYCODE_DPAD_DOWN);
            check(!prefs.contains("font_size")&&((TextView)get(dialog(),"preview")).getTextSize()==standard&&String.valueOf(control(dialog(),"font0").getContentDescription()).contains("✓"),"navigation changes neither value nor preview/check");
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(prefs.getInt("font_size",-1)==1&&prefs.getBoolean("large_text",false)&&((TextView)get(dialog(),"preview")).getTextSize()>standard,"confirm applies large font then updates preview");
            float large=((TextView)get(dialog(),"preview")).getTextSize();open("font2");
            check(prefs.getInt("font_size",-1)==2&&((TextView)get(dialog(),"preview")).getTextSize()>large,"extra font persists and preview grows");
            key(KeyEvent.KEYCODE_BACK);check(page()==0&&focus().equals("quick_font")&&String.valueOf(control(dialog(),"quick_font").getContentDescription()).contains("特大"),"font Back restores parent row and current label");
            key(KeyEvent.KEYCODE_BACK);open("home_settings");open("quick_font");check(focus().equals("font2"),"reopened panel reads stored extra font");
            final Bundle state=new Bundle();ui(new Runnable(){public void run(){call(activity,"onSaveInstanceState",new Class<?>[]{Bundle.class},state);}});
            check(state.getInt("quick_page",-1)==1&&"font2".equals(state.getString("quick_focus"))&&!state.getBoolean("quick_standalone"),"saved state retains panel page and focus");
            final Dialog previous=dialog();ui(new Runnable(){public void run(){activity.onConfigurationChanged(new Configuration(activity.getResources().getConfiguration()));}});
            check(dialog()!=null&&dialog()!=previous&&page()==1&&focus().equals("font2"),"configuration rebuild restores open child panel");
            key(KeyEvent.KEYCODE_BACK);open("quick_margin");open("margin2");check(prefs.getInt("margin",-1)==2,"panel margin writes existing preference");
            key(KeyEvent.KEYCODE_BACK);check(focus().equals("quick_margin"),"margin Back restores parent row");
            open("quick_motion");check(prefs.getBoolean("reduce_motion",false)&&!(Boolean)get(activity,"motion")&&focus().equals("quick_motion"),"motion switch applies existing flag and keeps focus");
            open("quick_device");check(screen()==4&&dialog()==null,"device row opens existing device page");
            key(KeyEvent.KEYCODE_BACK);check(page()==0&&focus().equals("quick_device"),"device Back returns to source panel row");
            open("quick_all");check(screen()==2&&dialog()==null,"All Settings opens full page");
            boolean complete=true;for(int i=0;i<9;i++)complete&=control(activity,"setting"+i)!=null;
            check(complete,"all nine existing settings remain accessible");
            open("setting0");check(screen()==3,"startup page remains reachable");key(KeyEvent.KEYCODE_BACK);
            check(screen()==2&&focus().equals("setting0"),"startup Back restores full settings");
            open("setting8");key(KeyEvent.KEYCODE_BACK);check(screen()==2&&focus().equals("setting8"),"device nested under full settings returns there");
            open("setting3");check(page()==1&&focus().equals("font2")&&(Boolean)get(dialog(),"standalone"),"full settings reads same font selection");
            open("font0");key(KeyEvent.KEYCODE_BACK);check(dialog()==null&&screen()==2&&focus().equals("setting3")&&!prefs.getBoolean("large_text",true),"full font selection returns to its caller and mirrors legacy preference");
            open("setting1");check(!prefs.getBoolean("reduce_motion",true),"full motion switch shares panel value");
            open("settings_updates");check(screen()==5,"existing update entry remains reachable");key(KeyEvent.KEYCODE_BACK);
            key(KeyEvent.KEYCODE_BACK);check(page()==0&&focus().equals("quick_all")&&String.valueOf(control(dialog(),"quick_font").getContentDescription()).contains("标准")&&String.valueOf(control(dialog(),"quick_motion").getContentDescription()).contains("开启"),"nested full settings return route and values remain synchronized");
            open("quick_system");TestKeys.awaitPackage(runner,"com.android.settings");check(!activity.hasWindowFocus(),"system row launches real Android Settings");
            key(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(runner,"cn.wengmang.qiping");check(dialog()!=null&&focus().equals("quick_system"),"return from system Settings restores same panel row");
            key(KeyEvent.KEYCODE_BACK);
            for(final int font:new int[]{0,1,2})for(final int margin:new int[]{0,1,2}) {
                ui(new Runnable(){public void run(){prefs.edit().putInt("font_size",font).putBoolean("large_text",font>0).putInt("margin",margin).commit();}});
                render(0,"home_settings");open("home_settings");verifyPanel(font,margin);
                open("quick_font");check(textsFit(dialog().getWindow().getDecorView(),true),"font choices and preview fit "+font+"/"+margin);
                key(KeyEvent.KEYCODE_BACK);open("quick_margin");check(textsFit(dialog().getWindow().getDecorView(),true),"margin choices fit "+font+"/"+margin);
                key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);
                if(font==2) {
                    render(2,"setting0");boolean fit=true;for(int i=0;i<9;i++)fit&=textsFit(control(activity,"setting"+i),true);check(fit,"extra font fits full settings "+margin);
                    open("setting8");fit=true;for(int i=0;i<6;i++)fit&=textsFit(control(activity,"device"+i),false);check(fit,"extra font fits device cards "+margin);
                    render(0,"dock0");TextView hero=(TextView)get(activity,"heroTitle");check(textsFit(hero,true),"extra font fits home heading "+margin);
                }
            }
            check(startup.equals(activity.getSharedPreferences("startup",0).getAll()),"display actions preserve startup state");
            check(organizer.equals(activity.getSharedPreferences("app_organizer",0).getAll()),"display actions preserve history and collection");
            for(String name:new String[]{"category_0","category_1","category_2","category_3","favorites","performance_mode"})check(String.valueOf(original.get(name)).equals(String.valueOf(prefs.getAll().get(name))),"display actions preserve "+name);
        } finally {
            ui(new Runnable(){public void run(){call(activity,"dismissDialog",new Class<?>[0]);restore(prefs,original);set(activity,"quickReturnScreen",-1);set(activity,"pendingQuickPage",-1);set(activity,"screen",0);set(activity,"page",0);set(activity,"selected",0);call(activity,"render",new Class<?>[]{String.class},"dock0");}});
        }
    }
}
