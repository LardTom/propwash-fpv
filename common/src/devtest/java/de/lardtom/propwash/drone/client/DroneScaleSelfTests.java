package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.DroneScale;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.client.AssistText;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class DroneScaleSelfTests {
    public static final String GROUP = "drone-scale: ";
    public static final float[] SHOWN = {0.25F, 1.0F, 3.0F};
    private static final double[] LINEUP_X = {-2.0, -1.2, 0.0};
    private static final double PARKED_X = -4.0;
    private static final long MS = 1_000_000L;
    private static final double SIZE_EPS = 1.0E-4;
    private static final double EYE_EPS = 2.0E-3;
    private static final int SITE_X = 30;
    private static final int SITE_Z = 30;
    private static final double EYE_HEIGHT = 1.62;
    private static final String FUTURE = "scale_future";
    private static final String PHASE = "scale_phase";
    private static final String SINCE = "scale_since";
    private static final AtomicReference<@Nullable String> RESULT = new AtomicReference<>();
    private static final AtomicReference<@Nullable Vec3> SITE = new AtomicReference<>();
    private static final AtomicReference<@Nullable Saved> SAVED = new AtomicReference<>();
    private static final List<Integer> SPAWNED = new CopyOnWriteArrayList<>();
    private static final AtomicReference<int @Nullable []> LINEUP = new AtomicReference<>();
    private static final AtomicReference<@Nullable Course> COURSE = new AtomicReference<>();
    private static final AtomicInteger ATTRIBUTE_DRONE = new AtomicInteger(-1);
    private static final AtomicReference<@Nullable String> GATE_LOG = new AtomicReference<>();
    private static final AtomicReference<Leaderboard.@Nullable Entry> SCALED_LAP = new AtomicReference<>();
    private static final AtomicReference<List<Integer>> GATE_DRONES = new AtomicReference<>(List.of());
    private static final AtomicInteger GATE_ID = new AtomicInteger(-1);
    private static final Map<BlockPos, BlockState> PLACED = new ConcurrentHashMap<>();

    private DroneScaleSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "/attribute setzt scale an der Drohne, Grenzen 0,0625–16, Modifikatoren, Speichern und Laden", 400,
                DroneScaleSelfTests::attributeStep);
        SelfTest.clientStep(GROUP + "Client sieht 0,25×, 1× und 3× mit passender Hitbox und mitskaliertem Modell", 400, DroneScaleSelfTests::renderStep);
        if (Screenshots.enabled()) {
            Screenshots.step(GROUP + "Bildschirmfoto 0,25×", "drone_scale_025", 20, new ShotScene(0));
            Screenshots.step(GROUP + "Bildschirmfoto 1×", "drone_scale_100", 20, new ShotScene(1));
            Screenshots.step(GROUP + "Bildschirmfoto 3×", "drone_scale_300", 20, new ShotScene(2));
            Screenshots.step(GROUP + "Bildschirmfoto 0,25×, 1× und 3× nebeneinander", "drone_scale_lineup", 20, new ShotScene(-1));
        }
        SelfTest.clientStep(GROUP + "FPV-Kamera sitzt bei 0,25× und 3× mitskaliert über dem Fuß", 900, DroneScaleSelfTests::cameraStep);
        SelfTest.clientStep(GROUP + "3×-Drohne bleibt am kleinen Gate hängen, 1× und 0,25× fliegen durch", 300, DroneScaleSelfTests::gateStep);
        SelfTest.clientStep(GROUP + "Runde mit 0,5× zählt als Flughilfe „Skalierung“, mit 1× ohne", 600, DroneScaleSelfTests::lapStep);
        SelfTest.clientStep(GROUP + "Aufräumen", 400, DroneScaleSelfTests::cleanupStep);
    }

    private record Saved(Vec3 pos, float yaw, float pitch, ItemStack head, ItemStack hand) {
    }

    private record Course(int id, int gateA, int gateB, int drone) {
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static @Nullable StepResult waitFor(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail("kein Server-Schritt");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Schritt fehlgeschlagen: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        String result = RESULT.getAndSet(null);
        return result == null ? null : StepResult.fail(result);
    }

    private static void server(SelfTest.StepContext ctx, ServerCheck check) {
        RESULT.set(null);
        ctx.state().put(FUTURE, ctx.onServer(server -> {
            try {
                RESULT.set(check.run(server));
            } catch (RuntimeException e) {
                RESULT.set(e.toString());
            }
        }));
    }

    @FunctionalInterface
    private interface ServerCheck {
        @Nullable String run(MinecraftServer server);
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
    }

    private static int waited(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    static Vec3 site(MinecraftServer server) {
        Vec3 known = SITE.get();
        if (known != null) {
            return known;
        }
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        int x = player.getBlockX() + SITE_X;
        int z = player.getBlockZ() + SITE_Z;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        Vec3 site = new Vec3(x + 0.5, y, z + 0.5);
        SITE.set(site);
        return site;
    }

    static QuadcopterEntity spawn(ServerLevel level, DroneBuild build, Vec3 at, boolean gravity) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(build);
        drone.setNoGravity(!gravity);
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        drone.resetTo(at, new Quaternionf().rotationY((float) Math.PI));
        SPAWNED.add(drone.getId());
        return drone;
    }

    static Output command(MinecraftServer server, Vec3 at, String command) {
        Output out = new Output();
        CommandSourceStack source = server.createCommandSourceStack().withPosition(at).withSource(out).withCallback((success, result) -> {
            out.success = success;
            out.result = result;
        });
        server.getCommands().performPrefixedCommand(source, command);
        return out;
    }

    static String setScale(MinecraftServer server, QuadcopterEntity drone, float scale) {
        Output out = command(server, drone.position(), String.format(Locale.ROOT, "attribute %s minecraft:scale base set %s", drone.getStringUUID(),
                scale));
        return out.success ? "" : "attribute base set " + scale + ": " + out.text();
    }

    private static float baseValue(QuadcopterEntity drone) {
        return (float) drone.attributes().map().getBaseValue(Attributes.SCALE);
    }

    private static StepResult attributeStep(SelfTest.StepContext ctx) {
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, server -> {
                    ServerLevel level = player(server).level();
                    Vec3 at = site(server).add(-3.0, 0.02, 0.0);
                    QuadcopterEntity drone = spawn(level, DroneBuild.FREESTYLE, at, true);
                    ATTRIBUTE_DRONE.set(drone.getId());
                    AirframeSpec spec = AirframeSpec.of(DroneBuild.FREESTYLE);
                    if (Math.abs(drone.getBbWidth() - spec.hitboxWidth) > SIZE_EPS || drone.scale() != 1.0F) {
                        return "neue Drohne nicht normal groß: " + drone.getBbWidth() + " scale " + drone.scale();
                    }
                    Output set = command(server, drone.position(), "attribute @e[type=propwash:drone,limit=1,sort=nearest] minecraft:scale base set 3");
                    if (!set.success || !set.text().contains("3")) {
                        return "/attribute … base set 3 scheitert: " + set.text();
                    }
                    return baseValue(drone) == 3.0F ? null : "Grundwert nach dem Befehl " + baseValue(drone);
                });
                next(ctx, 1);
            }
            case 1 -> {
                StepResult waiting = waitFor(ctx);
                if (waiting != null) {
                    return waiting;
                }
                next(ctx, 2);
            }
            case 2 -> {
                if (waited(ctx) < 5) {
                    return StepResult.pending();
                }
                int id = ATTRIBUTE_DRONE.get();
                server(ctx, server -> checkAttribute(server, id));
                next(ctx, 3);
            }
            case 3 -> {
                StepResult waiting = waitFor(ctx);
                if (waiting != null) {
                    return waiting;
                }
                next(ctx, 4);
            }
            case 4 -> {
                Minecraft mc = Minecraft.getInstance();
                int id = ATTRIBUTE_DRONE.get();
                if (mc.level == null || !(mc.level.getEntity(id) instanceof QuadcopterEntity drone)) {
                    return waited(ctx) > 100 ? StepResult.fail("Client kennt die Drohne nicht") : StepResult.pending();
                }
                if (drone.scale() != 1.5F) {
                    return waited(ctx) > 100 ? StepResult.fail("Client sieht scale " + drone.scale() + " statt 1,5") : StepResult.pending();
                }
                double expected = AirframeSpec.of(drone.build()).hitboxWidth * 1.5;
                if (Math.abs(drone.getBbWidth() - expected) > SIZE_EPS) {
                    return StepResult.fail("Client-Hitbox " + drone.getBbWidth() + " statt " + expected);
                }
                ctx.log(String.format(Locale.ROOT, "Client: scale %.3f, Hitbox %.4f × %.4f", drone.scale(), drone.getBbWidth(), drone.getBbHeight()));
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
        return StepResult.pending();
    }

    private static @Nullable String checkAttribute(MinecraftServer server, int id) {
        ServerLevel level = player(server).level();
        if (!(level.getEntity(id) instanceof QuadcopterEntity drone)) {
            return "Drohne verschwunden";
        }
        AirframeSpec spec = AirframeSpec.of(drone.build());
        if (drone.scale() != 3.0F || Math.abs(drone.getBbWidth() - spec.hitboxWidth * 3.0) > SIZE_EPS
                || Math.abs(drone.getBbHeight() - spec.hitboxHeight * 3.0) > SIZE_EPS) {
            return "nach einem Tick: scale " + drone.scale() + ", Hitbox " + drone.getBbWidth() + " × " + drone.getBbHeight();
        }
        Output get = command(server, drone.position(), "attribute " + drone.getStringUUID() + " minecraft:scale get 100");
        if (!get.success || get.result != 300) {
            return "attribute get 100 meldet " + get.result + ": " + get.text();
        }
        String big = setScale(server, drone, 40.0F);
        if (!big.isEmpty() || drone.attributes().scale() != 16.0F) {
            return "scale 40 nicht auf 16 begrenzt: " + drone.attributes().scale() + " " + big;
        }
        String tiny = setScale(server, drone, 0.01F);
        if (!tiny.isEmpty() || drone.attributes().scale() != 0.0625F) {
            return "scale 0,01 nicht auf 0,0625 begrenzt: " + drone.attributes().scale() + " " + tiny;
        }
        Output reset = command(server, drone.position(), "attribute " + drone.getStringUUID() + " minecraft:scale base reset");
        if (!reset.success || baseValue(drone) != 1.0F) {
            return "base reset: " + baseValue(drone) + " " + reset.text();
        }
        Output add = command(server, drone.position(), "attribute " + drone.getStringUUID()
                + " minecraft:scale modifier add propwash:selftest 0.5 add_value");
        if (!add.success || drone.attributes().scale() != 1.5F) {
            return "Modifikator +0,5: " + drone.attributes().scale() + " " + add.text();
        }
        Output value = command(server, drone.position(), "attribute " + drone.getStringUUID() + " minecraft:scale modifier value get propwash:selftest 10");
        if (!value.success || value.result != 5) {
            return "modifier value get: " + value.result + " " + value.text();
        }
        Output speed = command(server, drone.position(), "attribute " + drone.getStringUUID() + " minecraft:movement_speed get");
        if (speed.success || !speed.text().toLowerCase(Locale.ROOT).contains("attribut")) {
            return "fremdes Attribut ohne Fehlermeldung: " + speed.text();
        }
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, drone.registryAccess());
        if (!drone.save(output)) {
            return "Drohne nicht speicherbar";
        }
        Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()),
                level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
        if (!(loaded instanceof QuadcopterEntity copy) || copy.scale() != 1.5F || copy.attributes().scale() != 1.5F
                || Math.abs(copy.getBbWidth() - spec.hitboxWidth * 1.5) > SIZE_EPS) {
            return "nach dem Laden: " + (loaded instanceof QuadcopterEntity q ? q.scale() + " Hitbox " + q.getBbWidth() : String.valueOf(loaded));
        }
        QuadcopterEntity plain = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        TagValueOutput plainOutput = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        if (plain == null || !plain.save(plainOutput) || plainOutput.buildResult().contains("attributes")) {
            return "normale Drohne speichert eine Attributliste";
        }
        QuadcopterEntity placed = DroneModule.DRONE.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (placed == null) {
            return "Drohne aus dem Item nicht erzeugt";
        }
        placed.loadFromStack(drone.toStack());
        if (placed.scale() != 1.0F || placed.attributes().changed() || Math.abs(placed.getBbWidth() - spec.hitboxWidth) > SIZE_EPS) {
            return "aus dem Item abgesetzte Drohne übernimmt scale " + placed.scale();
        }
        return null;
    }

    private static StepResult renderStep(SelfTest.StepContext ctx) {
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, DroneScaleSelfTests::lineup);
                next(ctx, 1);
            }
            case 1 -> {
                StepResult waiting = waitFor(ctx);
                if (waiting != null) {
                    return waiting;
                }
                next(ctx, 2);
            }
            case 2 -> {
                String seen = seenOnClient();
                if (seen != null) {
                    return waited(ctx) > 200 ? StepResult.fail(seen) : StepResult.pending();
                }
                String render = renderCheck(ctx);
                return render == null ? StepResult.pass() : StepResult.fail(render);
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
        return StepResult.pending();
    }

    static @Nullable String lineup(MinecraftServer server) {
        int[] known = LINEUP.get();
        ServerLevel level = player(server).level();
        if (known != null) {
            boolean alive = true;
            for (int id : known) {
                alive &= level.getEntity(id) instanceof QuadcopterEntity;
            }
            if (alive) {
                return null;
            }
        }
        if (level.getEntity(ATTRIBUTE_DRONE.getAndSet(-1)) instanceof QuadcopterEntity used) {
            used.discard();
        }
        Vec3 site = site(server);
        int[] ids = new int[SHOWN.length];
        for (int i = 0; i < SHOWN.length; i++) {
            QuadcopterEntity drone = spawn(level, DroneBuild.FREESTYLE, site.add(LINEUP_X[i], 0.02, 0.0), true);
            String set = setScale(server, drone, SHOWN[i]);
            if (!set.isEmpty()) {
                return set;
            }
            ids[i] = drone.getId();
        }
        LINEUP.set(ids);
        return null;
    }

    private static @Nullable String seenOnClient() {
        int[] ids = LINEUP.get();
        Minecraft mc = Minecraft.getInstance();
        if (ids == null || mc.level == null) {
            return "Reihe fehlt";
        }
        for (int i = 0; i < ids.length; i++) {
            if (!(mc.level.getEntity(ids[i]) instanceof QuadcopterEntity drone)) {
                return "Client kennt Drohne " + i + " nicht";
            }
            double expected = AirframeSpec.of(drone.build()).hitboxWidth * SHOWN[i];
            if (drone.scale() != SHOWN[i] || Math.abs(drone.getBbWidth() - expected) > SIZE_EPS) {
                return "Client: Drohne " + i + " scale " + drone.scale() + " Hitbox " + drone.getBbWidth() + " statt " + SHOWN[i] + " / " + expected;
            }
        }
        return null;
    }

    private static @Nullable String renderCheck(SelfTest.StepContext ctx) {
        int[] ids = LINEUP.get();
        Minecraft mc = Minecraft.getInstance();
        if (ids == null || mc.level == null) {
            return "Reihe fehlt";
        }
        DroneRenderState[] states = new DroneRenderState[ids.length];
        for (int i = 0; i < ids.length; i++) {
            QuadcopterEntity drone = (QuadcopterEntity) mc.level.getEntity(ids[i]);
            if (drone == null) {
                return "Drohne " + i + " fehlt";
            }
            EntityRenderState state = mc.getEntityRenderDispatcher().getRenderer(drone).createRenderState(drone, 1.0F);
            if (!(state instanceof DroneRenderState drawn)) {
                return "kein Drohnen-Renderzustand: " + state;
            }
            states[i] = drawn;
        }
        DroneRenderState normal = states[1];
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            DroneRenderState s = states[i];
            float k = SHOWN[i];
            if (s.entityScale != k || Math.abs(s.poseScale - normal.poseScale * k) > 1.0E-5F || Math.abs(s.originY - normal.originY * k) > 1.0E-4F
                    || Math.abs(s.visualRadius - normal.visualRadius * k) > 1.0E-4F) {
                return String.format(Locale.ROOT, "Modell %s×: entityScale %.3f, poseScale %.5f (1×: %.5f), originY %.4f (1×: %.4f), Radius %.4f (1×: %.4f)",
                        k, s.entityScale, s.poseScale, normal.poseScale, s.originY, normal.originY, s.visualRadius, normal.visualRadius);
            }
            log.append(String.format(Locale.ROOT, "%s×: poseScale %.5f, Schwerpunkt %.4f über dem Fuß, Radius %.3f; ", k, s.poseScale, s.originY,
                    s.visualRadius));
        }
        ctx.log("Renderzustand " + log);
        return null;
    }

    private static final class ShotScene implements Screenshots.Scene {
        private final int only;

        private ShotScene(int only) {
            this.only = only;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            if (!state.containsKey("shot_server")) {
                int only = this.only;
                state.put("shot_server", ctx.onServer(server -> {
                    lineup(server);
                    pose(server, only);
                }));
                return StepResult.pending();
            }
            CompletableFuture<?> future = (CompletableFuture<?>) state.get("shot_server");
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Szene: " + future.exceptionNow());
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            String seen = seenOnClient();
            if (seen != null) {
                return ctx.tick() > 300 ? StepResult.fail(seen) : StepResult.pending();
            }
            Minecraft mc = Minecraft.getInstance();
            int[] ids = LINEUP.get();
            if (ids != null && mc.level != null) {
                for (int i = 0; i < ids.length; i++) {
                    Entity drone = mc.level.getEntity(ids[i]);
                    if (drone != null) {
                        drone.setInvisible(this.only >= 0 && this.only != i);
                    }
                }
            }
            if (!state.containsKey("shot_gui")) {
                boolean toggled = !mc.gui.hud.isHidden();
                if (toggled) {
                    mc.gui.hud.toggle();
                }
                state.put("shot_gui", toggled);
            }
            return ctx.tick() > 40 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (Boolean.TRUE.equals(ctx.state().remove("shot_gui")) && mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            int[] ids = LINEUP.get();
            if (ids != null && mc.level != null) {
                for (int id : ids) {
                    Entity drone = mc.level.getEntity(id);
                    if (drone != null) {
                        drone.setInvisible(false);
                    }
                }
            }
        }
    }

    private static void pose(MinecraftServer server, int only) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot(), player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                player.getMainHandItem().copy()));
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        Vec3 site = site(server);
        BlockPos marker = BlockPos.containing(site.x + 1.0, site.y, site.z);
        if (!PLACED.containsKey(marker)) {
            PLACED.put(marker.immutable(), level.getBlockState(marker));
            level.setBlockAndUpdate(marker, Blocks.QUARTZ_BLOCK.defaultBlockState());
        }
        int[] ids = LINEUP.get();
        if (ids != null) {
            for (int i = 0; i < ids.length; i++) {
                if (level.getEntity(ids[i]) instanceof QuadcopterEntity drone) {
                    double x = only < 0 ? LINEUP_X[i] : i == only ? 0.0 : i == SHOWN.length - 1 ? PARKED_X : LINEUP_X[i];
                    Vec3 at = site.add(x, 0.02, 0.0);
                    drone.resetTo(at, new Quaternionf().rotationY((float) Math.PI));
                }
            }
        }
        double x = only < 0 ? site.x - 0.6 : site.x + 0.5;
        double back = only < 0 ? 2.8 : 2.0;
        float pitch = (float) Math.toDegrees(Math.atan2(EYE_HEIGHT - 0.1, back));
        player.teleportTo(level, x, site.y, site.z + back, Set.of(), 180.0F, pitch, true);
    }

    private static StepResult cameraStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, DroneScaleSelfTests::startFpv);
                next(ctx, 1);
            }
            case 1 -> {
                StepResult waiting = waitFor(ctx);
                if (waiting != null) {
                    return waiting;
                }
                next(ctx, 2);
            }
            case 2, 4, 6 -> {
                QuadcopterEntity drone = session.drone();
                float wanted = phase(ctx) == 2 ? 1.0F : phase(ctx) == 4 ? 3.0F : 0.25F;
                if (!session.isFpv() || drone == null || drone.scale() != wanted) {
                    return waited(ctx) > 300 ? StepResult.fail("FPV-Sitzung mit scale " + wanted + " fehlt: " + (drone == null ? "-" : drone.scale()))
                            : StepResult.pending();
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                if (waited(ctx) < 30) {
                    return StepResult.pending();
                }
                Vector3d eye = new Vector3d();
                Quaternionf rotation = new Quaternionf();
                if (!FpvView.cameraPose(eye, rotation)) {
                    return StepResult.fail("keine FPV-Kamera");
                }
                double above = eye.y - drone.getY();
                ctx.state().put("eye_" + phase(ctx), above);
                ctx.log(String.format(Locale.ROOT, "FPV-Kamera bei %s× %.4f über dem Fuß, Hitbox-Höhe %.4f", wanted, above, drone.getBbHeight()));
                if (phase(ctx) == 6) {
                    session.clearTestInput();
                    double normal = (double) ctx.state().get("eye_2");
                    double big = (double) ctx.state().get("eye_4");
                    if (Math.abs(big - 3.0 * normal) > EYE_EPS || Math.abs(above - 0.25 * normal) > EYE_EPS) {
                        return StepResult.fail(String.format(Locale.ROOT, "Kamera nicht mitskaliert: 1× %.4f, 3× %.4f (erwartet %.4f), 0,25× %.4f (erwartet %.4f)",
                                normal, big, 3.0 * normal, above, 0.25 * normal));
                    }
                    server(ctx, server -> {
                        DronePiloting.pilotedBy(player(server).getUUID()).ifPresent(DronePiloting::release);
                        return null;
                    });
                    next(ctx, 8);
                    return StepResult.pending();
                }
                float scale = phase(ctx) == 2 ? 3.0F : 0.25F;
                int id = drone.getId();
                server(ctx, server -> player(server).level().getEntity(id) instanceof QuadcopterEntity served ? emptyToNull(setScale(server, served, scale))
                        : "Drohne fehlt auf dem Server");
                next(ctx, phase(ctx) + 1);
            }
            case 3, 5 -> {
                StepResult waiting = waitFor(ctx);
                if (waiting != null) {
                    return waiting;
                }
                next(ctx, phase(ctx) + 1);
            }
            case 8 -> {
                StepResult waiting = waitFor(ctx);
                if (waiting != null) {
                    return waiting;
                }
                next(ctx, 9);
            }
            case 9 -> {
                return session.isActive() ? (waited(ctx) > 200 ? StepResult.fail("FPV-Sitzung endet nicht") : StepResult.pending()) : StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
        return StepResult.pending();
    }

    private static @Nullable String emptyToNull(String text) {
        return text.isEmpty() ? null : text;
    }

    private static @Nullable String startFpv(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot(), player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                player.getMainHandItem().copy()));
        Vec3 site = site(server);
        Vec3 at = site.add(4.0, 0.02, -2.0);
        player.teleportTo(level, site.x + 4.0, site.y, site.z + 1.0, Set.of(), 180.0F, 10.0F, true);
        QuadcopterEntity drone = spawn(level, DroneBuild.FREESTYLE, at, true);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            return "Kreativ-Brille nicht registriert";
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        return result == DronePiloting.StartResult.STARTED ? null : "FPV-Start verweigert: " + result;
    }

    private static StepResult gateStep(SelfTest.StepContext ctx) {
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, DroneScaleSelfTests::gateSetup);
                next(ctx, 1);
            }
            case 1 -> {
                StepResult waiting = waitFor(ctx);
                if (waiting != null) {
                    return waiting;
                }
                next(ctx, 2);
            }
            case 2 -> {
                if (waited(ctx) < 5) {
                    return StepResult.pending();
                }
                server(ctx, DroneScaleSelfTests::gateFly);
                next(ctx, 3);
            }
            case 3 -> {
                StepResult waiting = waitFor(ctx);
                if (waiting != null) {
                    return waiting;
                }
                String log = GATE_LOG.getAndSet(null);
                if (log != null) {
                    ctx.log(log);
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
        return StepResult.pending();
    }

    private record GateCase(String name, DroneBuild build, float scale, boolean passes) {
    }

    private static final GateCase[] GATE_CASES = {
            new GateCase("7 Zoll 1×", DroneBuild.LONG_RANGE, 1.0F, true),
            new GateCase("7 Zoll 3×", DroneBuild.LONG_RANGE, 3.0F, false),
            new GateCase("5 Zoll 0,25×", DroneBuild.FREESTYLE, 0.25F, true),
            new GateCase("5 Zoll 3×", DroneBuild.FREESTYLE, 3.0F, true),
            new GateCase("5 Zoll 4×", DroneBuild.FREESTYLE, 4.0F, false)
    };

    private static @Nullable String gateSetup(MinecraftServer server) {
        ServerLevel level = player(server).level();
        Vec3 site = site(server).add(0.0, 0.0, -14.0);
        GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (gate == null) {
            return "Gate nicht erzeugt";
        }
        gate.setSize(GateSize.SMALL);
        gate.snapTo(site.x, site.y, site.z, 0.0F, 0.0F);
        gate.setOpening(GateSize.MIN_OPENING, GateSize.MIN_OPENING);
        if (!level.addFreshEntity(gate)) {
            return "Gate nicht gespawnt";
        }
        SPAWNED.add(gate.getId());
        GateGeometry g = gate.geometry();
        Vec3 normal = new Vec3(g.normalX(), 0.0, g.normalZ()).normalize();
        List<Integer> drones = new ArrayList<>();
        for (int i = 0; i < GATE_CASES.length; i++) {
            GateCase test = GATE_CASES[i];
            AirframeSpec spec = AirframeSpec.of(test.build());
            double foot = g.centerY() - spec.hitboxHeight * test.scale() / 2.0;
            Vec3 lane = new Vec3(g.centerX(), foot, g.centerZ()).subtract(normal.scale(2.0 + 3.0 * i));
            QuadcopterEntity drone = spawn(level, test.build(), lane, false);
            String set = setScale(server, drone, test.scale());
            if (!set.isEmpty()) {
                return set;
            }
            drones.add(drone.getId());
        }
        GATE_DRONES.set(List.copyOf(drones));
        GATE_ID.set(gate.getId());
        return null;
    }

    private static @Nullable String gateFly(MinecraftServer server) {
        ServerLevel level = player(server).level();
        if (!(level.getEntity(GATE_ID.get()) instanceof GateEntity gate)) {
            return "Gate fehlt";
        }
        GateGeometry g = gate.geometry();
        Vec3 center = new Vec3(g.centerX(), g.centerY(), g.centerZ());
        Vec3 normal = new Vec3(g.normalX(), 0.0, g.normalZ()).normalize();
        List<Integer> drones = GATE_DRONES.get();
        StringBuilder log = new StringBuilder("Gate-Öffnung " + GateSize.MIN_OPENING + ": ");
        for (int i = 0; i < GATE_CASES.length; i++) {
            GateCase test = GATE_CASES[i];
            if (!(level.getEntity(drones.get(i)) instanceof QuadcopterEntity drone)) {
                return test.name() + ": Drohne fehlt";
            }
            if (drone.scale() != test.scale()) {
                return test.name() + ": scale " + drone.scale();
            }
            Vec3 foot = new Vec3(center.x, center.y - drone.getBbHeight() / 2.0, center.z).subtract(normal.scale(1.5));
            drone.snapTo(foot.x, foot.y, foot.z, 0.0F, 0.0F);
            drone.setDeltaMovement(Vec3.ZERO);
            drone.move(MoverType.SELF, normal.scale(3.0));
            double through = drone.position().subtract(center).dot(normal);
            boolean passed = through > 1.0;
            Vec3 lane = center.subtract(normal.scale(2.0 + 3.0 * i));
            drone.snapTo(lane.x, lane.y, lane.z, 0.0F, 0.0F);
            log.append(String.format(Locale.ROOT, "%s (Breite %.3f) %s bei %.2f; ", test.name(), drone.getBbWidth(), passed ? "durch" : "hängt",
                    through));
            if (passed != test.passes()) {
                return test.name() + (test.passes() ? " bleibt hängen" : " fliegt durch") + String.format(Locale.ROOT,
                        " (Breite %.3f, Weg hinter dem Gate %.2f)", drone.getBbWidth(), through);
            }
        }
        GATE_LOG.set(log.toString());
        return null;
    }

    private static StepResult lapStep(SelfTest.StepContext ctx) {
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, DroneScaleSelfTests::courseSetup);
                next(ctx, 1);
            }
            case 1, 3, 5 -> {
                StepResult waiting = waitFor(ctx);
                if (waiting != null) {
                    return waiting;
                }
                next(ctx, phase(ctx) + 1);
            }
            case 2 -> {
                if (waited(ctx) < 5) {
                    return StepResult.pending();
                }
                server(ctx, server -> lap(server, 0.5F, true));
                next(ctx, 3);
            }
            case 4 -> {
                if (waited(ctx) < 5) {
                    return StepResult.pending();
                }
                server(ctx, server -> lap(server, 1.0F, false));
                next(ctx, 5);
            }
            case 6 -> {
                Leaderboard.Entry entry = SCALED_LAP.getAndSet(null);
                if (entry == null) {
                    return StepResult.fail("keine Runde mit Flughilfe gemerkt");
                }
                String text = AssistText.storedReasons(AssistReasons.of(entry.assist(), entry.details())).getString();
                if (!text.contains("50")) {
                    return StepResult.fail("Text nennt die Größe nicht: " + text);
                }
                ctx.log("Runde mit 0,5×: " + text);
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
        return StepResult.pending();
    }

    private static @Nullable String courseSetup(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return "CourseRegistry läuft nicht";
        }
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
        Vec3 site = site(server).add(-12.0, 0.0, -30.0);
        GateEntity a = lapGate(level, site);
        GateEntity b = lapGate(level, site.add(0.0, 0.0, -24.0));
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CourseToolItem.useOnGate(tool, player, a);
        CourseToolItem.useOnGate(tool, player, b);
        int id = CourseToolItem.courseOf(tool);
        CourseRegistry.Course course = registry.course(id);
        if (course == null || course.gates.size() != 2) {
            return "Kurs nicht angelegt";
        }
        registry.setLaps(course, 3);
        CourseRegistry.GateRecord start = course.gates.getFirst();
        QuadcopterEntity drone = spawn(level, DroneBuild.FREESTYLE, new Vec3(start.x, start.y + 1.5, start.z + 3.0), false);
        drone.setPilot(player);
        COURSE.set(new Course(id, a.getId(), b.getId(), drone.getId()));
        return setScale(server, drone, 0.5F).isEmpty() ? null : "scale 0,5 nicht gesetzt";
    }

    private static GateEntity lapGate(ServerLevel level, Vec3 at) {
        GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (gate == null) {
            throw new IllegalStateException("Gate nicht erzeugt");
        }
        gate.setSize(GateSize.MEDIUM);
        gate.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        if (!level.addFreshEntity(gate)) {
            throw new IllegalStateException("Gate nicht gespawnt");
        }
        SPAWNED.add(gate.getId());
        return gate;
    }

    private static @Nullable String lap(MinecraftServer server, float scale, boolean assisted) {
        ServerPlayer player = player(server);
        Course site = COURSE.get();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = site == null || registry == null ? null : registry.course(site.id());
        if (course == null || !(player.level().getEntity(site.drone()) instanceof QuadcopterEntity drone)) {
            return "Kurs oder Drohne fehlt";
        }
        if (drone.scale() != scale) {
            return "Drohne hat scale " + drone.scale() + " statt " + scale;
        }
        if ((RaceServer.serverAssist(drone) & LapAssist.SCALE.bit()) != 0 != assisted) {
            return "Server-Prüfung: Skalierung " + (assisted ? "fehlt" : "gemeldet") + " bei " + scale;
        }
        Leaderboard board = registry.board(course);
        List<GateGeometry> gates = course.toData().geometry();
        long lap = Math.max(RaceServer.minimumLapNanos(course, player) * 3L, 6_000L * MS);
        long t = System.nanoTime() + (assisted ? 60_000L : 120_000L) * MS;
        RaceServer.Verdict started = RaceServer.handle(report(RacePayloads.ReportKind.START, course.id, 0, 0L), player, t);
        if (started != RaceServer.Verdict.ACCEPTED) {
            return "Start abgelehnt: " + started;
        }
        long end = flyLap(drone, player, gates, t + 100L * MS, lap);
        RaceServer.Verdict verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, course.id, 1, lap), player, end + 100L * MS);
        if (verdict != RaceServer.Verdict.ACCEPTED) {
            return "Runde mit " + scale + "× abgelehnt: " + verdict;
        }
        RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.ABORT, course.id, 0, 0L, 0L), player, end + 200L * MS);
        if (assisted) {
            Leaderboard.Entry entry = board.bestAssisted(player.getUUID(), null);
            if (entry == null || (entry.assist() & LapAssist.SCALE.bit()) == 0) {
                return "Runde mit 0,5× nicht mit Grund Skalierung gespeichert: " + entry;
            }
            boolean named = entry.details().stream().anyMatch(d -> d.reason() == LapAssist.SCALE && d.value() == 50
                    && AssistDetail.SCALE_PERCENT.equals(d.subject()));
            if (!named || board.best(player.getUUID(), null) != null) {
                return "Gründe der Runde: " + entry.details() + ", Wertung ohne Flughilfe " + board.best(player.getUUID(), null);
            }
            SCALED_LAP.set(entry);
            String reset = emptyToNull(command(server, drone.position(), "attribute " + drone.getStringUUID() + " minecraft:scale base reset").success
                    ? "" : "base reset scheitert");
            return reset;
        }
        Leaderboard.Entry plain = board.best(player.getUUID(), null);
        if (plain == null || plain.assisted() || plain.nanos() != lap) {
            return "Runde mit 1× nicht ohne Flughilfe gewertet: " + plain;
        }
        return null;
    }

    private static RacePayloads.RaceReportC2S report(RacePayloads.ReportKind kind, int course, int lap, long lapNanos) {
        return new RacePayloads.RaceReportC2S(kind, course, lap, lapNanos, 0L, 0, 0L);
    }

    private static long flyLap(QuadcopterEntity drone, ServerPlayer player, List<GateGeometry> gates, long start, long lap) {
        long step = 250L * MS;
        long t = start;
        int passes = gates.size() + 1;
        for (int i = 0; i < passes; i++) {
            GateGeometry gate = gates.get(i % gates.size());
            long at = start + lap * i / gates.size();
            Vec3 aside = new Vec3(gate.x() + 6.0, gate.centerY() + 3.0, gate.z());
            for (; t < at; t += step) {
                RaceServer.onDroneMoved(drone, player, aside, aside, t);
            }
            Vec3 center = new Vec3(gate.x(), gate.centerY(), gate.z());
            Vec3 normal = new Vec3(gate.normalX(), 0.0, gate.normalZ());
            RaceServer.onDroneMoved(drone, player, center.subtract(normal), center.add(normal), at);
            t = at + step;
        }
        return start + lap;
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (!ctx.state().containsKey(FUTURE) && !ctx.state().containsKey("cleaned")) {
            PilotSession.INSTANCE.clearTestInput();
            server(ctx, server -> {
                tearDown(server);
                return null;
            });
            ctx.state().put("cleaned", true);
            return StepResult.pending();
        }
        if (ctx.state().containsKey(FUTURE)) {
            StepResult waiting = waitFor(ctx);
            if (waiting != null) {
                return waiting;
            }
        }
        return PilotSession.INSTANCE.isActive() ? (ctx.tick() > 300 ? StepResult.fail("FPV-Sitzung endet nicht") : StepResult.pending())
                : StepResult.pass();
    }

    static void tearDown(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, WorldRuleSpecs.SAVE_ASSISTED_LAPS.defaultValue());
        for (int id : SPAWNED) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                drone.setPilot(null);
            }
            if (entity != null) {
                entity.discard();
            }
        }
        SPAWNED.clear();
        PLACED.forEach(level::setBlockAndUpdate);
        PLACED.clear();
        LINEUP.set(null);
        GATE_DRONES.set(List.of());
        GATE_ID.set(-1);
        ATTRIBUTE_DRONE.set(-1);
        GATE_LOG.set(null);
        SCALED_LAP.set(null);
        Course course = COURSE.getAndSet(null);
        CourseRegistry registry = CourseRegistry.get();
        if (course != null && registry != null) {
            registry.deleteCourse(course.id());
        }
        Saved saved = SAVED.getAndSet(null);
        if (saved != null) {
            player.setItemSlot(EquipmentSlot.HEAD, saved.head());
            player.setItemSlot(EquipmentSlot.MAINHAND, saved.hand());
            player.teleportTo(level, saved.pos().x, saved.pos().y, saved.pos().z, Set.of(), saved.yaw(), saved.pitch(), true);
        }
        SITE.set(null);
    }

    static final class Output implements CommandSource {
        private final List<String> lines = new CopyOnWriteArrayList<>();
        boolean success;
        int result;

        String text() {
            return String.join(" | ", this.lines);
        }

        @Override
        public void sendSystemMessage(Component message) {
            this.lines.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
