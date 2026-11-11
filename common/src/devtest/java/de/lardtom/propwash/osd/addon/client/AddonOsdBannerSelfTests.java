package de.lardtom.propwash.osd.addon.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleOsd;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdPreset;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.osd.client.SpectateBannerOsdProbe;
import de.lardtom.propwash.video.SpectateServer;
import de.lardtom.propwash.video.client.SpectateConfig;
import de.lardtom.propwash.video.client.SpectateHud;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AddonOsdBannerSelfTests {
    static final String PREFIX = AddonOsdSelfTests.GROUP + "Zuschauer-Banner";
    private static final String GERMAN = "de_de";
    private static final String PILOT = "Lotte";
    private static final String FUTURE = "banner_future";
    private static final String RUN = "banner_run";
    private static final String SHOT = "banner_shot";
    private static final int SETTLE_TICKS = 3;
    private static final int CONNECT_TICKS = 300;
    private static final int FRAME_TICKS = 100;
    private static final int SHOT_TICKS = 20;
    private static final int SMALLEST_WIDTH = 320;
    private static final int SMALLEST_HEIGHT = 240;
    private static final int MAX_SCALE = 8;
    private static final AtomicReference<@Nullable UUID> WATCHED = new AtomicReference<>();
    private static final AtomicReference<@Nullable UUID> OWN = new AtomicReference<>();
    private static final AtomicInteger WATCHED_ID = new AtomicInteger(-1);
    private static final AtomicInteger OWN_ID = new AtomicInteger(-1);
    private static volatile @Nullable ItemStack previousHead;

    private AddonOsdBannerSelfTests() {
    }

    enum Mode {
        SPECTATE("Zuschauen", "spectate"),
        OPERATOR("Kameramann", "operator"),
        SOLO("allein am Gimbal", "solo");

        final String label;
        final String file;

        Mode(String label, String file) {
            this.label = label;
            this.file = file;
        }
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + ": Testfeld mit Sender der Pilotin Lotte und eigener Drohne mit Sender", 600, AddonOsdBannerSelfTests::setupStep);
        String check = PREFIX + " und Warnfeld mit GHOST überlagern sich nicht: Zuschauen, Kameramann und allein am Gimbal, "
                + "Presets MINIMAL, STANDARD, RACE und FULL, jede GUI-Skala ab 320×240";
        SelfTest.clientStep(check, 6000, AddonOsdBannerSelfTests::checkStep);
        boolean shots = Screenshots.enabled();
        if (shots) {
            SelfTest.clientStep(PREFIX + ": Fenster für die Bildschirmfotos", 200, Screenshots::resizeWindow);
            shots("en", "");
        }
        String back = PREFIX + ": Sprache zurückstellen";
        SelfTest.switchStep(PREFIX + ": Sprache Deutsch", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
        SelfTest.clientStep(check + " (deutsch)", 6000, AddonOsdBannerSelfTests::checkStep);
        if (shots) {
            shots("de", " deutsch");
        }
        SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
        if (shots) {
            SelfTest.clientStep(PREFIX + ": Fenster zurück", 100, Screenshots::restoreWindow);
        }
        SelfTest.clientStep(PREFIX + ": Testfeld abbauen", 400, AddonOsdBannerSelfTests::cleanupStep);
    }

    private static void shots(String language, String label) {
        for (Mode mode : Mode.values()) {
            Screenshots.step(PREFIX + ": Bildschirmfoto " + mode.label + " mit Warnung GHOST, Preset MINIMAL" + label,
                    mode.file + "_banner_warning_" + language, 10, new Shot(mode));
        }
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!JoinTestEnv.exampleAddon()) {
            ctx.log("Beispiel-Add-on aus, nichts zu prüfen");
            return StepResult.pass();
        }
        if (!state.containsKey(FUTURE)) {
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
                return ctx.tick() > 200 ? StepResult.fail("alte Flugsitzung endet nicht") : StepResult.pending();
            }
            SpectateSession.INSTANCE.stop(mc, true);
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            state.put(FUTURE, ctx.onServer(AddonOsdBannerSelfTests::build));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufbau: " + future.exceptionNow());
        }
        SpectateSession session = SpectateSession.INSTANCE;
        UUID watched = WATCHED.get();
        UUID own = OWN.get();
        ChannelListS2C.Entry entry = watched == null ? null : session.entry(watched);
        if (entry == null || own == null || session.entry(own) == null || SpectateSession.goggles(mc) == null) {
            return ctx.tick() > 400 ? StepResult.fail("Kanäle oder Brille fehlen auf dem Client") : StepResult.pending();
        }
        ctx.log("Sender " + SpectateSession.channelName(entry).getString() + " von " + PILOT + ", eigene Drohne " + own);
        return StepResult.pass();
    }

    private static StepResult checkStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        Run run = (Run) state.get(RUN);
        if (run == null) {
            if (!JoinTestEnv.exampleAddon()) {
                ctx.log("Beispiel-Add-on aus, nichts zu prüfen");
                return StepResult.pass();
            }
            if (WATCHED.get() == null || OWN.get() == null) {
                return StepResult.fail("Testfeld fehlt");
            }
            if (!SpectateConfig.get().showBanner) {
                return StepResult.fail("Banner in spectate.json abgeschaltet");
            }
            run = new Run(mc.options.guiScale().get(), scales(mc.getWindow()));
            if (run.scales.length == 0) {
                return StepResult.fail("Fenster kleiner als 320×240");
            }
            state.put(RUN, run);
            ExampleOsd.ghost(true);
        }
        StepResult result;
        try {
            result = run.step(ctx, mc);
        } catch (RuntimeException e) {
            result = StepResult.fail("Prüfung: " + e);
        }
        if (result.status() != StepResult.Status.PENDING) {
            run.finish(mc);
        }
        return result;
    }

    private static int[] scales(Window window) {
        List<Integer> out = new ArrayList<>();
        for (int scale = 1; scale <= MAX_SCALE; scale++) {
            if (window.getWidth() / scale >= SMALLEST_WIDTH && window.getHeight() / scale >= SMALLEST_HEIGHT) {
                out.add(scale);
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    static boolean start(Minecraft mc, Mode mode) {
        SpectateSession session = SpectateSession.INSTANCE;
        UUID watched = WATCHED.get();
        UUID own = OWN.get();
        if (watched == null || own == null) {
            return false;
        }
        return switch (mode) {
            case SPECTATE -> {
                ChannelListS2C.Entry entry = session.entry(watched);
                yield entry != null && session.request(mc, entry);
            }
            case OPERATOR -> {
                session.operate(mc, watched, WATCHED_ID.get());
                yield true;
            }
            case SOLO -> {
                session.operate(mc, own, OWN_ID.get());
                yield true;
            }
        };
    }

    static boolean watching(Mode mode) {
        SpectateSession session = SpectateSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        int id = mode == Mode.SOLO ? OWN_ID.get() : WATCHED_ID.get();
        return session.watching() && drone != null && drone.getId() == id && session.operator() == (mode != Mode.SPECTATE);
    }

    static @Nullable String overlap(int[] box, float @Nullable [] warning) {
        if (warning == null) {
            return "Warnfeld nicht gezeichnet";
        }
        boolean covered = box[0] < warning[2] && warning[0] < box[2] && box[1] < warning[3] && warning[1] < box[3];
        return covered ? String.format(Locale.ROOT, "Banner %d,%d–%d,%d über dem Warnfeld %.1f,%.1f–%.1f,%.1f", box[0], box[1], box[2], box[3],
                warning[0], warning[1], warning[2], warning[3]) : null;
    }

    private static final class Run {
        private final int savedScale;
        private final int[] scales;
        private final List<String> errors = new ArrayList<>();
        private int mode;
        private int scale;
        private int preset;
        private String phase = "start";
        private int since;
        private int bannerFrames;
        private int osdFrames;
        private int checked;

        Run(int savedScale, int[] scales) {
            this.savedScale = savedScale;
            this.scales = scales;
        }

        StepResult step(SelfTest.StepContext ctx, Minecraft mc) {
            SpectateSession session = SpectateSession.INSTANCE;
            Mode current = Mode.values()[this.mode];
            switch (this.phase) {
                case "start" -> {
                    if (session.active()) {
                        session.stop(mc, true);
                    }
                    if (!start(mc, current)) {
                        Component blocker = session.blocker(mc, null);
                        return StepResult.fail(current.label + " startet nicht: " + (blocker == null ? session.lastResult() : blocker.getString()));
                    }
                    this.phase = "connect";
                    this.since = ctx.tick();
                    return StepResult.pending();
                }
                case "connect" -> {
                    if (!watching(current)) {
                        return ctx.tick() - this.since > CONNECT_TICKS
                                ? StepResult.fail(current.label + " verbindet nicht: " + session.phase() + ", Operator " + session.operator())
                                : StepResult.pending();
                    }
                    if (mc.gui.screen() != null) {
                        mc.gui.setScreen(null);
                    }
                    this.apply(ctx, mc);
                    return StepResult.pending();
                }
                default -> {
                    if (!watching(current)) {
                        return StepResult.fail(current.label + " endet unerwartet: " + session.phase());
                    }
                    boolean fresh = SpectateHud.renderedFrames() - this.bannerFrames >= 2 && OsdHud.renderedFrames() - this.osdFrames >= 2;
                    if (ctx.tick() - this.since < SETTLE_TICKS || !fresh) {
                        return ctx.tick() - this.since > FRAME_TICKS ? StepResult.fail("kein frisches Bild bei " + this.where(mc, current))
                                : StepResult.pending();
                    }
                    this.measure(ctx, mc, current);
                    return this.advance(ctx, mc);
                }
            }
        }

        private void apply(SelfTest.StepContext ctx, Minecraft mc) {
            int wanted = this.scales[this.scale];
            if (mc.options.guiScale().get() != wanted) {
                mc.options.guiScale().set(wanted);
                mc.resizeGui();
            }
            OsdConfig config = OsdConfig.defaults();
            config.applyPreset(OsdPreset.values()[this.preset]);
            SpectateBannerOsdProbe.override(config);
            this.bannerFrames = SpectateHud.renderedFrames();
            this.osdFrames = OsdHud.renderedFrames();
            this.phase = "settle";
            this.since = ctx.tick();
        }

        private String where(Minecraft mc, Mode current) {
            Window window = mc.getWindow();
            return current.label + " " + OsdPreset.values()[this.preset] + " GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight();
        }

        private void measure(SelfTest.StepContext ctx, Minecraft mc, Mode current) {
            int[] box = SpectateHud.lastBox();
            float[] warning = SpectateBannerOsdProbe.drawn(OsdElement.WARNINGS);
            String where = this.where(mc, current);
            String problem = overlap(box, warning);
            ctx.log(where + ": Banner Zeile " + SpectateHud.lastRow() + " (y " + box[1] + "–" + box[3] + ")"
                    + (warning == null ? "" : String.format(Locale.ROOT, ", Warnfeld y %.1f–%.1f", warning[1], warning[3]))
                    + (problem == null ? " frei" : " – " + problem));
            if (problem != null) {
                this.errors.add(where + ": " + problem);
            }
            this.checked++;
        }

        private StepResult advance(SelfTest.StepContext ctx, Minecraft mc) {
            if (this.preset + 1 < OsdPreset.values().length) {
                this.preset++;
                this.apply(ctx, mc);
                return StepResult.pending();
            }
            this.preset = 0;
            if (this.scale + 1 < this.scales.length) {
                this.scale++;
                this.apply(ctx, mc);
                return StepResult.pending();
            }
            this.scale = 0;
            if (this.mode + 1 < Mode.values().length) {
                this.mode++;
                this.phase = "start";
                return StepResult.pending();
            }
            ctx.log(this.checked + " Bilder geprüft, " + this.errors.size() + " mit verdeckter Warnung");
            return this.errors.isEmpty() ? StepResult.pass() : StepResult.fail(this.errors.size() + " von " + this.checked + ": " + String.join("; ", this.errors));
        }

        void finish(Minecraft mc) {
            SpectateSession.INSTANCE.stop(mc, true);
            SpectateBannerOsdProbe.override(null);
            ExampleOsd.ghost(false);
            if (mc.options.guiScale().get() != this.savedScale) {
                mc.options.guiScale().set(this.savedScale);
                mc.resizeGui();
            }
        }
    }

    private record Shot(Mode mode) implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!JoinTestEnv.exampleAddon()) {
                return StepResult.fail("Beispiel-Add-on aus");
            }
            if (!state.containsKey(SHOT)) {
                SpectateSession.INSTANCE.stop(mc, true);
                SpectateBannerOsdProbe.override(OsdConfig.defaults());
                ExampleOsd.ghost(true);
                if (!start(mc, this.mode)) {
                    return StepResult.fail(this.mode.label + " startet nicht");
                }
                state.put(SHOT, ctx.tick());
                return StepResult.pending();
            }
            int since = ctx.tick() - (int) state.get(SHOT);
            if (!watching(this.mode)) {
                return since > CONNECT_TICKS ? StepResult.fail(this.mode.label + " verbindet nicht") : StepResult.pending();
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            return since < SHOT_TICKS ? StepResult.pending() : StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            int[] box = SpectateHud.lastBox();
            String problem = overlap(box, SpectateBannerOsdProbe.drawn(OsdElement.WARNINGS));
            ctx.log(this.mode.label + ": Banner Zeile " + SpectateHud.lastRow() + " „" + SpectateHud.lastBanner() + "“" + (problem == null ? ", Warnfeld frei"
                    : " – " + problem));
            SpectateSession.INSTANCE.stop(Minecraft.getInstance(), true);
            SpectateBannerOsdProbe.override(null);
            ExampleOsd.ghost(false);
        }
    }

    private static void build(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        remove(server);
        ServerLevel level = player.level();
        float yaw = player.getYRot();
        Vec3 ahead = Vec3.directionFromRotation(0.0F, yaw);
        Vec3 side = ahead.yRot((float) (Math.PI / 2.0));
        Vec3 first = player.position().add(ahead.scale(3.0)).add(0.0, 2.0, 0.0);
        QuadcopterEntity watched = spawn(level, first, yaw, DroneBuild.RACER);
        SpectateServer.simulateTransmitter(watched, PILOT);
        QuadcopterEntity own = spawn(level, first.add(side.scale(2.5)), yaw, DroneBuild.RACER);
        SpectateServer.simulateTransmitter(own, player.getName().getString());
        WATCHED.set(watched.getUUID());
        WATCHED_ID.set(watched.getId());
        OWN.set(own.getUUID());
        OWN_ID.set(own.getId());
        if (previousHead == null) {
            previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        }
        player.setItemSlot(EquipmentSlot.HEAD, ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new)
                .orElseThrow(() -> new IllegalStateException("Analog-Brille fehlt")));
        SpectateServer.refreshSoon();
    }

    private static QuadcopterEntity spawn(ServerLevel level, Vec3 pos, float yaw, DroneBuild build) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(build);
        drone.setNoGravity(true);
        drone.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        drone.resetTo(pos, new Quaternionf().rotationY((float) Math.toRadians(-yaw) + (float) Math.PI).rotateX((float) Math.toRadians(-8.0)));
        return drone;
    }

    private static void remove(MinecraftServer server) {
        for (AtomicReference<@Nullable UUID> ref : List.of(WATCHED, OWN)) {
            UUID id = ref.getAndSet(null);
            if (id == null) {
                continue;
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                    SpectateServer.simulateTransmitter(drone, null);
                    drone.discard();
                }
            }
        }
        WATCHED_ID.set(-1);
        OWN_ID.set(-1);
    }

    private static void teardown(MinecraftServer server) {
        remove(server);
        ItemStack head = previousHead;
        if (head != null) {
            server.getPlayerList().getPlayers().getFirst().setItemSlot(EquipmentSlot.HEAD, head);
            previousHead = null;
        }
        SpectateServer.refreshSoon();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            SpectateSession.INSTANCE.stop(mc, true);
            SpectateBannerOsdProbe.override(null);
            ExampleOsd.ghost(false);
            state.put(FUTURE, ctx.onServer(AddonOsdBannerSelfTests::teardown));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (!future.isDone()) {
            return ctx.tick() > 200 ? StepResult.fail("Abbau hängt") : StepResult.pending();
        }
        return future.isCompletedExceptionally() ? StepResult.fail("Abbau: " + future.exceptionNow()) : StepResult.pass();
    }
}
