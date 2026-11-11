package de.lardtom.propwash.share.client.chat;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.share.client.ShareConfigTab;
import de.lardtom.propwash.share.client.ShareImportState;
import de.lardtom.propwash.share.client.ShareSettings;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ShareChat {
    public static final Identifier OPEN = Constants.id("share_chat/open");
    public static final Identifier COPY = Constants.id("share_chat/copy");
    public static final int COLOR = Palette.ACCENT;
    public static final int INVALID_COLOR = Palette.ERROR;
    public static final int CACHE_SIZE = 32;
    public static final int DECODES_PER_SECOND = 8;
    private static final long SECOND_NANOS = 1_000_000_000L;
    private static final ChatCodeCache<ChatPreview> CACHE = new ChatCodeCache<>(CACHE_SIZE, DECODES_PER_SECOND, SECOND_NANOS, ChatPreview::decode,
            System::nanoTime);
    private static @Nullable Object session;
    private static @Nullable String language;
    private static boolean initialized;
    private static boolean warned;
    private static int decorated;
    private static int tooltips;

    private ShareChat() {
    }

    public record Target(String code, boolean copy) {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ShareChatConfig.CONFIG.load();
    }

    public static boolean enabled() {
        return ShareChatConfig.get().clickableCodes;
    }

    public static void setEnabled(boolean value) {
        if (ShareChatConfig.get().clickableCodes == value) {
            return;
        }
        ShareChatConfig.get().clickableCodes = value;
        ShareChatConfig.CONFIG.save();
        refreshChat();
    }

    public static void refreshChat() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gui != null) {
            minecraft.gui.hud.getChat().rescaleChat();
        }
    }

    public static FormattedText display(FormattedText message) {
        if (message == null || !enabled()) {
            return message;
        }
        try {
            ChatCodeText.Result result = ChatCodeText.apply(message, colors(), ShareChat::decoration);
            if (!result.codes().isEmpty()) {
                decorated++;
            }
            return result.text();
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                Constants.LOG.error("Sharecodes im Chat: Zeile nicht erweitert", e);
            }
            return message;
        }
    }

    private static boolean colors() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.options == null || minecraft.options.chatColors().get();
    }

    static ChatCodeText.@Nullable Decoration decoration(ChatCodeFinder.Found found, boolean copy) {
        ChatPreview preview = preview(found.code(), true);
        if (preview == null) {
            return null;
        }
        if (!preview.valid()) {
            return new ChatCodeText.Decoration(INVALID_COLOR, null, new HoverEvent.ShowText(invalidText()));
        }
        ClickEvent click = new ClickEvent.Custom(copy ? COPY : OPEN, Optional.of(StringTag.valueOf(found.code())));
        return new ChatCodeText.Decoration(COLOR, click, new HoverEvent.ShowText(preview.hoverText(copy)));
    }

    public static Component invalidText() {
        return ThemeText.colored(Component.translatable(ChatPreview.KEY + "invalid"), Palette.ERROR);
    }

    public static @Nullable ChatPreview preview(String code, boolean limited) {
        Object current = currentSession();
        String selected = ShareImportState.language();
        if (current != session || !selected.equals(language)) {
            session = current;
            language = selected;
            CACHE.clear();
        }
        return CACHE.get(code, limited);
    }

    private static @Nullable Object currentSession() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft == null ? null : minecraft.getConnection();
        return connection;
    }

    public static Optional<Target> target(@Nullable Style style) {
        if (style == null || !(style.getClickEvent() instanceof ClickEvent.Custom custom)) {
            return Optional.empty();
        }
        boolean copy = custom.id().equals(COPY);
        if (!copy && !custom.id().equals(OPEN)) {
            return Optional.empty();
        }
        Optional<String> code = custom.payload().flatMap(Tag::asString);
        return code.map(text -> new Target(text, copy));
    }

    public static boolean click(@Nullable Style style, boolean shift, @Nullable Screen screen) {
        Optional<Target> target = target(style);
        if (target.isEmpty()) {
            return false;
        }
        String code = target.get().code();
        List<ChatCodeFinder.Found> found = ChatCodeFinder.find(code);
        if (found.size() != 1 || !found.getFirst().code().equals(code)) {
            return true;
        }
        if (shift || target.get().copy()) {
            copy(code);
        } else {
            open(code, screen);
        }
        return true;
    }

    public static void copy(String code) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.keyboardHandler.setClipboard(code);
        minecraft.gui.hud.setOverlayMessage(ThemeText.colored(Component.translatable(ChatPreview.KEY + "copied", code.length()), Palette.SUCCESS), false);
    }

    public static boolean open(String code, @Nullable Screen parent) {
        ShareSettings.lastInput(code);
        boolean opened;
        try {
            opened = PropwashClientApi.openConfigTab(ShareConfigTab.ID, parent);
        } catch (RuntimeException e) {
            Constants.LOG.error("Sharecodes im Chat: Import nicht geöffnet", e);
            opened = false;
        }
        if (!opened) {
            Minecraft.getInstance().gui.hud.setOverlayMessage(ThemeText.colored(Component.translatable(ChatPreview.KEY + "no_open"), Palette.ERROR), false);
            return false;
        }
        ShareConfigTab tab = ShareConfigTab.active();
        if (tab != null && !code.equals(tab.state().text())) {
            tab.setInput(code);
        }
        return true;
    }

    public static @Nullable List<ClientTooltipComponent> tooltip(@Nullable Style style, Font font, int guiWidth) {
        Optional<Target> target = target(style);
        if (target.isEmpty()) {
            return null;
        }
        ChatPreview preview = preview(target.get().code(), true);
        if (preview == null || !preview.valid()) {
            return null;
        }
        int width = Math.max(guiWidth / 2, 200);
        List<ClientTooltipComponent> out = new ArrayList<>();
        out.add(ClientTooltipComponent.create(preview.title().getVisualOrderText()));
        preview.drone().ifPresent(drone -> out.add(new ChatPreviewImage(drone)));
        for (Component line : preview.lines(target.get().copy())) {
            for (FormattedCharSequence row : font.split(line, width)) {
                out.add(ClientTooltipComponent.create(row));
            }
        }
        tooltips++;
        return out;
    }

    public static int decorated() {
        return decorated;
    }

    public static int tooltips() {
        return tooltips;
    }

    public static int decodes() {
        return CACHE.loads();
    }

    public static int refused() {
        return CACHE.refused();
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
