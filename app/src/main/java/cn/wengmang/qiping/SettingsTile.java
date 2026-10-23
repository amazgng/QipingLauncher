package cn.wengmang.qiping;

import android.animation.ValueAnimator;
import android.content.Context;
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

/** A television-sized preference card, using the same glass as the home dock. */
final class SettingsTile extends ViewGroup {
    private final SceneView scene;
    private final String glyph;
    private final int toggle;
    private final boolean motion;
    private final boolean extra;
    private final TextView title,subtitle;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float focusAmount;
    private final GlassPanel.Surface surface=new GlassPanel.Surface();
    private final GlassSymbol glassIcon=new GlassSymbol(),chevron=new GlassSymbol();
    private final RectF face=new RectF(),badge=new RectF(),track=new RectF();

    SettingsTile(Context context,SceneView scene,String glyph,String title,String subtitle,int toggle,boolean large,boolean motion,float unit) {
        super(context);this.scene=scene;this.glyph=glyph;this.toggle=toggle;this.motion=motion;extra=DisplayPreferences.font(context.getSharedPreferences("desktop",0))==2;
        setWillNotDraw(false);setDefaultFocusHighlightEnabled(false);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        this.title=label(context,title,large?22:20,GlassPanel.TEXT,unit);
        this.subtitle=label(context,subtitle,large?15:13,GlassPanel.SECONDARY,unit);
        addView(this.title);addView(this.subtitle);setContentDescription(title);scene.addGlass(this);
    }
    private TextView label(Context context,String value,int size,int color,float unit) {
        TextView view=new TextView(context);view.setText(value);view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX,size*unit*DisplayPreferences.extraScale(context));view.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        view.setIncludeFontPadding(false);view.setSingleLine();view.setEllipsize(TextUtils.TruncateAt.END);
        view.setGravity(Gravity.CENTER_VERTICAL);view.setFocusable(false);return view;
    }
    @Override protected void onMeasure(int ws,int hs) {
        int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);
        float u=Math.min(w/276f,h/100f);
        title.measure(MeasureSpec.makeMeasureSpec(Math.max(1,w-Math.round(130*u)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round((extra?32:29)*u),MeasureSpec.EXACTLY));
        subtitle.measure(MeasureSpec.makeMeasureSpec(Math.max(1,w-Math.round(98*u)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(25*u),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        float u=Math.min(getWidth()/276f,getHeight()/100f);int x=Math.round(78*u),y=Math.round(21*u);
        title.layout(x,y,x+title.getMeasuredWidth(),y+title.getMeasuredHeight());y=Math.round(53*u);
        subtitle.layout(x,y,x+subtitle.getMeasuredWidth(),y+subtitle.getMeasuredHeight());
    }
    @Override protected void onFocusChanged(boolean gain,int direction,Rect oldRect) {
        super.onFocusChanged(gain,direction,oldRect);animateFocus();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);if(focused)animateFocus();else finishAnimation();
    }
    private void animateFocus() {
        boolean gain=isFocused()&&hasWindowFocus();
        if(animator!=null)animator.cancel();
        if(!motion){focusAmount=gain?1:0;invalidate();return;}
        animator=ValueAnimator.ofFloat(focusAmount,gain?1:0);animator.setDuration(GlassPanel.FOCUS_MILLIS);animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){@Override public void onAnimationUpdate(ValueAnimator value){focusAmount=(Float)value.getAnimatedValue();invalidate();}});animator.start();
    }
    @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
    void finishAnimation(){if(animator!=null){animator.cancel();animator=null;}focusAmount=isFocused()&&hasWindowFocus()?1:0;invalidate();}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas) {
        float u=Math.min(getWidth()/276f,getHeight()/100f),grow=2*u*focusAmount;
        face.set(6*u-grow,6*u-grow,getWidth()-6*u+grow,getHeight()-6*u+grow);
        surface.draw(canvas,face,19*u,scene,this,paint,focusAmount,u,GlassPanel.TINT);
        badge.set(20*u,27*u,62*u,69*u);paint.setColor(0x10FFFFFF);canvas.drawRoundRect(badge,12*u,12*u,paint);
        glassIcon.draw(canvas,glyph,scene,this,28*u,35*u,26*u,focusAmount);
        if(toggle>=0) {
            track.set(getWidth()-49*u,29*u,getWidth()-19*u,47*u);
            paint.setColor(toggle==1?0xE077899C:0xE0111720);canvas.drawRoundRect(track,9*u,9*u,paint);
            paint.setColor(GlassPanel.ICON);canvas.drawCircle(toggle==1?track.right-9*u:track.left+9*u,track.centerY(),6.7f*u,paint);
        } else {
            chevron.draw(canvas,"next",scene,this,getWidth()-37*u,29*u,18*u,focusAmount);
        }
        if(isPressed()){paint.setColor(0x24000000);canvas.drawRoundRect(face,19*u,19*u,paint);}
    }
}
