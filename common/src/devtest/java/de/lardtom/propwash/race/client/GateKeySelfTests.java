package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseInventoryScreen;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateItem;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class GateKeySelfTests {
    private static final int MAX_TICKS = 900;
    private static final int SETTLE = 3;
    private static final int OTHER_KEY = InputConstants.KEY_F13;
    private static final GateKeyRoute.Hands GATE = new GateKeyRoute.Hands(true, false, false);
    private static final GateKeyRoute.Hands TOOL = new GateKeyRoute.Hands(false, true, false);
    private static final GateKeyRoute.Hands REMOTE = new GateKeyRoute.Hands(false, false, true);

    private GateKeySelfTests() {
    }

    static void register() {
        SelfTest.clientStep("race: Taste R ohne Doppelbelegungs-Warnung in der Steuerung", 200, GateKeySelfTests::controlsStep);
        SelfTest.clientStep("race: Taste R dreht Gates oder wechselt den Flugmodus je nach Hand", MAX_TICKS + 100, GateKeySelfTests::dispatchStep);
    }

    public static Screenshots.Scene controlsScene() {
        return new ControlsScene();
    }

    public static Screenshots.Scene tooltipScene(boolean courseTool) {
        return new TooltipScene(courseTool ? () -> new ItemStack(RaceModule.COURSE_TOOL.get())
                : () -> new ItemStack(RaceModule.gateItem(GateSize.MEDIUM).orElseThrow()));
    }

    public static Screenshots.Scene tooltipScene(Supplier<ItemStack> stack) {
        return new TooltipScene(stack);
    }

    private static KeyMapping rotate() {
        KeyMapping mapping = GateMountKeys.mapping();
        if (mapping == null) {
            throw new IllegalStateException("Taste „Gate drehen“ nicht registriert");
        }
        return mapping;
    }

    private static void bind(KeyMapping mapping, InputConstants.Key key) {
        mapping.setKey(key);
        KeyMapping.resetMapping();
    }

    private static void press(KeyMapping mapping) {
        KeyMapping.click(InputConstants.getKey(mapping.saveString()));
    }

    private static StepResult controlsStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping rotate = rotate();
        KeyMapping flight = InputKeys.FLIGHT_MODE;
        KeyMapping arm = InputKeys.ARM;
        InputConstants.Key savedRotate = InputConstants.getKey(rotate.saveString());
        InputConstants.Key savedFlight = InputConstants.getKey(flight.saveString());
        InputConstants.Key savedArm = InputConstants.getKey(arm.saveString());
        try {
            rotate.setKey(rotate.getDefaultKey());
            flight.setKey(flight.getDefaultKey());
            KeyMapping.resetMapping();
            if (!rotate.getDefaultKey().equals(flight.getDefaultKey()) || rotate.getDefaultKey().getValue() != InputConstants.KEY_R) {
                return StepResult.fail("Standardtasten: Gate drehen " + rotate.getDefaultKey().getName() + ", Flugmodus " + flight.getDefaultKey().getName());
            }
            if (rotate.same(flight) || flight.same(rotate)) {
                return StepResult.fail("KeyMapping#same meldet Gate drehen und Flugmodus als Konflikt");
            }
            if (!GateMountKeys.sharedWithFlightMode()) {
                return StepResult.fail("Gate drehen und Flugmodus gelten nicht als geteilte Taste");
            }
            KeyBindsScreen screen = new KeyBindsScreen(null, mc.options);
            mc.gui.setScreen(screen);
            KeyBindsList list = list(screen);
            if (list == null) {
                return StepResult.fail("Steuerung ohne Tastenliste");
            }
            String clean = describe(list, rotate) + " / " + describe(list, flight);
            if (!clean.equals("R / R")) {
                return StepResult.fail("Steuerung zeigt Gate drehen / Flugmodus als " + clean);
            }
            bind(arm, rotate.getDefaultKey());
            list.resetMappingAndUpdateButtons();
            String clash = describe(list, rotate) + " / " + describe(list, arm);
            if (!arm.same(rotate) || !clash.equals("[ R ] / [ R ]")) {
                return StepResult.fail("Echter Konflikt mit „Scharf schalten“ wird nicht gemeldet: " + clash);
            }
            bind(arm, savedArm);
            bind(flight, InputConstants.Type.KEYBOARD.getOrCreate(OTHER_KEY));
            list.resetMappingAndUpdateButtons();
            String rebound = describe(list, rotate) + " / " + describe(list, flight);
            if (GateMountKeys.sharedWithFlightMode() || rebound.contains("[")) {
                return StepResult.fail("Nach dem Umlegen von Flugmodus: geteilt=" + GateMountKeys.sharedWithFlightMode() + ", Anzeige " + rebound);
            }
            bind(flight, flight.getDefaultKey());
            String modifier = modifierClash(list, rotate, flight);
            if (modifier.startsWith("!")) {
                return StepResult.fail(modifier.substring(1));
            }
            ctx.log("Steuerung: R / R ohne Warnung, Konflikt mit Scharf schalten " + clash + ", umgelegt " + rebound + ", " + modifier);
            return StepResult.pass();
        } catch (ReflectiveOperationException exception) {
            return StepResult.fail("Tastenmodifikator nicht setzbar: " + exception);
        } finally {
            rotate.setKey(savedRotate);
            flight.setKey(savedFlight);
            arm.setKey(savedArm);
            KeyMapping.resetMapping();
            if (mc.gui.screen() instanceof KeyBindsScreen) {
                mc.gui.setScreen(null);
            }
        }
    }

    private static String modifierClash(KeyBindsList list, KeyMapping rotate, KeyMapping flight) throws ReflectiveOperationException {
        Class<?> type;
        try {
            type = Class.forName("net.neoforged.neoforge.client.settings.KeyModifier");
        } catch (ClassNotFoundException absent) {
            return "Loader ohne Tastenmodifikatoren";
        }
        Method set = KeyMapping.class.getMethod("setKeyModifierAndCode", type, InputConstants.Key.class);
        Object saved = KeyMapping.class.getMethod("getKeyModifier").invoke(flight);
        try {
            set.invoke(flight, modifier(type, "SHIFT"), flight.getDefaultKey());
            KeyMapping.resetMapping();
            list.resetMappingAndUpdateButtons();
            String shown = describe(list, rotate) + " / " + describe(list, flight);
            boolean shared = GateMountKeys.sharedWithFlightMode();
            if (shared || !flight.same(rotate) || !rotate.same(flight) || !shown.startsWith("[") || !shown.contains("/ [")) {
                return "!Flugmodus auf Shift+R, Gate drehen auf R: geteilt=" + shared + ", Konflikt " + flight.same(rotate) + "/" + rotate.same(flight)
                        + ", Anzeige " + shown;
            }
            return "Flugmodus auf Shift+R gegen Gate drehen auf R als Konflikt gemeldet: " + shown;
        } finally {
            set.invoke(flight, saved, flight.getDefaultKey());
            KeyMapping.resetMapping();
            list.resetMappingAndUpdateButtons();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object modifier(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static @Nullable KeyBindsList list(KeyBindsScreen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof KeyBindsList list) {
                return list;
            }
        }
        return null;
    }

    private static int row(KeyMapping mapping) {
        KeyMapping[] sorted = Minecraft.getInstance().options.keyMappings.clone();
        Arrays.sort(sorted);
        int row = 0;
        KeyMapping.Category previous = null;
        for (KeyMapping key : sorted) {
            if (key.getCategory() != previous) {
                previous = key.getCategory();
                row++;
            }
            if (key == mapping) {
                break;
            }
            row++;
        }
        return row;
    }

    private static String describe(KeyBindsList list, KeyMapping mapping) {
        int row = row(mapping);
        if (row >= list.children().size() || !(list.children().get(row) instanceof KeyBindsList.KeyEntry entry)) {
            return "?";
        }
        List<? extends GuiEventListener> buttons = entry.children();
        return buttons.isEmpty() || !(buttons.getFirst() instanceof AbstractWidget button) ? "?" : button.getMessage().getString();
    }

    private static StepResult dispatchStep(SelfTest.StepContext ctx) {
        Scenario s = (Scenario) ctx.state().computeIfAbsent("gate_key", key -> new Scenario());
        try {
            StepResult result = run(ctx, s);
            if (result.status() != StepResult.Status.PENDING) {
                cleanup(ctx, s);
            }
            return result;
        } catch (RuntimeException exception) {
            cleanup(ctx, s);
            throw exception;
        }
    }

    private static StepResult run(SelfTest.StepContext ctx, Scenario s) {
        if (s.pending != null) {
            if (s.pending.isCompletedExceptionally()) {
                return StepResult.fail(s.phase + ": Server-Aktion fehlgeschlagen: " + s.pending.exceptionNow().getMessage());
            }
            if (!s.pending.isDone()) {
                return StepResult.pending();
            }
            s.pending = null;
        }
        if (ctx.tick() > MAX_TICKS) {
            return StepResult.fail("Zeit abgelaufen in Phase " + s.phase);
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.fail("kein Spieler");
        }
        KeyMapping rotate = rotate();
        int waited = ctx.tick() - s.since;
        switch (s.phase) {
            case SETUP -> {
                s.savedRotate = InputConstants.getKey(rotate.saveString());
                s.savedFlight = InputConstants.getKey(InputKeys.FLIGHT_MODE.saveString());
                rotate.setKey(rotate.getDefaultKey());
                InputKeys.FLIGHT_MODE.setKey(InputKeys.FLIGHT_MODE.getDefaultKey());
                KeyMapping.resetMapping();
                InputKeys.drain(rotate);
                InputKeys.drain(InputKeys.FLIGHT_MODE);
                s.savedPitch = player.getXRot();
                player.setXRot(90.0F);
                GateMountKeys.setPreference(GatePlacement.Preference.DEFAULT);
                s.pending = ctx.onServer(server -> {
                    ServerPlayer serverPlayer = firstPlayer(server);
                    s.previousMain = serverPlayer.getMainHandItem().copy();
                    s.previousOff = serverPlayer.getOffhandItem().copy();
                    s.previousHead = serverPlayer.getItemBySlot(EquipmentSlot.HEAD).copy();
                    serverPlayer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(RaceModule.gateItem(GateSize.SMALL).orElseThrow()));
                    serverPlayer.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                });
                s.enter(Phase.GATE, ctx.tick());
                return StepResult.pending();
            }
            case GATE -> {
                if (!GateMountKeys.hands(player).equals(GATE) || mc.hitResult instanceof EntityHitResult) {
                    return waited > 100 ? StepResult.fail("Gate in der Hand kommt nicht an: " + GateMountKeys.hands(player) + ", Ziel " + mc.hitResult)
                            : StepResult.pending();
                }
                s.surface = GateMountKeys.lastSurface();
                s.before = GateMountKeys.preference();
                press(rotate);
                s.enter(Phase.GATE_TURNED, ctx.tick());
                return StepResult.pending();
            }
            case GATE_TURNED -> {
                if (waited < SETTLE) {
                    return StepResult.pending();
                }
                GatePlacement.Preference now = GateMountKeys.preference();
                if (now.turn(s.surface) == s.before.turn(s.surface)) {
                    return StepResult.fail("R mit Gate in der Hand dreht die Vorschau nicht (" + s.surface + ", Stellung " + now.turn(s.surface) + ")");
                }
                if (InputKeys.FLIGHT_MODE.consumeClick()) {
                    return StepResult.fail("Flugmodus-Taste behält ohne Flug einen Klick");
                }
                ctx.log("Gate in der Hand: R dreht die Vorschau (" + s.surface + " Stellung " + s.before.turn(s.surface) + " -> " + now.turn(s.surface) + ")");
                s.before = now;
                mc.options.keyShift.setDown(true);
                press(rotate);
                s.enter(Phase.GATE_SNEAK, ctx.tick());
                return StepResult.pending();
            }
            case GATE_SNEAK -> {
                if (waited < SETTLE) {
                    return StepResult.pending();
                }
                mc.options.keyShift.setDown(false);
                GateMount mount = GateMountKeys.preference().mount(s.surface);
                if (mount == s.before.mount(s.surface)) {
                    return StepResult.fail("Schleichen + R mit Gate wechselt die Lage nicht (" + mount + ")");
                }
                ctx.log("Schleichen + R: Lage " + s.before.mount(s.surface) + " -> " + mount);
                s.pending = ctx.onServer(server -> firstPlayer(server).setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(DroneModule.REMOTE.get())));
                s.enter(Phase.REMOTE, ctx.tick());
                return StepResult.pending();
            }
            case REMOTE -> {
                if (!GateMountKeys.hands(player).equals(REMOTE)) {
                    return waited > 100 ? StepResult.fail("Funke in der Hand kommt nicht an: " + GateMountKeys.hands(player)) : StepResult.pending();
                }
                GateKeyRoute.Action atGate = GateKeyRoute.route(context(player, true));
                if (atGate != GateKeyRoute.Action.NONE) {
                    return StepResult.fail("Funke in der Hand, Blick aufs Gate: " + atGate + " statt NONE");
                }
                s.before = GateMountKeys.preference();
                press(rotate);
                s.enter(Phase.REMOTE_PRESSED, ctx.tick());
                return StepResult.pending();
            }
            case REMOTE_PRESSED -> {
                if (waited < SETTLE) {
                    return StepResult.pending();
                }
                if (!GateMountKeys.preference().equals(s.before)) {
                    return StepResult.fail("R mit der Funke in der Hand dreht die Gate-Vorschau");
                }
                if (InputKeys.FLIGHT_MODE.consumeClick()) {
                    return StepResult.fail("Flugmodus-Taste behält ohne Flug einen Klick");
                }
                s.pending = ctx.onServer(server -> firstPlayer(server).setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(RaceModule.COURSE_TOOL.get())));
                s.enter(Phase.TOOL, ctx.tick());
                return StepResult.pending();
            }
            case TOOL -> {
                if (!GateMountKeys.hands(player).equals(TOOL)) {
                    return waited > 100 ? StepResult.fail("Kurs-Werkzeug in der Hand kommt nicht an: " + GateMountKeys.hands(player)) : StepResult.pending();
                }
                GateKeyRoute.Action atGate = GateKeyRoute.route(context(player, true));
                GateKeyRoute.Action elsewhere = GateKeyRoute.route(context(player, false));
                if (atGate != GateKeyRoute.Action.ROTATE_GATE || elsewhere != GateKeyRoute.Action.NONE) {
                    return StepResult.fail("Kurs-Werkzeug: Blick aufs Gate " + atGate + ", sonst " + elsewhere);
                }
                s.before = GateMountKeys.preference();
                press(rotate);
                s.enter(Phase.TOOL_PRESSED, ctx.tick());
                return StepResult.pending();
            }
            case TOOL_PRESSED -> {
                if (waited < SETTLE) {
                    return StepResult.pending();
                }
                if (!GateMountKeys.preference().equals(s.before)) {
                    return StepResult.fail("R mit dem Kurs-Werkzeug ohne Gate im Blick dreht die Vorschau");
                }
                ctx.log("Funke und Kurs-Werkzeug ohne Gate im Blick: R ändert nichts");
                s.savedYaw = player.getYRot();
                s.pending = ctx.onServer(server -> placeGateAhead(server, s));
                s.enter(Phase.PLACED_AIM, ctx.tick());
                return StepResult.pending();
            }
            case PLACED_AIM -> {
                if (s.placeProblem != null) {
                    return StepResult.fail("Prüf-Gate: " + s.placeProblem);
                }
                StepResult aiming = aimAtGate(mc, player, s, waited);
                if (aiming != null) {
                    return aiming;
                }
                GateKeyRoute.Action action = GateKeyRoute.route(context(player, true));
                if (action != GateKeyRoute.Action.ROTATE_GATE) {
                    return StepResult.fail("Kurs-Werkzeug, Blick aufs platzierte Gate: " + action + " statt ROTATE_GATE");
                }
                s.before = GateMountKeys.preference();
                press(rotate);
                s.enter(Phase.PLACED_ROTATED, ctx.tick());
                return StepResult.pending();
            }
            case PLACED_ROTATED -> {
                if (!s.polled) {
                    s.polled = true;
                    s.pending = ctx.onServer(server -> readGate(server, s));
                    return StepResult.pending();
                }
                s.polled = false;
                if (s.serverYaw != s.expectYaw) {
                    return waited > 100 ? StepResult.fail("R mit Blick aufs platzierte Gate dreht es auf dem Server nicht (" + s.startYaw + "° -> "
                            + s.serverYaw + "°, erwartet " + s.expectYaw + "°)") : StepResult.pending();
                }
                if (!GateMountKeys.preference().equals(s.before)) {
                    return StepResult.fail("R am platzierten Gate dreht zusätzlich die Vorschau");
                }
                ctx.log("Kurs-Werkzeug, Blick aufs platzierte Gate: R dreht es auf dem Server " + s.startYaw + "° -> " + s.serverYaw + "°");
                mc.options.keyShift.setDown(true);
                s.enter(Phase.PLACED_SNEAK_AIM, ctx.tick());
                return StepResult.pending();
            }
            case PLACED_SNEAK_AIM -> {
                StepResult aiming = aimAtGate(mc, player, s, waited);
                if (aiming != null) {
                    return aiming;
                }
                press(rotate);
                s.enter(Phase.PLACED_MOUNTED, ctx.tick());
                return StepResult.pending();
            }
            case PLACED_MOUNTED -> {
                if (!s.polled) {
                    s.polled = true;
                    s.pending = ctx.onServer(server -> readGate(server, s));
                    return StepResult.pending();
                }
                s.polled = false;
                if (s.serverMount != s.expectMount) {
                    return waited > 100 ? StepResult.fail("Schleichen + R mit Blick aufs platzierte Gate wechselt die Lage auf dem Server nicht ("
                            + s.serverMount + ", erwartet " + s.expectMount + ")") : StepResult.pending();
                }
                mc.options.keyShift.setDown(false);
                ctx.log("Schleichen + R am platzierten Gate: Lage auf dem Server STANDING -> " + s.serverMount);
                if (s.savedPitch != null && s.savedYaw != null) {
                    player.setXRot(s.savedPitch);
                    player.setYRot(s.savedYaw);
                }
                s.pending = ctx.onServer(server -> {
                    removeGate(server, s);
                    spawnAndFly(server, s);
                });
                s.enter(Phase.SESSION, ctx.tick());
                return StepResult.pending();
            }
            case SESSION -> {
                PilotSession session = PilotSession.INSTANCE;
                if (!session.isActive() || session.drone() == null || session.drone().getId() != s.droneId) {
                    return waited > 200 ? StepResult.fail("keine Pilot-Sitzung mit der Testdrohne") : StepResult.pending();
                }
                if (s.modeBefore == null) {
                    session.setTestInput(StickPositions.IDLE, false, null);
                    s.modeBefore = session.flightMode();
                    s.since = ctx.tick();
                    return StepResult.pending();
                }
                if (waited < SETTLE) {
                    return StepResult.pending();
                }
                if (!GateMountKeys.hands(player).gate()) {
                    return waited > 100 ? StepResult.fail("Gate in der Nebenhand kommt nicht an") : StepResult.pending();
                }
                s.modeBefore = session.flightMode();
                s.before = GateMountKeys.preference();
                press(rotate);
                s.enter(Phase.SESSION_PRESSED, ctx.tick());
                return StepResult.pending();
            }
            case SESSION_PRESSED -> {
                StabMode mode = PilotSession.INSTANCE.flightMode();
                if (mode != next(s.modeBefore)) {
                    return waited > 60 ? StepResult.fail("R im Flug wechselt den Flugmodus nicht (" + s.modeBefore + " -> " + mode + ")")
                            : StepResult.pending();
                }
                if (!GateMountKeys.preference().equals(s.before)) {
                    return StepResult.fail("R im Flug dreht trotz Gate in der Nebenhand die Gate-Vorschau");
                }
                ctx.log("Im Flug (Funke, Gate in der Nebenhand): R wechselt " + s.modeBefore + " -> " + mode);
                bind(InputKeys.FLIGHT_MODE, InputConstants.Type.KEYBOARD.getOrCreate(OTHER_KEY));
                if (GateMountKeys.sharedWithFlightMode()) {
                    return StepResult.fail("Flugmodus auf eigener Taste gilt weiter als geteilt");
                }
                s.modeBefore = mode;
                press(rotate);
                s.enter(Phase.REBOUND_R, ctx.tick());
                return StepResult.pending();
            }
            case REBOUND_R -> {
                if (waited < SETTLE + 2) {
                    return StepResult.pending();
                }
                StabMode mode = PilotSession.INSTANCE.flightMode();
                if (mode != s.modeBefore || !GateMountKeys.preference().equals(s.before)) {
                    return StepResult.fail("Flugmodus umgelegt: R wechselt trotzdem " + s.modeBefore + " -> " + mode + " oder dreht die Vorschau");
                }
                press(InputKeys.FLIGHT_MODE);
                s.enter(Phase.REBOUND_OTHER, ctx.tick());
                return StepResult.pending();
            }
            case REBOUND_OTHER -> {
                StabMode mode = PilotSession.INSTANCE.flightMode();
                if (mode != next(s.modeBefore)) {
                    return waited > 60 ? StepResult.fail("umgelegte Flugmodus-Taste wirkt nicht (" + s.modeBefore + " -> " + mode + ")")
                            : StepResult.pending();
                }
                ctx.log("Flugmodus auf eigener Taste: R bleibt im Flug still, die neue Taste wechselt " + s.modeBefore + " -> " + mode);
                return StepResult.pass();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static GateKeyRoute.Context context(LocalPlayer player, boolean lookingAtGate) {
        return new GateKeyRoute.Context(false, PilotSession.INSTANCE.isActive(), GateMountKeys.hands(player), lookingAtGate, false,
                GateMountKeys.sharedWithFlightMode());
    }

    private static StabMode next(@Nullable StabMode mode) {
        return switch (mode == null ? StabMode.ACRO : mode) {
            case ACRO -> StabMode.ANGLE;
            case ANGLE -> StabMode.HORIZON;
            case HORIZON -> StabMode.ACRO;
        };
    }

    private static void placeGateAhead(MinecraftServer server, Scenario s) {
        ServerPlayer player = firstPlayer(server);
        ServerLevel level = player.level();
        Vec3 start = player.position();
        s.savedPos = start;
        BlockPos feet = player.blockPosition();
        StringBuilder tried = new StringBuilder();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            double x = feet.getX() + 0.5;
            double z = feet.getZ() + 0.5;
            player.teleportTo(x - direction.getStepX() * 0.4, start.y, z - direction.getStepZ() * 0.4);
            GateGeometry placed = GatePlacement.place(GateMount.STANDING, x + direction.getStepX() * 2.0, feet.getY(), z + direction.getStepZ() * 2.0, 0.0F,
                    false, GateSize.SMALL);
            GateGeometry turned = GatePlacement.rotated(placed, false, false);
            GateGeometry mounted = turned == null ? null : GatePlacement.rotated(turned, true, false);
            if (turned == null || mounted == null || !fits(level, placed) || !fits(level, turned) || !fits(level, mounted)) {
                tried.append(direction.getName()).append(' ');
                continue;
            }
            GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
            if (gate == null) {
                throw new IllegalStateException("Gate nicht erzeugt");
            }
            gate.setSize(GateSize.SMALL);
            gate.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
            gate.setMount(placed.mount(), placed.flipped());
            gate.snapTo(placed.x(), placed.y(), placed.z(), placed.yawDegrees(), 0.0F);
            if (!level.addFreshEntity(gate)) {
                throw new IllegalStateException("Gate nicht gespawnt");
            }
            s.gateId = gate.getId();
            s.startYaw = gate.facingYaw();
            s.serverYaw = s.startYaw;
            s.expectYaw = turned.yawDegrees();
            s.serverMount = gate.mount();
            s.expectMount = mounted.mount();
            return;
        }
        player.teleportTo(start.x, start.y, start.z);
        s.placeProblem = "kein freier Platz vor dem Spieler (" + tried.toString().trim() + ")";
    }

    private static boolean fits(ServerLevel level, GateGeometry geometry) {
        return GateItem.insideWorld(level, geometry) && GateItem.free(level, geometry, geometry.mount().base(), true);
    }

    private static void readGate(MinecraftServer server, Scenario s) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(s.gateId) instanceof GateEntity gate) {
                s.serverYaw = gate.facingYaw();
                s.serverMount = gate.mount();
                return;
            }
        }
    }

    private static void removeGate(MinecraftServer server, Scenario s) {
        if (s.gateId < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(s.gateId) instanceof GateEntity gate) {
                gate.discard();
            }
        }
        s.gateId = -1;
        Vec3 saved = s.savedPos;
        if (saved != null) {
            firstPlayer(server).teleportTo(saved.x, saved.y, saved.z);
            s.savedPos = null;
        }
    }

    private static @Nullable StepResult aimAtGate(Minecraft mc, LocalPlayer player, Scenario s, int waited) {
        Entity entity = mc.level == null || s.gateId < 0 ? null : mc.level.getEntity(s.gateId);
        if (!(entity instanceof GateEntity gate)) {
            return waited > 100 ? StepResult.fail("platziertes Prüf-Gate kommt beim Client nicht an") : StepResult.pending();
        }
        Vec3 eye = player.getEyePosition();
        AABB nearest = null;
        double best = Double.MAX_VALUE;
        for (AABB box : gate.colliders()) {
            double distance = box.getCenter().distanceToSqr(eye);
            if (distance < best) {
                best = distance;
                nearest = box;
            }
        }
        if (nearest == null) {
            return StepResult.fail("Prüf-Gate ohne Kollisionsteile");
        }
        Vec3 to = nearest.getCenter().subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        if (waited >= SETTLE && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == gate) {
            return null;
        }
        return waited > 100 ? StepResult.fail("Blick trifft das Prüf-Gate nicht (Abstand " + String.format("%.2f", Math.sqrt(best)) + ", Ziel " + mc.hitResult + ")")
                : StepResult.pending();
    }

    private static void spawnAndFly(MinecraftServer server, Scenario s) {
        ServerPlayer player = firstPlayer(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 feet = player.position();
        drone.snapTo(feet.x, feet.y, feet.z + 3.0, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf().rotationY((float) Math.PI));
        drone.setBuild(DroneBuild.FREESTYLE);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        s.droneId = drone.getId();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElseThrow();
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(RaceModule.gateItem(GateSize.SMALL).orElseThrow()));
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void cleanup(SelfTest.StepContext ctx, Scenario s) {
        if (s.cleaned) {
            return;
        }
        s.cleaned = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyShift.setDown(false);
        PilotSession.INSTANCE.clearTestInput();
        KeyMapping rotate = GateMountKeys.mapping();
        if (rotate != null && s.savedRotate != null) {
            rotate.setKey(s.savedRotate);
        }
        if (s.savedFlight != null) {
            InputKeys.FLIGHT_MODE.setKey(s.savedFlight);
        }
        KeyMapping.resetMapping();
        if (rotate != null) {
            InputKeys.drain(rotate);
        }
        InputKeys.drain(InputKeys.FLIGHT_MODE);
        if (mc.player != null && s.savedPitch != null) {
            mc.player.setXRot(s.savedPitch);
        }
        if (mc.player != null && s.savedYaw != null) {
            mc.player.setYRot(s.savedYaw);
        }
        GateMountKeys.setPreference(GatePlacement.Preference.DEFAULT);
        ctx.onServer(server -> {
            removeGate(server, s);
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = s.droneId >= 0 ? level.getEntity(s.droneId) : null;
                if (entity instanceof QuadcopterEntity drone) {
                    DronePiloting.release(drone);
                    drone.discard();
                }
            }
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (!players.isEmpty()) {
                ServerPlayer player = players.getFirst();
                player.setItemSlot(EquipmentSlot.MAINHAND, s.previousMain == null ? ItemStack.EMPTY : s.previousMain);
                player.setItemSlot(EquipmentSlot.OFFHAND, s.previousOff == null ? ItemStack.EMPTY : s.previousOff);
                player.setItemSlot(EquipmentSlot.HEAD, s.previousHead == null ? ItemStack.EMPTY : s.previousHead);
            }
        });
    }

    private static ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static final class ControlsScene implements Screenshots.Scene {
        private InputConstants.@Nullable Key savedRotate;
        private InputConstants.@Nullable Key savedFlight;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof KeyBindsScreen screen)) {
                KeyMapping rotate = rotate();
                if (this.savedRotate == null || this.savedFlight == null) {
                    this.savedRotate = InputConstants.getKey(rotate.saveString());
                    this.savedFlight = InputConstants.getKey(InputKeys.FLIGHT_MODE.saveString());
                }
                rotate.setKey(rotate.getDefaultKey());
                InputKeys.FLIGHT_MODE.setKey(InputKeys.FLIGHT_MODE.getDefaultKey());
                KeyMapping.resetMapping();
                mc.gui.setScreen(new KeyBindsScreen(null, mc.options));
                return StepResult.pending();
            }
            KeyBindsList list = list(screen);
            if (list == null) {
                return StepResult.fail("Steuerung ohne Tastenliste");
            }
            int first = Math.min(row(rotate()), row(InputKeys.FLIGHT_MODE));
            int last = Math.max(row(rotate()), row(InputKeys.FLIGHT_MODE));
            double middle = (list.getRowTop(first) + list.getRowBottom(last)) / 2.0;
            list.setScrollAmount(list.scrollAmount() + middle - list.getY() - list.getHeight() / 2.0);
            if (list.getRowTop(first) < list.getY() || list.getRowBottom(last) > list.getBottom()) {
                return StepResult.fail("Steuerung: Gate drehen und Flugmodus nicht zusammen sichtbar (Zeilen " + list.getRowTop(first) + "–"
                        + list.getRowBottom(last) + ", Liste " + list.getY() + "–" + list.getBottom() + ")");
            }
            ctx.log("Steuerung: Gate drehen „" + describe(list, rotate()) + "“, Flugmodus „" + describe(list, InputKeys.FLIGHT_MODE) + "“");
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.savedRotate != null && this.savedFlight != null) {
                rotate().setKey(this.savedRotate);
                InputKeys.FLIGHT_MODE.setKey(this.savedFlight);
                KeyMapping.resetMapping();
                this.savedRotate = null;
                this.savedFlight = null;
            }
            if (mc.gui.screen() instanceof KeyBindsScreen) {
                mc.gui.setScreen(null);
            }
        }
    }

    private static final class TooltipScene implements Screenshots.Scene {
        private static final int SLOT = 9;
        private final Supplier<ItemStack> source;
        private @Nullable CompletableFuture<Void> placed;
        private volatile @Nullable ItemStack previous;
        private volatile @Nullable GameType previousMode;

        private TooltipScene(Supplier<ItemStack> source) {
            this.source = source;
        }

        private ItemStack stack() {
            return this.source.get();
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.placed == null) {
                ItemStack stack = this.stack();
                this.placed = ctx.onServer(server -> {
                    ServerPlayer player = firstPlayer(server);
                    player.closeContainer();
                    this.previousMode = player.gameMode();
                    player.setGameMode(GameType.SURVIVAL);
                    this.previous = player.getInventory().getItem(SLOT).copy();
                    player.getInventory().setItem(SLOT, stack);
                });
                return StepResult.pending();
            }
            if (this.placed.isCompletedExceptionally()) {
                return StepResult.fail("Tooltip-Szene: " + this.placed.exceptionNow().getMessage());
            }
            ItemStack shown = mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getItem(SLOT);
            if (!this.placed.isDone() || !ItemStack.isSameItem(shown, this.stack()) || mc.player.hasInfiniteMaterials()) {
                return ctx.tick() > 200 ? StepResult.fail("Tooltip-Szene: Item oder Überlebensmodus kommt nicht an") : StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                if (ctx.tick() > 400) {
                    return StepResult.fail("Tooltip-Szene: Inventar öffnet nicht (" + mc.gui.screen() + ")");
                }
                TooltipShift.override(true);
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, SLOT));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            TooltipShift.override(null);
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ShowcaseInventoryScreen) {
                mc.gui.setScreen(null);
            }
            ItemStack restore = this.previous;
            GameType mode = this.previousMode;
            ctx.onServer(server -> {
                ServerPlayer player = firstPlayer(server);
                player.getInventory().setItem(SLOT, restore == null ? ItemStack.EMPTY : restore);
                if (mode != null) {
                    player.setGameMode(mode);
                }
            });
            this.placed = null;
            this.previous = null;
            this.previousMode = null;
        }
    }

    private enum Phase {
        SETUP,
        GATE,
        GATE_TURNED,
        GATE_SNEAK,
        REMOTE,
        REMOTE_PRESSED,
        TOOL,
        TOOL_PRESSED,
        PLACED_AIM,
        PLACED_ROTATED,
        PLACED_SNEAK_AIM,
        PLACED_MOUNTED,
        SESSION,
        SESSION_PRESSED,
        REBOUND_R,
        REBOUND_OTHER
    }

    private static final class Scenario {
        private Phase phase = Phase.SETUP;
        private int since;
        private volatile int droneId = -1;
        private volatile @Nullable ItemStack previousMain;
        private volatile @Nullable ItemStack previousOff;
        private volatile @Nullable ItemStack previousHead;
        private InputConstants.@Nullable Key savedRotate;
        private InputConstants.@Nullable Key savedFlight;
        private @Nullable Float savedPitch;
        private @Nullable Float savedYaw;
        private volatile @Nullable Vec3 savedPos;
        private volatile int gateId = -1;
        private volatile @Nullable String placeProblem;
        private volatile float startYaw;
        private volatile float serverYaw;
        private volatile float expectYaw;
        private volatile @Nullable GateMount serverMount;
        private volatile @Nullable GateMount expectMount;
        private boolean polled;
        private GateMount.Surface surface = GateMount.Surface.FLOOR;
        private GatePlacement.Preference before = GatePlacement.Preference.DEFAULT;
        private @Nullable StabMode modeBefore;
        private @Nullable CompletableFuture<Void> pending;
        private boolean cleaned;

        private void enter(Phase next, int tick) {
            this.phase = next;
            this.since = tick;
        }
    }
}
