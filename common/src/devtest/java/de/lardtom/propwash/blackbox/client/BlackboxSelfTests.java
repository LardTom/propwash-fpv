package de.lardtom.propwash.blackbox.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.blackbox.BlackboxEncoder;
import de.lardtom.propwash.blackbox.BlackboxLibrary;
import de.lardtom.propwash.blackbox.BlackboxRecorder;
import de.lardtom.propwash.blackbox.BlackboxWriter;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.PidProfile;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class BlackboxSelfTests {
    private static final String PHASE = "phase";
    private static final String BEFORE = "before";
    private static final String SINCE = "since";
    private static final int FLIGHT_TICKS = 60;

    private BlackboxSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("blackbox: Flug mit eingeschalteter Blackbox schreibt einen gültigen Log", 1600, BlackboxSelfTests::flightStep);
        SelfTest.clientStep("blackbox: Taste pausiert die Aufzeichnung und setzt sie fort", 1600, BlackboxSelfTests::keyStep);
        SelfTest.clientStep("blackbox: ausgeschaltet entsteht kein Log", 1200, BlackboxSelfTests::offStep);
        SelfTest.clientStep("blackbox: aufräumen", 300, BlackboxFlightFixture::cleanup);
        SelfTest.afterLeaveStep("blackbox: nach dem Verlassen kein offener Log", 200, ctx -> {
            BlackboxRecorder recorder = BlackboxSession.INSTANCE.recorderIfCreated();
            if (recorder == null) {
                return StepResult.pass();
            }
            if (recorder.logging() || recorder.writer().currentFile() != null) {
                return ctx.tick() > 150 ? StepResult.fail("Log nach dem Verlassen noch offen: " + recorder.writer().currentFile()) : StepResult.pending();
            }
            return StepResult.pass();
        });
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void phase(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static Set<String> logNames(BlackboxLibrary library) {
        Set<String> names = new HashSet<>();
        for (BlackboxLibrary.LogFile file : library.list()) {
            names.add(file.name());
        }
        return names;
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (phase(ctx) == 0) {
            BlackboxFlightFixture.enableBlackbox();
            StepResult piloting = BlackboxFlightFixture.ensurePiloting(ctx);
            if (piloting.status() != StepResult.Status.PASS) {
                return piloting;
            }
            phase(ctx, 1);
            return StepResult.pending();
        }
        BlackboxRecorder recorder = BlackboxSession.INSTANCE.recorder(minecraft);
        DroneSim sim = PilotSession.INSTANCE.sim();
        if (sim == null) {
            return StepResult.fail("Flugmodell fehlt");
        }
        if (phase(ctx) == 1) {
            if (sim.fcListener() != recorder) {
                return since(ctx) > 40 ? StepResult.fail("Blackbox hängt nicht an der Regelschleife") : StepResult.pending();
            }
            ctx.state().put(BEFORE, logNames(recorder.library()));
            phase(ctx, 2);
            return StepResult.pending();
        }
        if (phase(ctx) == 2) {
            if (since(ctx) == 25 && recorder.state() != BlackboxRecorder.State.LOGGING) {
                return StepResult.fail("Blackbox zeichnet im Flug nicht auf: " + recorder.state());
            }
            StepResult flown = BlackboxFlightFixture.fly(ctx, FLIGHT_TICKS);
            if (flown.status() != StepResult.Status.PASS) {
                return flown;
            }
            phase(ctx, 3);
            return StepResult.pending();
        }
        if (recorder.state() != BlackboxRecorder.State.READY || recorder.writer().currentFile() != null) {
            return since(ctx) > 200 ? StepResult.fail("Log nach dem Entschärfen nicht beendet: " + recorder.state()) : StepResult.pending();
        }
        BlackboxWriter.Saved saved = recorder.writer().lastSaved();
        @SuppressWarnings("unchecked")
        Set<String> before = (Set<String>) ctx.state().get(BEFORE);
        if (saved == null || before.contains(saved.path().getFileName().toString())) {
            return since(ctx) > 200 ? StepResult.fail("kein neuer Log") : StepResult.pending();
        }
        String problem = validate(saved.path(), sim.activePid());
        if (problem != null) {
            return StepResult.fail(problem);
        }
        long minimum = (long) (FLIGHT_TICKS / 20.0 * 2000.0 * 0.5);
        if (saved.frames() < minimum) {
            return StepResult.fail("zu wenige Frames: " + saved.frames() + " (mindestens " + minimum + ")");
        }
        ctx.log(String.format(Locale.ROOT, "Log %s: %d Frames, %d Bytes", saved.path().getFileName(), saved.frames(), saved.bytes()));
        return StepResult.pass();
    }

    static String validate(Path file, PidProfile pid) {
        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            return "Log nicht lesbar: " + e.getMessage();
        }
        String text = new String(data, StandardCharsets.ISO_8859_1);
        if (!text.startsWith("H Product:" + BlackboxEncoder.PRODUCT + "\n")) {
            return "Kopf beginnt nicht mit der Betaflight-Kennung";
        }
        String rollPid = "H rollPID:" + Math.round(pid.roll.p) + "," + Math.round(pid.roll.i) + "," + Math.round(pid.roll.d) + "\n";
        for (String needle : new String[]{"H Firmware revision:Betaflight 4.5.1 compatible (Propwash ", "H Craft name:" + BlackboxFlightFixture.CRAFT_NAME + "\n",
                rollPid, "H Field I name:loopIteration,time,", "H P interval:1/1\n", "H looptime:500\n"}) {
            if (!text.contains(needle)) {
                return "Kopfzeile fehlt: " + needle.trim();
            }
        }
        int dataStart = 0;
        while (dataStart < data.length && data[dataStart] == 'H') {
            int newline = text.indexOf('\n', dataStart);
            if (newline < 0) {
                return "Kopf endet nicht";
            }
            dataStart = newline + 1;
        }
        if (dataStart >= data.length || (data[dataStart] != 'S' && data[dataStart] != 'I')) {
            return "erster Frame ist weder S noch I";
        }
        if (!text.endsWith("E" + (char) BlackboxEncoder.EVENT_LOG_END + "End of log\0")) {
            return "Log endet nicht mit dem Betaflight-Logende";
        }
        return null;
    }

    private static StepResult keyStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        BlackboxRecorder recorder = BlackboxSession.INSTANCE.recorder(minecraft);
        if (phase(ctx) == 0) {
            BlackboxFlightFixture.enableBlackbox();
            StepResult piloting = BlackboxFlightFixture.freshDrone(ctx);
            if (piloting.status() != StepResult.Status.PASS) {
                return piloting;
            }
            if (BlackboxKeys.TOGGLE.isUnbound()) {
                return StepResult.fail("Taste „Blackbox pausieren / fortsetzen“ ist nicht belegt");
            }
            phase(ctx, 1);
            return StepResult.pending();
        }
        PilotSession session = PilotSession.INSTANCE;
        if (phase(ctx) == 1) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
            if (recorder.state() != BlackboxRecorder.State.LOGGING) {
                return since(ctx) > 100 ? StepResult.fail("Aufzeichnung startet nicht: " + recorder.state()) : StepResult.pending();
            }
            if (since(ctx) < 20) {
                return StepResult.pending();
            }
            minecraft.gui.toastManager().clear();
            KeyMapping.click(InputConstants.getKey(BlackboxKeys.TOGGLE.saveString()));
            phase(ctx, 2);
            return StepResult.pending();
        }
        if (phase(ctx) == 2) {
            if (recorder.state() != BlackboxRecorder.State.PAUSED) {
                return since(ctx) > 40 ? StepResult.fail("Taste pausiert nicht: " + recorder.state()) : StepResult.pending();
            }
            String toast = toast(minecraft);
            if (!Component.translatable("propwash.blackbox.toast.paused").getString().equals(toast)) {
                return since(ctx) > 40 ? StepResult.fail("Hinweis fehlt, statt dessen: " + toast) : StepResult.pending();
            }
            if (since(ctx) < 30) {
                return StepResult.pending();
            }
            KeyMapping.click(InputConstants.getKey(BlackboxKeys.TOGGLE.saveString()));
            phase(ctx, 3);
            return StepResult.pending();
        }
        if (phase(ctx) == 3) {
            if (recorder.state() != BlackboxRecorder.State.LOGGING) {
                return since(ctx) > 40 ? StepResult.fail("Taste setzt nicht fort: " + recorder.state()) : StepResult.pending();
            }
            if (since(ctx) < 20) {
                return StepResult.pending();
            }
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
            phase(ctx, 4);
            return StepResult.pending();
        }
        if (recorder.state() != BlackboxRecorder.State.READY || recorder.writer().currentFile() != null) {
            return since(ctx) > 200 ? StepResult.fail("Log nach dem Entschärfen nicht beendet") : StepResult.pending();
        }
        BlackboxWriter.Saved saved = recorder.writer().lastSaved();
        DroneSim sim = session.sim();
        if (saved == null || sim == null) {
            return StepResult.fail("kein Log nach Pause und Fortsetzen");
        }
        String problem = validate(saved.path(), sim.activePid());
        if (problem != null) {
            return StepResult.fail(problem);
        }
        ctx.log("Pause und Fortsetzen per Taste, Log " + saved.path().getFileName() + " mit " + saved.frames() + " Frames");
        return StepResult.pass();
    }

    private static StepResult offStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        BlackboxRecorder recorder = BlackboxSession.INSTANCE.recorder(minecraft);
        if (phase(ctx) == 0) {
            StepResult piloting = BlackboxFlightFixture.freshDrone(ctx);
            if (piloting.status() != StepResult.Status.PASS) {
                return piloting;
            }
            BlackboxFlightFixture.disableBlackbox();
            ctx.state().put(BEFORE, logNames(recorder.library()));
            phase(ctx, 1);
            return StepResult.pending();
        }
        if (phase(ctx) == 1) {
            if (recorder.state() == BlackboxRecorder.State.LOGGING) {
                return StepResult.fail("Blackbox zeichnet trotz Aus auf");
            }
            StepResult flown = BlackboxFlightFixture.fly(ctx, 30);
            if (flown.status() != StepResult.Status.PASS) {
                return flown;
            }
            phase(ctx, 2);
            return StepResult.pending();
        }
        if (since(ctx) < 20) {
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        Set<String> before = (Set<String>) ctx.state().get(BEFORE);
        Set<String> now = logNames(recorder.library());
        now.removeAll(before);
        BlackboxFlightFixture.restoreConfig();
        return StepResult.check(now.isEmpty() && recorder.state() == BlackboxRecorder.State.OFF,
                "ausgeschaltet: neue Logs " + now + ", Zustand " + recorder.state());
    }

    private static String toast(Minecraft minecraft) {
        ThemedToast toast = ThemedToast.find(minecraft.gui.toastManager(), BlackboxSession.TOAST_TOKEN);
        return toast == null ? null : toast.title().getString();
    }
}
