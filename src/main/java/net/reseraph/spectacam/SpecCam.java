package net.reseraph.spectacam;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.reseraph.spectacam.camera.SpectatorCameraController;
import net.reseraph.spectacam.command.SpecCamCommand;
import net.reseraph.spectacam.config.SpecCamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SpecCam — Spectator camera mod for Minecraft 1.21.x (Fabric).
 *
 * Tracks a named player with configurable camera modes:
 *   FIRST_PERSON  — camera at the target's eye position
 *   THIRD_PERSON  — camera pulled back behind the target
 *   ORBIT         — camera slowly circles the target
 *
 * When the target is dead or offline the camera drifts in a slow
 * creative fly-around above the map until the player is seen again.
 */
public class SpecCam implements ClientModInitializer {

    public static final String MOD_ID = "spectacam";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Singleton camera controller — read by CameraMixin every render frame. */
    public static SpectatorCameraController cameraController;

    @Override
    public void onInitializeClient() {
        // 1. Load config from disk (writes defaults on first run)
        SpecCamConfig.load();

        // 2. Create controller
        cameraController = new SpectatorCameraController();

        // 3. Register keybinds
        KeyBindings.register();

        // 4. Register /spectacam command
        SpecCamCommand.register();

        // 5. Tick the controller and process keybinds every game tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            KeyBindings.handleInput(client);
            cameraController.tick(client);
        });

        // 6. If a default target is configured, set it on load
        String def = SpecCamConfig.get().defaultTarget;
        if (def != null && !def.isBlank()) {
            cameraController.setTarget(def);
            LOGGER.info("[SpecCam] Auto-targeting default player: {}", def);
        }

        LOGGER.info("[SpecCam] Initialized — use /spectacam target <name> to begin.");
    }
}
