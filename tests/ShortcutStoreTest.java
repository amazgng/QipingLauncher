package cn.wengmang.qiping;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Host-side behavior test; no Android runtime or mocking framework is required. */
public final class ShortcutStoreTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args) {
        MemoryPreferences prefs=new MemoryPreferences();prefs.edit().putString("favorites","legacy-v1-data").apply();
        ShortcutStore store=new ShortcutStore(prefs);
        for(int i=0;i<4;i++)check("".equals(store.get(i)));
        store.put(0,"video.app");store.put(1,"video.app");store.put(2,"music.app");store.put(3,"photos.app");
        ShortcutStore restarted=new ShortcutStore(prefs);
        check("video.app".equals(restarted.get(0))&&"video.app".equals(restarted.get(1)));
        check("music.app".equals(restarted.get(2))&&"photos.app".equals(restarted.get(3)));
        restarted.put(0,"another.video");restarted.put(3,"");
        check("video.app".equals(restarted.get(1))&&restarted.contains("another.video"));
        check(!restarted.contains("photos.app")&&"".equals(restarted.get(3)));
        check("legacy-v1-data".equals(prefs.getString("favorites","")));
        boolean rejected=false;try{store.put(4,"bad");}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        System.out.println("PASS: four category bindings, shared app, replacement, restart, unbind, v1 preservation, bounds");
    }
    static final class MemoryPreferences implements SharedPreferences {
        boolean commitSucceeds=true;
        private final Map<String,Object> values=new HashMap<String,Object>();
        public Map<String,?> getAll(){return new HashMap<String,Object>(values);}
        public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String> d){return values.containsKey(k)?(Set<String>)values.get(k):d;}
        public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}
        public long getLong(String k,long d){return values.containsKey(k)?(Long)values.get(k):d;}
        public float getFloat(String k,float d){return values.containsKey(k)?(Float)values.get(k):d;}
        public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}
        public boolean contains(String k){return values.containsKey(k);}
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
        public Editor edit(){return new Editor(){
            private final Map<String,Object> pending=new HashMap<String,Object>();private boolean clear;
            public Editor putString(String k,String v){pending.put(k,v);return this;}
            public Editor putStringSet(String k,Set<String> v){pending.put(k,v);return this;}
            public Editor putInt(String k,int v){pending.put(k,v);return this;}
            public Editor putLong(String k,long v){pending.put(k,v);return this;}
            public Editor putFloat(String k,float v){pending.put(k,v);return this;}
            public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
            public Editor remove(String k){pending.put(k,null);return this;}
            public Editor clear(){clear=true;return this;}
            public boolean commit(){apply();return commitSucceeds;}
            public void apply(){if(clear)values.clear();for(Map.Entry<String,Object> e:pending.entrySet()){if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());}}
        };}
    }
}
