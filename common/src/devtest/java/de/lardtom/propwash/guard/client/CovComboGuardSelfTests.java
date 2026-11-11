package de.lardtom.propwash.guard.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.guard.GuardReport;
import de.lardtom.propwash.guard.NamespaceGuard;
import de.lardtom.propwash.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CovComboGuardSelfTests {
    public static final String NAME = "guard: Abdeckung cov-combo – mit Just More Parts 1.2.0, Mob Defense und Kamera-Add-on: 0 Funde, "
            + "kein Hinweis, Log-Zeile mit 0 findings für start, resources und server start";
    public static final List<String> MODS = List.of("justmoreparts", "mobdefense", "camdrones");
    public static final List<String> PHASES = List.of("start", "resources", "server start");
    private static final Pattern LINE = Pattern.compile("Namespace check \\(([^)]+)\\): (\\d+) findings in ([0-9.]+) ms");
    private static final Pattern GUARD_ERROR = Pattern.compile("/ERROR\\].*(Namespace check|reserved namespace|propwash namespace)");

    private CovComboGuardSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(NAME, 400, CovComboGuardSelfTests::step);
    }

    static Map<String, List<String>> phases(List<String> lines) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher matcher = LINE.matcher(line);
            if (matcher.find()) {
                out.computeIfAbsent(matcher.group(1), k -> new ArrayList<>()).add(matcher.group(2) + " in " + matcher.group(3) + " ms");
            }
        }
        return out;
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        List<String> missing = new ArrayList<>();
        Map<String, String> versions = new LinkedHashMap<>();
        for (String mod : MODS) {
            if (!Services.PLATFORM.isModLoaded(mod)) {
                missing.add(mod);
            } else {
                versions.put(mod, Services.PLATFORM.modVersion(mod).orElse("?"));
            }
        }
        if (!missing.isEmpty()) {
            ctx.log("nicht geladen: " + missing + ", Kombi-Lauf nur mit allen drei Jars in mods/, übersprungen");
            return StepResult.pass();
        }
        if ((GuardClient.logins() < 1 || NamespaceGuard.runs() < 3) && ctx.tick() < 399) {
            return StepResult.pending();
        }
        List<String> problems = new ArrayList<>();
        GuardReport report = NamespaceGuard.report();
        if (report.count() != 0) {
            problems.add(report.count() + " Funde: " + report.mods());
        }
        if (GuardClient.hints() != 0) {
            problems.add(GuardClient.hints() + " Namensraum-Hinweise im Chat");
        }
        Path log = Services.PLATFORM.getGameDir().resolve("logs").resolve("latest.log");
        Map<String, List<String>> phases;
        int guardErrors = 0;
        try {
            List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
            phases = phases(lines);
            for (String line : lines) {
                if (GUARD_ERROR.matcher(line).find()) {
                    guardErrors++;
                }
            }
        } catch (IOException e) {
            return StepResult.fail("logs/latest.log nicht lesbar: " + e);
        }
        for (String phase : PHASES) {
            List<String> found = phases.get(phase);
            if (found == null || found.isEmpty()) {
                problems.add("keine Zeile „Namespace check (" + phase + ")“ im Log");
            } else {
                for (String entry : found) {
                    if (!entry.startsWith("0 ")) {
                        problems.add("Namespace check (" + phase + "): " + entry);
                    }
                }
            }
        }
        if (guardErrors > 0) {
            problems.add(guardErrors + " ERROR-Zeilen der Namensraum-Prüfung");
        }
        ctx.log("Mods " + versions + ", " + NamespaceGuard.runs() + " Prüfungen, " + report.count() + " Funde, " + GuardClient.hints() + " Hinweise, Log "
                + phases);
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }
}
