package cn.wengmang.qiping;

import android.content.SharedPreferences;
import java.io.*;
import java.util.*;

/** Whitelist-only restore with an on-disk before-image and one-level undo. */
final class ConfigStore {
    private static final String[] OLD_DESKTOP={"category_0","category_1","category_2","category_3","font_size","large_text","margin","reduce_motion","performance_mode","simple_home","layout_locked"};
    private static final String[] OLD_ORGANIZER={"collection_v1"};
    static final String[] DESKTOP=append(OLD_DESKTOP,"home_order","home_hidden","saver_minutes");
    private static final String[] ORGANIZER=append(OLD_ORGANIZER,"app_order","app_hidden");
    private static String[] append(String[] values,String... extra){List<String> all=new ArrayList<String>(Arrays.asList(values));all.addAll(Arrays.asList(extra));return all.toArray(new String[all.size()]);}
    private final SharedPreferences desktop,organizer;
    private final File directory,journal,undo;
    ConfigStore(SharedPreferences desktop,SharedPreferences organizer,File directory){this.desktop=desktop;this.organizer=organizer;this.directory=directory;journal=new File(directory,"restore.pending");undo=new File(directory,"restore.undo");}
    boolean pending(){return journal.exists();}
    boolean canUndo(){return undo.isFile();}
    synchronized ConfigBackup capture()throws IOException {
        try {
            List<String> bindings=new ArrayList<String>();for(int i=0;i<4;i++)bindings.add(desktop.getString("category_"+i,""));
            String raw=organizer.getString("collection_v1","1");if(raw.length()>16384)throw new IllegalArgumentException();
            String[] lines=raw.split("\n",-1);if(!"1".equals(lines[0])||lines.length>13)throw new IllegalArgumentException();
            List<String> saved=new ArrayList<String>();for(int i=1;i<lines.length;i++){String[] f=lines[i].split("\t",-1);if(f.length!=3||!f[1].isEmpty()||!f[2].equals("0"))throw new IllegalArgumentException();saved.add(f[0]);}
            return new ConfigBackup(System.currentTimeMillis(),bindings,saved,DisplayPreferences.font(desktop),desktop.getInt("margin",1),desktop.getBoolean("reduce_motion",false),desktop.getInt("performance_mode",0),desktop.getBoolean("simple_home",false),desktop.getBoolean("layout_locked",false),desktop.getString("home_order",WorkspaceLayout.DEFAULT_HOME),desktop.getInt("home_hidden",0),organizer.getString("app_order",""),organizer.getString("app_hidden",""),desktop.getInt("saver_minutes",0));
        }catch(RuntimeException e){throw new IOException("当前配置或收藏无法完整读取，请先检查设置和收藏");}
    }
    synchronized File backup()throws IOException {
        if(pending())throw new IOException("上次恢复尚未完成，请先重试还原");
        byte[] bytes=capture().encode();File file=new File(directory,"qiping-"+System.currentTimeMillis()+".qiping");int suffix=0;
        while(file.exists())file=new File(directory,"qiping-"+System.currentTimeMillis()+"-"+(++suffix)+".qiping");
        atomic(file,bytes);List<File> files=backups();for(int i=5;i<files.size();i++)files.get(i).delete();return file;
    }
    synchronized List<File> backups() {
        List<File> result=new ArrayList<File>();File[] files=directory.listFiles();
        if(files!=null)for(File f:files)if(f.isFile()&&f.getName().matches("qiping-[0-9]+(?:-[0-9]+)?\\.qiping"))result.add(f);
        Collections.sort(result,new Comparator<File>(){public int compare(File a,File b){return a.lastModified()==b.lastModified()?b.getName().compareTo(a.getName()):a.lastModified()<b.lastModified()?1:-1;}});return result;
    }
    File file(String name)throws IOException {if(name==null||!name.matches("qiping-[0-9]+(?:-[0-9]+)?\\.qiping"))throw new IOException("备份文件名无效");return new File(directory,name);}
    static byte[] read(File file)throws IOException {InputStream in=new FileInputStream(file);try{return ConfigBackup.read(in);}finally{in.close();}}
    synchronized void restore(ConfigBackup config)throws IOException {
        if(pending())recover();
        // Validate even an in-memory object again before writing any preferences.
        ConfigBackup checked=ConfigBackup.decode(config.encode());Snapshot before=snapshot(),after=target(checked);
        transact(before,after);
    }
    synchronized void undo()throws IOException {if(pending())recover();Snapshot after=decode(read(undo)),before=snapshot();transact(before,after);undo.delete();}
    synchronized ConfigBackup undoPreview()throws IOException {Snapshot value=decode(read(undo));return model(value);}
    private void transact(Snapshot before,Snapshot after)throws IOException {
        byte[] old=encode(before);atomic(journal,old);
        try {
            if(!write(after))throw new IOException("配置写入失败");
            atomic(undo,old);
            if(!journal.delete())throw new IOException("无法完成配置提交");
        }catch(IOException error){
            boolean rolledBack=write(before);if(rolledBack)journal.delete();
            throw new IOException(rolledBack?"恢复失败，已还原恢复前的配置":"恢复未完成，请腾出存储空间后重试还原");
        }
    }
    synchronized boolean recover()throws IOException {
        if(!pending())return false;Snapshot old=decode(read(journal));
        if(!write(old)||!journal.delete())throw new IOException("上次恢复未完成，请腾出存储空间后重试还原");return true;
    }
    static void atomic(File target,byte[] bytes)throws IOException {
        File dir=target.getParentFile();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建备份目录");
        File temp=new File(dir,target.getName()+".new");
        try {
            FileOutputStream out=new FileOutputStream(temp);try{out.write(bytes);out.flush();out.getFD().sync();}finally{out.close();}
            if(!temp.renameTo(target))throw new IOException("备份文件保存失败");
        }finally{if(temp.exists())temp.delete();}
    }
    private static final class Snapshot {final Map<String,Object> desktop=new LinkedHashMap<String,Object>(),organizer=new LinkedHashMap<String,Object>();}
    private Snapshot snapshot()throws IOException {
        Snapshot s=new Snapshot();captureFields(desktop,s.desktop,DESKTOP);captureFields(organizer,s.organizer,ORGANIZER);return s;
    }
    private static void captureFields(SharedPreferences prefs,Map<String,Object> out,String[] keys)throws IOException {
        Map<String,?> all=prefs.getAll();for(String key:keys){Object v=all.get(key);if(v!=null&&!(v instanceof String)&&!(v instanceof Integer)&&!(v instanceof Boolean))throw new IOException("当前配置类型异常，未进行恢复");out.put(key,v);}
    }
    private Snapshot target(ConfigBackup c) {
        Snapshot s=new Snapshot();for(int i=0;i<4;i++)s.desktop.put("category_"+i,c.categories.get(i));
        s.desktop.put("font_size",c.font);s.desktop.put("large_text",c.font>0);s.desktop.put("margin",c.margin);s.desktop.put("reduce_motion",c.reducedMotion);
        s.desktop.put("performance_mode",c.performance);s.desktop.put("simple_home",c.simpleHome);s.desktop.put("layout_locked",c.locked);
        StringBuilder list=new StringBuilder("1");for(String pkg:c.collection)list.append('\n').append(pkg).append("\t\t0");s.organizer.put("collection_v1",list.toString());
        if(c.extended){s.desktop.put("home_order",c.homeOrder);s.desktop.put("home_hidden",c.homeHidden);s.desktop.put("saver_minutes",c.saverMinutes);s.organizer.put("app_order",c.appOrder);s.organizer.put("app_hidden",c.appHidden);}return s;
    }
    private ConfigBackup model(Snapshot s)throws IOException {
        // Normalize the preview only. Undo still writes the exact previous values, including absent keys.
        try {
            List<String> bindings=new ArrayList<String>();for(int i=0;i<4;i++)bindings.add((String)value(s.desktop,"category_"+i,""));
            String raw=(String)value(s.organizer,"collection_v1","1");List<String> saved=new ArrayList<String>();String[] rows=raw.split("\n",-1);
            if(!rows[0].equals("1"))throw new IllegalArgumentException();for(int i=1;i<rows.length;i++){String[] f=rows[i].split("\t",-1);if(f.length!=3||!f[1].isEmpty()||!f[2].equals("0"))throw new IllegalArgumentException();saved.add(f[0]);}
            int font=(Integer)value(s.desktop,"font_size",(Boolean)value(s.desktop,"large_text",false)?1:0);
            return new ConfigBackup(0,bindings,saved,font,(Integer)value(s.desktop,"margin",1),(Boolean)value(s.desktop,"reduce_motion",false),(Integer)value(s.desktop,"performance_mode",0),(Boolean)value(s.desktop,"simple_home",false),(Boolean)value(s.desktop,"layout_locked",false),s.desktop.containsKey("home_order")?(String)value(s.desktop,"home_order",WorkspaceLayout.DEFAULT_HOME):null,(Integer)value(s.desktop,"home_hidden",0),(String)value(s.organizer,"app_order",""),(String)value(s.organizer,"app_hidden",""),(Integer)value(s.desktop,"saver_minutes",0));
        }catch(RuntimeException e){throw new IOException("恢复前的配置无法预览，请先使用有效备份");}
    }
    private static Object value(Map<String,Object> map,String key,Object fallback){Object v=map.get(key);return v==null?fallback:v;}
    private boolean write(Snapshot s) {boolean first=write(desktop,s.desktop),second=write(organizer,s.organizer);return first&&second;}
    private static boolean write(SharedPreferences prefs,Map<String,Object> values) {
        try {SharedPreferences.Editor e=prefs.edit();for(Map.Entry<String,Object> item:values.entrySet()) {
            Object v=item.getValue();String k=item.getKey();if(v==null)e.remove(k);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);
        }return e.commit();}catch(RuntimeException error){return false;}
    }
    private static byte[] encode(Snapshot s)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x51504332);
        encodeFields(out,s.desktop,DESKTOP);encodeFields(out,s.organizer,ORGANIZER);out.close();byte[] result=bytes.toByteArray();if(result.length>ConfigBackup.MAX_BYTES)throw new IOException("当前配置过大，未进行恢复");return result;
    }
    private static void encodeFields(DataOutputStream out,Map<String,Object> values,String[] keys)throws IOException {
        for(String key:keys){Object v=values.get(key);out.writeByte(v==null?0:v instanceof String?1:v instanceof Integer?2:3);if(v instanceof String)out.writeUTF((String)v);else if(v instanceof Integer)out.writeInt((Integer)v);else if(v instanceof Boolean)out.writeBoolean((Boolean)v);}
    }
    private static Snapshot decode(byte[] bytes)throws IOException {
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));int version=in.readInt();if(version!=0x51504331&&version!=0x51504332)throw new IOException("还原记录损坏");Snapshot s=new Snapshot();decodeFields(in,s.desktop,version==0x51504331?OLD_DESKTOP:DESKTOP);decodeFields(in,s.organizer,version==0x51504331?OLD_ORGANIZER:ORGANIZER);if(in.available()!=0)throw new IOException("还原记录格式无效");return s;
    }
    private static void decodeFields(DataInputStream in,Map<String,Object> map,String[] keys)throws IOException {
        for(String key:keys){int type=in.readUnsignedByte();Object value;if(type==0)value=null;else if(type==1)value=in.readUTF();else if(type==2)value=in.readInt();else if(type==3)value=in.readBoolean();else throw new IOException("还原记录格式无效");map.put(key,value);}
    }
}
