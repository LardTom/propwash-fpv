package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.dev.ZonesOpenJoinCheck;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneDraft;
import de.lardtom.propwash.zone.ZoneEditC2S;
import de.lardtom.propwash.zone.ZoneJumpC2S;
import de.lardtom.propwash.zone.ZoneOwner;
import de.lardtom.propwash.zone.ZoneShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class ZonesOpenJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 90;
    public static final int GRAVITY = 50;
    private static final String START = "zones_open_start";
    private static final String LINES = "zones_open_lines";
    private static final String STAGE = "zones_open_stage";
    private static final String OTHER_SEEN = "zones_open_other_seen";
    private static final String JUMP_FROM = "zones_open_jump_from";

    private ZonesOpenJoinProbe() {
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static @Nullable Zone stored() {
        for (Zone zone : ClientZones.INSTANCE.stored()) {
            if (zone.name().equals(ZonesOpenJoinCheck.ZONE)) {
                return zone;
            }
        }
        return null;
    }

    private static @Nullable Zone effective(int id) {
        for (Zone zone : ClientZones.INSTANCE.zones()) {
            if (zone.id() == id) {
                return zone;
            }
        }
        return null;
    }

    private static boolean named(String name) {
        for (Zone zone : ClientZones.INSTANCE.stored()) {
            if (zone.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        if (tick - start > TIMEOUT_TICKS) {
            line(state, "zones_open timeout at stage " + stage);
            return (String) state.get(LINES);
        }
        LocalPlayer player = mc.player;
        if (player == null || !ClientZones.INSTANCE.known()) {
            return null;
        }
        Zone zone = stored();
        if (zone != null && zone.priority() == ZonesOpenJoinCheck.OTHER_PRIORITY) {
            state.put(OTHER_SEEN, true);
        }
        switch (stage) {
            case 0 -> {
                line(state, "zones_open_creatable=" + ClientZones.INSTANCE.creatable());
                line(state, "zones_open_admin=" + ClientZones.INSTANCE.editable());
                if (!ClientZones.INSTANCE.creatable()) {
                    return (String) state.get(LINES);
                }
                Zone fresh = Zone.create(ClientZones.INSTANCE.syncedDimension(), ZonesOpenJoinCheck.ZONE, ZoneShape.BOX,
                                ZoneBox.around(Math.floor(player.getX()) + 0.5, Math.floor(player.getY()) - 2.0, Math.floor(player.getZ()) + 0.5, 12.0, 10.0, 12.0))
                        .withOverride(WorldRuleSpecs.GRAVITY_SCALE, GRAVITY).withOwner(new ZoneOwner(player.getUUID(), player.getGameProfile().name()));
                ClientNet.sendToServer(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, fresh));
                state.put(STAGE, 1);
                return null;
            }
            case 1 -> {
                if (zone == null) {
                    return null;
                }
                line(state, "zones_open_owned=" + (zone.ownedBy(player.getUUID()) ? "self" : "other:" + zone.owner()));
                ClientNet.sendToServer(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, zone.withRaceLegal(true)));
                state.put(STAGE, 2);
                return null;
            }
            case 2 -> {
                if (zone == null || !zone.raceLegal()) {
                    return null;
                }
                line(state, "zones_open_race_legal=self");
                state.put(STAGE, 3);
                return null;
            }
            case 3 -> {
                if (zone == null || zone.priority() != ZonesOpenJoinCheck.OPERATOR_PRIORITY) {
                    return null;
                }
                line(state, "zones_open_other=" + (state.containsKey(OTHER_SEEN) ? "accepted" : "denied"));
                line(state, "zones_open_operator=" + (zone.ownedBy(player.getUUID()) && zone.raceLegal() ? "changed" : "owner_lost:" + zone));
                state.put(STAGE, 31);
                return null;
            }
            case 31 -> {
                boolean synced = named(ZonesOpenJoinCheck.ADMIN_ZONE) && named(ZonesOpenJoinCheck.OTHER_ZONE);
                if (!synced) {
                    return null;
                }
                List<String> listed = ClientZones.INSTANCE.listed().stream().map(Zone::name).toList();
                line(state, "zones_open_listed=" + (listed.equals(List.of(ZonesOpenJoinCheck.ZONE)) ? "own" : "listed:" + listed));
                line(state, "zones_open_jump_button=" + (ClientZones.mayJump() ? "shown" : "hidden"));
                state.put(JUMP_FROM, new double[]{player.getX(), player.getY(), player.getZ()});
                ClientNet.sendToServer(new ZoneJumpC2S(zone.id()));
                state.put(STAGE, 4);
                return null;
            }
            case 4 -> {
                if (zone == null || ClientZones.INSTANCE.release().released(WorldRuleSpecs.GRAVITY_SCALE)) {
                    return null;
                }
                Zone applied = effective(zone.id());
                boolean kept = zone.value(WorldRuleSpecs.GRAVITY_SCALE, -1) == GRAVITY && applied != null
                        && !applied.overrides(WorldRuleSpecs.GRAVITY_SCALE);
                ZoneDraft draft = new ZoneDraft(zone, ClientZones.INSTANCE.release());
                boolean greyed = draft.rows().contains(new ZoneDraft.Row(ZoneDraft.Kind.LOCKED, WorldRuleSpecs.GRAVITY_SCALE))
                        && !draft.rows().contains(new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.GRAVITY_SCALE));
                double[] from = (double[]) state.get(JUMP_FROM);
                double moved = from == null ? -1.0 : Math.sqrt(player.distanceToSqr(from[0], from[1], from[2]));
                line(state, "zones_open_jump=" + (moved >= 0.0 && moved < 0.5 ? "denied" : "moved:" + moved));
                line(state, "zones_open_locked=" + (kept ? "kept" : "lost:" + zone + " / " + applied));
                line(state, "zones_open_greyed=" + greyed);
                ClientNet.sendToServer(new ZoneEditC2S(ZoneEditC2S.Action.DELETE, zone));
                state.put(STAGE, 5);
                return null;
            }
            case 5 -> {
                if (zone != null) {
                    return null;
                }
                line(state, "zones_open_deleted=own");
                return (String) state.get(LINES);
            }
            default -> {
                return (String) state.get(LINES);
            }
        }
    }
}
