package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.dev.ZoneJoinCheck;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.AssistWords;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.client.AssistText;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneEditC2S;
import de.lardtom.propwash.zone.ZoneShape;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public final class ZoneJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 75;
    private static final String START = "zones_start";
    private static final String LINES = "zones_lines";
    private static final String STAGE = "zones_stage";
    private static final String DENIED = "Beitrittstest Gast";

    private ZoneJoinProbe() {
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static @Nullable Zone find(String name) {
        for (Zone zone : ClientZones.INSTANCE.zones()) {
            if (zone.name().equals(name)) {
                return zone;
            }
        }
        return null;
    }

    private static double gravity() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        return sim == null ? Double.NaN : sim.tuning().gravityScale;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        if (tick - start > TIMEOUT_TICKS) {
            line(state, "zones timeout at stage " + stage);
            return (String) state.get(LINES);
        }
        PilotSession session = PilotSession.INSTANCE;
        switch (stage) {
            case 0 -> {
                Zone zeroG = find(ZoneJoinCheck.FLOAT);
                Zone field = find(ZoneJoinCheck.FIELD);
                if (zeroG == null || field == null) {
                    return null;
                }
                line(state, "zones_synced=" + ClientZones.INSTANCE.zones().size());
                line(state, "zones_editable=" + ClientZones.INSTANCE.editable());
                ClientNet.sendToServer(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, Zone.create(ClientZones.INSTANCE.syncedDimension(), DENIED, ZoneShape.BOX,
                        ZoneBox.blocks(0, 0, 0, 2, 2, 2))));
                state.put("zones_sent", tick);
                state.put(STAGE, 1);
                return null;
            }
            case 1 -> {
                if (tick - (int) state.get("zones_sent") < 40) {
                    return null;
                }
                line(state, find(DENIED) == null ? "zones_guest_edit=denied" : "zones_guest_edit=accepted");
                state.put(STAGE, 2);
                return null;
            }
            case 2 -> {
                if (!session.isActive() || session.sim() == null || gravity() != 0.0) {
                    return null;
                }
                line(state, "zone_gravity=0");
                line(state, "zone_race_legal_assist=" + (session.physicsModified() ? "flagged" : "clean"));
                state.put(STAGE, 3);
                return null;
            }
            case 3 -> {
                if (!session.physicsModified()) {
                    return null;
                }
                QuadcopterEntity drone = session.drone();
                if (drone == null) {
                    line(state, "zone_assist=no drone");
                    return (String) state.get(LINES);
                }
                AssistReasons reasons = AssistText.evaluate(session, drone);
                String text = AssistWords.describe(reasons).getString();
                line(state, "zone_assist=" + (reasons.has(LapAssist.ZONE_PHYSICS) && text.contains(ZoneJoinCheck.FLOAT) ? "named" : "unnamed:" + text));
                state.put(STAGE, 4);
                return null;
            }
            case 4 -> {
                if (find(ZoneJoinCheck.FLOAT) != null || gravity() != 1.0) {
                    return null;
                }
                line(state, String.format(Locale.ROOT, "zone_left=world gravity %.2f", gravity()));
                line(state, "zone_after_assist=" + (session.physicsModified() ? "flagged" : "clean"));
                return (String) state.get(LINES);
            }
            default -> {
                return (String) state.get(LINES);
            }
        }
    }
}
