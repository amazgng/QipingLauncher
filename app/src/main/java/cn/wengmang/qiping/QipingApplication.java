package cn.wengmang.qiping;

import android.app.Application;

/** Registers bounded, local-only diagnostics once per process. */
public final class QipingApplication extends Application {
    @Override public void onCreate(){super.onCreate();final RunDiagnostics diagnostics=RunDiagnostics.get(this);
        WakeMonitor.get(this);
        diagnostics.event("进程启动","Android "+android.os.Build.VERSION.RELEASE+" · 启动标识 "+BootSession.current(this));
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){public void uncaughtException(Thread thread,Throwable error){
            try{diagnostics.crash(error);}catch(Throwable ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
        }});
    }
}
