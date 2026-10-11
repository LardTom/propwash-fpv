package de.lardtom.propwash.parts;

import com.google.gson.JsonObject;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.PartRegistry;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PartRegistryImpl implements PartRegistry {
    public static final PartRegistryImpl INSTANCE = new PartRegistryImpl();

    private PartRegistryImpl() {
    }

    @Override
    public <D extends PartDefinition> D register(D definition) {
        PartIndex.register(definition);
        PropwashEvents.PART_REGISTERED.fire(listener -> listener.onPartRegistered(definition));
        return definition;
    }

    @Override
    public CellChemistry registerChemistry(CellChemistry chemistry) {
        PartIndex.registerChemistry(chemistry);
        return chemistry;
    }

    @Override
    public int loadBundled(String modId) {
        return BundledParts.load(modId);
    }

    @Override
    public Optional<PartDefinition> get(Identifier id) {
        return PartIndex.byId(id).map(PartInfo::definition);
    }

    @Override
    public <D extends PartDefinition> Optional<D> get(Class<D> type, Identifier id) {
        return this.get(id).filter(type::isInstance).map(type::cast);
    }

    @Override
    public List<PartDefinition> all() {
        List<PartDefinition> out = new ArrayList<>();
        for (PartInfo part : PartIndex.all()) {
            out.add(part.definition());
        }
        return List.copyOf(out);
    }

    @Override
    public List<PartDefinition> all(PartKind kind) {
        List<PartDefinition> out = new ArrayList<>();
        for (PartInfo part : PartIndex.all(kind)) {
            out.add(part.definition());
        }
        return List.copyOf(out);
    }

    @Override
    public Optional<CellChemistry> chemistry(Identifier id) {
        return PartIndex.chemistry(id).map(BatteryType.Chemistry::definition);
    }

    @Override
    public List<CellChemistry> chemistries() {
        List<CellChemistry> out = new ArrayList<>();
        for (BatteryType.Chemistry chemistry : PartIndex.chemistries()) {
            out.add(chemistry.definition());
        }
        return List.copyOf(out);
    }

    @Override
    public boolean isBuiltin(Identifier id) {
        return PartIndex.isBuiltin(id);
    }

    @Override
    public boolean isFrozen() {
        return PartIndex.isFrozen();
    }

    @Override
    public String fingerprint() {
        return PartIndex.fingerprint();
    }

    @Override
    public JsonObject toJson(PartDefinition definition) {
        return PartJson.encode(Objects.requireNonNull(definition, "definition"));
    }

    @Override
    public JsonObject toJson(CellChemistry chemistry) {
        return PartJson.encode(Objects.requireNonNull(chemistry, "chemistry"));
    }

    @Override
    public PartDefinition fromJson(PartKind kind, Identifier id, JsonObject json) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(json, "json");
        PartDataProblems problems = new PartDataProblems(id.toString());
        PartDefinition definition = PartJson.decode(kind, id, json.deepCopy(), problems);
        if (definition == null) {
            throw new IllegalArgumentException(id + ": " + String.join("; ", problems.errors()));
        }
        return definition;
    }

    @Override
    public CellChemistry chemistryFromJson(Identifier id, JsonObject json) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(json, "json");
        PartDataProblems problems = new PartDataProblems(id.toString());
        CellChemistry chemistry = PartJson.decodeChemistry(id, json.deepCopy(), problems);
        if (chemistry == null) {
            throw new IllegalArgumentException(id + ": " + String.join("; ", problems.errors()));
        }
        return chemistry;
    }
}
