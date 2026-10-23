package cn.wengmang.qiping;

/** Only the visible Activity polls this coordinator; a receiver never launches an Activity. */
final class WakeFlow {
    private final WakeState state;
    private long observed=-1,deadline=-1;
    WakeFlow(WakeState state){this.state=state;}
    void suspend(){deadline=-1;}
    long deadline(){return deadline;}
    int poll(String boot,long now,String waitReason){
        if(!state.pending(boot,now)){suspend();return StartupFlow.IDLE;}
        if(observed!=state.sequence()){observed=state.sequence();suspend();}
        if(waitReason!=null){suspend();state.record("待机唤醒："+waitReason,now);return StartupFlow.WAIT;}
        if(deadline<0){deadline=now+StartupFlow.COUNTDOWN_MS;state.record("唤醒后即将打开直播，倒计时中",now);}
        if(now<deadline)return StartupFlow.COUNTDOWN;
        suspend();return state.claim(boot,now)?StartupFlow.LAUNCH:StartupFlow.IDLE;
    }
}
