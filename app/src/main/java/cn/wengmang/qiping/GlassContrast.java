package cn.wengmang.qiping;

import java.util.Arrays;

/** One small luminance sample per decoded wallpaper; no pixel reads during drawing. */
final class GlassContrast {
    private GlassContrast() {}
    static float strength(int[] pixels) {
        if(pixels==null||pixels.length==0)return 0;
        float[] light=new float[pixels.length];float sum=0;
        for(int i=0;i<pixels.length;i++) {
            int p=pixels[i];
            light[i]=.2126f*linear((p>>16)&255)+.7152f*linear((p>>8)&255)+.0722f*linear(p&255);
            sum+=light[i];
        }
        Arrays.sort(light);
        float value=.65f*light[Math.min(light.length-1,(int)(light.length*.85f))]+.35f*sum/light.length;
        return clamp((value-.16f)/.58f);
    }
    private static float linear(int channel){float v=channel/255f;return v<=.04045f?v/12.92f:(float)Math.pow((v+.055f)/1.055f,2.4);}
    static float clamp(float value){return Math.max(0,Math.min(1,value));}
    static int tint(int color,float strength){int base=color>>>24;int target=Math.max(base,Math.min(228,base+90));return (Math.round(base+(target-base)*clamp(strength))<<24)|(color&0xffffff);}
}
