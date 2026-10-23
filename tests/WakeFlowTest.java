package cn.wengmang.qiping;

/** Event-sequence regressions independent of manufacturer lifecycle ordering. */
public final class WakeFlowTest {
    static int checks;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static final class Cycle {
        final ShortcutStoreTest.MemoryPreferences prefs=new ShortcutStoreTest.MemoryPreferences();
        WakeState state=new WakeState(prefs);WakeFlow flow=new WakeFlow(state);
        Cycle(){check(state.setEnabled(true,100),"enable saved");}
        void wake(long now){state.screenOff("count:7",now-10000);check(state.screenOn("count:7",now),"observed OFF/ON creates wake");}
        int at(long now,String wait){return flow.poll("count:7",now,wait);}
        void recreate(){state=new WakeState(prefs);flow=new WakeFlow(state);}
    }
    public static void main(String[] args){
        Cycle c=new Cycle();long day=86400000;
        check(c.at(day,null)==StartupFlow.IDLE,"enable and ordinary Activity resume cannot trigger");
        check(!c.state.screenOn("count:7",day),"unpaired SCREEN_ON cannot trigger");
        c.wake(day);
        check(c.at(day,StartupFlow.WAIT_FOREGROUND)==StartupFlow.WAIT,"broadcast alone cannot launch from background");
        check(c.at(day+1000,null)==StartupFlow.COUNTDOWN,"wake works beyond ten minute boot window");
        check(c.at(day+3999,null)==StartupFlow.COUNTDOWN,"full three seconds visible");
        check(c.at(day+4000,null)==StartupFlow.LAUNCH,"ready HOME opens once");
        check(!c.state.screenOn("count:7",day+4100),"duplicate ON cannot re-arm");
        check(c.at(day+8000,null)==StartupFlow.IDLE,"returning from live does not reopen it");
        c.recreate();check(c.at(day+9000,null)==StartupFlow.IDLE,"handled wake survives process recreation");
        c.wake(day+600000);check(c.at(day+600000,null)==StartupFlow.COUNTDOWN,"next standby cycle can launch again");
        check(c.at(day+603000,null)==StartupFlow.LAUNCH,"second wake launches once");
        c=new Cycle();c.wake(day);c.at(day,null);c.flow.suspend();
        check(c.at(day+2000,StartupFlow.WAIT_FOREGROUND)==StartupFlow.WAIT,"focus loss suspends countdown");
        check(c.at(day+4000,null)==StartupFlow.COUNTDOWN,"focus return starts full countdown");
        check(c.at(day+6999,null)==StartupFlow.COUNTDOWN,"old deadline cannot launch");
        check(c.at(day+7000,null)==StartupFlow.LAUNCH,"new deadline respected");
        c=new Cycle();c.wake(day);c.at(day,null);c.state.cancel(day+1000);
        check(c.at(day+5000,null)==StartupFlow.IDLE,"Back cancels this wake");
        c.recreate();check(c.at(day+6000,null)==StartupFlow.IDLE,"cancellation survives recreation");
        c=new Cycle();c.state.screenOff("count:7",day);c.state.cancel(day+1);
        check(!c.state.screenOn("count:7",day+2),"user input before ON also cancels");
        c=new Cycle();c.wake(day);c.at(day,StartupFlow.WAIT_SCAN);
        check(c.at(day+60000,StartupFlow.WAIT_SCAN)==StartupFlow.WAIT,"exact wait bound allowed");
        check(c.at(day+60001,null)==StartupFlow.IDLE,"late return to HOME does not launch");
        check(c.state.status().contains(StartupFlow.WAIT_SCAN),"timeout preserves blocking reason");
        c=new Cycle();c.wake(day);c.at(day+59000,null);
        check(c.at(day+62000,null)==StartupFlow.IDLE,"countdown cannot cross wake window");
        c=new Cycle();c.wake(day);c.at(day,null);c.state.setEnabled(false,day+1);
        check(c.at(day+4000,null)==StartupFlow.IDLE,"disabling cancels countdown");
        c.state.setEnabled(true,day+5000);check(c.at(day+6000,null)==StartupFlow.IDLE,"re-enabling waits for next actual sleep");
        c=new Cycle();c.wake(day);c.recreate();
        check(c.at(day+1000,null)==StartupFlow.COUNTDOWN,"observed ON survives recreation within bounded window");
        c=new Cycle();c.state.screenOff("count:7",day);c.recreate();c.state.processStartedInteractive(day+10000);
        check(!c.state.screenOn("count:7",day+10001),"lost ON after process death is not guessed");
        c=new Cycle();c.state.screenOff("count:7",day);
        check(!c.state.screenOn("count:8",20000),"cold reboot does not become standby wake");
        c=new Cycle();c.wake(day);check(c.flow.poll("count:8",10000,null)==StartupFlow.IDLE,"saved wake from another boot is invalid");
        c=new Cycle();c.wake(day);check(c.at(day-1,null)==StartupFlow.IDLE,"backward monotonic time is rejected");
        c=new Cycle();c.wake(day);c.at(day,null);c.prefs.commitSucceeds=false;
        check(c.at(day+3000,null)==StartupFlow.IDLE,"cannot launch when claim persistence fails");
        check(c.state.status().contains("未能保存"),"failed persistence is diagnosed accurately");
        c=new Cycle();c.wake(day);c.at(day,null);c.state.screenOff("count:7",day+1000);
        check(c.at(day+3000,null)==StartupFlow.IDLE,"sleep during countdown invalidates prior wake");
        c.state.screenOn("count:7",day+5000);check(c.at(day+5000,null)==StartupFlow.COUNTDOWN,"new wake resets prior deadline");
        check(c.at(day+7999,null)==StartupFlow.COUNTDOWN,"new wake gets full delay");
        check(c.at(day+8000,null)==StartupFlow.LAUNCH,"new wake can finish normally");
        c=new Cycle();c.state.screenOff("count:7",day);c.state.screenOff("count:7",day+1);c.state.screenOn("count:7",day+2);
        check(c.state.sequence()==1,"duplicate OFF represents one wake");
        check(!c.prefs.contains("live_enabled")&&!c.prefs.contains("handled_boot"),"wake settings never alter cold boot settings");
        System.out.println("PASS: WakeFlow "+checks+" event-sequence checks");
    }
}
