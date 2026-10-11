package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.widget.InfoField;
import de.lardtom.propwash.video.FovMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CameraFovSelfTests {
    public static final String STEP = "video: Sichtfeld folgt dem Videomodul der Drohne (Drohnenwechsel, Kreativ-Brille, Video-Tab)";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String IDS = "ids";
    private static final String SAVED = "saved";
    private static final int SETTLE_TICKS = 3;

    private CameraFovSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(STEP, 600, CameraFovSelfTests::step);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        VideoState video = VideoState.INSTANCE;
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            state.put(SAVED, VideoSettings.get().copy());
            List<Integer> spawned = new CopyOnWriteArrayList<>();
            state.put(IDS, spawned);
            state.put(FUTURE, ctx.onServer(server -> spawned.addAll(spawn(server, List.of(DroneBuild.RACER, DroneBuild.CINEWHOOP,
                    DroneBuild.FREESTYLE)))));
            state.put(PHASE, 1);
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return finish(ctx, "Drohnen spawnen: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) state.get(IDS);
        List<QuadcopterEntity> drones = new ArrayList<>();
        for (int id : ids) {
            Entity entity = mc.level == null ? null : mc.level.getEntity(id);
            if (!(entity instanceof QuadcopterEntity drone)) {
                return ctx.tick() > 300 ? finish(ctx, "Drohne " + id + " auf dem Client nicht sichtbar") : StepResult.pending();
            }
            drones.add(drone);
        }
        QuadcopterEntity analog = drones.get(0);
        QuadcopterEntity lite = drones.get(1);
        QuadcopterEntity hd = drones.get(2);
        switch (phase) {
            case 1 -> {
                video.attachTestSource(analog);
                video.overrideTestReceiver(true, VideoSystem.ANALOG);
                return next(ctx, 2);
            }
            case 2 -> {
                if (waiting(ctx)) {
                    return StepResult.pending();
                }
                String problem = expect(video, VideoType.ANALOG_RACE, FovMath.cameraHorizontalDegrees(VideoType.ANALOG_RACE), "analoge Brille, Race-Kamera");
                if (problem != null) {
                    return finish(ctx, problem);
                }
                problem = tab(VideoType.ANALOG_RACE, false);
                if (problem != null) {
                    return finish(ctx, problem);
                }
                video.attachTestSource(lite);
                video.overrideTestReceiver(true, VideoSystem.DIGITAL);
                return next(ctx, 3);
            }
            case 3 -> {
                if (waiting(ctx)) {
                    return StepResult.pending();
                }
                String problem = expect(video, VideoType.DIGITAL_LITE, FovMath.cameraHorizontalDegrees(VideoType.DIGITAL_LITE),
                        "Drohnenwechsel auf das digitale Lite-System");
                if (problem == null) {
                    problem = tab(VideoType.DIGITAL_LITE, false);
                }
                if (problem != null) {
                    return finish(ctx, problem);
                }
                video.attachTestSource(hd);
                return next(ctx, 4);
            }
            case 4 -> {
                if (waiting(ctx)) {
                    return StepResult.pending();
                }
                String problem = expect(video, VideoType.DIGITAL_HD, FovMath.cameraHorizontalDegrees(VideoType.DIGITAL_HD), "Drohnenwechsel auf HD");
                if (problem != null) {
                    return finish(ctx, problem);
                }
                VideoConfig module = VideoSettings.get().copy();
                module.creativeFovFromModule = true;
                module.fovCreative = 147;
                VideoSettings.CONFIG.set(module);
                video.overrideTestReceiver(true, VideoSystem.CREATIVE);
                return next(ctx, 5);
            }
            case 5 -> {
                if (waiting(ctx)) {
                    return StepResult.pending();
                }
                String problem = expect(video, VideoType.DIGITAL_HD, FovMath.cameraHorizontalDegrees(VideoType.DIGITAL_HD), "Kreativ-Brille vom Videomodul");
                if (problem == null) {
                    problem = tab(VideoType.DIGITAL_HD, true);
                }
                if (problem != null) {
                    return finish(ctx, problem);
                }
                VideoConfig own = VideoSettings.get().copy();
                own.creativeFovFromModule = false;
                VideoSettings.CONFIG.set(own);
                return next(ctx, 6);
            }
            default -> {
                if (waiting(ctx)) {
                    return StepResult.pending();
                }
                if (Math.abs(video.horizontalFov() - 147.0) > 1.0E-6) {
                    return finish(ctx, "Kreativ-Brille mit eigenem Sichtfeld: " + video.horizontalFov() + "° statt 147°");
                }
                String problem = tab(VideoType.DIGITAL_HD, true);
                if (problem != null) {
                    return finish(ctx, problem);
                }
                ctx.log(String.format(Locale.ROOT, "Sichtfeld: Race analog %.0f°, Lite digital %.0f°, HD %.0f°, Kreativ eigenes 147°",
                        FovMath.cameraHorizontalDegrees(VideoType.ANALOG_RACE), FovMath.cameraHorizontalDegrees(VideoType.DIGITAL_LITE),
                        FovMath.cameraHorizontalDegrees(VideoType.DIGITAL_HD)));
                return finish(ctx, null);
            }
        }
    }

    private static StepResult next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static boolean waiting(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0) < SETTLE_TICKS;
    }

    private static @Nullable String expect(VideoState video, VideoType camera, double degrees, String what) {
        if (video.camera() != camera) {
            return what + ": Kamera " + video.camera() + " statt " + camera.key();
        }
        if (Math.abs(video.horizontalFov() - degrees) > 1.0E-6) {
            return what + ": Sichtfeld " + video.horizontalFov() + "° statt " + degrees + "°";
        }
        if (Math.abs(VideoSettings.get().horizontalFov(video.receiver(), camera) - degrees) > 1.0E-6) {
            return what + ": Einstellung liefert " + VideoSettings.get().horizontalFov(video.receiver(), camera) + "°";
        }
        return null;
    }

    private static @Nullable String tab(VideoType camera, boolean creative) {
        List<AbstractWidget> widgets = new ArrayList<>();
        ConfigTabHost host = new ConfigTabHost() {
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
        new VideoConfigTab().attach(host, 0, 0, 480, 280);
        String degrees = Math.round(FovMath.cameraHorizontalDegrees(camera)) + "°";
        List<InfoField> infos = widgets.stream().filter(InfoField.class::isInstance).map(InfoField.class::cast).toList();
        List<StepSlider> sliders = widgets.stream().filter(StepSlider.class::isInstance).map(StepSlider.class::cast).toList();
        if (!creative) {
            if (infos.size() != 1 || !infos.getFirst().value().getString().equals(degrees)) {
                return "Video-Tab zeigt nicht das Sichtfeld des Videomoduls " + degrees + ": " + infos.stream().map(i -> i.value().getString()).toList();
            }
            boolean fovSlider = sliders.stream().anyMatch(s -> s.getMessage().getString().contains(degrees) && s.getMessage().getString().contains(":"));
            return fovSlider ? "Video-Tab hat für die " + camera.system + "-Brille noch einen Sichtfeld-Regler" : null;
        }
        String module = Component.translatable("propwash.video.config.fov_source.module").getString();
        String own = Component.translatable("propwash.video.config.fov_source.own").getString();
        boolean source = widgets.stream().anyMatch(w -> w instanceof CycleButton<?> && (w.getMessage().getString().contains(module)
                || w.getMessage().getString().contains(own)));
        boolean slider = !sliders.isEmpty();
        if (!infos.isEmpty() || !source || !slider) {
            return "Video-Tab der Kreativ-Brille ohne Umschalter/Regler: " + infos.size() + " Info, Umschalter " + source + ", Regler " + slider;
        }
        StepSlider fov = sliders.getFirst();
        boolean fromModule = VideoSettings.get().creativeFovFromModule;
        if (fov.active == fromModule) {
            return "Sichtfeld-Regler der Kreativ-Brille " + (fov.active ? "aktiv" : "gesperrt") + " bei „vom Videomodul“ = " + fromModule;
        }
        return null;
    }

    private static StepResult finish(SelfTest.StepContext ctx, @Nullable String problem) {
        VideoState.INSTANCE.attachTestSource(null);
        VideoState.INSTANCE.overrideTestReceiver(false, null);
        Object saved = ctx.state().get(SAVED);
        if (saved instanceof VideoConfig config) {
            VideoSettings.CONFIG.set(config);
        }
        Object ids = ctx.state().get(IDS);
        if (ids instanceof List<?> list) {
            ctx.onServer(server -> discard(server, list));
        }
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static List<Integer> spawn(MinecraftServer server, List<DroneBuild> builds) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < builds.size(); i++) {
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
            stack.set(CoreComponents.DRONE_BUILD.get(), builds.get(i));
            drone.loadFromStack(stack);
            Vec3 pos = player.position().add(4.0 + 2.0 * i, 0.0, 4.0);
            drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            level.addFreshEntity(drone);
            ids.add(drone.getId());
        }
        return ids;
    }

    private static void discard(MinecraftServer server, List<?> ids) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Object id : ids) {
                if (id instanceof Integer value && level.getEntity(value) instanceof QuadcopterEntity drone) {
                    drone.discard();
                }
            }
        }
    }
}
