package de.lardtom.propwash.server;

import com.mojang.brigadier.tree.CommandNode;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.dev.example.ExampleRules;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class CovAddonRulesServerChecks {
    private static final int TARGET = 12;
    private static String summary = "";

    private CovAddonRulesServerChecks() {
    }

    private static WorldRuleEditC2S edit(Identifier id, String value) {
        return new WorldRuleEditC2S(List.of(new WorldRulesS2C.Entry(id, value)));
    }

    private static int cooldown(MinecraftServer server) {
        return ExampleRules.cooldown().get(server.overworld());
    }

    private static boolean isGuest(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null && WorldRulesRights.GUEST.equals(player.getUUID());
    }

    private static void requirement(CommandNode<CommandSourceStack> node, Predicate<CommandSourceStack> requirement) throws ReflectiveOperationException {
        Field field = CommandNode.class.getDeclaredField("requirement");
        field.setAccessible(true);
        field.set(node, requirement);
    }

    private static int viaCommand(MinecraftServer server, ServerPlayer player, Identifier id, int value) {
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "gamerule " + id + " " + value);
        return cooldown(server);
    }

    public static @Nullable String check(MinecraftServer server) {
        if (!ExampleRules.registered()) {
            summary = "Beispiel-Add-on aus, nichts zu prüfen";
            return null;
        }
        ServerPlayer guest = WorldRulesRights.guest(server);
        if (server.getPlayerList().isOp(guest.nameAndId())) {
            return "Gast steht schon in der Operator-Liste";
        }
        Identifier id = ExampleRules.LAUNCHER_COOLDOWN;
        WorldRule<Integer> rule = ExampleRules.cooldown();
        GameRule<?> game = AddonWorldRules.gameRule(id).orElse(null);
        if (game == null) {
            return id + " fehlt als Gamerule";
        }
        CommandNode<CommandSourceStack> gamerule = server.getCommands().getDispatcher().getRoot().getChild("gamerule");
        CommandNode<CommandSourceStack> node = gamerule == null ? null : gamerule.getChild(id.toString());
        CommandNode<CommandSourceStack> gravity = gamerule == null ? null : gamerule.getChild(WorldRuleSpecs.GRAVITY_SCALE.id());
        if (node == null || gravity == null) {
            return "Knoten /gamerule " + id + " oder " + WorldRuleSpecs.GRAVITY_SCALE.id() + " fehlt";
        }
        Predicate<CommandSourceStack> rootOriginal = gamerule.getRequirement();
        Predicate<CommandSourceStack> nodeOriginal = node.getRequirement();
        Predicate<CommandSourceStack> gravityOriginal = gravity.getRequirement();
        int before = cooldown(server);
        int gravityBefore = WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE);
        Map<String, String> seen = new LinkedHashMap<>();
        try {
            if (WorldRules.mayEdit(guest) || WorldRules.maySet(guest, game)) {
                return "Gast ohne Recht darf die Add-on-Regel ändern";
            }
            WorldRulesS2C snapshot = WorldRules.snapshotFor(guest);
            if (snapshot.editable() || snapshot.entries().stream().noneMatch(entry -> entry.id().equals(id))) {
                return "Gast sieht die Add-on-Regel nicht nur lesbar: editierbar " + snapshot.editable();
            }
            if (WorldRules.edit(edit(id, Integer.toString(TARGET)), new ServerPayloadContext(guest)) || cooldown(server) != before) {
                return "Server übernimmt die Änderung der Add-on-Regel von einem Gast ohne Recht";
            }
            if (viaCommand(server, guest, id, TARGET) != before) {
                return "Gast ohne Recht setzt die Add-on-Regel per /gamerule";
            }
            seen.put("Gast", "nur lesbar, Tab und /gamerule abgelehnt");
            requirement(gamerule, source -> isGuest(source) || rootOriginal.test(source));
            requirement(node, source -> !isGuest(source) && nodeOriginal.test(source));
            if (WorldRules.maySet(guest, game)) {
                return "Gast mit /gamerule, aber ohne den Knoten der Add-on-Regel, darf sie ändern";
            }
            if (!WorldRules.mayEdit(guest)) {
                return "Gast mit /gamerule darf keine Regel ändern";
            }
            if (WorldRules.edit(edit(id, Integer.toString(TARGET)), new ServerPayloadContext(guest)) && cooldown(server) != before) {
                return "Server übernimmt die Add-on-Regel trotz gesperrtem Knoten";
            }
            if (viaCommand(server, guest, id, TARGET) != before) {
                return "/gamerule setzt die Add-on-Regel trotz gesperrtem Knoten";
            }
            seen.put("Knoten der Add-on-Regel gesperrt", "Tab und /gamerule abgelehnt");
            requirement(node, nodeOriginal);
            requirement(gravity, source -> !isGuest(source) && gravityOriginal.test(source));
            if (!WorldRules.maySet(guest, game)) {
                return "Gast mit Knoten der Add-on-Regel darf sie nicht ändern";
            }
            if (!WorldRules.edit(edit(id, Integer.toString(TARGET)), new ServerPayloadContext(guest)) || cooldown(server) != TARGET) {
                return "Server übernimmt die Add-on-Regel mit freigegebenem Knoten nicht: " + cooldown(server);
            }
            WorldRules.edit(edit(Identifier.parse(WorldRuleSpecs.GRAVITY_SCALE.id()), "150"), new ServerPayloadContext(guest));
            if (WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE) != gravityBefore) {
                return "Propwash-Regel mit gesperrtem Knoten trotzdem geändert";
            }
            seen.put("nur der Knoten der Add-on-Regel frei", "Add-on-Regel geändert, Schwerkraft abgelehnt");
            requirement(gamerule, rootOriginal);
            requirement(gravity, gravityOriginal);
            WorldRules.setSerialized(server, game, AddonWorldRules.serialize(rule, rule.defaultValue()));
            server.getPlayerList().op(guest.nameAndId());
            if (!WorldRules.edit(edit(id, Integer.toString(TARGET)), new ServerPayloadContext(guest)) || cooldown(server) != TARGET) {
                return "Operator ändert die Add-on-Regel nicht";
            }
            if (WorldRules.entries(server).stream().noneMatch(entry -> entry.id().equals(id) && entry.value().equals(Integer.toString(TARGET)))) {
                return "Abgleich an die Clients ohne den neuen Wert";
            }
            seen.put("Operator", "Add-on-Regel auf " + TARGET);
            summary = "Add-on-Regel " + id + ": " + seen;
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "Rechte am Knoten nicht prüfbar: " + e;
        } finally {
            server.getPlayerList().deop(guest.nameAndId());
            try {
                requirement(gamerule, rootOriginal);
                requirement(node, nodeOriginal);
                requirement(gravity, gravityOriginal);
            } catch (ReflectiveOperationException | RuntimeException e) {
                Constants.LOG.error("[Server-Rauchtest] /gamerule-Knoten nicht zurückgesetzt", e);
            }
            WorldRules.setSerialized(server, game, AddonWorldRules.serialize(rule, rule.defaultValue()));
            WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, gravityBefore);
        }
    }

    public static String summary() {
        return summary;
    }
}
