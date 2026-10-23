package cn.wengmang.qiping;

public final class UpdatePolicyTest {
    private static int checks;
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    public static void main(String[] args) {
        check(UpdatePolicy.newer(10,11,14,14),"Android 4 compatible newer version");
        check(UpdatePolicy.newer(10,11,9,14),"apps also supporting pre-Android-4 devices remain compatible");
        check(!UpdatePolicy.newer(11,11,14,35),"same version clears badge after install");
        check(!UpdatePolicy.newer(12,11,14,35),"downgrade never advertised");
        check(!UpdatePolicy.newer(-1,11,14,35),"unknown installed version cannot advertise update");
        check(!UpdatePolicy.newer(10,11,21,19),"incompatible minimum SDK hidden");
        check(!UpdatePolicy.newer(10,11,-1,35),"invalid SDK hidden");
        check(UpdatePolicy.newer(2147483648L,2147483649L,14,35),"long version code supported");
        long now=1000000000L;
        check(UpdatePolicy.fresh(now-UpdatePolicy.MAX_AGE,now),"inclusive cache expiry boundary");
        check(!UpdatePolicy.fresh(now-UpdatePolicy.MAX_AGE-1,now),"expired reminder hidden");
        check(!UpdatePolicy.fresh(0,now),"no successful check is not up-to-date");
        check(!UpdatePolicy.fresh(now+1,now),"clock rollback invalidates future cache");
        check(UpdatePolicy.packageName("cn.example.tv_app"),"Android package accepted");
        check(!UpdatePolicy.packageName("cn.example/app?extra=1"),"query injection rejected");
        check(!UpdatePolicy.packageName(""),"empty package rejected");
        check(UpdatePolicy.https("https://updates.example.com/catalog.json"),"HTTPS catalog accepted");
        check(!UpdatePolicy.https("http://updates.example.com/catalog.json"),"HTTP catalog rejected");
        check(!UpdatePolicy.https("https://name:secret@example.com/catalog.json"),"URL credentials rejected");
        check(!UpdatePolicy.https("https://example.com/#fragment"),"fragment rejected");
        check(!UpdatePolicy.https("https:///catalog.json"),"missing host rejected");
        check(UpdatePolicy.storeTemplate("market://details?id={package}"),"market details accepted");
        check(UpdatePolicy.storeTemplate("hisense-example://app/{package}"),"fixed vendor scheme supported");
        check(UpdatePolicy.storeTemplate("https://store.example.com/app/{package}"),"HTTPS store link supported");
        check(!UpdatePolicy.storeTemplate("intent://details?id={package}#Intent;end"),"serialized intents rejected");
        check(!UpdatePolicy.storeTemplate("file:///data/{package}"),"file URI rejected");
        check(!UpdatePolicy.storeTemplate("content://data/{package}"),"content URI rejected");
        check(!UpdatePolicy.storeTemplate("http://example.com/{package}"),"HTTP store link rejected");
        check(!UpdatePolicy.storeTemplate("javascript:open('{package}')"),"script URI rejected");
        check(!UpdatePolicy.storeTemplate("market://details?id=constant"),"package placeholder required");
        System.out.println("UpdatePolicyTest: "+checks+" assertions passed");
    }
}
