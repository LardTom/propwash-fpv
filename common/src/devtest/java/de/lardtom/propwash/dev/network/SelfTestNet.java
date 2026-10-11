package de.lardtom.propwash.dev.network;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.network.PropwashNet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class SelfTestNet {
    private SelfTestNet() {
    }

    public static void init() {
        PropwashNet.registerC2S(PingC2S.TYPE, PingC2S.CODEC, (payload, ctx) -> {
            if (!SelfTest.enabled()) {
                return;
            }
            if (!ctx.server().isSameThread()) {
                Constants.LOG.error("Selbsttest-Ping nicht auf dem Server-Thread empfangen");
                return;
            }
            PropwashNet.sendToTrackingAndSelf(ctx.player(), new PongS2C(payload.nonce()));
        });
        PropwashNet.registerS2C(PongS2C.TYPE, PongS2C.CODEC);
    }

    public record PingC2S(int nonce) implements CustomPacketPayload {
        public static final Type<PingC2S> TYPE = new Type<>(Constants.id("selftest_ping"));
        public static final StreamCodec<ByteBuf, PingC2S> CODEC = ByteBufCodecs.VAR_INT.map(PingC2S::new, PingC2S::nonce);

        @Override
        public Type<PingC2S> type() {
            return TYPE;
        }
    }

    public record PongS2C(int nonce) implements CustomPacketPayload {
        public static final Type<PongS2C> TYPE = new Type<>(Constants.id("selftest_pong"));
        public static final StreamCodec<ByteBuf, PongS2C> CODEC = ByteBufCodecs.VAR_INT.map(PongS2C::new, PongS2C::nonce);

        @Override
        public Type<PongS2C> type() {
            return TYPE;
        }
    }
}
