package cn.wengmang.qiping;

/** Deterministic limits: physical RAM and application heap are different measurements. */
final class PerformancePolicy {
    static final long MIB = 1024L * 1024L;
    final boolean light;
    final int wallpaperWidth, blurWidth, sceneCount, iconCount, iconSize, fadeMillis;
    final long sceneBytes;

    private PerformancePolicy(boolean light, int viewportWidth) {
        this.light = light;
        wallpaperWidth = Math.max(1, Math.min(viewportWidth > 0 ? viewportWidth : 960, light ? 960 : 1920));
        blurWidth = light ? 96 : 128;
        sceneCount = light ? 2 : 3;
        sceneBytes = (light ? 4 : 20) * MIB;
        iconCount = light ? 16 : 48;
        iconSize = light ? 96 : 192;
        fadeMillis = light ? 220 : 360;
    }

    static PerformancePolicy choose(int mode, long totalRam, int heapMb, boolean lowRam, int viewportWidth) {
        boolean light = mode == 1 || lowRam || totalRam <= 0
                || totalRam <= 1536L * MIB || (heapMb > 0 && heapMb <= 128);
        return new PerformancePolicy(light, viewportWidth);
    }

    boolean sameLimits(PerformancePolicy other) {
        return other != null && light == other.light && wallpaperWidth == other.wallpaperWidth;
    }

    /** Enforce both dimensions, also for malformed/tall artwork. Decoder samples are powers of two. */
    static int sampleSize(int width, int height, int maxSide) {
        int sample = 1;
        int limit = Math.max(1, maxSide);
        while (((long)Math.max(width, height) + sample - 1) / sample > limit && sample < (1 << 30)) sample *= 2;
        return sample;
    }
}
