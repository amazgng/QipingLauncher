package cn.wengmang.qiping;

import android.content.SharedPreferences;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local launcher rules. Separate from exported desktop settings and OS policy. */
final class ParentalStore {
    static final String PREFS="parental_control";
    static final int OK=0,WRONG=1,LOCKED=2,ERROR=3,MAX_APPS=128;
    private static final int ITERATIONS=120000;
    private static final long COOLDOWN=30000;
    private final SharedPreferences prefs;
    ParentalStore(SharedPreferences prefs){this.prefs=prefs;}
    boolean enabled(){try{return prefs.getBoolean("enabled",false);}catch(RuntimeException e){return true;}}
    boolean configured(){return string("pin_hash").matches("[0-9a-f]{64}")&&string("salt").matches("[0-9a-f]{32}");}
    boolean scheduled(){try{return prefs.getBoolean("schedule",false);}catch(RuntimeException e){return true;}}
    private String string(String key){try{return prefs.getString(key,"");}catch(RuntimeException e){return "";}}
    private int number(String key,int fallback){try{return prefs.getInt(key,fallback);}catch(RuntimeException e){return fallback;}}
    private int minute(String key,int initial){try{return prefs.getInt(key,initial);}catch(RuntimeException e){return -1;}}
    int start(){return minute("start",19*60);}
    int end(){return minute("end",20*60);}
    boolean validSchedule(){
        // Older versions may legitimately omit BOTH fields for the default window.
        try{return prefs.contains("start")==prefs.contains("end")&&validWindow(start(),end());}
        catch(RuntimeException e){return false;}
    }
    static boolean validPackage(String value){return value!=null&&value.length()<=255&&value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");}
    List<String> allowed(){
        List<String> values=new ArrayList<String>();String raw=string("allowed");
        if(raw.length()>32768)return values;
        for(String value:raw.split("\n")){
            if(value.length()==0)continue;
            if(!validPackage(value)||values.contains(value)||values.size()>=MAX_APPS)return new ArrayList<String>();
            values.add(value);
        }
        return values;
    }
    boolean contains(String pkg){return validPackage(pkg)&&allowed().contains(pkg);}
    boolean setAllowed(List<String> values){
        if(values==null||values.size()>MAX_APPS)return false;
        StringBuilder out=new StringBuilder();List<String> unique=new ArrayList<String>();
        for(String pkg:values){if(!validPackage(pkg)||unique.contains(pkg))return false;unique.add(pkg);if(out.length()>0)out.append('\n');out.append(pkg);}
        if(out.length()>32768)return false;
        return write("allowed",out.toString());
    }
    boolean setEnabled(boolean value){return (!value||configured()&&!allowed().isEmpty())&&write("enabled",value);}
    boolean setSchedule(boolean value){return value?validSchedule()&&write("schedule",true,"start",start(),"end",end()):write("schedule",false);}
    boolean setWindow(int start,int end){return validWindow(start,end)&&write("start",start,"end",end);}
    static boolean validWindow(int start,int end){return start>=0&&start<1440&&end>=0&&end<1440&&start!=end;}
    static boolean inside(int minute,int start,int end){
        return minute>=0&&minute<1440&&validWindow(start,end)&&(start<end?minute>=start&&minute<end:minute>=start||minute<end);
    }
    boolean timeAllowed(long wallTime){
        if(!scheduled())return true;
        if(!validSchedule())return false;
        Calendar c=Calendar.getInstance();c.setTimeInMillis(wallTime);
        return inside(c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE),start(),end());
    }
    boolean mayLaunch(String pkg,long wallTime){return !enabled()||configured()&&contains(pkg)&&timeAllowed(wallTime);}
    static String time(int minute){return minute>=0&&minute<1440?String.format(java.util.Locale.ROOT,"%02d:%02d",minute/60,minute%60):"时间无效";}
    String window(){return !scheduled()?"全天可打开":!validSchedule()?"时间设置异常，请家长重新设置":time(start())+"—"+time(end())+(start()>end()?"（跨午夜）":"");}
    static int parseTime(String value){if(value==null||!value.matches("[0-9]{4}"))return -1;int h=Integer.parseInt(value.substring(0,2)),m=Integer.parseInt(value.substring(2));return h<24&&m<60?h*60+m:-1;}
    static final class Credentials {
        final String salt,pinHash,recoveryHash,recoveryCode;
        Credentials(String salt,String pin,String hash,String code){this.salt=salt;pinHash=pin;recoveryHash=hash;recoveryCode=code;}
    }
    static Credentials prepare(String pin)throws Exception{
        if(pin==null||!pin.matches("[0-9]{6}"))throw new IllegalArgumentException("请输入 6 位数字");
        SecureRandom random=new SecureRandom();byte[] salt=new byte[16];random.nextBytes(salt);
        StringBuilder recovery=new StringBuilder();for(int i=0;i<12;i++)recovery.append(random.nextInt(10));
        String code=recovery.toString();return new Credentials(hex(salt),derive(pin,salt),derive(code,salt),code);
    }
    boolean saveCredentials(Credentials value){return value!=null&&write("salt",value.salt,"pin_hash",value.pinHash,"recovery_hash",value.recoveryHash,"failed",0,"lock_elapsed",0L,"lock_wall",0L,"lock_boot","");}
    /** Rebase a previous boot's bounded remainder ONCE; negative means storage/clock failure. */
    long remaining(long wall,long elapsed,String boot){
        try{
            if(boot==null||boot.length()==0||elapsed<0||elapsed>Long.MAX_VALUE-COOLDOWN)return -1;
            boolean sameBoot=boot.equals(string("lock_boot"));
            long until=prefs.getLong(sameBoot?"lock_elapsed":"lock_wall",0);
            if(until<=0)return 0;
            long left=boundedRemaining(until,sameBoot?elapsed:wall);
            if(sameBoot)return left;
            // Persist the new monotonic deadline so reopening/recreating this screen cannot extend it.
            // Keep the wall deadline until expiry: another reboot must not clear an active lock.
            if(!write("lock_elapsed",left==0?0L:elapsed+left,"lock_boot",boot,"lock_wall",left==0?0L:until))return -1;
            return left;
        }catch(RuntimeException e){return -1;}
    }
    private static long boundedRemaining(long until,long now){
        if(until<=now)return 0;
        long delta=until-now;
        return delta<0?COOLDOWN:Math.min(COOLDOWN,delta);
    }
    /** Called off the UI thread. Attempt counters are durable before accepting a password. */
    int authenticate(String code,boolean recovery,long wall,long elapsed,String boot){
        long left=remaining(wall,elapsed,boot);
        if(left<0)return ERROR;
        if(left>0)return LOCKED;
        try{
            if(!configured())return ERROR;
            String stored=string(recovery?"recovery_hash":"pin_hash");
            boolean correct=code!=null&&code.matches(recovery?"[0-9]{12}":"[0-9]{6}")&&stored.matches("[0-9a-f]{64}")&&MessageDigest.isEqual(unhex(stored),unhex(derive(code,unhex(string("salt")))));
            if(correct)return write("failed",0,"lock_elapsed",0L,"lock_wall",0L,"lock_boot","")?OK:ERROR;
            int failed=Math.min(4,Math.max(0,number("failed",0)))+1;
            if(failed>=5)return write("failed",0,"lock_elapsed",elapsed+COOLDOWN,"lock_wall",wall+COOLDOWN,"lock_boot",boot)?LOCKED:ERROR;
            return write("failed",failed)?WRONG:ERROR;
        }catch(Exception e){return ERROR;}
    }
    private static String derive(String text,byte[] salt)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(text.toCharArray(),salt,ITERATIONS,256);
        try{return hex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());}finally{spec.clearPassword();}
    }
    private static String hex(byte[] value){StringBuilder out=new StringBuilder();for(byte b:value)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    private static byte[] unhex(String value){byte[] out=new byte[value.length()/2];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);return out;}
    private boolean write(Object... pairs){
        Map<String,Object> before=new LinkedHashMap<String,Object>();
        try{
            Map<String,?> all=prefs.getAll();SharedPreferences.Editor edit=prefs.edit();
            for(int i=0;i<pairs.length;i+=2){String key=(String)pairs[i];before.put(key,all.get(key));put(edit,key,pairs[i+1]);}
            if(edit.commit())return true;
            edit=prefs.edit();for(Map.Entry<String,Object> old:before.entrySet())put(edit,old.getKey(),old.getValue());edit.apply();
        }catch(RuntimeException ignored){}
        return false;
    }
    private static void put(SharedPreferences.Editor e,String key,Object value){
        if(value==null)e.remove(key);else if(value instanceof String)e.putString(key,(String)value);else if(value instanceof Boolean)e.putBoolean(key,(Boolean)value);else if(value instanceof Integer)e.putInt(key,(Integer)value);else if(value instanceof Long)e.putLong(key,(Long)value);
    }
}
