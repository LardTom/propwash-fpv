package de.lardtom.propwash.share;

import com.mojang.brigadier.tree.CommandNode;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public final class ShareServerChecks {
    public static final List<String> COMMANDS = List.of("export", "import", "info", "convert", "give");
    private static String summary = "";

    private ShareServerChecks() {
    }

    public static @Nullable String check(MinecraftServer server) {
        List<String> errors = new ArrayList<>();
        CommandNode<CommandSourceStack> root = server.getCommands().getDispatcher().getRoot().getChild(ShareCommands.ROOT);
        CommandNode<CommandSourceStack> code = root == null ? null : root.getChild(ShareCommands.NODE);
        if (code == null) {
            return "/propwash code fehlt";
        }
        for (String child : COMMANDS) {
            if (code.getChild(child) == null) {
                errors.add("/propwash code " + child + " fehlt");
            }
        }
        int info = execute(server, "propwash code info " + ShareVectors.P1);
        int broken = execute(server, "propwash code info " + ShareVectors.P10_CHECKSUM);
        int convert = execute(server, "propwash code convert " + ShareVectors.PW2_NAMED);
        if (info != ShareCommands.SUCCESS || broken != ShareCommands.FAILURE || convert != ShareCommands.SUCCESS) {
            errors.add("info " + info + ", info P10 " + broken + ", convert " + convert);
        }
        ShareConvert.Conversion pw1 = ShareService.convert(ShareVectors.PW1_A);
        if (!pw1.code().equals(Optional.of(ShareVectors.PW1_A_PW3))) {
            errors.add("PW1 umgerechnet zu " + pw1.code());
        }
        ShareDecoded named = ShareService.decode(ShareVectors.NAMED_WHOOP);
        if (!named.partsKnown() || !named.require().name().equals(Optional.of(ShareVectors.NAMED_WHOOP_NAME))) {
            errors.add("benannter Vektor: " + named.status());
        }
        String jmp = "ohne Just More Parts";
        Optional<String> version = Services.PLATFORM.modVersion(ShareVectors.JMP);
        if (version.isPresent()) {
            String exported = ShareService.encode(DroneData.of(ShareVectors.jmpBuild()), EnumSet.of(ShareLayer.PARTS), true, Optional.empty());
            if (!ShareVectors.P2_JMP.equals(exported)) {
                errors.add("PW3-Export der JMP-Drohne " + exported + " statt " + ShareVectors.P2_JMP);
            }
            jmp = "Just More Parts " + version.get() + ", PW3-Export der JMP-Drohne " + exported;
        }
        summary = "Befehle " + COMMANDS + " angemeldet, info/convert am Server, PW1 → " + pw1.code().orElse("–") + ", Tabellen "
                + ShareTables.current().installedTables().keySet() + ", " + jmp;
        return errors.isEmpty() ? null : String.join("; ", errors);
    }

    public static String summary() {
        return summary;
    }

    private static int execute(MinecraftServer server, String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
        } catch (Exception e) {
            Constants.LOG.error("[Server-Rauchtest] Sharecode: {} warf", command, e);
            return -1;
        }
    }
}
