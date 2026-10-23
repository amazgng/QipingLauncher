package cn.wengmang.qiping;

/** Foreground startup coordinator. Time and lifecycle readiness are supplied by the Activity. */
final class StartupFlow {
    static final int IDLE = 0, WAIT = 1, COUNTDOWN = 2, LAUNCH = 3;
    static final long COUNTDOWN_MS = 3000, WAIT_MS = 60000;
    static final String WAIT_FOREGROUND = "等待桌面回到前台并获得焦点";
    static final String WAIT_DEFAULT = "等待系统确认栖屏为默认桌面";
    static final String WAIT_SCAN = "等待应用列表读取完成";
    static final String WAIT_TARGET = "所选直播应用暂不可用";
    static final String WAIT_SCAN_ERROR = "应用列表读取失败，等待重试";

    private final StartupState state;
    private boolean interacted;
    private String observed = "", waitingReason;
    private long deadline = -1;

    StartupFlow(StartupState state) { this.state = state; }
    boolean interacted() { return interacted; }
    void restoreInteraction(boolean value) { interacted = value; }
    long deadline() { return deadline; }

    // onPause, focus loss and a repeated HOME intent only reset the visible countdown.
    void suspend() { deadline = -1; }

    void cancel(String token, long now) {
        interacted = true;
        suspend();
        state.skip(token, "已取消：检测到遥控器或触摸操作"
                + (waitingReason == null ? "" : "\n取消前：" + waitingReason), now);
    }

    int poll(String token, long now, boolean homeEntry, String waitReason) {
        if (token == null) { suspend(); return IDLE; }
        if (!token.equals(observed)) { observed = token; suspend(); waitingReason = null; }
        if (!state.pending(token)) { suspend(); return IDLE; }
        if (!homeEntry) {
            interacted = true;
            state.skip(token, "通过应用入口打开，未执行开机自动打开", now);
            suspend(); return IDLE;
        }
        if (interacted) { cancel(token, now); return IDLE; }
        if (!state.eligible(token, now)) { suspend(); return IDLE; }
        long started = state.waitStarted(token, now);
        if (started < 0 || started > now) {
            state.skip(token, "启动等待记录不可用，本次没有打开应用", now);
            suspend(); return IDLE;
        }
        if (now - started > WAIT_MS) {
            state.skip(token, "等待启动条件超过 60 秒，本次保持桌面\n"
                    + (waitReason != null ? waitReason : waitingReason != null ? waitingReason : "桌面未能连续稳定显示 3 秒"), now);
            suspend(); return IDLE;
        }
        if (waitReason != null) {
            waitingReason = waitReason;
            suspend(); state.record(token, waitReason, now);
            return WAIT;
        }
        if (deadline < 0) {
            deadline = now + COUNTDOWN_MS;
            state.record(token, "即将打开直播，倒计时中", now);
        }
        if (now < deadline) return COUNTDOWN;
        suspend();
        return state.claim(token, now, true, true) ? LAUNCH : IDLE;
    }
}
