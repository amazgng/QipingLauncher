package cn.wengmang.qiping;
import java.io.*;
import java.util.*;
public final class PersonalizationTest {
    static int count;static void check(boolean value){if(!value)throw new AssertionError("check "+count);count++;}
    static void permutations(String prefix,String remaining,WorkspaceLayout layout,ShortcutStoreTest.MemoryPreferences prefs){
        if(remaining.length()>0){for(int i=0;i<remaining.length();i++)permutations(prefix+remaining.charAt(i),remaining.substring(0,i)+remaining.substring(i+1),layout,prefs);return;}
        String order=prefix.replaceAll("(?!^)(.)",",$1");check(WorkspaceLayout.validHome(order));
        for(int mask=0;mask<16;mask++){
            prefs.edit().putString("home_order",order).putInt("home_hidden",mask).commit();int[] visible=layout.home(true);check(visible.length==5-Integer.bitCount(mask));boolean containsApps=false;
            int last=-1;for(int slot:visible){check((mask&(1<<slot))==0);int index=order.indexOf(Integer.toString(slot));check(index>last);last=index;if(slot==4)containsApps=true;}check(containsApps);
            for(int selected=0;selected<5;selected++){int resolved=layout.visibleSelection(selected);check((mask&(1<<resolved))==0);}
        }
    }
    public static void main(String[] args)throws Exception {
        ShortcutStoreTest.MemoryPreferences desktop=new ShortcutStoreTest.MemoryPreferences(),apps=new ShortcutStoreTest.MemoryPreferences();WorkspaceLayout layout=new WorkspaceLayout(desktop,apps);
        permutations("","01234",layout,desktop);check(!WorkspaceLayout.validHome("0,0,1,2,3"));check(!WorkspaceLayout.validHome("0,1,2,3,5"));
        desktop.edit().putString("home_order","bad").putInt("home_hidden",31).commit();check(layout.homeOrder().equals("0,1,2,3,4")&&Arrays.equals(layout.home(true),new int[]{4}));check(!layout.hideHome(4,true));
        check(layout.resetHome());check(layout.moveHome(0,1));check(layout.homeOrder().equals("1,0,2,3,4"));check(layout.hideHome(0,true));check(layout.visibleSelection(0)==1);check(layout.hideHome(0,false));
        desktop.commitSucceeds=false;check(!layout.moveHome(0,1));check(layout.homeOrder().equals("1,0,2,3,4"));desktop.commitSucceeds=true;
        check(layout.hideApp("some.app",true)&&layout.hidden("some.app"));check(layout.hideApp("some.app",true)&&layout.hiddenApps().size()==1);check(!layout.hideApp("bad/package",true));check(layout.showAll()&&!layout.hidden("some.app"));
        check(!WorkspaceLayout.validPackages("a.app,a.app"));check(!WorkspaceLayout.validPackages("a.app,"));check(!WorkspaceLayout.validPackages("../a"));
        for(int delay:new int[]{0,5,10,15,30})check(layout.setSaver(delay)&&layout.saverMinutes()==delay);check(!layout.setSaver(1)&&layout.saverMinutes()==30);
        ExperiencePolicy gate=new ExperiencePolicy();check(gate.beginLaunch(100));check(!gate.beginLaunch(100)&&!gate.beginLaunch(1899));check(gate.beginLaunch(1900));gate.clearLaunch();check(gate.beginLaunch(1901));
        check(ExperiencePolicy.remaining(600000,300000,5)==0);check(ExperiencePolicy.remaining(500,1000,5)==300000);check(ExperiencePolicy.remaining(1,0,0)==Long.MAX_VALUE);
        for(int bits=0;bits<64;bits++){boolean actual=ExperiencePolicy.canIdle((bits&1)!=0,(bits&2)!=0,(bits&4)!=0,(bits&8)!=0,(bits&16)!=0,(bits&32)!=0);check(actual==(bits==7));}
        File dir=File.createTempFile("qiping-personal","");dir.delete();dir.mkdirs();ConfigStore store=new ConfigStore(desktop,apps,dir);
        try{
            desktop.edit().putString("category_1","keep.live").putInt("home_hidden",5).putInt("saver_minutes",15).commit();apps.edit().putString("app_order","one.app,two.app").putString("app_hidden","hidden.app").commit();
            Map<String,?> before=desktop.getAll(),beforeApps=apps.getAll();ConfigBackup captured=store.capture(),decoded=ConfigBackup.decode(captured.encode());check(decoded.extended&&decoded.saverMinutes==15&&decoded.homeHidden==5&&decoded.appOrder.equals("one.app,two.app"));
            ConfigBackup old=new ConfigBackup(1,Arrays.asList("","old.live","",""),new ArrayList<String>(),0,1,false,0,false,false);store.restore(old);check(desktop.getInt("home_hidden",0)==5&&desktop.getInt("saver_minutes",0)==15&&apps.getString("app_hidden","").equals("hidden.app"));store.undo();check(desktop.getAll().equals(before)&&apps.getAll().equals(beforeApps));
            ConfigBackup next=new ConfigBackup(1,old.categories,old.collection,0,1,false,0,false,false,"4,3,2,1,0",15,"two.app,one.app","one.app",5);store.restore(next);check(layout.homeOrder().equals("4,3,2,1,0")&&layout.saverMinutes()==5&&layout.hidden("one.app"));check(store.undoPreview().extended&&store.undoPreview().homeHidden==5);store.undo();check(desktop.getAll().equals(before)&&apps.getAll().equals(beforeApps));
            boolean rejected=false;try{new ConfigBackup(1,old.categories,old.collection,0,1,false,0,false,false,"4,3,2,1,0",16,"","",5);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
            // Pre-0.2.15 journal recovery must not remove fields the older version never owned.
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x51504331);for(int i=0;i<12;i++)out.writeByte(0);out.close();ConfigStore.atomic(new File(dir,"restore.pending"),bytes.toByteArray());check(store.recover());check(desktop.getInt("home_hidden",0)==5&&layout.hidden("hidden.app"));
        }finally{for(File f:dir.listFiles())f.delete();dir.delete();}
        System.out.println("PASS: Personalization "+count+" checks");
    }
}
