package cn.wengmang.qiping;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.UUID;

/** Uses a public system boot counter where available; old Android uses BOOT_COMPLETED. */
final class BootSession {
    static final String BROADCAST_TOKEN = "broadcast_boot";
    private BootSession() {}

    static String current(Context context) {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                // Public boot-count API; retained defensive access for manufacturer firmware.
                Class<?> global = Class.forName("android.provider.Settings$Global");
                int count = ((Integer) global.getMethod("getInt", ContentResolver.class,
                        String.class, int.class).invoke(null, context.getContentResolver(),
                        "boot_count", -1)).intValue();
                if (count >= 0) return "count:" + count;
            } catch (Exception ignored) { /* Vendor provider: wait for the boot broadcast. */ }
        }
        return preferences(context).getString(BROADCAST_TOKEN, "broadcast:initial");
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(StartupState.PREFERENCES, Context.MODE_PRIVATE);
    }

    static void completed(Context context) {
        // This token is only changed by the protected system boot broadcast, not by
        // wall-clock changes, screen-on, application updates, or Activity restarts.
        preferences(context).edit().putString(BROADCAST_TOKEN,
                "broadcast:" + UUID.randomUUID().toString()).commit();
    }
}
