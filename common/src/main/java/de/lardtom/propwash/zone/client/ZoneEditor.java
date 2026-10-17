package de.lardtom.propwash.zone.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.client.GateEditor;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneCheck;
import de.lardtom.propwash.zone.ZoneDraft;
import de.lardtom.propwash.zone.ZoneEditC2S;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ZoneEditor {
    public static final long DELETE_CONFIRM_NANOS = 4_000_000_000L;
    private static final int NO_KEY = Integer.MIN_VALUE;
    private static @Nullable ZoneEditor current;
    private static @Nullable ZoneEditPanel hudPanel;
    private static int swallowed = NO_KEY;

    private final ZoneDraft draft;
    private final String dimension;
    private long deleteArmedUntil;

    private ZoneEditor(Zone zone, String dimension) {
        this.draft = new ZoneDraft(zone);
        this.dimension = dimension;
    }

    public static @Nullable ZoneEditor current() {
        return current;
    }

    public static boolean active() {
        return current != null;
    }

    public static boolean start(Zone zone) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !ClientZones.INSTANCE.editable()) {
            return false;
        }
        GateEditor.stop();
        if (minecraft.gui.screen() != null) {
            minecraft.gui.setScreen(null);
        }
        current = new ZoneEditor(zone, ClientZones.dimension(minecraft.level));
        hudPanel = null;
        return true;
    }

    public static boolean startNew() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return false;
        }
        String name = Component.translatable("propwash.zone.new_name", nextNumber()).getString();
        return start(ZoneDraft.fresh(ClientZones.dimension(minecraft.level), name, player.getX(), player.getY(), player.getZ()));
    }

    static int nextNumber() {
        int number = ClientZones.INSTANCE.zones().size() + 1;
        while (taken(Component.translatable("propwash.zone.new_name", number).getString())) {
            number++;
        }
        return number;
    }

    private static boolean taken(String name) {
        for (Zone zone : ClientZones.INSTANCE.zones()) {
            if (ZoneCheck.sameName(zone.name(), name)) {
                return true;
            }
        }
        return false;
    }

    public static void stop() {
        if (current != null) {
            current.end(null);
        }
    }

    public ZoneDraft model() {
        return this.draft;
    }

    public Zone draft() {
        return this.draft.zone();
    }

    public boolean replaces(Zone zone) {
        return !this.draft.isNew() && zone.id() == this.draft.original().id();
    }

    public @Nullable String problem() {
        return ZoneCheck.problem(this.draft.zone(), ClientZones.INSTANCE.zones());
    }

    public boolean deleteArmed() {
        return this.deleteArmedUntil > Util.getNanos();
    }

    public void change(ZoneDraft.Row row, int notches) {
        this.draft.change(row, notches, Minecraft.getInstance().hasControlDown(), ClientWorldRules.INSTANCE.physics());
    }

    public void scroll(int notches) {
        if (notches == 0) {
            return;
        }
        if (Minecraft.getInstance().options.keyShift.isDown()) {
            this.draft.moveSelection(-notches);
            return;
        }
        this.change(this.draft.selected(), notches);
    }

    public void reset(ZoneDraft.Row row) {
        this.draft.reset(row);
    }

    public void centerOnPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            this.draft.centerOn(player.getX(), player.getY(), player.getZ());
        }
    }

    public boolean apply() {
        String problem = this.problem();
        if (problem != null) {
            return false;
        }
        if (!this.draft.changed()) {
            this.end(null);
            return true;
        }
        boolean sent = ClientNet.sendToServer(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, this.draft.zone()));
        this.end(sent ? null : Component.translatable("propwash.zone.edit.not_sent").withStyle(ChatFormatting.RED));
        return sent;
    }

    public boolean delete() {
        if (this.draft.isNew()) {
            this.end(null);
            return true;
        }
        if (!this.deleteArmed()) {
            this.deleteArmedUntil = Util.getNanos() + DELETE_CONFIRM_NANOS;
            return false;
        }
        boolean sent = ClientNet.sendToServer(new ZoneEditC2S(ZoneEditC2S.Action.DELETE, this.draft.original()));
        this.end(sent ? null : Component.translatable("propwash.zone.edit.not_sent").withStyle(ChatFormatting.RED));
        return sent;
    }

    public void cancel() {
        this.end(null);
    }

    private void end(@Nullable Component message) {
        if (current == this) {
            current = null;
            hudPanel = null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof ZoneEditScreen) {
            minecraft.gui.setScreen(null);
        }
        if (message != null && minecraft.player != null) {
            minecraft.player.sendOverlayMessage(message);
        }
    }

    static void tick(Minecraft minecraft) {
        ZoneEditor editor = current;
        if (editor == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !ClientZones.dimension(minecraft.level).equals(editor.dimension) || player.isSpectator()
                || !player.isAlive() || PilotSession.INSTANCE.isActive() || GateEditor.active()) {
            editor.end(null);
            return;
        }
        if (!ClientZones.INSTANCE.editable()) {
            editor.end(Component.translatable("propwash.zone.edit.closed.rights").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!editor.draft.isNew() && ClientZones.INSTANCE.get(editor.draft.original().id()) == null) {
            editor.end(Component.translatable("propwash.zone.edit.closed.removed").withStyle(ChatFormatting.GRAY));
            return;
        }
        KeyMapping key = GateEditor.cursorKey();
        if (key != null && key.isDown() && minecraft.gui.screen() == null && minecraft.gui.overlay() == null) {
            minecraft.gui.setScreen(new ZoneEditScreen(editor));
        }
    }

    public static void beforeKeybinds(Minecraft minecraft) {
        ZoneEditor editor = current;
        if (editor == null) {
            return;
        }
        Options options = minecraft.options;
        boolean used = false;
        while (options.keyUse.consumeClick()) {
            used = true;
        }
        options.keyUse.setDown(false);
        boolean attacked = true;
        while (attacked) {
            attacked = options.keyAttack.consumeClick();
        }
        options.keyAttack.setDown(false);
        if (used) {
            editor.apply();
        }
    }

    public static boolean onScroll(long handle, double xoffset, double yoffset) {
        Minecraft minecraft = Minecraft.getInstance();
        ZoneEditor editor = current;
        if (editor == null || handle != minecraft.getWindow().handle() || minecraft.gui.screen() != null || minecraft.gui.overlay() != null
                || minecraft.player == null) {
            return false;
        }
        boolean discrete = minecraft.options.discreteMouseScroll().get();
        double sensitivity = minecraft.options.mouseWheelSensitivity().get();
        double x = (discrete ? Math.signum(xoffset) : xoffset) * sensitivity;
        double y = (discrete ? Math.signum(yoffset) : yoffset) * sensitivity;
        editor.scroll(GateEditor.notches(x, y));
        return true;
    }

    public static boolean onKey(long handle, int action, KeyEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (handle != minecraft.getWindow().handle()) {
            return false;
        }
        int key = event.key();
        if (action == InputConstants.RELEASE) {
            if (key == swallowed) {
                swallowed = NO_KEY;
            }
            return false;
        }
        if (key == swallowed) {
            return true;
        }
        ZoneEditor editor = current;
        if (editor == null || action != InputConstants.PRESS || minecraft.gui.screen() != null || minecraft.gui.overlay() != null) {
            return false;
        }
        if (event.isEscape()) {
            swallowed = key;
            editor.cancel();
            return true;
        }
        if (event.isConfirmation()) {
            swallowed = key;
            editor.apply();
            return true;
        }
        if (key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
            swallowed = key;
            editor.reset(editor.draft.selected());
            return true;
        }
        if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
            editor.draft.moveSelection(key == InputConstants.KEY_UP ? -1 : 1);
            return true;
        }
        if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
            editor.change(editor.draft.selected(), key == InputConstants.KEY_RIGHT ? 1 : -1);
            return true;
        }
        return false;
    }

    public static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        ZoneEditor editor = current;
        if (editor == null || minecraft.gui.screen() instanceof ZoneEditScreen || minecraft.player == null) {
            return;
        }
        ZoneEditPanel panel = hudPanel;
        if (panel == null || panel.editor() != editor) {
            panel = new ZoneEditPanel(editor, false);
            hudPanel = panel;
        }
        panel.layout(graphics.guiWidth(), graphics.guiHeight());
        panel.draw(graphics, -1, -1);
    }

    static List<ZoneDraft.Row> rows(ZoneEditor editor) {
        return editor.draft.rows();
    }
}
