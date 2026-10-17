package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.PilotView;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.server.ServerRulesConfig;
import de.lardtom.propwash.server.SpectatorLinks;
import de.lardtom.propwash.server.ViewAnchors;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.video.ImageFormat;
import de.lardtom.propwash.video.LinkBudget;
import de.lardtom.propwash.video.SpectateServer;
import de.lardtom.propwash.video.VideoChannels;
import de.lardtom.propwash.video.VtxSettings;
import de.lardtom.propwash.video.net.ChannelListS2C;
import de.lardtom.propwash.video.net.SpectateC2S;
import de.lardtom.propwash.video.net.SpectateS2C;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SpectateSelfTests {
    private static final String PILOT_A = "Testpilotin";
    private static final String PILOT_C = "Digitalpilot";
    private static final double NEAR = 16.0;
    private static final double FAR = 150.0;
    private static final double STEP_BLOCKS = 3.0;

    private static @Nullable Scenario scenario;

    private SpectateSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("video: Videokanäle werden automatisch ohne Doppelung vergeben", 300, SpectateSelfTests::channelStep);
        SelfTest.clientStep("video: Zuschauen – Taste wählt einen Fremdkanal, Kamera, Bild und Chunks folgen der Drohne", 1400,
                SpectateSelfTests::spectateStep);
        SelfTest.afterLeaveStep("video: Zuschauen nach dem Verlassen zurückgesetzt", 20, ctx -> {
            SpectateSession session = SpectateSession.INSTANCE;
            if (session.active() || session.drone() != null) {
                return StepResult.fail("Zuschauen nach dem Verlassen noch aktiv: " + session.phase());
            }
            return StepResult.check(session.channels().isEmpty(), "Kanalliste nach dem Verlassen nicht leer: " + session.channels().size());
        });
    }

    private static StepResult channelStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            Scenario created = new Scenario();
            scenario = created;
            created.future = ctx.onServer(server -> setupChannels(server, created));
            return StepResult.pending();
        }
        Scenario s = scenario;
        if (s == null) {
            return StepResult.fail("kein Testaufbau");
        }
        if (s.future.isCompletedExceptionally()) {
            return finish(ctx, s, StepResult.fail("Kanalvergabe: " + s.future.exceptionNow().getMessage()));
        }
        if (!s.future.isDone()) {
            return StepResult.pending();
        }
        ChannelListS2C.Entry a = entry(s.droneA);
        ChannelListS2C.Entry c = entry(s.droneC);
        boolean gone = entry(s.droneB) == null && entry(s.droneD) == null;
        if (a == null || c == null || !gone) {
            return ctx.tick() > 200 ? finish(ctx, s, StepResult.fail("Kanalliste auf dem Client unvollständig: " + describe()))
                    : StepResult.pending();
        }
        if (a.channel() != 0 || a.system() != VideoSystem.ANALOG || !PILOT_A.equals(a.pilotName())) {
            return finish(ctx, s, StepResult.fail("Kanal der Analog-Drohne falsch: " + describe()));
        }
        if (c.channel() != 0 || c.system() != VideoSystem.DIGITAL) {
            return finish(ctx, s, StepResult.fail("Kanal der Digital-Drohne falsch: " + describe()));
        }
        if (!"R1".equals(VideoChannels.label(a.system(), a.channel())) || !"CH1".equals(VideoChannels.label(c.system(), c.channel()))) {
            return finish(ctx, s, StepResult.fail("Beschriftung falsch: " + describe()));
        }
        ctx.log("Kanäle: " + describe() + " · " + s.log);
        return StepResult.pass();
    }

    private static void setupChannels(MinecraftServer server, Scenario s) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        QuadcopterEntity a = spawn(level, eye.add(0.0, 3.0, NEAR), DroneBuild.RACER);
        QuadcopterEntity b = spawn(level, eye.add(4.0, 3.0, NEAR), DroneBuild.RACER);
        QuadcopterEntity c = spawn(level, eye.add(-4.0, 3.0, NEAR), DroneBuild.FREESTYLE);
        QuadcopterEntity d = spawn(level, eye.add(8.0, 3.0, NEAR), DroneBuild.RACER);
        s.droneA = a.getUUID();
        s.droneB = b.getUUID();
        s.droneC = c.getUUID();
        s.droneD = d.getUUID();
        SpectateServer.simulateTransmitter(a, PILOT_A);
        SpectateServer.simulateTransmitter(b, "Zweiter");
        SpectateServer.simulateTransmitter(c, PILOT_C);
        int ca = SpectateServer.channelOf(a);
        int cb = SpectateServer.channelOf(b);
        int cc = SpectateServer.channelOf(c);
        if (ca != 0 || cb != 1 || cc != 0) {
            throw new IllegalStateException("automatisch: A=" + ca + " B=" + cb + " C=" + cc + " statt R1, R2, CH1");
        }
        d.setTune(new FlightTune(VtxSettings.with(d.tune().values(), 1, true)));
        SpectateServer.simulateTransmitter(d, "Fest");
        int ca2 = SpectateServer.channelOf(a);
        int cb2 = SpectateServer.channelOf(b);
        int cd = SpectateServer.channelOf(d);
        if (cd != 1 || !VtxSettings.manual(d.tune().values())) {
            throw new IllegalStateException("fester Kanal R2 nicht behalten: D=" + cd);
        }
        if (ca2 != 0 || cb2 == 1 || VideoChannels.frequencyMhz(VideoSystem.ANALOG, cb2) == VideoChannels.frequencyMhz(VideoSystem.ANALOG, 0)) {
            throw new IllegalStateException("automatischer Kanal weicht dem festen nicht aus: A=" + ca2 + " B=" + cb2);
        }
        s.log = "B wich von R2 auf " + VideoChannels.label(VideoSystem.ANALOG, cb2) + " aus";
        SpectateServer.simulateTransmitter(b, null);
        SpectateServer.simulateTransmitter(d, null);
        b.discard();
        d.discard();
        SpectateServer.refreshSoon();
    }

    private static QuadcopterEntity spawn(ServerLevel level, Vec3 pos, DroneBuild build) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(build);
        drone.setNoGravity(true);
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        drone.setAttitude(new Quaternionf());
        return drone;
    }

    private static StepResult spectateStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Scenario s = scenario;
        if (s == null || s.droneA == null) {
            return StepResult.fail("kein Testaufbau aus dem Kanal-Schritt");
        }
        SpectateSession session = SpectateSession.INSTANCE;
        if (s.future != null && s.future.isCompletedExceptionally()) {
            return finish(ctx, s, StepResult.fail(s.phase + ": " + s.future.exceptionNow().getMessage()));
        }
        if (s.future != null && !s.future.isDone()) {
            return StepResult.pending();
        }
        int inPhase = ctx.tick() - s.phaseTick;
        QuadcopterEntity a = clientDrone(minecraft, s.droneA);
        switch (s.phase) {
            case EQUIP -> {
                if (PilotSession.INSTANCE.isActive()) {
                    PilotSession.INSTANCE.requestStop();
                    return StepResult.pending();
                }
                if (!s.equipRequested) {
                    s.equipRequested = true;
                    s.future = ctx.onServer(server -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                        s.previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
                        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new)
                                .orElseThrow(() -> new IllegalStateException("Analog-Brille fehlt"));
                        player.setItemSlot(EquipmentSlot.HEAD, goggles);
                    });
                    return StepResult.pending();
                }
                if (SpectateSession.goggles(minecraft) != VideoSystem.ANALOG || a == null) {
                    return inPhase > 200 ? finish(ctx, s, StepResult.fail("Analog-Brille oder Drohne A fehlt auf dem Client")) : StepResult.pending();
                }
                if (!session.watchable(minecraft).stream().allMatch(e -> e.system() == VideoSystem.ANALOG)) {
                    return finish(ctx, s, StepResult.fail("Digital-Kanal für die Analog-Brille wählbar: " + describe()));
                }
                press(SpectateKeys.CYCLE);
                s.enter(Phase.WATCH, ctx.tick());
                return StepResult.pending();
            }
            case WATCH -> {
                if (!session.watching() || a == null || session.drone() != a) {
                    return inPhase > 200 ? finish(ctx, s, StepResult.fail("Taste startet das Zuschauen nicht: Phase " + session.phase()
                            + ", Ergebnis " + session.lastResult())) : StepResult.pending();
                }
                if (inPhase < 25) {
                    return StepResult.pending();
                }
                String problem = checkWatching(minecraft, a);
                if (problem != null) {
                    return finish(ctx, s, StepResult.fail(problem));
                }
                s.nearQuality = VideoState.INSTANCE.rawQuality();
                s.cameraBefore = minecraft.gameRenderer.mainCamera().position();
                s.future = ctx.onServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    QuadcopterEntity drone = serverDrone(server, s.droneA);
                    s.serverOk = drone != null && SpectateServer.watching(player) == drone && SpectatorLinks.watched(player) == drone
                            && ViewAnchors.anchorDrone(player) == drone;
                    if (drone != null) {
                        drone.snapTo(drone.getX() + 6.0, drone.getY(), drone.getZ(), 90.0F, 0.0F);
                        drone.setAttitude(new Quaternionf().rotationY((float) Math.toRadians(-90.0)));
                    }
                });
                s.enter(Phase.MOVE, ctx.tick());
                return StepResult.pending();
            }
            case MOVE -> {
                if (!s.serverOk) {
                    return finish(ctx, s, StepResult.fail("Server kennt den Zuschauer nicht oder verankert die Sicht nicht an der Drohne"));
                }
                Vec3 camera = minecraft.gameRenderer.mainCamera().position();
                Vec3 before = s.cameraBefore;
                if (before == null || camera.distanceTo(before) < 4.0) {
                    return inPhase > 120 ? finish(ctx, s, StepResult.fail("Kamera folgt der bewegten Drohne nicht: " + camera + " vorher " + before))
                            : StepResult.pending();
                }
                ctx.log("Kamera folgt der Drohne über " + String.format(java.util.Locale.ROOT, "%.1f", camera.distanceTo(before)) + " Blöcke");
                s.enter(Phase.FAR, ctx.tick());
                return StepResult.pending();
            }
            case FAR -> {
                if (a == null) {
                    return inPhase > 300 ? finish(ctx, s, StepResult.fail("Drohne A beim Wegfliegen vom Client verschwunden")) : StepResult.pending();
                }
                if (s.moved < FAR) {
                    s.moved += STEP_BLOCKS;
                    s.future = ctx.onServer(server -> {
                        QuadcopterEntity drone = serverDrone(server, s.droneA);
                        if (drone == null) {
                            throw new IllegalStateException("Drohne A auf dem Server verschwunden");
                        }
                        drone.snapTo(drone.getX() + STEP_BLOCKS, drone.getY(), drone.getZ(), 90.0F, 0.0F);
                    });
                    return StepResult.pending();
                }
                Minecraft mc = minecraft;
                boolean chunk = mc.level != null && mc.level.getChunkSource().hasChunk(a.getBlockX() >> 4, a.getBlockZ() >> 4);
                double distance = VideoState.INSTANCE.distanceBlocks();
                if (!chunk || distance < FAR - 10.0 || !session.watching()) {
                    return inPhase > 400 ? finish(ctx, s, StepResult.fail("Chunk an der fernen Drohne fehlt oder Abstand falsch: Chunk=" + chunk
                            + " Abstand=" + distance + " schaut=" + session.watching())) : StepResult.pending();
                }
                if (s.farChecked == null) {
                    s.farChecked = ctx.onServer(server -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                        QuadcopterEntity drone = serverDrone(server, s.droneA);
                        s.serverOk = drone != null && ViewAnchors.anchorDrone(player) == drone
                                && player.level().hasChunk(drone.getBlockX() >> 4, drone.getBlockZ() >> 4);
                    });
                    return StepResult.pending();
                }
                if (!s.farChecked.isDone()) {
                    return StepResult.pending();
                }
                if (!s.serverOk) {
                    return finish(ctx, s, StepResult.fail("Server hält die Chunks um die ferne Drohne nicht für den Zuschauer"));
                }
                if (inPhase < 60) {
                    return StepResult.pending();
                }
                float far = VideoState.INSTANCE.rawQuality();
                double expected = LinkBudget.quality(VideoSystem.ANALOG, distance, a.build().video().rangeBlocks, VideoState.INSTANCE.obstructionDb());
                if (Math.abs(far - expected) > 0.02 || far > s.nearQuality - 0.01F) {
                    return finish(ctx, s, StepResult.fail("Signal hängt nicht am Abstand des Zuschauers: nah " + s.nearQuality + ", fern " + far
                            + ", erwartet " + expected));
                }
                ctx.log("Signal nah " + s.nearQuality + ", fern " + far + " bei " + Math.round(distance) + " Blöcken");
                s.future = ctx.onServer(server -> {
                    QuadcopterEntity drone = serverDrone(server, s.droneA);
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    if (drone != null) {
                        Vec3 eye = player.getEyePosition();
                        drone.snapTo(eye.x, eye.y + 3.0, eye.z + NEAR, 180.0F, 0.0F);
                    }
                });
                ClientNet.sendToServer(SpectateC2S.watch(s.droneC));
                s.enter(Phase.WRONG, ctx.tick());
                return StepResult.pending();
            }
            case WRONG -> {
                if (session.active() || session.lastResult() != SpectateS2C.Result.WRONG_SYSTEM) {
                    return inPhase > 100 ? finish(ctx, s, StepResult.fail("Server lässt die Analog-Brille Digital schauen: " + session.lastResult()
                            + " " + session.phase())) : StepResult.pending();
                }
                if (minecraft.getCameraEntity() != minecraft.player || FpvView.active()) {
                    return inPhase > 40 ? finish(ctx, s, StepResult.fail("Kamera nach der Ablehnung nicht zurück beim Spieler")) : StepResult.pending();
                }
                ChannelListS2C.Entry c = entry(s.droneC);
                if (c != null && session.request(minecraft, c)) {
                    return finish(ctx, s, StepResult.fail("Client fragt Digital-Kanal mit Analog-Brille an"));
                }
                press(SpectateKeys.CYCLE);
                s.enter(Phase.CYCLE_OFF, ctx.tick());
                return StepResult.pending();
            }
            case CYCLE_OFF -> {
                if (!s.pressedOff) {
                    if (!session.watching()) {
                        return inPhase > 200 ? finish(ctx, s, StepResult.fail("Zweites Zuschauen startet nicht")) : StepResult.pending();
                    }
                    press(SpectateKeys.CYCLE);
                    s.pressedOff = true;
                    return StepResult.pending();
                }
                if (session.active()) {
                    return inPhase > 240 ? finish(ctx, s, StepResult.fail("Taste nach dem letzten Kanal beendet das Zuschauen nicht")) : StepResult.pending();
                }
                press(SpectateKeys.CYCLE);
                s.enter(Phase.ESCAPE, ctx.tick());
                return StepResult.pending();
            }
            case ESCAPE -> {
                if (!s.escaped) {
                    if (!session.watching()) {
                        return inPhase > 200 ? finish(ctx, s, StepResult.fail("Drittes Zuschauen startet nicht")) : StepResult.pending();
                    }
                    long handle = minecraft.getWindow().handle();
                    KeyEvent escape = new KeyEvent(InputConstants.KEY_ESCAPE, 27, 0);
                    minecraft.keyboardHandler.keyPress(handle, InputConstants.PRESS, escape);
                    minecraft.keyboardHandler.keyPress(handle, InputConstants.RELEASE, escape);
                    s.escaped = true;
                    return StepResult.pending();
                }
                if (session.active() || minecraft.gui.screen() != null || minecraft.getCameraEntity() != minecraft.player) {
                    return finish(ctx, s, StepResult.fail("Esc beendet das Zuschauen nicht sauber: aktiv=" + session.active() + " Screen="
                            + minecraft.gui.screen()));
                }
                s.future = ctx.onServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    s.serverOk = SpectateServer.watching(player) == null;
                });
                s.enter(Phase.RELEASED, ctx.tick());
                return StepResult.pending();
            }
            case RELEASED -> {
                if (!s.releaseConfirmed) {
                    if (s.serverOk) {
                        s.releaseConfirmed = true;
                        s.enter(Phase.RELEASED, ctx.tick());
                        return StepResult.pending();
                    }
                    if (inPhase > 60) {
                        return finish(ctx, s, StepResult.fail("Server führt den Spieler nach Esc noch als Zuschauer"));
                    }
                    s.future = ctx.onServer(server -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                        s.serverOk = SpectateServer.watching(player) == null;
                    });
                    return StepResult.pending();
                }
                if (inPhase == 10) {
                    s.future = ctx.onServer(server -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                        s.serverOk = ViewAnchors.anchorDrone(player) == null;
                    });
                    return StepResult.pending();
                }
                if (inPhase < 12) {
                    return StepResult.pending();
                }
                if (!s.serverOk) {
                    return finish(ctx, s, StepResult.fail("Sicht-Anker nach dem Zuschauen nicht gelöst"));
                }
                String tab = checkTab(minecraft, s);
                if (tab != null) {
                    return finish(ctx, s, StepResult.fail(tab));
                }
                s.future = ctx.onServer(server -> {
                    ServerRulesConfig.get().allowSpectating = false;
                    SpectateServer.refreshSoon();
                });
                s.enter(Phase.RULE, ctx.tick());
                return StepResult.pending();
            }
            case RULE -> {
                if (session.allowed()) {
                    return inPhase > 100 ? finish(ctx, s, StepResult.fail("Server-Regel „Zuschauen aus“ kommt nicht an")) : StepResult.pending();
                }
                if (!s.ruleProbe) {
                    s.ruleProbe = true;
                    press(SpectateKeys.CYCLE);
                    ClientNet.sendToServer(SpectateC2S.watch(s.droneA));
                    s.ruleTick = ctx.tick();
                    return StepResult.pending();
                }
                if (session.lastResult() != SpectateS2C.Result.RULE_OFF || session.active()) {
                    return ctx.tick() - s.ruleTick > 100 ? finish(ctx, s, StepResult.fail("Zuschauen trotz Server-Regel möglich: "
                            + session.lastResult() + " " + session.phase())) : StepResult.pending();
                }
                return finish(ctx, s, StepResult.pass());
            }
            default -> {
                return finish(ctx, s, StepResult.pass());
            }
        }
    }

    private static @Nullable String checkWatching(Minecraft minecraft, QuadcopterEntity a) {
        if (minecraft.getCameraEntity() != a) {
            return "Kamera-Entity ist nicht die Drohne";
        }
        Camera camera = minecraft.gameRenderer.mainCamera();
        double offset = camera.position().distanceTo(a.position());
        if (offset > 1.0) {
            return "Kamera nicht an der Drohne: " + String.format(java.util.Locale.ROOT, "%.2f", offset) + " Blöcke";
        }
        if (!FpvView.active() || !FpvView.spectating() || !PilotView.active()) {
            return "Zuschau-Ansicht nicht aktiv (FpvView/PilotView)";
        }
        VideoState video = VideoState.INSTANCE;
        if (video.source() != a || video.status() != VideoState.LinkStatus.CONNECTED || video.system() != VideoSystem.ANALOG
                || video.receiver() != VideoSystem.ANALOG) {
            return "Videobild nicht von der Drohne: Quelle=" + video.source() + " Status=" + video.status() + " System=" + video.system();
        }
        if (video.fpvFov() <= 0.0F) {
            return "FPV-Sichtfeld der Brille nicht aktiv";
        }
        if (ImageFormat.AUTO.resolve(FpvImage.transmitter()) != ImageFormat.FOUR_THREE) {
            return "Bildformat Auto folgt beim Zuschauen nicht dem analogen Sender: " + FpvImage.transmitter();
        }
        if (video.distanceBlocks() < NEAR - 4.0 || video.distanceBlocks() > NEAR + 6.0) {
            return "Abstand Brille ↔ Drohne unplausibel: " + video.distanceBlocks();
        }
        OsdData data = OsdHud.sampler().data();
        if (!data.active || !data.spectating || !"R1".equals(data.vtxChannel)) {
            return "OSD des Piloten fehlt: aktiv=" + data.active + " zuschauen=" + data.spectating + " VTX=" + data.vtxChannel;
        }
        if (SpectateHud.renderedFrames() == 0 || !SpectateHud.lastBanner().contains("R1") || !SpectateHud.lastBanner().contains(PILOT_A)) {
            return "Kanal-Einblendung fehlt: " + SpectateHud.lastBanner();
        }
        if (!OsdHud.hideVanilla() && OsdHud.config().hideVanillaHud) {
            return "Vanilla-HUD beim Zuschauen sichtbar";
        }
        return null;
    }

    private static @Nullable String checkTab(Minecraft minecraft, Scenario s) {
        List<AbstractWidget> widgets = new ArrayList<>();
        ConfigTabHost host = new ConfigTabHost() {
            @Override
            public <T extends AbstractWidget> T addWidget(T widget) {
                widgets.add(widget);
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
        };
        ChannelTab tab = new ChannelTab();
        tab.attach(host, 0, 0, 480, 300);
        List<FlatButton> rows = tab.channelButtons();
        if (rows.size() != 2) {
            return "Kanal-Tab zeigt " + rows.size() + " statt 2 Kanäle";
        }
        int active = 0;
        for (FlatButton row : rows) {
            if (row.active) {
                active++;
                if (!row.getMessage().getString().contains("R1")) {
                    return "wählbarer Kanal ist nicht R1: " + row.getMessage().getString();
                }
            }
        }
        if (active != 1) {
            return "Kanal-Tab: " + active + " wählbare Kanäle statt 1 (Digital darf mit Analog-Brille nicht wählbar sein)";
        }
        if (widgets.size() < rows.size() + 3) {
            return "Kanal-Tab ohne Bedienelemente: " + widgets.size();
        }
        tab.selectChannel(9);
        if (tab.vtxEditable() && tab.selectedChannel() != VideoChannels.clamp(tab.droneSystem(), 9)) {
            return "fester Kanal im Tab nicht gesetzt: " + tab.selectedChannel();
        }
        tab.selectChannel(VideoChannels.UNASSIGNED);
        if (tab.selectedChannel() != VideoChannels.UNASSIGNED) {
            return "Automatisch im Tab nicht gesetzt: " + tab.selectedChannel();
        }
        return null;
    }

    private static void press(KeyMapping mapping) {
        KeyMapping.click(InputConstants.getKey(mapping.saveString()));
    }

    private static ChannelListS2C.@Nullable Entry entry(@Nullable UUID drone) {
        return drone == null ? null : SpectateSession.INSTANCE.entry(drone);
    }

    private static String describe() {
        StringBuilder out = new StringBuilder();
        for (ChannelListS2C.Entry e : SpectateSession.INSTANCE.channels()) {
            out.append(e.pilotName()).append('=').append(VideoChannels.label(e.system(), e.channel())).append(' ');
        }
        return out.toString().trim();
    }

    private static @Nullable QuadcopterEntity clientDrone(Minecraft minecraft, @Nullable UUID id) {
        if (minecraft.level == null || id == null) {
            return null;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof QuadcopterEntity drone && drone.getUUID().equals(id)) {
                return drone;
            }
        }
        return null;
    }

    private static @Nullable QuadcopterEntity serverDrone(MinecraftServer server, @Nullable UUID id) {
        if (id == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        return null;
    }

    private static StepResult finish(SelfTest.StepContext ctx, Scenario s, StepResult result) {
        Minecraft minecraft = Minecraft.getInstance();
        SpectateSession.INSTANCE.stop(minecraft, true);
        scenario = null;
        ctx.onServer(server -> {
            ServerRulesConfig.get().allowSpectating = true;
            for (UUID id : new UUID[]{s.droneA, s.droneB, s.droneC, s.droneD}) {
                QuadcopterEntity drone = serverDrone(server, id);
                if (drone != null) {
                    SpectateServer.simulateTransmitter(drone, null);
                    drone.discard();
                }
            }
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            if (s.previousHead != null) {
                player.setItemSlot(EquipmentSlot.HEAD, s.previousHead);
            }
            SpectateServer.refreshSoon();
        });
        return result;
    }

    private enum Phase {
        EQUIP,
        WATCH,
        MOVE,
        FAR,
        WRONG,
        CYCLE_OFF,
        ESCAPE,
        RELEASED,
        RULE
    }

    private static final class Scenario {
        private CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        private @Nullable CompletableFuture<Void> farChecked;
        private volatile @Nullable UUID droneA;
        private volatile @Nullable UUID droneB;
        private volatile @Nullable UUID droneC;
        private volatile @Nullable UUID droneD;
        private volatile String log = "";
        private volatile @Nullable ItemStack previousHead;
        private volatile boolean serverOk = true;
        private Phase phase = Phase.EQUIP;
        private int phaseTick;
        private boolean equipRequested;
        private boolean pressedOff;
        private boolean escaped;
        private boolean releaseConfirmed;
        private boolean ruleProbe;
        private int ruleTick;
        private double moved;
        private float nearQuality;
        private @Nullable Vec3 cameraBefore;

        private void enter(Phase next, int tick) {
            this.phase = next;
            this.phaseTick = tick;
        }
    }
}
