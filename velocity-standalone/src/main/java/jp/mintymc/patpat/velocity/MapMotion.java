package jp.mintymc.patpat.velocity;

/**
 * Quantizes Geyser's eased vertical stroke to the five map poses.
 * Only timing is converted here: MapFrames converts the configured
 * world-space depth to pixels. Applying depth twice shrinks Java's stroke.
 */
final class MapMotion {
    static final int POSES=5;
    private MapMotion() {}

    static int poseAt(int tick,int duration,double depth){
        if(tick<=0 || tick>=duration || duration<2 ||
                !Double.isFinite(depth) || depth<=0)return 0;
        double t=(double)tick/duration;
        double eased=1-(1-t)*(1-t);
        int pose=(int)Math.round((POSES-1)*Math.sin(Math.PI*eased));
        return Math.max(0,Math.min(POSES-1,pose));
    }
}
