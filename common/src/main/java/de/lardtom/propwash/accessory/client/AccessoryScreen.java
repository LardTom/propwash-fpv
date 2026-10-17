package de.lardtom.propwash.accessory.client;

import de.lardtom.propwash.accessory.AccessoryBehaviors;
import de.lardtom.propwash.accessory.AccessoryMenu;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.client.ThemedPanel;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class AccessoryScreen extends AbstractContainerScreen<AccessoryMenu> implements ThemedScreen {
    private static final int HEADER_HEIGHT = 14;
    private static final int LABEL_OFFSET = 11;

    public AccessoryScreen(AccessoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AccessoryMenu.WIDTH, AccessoryMenu.height(menu.rows().size()));
        this.inventoryLabelX = AccessoryMenu.inventoryX();
        this.inventoryLabelY = menu.inventoryTop() - 10;
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
        ThemedPanel.panel(graphics, x, y, this.imageWidth, this.imageHeight);
        List<AccessoryMount> rows = this.menu.rows();
        for (int row = 0; row < rows.size(); row++) {
            AccessoryMount mount = rows.get(row);
            int rowY = AccessoryMenu.FIRST_ROW_Y + row * AccessoryMenu.ROW_HEIGHT;
            graphics.fill(x + AccessoryMenu.LEFT - 1, y + rowY - LABEL_OFFSET - 2, x + this.imageWidth - AccessoryMenu.LEFT + 1, y + rowY + 18,
                    ThemedPanel.DARK);
            for (int slot = 0; slot < this.menu.size(mount); slot++) {
                ThemedPanel.slot(graphics, x + AccessoryMenu.SLOT_X + slot * 18, y + rowY);
            }
            AccessoryType type = this.menu.accessory(mount);
            if (type != null) {
                graphics.item(ContentItems.stack(type), x + AccessoryMenu.LEFT + 4, y + rowY);
            }
        }
        ThemedPanel.inventory(graphics, x, y, AccessoryMenu.inventoryX(), this.menu.inventoryTop(), this.menu.inventoryTop() + 58);
        ThemeDraw.brandedHeader(graphics, this.font, x + 2, y + 2, x + this.imageWidth - 2, HEADER_HEIGHT, this.title, x + this.imageWidth - 8);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, ThemedPanel.MUTED, false);
        List<AccessoryMount> rows = this.menu.rows();
        QuadcopterEntity drone = this.menu.drone();
        for (int row = 0; row < rows.size(); row++) {
            AccessoryMount mount = rows.get(row);
            int labelY = AccessoryMenu.FIRST_ROW_Y + row * AccessoryMenu.ROW_HEIGHT - LABEL_OFFSET;
            AccessoryType type = this.menu.accessory(mount);
            Component label = type == null ? Component.empty() : ContentItems.stack(type).getHoverName();
            int right = this.imageWidth - AccessoryMenu.LEFT - 2;
            double grams = 0.0;
            if (drone != null && type != null) {
                List<ItemStack> contents = drone.accessoryContents().at(mount);
                grams = AccessoryBehaviors.INSTANCE.payloadGrams(type, contents);
            }
            Component load = Component.translatable("propwash.accessory.menu.load", Math.round(grams));
            int loadWidth = this.font.width(load);
            ThemeText.fitted(graphics, this.font, label, AccessoryMenu.LEFT + 2, labelY, right - loadWidth - 6 - AccessoryMenu.LEFT, ThemedPanel.TEXT);
            graphics.text(this.font, load, right - loadWidth, labelY, ThemedPanel.MUTED, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int rx = mouseX - this.leftPos;
        int ry = mouseY - this.topPos;
        List<AccessoryMount> rows = this.menu.rows();
        for (int row = 0; row < rows.size(); row++) {
            int rowY = AccessoryMenu.FIRST_ROW_Y + row * AccessoryMenu.ROW_HEIGHT;
            if (rx >= AccessoryMenu.LEFT && rx < AccessoryMenu.SLOT_X - 2 && ry >= rowY && ry < rowY + 18) {
                AccessoryMount mount = rows.get(row);
                ThemeText.tooltipLines(graphics, this.font, List.of(Component.translatable("propwash.content.workbench.slot.accessory_" + mount.key()),
                        Component.translatable("propwash.accessory.menu.hint")), mouseX, mouseY);
            }
        }
    }
}
