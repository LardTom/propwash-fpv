package de.lardtom.propwash.dev.example;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.MotorThermal;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartVisual;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.api.registry.TabSection;
import de.lardtom.propwash.dev.SelfTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ExampleAddon {
    public static final String NAMESPACE = "propwash_example";
    public static final String REQUIRED_API = "1.1";
    public static final String RESOURCE_MOD = "propwash";

    public static final Identifier MOTOR_2806 = id("m2806_1300");
    public static final Identifier PROP_13 = id("p13_7_2b_carbon");
    public static final Identifier FRAME_STRETCH = id("race_stretch_5");
    public static final Identifier FRAME_DEADCAT = id("deadcat_5");
    public static final Identifier FRAME_XCLASS = id("xclass_13");
    public static final Identifier STACK_8S = id("stack_30_8s");
    public static final Identifier MOTOR_4214 = id("m4214_400");
    public static final Identifier BATTERY_8S = id("b8s_4500_lion");
    public static final Identifier CHEMISTRY_LIHV = id("lihv_445");
    public static final Identifier MOUNT_M42 = id("m42");
    public static final Identifier PRESET_XCLASS = id("xclass");
    public static final Identifier VIDEO_HD = Identifier.fromNamespaceAndPath("propwash", "digital_hd");
    public static final Identifier VIDEO_ANALOG = Identifier.fromNamespaceAndPath("propwash", "analog_race");
    public static final ResourceKey<LootTable> LOOT_TARGET = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.withDefaultNamespace("chests/simple_dungeon"));
    public static final ResourceKey<LootTable> LOOT_TABLE = ResourceKey.create(Registries.LOOT_TABLE, id("inject/simple_dungeon"));

    public static final List<TabSection> TAB_ORDER = List.of(TabSection.PRESETS, TabSection.ENTRIES, TabSection.FRAMES, TabSection.MOTORS,
            TabSection.PROPS);
    public static final List<Identifier> JAVA_PARTS = List.of(MOTOR_2806, PROP_13, FRAME_STRETCH);
    public static final List<Identifier> JSON_PARTS = List.of(FRAME_DEADCAT, FRAME_XCLASS, STACK_8S, MOTOR_4214, BATTERY_8S);

    public static final AtomicInteger PARTS_SEEN = new AtomicInteger();
    public static final AtomicInteger FROZEN = new AtomicInteger();
    public static final AtomicInteger ASSEMBLED = new AtomicInteger();
    public static final AtomicInteger DISASSEMBLED = new AtomicInteger();
    public static final AtomicInteger IMPORTS = new AtomicInteger();
    public static final AtomicReference<ImportResult> LAST_IMPORT = new AtomicReference<>();
    public static final AtomicInteger ESC_BURNS = new AtomicInteger();
    public static final AtomicInteger LAST_ESC_CELLS = new AtomicInteger();
    public static final AtomicReference<DroneData> LAST_ESC_DRONE = new AtomicReference<>();
    public static final List<Identifier> REGISTERED = new CopyOnWriteArrayList<>();

    private static volatile boolean showcase = !SelfTest.enabled();
    private static volatile int bundled = -1;
    private static boolean initialized;

    private ExampleAddon() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashApi.requireVersion(NAMESPACE, REQUIRED_API);
        registerEvents();
        PropwashApi.parts().register(motor2806());
        PropwashApi.parts().register(prop13());
        PropwashApi.parts().register(stretchedFrame());
        bundled = PropwashApi.parts().loadBundled(RESOURCE_MOD);
        PropwashApi.partItems().registerItem(MOTOR_2806, properties -> properties.rarity(Rarity.EPIC));
        PropwashApi.partItems().registerItems(NAMESPACE);
        PropwashApi.builds().registerPreset(new DronePreset(PRESET_XCLASS, xclass(), false));
        PropwashApi.loot().inject(LOOT_TARGET, LOOT_TABLE);
        PropwashApi.extensionTab(NAMESPACE)
                .icon(FRAME_XCLASS)
                .order(TAB_ORDER.toArray(TabSection[]::new))
                .add(ExampleAddon::creativeEntries);
    }

    public static boolean showcase() {
        return showcase;
    }

    public static void showcase(boolean visible) {
        showcase = visible;
    }

    public static int bundledCount() {
        return bundled;
    }

    public static List<Identifier> allParts() {
        List<Identifier> all = new ArrayList<>(JAVA_PARTS);
        all.addAll(JSON_PARTS);
        return List.copyOf(all);
    }

    public static DroneData xclass() {
        BuildSpec build = BuildSpec.of(FRAME_XCLASS, STACK_8S, MOTOR_4214, PROP_13, VIDEO_HD, BATTERY_8S);
        PaintSpec paint = new PaintSpec(Map.of("frame", 0x1B1D22, "motors", 0xD53F30, "prop_fl", 0xFFC23D, "prop_fr", 0xFFC23D,
                "prop_rl", 0x2A2C30, "prop_rr", 0x2A2C30));
        TuneSpec tune = new TuneSpec(Map.of("pid.throttleMid", 0.35F, "pid.throttleExpo", 0.3F, "rates.rcDeadband", 0.02F));
        return new DroneData(build, paint, tune, Optional.of("X-Class 13″"));
    }

    public static DroneData deadcat() {
        return DroneData.of(BuildSpec.of(FRAME_DEADCAT, Identifier.fromNamespaceAndPath("propwash", "stack_30"),
                Identifier.fromNamespaceAndPath("propwash", "m2207_1750"), Identifier.fromNamespaceAndPath("propwash", "p51_3b"), VIDEO_ANALOG,
                Identifier.fromNamespaceAndPath("propwash", "b6s_1300")));
    }

    public static DroneData stretched() {
        return DroneData.of(BuildSpec.of(FRAME_STRETCH, Identifier.fromNamespaceAndPath("propwash", "stack_30"),
                Identifier.fromNamespaceAndPath("propwash", "m2306_1860"), Identifier.fromNamespaceAndPath("propwash", "p51_3b"), VIDEO_ANALOG,
                Identifier.fromNamespaceAndPath("propwash", "b6s_1300")));
    }

    static MotorDefinition motor2806() {
        return MotorDefinition.builder(MOTOR_2806)
                .basics(PartBasics.of(52.0).withCreativeListed(false).withRarity(Rarity.UNCOMMON).withVisual(new PartVisual(OptionalInt.of(0x3AA0FF),
                        Optional.of(Identifier.fromNamespaceAndPath("propwash", "drone_render/motor/m2807_1300")))))
                .stator("2806.5").kv(1300).mount(Mounts.MOTOR_M28).maxCurrent(48.0).resistance(0.062).cells(6, 8)
                .thermal(MotorThermal.builder().limits(135.0, 155.0).build())
                .build();
    }

    static PropDefinition prop13() {
        return PropDefinition.builder(PROP_13)
                .basics(PartBasics.of(28.0).withCreativeListed(false).withVisual(PartVisual.accent(0x2B2D31)))
                .durability(PropMaterial.CARBON.defaultDurability())
                .size(330, 7.0, 2).coefficients(0.095, 0.042).material(PropMaterial.CARBON)
                .build();
    }

    static FrameDefinition stretchedFrame() {
        return FrameDefinition.builder(FRAME_STRETCH)
                .basics(PartBasics.of(92.0).withCreativeListed(false))
                .role(FrameRole.RACE).shape(FrameShape.STRETCHED_X).wheelbase(227).maxProp(130)
                .motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30, Mounts.STACK_S20)
                .accessoryMount(AccessorySlot.TOP)
                .arms(List.of(MotorArm.of(-72.0, 0.0, -88.0, MotorArm.Spin.CW), MotorArm.of(72.0, 0.0, -88.0, MotorArm.Spin.CCW),
                        MotorArm.of(-72.0, 0.0, 88.0, MotorArm.Spin.CCW), MotorArm.of(72.0, 0.0, 88.0, MotorArm.Spin.CW)))
                .battery(true, 26.0).electronics(10.0).camera(new Vec3Mm(0.0, 18.5, -64.0)).cameraTilt(40.0)
                .aero(0.009, 0.72).impactTolerance(7.0)
                .batteryFit(new BatteryFitRule(4, 6, 230.0, 6, 0))
                .build();
    }

    private static List<ItemStack> creativeEntries() {
        if (!showcase) {
            return List.of();
        }
        return List.of(PropwashApi.builds().createDrone(xclass()));
    }

    private static void registerEvents() {
        PropwashEvents.PART_REGISTERED.register(definition -> {
            if (NAMESPACE.equals(definition.id().getNamespace())) {
                PARTS_SEEN.incrementAndGet();
                REGISTERED.add(definition.id());
            }
        });
        PropwashEvents.REGISTRY_FROZEN.register(registry -> FROZEN.incrementAndGet());
        PropwashEvents.DRONE_ASSEMBLED.register((player, drone, data) -> ASSEMBLED.incrementAndGet());
        PropwashEvents.DRONE_DISASSEMBLED.register((player, data) -> DISASSEMBLED.incrementAndGet());
        PropwashEvents.BUILD_IMPORTED.register((player, request, result) -> {
            IMPORTS.incrementAndGet();
            LAST_IMPORT.set(result);
        });
        PropwashEvents.ESC_BURNED.register((player, drone, data, cells) -> {
            LAST_ESC_CELLS.set(cells);
            LAST_ESC_DRONE.set(data);
            ESC_BURNS.incrementAndGet();
        });
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }
}
