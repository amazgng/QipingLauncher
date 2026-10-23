package cn.wengmang.qiping;

import android.content.SharedPreferences;

/** Durable, once-per-observed-wake gate. Never infers a wake from an ordinary Activity resume. */
final class WakeState {
    static final String ENABLED="wake_live_enabled";
    static final long WINDOW_MS=60000;
    private final SharedPreferences prefs;
    WakeState(SharedPreferences prefs){this.prefs=prefs;}
    boolean enabled(){return prefs.getBoolean(ENABLED,false);}
    boolean waiting(){return enabled()&&(prefs.getBoolean("wake_armed",false)||prefs.getBoolean("wake_pending",false));}
    String status(){return prefs.getString("wake_status","尚未开启待机唤醒直播");}
    long statusTime(){return prefs.getLong("wake_status_time",-1);}
    long wakeTime(){return prefs.getLong("wake_at",-1);}
    long sequence(){return prefs.getLong("wake_sequence",0);}
    private SharedPreferences.Editor message(String text,long now){return prefs.edit().putString("wake_status",text).putLong("wake_status_time",now);}
    boolean setEnabled(boolean enabled,long now){return message(enabled?"已开启，等待下一次待机唤醒":"待机唤醒直播已关闭",now)
            .putBoolean(ENABLED,enabled).putBoolean("wake_armed",false).putBoolean("wake_pending",false).commit();}
    void screenOff(String boot,long now){
        if(!enabled()||boot==null||now<0)return;
        if(prefs.getBoolean("wake_armed",false)&&boot.equals(prefs.getString("wake_off_boot","")))return;
        message("已检测到待机，等待唤醒",now).putString("wake_off_boot",boot).putLong("wake_off_at",now)
                .putBoolean("wake_armed",true).putBoolean("wake_pending",false).commit();
    }
    boolean screenOn(String boot,long now){
        if(!enabled()||!prefs.getBoolean("wake_armed",false))return false;
        long off=prefs.getLong("wake_off_at",-1);
        if(boot==null||!boot.equals(prefs.getString("wake_off_boot",""))||off<0||now<off){
            finish("待机记录属于其他系统启动，本次不补启动",now);return false;
        }
        long next=sequence()==Long.MAX_VALUE?1:sequence()+1;
        return message("已检测到唤醒，等待桌面就绪",now).putBoolean("wake_armed",false)
                .putBoolean("wake_pending",true).putString("wake_boot",boot).putLong("wake_at",now)
                .putLong("wake_sequence",next).commit();
    }
    /** A saved OFF without a witnessed ON cannot date a wake after process death. */
    void processStartedInteractive(long now){
        if(prefs.getBoolean("wake_armed",false))finish("进程重建时已亮屏，未确认本次唤醒；等待下一次待机唤醒",now);
    }
    boolean pending(String boot,long now){
        if(!enabled()||!prefs.getBoolean("wake_pending",false))return false;
        long at=wakeTime();
        if(boot==null||!boot.equals(prefs.getString("wake_boot",""))||at<0||now<at){finish("唤醒记录已过期或属于其他系统启动",now);return false;}
        if(now-at>WINDOW_MS){finish("唤醒后超过 60 秒，本次不再补启动\n此前："+status(),now);return false;}
        return true;
    }
    boolean claim(String boot,long now){
        if(!pending(boot,now))return false;
        if(finish("正在请求打开直播（待机唤醒）",now))return true;
        record("唤醒启动记录未能保存，本次没有打开应用",now);return false;
    }
    void cancel(long now){
        if(enabled()&&(prefs.getBoolean("wake_pending",false)||prefs.getBoolean("wake_armed",false)))
            finish("已取消本次唤醒启动：检测到遥控器或触摸操作",now);
    }
    void keepExternalApp(long now){if(enabled())finish("关屏前已离开栖屏首页，本次保持原应用",now);}
    private boolean finish(String text,long now){return message(text,now).putBoolean("wake_pending",false).putBoolean("wake_armed",false).commit();}
    void record(String text,long now){if(!text.equals(status()))message(text,now).apply();}
}
