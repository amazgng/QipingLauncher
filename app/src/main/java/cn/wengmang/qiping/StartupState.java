package cn.wengmang.qiping;

import android.content.SharedPreferences;

/** Durable once-per-boot gate, kept separate from Activity and clock APIs for testing. */
final class StartupState {
    static final String PREFERENCES = "startup";
    static final String ENABLED = "live_enabled";
    static final String HANDLED = "handled_boot";
    private static final String WAIT_BOOT = "wait_boot", WAIT_START = "wait_start";
    private static final String STATUS_BOOT = "status_boot", STATUS = "status", STATUS_TIME = "status_time";
    // A later visit to HOME should not unexpectedly interrupt the user.
    static final long START_WINDOW_MS = 10 * 60 * 1000L;
    private final SharedPreferences preferences;

    StartupState(SharedPreferences preferences) { this.preferences = preferences; }

    boolean enabled() { return preferences.getBoolean(ENABLED, false); }

    boolean setEnabled(boolean enabled, String token) {
        // Enabling in settings takes effect on the next boot, never on this visit.
        return preferences.edit().putBoolean(ENABLED, enabled).putString(HANDLED, token)
                .putString(STATUS_BOOT, token).putString(STATUS, enabled ? "已开启，等待下一次系统重启" : "自动打开已关闭")
                .putLong(STATUS_TIME, -1).commit();
    }

    boolean pending(String token) {
        return enabled() && token != null && token.length() > 0
                && !token.equals(preferences.getString(HANDLED, ""));
    }

    boolean skip(String token) {
        return skip(token, "已跳过本次自动打开", -1);
    }

    boolean skip(String token, String reason, long now) {
        return !pending(token) || statusEditor(token, reason, now).putString(HANDLED, token).commit();
    }

    /** Check durable eligibility without spending the attempt on a transient lifecycle event. */
    boolean eligible(String token, long elapsedSinceBoot) {
        if (!pending(token)) return false;
        String previous = preferences.getString(HANDLED, "");
        boolean sameSource = previous.startsWith("count:") && token.startsWith("count:")
                || previous.startsWith("broadcast:") && token.startsWith("broadcast:");
        // A provider becoming available/unavailable is not itself a new boot.
        if (!sameSource) {
            skip(token, "启动标识来源发生变化，本次保守跳过；下次重启再试", elapsedSinceBoot);
            return false;
        }
        if (elapsedSinceBoot < 0 || elapsedSinceBoot > START_WINDOW_MS) {
            skip(token, "已超过开机后 10 分钟，本次保持桌面", elapsedSinceBoot);
            return false;
        }
        return true;
    }

    boolean claim(String token, long elapsedSinceBoot, boolean homeEntry, boolean defaultHome) {
        if (!eligible(token, elapsedSinceBoot)) return false;
        if (!homeEntry) {
            skip(token, "通过应用入口打开，未执行开机自动打开", elapsedSinceBoot);
            return false;
        }
        // Default HOME can still be resolving during boot. The flow supplies a bounded wait.
        if (!defaultHome) return false;
        // Commit immediately BEFORE startActivity, not before a cancelable countdown.
        boolean saved = statusEditor(token, "正在向系统请求打开应用", elapsedSinceBoot)
                .putString(HANDLED, token).commit();
        if (!saved) record(token, "启动记录未能保存，本次没有打开应用", elapsedSinceBoot);
        return saved;
    }

    /** Survives Activity/process recreation so waiting cannot be extended indefinitely. */
    long waitStarted(String token, long now) {
        if (token.equals(preferences.getString(WAIT_BOOT, ""))) return preferences.getLong(WAIT_START, -1);
        return preferences.edit().putString(WAIT_BOOT, token).putLong(WAIT_START, now).commit() ? now : -1;
    }

    void record(String token, String message, long now) {
        if (token.equals(statusBoot()) && message.equals(status())) return;
        statusEditor(token, message, now).apply();
    }

    String status() { return preferences.getString(STATUS, "暂无记录，请在开启后真正重启一次"); }
    String statusBoot() { return preferences.getString(STATUS_BOOT, ""); }
    long statusTime() { return preferences.getLong(STATUS_TIME, -1); }
    String handledBoot() { return preferences.getString(HANDLED, ""); }

    private SharedPreferences.Editor statusEditor(String token, String message, long now) {
        return preferences.edit().putString(STATUS_BOOT, token).putString(STATUS, message).putLong(STATUS_TIME, now);
    }
}
