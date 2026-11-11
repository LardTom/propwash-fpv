package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.BatteryFit;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

final class BatteryPlugSelfTests {
    static final String PREFIX = "drone-render: Akku gesteckt";
    private static final float MM = 0.01F;
    private static final Identifier EXAMPLE_STACK = Identifier.fromNamespaceAndPath("propwash_example", "stack_30_8s");
    static final String HIDDEN_STEP = PREFIX + " – Stecker und Kabel an der Drohne ausgeblendet";
    private static final String WIRE = "wire";
    private static final String BALANCE_PLUG = "balance_plug";

    private BatteryPlugSelfTests() {
    }

    static void register() {
        SelfTest.clientStep(HIDDEN_STEP, 1, BatteryPlugSelfTests::hiddenStep);
        SelfTest.clientStep(PREFIX + " – Varianten und Masken aller eingebauten Teile auflösbar", 1, BatteryPlugSelfTests::anchorsStep);
        SelfTest.clientStep(PREFIX + " – Rückfall ohne _mounted", 1, BatteryPlugSelfTests::fallbackStep);
        BatteryPlugShowcase.register();
    }

    static DroneBuild build(FrameType frame, BatteryType battery, StackType stack) {
        DroneBuild preset = DroneBuild.PRESETS.stream().filter(b -> b.frame() == frame).findFirst().orElse(DroneBuild.FREESTYLE);
        return new DroneBuild(frame, stack, preset.motor(), preset.prop(), preset.video(), battery);
    }

    private static StepResult anchorsStep(SelfTest.StepContext ctx) {
        DroneRenderResources res = DroneVisuals.resources();
        int stacks = 0;
        for (StackType stack : StackType.values()) {
            if (!stack.builtin()) {
                continue;
            }
            PartModel mounted = PartModel.stackMounted(stack);
            if (!res.hasModel(mounted)) {
                return StepResult.fail(mounted + " fehlt");
            }
            if (res.mountedCables(mounted).boxes().size() != 2) {
                return StepResult.fail(mounted + ": keine zwei " + PlugAnchors.PIGTAIL + " oder Stecker noch im Modell (" + res.plugFallbacks() + ")");
            }
            if (!res.hasModel(mounted.paintVariant()) || res.paintGroups(mounted).isEmpty()) {
                return StepResult.fail(mounted.paintVariant() + " fehlt oder hat keine Lack-Gruppen");
            }
            stacks++;
        }
        int batteries = 0;
        for (BatteryType battery : BatteryType.values()) {
            if (!battery.builtin()) {
                continue;
            }
            for (PartModel model : List.of(PartModel.of(battery), PartModel.batteryUnder(battery))) {
                if (!res.hasModel(model)) {
                    continue;
                }
                int boxes = res.batteryPlug(model).boxes().size();
                if (boxes < 2) {
                    return StepResult.fail(model + ": Stecker und Kabel des Akkus nicht gefunden (" + boxes + " Quader)");
                }
                batteries++;
            }
        }
        int combinations = 0;
        for (DroneBuild build : combinations()) {
            if (!build.frame().builtin()) {
                continue;
            }
            String problem = plugged(res, build);
            if (problem != null) {
                return StepResult.fail(problem);
            }
            combinations++;
        }
        ctx.log(stacks + " Stacks mit _mounted, " + batteries + " Akku-Modelle mit Stecker, " + combinations
                + " gültige Kombinationen ohne sichtbare Stecker");
        return StepResult.pass();
    }

    static List<DroneBuild> combinations() {
        List<DroneBuild> out = new ArrayList<>();
        for (FrameType frame : FrameType.values()) {
            if (!frame.builtin()) {
                continue;
            }
            for (BatteryType battery : BatteryType.values()) {
                if (!battery.builtin() || !BatteryFit.check(frame, battery).allowed()) {
                    continue;
                }
                for (StackType stack : StackType.values()) {
                    if (stack.builtin() && stack.fits(frame)) {
                        out.add(build(frame, battery, stack));
                    }
                }
            }
        }
        for (DronePreset preset : PropwashApi.builds().presets()) {
            Conversions.build(preset.data().build()).filter(build -> !out.contains(build)).ifPresent(out::add);
        }
        if (PartIndex.registeredById(RenderExampleParts.X_CLASS_SCALED.id()).isPresent()) {
            out.add(scaledBuild());
        }
        return out;
    }

