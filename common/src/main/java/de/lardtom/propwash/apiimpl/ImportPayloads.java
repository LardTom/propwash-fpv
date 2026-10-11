package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ImportPayloads {
    public static final int MAX_MISSING = 64;

    private ImportPayloads() {
    }

    public record ImportC2S(int requestId, int containerId, ImportWire request) implements CustomPacketPayload {
        public static final int NO_CONTAINER = -1;
        public static final Type<ImportC2S> TYPE = new Type<>(Constants.id("api_import"));
        public static final StreamCodec<ByteBuf, ImportC2S> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ImportC2S::requestId,
                ByteBufCodecs.VAR_INT, ImportC2S::containerId,
                ImportWire.CODEC, ImportC2S::request,
                ImportC2S::new);

        @Override
        public Type<ImportC2S> type() {
            return TYPE;
        }
    }

    public record ImportResultS2C(int requestId, int status, int applied, List<String> missingParts, Map<String, Integer> missingDyes)
            implements CustomPacketPayload {
        public static final Type<ImportResultS2C> TYPE = new Type<>(Constants.id("api_import_result"));
        public static final StreamCodec<ByteBuf, ImportResultS2C> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ImportResultS2C::requestId,
                ByteBufCodecs.VAR_INT, ImportResultS2C::status,
                ByteBufCodecs.VAR_INT, ImportResultS2C::applied,
                ByteBufCodecs.stringUtf8(ImportWire.MAX_ID_LENGTH).apply(ByteBufCodecs.list(MAX_MISSING)), ImportResultS2C::missingParts,
                ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(LinkedHashMap::new, ByteBufCodecs.stringUtf8(64),
                        ByteBufCodecs.VAR_INT, MAX_MISSING), ImportResultS2C::missingDyes,
                ImportResultS2C::new);

        public ImportResultS2C {
            missingParts = List.copyOf(missingParts);
            missingDyes = new LinkedHashMap<>(missingDyes);
        }

        public static ImportResultS2C of(int requestId, ImportResult result) {
            List<String> parts = new ArrayList<>();
            for (Identifier id : result.missingParts()) {
                if (parts.size() >= MAX_MISSING) {
                    break;
                }
                parts.add(id.toString());
            }
            Map<String, Integer> dyes = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : result.missingDyes().entrySet()) {
                if (dyes.size() >= MAX_MISSING) {
                    break;
                }
                dyes.put(entry.getKey(), entry.getValue());
            }
            return new ImportResultS2C(requestId, result.status().ordinal(), ImportWire.mask(result.applied()), parts, dyes);
        }

        public ImportResult result() {
            ImportStatus[] statuses = ImportStatus.values();
            if (this.status < 0 || this.status >= statuses.length) {
                return ImportResult.of(ImportStatus.INVALID);
            }
            List<Identifier> parts = new ArrayList<>();
            for (String text : this.missingParts) {
                Identifier id = Identifier.tryParse(text);
                if (id != null) {
                    parts.add(id);
                }
            }
            Set<ImportLayer> layers;
            try {
                layers = ImportWire.unmask(ImportLayer.class, this.applied);
            } catch (IllegalArgumentException e) {
                layers = Set.of();
            }
            return new ImportResult(statuses[this.status], layers, parts, this.missingDyes);
        }

        @Override
        public Type<ImportResultS2C> type() {
            return TYPE;
        }
    }
}
