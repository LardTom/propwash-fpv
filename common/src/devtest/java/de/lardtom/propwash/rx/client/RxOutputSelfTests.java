package de.lardtom.propwash.rx.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxModule;
import de.lardtom.propwash.rx.RxServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class RxOutputSelfTests {
    private static final float[] YAWS = {-90.0F, 0.0F, 90.0F, 180.0F};
    private static final Direction[] LOOKS = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};

    private RxOutputSelfTests() {
    }

    record Layout(BlockPos rx, BlockPos front, BlockPos behindFront, BlockPos dust, BlockPos right, BlockPos back, BlockPos up, BlockPos down) {
        static Layout of(BlockPos site) {
            BlockPos rx = site.offset(-3, 0, -2);
            return new Layout(rx, rx.east(), rx.east(2), rx.north(), rx.south(), rx.west(), rx.above(), rx.below());
        }

        List<BlockPos> lamps() {
            return List.of(this.front, this.behindFront, this.right, this.back, this.up, this.down);
        }
    }

    static void register() {
        SelfTest.clientStep(RxSelfTests.GROUP + "Ausgang nach vorne wie beim Repeater – beim Setzen in Blickrichtung, Lampe vorne und Lampe "
                + "dahinter an, seitlich, hinten, oben und unten aus, Staub nur vorne verbunden; alter Block aus der Welt sendet weiter an alle Seiten",
                2400, RxSelfTests.flowFromSite(
                        RxSelfTests.phase("Block von Hand nach Osten setzen, Lampen und Staub rundum", 300, (ctx, run) -> RxSelfTests.check(ctx, run,
                                RxOutputSelfTests::build)),
                        RxSelfTests.phase("Gas 0 % – alles aus", 400, (ctx, run) -> {
                            RxSelfTests.throttle(1000);
                            return settle(ctx, run, server -> lamps(server, 0, false));
                        }),
                        RxSelfTests.phase("Gas 100 % – nur vorne an, auch durch die Lampe vorne hindurch", 400, (ctx, run) -> {
                            RxSelfTests.throttle(2000);
                            StepResult result = settle(ctx, run, server -> lamps(server, 15, false));
                            if (result.status() == StepResult.Status.PASS) {
                                run.notes.add("nach Osten: Lampe vorne und die dahinter an, rechts, hinten, oben, unten und Staub links aus, Staub verbindet nur vorne");
                            }
                            return result;
                        }),
                        RxSelfTests.phase("Gas 0 % – vorne und dahinter wieder aus", 400, (ctx, run) -> {
                            RxSelfTests.throttle(1000);
                            return settle(ctx, run, server -> lamps(server, 0, false));
                        }),
                        RxSelfTests.phase("in jede Blickrichtung setzen", 200, (ctx, run) -> RxSelfTests.check(ctx, run, RxOutputSelfTests::placeFacings)),
                        RxSelfTests.phase("alter Block aus der Welt ohne Richtung", 200, (ctx, run) -> RxSelfTests.check(ctx, run, RxOutputSelfTests::loadOld)),
                        RxSelfTests.phase("alter Block bei Gas 100 % – an allen Seiten, nicht durch die Lampe vorne", 400, (ctx, run) -> {
                            RxSelfTests.throttle(2000);
                            StepResult result = settle(ctx, run, server -> lamps(server, 15, true));
                            if (result.status() == StepResult.Status.PASS) {
                                run.notes.add("alter Block (gespeichert ohne Richtung): output=all, Lampen rundum an, Staub an, Lampe hinter der vorderen aus");
                            }
                            return result;
                        }),
                        RxSelfTests.phase("alter Block bei Gas 0 % – alles aus", 400, (ctx, run) -> {
                            RxSelfTests.throttle(1000);
                            StepResult result = settle(ctx, run, server -> lamps(server, 0, true));
                            if (result.status() == StepResult.Status.PASS) {
                                ctx.onServer(RxOutputSelfTests::clear);
                            }
                            return result;
                        })));
    }

    static StepResult settle(SelfTest.StepContext ctx, RxSelfTests.Run run, Function<MinecraftServer, @Nullable String> check) {
        if (run.waited(ctx) < 12) {
            return StepResult.pending();
        }
        StepResult checked = RxSelfTests.check(ctx, run, check);
        if (checked.status() == StepResult.Status.FAIL) {
            run.future = null;
            return new StepResult(StepResult.Status.PENDING, checked.message());
        }
        return checked;
    }

    static @Nullable String property(BlockState state, String name) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        return property == null ? null : valueName(state, property);
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    static @Nullable String placeByHand(ServerPlayer player, BlockPos pos, float yaw) {
        ServerLevel level = player.level();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        RxSelfTests.teleport(player, Vec3.atBottomCenterOf(RxSelfTests.site()), yaw, 20.0F);
        ItemStack stack = new ItemStack(RxModule.RX_BLOCK.item().get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos support = pos.below();
        InteractionResult result = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support).add(0.0, 0.5, 0.0), Direction.UP, support, false)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneModule.REMOTE.get()));
        if (!result.consumesAction() || !(level.getBlockState(pos).getBlock() instanceof RxBlock)) {
            return "RX-Block bei " + pos.toShortString() + " nicht gesetzt: " + result;
        }
        return null;
    }

    static @Nullable String build(MinecraftServer server) {
        ServerPlayer player = RxSelfTests.player(server);
        ServerLevel level = player.level();
        Layout l = Layout.of(RxSelfTests.site());
        level.setBlock(l.down(), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        String problem = placeByHand(player, l.rx(), YAWS[0]);
        RxSelfTests.teleport(player, Vec3.atBottomCenterOf(RxSelfTests.site()), -90.0F, 20.0F);
        if (problem != null) {
            return problem;
        }
        for (BlockPos lamp : List.of(l.front(), l.behindFront(), l.right(), l.back(), l.up())) {
            level.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(l.dust(), Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        RxSelfTests.bindDirect(level, l.rx(), player.getUUID(), 64);
        RxServer.refreshDemand(player);
        return null;
    }

    static @Nullable String lamps(MinecraftServer server, int power, boolean old) {
        ServerLevel level = server.overworld();
        Layout l = Layout.of(RxSelfTests.site());
        BlockState rx = level.getBlockState(l.rx());
        List<String> wrong = new ArrayList<>();
        if (!(rx.getBlock() instanceof RxBlock)) {
            return "kein RX-Block: " + rx;
        }
        if (rx.getValue(RxBlock.POWER) != power) {
            wrong.add("Stärke " + rx.getValue(RxBlock.POWER) + " statt " + power);
        }
        boolean on = power > 0;
        Map<String, Boolean> want = new LinkedHashMap<>();
        want.put("vorne", on);
        want.put("hinter der vorderen", on && !old);
        want.put("rechts", on && old);
        want.put("hinten", on && old);
        want.put("oben", on && old);
        want.put("unten", on && old);
        List<BlockPos> lamps = l.lamps();
        int index = 0;
        for (Map.Entry<String, Boolean> lamp : want.entrySet()) {
            BlockState state = level.getBlockState(lamps.get(index++));
            boolean lit = state.is(Blocks.REDSTONE_LAMP) && state.getValue(RedstoneLampBlock.LIT);
            if (lit != lamp.getValue()) {
                wrong.add("Lampe " + lamp.getKey() + (lit ? " an" : " aus"));
            }
        }
        BlockState dust = level.getBlockState(l.dust());
        boolean dustOn = dust.is(Blocks.REDSTONE_WIRE) && dust.getValue(RedstoneWireBlock.POWER) > 0;
        if (dustOn != (on && old)) {
            wrong.add("Staub seitlich " + (dustOn ? "an" : "aus"));
        }
        for (Direction toward : Direction.Plane.HORIZONTAL) {
            boolean connects = rx.shouldRedstoneWireConnectTo(level, l.rx(), toward);
            boolean wanted = old || toward == Direction.WEST;
            if (connects != wanted) {
                wrong.add("Staub " + toward.getOpposite().getSerializedName() + " vom Block " + (connects ? "verbindet" : "verbindet nicht"));
            }
        }
        return wrong.isEmpty() ? null : String.join(", ", wrong);
    }

    static @Nullable String placeFacings(MinecraftServer server) {
        ServerPlayer player = RxSelfTests.player(server);
        ServerLevel level = player.level();
        BlockPos site = RxSelfTests.site();
        List<String> wrong = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < YAWS.length; i++) {
            BlockPos pos = site.offset(-4 + 2 * i, 0, 4);
            String problem = placeByHand(player, pos, YAWS[i]);
            if (problem != null) {
                wrong.add(problem);
                continue;
            }
            BlockState state = level.getBlockState(pos);
            String facing = property(state, "facing");
            String output = property(state, "output");
            seen.add(String.format(Locale.ROOT, "Blick %s → facing=%s output=%s", LOOKS[i].getSerializedName(), facing, output));
            if (!LOOKS[i].getSerializedName().equals(facing) || !"front".equals(output)) {
                wrong.add("Blick " + LOOKS[i].getSerializedName() + ": facing=" + facing + ", output=" + output);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        RxSelfTests.teleport(player, Vec3.atBottomCenterOf(site), -90.0F, 20.0F);
        return wrong.isEmpty() ? null : String.join("; ", wrong) + " (" + String.join(", ", seen) + ")";
    }

    static @Nullable String loadOld(MinecraftServer server) {
        ServerPlayer player = RxSelfTests.player(server);
        ServerLevel level = player.level();
        Layout l = Layout.of(RxSelfTests.site());
        CompoundTag saved = new CompoundTag();
        saved.putString("id", "propwash:rx_block");
        CompoundTag properties = new CompoundTag();
        properties.putString("power", "0");
        properties.putString("link", "linked");
        saved.put("properties", properties);
        List<String> wrong = new ArrayList<>();
        BlockState old = null;
        for (Tag tag : List.of((Tag) saved, StringTag.valueOf("propwash:rx_block"))) {
            BlockState state = BlockState.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
            if (state == null || !(state.getBlock() instanceof RxBlock)) {
                wrong.add("nicht lesbar: " + tag);
                continue;
            }
            if (!"all".equals(property(state, "output")) || !"north".equals(property(state, "facing"))) {
                wrong.add(tag + " → output=" + property(state, "output") + ", facing=" + property(state, "facing"));
            }
            if (old == null) {
                old = state;
            }
        }
        if (old == null || !wrong.isEmpty()) {
            return String.join("; ", wrong);
        }
        BlockState current = level.getBlockState(l.rx());
        level.setBlock(l.rx(), old.setValue(RxBlock.POWER, current.getValue(RxBlock.POWER)), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(l.rx()) instanceof RxBlockEntity entity) || !entity.settings().ownedBy(player.getUUID())) {
            return "Block-Entity nach dem Tausch nicht mehr gebunden";
        }
        return null;
    }

    static void clear(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Layout l = Layout.of(RxSelfTests.site());
        for (BlockPos pos : List.of(l.rx(), l.front(), l.behindFront(), l.dust(), l.right(), l.back(), l.up())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(l.down(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    }
}
