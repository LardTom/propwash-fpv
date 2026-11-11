# Addons für Propwash FPV — Schritt für Schritt (Parts-API 1.6)

Dieses Handbuch zeigt, wie ein eigenes Mod Propwash FPV erweitert, **ohne Propwash zu ändern**: neue Bauteile aller
Kategorien (auch neue Rahmenformen wie Deadcat, Stretched-X, H-Frame, Ducts oder X-Class 10–13″), neue Zellchemien,
Presets, ein eigener Kreativ-Tab, Werkbank-Knöpfe mit eigenen Fenstern, Konfigurator-Tabs, Flugschul-Szenen und ein
Sharecode-System (Baupläne, Lackierung, Tuning und OSD-Layout teilen). Das erste Addon dieser Art ist „Propwash: Just
More Parts“; alle Beispiele benutzen deshalb den Namensraum `justmoreparts`. Seit API 1.6 (Propwash 0.5.2) kommen
Zubehör mit Verhalten und Inventar, Zubehör-Aktionen auf Schaltern und Tasten, Stöße und Kräfte im Flugmodell, Events für
Treffer und Schaden, Drohnen-Abfragen, Flughilfen, Gimbal-Kameras und HUD-Elemente dazu (Abschnitt 20).

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
| Items, Tooltips, Werkbank, Analyse, Flugmodell, Rendering, Flugschule, Speichern „geschenkt“ | neue Lack-Plätze, weitere Zubehör-Halterungen als oben, unten und vorne (seit 1.6) |
| Zubehör mit Inventar, Aktionen, Rückstoß, Kräften, Flughilfen, Gimbal und HUD (seit 1.6, Abschnitt 20) | eigene Funken-Items und -Modelle |
| Presets und ein eigener Kreativ-Tab „Propwash: <Name>“ direkt hinter dem Propwash-Tab | eigene Item-Klassen für Teile (Propwash stellt die Teil-Items) |
| Baupläne lesen, Drohnen-Items erzeugen, Tuning und Lack setzen | Einträge in Propwashs eigene Beutetabellen (eigene Tabellen hängt `PropwashApi.loot()` in jede Vanilla-Truhe) |
| server-geprüfter Import in die Werkbank oder auf die gehaltene Drohne | Zubehör auf der Use-Taste: der Rechtsklick beendet im Flug immer das FPV, Zubehör löst über die Zubehör-Aktionen aus |
| Werkbank-Knöpfe, Kind-Fenster, Konfigurator-Tabs, Flugschul-Szenen, Events | freie Grafik im OSD-Raster (OSD-Elemente sind Text; Grafik bleibt ein `HudElement`) |
| eigene Schalter, Taster und analoge Kanäle im Tab „Modi“, Namen der Zubehör-Aktionen (seit 1.7, Abschnitt 21.2–21.3) | mehr als ein Operator je Drohne |
| OSD-Textelemente im OSD-Editor und Warnungen im Warnfeld (seit 1.7, 21.4) | Add-on-Welt-Regeln in Zonen überschreiben; Schwerkraft, Wind oder Luftdichte je Drohne (dafür Zonen, Welt-Regeln und `ForceProvider`) |
| Kollisionsregeln: durch Blöcke, Wasser, Entities und Ranken fliegen, Antwort berührter Flächen ändern (seit 1.7, 21.5) | Add-on-Daten in Sharecodes und Bauplänen (sie gehören zur einzelnen Drohne) |
| Werfen wie ein Dispenser, Inhalt an der Drohne sichtbar, Explosions- und Windstöße, alle Drohnen eines Levels, Kamerablick am Server (seit 1.7, 21.6–21.9) | autonomer Flug außerhalb der Funkreichweite |
| eigene Welt-Regeln im Tab „Welt-Regeln“, auch in den Voreinstellungen (seit 1.7, 21.10) | Lautstärke einer Drohne am Server, Flugmodell-Prüfstand als API, Rahmen-Rolle `CAMERA` |
| Daten an Drohne und Drohnen-Item, Knöpfe in der Wartung, Pilotenwechsel (seit 1.7, 21.11 und 21.15) | Zeiten in Bestenlisten schreiben (sie entstehen nur durch echtes Fliegen) |
| autonomer Flug mit eigener Flughilfe, zweiter Operator am Gimbal, Gimbal in Aufnahme, Modell und Brennweite (seit 1.7, 21.12–21.14) | ein Kamera-Haken für VR |
| Sharecodes lesen, schreiben und umrechnen, Teile-Tabelle für kurze Codes (seit 1.7, Abschnitt 13) | |
| Renndaten lesen: Kurse, Bestenlisten und Ereignisse nach Kurs-ID, für Server und Bots (seit 1.7, 21.17) | |

Seit 1.7 brauchen Add-ons für Schalter, OSD, Kollision und autonomen Flug keine Mixins in Propwash-Interna mehr; der
Namensraum `propwash` ist reserviert (21.18). Wer von Mixins umsteigt: Abschnitt 21.19.

Garantie: Alle eingebauten Teile, Items, Speicherstände, Rezepte, Beutetabellen, Presets und das Flugverhalten bleiben
genau wie in 0.3.2. Ein Addon kann eingebaute Teile weder ändern noch entfernen.

---

## 1. Projekt aufsetzen

### 1.1 Gradle
Propwash veröffentlicht drei Maven-Artefakte (Gruppe `de.lardtom`, je mit Quellen- und Javadoc-Jar):

| Artefakt | Inhalt | Verwendung im Addon |
|---|---|---|
| `de.lardtom:propwash-common-26.3:0.5.2` | gemeinsamer Code (ganze API, Mojang-Namen) | `compileOnly` im Modul `common` |
| `de.lardtom:propwash-fabric-26.3:0.5.2` | Fabric-Mod-Jar | `implementation` im Modul `fabric` (Dev-Laufzeit) |
| `de.lardtom:propwash-neoforge-26.3:0.5.2` | NeoForge-Mod-Jar | `implementation` im Modul `neoforge` (Dev-Laufzeit) |

**Lokal entwickeln (gegen einen eigenen Propwash-Stand):** im Propwash-Repository einmal
`JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew publishToMavenLocal` ausführen. Das legt alle drei Artefakte in
`~/.m2/repository/de/lardtom/` ab. Im Addon:

```groovy
repositories {
    mavenLocal()
}

// common/build.gradle
dependencies {
    compileOnly 'de.lardtom:propwash-common-26.3:0.5.2'
}

// fabric/build.gradle (Loom, 26.3 ist nicht obfuskiert: kein modImplementation nötig)
dependencies {
    implementation 'de.lardtom:propwash-fabric-26.3:0.5.2'
}

// neoforge/build.gradle (ModDevGradle)
dependencies {
    implementation 'de.lardtom:propwash-neoforge-26.3:0.5.2'
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
    compileOnly 'maven.modrinth:propwash-fpv:0.5.2+neoforge'       // common
    implementation 'maven.modrinth:propwash-fpv:0.5.2+fabric'      // fabric
    implementation 'maven.modrinth:propwash-fpv:0.5.2+neoforge'    // neoforge
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

### 1.3.1 Add-ons, die Spieler nicht installieren müssen

Manche Add-ons laufen nur auf dem Server (Befehle, Admin-Werkzeuge, Spuren aus Vanilla-Partikeln) oder haben einen
optionalen Client-Teil. Sie gehören nicht unter „Propwash-Add-ons auf diesem Server“, wenn ein Spieler abgewiesen wird,
und niemand soll sie installieren müssen. Propwash lässt ein Add-on aus der Beitritts-Übersicht weg, wenn es angibt,
dass der Client es nicht braucht:

| | `fabric.mod.json` | `neoforge.mods.toml` |
|---|---|---|
| nur auf dem Server | `"environment": "server"` (mehr braucht es nicht) | – |
| beim Client optional | `"custom": { "propwash:client": "optional" }` | `[modproperties.<modid>]` mit `"propwash:client" = "optional"` |

```json
"environment": "*",
"custom": { "propwash:client": "optional" }
```

```toml
[modproperties.meinaddon]
"propwash:client" = "optional"
```

Ein Add-on, das Teile oder Akku-Chemien anmeldet, bleibt immer in der Übersicht, egal was es angibt: Teile müssen auf
beiden Seiten passen. Die Angabe ändert nur die Anzeige, nichts am Netz.

### 1.4 API-Version prüfen
Die API hat eine eigene Version (`PropwashApi.API_VERSION`, SemVer), unabhängig von der Mod-Version: Propwash 0.6.0 bringt
**1.8.0** (Propwash 0.6.0; 0.5.2 brachte 1.6.0, 0.5.0 und 0.5.1 brachten 1.5.0, 0.4.0 brachte 1.4.0). Die Nebenversionen zählen die Ergänzungen: 1.0 Grundumfang, 1.1 Kamera-Aufbau `cameraView`, 1.2 eigene
Kreativ-Tabs, Flugzeit-Kennzahlen in `BuildStats`, `power_w`, `usable_fraction`, 1.3 Flugschul-Verknüpfungen für Items,
1.4 Truhen-Beute ohne Loader-Code (`PropwashApi.loot()`, Abschnitt 8), Seltenheit per JSON, weitere
Analyse-Kennzahlen, ESC-Event, Teilezustand, Farbstoff-Kosten, Akkus mit Teilladung, Name beim Import, Drohne erzeugen,
JSON-Codec, Sprachtexte ohne Client, die Werkbank- und Client-Ergänzungen sowie die Kamera: Sichtfeld aus
`camera_fov_deg`, Uptilt pro Drohne mit Bereich des Kamerahalters (alle fünf sind in Propwash 0.4.0 enthalten; Details
an den Stellen mit „Neu in 1.4“), 1.5 (Propwash 0.5.0) die Szenen-Konstanten `FlightSchoolScenes.TRICKS` und `FlightSchoolScenes.GRIPPER` sowie
`BuildAccess#partCompatibility()` für die Welt-Regel „Teile müssen zusammenpassen“ und
`PropwashClientApi.isAddonActive(String)` für Addons, die nur beim Spieler installiert sind (Abschnitt 17.1), 1.6
(Propwash 0.5.2) die Leistungsstufen des Videosenders (`vtx_levels_mw`, `VideoDefinition#vtxLevelsMw()`, Abschnitt 3.4), 1.7
(Propwash 0.6.0) die Bausteine aus Abschnitt 21 und Sharecodes über Propwash (Abschnitt 13), 1.8 (ebenfalls Propwash 0.6.0,
ab `0.6.0-dev.14`) Fracht am Greifer (Abschnitt 21.23) und Zubehör einrücken (`recess_mm`, `drop_mm`, Abschnitt 3.4). Beim Start
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
        .thermal(MotorThermal.limits(135.0, 155.0))                   // Leistungsverlust ab 135 °C, starker Verschleiß ab 155 °C
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
        .coolingPerRadS(4.0E-4)      // W/K je rad/s Motordrehzahl
        .limits(140.0, 160.0)        // Leistungsverlust ab, starker Verschleiß ab (°C)
        .wearAtMax(0.001)            // Verschleiß je Sekunde bei Maximum
        .build())
```
Bedeutung seit Propwash 0.6.0 (Namen und Werte bleiben): ab `derate_start_c` verliert ein heißer Motor Leistung
(Drehmoment linear bis −10 % bei `max_temp_c`) und verschleißt, ab `max_temp_c` stark. Er regelt nicht selbst ab;
abgeregelt wird nur im ESC nach dessen Temperatur ab 140 °C, linear bis 25 % bei 155 °C (wie BLHeli_32). Die Kühlwerte
stellt man am besten nach dem 60-s-Wert des Datenblatts ein (Werkbank: Vollgas im Stand nach 60 s); der eingebaute
1404 4600 KV nutzt so `cooling_w_per_k_per_rad_s` = 1,6·10⁻⁴.

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
        .cameraWidth(14.0).cameraFov(155.0).vtxPower(700).vtxLevels(25, 100, 400, 700, 1200).build());
```
Sendeleistung (seit API 1.6): `vtx_power_mw` ist die **Nennleistung** – die Stufe, bei der `range_blocks` und `power_w`
gelten. Drohnen ohne eigene Einstellung senden mit *Auto* und regeln zwischen der niedrigsten und der höchsten Stufe. `vtx_levels_mw` (Builder `vtxLevels`) listet die Stufen, die der Spieler
wählen kann (aufsteigend, höchstens 8, mit der Nennleistung). Ohne Liste leitet Propwash Stufen bis zur Nennleistung
ab (analog 25/200/400/800/…, digital 25/100/400/700/1200); ein Teil mit `vtx_power_mw: 800` kann also 25–800 mW.
Höhere Stufen bringen 10·log10(P/P_Nenn) dB Reserve, kosten Strom und heizen den Sender auf
(`docs/design/vtx-power.md`).
Chemien vor den Akkus anmelden, die sie benutzen. Zubehör (`AccessoryDefinition`) kennt die Plätze `TOP`/`BOTTOM`,
Maße, Leistungsaufnahme, LEDs und optional eine Action-Cam (`ActionCamDefinition`, nur oben).

**Neu in 1.7 (Propwash 0.6.0) — LED-Pfad:** Ohne Angabe sitzen die LEDs eines Zubehörs gleichmäßig auf einer
geraden Leiste. Mit `led_path` (Builder `ledPath(Vec3Mm...)`) leuchten sie entlang einer eigenen Linie aus 2–256 Punkten
in mm im Koordinatensystem des Zubehörs (wie `size_mm`: Ursprung am Anbaupunkt, +X rechts, +Y oben, −Z vorn), nach
Länge gleichmäßig verteilt – ein Ring, ein Bogen, eine Leiste entlang der Arme. Alle LED-Modi laufen entlang der Linie
(der Regenbogen um den Ring). Ein geschlossener Ring wiederholt am Ende den ersten Punkt:
```json
"slot": "top", "size_mm": [44.0, 4.0, 44.0], "leds": 20,
"led_path": [[20.0, 7.0, 0.0], [0.0, 7.0, 20.0], [-20.0, 7.0, 0.0], [0.0, 7.0, -20.0], [20.0, 7.0, 0.0]]
```
Grenzen: jede Koordinate ±500 mm, Länge > 0, nur mit `leds` > 0 (höchstens 64). Ein Zubehör mit Pfad wird nicht auf die
Streifenlänge des Rahmens gestreckt; Kollision und Stirnfläche rechnen mit `size_mm`, also den Quader um den Pfad legen.
Ältere Propwash-Versionen ignorieren das Feld mit einer Warnung und zeigen den geraden Streifen. Vollständiges Beispiel:
`propwash_example:led_ring` im Beispiel-Addon; Einzelheiten `docs/api/changes.md` Abschnitt 0.6.0.

**Neu in 1.7 (Propwash 0.6.0) — gerade hängend:** Jede Halterung eines Rahmens hat eine Neigung
(`accessory_anchors` → `tilt_deg`, am 5-Zöller oben 22° und unten 30°, am Whoop unten 90°), und unten wird Zubehör in x
auf die Streifenlänge des Rahmens (`length_mm`) gestreckt. Das passt zu Action-Cams und LED-Streifen, nicht zu
Halterungen, Werfern oder Speeren, und ein Add-on kann es nicht selbst ausgleichen, weil die Werte je Rahmen anders sind.
Mit `"hangs_straight": true` (Builder `hangsStraight(true)`, lesen über `AccessoryDefinition#hangsStraight()`) sitzt das
Zubehör an jedem Rahmen ohne Neigung und in seiner eigenen Breite, wie der Greifer: Modell, Hülle, Flugmodell, LEDs,
die Lage von `ItemDisplay` und `MountPose` (`forward()` waagerecht nach vorn, `front()` mittig an der Vorderseite des
Quaders aus `size_mm`). Standard ist `false`, also das Verhalten aus 1.6; Greifer, LED-Streifen und Action-Cams bleiben
unverändert. Der Codec schreibt das Feld nur, wenn es gesetzt ist; die Konstruktoren mit 12 und 13 Argumenten bleiben
(ohne Angabe `false`). Ältere Propwash-Versionen ignorieren es mit der Warnung „unbekanntes Feld wird ignoriert“ und
neigen das Teil wie bisher. Beispiel: `propwash_example:spear_holder` in `dev/example/ExampleDispenser`.
```json
"slot": "bottom", "size_mm": [12.0, 10.0, 60.0], "hangs_straight": true
```

**Neu in 1.7 (Propwash 0.6.0) — Mündung:** `MountPose#front()` und der Abgabepunkt von
`AccessoryContext#dispense` liegen in der Mitte der Vorderfläche des Quaders aus `size_mm`. Mit `"muzzle_mm": [x, y, z]`
(Builder `muzzle(x, y, z)`, lesen über `AccessoryDefinition#muzzleMm()`) sitzt die Mündung woanders: Versatz in mm von
dieser Mitte, x rechts, y oben, −z vorn, in den Achsen des Zubehörs (mit Neigung der Halterung, ohne bei
`hangs_straight`). Erlaubt ist ein Punkt in der Box oder höchstens 20 mm außerhalb (`MAX_MUZZLE_OUTSIDE_MM`), sonst
wirft die Definition mit „liegt mehr als 20 mm außerhalb der Box“. Ohne Angabe bleibt alles wie in 1.6, der Codec
schreibt das Feld nur, wenn es gesetzt ist; die Konstruktoren mit 12, 13 und 14 Argumenten bleiben (ohne Mündung).
Ältere Propwash-Versionen ignorieren das Feld mit einer Warnung. Beispiel für eine 58 mm hohe Halterung, die oben feuert:
```json
"slot": "front", "size_mm": [62.0, 58.0, 64.0], "muzzle_mm": [0.0, 20.0, 0.0]
```

