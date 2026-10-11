package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.content.client.WorkbenchReport;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseInventoryScreen;
import de.lardtom.propwash.sim.PropFailure;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class WearWeightSelfTests {
    public static final DroneBuild TYPICAL_7 = DroneBuild.LONG_RANGE.withBattery(BatteryType.B6S_1300);
    private static final int TOOLTIP_SLOT = 9;
    private static final String FUTURE = "wear_weight_future";

    private WearWeightSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("drone: Tooltips zeigen Bruchverhalten der Props und beide Gewichte", 20, ctx -> tooltipStep());
    }

    public static ItemStack propStack() {
        return ContentItems.stack(PropType.P51_3B);
    }

    public static ItemStack droneStack() {
        return ContentTab.presetDrone(TYPICAL_7);
    }

    static StepResult tooltipStep() {
        Minecraft mc = Minecraft.getInstance();
        Item.TooltipContext context = Item.TooltipContext.of(mc.level);
        List<String> errors = new ArrayList<>();
        try {
            TooltipShift.override(true);
            List<Component> prop = lines(propStack(), context);
            String wanted = "propwash.parts.tooltip.failure." + PropFailure.of(PropType.P51_3B.definition()).mode().key();
            if (prop.stream().noneMatch(line -> mentions(line, wanted))) {
                errors.add("Prop-Tooltip ohne " + wanted);
            }
            List<Component> drone = lines(droneStack(), context);
            String dry = String.format(Locale.ROOT, "%.0f", TYPICAL_7.dryMassGrams());
            String total = String.format(Locale.ROOT, "%.0f", TYPICAL_7.totalMassGrams());
            boolean weights = drone.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents t
                    && "propwash.drone.tooltip.weights".equals(t.getKey()) && t.getArgs().length == 2
                    && dry.equals(String.valueOf(t.getArgs()[0])) && total.equals(String.valueOf(t.getArgs()[1])));
            if (!weights) {
                errors.add("Drohnen-Tooltip ohne „" + dry + " g trocken · " + total + " g mit Akku“");
            }
        } finally {
            TooltipShift.override(null);
        }
        WorkbenchReport report = WorkbenchReport.of(BuildSlots.check(BuildSlots.layout(TYPICAL_7)), true);
        List<WorkbenchReport.Row> rows = report.rows();
        if (rows.size() != WorkbenchReport.STAT_KEYS.size()) {
            errors.add("Werkbank zeigt " + rows.size() + " Kennzahlen");
        } else {
            String first = rows.get(0).value().getString();
            String second = rows.get(1).value().getString();
            if (!first.startsWith(String.format(Locale.ROOT, "%.0f", TYPICAL_7.dryMassGrams()))
                    || !second.startsWith(String.format(Locale.ROOT, "%.0f", TYPICAL_7.totalMassGrams()))) {
                errors.add("Werkbank: „" + first + "“ / „" + second + "“");
            }
        }
        return errors.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", errors));
    }

    private static List<Component> lines(ItemStack stack, Item.TooltipContext context) {
        List<Component> out = new ArrayList<>();
        stack.getItem().appendHoverText(stack, context, TooltipDisplay.DEFAULT, out::add, TooltipFlag.NORMAL);
        return out;
    }

    private static boolean mentions(Component line, String key) {
        if (line.getContents() instanceof TranslatableContents t) {
            if (key.equals(t.getKey())) {
                return true;
            }
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component c && mentions(c, key)) {
                    return true;
                }
            }
        }
        for (Component sibling : line.getSiblings()) {
            if (mentions(sibling, key)) {
                return true;
            }
        }
        return false;
    }

    public static Screenshots.Scene tooltipScene(Supplier<ItemStack> item) {
        return new TooltipScene(item);
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler");
        }
        return players.getFirst();
    }

    private static final class TooltipScene implements Screenshots.Scene {
        private final Supplier<ItemStack> item;
        private volatile @Nullable ItemStack previous;
        private volatile @Nullable GameType previousMode;

        TooltipScene(Supplier<ItemStack> item) {
            this.item = item;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            ItemStack stack = this.item.get();
            Object value = ctx.state().get(FUTURE);
            if (!(value instanceof CompletableFuture<?> future)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.closeContainer();
                    this.previousMode = player.gameMode();
                    player.setGameMode(GameType.SURVIVAL);
                    this.previous = player.getInventory().getItem(TOOLTIP_SLOT).copy();
                    player.getInventory().setItem(TOOLTIP_SLOT, stack.copy());
                }));
                return StepResult.pending();
            }
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Tooltip-Szene: " + future.exceptionNow());
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            ItemStack shown = mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getItem(TOOLTIP_SLOT);
            if (!ItemStack.isSameItem(shown, stack) || mc.player.hasInfiniteMaterials()) {
                return ctx.tick() > 200 ? StepResult.fail("Tooltip-Szene: Item kommt nicht an") : StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                TooltipShift.override(true);
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, TOOLTIP_SLOT));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            TooltipShift.override(null);
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ItemStack old = this.previous;
            GameType mode = this.previousMode;
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.getInventory().setItem(TOOLTIP_SLOT, old == null ? ItemStack.EMPTY : old);
                player.setGameMode(mode == null ? GameType.CREATIVE : mode);
            });
        }
    }
}
