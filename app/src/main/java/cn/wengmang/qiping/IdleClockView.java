package cn.wengmang.qiping;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Reuses the current wallpaper bitmap; refreshes once a minute, without animation. */
final class IdleClockView extends View {
    private final SceneView scene;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    IdleClockView(Context context,SceneView scene){super(context);this.scene=scene;setFocusable(false);setContentDescription("桌面屏保，按方向键或返回键退出");}
    @Override protected void onDraw(Canvas canvas){
        scene.drawBackdrop(canvas,false);canvas.drawColor(scene.adaptiveTint(0x37080c16));
        float unit=Math.min(getWidth()/960f,getHeight()/540f);long minute=System.currentTimeMillis()/60000;
        // Small periodic relocation avoids keeping the clock on exactly the same pixels.
        float x=getWidth()*.5f+(minute%3-1)*36*unit,y=getHeight()*.48f+(minute%2==0?-14:14)*unit;
        Date now=new Date();paint.setTextAlign(Paint.Align.CENTER);paint.setColor(0xfff5f7ff);paint.setTypeface(Typeface.create("sans-serif-light",0));paint.setTextSize(76*unit);paint.setShadowLayer(8*unit,0,2*unit,0x66000000);
        canvas.drawText(new SimpleDateFormat("HH:mm",Locale.CHINA).format(now),x,y,paint);
        paint.setTextSize(19*unit);paint.setColor(0xffe2e5ed);canvas.drawText(new SimpleDateFormat("M月d日  EEEE",Locale.CHINA).format(now),x,y+42*unit,paint);
        paint.setTextSize(13*unit);paint.setColor(0xcce5e8f0);canvas.drawText("按方向键或返回键回到桌面",getWidth()*.5f,getHeight()-48*unit,paint);
    }
}
