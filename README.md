# SpectaCam

A Fabric 1.21.x client-side mod that locks your camera onto a named player with configurable modes — then gracefully drifts into a slow creative idle fly when the target is offline or dead.

---

## Requirements

| Dependency    | Version  |
|---------------|----------|
| Minecraft     | 1.21.4   |
| Fabric Loader | 0.16.9+  |
| Fabric API    | 0.110.0+ |
| Java          | 21+      |

---

## Building

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/spectacam-1.0.0.jar`.

> **Note:** Verify that `gradle.properties` has the correct `yarn_mappings` and `fabric_version` for
> your exact MC version before building. Check https://fabricmc.net/develop/ for the latest values.

---

## Installing

Drop the JAR into your `.minecraft/mods/` folder alongside Fabric API. **Client-side only** — does not need to be on the server.

---

## Usage

### Start spectating

```
/spectacam <playerName>
```

The mod will:

1. Put you in spectator mode if you aren't already (requires OP on most servers).
2. Issue `/spectate <playerName>` to attach your client's spectator camera to the target so the server streams their entity data to you.
3. Lock the camera onto that player using the current mode.

Tab-completion suggests online player names. The legacy form `/spectacam target <playerName>` is still accepted as a hidden alias.

> **Permissions note:** `/gamemode spectator` and `/spectate` are vanilla server-side commands that default to OP level 2. On a server where you aren't OP (or the server hasn't granted those permissions), the server will silently reject them and the camera will drift in idle mode until it can attach.

### Camera modes

| Command                    | Keybind | Description                              |
|----------------------------|---------|------------------------------------------|
| `/spectacam mode first`    | F7      | First-person at target's eye             |
| `/spectacam mode third`    | F7      | Third-person pulled back behind target   |
| `/spectacam mode orbit`    | F7      | Slow circular orbit around target        |

**F7** cycles through all three modes in order.

### Zoom

| Command                 | Keybind | Effect                     |
|-------------------------|---------|----------------------------|
| `/spectacam zoom in`    | `=`     | Move camera closer         |
| `/spectacam zoom out`   | `-`     | Move camera further        |
| `/spectacam zoom in 5`  | —       | Zoom in by a specific amount |

Zoom affects both third-person distance and orbit radius simultaneously.

### Stop spectating

```
/spectacam stop
```
or press **F8**.

### Status

```
/spectacam status
```

### Save config

```
/spectacam config save
```

---

## Behavior when target is dead / offline

When the target player dies or disconnects, SpectaCam waits a 3-second grace period (covers normal respawn animations), then enters **idle mode**:

- Camera seeds its starting position exactly where it last was and gently lerps upward toward cruising altitude over ~20–30 seconds. No instant elevator jump.
- Slowly drifts 140–160 blocks above the terrain (configurable).
- Randomly picks a new heading every 45–150 seconds and smoothly turns toward it.
- Gently undulates pitch so it doesn't feel static.
- While idle, the mod re-issues `/spectate <name>` every 10 seconds for the first 6 attempts (covers most servers that auto-detach on target death/disconnect), then backs off to once per minute so it never fully gives up but also never spams.

When the target **respawns** or **reconnects** and becomes visible again, the camera rubber-bands smoothly back to them over ~3 seconds (not a hard snap) and resumes the active mode automatically.

### Rubbery transitions

Both the "rise into idle" and the "return to target" transitions are deliberately soft. At every state change the camera's smoothing factor drops to `transitionSmoothing` (default 0.04) and linearly ramps back to the configured `smoothing` over `transitionRampTicks` (default 60 ticks / 3 seconds). This produces an ease-out feel rather than a uniform chase.

---

## Config

Edit `.minecraft/config/spectacam.json`. A fresh file is written with defaults on first launch — edit in place.

| Key                         | Default | Description                                              |
|-----------------------------|---------|----------------------------------------------------------|
| `orbitRadius`               | 8.0     | Circle radius for orbit mode (blocks)                    |
| `orbitSpeed`                | 0.5     | Orbit degrees advanced per tick (20 ticks/sec)           |
| `thirdPersonDistance`       | 5.0     | Starting pull-back for third-person mode                 |
| `zoomStep`                  | 1.0     | How many blocks each zoom key press / step changes       |
| `smoothing`                 | 0.15    | Primary camera lerp factor per tick (0.05 floaty … 1.0 instant) |
| `transitionSmoothing`       | 0.04    | Softer smoothing during transitions. Lower = rubberier   |
| `transitionRampTicks`       | 60      | Duration (ticks) that transitionSmoothing ramps to smoothing |
| `idleHeightMin`             | 140     | Target cruising altitude floor (blocks)                  |
| `idleHeightMax`             | 160     | Target cruising altitude ceiling (blocks)                |
| `idleMoveSpeed`             | 0.25    | Forward drift speed (blocks/tick)                        |
| `idleYawLerpRate`           | 0.0001  | How fast idle camera turns toward new headings           |
| `idleDirectionChangeMinSec` | 45      | Min seconds between random heading changes              |
| `idleDirectionChangeMaxSec` | 150     | Max seconds between random heading changes              |
| `idleDirectionChangeMinDeg` | 5       | Min degrees of change per heading shift                 |
| `idleDirectionChangeMaxDeg` | 35      | Max degrees of change per heading shift                 |
| `idleHeightOscillation`     | 15      | ± block amplitude of the slow vertical wave              |
| `idlePitchBase`             | 20      | Base look-down angle (degrees)                           |
| `idlePitchOscillation`      | 12      | ± degree amplitude of pitch undulation                   |
| `defaultTarget`             | `""`    | Auto-target this player on every connection              |

---

## Notes

- **Spectator mode is automatic.** `/spectacam <name>` issues `/gamemode spectator` for you if you're not already there. That command is OP-gated by default on most servers — you won't be able to spectate without the permission.
- **Entity tracking range matters.** The client can only see players whose entity packets the server is streaming. `/spectate <name>` (auto-issued) tells the server to attach your spectator camera to the target, which makes the server stream that player's data to you regardless of distance or dimension.
- This is a purely **visual** override — it does not affect chunk loading, hitbox detection, or server-side position.

---

## Changelog

### 1.14.1
- Fixed: target player going invisible in THIRD_PERSON and ORBIT modes. Minecraft was culling the camera-focused entity because its internal `thirdPerson` flag stayed false even though SpectaCam had pulled the camera away from the target's eye. CameraMixin now writes that flag to match SpectaCam's mode after every camera update.

### 1.14.0
- Command shortened to `/spectacam <playerName>` (legacy `/spectacam target <name>` still works).
- Auto-dispatches `/gamemode spectator` + `/spectate <name>` when spectating is requested.
- Re-attach retry while idle: fires `/spectate <name>` every 10s for first 6 attempts, then every 60s — covers target deaths and disconnect/reconnect.
- Tab-complete online player names.
- Fixed: camera no longer teleports to world origin (0,0,0) during the initial grace period before the target is first located.
- Fixed: no more hard snap when returning from idle to the target — smooth state is preserved and rubber-bands back.
- New rubbery transitions on grace→idle and idle→tracking via `transitionSmoothing` + `transitionRampTicks` config.
- Idle camera no longer jumps +80 Y on seed; softly lerps toward cruising altitude over ~20–30 seconds.

---

## License

MIT
