package de.lardtom.propwash.server;

import com.mojang.authlib.GameProfile;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

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

    private static void restore(MinecraftServer server, RuleValues values) {
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            WorldRules.set(server, spec, values.get(spec));
        }
    }
}
