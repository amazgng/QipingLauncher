package cn.wengmang.qiping;

import android.content.ComponentName;
import android.content.pm.ResolveInfo;

final class AppEntry {
    final String label;
    final String packageName;
    final ComponentName component;
    final String category;
    final ResolveInfo info;
    final long versionCode;
    final String versionName;

    AppEntry(String label, ResolveInfo info, String category, long versionCode, String versionName) {
        this.label = label;
        this.info = info;
        this.packageName = info.activityInfo.packageName;
        this.component = new ComponentName(packageName, info.activityInfo.name);
        this.category = category;
        this.versionCode = versionCode;
        this.versionName = versionName;
    }
}