**Neu in 1.8 (Propwash 0.6.0) — einrücken:** Am Platz vorn hängt der Quader aus `size_mm` vor und unter dem
Anker, also vor der Nase. Mit `"recess_mm"` (Builder `recess(double)`, lesen über `AccessoryDefinition#recessMm()`) rückt
das Zubehör nach hinten, mit `"drop_mm"` (Builder `drop(double)`, `#dropMm()`) nach unten, beides in den Achsen des
Zubehörs. Das gilt für Modell, `ItemDisplay`, Flugmodell und `MountPose`: `center()` und `front()` folgen, `anchor()` und
die Richtungen bleiben. Das Modell zeichnet man weiter am Anker. Erlaubt sind 0 bis zur Tiefe bzw. Höhe; Standard 0 =
Verhalten wie bisher, der Codec schreibt die Felder nur, wenn sie gesetzt sind, die Konstruktoren mit 12 bis 16 Argumenten
bleiben. Ältere Propwash-Versionen ignorieren die Felder mit einer Warnung; wer `recess` oder `drop` im Builder aufruft,
verlangt `requireVersion(modId, "1.8")`. Eingerücktes Zubehör kann in den Unten-Platz
oder einen Akku unter dem Rahmen ragen; Maße und Messwerte stehen in [`../accessory-hang.md`](../accessory-hang.md) §8.
```json
"slot": "front", "size_mm": [62.0, 58.0, 64.0], "recess_mm": 58.0, "drop_mm": 1.5
```

**Neu in 1.7 (Propwash 0.6.0) — nur an bestimmten Rahmen:** Ohne Angabe passt ein Zubehör an jeden Rahmen
mit der passenden Halterung. Mit `"frames"` (Builder `frames(Identifier...)`, lesen über `AccessoryDefinition#frames()`,
ein unveränderliches, sortiertes `Set<Identifier>`) passt es nur an die genannten Rahmen, etwa ein Gimbal nur an die
Kamera-Drohnen. Volle Ids benutzen; eine Id ohne Namensraum gilt als `propwash:`. Rahmen anderer Add-ons dürfen in der
Liste stehen, auch wenn das Add-on fehlt. Eine leere Liste heißt wie ohne Angabe „jeder Rahmen“.
```json
"slot": "front", "size_mm": [40.0, 30.0, 36.0], "frames": ["propwash:cinewhoop_3", "propwash:long_range_7"]
```
Dann gilt:
- `DroneBuild`-Prüfung und `PropwashApi.builds().check(spec)` melden an einem anderen Rahmen das Problem
  `accessory_frame` (Text `propwash.build.issue.accessory_frame`). Die Werkbank baut so eine Drohne nicht, ebenso
  wenig Importe, Presets (`createDrone` wirft) und PW3-Codes. Die Welt-Regel „Teile müssen zusammenpassen“ hebt das
  Problem wie die übrigen Halterungsprobleme zur Warnung herab (Abschnitt 3.5): Ist sie aus, ist das Zubehör an jedem
  Rahmen mit passender Halterung erlaubt. Werkbank, Importe, Presets und PW3-Codes bauen die Drohne, `check(spec)`
  nennt `accessory_frame` unter `warnings()`, und die Drohne fliegt.
- Die Werkbank nimmt das Zubehör in die Halterung nicht an, solange ein anderer Rahmen im Rahmen-Slot liegt und die
  Welt-Regel an ist (auch nicht per Shift-Klick). Hält der Spieler es darüber, nennt ein Hinweis die erlaubten Rahmen.
  Der Tooltip „Passt zu“ nennt nur diese Rahmen.
- Drohnen, die vor der Einschränkung gebaut wurden, behalten das Zubehör; nichts wird gelöscht. Wie eine Drohne mit
  fehlenden Teilen zeigt sie das Problem und lässt sich bei geltender Welt-Regel nicht steuern
  (`propwash.server.deny.parts_incompatible`).
  In der Werkbank zerlegt, liegt das Zubehör wieder in seiner Halterung und kann ins Inventar.
- Der Codec schreibt das Feld nur, wenn es gesetzt ist; die Konstruktoren mit 12 bis 15 Argumenten bleiben (ohne
  Einschränkung). Ältere Propwash-Versionen ignorieren das Feld mit einer Warnung, das Zubehör passt dort überall.
  Beispiel: `propwash_example:cine_gimbal` im Beispiel-Addon (`data/propwash_example/propwash_parts/accessory/`).

### 3.5 Kompatibilität, die Propwash prüft
Nicht baubar (Probleme): `missing_part`, `motor_mount`, `prop_too_large`, `prop_too_small`, `prop_mount`,
`stack_mount`, `battery_frame`, `accessory_mount`, `accessory_frame` (seit 1.7, Zubehör mit `frames` an einem anderen
Rahmen). Warnungen: `motor_overvoltage`, `motor_undervoltage`,
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
(`{"item": "propwash:copper_coil", "count": 1}` oder `"none"`; nur für Teile ohne Crafting-Rezept, Menge bei 100 %
Zustand, siehe Abschnitt 8), `visual: {"accent": "#e8b622", "render_model": "…"}` und
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

**Neu in Propwash 0.6.0 — Akku gesteckt, Stecker ausgeblendet (optional, Ressourcen-Konvention):** Sitzt ein Akku in
der Drohne, zeichnet Propwash keine Stecker und keine Kabel. Dafür liest es die Element-Namen der Modelle: Am Akku
fehlen `connector` (+ `connector_grip`) bzw. `bt_connector`, `balance_plug` und jedes `wire`, das eines davon berührt
(Kabel zur Buchse, Balancer-Kabel); ohne Buchse bleibt der Akku, wie er ist. Ein Stack mit Stecker liefert zusätzlich
`items/drone_render/stack/<pfad>_mounted.json` (und `_mounted_paint` mit denselben Lack-Gruppen in `drone_paint.json`):
dasselbe Modell ohne die Stecker-Elemente, die beiden Stromkabel heißen `pigtail`; an der Drohne fehlen auch sie.
Akku-Modelle bleiben, wie sie sind, das einzelne Akku- und Stack-Item zeigt Stecker und Kabel weiter. Ohne `_mounted` –
oder wenn es aus einem anderen Ressourcenpaket kommt als das Grundmodell – zeigt Propwash das bisherige Bild mit beiden
Steckern. Eine `transformation` der Render-Definition (etwa ein skalierter X-Class-Rahmen) spielt keine Rolle; ein
Eintrag `power_lead` in `drone_layout.json` wird ignoriert.
Regeln und Maße: [`docs/ASSETS.md`](../ASSETS.md), „Akku gesteckt“.

**Neu in Propwash 0.6.0 — `_blur`-Modell eines Props (Ressourcen-Konvention):** Das drehende Prop zeigt im FPV-Bild
über der Scheibe die deckenden Flächen seines `_blur`-Modells als Mitte (Nabe, Mutter, Schrauben). Die Scheibe ist jedes
Element, dessen Flächen alle eine Textur mit `"force_translucent": true` tragen; alle übrigen Elemente sind die Mitte und
stehen unverändert wie im stehenden Modell (gleiche `from`/`to`, `rotation`, UV und Sprite; der Textur-Schlüssel darf
anders heißen). Was zur Mitte gehört, legt das stehende Modell fest: Ihr Radius ist der weiteste Abstand von der
Motorachse (x 8, z 8 im Modell), den seine nicht angestellten Elemente erreichen (ohne Drehung um x oder z, etwa Nabe,
Mutter, Schrauben). Jedes Element des stehenden Modells bis zu diesem Radius gehört zur Mitte und muss im `_blur`-Modell
stehen; die Blätter sind angestellt (Drehung um x oder z), reichen weiter hinaus und fehlen im `_blur`-Modell. Ein Blatt
ohne Anstellwinkel zählt als Mitte. Element-Namen (`blade`, `blade_tip`, `blur_disc` …) spielen keine Rolle. Ohne `_blur`-Modell zeichnet
Propwash eine Ersatzform. Regeln: [`docs/ASSETS.md`](../ASSETS.md), „Props im FPV-Bild“.

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
| Überschrift der eigenen Welt-Regeln im Tab „Welt-Regeln“ (**neu in 1.7**, [`docs/addon-world-rules.md`](../addon-world-rules.md) §3) | `justmoreparts.world_rules.section` (Namensraum der Regeln), nur der Name | optional, sonst Mod-Name |
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
- Recycling im Tab „Recyceln“ der Werkbank (ab Propwash 0.6.0): Hat das Teil ein Crafting-Rezept (Ergebnis = das
  Teil-Item), gibt Recycling dessen Zutaten zurück, geteilt durch die Ergebnisanzahl. Nur ohne Rezept gilt
  `basics.recycle` (`RecycleYield`) bzw. der Kategorie-Standard, und zwar als Menge bei **100 % Zustand**;
  `RecycleYield.NONE` (`"none"`) heißt dann: nicht recycelbar. Die Stufen setzt Propwash, Add-ons können sie nicht ändern:
  100 % Zustand → alles, 60–99 % → die Hälfte, darunter (auch kaputt) → ein Viertel. Nachkommaanteile würfelt der Server
  je Zutat, im Schnitt kommt genau dieser Anteil heraus. Ein eigenes Crafting-Rezept reicht also, damit dein Teil fair
  recycelt wird; `recycle` brauchst du nur für Teile ohne Rezept (etwa Beute-Teile).

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
- `PropwashApi.creativeTab().add(…)` gibt es weiter: Solche Einträge landen im **Propwash-Tab** hinter den eingebauten,
  aber nur, solange der Namensraum ihres Items (Registry-Id) keinen eigenen Kreativ-Tab hat. Hat er einen, egal ob
  über `extensionTab(ns)`, automatisch über Teil-Items oder Presets oder direkt im Register der Kreativ-Tabs
  (`<ns>:…`), zeigt Propwash sie nicht im Propwash-Tab, damit nichts doppelt erscheint. Einträge mit Items aus
  `propwash` und `minecraft` bleiben immer im Propwash-Tab. Für eigene Inhalte ist der eigene Tab gedacht
  (`extensionTab(ns).add(…)`).
- Kann der Tab nicht angelegt werden (z. B. Namensraum erst nach dem Einfrieren) und hat der Namensraum auch sonst
  keinen eigenen Kreativ-Tab, bleiben Teile und Presets des Addons als Rückfall im Propwash-Tab.

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
| `VTX` | `vtx.*` (Videokanal und Sendeleistung — gehören nie in einen Sharecode) |
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
- **Neu in 1.7:** Elemente von Add-ons (Schlüssel `<namensraum>:<pfad>`, Abschnitt 21.4) stehen nach den eingebauten,
  immer mit `on`, `col` und `row`, eingeschaltete zuerst, sonst nach Schlüssel sortiert. Beim Übernehmen gelten sie auch
  ohne installiertes Add-on; ungültige Schlüssel und der Namensraum `propwash` werden übersprungen. Add-on-Elemente, die
  im Layout fehlen, behalten ihre Einstellungen (wie bei einer Vorlage). Passt nicht alles in 8192 Zeichen, lässt der
  Export zuerst ausgeschaltete Add-on-Elemente weg.
- Nicht enthalten (Spielervorlieben): Vanilla-HUD ausblenden, analoges Bildzerreißen.
- **Neu in 1.4:** `validate(spec)` prüft nach denselben Regeln wie `apply`, ohne etwas zu übernehmen – für eine
  Eingabe, die erst bei gültigem Layout den „Übernehmen“-Knopf freigibt (Beispiel: OSD-Feld im Tab „API-Beispiel“).

---

## 13. Sharecodes Schritt für Schritt

**Neu in 1.7:** Sharecodes gehören Propwash. Seit 0.6.0 liest Propwash PW1, PW2 und PW3 und schreibt nur noch PW3
(`PW3-…`); jedes Add-on liefert nur eine **Teile-Tabelle** in seinem Jar. Ein eigenes Code-Format, ein eigener Knopf in
der Werkbank oder ein eigener Befehl sind nicht mehr nötig: Werkbank (Knopf „Sharecode“), Konfigurator (Tab
„Sharecode“), `/propwash code …` und die Website verstehen jeden Code mit Teilen aus jedem Add-on. Format:
[`../sharecode/pw3.md`](../sharecode/pw3.md), Tabellen: [`../sharecode/tables.md`](../sharecode/tables.md), Bedienung:
[`../sharecode-ui.md`](../sharecode-ui.md), Design: [`../design/0.6.0/sharecodes-pw3.md`](../design/0.6.0/sharecodes-pw3.md).

### 13.1 Was in einen Code gehört
| Ebene (`ShareOptions.Layer`) | Standard | Quelle |
|---|---|---|
| Teile (`PARTS`, `BuildSpec`) | immer | `builds().read(stack)` bzw. `WorkbenchContext#result()` |
| Lackierung (`PAINT`, `PaintSpec`) | an | dieselbe `DroneData` |
| Flug-Tuning samt Kamerawinkel (`TUNE`, `TuneSpec`) | an | dieselbe `DroneData` |
| OSD-Layout (`OSD`) | aus | `ShareOptions#withOsd(json)`, etwa aus `PropwashClientApi.osd().current()` |
| Name (`NAME`) | an | `DroneData#name` |

Nie: Schalter- und Tastenbelegung, Funkprofile, Akkustand, Verschleiß, Videokanal (`VTX`), Bindungen und Add-on-Daten an
der Drohne (21.11).

### 13.2 Teile-Tabelle des Add-ons
Eine Datei `data/<namensraum>/propwash_share/table.json` im Jar gibt jedem Teil des Add-ons eine kurze Nummer im Code;
dazu `since` (Version, mit der das Teil dazukam) und optional ein eingebautes Ersatzteil (`fallback`) für Spieler ohne das
Add-on (Auszug aus `common/src/devtest/resources/data/propwash_example/propwash_share/table.json`):

```json
{
  "format": 1,
  "namespace": "propwash_example",
  "parts": {
    "frame": [
      {"id": "old_stretch_5", "since": "1.0.0", "deprecated": "1.0.0", "replaced_by": "propwash_example:race_stretch_5", "fallback": "propwash:race_5"},
      {"id": "race_stretch_5", "since": "1.0.0", "fallback": "propwash:race_5"},
      {"id": "deadcat_5", "since": "1.0.0", "fallback": "propwash:freestyle_5"}
    ],
    "motor": [{"id": "m2806_1300", "since": "1.0.0", "fallback": "propwash:m2807_1300"}]
  }
}
```

- Einträge nur **hinten** anhängen, nie umsortieren oder löschen; ein umbenanntes Teil bekommt einen neuen Eintrag, der
  alte `deprecated` und `replaced_by`. Sonst lesen ältere Codes ein falsches Teil.
- Ohne Tabelle funktioniert ein Add-on trotzdem: seine Teile gehen als Text in den Code (länger), Propwash schreibt einmal
  einen Hinweis ins Log.
- Das Prüfwerkzeug `de.lardtom.propwash.api.share.ShareTableCheck` (API-Paket, MIT, braucht nur Gson) läuft im Build des
  Add-ons (`check`, `add`, `freeze`, `init`; Gradle-Aufgabe und Befunde in `tables.md` §3–§4) oder aus JUnit:

```java
List<ShareTableCheck.Finding> findings = ShareTableCheck.check(Path.of("src/main/resources"), "1.2.0",
        Path.of("sharecode-baseline.json"));                            // Vergleichsdatei aus freeze; vor dem ersten Release null
assertTrue(findings.isEmpty(), findings.toString());
```

- Eine Tabelle für `propwash` aus einem fremden Jar verwirft Propwash (21.18). Kurzkennungen und den Eintrag ins
  offizielle Verzeichnis vergibt die Website (`tables.md` §5); Name und Link aus den Mod-Metadaten (Abschnitt 1.3)
  erscheinen beim Import, wenn das Add-on fehlt.

### 13.3 Codes per API (`PropwashApi.shareCodes()`)
Für Mods, die Codes selbst lesen oder verteilen (etwa die Drohne eines Siegers als Code im Chat):

```java
ShareCodes codes = PropwashApi.shareCodes();
ShareResult read = codes.decode(text);                                   // PW1, PW2 oder PW3, wirft nie
if (read.ok()) {
    DroneData imported = read.data().orElseThrow();                      // prüfen und übernehmen: 13.4, 13.5
    for (ShareResult.NamespaceInfo ns : read.namespaces()) {
        if (ns.check() == ShareResult.Check.MISSING) showMissing(ns.name().orElse(ns.namespace()), ns.minVersion(), ns.link());
    }
}
String code = codes.encode(own, ShareOptions.defaults().withLayers(EnumSet.of(ShareOptions.Layer.PARTS, ShareOptions.Layer.PAINT)));
ShareResult converted = codes.convert(oldCode);                         // PW1/PW2 → PW3
converted.convertedCode().ifPresent(this::copyToClipboard);
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `PropwashApi.shareCodes()` → `ShareCodes` | Fassade für Codes | am Server und am Client |
| `ShareCodes#decode(String)` → `ShareResult` | Code lesen (tolerant: Leerraum, Bindestriche, Groß- und Kleinschreibung des Präfixes) | wirft nie; Eingabe höchstens 4000 Zeichen nach dem Entfernen von Leerraum |
| `ShareCodes#encode(DroneData, ShareOptions)` → `String` | PW3-Code schreiben | Teile immer; `IllegalArgumentException`, wenn sich der Inhalt nicht schreiben lässt (über 2500 Bytes, unbekannte Alt-Teile) |
| `ShareCodes#convert(String)` → `ShareResult` | alten Code in PW3 umrechnen | nur mit eingefrorenen Daten, unabhängig vom Rechner; `NOT_CONVERTIBLE` mit Grund, ein PW3-Code kommt unverändert zurück |
| `ShareResult`: `status()`, `format()`, `data()`, `osdJson()`, `namespaces()`, `unknownParts()`, `convertedCode()`, `ok()` | Ergebnis | Status `OK`, `EMPTY`, `INVALID_PREFIX`, `BAD_CHARACTER`, `TOO_LONG`, `TRUNCATED`, `CHECKSUM`, `UNSUPPORTED_VERSION`, `TOO_LARGE`, `CORRUPT`, `OTHER_CODE`, `NOT_CONVERTIBLE` |
| `ShareResult.NamespaceInfo`: `namespace`, `shortId`, `installedVersion`, `minVersion`, `check` (`MATCH`, `MISMATCH`, `NEWER`, `MISSING`, `NO_TABLE`), `name`, `link`, `trustedLink` | je Namensraum des Codes: installiert, passend, wo es das Add-on gibt | `trustedLink` nur für Links aus dem Verzeichnis-Abzug, nie für Links aus dem Code |
| `ShareOptions.defaults()`, `withLayers(Set<Layer>)`, `withAddonInfo(boolean)`, `withOsd(String)`, `has(Layer)` | Ebenen, Name und Link von Add-ons ohne Verzeichniseintrag mitschreiben, OSD-Layout | Standard: Teile, Lack, Tuning, Name, mit Add-on-Infos, ohne OSD |

