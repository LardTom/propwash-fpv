# LED-Streifen und FPV-Scheinwerfer

Der LED-Streifen (Zubehör unten, `AccessoryType.LED_STRIP`, 8 LEDs) leuchtet in einer einstellbaren Farbe und einem
Modus wie die LED_STRIP-Funktionen von Betaflight. Im Dunkeln wirft er im FPV-Bild des Piloten einen Lichtkegel nach
vorn, damit Höhlen fliegbar werden. Mit der optionalen Mod LambDynamicLights ist die Drohne zusätzlich eine echte
Lichtquelle in der Welt. Besitzer: drone (Einstellungen, `drone.led`), drone-render (Leuchten), video (Scheinwerfer),
ui (Konfigurator-Tab), `compat.lambdynamiclights` (drone). Verträge: `docs/FOUNDATION.md` §15 „LED-Streifen und
FPV-Scheinwerfer“.

## Einstellungen je Drohne

Wie bei Betaflight liegt die LED-Konfiguration im Flight-Controller: vier Schlüssel im `FlightTune` der Drohne (Item,
Entity, an alle Tracker synchronisiert; beim Zerlegen bleibt sie mit dem Tuning am FC-Teil).

| Schlüssel | Werte | Standard |
|---|---|---|
| `led.mode` | Ordinalzahl von `LedMode`: Dauerhaft, Blinken, Scharf-Status, Akku-Warnung, Regenbogen | Dauerhaft |
| `led.color` | Ordinalzahl von `LedColor` (11 Farben: Weiß, Warmweiß, Rot, Orange, Gelb, Grün, Cyan, Blau, Violett, Magenta, Rosa) | Weiß |
| `led.brightnessPercent` | 10 … 100 in 5er-Schritten | 100 |
| `led.headlight` | 0/1: Scheinwerfer im FPV-Bild | 1 |

Gespeichert werden nur Abweichungen vom Standard (`TuneMapping.normalize`). Die Schlüssel sind „Einstellungen der
Drohne“ wie der Kamera-Uptilt (`TuneMapping.isDroneSetting`): Sie zählen nicht als eigenes Tuning, eine
Konfigurator-Vorlage überschreibt sie nicht.

## Modi (`LedAnimator`)

Alle LEDs laufen vom Flugakku: ohne Akku, mit leerem Akku oder per Funkenschalter „LEDs aus“ (unten) bleibt der
Streifen dunkel (`LedAnimator.powered`).

- **Dauerhaft:** Farbe × Helligkeit.
- **Blinken:** 2 Hz, halbe Zeit an.
- **Scharf-Status:** entschärft grün (`#3CFF4A`), langsam pulsierend (2,4 s, 30–100 %), scharf dauerhaft in der Farbe.
- **Akku-Warnung:** in der Farbe; unter 20 % Ladung (`LOW_BATTERY_CHARGE`, dieselbe Grenze wie die Arm-LEDs) blinkt
  der Streifen mit 3 Hz orange (`#FF9020`).
- **Regenbogen:** Farbton je LED um 1/8 versetzt, läuft in 4 s einmal durch; die gewählte Farbe wirkt nicht.

Die Helligkeit landet als Alpha im ARGB-Wert je LED. `LedAnimator.light` mischt den Streifen zu einer Lichtfarbe
(höchster Kanal = 1) und einer mittleren Stärke 0 … 1; daraus speisen sich Scheinwerfer und LambDynamicLights
(Lichtstärke `round(12 × Stärke)`).

## Schalter an der Funke: „LEDs aus“ (input, drone)

Wie der Modus LED_LOW bei Betaflight schaltet die Schalterfunktion `SwitchFunction.LEDS_OFF` („LEDs aus“) den ganzen
Streifen ab: Leuchten und Schein am Modell, Licht für LambDynamicLights und den FPV-Scheinwerfer. Ist sie nicht belegt,
leuchten die LEDs wie im Tab „LED-Streifen“ eingestellt; eine unlesbare Quelle (Kanal fehlt) schaltet sie nie ab.

