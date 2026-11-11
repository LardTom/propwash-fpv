package de.lardtom.propwash.share.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.share.MissingAddons;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareService;
import de.lardtom.propwash.share.ShareVectors;
import de.lardtom.propwash.share.Substitutes;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CovComboShareSelfTests {
    public static final String GROUP = "share: Abdeckung cov-combo – ";
    public static final String MOB_DEFENSE = "mobdefense";
    public static final String FIXTURE = "/cov/cov-combo/mobdefense-codes.json";
    public static final String NAME = "Cov Mob Defense";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FUTURE = "future";
    private static final String CODES = "cov_combo_codes";
    private static final String SAVED_SUBSTITUTES = "cov_combo_substitutes";
    private static final String SAVED_INPUT = "cov_combo_input";

    private CovComboShareSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Codes einer Drohne mit Werfer, Booster und Speer von Mob Defense mit und ohne Add-on-Infos (nur mit dem Jar)", 100,
                CovComboShareSelfTests::writeStep);
        SelfTest.clientStep(GROUP + "Codes mit Mob-Defense-Teilen ohne das Add-on: Name, Version und Link aus dem Code bzw. nur die Mod-Id, "
                + "„Mit Ersatzteilen erzeugen“ ohne die drei Halterungen (nur ohne das Jar)", 2400, importStep());
    }

    public static Map<AccessorySlot, Identifier> mobDefenseParts() {
        Map<AccessorySlot, Identifier> parts = new EnumMap<>(AccessorySlot.class);
        parts.put(AccessorySlot.FRONT, Identifier.fromNamespaceAndPath(MOB_DEFENSE, "dispenser_mount"));
        parts.put(AccessorySlot.TOP, Identifier.fromNamespaceAndPath(MOB_DEFENSE, "rocket_booster"));
        parts.put(AccessorySlot.BOTTOM, Identifier.fromNamespaceAndPath(MOB_DEFENSE, "spear_mount"));
        return parts;
    }

    public static BuildSpec mobDefenseBuild() {
        BuildSpec base = ShareVectors.freestyle();
        return new BuildSpec(base.frame(), base.stack(), base.motor(), base.prop(), base.video(), base.battery(), mobDefenseParts());
    }

    public static BuildSpec withoutAccessories() {
        BuildSpec base = ShareVectors.freestyle();
        return BuildSpec.of(base.frame(), base.stack(), base.motor(), base.prop(), base.video(), base.battery());
    }

    private static StepResult writeStep(SelfTest.StepContext ctx) {
        if (!Services.PLATFORM.isModLoaded(MOB_DEFENSE)) {
            ctx.log("Mob Defense nicht geladen, keine Codes zu schreiben");
            return StepResult.pass();
        }
        BuildSpec build = mobDefenseBuild();
        List<String> problems = new ArrayList<>();
        for (Identifier part : build.allParts()) {
            if (PropwashApi.parts().get(part).isEmpty()) {
                problems.add("Teil " + part + " unbekannt");
            }
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        DroneData data = new DroneData(build, PaintSpec.DEFAULT, TuneSpec.EMPTY, Optional.of(NAME));
        String withInfo = ShareService.encode(data, ShareLayer.DEFAULT_EXPORT, true, Optional.empty());
        String withoutInfo = ShareService.encode(data, ShareLayer.DEFAULT_EXPORT, false, Optional.empty());
        if (withoutInfo.length() >= withInfo.length()) {
            problems.add("ohne Add-on-Infos nicht kürzer: " + withoutInfo.length() + " gegen " + withInfo.length() + " Zeichen");
        }
        for (String code : List.of(withInfo, withoutInfo)) {
            ShareDecoded decoded = ShareService.decode(code);
            if (!decoded.ok() || decoded.content().isEmpty() || !decoded.content().get().build().equals(build)) {
                problems.add("Code liest sich mit Mob Defense nicht als dieselbe Drohne: " + decoded.status());
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("note", "Codes aus dem Selbsttest " + GROUP + "… mit dem Jar von Mob Defense, Propwash " + Services.PLATFORM.getModVersion());
        out.addProperty("mod", MOB_DEFENSE);
        out.addProperty("modName", Services.PLATFORM.getModName(MOB_DEFENSE).orElse(""));
        out.addProperty("modVersion", Services.PLATFORM.modVersion(MOB_DEFENSE).orElse(""));
        out.addProperty("droneName", NAME);
        out.addProperty("withInfo", withInfo);
        out.addProperty("withoutInfo", withoutInfo);
        JsonObject parts = new JsonObject();
        mobDefenseParts().forEach((slot, id) -> parts.addProperty(slot.key(), id.toString()));
        out.add("accessories", parts);
        out.addProperty("frame", build.frame().toString());
        try {
            Path dir = Services.PLATFORM.getGameDir().resolve("cov-combo");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("mobdefense-codes.json"), GSON.toJson(out) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            problems.add("Datei nicht geschrieben: " + e);
        }
        ctx.log("Code mit Add-on-Infos " + withInfo.length() + " Zeichen, ohne " + withoutInfo.length() + " Zeichen: " + withInfo + " / " + withoutInfo);
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    public static JsonObject fixture() throws IOException {
        try (InputStream in = CovComboShareSelfTests.class.getResourceAsStream(FIXTURE)) {
            if (in == null) {
                throw new IOException("Fixture fehlt: " + FIXTURE);
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Optional<MissingAddons.Line> line(ShareImportState state) {
        return state.missing().stream().filter(l -> l.key().equals(MOB_DEFENSE) || l.namespace().equals(Optional.of(MOB_DEFENSE))).findFirst();
    }

    private static void checkLine(ShareConfigTab tab, JsonObject fixture, boolean info, List<String> errors, List<String> seen) {
        ShareImportState state = tab.state();
        String label = info ? "mit Add-on-Infos" : "ohne Add-on-Infos";
        if (!state.decoded().ok()) {
            errors.add(label + ": Code nicht lesbar " + state.decoded().status());
            return;
        }
        Optional<MissingAddons.Line> line = line(state);
        if (line.isEmpty()) {
            errors.add(label + ": keine Zeile für " + MOB_DEFENSE + " in " + state.missing());
            return;
        }
        MissingAddons.Line l = line.get();
        if (l.kind() != MissingAddons.Kind.MISSING) {
            errors.add(label + ": Art " + l.kind() + " statt MISSING");
        }
        String modName = fixture.get("modName").getAsString();
        if (info) {
            if (!l.name().equals(modName)) {
                errors.add(label + ": Name „" + l.name() + "“ statt „" + modName + "“");
            }
            if (l.links().isEmpty()) {
                errors.add(label + ": kein Link aus dem Code");
            }
        } else {
            if (!l.name().equals(MOB_DEFENSE)) {
                errors.add(label + ": Name „" + l.name() + "“ statt nur der Mod-Id " + MOB_DEFENSE);
            }
            if (!l.links().isEmpty()) {
                errors.add(label + ": Links " + l.links() + " ohne Add-on-Infos");
            }
        }
        if (l.requiredVersion().isEmpty()) {
            errors.add(label + ": keine Version aus dem Code");
        }
        if (l.parts().size() != 3) {
            errors.add(label + ": " + l.parts().size() + " fehlende Teile statt 3");
        }
        Substitutes.Result result = state.substitutes();
        List<String> hints = new ArrayList<>();
        for (ShareLines.Line shown : tab.detailLines()) {
            if (shown.tag().equals(ShareLines.TAG_SWAP) || shown.tag().equals(ShareLines.TAG_NO_SUBSTITUTE)) {
                hints.add(shown.text().getString());
            }
        }
        if (state.substitutesFound()) {
            for (AccessorySlot slot : mobDefenseParts().keySet()) {
                String position = "accessory:" + slot.key();
                boolean dropped = result.swaps().stream().anyMatch(swap -> swap.position().equals(position) && swap.from().toString().contains(MOB_DEFENSE)
                        && swap.to().isEmpty());
                if (!dropped) {
                    errors.add(label + ": Halterung " + slot.key() + " nicht leer gelassen: " + result.swaps());
                }
            }
            String removed = Component.translatable("propwash.share.substitutes.removed").getString();
            if (hints.stream().filter(text -> text.contains(removed)).count() != 3) {
                errors.add(label + ": Hinweise zu den Halterungen " + hints);
            }
        } else {
            String none = Component.translatable("propwash.share.substitutes.none", "").getString().trim();
            if (hints.stream().noneMatch(text -> text.startsWith(none))) {
                errors.add(label + ": weder Ersatzteil noch „" + none + " …“: " + hints);
            }
        }
        seen.add(label + ": „" + l.name() + "“ " + l.requiredVersion().orElse("?") + " " + l.links().stream().map(MissingAddons.Link::url).toList()
                + ", Ersatz " + (state.substitutesFound() ? result.method() : "keiner") + ", Hinweise " + hints);
    }

    private static SelfTest.ClientStep importStep() {
        return CovShareScript.create()
                .then("Fixture lesen", 40, (ctx, s, age) -> {
                    if (Services.PLATFORM.isModLoaded(MOB_DEFENSE)) {
                        ctx.log("Mob Defense geladen, der Fall braucht einen Lauf ohne das Jar");
                        s.put(CODES, null);
                        return StepResult.pass();
                    }
                    try {
                        s.put(CODES, fixture());
                    } catch (IOException e) {
                        return StepResult.fail(e.toString());
                    }
                    s.put(SAVED_SUBSTITUTES, ShareSettings.substitutes());
                    s.put(SAVED_INPUT, ShareSettings.lastInput());
                    ShareSettings.substitutes(true);
                    return StepResult.pass();
                })
                .server("Kreativ, Inventar leer", (server, out) -> {
                    ServerPlayer player = ShareSelfTests.player(server);
                    player.closeContainer();
                    player.setGameMode(GameType.CREATIVE);
                    player.getInventory().clearContent();
                    player.containerMenu.broadcastChanges();
                })
                .then("Tab öffnen", 300, (ctx, s, age) -> {
                    if (s.get(CODES) == null) {
                        return StepResult.pass();
                    }
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || !mc.player.hasInfiniteMaterials()) {
                        return CovShareScript.wait(s, "Spieler nicht im Kreativmodus");
                    }
                    return PropwashClientApi.openConfigTab(ShareConfigTab.ID, null) ? StepResult.pass()
                            : StepResult.fail("Tab " + ShareConfigTab.ID + " lässt sich nicht öffnen");
                })
                .then("beide Codes einfügen", 300, (ctx, s, age) -> {
                    if (s.get(CODES) == null) {
                        return StepResult.pass();
                    }
                    JsonObject fixture = (JsonObject) s.get(CODES);
                    ShareConfigTab tab = ShareConfigTab.active();
                    if (tab == null || tab.frames() < 3) {
                        return CovShareScript.wait(s, "Tab erscheint nicht: " + Minecraft.getInstance().gui.screen());
                    }
                    List<String> errors = new ArrayList<>();
                    List<String> seen = new ArrayList<>();
                    tab.setInput(fixture.get("withInfo").getAsString());
                    checkLine(tab, fixture, true, errors, seen);
                    tab.setInput(fixture.get("withoutInfo").getAsString());
                    checkLine(tab, fixture, false, errors, seen);
                    if (!errors.isEmpty()) {
                        return StepResult.fail(String.join("; ", errors));
                    }
                    if (!tab.state().substitutesFound()) {
                        ctx.log(String.join("; ", seen));
                        return StepResult.fail("kein Ersatz-Bauplan, „Mit Ersatzteilen erzeugen“ nicht möglich");
                    }
                    CompletableFuture<ImportResult> future = tab.createSubstituted();
                    if (future == null) {
                        return StepResult.fail("„Mit Ersatzteilen erzeugen“ schickt nichts");
                    }
                    s.put(FUTURE, future);
                    ctx.log(String.join("; ", seen));
                    return StepResult.pass();
                })
                .then("Drohne ohne die drei Halterungen im Inventar", 400, (ctx, s, age) -> {
                    if (s.get(CODES) == null) {
                        return StepResult.pass();
                    }
                    ImportResult result = ShareSelfTests.result(s);
                    if (result == null) {
                        return CovShareScript.wait(s, "keine Antwort auf „Mit Ersatzteilen erzeugen“");
                    }
                    if (result.status() != ImportStatus.APPLIED) {
                        return StepResult.fail("„Mit Ersatzteilen erzeugen“: " + result);
                    }
                    Optional<DroneData> created = ShareSelfTests.inventoryDrone(Minecraft.getInstance(), data -> data.name().equals(Optional.of(NAME)));
                    if (created.isEmpty()) {
                        return CovShareScript.wait(s, "keine Drohne „" + NAME + "“ im Inventar");
                    }
                    BuildSpec build = created.get().build();
                    if (!build.accessories().isEmpty() || !build.equals(withoutAccessories())) {
                        return StepResult.fail("erzeugte Drohne " + build + " statt " + withoutAccessories());
                    }
                    ctx.log("Drohne „" + NAME + "“ im Inventar: " + build.frame() + ", Halterungen leer");
                    ShareSelfTests.closeConfigurator(Minecraft.getInstance());
                    return StepResult.pass();
                })
                .cleanup(ctx -> {
                    Object substitutes = ctx.state().get(SAVED_SUBSTITUTES);
                    if (substitutes instanceof Boolean value) {
                        ShareSettings.substitutes(value);
                    }
                    Object input = ctx.state().get(SAVED_INPUT);
                    if (input instanceof String value) {
                        ShareSettings.lastInput(value);
                    }
                    ShareSelfTests.closeConfigurator(Minecraft.getInstance());
                })::tick;
    }
}
