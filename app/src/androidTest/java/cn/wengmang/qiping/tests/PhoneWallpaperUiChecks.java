package cn.wengmang.qiping.tests;

import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;
import java.io.*;import java.lang.reflect.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;

/** API 28 real-socket checks with the actual decoder, private staging and remote buttons. */
final class PhoneWallpaperUiChecks {
    private final Instrumentation runner;private final Activity activity;private final StringBuilder report;
    private PhoneWallpaperUiChecks(Instrumentation r,Activity a,StringBuilder report){runner=r;activity=a;this.report=report;}
    static void run(Instrumentation r,Activity a,StringBuilder out)throws Exception{new PhoneWallpaperUiChecks(r,a,out).run();}
    private Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object o,String name){return call(o,name,new Class<?>[0]);}
    private void ui(Runnable r){runner.runOnMainSync(r);SystemClock.sleep(220);runner.waitForIdleSync();}
    private void key(int code){TestKeys.press(runner,code);SystemClock.sleep(280);runner.waitForIdleSync();}
    private Dialog dialog(){return (Dialog)field(activity,"currentDialog");}
    private View find(View view,String description){if(description.equals(String.valueOf(view.getContentDescription())))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View value=find(((ViewGroup)view).getChildAt(i),description);if(value!=null)return value;}return null;}
    private void choose(final String label){ui(new Runnable(){public void run(){View view=find(dialog().getWindow().getDecorView(),label);if(view==null)throw new AssertionError("Missing "+label);view.requestFocus();}});key(KeyEvent.KEYCODE_DPAD_CENTER);}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);report.append("PASS P16 ").append(label).append('\n');}
    private Object waitServer()throws Exception{long deadline=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<deadline){Dialog d=dialog();if(d!=null&&d.getClass().getSimpleName().equals("PhoneWallpaperDialog")){Object server=field(d,"server");if(server!=null&&(Boolean)call(server,"isRunning"))return server;}SystemClock.sleep(100);}throw new AssertionError("Receiver did not start");}
    private void waitPreview()throws Exception{long deadline=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<deadline){if((Boolean)field(dialog(),"previewReady"))return;SystemClock.sleep(100);}throw new AssertionError("Preview not ready");}
    private String auth(Object server){String qr=(String)call(server,"qrAddress");return "X-Qiping-Token: "+qr.substring(qr.indexOf('#')+1)+"\r\n";}
    private String request(Object server,String method,String path,String headers,byte[] body)throws Exception{
        URL url=new URL((String)call(server,"address"));Socket socket=new Socket();socket.connect(new InetSocketAddress(url.getHost(),url.getPort()),3000);socket.setSoTimeout(30000);try{OutputStream out=socket.getOutputStream();String head=method+" "+path+" HTTP/1.1\r\nHost: "+url.getAuthority()+"\r\n"+headers+(body==null?"":"Content-Length: "+body.length+"\r\n")+"Connection: close\r\n\r\n";out.write(head.getBytes(StandardCharsets.US_ASCII));if(body!=null)out.write(body);out.flush();ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=socket.getInputStream().read(buffer))!=-1)result.write(buffer,0,n);return new String(result.toByteArray(),StandardCharsets.UTF_8);}finally{socket.close();}
    }
    private void snap(String name)throws Exception{SystemClock.sleep(500);Bitmap image=runner.getUiAutomation().takeScreenshot();File directory=new File(activity.getFilesDir(),"verification-016");directory.mkdirs();FileOutputStream out=new FileOutputStream(new File(directory,name+".png"));try{image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{out.close();image.recycle();}}
    private byte[] read(File file)throws IOException{if(!file.isFile())return null;FileInputStream input=new FileInputStream(file);ByteArrayOutputStream output=new ByteArrayOutputStream();try{byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);}finally{input.close();}return output.toByteArray();}
    private void run()throws Exception{
        final Object store=field(activity,"wallpapers");final File[] files=new File[6];final byte[][] originals=new byte[6][];for(int i=0;i<6;i++){files[i]=(File)call(store,"file",new Class<?>[]{int.class},i);originals[i]=read(files[i]);}
        final SharedPreferences prefs=activity.getSharedPreferences("desktop",0);final int oldFont=prefs.getInt("font_size",0),oldMargin=prefs.getInt("margin",1);
        try{
            ui(new Runnable(){public void run(){call(activity,"showWallpapers");}});choose("手机传壁纸");final Object server=waitServer();final Dialog receive=dialog();
            check(server!=null,"wallpaper menu opens LAN receiver");check(InetAddress.getByName(new URL((String)call(server,"address")).getHost()).isSiteLocalAddress(),"receiver binds actual emulator LAN address");
            check(request(server,"GET","/","",null).contains("把喜欢的风景"),"phone page served from packaged assets");check(request(server,"GET","/style.css","",null).startsWith("HTTP/1.1 200"),"phone style served locally");check(request(server,"GET","/app.js","",null).startsWith("HTTP/1.1 200"),"phone script served locally");
            URL lan=new URL((String)call(server,"address"));Socket local=new Socket("127.0.0.1",lan.getPort());local.setSoTimeout(5000);local.getOutputStream().write(("GET / HTTP/1.1\r\nHost: 127.0.0.1:"+lan.getPort()+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));BufferedReader response=new BufferedReader(new InputStreamReader(local.getInputStream()));check(response.readLine().startsWith("HTTP/1.1 200"),"loopback companion supports Android Studio adb forwarding");local.close();
            String qr=(String)call(server,"qrAddress");FileOutputStream link=new FileOutputStream(new File(activity.getFilesDir(),"verification-016-qr.txt"));link.write(qr.getBytes(StandardCharsets.UTF_8));link.close();snap("01-phone-receiver");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);check("结束传输".equals(String.valueOf(receive.getCurrentFocus().getContentDescription())),"remote skips unavailable actions before upload");
            ui(new Runnable(){public void run(){call(activity,"enterScreensaver");}});check(field(activity,"idleView")==null,"receiver dialog prevents desktop screensaver");
            byte[] junk={1,2,3};String invalid=request(server,"POST","/upload?scene=1",auth(server)+"Content-Type: image/png\r\n",junk);check(invalid.startsWith("HTTP/1.1 422")&&Arrays.equals(originals[1],read(files[1])),"bad upload preserves prior wallpaper");
            Bitmap fixture=Bitmap.createBitmap(1600,900,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(fixture);Paint paint=new Paint();paint.setShader(new LinearGradient(0,0,1600,900,new int[]{0xff162b3d,0xff4d7682,0xffd2b382},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,1600,900,paint);ByteArrayOutputStream data=new ByteArrayOutputStream();fixture.compress(Bitmap.CompressFormat.JPEG,90,data);fixture.recycle();byte[] image=data.toByteArray();
            check(request(server,"POST","/upload?scene=1",auth(server)+"Content-Type: image/jpeg\r\n",image).startsWith("HTTP/1.1 200"),"actual image upload decodes successfully");waitPreview();check(Arrays.equals(originals[1],read(files[1]))&&"pending".equals(call(server,"state")),"received image waits for TV approval");snap("02-phone-preview");
            check(request(server,"POST","/upload?scene=2",auth(server)+"Content-Type: image/jpeg\r\n",image).startsWith("HTTP/1.1 409"),"second phone upload cannot replace pending image");
            key(KeyEvent.KEYCODE_DPAD_LEFT);check("放弃图片".equals(String.valueOf(receive.getCurrentFocus().getContentDescription())),"Left reaches discard action");key(KeyEvent.KEYCODE_DPAD_CENTER);check("rejected".equals(call(server,"state"))&&Arrays.equals(originals[1],read(files[1])),"remote discard keeps previous wallpaper");
            request(server,"POST","/upload?scene=1",auth(server)+"Content-Type: image/jpeg\r\n",image);waitPreview();key(KeyEvent.KEYCODE_DPAD_CENTER);check("applied".equals(call(server,"state"))&&files[1].isFile(),"remote confirms prepared wallpaper atomically");byte[] applied=read(files[1]);check(!Arrays.equals(originals[1],applied),"confirmed file differs from previous wallpaper");
            BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;BitmapFactory.decodeFile(files[1].getPath(),dimensions);check(dimensions.outWidth==1600&&dimensions.outHeight==900,"stored image retains landscape dimensions");
            check(request(server,"GET","/status",auth(server),null).contains("\"state\":\"applied\""),"phone sees TV acceptance");
            request(server,"POST","/upload?scene=5",auth(server)+"Content-Type: image/jpeg\r\n",image);waitPreview();key(KeyEvent.KEYCODE_DPAD_CENTER);check(files[5].isFile()&&Arrays.equals(applied,read(files[1])),"another category can be uploaded in same session");
            for(int slot:new int[]{0,2,3,4})check(Arrays.equals(originals[slot],read(files[slot])),"other category preserved "+slot);
            final File stage=(File)field(receive,"stagingDirectory");key(KeyEvent.KEYCODE_BACK);check(!(Boolean)call(server,"isRunning"),"Back stops receiver immediately");boolean refused=false;try{request(server,"GET","/","",null);}catch(IOException expected){refused=true;}check(refused,"Back closes network port");
            long timeout=SystemClock.uptimeMillis()+8000;while(stage.exists()&&SystemClock.uptimeMillis()<timeout)SystemClock.sleep(100);check(!stage.exists(),"session staging cleaned after exit");check(find(dialog().getWindow().getDecorView(),"手机传壁纸")!=null,"Back returns to wallpaper actions");
            choose("手机传壁纸");final Object second=waitServer();check(!qr.equals(call(second,"qrAddress")),"reopening creates a new pairing session");
            check(request(second,"GET","/status",auth(server),null).startsWith("HTTP/1.1 401"),"old QR no longer authorizes new receiver");
            request(second,"POST","/upload?scene=1",auth(second)+"Content-Type: image/jpeg\r\n",image);waitPreview();
            ui(new Runnable(){public void run(){activity.startActivity(new Intent().setClassName("cn.wengmang.qiping.tests","cn.wengmang.qiping.tests.ProbeActivity"));}});TestKeys.awaitPackage(runner,"cn.wengmang.qiping.tests");
            check(!(Boolean)call(second,"isRunning")&&dialog()==null,"external app pause closes transfer screen");check(Arrays.equals(applied,read(files[1])),"unconfirmed image not applied when backgrounded");key(KeyEvent.KEYCODE_BACK);TestKeys.awaitPackage(runner,"cn.wengmang.qiping");check(dialog()==null,"returning does not reopen reception automatically");
            ui(new Runnable(){public void run(){prefs.edit().putInt("font_size",2).putInt("margin",2).commit();call(activity,"render",new Class<?>[]{String.class},"home_settings");call(activity,"showPhoneWallpaper",new Class<?>[]{int.class},0);}});final Object large=waitServer();snap("03-phone-large-font");
            check(dialog().getWindow().getDecorView().getWidth()<=activity.getWindow().getDecorView().getWidth()*.86+2,"receiver fits widest safe margins");
            ui(new Runnable(){public void run(){call(large,"stop",new Class<?>[]{String.class},"连接已到期，请重新连接");}});check(find(dialog().getWindow().getDecorView(),"重新连接")!=null,"expired receiver offers remote reconnect");choose("重新连接");Object renewed=waitServer();check(renewed!=large&&(Boolean)call(renewed,"isRunning"),"remote reconnect starts a fresh session");key(KeyEvent.KEYCODE_BACK);
        }finally{
            ui(new Runnable(){public void run(){call(activity,"dismissDialog");prefs.edit().putInt("font_size",oldFont).putInt("margin",oldMargin).commit();}});
            for(int i=0;i<6;i++){if(originals[i]==null)files[i].delete();else{files[i].getParentFile().mkdirs();FileOutputStream output=new FileOutputStream(files[i]);output.write(originals[i]);output.close();}}
            ui(new Runnable(){public void run(){call(field(activity,"scene"),"reloadWallpapers");call(activity,"render",new Class<?>[]{String.class},"dock0");}});
        }
    }
}