- **Belegen:** Tab „Modi“ (Zeile „LEDs aus“) oder Seite „Schalter“ des Empfänger-Assistenten, wie jede Funktion: AUX-Kanal
  mit Bereich (Standard 1700–2100 µs, also „Schalter oben = LEDs aus“), 3-Stufen-Schalter mit mittlerem Bereich, Taste oder
  Hat, „Inv.“ tauscht die Stellungen, Tasten auch als Umschalter (Gamepad-Tasten rasten standardmäßig ein und starten mit
  LEDs an).
- **Tastatur:** Taste „LEDs an/aus“ (Standard F8, neben F9 der Action-Cam) schaltet während einer Pilot-Sitzung um; sie
  wirkt per ODER zum Funkenschalter. Hält der Schalter die LEDs aus, meldet die Taste „LEDs bleiben aus“.
- **Sofort und für alle:** Der Client des Piloten übernimmt den Zustand im selben Frame (`PilotSession#ledsOff`,
  `QuadcopterEntity#applyLocalState(…, ledsOff)`), schickt den Flugzustand bei einer Änderung sofort (`DroneStateC2S#ledsOff`)
  und der Server verteilt ihn als synchronisiertes Feld `ledsOff()` an alle Spieler. Er wird nicht gespeichert: Bei jedem
  Pilotenwechsel, also auch nach dem Flug, setzt der Server ihn zurück, die LEDs leuchten dann wieder.
- **OSD:** Element „LEDs aus (Schalter)“ zeigt `LED AUS` unter LQ/VID, solange der Streifen per Schalter aus ist; im
  OSD-Editor aus- und verschiebbar.

## Leuchten in der Welt (drone-render)

Je LED ein leuchtender Quader (3 × 0,8 × 3 mm) um das weiße `led`-Element des Modells und ein weicher Leuchthof
(60 % Alpha, `ShapeBuilder.glow`: drei gekreuzte, beidseitige Scheiben mit Radius 7 mm bzw. 1,6 × LED-Abstand, innen voll,
bei 35 % des Radius 40 %, außen 0 – wirkt aus jedem Blickwinkel rund), beides mit `RenderTypes.eyes` (voll hell, auch Iris-Shaderpacks behandeln es
als leuchtend). Die Formen hängen im selben Pose-Stapel wie das Zubehör (Anbaupunkt → Neigung → Streckung), die Farben
rechnet `DroneVisuals` jeden Frame aus Tuning, scharf, Akku und Alter der Entity (`ageInTicks / 20`). Werkbank und
Vorschauen zeigen den Standard (weiß); der LED-Tab zeichnet die Vorschau mit den Entwurfswerten animiert.
Hat die gewählte Drohne keinen LED-Streifen, zeigt der LED-Tab sie ohne Streifen und die Lampenreihe darunter nur
dunkel umrandet (keine leuchtenden LEDs, die es nicht gibt); nur ohne Drohne (Vorlage) steckt die Vorschau einen
Streifen an, damit die Entwurfswerte sichtbar werden.

## FPV-Scheinwerfer (video)

Aktiv, solange das FPV-Bild läuft (`VideoState`), die Drohne einen LED-Streifen hat, `led.headlight` an ist, die LEDs
gerade leuchten (also auch nicht per Schalter „LEDs aus“ abgeschaltet sind), die persönliche Einstellung
„LED-Ausleuchtung“ im Tab „Video“ an ist (`VideoConfig.ledLighting`, Standard an) und es dunkel ist. Die persönliche
Einstellung schaltet nur den Effekt im eigenen FPV-Bild ab (Shader-Pass und Overlay); Leuchten am Modell, das Licht für
LambDynamicLights und was andere Spieler sehen, bleiben.

- **Dunkelheit:** `level.getMaxLocalRawBrightness` an der Drohne; ab Lichtstufe 2 und darunter voll, ab 11 nichts,
  dazwischen weich. Die Helligkeits-Einstellung (Gamma) nimmt bis zu 35 % weg. Geglättet mit 0,35 s.
