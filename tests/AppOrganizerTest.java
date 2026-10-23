package cn.wengmang.qiping;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Host-side persistence/ordering/failure tests using the existing SharedPreferences fake. */
public final class AppOrganizerTest {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    public static void main(String[] args) {
        ShortcutStoreTest.MemoryPreferences prefs=new ShortcutStoreTest.MemoryPreferences();
        prefs.edit().putString("favorites","legacy").putString("category_1","live.app").putString("handled_boot","boot").apply();
        AppOrganizer store=new AppOrganizer(prefs);
        check(store.enabled()&&store.recent().isEmpty()&&store.saved().isEmpty(),"new defaults");
        store.recordLaunch("app.one","app.one/.Main",1,false);
        check(store.recent().isEmpty(),"rejected launch never records");
        for(int i=0;i<15;i++)check(store.recordLaunch("app.p"+i,"app.p"+i+"/.Main",i,true)==AppOrganizer.OK,"record accepted");
        check(store.recent().size()==12&&store.recent().get(0).packageName.equals("app.p14")&&store.recent().get(11).packageName.equals("app.p3"),"bounded most recent first");
        store.recordLaunch("app.p5","app.p5/.Changed",0,true);
        check(store.recent().size()==12&&store.recent().get(0).packageName.equals("app.p5")&&store.recent().get(0).component.equals("app.p5/.Changed"),"dedupe and component change; backward clock still most recent");
        store.setEnabled(false);String prior=prefs.getString("recent_v1","");
        store.recordLaunch("app.other","app.other/.Main",22,true);
        check(prior.equals(prefs.getString("recent_v1","")),"disabled does not collect or rewrite");
        store=new AppOrganizer(prefs);check(!store.enabled()&&store.recent().size()==12,"disabled and history survive restart");
        check(store.clearRecent()==AppOrganizer.OK&&store.recent().isEmpty()&&!store.enabled(),"clear preserves disabled state");
        store.setEnabled(true);store.recordLaunch("app.one","app.one/.Main",3,true);
        for(int i=0;i<12;i++)check(store.add("saved.p"+i)==AppOrganizer.OK,"add collection");
        check(store.add("saved.p0")==AppOrganizer.EXISTS&&store.add("saved.extra")==AppOrganizer.FULL,"duplicates and capacity");
        check(store.move("saved.p0",-1)==AppOrganizer.EDGE&&store.move("saved.p11",1)==AppOrganizer.EDGE,"no wrap at boundaries");
        check(store.move("saved.p5",1)==AppOrganizer.OK&&store.saved().get(6).packageName.equals("saved.p5"),"right across row boundary");
        check(store.move("saved.p5",-1)==AppOrganizer.OK&&store.saved().get(5).packageName.equals("saved.p5"),"left across row boundary");
        check(store.move("saved.p5",2)==AppOrganizer.INVALID,"reject invalid move");
        check(store.remove("saved.p3")==AppOrganizer.OK&&store.add("saved.extra")==AppOrganizer.OK,"remove frees slot");
        store=new AppOrganizer(prefs);check(store.size()==12&&store.saved().get(11).packageName.equals("saved.extra"),"order survives restart");
        List<AppOrganizer.Item> snapshot=store.saved();snapshot.clear();check(store.size()==12,"snapshot cannot mutate store");
        check(store.removeUnavailable(new HashSet<String>(Arrays.asList("saved.p0","saved.p5")))==AppOrganizer.OK&&store.size()==2&&store.position("saved.p5")==1,"explicit missing cleanup retains relative order");
        check(store.recent().size()==1,"collection cleanup does not touch history");
        check(prefs.getString("favorites","").equals("legacy")&&prefs.getString("category_1","").equals("live.app")&&prefs.getString("handled_boot","").equals("boot"),"legacy bindings and boot keys untouched");
        String saved=prefs.getString("collection_v1","");prefs.commitSucceeds=false;
        check(store.add("failed.app")==AppOrganizer.WRITE_ERROR&&!store.contains("failed.app"),"write failure does not report success");
        check(prefs.getString("collection_v1","").equals(saved),"failed write restores in-memory preference");
        check(store.setEnabled(false)==AppOrganizer.WRITE_ERROR&&store.enabled(),"failed toggle stays visibly unchanged");
        prefs.commitSucceeds=true;
        String[] corrupt={"2","1\n","1\ninvalid/pkg\t\t0","1\nvalid.app\t\t0\nvalid.app\t\t0","1\nvalid.app\t\t-1","1\nvalid.app\t\txyz","1\nvalid.app\tunexpected\t0"};
        for(String raw:corrupt) {
            prefs.edit().putString("collection_v1",raw).apply();store=new AppOrganizer(prefs);
            check(store.savedError()&&store.saved().isEmpty(),"bad collection has readable error state");
            check(store.add("new.app")==AppOrganizer.READ_ERROR&&raw.equals(prefs.getString("collection_v1","")),"corrupt data never silently overwritten");
        }
        prefs.edit().putInt("collection_v1",123).putString("recent_v1","bad").apply();store=new AppOrganizer(prefs);
        check(store.savedError()&&store.recentError(),"wrong preference type safely rejected");
        check(store.recordLaunch("app.one","app.one/.Main",1,true)==AppOrganizer.READ_ERROR,"launch never clobbers corrupt history");
        check(store.clearSaved()==AppOrganizer.OK&&!store.savedError()&&store.recentError(),"explicit repair only collection");
        check(store.clearRecent()==AppOrganizer.OK&&!store.recentError(),"explicit history repair");
        check(store.recordLaunch("app.one","other.app/.Main",1,true)==AppOrganizer.INVALID,"mismatched component rejected");
        check(store.add("app.one\nother.app")==AppOrganizer.INVALID,"no injected serialization rows");
        prefs.edit().putString("record_recent","broken").apply();store=new AppOrganizer(prefs);
        check(!store.enabled(),"corrupt record switch fails closed");
        System.out.println("PASS: AppOrganizer "+checks+" checks");
    }
}
