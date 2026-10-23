package cn.wengmang.qiping;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Native dialog with wallpaper glass, scrolling content and explicit D-pad focus links. */
final class GlassDialog extends Dialog {
    static final class Builder {
        final Activity activity;
        final SceneView scene;
        final float unit;
        final boolean large;
        String title="",message,positive,negative;
        String[] items;
        int checked=-1,initialItem=-1;
        boolean sidePanel;
        Runnable backAction;
        DialogInterface.OnClickListener itemAction,positiveAction,negativeAction;
        Builder(Activity activity,SceneView scene,float unit,boolean large){this.activity=activity;this.scene=scene;this.unit=unit;this.large=large;}
        Builder setTitle(String value){title=value;return this;}
        Builder setInitialItem(int value){initialItem=value;return this;}
        Builder setSidePanel(){sidePanel=true;return this;}
        Builder setMessage(String value){message=value;return this;}
        Builder setItems(String[] values,DialogInterface.OnClickListener action){items=values;itemAction=action;return this;}
        Builder setSingleChoiceItems(String[] values,int checked,DialogInterface.OnClickListener action){items=values;this.checked=checked;itemAction=action;return this;}
        Builder setPositiveButton(String value,DialogInterface.OnClickListener action){positive=value;positiveAction=action;return this;}
        Builder setNegativeButton(String value,DialogInterface.OnClickListener action){negative=value;negativeAction=action;return this;}
        Builder setBackAction(Runnable action){backAction=action;return this;}
        GlassDialog create(){return new GlassDialog(this);}
    }
    private final Builder spec;
    private final List<View> glassViews=new ArrayList<View>();
    private final List<View> choices=new ArrayList<View>(),buttons=new ArrayList<View>();
    private View firstFocus;
    private ScrollView bodyScroll;
    private boolean messageScrollable;
    private View confirmTarget;
    private int confirmKey;
    private int id=30000,width,height;
    private GlassDialog(Builder spec){super(spec.activity,android.R.style.Theme_Holo_Dialog_NoActionBar);this.spec=spec;}
    private int u(float value){return Math.round(value*spec.unit);}
    private TextView label(String text,int size) {
        TextView view=new TextView(getContext());view.setText(text);view.setTextColor(GlassPanel.TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX,u(size)*DisplayPreferences.extraScale(spec.activity));view.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER_VERTICAL);view.setFocusable(false);return view;
    }
    private GlassButton action(String text,boolean chosen,final int which,final DialogInterface.OnClickListener callback) {
        TextView label=label(text,spec.large?20:18);label.setSingleLine();label.setEllipsize(TextUtils.TruncateAt.END);label.setPadding(u(16),0,u(16),0);
        GlassButton view=new GlassButton(getContext(),spec.scene,null,label);view.setChosen(chosen);view.setTint(GlassPanel.TINT);view.setId(id++);
        view.setContentDescription(text);glassViews.add(view);
        view.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){dismiss();if(callback!=null)callback.onClick(GlassDialog.this,which);}});
        return view;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);setCanceledOnTouchOutside(true);
        DisplayMetrics metrics=new DisplayMetrics();spec.activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int margin=Math.max(0,Math.min(2,spec.activity.getSharedPreferences("desktop",0).getInt("margin",1)));
        float safeFraction=1-2*new float[]{.03f,.05f,.07f}[margin];
        width=Math.min(u(spec.sidePanel?330:600),Math.round(metrics.widthPixels*Math.min(.86f,safeFraction)));
        int maxHeight=Math.min(u(466),Math.round(metrics.heightPixels*Math.min(.88f,safeFraction)));
        int innerWidth=width-u(56),footerHeight=(spec.positive!=null||spec.negative!=null)?u(68):0;
        FrameLayout shell=new FrameLayout(getContext());shell.setPadding(u(4),u(4),u(4),u(4));
        GlassPanel panel=new GlassPanel(getContext(),spec.scene);panel.setTint(GlassPanel.DIALOG_TINT);glassViews.add(panel);shell.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=new LinearLayout(getContext());content.setOrientation(LinearLayout.VERTICAL);content.setPadding(u(24),u(20),u(24),u(20));
        shell.addView(content,new FrameLayout.LayoutParams(-1,-1));
        TextView title=label(spec.title,spec.large?29:27);title.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(title,new LinearLayout.LayoutParams(-1,u(40)));
        View gap=new View(getContext());content.addView(gap,new LinearLayout.LayoutParams(1,u(14)));
        int introHeight=0;
        if(spec.items!=null&&spec.message!=null) {
            TextView intro=label(spec.message,spec.large?17:15);intro.setTextColor(GlassPanel.SECONDARY);intro.setGravity(Gravity.TOP);intro.setMaxLines(3);
            intro.measure(View.MeasureSpec.makeMeasureSpec(innerWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            introHeight=Math.min(u(74),intro.getMeasuredHeight()+u(10));content.addView(intro,new LinearLayout.LayoutParams(-1,introHeight));
        }
        final ScrollView scroller=new ScrollView(getContext());bodyScroll=scroller;scroller.setVerticalScrollBarEnabled(false);scroller.setFillViewport(false);scroller.setId(id++);
        int available=Math.max(u(60),maxHeight-u(102)-footerHeight-introHeight),bodyHeight;
        if(spec.items!=null) {
            LinearLayout rows=new LinearLayout(getContext());rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(u(3),u(3),u(3),u(3));
            for(int i=0;i<spec.items.length;i++) {
                boolean chosen=i==spec.checked;
                GlassButton row=action(spec.items[i]+(chosen?"  ✓":""),chosen,i,spec.itemAction);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,u(50));if(i>0)lp.topMargin=u(6);
                rows.addView(row,lp);choices.add(row);if(firstFocus==null||chosen||i==spec.initialItem)firstFocus=row;
            }
            scroller.addView(rows,new ScrollView.LayoutParams(-1,-2));scroller.setFocusable(false);
            bodyHeight=Math.min(available,u(spec.items.length*56));
        } else {
            TextView message=label(spec.message==null?"":spec.message,spec.large?19:17);message.setTextColor(0xFFD9DCE1);message.setGravity(Gravity.TOP);
            message.setLineSpacing(u(5),1);message.setPadding(u(3),u(2),u(3),u(4));
            message.measure(View.MeasureSpec.makeMeasureSpec(innerWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            bodyHeight=Math.min(available,message.getMeasuredHeight());scroller.addView(message,new ScrollView.LayoutParams(-1,-2));
            messageScrollable=message.getMeasuredHeight()>available;scroller.setFocusable(messageScrollable);scroller.setFocusableInTouchMode(messageScrollable);
            if(messageScrollable){
                scroller.setContentDescription("说明内容，上下滚动，右键到操作按钮");firstFocus=scroller;
                scroller.setOnFocusChangeListener(new View.OnFocusChangeListener(){@Override public void onFocusChange(View view,boolean focused){
                    GradientDrawable highlight=new GradientDrawable();highlight.setColor(focused?0x12FFFFFF:0x00000000);highlight.setCornerRadius(u(6));highlight.setStroke(Math.max(1,u(1)),focused?0xEBF2F3F5:0x00000000);scroller.setBackgroundDrawable(highlight);
                }});
            }
        }
        bodyHeight=Math.max(u(24),bodyHeight);
        content.addView(scroller,new LinearLayout.LayoutParams(-1,bodyHeight));
        if(footerHeight>0) {
            View beforeButtons=new View(getContext());content.addView(beforeButtons,new LinearLayout.LayoutParams(1,u(18)));
            LinearLayout actions=new LinearLayout(getContext());actions.setOrientation(LinearLayout.HORIZONTAL);
            if(spec.negative!=null)buttons.add(action(spec.negative,false,DialogInterface.BUTTON_NEGATIVE,spec.negativeAction));
            if(spec.positive!=null)buttons.add(action(spec.positive,true,DialogInterface.BUTTON_POSITIVE,spec.positiveAction));
            for(int i=0;i<buttons.size();i++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,u(50),1);if(i>0)lp.leftMargin=u(12);actions.addView(buttons.get(i),lp);}
            content.addView(actions,new LinearLayout.LayoutParams(-1,u(50)));
            if(firstFocus==null)firstFocus=buttons.get(buttons.size()-1);
        }
        height=Math.min(maxHeight,u(102)+introHeight+bodyHeight+footerHeight);
        setContentView(shell);Window window=getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowManager.LayoutParams attrs=window.getAttributes();attrs.dimAmount=.30f;
        attrs.gravity=spec.sidePanel?Gravity.RIGHT|Gravity.CENTER_VERTICAL:Gravity.CENTER;
        if(spec.sidePanel)attrs.x=Math.round(metrics.widthPixels*(1-safeFraction)/2);
        attrs.windowAnimations=0;window.setAttributes(attrs);
        for(int i=0;i<choices.size();i++) {
            View row=choices.get(i);row.setNextFocusUpId(i==0?row.getId():choices.get(i-1).getId());
            row.setNextFocusDownId(i+1<choices.size()?choices.get(i+1).getId():buttons.isEmpty()?row.getId():buttons.get(0).getId());
            row.setNextFocusLeftId(row.getId());row.setNextFocusRightId(row.getId());
        }
        for(int i=0;i<buttons.size();i++) {
            View button=buttons.get(i);button.setNextFocusLeftId(buttons.get(Math.max(0,i-1)).getId());button.setNextFocusRightId(buttons.get(Math.min(buttons.size()-1,i+1)).getId());
            button.setNextFocusDownId(button.getId());button.setNextFocusUpId(!choices.isEmpty()?choices.get(choices.size()-1).getId():scroller.isFocusable()?scroller.getId():button.getId());
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code=event.getKeyCode();
        if(code>=KeyEvent.KEYCODE_DPAD_UP&&code<=KeyEvent.KEYCODE_DPAD_RIGHT) {
            if(event.getAction()==KeyEvent.ACTION_DOWN) {
                clearConfirm();View current=getCurrentFocus();int choice=choices.indexOf(current),button=buttons.indexOf(current);
                if(current==bodyScroll&&messageScrollable) {
                    int max=Math.max(0,bodyScroll.getChildAt(0).getHeight()-bodyScroll.getHeight());
                    if(code==KeyEvent.KEYCODE_DPAD_DOWN&&bodyScroll.getScrollY()<max)bodyScroll.scrollTo(0,Math.min(max,bodyScroll.getScrollY()+Math.max(u(40),bodyScroll.getHeight()/3)));
                    else if(code==KeyEvent.KEYCODE_DPAD_UP)bodyScroll.scrollTo(0,Math.max(0,bodyScroll.getScrollY()-Math.max(u(40),bodyScroll.getHeight()/3)));
                    else if(!buttons.isEmpty())buttons.get(code==KeyEvent.KEYCODE_DPAD_RIGHT?buttons.size()-1:0).requestFocus();
                }else if(choice>=0) {
                    if(code==KeyEvent.KEYCODE_DPAD_UP)choices.get(Math.max(0,choice-1)).requestFocus();
                    else if(code==KeyEvent.KEYCODE_DPAD_DOWN){if(choice+1<choices.size())choices.get(choice+1).requestFocus();else if(!buttons.isEmpty())buttons.get(0).requestFocus();}
                    else if(code==KeyEvent.KEYCODE_DPAD_RIGHT&&!buttons.isEmpty())buttons.get(buttons.size()-1).requestFocus();
                }else if(button>=0) {
                    if(code==KeyEvent.KEYCODE_DPAD_LEFT)buttons.get(Math.max(0,button-1)).requestFocus();
                    else if(code==KeyEvent.KEYCODE_DPAD_RIGHT)buttons.get(Math.min(buttons.size()-1,button+1)).requestFocus();
                    else if(code==KeyEvent.KEYCODE_DPAD_UP){if(!choices.isEmpty())choices.get(choices.size()-1).requestFocus();else if(messageScrollable)bodyScroll.requestFocus();}
                }else if(firstFocus!=null)firstFocus.requestFocus();
            }
            return true;
        }
        if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER||code==KeyEvent.KEYCODE_BUTTON_A) {
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){clearConfirm();confirmKey=code;confirmTarget=getCurrentFocus();if(confirmTarget!=null)confirmTarget.setPressed(true);}
            else if(event.getAction()==KeyEvent.ACTION_UP){View target=confirmTarget;boolean click=confirmKey==code&&!event.isCanceled();clearConfirm();if(click&&target!=null&&target==getCurrentFocus())target.performClick();}
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){if(spec.backAction!=null){dismiss();spec.backAction.run();}else super.onBackPressed();}
    private void clearConfirm(){if(confirmTarget!=null)confirmTarget.setPressed(false);confirmTarget=null;confirmKey=0;}
    @Override protected void onStart() {
        super.onStart();for(View view:glassViews)spec.scene.addGlass(view);
        Window window=getWindow();window.setLayout(width,height);window.getDecorView().setSystemUiVisibility(Build.VERSION.SDK_INT>=19?5894:1);
        if(firstFocus!=null)firstFocus.post(new Runnable(){@Override public void run(){if(isShowing())firstFocus.requestFocus();}});
    }
    @Override protected void onStop(){clearConfirm();for(View view:glassViews)spec.scene.removeGlass(view);super.onStop();}
}