`ShareCodes` liefert Daten, keine Wirkung: Übernehmen geht weiter über den server-geprüften Import (13.5).

### 13.4 Prüfen und zeigen
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

### 13.5 Übernehmen — der Server entscheidet
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

### 13.6 Tuning ohne Import (nur Client)
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
  Seit 0.6.0 steht dort auch Propwashs eigener Knopf „Sharecode“ (Id `propwash:share_code`, `order()` 1000). Er zählt wie
  ein Addon-Knopf, weicht bei Platzmangel aber zuerst auf „Code“ und dann auf das Symbol `</>` aus, bevor ein Addon-Knopf
  hinter „⋯“ wandert.
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

**Neu in 1.7 – Halten im Selbsttest:** Selbsttests können die echte Taste nicht drücken.
`PropwashClientApi.simulateFlightSchoolHold(slot)` hält die Flugschul-Taste über dem Slot `slot`, bis
`stopSimulatedFlightSchoolHold()` sie loslässt. Damit läuft im Selbsttest derselbe Weg wie beim Spieler: Halte-Zeit
(650 ms), Hinweis und Öffnen der Szene bzw. der gefilterten Übersicht über dem Inventar.

```java
// Spieler im Überlebensmodus, Teil im Hauptinventar, Slot 9 (am Server gesetzt, beim Client angekommen)
mc.gui.setScreen(new InventoryScreen(mc.player));
PropwashClientApi.simulateFlightSchoolHold(InventoryMenu.INV_SLOT_START);   // Slot 9 im Menü des Inventars
// nach etwa 13 Ticks: Szene oder Übersicht „Szenen für: <Teil>“, Eltern-Screen ist das Inventar
PropwashClientApi.stopSimulatedFlightSchoolHold();                          // am Ende des Tests immer loslassen
```

- **Welcher Slot:** `slot` ist der Index im Menü des gerade offenen Container-Screens
  (`AbstractContainerScreen#getMenu().slots`, wie `Slot#index`). Im Spieler-Inventar (`InventoryMenu`) sind das 9–35 für
  das Hauptinventar (`INV_SLOT_START`) und 36–44 für die Schnellleiste (`USE_ROW_SLOT_START`). Der Index gilt für jeden
  Container-Screen, der offen ist, auch für einen, der erst nach dem Aufruf aufgeht. Hat das offene Menü den Slot nicht
  oder ist er leer, passiert nichts. Im Kreativmodus öffnet Minecraft statt `InventoryScreen` das Kreativ-Inventar mit
  anderen Slots; ein Test mit dem Spieler-Inventar stellt den Spieler deshalb vorher in den Überlebensmodus.
- **Wie die echte Taste:** Solange das Halten läuft, zählt nur dieser Slot, die Maus spielt keine Rolle. Wie beim Spieler
  wirkt es nicht, solange ein Item am Mauszeiger hängt oder ein Textfeld den Fokus hat, etwa die Suche im Kreativ-Inventar.
  Der Hinweis erscheint als Pille unter dem Slot, oder als letzte Zeile im Tooltip, wenn die Maus über demselben Slot
  steht. Nach dem Öffnen öffnet dasselbe Halten nicht noch einmal, auch nicht nach der Rückkehr ins Inventar.
- **Neues Halten:** Jeder Aufruf von `simulateFlightSchoolHold` beginnt ein neues Halten, wie Loslassen und erneutes
  Drücken. So prüft ein Test mehrere Teile nacheinander, ohne dazwischen `stopSimulatedFlightSchoolHold()` aufzurufen.
- **Nur in Tests:** `simulateFlightSchoolHold` gibt `false` zurück und wirkt nicht, wenn der Slot negativ ist oder Propwash
  nicht in der Entwicklungsumgebung läuft (Loader im Entwicklungsmodus, etwa `runClient`). Im Spiel bleibt also nur die
  echte Taste. `stopSimulatedFlightSchoolHold()` ändert an der echten Taste nichts.
- Wer diese Methoden benutzt, verlangt `PropwashApi.requireVersion(modId, "1.7")`.

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
| `DRONE_ASSEMBLED(player, drone, data)` | Server | Drohne aus dem Werkbank-Ausgang genommen, auf jedem Weg (Klick, Shift-Klick, Zifferntaste, Wegwerfen) genau einmal; `drone` ist die entnommene Drohne mit Bauplan, Lack, Tuning und Namen, `data` ihre `DroneData` |
| `DRONE_DISASSEMBLED(player, data)` | Server | Drohne in der Werkbank zerlegt |
| `BUILD_IMPORTED(player, request, result)` | Server | nach jeder Import-Anfrage, auch abgelehnten |
| `ESC_BURNED(player, drone, data, cells)` (**neu in 1.4**) | Server | der ESC einer geflogenen Drohne ist durchgebrannt, weil der Akku mehr Zellen hat, als der Stack verträgt — genau einmal, wenn der Stack am Server kaputtgeht (nur mit der Welt-Regel `propwash:crash_damage`, bis 0.4.0 Server-Regel `partWear`); `drone` ist eine Kopie der Drohne als Item, `cells` die Zellzahl |
| `DRONE_HIT_ENTITY(hit)` (**neu in 1.6**) | Server | eine geflogene Drohne trifft ein Lebewesen (die Spielregeln erlauben es); `EntityHit` mit Relativtempo, Kontaktpunkt, Halterung und änderbarem Schaden (Abschnitt 20.5) |
| `DRONE_DAMAGED(damage)` (**neu in 1.6**) | Server | die Drohne nimmt Schaden (Mobs, Pfeile, Explosionen, Spieler); `DroneDamage` mit `setAmount` und `cancel` |
| `ACCESSORY_ACTION(player, drone, number, pressed)` (**neu in 1.6**) | Server | der Pilot drückt oder lässt eine Zubehör-Aktion 1–4 los (geprüft) |

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
| Client-Erweiterungen | `api.client.*` (seit 1.6 mit `api.client.hud`, `api.client.camera`, `ClientInput`; seit 1.7 mit `api.client.osd`, `RecordingControl`, `ServiceExtension`) |
| Zubehör, Drohnen, Flug (seit 1.6) | `api.accessory.*`, `api.drone.*`, `api.flight.*` (reines Java); seit 1.7 mit Werfen, Anzeige, Kollisionsregeln, Drohnen-Daten, autonomem Flug und Operator |
| Schalter und Achsen (seit 1.7) | `api.input.*` |
| Welt-Regeln der Add-ons (seit 1.7) | `api.worldrule.*` |
| Sharecodes (seit 1.7) | `api.share.*` samt Prüfwerkzeug `ShareTableCheck` |
| Renndaten (seit 1.7) | `api.race.*` (nur lesend; JSON-Spiegel mit eigener Schema-Nummer, [`race-data.md`](race-data.md) §5) |
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
- **Records aus 1.7** sind keine Wertrecords: Sie entstehen nur über Builder und Fabriken (`SwitchSpec.builder`,
  `AxisSpec.builder`, `OsdElementSpec.builder`, `OsdText.of`, `AutonomyOptions.of`, `AutonomyRequest.of`,
  `DispenseOptions.defaults`, `ItemDisplay.at`, `ShareOptions.defaults`, `BoardQuery.laps`/`races`,
  `CollisionFlags.SOLID`/`GHOST` mit `with…`) und bekommen in Nebenversionen Felder. Ergebnis-Records (`CameraPose`, `DispenseResult`, `ShareResult`, die Schnappschüsse
  aus `api.race`) liest ein Add-on nur; keine Record-Muster mit allen Komponenten.
- Enums (`PartKind`, `FrameRole`, `FrameShape`, `PropMaterial`, `TuneGroup`, `ImportStatus`, `ImportLayer`,
  `ImportTarget`, seit 1.7 auch `AutonomyResult`, `AutonomyEnd`, `DispenseResult.Status`, `OsdTone`, `OsdGroup`,
  `DroneKnockback.Cause`, `ShareResult.Status`, `Category`, …) bekommen in Nebenversionen neue Konstanten **am Ende** —
  `switch` immer mit `default`.
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

API 1.6 (in Propwash 0.5.2, Netzprotokoll 16): Halterung vorne (`AccessorySlot.FRONT`), Zubehör-Verhalten mit
Inventar und Aktionen (`PropwashApi.accessories()`), Drohnen-Abfragen und Stöße (`PropwashApi.drones()`), Flughilfen und
Kräfte (`PropwashApi.flight()`), die Events `DRONE_HIT_ENTITY`, `DRONE_DAMAGED`, `ACCESSORY_ACTION` und im Client
`registerHudElement`, `registerGimbal`, `input()` – Abschnitt 20 und [`../design/addon-api-1.6.md`](../design/addon-api-1.6.md).
Addons für 1.0 bis 1.5 laufen unverändert; wer die neuen Punkte benutzt, prüft `requireVersion(modId, "1.6")` und hängt
von `propwash >= 0.5.2` ab.

API 1.7 (in Propwash 0.6.0, Netzprotokoll 20): Schalter und Achsen (`PropwashApi.switches()`, `api.input`), OSD-Elemente
und -Warnungen (`api.client.osd`), Kollisionsregeln, Werfen (`AccessoryContext#dispense`), sichtbarer Inhalt
(`AccessoryBehavior#display`), Stöße (`DRONE_KNOCKBACK`), alle Drohnen und Kamerablick, Welt-Regeln
(`PropwashApi.worldRules()`), Daten an der Drohne (`PropwashApi.droneData()`), autonomer Flug, zweiter Operator, Gimbal in
Aufnahme, Modell und Brennweite, Wartungsknöpfe, `PILOT_CHANGED`, Kompass im `FlightState`, LED-Pfad und gerade hängendes
Zubehör, Sharecodes (`PropwashApi.shareCodes()`) und Renndaten (`PropwashApi.race()`) – Abschnitt 13 und 21,
[`../addon-api-1.7.md`](../addon-api-1.7.md). Jede neue Methode an einer Schnittstelle aus 1.6 ist `default`; Addons für
1.0 bis 1.6 laufen unverändert. Einzige Ausnahme: Anmeldungen unter `propwash:` an bisher toleranten Stellen
(Konfigurator-Tabs, Werkbank-Erweiterungen, Flugschul-Szenen) werden verweigert, ohne Absturz (21.18). Wer die neuen
Punkte benutzt, prüft `requireVersion(modId, "1.7")` und hängt in Fabric von `"propwash": ">=0.6.0-"`, in NeoForge von
`versionRange = "[0.6.0-alpha,)"` ab (21.1); nicht `>=0.6.0` oder `[0.6.0,)`, die Vorabstände ablehnen. Ab dem ersten
Release-Kandidaten ist 1.7 eingefroren.

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

Seit API 1.6 zeigt `dev/example/ExampleAccessories.java` (Client: `client/ExampleAccessoriesClient.java`) die neuen
Bausteine: Werfer vorne mit vier Slots (Schneebälle, Pfeile, Windkugeln, Rückstoß auf Aktion 1), Speer unten (mehr
Schaden bei Treffern von unten über `DRONE_HIT_ENTITY`), Gimbal-Kamera oben mit Kino-Flughilfe (`CineAssist`:
Geschwindigkeits-Steuerung, Position halten, Stopp vor Hindernissen, Umkreisen auf Aktion 2), Gimbal über `GimbalModel`
(Aktion 4 sperrt die Richtung) und ein HUD-Element mit Höhe, Abstand, Tempo und Munition. Selbsttest-Gruppe
`addon-api:` (`interact.client.AddonApiSelfTests`), Bildschirmfotos `addon_api_*.png`.

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

---

## 20. Zubehör, Aktionen, Physik, Flughilfen, Gimbal und HUD (neu in 1.6)

Verbindliche Einzelheiten (Koordinaten, Grenzen, Netz, Threads) stehen in
[`../design/addon-api-1.6.md`](../design/addon-api-1.6.md); wie die beiden offiziellen Add-ons die Bausteine benutzen,
zeigen [`../design/addon-mob-defense.md`](../design/addon-mob-defense.md) und
[`../design/addon-cinematic.md`](../design/addon-cinematic.md). Alles hier ist optional: Ohne Add-on verhält sich Propwash
wie vorher.

### 20.1 Zubehör vorne
Neben oben (`top`) und unten (`bottom`) gibt es die Halterung `front` (`AccessorySlot.FRONT`): Das Zubehör hängt unter
der Nase und ragt nach vorn – für Werfer, Gimbal-Kameras, Scheinwerfer. Alle eingebauten Rahmen außer dem Whoop haben
sie; eigene Rahmen melden sie an:

```json
"accessory_mounts": ["top", "bottom", "front"],
"accessory_anchors": { "front": {"pos": [0.0, 0.0, -64.0], "tilt_deg": 0.0, "length_mm": 0.0} }
```

Zubehör für vorne: `"slot": "front"` bzw. `AccessoryDefinition.builder(id).slot(AccessorySlot.FRONT)`. Masse, Maße und
Stirnfläche wirken im Flugmodell wie bei den anderen Halterungen.

**Lage des Ankers (seit 0.6.0, Befund RC3P-5):** Der Quader aus `size_mm` hängt unter dem Anker und ragt nach vorn
(`x ∈ [−b/2, b/2]`, `y ∈ [−h, 0]`, `z ∈ [−t, 0]`); vorn gibt es keinen Sockel. Leg den Anker deshalb genau an die
Vorderkante der Bodenplatte (Oberkante der Platte, wie `freestyle_5` mit (0; 0; −64)) oder unter die Nase bzw. die Platte
(wie `cinewhoop_3` mit (0; −3; −30)), nicht davor: Zwischen gezeichnetem Zubehör und gezeichnetem Rahmen darf höchstens
1 mm Luft bleiben, und der Rahmen darf höchstens 2 mm in den Quader ragen. Die eingebauten Rahmen lagen bis 0.6.0-rc.3
6–9 mm zu weit vorn (Freestyle −72, Race −66, Long Range −76, Cinewhoop (0; −3; −50)). Prüfen lässt sich jeder Rahmen
im Propwash-Entwicklungslauf mit dem eigenen Jar in `fabric/runs/selftest-client/mods/` und
`'-PpropwashSelftestOnly=addon-api-17: Zubehör am Rahmen'`:
Der Schritt meldet je Rahmen und Zubehör Abstand, Überschneidung und bei Ausreißern einen Soll-Anker
([`../accessory-hang.md`](../accessory-hang.md) §7).

### 20.2 Verhalten und Inventar

```java
PropwashApi.accessories().register(LAUNCHER, new AccessoryBehavior() {
    @Override public int slots() { return 4; }
    @Override public boolean accepts(int slot, ItemStack stack) { return stack.is(Items.ARROW); }
    @Override public void onAction(AccessoryAction action) {
        if (action.number() != 1 || !action.pressed()) return;
        int slot = action.inventory().firstFilled();
        if (slot < 0) return;
        ItemStack ammo = action.inventory().take(slot, 1);
        MountPose mount = action.mount();
        Arrow arrow = new Arrow(action.level(), mount.front().x, mount.front().y, mount.front().z, ammo, null);
        arrow.shoot(mount.forward().x, mount.forward().y, mount.forward().z, 2.5F, 0.0F);
        arrow.setOwner(action.player());
        action.level().addFreshEntity(arrow);
        action.physics().impulse(mount.forward().scale(-1.0), new Vec3(0.0, -0.03, -0.08));
    }
});
```

- In der gemeinsamen Init anmelden, je Zubehör-Id einmal. Das Teil selbst meldest du wie jedes Zubehör an.
- Spieler füllen das Inventar über **Wartung → „Zubehör“** (Schleichen + Rechtsklick auf die Drohne); `accepts` und
  `maxStackSize` filtern. Der Inhalt bleibt an Drohne und Item, die Werkbank gibt ihn beim Zerlegen zurück.
- Die Beladung wiegt im Flug mit, mit ihrer echten Masse (Standard: Item-Gewichte wie beim Greifer, Behälter samt
  Inhalt; eigenes Gewicht über `payloadGrams`). Eine schwere Beladung macht die Drohne träge, eine zu schwere lässt sie
  nicht mehr abheben, wie in echt. Einzige Grenze: Zubehör samt Beladung zählt im Flugmodell höchstens 20 kg
  (`AccessoryPayload.MAX_LADEN_GRAMS`, die Grenze jedes Bauteils); keine eingebaute Drohne hebt so viel. Darüber ist
  die Drohne „überladen“: die Wartung zeigt die Beladung rot mit „überladen“, das Drohnen-Item (auch in der Werkbank)
  „Überladen“ und das OSD die Warnung `ÜBERLADEN`. `AccessoryInventory#payloadGrams()` liefert immer die echte
  Beladung; bis 0.5.2 wurde sie stumm auf 2 kg je Halterung gekappt.
