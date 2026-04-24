package net.reseraph.spectacam.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.reseraph.spectacam.SpectaCam;
import net.reseraph.spectacam.camera.CameraMode;
import net.reseraph.spectacam.camera.SpectatorCameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into Camera.update() after Minecraft has finished setting its own
 * position, then overwrites pos/rotation with our computed values when
 * SpectaCam is active.
 *
 * setPos() and setRotation() are used (not raw field writes) so that the
 * camera's internal direction vectors and rotation quaternion stay consistent.
 *
 * <h2>v1.14.1 — third-person field override</h2>
 *
 * When the client is attached to another player via {@code /spectate <name>},
 * that player becomes the Camera's focusedEntity and Minecraft's
 * {@code WorldRenderer.render()} culls it whenever {@code camera.isThirdPerson()}
 * returns false — because vanilla assumes "camera at the entity's eye = don't
 * draw that entity's face."
 *
 * This is the right behavior for our {@link CameraMode#FIRST_PERSON} mode, but
 * it made the target go invisible in THIRD_PERSON and ORBIT modes where the
 * camera is clearly pulled back behind the player.
 *
 * Fix: after our setPos/setRotation override, write Camera.thirdPerson directly
 * via {@code @Shadow} to match the SpectaCam mode. When the mode is not
 * FIRST_PERSON we report true, which flips Minecraft's cull to render the
 * target normally.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    /**
     * Vanilla's third-person flag. Read by {@code isThirdPerson()} which the
     * world renderer consults to decide whether to draw the camera's focused
     * entity. Writable via {@code @Shadow} so we can match it to SpectaCam's mode.
     */
    @Shadow
    private boolean thirdPerson;

    @Inject(method = "update", at = @At("RETURN"))
    private void spectacam$onUpdate(
            BlockView area,
            Entity focusedEntity,
            boolean vanillaThirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo ci
    ) {
        SpectatorCameraController ctrl = SpectaCam.cameraController;
        if (ctrl == null || !ctrl.isActive() || !ctrl.overrideCamera) return;

        // ── v1.14.2 jitter fix ───────────────────────────────────────────────
        // The controller only updates cameraPos/Pitch/Yaw once per tick (20 Hz).
        // Minecraft calls Camera.update() once per frame (60+ Hz). Applying the
        // raw per-tick values produced a 50ms stair-step while the target moved
        // smoothly via vanilla interpolation. We lerp prev → curr using the
        // render tickDelta to deliver per-frame motion.
        float td = tickDelta;
        if (td < 0f) td = 0f;
        if (td > 1f) td = 1f;

        Vec3d   prev = ctrl.prevCameraPos;
        Vec3d   curr = ctrl.cameraPos;
        double  ix   = prev.x + (curr.x - prev.x) * td;
        double  iy   = prev.y + (curr.y - prev.y) * td;
        double  iz   = prev.z + (curr.z - prev.z) * td;
        float   iyaw = lerpAngle(ctrl.prevCameraYaw,   ctrl.cameraYaw,   td);
        float   ipit = lerpAngle(ctrl.prevCameraPitch, ctrl.cameraPitch, td);

        setPos(ix, iy, iz);
        setRotation(iyaw, ipit);

        // Flip Minecraft's third-person flag so WorldRenderer stops culling
        // the camera-focused entity when SpectaCam is pulling the camera away
        // from the target's eye position. First-person mode keeps the vanilla
        // "you are inside them, hide their face" behavior.
        this.thirdPerson = (ctrl.getMode() != CameraMode.FIRST_PERSON);
    }

    /** Shortest-path angle lerp; matches SpectatorCameraController.lerpAngle. */
    private static float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff >  180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return from + diff * t;
    }
}
