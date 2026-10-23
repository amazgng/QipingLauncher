package cn.wengmang.qiping;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Wallpaper-backed glass glyph. No frame captures, bitmap allocation or blur on the UI thread. */
final class GlassSymbol {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape=new Path();
    private final Path window=new Path(),windowSource=new Path();
    private final Matrix transform=new Matrix();
    private final LinearGradient sheen=new LinearGradient(0,0,36,48,
            new int[]{0xB8F7F8FA,0x70F0F1F3,0x48E7E8EB,0xA5FAFBFC},
            new float[]{0,.34f,.66f,1},Shader.TileMode.CLAMP);
    private final LinearGradient rim=new LinearGradient(0,0,34,48,
            new int[]{0xFFF3FAFF,0x9DDDEBFA,0xC6ECF5FF,0xF0E1EEFA},
            new float[]{0,.42f,.7f,1},Shader.TileMode.CLAMP);
    private String lastKind;
    private float lastX=Float.NaN,lastY,lastSize;

    /** Coordinates are in the owner's untransformed Canvas, including for dialog-window owners. */
    void draw(Canvas canvas,String kind,SceneView scene,View owner,float x,float y,float size,float focus) {
        if(size<=0)return;
        if(!kind.equals(lastKind)||x!=lastX||y!=lastY||size!=lastSize) {
            transform.setScale(size/48,size/48);transform.postTranslate(x,y);
            Symbols.shape(kind).transform(transform,shape);
            windowSource.reset();
            if("live".equals(kind))windowSource.addRoundRect(5,12,43,37,4,4,Path.Direction.CW);
            else if("album".equals(kind))windowSource.addRoundRect(3,7,45,41,4,4,Path.Direction.CW);
            else if("desktop".equals(kind))windowSource.addRoundRect(4,7,44,34,4,4,Path.Direction.CW);
            windowSource.transform(transform,window);
            sheen.setLocalMatrix(transform);rim.setLocalMatrix(transform);
            lastKind=kind;lastX=x;lastY=y;lastSize=size;
        }
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(0x60050A13);
        canvas.save();canvas.translate(0,Math.max(.7f,size*.022f));canvas.drawPath(shape,paint);canvas.restore();
        float contrast=scene.contrastStrength();
        if(contrast>0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1.4f,size*.045f));paint.setColor((Math.round(130*contrast)<<24)|0x10151D);canvas.drawPath(shape,paint);paint.setStyle(Paint.Style.FILL);}
        if(!window.isEmpty()) {
            scene.drawGlassGlyph(canvas,window,owner);
            paint.setColor(0x28F0F1F3);canvas.drawPath(window,paint);
        }
        scene.drawGlassGlyph(canvas,shape,owner);
        paint.setColor(0xFFFFFFFF);paint.setShader(sheen);canvas.drawPath(shape,paint);
        paint.setShader(null);
        if(focus>0){paint.setColor((Math.round(18*focus)<<24)|0xFFFFFF);canvas.drawPath(shape,paint);}
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(.65f,size*.018f));
        paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(0xFFFFFFFF);paint.setShader(rim);
        canvas.drawPath(shape,paint);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
    }
}
