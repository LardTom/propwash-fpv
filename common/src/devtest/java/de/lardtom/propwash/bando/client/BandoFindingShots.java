package de.lardtom.propwash.bando.client;

import de.lardtom.propwash.bando.BandoPiece;
import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoTransform;
import de.lardtom.propwash.bando.plan.BandoVariant;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class BandoFindingShots {
    record Known(BandoFamily family, BandoVariant variant, ChunkPos chunk, BlockPos focus) {
    }

    record Spot(String file, BandoFamily family, BandoVariant variant, ChunkPos chunk, @Nullable BlockPos focus, boolean close) {
    }

    static final Map<Long, List<Known>> KNOWN = Map.of(
            1618033L, List.of(new Known(BandoFamily.FIELD, BandoVariant.BARN, new ChunkPos(-296, -417), new BlockPos(-4720, 63, -6667))),
            900001L, List.of(new Known(BandoFamily.FIELD, BandoVariant.BARN, new ChunkPos(-292, -72), new BlockPos(-4654, 132, -1148))));

    private int index;
    private String phase = "resize";
    private int since;
    private int stable;
    private int count;
    private final List<Spot> spots = new ArrayList<>();
    private @Nullable CompletableFuture<?> pending;
    private @Nullable CompletableFuture<String> shot;
    private volatile @Nullable String note;
    private volatile @Nullable Vec3 eye;

    static void register() {
        if (Screenshots.enabled()) {
            SelfTest.afterLeaveStep("bando: Lage – Luftbilder der Stellen mit Befund", 20 * 60 * 6, new BandoFindingShots()::tick);
        }
    }

    static List<Spot> spots(long seed, List<BandoPlacementTests.Site> flagged) {
        List<Spot> spots = new ArrayList<>();
        Set<ChunkPos> seen = new HashSet<>();
        String prefix = "bando_befund_" + Long.toString(seed).replace('-', 'm') + "_";
        for (Known known : KNOWN.getOrDefault(seed, List.of())) {
            if (seen.add(known.chunk())) {
                String file = prefix + known.variant().key() + "_" + known.chunk().x() + "_" + known.chunk().z();
                spots.add(new Spot(file.toLowerCase(Locale.ROOT), known.family(), known.variant(), known.chunk(), known.focus(), false));
                spots.add(new Spot(file.toLowerCase(Locale.ROOT) + "_nah", known.family(), known.variant(), known.chunk(), known.focus(), true));
            }
        }
        for (BandoPlacementTests.Site site : flagged) {
            if (seen.add(site.chunk())) {
                String file = prefix + site.variant().key() + "_" + site.chunk().x() + "_" + site.chunk().z();
                spots.add(new Spot(file.toLowerCase(Locale.ROOT), site.family(), site.variant(), site.chunk(), null, false));
            }
        }
        return spots;
    }

    StepResult tick(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (this.pending != null && this.pending.isCompletedExceptionally()) {
            return this.fail(ctx, "Server-Aktion: " + this.pending.exceptionNow());
        }
        switch (this.phase) {
            case "resize" -> {
                this.spots.clear();
                this.spots.addAll(spots(BandoSelfTests.SEED, BandoPlacementTests.FLAGGED));
                if (this.spots.isEmpty()) {
                    ctx.log("keine Stellen mit Befund für Seed " + BandoSelfTests.SEED);
                    return StepResult.pass();
                }
                StepResult resized = Screenshots.resizeWindow(ctx);
                if (resized.status() == StepResult.Status.PENDING) {
                    return resized;
                }
                this.next("go", ctx);
            }
            case "go" -> {
                if (this.index >= this.spots.size()) {
                    StepResult restored = Screenshots.restoreWindow(ctx);
                    if (mc.gui.hud.isHidden()) {
                        mc.gui.hud.toggle();
                    }
                    ctx.log(this.count + " Bilder von Stellen mit Befund in " + Screenshots.dir());
                    return restored;
                }
                Spot spot = this.spots.get(this.index);
                this.note = null;
                this.eye = null;
                this.pending = ctx.onServer(server -> this.note = this.view(server, spot));
                this.next("wait", ctx);
            }
            case "wait" -> {
                if (this.pending == null || !this.pending.isDone()) {
                    return StepResult.pending();
                }
                String text = this.note;
                if (text != null) {
                    ctx.log(text);
                    this.note = null;
                }
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                Screenshots.clearOverlays(mc);
                Screenshots.parkMouse(mc);
                Vec3 target = this.eye;
                boolean arrived = target != null && mc.player != null && mc.player.getEyePosition().distanceTo(target) < 1.0;
                if (arrived && mc.levelRenderer.hasRenderedAllSections() && ctx.tick() - this.since > 60) {
                    this.stable++;
                } else {
                    this.stable = 0;
                }
                if (this.stable >= 20 || ctx.tick() - this.since > 600) {
                    this.shot = Screenshots.capture(this.spots.get(this.index).file());
                    this.next("shot", ctx);
                }
            }
            case "shot" -> {
                if (this.shot == null || !this.shot.isDone()) {
                    return StepResult.pending();
                }
                if (this.shot.isCompletedExceptionally()) {
                    return this.fail(ctx, "Bildschirmfoto: " + this.shot.exceptionNow());
                }
                this.count++;
                this.index++;
                this.next("go", ctx);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + this.phase);
            }
        }
        return StepResult.pending();
    }

    private StepResult fail(SelfTest.StepContext ctx, String reason) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        Screenshots.restoreWindow(ctx);
        Spot spot = this.index < this.spots.size() ? this.spots.get(this.index) : null;
        return StepResult.fail((spot == null ? "" : spot.file() + ": ") + reason);
    }

    private void next(String phase, SelfTest.StepContext ctx) {
        this.phase = phase;
        this.since = ctx.tick();
        this.stable = 0;
    }

    private static @Nullable BandoPiece piece(ServerLevel level, MinecraftServer server, Spot spot) {
        ChunkAccess chunk = level.getChunk(spot.chunk().x(), spot.chunk().z(), ChunkStatus.STRUCTURE_STARTS, true);
        StructureStart start = chunk.getStartForStructure(BandoSelfTests.holder(server, spot.family()).value());
        BandoPiece piece = start == null ? null : BandoWorldTests.piece(start);
        return piece != null && piece.variant() == spot.variant() ? piece : null;
    }

    private String view(MinecraftServer server, Spot spot) {
        ServerLevel level = server.overworld();
        BandoPiece piece = piece(level, server, spot);
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        CommandSourceStack quiet = server.createCommandSourceStack().withSource(CommandSource.NULL).withLevel(level);
        server.getCommands().performPrefixedCommand(quiet, "time set noon");
        server.getCommands().performPrefixedCommand(quiet, "weather clear");
        BoundingBox box;
        Vec3 center;
        int size;
        double top;
        if (piece != null) {
            BandoPlan plan = piece.plan();
            BandoTransform t = piece.transform();
            box = piece.getBoundingBox();
            size = Math.max(t.widthX(), t.widthZ());
            center = new Vec3(t.originX() + t.widthX() / 2.0, t.worldY(plan.ground()), t.originZ() + t.widthZ() / 2.0);
            top = Math.min(plan.sizeY(), 24) * 0.25;
        } else {
            int x = spot.chunk().getMiddleBlockX();
            int z = spot.chunk().getMiddleBlockZ();
            level.getChunk(spot.chunk().x(), spot.chunk().z());
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            box = new BoundingBox(x - 12, y - 2, z - 12, x + 12, y + 8, z + 12);
            size = 24;
            center = new Vec3(x + 0.5, y, z + 0.5);
            top = 2.0;
        }
        BandoCamera.Pose pose;
        if (spot.close() && spot.focus() != null) {
            BlockPos focus = spot.focus();
            Vec3 target = Vec3.atCenterOf(focus);
            double angle = Math.atan2(target.x - center.x, target.z - center.z);
            pose = BandoCamera.aerial(level, new BoundingBox(focus).inflatedBy(1), target, angle, 7.0, 3.0);
        } else {
            double reach = size * 0.75 + 6.0;
            double best = Double.MAX_VALUE;
            double angle = 0.0;
            for (int k = 0; k < 8; k++) {
                double a = Math.toRadians(22.5 + 45.0 * k);
                int x = (int) Math.floor(center.x + Math.sin(a) * reach);
                int z = (int) Math.floor(center.z + Math.cos(a) * reach);
                double h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (h < best) {
                    best = h;
                    angle = a;
                }
            }
            pose = BandoCamera.aerial(level, box, center.add(0.0, top, 0.0), angle, size * 0.9 + 12.0, size * 0.45 + 9.0);
        }
        player.teleportTo(level, pose.eye().x, pose.eye().y - player.getEyeHeight(), pose.eye().z, Set.of(), pose.yaw(), pose.pitch(), true);
        this.eye = pose.eye();
        return spot.file() + ": " + (piece == null ? "kein Bando mehr in Chunk " + spot.chunk() : "Bando " + piece.variant().key() + " @" + piece.getBoundingBox())
                + String.format(Locale.ROOT, ", Kamera %.1f %.1f %.1f", pose.eye().x, pose.eye().y, pose.eye().z);
    }
}