- **Stärke:** `12 × LED-Stärke × Dunkelheit` (`HeadlightMath.gain`).
- **Shader-Pass** (`HeadlightPass`, `shaders/post/fpv_headlight.fsh`): läuft in `GameRenderer#renderLevel` direkt vor
  `render3dHud`, also nach der ganzen Welt, aber bevor die Hand und die Bildschirm-Effekte die Tiefe überschreiben. Er
  kopiert das Hauptbild, liest die Welttiefe und rekonstruiert mit der inversen Projektion (abgegriffen am
  `levelProjectionMatrixBuffer.getBuffer` derselben Methode) die Position jedes Pixels relativ zur Kamera. Aufhellung
  `farbe + (basis × reichweite × reserve + 0,004 × reichweite) × lichtfarbe` mit `reichweite = stärke × kegel × abfall × lambert`:
  - Basis = Farbe, bei sehr dunklen Pixeln (Luma unter 0,03, weich bis 0,14) ihr Grauwert: der Farbton fast schwarzer
    8-Bit-Pixel ist Rundungsrauschen und würde sonst rosa oder violett verstärkt (`HeadlightMath.hueTrust`);
  - die kleine additive Aufhellung (`HeadlightMath.lift`) macht auch ganz schwarze Wände im Kegel neutral grau sichtbar;
  - Kegel um die Kameraachse: innen 11°, außen 34°, dazwischen `smoothstep²` (deutlicher Rand), außerhalb 2 % Streulicht
    (Bildprüfung: mit 18 % Streulicht und 28 Blöcken Reichweite wirkte das ganze Bild heller statt eines Lichtkegels, mit
    16°/40° und linearem Rand wirkte der Kegel flach grau);
  - Lichtfarbe `HeadlightMath.tint`: Warmweiß (1; 0,9; 0,74), zu 35 % zur LED-Farbe gezogen, höchster Kanal 1 – weiße LEDs
    leuchten warmweiß, farbige färben den Kegel nur leicht;
  - Abfall `1 / (1 + (d / 7,5)²)` (Wände 5–10 Blöcke voraus bleiben deutlich hell), ab 70 % von 24 Blöcken weich auf 0, ferne
    Wände bleiben dunkel;
  - Lambert aus der Normalen (Ableitungen der Position), mit 70 % Gewicht, damit der flach angestrahlte Boden direkt
    vor der Linse nicht als gleichmäßig helle Fläche erscheint;
  - Reserve `1 − smoothstep(0,05; 0,85; Luma)`: helle Stellen (Fackeln, Tageslicht am Höhlenausgang) brennen nicht aus;
  - Himmel (Tiefe 0, Reverse-Z) bleibt unverändert.
  - Robust gegen ungültige Werte (seit 0.5.0, siehe „Schwarzes Bild auf manchen Grafiktreibern“): Bildpunkte mit Tiefe
    ≤ 0 oder ≥ 1, `|w| ≤ 10⁻⁹` nach der inversen Projektion, nicht endlicher Position oder ungültiger Stärke behalten
    ihre Eingangsfarbe; die Normale aus den Ableitungen zählt nur, wenn ihre Länge endlich und größer als 10⁻⁶ ist; das
    Ergebnis wird nur übernommen, wenn es endlich und in jedem Kanal mindestens so hell wie die Eingangsfarbe ist.
    Wo der Pass bisher funktionierte, rechnet er wie vorher (Abweichung höchstens 10⁻⁶, JUnit `HeadlightShaderTest`).
  Der Video-Effekt (Rauschen, Blockartefakte, Latenz-Puffer) läuft danach auf dem schon beleuchteten Bild, also auch
  Standbilder und Blockartefakte zeigen den Lichtkegel.
- **Ersatz ohne Tiefe** (`HeadlightOverlay`): wenn der Video-Pass ins Ersatz-Overlay fällt (Shaderpack über Iris/Oculus,
  Einstellung „Overlay“, fehlender Render-Hook, Pipeline-Fehler) oder der Scheinwerfer-Hook nicht läuft. Eine radiale
  Kegeltextur wird im HUD drei Mal mit Mischfunktion `DST_COLOR, ONE` gezeichnet (`Ziel × (1 + Quelle)`, zusammen bis
  8-fach in der Bildmitte) und einmal leicht additiv (3,5 % Aufhellung in Lichtfarbe). Der Kegel folgt dem FPV-Sichtfeld.

### Schwarzes Bild auf manchen Grafiktreibern (0.5.0)

