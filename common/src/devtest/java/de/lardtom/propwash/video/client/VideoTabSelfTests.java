package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class VideoTabSelfTests {
    private static final String FUTURE = "video_tab_future";
    private static final List<String> CREATIVE_KEYS = List.of("propwash.video.config.fov_source", "propwash.video.config.latency_source",
            "propwash.video.config.creative.obstruction");
    private static final List<String> CREATIVE_SLIDERS = List.of("propwash.video.config.creative.latency", "propwash.video.config.creative.range",
            "propwash.video.config.creative.noise");
    private static final List<String> COMMON_KEYS = List.of("propwash.video.config.format", "propwash.video.config.reduce_flicker",
            "propwash.video.config.renderer", "propwash.video.config.led_lighting");

    private VideoTabSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("video: Video-Tab ohne Brille zeigt nur den Hinweis", 20, VideoTabSelfTests::noGogglesStep);
        SelfTest.clientStep("video: Video-Tab zeigt nur die Einstellungen der getragenen Brille, kein Umschalter", 20,
                VideoTabSelfTests::wornOnlyStep);
        SelfTest.clientStep("video: Video-Tab – Standardwerte und Speichern lassen verborgene Kreativ-Werte stehen", 20,
                VideoTabSelfTests::hiddenValuesStep);
        SelfTest.clientStep("video: Video-Tab folgt der Brille auf dem Kopf", 300, VideoTabSelfTests::followStep);
    }

    public static void registerScreenshots(boolean german) {
        String suffix = german ? " deutsch" : "";
        String file = german ? "_de" : "";
        Screenshots.step("bildschirmfotos: Video-Tab ohne Brille" + suffix, "configurator_video_no_goggles" + file, 30, new NoGogglesScene());
    }

    private static StepResult noGogglesStep(SelfTest.StepContext ctx) {
        VideoState.INSTANCE.overrideTestReceiver(true, null);
        try {
            List<AbstractWidget> widgets = new ArrayList<>();
            VideoConfigTab tab = new VideoConfigTab();
            tab.attach(host(widgets), 0, 0, 480, 280);
            if (!widgets.isEmpty()) {
                return StepResult.fail("Video-Tab ohne Brille hat Einstellungen: " + labels(widgets));
            }
            VideoConfig before = VideoSettings.get().copy();
            tab.resetDefaults();
            tab.save();
            return StepResult.check(same(before, VideoSettings.get()), "Video-Tab ohne Brille ändert die gespeicherten Werte");
        } finally {
            VideoState.INSTANCE.overrideTestReceiver(false, null);
            FpvImage.draft(null);
        }
    }

    private static StepResult wornOnlyStep(SelfTest.StepContext ctx) {
        try {
            for (VideoSystem system : VideoSystem.values()) {
                VideoState.INSTANCE.overrideTestReceiver(true, system);
                List<AbstractWidget> widgets = new ArrayList<>();
                new VideoConfigTab().attach(host(widgets), 0, 0, 480, 280);
                for (AbstractWidget widget : widgets) {
                    if (widget instanceof CycleButton<?> button && button.getValue() instanceof VideoSystem) {
                        return StepResult.fail("Video-Tab hat noch einen Umschalter für die Brille (" + system + ")");
                    }
                }
                boolean creative = system == VideoSystem.CREATIVE;
                for (String key : CREATIVE_KEYS) {
                    if (has(widgets, key) != creative) {
                        return StepResult.fail(system + "-Brille: Kreativ-Einstellung " + key + (creative ? " fehlt" : " sichtbar"));
                    }
                }
                for (String key : CREATIVE_SLIDERS) {
                    if (hasSlider(widgets, key) != creative) {
                        return StepResult.fail(system + "-Brille: Kreativ-Regler " + key + (creative ? " fehlt" : " sichtbar"));
                    }
                }
                for (String key : COMMON_KEYS) {
                    if (!has(widgets, key)) {
                        return StepResult.fail(system + "-Brille: Einstellung " + key + " fehlt");
                    }
                }
            }
            return StepResult.pass();
        } finally {
            VideoState.INSTANCE.overrideTestReceiver(false, null);
            FpvImage.draft(null);
        }
    }

    private static StepResult hiddenValuesStep(SelfTest.StepContext ctx) {
        VideoConfig saved = VideoSettings.get().copy();
        try {
            VideoConfig tuned = saved.copy();
            tuned.creativeFovFromModule = false;
            tuned.fovCreative = 150;
            tuned.creativeLatencyFromModule = false;
            tuned.creativeLatencyMs = 5;
            tuned.creativeRangeBlocks = 1500;
            tuned.creativeNoise = 0.5F;
            tuned.effectStrength = 0.2F;
            VideoSettings.CONFIG.set(tuned);
            VideoState.INSTANCE.overrideTestReceiver(true, VideoSystem.ANALOG);
            VideoConfigTab tab = new VideoConfigTab();
            tab.attach(host(new ArrayList<>()), 0, 0, 480, 280);
            tab.resetDefaults();
            tab.save();
            VideoConfig after = VideoSettings.get();
            if (after.effectStrength != new VideoConfig().effectStrength) {
                return StepResult.fail("Standardwerte setzen die sichtbaren Einstellungen nicht zurück: " + after.effectStrength);
            }
            if (after.creativeRangeBlocks != 1500 || after.fovCreative != 150 || after.creativeFovFromModule || after.creativeLatencyFromModule
                    || after.creativeLatencyMs != 5 || after.creativeNoise != 0.5F) {
                return StepResult.fail("Standardwerte mit Analog-Brille ändern die Kreativ-Werte");
            }
            VideoState.INSTANCE.overrideTestReceiver(true, VideoSystem.CREATIVE);
            VideoConfigTab creative = new VideoConfigTab();
            creative.attach(host(new ArrayList<>()), 0, 0, 480, 280);
            creative.resetDefaults();
            creative.save();
            return StepResult.check(VideoSettings.get().creativeRangeBlocks == new VideoConfig().creativeRangeBlocks
                    && VideoSettings.get().creativeFovFromModule, "Standardwerte mit Kreativ-Brille setzen die Kreativ-Werte nicht zurück");
        } finally {
            VideoState.INSTANCE.overrideTestReceiver(false, null);
            FpvImage.draft(null);
            VideoSettings.CONFIG.set(saved);
        }
    }

    private static StepResult followStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int stage = (int) ctx.state().getOrDefault("stage", 0);
        int since = ctx.tick() - (int) ctx.state().getOrDefault("since", 0);
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        VideoSystem worn = ContentIds.wornGogglesSystem(mc.player).orElse(null);
        switch (stage) {
            case 0, 5 -> {
                if (mc.gui.screen() instanceof ConfiguratorScreen open) {
                    open.discardAndClose();
                    return StepResult.pending();
                }
                Identifier goggles = stage == 0 ? null : ContentIds.GOGGLES_ANALOG;
                StepResult result = wear(ctx, "follow_wear_" + stage, goggles);
                if (result != null) {
                    return result;
                }
                if (worn != (stage == 0 ? null : VideoSystem.ANALOG)) {
                    return since > 100 ? StepResult.fail("Brille kommt beim Client nicht an: " + worn) : StepResult.pending();
                }
                return next(ctx, stage + 1);
            }
            case 1, 6 -> {
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                    ConfigTabs.open(null, "video");
                    return since > 100 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
                }
                ConfigTab active = screen.activeTab();
                if (active == null || !"video".equals(active.id())) {
                    screen.selectTab("video");
                    return StepResult.pending();
                }
                int formats = count(screen, "propwash.video.config.format");
                if (stage == 1) {
                    return formats == 0 ? next(ctx, 2) : StepResult.fail("Video-Tab zeigt ohne Brille Einstellungen");
                }
                if (formats != 1 || count(screen, "propwash.video.config.creative.obstruction") != 0) {
                    return StepResult.fail("Video-Tab zeigt nicht die Einstellungen der getragenen Analog-Brille");
                }
                screen.discardAndClose();
                return next(ctx, 7);
            }
            case 2, 3, 4 -> {
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                    return StepResult.fail("Konfigurator geschlossen");
                }
                VideoSystem wanted = stage == 2 ? VideoSystem.ANALOG : stage == 3 ? VideoSystem.CREATIVE : null;
                VideoState.INSTANCE.overrideTestReceiver(true, wanted);
                int formats = count(screen, "propwash.video.config.format");
                int creative = count(screen, "propwash.video.config.creative.obstruction");
                boolean shown = formats == (wanted == null ? 0 : 1) && creative == (wanted == VideoSystem.CREATIVE ? 1 : 0);
                if (!shown) {
                    if (since > 40) {
                        VideoState.INSTANCE.overrideTestReceiver(false, null);
                        return StepResult.fail("offener Video-Tab folgt dem Brillenwechsel nicht (" + wanted + "): " + formats + "/" + creative);
                    }
                    return StepResult.pending();
                }
                if (stage == 4) {
                    VideoState.INSTANCE.overrideTestReceiver(false, null);
                }
                return next(ctx, stage + 1);
            }
            default -> {
                StepResult result = wear(ctx, "follow_wear_end", null);
                if (result != null) {
                    return result;
                }
                if (worn != null) {
                    return since > 100 ? StepResult.fail("Brille lässt sich nicht absetzen") : StepResult.pending();
                }
                return StepResult.pass();
            }
        }
    }

    private static StepResult next(SelfTest.StepContext ctx, int stage) {
        ctx.state().put("stage", stage);
        ctx.state().put("since", ctx.tick());
        return StepResult.pending();
    }

    private static @Nullable StepResult wear(SelfTest.StepContext ctx, String key, @Nullable Identifier goggles) {
        Object value = ctx.state().get(key);
        if (!(value instanceof CompletableFuture<?> future)) {
            ctx.state().put(key, ctx.onServer(server -> {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    throw new IllegalStateException("kein Spieler auf dem Server");
                }
                players.getFirst().setItemSlot(EquipmentSlot.HEAD, goggles == null ? ItemStack.EMPTY
                        : ContentIds.item(goggles).map(ItemStack::new).orElse(ItemStack.EMPTY));
            }));
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(future.exceptionNow().getMessage());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static int count(ConfiguratorScreen screen, String key) {
        String label = Component.translatable(key).getString();
        int found = 0;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.getMessage().getString().startsWith(label)) {
                found++;
            }
        }
        return found;
    }

    private static boolean has(List<AbstractWidget> widgets, String key) {
        String label = Component.translatable(key).getString();
        return widgets.stream().anyMatch(widget -> widget.getMessage().getString().startsWith(label));
    }

    private static boolean hasSlider(List<AbstractWidget> widgets, String key) {
        String label = Component.translatable(key, "").getString().strip();
        return widgets.stream().anyMatch(widget -> widget.getMessage().getString().startsWith(label));
    }

    private static List<String> labels(List<AbstractWidget> widgets) {
        return widgets.stream().map(widget -> widget.getMessage().getString()).toList();
    }

    private static boolean same(VideoConfig a, VideoConfig b) {
        return a.effectStrength == b.effectStrength && a.imageFormat == b.imageFormat && a.cameraShake == b.cameraShake
                && a.reduceFlicker == b.reduceFlicker && a.renderer == b.renderer && a.ledLighting == b.ledLighting
                && a.creativeFovFromModule == b.creativeFovFromModule && a.fovCreative == b.fovCreative
                && a.creativeLatencyFromModule == b.creativeLatencyFromModule && a.creativeLatencyMs == b.creativeLatencyMs
                && a.creativeRangeBlocks == b.creativeRangeBlocks && a.creativeNoise == b.creativeNoise
                && a.creativeObstruction == b.creativeObstruction;
    }

    private static ConfigTabHost host(List<AbstractWidget> widgets) {
        return new ConfigTabHost() {
            @Override
            public <T extends AbstractWidget> T addWidget(T widget) {
                widgets.add(widget);
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
                return Minecraft.getInstance().gui.screen();
            }

            @Override
            public void rebuild() {
            }

            @Override
            public void changed() {
            }
        };
    }

    private static final class NoGogglesScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            StepResult worn = wear(ctx, FUTURE, null);
            if (worn != null) {
                return worn;
            }
            if (mc.player == null || ContentIds.wornGogglesSystem(mc.player).isPresent()) {
                return ctx.tick() > 200 ? StepResult.fail("Brille noch auf dem Kopf") : StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                ConfigTabs.open(null, "video");
                return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            ConfigTab active = screen.activeTab();
            if (active == null || !"video".equals(active.id())) {
                screen.selectTab("video");
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
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
        }
    }
}
