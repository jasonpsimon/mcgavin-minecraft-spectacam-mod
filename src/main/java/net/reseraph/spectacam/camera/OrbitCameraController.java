package net.reseraph.spectacam.camera;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Continuously orbits around the target player in a slow circle.
 * Radius and speed are configurable.
 */
public class OrbitCameraController {

    private float angle = 0f;      // Current orbit angle in degrees
    private float radius;
    private final float height = 3.0f;

    private Vec3d pos = Vec3d.ZERO;
    private float pitch = 15f;
    private float yaw = 0f;

    public OrbitCameraController(float initialRadius) {
        this.radius = initialRadius;
    }

    /**
     * Tick the orbit — advances the angle and recomputes camera pos/rotation.
     *
     * @param target     The player being orbited.
     * @param orbitSpeed Degrees to advance per tick (from config).
     */
    public void tick(PlayerEntity target, float orbitSpeed) {
        angle = (angle + orbitSpeed) % 360f;

        Vec3d center = target.getPos().add(0, target.getHeight() * 0.5 + 0.5, 0);

        double rad = Math.toRadians(angle);
        double camX = center.x + Math.sin(rad) * radius;
        double camZ = center.z + Math.cos(rad) * radius;
        double camY = center.y + height;

        pos = new Vec3d(camX, camY, camZ);

        // Compute yaw/pitch so the camera always faces the target center
        double dx = center.x - camX;
        double dy = center.y - camY;
        double dz = center.z - camZ;
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        pitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist));
    }

    public void reset() {
        angle = 0f;
    }

    public void adjustRadius(float delta) {
        radius = Math.max(3f, Math.min(30f, radius + delta));
    }

    public float getRadius() { return radius; }
    public Vec3d getPos()    { return pos; }
    public float getPitch()  { return pitch; }
    public float getYaw()    { return yaw; }
}
