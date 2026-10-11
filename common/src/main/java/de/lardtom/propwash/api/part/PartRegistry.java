package de.lardtom.propwash.api.part;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public interface PartRegistry {
    <D extends PartDefinition> D register(D definition);

    CellChemistry registerChemistry(CellChemistry chemistry);

    int loadBundled(String modId);

    Optional<PartDefinition> get(Identifier id);

    <D extends PartDefinition> Optional<D> get(Class<D> type, Identifier id);

    List<PartDefinition> all();

    List<PartDefinition> all(PartKind kind);

    Optional<CellChemistry> chemistry(Identifier id);

    List<CellChemistry> chemistries();

    boolean isBuiltin(Identifier id);

    boolean isFrozen();

    String fingerprint();

    default JsonObject toJson(PartDefinition definition) {
        throw new UnsupportedOperationException("toJson");
    }

    default JsonObject toJson(CellChemistry chemistry) {
        throw new UnsupportedOperationException("toJson");
    }

    default PartDefinition fromJson(PartKind kind, Identifier id, JsonObject json) {
        throw new UnsupportedOperationException("fromJson");
    }

    default CellChemistry chemistryFromJson(Identifier id, JsonObject json) {
        throw new UnsupportedOperationException("chemistryFromJson");
    }
}
