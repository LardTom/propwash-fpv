package de.lardtom.propwash.ui;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LayoutAudit {
    public static final int MARQUEE_HEIGHT = 14;
    public static final int SLACK = 1;
    public static final int SIDE_SLACK = 2;
    private static final int OVERLAP_MIN = 2;
    private static final int OVERLAP_MIN_WIDTH = 3;
    private static final int TWIN_OFFSET = 2;
    private static final int COVER_MIN_WIDTH = 4;
    private static final int COVER_MIN_HEIGHT = 2;
    private static final int COVER_MIN_SIZE = 8;

    private LayoutAudit() {
    }

    public enum Kind {
        OFFSCREEN,
        CLIPPED,
        HIDDEN,
        MARQUEE,
        TEXT_OVERLAP,
        WIDGET_OVERLAP,
        TEXT_ON_WIDGET,
        CUT,
        COVERED,
        TOOLTIP,
        SCROLLBAR
    }

    public record Box(int left, int top, int right, int bottom) {
        public static Box of(int x, int y, int width, int height) {
            return new Box(x, y, x + width, y + height);
        }

        public int width() {
            return this.right - this.left;
        }

        public int height() {
            return this.bottom - this.top;
        }

        public boolean empty() {
            return this.right <= this.left || this.bottom <= this.top;
        }

        public boolean contains(Box other, int slack) {
            return other.left >= this.left - slack && other.top >= this.top - slack && other.right <= this.right + slack
                    && other.bottom <= this.bottom + slack;
        }

        public boolean containsPoint(int x, int y) {
            return x >= this.left && x < this.right && y >= this.top && y < this.bottom;
        }

        public @Nullable Box intersection(Box other) {
            Box box = new Box(Math.max(this.left, other.left), Math.max(this.top, other.top), Math.min(this.right, other.right),
                    Math.min(this.bottom, other.bottom));
            return box.empty() ? null : box;
        }

        public int centerX() {
            return (this.left + this.right) / 2;
        }

        public int centerY() {
            return (this.top + this.bottom) / 2;
        }

        public String describe() {
            return this.left + "," + this.top + " " + this.width() + "x" + this.height();
        }
    }

    public record Text(String value, Box full, @Nullable Box clip, int layer) {
        public @Nullable Box visible() {
            return this.clip == null ? this.full : this.clip.intersection(this.full);
        }

        public boolean above() {
            return this.clip != null && this.full.top() < this.clip.top() - SLACK;
        }

        public boolean below() {
            return this.clip != null && this.full.bottom() > this.clip.bottom() + SLACK;
        }

        public boolean sideways() {
            return this.clip != null && (this.full.left() < this.clip.left() - SIDE_SLACK || this.full.right() > this.clip.right() + SIDE_SLACK);
        }
    }

    public record Widget(String name, Box box) {
    }

    public record Finding(Kind kind, String subject, String detail) {
        public String describe() {
            return this.kind + " \"" + this.subject + "\" " + this.detail;
        }
    }

    public static List<Finding> statics(Box screen, List<Text> texts, List<Widget> widgets) {
        Set<Finding> out = new LinkedHashSet<>();
        for (Text text : texts) {
            Box clip = text.clip();
            if (clip != null && clip.empty()) {
                continue;
            }
            Box visible = text.visible();
            if (visible == null) {
                if (!text.above() && !text.below()) {
                    out.add(new Finding(Kind.HIDDEN, text.value(), "seitlich außerhalb von " + describe(clip)));
                }
                continue;
            }
            if (!screen.contains(visible, SLACK)) {
                out.add(new Finding(Kind.OFFSCREEN, text.value(), "Text " + visible.describe() + " außerhalb des Bildschirms " + screen.describe()));
            }
            if (text.sideways()) {
                boolean marquee = clip != null && clip.height() <= MARQUEE_HEIGHT && !text.above() && !text.below();
                out.add(new Finding(marquee ? Kind.MARQUEE : Kind.CLIPPED, text.value(),
                        "seitlich: Text " + text.full().describe() + " in " + describe(clip)));
            }
        }
        for (Widget widget : widgets) {
            if (!screen.contains(widget.box(), 0)) {
                out.add(new Finding(Kind.OFFSCREEN, widget.name(), "Widget " + widget.box().describe() + " außerhalb des Bildschirms "
                        + screen.describe()));
            }
        }
        for (int i = 0; i < texts.size(); i++) {
            Text a = texts.get(i);
            Box boxA = shown(a);
            if (boxA == null) {
                continue;
            }
            for (int j = i + 1; j < texts.size(); j++) {
                Text b = texts.get(j);
                if (a.layer() != b.layer()) {
                    continue;
                }
                Box boxB = shown(b);
                if (boxB == null || !collides(boxA, boxB) || twins(a, b)) {
                    continue;
                }
                out.add(new Finding(Kind.TEXT_OVERLAP, a.value(), "über \"" + b.value() + "\": " + boxA.describe() + " und " + boxB.describe()));
            }
        }
        for (int i = 0; i < widgets.size(); i++) {
            Widget a = widgets.get(i);
            for (int j = i + 1; j < widgets.size(); j++) {
                Widget b = widgets.get(j);
                if (a.box().intersection(b.box()) == null || a.box().contains(b.box(), 0) || b.box().contains(a.box(), 0)) {
                    continue;
                }
                out.add(new Finding(Kind.WIDGET_OVERLAP, a.name(), "über \"" + b.name() + "\": " + a.box().describe() + " und "
                        + b.box().describe()));
            }
        }
        for (Text text : texts) {
            Box box = shown(text);
            if (box == null) {
                continue;
            }
            for (Widget widget : widgets) {
                if (!collides(box, widget.box()) || widget.box().contains(box, SIDE_SLACK)) {
                    continue;
                }
                out.add(new Finding(Kind.TEXT_ON_WIDGET, text.value(), "ragt in \"" + widget.name() + "\": " + box.describe() + " und "
                        + widget.box().describe()));
            }
        }
        return new ArrayList<>(out);
    }

    public record Cover(Box box, int textsBefore) {
    }

    public static List<Finding> covered(List<Text> texts, List<Cover> covers) {
        Set<Finding> out = new LinkedHashSet<>();
        for (Cover cover : covers) {
            if (cover.box().width() < COVER_MIN_SIZE || cover.box().height() < COVER_MIN_SIZE) {
                continue;
            }
            int before = Math.min(cover.textsBefore(), texts.size());
            for (int i = 0; i < before; i++) {
                Text text = texts.get(i);
                Box box = shown(text);
                if (box == null) {
                    continue;
                }
                Box common = box.intersection(cover.box());
                if (common == null || common.width() < COVER_MIN_WIDTH || common.height() < COVER_MIN_HEIGHT) {
                    continue;
                }
                boolean redrawn = false;
                for (int j = before; j < texts.size() && !redrawn; j++) {
                    redrawn = twins(text, texts.get(j));
                }
                if (!redrawn) {
                    out.add(new Finding(Kind.COVERED, text.value(), "verdeckt von einer später gezeichneten Fläche " + cover.box().describe()
                            + ": Text " + box.describe()));
                }
            }
        }
        return new ArrayList<>(out);
    }

    public static List<Finding> vertical(List<Text> texts, int pointX, int pointY, boolean atTop, boolean atBottom) {
        Set<Finding> out = new LinkedHashSet<>();
        for (Text text : texts) {
            Box clip = text.clip();
            if (clip == null || clip.empty() || !clip.containsPoint(pointX, pointY)) {
                continue;
            }
            Kind kind = text.visible() == null ? Kind.HIDDEN : Kind.CLIPPED;
            if (atTop && text.above()) {
                out.add(new Finding(kind, text.value(), "oben: Text " + text.full().describe() + " in " + clip.describe()));
            }
            if (atBottom && text.below()) {
                out.add(new Finding(kind, text.value(), "unten: Text " + text.full().describe() + " in " + clip.describe()));
            }
        }
        return new ArrayList<>(out);
    }

    public static List<Box> scrollRegions(List<Text> texts) {
        Set<Box> out = new LinkedHashSet<>();
        for (Text text : texts) {
            Box clip = text.clip();
            if (clip != null && !clip.empty() && (text.above() || text.below())) {
                out.add(clip);
            }
        }
        return new ArrayList<>(out);
    }

    public static List<Box> clipRegions(List<Text> texts) {
        Set<Box> out = new LinkedHashSet<>(scrollRegions(texts));
        for (Text text : texts) {
            Box clip = text.clip();
            if (clip != null && !clip.empty()) {
                out.add(clip);
            }
        }
        return new ArrayList<>(out);
    }

    public static @Nullable Finding tooltip(Box screen, String firstLine, Box text) {
        boolean wide = !TooltipPlacement.fits(text.left() - screen.left(), text.width(), screen.width());
        boolean tall = !TooltipPlacement.fits(text.top() - screen.top(), text.height(), screen.height());
        if (!wide && !tall) {
            return null;
        }
        String why = text.width() > TooltipPlacement.room(screen.width()) ? "ist breiter als der Bildschirm"
                : text.height() > TooltipPlacement.room(screen.height()) ? "ist höher als der Bildschirm" : "ragt aus dem Bildschirm";
        return new Finding(Kind.TOOLTIP, firstLine, why + ": Text " + text.describe() + " in " + screen.width() + "x" + screen.height());
    }

    public static Finding deadScrollbar(Box track, Box thumb, String why) {
        return new Finding(Kind.SCROLLBAR, "Scrollleiste " + track.describe(), why + " (Griff " + thumb.describe() + ")");
    }

    public static Finding cut(String full, String shown) {
        return new Finding(Kind.CUT, full, "gekürzt zu \"" + shown + "\"");
    }

    private static @Nullable Box shown(Text text) {
        Box clip = text.clip();
        if (clip != null && clip.empty()) {
            return null;
        }
        return text.visible();
    }

    private static boolean collides(Box a, Box b) {
        Box common = a.intersection(b);
        return common != null && common.width() >= OVERLAP_MIN_WIDTH && common.height() >= OVERLAP_MIN;
    }

    private static boolean twins(Text a, Text b) {
        return a.value().equals(b.value()) && Math.abs(a.full().left() - b.full().left()) <= TWIN_OFFSET
                && Math.abs(a.full().top() - b.full().top()) <= TWIN_OFFSET;
    }

    private static String describe(@Nullable Box box) {
        return box == null ? "-" : box.describe();
    }
}
