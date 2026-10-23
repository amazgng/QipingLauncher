package cn.wengmang.qiping;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** Bounded plain-text journal; contains only events explicitly supplied by the launcher. */
final class DiagnosticJournal {
    static final int MAX_EVENTS=100, MAX_BYTES=48*1024, MAX_LINE=220;
    private final File file;private final ArrayDeque<String> lines=new ArrayDeque<String>();
    DiagnosticJournal(File file)throws IOException {
        this.file=file;
        if(file.isFile()&&file.length()<=MAX_BYTES){
            BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8));
            try{String line;while((line=reader.readLine())!=null){if(!line.isEmpty())lines.add(clean(line));while(lines.size()>MAX_EVENTS)lines.removeFirst();}}finally{reader.close();}
        }
    }
    static String clean(String value){if(value==null)return "";StringBuilder out=new StringBuilder();for(int i=0;i<value.length()&&out.length()<MAX_LINE;i++){char c=value.charAt(i);out.append(c<32||c==127?' ':c);}return out.toString();}
    synchronized void add(String line)throws IOException {lines.add(clean(line));while(lines.size()>MAX_EVENTS)lines.removeFirst();while(bytes().length>MAX_BYTES&&!lines.isEmpty())lines.removeFirst();save();}
    synchronized String snapshot(){StringBuilder value=new StringBuilder();for(String line:lines)value.append(line).append('\n');return value.toString();}
    synchronized int count(){return lines.size();}
    synchronized void clear()throws IOException {lines.clear();save();}
    private byte[] bytes(){return snapshot().getBytes(StandardCharsets.UTF_8);}
    private void save()throws IOException {
        File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法创建记录目录");
        File pending=new File(parent,file.getName()+".tmp");
        try{FileOutputStream out=new FileOutputStream(pending);try{out.write(bytes());out.flush();out.getFD().sync();}finally{out.close();}
            if(!pending.renameTo(file))throw new IOException("记录保存失败");
        }finally{if(pending.exists())pending.delete();}
    }
}
