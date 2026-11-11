package de.lardtom.propwash.stats.advancement;

import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import de.lardtom.propwash.registry.RegList;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;

import java.util.List;

public final class PropwashTriggers {
    private static final RegList<CriterionTrigger<?>> TRIGGERS = PropwashRegistries.list(Registries.TRIGGER_TYPE);
    public static final RegHolder<MilestoneTrigger> MILESTONE = TRIGGERS.add("milestone", key -> new MilestoneTrigger());
    public static final RegHolder<FlightTrigger> FLIGHT = TRIGGERS.add("flight", key -> new FlightTrigger());
    public static final RegHolder<TrickTrigger> TRICK = TRIGGERS.add("trick", key -> new TrickTrigger());
    public static final RegHolder<FlightSchoolTrigger> FLIGHT_SCHOOL = TRIGGERS.add("flight_school", key -> new FlightSchoolTrigger());
    public static final RegHolder<CargoTrigger> CARGO_DELIVERED = TRIGGERS.add("cargo_delivered", key -> new CargoTrigger());
    public static final RegHolder<DroneStrikeTrigger> DRONE_STRIKE = TRIGGERS.add("drone_strike", key -> new DroneStrikeTrigger());

    private PropwashTriggers() {
    }

    static void bootstrap() {
    }

    public static List<RegHolder<? extends CriterionTrigger<?>>> all() {
        return List.of(MILESTONE, FLIGHT, TRICK, FLIGHT_SCHOOL, CARGO_DELIVERED, DRONE_STRIKE);
    }
}
