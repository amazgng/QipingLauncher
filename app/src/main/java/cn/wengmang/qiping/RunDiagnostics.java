package cn.wengmang.qiping;

import android.content.Context;
import android.os.SystemClock;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Application-owned writer: no retained Activity, background timer, network, or unbounded queue. */
final class RunDiagnostics {
    private static volatile RunDiagnostics instance;
    private final File file;
    private final ThreadPoolExecutor writer;
    private DiagnosticJournal journal;
    private volatile String saved="正在读取本地记录…",failure="";
    private volatile int count;
    private final AtomicInteger skipped=new AtomicInteger();
    final long processStarted=SystemClock.elapsedRealtime();
    static RunDiagnostics get(Context context){if(instance==null)synchronized(RunDiagnostics.class){if(instance==null)instance=new RunDiagnostics(context.getApplicationContext());}return instance;}
    private RunDiagnostics(Context context){
        file=new File(context.getFilesDir(),"diagnostics/events.txt");
        writer=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(16),new ThreadFactory(){public Thread newThread(final Runnable task){return new Thread(new Runnable(){public void run(){android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run();}},"qiping-diagnostics");}},new ThreadPoolExecutor.AbortPolicy());
        writer.allowCoreThreadTimeOut(true);
        execute(new Runnable(){public void run(){try{journal=new DiagnosticJournal(file);update();}catch(Exception error){failed(error);}}});
    }
    private void execute(Runnable action){try{writer.execute(action);}catch(RejectedExecutionException ignored){skipped.incrementAndGet();}}
    void event(final String kind,final String detail){
        final long time=System.currentTimeMillis(),uptime=SystemClock.elapsedRealtime();
        final String message=DiagnosticJournal.clean(kind+" · "+detail);
        execute(new Runnable(){public void run(){try{
            if(journal==null)journal=new DiagnosticJournal(file);
            String prefix=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(time))+" [开机 "+uptime/1000+"s] ";
            journal.add(prefix+message);update();
        }catch(Exception error){failed(error);}}});
    }
    private void update(){saved=journal.snapshot();count=journal.count();failure="";}
    private void failed(Exception error){failure="记录未能保存："+error.getClass().getSimpleName();}
    String snapshot(){return (failure.isEmpty()?"":failure+"\n")+(skipped.get()==0?"":"忙碌时省略 "+skipped.get()+" 条记录\n")+(saved.isEmpty()?"暂无记录\n":saved);}
    int count(){return count;}
    interface Completion {void done(boolean saved);}
    void clear(final Completion completion){try{writer.execute(new Runnable(){public void run(){boolean ok=false;try{if(journal==null)journal=new DiagnosticJournal(file);journal.clear();skipped.set(0);update();ok=true;}catch(Exception error){failed(error);}completion.done(ok);}});}catch(RejectedExecutionException error){completion.done(false);}}
    void crash(Throwable error){
        StringBuilder detail=new StringBuilder(error.getClass().getName());
        StackTraceElement[] trace=error.getStackTrace();for(int i=0,n=0;i<trace.length&&n<4;i++)if(trace[i].getClassName().startsWith("cn.wengmang.qiping")){detail.append(" · ").append(trace[i].getClassName()).append('.').append(trace[i].getMethodName()).append(':').append(trace[i].getLineNumber());n++;}
        event("未处理异常",detail.toString());
        // Best effort only. Never block or replace Android's original crash handling.
        if(!Thread.currentThread().getName().equals("qiping-diagnostics"))try{writer.submit(new Runnable(){public void run(){}}).get(300,TimeUnit.MILLISECONDS);}catch(Exception ignored){}
    }
}
