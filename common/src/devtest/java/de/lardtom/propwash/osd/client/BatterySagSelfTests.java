package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.osd.VoltageDisplay;
import de.lardtom.propwash.sim.BatteryState;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;

import java.util.Locale;
import java.util.Map;

public final class BatterySagSelfTests {
    private static final long TICK_NANOS = 50_000_000L;
    private static final double HOVER_STICK = 0.25;
    private static final int HOVER_TICKS = 40;
    private static final int PUNCH_TICKS = 20;
    private static final int REST_TICKS = 40;
    private static final int SCENE_PUNCH_TICKS = 56;
    private static final double FULL = 1.0;
    private static final double LOW = 0.08;
    private static final int BLINK_WAIT_TICKS = 30;
    private static final double BLINK_PERIOD = 0.8;
    private static final double BLINK_EARLY = 0.15;
    private static final String SOURCE = "sag_source";
    private static final String FRESH = "sag_fresh";

    private BatterySagSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("akku-sag: fast leerer Akku bricht im Punch stärker ein als ein voller, das OSD zeigt Einbruch und Erholung",
                2 * (HOVER_TICKS + PUNCH_TICKS + REST_TICKS) + 80, BatterySagSelfTests::sagStep);
    }

    public static void registerShowcase() {
        Screenshots.step("bildschirmfotos: Akku-Sag – voller Akku im Punch (OSD)", "battery_sag_full", 2, new PunchScene(FULL));
        Screenshots.step("bildschirmfotos: Akku-Sag – fast leerer Akku im Punch (OSD)", "battery_sag_low", 2, new PunchScene(LOW));
    }

    private static final class Flight implements OsdSampler.Source {
        private final DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 7L);
        private final PilotInput input = new PilotInput();
        private final SimEnvironment environment = new SimEnvironment();
        private final de.lardtom.propwash.sim.StepResult result = new de.lardtom.propwash.sim.StepResult();
        private final Vector3d position = new Vector3d();
        private final double homeX;
        private final double homeY;
        private final double homeZ;
        private int ticks;
        private double before = Double.NaN;
        private double lowest = Double.MAX_VALUE;
        private double shownLowest = Double.MAX_VALUE;
        private double thrust;
        private double current;
        private double afterShort = Double.NaN;
        private double afterLong = Double.NaN;
        private boolean warned;
        private boolean inPunch;
        private int flashed;

        Flight(double charge, double homeX, double homeY, double homeZ) {
            this.homeX = homeX;
            this.homeY = homeY;
            this.homeZ = homeZ;
            this.sim.reset(homeX, homeY + 150.0, homeZ, 0.0);
            this.sim.setBatteryCharge(charge);
            this.sim.step(0L, this.input, this.environment, this.result);
            this.input.armSwitch = true;
            this.sim.step(0L, this.input, this.environment, this.result);
            this.input.throttle = HOVER_STICK;
        }

        void tick(int punchTicks) {
            int t = this.ticks++;
            boolean punch = t >= HOVER_TICKS && t < HOVER_TICKS + punchTicks;
            this.inPunch = punch;
            this.input.throttle = punch ? 1.0 : HOVER_STICK;
            this.sim.step(TICK_NANOS, this.input, this.environment, this.result);
            Telemetry telemetry = this.sim.telemetry();
            double cell = telemetry.batteryCellVoltage;
            if (t == HOVER_TICKS - 1) {
                this.before = cell;
            }
            OsdData shown = OsdHud.sampler().data();
            if (punch && shown.active && Double.isFinite(shown.cellVoltage)) {
                this.shownLowest = Math.min(this.shownLowest, shown.cellVoltage);
                this.flashed = Math.max(this.flashed, shown.voltageAlert);
            }
            if (punch) {
                this.lowest = Math.min(this.lowest, cell);
                this.warned |= telemetry.batteryState != BatteryState.OK;
                if (t == HOVER_TICKS + 9) {
                    this.thrust = telemetry.totalThrustN;
                    this.current = telemetry.batteryCurrentA;
                }
            }
            if (t == HOVER_TICKS + punchTicks + 1) {
                this.afterShort = cell;
            }
            if (t == HOVER_TICKS + punchTicks + REST_TICKS - 1) {
                this.afterLong = cell;
            }
        }

        double sag() {
            return this.before - this.lowest;
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void fill(OsdData d) {
            d.applySim(this.sim);
            this.sim.getPosition(this.position);
            d.hasPosition = true;
            d.x = this.position.x;
            d.y = this.position.y;
            d.z = this.position.z;
            d.hasHome = true;
            d.homeX = this.homeX;
            d.homeY = this.homeY;
            d.homeZ = this.homeZ;
            d.craftName = "Selftest";
            d.radioConnected = true;
            d.linkStrength = 0.9;
            d.videoSystem = VideoSystem.ANALOG;
            d.videoSignal = 0.8;
            d.stickThrottle = (float) this.input.throttle;
        }
    }

    private static Flight start(double charge) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            throw new IllegalStateException("kein Spieler");
        }
        Flight flight = new Flight(charge, player.getX(), player.getY(), player.getZ());
        OsdHud.sampler().setOverride(flight);
        return flight;
    }

    private static StepResult sagStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(SOURCE)) {
            state.put(SOURCE, start(state.containsKey(FRESH) ? LOW : FULL));
            return StepResult.pending();
        }
        Flight flight = (Flight) state.get(SOURCE);
        flight.tick(PUNCH_TICKS);
        if (flight.ticks < HOVER_TICKS + PUNCH_TICKS + REST_TICKS) {
            return StepResult.pending();
        }
        OsdHud.sampler().setOverride(null);
        if (!flight.sim.telemetry().armed) {
            return StepResult.fail("Drohne hat im Punch abgeschaltet");
        }
        if (!state.containsKey(FRESH)) {
            state.put(FRESH, flight);
            state.remove(SOURCE);
            return StepResult.pending();
        }
        Flight fresh = (Flight) state.get(FRESH);
        String numbers = String.format(Locale.ROOT, "voll: %.2f V → %.2f V (−%.2f V je Zelle, %.0f A, %.1f N); fast leer: %.2f V → %.2f V (−%.2f V, "
                        + "%.0f A, %.1f N), 0,1 s danach fehlen %.2f V, 2 s danach %.2f V; im OSD tiefster Wert %.2f V, OSD-Warnstufe voll %d, "
                        + "fast leer %d", fresh.before, fresh.lowest, fresh.sag(), fresh.current, fresh.thrust, flight.before, flight.lowest, flight.sag(),
                flight.current, flight.thrust, flight.before - flight.afterShort, flight.before - flight.afterLong, flight.shownLowest, fresh.flashed,
                flight.flashed);
        if (!(fresh.sag() > 0.25 && fresh.sag() < 0.7)) {
            return StepResult.fail("voller Akku bricht nicht plausibel ein: " + numbers);
        }
        if (!(flight.sag() > 1.2 * fresh.sag() && flight.sag() > 0.5 && flight.sag() < 1.1)) {
            return StepResult.fail("fast leerer Akku bricht nicht deutlich stärker ein: " + numbers);
        }
        if (!(flight.thrust < 0.85 * fresh.thrust && flight.current < fresh.current)) {
            return StepResult.fail("Schub folgt der eingebrochenen Spannung nicht: " + numbers);
        }
        double shortGap = flight.before - flight.afterShort;
        double longGap = flight.before - flight.afterLong;
        if (!(shortGap > 0.15 * flight.sag() && shortGap < 0.8 * flight.sag() && longGap < 0.6 * shortGap)) {
            return StepResult.fail("Spannung erholt sich nicht in zwei Stufen: " + numbers);
        }
        if (!(flight.shownLowest < flight.before - 0.5 * flight.sag())) {
            return StepResult.fail("OSD zeigt den Einbruch nicht: " + numbers);
        }
        if (fresh.warned) {
            return StepResult.fail("voller Akku warnt in einem Punch von 1 s: " + numbers);
        }
        if (fresh.flashed != VoltageDisplay.NONE) {
            return StepResult.fail("OSD-Spannung blinkt schon beim vollen Akku: " + numbers);
        }
        if (flight.flashed == VoltageDisplay.NONE) {
            return StepResult.fail("OSD-Spannung blinkt am Ende des Packs im Punch nicht: " + numbers);
        }
        ctx.log("akku-sag: " + numbers);
        return StepResult.pass();
    }

    private record PunchScene(double charge) implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(SOURCE)) {
                state.put(SOURCE, start(this.charge));
                return StepResult.pending();
            }
            Flight flight = (Flight) state.get(SOURCE);
            flight.tick(SCENE_PUNCH_TICKS + 40);
            if (flight.ticks < HOVER_TICKS + SCENE_PUNCH_TICKS) {
                return StepResult.pending();
            }
            OsdData data = OsdHud.sampler().data();
            if (this.charge < 0.5 && flight.ticks < HOVER_TICKS + SCENE_PUNCH_TICKS + BLINK_WAIT_TICKS
                    && !(data.voltageAlert != VoltageDisplay.NONE && data.blinkSeconds % BLINK_PERIOD < BLINK_EARLY)) {
                return StepResult.pending();
            }
            boolean warns = data.batteryState != BatteryState.OK;
            if (this.charge < 0.5 && !warns) {
                return StepResult.fail("fast leerer Akku warnt nach " + SCENE_PUNCH_TICKS / 20.0 + " s Vollgas nicht (Zelle " + data.cellVoltage + " V)");
            }
            if (this.charge >= 0.5 && warns) {
                return StepResult.fail("voller Akku warnt nach " + SCENE_PUNCH_TICKS / 20.0 + " s Vollgas (Zelle " + data.cellVoltage + " V)");
            }
            if (this.charge < 0.5 && data.voltageAlert == VoltageDisplay.NONE) {
                return StepResult.fail("OSD-Spannung blinkt nach " + SCENE_PUNCH_TICKS / 20.0 + " s Vollgas am Ende des Packs nicht (Zelle " + data.cellVoltage + " V)");
            }
            ctx.log(String.format(Locale.ROOT, "akku-sag: Bild bei %.0f %% Ladung: %.1f V (%.2f V je Zelle), Zustand %s, OSD-Warnstufe %d", this.charge * 100.0,
                    data.packVoltage, data.cellVoltage, data.batteryState, data.voltageAlert));
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            if (ctx.state().get(SOURCE) instanceof Flight flight) {
                flight.tick(SCENE_PUNCH_TICKS + 40);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            OsdHud.sampler().setOverride(null);
        }
    }
}
