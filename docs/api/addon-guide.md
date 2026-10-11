# Addons für Propwash FPV — Schritt für Schritt (Parts-API 1.5)

Dieses Handbuch zeigt, wie ein eigenes Mod Propwash FPV erweitert, **ohne Propwash zu ändern**: neue Bauteile aller
Kategorien (auch neue Rahmenformen wie Deadcat, Stretched-X, H-Frame, Ducts oder X-Class 10–13″), neue Zellchemien,
Presets, ein eigener Kreativ-Tab, Werkbank-Knöpfe mit eigenen Fenstern, Konfigurator-Tabs, Flugschul-Szenen und ein
Sharecode-System (Baupläne, Lackierung, Tuning und OSD-Layout teilen). Das erste Addon dieser Art ist „Propwash: Just
More Parts“; alle Beispiele benutzen deshalb den Namensraum `justmoreparts`.

Weiterführend:
- öffentliche Fassung für Addon-Autoren (englisch und deutsch, je Release aus dem Code erzeugt): Handbuch
  <https://propwashfpv.com/docs/api/>, API-Referenz <https://propwashfpv.com/docs/api/reference/>, JSON-Format
  <https://propwashfpv.com/docs/parts-format/>; Erzeugung und Prüfung im Website-Repository (`tools/build-docs.mjs`,
  `tools/test-docs.mjs`), Ablauf in [`docs/RELEASING.md`](../RELEASING.md) §4
- verbindlicher Vertrag mit allen Feldern, Einheiten und Regeln: [`docs/design/parts-api.md`](../design/parts-api.md)
- vollständige JSON-Schema-Referenz aller Bauteile: [`docs/design/parts-api-data.md`](../design/parts-api-data.md)
- Modelle, Texturen, Layout und Lack für Teile aus anderen Namensräumen: [`docs/ASSETS.md`](../ASSETS.md), Abschnitt
  „Teile aus anderen Namensräumen“
- lauffähiges Beispiel-Addon im Quell-Set `devtest`: Abschnitt 18
- was sich je Propwash-Release für Addons ändert (API, Datenformate, Netzprotokoll, Umstellung):
  [`changes.md`](changes.md)

Minecraft-, Fabric- und NeoForge-APIs in den Beispielen stammen aus den 26.3-Quellen; Propwash selbst benutzt nur
Mojang-Namen (26.3 ist nicht mehr obfuskiert).

**Lizenz (ab Propwash 0.5.0):** Die API (`de.lardtom.propwash.api`), das Beispiel-Addon und die Doku stehen unter der
MIT-Lizenz (`LICENSE-MIT`). Dein Addon darf sie frei benutzen, daraus kopieren und selbst jede Lizenz haben. Der übrige
Propwash-Code steht unter der PolyForm Shield License 1.0.0 (`LICENSE`): Wer daraus etwas übernimmt, gibt die Lizenz
samt der Zeile `Required Notice: …` mit und darf damit nicht mit Propwash konkurrieren.

Abschnitte mit **„Neu in 1.4“** beschreiben die Ergänzungen der Parts-API 1.4; eine Übersicht steht in
[`parts-api.md`](../design/parts-api.md) Abschnitt 3.4.

---

## 0. Was die API kann — und was nicht

| Geht (API 1.x) | Geht (noch) nicht |
|---|---|
| Rahmen, Stacks/ESCs, Motoren, Props, Akkus, Video-Einheiten und Zubehör per Java oder JSON anmelden | andere Motorzahlen als 4 (Hexa, Okto) |
| eigene Arm-Geometrie, Kameraposition, Schwerpunkt, Ducts, Luftwiderstand, Akku-Regeln je Rahmen | neue Videosysteme oder Brillen (nur `analog`/`digital`) |
| neue Motor- und Stack-Halterungen, Prop-Aufnahmen, Zellchemien bis 12S | Teile aus Welt-Datenpaketen oder per `/reload` |
| Items, Tooltips, Werkbank, Analyse, Flugmodell, Rendering, Flugschule, Speichern „geschenkt“ | neue OSD-Elemente, neue Lack-Plätze, neue Zubehör-Halterungen |
| Presets und ein eigener Kreativ-Tab „Propwash: <Name>“ direkt hinter dem Propwash-Tab | eigene Item-Klassen für Teile (Propwash stellt die Teil-Items) |
| Baupläne lesen, Drohnen-Items erzeugen, Tuning und Lack setzen | Einträge in Propwashs eigene Beutetabellen (eigene Tabellen hängt `PropwashApi.loot()` in jede Vanilla-Truhe) |
| server-geprüfter Import in die Werkbank oder auf die gehaltene Drohne | VTX-Leistung wirkt nur informativ; das Kamera-Sichtfeld `camera_fov_deg` bestimmt seit API 1.4 das FPV-Bild |
| Werkbank-Knöpfe, Kind-Fenster, Konfigurator-Tabs, Flugschul-Szenen, Events | |

Garantie: Alle eingebauten Teile, Items, Speicherstände, Rezepte, Beutetabellen, Presets und das Flugverhalten bleiben
genau wie in 0.3.2. Ein Addon kann eingebaute Teile weder ändern noch entfernen.

---

## 1. Projekt aufsetzen

### 1.1 Gradle
Propwash veröffentlicht drei Maven-Artefakte (Gruppe `de.lardtom`, je mit Quellen- und Javadoc-Jar):

| Artefakt | Inhalt | Verwendung im Addon |
|---|---|---|
| `de.lardtom:propwash-common-26.3:0.5.0` | gemeinsamer Code (ganze API, Mojang-Namen) | `compileOnly` im Modul `common` |
| `de.lardtom:propwash-fabric-26.3:0.5.0` | Fabric-Mod-Jar | `implementation` im Modul `fabric` (Dev-Laufzeit) |
| `de.lardtom:propwash-neoforge-26.3:0.5.0` | NeoForge-Mod-Jar | `implementation` im Modul `neoforge` (Dev-Laufzeit) |

**Lokal entwickeln (gegen einen eigenen Propwash-Stand):** im Propwash-Repository einmal
`JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew publishToMavenLocal` ausführen. Das legt alle drei Artefakte in
`~/.m2/repository/de/lardtom/` ab. Im Addon:

```groovy
repositories {
    mavenLocal()
}

// common/build.gradle
dependencies {
    compileOnly 'de.lardtom:propwash-common-26.3:0.5.0'
}

// fabric/build.gradle (Loom, 26.3 ist nicht obfuskiert: kein modImplementation nötig)
dependencies {
    implementation 'de.lardtom:propwash-fabric-26.3:0.5.0'
}

// neoforge/build.gradle (ModDevGradle)
dependencies {
    implementation 'de.lardtom:propwash-neoforge-26.3:0.5.0'
}
```

Die Varianten tragen das Attribut `io.github.mcgradleconventions.loader` (`common`, `fabric`, `neoforge`) wie im
MultiLoader-Template; ein Addon aus demselben Template bekommt damit automatisch die passende Variante.

**Nach der Veröffentlichung** geht es auch über das Modrinth-Maven (eigene Datei je Loader, die API liegt vollständig im
gemeinsamen Code, deshalb reicht im Modul `common` eines der beiden Jars als `compileOnly`):

```groovy
repositories {
    exclusiveContent {
        forRepository { maven { url = 'https://api.modrinth.com/maven' } }
        filter { includeGroup 'maven.modrinth' }
    }
}

dependencies {
    compileOnly 'maven.modrinth:propwash-fpv:0.5.0+neoforge'       // common
    implementation 'maven.modrinth:propwash-fpv:0.5.0+fabric'      // fabric
    implementation 'maven.modrinth:propwash-fpv:0.5.0+neoforge'    // neoforge
}
```

Slug und Versionsnummern stehen in [`docs/modrinth/listing.md`](../modrinth/listing.md).

### 1.2 Abhängigkeit in den Loader-Metadaten
Propwash muss auf **Client und Server** laufen, das Addon ebenso (die Teile werden beim Verbinden verglichen).

`fabric.mod.json`:
```json
"depends": {
  "minecraft": "~26.3",
  "propwash": ">=0.4.0"
}
```

`neoforge.mods.toml`:
```toml
[[dependencies.justmoreparts]]
modId = "propwash"
type = "required"
versionRange = "[0.4.0,)"
ordering = "AFTER"
side = "BOTH"
```

### 1.3 Name, Version und Homepage für die Beitritts-Übersicht
Fehlt einem Spieler beim Verbinden ein Addon (oder hat er eine andere Version), zeigt Propwash eine Übersicht **je
Addon** mit Name, Mod-Id, Version auf dem Server und beim Spieler, was zu tun ist („Installiere *Dein Addon* 1.2.3“)
und einem Link zur Seite des Addons. Diese Angaben stammen aus den Loader-Metadaten des Addons, die der Server beim
Verbinden mitschickt (nur Propwash-Addons, nie die ganze Mod-Liste):

| Angabe | `fabric.mod.json` | `neoforge.mods.toml` (`[[mods]]`) |
|---|---|---|
| Name in der Übersicht | `name` | `displayName` |
| Version | `version` | `version` |
| Link (nur `https://` oder `http://`) | `contact.homepage`, sonst `contact.sources` | `displayURL`, sonst `modUrl` |
| erkannt als Propwash-Addon | `propwash` unter `depends`/`recommends`/`suggests` | `[[dependencies.<modid>]]` mit `modId = "propwash"` (`required` oder `optional`) |

```json
"name": "Propwash: Mein Addon",
"version": "1.2.3",
"contact": { "homepage": "https://modrinth.com/mod/mein-addon" }
```

```toml
[[mods]]
modId = "meinaddon"
version = "1.2.3"
displayName = "Propwash: Mein Addon"
displayURL = "https://modrinth.com/mod/mein-addon"
```

- **Namensraum = Mod-Id** empfohlen: Propwash ordnet Teile über ihren Namensraum dem Addon zu. JSON-Teile, die mit
  `loadBundled("<modid>")` aus den eigenen Ressourcen kommen, ordnet es auch bei anderem Namensraum richtig zu; per Java
  angemeldete Teile in einem fremden Namensraum landen sonst in der Gruppe „Unbekannte Teile (<namensraum>)“.
- Steuerzeichen, `§`-Formatierung und Links mit anderem Schema (`file:`, `javascript:` …) verwirft Propwash; Name
  höchstens 96, Version 64 Zeichen.
- Blockiert wird nur, was Propwash wirklich braucht (Teile, Netzprotokoll). Ein Addon ohne Teile, das nur auf einer
  Seite fehlt, steht unter „Außerdem anders“; trennt danach der Loader (z. B. wegen eigener Items), erscheint die
  Übersicht trotzdem, mit der Meldung des Loaders darunter.
- Hat nur der **Spieler** das Addon, darf er seit 0.5.0 trotzdem beitreten: Das Addon ist bei ihm für die Sitzung
  inaktiv (Abschnitt 17.1). Fehlt es dem Spieler, bleibt es Pflicht.

### 1.4 API-Version prüfen
Die API hat eine eigene Version (`PropwashApi.API_VERSION`, SemVer), unabhängig von der Mod-Version: Propwash 0.5.0
bringt **1.5.0** (Propwash 0.4.0 brachte 1.4.0). Die Nebenversionen zählen die Ergänzungen: 1.0 Grundumfang, 1.1 Kamera-Aufbau `cameraView`, 1.2 eigene
Kreativ-Tabs, Flugzeit-Kennzahlen in `BuildStats`, `power_w`, `usable_fraction`, 1.3 Flugschul-Verknüpfungen für Items,
1.4 Truhen-Beute ohne Loader-Code (`PropwashApi.loot()`, Abschnitt 8), Seltenheit per JSON, weitere
Analyse-Kennzahlen, ESC-Event, Teilezustand, Farbstoff-Kosten, Akkus mit Teilladung, Name beim Import, Drohne erzeugen,
JSON-Codec, Sprachtexte ohne Client, die Werkbank- und Client-Ergänzungen sowie die Kamera: Sichtfeld aus
`camera_fov_deg`, Uptilt pro Drohne mit Bereich des Kamerahalters (alle fünf sind in Propwash 0.4.0 enthalten; Details
an den Stellen mit „Neu in 1.4“), 1.5 (Propwash 0.5.0) die Szenen-Konstanten `FlightSchoolScenes.TRICKS` und `FlightSchoolScenes.GRIPPER` sowie
`BuildAccess#partCompatibility()` für die Welt-Regel „Teile müssen zusammenpassen“ und
`PropwashClientApi.isAddonActive(String)` für Addons, die nur beim Spieler installiert sind (Abschnitt 17.1). Beim Start
prüft das Addon die Hauptversion und die mindestens nötige Nebenversion:

```java
PropwashApi.requireVersion("justmoreparts", "1.1");   // wirft IllegalStateException mit klarer Meldung
if (PropwashApi.isCompatible("1.2")) { … }            // optionale Funktionen erst ab 1.2 benutzen
```
Wer `extensionTab(…)` (Abschnitt 9) aufruft, verlangt **1.1**. Addons für 1.0 laufen weiter und bekommen ihren Tab
automatisch (Name aus `itemGroup.<ns>` oder dem Mod-Namen, Standard-Reihenfolge, erstes Item als Symbol).

---

## 2. Einstiegspunkte und Lebenszyklus

