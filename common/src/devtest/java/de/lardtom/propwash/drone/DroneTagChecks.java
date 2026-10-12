package de.lardtom.propwash.drone;

import com.mojang.authlib.GameProfile;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.tag.DroneTagRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class DroneTagChecks {
    public static final UUID OWNER_ID = UUID.fromString("00000000-0000-4000-8000-0000000d7a61");
    public static final String OWNER_NAME = "Tag_Owner";
    public static final UUID OTHER_ID = UUID.fromString("00000000-0000-4000-8000-0000000d7a62");
    public static final String OTHER_NAME = "tagPilot2";
    public static final String OTHER_RENAMED = "tagPilot2b";
    private static final int CLICK_HEIGHT = 48;

    private DroneTagChecks() {
    }

    public static @Nullable String check(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos click = level.getRespawnData().pos().above(CLICK_HEIGHT).east(6);
        level.getChunk(click.getX() >> 4, click.getZ() >> 4);
        ServerPlayer owner = player(server, OWNER_ID, OWNER_NAME);
        ServerPlayer other = player(server, OTHER_ID, OTHER_NAME);
        List<QuadcopterEntity> spawned = new ArrayList<>();
        try {
            return run(level, click, owner, other, spawned);
        } catch (RuntimeException e) {
            return "Ausnahme: " + e;
        } finally {
            for (QuadcopterEntity drone : spawned) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                if (!drone.isRemoved()) {
                    drone.discard();
                }
            }
        }
    }

    private static @Nullable String run(ServerLevel level, BlockPos click, ServerPlayer owner, ServerPlayer other,
                                        List<QuadcopterEntity> spawned) {
        String ownerTag = DroneTagRules.ownerTag(OWNER_NAME);
        String ownerPilot = DroneTagRules.pilotTag(OWNER_NAME);
        String otherPilot = DroneTagRules.pilotTag(OTHER_NAME);

        owner.setItemInHand(InteractionHand.MAIN_HAND, droneStack());
        QuadcopterEntity drone = place(level, owner, click);
        if (drone == null) {
            return "Platzieren fehlgeschlagen";
        }
        spawned.add(drone);
        String problem = expect(drone, "nach dem Platzieren", ownerTag);
        if (problem != null) {
            return problem;
        }

        problem = start(owner, drone);
        if (problem != null) {
            return problem;
        }
        problem = expect(drone, "beim Fliegen", ownerTag, ownerPilot, DroneTagRules.FLYING);
        if (problem != null) {
            return problem;
        }

        owner.setHealth(0.0F);
        drone.tick();
        owner.setHealth(owner.getMaxHealth());
        if (drone.pilot().isEmpty()) {
            return "Sitzung endet schon im ersten Drohnen-Tick nach dem Tod";
        }
        problem = expect(drone, "nach dem Tod des Piloten (Sitzung noch in der Gnadenfrist)", ownerTag, ownerPilot);
        if (problem != null) {
            return problem;
        }
        drone.tick();
        problem = expect(drone, "Pilot lebt wieder, Sitzung läuft", ownerTag, ownerPilot, DroneTagRules.FLYING);
        if (problem != null) {
            return problem;
        }

        DronePiloting.release(drone);
        problem = expect(drone, "nach dem Aussteigen", ownerTag, ownerPilot);
        if (problem != null) {
            return problem;
        }

        problem = start(other, drone);
        if (problem != null) {
            return problem;
        }
        problem = expect(drone, "beim Fliegen eines anderen Spielers", ownerTag, otherPilot, DroneTagRules.FLYING);
        if (problem != null) {
            return problem;
        }

        CompoundTag saved = save(drone);
        if (!savedTags(saved).contains(DroneTagRules.FLYING)) {
            return "Speicherstand im Flug ohne " + DroneTagRules.FLYING + " – Neustart-Prüfung wäre wertlos: " + savedTags(saved);
        }
        problem = expectLoaded(level, saved, "nach dem Neustart im Flug", ownerTag, otherPilot);
        if (problem != null) {
            return problem;
        }
        ListTag crashed = new ListTag();
        for (String tag : List.of(ownerTag, otherPilot, DroneTagRules.FLYING, DroneTagRules.AUTOPILOT, "arena")) {
            crashed.add(StringTag.valueOf(tag));
        }
        saved.put("Tags", crashed);
        problem = expectLoaded(level, saved, "nach einem Absturz des Servers mit Autopilot-Rest", ownerTag, otherPilot, "arena");
        if (problem != null) {
            return problem;
        }

        DronePiloting.release(drone);
        drone.addTag(DroneTagRules.FLYING);
        drone.tick();
        problem = expect(drone, "nach /tag add " + DroneTagRules.FLYING + " auf einer geparkten Drohne", ownerTag, otherPilot);
        if (problem != null) {
            return problem;
        }

        ServerPlayer renamed = player(level.getServer(), OTHER_ID, OTHER_RENAMED);
        problem = start(renamed, drone);
        if (problem != null) {
            return problem;
        }
        problem = expect(drone, "beim Fliegen nach dem Umbenennen", ownerTag, DroneTagRules.pilotTag(OTHER_RENAMED), DroneTagRules.FLYING);
        if (problem != null) {
            return problem;
        }
        DronePiloting.release(drone);

        renamed.snapTo(drone.getX(), drone.getY(), drone.getZ());
        DronePiloting.pickUp(renamed, drone);
        if (!drone.isRemoved()) {
            return "Aufheben fehlgeschlagen";
        }
        ItemStack picked = takeDrone(renamed);
        if (picked.isEmpty()) {
            return "aufgehobene Drohne nicht im Inventar";
        }
        renamed.setItemInHand(InteractionHand.MAIN_HAND, picked);
        QuadcopterEntity replaced = place(level, renamed, click);
        if (replaced == null) {
            return "erneutes Platzieren fehlgeschlagen";
        }
        spawned.add(replaced);
        if (!replaced.getUUID().equals(drone.getUUID())) {
            return "neu platzierte Drohne hat eine andere UUID";
        }
        return expect(replaced, "nach Aufheben und Platzieren durch einen anderen Spieler", DroneTagRules.ownerTag(OTHER_RENAMED));
    }

    public static String summary() {
        return "Platzieren setzt " + DroneTagRules.ownerTag(OWNER_NAME) + ", Fliegen " + DroneTagRules.pilotTag(OWNER_NAME) + " und "
                + DroneTagRules.FLYING + ", Tod und Aussteigen nehmen " + DroneTagRules.FLYING + ", ein anderer Pilot ersetzt den Pilot-Tag, "
                + "Neustart und Absturz laden ohne " + DroneTagRules.FLYING + "/" + DroneTagRules.AUTOPILOT
                + ", Umbenennen ersetzt den Pilot-Tag, Aufheben und Platzieren ersetzen den Besitzer";
    }

    private static ServerPlayer player(MinecraftServer server, UUID id, String name) {
        GameProfile profile = new GameProfile(id, name);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static ItemStack droneStack() {
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        return stack;
    }

    private static @Nullable QuadcopterEntity place(ServerLevel level, ServerPlayer player, BlockPos click) {
        player.snapTo(click.getX() + 0.5, click.getY() + 1.0, click.getZ() + 2.5);
        Vec3 hit = new Vec3(click.getX() + 0.5, click.getY() + 1.0, click.getZ() + 0.5);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, click, false));
        InteractionResult result = player.getMainHandItem().useOn(context);
        if (!result.consumesAction()) {
            return null;
        }
        List<QuadcopterEntity> found = level.getEntitiesOfClass(QuadcopterEntity.class, new AABB(click.above()).inflate(1.0),
                drone -> !drone.isRemoved());
        return found.size() == 1 ? found.getFirst() : null;
    }

    private static @Nullable String start(ServerPlayer player, QuadcopterEntity drone) {
        player.snapTo(drone.getX() + 1.0, drone.getY(), drone.getZ());
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
        return result == DronePiloting.StartResult.STARTED ? null
                : player.getGameProfile().name() + " darf nicht fliegen: " + result;
    }

    private static ItemStack takeDrone(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(DroneModule.DRONE_ITEM.get())) {
                return player.getInventory().removeItemNoUpdate(slot);
            }
        }
        return ItemStack.EMPTY;
    }

    private static CompoundTag save(QuadcopterEntity drone) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, drone.registryAccess());
        if (!drone.save(output)) {
            throw new IllegalStateException("Drohne nicht speicherbar");
        }
        return output.buildResult();
    }

    private static Set<String> savedTags(CompoundTag saved) {
        Set<String> tags = new TreeSet<>();
        saved.getListOrEmpty("Tags").forEach(tag -> tag.asString().ifPresent(tags::add));
        return tags;
    }

    private static @Nullable String expectLoaded(ServerLevel level, CompoundTag saved, String when, String... tags) {
        Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved),
                level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
        if (!(loaded instanceof QuadcopterEntity drone)) {
            return when + ": keine Drohne geladen (" + loaded + ")";
        }
        if (drone.pilot().isPresent()) {
            return when + ": geladene Drohne hat einen Piloten";
        }
        Set<String> expected = new TreeSet<>(List.of(tags));
        Set<String> actual = new TreeSet<>(drone.entityTags());
        return expected.equals(actual) ? null : when + ": Tags " + actual + ", erwartet " + expected;
    }

    private static @Nullable String expect(Entity drone, String when, String... tags) {
        Set<String> expected = new TreeSet<>(List.of(tags));
        Set<String> actual = new TreeSet<>(drone.entityTags());
        return expected.equals(actual) ? null : when + ": Tags " + actual + ", erwartet " + expected;
    }
}
