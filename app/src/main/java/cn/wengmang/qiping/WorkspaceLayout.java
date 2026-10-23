package cn.wengmang.qiping;

import android.content.SharedPreferences;
import java.util.*;

/** Display preferences only: never uninstalls apps or clears category bindings. */
final class WorkspaceLayout {
    static final String HOME_ORDER="home_order", HOME_HIDDEN="home_hidden", APP_ORDER="app_order", APP_HIDDEN="app_hidden", SAVER="saver_minutes";
    static final String DEFAULT_HOME="0,1,2,3,4";
    private final SharedPreferences desktop, apps;
    WorkspaceLayout(SharedPreferences desktop,SharedPreferences apps){this.desktop=desktop;this.apps=apps;}
    static boolean validHome(String text){if(text==null||text.length()!=9)return false;Set<String> seen=new HashSet<String>();for(String v:text.split(",",-1))if(!v.matches("[0-4]")||!seen.add(v))return false;return seen.size()==5;}
    static boolean validPackages(String text){if(text==null||text.length()>12000)return false;if(text.isEmpty())return true;String[] parts=text.split(",",-1);if(parts.length>128)return false;Set<String> seen=new HashSet<String>();for(String pkg:parts)if(!ConfigBackup.validPackage(pkg)||!seen.add(pkg))return false;return true;}
    static List<String> packages(String text){return validPackages(text)&&!text.isEmpty()?new ArrayList<String>(Arrays.asList(text.split(","))):new ArrayList<String>();}
    static String join(List<String> values){StringBuilder s=new StringBuilder();for(String value:values){if(s.length()>0)s.append(',');s.append(value);}return s.toString();}
    String homeOrder(){String raw=desktop.getString(HOME_ORDER,DEFAULT_HOME);return validHome(raw)?raw:DEFAULT_HOME;}
    int hiddenHome(){return desktop.getInt(HOME_HIDDEN,0)&15;}
    int[] home(boolean visibleOnly){List<Integer> result=new ArrayList<Integer>();for(String item:homeOrder().split(",")){int value=Integer.parseInt(item);if(!visibleOnly||(hiddenHome()&(1<<value))==0)result.add(value);}int[] resultArray=new int[result.size()];for(int i=0;i<result.size();i++)resultArray[i]=result.get(i);return resultArray;}
    int visibleSelection(int selected){for(int value:home(true))if(value==selected)return value;return home(true)[0];}
    boolean moveHome(int slot,int direction){List<String> order=new ArrayList<String>(Arrays.asList(homeOrder().split(",")));int at=order.indexOf(String.valueOf(slot)),to=at+direction;if(at<0||to<0||to>=order.size())return true;Collections.swap(order,at,to);return save(desktop,HOME_ORDER,join(order));}
    boolean hideHome(int slot,boolean hide){if(slot<0||slot>=4)return false;return save(desktop,HOME_HIDDEN,hide?hiddenHome()|(1<<slot):hiddenHome()&~(1<<slot));}
    boolean resetHome(){return save(desktop,HOME_ORDER,DEFAULT_HOME)&&save(desktop,HOME_HIDDEN,0);}
    List<String> hiddenApps(){return packages(apps.getString(APP_HIDDEN,""));}
    boolean hidden(String pkg){return hiddenApps().contains(pkg);}
    List<AppEntry> visible(List<AppEntry> source){
        final List<String> order=packages(apps.getString(APP_ORDER,""));Set<String> hidden=new HashSet<String>(hiddenApps());List<AppEntry> result=new ArrayList<AppEntry>();
        for(AppEntry app:source)if(!hidden.contains(app.packageName))result.add(app);
        // Stable sorting preserves multiple activities from one package in test/catalog input.
        Collections.sort(result,new Comparator<AppEntry>(){public int compare(AppEntry a,AppEntry b){int x=order.indexOf(a.packageName),y=order.indexOf(b.packageName);return Integer.compare(x<0?Integer.MAX_VALUE:x,y<0?Integer.MAX_VALUE:y);}});return result;
    }
    boolean hideApp(String pkg,boolean hide){if(!ConfigBackup.validPackage(pkg))return false;List<String> values=hiddenApps();values.remove(pkg);if(hide)values.add(pkg);String raw=join(values);return validPackages(raw)&&save(apps,APP_HIDDEN,raw);}
    boolean showAll(){return save(apps,APP_HIDDEN,"");}
    boolean resetAppOrder(){return save(apps,APP_ORDER,"");}
    boolean moveApp(String pkg,int direction,List<AppEntry> source){
        List<AppEntry> visible=visible(source);List<String> order=new ArrayList<String>();for(AppEntry app:visible)if(!order.contains(app.packageName))order.add(app.packageName);
        int from=order.indexOf(pkg);if(from<0)return false;int to=direction==0?0:Math.max(0,Math.min(order.size()-1,from+direction));order.remove(from);order.add(to,pkg);
        // Preserve absent/hidden packages for later restoration. Never delete preferences on an unsuccessful scan.
        for(String old:packages(apps.getString(APP_ORDER,"")))if(!order.contains(old))order.add(old);
        String raw=join(order);return validPackages(raw)&&save(apps,APP_ORDER,raw);
    }
    static boolean validDelay(int minutes){return minutes==0||minutes==5||minutes==10||minutes==15||minutes==30;}
    int saverMinutes(){int value=desktop.getInt(SAVER,0);return validDelay(value)?value:0;}
    boolean setSaver(int minutes){return validDelay(minutes)&&save(desktop,SAVER,minutes);}
    private static boolean save(SharedPreferences prefs,String key,Object value){
        Object previous=prefs.getAll().get(key);try{SharedPreferences.Editor edit=prefs.edit();if(value instanceof Integer)edit.putInt(key,(Integer)value);else edit.putString(key,(String)value);if(edit.commit())return true;}catch(RuntimeException ignored){}
        SharedPreferences.Editor restore=prefs.edit();if(previous==null)restore.remove(key);else if(previous instanceof Integer)restore.putInt(key,(Integer)previous);else restore.putString(key,(String)previous);restore.apply();return false;
    }
}