| Phase | Wann | Erlaubt |
|---|---|---|
| **Offen** | Fabric `ModInitializer#onInitialize`, NeoForge `@Mod`-Konstruktor (beide Seiten) | `parts().register/registerChemistry/loadBundled`, `partItems().registerItems/registerItem`, `builds().registerPreset`, `extensionTab(ns)` anlegen, `creativeTab().add`, `PropwashEvents.*.register` |
| **Client-Init** | Fabric `ClientModInitializer`, NeoForge `@Mod(dist = Dist.CLIENT)` | `PropwashClientApi.register…` (Werkbank, Konfigurator-Tabs, Flugschul-Szenen) |
| **Eingefroren** | nach allen Initialisierungen (Event `REGISTRY_FROZEN`) | nur lesen; `register` und ein neuer `extensionTab` werfen `IllegalStateException` |

- Die Reihenfolge der Mods ist egal: Alle Aufrufe der offenen Phase funktionieren vor, während und nach Propwashs
  eigener Initialisierung (Fabric ordnet Entrypoints nicht, NeoForge baut Mods parallel auf).
- Teile **immer in der gemeinsamen Init** anmelden, nie nur auf dem Client — sonst trennt der Server die Verbindung.
- Unter NeoForge laufen die Konstruktoren auf einem Worker-Thread: dort nichts anfassen, was eine laufende Welt oder
  einen Client braucht.

```java
public final class JustMoreParts {
    public static final String MOD_ID = "justmoreparts";

    public static void init() {                                      // aus onInitialize bzw. dem @Mod-Konstruktor
        PropwashApi.requireVersion(MOD_ID, "1.1");
        JmpParts.register();                                         // Java-Teile (Abschnitt 3)
        PropwashApi.parts().loadBundled(MOD_ID);                     // JSON-Teile (Abschnitt 4)
        PropwashApi.partItems().registerItems(MOD_ID);               // Items für alle Teile des Namensraums (Abschnitt 5)
        PropwashApi.builds().registerPreset(DronePreset.of(id("lr8s"), JmpPresets.longRange8s()));
        PropwashApi.extensionTab(MOD_ID).icon(id("deadcat_5"))                // eigener Kreativ-Tab (Abschnitt 9)
                .order(TabSection.PRESETS, TabSection.FRAMES, TabSection.MOTORS, TabSection.PROPS);
        PropwashEvents.BUILD_IMPORTED.register((player, request, result) -> JmpStats.count(result));
    }

    public static void initClient() {                                // aus dem Client-Einstieg
        PropwashClientApi.registerWorkbenchExtension(new ShareCodeButton());
        PropwashClientApi.registerConfigTab(ShareCodeTab::new);
        PropwashClientApi.registerFlightSchoolScene(FrameShapesScene::create);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
```

---

## 3. Bauteile per Java

Jede Kategorie hat einen Record im Paket `api.part` mit einem Builder. **Addons bauen die Definitionen
(`…Definition`, `AirframeGeometry`, `FrameAero`) nur über Builder** (oder die `with…`-Methoden) — deren Konstruktoren sind
nicht Teil der Stabilitätszusage und bekommen in Nebenversionen neue Felder. Kleine **Wertrecords** wie `Vec3Mm`,
`BatteryFitRule`, `PackSize`, `CellChemistry`, `MotorThermal`, `DuctSpec`, `MassSplit`, `AnchorSpec`, `PartVisual`,
`TuneSpec`, `PaintSpec`, `DroneData`, `ImportRequest` oder `OsdLayoutSpec` dürfen dagegen direkt mit `new` gebaut werden:
Ihr kanonischer Konstruktor ist seit 1.4 stabil (neue Komponenten kommen nur mit einem zusätzlichen Konstruktor, der die
alte Signatur behält; vollständige Liste in Abschnitt 16). Jeder Record prüft seine Werte und wirft
`IllegalArgumentException` mit einer deutschen Meldung samt Id.

Gemeinsame Grunddaten (`PartBasics`): Masse in Gramm (Pflicht), Stapelgröße, Sortierung (`sortOrder`, Standard 1000),
`creativeListed`, Haltbarkeit (Teiler der Absturz-Abnutzung), Recycling-Ertrag, Akzentfarbe und ein anderes
Drohnen-Modell (`PartVisual`).

**Koordinaten** (Rahmen, Kamera, Arme, Anker): Millimeter, Ursprung Rahmenmitte auf Höhe der Stack-Auflage, +X rechts,
+Y oben, **−Z vorn** (Nase). Arme immer in der Reihenfolge vorne links, vorne rechts, hinten links, hinten rechts.

### 3.1 Motor
```java
PropwashApi.parts().register(MotorDefinition.builder(id("m2806_1300"))
        .mass(52.0).stator("2806.5").kv(1300).mount(Mounts.MOTOR_M28)
        .maxCurrent(48.0).resistance(0.062).cells(6, 8)
        .thermal(MotorThermal.limits(135.0, 155.0))                   // Drosselung ab 135 °C, Maximum 155 °C
        .build());
```
Statormaße kommen aus der Bezeichnung (erste zwei Ziffern Durchmesser, Rest Höhe) oder aus `statorSize(d, h)`. Eigene
Halterungen sind einfach neue Ids (`id("m42")`); passende Rahmen nennen dieselbe Id in `motorMount(…)`.

**Neu in 1.4:** Alle Hitzewerte ohne den Record-Konstruktor über den Builder; nicht gesetzte Werte bleiben auf dem
Standard (`MotorThermal.DEFAULT`):
```java
.thermal(MotorThermal.builder()
        .heatCapacity(64.0)          // J/K, Standard 0,5 J/(g·K) × Masse
        .cooling(0.21)               // W/K im Stand
        .coolingPerRadS(4.0E-4)      // W/K je rad/s Fahrtwind
        .limits(140.0, 160.0)        // Drosselung ab, Maximum (°C)
        .wearAtMax(0.001)            // Verschleiß je Sekunde bei Maximum
        .build())
```

### 3.2 Propeller
```java
PropwashApi.parts().register(PropDefinition.builder(id("p13_7_2b_carbon"))
        .mass(28.0).size(330, 7.0, 2).coefficients(0.095, 0.042)
        .material(PropMaterial.CARBON)                                  // Steifigkeit 2,2 und Haltbarkeit 1,4 als Standard
        .build());
```
Wer `basics(…)` setzt (z. B. für eine Akzentfarbe), setzt die Haltbarkeit danach mit `durability(…)` selbst, sonst gilt
1,0 statt des Material-Standards.

**Neu in 1.4:** Weicht `stiffness(…)` vom Standard des Materials ab, zeigt der Daten-Abschnitt des Tooltips die Zeile
„Steifigkeit: ×1,8“ (`propwash.parts.tooltip.stiffness`). Wie stark das Propwash-Rauschen dadurch sinkt, meldet
`analyze(spec).propwashNoise()` (Abschnitt 10).

**Neu in 1.5 – 3D-Props:** Im 3D-Modus drehen die Motoren rückwärts. Normale, gewölbte Props liefern dann nur etwa
55 % Schub; ein symmetrischer 3D-Prop gibt das mit `reverseThrust(…)` an (JSON `"reverse_thrust": 1.0`, erlaubt 0,2–1,0):
```java
PropwashApi.parts().register(PropDefinition.builder(id("p5_30_3b_3d"))
        .mass(4.4).size(127, 3.0, 3).coefficients(0.10, 0.055)          // flach und symmetrisch: weniger Schub als ein 5x4.3
        .reverseThrust(1.0)                                             // rückwärts gleich viel Schub wie vorwärts
        .build());
```
Der Tooltip zeigt dann „Umkehrschub (3D-Modus): 100 %“. Abfrage mit `PropwashApi.isCompatible("1.5")`; ältere Propwash
ignorieren das JSON-Feld mit einer Warnung.

### 3.3 Rahmen mit eigener Form (Deadcat)
```java
PropwashApi.parts().register(FrameDefinition.builder(id("deadcat_5"))
        .mass(118.0).role(FrameRole.FREESTYLE).shape(FrameShape.DEADCAT)
        .wheelbase(230).maxProp(130)
        .motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30, Mounts.STACK_S20)
        .accessoryMount(AccessorySlot.TOP, AccessorySlot.BOTTOM)
        .arms(List.of(MotorArm.of(-95, 0, -60, MotorArm.Spin.CW), MotorArm.of(95, 0, -60, MotorArm.Spin.CCW),
                MotorArm.of(-80, 0, 80, MotorArm.Spin.CCW), MotorArm.of(80, 0, 80, MotorArm.Spin.CW)))
        .battery(true, 30.0).electronics(12.0)
        .camera(new Vec3Mm(0.0, 14.0, -100.0)).cameraTilt(30.0)
        .centerOfMass(new Vec3Mm(0.0, 0.0, 6.0))
        .aero(0.012, 0.9).impactTolerance(8.0)
        .batteryFit(new BatteryFitRule(4, 6, 260.0, 6, 0))
        .build());
```
Ohne `arms` rechnet Propwash wie bisher einen True-X aus dem Radstand. Mit `arms` entstehen Mixer, Hebelarme,
Trägheit, Hülle und Hitbox aus der Geometrie. Eine Deadcat hält die Props nur dann aus dem FPV-Bild, wenn das Objektiv
(`camera`) weit genug vor den Scheiben der Vorderprops sitzt – im Beispiel 40 mm vor der Motorlinie; mit −58 mm sähe die
analoge Race-Kamera (125°) 4,4 % Props (geprüft von JUnit `GuideClaimsTest` mit genau dieser Geometrie: 0,0 % analog
und digital). Nachprüfen mit `PropwashApi.builds().analyze(spec)` (`propsInViewPercent`) oder `cameraView(spec)`
(Abschnitt 10): beide rechnen mit dem Aufbau, den das Spiel zeichnet (Nabenhöhe aus `motor_seat`, Standard-Uptilt des
Rahmens, Sichtfeld der Kamera im Videomodul, ohne `camera_fov_deg` analog 120°/digital 130°). Jeder Rahmen sollte `camera` mit Position setzen: das Objektiv sitzt in der Kamera, bei
X-Rahmen meist 1–5 mm unter der Propebene (Kamera im Käfig, Props oben auf den Motoren), bei Whoops knapp über den Ducts.
Schon 1 mm Höhe ändert „Props im Bild“ um mehrere Prozentpunkte; die eingebauten Rahmen liegen bei 3–11 %
(`docs/design/realism/props-in-view.md`). Ohne Position setzt Propwash das Objektiv nach der Altformel 15 mm über den
Schwerpunkt – mit Akku oben meist über die Propebene, dann zeigt das FPV-Bild kaum Props. Geduckte Rahmen setzen
`duct(DuctSpec.STANDARD)` (einzelne Werte über die `DuctSpec`-Felder), X-Class-Rahmen `role(FrameRole.X_CLASS)` mit
Props von 254–330 mm.

### 3.4 Stack, Akku, Zellchemie, Video, Zubehör
```java
PropwashApi.parts().register(StackDefinition.builder(id("stack_30_8s"))
        .mass(24.0).mount(Mounts.STACK_S30).escContinuous(65.0).cells(3, 8).build());

PropwashApi.parts().registerChemistry(new CellChemistry(id("lihv_445"), 4.45, 3.85, 3.5, 3.3, 3.9, 2.0,
        List.of(0.0, 0.13, 0.20, 0.29, 0.34, 0.39, 0.43, 0.49, 0.57, 0.69, 0.83, 1.0)));

PropwashApi.parts().register(BatteryDefinition.builder(id("b8s_4500_lion"))
        .mass(560.0).cells(8).capacity(4500).cellResistance(0.014).cRating(12)
        .chemistry(BatteryDefinition.LION).packSize(new PackSize(140.0, 45.0, 70.0)).connector("XT60").build());

PropwashApi.parts().register(VideoDefinition.builder(id("hd_o4"))
        .mass(9.0).link(VideoLink.DIGITAL).latency(24).range(560).imageQuality(1.0)
        .cameraWidth(14.0).cameraFov(155.0).vtxPower(1000).build());
```
Chemien vor den Akkus anmelden, die sie benutzen. Zubehör (`AccessoryDefinition`) kennt die Plätze `TOP`/`BOTTOM`,
Maße, Leistungsaufnahme, LEDs und optional eine Action-Cam (`ActionCamDefinition`, nur oben).

### 3.5 Kompatibilität, die Propwash prüft
Nicht baubar (Probleme): `missing_part`, `motor_mount`, `prop_too_large`, `prop_too_small`, `prop_mount`,
`stack_mount`, `battery_frame`, `accessory_mount`. Warnungen: `motor_overvoltage`, `motor_undervoltage`,
`stack_voltage`, `esc_undersized`, `battery_weak`, `battery_undersized`. Texte: `propwash.build.issue.<schlüssel>`.

---

## 4. Bauteile per JSON

Dieselben Teile lassen sich als Daten in den **eigenen Mod-Ressourcen** beschreiben:

```
src/main/resources/data/justmoreparts/propwash_parts/<kategorie>/<pfad>.json
```

- Kategorie: `frame`, `stack`, `motor`, `prop`, `video`, `battery`, `accessory` oder `chemistry`; Id =
  `justmoreparts:<pfad>`; keine Unterordner.
- Geladen wird **nur** durch `PropwashApi.parts().loadBundled("justmoreparts")` — in dieser Reihenfolge: `chemistry`,
  `frame`, `stack`, `motor`, `prop`, `video`, `battery`, `accessory`, je Ordner alphabetisch. Rückgabe: Anzahl der
  angemeldeten Definitionen. Nicht aus Welt-Datenpaketen, kein `/reload`.
- Jede Datei beginnt mit `"format": 1`. Unbekannte Felder erzeugen eine Warnung im Log und werden ignoriert; fehlende
  Pflichtfelder oder ungültige Werte brechen den Start mit einer Meldung ab, die **alle** fehlerhaften Dateien samt Feld
  nennt.

