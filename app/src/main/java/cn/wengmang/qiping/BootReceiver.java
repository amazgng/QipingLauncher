package cn.wengmang.qiping;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Record a boot only. UI launches are performed by the visible default HOME Activity. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            BootSession.completed(context);
            RunDiagnostics.get(context).event("开机广播","收到系统 BOOT_COMPLETED");
        }
    }
}
