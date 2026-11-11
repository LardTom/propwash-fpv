package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonObject;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.VideoAntenna;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.build.AntennaRules;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.client.render.DronePreview;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.parts.PartDataProblems;
import de.lardtom.propwash.parts.PartJson;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AntennaNoneSelfTests {
    static final String PREFIX = AntennaSelfTests.PREFIX + "ohne Antennen – ";
    static final String SHOT = "antenna_none_test_drone";
    private static final int LIGHT = 0xFFD9DDE3;
    private static final int TEXT = 0xFF1E2024;
    private static final int CELL_EDGE = 0x40808080;

    private AntennaNoneSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + "Videoteil mit \"antenna\": \"none\": keine Antennen, kein Lack-Platz Antennenspitzen, Masse und Funk wie mit Antennen",
                1, AntennaNoneSelfTests::dataStep);
        if (!Screenshots.enabled()) {
            return;
        }
        SelfTest.setupStep(PREFIX + "Fenster für das Bildschirmfoto vorbereiten", 400, ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            return Screenshots.resizeWindow(ctx);
        });
        Screenshots.step(PREFIX + "Bildschirmfoto Testdrohne mit und ohne Antennen schräg von hinten", SHOT, 20, new Scene());
        SelfTest.clientStep(PREFIX + "Fenster zurücksetzen", 200, Screenshots::restoreWindow);
    }

    static VideoType withAntennas() {
        VideoType chosen = null;
        for (VideoType video : VideoType.values()) {
            if (!video.builtin()) {
                continue;
            }
            if (chosen == null || AntennaRules.antenna(video.definition()).count() > AntennaRules.antenna(chosen.definition()).count()) {
                chosen = video;
            }
        }
        if (chosen == null) {
            throw new IllegalStateException("kein eingebauter Videosender");
        }
        return chosen;
    }

    static VideoType withoutAntennas(VideoType video) {
        JsonObject json = PartJson.encode(video.definition());
        json.addProperty("antenna", "none");
        PartDataProblems problems = new PartDataProblems(video.id() + ".json");
        PartDefinition definition = PartJson.decode(PartKind.VIDEO, video.id(), json, problems);
        if (!(definition instanceof VideoDefinition decoded) || problems.hasErrors()) {
            throw new IllegalStateException("Teile-JSON mit \"antenna\": \"none\" abgelehnt: " + problems.errors());
        }
        return (VideoType) PartIndex.detached(decoded);
    }

    static DroneBuild testDrone(VideoType video) {
        return AntennaSelfTests.with(DroneBuild.FREESTYLE, video);
    }

    private static StepResult dataStep(SelfTest.StepContext ctx) {
        VideoType original = withAntennas();
        VideoType none;
        try {
            none = withoutAntennas(original);
        } catch (IllegalStateException e) {
            return StepResult.fail(e.getMessage());
        }
        DroneBuild with = testDrone(original);
        DroneBuild without = testDrone(none);
        DroneRenderResources res = DroneVisuals.resources();
        VideoAntenna spec = AntennaRules.antenna(none.definition(), without.frame().definition());
        DroneAssembly.Part withPart = res.assembly(with).antenna();
        DroneAssembly.Part withoutPart = res.assembly(without).antenna();
        Set<PaintSlot> slotsWith = PaintPicker.available(with);
        Set<PaintSlot> slotsWithout = PaintPicker.available(without);
        Map<PaintSlot, Integer> defaults = PaintPicker.defaults(without);
        VideoDefinition a = original.definition();
        VideoDefinition b = none.definition();
        List<String> wrong = new ArrayList<>();
        if (spec.count() != 0) {
            wrong.add("Antennen am Rahmen: " + spec);
        }
        if (withPart.solid().isEmpty() || !slotsWith.contains(PaintSlot.ANTENNA)) {
            wrong.add("Vergleich mit Antennen ohne Antennen-Form oder Lack-Platz");
        }
        if (!withoutPart.solid().isEmpty() || !withoutPart.translucent().isEmpty()) {
            wrong.add("Antennen-Form ohne Antennen nicht leer");
        }
        if (slotsWithout.contains(PaintSlot.ANTENNA) || defaults.containsKey(PaintSlot.ANTENNA)) {
            wrong.add("Lack-Platz Antennenspitzen ohne Antennen angeboten");
        }
        double massWith = BuildAnalysis.of(with).massGrams();
        double massWithout = BuildAnalysis.of(without).massGrams();
        if (Math.abs(with.totalMassGrams() - without.totalMassGrams()) > 1.0E-9 || Math.abs(massWith - massWithout) > 1.0E-9) {
            wrong.add(String.format(Locale.ROOT, "Masse %.1f g statt %.1f g", without.totalMassGrams(), with.totalMassGrams()));
        }
        if (a.rangeBlocks() != b.rangeBlocks() || a.link() != b.link() || a.latencyMs() != b.latencyMs() || a.vtxPowerMw() != b.vtxPowerMw()
                || a.imageQuality() != b.imageQuality()) {
            wrong.add("Funk oder Videolink geändert");
        }
        String report = String.format(Locale.ROOT, "%s mit %s gegen \"antenna\": \"none\" (%s): Antennen-Form %d gegen %d Teile, Lack-Plätze %d gegen %d "
                        + "(Antennenspitzen %s gegen %s), Masse %.1f gegen %.1f g, Reichweite %d gegen %d Blöcke",
                original.id(), describe(AntennaRules.antenna(a)), describe(spec), withPart.solid().isEmpty() ? 0 : 1, withoutPart.solid().isEmpty() ? 0 : 1,
                slotsWith.size(), slotsWithout.size(), slotsWith.contains(PaintSlot.ANTENNA), slotsWithout.contains(PaintSlot.ANTENNA),
                with.totalMassGrams(), without.totalMassGrams(), a.rangeBlocks(), b.rangeBlocks());
        if (!wrong.isEmpty()) {
            return StepResult.fail(report + " – " + String.join("; ", wrong));
        }
        ctx.log(report);
        return StepResult.pass();
    }

    static String describe(VideoAntenna antenna) {
        return antenna.count() == 0 ? "keine" : AntennaShowcase.describe(antenna);
    }

    private static final class Scene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof NoneScreen)) {
                VideoType original = withAntennas();
                mc.gui.setScreen(new NoneScreen(List.of(testDrone(original), testDrone(withoutAntennas(original)))));
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
            if (mc.gui.screen() instanceof NoneScreen) {
                mc.gui.setScreen(null);
            }
        }
    }

    private static final class NoneScreen extends Screen {
        private final List<DroneBuild> cells;

        private NoneScreen(List<DroneBuild> cells) {
            super(Component.empty());
            this.cells = cells;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            g.fill(0, 0, this.width, this.height, LIGHT);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            int cellWidth = this.width / Math.max(1, this.cells.size());
            for (int i = 0; i < this.cells.size(); i++) {
                DroneBuild build = this.cells.get(i);
                int x0 = i * cellWidth;
                g.fill(x0, 0, x0 + 1, this.height, CELL_EDGE);
                DronePreview.render(g, build, x0 + 4, 16, x0 + cellWidth - 4, this.height - 16, AntennaShowcase.REAR_YAW);
                ThemeText.text(g, this.font, label(build), x0 + 5, 4, TEXT);
            }
            String caption = DroneBuild.FREESTYLE.frame().id() + " · Testdrohne";
            ThemeText.text(g, this.font, caption, this.width - this.font.width(caption) - 5, this.height - 11, TEXT);
            super.extractRenderState(g, mouseX, mouseY, a);
        }

        private static String label(@Nullable DroneBuild build) {
            if (build == null) {
                return "";
            }
            VideoAntenna antenna = AntennaRules.antenna(build.video().definition(), build.frame().definition());
            return build.video().id().getPath() + " · " + (antenna.count() == 0 ? "\"antenna\": \"none\"" : describe(antenna));
        }
    }
}
