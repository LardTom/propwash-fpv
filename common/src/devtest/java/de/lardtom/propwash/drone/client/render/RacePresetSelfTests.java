package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;
import net.minecraft.locale.Language;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;

import java.util.Locale;

final class RacePresetSelfTests {
    static final String PREFIX = "drone-render: Race-Preset";
    private static final float SHAPE_TOLERANCE_MM = 0.02F;
    private static final float PHYSICS_TOLERANCE_MM = 0.5F;
    private static final double TOP_SPEED_MIN_KMH = 140.0;
    private static final double TOP_SPEED_MAX_KMH = 160.0;

    private RacePresetSelfTests() {
    }

    static void register() {
        SelfTest.clientStep(PREFIX + " – True-X im Bild und im Flugmodell", 1, ctx -> frameStep(ctx));
        SelfTest.clientStep(PREFIX + " – Race-Prop 5,1 × 2,9 mit Modellen, Item und Lack", 1, ctx -> propStep());
        SelfTest.clientStep(PREFIX + " – flache Steigung kostet Topspeed, nicht Standschub", 1, ctx -> flightStep(ctx));
    }

    private static StepResult frameStep(SelfTest.StepContext ctx) {
        FrameType frame = DroneBuild.RACER.frame();
        if (frame != FrameType.RACE_5 || frame.definition().shape() != FrameShape.TRUE_X) {
            return StepResult.fail("Race-Preset auf " + frame.key() + " mit Form " + frame.definition().shape());
        }
        DroneLayout layout = DroneRenderResources.INSTANCE.layout(frame);
        if (!layout.fromFile() || !layout.batteryBelow()) {
            return StepResult.fail("race_5: Anbaupunkte nicht aus der Datei oder Akku nicht unter dem Rahmen");
        }
        AirframeSpec spec = AirframeSpec.of(DroneBuild.RACER);
        for (int motor = 0; motor < DroneLayout.SLOTS; motor++) {
            Vector3fc drawn = layout.motor(DroneLayout.slotForMotor(motor));
            float x = Math.abs(drawn.x());
            float z = Math.abs(drawn.z());
            if (Math.abs(x - z) > SHAPE_TOLERANCE_MM) {
                return StepResult.fail(String.format(Locale.ROOT, "Motor %d steht bei ±%.2f / ±%.2f mm, kein True-X", motor, x, z));
            }
            float diagonal = 2.0F * (float) Math.hypot(x, z);
            if (Math.abs(diagonal - frame.wheelbaseMm) > 0.1F) {
                return StepResult.fail(String.format(Locale.ROOT, "Diagonale %.2f mm statt %d mm", diagonal, frame.wheelbaseMm));
            }
            float simX = (float) Math.abs(spec.motorX(motor) * 1000.0);
            float simZ = (float) Math.abs(spec.motorZ(motor) * 1000.0);
            if (Math.abs(simX - x) > PHYSICS_TOLERANCE_MM || Math.abs(simZ - z) > PHYSICS_TOLERANCE_MM) {
                return StepResult.fail(String.format(Locale.ROOT, "Motor %d im Bild ±%.2f / ±%.2f mm, im Flugmodell ±%.2f / ±%.2f mm", motor, x, z,
                        simX, simZ));
            }
        }
        Vector3fc front = layout.motor(DroneLayout.slotForMotor(0));
        ctx.log(String.format(Locale.ROOT, "race_5: Motoren bei ±%.2f / ±%.2f mm, Bild und Flugmodell gleich", Math.abs(front.x()),
                Math.abs(front.z())));
        return StepResult.pass();
    }

