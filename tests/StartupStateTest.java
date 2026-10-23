package cn.wengmang.qiping;

/** Behavior checks for durable startup decisions; no Android runtime required. */
public final class StartupStateTest {
    private static int checks;
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) {
        ShortcutStoreTest.MemoryPreferences prefs=new ShortcutStoreTest.MemoryPreferences();
        StartupState state=new StartupState(prefs);
        check(!state.enabled()&&!state.pending("count:1"),"feature defaults off");
        check(state.setEnabled(true,"count:1"),"enable persists");
        check(!state.claim("count:1",30000,true,true),"enabling applies next reboot");
        check(state.claim("count:2",30000,true,true),"first eligible HOME after reboot launches");
        check(!state.claim("count:2",33000,true,true),"HOME does not relaunch");
        state=new StartupState(prefs);
        check(!state.claim("count:2",60000,true,true),"new instance/process keeps consumed boot");
        check(!state.claim("count:2",150000,true,true),"standby wake in same boot does not relaunch");
        check(state.claim("count:3",60000,true,true),"subsequent real reboot launches again");
        // The attempt is already recorded even if Activity launch subsequently fails.
        check(!new StartupState(prefs).pending("count:3"),"failed/missing target cannot replay attempt");
        check(state.skip("count:4"),"user cancels this boot");
        check(!state.claim("count:4",10000,true,true),"cancel remains canceled");
        check(state.claim("count:5",10000,true,true),"cancel does not disable later boots");
        check(!state.claim("count:6",10000,false,true),"manual Studio/application entry skips automatic launch");
        check(!state.claim("count:6",15000,true,true),"later HOME in manually opened boot stays put");
        check(!state.claim("count:7",10000,true,false),"unresolved default launcher does not launch");
        check(state.pending("count:7"),"temporary default resolution does not spend startup attempt");
        check(state.claim("count:7",20000,true,true),"confirmed default can launch within bounded startup flow");
        check(!state.claim("count:8",StartupState.START_WINDOW_MS+1,true,true),"late HOME visit skips launch");
        check(state.claim("count:9",StartupState.START_WINDOW_MS,true,true),"startup window upper boundary allowed");
        check(state.claim("count:10",0,true,true),"startup window lower boundary allowed");
        check(!state.claim("count:11",-1,true,true),"invalid elapsed clock skips launch");
        check(!state.pending("")&&!state.pending(null),"invalid identity cannot launch");
        check(state.setEnabled(false,"count:11"),"disable persists");
        check(!state.claim("count:12",10000,true,true),"disabled feature stays off across reboot");
        state.setEnabled(true,"count:12");
        check(!state.claim("broadcast:old",10000,true,true),"provider fallback is not treated as reboot");
        check(!state.pending("broadcast:old"),"identity source switch is consumed");
        check(state.claim("broadcast:new",12000,true,true),"new legacy boot broadcast launches");
        check(!new StartupState(prefs).claim("broadcast:new",14000,true,true),"legacy process restart does not repeat");
        check(!state.claim("count:13",12000,true,true),"provider recovery does not repeat boot");
        check(state.claim("count:14",12000,true,true),"next counter-based boot still works");
        prefs.commitSucceeds=false;
        check(!state.claim("count:15",12000,true,true),"failed durable write prevents launch");
        check(!state.setEnabled(true,"count:15"),"failed preference save reported to UI");
        prefs.commitSucceeds=true;
        check(state.claim("count:16",12000,true,true),"future boot recovers after storage error");
        check(!state.claim("count:16",22000,true,true),"wall-clock adjustments cannot change decision");
        System.out.println("PASS: "+checks+" startup state checks (once per boot, cancel, restart, standby, manual entry, defaults, storage failure, legacy fallback)");
    }
}
