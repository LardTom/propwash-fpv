package de.lardtom.propwash.content.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BatteryChargerBlockEntity;
import de.lardtom.propwash.content.BatteryChargerMenu;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class BatteryChargerScreen extends AbstractContainerScreen<BatteryChargerMenu> implements ThemedScreen {
    public static final Identifier TEXTURE = Constants.id("textures/gui/battery_charger.png");
    public static final int WIDTH = 176;
    public static final int HEIGHT = 207;
    private static final int HEADER_HEIGHT = 14;
    private static final String KEY = "propwash.content.charger.";
    private static final int BAR_TOP = 52;
    private static final int BAR_BOTTOM = 74;
    private static final int BAY_BOTTOM = 88;
    private static final int LABEL_Y = 78;
    private static final int LAMP_Y = 22;
    private static final int BAY_LEFT = 10;
    private static final int BAY_RIGHT = 26;
    private static final int STRIP_X0 = 7;
    private static final int STRIP_Y0 = 90;
    private static final int STRIP_X1 = WIDTH - 7;
    private static final int STRIP_Y1 = 111;
    private static final int STRIP_ICON_X = 10;
    private static final int STRIP_ICON_Y = 92;
    private static final int STRIP_TEXT_X = 30;
    private static final ItemStack REDSTONE = new ItemStack(Items.REDSTONE);
    private static final int STRIP_LINE_1 = 92;
    private static final int STRIP_LINE_2 = 101;

    public BatteryChargerScreen(BatteryChargerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.inventoryLabelY = 114;
    }

    @Override
    protected void init() {
        super.init();
        ThemedPanel.forget();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        if (!ThemedPanel.blit(graphics, TEXTURE, x, y, WIDTH, HEIGHT)) {
            ThemedPanel.panel(graphics, x, y, WIDTH, HEIGHT);
            for (int slot = 0; slot < BatteryChargerMenu.SLOTS; slot++) {
                int sx = BatteryChargerMenu.SLOT_X[slot];
                graphics.fill(x + sx - BAY_LEFT, y + 19, x + sx + BAY_RIGHT, y + BAY_BOTTOM, ThemedPanel.DARK);
                ThemedPanel.slot(graphics, x + sx, y + BatteryChargerMenu.SLOT_Y);
                graphics.outline(x + sx + 1, y + BAR_TOP - 1, 14, BAR_BOTTOM - BAR_TOP + 2, ThemedPanel.SLOT_EDGE);
            }
            graphics.fill(x + STRIP_X0, y + STRIP_Y0, x + STRIP_X1, y + STRIP_Y1, ThemedPanel.DARK);
            ThemedPanel.inventory(graphics, x, y, BatteryChargerMenu.INVENTORY_X, BatteryChargerMenu.INVENTORY_Y,
                    BatteryChargerMenu.INVENTORY_Y + 58);
        }
        ThemeDraw.brandedHeader(graphics, this.font, x + 2, y + 2, x + WIDTH - 2, HEADER_HEIGHT, this.title, x + WIDTH - 8);
        for (int slot = 0; slot < BatteryChargerMenu.SLOTS; slot++) {
            this.drawChargeGauge(graphics, x + BatteryChargerMenu.SLOT_X[slot], y, this.menu.battery(slot));
            this.drawBayLamp(graphics, x + BatteryChargerMenu.SLOT_X[slot], y, this.menu.battery(slot));
        }
        graphics.item(REDSTONE, x + STRIP_ICON_X, y + STRIP_ICON_Y);
        if (!this.menu.powered()) {
            graphics.fill(x + STRIP_ICON_X, y + STRIP_ICON_Y, x + STRIP_ICON_X + 16, y + STRIP_ICON_Y + 16, Palette.withAlpha(ThemedPanel.DARK, 0xB0));
        }
    }

    public static int lampColor(ItemStack battery, long millis) {
        if (!Batteries.isBattery(battery)) {
            return 0;
        }
        if (Batteries.isFull(Batteries.charge(battery))) {
            return Palette.SUCCESS;
        }
        float pulse = 0.5F + 0.5F * (float) Math.sin(millis / 1000.0 * Math.PI * 2.0);
        return Palette.mix(Palette.ACCENT_DIM, Palette.ACCENT, pulse);
    }

    private void drawBayLamp(GuiGraphicsExtractor g, int sx, int y, ItemStack battery) {
        int color = lampColor(battery, Util.getMillis());
        if (color != 0) {
            g.fill(sx + 4, y + LAMP_Y, sx + 12, y + LAMP_Y + 2, color);
        }
    }

    private void drawChargeGauge(GuiGraphicsExtractor g, int sx, int y, ItemStack battery) {
        if (!Batteries.isBattery(battery)) {
            return;
        }
        float charge = Batteries.charge(battery);
        int height = BAR_BOTTOM - BAR_TOP;
        int filled = Math.round(height * charge);
        if (filled > 0) {
            g.fill(sx + 2, y + BAR_BOTTOM - filled, sx + 14, y + BAR_BOTTOM, 0xFF000000 | Batteries.barColor(charge));
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, ThemedPanel.MUTED, false);
        boolean powered = this.menu.powered();
        int textWidth = STRIP_X1 - 2 - STRIP_TEXT_X;
        ThemeText.fitted(graphics, this.font, Component.translatable(KEY + (powered ? "boost_on" : "boost_off")), STRIP_TEXT_X, STRIP_LINE_1, textWidth, ThemedPanel.TEXT);
        ThemeText.fitted(graphics, this.font, Component.translatable(KEY + (powered ? "boost_on_hint" : "boost_off_hint")), STRIP_TEXT_X, STRIP_LINE_2, textWidth, ThemedPanel.MUTED);
        for (int slot = 0; slot < BatteryChargerMenu.SLOTS; slot++) {
            ItemStack battery = this.menu.battery(slot);
            boolean present = Batteries.isBattery(battery);
            String label = present
                    ? Component.translatable("propwash.content.tooltip.unit.percent", Batteries.percent(Batteries.charge(battery))).getString()
                    : "—";
            int center = BatteryChargerMenu.SLOT_X[slot] + 8;
            graphics.text(this.font, label, center - this.font.width(label) / 2, LABEL_Y, present ? ThemedPanel.TEXT : ThemedPanel.MUTED, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        List<Component> tooltip = this.stationTooltip(mouseX - this.leftPos, mouseY - this.topPos);
        ThemeText.tooltipLines(graphics, this.font, tooltip, mouseX, mouseY);
    }

    private List<Component> stationTooltip(int rx, int ry) {
        List<Component> lines = new ArrayList<>();
        if (rx >= STRIP_X0 && rx < STRIP_X1 && ry >= STRIP_Y0 && ry < STRIP_Y1) {
            lines.add(Component.translatable(KEY + (this.menu.powered() ? "redstone_on" : "redstone_off")));
            return lines;
        }
        for (int slot = 0; slot < BatteryChargerMenu.SLOTS; slot++) {
            int sx = BatteryChargerMenu.SLOT_X[slot];
            boolean inBay = rx >= sx - BAY_LEFT && rx < sx + BAY_RIGHT && (ry >= 48 && ry < BAY_BOTTOM || ry >= LAMP_Y - 2 && ry < LAMP_Y + 4);
            if (!inBay) {
                continue;
            }
            ItemStack battery = this.menu.battery(slot);
            if (!Batteries.isBattery(battery)) {
                lines.add(Component.translatable(KEY + "empty"));
            } else if (Batteries.isFull(Batteries.charge(battery))) {
                lines.add(Component.translatable(KEY + "full"));
            } else {
                int seconds = this.menu.remainingSeconds(slot);
                lines.add(Component.translatable(KEY + "charging", seconds == BatteryChargerBlockEntity.NO_TIME ? "?" : seconds));
            }
        }
        return lines;
    }
}
