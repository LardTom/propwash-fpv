package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.client.WorldRulesTab;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public final class CovFreecamJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 150;
    public static final String ACTIVE = "cov_freecam_active";
    private static final String PROBE = "cov_freecam_probe";
    private static final int ARM_TICKS = 100;
    private static final int FLY_TICKS = 60;
    private static final int OFFLINE_TICKS = 20;
    private static final int SETTLE_TICKS = 40;
    private static final int WALK_TICKS = 20;

    private CovFreecamJoinProbe() {
    }

    private static final class Probe {
        final int start;
        int stage;
        int mark = -1;
        int flying = -1;
        final FreecamSelfTests.Run arming = new FreecamSelfTests.Run();
        String button = "unknown";
        String ruleJump = "unknown";
        String worldRules = "unknown";
        String reason = "unknown";
        String operator = "unknown";
        String started = "no";
        String armedInFlight = "unknown";
        String endedOffline = "unknown";
        String afterRejoin = "unknown";
        String camera = "unknown";
        String moves = "unknown";
        String problem = "";
        double walked;
        Vec3 from = Vec3.ZERO;
        @Nullable String report;

        Probe(int start) {
            this.start = start;
        }
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        Probe probe = (Probe) state.get(PROBE);
        if (probe == null) {
            state.put(PROBE, new Probe(tick));
            state.put(ACTIVE, true);
            Constants.LOG.info("[Beitrittstest] Freecam-Abdeckung: Probe startet");
            return null;
        }
        return probe.report;
    }

    public static void whileActive(Map<String, Object> state, int tick, Minecraft mc) {
        Probe p = (Probe) state.get(PROBE);
        if (p == null) {
            state.remove(ACTIVE);
            return;
        }
        if (tick - p.start > TIMEOUT_TICKS) {
            finish(state, p, "timeout at stage " + p.stage);
            return;
        }
        LocalPlayer player = mc.player;
        boolean inWorld = mc.level != null && player != null;
        switch (p.stage) {
            case 0 -> overview(p, tick, mc);
            case 1 -> {
                if (!inWorld || !ClientWorldRules.INSTANCE.on(WorldRuleSpecs.ALLOW_FREECAM)) {
                    return;
                }
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                    return;
                }
                FreecamSession.INSTANCE.toggle(mc);
                p.started = FreecamSession.active() ? "yes" : "no";
                Constants.LOG.info("[Beitrittstest] Freecam-Abdeckung: Regel an, Start {}", FreecamSession.active() ? "ja" : "nein ("
                        + FreecamSession.INSTANCE.lastDenial() + ")");
                if (!FreecamSession.active()) {
                    finish(state, p, "start denied " + FreecamSession.INSTANCE.lastDenial());
                    return;
                }
                if (!CovFreecamSelfTests.stall()) {
                    finish(state, p, "hitch interrupted");
                    return;
                }
                p.stage = 2;
                p.mark = tick;
            }
            case 2 -> {
                if (!FreecamSession.active()) {
                    finish(state, p, "freecam ended early " + FreecamSession.INSTANCE.lastEnd());
                    return;
                }
                if (p.flying < 0) {
                    Vector3d at = FreecamSession.INSTANCE.position(new Vector3d());
                    if (FreecamSelfTests.armAt(p.arming, at.x, at.y, at.z, player != null ? player.getYRot() : 0.0F)) {
                        p.flying = tick;
                    }
                }
                if (p.flying >= 0) {
                    FreecamSession.INSTANCE.setTestInput(new StickPositions(0.0F, tick - p.flying > 15 ? 0.2F : 0.0F, 0.0F, FreecamSelfTests.hover()), true,
                            StabMode.ANGLE);
                }
                if (p.flying >= 0 ? tick - p.flying < FLY_TICKS : tick - p.mark < ARM_TICKS) {
                    return;
                }
                p.armedInFlight = FreecamSession.INSTANCE.armed() ? "yes" : "no";
                FreecamSession.INSTANCE.clearTestInput();
                Constants.LOG.info("[Beitrittstest] Freecam-Abdeckung: trenne in der Freecam (scharf {}), {} Starts", FreecamSession.INSTANCE.armed() ? "ja"
                        : "nein, Blocker " + FreecamSelfTests.armBlockers(), FreecamSession.INSTANCE.starts());
                mc.disconnectFromWorld(Component.literal("Propwash join test: rejoin"));
                p.stage = 3;
                p.mark = tick;
            }
            case 3 -> {
                if (inWorld) {
                    p.mark = tick;
                    return;
                }
                if (FreecamSession.active()) {
                    p.endedOffline = "still_active";
                }
                if (tick - p.mark < OFFLINE_TICKS) {
                    return;
                }
                if (!"still_active".equals(p.endedOffline)) {
                    p.endedOffline = FreecamSession.INSTANCE.lastEnd().name().toLowerCase(Locale.ROOT);
                }
                String target = JoinTestEnv.target();
                if (target == null) {
                    finish(state, p, "no target");
                    return;
                }
                Constants.LOG.info("[Beitrittstest] Freecam-Abdeckung: getrennt, Freecam {}, Ende {}; neuer Beitritt zu {}",
                        FreecamSession.active() ? "aktiv" : "aus", FreecamSession.INSTANCE.lastEnd(), target);
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(target), new ServerData("Propwash join test", target,
                        ServerData.Type.OTHER), false, null);
                p.stage = 4;
                p.mark = tick;
            }
            case 4 -> {
                boolean joined = inWorld && mc.gui.screen() == null;
                if (!joined) {
                    p.mark = tick;
                    return;
                }
                if (tick - p.mark < SETTLE_TICKS) {
                    return;
                }
                p.afterRejoin = FreecamSession.active() ? "active" : "inactive";
                boolean camera = mc.getCameraEntity() == player && !FpvView.active() && !FpvView.isPilotBody(player);
                p.camera = camera ? "player" : "other";
                p.from = player.position();
                mc.options.keyUp.setDown(true);
                p.stage = 5;
                p.mark = tick;
            }
            case 5, 6 -> {
                if (!inWorld) {
                    mc.options.keyUp.setDown(false);
                    mc.options.keyDown.setDown(false);
                    finish(state, p, "left world while walking");
                    return;
                }
                if (tick - p.mark < WALK_TICKS) {
                    return;
                }
                mc.options.keyUp.setDown(false);
                mc.options.keyDown.setDown(false);
                Vec3 now = player.position();
                p.walked = Math.max(p.walked, Math.hypot(now.x - p.from.x, now.z - p.from.z));
                if (p.walked < 0.3 && p.stage == 5) {
                    p.from = now;
                    mc.options.keyDown.setDown(true);
                    p.stage = 6;
                    p.mark = tick;
                    return;
                }
                p.moves = p.walked >= 0.3 ? "yes" : "no";
                Constants.LOG.info("[Beitrittstest] Freecam-Abdeckung: nach dem neuen Beitritt Freecam {}, Kamera {}, Spieler läuft {} Blöcke", p.afterRejoin,
                        p.camera, String.format(Locale.ROOT, "%.2f", p.walked));
                finish(state, p, "");
            }
            default -> finish(state, p, "");
        }
    }

    private static void overview(Probe p, int tick, Minecraft mc) {
        ClientWorldRules rules = ClientWorldRules.INSTANCE;
        if (!rules.known() || mc.player == null) {
            return;
        }
        if (p.mark < 0) {
            if (rules.on(WorldRuleSpecs.ALLOW_FREECAM)) {
                return;
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ConfigTabs.open(null, OverviewTab.ID);
            p.mark = tick;
            return;
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || screen.activeTab() == null || !OverviewTab.ID.equals(screen.activeTab().id())
                || tick - p.mark < 6) {
            return;
        }
        FlatButton start = screen.freecamStartButton();
        FlatButton rule = screen.freecamRuleButton();
        p.button = start == null ? "missing" : !start.visible ? "hidden" : start.active ? "enabled" : "disabled";
        p.ruleJump = rule != null && rule.visible ? "present" : "absent";
        String denied = Component.translatable("propwash.freecam.denied").getString().replace(" ", "");
        p.reason = String.join(" ", screen.statusLines()).replace(" ", "").contains(denied) ? "shown" : "missing";
        p.worldRules = "absent";
        for (ConfigTab tab : screen.tabs()) {
            if (WorldRulesTab.ID.equals(tab.id())) {
                p.worldRules = tab.readOnly() ? "readonly" : "editable";
            }
        }
        p.operator = rules.editable() ? "yes" : "no";
        Constants.LOG.info("[Beitrittstest] Freecam-Abdeckung: Übersicht ohne Recht: Knopf {}, Sprung zur Regel {}, Grund {}, Welt-Regeln {}, darf ändern {}",
                p.button, p.ruleJump, p.reason, p.worldRules, p.operator);
        screen.discardAndClose();
        p.stage = 1;
        p.mark = tick;
    }

    private static void finish(Map<String, Object> state, Probe p, String problem) {
        FreecamSession.INSTANCE.clearTestInput();
        p.problem = problem;
        StringBuilder out = new StringBuilder();
        if (!problem.isEmpty()) {
            out.append("cov_freecam ").append(problem).append('\n');
        }
        out.append("freecam_button=").append(p.button).append('\n');
        out.append("freecam_rule_jump=").append(p.ruleJump).append('\n');
        out.append("freecam_reason=").append(p.reason).append('\n');
        out.append("world_rules=").append(p.worldRules).append('\n');
        out.append("operator=").append(p.operator).append('\n');
        out.append("freecam_started=").append(p.started).append('\n');
        out.append("freecam_armed_in_flight=").append(p.armedInFlight).append('\n');
        out.append("freecam_end_on_disconnect=").append(p.endedOffline).append('\n');
        out.append("freecam_after_rejoin=").append(p.afterRejoin).append('\n');
        out.append("camera_after_rejoin=").append(p.camera).append('\n');
        out.append("player_moves=").append(p.moves).append('\n');
        out.append(String.format(Locale.ROOT, "player_walked=%.2f%n", p.walked));
        p.report = out.toString();
        state.remove(ACTIVE);
    }
}
