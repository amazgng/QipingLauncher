package cn.wengmang.qiping;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;
import java.util.Set;

/** App-only typography. Reading old preferences never migrates or clears them. */
final class DisplayPreferences {
    static final String FONT="font_size";
    static final String[] FONT_NAMES={"标准","大号","特大"};
    static final String[] MARGIN_NAMES={"较小 · 3%","标准 · 5%","较大 · 7%"};
    private DisplayPreferences(){}
    static int font(SharedPreferences prefs) {
        try{int value=prefs.getInt(FONT,-1);if(value>=0&&value<3)return value;}catch(RuntimeException ignored){}
        try{return prefs.getBoolean("large_text",false)?1:0;}catch(RuntimeException ignored){return 0;}
    }
    static int margin(SharedPreferences prefs) {
        try{return Math.max(0,Math.min(2,prefs.getInt("margin",1)));}catch(RuntimeException ignored){return 1;}
    }
    static boolean motion(SharedPreferences prefs) {
        try{return !prefs.getBoolean("reduce_motion",false);}catch(RuntimeException ignored){return true;}
    }
    static float extraScale(int mode){return mode==2?1.10f:1f;}
    static float extraScale(Context context){return extraScale(font(context.getSharedPreferences("desktop",0)));}
    static boolean setFont(SharedPreferences prefs,int value) {
        if(value<0||value>2)return false;
        return write(prefs,FONT,value,"large_text",value>0);
    }
    static boolean setMargin(SharedPreferences prefs,int value) {
        return value>=0&&value<3&&write(prefs,"margin",value,null,null);
    }
    static boolean setMotion(SharedPreferences prefs,boolean value){return write(prefs,"reduce_motion",!value,null,null);}
    static boolean setFamily(SharedPreferences prefs,String key,boolean value){return ("simple_home".equals(key)||"layout_locked".equals(key))&&write(prefs,key,value,null,null);}
    private static boolean write(SharedPreferences prefs,String key,Object value,String companion,Object companionValue) {
        try {
            Map<String,?> before=prefs.getAll();SharedPreferences.Editor edit=prefs.edit();put(edit,key,value);
            if(companion!=null)put(edit,companion,companionValue);
            if(edit.commit())return true;
            // commit may update SharedPreferences' in-memory map even when disk persistence fails.
            edit=prefs.edit();put(edit,key,before.get(key));if(companion!=null)put(edit,companion,before.get(companion));edit.apply();
        }catch(RuntimeException ignored){}
        return false;
    }
    @SuppressWarnings("unchecked") private static void put(SharedPreferences.Editor editor,String key,Object value) {
        if(value==null)editor.remove(key);
        else if(value instanceof Integer)editor.putInt(key,(Integer)value);
        else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
        else if(value instanceof String)editor.putString(key,(String)value);
        else if(value instanceof Long)editor.putLong(key,(Long)value);
        else if(value instanceof Float)editor.putFloat(key,(Float)value);
        else if(value instanceof Set)editor.putStringSet(key,(Set<String>)value);
    }
}
