package de.lardtom.propwash.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

public record PartRef(PartCategory category, String key) {
    public static final Codec<PartRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            PartCategory.CODEC.fieldOf("category").forGetter(PartRef::category),
            Codec.STRING.fieldOf("key").forGetter(PartRef::key)
    ).apply(i, PartRef::new));

    public static final StreamCodec<ByteBuf, PartRef> STREAM_CODEC = StreamCodec.composite(
            PartCategory.STREAM_CODEC, PartRef::category,
            ByteBufCodecs.stringUtf8(PartIndex.MAX_KEY_LENGTH), PartRef::key,
            PartRef::new);

    public static PartRef of(PartInfo part) {
        return new PartRef(PartCategory.of(part), part.key());
    }

    public Optional<PartInfo> resolve() {
        return this.category.resolve(this.key);
    }

    public Optional<PartInfo> resolveOrMissing() {
        return PartIndex.resolveOrMissing(this.category.kind(), this.key, null);
    }

    public <E extends PartInfo> Optional<E> resolve(Class<E> type) {
        return this.resolve().filter(type::isInstance).map(type::cast);
    }
}
