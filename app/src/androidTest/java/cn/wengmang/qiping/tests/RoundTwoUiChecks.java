package cn.wengmang.qiping.tests;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Round-two checks on a disposable emulator. Fixture packages never enter a production catalog. */
final class RoundTwoUiChecks {
    private final Instrumentation runner;private final Activity activity;private final StringBuilder report;
    private Object organizer;private Class<?> entryType;
    private RoundTwoUiChecks(Instrumentation r,Activity a,StringBuilder out){runner=r;activity=a;report=out;}
    static void run(Instrumentation r,Activity a,StringBuilder out)throws Exception{new RoundTwoUiChecks(r,a,out).run();}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);report.append("PASS R2 ").append(label).append('\n');}
    private Object get(Object obj,String key){try{Field f=obj.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(obj);}catch(Exception e){throw new RuntimeException(e);}}
    private void set(Object obj,String key,Object value){try{Field f=obj.getClass().getDeclaredField(key);f.setAccessible(true);f.set(obj,value);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object obj,String name,Class<?>[] types,Object... args){try{Method m=obj.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(obj,args);}catch(Exception e){throw new RuntimeException(e);}}
    private Object store(String method){return call(organizer,method,new Class<?>[0]);}
    private Object store(String method,String pkg){return call(organizer,method,new Class<?>[]{String.class},pkg);}
    private void settle(){SystemClock.sleep(330);runner.waitForIdleSync();}
    private void ui(Runnable r){runner.runOnMainSync(r);settle();}
    private void key(int code){
        // UI queue idle is not equivalent to WindowManager input focus, especially under software emulation.
        if(!(Boolean)get(activity,"resumed")){TestKeys.press(runner,code);settle();return;}
        final boolean[] ready={false};long deadline=SystemClock.uptimeMillis()+8000;
        while(SystemClock.uptimeMillis()<deadline){runner.runOnMainSync(new Runnable(){public void run(){Dialog d=dialog();View decor=d==null?activity.getWindow().getDecorView():d.getWindow().getDecorView();ready[0]=decor.hasWindowFocus()&&!decor.isLayoutRequested();}});if(ready[0])break;SystemClock.sleep(80);}
        if(!ready[0])throw new AssertionError("Input window not ready before key "+code);
        TestKeys.press(runner,code);settle();
    }
    private View view(String key){return (View)((Map<?,?>)get(activity,"controls")).get(key);}
    private String focus(){return String.valueOf(get(activity,"focusedKey"));}
    private int screen(){return (Integer)get(activity,"screen");}
    private void focus(final String key){ui(new Runnable(){public void run(){View v=view(key);if(v==null)throw new AssertionError("missing "+key);v.requestFocus();}});}
    private void open(String key){focus(key);key(KeyEvent.KEYCODE_DPAD_CENTER);}
    private Dialog dialog(){Object d=get(activity,"currentDialog");return d instanceof Dialog&&((Dialog)d).isShowing()?(Dialog)d:null;}
    private String dialogFocus(){View v=dialog()==null?null:dialog().getCurrentFocus();return v==null?"":String.valueOf(v.getContentDescription());}
    private View find(View root,String desc){if(desc.equals(String.valueOf(root.getContentDescription())))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=find(((ViewGroup)root).getChildAt(i),desc);if(v!=null)return v;}return null;}
    private void choose(final String desc){ui(new Runnable(){public void run(){View v=find(dialog().getWindow().getDecorView(),desc);if(v==null)throw new AssertionError("missing action "+desc);v.requestFocus();}});key(KeyEvent.KEYCODE_DPAD_CENTER);}
    private void render(final int screen,final int page,final String key){ui(new Runnable(){public void run(){set(activity,"screen",screen);set(activity,"page",page);call(activity,"render",new Class<?>[]{String.class},key);}});}
    @SuppressWarnings("unchecked") private List<Object> entries(){return (List<Object>)get(activity,"apps");}
    private int size(String name){return ((List<?>)store(name)).size();}
    private String pkg(Object item){return String.valueOf(get(item,"packageName"));}
    private String focusedPackage(){return String.valueOf(call(activity,"focusedPackage",new Class<?>[0]));}
    private void fixture(final List<Object> list){ui(new Runnable(){public void run(){set(activity,"scanGeneration",(Integer)get(activity,"scanGeneration")+1);entries().clear();entries().addAll(list);set(activity,"loading",false);set(activity,"scanError",false);}});}
    private void waitScan(){long until=SystemClock.uptimeMillis()+10000;while((Boolean)get(activity,"loading")&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);settle();}
    private boolean launch(final Object entry){final boolean[] result=new boolean[1];runner.runOnMainSync(new Runnable(){public void run(){result[0]=(Boolean)call(activity,"launch",new Class<?>[]{entryType},entry);}});return result[0];}
    private void returnFromProbe(){TestKeys.awaitPackage(runner,"cn.wengmang.qiping.tests");key(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(runner,"cn.wengmang.qiping");waitScan();}
    private void verifySidePanelSafeArea(final boolean large,final int margin) {
        // WindowManager may position a new window after the app's main queue becomes idle.
        // Read geometry on the UI thread and wait for the actual focused window, not a fixed sleep.
        final boolean[] ready={false};final String[] geometry={"window not ready"};
        long deadline=SystemClock.uptimeMillis()+8000;
        while(SystemClock.uptimeMillis()<deadline) {
            runner.runOnMainSync(new Runnable(){public void run(){
                Dialog d=dialog();if(d==null)return;View decor=d.getWindow().getDecorView();int[] xy=new int[2];decor.getLocationOnScreen(xy);
                float safe=new float[]{.03f,.05f,.07f}[margin];int w=activity.getWindow().getDecorView().getWidth(),h=activity.getWindow().getDecorView().getHeight();
                geometry[0]=xy[0]+","+xy[1]+" "+decor.getWidth()+"x"+decor.getHeight()+", focused="+decor.hasWindowFocus()+", inset="+d.getWindow().getAttributes().x;
                ready[0]=decor.hasWindowFocus()&&!decor.isLayoutRequested()&&xy[0]>=w*safe-2&&xy[0]+decor.getWidth()<=w*(1-safe)+2&&xy[1]>=h*safe-2&&xy[1]+decor.getHeight()<=h*(1-safe)+2;
            }});
            if(ready[0])break;SystemClock.sleep(100);
        }
        if(!ready[0])try{
            android.view.accessibility.AccessibilityNodeInfo node=runner.getUiAutomation().getRootInActiveWindow();report.append("INFO focused package=").append(node==null?"none":node.getPackageName()).append(" activityFocus=").append(activity.hasWindowFocus()).append('\n');if(node!=null)node.recycle();
            android.os.ParcelFileDescriptor descriptor=runner.getUiAutomation().executeShellCommand("dumpsys window windows");java.io.InputStream dump=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor);java.io.FileOutputStream saved=new java.io.FileOutputStream(new java.io.File(activity.getFilesDir(),"focus-windows.txt"));try{byte[] data=new byte[16384];int count;while((count=dump.read(data))!=-1)saved.write(data,0,count);}finally{dump.close();saved.close();}
            android.graphics.Bitmap frame=runner.getUiAutomation().takeScreenshot();java.io.File file=new java.io.File(activity.getFilesDir(),"focus-diagnostic.png");java.io.FileOutputStream out=new java.io.FileOutputStream(file);frame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);out.close();frame.recycle();
        }catch(Exception error){report.append("INFO diagnostic ").append(error).append('\n');}
        check(ready[0],"collection side menu safe area "+large+"/"+margin+": "+geometry[0]);
    }
    @SuppressWarnings("unchecked") private void verifyHeader() {
        int[] loc=new int[2];
        for(View v:((Map<String,View>)get(activity,"controls")).values()) {
            if(!String.valueOf(v.getContentDescription()).matches("最近打开|收藏|记录：开|记录：关|清空记录|添加应用|整理"))continue;
            v.getLocationOnScreen(loc);check(loc[0]>=0&&loc[0]+v.getWidth()<=activity.getWindow().getDecorView().getWidth(),"toolbar within visible width");
            if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(((ViewGroup)v).getChildAt(i) instanceof TextView) {
                TextView t=(TextView)((ViewGroup)v).getChildAt(i);
                check(t.getLayout()!=null&&t.getLayout().getEllipsisCount(0)==0&&t.getLayout().getHeight()<=t.getHeight(),"toolbar label fits");
            }
        }
    }
    @SuppressWarnings("unchecked") private void restore(SharedPreferences prefs,Map<String,?> backup) {
        SharedPreferences.Editor edit=prefs.edit().clear();
        for(Map.Entry<String,?> e:backup.entrySet()) {
            Object v=e.getValue();String k=e.getKey();
            if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
            else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);
            else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof java.util.Set)edit.putStringSet(k,(java.util.Set<String>)v);
        }edit.commit();
    }
    private void run()throws Exception {
        final SharedPreferences prefs=activity.getSharedPreferences("app_organizer",0),desktop=activity.getSharedPreferences("desktop",0);
        final Map<String,?> backup=prefs.getAll();final boolean oldLarge=desktop.getBoolean("large_text",false);final int oldMargin=desktop.getInt("margin",1);
        final Map<String,?> oldDesktop=desktop.getAll(),oldStartup=activity.getSharedPreferences("startup",0).getAll();
        organizer=get(activity,"organizer");entryType=Class.forName("cn.wengmang.qiping.AppEntry");
        Constructor<?> constructor=entryType.getDeclaredConstructor(String.class,ResolveInfo.class,String.class,long.class,String.class);constructor.setAccessible(true);
        ResolveInfo probeInfo=new ResolveInfo();probeInfo.activityInfo=activity.getPackageManager().getActivityInfo(new ComponentName("cn.wengmang.qiping.tests","cn.wengmang.qiping.tests.ProbeActivity"),0);
        final Object probe=constructor.newInstance("Qiping Test App",probeInfo,"android.intent.category.LAUNCHER",1L,"1.0");
        final List<Object> fixtures=new ArrayList<Object>();fixtures.add(probe);
        for(int i=1;i<14;i++) {
            ResolveInfo info=new ResolveInfo();info.activityInfo=new ActivityInfo(probeInfo.activityInfo);
            info.activityInfo.packageName="cn.qiping.fixture"+i;info.activityInfo.name=info.activityInfo.packageName+".Main";
            fixtures.add(constructor.newInstance("收藏测试 "+i,info,"android.intent.category.LAUNCHER",1L,"1.0"));
        }
        try {
            ui(new Runnable(){public void run(){prefs.edit().clear().commit();call(organizer,"reload",new Class<?>[0]);}});
            waitScan();render(1,0,"app0");
            check(view("apps_recent")!=null&&view("apps_collection")!=null&&view("apps_updates")!=null&&view("refresh")!=null,"all apps retains old tools and adds both list entries");
            focus("apps_recent");key(KeyEvent.KEYCODE_DPAD_RIGHT);check(focus().equals("apps_collection"),"Right reaches collection entry");key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(screen()==6&&view("app0")==null&&focus().equals("recent_toggle"),"empty history has actionable focus");
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(!(Boolean)store("enabled"),"remote turns history off");
            check(launch(probe),"successful launch while recording disabled");returnFromProbe();check(size("recent")==0,"disabled history does not collect");
            open("recent_toggle");check((Boolean)store("enabled"),"remote turns history on");
            check(!launch(fixtures.get(1)),"uninstalled app launch returns failure");waitScan();check(size("recent")==0&&dialog()!=null,"failed launch has returnable message and never enters history");key(KeyEvent.KEYCODE_BACK);
            check(launch(probe),"successful app launch request");returnFromProbe();check(size("recent")==1,"successful launcher request recorded");
            check(launch(probe),"repeat app request accepted");returnFromProbe();check(size("recent")==1,"repeat launch deduplicates");
            open("recent_clear");key(KeyEvent.KEYCODE_BACK);check(size("recent")==1&&focus().equals("recent_clear"),"clear cancel preserves history and caller focus");
            open("recent_clear");choose("清空记录");check(size("recent")==0&&(Boolean)store("enabled"),"confirmed clear preserves record switch");
            key(KeyEvent.KEYCODE_BACK);check(screen()==1&&focus().equals("apps_recent"),"Back restores all-app toolbar caller");
            open("apps_collection");check(screen()==7&&focus().equals("collection_add"),"empty collection focuses Add");
            open("collection_add");check((Boolean)get(activity,"pickingCollection"),"Add opens existing app picker");key(KeyEvent.KEYCODE_DPAD_LEFT);check(screen()==1&&(Boolean)get(activity,"pickingCollection"),"picker left edge does not cancel");
            key(KeyEvent.KEYCODE_BACK);check(screen()==7&&focus().equals("collection_add"),"picker cancel restores collection caller");
            open("collection_add");int probeIndex=-1;for(int i=0;i<entries().size();i++)if(pkg(entries().get(i)).equals("cn.wengmang.qiping.tests"))probeIndex=i;
            check(probeIndex>=0,"real probe found in picker");render(1,probeIndex/12,"app"+probeIndex);key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(screen()==7&&size("saved")==1&&focusedPackage().equals("cn.wengmang.qiping.tests"),"picker adds and focuses new collection app");
            for(int i=0;i<4;i++)check(String.valueOf(oldDesktop.get("category_"+i)).equals(String.valueOf(desktop.getAll().get("category_"+i))),"collection leaves category binding intact");
            check(String.valueOf(oldDesktop.get("favorites")).equals(String.valueOf(desktop.getAll().get("favorites"))),"legacy favorites preserved");
            fixture(fixtures);
            ui(new Runnable(){public void run(){call(organizer,"clearSaved",new Class<?>[0]);for(int i=0;i<12;i++)store("add",pkg(fixtures.get(i)));}});
            render(7,0,"app5");key(KeyEvent.KEYCODE_MENU);
            check(dialog()!=null&&dialogFocus().equals("向左移动"),"collection Menu opens order actions");
            check(((Float)get(view("app5"),"focusAmount"))==0f,"dialog has sole active focus; background card muted");
            key(KeyEvent.KEYCODE_DPAD_DOWN);check(dialogFocus().equals("向右移动"),"Down reaches right move");key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(focus().equals("app6")&&focusedPackage().equals(pkg(fixtures.get(5))),"move right across row keeps same app focused");
            key(KeyEvent.KEYCODE_MENU);key(KeyEvent.KEYCODE_DPAD_CENTER);check(focus().equals("app5"),"move left restores row position");
            focus("app11");key(KeyEvent.KEYCODE_MENU);check(!dialogFocus().equals("向右移动"),"end-of-list excludes impossible right move");choose("移出收藏");
            check(size("saved")==11&&focus().equals("app10"),"remove last card focuses populated neighbor");
            ui(new Runnable(){public void run(){store("add",pkg(fixtures.get(11)));}});render(7,0,"collection_add");key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(screen()==7&&!(Boolean)get(activity,"pickingCollection"),"full collection does not open unusable picker");
            for(final boolean large:new boolean[]{false,true})for(final int margin:new int[]{0,1,2}) {
                ui(new Runnable(){public void run(){desktop.edit().putBoolean("large_text",large).putInt("margin",margin).apply();}});
                render(1,0,"apps_recent");verifyHeader();render(6,0,"recent_toggle");verifyHeader();render(7,0,"app5");verifyHeader();
                key(KeyEvent.KEYCODE_MENU);verifySidePanelSafeArea(large,margin);
                key(KeyEvent.KEYCODE_BACK);check(dialog()==null&&focus().equals("app5"),"side menu Back restores source card");
            }
            // A vanished app is hidden without destroying stored favorites; cleanup requires confirmation.
            final List<Object> available=new ArrayList<Object>(fixtures);available.remove(0);fixture(available);render(7,0,"app0");
            check(((List<?>)get(activity,"gridApps")).size()==11&&size("saved")==12&&(Integer)get(activity,"unavailableCount")==1,"uninstalled app creates no ghost tile and is not silently deleted");
            open("collection_manage");key(KeyEvent.KEYCODE_BACK);check(size("saved")==12,"cleanup cancel keeps saved order");
            open("collection_manage");choose("移除不可用项");check(size("saved")==11,"confirmed cleanup removes only missing item");
            ui(new Runnable(){public void run(){set(activity,"scanError",true);}});render(7,0,"collection_manage");
            check(view("app0")==null&&size("saved")==11,"scan failure shows text state without deleting favorites");
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(dialog()==null,"cleanup blocked on failed scan");fixture(fixtures);
            ui(new Runnable(){public void run(){prefs.edit().putString("collection_v1","damaged").commit();call(organizer,"reload",new Class<?>[0]);}});render(7,0,"collection_manage");
            check(view("app0")==null&&(Boolean)store("savedError"),"corrupt collection has safe empty error UI");
            open("collection_manage");key(KeyEvent.KEYCODE_BACK);check("damaged".equals(prefs.getString("collection_v1","")),"cancel repair does not overwrite corrupt bytes");
            open("collection_manage");choose("重置收藏");check(!(Boolean)store("savedError")&&focus().equals("collection_add"),"explicit repair returns to actionable Add");
            render(1,1,"apps_collection");key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_BACK);
            check(screen()==1&&(Integer)get(activity,"page")==1&&focus().equals("apps_collection"),"nested list Back restores source page and toolbar focus");
            open("apps_collection");open("collection_add");
            final Bundle state=new Bundle();ui(new Runnable(){public void run(){call(activity,"onSaveInstanceState",new Class<?>[]{Bundle.class},state);}});
            check(state.getBoolean("picking_collection")&&state.getInt("collection_origin_page")==1&&"collection_add".equals(state.getString("picker_origin_focus")),"recreation bundle preserves picker and return route");key(KeyEvent.KEYCODE_BACK);
            check(oldStartup.equals(activity.getSharedPreferences("startup",0).getAll()),"organizer actions do not alter startup state");
        }finally {
            ui(new Runnable(){public void run(){
                call(activity,"dismissDialog",new Class<?>[0]);restore(prefs,backup);call(organizer,"reload",new Class<?>[0]);
                desktop.edit().putBoolean("large_text",oldLarge).putInt("margin",oldMargin).apply();
                set(activity,"pickingCollection",false);set(activity,"pickingSlot",-1);set(activity,"screen",0);set(activity,"page",0);set(activity,"selected",0);
                call(activity,"render",new Class<?>[]{String.class},"dock0");call(activity,"refreshApps",new Class<?>[0]);
            }});waitScan();
        }
    }
}
