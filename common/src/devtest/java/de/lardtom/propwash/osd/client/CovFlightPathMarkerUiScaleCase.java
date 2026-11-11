package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.UiScaleSelfTests;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.LayoutAudit.Finding;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CovFlightPathMarkerUiScaleCase implements UiScaleSelfTests.Case {
    public static final String NAME = "Konfigurator / osd mit Tooltip der Flugbahn-Marker-Beschreibung";
    private static final int OPEN_TICKS = 200;
    private static final int MAX_SCROLLS = 40;

    public static UiScaleSelfTests.Case create() {
        return new CovFlightPathMarkerUiScaleCase();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public StepResult open(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            if (!ctx.state().containsKey("cov_fpm_ui_opened")) {
                ctx.state().put("cov_fpm_ui_opened", ctx.tick());
                ConfigTabs.open(null, OsdConfigTab.ID);
            }
            return ctx.tick() > OPEN_TICKS ? StepResult.fail("K-Menü öffnet nicht") : StepResult.pending();
        }
        if (!(screen.activeTab() instanceof OsdConfigTab)) {
            screen.selectTab(OsdConfigTab.ID);
            return ctx.tick() > OPEN_TICKS ? StepResult.fail("Tab OSD öffnet nicht") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static LayoutAudit.@Nullable Text row(Minecraft mc, Screen screen, String name) {
        for (LayoutAudit.Text text : LayoutProbe.capture(mc, screen).texts()) {
            LayoutAudit.Box visible = text.visible();
            if (text.value().equals(name) && visible != null && visible.bottom() - visible.top() >= text.full().bottom() - text.full().top()) {
                return text;
            }
        }
        return null;
    }

    private static LayoutAudit.@Nullable Text anyListed(Minecraft mc, Screen screen) {
        List<String> names = new ArrayList<>();
        for (OsdElement element : OsdElement.values()) {
            names.add(Component.translatable(element.translationKey()).getString());
        }
        for (LayoutAudit.Text text : LayoutProbe.capture(mc, screen).texts()) {
            if (text.visible() != null && names.contains(text.value())) {
                return text;
            }
        }
        return null;
    }

    @Override
    public List<Finding> audit(Minecraft mc, Screen screen) {
        List<Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
        String subject = "Tab OSD, Tooltip der Flugbahn-Marker-Beschreibung";
        String name = Component.translatable(OsdElement.FLIGHT_PATH_MARKER.translationKey()).getString();
        String description = Component.translatable(OsdElement.FLIGHT_PATH_MARKER.translationKey() + ".description").getString();
        LayoutAudit.Text row = row(mc, screen, name);
        for (int i = 0; row == null && i < MAX_SCROLLS; i++) {
            LayoutAudit.Text anchor = anyListed(mc, screen);
            if (anchor == null) {
                break;
            }
            screen.mouseScrolled(anchor.full().left() + 3, (anchor.full().top() + anchor.full().bottom()) * 0.5, 0.0, -1.0);
            row = row(mc, screen, name);
        }
        if (row == null) {
            findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Listeneintrag „" + name + "“ nicht sichtbar"));
            return findings;
        }
        int x = row.full().left() + 3;
        int y = (row.full().top() + row.full().bottom()) / 2;
        LayoutProbe.Frame frame = LayoutProbe.capture(mc, screen, x, y);
        StringBuilder shown = new StringBuilder();
        for (LayoutAudit.Text line : frame.tooltip()) {
            if (!shown.isEmpty()) {
                shown.append(' ');
            }
            shown.append(line.value());
        }
        String flat = shown.toString().replaceAll("\\s+", " ").trim();
        String wanted = description.replaceAll("\\s+", " ").trim();
        if (frame.tooltip().isEmpty()) {
            findings.add(new Finding(LayoutAudit.Kind.TOOLTIP, subject, "kein Tooltip über dem Listeneintrag"));
        } else if (!flat.contains(wanted)) {
            findings.add(new Finding(LayoutAudit.Kind.CUT, subject, "Beschreibung nicht vollständig: „" + flat + "“ statt „" + wanted + "“"));
        }
        LayoutAudit.Box box = frame.tooltipBox();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        if (box != null && (box.left() < 0 || box.top() < 0 || box.right() > width || box.bottom() > height)) {
            findings.add(new Finding(LayoutAudit.Kind.OFFSCREEN, subject, String.format(Locale.ROOT, "Tooltip %d,%d–%d,%d außerhalb %d × %d", box.left(), box.top(),
                    box.right(), box.bottom(), width, height)));
        }
        for (LayoutAudit.Text line : frame.tooltip()) {
            LayoutAudit.Box visible = line.visible();
            if (visible == null || visible.right() < line.full().right() || visible.left() > line.full().left()) {
                findings.add(new Finding(LayoutAudit.Kind.CLIPPED, subject, "Tooltip-Zeile „" + line.value() + "“ abgeschnitten"));
            }
        }
        return findings;
    }

    @Override
    public void close(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
    }
}
