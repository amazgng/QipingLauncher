package cn.wengmang.qiping;

/** Policy boundaries and bounded decode dimensions; no Android runtime or invented performance timings. */
public final class PerformancePolicyTest {
    private static int count;
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
    private static PerformancePolicy auto(long ram,int heap,boolean low,int width){return PerformancePolicy.choose(0,ram,heap,low,width);}
    public static void main(String[] args) {
        long mb=PerformancePolicy.MIB;
        PerformancePolicy tv=auto(1024*mb,256,false,1920);
        check(tv.light,"1 GB TV chooses light even with a 256 MB application heap");
        check(auto(806*mb,256,false,1920).light,"system RAM lower than marketing RAM is supported");
        check(auto(1536*mb,256,false,1920).light,"inclusive 1.5 GB boundary");
        check(!auto(1536*mb+1,256,false,1920).light,"above boundary uses standard");
        check(auto(4096*mb,128,false,3840).light,"small app heap still limits high-RAM devices");
        check(!auto(4096*mb,129,false,3840).light,"heap threshold does not silently expand");
        check(auto(4096*mb,512,true,1920).light,"platform low-RAM flag overrides physical RAM");
        check(auto(-1,256,false,1920).light,"unknown RAM chooses conservative mode");
        check(auto(0,0,false,0).light,"missing measurements remain safe");
        check(PerformancePolicy.choose(1,8192*mb,512,false,1920).light,"phone can preview forced light mode");
        check(!PerformancePolicy.choose(0,8192*mb,512,false,1920).light,"return to auto restores standard on high-RAM device");
        check(tv.wallpaperWidth==960,"1 GB background size cap");
        check(auto(8192*mb,512,false,3840).wallpaperWidth==1920,"4K device does not decode full 4K wallpaper");
        check(auto(8192*mb,512,false,720).wallpaperWidth==720,"small viewport avoids oversized decode");
        check(tv.sceneCount==2&&tv.sceneBytes==4*mb,"light wallpaper cache count and byte budget");
        check(tv.iconCount==16&&tv.iconSize==96,"light icon bitmap limit");
        check(tv.fadeMillis<auto(4096*mb,256,false,1920).fadeMillis,"light transitions finish sooner");
        check(tv.sameLimits(auto(1024*mb,256,false,3840)),"repeated readings do not discard identical caches");
        check(!tv.sameLimits(auto(4096*mb,256,false,1920)),"changing quality invalidates cache limits");
        check(!tv.sameLimits(null),"initial policy is not treated as already configured");
        check(PerformancePolicy.sampleSize(1672,941,960)==2,"bundled scene is sampled before decoding");
        check(PerformancePolicy.sampleSize(1672,941,1920)==1,"standard mode retains source resolution");
        check(PerformancePolicy.sampleSize(500,5000,960)==8,"portrait sources also respect memory limits");
        check(PerformancePolicy.sampleSize(960,540,960)==1,"exact boundary does not downsample twice");
        check(PerformancePolicy.sampleSize(961,540,960)==2,"rounded width is bounded");
        check(PerformancePolicy.sampleSize(0,0,0)==1,"invalid bounds cannot loop");
        check(PerformancePolicy.sampleSize(Integer.MAX_VALUE,Integer.MAX_VALUE,960)>1,"large dimensions do not overflow");
        for(int[] size:new int[][]{{1672,941},{3840,2160},{960,540},{1080,1920},{8192,8192},{1,9600}}) {
            int sample=PerformancePolicy.sampleSize(size[0],size[1],960);
            check((sample&(sample-1))==0&&(size[0]+sample-1)/sample<=960&&(size[1]+sample-1)/sample<=960,
                    "decoded size fits both dimensions: "+size[0]+"x"+size[1]);
        }
        System.out.println("PerformancePolicyTest: "+count+" assertions passed");
    }
}
