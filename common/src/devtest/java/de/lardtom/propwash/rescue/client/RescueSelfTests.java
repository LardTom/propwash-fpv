package de.lardtom.propwash.rescue.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.rescue.RescueAutopilot;
import de.lardtom.propwash.rescue.RescuePhase;
import de.lardtom.propwash.rescue.RescueSettings;
import de.lardtom.propwash.rescue.RescueWorld;
import de.lardtom.propwash.rescue.VoxelFlight;
import de.lardtom.propwash.rescue.WorldProbe;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.EventQueue;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.SimEventType;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RescueSelfTests {
    private static final double FPS = 60.0;
    private static final long FRAME_NANOS = Math.round(1e9 / FPS);
    private static final double START_DISTANCE = 40.0;
    private static final double START_HEIGHT = 5.0;
    private static final double MAX_FLIGHT_SECONDS = 120.0;
    private static final double LANDING_TOLERANCE = 1.5;
    private static final int TAB_SETTINGS = 11;
    private static final int NARROW_WIDTH = 320;

    private RescueSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("rescue: Homepoint, Zustand, Tasten, Konfigurator", 20, ctx -> stateStep());
        SelfTest.clientStep("rescue: Konfigurator-Tab bei 427x240 und 320x240", 200, RescueSelfTests::tabLayoutStep);
        SelfTest.clientStep("rescue: Heimflug aus 40 m in der Flachwelt", 600, ctx -> flightStep(ctx.tick(), ctx.state()));
        AutonomousRescueSelfTests.register();
        HomePointSelfTests.register();
        HomeResetSelfTests.register();
    }

    private static StepResult tabLayoutStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ConfigTabs.open(null, RescueConfigTab.ID);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return ctx.tick() < 100 ? StepResult.pending() : StepResult.fail("Konfigurator öffnet sich nicht");
        }
        String problem;
        try {
            problem = checkTab(screen);
        } finally {
            screen.discardAndClose();
        }
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static String checkTab(ConfiguratorScreen screen) {
        if (!(screen.activeTab() instanceof RescueConfigTab tab)) {
            return "Rescue-Tab ist nach dem Öffnen nicht aktiv";
        }
        screen.resize(ConfiguratorScreen.MIN_WIDTH, ConfiguratorScreen.MIN_HEIGHT);
        String problem = checkSize(screen, tab, ConfiguratorScreen.MIN_WIDTH + "x" + ConfiguratorScreen.MIN_HEIGHT);
        if (problem != null) {
            return problem;
        }
        if (tab.maxScroll() != 0 || tab.columns() != 2) {
            return "Rescue-Tab passt bei 427x240 nicht ohne Scrollen: Spalten " + tab.columns() + ", Scroll " + tab.maxScroll();
        }
        screen.resize(NARROW_WIDTH, ConfiguratorScreen.MIN_HEIGHT);
        problem = checkSize(screen, tab, NARROW_WIDTH + "x" + ConfiguratorScreen.MIN_HEIGHT);
        if (problem != null) {
            return problem;
        }
        if (tab.maxScroll() > 0) {
            tab.scrollTo(0);
            AbstractWidget first = tab.widgets().getFirst();
            if (!(first instanceof RescueCycleButton<?> button)) {
                return "erste Einstellung ist kein Umschalter";
            }
            Object before = button.value();
            double cx = first.getX() + first.getWidth() / 2.0;
            double cy = first.getY() + first.getHeight() / 2.0;
            screen.mouseScrolled(cx, cy, 0.0, -1.0);
            if (tab.scroll() != 1 || !before.equals(button.value())) {
                return "Mausrad über einem Umschalter scrollt nicht (Scroll " + tab.scroll() + ", Wert " + before + " -> " + button.value() + ")";
            }
            screen.mouseScrolled(cx, cy, 0.0, 1.0);
            if (tab.scroll() != 0) {
                return "Mausrad scrollt nicht zurück (Scroll " + tab.scroll() + ")";
            }
        }
        String error = screen.tabErrors().get(RescueConfigTab.ID);
        return error == null ? null : "Rescue-Tab meldet Fehler: " + error;
    }

    private static String checkSize(ConfiguratorScreen screen, RescueConfigTab tab, String size) {
        ScreenRectangle area = screen.contentArea();
        List<AbstractWidget> widgets = tab.widgets();
        if (widgets.size() != TAB_SETTINGS) {
            return "Rescue-Tab hat " + widgets.size() + " statt " + TAB_SETTINGS + " Einstellungen";
        }
        boolean[] seen = new boolean[widgets.size()];
        for (int scroll = 0; scroll <= tab.maxScroll(); scroll++) {
            tab.scrollTo(scroll);
            for (int i = 0; i < widgets.size(); i++) {
                AbstractWidget w = widgets.get(i);
                String name = size + " Scroll " + scroll + ": " + w.getMessage().getString();
                if (w.visible) {
                    seen[i] = true;
                    if (w.getX() < area.left() || w.getRight() > area.right() || w.getY() < area.top() || w.getBottom() > area.bottom()) {
                        return name + " liegt außerhalb des Inhaltsbereichs " + area + " bei " + w.getRectangle();
                    }
                } else if (w.isMouseOver(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0)) {
                    return name + " ist unsichtbar, aber anklickbar";
                }
            }
        }
        for (int i = 0; i < seen.length; i++) {
            if (!seen[i]) {
                return size + ": " + widgets.get(i).getMessage().getString() + " ist nie erreichbar";
            }
        }
        tab.scrollTo(tab.maxScroll());
        return tab.statusVisible() ? null : size + ": Homepoint- und Tastenzeile ist nie sichtbar";
    }

    private static StepResult stateStep() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return StepResult.pending();
        }
        RescueState state = RescueState.INSTANCE;
        if (state.active() || state.forceSelfLevel() || !state.commandedSticks().equals(StickPositions.IDLE)
                || !state.phaseLabel().getString().isEmpty()) {
            return StepResult.fail("RescueState ohne Pilot-Sitzung nicht inaktiv");
        }
        Vec3 target = player.position().add(3.0, 0.0, -2.0);
        RescueController.INSTANCE.setHome(target, mc.level.dimension(), true);
        Vec3 home = state.home();
        RescueController.INSTANCE.clearHome();
        if (home == null || home.distanceTo(target) > 1e-6) {
            return StepResult.fail("Homepoint nicht übernommen: " + home);
        }
        if (state.home() != null) {
            return StepResult.fail("Homepoint nicht gelöscht");
        }
        if (!ClientRegistrar.keyMappings().contains(RescueKeys.RESCUE) || !ClientRegistrar.keyMappings().contains(RescueKeys.SET_HOME)) {
            return StepResult.fail("Rescue-Tasten nicht registriert");
        }
        if (!RescueKeys.RESCUE.getDefaultKey().equals(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_J))) {
            return StepResult.fail("Rescue-Taste ist nicht J");
        }
        boolean tab = false;
        for (ConfigTab t : ConfigTabs.create()) {
            tab |= RescueConfigTab.ID.equals(t.id());
        }
        if (!tab) {
            return StepResult.fail("Konfigurator-Tab GPS Rescue fehlt");
        }
        return StepResult.check(RescueConfigs.CONFIG.isLoaded(), "rescue.json nicht geladen");
    }

    private static StepResult flightStep(int tick, Map<String, Object> state) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return StepResult.pending();
        }
        LevelRescueWorld world = (LevelRescueWorld) state.computeIfAbsent("world", k -> new LevelRescueWorld());
        world.bind(mc.level);
        world.forgetChunk();
        RescueSettings cfg = new RescueSettings();
        Flight flight = (Flight) state.get("flight");
        if (flight == null) {
            Vec3 feet = player.position();
            double startX = Math.floor(feet.x) + 0.5 + START_DISTANCE;
            double startZ = Math.floor(feet.z) + 0.5;
            if (world.surfaceY((int) Math.floor(startX), (int) Math.floor(startZ)) == RescueWorld.UNKNOWN_SURFACE) {
                return tick < 200 ? StepResult.pending() : StepResult.fail("Chunk 40 m neben dem Spieler nicht geladen");
            }
            double homeX = Math.floor(feet.x) + 0.5;
            double homeZ = Math.floor(feet.z) + 0.5;
            double homeGround = WorldProbe.groundBelow(world, homeX, feet.y + 0.5, homeZ, 0.2, 64);
            if (!Double.isFinite(homeGround)) {
                return StepResult.fail("Kein Boden unter dem Spieler");
            }
            RescueController.INSTANCE.setHome(new Vec3(homeX, homeGround, homeZ), mc.level.dimension(), true);
            Vec3 home = RescueState.INSTANCE.home();
            RescueController.INSTANCE.clearHome();
            if (home == null) {
                return StepResult.fail("Homepoint fehlt");
            }
            double startGround = WorldProbe.groundBelow(world, startX, feet.y + 20.0, startZ, 0.2, 64);
            double startBottom = (Double.isFinite(startGround) ? startGround : home.y) + START_HEIGHT;
            DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 11L);
            VoxelFlight voxel = new VoxelFlight(sim, world);
            voxel.place(startX, startBottom, startZ, Math.PI * 0.5);
            voxel.arm();
            RescueAutopilot autopilot = new RescueAutopilot();
            autopilot.start(sim, voxel.x(), voxel.bottom(), voxel.z(), home.x, home.y, home.z, world, cfg);
            flight = new Flight(voxel, autopilot, home);
            state.put("flight", flight);
        }
        for (int i = 0; i < (int) FPS; i++) {
            flight.autopilot.update(1.0 / FPS, flight.voxel.sim, flight.voxel.x(), flight.voxel.bottom(), flight.voxel.z(), world, cfg);
            flight.voxel.apply(flight.autopilot);
            flight.voxel.frame(FRAME_NANOS);
            EventQueue events = flight.voxel.sim.events();
            flight.crashes += events.count(SimEventType.CRASH);
            flight.seconds += 1.0 / FPS;
            if (flight.crashes > 0) {
                return StepResult.fail("Crash beim Heimflug: " + describe(flight));
            }
            if (!flight.voxel.sim.telemetry().armed) {
                double dx = flight.voxel.x() - flight.home.x;
                double dz = flight.voxel.z() - flight.home.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (flight.autopilot.phase() != RescuePhase.LANDED) {
                    return StepResult.fail("Disarmt ohne Landung: " + describe(flight));
                }
                return StepResult.check(dist <= LANDING_TOLERANCE && Math.abs(flight.voxel.bottom() - flight.home.y) < 0.2,
                        "Landung zu weit vom Homepoint: " + describe(flight));
            }
            if (flight.seconds > MAX_FLIGHT_SECONDS) {
                return StepResult.fail("Heimflug dauert zu lange: " + describe(flight));
            }
        }
        return StepResult.pending();
    }

    private static String describe(Flight flight) {
        return String.format(Locale.ROOT, "t=%.1fs Phase=%s Pos=(%.2f, %.2f, %.2f) Home=(%.2f, %.2f, %.2f)",
                flight.seconds, flight.autopilot.phase(), flight.voxel.x(), flight.voxel.bottom(), flight.voxel.z(),
                flight.home.x, flight.home.y, flight.home.z);
    }

    private static final class Flight {
        final VoxelFlight voxel;
        final RescueAutopilot autopilot;
        final Vec3 home;
        int crashes;
        double seconds;

        Flight(VoxelFlight voxel, RescueAutopilot autopilot, Vec3 home) {
            this.voxel = voxel;
            this.autopilot = autopilot;
            this.home = home;
        }
    }
}
