package net.reseraph.spectacam.camera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.reseraph.spectacam.SpectaCam;
import net.reseraph.spectacam.config.SpectaCamConfig;

/**
 * Central controller for SpectaCam.
 *
 * Each tick this class:
 *   1. Locates the target player in the client world.
 *   2. If found and alive  → delegates to the active CameraMode sub-controller.
 *   3. If dead or offline  → delegates to IdleCameraController.
 *   4. Lerps the smooth camera state toward the computed target state.
 *   5. Exposes the smoothed pos/pitch/yaw for the CameraMixin to consume.
 *
 * The smoothing factor (config: smoothing, 0.05–1.0) controls how quickly
 * the camera chases its target. Lower = floatier/rubbery, higher = snappier.
 *
 * During state transitions (grace→idle, idle→tracking) the smoothing factor
 * is temporarily replaced by {@link SpectaCamConfig#transitionSmoothing} and
 * ramps back to the configured value over
 * {@link SpectaCamConfig#transitionRampTicks} ticks. This produces a deliberate
 * "ease out" rubbery feel rather than a uniform linear chase.
 *
 * v1.14.0:
 *   - /gamemode spectator + /spectate <name> auto-dispatched on setTarget.
 *   - Re-attach retry while missing (fast burst then slow back-off).
 *   - No more Vec3d.ZERO teleport: camera override only engages after we've
 *     located the target at least once, OR idle mode has been seeded.
 *   - No more snap on target reacquire: smooth state is preserved, transition
 *     ramp provides the rubbery return.
 */
public class SpectatorCameraController {

    // ── Public state read by CameraMixin ─────────────────────────────────────
    public Vec3d   cameraPos      = Vec3d.ZERO;
    public float   cameraPitch    = 0f;
    public float   cameraYaw      = 0f;
    public boolean overrideCamera = false;

    // ── Previous-tick snapshot (v1.14.2 jitter fix) ──────────────────────────
    // The controller only advances once per game tick (20 Hz). The game
    // renders at frame rate (60+ Hz). Without interpolation between ticks the
    // camera sits frozen while the target keeps moving via vanilla's entity
    // interpolation, producing visible judder. CameraMixin lerps prev → curr
    // per frame using the render tickDelta to eliminate that gap.
    public Vec3d prevCameraPos   = Vec3d.ZERO;
    public float prevCameraPitch = 0f;
    public float prevCameraYaw   = 0f;

    // ── Smoothed (interpolated) state ─────────────────────────────────────────
    private Vec3d  smoothPos   = Vec3d.ZERO;
    private float  smoothPitch = 0f;
    private float  smoothYaw   = 0f;
    private boolean smoothInitialized = false;

    // ── Internal state ────────────────────────────────────────────────────────
    private String     targetName = null;
    private boolean    active     = false;
    private CameraMode mode       = CameraMode.FIRST_PERSON;

    private float thirdPersonDistance;

    private final OrbitCameraController orbit;
    private final IdleCameraController  idle = new IdleCameraController();

    /**
     * Has this controller ever seen the target alive this session?
     * Used to avoid publishing a bogus Vec3d.ZERO camera position during
     * the initial grace period before the target has ever been located.
     */
    private boolean everLocated = false;

    /**
     * How many consecutive ticks the target must be missing before we enter
     * idle mode. 60 ticks = 3 seconds — long enough to survive death animations,
     * respawn transitions, and brief chunk unloads without falsely going idle.
     */
    private static final int IDLE_GRACE_TICKS = 60;
    /** How often to re-scan the player list when we have no cached entity. */
    private static final int CHECK_INTERVAL   = 100;

    // ── Re-attach retry ───────────────────────────────────────────────────────
    /** Attempts #1..N are spaced at this interval while the target is missing. */
    private static final int REATTACH_FAST_INTERVAL = 200;  // 10s
    /** After the fast burst, attempts fall back to this interval. */
    private static final int REATTACH_SLOW_INTERVAL = 1200; // 60s
    /** How many fast attempts before backing off to slow cadence. */
    private static final int REATTACH_FAST_ATTEMPTS = 6;

    private int missingTickCount  = 0;
    private int checkTicker       = CHECK_INTERVAL;
    private int reattachTicker    = 0;
    private int reattachAttempts  = 0;

    /** Ticks remaining in the current rubbery transition (0 = no transition). */
    private int transitionTicks = 0;

