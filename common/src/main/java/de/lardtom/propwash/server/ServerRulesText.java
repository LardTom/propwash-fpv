package de.lardtom.propwash.server;

import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ServerRulesText {
    public static final String DISTANCE_LIMIT_KEY = "propwash.server.rule.distance_limit";

    private ServerRulesText() {
    }

    public static List<Row> rows(ServerRulesS2C rules) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Component.translatable("propwash.server.rule.may_pilot"), yesNo(rules.mayPilot())));
        rows.add(new Row(Component.translatable(DISTANCE_LIMIT_KEY), distanceLimit(rules)));
        rows.add(new Row(Component.translatable("propwash.server.rule.max_drone_speed"), speed(rules.maxDroneSpeed())));
        rows.add(new Row(Component.translatable("propwash.server.rule.drone_view_distance"),
                Component.translatable("propwash.server.value.chunks", rules.droneViewDistance())));
        rows.add(new Row(Component.translatable("propwash.server.rule.max_streamed_chunks"),
                Component.translatable("propwash.server.value.chunks", rules.maxStreamedChunks())));
        rows.add(new Row(Component.translatable("propwash.server.rule.new_chunks"),
                Component.translatable("propwash.server.value.with_rule", yesNo(rules.mayGenerateChunks()), generators(rules))));
        rows.add(new Row(Component.translatable("propwash.server.rule.spawn_protection"), yesNo(rules.pilotingInSpawnProtection())));
        rows.add(new Row(Component.translatable("propwash.server.rule.spectating"), yesNo(rules.spectating())));
        return rows;
    }

    public static Component distanceLimit(ServerRulesS2C rules) {
        Component unlimitedFor = rules.unlimitedRangePermissionLevel() <= 0
                ? Component.translatable("propwash.server.value.everyone")
                : Component.translatable("propwash.server.value.level", rules.unlimitedRangePermissionLevel());
        if (rules.unlimitedRange()) {
            return Component.translatable("propwash.server.value.with_rule", Component.translatable("propwash.server.value.none"), unlimitedFor);
        }
        Component limit = blocks(rules.maxPilotDistance());
        return rules.unlimitedRangeEnabled() ? Component.translatable("propwash.server.value.limit_except", limit, unlimitedFor) : limit;
    }

    public static Component line(Row row) {
        return Component.translatable("propwash.server.rule.line", row.label(), row.value().copy().withStyle(ChatFormatting.AQUA));
    }

    public static Component generators(ServerRulesS2C rules) {
        if (!WorldRules.current().on(WorldRuleSpecs.DRONES_GENERATE_CHUNKS)) {
            return Component.translatable("propwash.server.value.world_rule_off");
        }
        if (!rules.onlyGeneratedChunks() || rules.generateChunksPermissionLevel() <= 0) {
            return Component.translatable("propwash.server.value.everyone");
        }
        if (rules.generateChunksPermissionLevel() >= ServerRulesConfig.GENERATE_NOBODY) {
            return Component.translatable("propwash.server.value.nobody");
        }
        return Component.translatable("propwash.server.value.level", rules.generateChunksPermissionLevel());
    }

    public static Component yesNo(boolean value) {
        return Component.translatable(value ? "propwash.server.value.yes" : "propwash.server.value.no");
    }

    public static Component blocks(double blocks) {
        if (!Double.isFinite(blocks)) {
            return Component.translatable("propwash.server.value.unlimited");
        }
        return Component.translatable("propwash.server.value.blocks", Math.round(blocks));
    }

    public static Component speed(double blocksPerSecond) {
        if (!(blocksPerSecond > 0.0) || !Double.isFinite(blocksPerSecond)) {
            return Component.translatable("propwash.server.value.unlimited");
        }
        return Component.translatable("propwash.server.value.speed", Math.round(blocksPerSecond),
                String.format(Locale.ROOT, "%.0f", blocksPerSecond * 3.6));
    }

    public static MutableComponent decimal(double value) {
        return Component.literal(String.format(Locale.ROOT, "%.0f", value));
    }

    public record Row(Component label, Component value) {
    }
}
