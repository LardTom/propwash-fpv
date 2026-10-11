package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.ReceiverTab;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.LayoutAudit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HoverSweepSelfTests {
    static final String GUID = "propwash-hover-sweep-radio";
    static final String LONG_NAME = "EdgeTX Radiomaster Boxer – Propwash-Testfunke mit einem besonders langen Gerätenamen Joystick";
    private static final int AXES = 8;
    private static final int BUTTONS = 24;
    private static final int GRID = 6;
    private static final int MAX_PAGES = 6;
    private static final int WIDE_WIDTH = 960;
    private static final int WIDE_HEIGHT = 540;
    private static final long BUDGET_NANOS = 45_000_000L;
    private static final int SWEEP_TICKS = 12000;
    private static final String SWEEP = "sweep";
    private static final String STATUS = "status";

    private HoverSweepSelfTests() {
    }

    static void register() {
        SelfTest.clientStep("ui: Konfigurator-Hover – alle Tabs ohne Testfunke", SWEEP_TICKS, ctx -> sweepStep(ctx, Variant.PLAIN));
        SelfTest.clientStep("ui: Konfigurator-Hover – alle Tabs mit Testfunke (langer Name)", SWEEP_TICKS, ctx -> sweepStep(ctx, Variant.RADIO));
        SelfTest.clientStep("ui: Konfigurator-Hover – alle Tabs mit Testfunke bei 427x240", SWEEP_TICKS, ctx -> sweepStep(ctx, Variant.RADIO_SMALL));
        SelfTest.clientStep("ui: Konfigurator-Hover – alle Tabs mit Testfunke bei 960x540", SWEEP_TICKS, ctx -> sweepStep(ctx, Variant.RADIO_WIDE));
        SelfTest.clientStep("ui: Konfigurator-Hover – alle Tabs mit Maus + Tastatur bei 427x240", SWEEP_TICKS, ctx -> sweepStep(ctx, Variant.MOUSE_SMALL));
        SelfTest.clientStep("ui: Konfigurator-Hover – Empfänger-Status zeigt den ganzen Namen", 600, HoverSweepSelfTests::statusStep);
    }

    public static Screenshots.Scene statusScene() {
        return new StatusScene();
    }

    public static SimulatedRadio radio() {
        return new SimulatedRadio(GUID, LONG_NAME, ProfileDefaults.radio(LONG_NAME, AXES), AXES, BUTTONS);
    }

    private enum Variant {
        PLAIN(false, 0, 0),
        RADIO(true, 0, 0),
        RADIO_SMALL(true, ConfiguratorScreen.MIN_WIDTH, ConfiguratorScreen.MIN_HEIGHT),
        RADIO_WIDE(true, WIDE_WIDTH, WIDE_HEIGHT),
        MOUSE_SMALL(false, ConfiguratorScreen.MIN_WIDTH, ConfiguratorScreen.MIN_HEIGHT, true);

        private final boolean radio;
        private final int width;
        private final int height;
        private final boolean mouse;

        Variant(boolean radio, int width, int height) {
            this(radio, width, height, false);
        }

        Variant(boolean radio, int width, int height, boolean mouse) {
            this.radio = radio;
            this.width = width;
            this.height = height;
            this.mouse = mouse;
        }
    }

    private static StepResult sweepStep(SelfTest.StepContext ctx, Variant variant) {
        Minecraft mc = Minecraft.getInstance();
        Sweep s = (Sweep) ctx.state().computeIfAbsent(SWEEP, key -> new Sweep(variant));
        try {
            StepResult result = s.advance(ctx, mc);
            if (result.status() != StepResult.Status.PENDING) {
                s.cleanup(mc);
            }
            return result;
        } catch (RuntimeException e) {
            s.cleanup(mc);
            throw e;
        }
    }

    private static final class Sweep {
        private final Variant variant;
        private int stage;
        private int tabIndex = -1;
        private int page;
        private int point;
        private int baseline = -1;
        private long points;
        private long nanos;
        private int pages;
        private final List<String> scrolled = new ArrayList<>();
        private boolean cleaned;
        private @Nullable String selectedBefore;

        private Sweep(Variant variant) {
            this.variant = variant;
        }

        private StepResult advance(SelfTest.StepContext ctx, Minecraft mc) {
            if (this.stage == 0) {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                TransmitterInput.INSTANCE.simulate(this.variant.radio ? radio() : null);
                if (this.variant.mouse) {
                    this.selectedBefore = InputConfig.get().selectedGuid;
                    InputConfig.get().selectedGuid = InputConfig.MOUSE_GUID;
                }
                ConfigTabs.open(null, null);
                this.stage = 1;
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            if (this.stage == 1) {
                if (this.variant.width > 0) {
                    screen.resize(this.variant.width, this.variant.height);
                }
                this.stage = 2;
                return StepResult.pending();
            }
            StepResult crashed = crashed(screen, "vor dem Abtasten");
            if (crashed != null) {
                return crashed;
            }
            List<ConfigTab> tabs = screen.tabs();
            if (this.tabIndex < 0) {
                this.tabIndex = 0;
                this.page = 0;
                this.point = 0;
                this.baseline = -1;
            }
            if (this.tabIndex >= tabs.size()) {
                ctx.log(String.format("%d Tabs, %d Seiten, %d Mauspositionen (Raster %d px) bei %dx%d in %.1f s; gescrollt: %s", tabs.size(), this.pages,
                        this.points, GRID, screen.width, screen.height, this.nanos / 1.0E9, this.scrolled.isEmpty() ? "–" : String.join(", ", this.scrolled)));
                return StepResult.pass();
            }
            ConfigTab tab = tabs.get(this.tabIndex);
            String id = tab.id();
            if (screen.activeTab() != tab) {
                screen.selectTab(this.tabIndex);
                this.baseline = -1;
                return StepResult.pending();
            }
            if (this.baseline < 0) {
                this.baseline = screen.extractedFrames(id);
                return StepResult.pending();
            }
            if (this.point == 0 && screen.extractedFrames(id) <= this.baseline) {
                return StepResult.pending();
            }
            ScreenRectangle area = screen.windowArea();
            int columns = Math.max(1, (area.width() + GRID - 1) / GRID);
            int rows = Math.max(1, (area.height() + GRID - 1) / GRID);
            int total = columns * rows;
            GuiRenderState state = new GuiRenderState();
            long start = Util.getNanos();
            while (this.point < total && Util.getNanos() - start < BUDGET_NANOS) {
                int x = area.left() + (this.point % columns) * GRID + GRID / 2;
                int y = area.top() + (this.point / columns) * GRID + GRID / 2;
                this.point++;
                this.points++;
                String where = "Tab " + id + ", Seite " + (this.page + 1) + ", Maus " + x + "," + y + " bei " + screen.width + "x" + screen.height;
                try {
                    extractAt(mc, screen, state, x, y);
                } catch (RuntimeException e) {
                    return StepResult.fail("Ausnahme beim Zeichnen (" + where + "): " + e);
                }
                crashed = crashed(screen, where);
                if (crashed != null) {
                    return crashed;
                }
            }
            this.nanos += Util.getNanos() - start;
            if (this.point < total) {
                return StepResult.pending();
            }
            this.pages++;
            boolean more = tab.contentBelow();
            if (more && this.page + 1 < MAX_PAGES) {
                ScreenRectangle content = screen.contentArea();
                screen.mouseScrolled(content.left() + content.width() / 2.0, content.top() + content.height() / 2.0, 0.0, -content.height() / 10.0);
                this.page++;
                this.point = 0;
                this.baseline = screen.extractedFrames(id);
                if (!this.scrolled.contains(id)) {
                    this.scrolled.add(id);
                }
                return StepResult.pending();
            }
            this.tabIndex++;
            this.page = 0;
            this.point = 0;
            this.baseline = -1;
            return StepResult.pending();
        }

        private void cleanup(Minecraft mc) {
            if (this.cleaned) {
                return;
            }
            this.cleaned = true;
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            TransmitterInput.INSTANCE.simulate(null);
            if (this.selectedBefore != null) {
                InputConfig.get().selectedGuid = this.selectedBefore;
            }
        }
    }

    static void extractAt(Minecraft mc, ConfiguratorScreen screen, GuiRenderState state, int x, int y) {
        state.reset();
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(mc, state, x, y);
        screen.extractRenderState(g, x, y, 0.0F);
        g.extractDeferredElements(x, y, 0.0F);
    }

    static @Nullable StepResult crashed(ConfiguratorScreen screen, String where) {
        Map<String, String> errors = screen.tabErrors();
        if (errors.isEmpty()) {
            return null;
        }
        StringBuilder message = new StringBuilder("Tab abgestürzt (" + where + "): " + errors);
        for (String id : errors.keySet()) {
            String report = screen.tabReport(id);
            if (report != null) {
                List<String> lines = report.lines().limit(4).toList();
                message.append(" | ").append(String.join(" / ", lines));
            }
        }
        return StepResult.fail(message.toString());
    }

    private static StepResult statusStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Status s = (Status) ctx.state().computeIfAbsent(STATUS, key -> new Status());
        try {
            StepResult result = s.advance(ctx, mc);
            if (result.status() != StepResult.Status.PENDING) {
                s.cleanup(mc);
            }
            return result;
        } catch (RuntimeException e) {
            s.cleanup(mc);
            throw e;
        }
    }

    private static final class Status {
        private int stage;
        private int baseline;
        private int movedAt;
        private boolean cleaned;

        private StepResult advance(SelfTest.StepContext ctx, Minecraft mc) {
            if (this.stage == 0) {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                TransmitterInput.INSTANCE.simulate(radio());
                ConfigTabs.open(null, ReceiverTab.ID);
                this.stage = 1;
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            ConfigTab active = screen.activeTab();
            if (active == null || !ReceiverTab.ID.equals(active.id())) {
                screen.selectTab(ReceiverTab.ID);
                return ctx.tick() > 200 ? StepResult.fail("Empfänger-Tab lässt sich nicht wählen") : StepResult.pending();
            }
            StepResult crashed = crashed(screen, "Empfänger-Tab");
            if (crashed != null) {
                return crashed;
            }
            RawInput raw = TransmitterInput.INSTANCE.raw();
            if (raw == null) {
                return StepResult.fail("Testfunke nicht aktiv");
            }
            String fullName = raw.device().displayName();
            int[] status = statusPoint(screen);
            if (this.stage == 1) {
                this.baseline = screen.extractedFrames(ReceiverTab.ID);
                this.stage = 2;
                return StepResult.pending();
            }
            if (this.stage == 2) {
                if (screen.extractedFrames(ReceiverTab.ID) <= this.baseline) {
                    return StepResult.pending();
                }
                TooltipProbe probe = new TooltipProbe(mc, new GuiRenderState(), status[0], status[1]);
                screen.extractRenderState(probe, status[0], status[1], 0.0F);
                probe.extractDeferredElements(status[0], status[1], 0.0F);
                crashed = crashed(screen, "Maus auf der Statuszeile " + status[0] + "," + status[1]);
                if (crashed != null) {
                    return crashed;
                }
                if (!probe.lines.isEmpty()) {
                    return StepResult.fail("Statuszeile zeigt einen Tooltip, obwohl sie umbricht: „" + String.join(" ", probe.lines) + "“");
                }
                StringBuilder shown = new StringBuilder();
                LayoutProbe.Frame frame = LayoutProbe.capture(mc, screen);
                for (LayoutAudit.Text text : frame.texts()) {
                    shown.append(text.value()).append(' ');
                }
                if (!compact(shown.toString()).contains(compact(fullName))) {
                    return StepResult.fail("Statuszeile zeigt nicht den ganzen Namen „" + fullName + "“");
                }
                for (String cut : frame.cuts().keySet()) {
                    if (compact(cut).contains(compact(fullName)) && cut.length() > fullName.length()) {
                        return StepResult.fail("Statuszeile ist gekürzt: „" + frame.cuts().get(cut) + "“");
                    }
                }
                ctx.log("Statuszeile zeigt den ganzen Namen über mehrere Zeilen");
                pointMouse(mc, status[0], status[1]);
                this.movedAt = ctx.tick();
                this.baseline = screen.extractedFrames(ReceiverTab.ID);
                this.stage = 3;
                return StepResult.pending();
            }
            pointMouse(mc, status[0], status[1]);
            if (screen.extractedFrames(ReceiverTab.ID) < this.baseline + 5 || ctx.tick() - this.movedAt < 5) {
                return StepResult.pending();
            }
            crashed = crashed(screen, "echte Maus auf der Statuszeile");
            return crashed != null ? crashed : StepResult.pass();
        }

        private void cleanup(Minecraft mc) {
            if (this.cleaned) {
                return;
            }
            this.cleaned = true;
            Screenshots.parkMouse(mc);
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            TransmitterInput.INSTANCE.simulate(null);
        }
    }

    static int[] statusPoint(ConfiguratorScreen screen) {
        ScreenRectangle content = screen.contentArea();
        return new int[]{content.left() + Math.min(40, content.width() / 4), content.top() + 4};
    }

    static void pointMouse(Minecraft mc, int guiX, int guiY) {
        double x = (guiX + 0.5) * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        double y = (guiY + 0.5) * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
        mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
    }

    static String compact(String text) {
        return text.replaceAll("\\s+", "");
    }

    static String plain(FormattedCharSequence text) {
        StringBuilder out = new StringBuilder();
        text.accept((index, style, codepoint) -> {
            out.appendCodePoint(codepoint);
            return true;
        });
        return out.toString();
    }

    static final class TooltipProbe extends GuiGraphicsExtractor {
        final List<String> lines = new ArrayList<>();

        TooltipProbe(Minecraft minecraft, GuiRenderState state, int mouseX, int mouseY) {
            super(minecraft, state, mouseX, mouseY);
        }

        @Override
        public void setTooltipForNextFrame(Font font, List<? extends FormattedCharSequence> lines, int xo, int yo, @Nullable Identifier style) {
            for (FormattedCharSequence line : lines) {
                this.lines.add(plain(line));
            }
            super.setTooltipForNextFrame(font, lines, xo, yo, style);
        }

        @Override
        public void setComponentTooltipForNextFrame(Font font, List<Component> lines, int xo, int yo, @Nullable Identifier style) {
            for (Component line : lines) {
                this.lines.add(line.getString());
            }
            super.setComponentTooltipForNextFrame(font, lines, xo, yo, style);
        }

        @Override
        public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> tooltip, ClientTooltipPositioner positioner, int xo, int yo,
                                           boolean replaceExisting) {
            for (FormattedCharSequence line : tooltip) {
                this.lines.add(plain(line));
            }
            super.setTooltipForNextFrame(font, tooltip, positioner, xo, yo, replaceExisting);
        }
    }

    private static final class StatusScene implements Screenshots.Scene {
        private int ready = -1;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.ready < 0) {
                if (!TransmitterInput.INSTANCE.simulating()) {
                    TransmitterInput.INSTANCE.simulate(radio());
                }
                if (!(mc.gui.screen() instanceof ConfiguratorScreen)) {
                    ConfigTabs.open(null, ReceiverTab.ID);
                    return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
                }
                this.ready = ctx.tick();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                return StepResult.fail("Konfigurator wurde geschlossen");
            }
            ConfigTab active = screen.activeTab();
            if (active == null || !ReceiverTab.ID.equals(active.id())) {
                screen.selectTab(ReceiverTab.ID);
                return StepResult.pending();
            }
            int[] status = statusPoint(screen);
            pointMouse(mc, status[0], status[1]);
            if (ctx.tick() - this.ready < 10) {
                return StepResult.pending();
            }
            StepResult crashed = crashed(screen, "Bildschirmfoto Empfänger-Tab");
            return crashed != null ? crashed : StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                int[] status = statusPoint(screen);
                pointMouse(mc, status[0], status[1]);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Screenshots.parkMouse(mc);
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            TransmitterInput.INSTANCE.simulate(null);
        }
    }
}
