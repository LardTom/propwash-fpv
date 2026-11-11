package de.lardtom.propwash.trick.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.trick.TrickFamily;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.trick.tutorial.TrickTutorial;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class TrickTutorialShowcase {
    private static final String PREFIX = "bildschirmfotos: Freestyle-Tutorial ";

    private record Shot(TrickFamily family, TrickType trick, String stage, float fraction) {
    }

    private static final List<Shot> SHOTS = List.of(
            new Shot(TrickFamily.FLIPS_ROLLS, TrickType.FLIP, "rotation", 0.45F),
            new Shot(TrickFamily.AIR, TrickType.JUICY_FLICK, "flick", 0.4F),
            new Shot(TrickFamily.LOOPS, TrickType.POWER_LOOP, "over_top", 0.3F),
            new Shot(TrickFamily.MATTY_SPLIT, TrickType.MATTY_FLIP, "punch", 0.5F),
            new Shot(TrickFamily.SPINS, TrickType.MCTWIST, "spin", 0.5F),
            new Shot(TrickFamily.DIVES, TrickType.DIVE, "dive", 0.6F),
            new Shot(TrickFamily.OBSTACLES, TrickType.GAP, "pass", 0.5F),
            new Shot(TrickFamily.SBANG_DEX, TrickType.SBANG, "split_s", 0.5F),
            new Shot(TrickFamily.THREE_D, TrickType.TIC_TOC, "rock", 0.4F));

    private TrickTutorialShowcase() {
    }

    public static void register() {
        for (Shot shot : SHOTS) {
            String family = shot.family().key();
            Screenshots.step(PREFIX + "Vorschau " + family, "trick_tutorial_preview_" + family, 10, new PreviewScene(shot));
            Screenshots.step(PREFIX + "Player " + family, "trick_tutorial_player_" + family, 6, new PlayerScene(shot, StickMode.MODE_2));
        }
        Shot flip = SHOTS.getFirst();
        Screenshots.step(PREFIX + "Stick-Cam Mode 2", "trick_tutorial_stickcam_mode2", 6, new PlayerScene(flip, StickMode.MODE_2));
        Screenshots.step(PREFIX + "Stick-Cam Mode 1", "trick_tutorial_stickcam_mode1", 6, new PlayerScene(flip, StickMode.MODE_1));
        Screenshots.step(PREFIX + "zurück in die gescrollte Liste", "trick_tutorial_back_scrolled", 6, new BackScene(false));
        Screenshots.step(PREFIX + "zurück, danach nach oben gescrollt", "trick_tutorial_back_scrolled_up", 6, new BackScene(true));
        String back = PREFIX + "Sprache zurücksetzen";
        SelfTest.switchStep(PREFIX + "Sprache Deutsch", back, 1200, ctx -> Screenshots.language(ctx, "de_de"));
        Screenshots.step(PREFIX + "Vorschau deutsch", "trick_tutorial_preview_flips_rolls_de", 10, new PreviewScene(flip));
        Screenshots.step(PREFIX + "Player deutsch", "trick_tutorial_player_flips_rolls_de", 6, new PlayerScene(flip, StickMode.MODE_2));
        Screenshots.step(PREFIX + "Player Hindernis deutsch", "trick_tutorial_player_obstacles_de", 6, new PlayerScene(SHOTS.get(6), StickMode.MODE_2));
        SelfTest.clientStep(back, 1200, Screenshots::restoreLanguage);
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

    private static final class BackScene implements Screenshots.Scene {
        private final boolean up;
        private int stage;
        private int since;
        private @Nullable TrickType far;
        private @Nullable ScreenRectangle row;

        private BackScene(boolean up) {
            this.up = up;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                TrickTutorialSelfTests.closeAll();
                Screenshots.parkMouse(mc);
                this.stage = 0;
                return StepResult.pending();
            }
            if (this.stage == 2) {
                if (!(mc.gui.screen() instanceof SceneScreen player)) {
                    return ctx.tick() - this.since > 200 ? StepResult.fail("Klick öffnet keinen Player") : StepResult.pending();
                }
                if (player.frames() < 3) {
                    return StepResult.pending();
                }
                player.onClose();
                this.stage = 3;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            TrickConfigTab tab = TrickTutorialSelfTests.openTab(ctx);
            if (tab == null) {
                return ctx.tick() > 300 ? StepResult.fail("Tab Freestyle öffnet nicht") : StepResult.pending();
            }
            if (this.stage == 0) {
                tab.pointAt(null);
                tab.search("");
                tab.filterFamily(null);
                this.far = TrickListLayoutTour.farthest(tab);
                if (this.far == null) {
                    return StepResult.fail("Liste leer");
                }
                tab.reveal(this.far);
                this.stage = 1;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            if (ctx.tick() - this.since < 3) {
                return StepResult.pending();
            }
            if (this.stage == 1) {
                ScreenRectangle at = this.far == null ? null : tab.rowOf(this.far);
                if (at == null || !TrickListLayoutTour.click(tab, at)) {
                    return StepResult.fail("Klick auf " + this.far + " in Zeile " + at + " geht nicht");
                }
                this.row = at;
                this.stage = 2;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            if (this.stage == 3) {
                ScreenRectangle at = this.row;
                if (this.up && at != null) {
                    int extra = 1;
                    for (int i = 0; i < 400 && tab.contentAbove() && extra >= 0; i++) {
                        tab.mouseScrolled(at.left() + 8, at.top() + at.height() / 2.0, 0.0, 1.0);
                        TextField search = tab.searchBox();
                        if (search != null && search.visible) {
                            extra--;
                        }
                    }
                }
                this.stage = 4;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            TrickTutorialSelfTests.closeAll();
        }
    }

    private static final class PlayerScene implements Screenshots.Scene {
        private final Shot shot;
        private final StickMode mode;
        private @Nullable StickMode saved;
        private float time = Float.NaN;

        private PlayerScene(Shot shot, StickMode mode) {
            this.shot = shot;
            this.mode = mode;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                TrickTutorialSelfTests.closeAll();
                if (this.saved == null) {
                    this.saved = InputConfig.get().stickMode;
                }
                InputConfig.get().stickMode = this.mode;
                Screenshots.parkMouse(mc);
                SceneScreen player = TrickTutorials.open(this.shot.trick(), null);
                TrickTutorial tutorial = TrickTutorials.now(this.shot.trick()).tutorial();
                this.time = TrickTutorialSelfTests.stageTime(player, tutorial, this.shot.stage(), this.shot.fraction());
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
