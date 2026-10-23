package cn.wengmang.qiping;

import java.util.Map;

/** Migration/read compatibility and failed-persistence semantics, with no Android runtime. */
public final class DisplayPreferencesTest {
    private static int count;
    private static void check(boolean ok){count++;if(!ok)throw new AssertionError("Display check "+count);}
    public static void main(String[] args) {
        ShortcutStoreTest.MemoryPreferences p=new ShortcutStoreTest.MemoryPreferences();
        check(DisplayPreferences.font(p)==0);check(p.getAll().isEmpty());
        p.edit().putBoolean("large_text",true).putString("category_1","live.app").putString("favorites","keep").putInt("performance_mode",1).apply();
        Map<String,?> before=p.getAll();
        check(DisplayPreferences.font(p)==1);check(p.getAll().equals(before));
        check(DisplayPreferences.extraScale(0)==1f&&DisplayPreferences.extraScale(1)==1f&&DisplayPreferences.extraScale(2)>1f);
        for(int font=0;font<3;font++) {
            check(DisplayPreferences.setFont(p,font));check(DisplayPreferences.font(p)==font);
            check(p.getInt("font_size",-1)==font&&p.getBoolean("large_text",false)==(font>0));
        }
        check("live.app".equals(p.getString("category_1",""))&&"keep".equals(p.getString("favorites",""))&&p.getInt("performance_mode",0)==1);
        before=p.getAll();check(!DisplayPreferences.setFont(p,-1)&&!DisplayPreferences.setFont(p,3));check(before.equals(p.getAll()));
        p.edit().putInt("font_size",9).putBoolean("large_text",true).apply();check(DisplayPreferences.font(p)==1);
        p.edit().putString("font_size","bad").putBoolean("large_text",false).apply();check(DisplayPreferences.font(p)==0);
        p.edit().putString("large_text","bad").apply();check(DisplayPreferences.font(p)==0);
        p.edit().remove("font_size").putBoolean("large_text",true).apply();before=p.getAll();p.commitSucceeds=false;
        check(!DisplayPreferences.setFont(p,2));check(before.equals(p.getAll()));check(DisplayPreferences.font(p)==1);
        p.commitSucceeds=true;check(DisplayPreferences.setFont(p,0));before=p.getAll();p.commitSucceeds=false;
        check(!DisplayPreferences.setFont(p,2));check(before.equals(p.getAll()));
        p.commitSucceeds=true;
        for(int m=0;m<3;m++){check(DisplayPreferences.setMargin(p,m));check(DisplayPreferences.margin(p)==m);}
        before=p.getAll();check(!DisplayPreferences.setMargin(p,3)&&!DisplayPreferences.setMargin(p,-1));check(before.equals(p.getAll()));
        check(DisplayPreferences.motion(p));check(DisplayPreferences.setMotion(p,false));check(p.getBoolean("reduce_motion",false)&&!DisplayPreferences.motion(p));
        check(DisplayPreferences.setMotion(p,true)&&!p.getBoolean("reduce_motion",true));
        before=p.getAll();p.commitSucceeds=false;
        check(!DisplayPreferences.setMargin(p,0)&&!DisplayPreferences.setMotion(p,false));check(before.equals(p.getAll()));
        System.out.println("PASS: DisplayPreferences "+count+" checks");
    }
}
