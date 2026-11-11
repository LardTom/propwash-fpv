package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.lardtom.propwash.api.part.VideoAntenna;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.AntennaRules;
import de.lardtom.propwash.build.BatteryFit;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class AntennaSelfTests {
    public static final String PREFIX = "antenne: ";
    static final float MIN_RISE_MM = 15.0F;
    static final float MIN_REACH_MM = 6.0F;
    static final float MARGIN_MM = 0.5F;
    static final float PROP_TOP_MM = 8.0F;
    static final int[] TILTS = {0, 20, 40, 60, 80};
    private static final int SETTLE_TICKS = 60;
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static @Nullable ItemStack previousHead;
    private static @Nullable ItemStack previousHand;

    private AntennaSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + "Antennen aller VTX hinten an jedem Rahmen, abgeleitet aus den VTX-Daten", 1, AntennaSelfTests::mountStep);
        SelfTest.clientStep(PREFIX + "Antennen frei von Akku, Props, Kamera und Zubehör", 1, AntennaSelfTests::clearanceStep);
        SelfTest.clientStep(PREFIX + "Antennen 3/4 von hinten und seitlich vor freiem Grund, höchstens 1,2 × Radstand", 1, AntennaSelfTests::visibilityStep);
        SelfTest.clientStep(PREFIX + "Antennen-Elemente der Add-on-VTX ausgelassen", 1, AntennaSelfTests::addonModelStep);
        SelfTest.clientStep(PREFIX + "Antenne nur von außen, nicht im eigenen FPV-Bild", 1, AntennaSelfTests::fpvRenderStep);
        AntennaShowcase.registerSheets(PREFIX);
        SelfTest.clientStep(PREFIX + "FPV-Flug mit der eigenen Drohne ohne Antenne im Bild", 900, AntennaSelfTests::fpvFlightStep);
        SelfTest.clientStep(PREFIX + "FPV-Flug aufräumen", 300, AntennaSelfTests::cleanupStep);
        AntennaShowcase.registerRestore(PREFIX);
    }

    static List<VideoType> videos() {
        return List.of(VideoType.values());
    }

    static List<VideoType> builtinVideos() {
        List<VideoType> out = new ArrayList<>();
        for (VideoType video : VideoType.values()) {
            if (video.builtin()) {
                out.add(video);
            }
        }
        return out;
    }

    static List<FrameType> frames() {
        return List.of(FrameType.values());
    }

    static boolean addonVideosPresent() {
        for (VideoType video : VideoType.values()) {
            if (!video.builtin()) {
                return true;
            }
        }
        return false;
    }

    static DroneBuild with(DroneBuild preset, VideoType video) {
        return new DroneBuild(preset.frame(), preset.stack(), preset.motor(), preset.prop(), video, preset.battery());
    }

    static DroneBuild with(FrameType frame, VideoType video) {
        DroneBuild base = DroneBuild.FREESTYLE;
        for (DroneBuild preset : DroneBuild.PRESETS) {
            if (preset.frame() == frame) {
                base = preset;
            }
        }
        if (base.frame() == frame) {
            return with(base, video);
        }
        DroneBuild nearest = DroneBuild.FREESTYLE;
        for (DroneBuild preset : DroneBuild.PRESETS) {
            if (Math.abs(preset.frame().wheelbaseMm - frame.wheelbaseMm) < Math.abs(nearest.frame().wheelbaseMm - frame.wheelbaseMm)) {
                nearest = preset;
            }
        }
        List<BatteryType> batteries = BatteryFit.fitting(frame);
        BatteryType battery = batteries.isEmpty() ? nearest.battery() : batteries.getFirst();
        return new DroneBuild(frame, nearest.stack(), nearest.motor(), nearest.prop(), video, battery);
    }

    static String label(DroneBuild build) {
        return build.frame().id().getPath() + " + " + build.video().id().getPath();
    }

    static String label(FrameType frame, VideoType video) {
        return frame.id() + " + " + video.id();
    }

    static VideoAntenna spec(FrameType frame, VideoType video) {
        return AntennaRules.antenna(video.definition(), frame.definition());
    }

    static List<Vector3f> antennaPoints(FrameType frame, VideoType video, Vector3fc anchor, boolean withMount) {
        VideoAntenna spec = spec(frame, video);
        List<Vector3f> out = new ArrayList<>();
        for (AntennaGeometry.Piece piece : AntennaGeometry.pieces(spec, DroneLayout.antennaTilt(frame), frame.wheelbaseMm,
                AntennaRules.pairLeanDeg(frame.definition()))) {
            if (!withMount && piece.name().equals("mount")) {
                continue;
            }
            for (Vector3f p : piece.samples(10)) {
                out.add(p.add(anchor));
            }
        }
        return out;
    }

    static float[] shapeRange(DroneAssembly.Part part) {
        Shape shape = part.solid();
        float mm = DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;
        Vector3fc at = part.position();
        return new float[]{shape.min().y() * mm + at.y(), shape.max().y() * mm + at.y(), shape.max().z() * mm + at.z()};
    }

    private static StepResult mountStep(SelfTest.StepContext ctx) {
        DroneRenderResources res = DroneVisuals.resources();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        int fallback = 0;
        int slid = 0;
        for (FrameType frame : frames()) {
            DroneLayout layout = res.layout(frame);
            Vector3fc anchor = layout.antenna(frame);
            if (frame.builtin() && !AntennaRules.explicitAnchor(frame.definition())) {
                problems.add(frame.id() + ": Antennenhalter fehlt im Kern-Rahmen");
            }
            if (!AntennaRules.explicitAnchor(frame.definition())) {
                fallback++;
            }
            for (VideoType video : videos()) {
                DroneBuild build = with(frame, video);
                String label = label(frame, video);
                VideoAntenna spec = spec(frame, video);
                if (spec.lengthMm() > AntennaRules.maxLengthMm(frame.definition())) {
                    problems.add(String.format(Locale.ROOT, "%s: %.0f mm lang, Radstand %d mm", label, spec.lengthMm(), frame.wheelbaseMm));
                }
                DroneAssembly assembly = res.assembly(build);
                DroneAssembly.Part antenna = assembly.antenna();
                if (antenna.solid().isEmpty()) {
                    problems.add(label + ": ohne Antenne");
                    continue;
                }
                float behind = DroneAssembly.behindPackMm(res, layout, frame, assembly.battery());
                if (antenna.position().distance(new Vector3f(anchor).add(0.0F, 0.0F, behind)) > 1.0E-4F) {
                    problems.add(label + ": Antenne bei " + antenna.position() + " statt am Halter " + anchor + " (+" + behind + " mm hinter den Akku)");
                }
                if (behind > 0.0F) {
                    slid++;
                }
                float[] range = shapeRange(antenna);
                float rise = range[1] - anchor.y();
                double cosTilt = Math.cos(Math.toRadians(DroneLayout.antennaTilt(frame)));
                if (rise < Math.max(MIN_RISE_MM, 0.6F * spec.lengthMm() * cosTilt)) {
                    problems.add(String.format(Locale.ROOT, "%s: Antenne nur %.1f mm über dem Halter (%.0f mm lang)", label, rise, spec.lengthMm()));
                }
                if (range[2] < anchor.z() + MIN_REACH_MM) {
                    problems.add(String.format(Locale.ROOT, "%s: Antenne nur %.1f mm nach hinten", label, range[2] - anchor.z()));
                }
                if (range[1] - anchor.y() > spec.lengthMm() + 12.0F) {
                    problems.add(String.format(Locale.ROOT, "%s: Antenne %.1f mm hoch, länger als %.0f mm", label, rise, spec.lengthMm()));
                }
                if (assembly.hull().max().y() + 0.01F < range[1]) {
                    problems.add(label + ": Hülle endet unter der Antennenspitze, Vorschauen schneiden sie ab");
                }
                DroneRenderState preview = DroneVisuals.previewState(build);
                if (preview == null || preview.antenna.solid.isEmpty() || preview.antenna.useItem) {
                    problems.add(label + ": Werkbank-Vorschau ohne Antenne");
                }
                DroneRenderState tilted = DroneVisuals.previewState(build, PartWear.PRISTINE, DronePaint.DEFAULT, 60.0F);
                if (tilted == null || tilted.antenna.position.distance(antenna.position()) > 1.0E-4F || tilted.antenna.solid != preview.antenna.solid) {
                    problems.add(label + ": Antenne wandert oder dreht mit dem Kamerawinkel");
                }
                DroneRenderState painted = DroneVisuals.previewState(build, PartWear.PRISTINE, DronePaint.DEFAULT.with(PaintSlot.ANTENNA, 0x20C0FF));
                if (painted == null || painted.antenna.solid == antenna.solid()) {
                    problems.add(label + ": Lack für die Antennenkappen greift nicht");
                }
                if (!PaintPicker.available(build).contains(PaintSlot.ANTENNA)) {
                    problems.add(label + ": Lack-Platz Antenne fehlt in der Werkbank");
                }
                checked++;
            }
        }
        ctx.log(checked + " Kombinationen aus " + frames().size() + " Rahmen (" + fallback + " mit Rückfall-Halter) und " + videos().size()
                + " VTX: Antenne aus den VTX-Daten am Halter (" + slid + " hinter einen überstehenden Akku geschoben), fest beim Kamerawinkel, lackierbar");
        return StepResult.check(problems.isEmpty() && checked == frames().size() * videos().size(), String.join("; ", problems));
    }

    record Obstacle(String name, AABB box) {
    }

    static AABB bounds(DroneAssembly.Part part, float tiltDegrees, float stretchX) {
        ItemStackRenderState item = part.item();
        float mm = DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;
        AABB raw;
        if (item != null && !item.isEmpty()) {
            raw = item.getModelBoundingBox();
        } else if (!part.solid().isEmpty()) {
            raw = new AABB(part.solid().min().x(), part.solid().min().y(), part.solid().min().z(), part.solid().max().x(), part.solid().max().y(),
                    part.solid().max().z());
        } else {
            return new AABB(0, 0, 0, 0, 0, 0);
        }
        Quaternionf rotation = new Quaternionf().rotationX((float) Math.toRadians(tiltDegrees));
        Vector3f lo = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f hi = new Vector3f(Float.NEGATIVE_INFINITY);
        for (int c = 0; c < 8; c++) {
            Vector3f p = new Vector3f((float) ((c & 1) == 0 ? raw.minX : raw.maxX) * mm * stretchX, (float) ((c & 2) == 0 ? raw.minY : raw.maxY) * mm,
                    (float) ((c & 4) == 0 ? raw.minZ : raw.maxZ) * mm);
            rotation.transform(p).add(part.position());
            lo.min(p);
            hi.max(p);
        }
        return new AABB(lo.x, lo.y, lo.z, hi.x, hi.y, hi.z);
    }

    static List<Obstacle> batteryObstacles(DroneRenderResources res, DroneBuild build) {
        DroneAssembly.Part part = DroneAssembly.batteryPart(res, res.layout(build.frame()), build.battery());
        List<Obstacle> out = new ArrayList<>();
        List<PlugAnchors.Element> elements = drawnBatteryElements(res, build, part);
        if (elements.isEmpty()) {
            out.add(new Obstacle("Akku " + build.battery().id(), bounds(part, 0.0F, 1.0F)));
        }
        for (PlugAnchors.Element element : elements) {
            PlugAnchors.Box b = element.box();
            Vector3fc at = part.position();
            out.add(new Obstacle("Akku " + build.battery().id() + " (" + element.name() + ")", new AABB(b.minX() + at.x(), b.minY() + at.y(),
                    b.minZ() + at.z(), b.maxX() + at.x(), b.maxY() + at.y(), b.maxZ() + at.z())));
        }
        return out;
    }

    static List<Obstacle> obstacles(DroneRenderResources res, FrameType frame, DroneAssembly assembly) {
        DroneLayout layout = res.layout(frame);
        List<Obstacle> out = new ArrayList<>(batteryObstacles(res, assembly.build()));
        out.addAll(cameraObstacles(assembly));
        for (AccessoryMount mount : frame.accessoryMounts) {
            AccessoryAnchor anchor = layout.accessory(mount).orElseGet(() -> AccessoryAnchor.of(frame, mount));
            for (AccessoryType type : AccessoryType.values()) {
                if (!type.fits(mount) || !type.fitsFrame(frame)) {
                    continue;
                }
                double[] origin = anchor.modelOrigin(type);
                Vector3f position = new Vector3f((float) origin[0], (float) origin[1], (float) origin[2]);
                ItemStackRenderState item = res.item(PartModel.of(type));
                DroneAssembly.Part body = item != null ? new DroneAssembly.Part(item, Shape.EMPTY, Shape.EMPTY, position)
                        : new DroneAssembly.Part(null, FallbackShapes.accessory(type), Shape.EMPTY, position);
                out.add(new Obstacle("Zubehör " + type.id(), bounds(body, (float) anchor.tiltFor(type), (float) anchor.stretch(type))));
            }
        }
        return out;
    }

    static List<Obstacle> cameraObstacles(DroneAssembly assembly) {
        DroneAssembly.Part video = assembly.video();
        List<PlugAnchors.Element> elements = video.model() == null ? List.of()
                : DroneRenderResources.elements(Minecraft.getInstance().getResourceManager(), video.model()).orElse(List.of());
        List<PlugAnchors.Element> camera = new ArrayList<>();
        for (PlugAnchors.Element element : elements) {
            String name = element.name().toLowerCase(Locale.ROOT);
            if (name.startsWith("cam") || name.startsWith("lens")) {
                camera.add(element);
            }
        }
        if (camera.isEmpty()) {
            camera = elements;
        }
        if (camera.isEmpty()) {
            return List.of(new Obstacle("Kamera und VTX", bounds(video, assembly.cameraTiltDegrees(), 1.0F)));
        }
        Quaternionf tilt = new Quaternionf().rotationX((float) Math.toRadians(assembly.cameraTiltDegrees()));
        List<Obstacle> out = new ArrayList<>(camera.size());
        for (PlugAnchors.Element element : camera) {
            PlugAnchors.Box b = element.box();
            Vector3f lo = new Vector3f(Float.POSITIVE_INFINITY);
            Vector3f hi = new Vector3f(Float.NEGATIVE_INFINITY);
            for (Vector3f corner : AntennaVisibility.corners(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                tilt.transform(corner).add(video.position());
                lo.min(corner);
                hi.max(corner);
            }
            out.add(new Obstacle("Kamera (" + element.name() + ")", new AABB(lo.x, lo.y, lo.z, hi.x, hi.y, hi.z)));
        }
        return out;
    }

    static @Nullable String propHit(DroneRenderResources res, FrameType frame, DroneAssembly assembly, List<Vector3f> points) {
        float radius = frame.maxPropMm * 0.5F + 1.5F;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            DroneAssembly.Part prop = assembly.prop(slot);
            Vector3fc hub = prop.position();
            float top = prop.model() != null ? res.top(prop.model(), PROP_TOP_MM) : PROP_TOP_MM;
            float upper = hub.y() + Math.max(2.0F, Math.min(PROP_TOP_MM, top)) + 2.0F;
            for (Vector3f p : points) {
                float dx = p.x - hub.x();
                float dz = p.z - hub.z();
                if (dx * dx + dz * dz < radius * radius && p.y > hub.y() - 3.0F && p.y < upper) {
                    return String.format(Locale.ROOT, "Prop-Kreis an Motor %d bei (%.1f, %.1f, %.1f), Nabe (%.1f, %.1f, %.1f), Radius %.1f", slot, p.x, p.y,
                            p.z, hub.x(), hub.y(), hub.z(), radius);
                }
            }
        }
        return null;
    }

    static List<String> clearance(DroneRenderResources res, FrameType frame, VideoType video) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (BatteryType battery : BatteryFit.fitting(frame)) {
            DroneBuild build = with(frame, video).withBattery(battery);
            DroneAssembly assembly = res.assembly(build);
            List<Vector3f> points = antennaPoints(frame, video, assembly.antenna().position(), false);
            String where = label(frame, video) + " (" + battery.id().getPath() + "): ";
            for (Obstacle obstacle : obstacles(res, frame, assembly)) {
                AABB box = obstacle.box().inflate(-MARGIN_MM);
                for (Vector3f p : points) {
                    if (box.contains(p.x, p.y, p.z)) {
                        out.add(String.format(Locale.ROOT, "%sAntenne durch %s bei (%.1f, %.1f, %.1f), Grenzen (%.1f, %.1f, %.1f)–(%.1f, %.1f, %.1f)",
                                where, obstacle.name(), p.x, p.y, p.z, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
                        break;
                    }
                }
            }
            String prop = propHit(res, frame, assembly, points);
            if (prop != null && seen.add(prop)) {
                out.add(where + "Antenne im " + prop);
            }
        }
        return out;
    }

    private static StepResult clearanceStep(SelfTest.StepContext ctx) {
        DroneRenderResources res = DroneVisuals.resources();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (FrameType frame : frames()) {
            for (VideoType video : videos()) {
                problems.addAll(clearance(res, frame, video));
                checked++;
            }
        }
        ctx.log(checked + " Kombinationen gegen jeden passenden Akku, die Prop-Kreise des größten Props, Kamera und VTX und jedes passende Zubehör geprüft");
        return StepResult.check(problems.isEmpty(), String.join("; ", problems));
    }

    static final float ADDON_SIDE_FREE = 0.8F;
    static final float ADDON_QUARTER_FREE = 0.4F;

    static List<PlugAnchors.Element> drawnBatteryElements(DroneRenderResources res, DroneBuild build, DroneAssembly.Part part) {
        if (part.model() == null) {
            return List.of();
        }
        List<PlugAnchors.Element> elements = DroneRenderResources.elements(Minecraft.getInstance().getResourceManager(), part.model())
                .orElse(List.of());
        if (!res.assembly(build).powerLead().present()) {
            return elements;
        }
        List<PlugAnchors.Box> hidden = PlugAnchors.batteryPlug(elements);
        List<PlugAnchors.Element> out = new ArrayList<>(elements.size());
        for (PlugAnchors.Element element : elements) {
            if (!hidden.contains(element.box())) {
                out.add(element);
            }
        }
        return out;
    }

    static List<List<Vector3f>> batteryCorners(DroneRenderResources res, DroneBuild build) {
        DroneLayout layout = res.layout(build.frame());
        DroneAssembly.Part part = DroneAssembly.batteryPart(res, layout, build.battery());
        Vector3fc at = part.position();
        List<List<Vector3f>> out = new ArrayList<>();
        for (PlugAnchors.Element element : drawnBatteryElements(res, build, part)) {
            PlugAnchors.Box b = element.box();
            out.add(AntennaVisibility.corners(b.minX() + at.x(), b.minY() + at.y(), b.minZ() + at.z(), b.maxX() + at.x(), b.maxY() + at.y(),
                    b.maxZ() + at.z()));
        }
        if (out.isEmpty()) {
            AABB box = bounds(part, 0.0F, 1.0F);
            out.add(AntennaVisibility.corners((float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY,
                    (float) box.maxZ));
        }
        return out;
    }

    record Share(String label, float side, float quarter, int count) {
    }

    static Share share(DroneRenderResources res, FrameType frame, VideoType video) {
        DroneBuild build = with(frame, video);
        DroneLayout layout = res.layout(frame);
        List<List<Vector3f>> pack = batteryCorners(res, build);
        VideoAntenna spec = spec(frame, video);
        Vector3fc at = res.assembly(build).antenna().position();
        double tilt = DroneLayout.antennaTilt(frame);
        return new Share(label(frame, video) + " (" + build.battery().id().getPath() + ")",
                AntennaVisibility.worstShare(spec, tilt, frame.wheelbaseMm, AntennaRules.pairLeanDeg(frame.definition()), at, pack,
                        AntennaVisibility.SIDE_VIEWS),
                AntennaVisibility.worstShare(spec, tilt, frame.wheelbaseMm, AntennaRules.pairLeanDeg(frame.definition()), at, pack,
                        AntennaVisibility.QUARTER_VIEWS), spec.count());
    }

    private static StepResult visibilityStep(SelfTest.StepContext ctx) {
        DroneRenderResources res = DroneVisuals.resources();
        List<String> problems = new ArrayList<>();
        List<Share> addonShares = new ArrayList<>();
        int checked = 0;
        for (FrameType frame : frames()) {
            for (VideoType video : videos()) {
                Share share = share(res, frame, video);
                float side = frame.builtin() ? AntennaVisibility.SIDE_FREE : ADDON_SIDE_FREE;
                float quarter = !frame.builtin() ? ADDON_QUARTER_FREE
                        : share.count() == 1 ? AntennaVisibility.QUARTER_FREE_SINGLE : AntennaVisibility.QUARTER_FREE_PAIR;
                if (share.side() < side || share.quarter() < quarter) {
                    problems.add(String.format(Locale.ROOT, "%s: seitlich %.0f %%, 3/4 von hinten %.0f %% vor freiem Grund", share.label(),
                            share.side() * 100.0F, share.quarter() * 100.0F));
                }
                if (!frame.builtin()) {
                    addonShares.add(share);
                }
                DroneAssembly.Part part = res.assembly(with(frame, video)).antenna();
                Vector3fc at = part.position();
                float reach = 0.0F;
                Vector3f p = new Vector3f();
                for (int v = 0; v < part.solid().vertexCount(); v++) {
                    part.solid().position(v, p);
                    p.mul(DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK).add(part.position());
                    reach = Math.max(reach, p.distance(at));
                }
                if (reach > AntennaRules.maxLengthMm(frame.definition()) + 4.0F) {
                    problems.add(String.format(Locale.ROOT, "%s: Antenne reicht %.0f mm weit bei %d mm Radstand", label(frame, video), reach,
                            frame.wheelbaseMm));
                }
                checked++;
            }
        }
        addonShares.sort((a, b) -> Float.compare(Math.min(a.side(), a.quarter()), Math.min(b.side(), b.quarter())));
        for (Share share : addonShares.subList(0, Math.min(6, addonShares.size()))) {
            ctx.log(String.format(Locale.ROOT, "knappste Add-on-Kombination %s: seitlich %.0f %%, 3/4 von hinten %.0f %%", share.label(),
                    share.side() * 100.0F, share.quarter() * 100.0F));
        }
        ctx.log(checked + " Kombinationen mit dem Akku der Bildschirmfotos: Kern-Rahmen seitlich je Antenne mindestens "
                + Math.round(AntennaVisibility.SIDE_FREE * 100) + " %, 3/4 von hinten einzeln " + Math.round(AntennaVisibility.QUARTER_FREE_SINGLE * 100)
                + " %, im Paar je " + Math.round(AntennaVisibility.QUARTER_FREE_PAIR * 100) + " % vor freiem Grund; Add-on-Rahmen "
                + Math.round(ADDON_SIDE_FREE * 100) + " % bzw. " + Math.round(ADDON_QUARTER_FREE * 100) + " %; Antenne höchstens 1,2 × Radstand");
        return StepResult.check(problems.isEmpty(), String.join("; ", problems));
    }

    private static StepResult addonModelStep(SelfTest.StepContext ctx) {
        DroneRenderResources res = DroneVisuals.resources();
        List<String> problems = new ArrayList<>();
        int stripped = 0;
        List<VideoType> checked = new ArrayList<>(videos());
        checked.add(RenderExampleParts.HD_PRO);
        for (VideoType video : checked) {
            PartModel model = PartModel.of(video);
            AntennaMask mask = res.antennaMask(model);
            if (video.builtin()) {
                if (!mask.isEmpty()) {
                    problems.add(model.name() + ": Kern-VTX-Modell trägt noch Antennen-Elemente");
                }
                continue;
            }
            ItemStackRenderState full = res.item(model);
            ItemStackRenderState drone = res.droneItem(model);
            if (full == null || drone == null) {
                continue;
            }
            Set<BakedQuad> before = PlugSubmitRecorder.quadsOf(full);
            Set<BakedQuad> after = PlugSubmitRecorder.quadsOf(drone);
            int hidden = before.size() - after.size();
            if (mask.isEmpty()) {
                if (hidden != 0) {
                    problems.add(model.name() + ": ohne Antennen-Elemente, trotzdem " + hidden + " Flächen ausgelassen");
                }
                continue;
            }
            if (video == RenderExampleParts.HD_PRO && mask.antennaElements() != 4) {
                problems.add(model.name() + ": " + mask.antennaElements() + " statt 4 Antennen-Elemente erkannt");
            }
            int expected = 0;
            for (BakedQuad quad : before) {
                if (mask.hides(quad)) {
                    expected++;
                }
            }
            if (hidden != expected || hidden < mask.antennaFaces() - mask.antennaElements()) {
                problems.add(model.name() + ": " + hidden + " Flächen ausgelassen, " + expected + " erkannt, " + mask.antennaFaces()
                        + " Antennen-Flächen im Modell");
            }
            AABB fullBox = full.getModelBoundingBox();
            AABB droneBox = drone.getModelBoundingBox();
            if (!fullBox.inflate(1.0E-4).contains(droneBox.minX, droneBox.minY, droneBox.minZ)
                    || !fullBox.inflate(1.0E-4).contains(droneBox.maxX, droneBox.maxY, droneBox.maxZ)) {
                problems.add(model.name() + ": Modell ohne Antennen verschoben: " + droneBox + " statt in " + fullBox);
            }
            for (BakedQuad quad : after) {
                if (mask.hides(quad)) {
                    problems.add(model.name() + ": Antennen-Fläche bleibt sichtbar");
                    break;
                }
            }
            DroneAssembly assembly = res.assembly(with(DroneBuild.FREESTYLE.frame(), video));
            if (assembly.video().item() != drone) {
                problems.add(model.name() + ": Drohne zeichnet das VTX-Modell mit Antennen");
            }
            DroneRenderState painted = DroneVisuals.previewState(with(DroneBuild.FREESTYLE.frame(), video), PartWear.PRISTINE,
                    DronePaint.DEFAULT.with(PaintSlot.CAMERA, 0x20C0FF));
            if (painted != null && painted.video.useItem && painted.video.item != drone) {
                Set<BakedQuad> paintedQuads = PlugSubmitRecorder.quadsOf(painted.video.item);
                AntennaMask paintMask = res.antennaMask(model.paintVariant());
                for (BakedQuad quad : paintedQuads) {
                    if (paintMask.hides(quad)) {
                        problems.add(model.name() + ": lackiertes VTX-Modell zeigt Antennen");
                        break;
                    }
                }
            }
            ctx.log(String.format(Locale.ROOT, "%s: %d Antennen-Elemente, %d Flächen ausgelassen, %d bleiben; gezeichnet: %s", model.name(),
                    mask.antennaElements(), hidden, after.size(), AntennaRules.antenna(video.definition())));
            stripped++;
        }
        ctx.log(stripped + " Add-on-VTX ohne Antennen im Modell gezeichnet");
        return StepResult.check(problems.isEmpty() && stripped >= 1, String.join("; ", problems));
    }

    private static @Nullable QuadcopterRenderer renderer(DroneRenderState state) {
        EntityRenderer<?, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(state);
        return renderer instanceof QuadcopterRenderer quad ? quad : null;
    }

    static CameraRenderState camera() {
        CameraRenderState camera = new CameraRenderState();
        camera.projectionMatrix.setPerspective((float) Math.toRadians(100.0), 16.0F / 9.0F, 0.05F, 256.0F);
        return camera;
    }

    static int antennaVertices(PlugSubmitRecorder recorder) {
        ColorCounter counter = new ColorCounter();
        PoseStack.Pose pose = new PoseStack().last();
        for (SubmitNodeCollector.CustomGeometryRenderer geometry : recorder.geometry) {
            geometry.render(pose, counter);
        }
        return counter.count;
    }

    static @Nullable String fpvProblem(String label, DroneRenderState state, QuadcopterRenderer renderer) {
        if (state.antenna.solid.isEmpty()) {
            return label + ": keine Antennen-Form";
        }
        PlugSubmitRecorder fpv = new PlugSubmitRecorder();
        renderer.submit(state, new PoseStack(), fpv, camera());
        int seen = antennaVertices(fpv);
        return seen == 0 ? null : label + ": Antenne im eigenen FPV-Bild (" + seen + " Ecken)";
    }

    private static StepResult fpvRenderStep(SelfTest.StepContext ctx) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (DroneBuild preset : DroneBuild.PRESETS) {
            for (VideoType video : videos()) {
                DroneBuild build = with(preset, video);
                String label = label(build);
                DroneRenderState state = new DroneRenderState();
                DroneVisuals.fill(state, build, true, false, new float[]{0.1F, 1.2F, 2.3F, 3.4F}, new float[]{0.6F, 0.6F, 0.6F, 0.6F}, 10.0F);
                DroneVisuals.placeInWorld(state);
                state.entityType = DroneModule.DRONE.get();
                QuadcopterRenderer renderer = renderer(state);
                if (renderer == null) {
                    return StepResult.fail("kein Drohnen-Renderer");
                }
                PlugSubmitRecorder outside = new PlugSubmitRecorder();
                DroneVisuals.submit(state, new PoseStack(), outside);
                if (antennaVertices(outside) == 0) {
                    problems.add(label + ": Antenne fehlt von außen");
                }
                state.hidden = true;
                state.fpvGeometry = FpvProps.geometry(build, state.cameraTiltDegrees);
                state.fpvGuards = FpvGuards.mesh(state.frame, build.frame());
                String problem = fpvProblem(label, state, renderer);
                if (problem != null) {
                    problems.add(problem);
                }
                checked++;
            }
        }
        ctx.log(checked + " Kombinationen: Antenne von außen gezeichnet, im FPV-Bild der eigenen Drohne nicht");
        return StepResult.check(problems.isEmpty(), String.join("; ", problems));
    }

    static final class ColorCounter implements VertexConsumer {
        int count;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this.setColor((a << 24) | (r << 16) | (g << 8) | b);
        }

        @Override
        public VertexConsumer setColor(int color) {
            if (color == AntennaGeometry.SMA || color == AntennaGeometry.MOUNT) {
                this.count++;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    private static StepResult fpvFlightStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        PilotSession session = PilotSession.INSTANCE;
        if (phase == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            state.put(FUTURE, ctx.onServer(server -> start(server, DroneBuild.FREESTYLE)));
            state.put(PHASE, 1);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (phase == 1) {
            if (!future.isDone()) {
                return StepResult.pending();
            }
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("FPV-Start: " + future.exceptionNow());
            }
            QuadcopterEntity drone = session.drone();
            if (!session.isFpv() || drone == null || drone.getId() != DRONE.get()) {
                return ctx.tick() - (int) state.get(SINCE) > 400 ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
            }
            state.put(PHASE, 2);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        if (phase == 2) {
            Screenshots.clearOverlays(mc);
            if (ctx.tick() - (int) state.get(SINCE) < SETTLE_TICKS) {
                return StepResult.pending();
            }
            QuadcopterEntity drone = session.drone();
            if (drone == null || !QuadcopterRenderer.isOwnFpvDrone(drone)) {
                return StepResult.fail("Testdrohne gilt nicht als eigene FPV-Drohne");
            }
            if (!(mc.getEntityRenderDispatcher().getRenderer(drone) instanceof QuadcopterRenderer renderer)) {
                return StepResult.fail("kein Drohnen-Renderer");
            }
            DroneRenderState extracted = renderer.createRenderState();
            renderer.extractRenderState(drone, extracted, 1.0F);
            if (!extracted.hidden) {
                return StepResult.fail("eigene FPV-Drohne wird gezeichnet");
            }
            String problem = fpvProblem(label(drone.build()), extracted, renderer);
            if (problem != null) {
                return StepResult.fail(problem);
            }
            ctx.log("FPV mit " + label(drone.build()) + ": eigene Drohne verborgen, keine Antennen-Form übergeben");
            if (!Screenshots.enabled()) {
                return StepResult.pass();
            }
            Screenshots.clearOverlays(mc);
            state.put("shot", Screenshots.capture("antenna_fpv_freestyle_5"));
            state.put(PHASE, 3);
            return StepResult.pending();
        }
        CompletableFuture<?> shot = (CompletableFuture<?>) state.get("shot");
        if (!shot.isDone()) {
            return StepResult.pending();
        }
        if (shot.isCompletedExceptionally()) {
            return StepResult.fail("Bildschirmfoto: " + shot.exceptionNow());
        }
        ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(Screenshots.fileName("antenna_fpv_freestyle_5") + ".png") + " (" + shot.join() + ")");
        return StepResult.pass();
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static void start(MinecraftServer server, DroneBuild build) {
        ServerPlayer player = player(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discard(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.snapTo(player.getX(), player.getY(), player.getZ() + 3.0, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(build);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        DRONE.set(drone.getId());
        ItemStack head = ContentIds.item(ContentIds.GOGGLES_DIGITAL).map(ItemStack::new).orElseThrow();
        head.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (previousHead == null) {
            previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
            previousHand = player.getMainHandItem().copy();
        }
        player.setItemSlot(EquipmentSlot.HEAD, head);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void discard(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                drone.discard();
            }
        }
    }

    private static void release(MinecraftServer server) {
        ServerPlayer player = player(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        if (previousHead != null) {
            player.setItemSlot(EquipmentSlot.HEAD, previousHead);
        }
        if (previousHand != null) {
            player.setItemSlot(EquipmentSlot.MAINHAND, previousHand);
        }
        previousHead = null;
        previousHand = null;
        discard(server);
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            state.put(FUTURE, ctx.onServer(AntennaSelfTests::release));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + future.exceptionNow());
        }
        return PilotSession.INSTANCE.isActive() ? (ctx.tick() > 250 ? StepResult.fail("FPV-Sitzung endet nicht") : StepResult.pending())
                : StepResult.pass();
    }

    static Map<String, List<FrameType>> addonFramesByRole() {
        Map<String, List<FrameType>> out = new LinkedHashMap<>();
        for (FrameType frame : FrameType.values()) {
            if (!frame.builtin()) {
                out.computeIfAbsent(frame.definition().role().key(), r -> new ArrayList<>()).add(frame);
            }
        }
        return out;
    }

    static List<VideoType> videoPerKind() {
        Map<AntennaRules.Kind, VideoType> chosen = new LinkedHashMap<>();
        for (AntennaRules.Kind kind : AntennaRules.Kind.values()) {
            for (VideoType video : VideoType.values()) {
                if (AntennaRules.kind(video.definition()) != kind) {
                    continue;
                }
                VideoType current = chosen.get(kind);
                if (current == null || (current.builtin() && !video.builtin())) {
                    chosen.put(kind, video);
                }
            }
        }
        return List.copyOf(chosen.values());
    }
}
