package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.FreeformBlocks;
import de.lardtom.propwash.race.FreeformFill;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateItem;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateSignal;
import de.lardtom.propwash.race.GateSignalTarget;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;

public final class GateEditor {
    public static final double LEAVE_REACH = 2.0 * GateMounting.EDIT_REACH;
    public static final int OLD_POSE_TINT = 0x30FFFFFF;
    public static final int SIGNAL_MARK = 0xFFFF2B1C;
    private static final float SIGNAL_BAND = 0.1F;
    private static final float SIGNAL_EDGE = 0.04F;
    private static final double SIGNAL_LIFT = 0.006;
    private static final int RECHECK_TICKS = 10;
    private static final int NO_KEY = Integer.MIN_VALUE;
    private static final ScrollWheelHandler WHEEL = new ScrollWheelHandler();
    private static @Nullable GateEditor current;
    private static @Nullable KeyMapping cursorKey;
    private static int swallowed = NO_KEY;
    private static @Nullable GateEditPanel hudPanel;

    public enum Status {
        SAME,
        FREE,
        BLOCKED,
        NOT_ALLOWED,
        STACKED,
        TOO_FAR
    }

    private final GateEntity gate;
    private final GateEditModel model;
    private @Nullable GateGeometry pending;
    private Status status = Status.SAME;
    private int recheck;
    private FreeformFill.@Nullable Integrity integrity;

    private GateEditor(GateEntity gate) {
        this.gate = gate;
        GateGeometry geometry = gate.geometry();
        this.model = new GateEditModel(gate.kind(), geometry.heading(), geometry.tilt().pitch(), geometry.tilt().roll(), gate.openingWidth(),
                gate.openingHeight(), gate.variant(), gate.signal());
    }

    static void register() {
        if (cursorKey == null) {
            cursorKey = ClientRegistrar.keyMapping(KeyDefaults.GATE_EDIT_CURSOR.name(), KeyDefaults.GATE_EDIT_CURSOR.key());
        }
    }

    public static @Nullable KeyMapping cursorKey() {
        return cursorKey;
    }

    public static Component cursorKeyName() {
        return Component.keybind(KeyDefaults.GATE_EDIT_CURSOR.mappingName());
    }

