package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public record DroneContext(Source source, @Nullable QuadcopterEntity entity, ItemStack stack, DroneBuild build, Component name,
                           @Nullable DroneSim sim) {
    public enum Source {
        PILOTING,
        HELD,
        NONE
    }

    public static DroneContext resolve(Minecraft minecraft) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        if (drone != null) {
            Component name = drone.getCustomName() != null ? drone.getCustomName() : drone.getName();
            return new DroneContext(Source.PILOTING, drone, ItemStack.EMPTY, drone.build(), name, session.sim());
        }
        Player player = minecraft.player;
        if (player != null) {
            ItemStack held = heldDrone(player);
            if (!held.isEmpty()) {
                DroneBuild build = held.getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
                return new DroneContext(Source.HELD, null, held, build, held.getHoverName(), null);
            }
        }
        return new DroneContext(Source.NONE, null, ItemStack.EMPTY, DroneBuild.FREESTYLE,
                Component.translatable("propwash.ui.header.no_drone"), null);
    }

    private static ItemStack heldDrone(Player player) {
        if (ContentIds.is(player.getMainHandItem(), ContentIds.DRONE)) {
            return player.getMainHandItem();
        }
        if (ContentIds.is(player.getOffhandItem(), ContentIds.DRONE)) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    public DronePaint paint() {
        if (this.entity != null) {
            return this.entity.paint();
        }
        return PaintItems.paint(this.stack);
    }

    public boolean piloting() {
        return this.source == Source.PILOTING;
    }

    public boolean hasDrone() {
        return this.source != Source.NONE;
    }

    public @Nullable Telemetry telemetry() {
        return this.sim != null ? this.sim.telemetry() : null;
    }

    public boolean sameDrone(DroneContext other) {
        if (this.source != other.source) {
            return false;
        }
        return switch (this.source) {
            case PILOTING -> this.entity == other.entity;
            case HELD -> this.build.equals(other.build);
            case NONE -> true;
        };
    }
}
