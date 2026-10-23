package cn.wengmang.qiping;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/** A floating glass app card. The caption remains a real accessible TextView. */
final class AppTileView extends ViewGroup {
    private final SceneView scene;
    private Bitmap icon;
    private final boolean pinned,motion;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final TextView caption;
    private final GlassPanel.Surface surface=new GlassPanel.Surface();
    private final RectF face=new RectF(),iconRect=new RectF();
    private final GlassSymbol glassIcon=new GlassSymbol();
    private final GlassArtwork glassArtwork=new GlassArtwork();
    private ValueAnimator animator;
    private float focusAmount;
    private boolean updateAvailable;
    private boolean menuTarget;
    private final UpdateBadge updateBadge=new UpdateBadge();

    AppTileView(Context context,SceneView scene,Bitmap icon,String label,boolean pinned,boolean large,boolean motion,float unit) {
        super(context);this.scene=scene;this.icon=icon;this.pinned=pinned;this.motion=motion;
        setWillNotDraw(false);setDefaultFocusHighlightEnabled(false);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);setLongClickable(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        caption=new TextView(context);caption.setText(label);caption.setTextColor(GlassPanel.SECONDARY);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_PX,(large?18:16)*unit*DisplayPreferences.extraScale(context));
        caption.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));caption.setIncludeFontPadding(false);
        caption.setGravity(Gravity.CENTER);caption.setSingleLine();caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setShadowLayer(unit*2,0,unit,0x88000000);caption.setFocusable(false);
        addView(caption);scene.addGlass(this);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);setMeasuredDimension(w,h);
        caption.measure(MeasureSpec.makeMeasureSpec(w-8,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(h*.23f),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        caption.layout(4,Math.round(getHeight()*.77f),getWidth()-4,getHeight());
    }
    @Override protected void onFocusChanged(boolean gain,int direction,Rect oldRect) {
        super.onFocusChanged(gain,direction,oldRect);animateFocus();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);if(focused)animateFocus();else finishAnimation();
    }
    private void animateFocus() {
        boolean gain=isFocused()&&hasWindowFocus();caption.setTextColor(gain?GlassPanel.TEXT:GlassPanel.SECONDARY);
        if(animator!=null)animator.cancel();
        if(!motion){focusAmount=gain?1:0;invalidate();return;}
        animator=ValueAnimator.ofFloat(focusAmount,gain?1:0);animator.setDuration(GlassPanel.FOCUS_MILLIS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){@Override public void onAnimationUpdate(ValueAnimator value){focusAmount=(Float)value.getAnimatedValue();invalidate();}});
        animator.start();
    }
    @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
    void finishAnimation(){if(animator!=null){animator.cancel();animator=null;}focusAmount=isFocused()&&hasWindowFocus()?1:0;caption.setTextColor(focusAmount>0?GlassPanel.TEXT:GlassPanel.SECONDARY);invalidate();}
    void releaseIcon(){icon=null;glassArtwork.clear();invalidate();}
    void setUpdateAvailable(boolean value){updateAvailable=value;invalidate();}
    void setMenuTarget(boolean value){menuTarget=value;invalidate();}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas) {
        float unit=Math.min(getWidth()/144f,getHeight()/138f),grow=2.4f*unit*focusAmount;
        face.set(12*unit-grow,10*unit-grow,getWidth()-12*unit+grow,getHeight()*.73f+grow);
        surface.draw(canvas,face,17*unit,scene,this,paint,focusAmount,unit,GlassPanel.TINT);
        if(menuTarget&&!hasWindowFocus()){paint.setColor(0x12FFFFFF);canvas.drawRoundRect(face,17*unit,17*unit,paint);}
        float size=58*unit*(1+focusAmount*.04f),cx=face.centerX(),cy=face.centerY();
        if(icon!=null) {
            iconRect.set(cx-size/2,cy-size/2,cx+size/2,cy+size/2);glassArtwork.draw(canvas,icon,iconRect);
        } else {
            glassIcon.draw(canvas,"grid",scene,this,cx-size*.34f,cy-size*.34f,size*.68f,focusAmount);
        }
        if(updateAvailable)updateBadge.draw(canvas,face.left+5*unit,face.top+5*unit,unit);
        if(pinned) {
            float x=face.right-11*unit,y=face.top+11*unit;
            paint.setColor(GlassPanel.ICON);canvas.drawCircle(x,y,4*unit,paint);
            paint.setColor(0xFF515965);canvas.drawCircle(x,y,1.7f*unit,paint);
        }
        if(isPressed()){paint.setColor(0x24000000);canvas.drawRoundRect(face,17*unit,17*unit,paint);}
    }
}
