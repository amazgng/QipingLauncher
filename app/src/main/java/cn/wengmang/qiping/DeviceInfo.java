package cn.wengmang.qiping;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.StatFs;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/** Local, permission-free diagnostics. Never collect serial numbers, accounts or network identifiers. */
final class DeviceInfo {
    final long totalRam, availableRam, totalStorage, availableStorage;
    final int heapMb;
    final boolean lowRam, memoryPressure;

    private DeviceInfo(long totalRam, long availableRam, int heapMb, boolean lowRam,
                       boolean memoryPressure, long totalStorage, long availableStorage) {
        this.totalRam=totalRam;this.availableRam=availableRam;this.heapMb=heapMb;
        this.lowRam=lowRam;this.memoryPressure=memoryPressure;
        this.totalStorage=totalStorage;this.availableStorage=availableStorage;
    }

    static DeviceInfo read(Context context) {
        long total=-1, available=-1, diskTotal=-1, diskAvailable=-1;
        int heap=0;boolean low=false, pressure=false;
        try {
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager!=null) {
                heap=manager.getMemoryClass();
                ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();manager.getMemoryInfo(info);
                available=info.availMem;pressure=info.lowMemory;
                // Defensive access to public memory/storage APIs across manufacturer firmware.
                if(Build.VERSION.SDK_INT>=16)try {total=info.getClass().getField("totalMem").getLong(info);}catch(Exception ignored){}
                if(Build.VERSION.SDK_INT>=19)try {low=Boolean.TRUE.equals(ActivityManager.class.getMethod("isLowRamDevice").invoke(manager));}catch(Exception ignored){}
            }
        } catch(RuntimeException ignored) { /* Unknown measurements remain unknown, not fabricated. */ }
        if(total<=0)total=readProcTotal();
        try {
            StatFs stat=new StatFs(context.getFilesDir().getAbsolutePath());
            long size=statValue(stat,"getBlockSizeLong",stat.getBlockSize());
            diskTotal=size*statValue(stat,"getBlockCountLong",stat.getBlockCount());
            diskAvailable=size*statValue(stat,"getAvailableBlocksLong",stat.getAvailableBlocks());
        } catch(RuntimeException ignored){}
        return new DeviceInfo(total,available,heap,low,pressure,diskTotal,diskAvailable);
    }

    private static long statValue(StatFs stat,String name,int legacy) {
        if(Build.VERSION.SDK_INT>=18)try {return ((Long)StatFs.class.getMethod(name).invoke(stat)).longValue();}catch(Exception ignored){}
        return legacy & 0xffffffffL;
    }

    private static long readProcTotal() {
        BufferedReader reader=null;
        try {
            reader=new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            while((line=reader.readLine())!=null)if(line.startsWith("MemTotal:")) {
                String[] values=line.trim().split("\\s+");
                return Long.parseLong(values[1])*1024L;
            }
        } catch(Exception ignored) { /* Some vendor builds restrict proc access. */ }
        finally {if(reader!=null)try {reader.close();}catch(Exception ignored){}}
        return -1;
    }

    static String bytes(long value) {
        if(value<0)return "未能读取";
        if(value>=1024L*1024L*1024L)return String.format(Locale.US,"%.1f GB",value/(1024d*1024d*1024d));
        return String.format(Locale.US,"%.0f MB",value/(1024d*1024d));
    }
    static String value(String text) {return text==null||text.trim().length()==0?"未能读取":text;}
}
