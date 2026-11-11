package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.MigrationValues;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.freecam.FreecamPreset;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.freecam.client.FreecamClient;
import de.lardtom.propwash.freecam.client.FreecamConfig;
import de.lardtom.propwash.freecam.client.FreecamSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

final class DrehweltFreecam {
    private static final String FUTURE = "future";
    private static final int ARRIVE_TICKS = 200;
    private static final int SETTLE_TICKS = 20;
    private static final int FIVE_TICKS = 80;
    private static final int WHOOP_TICKS = 30;
    private static final double MIN_FLOWN = 2.0;
    private static final float PITCH = 0.25F;

    private DrehweltFreecam() {
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    private static float hover(@Nullable DroneSim sim) {
        float hover = sim == null ? 0.3F : (float) sim.telemetry().hoverThrottleStick;
        return Float.isFinite(hover) && hover > 0.05F ? hover : 0.3F;
    }

    private static void fly(FreecamSession session, boolean forward) {
        DroneSim sim = session.sim();
        if (sim == null || !sim.telemetry().armed) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
        } else {
            session.setTestInput(new StickPositions(0.0F, forward ? PITCH : 0.0F, 0.0F, hover(sim)), true, StabMode.ANGLE);
        }
    }

    private static void cleanup(Minecraft mc, Map<String, Object> state) {
        FreecamSession session = FreecamSession.INSTANCE;
        session.clearTestInput();
        if (FreecamSession.active()) {
            session.end(mc, FreecamRules.End.KEY);
        }
        if (state.get("before") instanceof FreecamPreset before) {
            session.setPreset(before);
        }
    }

    private static StepResult fail(Minecraft mc, Map<String, Object> state, String reason) {
        cleanup(mc, state);
        return StepResult.fail(reason);
    }

