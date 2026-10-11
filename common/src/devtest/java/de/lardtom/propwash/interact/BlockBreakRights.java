package de.lardtom.propwash.interact;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.WorldRulesRights;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class BlockBreakRights {
    private static final NameAndId ADMIN = new NameAndId(UUID.fromString("00000000-0000-4000-8000-0000000ad417"), "propwash_admin");
    private static final double SPEED = 30.0;

    private BlockBreakRights() {
    }

    public static @Nullable String check(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        ServerPlayer guest = WorldRulesRights.guest(server);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        boolean rule = InteractRules.dronesBreakBlocks(server);
        boolean adminWasOp = server.getPlayerList().isOp(ADMIN);
        try {
            server.getGameRules().set(InteractRules.breakRule(), true, server);
            if (!adminWasOp) {
                server.getPlayerList().op(ADMIN);
            }
            BlockPos near = spawn.above(8).east(2);
            if (!server.isUnderSpawnProtection(level, near, guest)) {
                return "Spawn-Schutz greift am Spawn nicht (spawn-protection in server.properties?)";
            }
            String result = attempt(level, guest, drone, near, BreakRules.Outcome.PROTECTED);
            if (result != null) {
                return "Gast am Spawn: " + result;
            }
            server.getPlayerList().op(guest.nameAndId());
            result = attempt(level, guest, drone, near, BreakRules.Outcome.BREAK);
            server.getPlayerList().deop(guest.nameAndId());
            if (result != null) {
                return "Operator am Spawn: " + result;
            }
            BlockPos outside = spawn.above(8).east(24);
            result = attempt(level, guest, drone, outside, BreakRules.Outcome.BREAK);
            if (result != null) {
                return "Gast außerhalb des Spawn-Schutzes: " + result;
            }
            guest.setGameMode(GameType.ADVENTURE);
            result = attempt(level, guest, drone, outside, BreakRules.Outcome.GAME_MODE);
            if (result != null) {
                return "Gast im Abenteuermodus: " + result;
            }
            server.getGameRules().set(InteractRules.breakRule(), false, server);
            guest.setGameMode(GameType.SURVIVAL);
            result = attempt(level, guest, drone, outside, BreakRules.Outcome.RULE_OFF);
            if (result != null) {
                return "Regel aus: " + result;
            }
            return null;
        } finally {
            server.getPlayerList().deop(guest.nameAndId());
            if (!adminWasOp) {
                server.getPlayerList().deop(ADMIN);
            }
            server.getGameRules().set(InteractRules.breakRule(), rule, server);
            drone.discard();
        }
    }

    private static @Nullable String attempt(ServerLevel level, ServerPlayer pilot, QuadcopterEntity drone, BlockPos pos, BreakRules.Outcome expected) {
        BlockState before = level.getBlockState(pos);
        level.setBlockAndUpdate(pos, Blocks.GLASS_PANE.defaultBlockState());
        drone.snapTo(pos.getX() - 0.4, pos.getY() + 0.4, pos.getZ() + 0.5, 0.0F, 0.0F);
        drone.setDeltaMovement(new Vec3(SPEED / 20.0, 0.0, 0.0));
        DroneStrikes.onMoved(drone);
        BreakRules.Outcome cleared = DroneBreaks.clearance(level, pilot, drone, pos);
        if (cleared != expected || level.getBlockState(pos).isAir()) {
            level.setBlockAndUpdate(pos, before);
            return "Freigabe vorab: erwartet " + expected + ", Server sagt " + cleared;
        }
        BreakRules.Outcome outcome = DroneBreaks.attempt(level, pilot, drone, pos, new Vec3(SPEED, 0.0, 0.0), 0, true);
        boolean gone = level.getBlockState(pos).isAir();
        level.setBlockAndUpdate(pos, before);
        if (outcome != expected) {
            return "erwartet " + expected + ", Server sagt " + outcome;
        }
        if (gone != (expected == BreakRules.Outcome.BREAK)) {
            return "Block " + (gone ? "zerbrochen" : "ganz") + " bei " + outcome;
        }
        return null;
    }
}
