package cn.wengmang.qiping;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.SystemClock;
import java.io.*;

/** Owned, size-bounded images. Atomic replacement keeps the previous wallpaper on import failure. */
final class WallpaperStore {
    static final int MAX_BYTES=20*1024*1024,MAX_EDGE=1920;
    interface Cancellation {boolean cancelled();}
    private final Context context;private final File directory;
    WallpaperStore(Context context){this(context,new File(context.getFilesDir(),"wallpapers"));}
    WallpaperStore(Context context,File directory){this.context=context.getApplicationContext();this.directory=directory;}
    File file(int scene){if(scene<0||scene>5)throw new IllegalArgumentException();return new File(directory,"scene-"+scene+".jpg");}
    boolean has(int scene){return file(scene).isFile();}
    synchronized boolean reset(int scene){File file=file(scene);return !file.exists()||file.delete();}
    /** Only accepts a private JPEG already prepared by this class in the receiver's staging store. */
    synchronized boolean adoptPrepared(int scene,File prepared){
        if(!prepared.isFile()||prepared.length()==0)return false;
        if(!directory.isDirectory()&&!directory.mkdirs())return false;
        return prepared.renameTo(file(scene));
    }
    void importUri(int scene,Uri uri,Cancellation cancel)throws IOException {
        if(uri==null||(!"content".equals(uri.getScheme())&&!"file".equals(uri.getScheme())))throw new IOException("所选文件位置无法读取");
        InputStream input=context.getContentResolver().openInputStream(uri);
        if(input==null)throw new IOException("无法读取图片，请重新选择");
        try{importStream(scene,input,cancel);}finally{input.close();}
    }
    synchronized void importStream(int scene,InputStream input,final Cancellation cancel)throws IOException {
        File target=file(scene);if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("存储空间不足，无法保存壁纸");
        File original=File.createTempFile("import-",".tmp",directory),output=File.createTempFile("ready-",".tmp",directory);Bitmap bitmap=null,flat=null;
        try {
            long started=SystemClock.elapsedRealtime();int count=0,n;byte[] buffer=new byte[16384];
            FileOutputStream copy=new FileOutputStream(original);try{while((n=input.read(buffer))!=-1){check(cancel);count+=n;if(count>MAX_BYTES)throw new IOException("图片超过 20 MB，请换一张较小的图片");if(SystemClock.elapsedRealtime()-started>30000)throw new IOException("读取图片超时，请重试");copy.write(buffer,0,n);}}finally{copy.close();}
            check(cancel);if(count==0)throw new IOException("所选图片为空");
            bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(original),new ImageDecoder.OnHeaderDecodedListener(){public void onHeaderDecoded(ImageDecoder decoder,ImageDecoder.ImageInfo info,ImageDecoder.Source source){
                int width=info.getSize().getWidth(),height=info.getSize().getHeight();
                if(width<=0||height<=0||(long)width*height>100000000L)throw new IllegalArgumentException("图片尺寸过大");
                float scale=Math.min(1f,MAX_EDGE/(float)Math.max(width,height));decoder.setTargetSize(Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            }});
            check(cancel);
            if(bitmap.hasAlpha()){flat=Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.RGB_565);Canvas canvas=new Canvas(flat);canvas.drawColor(0xff15202c);canvas.drawBitmap(bitmap,0,0,null);}
            FileOutputStream encoded=new FileOutputStream(output);try{if(!(flat==null?bitmap:flat).compress(Bitmap.CompressFormat.JPEG,90,encoded))throw new IOException("图片无法转换");encoded.flush();encoded.getFD().sync();}finally{encoded.close();}
            check(cancel);if(!output.renameTo(target))throw new IOException("壁纸保存失败，请检查存储空间");
        }catch(OutOfMemoryError error){throw new IOException("图片过大，内存不足，请换一张较小的图片");}
        catch(RuntimeException error){throw new IOException("图片无法解码，请选择有效的 JPG、PNG 或 WebP 图片");}
        finally {original.delete();output.delete();if(bitmap!=null)bitmap.recycle();if(flat!=null)flat.recycle();}
    }
    private static void check(Cancellation cancel)throws IOException{if(Thread.currentThread().isInterrupted()||(cancel!=null&&cancel.cancelled()))throw new IOException("已取消导入");}
}
