package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.led.DroneLeds;
import de.lardtom.propwash.drone.led.LedAnimator;
import de.lardtom.propwash.drone.led.LedColor;
import de.lardtom.propwash.drone.led.LedMode;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.LedTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LedSelfTests {
    private static final String DRONE_ID = "led_drone_id";
    private static final String SPAWN = "led_spawn";
    private static final String SERVER_PROBLEM = "led_server_problem";
    private static final LedSettings SYNCED = new LedSettings(LedMode.BLINK, LedColor.RED, 60, true);

    private LedSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("drone-render: LED-Streifen leuchtet in Farbe und Modus", 1, ctx -> glowStep());
        SelfTest.clientStep("drone-render: LED-Einstellungen in der Drohne gespeichert und sichtbar", 300, LedSelfTests::entityStep);
        SelfTest.clientStep("ui: LED-Tab bearbeitet Farbe, Modus, Helligkeit und Scheinwerfer", 1, ctx -> tabStep());
    }

    private static StepResult glowStep() {
        PoseStack poseStack = new PoseStack();
        for (DroneBuild preset : DroneBuild.PRESETS) {
            if (!preset.frame().accessoryMounts.contains(AccessoryMount.BOTTOM)) {
                continue;
            }
            String where = preset.frame().key();
            DroneRenderState bare = new DroneRenderState();
            DroneVisuals.fillPreview(bare, preset);
            if (bare.stripLeds != 0 || bare.stripMount >= 0) {
                return StepResult.fail(where + ": LED-Streifen ohne Zubehör");
            }
            SubmitNodeStorage bareStorage = new SubmitNodeStorage();
            DroneVisuals.submit(bare, poseStack, bareStorage);
            if (!bareStorage.order(1).translucentCustomGeometry.isEmpty()) {
                return StepResult.fail(where + ": entschärfte Drohne ohne LED-Streifen leuchtet");
            }
            DroneBuild build = preset.withAccessory(AccessoryType.LED_STRIP);
            DroneRenderState preview = new DroneRenderState();
            DroneVisuals.fillPreview(preview, build);
            if (preview.stripMount != AccessoryMount.BOTTOM.ordinal() || preview.stripLeds != AccessoryType.LED_STRIP.ledCount) {
                return StepResult.fail(where + ": Streifen nicht erkannt (Halterung " + preview.stripMount + ", LEDs " + preview.stripLeds + ")");
            }
            for (int i = 0; i < preview.stripLeds; i++) {
                if (preview.stripGlow[i].isEmpty() || preview.stripHalo[i].isEmpty()) {
                    return StepResult.fail(where + ": LED " + i + " ohne Leuchtform");
                }
                if (preview.stripColor[i] != (0xFF000000 | LedColor.WHITE.rgb())) {
                    return StepResult.fail(where + ": Vorschau-LED " + i + " nicht weiß: " + Integer.toHexString(preview.stripColor[i]));
                }
            }
            SubmitNodeStorage storage = new SubmitNodeStorage();
            DroneVisuals.submit(preview, poseStack, storage);
            if (storage.order(1).translucentCustomGeometry.isEmpty()) {
                return StepResult.fail(where + ": LED-Streifen zeichnet kein Leuchten");
            }
            if (!poseStack.isEmpty()) {
                return StepResult.fail(where + ": PoseStack nach dem Zeichnen nicht leer");
            }
            for (LedMode mode : LedMode.values()) {
                for (boolean armed : new boolean[]{false, true}) {
                    double seconds = 0.1 + mode.ordinal() * 0.37;
                    LedSettings settings = new LedSettings(mode, LedColor.CYAN, 70, true);
                    DroneRenderState state = DroneVisuals.ledPreviewState(build, settings, armed, seconds);
                    if (state == null) {
                        return StepResult.fail(where + ": keine LED-Vorschau");
                    }
                    LedAnimator.Input input = new LedAnimator.Input(armed, true, 1.0F, seconds);
                    for (int i = 0; i < state.stripLeds; i++) {
                        int expected = LedAnimator.color(settings, i, state.stripLeds, input);
                        if (state.stripColor[i] != expected) {
                            return StepResult.fail(where + " " + mode + " scharf=" + armed + ": LED " + i + " zeigt "
                                    + Integer.toHexString(state.stripColor[i]) + " statt " + Integer.toHexString(expected));
                        }
                    }
                }
            }
            DroneRenderState off = DroneVisuals.ledPreviewState(build, new LedSettings(LedMode.BLINK, LedColor.RED, 100, true), false, 0.3);
            SubmitNodeStorage offStorage = new SubmitNodeStorage();
            DroneVisuals.submit(off, poseStack, offStorage);
            if (!offStorage.order(1).translucentCustomGeometry.isEmpty()) {
                return StepResult.fail(where + ": Blinken in der Aus-Phase leuchtet trotzdem");
            }
        }
        return StepResult.pass();
    }

    private static StepResult entityStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            AtomicInteger id = new AtomicInteger(-1);
            AtomicReference<String> problem = new AtomicReference<>();
            ctx.state().put(DRONE_ID, id);
            ctx.state().put(SERVER_PROBLEM, problem);
            ctx.state().put(SPAWN, ctx.onServer(server -> {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    throw new IllegalStateException("kein Spieler auf dem Server");
                }
                ServerPlayer player = players.getFirst();
                ServerLevel level = player.level();
                QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                if (drone == null) {
                    throw new IllegalStateException("EntityType.create lieferte null");
                }
                DroneBuild build = DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP);
                Vec3 pos = player.position().add(-2.0, 0.0, 2.0);
                drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
                drone.setBuild(build);
                FlightTune edited = new FlightTune(SYNCED.values());
                drone.setTune(DronePiloting.sanitize(FlightTune.EMPTY, edited, build));
                LedSettings stored = DroneLeds.settings(drone);
                if (!stored.equals(SYNCED)) {
                    problem.set("Server übernimmt die LED-Einstellungen nicht: " + stored + " aus " + drone.tune().values());
                }
                ItemStack item = drone.toStack();
                QuadcopterEntity copy = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                if (copy != null && !item.isEmpty()) {
                    copy.loadFromStack(item);
                    if (!DroneLeds.settings(copy).equals(SYNCED)) {
                        problem.set("LED-Einstellungen gehen beim Aufheben und Platzieren verloren: " + DroneLeds.settings(copy));
                    }
                    if (DroneLeds.luminance(copy) <= 0) {
                        problem.set("Lichtstärke für LambDynamicLights ist 0 trotz eingeschaltetem Streifen");
                    }
                }
                level.addFreshEntity(drone);
                id.set(drone.getId());
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> spawn = (CompletableFuture<?>) ctx.state().get(SPAWN);
        if (spawn.isCompletedExceptionally()) {
            return StepResult.fail("Spawn fehlgeschlagen: " + spawn.exceptionNow());
        }
        int id = ((AtomicInteger) ctx.state().get(DRONE_ID)).get();
        if (!spawn.isDone() || id < 0 || mc.level == null) {
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        String problem = ((AtomicReference<String>) ctx.state().get(SERVER_PROBLEM)).get();
        if (problem != null) {
            discard(ctx, id);
            return StepResult.fail(problem);
        }
        Entity entity = mc.level.getEntity(id);
        if (!(entity instanceof QuadcopterEntity drone) || !DroneLeds.settings(drone).equals(SYNCED) || DroneLeds.strip(drone).isEmpty()) {
            if (ctx.tick() >= 280) {
                discard(ctx, id);
                return StepResult.fail("LED-Einstellungen kommen nicht beim Client an");
            }
            return StepResult.pending();
        }
        EntityRenderState extracted;
        try {
            extracted = mc.getEntityRenderDispatcher().extractEntity(drone, 0.0F);
        } finally {
            discard(ctx, id);
        }
        if (!(extracted instanceof DroneRenderState state)) {
            return StepResult.fail("Render-State ist " + extracted.getClass().getName());
        }
        if (state.stripLeds != AccessoryType.LED_STRIP.ledCount) {
            return StepResult.fail("Welt-Drohne zeigt " + state.stripLeds + " LEDs");
        }
        LedAnimator.Input input = DroneLeds.input(drone, state.ageInTicks / 20.0);
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < state.stripLeds; i++) {
            int expected = LedAnimator.color(SYNCED, i, state.stripLeds, input);
            if (state.stripColor[i] != expected) {
                wrong.add(i + ": " + Integer.toHexString(state.stripColor[i]) + " statt " + Integer.toHexString(expected));
            }
        }
        if (!wrong.isEmpty()) {
            return StepResult.fail("LED-Farben der Welt-Drohne: " + wrong);
        }
        ctx.log("Welt-Drohne: " + SYNCED + ", LED 0 = " + Integer.toHexString(state.stripColor[0]));
        return StepResult.pass();
    }

    private static StepResult tabStep() {
        LedTab tab = null;
        for (ConfigTab candidate : ConfigTabs.create()) {
            if (candidate instanceof LedTab found) {
                tab = found;
            }
        }
        if (tab == null) {
            return StepResult.fail("LED-Tab nicht angemeldet");
        }
        List<AbstractWidget> widgets = new ArrayList<>();
        tab.attach(new Host(widgets), 0, 0, 420, 260);
        if (widgets.size() < 5) {
            return StepResult.fail("LED-Tab baut nur " + widgets.size() + " Bedienelemente");
        }
        tab.edit(LedSettings.MODE, LedMode.RAINBOW.ordinal());
        tab.edit(LedSettings.COLOR, LedColor.GREEN.ordinal());
        tab.edit(LedSettings.BRIGHTNESS, 42.0F);
        tab.edit(LedSettings.HEADLIGHT, 0.0F);
        LedSettings edited = tab.settings();
        LedSettings expected = new LedSettings(LedMode.RAINBOW, LedColor.GREEN, 40, false);
        if (!edited.equals(expected)) {
            tab.resetDefaults();
            return StepResult.fail("LED-Tab speichert " + edited + " statt " + expected);
        }
        tab.resetDefaults();
        if (!tab.settings().equals(LedSettings.DEFAULT)) {
            return StepResult.fail("Standardwerte setzen die LED-Einstellungen nicht zurück: " + tab.settings());
        }
        tab.discard();
        return StepResult.pass();
    }

    private static void discard(SelfTest.StepContext ctx, int id) {
        ctx.onServer(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
        });
    }

    private record Host(List<AbstractWidget> widgets) implements ConfigTabHost {
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
            return Minecraft.getInstance().gui.screen();
        }

        @Override
        public void rebuild() {
        }

        @Override
        public void changed() {
        }
    }
}
