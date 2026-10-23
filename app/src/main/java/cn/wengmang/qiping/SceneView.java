package cn.wengmang.qiping;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Process;
import android.view.View;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded, latest-request-only background decoding; published bitmaps are never manually recycled. */
final class SceneView extends View {
    static final int SETTINGS_SCENE=5;
    private static final int[] RES={R.drawable.wall_film,R.drawable.wall_live,R.drawable.wall_music,R.drawable.wall_album,R.drawable.wall_apps,R.drawable.wall_settings};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Handler handler=new Handler();
    private final ThreadPoolExecutor loader=new ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(1),new ThreadFactory(){
        @Override public Thread newThread(final Runnable runnable){return new Thread(new Runnable(){
            @Override public void run(){Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);runnable.run();}
        },"qiping-wallpaper");}
    },new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LinkedHashMap<Integer,Asset> cache=new LinkedHashMap<Integer,Asset>(4,.75f,true);
    private final WeakHashMap<View,FrostShaders> frostShaders=new WeakHashMap<View,FrostShaders>();
    private PerformancePolicy policy;
    private final WallpaperStore wallpapers;
    private Asset current,previous;
    private int currentIndex=4,previousIndex=4,wanted=-1;
    private volatile int request;
    private volatile boolean closed,active=true;
    private float blend=1;
    private boolean motion=true,libraryMode,settingsMode;
    private ValueAnimator fade;
    private Runnable scheduled;
    private final List<View> glasses=new ArrayList<View>();
    private final Matrix frostMatrix=new Matrix();
    private final RectF imageRect=new RectF();
    private final int[] scenePosition=new int[2],viewPosition=new int[2];
    private LinearGradient leftShade,bottomShade,overlayShade,adaptiveLeft,adaptiveBottom,adaptiveTop;
    private final LinearGradient[] fallback=new LinearGradient[RES.length];

    private static final class Asset {
        final Bitmap image,blurred;final float contrast;
        Asset(Bitmap image,Bitmap blurred){this.image=image;this.blurred=blurred;int[] samples=new int[144];
            for(int y=0;y<9;y++)for(int x=0;x<16;x++)samples[y*16+x]=blurred.getPixel(Math.min(blurred.getWidth()-1,(2*x+1)*blurred.getWidth()/32),Math.min(blurred.getHeight()-1,(2*y+1)*blurred.getHeight()/18));
            contrast=GlassContrast.strength(samples);}
        long bytes(){return (long)image.getRowBytes()*image.getHeight()+(long)blurred.getRowBytes()*blurred.getHeight();}
        void discard(){image.recycle();blurred.recycle();} // Only before publication to Canvas/cache.
    }
    private static final class FrostShaders {
        Asset first,second;
        BitmapShader a,b;
        BitmapShader shader(Asset asset) {
            if(first==asset)return a;if(second==asset)return b;
            BitmapShader result=new BitmapShader(asset.blurred,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            if(first==null){first=asset;a=result;}else{second=asset;b=result;}return result;
        }
    }
    SceneView(Context context,PerformancePolicy policy) {
        super(context);setFocusable(false);this.policy=policy;wallpapers=new WallpaperStore(context);loader.allowCoreThreadTimeOut(true);
    }
    void reloadWallpapers(){int target=wanted>=0?wanted:currentIndex;cancelLoads();finishAnimation();cache.clear();frostShaders.clear();wanted=-1;showScene(target,false);}
    void setPolicy(PerformancePolicy next) {
        if(policy.sameLimits(next))return;
        policy=next;cancelLoads();finishAnimation();cache.clear();frostShaders.clear();wanted=-1;
        // Keep the current frame only until its smaller replacement has decoded.
    }
    void setGlass(View view){glasses.clear();frostShaders.clear();if(view!=null)glasses.add(view);}
    void addGlass(View view){if(!glasses.contains(view))glasses.add(view);}
    void removeGlass(View view){glasses.remove(view);frostShaders.remove(view);}
    void setLibraryMode(boolean value){libraryMode=value;invalidate();}
    void setSettingsMode(boolean value){settingsMode=value;invalidate();}
    void setMotion(boolean enabled){motion=enabled;if(!enabled)finishAnimation();}
    void finishAnimation(){if(fade!=null){fade.cancel();fade=null;}blend=1;previous=null;frostShaders.clear();paint.setShader(null);refresh();}
    private void cancelLoads(){request++;if(scheduled!=null)handler.removeCallbacks(scheduled);scheduled=null;loader.getQueue().clear();}
    void pause() {
        active=false;cancelLoads();finishAnimation();cache.clear();
        if(current!=null)cache.put(currentIndex,current);
    }
    void releaseHidden() {
        pause();cache.clear();current=null;previous=null;frostShaders.clear();paint.setShader(null);refresh();
    }
    void resume() {
        if(closed)return;
        active=true;
        int index=wanted>=0?wanted:currentIndex;
        wanted=-1;showScene(index,false);
    }
    void trimMemory() {
        int target=wanted>=0?wanted:currentIndex;
        cancelLoads();finishAnimation();cache.clear();
        // If a pending scene was cancelled, keep the selected category's fallback rather than an old category's image.
        if(target!=currentIndex){current=null;currentIndex=target;}
        if(current!=null)cache.put(currentIndex,current);
        wanted=-1;refresh();
    }
    long cachedBytes(){long bytes=0;for(Asset asset:cache.values())bytes+=asset.bytes();return bytes;}
    int cachedSceneCount(){return cache.size();}
    int registeredGlassCount(){return glasses.size();}
    int queuedLoads(){return loader.getQueue().size();}
    float contrastStrength(){float now=current==null?0:current.contrast,old=previous==null?now:previous.contrast;return old+(now-old)*blend;}
    int adaptiveTint(int tint){return GlassContrast.tint(tint,contrastStrength());}
    boolean isActive(){return active;}
    private void trimCache() {
        Iterator<Map.Entry<Integer,Asset>> it=cache.entrySet().iterator();
        long bytes=cachedBytes();
        while(it.hasNext()&&(cache.size()>policy.sceneCount||bytes>policy.sceneBytes)){
            Map.Entry<Integer,Asset> entry=it.next();bytes-=entry.getValue().bytes();it.remove();
        }
    }
    void showScene(final int index,final boolean animate) {
        if(closed||index<0||index>=RES.length)return;
        if(index==wanted)return;
        wanted=index;cancelLoads();
        if(!active)return;
        final int token=request;
        Asset found=cache.get(index);
        if(found!=null){apply(index,found,animate);return;}
        final PerformancePolicy limits=policy;
        scheduled=new Runnable(){@Override public void run(){
            scheduled=null;
            if(!valid(token))return;
            try {loader.execute(new Runnable(){@Override public void run(){
                if(!valid(token))return;
                final Asset loaded=decode(index,limits,token);
                if(!valid(token)){if(loaded!=null)loaded.discard();return;}
                handler.post(new Runnable(){@Override public void run(){
                    if(!valid(token)){if(loaded!=null)loaded.discard();return;}
                    if(loaded!=null){cache.put(index,loaded);trimCache();}
                    apply(index,loaded,animate);
                }});
            }});}catch(RejectedExecutionException ignored) { /* Activity is closing. */ }
        }};
        // A fast sweep across the dock should decode the final selection, not every intermediate image.
        handler.postDelayed(scheduled,animate?(limits.light?90:40):0);
    }
    private boolean valid(int token){return !closed&&active&&token==request;}
    private Asset decode(int index,PerformancePolicy limits,int token) {
        for(int attempt=0;attempt<2;attempt++) {
            Bitmap bitmap=null,blurred=null;
            try {
                if(!valid(token))return null;
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;bounds.inScaled=false;
                boolean custom=wallpapers.has(index);
                if(custom)BitmapFactory.decodeFile(wallpapers.file(index).getPath(),bounds);
                if(!custom||bounds.outWidth<=0||bounds.outHeight<=0){custom=false;BitmapFactory.decodeResource(getResources(),RES[index],bounds);}
                BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
                options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                options.inSampleSize=PerformancePolicy.sampleSize(bounds.outWidth,bounds.outHeight,Math.max(1,limits.wallpaperWidth>>attempt));
                bitmap=custom?BitmapFactory.decodeFile(wallpapers.file(index).getPath(),options):BitmapFactory.decodeResource(getResources(),RES[index],options);
                if(bitmap==null&&custom)bitmap=BitmapFactory.decodeResource(getResources(),RES[index],options);
                if(bitmap==null)return null;
                if(!valid(token)){bitmap.recycle();return null;}
                blurred=blur(bitmap,limits.blurWidth);
                return new Asset(bitmap,blurred);
            } catch(OutOfMemoryError error) {
                if(bitmap!=null)bitmap.recycle();if(blurred!=null)blurred.recycle();
                RunDiagnostics.get(getContext()).event("壁纸内存", "解码不足，尝试较小图片 · 场景 "+index);
                // One smaller decode attempt; repeated allocation failure leaves the gradient fallback.
            } catch(RuntimeException error) {
                if(bitmap!=null)bitmap.recycle();if(blurred!=null)blurred.recycle();RunDiagnostics.get(getContext()).event("壁纸读取",error.getClass().getSimpleName()+" · 场景 "+index);return null;
            }
        }
        return null;
    }
    private void apply(int index,Asset asset,boolean animate) {
        if(fade!=null){fade.cancel();fade=null;}
        frostShaders.clear();previous=current;previousIndex=currentIndex;current=asset;currentIndex=index;
        if(!motion||!animate||previous==current||previous==null){blend=1;previous=null;refresh();return;}
        blend=0;fade=ValueAnimator.ofFloat(0,1);fade.setDuration(policy.fadeMillis);
        fade.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            @Override public void onAnimationUpdate(ValueAnimator a){
                blend=(Float)a.getAnimatedValue();
                if(blend>=1){previous=null;frostShaders.clear();}
                refresh();
            }
        });fade.start();
    }
    private void refresh(){invalidate();for(View view:glasses)view.invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);if(w<1||h<1)return;
        leftShade=new LinearGradient(0,0,w*.76f,0,new int[]{0x900C1016,0x000C1016},null,Shader.TileMode.CLAMP);
        bottomShade=new LinearGradient(0,h*.6f,0,h,new int[]{0x000C1016,0x580C1016},null,Shader.TileMode.CLAMP);
        overlayShade=new LinearGradient(0,0,0,h,new int[]{0x180C1016,0x580C1016},null,Shader.TileMode.CLAMP);
        adaptiveLeft=new LinearGradient(0,0,w*.80f,0,new int[]{0x900C1016,0x000C1016},null,Shader.TileMode.CLAMP);
        adaptiveBottom=new LinearGradient(0,h*.38f,0,h,new int[]{0x000C1016,0xB00C1016},null,Shader.TileMode.CLAMP);
        adaptiveTop=new LinearGradient(0,0,0,h*.32f,new int[]{0xB80C1016,0xA80C1016,0x000C1016},new float[]{0,.45f,1},Shader.TileMode.CLAMP);
        for(int i=0;i<RES.length;i++) {
            int[] colors=i==3?new int[]{0xFF86745F,0xFF2C2522}:i==2?new int[]{0xFF544C6D,0xFF191C30}:i==1?new int[]{0xFF3B6571,0xFF102333}:new int[]{0xFF667582,0xFF152331};
            fallback[i]=new LinearGradient(w,0,0,h,colors,null,Shader.TileMode.CLAMP);
        }
        frostShaders.clear();
    }
    @Override protected void onDraw(Canvas c) {
        drawBackdrop(c,false);paint.setAlpha(255);
        paint.setShader(leftShade);c.drawRect(0,0,getWidth(),getHeight(),paint);
        paint.setShader(bottomShade);c.drawRect(0,0,getWidth(),getHeight(),paint);
        if(libraryMode||settingsMode){paint.setShader(overlayShade);c.drawRect(0,0,getWidth(),getHeight(),paint);}
        paint.setAlpha(Math.round(255*contrastStrength()));paint.setShader(adaptiveLeft);c.drawRect(0,0,getWidth(),getHeight(),paint);
        paint.setShader(adaptiveBottom);c.drawRect(0,0,getWidth(),getHeight(),paint);
        paint.setShader(adaptiveTop);c.drawRect(0,0,getWidth(),getHeight(),paint);
        // App captions and page notes sit outside glass cards, including at the bright right edge.
        if(libraryMode||settingsMode){paint.setShader(null);paint.setColor((Math.round(112*contrastStrength())<<24)|0x0C1016);c.drawRect(0,0,getWidth(),getHeight(),paint);}
        paint.setShader(null);paint.setAlpha(255);
    }
    void drawBackdrop(Canvas c,boolean frosted) {
        paint.setShader(null);paint.setAlpha(255);c.drawColor(0xFF111B30);
        if(blend<1)drawScene(c,previous,previousIndex,255,frosted);
        drawScene(c,current,currentIndex,Math.round(blend*255),frosted);
        paint.setAlpha(255);paint.setShader(null);
    }
    void drawFrosted(Canvas canvas,RectF rect,float radius,View owner) {
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(0xFF1B2948);
        canvas.drawRoundRect(rect,radius,radius,paint);
        getLocationOnScreen(scenePosition);owner.getLocationOnScreen(viewPosition);
        float x=viewPosition[0]-scenePosition[0],y=viewPosition[1]-scenePosition[1];
        if(blend<1)drawFrostedAsset(canvas,rect,radius,previous,x,y,255,owner);
        drawFrostedAsset(canvas,rect,radius,current,x,y,Math.round(255*blend),owner);
        paint.setShader(null);paint.setAlpha(255);
    }
    /** Same scene crop and crossfade as the backdrop; each icon is a real wallpaper-backed path. */
    void drawGlassGlyph(Canvas canvas,Path path,View owner) {
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF3A4859);
        canvas.drawPath(path,paint);
        getLocationOnScreen(scenePosition);owner.getLocationOnScreen(viewPosition);
        float x=viewPosition[0]-scenePosition[0],y=viewPosition[1]-scenePosition[1];
        if(blend<1)drawGlyphAsset(canvas,path,previous,x,y,255,owner);
        drawGlyphAsset(canvas,path,current,x,y,Math.round(255*blend),owner);
        paint.setShader(null);paint.setAlpha(255);
    }
    private void drawGlyphAsset(Canvas canvas,Path path,Asset asset,float x,float y,int alpha,View owner) {
        if(asset==null||alpha<=0)return;
        paint.setShader(frostShader(asset,x,y,owner));paint.setAlpha(alpha);canvas.drawPath(path,paint);
    }
    private void drawFrostedAsset(Canvas canvas,RectF rect,float radius,Asset asset,float x,float y,int alpha,View owner) {
        if(asset==null||alpha<=0)return;
        paint.setShader(frostShader(asset,x,y,owner));paint.setAlpha(alpha);canvas.drawRoundRect(rect,radius,radius,paint);
    }
    private BitmapShader frostShader(Asset asset,float x,float y,View owner) {
        float scale=Math.max(getWidth()/(float)asset.blurred.getWidth(),getHeight()/(float)asset.blurred.getHeight());
        frostMatrix.setScale(scale,scale);
        frostMatrix.postTranslate((getWidth()-asset.blurred.getWidth()*scale)/2-x,(getHeight()-asset.blurred.getHeight()*scale)/2-y);
        FrostShaders pair=frostShaders.get(owner);if(pair==null){pair=new FrostShaders();frostShaders.put(owner,pair);}
        // Each owner retains its own shader: another card cannot overwrite its recorded transform.
        BitmapShader shader=pair.shader(asset);shader.setLocalMatrix(frostMatrix);
        return shader;
    }
    private void drawScene(Canvas c,Asset asset,int index,int alpha,boolean frosted) {
        float w=getWidth(),h=getHeight();paint.setAlpha(alpha);
        if(asset!=null) {
            Bitmap bitmap=frosted?asset.blurred:asset.image;
            float s=Math.max(w/bitmap.getWidth(),h/bitmap.getHeight());float bw=bitmap.getWidth()*s,bh=bitmap.getHeight()*s;
            imageRect.set((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2);c.drawBitmap(bitmap,null,imageRect,paint);
        } else {
            paint.setShader(fallback[index]);paint.setAlpha(alpha);c.drawRect(0,0,w,h,paint);paint.setShader(null);
        }
        paint.setAlpha(255);
    }
    private static Bitmap blur(Bitmap source,int maxSide) {
        float scale=Math.min(1,maxSide/(float)Math.max(source.getWidth(),source.getHeight()));
        int w=Math.max(1,Math.round(source.getWidth()*scale)),h=Math.max(1,Math.round(source.getHeight()*scale));
        Bitmap small=Bitmap.createScaledBitmap(source,w,h,true);
        try {
            int[] pixels=new int[w*h],temp=new int[w*h];small.getPixels(pixels,0,w,0,0,w,h);
            final int radius=4;
            for(int pass=0;pass<2;pass++) {
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                    int r=0,g=0,b=0,n=0;
                    for(int d=-radius;d<=radius;d++){int color=pixels[y*w+Math.max(0,Math.min(w-1,x+d))];r+=(color>>16)&255;g+=(color>>8)&255;b+=color&255;n++;}
                    temp[y*w+x]=0xFF000000|(r/n<<16)|(g/n<<8)|b/n;
                }
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                    int r=0,g=0,b=0,n=0;
                    for(int d=-radius;d<=radius;d++){int color=temp[Math.max(0,Math.min(h-1,y+d))*w+x];r+=(color>>16)&255;g+=(color>>8)&255;b+=color&255;n++;}
                    pixels[y*w+x]=0xFF000000|(r/n<<16)|(g/n<<8)|b/n;
                }
            }
            return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
        } finally {if(small!=source)small.recycle();}
    }
    void close(){closed=true;releaseHidden();loader.shutdownNow();handler.removeCallbacksAndMessages(null);glasses.clear();}
}
