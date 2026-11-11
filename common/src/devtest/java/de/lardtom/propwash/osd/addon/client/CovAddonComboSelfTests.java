package de.lardtom.propwash.osd.addon.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.addon.AddonOsdKeys;
import de.lardtom.propwash.osd.client.OsdConfigTab;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

public final class CovAddonComboSelfTests {
    public static final String NAME = AddonOsdSelfTests.GROUP
            + "Abdeckung cov-combo: Gruppe Add-ons mit Mob Defense und Kamera-Add-on zusammen (alle Elemente, Add-on-Name im Tooltip, Pfeiltasten)";
    public static final String MOB_DEFENSE = "mobdefense";
    public static final String CAM = "camdrones";
    public static final List<String> CAM_ELEMENTS = List.of("mode", "gimbal", "mission");
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);

    private CovAddonComboSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(NAME, 200, CovAddonComboSelfTests::step);
    }

    private static int intField(OsdConfigTab tab, String name) throws ReflectiveOperationException {
        Field field = OsdConfigTab.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(tab);
    }

    private static int listRow() throws ReflectiveOperationException {
        Field field = OsdConfigTab.class.getDeclaredField("LIST_ROW");
        field.setAccessible(true);
        return field.getInt(null);
    }

    @SuppressWarnings("unchecked")
    private static List<Component> tooltip(OsdConfigTab tab, OsdElementSpec spec) throws ReflectiveOperationException {
        Method method = OsdConfigTab.class.getDeclaredMethod("addonTooltip", OsdElementSpec.class);
        method.setAccessible(true);
        return (List<Component>) method.invoke(tab, spec);
    }

    private static @Nullable OsdElementSpec listed(OsdConfigTab tab, double y) throws ReflectiveOperationException {
        Method method = OsdConfigTab.class.getDeclaredMethod("listAddonAt", double.class);
        method.setAccessible(true);
        return (OsdElementSpec) method.invoke(tab, y);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        if (!Services.PLATFORM.isModLoaded(MOB_DEFENSE) || !Services.PLATFORM.isModLoaded(CAM)) {
            ctx.log("Mob Defense " + (Services.PLATFORM.isModLoaded(MOB_DEFENSE) ? "geladen" : "fehlt") + ", Kamera-Add-on "
                    + (Services.PLATFORM.isModLoaded(CAM) ? "geladen" : "fehlt") + ": Kombi-Lauf nur mit beiden Jars in mods/, übersprungen");
            return StepResult.pass();
        }
        List<String> problems = new ArrayList<>();
        List<OsdElementSpec> registered = Api17Registries.Client.osdElements();
        List<OsdElementSpec> targets = new ArrayList<>();
        TreeSet<String> mobIds = new TreeSet<>();
        TreeSet<String> camIds = new TreeSet<>();
        for (OsdElementSpec spec : registered) {
            String ns = spec.id().getNamespace();
            if (ns.equals(MOB_DEFENSE)) {
                targets.add(spec);
                mobIds.add(spec.id().getPath());
            } else if (ns.equals(CAM)) {
                targets.add(spec);
                camIds.add(spec.id().getPath());
            }
        }
        if (mobIds.isEmpty()) {
            problems.add("keine OSD-Elemente von Mob Defense angemeldet");
        }
        for (String path : CAM_ELEMENTS) {
            if (!camIds.contains(path)) {
                problems.add("camdrones:" + path + " ist nicht angemeldet (angemeldet: " + camIds + ")");
            }
        }
        if (!Language.getInstance().has(OsdConfigTab.ADDON_GROUP_KEY)) {
            problems.add("Text der Gruppe fehlt: " + OsdConfigTab.ADDON_GROUP_KEY);
        }
        Optional<String> mobName = Services.PLATFORM.getModName(MOB_DEFENSE);
        Optional<String> camName = Services.PLATFORM.getModName(CAM);
        if (mobName.isEmpty() || camName.isEmpty() || mobName.equals(camName)) {
            problems.add("Mod-Namen nicht unterscheidbar: " + mobName + " / " + camName);
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        OsdHud.sampler().setOverride(null);
        FakeHost host = new FakeHost(8, 28, 510, 302);
        OsdConfigTab tab = host.bind(new OsdConfigTab());
        tab.resetDefaults();
        List<OsdElementSpec> shown = tab.addons().specs();
        List<String> moved = new ArrayList<>();
        try {
            int row = listRow();
            int listX = intField(tab, "listX");
            int listY = intField(tab, "listY");
            int listW = intField(tab, "listW");
            int listH = intField(tab, "listH");
            for (OsdElementSpec spec : targets) {
                int index = -1;
                for (int i = 0; i < shown.size(); i++) {
                    if (shown.get(i).id().equals(spec.id())) {
                        index = i;
                    }
                }
                if (index < 0) {
                    problems.add(spec.id() + " fehlt in der Gruppe Add-ons");
                    continue;
                }
                String name = Component.translatable(spec.nameKey()).getString();
                if (!Language.getInstance().has(spec.nameKey()) || name.equals(spec.nameKey())) {
                    problems.add(spec.id() + ": Name ohne Text (" + spec.nameKey() + ")");
                }
                List<Component> lines = tooltip(tab, spec);
                String mod = spec.id().getNamespace().equals(MOB_DEFENSE) ? mobName.orElseThrow() : camName.orElseThrow();
                String source = lines.size() > 1 ? lines.get(1).getString() : "";
                if (lines.isEmpty() || !lines.getFirst().getString().equals(name) || !source.contains(mod)) {
                    problems.add(spec.id() + ": Tooltip " + lines.stream().map(Component::getString).toList() + " ohne „" + mod + "“");
                }
                int top = listY + 2 + (OsdElement.values().length + 1 + index) * row;
                for (int guard = 0; guard < 200; guard++) {
                    int scroll = intField(tab, "listScroll");
                    int rowTop = top - scroll;
                    if (rowTop >= listY && rowTop + row <= listY + listH) {
                        break;
                    }
                    tab.mouseScrolled(listX + 10, listY + 10, 0.0, rowTop < listY ? 1.0 : -1.0);
                }
                double y = top - intField(tab, "listScroll") + row / 2.0;
                OsdElementSpec at = listed(tab, y);
                if (at == null || !at.id().equals(spec.id())) {
                    problems.add(spec.id() + ": Zeile bei y=" + y + " zeigt " + (at == null ? "nichts" : at.id()));
                    continue;
                }
                double x = listX + listW / 2.0;
                tab.mouseClicked(new MouseButtonEvent(x, y, LEFT), false);
                if (!spec.id().equals(tab.selectedAddon())) {
                    problems.add(spec.id() + ": Klick auf die Zeile wählt " + tab.selectedAddon());
                    continue;
                }
                OsdConfig.Slot slot = AddonOsdKeys.slot(tab.working(), spec);
                int col = slot.col;
                int rowBefore = slot.row;
                tab.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, 0, 0));
                int dc = 1;
                if (AddonOsdKeys.slot(tab.working(), spec).col == col) {
                    tab.keyPressed(new KeyEvent(InputConstants.KEY_LEFT, 0, 0));
                    dc = -1;
                }
                tab.keyPressed(new KeyEvent(InputConstants.KEY_DOWN, 0, 0));
                int dr = 1;
                if (AddonOsdKeys.slot(tab.working(), spec).row == rowBefore) {
                    tab.keyPressed(new KeyEvent(InputConstants.KEY_UP, 0, 0));
                    dr = -1;
                }
                OsdConfig.Slot after = AddonOsdKeys.slot(tab.working(), spec);
                if (after.col != col + dc || after.row != rowBefore + dr) {
                    problems.add(spec.id() + ": Pfeiltasten verschieben von " + col + "/" + rowBefore + " nach " + after.col + "/" + after.row);
                    continue;
                }
                OsdConfig.Slot kept = tab.working().copy().elements.get(spec.id().toString());
                if (kept == null || kept.col != after.col || kept.row != after.row) {
                    problems.add(spec.id() + ": Kopie der Einstellungen verliert die neue Stelle");
                }
                moved.add(spec.id() + " " + col + "/" + rowBefore + "→" + after.col + "/" + after.row);
            }
        } catch (ReflectiveOperationException e) {
            return StepResult.fail("Editor nicht lesbar: " + e);
        }
        tab.resetLayout();
        ctx.log("Gruppe Add-ons mit " + shown.size() + " Elementen; " + MOB_DEFENSE + " " + mobIds + " („" + mobName.orElse("?") + "“), " + CAM + " "
                + camIds + " („" + camName.orElse("?") + "“); verschoben: " + moved);
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
    }

    private static final class FakeHost implements ConfigTabHost {
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private @Nullable OsdConfigTab tab;
        private @Nullable Screen screen;

        FakeHost(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        OsdConfigTab bind(OsdConfigTab tab) {
            this.tab = tab;
            this.rebuild();
            return tab;
        }

        @Override
        public <T extends AbstractWidget> T addWidget(T widget) {
            this.widgets.add(widget);
            return widget;
        }

        @Override
        public Font font() {
            return Minecraft.getInstance().font;
        }

        @Override
        public Minecraft minecraft() {
            return Minecraft.getInstance();
        }

        @Override
        public Screen screen() {
            if (this.screen == null) {
                this.screen = new Screen(Component.empty()) {
                };
            }
            return this.screen;
        }

        @Override
        public void rebuild() {
            this.widgets.clear();
            if (this.tab != null) {
                this.tab.attach(this, this.x, this.y, this.width, this.height);
            }
        }

        @Override
        public void changed() {
        }
    }
}
