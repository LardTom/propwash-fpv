package de.lardtom.propwash.zone.client;

import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.AssistWords;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.client.AssistText;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneCheck;
import de.lardtom.propwash.zone.ZoneData;
import de.lardtom.propwash.zone.ZoneDraft;
import de.lardtom.propwash.zone.ZoneServer;
import de.lardtom.propwash.zone.ZoneShape;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class ZoneSelfTests {
    public static final String PREFIX = "Selbsttest ";
    static final String HALL = PREFIX + "Halle";
    static final String ROUND = PREFIX + "Rund";
    static final String PILLAR = PREFIX + "Säule";
    static final String FLOAT = PREFIX + "Schwerelos";
    static final String EDITOR = PREFIX + "Editor";
    static final int AREA_OFFSET = 48;
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String MARK = "mark";
    private static final AtomicReference<@Nullable String> RESULT = new AtomicReference<>();
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicReference<double @Nullable []> RETURN = new AtomicReference<>();

    private ZoneSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("zonen: Befehle legen an, ändern und löschen; Weltdaten und Sync", 400, ZoneSelfTests::commandStep);
        SelfTest.clientStep("zonen: Flug durch eine Zone – Übergang, rennfrei, Flughilfe mit Zonennamen, Akku läuft weiter", 1200,
                ZoneSelfTests::flightStep);
        SelfTest.clientStep("zonen: Editor legt an, ändert und löscht", 600, ZoneSelfTests::editorStep);
        ZonePlacementSelfTests.register();
        SelfTest.clientStep("zonen: Aufräumen", 300, ZoneSelfTests::cleanupStep);
    }

    static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    static int command(MinecraftServer server, String command) {
        AtomicInteger result = new AtomicInteger();
        server.getCommands().performPrefixedCommand(player(server).createCommandSourceStack().withCallback((success, value) -> result.set(success
                ? value : 0)), command);
        return result.get();
    }

    static Optional<Zone> zone(MinecraftServer server, String name) {
        return ZoneData.get(server).byName(ZoneServer.dimension(player(server).level()), name);
    }

    static @Nullable Zone client(String name) {
        for (Zone zone : ClientZones.INSTANCE.zones()) {
            if (ZoneCheck.sameName(zone.name(), name)) {
                return zone;
            }
        }
        return null;
    }

    static void removeTestZones(MinecraftServer server) {
        for (Zone zone : ZoneData.get(server).all()) {
            if (zone.name().startsWith(PREFIX)) {
                ZoneServer.delete(server, zone.id(), Component.literal("Selbsttest"), "Selbsttest");
            }
        }
    }

    static Zone save(MinecraftServer server, Zone zone) {
        ZoneServer.Outcome outcome = ZoneServer.save(server, zone, Component.literal("Selbsttest"), "Selbsttest");
        if (!outcome.ok() || outcome.zone() == null) {
            throw new IllegalStateException("Zone " + zone.name() + " abgelehnt: " + outcome.problem());
        }
        return outcome.zone();
    }

    private static @Nullable StepResult waitFuture(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
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

    private static void server(SelfTest.StepContext ctx, Function<MinecraftServer, @Nullable String> action) {
        RESULT.set(null);
        ctx.state().put(FUTURE, ctx.onServer(server -> RESULT.set(action.apply(server))));
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(MARK, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    private static StepResult commandStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, ZoneSelfTests::commands);
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                Zone hall = client(HALL);
                if (hall == null || hall.priority() != 2 || !ClientZones.INSTANCE.editable()) {
                    return since(ctx) > 200 ? StepResult.fail("Zone kommt nicht beim Client an: " + ClientZones.INSTANCE.zones())
                            : StepResult.pending();
                }
                if (!hall.raceLegal() || hall.transitionTenths() != 15 || hall.value(WorldRuleSpecs.GRAVITY_SCALE, -1) != 50
                        || hall.value(WorldRuleSpecs.BATTERY_SCALE, -1) != 0) {
                    return StepResult.fail("Zone beim Client mit falschen Werten: " + hall);
                }
                if (client(PILLAR) != null || client(ROUND) != null) {
                    return StepResult.fail("gelöschte Zone noch beim Client");
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Phase");
            }
        }
    }

    private static @Nullable String commands(MinecraftServer server) {
        removeTestZones(server);
        String quoted = "\"" + HALL + "\"";
        List<String> ok = List.of(
                "propwash zone create " + quoted + " box ~10 ~-1 ~10 ~25 ~15 ~25",
                "propwash zone set " + quoted + " battery_scale 0",
                "propwash zone set " + quoted + " gravity_scale 50",
                "propwash zone set " + quoted + " wind 2",
                "propwash zone reset " + quoted + " wind",
                "propwash zone priority " + quoted + " 2",
                "propwash zone racelegal " + quoted + " true",
                "propwash zone transition " + quoted + " 1.5",
                "propwash zone create \"" + ROUND + "\" cylinder ~0 ~0 ~40 6 10",
                "propwash zone list",
                "propwash zone info " + quoted);
        for (String line : ok) {
            if (command(server, line) <= 0) {
                return "Befehl abgelehnt: /" + line;
            }
        }
        List<String> refused = List.of(
                "propwash zone set " + quoted + " drone_trails true",
                "propwash zone set " + quoted + " gravity_scale abc",
                "propwash zone create " + quoted + " box ~0 ~0 ~0 ~1 ~1 ~1",
                "propwash zone delete \"" + PREFIX + "gibt es nicht\"");
        for (String line : refused) {
            if (command(server, line) > 0) {
                return "Befehl hätte scheitern müssen: /" + line;
            }
        }
        Zone hall = zone(server, HALL).orElse(null);
        if (hall == null || hall.shape() != ZoneShape.BOX || hall.box().sizeX() != 16.0 || hall.box().sizeY() != 17.0) {
            return "Halle falsch angelegt: " + hall;
        }
        if (!Map.of("gravity_scale", 50, "battery_scale", 0).equals(Map.copyOf(hall.overrides())) || hall.priority() != 2 || !hall.raceLegal()
                || hall.transitionTenths() != 15) {
            return "Halle falsch geändert: " + hall;
        }
        Zone round = zone(server, ROUND).orElse(null);
        if (round == null || round.shape() != ZoneShape.CYLINDER || round.box().radius() != 6.0) {
            return "Zylinder falsch angelegt: " + round;
        }
        var codec = ZoneData.TYPE.codec();
        var encoded = codec.encodeStart(JsonOps.INSTANCE, ZoneData.get(server)).getOrThrow();
        ZoneData loaded = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        if (!loaded.all().equals(ZoneData.get(server).all()) || loaded.nextId() != ZoneData.get(server).nextId()) {
            return "Weltdaten nicht verlustfrei: " + encoded;
        }
        if (command(server, "propwash zone rename \"" + ROUND + "\" \"" + PILLAR + "\"") <= 0 || zone(server, PILLAR).isEmpty()) {
            return "Umbenennen fehlgeschlagen";
        }
        if (command(server, "propwash zone delete \"" + PILLAR + "\"") <= 0 || zone(server, PILLAR).isPresent()) {
            return "Löschen fehlgeschlagen";
        }
        return null;
    }

    static CompletableFuture<Void> startFlight(SelfTest.StepContext ctx, boolean fpv) {
        return ctx.onServer(server -> {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            if (RETURN.get() == null) {
                RETURN.set(new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()});
            }
            int x = (int) Math.floor(RETURN.get()[0]) - AREA_OFFSET;
            int z = (int) Math.floor(RETURN.get()[2]);
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos area = new BlockPos(x, y, z);
            player.teleportTo(level, area.getX() + 0.5, area.getY(), area.getZ() + 6.5, Set.of(), 180.0F, 20.0F, true);
            discard(server);
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
            stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
            drone.loadFromStack(stack);
            Vec3 pos = new Vec3(area.getX() + 0.5, area.getY(), area.getZ() + 0.5);
            drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
            level.addFreshEntity(drone);
            drone.resetTo(pos, new Quaternionf().rotationY((float) Math.toRadians(-180.0) + (float) Math.PI));
            DRONE.set(drone.getId());
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.getInventory().setItem(0, remote);
            player.getInventory().setSelectedSlot(0);
            player.connection.send(new ClientboundSetHeldSlotPacket(0));
            if (fpv) {
                ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_DIGITAL).map(ItemStack::new).orElse(ItemStack.EMPTY);
                goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                player.setItemSlot(EquipmentSlot.HEAD, goggles);
            } else {
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
            DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, fpv ? PilotMode.FPV : PilotMode.LINE_OF_SIGHT);
            if (result != DronePiloting.StartResult.STARTED) {
                throw new IllegalStateException("Start verweigert: " + result);
            }
        });
    }

    static void discard(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                drone.discard();
            }
        }
    }

    static void returnPlayer(MinecraftServer server) {
        double[] back = RETURN.getAndSet(null);
        ServerPlayer player = player(server);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.getInventory().setItem(0, ItemStack.EMPTY);
        if (back != null) {
            player.teleportTo(player.level(), back[0], back[1], back[2], Set.of(), (float) back[3], (float) back[4], true);
        }
    }

    static Zone floatDraft(MinecraftServer server, String name, boolean raceLegal) {
        ServerLevel level = player(server).level();
        if (!(level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("keine Testdrohne");
        }
        return Zone.create(ZoneServer.dimension(level), name, ZoneShape.BOX, ZoneBox.around(Math.floor(drone.getX()) + 0.5, Math.floor(drone.getY()) - 2.0,
                        Math.floor(drone.getZ()) + 0.5, 12.0, 40.0, 12.0))
                .withRaceLegal(raceLegal).withTransition(10).withPriority(1)
                .withOverride(WorldRuleSpecs.GRAVITY_SCALE, 0).withOverride(WorldRuleSpecs.WIND, 0).withOverride(WorldRuleSpecs.BATTERY_SCALE, 0);
    }

    static Zone floatZone(MinecraftServer server, boolean raceLegal) {
        Zone zone = floatDraft(server, FLOAT, raceLegal);
        Optional<Zone> existing = zone(server, FLOAT);
        return save(server, existing.map(old -> zone.withId(old.id())).orElse(zone));
    }

    private static double gravityScale() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        return sim == null ? Double.NaN : sim.tuning().gravityScale;
    }

    private static double charge() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        return sim == null ? Double.NaN : sim.telemetry().batteryCharge;
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, server -> {
                    removeTestZones(server);
                    return null;
                });
                state.put("start", startFlight(ctx, true));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                CompletableFuture<?> start = (CompletableFuture<?>) state.get("start");
                if (start.isCompletedExceptionally()) {
                    return StepResult.fail("Flugstart: " + start.exceptionNow());
                }
                if (!start.isDone() || !session.isActive() || session.sim() == null || since(ctx) < 20) {
                    return since(ctx) > 300 ? StepResult.fail("FPV-Sitzung startet nicht") : StepResult.pending();
                }
                if (gravityScale() != 1.0 || session.physicsModified()) {
                    return StepResult.fail("vor der Zone schon veränderte Physik: " + gravityScale());
                }
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                state.put("shown", ZoneHud.shown());
                server(ctx, server -> {
                    floatZone(server, true);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                double g = gravityScale();
                if (g > 0.0 && g < 1.0) {
                    state.put("blended", g);
                }
                if (g != 0.0) {
                    return since(ctx) > 120 ? StepResult.fail("Schwerkraft erreicht in der Zone nicht 0: " + g) : StepResult.pending();
                }
                if (!state.containsKey("blended")) {
                    return StepResult.fail("Schwerkraft sprang ohne Übergang auf 0");
                }
                if (since(ctx) < 12) {
                    return StepResult.fail("Übergang zu kurz: " + since(ctx) + " Ticks für 1 s");
                }
                if (session.physicsModified()) {
                    return StepResult.fail("rennfreie Zone meldet veränderte Physik");
                }
                QuadcopterEntity drone = session.drone();
                if (drone == null) {
                    return StepResult.fail("Drohne weg");
                }
                AssistReasons live = AssistText.evaluate(session, drone);
                if (live.has(LapAssist.ZONE_PHYSICS) || live.has(LapAssist.WORLD_PHYSICS)) {
                    return StepResult.fail("rennfreie Zone zählt als Flughilfe: " + live);
                }
                ZoneHud.Notice notice = ZoneHud.notice();
                if ((int) state.get("shown") >= ZoneHud.shown() || notice == null || !notice.title().getString().contains(FLOAT)) {
                    return StepResult.fail("kein OSD-Hinweis beim Einfliegen: " + notice);
                }
                if (!session.isFpv()) {
                    return StepResult.fail("Sitzung nicht im FPV");
                }
                ctx.log("Übergang auf Schwerelos: Zwischenwert " + state.get("blended") + " nach " + since(ctx) + " Ticks bei 0");
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                server(ctx, server -> {
                    floatZone(server, false);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                Zone zone = client(FLOAT);
                if (zone == null || zone.raceLegal() || !session.physicsModified()) {
                    return since(ctx) > 100 ? StepResult.fail("Zone ohne Rennfreigabe meldet keine veränderte Physik") : StepResult.pending();
                }
                QuadcopterEntity drone = session.drone();
                if (drone == null) {
                    return StepResult.fail("Drohne weg");
                }
                AssistReasons live = AssistText.evaluate(session, drone);
                List<AssistDetail> details = live.details(LapAssist.ZONE_PHYSICS);
                String text = AssistWords.describe(live).getString();
                if (details.isEmpty() || details.getFirst().reference() != zone.id() || !text.contains(FLOAT) || live.has(LapAssist.WORLD_PHYSICS)) {
                    return StepResult.fail("Flughilfe-Grund nennt die Zone nicht: " + live + " / " + text);
                }
                ctx.log("Flughilfe-Grund: " + text);
                int droneId = drone.getId();
                int zoneId = zone.id();
                server(ctx, server -> {
                    if (!(player(server).level().getEntity(droneId) instanceof QuadcopterEntity served)) {
                        return "Server kennt die Drohne nicht";
                    }
                    AssistReasons reasons = RaceServer.serverReasons(served);
                    List<AssistDetail> own = reasons.details(LapAssist.ZONE_PHYSICS);
                    if (own.isEmpty() || own.getFirst().reference() != zoneId || reasons.has(LapAssist.WORLD_PHYSICS)) {
                        return "Server erkennt die Zone nicht als Flughilfe: " + reasons;
                    }
                    Zone battery = zone(server, FLOAT).orElseThrow().without(WorldRuleSpecs.GRAVITY_SCALE).without(WorldRuleSpecs.WIND);
                    save(server, battery);
                    return null;
                });
                state.remove("blended");
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                double g = gravityScale();
                if (g > 0.0 && g < 1.0) {
                    state.put("blended", g);
                }
                if (g != 1.0) {
                    return since(ctx) > 120 ? StepResult.fail("Schwerkraft kehrt ohne Zonenwert nicht zurück: " + g) : StepResult.pending();
                }
                if (!state.containsKey("blended")) {
                    return StepResult.fail("Schwerkraft sprang ohne Übergang zurück");
                }
                if (!session.physicsModified()) {
                    return StepResult.fail("unendlicher Akku ohne Rennfreigabe zählt nicht als Flughilfe");
                }
                session.setTestInput(StickPositions.IDLE, true, StabMode.ANGLE);
                next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                DroneSim sim = session.sim();
                if (sim == null || !sim.telemetry().armed) {
                    return since(ctx) > 100 ? StepResult.fail("Drohne lässt sich zum Akku-Test nicht schärfen") : StepResult.pending();
                }
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                if (!state.containsKey("charge")) {
                    state.put("charge", charge());
                    state.put("chargeTick", ctx.tick());
                    return StepResult.pending();
                }
                if (ctx.tick() - (int) state.get("chargeTick") < 40) {
                    return StepResult.pending();
                }
                double before = (double) state.get("charge");
                if (charge() != before) {
                    return StepResult.fail("Akku sinkt trotz unendlichem Akku in der Zone: " + before + " -> " + charge());
                }
                server(ctx, server -> {
                    zone(server, FLOAT).ifPresent(zone -> ZoneServer.delete(server, zone.id(), Component.literal("Selbsttest"), "Selbsttest"));
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            case 7 -> {
                if (client(FLOAT) != null || session.physicsModified()) {
                    return since(ctx) > 100 ? StepResult.fail("Zone verschwindet beim Client nicht") : StepResult.pending();
                }
                double before = (double) state.get("charge");
                double now = charge();
                if (now > before || before - now > 0.002) {
                    return StepResult.fail("Akku springt beim Verlassen der Zone: " + before + " -> " + now);
                }
                ZoneHud.Notice notice = ZoneHud.notice();
                if (notice == null || !notice.title().getString().contains(FLOAT)) {
                    return StepResult.fail("kein OSD-Hinweis beim Verlassen: " + notice);
                }
                state.put("charge", now);
                next(ctx);
                return StepResult.pending();
            }
            case 8 -> {
                if (since(ctx) < 60) {
                    return StepResult.pending();
                }
                if (!(charge() < (double) state.get("charge"))) {
                    return StepResult.fail("Akku entlädt sich nach der Zone nicht weiter: " + state.get("charge") + " -> " + charge());
                }
                ctx.log("Akku nach der Zone: " + state.get("charge") + " -> " + charge());
                session.clearTestInput();
                session.requestStop();
                server(ctx, server -> {
                    discard(server);
                    returnPlayer(server);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                return PilotSession.INSTANCE.isActive() ? StepResult.pending() : StepResult.pass();
            }
        }
    }

    private static StepResult editorStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        if (PilotSession.INSTANCE.isActive()) {
            PilotSession.INSTANCE.clearTestInput();
            PilotSession.INSTANCE.requestStop();
            return since(ctx) > 200 ? StepResult.fail("Flugsitzung endet nicht") : StepResult.pending();
        }
        switch (phase(ctx)) {
            case 0 -> {
                if (!ZoneEditor.startNew()) {
                    return StepResult.fail("Editor startet nicht");
                }
                ZoneEditor editor = ZoneEditor.current();
                if (editor == null || !editor.model().isNew()) {
                    return StepResult.fail("kein neuer Entwurf");
                }
                double width = editor.draft().box().sizeX();
                editor.change(ZoneDraft.Row.of(ZoneDraft.Kind.WIDTH), 1);
                editor.change(new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.GRAVITY_SCALE), -1);
                editor.change(ZoneDraft.Row.of(ZoneDraft.Kind.SHAPE), 1);
                editor.model().setName(EDITOR);
                if (editor.draft().box().sizeX() != width + ZoneDraft.SIZE_STEP || editor.draft().shape() != ZoneShape.CYLINDER
                        || editor.draft().value(WorldRuleSpecs.GRAVITY_SCALE, -1) != 90) {
                    return StepResult.fail("Entwurf falsch geändert: " + editor.draft());
                }
                if (!ZoneEditScreen.openSticky() || !(mc.gui.screen() instanceof ZoneEditScreen)) {
                    return StepResult.fail("Mauszeiger-Fenster öffnet nicht");
                }
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (!(mc.gui.screen() instanceof ZoneEditScreen screen) || screen.panel() == null || screen.nameField() == null) {
                    return since(ctx) > 40 ? StepResult.fail("Editor-Fenster ohne Bedienfeld") : StepResult.pending();
                }
                if (!EDITOR.equals(screen.nameField().getValue())) {
                    return StepResult.fail("Namensfeld zeigt nicht den Entwurf: " + screen.nameField().getValue());
                }
                ZoneEditor editor = ZoneEditor.current();
                if (editor == null || editor.problem() != null || !editor.apply()) {
                    return StepResult.fail("Übernehmen fehlgeschlagen: " + (editor == null ? "kein Editor" : editor.problem()));
                }
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                Zone zone = client(EDITOR);
                if (zone == null) {
                    return since(ctx) > 200 ? StepResult.fail("neue Zone kommt nicht an") : StepResult.pending();
                }
                if (ZoneEditor.active() || mc.gui.screen() instanceof ZoneEditScreen) {
                    return StepResult.fail("Editor nach dem Übernehmen noch offen");
                }
                if (zone.shape() != ZoneShape.CYLINDER || zone.value(WorldRuleSpecs.GRAVITY_SCALE, -1) != 90) {
                    return StepResult.fail("Zone vom Editor falsch gespeichert: " + zone);
                }
                ZoneEditor.startNew();
                ZoneEditor dup = ZoneEditor.current();
                if (dup == null) {
                    return StepResult.fail("zweiter Entwurf startet nicht");
                }
                dup.model().setName(EDITOR.toUpperCase(Locale.ROOT));
                if (!ZoneCheck.NAME_TAKEN.equals(dup.problem()) || dup.apply()) {
                    return StepResult.fail("doppelter Name nicht abgelehnt: " + dup.problem());
                }
                dup.cancel();
                if (!ZoneEditor.start(zone)) {
                    return StepResult.fail("vorhandene Zone lässt sich nicht bearbeiten");
                }
                ZoneEditor editor = ZoneEditor.current();
                if (editor == null || editor.delete()) {
                    return StepResult.fail("Löschen ohne Rückfrage");
                }
                if (!editor.deleteArmed() || !editor.delete()) {
                    return StepResult.fail("zweites Löschen sendet nicht");
                }
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                if (client(EDITOR) != null) {
                    return since(ctx) > 200 ? StepResult.fail("gelöschte Zone bleibt") : StepResult.pending();
                }
                return StepResult.pass();
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (phase(ctx) == 0) {
            ZoneEditor.stop();
            PilotSession.INSTANCE.clearTestInput();
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
            }
            server(ctx, server -> {
                removeTestZones(server);
                discard(server);
                returnPlayer(server);
                return null;
            });
            next(ctx);
            return StepResult.pending();
        }
        List<String> left = new ArrayList<>();
        for (Zone zone : ClientZones.INSTANCE.zones()) {
            if (zone.name().startsWith(PREFIX)) {
                left.add(zone.name());
            }
        }
        if (!left.isEmpty()) {
            return since(ctx) > 200 ? StepResult.fail("Testzonen bleiben: " + left) : StepResult.pending();
        }
        return StepResult.pass();
    }
}
