package cn.wengmang.qiping;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import io.nayuki.qrcodegen.QrCode;

/** Foreground-only receiver and remote-controlled preview. Its stop path owns every socket and image. */
final class PhoneWallpaperDialog extends Dialog {
    private final Activity activity;private final SceneView scene;private final WallpaperStore destination;private final int category;
    private final float unit;private final Runnable applied,retry;private final Handler handler=new Handler();
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final List<View> glasses=new ArrayList<View>();private final List<GlassButton> buttons=new ArrayList<GlassButton>();
    private volatile PhoneWallpaperServer server;private volatile boolean closed;
    private File stagingDirectory;private WallpaperStore staging;private InetAddress boundAddress;
    private FrameLayout body;private QrView qr;private ImageView preview;private Bitmap previewBitmap;
    private TextView title,step,address,code,hint,status,applyLabel;private GlassButton applyButton,discardButton;
    private String renderedState="";private int width,height,confirmCode;private View confirmTarget;
    private boolean previewReady;
    private final Runnable tick=new Runnable(){public void run(){
        if(closed)return;PhoneWallpaperServer current=server;
        if(current!=null&&current.isRunning()){
            InetAddress now=LanAddress.find(activity);
            if(now==null||!now.equals(boundAddress))current.stop("网络已断开或地址改变，请重新连接");
        }
        refresh();handler.postDelayed(this,5000);
    }};
    PhoneWallpaperDialog(Activity activity,SceneView scene,WallpaperStore destination,float unit,int category,Runnable applied,Runnable retry){
        super(activity,android.R.style.Theme_Holo_Dialog_NoActionBar);this.activity=activity;this.scene=scene;this.destination=destination;this.unit=unit;this.category=category;this.applied=applied;this.retry=retry;
    }
    private int u(float value){return Math.round(value*unit);}
    private TextView label(String value,int size,boolean bold){TextView text=new TextView(activity);text.setText(value);text.setTextColor(GlassPanel.TEXT);text.setTextSize(TypedValue.COMPLEX_UNIT_PX,u(size)*Math.min(1.12f,DisplayPreferences.extraScale(activity)));text.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));text.setIncludeFontPadding(false);text.setGravity(Gravity.CENTER_VERTICAL);return text;}
    private void place(View view,int x,int y,int w,int h){FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(u(w),u(h));lp.leftMargin=u(x);lp.topMargin=u(y);body.addView(view,lp);}
    private GlassButton button(String value,int id,int x,int w,final Runnable action){TextView text=label(value,19,false);text.setGravity(Gravity.CENTER);GlassButton button=new GlassButton(activity,scene,null,text);button.setId(id);button.setContentDescription(value);button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});place(button,x,382,w,48);glasses.add(button);buttons.add(button);if(id==41003)applyLabel=text;return button;}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);setCanceledOnTouchOutside(false);
        DisplayMetrics metrics=new DisplayMetrics();activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);width=Math.min(u(810),Math.round(metrics.widthPixels*.86f));height=Math.min(u(450),Math.round(metrics.heightPixels*.86f));
        // The launcher uses a 960 x 540 design canvas. The dialog fits inside its largest safe margin.
        body=new FrameLayout(activity);GlassPanel panel=new GlassPanel(activity,scene);panel.setTint(GlassPanel.DIALOG_TINT);body.addView(panel,new FrameLayout.LayoutParams(-1,-1));glasses.add(panel);
        title=label("手机传壁纸",29,true);place(title,28,18,744,42);
        TextView intro=label("手机选择图片，电视预览后确认应用",17,false);intro.setTextColor(GlassPanel.SECONDARY);place(intro,28,60,744,30);
        qr=new QrView(activity);place(qr,28,104,224,224);preview=new ImageView(activity);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(0xff17202a);preview.setVisibility(View.GONE);place(preview,28,104,224,224);
        step=label("正在准备连接…",22,true);place(step,282,100,500,52);
        address=label("",19,false);address.setSingleLine();address.setTextIsSelectable(false);place(address,282,158,500,40);
        code=label("",22,true);place(code,282,204,500,42);
        hint=label("手机与电视连接同一路由器。\n电视也可以使用有线网络。",17,false);hint.setTextColor(GlassPanel.SECONDARY);hint.setGravity(Gravity.TOP);place(hint,282,254,500,76);
        status=label("正在开启接收…",18,false);status.setTextColor(GlassPanel.SECONDARY);place(status,28,335,754,38);
        button("结束传输",41001,28,208,new Runnable(){public void run(){dismiss();}});
        discardButton=button("放弃图片",41002,254,208,new Runnable(){public void run(){discard();}});discardButton.setEnabled(false);discardButton.setAlpha(.4f);
        applyButton=button("应用壁纸",41003,480,302,new Runnable(){public void run(){confirm();}});applyButton.setEnabled(false);applyButton.setAlpha(.4f);
        for(int i=0;i<buttons.size();i++){View v=buttons.get(i);v.setNextFocusLeftId(buttons.get(Math.max(0,i-1)).getId());v.setNextFocusRightId(buttons.get(Math.min(buttons.size()-1,i+1)).getId());v.setNextFocusUpId(v.getId());v.setNextFocusDownId(v.getId());}
        setContentView(body);Window window=getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND|WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);WindowManager.LayoutParams attrs=window.getAttributes();attrs.dimAmount=.30f;attrs.gravity=Gravity.CENTER;attrs.windowAnimations=0;window.setAttributes(attrs);
    }
    @Override protected void onStart(){super.onStart();getWindow().setLayout(width,height);getWindow().getDecorView().setSystemUiVisibility(5894);buttons.get(0).requestFocus();startReceiving();}
    private void startReceiving(){work.execute(new Runnable(){public void run(){
        try{
            boundAddress=LanAddress.find(activity);if(boundAddress==null)throw new IOException("未找到局域网地址，请先连接家庭 Wi-Fi 或网线");
            stagingDirectory=new File(activity.getCacheDir(),"phone-wallpaper-"+UUID.randomUUID().toString());staging=new WallpaperStore(activity,stagingDirectory);
            Map<String,byte[]> assets=new HashMap<String,byte[]>();for(String name:new String[]{"index.html","style.css","crop.js","app.js"}){InputStream input=activity.getAssets().open("phone-wallpaper/"+name);ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;try{while((n=input.read(buffer))!=-1)output.write(buffer,0,n);}finally{input.close();}assets.put(name.equals("index.html")?"/":"/"+name,output.toByteArray());}
            final PhoneWallpaperServer started=new PhoneWallpaperServer(boundAddress,category,assets,new PhoneWallpaperServer.Receiver(){public void receive(int slot,InputStream input,final PhoneWallpaperServer.Cancellation cancellation)throws IOException{
                for(int i=0;i<6;i++)staging.reset(i);
                staging.importStream(slot,input,new WallpaperStore.Cancellation(){public boolean cancelled(){return closed||cancellation.cancelled();}});
            }},new PhoneWallpaperServer.Listener(){public void changed(){if(!closed)handler.post(new Runnable(){public void run(){refresh();}});}});
            RunDiagnostics.get(activity).event("手机传图","接收会话开启");
            server=started;if(closed){started.close();return;}
            final QrCode symbol=QrCode.encodeText(started.qrAddress(),QrCode.Ecc.MEDIUM);started.start();
            handler.post(new Runnable(){public void run(){if(closed)return;qr.setCode(symbol);address.setText(started.address());code.setText("配对码  "+started.pairCode());step.setText("扫码打开手机上传页");hint.setText("扫码无法打开时，用手机浏览器输入\n上方地址，再填写配对码。");refresh();handler.postDelayed(tick,5000);}});
        }catch(final Exception error){RunDiagnostics.get(activity).event("传图连接失败",error.getClass().getSimpleName());handler.post(new Runnable(){public void run(){if(closed)return;step.setText("暂时无法连接");hint.setText("确认手机和电视连接同一路由器，\n检查网络后选择「重新连接」。");status.setText(error.getMessage()==null?"接收服务未能启动，请重试":error.getMessage());enableRetry();}});}
    }});}
    private void refresh(){
        if(closed||server==null)return;final PhoneWallpaperServer current=server;String value=current.state();status.setText(current.message()+("receiving".equals(value)?" · "+current.progress()+"%":""));
        if(!current.isRunning()){step.setText("连接已结束");qr.setCode(null);qr.setVisibility(View.VISIBLE);clearPreview();address.setText("请重新连接后扫码");code.setText("");hint.setText("旧二维码和配对码已失效。\n尚未确认的图片不会替换原壁纸。");enableRetry();renderedState=value;return;}
        title.setText("手机传壁纸 · "+Math.max(1,(current.remainingMillis()+59999)/60000)+" 分钟内有效");
        if(value.equals(renderedState))return;renderedState=value;RunDiagnostics.get(activity).event("手机传图",value+" · 场景 "+current.scene());
        if("pending".equals(value)){
            step.setText("已收到 · "+sceneName(current.scene()));address.setText("确认后替换此界面的壁纸");code.setText("请用遥控器确认");hint.setText("按左右键选择「应用壁纸」或「放弃图片」。\n确认前，原壁纸保持不变。");
            work.execute(new Runnable(){public void run(){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;Bitmap decoded=null;try{decoded=BitmapFactory.decodeFile(staging.file(current.scene()).getAbsolutePath(),options);}catch(OutOfMemoryError ignored){}final Bitmap image=decoded;
                handler.post(new Runnable(){public void run(){if(closed||server!=current||!"pending".equals(current.state())){if(image!=null)image.recycle();return;}clearPreview();previewBitmap=image;previewReady=image!=null;qr.setVisibility(View.GONE);preview.setVisibility(View.VISIBLE);preview.setImageBitmap(image);discardButton.setEnabled(true);discardButton.setAlpha(1);applyButton.setEnabled(previewReady);applyButton.setAlpha(previewReady?1:.4f);if(previewReady)applyButton.requestFocus();else{status.setText("无法显示图片预览，请放弃后换一张图片");discardButton.requestFocus();}}});
            }});
        }else if(!"applying".equals(value)){
            clearPreview();qr.setVisibility(View.VISIBLE);step.setText("扫码打开手机上传页");address.setText(current.address());code.setText("配对码  "+current.pairCode());hint.setText("扫码无法打开时，用手机浏览器输入\n上方地址，再填写配对码。");
            discardButton.setEnabled(false);discardButton.setAlpha(.4f);applyButton.setEnabled(false);applyButton.setAlpha(.4f);applyLabel.setText("应用壁纸");
            if(getCurrentFocus()==applyButton||getCurrentFocus()==discardButton)buttons.get(0).requestFocus();
        }
        title.setText("手机传壁纸 · "+Math.max(1,(current.remainingMillis()+59999)/60000)+" 分钟内有效");
    }
    private void enableRetry(){discardButton.setEnabled(false);discardButton.setAlpha(.4f);applyLabel.setText("重新连接");applyButton.setContentDescription("重新连接");applyButton.setEnabled(true);applyButton.setAlpha(1);}
    private void confirm(){PhoneWallpaperServer current=server;if(current==null||!current.isRunning()){dismiss();retry.run();return;}if(!previewReady||!current.beginApply())return;boolean saved=destination.adoptPrepared(current.scene(),staging.file(current.scene()));if(saved)applied.run();current.applied(saved);refresh();}
    private void discard(){PhoneWallpaperServer current=server;if(current!=null){synchronized(current){if("pending".equals(current.state())){staging.reset(current.scene());current.reject();}}refresh();}}
    private void clearPreview(){previewReady=false;if(preview!=null){preview.setImageDrawable(null);preview.setVisibility(View.GONE);}if(previewBitmap!=null){previewBitmap=null; /* ImageView/RenderThread may still hold the published frame. */}}
    private static String sceneName(int slot){return slot==5?"设置页面":ShortcutStore.NAMES[Math.max(0,Math.min(4,slot))];}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();if(key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT){
            if(event.getAction()==KeyEvent.ACTION_DOWN){clearConfirm();int index=buttons.indexOf(getCurrentFocus());if(index<0)index=0;if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){int direction=key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1;for(int i=index+direction;i>=0&&i<buttons.size();i+=direction)if(buttons.get(i).isEnabled()){buttons.get(i).requestFocus();break;}}else if(getCurrentFocus()==null)buttons.get(0).requestFocus();}return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER||key==KeyEvent.KEYCODE_BUTTON_A){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){clearConfirm();confirmCode=key;confirmTarget=getCurrentFocus();if(confirmTarget!=null)confirmTarget.setPressed(true);}else if(event.getAction()==KeyEvent.ACTION_UP){View target=confirmTarget;boolean click=key==confirmCode&&!event.isCanceled();clearConfirm();if(click&&target!=null&&target==getCurrentFocus()&&target.isEnabled())target.performClick();}return true;}
        return super.dispatchKeyEvent(event);
    }
    private void clearConfirm(){if(confirmTarget!=null)confirmTarget.setPressed(false);confirmTarget=null;confirmCode=0;}
    @Override protected void onStop(){
        RunDiagnostics.get(activity).event("手机传图","会话结束，停止接收");
        closed=true;clearConfirm();handler.removeCallbacksAndMessages(null);final PhoneWallpaperServer stopped=server;if(stopped!=null)stopped.close();work.shutdownNow();clearPreview();qr.setCode(null);for(View view:glasses)scene.removeGlass(view);
        new Thread(new Runnable(){public void run(){if(stopped!=null)stopped.awaitClosed();try{work.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}File directory=stagingDirectory;if(directory!=null){File[] files=directory.listFiles();if(files!=null)for(File file:files)if(file.isFile())file.delete();directory.delete();}}},"qiping-wallpaper-cleanup").start();
        super.onStop();
    }
    private static final class QrView extends View {
        private QrCode code;private final Paint paint=new Paint();QrView(Context context){super(context);setContentDescription("手机扫码上传壁纸");}
        void setCode(QrCode value){code=value;invalidate();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(code==null)return;canvas.drawColor(Color.WHITE);int scale=Math.max(1,Math.min(getWidth(),getHeight())/(code.size+8)),left=(getWidth()-code.size*scale)/2,top=(getHeight()-code.size*scale)/2;paint.setColor(Color.BLACK);for(int y=0;y<code.size;y++)for(int x=0;x<code.size;x++)if(code.getModule(x,y))canvas.drawRect(left+x*scale,top+y*scale,left+(x+1)*scale,top+(y+1)*scale,paint);}
    }
}
