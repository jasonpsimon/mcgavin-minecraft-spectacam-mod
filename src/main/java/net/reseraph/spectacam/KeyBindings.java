package net.reseraph.spectacam;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.reseraph.spectacam.util.SpectaCamText;
import org.lwjgl.glfw.GLFW;

/**
 * Registers SpectaCam keybinds and processes them each tick.
 *
 * Default bindings:
 *   F7       — cycle camera mode (First Person → Third Person → Orbit)
 *   =        — zoom in  (decrease distance / orbit radius)
 *   -        — zoom out (increase distance / orbit radius)
 *   F8       — clear target / stop spectating
 *
 * MC 1.21.11 swapped KeyBinding's 4th ctor arg from `String category` to a
 * `KeyBinding.Category` enum produced via `Category.create(String)`. A tiny
 * helper `reg()` keeps the four registration sites free of directive noise;
 * the gate only appears once — on the 4th argument and the CATEGORY constant.
 */
public class KeyBindings {

    //? if >=1.21.11 {
    /*private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(net.minecraft.util.Identifier.of("spectacam", "camera"));*/
    //?}

    public static KeyBinding cycleMode;
    public static KeyBinding zoomIn;
    public static KeyBinding zoomOut;
    public static KeyBinding clearTarget;

    private static KeyBinding reg(String translationKey, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                translationKey,
                InputUtil.Type.KEYSYM,
                key,
                //? if >=1.21.11 {
                /*CATEGORY*/
                //?} else {
                "category.spectacam"
                //?}
        ));
    }

    public static void register() {
        cycleMode   = reg("key.spectacam.cycle_mode",   GLFW.GLFW_KEY_F7);
        zoomIn      = reg("key.spectacam.zoom_in",      GLFW.GLFW_KEY_EQUAL);
        zoomOut     = reg("key.spectacam.zoom_out",     GLFW.GLFW_KEY_MINUS);
        clearTarget = reg("key.spectacam.clear_target", GLFW.GLFW_KEY_F8);
    }

    public static void handleInput(MinecraftClient client) {
        while (cycleMode.wasPressed()) {
            SpectaCam.cameraController.cycleMode();
            sendHUD(client, "Mode: " + SpectaCam.cameraController.getMode().getDisplayName());
        }

        while (zoomIn.wasPressed()) {
            SpectaCam.cameraController.adjustZoom(-1f);
            sendHUD(client, "Zoom in  (r=" + String.format("%.1f", SpectaCam.cameraController.getOrbitRadius()) + ")");
        }

        while (zoomOut.wasPressed()) {
            SpectaCam.cameraController.adjustZoom(1f);
            sendHUD(client, "Zoom out (r=" + String.format("%.1f", SpectaCam.cameraController.getOrbitRadius()) + ")");
        }

        while (clearTarget.wasPressed()) {
            SpectaCam.cameraController.setTarget(null);
            sendHUD(client, "Stopped — target cleared");
        }
    }

    private static void sendHUD(MinecraftClient client, String msg) {
        if (client.player != null) {
            client.player.sendMessage(SpectaCamText.lit("§b[SpectaCam]§r " + msg), true);
        }
    }
}
