package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.sim.AirframeSpec;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

final class RaceBatterySelfTests {
    static final String PREFIX = "drone-render: Akku unter dem Race-Rahmen";
    private static final float MM = 0.01F;
    private static final float PHYSICS_TOLERANCE_MM = 4.0F;

    private RaceBatterySelfTests() {
    }

    static void register() {
        SelfTest.clientStep(PREFIX + " – Anbaupunkte", 1, ctx -> layoutStep());
        SelfTest.clientStep(PREFIX + " – Modelle hängen unter der Platte", 1, ctx -> modelStep());
        SelfTest.clientStep(PREFIX + " – Whoop unverändert", 1, ctx -> whoopStep());
        SelfTest.clientStep(PREFIX + " – Bild und Flugmodell gleich tief", 1, ctx -> physicsStep());
        SelfTest.clientStep(PREFIX + " – Rahmen und Presets von Addons", 1, ctx -> addonStep(ctx));
    }

    private static StepResult addonStep(SelfTest.StepContext ctx) {
        DroneRenderResources res = DroneRenderResources.INSTANCE;
        int checked = 0;
        for (FrameType frame : FrameType.values()) {
            if (frame.builtin() || frame.definition().geometry().batteryOnTop()) {
                continue;
            }
            DroneLayout layout = res.layout(frame);
            if (!layout.batteryBelow()) {
                return StepResult.fail(frame.id() + ": Akku im Flugmodell unten, im Bild oben");
            }
            if (!layout.fromFile() && Math.abs(layout.battery().y() - DroneLayout.batteryPlateHeight(frame)) > MM) {
                return StepResult.fail(frame.id() + ": Aufhängepunkt " + layout.battery().y() + " statt " + DroneLayout.batteryPlateHeight(frame));
            }
            checked++;
        }
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            DroneBuild build = Conversions.build(preset.data().build()).orElse(null);
            if (build == null || !res.layout(build.frame()).batteryBelow()) {
                continue;
            }
            DroneAssembly.Part part = res.assembly(build).battery();
            PartModel under = PartModel.batteryUnder(build.battery());
            if (res.hasModel(under) && part.model() != under) {
                return StepResult.fail(preset.id() + ": " + under + " vorhanden, gezeichnet " + part.model());
            }
            if (part.model() == null || part.model() == under) {
                checked++;
                continue;
            }
            float anchor = res.layout(build.frame()).battery().y();
            float top = part.position().y() + res.top(part.model(), 0.0F);
            if (Math.abs(top - anchor) > MM) {
                return StepResult.fail(preset.id() + ": Oberkante " + top + " mm statt am Aufhängepunkt " + anchor + " mm");
            }
            checked++;
        }
        ctx.log(checked + " Addon-Rahmen und Presets mit dem Akku unten geprüft");
        return StepResult.pass();
    }

    private static StepResult layoutStep() {
        DroneRenderResources res = DroneRenderResources.INSTANCE;
        for (FrameType frame : FrameType.values()) {
            DroneLayout layout = res.layout(frame);
            boolean under = !frame.definition().geometry().batteryOnTop();
            if (layout.batteryBelow() != under) {
                return StepResult.fail(frame.key() + ": Akku im Bild " + (layout.batteryBelow() ? "unten" : "oben") + ", im Flugmodell "
                        + (under ? "unten" : "oben"));
            }
        }
        DroneLayout race = res.layout(FrameType.RACE_5);
        if (!race.batteryBelow()) {
            return StepResult.fail("race_5 trägt den Akku nicht unter dem Rahmen");
        }
        float plate = DroneLayout.batteryPlateHeight(FrameType.RACE_5);
        return StepResult.check(Math.abs(race.battery().y() - plate) < 1.0F,
                "race_5: Aufhängepunkt " + race.battery().y() + " mm, Akkuplatte im Flugmodell " + plate + " mm");
    }

    private static StepResult modelStep() {
        DroneRenderResources res = DroneRenderResources.INSTANCE;
        for (BatteryType battery : new BatteryType[]{BatteryType.B6S_1100, BatteryType.B6S_1300}) {
            PartModel under = PartModel.batteryUnder(battery);
            if (!res.hasModel(under)) {
                return StepResult.fail("Modell " + under + " fehlt");
            }
            DroneBuild build = DroneBuild.RACER.withBattery(battery);
            DroneAssembly assembly = res.assembly(build);
            DroneAssembly.Part part = assembly.battery();
            if (part.model() != under || part.item() == null) {
                return StepResult.fail(build + ": Akku zeichnet " + part.model() + " statt " + under);
            }
            String problem = samePosition(res.layout(FrameType.RACE_5).battery(), part.position(), battery.key());
            if (problem != null) {
                return StepResult.fail(problem);
            }
            float top = res.top(under, Float.NaN);
            float bottom = res.bottom(under, Float.NaN);
            if (!(top > 0.0F && top < 4.0F)) {
                return StepResult.fail(under + ": Oberkante " + top + " mm, erwartet nur der Riemen durch die Platte (0–4 mm)");
            }
            if (!(bottom < -25.0F)) {
                return StepResult.fail(under + ": Unterkante " + bottom + " mm, der Pack hängt nicht unter der Platte");
            }
            PaintSmoke paint = PaintSmoke.of(res, under);
            if (paint != null) {
                return StepResult.fail(paint.message());
            }
        }
        DroneAssembly weak = res.assembly(DroneBuild.RACER.withBattery(BatteryType.B4S_650));
        PartModel base = PartModel.of(BatteryType.B4S_650);
        if (weak.battery().model() != base) {
            return StepResult.fail("4S 650 ohne Riemen: erwartet das normale Modell, gezeichnet " + weak.battery().model());
        }
        float hang = res.layout(FrameType.RACE_5).battery().y() - res.top(base, Float.NaN);
        return StepResult.check(Math.abs(weak.battery().position().y() - hang) < MM,
                "4S 650 hängt bei " + weak.battery().position().y() + " mm statt " + hang + " mm");
    }

    private static StepResult whoopStep() {
        DroneRenderResources res = DroneRenderResources.INSTANCE;
        DroneAssembly whoop = res.assembly(DroneBuild.WHOOP);
        PartModel base = PartModel.of(BatteryType.B1S_300_HV);
        if (whoop.battery().model() != base) {
            return StepResult.fail("Whoop zeichnet " + whoop.battery().model() + " statt " + base);
        }
        Vector3fc position = whoop.battery().position();
        return StepResult.check(Math.abs(position.y() + 11.2F) < MM && Math.abs(position.z() - 1.0F) < MM,
                "Whoop-Akku liegt bei " + position + " statt wie bisher bei (0, −11,2, 1)");
    }

    private static StepResult physicsStep() {
        DroneRenderResources res = DroneRenderResources.INSTANCE;
        for (BatteryType battery : new BatteryType[]{BatteryType.B6S_1100, BatteryType.B6S_1300}) {
            DroneBuild build = DroneBuild.RACER.withBattery(battery);
            AirframeSpec spec = AirframeSpec.of(build);
            float lowest = (float) ((spec.comHeight + spec.hitboxFootOffset) * 1000.0);
            DroneAssembly.Part part = res.assembly(build).battery();
            float drawn = part.position().y() + res.bottom(part.model(), Float.NaN);
            if (!(Math.abs(drawn - lowest) < PHYSICS_TOLERANCE_MM)) {
                return StepResult.fail(battery.key() + ": Unterkante im Bild " + drawn + " mm, im Flugmodell " + lowest + " mm");
            }
        }
        return StepResult.pass();
    }

    private static @Nullable String samePosition(Vector3fc expected, Vector3fc actual, String what) {
        if (expected.distance(actual) < MM) {
            return null;
        }
        return what + ": Akku bei " + actual + " statt am Aufhängepunkt " + expected;
    }

    private record PaintSmoke(String message) {
        static @Nullable PaintSmoke of(DroneRenderResources res, PartModel model) {
            if (res.paintGroups(model).isEmpty()) {
                return new PaintSmoke(model + ": keine Lack-Gruppen in drone_paint.json");
            }
            return res.hasModel(model.paintVariant()) ? null : new PaintSmoke(model.paintVariant() + " fehlt");
        }
    }
}
