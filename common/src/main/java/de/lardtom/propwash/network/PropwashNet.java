package de.lardtom.propwash.network;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.Services;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PropwashNet {
    private static final List<C2S<?>> C2S_PAYLOADS = new ArrayList<>();
    private static final List<S2C<?>> S2C_PAYLOADS = new ArrayList<>();
    private static final Set<Identifier> IDS = new HashSet<>();
    private static boolean frozen;
    private static volatile boolean commonInitClosed;

    private PropwashNet() {
    }

    public static <T extends CustomPacketPayload> void registerC2S(CustomPacketPayload.Type<T> type,
                                                                   StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                   ServerPayloadHandler<T> handler) {
        checkOpen(type);
        C2S_PAYLOADS.add(new C2S<>(type, codec, handler));
    }

    public static <T extends CustomPacketPayload> void registerS2C(CustomPacketPayload.Type<T> type,
                                                                   StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        checkOpen(type);
        S2C_PAYLOADS.add(new S2C<>(type, codec));
    }

    private static void checkOpen(CustomPacketPayload.Type<?> type) {
        if (frozen || commonInitClosed) {
            throw new IllegalStateException("Payload " + type.id() + " nach PropwashCommon.init() registriert: Payloads (C2S und S2C)"
                    + " gehören in die Common-init() des Moduls, nur ClientNet.handle in die Client-init()");
        }
        if (!Constants.MOD_ID.equals(type.id().getNamespace())) {
            throw new IllegalArgumentException("Payload-Id muss im Namespace " + Constants.MOD_ID + " liegen: " + type.id());
        }
        if (!IDS.add(type.id())) {
            throw new IllegalArgumentException("Payload doppelt registriert: " + type.id());
        }
    }

    public static void closeAfterCommonInit() {
        commonInitClosed = true;
    }

    public static boolean isS2C(CustomPacketPayload.Type<?> type) {
        for (S2C<?> s : S2C_PAYLOADS) {
            if (s.type().equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static List<CustomPacketPayload.Type<?>> s2cTypes() {
        List<CustomPacketPayload.Type<?>> types = new ArrayList<>(S2C_PAYLOADS.size());
        for (S2C<?> s : S2C_PAYLOADS) {
            types.add(s.type());
        }
        return types;
    }

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return Services.NETWORK.canSend(player, type);
    }

    public static boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (!canSend(player, payload.type())) {
            return false;
        }
        Services.NETWORK.sendToPlayer(player, payload);
        return true;
    }

    public static void sendToTrackingAndSelf(Entity entity, CustomPacketPayload payload) {
        if (entity.level().isClientSide()) {
            throw new IllegalStateException("sendToTrackingAndSelf nur auf dem Server");
        }
        CustomPacketPayload.Type<?> type = payload.type();
        if (entity.level().getChunkSource() instanceof ServerChunkCache cache) {
            cache.sendToTrackingPlayersFiltered(entity, Services.NETWORK.clientboundPacket(payload), p -> canSend(p, type));
        }
        if (entity instanceof ServerPlayer self) {
            sendToPlayer(self, payload);
        }
    }

    public static List<C2S<?>> freezeC2S() {
        frozen = true;
        return Collections.unmodifiableList(C2S_PAYLOADS);
    }

    public static List<S2C<?>> freezeS2C() {
        frozen = true;
        return Collections.unmodifiableList(S2C_PAYLOADS);
    }

    @FunctionalInterface
    public interface ServerPayloadHandler<T extends CustomPacketPayload> {
        void handle(T payload, ServerPayloadContext context);
    }

    public record C2S<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                      StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                      ServerPayloadHandler<T> handler) {
    }

    public record S2C<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                      StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
    }
}
