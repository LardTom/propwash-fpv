package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.accessory.AccessoryPayload;
import de.lardtom.propwash.accessory.dispense.DispenseMath;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

final class DispenseRecoilSelfTests {
    static final double TRIDENT_GRAMS = 1500.0;
    static final double TRIDENT_KG = 1.5;
    static final double WIND_RECOIL_NS = 0.05 * 20.0 + 0.8;
    static final double DRONE_WIND_SHARE = 0.5;
    static final double SHARE_TOLERANCE = 0.05;
    static final double MIN_VANILLA_PUSH = 0.1;
    private static final double BURST_HEIGHT = 1.7;
    private static final int WALL_AHEAD = 8;
    private static final int LANE = 3;
    private static final double SHOT_BEFORE_WALL = 0.4;
    private static final double SHOT_SPEED = 0.6;
    private static final double ZOMBIE_BEFORE_WALL = 1.1;
    private static final double DRONE_BEFORE_WALL = 1.2;
    private static final double DRONE_BESIDE = 1.0;
    private static final AtomicReference<String> REPORT = new AtomicReference<>("");

    private DispenseRecoilSelfTests() {
    }

    static void registerB1(String prefix) {
        SelfTest.clientStep(prefix + "Dreizack wiegt 1,5 kg (Beladung, Flugmasse, Schub/Gewicht, Rückstoß 1,5 kg × Mündungstempo), Windkugel 1,8 N·s", 300,
                ctx -> serverStep(ctx, DispenseRecoilSelfTests::recoil));
    }

    static void registerB3(String prefix) {
        SelfTest.clientStep(prefix + "Windstoß einer Windkugel aus dem Werfer halb so stark wie Vanilla (Zombie und eigene Drohne), "
                + "Windkugel des Spielers unverändert", 300, ctx -> serverStep(ctx, DispenseRecoilSelfTests::burst));
    }

    private static StepResult serverStep(SelfTest.StepContext ctx, Function<MinecraftServer, String> check) {
        if (!AccessoryGapsSelfTests.ready()) {
            ctx.log("Beispiel-Add-on aus, übersprungen");
            return StepResult.pass();
        }
        if (InteractSelfTests.phase(ctx) == 0) {
            ctx.state().put(InteractSelfTests.FUTURE, ctx.onServer(server -> REPORT.set(check.apply(server))));
            InteractSelfTests.next(ctx);
            return StepResult.pending();
        }
        StepResult server = InteractSelfTests.await(ctx, "Server");
        if (server.status() == StepResult.Status.PASS) {
            ctx.log(REPORT.get());
        }
        return server;
    }