    private static StepResult propStep() {
        PropType prop = DroneBuild.RACER.prop();
        if (prop != PropType.P51_29_3B || prop.diameterMm != 130 || prop.blades != 3 || Math.abs(prop.pitchInch - 2.9) > 1.0E-9) {
            return StepResult.fail("Race-Preset mit " + prop.key() + " (" + prop.diameterMm + " mm, " + prop.pitchInch + "″, " + prop.blades + " Blätter)");
        }
        DroneRenderResources res = DroneRenderResources.INSTANCE;
        for (boolean counterClockwise : new boolean[]{false, true}) {
            for (PropDamage damage : PropDamage.values()) {
                PartModel model = PartModel.prop(prop, counterClockwise, damage);
                if (!res.hasModel(model)) {
                    return StepResult.fail("Modell " + model + " fehlt");
                }
                if (res.paintGroups(model).isEmpty() || !res.hasModel(model.paintVariant())) {
                    return StepResult.fail(model + ": Lack-Variante oder Lack-Gruppen fehlen");
                }
            }
        }
        PartModel blur = PartModel.blur(prop);
        if (!res.hasModel(blur)) {
            return StepResult.fail("Modell " + blur + " fehlt");
        }
        DroneAssembly assembly = res.assembly(DroneBuild.RACER);
        DroneLayout layout = res.layout(FrameType.RACE_5);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            DroneAssembly.Part part = assembly.prop(slot);
            PartModel expected = PartModel.prop(prop, layout.counterClockwise(slot));
            if (part.model() != expected || part.item() == null) {
                return StepResult.fail("Platz " + slot + " zeichnet " + part.model() + " statt " + expected);
            }
            if (assembly.blur(slot).model() != blur) {
                return StepResult.fail("Platz " + slot + ": Scheibe " + assembly.blur(slot).model() + " statt " + blur);
            }
        }
        ItemStack stack = ContentItems.stack(prop);
        if (stack.isEmpty()) {
            return StepResult.fail("kein Item für " + prop.key());
        }
        Language language = Language.getInstance();
        for (String key : new String[]{prop.translationKey(), "propwash.help." + prop.key() + ".summary"}) {
            if (!language.has(key)) {
                return StepResult.fail("Text " + key + " fehlt");
            }
        }
        return StepResult.pass();
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        BuildAnalysis race = BuildAnalysis.of(DroneBuild.RACER);
        BuildAnalysis steep = BuildAnalysis.of(DroneBuild.RACER.withProp(PropType.P51_3B));
        if (!race.warnings().isEmpty()) {
            return StepResult.fail("Race-Preset mit Warnungen " + race.warnings());
        }
        double top = race.topSpeedAcroKmh();
        if (!(top >= TOP_SPEED_MIN_KMH && top <= TOP_SPEED_MAX_KMH)) {
            return StepResult.fail(String.format(Locale.ROOT, "Topspeed %.1f km/h außerhalb %.0f–%.0f km/h", top, TOP_SPEED_MIN_KMH,
                    TOP_SPEED_MAX_KMH));
        }
        if (!(top < 0.92 * steep.topSpeedAcroKmh())) {
            return StepResult.fail(String.format(Locale.ROOT, "Topspeed %.1f km/h mit 2,9″, %.1f km/h mit 4,6″ Steigung", top,
                    steep.topSpeedAcroKmh()));
        }
        double thrust = race.maxThrustPerMotorGrams() / steep.maxThrustPerMotorGrams();
        if (!(thrust > 0.97 && thrust < 1.01)) {
            return StepResult.fail(String.format(Locale.ROOT, "Standschub %.0f g mit 2,9″, %.0f g mit 4,6″ Steigung",
                    race.maxThrustPerMotorGrams(), steep.maxThrustPerMotorGrams()));
        }
        ctx.log(String.format(Locale.ROOT, "Race-Preset: %.1f g, Schub/Gewicht %.1f, Schwebegas %.1f %%, Topspeed %.0f km/h (4,6″: %.0f km/h), "
                        + "Vollgas %.0f A, Props im Bild %.1f %%", race.massGrams(), race.thrustToWeight(), race.hoverThrottlePercent(), top,
                steep.topSpeedAcroKmh(), race.fullThrottleCurrentA(), race.propsInViewPercent()));
        return StepResult.pass();
    }
}
