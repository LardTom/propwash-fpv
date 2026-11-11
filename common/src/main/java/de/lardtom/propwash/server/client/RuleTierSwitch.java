package de.lardtom.propwash.server.client;

import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import de.lardtom.propwash.ui.client.widget.PanelWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class RuleTierSwitch extends PanelWidget {
    public static final String KEY = "propwash.rules.tier.";
    private static final int PAD = 4;

    private final Supplier<RuleTier> tier;
    private final Consumer<RuleTier> setter;
    private final boolean compact;

    public RuleTierSwitch(int x, int y, int width, int height, Component message, boolean active, boolean compact, Supplier<RuleTier> tier,
                          Consumer<RuleTier> setter) {
        super(x, y, width, height, message);
        this.active = active;
        this.compact = compact;
        this.tier = tier;
        this.setter = setter;
    }

    public static int width(Font font, boolean compact) {
        if (compact) {
            return Metrics.CHECK_SIZE;
        }
        int widest = 0;
        for (RuleTier tier : RuleTier.values()) {
            widest = Math.max(widest, font.width(name(tier)));
        }
        return widest + 2 * PAD;
    }

    public static int column(Font font, boolean compact) {
        return width(font, compact) + Metrics.GAP;
    }

    public static Component name(RuleTier tier) {
        return Component.translatable(tier.translationKey());
    }

    public static Component shortName(RuleTier tier) {
        return Component.translatable(tier.translationKey() + ".short");
    }

    public boolean compact() {
        return this.compact;
    }

    public static int color(RuleTier tier) {
        return switch (tier) {
            case FIXED -> Palette.TEXT_FAINT;
            case ZONES -> Palette.INFO;
            case PLAYERS -> Palette.SUCCESS;
        };
    }

    public static RuleTier next(RuleTier tier, int direction) {
        RuleTier[] all = RuleTier.values();
        int n = all.length;
        return all[((tier.ordinal() + direction) % n + n) % n];
    }

    public static Component tooltip(Component rule, RuleTier current, boolean editable) {
        MutableComponent tip = Component.empty().append(ThemeText.accent(Component.translatable(KEY + "title", rule)));
        for (RuleTier tier : RuleTier.values()) {
            Component line = Component.translatable(KEY + tier.key() + ".description");
            tip.append("\n").append(tier == current ? ThemeText.colored(line, color(tier) == Palette.TEXT_FAINT ? Palette.TEXT : color(tier))
                    : ThemeText.muted(line));
        }
        tip.append("\n").append(ThemeText.muted(Component.translatable(KEY + "kept")));
        tip.append("\n").append(ThemeText.muted(Component.translatable(editable ? KEY + "hint" : KEY + "read_only")));
        return tip;
    }

    public RuleTier tier() {
        return this.tier.get();
    }

    public void set(RuleTier tier) {
        if (this.active) {
            this.setter.accept(tier);
        }
    }

    public void cycle(int direction) {
        this.set(next(this.tier(), direction));
    }

    @Override
    protected boolean acceptsRightClick() {
        return true;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (isRightClick(event)) {
            this.set(RuleTier.DEFAULT);
            return;
        }
        this.cycle(1);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.active) {
            return false;
        }
        if (event.isLeft()) {
            this.cycle(-1);
            return true;
        }
        if (event.isRight() || event.isSelection()) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.cycle(1);
            return true;
        }
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        WidgetState state = this.state();
        RuleTier tier = this.tier();
        ThemeDraw.button(g, this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, ThemeDraw.ButtonStyle.NORMAL, state, false);
        int color = color(tier);
        int textY = Metrics.textY(this.getY(), this.height) + (state.pressed() ? 1 : 0);
        int inset = this.compact ? 1 : 2;
        ThemeText.line(g, this.font(), this.compact ? shortName(tier) : name(tier), this.getX() + inset, this.getX() + this.width - inset, textY, color,
                ThemeText.Align.CENTER, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.empty().append(this.getMessage()).append(": ").append(name(this.tier())));
    }
}
