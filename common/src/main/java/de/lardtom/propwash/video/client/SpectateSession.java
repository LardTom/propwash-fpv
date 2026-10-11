package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartNames;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.KeyRepeatFilter;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.server.client.ClientServerRules;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.video.VideoChannels;
import de.lardtom.propwash.video.net.ChannelListS2C;
import de.lardtom.propwash.video.net.SpectateC2S;
import de.lardtom.propwash.video.net.SpectateS2C;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SpectateSession {
    public static final SpectateSession INSTANCE = new SpectateSession();
    public static final Object TOAST_TOKEN = new Object();
    public static final int CONNECT_TIMEOUT_TICKS = 200;
    public static final double CUT_DISTANCE = 8.0;
    private static final long TOAST_MILLIS = 3000L;
    private static final double SPEED_TAU_SECONDS = 0.25;

    public enum Phase {
        IDLE,
        REQUESTED,
        CONNECTING,
        WATCHING
    }

    private List<ChannelListS2C.Entry> channels = List.of();
    private boolean allowed = true;
    private int listRevision;
    private Phase phase = Phase.IDLE;
    private @Nullable UUID target;
    private int targetEntityId = -1;
    private int phaseTicks;
    private @Nullable QuadcopterEntity watched;
    private long frameNanos;
    private long watchingSinceNanos;
    private final Quaternionf attitude = new Quaternionf();
    private final KeyRepeatFilter cycleKey = new KeyRepeatFilter();
    private double lastX;
    private double lastY;
    private double lastZ;
    private long lastNanos;
    private boolean hasLast;
    private double speed;
    private double verticalSpeed;
    private @Nullable DroneBuild specBuild;
    private @Nullable AirframeSpec spec;
    private SpectateS2C.@Nullable Result lastResult;
    private int pendingStopAcks;
    private int sessions;

    private SpectateSession() {
    }

    public Phase phase() {
        return this.phase;
    }

    public boolean active() {
        return this.phase != Phase.IDLE;
    }

    public boolean watching() {
        return this.phase == Phase.WATCHING && this.watched != null;
    }

    public @Nullable QuadcopterEntity drone() {
        QuadcopterEntity d = this.watched;
        return d != null && !d.isRemoved() ? d : null;
    }

    public @Nullable UUID target() {
        return this.target;
    }

    public List<ChannelListS2C.Entry> channels() {
        return this.channels;
    }

    public boolean allowed() {
        return this.allowed && ClientServerRules.INSTANCE.spectating();
    }

    public int listRevision() {
        return this.listRevision;
    }

    public long frameNanos() {
        return this.frameNanos;
    }

    public long watchingSinceNanos() {
        return this.watchingSinceNanos;
    }

    public double speed() {
        return this.speed;
    }

    public double verticalSpeed() {
        return this.verticalSpeed;
    }

    public SpectateS2C.@Nullable Result lastResult() {
        return this.lastResult;
    }

    public int sessions() {
        return this.sessions;
    }

    public ChannelListS2C.@Nullable Entry targetEntry() {
        UUID id = this.target;
        return id == null ? null : this.entry(id);
    }

    public ChannelListS2C.@Nullable Entry entry(UUID drone) {
        for (ChannelListS2C.Entry e : this.channels) {
            if (e.drone().equals(drone)) {
                return e;
            }
        }
        return null;
    }

    public static @Nullable VideoSystem goggles(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return player == null ? null : ContentIds.wornGogglesSystem(player).orElse(null);
    }

    public static boolean receivable(ChannelListS2C.Entry entry, @Nullable VideoSystem goggles) {
        return goggles != null && goggles.receives(entry.system());
    }

    public static boolean ownFlight(ChannelListS2C.Entry entry, @Nullable LocalPlayer player) {
        return player != null && entry.pilot().filter(player.getUUID()::equals).isPresent();
    }

    public List<ChannelListS2C.Entry> others(Minecraft minecraft) {
        List<ChannelListS2C.Entry> out = new ArrayList<>();
        for (ChannelListS2C.Entry e : this.channels) {
            if (!ownFlight(e, minecraft.player)) {
                out.add(e);
            }
        }
        return out;
    }

    public List<ChannelListS2C.Entry> watchable(Minecraft minecraft) {
        VideoSystem goggles = goggles(minecraft);
        List<ChannelListS2C.Entry> out = new ArrayList<>();
        for (ChannelListS2C.Entry e : this.others(minecraft)) {
            if (receivable(e, goggles)) {
                out.add(e);
            }
        }
        return out;
    }

    public static Component channelName(ChannelListS2C.Entry entry) {
        if (entry.channel() < 0) {
            return Component.translatable("propwash.video.channel.unassigned");
        }
        return Component.translatable("propwash.video.channel.label", VideoChannels.label(entry.system(), entry.channel()),
                VideoChannels.frequencyMhz(entry.system(), entry.channel()));
    }

    public static Component droneName(ChannelListS2C.Entry entry) {
        if (!entry.droneName().isEmpty()) {
            return Component.literal(entry.droneName());
        }
        return PartNames.frameShortByKey(entry.frame());
    }

    public static Component pilotName(ChannelListS2C.Entry entry) {
        return entry.pilotName().isEmpty() ? Component.translatable("propwash.video.spectate.unknown_pilot") : Component.literal(entry.pilotName());
    }

    public boolean request(Minecraft minecraft, ChannelListS2C.Entry entry) {
        Component problem = this.blocker(minecraft, entry);
        if (problem != null) {
            toast(minecraft, problem, null, Palette.WARNING);
            return false;
        }
        if (!ClientNet.sendToServer(SpectateC2S.watch(entry.drone()))) {
            toast(minecraft, Component.translatable("propwash.video.spectate.no_server"), null, Palette.WARNING);
            return false;
        }
        this.target = entry.drone();
        this.targetEntityId = entry.entityId();
        this.phase = Phase.REQUESTED;
        this.phaseTicks = 0;
        this.lastResult = null;
        return true;
    }

    public @Nullable Component blocker(Minecraft minecraft, ChannelListS2C.@Nullable Entry entry) {
        if (!this.allowed()) {
            return Component.translatable("propwash.video.spectate.result.rule_off");
        }
        if (PilotSession.INSTANCE.isActive()) {
            return Component.translatable("propwash.video.spectate.result.piloting");
        }
        VideoSystem goggles = goggles(minecraft);
        if (goggles == null) {
            return Component.translatable("propwash.video.spectate.result.no_goggles");
        }
        if (entry != null && !receivable(entry, goggles)) {
            return Component.translatable("propwash.video.spectate.result.wrong_system");
        }
        return null;
    }

    public void cycle(Minecraft minecraft) {
        Component problem = this.blocker(minecraft, null);
        if (problem != null) {
            if (this.active()) {
                this.stop(minecraft, true);
            }
            toast(minecraft, problem, null, Palette.WARNING);
            return;
        }
        List<ChannelListS2C.Entry> list = this.watchable(minecraft);
        if (list.isEmpty()) {
            if (this.active()) {
                this.stop(minecraft, true);
            }
            Component message = this.others(minecraft).isEmpty() ? Component.translatable("propwash.video.spectate.none")
                    : Component.translatable("propwash.video.spectate.none_for_goggles");
            toast(minecraft, message, null, Palette.INFO);
            return;
        }
        int index = -1;
        UUID current = this.target;
        if (current != null && this.active()) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).drone().equals(current)) {
                    index = i;
                    break;
                }
            }
        }
        if (this.active() && index == list.size() - 1) {
            this.stop(minecraft, true);
            toast(minecraft, Component.translatable("propwash.video.spectate.ended"), null, Palette.INFO);
            return;
        }
        ChannelListS2C.Entry next = list.get(index + 1);
        if (this.request(minecraft, next)) {
            toast(minecraft, Component.translatable("propwash.video.spectate.tuning", channelName(next)),
                    Component.translatable("propwash.video.spectate.pilot_line", pilotName(next), droneName(next)), Palette.SUCCESS);
        }
    }

    public boolean consumeEscape() {
        if (!this.active()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        this.stop(minecraft, true);
        toast(minecraft, Component.translatable("propwash.video.spectate.ended"), null, Palette.INFO);
        return true;
    }

    public void stop(Minecraft minecraft, boolean notifyServer) {
        if (this.phase == Phase.IDLE && this.watched == null) {
            return;
        }
        if (notifyServer && ClientBus.inSession() && ClientNet.sendToServer(SpectateC2S.STOP)) {
            this.pendingStopAcks++;
        }
        this.release(minecraft);
        this.phase = Phase.IDLE;
        this.target = null;
        this.targetEntityId = -1;
        this.phaseTicks = 0;
    }

    private void release(Minecraft minecraft) {
        this.watched = null;
        this.hasLast = false;
        this.speed = 0.0;
        this.verticalSpeed = 0.0;
        if (!PilotSession.INSTANCE.isActive()) {
            FpvView.holdBody(null);
            if (minecraft.player != null && minecraft.getCameraEntity() != minecraft.player) {
                minecraft.setCameraEntity(minecraft.player);
            }
        }
        VideoState.INSTANCE.cut(Util.getNanos());
    }

    void onChannelList(ChannelListS2C list) {
        this.channels = list.entries();
        this.allowed = list.allowed();
        this.listRevision++;
    }

    void onResult(Minecraft minecraft, SpectateS2C result) {
        if (result.result() == SpectateS2C.Result.STOPPED && this.pendingStopAcks > 0) {
            this.pendingStopAcks--;
            return;
        }
        this.lastResult = result.result();
        if (result.result() == SpectateS2C.Result.STARTED) {
            if (this.target != null && this.target.equals(result.drone()) && this.phase == Phase.REQUESTED) {
                this.targetEntityId = result.entityId();
                this.phase = Phase.CONNECTING;
                this.phaseTicks = 0;
            }
            return;
        }
        if (this.phase == Phase.IDLE) {
            return;
        }
        this.stop(minecraft, false);
        if (result.result() != SpectateS2C.Result.STOPPED) {
            toast(minecraft, Component.translatable(result.result().translationKey()), null, Palette.WARNING);
        }
    }

    void onClientTick(Minecraft minecraft) {
        if (InputKeys.pressed(SpectateKeys.CYCLE, this.cycleKey) && minecraft.player != null && minecraft.gui.screen() == null) {
            this.cycle(minecraft);
        }
        if (this.phase == Phase.IDLE) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            this.reset();
            return;
        }
        if (PilotSession.INSTANCE.isActive()) {
            this.stop(minecraft, true);
            return;
        }
        this.phaseTicks++;
        switch (this.phase) {
            case REQUESTED -> {
                if (this.phaseTicks > CONNECT_TIMEOUT_TICKS) {
                    this.fail(minecraft);
                }
            }
            case CONNECTING -> {
                QuadcopterEntity found = this.find(minecraft);
                if (found != null) {
                    this.attach(minecraft, found);
                } else if (this.phaseTicks > CONNECT_TIMEOUT_TICKS) {
                    this.fail(minecraft);
                }
            }
            case WATCHING -> {
                QuadcopterEntity d = this.watched;
                if (d == null || d.isRemoved() || d.level() != minecraft.level) {
                    this.release(minecraft);
                    this.phase = Phase.CONNECTING;
                    this.phaseTicks = 0;
                    return;
                }
                FpvView.holdBody(player);
                if (minecraft.getCameraEntity() != d) {
                    minecraft.setCameraEntity(d);
                }
            }
            default -> {
            }
        }
    }

    private void fail(Minecraft minecraft) {
        this.stop(minecraft, true);
        toast(minecraft, Component.translatable("propwash.video.spectate.timeout"), null, Palette.WARNING);
    }

    private @Nullable QuadcopterEntity find(Minecraft minecraft) {
        UUID id = this.target;
        if (id == null || minecraft.level == null) {
            return null;
        }
        Entity byId = this.targetEntityId >= 0 ? minecraft.level.getEntity(this.targetEntityId) : null;
        if (byId instanceof QuadcopterEntity drone && drone.getUUID().equals(id)) {
            return drone;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof QuadcopterEntity drone && drone.getUUID().equals(id)) {
                return drone;
            }
        }
        return null;
    }

    private void attach(Minecraft minecraft, QuadcopterEntity drone) {
        boolean switched = this.watched != null && this.watched != drone;
        this.watched = drone;
        this.phase = Phase.WATCHING;
        this.phaseTicks = 0;
        this.hasLast = false;
        this.watchingSinceNanos = Util.getNanos();
        this.sessions++;
        if (switched) {
            VideoState.INSTANCE.clearPoses();
        }
        VideoState.INSTANCE.cut(this.watchingSinceNanos);
        FpvView.holdBody(minecraft.player);
        minecraft.setCameraEntity(drone);
    }

    void onFrame(Minecraft minecraft, DeltaTracker deltaTracker) {
        QuadcopterEntity d = this.watched;
        if (d == null || this.phase != Phase.WATCHING || d.isRemoved()) {
            return;
        }
        float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
        Vec3 position = d.getPosition(partial);
        double x = position.x;
        double y = position.y + this.comOffset(d);
        double z = position.z;
        d.renderAttitude(partial, this.attitude);
        long now = Util.getNanos();
        if (this.hasLast) {
            double dx = x - this.lastX;
            double dy = y - this.lastY;
            double dz = z - this.lastZ;
            double dt = (now - this.lastNanos) * 1.0E-9;
            if (dx * dx + dy * dy + dz * dz > CUT_DISTANCE * CUT_DISTANCE) {
                VideoState.INSTANCE.cut(now);
            } else if (dt > 1.0E-4) {
                double alpha = 1.0 - Math.exp(-dt / SPEED_TAU_SECONDS);
                this.speed += (Math.sqrt(dx * dx + dy * dy + dz * dz) / dt - this.speed) * alpha;
                this.verticalSpeed += (dy / dt - this.verticalSpeed) * alpha;
            }
        }
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.lastNanos = now;
        this.hasLast = true;
        VideoState.INSTANCE.record(now, x, y, z, this.attitude);
        this.frameNanos = now;
    }

    public double comOffset(QuadcopterEntity drone) {
        return -this.spec(drone).hitboxFootOffset;
    }

    public double lensForwardOffset(QuadcopterEntity drone) {
        return this.spec(drone).lensForward;
    }

    public double lensUpOffset(QuadcopterEntity drone) {
        return this.spec(drone).lensUp;
    }

    public double lensRightOffset(QuadcopterEntity drone) {
        return this.spec(drone).lensRight;
    }

    public float cameraUptiltDeg(QuadcopterEntity drone) {
        return CameraMount.uptilt(drone.build(), drone.tune().values());
    }

    private AirframeSpec spec(QuadcopterEntity drone) {
        DroneBuild build = drone.build();
        AirframeSpec current = this.spec;
        if (current == null || !build.equals(this.specBuild)) {
            current = AirframeSpec.of(build);
            this.spec = current;
            this.specBuild = build;
        }
        return current;
    }

    void onDisconnect(Minecraft minecraft) {
        this.reset();
        this.channels = List.of();
        this.allowed = true;
        this.lastResult = null;
        this.pendingStopAcks = 0;
    }

    private void reset() {
        this.watched = null;
        this.phase = Phase.IDLE;
        this.target = null;
        this.targetEntityId = -1;
        this.phaseTicks = 0;
        this.hasLast = false;
        if (!PilotSession.INSTANCE.isActive()) {
            FpvView.holdBody(null);
        }
    }

    public Optional<ChannelListS2C.Entry> firstWatchable(Minecraft minecraft) {
        List<ChannelListS2C.Entry> list = this.watchable(minecraft);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    private static void toast(Minecraft minecraft, Component title, @Nullable Component message, int accent) {
        if (minecraft.player != null) {
            ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, title, message, accent, TOAST_MILLIS);
        }
    }
}
