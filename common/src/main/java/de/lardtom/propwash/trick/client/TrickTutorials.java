package de.lardtom.propwash.trick.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.scene.SceneFraming;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.trick.tutorial.TrickTutorial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class TrickTutorials {
    private static final Map<TrickType, CompletableFuture<Prepared>> CACHE = new EnumMap<>(TrickType.class);

    private TrickTutorials() {
    }

    public record Prepared(TrickTutorial tutorial, SceneFraming framing) {
    }

    public static synchronized CompletableFuture<Prepared> prepare(TrickType type) {
        return CACHE.computeIfAbsent(type, key -> CompletableFuture.supplyAsync(() -> build(key), Util.backgroundExecutor()));
    }

    public static @Nullable Prepared ready(TrickType type) {
        CompletableFuture<Prepared> future = prepare(type);
        if (!future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }
        return future.getNow(null);
    }

    public static Prepared now(TrickType type) {
        CompletableFuture<Prepared> future = prepare(type);
        Prepared prepared = future.isDone() && !future.isCompletedExceptionally() ? future.getNow(null) : null;
        if (prepared != null) {
            return prepared;
        }
        return build(type);
    }

    private static Prepared build(TrickType type) {
        try {
            TrickTutorial tutorial = TrickTutorial.create(type);
            return new Prepared(tutorial, SceneFraming.of(tutorial.scene()));
        } catch (RuntimeException e) {
            Constants.LOG.error("Trick-Tutorial {} konnte nicht erzeugt werden", type, e);
            throw e;
        }
    }

    public static SceneScreen.Options options(TrickTutorial tutorial) {
        return new SceneScreen.Options(Component.translatable(tutorial.titleKey()), Component.translatable("propwash.trick.tab.tutorial_title"),
                tutorial::stepTitleKey, tutorial::stepTextKey, TrickTutorial.DRONE);
    }

    public static SceneScreen open(TrickType type, @Nullable Screen parent) {
        Prepared prepared = now(type);
        SceneScreen screen = new SceneScreen(prepared.tutorial().scene(), parent, options(prepared.tutorial()), prepared.framing());
        Minecraft.getInstance().gui.setScreen(screen);
        return screen;
    }
}
