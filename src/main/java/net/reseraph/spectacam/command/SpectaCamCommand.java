package net.reseraph.spectacam.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

//? if >=26 {
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;*/
//?} else if >=1.19 {
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
//?} else {
/*import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;*/
//?}

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

//? if >=26 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;*/
//?} else {
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
//?}
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
 *
 * MC 26.x (mojmap) renamed several APIs used here:
 *   MinecraftClient            → Minecraft
 *   PlayerListEntry            → PlayerInfo          (pkg: client.multiplayer)
 *   CommandSource.suggestMatching → SharedSuggestionProvider.suggest
 *   mc.getNetworkHandler()     → mc.getConnection()
 *   nh.getPlayerList()         → nh.getOnlinePlayers()
 *   GameProfile.getName()      → GameProfile.name()  (already gated via 1.21.11)
 */
public class SpectaCamCommand {

    // Fabric API renamed ClientCommandManager → ClientCommands in 26.x.
    // These wrappers keep the command-tree builder readable at ~20 call sites.
    private static LiteralArgumentBuilder<FabricClientCommandSource> lit(String name) {
        //? if >=26 {
        /*return ClientCommands.literal(name);*/
        //?} else {
        return ClientCommandManager.literal(name);
        //?}
    }

    private static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> arg(
            String name, ArgumentType<T> type) {
        //? if >=26 {
        /*return ClientCommands.argument(name, type);*/
        //?} else {
        return ClientCommandManager.argument(name, type);
        //?}
    }

    /**
     * Tab-completes online player names from the client's player list.
     */
    private static final SuggestionProvider<FabricClientCommandSource> PLAYER_SUGGESTIONS =
        (ctx, builder) -> {
            //? if >=26 {
            /*Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getConnection() != null) {
                java.util.List<String> names = mc.getConnection().getOnlinePlayers().stream()
                        .map(PlayerInfo::getProfile)
                        .filter(java.util.Objects::nonNull)
                        .map(com.mojang.authlib.GameProfile::name)
                        .filter(java.util.Objects::nonNull)
                        .toList();
                return SharedSuggestionProvider.suggest(names, builder);
            }*/
            //?} else {
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
            //?}
            return builder.buildFuture();
        };

    public static void register() {
        var tree =
            lit("spectacam")

                // ── /spectacam <name> — preferred shortened form ─────────
                .then(arg("player", StringArgumentType.word())
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
                .then(lit("target")
                    .then(arg("player", StringArgumentType.word())
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
                .then(lit("stop")
                    .executes(ctx -> {
                        SpectaCam.cameraController.setTarget(null);
                        ctx.getSource().sendFeedback(
                            SpectaCamText.lit("§b[SpectaCam]§r Stopped spectating."));
                        return 1;
                    })
                )

                // ── /spectacam mode first|third|orbit ────────────────────
                .then(lit("mode")
                    .then(lit("first")
                        .executes(ctx -> {
                            SpectaCam.cameraController.setMode(CameraMode.FIRST_PERSON);
                            ctx.getSource().sendFeedback(
                                SpectaCamText.lit("§b[SpectaCam]§r Mode: First Person"));
                            return 1;
                        })
                    )
                    .then(lit("third")
                        .executes(ctx -> {
                            SpectaCam.cameraController.setMode(CameraMode.THIRD_PERSON);
                            ctx.getSource().sendFeedback(
                                SpectaCamText.lit("§b[SpectaCam]§r Mode: Third Person"));
                            return 1;
                        })
                    )
                    .then(lit("orbit")
                        .executes(ctx -> {
                            SpectaCam.cameraController.setMode(CameraMode.ORBIT);
                            ctx.getSource().sendFeedback(
                                SpectaCamText.lit("§b[SpectaCam]§r Mode: Orbit / Fly-Around"));
                            return 1;
                        })
                    )
                )

                // ── /spectacam zoom in|out [amount] ──────────────────────
                .then(lit("zoom")
                    .then(lit("in")
                        .executes(ctx -> {
                            SpectaCam.cameraController.adjustZoom(-1f);
                            return 1;
                        })
                        .then(arg("amount", FloatArgumentType.floatArg(0.1f, 20f))
                            .executes(ctx -> {
                                float amt = FloatArgumentType.getFloat(ctx, "amount");
                                SpectaCam.cameraController.adjustZoom(-amt);
                                return 1;
                            })
                        )
                    )
                    .then(lit("out")
                        .executes(ctx -> {
                            SpectaCam.cameraController.adjustZoom(1f);
                            return 1;
                        })
                        .then(arg("amount", FloatArgumentType.floatArg(0.1f, 20f))
                            .executes(ctx -> {
                                float amt = FloatArgumentType.getFloat(ctx, "amount");
                                SpectaCam.cameraController.adjustZoom(amt);
                                return 1;
                            })
                        )
                    )
                )

                // ── /spectacam status ────────────────────────────────────
                .then(lit("status")
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
                .then(lit("config")
                    .then(lit("save")
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
