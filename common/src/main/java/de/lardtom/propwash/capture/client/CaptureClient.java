package de.lardtom.propwash.capture.client;

import de.lardtom.propwash.api.client.CameraChunkRequests;
import de.lardtom.propwash.api.drone.CameraChunkResult;
import de.lardtom.propwash.capture.CaptureC2S;
import de.lardtom.propwash.capture.CaptureS2C;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.network.client.ClientNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class CaptureClient implements CameraChunkRequests {
    public static final CaptureClient INSTANCE = new CaptureClient();
    private static boolean initialized;
    private final Map<Integer, CaptureS2C.Entry> entries = new HashMap<>();
    private final Map<Integer, CameraChunkResult.Status> answers = new HashMap<>();
    private int storageRadius;
    private int received;

    private CaptureClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientNet.handle(CaptureS2C.TYPE, (payload, ctx) -> INSTANCE.accept(payload));
        ClientBus.DISCONNECT.register(minecraft -> INSTANCE.clear());
    }

    public void accept(CaptureS2C payload) {
        this.received++;
        this.entries.clear();
        for (CaptureS2C.Entry entry : payload.areas()) {
            this.entries.put(entry.entityId(), entry);
        }
        CameraChunkResult.Status[] statuses = CameraChunkResult.Status.values();
        if (payload.answerEntity() != CaptureS2C.NO_ANSWER && payload.answerStatus() >= 0 && payload.answerStatus() < statuses.length) {
            this.answers.put(payload.answerEntity(), statuses[payload.answerStatus()]);
        }
        this.storageRadius = Math.max(0, payload.storageRadius());
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getChunkSource() instanceof CaptureStorage storage) {
            storage.propwash$cameraStorage(this.storageRadius);
        }
    }

    public void clear() {
        this.entries.clear();
        this.answers.clear();
        this.storageRadius = 0;
    }

    public int storageRadius() {
        return this.storageRadius;
    }

    public int received() {
        return this.received;
    }

    public int entries() {
        return this.entries.size();
    }

    @Override
    public boolean supported() {
        return ClientNet.canSend(CaptureC2S.TYPE);
    }

    @Override
    public boolean request(int entityId, int radiusChunks) {
        if (!this.supported() || Minecraft.getInstance().level == null) {
            return false;
        }
        this.answers.remove(entityId);
        return ClientNet.sendToServer(new CaptureC2S(entityId, Math.max(1, radiusChunks)));
    }

    @Override
    public boolean release(int entityId) {
        if (!this.supported()) {
            return false;
        }
        this.answers.remove(entityId);
        return ClientNet.sendToServer(new CaptureC2S(entityId, 0));
    }

    @Override
    public int radius(int entityId) {
        CaptureS2C.Entry entry = this.entries.get(entityId);
        return entry == null || entry.paused() ? 0 : entry.radius();
    }

    public boolean registered(int entityId) {
        return this.entries.containsKey(entityId);
    }

    @Override
    public Optional<CameraChunkResult.Status> answer(int entityId) {
        return Optional.ofNullable(this.answers.get(entityId));
    }
}