```json
{"format": 1, "mass_g": 128.0, "stator": "4214", "kv": 400, "mount": "justmoreparts:m42",
 "max_current_a": 60.0, "resistance_ohm": 0.045, "cells": [6, 12],
 "thermal": {"derate_start_c": 140.0, "max_temp_c": 160.0}}
```

```json
{"format": 1, "mass_g": 1450.0, "role": "x_class", "wheelbase_mm": 820, "props_mm": [250, 330],
 "motor_mounts": ["justmoreparts:m42"], "stack_mounts": ["propwash:s30"],
 "aero": {"frontal_area_m2": 0.08, "drag_coefficient": 1.0}, "impact_tolerance": 6.0,
 "battery_fit": {"cells": [6, 8], "max_mass_g": 2500.0, "preferred_min_cells": 8, "preferred_min_capacity_mah": 4000}}
```

Gemeinsame Felder aller Teile: `mass_g`, `stack_size`, `sort`, `creative`, `durability`, `recycle`
(`{"item": "propwash:copper_coil", "count": 1}` oder `"none"`), `visual: {"accent": "#e8b622", "render_model": "…"}` und
**neu in 1.4** `rarity` (`"common"`, `"uncommon"`, `"rare"`, `"epic"`; Farbe des Item-Namens, ohne Java-Code).
Alle weiteren Felder stehen in der Spalte „JSON“ der Tabellen in [`parts-api.md`](../design/parts-api.md) Abschnitt 5
und vollständig in [`parts-api-data.md`](../design/parts-api-data.md).

**Neu in 1.4 — derselbe Codec für Werkzeuge:** Prüfstände, Daten-Generatoren oder ein Web-Export brauchen keinen eigenen
Leser mehr:
```java
JsonObject json = PropwashApi.parts().toJson(definition);                       // wie in den Teile-Dateien
PartDefinition back = PropwashApi.parts().fromJson(PartKind.MOTOR, id, json);    // IllegalArgumentException nennt das Feld
CellChemistry chem = PropwashApi.parts().chemistryFromJson(chemistryId, chemistryJson);
```
`fromJson` meldet nichts an und verändert das übergebene Objekt nicht; unbekannte Felder werden wie beim Laden
ignoriert.

---

## 5. Items

```java
int count = PropwashApi.partItems().registerItems("justmoreparts");     // ein Item je Teil des Namensraums
PropwashApi.partItems().registerItem(id("xclass_13"), p -> p.rarity(Rarity.RARE));   // einzeln, mit eigenen Eigenschaften
```
- Das Item hat **dieselbe Id wie das Teil**. Propwash liefert die Item-Klasse (Akkus mit Ladestand), die Komponente
  `propwash:part`, Stapelgröße, Shift-Tooltips, Verschleißbalken, Recycling, Werkbank-Slots und die Flugschul-Zuordnung.
- Einzeln angemeldete Teile vor `registerItems` anmelden, sonst bekommen sie schon das Standard-Item.
- Die Anmeldung übernimmt Propwash auf beiden Loadern (Warteschlange, geflusht mit Propwashs eigenen Items).
- Nachschlagen: `item(id)`, `stack(id)` (leer bei unbekannten Teilen), `partOf(stack)` (Teil-Id eines beliebigen
  Teil-Items, auch eingebauter).
- Teile ohne Item sind erlaubt (z. B. nur für Presets), erscheinen aber nicht in der Werkbank.
- **Neu in 1.4 — Seltenheit ohne Java:** JSON `"rarity": "rare"` bzw. `PartBasics#withRarity(Rarity.RARE)` setzt die
  Seltenheit des Items, das `registerItems` anlegt; ein eigener `registerItem(id, p -> …)` läuft danach und gewinnt.
- **Neu in 1.4 — Akku mit Teilladung:** `stack(id, 0.35F)` liefert einen Akku mit 35 % Ladung (gerundet wie im
  Ladegerät); `IllegalArgumentException` für Teile, die kein Akku sind, und für Werte außerhalb 0…1. Die Komponente
  `propwash:battery_charge` (`float` 0…1, fehlt = voll) ist stabil — Beutetabellen eines Addons dürfen sie mit
  `minecraft:set_components` setzen.

---

## 6. Assets

| Datei | Zweck | Pflicht |
|---|---|---|
| `assets/justmoreparts/items/<pfad>.json` | Inventar-Symbol (Item-Model-Definition) | ja |
| `assets/justmoreparts/items/drone_render/<kategorie>/<pfad>.json` (+ Varianten) | Modell an der Drohne | nein — sonst prozedurale Form |
| `assets/justmoreparts/models/part/<kategorie>/<pfad>.json` | das eigentliche Modell | nein |
| `assets/justmoreparts/drone_paint.json` | Tint-Ebenen der Lack-Varianten | nein — sonst bleibt das Teil in Originalfarbe |
| `assets/justmoreparts/drone_layout.json` | optische Feinlage je Rahmen/Motor; `motor_seat` legt auch die Prop-Höhe für „Props im Bild“ fest (ohne Eintrag: Statorhöhe + 9 mm) | nein |

Ohne eigene Modelle zeichnet Propwash jedes Teil **prozedural** aus seiner Definition (Arme, Platte, Ducts, Stator,
Blattzahl, Packmaße, Akzentfarbe). Für das Inventar-Symbol genügt der Sonder-Renderer `propwash:part`:

```json
{"model": {"type": "minecraft:special", "base": "propwash:item/part_base", "model": {"type": "propwash:part"}}}
```
Das Symbol zeigt das Teil schräg von oben (45°), Rahmen und Video leicht gedreht, damit ein X als X lesbar bleibt.
Große Rahmen bekommen im Symbol kräftigere Arme und Motorpads, Props breitere Blätter als an der Drohne, damit sie auch
in 16 Pixeln erkennbar sind; an der Drohne bleibt die Form maßstabsgetreu.

**Neu in 1.4 — Kategorie-Basismodelle:** Statt `propwash:item/part_base` darf `base` auch
`propwash:item/part_base/<kategorie>` sein (`frame`, `stack`, `motor`, `prop`, `video`, `battery`, `accessory`). Diese
Modelle bringen die Hand-, Boden- und Rahmen-Größen der eingebauten Teile dieser Kategorie mit, damit ein Addon-Motor in
der Hand so groß wirkt wie ein Propwash-Motor (erzeugt mit `tools/assets/parts/part_bases.py`).

**Neu in 1.4 — Lack auf prozeduralen Teilen:** Die Lackierung (`PaintSpec`) erreicht auch Teile ohne eigenes Modell:
Propwash färbt die Grundfarbe der Ersatzform (Carbon, TPU, Akzentfarbe des Motors, Prop-Farbe, Akkufarbe, Kamera) in
der gewählten Farbe ein – in der Welt, im Werkbank- und Import-Vorschaubild und im Inventar.

**Neu in 1.4 — große Modelle:** Die Vanilla-`transformation` einer Render-Definition unter `items/drone_render/…` wendet
Propwash an (zugesichert, Selbsttest „drone-render: skalierte Drohnen-Modelle …“). Ein X-Class-Rahmen darf also ein
verkleinertes Modell liefern, das die `transformation` auf echte Größe zurückskaliert. Die Hülle eines Teils wird erst
ab ±8 Modell-Blöcken gekappt (±1024 mm um den Teil-Ursprung, vorher ±256 mm).

Im **Item-Rahmen** hängen Propwashs Drohnen, Rahmen und Props wie ein Bild flach an der Wand (Oberseite zum Betrachter,
Nase oben). Fertige Drohnen aus Addon-Teilen bekommen das automatisch, sie zeichnet Propwash. Für eigene Rahmen und Props
ohne Modell wählt man im Item-Rahmen dieselbe Anzeige mit `"wall": true` (der Generator `tools/assets/parts/addon.py`
schreibt das selbst):

```json
{"model": {"type": "minecraft:select", "property": "minecraft:display_context",
  "cases": [{"when": "fixed", "model": {"type": "minecraft:special", "base": "propwash:item/part_wall",
                                         "model": {"type": "propwash:part", "wall": true}}},
            {"when": "ground", "model": {"type": "minecraft:special",
                                         "base": "propwash:item/part_base/frame", "model": {"type": "propwash:part", "ground": true}}},
            {"when": ["on_shelf", "head"], "model": {"type": "minecraft:special",
                                         "base": "propwash:item/part_base/frame", "model": {"type": "propwash:part", "box": true}}}],
  "fallback": {"type": "minecraft:special", "base": "propwash:item/part_base/frame", "model": {"type": "propwash:part"}}}}
```
Eigene Rahmen- und Prop-Modelle setzen `display.fixed` auf Drehung −90°/180°/0° und füllen mit ihrem Fußkreis 24
Item-Pixel (Regeln und Werte in [`docs/ASSETS.md`](../ASSETS.md), „Item-Rahmen und Regal“; `kit.compute_display` rechnet
sie aus).

**Neu in 1.4 — Größe am Boden, im Regal und am Kopf (`"box": true`, `"ground": true`):** Das Symbol ohne Modell passt
sich an die GUI-Ansicht an; ein langes Teil (Akku, Zweiblatt-Prop) würde dadurch am Boden, im Regal und am Kopf größer als
die Propwash-Teile. Mit `"box": true` passt `propwash:part` das ganze Teil in einen Block, die Kategorie-Basis bringt dann
dieselbe Größe wie die eingebauten Teile. Für `on_shelf` und `head` (und bei Teilen, die nicht an der Wand hängen, auch
für `fixed`) `"box": true` wählen wie oben, für `ground` `"ground": true`: dasselbe, zusätzlich liegt ein sperriges Teil
(Motor, Action-Cam) höchstens so groß wie ein Würfel mit 0,3 Block Kante am Boden, wie ein fallengelassener Vanilla-Block
(0,25). `addon.py --icons special` schreibt beides selbst. Eigene Modelle messen dort mit `kit.compute_display` ebenfalls
den ganzen Quader, am Boden mit derselben Würfel-Grenze. Obergrenzen je Ansicht (am Boden höchstens ein halber Block)
stehen in [`docs/ASSETS.md`](../ASSETS.md), „Größenregel der Items“; Propwashs Selbsttest „content: Item-Größen aller
Items in allen Anzeige-Kontexten“ prüft auch die Items geladener Addons.

**Ducts im FPV-Bild (0.5.0):** Ducts eines Rahmenmodells erscheinen im FPV-Bild, wenn sie aus Ringsegmenten bestehen
(mindestens fünf gleiche Elemente rundum um die Motorachse, wie `kit.Model.ring` sie baut) und im Ring zwischen 0,97 ×
Radius des größten Props und Radius + max(6 mm, 20 %) liegen. Canopy, Arme und Streben im selben Bereich bleiben draußen.

**Neu in 1.5 — Größe in der Hand (`"hand": true`):** Teile werden in der Hand so groß gezeigt, wie es ihrer echten Größe
entspricht: Vorderansicht 9 px (erste Person) bzw. 7 px (dritte Person) × Handfaktor f = (L / 180 mm)^0,6, begrenzt auf
0,25–1, L = längste Kante in Millimetern. Ein 2207-Motor ist damit in der ersten Person 0,18 Block breit statt 0,56,
Rahmen und Props ab 180 mm bleiben wie bisher. `kit.compute_display` rechnet das für eigene Modelle aus (beide Hände,
Akkus in der ersten Person ausgenommen); Symbole ohne Modell wählen in allen vier Hand-Kontexten `"hand": true`:

```json
{"when": ["thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand"],
 "model": {"type": "minecraft:special", "base": "propwash:item/part_base/motor", "model": {"type": "propwash:part", "hand": true}}}
```

`addon.py --icons special` schreibt den Fall selbst (bei Akkus nur für die dritte Person). Ohne ihn bleibt ein Symbol in der
Hand so groß wie in Propwash 0.4.0; Propwash 0.4.0 ignoriert das Feld. Der Selbsttest misst Teile in der Hand gegen die
Grenze ihrer echten Größe (`ItemSizeRule.partLimit`, Werte in [`docs/ASSETS.md`](../ASSETS.md), „Teile in der Hand“).

Ein Teil darf auch ein vorhandenes Modell benutzen: `visual.renderModel` (JSON `"render_model"`), z. B.
`propwash:drone_render/motor/m2807_1300`, und als Symbol `{"model": {"type": "minecraft:model", "model":
"propwash:part/motor/m2807_1300"}}`. Details zu Varianten (`_ccw`, `_blur`, `_worn`, `_damaged`, `_broken`, `_paint`),
Layout-Schlüsseln und Lack stehen in [`docs/ASSETS.md`](../ASSETS.md).

---

## 7. Sprachdateien

| Zweck | Schlüssel | |
|---|---|---|
| Teilname | `part.justmoreparts.<pfad>` | Pflicht |
| Shift-Hilfe | `justmoreparts.help.<pfad>.summary`, `.what.1…8`, `.use.1…8` | `summary` empfohlen, Zeilen ≤ 48 Zeichen |
| Rahmen-Kurzname (Klasse, Bestenliste, Bindung) | `justmoreparts.frame_short.<pfad>` | fehlt → Teilname |
| Motor-/Stack-Halterung, Prop-Aufnahme | `justmoreparts.mount.motor.<pfad>`, `.mount.stack.<pfad>`, `.mount.prop.<pfad>` | |
| Zellchemie | `justmoreparts.chemistry.<pfad>` | |
| Preset | `justmoreparts.preset.<pfad>` (`DronePreset#translationKey`) | |
| Name des eigenen Kreativ-Tabs | `itemGroup.justmoreparts` (`ExtensionTab#nameKey()`), nur der Name, z. B. „Just More Parts“ | empfohlen, sonst Mod-Name; Propwash setzt „Propwash: “ davor |
| Flugschul-Szene `justmoreparts:<name>` | `justmoreparts.guide.scene.<name>.title/description/step.<schritt>.title/text/callout.<n>/hint.<n>` | |
| eigener Item-Tag `justmoreparts:<pfad>` | `tag.item.justmoreparts.<pfad>` (Schrägstriche im Pfad → Punkte) | Pflicht, sonst warnt Fabric im Entwicklungsmodus; Rezeptanzeigen zeigen den Namen |