    private static StepResult hiddenStep(SelfTest.StepContext ctx) {
        DroneRenderResources res = DroneVisuals.resources();
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        int checked = 0;
        int plugFaces = 0;
        int cableFaces = 0;
        for (DroneBuild build : combinations()) {
            String name = build.frame().id() + " + " + build.battery().id() + " + " + build.stack().id();
            DroneAssembly assembly = res.assembly(build);
            if (!assembly.powerLead().present()) {
                continue;
            }
            DroneRenderState state = new DroneRenderState();
            DroneVisuals.fill(state, build, false, true, 0.0F);
            if (!state.powerLead.shown(state) || !state.battery.useItem || !state.powerLead.stack.useItem) {
                return StepResult.fail(name + ": Drohne mit Akku zeigt den Stack ohne Stecker nicht");
            }
            Set<BakedQuad> battery = PlugSubmitRecorder.quadsOf(state.battery.item);
            Set<BakedQuad> plug = inside(battery, plugBoxes(manager, assembly.battery().model()));
            Set<BakedQuad> cables = inside(PlugSubmitRecorder.quadsOf(state.powerLead.stack.item),
                    namedBoxes(manager, assembly.powerLead().stack().model(), PlugAnchors.PIGTAIL));
            if (plug.isEmpty() || cables.isEmpty()) {
                return StepResult.fail(name + ": Akku-Stecker (" + plug.size() + " Flächen) oder Stack-Kabel (" + cables.size()
                        + ") im Modell nicht gefunden");
            }
            PlugSubmitRecorder drawn = new PlugSubmitRecorder();
            DroneVisuals.submit(state, new PoseStack(), drawn);
            int plugDrawn = count(drawn.quads, plug);
            int cablesDrawn = count(drawn.quads, cables);
            int restMissing = battery.size() - plug.size() - count(drawn.quads, battery) + plugDrawn;
            int extraShapes = drawn.solidGeometry - (state.antenna.solid.isEmpty() ? 0 : 1);
            if (plugDrawn > 0 || cablesDrawn > 0 || extraShapes > 0) {
                return StepResult.fail(String.format(Locale.ROOT, "%s: Akku-Stecker %d von %d Flächen, Stack-Kabel %d von %d Flächen "
                        + "und %d Zusatzformen (Steckerpaar, Kabel) gezeichnet, erwartet 0", name, plugDrawn, plug.size(), cablesDrawn,
                        cables.size(), extraShapes));
            }
            if (restMissing != 0) {
                return StepResult.fail(name + ": " + restMissing + " Flächen des Akkus ohne Stecker fehlen");
            }
            checked++;
            plugFaces += plug.size();
            cableFaces += cables.size();
        }
        if (checked == 0) {
            return StepResult.fail("keine Drohne mit Akku und Stack _mounted gefunden");
        }
        ctx.log(checked + " Bauten mit Akku: " + plugFaces + " Flächen von Akku-Steckern und " + cableFaces
                + " Flächen von Stack-Kabeln ausgeblendet, keine Zusatzform, Rest des Akkus vollständig");
        return StepResult.pass();
    }

    static List<PlugAnchors.Box> plugBoxes(ResourceManager manager, @Nullable PartModel battery) {
        List<PlugAnchors.Element> elements = battery == null ? List.of()
                : DroneRenderResources.modelJson(manager, battery).map(PlugAnchors::elements).orElse(List.of());
        List<PlugAnchors.Box> plugs = new ArrayList<>();
        for (PlugAnchors.Element element : elements) {
            if (PlugAnchors.PLUG_ELEMENTS.contains(element.name()) || element.name().equals(BALANCE_PLUG)) {
                plugs.add(element.box());
            }
        }
        List<PlugAnchors.Box> out = new ArrayList<>(plugs);
        for (PlugAnchors.Element element : elements) {
            if (element.name().equals(WIRE) && plugs.stream().anyMatch(plug -> touches(element.box(), plug))) {
                out.add(element.box());
            }
        }
        return out;
    }

    static List<PlugAnchors.Box> namedBoxes(ResourceManager manager, @Nullable PartModel model, String name) {
        List<PlugAnchors.Box> out = new ArrayList<>();
        if (model != null) {
            for (PlugAnchors.Element element : DroneRenderResources.modelJson(manager, model).map(PlugAnchors::elements).orElse(List.of())) {
                if (element.name().equals(name)) {
                    out.add(element.box());
                }
            }
        }
        return out;
    }

