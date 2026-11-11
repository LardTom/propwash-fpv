package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.client.AccFixJoinProbe;
import de.lardtom.propwash.barrier.client.BarrierJoinProbe;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.trail.RemoteTrailCheck;
import de.lardtom.propwash.drone.collision.client.CollisionJoinProbe;
import de.lardtom.propwash.freecam.client.FreecamJoinProbe;
import de.lardtom.propwash.drone.client.CovCrashRulesRun;
import de.lardtom.propwash.drone.client.CovCrashWallRun;
import de.lardtom.propwash.drone.client.CovHeatJoinProbe;
import de.lardtom.propwash.drone.client.CovLandingJoinProbe;
import de.lardtom.propwash.drone.client.CovSurfaceJoinProbe;
import de.lardtom.propwash.drone.client.DroneScaleJoinProbe;
import de.lardtom.propwash.drone.client.DroneRotationJoinProbe;
import de.lardtom.propwash.freecam.client.CovFreecamJoinProbe;
import de.lardtom.propwash.video.client.CovCameraAngleLanCheck;
import de.lardtom.propwash.drone.data.client.AddonDataJoinProbe;
import de.lardtom.propwash.drone.operator.client.OperatorJoinProbe;
import de.lardtom.propwash.input.addon.client.Addon17JoinProbe;
import de.lardtom.propwash.interact.client.DroneTriggerSelfTests;
import de.lardtom.propwash.join.client.AddonSessionSelfTests;
import de.lardtom.propwash.osd.client.FlightPathMarkerLanCheck;
import de.lardtom.propwash.osd.client.SpecCountJoinProbe;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.race.client.AssistJoinProbe;
import de.lardtom.propwash.race.client.GateHintJoinProbe;
import de.lardtom.propwash.race.client.RaceDataJoinProbe;
import de.lardtom.propwash.race.client.RaceHomeJoinProbe;
import de.lardtom.propwash.race.client.CovRaceJoinProbe;
import de.lardtom.propwash.race.client.RaceLoopJoinProbe;
import de.lardtom.propwash.race.client.RaceP2pJoinProbe;
import de.lardtom.propwash.race.client.CourseMenuJoinProbe;
import de.lardtom.propwash.race.client.CreativeFcJoinProbe;
import de.lardtom.propwash.interact.client.CovVineJoinProbe;
import de.lardtom.propwash.audio.client.DroneSoundJoinProbe;
import de.lardtom.propwash.race.client.RaceRulesJoinProbe;
import de.lardtom.propwash.race.client.GateStackSelfTests;
import de.lardtom.propwash.race.client.GatePushJoinProbe;
import de.lardtom.propwash.race.client.RaceGhostsJoinProbe;
import de.lardtom.propwash.rx.client.RxJoinProbe;
import de.lardtom.propwash.rx.client.CovRxJoinProbe;
import de.lardtom.propwash.server.client.CovLanGuestJoinProbe;
import de.lardtom.propwash.video.client.CovWatchRescueJoinProbe;
import de.lardtom.propwash.video.client.CameraAngleJoinProbe;
import de.lardtom.propwash.video.client.SignalMemorySelfTests;
import de.lardtom.propwash.zone.client.ZoneJoinProbe;
import de.lardtom.propwash.zone.client.ZonesOpenJoinProbe;
import de.lardtom.propwash.drone.collision.client.CovNoAddonJoinProbe;
import de.lardtom.propwash.guard.client.CovGuardJoinProbes;
import de.lardtom.propwash.server.client.CovAddonRulesJoinProbe;
import de.lardtom.propwash.share.client.ShareJoinProbe;
import de.lardtom.propwash.share.chat.ShareChatJoinProbe;
import de.lardtom.propwash.input.client.AutoArmJoinProbe;
import de.lardtom.propwash.share.client.CovChatShareJoinProbe;
import de.lardtom.propwash.share.client.CovShareJoinProbe;
import de.lardtom.propwash.webexport.client.CovWebexportJoinProbe;
import de.lardtom.propwash.stats.client.StatsJoinProbe;
import de.lardtom.propwash.capture.client.CaptureJoinProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class JoinTest {
    private static final int MAX_TICKS = 20 * 150;
    private static final int STABLE_TICKS = 30;
    private static final int PILOT_WAIT_TICKS = 20 * 60;
    private static final String STEP = "Beitrittstest ";

    private JoinTest() {
    }

    public static boolean enabled() {
        return SelfTest.smokeEnabled() && JoinTestEnv.target() != null;
    }

    public static List<SelfTest.Step> steps() {
        String label = JoinTestEnv.label();
        int ticks = MAX_TICKS + 20 * (JoinTestEnv.trailSeconds() > 0 ? 180 + JoinTestEnv.trailSeconds() : 0) + (JoinTestEnv.assist() ? 20 * 90 : 0)
                + (JoinTestEnv.zones() ? ZoneJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.spectateFpmSeconds() > 0 ? 20 * (240 + JoinTestEnv.spectateFpmSeconds()) + FlightPathMarkerLanCheck.OBSERVER_EXTRA_TICKS : 0)
                + (JoinTestEnv.rx() ? RxJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.plates() ? 2 * DroneTriggerSelfTests.JOIN_TIMEOUT_TICKS : 0)
                + (JoinTestEnv.stackGate() ? 20 * 150 : 0)
                + (JoinTestEnv.signal() ? 20 * 90 : 0)
                + (JoinTestEnv.gateHint() ? GateHintJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + MultiJoinProbe.extraTicks()
                + (JoinTestEnv.addon17() ? Addon17JoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.addonData() ? AddonDataJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.collision() ? CollisionJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.share() ? ShareJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.raceHome() ? RaceHomeJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.shareChat() ? ShareChatJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.freecam() ? FreecamJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.raceLoop() ? RaceLoopJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covFreecam() ? CovFreecamJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.raceData() ? RaceDataJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.operator() ? OperatorJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.accFix() ? AccFixJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covSurface() ? CovSurfaceJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covLanding() ? CovLandingJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covHeat() ? CovHeatJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covCamangle() ? CovCameraAngleLanCheck.OBSERVER_TICKS + 20 * 60 * 5 : 0)
                + (JoinTestEnv.covRx() > 0 ? CovRxJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covWatchRescue() ? CovWatchRescueJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covLanGuest() ? CovLanGuestJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.droneScale() ? DroneScaleJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.barriers() ? BarrierJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.zonesOpen() ? ZonesOpenJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covAddonRules() ? CovAddonRulesJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covNoAddon() ? CovNoAddonJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covNsOp() || JoinTestEnv.covNoclip() ? CovGuardJoinProbes.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covRace() > 0 ? CovRaceJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.autoArm() ? AutoArmJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.raceRules() ? RaceRulesJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covShare() ? CovShareJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covChatShare() ? CovChatShareJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covWebexport() ? CovWebexportJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.raceP2p() ? RaceP2pJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.autonomyOffline() > 0 ? AutonomyOfflineJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.droneRotation() ? 3 * DroneRotationJoinProbe.TIMEOUT_TICKS : 0)
                + (JoinTestEnv.gatePush() ? GatePushJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.raceGhosts() ? RaceGhostsJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.stats() ? StatsJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.capture() ? CaptureJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covCrashRules() ? CovCrashRulesRun.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.courseMenu() ? CourseMenuJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.specCount() > 0 ? SpecCountJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.creativeFc() ? CreativeFcJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covVine() ? CovVineJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.droneSound() ? DroneSoundJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.covCrashWall() ? CovCrashWallRun.TIMEOUT_TICKS + 20 * 30 : 0)
                + (JoinTestEnv.menus() ? MenusJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0);
        SelfTest.Step join = new SelfTest.Step(STEP + label, ticks, JoinTest::tick);
        return JoinTestEnv.crossLoader() ? List.of(join, CrossLoaderJoinProbe.singleplayerStep()) : List.of(join);
    }

    private static SelfTest.StepResult tick(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        String target = JoinTestEnv.target();
        if (target == null) {
            return SelfTest.StepResult.fail("kein Ziel");
        }
        if (JoinTestEnv.crossLoader()) {
            if (ctx.tick() == 0) {
                CrossLoaderJoinProbe.before();
            }
            CrossLoaderJoinProbe.observe(ctx.tick(), mc);
        }
        if (ctx.tick() == 0) {
            ctx.log("verbinde mit " + target + " (" + Services.PLATFORM.getPlatformName() + ")");
            ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(target),
                    new ServerData("Propwash join test", target, ServerData.Type.OTHER), false, null);
            return SelfTest.StepResult.pending();
        }
        if (state.containsKey("capture")) {
            @SuppressWarnings("unchecked")
            CompletableFuture<String> capture = (CompletableFuture<String>) state.get("capture");
            if (!capture.isDone()) {
                return SelfTest.StepResult.pending();
            }
            if (state.containsKey("leaving")) {
                if (mc.level != null) {
                    return SelfTest.StepResult.pending();
                }
            } else if (mc.level != null) {
                state.put("leaving", true);
                mc.disconnectFromWorld(Component.literal("Propwash join test"));
                return SelfTest.StepResult.pending();
            }
            return evaluate((String) state.get("text"));
        }
        if (state.containsKey(CameraAngleJoinProbe.OPEN)) {
            CameraAngleJoinProbe.whileOpen(state, ctx.tick(), mc);
            return SelfTest.StepResult.pending();
        }
        if (state.containsKey(Addon17JoinProbe.OPEN)) {
            Addon17JoinProbe.whileOpen(state, ctx.tick(), mc);
            return SelfTest.StepResult.pending();
        }
        if (state.containsKey(CovAddonRulesJoinProbe.OPEN)) {
            CovAddonRulesJoinProbe.whileOpen(state, ctx.tick(), mc);
            return SelfTest.StepResult.pending();
        }
        if (state.containsKey(GateHintJoinProbe.ACTIVE)) {
            GateHintJoinProbe.whileActive(state, ctx.tick(), mc);
            return SelfTest.StepResult.pending();
        }
        if (state.containsKey(CovFreecamJoinProbe.ACTIVE)) {
            CovFreecamJoinProbe.whileActive(state, ctx.tick(), mc);
            return SelfTest.StepResult.pending();
        }
        if (state.containsKey(ShareJoinProbe.ACTIVE) && !state.containsKey("share")) {
            String share = ShareJoinProbe.observe(state, ctx.tick(), mc);
            if (share == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("share", share);
        }
        if (state.containsKey(CovShareJoinProbe.ACTIVE) && !state.containsKey("cov_share")) {
            String covShare = CovShareJoinProbe.observe(state, ctx.tick(), mc);
            if (covShare == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_share", covShare);
        }
        if (state.containsKey(CovChatShareJoinProbe.ACTIVE) && !state.containsKey("cov_chatshare")) {
            String covChatShare = CovChatShareJoinProbe.observe(state, ctx.tick(), mc);
            if (covChatShare == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_chatshare", covChatShare);
        }
        if (state.containsKey(AutonomyOfflineJoinProbe.AWAY)) {
            AutonomyOfflineJoinProbe.whileAway(state, ctx.tick(), mc, target);
            return SelfTest.StepResult.pending();
        }
        if (state.containsKey(StatsJoinProbe.OPEN)) {
            StatsJoinProbe.whileOpen(state, ctx.tick(), mc);
            return SelfTest.StepResult.pending();
        }
        if (state.containsKey(CourseMenuJoinProbe.OPEN)) {
            CourseMenuJoinProbe.whileOpen(state, ctx.tick(), mc);
            return SelfTest.StepResult.pending();
        }
        if (state.containsKey(RaceDataJoinProbe.OPEN)) {
            RaceDataJoinProbe.whileOpen(state, ctx.tick(), mc);
            return SelfTest.StepResult.pending();
        }
        if (state.containsKey(CreativeFcJoinProbe.OPEN)) {
            CreativeFcJoinProbe.whileOpen(state, ctx.tick(), mc);
            return SelfTest.StepResult.pending();
        }
        if (state.containsKey(MenusJoinProbe.OPEN)) {
            MenusJoinProbe.whileOpen(state, ctx.tick(), mc);
            return SelfTest.StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        boolean joined = mc.level != null && mc.player != null && screen == null;
        boolean settled = joined || (mc.level == null && screen != null && !transientScreen(screen));
        String key = joined ? "world" : screen == null ? "none" : screen.getClass().getName();
        if (!key.equals(state.get("screen"))) {
            state.put("screen", key);
            state.put("since", ctx.tick());
            return SelfTest.StepResult.pending();
        }
        if (!settled || ctx.tick() - (int) state.get("since") < STABLE_TICKS) {
            return SelfTest.StepResult.pending();
        }
        if (joined && JoinTestEnv.zones() && !state.containsKey("zones")) {
            String zones = ZoneJoinProbe.observe(state, ctx.tick(), mc);
            if (zones == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("zones", zones);
        }
        if (joined && JoinTestEnv.pilotSeconds() > 0 && !state.containsKey("pilot")) {
            String pilot = pilotProbe(ctx, mc);
            if (pilot == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("pilot", pilot);
        }
        if (joined && JoinTestEnv.pilotSeconds() > 0 && !state.containsKey("camangle")) {
            String angle = CameraAngleJoinProbe.observe(state, ctx.tick(), mc);
            if (angle == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("camangle", angle);
        }
        if (joined && JoinTestEnv.trailSeconds() > 0 && !state.containsKey("trail")) {
            String trail = RemoteTrailCheck.observe(state, ctx.tick(), JoinTestEnv.trailSeconds());
            if (trail == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("trail", trail);
        }
        if (joined && JoinTestEnv.assist() && !state.containsKey("assist")) {
            String assist = AssistJoinProbe.observe(state, ctx.tick(), mc);
            if (assist == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("assist", assist);
        }
        if (joined && JoinTestEnv.stackGate() && !state.containsKey("stackgate")) {
            String stack = GateStackSelfTests.joinProbe(state, ctx.tick(), mc);
            if (stack == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("stackgate", stack);
        }
        if (joined && JoinTestEnv.signal() && !state.containsKey("signal")) {
            String signal = SignalMemorySelfTests.joinProbe(state, ctx.tick(), mc);
            if (signal == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("signal", signal);
        }
        if (joined && JoinTestEnv.gateHint() && !state.containsKey("gate_hint")) {
            String hint = GateHintJoinProbe.observe(state, ctx.tick(), mc);
            if (hint == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("gate_hint", hint);
        }
        if (joined && JoinTestEnv.spectateFpmSeconds() > 0 && !state.containsKey("spectate_fpm")) {
            String fpm = FlightPathMarkerLanCheck.observe(state, ctx.tick(), JoinTestEnv.spectateFpmSeconds());
            if (fpm == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("spectate_fpm", fpm);
        }
        if (joined && JoinTestEnv.rx() && !state.containsKey("rx")) {
            String rx = RxJoinProbe.observe(state, ctx.tick(), mc);
            if (rx == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("rx", rx);
        }
        if (joined && JoinTestEnv.plates() && !state.containsKey("plates")) {
            String plates = DroneTriggerSelfTests.plateJoinProbe(state, ctx.tick(), mc);
            if (plates == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("plates", plates);
        }
        if (joined && JoinTestEnv.multi() != null && !state.containsKey("multi")) {
            String multi = MultiJoinProbe.observe(state, ctx.tick(), mc);
            if (multi == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("multi", multi);
        }
        if (joined && JoinTestEnv.addon17() && !state.containsKey("addon17")) {
            String addon17 = Addon17JoinProbe.observe(state, ctx.tick(), mc);
            if (addon17 == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("addon17", addon17);
        }
        if (joined && JoinTestEnv.addonData() && !state.containsKey("addondata")) {
            String addonData = AddonDataJoinProbe.observe(state, ctx.tick(), mc);
            if (addonData == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("addondata", addonData);
        }
        if (joined && JoinTestEnv.collision() && !state.containsKey("collision")) {
            String collision = CollisionJoinProbe.observe(state, ctx.tick(), mc);
            if (collision == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("collision", collision);
        }
        if (joined && JoinTestEnv.share() && !state.containsKey("share")) {
            String share = ShareJoinProbe.observe(state, ctx.tick(), mc);
            if (share == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("share", share);
        }
        if (joined && JoinTestEnv.raceHome() && !state.containsKey("racehome")) {
            String raceHome = RaceHomeJoinProbe.observe(state, ctx.tick(), mc);
            if (raceHome == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("racehome", raceHome);
        }
        if (joined && JoinTestEnv.shareChat() && !state.containsKey("share_chat")) {
            String shareChat = ShareChatJoinProbe.observe(state, ctx.tick(), mc);
            if (shareChat == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("share_chat", shareChat);
        }
        if (joined && JoinTestEnv.freecam() && !state.containsKey("freecam")) {
            String freecam = FreecamJoinProbe.observe(state, ctx.tick(), mc);
            if (freecam == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("freecam", freecam);
        }
        if (joined && JoinTestEnv.raceLoop() && !state.containsKey("raceloop")) {
            String raceLoop = RaceLoopJoinProbe.observe(state, ctx.tick(), mc);
            if (raceLoop == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("raceloop", raceLoop);
        }
        if (joined && JoinTestEnv.covFreecam() && !state.containsKey("cov_freecam")) {
            String covFreecam = CovFreecamJoinProbe.observe(state, ctx.tick(), mc);
            if (covFreecam == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_freecam", covFreecam);
        }
        if (joined && JoinTestEnv.raceData() && !state.containsKey("racedata")) {
            String raceData = RaceDataJoinProbe.observe(state, ctx.tick(), mc);
            if (raceData == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("racedata", raceData);
        }
        if (joined && JoinTestEnv.operator() && !state.containsKey("operator")) {
            String operator = OperatorJoinProbe.observe(state, ctx.tick(), mc);
            if (operator == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("operator", operator);
        }
        if (joined && JoinTestEnv.accFix() && !state.containsKey("accfix")) {
            String accFix = AccFixJoinProbe.observe(state, ctx.tick(), mc);
            if (accFix == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("accfix", accFix);
        }
        if (joined && JoinTestEnv.covSurface() && !state.containsKey("cov_surface")) {
            String covSurface = CovSurfaceJoinProbe.observe(state, ctx.tick(), mc);
            if (covSurface == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_surface", covSurface);
        }
        if (joined && JoinTestEnv.covLanding() && !state.containsKey("cov_landing")) {
            String covLanding = CovLandingJoinProbe.observe(state, ctx.tick(), mc);
            if (covLanding == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_landing", covLanding);
        }
        if (joined && JoinTestEnv.covHeat() && !state.containsKey("cov_heat")) {
            String covHeat = CovHeatJoinProbe.observe(state, ctx.tick(), mc);
            if (covHeat == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_heat", covHeat);
        }
        if (joined && JoinTestEnv.covCamangle() && !state.containsKey("cov_camangle")) {
            String covCamangle = CovCameraAngleLanCheck.observe(state, ctx.tick(), mc);
            if (covCamangle == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_camangle", covCamangle);
        }
        if (joined && JoinTestEnv.covRx() > 0 && !state.containsKey("cov_rx")) {
            String covRx = CovRxJoinProbe.observe(state, ctx.tick(), mc);
            if (covRx == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_rx", covRx);
        }
        if (joined && JoinTestEnv.covWatchRescue() && !state.containsKey("cov_watchrescue_text")) {
            String covWatch = CovWatchRescueJoinProbe.observe(state, ctx.tick(), mc);
            if (covWatch == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_watchrescue_text", covWatch);
        }
        if (joined && JoinTestEnv.covLanGuest() && !state.containsKey("cov_languest")) {
            String covLan = CovLanGuestJoinProbe.observe(state, ctx.tick(), mc);
            if (covLan == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_languest", covLan);
        }
        if (joined && JoinTestEnv.droneScale() && !state.containsKey("drone_scale")) {
            String droneScale = DroneScaleJoinProbe.observe(state, ctx.tick(), mc);
            if (droneScale == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("drone_scale", droneScale);
        }
        if (joined && JoinTestEnv.barriers() && !state.containsKey("barriers")) {
            String barriers = BarrierJoinProbe.observe(state, ctx.tick(), mc);
            if (barriers == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("barriers", barriers);
        }
        if (joined && JoinTestEnv.zonesOpen() && !state.containsKey("zones_open")) {
            String zonesOpen = ZonesOpenJoinProbe.observe(state, ctx.tick(), mc);
            if (zonesOpen == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("zones_open", zonesOpen);
        }
        if (joined && JoinTestEnv.covAddonRules() && !state.containsKey("cov_addonrules")) {
            String covRules = CovAddonRulesJoinProbe.observe(state, ctx.tick(), mc);
            if (covRules == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_addonrules", covRules);
        }
        if (joined && JoinTestEnv.covNoAddon() && !state.containsKey("cov_noaddon")) {
            String covNoAddon = CovNoAddonJoinProbe.observe(state, ctx.tick(), mc);
            if (covNoAddon == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_noaddon", covNoAddon);
        }
        if (joined && JoinTestEnv.covNsOp() && !state.containsKey("cov_nsop")) {
            String covNsOp = CovGuardJoinProbes.nsOp(state, ctx.tick(), mc);
            if (covNsOp == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_nsop", covNsOp);
        }
        if (joined && JoinTestEnv.covNoclip() && !state.containsKey("cov_noclip")) {
            String covNoclip = CovGuardJoinProbes.noclip(state, ctx.tick(), mc);
            if (covNoclip == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_noclip", covNoclip);
        }
        if (joined && JoinTestEnv.covRace() > 0 && !state.containsKey("cov_race")) {
            String covRace = CovRaceJoinProbe.observe(state, ctx.tick(), mc);
            if (covRace == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_race", covRace);
        }
        if (joined && JoinTestEnv.autoArm() && !state.containsKey("autoarm")) {
            String autoArm = AutoArmJoinProbe.observe(state, ctx.tick(), mc);
            if (autoArm == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("autoarm", autoArm);
        }
        if (joined && JoinTestEnv.raceRules() && !state.containsKey("race_rules")) {
            String raceRules = RaceRulesJoinProbe.observe(state, ctx.tick(), mc);
            if (raceRules == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("race_rules", raceRules);
        }
        if (joined && JoinTestEnv.covWebexport() && !state.containsKey("cov_webexport")) {
            String covWebexport = CovWebexportJoinProbe.observe(state, ctx.tick(), mc);
            if (covWebexport == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_webexport", covWebexport);
        }
        if (joined && JoinTestEnv.covShare() && !state.containsKey("cov_share")) {
            String covShare = CovShareJoinProbe.observe(state, ctx.tick(), mc);
            if (covShare == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_share", covShare);
        }
        if (joined && JoinTestEnv.covChatShare() && !state.containsKey("cov_chatshare")) {
            String covChatShare = CovChatShareJoinProbe.observe(state, ctx.tick(), mc);
            if (covChatShare == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_chatshare", covChatShare);
        }
        if (joined && JoinTestEnv.raceP2p() && !state.containsKey("race_p2p")) {
            String raceP2p = RaceP2pJoinProbe.observe(state, ctx.tick(), mc);
            if (raceP2p == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("race_p2p", raceP2p);
        }
        if (joined && JoinTestEnv.autonomyOffline() > 0 && !state.containsKey("autonomy_offline")) {
            String autonomyOffline = AutonomyOfflineJoinProbe.observe(state, ctx.tick(), mc);
            if (autonomyOffline == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("autonomy_offline", autonomyOffline);
        }
        if (joined && JoinTestEnv.droneRotation() && !state.containsKey("drone_rotation")) {
            String droneRotation = DroneRotationJoinProbe.observe(state, ctx.tick(), mc);
            if (droneRotation == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("drone_rotation", droneRotation);
        }
        if (joined && JoinTestEnv.gatePush() && !state.containsKey("gatepush")) {
            String gatePush = GatePushJoinProbe.observe(state, ctx.tick(), mc);
            if (gatePush == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("gatepush", gatePush);
        }
        if (joined && JoinTestEnv.raceGhosts() && !state.containsKey("race_ghosts")) {
            String raceGhosts = RaceGhostsJoinProbe.observe(state, ctx.tick(), mc);
            if (raceGhosts == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("race_ghosts", raceGhosts);
        }
        if (joined && JoinTestEnv.stats() && !state.containsKey("stats")) {
            String stats = StatsJoinProbe.observe(state, ctx.tick(), mc);
            if (stats == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("stats", stats);
        }
        if (joined && JoinTestEnv.capture() && !state.containsKey("capture_probe")) {
            String capture = CaptureJoinProbe.observe(state, ctx.tick(), mc);
            if (capture == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("capture_probe", capture);
        }
        if (joined && JoinTestEnv.courseMenu() && !state.containsKey("course_menu")) {
            String courseMenu = CourseMenuJoinProbe.observe(state, ctx.tick(), mc);
            if (courseMenu == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("course_menu", courseMenu);
        }
        if (joined && JoinTestEnv.specCount() > 0 && !state.containsKey(SpecCountJoinProbe.KEY)) {
            String specCount = SpecCountJoinProbe.observe(state, ctx.tick(), mc);
            if (specCount == null) {
                return SelfTest.StepResult.pending();
            }
            state.put(SpecCountJoinProbe.KEY, specCount);
        }
        if (joined && JoinTestEnv.creativeFc() && !state.containsKey(CreativeFcJoinProbe.KEY)) {
            String creativeFc = CreativeFcJoinProbe.observe(state, ctx.tick(), mc);
            if (creativeFc == null) {
                return SelfTest.StepResult.pending();
            }
            state.put(CreativeFcJoinProbe.KEY, creativeFc);
        }
        if (joined && JoinTestEnv.covCrashRules() && !state.containsKey("cov_crashrules")) {
            String covCrashRules = CovCrashRulesRun.observe(state, ctx.tick());
            if (covCrashRules == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_crashrules", covCrashRules);
        }
        if (joined && JoinTestEnv.covVine() && !state.containsKey("cov_vine")) {
            String covVine = CovVineJoinProbe.observe(state, ctx.tick(), mc);
            if (covVine == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_vine", covVine);
        }
        if (joined && JoinTestEnv.droneSound() && !state.containsKey("drone_sound")) {
            String droneSound = DroneSoundJoinProbe.observe(state, ctx.tick(), mc);
            if (droneSound == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("drone_sound", droneSound);
        }
        if (joined && JoinTestEnv.covCrashWall() && !state.containsKey("cov_crashwall")) {
            String covCrashWall = CovCrashWallRun.observe(state, ctx.tick());
            if (covCrashWall == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("cov_crashwall", covCrashWall);
        }
        if (joined && JoinTestEnv.menus() && !state.containsKey(MenusJoinProbe.KEY)) {
            String menus = MenusJoinProbe.observe(state, ctx.tick(), mc);
            if (menus == null) {
                return SelfTest.StepResult.pending();
            }
            state.put(MenusJoinProbe.KEY, menus);
        }
        String text = joined ? JoinTestEnv.JOINED + "\n" + AddonSessionSelfTests.sessionReport(mc) + state.getOrDefault("pilot", "") + state.getOrDefault("camangle", "") + state.getOrDefault("trail", "")
                + state.getOrDefault("assist", "") + state.getOrDefault("zones", "") + state.getOrDefault("spectate_fpm", "") + state.getOrDefault("rx", "")
                + state.getOrDefault("plates", "") + state.getOrDefault("stackgate", "") + state.getOrDefault("signal", "")
                + state.getOrDefault("gate_hint", "") + state.getOrDefault("multi", "") + state.getOrDefault("addon17", "") + state.getOrDefault("addondata", "") + state.getOrDefault("collision", "") + state.getOrDefault("share", "")
                + state.getOrDefault("racehome", "") + state.getOrDefault("share_chat", "")
                + state.getOrDefault("freecam", "") + state.getOrDefault("raceloop", "") + state.getOrDefault("cov_freecam", "") + state.getOrDefault("racedata", "")
                + state.getOrDefault("operator", "") + state.getOrDefault("accfix", "") + state.getOrDefault("cov_surface", "") + state.getOrDefault("cov_landing", "")
                + state.getOrDefault("cov_heat", "") + state.getOrDefault("cov_camangle", "") + state.getOrDefault("cov_rx", "")
                + state.getOrDefault("cov_watchrescue_text", "") + state.getOrDefault("cov_languest", "") + state.getOrDefault("drone_scale", "") + state.getOrDefault("barriers", "")
                + state.getOrDefault("zones_open", "")
                + state.getOrDefault("cov_addonrules", "") + state.getOrDefault("cov_noaddon", "") + state.getOrDefault("cov_nsop", "")
                + state.getOrDefault("cov_noclip", "")
                + state.getOrDefault("cov_race", "")
                + state.getOrDefault("autoarm", "")
                + state.getOrDefault("race_rules", "")
                + state.getOrDefault("cov_webexport", "") + state.getOrDefault("cov_share", "") + state.getOrDefault("cov_chatshare", "")
                + state.getOrDefault("race_p2p", "")
                + state.getOrDefault("autonomy_offline", "")
                + state.getOrDefault("drone_rotation", "")
                + state.getOrDefault("gatepush", "")
                + state.getOrDefault("race_ghosts", "")
                + state.getOrDefault("stats", "")
                + state.getOrDefault("capture_probe", "")
                + state.getOrDefault("course_menu", "")
                + state.getOrDefault(SpecCountJoinProbe.KEY, "")
                + state.getOrDefault(CreativeFcJoinProbe.KEY, "")
                + state.getOrDefault("cov_crashrules", "")
                + state.getOrDefault("cov_vine", "")
                + state.getOrDefault("drone_sound", "")
                + state.getOrDefault("cov_crashwall", "")
                + state.getOrDefault(MenusJoinProbe.KEY, "")
                + (JoinTestEnv.crossLoader() ? CrossLoaderJoinProbe.report() : "")
                : describe(screen);
        state.put("text", text);
        write(JoinTestEnv.label() + ".txt", text);
        Constants.LOG.info("[Beitrittstest] {}: {}", JoinTestEnv.label(), text.replace("\n", " | "));
        state.put("capture", capture(JoinTestEnv.label()));
        return SelfTest.StepResult.pending();
    }

    private static @Nullable String pilotProbe(SelfTest.StepContext ctx, Minecraft mc) {
        Map<String, Object> state = ctx.state();
        LocalPlayer player = mc.player;
        if (player == null) {
            return null;
        }
        if (!state.containsKey("fpvAt")) {
            if (PilotSession.INSTANCE.isFpv()) {
                state.put("fpvAt", ctx.tick());
                state.put("fpvY", player.getY());
                return null;
            }
            return ctx.tick() > PILOT_WAIT_TICKS ? "pilot no session\n" : null;
        }
        if (player.onGround()) {
            state.put("landed", true);
        }
        state.merge("minHealth", player.getHealth(), (a, b) -> Math.min((float) a, (float) b));
        if (ctx.tick() - (int) state.get("fpvAt") < JoinTestEnv.pilotSeconds() * 20) {
            return null;
        }
        double fell = (double) state.get("fpvY") - player.getY();
        return "pilot fpv " + (PilotSession.INSTANCE.isFpv() ? "held" : "ended") + "\nbody "
                + (state.containsKey("landed") && player.onGround() ? "landed" : "floating")
                + String.format(Locale.ROOT, " fell %.2f%n", fell)
                + ((float) state.get("minHealth") < player.getMaxHealth() - 0.5F ? "body hurt" : "body unhurt")
                + String.format(Locale.ROOT, " lowest %.1f now %.1f%n", (float) state.get("minHealth"), player.getHealth());
    }

    private static SelfTest.StepResult evaluate(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String expected : JoinTestEnv.expectations()) {
            boolean negate = expected.startsWith("!");
            String needle = (negate ? expected.substring(1) : expected).toLowerCase(Locale.ROOT);
            if (lower.contains(needle) == negate) {
                return SelfTest.StepResult.fail((negate ? "unerwartet: " : "fehlt: ") + needle);
            }
        }
        return SelfTest.StepResult.pass();
    }

    private static boolean transientScreen(Screen screen) {
        return screen instanceof ConnectScreen || screen instanceof GenericMessageScreen || screen instanceof GenericWaitingScreen
                || screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen;
    }

    public static String describe(@Nullable Screen screen) {
        if (screen == null) {
            return "kein Screen\n";
        }
        StringBuilder out = new StringBuilder();
        out.append("screen=").append(screen.getClass().getName()).append('\n');
        out.append("title=").append(screen.getTitle().getString()).append('\n');
        for (Method method : screen.getClass().getMethods()) {
            if (method.getName().equals("detailsText") && method.getParameterCount() == 0 && method.getReturnType() == String.class) {
                try {
                    out.append("details=\n").append(method.invoke(screen)).append('\n');
                } catch (ReflectiveOperationException e) {
                    out.append("details: ").append(e).append('\n');
                }
            }
        }
        for (Class<?> type = screen.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    appendValue(out, type.getSimpleName() + "." + field.getName(), field.get(screen));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    continue;
                }
            }
        }
        return out.toString();
    }

    private static void appendValue(StringBuilder out, String name, @Nullable Object value) {
        switch (value) {
            case null -> {
            }
            case Component component -> out.append(name).append('=').append(component.getString()).append('\n');
            case DisconnectionDetails details -> out.append(name).append(".reason=").append(details.reason().getString()).append('\n');
            case Map<?, ?> map -> {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getValue() instanceof Component component) {
                        out.append(name).append('[').append(entry.getKey()).append("]=").append(component.getString()).append('\n');
                    }
                }
            }
            case Collection<?> list -> {
                int index = 0;
                for (Object item : list) {
                    if (item instanceof Component component) {
                        out.append(name).append('[').append(index).append("]=").append(component.getString()).append('\n');
                    }
                    index++;
                }
            }
            default -> {
            }
        }
    }

    private static CompletableFuture<String> capture(String label) {
        CompletableFuture<String> done = new CompletableFuture<>();
        Path file = Services.PLATFORM.getGameDir().resolve("jointest").resolve(label + ".png");
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            done.complete("kein Ordner: " + e);
            return done;
        }
        RenderTarget renderTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        Screenshot.takeScreenshot(renderTarget, 1, image -> Util.ioPool().execute(() -> {
            try (image) {
                image.writeToFile(file);
                done.complete(file.toString());
            } catch (Exception e) {
                done.complete("Bildschirmfoto fehlgeschlagen: " + e);
            }
        }));
        return done;
    }

    private static void write(String name, String text) {
        try {
            Path dir = Services.PLATFORM.getGameDir().resolve("jointest");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.error("[Beitrittstest] {} nicht geschrieben", name, e);
        }
    }
}
