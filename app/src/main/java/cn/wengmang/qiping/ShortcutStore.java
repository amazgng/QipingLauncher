package cn.wengmang.qiping;

import android.content.SharedPreferences;

/** Category bindings are separate from v1 favorites, so upgrades never erase them. */
final class ShortcutStore {
    static final int COUNT=4;
    static final String[] NAMES={"影视","直播","音乐","相册","全部应用"};
    private final SharedPreferences preferences;
    ShortcutStore(SharedPreferences preferences){this.preferences=preferences;}
    String get(int slot){check(slot);return preferences.getString("category_"+slot,"");}
    void put(int slot,String pkg){check(slot);preferences.edit().putString("category_"+slot,pkg==null?"":pkg).apply();}
    boolean contains(String pkg){for(int i=0;i<COUNT;i++)if(get(i).equals(pkg))return true;return false;}
    private static void check(int slot){if(slot<0||slot>=COUNT)throw new IllegalArgumentException("Invalid category");}
}
