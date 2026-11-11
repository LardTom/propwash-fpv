package de.lardtom.propwash.input.client;

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
import de.lardtom.propwash.input.DeviceNames;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickMapper;
import de.lardtom.propwash.sim.ArmBlocker;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLGUID;
import org.lwjgl.sdl.SDLJoystick;
import org.lwjgl.sdl.SDL_GUID;
import org.lwjgl.sdl.SDL_VirtualJoystickDesc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class HotplugSelfTests {
    private static final String GROUP = "input: Hotplug – ";
    private static final String KEY = "hotplug";
    private static final String RADIO_NAME = "EdgeTX Propwash Hotplug Joystick";
    private static final String STICK_NAME = "Propwash Hotplug USB Joystick";
    private static final int STICK_VENDOR = 0x16C0;
    private static final int STICK_PRODUCT = 0x27DC;
    private static final int RADIO_AXES = 8;
    private static final int STICK_AXES = 4;
    private static final short THROTTLE_RAW = 16384;
    private static final short SWITCH_LOW = Short.MIN_VALUE;
    private static final int MAX_TICKS = 900;
    private static final int TAKEOVER_TICKS = 20;
    private static final int LOSS_TICKS = 20;
    private static final int HOLD_TICKS = 20;
    private static final List<Plugged> LEFTOVERS = new ArrayList<>();

    private HotplugSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Funke im FPV-Flug angesteckt löst ein nicht eingerichtetes Gerät sofort ab (gespeicherte Belegung, "
                + "Meldung „Funke verbunden“), abgezogen Failsafe ohne Rückfall auf das alte Gerät, wieder angesteckt weiter", MAX_TICKS,
                ctx -> step(ctx, true, false));
        SelfTest.clientStep(GROUP + "Funke im Tastaturflug angesteckt übernimmt sofort, abgezogen Failsafe", MAX_TICKS, ctx -> step(ctx, false, false));
        SelfTest.clientStep(GROUP + "Funke fliegt, Joystick im Flug dazu angesteckt: Funke behält den Flug, abgezogen Failsafe ohne Rückfall "
                + "auf den Joystick, wieder angesteckt weiter", MAX_TICKS, ctx -> step(ctx, false, true));
    }

    private static StepResult step(SelfTest.StepContext ctx, boolean withStick, boolean stickInFlight) {
        Scenario s = (Scenario) ctx.state().computeIfAbsent(KEY, key -> new Scenario(withStick, stickInFlight));
        StepResult result;
        try {
            result = run(ctx, s);
        } catch (RuntimeException | LinkageError e) {
            cleanup(ctx, s);
            throw e;
        }
        if (result.status() != StepResult.Status.PENDING) {
            cleanup(ctx, s);
            if (result.status() == StepResult.Status.PASS) {
                ctx.log(String.join("; ", s.notes));
            }
        }
        return result;
    }

    private static StepResult run(SelfTest.StepContext ctx, Scenario s) {
        CompletableFuture<Void> pending = s.pending;
        if (pending != null) {
            if (pending.isCompletedExceptionally()) {
                return StepResult.fail(s.phase + ": Server-Aktion fehlgeschlagen: " + pending.exceptionNow().getMessage());
            }
            if (!pending.isDone()) {
                return StepResult.pending();
            }
            s.pending = null;
        }
        if (ctx.tick() > MAX_TICKS - 40) {
            return StepResult.fail("Zeit abgelaufen in " + s.phase + " (" + describe(s) + ")");
        }
        TransmitterInput input = TransmitterInput.INSTANCE;
        PilotSession session = PilotSession.INSTANCE;
        int waited = ctx.tick() - s.since;
        if (s.phase.ordinal() > Phase.FLIGHT.ordinal() && !flying(s)) {
            return StepResult.fail(s.phase + ": FPV-Sitzung verloren (" + describe(s) + ")");
        }
        switch (s.phase) {
            case PREPARE -> {
                if (waited == 0) {
                    releaseLeftovers();
                    input.rescanSoon();
                }
                if (waited < 10) {
                    return StepResult.pending();
                }
                if (!input.available()) {
                    return StepResult.fail("SDL-Joystick-Subsystem nicht verfügbar");
                }
                if (!input.devices().isEmpty()) {
                    s.notes.add("übersprungen: echtes Gerät angeschlossen (" + names(input.devices()) + ")");
                    return StepResult.pass();
                }
                prepare(s);
                s.enter(s.withStick ? Phase.STICK : Phase.FLIGHT, ctx.tick());
                if (!s.withStick) {
                    s.pending = ctx.onServer(server -> fly(server, s));
                }
            }
            case STICK -> {
                DeviceInfo open = input.openDevice();
                if (open == null || !open.guid().equals(s.stickGuid) || !s.stickGuid.equals(RadioState.INSTANCE.deviceGuid())) {
                    return waited > 100 ? StepResult.fail("Joystick vor dem Flug nicht aktiv: " + describe(s)) : StepResult.pending();
                }
                s.notes.add("vor dem Flug aktiv: „" + open.name() + "“ ohne Einrichtung, Gas " + format(RadioState.INSTANCE.sticks().throttle()));
                s.pending = ctx.onServer(server -> fly(server, s));
                s.enter(Phase.FLIGHT, ctx.tick());
            }
            case FLIGHT -> {
                if (!flying(s)) {
                    return waited > 200 ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
                }
                session.clearTestInput();
                String expected = s.withStick ? s.stickGuid : "";
                boolean ready = s.withStick ? expected.equals(RadioState.INSTANCE.deviceGuid())
                        : RadioState.INSTANCE.source() == RadioSource.KEYBOARD;
                if (!ready || waited < 10) {
                    return waited > 100 ? StepResult.fail("Eingabe im Flug stellt sich nicht ein: " + describe(s)) : StepResult.pending();
                }
                s.notes.add("im Flug: " + RadioState.INSTANCE.source() + " " + RadioState.INSTANCE.deviceName());
                Minecraft.getInstance().gui.toastManager().clear();
                s.radio = plugRadio();
                s.enter(Phase.TAKEOVER, ctx.tick());
            }
            case TAKEOVER -> {
                DeviceInfo open = input.openDevice();
                boolean taken = open != null && open.guid().equals(s.radioGuid) && s.radioGuid.equals(RadioState.INSTANCE.deviceGuid())
                        && RadioState.INSTANCE.source() == RadioSource.JOYSTICK;
                if (!taken) {
                    return waited > TAKEOVER_TICKS ? StepResult.fail("Funke im Flug nach " + waited + " Ticks nicht übernommen: " + describe(s))
                            : StepResult.pending();
                }
                if (s.reachedAfter < 0) {
                    s.reachedAfter = waited;
                }
                if (!toastShown() && waited - s.reachedAfter < 20) {
                    return StepResult.pending();
                }
                String problem = checkTakeover(s, open);
                if (problem != null) {
                    return StepResult.fail(problem);
                }
                s.notes.add("Funke nach " + s.reachedAfter + " Ticks übernommen, Gas " + format(RadioState.INSTANCE.sticks().throttle())
                        + " aus der gespeicherten Belegung, Meldung „" + toastText() + "“");
                Minecraft.getInstance().gui.toastManager().clear();
                if (s.stickInFlight) {
                    s.stick = Plugged.attach(STICK_NAME, STICK_VENDOR, STICK_PRODUCT, STICK_AXES);
                    s.enter(Phase.ARRIVAL, ctx.tick());
                } else {
                    s.radio.unplug();
                    s.radio = null;
                    s.enter(Phase.UNPLUG, ctx.tick());
                }
            }
            case ARRIVAL -> {
                DeviceInfo open = input.openDevice();
                if (open == null || !open.guid().equals(s.radioGuid) || !s.radioGuid.equals(RadioState.INSTANCE.deviceGuid())) {
                    return StepResult.fail("im Flug angesteckter Joystick nimmt der Funke den Flug: " + describe(s));
                }
                if (!present(s.stickGuid)) {
                    return waited > TAKEOVER_TICKS ? StepResult.fail("im Flug angesteckter Joystick nicht erkannt: " + describe(s)) : StepResult.pending();
                }
                if (s.reachedAfter < 0) {
                    s.reachedAfter = waited;
                }
                if (waited - s.reachedAfter < HOLD_TICKS) {
                    return StepResult.pending();
                }
                s.notes.add("Joystick im Flug angesteckt, nach " + s.reachedAfter + " Ticks erkannt, Funke behält den Flug");
                Minecraft.getInstance().gui.toastManager().clear();
                s.radio.unplug();
                s.radio = null;
                s.enter(Phase.UNPLUG, ctx.tick());
            }
            case UNPLUG -> {
                DeviceInfo fallback = input.openDevice();
                if (fallback != null && !s.stickGuid.isEmpty() && fallback.guid().equals(s.stickGuid)) {
                    return StepResult.fail("Funke im Flug abgezogen: nach " + waited + " Ticks fliegt „" + fallback.name()
                            + "“ weiter statt Failsafe, obwohl der Joystick beim Abziehen schon angesteckt war: " + describe(s));
                }
                boolean lost = input.openDevice() == null && !RadioState.INSTANCE.connected() && input.linkLost()
                        && ArmBlocker.FAILSAFE.isSet(session.armBlockers()) && session.linkStrength() == 0.0F;
                if (!lost) {
                    return waited > LOSS_TICKS ? StepResult.fail("Abziehen im Flug ohne Failsafe: " + describe(s)) : StepResult.pending();
                }
                if (s.reachedAfter < 0) {
                    s.reachedAfter = waited;
                }
                if (!toastShown() && waited - s.reachedAfter < 20) {
                    return StepResult.pending();
                }
                String toast = checkToast(Component.translatable("propwash.input.toast.disconnected.radio").getString());
                if (toast != null) {
                    return StepResult.fail("Abziehen: " + toast);
                }
                s.notes.add("abgezogen nach " + s.reachedAfter + " Ticks: Failsafe, Meldung „" + toastText() + "“");
                s.enter(Phase.HOLD, ctx.tick());
            }
            case HOLD -> {
                DeviceInfo open = input.openDevice();
                if (open != null || RadioState.INSTANCE.connected()) {
                    return StepResult.fail("nach dem Abziehen springt die Eingabe auf „" + (open == null ? RadioState.INSTANCE.source() : open.name())
                            + "“ statt Failsafe");
                }
                if (waited < HOLD_TICKS) {
                    return StepResult.pending();
                }
                if (!s.stickGuid.isEmpty()) {
                    if (!present(s.stickGuid)) {
                        return StepResult.fail("Joystick fehlt nach dem Abziehen der Funke in der Geräteliste: " + describe(s));
                    }
                    s.notes.add(HOLD_TICKS + " Ticks Failsafe, Joystick angesteckt, kein Rückfall");
                }
                s.radio = plugRadio();
                s.enter(Phase.REPLUG, ctx.tick());
            }
            case REPLUG -> {
                DeviceInfo open = input.openDevice();
                boolean back = open != null && open.guid().equals(s.radioGuid) && s.radioGuid.equals(RadioState.INSTANCE.deviceGuid())
                        && !input.linkLost();
                if (!back) {
                    return waited > TAKEOVER_TICKS ? StepResult.fail("wieder angesteckte Funke nicht übernommen: " + describe(s)) : StepResult.pending();
                }
                if (ArmBlocker.FAILSAFE.isSet(session.armBlockers()) || session.linkStrength() <= 0.0F) {
                    return waited > TAKEOVER_TICKS + 20 ? StepResult.fail("Failsafe bleibt nach dem Wiederanstecken: " + describe(s))
                            : StepResult.pending();
                }
                s.notes.add("wieder angesteckt nach " + waited + " Ticks weiter");
                return StepResult.pass();
            }
        }
        return StepResult.pending();
    }

    private static @Nullable String checkTakeover(Scenario s, DeviceInfo open) {
        InputConfig config = InputConfig.get();
        DeviceProfile stored = config.profile(s.radioGuid);
        if (stored == null || !stored.setupDone) {
            return "gespeicherte Belegung der Funke fehlt";
        }
        float[] axes = Plugged.axes(RADIO_AXES);
        float expected = new StickMapper().map(stored, axes, 0.0F, false, false).throttle();
        float fallback = new StickMapper().map(ProfileDefaults.radio(RADIO_NAME, RADIO_AXES), axes, 0.0F, false, false).throttle();
        float actual = RadioState.INSTANCE.sticks().throttle();
        if (Math.abs(expected - fallback) < 0.1F) {
            return "Prüfwert taugt nicht: gespeichert " + format(expected) + ", Standard " + format(fallback);
        }
        if (Math.abs(actual - expected) > 0.01F) {
            return "Gas " + format(actual) + " statt " + format(expected) + " aus der gespeicherten Belegung (Standard wäre " + format(fallback) + ")";
        }
        if (ArmBlocker.FAILSAFE.isSet(PilotSession.INSTANCE.armBlockers())) {
            return "Failsafe trotz übernommener Funke";
        }
        String title = TransmitterInput.deviceToastTitle(open, true).getString();
        String problem = checkToast(title);
        if (problem != null) {
            return "Anstecken: " + problem;
        }
        ThemedToast toast = ThemedToast.find(Minecraft.getInstance().gui.toastManager(), TransmitterInput.TOAST_TOKEN);
        String message = TransmitterInput.deviceToastMessage(open, true).getString();
        String loaded = Component.translatable("propwash.input.toast.profile_loaded", "").getString().strip();
        if (toast == null || toast.message() == null || !toast.message().getString().equals(message) || !message.contains(loaded)) {
            return "Meldung nennt die geladene Belegung nicht: " + (toast == null || toast.message() == null ? "-" : toast.message().getString());
        }
        return null;
    }

    private static @Nullable String checkToast(String title) {
        ThemedToast toast = ThemedToast.find(Minecraft.getInstance().gui.toastManager(), TransmitterInput.TOAST_TOKEN);
        if (toast == null) {
            return "keine Gerätemeldung";
        }
        if (!toast.title().getString().equals(title)) {
            return "Meldung „" + toast.title().getString() + "“ statt „" + title + "“";
        }
        if (toast.getWantedVisibility() != Toast.Visibility.SHOW) {
            return "Gerätemeldung nicht sichtbar";
        }
        return null;
    }

    private static boolean toastShown() {
        ThemedToast toast = ThemedToast.find(Minecraft.getInstance().gui.toastManager(), TransmitterInput.TOAST_TOKEN);
        return toast != null && toast.getWantedVisibility() == Toast.Visibility.SHOW;
    }

    private static String toastText() {
        ThemedToast toast = ThemedToast.find(Minecraft.getInstance().gui.toastManager(), TransmitterInput.TOAST_TOKEN);
        if (toast == null) {
            return "-";
        }
        return toast.title().getString() + (toast.message() == null ? "" : " – " + toast.message().getString());
    }

    private static void prepare(Scenario s) {
        TransmitterInput input = TransmitterInput.INSTANCE;
        InputConfig config = InputConfig.get();
        s.selected = config.selectedGuid;
        s.keyboardFallback = config.keyboardFallback;
        s.suppressDevice = input.suppressDevice();
        input.simulate(null);
        Plugged probe = plugRadio();
        s.radioGuid = probe.guid;
        probe.unplug();
        InputConfig next = config.copy();
        next.selectedGuid = "";
        next.keyboardFallback = true;
        DeviceProfile profile = ProfileDefaults.radio(RADIO_NAME, RADIO_AXES);
        profile.throttle.invert = true;
        profile.setupDone = true;
        next.profiles.remove(s.radioGuid);
        next.profiles.put(s.radioGuid, profile);
        if (s.withStick || s.stickInFlight) {
            Plugged stick = Plugged.attach(STICK_NAME, STICK_VENDOR, STICK_PRODUCT, STICK_AXES);
            s.stickGuid = stick.guid;
            if (s.withStick) {
                s.stick = stick;
            } else {
                stick.unplug();
            }
            next.profiles.remove(s.stickGuid);
            next.neverAskFor(s.stickGuid);
        }
        next.sanitize();
        InputConfig.commit(next);
        input.setSuppressDevice(false);
        input.rescanSoon();
    }

    private static Plugged plugRadio() {
        Plugged radio = Plugged.attach(RADIO_NAME, DeviceNames.OPENTX_VENDOR, DeviceNames.OPENTX_PRODUCT, RADIO_AXES);
        for (int axis = 0; axis < RADIO_AXES; axis++) {
            radio.set(axis, Plugged.raw(axis));
        }
        return radio;
    }

    private static boolean flying(Scenario s) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return session.isActive() && session.isFpv() && drone != null && drone.getId() == s.droneId;
    }

    private static void fly(MinecraftServer server, Scenario s) {
        ServerPlayer player = player(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        int x = player.getBlockX() + 3;
        int z = player.getBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        drone.snapTo(x + 0.5, y + 0.1, z + 0.5, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        s.droneId = drone.getId();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        s.previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        s.previousHand = player.getMainHandItem().copy();
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (!drone.isFlownBy(player.getUUID())) {
            throw new IllegalStateException("FPV nicht gestartet: " + result);
        }
    }

    private static void land(MinecraftServer server, Scenario s) {
        for (ServerLevel level : server.getAllLevels()) {
            if (s.droneId >= 0 && level.getEntity(s.droneId) instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer player = players.getFirst();
        if (s.previousHead != null) {
            player.setItemSlot(EquipmentSlot.HEAD, s.previousHead);
        }
        if (s.previousHand != null) {
            player.setItemSlot(EquipmentSlot.MAINHAND, s.previousHand);
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static void cleanup(SelfTest.StepContext ctx, Scenario s) {
        if (s.cleaned) {
            return;
        }
        s.cleaned = true;
        if (s.radio != null) {
            s.radio.unplug();
            s.radio = null;
        }
        if (s.stick != null) {
            s.stick.unplug();
            s.stick = null;
        }
        releaseLeftovers();
        PilotSession.INSTANCE.clearTestInput();
        TransmitterInput input = TransmitterInput.INSTANCE;
        if (s.suppressDevice != null) {
            input.setSuppressDevice(s.suppressDevice);
        }
        if (s.selected != null) {
            InputConfig next = InputConfig.get().copy();
            if (s.radioGuid != null) {
                next.profiles.remove(s.radioGuid);
            }
            if (s.stickGuid != null) {
                next.profiles.remove(s.stickGuid);
                next.neverAsk.remove(s.stickGuid);
            }
            next.selectedGuid = s.selected;
            next.keyboardFallback = s.keyboardFallback == null || s.keyboardFallback;
            InputConfig.commit(next);
        }
        input.rescanSoon();
        Minecraft.getInstance().gui.toastManager().clear();
        if (s.droneId >= 0 || s.previousHead != null || s.previousHand != null) {
            ctx.onServer(server -> land(server, s));
        }
    }

    private static void releaseLeftovers() {
        List<Plugged> left;
        synchronized (LEFTOVERS) {
            left = new ArrayList<>(LEFTOVERS);
        }
        for (Plugged plugged : left) {
            plugged.unplug();
        }
    }

    private static String describe(Scenario s) {
        TransmitterInput input = TransmitterInput.INSTANCE;
        DeviceInfo open = input.openDevice();
        RadioState radio = RadioState.INSTANCE;
        return "Phase " + s.phase + ", Geräte " + names(input.devices()) + ", offen " + (open == null ? "-" : "„" + open.name() + "“")
                + ", Quelle " + radio.source() + " „" + radio.deviceName() + "“, verbunden " + radio.connected() + ", Verbindung verloren "
                + input.linkLost() + ", Sperren " + PilotSession.INSTANCE.armBlockers() + ", Meldung „" + toastText() + "“";
    }

    private static boolean present(String guid) {
        for (DeviceInfo device : TransmitterInput.INSTANCE.devices()) {
            if (device.guid().equals(guid)) {
                return true;
            }
        }
        return false;
    }

    private static String names(List<DeviceInfo> devices) {
        List<String> out = new ArrayList<>();
        for (DeviceInfo device : devices) {
            out.add("„" + device.name() + "“");
        }
        return out.isEmpty() ? "keine" : String.join(", ", out);
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private enum Phase {
        PREPARE, STICK, FLIGHT, TAKEOVER, ARRIVAL, UNPLUG, HOLD, REPLUG
    }

    private static final class Scenario {
        final boolean withStick;
        final boolean stickInFlight;
        final List<String> notes = new ArrayList<>();
        Phase phase = Phase.PREPARE;
        int since;
        int reachedAfter = -1;
        @Nullable CompletableFuture<Void> pending;
        @Nullable Plugged radio;
        @Nullable Plugged stick;
        @Nullable String radioGuid;
        String stickGuid = "";
        @Nullable String selected;
        @Nullable Boolean keyboardFallback;
        @Nullable Boolean suppressDevice;
        int droneId = -1;
        @Nullable ItemStack previousHead;
        @Nullable ItemStack previousHand;
        boolean cleaned;

        Scenario(boolean withStick, boolean stickInFlight) {
            this.withStick = withStick;
            this.stickInFlight = stickInFlight;
        }

        void enter(Phase next, int tick) {
            this.phase = next;
            this.since = tick;
            this.reachedAfter = -1;
        }
    }

    private static final class Plugged {
        final int id;
        final String guid;
        final ByteBuffer name;
        final long handle;
        boolean attached = true;

        private Plugged(int id, String guid, ByteBuffer name, long handle) {
            this.id = id;
            this.guid = guid;
            this.name = name;
            this.handle = handle;
        }

        static Plugged attach(String name, int vendor, int product, int axes) {
            ByteBuffer text = MemoryUtil.memUTF8(name);
            int id;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                SDL_VirtualJoystickDesc desc = SDL_VirtualJoystickDesc.calloc(stack);
                SDLJoystick.SDL_INIT_INTERFACE(desc);
                desc.type((short) SDLJoystick.SDL_JOYSTICK_TYPE_UNKNOWN).vendor_id((short) vendor).product_id((short) product)
                        .naxes((short) axes).name(text);
                id = SDLJoystick.SDL_AttachVirtualJoystick(desc);
            }
            if (id == 0) {
                MemoryUtil.memFree(text);
                throw new IllegalStateException("virtueller Joystick „" + name + "“ nicht angelegt: " + SDLError.SDL_GetError());
            }
            long handle = SDLJoystick.SDL_OpenJoystick(id);
            if (handle == 0L) {
                SDLJoystick.SDL_DetachVirtualJoystick(id);
                MemoryUtil.memFree(text);
                throw new IllegalStateException("virtueller Joystick „" + name + "“ lässt sich nicht öffnen: " + SDLError.SDL_GetError());
            }
            Plugged plugged = new Plugged(id, guidOf(id), text, handle);
            synchronized (LEFTOVERS) {
                LEFTOVERS.add(plugged);
            }
            return plugged;
        }

        void set(int axis, short value) {
            SDLJoystick.SDL_SetJoystickVirtualAxis(this.handle, axis, value);
        }

        void unplug() {
            if (!this.attached) {
                return;
            }
            this.attached = false;
            SDLJoystick.SDL_CloseJoystick(this.handle);
            SDLJoystick.SDL_DetachVirtualJoystick(this.id);
            MemoryUtil.memFree(this.name);
            synchronized (LEFTOVERS) {
                LEFTOVERS.remove(this);
            }
        }

        static short raw(int axis) {
            if (axis == 2) {
                return THROTTLE_RAW;
            }
            return axis >= ProfileDefaults.AUX_ARM ? SWITCH_LOW : 0;
        }

        static float[] axes(int count) {
            float[] axes = new float[count];
            for (int axis = 0; axis < count; axis++) {
                axes[axis] = TransmitterInput.normalize(raw(axis));
            }
            return axes;
        }

        private static String guidOf(int id) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                SDL_GUID guid = SDL_GUID.malloc(stack);
                ByteBuffer text = stack.malloc(33);
                SDLJoystick.SDL_GetJoystickGUIDForID(id, guid);
                SDLGUID.SDL_GUIDToString(guid, text);
                return MemoryUtil.memASCII(MemoryUtil.memAddress(text));
            }
        }
    }
}
