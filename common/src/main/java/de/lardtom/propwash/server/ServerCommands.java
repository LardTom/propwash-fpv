package de.lardtom.propwash.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.lardtom.propwash.server.rules.RulePreset;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public final class ServerCommands {
    private ServerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal("propwash")
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("rules")
                        .executes(ctx -> rules(ctx.getSource()))
                        .then(Commands.literal("preset")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(RulePreset.values()).map(RulePreset::id), builder))
                                        .executes(ctx -> preset(ctx.getSource(), StringArgumentType.getString(ctx, "preset"))))))
                .then(Commands.literal("range")
                        .executes(ctx -> range(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> range(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("pilots")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> pilots(ctx.getSource()))));
    }

    private static int reload(CommandSourceStack source) {
        ServerRulesConfig.CONFIG.load();
        ServerModule.broadcastRules(source.getServer());
        source.sendSuccess(() -> Component.translatable("propwash.server.config_reloaded"), true);
        return 1;
    }

    private static int rules(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        ServerRulesConfig config = ServerRulesConfig.get();
        ServerRulesS2C rules = player != null ? ServerRules.snapshotFor(player) : new ServerRulesS2C(true,
                config.allowUnlimitedRange, config.allowUnlimitedRange, config.unlimitedRangePermissionLevel,
                config.maxPilotDistance, config.maxDroneSpeed, config.droneViewDistance, config.maxStreamedChunks,
                config.onlyGeneratedChunks, config.generateChunksPermissionLevel, !config.onlyGeneratedChunks,
                config.allowPilotingInSpawnProtection, config.allowSpectating);
        source.sendSuccess(() -> Component.translatable("propwash.server.command.rules.header").withStyle(ChatFormatting.GOLD), false);
        for (ServerRulesText.Row row : ServerRulesText.rows(rules)) {
            source.sendSuccess(() -> ServerRulesText.line(row), false);
        }
        ServerRulesText.Row worldCourses = new ServerRulesText.Row(Component.translatable("propwash.server.rule.world_courses"),
                ServerRulesText.yesNo(config.worldCourses));
        source.sendSuccess(() -> ServerRulesText.line(worldCourses), false);
        source.sendSuccess(() -> Component.translatable("propwash.server.command.rules.world_header").withStyle(ChatFormatting.GOLD), false);
        Optional<RulePreset> matching = WorldRules.matchingPreset(source.getServer());
        ServerRulesText.Row presetRow = new ServerRulesText.Row(Component.translatable("propwash.server.world_rules.presets"),
                matching.<Component>map(p -> Component.translatable(p.nameKey()))
                        .orElseGet(() -> Component.translatable("propwash.server.world_rules.preset.none")));
        source.sendSuccess(() -> ServerRulesText.line(presetRow), false);
        for (WorldRulesS2C.Entry entry : WorldRules.entries(source.getServer())) {
            ServerRulesText.Row row = new ServerRulesText.Row(WorldRuleText.name(entry.id()), WorldRuleText.value(entry.id(), entry.value()));
            source.sendSuccess(() -> ServerRulesText.line(row), false);
        }
        return 1;
    }

    private static int preset(CommandSourceStack source, String id) {
        Optional<RulePreset> preset = RulePreset.byId(id);
        if (preset.isEmpty()) {
            source.sendFailure(Component.translatable("propwash.server.command.rules.preset.unknown", id,
                    String.join(", ", Arrays.stream(RulePreset.values()).map(RulePreset::id).toList())));
            return 0;
        }
        int changed = WorldRules.applyPreset(source.getServer(), preset.get(), source.getDisplayName(), source.getTextName());
        if (source.getPlayer() == null || changed == 0) {
            source.sendSuccess(() -> Component.translatable("propwash.server.command.rules.preset.done",
                    Component.translatable(preset.get().nameKey()), changed), false);
        }
        return Math.max(1, changed);
    }

    private static int range(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException {
        Component name = player.getDisplayName();
        boolean unlimited = ServerRules.unlimitedRange(player);
        PilotLink link = RangeManager.link(player.getUUID()).orElse(null);
        if (link == null) {
            Component allowance = unlimited
                    ? Component.translatable("propwash.server.value.unlimited")
                    : ServerRulesText.blocks(ServerRules.limitedRange(player));
            source.sendSuccess(() -> Component.translatable("propwash.server.command.range.idle", name, allowance), false);
            return 0;
        }
        Component message = switch (link.decision()) {
            case ANCHOR -> Component.translatable("propwash.server.command.range.anchored", name,
                    ServerRulesText.decimal(link.distance()), link.viewDistance());
            case LIMITED -> Component.translatable("propwash.server.command.range.limited", name,
                    ServerRulesText.decimal(link.distance()), ServerRulesText.blocks(ServerRules.limitedRange(player)));
            default -> Component.translatable("propwash.server.command.range.detached", name);
        };
        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int pilots(CommandSourceStack source) {
        Collection<PilotLink> links = RangeManager.links();
        if (links.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("propwash.server.command.pilots.none"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("propwash.server.command.pilots.header", links.size()).withStyle(ChatFormatting.GOLD), false);
        for (PilotLink link : links) {
            ServerPlayer pilot = link.pilot();
            Component name = pilot != null ? pilot.getDisplayName() : Component.literal(link.pilotId().toString());
            Vec3 pos = link.drone().position();
            Component mode = switch (link.decision()) {
                case ANCHOR -> Component.translatable("propwash.server.mode.anchored", link.viewDistance());
                case LIMITED -> Component.translatable("propwash.server.mode.limited");
                default -> Component.translatable("propwash.server.mode.detached");
            };
            source.sendSuccess(() -> Component.translatable("propwash.server.command.pilots.entry", name, link.drone().getId(),
                    Math.round(pos.x), Math.round(pos.y), Math.round(pos.z), ServerRulesText.decimal(link.distance()), mode), false);
        }
        source.sendSuccess(() -> Component.translatable("propwash.server.command.pilots.stats", RangeManager.ticketCount(),
                RangeManager.pendingChunkChecks()).withStyle(ChatFormatting.GRAY), false);
        return links.size();
    }
}