Ein Spieler meldete mit Intel UHD 620 unter Windows (OpenGL, ohne weitere Mods) ein völlig schwarzes FPV-Bild bei Nacht,
sobald die LEDs an waren. Die Ursache ließ sich ohne die Hardware nicht eindeutig festlegen: Unter Windows (OpenGL mit
`GL_ARB_clip_control`) liegt die Tiefe im Bereich 0..1, im Entwicklungslauf auf dem Mac bei -1..1, und Intel-Treiber
behandeln NaN, `clamp(NaN)` und Ableitungen ungültiger Werte anders als andere. Deshalb drei Schutzschichten:

1. **NaN-sichere Rechnung im Shader** (oben): ungültige Bildpunkte bleiben, wie sie sind, statt schwarz zu werden.
   Auf der Java-Seite gilt die Projektion nur, wenn Matrix und Inverse endlich sind (`HeadlightPass#captureProjection`),
   sonst übernimmt das Overlay.
2. **Bildprüfung** (`video.HeadlightCheck`, Rückleser `video.client.HeadlightProbe`): Der Pass macht das Bild nie
   dunkler, jeder Kanal ist danach mindestens so hell wie vorher. Das prüft Propwash am echten Bild: kurz nach dem
   Einschalten (3. Frame), dann alle 30 Frames bis zu drei bestandenen Prüfungen und danach alle 600 Frames werden
   4 Zeilen des Hauptbilds über die ganze Breite einmal vor und einmal nach dem Pass zurückgelesen
   (`copyTextureToBuffer`, asynchron, wenige KB; Band abwechselnd bei 50 %, 35 % und 65 % der Höhe). Gezählt werden Bildpunkte mit Helligkeit ab 4/255
   in einem Kanal; ist ein Bildpunkt danach in einem Kanal um mehr als 2/255 dunkler, gilt er als abgedunkelt. Mindestens
   64 zählbare Bildpunkte, sonst bleibt die Prüfung ohne Urteil. Sind mindestens 8 und mindestens 1 % abgedunkelt,
   schaltet sich der Shader-Weg ab (`VideoPass.Failure.CHECK`) und die Aufhellung im Overlay übernimmt. Das bleibt bis
   zum Neustart oder bis „Shader erneut versuchen“ im Tab „Video“, auch über einen Weltwechsel (sonst wäre das Bild
   in jeder Welt kurz schwarz). Lässt sich nicht zurücklesen, bleibt der Shader-Weg an und es gibt eine Logzeile.
3. **Persönlicher Schalter** „LED-Ausleuchtung“ im Tab „Video“ für alles, was die Prüfung nicht erkennt.

Jede Abschaltung des Shader-Wegs schreibt genau eine Logzeile mit Grund und Grafik, z. B.
`FPV-Scheinwerfer: Bildprüfung: 6832 von 6832 Bildpunkten dunkler als ohne Licht, Shader-Weg abgeschaltet, nutze die
Aufhellung im Overlay (Grafik: Intel / Intel(R) UHD Graphics 620 / … (OpenGL, Tiefe 0..1))`. Beim ersten Frame steht
einmal `Grafik: <Hersteller> / <Renderer> / <Treiber> (<Backend>, Tiefe 0..1|-1..1)` im Log (`video.client.GpuInfo`),
damit Meldungen aus dem Discord die Grafik gleich mitbringen. Im Tab „Video“ steht bei einer Abschaltung
„LED-Ausleuchtung: einfache Aufhellung (Grund)“.

Tests: `HeadlightShaderTest` rechnet den Shader in Java nach (Szene mit Boden, Wand und Himmel, beide Tiefenbereiche,
2×2-Ableitungen wie auf der GPU): nie dunkler, Himmel unverändert, gleich wie der alte Shader auf gültigen Bildpunkten,
NaN in der Projektion, `w = 0`, ungültige Tiefe, Stärke und Lichtfarbe lassen das Bild unverändert (der alte Shader
liefert dort NaN), Konstanten passen zu `HeadlightMath`. `HeadlightCheckTest` prüft Urteil und Zeitplan. Der
Selbsttest `video: FPV-Scheinwerfer bei Nacht im Freien …` vergleicht bei Mitternacht im Freien Bilder mit LEDs aus, an,
mit abgeschalteter LED-Ausleuchtung, mit NaN in der Projektion (gleich wie ohne Licht) und mit absichtlich schwarzem
Eingangsbild (Prüfung schaltet ab, eine Logzeile, Overlay hellt auf, nichts wird dunkler); Bildschirmfotos
`headlight_night_off.png` und `headlight_night.png`.

