package de.lardtom.propwash.server;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.tree.CommandNode;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class WorldRulesRights {
    public static final UUID GUEST = UUID.fromString("00000000-0000-4000-8000-0000000c0ffe");
    public static final String GUEST_NAME = "propwash_guest";
    private static final int FLOOD = 40;

    private WorldRulesRights() {
    }

    public static ServerPlayer guest(MinecraftServer server) {
        GameProfile profile = new GameProfile(GUEST, GUEST_NAME);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static WorldRuleEditC2S gravity(int percent) {
        return new WorldRuleEditC2S(List.of(new WorldRulesS2C.Entry(Identifier.parse(WorldRuleSpecs.GRAVITY_SCALE.id()), Integer.toString(percent))));
    }

    public static @Nullable String check(MinecraftServer server, boolean operator) {
        ServerPlayer guest = guest(server);
        int before = WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE);
        int target = before == 150 ? 120 : 150;
        if (server.getPlayerList().isOp(guest.nameAndId())) {
            return "Gast steht schon in der Operator-Liste";
        }
        if (WorldRules.mayEdit(guest)) {
            return "Gast ohne Operator-Rechte darf Welt-Regeln ändern";
        }
        if (viaCommand(server, guest, target) != before) {
            WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, before);
            return "Gast ohne Recht setzt die Regel per /gamerule";
        }
        if (WorldRules.edit(gravity(target), new ServerPayloadContext(guest)) || WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE) != before) {
            WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, before);
            return "Server übernimmt die Änderung eines Gasts ohne Recht";
        }
        RuleValues rulesBefore = WorldRules.values(server);
        RulePreset other = RulePreset.match(rulesBefore).orElse(null) == RulePreset.ARCADE ? RulePreset.TRAINING : RulePreset.ARCADE;
        if (WorldRules.preset(new WorldRulePresetC2S(other), new ServerPayloadContext(guest)) || !WorldRules.values(server).equals(rulesBefore)) {
            restore(server, rulesBefore);
            return "Server übernimmt die Voreinstellung eines Gasts ohne Recht";
        }
        for (int i = 0; i < FLOOD; i++) {
            boolean taken = WorldRules.edit(gravity(target), new ServerPayloadContext(guest))
                    | WorldRules.preset(new WorldRulePresetC2S(other), new ServerPayloadContext(guest));
            if (taken || !WorldRules.values(server).equals(rulesBefore)) {
                restore(server, rulesBefore);
                return "Server übernimmt beim " + (i + 1) + ". wiederholten Versuch eine Änderung eines Gasts ohne Recht";
            }
        }
        if (!operator) {
            return null;
        }
        server.getPlayerList().op(guest.nameAndId());
        try {
            if (!WorldRules.mayEdit(guest)) {
                return "Operator darf Welt-Regeln nicht ändern";
            }
            if (!WorldRules.edit(gravity(target), new ServerPayloadContext(guest)) || WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE) != target) {
                return "Server übernimmt die Änderung eines Operators nicht";
            }
            WorldRules.edit(gravity(before), new ServerPayloadContext(guest));
            if (WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE) != before) {
                return "Schwerkraft nach dem Test nicht zurückgesetzt";
            }
            if (!WorldRules.preset(new WorldRulePresetC2S(other), new ServerPayloadContext(guest)) || !other.matches(WorldRules.values(server))) {
                return "Server übernimmt die Voreinstellung " + other.id() + " eines Operators nicht: " + WorldRules.values(server);
            }
            if (WorldRules.preset(new WorldRulePresetC2S("gibt_es_nicht"), new ServerPayloadContext(guest)) || !other.matches(WorldRules.values(server))) {
                return "unbekannte Voreinstellung angenommen";
            }
        } finally {
            server.getPlayerList().deop(guest.nameAndId());
            WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, before);
            restore(server, rulesBefore);
        }
        return null;
    }

    public static @Nullable String checkPermissionMod(MinecraftServer server) {
        ServerPlayer admin = guest(server);
        if (server.getPlayerList().isOp(admin.nameAndId())) {
            return "Gast steht schon in der Operator-Liste";
        }
        CommandNode<CommandSourceStack> gamerule = server.getCommands().getDispatcher().getRoot().getChild("gamerule");
        if (gamerule == null) {
            return "Befehl /gamerule fehlt";
        }
        Predicate<CommandSourceStack> original = gamerule.getRequirement();
        Predicate<CommandSourceStack> granted = source -> isGuest(source) || original.test(source);
        int before = WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE);
        int target = before == 150 ? 120 : 150;
        RuleValues rulesBefore = WorldRules.values(server);
        RulePreset other = RulePreset.match(rulesBefore).orElse(null) == RulePreset.ARCADE ? RulePreset.TRAINING : RulePreset.ARCADE;
        try {
            String without = refused(server, admin, before, target, rulesBefore, other, "ohne /gamerule-Recht");
            if (without != null) {
                return without;
            }
            requirement(gamerule, granted);
            if (server.getPlayerList().isOp(admin.nameAndId())) {
                return "Admin steht nach der Freigabe in der Operator-Liste";
            }
            if (!WorldRules.mayEdit(admin) || !WorldRules.snapshotFor(admin).editable()) {
                return "Admin ohne Operator mit /gamerule-Recht sieht die Welt-Regeln nur lesbar";
            }
            if (!WorldRules.edit(gravity(target), new ServerPayloadContext(admin)) || WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE) != target) {
                return "Server übernimmt die Änderung eines Admins mit /gamerule-Recht nicht";
            }
            if (viaCommand(server, admin, before) != before) {
                return "Admin mit /gamerule-Recht setzt die Regel per Befehl nicht zurück";
            }
            if (!WorldRules.preset(new WorldRulePresetC2S(other), new ServerPayloadContext(admin)) || !other.matches(WorldRules.values(server))) {
                return "Server übernimmt die Voreinstellung " + other.id() + " eines Admins mit /gamerule-Recht nicht";
            }
            restore(server, rulesBefore);
            requirement(gamerule, original);
            return refused(server, admin, before, target, rulesBefore, other, "nach dem Entzug des /gamerule-Rechts");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "Rechte-Mod nicht simulierbar: " + e;
        } finally {
            try {
                requirement(gamerule, original);
            } catch (ReflectiveOperationException | RuntimeException e) {
                Constants.LOG.error("[Server-Rauchtest] /gamerule-Recht nicht zurückgesetzt", e);
            }
            restore(server, rulesBefore);
        }
    }

    private static @Nullable String refused(MinecraftServer server, ServerPlayer player, int before, int target, RuleValues rulesBefore,
            RulePreset other, String when) {
        if (WorldRules.mayEdit(player) || WorldRules.snapshotFor(player).editable()) {
            return "Admin " + when + " darf Welt-Regeln ändern";
        }
        if (WorldRules.edit(gravity(target), new ServerPayloadContext(player)) || WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE) != before) {
            return "Server übernimmt die Änderung eines Admins " + when;
        }
        if (WorldRules.preset(new WorldRulePresetC2S(other), new ServerPayloadContext(player)) || !WorldRules.values(server).equals(rulesBefore)) {
            return "Server übernimmt die Voreinstellung eines Admins " + when;
        }
        if (viaCommand(server, player, target) != before) {
            return "Admin " + when + " setzt die Regel per /gamerule";
        }
        return null;
    }

    private static boolean isGuest(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null && GUEST.equals(player.getUUID());
    }

    private static void requirement(CommandNode<CommandSourceStack> node, Predicate<CommandSourceStack> requirement) throws ReflectiveOperationException {
        Field field = CommandNode.class.getDeclaredField("requirement");
        field.setAccessible(true);
        field.set(node, requirement);
    }

    private static int viaCommand(MinecraftServer server, ServerPlayer player, int value) {
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "gamerule " + WorldRuleSpecs.GRAVITY_SCALE.id() + " " + value);
        return WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE);
    }

    private static void restore(MinecraftServer server, RuleValues values) {
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            WorldRules.set(server, spec, values.get(spec));
        }
    }
}