- `AccessoryContext#velocity()` ist das Tempo der Drohne am Server in m/s. Im Pilotenflug schätzt Propwash es aus den
  angenommenen Positionen des Piloten mit ihrer Empfangszeit (geglättet über die letzten 300 ms), auch zwischen zwei Ticks
  und wenn in einem Tick kein Paket kam; höchstens so schnell, wie die Tempo-Regel des Piloten erlaubt. Kommen 500 ms
  lang keine Positionen, gilt wieder die Bewegung zwischen den Ticks; eine ruhende Drohne hat 0. Ohne Pilot (abgestellt,
  Heimflug, autonom) ist es die Bewegung zwischen zwei Ticks.
- Lesen geht auf beiden Seiten (`PropwashApi.accessories().contents(drone, slot)`), schreiben nur am Server über
  `inventory(drone, slot)`.
- `tick(context)` läuft am Server jeden Tick (z. B. Nachladen, Abkühlen, gehaltene Aktion als Dauerfeuer über
  `actionHeld(n)`).

### 20.3 Zubehör-Aktionen 1–4
Die Spieler legen „Zubehör-Aktion 1–4“ im Tab „Modi“ auf Schalter oder Taster; ohne Belegung gehen die Tasten
Ziffernblock 9, 0, Enter und + (frei belegbar; Ziffernblock 8 ist der Turtle-Modus). Drücken und Loslassen gehen geprüft an den Server: `onAction` aller
Verhaltens-Zubehörteile der Drohne, danach das Event `ACCESSORY_ACTION`. Flughilfen sehen gehaltene Aktionen sofort als
`FlightState#action(n)`, der Client über `PropwashClientApi.input().action(n)`.

### 20.4 Stöße, Schübe, Kräfte
- `PropwashApi.drones().physics(drone)` → `impulse(J, rahmenpunkt)` (N·s) oder `push(F, rahmenrelativ, rahmenpunkt,
  sekunden)` (N). Propwash schickt sie an den Client des Piloten, ins Heimflug-Modell am Server oder als Tempo an eine
  abgestellte Drohne. Grenzen: 50 N·s, 500 N, 30 s.
- Dauerhafte Kräfte (Ballon, Fallschirm, Wasser-Eimer): `PropwashApi.flight().registerForces("ns:id", profile -> …)`
  mit `ForceProvider#apply(state, sink)`; läuft mit 100 Hz im Flugmodell auf beiden Seiten.

### 20.5 Treffer und Schaden
```java
PropwashEvents.DRONE_HIT_ENTITY.register(hit -> {
    if (hit.slot().equals(Optional.of(AccessorySlot.BOTTOM)) && hit.accessory().equals(Optional.of(SPEAR))) {
        hit.addDamage((float) (0.6 * hit.closingSpeed()));
    }
});
PropwashEvents.DRONE_DAMAGED.register(damage -> {
    if (damage.source().is(DamageTypes.ARROW)) damage.setAmount(damage.amount() * 0.5F);
});
```
`framePoint` ist der Kontaktpunkt in Rahmen-Koordinaten (m, +X rechts, +Y oben, −Z vorn), `slot()` die Halterung in
Richtung des Kontakts.

### 20.6 Drohnen finden, zielen, Hindernisse
`PropwashApi.drones()`: `dronesNear`, `nearestDrone(mob, …)` mit Sichtprüfung, `view(drone)` (Tempo, Masse, Zubehör,
Neigungsgrenze `angleLimitDeg()` aus dem Tuning, Abschnitt 20.7),
`aim(schütze, drohne, tempoProTick, schwerkraftProTick)` mit Vorhalt, `scan(drone, reichweite)` (sechs Richtungen) und
`obstacleDistance(…)`. Drohnen sind keine `LivingEntity` – Mobs brauchen eigene KI-Ziele des Add-ons.

### 20.7 Flughilfen
```java
PropwashApi.flight().registerAssist("ns:cine", profile -> profile.hasAccessory("ns:gimbal_cam") ? new CineAssist() : null);
```
`FlightAssist#update(state, out)` läuft mit 100 Hz im Flugmodell des Piloten. Ausgaben: `sticks` + `mode`
(Knüppel ersetzen), `holdAltitude`/`climb`, `holdPosition`/`velocity` + `maxSpeed` (Regelkreise von ALTHOLD/POSHOLD mit
GPS- und Barometerfehlern), `yawRate`/`heading`, `label` (OSD-Flugmodus), `requestReturnHome` (GPS Rescue).
`sensorRange()` schaltet sechs Hindernis-Strahlen ein. Während GPS Rescue ruht die Flughilfe.

**Neu in 1.7 — Kompass:** `FlightState#compassHeadingDeg()` ist der Kurs, den der Flight Controller annimmt,
`compassErrorDeg()` die Abweichung vom echten Kurs (−180 bis 180°) und `compassHealthy()` ist ab 15° Fehler falsch
(`FlightState.COMPASS_DISTURBED_DEG`). Propwash würfelt den Fehler je Flug (meist 1,5–7,5°, etwa jeder zehnte Flug über
20°); ab etwa 30° kreist die Drohne bei `holdPosition` und `velocity` („Toilet Bowl“). Echte GPS-Drohnen warnen dann
und wechseln in ATTI (Höhe halten, Position nicht); eine Flughilfe kann es ebenso tun:
`if (!state.compassHealthy()) { out.label("ATTI"); out.climb(0.0); return; }`. Am Server fliegt Propwash ohne
Kompassfehler (Fehler 0, gesund). Eigene `FlightState`-Implementierungen müssen nichts ändern (Standard: echter Kurs,
gesund). Für Tests legt die System-Eigenschaft `propwash.test.compassErrorDeg=<Grad>` den Fehler jedes danach
startenden Flugs fest (z. B. `40` gestört, `0` gesund); ohne sie bleibt es beim Würfeln. Einzelheiten:
[`docs/api-compass.md`](../api-compass.md), Testen in Abschnitt 4.

**Neu in 1.7 — Namen der Flugmodi:** Deutet eine Flughilfe die drei Modi um (eine Kamera-Drohne fliegt in ANGLE als N,
in HORIZON als C und in ACRO als S), nennt sie sie über `FlightAssist#modeNameKey(StabilizationMode)`: ein
Übersetzungsschlüssel des Add-ons oder leer (Standard, dann Acro, Angle und Horizon von Propwash). Propwash nimmt die
Namen in der Meldung beim Moduswechsel per Taste („Mode: C (key – radio switch: S)“) und im Hinweis „… per Taste
gewählt“ im Tab „Modi“. Gefragt wird beim Piloten im Client-Thread, nie im Flugmodell; der Schlüssel darf vom Zustand
der Flughilfe abhängen. Leer, blank oder eine Ausnahme ergeben für diesen Modus den Namen von Propwash.
```java
@Override
public Optional<String> modeNameKey(StabilizationMode mode) {
    return Optional.of(switch (mode) {
        case ANGLE -> this.gps ? "mymod.mode.n" : "mymod.mode.atti";
        case HORIZON -> "mymod.mode.c";
        case ACRO -> "mymod.mode.s";
    });
}
```

**Neu in 1.7 — Neigungsgrenze:** `FlightState#angleLimitDeg()` ist die Neigung in Grad, die ANGLE (und HORIZON im
selbstnivellierenden Teil) bei vollem Knüppel ansteuert: der Wert „Winkelgrenze“ (`pid.angleLimitDeg`) aus dem PID-Tab,
10–85°, Standard 60° (`FlightState.DEFAULT_ANGLE_LIMIT_DEG`). Knüppel = gewünschte Neigung / `angleLimitDeg()`; wer
Bremswege oder Beschleunigung mit einer eigenen Höchstneigung plant, nimmt das Minimum aus ihr und `angleLimitDeg()`,
sonst neigt die Drohne bei kleiner eingestellter Grenze weniger als geplant und bremst zu spät. Der Wert gilt am Client
des Piloten und im Add-on-Flug am Server, folgt Änderungen im Flug ab dem nächsten Aufruf und ändert am Flugverhalten
nichts. Dieselbe Grenze liefert `DroneView#angleLimitDeg()` für jede Drohne aus `PropwashApi.drones().view(…)`.
Eigene `FlightState`-Implementierungen müssen nichts ändern (Standard 60°), der bisherige `DroneView`-Konstruktor mit
11 Argumenten bleibt und setzt 60°.

### 20.8 Gimbal-Kamera und HUD (nur Client)
```java
PropwashClientApi.registerGimbal("ns:gimbal", new GimbalProvider() {
    private final GimbalModel model = new GimbalModel().pitch(-10.0);
    public boolean appliesTo(Entity drone) { return PropwashApi.accessories().accessoryAt(drone, AccessorySlot.FRONT).equals(Optional.of(GIMBAL)); }
    public void orient(GimbalContext ctx, Quaternionf rotation) {
        Quaternionfc f = ctx.frame();
        this.model.update(ctx.deltaSeconds(), f.x(), f.y(), f.z(), f.w());
        rotation.set((float) this.model.qx(), (float) this.model.qy(), (float) this.model.qz(), (float) this.model.qw());
    }
});
PropwashClientApi.registerHudElement("ns:hud", (graphics, ctx) -> { /* ctx.telemetry(), ctx.imageX0()… */ });
```
Ein HUD-Element darf mit `replacesOsd` das Propwash-OSD ersetzen; es zeichnet nie über Propwash-Fenstern.

### 20.9 Checkliste 1.6
- [ ] `requireVersion(modId, "1.6")`, Abhängigkeit `propwash >= 0.5.2` auf beiden Seiten.
- [ ] Verhalten, Flughilfen und Kräfte in der gemeinsamen Init, Gimbal und HUD in der Client-Init.
- [ ] Ids mit eigenem Namensraum (`ns:name`), Texte de und en.
- [ ] Welt verändern nur am Server (in `onAction`/`tick`), nie aus einer Flughilfe.
- [ ] Ausnahmen in Flughilfen und Kräften schalten diese für den Flug ab – im Log nachsehen.

---

## 21. Neu in 1.7: Schalter, OSD, Kollision, Werfen, autonomer Flug, Operator und mehr

API 1.7 kommt mit Propwash 0.6.0 (`API_VERSION` 1.8.0 mit den Ergänzungen aus 1.8, Netzprotokoll 20). Sie schließt die
Lücken, für die Add-ons bisher Mixins in Propwash-Interna brauchten: eigene Schalter und analoge Kanäle, Texte im OSD,
Kollisionsregeln, Werfen wie ein Dispenser, sichtbarer Inhalt, Stöße, Welt-Regeln, Daten an der Drohne, autonomer Flug,
ein zweiter Operator, Gimbal in der Aufnahme und Knöpfe in der Wartung. Dazu gehören Sharecodes über Propwash (Abschnitt
13), Renndaten für Server und Bots (21.17) und der reservierte Namensraum `propwash` (21.18). Alles ist optional: Ohne
Add-on verhält sich Propwash wie vorher.

Verbindliche Einzelheiten stehen im Design [`../design/0.6.0/addon-api-namespace.md`](../design/0.6.0/addon-api-namespace.md)
(§3, Tabelle §3.19, Entscheidungen §9), die Kurzfassung für die Website in [`../addon-api-1.7.md`](../addon-api-1.7.md).
Jeder Baustein hat einen eigenen Text mit Bedienung, Tests und Interna; er steht jeweils am Anfang des Unterabschnitts.

Die Beispiele benutzen den Namensraum des Beispiel-Add-ons, `propwash_example`, über eine Hilfsmethode
`id("ghost")` = `Identifier.fromNamespaceAndPath("propwash_example", "ghost")`. Ein echtes Add-on nimmt seine Mod-Id;
der Namensraum `propwash` ist gesperrt (21.18). Wo das Design die offiziellen Add-ons als Beispiel nennt, stehen deren
Namensräume (`mobdefense`, `camdrones`). Lauffähig zeigt jeden Baustein das Beispiel-Add-on im Quell-Set `devtest`
(`dev/example/Example…`, Abschnitt 18).

### 21.1 Abhängigkeit und Versionsprüfung

```json
"depends": { "minecraft": "~26.3", "propwash": ">=0.6.0-" }
```

```toml
[[dependencies.meinaddon]]
modId = "propwash"
type = "required"
versionRange = "[0.6.0-alpha,)"
ordering = "AFTER"
side = "BOTH"
```

```java
PropwashApi.requireVersion(MOD_ID, "1.7");    // in der gemeinsamen Init, vor allen 1.7-Aufrufen
```

- Beide Bereiche nehmen `0.6.0-dev.N`, `0.6.0-rc.N` und `0.6.0` an und lehnen 0.5.x ab (geprüft mit Fabric Loader
  0.19.5 und dem Bereichsvergleich von NeoForge).
- **Nicht verwenden:** `>=0.6.0` (Fabric lehnt alle Vorabstände ab), `[0.6.0,)` (NeoForge lehnt alle `-rc`-Stände ab)
  und `[0.6.0-dev.N,)` (lehnt unter NeoForge sogar `0.6.0` ab).
- `requireVersion` prüft die API, nicht die Mod-Version. Wer 1.7 nur teilweise nutzt, fragt
  `PropwashApi.isCompatible("1.7")` und schaltet die neuen Teile sonst ab.
- Fracht am Greifer (21.23) und Zubehör einrücken (3.4) kamen mit 1.8 (ab `0.6.0-dev.14`): Wer sie nutzt, verlangt
  `requireVersion(MOD_ID, "1.8")` oder fragt `isCompatible("1.8")`; Stände bis `0.6.0-rc.3` tragen 1.7.0 und kennen sie
  nicht.
- Jede neue Methode an einer Schnittstelle aus 1.6 ist eine `default`-Methode mit neutralem Ergebnis. Eigene
  Implementierungen und Test-Doubles (`FlightState`, `AssistOutput`, `GimbalContext`, `GimbalProvider`, `ClientInput`,
  `AccessoryBehavior`, `AccessoryContext`, `AccessoryRegistry`, `DroneAccess`) kompilieren und laufen unverändert.
- Wo anmelden: Schalter, Achsen, Kollisionsregeln, Welt-Regeln, Drohnen-Daten, Übergabe-Handler und Events in der
  **gemeinsamen** Init (beide Seiten gleich); Tasten, OSD-Elemente, OSD-Warnungen, Gimbals und Wartungsknöpfe in der
  **Client-Init**.

### 21.2 Schalter und Achsen (A1)

Bedienung, Netz und Tests: [`../addon-switches.md`](../addon-switches.md). Add-on-Funktionen erscheinen im Tab „Modi“
auf der Seite „Add-ons“ und lassen sich dort wie eingebaute Funktionen auf Funke, Gamepad oder Tastatur legen.

```java
AddonSwitch ghost = PropwashApi.switches().register(SwitchSpec.builder(id("ghost"))
        .toggle(true).latchesOnGamepad(true).serverSync(true)
        .descriptionKey("propwash_example.switch.ghost.description").build());
AddonAxis tilt = PropwashApi.switches().registerAxis(AxisSpec.builder(id("gimbal_tilt"))
        .center(0.0).keyRateDps(60.0).serverSync(true).build());
PropwashEvents.ADDON_SWITCH_CHANGED.register((player, target, switchId, active) -> log(player, switchId, active));
boolean onServer = ghost.active(drone);                                        // am Server: zuletzt gemeldet
double tiltNow = tilt.value(drone);                                            // −1 … 1
// Client-Init: Tasten, die das Add-on selbst beim Loader anmeldet
PropwashClientApi.bindSwitchKey(id("ghost"), ghostKey);
PropwashClientApi.bindAxisKeys(id("gimbal_tilt"), tiltUpKey, tiltDownKey);
boolean local = PropwashClientApi.input().switchActive(id("ghost"));
double localTilt = PropwashClientApi.input().axis(id("gimbal_tilt"));
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `PropwashApi.switches()` → `SwitchRegistry` | Register für Add-on-Schalter und -Achsen | – |
| `SwitchRegistry#register(SwitchSpec)` → `AddonSwitch` | Schalter (an/aus) oder Taster anmelden | höchstens 64 über alle Add-ons (`MAX_SWITCHES`, sonst `IllegalStateException`); doppelte Id und Namensraum `propwash` → `IllegalArgumentException` |
| `SwitchRegistry#registerAxis(AxisSpec)` → `AddonAxis` | analogen Kanal (Drehregler, Schieber, Tasten) anmelden | höchstens 16 (`MAX_AXES`); sonst wie oben |
| `SwitchRegistry#switches()`, `#axes()`, `#find(Identifier)`, `#findAxis(Identifier)` | angemeldete Funktionen lesen | unveränderliche Listen |
| `SwitchSpec.builder(Identifier)` mit `toggle`, `latchesOnGamepad`, `serverSync`, `nameKey`, `descriptionKey`, `build` | Beschreibung eines Schalters | Id höchstens 64 Zeichen (`SwitchSpec.MAX_ID_LENGTH`); Standard: Schalter (`toggle` an), rastet am Gamepad nicht, nur lokal; Name `<ns>.switch.<pfad>` (`SwitchSpec.defaultNameKey`) |
| `AxisSpec.builder(Identifier)` mit `center`, `keyRateDps`, `serverSync`, `nameKey`, `build` | Beschreibung einer Achse | `center` −1…1 (Standard 0), `keyRateDps` über 0 bis 3600 °/s (Standard 60, ein Vollausschlag = 90°); Name `<ns>.axis.<pfad>` |
| `AddonSwitch#active(Entity)`, `AddonAxis#value(Entity)` (dazu `id()`, `spec()`) | Zustand je Drohne | am Server der zuletzt gemeldete Zustand (nur mit `serverSync`), beim Piloten der eigene, sonst `false` bzw. `center` |
| `PropwashClientApi.bindSwitchKey(Identifier, KeyMapping)`, `bindAxisKeys(Identifier, KeyMapping, KeyMapping)` | Tastatur-Belegung des Add-ons übergeben | nur Client; das `KeyMapping` meldet das Add-on selbst beim Loader an (Name `key.<ns>.…`, eigene Kategorie) |
| `PropwashClientApi.simulateRadio(boolean)`, `stopSimulatedRadio()`, `setSimulatedAxis(int, float)`, `setSimulatedButton(int, boolean)`, `bindSimulatedSwitch(Identifier, int)`, `bindSimulatedSwitch(Identifier, int, int, int)`, `bindSimulatedAction(int, int)`, `bindSimulatedAction(int, int, int, int)` | Testfunke für Selbsttests: Gamepad oder Funke ohne Gerät, Add-on-Schalter und Zubehör-Aktionen belegen, Knöpfe und Achsen bewegen | nur Client und nur in der Entwicklungsumgebung; `false`, wenn nichts simuliert wird oder Id, Aktion, Knopf, Achse oder Bereich nicht passen |
| `ClientInput#switchActive(Identifier)`, `ClientInput#axis(Identifier)` | Zustand beim Piloten lesen (Flughilfe, Gimbal, OSD) | nur im eigenen Flug; sonst `false` bzw. `center`, unbekannte Id 0 |
| `PropwashEvents.ADDON_SWITCH_CHANGED` (`AddonSwitchChanged`: Spieler, Drohne, Id, an/aus) | Wechsel am Server mitbekommen | nur `serverSync`-Schalter; auch beim vollständigen Abgleich am Anfang einer Pilotensitzung, wenn sich etwas ändert |
| `FlightState#addonSwitch(String)`, `#addonAxis(String)`, `GimbalContext#axis(String)` | Zustand im Flugmodell bzw. im Gimbal (Id als Text `ns:pfad`) | dieselben Werte wie `AddonSwitch#active`/`AddonAxis#value` für die Drohne: in Flughilfe, Kräften und Gimbal beim Piloten der eigene Zustand, im Server-Flug (Heimflug, autonomer Flug) der zuletzt gemeldete – nach dem Pilotenwechsel also `false` bzw. `center`; unbekannte oder ungültige Id `false` bzw. 0, Achsen gekappt auf −1…1 |

