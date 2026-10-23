package cn.wengmang.qiping;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Explicit D-pad numeric pad. Never opens an IME or exposes PIN digits to accessibility. */
final class NumberEntryDialog extends Dialog {
    interface Submit {void submit(NumberEntryDialog dialog,String value);}
    private final Activity activity;private final SceneView scene;private final float unit;
    private final String title,prompt;private final int length;private final boolean secret;private final Submit submit;private final Runnable recover;
    private final List<View> glass=new ArrayList<View>(),keys=new ArrayList<View>();
    private TextView digits,message;private final StringBuilder input=new StringBuilder();private boolean busy;
    private View held;private int heldKey;private int width,height;
    NumberEntryDialog(Activity activity,SceneView scene,float unit,String title,String prompt,int length,boolean secret,Submit submit,Runnable recover){
        super(activity);this.activity=activity;this.scene=scene;this.unit=unit;this.title=title;this.prompt=prompt;this.length=length;this.secret=secret;this.submit=submit;this.recover=recover;
    }
    private int u(float n){return Math.round(n*unit);}
    private TextView label(String text,int size){TextView v=new TextView(getContext());v.setText(text);v.setTextColor(GlassPanel.TEXT);v.setGravity(Gravity.CENTER);v.setIncludeFontPadding(false);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,u(size)*DisplayPreferences.extraScale(activity));return v;}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);setCanceledOnTouchOutside(false);
        width=u(510);height=u(recover==null?440:466);
        FrameLayout shell=new FrameLayout(getContext());shell.setPadding(u(4),u(4),u(4),u(4));
        GlassPanel panel=new GlassPanel(getContext(),scene);panel.setTint(GlassPanel.DIALOG_TINT);shell.addView(panel,new FrameLayout.LayoutParams(-1,-1));glass.add(panel);
        LinearLayout body=new LinearLayout(getContext());body.setOrientation(LinearLayout.VERTICAL);body.setPadding(u(28),u(18),u(28),u(18));shell.addView(body,new FrameLayout.LayoutParams(-1,-1));
        TextView heading=label(title,25);heading.setTypeface(null,Typeface.BOLD);body.addView(heading,new LinearLayout.LayoutParams(-1,u(38)));
        message=label(prompt,15);message.setTextColor(GlassPanel.SECONDARY);body.addView(message,new LinearLayout.LayoutParams(-1,u(42)));
        digits=label("",30);digits.setContentDescription(secret?"密码输入区":"时间输入区");digits.setImportantForAccessibility(secret?View.IMPORTANT_FOR_ACCESSIBILITY_NO:View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);body.addView(digits,new LinearLayout.LayoutParams(-1,u(48)));refreshDigits();
        String[] labels={"1","2","3","4","5","6","7","8","9","删除","0","确定"};
        for(int r=0;r<4;r++){
            LinearLayout row=new LinearLayout(getContext());row.setOrientation(LinearLayout.HORIZONTAL);body.addView(row,new LinearLayout.LayoutParams(-1,u(49)));
            for(int c=0;c<3;c++){final int index=r*3+c;GlassButton b=button(labels[index],new Runnable(){public void run(){activate(index);}});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,u(43),1);lp.leftMargin=u(4);lp.rightMargin=u(4);row.addView(b,lp);}
        }
        GlassButton cancel=button("取消",new Runnable(){public void run(){cancel();}});body.addView(cancel,new LinearLayout.LayoutParams(-1,u(39)));
        if(recover!=null){GlassButton forgot=button("忘记密码 · 使用恢复码",new Runnable(){public void run(){if(!busy){dismiss();recover.run();}}});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,u(36));lp.topMargin=u(5);body.addView(forgot,lp);}
        setContentView(shell);Window w=getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_DIM_BEHIND|WindowManager.LayoutParams.FLAG_SECURE);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        WindowManager.LayoutParams p=w.getAttributes();p.dimAmount=.35f;p.gravity=Gravity.CENTER;p.windowAnimations=0;w.setAttributes(p);
    }
    private GlassButton button(String label,final Runnable action){GlassButton b=new GlassButton(getContext(),scene,null,label(label,19));b.setContentDescription(label);b.setId(45000+keys.size());keys.add(b);glass.add(b);b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(!busy){v.requestFocus();action.run();}}});return b;}
    private void activate(int index){
        if(busy)return;
        if(index==9){if(input.length()>0)input.deleteCharAt(input.length()-1);}
        else if(index==11){if(input.length()!=length){message.setText("请输入 "+length+" 位数字");return;}submit.submit(this,input.toString());return;}
        else if(input.length()<length)input.append(index==10?'0':(char)('1'+index));
        refreshDigits();
    }
    private void refreshDigits(){StringBuilder shown=new StringBuilder();for(int i=0;i<length;i++){if(i>0)shown.append(!secret&&length==4&&i==2?" : ":" ");shown.append(i<input.length()?secret?'●':input.charAt(i):'—');}if(digits!=null)digits.setText(shown.toString());}
    void busy(){busy=true;message.setText("正在验证，请稍候…");clearHeld();}
    void error(String text){busy=false;input.setLength(0);refreshDigits();message.setText(text);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int code=event.getKeyCode();
        if(code>=KeyEvent.KEYCODE_DPAD_UP&&code<=KeyEvent.KEYCODE_DPAD_RIGHT){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&!busy){clearHeld();int i=keys.indexOf(getCurrentFocus()),next=i;if(i<0)next=0;
                else if(i<12){if(code==KeyEvent.KEYCODE_DPAD_UP)next=i>=3?i-3:i;else if(code==KeyEvent.KEYCODE_DPAD_DOWN)next=i<9?i+3:12;else if(code==KeyEvent.KEYCODE_DPAD_LEFT&&i%3>0)next=i-1;else if(code==KeyEvent.KEYCODE_DPAD_RIGHT&&i%3<2)next=i+1;}
                else if(code==KeyEvent.KEYCODE_DPAD_UP)next=i==12?10:12;else if(code==KeyEvent.KEYCODE_DPAD_DOWN)next=Math.min(keys.size()-1,i+1);
                keys.get(next).requestFocus();}return true;
        }
        if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER||code==KeyEvent.KEYCODE_BUTTON_A){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&!busy){clearHeld();held=getCurrentFocus();heldKey=code;if(held!=null)held.setPressed(true);}
            else if(event.getAction()==KeyEvent.ACTION_UP){View v=held;boolean click=heldKey==code&&!event.isCanceled()&&!busy;clearHeld();if(click&&v!=null&&v==getCurrentFocus())v.performClick();}return true;
        }
        if(code>=KeyEvent.KEYCODE_0&&code<=KeyEvent.KEYCODE_9){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&!busy){int d=code-KeyEvent.KEYCODE_0;activate(d==0?10:d-1);}return true;}
        if(code==KeyEvent.KEYCODE_DEL){if(event.getAction()==KeyEvent.ACTION_DOWN&&!busy)activate(9);return true;}
        return super.dispatchKeyEvent(event);
    }
    private void clearHeld(){if(held!=null)held.setPressed(false);held=null;heldKey=0;}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus)clearHeld();}
    @Override protected void onStart(){super.onStart();for(View v:glass)scene.addGlass(v);getWindow().setLayout(width,height);getWindow().getDecorView().setSystemUiVisibility(5894);keys.get(0).requestFocus();}
    @Override protected void onStop(){input.setLength(0);clearHeld();for(View v:glass)scene.removeGlass(v);super.onStop();}
}
