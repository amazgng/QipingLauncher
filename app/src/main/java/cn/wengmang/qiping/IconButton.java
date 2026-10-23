package cn.wengmang.qiping;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

final class IconButton extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SceneView scene;
    private final GlassSymbol glassIcon=new GlassSymbol();
    private final GlassPanel.Surface surface=new GlassPanel.Surface();
    private final RectF face=new RectF();
    private String glyph;
    IconButton(Context context,SceneView scene,String glyph,String label) {
        super(context);this.scene=scene;this.glyph=glyph;setContentDescription(label);scene.addGlass(this);
        setDefaultFocusHighlightEnabled(false);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
    }
    void setGlyph(String value){glyph=value;invalidate();}
    @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);invalidate();}
    @Override protected void onDraw(Canvas c) {
        float size=Math.min(getWidth(),getHeight());
        boolean focused=isFocused()&&hasWindowFocus();
        float cx=getWidth()/2f,cy=getHeight()/2f,radius=size*23/48;
        face.set(cx-radius,cy-radius,cx+radius,cy+radius);
        surface.draw(c,face,radius,scene,this,paint,focused?1:0,size/48,GlassPanel.CARD_TINT);
        float glyphSize=size*.625f;
        glassIcon.draw(c,glyph,scene,this,cx-glyphSize/2,cy-glyphSize/2,glyphSize,focused?1:0);
        if(isPressed()){paint.setColor(0x24000000);c.drawCircle(cx,cy,radius,paint);}
    }
}
