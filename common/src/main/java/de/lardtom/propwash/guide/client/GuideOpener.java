package de.lardtom.propwash.guide.client;

import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.PartRef;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.screen.SceneIndexScreen;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.client.training.TrainingScreen;
import de.lardtom.propwash.guide.scene.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class GuideOpener {
    private GuideOpener() {
    }

    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.toString();
    }

    public static boolean isPart(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(CoreComponents.PART.get());
    }

    public static List<Scene> scenesFor(ItemStack stack) {
        String id = itemId(stack);
        if (id.isEmpty()) {
            return List.of();
        }
        PartRef part = stack.get(CoreComponents.PART.get());
        return GuideScenes.forItem(id, part != null, part == null ? null : part.category().kind());
    }

    public static void openScene(Scene scene, @Nullable Screen parent) {
        Minecraft.getInstance().gui.setScreen(new SceneScreen(scene, parent));
    }

    public static void openIndex(@Nullable Screen parent, ItemStack filter) {
        Minecraft.getInstance().gui.setScreen(new SceneIndexScreen(parent, filter));
    }

    public static void openTraining(@Nullable Screen parent) {
        Minecraft.getInstance().gui.setScreen(new TrainingScreen(parent));
    }

    public static void openFor(ItemStack stack, @Nullable Screen parent) {
        List<Scene> scenes = scenesFor(stack);
        if (scenes.size() == 1) {
            openScene(scenes.getFirst(), parent);
        } else {
            openIndex(parent, stack);
        }
    }
}
