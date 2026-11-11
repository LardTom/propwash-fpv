package de.lardtom.propwash.guard;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.platform.Services;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CovGuardServerChecks {
    public static final String NOCLIP = "pwnoclip";
    private static String summary = "";

    private CovGuardServerChecks() {
    }

    public static List<String> section(String text) {
        int start = text.indexOf(GuardCrash.TITLE + ":");
        if (start < 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : text.substring(start).lines().toList()) {
            if (!out.isEmpty() && !line.startsWith("\t\t")) {
                break;
            }
            out.add(line.strip());
        }
        return out;
    }

    public static @Nullable String sectionProblem(List<String> lines, GuardReport report, Map<String, Set<String>> mixins, List<ModMeta> addons) {
        String joined = String.join("\n", lines);
        List<String> problems = new ArrayList<>();
        if (lines.isEmpty()) {
            return "Abschnitt „" + GuardCrash.TITLE + "“ fehlt";
        }
        if (addons.isEmpty()) {
            if (!joined.contains("Add-ons: none")) {
                problems.add("ohne Add-on nicht „Add-ons: none“");
            }
        } else {
            for (ModMeta addon : addons) {
                String line = addon.name() + " (" + addon.id() + ") " + addon.version();
                if (!lines.contains(line)) {
                    problems.add("Add-on-Zeile „" + line + "“ fehlt");
                }
            }
        }
        if (mixins.isEmpty()) {
            if (!joined.contains("Mixins into Propwash internals: none")) {
                problems.add("ohne fremde Mixins nicht „Mixins into Propwash internals: none“");
            }
        } else {
            for (Map.Entry<String, Set<String>> entry : mixins.entrySet()) {
                if (!lines.contains(entry.getKey() + ": " + entry.getValue().size() + " classes")) {
                    problems.add("Mixin-Zeile für " + entry.getKey() + " mit " + entry.getValue().size() + " Klassen fehlt");
                }
            }
        }
        if (report.count() == 0) {
            if (!joined.contains("Namespace check: no findings")) {
                problems.add("ohne Funde nicht „Namespace check: no findings“");
            }
        } else {
            for (Map.Entry<String, Integer> entry : report.summary().entrySet()) {
                if (!lines.contains(entry.getKey() + ": " + entry.getValue() + " findings")) {
                    problems.add("Fund-Zeile „" + entry.getKey() + ": " + entry.getValue() + " findings“ fehlt");
                }
            }
        }
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    public static @Nullable String check(MinecraftServer server) {
        String text;
        String source;
        if (GuardCrash.viaCallable()) {
            text = GuardCrash.TITLE + ":" + GuardCrash.section() + "\n";
            source = "Crash-Callable direkt (ein CrashReport schreibt unter NeoForge eine FATAL-Zeile ins Log des Rauchtests)";
        } else {
            CrashReport report = CrashReport.forThrowable(new IllegalStateException("Propwash Abdeckung cov-api: Absturzbericht am Server"),
                    "Propwash coverage crash report");
            server.fillSystemReport(report.getSystemReport());
            Path file = server.getServerDirectory().resolve("crash-reports").resolve("crash-cov-api-" + System.currentTimeMillis() + "-server.txt");
            if (!report.saveToFile(file, ReportType.CRASH)) {
                return "Absturzbericht nicht gespeichert: " + file;
            }
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
                Files.deleteIfExists(file);
            } catch (IOException e) {
                return "Absturzbericht nicht lesbar: " + e;
            }
            source = "crash-reports/" + file.getFileName() + " (Systembericht)";
        }
        List<String> lines = section(text);
        Map<String, Set<String>> mixins = MixinScan.foreignTargets();
        List<ModMeta> addons = Services.PLATFORM.dependentMods(Constants.MOD_ID);
        String problem = sectionProblem(lines, NamespaceGuard.report(), mixins, addons);
        if (problem != null) {
            return problem + " (" + String.join(" | ", lines) + ")";
        }
        StringBuilder out = new StringBuilder(source + ": " + String.join(" | ", lines));
        if (Services.PLATFORM.isModLoaded(NOCLIP)) {
            out.append("; Noclip am Server: Funde ").append(NamespaceGuard.report().categories(NOCLIP)).append(", Mixins ")
                    .append(mixins.getOrDefault(NOCLIP, Set.of()).size()).append(" Klassen");
            if (NamespaceGuard.report().categories(NOCLIP).isEmpty()) {
                return "Noclip am Server ohne Funde der Namensraum-Prüfung";
            }
        }
        summary = out.toString();
        return null;
    }

    public static String summary() {
        return summary;
    }
}
