package cn.wengmang.qiping;
import java.util.Arrays;
public final class GlassContrastTest {
    private static int count;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;}
    public static void main(String[] args){int[] pixels=new int[144];
        Arrays.fill(pixels,0xff000000);check(GlassContrast.strength(pixels)==0,"black stays transparent");
        Arrays.fill(pixels,0xff303038);check(GlassContrast.strength(pixels)==0,"dark wallpaper retains existing tint");
        Arrays.fill(pixels,0xffffffff);check(GlassContrast.strength(pixels)==1,"white gets strongest contrast");
        Arrays.fill(pixels,0xff555555);float middle=GlassContrast.strength(pixels);Arrays.fill(pixels,0xffcccccc);check(GlassContrast.strength(pixels)>middle,"brightness monotonic");
        Arrays.fill(pixels,0xff121212);for(int i=0;i<30;i++)pixels[i]=0xffffffff;check(GlassContrast.strength(pixels)>.6f,"bright local region is noticed");
        check(GlassContrast.strength(new int[0])==0,"empty fallback");
        for(int base:new int[]{0x54171c25,0x70141921,0x98171c25,0xcb171c25}){
            check(GlassContrast.tint(base,0)==base,"dark tint unchanged");int color=GlassContrast.tint(base,1);
            check((color&0xffffff)==(base&0xffffff),"graphite hue unchanged");check((color>>>24)>(base>>>24),"bright increases shade");check((color>>>24)<=228,"glass does not become opaque");
            int old=base>>>24;for(int i=1;i<=20;i++){int alpha=GlassContrast.tint(base,i/20f)>>>24;check(alpha>=old,"fade shade monotonic");old=alpha;}
        }
        System.out.println("PASS: GlassContrast "+count+" checks");
    }
}
