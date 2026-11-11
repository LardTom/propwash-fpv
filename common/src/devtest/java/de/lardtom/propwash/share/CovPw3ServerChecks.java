package de.lardtom.propwash.share;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.tree.CommandNode;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CovPw3ServerChecks {
    public static final String GOLDEN = "/cov/cov-pw3/jmp-preset-parts.json";
    private static String summary = "";

    private CovPw3ServerChecks() {
    }

    public static @Nullable String check(MinecraftServer server) {
        List<String> errors = new ArrayList<>();
        String webexport = webexportRights(server, errors);
        String presets = jmpPresets(errors);
        summary = webexport + "; " + presets;
        return errors.isEmpty() ? null : String.join("; ", errors);
    }

    public static String summary() {
        return summary;
    }

    static String webexportRights(MinecraftServer server, List<String> errors) {
        CommandNode<CommandSourceStack> root = server.getCommands().getDispatcher().getRoot().getChild(ShareCommands.ROOT);
        CommandNode<CommandSourceStack> node = root == null ? null : root.getChild("webexport");
        if (node == null) {
            errors.add("/propwash webexport fehlt");
            return "webexport fehlt";
        }
        CommandSourceStack console = server.createCommandSourceStack();
        boolean level2 = node.canUse(console.withPermission(LevelBasedPermissionSet.GAMEMASTER));
        boolean level3 = node.canUse(console.withPermission(LevelBasedPermissionSet.ADMIN));
        boolean level4 = node.canUse(console.withPermission(LevelBasedPermissionSet.OWNER));
        boolean consoleUse = node.canUse(console);
        if (level2 || level3 || !level4 || !consoleUse) {
            errors.add("/propwash webexport: Stufe 2 " + level2 + ", Stufe 3 " + level3 + ", Stufe 4 " + level4 + ", Konsole " + consoleUse);
        }
        return "webexport: Stufe 2 " + level2 + ", Stufe 3 " + level3 + ", Stufe 4 " + level4 + ", Konsole " + consoleUse;
    }

    static String jmpPresets(List<String> errors) {
        Optional<String> version = Services.PLATFORM.modVersion(ShareVectors.JMP);
        if (version.isEmpty()) {
            return "JMP-Presets: Just More Parts nicht geladen";
        }
        Map<String, String> golden = golden();
        Map<String, String> exported = new LinkedHashMap<>();
        List<String> fresh = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        for (DronePreset preset : PropwashApi.builds().presets()) {
            if (!preset.id().getNamespace().equals(ShareVectors.JMP)) {
                continue;
            }
            String code = ShareService.encode(DroneData.of(preset.data().build()), EnumSet.of(ShareLayer.PARTS), true, Optional.empty());
            String key = preset.id().toString();
            exported.put(key, code);
            String expected = golden.get(key);
            if (expected == null) {
                fresh.add(key + " " + code);
            } else if (!expected.equals(code)) {
                changed.add(key + " " + code + " statt " + expected);
            }
        }
        List<String> gone = new ArrayList<>();
        for (String key : golden.keySet()) {
            if (!exported.containsKey(key)) {
                gone.add(key);
            }
        }
        if (!changed.isEmpty()) {
            errors.add("JMP-Presets weichen von der Golden-Umrechnung ab: " + changed);
        }
        if (!gone.isEmpty()) {
            errors.add("JMP-Presets fehlen: " + gone);
        }
        return "JMP " + version.get() + ": " + exported.size() + " Presets nur mit Teilen exportiert, " + (exported.size() - fresh.size())
                + " gleich der Golden-Umrechnung, neu ohne Vergleichswert " + fresh;
    }

    static Map<String, String> golden() {
        try (InputStream in = CovPw3ServerChecks.class.getResourceAsStream(GOLDEN)) {
            if (in == null) {
                throw new IllegalStateException("Vergleichswerte fehlen: " + GOLDEN);
            }
            JsonObject presets = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("presets");
            Map<String, String> out = new LinkedHashMap<>();
            presets.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            return out;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
