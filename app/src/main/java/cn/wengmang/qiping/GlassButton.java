package cn.wengmang.qiping;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Compact, consistently frosted controls; symbols have accessible labels. */
final class GlassButton extends FrameLayout {
    private final SceneView scene;
    private final String glyph;
    private boolean chosen;
    private int tint=GlassPanel.TINT;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlassPanel.Surface surface=new GlassPanel.Surface();
    private final GlassSymbol glassIcon=new GlassSymbol();
    private final RectF rect=new RectF();
    GlassButton(Context context,SceneView scene,String glyph,TextView label) {
        super(context);this.scene=scene;this.glyph=glyph;
        setWillNotDraw(false);setDefaultFocusHighlightEnabled(false);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        if(label!=null){label.setGravity(Gravity.CENTER);addView(label,new FrameLayout.LayoutParams(-1,-1));}
        scene.addGlass(this);
    }
    void setChosen(boolean value){chosen=value;invalidate();}
    void setTint(int value){tint=value;invalidate();}
    @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);invalidate();}
    @Override protected void onDraw(Canvas canvas) {
        float unit=getHeight()/44f,inset=3*unit;
        rect.set(inset,inset,getWidth()-inset,getHeight()-inset);
        float radius=glyph!=null?rect.height()/2:12*unit;
        surface.draw(canvas,rect,radius,scene,this,paint,isFocused()&&hasWindowFocus()?1:0,unit,tint);
        // Saved choices have a quiet fill and check mark, never a second focus ring.
        if(chosen){paint.setColor(0x0AFFFFFF);canvas.drawRoundRect(rect,radius,radius,paint);}
        if(glyph!=null) {
            float size=23*unit;glassIcon.draw(canvas,glyph,scene,this,(getWidth()-size)/2,(getHeight()-size)/2,size,isFocused()&&hasWindowFocus()?1:0);
        }
        if(isPressed()){paint.setColor(0x24000000);canvas.drawRoundRect(rect,radius,radius,paint);}
    }
}
