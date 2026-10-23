package cn.wengmang.qiping;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Shared graphite glass. Reuses the bounded wallpaper cache, never a window capture. */
final class GlassPanel extends View {
    static final int TEXT=0xFFF2F3F5, SECONDARY=0xFFCAD0D9, ICON=0xFFF0F1F3;
    static final int TINT=0x98171C25, CARD_TINT=0x54171C25, DOCK_TINT=0x70141921, DIALOG_TINT=0xCB171C25;
    static final int FOCUS_MILLIS=140;
    private final SceneView scene;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Surface surface=new Surface();
    private final RectF face=new RectF();
    private int tint=TINT;
    GlassPanel(Context context,SceneView scene){super(context);this.scene=scene;setFocusable(false);scene.addGlass(this);}
    void setTint(int value){tint=value;invalidate();}
    @Override protected void onDraw(Canvas canvas) {
        float u=Math.min(scene.getWidth()/960f,scene.getHeight()/540f);
        face.set(1,1,getWidth()-1,getHeight()-1);
        surface.draw(canvas,face,Math.min(getHeight()/2,22*u),scene,this,paint,0,u,tint);
    }

    /** Per-view drawing state avoids allocating rectangles and gradients on animation frames. */
    static final class Surface {
        private final RectF shadow=new RectF();
        private final Matrix highlightMatrix=new Matrix();
        private final LinearGradient highlight=new LinearGradient(0,0,1,1,
                new int[]{0x14FFFFFF,0x03FFFFFF},null,Shader.TileMode.CLAMP);
        void draw(Canvas canvas,RectF rect,float radius,SceneView scene,View owner,Paint paint,float focus,float unit,int tint) {
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(0x20000000);
            shadow.set(rect);shadow.offset(0,2*unit);canvas.drawRoundRect(shadow,radius,radius,paint);
            scene.drawFrosted(canvas,rect,radius,owner);
            paint.setColor(scene.adaptiveTint(tint));canvas.drawRoundRect(rect,radius,radius,paint);
            highlightMatrix.setScale(rect.width(),rect.height());highlightMatrix.postTranslate(rect.left,rect.top);
            highlight.setLocalMatrix(highlightMatrix);paint.setColor(0xFFFFFFFF);paint.setShader(highlight);
            canvas.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);
            if(focus>0){paint.setColor(((int)(focus*18)<<24)|0xFFFFFF);canvas.drawRoundRect(rect,radius,radius,paint);}
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,unit));
            paint.setColor(((int)(31+focus*204)<<24)|0xF2F3F5);canvas.drawRoundRect(rect,radius,radius,paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
