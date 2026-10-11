package de.lardtom.propwash.assets.world.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.content.BatteryChargerBlockEntity;
import de.lardtom.propwash.content.BayState;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.dev.SelfTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class WorldAssetsSelfTests {
    static final List<String> GOGGLES = List.of("goggles_analog", "goggles_digital", "goggles_creative");
    static final List<String> GATE_SIZES = List.of("small", "medium", "large");
    static final List<String> SLOT_SPRITES = List.of("frame", "stack", "motor", "prop", "video", "battery", "drone");
    static final List<String> WORKBENCH_FACES = List.of("top", "front", "side", "bottom");
    static final double WORN_MIN_DEPTH = 0.25;
    static final List<String> CHARGER_STATES = List.of("idle", "charging", "done");
    static final List<String> CHARGER_FACES = List.of("top", "side", "bottom", "front_idle", "front_charging", "front_done", "pack");
    static final List<String> GAMEPLAY_GUIS = List.of("battery_charger", "drone_service");

    private WorldAssetsSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("assets-world: Sprites in Item-, Block- und GUI-Atlas", 40, context -> {
            List<String> missing = new ArrayList<>();
            Minecraft minecraft = Minecraft.getInstance();
            TextureAtlas items = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS);
            TextureAtlas blocks = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
            TextureAtlas gui = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
            List<String> itemSprites = new ArrayList<>(GOGGLES);
            for (String goggles : GOGGLES) {
                itemSprites.add(goggles + "_worn");
            }
            for (String size : GATE_SIZES) {
                itemSprites.add("gate_" + size);
            }
            itemSprites.add("course_tool");
            itemSprites.add("race_flag");
            itemSprites.add("slalom_pole");
            itemSprites.add("dive_cube");
            itemSprites.add("freeform_gate");
            itemSprites.add("gate_pole");
            itemSprites.add("course/pole");
            itemSprites.add("course/flag");
            itemSprites.add("gate/frame");
            itemSprites.add("gate/lights");
            itemSprites.add("gate/start_frame");
            itemSprites.add("gate/banner");
            for (String sprite : itemSprites) {
                requireSprite(items, Constants.id("item/" + sprite), missing);
            }
            for (String face : WORKBENCH_FACES) {
                requireSprite(blocks, Constants.id("block/drone_workbench_" + face), missing);
            }
            for (String slot : SLOT_SPRITES) {
                requireSprite(gui, Constants.id("gui/slot/" + slot), missing);
            }
            return SelfTest.StepResult.check(missing.isEmpty(), "fehlende Sprites: " + missing);
        });

        SelfTest.clientStep("assets-world: Item-Modelle gebacken", 40, context -> {
            List<Identifier> ids = List.of(ContentIds.GOGGLES_ANALOG, ContentIds.GOGGLES_DIGITAL, ContentIds.GOGGLES_CREATIVE,
                    ContentIds.GATE_SMALL, ContentIds.GATE_MEDIUM, ContentIds.GATE_LARGE, ContentIds.COURSE_TOOL,
                    ContentIds.RACE_FLAG, ContentIds.SLALOM_POLE, ContentIds.DIVE_CUBE, ContentIds.FREEFORM_GATE, ContentIds.GATE_POLE,
                    ContentIds.DRONE_WORKBENCH);
            List<Identifier> missing = new ArrayList<>();
            for (Identifier id : ids) {
                if (Minecraft.getInstance().getModelManager().getItemModel(id) instanceof MissingItemModel) {
                    missing.add(id);
                }
            }
            return SelfTest.StepResult.check(missing.isEmpty(), "Item-Modell fehlt für " + missing);
        });

        SelfTest.clientStep("assets-world: Werkbank-Blockmodell", 40, context -> {
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(ContentIds.DRONE_WORKBENCH);
            if (block.isEmpty()) {
                return SelfTest.StepResult.fail("Block " + ContentIds.DRONE_WORKBENCH + " nicht registriert");
            }
            BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
            List<String> missing = new ArrayList<>();
            block.get().getStateDefinition().getPossibleStates().forEach(state -> {
                if (models.get(state) == models.missingModel()) {
                    missing.add(state.toString());
                }
            });
            return SelfTest.StepResult.check(missing.isEmpty(), "Blockzustände ohne Modell: " + missing);
        });

        SelfTest.clientStep("assets-world: Brillen als 3D-Modell auf dem Kopf", 40, context -> {
            Minecraft minecraft = Minecraft.getInstance();
            ResourceManager resources = minecraft.getResourceManager();
            List<String> problems = new ArrayList<>();
            for (String name : GOGGLES) {
                requireResource(resources, Constants.id("models/item/" + name + "_worn.json"), problems);
                requireResource(resources, Constants.id("textures/item/" + name + "_worn.png"), problems);
                BuiltInRegistries.ITEM.getOptional(Constants.id(name)).ifPresentOrElse(item -> {
                    ItemStack stack = new ItemStack(item);
                    Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
                    if (equippable == null || equippable.slot() != EquipmentSlot.HEAD) {
                        problems.add(name + " passt nicht in den Kopf-Slot");
                    } else if (equippable.assetId().isPresent()) {
                        problems.add(name + " trägt ein Equipment-Asset, die Kopf-Ebene würde das 3D-Modell verdecken");
                    }
                    double head = modelDepth(minecraft, stack, ItemDisplayContext.HEAD);
                    double gui = modelDepth(minecraft, stack, ItemDisplayContext.GUI);
                    if (!(head > WORN_MIN_DEPTH) || !(gui < head)) {
                        problems.add(String.format(Locale.ROOT, "%s: Kopf-Modell nicht räumlich (Tiefe Kopf %.3f, GUI %.3f)", name, head, gui));
                    }
                }, () -> problems.add(name + " nicht registriert"));
            }
            return SelfTest.StepResult.check(problems.isEmpty(), String.join("; ", problems));
        });

        SelfTest.clientStep("assets-world: Gate-Modelle, GUI und Mod-Icon", 40, context -> {
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            List<String> problems = new ArrayList<>();
            for (String size : GATE_SIZES) {
                requireResource(resources, Constants.id("models/gate/" + size + "_frame.json"), problems);
                requireResource(resources, Constants.id("models/gate/" + size + "_lights.json"), problems);
                requireResource(resources, Constants.id("models/gate/" + size + "_start_frame.json"), problems);
                requireResource(resources, Constants.id("models/gate/" + size + "_banner.json"), problems);
            }
            requireResource(resources, Constants.id("textures/gui/drone_workbench.png"), problems);
            requireResource(resources, Constants.id("icon.png"), problems);
            return SelfTest.StepResult.check(problems.isEmpty(), String.join("; ", problems));
        });

        registerGameplay();
    }

    static void registerGameplay() {
        SelfTest.clientStep("assets-world: Funke und Ladegerät", 40, context -> {
            Minecraft minecraft = Minecraft.getInstance();
            ResourceManager resources = minecraft.getResourceManager();
            List<String> problems = new ArrayList<>();
            requireResource(resources, Constants.id("models/item/remote.json"), problems);
            requireResource(resources, Constants.id("models/item/remote_body.json"), problems);
            requireResource(resources, Constants.id("textures/entity/remote/gimbal.png"), problems);
            for (String gui : GAMEPLAY_GUIS) {
                requireResource(resources, Constants.id("textures/gui/" + gui + ".png"), problems);
            }
            for (String state : CHARGER_STATES) {
                requireResource(resources, Constants.id("models/block/battery_charger_" + state + ".json"), problems);
            }
            for (int bay = 0; bay < BatteryChargerBlockEntity.SLOTS; bay++) {
                for (BayState bayState : BayState.values()) {
                    requireResource(resources, Constants.id("models/block/battery_charger_bay_" + bay + "_" + bayState.getSerializedName() + ".json"), problems);
                }
            }
            requireSprite(minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS), Constants.id("item/remote"), problems);
            TextureAtlas blocks = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
            for (String face : CHARGER_FACES) {
                requireSprite(blocks, Constants.id("block/battery_charger_" + face), problems);
            }
            for (Identifier id : List.of(Constants.id("remote"), Constants.id("battery_charger"))) {
                if (minecraft.getModelManager().getItemModel(id) instanceof MissingItemModel) {
                    problems.add("Item-Modell fehlt für " + id);
                }
            }
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(Constants.id("battery_charger"));
            if (block.isEmpty()) {
                problems.add("Block propwash:battery_charger nicht registriert");
            } else {
                BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
                block.get().getStateDefinition().getPossibleStates().forEach(state -> {
                    if (models.get(state) == models.missingModel()) {
                        problems.add("ohne Modell: " + state);
                    }
                });
            }
            return SelfTest.StepResult.check(problems.isEmpty(), String.join("; ", problems));
        });
    }

    private static double modelDepth(Minecraft minecraft, ItemStack stack, ItemDisplayContext context) {
        ItemStackRenderState state = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(state, stack, context, minecraft.level, null, 0);
        return state.isEmpty() ? 0.0 : state.getModelBoundingBox().getZsize();
    }

    private static void requireSprite(TextureAtlas atlas, Identifier sprite, List<String> missing) {
        if (atlas.getSprite(sprite) == atlas.missingSprite()) {
            missing.add(sprite.toString());
        }
    }

    private static void requireResource(ResourceManager resources, Identifier id, List<String> problems) {
        if (resources.getResource(id).isEmpty()) {
            problems.add("fehlt: " + id);
        }
    }
}
