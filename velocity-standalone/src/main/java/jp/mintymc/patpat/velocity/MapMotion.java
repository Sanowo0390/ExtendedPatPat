package jp.mintymc.patpat.velocity;

/**
 * Pure animation curve for pre-rendered map poses. The map is displayed by a
 * block-attached ItemFrame, so sub-block entity teleports do NOT move the art.
 * Move the map pixels instead: 0 (highest) -> 4 (lowest) -> 0.
 */
final class MapMotion {
    static final int POSES=5;
    private static final double FULL_DEPTH=0.425;
    private MapMotion() {}

    static int poseAt(int tick,int duration,double depth){
        if(tick<=0 || tick>=duration || duration<2 ||
                !Double.isFinite(depth) || depth<=0)return 0;
        double progress=(double)tick/duration;
        double amplitude=Math.min(1.0,depth/FULL_DEPTH);
        int pose=(int)Math.round((POSES-1)*amplitude*Math.sin(Math.PI*progress));
        return Math.max(0,Math.min(POSES-1,pose));
    }
}
