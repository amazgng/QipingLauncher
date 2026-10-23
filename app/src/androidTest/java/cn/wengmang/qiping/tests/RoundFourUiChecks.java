package cn.wengmang.qiping.tests;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Isolated emulator-only checks. No real user backup, external storage or boot defaults are modified. */
final class RoundFourUiChecks {
    private final Instrumentation runner;private final Activity activity;private final StringBuilder report;
    private RoundFourUiChecks(Instrumentation r,Activity a,StringBuilder out){runner=r;activity=a;report=out;}
    static void run(Instrumentation r,Activity a,StringBuilder out)throws Exception{new RoundFourUiChecks(r,a,out).run();}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);report.append("PASS R4 ").append(message).append('\n');}
    private Object get(Object obj,String key){try{Field f=obj.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(obj);}catch(Exception e){throw new RuntimeException(e);}}
    private void set(Object obj,String key,Object value){try{Field f=obj.getClass().getDeclaredField(key);f.setAccessible(true);f.set(obj,value);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object obj,String name,Class<?>[] types,Object... args){try{Method m=obj.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(obj,args);}catch(Exception e){throw new RuntimeException(e);}}
    private void settle(){SystemClock.sleep(330);runner.waitForIdleSync();}
    private void ui(Runnable r){runner.runOnMainSync(r);settle();}
    private void key(int key){TestKeys.press(runner,key);settle();}
    private Dialog dialog(){Object value=get(activity,"currentDialog");return value instanceof Dialog&&((Dialog)value).isShowing()?(Dialog)value:null;}
    private boolean quick(){return dialog()!=null&&dialog().getClass().getSimpleName().equals("QuickSettingsDialog");}
    private View control(Object owner,String key){return (View)((Map<?,?>)get(owner,"controls")).get(key);}
    private View control(String key){return control(quick()?dialog():activity,key);}
    private String focus(){return String.valueOf(get(quick()?dialog():activity,"focusedKey"));}
    private void focus(final String key){ui(new Runnable(){public void run(){View v=control(key);if(v==null)throw new AssertionError("missing "+key);v.requestFocus();}});}
    private void open(String key){focus(key);key(KeyEvent.KEYCODE_DPAD_CENTER);}
    private int page(){return (Integer)get(dialog(),"page");}
    private int screen(){return (Integer)get(activity,"screen");}
    private String activePackage()throws Exception {
        Object automation=Instrumentation.class.getMethod("getUiAutomation").invoke(runner);
        Object node=automation.getClass().getMethod("getRootInActiveWindow").invoke(automation);
        if(node==null)return "";
        try{return String.valueOf(node.getClass().getMethod("getPackageName").invoke(node));}
        finally{node.getClass().getMethod("recycle").invoke(node);}
    }
    private void render(final int screen,final String key){ui(new Runnable(){public void run(){set(activity,"screen",screen);call(activity,"render",new Class<?>[]{String.class},key);}});}
    private View find(View root,String desc){if(desc.equals(String.valueOf(root.getContentDescription())))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=find(((ViewGroup)root).getChildAt(i),desc);if(v!=null)return v;}return null;}
    private void choose(final String desc){ui(new Runnable(){public void run(){View v=find(dialog().getWindow().getDecorView(),desc);if(v==null)throw new AssertionError("missing choice "+desc);v.requestFocus();}});key(KeyEvent.KEYCODE_DPAD_CENTER);}
    private String title(){return dialog()==null||quick()?"":String.valueOf(get(get(dialog(),"spec"),"title"));}
    private void await(String title){long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end&&!title().equals(title)){SystemClock.sleep(100);runner.waitForIdleSync();}check(title().equals(title),"dialog reached: "+title);settle();}
    private String message(){return String.valueOf(get(get(dialog(),"spec"),"message"));}
    private void config(){if(dialog()!=null)ui(new Runnable(){public void run(){call(activity,"dismissDialog",new Class<?>[0]);}});render(2,"settings_family");open("settings_family");open("family_config");}
    private void read(final Uri uri){ui(new Runnable(){public void run(){call(activity,"readConfig",new Class<?>[]{Uri.class},uri);}});}
    private boolean fits(View view,boolean horizontal){if(view instanceof TextView){TextView t=(TextView)view;if(t.getText().length()>0&&(t.getLayout()==null||t.getLayout().getHeight()>t.getHeight()-t.getPaddingTop()-t.getPaddingBottom()+1||horizontal&&(t.getLayout().getEllipsisCount(0)>0||t.getPaint().measureText(t.getText().toString())>t.getWidth()-t.getPaddingLeft()-t.getPaddingRight()+1)))return false;}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(!fits(((ViewGroup)view).getChildAt(i),horizontal))return false;return true;}
    @SuppressWarnings("unchecked") private void restore(SharedPreferences prefs,Map<String,?> all){SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:all.entrySet()){Object v=e.getValue();String k=e.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof java.util.Set)edit.putStringSet(k,(java.util.Set<String>)v);}edit.commit();}
    private void write(File f,byte[] bytes)throws Exception{FileOutputStream out=new FileOutputStream(f);try{out.write(bytes);}finally{out.close();}}
    private byte[] bytes(File f)throws Exception{FileInputStream in=new FileInputStream(f);try{byte[] result=new byte[(int)f.length()];int at=0,n;while(at<result.length&&(n=in.read(result,at,result.length-at))>0)at+=n;return result;}finally{in.close();}}
    @SuppressWarnings("unchecked") private void run()throws Exception {
        final SharedPreferences desktop=activity.getSharedPreferences("desktop",0),organizer=activity.getSharedPreferences("app_organizer",0),startup=activity.getSharedPreferences("startup",0);
        final Map<String,?> originalDesktop=desktop.getAll(),originalOrganizer=organizer.getAll(),originalStartup=startup.getAll();
        final File directory=new File(activity.getFilesDir(),"config-backups");final Map<String,byte[]> oldFiles=new HashMap<String,byte[]>();if(directory.listFiles()!=null)for(File f:directory.listFiles())if(f.isFile())oldFiles.put(f.getName(),bytes(f));
        Object store=get(activity,"configStore");File fixture=new File(activity.getCacheDir(),"round4-import.qiping"),output=new File(activity.getCacheDir(),"round4-export.qiping");
        try {
            ui(new Runnable(){public void run(){desktop.edit().putBoolean("simple_home",false).putBoolean("layout_locked",false).putInt("font_size",0).putBoolean("large_text",false).putInt("margin",1).commit();}});
            render(2,"settings_family");check(control("settings_family")!=null&&control("settings_updates")!=null,"family footer adds entry without removing updates");
            open("settings_family");check(page()==3&&focus().equals("family_simple"),"family panel opens on layout switch");
            open("family_simple");check(desktop.getBoolean("simple_home",false)&&focus().equals("family_simple"),"explicit switch enables optional simple layout");key(KeyEvent.KEYCODE_BACK);check(dialog()==null&&focus().equals("settings_family"),"family Back restores full-settings caller");
            render(0,"simple0");check(control("simple0")!=null&&control("simple1")!=null&&control("simple2")!=null&&control("dock0")==null,"simple home has three large actions");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);check(focus().equals("simple1")&&(Integer)get(activity,"selected")==3,"photos action follows existing album category and background");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);check(screen()==1,"frequent apps uses existing app library");key(KeyEvent.KEYCODE_BACK);check(screen()==0&&focus().equals("simple2"),"library Back restores simple home action");
            key(KeyEvent.KEYCODE_DPAD_DOWN);check(focus().equals("simple_settings"),"Down reaches permanent layout/unlock entry");open("simple_settings");open("family_lock");key(KeyEvent.KEYCODE_BACK);
            check(desktop.getBoolean("layout_locked",false)&&focus().equals("simple_settings"),"layout lock persists and close restores entry");
            ui(new Runnable(){public void run(){desktop.edit().putString("category_1","").commit();}});render(0,"simple0");key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(screen()==0&&(Integer)get(activity,"pickingSlot")==-1,"locked unbound action does not edit binding");
            open("simple2");open("apps_collection");open("collection_add");check(!(Boolean)get(activity,"pickingCollection"),"lock blocks collection additions");
            key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);open("simple_settings");open("family_lock");open("family_simple");key(KeyEvent.KEYCODE_BACK);
            check(!desktop.getBoolean("simple_home",true)&&!desktop.getBoolean("layout_locked",true)&&control("dock0")!=null,"unlock and exit remain reachable and restore five-entry home");
            for(final int font:new int[]{0,1,2})for(final int margin:new int[]{0,1,2}) {
                ui(new Runnable(){public void run(){desktop.edit().putBoolean("simple_home",true).putInt("font_size",font).putBoolean("large_text",font>0).putInt("margin",margin).commit();}});
                render(0,"simple0");
                final int f=font,m=margin;ui(new Runnable(){public void run(){boolean ok=true;int[] xy=new int[2];View root=activity.getWindow().getDecorView();float safe=new float[]{.03f,.05f,.07f}[m];
                    for(int i=0;i<3;i++){View tile=control("simple"+i);tile.getLocationOnScreen(xy);ok&=fits(tile,true)&&xy[0]>=root.getWidth()*safe-3&&xy[0]+tile.getWidth()<=root.getWidth()*(1-safe)+3;}
                    check(ok,"simple cards fit font/margin "+f+"/"+m);}});
                open("simple_settings");check(fits(dialog().getWindow().getDecorView(),false),"family panel text fits "+font+"/"+margin);open("family_config");check(fits(dialog().getWindow().getDecorView(),false),"config panel text fits "+font+"/"+margin);
                key(KeyEvent.KEYCODE_BACK);check(page()==3&&focus().equals("family_config"),"config Back restores family row");key(KeyEvent.KEYCODE_BACK);
            }
            // Round-trip a local backup while retaining unrelated app data and all startup preferences.
            ui(new Runnable(){public void run(){desktop.edit().putBoolean("simple_home",true).putBoolean("layout_locked",false).putInt("font_size",2).putBoolean("large_text",true).putInt("margin",2).putString("category_0","cn.qiping.missing").putString("category_1","cn.wengmang.qiping.tests").putString("category_2","").putString("category_3","cn.wengmang.qiping.tests").commit();organizer.edit().putString("collection_v1","1\ncn.qiping.missing\t\t0\ncn.wengmang.qiping.tests\t\t0").commit();call(get(activity,"organizer"),"reload",new Class<?>[0]);}});
            config();open("config_export");await("本机备份已保存");
            List<File> files=(List<File>)call(store,"backups",new Class<?>[0]);check(!files.isEmpty()&&files.get(0).isFile(),"manual backup produces readable local file");
            final File backup=files.get(0);byte[] exported=bytes(backup);String source=new String(exported,"UTF-8");check(!source.contains("handled_boot")&&!source.contains("live_enabled")&&!source.contains("recent_v1")&&!source.contains("favorites"),"portable file excludes boot state, history and legacy slots");
            key(KeyEvent.KEYCODE_BACK);check(quick()&&focus().equals("config_export"),"backup result Back returns to source row");
            ui(new Runnable(){public void run(){desktop.edit().putBoolean("simple_home",false).putInt("font_size",0).putBoolean("large_text",false).putInt("margin",0).putString("category_0","before.video").putString("category_1","").commit();organizer.edit().putString("collection_v1","1").commit();call(get(activity,"organizer"),"reload",new Class<?>[0]);}});
            final Map<String,?> beforeDesktop=desktop.getAll(),beforeOrganizer=organizer.getAll(),beforeStartup=startup.getAll();
            read(Uri.fromFile(backup));await("预览恢复内容");check(message().contains("未安装：cn.qiping.missing")&&message().contains("本次恢复不会打开直播"),"preview identifies missing apps and explains boot preservation");
            check(desktop.getAll().equals(beforeDesktop)&&organizer.getAll().equals(beforeOrganizer),"preview leaves preferences untouched");
            key(KeyEvent.KEYCODE_BACK);check(quick()&&focus().equals("config_import")&&desktop.getAll().equals(beforeDesktop)&&organizer.getAll().equals(beforeOrganizer),"cancel restores config row without changing data");
            read(Uri.fromFile(backup));await("预览恢复内容");key(KeyEvent.KEYCODE_DPAD_RIGHT);check("确认恢复".equals(String.valueOf(dialog().getCurrentFocus().getContentDescription())),"remote reaches restore confirmation from scrollable preview");key(KeyEvent.KEYCODE_DPAD_CENTER);
            long end=SystemClock.uptimeMillis()+15000;while(!quick()&&SystemClock.uptimeMillis()<end){SystemClock.sleep(100);runner.waitForIdleSync();}settle();
            check(quick()&&focus().equals("config_import")&&desktop.getInt("font_size",-1)==2&&desktop.getBoolean("simple_home",false),"confirmed restore applies appearance and returns to config row");
            check("cn.qiping.missing".equals(desktop.getString("category_0",""))&&((List<?>)call(get(activity,"organizer"),"saved",new Class<?>[0])).size()==2,"missing bindings and collection order survive restore");
            check(startup.getAll().equals(beforeStartup)&&activity.hasWindowFocus()==false,"restore preserves startup state and keeps confirmation UI foreground");
            open("config_undo");await("预览撤销内容");choose("确认撤销");end=SystemClock.uptimeMillis()+15000;while(!quick()&&SystemClock.uptimeMillis()<end){SystemClock.sleep(100);runner.waitForIdleSync();}settle();
            check(desktop.getAll().equals(beforeDesktop)&&organizer.getAll().equals(beforeOrganizer)&&startup.getAll().equals(beforeStartup),"undo restores exact previous preferences without touching startup");check(!(Boolean)call(store,"canUndo",new Class<?>[0]),"undo is consumed after use");
            write(fixture,source.replace("version=1","version=9").replace("version=2","version=9").getBytes("UTF-8"));read(Uri.fromFile(fixture));await("操作未完成");check(desktop.getAll().equals(beforeDesktop)&&organizer.getAll().equals(beforeOrganizer),"unsupported backup causes no partial changes");key(KeyEvent.KEYCODE_BACK);
            write(fixture,new byte[32769]);read(Uri.fromFile(fixture));await("操作未完成");check(desktop.getAll().equals(beforeDesktop),"oversized backup is refused before changes");key(KeyEvent.KEYCODE_BACK);
            // Exercise the Activity result and resolver write path with a selected local fixture URI.
            ui(new Runnable(){public void run(){set(activity,"pendingBackupName",backup.getName());call(activity,"onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},701,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(output)));}});
            await("备份文件已导出");check(java.util.Arrays.equals(exported,bytes(output)),"export result writes exact portable file through resolver");key(KeyEvent.KEYCODE_BACK);
            open("config_import");choose("选择备份文件");TestKeys.awaitPackage(runner,"com.android.documentsui");check(dialog()==null&&!activity.hasWindowFocus(),"native local-file picker opens on API "+android.os.Build.VERSION.SDK_INT);
            key(KeyEvent.KEYCODE_BACK);
            // The native picker may initially open its locations drawer. Back first closes
            // that level; only send another Back while the picker still owns the window.
            if("com.android.documentsui".equals(activePackage())){report.append("INFO native picker still active after first Back; return through its next level\n");key(KeyEvent.KEYCODE_BACK);}
            TestKeys.awaitPackage(runner,"cn.wengmang.qiping");check(quick()&&focus().equals("config_import"),"file-picker cancel returns to restore row");
            check(startup.getAll().equals(beforeStartup),"file export/import/cancel never modifies startup preferences");
            key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);
            ui(new Runnable(){public void run(){desktop.edit().putBoolean("simple_home",true).putString("category_1","cn.wengmang.qiping.tests").putString("category_3","cn.wengmang.qiping.tests").commit();}});
            render(0,"simple0");open("simple0");TestKeys.awaitPackage(runner,"cn.wengmang.qiping.tests");key(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(runner,"cn.wengmang.qiping");check(screen()==0&&focus().equals("simple0"),"Watch TV launches bound external app and Back restores card");
            open("simple1");TestKeys.awaitPackage(runner,"cn.wengmang.qiping.tests");key(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(runner,"cn.wengmang.qiping");check(screen()==0&&focus().equals("simple1"),"Photos launches existing album binding and restores card");
            ui(new Runnable(){public void run(){desktop.edit().putBoolean("simple_home",false).commit();}});render(0,"dock0");check(control("dock4")!=null,"standard home still has all original five entries");
        }finally {
            ui(new Runnable(){public void run(){call(activity,"dismissDialog",new Class<?>[0]);restore(desktop,originalDesktop);restore(organizer,originalOrganizer);restore(startup,originalStartup);call(get(activity,"organizer"),"reload",new Class<?>[0]);set(activity,"configApplying",false);set(activity,"configRecoveryError",null);set(activity,"quickReturnScreen",-1);set(activity,"pendingQuickPage",-1);set(activity,"screen",0);set(activity,"selected",0);set(activity,"page",0);set(activity,"pickingSlot",-1);set(activity,"pickingCollection",false);call(activity,"render",new Class<?>[]{String.class},"dock0");}});
            File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.isFile())f.delete();for(Map.Entry<String,byte[]> e:oldFiles.entrySet())write(new File(directory,e.getKey()),e.getValue());fixture.delete();output.delete();
        }
    }
}
