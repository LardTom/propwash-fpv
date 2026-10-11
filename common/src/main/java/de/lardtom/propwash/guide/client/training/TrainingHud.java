package de.lardtom.propwash.guide.client.training;

import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.client.GuideClient;
import de.lardtom.propwash.guide.client.GuideKeys;
import de.lardtom.propwash.guide.training.Course;
import de.lardtom.propwash.guide.training.Goal;
import de.lardtom.propwash.guide.training.Lesson;
import de.lardtom.propwash.guide.training.LessonRun;
import de.lardtom.propwash.guide.training.Placement;
import de.lardtom.propwash.guide.training.StarRule;
import de.lardtom.propwash.guide.training.TrainingSample;
import de.lardtom.propwash.guide.training.TrainingScale;
import de.lardtom.propwash.osd.client.OsdText;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TrainingHud {
    public static final String STAR = "★";
    public static final String EMPTY_STAR = "☆";
    private static final int PAD_X = 8;
    private static final int LINE = 11;
    private static final float TOP = 0.05F;
    private static final float BANNER = 0.3F;
    private static long framesDrawn;
    private static List<String> lastLines = List.of();

    private record Line(String text, int color, float scale) {
    }

    private TrainingHud() {
    }

    public static long framesDrawn() {
        return framesDrawn;
    }

    public static List<String> lastLines() {
        return lastLines;
    }

    public static String stars(int stars) {
        int clamped = Math.clamp(stars, 0, StarRule.MAX_STARS);
        return STAR.repeat(clamped) + EMPTY_STAR.repeat(StarRule.MAX_STARS - clamped);
    }

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        TrainingSession session = TrainingSession.INSTANCE;
        Lesson lesson = session.lesson();
        if (lesson == null || minecraft.player == null || minecraft.gui.screen() instanceof TrainingScreen || GuideClient.flightSchoolOpen()) {
            lastLines = List.of();
            return;
        }
        boolean osd = PilotSession.INSTANCE.isFpv();
        List<Line> top = new ArrayList<>();
        top.add(new Line(Component.translatable("propwash.guide.training.hud.lesson", lesson.code(),
                Component.translatable(lesson.titleKey())).getString().toUpperCase(Locale.ROOT), Palette.ACCENT, 1.0F));
        LessonRun run = session.run();
        Course course = session.course();
        switch (session.phase()) {
            case WAITING -> top.add(new Line(Component.translatable("propwash.guide.training.hud.waiting").getString(), Palette.TEXT, 1.0F));
            case AIRBORNE -> top.add(new Line(Component.translatable("propwash.guide.training.hud.land_first").getString(), Palette.TEXT, 1.0F));
            case BLOCKED -> top.add(new Line(blocked(session.placement()), Palette.WARNING, 1.0F));
            case ACTIVE -> {
                if (run != null && course != null) {
                    running(top, lesson, run, session.sample(), session.scale());
                }
            }
            default -> {
            }
        }
        List<Line> banner = banner(session.banner(), lesson, run);
        List<Line> messages = new ArrayList<>();
        for (TrainingSession.Message message : session.messages()) {
            messages.add(new Line(message.text().getString(), message.color(), 1.0F));
        }
        if (osd) {
            graphics.nextStratum();
        }
        int centerX = graphics.guiWidth() / 2;
        float y = graphics.guiHeight() * TOP;
        y = draw(graphics, minecraft.font, top, centerX, y, osd, Palette.ACCENT);
        draw(graphics, minecraft.font, messages, centerX, y + 4.0F, osd, 0);
        if (!banner.isEmpty()) {
            draw(graphics, minecraft.font, banner, centerX, graphics.guiHeight() * BANNER, osd,
                    banner.getFirst().color());
        }
        List<String> all = new ArrayList<>();
        for (Line line : top) {
            all.add(line.text());
        }
        for (Line line : banner) {
            all.add(line.text());
        }
        for (Line line : messages) {
            all.add(line.text());
        }
        lastLines = List.copyOf(all);
        framesDrawn++;
    }

    private static String blocked(Placement.@Nullable Result result) {
        if (result == null) {
            return Component.translatable("propwash.guide.training.hud.land_first").getString();
        }
        return switch (result.status()) {
            case NO_GROUND -> Component.translatable("propwash.guide.training.hud.no_ground").getString();
            case NOT_LOADED -> Component.translatable("propwash.guide.training.hud.not_loaded").getString();
            default -> Component.translatable("propwash.guide.training.hud.blocked", result.blocked()).getString();
        };
    }

    private static void running(List<Line> lines, Lesson lesson, LessonRun run, TrainingSample sample, TrainingScale scale) {
        StarRule rule = lesson.stars();
        if (run.state() == LessonRun.State.READY) {
            lines.add(new Line(Component.translatable("propwash.guide.training.hud.ready").getString(), Palette.TEXT, 1.0F));
            lines.add(new Line(targets(rule, scale, run.timeLimit()), Palette.TEXT_MUTED, 1.0F));
            return;
        }
        if (run.state() != LessonRun.State.RUNNING) {
            return;
        }
        Goal goal = run.goal();
        if (goal != null) {
            lines.add(new Line(goalText(goal, run, sample).getString(), Palette.TEXT, 1.0F));
        }
        double elapsed = run.elapsed();
        String clock = TrainingSession.time(elapsed);
        String next;
        if (rule.metric() == StarRule.Metric.TIME) {
            double three = rule.threeFor(scale);
            double two = rule.twoFor(scale);
            next = elapsed <= three ? stars(3) + " " + TrainingSession.time(three)
                    : elapsed <= two ? stars(2) + " " + TrainingSession.time(two)
                    : stars(1) + " " + TrainingSession.time(run.timeLimit());
        } else {
            next = Component.translatable("propwash.guide.training.hud.limit", TrainingSession.time(run.timeLimit())).getString();
        }
        lines.add(new Line(clock + "   " + next, Palette.TEXT_MUTED, 1.0F));
        if (run.assisted()) {
            lines.add(new Line(Component.translatable("propwash.guide.training.hud.assisted").getString(), Palette.WARNING, 1.0F));
        }
    }

    private static String targets(StarRule rule, TrainingScale scale, double limit) {
        if (rule.metric() == StarRule.Metric.PAD_DISTANCE) {
            return Component.translatable("propwash.guide.training.stars_distance", decimal(rule.threeFor(scale)),
                    decimal(rule.twoFor(scale))).getString();
        }
        return Component.translatable("propwash.guide.training.stars_time", TrainingSession.time(rule.threeFor(scale)),
                TrainingSession.time(rule.twoFor(scale)), TrainingSession.time(limit)).getString();
    }

    public static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0, value));
    }

    public static Component goalText(Goal goal, LessonRun run, TrainingSample sample) {
        return switch (goal) {
            case Goal.Arm arm -> run.armedTime() < arm.seconds()
                    ? Component.translatable("propwash.guide.training.goal.arm", oneDecimal(run.armedTime()), oneDecimal(arm.seconds()))
                    : Component.translatable("propwash.guide.training.goal.arm.disarm");
            case Goal.Hover hover -> Component.translatable(run.inZone() ? "propwash.guide.training.goal.hover"
                    : "propwash.guide.training.goal.hover.outside", oneDecimal(run.zoneTime()), oneDecimal(hover.seconds()));
            case Goal.Heading heading -> {
                int step = Math.min(run.markerStep() + 1, heading.markers().size());
                if (!run.inZone()) {
                    yield Component.translatable("propwash.guide.training.goal.heading.box");
                }
                if (run.holdTime() > 0.0) {
                    yield Component.translatable("propwash.guide.training.goal.heading.hold", step, heading.markers().size());
                }
                double error = run.headingError();
                Component turn = Component.translatable(Double.isFinite(error) && error < 0.0 ? "propwash.guide.training.goal.heading.left"
                        : "propwash.guide.training.goal.heading.right");
                yield Component.translatable("propwash.guide.training.goal.heading", step, heading.markers().size()).append(" · ").append(turn);
            }
            case Goal.Rings rings -> Component.translatable("propwash.guide.training.goal.rings",
                    Math.min(run.nextRing() + 1, rings.count()), rings.count());
            case Goal.Laps laps -> run.lapsStarted()
                    ? Component.translatable("propwash.guide.training.goal.laps", Math.max(1, Math.min(run.lap(), laps.laps())), laps.laps(),
                    Math.max(1, run.activeRing() - laps.from() + 1), laps.count())
                    : Component.translatable("propwash.guide.training.goal.laps.start");
            case Goal.Land land -> {
                double distance = run.padDistance();
                boolean onPad = Double.isFinite(distance) && distance <= run.course().padRadius(land.pad()) && sample.nearGround();
                yield onPad ? Component.translatable("propwash.guide.training.goal.land.disarm")
                        : Component.translatable("propwash.guide.training.goal.land", oneDecimal(Double.isFinite(distance) ? distance : 0.0));
            }
            case Goal.Tricks tricks -> run.inZone()
                    ? Component.translatable("propwash.guide.training.goal.tricks", trickName(tricks.types()), run.trickCount(), tricks.count())
                    : Component.translatable("propwash.guide.training.goal.tricks.outside", run.trickCount(), tricks.count());
            case Goal.Combo combo -> Component.translatable(run.inZone() ? "propwash.guide.training.goal.combo"
                    : "propwash.guide.training.goal.combo.outside", Math.max(run.comboBest(), run.comboNow()), combo.tricks());
        };
    }

    public static Component trickName(Set<TrickType> types) {
        if (types.isEmpty()) {
            return Component.translatable("propwash.guide.training.goal.tricks.any");
        }
        TrickType base = types.iterator().next().variantOf();
        for (TrickType type : types) {
            if (type.variantOf() != base) {
                return Component.translatable("propwash.guide.training.goal.tricks.any");
            }
        }
        return Component.translatable(base.translationKey());
    }

    private static List<Line> banner(TrainingSession.@Nullable Banner banner, Lesson lesson, @Nullable LessonRun run) {
        List<Line> lines = new ArrayList<>();
        if (banner == null) {
            return lines;
        }
        if (banner.success()) {
            lines.add(new Line(Component.translatable("propwash.guide.training.result.success").getString() + "  " + stars(banner.stars()),
                    Palette.SUCCESS, 2.0F));
            lines.add(new Line(banner.distance()
                    ? Component.translatable("propwash.guide.training.result.distance", decimal(banner.metric())).getString()
                    : Component.translatable("propwash.guide.training.result.time", TrainingSession.time(banner.time())).getString(),
                    Palette.TEXT, 1.0F));
            if (banner.bestTime()) {
                lines.add(new Line(Component.translatable("propwash.guide.training.result.best", TrainingSession.time(banner.previousTime()))
                        .getString(), Palette.ACCENT, 1.0F));
            } else if (banner.moreStars()) {
                lines.add(new Line(Component.translatable("propwash.guide.training.result.more_stars", banner.stars()).getString(),
                        Palette.ACCENT, 1.0F));
            }
            if (banner.assisted()) {
                lines.add(new Line(Component.translatable("propwash.guide.training.result.assisted").getString(), Palette.WARNING, 1.0F));
            }
        } else {
            lines.add(new Line(Component.translatable("propwash.guide.training.result.failed").getString(), Palette.ERROR, 2.0F));
            if (banner.failure() != null) {
                lines.add(new Line(Component.translatable("propwash.guide.training.failure." + banner.failure().key()).getString(),
                        Palette.TEXT, 1.0F));
            }
        }
        boolean ready = run != null && run.state() == LessonRun.State.READY;
        if (!ready) {
            String hint = Component.translatable("propwash.guide.training.hud.retry").getString();
            if (banner.success()) {
                hint += " · " + Component.translatable("propwash.guide.training.hud.next", GuideKeys.label()).getString();
            }
            lines.add(new Line(hint, Palette.TEXT_MUTED, 1.0F));
        }
        return lines;
    }

    private static List<Line> wrap(GuiGraphicsExtractor graphics, Font font, List<Line> lines, boolean osd) {
        int room = graphics.guiWidth() - 2 * (PAD_X + 4);
        List<Line> out = new ArrayList<>();
        for (Line line : lines) {
            float scale = osd ? OsdText.scale(graphics, line.scale()) : line.scale();
            int width = Math.max(20, (int) (room / scale));
            if (font.width(line.text()) <= width) {
                out.add(line);
                continue;
            }
            for (FormattedText part : font.getSplitter().splitLines(line.text(), width, Style.EMPTY)) {
                out.add(new Line(part.getString().strip(), line.color(), line.scale()));
            }
        }
        return out;
    }

    private static float draw(GuiGraphicsExtractor graphics, Font font, List<Line> unwrapped, int centerX, float y, boolean osd, int accent) {
        if (unwrapped.isEmpty()) {
            return y;
        }
        List<Line> lines = wrap(graphics, font, unwrapped, osd);
        if (osd) {
            float cursor = y;
            for (Line line : lines) {
                float scale = OsdText.scale(graphics, line.scale());
                OsdText.drawCentered(graphics, font, line.text(), centerX, cursor, scale, line.color());
                cursor += OsdText.height(scale) + 3.0F * OsdText.scale(graphics, 1.0F);
            }
            return cursor;
        }
        int width = 0;
        int height = 0;
        for (Line line : lines) {
            width = Math.max(width, Math.round(font.width(line.text()) * line.scale()));
            height += Math.round(LINE * line.scale());
        }
        int half = width / 2 + PAD_X;
        int top = Math.round(y);
        int bottom = top + height + 6;
        if (accent != 0) {
            ThemeDraw.notice(graphics, centerX - half, top, centerX + half, bottom, accent | 0xFF000000, 1.0F);
        } else {
            ThemeDraw.notice(graphics, centerX - half, top, centerX + half, bottom, Palette.BORDER_STRONG, 0.85F);
        }
        int cursor = top + 4;
        for (Line line : lines) {
            if (line.scale() > 1.0F) {
                Matrix3x2fStack pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(centerX + 1, cursor);
                pose.scale(line.scale(), line.scale());
                graphics.centeredText(font, Component.literal(line.text()), 0, 0, line.color());
                pose.popMatrix();
            } else {
                ThemeText.textCentered(graphics, font, line.text(), centerX + 1, cursor, line.color());
            }
            cursor += Math.round(LINE * line.scale());
        }
        return bottom;
    }
}
