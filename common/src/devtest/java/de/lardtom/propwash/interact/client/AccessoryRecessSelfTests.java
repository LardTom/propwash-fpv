package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.accessory.MountPoses;
import de.lardtom.propwash.accessory.dispense.DispenseOrigin;
import de.lardtom.propwash.accessory.dispense.OwnShots;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.render.AccessoryFit;
import de.lardtom.propwash.drone.client.render.DroneAssembly;
import de.lardtom.propwash.drone.client.render.DroneRenderResources;
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.drone.client.render.DroneVisuals;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class AccessoryRecessSelfTests {
    public static final String GROUP = "addon-api-17: Zubehör eingerückt – ";
    private static final String FUTURE = InteractSelfTests.FUTURE;
    private static final float POSITION_TOLERANCE_MM = 0.01F;
    private static final double SHOT_TOLERANCE_M = 1.0E-3;
    private static final double FRAME_TOLERANCE_M = 1.0E-4;
    private static final double NOSE_DOWN_DEGREES = 45.0;
    private static final float MIN_HULL_SHIFT_MM = 10.0F;
    private static final List<DroneBuild> BUILDS = List.of(DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.CINEWHOOP, DroneBuild.LONG_RANGE);
    private static final AtomicReference<String> REPORT = new AtomicReference<>("");

    private AccessoryRecessSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Modell und Ersatzform sitzen um recess_mm und drop_mm versetzt am Rahmen, Hülle folgt, Anker bleibt", 300,
                ctx -> AccessoryGapsSelfTests.ready() ? modelStep(ctx) : skipped(ctx));
        SelfTest.clientStep(GROUP + "Schuss kommt aus der eingerückten Mündung: Pfeil an MountPose#front, vor dem Rahmen, nach vorn, kein Treffer an der eigenen Drohne", 400,
                AccessoryRecessSelfTests::shotStep);
    }

    private static StepResult skipped(SelfTest.StepContext ctx) {
        ctx.log("Beispiel-Add-on aus, übersprungen");
        return StepResult.pass();
    }

    private static StepResult modelStep(SelfTest.StepContext ctx) {
        AccessoryType recessed = AccessoryGapsSelfTests.type(ExampleDispenser.RECESSED_DISPENSER);
        AccessoryType fallback = (AccessoryType) PartIndex.detached(AccessoryDefinition.builder(Identifier.fromNamespaceAndPath("propwash_fittest", "recessed_fallback"))
                .basics(PartBasics.of(100.0)).slot(AccessorySlot.FRONT).size(62.0, 58.0, 64.0).frontalArea(0.001)
                .recess(ExampleDispenser.RECESS_MM).drop(ExampleDispenser.DROP_MM).build());
        AccessoryType plain = (AccessoryType) PartIndex.detached(AccessoryDefinition.builder(Identifier.fromNamespaceAndPath("propwash_fittest", "recessed_plain"))
                .basics(PartBasics.of(100.0)).slot(AccessorySlot.FRONT).size(62.0, 58.0, 64.0).frontalArea(0.001).build());
        DroneRenderResources res = DroneRenderResources.INSTANCE;
        List<String> problems = new ArrayList<>();
        List<FrameType> frames = new ArrayList<>();
        for (DroneBuild build : BUILDS) {
            frames.add(build.frame());
            String frame = build.frame().key();
            AccessoryAnchor anchor = AccessoryAnchor.of(build.frame(), AccessoryMount.FRONT);
            for (AccessoryType type : List.of(recessed, fallback)) {
                DroneBuild fitted = build.withoutAccessories().withAccessory(type);
                DroneAssembly assembly = res.assembly(fitted);
                DroneAssembly.Accessory part = assembly.accessory(AccessoryMount.FRONT);
                String what = frame + " + " + type.id();
                Vector3f expected = new Vector3f((float) anchor.x(), (float) (anchor.y() - ExampleDispenser.DROP_MM), (float) (anchor.z() + ExampleDispenser.RECESS_MM));
                if (part.body().position().distance(expected) > POSITION_TOLERANCE_MM) {
                    problems.add(what + ": Modell bei " + part.body().position() + " statt " + expected);
                }
                if (part.anchor() == null || !AccessoryFitSelfTests.same(part.anchor(), anchor)) {
                    problems.add(what + ": Anker " + part.anchor() + " statt " + anchor);
                }
                DroneRenderState state = new DroneRenderState();
                DroneVisuals.fillPreview(state, fitted);
                int i = AccessoryMount.FRONT.ordinal();
                if (state.accessories[i].position.distance(expected) > POSITION_TOLERANCE_MM) {
                    problems.add(what + ": Anzeige-Zustand bei " + state.accessories[i].position);
                }
                if ((type == recessed) != state.accessories[i].useItem) {
                    problems.add(what + ": Modell-Datei " + (type == recessed ? "nicht gezeichnet" : "unerwartet"));
                }
                AccessoryFit.Fit drawn = AccessoryFit.measure(assembly, AccessoryMount.FRONT, true, false);
                if (drawn == null || !drawn.ok()) {
                    problems.add(what + ": gezeichnet " + (drawn == null ? "nicht gemessen" : drawn.describe(anchor)));
                } else {
                    ctx.log(what + ": " + drawn.describe(anchor));
                }
            }
            Quaternionf noseDown = new Quaternionf().rotationX((float) Math.toRadians(-NOSE_DOWN_DEGREES));
            Vector3f pivot = new Vector3f();
            float flatLowest = res.assembly(build.withoutAccessories().withAccessory(plain)).hull().lowestPoint(noseDown, pivot);
            float sunkLowest = res.assembly(build.withoutAccessories().withAccessory(fallback)).hull().lowestPoint(noseDown, pivot);
            if (!(sunkLowest - flatLowest > MIN_HULL_SHIFT_MM)) {
                problems.add(String.format(Locale.ROOT, "%s: Hülle folgt nicht nach hinten, tiefster Punkt mit Nase unten %.1f → %.1f mm", frame, flatLowest, sunkLowest));
            }
        }
        ctx.log("gemessen an " + frames);
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    private static StepResult shotStep(SelfTest.StepContext ctx) {
        if (!AccessoryGapsSelfTests.ready()) {
            return skipped(ctx);
        }
        if (InteractSelfTests.phase(ctx) == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> REPORT.set(shots(server))));
            InteractSelfTests.next(ctx);
            return StepResult.pending();
        }
        StepResult server = InteractSelfTests.await(ctx, "Server");
        if (server.status() == StepResult.Status.PASS) {
            ctx.log(REPORT.get());
        }
        return server;
    }

    private static String shots(MinecraftServer server) {
        ServerPlayer player = InteractSelfTests.player(server);
        int[] arena = InteractSelfTests.arena(player);
        ServerLevel level = player.level();
        GameType mode = player.gameMode();
        player.setGameMode(GameType.CREATIVE);
        ExampleDispenser.consume(true);
        ExampleDispenser.speedScale(1.0);
        AccessoryType recessed = AccessoryGapsSelfTests.type(ExampleDispenser.RECESSED_DISPENSER);
        Vec3 ground = InteractSelfTests.start(arena);
        List<String> out = new ArrayList<>();
        try {
            for (DroneBuild base : BUILDS) {
                out.add(shot(level, player, base.withoutAccessories().withAccessory(recessed), recessed, ground));
            }
        } finally {
            player.setGameMode(mode);
        }
        return String.join("; ", out);
    }

    private static String shot(ServerLevel level, ServerPlayer player, DroneBuild build, AccessoryType type, Vec3 ground) {
        FrameType frame = build.frame();
        QuadcopterEntity drone = AccessoryGapsSelfTests.hover(level, build, ground);
        try {
            MountPose pose = MountPoses.of(drone, AccessoryMount.FRONT).orElseThrow(() -> new IllegalStateException(frame.key() + ": keine Halterung vorn"));
            AirframeSpec p = AirframeSpec.of(build);
            Quaternionf q = new Quaternionf(drone.orientation());
            AccessoryAnchor anchor = AccessoryAnchor.of(frame, AccessoryMount.FRONT);
            Vec3 local = MountPoses.toFrameMetres(MountPoses.center(drone), q, p, pose.front());
            double[] muzzle = anchor.toFrame(type.muzzleMm());
            Vec3 expected = new Vec3(muzzle[0] / 1000.0, muzzle[1] / 1000.0, muzzle[2] / 1000.0);
            if (local.distanceTo(expected) > FRAME_TOLERANCE_M) {
                throw new IllegalStateException(frame.key() + ": Mündung im Rahmen bei " + local + " statt " + expected);
            }
            if (!(muzzle[2] < anchor.z())) {
                throw new IllegalStateException(frame.key() + ": Mündung " + muzzle[2] + " mm liegt nicht vor dem Anker " + anchor.z() + " mm");
            }
            DispenseOrigin origin = DispenseOrigin.of(level, drone, AccessoryMount.FRONT, pose);
            AccessoryInventory inventory = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT)
                    .orElseThrow(() -> new IllegalStateException(frame.key() + ": Werfer ohne Inventar"));
            inventory.set(0, new ItemStack(Items.ARROW, 1));
            DispenseResult result = AccessoryGapsSelfTests.fire(drone, player);
            if (result.status() != DispenseResult.Status.FIRED || !(result.entity().orElse(null) instanceof Arrow arrow)) {
                throw new IllegalStateException(frame.key() + ": " + result.status() + " statt FIRED");
            }
            double off = arrow.position().distanceTo(origin.muzzle());
            Vector3f forward = q.transform(new Vector3f(0.0F, 0.0F, -1.0F));
            double along = arrow.getDeltaMovement().dot(new Vec3(forward.x, forward.y, forward.z));
            boolean protectedShot = OwnShots.protects(drone, arrow);
            arrow.discard();
            if (off > SHOT_TOLERANCE_M) {
                throw new IllegalStateException(String.format(Locale.ROOT, "%s: Pfeil startet %.4f m neben der Mündung", frame.key(), off));
            }
            if (along < 0.8) {
                throw new IllegalStateException(String.format(Locale.ROOT, "%s: Pfeil fliegt mit %.2f B/t nach vorn", frame.key(), along));
            }
            if (!protectedShot) {
                throw new IllegalStateException(frame.key() + ": eigener Pfeil ist nicht vor Treffern an der eigenen Drohne geschützt");
            }
            return String.format(Locale.ROOT, "%s: Mündung %.1f mm vor dem Anker, Pfeil %.4f m daneben, %.2f B/t nach vorn", frame.key(), anchor.z() - muzzle[2], off, along);
        } finally {
            drone.discard();
        }
    }
}
