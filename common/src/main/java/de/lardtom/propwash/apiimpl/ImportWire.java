package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public record ImportWire(int target, int layers, int tuneGroups, List<String> parts, Map<String, String> accessories,
                         Map<String, Integer> paint, Map<String, Float> tune, String name) {
    public static final int PART_COUNT = 6;
    public static final int MAX_ID_LENGTH = 256;
    public static final int MAX_ACCESSORIES = 8;
    public static final int MAX_PAINT = 32;
    public static final int MAX_SLOT_KEY = 32;
    public static final int MAX_TUNE_KEY = 64;
    public static final int MAX_NAME = 96;
    public static final int MAX_ENCODED_BYTES = 30000;

    public static final StreamCodec<ByteBuf, ImportWire> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ImportWire::target,
            ByteBufCodecs.VAR_INT, ImportWire::layers,
            ByteBufCodecs.VAR_INT, ImportWire::tuneGroups,
            ByteBufCodecs.stringUtf8(MAX_ID_LENGTH).apply(ByteBufCodecs.list(PART_COUNT)), ImportWire::parts,
            ByteBufCodecs.<ByteBuf, String, String, Map<String, String>>map(LinkedHashMap::new, ByteBufCodecs.stringUtf8(MAX_SLOT_KEY),
                    ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), MAX_ACCESSORIES), ImportWire::accessories,
            ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(LinkedHashMap::new, ByteBufCodecs.stringUtf8(MAX_SLOT_KEY),
                    ByteBufCodecs.INT, MAX_PAINT), ImportWire::paint,
            ByteBufCodecs.<ByteBuf, String, Float, Map<String, Float>>map(HashMap::new, ByteBufCodecs.stringUtf8(MAX_TUNE_KEY),
                    ByteBufCodecs.FLOAT, TuneSpec.MAX_ENTRIES), ImportWire::tune,
            ByteBufCodecs.stringUtf8(MAX_NAME), ImportWire::name,
            ImportWire::new);

    public ImportWire {
        parts = List.copyOf(parts);
        accessories = Collections.unmodifiableMap(new LinkedHashMap<>(accessories));
        paint = Collections.unmodifiableMap(new LinkedHashMap<>(paint));
        tune = Collections.unmodifiableMap(new TreeMap<>(tune));
        name = name == null ? "" : name;
    }

    public static ImportWire of(ImportRequest request) {
        DroneData data = request.data();
        BuildSpec build = data.build();
        List<String> parts = List.of(build.frame().toString(), build.stack().toString(), build.motor().toString(), build.prop().toString(),
                build.video().toString(), build.battery().toString());
        Map<String, String> accessories = new LinkedHashMap<>();
        for (Map.Entry<AccessorySlot, Identifier> entry : build.accessories().entrySet()) {
            accessories.put(entry.getKey().key(), entry.getValue().toString());
        }
        return new ImportWire(request.target().ordinal(), mask(request.layers()), mask(request.tuneGroups()), parts, accessories,
                data.paint().colors(), data.tune().values(), data.name().orElse(""));
    }

    public int encodedBytes() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            CODEC.encode(buffer, this);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }

    public ImportRequest toRequest() {
        if (this.target < 0 || this.target >= ImportTarget.values().length) {
            throw new IllegalArgumentException("Unbekanntes Import-Ziel: " + this.target);
        }
        if (this.parts.size() != PART_COUNT) {
            throw new IllegalArgumentException("Bauplan braucht " + PART_COUNT + " Teile, gesendet: " + this.parts.size());
        }
        List<Identifier> ids = new ArrayList<>(PART_COUNT);
        for (String part : this.parts) {
            ids.add(parseId(part));
        }
        EnumMap<AccessorySlot, Identifier> accessories = new EnumMap<>(AccessorySlot.class);
        for (Map.Entry<String, String> entry : this.accessories.entrySet()) {
            AccessorySlot slot = AccessorySlot.byKey(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Unbekannter Zubehör-Platz: " + entry.getKey()));
            accessories.put(slot, parseId(entry.getValue()));
        }
        BuildSpec build = new BuildSpec(ids.get(0), ids.get(1), ids.get(2), ids.get(3), ids.get(4), ids.get(5), accessories);
        DroneData data = new DroneData(build, new PaintSpec(this.paint), new TuneSpec(this.tune),
                this.name.isEmpty() ? Optional.empty() : Optional.of(this.name));
        return new ImportRequest(data, unmask(ImportLayer.class, this.layers), unmask(TuneGroup.class, this.tuneGroups),
                ImportTarget.values()[this.target]);
    }

    private static Identifier parseId(String text) {
        Identifier id = Identifier.tryParse(text);
        if (id == null) {
            throw new IllegalArgumentException("Ungültige Teil-Id: " + text);
        }
        return id;
    }

    static <E extends Enum<E>> int mask(Set<E> values) {
        int mask = 0;
        for (E value : values) {
            if (value.ordinal() >= Integer.SIZE - 1) {
                throw new IllegalArgumentException("Zu viele Werte für eine Bitmaske: " + value);
            }
            mask |= 1 << value.ordinal();
        }
        return mask;
    }

    static <E extends Enum<E>> Set<E> unmask(Class<E> type, int mask) {
        E[] all = type.getEnumConstants();
        if (mask < 0 || (all.length < Integer.SIZE - 1 && (mask >>> all.length) != 0)) {
            throw new IllegalArgumentException("Unbekannte Werte in der Bitmaske für " + type.getSimpleName() + ": " + mask);
        }
        EnumSet<E> out = EnumSet.noneOf(type);
        for (E value : all) {
            if ((mask & (1 << value.ordinal())) != 0) {
                out.add(value);
            }
        }
        return out;
    }
}