- **`serverSync`:** Nur solche Schalter und Achsen gehen an den Server (Pakete `propwash:addon_switch` und
  `propwash:addon_axis`, vor dem nächsten Bewegungspaket). Kennt der Server einen `serverSync`-Schalter nicht (Add-on
  nur beim Spieler), wirkt er beim Piloten nicht und die Seite „Add-ons“ zeigt „Auf diesem Server inaktiv“. Ohne
  `serverSync` wirkt eine Funktion nur lokal.
- **Belegung bleibt:** Ohne das Add-on bleibt seine Belegung in `config/propwash/input.json` stehen und wirkt wieder,
  sobald es zurück ist.
- **Moduswahl am 3-Stufen-Schalter:** drei Schalter auf den drei Bereichen desselben Kanals; eine eigene Signatur gibt es
  nicht.
- **Tasten:** Fabric `KeyMappingHelper.registerKeyMapping(…)`, NeoForge `RegisterKeyMappingsEvent#register` (und
  `registerCategory` für die eigene Kategorie). Ohne Standardtaste (`InputConstants.UNKNOWN`) legt der Spieler sie selbst
  fest.
- **Testfunke für Selbsttests:** `PropwashClientApi.simulateRadio(true)` startet ein simuliertes Gamepad mit 6 Achsen
  (0–3 Sticks, 4 und 5 Trigger) und 15 Knöpfen in der Gamepad-Reihenfolge von SDL (0 A, 1 B, 2 X, 3 Y, 4 Back, 5 Guide,
  6 Start, 7 und 8 Stick-Klick, 9 und 10 Schultertasten, 11–14 Steuerkreuz oben, unten, links, rechts),
  `simulateRadio(false)` eine Funke mit 12 Kanälen (0 Roll, 1 Nick, 2 Gas, 3 Gier, ab 4 Schalterkanäle) und 16 Knöpfen.
  Beide bekommen das Standardprofil von Propwash und ersetzen bis `stopSimulatedRadio()` jedes echte Gerät und die
  Tastatur als Steuerung, auch für ARM und die eingebauten Schalter. Frei sind im Standardprofil am Gamepad etwa Knopf 0
  und das Steuerkreuz, an der Funke die Kanäle 8–11 und alle Knöpfe. Am Anfang stehen die Sticks in der Mitte, Gas,
  Trigger und Schalterkanäle unten, kein Knopf ist gedrückt und kein Add-on-Schalter belegt. `bindSimulatedSwitch(id,
  knopf)` belegt einen Add-on-Schalter wie die Auswahl „Quelle“ auf der Seite „Add-ons“: Am Gamepad rastet ein Schalter
  mit `latchesOnGamepad` je Druck ein, sonst wirkt der Knopf nur gehalten. `bindSimulatedSwitch(id, kanal, vonMikros,
  bisMikros)` belegt einen Schalterkanal (nicht die Sticks) mit einem Bereich von 900 bis 2100 µs, etwa drei Bereiche
  desselben Kanals für einen 3-Stufen-Schalter. `bindSimulatedAction(nummer, knopf)` und `bindSimulatedAction(nummer,
  kanal, vonMikros, bisMikros)` belegen ebenso Zubehör-Aktion 1–4 wie die Auswahl „Quelle“ im Tab „Modi“: ein Knopf
  wirkt als Taster (auch am Gamepad nur gehalten), ein Schalterkanal mit Bereich als Schalter. Die Aktion nimmt dann den
  Weg der echten Funke bis zu `onAction` am Server (Drücken und Loslassen, dazwischen `actionHeld`), anders als
  `triggerAction` (Abschnitt 21.6), das nur den Server-Teil auslöst. `setSimulatedAxis(achse, wert)` nimmt −1 … 1
  (1000 … 2000 µs, also `(µs − 1500) / 500`), `setSimulatedButton(knopf, gedrückt)` drückt oder löst einen Knopf; Achsen,
  Kanäle und Knöpfe zählen ab 0. Zwischen Drücken und Lösen muss mindestens ein Bild liegen (im Selbsttest einige
  Ticks); Wechsel innerhalb eines Bildes (Prellen) sieht Propwash nicht, ein Druck rastet genau einmal ein. Ein neues
  `simulateRadio` ersetzt die Testfunke samt Belegungen. `stopSimulatedRadio()` gibt die Steuerung zurück und entfernt
  Testfunke und Profil auch aus `config/propwash/input.json`. Die Methoden geben `false` zurück, wenn nichts simuliert wird
  oder Id, Aktion, Knopf, Achse, Kanal oder Bereich nicht passen; `simulateRadio` außerdem außerhalb der Entwicklungsumgebung
  (Loader im Entwicklungsmodus, etwa `runClient`), damit ein Add-on im Spiel nie die Steuerung des Spielers ersetzt.
  Die Testfunke am besten vor dem Flug starten und am Ende des Tests immer `stopSimulatedRadio()` aufrufen. Mitten in
  einer FPV-Sitzung gestartet oder beendet, läuft die Sitzung weiter; solange die Testfunke läuft, kommen Knüppel, ARM
  und Flugmodus von ihr.

```java
PropwashClientApi.simulateRadio(true);
PropwashClientApi.bindSimulatedSwitch(id("ghost"), 0);
// FPV starten, dann je Druck (Knopf 0 = A):
PropwashClientApi.setSimulatedButton(0, true);                // Geist rastet ein, am Server ein ADDON_SWITCH_CHANGED
PropwashClientApi.setSimulatedButton(0, false);               // einige Ticks später lösen: Geist bleibt an
PropwashClientApi.simulateRadio(false);
PropwashClientApi.bindSimulatedSwitch(id("ghost"), 8, 1700, 2000);
PropwashClientApi.setSimulatedAxis(8, 1.0F);                   // 2000 µs: Geist an
PropwashClientApi.bindSimulatedAction(1, 1);                   // Zubehör-Aktion 1 als Taster auf Knopf 1
PropwashClientApi.setSimulatedButton(1, true);                 // am Server onAction(1, gedrückt)
PropwashClientApi.setSimulatedButton(1, false);                // einige Ticks später: onAction(1, losgelassen)
PropwashClientApi.bindSimulatedAction(1, 9, 1700, 2000);       // Zubehör-Aktion 1 als Schalter auf Kanal 9
PropwashClientApi.setSimulatedAxis(9, 1.0F);                   // an, bis setSimulatedAxis(9, -1.0F)
PropwashClientApi.stopSimulatedRadio();
```

### 21.3 Namen der Zubehör-Aktionen

Hinweis beim ersten Flug: [`../addon-switches.md`](../addon-switches.md) §2. Zubehör löst über die Zubehör-Aktionen
1–4 aus (Abschnitt 20.3); der Rechtsklick beendet im Flug weiter das FPV. Damit Spieler den Auslöser finden, nennt ein
Zubehör seine Aktionen:

```java
@Override
public Optional<String> actionNameKey(int number) {
    return number == 1 ? Optional.of("propwash_example.accessory.dispenser.action.1") : Optional.empty();
}
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `AccessoryBehavior#actionNameKey(int number)` → `Optional<String>` | Übersetzungsschlüssel des Add-ons für Aktion 1–4 | Standard leer; der Tab „Modi“ zeigt „Zubehör-Aktion 1 – Werfer feuern“, beim ersten Flug mit dem Zubehör zeigt Propwash einmal je Zubehörtyp, wie jede Aktion heißt und wo sie liegt |

Im Selbsttest löst ein Add-on seine Aktionen über die Testfunke aus (Abschnitt 21.2, `bindSimulatedAction`): als Taster
auf einem Knopf oder als Schalter auf einem Kanal, auf demselben Weg wie die Funke eines Spielers.

### 21.4 OSD-Elemente und Warnungen (A2)

Bedienung: [`../addon-osd.md`](../addon-osd.md). Propwash zeichnet die Texte in der OSD-Schrift mit den Einstellungen des
Spielers; im OSD-Editor stehen sie in der Gruppe „Add-ons“.

```java
PropwashClientApi.registerOsdElement(OsdElementSpec.builder(id("ammo"))
        .position(1, 10).align(OsdAlign.LEFT).maxChars(8).preview("AMMO 12").group(OsdGroup.ADDON)
        .content(ctx -> ctx.drone().map(drone -> OsdText.of("AMMO " + rounds(drone), OsdTone.NORMAL)).orElse(null))
        .build());
PropwashClientApi.registerOsdWarning(id("ghost"),
        ctx -> PropwashClientApi.input().switchActive(id("ghost")) ? OsdText.of("GHOST", OsdTone.CAUTION) : null);
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `PropwashClientApi.registerOsdElement(OsdElementSpec)` | Textelement im OSD-Raster, im Editor platzierbar | nur Client, einmal beim Start; doppelte Id und `propwash` werfen |
| `OsdElementSpec.builder(Identifier)` mit `nameKey`, `position(spalte, zeile)`, `align`, `enabledByDefault`, `group`, `maxChars`, `preview`, `content`, `build` | Standardplatz und Aussehen | Spalte 0–29, Zeile 0–15 (`COLUMNS`, `ROWS`), `maxChars` 1–30, Id höchstens 64 Zeichen; Standard: aus, Gruppe `ADDON`, links; ohne `content` wirft `build` |
| `OsdContent#text(OsdContext)` → `OsdText` oder `null` | Inhalt in jedem Bild | Render-Thread: kurz halten; `null` = nichts zeigen; eine Ausnahme schaltet das Element bis zum Neustart ab |
| `PropwashClientApi.registerOsdWarning(Identifier, OsdWarning)`, `OsdWarning#warning(OsdContext)` | Text im eingebauten Warnfeld | hinter Propwashs Warnungen, mehrere wechseln sich ab (alle 1,6 s); kritische Warnungen von Propwash haben Vorrang |
| `OsdText.of(text, OsdTone)`, `OsdText.of(text)` | Text und Ton | druckbares ASCII, anderes wird `?`; zu lang wird gekürzt |
| `OsdTone` (`NORMAL`, `ACCENT`, `CAUTION`, `WARNING`), `OsdAlign` (`LEFT`, `CENTER`, `RIGHT`), `OsdGroup` | Farbe je Ton aus den OSD-Einstellungen, Ausrichtung, Gruppe im Editor | neue Konstanten nur am Ende |
| `OsdContext`: `drone()`, `telemetry()`, `fpv()`, `lineOfSight()`, `preview()`, `gameTime()`, `partialTick()` | was das Element sieht | `drone()` leer ohne Sitzung und in der Editor-Vorschau |

- Plätze, Haken, Farbe und Größe stehen in `config/propwash/osd.json` unter `<ns>:<pfad>` und bleiben ohne das Add-on
  erhalten. Das OSD-Layout über `PropwashClientApi.osd()` (Abschnitt 12) und die OSD-Ebene von Sharecodes nehmen diese
  Einträge mit; Add-on-Elemente, die im übernommenen Layout fehlen, behalten ihre Einstellungen.
- Grafik (Balken, Symbole) bleibt ein `HudElement` (Abschnitt 20.8); OSD-Elemente sind Text.

### 21.5 Kollisionsregeln (A3)

Wirkung, Server-Prüfung und Tests: [`../addon-collision.md`](../addon-collision.md).

```java
PropwashApi.drones().registerCollisionRule(id("ghost"), new CollisionRule() {
    @Override
    public CollisionFlags flags(CollisionQuery query) {
        if (query.autonomous() || !query.switchActive(id("ghost"))) {
            return CollisionFlags.SOLID;
        }
        boolean allowed = query.pilot().map(p -> p.entityTags().contains("propwash_example_ghost")).orElse(false);
        return query.side() == CollisionQuery.Side.SERVER && !allowed ? CollisionFlags.SOLID : CollisionFlags.GHOST;
    }

    @Override
    public void onBlockContact(BlockContact contact) {
        if (contact.state().is(Blocks.STONE)) {
            contact.setRestitution(0.8);
            contact.setImpactScale(0.0);
        }
    }
});
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `DroneAccess#registerCollisionRule(Identifier, CollisionRule)` | Regel anmelden (gemeinsame Init, beide Seiten) | Id im eigenen Namensraum, jede nur einmal |
| `CollisionRule#flags(CollisionQuery)` → `CollisionFlags` | welche Kollisionen gelten | einmal je Client-Tick beim Piloten, am Server bei jedem Bewegungspaket und im autonomen Flug; je Feld gilt das UND aller Regeln (jede darf abschalten, keine wieder einschalten); eine Ausnahme zählt als „fest“ |
| `CollisionFlags` (`blocks`, `fluids`, `entities`, `snags`), `SOLID`, `GHOST`, `withBlocks/withFluids/withEntities/withSnags`, `and`, `solid()` | Ergebnis | nur über die Konstanten und `with…` bauen |
| `CollisionQuery`: `drone()`, `view()`, `pilot()`, `side()` (`CLIENT`/`SERVER`), `autonomous()`, `switchActive(Identifier)`, `axis(Identifier)` | was die Regel sieht | Schalter und Achsen am Server nur mit `serverSync` |
| `CollisionRule#onBlockContact(BlockContact)` | Antwort einer berührten Fläche ändern | dort, wo das Flugmodell läuft (Pilot, Server im Heimflug und autonomen Flug); Regeln nacheinander in Anmeldereihenfolge |
| `BlockContact`: `drone`, `level`, `pos`, `state`, `side`, `normal`, `impactSpeed`, `baseRestitution/baseGrip/baseImpactScale`, `restitution/grip/impactScale`, `setRestitution/setGrip/setImpactScale` | Kontakt und Antwort | Startwert aus den Block-Tags `#propwash:drone_bouncy|drone_cushion|drone_sticky`; gekappt auf Rückprall 0–0,95, Haftung 0,05–1,5, Aufprall 0–1 |

- **Der Server entscheidet:** Er lässt die Kollisionsprüfung des Bewegungspakets nur aus, wenn **seine eigene**
  Auswertung `blocks = false` ergibt; sonst setzt er zurück, und der Pilot liest „Der Server erlaubt hier kein Fliegen
  durch Blöcke.“ Rechte prüft die Regel deshalb auf der Seite `SERVER`.
- `blocks = false`: keine Block-Kollision und kein Boden im Flugmodell, Gate-Rahmen ohne Kollision, Kamera darf in
  Blöcke und zeigt sie wie im Zuschauermodus. Wird die Drohne in Blöcken wieder fest, bleibt sie durchlässig, bis die
  Hülle frei ist (OSD `IM BLOCK`).
- Runden mit abgeschalteter Kollision zählen als Runde mit Hilfe (Grund „Sonstiges“, Id der Regel). Ohne Regel ist das
  Flugmodell bitgleich wie vorher.

### 21.6 Werfen wie ein Dispenser (B1)

Ablauf je Item, Rückstoß-Tabelle und Tests: [`../accessory-gaps.md`](../accessory-gaps.md) §1.