**Namen** folgen dem gemeinsamen Schema aus [`docs/design/part-naming.md`](../design/part-naming.md), damit fremde Teile
neben den eingebauten gleich aussehen: Kenngröße und Gerät zuerst, Zusätze in Klammern, Zoll als `″`, Chemie wie in der
Chemie-Tabelle. Beispiele: `2806.5 Motor, 1300 KV` / `2806,5-Motor, 1300 KV`, `13″ Propeller (2-blade, 7.0″ pitch,
carbon)` / `13-Zoll-Propeller (2 Blätter, 7,0″ Steigung, Carbon)`, `8S 4500 mAh Battery (Li-Ion)` /
`8S-Akku 4500 mAh (Li-Ion)`, `30×30 FC/ESC Stack (3–8S, 65 A)`, `5″ Freestyle Frame (deadcat)` /
`5-Zoll-Freestyle-Rahmen (Deadcat)`, Kurzname `Deadcat 5″`. Die Shift-Kurzbeschreibung beginnt mit der Kurzkennung aus
§6 des Schemas, danach ` – ` und der Einsatz (Zeile höchstens 48 Zeichen): `5″ freestyle frame – deadcat, no props in view` /
`5″-Freestyle-Rahmen – Deadcat, Bild ohne Props`, `8S Li-Ion, 4500 mAh – long-range pack`. Propwash prüft fremde Namen
nicht.

Propwash stellt Texte bereit, die Addons wiederverwenden können: `ImportStatus#translationKey()`
(`propwash.api.import.<status>`), `ImportLayer#translationKey()`, `TuneGroup#translationKey()`,
`BuildCheck.translationKey(issue)` und `BuildStats.warningTranslationKey(warning)`.

**Neu in 1.4 — Texte ohne Client:** `PropwashApi.text("de_de", "part.justmoreparts.deadcat_5")` liest den Text aus den
Sprachdateien aller geladenen Mods (`assets/<ns>/lang/<code>.json`), auch auf dem dedizierten Server und unabhängig von
der Spielsprache — für Exporte und Server-Werkzeuge. Leer bei fehlendem Schlüssel (kein Rückfall auf `en_us`);
Minecraft-eigene Texte liefert es nicht. Im Spiel bleibt `Component.translatable` richtig.

---

## 8. Rezepte, Tags, Beute

- Rezepte sind normale Vanilla-Rezepte des Addons. Empfehlung: Gruppe `propwash_<kategorie>`, Reiter `equipment`,
  Zwischenteile `propwash:carbon_plate`, `copper_coil`, `circuit_board`, `lipo_cell`, `hd_chip` als Zutaten (stabil).
- Tags `#propwash:parts` und `#propwash:parts/<kategorie>` enthalten alle eingebauten Teile; Addons ergänzen sie mit
  `"replace": false`. Freischalt-Fortschritte können auf diese Tags prüfen. Ihre Anzeigenamen
  (`tag.item.propwash.parts`, `tag.item.propwash.parts.<kategorie>`) liefert Propwash; eigene Tags übersetzt das Addon
  selbst (Abschnitt 7).
- Truhen-Beute (**Neu in 1.4**): Das Addon legt eine eigene Beutetabelle an (`data/<ns>/loot_table/…`, Typ
  `minecraft:chest`) und hängt sie während der Mod-Initialisierung in eine bestehende Tabelle ein – Propwash erledigt das
  auf beiden Loadern (Fabric `LootTableEvents.MODIFY`, NeoForge `LootTableLoadEvent`) mit einem zusätzlichen Pool, der
  die Addon-Tabelle einmal würfelt; eigener Loader-Code oder Global Loot Modifier sind nicht nötig:

  ```java
  ResourceKey<LootTable> dungeon = ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("chests/simple_dungeon"));
  ResourceKey<LootTable> mine = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MOD_ID, "inject/simple_dungeon"));
  PropwashApi.loot().inject(dungeon, mine);                 // API 1.4, offene Phase
  PropwashApi.loot().tablesFor(dungeon);                    // alles, was in diese Tabelle eingehängt ist
  ```

  Ziele dürfen auch Propwash-Tabellen sein, z. B. die Truhen in Bandos (`propwash:chests/bando`, seit Propwash 0.5.0).
  Die Addon-Tabelle braucht den eigenen Namensraum (nicht `minecraft`/`propwash`); doppelte Paare, eine Tabelle in sich
  selbst und Anmeldungen nach der Mod-Initialisierung werfen eine Ausnahme mit Ids. Die Wahrscheinlichkeit steuert die
  Tabelle selbst (z. B. ein `minecraft:empty`-Eintrag mit Gewicht). Ersetzt ein Datenpaket die Vanilla-Tabelle, wird die
  Addon-Tabelle trotzdem eingehängt. Wer die Methode benutzt, verlangt `PropwashApi.requireVersion(modId, "1.4")`.
- Recycling im Zerlegen-Feld: `basics.recycle` oder der Kategorie-Standard.

---

## 9. Presets und eigener Kreativ-Tab

```java
DroneData longRange = new DroneData(
        BuildSpec.of(id("lr_8"), id("stack_30_8s"), id("m2806_1300"), Identifier.fromNamespaceAndPath("propwash", "p7_2b"),
                Identifier.fromNamespaceAndPath("propwash", "digital_hd"), id("b8s_4500_lion")),
        PaintSpec.DEFAULT.with("frame", 0x1B1D22), TuneSpec.EMPTY, Optional.of("LR 8S"));
PropwashApi.builds().registerPreset(DronePreset.of(id("lr8s"), longRange));        // nicht im Namensraum propwash

PropwashApi.extensionTab("justmoreparts")                                           // API 1.2, offene Phase
        .icon(id("deadcat_5"))                                                      // Item-Id oder Supplier<ItemStack>
        .order(TabSection.PRESETS, TabSection.FRAMES, TabSection.MOTORS, TabSection.PROPS,
                TabSection.STACKS, TabSection.BATTERIES, TabSection.VIDEO)
        .add(() -> List.of(PropwashApi.builds().createDrone(longRange.withName("LR 8S Race"))));
```
**Jedes Addon hat einen eigenen Kreativ-Tab** direkt hinter dem Propwash-Tab. Teil-Items (`creativeListed`) und
gelistete Presets eines Namensraums landen dort und **nicht** im Propwash-Tab; die eingebauten Einträge bleiben wie sie
sind.

| Was | Wie |
|---|---|
| Anlegen | automatisch beim ersten Teil-Item oder gelisteten Preset des Namensraums, oder ausdrücklich mit `PropwashApi.extensionTab(ns)` — beides nur in der offenen Phase; derselbe Namensraum liefert immer dasselbe `ExtensionTab` |
| Titel | immer „Propwash: <Name>“: Propwash liefert `itemGroup.propwash.extension` („Propwash: %s“), das Addon den Namen unter `itemGroup.<ns>` (de und en, Abschnitt 7); fehlt er, nimmt Propwash den Mod-Namen ohne „Propwash:“ davor |
| Symbol | `icon(itemId)` oder `icon(Supplier<ItemStack>)` (erst beim Zeichnen aufgerufen); sonst der erste Eintrag des Tabs |
| Reihenfolge | `order(TabSection…)`: `PRESETS` (gelistete Presets in Anmeldereihenfolge), `FRAMES`, `STACKS`, `MOTORS`, `PROPS`, `VIDEO`, `BATTERIES`, `ACCESSORIES` (Teil-Items der Kategorie nach `sort`, dann Id), `ENTRIES` (die `add(…)`-Lieferanten). Nicht genannte Abschnitte hängen in dieser Standard-Reihenfolge hinten an |
| Zusätzliche Einträge | `add(Supplier<Collection<ItemStack>>)` wie bei `creativeTab()`: Lieferanten laufen beim Aufbau des Tabs, Ausnahmen werden geloggt und übersprungen, doppelte Stapel entfernt; `collect()` liefert nur diese Einträge, `contents()` den ganzen Tab |
| Abfragen | `tabId()` (`propwash:main-<ns>`), `registered()`, `sections()`, `iconStack()`, `contents()`; `PropwashApi.extensionTabs()` listet alle |
| Leerer Tab | wird von Minecraft ausgeblendet (z. B. wenn alle Teile `creative: false` sind) |

- Position: Fabric sortiert Mod-Tabs nach Namensraum und Pfad, `propwash:main-<ns>` steht deshalb direkt hinter
  `propwash:main`; NeoForge bekommt die Reihenfolge ausdrücklich (`withTabsBefore`). Mehrere Addon-Tabs stehen nach
  Namensraum sortiert.
- `PropwashApi.creativeTab().add(…)` gibt es weiter: Solche Einträge landen bewusst im **Propwash-Tab** hinter den
  eingebauten. Für eigene Inhalte ist der eigene Tab gedacht.
- Kann der Tab nicht angelegt werden (z. B. Namensraum erst nach dem Einfrieren), bleiben Teile und Presets des Addons
  als Rückfall im Propwash-Tab.

`builds().presets()` liefert eingebaute (`propwash:whoop`, `cinewhoop`, `freestyle`, `racer`, `long_range`) und
angemeldete Presets. Entspricht der Bauplan in der Werkbank (ohne Zubehör) einem angemeldeten Preset, steht dessen Name
über der Vorschau: der Name aus `DroneData`, sonst `<ns>.preset.<pfad>`.

---

## 10. Baupläne und Drohnen-Items (`PropwashApi.builds()`)

| Typ | Inhalt |
|---|---|
| `BuildSpec` | Ids von Rahmen, Stack, Motor (×4), Prop (×4), Video, Akku und Zubehör je `AccessorySlot` |
| `PaintSpec` | RGB je Lack-Platz: `frame`, `tpu`, `motors`, `prop_fl`, `prop_fr`, `prop_rl`, `prop_rr`, `stack`, `battery`, `camera`, `antenna`, `accessories` (fehlend = Originalfarbe) |
| `TuneSpec` | offene Tabelle Schlüssel → Wert wie im Flight-Controller (höchstens 256 Einträge) |
| `DroneData` | `BuildSpec` + `PaintSpec` + `TuneSpec` + Name (≤ 48 Zeichen) |

**Nie enthalten:** Akkustand, Verschleiß, Drohnen-UUID, Bindungen an Funke/Brille, Funkprofile, Schalter- und
Tastenbelegung.

| Methode | Verhalten |
|---|---|
| `isDrone(stack)` | Propwash-Drohnen-Item? |
| `read(stack)` | `DroneData` des Items (ohne Bauplan: 5″-Freestyle wie beim Platzieren); leer bei anderen Items. Das Tuning ist das **gespeicherte**: nur Werte, die vom Standard abweichen (abgeleitete PIDs fehlen) |
| `createDrone(data)` | neues Drohnen-Item wie ein Preset: voller Akku, kein Verschleiß, Lackierung samt Symbol-Farben, bereinigtes Tuning, Name (auf 24 Zeichen gekürzt wie überall in Propwash). `IllegalArgumentException` bei unbekannten Teilen, Zubehör im falschen Platz oder Problemen (nicht baubar nach der Welt-Regel „Teile müssen zusammenpassen“) |
| `withTune(drone, tune)` | Kopie mit **ersetztem** Tuning (fehlende Schlüssel = Standard, Werte bereinigt); für einzelne Gruppen vorher `overlay` benutzen |
| `withPaint(drone, paint)` | Kopie mit neuer Lackierung; andere Komponenten bleiben |
| `check(spec)` | `BuildCheck`: unbekannte Ids, Probleme, Warnungen (Schlüssel wie in 3.5). **Seit 1.5** folgt die Prüfung der Welt-Regel `propwash:part_compatibility` („Teile müssen zusammenpassen“): Ist sie aus, stehen Probleme wie `motor_mount` oder `prop_too_large` unter `warnings()` und `buildable()` ist wahr; nur `missing_part` bleibt ein Problem |
| `partCompatibility()` (**neu in 1.5**) | `true`, wenn auf dieser Seite die Regel gilt (Server: Gamerule, Client: vom Server gemeldet, ohne Welt `true`) – z. B. um „in echt nicht baubar“ anzuzeigen |
| `analyze(spec)` | `BuildStats` wie im Werkbank-Bericht (Masse, Schub/Gewicht, Schwebegas, Flugzeiten Schweben/Reisen/gemischt/aggressiv, Reisegeschwindigkeit, Ströme, Landespannung, Grundlast, Topspeed, Motortemperaturen nach 60 s Vollgas, nach gemischtem Flug und im Dauervollgas, Props im Bild, Warnungen); leer bei unbekannten Teilen. Definitionen: Flugmodell-Spezifikation Kap. 18 |
| `cameraView(spec)` (API 1.1) | `CameraView`: FPV-Kamera, mit der Propwash das Bild zeichnet und „Props im Bild“ rechnet – Objektiv `lensMm` und vier `PropDisc`s (Scheibenmitte `centerMm`, `radiusMm` bis zur Blattspitze, `hubRadiusMm`) in Rahmen-Millimetern (x rechts, y oben, −z vorn), `uptiltDeg` (Standard des Rahmens), `uptiltRange()` (API 1.4: Bereich des Kamerahalters `minDeg`/`maxDeg`/`defaultDeg`), horizontales `horizontalFovDeg` (Kamera im Videomodul, ohne Angabe analog 120°, digital 130°), `aspect` 16:9, `verticalFovDeg()`, `propsInViewPercent`; leer bei unbekannten Teilen. Für Kamera-Vorschauen (z. B. Web-Konfigurator) |
| `cameraView(spec, uptiltDeg)` (API 1.4) | dasselbe bei einem anderen Kamerawinkel (auf den Halter des Rahmens begrenzt), `propsInViewPercent` für diesen Winkel |
| `uptiltRange(frame)` (API 1.4) | Bereich des Kamerahalters eines Rahmens (`CameraView.UptiltRange`), leer bei unbekannten Ids |
| `defaultTune(spec)` | vollständige Standardwerte des Flugmodells samt Kamerawinkel des Rahmens (`TuneSpec.CAMERA_UPTILT`) und LED-Standards; **wirft `IllegalArgumentException`** bei unbekannten Teilen oder Zubehör im falschen Platz (vorher `check(spec)`) |
| `applyImport(player, request)` | Server-Thread: Import nach Abschnitt 13.4 (feuert `BUILD_IMPORTED`) |
| `partCondition(drone, kind)` (**neu in 1.4**) | Zustand 0…1 eines Teils am Drohnen-Item (1 = neu, 0 = kaputt): Rahmen = schwächster Arm, Motor/Prop = schwächster der vier, Stack, Video, Akku; leer für Zubehör und andere Items |
| `paintCost(drone, target)` (**neu in 1.4**) | Farbstoffe für das Umlackieren auf `target` im Überlebensmodus, wie das Lack-Fenster rechnet: `"red"` → 2, eigene Hex-Farben zusammen unter `"custom"` (je ein beliebiger Farbstoff); leer = kostenlos. Für die Werkbank das Rahmen-Item übergeben (dort liegt der Lack) oder eine Vorschau-Drohne aus `createDrone(context.result())` |

