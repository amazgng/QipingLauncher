package cn.wengmang.qiping;

import java.io.*;
import java.util.*;

/** Portable format and cross-store restore, including failures and interrupted-write recovery. */
public final class ConfigBackupTest {
    private static int count;
    private static void check(boolean ok){count++;if(!ok)throw new AssertionError("Config check "+count);}
    private static ConfigBackup sample(){return new ConfigBackup(12345,Arrays.asList("video.app","live.app","music.app","photos.app"),Arrays.asList("one.app","two.app"),2,2,true,1,true,false);}
    private static void rejects(byte[] bytes){boolean rejected=false;try{ConfigBackup.decode(bytes);}catch(Exception e){rejected=true;}check(rejected);}
    private static void remove(File file){File[] files=file.listFiles();if(files!=null)for(File f:files)remove(f);file.delete();}
    public static void main(String[] args)throws Exception {
        ConfigBackup sample=sample(),roundTrip=ConfigBackup.decode(sample.encode());
        check(roundTrip.categories.equals(sample.categories));check(roundTrip.collection.equals(sample.collection));
        check(roundTrip.font==2&&roundTrip.margin==2&&roundTrip.performance==1&&roundTrip.reducedMotion&&roundTrip.simpleHome&&!roundTrip.locked&&roundTrip.created==12345);
        boolean immutable=false;try{roundTrip.collection.add("bad");}catch(UnsupportedOperationException e){immutable=true;}check(immutable);
        String encoded=new String(sample.encode(),"UTF-8");check(!encoded.contains("startup")&&!encoded.contains("handled_boot")&&!encoded.contains("live_enabled")&&!encoded.contains("recent"));
        check(ConfigBackup.decode(new ConfigBackup(0,Arrays.asList("shared.app","shared.app","",""),Collections.<String>emptyList(),0,0,false,0,false,true).encode()).categories.get(0).equals("shared.app"));
        for(int font=0;font<3;font++)for(int margin=0;margin<3;margin++)check(ConfigBackup.decode(new ConfigBackup(1,sample.categories,sample.collection,font,margin,false,0,false,false).encode()).margin==margin);
        rejects(new byte[0]);rejects(new byte[ConfigBackup.MAX_BYTES+1]);rejects(encoded.replace("version=1","version=2").getBytes("UTF-8"));
        rejects(encoded.replace("category.0=video.app","category.0=other.app").getBytes("UTF-8"));
        rejects(encoded.replace("font=2","font=9").getBytes("UTF-8"));rejects(encoded.substring(0,encoded.length()-1).getBytes("UTF-8"));
        rejects(encoded.replace("margin=2","font=2").getBytes("UTF-8"));rejects(encoded.replace("performance=1","live_enabled=1").getBytes("UTF-8"));
        rejects(encoded.replace("category.0=video.app","category.0=../private").getBytes("UTF-8"));rejects("not a backup".getBytes("UTF-8"));
        check(Arrays.equals(sample.encode(),ConfigBackup.read(new ByteArrayInputStream(sample.encode()))));
        boolean oversized=false;try{ConfigBackup.read(new ByteArrayInputStream(new byte[ConfigBackup.MAX_BYTES+1]));}catch(IOException e){oversized=true;}check(oversized);
        boolean invalid=false;try{new ConfigBackup(0,sample.categories,Arrays.asList("one.app","one.app"),0,1,false,0,false,false);}catch(IllegalArgumentException e){invalid=true;}check(invalid);
        invalid=false;try{new ConfigBackup(0,Arrays.asList("../bad","","",""),sample.collection,0,1,false,0,false,false);}catch(IllegalArgumentException e){invalid=true;}check(invalid);
        List<String> many=new ArrayList<String>();for(int i=0;i<13;i++)many.add("app.x"+i);
        invalid=false;try{new ConfigBackup(0,sample.categories,many,0,1,false,0,false,false);}catch(IllegalArgumentException e){invalid=true;}check(invalid);
        File directory=File.createTempFile("qiping-config-test-","");directory.delete();directory.mkdirs();
        ShortcutStoreTest.MemoryPreferences desktop=new ShortcutStoreTest.MemoryPreferences(),organizer=new ShortcutStoreTest.MemoryPreferences();
        desktop.edit().putString("category_0","old.video").putBoolean("large_text",true).putString("favorites","original-six-slots").putString("handled_boot","must-stay").putBoolean("live_enabled",true).apply();
        organizer.edit().putString("collection_v1","1\nold.saved\t\t0").putString("recent_v1","unchanged-history").putBoolean("record_recent",false).apply();
        Map<String,?> originalDesktop=desktop.getAll(),originalOrganizer=organizer.getAll();
        ConfigStore store=new ConfigStore(desktop,organizer,directory);
        try {
            check(!store.pending()&&!store.canUndo());ConfigBackup old=store.capture();check(old.font==1&&old.collection.equals(Arrays.asList("old.saved")));check(!desktop.contains("font_size"));
            for(int i=0;i<7;i++){File file=store.backup();check(file.isFile()&&ConfigBackup.decode(ConfigStore.read(file)).font==1);}
            check(store.backups().size()==5);check(desktop.getAll().equals(originalDesktop)&&organizer.getAll().equals(originalOrganizer));
            invalid=false;try{store.file("../outside");}catch(IOException e){invalid=true;}check(invalid);
            store.restore(sample);check(!store.pending()&&store.canUndo());
            check(desktop.getInt("font_size",-1)==2&&desktop.getBoolean("large_text",false)&&desktop.getBoolean("simple_home",false));
            check("live.app".equals(desktop.getString("category_1","")));check("1\none.app\t\t0\ntwo.app\t\t0".equals(organizer.getString("collection_v1","")));
            check("original-six-slots".equals(desktop.getString("favorites","")));check(desktop.getBoolean("live_enabled",false)&&"must-stay".equals(desktop.getString("handled_boot","")));
            check("unchanged-history".equals(organizer.getString("recent_v1",""))&&!organizer.getBoolean("record_recent",true));
            check(store.undoPreview().font==1&&store.undoPreview().categories.get(0).equals("old.video"));
            store.undo();check(!store.canUndo()&&!store.pending());check(desktop.getAll().equals(originalDesktop)&&organizer.getAll().equals(originalOrganizer));
            // Second preference-file commit fails after the first succeeded. Restore both in-memory values, retain the journal if persistence still fails.
            organizer.commitSucceeds=false;boolean failed=false;try{store.restore(sample);}catch(IOException e){failed=true;}check(failed&&store.pending());check(desktop.getAll().equals(originalDesktop)&&organizer.getAll().equals(originalOrganizer));
            // Model process interruption with partly committed files; startup recovery must use the durable before-image.
            desktop.edit().putString("category_1","partly.written").putInt("font_size",2).apply();organizer.edit().putString("collection_v1","1\npartial.app\t\t0").apply();organizer.commitSucceeds=true;
            check(new ConfigStore(desktop,organizer,directory).recover());check(!store.pending());check(desktop.getAll().equals(originalDesktop)&&organizer.getAll().equals(originalOrganizer));
            check(!store.recover());
            File blocked=new File(directory,"not-a-directory");ConfigStore.atomic(blocked,new byte[]{1});ConfigStore impossible=new ConfigStore(desktop,organizer,blocked);
            failed=false;try{impossible.restore(sample);}catch(IOException e){failed=true;}check(failed);check(desktop.getAll().equals(originalDesktop)&&organizer.getAll().equals(originalOrganizer));
            organizer.edit().putString("collection_v1","damaged").apply();failed=false;try{store.backup();}catch(IOException e){failed=true;}check(failed&&"damaged".equals(organizer.getString("collection_v1","")));organizer.edit().putString("collection_v1",(String)originalOrganizer.get("collection_v1")).apply();
            ConfigStore.atomic(new File(directory,"restore.pending"),new byte[]{1,2,3});failed=false;try{store.recover();}catch(IOException e){failed=true;}check(failed);check(desktop.getAll().equals(originalDesktop)&&organizer.getAll().equals(originalOrganizer));
        }finally{remove(directory);}
        System.out.println("PASS: ConfigBackup "+count+" checks");
    }
}
