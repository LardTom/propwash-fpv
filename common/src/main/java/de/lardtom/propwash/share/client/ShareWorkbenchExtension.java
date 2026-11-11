package de.lardtom.propwash.share.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.content.client.WorkbenchHost;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ShareWorkbenchExtension implements WorkbenchExtension {
    public static final Identifier ID = Constants.id("share_code");
    public static final Identifier ICON = Constants.id("share/share_code");
    public static final String LABEL_KEY = "propwash.share.workbench.code";
    public static final String SHORT_KEY = LABEL_KEY + ".short";
    public static final String TOOLTIP_KEY = LABEL_KEY + ".tooltip";
    public static final String ICON_TOOLTIP_KEY = LABEL_KEY + ".icon_tooltip";
    public static final List<String> LABEL_KEYS = List.of(LABEL_KEY, SHORT_KEY, TOOLTIP_KEY, ICON_TOOLTIP_KEY);

    private static @Nullable WorkbenchContext lastContext;

    public static @Nullable WorkbenchContext lastContext() {
        return lastContext;
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public void init(WorkbenchContext context) {
        lastContext = context;
        Runnable open = () -> open(context);
        if (context instanceof WorkbenchHost host) {
            host.addTextOrIconButton(Component.translatable(LABEL_KEY), Component.translatable(SHORT_KEY), Component.translatable(TOOLTIP_KEY), ICON,
                    Component.translatable(ICON_TOOLTIP_KEY), open);
        } else if (asText(context)) {
            context.addButton(Component.translatable(LABEL_KEY), Component.translatable(SHORT_KEY), Component.translatable(TOOLTIP_KEY), open);
        } else {
            context.addIconButton(ICON, Component.translatable(ICON_TOOLTIP_KEY), open);
        }
    }

    public static boolean asText(WorkbenchContext context) {
        return context.headerSpace() >= context.buttonWidth(Component.translatable(SHORT_KEY)) + WorkbenchContext.BUTTON_GAP;
    }

    public static void open(WorkbenchContext context) {
        ShareScreen.open(context, context.result().isPresent() ? ShareScreen.Mode.EXPORT : ShareScreen.Mode.IMPORT, false);
    }
}
