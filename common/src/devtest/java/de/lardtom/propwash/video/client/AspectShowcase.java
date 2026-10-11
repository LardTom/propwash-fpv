package de.lardtom.propwash.video.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.osd.client.OsdConfigTab;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.video.ImageFormat;
import net.minecraft.client.Minecraft;

public final class AspectShowcase {
    private static final String SAVED = "aspect_saved";

    private AspectShowcase() {
    }

    public static Screenshots.Scene format(ImageFormat format, Screenshots.Scene scene) {
        return new Screenshots.Scene() {
            @Override
            public StepResult prepare(SelfTest.StepContext ctx) {
                if (!ctx.state().containsKey(SAVED)) {
                    ctx.state().put(SAVED, VideoSettings.get().copy());
                    VideoConfig config = VideoSettings.get().copy();
                    config.imageFormat = format;
                    VideoSettings.CONFIG.set(config);
                }
                return scene.prepare(ctx);
            }

            @Override
            public void hold(SelfTest.StepContext ctx) {
                scene.hold(ctx);
            }

            @Override
            public void finish(SelfTest.StepContext ctx) {
                ctx.log("Bildformat " + format + " → " + FpvImage.format() + ", Bildbereich " + FpvImage.area());
                scene.finish(ctx);
                if (ctx.state().remove(SAVED) instanceof VideoConfig saved) {
                    VideoSettings.CONFIG.set(saved);
                }
            }
        };
    }

    public static Screenshots.Scene osdEditor() {
        return new Screenshots.Scene() {
            @Override
            public StepResult prepare(SelfTest.StepContext ctx) {
                Minecraft mc = Minecraft.getInstance();
                Screenshots.parkMouse(mc);
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                    ConfigTabs.open(null, OsdConfigTab.ID);
                    return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
                }
                ConfigTab active = screen.activeTab();
                if (active == null || !OsdConfigTab.ID.equals(active.id())) {
                    screen.selectTab(OsdConfigTab.ID);
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
                if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                    screen.discardAndClose();
                }
            }
        };
    }
}
