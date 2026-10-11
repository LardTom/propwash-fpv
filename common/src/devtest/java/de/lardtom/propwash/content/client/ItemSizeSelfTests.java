package de.lardtom.propwash.content.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.client.render.DroneVisualScale;
import de.lardtom.propwash.drone.client.render.PartItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ItemSizeSelfTests {
    public static final String REPORT = "propwash-item-sizes.txt";
    private static final int SHOWN_OUTLIERS = 12;
    private static final float MOTOR_TO_HELD_BLOCK = 0.6F;
    private static final float MOTOR_ORDER_SLACK = 0.005F;
    private static final float MOTOR_CLEARLY_LARGER = 1.1F;

    private ItemSizeSelfTests() {
    }

    public record Sample(String label, ItemStack stack) {
    }

    public static void register() {
        SelfTest.clientStep("content: Item-Größen aller Items in allen Anzeige-Kontexten", 1, ItemSizeSelfTests::auditStep);
        SelfTest.clientStep("content: Drohnen am Boden zeigen ihre Größenklasse", 1, ctx -> classesStep(ctx));
        SelfTest.clientStep("content: Motoren in der Hand kleiner als ein gehaltener Stein, nach Baugröße", 1, ctx -> motorsInHandStep(ctx));
        GroundItemsShowcase.register();
        HandItemsShowcase.register();
    }

    static ItemDisplayContext context(String name) {
        for (ItemDisplayContext context : ItemDisplayContext.values()) {
            if (context.getSerializedName().equals(name)) {
                return context;
            }
        }
        throw new IllegalArgumentException(name);
    }

    static ItemStack drone(DroneBuild build) {
        ItemStack stack = ContentTab.presetDrone(build);
        if (stack.isEmpty()) {
            stack = new ItemStack(DroneModule.DRONE_ITEM.get());
            stack.set(CoreComponents.DRONE_BUILD.get(), build);
        }
        return stack;
    }

    static ItemStack exampleXClass() {
        return PropwashApi.builds().createDrone(ExampleAddon.xclass());
    }

    static String droneLabel(ItemStack stack) {
        DroneBuild build = stack.getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "[" + build.frame().id() + "]";
    }

    static List<ItemStack> drones() {
        List<ItemStack> stacks = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            stacks.add(drone(build));
        }
        ItemStack xclass = exampleXClass();
        if (!xclass.isEmpty()) {
            stacks.add(xclass);
        }
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            ItemStack stack = PropwashApi.builds().createDrone(preset.data());
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    public static List<Sample> samples() {
        List<Sample> samples = new ArrayList<>();
        Item droneItem = DroneModule.DRONE_ITEM.get();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
                continue;
            }
            if (item == droneItem) {
                Map<String, ItemStack> unique = new LinkedHashMap<>();
                for (ItemStack stack : drones()) {
                    unique.putIfAbsent(droneLabel(stack), stack);
                }
                unique.forEach((label, stack) -> samples.add(new Sample(label, stack)));
                continue;
            }
            samples.add(new Sample(id.toString(), new ItemStack(item)));
        }
        return samples;
    }

    static List<Sample> references() {
        return List.of(new Sample("minecraft:stone", new ItemStack(Items.STONE)), new Sample("minecraft:iron_sword", new ItemStack(Items.IRON_SWORD)),
                new Sample("minecraft:carrot", new ItemStack(Items.CARROT)));
    }

    private static String format(ItemBounds.@Nullable Box box) {
        if (box == null) {
            return "-\t-\t-\t-";
        }
        return String.format(Locale.ROOT, "%.3f\t%.3f\t%.3f\t%.3f", box.x(), box.y(), box.z(), box.bulk());
    }

    static float partLengthMm(ItemStack stack) {
        Identifier part = PartItemRenderer.partId(stack);
        if (part == null) {
            return 0.0F;
        }
        if (hasTrueScaleModel(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            ItemBounds.Box none = ItemBounds.measure(stack, ItemDisplayContext.NONE);
            if (none != null && none.unmeasured() == 0) {
                return none.largest() * DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;
            }
        }
        return PartItemRenderer.lengthMm(part);
    }

    private static boolean hasTrueScaleModel(Identifier item) {
        Identifier file = Identifier.fromNamespaceAndPath(item.getNamespace(), "items/" + item.getPath() + ".json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            return false;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return noneIsModel(JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("model"));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean noneIsModel(@Nullable JsonObject node) {
        if (node == null || !node.has("type")) {
            return false;
        }
        return switch (node.get("type").getAsString()) {
            case "minecraft:model" -> true;
            case "minecraft:select" -> {
                if ("minecraft:display_context".equals(node.get("property").getAsString()) && node.has("cases")) {
                    for (JsonElement entry : node.getAsJsonArray("cases")) {
                        JsonElement when = entry.getAsJsonObject().get("when");
                        if (when.isJsonPrimitive() ? when.getAsString().equals("none") : when.getAsJsonArray().contains(new JsonPrimitive("none"))) {
                            yield noneIsModel(entry.getAsJsonObject().getAsJsonObject("model"));
                        }
                    }
                }
                yield noneIsModel(node.getAsJsonObject("fallback"));
            }
            case "minecraft:range_dispatch" -> noneIsModel(node.getAsJsonObject("fallback"));
            case "minecraft:condition" -> noneIsModel(node.getAsJsonObject("on_false"));
            default -> false;
        };
    }

    private static StepResult auditStep(SelfTest.StepContext ctx) {
        ItemBounds.Box stone = ItemBounds.measure(new ItemStack(Items.STONE), ItemDisplayContext.GROUND);
        ItemBounds.Box sword = ItemBounds.measure(new ItemStack(Items.IRON_SWORD), ItemDisplayContext.GROUND);
        if (stone == null || Math.abs(stone.largest() - 0.25F) > 0.01F || sword == null || Math.abs(sword.view() - 0.5F) > 0.01F) {
            return StepResult.fail("Messung stimmt nicht mit Vanilla überein: Stein " + format(stone) + ", Eisenschwert " + format(sword));
        }
        StringBuilder report = new StringBuilder("item\tcontext\tx\ty\tz\tbulk\tpart_mm\tstatus\n");
        for (Sample sample : references()) {
            for (String name : ItemSizeRule.CONTEXTS) {
                report.append(sample.label()).append('\t').append(name).append('\t')
                        .append(format(ItemBounds.measure(sample.stack(), context(name)))).append("\t-\tVanilla\n");
            }
        }
        List<String> outliers = new ArrayList<>();
        List<Sample> samples = samples();
        int checked = 0;
        for (Sample sample : samples) {
            float partMm = partLengthMm(sample.stack());
            String partColumn = partMm > 0.0F ? String.format(Locale.ROOT, "%.1f", partMm) : "-";
            for (String name : ItemSizeRule.CONTEXTS) {
                ItemSizeRule.Limit limit = (partMm > 0.0F ? ItemSizeRule.partLimit(name, partMm) : ItemSizeRule.limit(name)).orElseThrow();
                ItemBounds.Box box = ItemBounds.measure(sample.stack(), context(name));
                String status;
                if (box == null) {
                    status = "LEER";
                } else if (box.unmeasured() > 0) {
                    status = "UNGEMESSEN";
                } else if (limit.fits(box.x(), box.y(), box.z())) {
                    status = "OK";
                } else if (box.largest() < limit.min()) {
                    status = "ZU KLEIN";
                } else {
                    status = limit.fitsSize(box.x(), box.y(), box.z()) ? "ZU SPERRIG" : "ZU GROSS";
                }
                checked++;
                report.append(sample.label()).append('\t').append(name).append('\t').append(format(box)).append('\t').append(partColumn).append('\t')
                        .append(status).append('\n');
                if (!status.equals("OK")) {
                    outliers.add(sample.label() + " " + name + " " + status + " (" + (box == null ? "-" : String.format(Locale.ROOT,
                            "%.3f×%.3f×%.3f, Würfelmaß %.3f", box.x(), box.y(), box.z(), box.bulk())) + ")");
                }
            }
        }
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve(REPORT);
        try {
            Files.writeString(file, report.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return StepResult.fail("Bericht " + file + ": " + e);
        }
        ctx.log(samples.size() + " Items, " + checked + " Messungen, " + outliers.size() + " Ausreißer, Bericht " + file);
        if (!outliers.isEmpty()) {
            for (String outlier : outliers) {
                ctx.log("Ausreißer: " + outlier);
            }
            return StepResult.fail(outliers.size() + " Ausreißer, zuerst: " + String.join("; ", outliers.subList(0, Math.min(SHOWN_OUTLIERS,
                    outliers.size()))));
        }
        return StepResult.pass();
    }

    private static StepResult motorsInHandStep(SelfTest.StepContext ctx) {
        List<String> problems = new ArrayList<>();
        StringBuilder log = new StringBuilder("Motoren in der Hand (erste Person, längste Kante in Blöcken):");
        for (String name : List.of("firstperson_righthand", "thirdperson_righthand")) {
            ItemBounds.Box stone = ItemBounds.measure(new ItemStack(Items.STONE), context(name));
            if (stone == null) {
                return StepResult.fail("Stein in " + name + " nicht messbar");
            }
            List<float[]> motors = new ArrayList<>();
            for (PartInfo motor : PartIndex.all(PartKind.MOTOR)) {
                Item item = BuiltInRegistries.ITEM.getOptional(motor.id()).orElse(null);
                if (item == null) {
                    continue;
                }
                ItemStack stack = new ItemStack(item);
                ItemBounds.Box box = ItemBounds.measure(stack, context(name));
                if (box == null) {
                    problems.add(motor.id() + " " + name + " leer");
                    continue;
                }
                float limit = stone.largest() * MOTOR_TO_HELD_BLOCK;
                if (box.largest() > limit) {
                    problems.add(String.format(Locale.ROOT, "%s %s %.3f > %.3f", motor.id(), name, box.largest(), limit));
                }
                motors.add(new float[]{partLengthMm(stack), box.view()});
                if (name.startsWith("firstperson")) {
                    log.append(' ').append(motor.id().getPath()).append('=').append(String.format(Locale.ROOT, "%.3f", box.largest()));
                }
            }
            for (float[] small : motors) {
                for (float[] big : motors) {
                    if (big[0] >= small[0] * MOTOR_CLEARLY_LARGER && big[1] + MOTOR_ORDER_SLACK < small[1]) {
                        problems.add(String.format(Locale.ROOT, "%s: %.1f-mm-Motor kleiner als %.1f-mm-Motor (%.3f < %.3f)", name, big[0], small[0],
                                big[1], small[1]));
                    }
                }
            }
            if (motors.size() < 6) {
                problems.add(name + ": nur " + motors.size() + " Motoren");
            }
        }
        ctx.log(log.toString());
        if (!problems.isEmpty()) {
            return StepResult.fail(problems.size() + " Befunde, zuerst: " + String.join("; ", problems.subList(0, Math.min(SHOWN_OUTLIERS, problems.size()))));
        }
        return StepResult.pass();
    }

    static float groundFootprint(ItemStack stack) {
        ItemBounds.Box box = ItemBounds.measure(stack, ItemDisplayContext.GROUND);
        return box == null ? 0.0F : box.footprint();
    }

    private static StepResult classesStep(SelfTest.StepContext ctx) {
        List<ItemStack> ordered = new ArrayList<>();
        ordered.add(drone(DroneBuild.WHOOP));
        ordered.add(drone(DroneBuild.CINEWHOOP));
        ordered.add(drone(DroneBuild.FREESTYLE));
        ordered.add(drone(DroneBuild.LONG_RANGE));
        ItemStack xclass = exampleXClass();
        if (!xclass.isEmpty()) {
            ordered.add(xclass);
        }
        StringBuilder log = new StringBuilder("Drohnen am Boden (Breite in Blöcken):");
        float previous = 0.0F;
        String previousLabel = "";
        for (ItemStack stack : ordered) {
            float size = groundFootprint(stack);
            String label = stack.getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE).frame().id().getPath();
            log.append(' ').append(label).append('=').append(String.format(Locale.ROOT, "%.3f", size));
            if (!(size > previous + 0.005F)) {
                ctx.log(log.toString());
                return StepResult.fail(label + " liegt am Boden nicht größer als " + previousLabel + ": " + size + " / " + previous);
            }
            if (size > ItemSizeRule.GROUND_MAX * ItemSizeRule.TOLERANCE) {
                ctx.log(log.toString());
                return StepResult.fail(label + " am Boden breiter als ein halber Block: " + size);
            }
            previous = size;
            previousLabel = label;
        }
        ctx.log(log.toString());
        return StepResult.pass();
    }
}
