package net.reseraph.spectacam;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Registers SpecCam keybinds and processes them each tick.
 *
 * Default bindings:
 *   F7       — cycle camera mode (First Person → Third Person → Orbit)
 *   =        — zoom in  (decrease distance / orbit radius)
 *   -        — zoom out (increase distance / orbit radius)
 *   F8       — clear target / stop spectating
 */
public class KeyBindings {

    public static KeyBinding cycleMode;
    public static KeyBinding zoomIn;
    public static KeyBinding zoomOut;
    public static KeyBinding clearTarget;

    public static void register() {
        cycleMode = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spectacam.cycle_mode",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                "category.spectacam"
        ));

        zoomIn = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spectacam.zoom_in",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_EQUAL,
                "category.spectacam"
        ));

        zoomOut = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spectacam.zoom_out",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_MINUS,
                "category.spectacam"
        ));

        clearTarget = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spectacam.clear_target",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "category.spectacam"
        ));
    }

    public static void handleInput(MinecraftClient client) {
        while (cycleMode.wasPressed()) {
            SpecCam.cameraController.cycleMode();
            sendHUD(client, "Mode: " + SpecCam.cameraController.getMode().getDisplayName());
        }

        while (zoomIn.wasPressed()) {
            SpecCam.cameraController.adjustZoom(-1f);
            sendHUD(client, "Zoom in  (r=" + String.format("%.1f", SpecCam.cameraController.getOrbitRadius()) + ")");
        }

        while (zoomOut.wasPressed()) {
            SpecCam.cameraController.adjustZoom(1f);
            sendHUD(client, "Zoom out (r=" + String.format("%.1f", SpecCam.cameraController.getOrbitRadius()) + ")");
        }

        while (clearTarget.wasPressed()) {
            SpecCam.cameraController.setTarget(null);
            sendHUD(client, "Stopped — target cleared");
        }
    }

    private static void sendHUD(MinecraftClient client, String msg) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§b[SpecCam]§r " + msg), true);
        }
    }
}
