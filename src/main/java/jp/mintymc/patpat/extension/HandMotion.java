package jp.mintymc.patpat.extension;

/**
 * Vertical hand motion matching PatPat's timing curve.
 *
 * PatPat's default animation lasts 240 ms. Progress accelerates using
 * 1-(1-t)^2 before a sine-shaped downward press and upward recovery.
 * Screen offsets are applied separately, so the motion is always world-up.
 */
final class HandMotion {
    private HandMotion() {}

    static float displacement(long elapsedMs, long durationMs, double depth) {
        if (durationMs <= 0 || depth <= 0 || elapsedMs <= 0 || elapsedMs >= durationMs) {
            return 0f;
        }
        double t = (double) elapsedMs / durationMs;
        double eased = 1 - (1 - t) * (1 - t);
        return (float) (-depth * Math.sin(Math.PI * eased));
    }
}