`createDrone`, `withTune` und `withPaint` funktionieren auf Server und Client (Vorschau); ins Inventar eines Spielers
gehört ein erzeugtes Item nur auf dem Server (vom Client aus: `PropwashClientApi.sendCreateDrone`, Abschnitt 13.4).

**Neu in 1.4 — weitere Kennzahlen in `BuildStats`** (nur Analyse, das Flugmodell bleibt unverändert):

| Kennzahl | Bedeutung |
|---|---|
| `propwashNoise()` | Faktor des Propwash-Rauschens gegenüber Steifigkeit 1,0 bei sonst gleichem Aufbau (`stiffness^−0,5`; eingebaute Props 1,0, Carbon 2,2 → 0,67) |
| `fullThrottleCellVoltage()` | Zellspannung in V bei Vollgas im Stand mit vollem Akku — liegt sie unter der Abschaltspannung der Chemie, warnt `heavy_sag` |
| `rollAuthorityRadS2()`, `pitchAuthorityRadS2()`, `yawAuthorityRadS2()` | Winkelbeschleunigung in rad/s² bei vollem Differenzausschlag um den Schwebepunkt, aus Motorlage, Trägheit und Schub-/Drehmoment-Steigung der Motoren (dieselbe Regelstrecke, aus der Propwash die PIDs ableitet). Das Verhältnis Nick/Roll zeigt, ob eine Rahmenform Flips oder Rolls bevorzugt |

Ältere Konstruktoren von `BuildStats` bleiben und setzen die neuen Werte auf `NaN`.

Warnungen in `BuildStats#warnings()` (Text: `BuildStats.warningTranslationKey(w)`; `BuildStats.WARNINGS` enthält dieselbe
Liste, JUnit `DocTablesTest` hält sie mit dieser Zeile gleich):

<!-- build-warnings:begin -->
`cannot_hover`, `underpowered`, `sluggish_motors`, `heavy_sag`, `esc_current_limited`, `battery_overload`, `motor_thermal`, `props_in_view`, `esc_overvoltage`
<!-- build-warnings:end -->

---

## 11. Tuning-Gruppen

`TuneGroup.of(schlüssel)` ordnet jeden Schlüssel stabil zu:

| Gruppe | Schlüssel |
|---|---|
| `RATES` | `rates.*` |
| `PID` | übrige `pid.*` (P/I/D je Achse, `pid.source`, Angle/Horizon, I-Term-Relax, Anti-Gravity, Airmode) |
| `FEEDFORWARD` | `pid.<achse>.ff` |
| `TPA` | `pid.tpa*` |
| `THROTTLE` | `pid.throttle*`, `pid.motorIdlePercent`, `pid.thrustLinearPercent`, `pid.vbatSagCompensationPercent` |
| `FILTERS` | `filters.*` |
| `HOLD` | `hold.*` |
| `CAMERA` | `camera.*` (Uptilt der Drohne, `TuneSpec.CAMERA_UPTILT`; Propwash begrenzt ihn auf den Kamerahalter, ohne Wert gilt der Standard des Rahmens) |
| `LED` | `led.*` |
| `VTX` | `vtx.*` (Videokanal — gehört nie in einen Sharecode) |
| `OTHER` | alles andere |

`TuneGroup.FLIGHT` = Rates, PID, Feedforward, TPA, Filter, Gaskurve — der Standard eines Sharecodes.

**Neu in 1.4 — alle Schlüssel mit Bereich und Wertcodes:** Der Katalog in [`parts-api.md`](../design/parts-api.md)
Abschnitt 11.5 nennt jeden Tuning-Schlüssel mit Gruppe, Bereich, Schritt, Einheit und den Zahlen der Auswahlen
(`rates.type`, `pid.source`, `pid.itermRelax`, `pid.tpaMode`, `pid.throttleLimitType`, `filters.pidLoop`, `led.mode`,
`led.color` …). Er wird aus den Tabellen des Flight-Controllers erzeugt und per JUnit geprüft.

```java
TuneSpec shared = data.tune().only(TuneGroup.FLIGHT);                         // exportieren
TuneSpec merged = current.overlay(imported, EnumSet.of(TuneGroup.RATES));     // nur Rates ersetzen, Rest behalten
```
`overlay` entfernt die gewählten Gruppen aus `current` und nimmt sie aus `imported`. Weil gespeicherte Tunings nur
Abweichungen vom Standard enthalten, bedeutet ein fehlender Schlüssel „Standardwert“ — so landen auch zurückgesetzte
Werte korrekt auf der Zieldrohne, und abgeleitete PIDs werden für den Ziel-Bauplan neu berechnet.

---

## 12. OSD-Layout (nur Client)

`PropwashClientApi.osd()` liest und setzt das OSD-Layout des Spielers als `OsdLayoutSpec(version, json)`:
`current()`, `defaults()`, `apply(spec)` (speichert wie der OSD-Tab; `false` bei ungültigem JSON, der Grund steht nur
im Debug-Log – die Fehlermeldung an den Spieler gibt das Addon aus). Schema Version 1:

```json
{"v": 1, "preset": "standard",
 "style": {"opacity": 60, "line_opacity": 60, "scale": 1.0, "color": "#ffffff", "warning_color": "#ff3b30",
           "outline": "thin", "background": "off", "colored_alerts": true, "pitch_ladder": true, "ladder_step_deg": 10.0},
 "elements": {"speed": {"on": true, "col": 1, "row": 12}, "battery_voltage": {"on": true, "col": 1, "row": 14, "color": "#ff0000"},
              "crosshair": {"on": true}}}
```
- `preset`: `minimal`, `standard`, `full`, `race`; Elemente heißen wie im OSD-Tab (`crosshair`, `speed`, `altitude`, …).
- `col`/`row` nur bei verschiebbaren Elementen, `color`/`scale` nur bei eigenen Werten je Element.
- Beim Übernehmen: fehlende Elemente stehen wie im Preset, fehlende Stilwerte auf Standard, unbekannte Elemente und
  Felder werden übersprungen, alle Werte werden wie im OSD-Tab begrenzt. Höchstens 8192 Zeichen.
- Nicht enthalten (Spielervorlieben): Vanilla-HUD ausblenden, analoges Bildzerreißen.
- **Neu in 1.4:** `validate(spec)` prüft nach denselben Regeln wie `apply`, ohne etwas zu übernehmen – für eine
  Eingabe, die erst bei gültigem Layout den „Übernehmen“-Knopf freigibt (Beispiel: OSD-Feld im Tab „API-Beispiel“).

---

## 13. Sharecodes Schritt für Schritt

Das Sharecode-System lebt **im Addon**; Propwash liefert Daten, Prüfung und den server-geprüften Import.

### 13.1 Was in einen Code gehört
| Ebene | Standard | Quelle |
|---|---|---|
| Teile (`BuildSpec`) | immer | `builds().read(stack)` bzw. `WorkbenchContext#result()` |
| Lackierung (`PaintSpec`) | an | dieselbe `DroneData` |
| Flug-Tuning (`TuneSpec`, nur `TuneGroup.FLIGHT`) | an | dieselbe `DroneData`, `.only(TuneGroup.FLIGHT)` |
| OSD-Layout | aus | `PropwashClientApi.osd().current()` |
| Name | an | `DroneData#name` |

Nie: Schalter-/Tastenbelegung, Funkprofile, Akkustand, Verschleiß, Videokanal (`VTX`), Bindungen.

### 13.2 Kodieren (Beispiel, JDK und Gson)
```java
public final class ShareCode {
    public static final String PREFIX = "JMP1:";

    public static String encode(DroneData data, boolean paint, boolean tune, Optional<OsdLayoutSpec> osd) {
        JsonObject root = new JsonObject();
        BuildSpec b = data.build();
        JsonObject build = new JsonObject();
        build.addProperty("frame", b.frame().toString());
        build.addProperty("stack", b.stack().toString());
        build.addProperty("motor", b.motor().toString());
        build.addProperty("prop", b.prop().toString());
        build.addProperty("video", b.video().toString());
        build.addProperty("battery", b.battery().toString());
        b.accessories().forEach((slot, id) -> build.addProperty(slot.key(), id.toString()));
        root.add("build", build);
        if (paint) {
            JsonObject colors = new JsonObject();
            data.paint().colors().forEach((slot, rgb) -> colors.addProperty(slot, rgb));
            root.add("paint", colors);
        }
        if (tune) {
            JsonObject values = new JsonObject();
            data.tune().only(TuneGroup.FLIGHT).values().forEach(values::addProperty);
            root.add("tune", values);
        }
        osd.ifPresent(layout -> root.add("osd", JsonParser.parseString(layout.json())));
        data.name().ifPresent(name -> root.addProperty("name", name));
        byte[] json = root.toString().getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(out)) {
            deflate.write(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }
}
```
Beim Dekodieren alle Ids mit `Identifier.tryParse` lesen, Records in `try`/`catch (IllegalArgumentException)` bauen
(ungültige Codes nie als Absturz behandeln) und die Länge des Eingabetexts begrenzen.

### 13.3 Prüfen und zeigen
```java
BuildCheck check = PropwashApi.builds().check(data.build());
if (!check.known()) {                 // Teile fehlen → Addon fehlt oder ist zu alt
    check.unknownParts().forEach(id -> list.add(Component.literal(id.toString())));
} else if (!check.buildable()) {
    check.problems().forEach(p -> list.add(Component.translatable(BuildCheck.translationKey(p))));
}
PropwashApi.builds().analyze(data.build()).ifPresent(stats -> showStats(stats));
PropwashClientApi.renderPreview(graphics, data, x0, y0, x1, y1, yaw);    // im extractRenderState eines Screens
```
**Neu in 1.4 — fester Maßstab:** `renderPreview(graphics, data, x0, y0, x1, y1, yaw, pixelsPerMillimetre)` zeichnet die
Drohne in echter Größe (Pixel je Millimeter) statt eingepasst – mehrere Drohnen nebeneinander stehen so im selben
Maßstab. `PropwashApi.builds().footprintMm(spec)` liefert den Durchmesser über die Propspitzen (weitester Abstand
zweier Motorachsen plus ein Prop-Durchmesser, aus derselben Geometrie wie das FPV-Bild); `IllegalArgumentException` bei
unbekannten Teilen. Beispiel: `dev.example.client.ExampleSizeScreen` (Knopf „Größe“ in der Werkbank).

### 13.4 Übernehmen — der Server entscheidet
Der Spieler wählt die Ebenen („Ganzer Bauplan“, „Nur Tuning auf die gehaltene Drohne“, „Nur Lackierung“, „OSD-Layout
übernehmen“):

```java
// in der Werkbank (WorkbenchContext aus Abschnitt 14.1)
context.importIntoWorkbench(data, EnumSet.allOf(ImportLayer.class), TuneGroup.FLIGHT).thenAccept(this::show);
// nur Tuning auf die Drohne in der Haupthand
PropwashClientApi.sendImport(ImportRequest.tuneOnHeldDrone(data)).thenAccept(this::show);
// nur Lackierung auf die Drohne in der Haupthand
PropwashClientApi.sendImport(ImportRequest.paintOnHeldDrone(data)).thenAccept(this::show);
// neu in 1.4: frei gewählte Ebenen auf die gehaltene Drohne (ohne kanonischen Konstruktor), z. B. Lack und Name
PropwashClientApi.sendImport(ImportRequest.onHeldDrone(data, EnumSet.of(ImportLayer.PAINT, ImportLayer.NAME))).thenAccept(this::show);
// neu in 1.4: fertige Drohne ins Inventar (Kreativmodus oder Operator)
PropwashClientApi.sendCreateDrone(data).thenAccept(this::show);
// OSD-Layout: rein clientseitig
PropwashClientApi.osd().apply(new OsdLayoutSpec(OsdLayoutSpec.CURRENT_VERSION, osdJson));
```

