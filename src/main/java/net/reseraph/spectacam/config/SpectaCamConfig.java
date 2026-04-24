package net.reseraph.spectacam.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.reseraph.spectacam.SpectaCam;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Flat JSON config stored at .minecraft/config/spectacam.json.
 *
 * All fields are public so Gson can serialize/deserialize them directly.
 * Call SpectaCamConfig.get() anywhere to read the current instance.
 */
public class SpectaCamConfig {

    private static final Gson   GSON        = new GsonBuilder().setPrettyPrinting().create();
    private static final Path   CONFIG_PATH = FabricLoader.getInstance()
                                                           .getConfigDir()
                                                           .resolve("spectacam.json");
    private static SpectaCamConfig INSTANCE = new SpectaCamConfig();

    // ─── Config fields ────────────────────────────────────────────────────────

    // ─── Orbit mode ──────────────────────────────────────────────────────────

    /** Orbit circle radius in blocks — how far from the player the camera sits. Default: 8 */
    public float orbitRadius = 8f;

    /** Orbit rotation speed in degrees per tick. 0.5 = full loop in ~36 sec. Default: 0.5 */
    public float orbitSpeed = 0.5f;

    // ─── Third-person mode ───────────────────────────────────────────────────

    /** Starting distance behind the player for third-person mode, in blocks. Default: 5 */
    public float thirdPersonDistance = 5f;

    // ─── Zoom (affects third-person distance and orbit radius) ───────────────

    /** How many blocks to move per zoom key press / command step. Default: 1 */
    public float zoomStep = 1f;

    // ─── Global camera smoothing ─────────────────────────────────────────────

    /**
     * Camera smoothing / lerp factor per tick (0.05 – 1.0).
     *   0.05 = very floaty / rubbery
     *   0.15 = smooth cinematic (default)
     *   0.30 = snappy but still smooth
     *   1.0  = instant, no smoothing
     */
    public float smoothing = 0.15f;

    /**
     * Softer smoothing used during the first {@link #transitionRampTicks} ticks
     * after a state change (grace→idle, idle→tracking). Lets the camera
     * deliberately ease out of and into transitions with a rubbery feel,
     * then ramps linearly back to {@link #smoothing}.
     *
     * Lower = rubberier start/end. 0.04 is a comfortable default.
     */
    public float transitionSmoothing = 0.04f;

    /**
     * Duration of the rubbery transition ramp in ticks (20 ticks = 1 sec).
     * 60 ticks = 3 sec by default.
     */
    public int transitionRampTicks = 60;

    // ─── Idle fly-around (active when target is dead or offline) ─────────────

    /** Minimum Y height the idle camera drifts to. Default: 140 (50 below clouds) */
    public float idleHeightMin = 140f;

    /** Maximum Y height the idle camera drifts to. Default: 160 */
    public float idleHeightMax = 160f;

    /** Forward drift speed in blocks per tick. 0.25 = 5 blocks/sec. Default: 0.25 */
    public float idleMoveSpeed = 0.25f;

    /** Turn smoothness — how quickly it rotates toward new headings. Higher = snappier. Default: 0.0001 */
    public float idleYawLerpRate = 0.0001f;

    /** Minimum seconds between random heading changes. Default: 45 */
    public float idleDirectionChangeMinSec = 45f;

    /** Maximum seconds between random heading changes. Default: 150 */
    public float idleDirectionChangeMaxSec = 150f;

    /** Minimum degrees of heading change per direction shift. Default: 5 */
    public float idleDirectionChangeMinDeg = 5f;

    /** Maximum degrees of heading change per direction shift. Default: 35 */
    public float idleDirectionChangeMaxDeg = 35f;

    /** ± block amplitude of the slow up/down wave. Default: 15 */
    public float idleHeightOscillation = 15f;

    /** Base look-down angle in degrees. Default: 20 */
    public float idlePitchBase = 20f;

    /** ± degree amplitude of the pitch undulation. Default: 12 */
    public float idlePitchOscillation = 12f;

    // ─── Startup behavior ────────────────────────────────────────────────────

    /** If set, automatically targets this player on connection. Empty = disabled. */
    public String defaultTarget = "";

    // ─── Static API ──────────────────────────────────────────────────────────

    public static SpectaCamConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                SpectaCamConfig loaded = GSON.fromJson(r, SpectaCamConfig.class);
                if (loaded != null) INSTANCE = loaded;
            } catch (IOException e) {
                SpectaCam.LOGGER.warn("[SpectaCam] Failed to read config, using defaults: {}", e.getMessage());
                INSTANCE = new SpectaCamConfig();
            }
        }
        save(); // write defaults / fill any missing fields
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(INSTANCE, w);
        } catch (IOException e) {
            SpectaCam.LOGGER.warn("[SpectaCam] Failed to save config: {}", e.getMessage());
        }
    }
}
