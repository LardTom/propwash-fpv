package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.CoreConfig;
import de.lardtom.propwash.core.DroneBuildCodecs;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.core.PartRef;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.core.event.Event;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.network.SelfTestNet;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegList;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.registry.client.HudVisibility;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class CoreSelfTests {
    private static final AtomicInteger FRAMES = new AtomicInteger();
    private static final AtomicInteger CLIENT_TICKS = new AtomicInteger();
    private static final AtomicInteger CLIENT_LOGINS = new AtomicInteger();
    private static final AtomicInteger SERVER_TICKS = new AtomicInteger();
    private static final AtomicInteger SERVER_STARTS = new AtomicInteger();
    private static final AtomicInteger PLAYER_JOINS = new AtomicInteger();
    private static final AtomicInteger COMMAND_REGISTRATIONS = new AtomicInteger();
    private static final AtomicInteger HUD_CALLS = new AtomicInteger();
    private static final AtomicInteger ALWAYS_HUD_CALLS = new AtomicInteger();
    private static final AtomicInteger KEYS_REGISTERED_EVENTS = new AtomicInteger();
    private static final AtomicInteger CLIENT_DISCONNECTS = new AtomicInteger();
    private static final AtomicInteger PLAYER_LEAVES = new AtomicInteger();
    private static final AtomicInteger SERVER_STOPS = new AtomicInteger();
    private static final AtomicInteger LEVEL_UNLOADS = new AtomicInteger();
    private static final AtomicInteger GEOMETRY_CALLS = new AtomicInteger();
    private static final List<String> THREAD_VIOLATIONS = new CopyOnWriteArrayList<>();
    private static final List<String> SESSION_VIOLATIONS = new CopyOnWriteArrayList<>();
    private static final List<String> PILOT_EVENTS = new CopyOnWriteArrayList<>();
    private static volatile int lastPong = Integer.MIN_VALUE;
    private static volatile String lateRegistration = "nicht geprüft";
    private static KeyMapping selfTestKey;

    private CoreSelfTests() {
    }

    public static void onPong(SelfTestNet.PongS2C payload, ClientNet.ClientPayloadContext ctx) {
        if (SelfTest.enabled() && ctx.minecraft().isSameThread()) {
            lastPong = payload.nonce();
        }
    }

    public static void register() {
        lateRegistration = lateRegistrationAccepted();
        ClientBus.FRAME_START.register((mc, dt) -> {
            clientThread(mc, ClientBus.FRAME_START);
            FRAMES.incrementAndGet();
        });
        ClientBus.TICK_END.register(mc -> {
            clientThread(mc, ClientBus.TICK_END);
            CLIENT_TICKS.incrementAndGet();
        });
        ClientBus.LOGIN.register(mc -> {
            clientThread(mc, ClientBus.LOGIN);
            if (CLIENT_LOGINS.get() != CLIENT_DISCONNECTS.get()) {
                SESSION_VIOLATIONS.add("LOGIN ohne vorheriges DISCONNECT");
            }
            if (mc.player == null || mc.level == null) {
                SESSION_VIOLATIONS.add("LOGIN ohne Spieler/Level");
            }
            CLIENT_LOGINS.incrementAndGet();
        });
        ClientBus.DISCONNECT.register(mc -> {
            clientThread(mc, ClientBus.DISCONNECT);
            if (CLIENT_DISCONNECTS.get() + 1 != CLIENT_LOGINS.get()) {
                SESSION_VIOLATIONS.add("DISCONNECT ohne vorheriges LOGIN (logins=" + CLIENT_LOGINS.get() + ", disconnects=" + CLIENT_DISCONNECTS.get() + ")");
            }
            if (mc.player == null || mc.level == null) {
                SESSION_VIOLATIONS.add("DISCONNECT ohne Spieler/Level");
            }
            CLIENT_DISCONNECTS.incrementAndGet();
        });
        ClientBus.KEY_MAPPINGS_REGISTERED.register(list -> KEYS_REGISTERED_EVENTS.incrementAndGet());
        ClientBus.SUBMIT_WORLD_GEOMETRY.register((collector, poseStack, levelState) -> {
            clientThread(Minecraft.getInstance(), ClientBus.SUBMIT_WORLD_GEOMETRY);
            GEOMETRY_CALLS.incrementAndGet();
        });
        ServerBus.TICK_END.register(server -> {
            serverThread(server, ServerBus.TICK_END);
            SERVER_TICKS.incrementAndGet();
        });
        ServerBus.STARTED.register(server -> {
            serverThread(server, ServerBus.STARTED);
            SERVER_STARTS.incrementAndGet();
        });
        ServerBus.STOPPED.register(server -> {
            serverThread(server, ServerBus.STOPPED);
            SERVER_STOPS.incrementAndGet();
        });
        ServerBus.PLAYER_JOIN.register(player -> {
            serverThread(player.level().getServer(), ServerBus.PLAYER_JOIN);
            PLAYER_JOINS.incrementAndGet();
        });
        ServerBus.PLAYER_LEAVE.register(player -> {
            serverThread(player.level().getServer(), ServerBus.PLAYER_LEAVE);
            PLAYER_LEAVES.incrementAndGet();
        });
        ServerBus.LEVEL_UNLOAD.register(level -> {
            serverThread(level.getServer(), ServerBus.LEVEL_UNLOAD);
            LEVEL_UNLOADS.incrementAndGet();
        });
        ServerBus.REGISTER_COMMANDS.register((dispatcher, context, selection) -> COMMAND_REGISTRATIONS.incrementAndGet());
        QuadcopterEntity.PILOT_CHANGED.register((drone, previous, current) -> {
            if (drone.level() instanceof ServerLevel level) {
                serverThread(level.getServer(), QuadcopterEntity.PILOT_CHANGED);
            } else {
                recordViolation(QuadcopterEntity.PILOT_CHANGED.name() + " auf dem Client");
            }
            PILOT_EVENTS.add(drone.getId() + ":" + (previous.isPresent() ? "pilot" : "leer") + ">" + (current.isPresent() ? "pilot" : "leer"));
        });

        selfTestKey = ClientRegistrar.keyMapping("selftest", InputConstants.UNKNOWN.getValue());
        ClientRegistrar.hudLayer(Constants.id("selftest"), (graphics, deltaTracker) -> {
            HUD_CALLS.incrementAndGet();
            if (!Screenshots.markerHidden()) {
                graphics.text(Minecraft.getInstance().font, "Propwash Selbsttest", 4, 4, 0xFFFFFFFF);
            }
        });
        ClientRegistrar.hudLayer(Constants.id("selftest_always"), (graphics, deltaTracker) -> ALWAYS_HUD_CALLS.incrementAndGet(),
                HudVisibility.ALWAYS);

        SelfTest.clientStep("core: Registrierung", 1, ctx -> {
            if (BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Constants.id("drone_build")) != CoreComponents.DRONE_BUILD.get()) {
                return StepResult.fail("propwash:drone_build fehlt");
            }
            if (BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Constants.id("battery_charge")) != CoreComponents.BATTERY_CHARGE.get()) {
                return StepResult.fail("propwash:battery_charge fehlt");
            }
            if (BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Constants.id("part")) != CoreComponents.PART.get()) {
                return StepResult.fail("propwash:part fehlt");
            }
            if (BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Constants.id("flight_tune")) != CoreComponents.FLIGHT_TUNE.get()) {
                return StepResult.fail("propwash:flight_tune fehlt");
            }
            if (BuiltInRegistries.ENTITY_TYPE.getValue(Constants.id("drone")) != DroneModule.DRONE.get()) {
                return StepResult.fail("Entity-Typ propwash:drone fehlt");
            }
            if (BuiltInRegistries.ENTITY_TYPE.getValue(Constants.id("gate")) != RaceModule.GATE.get()) {
                return StepResult.fail("Entity-Typ propwash:gate fehlt");
            }
            List<RegList<?>> order = PropwashRegistries.inFlushOrder();
            if (order.indexOf(PropwashRegistries.DATA_COMPONENTS) > order.indexOf(PropwashRegistries.ITEMS)
                    || order.indexOf(PropwashRegistries.BLOCKS) > order.indexOf(PropwashRegistries.ITEMS)
                    || order.indexOf(PropwashRegistries.SOUNDS) > order.indexOf(PropwashRegistries.BLOCKS)) {
                return StepResult.fail("Flush-Reihenfolge falsch: " + order.stream().map(l -> l.registryKey().identifier().toString()).toList());
            }
            for (RegList<?> list : PropwashRegistries.inFlushOrder()) {
                if (!list.isFlushed()) {
                    return StepResult.fail("Register-Liste " + list.registryKey().identifier() + " wurde nie geflusht");
                }
            }
            if (EntityDataSerializers.getSerializedId(CoreComponents.DRONE_BUILD_SERIALIZER) < 0
                    || EntityDataSerializers.getSerializedId(CoreComponents.FLIGHT_TUNE_SERIALIZER) < 0) {
                return StepResult.fail("Entity-Data-Serializer propwash:drone_build/flight_tune nicht registriert");
            }
            return StepResult.check(lateRegistration == null, "In der Client-init(): " + lateRegistration);
        });

        SelfTest.clientStep("core: Codecs", 1, ctx -> {
            for (DroneBuild build : DroneBuild.PRESETS) {
                JsonElement json = DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, build).getOrThrow();
                DroneBuild back = DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                if (!build.equals(back)) {
                    return StepResult.fail("DroneBuild-Codec: " + json);
                }
            }
            for (PartCategory category : PartCategory.values()) {
                for (var part : category.parts()) {
                    PartRef ref = PartRef.of(part);
                    PartRef back = PartRef.CODEC.parse(JsonOps.INSTANCE, PartRef.CODEC.encodeStart(JsonOps.INSTANCE, ref).getOrThrow()).getOrThrow();
                    if (!ref.equals(back) || back.resolve().orElse(null) != part || category.get(category.with(DroneBuild.FREESTYLE, part)) != part) {
                        return StepResult.fail("PartRef/PartCategory: " + ref);
                    }
                }
            }
            Minecraft mc = Minecraft.getInstance();
            ItemStack stack = new ItemStack(Items.STICK);
            stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.RACER);
            stack.set(CoreComponents.BATTERY_CHARGE.get(), 0.25F);
            stack.set(CoreComponents.PART.get(), PartRef.of(DroneBuild.RACER.motor()));
            stack.set(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY.with("rate.roll.rc", 1.2F).with("pid.roll.p", 45.0F));
            DynamicOps<JsonElement> ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            JsonElement json = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
            ItemStack back = ItemStack.CODEC.parse(ops, json).getOrThrow();
            return StepResult.check(ItemStack.matches(stack, back), "ItemStack mit Propwash-Komponenten: " + json);
        });

        SelfTest.clientStep("core: Events", 100, ctx -> {
            boolean ok = FRAMES.get() > 0 && CLIENT_TICKS.get() > 0 && CLIENT_LOGINS.get() > 0 && SERVER_TICKS.get() > 0
                    && SERVER_STARTS.get() > 0 && PLAYER_JOINS.get() > 0 && COMMAND_REGISTRATIONS.get() > 0;
            if (ok) {
                ctx.log("frames=" + FRAMES.get() + " clientTicks=" + CLIENT_TICKS.get() + " serverTicks=" + SERVER_TICKS.get()
                        + " logins=" + CLIENT_LOGINS.get() + " joins=" + PLAYER_JOINS.get() + " commands=" + COMMAND_REGISTRATIONS.get());
                return StepResult.pass();
            }
            if (ctx.tick() == 99) {
                return StepResult.fail("frames=" + FRAMES.get() + " clientTicks=" + CLIENT_TICKS.get() + " serverTicks=" + SERVER_TICKS.get()
                        + " starts=" + SERVER_STARTS.get() + " logins=" + CLIENT_LOGINS.get() + " joins=" + PLAYER_JOINS.get()
                        + " commands=" + COMMAND_REGISTRATIONS.get());
            }
            return StepResult.pending();
        });

        SelfTest.clientStep("core: Netzwerk Ping/Pong", 100, ctx -> {
            int nonce = 4711;
            if (ctx.tick() == 0) {
                if (!ClientNet.canSend(SelfTestNet.PingC2S.TYPE)) {
                    return StepResult.fail("Server kennt den Ping-Kanal nicht");
                }
                ClientNet.sendToServer(new SelfTestNet.PingC2S(nonce));
            }
            return lastPong == nonce ? StepResult.pass() : StepResult.pending();
        });

        SelfTest.clientStep("core: Drohne spawnen/entfernen", 200, CoreSelfTests::droneStep);

        SelfTest.clientStep("core: Tasten & HUD", 100, ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (!Arrays.asList(mc.options.keyMappings).contains(selfTestKey)) {
                return StepResult.fail("Tastenbelegung key.propwash.selftest nicht in den Optionen");
            }
            if (selfTestKey.getCategory() != ClientRegistrar.keyCategory()) {
                return StepResult.fail("falsche Tasten-Kategorie");
            }
            if (KEYS_REGISTERED_EVENTS.get() != 1) {
                return StepResult.fail("KEY_MAPPINGS_REGISTERED " + KEYS_REGISTERED_EVENTS.get() + "× gefeuert");
            }
            return HUD_CALLS.get() > 0 ? StepResult.pass() : StepResult.pending();
        });

        SelfTest.clientStep("core: HUD bei F1", 100, CoreSelfTests::hiddenHudStep);

        SelfTest.clientStep("core: Config", 1, ctx -> StepResult.check(CoreConfig.CONFIG.isLoaded() && Files.isRegularFile(CoreConfig.CONFIG.path()),
                "config/propwash/core.json fehlt"));

        SelfTest.clientStep("core: Threads und Sitzung", 1, ctx -> {
            if (GEOMETRY_CALLS.get() == 0) {
                return StepResult.fail("SUBMIT_WORLD_GEOMETRY nie gefeuert");
            }
            if (CLIENT_LOGINS.get() != 1 || CLIENT_DISCONNECTS.get() != 0 || !ClientBus.inSession()) {
                return StepResult.fail("logins=" + CLIENT_LOGINS.get() + " disconnects=" + CLIENT_DISCONNECTS.get() + " inSession=" + ClientBus.inSession());
            }
            return violations();
        });

        SelfTest.afterLeaveStep("core: Events nach dem Verlassen", 40, ctx -> {
            if (CLIENT_DISCONNECTS.get() == 0 || SERVER_STOPS.get() == 0) {
                return ctx.tick() >= 39 ? StepResult.fail("disconnects=" + CLIENT_DISCONNECTS.get() + " stops=" + SERVER_STOPS.get())
                        : StepResult.pending();
            }
            String counts = "logins=" + CLIENT_LOGINS.get() + " disconnects=" + CLIENT_DISCONNECTS.get() + " joins=" + PLAYER_JOINS.get()
                    + " leaves=" + PLAYER_LEAVES.get() + " starts=" + SERVER_STARTS.get() + " stops=" + SERVER_STOPS.get()
                    + " levelUnloads=" + LEVEL_UNLOADS.get();
            ctx.log(counts);
            if (CLIENT_LOGINS.get() != 1 || CLIENT_DISCONNECTS.get() != 1 || PLAYER_JOINS.get() != 1 || PLAYER_LEAVES.get() != 1
                    || SERVER_STARTS.get() != 1 || SERVER_STOPS.get() != 1 || LEVEL_UNLOADS.get() == 0 || ClientBus.inSession()) {
                return StepResult.fail(counts + " inSession=" + ClientBus.inSession());
            }
            return violations();
        });
    }

    private static String lateRegistrationAccepted() {
        if (!PropwashRegistries.isClosedAfterCommonInit()) {
            return "Register nach PropwashCommon.init() nicht geschlossen";
        }
        String late = rejected("SoundEvent", () -> PropwashRegistries.SOUNDS.add("selftest_late", key -> SoundEvent.createVariableRangeEvent(key.identifier())));
        if (late == null) {
            late = rejected("Payload", () -> {
                PropwashNet.registerS2C(new CustomPacketPayload.Type<SelfTestNet.PongS2C>(Constants.id("selftest_late")), SelfTestNet.PongS2C.CODEC);
                return null;
            });
        }
        if (late == null) {
            late = rejected("Entity-Data-Serializer", () -> PropwashRegistries.entityDataSerializer("selftest_late",
                    EntityDataSerializer.forValueType(ByteBufCodecs.VAR_INT)));
        }
        return late == null ? null : late + " nach PropwashCommon.init() wurde angenommen";
    }

    private static String rejected(String what, Supplier<?> registration) {
        try {
            registration.get();
            return what;
        } catch (IllegalStateException expected) {
            return null;
        }
    }

    private static StepResult violations() {
        if (!THREAD_VIOLATIONS.isEmpty()) {
            return StepResult.fail("falscher Thread: " + String.join(", ", THREAD_VIOLATIONS));
        }
        if (!SESSION_VIOLATIONS.isEmpty()) {
            return StepResult.fail("Sitzungsfolge: " + String.join(", ", SESSION_VIOLATIONS));
        }
        return StepResult.pass();
    }

    private static void clientThread(Minecraft mc, Event<?> event) {
        if (!mc.isSameThread()) {
            recordViolation(event.name() + " auf " + Thread.currentThread().getName());
        }
    }

    private static void serverThread(MinecraftServer server, Event<?> event) {
        if (!server.isSameThread()) {
            recordViolation(event.name() + " auf " + Thread.currentThread().getName());
        }
    }

    private static void recordViolation(String violation) {
        if (!THREAD_VIOLATIONS.contains(violation)) {
            THREAD_VIOLATIONS.add(violation);
            Constants.LOG.error("[Selbsttest] {}", violation);
        }
    }

    private static final String DRONE_ID = "droneId";
    private static final String SPAWN = "spawn";
    private static final String REMOVE = "remove";
    private static final Quaternionf TEST_ORIENTATION = new Quaternionf().rotationXYZ(0.3F, 1.1F, -0.4F);
    private static final FlightTune TEST_TUNE = FlightTune.EMPTY.with("rate.roll.rc", 1.1F).with("pid.yaw.p", 38.0F);

    private static Map<String, Float> withoutServerKeys(FlightTune tune) {
        Map<String, Float> values = new TreeMap<>(tune.values());
        values.keySet().removeIf(key -> key.startsWith("vtx."));
        return values;
    }

    private static StepResult hiddenHudStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            return StepResult.pending();
        }
        if (!mc.gui.hud.isHidden()) {
            return StepResult.fail("HUD wurde während des Schritts wieder eingeblendet");
        }
        if (ctx.tick() == 5) {
            ctx.state().put("always", ALWAYS_HUD_CALLS.get());
            ctx.state().put("normal", HUD_CALLS.get());
            return StepResult.pending();
        }
        if (ctx.tick() < 25) {
            return StepResult.pending();
        }
        int always = ALWAYS_HUD_CALLS.get() - (Integer) ctx.state().get("always");
        int normal = HUD_CALLS.get() - (Integer) ctx.state().get("normal");
        mc.gui.hud.toggle();
        if (always <= 0) {
            return StepResult.fail("HUD-Ebene ALWAYS bei F1 nicht gezeichnet");
        }
        return StepResult.check(normal == 0, "HUD-Ebene WITH_HUD trotz F1 " + normal + "× gezeichnet");
    }

    private static StepResult droneStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            AtomicInteger id = new AtomicInteger(-1);
            ctx.state().put(DRONE_ID, id);
            ctx.state().put(SPAWN, ctx.onServer(server -> {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    throw new IllegalStateException("kein Spieler auf dem Server");
                }
                ServerPlayer player = players.getFirst();
                ServerLevel level = player.level();
                QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                if (drone == null) {
                    throw new IllegalStateException("EntityType.create lieferte null");
                }
                Vec3 pos = player.position().add(0.0, 1.5, 2.0);
                drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
                drone.setBuild(DroneBuild.RACER);
                drone.setBatteryCharge(0.5F);
                drone.setAttitude(TEST_ORIENTATION);
                drone.setArmed(true);
                drone.setMotorOutput(0.75F);
                drone.setTune(TEST_TUNE);
                checkStackTransfer(level);
                drone.setPilot(player);
                if (!level.addFreshEntity(drone)) {
                    throw new IllegalStateException("addFreshEntity fehlgeschlagen");
                }
                id.set(drone.getId());
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> spawn = (CompletableFuture<?>) ctx.state().get(SPAWN);
        if (spawn.isCompletedExceptionally()) {
            return StepResult.fail("Spawn auf dem Server fehlgeschlagen: " + spawn.exceptionNow().getMessage());
        }
        int id = ((AtomicInteger) ctx.state().get(DRONE_ID)).get();
        if (id < 0 || mc.level == null) {
            return StepResult.pending();
        }
        CompletableFuture<?> remove = (CompletableFuture<?>) ctx.state().get(REMOVE);
        Entity entity = mc.level.getEntity(id);
        if (remove == null) {
            if (!(entity instanceof QuadcopterEntity drone)) {
                return StepResult.pending();
            }
            boolean synced = drone.build().equals(DroneBuild.RACER)
                    && Math.abs(drone.batteryCharge() - 0.5F) < 1.0E-6F
                    && drone.orientation().equals(TEST_ORIENTATION, 1.0E-5F)
                    && drone.isArmed()
                    && Math.abs(drone.motorOutput() - 0.75F) < 1.0E-6F
                    && withoutServerKeys(drone.tune()).equals(TEST_TUNE.values())
                    && drone.pilot().filter(uuid -> uuid.equals(mc.player.getUUID())).isPresent();
            if (!synced) {
                return ctx.tick() > 100 ? StepResult.fail("Synchronisierte Daten falsch: build=" + drone.build() + " akku=" + drone.batteryCharge()
                        + " lage=" + drone.orientation() + " armed=" + drone.isArmed() + " pilot=" + drone.pilot() + " tune=" + drone.tune())
                        : StepResult.pending();
            }
            ctx.log("Drohne " + id + " auf dem Client sichtbar, Daten synchron");
            ctx.state().put(REMOVE, ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity e = level.getEntity(id);
                    if (e != null) {
                        e.discard();
                    }
                }
            }));
            return StepResult.pending();
        }
        if (remove.isCompletedExceptionally()) {
            return StepResult.fail("Entfernen auf dem Server fehlgeschlagen");
        }
        if (entity != null || !remove.isDone()) {
            return StepResult.pending();
        }
        List<String> expected = List.of(id + ":leer>pilot", id + ":pilot>leer");
        List<String> events = PILOT_EVENTS.stream().filter(e -> e.startsWith(id + ":")).toList();
        return StepResult.check(events.equals(expected), "PILOT_CHANGED: erwartet " + expected + ", bekommen " + events);
    }

    private static void checkStackTransfer(ServerLevel level) {
        QuadcopterEntity probe = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (probe == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        ItemStack source = new ItemStack(Items.STICK);
        source.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.RACER);
        source.set(CoreComponents.BATTERY_CHARGE.get(), 0.3F);
        source.set(CoreComponents.FLIGHT_TUNE.get(), TEST_TUNE);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Selbsttest"));
        probe.loadFromStack(source);
        if (!probe.build().equals(DroneBuild.RACER) || Math.abs(probe.batteryCharge() - 0.3F) > 1.0E-6F || !probe.tune().equals(TEST_TUNE)
                || probe.getCustomName() == null || !"Selbsttest".equals(probe.getCustomName().getString())) {
            throw new IllegalStateException("loadFromStack übernimmt die Komponenten nicht");
        }
        probe.loadFromStack(new ItemStack(Items.STICK));
        if (!probe.build().equals(DroneBuild.FREESTYLE) || probe.batteryCharge() != 1.0F || !probe.tune().equals(FlightTune.EMPTY)
                || probe.hasCustomName()) {
            throw new IllegalStateException("loadFromStack ohne Komponenten setzt keine Standardwerte");
        }
        probe.loadFromStack(source);
        ItemStack back = probe.toStack();
        if (ContentIds.item(ContentIds.DRONE).isEmpty()) {
            if (!back.isEmpty()) {
                throw new IllegalStateException("toStack ohne Drohnen-Item muss ItemStack.EMPTY liefern");
            }
        } else if (!ContentIds.is(back, ContentIds.DRONE) || !DroneBuild.RACER.equals(back.get(CoreComponents.DRONE_BUILD.get()))
                || !TEST_TUNE.equals(back.get(CoreComponents.FLIGHT_TUNE.get()))
                || Math.abs(back.getOrDefault(CoreComponents.BATTERY_CHARGE.get(), -1.0F) - 0.3F) > 1.0E-6F
                || back.get(DataComponents.CUSTOM_NAME) == null) {
            throw new IllegalStateException("toStack überträgt die Komponenten nicht: " + back.getComponents());
        }
        if (ContentIds.gogglesSystem(source).isPresent() || ContentIds.isGoggles(source)) {
            throw new IllegalStateException("gogglesSystem erkennt einen Stock als Brille");
        }
        probe.discard();
    }
}
