package de.lardtom.propwash.trick.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.trick.TrickCatalog;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class TrickListLayoutTour {
    public static final String NAME = "tricks: Kopfzeilen bleiben nach Klick in gescrollter Liste, Zurück, Escape, Scrollen und Neuaufbau an ihrer Stelle";
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
    private static final int SETTLE = 3;
    private static final int MAX_NOTCHES = 400;

    record Head(String name, ScreenRectangle rect, boolean visible) {
    }

    record Snapshot(Map<TrickType, ScreenRectangle> rows, List<Head> heads, int clipBottom) {
        static Snapshot of(TrickConfigTab tab) {
            Map<TrickType, ScreenRectangle> rows = new EnumMap<>(TrickType.class);
            for (TrickType type : TrickType.REPERTOIRE) {
                ScreenRectangle row = tab.rowOf(type);
                if (row != null) {
                    rows.put(type, row);
                }
            }
            List<Head> heads = new ArrayList<>();
            head(heads, "Trick-Erkennung", tab.modeButton());
            head(heads, "Suche", tab.searchBox());
            head(heads, "Familie", tab.familyButton());
            return new Snapshot(rows, heads, tab.clipBottom());
        }

        private static void head(List<Head> out, String name, @Nullable AbstractWidget widget) {
            if (widget != null) {
                out.add(new Head(name, widget.getRectangle(), widget.visible));
            }
        }
    }

    private int phase;
    private int since;
    private int round;
    private int target;
    private int checked;
    private int notches;
    private int scrolledRebuilds;
    private List<TrickType> targets = List.of();
    private @Nullable TrickType far;
    private @Nullable Snapshot base;
    private @Nullable Snapshot before;
    private @Nullable ScreenRectangle point;

    private TrickListLayoutTour() {
    }

    public static void register() {
        SelfTest.clientStep(NAME, 1800, new TrickListLayoutTour()::step);
    }

    static @Nullable TrickType farthest(TrickConfigTab tab) {
        List<TrickCatalog.Section> sections = tab.sections();
        return sections.isEmpty() || sections.getLast().tricks().isEmpty() ? null : sections.getLast().tricks().getLast();
    }

    static boolean click(TrickConfigTab tab, ScreenRectangle row) {
        return tab.mouseClicked(new MouseButtonEvent(row.left() + 8, row.top() + row.height() / 2.0, LEFT), false);
    }

    static @Nullable String misplaced(Snapshot before, Snapshot after, String when, boolean strict) {
        Integer shift = null;
        for (Map.Entry<TrickType, ScreenRectangle> entry : after.rows().entrySet()) {
            ScreenRectangle old = before.rows().get(entry.getKey());
            if (old == null) {
                continue;
            }
            int dy = entry.getValue().top() - old.top();
            if (shift == null) {
                shift = dy;
            } else if (shift != dy) {
                return when + ": Zeilen verschieben sich ungleich (" + entry.getKey() + " um " + dy + ", andere um " + shift + ")";
            }
        }
        if (after.heads().size() != before.heads().size() || after.heads().isEmpty()) {
            return when + ": Kopf-Widgets fehlen (" + before.heads().size() + " vorher, " + after.heads().size() + " nachher)";
        }
        if (shift == null) {
            return strict ? when + ": keine Trick-Zeile vorher und nachher sichtbar" : overlap(after, when);
        }
        for (int i = 0; i < after.heads().size(); i++) {
            Head now = after.heads().get(i);
            int expected = before.heads().get(i).rect().top() + shift;
            if (now.rect().top() != expected) {
                return when + ": " + now.name() + " liegt bei y=" + now.rect().top() + " statt y=" + expected + " (Liste um " + shift
                        + " verschoben)";
            }
        }
        return overlap(after, when);
    }

    static @Nullable String overlap(Snapshot snapshot, String when) {
        for (Head head : snapshot.heads()) {
            if (!head.visible()) {
                continue;
            }
            if (head.rect().top() >= snapshot.clipBottom()) {
                return when + ": " + head.name() + " " + head.rect() + " sichtbar unter dem Listenende y=" + snapshot.clipBottom();
            }
            for (Map.Entry<TrickType, ScreenRectangle> entry : snapshot.rows().entrySet()) {
                if (entry.getValue().intersects(head.rect())) {
                    return when + ": " + head.name() + " " + head.rect() + " liegt über der Zeile " + entry.getKey() + " " + entry.getValue();
                }
            }
        }
        return null;
    }

    private static @Nullable TrickConfigTab activeTab() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gui.screen() instanceof ConfiguratorScreen screen && screen.activeTab() instanceof TrickConfigTab tab ? tab : null;
    }

    private boolean settled(SelfTest.StepContext ctx) {
        return ctx.tick() - this.since >= SETTLE;
    }

    private StepResult next(SelfTest.StepContext ctx, int phase) {
        this.phase = phase;
        this.since = ctx.tick();
        return StepResult.pending();
    }

    private StepResult step(SelfTest.StepContext ctx) {
        StepResult result = this.run(ctx);
        if (result.status() != StepResult.Status.PENDING) {
            Screenshots.parkMouse(Minecraft.getInstance());
            TrickTutorialSelfTests.closeAll();
        }
        return result;
    }

    private StepResult run(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (this.phase == 0) {
            TrickTutorialSelfTests.closeAll();
            Screenshots.parkMouse(mc);
            return this.next(ctx, 1);
        }
        if (this.phase == 1) {
            TrickConfigTab tab = TrickTutorialSelfTests.openTab(ctx);
            if (tab == null) {
                return ctx.tick() > 300 ? StepResult.fail("Tab Freestyle öffnet nicht") : StepResult.pending();
            }
            tab.pointAt(null);
            tab.search("");
            tab.filterFamily(null);
            return this.next(ctx, 2);
        }
        if (this.phase == 4) {
            return this.player(ctx);
        }
        TrickConfigTab tab = activeTab();
        if (tab == null) {
            return StepResult.fail("Tab Freestyle nicht offen in Phase " + this.phase + ": " + mc.gui.screen());
        }
        if (!this.settled(ctx)) {
            return StepResult.pending();
        }
        switch (this.phase) {
            case 2 -> {
                Snapshot top = Snapshot.of(tab);
                TrickType last = farthest(tab);
                if (top.rows().isEmpty() || last == null || tab.contentAbove()) {
                    return StepResult.fail("frischer Tab: " + top.rows().size() + " Zeilen, letzter Trick " + last + ", oben " + !tab.contentAbove());
                }
                String wrong = overlap(top, "frischer Tab");
                if (wrong != null) {
                    return StepResult.fail(wrong);
                }
                this.base = top;
                this.far = last;
                List<TrickType> ends = new ArrayList<>();
                for (TrickCatalog.Section section : tab.sections()) {
                    ends.add(section.tricks().getLast());
                }
                this.targets = List.copyOf(ends);
                tab.reveal(last);
                return this.next(ctx, 3);
            }
            case 3 -> {
                TrickType last = this.far;
                ScreenRectangle row = last == null ? null : tab.rowOf(last);
                if (row == null || !tab.contentAbove()) {
                    return StepResult.fail("Liste nicht weit gescrollt: Zeile " + last + " = " + row + ", oben verdeckt " + tab.contentAbove());
                }
                Snapshot scrolled = Snapshot.of(tab);
                String wrong = overlap(scrolled, "gescrollt vor dem Klick");
                if (wrong != null) {
                    return StepResult.fail(wrong);
                }
                this.before = scrolled;
                this.point = row;
                if (!click(tab, row)) {
                    return StepResult.fail("Klick auf " + last + " nimmt der Tab nicht an");
                }
                return this.next(ctx, 4);
            }
            case 5 -> {
                Snapshot back = Snapshot.of(tab);
                String how = this.round == 0 ? "Zurück" : "Escape";
                String wrong = this.before == null ? "kein Stand vor dem Klick" : misplaced(this.before, back, "nach " + how + " aus dem Player", true);
                if (wrong != null) {
                    return StepResult.fail(wrong);
                }
                this.checked++;
                this.before = back;
                this.notches = 0;
                return this.scrollUp(ctx, tab);
            }
            case 6 -> {
                Snapshot now = Snapshot.of(tab);
                String how = this.round == 0 ? "Zurück" : "Escape";
                String wrong = this.before == null ? "kein Stand vor dem Scrollen" : misplaced(this.before, now,
                        "nach " + how + " " + this.notches + " Rasten nach oben gescrollt", this.notches == 1);
                if (wrong != null) {
                    return StepResult.fail(wrong);
                }
                this.checked++;
                this.before = now;
                if (tab.contentAbove()) {
                    return this.scrollUp(ctx, tab);
                }
                Snapshot top = this.base;
                wrong = top == null ? "kein Stand des frischen Tabs" : misplaced(top, now, "nach " + how + " ganz nach oben gescrollt", true);
                if (wrong != null) {
                    return StepResult.fail(wrong);
                }
                ctx.log("tricks: nach " + how + " aus dem Player " + this.notches + " Rasten bis oben, Kopfzeilen liegen bei jeder Rast mit der Liste");
                this.round++;
                if (this.round < 2 && this.far != null) {
                    tab.reveal(this.far);
                    return this.next(ctx, 3);
                }
                this.target = 0;
                return this.rebuild(ctx, tab);
            }
            case 7 -> {
                this.before = Snapshot.of(tab);
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                    return StepResult.fail("Konfigurator geschlossen vor dem Neuaufbau");
                }
                if (tab.contentAbove()) {
                    this.scrolledRebuilds++;
                }
                screen.rebuild();
                return this.next(ctx, 8);
            }
            case 8 -> {
                TrickType trick = this.targets.get(this.target);
                String wrong = this.before == null ? "kein Stand vor dem Neuaufbau" : misplaced(this.before, Snapshot.of(tab),
                        "Neuaufbau mit " + trick + " im Bild", true);
                if (wrong != null) {
                    return StepResult.fail(wrong);
                }
                this.checked++;
                this.target++;
                if (this.target < this.targets.size()) {
                    return this.rebuild(ctx, tab);
                }
                if (this.scrolledRebuilds < 2) {
                    return StepResult.fail("nur " + this.scrolledRebuilds + " Neuaufbauten mit gescrollter Liste");
                }
                ctx.log("tricks: " + this.checked + " Prüfungen, " + this.targets.size() + " Neuaufbauten (" + this.scrolledRebuilds
                        + " gescrollt): Trick-Erkennung, Suche und Familie liegen nach Klick, Zurück, Escape, Scrollen und Neuaufbau mit der Liste");
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + this.phase);
            }
        }
    }

    private StepResult scrollUp(SelfTest.StepContext ctx, TrickConfigTab tab) {
        ScreenRectangle at = this.point;
        if (at == null || this.notches >= MAX_NOTCHES) {
            return StepResult.fail("Scrollen nach oben endet nicht (" + this.notches + " Rasten)");
        }
        if (!tab.mouseScrolled(at.left() + 8, at.top() + at.height() / 2.0, 0.0, 1.0)) {
            return StepResult.fail("Mausrad nach oben nimmt der Tab nicht an (Rast " + (this.notches + 1) + ")");
        }
        this.notches++;
        return this.next(ctx, 6);
    }

    private StepResult rebuild(SelfTest.StepContext ctx, TrickConfigTab tab) {
        tab.reveal(this.targets.get(this.target));
        return this.next(ctx, 7);
    }

    private StepResult player(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof SceneScreen player)) {
            return ctx.tick() - this.since > 200 ? StepResult.fail("Klick öffnet keinen Player: " + mc.gui.screen()) : StepResult.pending();
        }
        if (player.frames() < 3) {
            return StepResult.pending();
        }
        if (this.round == 0) {
            player.onClose();
        } else if (!player.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0))) {
            return StepResult.fail("Escape schließt den Player nicht");
        }
        if (activeTab() == null) {
            return StepResult.fail((this.round == 0 ? "Zurück" : "Escape") + " führt nicht in den Tab Freestyle: " + mc.gui.screen());
        }
        return this.next(ctx, 5);
    }
}
