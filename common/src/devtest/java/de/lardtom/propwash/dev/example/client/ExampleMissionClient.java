package de.lardtom.propwash.dev.example.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.ServiceExtension;
import de.lardtom.propwash.dev.example.ExampleMission;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

public final class ExampleMissionClient {
    public static final Identifier BUTTON = ExampleMission.MISSION_ID;
    public static final AtomicInteger OPENED = new AtomicInteger();
    private static boolean initialized;

    private ExampleMissionClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashClientApi.registerServiceExtension(new Button());
    }

    public static final class Button implements ServiceExtension {
        @Override
        public Identifier id() {
            return BUTTON;
        }

        @Override
        public Component label() {
            return Component.translatable("propwash_example.mission.button");
        }

        @Override
        public boolean visible(Entity drone) {
            return ExampleMission.active();
        }

        @Override
        public void open(Entity drone, @Nullable Screen parent) {
            OPENED.incrementAndGet();
            Minecraft.getInstance().gui.setScreen(new MissionScreen(parent, drone));
        }
    }

    public static final class MissionScreen extends Screen implements ThemedScreen {
        private static final int MAX_WIDTH = 260;
        private static final int MAX_HEIGHT = 170;
        private static final int PAD = 8;
        private static final int SHOWN_WAYPOINTS = 4;

        private final @Nullable Screen parent;
        private final Entity drone;
        private int left;
        private int top;
        private int right;
        private int bottom;

        public MissionScreen(@Nullable Screen parent, Entity drone) {
            super(Component.translatable("propwash_example.mission.title"));
            this.parent = parent;
            this.drone = drone;
        }

        public @Nullable Screen parent() {
            return this.parent;
        }

        public ExampleMission.Mission shown() {
            return PropwashApi.droneData().get(this.drone, ExampleMission.mission());
        }

        @Override
        protected void init() {
            int w = Math.min(this.width - 16, MAX_WIDTH);
            int h = Math.min(this.height - 12, MAX_HEIGHT);
            this.left = (this.width - w) / 2;
            this.right = this.left + w;
            this.top = (this.height - h) / 2;
            this.bottom = this.top + h;
            Component back = CommonComponents.GUI_BACK;
            int backWidth = Math.max(60, this.font.width(back) + 16);
            int buttonY = this.bottom - Metrics.FOOTER_HEIGHT + (Metrics.FOOTER_HEIGHT - Metrics.CONTROL_HEIGHT) / 2;
            this.addRenderableWidget(new FlatButton(this.right - PAD - backWidth, buttonY, backWidth, Metrics.CONTROL_HEIGHT, back,
                    FlatButton.Style.NORMAL, this::onClose));
        }

        @Override
        public void onClose() {
            this.minecraft.gui.setScreen(this.parent);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            ThemeDraw.backdrop(g, this.width, this.height);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            ThemeDraw.window(g, this.left, this.top, this.right, this.bottom);
            int titleEnd = ThemeDraw.brandedHeader(g, this.font, this.left, this.top, this.right, Metrics.HEADER_HEIGHT, this.title,
                    this.right - PAD);
            ThemeDraw.headerSubtitle(g, this.font, titleEnd, this.top, Metrics.HEADER_HEIGHT, this.drone.getDisplayName(), this.right - PAD);
            ExampleMission.Mission mission = this.shown();
            int x = this.left + PAD;
            int width = this.right - this.left - 2 * PAD;
            int y = this.top + Metrics.HEADER_HEIGHT + PAD;
            if (mission.waypoints().isEmpty()) {
                ThemeText.fitted(g, this.font, Component.translatable("propwash_example.mission.empty"), x, y, width, Palette.TEXT_MUTED);
            } else {
                ThemeText.fitted(g, this.font, Component.translatable("propwash_example.mission.name", mission.name()), x, y, width, Palette.TEXT);
                y += Metrics.LINE_HEIGHT;
                ThemeText.fitted(g, this.font, Component.translatable("propwash_example.mission.flights", mission.flights(),
                        mission.waypoints().size()), x, y, width, Palette.TEXT);
                for (int i = 0; i < Math.min(SHOWN_WAYPOINTS, mission.waypoints().size()); i++) {
                    y += Metrics.LINE_HEIGHT;
                    BlockPos point = mission.waypoints().get(i);
                    ThemeText.fitted(g, this.font, Component.translatable("propwash_example.mission.waypoint", i + 1, point.getX(), point.getY(),
                            point.getZ()), x + PAD, y, width - PAD, Palette.TEXT_MUTED);
                }
            }
            int footerTop = this.bottom - Metrics.FOOTER_HEIGHT;
            ThemeText.fitted(g, this.font, Component.translatable("propwash_example.mission.server_only"), x, footerTop - Metrics.LINE_HEIGHT - 2,
                    width, Palette.TEXT_FAINT);
            ThemeDraw.footerBar(g, this.left, footerTop, this.right, this.bottom);
            super.extractRenderState(g, mouseX, mouseY, a);
            ThemeDraw.windowBorder(g, this.left, this.top, this.right, this.bottom);
        }
    }
}
