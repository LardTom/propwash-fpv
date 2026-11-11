package de.lardtom.propwash.input.client.screen;

import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.addon.AddonInputs;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovReceiverWizardAddonTest {
    private static final String NS = "covwizard";
    private static final Identifier GHOST = Identifier.fromNamespaceAndPath(NS, "ghost");
    private static final Identifier TILT = Identifier.fromNamespaceAndPath(NS, "gimbal_tilt");
    private static final Identifier PAUSE = Identifier.fromNamespaceAndPath(NS, "pause");

    @BeforeAll
    static void register() {
        if (AddonInputs.switchSpec(GHOST) == null) {
            Api17Registries.SWITCHES.register(SwitchSpec.builder(GHOST).toggle(true).latchesOnGamepad(true).serverSync(true).build());
            Api17Registries.SWITCHES.register(SwitchSpec.builder(PAUSE).toggle(false).build());
            Api17Registries.SWITCHES.registerAxis(AxisSpec.builder(TILT).center(0.0).keyRateDps(60).build());
        }
    }

    private static List<SwitchFunction> asked(String field) throws ReflectiveOperationException {
        Field list = ReceiverWizardScreen.class.getDeclaredField(field);
        list.setAccessible(true);
        return List.of((SwitchFunction[]) list.get(null));
    }

    @Test
    void wizardAsksOnlyPropwashFunctionsWhileAddonSwitchesAreRegistered() throws ReflectiveOperationException {
        assertTrue(AddonInputs.switchSpec(GHOST) != null && AddonInputs.switchSpec(PAUSE) != null && AddonInputs.axisSpec(TILT) != null,
                "Add-on-Schalter und -Achse angemeldet");
        Set<SwitchFunction> flight = EnumSet.copyOf(SwitchFunction.flightFunctions());
        List<String> addonKeys = new ArrayList<>();
        for (SwitchSpec spec : AddonInputs.switches()) {
            addonKeys.add(spec.id().toString());
            addonKeys.add(spec.nameKey());
        }
        for (AxisSpec spec : AddonInputs.axes()) {
            addonKeys.add(spec.id().toString());
            addonKeys.add(spec.nameKey());
        }
        for (String field : List.of("ALL_FUNCTIONS", "FUNCTIONS_WITHOUT_3D")) {
            List<SwitchFunction> asked = asked(field);
            assertFalse(asked.isEmpty(), field);
            assertEquals(SwitchFunction.ARM, asked.getFirst(), field + ": der Assistent beginnt mit dem Arm-Schalter");
            assertEquals(asked.size(), EnumSet.copyOf(asked).size(), field + ": keine Funktion doppelt");
            for (SwitchFunction function : asked) {
                assertTrue(flight.contains(function), field + ": " + function + " ist keine eingebaute Flugfunktion");
                assertFalse(function.accessoryAction() || function.command(), field + ": " + function);
                assertFalse(function.name().contains(":") || function.translationKey().contains(":"), field + ": " + function + " mit Doppelpunkt");
                assertFalse(addonKeys.contains(function.translationKey()), field + ": " + function + " trägt den Schlüssel eines Add-ons");
                assertTrue(function.translationKey().startsWith("propwash."), field + ": " + function.translationKey());
            }
        }
        List<SwitchFunction> all = asked("ALL_FUNCTIONS");
        List<SwitchFunction> without3d = asked("FUNCTIONS_WITHOUT_3D");
        assertTrue(all.contains(SwitchFunction.MODE_3D));
        assertFalse(without3d.contains(SwitchFunction.MODE_3D));
        assertEquals(all.size() - 1, without3d.size());
        assertFalse(all.contains(SwitchFunction.BLACKBOX) || all.contains(SwitchFunction.VTX_POWER));
    }

    @Test
    void anotherAddonSwitchDoesNotChangeTheWizardSteps() throws ReflectiveOperationException {
        List<SwitchFunction> before = asked("ALL_FUNCTIONS");
        Identifier extra = Identifier.fromNamespaceAndPath(NS, "follow_me");
        if (AddonInputs.switchSpec(extra) == null) {
            Api17Registries.SWITCHES.register(SwitchSpec.builder(extra).toggle(true).serverSync(true).build());
        }
        assertEquals(before, asked("ALL_FUNCTIONS"));
        assertEquals(SwitchFunction.values().length, EnumSet.allOf(SwitchFunction.class).size());
    }
}
