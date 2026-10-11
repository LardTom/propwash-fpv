package de.lardtom.propwash.content;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.BuildWarning;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BuildTexts {
    private static final String WARNING_KEY = "propwash.content.workbench.warning.";
    private static final String LIFTED_KEY = "propwash.content.workbench.lifted";

    public enum Severity {
        PROBLEM,
        WARNING,
        NOTE
    }

    public record Line(Component text, Severity severity) {
    }

    private BuildTexts() {
    }

    public static Component warning(BuildWarning warning) {
        return Component.translatable(WARNING_KEY + warning.name().toLowerCase(Locale.ROOT));
    }

    public static List<BuildWarning> analysisWarnings(DroneBuild build, @Nullable BuildAnalysis analysis) {
        List<BuildWarning> result = new ArrayList<>();
        if (analysis == null) {
            return result;
        }
        List<DroneBuild.Issue> issues = build.warnings();
        for (BuildWarning warning : BuildWarning.values()) {
            if (!analysis.warnings().contains(warning)) {
                continue;
            }
            if (warning == BuildWarning.BATTERY_OVERLOAD && issues.contains(DroneBuild.Issue.BATTERY_WEAK)) {
                continue;
            }
            if (warning == BuildWarning.ESC_CURRENT_LIMITED && issues.contains(DroneBuild.Issue.ESC_UNDERSIZED)) {
                continue;
            }
            result.add(warning);
        }
        return result;
    }

    public static List<Line> lines(DroneBuild build, @Nullable BuildAnalysis analysis) {
        return lines(build, analysis, PartCompatibility.ENFORCED);
    }

    public static List<Line> lines(DroneBuild build, @Nullable BuildAnalysis analysis, PartCompatibility rule) {
        List<Line> lines = new ArrayList<>();
        for (DroneBuild.Issue issue : build.problems(rule)) {
            lines.add(new Line(Component.translatable(issue.translationKey()), Severity.PROBLEM));
        }
        for (DroneBuild.Issue issue : build.liftedProblems(rule)) {
            lines.add(new Line(Component.translatable(LIFTED_KEY, Component.translatable(issue.translationKey())), Severity.WARNING));
        }
        for (DroneBuild.Issue issue : build.warnings()) {
            lines.add(new Line(Component.translatable(issue.translationKey()), Severity.WARNING));
        }
        for (BuildWarning warning : analysisWarnings(build, analysis)) {
            lines.add(new Line(warning(warning), warning == BuildWarning.CANNOT_HOVER ? Severity.PROBLEM : Severity.NOTE));
        }
        return lines;
    }
}
