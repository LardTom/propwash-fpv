package de.lardtom.propwash.trick.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.UiScaleSelfTests;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.trick.tutorial.TrickTutorial;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class TrickTutorialSelfTests {
    public static final String TAB = "tricks";
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);

    private TrickTutorialSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("tricks: Tutorial-Szenen für alle Tricks mit Texten", 1200, TrickTutorialSelfTests::scenesStep);
        SelfTest.clientStep("tricks: Tutorial-Vorschau beim Hovern, Klick öffnet den Player", 900, new HoverTour()::step);
        SelfTest.clientStep("tricks: Tutorial-Player mit Schritten, Pause, 0,5× und Stick-Cam", 900, new PlayerTour()::step);
        SelfTest.clientStep("tricks: Stick-Cam folgt dem Stick-Modus (Mode 2 und Mode 1)", 600, new ModeTour()::step);
        TrickListLayoutTour.register();
    }

    public static List<UiScaleSelfTests.Case> uiScaleCases() {
        return List.of(new PlayerCase(TrickType.SBANG), new PlayerCase(TrickType.FLIP));
    }

    @SuppressWarnings("unchecked")
    private static StepResult scenesStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            List<CompletableFuture<TrickTutorials.Prepared>> futures = new ArrayList<>();
            for (TrickType type : TrickType.REPERTOIRE) {
                futures.add(TrickTutorials.prepare(type));
            }
            state.put("futures", futures);
            return StepResult.pending();
        }
        List<CompletableFuture<TrickTutorials.Prepared>> futures = (List<CompletableFuture<TrickTutorials.Prepared>>) state.get("futures");
        for (CompletableFuture<TrickTutorials.Prepared> future : futures) {
            if (!future.isDone()) {
                return StepResult.pending();
            }
        }
        int steps = 0;
        for (int i = 0; i < futures.size(); i++) {
            TrickType type = TrickType.REPERTOIRE.get(i);
            CompletableFuture<TrickTutorials.Prepared> future = futures.get(i);
            if (future.isCompletedExceptionally()) {
                return StepResult.fail(type + ": Tutorial nicht erzeugt: " + future.exceptionNow());
            }
            TrickTutorial tutorial = future.join().tutorial();
            Scene scene = tutorial.scene();
            if (tutorial.trick() != type || scene.stepCount() < 3) {
                return StepResult.fail(type + ": " + scene.stepCount() + " Schritte");
            }
            List<String> keys = new ArrayList<>(List.of(tutorial.titleKey()));
            for (int step = 0; step < scene.stepCount(); step++) {
                keys.add(tutorial.stepTitleKey(step));
                keys.add(tutorial.stepTextKey(step));
            }
            for (String key : keys) {
                if (!Language.getInstance().has(key)) {
                    return StepResult.fail(type + ": Text fehlt " + key);
                }
            }
            steps += scene.stepCount();
        }
        ctx.log("tricks: " + futures.size() + " Tutorials mit " + steps + " Schritten erzeugt, alle Texte vorhanden");
        return StepResult.pass();
    }

    static @Nullable TrickConfigTab openTab(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            ConfigTabs.open(null, TAB);
            return null;
        }
        if (!(screen.activeTab() instanceof TrickConfigTab tab)) {
            screen.selectTab(TAB);
            return null;
        }
        return screen.extractedFrames(TAB) > 2 ? tab : null;
    }

    static void closeAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof SceneScreen player) {
            player.onClose();
        }
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
    }

    static float stageTime(SceneScreen player, TrickTutorial tutorial, String stage, float fraction) {
        int index = Math.max(0, tutorial.choreography().stages().indexOf(stage));
        Scene scene = player.scene();
        float from = scene.stepStart(index);
        float to = scene.stepEnd(index);
        if (tutorial.obstacleBlocks() > 0) {
            float best = from;
            float bestDistance = Float.MAX_VALUE;
            for (float t = from; t <= to; t += 0.5F) {
                SceneState state = SceneEvaluator.evaluate(scene, t);
                ActorState drone = state.actor(TrickTutorial.DRONE);
                if (drone == null) {
                    continue;
                }
                for (ActorState actor : state.visibleActors()) {
                    if (actor.id().startsWith("obstacle_")) {
                        float distance = drone.position().distance(actor.position());
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = t;
                        }
                    }
                }
            }
            return best;
        }
        return from + (to - from) * fraction;
    }

    private static final class HoverTour {
        private int phase;
        private int since;
        private @Nullable ScreenRectangle row;
        private float previewTime = Float.NaN;

        StepResult step(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.phase == 0) {
                closeAll();
                this.phase = 1;
                return StepResult.pending();
            }
            if (this.phase == 1) {
                TrickConfigTab tab = openTab(ctx);
                if (tab == null) {
                    return ctx.tick() > 300 ? StepResult.fail("Tab Freestyle öffnet nicht") : StepResult.pending();
                }
                if (this.since == 0) {
                    tab.search("");
                    tab.filterFamily(null);
                    tab.reveal(TrickType.FLIP);
                    this.since = ctx.tick();
                    return StepResult.pending();
                }
                ScreenRectangle row = ctx.tick() - this.since >= 3 ? tab.rowOf(TrickType.FLIP) : null;
                if (row == null) {
                    return ctx.tick() - this.since > 60 ? StepResult.fail("Zeile Flip fehlt") : StepResult.pending();
                }
                this.row = row;
                LayoutProbe.pointMouse(mc, row.left() + 8, row.top() + row.height() / 2);
                tab.pointAt(row);
                this.since = ctx.tick();
                this.phase = 2;
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof TrickConfigTab tab) || this.row == null) {
                if (this.phase == 4 && mc.gui.screen() instanceof SceneScreen) {
                    return this.player(ctx);
                }
                if (this.phase == 5) {
                    return StepResult.fail("Zurück aus dem Player öffnet nicht den Konfigurator: " + mc.gui.screen());
                }
                return StepResult.fail("Konfigurator geschlossen in Phase " + this.phase);
            }
            ScreenRectangle row = this.row;
            TrickPreview preview = tab.preview();
            if (this.phase == 2) {
                LayoutProbe.pointMouse(mc, row.left() + 8, row.top() + row.height() / 2);
                int waited = ctx.tick() - this.since;
                if (waited <= 2 && preview.visible()) {
                    return StepResult.fail("Vorschau erscheint ohne Hover-Zeit");
                }
                if (!preview.visible() || preview.trick() != TrickType.FLIP || preview.frames() < 5) {
                    return waited > 300 ? StepResult.fail("keine Vorschau nach " + waited + " Ticks (" + preview.trick() + ", " + preview.frames()
                            + ", Zeile " + row + ", unter der Maus " + tab.hovered() + ")") : StepResult.pending();
                }
                ScreenRectangle box = preview.box();
                ScreenRectangle tooltip = preview.tooltip();
                if (box == null || tooltip == null || box.intersects(tooltip) || box.intersects(row)) {
                    return StepResult.fail("Vorschau " + box + " überdeckt Tooltip " + tooltip + " oder Zeile " + row);
                }
                this.previewTime = preview.lastTime();
                this.since = ctx.tick();
                this.phase = 3;
                ctx.log("tricks: Vorschau nach " + waited + " Ticks bei " + box + ", Tooltip " + tooltip);
                return StepResult.pending();
            }
            if (this.phase == 3) {
                LayoutProbe.pointMouse(mc, row.left() + 8, row.top() + row.height() / 2);
                if (ctx.tick() - this.since < 12) {
                    return StepResult.pending();
                }
                if (!(preview.lastTime() != this.previewTime)) {
                    return StepResult.fail("Vorschau läuft nicht (Zeit " + preview.lastTime() + ")");
                }
                tab.pointAt(null);
                if (!tab.mouseClicked(new MouseButtonEvent(row.left() + 8, row.top() + row.height() / 2.0, LEFT), false)) {
                    return StepResult.fail("Klick auf den Trick nimmt der Tab nicht an");
                }
                this.phase = 4;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            return StepResult.fail("Phase " + this.phase + " ohne Player");
        }

        private StepResult player(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            SceneScreen player = (SceneScreen) mc.gui.screen();
            if (player.frames() < 3) {
                return StepResult.pending();
            }
            if (!player.scene().id().equals(TrickTutorial.sceneId(TrickType.FLIP)) || !player.stickCamAvailable()
                    || !(player.parent() instanceof ConfiguratorScreen)) {
                return StepResult.fail("Player zeigt " + player.scene().id() + ", Stick-Cam " + player.stickCamAvailable() + ", zurück zu " + player.parent());
            }
            Component title = player.getTitle();
            if (!title.getString().equals(Component.translatable(TrickType.FLIP.translationKey()).getString())) {
                return StepResult.fail("Titel " + title.getString());
            }
            player.onClose();
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof TrickConfigTab)) {
                return StepResult.fail("Zurück führt nicht in den Tab Freestyle: " + mc.gui.screen());
            }
            screen.discardAndClose();
            ctx.log("tricks: Klick öffnet " + player.scene().id() + " mit " + player.scene().stepCount() + " Schritten, Zurück führt in den Tab Freestyle");
            return StepResult.pass();
        }
    }

    private static final class PlayerTour {
        private int phase;
        private int since;
        private float mark;
        private long camFrames;

        StepResult step(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.phase == 0) {
                closeAll();
                TrickTutorials.open(TrickType.MCTWIST, null);
                this.phase = 1;
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof SceneScreen player)) {
                return StepResult.fail("Player geschlossen in Phase " + this.phase);
            }
            TrickTutorial tutorial = TrickTutorials.now(TrickType.MCTWIST).tutorial();
            switch (this.phase) {
                case 1 -> {
                    if (player.frames() < 5) {
                        return StepResult.pending();
                    }
                    if (player.scene().stepCount() != tutorial.choreography().stages().size()) {
                        return StepResult.fail("Player zeigt " + player.scene().stepCount() + " Schritte");
                    }
                    for (int step = 0; step < player.scene().stepCount(); step++) {
                        if (!player.stepTitleKey(step).equals(tutorial.stepTitleKey(step)) || !player.stepTextKey(step).equals(tutorial.stepTextKey(step))) {
                            return StepResult.fail("Schritt " + step + " zeigt " + player.stepTextKey(step));
                        }
                    }
                    if (!player.playback().playing() || player.playback().time() <= 0.0F) {
                        return StepResult.fail("Player spielt nicht ab");
                    }
                    player.togglePlay();
                    this.mark = player.playback().time();
                    this.since = ctx.tick();
                    this.phase = 2;
                }
                case 2 -> {
                    if (ctx.tick() - this.since < 6) {
                        return StepResult.pending();
                    }
                    if (player.playback().playing() || player.playback().time() != this.mark) {
                        return StepResult.fail("Pause hält nicht an: " + this.mark + " → " + player.playback().time());
                    }
                    int step = player.playback().step();
                    player.next();
                    if (player.playback().step() != step + 1) {
                        return StepResult.fail("Schritt vor: " + step + " → " + player.playback().step());
                    }
                    player.previous();
                    if (player.playback().step() != step) {
                        return StepResult.fail("Schritt zurück: " + player.playback().step() + " statt " + step);
                    }
                    player.playback().jumpTo(0);
                    player.toggleSlowMotion();
                    if (!player.slowMotion() || player.playback().speed() != SceneScreen.SLOW_MOTION) {
                        return StepResult.fail("0,5× nicht aktiv: " + player.playback().speed());
                    }
                    player.togglePlay();
                    this.mark = player.playback().time();
                    this.since = ctx.tick();
                    this.phase = 3;
                }
                case 3 -> {
                    if (ctx.tick() - this.since < 40) {
                        return StepResult.pending();
                    }
                    float advanced = player.playback().time() - this.mark;
                    float expected = (ctx.tick() - this.since) * SceneScreen.SLOW_MOTION;
                    if (advanced < expected * 0.5F || advanced > expected * 1.6F) {
                        return StepResult.fail("0,5×: " + advanced + " Ticks in " + (ctx.tick() - this.since) + " Ticks");
                    }
                    player.toggleSlowMotion();
                    if (player.slowMotion()) {
                        return StepResult.fail("0,5× lässt sich nicht ausschalten");
                    }
                    if (!player.stickCamShown() || !player.stickCam().submitted() || player.stickCamBox() == null) {
                        return StepResult.fail("Stick-Cam fehlt: gezeigt " + player.stickCamShown() + ", gezeichnet " + player.stickCam().submitted());
                    }
                    ScreenRectangle box = player.stickCamBox();
                    if (!player.viewport().encompasses(box)) {
                        return StepResult.fail("Stick-Cam " + box + " außerhalb der Bühne " + player.viewport());
                    }
                    ctx.log("tricks: 0,5× spielt " + advanced + " Ticks in " + (ctx.tick() - this.since) + " Ticks, Stick-Cam " + box);
                    player.toggleStickCam();
                    this.camFrames = player.stickCam().frames();
                    this.since = ctx.tick();
                    this.phase = 4;
                }
                case 4 -> {
                    if (ctx.tick() - this.since < 5) {
                        return StepResult.pending();
                    }
                    if (player.stickCamShown() || player.stickCamBox() != null || player.stickCam().frames() != this.camFrames) {
                        return StepResult.fail("Stick-Cam lässt sich nicht ausschalten");
                    }
                    player.toggleStickCam();
                    this.since = ctx.tick();
                    this.phase = 5;
                }
                case 5 -> {
                    if (ctx.tick() - this.since < 5) {
                        return StepResult.pending();
                    }
                    if (!player.stickCamShown() || player.stickCam().frames() <= this.camFrames) {
                        return StepResult.fail("Stick-Cam lässt sich nicht wieder einschalten");
                    }
                    player.onClose();
                    return StepResult.pass();
                }
                default -> {
                    return StepResult.fail("Phase " + this.phase);
                }
            }
            return StepResult.pending();
        }
    }

    private static final class ModeTour {
        private int phase;
        private int since;
        private @Nullable StickMode saved;
        private StickMode.@Nullable Sticks mode2;

        StepResult step(SelfTest.StepContext ctx) {
            try {
                StepResult result = this.run(ctx);
                if (result.status() != StepResult.Status.PENDING) {
                    this.restore();
                }
                return result;
            } catch (RuntimeException e) {
                this.restore();
                throw e;
            }
        }

        private void restore() {
            if (this.saved != null) {
                InputConfig.get().stickMode = this.saved;
                this.saved = null;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof SceneScreen player) {
                player.onClose();
            }
        }

        private StepResult run(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.phase == 0) {
                closeAll();
                this.saved = InputConfig.get().stickMode;
                InputConfig.get().stickMode = StickMode.MODE_2;
                SceneScreen player = TrickTutorials.open(TrickType.FLIP, null);
                TrickTutorial tutorial = TrickTutorials.now(TrickType.FLIP).tutorial();
                player.playback().pause();
                player.playback().seek(stageTime(player, tutorial, "rotation", 0.4F));
                this.since = ctx.tick();
                this.phase = 1;
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof SceneScreen player)) {
                return StepResult.fail("Player geschlossen");
            }
            if (ctx.tick() - this.since < 5) {
                return StepResult.pending();
            }
            StickMode.Sticks placed = player.stickCam().lastPlaced();
            if (placed == null || player.stickCam().lastMode() == null) {
                return StepResult.fail("Stick-Cam ohne Knüppelstellung");
            }
            if (this.phase == 1) {
                if (player.stickCam().lastMode() != StickMode.MODE_2 || Math.abs(placed.rightY()) < 0.5F || Math.abs(placed.leftX()) > 0.1F) {
                    return StepResult.fail("Mode 2: Nick nicht rechts " + placed);
                }
                this.mode2 = placed;
                InputConfig.get().stickMode = StickMode.MODE_1;
                this.since = ctx.tick();
                this.phase = 2;
                return StepResult.pending();
            }
            StickMode.Sticks mode2 = this.mode2;
            if (player.stickCam().lastMode() != StickMode.MODE_1 || mode2 == null || Math.abs(placed.leftY() - mode2.rightY()) > 1.0E-4F
                    || Math.abs(placed.rightY() - mode2.leftY()) > 1.0E-4F) {
                return StepResult.fail("Mode 1: Nick nicht links " + placed + " (Mode 2 " + mode2 + ")");
            }
            ctx.log("tricks: Stick-Cam Mode 2 " + mode2 + ", Mode 1 " + placed);
            return StepResult.pass();
        }
    }

    private static final class PlayerCase implements UiScaleSelfTests.Case {
        private final TrickType trick;

        private PlayerCase(TrickType trick) {
            this.trick = trick;
        }

        @Override
        public String name() {
            return "Trick-Tutorial " + this.trick.key();
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof SceneScreen player) || !player.scene().id().equals(TrickTutorial.sceneId(this.trick))) {
                TrickTutorials.open(this.trick, null);
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public int pages(Screen screen) {
            return 2;
        }

        @Override
        public String page(Screen screen, int index) {
            if (!(screen instanceof SceneScreen player)) {
                return "";
            }
            int longest = 0;
            int most = 0;
            Minecraft mc = Minecraft.getInstance();
            for (int step = 0; step < player.scene().stepCount(); step++) {
                int lines = mc.font.split(Component.translatable(player.stepTextKey(step)), player.captionWidth()).size();
                if (lines > most) {
                    most = lines;
                    longest = step;
                }
            }
            player.playback().pause();
            player.playback().jumpTo(index == 0 ? 0 : longest);
            return index == 0 ? "" : "Schritt " + (longest + 1);
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}
