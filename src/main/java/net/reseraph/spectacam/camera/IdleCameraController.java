package net.reseraph.spectacam.camera;

//? if >=26 {
/*import net.minecraft.world.phys.Vec3;*/
//?} else {
import net.minecraft.util.math.Vec3d;
//?}
import net.reseraph.spectacam.config.SpectaCamConfig;

import java.util.Random;

/**
 * A slow, non-linear fly-around camera for when the target is offline or dead.
 * Pans in a generally consistent direction, changing randomly at intervals.
 * Gently undulates pitch over time.
 *
 * MC 26.x renamed {@code net.minecraft.util.math.Vec3d} →
 * {@code net.minecraft.world.phys.Vec3}. We use a local type alias via
 * conditional imports + a {@code Vec} typedef-like wrapper is not possible
 * in Java, so the references are stonecutter-gated at their use sites.
 */
public class IdleCameraController {

    private static final Random RANDOM = new Random();

    // Heights and speeds are read from config each tick so they're always current

    //? if >=26 {
    /*private Vec3 pos;*/
    //?} else {
    private Vec3d pos;
    //?}
    private float yaw = 0f;
    private float targetYaw = 0f;
    private float pitch = 25f;

    private int ticksUntilDirectionChange = 0;

    private boolean initialized = false;

    public IdleCameraController() {
        randomizeNextChange();
    }

    /**
     * Seed the idle camera at exactly the given world position.
     *
     * Intentionally does NOT snap upward to cruising altitude — the tick()
     * height-lerp eases toward {@link SpectaCamConfig#idleHeightMin}..idleHeightMax
     * over several seconds, which combined with the controller's
     * transition-smoothing ramp produces a soft rubbery "rise" rather than
     * an instant elevator jump when the target is lost.
     */
    //? if >=26 {
    /*public void initAt(Vec3 worldPos) {*/
    //?} else {
    public void initAt(Vec3d worldPos) {
    //?}
        if (!initialized) {
            pos = worldPos;
            initialized = true;
        }
    }

    public void tick() {
        if (!initialized) {
            //? if >=26 {
            /*pos = new Vec3(0, SpectaCamConfig.get().idleHeightMin, 0);*/
            //?} else {
            pos = new Vec3d(0, SpectaCamConfig.get().idleHeightMin, 0);
            //?}
            initialized = true;
        }

        SpectaCamConfig cfg = SpectaCamConfig.get();

        // Count down to next direction change
        ticksUntilDirectionChange--;
        if (ticksUntilDirectionChange <= 0) {
            // Pick a random turn angle within the configured degree range,
            // with a random sign so we can turn left or right
            float minDeg = cfg.idleDirectionChangeMinDeg;
            float maxDeg = cfg.idleDirectionChangeMaxDeg;
            float range  = Math.max(0.1f, maxDeg - minDeg);
            float delta  = minDeg + RANDOM.nextFloat() * range;
            if (RANDOM.nextBoolean()) delta = -delta;
            targetYaw = normalizeAngle(yaw + delta);
            randomizeNextChange();
        }

        // Smoothly lerp yaw toward the target
        float diff = normalizeAngle(targetYaw - yaw);
        yaw += diff * cfg.idleYawLerpRate * 20f; // scale for smooth feel
        yaw = normalizeAngle(yaw);

        // Drift forward in the current yaw direction
        double rad = Math.toRadians(yaw);
        pos = pos.add(
            -Math.sin(rad) * cfg.idleMoveSpeed,
            0,
            Math.cos(rad) * cfg.idleMoveSpeed
        );

        // Gently oscillate height. We deliberately do NOT clamp Y to
        // [heightMin..heightMax] here — clamping would cause the camera to
        // snap up to heightMin on the first idle tick whenever the target
        // was lost at ground level, which defeats the "rubbery rise"
        // transition. Instead, the 0.5%/tick lerp carries Y smoothly toward
        // the cruise midpoint over ~20–30 seconds, regardless of where idle
        // was seeded.
        float heightMin = cfg.idleHeightMin;
        float heightMax = cfg.idleHeightMax;
        double heightOffset = Math.sin(System.currentTimeMillis() * 0.0003) * cfg.idleHeightOscillation;
        double targetY = heightMin + (heightMax - heightMin) * 0.5 + heightOffset;
        double newY = pos.y + (targetY - pos.y) * 0.005;
        //? if >=26 {
        /*pos = new Vec3(pos.x, newY, pos.z);*/
        //?} else {
        pos = new Vec3d(pos.x, newY, pos.z);
        //?}

        // Gently undulate pitch (look slightly up and down over time)
        pitch = cfg.idlePitchBase + (float)(Math.sin(System.currentTimeMillis() * 0.0005) * cfg.idlePitchOscillation);
    }

    public void reset() {
        initialized = false;
        randomizeNextChange();
    }

    private void randomizeNextChange() {
        // Interval configurable via config (idleDirectionChangeMinSec/MaxSec)
        SpectaCamConfig cfg = SpectaCamConfig.get();
        int minTicks = Math.max(20, Math.round(cfg.idleDirectionChangeMinSec * 20f));
        int maxTicks = Math.max(minTicks + 1, Math.round(cfg.idleDirectionChangeMaxSec * 20f));
        ticksUntilDirectionChange = minTicks + RANDOM.nextInt(maxTicks - minTicks);
    }

    private float normalizeAngle(float angle) {
        while (angle > 180f)  angle -= 360f;
        while (angle < -180f) angle += 360f;
        return angle;
    }

    //? if >=26 {
    /*public Vec3  getPos()   { return pos; }*/
    //?} else {
    public Vec3d getPos()   { return pos; }
    //?}
    public float getPitch() { return pitch; }
    public float getYaw()   { return yaw; }
}
