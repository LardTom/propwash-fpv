package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.guide.client.render.CovSceneDroneProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CovBatteryPlugSelfTests {
    static final String PREFIX = "drone-render: Abdeckung – ";
    private static final String FUTURE = "cov_plug_future";
    private static final String IDS = "cov_plug_ids";
    private static final int PAINT = 0x2E7D32;
    private static final int DEFAULT_YAW = 210;
    private static final int WORLD_TICKS = 400;

    private CovBatteryPlugSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + "Akku gesteckt in allen Darstellungen: Welt, Hand, Item-Rahmen, Boden, Werkbank, Ghost und Flugschul-Szene", WORLD_TICKS + 40,
                CovBatteryPlugSelfTests::everywhereStep);
        SelfTest.clientStep(PREFIX + "Akku gesteckt: Klick auf den Stack ohne Stecker wählt den Stack, Lack färbt ihn, Stecker bleiben ausgeblendet",
                1, CovBatteryPlugSelfTests::paintStep);
        SelfTest.clientStep(PREFIX + "Stack-Item mit eigenem Stecker: Hand, Item-Rahmen und Boden mit dem Grundmodell, nie _mounted, kein Kabel", 1,
                CovBatteryPlugSelfTests::stackItemStep);
        CovBatteryPlugShowcase.register();
    }

    static List<DroneBuild> plugged() {
        DroneRenderResources res = DroneVisuals.resources();
        List<DroneBuild> out = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            if (res.assembly(build).powerLead().present()) {
                out.add(build);
            }
        }
        return out;
    }

    static @Nullable String check(String where, DroneBuild build, @Nullable DroneRenderState state) {
        String name = where + " " + build.frame().key();
        if (state == null) {
            return name + ": kein Zustand";
        }
        DroneAssembly assembly = DroneVisuals.resources().assembly(build);
        PowerLead lead = assembly.powerLead();
        if (lead.stack().model() != PartModel.stackMounted(build.stack())) {
            return name + ": Baugruppe ohne _mounted (" + lead.stack().model() + ")";
        }
        if (!state.powerLead.shown(state)) {
            return name + ": Steckerpaar nicht gezeigt (vorhanden " + state.powerLead.present + ", ohne Akku " + state.batteryMissing + ")";
        }
        if (state.powerLead.batteryPlug.isEmpty() || state.powerLead.stackCables.isEmpty()) {
            return name + ": keine Maske für Akku-Stecker oder Stack-Kabel";
        }
        if (!state.powerLead.stack.useItem || state.powerLead.stack.item != lead.stack().item()) {
            return name + ": Stack zeichnet nicht die Variante _mounted";
        }
        if (PaintPicker.stackSlot(state) != state.powerLead.stack) {
            return name + ": Lack-Auswahl nimmt nicht den gesteckten Stack";
        }
        return null;
    }

    private static StepResult everywhereStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        List<DroneBuild> builds = plugged();
        if (builds.isEmpty()) {
            return StepResult.fail("keine Voreinstellung mit gestecktem Akku");
        }
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            List<Integer> ids = new ArrayList<>();
            state.put(IDS, ids);
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = player.level();
                int x = player.getBlockX() + 4;
                int z = player.getBlockZ() - 6;
                for (int i = 0; i < builds.size(); i++) {
                    QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                    if (drone == null) {
                        throw new IllegalStateException("Drohne nicht erzeugt");
                    }
                    ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
                    stack.set(CoreComponents.DRONE_BUILD.get(), builds.get(i));
                    stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
                    drone.loadFromStack(stack);
                    int dx = x + 2 * i;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, dx, z);
                    drone.snapTo(dx + 0.5, y, z + 0.5, 0.0F, 0.0F);
                    level.addFreshEntity(drone);
                    drone.resetTo(drone.position(), new Quaternionf());
                    synchronized (ids) {
                        ids.add(drone.getId());
                    }
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Welt-Drohnen: " + future.exceptionNow());
        }
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) state.get(IDS);
        List<QuadcopterEntity> drones = new ArrayList<>();
        for (int id : ids) {
            Entity entity = mc.level == null ? null : mc.level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                drones.add(drone);
            }
        }
        if (drones.size() < builds.size() || drones.stream().anyMatch(drone -> !drone.hasBattery())) {
            return ctx.tick() > WORLD_TICKS ? finish(ctx, StepResult.fail("Welt-Drohnen kamen nicht beim Client an: " + drones.size() + " von "
                    + builds.size())) : StepResult.pending();
        }
        Map<String, Integer> counted = new LinkedHashMap<>();
        for (QuadcopterEntity drone : drones) {
            DroneRenderState s = new DroneRenderState();
            DroneVisuals.fillFromEntity(s, drone, 0.0F);
            String problem = check("Welt-Entity", drone.build(), s);
            if (problem != null) {
                return finish(ctx, StepResult.fail(problem));
            }
            counted.merge("Welt", 1, Integer::sum);
        }
        for (DroneBuild build : builds) {
            ItemStack item = ContentTab.presetDrone(build);
            DroneItemRenderer.Look look = DroneItemRenderer.Look.of(item);
            if (!look.battery()) {
                return finish(ctx, StepResult.fail("Item " + build.frame().key() + " ohne Akku"));
            }
            for (Map.Entry<String, DroneItemRenderer.Fitted> entry : Map.of("Hand/GUI", DroneItemRenderer.fitted(look), "Item-Rahmen",
                    DroneItemRenderer.wallFitted(look), "Boden", DroneItemRenderer.groundFitted(look)).entrySet()) {
                String problem = check(entry.getKey(), build, entry.getValue().state());
                if (problem != null) {
                    return finish(ctx, StepResult.fail(problem));
                }
                counted.merge(entry.getKey(), 1, Integer::sum);
            }
            String problem = check("Werkbank", build, DroneVisuals.previewState(build));
            if (problem == null) {
                counted.merge("Werkbank", 1, Integer::sum);
                problem = check("Ghost", build, DroneGhost.state(build, DronePaint.DEFAULT));
            }
            if (problem == null) {
                counted.merge("Ghost", 1, Integer::sum);
                try {
                    problem = check("Flugschul-Szene", build, CovSceneDroneProbe.state(build));
                } catch (ReflectiveOperationException e) {
                    problem = "Flugschul-Szene: " + e;
                }
            }
            if (problem != null) {
                return finish(ctx, StepResult.fail(problem));
            }
            counted.merge("Flugschul-Szene", 1, Integer::sum);
        }
        ctx.log("gesteckt mit Kabel und _mounted-Stack: " + counted + " für " + builds.stream().map(b -> b.frame().key()).toList());
        return finish(ctx, StepResult.pass());
    }

    private static StepResult finish(SelfTest.StepContext ctx, StepResult result) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) ctx.state().get(IDS);
        if (ids != null) {
            List<Integer> copy;
            synchronized (ids) {
                copy = new ArrayList<>(ids);
            }
            ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    for (int id : copy) {
                        if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                            drone.discard();
                        }
                    }
                }
            });
        }
        return result;
    }

    private static StepResult paintStep(SelfTest.StepContext ctx) {
        List<String> notes = new ArrayList<>();
        for (DroneBuild build : plugged()) {
            String problem = paintChecks(build, notes);
            if (problem != null) {
                return StepResult.fail(build.frame().key() + ": " + problem);
            }
        }
        for (PaintSlot slot : PaintSlot.values()) {
            String key = slot.key();
            if (key.contains("lead") || key.contains("cable") || key.contains("wire") || key.contains("plug")) {
                return StepResult.fail("Lack-Slot für das Kabel: " + key);
            }
        }
        ctx.log(String.join("; ", notes));
        return StepResult.pass();
    }

    private static @Nullable String paintChecks(DroneBuild build, List<String> notes) {
        if (!DroneVisuals.resources().assembly(build).powerLead().present()) {
            return "Stecker nicht ausgeblendet";
        }
        List<Integer> stackYaws = new ArrayList<>();
        List<Integer> oldYaws = new ArrayList<>();
        for (int yaw = 30; yaw < 360; yaw += 60) {
            PaintPicker.View view = PaintPicker.view(build, PartWear.PRISTINE, DronePaint.DEFAULT, 0, 0, 200, 150, yaw);
            if (view == null) {
                return "keine Vorschau";
            }
            if (PaintPicker.stackSlot(view.state()) != view.state().powerLead.stack) {
                return "Lack-Auswahl liegt nicht am _mounted-Stack";
            }
            List<Vector3fc> anchors = PaintPicker.anchors(view, PaintSlot.STACK);
            if (anchors.isEmpty()) {
                return "kein Stack-Anker";
            }
            if (stackHit(view, view.state().powerLead.stack, anchors.getFirst())) {
                stackYaws.add(yaw);
            }
            DroneRenderState state = view.state();
            state.powerLead.present = false;
            try {
                if (stackHit(view, state.stack, PaintPicker.anchors(view, PaintSlot.STACK).getFirst())) {
                    oldYaws.add(yaw);
                }
            } finally {
                state.powerLead.present = true;
            }
        }
        if (oldYaws.contains(DEFAULT_YAW) && !stackYaws.contains(DEFAULT_YAW)) {
            return "Klick auf den Stack ohne Stecker wählt in der Standard-Drehung " + DEFAULT_YAW + "° nicht den Stack (ohne Stecker bei "
                    + stackYaws + "°, mit Stecker bei " + oldYaws + "°)";
        }
        if (stackYaws.isEmpty() && !oldYaws.isEmpty()) {
            return "Klick auf den Stack ohne Stecker (Anker, Oberseite, Seiten) wählt in keiner Drehung der Vorschau den Stack, "
                    + "mit Stecker wie bisher bei " + oldYaws + "°";
        }
        DroneRenderState plain = DroneVisuals.previewState(build, PartWear.PRISTINE, DronePaint.DEFAULT);
        DroneRenderState painted = DroneVisuals.previewState(build, PartWear.PRISTINE, DronePaint.DEFAULT.with(PaintSlot.STACK, PAINT));
        if (plain == null || painted == null) {
            return "keine Vorschau mit Lack";
        }
        if (painted.powerLead.stack.item == plain.powerLead.stack.item) {
            return "Lack auf dem Stack ändert den _mounted-Stack nicht";
        }
        if (!painted.powerLead.shown(painted) || painted.powerLead.stackCables != plain.powerLead.stackCables
                || painted.powerLead.batteryPlug != plain.powerLead.batteryPlug) {
            return "mit Lack andere oder keine Masken für Akku-Stecker und Stack-Kabel";
        }
        notes.add(build.frame().key() + ": Stack (Anker, Oberseite, Seiten) → stack bei " + stackYaws + "° (mit Stecker wie bisher bei " + oldYaws
                + "°), Masken mit und ohne Lack gleich");
        return null;
    }

    private static boolean stackHit(PaintPicker.View view, DroneRenderState.PartSlot slot, Vector3fc anchor) {
        for (Vector3fc point : stackPoints(slot, anchor)) {
            Vector2f at = PaintPicker.project(view, point);
            Optional<PaintSlot> stack = PaintPicker.pick(view, at.x, at.y);
            if (stack.isPresent() && stack.get() == PaintSlot.STACK) {
                return true;
            }
        }
        return false;
    }

    private static List<Vector3fc> stackPoints(DroneRenderState.PartSlot slot, Vector3fc anchor) {
        List<Vector3fc> points = new ArrayList<>();
        points.add(anchor);
        if (!slot.useItem || slot.item.isEmpty()) {
            return points;
        }
        net.minecraft.world.phys.AABB box = slot.item.getModelBoundingBox();
        float mm = DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;
        float x0 = (float) box.minX * mm;
        float x1 = (float) box.maxX * mm;
        float y0 = (float) box.minY * mm;
        float y1 = (float) box.maxY * mm;
        float z0 = (float) box.minZ * mm;
        float z1 = (float) box.maxZ * mm;
        float ym = (y0 + y1) * 0.5F;
        for (float fx : new float[]{0.2F, 0.5F, 0.8F}) {
            for (float fz : new float[]{0.2F, 0.5F, 0.8F}) {
                points.add(new Vector3f(x0 + (x1 - x0) * fx, y1, z0 + (z1 - z0) * fz).add(slot.position));
            }
            points.add(new Vector3f(x0 + (x1 - x0) * fx, ym, z0).add(slot.position));
            points.add(new Vector3f(x0 + (x1 - x0) * fx, ym, z1).add(slot.position));
            points.add(new Vector3f(x0, ym, z0 + (z1 - z0) * fx).add(slot.position));
            points.add(new Vector3f(x1, ym, z0 + (z1 - z0) * fx).add(slot.position));
        }
        return points;
    }

    private static StepResult stackItemStep(SelfTest.StepContext ctx) {
        DroneRenderResources res = DroneVisuals.resources();
        Minecraft mc = Minecraft.getInstance();
        int stacks = 0;
        List<String> plugs = new ArrayList<>();
        for (StackType stack : StackType.values()) {
            if (!stack.builtin()) {
                continue;
            }
            Optional<net.minecraft.world.item.Item> item = BuiltInRegistries.ITEM.getOptional(stack.id());
            if (item.isEmpty()) {
                return StepResult.fail(stack.id() + ": kein Item");
            }
            Identifier part = PartItemRenderer.partId(new ItemStack(item.get()));
            if (!stack.id().equals(part)) {
                return StepResult.fail(stack.id() + ": Item-Renderer liest " + part);
            }
            PartModel base = PartModel.of(stack);
            ItemStackRenderState own = res.item(base);
            ItemStackRenderState mounted = res.item(PartModel.stackMounted(stack));
            if (own == null) {
                return StepResult.fail(base + ": kein Item-Modell");
            }
            PartItemRenderer.Icon icon = new PartItemRenderer.Icon(stack.id(), PropDamage.NONE);
            Map<String, PartItemRenderer.Fitted> fits = new LinkedHashMap<>();
            fits.put("GUI", PartItemRenderer.fitted(icon));
            fits.put("Hand", PartItemRenderer.handFitted(icon));
            fits.put("Item-Rahmen", PartItemRenderer.wallFitted(icon));
            fits.put("Boden (ItemEntity)", PartItemRenderer.groundFitted(icon));
            fits.put("Kiste", PartItemRenderer.boxFitted(icon));
            for (Map.Entry<String, PartItemRenderer.Fitted> entry : fits.entrySet()) {
                PartItemRenderer.Fitted fitted = entry.getValue();
                if (fitted.item() != own) {
                    return StepResult.fail(stack.id() + " " + entry.getKey() + ": zeichnet nicht das Grundmodell"
                            + (fitted.item() != null && fitted.item() == mounted ? " sondern _mounted" : ""));
                }
                if (!fitted.solid().isEmpty() || !fitted.translucent().isEmpty()) {
                    return StepResult.fail(stack.id() + " " + entry.getKey() + ": zusätzliche Form (Kabel?) am Stack-Item");
                }
            }
            String plug = plugIn(mc, base);
            if (plug == null) {
                return StepResult.fail(base + ": Grundmodell ohne eigenen Stecker oder mit Kabel");
            }
            plugs.add(stack.id().getPath() + " " + plug);
            stacks++;
        }
        ctx.log(stacks + " eingebaute Stacks: GUI, Hand, Item-Rahmen, Boden und Kiste mit dem Grundmodell; Stecker im Grundmodell: " + plugs);
        return StepResult.pass();
    }

    private static @Nullable String plugIn(Minecraft mc, PartModel model) {
        Identifier file = model.modelFile();
        if (file == null) {
            return null;
        }
        Optional<Resource> resource = mc.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            String plug = null;
            for (PlugAnchors.Element element : PlugAnchors.elements(json)) {
                if (PlugAnchors.PIGTAIL.equals(element.name())) {
                    return null;
                }
                if (PlugAnchors.PLUG_ELEMENTS.contains(element.name())) {
                    plug = element.name();
                }
            }
            return plug;
        } catch (Exception e) {
            return null;
        }
    }
}