### Grenze mit Shaderpacks

Mit einem aktiven Shaderpack rendert Iris die Welt in eigene Puffer; die Welttiefe am Hook passt dann nicht zum Bild.
Propwash nutzt deshalb nur die Aufhellung im Overlay: ein Kegel in der Bildmitte ohne Entfernung, Normale und
Schatten. Ganz schwarze Pixel (viele Packs rendern Höhlen ohne Mindesthelligkeit) bleiben schwarz, weil eine
Multiplikation nichts aufhellen kann; das additive Streulicht macht sie nur leicht sichtbar. Echtes Licht mit Shadern
gibt es mit LambDynamicLights (verändert die Blocklicht-Daten, die auch Shaderpacks lesen). Der LED-Tab sagt das, sobald
ein Shaderpack läuft.

## LambDynamicLights (optional)

LambDynamicLights 4.13.0+26.3 gibt es für 26.3 auf Fabric und NeoForge (Modrinth, 15.09.2026). Die API liegt auf
`https://maven.gegy.dev/releases/` als `dev.lambdaurora.lambdynamiclights:lambdynamiclights-api` (braucht zum Kompilieren
`dev.yumi.commons:yumi-commons-event` von Maven Central). Propwash kompiliert nur dagegen (`compileOnly` in
`build-logic/.../multiloader-common.gradle`), liefert nichts davon aus und läuft ohne die Mod.

- Einstieg: `compat.lambdynamiclights.PropwashDynamicLights implements DynamicLightsInitializer`, eingetragen als
  Entrypoint `lambdynlights:initializer` in `fabric.mod.json` und in `neoforge.mods.toml` unter
  `[modproperties."propwash"."yumi:entrypoints"]`. Ohne die Mod lädt niemand die Klasse.
- Registriert einen eigenen Lichtwert-Typ `propwash:drone_leds` (`DroneLedLuminance`) und meldet ihn für
  `propwash:drone` an. LambDynamicLights fragt ihn pro Tick je Drohne ab: `DroneLeds.luminance` = 0 ohne Streifen oder
  Strom, sonst `round(12 × LED-Stärke)` (Blinken blinkt auch in der Welt).
- `LambDynamicLightsCompat.present()` (Mod-Ids `lambdynlights`/`lambdynamiclights` oder Einstieg gelaufen) steuert den
  Hinweis im LED-Tab.
- Folgearbeit: ein zusätzlicher `DynamicLightBehavior` als Lichtstrahl vor der Drohne (die API hat `LineLightBehavior`)
  würde auch in der Welt einen Kegel statt eines Punktlichts ergeben.

## Nicht umgesetzt / offen

- Die LED-Leistung am Flugakku bleibt der feste Mittelwert 1,5 W aus `AccessoryType`, auch mit „LEDs aus“;
  helligkeits- oder schalterabhängig bräuchte sie einen Eingang im Flugmodell (gegenüber rund 100 W im Schwebeflug
  vernachlässigbar).
- Das Funk-HUD im Sichtflug zeigt „LEDs aus“ nicht an; dort sieht man die Drohne selbst.
- Der Kegel folgt der Kameraachse, nicht der echten Abstrahlrichtung des Streifens (30° schräg nach unten vorn).
- NeoForge mit Stencil-Tiefe (`D24_UNORM_S8_UINT`, nur wenn eine andere Mod Stencil anfordert) unter dem Vulkan-Backend
  ist ungetestet; ein Fehler im Pass oder eine fehlgeschlagene Bildprüfung schaltet auf die Overlay-Aufhellung.
- Die Bildprüfung sieht nur Abdunklung. Ein Treiber, der das Bild zu hell oder verfärbt ausgibt, fällt ihr nicht auf;
  dafür gibt es den persönlichen Schalter.
