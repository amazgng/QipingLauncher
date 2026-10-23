package cn.wengmang.qiping;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

/** Readable diagnostics within the existing wallpaper-backed glass style. */
final class DeviceCard extends ViewGroup {
    private final SceneView scene;
    private final TextView title,value,detail;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float unit;
    private final boolean extra;
    private final GlassPanel.Surface surface=new GlassPanel.Surface();
    private final RectF face=new RectF();
    DeviceCard(Context context,SceneView scene,String heading,String content,String note,boolean large,float unit) {
        super(context);this.scene=scene;this.unit=unit;extra=DisplayPreferences.font(context.getSharedPreferences("desktop",0))==2;
        setWillNotDraw(false);setDefaultFocusHighlightEnabled(false);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        title=label(context,heading,large?16:14,GlassPanel.SECONDARY);
        value=label(context,content,large?24:22,GlassPanel.TEXT);
        detail=label(context,note,large?14:12,GlassPanel.SECONDARY);
        addView(title);addView(value);addView(detail);scene.addGlass(this);
        setContentDescription(heading+"，"+content+"，"+note);
    }
    private TextView label(Context context,String text,int size,int color) {
        TextView v=new TextView(context);v.setText(text);v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size*unit*DisplayPreferences.extraScale(context));v.setIncludeFontPadding(false);
        v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.END);v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    @Override protected void onMeasure(int ws,int hs) {
        int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);
        for(TextView v:new TextView[]{title,value,detail})v.measure(MeasureSpec.makeMeasureSpec(Math.max(1,w-Math.round(42*unit)),MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round((v==value?(extra?33:29):(extra?22:20))*unit),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        int x=Math.round(22*unit);TextView[] views={title,value,detail};int[] positions=extra?new int[]{9,31,65}:new int[]{10,31,62};
        for(int i=0;i<views.length;i++){TextView v=views[i];int y=Math.round(positions[i]*unit);v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());}
    }
    @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);invalidate();}
    @Override protected void onDraw(Canvas c) {
        face.set(5*unit,3*unit,getWidth()-5*unit,getHeight()-3*unit);
        surface.draw(c,face,18*unit,scene,this,paint,isFocused()&&hasWindowFocus()?1:0,unit,GlassPanel.TINT);
        if(isPressed()){paint.setColor(0x24000000);c.drawRoundRect(face,18*unit,18*unit,paint);}
    }
}
