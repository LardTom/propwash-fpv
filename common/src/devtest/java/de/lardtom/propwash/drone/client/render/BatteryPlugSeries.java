package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class BatteryPlugSeries {
    static final String ENV = "PROPWASH_PLUG_SERIES";
    static final String DIR = "battery-plug-series/";
    private static final String JMP = "justmoreparts";
    private static final float REAR_YAW = 215.0F;
    private static final float SIDE_YAW = 270.0F;
    private static final float PITCH_ABOVE = 22.0F;
    private static final float PITCH_BELOW = -14.0F;
    private static final float MIN_VIEW_MM = 70.0F;
    private static final float MAX_VIEW_MM = 260.0F;
    private static final float HULL_SHARE = 0.8F;

    private BatteryPlugSeries() {
    }

    record Case(String frame, String battery, String stack, String label) {
    }

    static final List<Case> CASES = List.of(
            new Case("propwash:freestyle_5", "propwash:b6s_1300", "propwash:stack_30", "5 Zoll Freestyle, 6S 1300 XT60 oben"),
            new Case("propwash:freestyle_5", "propwash:b4s_650", "propwash:stack_30", "5 Zoll Freestyle, 4S 650 XT30 oben (kurz)"),
            new Case("propwash:freestyle_5", "propwash:b6s_4000_lion", "propwash:stack_30", "5 Zoll Freestyle, 6S 4000 Li-Ion oben (lang)"),
            new Case("propwash:cinewhoop_3", "propwash:b4s_650", "propwash:aio_20", "3,5 Zoll Cinewhoop, 4S 650 XT30 oben"),
            new Case("propwash:cinewhoop_3", "propwash:b6s_1100", "propwash:aio_20", "3,5 Zoll Cinewhoop, 6S 1100 XT60 oben (lang)"),
            new Case("propwash:long_range_7", "propwash:b6s_4000_lion", "propwash:stack_30", "7 Zoll Long Range, 6S 4000 Li-Ion oben (lang)"),
            new Case("propwash:long_range_7", "propwash:b6s_1300", "propwash:stack_30", "7 Zoll Long Range, 6S 1300 XT60 oben (kurz)"),
            new Case("propwash:race_5", "propwash:b6s_1100", "propwash:stack_30", "5 Zoll Race, 6S 1100 XT60 unten"),
            new Case("propwash:race_5", "propwash:b4s_650", "propwash:aio_20", "5 Zoll Race, 4S 650 XT30 unten (kurz)"),
            new Case("propwash:whoop_65", "propwash:b1s_300_hv", "propwash:aio_whoop", "Whoop 65, 1S 300 BT2.0 unten"),
            new Case(JMP + ":xclass_10", JMP + ":b8s_5000", "propwash:stack_30", "JMP X-Class 10 (skaliert), 8S 5000 AS150 oben (lang)"),
            new Case(JMP + ":xclass_10", JMP + ":b6s_2200", "propwash:stack_30", "JMP X-Class 10 (skaliert), 6S 2200 XT60 oben (kurz)"),
            new Case(JMP + ":xclass_13", JMP + ":b8s_4500_lion", "propwash:stack_30", "JMP X-Class 13 (skaliert), 8S 4500 Li-Ion XT90 oben"),
            new Case(JMP + ":fs_5_squashed", JMP + ":b6s_2200", "propwash:stack_30", "JMP Squashed 5, 6S 2200 XT60 oben"),
            new Case(JMP + ":fs_5_squashed", JMP + ":b4s_450", "propwash:aio_20", "JMP Squashed 5, 4S 450 XT30 oben (kurz)"),
            new Case(JMP + ":fs_5_bash", JMP + ":b8s_2200", "propwash:stack_30", "JMP Bash 5, 8S 2200 XT90 oben (wie im Bild des Befunds)"),
            new Case(JMP + ":race_5_stretched", JMP + ":b6s_1500", "propwash:stack_30", "JMP Race 5 Stretched, 6S 1500 XT60 unten"),
            new Case(JMP + ":whoop_75_duct", JMP + ":b1s_450_hv", "propwash:aio_whoop", "JMP Whoop 75, 1S 450 BT2.0 unten"));

    static boolean wanted() {
        return System.getenv(ENV) != null;
    }

    static void register(String prefix) {
        for (int i = 0; i < CASES.size(); i++) {
            Case c = CASES.get(i);
            String file = String.format(Locale.ROOT, "%s%02d_%s_%s", DIR, i + 1, Identifier.parse(c.frame()).getPath(),
                    Identifier.parse(c.battery()).getPath());
            Screenshots.optionalStep(prefix + "Bildreihe " + (i + 1) + ": " + c.label(), file, 12, () -> wanted() && build(c).isPresent(),
                    new SeriesScene(c));
        }
    }

    static Optional<DroneBuild> build(Case c) {
        Optional<FrameType> frame = part(c.frame(), FrameType.class);
        Optional<BatteryType> battery = part(c.battery(), BatteryType.class);
        Optional<StackType> stack = part(c.stack(), StackType.class);
        if (frame.isEmpty() || battery.isEmpty() || stack.isEmpty()) {
            return Optional.empty();
        }
        DroneBuild base = template(frame.get());
        return Optional.of(new DroneBuild(frame.get(), stack.get(), base.motor(), base.prop(), base.video(), battery.get()));
    }

    private static DroneBuild template(FrameType frame) {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            if (preset.frame() == frame) {
                return preset;
            }
        }
        for (DronePreset preset : PropwashApi.builds().presets()) {
            Optional<DroneBuild> build = Conversions.build(preset.data().build());
            if (build.isPresent() && build.get().frame() == frame) {
                return build.get();
            }
        }
        return frame.wheelbaseMm < 100 ? DroneBuild.WHOOP : DroneBuild.FREESTYLE;
    }

    private static <T> Optional<T> part(String id, Class<T> type) {
        return PartIndex.registeredById(Identifier.parse(id)).filter(type::isInstance).map(type::cast);
    }

    private record SeriesScene(Case c) implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            DroneBuild build = build(this.c).orElse(null);
            if (build == null) {
                return StepResult.fail(this.c.label() + ": Teile fehlen");
            }
            if (!(mc.gui.screen() instanceof SeriesScreen screen) || !screen.build.equals(build)) {
                mc.gui.setScreen(new SeriesScreen(build, this.c.label()));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Screenshots.parkMouse(mc);
            Screenshots.clearOverlays(mc);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof SeriesScreen) {
                mc.gui.setScreen(null);
            }
        }
    }

    static final class SeriesScreen extends Screen {
        private final DroneBuild build;
        private final String label;

        SeriesScreen(DroneBuild build, String label) {
            super(Component.empty());
            this.build = build;
            this.label = label;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            ThemeDraw.backdrop(g, this.width, this.height);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            int half = this.width / 2;
            ThemeDraw.previewStage(g, 0, 0, half - 1, this.height);
            ThemeDraw.previewStage(g, half + 1, 0, this.width, this.height);
            DroneRenderState state = DroneVisuals.previewState(this.build);
            DroneAssembly assembly = DroneVisuals.resources().assembly(this.build);
            boolean below = DroneVisuals.resources().layout(this.build.frame()).batteryBelow();
            if (state != null) {
                view(g, state, assembly, REAR_YAW, below ? PITCH_BELOW : PITCH_ABOVE, 0, half - 1);
                view(g, state, assembly, SIDE_YAW, below ? PITCH_BELOW * 0.5F : PITCH_ABOVE * 0.4F, half + 1, this.width);
            }
            String hidden = assembly.powerLead().present() ? "Stecker und Kabel ausgeblendet" : "Rückfall: Stecker sichtbar";
            ThemeText.text(g, this.font, this.label + " – " + hidden, 6, 6, Palette.TEXT);
            ThemeText.text(g, this.font, "schräg von hinten", 6, this.height - 14, Palette.TEXT);
            ThemeText.text(g, this.font, "von der Seite", half + 7, this.height - 14, Palette.TEXT);
            super.extractRenderState(g, mouseX, mouseY, a);
        }

        private void view(GuiGraphicsExtractor g, DroneRenderState state, DroneAssembly assembly, float yaw, float pitch, int x0, int x1) {
            Vector3f spot = BatteryPlugShowcase.plugSpot(assembly);
            Vector3f focus = new Vector3f(0.0F, (spot.y + assembly.stack().position().y()) * 0.5F, spot.z - 15.0F);
            float size = Math.max(assembly.hull().max().x() - assembly.hull().min().x(), assembly.hull().max().z() - assembly.hull().min().z());
            float view = Math.clamp(HULL_SHARE * size, MIN_VIEW_MM, MAX_VIEW_MM);
            float scale = DroneVisuals.scaleForPixelsPerMillimetre(state, (x1 - x0) / view);
            Quaternionf rotation = new Quaternionf().rotationXYZ((float) Math.toRadians(pitch), 0.0F, (float) Math.PI)
                    .rotateY((float) Math.toRadians(yaw));
            Vector3f translation = rotation.transform(focus.sub(state.pivot).mul(DroneVisualScale.blocksPerMillimetre(state.visualScale))).negate();
            g.entity(state, scale, translation, rotation, null, x0, 0, x1, this.height);
        }
    }
}