    private static boolean touches(PlugAnchors.Box a, PlugAnchors.Box b) {
        return a.minX() <= b.maxX() + MM && b.minX() <= a.maxX() + MM && a.minY() <= b.maxY() + MM && b.minY() <= a.maxY() + MM
                && a.minZ() <= b.maxZ() + MM && b.minZ() <= a.maxZ() + MM;
    }

    static Set<BakedQuad> inside(Set<BakedQuad> quads, List<PlugAnchors.Box> boxes) {
        Set<BakedQuad> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BakedQuad quad : quads) {
            for (PlugAnchors.Box box : boxes) {
                if (inside(quad, box)) {
                    out.add(quad);
                    break;
                }
            }
        }
        return out;
    }

    private static boolean inside(BakedQuad quad, PlugAnchors.Box box) {
        for (int v = 0; v < 4; v++) {
            Vector3fc p = quad.position(v);
            float x = PlugAnchors.millimetres(p.x() * DroneVisualScale.MODEL_UNITS_PER_BLOCK);
            float y = PlugAnchors.millimetres(p.y() * DroneVisualScale.MODEL_UNITS_PER_BLOCK);
            float z = PlugAnchors.millimetres(p.z() * DroneVisualScale.MODEL_UNITS_PER_BLOCK);
            if (!box.contains(x, y, z, MM)) {
                return false;
            }
        }
        return true;
    }

    private static int count(Set<BakedQuad> drawn, Set<BakedQuad> wanted) {
        int n = 0;
        for (BakedQuad quad : wanted) {
            if (drawn.contains(quad)) {
                n++;
            }
        }
        return n;
    }

    static @Nullable String plugged(DroneRenderResources res, DroneBuild build) {
        String name = build.frame().key() + " + " + build.battery().key() + " + " + build.stack().key();
        DroneAssembly assembly = res.assembly(build);
        PowerLead lead = assembly.powerLead();
        if (!lead.present()) {
            return name + ": Stecker nicht ausgeblendet";
        }
        if (lead.stack().model() != PartModel.stackMounted(build.stack()) || lead.stack().item() == null) {
            return name + ": Stack zeichnet " + lead.stack().model() + " statt der Variante _mounted";
        }
        if (lead.batteryPlug() != res.batteryPlug(assembly.battery().model()) || lead.stackCables() != res.mountedCables(lead.stack().model())) {
            return name + ": Masken passen nicht zu Akku " + assembly.battery().model() + " und Stack " + lead.stack().model();
        }
        DroneRenderState state = new DroneRenderState();
        DroneVisuals.fill(state, build, false, true, 0.0F);
        if (!state.powerLead.present || !state.powerLead.shown(state) || !state.powerLead.stack.useItem || state.powerLead.batteryPlug.isEmpty()
                || state.powerLead.stackCables.isEmpty()) {
            return name + ": Zustand blendet die Stecker nicht aus";
        }
        state.batteryMissing = true;
        if (state.powerLead.shown(state) || PaintPicker.stackSlot(state) != state.stack) {
            return name + ": ohne Akku bleibt der Stack ohne Stecker";
        }
        return null;
    }

    static DroneBuild scaledBuild() {
        return new DroneBuild(RenderExampleParts.X_CLASS_SCALED, StackType.STACK_30, RenderExampleParts.M4214, RenderExampleParts.P13,
                RenderExampleParts.HD_PRO, BatteryType.B1S_300_HV);
    }

    private static StepResult fallbackStep(SelfTest.StepContext ctx) {
        DroneRenderResources res = DroneVisuals.resources();
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        PartModel replacedModel = PartModel.of(StackType.STACK_30);
        PartModel replacedItem = PartModel.of(StackType.AIO_20);
        Identifier modelFile = replacedModel.modelFile();
        if (modelFile == null) {
            return StepResult.fail(replacedModel + ": kein Modellpfad");
        }
        Optional<Resource> original = manager.getResource(modelFile);
        Optional<Resource> originalItem = manager.getResource(replacedItem.itemModelFile());
        if (original.isEmpty() || originalItem.isEmpty()) {
            return StepResult.fail("Grundmodelle der Stacks fehlen");
        }
        Optional<PackResources> other = manager.listPacks().filter(pack -> !pack.packId().equals(original.get().sourcePackId())).findFirst();
        if (other.isEmpty()) {
            return StepResult.fail("kein zweites Ressourcenpaket geladen");
        }
        Map<Identifier, Resource> replaced = Map.of(
                modelFile, new Resource(other.get(), original.get()::open),
                replacedItem.itemModelFile(), new Resource(other.get(), originalItem.get()::open));
        DroneRenderResources.Plugs plugs = DroneRenderResources.readPlugs(new Overlay(manager, replaced));
        PartModel mounted = PartModel.stackMounted(StackType.STACK_30);
        if (plugs.cables().containsKey(mounted) || !plugs.fallbacks().containsKey(mounted)) {
            return StepResult.fail("Paket ersetzt nur das Grundmodell von stack_30, trotzdem wird " + mounted + " benutzt");
        }
        PartModel mountedAio = PartModel.stackMounted(StackType.AIO_20);
        if (plugs.cables().containsKey(mountedAio)) {
            return StepResult.fail("Paket ersetzt nur das Render-Item von aio_20, trotzdem wird " + mountedAio + " benutzt");
        }
        PartModel untouched = PartModel.stackMounted(StackType.AIO_WHOOP);
        if (!plugs.cables().containsKey(untouched)) {
            return StepResult.fail(untouched + " fällt ohne Grund zurück: " + plugs.fallbacks());
        }
        if (!DroneRenderResources.readPlugs(manager).cables().containsKey(mounted)) {
            return StepResult.fail("ohne Testpaket fällt " + mounted + " zurück");
        }
        ctx.log("Testpaket " + other.get().packId() + ": " + plugs.fallbacks());

        Optional<StackType> example = PartIndex.registeredById(EXAMPLE_STACK).filter(StackType.class::isInstance).map(StackType.class::cast);
        if (example.isPresent()) {
            DroneBuild build = build(FrameType.FREESTYLE_5, BatteryType.B6S_1300, example.get());
            DroneAssembly assembly = res.assembly(build);
            if (assembly.powerLead().present()) {
                return StepResult.fail(EXAMPLE_STACK + " hat kein _mounted und wird trotzdem gesteckt");
            }
            DroneRenderState state = new DroneRenderState();
            DroneVisuals.fill(state, build, false, true, 0.0F);
            if (state.powerLead.shown(state) || PaintPicker.stackSlot(state) != state.stack) {
                return StepResult.fail(EXAMPLE_STACK + ": Zustand blendet Stecker aus, obwohl der Stack kein _mounted hat");
            }
        } else {
            ctx.log("Beispiel-Stack " + EXAMPLE_STACK + " nicht angemeldet, Add-on-Fall übersprungen");
        }
        DroneAssembly whole = res.assembly(DroneBuild.FREESTYLE);
        DroneRenderState state = new DroneRenderState();
        DroneVisuals.fill(state, DroneBuild.FREESTYLE, false, true, 0.0F);
        state.batteryMissing = true;
        if (state.powerLead.shown(state) || whole.stack().model() != PartModel.of(StackType.STACK_30)) {
            return StepResult.fail("ohne Akku zeigt die Drohne nicht den Stack wie bisher");
        }
        Constants.LOG.info("Selbsttest {}: Rückfall geprüft", PREFIX);
        return StepResult.pass();
    }

    private record Overlay(ResourceManager delegate, Map<Identifier, Resource> replaced) implements ResourceManager {
        @Override
        public Optional<Resource> getResource(Identifier id) {
            Resource resource = this.replaced.get(id);
            return resource != null ? Optional.of(resource) : this.delegate.getResource(id);
        }

        @Override
        public Set<String> getNamespaces() {
            return this.delegate.getNamespaces();
        }

        @Override
        public List<Resource> getResourceStack(Identifier id) {
            return this.delegate.getResourceStack(id);
        }

        @Override
        public Map<Identifier, Resource> listResources(String directory, ResourceManager.Selector filter) {
            return this.delegate.listResources(directory, filter);
        }

        @Override
        public Map<Identifier, List<Resource>> listResourceStacks(String directory, ResourceManager.Selector filter) {
            return this.delegate.listResourceStacks(directory, filter);
        }

        @Override
        public Stream<PackResources> listPacks() {
            return this.delegate.listPacks();
        }
    }
}
