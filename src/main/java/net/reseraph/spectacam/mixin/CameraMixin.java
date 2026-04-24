package net.reseraph.spectacam.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.reseraph.spectacam.SpecCam;
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
 * SpecCam is active.
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
 * via {@code @Shadow} to match the SpecCam mode. When the mode is not
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
     * entity. Writable via {@code @Shadow} so we can match it to SpecCam's mode.
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
        SpectatorCameraController ctrl = SpecCam.cameraController;
        if (ctrl == null || !ctrl.isActive() || !ctrl.overrideCamera) return;

        Vec3d p = ctrl.cameraPos;
        setPos(p.x, p.y, p.z);
        setRotation(ctrl.cameraYaw, ctrl.cameraPitch);

        // Flip Minecraft's third-person flag so WorldRenderer stops culling
        // the camera-focused entity when SpecCam is pulling the camera away
        // from the target's eye position. First-person mode keeps the vanilla
        // "you are inside them, hide their face" behavior.
        this.thirdPerson = (ctrl.getMode() != CameraMode.FIRST_PERSON);
    }
}
