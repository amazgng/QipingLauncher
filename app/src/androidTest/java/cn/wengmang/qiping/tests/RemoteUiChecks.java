package cn.wengmang.qiping.tests;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test-only in-memory fixtures. Never installs packages or changes a production update source. */
final class RemoteUiChecks {
    private final Instrumentation runner;
    private final Activity activity;
    private final StringBuilder report;
    private RemoteUiChecks(Instrumentation runner,Activity activity,StringBuilder report){this.runner=runner;this.activity=activity;this.report=report;}
    static void run(Instrumentation runner,Activity activity,StringBuilder report)throws Exception {new RemoteUiChecks(runner,activity,report).run();}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);report.append("PASS ").append(label).append('\n');}
    private Object get(Object object,String key){try{Field f=object.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new RuntimeException(e);}}
    private void set(Object object,String key,Object value){try{Field f=object.getClass().getDeclaredField(key);f.setAccessible(true);f.set(object,value);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object object,String name,Class<?>[] types,Object... args){try{Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}catch(Exception e){throw new RuntimeException(e);}}
    private void settle(){SystemClock.sleep(360);runner.waitForIdleSync();}
    private void key(int code){TestKeys.press(runner,code);settle();}
    private View view(String key){return (View)((Map<?,?>)get(activity,"controls")).get(key);}
    private String focus(){return String.valueOf(get(activity,"focusedKey"));}
    private void focus(final String key){runner.runOnMainSync(new Runnable(){@Override public void run(){View target=view(key);if(target==null)throw new AssertionError("missing control "+key);target.requestFocus();}});settle();}
    private Dialog dialog(){Object value=get(activity,"currentDialog");return value instanceof Dialog&&((Dialog)value).isShowing()?(Dialog)value:null;}
    private String dialogFocus(){Dialog d=dialog();View v=d==null?null:d.getCurrentFocus();return v==null?"":String.valueOf(v.getContentDescription());}
    private void render(final int screen,final int page,final String key){runner.runOnMainSync(new Runnable(){@Override public void run(){set(activity,"screen",screen);set(activity,"page",page);call(activity,"render",new Class<?>[]{String.class},key);}});settle();}
    @SuppressWarnings("unchecked") private List<Object> entries(){return (List<Object>)get(activity,"apps");}
    private void installFixture(final List<Object> entries){runner.runOnMainSync(new Runnable(){@Override public void run(){entries().clear();entries().addAll(entries);}});}
    private Object entry(Constructor<?> constructor,ResolveInfo info,int number,long version)throws Exception{return constructor.newInstance("遥控器测试 "+number,info,"android.intent.category.LAUNCHER",version,"1.0");}
    private void run()throws Exception {
        final List<Object> original=new ArrayList<Object>(entries());
        final Object repo=get(activity,"updates");
        final Map<String,Object> saved=new LinkedHashMap<String,Object>();
        for(String key:new String[]{"records","checkedAt","configured","loaded","lastAttempt","storePackage","storeTemplate","sourceName"})saved.put(key,get(repo,key));
        Object shortcuts=get(activity,"shortcuts");final String oldBinding=(String)call(shortcuts,"get",new Class<?>[]{int.class},0);
        ResolveInfo info=new ResolveInfo();info.activityInfo=runner.getTargetContext().getPackageManager().getActivityInfo(new ComponentName("cn.wengmang.qiping.tests","cn.wengmang.qiping.tests.ProbeActivity"),0);
        Class<?> entryClass=Class.forName("cn.wengmang.qiping.AppEntry");
        Constructor<?> constructor=entryClass.getDeclaredConstructor(String.class,ResolveInfo.class,String.class,long.class,String.class);constructor.setAccessible(true);
        List<Object> fixtures=new ArrayList<Object>();for(int i=0;i<25;i++)fixtures.add(entry(constructor,info,i+1,10));
        try {
            installFixture(fixtures);render(1,0,"app5");
            check(view("previous")==null&&view("next")==null,"no page buttons in application grid");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);check(focus().equals("app12"),"right edge pages to same first row");
            key(KeyEvent.KEYCODE_DPAD_LEFT);check(focus().equals("app5"),"left edge returns to previous row end");
            focus("app11");key(KeyEvent.KEYCODE_DPAD_RIGHT);check(focus().equals("app18"),"second-row edge preserves row");
            key(KeyEvent.KEYCODE_DPAD_LEFT);check(focus().equals("app11"),"second-row back page restores edge");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);focus("app23");key(KeyEvent.KEYCODE_DPAD_RIGHT);
            check(focus().equals("app24"),"one-item final page is reachable");key(KeyEvent.KEYCODE_DPAD_RIGHT);check(focus().equals("app24"),"final edge never focuses empty slots");
            key(KeyEvent.KEYCODE_DPAD_LEFT);check(focus().equals("app17"),"short final page returns to populated row");
            key(KeyEvent.KEYCODE_DPAD_UP);check(focus().equals("apps_recent")||focus().equals("apps_collection")||focus().equals("apps_updates")||focus().equals("refresh")||focus().equals("back"),"top row reaches a focusable toolbar control: "+focus());
            for(int step=0;step<5&&!focus().equals("refresh");step++)key(KeyEvent.KEYCODE_DPAD_RIGHT);
            check(focus().equals("refresh"),"toolbar Right reaches refresh");
            key(KeyEvent.KEYCODE_DPAD_DOWN);check(focus().equals("app17"),"toolbar down restores prior grid focus");
            key(KeyEvent.KEYCODE_MENU);check(dialog()!=null,"menu key opens app management");key(KeyEvent.KEYCODE_BACK);check(focus().equals("app17"),"closing management preserves grid focus");
            long downTime=SystemClock.uptimeMillis();TestKeys.send(runner,new KeyEvent(downTime,downTime,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER,0));
            SystemClock.sleep(700);runner.waitForIdleSync();check(dialog()!=null,"long center opens management without a Menu button");
            TestKeys.send(runner,new KeyEvent(downTime,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER,0));settle();
            check(dialog()!=null,"releasing long center does not activate first dialog action");key(KeyEvent.KEYCODE_BACK);
            render(1,0,"app0");key(KeyEvent.KEYCODE_DPAD_LEFT);check(((Integer)get(activity,"screen"))==0,"left edge of first normal page returns home");
            focus("dock4");key(KeyEvent.KEYCODE_DPAD_RIGHT);check(((Integer)get(activity,"screen"))==1,"home right edge enters all apps");
            runner.runOnMainSync(new Runnable(){@Override public void run(){set(activity,"pickingSlot",0);}});
            render(1,0,"app0");key(KeyEvent.KEYCODE_DPAD_LEFT);check(((Integer)get(activity,"screen"))==1,"left edge never cancels app binding");key(KeyEvent.KEYCODE_BACK);
            // Parser fixtures are local, explicitly test-only; no simulated catalog enters production assets.
            Method parse=repo.getClass().getDeclaredMethod("parse",String.class);parse.setAccessible(true);
            final String body="{\"schemaVersion\":1,\"apps\":[{\"packageName\":\"cn.wengmang.qiping.tests\",\"versionCode\":11,\"versionName\":\"1.1\",\"minSdk\":14}]}";
            final Map<?,?> catalog=(Map<?,?>)parse.invoke(null,body);
            check(catalog.size()==1,"real JSON parser accepts valid update catalog");
            for(String bad:new String[]{body.replace("\"schemaVersion\":1","\"schemaVersion\":2"),body.replace("\"versionCode\":11","\"versionCode\":11.5"),body.replace("cn.wengmang.qiping.tests","cn.bad/app?x=1"),"{not-json"}) {
                boolean rejected=false;try{parse.invoke(null,bad);}catch(java.lang.reflect.InvocationTargetException expected){rejected=true;}check(rejected,"malformed catalog rejected");
            }
            final Object bound=shortcuts;
            runner.runOnMainSync(new Runnable(){@Override public void run(){
                set(repo,"records",catalog);set(repo,"checkedAt",System.currentTimeMillis());set(repo,"sourceName","本地交互测试");set(repo,"configured",true);set(repo,"loaded",true);set(repo,"lastAttempt",System.currentTimeMillis());
                set(repo,"storePackage","cn.wengmang.qiping.tests");set(repo,"storeTemplate","qiping-test-update://details?id={package}");
                call(bound,"put",new Class<?>[]{int.class,String.class},0,"cn.wengmang.qiping.tests");
            }});
            List<Object> one=new ArrayList<Object>();one.add(fixtures.get(0));installFixture(one);render(0,0,"dock0");
            check((Boolean)get(view("dock0"),"updateAvailable"),"bound home tile receives update badge");
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(dialogFocus().equals("前往更新"),"normal OK on badged app offers remote update action");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);check(dialogFocus().equals("稍后"),"update dialog supports horizontal footer navigation");
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(dialog()==null&&(Boolean)get(view("dock0"),"updateAvailable"),"postponing retains truthful update badge");
            List<Object> installed=new ArrayList<Object>();installed.add(entry(constructor,info,1,11));installFixture(installed);render(1,0,"app0");
            check(!(Boolean)get(view("app0"),"updateAvailable"),"installed newer version clears application badge");
            installFixture(one);render(1,0,"app0");check((Boolean)get(view("app0"),"updateAvailable"),"app grid shares home update state");
            key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_CENTER);
            // Wait for the external window before asserting the handoff; its process may start slowly.
            TestKeys.awaitPackage(runner,"cn.wengmang.qiping.tests");check(!activity.hasWindowFocus(),"remote update action opens configured test-store activity");key(KeyEvent.KEYCODE_BACK);
            TestKeys.awaitPackage(runner,"cn.wengmang.qiping");runner.waitForIdleSync();check(activity.hasWindowFocus(),"returning from store restores launcher");
        }finally {
            runner.runOnMainSync(new Runnable(){@Override public void run(){
                for(Map.Entry<String,Object> item:saved.entrySet())set(repo,item.getKey(),item.getValue());
                Object shortcuts=get(activity,"shortcuts");call(shortcuts,"put",new Class<?>[]{int.class,String.class},0,oldBinding);
                entries().clear();entries().addAll(original);set(activity,"pickingSlot",-1);set(activity,"selected",0);set(activity,"screen",0);set(activity,"page",0);
                call(activity,"render",new Class<?>[]{String.class},"dock0");
            }});settle();
        }
    }
}
