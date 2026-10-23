package cn.wengmang.qiping;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small local lists. No usage access, network, legacy-slot migration or boot-state writes. */
final class AppOrganizer {
    static final int LIMIT=12;
    static final int OK=0, EXISTS=1, FULL=2, INVALID=3, READ_ERROR=4, WRITE_ERROR=5, MISSING=6, EDGE=7;
    private static final String RECENT="recent_v1", SAVED="collection_v1", ENABLED="record_recent";
    static final class Item {
        final String packageName,component;
        final long openedAt;
        Item(String pkg,String component,long time){this.packageName=pkg;this.component=component;openedAt=time;}
    }
    private final SharedPreferences prefs;
    private List<Item> recent=new ArrayList<Item>(),saved=new ArrayList<Item>();
    private boolean enabled=true,recentError,savedError;

    AppOrganizer(SharedPreferences prefs){this.prefs=prefs;reload();}
    void reload() {
        try{enabled=prefs.getBoolean(ENABLED,true);}catch(RuntimeException error){enabled=false;}
        try{recent=decode(prefs.getString(RECENT,"1"),true);recentError=false;}
        catch(RuntimeException error){recent=new ArrayList<Item>();recentError=true;}
        try{saved=decode(prefs.getString(SAVED,"1"),false);savedError=false;}
        catch(RuntimeException error){saved=new ArrayList<Item>();savedError=true;}
    }
    boolean enabled(){return enabled;}
    boolean recentError(){return recentError;}
    boolean savedError(){return savedError;}
    List<Item> recent(){return new ArrayList<Item>(recent);}
    List<Item> saved(){return new ArrayList<Item>(saved);}
    boolean contains(String pkg){return index(saved,pkg)>=0;}
    int position(String pkg){return index(saved,pkg);}
    int size(){return saved.size();}

    int setEnabled(boolean value) {
        try {
            if(!prefs.edit().putBoolean(ENABLED,value).commit()) {
                prefs.edit().putBoolean(ENABLED,enabled).apply();return WRITE_ERROR;
            }
            enabled=value;return OK;
        }catch(RuntimeException error){return WRITE_ERROR;}
    }
    /** Called with the result of an explicit application launch request, never on mere focus. */
    int recordLaunch(String pkg,String component,long time,boolean accepted) {
        if(!accepted||!enabled)return OK;
        if(recentError)return READ_ERROR;
        if(!validPackage(pkg)||!validComponent(pkg,component)||time<0)return INVALID;
        List<Item> next=recent();int index=index(next,pkg);if(index>=0)next.remove(index);
        next.add(0,new Item(pkg,component,time));while(next.size()>LIMIT)next.remove(next.size()-1);
        return save(true,next);
    }
    int clearRecent(){return save(true,new ArrayList<Item>());}
    int add(String pkg) {
        if(savedError)return READ_ERROR;
        if(!validPackage(pkg))return INVALID;
        if(contains(pkg))return EXISTS;
        if(saved.size()>=LIMIT)return FULL;
        List<Item> next=saved();next.add(new Item(pkg,"",0));return save(false,next);
    }
    int remove(String pkg) {
        if(savedError)return READ_ERROR;
        List<Item> next=saved();int index=index(next,pkg);if(index<0)return MISSING;
        next.remove(index);return save(false,next);
    }
    int move(String pkg,int delta) {
        if(savedError)return READ_ERROR;
        if(delta!=-1&&delta!=1)return INVALID;
        List<Item> next=saved();int index=index(next,pkg);if(index<0)return MISSING;
        int target=index+delta;if(target<0||target>=next.size())return EDGE;
        Item item=next.remove(index);next.add(target,item);return save(false,next);
    }
    int clearSaved(){return save(false,new ArrayList<Item>());}
    int removeUnavailable(Set<String> installed) {
        if(savedError)return READ_ERROR;
        List<Item> next=new ArrayList<Item>();
        for(Item item:saved)if(installed.contains(item.packageName))next.add(item);
        return next.size()==saved.size()?OK:save(false,next);
    }
    private int save(boolean history,List<Item> next) {
        String key=history?RECENT:SAVED;
        Object previous;
        try {
            // Preserve even corrupt source bytes unless an explicitly confirmed repair succeeds.
            previous=prefs.getAll().get(key);
            if(!prefs.edit().putString(key,encode(next)).commit()) {
                SharedPreferences.Editor restore=prefs.edit();
                if(previous instanceof String)restore.putString(key,(String)previous);
                else if(previous instanceof Integer)restore.putInt(key,(Integer)previous);
                else if(previous instanceof Boolean)restore.putBoolean(key,(Boolean)previous);
                else if(previous instanceof Long)restore.putLong(key,(Long)previous);
                else if(previous instanceof Float)restore.putFloat(key,(Float)previous);
                else if(previous==null)restore.remove(key);
                restore.apply();return WRITE_ERROR;
            }
        }catch(RuntimeException error){return WRITE_ERROR;}
        if(history){recent=next;recentError=false;}else{saved=next;savedError=false;}
        return OK;
    }
    private static int index(List<Item> list,String pkg) {
        for(int i=0;i<list.size();i++)if(list.get(i).packageName.equals(pkg))return i;
        return -1;
    }
    private static boolean validPackage(String pkg) {
        return pkg!=null&&pkg.length()<=255&&pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
    }
    private static boolean validComponent(String pkg,String component) {
        return component!=null&&component.length()<=768&&component.startsWith(pkg+"/")
                &&component.substring(pkg.length()+1).matches("[A-Za-z_.$][A-Za-z0-9_.$]*");
    }
    private static String encode(List<Item> list) {
        StringBuilder text=new StringBuilder("1");
        for(Item item:list)text.append('\n').append(item.packageName).append('\t').append(item.component).append('\t').append(item.openedAt);
        return text.toString();
    }
    private static List<Item> decode(String raw,boolean history) {
        if(raw==null||raw.length()>16384)throw new IllegalArgumentException("invalid list");
        String[] rows=raw.split("\n",-1);
        if(!"1".equals(rows[0])||rows.length>LIMIT+1)throw new IllegalArgumentException("invalid version or size");
        List<Item> list=new ArrayList<Item>();Set<String> seen=new HashSet<String>();
        for(int i=1;i<rows.length;i++) {
            String[] fields=rows[i].split("\t",-1);
            if(fields.length!=3||!validPackage(fields[0])||!seen.add(fields[0]))throw new IllegalArgumentException("invalid entry");
            long time=Long.parseLong(fields[2]);
            if(time<0||(history?!validComponent(fields[0],fields[1]):!fields[1].equals("")||time!=0))throw new IllegalArgumentException("invalid metadata");
            list.add(new Item(fields[0],fields[1],time));
        }
        return list;
    }
}
