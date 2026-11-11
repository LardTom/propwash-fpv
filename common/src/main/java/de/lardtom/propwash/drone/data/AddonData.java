package de.lardtom.propwash.drone.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.drone.DroneDataAccess;
import de.lardtom.propwash.api.drone.DroneDataKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class AddonData {
    public static final String SAVE_KEY = "AddonData";
    public static final AddonData EMPTY = new AddonData(new CompoundTag());
    public static final Codec<AddonData> CODEC = CompoundTag.CODEC.xmap(AddonData::of, AddonData::toTag);
    public static final StreamCodec<ByteBuf, AddonData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(AddonData::of, AddonData::toTag);

    private static final Set<Identifier> WARNED = ConcurrentHashMap.newKeySet();

    private final CompoundTag root;
    private final Map<DroneDataKey<?>, Object> decoded = new ConcurrentHashMap<>();
    private volatile int bytes = -1;

    private AddonData(CompoundTag root) {
        this.root = root;
    }

    public static AddonData of(CompoundTag tag) {
        return tag.isEmpty() ? EMPTY : new AddonData(tag.copy());
    }

    public CompoundTag toTag() {
        return this.root.copy();
    }

    public boolean isEmpty() {
        return this.root.isEmpty();
    }

    public int bytes() {
        int size = this.bytes;
        if (size < 0) {
            size = bytes(this.root);
            this.bytes = size;
        }
        return size;
    }

    public static int bytes(Tag tag) {
        Counter counter = new Counter();
        try (DataOutputStream out = new DataOutputStream(counter)) {
            NbtIo.writeAnyTag(tag, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return counter.count;
    }

    public List<Identifier> ids() {
        List<Identifier> out = new ArrayList<>();
        for (String namespace : this.root.keySet()) {
            this.root.getCompound(namespace).ifPresent(entries -> {
                for (String path : entries.keySet()) {
                    Identifier id = Identifier.tryBuild(namespace, path);
                    if (id != null) {
                        out.add(id);
                    }
                }
            });
        }
        return out;
    }

    public boolean contains(Identifier id) {
        return this.entry(id) != null;
    }

    public Optional<Tag> raw(Identifier id) {
        Tag value = this.entry(id);
        return value == null ? Optional.empty() : Optional.of(value.copy());
    }

    @SuppressWarnings("unchecked")
    public <T> T value(DroneDataKey<T> key, DynamicOps<Tag> ops) {
        Object cached = this.decoded.get(key);
        if (cached != null) {
            return (T) cached;
        }
        Tag value = this.entry(key.id());
        if (value == null) {
            return key.defaultValue();
        }
        DataResult<T> result = key.codec().parse(ops, value);
        Optional<T> parsed = result.result();
        if (parsed.isEmpty()) {
            if (WARNED.add(key.id())) {
                Constants.LOG.warn("Drohnen-Daten {} lassen sich nicht lesen, es gilt der Standard: {}", key.id(),
                        result.error().map(DataResult.Error::message).orElse("?"));
            }
            return key.defaultValue();
        }
        this.decoded.put(key, parsed.get());
        return parsed.get();
    }

    public <T> AddonData with(DroneDataKey<T> key, T value, DynamicOps<Tag> ops) {
        Tag encoded = key.codec().encodeStart(ops, value)
                .getOrThrow(message -> new IllegalArgumentException("Drohnen-Daten " + key.id() + " lassen sich nicht speichern: " + message));
        int size = bytes(encoded);
        if (size > DroneDataAccess.MAX_BYTES_PER_KEY) {
            throw new IllegalArgumentException("Drohnen-Daten " + key.id() + ": " + size + " Bytes, höchstens "
                    + DroneDataAccess.MAX_BYTES_PER_KEY + " je Schlüssel");
        }
        AddonData next = this.withRaw(key.id(), encoded);
        if (next.bytes() > DroneDataAccess.MAX_BYTES_PER_DRONE) {
            throw new IllegalStateException("Drohnen-Daten " + key.id() + ": die Drohne hätte " + next.bytes() + " Bytes, höchstens "
                    + DroneDataAccess.MAX_BYTES_PER_DRONE + " je Drohne");
        }
        return next;
    }

    public AddonData withRaw(Identifier id, Tag value) {
        CompoundTag copy = this.root.copy();
        Tag existing = copy.get(id.getNamespace());
        CompoundTag entries;
        if (existing == null) {
            entries = new CompoundTag();
            copy.put(id.getNamespace(), entries);
        } else if (existing instanceof CompoundTag compound) {
            entries = compound;
        } else {
            throw new IllegalStateException("Drohnen-Daten " + id + ": " + SAVE_KEY + "." + id.getNamespace() + " ist kein Compound");
        }
        entries.put(id.getPath(), value.copy());
        return new AddonData(copy);
    }

    public AddonData without(Identifier id) {
        if (this.entry(id) == null) {
            return this;
        }
        CompoundTag copy = this.root.copy();
        CompoundTag entries = copy.getCompoundOrEmpty(id.getNamespace());
        entries.remove(id.getPath());
        if (entries.isEmpty()) {
            copy.remove(id.getNamespace());
        }
        return copy.isEmpty() ? EMPTY : new AddonData(copy);
    }

    public AddonData filter(Predicate<Identifier> keep) {
        CompoundTag out = new CompoundTag();
        for (String namespace : this.root.keySet()) {
            Optional<CompoundTag> entries = this.root.getCompound(namespace);
            if (entries.isEmpty()) {
                continue;
            }
            CompoundTag kept = new CompoundTag();
            for (String path : entries.get().keySet()) {
                Identifier id = Identifier.tryBuild(namespace, path);
                Tag value = entries.get().get(path);
                if (id != null && value != null && keep.test(id)) {
                    kept.put(path, value.copy());
                }
            }
            if (!kept.isEmpty()) {
                out.put(namespace, kept);
            }
        }
        if (out.isEmpty()) {
            return EMPTY;
        }
        return out.equals(this.root) ? this : new AddonData(out);
    }

    private @Nullable Tag entry(Identifier id) {
        Tag entries = this.root.get(id.getNamespace());
        return entries instanceof CompoundTag compound ? compound.get(id.getPath()) : null;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AddonData data && this.root.equals(data.root);
    }

    @Override
    public int hashCode() {
        return this.root.hashCode();
    }

    @Override
    public String toString() {
        return SAVE_KEY + this.root;
    }

    private static final class Counter extends OutputStream {
        private int count;

        @Override
        public void write(int b) {
            this.count++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            this.count += len;
        }
    }
}
