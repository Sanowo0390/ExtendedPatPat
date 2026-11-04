package jp.mintymc.patpat.velocity;

/** The same eased down/up stroke Geyser's HandMotion uses (0.425 blocks). */
final class MapMotion {
    static final int POSES=5;
    private static final double REFERENCE_DEPTH=0.425;
    private MapMotion() {}

    static int poseAt(int tick,int duration,double depth){
        if(tick<=0 || tick>=duration || duration<2 ||
                !Double.isFinite(depth) || depth<=0)return 0;
        double t=(double)tick/duration;
        // Match Geyser HandMotion: 1-(1-t)^2, followed by a sine stroke.
        double eased=1-(1-t)*(1-t);
        double amplitude=Math.min(1.0,depth/REFERENCE_DEPTH);
        int pose=(int)Math.round((POSES-1)*amplitude*Math.sin(Math.PI*eased));
        return Math.max(0,Math.min(POSES-1,pose));
    }
}
