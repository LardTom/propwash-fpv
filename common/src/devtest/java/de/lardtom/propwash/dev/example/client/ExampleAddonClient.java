package de.lardtom.propwash.dev.example.client;

import de.lardtom.propwash.api.client.FlightSchoolScenes;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.dev.example.ExampleAddon;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExampleAddonClient {
    public static final Identifier WORKBENCH_BUTTON = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "import_example");
    public static final Identifier SIZE_BUTTON = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "size_example");
    public static final Identifier SIZE_ICON = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "workbench/size");
    public static final int SIZE_ORDER = 990;
    private static final Logger LOG = LoggerFactory.getLogger(ExampleAddon.NAMESPACE);

    private static boolean initialized;
    private static boolean tabRegistered;
    private static @Nullable WorkbenchContext lastContext;

    private ExampleAddonClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashClientApi.registerWorkbenchExtension(new ImportButton());
        PropwashClientApi.registerWorkbenchExtension(new SizeButton());
        try {
            PropwashClientApi.registerFlightSchoolScene(ExampleScene::create, ExampleAddon::showcase);
        } catch (IllegalArgumentException e) {
            LOG.error("Flugschul-Szene des Beispiel-Addons nicht angemeldet (fehlen Beispiel-Teile?)", e);
        }
        PropwashClientApi.linkFlightSchoolParts(ExampleScene.ID, ExampleAddon.NAMESPACE, PartKind.FRAME);
        PropwashClientApi.linkFlightSchoolItems(FlightSchoolScenes.REPAIR, ExampleAddon.MOTOR_2806.toString(), ExampleAddon.MOTOR_4214.toString());
        if (ExampleAddon.showcase()) {
            registerTab();
        }
        ExampleAccessoriesClient.init();
    }

    public static synchronized void registerTab() {
        if (tabRegistered) {
            return;
        }
        tabRegistered = true;
        PropwashClientApi.registerConfigTab(() -> ExampleAddon.showcase() ? new ExampleConfigTab() : null);
    }

    public static @Nullable WorkbenchContext lastContext() {
        return lastContext;
    }

    private static final class ImportButton implements WorkbenchExtension {
        @Override
        public Identifier id() {
            return WORKBENCH_BUTTON;
        }

        @Override
        public void init(WorkbenchContext context) {
            if (!ExampleAddon.showcase()) {
                return;
            }
            lastContext = context;
            context.addButton(Component.translatable("propwash_example.workbench.button"),
                    Component.translatable("propwash_example.workbench.button.short"),
                    Component.translatable("propwash_example.workbench.tooltip"),
                    () -> context.openChild(parent -> new ExampleImportScreen(parent, context)));
        }
    }

    public static boolean sizeAsText(WorkbenchContext context) {
        Component label = Component.translatable("propwash_example.workbench.size");
        Component importLabel = Component.translatable("propwash_example.workbench.button.short");
        return context.headerSpace() >= context.buttonWidth(label) + context.buttonWidth(importLabel) + 2 * WorkbenchContext.BUTTON_GAP;
    }

    private static final class SizeButton implements WorkbenchExtension {
        @Override
        public Identifier id() {
            return SIZE_BUTTON;
        }

        @Override
        public int order() {
            return SIZE_ORDER;
        }

        @Override
        public void init(WorkbenchContext context) {
            if (!ExampleAddon.showcase()) {
                return;
            }
            Component tooltip = Component.translatable("propwash_example.workbench.size.tooltip");
            Runnable open = () -> context.openChild(ExampleSizeScreen::new);
            if (sizeAsText(context)) {
                context.addButton(Component.translatable("propwash_example.workbench.size"), tooltip, open);
            } else {
                context.addIconButton(SIZE_ICON, tooltip, open);
            }
        }
    }
}