    static StepResult towerStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        DrehweltSelfTests.Built canyon = DrehweltSelfTests.find("R1");
        if (canyon == null || !canyon.complete()) {
            return StepResult.fail("Schlucht mit Turm nicht gebaut");
        }
        FreecamSession session = FreecamSession.INSTANCE;
        int phase = (int) state.getOrDefault("phase", 0);
        int since = (int) state.getOrDefault("since", 0);
        switch (phase) {
            case 0 -> {
                if (ctx.server() == null) {
                    return StepResult.fail("keine Drehwelt geöffnet");
                }
                AtomicReference<Vec3> stand = new AtomicReference<>();
                state.put("stand", stand);
                state.put("before", FreecamConfig.get().preset());
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    DrehweltSelfTests.Site site = DrehweltSelfTests.locate(player, canyon.plot());
                    BlockPos pos = DrehweltSelfTests.towerStand(site);
                    player.teleportTo(site.level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), -90.0F, 15.0F, true);
                    stand.set(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
                }));
                state.put("phase", 1);
                state.put("since", ctx.tick());
                return StepResult.pending();
            }
            case 1 -> {
                StepResult waiting = DrehweltSelfTests.future(ctx);
                if (waiting != null) {
                    return waiting;
                }
                Vec3 stand = DrehweltFreecam.<AtomicReference<Vec3>>cast(state.get("stand")).get();
                boolean there = mc.player != null && stand != null && mc.player.position().distanceTo(stand) < 1.5;
                if (!there) {
                    state.remove("arrived");
                    return ctx.tick() - since > ARRIVE_TICKS ? fail(mc, state, "Spieler nicht auf dem Turm: " + (mc.player == null ? "kein Spieler"
                            : mc.player.position() + " statt " + stand)) : StepResult.pending();
                }
                state.putIfAbsent("arrived", ctx.tick());
                if (ctx.tick() - (int) state.get("arrived") < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                session.setPreset(FreecamPreset.FIVE);
                FreecamRules.Denial denial = session.tryStart(mc);
                if (!FreecamSession.active()) {
                    return fail(mc, state, "Freecam startet auf dem Turm nicht: " + denial);
                }
                Vector3d from = session.position(new Vector3d());
                state.put("from", from);
                state.put("osd", FreecamClient.osdName(session.preset()).getString());
                state.put("phase", 2);
                state.put("since", ctx.tick());
                ctx.log(String.format(Locale.ROOT, "F6 auf dem Turm bei %.1f %.1f %.1f: %s, OSD-Name „%s“", from.x, from.y, from.z, session.preset(),
                        state.get("osd")));
                return StepResult.pending();
            }
            case 2, 3 -> {
                if (!FreecamSession.active()) {
                    return fail(mc, state, "Freecam endete im Flug: " + session.lastEnd());
                }
                fly(session, true);
                int flown = ctx.tick() - since;
                if (phase == 2 && flown >= FIVE_TICKS) {
                    double distance = session.position(new Vector3d()).distance(DrehweltFreecam.<Vector3d>cast(state.get("from")));
                    state.put("five", distance);
                    session.setPreset(FreecamPreset.WHOOP);
                    state.put("phase", 3);
                    state.put("since", ctx.tick());
                    return StepResult.pending();
                }
                if (phase == 3 && flown >= WHOOP_TICKS) {
                    FreecamPreset now = session.preset();
                    Vector3d at = session.position(new Vector3d());
                    double five = (double) state.get("five");
                    String whoop = FreecamClient.osdName(now).getString();
                    String osd = (String) state.get("osd");
                    cleanup(mc, state);
                    if (now != FreecamPreset.WHOOP) {
                        return StepResult.fail("Wechsel auf Whoop im Flug nicht übernommen: " + now);
                    }
                    if (five < MIN_FLOWN) {
                        return StepResult.fail(String.format(Locale.ROOT, "Freecam als 5 Zoll nur %.1f Blöcke geflogen", five));
                    }
                    if (!osd.toUpperCase(Locale.ROOT).contains("FREECAM") || !whoop.toUpperCase(Locale.ROOT).contains("FREECAM")) {
                        return StepResult.fail("OSD-Name ohne FREECAM: „" + osd + "“, „" + whoop + "“");
                    }
                    ctx.log(String.format(Locale.ROOT, "als 5 Zoll %.1f Blöcke in %.1f s geflogen, im Flug auf Whoop gewechselt („%s“), zuletzt bei %.1f %.1f %.1f,"
                            + " Freecam mit F6 beendet", five, FIVE_TICKS / 20.0, whoop, at.x, at.y, at.z));
                    return StepResult.pass();
                }
                return StepResult.pending();
            }
            default -> {
                return fail(mc, state, "unbekannte Phase " + phase);
            }
        }
    }

    static StepResult reopenStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            if (mc.level != null) {
                return StepResult.fail("noch in einer Welt");
            }
            mc.createWorldOpenFlows().openWorld(DrehweltSelfTests.WORLD, () -> mc.gui.setScreen(new TitleScreen()));
            return StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        if (screen != null && ctx.tick() % 20 == 0) {
            if (!press(screen, Component.translatable("selectWorld.backupJoinSkipButton").getString()) && screen instanceof ConfirmScreen) {
                press(screen, CommonComponents.GUI_YES.getString());
            }
        }
        if (mc.player != null && mc.level != null && screen == null && mc.getSingleplayerServer() != null) {
            state.putIfAbsent("ready", ctx.tick());
            if (ctx.tick() - (int) state.get("ready") > 60) {
                ctx.log("saves/" + DrehweltSelfTests.WORLD + " geöffnet");
                return StepResult.pass();
            }
        }
        return StepResult.pending();
    }

    private static boolean press(Screen screen, String text) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && text.equals(button.getMessage().getString())) {
                button.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                return true;
            }
        }
        return false;
    }

    static StepResult savedStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (ctx.server() == null) {
                return StepResult.fail("Drehwelt nicht geöffnet");
            }
            AtomicReference<String> result = new AtomicReference<>();
            state.put("result", result);
            state.put(FUTURE, ctx.onServer(server -> {
                List<String> found = new ArrayList<>();
                List<String> wrong = new ArrayList<>();
                int rule = WorldRules.get(server, WorldRuleSpecs.ALLOW_FREECAM);
                if (rule != 1) {
                    wrong.add(MigrationValues.FREECAM_RULE + "=" + rule);
                }
                CourseRegistry registry = CourseRegistry.get();
                for (String plot : List.of("R11", "T")) {
                    DrehweltSelfTests.Built built = DrehweltSelfTests.find(plot);
                    Integer id = DrehweltSelfTests.COURSES.get(plot);
                    CourseRegistry.Course course = registry == null || id == null ? null : registry.course(id);
                    int gates = course == null ? -1 : course.gates.size();
                    if (built == null || gates < 1 || plot.equals("R11") && gates != built.gates()) {
                        wrong.add(plot + " Kurs " + id + " mit " + gates + " Gates");
                    } else {
                        found.add(plot + " Kurs " + id + " mit " + gates + " Gates");
                    }
                }
                result.set(wrong.isEmpty() ? "ok " + MigrationValues.FREECAM_RULE + "=" + rule + ", " + String.join(", ", found) : String.join("; ", wrong));
            }));
            return StepResult.pending();
        }
        StepResult waiting = DrehweltSelfTests.future(ctx);
        if (waiting != null) {
            return waiting;
        }
        String result = DrehweltFreecam.<AtomicReference<String>>cast(state.get("result")).get();
        if (result == null || !result.startsWith("ok ")) {
            return StepResult.fail("gespeicherte Drehwelt: " + result);
        }
        FreecamRules.Denial denial = FreecamSession.denialFromMenu(mc);
        if (!ClientWorldRules.INSTANCE.on(WorldRuleSpecs.ALLOW_FREECAM) || !denial.allowed()) {
            return ctx.tick() > 200 ? StepResult.fail("Client nach dem Neuladen: Freecam " + denial) : StepResult.pending();
        }
        ctx.log(result.substring(3) + ", Client: Freecam darf starten");
        return StepResult.pass();
    }
}
