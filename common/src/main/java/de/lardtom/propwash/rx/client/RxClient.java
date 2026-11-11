package de.lardtom.propwash.rx.client;

import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.scenes.RxBlockScene;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.rx.RxAccess;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxConfigC2S;
import de.lardtom.propwash.rx.RxDemandS2C;
import de.lardtom.propwash.rx.RxInputC2S;
import de.lardtom.propwash.rx.RxMask;
import de.lardtom.propwash.rx.RxSettings;
import de.lardtom.propwash.rx.RxSource;
import de.lardtom.propwash.rx.RxText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class RxClient {
    public static final int HEARTBEAT_TICKS = 40;
    public static final float CHANGE = 2.0F / 512.0F;
    private static RxMask demand = RxMask.NONE;
    private static @Nullable RxMask sentMask;
    private static final float[] SENT_STICKS = new float[RxSource.STICKS];
    private static final float[] SENT_AXES = new float[RxSource.AXES];
    private static int sentPressed;
    private static int sinceSent;
    private static int sequence;
    private static int sentCount;
    private static boolean initialized;

    private RxClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientNet.handle(RxDemandS2C.TYPE, (payload, context) -> {
            demand = payload.mask();
            sentMask = null;
        });
        ClientBus.TICK_END.register(RxClient::tick);
        ClientBus.DISCONNECT.register(minecraft -> reset());
        RxBlock.clientUse(RxClient::use);
        GuideScenes.register(RxBlockScene::create);
    }

    public static RxMask demand() {
        return demand;
    }

    public static int sentCount() {
        return sentCount;
    }

    private static void reset() {
        demand = RxMask.NONE;
        sentMask = null;
        sinceSent = 0;
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        RxMask mask = demand;
        if (mask.empty()) {
            sentMask = null;
            return;
        }
        RxRadio.Frame frame = RxRadio.current();
        if (frame == null) {
            return;
        }
        sinceSent++;
        int pressed = frame.pressed() & mask.buttons();
        if (!changed(mask, frame, pressed) && sinceSent < HEARTBEAT_TICKS) {
            return;
        }
        RxInputC2S payload = RxInputC2S.of(++sequence, mask, frame.sticks(), frame.axes(), pressed);
        if (ClientNet.sendToServer(payload)) {
            sentMask = mask;
            sentPressed = pressed;
            payload.unpack(SENT_STICKS, SENT_AXES);
            sinceSent = 0;
            sentCount++;
        }
    }

    private static boolean changed(RxMask mask, RxRadio.Frame frame, int pressed) {
        if (sentMask == null || !mask.equals(sentMask) || pressed != sentPressed) {
            return true;
        }
        for (int i = 0; i < RxSource.STICKS; i++) {
            if ((mask.sticks() & (1 << i)) != 0 && moved(frame.sticks()[i], SENT_STICKS[i])) {
                return true;
            }
        }
        for (int i = 0; i < RxSource.AXES; i++) {
            if ((mask.axes() & (1 << i)) != 0 && moved(i < frame.axes().length ? frame.axes()[i] : 0.0F, SENT_AXES[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean moved(float now, float sent) {
        float value = Float.isFinite(now) ? Math.clamp(now, -1.0F, 1.0F) : 0.0F;
        return Math.abs(value - sent) > CHANGE;
    }

    public static boolean sneakUse(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        Minecraft minecraft = Minecraft.getInstance();
        if (hand != InteractionHand.MAIN_HAND || !player.isSecondaryUseActive() || !ContentIds.isRemote(player.getMainHandItem())
                || minecraft.level == null || !(minecraft.level.getBlockState(hit.getBlockPos()).getBlock() instanceof RxBlock)) {
            return false;
        }
        use(hit.getBlockPos().immutable(), Boolean.TRUE);
        return true;
    }

    public static @Nullable RxBlockEntity blockEntity(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        BlockEntity entity = minecraft.level.getBlockEntity(pos);
        return entity instanceof RxBlockEntity rx && !rx.isRemoved() ? rx : null;
    }

    public static RxAccess access(LocalPlayer player, RxSettings settings) {
        return RxAccess.of(settings.owner(), player.getUUID(), operator(player));
    }

    public static boolean operator(LocalPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static void use(BlockPos pos, Boolean unbind) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        RxBlockEntity entity = blockEntity(pos);
        if (player == null || entity == null) {
            return;
        }
        RxSettings settings = entity.settings();
        RxAccess access = access(player, settings);
        if (access == RxAccess.FOREIGN) {
            if (unbind) {
                player.sendOverlayMessage(Component.translatable(RxText.FOREIGN, RxText.owner(entity.ownerName(), settings.owner()))
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        if (access == RxAccess.UNBOUND && unbind) {
            ClientNet.sendToServer(RxConfigC2S.bind(pos));
        }
        minecraft.gui.setScreen(new RxScreen(pos, unbind && access.mayChange()));
    }
}
