package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryFit;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.render.AccessoryFit;
import de.lardtom.propwash.drone.client.render.DroneAssembly;
import de.lardtom.propwash.drone.client.render.DroneRenderResources;
import de.lardtom.propwash.drone.client.render.DroneVisuals;
import de.lardtom.propwash.drone.client.render.VisualHull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class AccessoryFitSelfTests {
    public static final String GROUP = "addon-api-17: Zubehör am Rahmen – ";
    static final Identifier MOB_DEFENSE_DISPENSER = Identifier.fromNamespaceAndPath("mobdefense", "dispenser_mount");
    static final Identifier CINE_FRAME = Identifier.fromNamespaceAndPath("camdrones", "cine_frame");
    static final Identifier JMP_DEADCAT = Identifier.fromNamespaceAndPath("justmoreparts", "deadcat_5");
    private static final long BUDGET_NANOS = 40_000_000L;
    private static final int OFFSET_X = 40;
    private static final int OFFSET_Z = 60;
    private static final double LIFT = 1.5;
    private static final int FOV = 30;
    private static final double RANGE = 1.7;
    private static final double MIN_RANGE = 0.35;
    private static final int SETTLE_TICKS = 10;
    private static final int CAMERA_TICKS = 60;
    private static final String FUTURE = "future";
    private static final String CURSOR = "cursor";
    private static final String TALLY = "tally";
    private static final AtomicReference<@Nullable BlockPos> BASE = new AtomicReference<>();
    private static final AtomicReference<@Nullable GameType> MODE = new AtomicReference<>();
    private static final Set<Integer> DRONES = ConcurrentHashMap.newKeySet();
    private static int savedFov = -1;

    private AccessoryFitSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Zubehör vorne an jedem Rahmen: Abstand höchstens 1 mm, keine tiefe Überschneidung, Ausreißer mit Soll-Anker",
                1200, ctx -> measureStep(ctx, List.of(AccessoryMount.FRONT), true));
        SelfTest.clientStep(GROUP + "Zubehör unten und oben an jedem Rahmen: Abstand höchstens 1 mm, Überschneidung gemessen",
                1200, ctx -> measureStep(ctx, List.of(AccessoryMount.BOTTOM, AccessoryMount.TOP), false));
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + "Platz vorbereiten", 400, AccessoryFitSelfTests::prepareStep);
            Screenshots.optionalStep(GROUP + "Bildschirmfoto Dispenser an der 6S-Kamera-Drohne (cine_frame aus dem Kamera-Add-on)",
                    "accessory_fit_camdrones_cine", 30, () -> FrameType.byId(CINE_FRAME).isPresent() && dispenser().isPresent(), new Scene(0));
            Screenshots.step(GROUP + "Bildschirmfoto Dispenser am race_5", "accessory_fit_race", 30, new Scene(1));
            Screenshots.step(GROUP + "Bildschirmfoto Dispenser am freestyle_5", "accessory_fit_freestyle", 30, new Scene(2));
            Screenshots.step(GROUP + "Bildschirmfoto Dispenser am cinewhoop_3", "accessory_fit_cinewhoop", 30, new Scene(3));
            Screenshots.step(GROUP + "Bildschirmfoto Dispenser am long_range_7", "accessory_fit_long_range", 30, new Scene(4));
            Screenshots.optionalStep(GROUP + "Bildschirmfoto Dispenser am deadcat_5 aus Just More Parts", "accessory_fit_jmp_deadcat", 30,
                    () -> FrameType.byId(JMP_DEADCAT).isPresent() && dispenser().isPresent(), new Scene(5));
            Screenshots.optionalStep(GROUP + "Bildschirmfoto eingerückter Werfer am cine_frame aus dem Kamera-Add-on", "accessory_recess_camdrones_cine", 30,
                    () -> FrameType.byId(CINE_FRAME).isPresent() && recessed().isPresent(), new Scene(0, AccessoryFitSelfTests::recessed));
            Screenshots.optionalStep(GROUP + "Bildschirmfoto eingerückter Werfer am race_5", "accessory_recess_race", 30,
                    () -> recessed().isPresent(), new Scene(1, AccessoryFitSelfTests::recessed));
            Screenshots.optionalStep(GROUP + "Bildschirmfoto eingerückter Werfer am freestyle_5", "accessory_recess_freestyle", 30,
                    () -> recessed().isPresent(), new Scene(2, AccessoryFitSelfTests::recessed));
            Screenshots.optionalStep(GROUP + "Bildschirmfoto eingerückter Werfer am cinewhoop_3", "accessory_recess_cinewhoop", 30,
                    () -> recessed().isPresent(), new Scene(3, AccessoryFitSelfTests::recessed));
            Screenshots.optionalStep(GROUP + "Bildschirmfoto eingerückter Werfer am long_range_7", "accessory_recess_long_range", 30,
                    () -> recessed().isPresent(), new Scene(4, AccessoryFitSelfTests::recessed));
            Screenshots.optionalStep(GROUP + "Bildschirmfoto eingerückter Werfer am deadcat_5 aus Just More Parts", "accessory_recess_jmp_deadcat", 30,
                    () -> FrameType.byId(JMP_DEADCAT).isPresent() && recessed().isPresent(), new Scene(5, AccessoryFitSelfTests::recessed));
            SelfTest.clientStep(GROUP + "Aufräumen", 300, AccessoryFitSelfTests::cleanupStep);
        }
        AccessoryRecessSelfTests.register();
    }

    record Pair(FrameType frame, AccessoryType type, AccessoryMount mount, boolean probe) {
        String label() {
            return this.frame.id() + " + " + this.type.id();
        }
    }

    static final class Tally {
        final List<String> coreMisses = new ArrayList<>();
        final List<String> addonMisses = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        int core;
        int addon;
        float worstCoreGap;
        float worstCoreOverlap;
    }

    static List<AccessoryType> probes() {
        List<AccessoryType> out = new ArrayList<>();
        for (double[] size : new double[][]{{62.0, 58.0, 64.0}, {12.0, 10.0, 20.0}}) {
            Identifier id = Identifier.fromNamespaceAndPath("propwash_fittest", "front_" + (int) size[0] + "_" + (int) size[1] + "_" + (int) size[2]);
            out.add((AccessoryType) PartIndex.detached(AccessoryDefinition.builder(id).basics(PartBasics.of(100.0)).slot(AccessorySlot.FRONT)
                    .size(size[0], size[1], size[2]).frontalArea(0.001).build()));
        }
        out.add((AccessoryType) PartIndex.detached(AccessoryDefinition.builder(Identifier.fromNamespaceAndPath("propwash_fittest", "front_recessed_62_58_64"))
                .basics(PartBasics.of(100.0)).slot(AccessorySlot.FRONT).size(62.0, 58.0, 64.0).frontalArea(0.001)
                .recess(ExampleDispenser.RECESS_MM).drop(ExampleDispenser.DROP_MM).build()));
        return out;
    }

    static List<Pair> pairs(List<AccessoryMount> mounts) {
        List<Pair> out = new ArrayList<>();
        for (FrameType frame : FrameType.values()) {
            if (frame.missing()) {
                continue;
            }
            for (AccessoryMount mount : mounts) {
                if (!frame.accessoryMounts.contains(mount)) {
                    continue;
                }
                for (AccessoryType type : AccessoryType.values()) {
                    if (!type.missing() && type.fits(mount) && type.fitsFrame(frame)) {
                        out.add(new Pair(frame, type, mount, false));
                    }
                }
                if (mount == AccessoryMount.FRONT && frame.builtin()) {
                    for (AccessoryType probe : probes()) {
                        out.add(new Pair(frame, probe, mount, true));
                    }
                }
            }
        }
        return out;
    }

    static DroneBuild base(FrameType frame) {
        DroneBuild nearest = DroneBuild.FREESTYLE;
        for (DroneBuild preset : DroneBuild.PRESETS) {
            if (preset.frame() == frame) {
                return preset.withoutAccessories();
            }
            if (Math.abs(preset.frame().wheelbaseMm - frame.wheelbaseMm) < Math.abs(nearest.frame().wheelbaseMm - frame.wheelbaseMm)) {
                nearest = preset;
            }
        }
        List<BatteryType> batteries = BatteryFit.fitting(frame);
        BatteryType battery = batteries.isEmpty() ? nearest.battery() : batteries.getFirst();
        return new DroneBuild(frame, nearest.stack(), nearest.motor(), nearest.prop(), nearest.video(), battery);
    }

    @SuppressWarnings("unchecked")
    private static StepResult measureStep(SelfTest.StepContext ctx, List<AccessoryMount> mounts, boolean front) {
        Map<String, Object> state = ctx.state();
        List<Pair> pairs = (List<Pair>) state.computeIfAbsent("pairs", key -> pairs(mounts));
        int[] cursor = (int[]) state.computeIfAbsent(CURSOR, key -> new int[]{0});
        Tally tally = (Tally) state.computeIfAbsent(TALLY, key -> new Tally());
        if (pairs.isEmpty()) {
            return front ? StepResult.fail("keine Rahmen mit Halterung vorne") : StepResult.pass();
        }
        DroneRenderResources res = DroneRenderResources.INSTANCE;
        long start = System.nanoTime();
        while (cursor[0] < pairs.size() && System.nanoTime() - start < BUDGET_NANOS) {
            Pair pair = pairs.get(cursor[0]++);
            DroneAssembly assembly = res.assembly(base(pair.frame()).withAccessory(pair.type()));
            AccessoryFit.Fit fit = AccessoryFit.measure(assembly, pair.mount(), front, pair.probe());
            AccessoryAnchor anchor = assembly.accessory(pair.mount()).anchor();
            if (fit == null || anchor == null) {
                tally.notes.add(pair.label() + ": Zubehör nicht in der Baugruppe");
                continue;
            }
            if (!same(anchor, AccessoryAnchor.of(pair.frame(), pair.mount()))) {
                tally.notes.add(pair.label() + ": Anker im Modell " + anchor + " ≠ Anker im Flugmodell " + AccessoryAnchor.of(pair.frame(), pair.mount()));
            }
            String line = String.format(Locale.ROOT, "%s (%s): %s", pair.label(), pair.mount().key(), fit.describe(anchor));
            if (pair.frame().builtin()) {
                tally.core++;
                tally.worstCoreGap = Math.max(tally.worstCoreGap, fit.gapMm());
                tally.worstCoreOverlap = Math.max(tally.worstCoreOverlap, fit.overlapMm());
                if (front ? !fit.ok() : fit.gapMm() > AccessoryFit.MAX_GAP_MM) {
                    tally.coreMisses.add(line);
                }
            } else {
                tally.addon++;
                if (!fit.ok()) {
                    tally.addonMisses.add(line);
                }
            }
            ctx.log((fit.ok() ? "am Rahmen: " : "AUSREISSER: ") + line);
        }
        if (cursor[0] < pairs.size()) {
            return StepResult.pending();
        }
        for (String note : tally.notes) {
            ctx.log("Hinweis: " + note);
        }
        String summary = String.format(Locale.ROOT, "%d Paare an eingebauten Rahmen (größter Abstand %.1f mm, größte Überschneidung %.1f mm, %d außerhalb), "
                        + "%d an Add-on-Rahmen (%d Ausreißer)", tally.core, tally.worstCoreGap, tally.worstCoreOverlap, tally.coreMisses.size(), tally.addon,
                tally.addonMisses.size());
        ctx.log(summary);
        for (String miss : tally.addonMisses) {
            ctx.log("Add-on-Befund: " + miss);
        }
        if (front && tally.core == 0) {
            return StepResult.fail("kein eingebauter Rahmen mit Halterung vorne gemessen");
        }
        if (!tally.coreMisses.isEmpty()) {
            return StepResult.fail("Zubehör nicht am Rahmen: " + String.join("; ", tally.coreMisses));
        }
        return StepResult.pass();
    }

    static boolean same(AccessoryAnchor a, AccessoryAnchor b) {
        double eps = 1.0E-3;
        return Math.abs(a.x() - b.x()) < eps && Math.abs(a.y() - b.y()) < eps && Math.abs(a.z() - b.z()) < eps
                && Math.abs(a.tiltDegrees() - b.tiltDegrees()) < eps && Math.abs(a.lengthMm() - b.lengthMm()) < eps;
    }

    static Optional<AccessoryType> dispenser() {
        Optional<AccessoryType> mobDefense = AccessoryType.byId(MOB_DEFENSE_DISPENSER);
        return mobDefense.isPresent() ? mobDefense : AccessoryType.byId(ExampleDispenser.DISPENSER);
    }

    static Optional<AccessoryType> recessed() {
        return AccessoryType.byId(ExampleDispenser.RECESSED_DISPENSER);
    }

    static @Nullable FrameType sceneFrame(int index) {
        return switch (index) {
            case 0 -> FrameType.byId(CINE_FRAME).orElse(null);
            case 1 -> FrameType.RACE_5;
            case 2 -> FrameType.FREESTYLE_5;
            case 3 -> FrameType.CINEWHOOP_3;
            case 4 -> FrameType.LONG_RANGE_7;
            default -> FrameType.byId(JMP_DEADCAT).orElse(null);
        };
    }

    private static StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": keine Server-Aktion");
        }
        if (future.isCompletedExceptionally()) {
            Throwable error = future.exceptionNow();
            return StepResult.fail(what + ": " + (error.getCause() != null ? error.getCause().getMessage() : error.getMessage()));
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult prepareStep(SelfTest.StepContext ctx) {
        if (dispenser().isEmpty()) {
            ctx.log("kein Dispenser angemeldet (Beispiel-Add-on aus, Mob Defense fehlt), übersprungen");
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            Screenshots.hideMarker(true);
            savedFov = mc.options.fov().get();
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = InteractSelfTests.player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + OFFSET_X;
                int z = player.getBlockZ() + OFFSET_Z;
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BASE.set(new BlockPos(x, y, z));
                MODE.compareAndSet(null, player.gameMode());
                player.setGameMode(GameType.SPECTATOR);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.teleportTo(level, x + 0.5, y + 2.0, z + 3.5, Set.of(), 180.0F, 10.0F, true);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Platz vorbereiten");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        BlockPos base = BASE.get();
        if (mc.player == null || mc.level == null || base == null || mc.player.blockPosition().distManhattan(base) > 8) {
            return ctx.tick() > 380 ? StepResult.fail("Spieler kam nicht am Platz an") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static void discardDrones(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Integer id : DRONES) {
                if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                    drone.discard();
                }
            }
        }
        DRONES.clear();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!ctx.state().containsKey(FUTURE)) {
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Screenshots.hideMarker(false);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                discardDrones(server);
                BASE.set(null);
                GameType mode = MODE.getAndSet(null);
                if (mode != null) {
                    InteractSelfTests.player(server).setGameMode(mode);
                }
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        return Screenshots.restoreWindow(ctx);
    }

    private static void look(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
    }

    private static final class Scene implements Screenshots.Scene {
        private static final double AZIMUTH = 80.0;
        private static final double ELEVATION = 6.0;
        private final int index;
        private final Supplier<Optional<AccessoryType>> part;
        private Vec3 aim = Vec3.ZERO;

        private Scene(int index) {
            this(index, AccessoryFitSelfTests::dispenser);
        }

        private Scene(int index, Supplier<Optional<AccessoryType>> part) {
            this.index = index;
            this.part = part;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Optional<AccessoryType> dispenser = this.part.get();
            FrameType frame = sceneFrame(this.index);
            BlockPos base = BASE.get();
            if (dispenser.isEmpty() || frame == null) {
                return StepResult.fail("Dispenser oder Rahmen fehlt");
            }
            if (base == null) {
                return StepResult.fail("Platz nicht vorbereitet");
            }
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            DroneBuild build = base(frame).withAccessory(dispenser.get());
            if (!state.containsKey(FUTURE)) {
                Vec3 feet = Vec3.atBottomCenterOf(base).add(0.0, LIFT, 0.0);
                AtomicReference<Integer> placed = new AtomicReference<>(-1);
                state.put(FUTURE, ctx.onServer(server -> {
                    discardDrones(server);
                    ServerLevel level = InteractSelfTests.player(server).level();
                    QuadcopterEntity drone = AccessoryGapsSelfTests.park(level, build, feet);
                    drone.setNoGravity(true);
                    DRONES.add(drone.getId());
                    placed.set(drone.getId());
                }));
                state.put("placed", placed);
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Drohne setzen");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            @SuppressWarnings("unchecked")
            int id = ((AtomicReference<Integer>) state.get("placed")).get();
            if (mc.level == null || mc.player == null || !(mc.level.getEntity(id) instanceof QuadcopterEntity entity)) {
                return ctx.tick() > 200 ? StepResult.fail("Drohne kam nicht beim Client an") : StepResult.pending();
            }
            AccessoryAnchor anchor = AccessoryAnchor.of(frame, AccessoryMount.FRONT);
            if (!state.containsKey("eye")) {
                Vec3 last = (Vec3) state.put("last", entity.position());
                int still = last != null && last.distanceTo(entity.position()) < 1.0E-4 ? (int) state.getOrDefault("still", 0) + 1 : 0;
                state.put("still", still);
                if (still < SETTLE_TICKS) {
                    return ctx.tick() > 200 ? StepResult.fail("Drohne kam nicht zur Ruhe") : StepResult.pending();
                }
                VisualHull hull = DroneRenderResources.INSTANCE.assembly(build).hull();
                float radiusMm = hull.radius(hull.center(new Vector3f()));
                double radius = DroneVisuals.estimatedRadiusBlocks(build);
                double perMm = radius / Math.max(1.0F, radiusMm);
                Vector3f forward = entity.orientation().transform(new Vector3f(0.0F, 0.0F, -1.0F));
                Vector3f up = entity.orientation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
                double ahead = -anchor.z() * perMm;
                double drop = (anchor.y() - dispenser.get().heightMm / 3.0) * perMm;
                Vec3 center = entity.position().add(0.0, radius * 0.1, 0.0);
                this.aim = center.add(forward.x * ahead + up.x * drop, forward.y * ahead + up.y * drop, forward.z * ahead + up.z * drop);
                double range = Math.max(radius * RANGE, MIN_RANGE);
                double heading = Math.atan2(forward.z, forward.x) + Math.toRadians(AZIMUTH);
                double rise = Math.toRadians(ELEVATION);
                Vec3 eye = this.aim.add(Math.cos(heading) * Math.cos(rise) * range, Math.sin(rise) * range, Math.sin(heading) * Math.cos(rise) * range);
                state.put("eye", eye);
                state.put("sent", ctx.tick());
                state.put(FUTURE, ctx.onServer(srv -> {
                    ServerPlayer player = InteractSelfTests.player(srv);
                    player.teleportTo(player.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), 0.0F, 0.0F, true);
                }));
                return StepResult.pending();
            }
            Vec3 eye = (Vec3) state.get("eye");
            double off = mc.player.getEyePosition().distanceTo(eye);
            if (off > 0.1 && ctx.tick() - (int) state.get("sent") < CAMERA_TICKS) {
                return StepResult.pending();
            }
            if (off > 0.1) {
                ctx.log(String.format(Locale.ROOT, "Kamera %.2f Blöcke neben dem Ziel", off));
            }
            AccessoryFit.Fit fit = AccessoryFit.measure(DroneRenderResources.INSTANCE.assembly(build), AccessoryMount.FRONT, true, false);
            ctx.log(frame.id() + " + " + dispenser.get().id() + ": " + (fit == null ? "nicht gemessen" : fit.describe(anchor)));
            mc.options.fov().set(FOV);
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                look(mc.player, this.aim);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
        }
    }
}
