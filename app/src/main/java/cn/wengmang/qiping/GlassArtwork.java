package cn.wengmang.qiping;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;

/** Gives installed artwork a translucent finish without replacing its recognizable app identity. */
final class GlassArtwork {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Matrix imageMatrix=new Matrix(),sheenMatrix=new Matrix();
    private final LinearGradient sheen=new LinearGradient(0,0,1,1,
            new int[]{0x38FFFFFF,0x06FFFFFF,0x00FFFFFF,0x18FFFFFF},
            new float[]{0,.36f,.65f,1},Shader.TileMode.CLAMP);
    private Bitmap image;
    private BitmapShader mask;
    private ComposeShader gloss;
    void clear(){image=null;mask=null;gloss=null;paint.setShader(null);}
    void draw(Canvas canvas,Bitmap bitmap,RectF rect) {
        if(image!=bitmap){image=bitmap;mask=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);gloss=new ComposeShader(mask,sheen,PorterDuff.Mode.SRC_IN);}
        paint.setShader(null);paint.setColor(0xE8FFFFFF);canvas.drawBitmap(bitmap,null,rect,paint);
        imageMatrix.setScale(rect.width()/bitmap.getWidth(),rect.height()/bitmap.getHeight());imageMatrix.postTranslate(rect.left,rect.top);mask.setLocalMatrix(imageMatrix);
        sheenMatrix.setScale(rect.width(),rect.height());sheenMatrix.postTranslate(rect.left,rect.top);sheen.setLocalMatrix(sheenMatrix);
        paint.setColor(0xFFFFFFFF);paint.setShader(gloss);canvas.drawRect(rect,paint);paint.setShader(null);
    }
}