    private static String recoil(MinecraftServer server) {
        ServerPlayer player = InteractSelfTests.player(server);
        int[] arena = InteractSelfTests.arena(player);
        ServerLevel level = player.level();
        ExampleDispenser.consume(true);
        ExampleDispenser.speedScale(1.0);
        QuadcopterEntity drone = AccessoryGapsSelfTests.hover(level, AccessoryGapsSelfTests.dispenserBuild(), InteractSelfTests.start(arena));
        List<String> out = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        try {
            AccessoryInventory inventory = front(drone);
            Vec3 forward = forward(drone);
            double each = PropwashApi.accessories().itemGrams(new ItemStack(Items.TRIDENT));
            out.add(String.format(Locale.ROOT, "Dreizack %.0f g", each));
            if (Math.abs(each - TRIDENT_GRAMS) > 1.0E-6) {
                wrong.add(String.format(Locale.ROOT, "Dreizack wiegt %.0f g statt %.0f g", each, TRIDENT_GRAMS));
            }
            DroneBuild emptyBuild = AccessoryPayload.flightBuild(drone);
            BuildAnalysis empty = BuildAnalysis.of(emptyBuild);
            double emptyCom = AirframeSpec.of(emptyBuild).comHeight * 1000.0;
            inventory.set(0, new ItemStack(Items.TRIDENT));
            inventory.set(1, new ItemStack(Items.TRIDENT));
            double payload = AccessoryPayload.payloadGrams(drone);
            DroneBuild ladenBuild = AccessoryPayload.flightBuild(drone);
            BuildAnalysis laden = BuildAnalysis.of(ladenBuild);
            double ladenCom = AirframeSpec.of(ladenBuild).comHeight * 1000.0;
            double added = laden.massGrams() - empty.massGrams();
            out.add(String.format(Locale.ROOT, "2 Dreizacke im Werfer: Beladung %.0f g, Flugmasse %.0f → %.0f g (+%.0f g), Schub/Gewicht %.2f → %.2f, "
                    + "Schwerpunkthöhe %.1f → %.1f mm", payload, empty.massGrams(), laden.massGrams(), added, empty.thrustToWeight(), laden.thrustToWeight(),
                    emptyCom, ladenCom));
            if (Math.abs(payload - 2.0 * TRIDENT_GRAMS) > 1.0E-6 || Math.abs(added - 2.0 * TRIDENT_GRAMS) > 0.5) {
                wrong.add(String.format(Locale.ROOT, "Beladung %.0f g und Flugmasse +%.0f g statt je %.0f g", payload, added, 2.0 * TRIDENT_GRAMS));
            }
            if (!(laden.thrustToWeight() < 0.5 * empty.thrustToWeight())) {
                wrong.add(String.format(Locale.ROOT, "Schub/Gewicht %.2f → %.2f folgt der Beladung nicht", empty.thrustToWeight(), laden.thrustToWeight()));
            }
            drone.setDeltaMovement(Vec3.ZERO);
            DispenseResult tridentShot = AccessoryGapsSelfTests.fire(drone, player);
            double tridentBack = -drone.getDeltaMovement().dot(forward) * DispenseMath.TICKS_PER_SECOND;
            tridentShot.entity().ifPresent(Entity::discard);
            double expected = TRIDENT_KG * DispenseMath.muzzleSpeed(ProjectileItem.DispenseConfig.DEFAULT.power(), 1.0);
            out.add(String.format(Locale.ROOT, "Dreizack %s, Rückstoß %.2f N·s (soll %.2f), abgestellte Drohne %.2f m/s zurück, danach Beladung %.0f g",
                    tridentShot.status(), tridentShot.recoilNs(), expected, tridentBack, AccessoryPayload.payloadGrams(drone)));
            if (tridentShot.status() != DispenseResult.Status.FIRED || !(tridentShot.entity().orElse(null) instanceof ThrownTrident)) {
                wrong.add("Dreizack nicht als Geschoss abgefeuert: " + tridentShot.status());
            }
            if (Math.abs(tridentShot.recoilNs() - expected) > 1.0E-3 || !(tridentBack > 0.5)) {
                wrong.add(String.format(Locale.ROOT, "Rückstoß des Dreizacks %.2f N·s statt %.2f N·s", tridentShot.recoilNs(), expected));
            }
            if (Math.abs(AccessoryPayload.payloadGrams(drone) - TRIDENT_GRAMS) > 1.0E-6) {
                wrong.add("Beladung nach dem Wurf " + AccessoryPayload.payloadGrams(drone) + " g statt 1500 g");
            }
            inventory.set(1, ItemStack.EMPTY);
            inventory.set(0, new ItemStack(Items.WIND_CHARGE));
            drone.setDeltaMovement(Vec3.ZERO);
            DispenseResult windShot = AccessoryGapsSelfTests.fire(drone, player);
            double windBack = -drone.getDeltaMovement().dot(forward) * DispenseMath.TICKS_PER_SECOND;
            windShot.entity().ifPresent(Entity::discard);
            double mass = AirframeSpec.of(AccessoryPayload.flightBuild(drone)).massKg;
            out.add(String.format(Locale.ROOT, "Windkugel %s, Rückstoß %.2f N·s (soll %.2f), abgestellte Drohne (%.3f kg) %.2f m/s zurück",
                    windShot.status(), windShot.recoilNs(), WIND_RECOIL_NS, mass, windBack));
            if (Math.abs(windShot.recoilNs() - WIND_RECOIL_NS) > 1.0E-6 || !(windBack > 0.5)) {
                wrong.add(String.format(Locale.ROOT, "Rückstoß der Windkugel %.2f N·s statt %.2f N·s", windShot.recoilNs(), WIND_RECOIL_NS));
            }
        } finally {
            drone.discard();
        }
        if (!wrong.isEmpty()) {
            throw new IllegalStateException(String.join("; ", out) + " – falsch: " + String.join("; ", wrong));
        }
        return String.join("; ", out);
    }

