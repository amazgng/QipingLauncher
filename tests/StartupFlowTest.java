package cn.wengmang.qiping;

/** Regression sequences for boot lifecycle interruptions; runs without an Android runtime. */
public final class StartupFlowTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static final class Boot {
        final ShortcutStoreTest.MemoryPreferences prefs = new ShortcutStoreTest.MemoryPreferences();
        StartupState state = new StartupState(prefs);
        StartupFlow flow = new StartupFlow(state);
        Boot() { state.setEnabled(true, "count:1"); }
        int at(long now, String wait) { return flow.poll("count:2", now, true, wait); }
        void recreate() { state = new StartupState(prefs); flow = new StartupFlow(state); }
    }
    public static void main(String[] args) {
        // Reported failure path: boot HOME -> pause -> repeated HOME -> resume/focus.
        Boot b = new Boot();
        check(b.at(10000, StartupFlow.WAIT_FOREGROUND) == StartupFlow.WAIT, "wait for initial focus");
        b.flow.suspend();
        check(b.state.pending("count:2"), "early onPause must not consume boot");
        check(b.at(11000, StartupFlow.WAIT_SCAN) == StartupFlow.WAIT, "duplicate HOME waits for scan");
        check(b.at(12000, null) == StartupFlow.COUNTDOWN, "visible HOME starts countdown");
        check(b.state.pending("count:2"), "countdown is not an executed attempt");
        b.flow.suspend();
        check(b.at(13000, StartupFlow.WAIT_FOREGROUND) == StartupFlow.WAIT, "overlay during countdown waits");
        check(b.at(14000, null) == StartupFlow.COUNTDOWN, "focus return starts full countdown");
        check(b.at(15000, null) == StartupFlow.COUNTDOWN, "old deadline cannot launch early");
        check(b.at(16999, null) == StartupFlow.COUNTDOWN, "three seconds remain visible");
        check(b.at(17000, null) == StartupFlow.LAUNCH, "eventually launches after stable foreground");
        check(!b.state.pending("count:2"), "attempt persisted before caller launches");
        b.flow.suspend();
        check(b.at(18000, null) == StartupFlow.IDLE, "HOME after target opens cannot relaunch");
        b.recreate();
        check(b.at(19000, null) == StartupFlow.IDLE, "process restart after launch stays put");
        check(b.at(200000, null) == StartupFlow.IDLE, "same boot standby wake stays put");
        check(b.flow.poll("count:3", 10000, true, null) == StartupFlow.COUNTDOWN, "next real boot is eligible");

        b = new Boot();
        check(b.at(10000, StartupFlow.WAIT_DEFAULT) == StartupFlow.WAIT, "temporarily unresolved default waits");
        check(b.state.pending("count:2"), "default wait preserves attempt");
        check(b.at(12000, null) == StartupFlow.COUNTDOWN, "default confirmation recovers");
        check(b.at(15000, null) == StartupFlow.LAUNCH, "confirmed HOME launches once");

        b = new Boot();
        check(b.at(10000, StartupFlow.WAIT_SCAN_ERROR) == StartupFlow.WAIT, "initial package error waits");
        check(b.at(13000, StartupFlow.WAIT_TARGET) == StartupFlow.WAIT, "initial missing target waits");
        check(b.at(20000, null) == StartupFlow.COUNTDOWN, "target discovered later starts countdown");
        check(b.at(23000, null) == StartupFlow.LAUNCH, "recovered package starts once");

        b = new Boot();
        b.at(10000, null); b.recreate();
        check(b.at(12000, null) == StartupFlow.COUNTDOWN, "recreation before launch remains eligible");
        check(b.at(13000, null) == StartupFlow.COUNTDOWN, "recreation supplies full new countdown");
        check(b.at(15000, null) == StartupFlow.LAUNCH, "recreated countdown may complete");

        b = new Boot();
        b.at(10000, StartupFlow.WAIT_SCAN);
        b.flow.cancel("count:2", 11000);
        check(b.at(12000, null) == StartupFlow.IDLE, "user input during wait cancels");
        check(b.state.status().contains(StartupFlow.WAIT_SCAN), "cancellation retains earlier waiting reason");
        b.recreate();
        check(b.at(13000, null) == StartupFlow.IDLE, "cancel persists through process death");
        b = new Boot(); b.at(10000, null); b.flow.cancel("count:2", 11000);
        check(b.at(14000, null) == StartupFlow.IDLE, "Back during countdown prevents launch");
        b = new Boot(); b.flow.cancel("count:1", 9000);
        check(b.at(10000, null) == StartupFlow.IDLE, "input before delayed boot identity also cancels");
        b = new Boot(); b.flow.restoreInteraction(true);
        check(b.at(10000, null) == StartupFlow.IDLE, "saved user interaction survives Activity recreation");

        b = new Boot();
        check(b.flow.poll("count:2", 10000, false, null) == StartupFlow.IDLE, "Android Studio Run is a manual entry");
        check(b.at(11000, null) == StartupFlow.IDLE, "subsequent HOME after manual entry does not interrupt");
        check(b.state.status().contains("应用入口"), "manual entry diagnostic is retained");

        b = new Boot();
        b.at(10000, StartupFlow.WAIT_DEFAULT);
        check(b.at(70000, StartupFlow.WAIT_DEFAULT) == StartupFlow.WAIT, "bounded wait includes exact upper boundary");
        check(b.at(70001, StartupFlow.WAIT_DEFAULT) == StartupFlow.IDLE, "unconfirmed default eventually stops waiting");
        check(b.state.status().contains(StartupFlow.WAIT_DEFAULT), "timeout explains missing default");
        check(b.at(71000, null) == StartupFlow.IDLE, "late default change cannot surprise user");
        b = new Boot(); b.at(10000, StartupFlow.WAIT_TARGET); b.recreate();
        check(b.at(70001, null) == StartupFlow.IDLE, "recreation cannot extend persisted wait budget");
        b = new Boot(); b.at(10000, null); b.flow.suspend();
        check(b.at(90000, null) == StartupFlow.IDLE, "long background/standby gap expires pending countdown");
        b = new Boot();
        check(b.at(StartupState.START_WINDOW_MS + 1, null) == StartupFlow.IDLE, "late first HOME never launches");
        b = new Boot();
        b.at(StartupState.START_WINDOW_MS - 1000, null);
        check(b.at(StartupState.START_WINDOW_MS + 2000, null) == StartupFlow.IDLE, "countdown cannot cross global boot window");

        b = new Boot(); b.at(10000, null); b.state.setEnabled(false, "count:2");
        check(b.at(13000, null) == StartupFlow.IDLE, "disabling stops pending countdown");
        b.state.setEnabled(true, "count:2");
        check(b.at(14000, null) == StartupFlow.IDLE, "re-enable still applies next reboot");
        b = new Boot(); b.at(10000, null); b.prefs.commitSucceeds = false;
        check(b.at(13000, null) == StartupFlow.IDLE, "failed attempt commit forbids launching");
        check(b.state.status().contains("未能保存"), "storage error is visible");
        b = new Boot(); b.prefs.commitSucceeds = false;
        check(b.at(10000, null) == StartupFlow.IDLE, "failed wait record cannot start countdown");

        b = new Boot();
        check(b.flow.poll("broadcast:old", 10000, true, null) == StartupFlow.IDLE, "provider switch is not a reboot");
        check(b.flow.poll("broadcast:new", 12000, true, null) == StartupFlow.COUNTDOWN, "new legacy broadcast starts countdown");
        check(b.flow.poll("broadcast:new", 15000, true, null) == StartupFlow.LAUNCH, "legacy boot still works");
        check(b.flow.poll("broadcast:new", 16000, true, null) == StartupFlow.IDLE, "legacy boot launches only once");
        b = new Boot(); b.at(10000, null); b.at(13000, null);
        b.state.record("count:2", "已向系统发送打开请求", 13000);
        b.flow.cancel("count:2", 14000);
        check(b.state.status().equals("已向系统发送打开请求"), "later navigation does not erase launch result");
        check(b.flow.poll(null, 15000, true, null) == StartupFlow.IDLE, "missing identity never launches");
        System.out.println("PASS: " + checks + " startup flow regression checks (repeated HOME, overlays, late readiness, cancellation, bounded wait, process restart, standby)");
    }
}
