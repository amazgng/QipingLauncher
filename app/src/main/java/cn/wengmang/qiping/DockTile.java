package cn.wengmang.qiping;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Icon-only tile: its accessible name is intentionally not drawn in the dock. */
final class DockTile extends View {
    static final float CANVAS_WIDTH=168f, CANVAS_HEIGHT=120f;
    private static final float CARD_WIDTH=142f, CARD_HEIGHT=80f;
    private static final float FOCUS_GROWTH=.04f, GLYPH_SIZE=42f;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tile=new RectF(-CARD_WIDTH/2,-CARD_HEIGHT/2,CARD_WIDTH/2,CARD_HEIGHT/2);
    private final SceneView scene;
    private final GlassPanel.Surface surface=new GlassPanel.Surface();
    private final GlassSymbol glassIcon=new GlassSymbol();
    private final String glyph;
    private ValueAnimator animator;
    private float focusAmount, drawingScale;
    private boolean chosen, motion=true;
    private boolean updateAvailable;
    private final UpdateBadge updateBadge=new UpdateBadge();

    DockTile(Context context,SceneView scene,String glyph) {
        super(context);this.scene=scene;this.glyph=glyph;scene.addGlass(this);
        setDefaultFocusHighlightEnabled(false);setFocusable(true);setClickable(true);setFocusableInTouchMode(true);
    }
    void setMotion(boolean enabled) {motion=enabled;}
    void setUpdateAvailable(boolean value){updateAvailable=value;invalidate();}
    void finishAnimation(){if(animator!=null){animator.cancel();animator=null;}focusAmount=hasFocus()&&hasWindowFocus()?1:0;invalidate();}
    void setChosen(boolean value) {chosen=value;invalidate();}
    @Override protected void onFocusChanged(boolean gain,int direction,Rect rect) {
        super.onFocusChanged(gain,direction,rect);animateFocus();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);if(focused)animateFocus();else finishAnimation();
    }
    private void animateFocus() {
        float target=hasFocus()&&hasWindowFocus()?1:0;
        if(animator!=null)animator.cancel();
        if(!motion){focusAmount=target;invalidate();return;}
        animator=ValueAnimator.ofFloat(focusAmount,target);animator.setDuration(GlassPanel.FOCUS_MILLIS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            @Override public void onAnimationUpdate(ValueAnimator a){focusAmount=(Float)a.getAnimatedValue();invalidate();}
        });animator.start();
    }
    @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        // The slot may stretch with the screen. Its artwork must not stretch.
        drawingScale=Math.min(w/CANVAS_WIDTH,h/CANVAS_HEIGHT);
    }
    @Override protected void onDraw(Canvas c) {
        if(drawingScale<=0)return;
        float scale=drawingScale*(1f+FOCUS_GROWTH*focusAmount);
        float cx=getWidth()/2f,cy=getHeight()/2f;
        tile.set(cx-CARD_WIDTH*scale/2,cy-CARD_HEIGHT*scale/2,cx+CARD_WIDTH*scale/2,cy+CARD_HEIGHT*scale/2);
        surface.draw(c,tile,14*scale,scene,this,paint,focusAmount,scale,GlassPanel.CARD_TINT);
        if(chosen){paint.setColor(0x07FFFFFF);c.drawRoundRect(tile,14*scale,14*scale,paint);}
        float icon=GLYPH_SIZE*scale;glassIcon.draw(c,glyph,scene,this,cx-icon/2,cy-icon/2,icon,focusAmount);
        if(isPressed()){paint.setColor(0x20000000);c.drawRoundRect(tile,14*scale,14*scale,paint);}
        if(updateAvailable)updateBadge.draw(c,tile.left+6*scale,tile.top+6*scale,scale);
    }
}
