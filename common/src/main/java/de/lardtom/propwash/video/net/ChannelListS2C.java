package de.lardtom.propwash.video.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.VideoSystem;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ChannelListS2C(boolean allowed, List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_NAME = 48;
    public static final ChannelListS2C EMPTY = new ChannelListS2C(true, List.of());
    public static final Type<ChannelListS2C> TYPE = new Type<>(Constants.id("video_channel_list"));
    public static final StreamCodec<ByteBuf, ChannelListS2C> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ChannelListS2C::allowed,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), ChannelListS2C::entries,
            ChannelListS2C::new);

    public ChannelListS2C {
        entries = List.copyOf(entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries);
    }

    @Override
    public Type<ChannelListS2C> type() {
        return TYPE;
    }

    public record Entry(UUID drone, int entityId, VideoSystem system, int channel, boolean manual, Optional<UUID> pilot,
                        String pilotName, String droneName, String frame) {
        private static final StreamCodec<ByteBuf, VideoSystem> SYSTEM = ByteBufCodecs.idMapper(
                i -> VideoSystem.values()[Math.max(0, Math.min(VideoSystem.values().length - 1, i))], VideoSystem::ordinal);
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Entry::drone,
                ByteBufCodecs.VAR_INT, Entry::entityId,
                SYSTEM, Entry::system,
                ByteBufCodecs.VAR_INT, Entry::channel,
                ByteBufCodecs.BOOL, Entry::manual,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Entry::pilot,
                ByteBufCodecs.stringUtf8(MAX_NAME), Entry::pilotName,
                ByteBufCodecs.stringUtf8(MAX_NAME), Entry::droneName,
                ByteBufCodecs.stringUtf8(MAX_NAME), Entry::frame,
                Entry::new);

        public Entry {
            pilotName = clip(pilotName);
            droneName = clip(droneName);
            frame = clip(frame);
        }

        private static String clip(String text) {
            if (text == null) {
                return "";
            }
            return text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text;
        }
    }
}
