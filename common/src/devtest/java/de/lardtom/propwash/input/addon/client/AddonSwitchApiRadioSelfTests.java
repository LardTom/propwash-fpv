package de.lardtom.propwash.input.addon.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleSwitches;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.input.client.TransmitterInput;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class AddonSwitchApiRadioSelfTests {
    private static final String INDEX = "api_radio_index";
    private static final String SINCE = "api_radio_since";
    private static final String FUTURE = "api_radio_future";
    private static final String RESTORE = "api_radio_restore";
    private static final String CHANGES = "api_radio_changes";
    private static final String NOTES = "api_radio_notes";
    private static final int WAIT_TICKS = 100;
    private static final int PRESSES = 3;
    private static final int PAD_GHOST = ProfileDefaults.PAD_SOUTH;
    private static final int RADIO_CHANNEL = 8;
    private static final int RADIO_BUTTON = 0;
    private static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath("propwash_example", "unbekannt");
    private static final AtomicReference<String> SERVER = new AtomicReference<>("");
    private static final String SIGNALS = "api_radio_signals";
    private static final int ACTION_BUTTON = 1;
    private static final int ACTION_CHANNEL = 9;
    private static final int PULSES = 2;
    private static final int PULSE_TICKS = 4;
    private static final int HOLD_TICKS = 40;
    private static final int HOLD_TOLERANCE = 3;
    private static final AtomicBoolean HOOKED = new AtomicBoolean();
    private static final List<long[]> ACTIONS = Collections.synchronizedList(new ArrayList<>());
    private static volatile int target = -1;

    private AddonSwitchApiRadioSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(AddonSwitchSelfTests.GROUP + "Testfunke über die API: Gamepad lässt „Geist“ mit Prellen je Druck einmal einrasten, "
                + "je Wechsel ein ADDON_SWITCH_CHANGED; Funke per Kanal und Bereich, Knopf nur gehalten", 3000, AddonSwitchApiRadioSelfTests::step);
        SelfTest.clientStep(AddonSwitchSelfTests.GROUP + "Testfunke über die API: Zubehör-Aktion 1 mitten im Flug per Knopf als Taster "
                + "(je Druck ein Drücken und Loslassen am Server) und per Kanal und Bereich als Schalter (40 Ticks gehalten)", 3000,
                AddonSwitchApiRadioSelfTests::actionStep);
    }

    private interface Phase {
        @Nullable StepResult run(SelfTest.StepContext ctx, int waited);
    }

    private static boolean exampleLoaded() {
        return JoinTestEnv.exampleAddon() && AddonInputs.switchSpec(ExampleSwitches.GHOST) != null;
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        if (!exampleLoaded()) {
            ctx.log("Beispiel-Add-on nicht geladen – übersprungen");
            return StepResult.pass();
        }
        StepResult result = script(ctx, phases());
        if (result.status() == StepResult.Status.PASS) {
            notes(ctx).forEach(ctx::log);
        }
        return result;
    }

    private static StepResult actionStep(SelfTest.StepContext ctx) {
        if (!exampleLoaded()) {
            ctx.log("Beispiel-Add-on nicht geladen – übersprungen");
            return StepResult.pass();
        }
        StepResult result = script(ctx, actionPhases());
        if (result.status() == StepResult.Status.PASS) {
            notes(ctx).forEach(ctx::log);
        }
        return result;
    }

    private static List<Phase> actionPhases() {
        List<Phase> phases = new ArrayList<>();
        phases.add((ctx, waited) -> {
            ctx.state().put(RESTORE, ownRadioActive());
            AccessoryHintClient.mute(true);
            hook();
            target = -1;
            ACTIONS.clear();
            if (PropwashClientApi.bindSimulatedAction(1, ACTION_BUTTON) || PropwashClientApi.bindSimulatedAction(1, ACTION_CHANNEL, 1700, 2000)) {
                return StepResult.fail("ohne simulateRadio belegt die API Zubehör-Aktionen");
            }
            return null;
        });
        phases.add(server("Flugstart mit Signallicht", server -> {
            AddonSwitchSelfTests.land(server);
            AddonSwitchSelfTests.fly(AddonSwitchSelfTests.player(server), AddonSwitchSelfTests.signalBuild());
        }));
        phases.add(await("keine FPV-Sitzung", () -> PilotSession.INSTANCE.isActive() && PilotSession.INSTANCE.drone() != null));
        phases.add(wait(10));
        phases.add((ctx, waited) -> {
            QuadcopterEntity drone = PilotSession.INSTANCE.drone();
            if (drone == null) {
                return StepResult.fail("keine Drohne in der FPV-Sitzung");
            }
            target = drone.getId();
            ACTIONS.clear();
            ctx.state().put(SIGNALS, ExampleSwitches.SIGNALS.get());
            if (!PropwashClientApi.simulateRadio(false)) {
                return StepResult.fail("PropwashClientApi.simulateRadio(false) startet mitten im Flug keine Funke");
            }
            return null;
        });
        phases.add(await("Funke aus simulateRadio(false) kommt mitten im Flug nicht an", () -> {
            RawInput raw = TransmitterInput.INSTANCE.raw();
            return raw != null && AddonTestRadio.RADIO_GUID.equals(raw.device().guid()) && RadioState.INSTANCE.connected();
        }));
        phases.add((ctx, waited) -> {
            if (!PilotSession.INSTANCE.isActive()) {
                return StepResult.fail("FPV-Sitzung endet mit dem Start der Testfunke");
            }
            List<String> wrong = new ArrayList<>();
            if (PropwashClientApi.bindSimulatedAction(0, ACTION_BUTTON)) {
                wrong.add("Aktion 0 belegt");
            }
            if (PropwashClientApi.bindSimulatedAction(SwitchFunction.ACCESSORY_COUNT + 1, ACTION_BUTTON)) {
                wrong.add("Aktion " + (SwitchFunction.ACCESSORY_COUNT + 1) + " belegt");
            }
            if (PropwashClientApi.bindSimulatedAction(1, AddonTestRadio.RADIO_BUTTONS)) {
                wrong.add("Knopf " + AddonTestRadio.RADIO_BUTTONS + " belegt");
            }
            if (PropwashClientApi.bindSimulatedAction(1, 2, 1700, 2000)) {
                wrong.add("Gaskanal belegt");
            }
            if (PropwashClientApi.bindSimulatedAction(1, AddonTestRadio.RADIO_CHANNELS, 1700, 2000)) {
                wrong.add("Kanal " + AddonTestRadio.RADIO_CHANNELS + " belegt");
            }
            if (PropwashClientApi.bindSimulatedAction(1, ACTION_CHANNEL, 1800, 1700)) {
                wrong.add("Bereich 1800–1700 µs belegt");
            }
            if (!wrong.isEmpty()) {
                return StepResult.fail("Funke nimmt Ungültiges an: " + wrong);
            }
            if (!PropwashClientApi.bindSimulatedAction(1, ACTION_BUTTON)) {
                return StepResult.fail("bindSimulatedAction(1, Knopf " + ACTION_BUTTON + ") lehnt ab");
            }
            SwitchAssignment binding = savedAction(1);
            if (binding.source != SwitchSource.BUTTON || binding.index != ACTION_BUTTON || binding.toggle) {
                return StepResult.fail("Zubehör-Aktion 1: Belegung " + binding + " statt Knopf " + ACTION_BUTTON + " als Taster");
            }
            notes(ctx).add("Testfunke mitten im Flug gestartet, Sitzung läuft weiter; Zubehör-Aktion 1 auf Knopf " + ACTION_BUTTON
                    + " gespeichert " + binding);
            return null;
        });
        for (int pulse = 1; pulse <= PULSES; pulse++) {
            phases.add(run(() -> PropwashClientApi.setSimulatedButton(ACTION_BUTTON, true)));
            phases.add(wait(PULSE_TICKS));
            phases.add(run(() -> PropwashClientApi.setSimulatedButton(ACTION_BUTTON, false)));
            phases.add(serverActions(pulse + ". Druck auf Knopf " + ACTION_BUTTON, 2 * pulse));
            phases.add(wait(10));
        }
        phases.add((ctx, waited) -> {
            String taster = sequence();
            int signals = ExampleSwitches.SIGNALS.get() - (int) ctx.state().get(SIGNALS);
            if (!taster.equals("1+ 1- 1+ 1-") || signals != PULSES) {
                return StepResult.fail("Taster: am Server [" + taster + "] mit " + signals + " Auslösungen statt [1+ 1- 1+ 1-] mit " + PULSES);
            }
            notes(ctx).add("Taster: " + PULSES + " Drücke von je " + PULSE_TICKS + " Ticks auf Knopf " + ACTION_BUTTON + " → am Server [" + taster
                    + "], Signallicht " + signals + "-mal ausgelöst");
            ACTIONS.clear();
            if (!PropwashClientApi.bindSimulatedAction(1, ACTION_CHANNEL, 1700, 2000)) {
                return StepResult.fail("bindSimulatedAction(1, Kanal " + ACTION_CHANNEL + ", 1700, 2000) lehnt ab");
            }
            SwitchAssignment binding = savedAction(1);
            if (binding.source != SwitchSource.AXIS || binding.index != ACTION_CHANNEL || binding.lowMicros() != 1700 || binding.highMicros() != 2000) {
                return StepResult.fail("Zubehör-Aktion 1: Belegung " + binding + " statt Kanal " + ACTION_CHANNEL + " 1700–2000 µs");
            }
            PropwashClientApi.setSimulatedButton(ACTION_BUTTON, true);
            return null;
        });
        phases.add(wait(10));
        phases.add((ctx, waited) -> {
            PropwashClientApi.setSimulatedButton(ACTION_BUTTON, false);
            if (!ACTIONS.isEmpty() || PropwashClientApi.input().action(1)) {
                return StepResult.fail("Knopf " + ACTION_BUTTON + " löst nach dem Umbelegen auf Kanal " + ACTION_CHANNEL + " weiter aus: [" + sequence() + "]");
            }
            PropwashClientApi.setSimulatedAxis(ACTION_CHANNEL, 1.0F);
            return null;
        });
        phases.add((ctx, waited) -> {
            boolean held = PropwashClientApi.input().action(1);
            if (!held && waited > HOLD_TOLERANCE) {
                return StepResult.fail("Schalter an: Zubehör-Aktion 1 nach " + waited + " Ticks nicht gehalten");
            }
            return waited >= HOLD_TICKS ? null : StepResult.pending();
        });
        phases.add(run(() -> PropwashClientApi.setSimulatedAxis(ACTION_CHANNEL, -1.0F)));
        phases.add(serverActions("Schalter " + HOLD_TICKS + " Ticks an, dann aus", 2));
        phases.add(wait(10));
        phases.add((ctx, waited) -> {
            String schalter = sequence();
            long held = heldTicks();
            int signals = ExampleSwitches.SIGNALS.get() - (int) ctx.state().get(SIGNALS);
            if (!schalter.equals("1+ 1-") || Math.abs(held - HOLD_TICKS) > HOLD_TOLERANCE || signals != PULSES + 1) {
                return StepResult.fail("Schalter: am Server [" + schalter + "], " + held + " Ticks gehalten statt " + HOLD_TICKS + ", "
                        + signals + " Auslösungen statt " + (PULSES + 1));
            }
            if (PropwashClientApi.input().action(1)) {
                return StepResult.fail("Schalter aus: Client hält Zubehör-Aktion 1 weiter");
            }
            notes(ctx).add("Schalter: Kanal " + ACTION_CHANNEL + " 1700–2000 µs " + HOLD_TICKS + " Ticks auf 2000 µs → am Server [" + schalter + "], "
                    + held + " Ticks gehalten; Knopf " + ACTION_BUTTON + " nach dem Umbelegen ohne Wirkung");
            PropwashClientApi.stopSimulatedRadio();
            return null;
        });
        phases.add(await("stopSimulatedRadio lässt die Testfunke stehen", () -> {
            RawInput raw = TransmitterInput.INSTANCE.raw();
            boolean ours = raw != null && AddonTestRadio.RADIO_GUID.equals(raw.device().guid());
            return !ours && InputConfig.get().profile(AddonTestRadio.RADIO_GUID) == null;
        }));
        phases.add((ctx, waited) -> {
            if (!PilotSession.INSTANCE.isActive()) {
                return StepResult.fail("FPV-Sitzung endet mit stopSimulatedRadio");
            }
            if (PropwashClientApi.bindSimulatedAction(1, ACTION_BUTTON)) {
                return StepResult.fail("nach stopSimulatedRadio belegt die API weiter Zubehör-Aktionen");
            }
            notes(ctx).add("stopSimulatedRadio mitten im Flug: Sitzung läuft weiter, Testfunke und Profil sind weg");
            return null;
        });
        phases.add(server("Landen", AddonSwitchSelfTests::land));
        phases.add(await("FPV-Sitzung endet nicht", () -> !PilotSession.INSTANCE.isActive()));
        phases.add((ctx, waited) -> {
            target = -1;
            restore(ctx);
            return null;
        });
        return phases;
    }

    private static void hook() {
        if (HOOKED.compareAndSet(false, true)) {
            PropwashEvents.ACCESSORY_ACTION.register((player, drone, number, pressed) -> {
                if (drone.getId() == target) {
                    ACTIONS.add(new long[]{drone.level().getGameTime(), number, pressed ? 1L : 0L});
                }
            });
        }
    }

    private static String sequence() {
        List<String> out = new ArrayList<>();
        synchronized (ACTIONS) {
            for (long[] action : ACTIONS) {
                out.add(action[1] + (action[2] == 1L ? "+" : "-"));
            }
        }
        return String.join(" ", out);
    }

    private static long heldTicks() {
        synchronized (ACTIONS) {
            return ACTIONS.size() == 2 ? ACTIONS.get(1)[0] - ACTIONS.get(0)[0] : -1L;
        }
    }

    private static Phase serverActions(String what, int expected) {
        return (ctx, waited) -> {
            int seen = ACTIONS.size();
            if (seen > expected) {
                return StepResult.fail(what + ": am Server [" + sequence() + "] statt " + expected + " Meldungen");
            }
            if (seen < expected) {
                return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail(what + ": am Server nur [" + sequence() + "]");
            }
            return null;
        };
    }

    private static SwitchAssignment savedAction(int number) {
        DeviceProfile profile = InputConfig.get().profile(AddonTestRadio.RADIO_GUID);
        return profile == null ? SwitchAssignment.none() : profile.binding(SwitchFunction.accessory(number));
    }

    private static List<Phase> phases() {
        Identifier ghost = ExampleSwitches.GHOST;
        List<Phase> phases = new ArrayList<>();
        phases.add((ctx, waited) -> {
            ctx.state().put(RESTORE, ownRadioActive());
            AccessoryHintClient.mute(true);
            if (PropwashClientApi.setSimulatedButton(0, true) || PropwashClientApi.setSimulatedAxis(0, 0.5F)
                    || PropwashClientApi.bindSimulatedSwitch(ghost, 0)) {
                return StepResult.fail("ohne simulateRadio nimmt die API Knöpfe, Achsen oder Belegungen an");
            }
            if (!PropwashClientApi.simulateRadio(true)) {
                return StepResult.fail("PropwashClientApi.simulateRadio(true) startet in der Entwicklungsumgebung kein Gamepad");
            }
            return null;
        });
        phases.add(await("Gamepad aus simulateRadio(true) kommt nicht an", () -> {
            RawInput raw = TransmitterInput.INSTANCE.raw();
            DeviceProfile saved = InputConfig.get().profile(AddonTestRadio.GAMEPAD_GUID);
            return raw != null && saved != null && AddonTestRadio.GAMEPAD_GUID.equals(raw.device().guid()) && raw.device().gamepad()
                    && raw.usesGamepadLayout(saved);
        }));
        phases.add((ctx, waited) -> {
            if (!PropwashClientApi.bindSimulatedSwitch(ghost, PAD_GHOST)) {
                return StepResult.fail("bindSimulatedSwitch(Geist, Knopf " + PAD_GHOST + ") lehnt ab");
            }
            List<String> wrong = new ArrayList<>();
            if (PropwashClientApi.bindSimulatedSwitch(UNKNOWN, 0)) {
                wrong.add("unbekannte Id belegt");
            }
            if (PropwashClientApi.bindSimulatedSwitch(ghost, ProfileDefaults.PAD_BUTTONS)) {
                wrong.add("Knopf " + ProfileDefaults.PAD_BUTTONS + " belegt");
            }
            if (PropwashClientApi.setSimulatedButton(ProfileDefaults.PAD_BUTTONS, true)) {
                wrong.add("Knopf " + ProfileDefaults.PAD_BUTTONS + " gedrückt");
            }
            if (PropwashClientApi.setSimulatedAxis(ProfileDefaults.PAD_AXES, 0.0F)) {
                wrong.add("Achse " + ProfileDefaults.PAD_AXES + " gesetzt");
            }
            if (PropwashClientApi.setSimulatedAxis(0, Float.NaN)) {
                wrong.add("Achse 0 auf NaN gesetzt");
            }
            if (!wrong.isEmpty()) {
                return StepResult.fail("Gamepad nimmt Ungültiges an: " + wrong);
            }
            SwitchAssignment binding = saved(AddonTestRadio.GAMEPAD_GUID, ghost);
            if (binding.source != SwitchSource.BUTTON || binding.index != PAD_GHOST || !binding.toggle) {
                return StepResult.fail("Gamepad: Belegung " + binding + " statt Knopf " + PAD_GHOST + " mit Umschalten");
            }
            notes(ctx).add("Gamepad: Knopf " + PAD_GHOST + " belegt wie „Quelle“ auf der Seite „Add-ons“, gespeichert " + binding);
            return null;
        });
        phases.add(server("Flugstart", server -> {
            AddonSwitchSelfTests.land(server);
            AddonSwitchSelfTests.fly(AddonSwitchSelfTests.player(server), DroneBuild.FREESTYLE);
        }));
        phases.add(await("keine FPV-Sitzung", () -> PilotSession.INSTANCE.isActive() && PilotSession.INSTANCE.drone() != null));
        phases.add(wait(10));
        phases.add((ctx, waited) -> {
            if (PropwashClientApi.input().switchActive(ghost)) {
                return StepResult.fail("Geist vor dem ersten Druck an");
            }
            ctx.state().put(CHANGES, ExampleSwitches.GHOST_CHANGES.get());
            return null;
        });
        for (int press = 1; press <= PRESSES; press++) {
            boolean expected = press % 2 == 1;
            int number = press;
            phases.add(run(() -> {
                PropwashClientApi.setSimulatedButton(PAD_GHOST, true);
                PropwashClientApi.setSimulatedButton(PAD_GHOST, false);
                PropwashClientApi.setSimulatedButton(PAD_GHOST, true);
            }));
            phases.add(wait(6));
            phases.add(run(() -> PropwashClientApi.setSimulatedButton(PAD_GHOST, false)));
            phases.add(wait(6));
            phases.add(state(number + ". Druck mit Prellen", expected, number));
        }
        phases.add(wait(20));
        phases.add((ctx, waited) -> {
            int changes = changes(ctx);
            if (changes != PRESSES) {
                return StepResult.fail("Gamepad: " + changes + " ADDON_SWITCH_CHANGED statt " + PRESSES);
            }
            notes(ctx).add("Gamepad: " + PRESSES + " Drücke mit Prellen → Geist an/aus/an, " + changes + " ADDON_SWITCH_CHANGED am Server");
            if (!PropwashClientApi.simulateRadio(false)) {
                return StepResult.fail("PropwashClientApi.simulateRadio(false) startet keine Funke");
            }
            return null;
        });
        phases.add(await("Funke aus simulateRadio(false) kommt nicht an", () -> {
            RawInput raw = TransmitterInput.INSTANCE.raw();
            DeviceProfile saved = InputConfig.get().profile(AddonTestRadio.RADIO_GUID);
            return raw != null && saved != null && AddonTestRadio.RADIO_GUID.equals(raw.device().guid()) && !raw.device().gamepad()
                    && !raw.usesGamepadLayout(saved) && InputConfig.get().profile(AddonTestRadio.GAMEPAD_GUID) == null;
        }));
        phases.add(state("Funke ersetzt das Gamepad, Geist ohne Belegung", false, PRESSES + 1));
        phases.add((ctx, waited) -> {
            List<String> wrong = new ArrayList<>();
            if (PropwashClientApi.bindSimulatedSwitch(ghost, AddonTestRadio.RADIO_CHANNELS, 1700, 2000)) {
                wrong.add("Kanal " + AddonTestRadio.RADIO_CHANNELS + " belegt");
            }
            if (PropwashClientApi.bindSimulatedSwitch(ghost, RADIO_CHANNEL, 1800, 1700)) {
                wrong.add("Bereich 1800–1700 µs belegt");
            }
            if (PropwashClientApi.bindSimulatedSwitch(UNKNOWN, RADIO_CHANNEL, 1700, 2000)) {
                wrong.add("unbekannte Id auf Kanal belegt");
            }
            if (!wrong.isEmpty()) {
                return StepResult.fail("Funke nimmt Ungültiges an: " + wrong);
            }
            if (!PropwashClientApi.bindSimulatedSwitch(ghost, RADIO_CHANNEL, 1700, 2000)) {
                return StepResult.fail("bindSimulatedSwitch(Geist, Kanal " + RADIO_CHANNEL + ", 1700, 2000) lehnt ab");
            }
            SwitchAssignment binding = saved(AddonTestRadio.RADIO_GUID, ghost);
            if (binding.source != SwitchSource.AXIS || binding.index != RADIO_CHANNEL || binding.lowMicros() != 1700 || binding.highMicros() != 2000) {
                return StepResult.fail("Funke: Belegung " + binding + " statt Kanal " + RADIO_CHANNEL + " 1700–2000 µs");
            }
            PropwashClientApi.setSimulatedAxis(RADIO_CHANNEL, 1.0F);
            return null;
        });
        phases.add(state("Kanal " + RADIO_CHANNEL + " auf 2000 µs", true, PRESSES + 2));
        phases.add(run(() -> PropwashClientApi.setSimulatedAxis(RADIO_CHANNEL, -1.0F)));
        phases.add(state("Kanal " + RADIO_CHANNEL + " auf 1000 µs", false, PRESSES + 3));
        phases.add((ctx, waited) -> {
            notes(ctx).add("Funke: Kanal " + RADIO_CHANNEL + " im Bereich 1700–2000 µs schaltet Geist an, 1000 µs aus");
            if (!PropwashClientApi.bindSimulatedSwitch(ghost, RADIO_BUTTON)) {
                return StepResult.fail("bindSimulatedSwitch(Geist, Knopf " + RADIO_BUTTON + ") an der Funke lehnt ab");
            }
            SwitchAssignment binding = saved(AddonTestRadio.RADIO_GUID, ghost);
            if (binding.source != SwitchSource.BUTTON || binding.index != RADIO_BUTTON || binding.toggle) {
                return StepResult.fail("Funke: Belegung " + binding + " statt Knopf " + RADIO_BUTTON + " mit Halten");
            }
            PropwashClientApi.setSimulatedButton(RADIO_BUTTON, true);
            return null;
        });
        phases.add(state("Funke: Knopf " + RADIO_BUTTON + " gehalten", true, PRESSES + 4));
        phases.add(wait(10));
        phases.add((ctx, waited) -> {
            if (!PropwashClientApi.input().switchActive(ghost)) {
                return StepResult.fail("Funke: Geist geht aus, obwohl Knopf " + RADIO_BUTTON + " gehalten wird");
            }
            PropwashClientApi.setSimulatedButton(RADIO_BUTTON, false);
            return null;
        });
        phases.add(state("Funke: Knopf " + RADIO_BUTTON + " losgelassen", false, PRESSES + 5));
        phases.add((ctx, waited) -> {
            notes(ctx).add("Funke: Knopf " + RADIO_BUTTON + " rastet nicht ein, Geist nur gehalten an");
            PropwashClientApi.stopSimulatedRadio();
            return null;
        });
        phases.add(await("stopSimulatedRadio lässt die Testfunke stehen", () -> {
            RawInput raw = TransmitterInput.INSTANCE.raw();
            boolean ours = raw != null && (AddonTestRadio.RADIO_GUID.equals(raw.device().guid()) || AddonTestRadio.GAMEPAD_GUID.equals(raw.device().guid()));
            return !ours && InputConfig.get().profile(AddonTestRadio.RADIO_GUID) == null && InputConfig.get().profile(AddonTestRadio.GAMEPAD_GUID) == null;
        }));
        phases.add((ctx, waited) -> {
            if (PropwashClientApi.setSimulatedButton(RADIO_BUTTON, true) || PropwashClientApi.bindSimulatedSwitch(ghost, RADIO_BUTTON)) {
                return StepResult.fail("nach stopSimulatedRadio nimmt die API weiter Knöpfe oder Belegungen an");
            }
            notes(ctx).add("stopSimulatedRadio: Testfunke und ihr Profil sind weg, die API nimmt danach nichts mehr an");
            return null;
        });
        phases.add(server("Landen", AddonSwitchSelfTests::land));
        phases.add(await("FPV-Sitzung endet nicht", () -> !PilotSession.INSTANCE.isActive()));
        phases.add((ctx, waited) -> {
            restore(ctx);
            return null;
        });
        return phases;
    }

    private static Phase state(String what, boolean expected, int changes) {
        return (ctx, waited) -> {
            boolean client = PropwashClientApi.input().switchActive(ExampleSwitches.GHOST);
            int seen = changes(ctx);
            CompletableFuture<?> query = (CompletableFuture<?>) ctx.state().get(FUTURE);
            if (query == null) {
                if (client != expected || seen < changes) {
                    return waited < WAIT_TICKS ? StepResult.pending()
                            : StepResult.fail(what + ": Client " + (client ? "an" : "aus") + ", " + seen + " ADDON_SWITCH_CHANGED statt " + changes);
                }
                ctx.state().put(FUTURE, ctx.onServer(server -> SERVER.set(serverGhost(server))));
                return StepResult.pending();
            }
            if (!query.isDone()) {
                return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail(what + ": Server antwortet nicht");
            }
            ctx.state().remove(FUTURE);
            if (!SERVER.get().equals(expected ? "an" : "aus")) {
                return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail(what + ": Server sieht den Geist " + SERVER.get());
            }
            if (seen != changes) {
                return StepResult.fail(what + ": " + seen + " ADDON_SWITCH_CHANGED statt " + changes);
            }
            return null;
        };
    }

    private static String serverGhost(MinecraftServer server) {
        QuadcopterEntity drone = AddonSwitchSelfTests.serverDrone(server);
        if (drone == null) {
            return "ohne Drohne";
        }
        return ExampleSwitches.ghost().active(drone) ? "an" : "aus";
    }

    private static int changes(SelfTest.StepContext ctx) {
        return ExampleSwitches.GHOST_CHANGES.get() - (int) ctx.state().getOrDefault(CHANGES, ExampleSwitches.GHOST_CHANGES.get());
    }

    private static SwitchAssignment saved(String guid, Identifier id) {
        DeviceProfile profile = InputConfig.get().profile(guid);
        return profile == null ? SwitchAssignment.none() : profile.addonBinding(id);
    }

    private static boolean ownRadioActive() {
        RawInput raw = TransmitterInput.INSTANCE.simulating() ? TransmitterInput.INSTANCE.raw() : null;
        return raw != null && AddonSwitchSelfTests.RADIO_GUID.equals(raw.device().guid());
    }

    private static void restore(SelfTest.StepContext ctx) {
        PropwashClientApi.stopSimulatedRadio();
        if (Boolean.TRUE.equals(ctx.state().remove(RESTORE))) {
            AddonSwitchSelfTests.installRadio(AddonSwitchSelfTests.radioProfile());
        }
        AccessoryHintClient.mute(false);
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, k -> new ArrayList<String>());
    }

    private static StepResult script(SelfTest.StepContext ctx, List<Phase> phases) {
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        state.putIfAbsent(SINCE, ctx.tick());
        while (index < phases.size()) {
            StepResult result;
            try {
                result = phases.get(index).run(ctx, ctx.tick() - (int) state.get(SINCE));
            } catch (RuntimeException e) {
                Constants.LOG.error("[Selbsttest] {}", AddonSwitchSelfTests.GROUP, e);
                result = StepResult.fail(e.getMessage() != null ? e.getMessage() : e.toString());
            }
            if (result == null) {
                index++;
                state.put(INDEX, index);
                state.put(SINCE, ctx.tick());
                continue;
            }
            if (result.status() == StepResult.Status.FAIL) {
                restore(ctx);
                ctx.onServer(AddonSwitchSelfTests::land);
            }
            return result;
        }
        return StepResult.pass();
    }

    private static Phase server(String what, Consumer<MinecraftServer> action) {
        return (ctx, waited) -> {
            CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
            if (future == null) {
                ctx.state().put(FUTURE, ctx.onServer(action));
                return StepResult.pending();
            }
            if (!future.isDone()) {
                return waited > WAIT_TICKS ? StepResult.fail(what + ": Server antwortet nicht") : StepResult.pending();
            }
            ctx.state().remove(FUTURE);
            if (future.isCompletedExceptionally()) {
                Throwable cause = future.exceptionNow();
                return StepResult.fail(what + ": " + (cause.getMessage() != null ? cause.getMessage() : cause.toString()));
            }
            return null;
        };
    }

    private static Phase await(String what, BooleanSupplier condition) {
        return (ctx, waited) -> condition.getAsBoolean() ? null : waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail(what);
    }

    private static Phase wait(int ticks) {
        return (ctx, waited) -> waited >= ticks ? null : StepResult.pending();
    }

    private static Phase run(Runnable action) {
        return (ctx, waited) -> {
            action.run();
            return null;
        };
    }
}
