package cn.wengmang.qiping;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

final class AppCatalog {
    static final String TV_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

    static List<AppEntry> scan(PackageManager pm, String self) {
        LinkedHashMap<String, AppEntry> unique = new LinkedHashMap<String, AppEntry>();
        // Prefer a remote-friendly TV entry point when the package provides both.
        for (String category : new String[]{TV_CATEGORY, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo item : pm.queryIntentActivities(query, 0)) {
                if (item.activityInfo == null || !item.activityInfo.exported || !item.activityInfo.enabled
                        || !item.activityInfo.applicationInfo.enabled) continue;
                String pkg = item.activityInfo.packageName;
                if (self.equals(pkg) || unique.containsKey(pkg)) continue;
                String label;
                try { label = item.loadLabel(pm).toString(); }
                catch (RuntimeException error) { label = pkg; }
                long code=-1;String version="";
                try {
                    PackageInfo installed=pm.getPackageInfo(pkg,0);code=installed.versionCode;
                    if(android.os.Build.VERSION.SDK_INT>=28) {
                        try {code=((Long)PackageInfo.class.getMethod("getLongVersionCode").invoke(installed)).longValue();}
                        catch(Exception ignored){/* Older public version code remains the fallback. */}
                    }
                    version=installed.versionName==null?"":installed.versionName;
                }catch(Exception ignored){/* Unknown versions never produce update badges. */}
                unique.put(pkg, new AppEntry(label, item, category,code,version));
            }
        }
        List<AppEntry> result = new ArrayList<AppEntry>(unique.values());
        final Collator collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        Collections.sort(result, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                int labelOrder = collator.compare(a.label, b.label);
                return labelOrder == 0 ? a.packageName.compareTo(b.packageName) : labelOrder;
            }
        });
        return result;
    }
}
