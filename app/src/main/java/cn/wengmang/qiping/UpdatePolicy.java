package cn.wengmang.qiping;

import java.net.URI;

/** Version codes, freshness and URL checks are kept independent of Android for regression tests. */
final class UpdatePolicy {
    static final long CHECK_INTERVAL=6*60*60*1000L, RETRY_INTERVAL=15*60*1000L, MAX_AGE=24*60*60*1000L;
    static boolean newer(long installed,long latest,int minSdk,int sdk) {
        return installed>=0&&latest>installed&&minSdk>=1&&minSdk<=sdk;
    }
    static boolean fresh(long checked,long now) {return checked>0&&now>=checked&&now-checked<=MAX_AGE;}
    static boolean packageName(String value) {return value!=null&&value.length()<=240&&value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");}
    static boolean https(String value) {
        try {URI uri=new URI(value);return "https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getFragment()==null;}
        catch(Exception error){return false;}
    }
    static boolean storeTemplate(String value) {
        if(value==null||value.length()>2048||value.indexOf("{package}")<0)return false;
        try {
            URI uri=new URI(value.replace("{package}","cn.example.app"));String scheme=uri.getScheme();
            // Only a fixed, explicitly configured store receives this ACTION_VIEW URI; never parse intent:// extras.
            return scheme!=null&&uri.getUserInfo()==null&&!scheme.equalsIgnoreCase("intent")
                    &&!scheme.equalsIgnoreCase("file")&&!scheme.equalsIgnoreCase("content")
                    &&!scheme.equalsIgnoreCase("javascript")&&!scheme.equalsIgnoreCase("http")
                    &&!scheme.equalsIgnoreCase("data");
        }catch(Exception error){return false;}
    }
}
