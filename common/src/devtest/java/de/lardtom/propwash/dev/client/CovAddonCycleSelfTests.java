package de.lardtom.propwash.dev.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.AccessoryContents;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.input.AddonAxis;
import de.lardtom.propwash.api.input.AddonSwitch;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.addon.AxisAssignment;
import de.lardtom.propwash.input.client.screen.AddonSwitchRows;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.addon.AddonOsdKeys;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.server.AddonWorldRules;
import de.lardtom.propwash.server.CovComboRuleAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueOutput;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CovAddonCycleSelfTests {
    public static final String GROUP = "platzhalter: Abdeckung cov-combo – ";
    public static final String WORLD = "Cov Addon-Zyklus";
    public static final String GUID = "03000000covcombo0cycle0000000000";
    public static final Set<String> ADDONS = Set.of("mobdefense", "camdrones");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final List<String> DRONE_KEYS = List.of("Build", "AccessoryContents", "AddonData");
    private static final String FUTURE = "future";
    private static final AtomicReference<@Nullable JsonObject> CURRENT = new AtomicReference<>();

    private CovAddonCycleSelfTests() {
    }

    public static void register() {
        String one = GROUP + "Start 1 mit Mob Defense und Kamera-Add-on: ";
        step(1, one + "Welt anlegen", 2400, ctx -> DrehweltSelfTests.createFlatWorld(ctx, WORLD));
        step(1, one + "Belegungen, OSD-Plätze, Regeln und Drohnen mit Add-on-Teilen, Munition und Daten setzen", 600,
                CovAddonCycleSelfTests::setStep);
        step(1, one + "speichern und verlassen", 1200, ctx -> DrehweltSelfTests.leaveWorld(ctx, "cov-combo Start 1"));
        step(1, one + "Stand nach dem Speichern ablegen", 200, ctx -> storeStep(ctx, 1));
        String two = GROUP + "Start 2 ohne die Add-ons: ";
        step(2, two + "Welt öffnen", 2400, ctx -> openStep(ctx, two));
        step(2, two + "keine Seite, kein Abschnitt, eingebaute OSD-Elemente gleich, Teile als Platzhalter, eingebaute Belegung ändern", 600,
                CovAddonCycleSelfTests::withoutStep);
        step(2, two + "speichern und verlassen", 1200, ctx -> DrehweltSelfTests.leaveWorld(ctx, "cov-combo Start 2"));
        step(2, two + "Log ohne Fehler, Add-on-Stand auf der Platte erhalten", 200, ctx -> afterStep(ctx, 2));
        String three = GROUP + "Start 3 wieder mit den Add-ons: ";
        step(3, three + "Welt öffnen", 2400, ctx -> openStep(ctx, three));
        step(3, three + "Belegungen, OSD-Plätze, Regeln und Drohnen-Daten wie nach Start 1", 600, CovAddonCycleSelfTests::againStep);
        step(3, three + "verlassen", 1200, ctx -> DrehweltSelfTests.leaveWorld(ctx, "cov-combo Start 3"));
        step(3, three + "Log ohne Fehler", 200, ctx -> afterStep(ctx, 3));
    }

    private static void step(int start, String name, int maxTicks, SelfTest.ClientStep body) {
        SelfTest.afterLeaveStep(name, maxTicks, ctx -> {
            if (!selected(start)) {
                ctx.log("nur einzeln mit dem Filter „" + GROUP + "Start " + start
                        + "“ (tools/remote_cov_addoncycle_test.sh), übersprungen");
                return StepResult.pass();
            }
            return body.tick(ctx);
        });
    }

    static boolean selected(int start) {
        String filter = SelfTest.filter();
        if (filter == null) {
            return false;
        }
        String prefix = GROUP + "Start " + start;
        for (String token : filter.split(",")) {
            String part = token.trim();
            if (part.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return true;
            }
        }
        return false;
    }

    static Path dir() throws IOException {
        Path dir = Services.PLATFORM.getGameDir().resolve("cov-combo").resolve("addoncycle");
        Files.createDirectories(dir);
        return dir;
    }

    private static boolean ours(Identifier id) {
        return ADDONS.contains(id.getNamespace());
    }

    private static JsonObject read(int start) throws IOException {
        return JsonParser.parseString(Files.readString(dir().resolve("start" + start + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void write(int start, JsonObject value) throws IOException {
        Files.writeString(dir().resolve("start" + start + ".json"), GSON.toJson(value) + "\n", StandardCharsets.UTF_8);
    }

    private static String slotText(OsdConfig.@Nullable Slot slot) {
        return slot == null ? "fehlt" : slot.enabled + "," + slot.col + "," + slot.row;
    }

    private static JsonObject clientState() {
        JsonObject out = new JsonObject();
        JsonObject builtIn = new JsonObject();
        JsonObject addon = new JsonObject();
        OsdConfig osd = OsdClient.CONFIG.get();
        for (Map.Entry<String, OsdConfig.Slot> entry : new TreeMap<>(osd.elements).entrySet()) {
            boolean foreign = entry.getKey().contains(":") && ADDONS.contains(entry.getKey().substring(0, entry.getKey().indexOf(':')));
            (foreign ? addon : builtIn).addProperty(entry.getKey(), slotText(entry.getValue()));
        }
        out.add("osdBuiltIn", builtIn);
        out.add("osdAddon", addon);
        JsonObject switches = new JsonObject();
        JsonObject axes = new JsonObject();
        DeviceProfile profile = InputConfig.get().profile(GUID);
        if (profile != null) {
            for (Map.Entry<String, SwitchAssignment> entry : new TreeMap<>(profile.switches).entrySet()) {
                switches.addProperty(entry.getKey(), entry.getValue().source + ":" + entry.getValue().index + ":" + entry.getValue().low + ":" + entry.getValue().high);
            }
            for (Map.Entry<String, AxisAssignment> entry : new TreeMap<>(profile.addonAxes).entrySet()) {
                axes.addProperty(entry.getKey(), GSON.toJson(entry.getValue()).replaceAll("\\s+", ""));
            }
        }
        out.add("switches", switches);
        out.add("axes", axes);
        return out;
    }

    private static StepResult onServer(SelfTest.StepContext ctx, java.util.function.Function<MinecraftServer, String> task) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (ctx.server() == null) {
                return StepResult.fail("keine Welt geöffnet");
            }
            AtomicReference<String> result = new AtomicReference<>();
            state.put("result", result);
            state.put(FUTURE, ctx.onServer(server -> result.set(task.apply(server))));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            Constants.LOG.error("[Selbsttest] cov-combo Add-on-Zyklus", future.exceptionNow());
            return StepResult.fail("Server: " + future.exceptionNow());
        }
        @SuppressWarnings("unchecked")
        String result = ((AtomicReference<String>) state.get("result")).get();
        if (result != null && result.startsWith("OK ")) {
            ctx.log(result.substring(3));
            return StepResult.pass();
        }
        return StepResult.fail(result == null ? "kein Ergebnis" : result);
    }

    private static StepResult setStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            for (String mod : ADDONS) {
                if (!Services.PLATFORM.isModLoaded(mod)) {
                    return StepResult.fail(mod + " nicht geladen: Start 1 braucht beide Jars in mods/");
                }
            }
            InputConfig input = InputConfig.get().copy();
            DeviceProfile profile = new DeviceProfile();
            profile.name = "Cov Add-on-Zyklus";
            profile.setupDone = true;
            profile.setBinding(SwitchFunction.ARM, SwitchAssignment.axisMicros(4, 1700, 2100));
            int button = 70;
            int axis = 6;
            List<String> bound = new ArrayList<>();
            for (AddonSwitch control : Api17Registries.SWITCHES.switches()) {
                if (ours(control.id())) {
                    profile.setAddonBinding(control.id(), SwitchAssignment.button(button++, false));
                    bound.add(control.id().toString());
                }
            }
            for (AddonAxis control : Api17Registries.SWITCHES.axes()) {
                if (ours(control.id())) {
                    profile.setAddonAxis(control.id(), AxisAssignment.axis(axis++));
                    bound.add(control.id() + " (Achse)");
                }
            }
            input.profiles.put(GUID, profile);
            InputConfig.commit(input);
            OsdConfig osd = OsdClient.CONFIG.get().copy();
            int index = 0;
            for (OsdElementSpec spec : Api17Registries.Client.osdElements()) {
                if (ours(spec.id())) {
                    OsdConfig.Slot slot = AddonOsdKeys.slot(osd, spec);
                    slot.enabled = true;
                    AddonOsdKeys.move(osd, spec, 2 + (index * 5) % 25, 3 + index / 5);
                    index++;
                }
            }
            OsdClient.CONFIG.set(osd);
            ctx.state().put("bound", bound);
            ctx.state().put("elements", index);
        }
        return onServer(ctx, CovAddonCycleSelfTests::setServer);
    }

    private static CompoundTag nbt(MinecraftServer server, Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(new ProblemReporter.Collector(), server.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    static JsonObject droneKeys(CompoundTag tag) {
        JsonObject out = new JsonObject();
        for (String key : DRONE_KEYS) {
            Tag value = tag.get(key);
            out.addProperty(key, value == null ? "" : value.toString());
        }
        return out;
    }

    private static String setServer(MinecraftServer server) {
        ServerLevel level = server.overworld();
        JsonObject rules = new JsonObject();
        List<String> problems = new ArrayList<>();
        for (WorldRule<?> rule : AddonWorldRules.registered()) {
            if (!ours(rule.id())) {
                continue;
            }
            Object value = rule.type() == WorldRule.Type.BOOLEAN ? !Boolean.TRUE.equals(rule.defaultValue())
                    : (int) rule.defaultValue() < rule.max() ? (int) rule.defaultValue() + 1 : (int) rule.defaultValue() - 1;
            if (!CovComboRuleAccess.set(server, rule, value)) {
                problems.add("Regel " + rule.id() + " nicht gesetzt");
            }
            rules.addProperty(rule.id().toString(), String.valueOf(AddonWorldRules.value(rule, server)));
        }
        BlockPos spawn = level.getRespawnData().pos();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        DroneBuild mob = DroneBuild.FREESTYLE;
        for (String path : List.of("dispenser_mount", "rocket_booster", "spear_mount")) {
            Optional<AccessoryType> type = PartIndex.byId(AccessoryType.class, Identifier.fromNamespaceAndPath("mobdefense", path));
            if (type.isEmpty()) {
                return "Teil mobdefense:" + path + " unbekannt";
            }
            mob = mob.withAccessory(type.get());
        }
        Optional<DroneBuild> cam = Optional.empty();
        for (DronePreset preset : PropwashApi.builds().presets()) {
            if (preset.id().getNamespace().equals("camdrones")) {
                cam = Conversions.build(preset.data().build());
                if (cam.isPresent()) {
                    break;
                }
            }
        }
        if (cam.isEmpty()) {
            return "kein Preset des Kamera-Add-ons";
        }
        JsonObject drones = new JsonObject();
        int i = 0;
        for (DroneBuild build : List.of(mob, cam.get())) {
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                return "Drohne nicht erzeugt";
            }
            drone.snapTo(spawn.getX() + 4.5 + i * 4, ground, spawn.getZ() + 6.5, 0.0F, 0.0F);
            drone.setAttitude(new Quaternionf());
            drone.setBuild(build);
            if (i == 0) {
                drone.setAccessoryContents(AccessoryContents.EMPTY.set(AccessoryMount.FRONT, 0, new ItemStack(Items.ARROW, 12)));
            }
            CompoundTag data = new CompoundTag();
            data.put("start", IntTag.valueOf(1));
            data.put("mission", StringTag.valueOf(i == 0 ? "Werfer geladen" : "Mission gesendet"));
            String ns = i == 0 ? "mobdefense" : "camdrones";
            drone.setAddonData(drone.addonData().withRaw(Identifier.fromNamespaceAndPath(ns, "cov_cycle"), data));
            if (!level.addFreshEntity(drone)) {
                return "Drohne " + i + " nicht gesetzt";
            }
            drones.add(drone.getUUID().toString(), droneKeys(nbt(server, drone)));
            i++;
        }
        JsonObject out = new JsonObject();
        out.add("rules", rules);
        out.add("drones", drones);
        CURRENT.set(out);
        return problems.isEmpty() ? "OK " + rules.size() + " Regeln geändert, Drohnen mit " + mob.accessories().key() + " und " + cam.get().frame().key()
                + " gesetzt" : String.join("; ", problems);
    }

    private static StepResult storeStep(SelfTest.StepContext ctx, int start) {
        JsonObject server = CURRENT.get();
        if (server == null) {
            return StepResult.fail("kein Stand aus Start " + start);
        }
        JsonObject out = server.deepCopy();
        out.add("client", clientState());
        try {
            out.add("disk", disk(server));
            write(start, out);
        } catch (IOException e) {
            return StepResult.fail("Stand nicht geschrieben: " + e);
        }
        ctx.log("Start " + start + ": " + out.getAsJsonObject("client").getAsJsonObject("switches").size() + " Schalter, "
                + out.getAsJsonObject("client").getAsJsonObject("axes").size() + " Achsen, " + out.getAsJsonObject("client").getAsJsonObject("osdAddon").size()
                + " OSD-Elemente der Add-ons, " + out.getAsJsonObject("rules").size() + " Regeln, Drohnen " + out.getAsJsonObject("drones").keySet());
        return StepResult.pass();
    }

    private static JsonObject disk(JsonObject server) throws IOException {
        Minecraft mc = Minecraft.getInstance();
        Path world = mc.getLevelSource().getBaseDir().resolve(WORLD);
        Map<String, CompoundTag> saved = CovRegionFiles.entities(world);
        JsonObject drones = new JsonObject();
        for (String uuid : server.getAsJsonObject("drones").keySet()) {
            CompoundTag tag = saved.get(uuid);
            drones.add(uuid, tag == null ? new JsonObject() : droneKeys(tag));
        }
        JsonObject out = new JsonObject();
        out.add("drones", drones);
        out.addProperty("gameRules", CovRegionFiles.gameRules(world));
        Path config = Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID);
        out.addProperty("osdJson", Files.readString(config.resolve("osd.json"), StandardCharsets.UTF_8));
        out.addProperty("inputJson", Files.readString(config.resolve("input.json"), StandardCharsets.UTF_8));
        return out;
    }

    private static StepResult openStep(SelfTest.StepContext ctx, String label) {
        if (ctx.tick() == 0) {
            if (!Files.isRegularFile(Minecraft.getInstance().getLevelSource().getBaseDir().resolve(WORLD).resolve("level.dat"))) {
                return StepResult.fail("Welt „" + WORLD + "“ aus Start 1 fehlt");
            }
            MigrationLog.begin(label + "Welt öffnen");
        }
        return CovMigrationSelfTests.openWorld(ctx, WORLD);
    }

    private static StepResult withoutStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            List<String> problems = new ArrayList<>();
            for (String mod : ADDONS) {
                if (Services.PLATFORM.isModLoaded(mod)) {
                    problems.add(mod + " ist geladen, Start 2 braucht einen Lauf ohne die Jars");
                }
            }
            JsonObject before;
            try {
                before = read(1);
            } catch (IOException e) {
                return StepResult.fail("Stand aus Start 1 fehlt: " + e);
            }
            if (AddonSwitchRows.available()) {
                problems.add("Seite „Add-ons“ im Tab Modi ist da (Beispiel-Add-on aus? PROPWASH_DEV_EXAMPLE=off)");
            }
            if (AddonWorldRules.registered().stream().anyMatch(rule -> ours(rule.id()))) {
                problems.add("Welt-Regeln der Add-ons angemeldet");
            }
            JsonObject now = clientState();
            JsonObject builtIn = before.getAsJsonObject("client").getAsJsonObject("osdBuiltIn");
            for (String key : builtIn.keySet()) {
                JsonElement value = now.getAsJsonObject("osdBuiltIn").get(key);
                if (value == null || !value.getAsString().equals(builtIn.get(key).getAsString())) {
                    problems.add("eingebautes OSD-Element " + key + " " + builtIn.get(key).getAsString() + " → " + value);
                }
            }
            if (!now.getAsJsonObject("osdAddon").equals(before.getAsJsonObject("client").getAsJsonObject("osdAddon"))) {
                problems.add("OSD-Plätze der Add-ons im Speicher geändert: " + now.getAsJsonObject("osdAddon"));
            }
            if (!now.getAsJsonObject("switches").equals(before.getAsJsonObject("client").getAsJsonObject("switches"))
                    || !now.getAsJsonObject("axes").equals(before.getAsJsonObject("client").getAsJsonObject("axes"))) {
                problems.add("Belegungen ohne die Add-ons geändert: " + now.getAsJsonObject("switches") + " " + now.getAsJsonObject("axes"));
            }
            InputConfig input = InputConfig.get().copy();
            DeviceProfile profile = input.profile(GUID);
            if (profile == null) {
                problems.add("Geräteprofil fehlt");
            } else {
                profile.setBinding(SwitchFunction.ARM, SwitchAssignment.axisMicros(5, 1700, 2100));
                InputConfig.commit(input);
            }
            if (!problems.isEmpty()) {
                return StepResult.fail(String.join("; ", problems));
            }
            ctx.state().put("before", before);
        }
        JsonObject before = (JsonObject) ctx.state().get("before");
        return onServer(ctx, server -> placeholders(server, before));
    }

    private static String placeholders(MinecraftServer server, JsonObject before) {
        ServerLevel level = server.overworld();
        List<String> problems = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String uuid : before.getAsJsonObject("drones").keySet()) {
            Entity entity = level.getEntity(UUID.fromString(uuid));
            if (!(entity instanceof QuadcopterEntity drone)) {
                problems.add("Drohne " + uuid + " nicht geladen");
                continue;
            }
            List<String> placeholder = new ArrayList<>();
            for (var part : drone.build().parts()) {
                if (ours(part.id())) {
                    placeholder.add(part.id().toString());
                }
            }
            boolean missing = drone.build().problems().stream().anyMatch(problem -> problem.name().equals("MISSING_PART"));
            if (placeholder.isEmpty() || !missing) {
                problems.add("Drohne " + uuid + " ohne Platzhalter (Teile " + placeholder + ", Probleme " + drone.build().problems() + ")");
            }
            seen.add(placeholder.size() + " Platzhalter " + placeholder);
        }
        return problems.isEmpty() ? "OK keine Seite „Add-ons“, keine Regeln der Add-ons, eingebaute OSD-Elemente und Belegungen unverändert, "
                + String.join("; ", seen) + ", Arm-Schalter auf Kanal 6 gelegt und gespeichert" : String.join("; ", problems);
    }

    private static StepResult againStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            for (String mod : ADDONS) {
                if (!Services.PLATFORM.isModLoaded(mod)) {
                    return StepResult.fail(mod + " nicht geladen: Start 3 braucht beide Jars in mods/");
                }
            }
            JsonObject before;
            JsonObject middle;
            try {
                before = read(1);
                middle = read(2);
            } catch (IOException e) {
                return StepResult.fail("Stand aus Start 1 oder 2 fehlt: " + e);
            }
            List<String> problems = new ArrayList<>();
            JsonObject now = clientState();
            JsonObject old = before.getAsJsonObject("client");
            if (!now.getAsJsonObject("osdAddon").equals(old.getAsJsonObject("osdAddon"))) {
                problems.add("OSD-Plätze der Add-ons " + now.getAsJsonObject("osdAddon") + " statt " + old.getAsJsonObject("osdAddon"));
            }
            for (String key : old.getAsJsonObject("switches").keySet()) {
                if (key.equals("arm")) {
                    continue;
                }
                JsonElement value = now.getAsJsonObject("switches").get(key);
                if (value == null || !value.equals(old.getAsJsonObject("switches").get(key))) {
                    problems.add("Belegung " + key + " " + value + " statt " + old.getAsJsonObject("switches").get(key));
                }
            }
            if (!now.getAsJsonObject("axes").equals(old.getAsJsonObject("axes"))) {
                problems.add("Achsen " + now.getAsJsonObject("axes") + " statt " + old.getAsJsonObject("axes"));
            }
            JsonElement arm = now.getAsJsonObject("switches").get("arm");
            if (arm == null || !arm.getAsString().startsWith("AXIS:5:")) {
                problems.add("eingebaute Belegung aus Start 2 verloren: arm " + arm);
            }
            if (!problems.isEmpty()) {
                return StepResult.fail(String.join("; ", problems));
            }
            ctx.state().put("before", before);
            ctx.state().put("middle", middle);
        }
        JsonObject before = (JsonObject) ctx.state().get("before");
        JsonObject middle = (JsonObject) ctx.state().get("middle");
        return onServer(ctx, server -> again(server, before, middle));
    }

    private static String again(MinecraftServer server, JsonObject before, JsonObject middle) {
        ServerLevel level = server.overworld();
        List<String> problems = new ArrayList<>();
        JsonObject rules = before.getAsJsonObject("rules");
        for (WorldRule<?> rule : AddonWorldRules.registered()) {
            if (!ours(rule.id())) {
                continue;
            }
            String want = rules.has(rule.id().toString()) ? rules.get(rule.id().toString()).getAsString() : null;
            String have = String.valueOf(AddonWorldRules.value(rule, server));
            if (!have.equals(want)) {
                problems.add("Regel " + rule.id() + " = " + have + " statt " + want);
            }
        }
        for (String uuid : before.getAsJsonObject("drones").keySet()) {
            Entity entity = level.getEntity(UUID.fromString(uuid));
            if (!(entity instanceof QuadcopterEntity drone)) {
                problems.add("Drohne " + uuid + " nicht geladen");
                continue;
            }
            JsonObject now = droneKeys(nbt(server, drone));
            JsonObject old = before.getAsJsonObject("drones").getAsJsonObject(uuid);
            JsonObject away = middle.getAsJsonObject("disk").getAsJsonObject("drones").getAsJsonObject(uuid);
            for (String key : DRONE_KEYS) {
                if (!old.get(key).equals(now.get(key))) {
                    problems.add("Drohne " + uuid + " " + key + " " + shorten(now.get(key).getAsString()) + " statt " + shorten(old.get(key).getAsString()));
                }
                if (away != null && away.has(key) && !old.get(key).equals(away.get(key))) {
                    problems.add("Drohne " + uuid + " " + key + " ohne Add-ons gespeichert als " + shorten(away.get(key).getAsString()));
                }
            }
        }
        return problems.isEmpty() ? "OK " + rules.size() + " Regeln, " + before.getAsJsonObject("drones").size()
                + " Drohnen (Bau, Inhalt, Add-on-Daten) wie nach Start 1, auch auf der Platte nach Start 2" : String.join("; ", problems);
    }

    private static String shorten(String text) {
        return text.length() > 120 ? text.substring(0, 119) + "…" : text;
    }

    private static StepResult afterStep(SelfTest.StepContext ctx, int start) {
        MigrationLog.Report report = MigrationLog.finish(true);
        List<String> problems = new ArrayList<>();
        if (report != null) {
            ctx.log(report.summary());
            if (!report.unexpected().isEmpty()) {
                problems.add(report.unexpected().size() + " unerwartete Log-Zeilen, erste: " + report.unexpected().getFirst().message());
            }
        } else {
            problems.add("kein Log-Abgriff");
        }
        if (start == 2) {
            try {
                JsonObject before = read(1);
                JsonObject out = new JsonObject();
                out.add("disk", disk(before));
                JsonObject disk = out.getAsJsonObject("disk");
                String osd = disk.get("osdJson").getAsString();
                for (String key : before.getAsJsonObject("client").getAsJsonObject("osdAddon").keySet()) {
                    if (!osd.contains("\"" + key + "\"")) {
                        problems.add("osd.json ohne " + key + " nach dem Speichern ohne Add-on");
                    }
                }
                String input = disk.get("inputJson").getAsString();
                for (String key : before.getAsJsonObject("client").getAsJsonObject("switches").keySet()) {
                    if (key.contains(":") && !input.contains("\"" + key + "\"")) {
                        problems.add("input.json ohne " + key + " nach dem Speichern ohne Add-on");
                    }
                }
                String rules = disk.get("gameRules").getAsString();
                for (String key : before.getAsJsonObject("rules").keySet()) {
                    if (!rules.contains(key)) {
                        problems.add("game_rules.dat ohne " + key + " nach dem Speichern ohne Add-on");
                    }
                }
                write(2, out);
            } catch (IOException e) {
                problems.add("Stand nach Start 2 nicht abgelegt: " + e);
            }
        }
        if (report != null && problems.isEmpty()) {
            return MigrationLog.result(report);
        }
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }
}
