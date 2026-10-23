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

/** Three readable actions in the optional home layout; existing scene and glass renderer. */
final class SimpleHomeTile extends ViewGroup {
    private final SceneView scene;private final String glyph;private final float unit;private final boolean motion;
    private final TextView title,hint;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlassSymbol glassIcon=new GlassSymbol();
    private final RectF face=new RectF();private final GlassPanel.Surface surface=new GlassPanel.Surface();
    private ValueAnimator animator;private float focusAmount;private boolean update;
    SimpleHomeTile(Context context,SceneView scene,String glyph,String title,float unit,boolean large,boolean motion) {
        super(context);this.scene=scene;this.glyph=glyph;this.unit=unit;this.motion=motion;
        setWillNotDraw(false);setDefaultFocusHighlightEnabled(false);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        this.title=label(title,large?36:32,true);hint=label("",large?16:14,false);hint.setTextColor(GlassPanel.SECONDARY);
        addView(this.title);addView(hint);scene.addGlass(this);
    }
    private int u(float n){return Math.round(unit*n);}
    private TextView label(String text,int size,boolean bold){TextView v=new TextView(getContext());v.setText(text);v.setTextColor(GlassPanel.TEXT);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size*unit*DisplayPreferences.extraScale(getContext()));v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setIncludeFontPadding(false);v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.END);v.setGravity(Gravity.CENTER);v.setFocusable(false);return v;}
    void setHint(String text){hint.setText(text);setContentDescription(title.getText()+"，"+text);}
    void setUpdateAvailable(boolean value){update=value;invalidate();}
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);title.measure(MeasureSpec.makeMeasureSpec(Math.max(1,w-u(28)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(u(54),MeasureSpec.EXACTLY));hint.measure(MeasureSpec.makeMeasureSpec(Math.max(1,w-u(28)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(u(27),MeasureSpec.EXACTLY));}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int x=u(14);title.layout(x,u(128),getWidth()-x,u(182));hint.layout(x,u(183),getWidth()-x,u(210));}
    @Override protected void onFocusChanged(boolean gain,int dir,Rect old){super.onFocusChanged(gain,dir,old);animateFocus();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)animateFocus();else finishAnimation();}
    private void animateFocus(){if(animator!=null)animator.cancel();float value=isFocused()&&hasWindowFocus()?1:0;if(!motion){focusAmount=value;invalidate();return;}animator=ValueAnimator.ofFloat(focusAmount,value);animator.setDuration(GlassPanel.FOCUS_MILLIS);animator.setInterpolator(new DecelerateInterpolator());animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){public void onAnimationUpdate(ValueAnimator v){focusAmount=(Float)v.getAnimatedValue();invalidate();}});animator.start();}
    void finishAnimation(){if(animator!=null){animator.cancel();animator=null;}focusAmount=isFocused()&&hasWindowFocus()?1:0;invalidate();}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
    @Override protected void onDraw(Canvas c){float grow=2*unit*focusAmount;face.set(5*unit-grow,5*unit-grow,getWidth()-5*unit+grow,getHeight()-5*unit+grow);surface.draw(c,face,24*unit,scene,this,paint,focusAmount,unit,GlassPanel.TINT);float icon=76*unit;glassIcon.draw(c,glyph,scene,this,(getWidth()-icon)/2,37*unit,icon,focusAmount);if(update){paint.setColor(0xFFFFBF61);c.drawCircle(22*unit,22*unit,5*unit,paint);}if(isPressed()){paint.setColor(0x24000000);c.drawRoundRect(face,24*unit,24*unit,paint);}}
}