Ablauf: Client → Payload `propwash:api_import` → Server prüft Größe und Rate (höchstens **2 Anfragen je Sekunde und
Spieler**, sonst `BUSY`), das Ziel (offene Werkbank mit passender Container-Id bzw. Drohne in der Haupthand, sonst
`NO_TARGET`), unbekannte Teile (`UNKNOWN_PARTS`) und Zuschauermodus (`NOT_ALLOWED`) → Import → Event `BUILD_IMPORTED`
→ Antwort `propwash:api_import_result` → das `CompletableFuture` wird auf dem Render-Thread erfüllt. Ebenen gelten in
der Reihenfolge PARTS → PAINT → TUNE → NAME, jede ganz oder gar nicht; scheitert eine, folgen die nächsten nicht.

| Ziel | Ebene | Überlebensmodus | Kreativmodus |
|---|---|---|---|
| Werkbank | PARTS | passende Slots bleiben, übrige Teile wandern ins Inventar (kein Platz → `NOT_ALLOWED`), benötigte Teile kommen aus dem Inventar (bester Zustand zuerst, Akku mit höchstem Ladestand); fehlt etwas → `MISSING_PARTS` mit je fehlendem Stück einer Id | Teile werden neu erzeugt |
| | PAINT | Farbstoffe wie im Lack-Fenster, sonst `MISSING_DYES` | gratis |
| | TUNE | „ausstehendes Tuning“, die entnommene Drohne trägt es | gleich |
| | NAME (neu in 1.4) | Name am Rahmen, die entnommene Drohne trägt ihn (24 Zeichen); ohne Name bleibt der bisherige | gleich |
| gehaltene Drohne | PAINT | Farbstoffe wie im Lack-Fenster | gratis |
| | TUNE | `aktuell.overlay(import, gruppen)`, bereinigt | gleich |
| | NAME (neu in 1.4) | wie Umbenennen im Konfigurator, die gebundene Funke übernimmt ihn; ohne Name bleibt der bisherige | gleich |
| | PARTS | nicht erlaubt (`ImportRequest` wirft schon beim Bauen) | — |
| neue Drohne (`NEW_DRONE`, neu in 1.4) | PARTS + gewählte Ebenen | nur Operatoren, sonst `NOT_ALLOWED` | neue Drohne ins Inventar (voll → vor die Füße); nicht baubar → `INVALID` |

**`NAME` ändert bestehende Importe:** `ImportRequest.wholeBuild` und `EnumSet.allOf(ImportLayer.class)` enthalten seit
1.4 auch `NAME`. Ein Name wird nur gesetzt, wenn die `DroneData` einen trägt; gelöscht wird nie einer. Wer den Namen
nicht übernehmen will, wählt `EnumSet.of(ImportLayer.PARTS, ImportLayer.PAINT, ImportLayer.TUNE)`.

Ergebnis (`ImportResult`): `status` (`APPLIED`, `UNCHANGED`, `UNKNOWN_PARTS`, `MISSING_PARTS`, `MISSING_DYES`,
`NO_TARGET`, `NOT_ALLOWED`, `INVALID`, `BUSY`), `applied` (übernommene Ebenen), `missingParts`, `missingDyes`
(Farbstoff → Anzahl). Anzeige mit `Component.translatable(result.status().translationKey())`. Ohne Verbindung,
beim Trennen und nach 10 Sekunden ohne Antwort endet eine Anfrage mit `NO_TARGET`. Anfragen über 30000 Bytes
(kodiert) werden nicht gesendet und enden sofort mit `INVALID` (Grund nur im Debug-Log). Aufrufe nur vom Render-Thread.

Server-Code (z. B. ein Befehl des Addons) ruft `PropwashApi.builds().applyImport(player, request)` direkt auf; dort
gibt es keine Ratenbegrenzung.

### 13.5 Tuning ohne Import (nur Client)
`PropwashClientApi.tune()`: `available()` (aktive Pilot-Sitzung oder Drohne in der Hand), `current()` (vollständige
wirksame Werte), `defaults()`, `apply(tune)`. `apply` erwartet wie der Konfigurator das **ganze** Tuning; für einzelne
Gruppen: `tune().apply(tune().current().overlay(importiert, gruppen))`. Die `DroneData` der Drohne in der Haupthand
liefert `PropwashClientApi.heldDrone()` (direkt an `PropwashClientApi`, nicht an `tune()`).

---

## 14. Erweiterungspunkte im Client

### 14.1 Werkbank: Knöpfe und Kind-Fenster
```java
public final class ShareCodeButton implements WorkbenchExtension {
    @Override
    public Identifier id() {
        return JustMoreParts.id("share_code");
    }

    @Override
    public void init(WorkbenchContext context) {
        context.addButton(Component.translatable("justmoreparts.workbench.code"),
                Component.translatable("justmoreparts.workbench.code.tooltip"),
                () -> context.openChild(parent -> new ShareCodeScreen(parent, context)));
    }
}
```
- `init` läuft bei **jedem** Aufbau des Werkbank-Screens (auch nach einer Größenänderung) auf dem Render-Thread;
  Ausnahmen werden geloggt und betreffen nur diese Erweiterung. `order()` (Standard 1000) sortiert.
- `WorkbenchContext`: `screen()`, `containerId()`, `creative()`, `slot(kind, position)` (Id des Teils im Slot; Motoren
  und Props 0–3 in der Reihenfolge vorne links, vorne rechts, hinten links, hinten rechts), `result()` (fertige Drohne,
  wenn baubar), `addButton(…)`, `openChild(…)`, `importIntoWorkbench(…)`.
- Platzierung: bis zu zwei Knöpfe links neben „Lackierung“, weitere hinter „⋯“. Beschriftungen kurz halten.
- **Neu in 1.4 — Platz im Kopf:** Propwash reserviert Marke und Titel („Drohnen-Werkbank“ in jeder Sprache) und
  verteilt nur den Rest; der Titel wird nie abgeschnitten. `headerSpace()` nennt diesen Rest in GUI-Pixeln (inklusive
  der Abstände von `BUTTON_GAP` = 3 px), `buttonWidth(label)` die Breite, die ein Text-Knopf bekäme (Text + 14 px,
  höchstens 80 px).
- **Neu in 1.4 — Kurzbeschriftung:** `addButton(label, shortLabel, tooltip, onPress)` – passt `label` nicht, nimmt
  Propwash `shortLabel` (zuerst beim breitesten Knopf), erst danach wandern Knöpfe hinter „⋯“. Der Tooltip zeigt immer
  die volle Beschriftung.
- **Neu in 1.4 — Symbol-Knopf:** `addIconButton(sprite, tooltip, onPress)` zeichnet ein 13 × 13-px-GUI-Sprite des Addons
  (`assets/<ns>/textures/gui/sprites/<pfad>.png`, Id ohne `textures/gui/sprites/`) im Propwash-Knopfstil, Breite 13 px;
  der Tooltip ist dann Titel und Erklärung zugleich. Ein Addon wählt selbst zwischen Text und Symbol, z. B.
  `context.headerSpace() >= context.buttonWidth(label) + …` (Beispiel: `ExampleAddonClient.sizeAsText`).
- **Neu in 1.4 — fehlende Teile:** `missingParts(data)` nennt, was dem Spieler für diesen Bauplan fehlt – nach denselben
  Regeln wie der Server beim Werkbank-Import (Teile in den Werkbank-Slots zählen, dann das Inventar; Kreativ: leer;
  unbekannte Ids: genau diese). Synchron auf dem Client, weil Slots und Inventar dort gespiegelt sind; der Server
  entscheidet beim Import trotzdem selbst (Antwort `MISSING_PARTS`).
- Kind-Fenster: „Zurück“/Esc kehrt zur Werkbank zurück (`minecraft.gui.setScreen(parent)`), das Fenster schließt sich,
  wenn der Server das Menü schließt.

### 14.2 Konfigurator-Tabs
```java
PropwashClientApi.registerConfigTab(ShareCodeTab::new);
```
Ein Tab erweitert `ui.client.config.ConfigTab`: Id mit Namensraum (`"justmoreparts:sharecode"`), eigene `order()` ab
1000, Widgets in `build()` über `host.addWidget(…)`, Zeichnen in `extract(…)`, Änderungen melden mit
`host.changed()`, Speichern in `save()`, Verwerfen in `discard()` (beides auch ohne vorheriges `build()`), reine
Anzeige-Tabs mit `readOnly()`. Ein Tab mit Knöpfen, die sofort wirken, ist **nicht** `readOnly()` (sonst steht in der
Fußzeile „Nur Anzeige“): Er sagt mit `storage()`, wo die Wirkung landet (`DRONE`, `GAME`), bei gemischter Wirkung
`Storage.MIXED` mit eigenem Fußzeilentext über `mixedStorageKey()` — so macht es der Beispiel-Tab „API-Beispiel“.
Eine Fabrik darf `null` liefern — der Tab erscheint dann nicht. Vertrag im Detail:
[`FOUNDATION.md`](../FOUNDATION.md) §15 „Konfigurator-Tabs“.

**Neu in 1.5 – Tabs scrollen von selbst:** Passt der Inhalt eines Tabs nicht in die Ansicht (große GUI-Skala, kleines
Fenster), verschiebt ihn der Konfigurator – mit Scrollleiste, Mausrad und Bild auf/ab. Der Tab rechnet dabei weiter in
den Koordinaten aus `build()`. Die Höhe ergibt sich aus der untersten Kante seiner Widgets; zeichnet der Tab Text
darunter, meldet er dessen untere Kante mit `reportBottom(y)` (in `build()` oder in jedem `extract()`). Was beim
Scrollen stehen bleiben soll, zeichnet er um `host.contentShift()` versetzt. Ein Tab mit eigenem Bildlauf überschreibt
`scrollsItself()`. Regeln und Prüfung: [`docs/design/ui-scaling.md`](../design/ui-scaling.md).

**Neu in 1.4 – Shift-Hilfe ohne Taste:** `PropwashClientApi.forceShiftHelp(true)` zeigt die Shift-Hilfe aller Teil-Items, als
wäre die Umschalttaste gedrückt (`false` versteckt sie, `null` folgt wieder der Taste). Gedacht für Bildschirmfotos und
Selbsttests, die keine Taste drücken können; danach immer `forceShiftHelp(null)`.

**Neu in 1.4:** `PropwashClientApi.openConfigTab("justmoreparts:sharecode", parent)` öffnet den echten Konfigurator
direkt auf diesem Tab (oder wechselt im offenen Konfigurator dorthin); `false`, wenn es den Tab nicht gibt (Fabrik
lieferte `null`, Tippfehler) oder der Konfigurator gerade nicht öffnen darf (z. B. während des Fliegens).

### 14.3 Flugschul-Szenen
```java
PropwashClientApi.registerFlightSchoolScene(FrameShapesScene::create);
PropwashClientApi.registerFlightSchoolScene(ExpertScene::create, JmpConfig::expertScenes);   // nur sichtbar, wenn true
```
```java
public static Scene create() {
    ActorSpec.Drone deadcat = PropwashClientApi.flightSchoolDrone(deadcatBuild, true, ActorSpec.Drone.STAGE_SCALE);
    return SceneBuilder.scene("justmoreparts:frames")
            .icon("justmoreparts:deadcat_5").items("justmoreparts:xclass_13")
            .view(2.8F).camera(new ScenePos(0.8F, 1.3F, 0.0F), 20.0F, 24.0F, 1.0F)
            .step("deadcat", 120.0F, s -> {
                s.at(0.0F).floor(-3, -2, 4, 2, 0, BlockSpec.of("minecraft:gray_concrete"), 1.2F, 10.0F);
                s.at(14.0F).spawn("deadcat", deadcat, new ScenePos(-1.2F, 1.0F, 0.2F), 200.0F, Cue.Appear.POP, 10.0F);
                s.at(34.0F).callout("deadcat", Anchor.on("deadcat", 0.0F, 0.2F, 0.0F), Cue.Side.ABOVE, 0.0F);
            })
            .build();
}
```
- Addon-Szenen brauchen eine Id mit eigenem Namensraum (`justmoreparts:frames`), sonst wirft die Anmeldung. Texte unter
  `justmoreparts.guide.scene.frames.…` (Titel ≤ 38, Beschreibung ≤ 64, Schritttext ≤ 150, Hinweis ≤ 90 Zeichen).
- Drohnen-Akteure aus API-Bauplänen baut `flightSchoolDrone(spec, zusammengebaut, maßstab)`; fremde Teile sind erlaubt.
- `openFlightSchoolScene(id, parent)` öffnet eine Szene direkt, z. B. aus einem „?“-Knopf.
- Hinweise (`callout`) setzt Propwash einmal und lässt sie dann stehen; nur die Zeigelinie folgt dem Anker. Ein Hinweis mit
  Dauer 0 gilt bis zum Schrittende und blendet dort aus. Die bis zum Schluss offenen Hinweise des **letzten** Schritts sind
  das Schlussbild bei „Fertig“ – was dort nicht mehr stehen soll, bekommt eine Dauer. Ein neuerer Hinweis am selben Anker
  und auf derselben Seite löst den älteren ab (z. B. eine Zusammenfassung mit Namensschildchen am Ende ersetzt die
  ausführliche Erklärung desselben Objekts); auf verschiedenen Seiten bleiben beide stehen (seit 0.5.0).
- Szenen-DSL (`guide.scene.*`) und Regeln: [`FOUNDATION.md`](../FOUNDATION.md) §15 „Flugschule“.

