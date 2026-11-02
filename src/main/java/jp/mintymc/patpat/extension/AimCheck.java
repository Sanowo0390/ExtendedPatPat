package jp.mintymc.patpat.extension;

import org.cloudburstmc.math.vector.Vector3f;

/** True only when the viewer's current crosshair ray intersects the target hitbox. */
final class AimCheck {
    private AimCheck() {}

    static boolean hits(Vector3f eye, float yaw, float pitch, Vector3f feet,
                        double width, double height, double reach) {
        if (eye == null || feet == null || !(width > 0) || !(height > 0) || !(reach > 0)) return false;
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double cos = Math.cos(p);
        double[] from = {eye.getX(), eye.getY(), eye.getZ()};
        double[] dir = {-Math.sin(y) * cos, -Math.sin(p), Math.cos(y) * cos};
        double half = width / 2;
        double[] min = {feet.getX() - half, feet.getY(), feet.getZ() - half};
        double[] max = {feet.getX() + half, feet.getY() + height, feet.getZ() + half};
        double near = 0, far = reach;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dir[i]) < 1e-8) {
                if (from[i] < min[i] || from[i] > max[i]) return false;
            } else {
                double a = (min[i] - from[i]) / dir[i], b = (max[i] - from[i]) / dir[i];
                near = Math.max(near, Math.min(a, b));
                far = Math.min(far, Math.max(a, b));
                if (near > far) return false;
            }
        }
        return true;
    }
}