    private static String burst(MinecraftServer server) {
        ServerPlayer player = InteractSelfTests.player(server);
        int[] arena = InteractSelfTests.arena(player);
        ServerLevel level = player.level();
        ExampleDispenser.consume(true);
        ExampleDispenser.speedScale(1.0);
        QuadcopterEntity drone = AccessoryGapsSelfTests.hover(level, AccessoryGapsSelfTests.dispenserBuild(), InteractSelfTests.start(arena));
        BlockPos ownWall = new BlockPos(arena[0] + WALL_AHEAD, arena[1] + 1, arena[2] - LANE);
        BlockPos vanillaWall = new BlockPos(arena[0] + WALL_AHEAD, arena[1] + 1, arena[2] + LANE);
        List<Entity> spawned = new ArrayList<>();
        try {
            level.setBlockAndUpdate(ownWall, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(vanillaWall, Blocks.STONE.defaultBlockState());
            Zombie ownZombie = zombie(level, ownWall, arena[1]);
            Zombie vanillaZombie = zombie(level, vanillaWall, arena[1]);
            spawned.add(ownZombie);
            spawned.add(vanillaZombie);
            front(drone).set(0, new ItemStack(Items.WIND_CHARGE));
            DispenseResult shot = AccessoryGapsSelfTests.fire(drone, player);
            if (shot.status() != DispenseResult.Status.FIRED || !(shot.entity().orElse(null) instanceof WindCharge own)) {
                throw new IllegalStateException("Werfer feuert keine Windkugel: " + shot.status() + " " + shot.entity());
            }
            Vec3 ownDrone = blast(drone, ownZombie, own, ownWall);
            Vec3 ownMob = ownZombie.getDeltaMovement();
            WindCharge thrown = new WindCharge(player, level, 0.0, 0.0, 0.0);
            level.addFreshEntity(thrown);
            Vec3 vanillaDrone = blast(drone, vanillaZombie, thrown, vanillaWall);
            Vec3 vanillaMob = vanillaZombie.getDeltaMovement();
            double mobShare = ownMob.length() / Math.max(1.0E-9, vanillaMob.length());
            double droneShare = ownDrone.length() / Math.max(1.0E-9, vanillaDrone.length());
            String report = String.format(Locale.ROOT, "Zombie: Windkugel des Spielers %.2f m/s, aus dem Werfer %.2f m/s (%.0f %%); eigene Drohne %.2f m/s "
                            + "neben einer Windkugel des Spielers, %.2f m/s neben ihrer eigenen (%.0f %%)",
                    vanillaMob.length() * DispenseMath.TICKS_PER_SECOND, ownMob.length() * DispenseMath.TICKS_PER_SECOND, 100.0 * mobShare,
                    vanillaDrone.length() * DispenseMath.TICKS_PER_SECOND, ownDrone.length() * DispenseMath.TICKS_PER_SECOND, 100.0 * droneShare);
            if (vanillaMob.length() < MIN_VANILLA_PUSH || vanillaDrone.length() < MIN_VANILLA_PUSH) {
                throw new IllegalStateException("Windkugel des Spielers stößt kaum: " + report);
            }
            if (Math.abs(mobShare - DRONE_WIND_SHARE) > SHARE_TOLERANCE || Math.abs(droneShare - DRONE_WIND_SHARE) > SHARE_TOLERANCE) {
                throw new IllegalStateException("Windstoß aus dem Werfer nicht halb so stark: " + report);
            }
            return report;
        } finally {
            for (Entity entity : spawned) {
                entity.discard();
            }
            drone.discard();
            level.setBlockAndUpdate(ownWall, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(vanillaWall, Blocks.AIR.defaultBlockState());
        }
    }

    private static Zombie zombie(ServerLevel level, BlockPos wall, int ground) {
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (zombie == null) {
            throw new IllegalStateException("Zombie nicht erzeugt");
        }
        zombie.setNoAi(true);
        zombie.setSilent(true);
        zombie.snapTo(wall.getX() - ZOMBIE_BEFORE_WALL, ground, wall.getZ() + 0.5, -90.0F, 0.0F);
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static Vec3 blast(QuadcopterEntity drone, Zombie zombie, WindCharge charge, BlockPos wall) {
        double y = wall.getY() - 1 + BURST_HEIGHT;
        drone.resetTo(new Vec3(wall.getX() - DRONE_BEFORE_WALL, y - 0.2, wall.getZ() + 0.5 + DRONE_BESIDE), InteractSelfTests.upright());
        drone.setDeltaMovement(Vec3.ZERO);
        zombie.setDeltaMovement(Vec3.ZERO);
        charge.setPos(wall.getX() - SHOT_BEFORE_WALL, y, wall.getZ() + 0.5);
        charge.setDeltaMovement(new Vec3(SHOT_SPEED, 0.0, 0.0));
        charge.accelerationPower = 0.0;
        charge.tick();
        if (!charge.isRemoved()) {
            charge.discard();
            throw new IllegalStateException("Windkugel ist an der Wand nicht geplatzt (" + charge.position() + ")");
        }
        return drone.getDeltaMovement();
    }

    private static AccessoryInventory front(QuadcopterEntity drone) {
        return PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT)
                .orElseThrow(() -> new IllegalStateException("Werfer ohne Inventar"));
    }

    private static Vec3 forward(QuadcopterEntity drone) {
        Vector3f f = new Quaternionf(drone.orientation()).transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return new Vec3(f.x, f.y, f.z).normalize();
    }
}
