package de.lardtom.propwash.guard;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.apiimpl.NamespaceRules;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class RegistryScan {
    private RegistryScan() {
    }

    public static void run(GuardReport report) {
        int unattributed = 0;
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            String registryId = registry.key().identifier().toString();
            for (Identifier id : registry.keySet()) {
                if (!NamespaceRules.isReserved(id.getNamespace())) {
                    continue;
                }
                Optional<Optional<String>> origin = RegistryOrigins.get(registryId, id.toString());
                Optional<String> mod = origin.flatMap(found -> found);
                if (mod.isEmpty()) {
                    unattributed++;
                } else if (!NamespaceGuard.own(mod)) {
                    report.add(mod.get(), GuardCategory.REGISTRY, id.toString());
                }
            }
        }
        if (unattributed > 0) {
            Constants.LOG.info("Namespace check: {} registry ids in the namespace propwash could not be assigned to a mod (info)", unattributed);
        }
    }
}
