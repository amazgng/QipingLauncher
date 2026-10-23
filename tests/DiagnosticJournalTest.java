package cn.wengmang.qiping;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;
public final class DiagnosticJournalTest {
    private static int count;private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;}
    public static void main(String[] args)throws Exception{Path dir=Files.createTempDirectory("qiping-journal-test-");File file=dir.resolve("events.txt").toFile();
        DiagnosticJournal journal=new DiagnosticJournal(file);check(journal.count()==0,"new journal empty");
        journal.add("启动 · 123");journal.add("多行\n不注入\r控制\t字符");check(!journal.snapshot().contains("\n不注入"),"one event cannot forge lines");
        check(new DiagnosticJournal(file).snapshot().equals(journal.snapshot()),"records survive new process");
        for(int i=0;i<150;i++)journal.add("记录 "+i+" "+new String(new char[1000]).replace('\0','图'));
        check(journal.count()<=DiagnosticJournal.MAX_EVENTS,"event cap");check(file.length()<=DiagnosticJournal.MAX_BYTES,"UTF-8 byte cap");check(journal.snapshot().contains("记录 149"),"newest retained");check(!journal.snapshot().contains("记录 0 "),"oldest discarded");
        check(!dir.resolve("events.txt.tmp").toFile().exists(),"atomic temp cleaned");
        journal.clear();check(file.length()==0&&new DiagnosticJournal(file).count()==0,"clear is persistent");
        Files.write(file.toPath(),new byte[DiagnosticJournal.MAX_BYTES+1]);journal=new DiagnosticJournal(file);check(journal.count()==0,"oversized corrupted file ignored");journal.add("恢复记录");check(file.length()<100,"oversized file replaced safely");
        check(DiagnosticJournal.clean(new String(new char[1000]).replace('\0','a')).length()==DiagnosticJournal.MAX_LINE,"line cap");
        Files.delete(file.toPath());Files.delete(dir);System.out.println("PASS: DiagnosticJournal "+count+" checks");
    }
}
