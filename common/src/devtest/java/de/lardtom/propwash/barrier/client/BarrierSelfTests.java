package de.lardtom.propwash.barrier.client;

import de.lardtom.propwash.barrier.BarrierKind;
import de.lardtom.propwash.barrier.BarrierModule;
import de.lardtom.propwash.barrier.DroneBarrierBlock;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.ItemHelp;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.video.client.BarrierVideoProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class BarrierSelfTests {
    public static final String GROUP = "barriers: ";
    static final String FUTURE = "barrier_future";
    static final String PHASE = "barrier_phase";
    static final String MARK = "barrier_mark";
    static final String RESULT = "barrier_result";
    private static final int ENTITY_TICKS = 50;
    private static final int WALK_TICKS = 25;
    private static final int REST_TICKS = 12;
    private static final int MARKER_TICKS = 40;
    private static final int LIGHT_TICKS = 30;
    private static final double SLAB_Y = 2.0;
    private static final int ENTITY_WALL_X = 12;
    private static final int CATCH_X = 16;
    private static final int PASS_LANE_Z = 12;
    private static final int BLOCK_LANE_Z = 20;
    private static final int WALK_PASS_Z = 27;
    private static final int WALK_BLOCK_Z = 33;
    private static final int WALK_WALL_X = 4;
    private static final int SHOW_Z = -4;
    private static final float SHOW_PITCH = 2.0F;
    private static final int LIGHT_Z = 37;
    private static final int RED_Z = 41;
    private static final int VIDEO_Z = 46;
    private static final AtomicReference<List<UUID>> SPAWNED = new AtomicReference<>(List.of());

    private BarrierSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Eigenschaften wie die Vanilla-Barriere: Kreativ-Tab, unzerstörbar, ohne Beute, unsichtbar, Licht und Redstone",
                400, BarrierSelfTests::propertiesStep);
        SelfTest.clientStep(GROUP + "Durchlass hält Rüstungsständer, Schwein, Item und Pfeil auf, Sperre lässt sie durch", 600,
                BarrierSelfTests::entitiesStep);
        SelfTest.clientStep(GROUP + "Spieler läuft gegen den Durchlass und durch die Sperre, Server gleich", 600, BarrierSelfTests::walkStep);
        SelfTest.clientStep(GROUP + "Umriss im Kreativmodus auch ohne Barriere in der Hand, Symbole nur mit, im Überleben unsichtbar", 1000,
                BarrierSelfTests::visibilityStep);
        SelfTest.clientStep(GROUP + "Licht, Redstone und Videosignal gehen durch beide Barrieren", 600, BarrierSelfTests::transparencyStep);
        BarrierCourseSelfTests.register();
        BarrierFlightSelfTests.register();
        SelfTest.clientStep(GROUP + "Testplatz abbauen", 400, BarrierSelfTests::cleanupStep);
        BarrierShowcase.register();
    }

    private static StepResult propertiesStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return StepResult.pending();
        }
        List<String> problems = new ArrayList<>();
        List<ItemStack> tab = ContentTab.contents();
        Language language = Language.getInstance();
        Item.TooltipContext context = Item.TooltipContext.of(mc.level);
        for (BarrierKind kind : BarrierKind.values()) {
            DroneBarrierBlock block = BarrierModule.of(kind).block().get();
            Item item = BarrierModule.of(kind).item().get();
            BlockState state = block.defaultBlockState();
            String id = kind.path();
            if (block.kind() != kind || !BuiltInRegistries.BLOCK.getKey(block).getPath().equals(id)) {
                problems.add(id + " falsch angemeldet");
            }
            if (tab.stream().noneMatch(stack -> stack.is(item))) {
                problems.add(id + " fehlt im Kreativ-Tab");
            }
            if (state.getRenderShape() != RenderShape.INVISIBLE) {
                problems.add(id + " wird gezeichnet");
            }
            if (!state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
                problems.add(id + " hat ohne Spieler einen Umriss");
            }
            if (state.getLightDampening() != 0 || !state.propagatesSkylightDown() || state.canOcclude()) {
                problems.add(id + " schluckt Licht");
            }
            if (state.isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) || state.isSignalSource()) {
                problems.add(id + " leitet Redstone");
            }
            if (!(state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < 0.0F)) {
                problems.add(id + " abbaubar");
            }
            if (block.getExplosionResistance() < BarrierModule.EXPLOSION_RESISTANCE - 1.0F) {
                problems.add(id + " nicht sprengfest: " + block.getExplosionResistance());
            }
            if (block.getLootTable().isPresent()) {
                problems.add(id + " mit Beute-Tabelle");
            }
            if (state.getPistonPushReaction() != PushReaction.IMMOVEABLE) {
                problems.add(id + " von Kolben bewegbar");
            }
            if (state.isValidSpawn(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, EntityTypes.ZOMBIE)) {
                problems.add(id + " lässt Mobs spawnen");
            }
            if (mc.getModelManager().getItemModel(BuiltInRegistries.ITEM.getKey(item)) instanceof MissingItemModel) {
                problems.add(id + " ohne Item-Modell");
            }
            if (mc.getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite().contents().name()
                    .equals(MissingTextureAtlasSprite.getLocation())) {
                problems.add(id + " ohne Symbol für die Markierung");
            }
            if (!language.has("block.propwash." + id)) {
                problems.add(id + " ohne Namen");
            }
            List<Component> brief = new ArrayList<>();
            List<Component> detail = new ArrayList<>();
            try {
                TooltipShift.override(false);
                item.appendHoverText(new ItemStack(item), context, TooltipDisplay.DEFAULT, brief::add, TooltipFlag.NORMAL);
                TooltipShift.override(true);
                item.appendHoverText(new ItemStack(item), context, TooltipDisplay.DEFAULT, detail::add, TooltipFlag.NORMAL);
            } finally {
                TooltipShift.override(null);
            }
            if (brief.size() != 2 || !(brief.getLast().getContents() instanceof TranslatableContents last) || !ItemHelp.SHIFT_KEY.equals(last.getKey())) {
                problems.add(id + ": Tooltip ohne Kurzzeile und Shift-Hinweis");
            }
            if (detail.size() < 6) {
                problems.add(id + ": Shift-Tooltip mit nur " + detail.size() + " Zeilen");
            }
            for (Component line : detail) {
                if (line.getContents() instanceof TranslatableContents translatable && !language.has(translatable.getKey())) {
                    problems.add(id + ": fehlende Übersetzung " + translatable.getKey());
                }
            }
            if (state.getDestroyProgress(mc.player, mc.level, BlockPos.ZERO) != 0.0F) {
                problems.add(id + " im Überleben abbaubar");
            }
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        ctx.log("beide Barrieren: im Kreativ-Tab, unsichtbar, ohne Umriss ohne Spieler, Licht 0, kein Redstone-Leiter, Härte -1, sprengfest, "
                + "ohne Beute, Kolben unbeweglich, kein Spawn, Symbol, Name und Tooltip");
        return StepResult.pass();
    }

    private static StepResult entitiesStep(SelfTest.StepContext ctx) {
        StepResult waiting = BarrierSite.await(ctx, FUTURE, "Server");
        if (waiting != null) {
            return waiting;
        }
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                ctx.state().put(FUTURE, ctx.onServer(BarrierSelfTests::spawnEntities));
                ctx.state().put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                ctx.state().put(MARK, ctx.tick());
                ctx.state().put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (ctx.tick() - (int) ctx.state().get(MARK) < ENTITY_TICKS) {
                    return StepResult.pending();
                }
                AtomicReference<String> result = new AtomicReference<>();
                ctx.state().put(RESULT, result);
                ctx.state().put(FUTURE, ctx.onServer(server -> result.set(checkEntities(server))));
                ctx.state().put(PHASE, 3);
                return StepResult.pending();
            }
            default -> {
                @SuppressWarnings("unchecked")
                String result = ((AtomicReference<String>) ctx.state().get(RESULT)).get();
                if (result.startsWith("FEHLER ")) {
                    return StepResult.fail(result.substring(7));
                }
                ctx.log(result);
                return StepResult.pass();
            }
        }
    }

    private static void spawnEntities(MinecraftServer server) {
        BlockPos o = BarrierSite.prepare(server);
        ServerLevel level = BarrierSite.player(server).level();
        List<UUID> ids = new ArrayList<>();
        for (BarrierKind kind : BarrierKind.values()) {
            int z = kind == BarrierKind.DRONE_PASS ? PASS_LANE_Z : BLOCK_LANE_Z;
            BlockState barrier = BarrierSite.state(kind);
            BarrierSite.fill(level, o.offset(2, (int) SLAB_Y, z - 2), o.offset(6, (int) SLAB_Y, z + 2), barrier);
            BarrierSite.fill(level, o.offset(ENTITY_WALL_X, 0, z - 2), o.offset(ENTITY_WALL_X, 4, z + 2), barrier);
            BarrierSite.fill(level, o.offset(CATCH_X, 0, z - 2), o.offset(CATCH_X, 4, z + 2), Blocks.STONE.defaultBlockState());
            ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
            Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
            if (stand == null || pig == null) {
                throw new IllegalStateException("Rüstungsständer oder Schwein nicht erzeugt");
            }
            AttributeInstance speed = pig.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(0.0);
            }
            stand.snapTo(o.getX() + 3.5, o.getY() + SLAB_Y + 2.5, o.getZ() + z - 0.5, 0.0F, 0.0F);
            pig.snapTo(o.getX() + 4.5, o.getY() + SLAB_Y + 2.5, o.getZ() + z + 0.5, 0.0F, 0.0F);
            ItemEntity item = new ItemEntity(level, o.getX() + 5.5, o.getY() + SLAB_Y + 2.5, o.getZ() + z + 1.5, new ItemStack(Items.APPLE), 0.0, 0.0, 0.0);
            item.setNeverPickUp();
            Arrow arrow = new Arrow(level, o.getX() + ENTITY_WALL_X - 3.5, o.getY() + 2.5, o.getZ() + z + 0.5, new ItemStack(Items.ARROW), null);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.shoot(1.0, 0.0, 0.0, 1.5F, 0.0F);
            for (Entity entity : List.of(stand, pig, item, arrow)) {
                level.addFreshEntity(entity);
                ids.add(entity.getUUID());
            }
        }
        SPAWNED.set(List.copyOf(ids));
    }

    private static String checkEntities(MinecraftServer server) {
        BlockPos o = BarrierSite.origin();
        ServerLevel level = BarrierSite.player(server).level();
        List<String> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<UUID> ids = SPAWNED.getAndSet(List.of());
        try {
            if (ids.size() != 8) {
                return "FEHLER " + ids.size() + " statt 8 Entitys";
            }
            double top = o.getY() + SLAB_Y + 1.0;
            double wall = o.getX() + ENTITY_WALL_X;
            for (int i = 0; i < ids.size(); i++) {
                Entity entity = level.getEntity(ids.get(i));
                boolean pass = i < 4;
                String lane = pass ? "Durchlass" : "Sperre";
                if (entity == null) {
                    problems.add(lane + ": Entity " + i + " verschwunden");
                    continue;
                }
                String name = entity.getType().getDescription().getString();
                if (entity instanceof Arrow) {
                    double front = entity.getX();
                    if (pass && !(front < wall && front > wall - 1.5)) {
                        problems.add(String.format(Locale.ROOT, "%s: Pfeil nicht an der Wand aufgehalten, x %.2f, Wand %.0f", lane, front, wall));
                    } else if (!pass && !(front > wall + 1.0)) {
                        problems.add(String.format(Locale.ROOT, "%s: Pfeil nicht durchgeflogen, x %.2f, Wand %.0f", lane, front, wall));
                    }
                    notes.add(String.format(Locale.ROOT, "%s Pfeil x %+.2f", lane, front - wall));
                    continue;
                }
                double y = entity.getY();
                if (pass && Math.abs(y - top) > 0.15) {
                    problems.add(String.format(Locale.ROOT, "%s: %s liegt nicht auf der Barriere, y %.2f statt %.2f", lane, name, y, top));
                } else if (!pass && Math.abs(y - o.getY()) > 0.15) {
                    problems.add(String.format(Locale.ROOT, "%s: %s nicht durchgefallen, y %.2f statt %d", lane, name, y, o.getY()));
                }
                notes.add(String.format(Locale.ROOT, "%s %s y %+.2f", lane, name, y - o.getY()));
            }
        } finally {
            for (UUID id : ids) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
        return problems.isEmpty() ? String.join(", ", notes) : "FEHLER " + String.join("; ", problems);
    }

    private static StepResult walkStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        StepResult waiting = BarrierSite.await(ctx, FUTURE, "Server");
        if (waiting != null) {
            return waiting;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.pending();
        }
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        int lane = phase < 10 ? 0 : 1;
        BarrierKind kind = lane == 0 ? BarrierKind.DRONE_PASS : BarrierKind.DRONE_BLOCK;
        int step = phase % 10;
        BlockPos o = BarrierSite.originOrNull();
        switch (step) {
            case 0 -> {
                ctx.state().put(FUTURE, ctx.onServer(server -> placeWalker(server, kind)));
                ctx.state().put(PHASE, phase + 1);
                ctx.state().put(MARK, ctx.tick());
                return StepResult.pending();
            }
            case 1 -> {
                if (o == null) {
                    return StepResult.fail("kein Testplatz");
                }
                Vec3 start = walkStart(o, kind);
                if (player.position().distanceTo(start) > 0.2 || !player.onGround()) {
                    return ctx.tick() - (int) ctx.state().get(MARK) > BarrierSite.SYNC_TICKS
                            ? StepResult.fail("Spieler nicht an den Start gesetzt: " + player.position()) : StepResult.pending();
                }
                player.setYRot(-90.0F);
                player.setXRot(0.0F);
                ctx.state().put(MARK, ctx.tick());
                ctx.state().put(PHASE, phase + 1);
                return StepResult.pending();
            }
            case 2 -> {
                int waited = ctx.tick() - (int) ctx.state().get(MARK);
                player.setYRot(-90.0F);
                mc.options.keyUp.setDown(waited < WALK_TICKS);
                if (waited < WALK_TICKS + REST_TICKS) {
                    return StepResult.pending();
                }
                mc.options.keyUp.setDown(false);
                AtomicReference<Double> serverX = new AtomicReference<>(Double.NaN);
                ctx.state().put(RESULT, serverX);
                ctx.state().put(FUTURE, ctx.onServer(server -> serverX.set(BarrierSite.player(server).getX())));
                ctx.state().put(PHASE, phase + 1);
                return StepResult.pending();
            }
            default -> {
                if (o == null) {
                    return StepResult.fail("kein Testplatz");
                }
                @SuppressWarnings("unchecked")
                double serverX = ((AtomicReference<Double>) ctx.state().get(RESULT)).get();
                double wall = o.getX() + WALK_WALL_X;
                double half = player.getBbWidth() / 2.0;
                double x = player.getX();
                String what = lane == 0 ? "Durchlass" : "Sperre";
                if (lane == 0 && !(x + half <= wall + 0.02 && x + half > wall - 0.3)) {
                    return StepResult.fail(String.format(Locale.ROOT, "%s hält den Spieler nicht auf: x %.2f, Wand %.0f", what, x, wall));
                }
                if (lane == 1 && !(x - half > wall + 1.0)) {
                    return StepResult.fail(String.format(Locale.ROOT, "Spieler kommt nicht durch die %s: x %.2f, Wand %.0f", what, x, wall));
                }
                if (Math.abs(serverX - x) > 0.3) {
                    return StepResult.fail(String.format(Locale.ROOT, "%s: Server sieht den Spieler bei x %.2f, Client bei %.2f", what, serverX, x));
                }
                ctx.state().put(lane == 0 ? "barrier_walk_pass" : "barrier_walk_block", String.format(Locale.ROOT, "%s x %+.2f (Server %+.2f)",
                        what, x - wall, serverX - wall));
                if (lane == 0) {
                    ctx.state().put(PHASE, 10);
                    return StepResult.pending();
                }
                ctx.log("Spieler: " + ctx.state().get("barrier_walk_pass") + " an der Wand, " + ctx.state().get("barrier_walk_block") + " dahinter");
                return StepResult.pass();
            }
        }
    }

    private static Vec3 walkStart(BlockPos o, BarrierKind kind) {
        int z = kind == BarrierKind.DRONE_PASS ? WALK_PASS_Z : WALK_BLOCK_Z;
        return new Vec3(o.getX() + 1.5, o.getY(), o.getZ() + z + 0.5);
    }

    private static void placeWalker(MinecraftServer server, BarrierKind kind) {
        BlockPos o = BarrierSite.prepare(server);
        ServerPlayer player = BarrierSite.player(server);
        ServerLevel level = player.level();
        int z = kind == BarrierKind.DRONE_PASS ? WALK_PASS_Z : WALK_BLOCK_Z;
        BarrierSite.fill(level, o.offset(WALK_WALL_X, 0, z - 1), o.offset(WALK_WALL_X, 2, z + 1), BarrierSite.state(kind));
        BarrierSite.fill(level, o.offset(WALK_WALL_X + 4, 0, z - 1), o.offset(WALK_WALL_X + 4, 2, z + 1), Blocks.STONE.defaultBlockState());
        player.setGameMode(GameType.CREATIVE);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        BarrierSite.teleport(player, walkStart(o, kind), -90.0F, 0.0F);
    }

    private static StepResult visibilityStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        StepResult waiting = BarrierSite.await(ctx, FUTURE, "Server");
        if (waiting != null) {
            return waiting;
        }
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return StepResult.pending();
        }
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        Hand[] hands = Hand.values();
        int index = phase / 10;
        if (index >= hands.length) {
            ctx.log(String.valueOf(ctx.state().get("barrier_seen")));
            return StepResult.pass();
        }
        Hand hand = hands[index];
        BlockPos o = BarrierSite.originOrNull();
        switch (phase % 10) {
            case 0 -> {
                ctx.state().put(FUTURE, ctx.onServer(server -> holdForVisibility(server, hand)));
                ctx.state().put(MARK, ctx.tick());
                ctx.state().put(PHASE, phase + 1);
                return StepResult.pending();
            }
            case 1 -> {
                boolean ready = o != null && player.isCreative() == hand.creative && ItemStack.isSameItem(player.getMainHandItem(), hand.stack())
                        && player.position().distanceTo(showStand(o)) < 0.2;
                if (!ready) {
                    return ctx.tick() - (int) ctx.state().get(MARK) > BarrierSite.SYNC_TICKS
                            ? StepResult.fail(hand.label + ": Ausgangslage kommt nicht an") : StepResult.pending();
                }
                Map<BarrierKind, Long> before = new EnumMap<>(BarrierKind.class);
                for (BarrierKind kind : BarrierKind.values()) {
                    before.put(kind, BarrierMarkers.spawned(kind));
                }
                ctx.state().put("barrier_before", before);
                ctx.state().put(MARK, ctx.tick());
                ctx.state().put(PHASE, phase + 1);
                return StepResult.pending();
            }
            default -> {
                if (ctx.tick() - (int) ctx.state().get(MARK) < MARKER_TICKS || o == null) {
                    return StepResult.pending();
                }
                @SuppressWarnings("unchecked")
                Map<BarrierKind, Long> before = (Map<BarrierKind, Long>) ctx.state().get("barrier_before");
                List<String> counts = new ArrayList<>();
                for (BarrierKind kind : BarrierKind.values()) {
                    long markers = BarrierMarkers.spawned(kind) - before.get(kind);
                    BlockPos pos = showPos(o, kind);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof DroneBarrierBlock)) {
                        return StepResult.fail(hand.label + ": " + kind.path() + " fehlt beim Client");
                    }
                    boolean outline = !state.getShape(level, pos, CollisionContext.of(player)).isEmpty();
                    Vec3 eye = player.getEyePosition();
                    BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                    boolean aimed = hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
                    boolean crosshair = mc.hitResult instanceof BlockHitResult target && target.getType() == HitResult.Type.BLOCK
                            && target.getBlockPos().equals(pos);
                    boolean crosshairWrong = kind == BarrierKind.DRONE_PASS && crosshair != hand.outline;
                    if (hand.markers ? markers <= 0 : markers != 0) {
                        return StepResult.fail(String.format(Locale.ROOT, "%s: %s Symbole %d, erwartet %s", hand.label, kind.path(), markers,
                                hand.markers ? "welche" : "keine"));
                    }
                    if (outline != hand.outline || aimed != hand.outline || crosshairWrong) {
                        return StepResult.fail(String.format(Locale.ROOT, "%s: %s Umriss %s, anvisierbar %s, Fadenkreuz %s, erwartet %s", hand.label,
                                kind.path(), outline, aimed, crosshair, hand.outline ? "Umriss" : "unsichtbar"));
                    }
                    counts.add(kind.path() + " " + markers);
                }
                String seen = (String) ctx.state().getOrDefault("barrier_seen", "");
                ctx.state().put("barrier_seen", (seen.isEmpty() ? "" : seen + "; ") + hand.label + ": Umriss " + (hand.outline ? "ja" : "nein")
                        + ", Symbole " + String.join(", ", counts));
                ctx.state().put(PHASE, (index + 1) * 10);
                return StepResult.pending();
            }
        }
    }

    private enum Hand {
        EMPTY("Kreativ, leere Hand", true, false, null),
        PASS("Kreativ, Durchlass in der Hand", true, true, BarrierKind.DRONE_PASS),
        BLOCK("Kreativ, Sperre in der Hand", true, true, BarrierKind.DRONE_BLOCK),
        STONE("Kreativ, Stein in der Hand", true, false, null),
        SURVIVAL("Überleben, Durchlass in der Hand", false, false, BarrierKind.DRONE_PASS),
        SURVIVAL_EMPTY("Überleben, leere Hand", false, false, null);

        final String label;
        final boolean creative;
        final boolean markers;
        final boolean outline;
        final @Nullable BarrierKind kind;

        Hand(String label, boolean creative, boolean markers, @Nullable BarrierKind kind) {
            this.label = label;
            this.creative = creative;
            this.markers = markers;
            this.outline = creative;
            this.kind = kind;
        }

        ItemStack stack() {
            if (this.kind != null) {
                return BarrierSite.stack(this.kind);
            }
            return this == STONE ? new ItemStack(Items.STONE) : ItemStack.EMPTY;
        }
    }

    private static Vec3 showStand(BlockPos o) {
        return new Vec3(o.getX() + 0.5, o.getY(), o.getZ() + SHOW_Z + 0.5);
    }

    private static BlockPos showPos(BlockPos o, BarrierKind kind) {
        return o.offset(3, 1, kind == BarrierKind.DRONE_PASS ? SHOW_Z : SHOW_Z + 2);
    }

    private static BlockPos showColumn(BlockPos o, BarrierKind kind) {
        return o.offset(6, 0, kind == BarrierKind.DRONE_PASS ? SHOW_Z - 2 : SHOW_Z + 3);
    }

    private static void holdForVisibility(MinecraftServer server, Hand hand) {
        BlockPos o = BarrierSite.prepare(server);
        ServerPlayer player = BarrierSite.player(server);
        ServerLevel level = player.level();
        for (BarrierKind kind : BarrierKind.values()) {
            BarrierSite.set(level, showPos(o, kind), BarrierSite.state(kind));
            BarrierSite.fill(level, showColumn(o, kind), showColumn(o, kind).above(3), BarrierSite.state(kind));
        }
        player.setGameMode(hand.creative ? GameType.CREATIVE : GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.MAINHAND, hand.stack());
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        BarrierSite.teleport(player, showStand(o), -90.0F, SHOW_PITCH);
    }

    private static StepResult transparencyStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        StepResult waiting = BarrierSite.await(ctx, FUTURE, "Server");
        if (waiting != null) {
            return waiting;
        }
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        switch (phase) {
            case 0 -> {
                ctx.state().put(FUTURE, ctx.onServer(BarrierSelfTests::buildTransparency));
                ctx.state().put(PHASE, 1);
                ctx.state().put(MARK, ctx.tick());
                return StepResult.pending();
            }
            case 1 -> {
                if (ctx.tick() - (int) ctx.state().get(MARK) < LIGHT_TICKS) {
                    return StepResult.pending();
                }
                AtomicReference<String> result = new AtomicReference<>();
                ctx.state().put(RESULT, result);
                ctx.state().put(FUTURE, ctx.onServer(server -> result.set(checkLightAndRedstone(server))));
                ctx.state().put(PHASE, 2);
                return StepResult.pending();
            }
            default -> {
                @SuppressWarnings("unchecked")
                String server = ((AtomicReference<String>) ctx.state().get(RESULT)).get();
                if (server.startsWith("FEHLER ")) {
                    return StepResult.fail(server.substring(7));
                }
                ClientLevel level = mc.level;
                BlockPos o = BarrierSite.originOrNull();
                if (level == null || o == null) {
                    return StepResult.fail("keine Welt");
                }
                List<String> video = new ArrayList<>();
                double stone = Double.NaN;
                for (int lane = 0; lane < 3; lane++) {
                    int x = 2 + lane * 6;
                    if (!level.getBlockState(o.offset(x + 1, 2, VIDEO_Z)).is(lane == 2 ? Blocks.STONE : lane == 0
                            ? BarrierModule.DRONE_PASS.block().get() : BarrierModule.DRONE_BLOCK.block().get())) {
                        return ctx.tick() - (int) ctx.state().get(MARK) > 400 ? StepResult.fail("Wände für das Videosignal fehlen beim Client")
                                : StepResult.pending();
                    }
                    Vec3 from = new Vec3(o.getX() + x - 1.5, o.getY() + 2.5, o.getZ() + VIDEO_Z + 0.5);
                    Vec3 to = new Vec3(o.getX() + x + 4.5, o.getY() + 2.5, o.getZ() + VIDEO_Z + 0.5);
                    double loss = BarrierVideoProbe.lossDb(level, from, to);
                    if (lane < 2 && loss > 0.01) {
                        return StepResult.fail(String.format(Locale.ROOT, "%s dämpft das Videosignal um %.2f dB",
                                lane == 0 ? "Durchlass" : "Sperre", loss));
                    }
                    if (lane == 2) {
                        stone = loss;
                    }
                    video.add(String.format(Locale.ROOT, "%s %.2f dB", lane == 0 ? "Durchlass" : lane == 1 ? "Sperre" : "Stein", loss));
                }
                if (!(stone > 3.0)) {
                    return StepResult.fail(String.format(Locale.ROOT, "Gegenprobe: 3 Blöcke Stein dämpfen nur %.2f dB", stone));
                }
                ctx.log(server + "; Videosignal durch 3 Blöcke: " + String.join(", ", video));
                return StepResult.pass();
            }
        }
    }

    private static void buildTransparency(MinecraftServer server) {
        BlockPos o = BarrierSite.prepare(server);
        ServerPlayer player = BarrierSite.player(server);
        ServerLevel level = player.level();
        player.setGameMode(GameType.CREATIVE);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        List<BlockState> materials = List.of(BarrierSite.state(BarrierKind.DRONE_PASS), BarrierSite.state(BarrierKind.DRONE_BLOCK),
                Blocks.STONE.defaultBlockState());
        for (int lane = 0; lane < 3; lane++) {
            BlockState material = materials.get(lane);
            int x = 2 + lane * 6;
            BarrierSite.fill(level, o.offset(x - 1, 4, LIGHT_Z - 1), o.offset(x + 1, 4, LIGHT_Z + 1), material);
            BarrierSite.fill(level, o.offset(x, 0, VIDEO_Z - 1), o.offset(x + 2, 4, VIDEO_Z + 1), material);
            int z = RED_Z + lane;
            BarrierSite.set(level, o.offset(x, 0, z), Blocks.REDSTONE_BLOCK.defaultBlockState());
            BarrierSite.set(level, o.offset(x + 1, 1, z), material);
            BarrierSite.set(level, o.offset(x + 2, 0, z), Blocks.STONE.defaultBlockState());
            BarrierSite.set(level, o.offset(x + 1, 0, z), Blocks.REDSTONE_WIRE.defaultBlockState());
            BarrierSite.set(level, o.offset(x + 2, 1, z), Blocks.REDSTONE_WIRE.defaultBlockState());
        }
        BarrierSite.teleport(player, Vec3.atBottomCenterOf(o.offset(-2, 0, VIDEO_Z - 4)), -90.0F, 10.0F);
    }

    private static String checkLightAndRedstone(MinecraftServer server) {
        BlockPos o = BarrierSite.origin();
        ServerLevel level = BarrierSite.player(server).level();
        List<String> notes = new ArrayList<>();
        int stoneLight = -1;
        int stonePower = -1;
        for (int lane = 0; lane < 3; lane++) {
            int x = 2 + lane * 6;
            String what = lane == 0 ? "Durchlass" : lane == 1 ? "Sperre" : "Stein";
            int light = level.getBrightness(LightLayer.SKY, o.offset(x, 1, LIGHT_Z));
            BlockState upper = level.getBlockState(o.offset(x + 2, 1, RED_Z + lane));
            int power = upper.is(Blocks.REDSTONE_WIRE) ? upper.getValue(RedstoneWireBlock.POWER) : -1;
            if (lane < 2 && light != 15) {
                return "FEHLER " + what + " als Dach: Himmelslicht darunter " + light + " statt 15";
            }
            if (lane < 2 && power <= 0) {
                return "FEHLER " + what + " über dem Redstone trennt die Stufe nach oben: Stärke oben " + power;
            }
            if (lane == 2) {
                stoneLight = light;
                stonePower = power;
            }
            notes.add(what + " Licht " + light + ", Redstone oben " + power);
        }
        if (stoneLight >= 15 || stonePower != 0) {
            return "FEHLER Gegenprobe mit Stein greift nicht: Licht " + stoneLight + ", Redstone oben " + stonePower;
        }
        return String.join(", ", notes);
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyUp.setDown(false);
        BarrierFlightSelfTests.finish();
        if (ctx.tick() == 0) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                BarrierFlightSelfTests.discard(server);
                for (UUID id : SPAWNED.getAndSet(List.of())) {
                    Entity entity = BarrierSite.player(server).level().getEntity(id);
                    if (entity != null) {
                        entity.discard();
                    }
                }
                if (BarrierSite.originOrNull() != null) {
                    BarrierSite.restore(server);
                }
            }));
            return StepResult.pending();
        }
        StepResult waiting = BarrierSite.await(ctx, FUTURE, "Aufräumen");
        if (waiting != null) {
            return waiting;
        }
        return StepResult.pass();
    }
}
