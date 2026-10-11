package de.lardtom.propwash.content;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class DroneDataReader {
    private DroneDataReader() {
    }

    public static DroneData read(ItemStack drone) {
        FlightTune tune = drone.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
        Component custom = drone.get(DataComponents.CUSTOM_NAME);
        Optional<String> name = Optional.ofNullable(custom).map(Component::getString).map(DroneDataReader::trim);
        return new DroneData(Conversions.spec(DroneAssembly.buildOf(drone)), Conversions.paint(PaintItems.paint(drone)), Conversions.tune(tune),
                name);
    }

    private static String trim(String name) {
        return name.length() > DroneData.MAX_NAME_LENGTH ? name.substring(0, DroneData.MAX_NAME_LENGTH) : name;
    }
}
