package de.lardtom.propwash.rx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class RxBlockEntity extends BlockEntity {
    public static final String OWNER_NAME = "OwnerName";

    private RxSettings settings = RxSettings.EMPTY;
    private String ownerName = "";
    private boolean indexed;
    private long linkCheckedAt = Long.MIN_VALUE;
    private boolean inRange;

    public RxBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RxModule.RX_BLOCK_BE.get(), worldPosition, blockState);
    }

    public RxSettings settings() {
        return this.settings;
    }

    public String ownerName() {
        return this.ownerName;
    }

    public void apply(RxSettings next) {
        if (next.equals(this.settings)) {
            return;
        }
        boolean ownerChanged = !Objects.equals(next.owner(), this.settings.owner());
        this.settings = next;
        this.linkCheckedAt = Long.MIN_VALUE;
        Level level = this.getLevel();
        if (this.indexed && level != null && ownerChanged) {
            RxIndex.of(level).put(this);
        }
        this.setChanged();
        if (level instanceof ServerLevel server && !this.isRemoved()) {
            BlockState state = this.getBlockState();
            server.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    boolean linkDue(long tick, int interval) {
        return this.linkCheckedAt == Long.MIN_VALUE || tick - this.linkCheckedAt >= interval || tick < this.linkCheckedAt;
    }

    void link(long tick, boolean inRange) {
        this.linkCheckedAt = tick;
        this.inRange = inRange;
    }

    boolean inRange() {
        return this.inRange;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        RxSettings read = input.read(RxSettings.MAP_CODEC).orElse(RxSettings.EMPTY);
        boolean ownerChanged = !Objects.equals(read.owner(), this.settings.owner());
        this.settings = read;
        this.ownerName = input.getStringOr(OWNER_NAME, "");
        this.linkCheckedAt = Long.MIN_VALUE;
        Level level = this.getLevel();
        if (this.indexed && level != null && ownerChanged) {
            RxIndex.of(level).put(this);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store(RxSettings.MAP_CODEC, this.settings);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = this.saveCustomOnly(registries);
        String name = RxServer.ownerName(this.getLevel(), this.settings.owner());
        if (!name.isEmpty()) {
            tag.putString(OWNER_NAME, name);
        }
        return tag;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        Level level = this.getLevel();
        if (level != null) {
            RxIndex.of(level).put(this);
            this.indexed = true;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Level level = this.getLevel();
        if (this.indexed && level != null) {
            RxIndex.of(level).remove(this);
        }
        this.indexed = false;
    }
}