```java
@Override
public void onAction(AccessoryAction action) {
    if (action.number() != 1 || !action.pressed()) return;
    int slot = action.inventory().firstFilled();
    if (slot < 0) return;
    DispenseResult result = action.dispense(slot, DispenseOptions.defaults().speedScale(1.0).inaccuracy(1.0).consume(true));
    if (result.status() == DispenseResult.Status.DENIED) action.pilot().ifPresent(p -> p.sendSystemMessage(NO_RIGHTS));
}
// am Server ohne Client, etwa aus einem Befehl mit Operator-Recht
boolean handled = PropwashApi.accessories().triggerAction(drone, 1, true, operator);
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `AccessoryContext#dispense(int slot, DispenseOptions)` → `DispenseResult` | Item aus einem Slot werfen, wie ein Vanilla-Dispenser, in genau `MountPose#forward()` | nur am Server-Thread aus `onAction` oder `tick` (sonst `REFUSED`); Geschosse treffen die eigene Drohne nie (fliegen hindurch, ihr Schaden kommt dort nicht an); TNT nur mit der Spielregel `tnt_explodes` |
| `DispenseOptions.defaults()`, `speedScale`, `inaccuracy`, `allowFluids`, `applyRecoil`, `consume` | Mündungstempo und Streuung (Faktor auf Vanilla), Eimer erlauben, Rückstoß selbst anwenden, Item verbrauchen | Standard 1, 1, aus, an, an; negative oder unendliche Werte werfen |
| `DispenseResult`: `status()`, `entity()`, `recoilNs()`, `success()`; `Status` `FIRED`, `PLACED`, `USED`, `EMPTY`, `REFUSED`, `BLOCKED`, `DENIED` | Ergebnis | `DENIED` = keine Bau-Rechte des Piloten bzw. auslösenden Spielers am Mündungsblock (vor TNT, Flüssigkeiten und Block-Verhalten; Geschosse prüfen keine Bau-Rechte) |
| `AccessoryRegistry#triggerAction(Entity drone, int number, boolean pressed, ServerPlayer actor)` → `boolean` | Zubehör-Aktion am Server auslösen, wie das Paket des Piloten | Rechte prüft der Aufrufer; gleiche Ratenbegrenzung; `false` ohne Zubehör mit Verhalten, bei Nummer außerhalb 1–4 oder außerhalb des Server-Threads |

- Abgabepunkt ist `MountPose#front()` (Mitte der Vorderfläche oder `muzzle_mm`, Abschnitt 3.4). Steht die Drohne auf
  dem Boden, trägt sie ihr Zubehör: Ragt die Box unter die Standfläche (eine Halterung vorn hängt bis zu ihrer Höhe unter
  dem Anbaupunkt), rückt der Abgabepunkt um diese Tiefe nach oben, TNT und ausgeworfene Items starten nicht unter der
  Standfläche. `BLOCKED` gibt es nur, wenn dieser Punkt in einem festen Block liegt (oder der Chunk nicht geladen ist
  oder ein Vanilla-Verhalten nichts bewirkt); eine abgestellte Drohne feuert also. Im Flug bleibt der Punkt, wo er ist.
- `consume(false)`: der Slot bleibt unverändert, Rückgaben wie Flaschen entfallen. Mit `applyRecoil` (Standard) gibt
  Propwash `recoilNs()` selbst als Stoß entgegen `forward()` ins Flugmodell (Windkugel zusätzlich 0,8 N·s).
- Treffer durch Mobs brauchen keine Signatur: `DRONE_DAMAGED` liefert die Schadensquelle mit dem Mob als Verursacher.

### 21.7 Inhalt sichtbar (B2)

Lage, Drehung und Beispiel: [`../accessory-gaps.md`](../accessory-gaps.md) §2.

```java
@Override
public Optional<ItemDisplay> display(int slot) {
    return Optional.of(ItemDisplay.at(0.0, -18.0, 25.0).rotated(-90.0F, 0.0F, 45.0F).scaled(1.0F));
}
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `AccessoryBehavior#display(int slot)` → `Optional<ItemDisplay>` | den Stapel aus diesem Slot an der Drohne zeigen (alle Zuschauer) | läuft am Client in jedem Bild, ohne Seiteneffekte; leerer Slot oder leer = nichts; nicht in der eigenen FPV-Ansicht |
| `ItemDisplay.at(xMm, yMm, zMm)`, `rotated(gieren, nicken, rollen)`, `scaled(faktor)` | Lage in mm vom Anbaupunkt (x rechts, y oben, −z vorn), Drehung in Grad, Größe | Versatz ±500 mm je Achse, Skalierung über 0 bis 4; `scale` 1 = Item-Modell 160 mm |

Mit `hangsStraight` (Abschnitt 3.4) liegt der Versatz in den Achsen des Zubehörs ohne Neigung der Halterung.

### 21.8 Explosions- und Windstöße (B3)

Ursachen und Rechnung: [`../accessory-gaps.md`](../accessory-gaps.md) §3.

```java
PropwashEvents.DRONE_KNOCKBACK.register(knockback -> {
    if (knockback.cause() != DroneKnockback.Cause.OTHER && carriesLauncher(knockback.drone())) {
        knockback.setScale(1.0);
    }
});
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `PropwashEvents.DRONE_KNOCKBACK` (`DroneKnockedBack#onDroneKnockback(DroneKnockback)`) | Stoß durch Explosion oder Windkugel ins Flugmodell einer **geflogenen** Drohne geben | am Server; **Standard-Skala 0**: ohne Listener spüren geflogene Drohnen nichts (wie 0.5.2) |
| `DroneKnockback`: `drone()`, `level()`, `cause()` (`EXPLOSION`, `WIND_CHARGE`, `OTHER`), `deltaVelocity()`, `scale()`, `setScale(double)`, `cancelled()`, `cancel()` | Ursache, Stoß in m/s, Stärke anfordern oder abbrechen | `setScale` 0–2 (`MAX_SCALE`); Stoß = Masse samt Beladung × Δv × Skala, gekappt auf 50 N·s |

Grund für die Standard-Skala 0: Auf Renn-Servern wäre ein Stoß durch fremde Windkugeln ein Griefing-Weg. Der Rückstoß
beim eigenen Werfen (21.6) gilt immer.

### 21.9 Alle Drohnen eines Levels und Kamerablick (B4)

Einzelheiten: [`../accessory-gaps.md`](../accessory-gaps.md) §4.

```java
for (Entity drone : PropwashApi.drones().loaded(level)) {
    PropwashApi.drones().cameraPose(drone).ifPresent(pose -> reactToGaze(pose.eye(), pose.look(), pose.verticalFovDegrees()));
}
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `DroneAccess#loaded(ServerLevel)` → `List<Entity>` | alle geladenen Drohnen eines Levels ohne Entity-Suche (Sculk, Warden, Mob-Ziele) | nur am Server; unveränderlich, günstig genug für jeden Tick |
| `DroneAccess#cameraPose(Entity)` → `Optional<CameraPose>`; `CameraPose(eye, look, up, verticalFovDegrees)` | Blick der FPV-Kamera am Server (Enderman, Creaking) | Linse, Sichtfeld und Uptilt des Baus, ohne Gimbal-Nachlauf; leer ohne FPV-Kamera oder für fremde Entities; `look` und `up` sind Einheitsvektoren |

### 21.10 Welt-Regeln der Add-ons (B5)

Tab, Voreinstellungen und Tests: [`../addon-world-rules.md`](../addon-world-rules.md).

```java
WorldRules rules = PropwashApi.worldRules();
WorldRule<Boolean> buckets = rules.registerBoolean(Identifier.fromNamespaceAndPath("mobdefense", "allow_buckets"), false);
WorldRule<Integer> cooldown = rules.registerInt(Identifier.fromNamespaceAndPath("mobdefense", "cooldown_ticks"), 4, 0, 100);
rules.presetValue(cooldown, WorldRules.PRESET_TRAINING, 0);
rules.presetValue(buckets, WorldRules.PRESET_ARCADE, true);
int ticks = cooldown.get(level);                                               // Server und Client
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `PropwashApi.worldRules()` → `WorldRules` | Register der Add-on-Regeln | – |
| `WorldRules#registerBoolean(Identifier, boolean)`, `#registerInt(Identifier, int standard, int min, int max)` → `WorldRule<T>` | echte Gamerule im eigenen Namensraum, eigener Abschnitt im Tab „Welt-Regeln“ | nur in der gemeinsamen Init (Gamerules sind ein Register); Standard muss im Bereich liegen; doppelt oder `propwash` wirft; nicht in Zonen überschreibbar |
| `WorldRules#presetValue(WorldRule<T>, String presetId, T value)` | Wert in einer eingebauten Voreinstellung | nur `realistic`, `training`, `arcade` (`PRESET_REALISTIC`, `PRESET_TRAINING`, `PRESET_ARCADE`, Liste `PRESETS`); fremde Regel, falscher Typ oder Wert außerhalb wirft; ohne Eintrag lässt die Voreinstellung die Regel unverändert |
| `WorldRules#registered()`, `#find(Identifier)` | angemeldete Regeln lesen | – |
| `WorldRule<T>`: `get(Level)`, `id()`, `type()`, `defaultValue()`, `min()`, `max()`, `nameKey()`, `descriptionKey()` | Wert lesen, Beschreibung | am Client der zuletzt abgeglichene Wert; ohne Welt, vor dem Abgleich oder auf einem Server ohne das Add-on der Standard |

Texte im eigenen Namensraum: `gamerule.<ns>.<pfad>` und `….description`, optional `<ns>.world_rules.section` als
Überschrift des Abschnitts (sonst der Mod-Name). Bearbeiten dürfen wie bei Propwash-Regeln Einzelspieler und Operatoren.

### 21.11 Daten an der Drohne (B6)

Grenzen, Speichern und Abgleich: [`../drone-data.md`](../drone-data.md) §1.

```java
DroneDataKey<Mission> mission = PropwashApi.droneData().register(Identifier.fromNamespaceAndPath("camdrones", "mission"),
        Mission.CODEC, Mission.EMPTY, DroneDataKey.Sync.TRACKING);
PropwashApi.droneData().set(drone, mission, next);                              // nur am Server
Mission now = PropwashApi.droneData().get(drone, mission);                      // Server; Client nur bei TRACKING
Mission onItem = PropwashApi.droneData().get(droneItem, mission);
PropwashApi.droneData().remove(drone, mission);
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `PropwashApi.droneData()` → `DroneDataAccess` | typisierte Add-on-Daten an Drohne und Drohnen-Item | – |
| `DroneDataAccess#register(Identifier, Codec<T>, T standard, DroneDataKey.Sync)` → `DroneDataKey<T>` | Schlüssel anmelden | einmal beim Start, eigener Namensraum, keine Doppelten |
| `#set(Entity, DroneDataKey<T>, T)`, `#remove(Entity, DroneDataKey<?>)` | schreiben | nur am Server (`IllegalStateException` am Client); 2048 Bytes je Schlüssel (`MAX_BYTES_PER_KEY`, `IllegalArgumentException`), 8192 je Drohne (`MAX_BYTES_PER_DRONE`, `IllegalStateException`), gezählt als Binär-NBT |
| `#get(Entity, DroneDataKey<T>)`, `#get(ItemStack, DroneDataKey<T>)` | lesen | fehlt der Wert oder ist er nicht lesbar: Standard; am Client nur `TRACKING` |
| `#keys()`, `#find(Identifier)`; `DroneDataKey`: `id()`, `codec()`, `defaultValue()`, `sync()` | angemeldete Schlüssel | – |
| `DroneDataKey.Sync` `NONE`, `TRACKING` | Abgleich mit Clients, die die Drohne sehen | am Item reisen **alle** Werte (auch `NONE`) – nie Geheimnisse speichern |

Fehlt ein Add-on, bleiben seine Daten an Drohne, Item und Welt unverändert erhalten. Nicht Teil von Sharecodes und
Bauplänen; eine neu gebaute Drohne startet leer.

### 21.12 Autonomer Flug mit Add-on-Flughilfe (B7)

Ablauf, Budget und Ende: [`../addon-autonomy.md`](../addon-autonomy.md).

```java
AutonomyAccess autonomy = PropwashApi.drones().autonomy();
AutonomyResult r = autonomy.start(drone, owner, new FollowMe(owner),
        AutonomyOptions.of(Identifier.fromNamespaceAndPath("camdrones", "follow")).label("FOLLOW").maxDuration(Duration.ofMinutes(20)));
AutonomyOptions upOptions = AutonomyOptions.of(id("hold")).label("HOLD").takeoff(5.0);
AutonomyResult up = autonomy.start(grounded, owner, new Hold(), upOptions);
Optional<AutonomyNotReady> why = up == AutonomyResult.NOT_READY ? autonomy.notReadyReason(grounded, upOptions) : Optional.empty();
autonomy.registerExitHandler(id("mission"), (exiting, pilot) -> missionRunning(exiting)
        ? Optional.of(AutonomyRequest.of(new MissionFlight(pilot), AutonomyOptions.of(id("mission")).label("MISSION")))
        : Optional.empty());
PropwashEvents.AUTONOMY_ENDED.register((ended, ownerId, flightId, reason) -> forget(ended, reason));
Optional<Identifier> running = autonomy.active(drone);
autonomy.stop(drone);
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `DroneAccess#autonomy()` → `AutonomyAccess` | Server-Flug ohne Pilot steuern | nur am Server-Thread |
| `AutonomyAccess#start(Entity, ServerPlayer owner, FlightAssist, AutonomyOptions)` → `AutonomyResult` | Drohne mit der Flughilfe des Add-ons fliegen lassen (Halten, Folgen, Punkt, Wegpunkte), gleiches Flugmodell wie der Heimflug | Budget mit dem Heimflug geteilt (8 je Server, 2 je Spieler; Add-on-Flüge höchstens 6 bzw. 1); Besitzer online und in Funkreichweite; ein neuer Start ersetzt den laufenden Flug (`REPLACED`) |
| `AutonomyOptions.of(Identifier)`, `label(text)`, `maxDuration(Duration)`, `takeoff(metres)` | Id, OSD-Label, Höchstdauer, Start vom Boden | Label höchstens 8 Zeichen; Dauer 1 s bis 60 min (Standard 60 min, harte Grenze; sonst endet der Flug, wenn der Akku kritisch wird); Starthöhe 1–30 m |
| `AutonomyResult` `STARTED`, `NO_BUDGET`, `PILOTED`, `NOT_READY`, `DENIED`, `NOT_ARMABLE`; `started()` | Ergebnis des Starts | `NOT_ARMABLE`: Schärfen am Boden scheitert, der Besitzer bekommt den Grund |
| `AutonomyAccess#notReadyReason(Entity, AutonomyOptions)` → `Optional<AutonomyNotReady>` (`NOT_ON_GROUND`, `NO_TAKEOFF`, `NO_BATTERY`, `REMOVED`) | Grund, aus dem `start` mit diesen Optionen `NOT_READY` ergibt, etwa für die Meldung an den Spieler | `default`-Methode (leer); nur am Server-Thread; leer, wenn diese Bedingungen den Start nicht verhindern; „am Boden“ heißt Kontakt der ganzen Kollisionsform mit Zubehör, Gimbal und Standbeinen (unter 2 cm) und Ruhe; Akkustand und GPS prüft Propwash nicht; `switch` mit `default` ([`../addon-autonomy.md`](../addon-autonomy.md) §1, §3) |
| `AssistOutput#land()` | senkrecht an Ort und Stelle landen, am Boden entschärfen | beendet den Flug mit `LANDED`; Test-Doubles aus 1.6 kompilieren weiter (`default`) |
| `AutonomyAccess#stop(Entity)` → `boolean`, `#active(Entity)` → `Optional<Identifier>` | Flug beenden (`STOPPED`), laufenden Flug nennen | `active` leer ohne Add-on-Flug |
| `AutonomyAccess#registerExitHandler(Identifier, ExitHandler)`; `ExitHandler#onPilotExit(Entity, ServerPlayer)` → `Optional<AutonomyRequest>`; `AutonomyRequest.of(FlightAssist, AutonomyOptions)` | Mission läuft weiter, wenn der Pilot die FPV-Ansicht verlässt | Handler in Anmeldereihenfolge vor Propwashs Heimflug; der erste Treffer startet nahtlos (kein Absacken); am Boden nur mit `takeoff`; ohne Treffer oder bei `NO_BUDGET` Verhalten wie 0.5.2 |
| `PropwashEvents.AUTONOMY_ENDED` (`AutonomyEnded`: Drohne, Besitzer-UUID, Id, `AutonomyEnd`) | Ende jedes Add-on-Flugs | Gründe `PILOT`, `LANDED`, `BATTERY`, `OWNER_OFFLINE`, `OUT_OF_RANGE`, `STOPPED`, `TIMEOUT`, `ERROR`, `REPLACED`, `TERRAIN_LIMIT`; nach `BATTERY`, `TIMEOUT`, `OUT_OF_RANGE` und `TERRAIN_LIMIT` folgt Propwashs Heimflug bzw. Landung; nach `OUT_OF_RANGE` und `TERRAIN_LIMIT` ohne GPS-Empfang an der Stelle der Drohne (Steindecke, Höhle, Nether) Landung an Ort und Stelle; `switch` über `AutonomyEnd` immer mit `default` |
| `FlightState#terrainLimit()` | die Drohne steht am Rand des erzeugten Geländes, weil die Flughilfe sie darüber hinaus schickt | nur im Add-on-Flug am Server, wenn der Besitzer kein Gelände erzeugen darf (Welt-Regel „Drohnen erzeugen neues Gelände“ aus oder Rechtestufe); kehrt die Flughilfe nicht binnen 1 s um, endet der Flug mit `TERRAIN_LIMIT`; sonst immer `false` (Standard für eigene Implementierungen) |

- Die Flughilfe läuft im Server-Thread (4–8 Rechenschritte je Tick) und darf dort Spieler und Welt lesen. Pilotknüppel
  sind neutral; Position, Tempo und Lage sind die echten Werte (kein GPS-Fehler, kein Kompassfehler). Zonen wirken wie
  im FPV-Flug. Eine Ausnahme beendet den Flug mit `ERROR` und Landung.
