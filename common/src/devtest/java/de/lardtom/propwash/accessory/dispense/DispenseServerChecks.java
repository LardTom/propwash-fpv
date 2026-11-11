package de.lardtom.propwash.accessory.dispense;

import de.lardtom.propwash.accessory.MountPoses;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.WorldRulesRights;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class DispenseServerChecks {
    private static final NameAndId ADMIN = new NameAndId(UUID.fromString("00000000-0000-4000-8000-0000000d15e5"), "propwash_dispense_admin");
    private static String summary = "";

    private DispenseServerChecks() {
    }

    public static String summary() {
        return summary;
    }

    public static @Nullable String check(MinecraftServer server) {
        if (!JoinTestEnv.exampleAddon() || AccessoryType.byId(ExampleDispenser.DISPENSER).isEmpty()) {
            summary = "Beispiel-Add-on aus, übersprungen";
            return null;
        }
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        ServerPlayer guest = WorldRulesRights.guest(server);
        guest.setGameMode(GameType.SURVIVAL);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE.withAccessory(AccessoryType.byId(ExampleDispenser.DISPENSER).orElseThrow()));
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        Vec3 pos = Vec3.atBottomCenterOf(spawn.above(6).east(2));
        drone.snapTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        drone.setNoGravity(true);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf().rotationY((float) Math.toRadians(90.0) + (float) Math.PI));
        boolean adminWasOp = server.getPlayerList().isOp(ADMIN);
        ExampleDispenser.consume(true);
        ExampleDispenser.speedScale(1.0);
        try {
            if (!adminWasOp) {
                server.getPlayerList().op(ADMIN);
            }
            AccessoryInventory inventory = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT).orElse(null);
            if (inventory == null) {
                return "Werfer ohne Inventar am dedizierten Server";
            }
            Vec3 muzzle = MountPoses.of(drone, AccessoryMount.FRONT).orElseThrow().front();
            if (!server.isUnderSpawnProtection(level, BlockPos.containing(muzzle), guest)) {
                return "Spawn-Schutz greift an der Mündung nicht (spawn-protection in server.properties?)";
            }
            inventory.set(0, new ItemStack(Items.TNT));
            DispenseResult denied = fire(drone, guest);
            if (denied.status() != DispenseResult.Status.DENIED || !inventory.get(0).is(Items.TNT)) {
                return "TNT im Spawn-Schutz: " + denied.status() + " statt DENIED, Rest " + inventory.get(0);
            }
            server.getPlayerList().op(guest.nameAndId());
            DispenseResult placed = fire(drone, guest);
            server.getPlayerList().deop(guest.nameAndId());
            placed.entity().ifPresent(Entity::discard);
            if (placed.status() != DispenseResult.Status.PLACED || !(placed.entity().orElse(null) instanceof PrimedTnt)) {
                return "TNT als Operator im Spawn-Schutz: " + placed.status() + " statt PLACED";
            }
            inventory.set(0, new ItemStack(Items.ARROW));
            DispenseResult arrow = fire(drone, guest);
            arrow.entity().ifPresent(Entity::discard);
            if (arrow.status() != DispenseResult.Status.FIRED || !(arrow.entity().orElse(null) instanceof Arrow)) {
                return "Pfeil ohne Client: " + arrow.status() + " statt FIRED";
            }
            if (PropwashApi.accessories().triggerAction(drone, 5, true, guest)) {
                return "triggerAction mit Aktion 5 liefert true";
            }
            if (!PropwashApi.drones().loaded(level).contains(drone)) {
                return "loaded(level) ohne die Werfer-Drohne";
            }
            if (PropwashApi.drones().cameraPose(drone).isEmpty()) {
                return "cameraPose am dedizierten Server leer";
            }
            summary = "Spawn-Schutz: TNT des Gasts DENIED, als Operator PLACED; Pfeil per triggerAction ohne Client FIRED; Aktion 5 → false; "
                    + "loaded(level) " + PropwashApi.drones().loaded(level).size() + " Drohnen; cameraPose vorhanden";
            return null;
        } finally {
            server.getPlayerList().deop(guest.nameAndId());
            if (!adminWasOp) {
                server.getPlayerList().deop(ADMIN);
            }
            drone.discard();
        }
    }

    private static DispenseResult fire(QuadcopterEntity drone, ServerPlayer actor) {
        int before = ExampleDispenser.SHOTS.get();
        boolean pressed = PropwashApi.accessories().triggerAction(drone, ExampleDispenser.FIRE, true, actor);
        PropwashApi.accessories().triggerAction(drone, ExampleDispenser.FIRE, false, actor);
        if (!pressed || ExampleDispenser.SHOTS.get() == before) {
            return DispenseResult.of(DispenseResult.Status.REFUSED);
        }
        return ExampleDispenser.LAST_RESULT.get();
    }
}
