package cn.wengmang.qiping;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** The dock shares the same cached wallpaper glass as cards and dialogs. */
final class GlassDock extends View {
    private final SceneView scene;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlassPanel.Surface surface=new GlassPanel.Surface();
    private final RectF face=new RectF();
    GlassDock(Context context,SceneView scene){super(context);this.scene=scene;setFocusable(false);}
    @Override protected void onDraw(Canvas canvas) {
        float unit=getHeight()/DockTile.CANVAS_HEIGHT;
        face.set(1,1,getWidth()-1,getHeight()-1);
        surface.draw(canvas,face,22*unit,scene,this,paint,0,unit,GlassPanel.DOCK_TINT);
    }
}
