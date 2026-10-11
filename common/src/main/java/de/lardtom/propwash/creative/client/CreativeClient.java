package de.lardtom.propwash.creative.client;

import de.lardtom.propwash.creative.CreativeScene;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;

public final class CreativeClient {
    private static boolean initialized;

    private CreativeClient() {
    }

    public static boolean sceneVisible() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null || minecraft.player.isCreative();
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ConfigTabs.register(CreativeEditorTab::create);
        GuideScenes.register(CreativeScene::create, CreativeClient::sceneVisible);
    }
}
