package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.audio.client.LocatorBeeper;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.input.DeviceChooser;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.KeyRepeatFilter;
import de.lardtom.propwash.input.MfiHint;
import de.lardtom.propwash.input.MouseAxis;
import de.lardtom.propwash.input.MouseSettings;
import de.lardtom.propwash.input.MouseSticks;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioFrame;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SetupStatus;
import de.lardtom.propwash.input.StickMapper;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchCommand;
import de.lardtom.propwash.input.SwitchEvaluator;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.ThrottleCentering;
import de.lardtom.propwash.input.client.screen.InputLabels;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLGUID;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLJoystick;
import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.sdl.SDLVersion;
import org.lwjgl.sdl.SDL_GUID;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TransmitterInput {
    private static final Object BEEPER_TOAST_TOKEN = new Object();
    private static final Object LEDS_TOAST_TOKEN = new Object();
    public static final TransmitterInput INSTANCE = new TransmitterInput();

    private static final int MAX_AXES = 32;
    private static final int MAX_BUTTONS = 64;
    private static final int MAX_HATS = 4;
    private static final long SCAN_INTERVAL_NANOS = 250_000_000L;
    private static final long PREVIEW_TIMEOUT_NANOS = 1_000_000_000L;
    private static final long CAPTURE_TIMEOUT_NANOS = 300_000_000L;
    private static final long OPEN_RETRY_NANOS = 5_000_000_000L;
    private static final int EDGE_FUNCTIONS = SwitchEvaluator.bit(SwitchFunction.ARM) | SwitchEvaluator.bit(SwitchFunction.RESCUE)
            | SwitchEvaluator.bit(SwitchFunction.FLIP_OVER) | SwitchEvaluator.bit(SwitchFunction.SET_HOME)
            | SwitchEvaluator.bit(SwitchFunction.RESET) | SwitchEvaluator.bit(SwitchFunction.RECORD)
            | SwitchEvaluator.bit(SwitchFunction.VTX_POWER) | commandBits();
    private static final int ONE_SHOT_FUNCTIONS = EDGE_FUNCTIONS & ~SwitchEvaluator.bit(SwitchFunction.ARM);
    private static final float ACTIVITY_EPSILON = 0.01F;
    private static final float[] NO_AXES = new float[0];
    private static final boolean[] NO_BUTTONS = new boolean[0];
    public static final String TOAST_TOKEN = "propwash:input_device";
    private static final long TOAST_MILLIS = 4000L;

    private boolean initTried;
    private boolean available;
    private MfiHint.@Nullable Plan mfiPlan;
    private boolean mfiBeforeInit;
    private int backendStarts;
    private boolean emptyLogged;
    private boolean deviceBroken;
    private long nextScanNanos;
    private long physicsFrameNanos;
    private List<DeviceInfo> devices = List.of();
    private Set<Integer> knownIds = new HashSet<>();
    private final Map<Integer, Long> openFailures = new HashMap<>();

    private long joystick;
    private long gamepad;
    private @Nullable DeviceInfo open;
    private int axisCount;
    private int buttonCount;
    private int hatCount;
    private float[] joystickAxes = NO_AXES;
    private boolean[] joystickButtons = NO_BUTTONS;
    private final float[] gamepadAxes = new float[ProfileDefaults.PAD_AXES];
    private final boolean[] gamepadButtons = new boolean[ProfileDefaults.PAD_BUTTONS];
    private float[] previousAxes = NO_AXES;
    private boolean[] previousButtons = NO_BUTTONS;

    private @Nullable String previewGuid;
    private long previewUntil;
    private boolean suppressDevice;
    private long captureUntil;
    private boolean capturing;
    private int maskedSwitches;
    private int lastSwitchBits;
    private @Nullable String switchesGuid;
    private Map<String, SwitchAssignment> appliedSwitches = Map.of();
    private @Nullable String sessionGuid;
    private @Nullable String stateGuid;
    private boolean publishFailed;

    private final StickMapper mapper = new StickMapper();
    private volatile boolean throttle3d;
    private final SwitchEvaluator evaluator = new SwitchEvaluator();
    private @Nullable String evaluatedGuid;
    private @Nullable DeviceProfile evaluatedProfile;
    private final KeyboardFlight keyboard = new KeyboardFlight();
    private final MouseFlight mouse = new MouseFlight();
    private MouseCapture.Frame mouseFrame = MouseCapture.Frame.EMPTY;
    private boolean mouseFlying;
    private boolean mouseWheel;
    private boolean forceMouseCapture;
    private boolean beeperLatch;
    private @Nullable QuadcopterEntity beeperDrone;
    private boolean ledsOffLatch;
    private final KeyRepeatFilter beeperKey = new KeyRepeatFilter();
    private final KeyRepeatFilter ledsKey = new KeyRepeatFilter();
    private int deviceSwitchBits;
    private @Nullable SimulatedRadio simulated;
    private Map<String, SwitchCommand> commands = Map.of();

    private TransmitterInput() {
    }

    public long pollNanos() {
        return this.physicsFrameNanos;
    }

    public void onFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        long now = Util.getNanos();
        float dt = this.physicsFrameNanos == 0L ? 0.0F : Math.min(0.1F, Math.max(0.0F, (now - this.physicsFrameNanos) / 1.0E9F));
        this.physicsFrameNanos = now;
        InputConfig config = InputConfig.get();
        boolean session = PilotSession.INSTANCE.isActive();
        if (!session) {
            this.sessionGuid = null;
        }
        this.updateKeySwitches(minecraft);
        if (!this.deviceBroken) {
            this.restartIfOptionChanged(config, session);
        }
        if (!this.deviceBroken && this.ensureInit()) {
            try {
                this.pollDevices(config, now, session);
            } catch (RuntimeException | LinkageError e) {
                this.deviceBroken = true;
                Constants.LOG.error("Propwash: Controller-Eingabe nach einem Fehler abgeschaltet", e);
                this.closeQuietly();
            }
        }
        this.mouseFrame = MouseCapture.INSTANCE.take();
        this.mouseFlying = false;
        this.mouseWheel = false;
        try {
            this.publish(minecraft, config, dt, now, session);
        } catch (RuntimeException e) {
            if (!this.publishFailed) {
                this.publishFailed = true;
                Constants.LOG.error("Propwash: Eingabe konnte nicht ausgewertet werden", e);
            }
            RadioState.INSTANCE.publish(RadioFrame.NONE);
            this.commands = Map.of();
        }
        MouseCapture.INSTANCE.accept(this.mouseFlying, this.mouseWheel);
        SwitchCommandRunner.INSTANCE.onFrame(minecraft, this.commands);
    }

    public void forceMouseCapture(boolean force) {
        this.forceMouseCapture = force;
    }

    private static int commandBits() {
        int bits = 0;
        for (SwitchFunction function : SwitchFunction.commands()) {
            bits |= SwitchEvaluator.bit(function);
        }
        return bits;
    }

    public Map<String, SwitchCommand> activeCommands() {
        return this.commands;
    }

    private void publish(Minecraft minecraft, InputConfig config, float dt, long now, boolean session) {
        SimulatedRadio fake = this.simulated;
        this.commands = Map.of();
        if (fake != null) {
            this.publishSimulated(fake, dt);
            return;
        }
        if (this.open != null && !this.suppressDevice) {
            this.publishDevice(minecraft, config, dt, now, session);
            return;
        }
        this.capturing = false;
        if (session && this.sessionGuid != null && !this.suppressDevice) {
            boolean keyboardOn = config.keyboardFallback || config.keyboardSelected();
            boolean takeOver = keyboardOn && minecraft.gui.screen() == null && !PilotSession.INSTANCE.armed() && InputKeys.anyFlightKeyDown();
            if (!takeOver) {
                this.keyboard.reset();
                this.mouse.reset();
                RadioState.INSTANCE.publish(RadioFrame.NONE);
                this.commands = config.keyboard.commands;
                return;
            }
            Constants.LOG.info("Propwash: Tastatur übernimmt nach Verlust von {}", this.sessionGuid);
            this.sessionGuid = null;
            this.keyboard.reset();
            this.mouse.reset();
        }
        this.publishFallback(minecraft, config, dt);
    }

    public List<DeviceInfo> devices() {
        return this.devices;
    }

    public boolean available() {
        return this.available && !this.deviceBroken;
    }

    public @Nullable DeviceInfo openDevice() {
        return this.open;
    }

    public @Nullable RawInput raw() {
        SimulatedRadio fake = this.simulated;
        if (fake != null) {
            return fake.raw();
        }
        DeviceInfo device = this.open;
        if (device == null) {
            return null;
        }
        boolean pad = this.gamepad != 0L;
        return new RawInput(device, this.axisCount, this.buttonCount, this.hatCount, this.joystickAxes.clone(), this.joystickButtons.clone(),
                pad ? this.gamepadAxes.clone() : null, pad ? this.gamepadButtons.clone() : null);
    }

    public void requestPreview(@Nullable String guid) {
        String wanted = guid == null ? "" : guid;
        long now = Util.getNanos();
        if (!wanted.equals(this.previewGuid) || now >= this.previewUntil) {
            this.nextScanNanos = 0L;
        }
        this.previewGuid = wanted;
        this.previewUntil = now + PREVIEW_TIMEOUT_NANOS;
    }

    public void rescanSoon() {
        this.nextScanNanos = 0L;
    }

    public void requestCapture() {
        this.captureUntil = Util.getNanos() + CAPTURE_TIMEOUT_NANOS;
    }

    public boolean linkLost() {
        return this.sessionGuid != null && this.open == null && PilotSession.INSTANCE.isActive() && !this.suppressDevice;
    }

    public void setThrottle3d(boolean centered) {
        this.throttle3d = centered;
    }

    public boolean throttle3d() {
        return this.throttle3d;
    }

    public void followSwitches(SwitchEvaluator target, String guid, DeviceProfile profile) {
        DeviceProfile live = this.evaluatedProfile;
        if (live != null && guid.equals(this.evaluatedGuid)) {
            target.follow(this.evaluator, live, profile);
        }
    }

    public void adoptSwitches(String guid, SwitchEvaluator edited) {
        if (guid.equals(this.evaluatedGuid)) {
            this.evaluator.copyFrom(edited);
        }
    }

    private void evaluated(@Nullable String guid, @Nullable DeviceProfile profile) {
        this.evaluatedGuid = guid;
        this.evaluatedProfile = profile;
    }

    public void setSuppressDevice(boolean suppress) {
        this.suppressDevice = suppress;
    }

    public boolean suppressDevice() {
        return this.suppressDevice;
    }

    public void onDisconnect() {
        this.evaluator.reset();
        this.evaluated(null, null);
        this.mapper.reset();
        this.keyboard.reset();
        this.mouse.reset();
        this.stateGuid = null;
        this.sessionGuid = null;
        this.capturing = false;
        this.maskedSwitches = 0;
        this.lastSwitchBits = 0;
        this.switchesGuid = null;
        this.beeperLatch = false;
        this.beeperDrone = null;
        this.ledsOffLatch = false;
        this.deviceSwitchBits = 0;
        SwitchCommandRunner.INSTANCE.reset();
    }

    public void simulate(@Nullable SimulatedRadio radio) {
        this.simulated = radio;
        this.evaluator.reset();
        this.evaluated(null, null);
        this.mapper.reset();
        this.keyboard.reset();
        this.mouse.reset();
        this.maskedSwitches = 0;
        this.lastSwitchBits = 0;
        this.switchesGuid = null;
        this.deviceSwitchBits = 0;
    }

    public boolean simulating() {
        return this.simulated != null;
    }

    public int keySwitchBits() {
        int bits = 0;
        if (this.beeperLatch) {
            bits |= SwitchEvaluator.bit(SwitchFunction.BEEPER);
        }
        if (this.ledsOffLatch) {
            bits |= SwitchEvaluator.bit(SwitchFunction.LEDS_OFF);
        }
        return bits;
    }

    private void updateKeySwitches(Minecraft minecraft) {
        boolean beeperPressed = this.beeperKey.pressed(clicks(InputKeys.BEEPER), InputKeys.BEEPER.isDown());
        boolean ledsPressed = this.ledsKey.pressed(clicks(InputKeys.LEDS), InputKeys.LEDS.isDown());
        if (!PilotSession.INSTANCE.isActive()) {
            this.ledsOffLatch = false;
            this.beeperDrone = null;
            if (beeperPressed && (this.beeperLatch || LocatorBeeper.ownBeeping(minecraft) != null)) {
                this.beeperLatch = false;
                LocatorBeeper.switchOffOwn(minecraft);
                this.beeperToast(minecraft);
            }
            return;
        }
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone != this.beeperDrone) {
            this.beeperDrone = drone;
            this.beeperLatch = drone != null && minecraft.player != null && drone.beeperBy().filter(minecraft.player.getUUID()::equals).isPresent();
        }
        if (ledsPressed) {
            this.ledsOffLatch = !this.ledsOffLatch;
            if (minecraft.player != null) {
                this.ledsToast(minecraft);
            }
        }
        if (beeperPressed) {
            this.beeperLatch = !this.beeperLatch;
            this.beeperToast(minecraft);
        }
    }

    private void beeperToast(Minecraft minecraft) {
        if (minecraft.player != null) {
            ThemedToast.show(minecraft.gui.toastManager(), BEEPER_TOAST_TOKEN,
                    Component.translatable(this.beeperLatch ? "propwash.input.beeper.on" : "propwash.input.beeper.off"), null,
                    this.beeperLatch ? Palette.WARNING : Palette.INFO, 2500L);
        }
    }

    private static int clicks(KeyMapping key) {
        int clicks = 0;
        while (key.consumeClick()) {
            clicks++;
        }
        return clicks;
    }

    private void ledsToast(Minecraft minecraft) {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        String key;
        int color;
        if (drone != null && LedSettings.strip(drone.build()).isEmpty()) {
            key = "propwash.input.leds.no_strip";
            color = Palette.WARNING;
        } else if (!this.ledsOffLatch && SwitchEvaluator.isSet(this.deviceSwitchBits, SwitchFunction.LEDS_OFF)) {
            key = "propwash.input.leds.held_off";
            color = Palette.WARNING;
        } else {
            key = this.ledsOffLatch ? "propwash.input.leds.off" : "propwash.input.leds.on";
            color = Palette.INFO;
        }
        Component text = Component.translatable(key, Component.translatable(SwitchFunction.LEDS_OFF.translationKey()));
        ThemedToast.show(minecraft.gui.toastManager(), LEDS_TOAST_TOKEN, text, null, color, 2500L);
    }

    private boolean ensureInit() {
        if (this.initTried) {
            return this.available;
        }
        if ((SDLInit.SDL_WasInit(SDLInit.SDL_INIT_VIDEO) & SDLInit.SDL_INIT_VIDEO) == 0) {
            return false;
        }
        this.initTried = true;
        try {
            SDLHints.SDL_SetHint(SDLHints.SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1");
            try {
                this.applyMfiHint();
            } catch (RuntimeException | LinkageError e) {
                Constants.LOG.warn("Propwash: {} konnte nicht gesetzt werden", MfiHint.NAME, e);
            }
            this.available = SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_JOYSTICK | SDLInit.SDL_INIT_GAMEPAD);
            this.backendStarts++;
            if (this.available) {
                Constants.LOG.info("Propwash: SDL-Joystick/Gamepad-Subsystem gestartet (SDL {})", sdlVersion());
            } else {
                Constants.LOG.warn("Propwash: SDL-Joystick/Gamepad-Subsystem konnte nicht gestartet werden: {}", SDLError.SDL_GetError());
            }
        } catch (RuntimeException | LinkageError e) {
            this.available = false;
            Constants.LOG.error("Propwash: SDL-Joystick/Gamepad-Subsystem nicht verfügbar", e);
        }
        return this.available;
    }

    private void applyMfiHint() {
        MfiHint.Plan plan = MacControllers.plan(InputConfig.get());
        this.mfiPlan = plan;
        this.mfiBeforeInit = (SDLInit.SDL_WasInit(SDLInit.SDL_INIT_JOYSTICK) & SDLInit.SDL_INIT_JOYSTICK) == 0;
        if (plan.source() == MfiHint.Source.NOT_MAC) {
            return;
        }
        boolean applied = true;
        if (plan.setsOff()) {
            applied = SDLHints.SDL_SetHint(SDLHints.SDL_HINT_JOYSTICK_MFI, MfiHint.OFF);
        } else if (plan.resets()) {
            applied = SDLHints.SDL_ResetHint(SDLHints.SDL_HINT_JOYSTICK_MFI);
        }
        Constants.LOG.info("Propwash: macOS-Controller-Erkennung: {}={} ({}), Apples GameController-Framework {}", MfiHint.NAME, loggedHint(),
                describe(plan), plan.appleFramework() ? "an" : "aus");
        if (!applied) {
            Constants.LOG.warn("Propwash: SDL hat {} nicht übernommen: {}", MfiHint.NAME, SDLError.SDL_GetError());
        }
        if (!this.mfiBeforeInit) {
            Constants.LOG.warn("Propwash: Das SDL-Joystick-Subsystem lief schon vor Propwash (anderer Mod?), {} wirkt dann nur auf neu angeschlossene Controller",
                    MfiHint.NAME);
        }
    }

    private static String describe(MfiHint.Plan plan) {
        return switch (plan.source()) {
            case NOT_MAC -> "kein macOS";
            case ENVIRONMENT -> "Umgebungsvariable";
            case OPTION_OFF, OPTION_ON -> "Propwash-Einstellung";
        };
    }

    private void restartIfOptionChanged(InputConfig config, boolean session) {
        MfiHint.Plan applied = this.mfiPlan;
        if (!this.initTried || session || applied == null || !(applied.setsOff() || applied.resets())
                || applied.appleFramework() == config.appleGameController) {
            return;
        }
        MfiHint.Plan wanted = MacControllers.plan(config);
        if (!MfiHint.restartNeeded(applied, wanted)) {
            return;
        }
        Constants.LOG.info("Propwash: Controller-Erkennung startet neu, Apples GameController-Framework {}", wanted.appleFramework() ? "an" : "aus");
        this.closeQuietly();
        try {
            if (this.available) {
                SDLInit.SDL_QuitSubSystem(SDLInit.SDL_INIT_GAMEPAD | SDLInit.SDL_INIT_JOYSTICK);
            }
        } catch (RuntimeException | LinkageError e) {
            Constants.LOG.warn("Propwash: SDL-Joystick/Gamepad-Subsystem ließ sich nicht beenden", e);
        }
        this.initTried = false;
        this.available = false;
        this.devices = List.of();
        this.knownIds = new HashSet<>();
        this.openFailures.clear();
        this.nextScanNanos = 0L;
        this.emptyLogged = false;
    }

    public MfiHint.@Nullable Plan mfiPlan() {
        return this.mfiPlan;
    }

    public boolean mfiHintBeforeInit() {
        return this.mfiBeforeInit;
    }

    public int backendStarts() {
        return this.backendStarts;
    }

    public static @Nullable String mfiHintValue() {
        return SDLHints.SDL_GetHint(SDLHints.SDL_HINT_JOYSTICK_MFI);
    }

    private static String loggedHint() {
        String value = mfiHintValue();
        return value == null ? "nicht gesetzt (SDL-Standard: an)" : value;
    }

    public static String sdlVersion() {
        try {
            int version = SDLVersion.SDL_GetVersion();
            return SDLVersion.SDL_VERSIONNUM_MAJOR(version) + "." + SDLVersion.SDL_VERSIONNUM_MINOR(version) + "."
                    + SDLVersion.SDL_VERSIONNUM_MICRO(version) + " " + Objects.requireNonNullElse(SDLVersion.SDL_GetRevision(), "");
        } catch (RuntimeException | LinkageError e) {
            return "?";
        }
    }

    private void logEmptyScan() {
        if (this.emptyLogged) {
            return;
        }
        this.emptyLogged = true;
        MfiHint.Plan plan = this.mfiPlan;
        String hint = plan == null || plan.source() == MfiHint.Source.NOT_MAC ? "-" : loggedHint() + " (" + describe(plan) + ")";
        Constants.LOG.info("Propwash: SDL findet keine Controller | System {} {} {} | SDL {} | {}={}", System.getProperty("os.name"),
                System.getProperty("os.version"), System.getProperty("os.arch"), sdlVersion(), MfiHint.NAME, hint);
    }

    private void pollDevices(InputConfig config, long now, boolean session) {
        SDLJoystick.SDL_UpdateJoysticks();
        DeviceInfo current = this.open;
        if (current != null && !SDLJoystick.SDL_JoystickConnected(this.joystick)) {
            Constants.LOG.info("Propwash: Eingabegerät getrennt: \"{}\" ({})", current.name(), current.guid());
            this.closeDevice();
            showDeviceToast(current, false);
            this.nextScanNanos = 0L;
        }
        if (now >= this.nextScanNanos) {
            this.nextScanNanos = now + SCAN_INTERVAL_NANOS;
            this.scan(config, now, session);
        }
        if (this.open != null) {
            this.readDevice();
        }
    }

    private void scan(InputConfig config, long now, boolean session) {
        List<DeviceInfo> list = listDevices();
        if (list.isEmpty()) {
            this.logEmptyScan();
        }
        Set<Integer> ids = new HashSet<>();
        List<DeviceInfo> added = new ArrayList<>();
        for (DeviceInfo device : list) {
            ids.add(device.id());
            if (!this.knownIds.contains(device.id())) {
                added.add(device);
            }
        }
        for (DeviceInfo previous : this.devices) {
            if (!ids.contains(previous.id())) {
                this.openFailures.remove(previous.id());
                Constants.LOG.info("Propwash: Controller entfernt: \"{}\" | GUID {}", previous.name(), previous.guid());
            }
        }
        this.knownIds = ids;
        this.devices = List.copyOf(list);

        boolean previewing = this.previewGuid != null && now < this.previewUntil;
        String wanted = previewing ? this.previewGuid : config.selectedGuid;
        if (InputConfig.withoutDevice(wanted)) {
            this.sessionGuid = null;
        }
        List<DeviceChooser.Candidate> candidates = new ArrayList<>(list.size());
        for (DeviceInfo device : list) {
            candidates.add(device.candidate());
        }
        String lock = !previewing && session ? this.sessionGuid : null;
        int index = DeviceChooser.pick(candidates, wanted, lock, i -> this.openBlocked(list.get(i).id(), now), config::usageRank);
        DeviceInfo target = index < 0 ? null : list.get(index);
        DeviceInfo current = this.open;
        if (target == null) {
            if (current != null) {
                this.closeDevice();
            }
        } else if (current == null || current.id() != target.id()) {
            this.openDevice(target, now, session);
        }
        for (DeviceInfo device : added) {
            this.logDevice(device);
        }
        DeviceOnboarding.INSTANCE.onScan(list, this.open, now);
    }

    private static List<DeviceInfo> listDevices() {
        IntBuffer ids = SDLJoystick.SDL_GetJoysticks();
        if (ids == null) {
            return List.of();
        }
        List<DeviceInfo> out = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SDL_GUID guid = SDL_GUID.malloc(stack);
            ByteBuffer text = stack.malloc(33);
            for (int i = ids.position(); i < ids.limit(); i++) {
                int id = ids.get(i);
                SDLJoystick.SDL_GetJoystickGUIDForID(id, guid);
                SDLGUID.SDL_GUIDToString(guid, text);
                String guidText = MemoryUtil.memASCII(MemoryUtil.memAddress(text));
                String name = Objects.requireNonNullElse(SDLJoystick.SDL_GetJoystickNameForID(id), "");
                out.add(new DeviceInfo(id, name.isBlank() ? "SDL #" + id : name.strip(), guidText, SDLGamepad.SDL_IsGamepad(id),
                        SDLJoystick.SDL_GetJoystickVendorForID(id) & 0xFFFF, SDLJoystick.SDL_GetJoystickProductForID(id) & 0xFFFF));
            }
        } finally {
            SDLStdinc.SDL_free(ids);
        }
        return out;
    }

    private boolean openBlocked(int id, long now) {
        Long retryAt = this.openFailures.get(id);
        return retryAt != null && now < retryAt;
    }

    private void openDevice(DeviceInfo device, long now, boolean session) {
        long pad = 0L;
        long joy = 0L;
        if (device.gamepad()) {
            pad = SDLGamepad.SDL_OpenGamepad(device.id());
            joy = pad != 0L ? SDLGamepad.SDL_GetGamepadJoystick(pad) : 0L;
            if (pad != 0L && joy == 0L) {
                SDLGamepad.SDL_CloseGamepad(pad);
                pad = 0L;
            }
        }
        if (joy == 0L) {
            joy = SDLJoystick.SDL_OpenJoystick(device.id());
        }
        if (joy == 0L) {
            if (this.openFailures.put(device.id(), now + OPEN_RETRY_NANOS) == null) {
                Constants.LOG.warn("Propwash: Controller \"{}\" lässt sich nicht öffnen: {}", device.name(), SDLError.SDL_GetError());
            }
            return;
        }
        this.openFailures.remove(device.id());
        this.closeDevice();
        this.joystick = joy;
        this.gamepad = pad;
        this.open = device;
        this.axisCount = clampCount(SDLJoystick.SDL_GetNumJoystickAxes(joy), MAX_AXES);
        this.buttonCount = clampCount(SDLJoystick.SDL_GetNumJoystickButtons(joy), MAX_BUTTONS);
        this.hatCount = clampCount(SDLJoystick.SDL_GetNumJoystickHats(joy), MAX_HATS);
        this.joystickAxes = new float[this.axisCount];
        this.joystickButtons = new boolean[this.buttonCount + this.hatCount * 4];
        boolean resume = session && device.guid().equals(this.sessionGuid) && device.guid().equals(this.stateGuid);
        if (!resume) {
            this.mapper.reset();
            this.evaluator.reset();
        }
        this.stateGuid = device.guid();
        this.touchProfile(device.guid());
        Constants.LOG.info("Propwash: Eingabegerät aktiv: \"{}\" | GUID {} | {} Achsen, {} Tasten, {} Hats | gelesen als {}",
                device.name(), device.guid(), this.axisCount, this.buttonCount, this.hatCount, pad != 0L ? "SDL-Gamepad" : "Joystick");
        showDeviceToast(device, true);
    }

    private void touchProfile(String guid) {
        InputConfig config = InputConfig.get();
        if (config.profile(guid) != null && !config.isNewest(guid)) {
            config.touch(guid);
            InputConfig.commit(config);
        }
    }

    private void logDevice(DeviceInfo device) {
        int axes;
        int buttons;
        int hats;
        DeviceInfo current = this.open;
        if (current != null && current.id() == device.id()) {
            axes = this.axisCount;
            buttons = this.buttonCount;
            hats = this.hatCount;
        } else {
            long handle = SDLJoystick.SDL_OpenJoystick(device.id());
            if (handle != 0L) {
                axes = SDLJoystick.SDL_GetNumJoystickAxes(handle);
                buttons = SDLJoystick.SDL_GetNumJoystickButtons(handle);
                hats = SDLJoystick.SDL_GetNumJoystickHats(handle);
                SDLJoystick.SDL_CloseJoystick(handle);
            } else {
                axes = -1;
                buttons = -1;
                hats = -1;
            }
        }
        Constants.LOG.info("Propwash: Controller verbunden: \"{}\" | GUID {} | {} Achsen, {} Tasten, {} Hats | SDL-Gamepad: {} | USB {} | Funke erkannt: {} | EdgeTX/OpenTX: {}",
                device.name(), device.guid(), axes, buttons, hats, device.gamepad() ? "ja" : "nein", device.usbId(),
                device.radio() ? "ja" : "nein", device.edgeTxOrOpenTx() ? "ja" : "nein");
    }

    private void readDevice() {
        long joy = this.joystick;
        for (int i = 0; i < this.axisCount; i++) {
            this.joystickAxes[i] = normalize(SDLJoystick.SDL_GetJoystickAxis(joy, i));
        }
        for (int i = 0; i < this.buttonCount; i++) {
            this.joystickButtons[i] = SDLJoystick.SDL_GetJoystickButton(joy, i);
        }
        for (int h = 0; h < this.hatCount; h++) {
            int hat = SDLJoystick.SDL_GetJoystickHat(joy, h) & 0xFF;
            int base = this.buttonCount + h * 4;
            this.joystickButtons[base] = (hat & SDLJoystick.SDL_HAT_UP) != 0;
            this.joystickButtons[base + 1] = (hat & SDLJoystick.SDL_HAT_RIGHT) != 0;
            this.joystickButtons[base + 2] = (hat & SDLJoystick.SDL_HAT_DOWN) != 0;
            this.joystickButtons[base + 3] = (hat & SDLJoystick.SDL_HAT_LEFT) != 0;
        }
        long pad = this.gamepad;
        if (pad != 0L) {
            for (int a = 0; a < ProfileDefaults.PAD_AXES; a++) {
                short raw = SDLGamepad.SDL_GetGamepadAxis(pad, a);
                this.gamepadAxes[a] = a >= ProfileDefaults.PAD_LEFT_TRIGGER ? trigger(raw) : normalize(raw);
            }
            for (int b = 0; b < ProfileDefaults.PAD_BUTTONS; b++) {
                this.gamepadButtons[b] = SDLGamepad.SDL_GetGamepadButton(pad, b);
            }
        }
    }

    private void publishDevice(Minecraft minecraft, InputConfig config, float dt, long now, boolean session) {
        DeviceInfo device = Objects.requireNonNull(this.open);
        DeviceProfile profile = config.profile(device.guid());
        if (profile == null) {
            profile = ProfileDefaults.create(device.name(), device.gamepadByDefault(), this.axisCount, config.stickMode());
            config.profiles.put(device.guid(), profile);
            InputConfig.commit(config);
            Constants.LOG.info("Propwash: Standardbelegung für \"{}\" angelegt ({})", device.name(),
                    profile.gamepadLayout ? "Gamepad Mode " + config.stickMode().number() : "Funke AETR");
        }
        boolean usePad = profile.gamepadLayout && this.gamepad != 0L;
        float[] axes = usePad ? this.gamepadAxes : this.joystickAxes;
        boolean[] buttons = usePad ? this.gamepadButtons : this.joystickButtons;
        this.commands = profile.commands;
        boolean armed = PilotSession.INSTANCE.armed();
        StickPositions sticks = this.mapper.map(profile, axes, dt, this.throttle3d, armed);
        float throttle3d = this.mapper.throttle3d();
        int bits = this.evaluator.evaluate(profile, axes, buttons);
        this.evaluated(device.guid(), profile);
        boolean reconfigured = this.noteSwitches(device.guid(), profile);
        int evaluated = bits;
        if (this.captureActive(minecraft, now)) {
            this.capturing = true;
            sticks = StickPositions.IDLE;
            throttle3d = ThrottleCentering.CENTER;
            bits = 0;
        } else {
            if (this.capturing) {
                this.capturing = false;
                this.mapper.reset();
                this.evaluator.reset();
                sticks = this.mapper.map(profile, axes, 0.0F, this.throttle3d, armed);
                throttle3d = this.mapper.throttle3d();
                bits = this.evaluator.evaluate(profile, axes, buttons);
                evaluated = bits;
                this.maskedSwitches = bits & EDGE_FUNCTIONS;
            } else if (reconfigured) {
                this.maskedSwitches |= bits & ~this.lastSwitchBits & ONE_SHOT_FUNCTIONS;
            }
            this.maskedSwitches &= bits;
            bits &= ~this.maskedSwitches;
        }
        this.lastSwitchBits = evaluated;
        this.deviceSwitchBits = bits;
        bits |= this.keySwitchBits();
        if (session) {
            this.sessionGuid = device.guid();
        }
        this.keyboard.reset();
        this.mouse.reset();
        if (this.noteActivity(axes, buttons)) {
            wake(minecraft);
        }
        RadioState.INSTANCE.publish(new RadioFrame(usePad ? RadioSource.GAMEPAD : RadioSource.JOYSTICK, device.displayName(), device.guid(),
                sticks, bits, SwitchEvaluator.readable(profile, axes, buttons), axes.clone(), buttons.clone(), throttle3d),
                profile.binding(SwitchFunction.ARM));
    }

    private boolean noteSwitches(String guid, DeviceProfile profile) {
        boolean sameDevice = guid.equals(this.switchesGuid);
        if (sameDevice && profile.switches.equals(this.appliedSwitches)) {
            return false;
        }
        DeviceProfile applied = new DeviceProfile();
        applied.copySwitchesFrom(profile);
        this.switchesGuid = guid;
        this.appliedSwitches = applied.switches;
        return sameDevice;
    }

    private boolean captureActive(Minecraft minecraft, long now) {
        return now < this.captureUntil || minecraft.gui != null && minecraft.gui.screen() instanceof ReceiverWizardScreen;
    }

    private void publishSimulated(SimulatedRadio fake, float dt) {
        this.capturing = false;
        DeviceProfile profile = fake.profile(InputConfig.get());
        this.commands = profile.commands;
        float[] axes = fake.axes();
        boolean[] buttons = fake.buttons();
        StickPositions sticks = this.mapper.map(profile, axes, dt, this.throttle3d, PilotSession.INSTANCE.armed());
        int bits = this.evaluator.evaluate(profile, axes, buttons);
        this.evaluated(fake.guid(), profile);
        this.deviceSwitchBits = bits;
        this.keyboard.reset();
        this.mouse.reset();
        RadioState.INSTANCE.publish(new RadioFrame(RadioSource.JOYSTICK, fake.device().displayName(), fake.guid(), sticks, bits | this.keySwitchBits(),
                SwitchEvaluator.readable(profile, axes, buttons), axes, buttons, this.mapper.throttle3d()), profile.binding(SwitchFunction.ARM));
    }

    private void publishFallback(Minecraft minecraft, InputConfig config, float dt) {
        this.evaluated(null, null);
        this.previousAxes = NO_AXES;
        this.previousButtons = NO_BUTTONS;
        this.deviceSwitchBits = 0;
        boolean inWorld = minecraft.level != null && minecraft.player != null;
        this.commands = config.keyboard.commands;
        if (!(config.keyboardFallback || config.keyboardSelected()) || !inWorld) {
            this.keyboard.reset();
            this.mouse.reset();
            RadioState.INSTANCE.publish(RadioFrame.NONE);
            return;
        }
        boolean session = PilotSession.INSTANCE.isActive();
        if (config.mouseSelected()) {
            this.publishMouse(minecraft, config, dt, session);
            return;
        }
        this.mouse.reset();
        InputKeys.drain(InputKeys.MOUSE_CENTER);
        this.keyboard.yawKeysSteerRoll(false);
        KeyboardFlight.Output output = this.keyboard.update(dt, session, minecraft.gui.screen() == null, config.keyboard, config.invertPitch,
                this.throttle3d, PilotSession.INSTANCE.armed());
        if (output.keysHeld()) {
            wake(minecraft);
        }
        RadioState.INSTANCE.publish(new RadioFrame(RadioSource.KEYBOARD, I18n.get("propwash.input.keyboard"), "", output.sticks(),
                this.keySwitchBits(), 0, NO_AXES, NO_BUTTONS));
    }

    private void publishMouse(Minecraft minecraft, InputConfig config, float dt, boolean session) {
        boolean keysAvailable = minecraft.gui.screen() == null;
        boolean captured = this.forceMouseCapture || minecraft.mouseHandler.isMouseGrabbed() && minecraft.isWindowActive();
        boolean flying = session && keysAvailable && captured;
        MouseSettings settings = config.mouse;
        MouseCapture.Frame frame = this.mouseFrame;
        this.mouseFlying = flying;
        this.mouseWheel = settings.wheelThrottle();
        this.keyboard.yawKeysSteerRoll(settings.xAxis == MouseAxis.YAW);
        if (flying && frame.wheel() != 0.0F) {
            this.keyboard.nudgeThrottle(frame.wheel() * settings.wheelStep);
        }
        KeyboardFlight.Output keys = this.keyboard.update(dt, session, keysAvailable, config.keyboard, config.invertPitch, this.throttle3d,
                PilotSession.INSTANCE.armed());
        boolean selfLevel = PilotSession.INSTANCE.flightMode() != StabMode.ACRO;
        MouseSticks.Output aim = this.mouse.update(flying, frame, dt, settings, selfLevel, config.invertPitch);
        StickPositions held = keys.sticks();
        StickPositions sticks = new StickPositions(held.roll() + aim.roll(), held.pitch() + aim.pitch(), held.yaw() + aim.yaw(), held.throttle()).clamped();
        if (keys.keysHeld() || flying && frame.moved()) {
            wake(minecraft);
        }
        RadioState.INSTANCE.publish(new RadioFrame(RadioSource.KEYBOARD, I18n.get("propwash.input.mouse_keyboard"), InputConfig.MOUSE_GUID, sticks,
                this.keySwitchBits(), 0, NO_AXES, NO_BUTTONS));
    }

    private boolean noteActivity(float[] axes, boolean[] buttons) {
        boolean changed = false;
        if (this.previousAxes.length != axes.length || this.previousButtons.length != buttons.length) {
            this.previousAxes = axes.clone();
            this.previousButtons = buttons.clone();
            return true;
        }
        for (int i = 0; i < axes.length; i++) {
            if (Math.abs(axes[i] - this.previousAxes[i]) > ACTIVITY_EPSILON) {
                this.previousAxes[i] = axes[i];
                changed = true;
            }
        }
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] != this.previousButtons[i]) {
                this.previousButtons[i] = buttons[i];
                changed = true;
            }
        }
        return changed;
    }

    private static void wake(Minecraft minecraft) {
        FramerateLimitTracker tracker = minecraft.getFramerateLimitTracker();
        if (tracker != null) {
            tracker.onInputReceived();
        }
    }

    private void closeDevice() {
        if (this.gamepad != 0L) {
            SDLGamepad.SDL_CloseGamepad(this.gamepad);
        } else if (this.joystick != 0L) {
            SDLJoystick.SDL_CloseJoystick(this.joystick);
        }
        this.gamepad = 0L;
        this.joystick = 0L;
        this.open = null;
        this.axisCount = 0;
        this.buttonCount = 0;
        this.hatCount = 0;
        this.joystickAxes = NO_AXES;
        this.joystickButtons = NO_BUTTONS;
        this.previousAxes = NO_AXES;
        this.previousButtons = NO_BUTTONS;
        this.capturing = false;
        this.maskedSwitches = 0;
        this.lastSwitchBits = 0;
        this.switchesGuid = null;
    }

    private void closeQuietly() {
        try {
            this.closeDevice();
        } catch (RuntimeException | LinkageError e) {
            this.gamepad = 0L;
            this.joystick = 0L;
            this.open = null;
        }
    }

    public static void showDeviceToast(DeviceInfo device, boolean connected) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return;
        }
        ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, deviceToastTitle(device, connected), deviceToastMessage(device, connected),
                connected ? Palette.SUCCESS : Palette.WARNING, TOAST_MILLIS);
    }

    public static Component deviceToastTitle(DeviceInfo device, boolean connected) {
        return Component.translatable((connected ? "propwash.input.toast.connected." : "propwash.input.toast.disconnected.") + InputLabels.kindId(device));
    }

    public static Component deviceToastMessage(DeviceInfo device, boolean connected) {
        Component name = InputLabels.deviceTitle(device);
        if (!connected) {
            return name;
        }
        boolean loaded = InputConfig.get().setupStatus(device.guid()) == SetupStatus.COMPLETED;
        return Component.translatable(loaded ? "propwash.input.toast.profile_loaded" : "propwash.input.toast.not_set_up", name);
    }

    private static int clampCount(int count, int max) {
        return Math.max(0, Math.min(count, max));
    }

    static float normalize(short raw) {
        return raw < 0 ? raw / 32768.0F : raw / 32767.0F;
    }

    static float trigger(short raw) {
        return Math.max(0, raw) / 32767.0F * 2.0F - 1.0F;
    }
}
