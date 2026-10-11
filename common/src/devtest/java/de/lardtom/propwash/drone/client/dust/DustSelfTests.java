package de.lardtom.propwash.drone.client.dust;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.render.DroneRenderConfigTab;
import de.lardtom.propwash.drone.dust.DustModel;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class DustSelfTests {
    private static final String FUTURE = "future";
    private static final String ORIGIN = "origin";
    private static final String DRONE = "drone";
    private static final String GROUND_Y = "groundY";
    private static final String PHASE = "phase";
    private static final String MARK = "mark";
    private static final String BASELINE = "baseline";
    private static final String SPRAY_BASELINE = "sprayBaseline";
    private static final String RESTORE = "restore";
    private static final String BLOCKS = "blocks";
    private static final String MOVE = "move";
    private static final String POOL = "pool";
    private static final double HOVER_HEIGHT = 0.4;
    private static final double HIGH_ALTITUDE = 12.0;
    private static final float MOTOR = 0.45F;
    private static final int PROBE_GIVE_UP = 150;

    private record Restore(boolean enabled, float intensity, ParticleStatus particles) {
    }

    private DustSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("dust: Untergrund erkennen", 200, DustSelfTests::surfaceStep);
        SelfTest.clientStep("dust: Staub beim Schweben, Gischt über Wasser, nichts in der Höhe", 1200, DustSelfTests::hoverStep);
        SelfTest.clientStep("dust: Einstellungen im Tab Darstellung", 20, DustSelfTests::tabStep);
    }

    private static StepResult surfaceStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            AtomicReference<BlockPos> origin = new AtomicReference<>();
            Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
            state.put(ORIGIN, origin);
            state.put(BLOCKS, saved);
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + 5;
                int z = player.getBlockZ() - 5;
                BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
                for (int dx = 0; dx <= 6; dx += 2) {
                    for (int dy = 1; dy <= 3; dy++) {
                        place(level, saved, ground.offset(dx, dy, 0), Blocks.AIR.defaultBlockState());
                    }
                }
                place(level, saved, ground, Blocks.WATER.defaultBlockState());
                place(level, saved, ground.east(2), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                place(level, saved, ground.east(4), Blocks.SAND.defaultBlockState());
                place(level, saved, ground.east(6), Blocks.GRASS_BLOCK.defaultBlockState());
                origin.set(ground);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Blöcke setzen fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        @SuppressWarnings("unchecked")
        AtomicReference<BlockPos> origin = (AtomicReference<BlockPos>) state.get(ORIGIN);
        ClientLevel level = Minecraft.getInstance().level;
        if (!future.isDone() || origin.get() == null || level == null) {
            return StepResult.pending();
        }
        BlockPos ground = origin.get();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double startY = ground.getY() + 1.6;
        DustSurface.Ground water = DustSurface.probe(level, ground.getX() + 0.5, startY, ground.getZ() + 0.5, 2.0, cursor);
        DustSurface.Ground leaves = DustSurface.probe(level, ground.getX() + 2.5, startY, ground.getZ() + 0.5, 2.0, cursor);
        DustSurface.Ground sand = DustSurface.probe(level, ground.getX() + 4.5, startY, ground.getZ() + 0.5, 2.0, cursor);
        DustSurface.Ground grass = DustSurface.probe(level, ground.getX() + 6.5, startY, ground.getZ() + 0.5, 2.0, cursor);
        DustSurface.Ground tooHigh = DustSurface.probe(level, ground.getX() + 6.5, startY + 3.0, ground.getZ() + 0.5, 2.0, cursor);
        double top = ground.getY() + 1.0;
        String problem = null;
        if (water.surface() != DustModel.Surface.WATER || water.top() < top - 0.5 || water.top() > top + 1.0E-6) {
            problem = "Wasser erkannt als " + water;
        } else if (leaves.surface() != DustModel.Surface.NONE) {
            problem = "Laub erkannt als " + leaves;
        } else if (sand.surface() != DustModel.Surface.SOLID || sand.material() != DustModel.Material.LOOSE || Math.abs(sand.top() - top) > 1.0E-6) {
            problem = "Sand erkannt als " + sand;
        } else if (grass.surface() != DustModel.Surface.SOLID || grass.material() != DustModel.Material.COVERED || Math.abs(grass.top() - top) > 1.0E-6) {
            problem = "Gras erkannt als " + grass;
        } else if (sand.color() == grass.color()) {
            problem = "Sand und Gras haben dieselbe Staubfarbe " + Integer.toHexString(sand.color());
        } else if (tooHigh.surface() != DustModel.Surface.NONE) {
            problem = "Boden außerhalb der Suchtiefe gefunden: " + tooHigh;
        }
        if (problem != null && ctx.tick() < PROBE_GIVE_UP) {
            return StepResult.pending();
        }
        restoreBlocks(ctx);
        if (problem != null) {
            return StepResult.fail(problem);
        }
        ctx.log("Staubfarben: Sand #" + Integer.toHexString(sand.color()) + ", Gras #" + Integer.toHexString(grass.color())
                + ", Wasseroberfläche " + String.format(Locale.ROOT, "%.3f", water.top() - ground.getY()));
        return StepResult.pass();
    }

    private static StepResult hoverStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            DustConfig config = DustConfig.get();
            state.put(RESTORE, new Restore(config.enabled, config.intensity, minecraft.options.particles().get()));
            config.enabled = true;
            config.intensity = DustModel.MAX_INTENSITY;
            minecraft.options.particles().set(ParticleStatus.ALL);
            AtomicInteger id = new AtomicInteger(-1);
            AtomicReference<Double> groundY = new AtomicReference<>();
            state.put(DRONE, id);
            state.put(GROUND_Y, groundY);
            state.put(BLOCKS, new LinkedHashMap<BlockPos, BlockState>());
            state.put(PHASE, 0);
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                if (drone == null) {
                    throw new IllegalStateException("EntityType.create lieferte null");
                }
                int x = player.getBlockX() + 3;
                int z = player.getBlockZ() + 3;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                drone.snapTo(x + 0.5, top + HOVER_HEIGHT, z + 0.5, 0.0F, 0.0F);
                drone.setBuild(DroneBuild.RACER);
                drone.setNoGravity(true);
                if (!level.addFreshEntity(drone)) {
                    throw new IllegalStateException("addFreshEntity fehlgeschlagen");
                }
                groundY.set((double) top);
                id.set(drone.getId());
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> spawn = (CompletableFuture<?>) state.get(FUTURE);
        if (spawn.isCompletedExceptionally()) {
            return finish(ctx, StepResult.fail("Spawn fehlgeschlagen: " + spawn.exceptionNow().getMessage()));
        }
        int id = ((AtomicInteger) state.get(DRONE)).get();
        if (!spawn.isDone() || id < 0 || minecraft.level == null) {
            return StepResult.pending();
        }
        if (!(minecraft.level.getEntity(id) instanceof QuadcopterEntity drone)) {
            return ctx.tick() > 200 ? finish(ctx, StepResult.fail("Drohne kam nicht beim Client an")) : StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        double groundY = ((AtomicReference<Double>) state.get(GROUND_Y)).get();
        drone.setArmed(true);
        drone.setMotorOutputs(MOTOR, MOTOR, MOTOR, MOTOR);
        if (DustEffects.live() > DustModel.Detail.ALL.cap) {
            return finish(ctx, StepResult.fail("mehr Staubpartikel als erlaubt: " + DustEffects.live()));
        }
        int phase = (int) state.get(PHASE);
        long spawned = DustEffects.spawnedTotal();
        switch (phase) {
            case 0 -> {
                long baseline = (long) state.computeIfAbsent(BASELINE, k -> spawned);
                if (spawned - baseline >= 5 && DustEffects.liveOf(WashParticle.Style.DUST) > 0) {
                    if (DustEffects.untextured() > 0) {
                        return finish(ctx, StepResult.fail("Staubpartikel ohne Textur: " + DustEffects.untextured()));
                    }
                    ctx.log("Staub beim Schweben: " + (spawned - baseline) + " Partikel, aktiv " + DustEffects.live() + ", " + DustEffects.lastWash(drone));
                    minecraft.options.particles().set(ParticleStatus.MINIMAL);
                    next(state, 1, ctx.tick(), spawned);
                } else if (ctx.tick() > 260) {
                    return finish(ctx, StepResult.fail("kein Staub beim Schweben in " + HOVER_HEIGHT + " Blöcken: " + DustEffects.lastWash(drone)
                            + " Boden=" + DustEffects.lastGround(drone) + " y=" + (drone.getY() - groundY) + " armed=" + drone.isArmed()
                            + " Motor=" + drone.motorOutput(0)));
                }
            }
            case 1 -> {
                if (spawned != (long) state.get(BASELINE)) {
                    return finish(ctx, StepResult.fail("Staub trotz Partikel-Einstellung „Minimal“"));
                }
                if (ctx.tick() - (int) state.get(MARK) >= 20) {
                    minecraft.options.particles().set(ParticleStatus.ALL);
                    double x = drone.getX();
                    double z = drone.getZ();
                    state.put(MOVE, ctx.onServer(server -> serverDrone(server, id).snapTo(x, groundY + HIGH_ALTITUDE, z, 0.0F, 0.0F)));
                    next(state, 2, ctx.tick(), spawned);
                }
            }
            case 2 -> {
                if (drone.getY() >= groundY + HIGH_ALTITUDE - 0.5) {
                    next(state, 3, ctx.tick(), spawned);
                } else if (ctx.tick() - (int) state.get(MARK) > 120) {
                    return finish(ctx, StepResult.fail("Drohne kam nicht in der Höhe an: y=" + (drone.getY() - groundY)));
                }
            }
            case 3 -> {
                if (spawned != (long) state.get(BASELINE)) {
                    return finish(ctx, StepResult.fail("Staub in " + HIGH_ALTITUDE + " Blöcken Höhe: " + DustEffects.lastWash(drone)));
                }
                if (ctx.tick() - (int) state.get(MARK) >= 30) {
                    if (DustEffects.lastWash(drone).active()) {
                        return finish(ctx, StepResult.fail("Staubquelle in der Höhe noch aktiv: " + DustEffects.lastWash(drone)));
                    }
                    @SuppressWarnings("unchecked")
                    Map<BlockPos, BlockState> saved = (Map<BlockPos, BlockState>) state.get(BLOCKS);
                    double x = drone.getX();
                    double z = drone.getZ();
                    state.put(MOVE, ctx.onServer(server -> {
                        ServerLevel level = player(server).level();
                        BlockPos center = BlockPos.containing(x, groundY - 1.0, z);
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                place(level, saved, center.offset(dx, 0, dz), Blocks.WATER.defaultBlockState());
                            }
                        }
                        serverDrone(server, id).snapTo(x, groundY + HOVER_HEIGHT, z, 0.0F, 0.0F);
                    }));
                    state.put(POOL, BlockPos.containing(x, groundY - 1.0, z));
                    next(state, 4, ctx.tick(), spawned);
                }
            }
            case 4 -> {
                CompletableFuture<?> move = (CompletableFuture<?>) state.get(MOVE);
                if (move.isCompletedExceptionally()) {
                    return finish(ctx, StepResult.fail("Wasserfläche anlegen fehlgeschlagen: " + move.exceptionNow().getMessage()));
                }
                BlockPos pool = (BlockPos) state.get(POOL);
                if (move.isDone() && drone.getY() < groundY + 1.0 && minecraft.level.getFluidState(pool).is(FluidTags.WATER)) {
                    state.put(SPRAY_BASELINE, DustEffects.spawnedSpray());
                    next(state, 5, ctx.tick(), spawned);
                } else if (ctx.tick() - (int) state.get(MARK) > 120) {
                    return finish(ctx, StepResult.fail("Wasserfläche oder Drohne kam nicht beim Client an: y=" + (drone.getY() - groundY)));
                }
            }
            case 5 -> {
                long spray = DustEffects.spawnedSpray() - (long) state.get(SPRAY_BASELINE);
                long dust = spawned - (long) state.get(BASELINE) - spray;
                if (spray >= 3) {
                    if (dust > 0) {
                        return finish(ctx, StepResult.fail("über Wasser " + dust + " Staubpartikel statt nur Gischt"));
                    }
                    ctx.log("Gischt über Wasser: " + spray + " Partikel, " + DustEffects.lastWash(drone));
                    return finish(ctx, StepResult.pass());
                }
                if (ctx.tick() - (int) state.get(MARK) > 200) {
                    return finish(ctx, StepResult.fail("keine Gischt über Wasser: Gischt=" + spray + " Staub=" + dust + " "
                            + DustEffects.lastWash(drone) + " Boden=" + DustEffects.lastGround(drone) + " y=" + (drone.getY() - groundY)));
                }
            }
            default -> {
                return finish(ctx, StepResult.fail("unbekannte Phase " + phase));
            }
        }
        return StepResult.pending();
    }

    private static StepResult tabStep(SelfTest.StepContext ctx) {
        DroneRenderConfigTab tab = null;
        for (ConfigTab candidate : ConfigTabs.create()) {
            if (candidate instanceof DroneRenderConfigTab found) {
                tab = found;
            }
        }
        if (tab == null) {
            return StepResult.fail("Tab Darstellung nicht angemeldet");
        }
        DustConfig before = DustConfig.get().copy();
        tab.attach(new Host(), 0, 0, 320, 240);
        float changed = before.intensity >= 0.8F ? 0.2F : 0.9F;
        tab.dustDraft().intensity = changed;
        tab.dustDraft().enabled = !before.enabled;
        if (DustConfig.get().intensity != before.intensity || DustConfig.get().enabled != before.enabled) {
            return StepResult.fail("Tab verändert die aktive Staub-Einstellung vor dem Speichern");
        }
        try {
            tab.save();
            if (DustConfig.get().intensity != changed || DustConfig.get().enabled == before.enabled) {
                return StepResult.fail("Speichern übernimmt die Staub-Einstellung nicht: " + DustConfig.get().enabled + "/" + DustConfig.get().intensity);
            }
            tab.resetDefaults();
            if (!tab.dustDraft().enabled || tab.dustDraft().intensity != DustModel.DEFAULT_INTENSITY) {
                return StepResult.fail("Standardwerte falsch: " + tab.dustDraft().enabled + "/" + tab.dustDraft().intensity);
            }
        } finally {
            DustConfig.CONFIG.set(before);
        }
        return StepResult.pass();
    }

    private static void next(Map<String, Object> state, int phase, int tick, long spawned) {
        state.put(PHASE, phase);
        state.put(MARK, tick);
        state.put(BASELINE, spawned);
    }

    private static StepResult finish(SelfTest.StepContext ctx, StepResult result) {
        Map<String, Object> state = ctx.state();
        if (state.get(RESTORE) instanceof Restore restore) {
            DustConfig config = DustConfig.get();
            config.enabled = restore.enabled();
            config.intensity = restore.intensity();
            Minecraft.getInstance().options.particles().set(restore.particles());
        }
        int id = state.get(DRONE) instanceof AtomicInteger value ? value.get() : -1;
        ctx.onServer(server -> {
            if (id >= 0) {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) {
                        entity.discard();
                    }
                }
            }
        });
        restoreBlocks(ctx);
        return result;
    }

    private static void restoreBlocks(SelfTest.StepContext ctx) {
        @SuppressWarnings("unchecked")
        Map<BlockPos, BlockState> saved = (Map<BlockPos, BlockState>) ctx.state().get(BLOCKS);
        if (saved == null) {
            return;
        }
        ctx.onServer(server -> {
            ServerLevel level = player(server).level();
            List<Map.Entry<BlockPos, BlockState>> entries = List.copyOf(saved.entrySet());
            for (int i = entries.size() - 1; i >= 0; i--) {
                level.setBlock(entries.get(i).getKey(), entries.get(i).getValue(), Block.UPDATE_ALL);
            }
            saved.clear();
        });
    }

    private static void place(ServerLevel level, Map<BlockPos, BlockState> saved, BlockPos pos, BlockState state) {
        BlockPos key = pos.immutable();
        saved.putIfAbsent(key, level.getBlockState(key));
        level.setBlock(key, state, Block.UPDATE_ALL);
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static QuadcopterEntity serverDrone(MinecraftServer server, int id) {
        if (!(player(server).level().getEntity(id) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne auf dem Server verschwunden");
        }
        return drone;
    }

    private static final class Host implements ConfigTabHost {
        @Override
        public <T extends AbstractWidget> T addWidget(T widget) {
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
    }
}
