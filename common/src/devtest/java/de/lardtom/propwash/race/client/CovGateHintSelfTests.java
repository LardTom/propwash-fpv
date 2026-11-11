package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.StackGateJoinCheck;
import de.lardtom.propwash.guide.client.screen.GuideDraw;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.client.CovRaceSite.Site;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.FirstUseHints;
import de.lardtom.propwash.ui.client.LayoutProbe;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class CovGateHintSelfTests {
    public static final String GROUP = "race: Abdeckung – Gate-Hinweis ";
    private static final int OFFSET_X = -1600;
    private static final int OFFSET_Z = 1680;
    private static final int[] BOUNDS = {-16, 16, -12, 8, 10};
    private static final int WAIT_TICKS = 160;
    private static final int QUIET_TICKS = 40;
    private static final int INVENTORY_TICKS = 60;
    private static final int LONG_TICKS = GatePlaceHint.PENDING_TICKS + 20;
    private static final String NEW_SNEAK = "key.keyboard.g";
    private static final AtomicReference<Site> SITE = new AtomicReference<>();
    private static final AtomicReference<@Nullable Memory> SAVED = new AtomicReference<>();
    private static final AtomicReference<InputConstants.@Nullable Key> SNEAK = new AtomicReference<>();
    private static final List<ElementSpot> ELEMENTS = List.of(
            new ElementSpot(new BlockPos(-4, -1, 0), CourseElement.FLAG),
            new ElementSpot(new BlockPos(-1, -1, 0), CourseElement.POLE),
            new ElementSpot(new BlockPos(4, -1, 0), CourseElement.CUBE),
            new ElementSpot(new BlockPos(10, -1, 0), CourseElement.GATE_POLE));
    private static final BlockPos LOWER = new BlockPos(-11, 0, 0);
    private static final BlockPos KEYS = new BlockPos(-8, -1, -7);
    private static final BlockPos INVENTORY = new BlockPos(0, -1, -7);
    private static final BlockPos LONG = new BlockPos(8, -1, -7);

    private record Memory(@Nullable Map<String, Integer> hints, @Nullable List<String> worlds) {
    }

    private record ElementSpot(BlockPos aim, CourseElement kind) {
    }

    private CovGateHintSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "– Platz", 900, CovGateHintSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "Stapeln: erstes Gate in frischer Welt per Klick auf ein vorhandenes Gate öffnet das Fenster", 1200,
                CovGateHintSelfTests::stackStep);
        SelfTest.clientStep(GROUP + "Kurs-Elemente: Rennflagge, Slalomstange, Tauchwürfel und Gate-Stange zeigen kein Fenster", 2400,
                CovGateHintSelfTests::elementsStep);
        SelfTest.clientStep(GROUP + "eigene Belegung: Fenster zeigt die neue Schleichen-Taste statt der alten", 1200, CovGateHintSelfTests::keysStep);
        SelfTest.clientStep(GROUP + "anderes Fenster offen: Hinweis wartet hinter dem Inventar und erscheint nach dem Schließen", 1200,
                ctx -> inventoryStep(ctx, INVENTORY, INVENTORY_TICKS, true));
        SelfTest.clientStep(GROUP + "anderes Fenster länger als 10 s offen: danach kein Fenster mehr", 1200,
                ctx -> inventoryStep(ctx, LONG, LONG_TICKS, false));
        SelfTest.clientStep(GROUP + "– aufräumen", 600, CovGateHintSelfTests::cleanupStep);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult prepared = CovRaceSite.prepare(ctx, SITE, OFFSET_X, OFFSET_Z, BOUNDS, new Vec3(0.5, 0.0, 5.0));
        if (prepared.status() != StepResult.Status.PASS) {
            return prepared;
        }
        remember();
        return StepResult.pass();
    }

    private static void remember() {
        if (SAVED.get() != null) {
            return;
        }
        String player = FirstUseHints.playerKey();
        Map<String, Integer> hints = GatePlaceHint.hints().players.get(player);
        List<String> worlds = GatePlaceHint.worlds().players.get(player);
        SAVED.set(new Memory(hints == null ? null : new LinkedHashMap<>(hints), worlds == null ? null : new ArrayList<>(worlds)));
    }

    private static void restoreMemory() {
        Memory memory = SAVED.getAndSet(null);
        if (memory == null) {
            return;
        }
        String player = FirstUseHints.playerKey();
        GatePlaceHint.hints().players.remove(player);
        if (memory.hints() != null) {
            GatePlaceHint.hints().players.put(player, new LinkedHashMap<>(memory.hints()));
        }
        FirstUseHints.CONFIG.save();
        GatePlaceHint.worlds().players.remove(player);
        if (memory.worlds() != null) {
            GatePlaceHint.worlds().players.put(player, new ArrayList<>(memory.worlds()));
        }
        GatePlaceHint.WORLDS.save();
    }

    private static void fresh(String world) {
        FirstUseHints.forget(GateHintMemory.HINT);
        if (GateHintMemory.forgetWorlds(GatePlaceHint.worlds(), FirstUseHints.playerKey())) {
            GatePlaceHint.WORLDS.save();
        }
        GatePlaceHint.worldOverride(world);
        GatePlaceHint.mute(false);
    }

    private static Memory snapshot() {
        String player = FirstUseHints.playerKey();
        Map<String, Integer> hints = GatePlaceHint.hints().players.get(player);
        List<String> worlds = GatePlaceHint.worlds().players.get(player);
        return new Memory(hints == null ? null : new LinkedHashMap<>(hints), worlds == null ? null : new ArrayList<>(worlds));
    }

    private static Vec3 standFor(Site site, BlockPos aim) {
        return new Vec3(site.origin().getX() + aim.getX() + 0.5, site.origin().getY(), site.origin().getZ() + aim.getZ() + 3.5);
    }

    private static @Nullable StepResult arrive(SelfTest.StepContext ctx, String key, Vec3 stand, float yaw, Item item) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.fail("kein Spieler");
        }
        StepResult wait = CovRaceSite.once(ctx, key + ".stand", server -> {
            ServerPlayer p = CovRaceSite.player(server);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 16));
            p.connection.send(new ClientboundSetHeldSlotPacket(p.getInventory().getSelectedSlot()));
            p.teleportTo(p.level(), stand.x, stand.y, stand.z, Set.of(), yaw, 30.0F, true);
            p.resetFallDistance();
            return null;
        });
        if (wait != null) {
            return wait;
        }
        boolean there = player.position().distanceTo(stand) < 0.3 && player.getMainHandItem().is(item) && mc.gui.screen() == null;
        if (!there) {
            int since = (int) ctx.state().computeIfAbsent(key + ".arrive", k -> ctx.tick());
            return ctx.tick() - since > WAIT_TICKS ? StepResult.fail(key + ": Spieler kommt nicht an (" + player.position() + ", Hand "
                    + player.getMainHandItem() + ", Bildschirm " + mc.gui.screen() + ")") : StepResult.pending();
        }
        return null;
    }

    private static @Nullable StepResult clickFloor(SelfTest.StepContext ctx, String key, BlockPos aim) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Site site = SITE.get();
        BlockPos block = site.origin().offset(aim);
        CovRaceSite.lookAt(player, Vec3.atCenterOf(block).add(0.0, 0.5, 0.0));
        boolean aimed = mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(block)
                && hit.getDirection() == Direction.UP;
        if (!aimed) {
            int since = (int) ctx.state().computeIfAbsent(key + ".aim", k -> ctx.tick());
            return ctx.tick() - since > 40 ? StepResult.fail(key + ": Fadenkreuz trifft " + mc.hitResult + " statt " + block.toShortString())
                    : StepResult.pending();
        }
        ctx.state().put(key + ".detected", GatePlaceHint.detected());
        ctx.state().put(key + ".click", ctx.tick());
        KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
        return null;
    }

    private static @Nullable GateEntity near(Vec3 at, CourseElement kind, double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        List<GateEntity> found = mc.level.getEntitiesOfClass(GateEntity.class, new AABB(at, at).inflate(radius), gate -> !gate.isRemoved() && gate.kind() == kind);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static StepResult window(SelfTest.StepContext ctx, String key) {
        Minecraft mc = Minecraft.getInstance();
        if (GatePlaceHint.detected() <= (int) ctx.state().get(key + ".detected")) {
            return ctx.tick() - (int) ctx.state().get(key + ".click") > WAIT_TICKS ? StepResult.fail(key + ": kein neues Gate erkannt (Bildschirm "
                    + mc.gui.screen() + ")") : StepResult.pending();
        }
        if (mc.gui.screen() instanceof GateHintScreen) {
            return StepResult.pass();
        }
        int since = (int) ctx.state().computeIfAbsent(key + ".detectedAt", k -> ctx.tick());
        return ctx.tick() - since > QUIET_TICKS ? StepResult.fail(key + ": kein Fenster (Ergebnis " + GatePlaceHint.lastOutcome() + ", Bildschirm "
                + mc.gui.screen() + ")") : StepResult.pending();
    }

    private static StepResult stackStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (site == null || player == null) {
            return StepResult.fail("kein Platz");
        }
        switch (CovRaceSite.phase(ctx)) {
            case 0 -> {
                GatePlaceHint.mute(true);
                StepResult wait = CovRaceSite.once(ctx, "unteres Gate", server -> {
                    ServerPlayer p = CovRaceSite.player(server);
                    BlockPos at = site.origin().offset(LOWER);
                    GateEntity lower = StackGateJoinCheck.place(p, GateSize.MEDIUM, at.getX(), at.getY(), at.getZ(), 0.0F);
                    site.ids().put("hint_lower", lower.getId());
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                if (CovRaceSite.clientEntity(site, "hint_lower") == null) {
                    return CovRaceSite.since(ctx) > 200 ? StepResult.fail("unteres Gate kommt nicht beim Client an") : StepResult.pending();
                }
                fresh("sp_cov_race_stapeln");
                if (GatePlaceHint.never() || GateHintMemory.seen(GatePlaceHint.worlds(), FirstUseHints.playerKey(), GatePlaceHint.world(mc))) {
                    return StepResult.fail("Hinweis-Speicher nicht geleert");
                }
                CovRaceSite.next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                GateEntity lower = CovRaceSite.clientEntity(site, "hint_lower");
                if (lower == null) {
                    return StepResult.fail("unteres Gate fehlt");
                }
                Vec3 stand = new Vec3(lower.getX(), lower.getY(), lower.getZ() + 2.0);
                StepResult arrived = arrive(ctx, "stapeln", stand, 180.0F, RaceModule.GATE_MEDIUM.get());
                if (arrived != null) {
                    return arrived;
                }
                GateGeometry g = lower.geometry();
                CovRaceSite.lookAt(player, new Vec3(g.x(), g.y() + g.height() - g.frame() * 0.5, g.z()));
                if (!(mc.hitResult instanceof EntityHitResult hit) || hit.getEntity() != lower) {
                    return CovRaceSite.since(ctx) > 60 ? StepResult.fail("Fadenkreuz trifft das untere Gate nicht: " + mc.hitResult) : StepResult.pending();
                }
                ctx.state().put("stapeln.detected", GatePlaceHint.detected());
                ctx.state().put("stapeln.click", ctx.tick());
                KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
                CovRaceSite.next(ctx, 2);
                return StepResult.pending();
            }
            default -> {
                StepResult shown = window(ctx, "stapeln");
                if (shown.status() != StepResult.Status.PASS) {
                    return shown;
                }
                GateEntity lower = CovRaceSite.clientEntity(site, "hint_lower");
                GateHintScreen hint = (GateHintScreen) mc.gui.screen();
                String problem = null;
                if (lower == null) {
                    problem = "unteres Gate verschwunden";
                } else if (GatePlaceHint.lastOutcome() != GateHintMemory.Outcome.SHOW) {
                    problem = "Ergebnis " + GatePlaceHint.lastOutcome() + " statt SHOW";
                } else if (mc.level.getEntitiesOfClass(GateEntity.class, lower.getBoundingBox().inflate(1.0), gate -> gate != lower && !gate.isRemoved()
                        && gate.kind() == CourseElement.GATE && Math.abs(gate.getY() - GateStacking.seat(lower.geometry())) < 0.05
                        && Math.abs(gate.getX() - lower.getX()) < 0.05 && Math.abs(gate.getZ() - lower.getZ()) < 0.05).isEmpty()) {
                    problem = "das zweite Gate sitzt nicht auf dem unteren";
                }
                hint.confirm();
                GatePlaceHint.mute(true);
                if (problem == null) {
                    ctx.log("Gate per Klick auf ein vorhandenes Gate gestapelt: Fenster " + (ctx.tick() - (int) ctx.state().get("stapeln.click"))
                            + " Ticks nach dem Klick, Welt " + GatePlaceHint.world(mc));
                }
                return StepResult.check(problem == null, Objects.requireNonNullElse(problem, ""));
            }
        }
    }

    private static Item itemFor(CourseElement kind) {
        return switch (kind) {
            case FLAG -> RaceModule.RACE_FLAG.get();
            case POLE -> RaceModule.SLALOM_POLE.get();
            case CUBE -> RaceModule.DIVE_CUBE.get();
            default -> RaceModule.GATE_POLE.get();
        };
    }

    private static StepResult elementsStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null || mc.player == null) {
            return StepResult.fail("kein Platz");
        }
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("memory")) {
            fresh("sp_cov_race_elemente");
            state.put("memory", snapshot());
            state.put("detected", GatePlaceHint.detected());
            state.put("outcome", String.valueOf(GatePlaceHint.lastOutcome()));
        }
        int index = (int) state.getOrDefault("index", 0);
        if (index >= ELEMENTS.size()) {
            Memory before = (Memory) state.get("memory");
            Memory after = snapshot();
            GatePlaceHint.mute(true);
            if (!Objects.equals(before, after)) {
                return StepResult.fail("Hinweis-Speicher geändert: vorher " + before + ", nachher " + after);
            }
            if (GatePlaceHint.detected() != (int) state.get("detected")) {
                return StepResult.fail("Kurs-Element als Gate erkannt: " + (GatePlaceHint.detected() - (int) state.get("detected")) + "x");
            }
            ctx.log("Rennflagge, Slalomstange, Tauchwürfel und Gate-Stange per Klick gesetzt, je " + QUIET_TICKS
                    + " Ticks kein Fenster, Hinweis-Speicher unverändert");
            return StepResult.pass();
        }
        ElementSpot spot = ELEMENTS.get(index);
        String key = "element" + index;
        int phase = (int) state.getOrDefault(key + ".phase", 0);
        switch (phase) {
            case 0 -> {
                StepResult arrived = arrive(ctx, key, standFor(site, spot.aim()), 180.0F, itemFor(spot.kind()));
                if (arrived != null) {
                    return arrived;
                }
                StepResult clicked = clickFloor(ctx, key, spot.aim());
                if (clicked != null) {
                    return clicked;
                }
                state.put(key + ".phase", 1);
                return StepResult.pending();
            }
            case 1 -> {
                Vec3 at = Vec3.atCenterOf(site.origin().offset(spot.aim())).add(0.0, 0.5, 0.0);
                if (near(at, spot.kind(), 3.0) == null) {
                    return ctx.tick() - (int) state.get(key + ".click") > WAIT_TICKS ? StepResult.fail(spot.kind() + " nicht gesetzt (Bildschirm "
                            + mc.gui.screen() + ")") : StepResult.pending();
                }
                state.put(key + ".placed", ctx.tick());
                state.put(key + ".phase", 2);
                return StepResult.pending();
            }
            default -> {
                if (mc.gui.screen() instanceof GateHintScreen) {
                    mc.gui.setScreen(null);
                    GatePlaceHint.mute(true);
                    return StepResult.fail(spot.kind() + " öffnet das Gate-Fenster (Ergebnis " + GatePlaceHint.lastOutcome() + ")");
                }
                if (ctx.tick() - (int) state.get(key + ".placed") < QUIET_TICKS) {
                    return StepResult.pending();
                }
                state.put("index", index + 1);
                return StepResult.pending();
            }
        }
    }

    private static List<String> texts(Minecraft mc, GateHintScreen screen) {
        List<String> out = new ArrayList<>();
        for (LayoutAudit.Text text : LayoutProbe.capture(mc, screen).texts()) {
            out.add(text.value().trim());
        }
        return out;
    }

    private static StepResult keysStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null || mc.player == null) {
            return StepResult.fail("kein Platz");
        }
        Map<String, Object> state = ctx.state();
        KeyMapping sneak = mc.options.keyShift;
        if (!state.containsKey("bound")) {
            String old = GateHintScreen.Chip.of(sneak).text();
            SNEAK.compareAndSet(null, InputConstants.getKey(sneak.saveString()));
            sneak.setKey(InputConstants.getKey(NEW_SNEAK));
            KeyMapping.resetMapping();
            String fresh = GateHintScreen.Chip.of(sneak).text();
            if (fresh.equals(old)) {
                return StepResult.fail("neue Taste hat denselben Namen wie die alte: " + fresh);
            }
            state.put("old", old);
            state.put("new", fresh);
            fresh("sp_cov_race_belegung");
            state.put("bound", true);
        }
        int phase = (int) state.getOrDefault("keys.phase", 0);
        if (phase == 0) {
            StepResult arrived = arrive(ctx, "keys", standFor(site, KEYS), 180.0F, RaceModule.GATE_MEDIUM.get());
            if (arrived != null) {
                return arrived;
            }
            StepResult clicked = clickFloor(ctx, "keys", KEYS);
            if (clicked != null) {
                return clicked;
            }
            state.put("keys.phase", 1);
            return StepResult.pending();
        }
        StepResult shown = window(ctx, "keys");
        if (shown.status() != StepResult.Status.PASS) {
            if (shown.status() == StepResult.Status.FAIL) {
                restoreSneak();
            }
            return shown;
        }
        GateHintScreen hint = (GateHintScreen) mc.gui.screen();
        List<String> drawn = texts(mc, hint);
        String old = (String) state.get("old");
        String fresh = (String) state.get("new");
        hint.confirm();
        GatePlaceHint.mute(true);
        restoreSneak();
        if (!drawn.contains(fresh)) {
            return StepResult.fail("Fenster zeigt die neue Taste „" + fresh + "“ nicht: " + drawn);
        }
        if (drawn.contains(old)) {
            return StepResult.fail("Fenster zeigt noch die alte Taste „" + old + "“: " + drawn);
        }
        if (!GateHintScreen.Chip.of(mc.options.keyShift).text().equals(old)) {
            return StepResult.fail("Belegung nicht zurückgestellt: " + mc.options.keyShift.saveString());
        }
        ctx.log("Schleichen auf „" + fresh + "“ gelegt: Fenster zeigt „" + fresh + "“ statt „" + old + "“, Belegung danach zurück");
        return StepResult.pass();
    }

    private static void restoreSneak() {
        InputConstants.Key key = SNEAK.getAndSet(null);
        if (key != null) {
            Minecraft.getInstance().options.keyShift.setKey(key);
            KeyMapping.resetMapping();
        }
    }

    private static StepResult inventoryStep(SelfTest.StepContext ctx, BlockPos spot, int hold, boolean expectWindow) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (site == null || player == null) {
            return StepResult.fail("kein Platz");
        }
        Map<String, Object> state = ctx.state();
        String key = "inv";
        if (!state.containsKey("ready") && mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        if (!state.containsKey("ready")) {
            fresh(expectWindow ? "sp_cov_race_inventar" : "sp_cov_race_inventar_lang");
            state.put("ready", true);
        }
        int phase = (int) state.getOrDefault("inv.phase", 0);
        switch (phase) {
            case 0 -> {
                StepResult arrived = arrive(ctx, key, standFor(site, spot), 180.0F, RaceModule.GATE_MEDIUM.get());
                if (arrived != null) {
                    return arrived;
                }
                StepResult clicked = clickFloor(ctx, key, spot);
                if (clicked != null) {
                    return clicked;
                }
                state.put("inv.phase", 1);
                return StepResult.pending();
            }
            case 1 -> {
                int before = (int) state.get(key + ".detected");
                if (GatePlaceHint.detected() > before) {
                    return StepResult.fail("Gate erkannt, bevor das Inventar offen war (Test zu langsam)");
                }
                if (!GatePlaceHint.armed()) {
                    return ctx.tick() - (int) state.get(key + ".click") > 20 ? StepResult.fail("Klick mit dem Gate wird nicht erkannt") : StepResult.pending();
                }
                mc.gui.setScreen(new InventoryScreen(player));
                state.put("opened", ctx.tick());
                state.put("inv.phase", 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (mc.gui.screen() instanceof GateHintScreen) {
                    mc.gui.setScreen(null);
                    return StepResult.fail("Gate-Fenster verdrängt das offene Inventar");
                }
                if (mc.gui.screen() == null) {
                    return StepResult.fail("Inventar schließt sich von selbst");
                }
                state.putIfAbsent("screen", mc.gui.screen().getClass().getSimpleName());
                if (GatePlaceHint.detected() > (int) state.get(key + ".detected") && !state.containsKey("seen")) {
                    state.put("seen", ctx.tick() - (int) state.get("opened"));
                    state.put("outcome", String.valueOf(GatePlaceHint.lastOutcome()));
                }
                if (ctx.tick() - (int) state.get("opened") < hold) {
                    return StepResult.pending();
                }
                if (!state.containsKey("seen") || !"SHOW".equals(state.get("outcome"))) {
                    mc.gui.setScreen(null);
                    return StepResult.fail("Gate hinter dem Inventar nicht als erstes Gate erkannt: " + state.get("outcome"));
                }
                mc.gui.setScreen(null);
                state.put("closed", ctx.tick());
                state.put("inv.phase", 3);
                return StepResult.pending();
            }
            default -> {
                int since = ctx.tick() - (int) state.get("closed");
                boolean open = mc.gui.screen() instanceof GateHintScreen;
                if (expectWindow) {
                    if (open) {
                        ((GateHintScreen) mc.gui.screen()).confirm();
                        GatePlaceHint.mute(true);
                        ctx.log("Gate erkannt " + state.get("seen") + " Ticks nach dem Öffnen des Inventars (" + state.get("screen") + "), " + hold
                                + " Ticks kein Fenster, " + since + " Ticks nach dem Schließen erscheint es");
                        return StepResult.pass();
                    }
                    return since > 20 ? StepResult.fail("nach dem Schließen des Inventars kein Fenster") : StepResult.pending();
                }
                if (open) {
                    mc.gui.setScreen(null);
                    GatePlaceHint.mute(true);
                    return StepResult.fail("Fenster erscheint noch nach " + hold + " Ticks offenem Inventar (Regel: " + GatePlaceHint.PENDING_TICKS + ")");
                }
                if (since < QUIET_TICKS) {
                    return StepResult.pending();
                }
                GatePlaceHint.mute(true);
                boolean marked = GateHintMemory.seen(GatePlaceHint.worlds(), FirstUseHints.playerKey(), GatePlaceHint.world(mc));
                ctx.log("Inventar " + hold + " Ticks offen: Hinweis verfällt nach " + GatePlaceHint.PENDING_TICKS + " Ticks, nach dem Schließen "
                        + QUIET_TICKS + " Ticks kein Fenster; Welt danach als gesehen gemerkt: " + marked);
                return StepResult.pass();
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!ctx.state().containsKey("started")) {
            ctx.state().put("started", true);
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            restoreSneak();
            GatePlaceHint.mute(true);
            GatePlaceHint.worldOverride(null);
            restoreMemory();
        }
        Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = CovRaceSite.once(ctx, "Aufräumen", server -> CovRaceSite.teardown(server, site));
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        return StepResult.pass();
    }
}
