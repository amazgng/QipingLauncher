package cn.wengmang.qiping;

/** Monotonic timing shared by touch and remote input. No background polling. */
final class ExperiencePolicy {
    static final long LAUNCH_WINDOW_MS=1800;
    private long launchUntil=-1;
    boolean beginLaunch(long now){if(launching(now))return false;launchUntil=now+LAUNCH_WINDOW_MS;return true;}
    boolean launching(long now){return launchUntil>now;}
    void clearLaunch(){launchUntil=-1;}
    static long remaining(long now,long lastInput,int minutes){if(minutes<=0)return Long.MAX_VALUE;return Math.max(0,minutes*60000L-Math.max(0,now-lastInput));}
    static boolean canIdle(boolean resumed,boolean focused,boolean home,boolean dialog,boolean startup,boolean busy){return resumed&&focused&&home&&!dialog&&!startup&&!busy;}
}