#### Items mit Szenen verknüpfen (API 1.3)
Hält der Spieler im Inventar die Flugschul-Taste (Standard **M**) über einem Item, öffnet Propwash dessen Szene, bei
mehreren die gefilterte Übersicht. Das gilt für Items aus **jedem** Namensraum. Ohne eigenes Zutun bekommen die
Teil-Items eines Addons:

| Teil-Art | Propwash-Szenen automatisch |
|---|---|
| jedes Teil | `FlightSchoolScenes.DRONE_BUILD` (Werkbank), `SURVIVAL` (Herstellen, Truhen) |
| Akku | zusätzlich `BATTERY` (Tauschen, Ladegerät) |
| Video | zusätzlich `VIDEO` (Videosysteme und Brillen) |
| Stack | zusätzlich `CONFIGURATOR` (Rates, PID, Modi) |
| Prop | zusätzlich `REPAIR` (Schäden, Props tauschen) |

Weitere Szenen — Propwashs eigene oder die des Addons — verknüpft das Addon in der Client-Init:

```java
PropwashClientApi.linkFlightSchoolParts("justmoreparts:frames", "justmoreparts", PartKind.FRAME);   // alle eigenen Rahmen
PropwashClientApi.linkFlightSchoolParts(FlightSchoolScenes.REPAIR, "justmoreparts", PartKind.MOTOR, PartKind.FRAME);
PropwashClientApi.linkFlightSchoolItems(FlightSchoolScenes.BIND, "meinmod:goggles_hd");                // einzelne Items
List<String> scenes = PropwashClientApi.flightSchoolScenes(stack);   // ["propwash:drone_build", "propwash:repair", …]
PropwashClientApi.openFlightSchool(stack, parent);                    // wie die Halten-Taste; false ohne Szene
```
- `FlightSchoolScenes` nennt die Ids aller Propwash-Szenen (`DRONE_BUILD`, `BIND`, `FPV_LOS`, `BATTERY`, `RACE`, `TRICKS` (seit API 1.5), `VIDEO`,
  `ACTION_CAM`, `GRIPPER` (seit API 1.5), `CONFIGURATOR`, `RESCUE`, `HOLD`, `RECOVERY`, `REPAIR`, `SURVIVAL`; Liste `ALL` in
  Anzeigereihenfolge).
- `linkFlightSchoolParts(szene, namensraum, arten…)` gilt für jedes Teil-Item des Namensraums mit einer der Arten, auch für
  Teile, die erst später angemeldet werden; ohne Arten für alle Teile des Namensraums. `linkFlightSchoolItems(szene,
  ids…)` nimmt beliebige Item-Ids, auch Nicht-Teile.
- Verknüpfungen wirken nur ergänzend und dürfen vor der Szene angemeldet werden; eine unsichtbare Szene (Sichtbarkeit
  `false`) erscheint auch über Verknüpfungen nicht. Ungültige Szenen-Ids, Item-Ids oder Namensräume werfen
  `IllegalArgumentException`.
- `flightSchoolScenes(stack)` liefert die Ids genau der Szenen, die die Halten-Taste anbietet (Propwash-Szenen mit
  `propwash:`), in Anzeigereihenfolge — gedacht für Selbsttests („jedes Item öffnet mindestens eine Szene“).
- Wer diese Methoden benutzt, verlangt `PropwashApi.requireVersion(modId, "1.3")`.

### 14.4 Theme-Pflicht
Jedes Fenster eines Addons, das sich in Propwash einklinkt, benutzt das Propwash-Theme: `Screen` +
Marker-Interface `ui.client.theme.ThemedScreen`, Zeichnen mit `ThemeDraw` (`backdrop`, `window`, `brandedHeader`,
`footerBar`, `previewStage`, `sectionTitle`, …), Text mit `ThemeText`, Farben aus `Palette`, Maße aus `Metrics`,
Knöpfe `ui.client.widget.FlatButton`, Listen `ScrollPanel`, Regler `ui.client.config.StepSlider` (**seit 1.5 tippbar**;
zeigt die Beschriftung nicht den Rohwert, `percent()` bzw. `display(faktor, versatz)` aufrufen, siehe
`docs/api/changes.md` „Konfigurator-Tabs“), Textfelder
`ui.client.widget.TextField` (**neu in 1.4**: ein `EditBox` im Propwash-Stil, auch außerhalb eines `ThemedScreen`;
`hint(text)` setzt den Platzhalter in der gedämpften Theme-Farbe). Diese Klassen sind für Addons freigegeben und werden
nur ergänzt (Abschnitt 16). Vorbild im Code: das Beispiel-Fenster
`dev.example.client.ExampleImportScreen`.

---

## 15. Events (`api.event.PropwashEvents`)

| Event | Thread | Wann |
|---|---|---|
| `PART_REGISTERED(definition)` | Init-Thread des anmeldenden Mods | nach jeder fremden Anmeldung (Java und JSON) |
| `REGISTRY_FROZEN(registry)` | Init-/Hauptthread | genau einmal beim Einfrieren |
| `DRONE_ASSEMBLED(player, drone, data)` | Server | Drohne aus dem Werkbank-Ausgang genommen |
| `DRONE_DISASSEMBLED(player, data)` | Server | Drohne in der Werkbank zerlegt |
| `BUILD_IMPORTED(player, request, result)` | Server | nach jeder Import-Anfrage, auch abgelehnten |
| `ESC_BURNED(player, drone, data, cells)` (**neu in 1.4**) | Server | der ESC einer geflogenen Drohne ist durchgebrannt, weil der Akku mehr Zellen hat, als der Stack verträgt — genau einmal, wenn der Stack am Server kaputtgeht (nur mit der Welt-Regel `propwash:crash_damage`, bis 0.4.0 Server-Regel `partWear`); `drone` ist eine Kopie der Drohne als Item, `cells` die Zellzahl |

Listener mit `PropwashEvents.X.register(…)` in der offenen Phase anmelden. `fire` ist intern.

---

## 16. Stabilität und Versionierung

| Stabil (API 1.x) | |
|---|---|
| Fassade | `api.PropwashApi`, `api.ApiVersion`, `api.client.PropwashClientApi` |
| Bauteil-Modell | `api.part.*` |
| Baupläne und Import | `api.build.*` |
| Items und Kreativ-Tabs | `api.registry.PartItems`, `api.registry.CreativeEntries`, `api.registry.ExtensionTab`, `api.registry.TabSection` |
| Events | `api.event.PropwashEvents`, `PropwashEvent#register` |
| Client-Erweiterungen | `api.client.*` |
| freigegebene interne Typen | `ui.client.config.ConfigTab`, `ConfigTabHost`, `StepSlider`; `ui.client.theme.Palette`, `Metrics`, `ThemeText`, `ThemeDraw`, `ThemedWidgets`, `ThemedScreen`; `ui.client.widget.FlatButton`, `ScrollPanel`; `guide.scene.*` |

- **SemVer**: Hauptversion bei jeder inkompatiblen Änderung, Nebenversion bei Ergänzungen, Patch bei Fehlerbehebungen.
- **Records (Regel seit 1.4):** Definitionen (`FrameDefinition`, `StackDefinition`, `MotorDefinition`, `PropDefinition`,
  `BatteryDefinition`, `VideoDefinition`, `AccessoryDefinition`, `ActionCamDefinition`, `AirframeGeometry`, `FrameAero`)
  nur über Builder und `with…` bauen — ihre Konstruktoren bekommen in Nebenversionen neue Felder. Alle übrigen Records
  sind **Wertrecords** mit stabilem kanonischem Konstruktor (`ApiVersion`, `Vec3Mm`, `MotorArm`, `PackSize`, `AnchorSpec`,
  `MassSplit`, `BatteryFitRule`, `DuctSpec`, `MotorThermal`, `CellChemistry`, `RecycleYield`, `PartVisual`, `PartBasics`,
  `BuildSpec`, `PaintSpec`, `TuneSpec`, `DroneData`, `DronePreset`, `ImportRequest`, `ImportResult`, `BuildCheck`,
  `BuildStats`, `CameraView`, `CameraView.PropDisc`, `OsdLayoutSpec`): bekommen sie eine Komponente, bleibt der alte
  Konstruktor zusätzlich erhalten (so wie bei `BuildStats` und `PartBasics`). Fabriken und Builder (`MotorThermal.builder()`,
  `ImportRequest.onHeldDrone`, `DroneData.of`, …) sind trotzdem der bequemere Weg.
- Enums (`PartKind`, `FrameRole`, `FrameShape`, `PropMaterial`, `TuneGroup`, `ImportStatus`, `ImportLayer`,
  `ImportTarget`, …) bekommen in Nebenversionen neue Konstanten **am Ende** — `switch` immer mit `default`.
- Interfaces bekommen neue Methoden nur mit `default`-Implementierung.
- JSON `"format": 1`: neue optionale Felder in Nebenversionen.
- Veraltetes wird mindestens eine Nebenversion vorher angekündigt und erst in der nächsten Hauptversion entfernt.
- **Alles andere ist intern** (`build.*`, `core.*`, `content.*`, `parts.*`, `apiimpl.*`, `sim.*`, …) und darf sich in
  jeder Version ändern.

API 1.0 ergänzt gegenüber dem ersten Vertragsstand: `PropwashClientApi.registerFlightSchoolScene(szene, sichtbar)`,
`flightSchoolDrone(…)`, `openFlightSchoolScene(…)`, `ImportLayer#translationKey()`, `TuneGroup#translationKey()`,
`BuildStats.warningTranslationKey(…)`.

API 1.1 (in Propwash 0.4.0): `BuildAccess#cameraView(BuildSpec)` mit `api.build.CameraView` und `CameraView.PropDisc`
(Default-Methode, liefert bei fremden Implementierungen leer). Addons, die sie benutzen, prüfen
`PropwashApi.requireVersion(modId, "1.1")` und hängen von `propwash >= 0.4.0` ab.

API 1.2 (in Propwash 0.4.0) ergänzt außerdem: `VideoDefinition#powerW()` (JSON `power_w`, Leistungsaufnahme des Videosystems
in W), `CellChemistry#usableFraction()` (JSON `usable_fraction`) mit `effectiveUsableFraction()` und
`landingCellVoltage()`, und in `BuildStats` die Flugzeiten gemischt/aggressiv, Ströme, Reisegeschwindigkeit,
Landespannung, Grundlast und Motortemperaturen (alter Konstruktor bleibt). Eigener Kreativ-Tab je Addon — `PropwashApi.extensionTab(ns)`, `PropwashApi.extensionTabs()`,
`api.registry.ExtensionTab` (`icon`, `order`, `add`, `collect`, `contents`, …) und `api.registry.TabSection`. Teile und
Presets eines Addons stehen seitdem in dessen Tab statt im Propwash-Tab.

API 1.3 (in Propwash 0.4.0): Flugschul-Verknüpfungen — `PropwashClientApi.linkFlightSchoolItems(…)`,
`linkFlightSchoolParts(…)`, `flightSchoolScenes(stack)`, `openFlightSchool(stack, parent)` und die Konstanten
`api.client.FlightSchoolScenes`. Die Halten-Taste der Flugschule wirkt seitdem auf Items jedes Namensraums, Akkus,
Videos, Stacks und Props von Addons bekommen die passende Propwash-Szene automatisch (Abschnitt 14.3).

API 1.4 (in Propwash 0.4.0): Truhen-Beute ohne Loader-Code — `PropwashApi.loot()` mit `api.registry.LootInjections`
(`inject(ziel, addonTabelle)`, `tablesFor(ziel)`), Abschnitt 8. Außerdem `MotorThermal.builder()`;
`PartBasics#rarity()`/`withRarity(Rarity)` und JSON `rarity`; Tooltip-Zeile Steifigkeit; `BuildStats#propwashNoise()`,
`fullThrottleCellVoltage()`, `rollAuthorityRadS2()`, `pitchAuthorityRadS2()`, `yawAuthorityRadS2()` und
`BuildStats.WARNINGS`; `PropwashEvents.ESC_BURNED`; `BuildAccess#partCondition(…)` und `paintCost(…)`;
`PartItems#stack(id, charge)` (Komponente `propwash:battery_charge` stabil); `ImportLayer.NAME`, `ImportTarget.NEW_DRONE`,
`ImportRequest.onHeldDrone(…)`/`createDrone(…)`, `PropwashClientApi.sendCreateDrone(…)`; `PartRegistry#toJson(…)`/
`fromJson(…)`/`chemistryFromJson(…)`; `PropwashApi.text(…)`; den Katalog der Tuning-Schlüssel und die Stabilitätsregel
für Wertrecords. Netzwerk-Protokoll 8: Client und Server müssen beide 0.4.0 mit API 1.4 haben (sonst trennt der
Handshake mit klarer Meldung). Weitere Ergänzungen von 1.4 sind in den Abschnitten mit „Neu in 1.4“ markiert.

Ebenfalls in API 1.4: Kamera wie in echt. Das FPV-Sichtfeld mit analoger und digitaler Brille kommt von
`camera_fov_deg` des Videomoduls (horizontal; Datenblätter nennen meist das diagonale Sichtfeld, horizontal ≈ 0,8 ×
diagonal bei 4:3 und 0,85 × bei 16:9) – ohne Angabe der alte Standard des Systems. Der Kamerawinkel gehört zur Drohne
(`TuneSpec.CAMERA_UPTILT` im Tuning, im Wartungsmenü einstellbar) und bleibt im Bereich des Kamerahalters der
Rahmenklasse: Whoop 10–35°, Toothpick 10–40°, Cinewhoop 0–35°, Freestyle 0–50°, Race 15–70°, Long Range 0–40°,
Cinelifter 0–30°, X-Class 0–50°, immer einschließlich `camera.tilt_deg` des Rahmens. Neu: `CameraView#uptiltRange()`,
`BuildAccess#cameraView(spec, uptiltDeg)`, `BuildAccess#uptiltRange(frame)`. Imports, die den Winkel mitnehmen sollen,
nennen `TuneGroup.CAMERA` in ihren Tuning-Gruppen (zum Beispiel ein Sharecode); fehlt der Winkel, gilt danach der
Standard des Rahmens.

