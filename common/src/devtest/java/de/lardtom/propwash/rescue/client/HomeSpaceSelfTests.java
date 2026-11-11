package de.lardtom.propwash.rescue.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.SoftGround;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.rescue.HomeMode;
import de.lardtom.propwash.rescue.HomePoint;
import de.lardtom.propwash.rescue.HomePointCodecs;
import de.lardtom.propwash.rescue.HomeResetRules;
import de.lardtom.propwash.rescue.HomeResets;
import de.lardtom.propwash.rescue.RescueSettings;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class HomeSpaceSelfTests {
    private static final String PREFIX = "reset: freier Platz – ";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final int HALF = 5;
    private static final int HEIGHT = 7;
    private static final int FLOAT = 3;
    private static final double TOLERANCE = 0.02;
    private static final double FALL_TOLERANCE = 0.05;
    private static final double NEARBY = 3.5;
    private static final AtomicLong ASKED = new AtomicLong();
    private static final AtomicReference<UUID> DRONE = new AtomicReference<>();
    private static final AtomicReference<BlockPos> SITE = new AtomicReference<>();
    private static final AtomicReference<HomeMode> MODE = new AtomicReference<>();
    private static final AtomicReference<HomePoint> HOME = new AtomicReference<>();
    private static final AtomicReference<Vec3> SPOT = new AtomicReference<>();
    private static final AtomicReference<String> OUTCOME = new AtomicReference<>();
    private static final AtomicReference<String> NOTE = new AtomicReference<>();
    private static final AtomicReference<Double> TOP = new AtomicReference<>();
    private static final Map<BlockPos, BlockState> SAVED = new HashMap<>();

    private HomeSpaceSelfTests() {
    }

    static void register() {
        SelfTest.clientStep(PREFIX + "Vorbereitung (Drohne im Sichtflug, Bauplatz)", 400, HomeSpaceSelfTests::setupStep);
        for (Support support : Support.values()) {
            SelfTest.clientStep(PREFIX + support.label, 700, ctx -> supportStep(ctx, support));
        }
        SelfTest.clientStep(PREFIX + "Homepoint unter einem vollen Block: Reset genau dorthin, nicht durch den Boden", 700,
                HomeSpaceSelfTests::underStep);
        SelfTest.clientStep(PREFIX + "Block auf dem Homepoint: FPV-Bild im Block überdeckt, Reset mit Hinweis daneben statt durch die Decke",
                1000, HomeSpaceSelfTests::onStep);
        SelfTest.clientStep(PREFIX + "Schlamm im FPV: Reset auf die Oberfläche, Bild nicht überdeckt", 900,
                ctx -> softFpvStep(ctx, Support.MUD));
        SelfTest.clientStep(PREFIX + "Honigblock im FPV: Reset auf die Oberfläche, Kamera nicht im Honig", 900,
                ctx -> softFpvStep(ctx, Support.HONEY));
        SelfTest.clientStep(PREFIX + "Block auf dem Homepoint unter freiem Himmel: nächste freie Stelle mit Hinweis", 600,
                HomeSpaceSelfTests::openStep);
        SelfTest.clientStep(PREFIX + "Homepoint ganz zugebaut: Meldung, Drohne bleibt, wo sie ist", 500, HomeSpaceSelfTests::buriedStep);
        SelfTest.clientStep(PREFIX + "Aufräumen", 300, HomeSpaceSelfTests::cleanupStep);
    }

    private enum Support {
        SLAB("Stufe schwebend: Homepoint auf der Oberkante, Reset fällt nicht durch", 0.5, false),
        STAIRS("Treppe schwebend (untere Stufe): Homepoint auf der Oberkante, Reset fällt nicht durch", 0.78, false),
        TABLE("Zaubertisch schwebend: Homepoint auf der Oberkante, Reset fällt nicht durch", 0.5, false),
        CARPET("Teppich auf Stein: Homepoint auf dem Teppich, nicht darin", 0.5, true),
        SNOW("Schneeschicht auf Stein: Homepoint auf dem Schnee, nicht darin", 0.5, true),
        MUD("Schlamm schwebend: Homepoint auf der Oberfläche, auf der die Drohne liegt, Reset nicht in den Schlamm", 0.5, false),
        SOUL_SAND("Seelensand schwebend: Homepoint auf der Oberfläche, auf der die Drohne liegt, Reset nicht in den Seelensand", 0.5,
                false),
        HONEY("Honigblock schwebend: Homepoint auf der Oberfläche, auf der die Drohne liegt, Reset nicht in den Honig", 0.5, false),
        FULL("voller Block schwebend: Homepoint auf der Oberkante, Reset fällt nicht durch", 0.5, false);

        final String label;
        final double dz;
        final boolean onStone;

        Support(String label, double dz, boolean onStone) {
            this.label = label;
            this.dz = dz;
            this.onStone = onStone;
        }

        BlockState state() {
            return switch (this) {
                case SLAB -> Blocks.STONE_SLAB.defaultBlockState();
                case STAIRS -> Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)
                        .setValue(StairBlock.HALF, Half.BOTTOM);
                case TABLE -> Blocks.ENCHANTING_TABLE.defaultBlockState();
                case CARPET -> Blocks.MOSS_CARPET.defaultBlockState();
                case SNOW -> Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3);
                case MUD -> Blocks.MUD.defaultBlockState();
                case SOUL_SAND -> Blocks.SOUL_SAND.defaultBlockState();
                case HONEY -> Blocks.HONEY_BLOCK.defaultBlockState();
                case FULL -> Blocks.STONE.defaultBlockState();
            };
        }
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static void next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
    }

    @SuppressWarnings("unchecked")
    private static List<String> problems(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent("problems", key -> new ArrayList<String>());
    }

    private static @Nullable StepResult once(SelfTest.StepContext ctx, String key, Consumer<MinecraftServer> action) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(key);
        if (future == null) {
            ctx.state().put(key, ctx.onServer(action));
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(key + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static ServerPlayer serverPlayer(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static QuadcopterEntity serverDrone(MinecraftServer server) {
        UUID id = DRONE.get();
        if (id != null && serverPlayer(server).level().getEntity(id) instanceof QuadcopterEntity drone && !drone.isRemoved()) {
            return drone;
        }
        throw new IllegalStateException("Testdrohne fehlt auf dem Server");
    }

    private static @Nullable QuadcopterEntity sessionDrone() {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return session.isActive() && drone != null && drone.getUUID().equals(DRONE.get()) ? drone : null;
    }

    private static BlockPos site() {
        BlockPos site = SITE.get();
        if (site == null) {
            throw new IllegalStateException("kein Bauplatz aus der Vorbereitung");
        }
        return site;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) {
        SAVED.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static void clearSite(ServerLevel level) {
        BlockPos site = site();
        for (int dy = HEIGHT; dy >= -1; dy--) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    BlockPos pos = site.offset(dx, dy, dz);
                    put(level, pos, dy < 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static boolean free(ServerLevel level, QuadcopterEntity drone, Vec3 at) {
        Vec3 from = drone.position();
        AABB box = drone.getBoundingBox().move(at.x - from.x, at.y - from.y, at.z - from.z);
        List<AABB> soft = new ArrayList<>();
        SoftGround.collect(level, box.deflate(1.0E-4), soft);
        return level.noBlockCollision(drone, box) && soft.isEmpty();
    }

    private static double top(ServerLevel level, BlockPos pos, double x, double z, double half) {
        BlockState state = level.getBlockState(pos);
        double best = top(state.getCollisionShape(level, pos), pos, x, z, half, Double.NaN);
        return SoftGround.enabled() && state.is(SoftGround.SURFACE) ? top(state.getShape(level, pos), pos, x, z, half, best) : best;
    }

    private static double top(VoxelShape shape, BlockPos pos, double x, double z, double half, double best) {
        for (AABB box : shape.toAabbs()) {
            AABB at = box.move(pos);
            if (at.maxX > x - half && at.minX < x + half && at.maxZ > z - half && at.minZ < z + half) {
                best = Double.isNaN(best) ? at.maxY : Math.max(best, at.maxY);
            }
        }
        return best;
    }

    private static void place(MinecraftServer server, Vec3 at) {
        QuadcopterEntity drone = serverDrone(server);
        drone.resetTo(at, new Quaternionf().rotationY((float) Math.PI * 0.5F));
    }

    private static String overlay() {
        try {
            java.lang.reflect.Field field = net.minecraft.client.gui.Hud.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            Object message = field.get(Minecraft.getInstance().gui.hud);
            return message instanceof Component component ? component.getString() : "-";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "? " + e;
        }
    }

    private static PlayerRenderState.@Nullable BlockOverlay blockOverlay() {
        return Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.playerRenderState.blockOverlay;
    }

    private static boolean viewBlocked(Level level, Vector3dc at) {
        BlockPos pos = BlockPos.containing(at.x(), at.y(), at.z());
        BlockState state = level.getBlockState(pos);
        return state.isViewBlocking(level, pos, new AABB(at.x() - 0.01, at.y() - 0.01, at.z() - 0.01, at.x() + 0.01, at.y() + 0.01, at.z() + 0.01));
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            RescueSettings cfg = RescueConfigs.get();
            MODE.compareAndSet(null, cfg.homeMode);
            cfg.homeMode = HomeMode.MANUAL;
            RescueController.INSTANCE.clearHome();
            SAVED.clear();
        }
        StepResult wait = once(ctx, "Drohne", server -> {
            ServerPlayer player = serverPlayer(server);
            ServerLevel level = player.level();
            WorldRules.set(server, WorldRuleSpecs.ALLOW_RESET, 1);
            int x = player.getBlockX() - 12;
            int z = player.getBlockZ() - 12;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            SITE.set(new BlockPos(x, ground, z));
            clearSite(level);
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
            stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
            stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
            drone.loadFromStack(stack);
            drone.snapTo(x + 0.5, ground + 0.2, z + 0.5 + HALF - 1, 90.0F, 0.0F);
            if (!level.addFreshEntity(drone)) {
                throw new IllegalStateException("Drohne nicht gespawnt");
            }
            DRONE.set(drone.getUUID());
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.getInventory().setItem(0, remote);
            player.getInventory().setSelectedSlot(0);
            player.connection.send(new ClientboundSetHeldSlotPacket(0));
            DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
            if (result != DronePiloting.StartResult.STARTED) {
                throw new IllegalStateException("Start verweigert: " + result);
            }
            NOTE.set("Bauplatz " + SITE.get().toShortString() + ", Drohne " + number(drone.getBbWidth()) + " × " + number(drone.getBbHeight()));
        });
        if (wait != null) {
            return wait;
        }
        if (sessionDrone() == null) {
            return ctx.tick() > 200 ? StepResult.fail("keine Sitzung am Client") : StepResult.pending();
        }
        PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        ctx.log(NOTE.get());
        return StepResult.pass();
    }

    private static @Nullable StepResult settleAndSetHome(SelfTest.StepContext ctx, int first, String label) {
        QuadcopterEntity client = sessionDrone();
        if (client == null) {
            return StepResult.fail("Sitzung verloren (" + label + ")");
        }
        int phase = phase(ctx);
        if (phase == first) {
            if (since(ctx) < 70) {
                return StepResult.pending();
            }
            ctx.state().put("foot", client.getY());
            RescueController.INSTANCE.requestSetHome();
            next(ctx, first + 1);
            return StepResult.pending();
        }
        if (phase == first + 1) {
            String key = "Homepoint " + (since(ctx) / 5);
            StepResult wait = once(ctx, key, server -> {
                QuadcopterEntity drone = serverDrone(server);
                HomePoint home = drone.homePoint();
                if (home != null && home.source() == HomePoint.Source.MANUAL && home.gameTime() > ASKED.get()) {
                    HOME.set(home);
                    ServerLevel level = (ServerLevel) drone.level();
                    NOTE.set(free(level, drone, new Vec3(home.x(), home.y(), home.z())) ? "frei" : "im Block");
                }
            });
            if (wait != null) {
                return wait;
            }
            HomePoint home = HOME.get();
            if (home == null) {
                return since(ctx) > 80 ? StepResult.fail("kein Homepoint nach „Homepoint setzen“ (" + label + ")") : StepResult.pending();
            }
            double top = TOP.get();
            double foot = (double) ctx.state().get("foot");
            ctx.log(label + ": Oberkante " + number(top) + ", Drohne ruht bei " + number(foot) + " (ragt " + number(Math.max(0.0, top - foot))
                    + " hinein, " + number(Math.max(0.0, foot - top)) + " darüber), Homepoint y " + number(home.y()) + " (" + NOTE.get() + ")");
            if (Math.abs(home.y() - top) > TOLERANCE) {
                problems(ctx).add("Homepoint y " + number(home.y()) + " statt Oberkante " + number(top));
            }
            if (!"frei".equals(NOTE.get())) {
                problems(ctx).add("Homepoint liegt im Block");
            }
            next(ctx, first + 2);
            return StepResult.pending();
        }
        return null;
    }

    private static @Nullable StepResult resetAndCheck(SelfTest.StepContext ctx, int first, String label, Vec3 away, boolean expectNearby,
                                                      double maxRise) {
        int phase = phase(ctx);
        HomePoint home = HOME.get();
        if (home == null) {
            return StepResult.fail("kein Homepoint");
        }
        double top = TOP.get();
        if (phase == first) {
            StepResult wait = once(ctx, "weg " + first, server -> place(server, away));
            if (wait != null) {
                return wait;
            }
            if (since(ctx) < HomeResetRules.COOLDOWN_TICKS + 10) {
                return StepResult.pending();
            }
            ctx.state().put("gesendet", HomeResetInput.sent());
            KeyMapping.click(InputConstants.getKey(RescueKeys.RESET.saveString()));
            next(ctx, first + 1);
            return StepResult.pending();
        }
        if (phase == first + 1) {
            if (HomeResetInput.sent() <= (int) ctx.state().get("gesendet")) {
                return since(ctx) > 20 ? StepResult.fail("Reset-Taste sendet keine Anfrage") : StepResult.pending();
            }
            if (since(ctx) < 10) {
                return StepResult.pending();
            }
            StepResult reset = once(ctx, "Reset " + first, server -> {
                QuadcopterEntity drone = serverDrone(server);
                SPOT.set(drone.position());
                NOTE.set(free((ServerLevel) drone.level(), drone, drone.position()) ? "frei" : "im Block");
            });
            if (reset != null) {
                return reset;
            }
            String shown = overlay();
            String outcome = shown.equals(Component.translatable("propwash.rescue.reset.home").getString()) ? "HOME"
                    : shown.equals(Component.translatable("propwash.rescue.reset.nearby").getString()) ? "NEARBY" : "„" + shown + "“";
            OUTCOME.set(outcome);
            Vec3 spot = SPOT.get();
            double dx = spot.x - home.x();
            double dz = spot.z - home.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            ctx.log(label + ": Reset " + outcome + " nach " + number(spot.x) + " " + number(spot.y) + " " + number(spot.z) + " (" + NOTE.get()
                    + ", " + number(horizontal) + " m seitlich, " + number(spot.y - top) + " m über der Oberkante)");
            if (!"frei".equals(NOTE.get())) {
                problems(ctx).add("nach dem Reset im Block");
            }
            if (spot.y < top - TOLERANCE) {
                problems(ctx).add("Reset setzt die Drohne " + number(top - spot.y) + " m unter die Oberkante (durchgefallen)");
            }
            if (spot.y > top + maxRise) {
                problems(ctx).add("Reset setzt die Drohne " + number(spot.y - top) + " m über die Oberkante");
            }
            if (expectNearby) {
                if (!"NEARBY".equals(outcome)) {
                    problems(ctx).add("Meldung " + outcome + " statt Hinweis „" + Component.translatable("propwash.rescue.reset.nearby").getString()
                            + "“");
                }
                if (horizontal > NEARBY) {
                    problems(ctx).add("freie Stelle " + number(horizontal) + " m entfernt");
                }
            } else {
                if (!"HOME".equals(outcome)) {
                    problems(ctx).add("Meldung " + outcome + " statt „zurück am Homepoint“");
                }
                if (horizontal > 0.05) {
                    problems(ctx).add("nicht am Homepoint: " + number(horizontal) + " m seitlich");
                }
            }
            next(ctx, first + 2);
            return StepResult.pending();
        }
        if (phase == first + 2) {
            if (since(ctx) < 40) {
                return StepResult.pending();
            }
            QuadcopterEntity client = sessionDrone();
            Vec3 spot = SPOT.get();
            if (client == null) {
                return StepResult.fail("Sitzung nach dem Reset verloren");
            }
            ctx.log(label + ": Client-Drohne nach 2 s bei " + number(client.getY()));
            if (client.getY() < Math.min(top, spot.y) - FALL_TOLERANCE) {
                problems(ctx).add("Client-Drohne fällt nach dem Reset auf " + number(client.getY()));
            }
            next(ctx, first + 3);
            return StepResult.pending();
        }
        return null;
    }

    private static StepResult finish(SelfTest.StepContext ctx) {
        List<String> problems = problems(ctx);
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    private static StepResult supportStep(SelfTest.StepContext ctx, Support support) {
        PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        if (sessionDrone() == null) {
            return StepResult.fail("keine Sitzung");
        }
        StepResult result = supportPhases(ctx, support);
        return result != null ? result : finish(ctx);
    }

    private static StepResult softFpvStep(SelfTest.StepContext ctx, Support support) {
        PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        if (sessionDrone() == null || PilotSession.INSTANCE.mode() != PilotMode.FPV) {
            return StepResult.fail("keine FPV-Sitzung");
        }
        StepResult result = supportPhases(ctx, support);
        if (result != null) {
            return result;
        }
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return StepResult.fail("keine Welt am Client");
        }
        double top = TOP.get();
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        Vector3dc anchor = FpvView.anchor();
        boolean cameraInside = viewBlocked(level, new Vector3d(camera.x, camera.y, camera.z));
        boolean anchorInside = viewBlocked(level, anchor);
        PlayerRenderState.BlockOverlay overlay = blockOverlay();
        ctx.log(support.name() + " im FPV nach dem Reset: Kamera y " + number(camera.y) + " (" + number(camera.y - top)
                + " m über der Oberfläche), Drohne y " + number(anchor.y()) + (anchorInside ? " im Block" : " frei") + ", Überdeckung "
                + (overlay == null ? "keine" : overlay.atlasLocation()));
        if (overlay != null) {
            problems(ctx).add("FPV-Bild nach dem Reset von der Block-Überlagerung bedeckt");
        }
        if (camera.y < top) {
            problems(ctx).add("FPV-Kamera nach dem Reset " + number(top - camera.y) + " m unter der Oberfläche im Block");
        }
        if (cameraInside || anchorInside) {
            problems(ctx).add("Kamera bzw. Drohne steckt nach dem Reset in einem Block, der die Sicht versperrt");
        }
        return finish(ctx);
    }

    private static @Nullable StepResult supportPhases(SelfTest.StepContext ctx, Support support) {
        if (phase(ctx) == 0) {
            StepResult wait = once(ctx, "bauen", server -> {
                ServerLevel level = serverPlayer(server).level();
                QuadcopterEntity drone = serverDrone(server);
                clearSite(level);
                BlockPos base = site().above(FLOAT);
                BlockPos block = base;
                if (support.onStone) {
                    put(level, base, Blocks.STONE.defaultBlockState());
                    block = base.above();
                }
                put(level, block, support.state());
                double x = block.getX() + 0.5;
                double z = block.getZ() + support.dz;
                double top = top(level, block, x, z, drone.getBbWidth() * 0.5);
                if (Double.isNaN(top)) {
                    throw new IllegalStateException("keine Oberkante unter der Drohne");
                }
                TOP.set(top);
                HOME.set(null);
                ASKED.set(level.getGameTime());
                place(server, new Vec3(x, top + 0.3, z));
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        StepResult home = settleAndSetHome(ctx, 1, support.name());
        if (home != null) {
            return home;
        }
        BlockPos site = site();
        Vec3 away = new Vec3(site.getX() + 0.5 + 3.0, site.getY() + 1.0, site.getZ() + 0.5);
        return resetAndCheck(ctx, 3, support.name(), away, false, 0.3);
    }

    private static StepResult underStep(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        if (sessionDrone() == null) {
            return StepResult.fail("keine Sitzung");
        }
        if (phase(ctx) == 0) {
            StepResult wait = once(ctx, "bauen", server -> {
                ServerLevel level = serverPlayer(server).level();
                clearSite(level);
                BlockPos site = site();
                for (int dy = 1; dy <= 3; dy++) {
                    put(level, site.above(dy), Blocks.OAK_PLANKS.defaultBlockState());
                }
                TOP.set((double) site.getY());
                HOME.set(null);
                ASKED.set(level.getGameTime());
                place(server, new Vec3(site.getX() + 0.5, site.getY() + 0.3, site.getZ() + 0.5));
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        StepResult home = settleAndSetHome(ctx, 1, "unter vollem Block");
        if (home != null) {
            return home;
        }
        BlockPos site = site();
        Vec3 away = new Vec3(site.getX() + 0.5 + 3.0, site.getY() + 1.0, site.getZ() + 0.5);
        StepResult reset = resetAndCheck(ctx, 3, "unter vollem Block", away, false, 0.3);
        if (reset != null) {
            return reset;
        }
        QuadcopterEntity client = sessionDrone();
        if (client != null && client.getY() >= site.getY() + 1.0) {
            problems(ctx).add("Drohne über der Decke: " + number(client.getY()));
        }
        return finish(ctx);
    }

    private static void startFpv(MinecraftServer server) {
        ServerPlayer player = serverPlayer(server);
        QuadcopterEntity drone = serverDrone(server);
        if (drone.pilot().isPresent()) {
            DronePiloting.release(drone);
        }
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static StepResult onStep(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        HomePoint home = HOME.get();
        Minecraft mc = Minecraft.getInstance();
        if (home == null) {
            return StepResult.fail("kein Homepoint aus dem Schritt davor");
        }
        BlockPos site = site();
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = once(ctx, "zurück", server -> place(server, new Vec3(home.x(), home.y() + 0.2, home.z())));
                if (wait != null) {
                    return wait;
                }
                StepResult fpv = once(ctx, "FPV", HomeSpaceSelfTests::startFpv);
                if (fpv != null) {
                    return fpv;
                }
                if (sessionDrone() == null || PilotSession.INSTANCE.mode() != PilotMode.FPV) {
                    return since(ctx) > 140 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (since(ctx) < 50) {
                    return StepResult.pending();
                }
                StepResult wait = once(ctx, "Block setzen", server -> put(serverPlayer(server).level(), site, Blocks.OAK_PLANKS.defaultBlockState()));
                if (wait != null) {
                    return wait;
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                Level level = mc.level;
                if (level == null) {
                    return StepResult.fail("keine Welt am Client");
                }
                Vec3 camera = mc.gameRenderer.mainCamera().position();
                boolean anchorInside = viewBlocked(level, FpvView.anchor());
                PlayerRenderState.BlockOverlay overlay = blockOverlay();
                ctx.log("Drohne im Block: Kamera " + number(camera.x) + " " + number(camera.y) + " " + number(camera.z) + ", Drohne im Block "
                        + anchorInside + ", Überdeckung " + (overlay == null ? "keine" : overlay.atlasLocation()));
                if (!anchorInside) {
                    return StepResult.fail("Vorbedingung: Drohne steckt nicht im Block");
                }
                if (overlay == null) {
                    problems(ctx).add("FPV-Kamera im Block ohne Überdeckung (zeigt das Blockinnere)");
                }
                if (Screenshots.enabled() && !ctx.state().containsKey("shot")) {
                    Screenshots.clearOverlays(mc);
                    ctx.state().put("shot", Screenshots.capture("reset_fpv_in_block"));
                }
                next(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                CompletableFuture<?> shot = (CompletableFuture<?>) ctx.state().get("shot");
                if (shot != null && !shot.isDone()) {
                    return StepResult.pending();
                }
                TOP.set((double) site.getY());
                next(ctx, 4);
                return StepResult.pending();
            }
            default -> {
                Vec3 away = new Vec3(site.getX() + 0.5, site.getY() + 0.2, site.getZ() + 0.5);
                StepResult reset = resetAndCheck(ctx, 4, "Block auf dem Homepoint", away, true, 0.3);
                if (reset != null) {
                    return reset;
                }
                if (blockOverlay() != null) {
                    problems(ctx).add("Überdeckung bleibt nach dem Reset");
                }
                return finish(ctx);
            }
        }
    }

    private static StepResult openStep(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        if (sessionDrone() == null) {
            return StepResult.fail("keine Sitzung");
        }
        BlockPos site = site();
        if (phase(ctx) == 0) {
            StepResult wait = once(ctx, "bauen", server -> {
                ServerLevel level = serverPlayer(server).level();
                QuadcopterEntity drone = serverDrone(server);
                clearSite(level);
                HomePoint home = new HomePoint(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, HomePointCodecs.dimension(level),
                        level.getGameTime(), HomePoint.Source.MANUAL);
                drone.setHomePoint(home);
                HOME.set(home);
                TOP.set((double) site.getY());
                put(level, site, Blocks.STONE.defaultBlockState());
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        Vec3 away = new Vec3(site.getX() + 0.5 + 3.0, site.getY() + 1.0, site.getZ() + 0.5);
        StepResult reset = resetAndCheck(ctx, 1, "Block unter freiem Himmel", away, true, 1.0 + TOLERANCE);
        return reset != null ? reset : finish(ctx);
    }

    private static StepResult buriedStep(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        if (sessionDrone() == null) {
            return StepResult.fail("keine Sitzung");
        }
        BlockPos site = site();
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = once(ctx, "zubauen", server -> {
                    ServerLevel level = serverPlayer(server).level();
                    QuadcopterEntity drone = serverDrone(server);
                    clearSite(level);
                    HomePoint home = new HomePoint(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, HomePointCodecs.dimension(level),
                            level.getGameTime(), HomePoint.Source.MANUAL);
                    drone.setHomePoint(home);
                    HOME.set(home);
                    for (int dy = 0; dy < HEIGHT - 1; dy++) {
                        for (int dx = -HALF + 1; dx <= HALF - 1; dx++) {
                            for (int dz = -HALF + 1; dz <= HALF - 1; dz++) {
                                put(level, site.offset(dx, dy, dz), Blocks.STONE.defaultBlockState());
                            }
                        }
                    }
                    place(server, new Vec3(site.getX() + 0.5 + HALF + 2.0, site.getY() + 0.3, site.getZ() + 0.5));
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (since(ctx) < HomeResetRules.COOLDOWN_TICKS + 30) {
                    return StepResult.pending();
                }
                StepResult reset = once(ctx, "Reset", server -> {
                    QuadcopterEntity drone = serverDrone(server);
                    Vec3 before = drone.position();
                    HomeResetRules.Outcome outcome = HomeResets.reset(serverPlayer(server), drone);
                    OUTCOME.set(outcome.name());
                    SPOT.set(drone.position());
                    NOTE.set(number(before.distanceTo(drone.position())));
                });
                if (reset != null) {
                    return reset;
                }
                ctx.log("zugebaut: Ergebnis " + OUTCOME.get() + ", Drohne " + NOTE.get() + " m bewegt");
                if (!"BLOCKED".equals(OUTCOME.get())) {
                    problems(ctx).add("Ergebnis " + OUTCOME.get() + " statt BLOCKED");
                }
                if (Double.parseDouble(NOTE.get()) > 0.05) {
                    problems(ctx).add("Drohne trotz Meldung versetzt");
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            default -> {
                StepResult wait = once(ctx, "räumen", server -> clearSite(serverPlayer(server).level()));
                return wait != null ? wait : finish(ctx);
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.clearTestInput();
        StepResult wait = once(ctx, "Aufräumen", server -> {
            ServerPlayer player = serverPlayer(server);
            ServerLevel level = player.level();
            UUID id = DRONE.getAndSet(null);
            if (id != null && level.getEntity(id) instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
            for (int pass = 0; pass < 2; pass++) {
                for (Map.Entry<BlockPos, BlockState> entry : SAVED.entrySet()) {
                    level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
                }
            }
            SAVED.clear();
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        });
        if (wait != null) {
            return wait;
        }
        HomeMode mode = MODE.getAndSet(null);
        if (mode != null) {
            RescueConfigs.get().homeMode = mode;
        }
        RescueController.INSTANCE.clearHome();
        HOME.set(null);
        SITE.set(null);
        return PilotSession.INSTANCE.isActive() ? (ctx.tick() > 100 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending())
                : StepResult.pass();
    }
}
