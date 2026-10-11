package de.lardtom.propwash.drone.client.trail;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.render.DroneRenderConfigTab;
import de.lardtom.propwash.drone.led.LedColor;
import de.lardtom.propwash.drone.led.LedMode;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.drone.trail.TrailColor;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.search.SearchBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class TrailSelfTests {
    static final int PAINT_RED = 0xD02020;
    static final LedColor LED = LedColor.GREEN;
    private static final String FUTURE = "trail_future";
    private static final String MARK = "trail_mark";
    private static final String PHASE = "trail_phase";
    private static final String FRAMES = "trail_frames";
    private static final String BEFORE = "trail_before";
    private static final String FADE = "trail_fade";
    private static final String FADE_LOW = "trail_fade_low";
    private static final int MIN_FADE_TICKS = 15;
    private static final long CLOCK_JUMP_BACK = 40L;
    private static final long CLOCK_JUMP_AHEAD = 400L;
    private static final int ARM_TICKS = 20;
    private static final AtomicInteger SLOW = new AtomicInteger(-1);
    private static final AtomicInteger FAST = new AtomicInteger(-1);
    private static final AtomicInteger LIT = new AtomicInteger(-1);
    private static final AtomicInteger OWN = new AtomicInteger(-1);
    private static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    private static @Nullable Boolean savedEnabled;

    private record Saved(int rule, Vec3 pos, float yaw, float pitch, ItemStack head, ItemStack hand) {
    }

    private TrailSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("trails: Testfeld mit drei fliegenden Drohnen aufbauen", 400, TrailSelfTests::setupStep);
        SelfTest.clientStep("trails: Welt-Regel an – fliegende Drohnen ziehen Trails in ihrer Farbe, schnelle länger", 600, TrailSelfTests::ruleOnStep);
        SelfTest.clientStep("trails: Spielzeit des Clients springt (Abgleich mit dem Server) – Trails bleiben", 300, TrailSelfTests::clockStep);
        SelfTest.clientStep("trails: Welt-Regel aus – keine Trails, Schalter im Tab Darstellung gesperrt", 400, TrailSelfTests::ruleOffStep);
        SelfTest.clientStep("trails: persönlicher Schalter aus – keine Trails, Taste schaltet wieder ein", 400, TrailSelfTests::personalStep);
        SelfTest.clientStep("trails: Einstellungssuche findet den Schalter (Trail, Spur, Schweif)", 300, TrailSelfTests::searchStep);
        SelfTest.clientStep("trails: gelandet und entschärft – keine neue Spur, Trail verblasst", 600, TrailSelfTests::landedStep);
        SelfTest.clientStep("trails: eigene Drohne – im FPV ohne eigenen Trail, im Sichtflug mit", 1600, TrailSelfTests::ownStep);
        SelfTest.clientStep("trails: Aufräumen", 400, TrailSelfTests::cleanupStep);
    }

    static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    static FlightTune ledTune(LedColor color) {
        Map<String, Float> values = new TreeMap<>(new LedSettings(LedMode.STATIC, color, 100, false).values());
        return new FlightTune(values);
    }

    private static void setUp(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        SAVED.compareAndSet(null, new Saved(WorldRules.get(server, WorldRuleSpecs.DRONE_TRAILS), player.position(), player.getYRot(),
                player.getXRot(), player.getItemBySlot(EquipmentSlot.HEAD).copy(), player.getMainHandItem().copy()));
        WorldRules.set(server, WorldRuleSpecs.DRONE_TRAILS, 1);
        TrailFlights.clear(server);
        Vec3 p = player.position();
        QuadcopterEntity slow = TrailFlights.spawn(level, new TrailFlights.Path(p.add(-6.0, 5.0, 12.0), 2.5, 2.5, 2.5, 0.3, 0.0, false),
                DroneBuild.WHOOP, DronePaint.of(Map.of(PaintSlot.FRAME, PAINT_RED)));
        QuadcopterEntity fast = TrailFlights.spawn(level, new TrailFlights.Path(p.add(0.0, 9.0, 26.0), 12.0, 8.0, 24.0, 1.0, 1.0, false),
                DroneBuild.RACER, DronePaint.DEFAULT);
        QuadcopterEntity lit = TrailFlights.spawn(level, new TrailFlights.Path(p.add(8.0, 6.0, 14.0), 4.0, 4.0, 9.0, 0.5, 2.0, false),
                DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP), DronePaint.DEFAULT);
        lit.setTune(ledTune(LED));
        SLOW.set(slow.getId());
        FAST.set(fast.getId());
        LIT.set(lit.getId());
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (savedEnabled == null) {
                savedEnabled = TrailConfig.get().enabled;
            }
            TrailClient.setEnabled(true);
            state.put(FUTURE, ctx.onServer(TrailSelfTests::setUp));
            return StepResult.pending();
        }
        StepResult server = await(ctx, "Testfeld");
        if (server != null) {
            return server;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean arrived = mc.level != null && mc.level.getEntity(SLOW.get()) instanceof QuadcopterEntity
                && mc.level.getEntity(FAST.get()) instanceof QuadcopterEntity && mc.level.getEntity(LIT.get()) instanceof QuadcopterEntity;
        if (!arrived || !TrailClient.ruleOn()) {
            return ctx.tick() > 300 ? StepResult.fail("Drohnen oder Welt-Regel kamen nicht beim Client an (Regel " + TrailClient.ruleOn() + ")")
                    : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static StepResult ruleOnStep(SelfTest.StepContext ctx) {
        TrailFlights.armOnClient(true);
        if (ctx.tick() < 70) {
            return StepResult.pending();
        }
        String problem = flyingProblem();
        if (problem == null) {
            Minecraft mc = Minecraft.getInstance();
            int fast = FAST.get();
            UUID id = mc.level.getEntity(fast).getUUID();
            int expectedFast = TrailColor.palette(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
            if (TrailRenderer.color(SLOW.get()) != TrailColor.vivid(PAINT_RED)) {
                problem = "Farbe der lackierten Drohne " + Integer.toHexString(TrailRenderer.color(SLOW.get()));
            } else if (TrailRenderer.color(LIT.get()) != TrailColor.vivid(LED.rgb())) {
                problem = "Farbe der Drohne mit LED-Streifen " + Integer.toHexString(TrailRenderer.color(LIT.get()));
            } else if (TrailRenderer.color(fast) != expectedFast) {
                problem = "Farbe der unlackierten Drohne " + Integer.toHexString(TrailRenderer.color(fast));
            } else if (TrailRenderer.samples(fast) < 1.4 * TrailRenderer.samples(SLOW.get())) {
                problem = "schneller Trail nicht länger: schnell " + TrailRenderer.samples(fast) + ", langsam " + TrailRenderer.samples(SLOW.get());
            }
        }
        if (problem != null) {
            return ctx.tick() > 450 ? StepResult.fail(problem) : StepResult.pending();
        }
        ctx.log("Trails: " + TrailRenderer.trailsTracked() + " verfolgt, " + TrailRenderer.lastTrailsDrawn() + " gezeichnet, "
                + TrailRenderer.lastQuads() + " Vierecke; Proben schnell " + TrailRenderer.samples(FAST.get()) + ", langsam "
                + TrailRenderer.samples(SLOW.get()) + ", LED " + TrailRenderer.samples(LIT.get()));
        return StepResult.pass();
    }

    private static StepResult clockStep(SelfTest.StepContext ctx) {
        TrailFlights.armOnClient(true);
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return StepResult.fail("keine Welt");
        }
        if (phase == 0) {
            String problem = flyingProblem();
            if (problem != null) {
                return ctx.tick() > 150 ? StepResult.fail(problem) : StepResult.pending();
            }
            state.put(BEFORE, sampleCounts());
            mc.level.setTimeFromServer(mc.level.getGameTime() - CLOCK_JUMP_BACK);
            next(state, 1, ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) state.get(MARK) < 3) {
            return StepResult.pending();
        }
        int[] before = (int[]) state.get(BEFORE);
        int[] after = sampleCounts();
        String direction = phase == 1 ? "zurück" : "vor";
        for (int i = 0; i < before.length; i++) {
            if (after[i] < Math.max(3, before[i] / 2)) {
                return StepResult.fail("Spielzeit " + direction + ": Trail " + i + " verlor seine Proben (" + before[i] + " → " + after[i] + ")");
            }
        }
        String problem = flyingProblem();
        if (problem != null) {
            return StepResult.fail("Spielzeit " + direction + ": " + problem);
        }
        if (phase == 1) {
            ctx.log("Spielzeit " + CLOCK_JUMP_BACK + " Ticks zurück: Proben " + Arrays.toString(before) + " → "
                    + Arrays.toString(after));
            state.put(BEFORE, after);
            mc.level.setTimeFromServer(mc.level.getGameTime() + CLOCK_JUMP_AHEAD);
            next(state, 2, ctx.tick());
            return StepResult.pending();
        }
        ctx.log("Spielzeit " + CLOCK_JUMP_AHEAD + " Ticks vor: Proben " + Arrays.toString(before) + " → "
                + Arrays.toString(after));
        return StepResult.pass();
    }

    private static int[] sampleCounts() {
        return new int[]{TrailRenderer.samples(SLOW.get()), TrailRenderer.samples(FAST.get()), TrailRenderer.samples(LIT.get())};
    }

    private static @Nullable String flyingProblem() {
        if (!TrailRenderer.active()) {
            return "Trails nicht aktiv (Regel " + TrailClient.ruleOn() + ", persönlich " + TrailConfig.get().enabled + ")";
        }
        if (TrailRenderer.trailsTracked() < 3) {
            return "nur " + TrailRenderer.trailsTracked() + " Trails verfolgt";
        }
        for (AtomicInteger id : new AtomicInteger[]{SLOW, FAST, LIT}) {
            if (!TrailRenderer.drawn(id.get())) {
                return "Trail von Drohne " + id.get() + " nicht gezeichnet (Proben " + TrailRenderer.samples(id.get()) + ")";
            }
        }
        if (TrailRenderer.lastQuads() <= 0) {
            return "keine Vierecke gezeichnet";
        }
        return null;
    }

    private static StepResult ruleOffStep(SelfTest.StepContext ctx) {
        TrailFlights.armOnClient(true);
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.DRONE_TRAILS, 0)));
                next(state, 1, ctx.tick());
            }
            case 1 -> {
                if (TrailClient.ruleOn()) {
                    return ctx.tick() > 200 ? StepResult.fail("Welt-Regel aus kam nicht beim Client an") : StepResult.pending();
                }
                if (ctx.tick() - (int) state.get(MARK) < 12) {
                    return StepResult.pending();
                }
                if (TrailRenderer.trailsTracked() != 0 || TrailRenderer.lastTrailsDrawn() != 0) {
                    return StepResult.fail("Trails trotz Welt-Regel aus: " + TrailRenderer.trailsTracked() + " verfolgt, "
                            + TrailRenderer.lastTrailsDrawn() + " gezeichnet");
                }
                boolean before = TrailConfig.get().enabled;
                TrailClient.toggle(Minecraft.getInstance());
                if (TrailConfig.get().enabled != before) {
                    return StepResult.fail("Taste ändert den persönlichen Schalter, obwohl die Welt-Regel aus ist");
                }
                String tab = tabProblem(false);
                if (tab != null) {
                    return StepResult.fail(tab);
                }
                state.put(FUTURE, ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.DRONE_TRAILS, 1)));
                next(state, 2, ctx.tick());
            }
            default -> {
                if (!TrailClient.ruleOn()) {
                    return ctx.tick() > 400 ? StepResult.fail("Welt-Regel an kam nicht zurück") : StepResult.pending();
                }
                return StepResult.pass();
            }
        }
        return StepResult.pending();
    }

    private static @Nullable String tabProblem(boolean ruleOn) {
        DroneRenderConfigTab tab = null;
        for (ConfigTab candidate : ConfigTabs.create()) {
            if (candidate instanceof DroneRenderConfigTab found) {
                tab = found;
            }
        }
        if (tab == null) {
            return "Tab Darstellung nicht angemeldet";
        }
        tab.attach(new Host(), 0, 0, 480, 270);
        CycleButton<Boolean> toggle = tab.trailToggle();
        if (toggle == null) {
            return "Schalter „Drohnen-Trails zeigen“ fehlt im Tab Darstellung";
        }
        if (toggle.active != ruleOn) {
            return "Schalter „Drohnen-Trails zeigen“ " + (toggle.active ? "bedienbar" : "gesperrt") + " bei Welt-Regel " + (ruleOn ? "an" : "aus");
        }
        if (tab.trailRuleHintShown() == ruleOn) {
            return "Hinweis auf die Welt-Regel " + (ruleOn ? "trotz Regel an" : "fehlt bei Regel aus");
        }
        boolean before = TrailConfig.get().enabled;
        tab.trailDraft().enabled = !before;
        if (TrailConfig.get().enabled != before) {
            return "Tab ändert den Schalter vor dem Speichern";
        }
        tab.save();
        boolean saved = TrailConfig.get().enabled;
        TrailClient.setEnabled(before);
        if (saved == before) {
            return "Speichern übernimmt den Schalter nicht";
        }
        tab.resetDefaults();
        if (!tab.trailDraft().enabled) {
            return "Standardwert des Schalters ist nicht an";
        }
        return null;
    }

    private static StepResult personalStep(SelfTest.StepContext ctx) {
        TrailFlights.armOnClient(true);
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                String tab = tabProblem(true);
                if (tab != null) {
                    return StepResult.fail(tab);
                }
                TrailClient.setEnabled(false);
                next(state, 1, ctx.tick());
            }
            case 1 -> {
                if (ctx.tick() - (int) state.get(MARK) < 10) {
                    return StepResult.pending();
                }
                if (TrailRenderer.trailsTracked() != 0 || TrailRenderer.lastTrailsDrawn() != 0) {
                    return StepResult.fail("Trails trotz persönlichem Schalter aus: " + TrailRenderer.trailsTracked() + " verfolgt");
                }
                state.put(FRAMES, TrailRenderer.framesSubmitted());
                next(state, 2, ctx.tick());
            }
            case 2 -> {
                if (ctx.tick() - (int) state.get(MARK) < 20) {
                    return StepResult.pending();
                }
                if (TrailRenderer.framesSubmitted() != (long) state.get(FRAMES)) {
                    return StepResult.fail("Trails trotz Schalter aus übergeben");
                }
                TrailClient.toggle(Minecraft.getInstance());
                if (!TrailConfig.get().enabled) {
                    return StepResult.fail("Taste schaltet die Trails nicht wieder ein");
                }
                next(state, 3, ctx.tick());
            }
            default -> {
                if (!TrailRenderer.drawn(FAST.get()) || !TrailRenderer.drawn(SLOW.get())) {
                    return ctx.tick() - (int) state.get(MARK) > 200 ? StepResult.fail("Trails kommen nach dem Einschalten nicht zurück")
                            : StepResult.pending();
                }
                return StepResult.pass();
            }
        }
        return StepResult.pending();
    }

    private static StepResult searchStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            if (ctx.tick() == 0) {
                ConfigTabs.open(null, "drone_render");
            }
            return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
        }
        SearchBar search = screen.search();
        if (search == null) {
            screen.discardAndClose();
            return StepResult.fail("Konfigurator ohne Suchfeld");
        }
        String label = Component.translatable("propwash.trails.enabled").getString();
        String problem = null;
        search.focus();
        for (String query : new String[]{"trail", "spur", "schweif", "tracer"}) {
            search.type(query);
            boolean found = search.hits().stream().anyMatch(hit -> "drone_render".equals(hit.entry().tabId()) && hit.entry().label().contains(label));
            if (!found) {
                problem = "Suche „" + query + "“ findet „" + label + "“ nicht: " + search.hits().stream().limit(4)
                        .map(hit -> hit.entry().label() + " (" + hit.entry().tabId() + ")").toList();
                break;
            }
        }
        search.type("");
        screen.discardAndClose();
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static StepResult landedStep(SelfTest.StepContext ctx) {
        TrailFlights.armOnClient(true);
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        int lit = LIT.get();
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level != null && mc.level.getEntity(lit) instanceof QuadcopterEntity drone)) {
            return StepResult.fail("Drohne mit LED-Streifen fehlt");
        }
        switch (phase) {
            case 0 -> {
                if (!TrailRenderer.drawn(lit)) {
                    return ctx.tick() > 100 ? StepResult.fail("Drohne mit LED-Streifen zieht vor der Landung keinen Trail") : StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerLevel level = player(server).level();
                    if (level.getEntity(lit) instanceof QuadcopterEntity serverDrone) {
                        int x = serverDrone.getBlockX();
                        int z = serverDrone.getBlockZ();
                        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                        TrailFlights.park(server, lit, new Vec3(x + 0.5, top + 0.05, z + 0.5));
                    }
                }));
                next(state, 1, ctx.tick());
            }
            case 1 -> {
                if (drone.isArmed() || TrailRenderer.emitting(lit)) {
                    return ctx.tick() - (int) state.get(MARK) > 60 ? StepResult.fail("entschärfte Drohne zieht weiter einen Trail")
                            : StepResult.pending();
                }
                int since = ctx.tick() - (int) state.get(MARK);
                if (TrailRenderer.samples(lit) > 0) {
                    float fresh = TrailRenderer.newestFreshness(lit);
                    float previous = (float) state.getOrDefault(FADE, 1.0F);
                    if (fresh > previous + 0.02F) {
                        return StepResult.fail("Trail der entschärften Drohne wird wieder deckender: " + previous + " → " + fresh);
                    }
                    state.put(FADE, fresh);
                    state.put(FADE_LOW, Math.min(fresh, (float) state.getOrDefault(FADE_LOW, 1.0F)));
                    return since > 120 ? StepResult.fail("Trail der entschärften Drohne verblasst nicht: " + TrailRenderer.samples(lit))
                            : StepResult.pending();
                }
                float lowest = (float) state.getOrDefault(FADE_LOW, 1.0F);
                if (since < MIN_FADE_TICKS || lowest > 0.5F) {
                    return StepResult.fail("Trail der entschärften Drohne verschwindet abrupt statt zu verblassen: nach " + since
                            + " Ticks, zuletzt gesehene Frische " + String.format(Locale.ROOT, "%.2f", lowest));
                }
                ctx.log("Trail der entschärften Drohne verblasst nach " + since + " Ticks (zuletzt gesehene Frische "
                        + String.format(Locale.ROOT, "%.2f", lowest) + ")");
                next(state, 2, ctx.tick());
            }
            default -> {
                drone.setArmed(true);
                int since = ctx.tick() - (int) state.get(MARK);
                if (since > 5 && (TrailRenderer.emitting(lit) || TrailRenderer.samples(lit) > 1)) {
                    return StepResult.fail("gelandete, scharfe Drohne zieht einen Trail: Proben " + TrailRenderer.samples(lit) + ", y "
                            + String.format(Locale.ROOT, "%.2f", drone.getY()) + ", Boden " + drone.onGround());
                }
                if (since < 60) {
                    return StepResult.pending();
                }
                drone.setArmed(false);
                return StepResult.pass();
            }
        }
        return StepResult.pending();
    }

    private static void startOwn(MinecraftServer server, PilotMode mode) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        QuadcopterEntity drone = level.getEntity(OWN.get()) instanceof QuadcopterEntity existing ? existing : null;
        if (drone == null) {
            drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            Vec3 feet = player.position();
            drone.snapTo(feet.x + 1.5, feet.y, feet.z + 3.0, 0.0F, 0.0F);
            drone.setAttitude(new Quaternionf());
            drone.setBuild(DroneBuild.FREESTYLE);
            drone.setPaint(DronePaint.of(Map.of(PaintSlot.FRAME, 0x2050E0)));
            if (!level.addFreshEntity(drone)) {
                throw new IllegalStateException("addFreshEntity fehlgeschlagen");
            }
            OWN.set(drone.getId());
        }
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        if (mode == PilotMode.FPV) {
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (goggles.isEmpty()) {
                throw new IllegalStateException("Kreativ-Brille nicht registriert");
            }
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemSlot(EquipmentSlot.HEAD, goggles);
        } else {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, mode);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException(mode + "-Start verweigert: " + result);
        }
    }

    private static void releaseOwn(MinecraftServer server) {
        DronePiloting.pilotedBy(player(server).getUUID()).ifPresent(DronePiloting::release);
    }

    private static boolean climb(PilotSession session, int since) {
        DroneSim sim = session.sim();
        if (sim == null) {
            return false;
        }
        if (since < ARM_TICKS) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since >= ARM_TICKS / 2, StabMode.ANGLE);
            return true;
        }
        float hover = (float) Math.max(0.15, Math.min(0.6, sim.telemetry().hoverThrottleStick));
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, Math.min(1.0F, hover + 0.12F)), true, StabMode.ANGLE);
        return true;
    }

    private static StepResult ownStep(SelfTest.StepContext ctx) {
        TrailFlights.armOnClient(true);
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity current = session.drone();
        boolean ownActive = current != null && current.getId() == OWN.get() && session.sim() != null;
        int since = ctx.tick() - (int) state.getOrDefault(MARK, 0);
        switch (phase) {
            case 0, 3 -> {
                if (session.isActive()) {
                    return since > 300 ? StepResult.fail("vorige Flugsitzung endet nicht") : StepResult.pending();
                }
                PilotMode mode = phase == 0 ? PilotMode.FPV : PilotMode.LINE_OF_SIGHT;
                state.put(FUTURE, ctx.onServer(server -> startOwn(server, mode)));
                next(state, phase + 1, ctx.tick());
            }
            case 1, 4 -> {
                StepResult server = await(ctx, "Start");
                if (server != null) {
                    return server;
                }
                boolean fpv = phase == 1;
                if (!ownActive || session.isFpv() != fpv) {
                    return since > 300 ? StepResult.fail("keine " + (fpv ? "FPV-Sitzung" : "Sichtflug-Sitzung") + " mit der eigenen Drohne")
                            : StepResult.pending();
                }
                climb(session, 0);
                next(state, phase + 1, ctx.tick());
            }
            case 2 -> {
                climb(session, since);
                int own = OWN.get();
                if (since < 60 || TrailRenderer.samples(own) < 3 || !TrailRenderer.drawn(FAST.get())) {
                    return since > 200 ? StepResult.fail("eigene Drohne im FPV ohne Proben (" + TrailRenderer.describe(own) + ", scharf "
                            + (current != null && current.isArmed()) + ", Höhe " + (current == null ? "-" : String.format(Locale.ROOT, "%.2f", current.getY()))
                            + ", Telemetrie scharf " + (session.sim() != null && session.sim().telemetry().armed) + ") oder fremder Trail nicht gezeichnet ("
                            + TrailRenderer.drawn(FAST.get()) + ")") : StepResult.pending();
                }
                if (TrailRenderer.drawn(own)) {
                    return StepResult.fail("eigener Trail im FPV-Bild gezeichnet");
                }
                ctx.log("FPV: eigene Drohne " + TrailRenderer.samples(own) + " Proben, nicht gezeichnet; fremde Trails gezeichnet: "
                        + TrailRenderer.lastTrailsDrawn());
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                state.put(FUTURE, ctx.onServer(TrailSelfTests::releaseOwn));
                next(state, 3, ctx.tick());
            }
            default -> {
                climb(session, since);
                int own = OWN.get();
                if (since < 60 || !TrailRenderer.drawn(own)) {
                    return since > 240 ? StepResult.fail("eigener Trail im Sichtflug nicht gezeichnet (" + TrailRenderer.describe(own) + ", scharf "
                            + (current != null && current.isArmed()) + ")") : StepResult.pending();
                }
                ctx.log("Sichtflug: eigener Trail mit " + TrailRenderer.samples(own) + " Proben gezeichnet");
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                return StepResult.pass();
            }
        }
        return StepResult.pending();
    }

    private static void tearDown(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        if (level.getEntity(OWN.getAndSet(-1)) instanceof QuadcopterEntity drone) {
            drone.discard();
        }
        TrailFlights.clear(server);
        SLOW.set(-1);
        FAST.set(-1);
        LIT.set(-1);
        Saved saved = SAVED.getAndSet(null);
        if (saved != null) {
            WorldRules.set(server, WorldRuleSpecs.DRONE_TRAILS, saved.rule());
            player.setItemSlot(EquipmentSlot.HEAD, saved.head());
            player.setItemSlot(EquipmentSlot.MAINHAND, saved.hand());
            player.teleportTo(level, saved.pos().x, saved.pos().y, saved.pos().z, Set.of(), saved.yaw(), saved.pitch(), true);
        } else {
            WorldRules.set(server, WorldRuleSpecs.DRONE_TRAILS, WorldRuleSpecs.DRONE_TRAILS.defaultValue());
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            PilotSession.INSTANCE.clearTestInput();
            state.put(FUTURE, ctx.onServer(TrailSelfTests::tearDown));
            return StepResult.pending();
        }
        StepResult server = await(ctx, "Aufräumen");
        if (server != null) {
            return server;
        }
        Boolean enabled = savedEnabled;
        if (enabled != null) {
            TrailClient.setEnabled(enabled);
            savedEnabled = null;
        }
        if (PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 300 ? StepResult.fail("Flugsitzung endet nicht") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static @Nullable StepResult await(SelfTest.StepContext ctx, String what) {
        Object value = ctx.state().get(FUTURE);
        if (!(value instanceof CompletableFuture<?> future)) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static void next(Map<String, Object> state, int phase, int tick) {
        state.put(PHASE, phase);
        state.put(MARK, tick);
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
