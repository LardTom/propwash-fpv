package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CovZoneSelfTests {
    static final String NAME = "zonen: Abdeckung – leere Dimension: im Nether zeigt der Tab Zonen „In dieser Dimension gibt es noch keine Zonen.“ "
            + "und den Knopf „Neue Zone hier“";
    private static final String PHASE = "cov_zone_phase";
    private static final String MARK = "cov_zone_mark";
    private static final String FUTURE = "cov_zone_future";
    private static final BlockPos NETHER_SPOT = new BlockPos(40, 90, 40);
    private static final AtomicReference<Saved> SAVED = new AtomicReference<>();

    private CovZoneSelfTests() {
    }

    record Saved(Vec3 position, float yaw, float pitch) {
    }

    public static void register() {
        SelfTest.clientStep(NAME, 1200, CovZoneSelfTests::step);
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(MARK, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    private static @Nullable ZonesTab tab() {
        if (!(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen)) {
            return null;
        }
        ConfigTab active = screen.activeTab();
        return active instanceof ZonesTab tab ? tab : null;
    }

    private static boolean done(SelfTest.StepContext ctx) {
        Object future = ctx.state().get(FUTURE);
        return future instanceof CompletableFuture<?> f && f.isDone();
    }

    private static StepResult back(SelfTest.StepContext ctx, StepResult result) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        ctx.state().put("cov_zone_result", result);
        ctx.state().put(PHASE, 5);
        ctx.state().put(MARK, ctx.tick());
        ctx.state().put(FUTURE, ctx.onServer(server -> {
            ServerPlayer player = ZoneSelfTests.player(server);
            Saved saved = SAVED.getAndSet(null);
            Vec3 at = saved == null ? Vec3.atBottomCenterOf(server.overworld().getRespawnData().pos()) : saved.position();
            player.teleportTo(server.overworld(), at.x, at.y, at.z, Set.of(), saved == null ? 0.0F : saved.yaw(), saved == null ? 0.0F : saved.pitch(),
                    true);
        }));
        return StepResult.pending();
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        switch (phase(ctx)) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = ZoneSelfTests.player(server);
                    SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot()));
                    ServerLevel nether = server.getLevel(Level.NETHER);
                    if (nether == null) {
                        throw new IllegalStateException("kein Nether");
                    }
                    nether.getChunk(NETHER_SPOT.getX() >> 4, NETHER_SPOT.getZ() >> 4);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            nether.setBlock(NETHER_SPOT.offset(dx, -1, dz), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
                            for (int h = 0; h < 3; h++) {
                                nether.setBlock(NETHER_SPOT.offset(dx, h, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                            }
                        }
                    }
                    ZoneSelfTests.command(server, "execute in minecraft:the_nether run tp " + player.getPlainTextName() + " " + NETHER_SPOT.getX() + ".5 "
                            + NETHER_SPOT.getY() + " " + NETHER_SPOT.getZ() + ".5");
                }));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return back(ctx, StepResult.fail("Nether vorbereiten: " + future.exceptionNow()));
                }
                if (!future.isDone() || mc.level == null || mc.level.dimension() != Level.NETHER || mc.player == null) {
                    return since(ctx) > 400 ? back(ctx, StepResult.fail("Spieler kam nicht im Nether an")) : StepResult.pending();
                }
                if (since(ctx) < 40) {
                    return StepResult.pending();
                }
                ConfigTabs.open(null, ZonesTab.ID);
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                ZonesTab tab = tab();
                if (tab == null || since(ctx) < 5) {
                    return since(ctx) > 200 ? back(ctx, StepResult.fail("Tab Zonen öffnet nicht")) : StepResult.pending();
                }
                if (!tab.zones().entries().isEmpty()) {
                    return back(ctx, StepResult.fail("Nether-Tab zeigt " + tab.zones().entries().size() + " Zonen"));
                }
                FlatButton button = tab.zones().newButton();
                String newLabel = Component.translatable(ZoneSection.KEY + "new").getString();
                if (button == null || !button.visible || !button.getMessage().getString().equals(newLabel)) {
                    return back(ctx, StepResult.fail("Knopf „" + newLabel + "“ fehlt: " + (button == null ? "-" : button.getMessage().getString())));
                }
                StringBuilder joined = new StringBuilder();
                for (LayoutAudit.Text text : LayoutProbe.capture(mc, mc.gui.screen()).texts()) {
                    joined.append(text.value()).append(' ');
                }
                String shown = joined.toString().replaceAll("\\s+", " ");
                String empty = Component.translatable(ZoneSection.KEY + "empty").getString().replaceAll("\\s+", " ").trim();
                if (!shown.contains(empty)) {
                    return back(ctx, StepResult.fail("Hinweis „" + empty + "“ nicht gezeichnet: " + shown));
                }
                ctx.log("Nether: keine Zonen, „" + empty + "“ gezeichnet, Knopf „" + newLabel + "“ bei y " + button.getY());
                return back(ctx, StepResult.pass());
            }
            default -> {
                if (!done(ctx) || mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
                    return since(ctx) > 400 ? StepResult.fail("Spieler kam nicht in die Oberwelt zurück") : StepResult.pending();
                }
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                return (StepResult) state.get("cov_zone_result");
            }
        }
    }
}
