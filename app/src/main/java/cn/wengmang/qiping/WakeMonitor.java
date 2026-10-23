package cn.wengmang.qiping;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.os.SystemClock;
import java.lang.ref.WeakReference;

/** Application lifetime receiver, no service, polling, wake lock, or background launch. */
final class WakeMonitor {
    interface Listener {void powerChanged(boolean interactive);}
    private static WakeMonitor instance;
    private final Context app;
    final WakeState state;
    private Boolean lastInteractive;
    private boolean homeEligible;
    private WeakReference<Listener> listener=new WeakReference<Listener>(null);
    private String observation="等待电源状态";
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        if(i==null)return;
        if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())||Intent.ACTION_SCREEN_ON.equals(i.getAction())){
            Boolean current=power();if(current!=null)observe(current,"系统电源信号复核");
        }
    }};
    static synchronized WakeMonitor get(Context context){if(instance==null)instance=new WakeMonitor(context.getApplicationContext());return instance;}
    private WakeMonitor(Context context){
        app=context;state=new WakeState(BootSession.preferences(app));
        Boolean current=power();lastInteractive=current;
        if(Boolean.TRUE.equals(current))state.processStartedInteractive(SystemClock.elapsedRealtime());
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);
        try{app.registerReceiver(receiver,filter);observation="已监听系统待机 / 唤醒信号";}
        catch(RuntimeException error){observation="系统电源信号监听失败："+error.getClass().getSimpleName();}
        RunDiagnostics.get(app).event("待机监听",observation);
    }
    private Boolean power(){try{PowerManager pm=(PowerManager)app.getSystemService(Context.POWER_SERVICE);return pm==null?null:pm.isInteractive();}catch(RuntimeException e){return null;}}
    boolean interactive(){return Boolean.TRUE.equals(power());}
    String observation(){return observation;}
    void visibleHome(boolean value){homeEligible=value;}
    void externalLaunch(){homeEligible=false;state.cancel(SystemClock.elapsedRealtime());}
    void listen(Listener value){listener=new WeakReference<Listener>(value);}
    void detach(Listener value){if(listener.get()==value)listener.clear();}
    /** State changes only: a normal app return while interactive does nothing. */
    void refresh(){Boolean current=power();if(current!=null&&!current.equals(lastInteractive))observe(current,"前台电源状态复核");}
    private void observe(boolean interactive,String source){
        if(lastInteractive!=null&&lastInteractive.booleanValue()==interactive)return;
        long now=SystemClock.elapsedRealtime();String boot=BootSession.current(app);
        if(interactive)state.screenOn(boot,now);
        else if(homeEligible)state.screenOff(boot,now);
        else state.keepExternalApp(now);
        lastInteractive=interactive;observation=source+" · "+(interactive?"已唤醒":"已待机")+" · 开机后 "+now/1000+" 秒";
        RunDiagnostics.get(app).event("电源状态",observation);
        Listener value=listener.get();if(value!=null)value.powerChanged(interactive);
    }
}