- Add-on-Flüge werden nicht gespeichert: Nach einem Neustart des Servers startet das Add-on seine Mission neu.
- Neues Gelände wie beim Piloten: Darf der Besitzer Gelände erzeugen (Standard), erzeugt der Server es auch für den
  Add-on-Flug, eine Mission fliegt also bis zu ihrem Ende. Sonst hält Propwash die Drohne 4 m vor dem Rand des erzeugten
  Geländes, meldet das über `FlightState#terrainLimit()` und beendet den Flug nach 1 s mit `TERRAIN_LIMIT` und einem
  Hinweis an den Besitzer; danach Heimflug, ohne GPS-Empfang an der Stelle der Drohne Landung an Ort und Stelle
  ([`../addon-autonomy.md`](../addon-autonomy.md) §4).

### 21.13 Zweiter Operator (B8)

Binden, Lösen und Eingaben: [`../operator-gimbal.md`](../operator-gimbal.md) §1–§2.

```java
OperatorAccess operators = PropwashApi.drones().operators();
if (operators.operator(drone).isEmpty() && operators.bind(drone, invited)) { … }   // nur am Server
operators.unbind(drone);
// Gimbal am Client: dieselben Werte bei Pilot, Operator und Zuschauern
double pan = context.operatorAxis(0);
boolean lock = context.operatorAction(4);
boolean iAmOperator = PropwashClientApi.input().operating();
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `DroneAccess#operators()` → `OperatorAccess` mit `bind(Entity, ServerPlayer)`, `unbind(Entity)`, `operator(Entity)` | zweiten Spieler an die Kamera einer Drohne binden | nur am Server; Operator ist nicht der Pilot, fliegt keine andere Drohne, gleiche Dimension und Videoreichweite; höchstens einer je Drohne; Propwash löst selbst (Fliegen, Dimension, über 110 % Reichweite, Verlassen, Esc) |
| `GimbalContext#operatorAxis(int)` (0 Roll, 1 Nick, 2 Gier, 3 Gas), `#operatorAction(int)` (1–4) | Funke des Operators im Gimbal | 4 Achsen und 4 Aktionen (`OperatorAccess.AXES`, `ACTIONS`); 20 Hz über den Server, keine Vorhersage; ohne Operator 0 bzw. `false` |
| `FlightState#operatorAxis(int)` | dieselben Achsen in der Flughilfe | am Client des Piloten und im autonomen Flug am Server |
| `ClientInput#operating()` | ist dieser Client gerade Operator? | wahr, solange er gebunden ist und das Bild sieht |

Die Funke des Operators fliegt nicht; Tastatur-Flug zählt nicht (Funke oder Gamepad nötig). Ein Ereignis für Binden und
Lösen gibt es nicht; `operator(drone)` ist danach leer.

**Testen von Add-ons:** Ohne Funke legt die System-Eigenschaft
`propwash.test.operatorInput=<Roll>,<Nick>,<Gier>,<Gas>[,<Aktion>…]` die Eingaben fest, die der Client des Operators
schickt (z. B. `1,0,0,0.5,4`: volle Rollachse, Gas halb, Aktion 4 gehalten). Sie wirkt nur im Prozess, in dem sie
gesetzt ist, und nur, solange dieser Client Operator ist; ohne sie ändert sich nichts. Im Selbsttest nach dem Binden
`System.setProperty(…)`, danach den vorherigen Wert wiederherstellen. Einzelheiten:
[`../operator-gimbal.md`](../operator-gimbal.md), Testen in Abschnitt 9.

### 21.14 Gimbal: Aufnahme, Kopf, Brennweite, Livebild (B9)

Einzelheiten: [`../operator-gimbal.md`](../operator-gimbal.md) §3–§6.

```java
PropwashClientApi.registerGimbal("propwash_example:operator_gimbal", new GimbalProvider() {
    public boolean appliesTo(Entity drone) { return carriesGimbal(drone); }
    public void orient(GimbalContext ctx, Quaternionf rotation) { aim(ctx, rotation); }
    public boolean stabilizesRecording() { return true; }
    public boolean modelPose(GimbalContext ctx, Quaternionf rotation) {
        aim(ctx, rotation);
        rotation.premul(new Quaternionf(ctx.frame()).conjugate());               // relativ zum Rahmen
        return true;
    }
    public double fovDegrees(GimbalContext ctx) { return zoomed ? 40.0 : Double.NaN; }
    public boolean showsProps(GimbalContext ctx) { return false; }               // Gimbal-Bild ohne Props
    public boolean gimbalRotation(GimbalContext ctx, Quaternionf rotation) {
        aim(ctx, rotation);                                                      // Kamerapfad aus der Gimbal-Kamera
        return true;
    }
    public double gimbalFovDegrees(GimbalContext ctx) { return zoomed ? 40.0 : Double.NaN; } // Brennweite im Pfad
});
RecordingControl rec = PropwashClientApi.recording();
if (!rec.recording()) rec.start(drone);
rec.photo(drone);
rec.stop();
PropwashClientApi.spectate(drone);
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `action_cam` auf der Halterung `front` (`AccessoryDefinition`, JSON) | ein Gimbal vorn trägt die Action-Cam | bisher nur oben |
| `GimbalProvider#stabilizesRecording()` | Action-Cam nimmt mit der Gimbal-Lage auf, Flashback-Kamerapfad übernimmt sie | Standard `false`; nur melden, wenn der Gimbal die Action-Cam der Drohne trägt |
| `GimbalProvider#modelPose(GimbalContext, Quaternionf)` → `boolean` | Element `gimbal_head` im Zubehör-Modell drehen, für jede sichtbare Drohne | Standard `false` (starr); Drehung relativ zum Rahmen; auf Clients, die nur zusehen, sind die Operator-Achsen 0 – gemeinsame Werte gleicht das Add-on über Drohnen-Daten (`TRACKING`) ab |
| `GimbalProvider#fovDegrees(GimbalContext)` | horizontales Sichtfeld (Brennweite) | Standard `NaN` = Sichtfeld des Baus; gekappt auf 20–150° (`MIN_FOV_DEGREES`, `MAX_FOV_DEGREES`); wirkt auf FPV, Zuschauer, Operator und Aufnahme |
| `GimbalProvider#showsProps(GimbalContext)` → `boolean` | `false`: im gerade gezeigten Bild (Gimbal-Kamera) keine Props und Ducts vor der Kamera | Standard `true` (FPV-Bild mit Props); jeden Frame, für Pilot, Zuschauer und Operator und damit für Fotos und Aufnahmen dieses Bilds |
| `GimbalProvider#gimbalRotation(GimbalContext, Quaternionf)` → `boolean` | Lage der Gimbal-Kamera in Weltachsen für den Flashback-Kamerapfad, unabhängig vom Bild auf dem Schirm | Standard `false` (Pfad wie bisher); nur am Client des Piloten, `spectating()` `false`; Aufnahme und Foto nehmen weiter das Bild auf dem Schirm |
| `GimbalProvider#gimbalFovDegrees(GimbalContext)` | horizontales Sichtfeld der Gimbal-Kamera (Brennweite, Zoom) für den Flashback-Kamerapfad, unabhängig vom Bild auf dem Schirm | Standard `NaN` (Pfad mit dem Sichtfeld des Schirms wie bisher); gekappt auf 20–150°; Kontext wie bei `gimbalRotation` |
| `PropwashClientApi.recording()` → `RecordingControl` mit `start(Entity)`, `stop()`, `photo(Entity)`, `recording()` | eingebaute Aufnahme und Foto (PNG vor Videoeffekten und OSD) | nur Pilot im FPV, Operator und zuschauender Besitzer; `false`, wenn nicht erlaubt, keine Action-Cam oder schon aktiv |
| `PropwashClientApi.spectate(Entity)` → `boolean` | Livebild der eigenen Drohne ohne Brillen-Menü | gleiche Prüfungen wie über die Brille; „eigen“ = im Add-on-Flug (21.12) dieses Spielers |

### 21.15 Pilotenwechsel und Wartung (B10)

Knopfleiste und Beispiel: [`../drone-data.md`](../drone-data.md) §2.

```java
PropwashEvents.PILOT_CHANGED.register((drone, previous, current) -> current.ifPresent(pilot -> countFlight(drone)));
PropwashClientApi.registerServiceExtension(new ServiceExtension() {
    public Identifier id() { return Identifier.fromNamespaceAndPath("propwash_example", "mission"); }
    public Component label() { return Component.translatable("propwash_example.mission.button"); }
    public boolean visible(Entity drone) { return true; }
    public void open(Entity drone, @Nullable Screen parent) { Minecraft.getInstance().gui.setScreen(new MissionScreen(parent, drone)); }
});
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `PropwashEvents.PILOT_CHANGED` (`PilotChanged`: Drohne, `Optional<UUID>` vorher, `Optional<UUID>` jetzt) | jeden Pilotenwechsel am Server mitbekommen | beide Richtungen und Wechsel zwischen zwei Piloten |
| `PropwashClientApi.registerServiceExtension(ServiceExtension)`; `ServiceExtension`: `id()`, `label()`, `visible(Entity)`, `open(Entity, Screen)` | Knopf im Kopf der Wartung (Schleichen + Rechtsklick), Kind-Fenster des Add-ons | höchstens drei sichtbar (`MAX_VISIBLE_BUTTONS`), weitere unter „Mehr“; `open` bekommt die Wartung als `parent`, das Fenster folgt der Theme-Pflicht (14.4) und kehrt mit `setScreen(parent)` zurück |

### 21.16 Weitere Ergänzungen in 1.7

- **Kompass im Flug-Zustand:** `FlightState#compassHeadingDeg()`, `#compassErrorDeg()`, `#compassHealthy()`,
  `FlightState.COMPASS_DISTURBED_DEG` – Abschnitt 20.7.
- **Neigungsgrenze aus dem PID-Tab:** `FlightState#angleLimitDeg()`, `FlightState.DEFAULT_ANGLE_LIMIT_DEG`,
  `DroneView#angleLimitDeg()` – Abschnitt 20.7.
- **LED-Pfad für Zubehör:** `AccessoryDefinition#ledPathMm()`, Builder `ledPath(…)`, JSON `led_path` –
  [`changes.md`](changes.md) (0.6.0).
- **Gerade hängendes Zubehör:** `AccessoryDefinition#hangsStraight()`, Builder `hangsStraight(boolean)`, JSON
  `hangs_straight` – Abschnitt 3.4 und [`../accessory-hang.md`](../accessory-hang.md).
- **Sharecodes:** `PropwashApi.shareCodes()` und die Teile-Tabelle je Add-on – Abschnitt 13.
- **Mündung des Zubehörs:** `AccessoryDefinition#muzzleMm()`, Builder `muzzle(x, y, z)`, JSON `muzzle_mm`; abgestellte
  Drohnen feuern – Abschnitte 3.4 und 21.6, [`../accessory-fix.md`](../accessory-fix.md).
- **Zubehör einrücken (1.8):** `AccessoryDefinition#recessMm()` und `#dropMm()`, Builder `recess(double)` und `drop(double)`,
  JSON `recess_mm` und `drop_mm` – Abschnitt 3.4 und [`../accessory-hang.md`](../accessory-hang.md) §8.
- **Zubehör nur an bestimmten Rahmen:** `AccessoryDefinition#frames()`, Builder `frames(Identifier...)`, JSON `frames`;
  Bauproblem `accessory_frame` – Abschnitte 3.4 und 3.5, [`../accessory-fix.md`](../accessory-fix.md) §4.
- **Beladung ohne 2-kg-Kappung** und **Tempo zwischen den Ticks:** Abschnitt 20.2,
  [`../accessory-fix.md`](../accessory-fix.md).
- **Flugschul-Taste im Selbsttest:** `PropwashClientApi.simulateFlightSchoolHold(int)` und
  `stopSimulatedFlightSchoolHold()` halten die Taste über einem Slot des offenen Inventars, nur in der
  Entwicklungsumgebung – Abschnitt 14.3.
- **VTX-Antennen hinten am Rahmen:** Propwash zeichnet die Antennen des Videosenders getrennt vom VTX-Modell am
  Antennenhalter des Rahmens, schräg nach hinten oben, und dreht sie nicht mit dem Kamerawinkel. Anzahl, Länge und Kappe
  leitet der Kern aus den vorhandenen VTX-Daten ab (Videosystem, Masse, Latenz, Bildqualität, Leistung, Reichweite);
  ein Add-on muss dafür nichts ändern. Elemente, deren Name mit `antenna` beginnt (`antenna`, `antenna_tip`,
  `antenna_socket`, `antenna_tee`, `antenna_joint`, `antenna_lead`), lässt der Renderer im VTX-Modell an der Drohne
  aus. Beides optional und additiv:
  - Rahmen: `FrameDefinition.Builder#antennaAnchor(AnchorSpec)` bzw. JSON
    `"antenna_anchor": {"pos": [0.0, 30.0, 62.0], "tilt_deg": 45.0}` – Fuß der Antennen in mm (X rechts, Y oben,
    Z hinten): mittig, auf Höhe der Deckplatte, ganz hinten hinter der Rahmenkante und hinter dem Akku; der TPU-Halter
    reicht von dort nach vorn bis an die Platte. Neigung aus der Senkrechten nach hinten (Standard
    `AirframeGeometry.DEFAULT_ANTENNA_TILT` = 45°). Ohne Angabe gilt ein Rückfall aus der Rahmengeometrie
    (Akkuplatte, Elektronikhöhe, Radstand; 12 mm hinter der Deckplatte). Ragt ein Akku weiter nach hinten, rücken die
    Antennen 5 mm hinter ihn. Lesen: `AirframeGeometry#antennaAnchor()`.
  - VTX: `VideoDefinition.Builder#antenna(new VideoAntenna(2, 95.0, AntennaCap.PAGODA))` bzw. JSON
    `"antenna": {"count": 2, "length_mm": 95, "cap": "pagoda"}`; Kappen `whip`, `stubby`, `lollipop`, `pagoda`,
    `patch`, Anzahl 1–2, Länge 10–250 mm, an der Drohne höchstens 1,2 × Radstand. Lesen: `VideoDefinition#antenna()`.
  - VTX ohne externe Antennen (etwa die Funkstrecke einer Kamera-Drohne): `VideoDefinition.Builder#antenna(VideoAntenna.none())`
    bzw. JSON `"antenna": "none"`. Propwash zeichnet dann weder Antennen noch Halter und bietet den Lack-Platz
    „Antennenspitzen“ nicht an; Masse, Reichweite und Videolink bleiben, wie die Daten des VTX sie angeben (die Antennen
    haben keine eigene Masse). `VideoAntenna#count()` ist 0, `VideoAntenna#isNone()` wahr; `new VideoAntenna(0, …)`
    ergibt dasselbe. VTX ohne Angabe bekommen weiter abgeleitete Antennen.
  - Einzelheiten und Tabelle der abgeleiteten Antennen: [`../ASSETS.md`](../ASSETS.md) „VTX-Antennen hinten am Rahmen“.

### 21.17 Renndaten für Server und Bots (`api.race`)

Ein Server-Add-on gibt es nicht. Server, Skripte und Discord-Bots lesen die Renndaten direkt: Jeder Kurs hat eine
stabile **Kurs-ID** (UUID), an ihr hängen die Bestenlisten. Vollständige Beschreibung mit Bot-Beispiel und JSON-Schema:
[`race-data.md`](race-data.md).

