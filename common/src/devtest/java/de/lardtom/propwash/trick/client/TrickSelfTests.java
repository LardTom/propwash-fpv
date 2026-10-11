package de.lardtom.propwash.trick.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StickMath;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.trick.TrickFamily;
import de.lardtom.propwash.trick.TrickMode;
import de.lardtom.propwash.trick.TrickSample;
import de.lardtom.propwash.trick.TrickScore;
import de.lardtom.propwash.trick.TrickSession;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TrickSelfTests {
    private static final long FRAME_NANOS = 16_666_667L;
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);

    private TrickSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("tricks: Anmeldung und Standard", 20, TrickSelfTests::setupStep);
        SelfTest.clientStep("tricks: Konfigurator-Tab", 100, TrickSelfTests::tabStep);
        SelfTest.clientStep("tricks: Flip in der Simulation, OSD-Meldung und Flugstatistik", 900, TrickSelfTests::flipStep);
        SelfTest.clientStep("tricks: Kette aus Flip und Rolle, gemeinsame OSD-Meldung", 900, TrickSelfTests::chainStep);
        SelfTest.clientStep("tricks: Repertoire-Tab mit Erklärungen", 100, TrickSelfTests::repertoireStep);
        SelfTest.clientStep("tricks: OSD-Meldung für jeden Trick des Katalogs", 900, TrickSelfTests::catalogOsdStep);
        ProTrickSelfTests.register();
        SelfTest.afterLeaveStep("tricks: Zustand nach dem Verlassen", 40, ctx -> {
            if (TrickTracker.INSTANCE.active()) {
                return StepResult.fail("Trick-Erkennung nach dem Verlassen noch aktiv");
            }
            return StepResult.check(TrickTracker.INSTANCE.session().score().flightScore() == 0, "Trick-Punkte nach dem Verlassen nicht zurückgesetzt");
        });
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        if (TrickClient.CONFIG.get().mode == null) {
            return StepResult.fail("Trick-Config ohne Modus");
        }
        boolean found = false;
        for (ConfigTab tab : ConfigTabs.create()) {
            if ("tricks".equals(tab.id())) {
                found = true;
                if (tab.order() != TrickConfigTab.ORDER) {
                    return StepResult.fail("Tab „Freestyle“ hat die Reihenfolge " + tab.order());
                }
            }
        }
        if (!found) {
            return StepResult.fail("Tab „Freestyle“ fehlt im Konfigurator");
        }
        if (!TrickMode.AUTO.enabledFor(DroneBuild.FREESTYLE) || TrickMode.AUTO.enabledFor(DroneBuild.RACER)) {
            return StepResult.fail("Automatik schaltet Freestyle/Race falsch");
        }
        if (TrickTracker.INSTANCE.active()) {
            return StepResult.fail("Trick-Erkennung ohne Flug aktiv");
        }
        String missing = missingTranslation();
        return missing == null ? StepResult.pass() : StepResult.fail("Übersetzung fehlt: " + missing);
    }

    private static @Nullable String missingTranslation() {
        List<String> keys = new ArrayList<>();
        for (TrickType type : TrickType.values()) {
            keys.add(type.translationKey());
            keys.add("propwash.osd.trick.type." + type.key());
        }
        for (TrickMode mode : TrickMode.values()) {
            keys.add(mode.translationKey());
            keys.add(mode.translationKey() + ".tooltip");
        }
        keys.add(OsdElement.TRICKS.translationKey());
        keys.add("propwash.osd.stats.trick_score");
        keys.add("propwash.osd.stats.best_combo");
        for (String key : keys) {
            if (!Language.getInstance().has(key)) {
                return key;
            }
        }
        return null;
    }

    private static StepResult tabStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            FakeHost host = new FakeHost(8, 28, 510, 302);
            TrickConfigTab tab = host.bind(new TrickConfigTab());
            CycleButton<TrickMode> button = tab.modeButton();
            if (host.widgets.size() != 3 || button == null || tab.searchBox() == null || tab.familyButton() == null) {
                return StepResult.fail("Tab hat " + host.widgets.size() + " statt 3 Widgets (Modus, Suche, Familie)");
            }
            TrickMode before = tab.working().mode;
            if (!click(button)) {
                return StepResult.fail("Moduswahl nimmt keinen Klick an");
            }
            if (tab.working().mode == before || host.changes == 0) {
                return StepResult.fail("Klick ändert den Modus nicht (" + before + ")");
            }
            if (TrickClient.CONFIG.get().mode != before) {
                return StepResult.fail("Tab schreibt vor dem Speichern in die Config");
            }
            tab.resetDefaults();
            if (tab.working().mode != TrickMode.AUTO) {
                return StepResult.fail("Standardwerte setzen nicht „Automatisch“");
            }
            FakeHost narrow = new FakeHost(49, 29, 200, 120);
            TrickConfigTab small = narrow.bind(new TrickConfigTab());
            AtomicBoolean drawn = new AtomicBoolean();
            OsdHud.probeNextFrame(graphics -> {
                tab.extract(graphics, 0, 0, 0.0F);
                small.extract(graphics, 0, 0, 0.0F);
                drawn.set(true);
            });
            state.put("drawn", drawn);
            return StepResult.pending();
        }
        AtomicBoolean drawn = (AtomicBoolean) state.get("drawn");
        return drawn.get() ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult flipStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            Player player = mc.player;
            if (player == null) {
                return StepResult.fail("kein Spieler");
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            FlipSource source = new FlipSource(player.getX(), player.getY() + 40.0, player.getZ());
            state.put("source", source);
            state.put("errors", OsdHud.errors());
            OsdHud.sampler().setOverride(source);
            return StepResult.pending();
        }
        FlipSource source = (FlipSource) state.get("source");
        int errors = OsdHud.errors() - (Integer) state.get("errors");
        if (errors > 0) {
            OsdHud.sampler().setOverride(null);
            return StepResult.fail(errors + " OSD-Fehler beim Zeichnen (siehe Log)");
        }
        TrickScore score = source.session.score();
        OsdData data = OsdHud.sampler().data();
        boolean popupEnabled = OsdHud.config().enabled(OsdElement.TRICKS);
        if (!state.containsKey("flip")) {
            if (score.tricks() > 0) {
                if (score.popupType() != TrickType.FLIP) {
                    OsdHud.sampler().setOverride(null);
                    return StepResult.fail("statt eines Flips erkannt: " + score.popupType());
                }
                state.put("flip", source.steps);
            } else if (source.steps > FlipSource.DISARM_STEP) {
                OsdHud.sampler().setOverride(null);
                return StepResult.fail("kein Trick erkannt (Absturz " + source.sim.telemetry().crashCount + ", scharf " + source.armedSeen + ")");
            }
            return StepResult.pending();
        }
        if (!state.containsKey("popup")) {
            if (!popupEnabled || OsdHud.drewLastFrame(OsdElement.TRICKS)) {
                state.put("popup", true);
                if (!popupEnabled) {
                    ctx.log("tricks: OSD-Element „Tricks“ ist in dieser Config aus, Meldung nicht geprüft");
                }
            } else if (data.trickAgeSeconds > 2.0 && data.trickPopup != OsdData.TRICK_NONE) {
                OsdHud.sampler().setOverride(null);
                return StepResult.fail("Trick-Meldung wurde im OSD nicht gezeichnet");
            }
            return StepResult.pending();
        }
        if (!data.showStats) {
            return StepResult.pending();
        }
        boolean statsEnabled = OsdHud.config().enabled(OsdElement.POST_FLIGHT_STATS);
        if (!data.stats.tricksOn || data.stats.trickScore <= 0) {
            OsdHud.sampler().setOverride(null);
            return StepResult.fail("Flugstatistik ohne Trick-Punkte (" + data.stats.trickScore + ")");
        }
        if (statsEnabled && !OsdHud.drewLastFrame(OsdElement.POST_FLIGHT_STATS)) {
            return StepResult.pending();
        }
        int statsScore = data.stats.trickScore;
        OsdHud.sampler().setOverride(null);
        ctx.log("tricks: Flip nach " + state.get("flip") + " Sim-Schritten, " + statsScore + " Punkte in der Statistik");
        return StepResult.pass();
    }

    private static StepResult chainStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            Player player = mc.player;
            if (player == null) {
                return StepResult.fail("kein Spieler");
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ChainSource source = new ChainSource(player.getX(), player.getY() + 60.0, player.getZ());
            state.put("source", source);
            state.put("errors", OsdHud.errors());
            OsdHud.sampler().setOverride(source);
            return StepResult.pending();
        }
        ChainSource source = (ChainSource) state.get("source");
        int errors = OsdHud.errors() - (Integer) state.get("errors");
        if (errors > 0) {
            OsdHud.sampler().setOverride(null);
            return StepResult.fail(errors + " OSD-Fehler beim Zeichnen (siehe Log)");
        }
        TrickScore score = source.session.score();
        OsdData data = OsdHud.sampler().data();
        if (!state.containsKey("chain")) {
            if (score.tricks() >= 2) {
                List<TrickType> types = new ArrayList<>();
                for (int i = 0; i < score.popupTypeCount(); i++) {
                    types.add(score.popupType(i));
                }
                if (!types.equals(List.of(TrickType.FLIP, TrickType.ROLL)) || !score.popupChain()) {
                    OsdHud.sampler().setOverride(null);
                    return StepResult.fail("statt Kette Flip + Rolle: " + types + " Kette " + score.popupChain() + " Tricks "
                            + source.session.history().stream().map(t -> t.type()).toList());
                }
                if (data.trickTypeCount != 2) {
                    OsdHud.sampler().setOverride(null);
                    return StepResult.fail("OSD-Daten ohne beide Tricks (" + data.trickTypeCount + ")");
                }
                state.put("chain", source.steps);
            } else if (source.steps > ChainSource.DISARM_STEP) {
                OsdHud.sampler().setOverride(null);
                return StepResult.fail("Kette nicht erkannt: " + source.session.history().stream().map(t -> t.type()).toList()
                        + " (Absturz " + source.sim.telemetry().crashCount + ")");
            }
            return StepResult.pending();
        }
        boolean popupEnabled = OsdHud.config().enabled(OsdElement.TRICKS);
        if (popupEnabled && !OsdHud.drewLastFrame(OsdElement.TRICKS)) {
            if (data.trickAgeSeconds > 2.0) {
                OsdHud.sampler().setOverride(null);
                return StepResult.fail("Ketten-Meldung wurde im OSD nicht gezeichnet");
            }
            return StepResult.pending();
        }
        OsdHud.sampler().setOverride(null);
        ctx.log("tricks: Kette Flip + Rolle nach " + state.get("chain") + " Sim-Schritten, " + score.flightScore() + " Punkte, "
                + score.popupPoints() + " in der Meldung");
        return StepResult.pass();
    }

    private static StepResult repertoireStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            FakeHost wide = new FakeHost(8, 28, 640, 360);
            TrickConfigTab tab = wide.bind(new TrickConfigTab());
            if (tab.columns() != TrickConfigTab.MAX_COLUMNS) {
                return StepResult.fail("breiter Tab hat " + tab.columns() + " statt " + TrickConfigTab.MAX_COLUMNS + " Spalten");
            }
            if (tab.visible().size() != TrickType.REPERTOIRE.size() || tab.sections().size() != TrickFamily.values().length) {
                return StepResult.fail("Tab zeigt " + tab.visible().size() + " Tricks in " + tab.sections().size() + " Familien");
            }
            if (tab.searchBox() == null || tab.familyButton() == null) {
                return StepResult.fail("Suche oder Familienfilter fehlt");
            }
            for (TrickType type : TrickType.values()) {
                if (!TrickType.REPERTOIRE.contains(type)) {
                    return StepResult.fail("Trick fehlt im Tab: " + type);
                }
                if (!Language.getInstance().has(type.descriptionKey())) {
                    return StepResult.fail("Erklärung fehlt: " + type.descriptionKey());
                }
            }
            for (TrickFamily family : TrickFamily.values()) {
                if (!Language.getInstance().has(family.translationKey())) {
                    return StepResult.fail("Familienname fehlt: " + family.translationKey());
                }
            }
            int fullRows = tab.rows();
            tab.search("matty");
            List<TrickType> matty = tab.visible();
            if (!matty.containsAll(List.of(TrickType.MATTY_FLIP, TrickType.ANTI_MATTY, TrickType.HALF_MATTY, TrickType.POWER_MATTY)) || tab.rows() >= fullRows) {
                return StepResult.fail("Suche „matty“ zeigt " + matty);
            }
            tab.search("xyzzy");
            if (!tab.visible().isEmpty() || !tab.sections().isEmpty()) {
                return StepResult.fail("Suche ohne Treffer zeigt " + tab.visible());
            }
            tab.search("");
            tab.filterFamily(TrickFamily.THREE_D);
            if (!tab.visible().equals(TrickFamily.THREE_D.tricks())) {
                return StepResult.fail("Familienfilter 3D zeigt " + tab.visible());
            }
            tab.filterFamily(null);
            if (tab.visible().size() != TrickType.REPERTOIRE.size()) {
                return StepResult.fail("Filter lässt sich nicht zurücksetzen");
            }
            FakeHost narrow = new FakeHost(4, 20, 180, 140);
            TrickConfigTab small = narrow.bind(new TrickConfigTab());
            if (small.columns() != 1 || !small.contentBelow()) {
                return StepResult.fail("schmaler Tab: " + small.columns() + " Spalten, unten mehr " + small.contentBelow());
            }
            if (!small.mouseScrolled(40.0, 60.0, 0.0, -3.0) || !small.contentAbove()) {
                return StepResult.fail("schmaler Tab lässt sich nicht scrollen");
            }
            AtomicBoolean drawn = new AtomicBoolean();
            OsdHud.probeNextFrame(graphics -> {
                tab.extract(graphics, 30, 120, 0.0F);
                small.extract(graphics, 20, 60, 0.0F);
                drawn.set(true);
            });
            state.put("drawn", drawn);
            return StepResult.pending();
        }
        AtomicBoolean drawn = (AtomicBoolean) state.get("drawn");
        return drawn.get() ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult catalogOsdStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            Player player = mc.player;
            if (player == null) {
                return StepResult.fail("kein Spieler");
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            CatalogSource source = new CatalogSource(player.getX(), player.getY() + 40.0, player.getZ());
            state.put("source", source);
            state.put("errors", OsdHud.errors());
            OsdHud.sampler().setOverride(source);
            return StepResult.pending();
        }
        CatalogSource source = (CatalogSource) state.get("source");
        int errors = OsdHud.errors() - (Integer) state.get("errors");
        if (errors > 0) {
            OsdHud.sampler().setOverride(null);
            return StepResult.fail(errors + " OSD-Fehler bei " + source.current() + " (siehe Log)");
        }
        boolean enabled = OsdHud.config().enabled(OsdElement.TRICKS);
        if (source.steps < 10) {
            return StepResult.pending();
        }
        if (source.index == 0) {
            source.index = 1;
            return StepResult.pending();
        }
        if (!enabled || OsdHud.drewLastFrame(OsdElement.TRICKS)) {
            source.shown++;
            source.index++;
        }
        if (source.index > TrickType.REPERTOIRE.size()) {
            OsdHud.sampler().setOverride(null);
            if (enabled && source.shown < TrickType.REPERTOIRE.size()) {
                return StepResult.fail("Trick-Meldung nur " + source.shown + " von " + TrickType.REPERTOIRE.size() + " Mal gezeichnet");
            }
            ctx.log("tricks: OSD-Meldung für " + TrickType.REPERTOIRE.size() + " Tricks gezeichnet" + (enabled ? "" : " (Element aus, nur ohne Fehler)"));
            return StepResult.pass();
        }
        return StepResult.pending();
    }

    private static final class CatalogSource implements OsdSampler.Source {
        final DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 17L);
        private final PilotInput input = new PilotInput();
        private final SimEnvironment environment = new SimEnvironment();
        private final de.lardtom.propwash.sim.StepResult result = new de.lardtom.propwash.sim.StepResult();
        private final Vector3d position = new Vector3d();
        int steps;
        int index;
        int shown;

        CatalogSource(double x, double y, double z) {
            this.sim.reset(x, y, z, 0.0);
            this.sim.setBatteryCharge(1.0);
        }

        TrickType current() {
            List<TrickType> all = TrickType.REPERTOIRE;
            return all.get(Math.clamp(this.index - 1, 0, all.size() - 1));
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void fill(OsdData d) {
            PilotInput in = this.input;
            in.mode = StabMode.ANGLE;
            in.armSwitch = this.steps >= 3;
            double hover = this.sim.telemetry().hoverThrottleStick;
            in.throttle = this.steps < 5 ? 0.0 : Double.isFinite(hover) && hover > 0.05 ? hover : 0.35;
            this.sim.step(FRAME_NANOS, in, this.environment, this.result);
            this.steps++;
            d.applySim(this.sim);
            this.sim.getPosition(this.position);
            d.hasPosition = true;
            d.x = this.position.x;
            d.y = this.position.y;
            d.z = this.position.z;
            d.radioConnected = true;
            d.linkStrength = 0.95;
            d.videoSystem = VideoSystem.DIGITAL;
            d.videoSignal = 0.9;
            List<TrickType> all = TrickType.REPERTOIRE;
            if (this.index == 0 || this.index > all.size()) {
                d.clearTricks();
                return;
            }
            d.tricksOn = true;
            d.trickPopup = OsdData.TRICK_DONE;
            int n = Math.min(TrickScore.POPUP_TYPES, this.index);
            d.trickTypeCount = n;
            for (int i = 0; i < n; i++) {
                d.trickTypes[i] = all.get(this.index - n + i);
            }
            d.trickType = d.trickTypes[n - 1];
            d.trickPoints = 1650;
            d.trickCombo = 12;
            d.trickAgeSeconds = 0.2;
        }
    }

    private static boolean click(AbstractWidget widget) {
        double cx = widget.getX() + widget.getWidth() * 0.5;
        double cy = widget.getY() + widget.getHeight() * 0.5;
        return widget.mouseClicked(new MouseButtonEvent(cx, cy, LEFT), false);
    }

    private static final class FlipSource implements OsdSampler.Source {
        static final int ARM_STEP = 3;
        static final int FLIP_STEP = 60;
        static final int FLIP_STEPS = 33;
        static final int DISARM_STEP = 300;

        final DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 11L);
        final TrickSession session = new TrickSession();
        private final PilotInput input = new PilotInput();
        private final SimEnvironment environment = new SimEnvironment();
        private final de.lardtom.propwash.sim.StepResult result = new de.lardtom.propwash.sim.StepResult();
        private final TrickSample sample = new TrickSample();
        private final Vector3d position = new Vector3d();
        private final Vector3d velocity = new Vector3d();
        private final Quaterniond attitude = new Quaterniond();
        int steps;
        boolean armedSeen;

        FlipSource(double x, double y, double z) {
            this.sim.reset(x, y, z, 0.0);
            this.sim.setBatteryCharge(1.0);
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void fill(OsdData d) {
            this.command();
            this.sim.step(FRAME_NANOS, this.input, this.environment, this.result);
            this.steps++;
            Telemetry t = this.sim.telemetry();
            this.armedSeen |= t.armed;
            d.applySim(this.sim);
            this.sim.getPosition(this.position);
            this.sim.getVelocity(this.velocity);
            this.sim.getAttitude(this.attitude);
            d.hasPosition = true;
            d.x = this.position.x;
            d.y = this.position.y;
            d.z = this.position.z;
            d.radioConnected = true;
            d.linkStrength = 0.95;
            d.videoSystem = VideoSystem.DIGITAL;
            d.videoSignal = 0.9;
            TrickSample s = this.sample;
            s.time = t.time;
            s.x = this.position.x;
            s.y = this.position.y;
            s.z = this.position.z;
            s.vx = this.velocity.x;
            s.vy = this.velocity.y;
            s.vz = this.velocity.z;
            s.qx = this.attitude.x;
            s.qy = this.attitude.y;
            s.qz = this.attitude.z;
            s.qw = this.attitude.w;
            s.armed = t.armed;
            s.crashCount = t.crashCount;
            s.impactCount = t.impactCount;
            s.grounded = t.grounded;
            s.throttle = t.throttleOut;
            s.clearNearby();
            this.session.update(s);
            d.applyTricks(this.session);
        }

        private void command() {
            PilotInput in = this.input;
            in.roll = 0.0;
            in.pitch = 0.0;
            in.yaw = 0.0;
            in.armSwitch = this.steps >= ARM_STEP && this.steps < DISARM_STEP;
            double hover = this.sim.telemetry().hoverThrottleStick;
            double hoverStick = Double.isFinite(hover) && hover > 0.05 ? hover : 0.35;
            if (this.steps < ARM_STEP + 2) {
                in.mode = StabMode.ANGLE;
                in.throttle = 0.0;
            } else if (this.steps < FLIP_STEP) {
                in.mode = StabMode.ANGLE;
                in.throttle = hoverStick;
            } else if (this.steps < FLIP_STEP + FLIP_STEPS) {
                in.mode = StabMode.ACRO;
                in.throttle = 0.3;
                in.pitch = StickMath.rateToStick(650.0, this.sim.config().rates, Axis.PITCH);
            } else {
                in.mode = StabMode.ANGLE;
                in.throttle = this.steps < DISARM_STEP ? hoverStick : 0.0;
            }
        }
    }

    private static final class ChainSource implements OsdSampler.Source {
        static final int ARM_STEP = 3;
        static final int TRICK_STEP = 60;
        static final double RATE = 650.0;
        static final int DISARM_STEP = 360;

        final DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 13L);
        final TrickSession session = new TrickSession();
        private final PilotInput input = new PilotInput();
        private final SimEnvironment environment = new SimEnvironment();
        private final de.lardtom.propwash.sim.StepResult result = new de.lardtom.propwash.sim.StepResult();
        private final TrickSample sample = new TrickSample();
        private final Vector3d position = new Vector3d();
        private final Vector3d velocity = new Vector3d();
        private final Quaterniond attitude = new Quaterniond();
        private final Quaterniond before = new Quaterniond();
        private final Quaterniond step = new Quaterniond();
        private int phase;
        private double turned;
        int steps;

        ChainSource(double x, double y, double z) {
            this.sim.reset(x, y, z, 0.0);
            this.sim.setBatteryCharge(1.0);
            this.session.keepHistory(16);
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void fill(OsdData d) {
            this.command();
            this.sim.getAttitude(this.before);
            this.sim.step(FRAME_NANOS, this.input, this.environment, this.result);
            this.steps++;
            Telemetry t = this.sim.telemetry();
            d.applySim(this.sim);
            this.sim.getPosition(this.position);
            this.sim.getVelocity(this.velocity);
            this.sim.getAttitude(this.attitude);
            this.before.conjugate(this.step).mul(this.attitude);
            if (this.step.w < 0.0) {
                this.step.set(-this.step.x, -this.step.y, -this.step.z, -this.step.w);
            }
            double vl = Math.sqrt(this.step.x * this.step.x + this.step.y * this.step.y + this.step.z * this.step.z);
            if (vl > 1.0E-12) {
                double k = Math.toDegrees(2.0 * Math.atan2(vl, this.step.w) / vl);
                this.turned += this.phase == 1 ? this.step.x * k : this.phase == 2 ? this.step.z * k : 0.0;
            }
            d.hasPosition = true;
            d.x = this.position.x;
            d.y = this.position.y;
            d.z = this.position.z;
            d.radioConnected = true;
            d.linkStrength = 0.95;
            d.videoSystem = VideoSystem.DIGITAL;
            d.videoSignal = 0.9;
            TrickSample s = this.sample;
            s.time = t.time;
            s.x = this.position.x;
            s.y = this.position.y;
            s.z = this.position.z;
            s.vx = this.velocity.x;
            s.vy = this.velocity.y;
            s.vz = this.velocity.z;
            s.qx = this.attitude.x;
            s.qy = this.attitude.y;
            s.qz = this.attitude.z;
            s.qw = this.attitude.w;
            s.armed = t.armed;
            s.crashCount = t.crashCount;
            s.impactCount = t.impactCount;
            s.grounded = t.grounded;
            s.throttle = t.throttleOut;
            s.clearNearby();
            this.session.update(s);
            d.applyTricks(this.session);
        }

        private void command() {
            PilotInput in = this.input;
            in.roll = 0.0;
            in.pitch = 0.0;
            in.yaw = 0.0;
            in.armSwitch = this.steps >= ARM_STEP && this.steps < DISARM_STEP;
            double hover = this.sim.telemetry().hoverThrottleStick;
            double hoverStick = Double.isFinite(hover) && hover > 0.05 ? hover : 0.35;
            if (this.steps < ARM_STEP + 2) {
                in.mode = StabMode.ANGLE;
                in.throttle = 0.0;
                return;
            }
            if (this.steps >= TRICK_STEP && this.phase == 0) {
                this.phase = 1;
                this.turned = 0.0;
            }
            if (this.phase == 1 && this.turned >= 360.0) {
                this.phase = 2;
                this.turned = 0.0;
            } else if (this.phase == 2 && this.turned >= 360.0) {
                this.phase = 3;
            }
            switch (this.phase) {
                case 1 -> {
                    in.mode = StabMode.ACRO;
                    in.throttle = 0.3;
                    in.pitch = StickMath.rateToStick(RATE, this.sim.config().rates, Axis.PITCH);
                }
                case 2 -> {
                    in.mode = StabMode.ACRO;
                    in.throttle = 0.3;
                    in.roll = StickMath.rateToStick(RATE, this.sim.config().rates, Axis.ROLL);
                }
                default -> {
                    in.mode = StabMode.ANGLE;
                    in.throttle = this.steps < DISARM_STEP ? hoverStick : 0.0;
                }
            }
        }
    }

    private static final class FakeHost implements ConfigTabHost {
        final List<AbstractWidget> widgets = new ArrayList<>();
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        int changes;
        private @Nullable ConfigTab tab;
        private @Nullable Screen screen;

        FakeHost(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        <T extends ConfigTab> T bind(T tab) {
            this.tab = tab;
            this.rebuild();
            return tab;
        }

        @Override
        public <T extends AbstractWidget> T addWidget(T widget) {
            this.widgets.add(widget);
            return widget;
        }

        @Override
        public Font font() {
            return Minecraft.getInstance().font;
        }

        @Override
        public Minecraft minecraft() {
            return Minecraft.getInstance();
        }

        @Override
        public Screen screen() {
            if (this.screen == null) {
                this.screen = new Screen(Component.empty()) {
                };
            }
            return this.screen;
        }

        @Override
        public void rebuild() {
            this.widgets.clear();
            if (this.tab != null) {
                this.tab.attach(this, this.x, this.y, this.width, this.height);
            }
        }

        @Override
        public void changed() {
            this.changes++;
        }
    }
}
