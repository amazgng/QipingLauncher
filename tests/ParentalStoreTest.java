package cn.wengmang.qiping;
import java.util.*;
/** Rules, persistence, authentication, schedule edges and failed-write behavior. */
public final class ParentalStoreTest {
    private static int n;
    private static void check(boolean v,String text){if(!v)throw new AssertionError(text);n++;}
    private static long at(int h,int m){Calendar c=Calendar.getInstance();c.set(2026,9,21,h,m,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    public static void main(String[] args)throws Exception{
        ShortcutStoreTest.MemoryPreferences prefs=new ShortcutStoreTest.MemoryPreferences();ParentalStore p=new ParentalStore(prefs);
        check(!p.enabled()&&!p.configured(),"upgrade defaults off without configuring credentials");
        check(!p.setEnabled(true),"cannot enable before setup");
        check(!p.setAllowed(Arrays.asList("bad/../pkg")),"malformed package rejected");
        check(!p.setAllowed(Arrays.asList("test.one","test.one")),"duplicate package rejected");
        check(p.setAllowed(Arrays.asList("test.one","test.two")),"allowlist saves");
        ParentalStore.Credentials c=ParentalStore.prepare("628417");check(c.recoveryCode.matches("[0-9]{12}"),"recovery code has 12 digits");
        check(p.saveCredentials(c)&&p.configured(),"credentials save atomically");
        check(!prefs.getAll().toString().contains("628417")&&!prefs.getAll().toString().contains(c.recoveryCode),"no plaintext password or recovery code persists");
        check(p.setEnabled(true),"configured mode enables");check(p.mayLaunch("test.one",at(10,0)),"allowed app launches with unrestricted hours");
        check(!p.mayLaunch("test.other",at(10,0)),"unlisted app denied");
        check(p.setWindow(19*60,20*60)&&p.setSchedule(true),"daily window saves");
        check(!p.mayLaunch("test.one",at(18,59)),"before opening blocked");check(p.mayLaunch("test.one",at(19,0)),"opening minute included");
        check(p.mayLaunch("test.one",at(19,59)),"last minute included");check(!p.mayLaunch("test.one",at(20,0)),"closing minute excluded");
        check(p.setWindow(22*60,6*60),"overnight window accepted");
        check(p.mayLaunch("test.one",at(23,0))&&p.mayLaunch("test.one",at(0,0))&&p.mayLaunch("test.one",at(5,59)),"overnight midnight range works");
        check(!p.mayLaunch("test.one",at(6,0))&&!p.mayLaunch("test.one",at(12,0)),"overnight daytime blocked");
        check(!p.setWindow(1,1)&&!p.setWindow(-1,60)&&!p.setWindow(60,1440),"invalid ranges do not replace old range");
        check(p.start()==1320&&p.end()==360,"rejected ranges preserve state");
        check(ParentalStore.parseTime("0000")==0&&ParentalStore.parseTime("2359")==1439,"valid time extremes");
        check(ParentalStore.parseTime("2400")==-1&&ParentalStore.parseTime("1260")==-1&&ParentalStore.parseTime("7:30")==-1,"invalid time input rejected");
        check(!ParentalStore.inside(-1,0,60)&&!ParentalStore.inside(1440,0,60),"invalid clock minute denied");
        p=new ParentalStore(prefs);check(p.enabled()&&p.contains("test.one")&&p.scheduled(),"mode and rules survive instance recreation");
        check(p.authenticate("628417",false,100000,1000,"boot1")==ParentalStore.OK,"correct PIN verifies");
        for(int i=0;i<4;i++)check(p.authenticate("000000",false,100000+i,1000+i,"boot1")==ParentalStore.WRONG,"wrong PIN "+i);
        check(p.authenticate("000000",false,100004,1004,"boot1")==ParentalStore.LOCKED,"fifth wrong attempt enters cooldown");
        check(new ParentalStore(prefs).remaining(100004,1004,"boot1")==30000,"cooldown survives recreation");
        check(p.authenticate("628417",false,100005,1005,"boot1")==ParentalStore.LOCKED,"correct PIN cannot bypass cooldown");
        check(p.remaining(1,1005,"boot1")==29999,"same boot uses monotonic time despite wall clock rollback");
        check(p.remaining(100005,5,"boot2")==29999,"reboot retains wall clock cooldown");
        check(p.authenticate("628417",false,130004,31004,"boot1")==ParentalStore.OK,"PIN works at cooldown expiry");
        check(p.authenticate(c.recoveryCode,true,140000,40000,"boot1")==ParentalStore.OK,"recovery code verifies");
        check(p.authenticate("628417",true,140001,40001,"boot1")==ParentalStore.WRONG,"PIN cannot substitute for recovery");
        ParentalStore.Credentials next=ParentalStore.prepare("492837");
        prefs.commitSucceeds=false;check(!p.saveCredentials(next),"failed credential replacement reported");
        check(!p.setEnabled(false)&&p.enabled(),"failed disable preserves enabled gate in memory");
        check(!p.setAllowed(Arrays.asList("test.other"))&&p.contains("test.one"),"failed allowlist update preserves rules");
        prefs.commitSucceeds=true;
        check(p.authenticate("628417",false,150000,50000,"boot1")==ParentalStore.OK,"old credential works after failed replacement");
        check(p.saveCredentials(next),"new password saves");check(p.authenticate("628417",false,150001,50001,"boot1")==ParentalStore.WRONG,"old PIN invalidated");
        check(p.authenticate(c.recoveryCode,true,150002,50002,"boot1")==ParentalStore.WRONG,"old recovery invalidated");
        check(p.authenticate("492837",false,150003,50003,"boot1")==ParentalStore.OK,"new PIN works");
        check(p.setAllowed(Collections.<String>emptyList())&&!p.mayLaunch("test.one",at(23,0)),"empty allowlist blocks all apps while retaining mode");
        check(p.setEnabled(false)&&p.mayLaunch("test.one",at(12,0)),"parent disable restores normal launch behavior");
        prefs.edit().putBoolean("enabled",true).putString("allowed","test.one\n../broken").commit();check(p.allowed().isEmpty()&&!p.mayLaunch("test.one",at(23,0)),"corrupt allowlist fails closed");
        prefs.edit().putString("allowed","test.one").putInt("start",2000).commit();check(!p.mayLaunch("test.one",at(23,0)),"corrupt active window fails closed");
        maintenance(c);
        System.out.println("PASS: "+n+" parental checks");
    }
    private static void maintenance(ParentalStore.Credentials c)throws Exception{
        ShortcutStoreTest.MemoryPreferences prefs=new ShortcutStoreTest.MemoryPreferences();ParentalStore p=new ParentalStore(prefs);
        check(p.saveCredentials(c)&&p.setAllowed(Arrays.asList("test.one"))&&p.setEnabled(true),"1.0 fixture configured");
        long wall=at(21,0),elapsed=100000;
        for(int i=0;i<5;i++)p.authenticate("000000",false,wall,elapsed,"old-boot");
        check(p.remaining(wall,elapsed,"old-boot")==30000,"five errors persist a full cooldown");
        long rolledBack=wall-86400000;
        check(p.authenticate("628417",false,rolledBack,500,"new-boot")==ParentalStore.LOCKED,"reboot plus one-day rollback does not bypass lock");
        p=new ParentalStore(prefs);
        check(p.remaining(rolledBack,10500,"new-boot")==20000,"rebased cooldown decreases even with frozen wall clock");
        check(new ParentalStore(prefs).remaining(rolledBack-86400000,20500,"new-boot")==10000,"recreation and further wall rollback do not extend cooldown");
        check(p.authenticate(c.recoveryCode,true,rolledBack,30499,"new-boot")==ParentalStore.LOCKED,"recovery shares cooldown through final millisecond");
        check(p.authenticate("628417",false,rolledBack,30500,"new-boot")==ParentalStore.OK,"correct PIN works exactly 30 seconds after rebasing");
        check(p.authenticate(c.recoveryCode,true,rolledBack,30600,"new-boot")==ParentalStore.OK,"recovery also works after rebased expiry");
        check(p.remaining(rolledBack,1,"third-boot")==0,"successful authentication does not reintroduce lock on reboot");

        prefs.edit().putString("lock_boot","old-boot").putLong("lock_elapsed",130000).putLong("lock_wall",wall+30000).commit();
        check(p.remaining(wall+10000,100,"boot-a")==20000,"accurate reboot clock preserves only unexpired remainder");
        check(p.remaining(wall+15000,50,"boot-b")==15000,"second reboot cannot erase an active cooldown");
        check(p.remaining(wall+1000000,100,"boot-b")==14950,"same-boot forward clock adjustment cannot shorten cooldown");
        check(p.remaining(wall+30000,1,"boot-c")==0,"expired cross-boot lock clears");

        prefs.edit().putString("lock_boot","old-boot").putLong("lock_elapsed",130000).putLong("lock_wall",wall+30000).commit();
        prefs.commitSucceeds=false;
        check(p.authenticate("628417",false,rolledBack,500,"failed-boot")==ParentalStore.ERROR,"failed rebase write never authorizes a PIN");
        check("old-boot".equals(prefs.getString("lock_boot","")),"failed rebase restores old lock identity");
        prefs.commitSucceeds=true;
        check(p.authenticate("628417",false,rolledBack,1000,"failed-boot")==ParentalStore.LOCKED,"retry after storage recovery keeps a cooldown");
        check(p.authenticate("628417",false,rolledBack,31000,"failed-boot")==ParentalStore.OK,"storage recovery allows normal bounded expiry");
        prefs.edit().putString("lock_boot","broken").putString("lock_elapsed","bad").commit();
        check(p.authenticate("628417",false,wall,1,"broken")==ParentalStore.ERROR,"wrong-typed lock record reports failure without authorization");
        check(p.saveCredentials(c),"reset fixture after invalid lock test");

        check(p.setWindow(21*60,22*60)&&p.setSchedule(true),"custom restricted window saves");
        check(!p.mayLaunch("test.one",at(20,0)),"custom window is closed at 20:00");
        prefs.edit().putString("start","invalid").commit();
        check(!p.validSchedule()&&!p.mayLaunch("test.one",at(20,0)),"wrong-typed start cannot silently fall back to 19:00");
        check(p.window().contains("异常"),"damaged rule is visible to parent");
        check(!p.setSchedule(true),"damaged rule cannot be enabled as a default");
        check(p.setWindow(21*60,22*60)&&p.mayLaunch("test.one",at(21,0)),"explicit repair restores valid access");
        prefs.edit().putLong("end",22*60).commit();
        check(!p.mayLaunch("test.one",at(21,0)),"wrong numeric type in end denies even inside previous window");
        prefs.edit().putInt("end",22*60).remove("start").commit();
        check(!p.validSchedule()&&!p.mayLaunch("test.one",at(20,0)),"partially missing rule cannot silently widen window");
        prefs.edit().remove("end").commit();
        check(p.validSchedule()&&p.mayLaunch("test.one",at(19,30))&&!p.mayLaunch("test.one",at(20,0)),"legacy default rule with both fields absent stays compatible");
        check(p.setSchedule(true)&&prefs.contains("start")&&prefs.contains("end"),"new default schedule persists both explicit fields");
        prefs.edit().putBoolean("start",true).putString("end","invalid").commit();
        prefs.commitSucceeds=false;
        check(!p.setWindow(21*60,22*60)&&!p.mayLaunch("test.one",at(21,30)),"failed repair preserves closed gate");
        prefs.commitSucceeds=true;
        check(p.setWindow(22*60,6*60)&&p.mayLaunch("test.one",at(23,0))&&!p.mayLaunch("test.one",at(6,0)),"repair can restore an overnight rule");
    }
}
