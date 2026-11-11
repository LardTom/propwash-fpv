package de.lardtom.propwash.share.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.apiimpl.client.WorkbenchExtensions;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.join.SafeText;
import de.lardtom.propwash.share.AddonDirectory;
import de.lardtom.propwash.share.MissingAddons;
import de.lardtom.propwash.share.client.chat.ShareChat;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.net.URI;

public final class ShareClient {
    private static boolean initialized;

    private ShareClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        WorkbenchExtensions.register(new ShareWorkbenchExtension());
        ConfigTabs.register(ShareConfigTab::new);
        try {
            GuideScenes.register(ShareScene::create);
        } catch (RuntimeException e) {
            Constants.LOG.error("Flugschul-Szene „Builds teilen“ nicht angemeldet", e);
        }
        ShareChat.init();
    }

    public static boolean openLink(@Nullable Screen parent, String url) {
        String safe = SafeText.url(url);
        if (safe.isEmpty() || !(safe.startsWith(MissingAddons.MODRINTH)
                || safe.startsWith(MissingAddons.CURSEFORGE)
                || safe.equals(AddonDirectory.ADDONS_PAGE))) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = parent != null ? parent : minecraft.gui.screen();
        ConfirmLinkScreen.confirmLinkNow(screen, URI.create(safe), false);
        return true;
    }
}
