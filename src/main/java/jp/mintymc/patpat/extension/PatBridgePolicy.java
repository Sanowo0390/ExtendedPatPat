package jp.mintymc.patpat.extension;

import java.util.UUID;

/** Pure validation for a Velocity Java-player Pat replicated into Geyser. */
final class PatBridgePolicy {
    private PatBridgePolicy(){}

    static boolean sameWorldRange(UUID actor,UUID target,double distanceSquared,double reach) {
        if(actor==null || target==null || actor.equals(target)
                || !Double.isFinite(distanceSquared) || distanceSquared<0
                || !Double.isFinite(reach) || reach<=0)return false;
        // Entity positions are asynchronous across Geyser and Java.
        // Allow two blocks of movement tolerance beyond the click reach.
        double allowed=reach+2;
        return distanceSquared<=allowed*allowed;
    }
}