    /** Pending delayed /spectate dispatch scheduling. Used so /gamemode spectator
     *  has a chance to land on the server before we tell it to attach. */
    private int    pendingSpectateTicks = 0;
    private String pendingSpectateName  = null;

    /** The live player entity we're tracking. null when lost/dead. */
    private PlayerEntity cachedTarget = null;

    public SpectatorCameraController() {
        SpectaCamConfig cfg = SpectaCamConfig.get();
        thirdPersonDistance = cfg.thirdPersonDistance;
        orbit = new OrbitCameraController(cfg.orbitRadius);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tick — called by ClientTickEvent
    // ─────────────────────────────────────────────────────────────────────────

    public void tick(MinecraftClient client) {
        // Process deferred /spectate dispatch regardless of active state so it
        // still fires even if someone /spectacam stops mid-delay. (It won't,
        // because setTarget(null) clears the pending name, but defensive.)
        if (pendingSpectateTicks > 0) {
            pendingSpectateTicks--;
            if (pendingSpectateTicks == 0 && pendingSpectateName != null) {
                dispatchSpectateCommand(client, pendingSpectateName);
                pendingSpectateName = null;
            }
        }

        if (!active || targetName == null || client.world == null) {
            overrideCamera = false;
            return;
        }

        // ── Step 1: Validate / refresh cached target ─────────────────────────
        if (cachedTarget != null && (!cachedTarget.isAlive() || cachedTarget.isRemoved())) {
            cachedTarget = null;
        }

        // If we have no cached target, rescan every CHECK_INTERVAL ticks.
        checkTicker++;
        if (cachedTarget == null && checkTicker >= CHECK_INTERVAL) {
            checkTicker = 0;
            cachedTarget = client.world.getPlayers().stream()
                    .filter(p -> p.getName().getString().equalsIgnoreCase(targetName))
                    .filter(p -> p.isAlive() && !p.isRemoved())
                    .findFirst().orElse(null);
        }

        // ── Step 2: Compute raw target position based on state ───────────────
        Vec3d targetPos;
        float targetPitch;
        float targetYaw;

        if (cachedTarget != null) {
            // ── TRACKING — we have a live player ─────────────────────────────
            boolean wasMissing = missingTickCount >= IDLE_GRACE_TICKS;

            if (!everLocated) {
                // First-ever acquisition: seed smooth state AT the target so we
                // don't lerp from Vec3d.ZERO into the world.
                Vec3d tp      = cachedTarget.getCameraPosVec(1.0f);
                smoothPos     = tp;
                smoothPitch   = cachedTarget.getPitch();
                smoothYaw     = cachedTarget.getYaw();
                smoothInitialized = true;
                everLocated   = true;
                sendHUD(client, "§a[SpectaCam] Attached to §e" + targetName);
            } else if (wasMissing) {
                // Coming back from idle/offline: begin a rubbery transition.
                // Crucially we do NOT reset smoothInitialized — the existing
                // smoothed state is what we lerp FROM, giving the rubber-band.
                orbit.reset();
                idle.reset();
                transitionTicks = Math.max(transitionTicks, SpectaCamConfig.get().transitionRampTicks);
                sendHUD(client, "§a[SpectaCam] Re-attached to §e" + targetName);
            }

            missingTickCount   = 0;
            reattachTicker     = 0;
            reattachAttempts   = 0;

            SpectaCamConfig cfg = SpectaCamConfig.get();
            switch (mode) {
                case FIRST_PERSON -> {
                    targetPos   = cachedTarget.getCameraPosVec(1.0f);
                    targetPitch = cachedTarget.getPitch();
                    targetYaw   = cachedTarget.getYaw();
                }
                case THIRD_PERSON -> {
                    float[] tp = computeThirdPerson(cachedTarget);
                    targetPos   = new Vec3d(tp[0], tp[1], tp[2]);
                    targetYaw   = tp[3];
                    targetPitch = tp[4];
                }
                case ORBIT -> {
                    orbit.tick(cachedTarget, cfg.orbitSpeed);
                    targetPos   = orbit.getPos();
                    targetPitch = orbit.getPitch();
                    targetYaw   = orbit.getYaw();
                }
                default -> {
                    targetPos   = cameraPos;
                    targetPitch = cameraPitch;
                    targetYaw   = cameraYaw;
                }
            }

        } else {
            // ── MISSING — player is dead, disconnected, or not yet found ─────
            missingTickCount++;

            if (missingTickCount == IDLE_GRACE_TICKS) {
                // Seed idle at an appropriate world position.
                Vec3d seed;
                if (everLocated) {
                    seed = cameraPos;
                } else if (client.player != null) {
                    seed = client.player.getCameraPosVec(1.0f);
                } else {
                    seed = Vec3d.ZERO;
                }
                idle.initAt(seed);

                // Ensure smooth state has a sensible starting point if we've
                // never published a camera position yet.
                if (!smoothInitialized) {
                    smoothPos   = seed;
                    smoothPitch = client.player != null ? client.player.getPitch() : 0f;
                    smoothYaw   = client.player != null ? client.player.getYaw()   : 0f;
                    smoothInitialized = true;
                }

                transitionTicks = SpectaCamConfig.get().transitionRampTicks;
                sendHUD(client, "§7[SpectaCam] Target lost — idle camera active");

                // First re-attach attempt fires the moment idle kicks in.
                attemptReAttach(client);
            }

            if (missingTickCount >= IDLE_GRACE_TICKS) {
                idle.tick();
                targetPos   = idle.getPos();
                targetPitch = idle.getPitch();
                targetYaw   = idle.getYaw();

                // Periodic re-attach while we stay in idle.
                reattachTicker++;
                int interval = (reattachAttempts < REATTACH_FAST_ATTEMPTS)
                        ? REATTACH_FAST_INTERVAL
                        : REATTACH_SLOW_INTERVAL;
                if (reattachTicker >= interval) {
                    reattachTicker = 0;
                    attemptReAttach(client);
                }
            } else if (everLocated) {
                // Grace period after having tracked — hold last camera position.
                targetPos   = cameraPos;
                targetPitch = cameraPitch;
                targetYaw   = cameraYaw;
            } else {
                // Grace period before ever locating — do NOT override the
                // vanilla camera. This prevents the Vec3d.ZERO teleport bug.
                overrideCamera = false;
                return;
            }
        }

        // ── Init smooth state on first publish, then lerp toward target ──────
        if (!smoothInitialized) {
            smoothPos   = targetPos;
            smoothPitch = targetPitch;
            smoothYaw   = targetYaw;
            smoothInitialized = true;
        }

        // Effective smoothing: during a transition, start soft and linearly
        // ramp back up to the configured smoothing. Gives an ease-out feel
        // at both ends of the transition.
        SpectaCamConfig cfg = SpectaCamConfig.get();
        float t;
        int rampMax = Math.max(1, cfg.transitionRampTicks);
        if (transitionTicks > 0) {
            float progress = 1f - (transitionTicks / (float) rampMax); // 0 → 1
            t = cfg.transitionSmoothing + (cfg.smoothing - cfg.transitionSmoothing) * progress;
            transitionTicks--;
        } else {
            t = cfg.smoothing;
        }

        smoothPos   = lerpVec(smoothPos, targetPos, t);
        smoothPitch = lerpAngle(smoothPitch, targetPitch, t);
        smoothYaw   = lerpAngle(smoothYaw,   targetYaw,   t);

        // ── Snapshot prev state for per-frame interpolation in CameraMixin ───
        // On the first publish of an override session, seed prev = smooth so
        // the mixin's lerp(prev, curr, tickDelta) is a no-op and we don't snap
        // from a stale cached value (which may be Vec3d.ZERO or the last
        // position from a prior session).
        if (overrideCamera) {
            prevCameraPos   = cameraPos;
            prevCameraPitch = cameraPitch;
            prevCameraYaw   = cameraYaw;
        } else {
            prevCameraPos   = smoothPos;
            prevCameraPitch = smoothPitch;
            prevCameraYaw   = smoothYaw;
        }

        // ── Publish smoothed values for CameraMixin ───────────────────────────
        cameraPos      = smoothPos;
        cameraPitch    = smoothPitch;
        cameraYaw      = smoothYaw;
        overrideCamera = true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Camera position computation
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns [x, y, z, yaw, pitch] */
    private float[] computeThirdPerson(PlayerEntity target) {
        Vec3d eye  = target.getCameraPosVec(1.0f);
        float tyaw = target.getYaw();

        double rad = Math.toRadians(tyaw);
        double dx  = -Math.sin(rad) * thirdPersonDistance;
        double dz  =  Math.cos(rad) * thirdPersonDistance;
        double dy  =  thirdPersonDistance * 0.4;

        Vec3d pos = eye.add(dx, dy, dz);
        return new float[]{ (float)pos.x, (float)pos.y, (float)pos.z, tyaw + 180f, 18f };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Interpolation helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Vec3d lerpVec(Vec3d from, Vec3d to, float t) {
        return new Vec3d(
            from.x + (to.x - from.x) * t,
            from.y + (to.y - from.y) * t,
            from.z + (to.z - from.z) * t
        );
    }

    /** Lerp that takes the shortest path around the 360° circle. */
    private float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff >  180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return from + diff * t;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Server-side dispatch helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by the /spectacam command. Issues /gamemode spectator (if not
     * already in spectator), then schedules a /spectate &lt;name&gt; dispatch a
     * few ticks later so the server can process the gamemode change first.
     * Finally sets the camera target so the controller begins tracking.
     */
    public void startSpectating(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        setTarget(name);

        if (client == null || client.getNetworkHandler() == null || client.player == null) {
            return;
        }

        // Step 1 — ensure we're in spectator mode. Server decides if we have perms.
        GameMode gm = (client.interactionManager != null)
                ? client.interactionManager.getCurrentGameMode()
                : null;
        if (gm != GameMode.SPECTATOR) {
            try {
                client.getNetworkHandler().sendChatCommand("gamemode spectator");
                sendHUD(client, "§7[SpectaCam] requested spectator mode…");
            } catch (Exception e) {
                SpectaCam.LOGGER.warn("[SpectaCam] /gamemode dispatch failed: {}", e.getMessage());
                sendHUD(client, "§c[SpectaCam] server refused /gamemode — need OP?");
            }
            // Queue /spectate a few ticks later to give the server time to
            // apply the gamemode change.
            pendingSpectateName  = name;
            pendingSpectateTicks = 5;
        } else {
            // Already in spectator — dispatch /spectate immediately.
            dispatchSpectateCommand(client, name);
        }
    }

    /** Fire a /spectate <name> chat command. No-op if disconnected. */
    private void dispatchSpectateCommand(MinecraftClient client, String name) {
        if (client == null) return;
        ClientPlayNetworkHandler nh = client.getNetworkHandler();
        if (nh == null || client.player == null || name == null) return;
        try {
            nh.sendChatCommand("spectate " + name);
        } catch (Exception e) {
            SpectaCam.LOGGER.warn("[SpectaCam] /spectate dispatch failed: {}", e.getMessage());
        }
    }

    /** Count a re-attach attempt and fire /spectate. */
    private void attemptReAttach(MinecraftClient client) {
        if (targetName == null) return;
        reattachAttempts++;
        SpectaCam.LOGGER.info("[SpectaCam] re-attach attempt #{} → /spectate {}",
                reattachAttempts, targetName);
        dispatchSpectateCommand(client, targetName);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void sendHUD(MinecraftClient client, String msg) {
        if (client != null && client.player != null) {
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§b[SpectaCam]§r " + msg), true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public controls
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Set or clear the tracked target by name. Does NOT issue any server-side
     * commands — call {@link #startSpectating(String)} from the command layer
     * if you want the full spectator + attach flow.
     */
    public void setTarget(String name) {
        this.targetName = (name != null && !name.isBlank()) ? name : null;
        this.active     = this.targetName != null;
        orbit.reset();
        idle.reset();
        missingTickCount  = 0;
        checkTicker       = CHECK_INTERVAL; // force immediate scan on next tick
        reattachTicker    = 0;
        reattachAttempts  = 0;
        cachedTarget      = null;
        everLocated       = false;
        smoothInitialized = false;
        transitionTicks   = 0;
        pendingSpectateTicks = 0;
        pendingSpectateName  = null;
        // overrideCamera is NOT forced true here — tick() decides once it
        // has something sensible to publish. This kills the Vec3d.ZERO
        // teleport that used to happen between command and first target
        // location.
        overrideCamera    = false;
    }

    public void setMode(CameraMode newMode) {
        this.mode = newMode;
        if (newMode == CameraMode.ORBIT) orbit.reset();
    }

    public void cycleMode() {
        setMode(mode.next());
    }

    /**
     * delta < 0 → zoom in (closer), delta > 0 → zoom out (further)
     */
    public void adjustZoom(float delta) {
        float step = SpectaCamConfig.get().zoomStep;
        thirdPersonDistance = Math.max(2f, Math.min(20f, thirdPersonDistance + delta * step));
        orbit.adjustRadius(delta * step);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isActive()     { return active; }
    public String  getTargetName(){ return targetName; }
    public CameraMode getMode()   { return mode; }
    public float  getOrbitRadius(){ return orbit.getRadius(); }
}
