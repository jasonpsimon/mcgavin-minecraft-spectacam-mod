package net.reseraph.spectacam.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

//? if >=1.19 {
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
//?} else {
/*import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;*/
//?}

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import net.reseraph.spectacam.SpectaCam;
import net.reseraph.spectacam.camera.CameraMode;
import net.reseraph.spectacam.config.SpectaCamConfig;
import net.reseraph.spectacam.util.SpectaCamText;

/**
 * Registers the /spectacam client command tree.
 *
 * Usage:
 *   /spectacam &lt;playerName&gt;          — start spectating a player (preferred)
 *   /spectacam target &lt;playerName&gt;   — hidden legacy alias, kept for muscle memory
 *   /spectacam stop                   — stop spectating
 *   /spectacam mode first|third|orbit — switch camera mode
 *   /spectacam zoom in|out [amount]   — adjust zoom
 *   /spectacam status                 — print current state
 *   /spectacam config save            — persist current config
 *
 * Caveat on the shortened form: a player literally named stop/mode/zoom/
 * status/config would collide with the subcommand literals. In practice
 * nobody is. The legacy `/spectacam target &lt;name&gt;` form works for any name.
 */
public class SpectaCamCommand {

    /**
     * Tab-completes online player names from the client's player list.
     */
    private static final SuggestionProvider<FabricClientCommandSource> PLAYER_SUGGESTIONS =
        (ctx, builder) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getNetworkHandler() != null) {
                java.util.List<String> names = mc.getNetworkHandler().getPlayerList().stream()
                        .map(PlayerListEntry::getProfile)
                        .filter(java.util.Objects::nonNull)
                        //? if >=1.21.11 {
                        /*.map(com.mojang.authlib.GameProfile::name)*/
                        //?} else {
                        .map(com.mojang.authlib.GameProfile::getName)
                        //?}
                        .filter(java.util.Objects::nonNull)
                        .toList();
                return CommandSource.suggestMatching(names, builder);
            }
            return builder.buildFuture();
        };

    public static void register() {
        var tree =
            ClientCommandManager.literal("spectacam")

                // ── /spectacam <name> — preferred shortened form ─────────
                .then(ClientCommandManager.argument("player", StringArgumentType.word())
                    .suggests(PLAYER_SUGGESTIONS)
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "player");
                        SpectaCam.cameraController.startSpectating(name);
                        ctx.getSource().sendFeedback(
                            SpectaCamText.lit("§b[SpectaCam]§r Now targeting: §e" + name));
                        return 1;
                    })
                )

                // ── /spectacam target <name> — legacy alias ──────────────
                .then(ClientCommandManager.literal("target")
                    .then(ClientCommandManager.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "player");
                            SpectaCam.cameraController.startSpectating(name);
                            ctx.getSource().sendFeedback(
                                SpectaCamText.lit("§b[SpectaCam]§r Now targeting: §e" + name));
                            return 1;
                        })
                    )
                )

                // ── /spectacam stop ──────────────────────────────────────
                .then(ClientCommandManager.literal("stop")
                    .executes(ctx -> {
                        SpectaCam.cameraController.setTarget(null);
                        ctx.getSource().sendFeedback(
                            SpectaCamText.lit("§b[SpectaCam]§r Stopped spectating."));
                        return 1;
                    })
                )

                // ── /spectacam mode first|third|orbit ────────────────────
                .then(ClientCommandManager.literal("mode")
                    .then(ClientCommandManager.literal("first")
                        .executes(ctx -> {
                            SpectaCam.cameraController.setMode(CameraMode.FIRST_PERSON);
                            ctx.getSource().sendFeedback(
                                SpectaCamText.lit("§b[SpectaCam]§r Mode: First Person"));
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("third")
                        .executes(ctx -> {
                            SpectaCam.cameraController.setMode(CameraMode.THIRD_PERSON);
                            ctx.getSource().sendFeedback(
                                SpectaCamText.lit("§b[SpectaCam]§r Mode: Third Person"));
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("orbit")
                        .executes(ctx -> {
                            SpectaCam.cameraController.setMode(CameraMode.ORBIT);
                            ctx.getSource().sendFeedback(
                                SpectaCamText.lit("§b[SpectaCam]§r Mode: Orbit / Fly-Around"));
                            return 1;
                        })
                    )
                )

                // ── /spectacam zoom in|out [amount] ──────────────────────
                .then(ClientCommandManager.literal("zoom")
                    .then(ClientCommandManager.literal("in")
                        .executes(ctx -> {
                            SpectaCam.cameraController.adjustZoom(-1f);
                            return 1;
                        })
                        .then(ClientCommandManager.argument("amount", FloatArgumentType.floatArg(0.1f, 20f))
                            .executes(ctx -> {
                                float amt = FloatArgumentType.getFloat(ctx, "amount");
                                SpectaCam.cameraController.adjustZoom(-amt);
                                return 1;
                            })
                        )
                    )
                    .then(ClientCommandManager.literal("out")
                        .executes(ctx -> {
                            SpectaCam.cameraController.adjustZoom(1f);
                            return 1;
                        })
                        .then(ClientCommandManager.argument("amount", FloatArgumentType.floatArg(0.1f, 20f))
                            .executes(ctx -> {
                                float amt = FloatArgumentType.getFloat(ctx, "amount");
                                SpectaCam.cameraController.adjustZoom(amt);
                                return 1;
                            })
                        )
                    )
                )

                // ── /spectacam status ────────────────────────────────────
                .then(ClientCommandManager.literal("status")
                    .executes(ctx -> {
                        boolean active = SpectaCam.cameraController.isActive();
                        String target = SpectaCam.cameraController.getTargetName();
                        String mode   = SpectaCam.cameraController.getMode().getDisplayName();
                        float  radius = SpectaCam.cameraController.getOrbitRadius();
                        ctx.getSource().sendFeedback(SpectaCamText.lit(
                            "§b[SpectaCam Status]§r\n" +
                            "  Active : " + active + "\n" +
                            "  Target : " + (target != null ? target : "none") + "\n" +
                            "  Mode   : " + mode + "\n" +
                            "  Radius : " + String.format("%.1f", radius)
                        ));
                        return 1;
                    })
                )

                // ── /spectacam config save ───────────────────────────────
                .then(ClientCommandManager.literal("config")
                    .then(ClientCommandManager.literal("save")
                        .executes(ctx -> {
                            SpectaCamConfig.save();
                            ctx.getSource().sendFeedback(
                                SpectaCamText.lit("§b[SpectaCam]§r Config saved."));
                            return 1;
                        })
                    )
                );

        //? if >=1.19 {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(tree));
        //?} else {
        /*ClientCommandManager.DISPATCHER.register(tree);*/
        //?}
    }
}