---

## 17. Mehrspieler und fehlende Addons

- Beim Verbinden vergleicht Propwash zuerst Netzprotokoll und Addon-Liste, dann die Teile-Listen von Server und Client
  (Ids und ein kurzer Hash je Definition) — in der Konfigurationsphase **vor** dem Registerabgleich von Fabric und
  NeoForge. Weichen die Teile ab, trennt der Server, und der Spieler sieht die Übersicht je Addon (Abschnitt 1.3) mit
  „Details kopieren“; das Server-Log enthält dieselbe Zusammenfassung. Das Flugmodell läuft auf dem Client und muss
  dieselben Daten sehen.
- Eigene Netzwerk-Kanäle eines Addons prüft NeoForge vor Propwash; fehlt dort ein Pflicht-Kanal, zeigt NeoForge seine
  eigene Liste (mit dem `displayName` des Addons).
- Wird ein Addon aus einer Welt entfernt, bleiben Drohnen mit seinen Teilen erhalten: Propwash ersetzt fehlende Teile
  durch **Platzhalter** mit der unbekannten Id (Problem `missing_part`, nicht scharfschaltbar, Tooltip „Unbekanntes
  Bauteil“) und speichert die Id weiter. Kommt das Addon zurück, ist die Drohne wieder vollständig. Items der Teile
  verschwinden wie bei jedem Mod.
- Speicherschlüssel: Teile im Namensraum `propwash` schreiben nur den Pfad, alle anderen die volle Id.

### 17.1 Optional auf Servern: Addon nur beim Spieler

Seit 0.5.0 darf ein Spieler mit deinem Addon einem Server **ohne** dein Addon beitreten (Ablauf und Regel:
`docs/design/client-only-addons.md`). Propwash schaltet dein Addon bei ihm für die Sitzung ab und zeigt einen Hinweis
(„*Dein Addon* ist auf diesem Server nicht installiert und für diese Sitzung inaktiv.“); beim Verlassen ist alles wieder
da. Hat der Server dein Addon und der Spieler nicht, bleibt es Pflicht, ebenso andere Teiledaten auf beiden Seiten.

**Was Propwash für dich erledigt**, wenn du nur die API benutzt und alle Ids im Namensraum deiner Mod-Id anlegst:

| Beitrag | wird in der Sitzung … |
|---|---|
| Teile und Zellchemien (`parts().register`, `loadBundled`) | aus allen Listen genommen; Baupläne mit ihnen zeigen Platzhalter wie auf dem Server |
| Teil-Items (`partItems()`), eigener Kreativ-Tab (`extensionTab`), Kreativ-Einträge | ausgeblendet (Tab und Suche); Kreativ-Pakete mit diesen Items gehen nicht an den Server |
| Presets (`registerPreset`) | ausgeblendet, ebenso fremde Presets mit deinen Teilen |
| Werkbank-Erweiterungen (`WorkbenchExtension#id` im Namensraum deiner Mod-Id) | ausgeblendet |
| Konfigurator-Tabs mit Id `<modid>:…` | ausgeblendet |
| Flugschul-Szenen `<modid>:…` | ausgeblendet, `openFlightSchoolScene` liefert `false` |
| `PropwashClientApi.sendImport`/`sendCreateDrone` mit deinen Teilen | nicht gesendet, Ergebnis sofort `UNKNOWN_PARTS` mit den Ids |

Inaktiv wird ein Addon nur, wenn es **Teile** bringt (oder Teile in einem Namensraum, den der Server nicht kennt). Ein
Addon ohne Teile (z. B. ein HUD) gilt als reines Client-Addon und bleibt aktiv – dann gelten die Regeln unten erst
recht.

**Regeln, damit dein Addon auf Servern optional sein kann:**

1. **Ids im eigenen Namensraum:** Teile, Items, Presets, Werkbank-Erweiterungen, Konfigurator-Tabs (`"<modid>:…"`) und
   Szenen (`"<modid>:…"`). Nur daran erkennt Propwash, was zu deinem Addon gehört.
2. **Eigene Items, Blöcke, Entities nur, wenn es sein muss.** Fabric und NeoForge lassen zusätzliche Registereinträge beim
   Spieler zu, der Server kennt sie aber nicht: Fabric gibt ihnen Nummern, die der Server nicht lesen kann, NeoForge gar
   keine Netz-Nummer. Ein solches Item darf nie an den Server gehen. Teil-Items über `partItems()` schützt Propwash;
   direkt registrierte Items nimm selbst aus Tabs und Menüs, solange `PropwashClientApi.isAddonActive("<modid>")`
   `false` ist (z. B. im `displayItems` deines Tabs prüfen).
3. **Netzwerk-Kanäle optional anmelden und vor dem Senden prüfen.** NeoForge verweigert eine Verbindung, wenn auf einer
   Seite ein Pflicht-Kanal fehlt – in beide Richtungen. Melde deine Kanäle deshalb optional an und sende nur, wenn die
   Gegenseite sie kennt:

   ```java
   // NeoForge (RegisterPayloadHandlersEvent)
   PayloadRegistrar registrar = event.registrar("1").optional();
   registrar.playToServer(MyPayload.TYPE, MyPayload.CODEC, MyHandlers::onServer);
   // vor dem Senden auf dem Client
   if (Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().getConnection().hasChannel(MyPayload.TYPE)) { … }

   // Fabric: Kanäle trennen nie, aber vor dem Senden prüfen
   if (ClientPlayNetworking.canSend(MyPayload.TYPE)) { … }
   ```

   Just More Parts und das Beispiel-Addon melden keine eigenen Kanäle an; Importe laufen über Propwashs Kanäle.
4. **Nichts über deine Teile selbst senden** – nutze `sendImport`/`sendCreateDrone`, Propwash prüft dort.
5. **Server-Seite gibt es nur mit dem Addon auf dem Server:** Befehle, Beute, Rezepte und Events auf dem Server laufen
   ohnehin nur dort, wo es installiert ist.
6. **Testen** mit dem Beitrittstest aus zwei Prozessen (`docs/testing.md`): Server ohne, Spieler mit deinem Addon in
   `runs/smoke-client/mods`; erwartet `joined` und `session_inactive=<modid>`.

---

## 18. Das Beispiel-Addon (`devtest`)

Im Quell-Set `common/src/devtest` (nicht im Release-Jar) liegt ein vollständiges Addon im Namensraum
`propwash_example`, das **nur die API** benutzt:

| Datei | Inhalt |
|---|---|
| `dev/example/ExampleAddon.java` | Versionsprüfung, Events, drei Teile per Java (Motor 2806,5, Carbon-Prop 13×7, Stretched-X-Race-Rahmen mit eigenen Armen), `loadBundled`, Items, Preset „X-Class 13″“, eigener Kreativ-Tab „Propwash: API-Beispiel“ (Symbol X-Class 13″, eigene Reihenfolge) mit einem Eintrag |
| `resources/data/propwash_example/propwash_parts/…` | fünf Teile per JSON (Deadcat 5″, X-Class 13″, Stack 3–8S, Motor 4214 mit Hitzegrenzen, 8S Li-Ion) und die Zellchemie LiHV+ 4,45 V |
| `resources/assets/propwash_example/…` | Item-Symbole (Sonder-Renderer `propwash:part` bzw. vorhandene Propwash-Modelle) und Sprachdateien de/en |
| `dev/example/client/ExampleAddonClient.java` | Werkbank-Knopf „Beispiel“ mit Kind-Fenster (Vorschau, Teile-Check, Import „Ganzer Bauplan“/„Nur Lackierung“), Konfigurator-Tab „API-Beispiel“ (OSD-Layout über die Zwischenablage, Rates und Gaskurve auf die gehaltene Drohne), Flugschul-Szene `propwash_example:frames` mit allen Beispiel-Rahmen verknüpft, Beispiel-Motoren zusätzlich mit `FlightSchoolScenes.REPAIR` |

Weil die Beispiel-Ressourcen zum Propwash-Jar der Entwicklungsumgebung gehören, lädt das Beispiel seine JSON-Teile mit
`loadBundled("propwash")`; ein echtes Addon ruft `loadBundled("<eigene mod-id>")`. Knopf, Tab, Szene und
Kreativ-Eintrag sind in normalen Entwicklungs-Runs sichtbar und im Selbsttest nur während der eigenen Schritte
(`ExampleAddon.showcase`), damit andere Selbsttests und Bildschirmfotos unverändert bleiben; außerhalb davon ist der
Beispiel-Tab leer und damit ausgeblendet.

Selbsttest `apiimpl.client.ApiSelfTests` (Schritte „api: …“): Versionen, Register, Items, Namen und Events der
Beispiel-Teile; `check`/`analyze`/`defaultTune`; `createDrone` → `read` → `withTune`/`withPaint`; Presets und
Kreativ-Einträge im eigenen Tab (nicht im Propwash-Tab); eigener Tab: Id, Titel „Propwash: API-Beispiel“, Symbol,
Reihenfolge, Ablehnung von `propwash`/ungültigen/zu späten Namensräumen und (NeoForge, echte Tab-Sortierung) Platz
direkt hinter dem Propwash-Tab; OSD-Rundreise; Import der Deadcat in die Werkbank, Export → Import derselben Drohne und
`DRONE_ASSEMBLED`; Tuning nur Rates und Gaskurve sowie Lack auf die gehaltene Drohne; Ratenbegrenzung, fehlendes Ziel,
unbekannte Teile; Werkbank-Knopf mit Kind-Fenster und Import, Konfigurator-Tab, Flugschul-Szene Schritt für Schritt;
Flugschul-Szenen jedes Beispiel-Teils (`flightSchoolScenes`), Halten-Taste über dem Beispiel-Prop (gefilterte Übersicht)
und `openFlightSchool` für den Beispiel-Akku.
Bildschirmfotos im Rundgang: `api_workbench_extension.png`, `api_workbench_import.png`, `api_config_tab.png`.

---

## 19. Checkliste und Fehlersuche

- [ ] `requireVersion` beim Start (`"1.1"`, wenn der eigene Tab eingerichtet wird), Abhängigkeit `propwash >= 0.4.0` in
  beiden Loader-Metadaten, `side = "BOTH"`.
- [ ] Tab-Name `itemGroup.<ns>` in de und en, Symbol und Reihenfolge mit `extensionTab(ns)` gesetzt.
- [ ] Teile und Items in der **gemeinsamen** Init; Client-Erweiterungen in der Client-Init.
- [ ] Jede Id: Pfad nur `[a-z0-9_.-]`, eindeutig über alle Kategorien, Namensraum ≠ `propwash`.
- [ ] Item-Symbol `assets/<ns>/items/<pfad>.json` und Name `part.<ns>.<pfad>` für jedes Teil.
- [ ] Neue Halterungen tauchen bei mindestens einem Rahmen **und** einem Teil auf (sonst Warnung beim Einfrieren).
- [ ] Szenen-Ids mit Namensraum, Texte in beiden Sprachen, deutsch mit Umlauten und ß.
- [ ] Jedes Item öffnet mit der Flugschul-Taste mindestens eine passende Szene (`flightSchoolScenes(stack)` im Selbsttest).
- [ ] Sharecodes: Länge begrenzen, nie `VTX`, Import-Ergebnisse anzeigen, `BUSY` erneut versuchen lassen.

| Meldung / Verhalten | Ursache |
|---|---|
| `IllegalStateException … braucht die Propwash-API …` | installierte Propwash-Version zu alt oder neue Hauptversion |
| `IllegalStateException` beim `register` | Anmeldung nach dem Einfrieren (z. B. aus einem Event oder der Client-Init) |
| Start bricht mit Liste von JSON-Dateien ab | Pflichtfeld fehlt oder Wert ungültig; Meldung nennt Datei und Feld |
| Verbindung getrennt: Übersicht „Add-ons passen nicht zum Server“ | Addon fehlt auf einer Seite oder hat andere Werte; die Übersicht nennt je Addon, was zu tun ist |
| Addon heißt in der Übersicht nur wie seine Mod-Id, ohne Link | `name`/`displayName` bzw. `contact.homepage`/`displayURL` fehlt in den Metadaten (Abschnitt 1.3) |
| Teile stehen unter „Unbekannte Teile“ | Namensraum ist weder die Mod-Id noch kommen die Teile über `loadBundled` des Addons |
| Teil erscheint nicht in der Werkbank | kein Item angemeldet (`registerItems` vergessen) |
| Tab heißt „Propwash: <Mod-Name>“ bzw. „Propwash: <ns>“ statt des gewünschten Namens | Schlüssel `itemGroup.<ns>` fehlt in der Sprachdatei |
| Addon-Teile stehen im Propwash-Tab | eigener Tab nicht angelegt (Warnung im Log) oder Namensraum erst nach dem Einfrieren |
| Eigener Tab fehlt | alle Teile `creative: false`, keine gelisteten Presets und keine `add(…)`-Einträge — leere Tabs blendet Minecraft aus |
| Import liefert `NO_TARGET` | Werkbank nicht offen (bzw. andere Container-Id) oder keine Drohne in der Haupthand |
| Import liefert `BUSY` | mehr als 2 Anfragen pro Sekunde |
| Szene wirft bei der Anmeldung | Id ohne eigenen Namensraum |
