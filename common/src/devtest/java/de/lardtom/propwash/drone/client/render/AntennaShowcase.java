package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.AntennaRules;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.client.render.DronePreview;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.api.part.VideoAntenna;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AntennaShowcase {
    static final float REAR_YAW = 215.0F;
    static final float CLOSE_QUARTER_YAW = 215.0F;
    static final float CLOSE_QUARTER_ELEVATION = 15.0F;
    static final float CLOSE_SIDE_YAW = 270.0F;
    static final float CLOSE_SIDE_ELEVATION = 0.0F;
    static final List<AntennaRules.Kind> CLOSE_KINDS = List.of(AntennaRules.Kind.RACE, AntennaRules.Kind.LITE, AntennaRules.Kind.HD,
            AntennaRules.Kind.LONG_RANGE);
    private static final int LIGHT = 0xFFD9DDE3;
    private static final int DARK = 0xFF15171B;
    private static final int LIGHT_TEXT = 0xFF1E2024;
    private static final int CELL_EDGE = 0x40808080;

    private AntennaShowcase() {
    }

    static void registerSheets(String prefix) {
        if (!Screenshots.enabled()) {
            return;
        }
        SelfTest.setupStep(prefix + "Fenster für die Bildschirmfotos vorbereiten", 400, ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            return Screenshots.resizeWindow(ctx);
        });
        boolean addons = AntennaSelfTests.addonVideosPresent();
        String suffix = addons ? "_addons" : "";
        for (DroneBuild preset : DroneBuild.PRESETS) {
            String frame = preset.frame().key();
            List<DroneBuild> cells = new ArrayList<>();
            for (VideoType video : AntennaSelfTests.videos()) {
                cells.add(AntennaSelfTests.with(preset, video));
            }
            for (boolean dark : new boolean[]{false, true}) {
                String shade = dark ? "dunkel" : "hell";
                Screenshots.step(prefix + "Bildschirmfoto " + frame + " mit allen VTX schräg von hinten, " + shade,
                        "antenna_rear_" + frame + suffix + (dark ? "_dark" : "_light"), 20,
                        new SheetScene(cells, dark, cells.size() <= 4 ? 2 : 3, preset.frame().id().toString()));
            }
        }
        List<VideoType> close = closeVideos();
        List<FrameType> closeFrames = new ArrayList<>();
        for (FrameType frame : FrameType.values()) {
            if (frame.builtin()) {
                closeFrames.add(frame);
            }
        }
        Map<String, FrameType> perClass = new LinkedHashMap<>();
        for (List<FrameType> role : AntennaSelfTests.addonFramesByRole().values()) {
            for (FrameType frame : role) {
                perClass.putIfAbsent(frame.id().getNamespace() + ":" + frame.definition().role().key(), frame);
            }
        }
        closeFrames.addAll(perClass.values());
        for (FrameType frame : closeFrames) {
            List<DroneBuild> cells = new ArrayList<>();
            for (VideoType video : close) {
                cells.add(AntennaSelfTests.with(frame, video));
            }
            String name = frame.id().getNamespace() + "_" + frame.id().getPath();
            for (boolean dark : new boolean[]{false, true}) {
                String shade = dark ? "dunkel" : "hell";
                Screenshots.step(prefix + "Bildschirmfoto nah " + frame.id() + " (Klasse " + frame.definition().role().key() + ") 3/4 von hinten und seitlich, "
                        + shade, "antenna_close_" + name + (dark ? "_dark" : "_light"), 20, new CloseScene(cells, dark,
                        frame.id() + " · " + frame.definition().role().key() + " · " + frame.wheelbaseMm + " mm · "
                                + (AntennaRules.explicitAnchor(frame.definition()) ? "eigener Halter" : "Rückfall-Halter")));
            }
        }
        List<VideoType> kinds = AntennaSelfTests.videoPerKind();
        for (Map.Entry<String, List<FrameType>> role : AntennaSelfTests.addonFramesByRole().entrySet()) {
            for (FrameType frame : role.getValue()) {
                List<DroneBuild> cells = new ArrayList<>();
                for (VideoType video : kinds) {
                    cells.add(AntennaSelfTests.with(frame, video));
                }
                String name = frame.id().getNamespace() + "_" + frame.id().getPath();
                Screenshots.step(prefix + "Bildschirmfoto Add-on-Rahmen " + frame.id() + " (Klasse " + role.getKey() + ") schräg von hinten",
                        "antenna_frame_" + name, 20, new SheetScene(cells, false, 3, frame.id() + " · " + role.getKey() + " · "
                        + (AntennaRules.explicitAnchor(frame.definition()) ? "eigener Halter" : "Rückfall-Halter")));
            }
        }
    }

    static void registerRestore(String prefix) {
        if (!Screenshots.enabled()) {
            return;
        }
        SelfTest.clientStep(prefix + "Fenster zurücksetzen", 200, Screenshots::restoreWindow);
    }

    static List<VideoType> closeVideos() {
        List<VideoType> out = new ArrayList<>();
        for (AntennaRules.Kind kind : CLOSE_KINDS) {
            VideoType chosen = null;
            for (VideoType video : VideoType.values()) {
                if (AntennaRules.kind(video.definition()) != kind) {
                    continue;
                }
                if (chosen == null || (!chosen.builtin() && video.builtin())
                        || (chosen.builtin() == video.builtin() && AntennaRules.antenna(video.definition()).count() > AntennaRules.antenna(chosen.definition()).count())) {
                    chosen = video;
                }
            }
            if (chosen != null) {
                out.add(chosen);
            }
        }
        return out;
    }

    static Quaternionf rotation(float yawDegrees, float elevationDegrees) {
        return new Quaternionf().rotationXYZ((float) Math.toRadians(elevationDegrees), 0.0F, (float) Math.PI).rotateY((float) Math.toRadians(yawDegrees));
    }

    static List<Vector3f> framingPoints(DroneBuild build) {
        FrameType frame = build.frame();
        DroneRenderResources res = DroneVisuals.resources();
        Vector3fc at = res.assembly(build).antenna().position();
        VideoAntenna spec = AntennaSelfTests.spec(frame, build.video());
        List<Vector3f> out = new ArrayList<>();
        out.add(new Vector3f(at));
        out.add(new Vector3f(0.0F, -6.0F, 0.0F));
        out.add(new Vector3f(0.0F, at.y(), (float) (-0.12 * frame.wheelbaseMm)));
        for (AntennaGeometry.Piece piece : AntennaGeometry.pieces(spec, DroneLayout.antennaTilt(frame), frame.wheelbaseMm,
                AntennaRules.pairLeanDeg(frame.definition()))) {
            out.add(piece.point(0.0F, 0.5F, 0.0F).add(at));
        }
        return out;
    }

    private static final class CloseScene implements Screenshots.Scene {
        private final List<DroneBuild> cells;
        private final boolean dark;
        private final String caption;

        private CloseScene(List<DroneBuild> cells, boolean dark, String caption) {
            this.cells = cells;
            this.dark = dark;
            this.caption = caption;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof CloseScreen)) {
                mc.gui.setScreen(new CloseScreen(this.cells, this.dark, this.caption));
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
            if (mc.gui.screen() instanceof CloseScreen) {
                mc.gui.setScreen(null);
            }
        }
    }

    private static final class CloseScreen extends Screen {
        private final List<DroneBuild> cells;
        private final boolean dark;
        private final String caption;

        private CloseScreen(List<DroneBuild> cells, boolean dark, String caption) {
            super(Component.empty());
            this.cells = cells;
            this.dark = dark;
            this.caption = caption;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            g.fill(0, 0, this.width, this.height, this.dark ? DARK : LIGHT);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            int columns = Math.max(1, this.cells.size());
            int cellWidth = this.width / columns;
            int cellHeight = (this.height - 12) / 2;
            int top = 24;
            int text = this.dark ? Palette.TEXT : LIGHT_TEXT;
            float[][] views = {{CLOSE_QUARTER_YAW, CLOSE_QUARTER_ELEVATION}, {CLOSE_SIDE_YAW, CLOSE_SIDE_ELEVATION}};
            String[] names = {"3/4 hinten", "seitlich"};
            for (int row = 0; row < 2; row++) {
                Quaternionf rotation = rotation(views[row][0], views[row][1]);
                Vector3f lo = new Vector3f(Float.POSITIVE_INFINITY);
                Vector3f hi = new Vector3f(Float.NEGATIVE_INFINITY);
                for (DroneBuild build : this.cells) {
                    for (Vector3f p : framingPoints(build)) {
                        rotation.transform(p);
                        lo.min(p);
                        hi.max(p);
                    }
                }
                Vector3f center = new Vector3f(lo).add(hi).mul(0.5F);
                float spanX = Math.max(1.0F, hi.x - lo.x);
                float spanY = Math.max(1.0F, hi.y - lo.y);
                float ppm = Math.min((cellWidth - 8) * 0.9F / spanX, (cellHeight - top - 4) * 0.9F / spanY);
                Vector3f world = new Quaternionf(rotation).conjugate().transform(new Vector3f(center));
                for (int i = 0; i < this.cells.size(); i++) {
                    DroneBuild build = this.cells.get(i);
                    int x0 = i * cellWidth;
                    int y0 = row * cellHeight;
                    g.fill(x0, y0, x0 + cellWidth, y0 + 1, CELL_EDGE);
                    g.fill(x0, y0, x0 + 1, y0 + cellHeight, CELL_EDGE);
                    DroneRenderState state = DroneVisuals.previewState(build);
                    if (state != null) {
                        float blocksPerMm = DroneVisualScale.blocksPerMillimetre(state.visualScale);
                        Vector3f entity = new Vector3f(world).sub(state.pivot).mul(blocksPerMm).add(0.0F, state.originY, 0.0F);
                        Vector3f translation = new Quaternionf(rotation).transform(entity).negate();
                        float scale = DroneVisuals.scaleForPixelsPerMillimetre(state, ppm);
                        ScreenRectangle placed = new ScreenRectangle(x0 + 2, y0 + top, cellWidth - 4, cellHeight - top - 2).transformAxisAligned(g.pose());
                        g.entity(state, scale, translation, rotation, null, placed.left(), placed.top(), placed.right(), placed.bottom());
                    }
                    ThemeText.text(g, this.font, build.video().id().getPath(), x0 + 5, y0 + 3, text);
                    ThemeText.text(g, this.font, names[row] + " · " + describe(AntennaSelfTests.spec(build.frame(), build.video())), x0 + 5, y0 + 13,
                            text);
                }
            }
            ThemeText.text(g, this.font, this.caption, this.width - this.font.width(this.caption) - 5, this.height - 11, text);
            super.extractRenderState(g, mouseX, mouseY, a);
        }
    }

    static String describe(VideoAntenna antenna) {
        return (antenna.count() == 2 ? "2×" : "") + Math.round(antenna.lengthMm()) + " mm " + antenna.cap().key();
    }

    private static final class SheetScene implements Screenshots.Scene {
        private final List<DroneBuild> cells;
        private final boolean dark;
        private final int columns;
        private final String caption;

        private SheetScene(List<DroneBuild> cells, boolean dark, int columns, String caption) {
            this.cells = cells;
            this.dark = dark;
            this.columns = columns;
            this.caption = caption;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof SheetScreen)) {
                mc.gui.setScreen(new SheetScreen(this.cells, this.dark, this.columns, this.caption));
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
            if (mc.gui.screen() instanceof SheetScreen) {
                mc.gui.setScreen(null);
            }
        }
    }

    private static final class SheetScreen extends Screen {
        private final List<DroneBuild> cells;
        private final boolean dark;
        private final int columns;
        private final String caption;

        private SheetScreen(List<DroneBuild> cells, boolean dark, int columns, String caption) {
            super(Component.empty());
            this.cells = cells;
            this.dark = dark;
            this.columns = Math.max(1, columns);
            this.caption = caption;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            g.fill(0, 0, this.width, this.height, this.dark ? DARK : LIGHT);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            int columns = this.columns;
            int rows = Math.max(1, (this.cells.size() + columns - 1) / columns);
            int cellWidth = this.width / columns;
            int cellHeight = this.height / rows;
            int text = this.dark ? Palette.TEXT : LIGHT_TEXT;
            for (int i = 0; i < this.cells.size(); i++) {
                int x0 = (i % columns) * cellWidth;
                int y0 = (i / columns) * cellHeight;
                DroneBuild build = this.cells.get(i);
                g.fill(x0, y0, x0 + cellWidth, y0 + 1, CELL_EDGE);
                g.fill(x0, y0, x0 + 1, y0 + cellHeight, CELL_EDGE);
                DronePreview.render(g, build, x0 + 4, y0 + 12, x0 + cellWidth - 4, y0 + cellHeight - 4, REAR_YAW);
                ThemeText.text(g, this.font, AntennaSelfTests.label(build), x0 + 5, y0 + 3, text);
            }
            ThemeText.text(g, this.font, this.caption, this.width - this.font.width(this.caption) - 5, this.height - 11, text);
            super.extractRenderState(g, mouseX, mouseY, a);
        }
    }
}
