package de.lardtom.propwash.input.addon;

import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.apiimpl.NamespaceRules;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

public final class AddonIds {
    public static final int MAX_LENGTH = SwitchSpec.MAX_ID_LENGTH;
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private AddonIds() {
    }

    public static boolean valid(@Nullable String key) {
        return key != null && key.length() <= MAX_LENGTH && FORMAT.matcher(key).matches() && !NamespaceRules.isReservedId(key);
    }

    public static boolean valid(@Nullable Identifier id) {
        return id != null && valid(id.toString());
    }

    public static @Nullable Identifier parse(@Nullable String key) {
        return valid(key) ? Identifier.tryParse(key) : null;
    }
}
