package de.lardtom.propwash.network.client;

import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.platform.client.ClientServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClientNet {
    private static final List<Handler<?>> HANDLERS = new ArrayList<>();
    private static final Set<CustomPacketPayload.Type<?>> HANDLED = new HashSet<>();
    private static boolean frozen;

    private ClientNet() {
    }

    public static <T extends CustomPacketPayload> void handle(CustomPacketPayload.Type<T> type, ClientPayloadHandler<T> handler) {
        if (frozen) {
            throw new IllegalStateException("Client-Handler für " + type.id() + " zu spät registriert (nur in der Client-init())");
        }
        if (!HANDLED.add(type)) {
            throw new IllegalArgumentException("Client-Handler doppelt registriert: " + type.id());
        }
        HANDLERS.add(new Handler<>(type, handler));
    }

    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return Minecraft.getInstance().getConnection() != null && ClientServices.PLATFORM.canSendToServer(type);
    }

    public static boolean sendToServer(CustomPacketPayload payload) {
        if (!canSend(payload.type())) {
            return false;
        }
        ClientServices.PLATFORM.sendToServer(payload);
        return true;
    }

    public static List<Handler<?>> freeze() {
        frozen = true;
        for (Handler<?> h : HANDLERS) {
            if (!PropwashNet.isS2C(h.type())) {
                throw new IllegalStateException("Client-Handler für nicht angemeldeten S2C-Payload " + h.type().id()
                        + " (PropwashNet.registerS2C in der Common-init() fehlt)");
            }
        }
        List<String> missing = new ArrayList<>();
        for (CustomPacketPayload.Type<?> type : PropwashNet.s2cTypes()) {
            if (!HANDLED.contains(type)) {
                missing.add(type.id().toString());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Server→Client-Payloads ohne Client-Handler: " + missing
                    + " — ClientNet.handle(TYPE, ...) in der Client-init() des Moduls fehlt (immer registrieren,"
                    + " nicht nur im Selbsttest oder abhängig von einer Config)");
        }
        return Collections.unmodifiableList(HANDLERS);
    }

    @FunctionalInterface
    public interface ClientPayloadHandler<T extends CustomPacketPayload> {
        void handle(T payload, ClientPayloadContext context);
    }

    public record ClientPayloadContext(Minecraft minecraft, LocalPlayer player) {
    }

    public record Handler<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, ClientPayloadHandler<T> handler) {
    }
}
