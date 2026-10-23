package cn.wengmang.qiping;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict, bounded, portable desktop configuration. Never contains boot state or app data. */
final class ConfigBackup {
    static final int MAX_BYTES=32768;
    private static final String HEADER="QIPING-CONFIG\n";
    private static final List<String> KEYS=Arrays.asList("version","created","category.0","category.1","category.2","category.3","collection","font","margin","reduce_motion","performance","simple_home","layout_locked");
    private static final List<String> EXTRA_KEYS=Arrays.asList("home_order","home_hidden","app_order","app_hidden","saver_minutes");
    final String homeOrder,appOrder,appHidden;
    final int homeHidden,saverMinutes;
    final boolean extended;
    final long created;
    final List<String> categories,collection;
    final int font,margin,performance;
    final boolean reducedMotion,simpleHome,locked;
    ConfigBackup(long created,List<String> categories,List<String> collection,int font,int margin,boolean reduced,int performance,boolean simple,boolean locked) {
        this(created,categories,collection,font,margin,reduced,performance,simple,locked,null,0,"","",0);
    }
    ConfigBackup(long created,List<String> categories,List<String> collection,int font,int margin,boolean reduced,int performance,boolean simple,boolean locked,String homeOrder,int homeHidden,String appOrder,String appHidden,int saverMinutes) {
        extended=homeOrder!=null;
        if(extended&&(!WorkspaceLayout.validHome(homeOrder)||homeHidden<0||homeHidden>15||!WorkspaceLayout.validPackages(appOrder)||!WorkspaceLayout.validPackages(appHidden)||!WorkspaceLayout.validDelay(saverMinutes)))throw new IllegalArgumentException("个性设置无效");
        this.homeOrder=homeOrder;this.homeHidden=homeHidden;this.appOrder=appOrder;this.appHidden=appHidden;this.saverMinutes=saverMinutes;
        if(created<0||categories==null||categories.size()!=4||collection==null||collection.size()>12||font<0||font>2||margin<0||margin>2||performance<0||performance>1)throw new IllegalArgumentException("配置数值超出范围");
        for(String pkg:categories)if(pkg==null||(!pkg.isEmpty()&&!validPackage(pkg)))throw new IllegalArgumentException("首页绑定格式无效");
        HashSet<String> seen=new HashSet<String>();for(String pkg:collection)if(!validPackage(pkg)||!seen.add(pkg))throw new IllegalArgumentException("收藏包含无效或重复应用");
        this.created=created;this.categories=Collections.unmodifiableList(new ArrayList<String>(categories));this.collection=Collections.unmodifiableList(new ArrayList<String>(collection));
        this.font=font;this.margin=margin;reducedMotion=reduced;this.performance=performance;simpleHome=simple;this.locked=locked;
    }
    static boolean validPackage(String pkg){return pkg!=null&&pkg.length()<=255&&pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");}
    byte[] encode()throws IOException {
        StringBuilder s=new StringBuilder(HEADER).append(extended?"version=2\ncreated=":"version=1\ncreated=").append(created).append('\n');
        for(int i=0;i<4;i++)s.append("category.").append(i).append('=').append(categories.get(i)).append('\n');
        s.append("collection=");for(int i=0;i<collection.size();i++){if(i>0)s.append(',');s.append(collection.get(i));}
        s.append("\nfont=").append(font).append("\nmargin=").append(margin).append("\nreduce_motion=").append(reducedMotion?1:0)
            .append("\nperformance=").append(performance).append("\nsimple_home=").append(simpleHome?1:0).append("\nlayout_locked=").append(locked?1:0).append('\n');
        if(extended)s.append("home_order=").append(homeOrder).append("\nhome_hidden=").append(homeHidden).append("\napp_order=").append(appOrder).append("\napp_hidden=").append(appHidden).append("\nsaver_minutes=").append(saverMinutes).append('\n');
        byte[] payload=s.toString().getBytes("UTF-8");s.append("sha256=").append(hash(payload)).append('\n');byte[] result=s.toString().getBytes("UTF-8");if(result.length>MAX_BYTES)throw new IOException("配置超过 32 KB");return result;
    }
    static ConfigBackup decode(byte[] bytes)throws IOException {
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw new IOException("文件为空或超过 32 KB");
        for(byte b:bytes)if((b<32&&b!='\n')||b>126)throw new IOException("文件编码或内容无效");
        String text=new String(bytes,"UTF-8");if(!text.startsWith(HEADER)||!text.endsWith("\n"))throw new IOException("不是完整的栖屏配置文件");
        String[] lines=text.split("\n",-1);boolean extended=text.startsWith(HEADER+"version=2\n");List<String> accepted=new ArrayList<String>(KEYS);if(extended)accepted.addAll(EXTRA_KEYS);if(lines.length!=accepted.size()+3)throw new IOException("配置字段不完整或包含未知字段");
        Map<String,String> map=new LinkedHashMap<String,String>();
        for(int i=1;i<lines.length-2;i++) {
            int equals=lines[i].indexOf('=');if(equals<1)throw new IOException("配置字段格式错误");
            String key=lines[i].substring(0,equals);if(!accepted.contains(key)||map.put(key,lines[i].substring(equals+1))!=null)throw new IOException("配置包含未知或重复字段");
        }
        if(map.size()!=accepted.size())throw new IOException("配置字段缺失");
        if(!(extended?"2":"1").equals(map.get("version")))throw new IOException("此备份版本暂不支持，请使用匹配版本的栖屏");
        int end=text.lastIndexOf("sha256=");if(end<0||!lines[lines.length-2].matches("sha256=[a-f0-9]{64}")||!hash(text.substring(0,end).getBytes("UTF-8")).equals(lines[lines.length-2].substring(7)))throw new IOException("文件校验失败，可能已损坏");
        try {
            List<String> categories=new ArrayList<String>();for(int i=0;i<4;i++)categories.add(map.get("category."+i));
            String list=map.get("collection");List<String> saved=list.isEmpty()?new ArrayList<String>():Arrays.asList(list.split(",",-1));
            String created=map.get("created");if(!created.matches("[0-9]{1,19}"))throw new IllegalArgumentException("备份日期无效");
            return new ConfigBackup(Long.parseLong(created),categories,saved,number(map,"font",2),number(map,"margin",2),number(map,"reduce_motion",1)==1,number(map,"performance",1),number(map,"simple_home",1)==1,number(map,"layout_locked",1)==1,extended?map.get("home_order"):null,extended?number(map,"home_hidden",15):0,extended?map.get("app_order"):"",extended?map.get("app_hidden"):"",extended?number(map,"saver_minutes",30):0);
        }catch(RuntimeException e){throw new IOException("配置数值或应用列表无效");}
    }
    private static int number(Map<String,String> map,String name,int max){String s=map.get(name);if(s==null||!s.matches("0|[1-9][0-9]?"))throw new IllegalArgumentException(name);int value=Integer.parseInt(s);if(value>max)throw new IllegalArgumentException(name);return value;}
    static byte[] read(InputStream in)throws IOException {
        if(in==null)throw new IOException("无法读取所选文件");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
        while((n=in.read(buffer))!=-1){if(n==0)throw new IOException("文件读取中断");if(out.size()+n>MAX_BYTES)throw new IOException("文件超过 32 KB");out.write(buffer,0,n);}return out.toByteArray();
    }
    private static String hash(byte[] bytes)throws IOException {
        try{StringBuilder b=new StringBuilder();for(byte n:MessageDigest.getInstance("SHA-256").digest(bytes))b.append(String.format(java.util.Locale.US,"%02x",n&255));return b.toString();}
        catch(java.security.NoSuchAlgorithmException e){throw new IOException("无法校验文件");}
    }
}
