package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class CovBatteryPlugIrisShowcase {
    static final String PREFIX = "bildschirmfotos: Iris Akku gesteckt – ";
    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String CONFIG = "net.irisshaders.iris.api.v0.IrisApiConfig";
    private static final String SWITCHED = "cov_iris_switched";
    private static final String FUTURE = "cov_plug_shot_future";
    private static final int SHADER_SETTLE_TICKS = 40;
    private static final long MORNING = 1500L;
    private static final int FOV = 30;
    private static final int STAND = 2;
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicLong SAVED_TIME = new AtomicLong(Long.MIN_VALUE);
    private static boolean startedWithShaders;
    private static int savedFov = -1;

    private CovBatteryPlugIrisShowcase() {
    }

    public static void register() {
        if (!Screenshots.enabled() || !(Services.PLATFORM.isModLoaded("iris") || Services.PLATFORM.isModLoaded("oculus"))) {
            return;
        }
        SelfTest.clientStep(PREFIX + "Platz vorbereiten, Morgen, Shaderpack an", 900, ctx -> prepare(ctx, true));
        Screenshots.step(PREFIX + "Nahaufnahme des Paars am 5-Zöller mit Shaderpack", "cov_battery_plug_iris_freestyle_5_shader", 40,
                new RearShot(DroneBuild.FREESTYLE, 26.0, 0.32F));
        Screenshots.step(PREFIX + "Nahaufnahme des Paars am Racer mit Shaderpack", "cov_battery_plug_iris_race_5_shader", 40,
                new RearShot(DroneBuild.RACER, 14.0, 0.30F));
        SelfTest.clientStep(PREFIX + "Shaderpack aus", 600, ctx -> shaders(ctx, false));
        Screenshots.step(PREFIX + "Nahaufnahme des Paars am 5-Zöller ohne Shaderpack", "cov_battery_plug_iris_freestyle_5_vanilla", 40,
                new RearShot(DroneBuild.FREESTYLE, 26.0, 0.32F));
        Screenshots.step(PREFIX + "Nahaufnahme des Paars am Racer ohne Shaderpack", "cov_battery_plug_iris_race_5_vanilla", 40,
                new RearShot(DroneBuild.RACER, 14.0, 0.30F));
        SelfTest.clientStep(PREFIX + "aufräumen", 600, CovBatteryPlugIrisShowcase::cleanup);
    }

    private static boolean shadersEnabled() {
        try {
            Object api = Class.forName(API).getMethod("getInstance").invoke(null);
            Object config = Class.forName(API).getMethod("getConfig").invoke(api);
            return Boolean.TRUE.equals(Class.forName(CONFIG).getMethod("areShadersEnabled").invoke(config));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean packInUse() {
        try {
            Object api = Class.forName(API).getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(Class.forName(API).getMethod("isShaderPackInUse").invoke(api));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static void enable(boolean enabled) throws ReflectiveOperationException {
        Object api = Class.forName(API).getMethod("getInstance").invoke(null);
        Object config = Class.forName(API).getMethod("getConfig").invoke(api);
        Class.forName(CONFIG).getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, enabled);
    }

    private static StepResult prepare(SelfTest.StepContext ctx, boolean shaders) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("cov_iris_place")) {
            startedWithShaders = shadersEnabled();
            savedFov = Minecraft.getInstance().options.fov().get();
            StepResult placed = CovBatteryPlugShowcase.prepareStep(ctx);
            if (placed.status() != StepResult.Status.PASS) {
                return placed;
            }
            state.put("cov_iris_place", Boolean.TRUE);
            ctx.onServer(server -> {
                long now = totalTicks(server);
                SAVED_TIME.compareAndSet(Long.MIN_VALUE, now);
                setTicks(server, now - Math.floorMod(now, 24000L) + 24000L + MORNING);
            });
            return StepResult.pending();
        }
        return shaders(ctx, shaders);
    }

    private static StepResult shaders(SelfTest.StepContext ctx, boolean enabled) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(SWITCHED)) {
            if (shadersEnabled() != enabled) {
                try {
                    enable(enabled);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return StepResult.fail("Iris-Shader nicht umschaltbar: " + e);
                }
            }
            state.put(SWITCHED, ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) state.get(SWITCHED) < SHADER_SETTLE_TICKS) {
            return StepResult.pending();
        }
        if (enabled && !packInUse()) {
            return StepResult.fail("kein Shaderpack aktiv");
        }
        if (!enabled && packInUse()) {
            return StepResult.fail("Shaderpack bleibt aktiv");
        }
        ctx.log("Iris: Shaderpack " + (enabled ? "an" : "aus"));
        return StepResult.pass();
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("cov_iris_clean")) {
            state.put("cov_iris_clean", Boolean.TRUE);
            ctx.onServer(server -> {
                discard(server);
                long time = SAVED_TIME.getAndSet(Long.MIN_VALUE);
                if (time != Long.MIN_VALUE) {
                    setTicks(server, time);
                }
            });
            if (startedWithShaders != shadersEnabled()) {
                try {
                    enable(startedWithShaders);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return StepResult.fail("Iris-Shader nicht zurückgestellt: " + e);
                }
            }
            if (savedFov > 0) {
                Minecraft.getInstance().options.fov().set(savedFov);
            }
            return StepResult.pending();
        }
        return CovBatteryPlugShowcase.cleanupStep(ctx);
    }

    private static long totalTicks(MinecraftServer server) {
        Optional<Holder<WorldClock>> clock = server.overworld().dimensionType().defaultClock();
        return clock.isEmpty() ? 0L : server.clockManager().getInstance(clock.get()).totalTicks();
    }

    private static void setTicks(MinecraftServer server, long ticks) {
        Optional<Holder<WorldClock>> clock = server.overworld().dimensionType().defaultClock();
        clock.ifPresent(holder -> server.clockManager().setTotalTicks(holder, ticks));
    }

    private static void discard(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        if (id >= 0 && server.overworld().getEntity(id) instanceof QuadcopterEntity drone) {
            drone.discard();
        }
    }

    private static final class RearShot implements Screenshots.Scene {
        private final DroneBuild build;
        private final double pitch;
        private final float distance;
        private Vec3 aim = Vec3.ZERO;

        private RearShot(DroneBuild build, double pitch, float distance) {
            this.build = build;
            this.pitch = pitch;
            this.distance = distance;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                BlockPos stand = CovBatteryPlugShowcase.base();
                Vec3 feet = Vec3.atBottomCenterOf(stand.above(STAND));
                this.aim = BatteryPlugShowcase.plugCenter(this.build, feet, 0.0F);
                Vec3 eye = CovBatteryPlugShowcase.eyeFor(this.build, feet, 0.0F, this.pitch, this.distance);
                state.put(FUTURE, ctx.onServer(server -> {
                    discard(server);
                    ServerPlayer player = CovBatteryPlugShowcase.player(server);
                    ServerLevel level = player.level();
                    player.setGameMode(GameType.SPECTATOR);
                    CovBatteryPlugShowcase.place(player, eye.x, eye.y - player.getEyeHeight(), eye.z);
                    QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                    if (drone == null) {
                        throw new IllegalStateException("Drohne nicht erzeugt");
                    }
                    ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
                    stack.set(CoreComponents.DRONE_BUILD.get(), this.build);
                    drone.loadFromStack(stack);
                    drone.snapTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
                    level.addFreshEntity(drone);
                    drone.resetTo(feet, CovBatteryPlugShowcase.rotation(0.0F));
                    DRONE.set(drone.getId());
                }));
                return StepResult.pending();
            }
            StepResult server = CovBatteryPlugShowcase.awaitServer(ctx, this.build.frame().key());
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.level == null || !(mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone) || drone.build().frame() != this.build.frame()) {
                return ctx.tick() > 200 ? StepResult.fail(this.build.frame().key() + ": Drohne kam nicht beim Client an") : StepResult.pending();
            }
            mc.options.fov().set(FOV);
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                CovBatteryPlugShowcase.look(mc.player, this.aim);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            ctx.onServer(CovBatteryPlugIrisShowcase::discard);
        }
    }
}
