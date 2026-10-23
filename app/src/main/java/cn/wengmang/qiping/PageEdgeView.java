package cn.wengmang.qiping;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/** A quiet, non-interactive overflow cue. Paging never requires focusing this view. */
final class PageEdgeView extends View {
    private final SceneView scene;
    private final GlassSymbol glassIcon=new GlassSymbol();
    private final boolean right;
    PageEdgeView(Context context,SceneView scene,boolean right){super(context);this.scene=scene;this.right=right;setFocusable(false);setClickable(false);scene.addGlass(this);}
    @Override protected void onDraw(Canvas c) {
        float size=Math.min(getHeight(),getWidth()*2f);
        glassIcon.draw(c,right?"next":"back",scene,this,(getWidth()-size)/2,(getHeight()-size)/2,size,0);
    }
}
