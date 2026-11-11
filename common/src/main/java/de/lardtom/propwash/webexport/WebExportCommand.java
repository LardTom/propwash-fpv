package de.lardtom.propwash.webexport;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

public final class WebExportCommand {
    public static final String LOG_PREFIX = "Propwash-Web-Export: ";
    private static final String KEY = "propwash.webexport.command.";
    private static final String SELECTION = "namespace";

    private WebExportCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("propwash").then(Commands.literal("webexport")
                .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                .executes(ctx -> run(ctx.getSource(), WebExport.ALL))
                .then(Commands.argument(SELECTION, StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(choices(), builder))
                        .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, SELECTION))))));
    }

    static List<String> choices() {
        List<String> out = new ArrayList<>();
        out.add(WebExport.ALL);
        out.addAll(WebExport.namespaces());
        return out;
    }

    private static int run(CommandSourceStack source, String selection) {
        SortedSet<String> available = WebExport.namespaces();
        if (!WebExport.ALL.equals(selection) && !available.contains(selection)) {
            source.sendFailure(Component.translatable(KEY + "unknown", selection, String.join(", ", available)));
            return 0;
        }
        Path target = WebExport.target(Services.PLATFORM.getGameDir()).toAbsolutePath().normalize();
        source.sendSuccess(() -> Component.translatable(KEY + "start", selection), false);
        long start = System.nanoTime();
        WebExport.Result result;
        try {
            result = WebExport.run(target, selection, ModResources.platform());
        } catch (RuntimeException e) {
            Constants.LOG.warn(LOG_PREFIX + "FAIL Ausnahme im Web-Export ({})", selection, e);
            source.sendFailure(Component.translatable(KEY + "crash", String.valueOf(e.getMessage())));
            return 0;
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        if (result.pass()) {
            Constants.LOG.info(LOG_PREFIX + "{} nach {} in {} ms", result.summary(), target, millis);
            source.sendSuccess(() -> Component.translatable(KEY + "pass", result.parts(), String.join(", ", result.namespaces()), result.warnings(),
                    target.toString()).withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        Constants.LOG.warn(LOG_PREFIX + "{} nach {} in {} ms", result.summary(), target, millis);
        source.sendFailure(Component.translatable(KEY + "fail", result.errors(), target.toString()));
        return 0;
    }
}
