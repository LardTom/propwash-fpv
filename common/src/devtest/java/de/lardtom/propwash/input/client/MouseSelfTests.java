package de.lardtom.propwash.input.client;

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
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.MouseAxis;
import de.lardtom.propwash.input.MouseDemo;
import de.lardtom.propwash.input.MouseMode;
import de.lardtom.propwash.input.MouseSettings;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.client.screen.MousePreview;
import de.lardtom.propwash.input.client.screen.MouseSetupScreen;
import de.lardtom.propwash.input.client.screen.ReceiverTab;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.input.client.screen.remote.MouseDemoView;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.SettingsSearch;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.search.SearchBar;
import de.lardtom.propwash.ui.client.theme.Metrics;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class MouseSelfTests {
    static final float COUNTS_PER_CM = MouseDemo.DPI / 2.54F;
    private static final int MAX_TICKS = 2400;
    private static final float THROTTLE_NOTCHES = 14.0F;

    private MouseSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("input: Maus + Tastatur – Quelle ohne Gerät", 80, MouseSelfTests::sourceStep);
        SelfTest.clientStep("input: Maus + Tastatur – Maus wird Knüppel, Menü sperrt, kurzer Flug", MAX_TICKS + 100, MouseSelfTests::flightStep);
        SelfTest.clientStep("input: Maus + Tastatur – Empfänger-Tab, Testfläche, Suche und Assistent", 900, MouseSelfTests::uiStep);
    }

    public static void registerScreenshots(boolean german) {
        String suffix = german ? " deutsch" : "";
        String file = german ? "_de" : "";
        Screenshots.step("bildschirmfotos: Maus + Tastatur, Empfänger-Tab mit virtuellen Knüppeln" + suffix, "receiver_mouse" + file, 6, new TabScene());
        Screenshots.step("bildschirmfotos: Maus + Tastatur, Assistent Gerät" + suffix, "receiver_wizard_mouse_device" + file, 6, new WizardScene(false));
        Screenshots.step("bildschirmfotos: Maus + Tastatur, Assistent Maus-Schritt" + suffix, "receiver_wizard_mouse" + file, 6, new WizardScene(true));
    }

    static MouseSettings testSettings() {
        MouseSettings s = new MouseSettings();
        s.mode = MouseMode.AUTO;
        s.xAxis = MouseAxis.ROLL;
        s.deadzone = 0.0F;
        s.expo = 0.0F;
        s.returnSpeed = 0.0F;
        s.sensitivity = 1.0F;
        s.dpi = MouseDemo.DPI;
        return s;
    }

    private static StepResult sourceStep(SelfTest.StepContext ctx) {
        InputConfig config = InputConfig.get();
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            state.put("selected", config.selectedGuid);
            state.put("frames", RadioState.INSTANCE.frameCount());
            TransmitterInput.INSTANCE.simulate(null);
            config.selectedGuid = InputConfig.MOUSE_GUID;
            TransmitterInput.INSTANCE.rescanSoon();
            return StepResult.pending();
        }
        long frames = (Long) state.get("frames");
        boolean settled = RadioState.INSTANCE.frameCount() >= frames + 3 && TransmitterInput.INSTANCE.openDevice() == null
                && InputConfig.MOUSE_GUID.equals(RadioState.INSTANCE.deviceGuid());
        if (!settled && ctx.tick() < 79) {
            return StepResult.pending();
        }
        try {
            RadioState radio = RadioState.INSTANCE;
            DeviceInfo open = TransmitterInput.INSTANCE.openDevice();
            if (open != null) {
                return StepResult.fail("Gerät trotz Maus + Tastatur offen: " + open.name());
            }
            if (radio.source() != RadioSource.KEYBOARD || !radio.connected() || !InputConfig.MOUSE_GUID.equals(radio.deviceGuid())) {
                return StepResult.fail("Quelle " + radio.source() + " / " + radio.deviceGuid());
            }
            if (!I18n.get("propwash.input.mouse_keyboard").equals(radio.deviceName())) {
                return StepResult.fail("Gerätename „" + radio.deviceName() + "“");
            }
            if (!radio.sticks().equals(StickPositions.IDLE)) {
                return StepResult.fail("Knüppel ohne Flug nicht in Ruhe: " + radio.sticks());
            }
            return StepResult.check(!MouseCapture.INSTANCE.flying() && !MouseCapture.INSTANCE.wheelThrottle(), "Maus nimmt ohne Flug Bewegungen an");
        } finally {
            config.selectedGuid = (String) state.get("selected");
            TransmitterInput.INSTANCE.rescanSoon();
        }
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        Flight s = (Flight) ctx.state().computeIfAbsent("mouse_flight", key -> new Flight());
        try {
            StepResult result = s.advance(ctx);
            if (result.status() != StepResult.Status.PENDING) {
                s.cleanup(ctx);
            }
            return result;
        } catch (RuntimeException e) {
            s.cleanup(ctx);
            throw e;
        }
    }

    static void press(KeyMapping mapping) {
        KeyMapping.click(InputConstants.getKey(mapping.saveString()));
    }

    private static String sticks() {
        StickPositions p = RadioState.INSTANCE.sticks();
        return String.format(Locale.ROOT, "R %.3f N %.3f G %.3f Gas %.3f", p.roll(), p.pitch(), p.yaw(), p.throttle());
    }

    private enum Stage {
        PREPARE,
        SOURCE,
        SESSION,
        HOLD_PUSH,
        HOLD_KEEP,
        INVERT_PUSH,
        INVERT_KEY,
        NORMAL_KEY,
        KEY_RELEASE,
        SCREEN,
        SCREEN_MOVE,
        CENTER_PUSH,
        CENTER_PRESS,
        ACRO,
        RATE,
        RATE_STOP,
        ANGLE,
        ARM,
        THROTTLE,
        CLIMB,
        FORWARD,
        DISARM
    }

    private static final class Flight {
        private Stage stage = Stage.PREPARE;
        private int since;
        private @Nullable CompletableFuture<Void> pending;
        private int droneId = -1;
        private @Nullable ItemStack previousHead;
        private @Nullable ItemStack previousHand;
        private @Nullable String selected;
        private @Nullable MouseSettings mouse;
        private @Nullable Boolean startInAngle;
        private @Nullable Boolean invertPitch;
        private @Nullable Screen screen;
        private float rateMin = Float.MAX_VALUE;
        private float rateMax = -Float.MAX_VALUE;
        private double startY;
        private @Nullable Vec3 startPos;
        private boolean cleaned;

        private void enter(Stage next, int tick) {
            this.stage = next;
            this.since = tick;
        }

        private StepResult advance(SelfTest.StepContext ctx) {
            if (this.pending != null) {
                if (this.pending.isCompletedExceptionally()) {
                    return StepResult.fail(this.stage + ": Server-Aktion fehlgeschlagen: " + this.pending.exceptionNow().getMessage());
                }
                if (!this.pending.isDone()) {
                    return StepResult.pending();
                }
                this.pending = null;
            }
            if (ctx.tick() > MAX_TICKS) {
                return StepResult.fail("Zeit abgelaufen in " + this.stage + " (" + sticks() + ")");
            }
            Minecraft mc = Minecraft.getInstance();
            PilotSession session = PilotSession.INSTANCE;
            int waited = ctx.tick() - this.since;
            StickPositions p = RadioState.INSTANCE.sticks();
            return switch (this.stage) {
                case PREPARE -> {
                    InputConfig config = InputConfig.get();
                    this.selected = config.selectedGuid;
                    this.mouse = config.mouse.copy();
                    this.startInAngle = config.keyboard.startInAngle;
                    this.invertPitch = config.invertPitch;
                    config.invertPitch = false;
                    config.selectedGuid = InputConfig.MOUSE_GUID;
                    config.mouse = testSettings();
                    config.keyboard.startInAngle = true;
                    TransmitterInput.INSTANCE.simulate(null);
                    TransmitterInput.INSTANCE.forceMouseCapture(true);
                    TransmitterInput.INSTANCE.rescanSoon();
                    this.enter(Stage.SOURCE, ctx.tick());
                    yield StepResult.pending();
                }
                case SOURCE -> {
                    if (!InputConfig.MOUSE_GUID.equals(RadioState.INSTANCE.deviceGuid())) {
                        yield waited > 60 ? StepResult.fail("Maus + Tastatur wird nicht Quelle: " + RadioState.INSTANCE.deviceGuid()) : StepResult.pending();
                    }
                    this.pending = ctx.onServer(this::spawnAndFly);
                    this.enter(Stage.SESSION, ctx.tick());
                    yield StepResult.pending();
                }
                case SESSION -> {
                    QuadcopterEntity drone = session.drone();
                    if (drone == null || drone.getId() != this.droneId || !session.isFpv() || !MouseCapture.INSTANCE.flying()) {
                        yield waited > 200 ? StepResult.fail("keine FPV-Sitzung mit Maus: Sitzung " + session.isActive() + ", Maus "
                                + MouseCapture.INSTANCE.flying()) : StepResult.pending();
                    }
                    session.clearTestInput();
                    if (session.flightMode() != StabMode.ANGLE) {
                        yield StepResult.fail("Maus + Tastatur startet nicht in Angle: " + session.flightMode());
                    }
                    MouseCapture.INSTANCE.inject(0.0F, -3.0F * COUNTS_PER_CM, 0.0F);
                    this.enter(Stage.HOLD_PUSH, ctx.tick());
                    yield StepResult.pending();
                }
                case HOLD_PUSH -> {
                    if (Math.abs(p.pitch() - 0.5F) > 0.01F || Math.abs(p.roll()) > 0.01F) {
                        yield waited > 20 ? StepResult.fail("3 cm vor gibt nicht Nick 50 % (Angle, Halten): " + sticks()) : StepResult.pending();
                    }
                    this.enter(Stage.HOLD_KEEP, ctx.tick());
                    yield StepResult.pending();
                }
                case HOLD_KEEP -> {
                    if (Math.abs(p.pitch() - 0.5F) > 0.01F) {
                        yield StepResult.fail("Nick bleibt im Modus Halten nicht stehen: " + sticks());
                    }
                    if (waited < 10) {
                        yield StepResult.pending();
                    }
                    InputConfig.get().invertPitch = true;
                    MouseCapture.INSTANCE.inject(0.0F, -6.0F * COUNTS_PER_CM, 0.0F);
                    this.enter(Stage.INVERT_PUSH, ctx.tick());
                    yield StepResult.pending();
                }
                case INVERT_PUSH -> {
                    if (Math.abs(p.pitch() + 0.5F) > 0.01F || Math.abs(p.roll()) > 0.01F) {
                        yield waited > 20 ? StepResult.fail("„Nick umkehren“: 6 cm vor führt von +50 % nicht auf Nick −50 %: " + sticks()) : StepResult.pending();
                    }
                    InputKeys.effective(InputKeys.PITCH_FORWARD).setDown(true);
                    this.enter(Stage.INVERT_KEY, ctx.tick());
                    yield StepResult.pending();
                }
                case INVERT_KEY -> {
                    if (p.pitch() > -0.95F) {
                        yield waited > 40 ? StepResult.fail("„Nick umkehren“: Taste „Nase runter“ zieht die Nase nicht hoch: " + sticks()) : StepResult.pending();
                    }
                    InputConfig.get().invertPitch = false;
                    this.enter(Stage.NORMAL_KEY, ctx.tick());
                    yield StepResult.pending();
                }
                case NORMAL_KEY -> {
                    float expected = -0.5F + InputConfig.get().keyboard.rollPitchLimit;
                    if (Math.abs(p.pitch() - expected) > 0.02F) {
                        yield waited > 60 ? StepResult.fail("ohne „Nick umkehren“ drückt die Taste „Nase runter“ nicht nach vorn (erwartet "
                                + expected + "): " + sticks()) : StepResult.pending();
                    }
                    InputKeys.effective(InputKeys.PITCH_FORWARD).setDown(false);
                    MouseCapture.INSTANCE.inject(0.0F, -6.0F * COUNTS_PER_CM, 0.0F);
                    this.enter(Stage.KEY_RELEASE, ctx.tick());
                    yield StepResult.pending();
                }
                case KEY_RELEASE -> {
                    if (waited < 30) {
                        yield StepResult.pending();
                    }
                    if (Math.abs(p.pitch() - 0.5F) > 0.01F || Math.abs(p.roll()) > 0.01F) {
                        yield StepResult.fail("nach „Nick umkehren“ steht der Knüppel nicht wieder auf Nick 50 %: " + sticks());
                    }
                    this.screen = new Screen(Component.literal("Propwash-Selbsttest")) {
                    };
                    mc.gui.setScreen(this.screen);
                    this.enter(Stage.SCREEN, ctx.tick());
                    yield StepResult.pending();
                }
                case SCREEN -> {
                    if (waited < 3) {
                        yield StepResult.pending();
                    }
                    if (MouseCapture.INSTANCE.flying() || MouseCapture.INSTANCE.wheelThrottle()) {
                        yield StepResult.fail("Maus fliegt trotz offenem Menü");
                    }
                    MouseCapture.INSTANCE.inject(2.0F * COUNTS_PER_CM, -2.0F * COUNTS_PER_CM, 3.0F);
                    this.enter(Stage.SCREEN_MOVE, ctx.tick());
                    yield StepResult.pending();
                }
                case SCREEN_MOVE -> {
                    if (waited < 5) {
                        yield StepResult.pending();
                    }
                    if (p.roll() != 0.0F || p.pitch() != 0.0F || p.throttle() != 0.0F) {
                        yield StepResult.fail("Maus bewegt die Knüppel trotz Menü: " + sticks());
                    }
                    mc.gui.setScreen(null);
                    this.screen = null;
                    this.enter(Stage.CENTER_PUSH, ctx.tick());
                    yield StepResult.pending();
                }
                case CENTER_PUSH -> {
                    if (!MouseCapture.INSTANCE.flying()) {
                        yield waited > 20 ? StepResult.fail("Maus nimmt nach dem Menü nicht wieder an") : StepResult.pending();
                    }
                    if (waited < 2) {
                        yield StepResult.pending();
                    }
                    if (waited == 2) {
                        MouseCapture.INSTANCE.inject(0.0F, -2.0F * COUNTS_PER_CM, 0.0F);
                        yield StepResult.pending();
                    }
                    if (Math.abs(p.pitch() - 1.0F / 3.0F) > 0.01F) {
                        yield waited > 25 ? StepResult.fail("2 cm vor gibt nicht Nick 33 %: " + sticks()) : StepResult.pending();
                    }
                    press(InputKeys.MOUSE_CENTER);
                    this.enter(Stage.CENTER_PRESS, ctx.tick());
                    yield StepResult.pending();
                }
                case CENTER_PRESS -> {
                    if (p.pitch() != 0.0F) {
                        yield waited > 10 ? StepResult.fail("Zentrieren-Taste stellt Nick nicht auf 0: " + sticks()) : StepResult.pending();
                    }
                    press(InputKeys.MODE_ACRO);
                    this.enter(Stage.ACRO, ctx.tick());
                    yield StepResult.pending();
                }
                case ACRO -> {
                    if (session.flightMode() != StabMode.ACRO) {
                        yield waited > 40 ? StepResult.fail("Acro-Taste wirkt nicht: " + session.flightMode()) : StepResult.pending();
                    }
                    MouseCapture.INSTANCE.simulateVelocity(15.0F * COUNTS_PER_CM, 0.0F);
                    this.enter(Stage.RATE, ctx.tick());
                    yield StepResult.pending();
                }
                case RATE -> {
                    if (waited < 6) {
                        yield StepResult.pending();
                    }
                    this.rateMin = Math.min(this.rateMin, p.roll());
                    this.rateMax = Math.max(this.rateMax, p.roll());
                    if (waited < 16) {
                        yield StepResult.pending();
                    }
                    MouseCapture.INSTANCE.simulateVelocity(0.0F, 0.0F);
                    if (this.rateMin < 0.42F || this.rateMax > 0.58F) {
                        yield StepResult.fail(String.format(Locale.ROOT, "15 cm/s gibt nicht Roll 50 %% (Acro, Tempo): %.3f … %.3f", this.rateMin, this.rateMax));
                    }
                    ctx.log(String.format(Locale.ROOT, "Acro, 15 cm/s: Roll %.3f … %.3f", this.rateMin, this.rateMax));
                    this.enter(Stage.RATE_STOP, ctx.tick());
                    yield StepResult.pending();
                }
                case RATE_STOP -> {
                    if (p.roll() != 0.0F) {
                        yield waited > 4 ? StepResult.fail("Knüppel federt nach dem Anhalten nicht zurück: " + sticks()) : StepResult.pending();
                    }
                    press(InputKeys.MODE_ANGLE);
                    this.enter(Stage.ANGLE, ctx.tick());
                    yield StepResult.pending();
                }
                case ANGLE -> {
                    if (session.flightMode() != StabMode.ANGLE) {
                        yield waited > 40 ? StepResult.fail("Angle-Taste wirkt nicht: " + session.flightMode()) : StepResult.pending();
                    }
                    if (waited < 3) {
                        yield StepResult.pending();
                    }
                    press(InputKeys.ARM);
                    this.enter(Stage.ARM, ctx.tick());
                    yield StepResult.pending();
                }
                case ARM -> {
                    if (!session.armed()) {
                        yield waited > 60 ? StepResult.fail("Scharfschalten mit der Taste klappt nicht, Sperren " + Integer.toBinaryString(session.armBlockers()))
                                : StepResult.pending();
                    }
                    MouseCapture.INSTANCE.inject(0.0F, 0.0F, THROTTLE_NOTCHES);
                    this.enter(Stage.THROTTLE, ctx.tick());
                    yield StepResult.pending();
                }
                case THROTTLE -> {
                    float wanted = THROTTLE_NOTCHES * InputConfig.get().mouse.wheelStep;
                    if (Math.abs(p.throttle() - wanted) > 0.01F) {
                        yield waited > 10 ? StepResult.fail("Mausrad gibt nicht " + wanted + " Gas: " + sticks()) : StepResult.pending();
                    }
                    QuadcopterEntity drone = session.drone();
                    if (drone == null) {
                        yield StepResult.fail("Drohne weg");
                    }
                    this.startY = drone.getY();
                    this.enter(Stage.CLIMB, ctx.tick());
                    yield StepResult.pending();
                }
                case CLIMB -> {
                    QuadcopterEntity drone = session.drone();
                    if (drone == null) {
                        yield StepResult.fail("Drohne weg");
                    }
                    if (waited < 30) {
                        yield StepResult.pending();
                    }
                    double climbed = drone.getY() - this.startY;
                    if (climbed < 0.5) {
                        yield StepResult.fail(String.format(Locale.ROOT, "Drohne steigt mit Mausrad-Gas nicht: %.2f m", climbed));
                    }
                    ctx.log(String.format(Locale.ROOT, "Mausrad-Gas %.2f: %.2f m gestiegen", p.throttle(), climbed));
                    this.startPos = drone.position();
                    MouseCapture.INSTANCE.inject(0.0F, -2.0F * COUNTS_PER_CM, 0.0F);
                    this.enter(Stage.FORWARD, ctx.tick());
                    yield StepResult.pending();
                }
                case FORWARD -> {
                    QuadcopterEntity drone = session.drone();
                    if (drone == null || this.startPos == null) {
                        yield StepResult.fail("Drohne weg");
                    }
                    if (waited < 30) {
                        yield StepResult.pending();
                    }
                    Vec3 moved = drone.position().subtract(this.startPos);
                    double horizontal = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
                    if (Math.abs(p.pitch() - 1.0F / 3.0F) > 0.01F || horizontal < 0.5) {
                        yield StepResult.fail(String.format(Locale.ROOT, "Maus vor bringt die Drohne nicht voran: %.2f m (%s)", horizontal, sticks()));
                    }
                    ctx.log(String.format(Locale.ROOT, "Maus 2 cm vor (Nick %.2f): %.2f m geflogen", p.pitch(), horizontal));
                    MouseCapture.INSTANCE.inject(0.0F, 0.0F, -THROTTLE_NOTCHES);
                    press(InputKeys.MOUSE_CENTER);
                    press(InputKeys.ARM);
                    this.enter(Stage.DISARM, ctx.tick());
                    yield StepResult.pending();
                }
                case DISARM -> {
                    if (session.armed()) {
                        yield waited > 40 ? StepResult.fail("Entschärfen mit der Taste klappt nicht") : StepResult.pending();
                    }
                    yield StepResult.check(p.throttle() == 0.0F && p.pitch() == 0.0F, "nach dem Flug nicht in Ruhe: " + sticks());
                }
            };
        }

        private void spawnAndFly(MinecraftServer server) {
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
            this.droneId = drone.getId();
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (goggles.isEmpty()) {
                throw new IllegalStateException("Kreativ-Brille nicht registriert");
            }
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            this.previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
            player.setItemSlot(EquipmentSlot.HEAD, goggles);
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            this.previousHand = player.getMainHandItem().copy();
            player.setItemSlot(EquipmentSlot.MAINHAND, remote);
            DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
            if (result != DronePiloting.StartResult.STARTED) {
                throw new IllegalStateException("FPV-Start verweigert: " + result);
            }
        }

        private void cleanup(SelfTest.StepContext ctx) {
            if (this.cleaned) {
                return;
            }
            this.cleaned = true;
            Minecraft mc = Minecraft.getInstance();
            if (this.screen != null && mc.gui.screen() == this.screen) {
                mc.gui.setScreen(null);
            }
            MouseCapture.INSTANCE.simulateVelocity(0.0F, 0.0F);
            TransmitterInput.INSTANCE.forceMouseCapture(false);
            for (KeyMapping key : List.of(InputKeys.MOUSE_CENTER, InputKeys.MODE_ACRO, InputKeys.MODE_ANGLE, InputKeys.ARM)) {
                InputKeys.drain(key);
            }
            PilotSession.INSTANCE.clearTestInput();
            InputConfig config = InputConfig.get();
            if (this.selected != null) {
                config.selectedGuid = this.selected;
            }
            if (this.mouse != null) {
                config.mouse = this.mouse;
            }
            if (this.invertPitch != null) {
                config.invertPitch = this.invertPitch;
            }
            InputKeys.effective(InputKeys.PITCH_FORWARD).setDown(false);
            if (this.startInAngle != null) {
                config.keyboard.startInAngle = this.startInAngle;
            }
            TransmitterInput.INSTANCE.rescanSoon();
            int id = this.droneId;
            ItemStack head = this.previousHead;
            ItemStack hand = this.previousHand;
            ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = id >= 0 ? level.getEntity(id) : null;
                    if (entity instanceof QuadcopterEntity drone) {
                        DronePiloting.release(drone);
                        drone.discard();
                    }
                }
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (!players.isEmpty()) {
                    ServerPlayer player = players.getFirst();
                    if (head != null) {
                        player.setItemSlot(EquipmentSlot.HEAD, head);
                    }
                    if (hand != null) {
                        player.setItemSlot(EquipmentSlot.MAINHAND, hand);
                    }
                }
            });
        }
    }

    private static ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static StepResult uiStep(SelfTest.StepContext ctx) {
        Ui s = (Ui) ctx.state().computeIfAbsent("mouse_ui", key -> new Ui());
        try {
            StepResult result = s.advance(ctx);
            if (result.status() != StepResult.Status.PENDING) {
                s.cleanup();
            }
            return result;
        } catch (RuntimeException e) {
            s.cleanup();
            throw e;
        }
    }

    private static final class Ui {
        private int phase;
        private int since;
        private @Nullable String selected;
        private @Nullable MouseSettings mouse;
        private @Nullable Boolean invertPitch;
        private long demoFrames;
        private boolean cleaned;

        private StepResult advance(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            int waited = ctx.tick() - this.since;
            switch (this.phase) {
                case 0 -> {
                    InputConfig config = InputConfig.get();
                    this.selected = config.selectedGuid;
                    this.mouse = config.mouse.copy();
                    this.invertPitch = config.invertPitch;
                    config.invertPitch = false;
                    config.selectedGuid = InputConfig.MOUSE_GUID;
                    config.mouse = new MouseSettings();
                    mc.gui.setScreen(null);
                    ConfigTabs.open(null, ReceiverTab.ID);
                    return this.next(ctx);
                }
                case 1 -> {
                    if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                        return waited > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
                    }
                    ConfigTab active = screen.activeTab();
                    if (!(active instanceof ReceiverTab tab)) {
                        screen.selectTab(ReceiverTab.ID);
                        return StepResult.pending();
                    }
                    if (!tab.mouseLayout() || screen.extractedFrames(ReceiverTab.ID) < 3) {
                        return waited > 100 ? StepResult.fail("Empfänger-Tab zeigt die Maus-Einstellungen nicht") : StepResult.pending();
                    }
                    if (!screen.tabErrors().isEmpty()) {
                        return StepResult.fail("Tab-Fehler: " + screen.tabErrors());
                    }
                    String misplaced = invertSwitchProblem(tab);
                    if (misplaced != null) {
                        return StepResult.fail("Maus + Tastatur: " + misplaced);
                    }
                    MousePreview preview = tab.mousePreview();
                    int[] pad = preview.padCenter();
                    if (!preview.mouseClicked(pad[0], pad[1], InputConstants.MOUSE_BUTTON_LEFT) || !preview.dragging()) {
                        return StepResult.fail("Testfläche nimmt den Klick nicht an");
                    }
                    MouseCapture.INSTANCE.inject(1.5F * COUNTS_PER_CM, -2.0F * COUNTS_PER_CM, 0.0F);
                    return this.next(ctx);
                }
                case 2 -> {
                    if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof ReceiverTab tab)) {
                        return StepResult.fail("Empfänger-Tab geschlossen");
                    }
                    StickPositions shown = tab.mousePreview().sticks();
                    if (shown.roll() < 0.1F || shown.pitch() < 0.15F) {
                        return waited > 20 ? StepResult.fail("Testfläche bewegt die virtuellen Knüppel nicht: " + shown) : StepResult.pending();
                    }
                    tab.mousePreview().mouseReleased();
                    CycleButton<Boolean> invert = tab.invertPitchButton();
                    if (invert == null) {
                        return StepResult.fail("Schalter „Nick umkehren“ verschwunden");
                    }
                    invert.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    if (!tab.pitchInverted() || !invert.getValue() || InputConfig.get().invertPitch) {
                        return StepResult.fail("„Nick umkehren“ muss erst die Arbeitskopie umschalten: Kopie " + tab.pitchInverted() + ", Knopf "
                                + invert.getValue() + ", gespeichert " + InputConfig.get().invertPitch);
                    }
                    String shownKeys = tab.shownMouseKeyRows().get(2).keys().getString();
                    if (!I18n.get("propwash.input.mouse.key.mouse_y_inverted").equals(shownKeys)) {
                        return StepResult.fail("Tastentabelle zeigt die Umkehrung nicht: „" + shownKeys + "“");
                    }
                    MousePreview preview = tab.mousePreview();
                    int[] pad = preview.padCenter();
                    if (!preview.mouseClicked(pad[0], pad[1], InputConstants.MOUSE_BUTTON_LEFT) || !preview.dragging()) {
                        return StepResult.fail("Testfläche nimmt den zweiten Klick nicht an");
                    }
                    MouseCapture.INSTANCE.inject(1.5F * COUNTS_PER_CM, -2.0F * COUNTS_PER_CM, 0.0F);
                    return this.next(ctx);
                }
                case 3 -> {
                    if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof ReceiverTab tab)) {
                        return StepResult.fail("Empfänger-Tab geschlossen");
                    }
                    StickPositions shown = tab.mousePreview().sticks();
                    if (shown.roll() < 0.1F || shown.pitch() > -0.15F) {
                        return waited > 20 ? StepResult.fail("Testfläche zeigt „Nick umkehren“ nicht (Maus vor muss Nick negativ geben): " + shown)
                                : StepResult.pending();
                    }
                    tab.mousePreview().mouseReleased();
                    String query = I18n.get("propwash.input.mouse.section");
                    SearchBar search = screen.search();
                    if (search == null) {
                        return StepResult.fail("Konfigurator ohne Suche");
                    }
                    search.focus();
                    search.type(query);
                    long receiverHits = search.hits().stream().map(SettingsSearch.Hit::entry)
                            .filter(e -> ReceiverTab.ID.equals(e.tabId())).count();
                    if (receiverHits < 5) {
                        return StepResult.fail("Suche „" + query + "“ findet nur " + receiverHits + " Maus-Einstellungen: "
                                + search.hits().stream().map(h -> h.entry().label()).toList());
                    }
                    ctx.log("Suche „" + query + "“: " + receiverHits + " Treffer im Empfänger-Tab");
                    String invertLabel = I18n.get("propwash.input.invert_pitch");
                    for (String word : List.of("invert", "pitch", "reverse")) {
                        search.type(word);
                        boolean found = search.hits().stream().map(SettingsSearch.Hit::entry)
                                .anyMatch(e -> ReceiverTab.ID.equals(e.tabId()) && invertLabel.equals(e.label()));
                        if (!found) {
                            return StepResult.fail("Suche „" + word + "“ findet „" + invertLabel + "“ nicht: "
                                    + search.hits().stream().map(h -> h.entry().label()).toList());
                        }
                    }
                    screen.discardAndClose();
                    if (InputConfig.get().invertPitch) {
                        return StepResult.fail("verworfene Änderung hat „Nick umkehren“ trotzdem gespeichert");
                    }
                    ReceiverWizardScreen wizard = ReceiverWizardScreen.standalone(null);
                    mc.gui.setScreen(wizard);
                    return this.next(ctx);
                }
                case 4 -> {
                    if (!(mc.gui.screen() instanceof ReceiverWizardScreen wizard)) {
                        return waited > 40 ? StepResult.fail("Assistent öffnet nicht") : StepResult.pending();
                    }
                    if (!wizard.working().mouseSelected() || waited < 3) {
                        return waited > 40 ? StepResult.fail("Assistent übernimmt Maus + Tastatur nicht") : StepResult.pending();
                    }
                    wizard.openMouseStep();
                    return this.next(ctx);
                }
                case 5 -> {
                    if (!(mc.gui.screen() instanceof MouseSetupScreen step)) {
                        return waited > 40 ? StepResult.fail("Maus-Schritt öffnet nicht") : StepResult.pending();
                    }
                    if (waited < 8) {
                        return StepResult.pending();
                    }
                    if (step.demo().lastFrame() == null) {
                        return StepResult.fail("Maus-Vorführung wird nicht gezeichnet");
                    }
                    if (!step.demo().drawn()) {
                        return StepResult.fail("Funke in der Maus-Vorführung fehlt");
                    }
                    CycleButton<Boolean> invert = step.invertPitchButton();
                    if (invert == null || !invert.visible || invert.getValue()
                            || !I18n.get("propwash.input.invert_pitch").equals(labelOf(invert))) {
                        return StepResult.fail("Maus-Schritt ohne Schalter „Nick umkehren“ auf Aus");
                    }
                    float forward = forwardSeconds();
                    step.demo().freezeAt(forward);
                    this.demoFrames = step.demo().frames();
                    return this.next(ctx);
                }
                case 6 -> {
                    if (!(mc.gui.screen() instanceof MouseSetupScreen step)) {
                        return StepResult.fail("Maus-Schritt geschlossen");
                    }
                    MouseDemo.Frame frame = step.demo().lastFrame();
                    if (step.demo().frames() < this.demoFrames + 2 || frame == null) {
                        return waited > 40 ? StepResult.fail("Maus-Vorführung zeichnet nicht weiter") : StepResult.pending();
                    }
                    if (frame.sticks().pitch() < 0.25F) {
                        return StepResult.fail("Vorführung „Maus nach vorn“ ohne Umkehrung nicht Nick vor: " + frame.sticks().pitch());
                    }
                    CycleButton<Boolean> invert = step.invertPitchButton();
                    if (invert == null) {
                        return StepResult.fail("Schalter „Nick umkehren“ im Maus-Schritt verschwunden");
                    }
                    invert.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    if (!step.working().invertPitch) {
                        return StepResult.fail("Schalter im Maus-Schritt stellt „Nick umkehren“ nicht um");
                    }
                    step.demo().freezeAt(forwardSeconds());
                    this.demoFrames = step.demo().frames();
                    return this.next(ctx);
                }
                case 7 -> {
                    if (!(mc.gui.screen() instanceof MouseSetupScreen step)) {
                        return StepResult.fail("Maus-Schritt geschlossen");
                    }
                    MouseDemo.Frame frame = step.demo().lastFrame();
                    if (step.demo().frames() < this.demoFrames + 2 || frame == null) {
                        return waited > 40 ? StepResult.fail("Maus-Vorführung zeichnet nach dem Umschalten nicht weiter") : StepResult.pending();
                    }
                    if (frame.sticks().pitch() > -0.25F) {
                        return StepResult.fail("Vorführung „Maus nach vorn“ mit Umkehrung nicht Nick zurück: " + frame.sticks().pitch());
                    }
                    String caption = MouseDemoView.caption(MouseDemo.Cue.FORWARD, step.working().mouse, step.working().invertPitch, frame.sticks())
                            .getString();
                    if (!I18n.get("propwash.input.mouse.demo.forward.inverted").equals(caption)) {
                        return StepResult.fail("Vorführung beschriftet die Umkehrung nicht: „" + caption + "“");
                    }
                    step.onClose();
                    return this.next(ctx);
                }
                case 8 -> {
                    if (!(mc.gui.screen() instanceof ReceiverWizardScreen)) {
                        return waited > 40 ? StepResult.fail("„Zurück“ führt nicht zum Assistenten") : StepResult.pending();
                    }
                    mc.gui.setScreen(null);
                    InputConfig config = InputConfig.get();
                    config.selectedGuid = InputConfig.KEYBOARD_GUID;
                    config.invertPitch = false;
                    ConfigTabs.open(null, ReceiverTab.ID);
                    return this.next(ctx);
                }
                default -> {
                    if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof ReceiverTab tab)
                            || screen.extractedFrames(ReceiverTab.ID) < 3) {
                        return waited > 200 ? StepResult.fail("Empfänger-Tab für die Tastatur öffnet nicht") : StepResult.pending();
                    }
                    if (tab.mouseLayout()) {
                        return StepResult.fail("Empfänger-Tab zeigt für „Tastatur“ das Maus-Layout");
                    }
                    String misplaced = invertSwitchProblem(tab);
                    if (misplaced != null) {
                        return StepResult.fail("Tastatur: " + misplaced);
                    }
                    if (!screen.tabErrors().isEmpty()) {
                        return StepResult.fail("Tab-Fehler: " + screen.tabErrors());
                    }
                    return StepResult.pass();
                }
            }
        }

        private StepResult next(SelfTest.StepContext ctx) {
            this.phase++;
            this.since = ctx.tick();
            return StepResult.pending();
        }

        private static float forwardSeconds() {
            for (MouseDemo.Phase phase : MouseDemo.all()) {
                if (phase.cue() == MouseDemo.Cue.FORWARD) {
                    return (phase.start() + phase.end()) * 0.5F;
                }
            }
            throw new IllegalStateException("Vorführung ohne Phase „Maus nach vorn“");
        }

        private static String labelOf(AbstractWidget widget) {
            String text = widget.getMessage().getString();
            int colon = text.indexOf(':');
            return colon < 0 ? text : text.substring(0, colon);
        }

        private static @Nullable String invertSwitchProblem(ReceiverTab tab) {
            CycleButton<Boolean> invert = tab.invertPitchButton();
            AbstractWidget source = tab.sourceButton();
            if (invert == null || source == null || !invert.visible) {
                return "Schalter „Nick umkehren“ fehlt im Empfänger-Tab";
            }
            if (invert.getValue()) {
                return "Schalter „Nick umkehren“ steht ohne Zutun auf An";
            }
            if (!I18n.get("propwash.input.invert_pitch").equals(labelOf(invert))) {
                return "Schalter heißt „" + invert.getMessage().getString() + "“";
            }
            if (invert.getX() != source.getX() || invert.getY() != source.getY() + Metrics.ROW_HEIGHT || invert.getWidth() != source.getWidth()) {
                return "Schalter „Nick umkehren“ steht nicht direkt unter der Eingabequelle: Quelle " + source.getX() + "/" + source.getY()
                        + ", Schalter " + invert.getX() + "/" + invert.getY();
            }
            return null;
        }

        private void cleanup() {
            if (this.cleaned) {
                return;
            }
            this.cleaned = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            InputConfig config = InputConfig.get();
            if (this.selected != null) {
                config.selectedGuid = this.selected;
            }
            if (this.mouse != null) {
                config.mouse = this.mouse;
            }
            if (this.invertPitch != null) {
                config.invertPitch = this.invertPitch;
            }
            InputKeys.effective(InputKeys.PITCH_FORWARD).setDown(false);
        }
    }

    private static final class TabScene implements Screenshots.Scene {
        private int phase;
        private @Nullable String selected;
        private @Nullable MouseSettings mouse;
        private @Nullable Boolean invertPitch;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.phase == 0) {
                InputConfig config = InputConfig.get();
                this.selected = config.selectedGuid;
                this.mouse = config.mouse.copy();
                this.invertPitch = config.invertPitch;
                config.invertPitch = false;
                config.selectedGuid = InputConfig.MOUSE_GUID;
                config.mouse = new MouseSettings();
                TransmitterInput.INSTANCE.simulate(null);
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                ConfigTabs.open(null, ReceiverTab.ID);
                this.phase = 1;
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            if (!(screen.activeTab() instanceof ReceiverTab tab)) {
                screen.selectTab(ReceiverTab.ID);
                return StepResult.pending();
            }
            if (!tab.mouseLayout() || screen.extractedFrames(ReceiverTab.ID) < 3) {
                return StepResult.pending();
            }
            if (this.phase == 1) {
                int[] pad = tab.mousePreview().padCenter();
                tab.mousePreview().setAcro(true);
                tab.mousePreview().mouseClicked(pad[0], pad[1], InputConstants.MOUSE_BUTTON_LEFT);
                MouseCapture.INSTANCE.simulateVelocity(9.0F * COUNTS_PER_CM, -6.0F * COUNTS_PER_CM);
                this.phase = 2;
                return StepResult.pending();
            }
            StickPositions shown = tab.mousePreview().sticks();
            if (shown.roll() < 0.15F || shown.pitch() < 0.08F) {
                return ctx.tick() > 300 ? StepResult.fail("virtuelle Knüppel bleiben in der Mitte: " + shown) : StepResult.pending();
            }
            return StepResult.check(screen.tabErrors().isEmpty(), "Tab-Fehler: " + screen.tabErrors());
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            MouseCapture.INSTANCE.simulateVelocity(0.0F, 0.0F);
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                if (screen.activeTab() instanceof ReceiverTab tab) {
                    tab.mousePreview().mouseReleased();
                    tab.mousePreview().setAcro(false);
                }
                screen.discardAndClose();
            }
            InputConfig config = InputConfig.get();
            if (this.selected != null) {
                config.selectedGuid = this.selected;
            }
            if (this.mouse != null) {
                config.mouse = this.mouse;
            }
            if (this.invertPitch != null) {
                config.invertPitch = this.invertPitch;
            }
            InputKeys.effective(InputKeys.PITCH_FORWARD).setDown(false);
            this.phase = 0;
        }
    }

    private static final class WizardScene implements Screenshots.Scene {
        private final boolean step;
        private int phase;
        private @Nullable String selected;

        private WizardScene(boolean step) {
            this.step = step;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.phase == 0) {
                InputConfig config = InputConfig.get();
                this.selected = config.selectedGuid;
                config.selectedGuid = InputConfig.MOUSE_GUID;
                TransmitterInput.INSTANCE.simulate(null);
                mc.gui.setScreen(ReceiverWizardScreen.standalone(null));
                this.phase = 1;
                return StepResult.pending();
            }
            if (this.phase == 1) {
                if (!(mc.gui.screen() instanceof ReceiverWizardScreen wizard) || TransmitterInput.INSTANCE.raw() != null) {
                    return ctx.tick() > 200 ? StepResult.fail("Assistent ohne Gerät öffnet nicht") : StepResult.pending();
                }
                if (!this.step) {
                    return StepResult.pass();
                }
                wizard.openMouseStep();
                this.phase = 2;
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof MouseSetupScreen screen)) {
                return ctx.tick() > 200 ? StepResult.fail("Maus-Schritt öffnet nicht") : StepResult.pending();
            }
            float t = 0.0F;
            for (MouseDemo.Phase phase : MouseDemo.all()) {
                if (phase.cue() == MouseDemo.Cue.FORWARD) {
                    t = phase.end() - 0.05F;
                }
            }
            screen.demo().freezeAt(t);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            if (this.selected != null) {
                InputConfig.get().selectedGuid = this.selected;
            }
            this.phase = 0;
        }
    }
}