```java
RaceData race = PropwashApi.race();                                             // nur am Server-Thread
List<CourseInfo> courses = race.courses(server);
Optional<BoardSnapshot> top = race.leaderboard(server, courseId, BoardQuery.laps().limit(10));
RaceEvents.COURSE_RECORD.register(record -> announce(record.courseName(), record.entry().name(), record.entry().timeMillis()));
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `PropwashApi.race()` → `RaceData` mit `courses(MinecraftServer)`, `courses(ServerLevel)`, `course(server, UUID)`, `courseByNumber(server, int)`, `leaderboard(server, UUID, BoardQuery)`, `version()` | Kurse und Bestenlisten nach Kurs-ID lesen | nur lesend, nur am Server-Thread (sonst `IllegalStateException`); unveränderliche Schnappschüsse; ohne Server leer |
| `BoardQuery.laps()`, `.races(runden)`, `assisted()`, `category(…)`, `layout(n)`, `limit(n)`, `frame(klasse)` | Kategorie (rennfrei bzw. mit Flughilfe), Layout, Klasse, Anzahl | `limit` 1–500, Layout 0 = aktuelles |
| `RaceEvents.LAP_COMPLETED`, `RACE_FINISHED`, `PERSONAL_BEST`, `COURSE_RECORD`, `COURSE_CREATED`, `COURSE_CHANGED`, `COURSE_DELETED` | Ereignisse am Server-Thread | ohne Listener keine Last; Netzwerk nie im Listener, sondern in einem eigenen Thread |

Ohne eigene Mod liegen dieselben Daten als JSON-Spiegel im Weltordner unter `propwash/race-data/` (`courses.json`,
`leaderboards/<kurs-id>.json`, gebündelt und atomar geschrieben). Eine Schreib-Schnittstelle gibt es nicht: Zeiten
entstehen nur durch echtes Fliegen.

### 21.18 Namensraum `propwash` (C1–C3)

Der Namensraum `propwash` gehört Propwash; Regeln für Add-ons: [`../../ADDONS.md`](../../ADDONS.md), Prüfung beim
Start: [`../namespace-guard.md`](../namespace-guard.md).

- **Neue Punkte aus 1.7** (Schalter, Achsen, Tasten, OSD-Elemente und -Warnungen, Kollisionsregeln, Welt-Regeln,
  Drohnen-Daten, autonome Flüge, Übergabe-Handler, Wartungsknöpfe) und Punkte, die schon in 1.6 warfen (Teile,
  Zellchemien, Zubehör-Verhalten, HUD, Gimbal, Flughilfen, Kräfte, Presets, Beute-Tabellen, Kreativ-Tabs) werfen eine
  `IllegalArgumentException` mit Mod und Id:
  `Mod 'x' tried to register 'propwash:y' (osd element): the namespace propwash is reserved for Propwash. Use your mod id.`
  Die bisherigen deutschen Meldungen dieser Stellen sind durch diesen englischen Text ersetzt.
- **Bisher tolerante Punkte** (Konfigurator-Tabs, Werkbank-Erweiterungen, Flugschul-Szenen) übernehmen die Anmeldung
  nicht, ohne Ausnahme, mit demselben Text als Fehler im Log. Eine Flugschul-Szene ohne eigenen Namensraum warf bis 1.6
  eine Ausnahme und wird jetzt still verweigert. So bricht kein Add-on, das mit 0.5.2 startete, beim Start ab.
- Reserviert ist nur genau `propwash`; `propwash_example` oder `propwashy` sind eigene Namensräume.
- Beim Start und beim Neuladen sucht Propwash fremde Registry-Ids, Übersetzungsschlüssel, Tastenbelegungen,
  Netzwerk-Pakete, per Mixin angehängte Enum-Werte, neue Daten- und Asset-Dateien und `/propwash`-Unterbefehle unter
  `propwash`. Folge: Fehler im Log und einmal je Spielstart ein Hinweis im Chat, **nie ein Absturz**. Ressourcen- und
  Datenpakete der Spieler dürfen Propwash-Dateien weiter überschreiben.
- Jeder Crash-Report hat den Abschnitt „Propwash add-ons“ mit den Add-ons, den Mods mit Mixins in Propwash-Interna und
  den Funden der Prüfung.

### 21.19 Umstieg: Noclip ohne Mixins

Das Community-Add-on *Noclip* (0.1.x, MIT) schaltet die Kollision der Drohne per Mixins in Propwash-Interna ab. Mit
API 1.7 geht dasselbe ohne Mixin, auf dem dedizierten Server mit Rechteprüfung und ohne Einträge im Namensraum
`propwash`:

| Mixin-Ziel bis 0.5.2 (intern) | Zweck | Ersatz in 1.7 |
|---|---|---|
| `input.SwitchFunction`, `input.client.screen.ModesTab$Row` (angehängte Enum-Werte), `input.client.FunctionKeys` | eigener Schalter „Noclip“ samt Taste | `PropwashApi.switches().register(…)` (Seite „Add-ons“ im Tab „Modi“), `PropwashClientApi.bindSwitchKey(…)` mit eigenem `KeyMapping` |
| `osd.OsdElement` (angehängter Enum-Wert), `osd.client.OsdRenderer#drawElement` | Warnung „NOCLIP ACTIVE“ im OSD | `PropwashClientApi.registerOsdWarning(…)` oder ein OSD-Element (21.4) |
| `drone.client.PilotSession` (`noPhysics`, `fluidAt`) | Flugmodell ohne Block- und Wasserkontakt | Kollisionsregel mit `CollisionFlags.GHOST` (21.5) |
| `drone.DronePiloting#onState` (`noBlockCollision`, `PathCheck.clear`) | Prüfung des Servers umgehen | der Server wertet dieselbe Regel selbst aus und prüft dabei das Recht |
| `drone.client.FpvEyeGuard`, Vanilla `Camera` | Kamera in Blöcken, Darstellung wie im Zuschauermodus | macht Propwash selbst, solange `blocks = false` gilt |
| Schlüssel `key.propwash.noclip`, `propwash.input.switch.…`, `propwash.osd.element.…` | Texte | Schlüssel im eigenen Namensraum: `<ns>.switch.<pfad>`, `key.<ns>.…` |

Das ganze Add-on (gemeinsamer Teil, Client-Teil und die beiden Loader-Einstiege):

```java
public final class GhostAddon {
    public static final String MOD_ID = "propwash_example";                       // im echten Add-on: die eigene Mod-Id
    public static final Identifier GHOST = Identifier.fromNamespaceAndPath(MOD_ID, "ghost");
    public static final String PERMISSION_TAG = MOD_ID + "_ghost";                // Recht am Server: /tag <spieler> add …

    private GhostAddon() {
    }

    public static void init() {                                                  // gemeinsame Init, beide Seiten
        PropwashApi.requireVersion(MOD_ID, "1.7");
        PropwashApi.switches().register(SwitchSpec.builder(GHOST)
                .toggle(true).latchesOnGamepad(true).serverSync(true).build());
        PropwashApi.drones().registerCollisionRule(GHOST, GhostAddon::flags);
    }

    static CollisionFlags flags(CollisionQuery query) {
        if (query.autonomous() || !query.switchActive(GHOST)) {
            return CollisionFlags.SOLID;
        }
        if (query.side() == CollisionQuery.Side.SERVER && !query.pilot().map(GhostAddon::allowed).orElse(false)) {
            return CollisionFlags.SOLID;                                         // ohne Recht setzt der Server zurück
        }
        return CollisionFlags.GHOST;
    }

    static boolean allowed(Player pilot) {
        return pilot.entityTags().contains(PERMISSION_TAG);
    }
}

public final class GhostAddonClient {
    private GhostAddonClient() {
    }

    public static void init(KeyMapping ghostKey) {                               // Client-Init
        PropwashClientApi.bindSwitchKey(GhostAddon.GHOST, ghostKey);
        PropwashClientApi.registerOsdWarning(GhostAddon.GHOST, ctx ->
                PropwashClientApi.input().switchActive(GhostAddon.GHOST) ? OsdText.of("GHOST", OsdTone.CAUTION) : null);
    }

    public static KeyMapping key(KeyMapping.Category category) {
        return new KeyMapping("key." + GhostAddon.MOD_ID + ".ghost", InputConstants.UNKNOWN.getValue(), category);
    }
}

// Fabric: ClientModInitializer#onInitializeClient
KeyMapping key = GhostAddonClient.key(KeyMapping.Category.register(Identifier.fromNamespaceAndPath(GhostAddon.MOD_ID, "keys")));
KeyMappingHelper.registerKeyMapping(key);
GhostAddonClient.init(key);

// NeoForge: Client-Einstieg, modBus aus dem @Mod-Konstruktor
KeyMapping.Category category = new KeyMapping.Category(Identifier.fromNamespaceAndPath(GhostAddon.MOD_ID, "keys"));
KeyMapping key = GhostAddonClient.key(category);
modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
    event.registerCategory(category);
    event.register(key);
});
GhostAddonClient.init(key);
```

- Texte in `assets/<ns>/lang/*.json`: `<ns>.switch.ghost` (Name auf der Seite „Add-ons“), `key.<ns>.ghost`,
  `key.category.<ns>.keys`.
- Im Spiel: Seite „Add-ons“ im Tab „Modi“, Schalter auf Funke, Gamepad oder Taste legen; im Flug durch Wände. Auf einem
  Server ohne das Add-on zeigt die Seite „Auf diesem Server inaktiv“, der Geist wirkt nicht, und es gibt keine
  Rücksetzungen. Auf einem Server mit dem Add-on braucht der Pilot das Recht (hier ein Entity-Tag; ein echtes Add-on
  fragt etwa seine Rechte-Mod oder eine eigene Welt-Regel aus 21.10).
- Runden im Geistmodus zählen als Runde mit Hilfe; in Blöcken wieder fest geschaltet bleibt die Drohne durchlässig, bis
  die Hülle frei ist (`IM BLOCK`).
- Lauffähig im Repository: `dev/example/ExampleSwitches` (Schalter), `ExampleGhost` (Regel) und `ExampleOsd` (Warnung),
  Tasten in `dev/example/client/ExampleSwitchesClient`.

### 21.20 Kompatibilität: was für Mixin-Add-ons bricht

Stabil ist nur `de.lardtom.propwash.api` (Abschnitt 16). Mixins in Interna bleiben erlaubt, aber auf eigenes Risiko:
Eine Kompatibilitätsschicht gibt es nicht. Ein Mixin mit `require` bzw. `defaultRequire: 1`, dessen Ziel sich geändert
hat, bricht den Start ab, sobald die Zielklasse geladen wird. In 0.6.0 ändern sich unter anderem:

| Bereich (intern) | Änderung in 0.6.0 | Folge für Mixins |
|---|---|---|
| Schaltermaske: `input.RadioFrame#activeSwitches`/`readableSwitches`, `input.SwitchEvaluator#bit`, Aufrufer in `PilotSession`, `FlightModeSelector`, `Mode3dSelector`, `SwitchCommandRunner` | `int` → `long` (mehr als 32 eingebaute Funktionen); Add-on-Schalter in einer eigenen Maske | Ziele, `@Shadow` und `@ModifyExpressionValue` mit `int`-Deskriptor finden nichts mehr |
| `rescue.ServerReturnFlight` | Flugmodell in `rescue.ServerFlight` verallgemeinert (Heimflug und autonomer Flug) | Ziele im Heimflug-Modell sind verschoben |
| `drone.client.PilotSession`, `sim.DroneSim`, `sim.MoveReport` | Kollisionsregeln, Oberflächen, Motor-Hitze, Freecam; `MoveReport` bekommt die Felder `surfaceImpactScale` und `ghost` | Einschübe in den Flugweg sehen andere Aufrufe und Zwischenwerte |
| `osd.OsdElement`, `osd.client.OsdRenderer` | neues Element `FLIGHT_PATH_MARKER` am Ende, Add-on-Elemente nach den eingebauten | angehängte Enum-Werte rücken eine Ordnungszahl weiter |
| `drone.client.FpvEyeGuard`, Kamera-Mixin von Propwash | fragen die gemeinsame Abfrage „Kamera darf in Blöcke“ (Kollisionsregeln, Freecam) | Kamera-Schutz und Darstellung in Blöcken schaltet Propwash selbst; ein eigener Mixin dafür ist unnötig |
| Namensraum `propwash` | Prüfung beim Start und Neuladen (21.18) | angehängte Enum-Werte, Schlüssel `propwash.…`/`key.propwash.…`, Pakete und Dateien unter `propwash` sind Funde (Log und Chat-Hinweis, kein Absturz) |

**Noclip 0.1.x:** Die Stellen, auf die es zielt (Tabelle in 21.19: unter anderem `DronePiloting#onState`,
`PilotSession`, `FpvEyeGuard`, `SwitchFunction`, `OsdElement` und `OsdRenderer#drawElement`), hat 0.6.0 mit Name und
Deskriptor behalten, wo es ging, damit das Add-on nicht schon beim Start abbricht. Die Namensraum-Prüfung meldet es
trotzdem (angehängte Enum-Werte, Schlüssel unter `propwash`), und jede spätere Version darf diese Stellen ändern. Der
Weg nach vorn ist der Umstieg aus 21.19.

### 21.21 Checkliste 1.7
- [ ] Fabric `"propwash": ">=0.6.0-"`, NeoForge `versionRange = "[0.6.0-alpha,)"`, `requireVersion(modId, "1.7")`;
  mit Fracht am Greifer oder Zubehör einrücken `requireVersion(modId, "1.8")`.
- [ ] Schalter, Achsen, Kollisionsregeln, Welt-Regeln, Drohnen-Daten und Übergabe-Handler in der gemeinsamen Init;
  Tasten, OSD, Gimbal und Wartungsknöpfe in der Client-Init.
- [ ] Jede Id im eigenen Namensraum, nie `propwash`; Texte `<ns>.switch.…`, `<ns>.axis.…`, `<ns>.osd.…`,
  `gamerule.<ns>.…`, `key.<ns>.…` in de und en.
- [ ] `serverSync` für alles, was der Server sehen muss (Kollisionsregeln, Rechte, `ADDON_SWITCH_CHANGED`).
- [ ] Rechte am Server prüfen: Kollisionsregeln auf der Seite `SERVER`, `triggerAction` vor dem Aufruf.
- [ ] `OsdContent`, `display`, `modelPose`, `fovDegrees`, `showsProps`, `gimbalRotation` und `gimbalFovDegrees`
  laufen in jedem Bild: schnell, ohne Seiteneffekte.
- [ ] Drohnen-Daten klein halten (2 KiB je Schlüssel, 8 KiB je Drohne), keine Geheimnisse.
- [ ] Autonome Flüge: Ergebnis von `start` prüfen, `AUTONOMY_ENDED` auswerten, Missionen nach einem Neustart neu
  starten.
- [ ] Teile-Tabelle für Sharecodes im Jar (Abschnitt 13.2), geprüft mit `ShareTableCheck`.
- [ ] Keine Mixins in Propwash-Interna mehr nötig – sonst fehlt ein Haken: Issue oder Discord (`ADDONS.md`, Regel 3).

### 21.22 Chunks um die Kamera-Drohne (Aufnahmen weit weg)

Flashback zeichnet nur auf, was der Client geladen hat. Fliegt eine Kamera-Drohne außerhalb der Sichtweite ihres
Spielers, schickt der Server diesem Client auf Anfrage die Chunks und Entities um die Drohne mit, ohne seine Ansicht zu
ändern. Kein zweiter Zeichen-Durchgang, kein Einfluss auf Hauptbild, Shader oder Sodium.

```java
// Server
CameraChunkResult result = PropwashApi.drones().cameraChunks().open(player, drone, 4);
PropwashApi.drones().cameraChunks().close(player, drone);

// Client, etwa wenn Flashback aufnimmt
CameraChunkRequests requests = PropwashClientApi.cameraChunks();
if (requests.supported()) requests.request(droneId, 4);
requests.release(droneId);
```

- Die Lage folgt der Drohne; nur die eigene Drohne (geflogen, als Operator gesteuert oder vom Spieler aufgestellt).
- Radius 1–8 Chunks, höchstens die Welt-Regel „Chunks um Kamera-Drohnen“ (`propwash:camera_chunks`, Standard 4, 0 = aus);
  2 Lagen je Spieler, 16 auf dem Server; außerhalb der Funkreichweite oder über 128 Chunks pausiert die Lage.
- Ende bei `close`/`release`, Trennen, Weltwechsel, Tod, verschwundener Drohne, fremder Drohne und Welt-Regel 0;
  `status(...)` ist danach leer, am Client `radius(...)` 0.
- Die Client-Anfrage geht über Propwash an den Server (`propwash:camera_chunks_request`), das Add-on braucht kein
  eigenes Paket. Mit Entity-Id auch für Drohnen, die der Client gerade nicht sieht.

Einzelheiten: [`camera-chunks.md`](../camera-chunks.md).

### 21.23 Greifer und Fracht am Server (Flugplaner, autonome Flüge)

Der Greifer (`propwash:gripper`) ist fest in Propwash eingebaut und hat kein `AccessoryBehavior`; `triggerAction`
(Abschnitt 21.6) erreicht ihn nicht. Seit 0.6.0 greift und lässt ein Add-on ihn am Server über `AccessoryRegistry`
los, auch wenn die Drohne ohne Pilot im Add-on-Flug (Abschnitt 21.12) fliegt, etwa an den Wegpunkten eines Flugplaners.
Neu in 1.8 (ab `0.6.0-dev.14`): Wer die drei Methoden nutzt, verlangt `PropwashApi.requireVersion(modId, "1.8")`.

```java
AccessoryRegistry accessories = PropwashApi.accessories();
// Wegpunkt A: tief über der Fracht schweben, der Fuß der Drohne höchstens etwa 0,9 Blöcke über den Items
boolean open = accessories.grabCargo(drone, owner);
// unterwegs: was hängt im Greifer?
List<ItemStack> held = accessories.cargo(drone);
// Wegpunkt B
int dropped = accessories.releaseCargo(drone, owner);
```

| Signatur | Zweck | Grenzen |
|---|---|---|
| `AccessoryRegistry#grabCargo(Entity drone, ServerPlayer actor)` → `boolean` | Greifer öffnen und greifen: sofort, was in Reichweite liegt, sonst 5 s lang alle 4 Ticks, auch ohne Pilot; der Griff schließt nach der ersten Aufnahme | `false` ohne Greifer, entschärft, am Client oder außerhalb des Server-Threads; Reichweite, Sicht, Tragkraft und Stapel wie beim Piloten; Items, die Vanilla nur einem Spieler gibt, nur für den Piloten oder `actor`; Rechte prüft der Aufrufer |
| `AccessoryRegistry#releaseCargo(Entity drone, ServerPlayer actor)` → `int` | Fracht loslassen wie die Taste des Piloten | Zahl der losgelassenen Items, 0 ohne Fracht, am Client oder außerhalb des Server-Threads; danach greift die Drohne erst 1,5 Blöcke weiter wieder zu, das Abgelegte ignoriert sie 60 s |
| `AccessoryRegistry#cargo(Entity drone)` → `List<ItemStack>` | Kopien der Fracht im Greifer | leer ohne Fracht und am Client |

- Ohne `grabCargo` nimmt eine Drohne ohne Pilot nichts auf, auch nicht im Heimflug. Mit Pilot greift der Greifer wie
  bisher selbst, alle 4 Ticks.
- Meldungen in der Aktionsleiste („Greifer: +4 × Bruchstein · 200 / 400 g“) gehen ohne Pilot an `actor`.
- Das Flugmodell am Server rechnet die Fracht mit: 4 Bruchstein (200 g) machen einen Freestyle 5″ im Add-on-Flug
  0,2 kg schwerer (`FlightState#massKg()`), nach dem Loslassen ist er wieder leicht.
- Alle drei sind `default`-Methoden: eigene Implementierungen von `AccessoryRegistry` kompilieren weiter und liefern
  leer, `false` bzw. 0. Kein neues Paket, das Netzprotokoll bleibt.

Einzelheiten: [`addon-autonomy.md`](../addon-autonomy.md) §11, Greifer: [`drone-interaction.md`](../design/drone-interaction.md) §2.
