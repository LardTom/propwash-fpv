package de.lardtom.propwash.race.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.race.AssistChecklist;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.AssistWords;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.server.client.ClientPersonalRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.ui.client.DroneContext;
import de.lardtom.propwash.video.client.VideoConfig;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AssistPreflight {
    private static final String KEY = "propwash.race.preflight.";
    private static final String RULES_KEY = "propwash.race_rules.preflight.rules";
    public static final String RACE_LEGAL_KEY = "propwash.rules.my.race_legal";

    private AssistPreflight() {
    }

    public record Row(AssistChecklist.Status status, Component label, Component detail) {
    }

    public record Result(AssistReasons reasons, List<Row> rows, Component summary, boolean saved, boolean piloting) {
        public boolean clean() {
            return this.reasons.none();
        }
    }

    public static Result check(Minecraft minecraft) {
        return check(minecraft, ClientRaceTracker.INSTANCE.courseId());
    }

    public static Result check(Minecraft minecraft, int course) {
        DroneContext context = DroneContext.resolve(minecraft);
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity piloted = context.piloting() ? context.entity() : null;
        DroneBuild build = context.hasDrone() ? context.build() : null;
        VideoSystem goggles = VideoState.INSTANCE.goggles(minecraft.player);
        AssistReasons reasons;
        if (piloted != null) {
            reasons = AssistText.evaluate(session, piloted);
            HoldMode requested = requestedHold();
            if (!reasons.has(LapAssist.HOLD_MODE) && requested != HoldMode.OFF) {
                reasons = reasons.plus(AssistText.preflight(null, null, requested).only(LapAssist.HOLD_MODE.bit()));
            }
        } else {
            reasons = AssistText.preflight(build, goggles, requestedHold());
        }
        reasons = ClientCreativeFc.apply(reasons, course);
        boolean creativeLegal = build != null && build.isCreative() && ClientCreativeFc.legal(course);
        boolean saved = ClientWorldRules.INSTANCE.on(WorldRuleSpecs.SAVE_ASSISTED_LAPS);
        List<Row> rows = new ArrayList<>();
        for (AssistChecklist.Item item : AssistChecklist.items(reasons, build != null, goggles != null)) {
            rows.add(new Row(item.status(), label(item.category()), capitalized(detail(item, context, goggles, creativeLegal))));
        }
        rows.add(new Row(AssistChecklist.Status.INFO, Component.translatable(KEY + "input"), capitalized(input())));
        rows.add(new Row(AssistChecklist.Status.INFO, Component.translatable(KEY + "saving"),
                capitalized(Component.translatable(KEY + (saved ? "saving.on" : "saving.off")))));
        if (!ClientWorldRules.INSTANCE.known()) {
            rows.add(new Row(AssistChecklist.Status.INFO, Component.translatable(KEY + "sync"),
                    capitalized(Component.translatable(KEY + "sync.pending"))));
        }
        Component summary;
        if (reasons.none()) {
            summary = Component.translatable(KEY + "summary.clean");
        } else {
            summary = Component.translatable(KEY + (saved ? "summary.assisted_saved" : "summary.assisted"));
        }
        return new Result(reasons, List.copyOf(rows), summary, saved, piloted != null);
    }

    public static boolean raceLegalOffered() {
        ClientPersonalRules rules = ClientPersonalRules.INSTANCE;
        return rules.known() && !rules.deviations().isEmpty();
    }

    public static boolean applyRaceLegal() {
        return raceLegalOffered() && ClientPersonalRules.INSTANCE.apply(Map.of());
    }

    static Component label(AssistChecklist.Category category) {
        return category == AssistChecklist.Category.PHYSICS ? Component.translatable(RULES_KEY) : Component.translatable(KEY + category.key());
    }

    static Component capitalized(Component text) {
        String plain = text.getString();
        if (plain.isEmpty() || !Character.isLowerCase(plain.codePointAt(0))) {
            return text;
        }
        int first = plain.codePointAt(0);
        return Component.literal(new StringBuilder().appendCodePoint(Character.toUpperCase(first)).append(plain.substring(Character.charCount(first)))
                .toString());
    }

    public static HoldMode requestedHold() {
        HoldMode live = PilotSession.INSTANCE.holdMode();
        if (live != HoldMode.OFF) {
            return live;
        }
        RadioState radio = RadioState.INSTANCE;
        if (radio.isActive(SwitchFunction.POS_HOLD)) {
            return HoldMode.POSHOLD;
        }
        return radio.isActive(SwitchFunction.ALT_HOLD) ? HoldMode.ALTHOLD : HoldMode.OFF;
    }

    private static Component detail(AssistChecklist.Item item, DroneContext context, @Nullable VideoSystem goggles, boolean creativeLegal) {
        if (item.status() == AssistChecklist.Status.FLAGGED) {
            MutableComponent out = Component.empty();
            List<LapAssist> reasons = item.reasons().list();
            for (int i = 0; i < reasons.size(); i++) {
                if (i > 0) {
                    out.append(Component.translatable(AssistWords.SEPARATOR));
                }
                out.append(AssistWords.reason(reasons.get(i), item.reasons().details(reasons.get(i)), true));
            }
            return out;
        }
        String key = KEY + item.category().key();
        return switch (item.category()) {
            case PHYSICS -> Component.translatable(RULES_KEY + ".ok");
            case DRONE -> !context.hasDrone() ? Component.translatable(key + ".none") : creativeLegal
                    ? Component.translatable(ClientCreativeFc.KEY + "preflight", context.name()) : Component.translatable(key + ".ok", context.name());
            case GOGGLES -> goggles == null ? Component.translatable(key + ".none")
                    : goggles == VideoSystem.CREATIVE ? Component.translatable(key + ".creative_ok", creativeLatency())
                    : Component.translatable(key + ".ok", Component.translatable("propwash.video.system." + goggles.name().toLowerCase(Locale.ROOT)));
            case HOLD -> Component.translatable(key + ".ok");
            case RESCUE -> Component.translatable(key + ".ok");
        };
    }

    private static Component creativeLatency() {
        VideoConfig config = VideoSettings.get();
        return config.creativeLatencyFromModule ? Component.translatable(KEY + "goggles.from_module")
                : Component.translatable(KEY + "goggles.own", config.creativeLatencyMs);
    }

    private static Component input() {
        RadioSource source = RadioState.INSTANCE.source();
        if (source == RadioSource.NONE) {
            return Component.translatable(KEY + "input.none");
        }
        return Component.translatable(KEY + "input.ok", Component.translatable(source.translationKey()));
    }
}
