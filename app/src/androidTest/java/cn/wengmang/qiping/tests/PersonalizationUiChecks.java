package cn.wengmang.qiping.tests;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Disposable-emulator checks. Restores preferences and wallpaper files even on failure. */
final class PersonalizationUiChecks {
    private final Instrumentation runner;private final Activity activity;private final StringBuilder report;
    private PersonalizationUiChecks(Instrumentation r,Activity a,StringBuilder out){runner=r;activity=a;report=out;}
    static void run(Instrumentation r,Activity a,StringBuilder out)throws Exception{new PersonalizationUiChecks(r,a,out).run();}
    private void check(boolean value,String text){if(!value)throw new AssertionError(text);report.append("PASS P15 ").append(text).append('\n');}
    private Object get(Object o,String key){try{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    private void set(Object o,String key,Object value){try{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    private Object invoke(String name){return call(activity,name,new Class<?>[0]);}
    private void settle(){SystemClock.sleep(300);runner.waitForIdleSync();}
    private void ui(Runnable action){runner.runOnMainSync(action);settle();}
    private void key(int key){
        if((Boolean)get(activity,"resumed")){
            final boolean[] ready={false};long deadline=SystemClock.uptimeMillis()+8000;
            while(SystemClock.uptimeMillis()<deadline){runner.runOnMainSync(new Runnable(){public void run(){Dialog d=dialog();View decor=d==null?activity.getWindow().getDecorView():d.getWindow().getDecorView();ready[0]=decor.hasWindowFocus()&&!decor.isLayoutRequested();}});if(ready[0])break;SystemClock.sleep(80);}
            if(!ready[0])throw new AssertionError("Input window not ready before key "+key);
        }
        TestKeys.press(runner,key);settle();
    }
    private View view(String key){return (View)((Map<?,?>)get(activity,"controls")).get(key);}
    private Dialog dialog(){return (Dialog)get(activity,"currentDialog");}
    private View find(View root,String label){String value=String.valueOf(root.getContentDescription());if(value.equals(label)||value.equals(label+"  ✓"))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=find(((ViewGroup)root).getChildAt(i),label);if(v!=null)return v;}return null;}
    private void choose(final String label){ui(new Runnable(){public void run(){View v=find(dialog().getWindow().getDecorView(),label);if(v==null)throw new AssertionError("Missing "+label);v.requestFocus();}});key(KeyEvent.KEYCODE_DPAD_CENTER);}
    private void open(final String key){ui(new Runnable(){public void run(){view(key).requestFocus();}});key(KeyEvent.KEYCODE_DPAD_CENTER);}
    private void render(final int screen,final String focus){ui(new Runnable(){public void run(){invoke("dismissDialog");set(activity,"screen",screen);set(activity,"page",0);set(activity,"pickingSlot",-1);set(activity,"pickingCollection",false);call(activity,"render",new Class<?>[]{String.class},focus);}});}
    @SuppressWarnings("unchecked") private List<Object> apps(){return (List<Object>)get(activity,"apps");}
    private List<?> grid(){return (List<?>)get(activity,"gridApps");}
    private String pkg(Object o){return (String)get(o,"packageName");}
    private void snapshot(String name)throws Exception{SystemClock.sleep(550);runner.waitForIdleSync();Bitmap bitmap=runner.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IOException("Screenshot failed");File dir=new File(activity.getFilesDir(),"verification-015");dir.mkdirs();FileOutputStream out=new FileOutputStream(new File(dir,name+".png"));try{bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{out.close();bitmap.recycle();}}
    private void waitHomeFocus(){long deadline=SystemClock.uptimeMillis()+8000;while(!activity.hasWindowFocus()&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100);check(activity.hasWindowFocus(),"launcher window focused");}
    private byte[] read(File file)throws Exception{if(!file.exists())return null;ByteArrayOutputStream out=new ByteArrayOutputStream();FileInputStream in=new FileInputStream(file);try{byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}finally{in.close();}return out.toByteArray();}
    private static void restore(SharedPreferences prefs,Map<String,?> saved){SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> item:saved.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);}edit.commit();}
    private void run()throws Exception{
        final SharedPreferences desktop=activity.getSharedPreferences("desktop",0),organizer=activity.getSharedPreferences("app_organizer",0);
        final Map<String,?> oldDesktop=desktop.getAll(),oldOrganizer=organizer.getAll();final List<Object> original=new ArrayList<Object>(apps());final Object store=get(activity,"workspace"),wallpapers=get(activity,"wallpapers");
        final File[] files=new File[6];final byte[][] images=new byte[6][];for(int i=0;i<6;i++){files[i]=(File)call(wallpapers,"file",new Class<?>[]{int.class},i);images[i]=read(files[i]);}
        final Class<?> entryType=Class.forName("cn.wengmang.qiping.AppEntry");Constructor<?> constructor=entryType.getDeclaredConstructor(String.class,ResolveInfo.class,String.class,long.class,String.class);constructor.setAccessible(true);
        ResolveInfo probeInfo=new ResolveInfo();probeInfo.activityInfo=activity.getPackageManager().getActivityInfo(new ComponentName("cn.wengmang.qiping.tests","cn.wengmang.qiping.tests.ProbeActivity"),0);
        final Object probe=constructor.newInstance("Qiping Test App",probeInfo,Intent.CATEGORY_LAUNCHER,1L,"1.0");final List<Object> fixtures=new ArrayList<Object>();fixtures.add(probe);
        for(int i=1;i<25;i++){ResolveInfo info=new ResolveInfo();info.activityInfo=new ActivityInfo(probeInfo.activityInfo);info.activityInfo.packageName="cn.qiping.personal"+i;info.activityInfo.name=info.activityInfo.packageName+".Main";fixtures.add(constructor.newInstance("应用 "+i,info,Intent.CATEGORY_LAUNCHER,1L,"1.0"));}
        try{
            ui(new Runnable(){public void run(){desktop.edit().putBoolean("simple_home",false).putBoolean("layout_locked",false).putInt("font_size",0).putInt("margin",1).putInt("saver_minutes",0).putString("home_order","0,1,2,3,4").putInt("home_hidden",0).commit();organizer.edit().putString("app_order","").putString("app_hidden","").commit();set(activity,"scanGeneration",(Integer)get(activity,"scanGeneration")+1);set(activity,"loading",false);set(activity,"scanError",false);apps().clear();apps().addAll(fixtures);}});
            render(0,"home_settings");open("home_settings");choose("个性化，›");check(dialog()!=null,"quick settings opens personalization");snapshot("01-personalization");
            choose("首页排列与隐藏");choose("1. 影视 · 显示");choose("向右移动");check(desktop.getString("home_order","").startsWith("1,0"),"remote moves home category");choose("隐藏此入口");check((desktop.getInt("home_hidden",0)&1)!=0,"remote hides home category");key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);
            render(0,"dock1");check(view("dock0")==null&&view("dock4")!=null&&view("home_settings")!=null,"hidden home keeps essential access");snapshot("02-home-custom");
            int previous=-1;for(int slot:new int[]{1,2,3,4}){View v=view("dock"+slot);int[] xy=new int[2];v.getLocationOnScreen(xy);check(xy[0]>previous,"custom home spacing order "+slot);previous=xy[0];}
            key(KeyEvent.KEYCODE_DPAD_RIGHT);check("dock2".equals(get(activity,"focusedKey")),"remote skips hidden category");key(KeyEvent.KEYCODE_DPAD_UP);check("home_settings".equals(get(activity,"focusedKey")),"custom home Up reaches settings");key(KeyEvent.KEYCODE_DPAD_DOWN);check("dock2".equals(get(activity,"focusedKey")),"custom home restores previous tile");
            ui(new Runnable(){public void run(){desktop.edit().putString("home_order","4,3,2,1,0").putInt("home_hidden",15).commit();}});render(0,"dock4");check(view("dock4")!=null&&view("dock0")==null,"all optional home entries can hide safely");key(KeyEvent.KEYCODE_DPAD_RIGHT);check((Integer)get(activity,"screen")==1,"right edge opens all apps after reorder");
            ui(new Runnable(){public void run(){call(store,"resetHome",new Class<?>[0]);}});render(1,"app13");
            final String moving=pkg(fixtures.get(13));ui(new Runnable(){public void run(){call(activity,"showAppLayout",new Class<?>[]{entryType},fixtures.get(13));}});choose("移到最前");check(pkg(grid().get(0)).equals(moving),"move app across pages to front");check(moving.equals(call(activity,"focusedPackage",new Class<?>[0])),"focus follows moved app");snapshot("03-app-order");choose("在全部应用中隐藏");check(grid().size()==24&&!pkg(grid().get(0)).equals(moving),"hidden app removed from normal list");
            open("apps_manage");choose("已隐藏应用（1）");choose("应用 13 · 恢复显示");check(grid().size()==25,"hidden app can be restored by remote");key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);
            ui(new Runnable(){public void run(){for(Object app:fixtures)call(store,"hideApp",new Class<?>[]{String.class,boolean.class},pkg(app),true);}});render(1,"apps_manage");check(grid().isEmpty()&&view("apps_manage")!=null,"all-hidden empty state retains recovery button");snapshot("04-apps-hidden");
            ui(new Runnable(){public void run(){set(activity,"pickingSlot",1);call(activity,"render",new Class<?>[]{String.class},"app0");}});check(grid().size()==25,"binding picker still includes hidden apps");
            render(1,"apps_manage");open("apps_manage");choose("显示全部隐藏应用");check(grid().size()==25,"show all restores full catalog");choose("恢复默认排序");check(pkg(grid().get(0)).equals(pkg(fixtures.get(0))),"restore default application order");key(KeyEvent.KEYCODE_BACK);
            ui(new Runnable(){public void run(){desktop.edit().putBoolean("layout_locked",true).commit();call(activity,"showAppLayout",new Class<?>[]{entryType},fixtures.get(0));}});check(dialog()==null&&organizer.getString("app_hidden","").isEmpty(),"layout lock blocks app organizer mutation");desktop.edit().putBoolean("layout_locked",false).commit();SystemClock.sleep(2300);
            render(0,"dock0");waitHomeFocus();ui(new Runnable(){public void run(){invoke("showScreensaverSettings");}});snapshot("05-screensaver-options");choose("5 分钟后");check(desktop.getInt("saver_minutes",0)==5,"saver delay saved by remote");choose("立即预览");SystemClock.sleep(500);check(get(activity,"idleView")!=null,"preview enters wallpaper clock");snapshot("06-screensaver");
            String before=(String)get(activity,"focusedKey");key(KeyEvent.KEYCODE_DPAD_CENTER);check(get(activity,"idleView")==null&&dialog()==null&&(Integer)get(activity,"screen")==0,"wake Confirm does not activate tile");check(before.equals(get(activity,"focusedKey"))&&activity.getCurrentFocus()==view(before),"wake preserves selected tile");
            ui(new Runnable(){public void run(){set(activity,"lastInput",SystemClock.elapsedRealtime()-300001);((Runnable)get(activity,"idleCheck")).run();}});check(get(activity,"idleView")!=null,"idle timeout enters screensaver");key(KeyEvent.KEYCODE_BACK);check(get(activity,"idleView")==null&&(Integer)get(activity,"screen")==0,"first Back exits only screensaver");
            render(1,"app0");ui(new Runnable(){public void run(){invoke("enterScreensaver");}});check(get(activity,"idleView")==null,"all-apps screen never enters saver");
            render(0,"dock0");open("home_settings");ui(new Runnable(){public void run(){invoke("enterScreensaver");}});check(get(activity,"idleView")==null,"modal settings prevents saver");
            ui(new Runnable(){public void run(){activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));}});check(dialog()!=null&&(Integer)get(activity,"screen")==0&&get(activity,"launchFeedback")==null,"activity ignores navigation behind modal during window transition");key(KeyEvent.KEYCODE_BACK);
            ui(new Runnable(){public void run(){set(activity,"startupTarget",probe);invoke("enterScreensaver");set(activity,"startupTarget",null);}});check(get(activity,"idleView")==null,"startup countdown prevents saver");
            final int glassCount=((List<?>)get(get(activity,"scene"),"glasses")).size();
            ui(new Runnable(){public void run(){call(activity,"showLaunchFeedback",new Class<?>[]{String.class},"电视直播");}});check(get(activity,"launchFeedback")!=null,"launch feedback uses visible overlay");snapshot("07-opening-feedback");ui(new Runnable(){public void run(){invoke("clearLaunchFeedback");}});check(((List<?>)get(get(activity,"scene"),"glasses")).size()==glassCount,"launch feedback releases glass view references");
            final Class<?> cancellation=Class.forName("cn.wengmang.qiping.WallpaperStore$Cancellation");
            Bitmap generated=Bitmap.createBitmap(2600,1500,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(generated);Paint paint=new Paint();paint.setShader(new LinearGradient(0,0,2600,1500,new int[]{0xff183b56,0xff657e89,0xffd4b385},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,2600,1500,paint);
            ByteArrayOutputStream encoded=new ByteArrayOutputStream();generated.compress(Bitmap.CompressFormat.JPEG,90,encoded);generated.recycle();final byte[] image=encoded.toByteArray();
            for(int i=0;i<6;i++){call(wallpapers,"importStream",new Class<?>[]{int.class,InputStream.class,cancellation},i,new ByteArrayInputStream(image),null);check(files[i].isFile(),"wallpaper stored for scene "+i);}
            BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;BitmapFactory.decodeFile(files[0].getPath(),dimensions);check(Math.max(dimensions.outWidth,dimensions.outHeight)<=1920,"oversized source downsampled before saving");
            final byte[] good=read(files[0]);boolean failed=false;try{call(wallpapers,"importStream",new Class<?>[]{int.class,InputStream.class,cancellation},0,new ByteArrayInputStream(new byte[]{1,2,3}),null);}catch(RuntimeException error){failed=true;}check(failed&&Arrays.equals(good,read(files[0])),"invalid image preserves prior wallpaper");
            Object cancelled=Proxy.newProxyInstance(cancellation.getClassLoader(),new Class<?>[]{cancellation},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args){return true;}});
            failed=false;try{call(wallpapers,"importStream",new Class<?>[]{int.class,InputStream.class,cancellation},0,new ByteArrayInputStream(image),cancelled);}catch(RuntimeException error){failed=true;}check(failed&&Arrays.equals(good,read(files[0])),"cancelled import preserves prior wallpaper");
            InputStream oversized=new InputStream(){int left=21*1024*1024;public int read(){return left-->0?1:-1;}public int read(byte[] b,int off,int len){if(left<=0)return -1;int n=Math.min(left,len);Arrays.fill(b,off,off+n,(byte)1);left-=n;return n;}};
            failed=false;try{call(wallpapers,"importStream",new Class<?>[]{int.class,InputStream.class,cancellation},0,oversized,null);}catch(RuntimeException error){failed=true;}check(failed&&Arrays.equals(good,read(files[0])),"20 MB bound preserves wallpaper on oversize import");
            File source=new File(activity.getCacheDir(),"local-wallpaper-fixture.jpg");FileOutputStream writer=new FileOutputStream(source);writer.write(image);writer.close();
            android.media.ExifInterface exif=new android.media.ExifInterface(source.getPath());exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6");exif.saveAttributes();
            call(wallpapers,"importUri",new Class<?>[]{int.class,android.net.Uri.class,cancellation},3,android.net.Uri.fromFile(source),null);BitmapFactory.decodeFile(files[3].getPath(),dimensions);check(dimensions.outHeight>dimensions.outWidth,"EXIF portrait orientation applied");source.delete();
            ui(new Runnable(){public void run(){call(get(activity,"scene"),"reloadWallpapers",new Class<?>[0]);}});render(0,"dock0");SystemClock.sleep(1500);check(files[0].isFile(),"owned wallpaper survives source removal");snapshot("08-local-wallpaper");
            ui(new Runnable(){public void run(){invoke("showWallpapers");}});snapshot("09-wallpaper-settings");choose("影视 · 本地图片");choose("恢复内置壁纸");check(!files[0].exists(),"remote restores built-in wallpaper");key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);render(0,"dock0");
            File resultFile=new File(activity.getCacheDir(),"result-wallpaper.jpg");writer=new FileOutputStream(resultFile);writer.write(image);writer.close();final Intent resultIntent=new Intent().setData(android.net.Uri.fromFile(resultFile));
            ui(new Runnable(){public void run(){set(activity,"wallpaperScene",0);call(activity,"onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},703,Activity.RESULT_OK,resultIntent);}});
            long deadline=SystemClock.uptimeMillis()+20000;while((Boolean)get(activity,"wallpaperBusy")&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100);check(!(Boolean)get(activity,"wallpaperBusy")&&files[0].isFile(),"picker result imports asynchronously");check((Integer)get(activity,"wallpaperScene")==-1,"picker result consumes pending scene");resultFile.delete();render(0,"dock0");
            // Both requests are issued within one main-loop turn. Only the first may advance the gate.
            final long[] gate=new long[2];ui(new Runnable(){public void run(){call(activity,"launch",new Class<?>[]{entryType},probe);gate[0]=(Long)get(get(activity,"experience"),"launchUntil");call(activity,"launch",new Class<?>[]{entryType},probe);gate[1]=(Long)get(get(activity,"experience"),"launchUntil");}});check(gate[0]==gate[1]&&gate[0]>0,"repeat launch does not advance pending request");
            TestKeys.awaitPackage(runner,"cn.wengmang.qiping.tests");check(!(Boolean)get(activity,"resumed")&&get(activity,"idleView")==null&&get(activity,"launchFeedback")==null,"external app stops saver and launch overlay");
            ui(new Runnable(){public void run(){set(activity,"lastInput",SystemClock.elapsedRealtime()-600001);((Runnable)get(activity,"idleCheck")).run();}});check(get(activity,"idleView")==null,"idle callback cannot cover external video app");
            key(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(runner,"cn.wengmang.qiping");check(!(Boolean)call(get(activity,"experience"),"launching",new Class<?>[]{long.class},SystemClock.elapsedRealtime()),"returning from app clears launch lock");
            ui(new Runnable(){public void run(){call(wallpapers,"reset",new Class<?>[]{int.class},0);desktop.edit().putString("home_order","1,0,2,3,4").putInt("home_hidden",4).putInt("saver_minutes",10).commit();}});
            Object fresh=store.getClass().getDeclaredConstructor(SharedPreferences.class,SharedPreferences.class);Constructor<?> layoutConstructor=(Constructor<?>)fresh;layoutConstructor.setAccessible(true);Object reloaded=layoutConstructor.newInstance(desktop,organizer);check(((String)call(reloaded,"homeOrder",new Class<?>[0])).startsWith("1,0")&&(Integer)call(reloaded,"hiddenHome",new Class<?>[0])==4&&(Integer)call(reloaded,"saverMinutes",new Class<?>[0])==10,"new preferences reload without relying on view state");
            for(int font:new int[]{1,2}){final int selectedFont=font;ui(new Runnable(){public void run(){desktop.edit().putInt("font_size",selectedFont).putInt("margin",2).commit();}});render(0,"home_settings");open("home_settings");choose("个性化，›");check(dialog().getWindow().getDecorView().getHeight()<=activity.getWindow().getDecorView().getHeight()*.88f+2,"personalization fits large-font safe area "+font);if(font==2)snapshot("10-personalization-large");key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);}

        }finally{
            ui(new Runnable(){public void run(){invoke("dismissDialog");invoke("exitScreensaver");invoke("clearLaunchFeedback");set(activity,"startupTarget",null);restore(desktop,oldDesktop);restore(organizer,oldOrganizer);apps().clear();apps().addAll(original);call(get(activity,"organizer"),"reload",new Class<?>[0]);}});
            for(int i=0;i<6;i++){if(images[i]==null)files[i].delete();else {FileOutputStream out=new FileOutputStream(files[i]);out.write(images[i]);out.close();}}
            ui(new Runnable(){public void run(){call(get(activity,"scene"),"reloadWallpapers",new Class<?>[0]);}});render(0,"dock0");
        }
    }
}
