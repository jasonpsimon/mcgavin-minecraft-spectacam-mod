package net.reseraph.spectacam;

//? if >=26 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;*/
//?} else {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
//?}

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
 * `KeyBinding.Category` enum produced via `Category.create(String)`.
 * MC 26.x renamed the class wholesale to `KeyMapping` (mojmap) and the
 * Category factory to `.register(Identifier)`. Tiny helper `reg()` keeps
 * the four registration sites free of directive noise.
 */
public class KeyBindings {

    //? if >=26 {
    /*private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("spectacam", "camera"));*/
    //?} else if >=1.21.11 {
    /*private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(net.minecraft.util.Identifier.of("spectacam", "camera"));*/
    //?}

    //? if >=26 {
    /*public static KeyMapping cycleMode;
    public static KeyMapping zoomIn;
    public static KeyMapping zoomOut;
    public static KeyMapping clearTarget;*/
    //?} else {
    public static KeyBinding cycleMode;
    public static KeyBinding zoomIn;
    public static KeyBinding zoomOut;
    public static KeyBinding clearTarget;
    //?}

    //? if >=26 {
    /*private static KeyMapping reg(String translationKey, int key) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYSYM,
                key,
                CATEGORY
        ));
    }*/
    //?} else {
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
    //?}

    public static void register() {
        cycleMode   = reg("key.spectacam.cycle_mode",   GLFW.GLFW_KEY_F7);
        zoomIn      = reg("key.spectacam.zoom_in",      GLFW.GLFW_KEY_EQUAL);
        zoomOut     = reg("key.spectacam.zoom_out",     GLFW.GLFW_KEY_MINUS);
        clearTarget = reg("key.spectacam.clear_target", GLFW.GLFW_KEY_F8);
    }

    //? if >=26 {
    /*public static void handleInput(Minecraft client) {
        while (cycleMode.consumeClick()) {
            SpectaCam.cameraController.cycleMode();
            sendHUD(client, "Mode: " + SpectaCam.cameraController.getMode().getDisplayName());
        }

        while (zoomIn.consumeClick()) {
            SpectaCam.cameraController.adjustZoom(-1f);
            sendHUD(client, "Zoom in  (r=" + String.format("%.1f", SpectaCam.cameraController.getOrbitRadius()) + ")");
        }

        while (zoomOut.consumeClick()) {
            SpectaCam.cameraController.adjustZoom(1f);
            sendHUD(client, "Zoom out (r=" + String.format("%.1f", SpectaCam.cameraController.getOrbitRadius()) + ")");
        }

        while (clearTarget.consumeClick()) {
            SpectaCam.cameraController.setTarget(null);
            sendHUD(client, "Stopped — target cleared");
        }
    }

    private static void sendHUD(Minecraft client, String msg) {
        if (client.player != null) {
            client.player.sendOverlayMessage(SpectaCamText.lit("§b[SpectaCam]§r " + msg));
        }
    }*/
    //?} else {
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
    //?}
}
