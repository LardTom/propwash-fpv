package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.video.LinkBudget;
import de.lardtom.propwash.video.VtxTables;
import de.lardtom.propwash.video.vtx.VtxPowerSettings;
import de.lardtom.propwash.video.vtx.VtxTable;
import de.lardtom.propwash.video.vtx.VtxUnit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class VtxPowerSelfTests {
    static final double RANGE_DISTANCE = 32.0;
    static final double NEAR = 5.0;
    private static final int SETTLE_TICKS = 30;
    private static final int[] LEVELS = {400, 25, 1600};
    private static final String FUTURE = "vtx_future";
    private static final String PHASE = "vtx_phase";
    private static final String SINCE = "vtx_since";

    private VtxPowerSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("vtx-leistung: Reichweite je Stufe", 1200, ctx -> rangeStep(ctx, false));
        SelfTest.clientStep("vtx-leistung: Wand – mehr Leistung dringt durch", 1500, ctx -> rangeStep(ctx, true));
        SelfTest.clientStep("vtx-leistung: Mehrstrom, Taste und OSD im Flug", 900, VtxPowerSelfTests::flightStep);
        SelfTest.clientStep("vtx-leistung: Hitzeschutz und Warnung", 600, VtxPowerSelfTests::heatStep);
        SelfTest.clientStep("vtx-leistung: Auto regelt digital herunter", 900, VtxPowerSelfTests::autoStep);
        SelfTest.clientStep("vtx-leistung: aufräumen", 300, VtxPowerSelfTests::cleanupStep);
    }

    static StepResult startFlight(SelfTest.StepContext ctx, DroneBuild build, Identifier goggles, Map<String, Float> tune, double distance) {
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(server -> VtxFixture.spawn(server, build, goggles, tune, distance, true)));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Testflug nicht gestartet: " + future.exceptionNow().getMessage());
        }
        if (VtxFixture.piloted() == null) {
            return ctx.tick() > 300 ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        return StepResult.pass();
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void enter(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, ctx.tick());
    }

    static void pressStep() {
        KeyMapping.click(InputConstants.getKey(VtxKeys.STEP.saveString()));
    }

    private static StepResult rangeStep(SelfTest.StepContext ctx, boolean wall) {
        VideoState state = VideoState.INSTANCE;
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                VtxFixture.spawn(server, DroneBuild.RACER, ContentIds.GOGGLES_ANALOG, Map.of(), RANGE_DISTANCE, false);
                if (wall) {
                    VtxFixture.buildWall(server, RANGE_DISTANCE / 2.0);
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> spawn = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!spawn.isDone()) {
            return StepResult.pending();
        }
        if (spawn.isCompletedExceptionally()) {
            state.attachTestSource(null);
            return StepResult.fail("Drohne spawnen fehlgeschlagen: " + spawn.exceptionNow().getMessage());
        }
        QuadcopterEntity drone = VtxFixture.clientDrone();
        if (drone == null) {
            return ctx.tick() > 200 ? StepResult.fail("Testdrohne auf dem Client nicht sichtbar") : StepResult.pending();
        }
        state.attachTestSource(drone);
        int index = phase(ctx);
        int level = LEVELS[index];
        if (!ctx.state().containsKey(SINCE)) {
            enter(ctx, index);
            if (level != VtxTables.of(drone.build().video()).ratedMw()) {
                ctx.onServer(server -> VtxFixture.setTune(server, VtxPowerSettings.withLevel(Map.of(), level)));
            }
            return StepResult.pending();
        }
        if (VtxTables.configuredMw(drone.tune().values(), drone.build().video()) != level) {
            return since(ctx) > 200 ? StepResult.fail("Leistung " + level + " mW kommt nicht an der Drohne an") : StepResult.pending();
        }
        if (since(ctx) < (wall && index == 0 ? SETTLE_TICKS * 3 : SETTLE_TICKS)) {
            return StepResult.pending();
        }
        if (state.status() != VideoState.LinkStatus.CONNECTED) {
            state.attachTestSource(null);
            return StepResult.fail("keine Videoverbindung: " + state.status());
        }
        double gain = state.powerGainDb();
        double expectedGain = 10.0 * Math.log10(level / 400.0);
        double margin = state.ratedMarginDb(drone);
        double obstruction = state.obstructionDb();
        float raw = state.rawQuality();
        double expected = LinkBudget.quality(VideoSystem.ANALOG, margin + gain);
        ctx.log(level + " mW: Gewinn " + String.format("%.2f", gain) + " dB, Reserve bei Nennleistung " + String.format("%.1f", margin)
                + " dB, Dämpfung " + String.format("%.1f", state.obstructionDb()) + " dB, Signal " + String.format("%.3f", raw));
        if (Math.abs(gain - expectedGain) > 0.01) {
            state.attachTestSource(null);
            return StepResult.fail("Gewinn bei " + level + " mW " + gain + " dB statt " + expectedGain);
        }
        if (Double.isNaN(margin) || Math.abs(raw - expected) > 0.02) {
            state.attachTestSource(null);
            return StepResult.fail("Signal bei " + level + " mW " + raw + " statt " + expected + " (Reserve " + margin + " dB)");
        }
        ctx.state().put("q" + level, raw);
        ctx.state().remove(SINCE);
        if (index + 1 < LEVELS.length) {
            ctx.state().put(PHASE, index + 1);
            return StepResult.pending();
        }
        state.attachTestSource(null);
        if (wall) {
            ctx.onServer(VtxFixture::clearWall);
        }
        float low = (float) ctx.state().get("q25");
        float rated = (float) ctx.state().get("q400");
        float high = (float) ctx.state().get("q1600");
        if (wall && obstruction < 6.0) {
            return StepResult.fail("Wand dämpft kaum: " + obstruction + " dB");
        }
        boolean distinct = LinkBudget.quality(VideoSystem.ANALOG, margin + 6.02) > LinkBudget.quality(VideoSystem.ANALOG, margin - 12.04) + 0.05;
        if (!(low <= rated && rated <= high) || (distinct && !(low < high - 0.05F))) {
            return StepResult.fail("Signal steigt nicht mit der Leistung: 25 mW " + low + ", 400 mW " + rated + ", 1600 mW " + high);
        }
        return StepResult.pass();
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        int phase = phase(ctx);
        if (phase == 0) {
            StepResult started = startFlight(ctx, DroneBuild.RACER, ContentIds.GOGGLES_ANALOG, Map.of(), NEAR);
            if (started.status() != StepResult.Status.PASS) {
                return started;
            }
            enter(ctx, 1);
            return StepResult.pending();
        }
        QuadcopterEntity drone = VtxFixture.piloted();
        DroneSim sim = PilotSession.INSTANCE.sim();
        VtxUnit unit = VtxRuntime.INSTANCE.unit(drone);
        if (drone == null || sim == null || unit == null) {
            return StepResult.fail("Flug mit der Testdrohne endete");
        }
        OsdData data = OsdHud.sampler().data();
        if (phase == 1) {
            if (since(ctx) < 20) {
                return StepResult.pending();
            }
            if (!unit.auto() || !data.vtxPowerAuto || drone.tune().values().containsKey(VtxPowerSettings.POWER)) {
                return StepResult.fail("Drohne ohne Einstellung sendet nicht mit Auto: " + unit.effectiveMw() + " mW, OSD Auto " + data.vtxPowerAuto);
            }
            if (unit.effectiveMw() != 25 || data.vtxPowerMw != 25) {
                return since(ctx) > 240 ? StepResult.fail("Auto regelt analog nah am Piloten nicht herunter: " + unit.effectiveMw() + " mW")
                        : StepResult.pending();
            }
            ctx.log("Auto analog nah am Piloten: 25 mW nach " + since(ctx) + " Ticks, Mehrlast " + String.format("%.2f", sim.videoPowerDelta()) + " W");
            ctx.state().put(FUTURE, ctx.onServer(server -> VtxFixture.setTune(server, VtxPowerSettings.withLevel(Map.of(), 400))));
            enter(ctx, 3);
            return StepResult.pending();
        }
        if (phase == 3) {
            if (unit.auto() || unit.effectiveMw() != 400) {
                return since(ctx) > 200 ? StepResult.fail("feste Nennleistung kommt nicht an: " + unit.effectiveMw() + " mW, Auto " + unit.auto())
                        : StepResult.pending();
            }
            if (since(ctx) < 10) {
                return StepResult.pending();
            }
            if (sim.videoPowerDelta() != 0.0 || data.vtxPowerMw != 400 || data.vtxPowerAuto) {
                return StepResult.fail("feste Nennleistung nicht wie vorher: Mehrlast " + sim.videoPowerDelta() + " W, OSD " + data.vtxPowerMw + " mW");
            }
            ctx.state().remove(FUTURE);
            pressStep();
            enter(ctx, 2);
            return StepResult.pending();
        }
        Float stored = drone.tune().values().get(VtxPowerSettings.POWER);
        boolean echoed = stored != null && stored == 800.0F;
        if (unit.effectiveMw() != 800 || !echoed || data.vtxPowerMw != 800) {
            return since(ctx) > 200 ? StepResult.fail("Taste schaltet nicht auf 800 mW: Sender " + unit.effectiveMw() + " mW, Drohne " + stored
                    + ", OSD " + data.vtxPowerMw) : StepResult.pending();
        }
        double expected = VtxTables.of(drone.build().video()).extraW(3);
        if (Math.abs(sim.videoPowerDelta() - expected) > 1.0E-9 || Math.abs(expected - 4.3 * 0.4) > 1.0E-9) {
            return StepResult.fail("Mehrlast " + sim.videoPowerDelta() + " W statt " + expected + " W");
        }
        ctx.log("800 mW: Mehrlast " + String.format("%.2f", sim.videoPowerDelta()) + " W, Sender " + String.format("%.1f", unit.tempC()) + " °C");
        return StepResult.pass();
    }

    private static StepResult heatStep(SelfTest.StepContext ctx) {
        int phase = phase(ctx);
        QuadcopterEntity drone = VtxFixture.piloted();
        VtxUnit unit = VtxRuntime.INSTANCE.unit(drone);
        if (drone == null || unit == null) {
            return StepResult.fail("kein Testflug aus dem vorigen Schritt");
        }
        if (phase == 0) {
            pressStep();
            enter(ctx, 1);
            return StepResult.pending();
        }
        if (phase == 1) {
            if (unit.effectiveMw() != 1600) {
                return since(ctx) > 200 ? StepResult.fail("Taste schaltet nicht auf 1600 mW: " + unit.effectiveMw()) : StepResult.pending();
            }
            VtxRuntime.INSTANCE.overrideTestTemperature(92.0);
            enter(ctx, 2);
            return StepResult.pending();
        }
        if (since(ctx) < 6) {
            return StepResult.pending();
        }
        OsdData data = OsdHud.sampler().data();
        List<OsdWarnings.Warning> warnings = new ArrayList<>();
        OsdWarnings.collect(data, warnings);
        boolean ok = unit.throttled() && unit.hot() && unit.effectiveMw() == 800 && data.vtxThrottled && data.vtxHot && data.vtxPowerMw == 800
                && warnings.contains(OsdWarnings.Warning.VTX_HOT);
        if (!ok) {
            return since(ctx) > 100 ? StepResult.fail("Hitzeschutz greift nicht: Sender " + unit.effectiveMw() + " mW, " + unit.tempC() + " °C, gedrosselt "
                    + unit.throttled() + ", OSD " + data.vtxPowerMw + " mW heiß " + data.vtxHot + ", Warnungen " + warnings) : StepResult.pending();
        }
        ctx.log("Hitzeschutz: " + String.format("%.1f", unit.tempC()) + " °C, Sender auf " + unit.effectiveMw() + " mW, Warnung " + warnings);
        VtxRuntime.INSTANCE.overrideTestTemperature(22.0);
        return StepResult.pass();
    }

    private static StepResult autoStep(SelfTest.StepContext ctx) {
        int phase = phase(ctx);
        if (phase == 0) {
            StepResult started = startFlight(ctx, DroneBuild.FREESTYLE, ContentIds.GOGGLES_DIGITAL, VtxPowerSettings.withAuto(Map.of()), NEAR);
            if (started.status() != StepResult.Status.PASS) {
                return started;
            }
            enter(ctx, 1);
            return StepResult.pending();
        }
        QuadcopterEntity drone = VtxFixture.piloted();
        DroneSim sim = PilotSession.INSTANCE.sim();
        VtxUnit unit = VtxRuntime.INSTANCE.unit(drone);
        if (drone == null || sim == null || unit == null) {
            return StepResult.fail("Flug mit der digitalen Testdrohne endete");
        }
        if (!unit.auto()) {
            return StepResult.fail("Auto nicht aktiv");
        }
        OsdData data = OsdHud.sampler().data();
        if (unit.effectiveMw() != 25 || data.vtxPowerMw != 25 || !data.vtxPowerAuto) {
            return since(ctx) > 600 ? StepResult.fail("Auto regelt nah am Piloten nicht herunter: " + unit.effectiveMw() + " mW, OSD "
                    + data.vtxPowerMw + " (Auto " + data.vtxPowerAuto + "), Reserve " + VideoState.INSTANCE.ratedMarginDb(drone) + " dB")
                    : StepResult.pending();
        }
        VtxTable table = VtxTables.of(drone.build().video());
        if (Math.abs(sim.videoPowerDelta() - table.extraW(0)) > 1.0E-9 || !(sim.videoPowerDelta() < 0.0)) {
            return StepResult.fail("Auto auf 25 mW spart keinen Strom: " + sim.videoPowerDelta() + " W");
        }
        ctx.log("Auto nach " + since(ctx) + " Ticks auf 25 mW, Ersparnis " + String.format("%.2f", -sim.videoPowerDelta()) + " W");
        return StepResult.pass();
    }

    static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() != null) {
                minecraft.gui.setScreen(null);
            }
            VideoState.INSTANCE.attachTestSource(null);
            VtxRuntime.INSTANCE.overrideTestTemperature(22.0);
            ctx.state().put(FUTURE, ctx.onServer(VtxFixture::release));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        return PilotSession.INSTANCE.isActive() ? (ctx.tick() > 150 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending()) : StepResult.pass();
    }
}