    public static void start(GateEntity gate) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || gate.isRemoved()) {
            return;
        }
        if (minecraft.gui.screen() instanceof GateEditCursorScreen) {
            minecraft.gui.setScreen(null);
        }
        GateEditor editor = new GateEditor(gate);
        current = editor;
        hudPanel = null;
        editor.refresh();
    }

    public static @Nullable GateEditor current() {
        return current;
    }

    public static boolean active() {
        return current != null;
    }

    public static void stop() {
        if (current != null) {
            current.end(null);
        }
    }

    public static boolean replaces(GateEntity entity) {
        GateEditor editor = current;
        return editor != null && editor.gate == entity && editor.status == Status.FREE && editor.pending != null;
    }

    public GateEntity gate() {
        return this.gate;
    }

    public GateEditModel model() {
        return this.model;
    }

    public @Nullable GateGeometry pending() {
        return this.pending;
    }

    public Status status() {
        return this.status;
    }

    public FreeformFill.@Nullable Integrity integrity() {
        return this.integrity;
    }

    public boolean freeform() {
        return this.gate.kind() == CourseElement.FREEFORM;
    }

    public boolean changed() {
        return this.status != Status.SAME || this.model.signalChanged();
    }

    public void showPage(int page) {
        this.model.showPage(page);
    }

    public @Nullable GateSignalTarget signalTarget() {
        if (!this.gate.kind().signals() || !this.model.signal().enabled()) {
            return null;
        }
        GateGeometry next = this.pending;
        if (next == null || this.status != Status.FREE || this.gate.inStack()) {
            return this.gate.signalTarget();
        }
        return GateSignalTarget.of(next);
    }

    public void refresh() {
        GateGeometry current = this.gate.geometry();
        GateGeometry next = GateMounting.preview(this.gate, this.model.heading(), this.model.pitch(), this.model.roll(), this.model.width(),
                this.model.height(), this.model.variant());
        this.pending = next;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (next.sameAs(current)) {
            this.status = Status.SAME;
        } else if (this.gate.inStack()) {
            this.status = Status.STACKED;
        } else if (player == null || !player.mayBuild()) {
            this.status = Status.NOT_ALLOWED;
        } else if (!player.isWithinEntityInteractionRange(this.gate, GateMounting.EDIT_REACH)) {
            this.status = Status.TOO_FAR;
        } else if (this.freeform()) {
            this.status = Status.FREE;
        } else {
            boolean free = minecraft.level != null && GateItem.insideWorld(minecraft.level, next)
                    && GateItem.free(minecraft.level, next, GateStacking.placedBase(next), true, this.gate);
            this.status = free ? Status.FREE : Status.BLOCKED;
        }
        if (this.freeform() && minecraft.level != null) {
            this.integrity = FreeformBlocks.integrity(minecraft.level, current);
        }
        this.recheck = RECHECK_TICKS;
    }

    public void change(GateEditModel.Row row, int notches) {
        if (notches == 0) {
            return;
        }
        this.model.select(row);
        this.model.change(row, notches, Minecraft.getInstance().hasControlDown());
        this.refresh();
    }

    public void scroll(int notches) {
        if (notches == 0) {
            return;
        }
        if (Minecraft.getInstance().options.keyShift.isDown()) {
            this.model.moveSelection(-notches);
            return;
        }
        this.change(this.model.selected(), notches);
    }

    public void set(GateEditModel.Row row, double value) {
        this.model.select(row);
        this.model.set(row, value);
        this.refresh();
    }

    public void set(float heading, float pitch, float roll, double width, double height) {
        this.model.set(heading, pitch, roll, width, height);
        this.refresh();
    }

    public void setVariant(int variant) {
        this.model.setVariant(variant);
        this.refresh();
    }

    public void rotateKey(boolean sneaking) {
        if (this.freeform()) {
            this.change(GateEditModel.Row.VARIANT, 1);
            return;
        }
        if (sneaking && this.model.rows().contains(GateEditModel.Row.VARIANT)) {
            this.change(GateEditModel.Row.VARIANT, 1);
            return;
        }
        this.model.select(GateEditModel.Row.HEADING);
        this.model.turn(sneaking ? -GateMountKeys.step() : GateMountKeys.step());
        this.refresh();
    }

    public void reset() {
        float heading = this.gate.mount().turns() == 4 ? this.gate.geometry().heading() : this.gate.geometry().yawDegrees();
        this.model.reset(heading, this.gate.standardOpeningWidth(), this.gate.standardOpeningHeight());
        this.refresh();
    }

    public void resetSignal() {
        this.model.setSignal(GateSignal.DEFAULT);
    }

    public boolean apply() {
        this.refresh();
        if ((this.status == Status.SAME || this.status == Status.FREE) && this.model.signalChanged()) {
            ClientNet.sendToServer(new RacePayloads.GateSignalC2S(this.gate.getId(), this.model.signal().pack()));
        }
        switch (this.status) {
            case SAME -> {
                this.end(null);
                return true;
            }
            case FREE -> {
                ClientNet.sendToServer(this.freeform()
                        ? new RacePayloads.GateEditC2S(this.gate.getId(), RacePayloads.GateEditAction.APPLY, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                        this.model.variant())
                        : new RacePayloads.GateEditC2S(this.gate.getId(), RacePayloads.GateEditAction.APPLY, this.model.heading(),
                        this.model.pitch(), this.model.roll(), (float) this.model.width(), (float) this.model.height(), this.model.variant()));
                this.end(null);
                return true;
            }
            default -> {
                RaceHud.message(statusText(this.status), RaceHud.RED);
                return false;
            }
        }
    }

    public void cancel() {
        this.end(this.changed() ? Component.translatable("propwash.race.edit.discarded") : null);
    }

    public void rescan() {
        ClientNet.sendToServer(new RacePayloads.GateEditC2S(this.gate.getId(), RacePayloads.GateEditAction.RESCAN, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                this.gate.variant()));
    }

    public void unlink() {
        ClientNet.sendToServer(new RacePayloads.GateEditC2S(this.gate.getId(), RacePayloads.GateEditAction.UNLINK, this.model.heading(),
                this.model.pitch(), this.model.roll(), (float) this.model.width(), (float) this.model.height()));
        this.end(null);
    }

    public boolean holdingCourseTool() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && (CourseToolItem.isCourseTool(player.getMainHandItem()) || CourseToolItem.isCourseTool(player.getOffhandItem()));
    }

    private void end(@Nullable Component message) {
        if (current == this) {
            current = null;
            hudPanel = null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof GateEditCursorScreen) {
            minecraft.gui.setScreen(null);
        }
        if (message != null) {
            RaceHud.message(message, RaceHud.WHITE);
        }
    }

    public static Component statusText(Status status) {
        return switch (status) {
            case SAME -> Component.translatable("propwash.race.edit.status.same");
            case FREE -> Component.translatable("propwash.race.edit.status.free");
            case BLOCKED -> Component.translatable("propwash.race.mount.blocked");
            case NOT_ALLOWED -> Component.translatable("propwash.race.mount.not_allowed");
            case STACKED -> Component.translatable("propwash.race.edit.status.stacked");
            case TOO_FAR -> Component.translatable("propwash.race.edit.status.too_far");
        };
    }

    static void tick(Minecraft minecraft) {
        KeyMapping key = cursorKey;
        GateEditor editor = current;
        if (editor == null) {
            drain(key);
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || editor.gate.isRemoved() || editor.gate.level() != player.level() || player.isSpectator() || !player.isAlive()
                || PilotSession.INSTANCE.isActive()) {
            editor.end(null);
            return;
        }
        if (!GateMounting.holdsTool(player)) {
            editor.end(Component.translatable("propwash.race.edit.closed.tool").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!player.isWithinEntityInteractionRange(editor.gate, LEAVE_REACH)) {
            editor.end(Component.translatable("propwash.race.edit.closed.far").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (--editor.recheck <= 0) {
            editor.refresh();
        }
        drain(key);
        if (key != null && key.isDown() && minecraft.gui.screen() == null && minecraft.gui.overlay() == null) {
            minecraft.gui.setScreen(new GateEditCursorScreen(editor));
        }
    }

    private static void drain(@Nullable KeyMapping key) {
        if (key == null) {
            return;
        }
        boolean clicked = true;
        while (clicked) {
            clicked = key.consumeClick();
        }
    }

    public static void beforeKeybinds(Minecraft minecraft) {
        GateEditor editor = current;
        if (editor == null) {
            return;
        }
        Options options = minecraft.options;
        boolean used = false;
        while (options.keyUse.consumeClick()) {
            used = true;
        }
        options.keyUse.setDown(false);
        drain(options.keyAttack);
        options.keyAttack.setDown(false);
        if (used) {
            editor.apply();
        }
    }

    public static boolean onScroll(long handle, double xoffset, double yoffset) {
        Minecraft minecraft = Minecraft.getInstance();
        GateEditor editor = current;
        if (editor == null || handle != minecraft.getWindow().handle() || minecraft.gui.screen() != null || minecraft.gui.overlay() != null
                || minecraft.player == null) {
            return false;
        }
        boolean discrete = minecraft.options.discreteMouseScroll().get();
        double sensitivity = minecraft.options.mouseWheelSensitivity().get();
        double x = (discrete ? Math.signum(xoffset) : xoffset) * sensitivity;
        double y = (discrete ? Math.signum(yoffset) : yoffset) * sensitivity;
        editor.scroll(notches(x, y));
        return true;
    }

    public static int notches(double scaledX, double scaledY) {
        Vector2i wheel = WHEEL.onMouseScroll(scaledX, scaledY);
        return wheel.y == 0 ? -wheel.x : wheel.y;
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
        GateEditor editor = current;
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
        return false;
    }

    static void submit(SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState) {
        Minecraft minecraft = Minecraft.getInstance();
        GateEditor editor = current;
        GateRenderer renderer = GateRenderer.current();
        Vec3 camera = levelState.cameraRenderState.pos;
        if (editor == null || renderer == null || camera == null || minecraft.level == null) {
            return;
        }
        GateSignalTarget target = editor.signalTarget();
        if (target != null) {
            submitSignal(target, collector, poseStack, camera);
        }
        GateGeometry pending = editor.pending;
        if (pending == null || editor.status == Status.SAME) {
            return;
        }
        if (editor.status == Status.FREE) {
            float age = minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            renderer.submitEdited(editor.gate, pending, poseStack, collector, camera, age);
            if (!pending.freeform()) {
                GatePreview.submitTinted(renderer, editor.gate.geometry(), editor.gate.size(), OLD_POSE_TINT, 0, minecraft.level, collector,
                        poseStack, camera);
                GatePreview.submitArrow(renderer, pending, editor.gate.size(), GatePreview.FREE_ARROW, minecraft.level, collector, poseStack,
                        camera);
            }
            return;
        }
        if (!pending.freeform()) {
            GatePreview.submitGhost(renderer, pending, editor.gate.size(), false, minecraft.level, collector, poseStack, camera);
        }
    }

    private static void submitSignal(GateSignalTarget target, SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera) {
        for (int face = 0; face < 6; face++) {
            int fx = GateSignalTarget.stepX(face);
            int fy = GateSignalTarget.stepY(face);
            int fz = GateSignalTarget.stepZ(face);
            float band = face == target.face() ? SIGNAL_BAND : SIGNAL_EDGE;
            poseStack.pushPose();
            poseStack.translate(target.x() + 0.5 + fx * (0.5 + SIGNAL_LIFT) - camera.x, target.y() + 0.5 + fy * (0.5 + SIGNAL_LIFT) - camera.y,
                    target.z() + 0.5 + fz * (0.5 + SIGNAL_LIFT) - camera.z);
            poseStack.rotate(new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, fx, fy, fz));
            collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(),
                    (pose, buffer) -> GateRenderer.frameBand(pose, buffer, -0.5F, -0.5F, 0.5F, 0.5F, band, 0.0F, SIGNAL_MARK, SIGNAL_MARK));
            poseStack.popPose();
        }
    }

    public static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        GateEditor editor = current;
        if (editor == null || minecraft.gui.screen() instanceof GateEditCursorScreen || minecraft.player == null) {
            return;
        }
        GateEditPanel panel = hudPanel;
        if (panel == null || panel.editor() != editor) {
            panel = new GateEditPanel(editor, false);
            hudPanel = panel;
        }
        panel.extractHud(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
