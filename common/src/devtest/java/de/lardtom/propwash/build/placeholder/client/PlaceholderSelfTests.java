package de.lardtom.propwash.build.placeholder.client;

import de.lardtom.propwash.accessory.AccessoryContents;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.DroneBuildCodecs;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.dev.example.ExampleRules;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.UnknownGameRules;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class PlaceholderSelfTests {
    public static final String GROUP = "platzhalter: ";
    private static final String FUTURE = "placeholder_future";
    private static final String GONE = "selftest_gone";
    private static final List<Identifier> PARTS = List.of(ExampleDispenser.DISPENSER, ExampleDispenser.BOOSTER, ExampleDispenser.SPEAR_HOLDER);

    private PlaceholderSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Add-on zeitweise entfernt – Drohne mit Teil vorn, oben und unten des Beispiel-Add-ons:"
                + " ohne Add-on Platzhalter mit gleicher Id an jeder Halterung, danach wieder die Originalteile (Entity, Drohnen-Item, Netz)",
                200, ctx -> onServer(ctx, PlaceholderSelfTests::droneCheck));
        SelfTest.clientStep(GROUP + "Add-on zeitweise entfernt – Welt-Regeln eines fehlenden Add-ons laden ohne Fehler und werden"
                + " mitgespeichert", 200, ctx -> onServer(ctx, PlaceholderSelfTests::rulesCheck));
    }

    private static StepResult onServer(SelfTest.StepContext ctx, Function<MinecraftServer, @Nullable String> check) {
        Map<String, Object> state = ctx.state();
        @SuppressWarnings("unchecked")
        CompletableFuture<Void> future = (CompletableFuture<Void>) state.get(FUTURE);
        AtomicReference<String> result = resultRef(state);
        if (future == null) {
            if (!ExampleRules.registered() || PartIndex.registeredById(ExampleDispenser.DISPENSER).isEmpty()) {
                return StepResult.fail("Beispiel-Add-on nicht geladen (" + ExampleAddon.NAMESPACE + ", PROPWASH_DEV_EXAMPLE)");
            }
            state.put(FUTURE, ctx.onServer(server -> {
                try {
                    result.set(check.apply(server));
                } catch (RuntimeException e) {
                    result.set("Ausnahme: " + e);
                }
            }));
            return StepResult.pending();
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server: " + future.exceptionNow());
        }
        String problem = result.get();
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<String> resultRef(Map<String, Object> state) {
        return (AtomicReference<String>) state.computeIfAbsent("placeholder_result", key -> new AtomicReference<String>());
    }

    private static @Nullable String droneCheck(MinecraftServer server) {
        ServerLevel level = server.overworld();
        RegistryAccess access = level.registryAccess();
        DroneBuild original = DroneBuild.FREESTYLE;
        for (Identifier id : PARTS) {
            original = original.withAccessory(PartIndex.byId(AccessoryType.class, id).orElseThrow());
        }
        if (original.accessories().size() != 3) {
            return "Beispiel-Drohne hat nicht drei Zubehörteile: " + original.accessories().key();
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        drone.setBuild(original);
        AccessoryContents contents = AccessoryContents.EMPTY.set(AccessoryMount.FRONT, 0, new ItemStack(Items.ARROW, 5));
        drone.setAccessoryContents(contents);
        CompoundTag saved = save(drone);
        RegistryOps<Tag> ops = access.createSerializationContext(NbtOps.INSTANCE);
        Tag item = ItemStack.CODEC.encodeStart(ops, drone.toStack()).getOrThrow();
        byte[] wire = wire(original);
        drone.discard();

        Set<String> previous = PartAvailability.inactiveNamespaces();
        CompoundTag savedAway;
        Tag itemAway;
        byte[] wireAway;
        try {
            Set<String> inactive = new TreeSet<>(previous);
            inactive.add(ExampleAddon.NAMESPACE);
            PartAvailability.set(inactive);
            QuadcopterEntity loaded = load(level, saved);
            String problem = placeholders("Entity", loaded.build());
            if (problem != null) {
                return problem;
            }
            if (!contents.equals(loaded.accessoryContents())) {
                return "Inhalt der Halterung vorn ohne Add-on: " + loaded.accessoryContents();
            }
            savedAway = save(loaded);
            loaded.discard();
            ItemStack stack = ItemStack.CODEC.parse(ops, item).getOrThrow();
            problem = placeholders("Drohnen-Item", stack.get(CoreComponents.DRONE_BUILD.get()));
            if (problem != null) {
                return problem;
            }
            itemAway = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
            DroneBuild fromWire = DroneBuildCodecs.STREAM_CODEC.decode(Unpooled.wrappedBuffer(wire));
            problem = placeholders("Netz", fromWire);
            if (problem != null) {
                return problem;
            }
            wireAway = wire(fromWire);
        } finally {
            PartAvailability.set(previous);
        }
        if (!Arrays.equals(wire, wireAway)) {
            return "Netz-Bytes ohne Add-on anders";
        }
        QuadcopterEntity back = load(level, savedAway);
        DroneBuild backBuild = back.build();
        back.discard();
        if (!original.equals(backBuild) || !backBuild.missingParts().isEmpty()) {
            return "Entity mit Add-on: " + backBuild.accessories().key() + " statt " + original.accessories().key();
        }
        DroneBuild itemBack = ItemStack.CODEC.parse(ops, itemAway).getOrThrow().get(CoreComponents.DRONE_BUILD.get());
        if (!original.equals(itemBack)) {
            return "Drohnen-Item mit Add-on: " + (itemBack == null ? "ohne Bauplan" : itemBack.accessories().key());
        }
        if (Math.abs(original.totalMassGrams() - backBuild.totalMassGrams()) > 1.0E-9) {
            return String.format(Locale.ROOT, "Masse %.1f g statt %.1f g", backBuild.totalMassGrams(), original.totalMassGrams());
        }
        return null;
    }

    private static @Nullable String placeholders(String where, @Nullable DroneBuild build) {
        if (build == null) {
            return where + ": kein Bauplan";
        }
        if (build.accessories().size() != 3) {
            return where + " ohne Add-on: Zubehör " + build.accessories().key() + " statt drei Platzhalter";
        }
        List<String> problems = new ArrayList<>();
        for (Identifier id : PARTS) {
            AccessoryMount mount = PartIndex.registeredById(id).map(part -> ((AccessoryType) part).mount).orElseThrow();
            AccessoryType type = build.accessories().at(mount).orElse(null);
            if (type == null || !type.missing() || !id.equals(type.id()) || type.mount != mount || type.definition().slot() != mount.slot()) {
                problems.add(mount.key() + "=" + (type == null ? "leer" : type.id() + (type.missing() ? " (Platzhalter " : " (")
                        + type.definition().slot().key() + ")"));
            }
        }
        if (build.problems().isEmpty() || build.problems().getFirst() != DroneBuild.Issue.MISSING_PART) {
            problems.add("Problem missing_part fehlt: " + build.problems());
        }
        return problems.isEmpty() ? null : where + " ohne Add-on: " + String.join(", ", problems);
    }

    private static @Nullable String rulesCheck(MinecraftServer server) {
        RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var codec = GameRules.codec(server.getWorldData().enabledFeatures());
        CompoundTag tag = (CompoundTag) codec.encodeStart(ops, server.getGameRules()).getOrThrow();
        String own = ExampleRules.cooldown().id().toString();
        if (!tag.contains(own)) {
            return "Regel " + own + " fehlt im Speicherstand: " + tag.keySet().size() + " Regeln";
        }
        tag.putInt(GONE + ":range", 24);
        tag.putBoolean(GONE + ":allow_tnt", true);
        var parsed = codec.parse(ops, tag);
        if (parsed.isError()) {
            return "Laden meldet einen Fehler: " + parsed.error().orElseThrow().message();
        }
        GameRules rules = parsed.getOrThrow();
        CompoundTag again = (CompoundTag) codec.encodeStart(ops, rules).getOrThrow();
        if (!tag.equals(again)) {
            return "Speicherstand nach dem Laden anders: " + again.get(GONE + ":range") + ", " + again.get(GONE + ":allow_tnt");
        }
        CompoundTag only = new CompoundTag();
        only.putInt(GONE + ":range", 24);
        GameRuleMap loaded = GameRuleMap.CODEC.parse(ops, only).getOrThrow();
        Map<String, Tag> carried = UnknownGameRules.unknown(GameRuleMap.copyOf(loaded));
        if (!Map.of(GONE + ":range", IntTag.valueOf(24)).equals(carried) || !UnknownGameRules.unknown(GameRuleMap.of()).isEmpty()) {
            return "Kopie der Welt-Regeln ohne die fehlende Regel: " + carried;
        }
        if (!ByteTag.valueOf(true).equals(again.get(GONE + ":allow_tnt"))) {
            return "allow_tnt ohne Add-on: " + again.get(GONE + ":allow_tnt");
        }
        return null;
    }

    private static CompoundTag save(QuadcopterEntity drone) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, drone.registryAccess());
        drone.saveWithoutId(output);
        return output.buildResult();
    }

    private static QuadcopterEntity load(ServerLevel level, CompoundTag tag) {
        Entity entity = EntityType.create(DroneModule.DRONE.get(), TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag),
                level, EntitySpawnReason.LOAD).orElseThrow(() -> new IllegalStateException("Drohne nicht geladen"));
        if (!(entity instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("keine Drohne: " + entity);
        }
        return drone;
    }

    private static byte[] wire(DroneBuild build) {
        ByteBuf buffer = Unpooled.buffer();
        DroneBuildCodecs.STREAM_CODEC.encode(buffer, build);
        byte[] out = new byte[buffer.readableBytes()];
        buffer.readBytes(out);
        return out;
    }
}
