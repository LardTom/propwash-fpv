package de.lardtom.propwash.trick.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.client.ForwardFlightShowcase;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.trick.TrickFamily;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.trick.tutorial.TrickTutorial;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class TrickDirectionShowcase {
    private static final String PREFIX = "bildschirmfotos: Freestyle-Tutorial Flugrichtung ";

    private record Shot(TrickFamily family, TrickType trick, String file) {
    }

    private record Moment(Shot shot, String stage, float fraction, float zoom) {
    }

    private static final List<Shot> SHOTS = List.of(
            new Shot(TrickFamily.LOOPS, TrickType.POWER_LOOP, "power_loop"),
            new Shot(TrickFamily.MATTY_SPLIT, TrickType.SPLIT_S, "split_s"),
            new Shot(TrickFamily.FLIPS_ROLLS, TrickType.VANNY_ROLL, "vanny_roll"));

    private TrickDirectionShowcase() {
    }

    public static void register() {
        List<String> moves = List.of("pull_up", "half_roll", "yaw_out");
        for (int i = 0; i < SHOTS.size(); i++) {
            Shot shot = SHOTS.get(i);
            Screenshots.step(PREFIX + "Vorschau " + shot.file(), "trick_direction_preview_" + shot.file(), 10, new PreviewScene(shot));
            Screenshots.step(PREFIX + "Player Anflug " + shot.file(), "trick_direction_player_" + shot.file() + "_approach", 6,
                    new PlayerScene(new Moment(shot, "approach", 0.85F, 2.2F)));
            Screenshots.step(PREFIX + "Player " + moves.get(i) + " " + shot.file(), "trick_direction_player_" + shot.file() + "_" + moves.get(i), 6,
                    new PlayerScene(new Moment(shot, moves.get(i), 0.35F, 1.6F)));
        }
        Screenshots.step(PREFIX + "Drohne im Flug von der Seite", "trick_direction_in_flight", 3, ForwardFlightShowcase.scene());
    }

    private static final class PreviewScene implements Screenshots.Scene {
        private final Shot shot;
        private @Nullable ScreenRectangle row;
        private int filtered = -1;

        private PreviewScene(Shot shot) {
            this.shot = shot;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            if (ctx.tick() == 0) {
                TrickTutorialSelfTests.closeAll();
                return StepResult.pending();
            }
            TrickConfigTab tab = TrickTutorialSelfTests.openTab(ctx);
            if (tab == null) {
                return ctx.tick() > 300 ? StepResult.fail("Tab Freestyle öffnet nicht") : StepResult.pending();
            }
            if (this.filtered < 0) {
                tab.search("");
                tab.filterFamily(this.shot.family());
                tab.reveal(this.shot.trick());
                this.filtered = ctx.tick();
                return StepResult.pending();
            }
            if (this.row == null) {
                if (ctx.tick() - this.filtered < 3) {
                    return StepResult.pending();
                }
                this.row = tab.rowOf(this.shot.trick());
                return this.row == null ? StepResult.fail("Zeile für " + this.shot.trick() + " fehlt") : StepResult.pending();
            }
            this.hold(ctx);
            TrickPreview preview = tab.preview();
            if (preview.visible() && preview.trick() == this.shot.trick() && preview.frames() > 8) {
                return StepResult.pass();
            }
            return ctx.tick() > 400 ? StepResult.fail("keine Vorschau für " + this.shot.trick()) : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            ScreenRectangle row = this.row;
            if (row != null) {
                LayoutProbe.pointMouse(Minecraft.getInstance(), row.left() + 8, row.top() + row.height() / 2);
                if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen
                        && screen.activeTab() instanceof TrickConfigTab tab) {
                    tab.pointAt(row);
                }
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
            TrickTutorialSelfTests.closeAll();
        }
    }

    private static final class PlayerScene implements Screenshots.Scene {
        private final Moment moment;
        private @Nullable StickMode saved;
        private float time = Float.NaN;

        private PlayerScene(Moment moment) {
            this.moment = moment;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                TrickTutorialSelfTests.closeAll();
                if (this.saved == null) {
                    this.saved = InputConfig.get().stickMode;
                }
                InputConfig.get().stickMode = StickMode.MODE_2;
                Screenshots.parkMouse(mc);
                SceneScreen player = TrickTutorials.open(this.moment.shot().trick(), null);
                TrickTutorial tutorial = TrickTutorials.now(this.moment.shot().trick()).tutorial();
                this.time = TrickTutorialSelfTests.stageTime(player, tutorial, this.moment.stage(), this.moment.fraction());
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof SceneScreen player)) {
                return StepResult.fail("Player öffnet nicht");
            }
            this.hold(ctx);
            return player.frames() > 6 && player.stickCam().frames() > 3 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            if (Minecraft.getInstance().gui.screen() instanceof SceneScreen player && !Float.isNaN(this.time)) {
                player.playback().pause();
                player.playback().seek(this.time);
                player.snapView(0.0F, 0.0F, this.moment.zoom());
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (this.saved != null) {
                InputConfig.get().stickMode = this.saved;
                this.saved = null;
            }
            TrickTutorialSelfTests.closeAll();
        }
    }
}
