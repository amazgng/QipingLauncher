package cn.wengmang.qiping;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A modal, bounded right-hand panel. Home/volume remain system keys. */
final class QuickSettingsDialog extends Dialog {
    static final int MAIN=0,FONT=1,MARGIN=2,FAMILY=3,CONFIG=4,PERSONAL=5;
    interface Actions {
        void displayChanged();
        void openDevice();
        void openSystem();
        void openAllSettings();
        void personalAction(int action);
        void openParental();
        void backupAction(int action);
    }
    private final Activity activity;
    private final SceneView scene;
    private final SharedPreferences prefs;
    private final Actions actions;
    private final float unit;
    private final boolean standalone;
    private final Map<String,View> controls=new LinkedHashMap<String,View>();
    private final List<View> order=new ArrayList<View>(),glassViews=new ArrayList<View>();
    private int page,fontMode,confirmKey;
    private String focusedKey,desiredKey;
    private View confirmTarget;
    private TextView preview;
    QuickSettingsDialog(Activity activity,SceneView scene,float unit,int initialPage,String initialKey,boolean standalone,Actions actions) {
        super(activity);this.activity=activity;this.scene=scene;this.unit=unit;this.actions=actions;this.standalone=standalone;
        prefs=activity.getSharedPreferences("desktop",0);page=validPage(initialPage);desiredKey=initialKey;
    }
    private static int validPage(int page){return page>=MAIN&&page<=PERSONAL?page:MAIN;}
    int page(){return page;}
    String focusedKey(){return focusedKey;}
    boolean standalone(){return standalone;}
    private int u(float n){return Math.round(unit*n);}
    private float size(float standard,float large){return (fontMode==0?standard:large)*DisplayPreferences.extraScale(fontMode)*unit;}
    private TextView label(String value,float standard,float large,int color,boolean bold) {
        TextView v=new TextView(getContext());v.setText(value);v.setTextColor(color);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size(standard,large));
        v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);v.setFocusable(false);return v;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);setCanceledOnTouchOutside(true);
        Window w=getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        rebuild(desiredKey);
    }
    private void register(final String key,View view,final Runnable action) {
        controls.put(key,view);order.add(view);view.setId(3000+order.size());view.setContentDescription(key);
        view.setOnFocusChangeListener(new View.OnFocusChangeListener(){@Override public void onFocusChange(View v,boolean focused){if(focused)focusedKey=key;}});
        view.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){v.requestFocus();clearConfirm();action.run();}});
    }
    private void addRow(LinearLayout body,String key,String glyph,String title,String value,boolean chosen,final Runnable action) {
        final GlassButton row=new GlassButton(getContext(),scene,null,null);glassViews.add(row);row.setChosen(chosen);
        // Icon, name and value have independent layout bounds; all remain real accessible views.
        if(glyph!=null) {
            View icon=new SymbolView(glyph);
            FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(u(23),u(23));ip.gravity=Gravity.LEFT|Gravity.CENTER_VERTICAL;ip.leftMargin=u(14);row.addView(icon,ip);
        }
        TextView name=label(title,18,20,GlassPanel.TEXT,false);name.setSingleLine();
        FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,-1);np.leftMargin=u(glyph==null?18:49);np.rightMargin=u(value.length()>0?72:18);row.addView(name,np);
        if(value.length()>0) {
            TextView current=label(value,14,16,GlassPanel.SECONDARY,false);current.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);current.setSingleLine();
            FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(u(61),-1);vp.gravity=Gravity.RIGHT;vp.rightMargin=u(15);row.addView(current,vp);
        }
        register(key,row,action);row.setContentDescription(title+(value.length()>0?"，"+value:""));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,u(page==MAIN?44:48));lp.bottomMargin=u(page==MAIN?5:6);body.addView(row,lp);
    }
    private void divider(LinearLayout body) {
        View line=new View(getContext());line.setBackgroundColor(0x20FFFFFF);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Math.max(1,u(1)));lp.topMargin=u(5);lp.bottomMargin=u(10);lp.leftMargin=u(8);lp.rightMargin=u(8);body.addView(line,lp);
    }
    private void rebuild(String restore) {
        clearConfirm();for(View v:glassViews)scene.removeGlass(v);glassViews.clear();controls.clear();order.clear();preview=null;
        fontMode=DisplayPreferences.font(prefs);
        DisplayMetrics metrics=new DisplayMetrics();activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float safe=new float[]{.03f,.05f,.07f}[DisplayPreferences.margin(prefs)];
        int width=Math.min(u(350),Math.round(metrics.widthPixels*(1-2*safe)));
        int height=Math.min(u(466),Math.round(metrics.heightPixels*(1-2*safe)));
        FrameLayout shell=new FrameLayout(getContext());shell.setPadding(u(4),u(4),u(4),u(4));
        GlassPanel panel=new GlassPanel(getContext(),scene);panel.setTint(GlassPanel.DIALOG_TINT);glassViews.add(panel);shell.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=new LinearLayout(getContext());content.setOrientation(LinearLayout.VERTICAL);content.setPadding(u(18),u(18),u(18),u(16));shell.addView(content,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout heading=new FrameLayout(getContext());content.addView(heading,new LinearLayout.LayoutParams(-1,u(44)));
        GlassButton back=new GlassButton(getContext(),scene,"back",null);glassViews.add(back);
        register("quick_back",back,new Runnable(){@Override public void run(){onBackPressed();}});back.setContentDescription(page==CONFIG?"返回家庭与配置":page==FAMILY?"返回上一界面":standalone?"返回桌面设置":page==MAIN?"关闭快捷设置":"返回个性设置");
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(u(40),u(40));bp.gravity=Gravity.LEFT|Gravity.CENTER_VERTICAL;heading.addView(back,bp);
        TextView title=label(page==MAIN?"快捷设置":page==FONT?"显示字号":page==MARGIN?"屏幕边距":page==FAMILY?"家庭与配置":page==PERSONAL?"个性设置":"桌面配置",25,27,GlassPanel.TEXT,true);title.setSingleLine();
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,-1);tp.leftMargin=u(51);heading.addView(title,tp);
        ScrollView scroll=new ScrollView(getContext());scroll.setVerticalScrollBarEnabled(false);scroll.setFocusable(false);scroll.setFillViewport(false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=u(10);content.addView(scroll,sp);
        LinearLayout body=new LinearLayout(getContext());body.setOrientation(LinearLayout.VERTICAL);body.setPadding(u(2),u(2),u(2),u(2));scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        if(page==MAIN)buildMain(body);else if(page==FAMILY)buildFamily(body);else if(page==CONFIG)buildConfig(body);else if(page==PERSONAL)buildPersonal(body);else buildChoices(body);
        TextView hint=label(page==MAIN?"方向键选择 · 返回关闭":page==FONT?"仅调整栖屏字号 · 确定应用":page>=FAMILY?"方向键选择 · 返回上一级":"确定应用 · 返回保留设置",12,13,GlassPanel.SECONDARY,false);
        hint.setSingleLine();content.addView(hint,new LinearLayout.LayoutParams(-1,u(23)));
        setContentView(shell);Window w=getWindow();WindowManager.LayoutParams params=w.getAttributes();
        params.gravity=Gravity.RIGHT|Gravity.CENTER_VERTICAL;params.x=Math.round(metrics.widthPixels*safe);params.width=width;params.height=height;params.dimAmount=.20f;params.windowAnimations=0;w.setAttributes(params);
        w.getDecorView().setSystemUiVisibility(Build.VERSION.SDK_INT>=19?5894:1);
        for(View v:glassViews)scene.addGlass(v);
        for(int i=0;i<order.size();i++) {
            View v=order.get(i);v.setNextFocusUpId(order.get(Math.max(0,i-1)).getId());v.setNextFocusDownId(order.get(Math.min(order.size()-1,i+1)).getId());
            v.setNextFocusLeftId(v.getId());v.setNextFocusRightId(v.getId());v.setNextFocusForwardId(order.get((i+1)%order.size()).getId());
        }
        View target=controls.get(restore);
        if(target==null)target=controls.get(page==MAIN?"quick_all":page==FAMILY?"family_simple":page==CONFIG?"config_export":page==PERSONAL?"personal_motion":page==FONT?"font"+fontMode:"margin"+DisplayPreferences.margin(prefs));
        final View focus=target;final View currentShell=shell;
        shell.post(new Runnable(){@Override public void run(){if(isShowing()&&currentShell.getWindowToken()!=null&&focus!=null)focus.requestFocus();}});
    }
    private void buildMain(LinearLayout body) {
        addRow(body,"quick_all","grid","全部设置","›",false,new Runnable(){public void run(){actions.openAllSettings();}});
        addRow(body,"quick_device","info","设备与性能","›",false,new Runnable(){public void run(){actions.openDevice();}});
        addRow(body,"quick_system","settings","系统设置","›",false,new Runnable(){public void run(){actions.openSystem();}});
        note(body,"外观、字号、边距和布局统一在\n全部设置 → 个性设置中调整。",76);
    }
    private void buildPersonal(LinearLayout body) {
        addRow(body,"personal_motion","motion","界面动效",DisplayPreferences.motion(prefs)?"开启":"关闭",false,new Runnable(){public void run(){
            if(DisplayPreferences.setMotion(prefs,!DisplayPreferences.motion(prefs))){actions.displayChanged();rebuild("personal_motion");}else saveFailed();
        }});
        addRow(body,"personal_margin","margin","屏幕边距",new String[]{"3%","5%","7%"}[DisplayPreferences.margin(prefs)],false,new Runnable(){public void run(){page=MARGIN;rebuild(null);}});
        addRow(body,"personal_font","text","显示字号",DisplayPreferences.FONT_NAMES[fontMode],false,new Runnable(){public void run(){page=FONT;rebuild(null);}});
        divider(body);
        String[] names={"首页排列与隐藏","全部应用整理","桌面空闲屏保","本地壁纸"};String[] glyphs={"home","grid","history","album"};
        for(int i=0;i<names.length;i++){final int action=i;addRow(body,"personal_"+i,glyphs[i],names[i],"›",false,new Runnable(){public void run(){actions.personalAction(action);}});}
    }
    private void note(LinearLayout body,String text,int height) {
        TextView label=label(text,14,15,GlassPanel.SECONDARY,false);label.setGravity(Gravity.TOP);label.setLineSpacing(u(3),1);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,u(height));lp.leftMargin=u(8);lp.rightMargin=u(8);lp.topMargin=u(8);body.addView(label,lp);
    }
    private void buildFamily(LinearLayout body) {
        addRow(body,"family_simple","desktop","长辈布局",prefs.getBoolean("simple_home",false)?"开启":"关闭",false,new Runnable(){public void run(){
            if(DisplayPreferences.setFamily(prefs,"simple_home",!prefs.getBoolean("simple_home",false))){actions.displayChanged();rebuild("family_simple");}else saveFailed();
        }});
        addRow(body,"family_lock","lock","布局锁定",prefs.getBoolean("layout_locked",false)?"锁定":"未锁",false,new Runnable(){public void run(){
            if(DisplayPreferences.setFamily(prefs,"layout_locked",!prefs.getBoolean("layout_locked",false))){actions.displayChanged();rebuild("family_lock");}else saveFailed();
        }});
        addRow(body,"family_minor","lock","未成年模式","›",false,new Runnable(){public void run(){actions.openParental();}});
        divider(body);
        addRow(body,"family_config","export","桌面配置","›",false,new Runnable(){public void run(){page=CONFIG;rebuild(null);}});
        note(body,"长辈布局保留原有绑定。\n关闭开关即可回到五入口首页。\n\n锁定后暂停绑定和收藏编辑；\n仍可使用应用、进入设置和解锁。",142);
    }
    private void buildConfig(LinearLayout body) {
        addRow(body,"config_export","export","备份配置","›",false,new Runnable(){public void run(){actions.backupAction(0);}});
        addRow(body,"config_import","import","恢复配置","›",false,new Runnable(){public void run(){actions.backupAction(1);}});
        addRow(body,"config_undo","history","撤销上次恢复","›",false,new Runnable(){public void run(){actions.backupAction(2);}});
        divider(body);
        note(body,"备份：首页绑定、收藏顺序、\n字号、边距、动效与布局偏好。\n\n不包含应用数据、最近打开记录\n或开机执行状态。恢复前先预览。",132);
    }
    private void buildChoices(LinearLayout body) {
        final boolean fonts=page==FONT;int checked=fonts?fontMode:DisplayPreferences.margin(prefs);
        for(int i=0;i<3;i++) {
            final int choice=i;
            addRow(body,(fonts?"font":"margin")+i,null,fonts?DisplayPreferences.FONT_NAMES[i]:DisplayPreferences.MARGIN_NAMES[i],i==checked?"✓":"",i==checked,new Runnable(){public void run(){
                boolean saved=fonts?DisplayPreferences.setFont(prefs,choice):DisplayPreferences.setMargin(prefs,choice);
                if(saved){actions.displayChanged();rebuild((fonts?"font":"margin")+choice);}else saveFailed();
            }});
        }
        divider(body);
        TextView caption=label(fonts?"文字预览":"当前边距",14,16,GlassPanel.SECONDARY,false);body.addView(caption,new LinearLayout.LayoutParams(-1,u(27)));
        FrameLayout card=new FrameLayout(getContext());GlassPanel glass=new GlassPanel(getContext(),scene);glassViews.add(glass);card.addView(glass,new FrameLayout.LayoutParams(-1,-1));
        preview=label(fonts?"电视直播":DisplayPreferences.MARGIN_NAMES[checked],fonts?44:23,fonts?50:25,GlassPanel.TEXT,fonts);preview.setGravity(Gravity.CENTER);preview.setSingleLine();card.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        body.addView(card,new LinearLayout.LayoutParams(-1,u(92)));
        if(!fonts){TextView note=label("调整内容与屏幕边缘的距离",13,14,GlassPanel.SECONDARY,false);body.addView(note,new LinearLayout.LayoutParams(-1,u(32)));}
    }
    private void saveFailed(){Toast.makeText(getContext(),"设置保存失败，请重试",Toast.LENGTH_SHORT).show();}
    @Override public void onBackPressed() {
        clearConfirm();
        if(page==CONFIG){page=FAMILY;rebuild("family_config");return;}
        if(page==FONT||page==MARGIN){int old=page;page=PERSONAL;rebuild(old==FONT?"personal_font":"personal_margin");}
        else super.onBackPressed();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code=event.getKeyCode();
        if(code>=KeyEvent.KEYCODE_DPAD_UP&&code<=KeyEvent.KEYCODE_DPAD_RIGHT) {
            if(event.getAction()==KeyEvent.ACTION_DOWN) {
                clearConfirm();View current=getCurrentFocus();int index=order.indexOf(current);
                if(index<0){if(!order.isEmpty())order.get(0).requestFocus();}
                else if(code==KeyEvent.KEYCODE_DPAD_UP)order.get(Math.max(0,index-1)).requestFocus();
                else if(code==KeyEvent.KEYCODE_DPAD_DOWN)order.get(Math.min(order.size()-1,index+1)).requestFocus();
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
    private void clearConfirm(){if(confirmTarget!=null)confirmTarget.setPressed(false);confirmTarget=null;confirmKey=0;}
    private final class SymbolView extends View {
        private final String glyph;private final GlassSymbol glassIcon=new GlassSymbol();
        SymbolView(String glyph){super(QuickSettingsDialog.this.getContext());this.glyph=glyph;setFocusable(false);glassViews.add(this);scene.addGlass(this);}
        @Override protected void onDraw(Canvas canvas){float size=Math.min(getWidth(),getHeight());glassIcon.draw(canvas,glyph,scene,this,(getWidth()-size)/2,(getHeight()-size)/2,size,0);}
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused)clearConfirm();}
    @Override protected void onStop(){clearConfirm();for(View v:glassViews)scene.removeGlass(v);super.onStop();}
}
