package com.pathways.beyond.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.pathways.beyond.PathwaysMod;
import com.pathways.beyond.event.WorldEventManager;
import com.pathways.beyond.network.ModNetwork;
import com.pathways.beyond.pathway.PlayerPathway;
import com.pathways.beyond.pathway.SequenceLogic;
import com.pathways.beyond.registry.ModAttachments;
import com.pathways.beyond.sanity.OccultState;
import com.pathways.beyond.sanity.SanitySystem;
import com.pathways.beyond.spirit.SoulProjection;
import com.pathways.beyond.spirit.SpiritWorld;
import com.pathways.beyond.worldgen.StructurePlacer;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * Operator and player commands.
 *
 * <p>Debug surface for a mod whose systems are mostly invisible: sanity, corruption, digestion,
 * the ritual site, the events, and the Spirit World all get direct readouts, because a system
 * the player cannot observe is a system the player cannot enjoy.
 */
public final class PathwaysCommand {
    private PathwaysCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("pathways")
                .executes(ctx -> info(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                // ---- player-facing readouts ---------------------------------------------
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx.getSource(), ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("codex")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ModNetwork.sendPathwaySync(player);
                            player.displayClientMessage(Component.literal(
                                    "Your Codex knowledge is in the Codex screen (use a Pathway Codex)."), false);
                            return 1;
                        }))
                // ---- operator state manipulation ---------------------------------------
                .then(Commands.literal("sanity")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 100.0))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    float value = (float) DoubleArgumentType.getDouble(ctx, "value");
                                    OccultState state = ModAttachments.occult(player);
                                    state.setSanity(value);
                                    ModNetwork.sendOccultSync(player);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Sanity = " + value), false);
                                    return 1;
                                })))
                .then(Commands.literal("corruption")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 100.0))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    float value = (float) DoubleArgumentType.getDouble(ctx, "value");
                                    SanitySystem.setCorruption(player, value);
                                    ModNetwork.sendOccultSync(player);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Corruption = " + value + " ("
                                                    + SanitySystem.stageFor(value).id() + ")"), false);
                                    return 1;
                                })))
                .then(Commands.literal("sequence")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("value", IntegerArgumentType.integer(-1, 9))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int value = IntegerArgumentType.getInteger(ctx, "value");
                                    PlayerPathway pathway = ModAttachments.pathway(player);
                                    pathway.setSequence(value);
                                    ModNetwork.sendPathwaySync(player);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Sequence = " + value), false);
                                    return 1;
                                })))
                .then(Commands.literal("advance")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            PlayerPathway pathway = ModAttachments.pathway(player);
                            pathway.addDigestion(100.0f);
                            SequenceLogic.advance(player, false);
                            ModNetwork.sendPathwaySync(player);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Attempted advancement to Sequence " + pathway.sequence()), false);
                            return 1;
                        }))
                .then(Commands.literal("pathway")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("fool"), builder))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    PlayerPathway pathway = ModAttachments.pathway(player);
                                    pathway.begin(id, 9);
                                    ModNetwork.sendPathwaySync(player);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Pathway set to " + id + " at Sequence 9"), false);
                                    return 1;
                                })))
                // ---- rituals ------------------------------------------------------------
                .then(Commands.literal("ritual")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    var recipes = com.pathways.beyond.ritual.RitualRecipes.forPlayer(
                                            ModAttachments.pathway(player));
                                    for (var recipe : recipes) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                recipe.id() + " - " + recipe.display()), false);
                                    }
                                    return recipes.size();
                                }))
                        .then(Commands.literal("site")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    var site = com.pathways.beyond.ritual.RitualManager.findSite(
                                            player.serverLevel(), player.blockPosition());
                                    if (site == null) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "No ritual site here. Needs chalk, candles and a basin."));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Site: chalk " + site.chalkBlocks().size()
                                                    + ", candles " + site.litCandles().size()
                                                    + ", basins " + site.basins().size()), false);
                                    return 1;
                                })))
                // ---- world events -------------------------------------------------------
                .then(Commands.literal("event")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(WorldEventManager.Event.values())
                                                .map(e -> e.id).toList(), builder))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    for (WorldEventManager.Event event : WorldEventManager.Event.values()) {
                                        if (event.id.equals(id)) {
                                            if (event == WorldEventManager.Event.THE_NOTICING) {
                                                WorldEventManager.beginTheNoticing(player.serverLevel(), player);
                                            } else {
                                                WorldEventManager.start(player.serverLevel(), event);
                                            }
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Started event: " + id), true);
                                            return 1;
                                        }
                                    }
                                    ctx.getSource().sendFailure(Component.literal("Unknown event: " + id));
                                    return 0;
                                }))
                        .then(Commands.literal("noticing")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    WorldEventManager.beginTheNoticing(player.serverLevel(), player);
                                    return 1;
                                })))
                // ---- spirit world -------------------------------------------------------
                .then(Commands.literal("spirit")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("enter")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    SpiritWorld.travel(player, true);
                                    return 1;
                                }))
                        .then(Commands.literal("exit")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    SpiritWorld.travel(player, false);
                                    return 1;
                                }))
                        .then(Commands.literal("project")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            SoulProjection.request(player, BoolArgumentType.getBool(ctx, "enabled"));
                                            return 1;
                                        }))))
                // ---- worldgen -----------------------------------------------------------
                .then(structureCommand())
                // ---- targeting other players -------------------------------------------
                .then(Commands.literal("target")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("state")
                                        .executes(ctx -> info(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> structureCommand() {
        return Commands.literal("structure")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            for (StructurePlacer.Rule rule : StructurePlacer.RULES) {
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        rule.id() + " (spacing " + rule.spacing() + ")"), false);
                            }
                            return StructurePlacer.RULES.size();
                        }))
                .then(Commands.literal("place")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        StructurePlacer.RULES.stream().map(StructurePlacer.Rule::id).toList(),
                                        builder))
                                .executes(ctx -> placeStructure(ctx, null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> placeStructure(ctx,
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))));
    }

    private static int placeStructure(CommandContext<CommandSourceStack> ctx, BlockPos pos)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "id");
        BlockPos target = pos == null ? player.blockPosition().offset(8, 0, 8) : pos;
        boolean placed = StructurePlacer.forceAt(player.serverLevel(), id, target,
                net.minecraft.util.RandomSource.create(player.getRandom().nextLong()));
        ctx.getSource().sendSuccess(() -> Component.literal("Placement of " + id + ": " + placed), true);
        return placed ? 1 : 0;
    }

    private static int info(CommandSourceStack source, ServerPlayer player) {
        PlayerPathway pathway = ModAttachments.pathway(player);
        OccultState state = ModAttachments.occult(player);
        source.sendSuccess(() -> Component.literal("=== Pathways of the Beyond ==="), false);
        source.sendSuccess(() -> Component.literal("pathway: "
                + (pathway.hasPathway() ? pathway.pathwayId() : "none")
                + "  sequence: " + pathway.sequence()
                + "  digestion: " + String.format("%.1f", pathway.digestion()) + "%"), false);
        source.sendSuccess(() -> Component.literal("sanity: " + String.format("%.1f", state.sanity())
                + " (" + SanitySystem.tierFor(state.sanity()).id() + ")"
                + "  corruption: " + String.format("%.1f", state.corruption())
                + " (" + SanitySystem.stageFor(state.corruption()).id() + ")"), false);
        source.sendSuccess(() -> Component.literal("rituals known: " + pathway.knownRituals().size()
                + "  threads bound: " + com.pathways.beyond.soul.SoulThreadManager.boundCount(player)
                + "  spirit form: " + state.spiritForm()
                + "  noticed: " + state.noticedByTheBeyond()), false);
        ServerLevel level = player.serverLevel();
        source.sendSuccess(() -> Component.literal("dimension: "
                + level.dimension().location()
                + "  red moon: " + WorldEventManager.isRedMoon(level)
                + "  noticing: " + WorldEventManager.theNoticingHasBegun()), false);
        return 1;
    }

    /** Log helper used at startup so a broken command registration is visible in the log. */
    public static void logRegistered() {
        PathwaysMod.LOGGER.info("[Pathways] commands registered under /pathways");
    }
}
