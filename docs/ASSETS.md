# Asset-Vertrag

Verbindlich für die Module assets-parts, assets-world, drone-render, race und content. Wer Modelle erzeugt,
hält sich an die IDs und Maße; wer sie rendert, verlässt sich nur auf diese Datei — keine direkte Abhängigkeit
zwischen den Agenten.

## Maßstab

- Teil-Modelle sind Blockbench-kompatible JSON-Modelle (Java Block/Item). **1 Modell-Einheit = 8 mm echte Größe.**
  Beispiel: 5"-Prop (130 mm) = 16,25 Einheiten Durchmesser, 2207-Motor (28 mm) = 3,5 Einheiten.
- Jedes Teil ist um seinen **Anbaupunkt** zentriert: der Anbaupunkt liegt bei `(8, 8, 8)` (Modellmitte). Was „oben“
  ist, zeigt nach +Y, die Flugrichtung (Nase) nach −Z (Blockbench „Norden“).
  - Rahmen: Anbaupunkt = Mitte der Oberseite der Bodenplatte.
  - Motor: Mitte der Unterseite (Montagefläche).
  - Prop: Nabenmitte (Unterseite der Nabe).
  - Stack: Mitte der Unterseite.
  - Akku: Mitte der Unterseite; die hängende Variante `<key>_under` (Akku unter dem Rahmen): Mitte der Oberseite.
  - Video-Einheit: Mitte der Kamera-Befestigung (Kamera zeigt nach −Z).
  - Zubehör oben (Action-Cams) und der Sockel `mount_top`: Drehachse des Kameragelenks (Achse entlang X), siehe „Zubehör“.
  - Zubehör unten (LED-Streifen): Mitte der Oberseite des Streifens.
- Der Renderer skaliert `8 mm / 62,5 mm = 0,128` pro Einheit (16 Einheiten = 1 Block) und zusätzlich mit
  der Drohnengröße `DroneRenderConfig.visualScale` (drone-render, Standard 1,0 = echte Größe, einstellbar bis 3,0; Umrechnung in
  `DroneVisualScale`), damit Drohnen im Spiel auf Wunsch besser sichtbar sind.
- Texturen dürfen 32×32 oder 64×64 groß sein (Modell-JSON mit `texture_size`).

## IDs

| Teil | Modell (für den Drohnen-Renderer) | Item-Modell-Definition |
|---|---|---|
| Rahmen | `propwash:part/frame/<key>` | `assets/propwash/items/<key>.json` |
| Stack | `propwash:part/stack/<key>` | dito |
| Motor | `propwash:part/motor/<key>` | dito |
| Prop | `propwash:part/prop/<key>` (Drehrichtung cw), `propwash:part/prop/<key>_ccw` (gespiegelte Blattsteigung für ccw-Motoren), `propwash:part/prop/<key>_blur` (Scheibe für hohe Drehzahl: 20–30 % deckend, zur Mitte etwas dichter, Farbe zu 55 % entsättigt, ohne Randring und Linien – `BLUR_ALPHA`, `BLUR_SATURATION`; Rahmen und Motoren bleiben lesbar) sowie die Schadensstufen `<key>_worn`/`<key>_worn_ccw` (angeknackst: Spitzen 4–10 % gekürzt und an der Vorderkante abgesplittert, Kerben in den Blättern), `<key>_damaged`/`<key>_damaged_ccw` (ein Blatt verliert fast die Hälfte, die anderen angeknackst) und `<key>_broken`/`<key>_broken_ccw` (ein Blatt an der Nabe abgebrochen, eines um 32° nach unten verbogen, das dritte stark gekürzt); Bruch- und Knickkanten hell (`prop_break`), Schäden je Blatt in `BLADE_DAMAGE` (`tools/assets/parts/props.py`) | dito; das Item-Symbol wählt die Stufe über `minecraft:range_dispatch` auf `propwash:prop_damage` (0 neu, 1 angeknackst, 2 beschädigt, 3 kaputt, aus `propwash:part_condition`); Schadensstufen zusätzlich als `items/drone_render/prop/<key>_<stufe>[_ccw].json` |
| Akku | `propwash:part/battery/<key>`; Akkus mit Riemen zusätzlich `propwash:part/battery/<key>_under` (hängend unter dem Rahmen, Abschnitt „Akku unter dem Rahmen“) | dito; `_under` nur als `items/drone_render/battery/<key>_under.json` samt `_under_paint`, kein Inventar-Item |
| Video | `propwash:part/video/<key>` | dito |
| Zubehör | `propwash:part/accessory/<key>` (`led_strip`, `action_cam_light`, `action_cam_heavy`, `action_cam_flashback`) und der Kamera-Sockel `propwash:part/accessory/mount_top` (kein Item) | dito (nicht für `mount_top`) |

`<key>` = der stabile `key()` des eingebauten Bauteils (z. B. `freestyle_5`, `m2207_1750`, `p51_3b`). Item-IDs der Teile sind
ebenfalls `propwash:<key>`. Das Drohnen-Item `propwash:drone` zeichnet seinen Bauplan über den Sonder-Renderer
`propwash:drone_build` (`items/drone.json`, Typ `minecraft:special`, im Item-Rahmen mit `"wall": true`, Abschnitt
„Item-Rahmen und Regal“, am Boden mit `"ground": true` und der Basis `propwash:item/drone_ground`, Abschnitt
„Größenregel der Items“); das Icon-Modell `propwash:item/drone` (Freestyle-Drohne) liefert nur noch die
Anzeige-Transformationen (GUI, Hand, Rahmen, Regal, Kopf) und das Partikel.

Die drei X-Rahmen tragen farbige TPU-Armschützer an den Armspitzen (`arm_guard`) und Motorpads mit Schrauben in derselben
Farbe, damit ihre Item-Icons unterscheidbar sind: `freestyle_5` rot (passend zu den roten Abstandshaltern), `race_5` blau,
`long_range_7` orange (`tools/assets/parts/frames.py`, Parameter `guard` von `x_arms`).

Welt-Assets (assets-world): `propwash:block/drone_workbench`, Brillen `propwash:goggles_analog|digital|creative`
(flaches Icon + 3D-Modell `propwash:item/goggles_<art>_worn` für den Kopf), Gates `propwash:gate/<size>_frame` und
`propwash:gate/<size>_lights` (`size` = `small|medium|large`, Lichter weiß texturiert, Einfärbung grün/weiß per Tint
im Renderer), Kurs-Werkzeug `propwash:course_tool`, GUI `propwash:textures/gui/drone_workbench.png`,
Slot-Symbole `propwash:gui/slot/<category>` (Sprites, `category` = `frame|stack|motor|prop|video|battery|accessory_top|accessory_bottom`),
Mod-Icon `assets/propwash/icon.png` (Loader-Metadaten) und `pack.png`, Funke `propwash:remote` (Item-Modelle
`propwash:item/remote` und `propwash:item/remote_body`, Knüppel-Textur `propwash:textures/entity/remote/gimbal.png`),
Ladegerät `propwash:battery_charger` (`propwash:block/battery_charger_<idle|charging|done>`), GUIs
`propwash:textures/gui/battery_charger.png` und `propwash:textures/gui/drone_service.png`.

## Lackierung: Lack-Varianten der Teilmodelle

`tools/assets/parts/paintable.py` (Teil von `generate.py`, geprüft von `validate.py`, Vorschau `paint_preview.py` →
`docs/asset-sheets/paint_preview.png`) erzeugt zu jedem Teilmodell mit lackierbaren Flächen eine Variante
`propwash:part/<kategorie>/<key>_paint` mit eigener Textur `item/part/<kategorie>/<key>_paint` (bei ccw-Props mit gleicher Textur und bei der
Blur-Nabe die Textur der cw-Variante) und `items/drone_render/<kategorie>/<key>_paint.json`. Die **Originalmodelle bleiben unverändert und
ohne Tint** (JUnit `PaintAssetsTest`, `validate.py`). In der Variante bekommt jede lackierbare Fläche `tintindex` = Nummer ihrer Tint-Ebene;
die Textur dieser Flächen ist in Graustufen umgerechnet (Helligkeit relativ zur Grundfarbe des Materials, Grundfarbe = Referenz-Grau des
Kanals, Lichter mit weichem Knie), sodass Tint × Textur mit dem Standard-Tint wieder die Originalfarbe ergibt. Welche Materialien welcher
Kanal sind, steht in `paintable.MATERIALS` (Kategorie, Material) und für Sheets in `sheet_rule`:

| Kanal | Flächen |
|---|---|
| `frame` | `carbon` (Platten, Arme, Motorpads, Kamerakäfig), Whoop `black_matte` |
| `tpu` | `tpu_*` (Armschützer, Heckhalter, Whoop-Canopy, Cinewhoop-Ducts und -Ring), eloxierte Abstandshalter `alu_*`, Kamerasockel `mount_top` |
| `motor` | Glockenmantel und -deckel (`motor_bell_*`, Sheet `bell_top`) |
| `prop` | Blätter, Nabe, Nabendeckel, Blur-Scheibe (auch `_ccw`, `_worn`, `_damaged`, `_broken`) |
| `stack` | Platinen-Oberseiten (Sheets `fc`, `esc`, `pcb`) |
| `battery` | Hülle (`wrap_*`, Li-Ion `cell_wrap`) |
| `camera` | Kameragehäuse `cam_body` und Blende `cam_bezel_*` |
| `antenna` | Antennenspitzen `antenna_red`, `antenna_white` |
| `accessory` | LED-Profil, Kühlkörper der leichten und Gehäuse der schweren Action-Cam, Gehäuse der Action-Cam (Flashback) |

Mehrere Grundfarben im selben Kanal (z. B. TPU rot und rote Abstandshalter) sind eigene Tint-Ebenen; lackiert bekommen alle dieselbe Farbe,
ungesetzt ihre eigene. Referenz-Grau je Kanal in `paintable.CHANNELS` = `build.PaintChannel`; in Modellen mit mehreren Kanälen darf keine
Grundfarbe darüber liegen (`check_references`, sonst bricht `generate.py` ab). `drone_paint.json` listet je Modell die Tint-Ebenen
(`channel`, `base`) für den Renderer. `propwash:item/drone_paint` (Tint 0–11 = Lack-Platz, weitere Ebenen fest) wird weiter erzeugt,
das Drohnen-Item benutzt es aber nicht mehr: `items/drone.json` ist ein `minecraft:select` auf `minecraft:display_context`,
Fall `fixed` → `{"type": "minecraft:special", "base": "propwash:item/drone", "model": {"type": "propwash:drone_build",
"wall": true}}`, sonst dasselbe ohne `wall` (von `generate.py` geschrieben, `validate.py` prüft Auswahl und Typ), Lackierung,
Zubehör und Prop-Schäden zeichnet der Renderer aus dem Bauplan.

## Anbaupunkte je Rahmen: `assets/propwash/drone_layout.json`

Von assets-parts erzeugt, von drone-render per Resource-Reload gelesen. Alle Werte in **Millimetern**, relativ zum
Rahmen-Anbaupunkt; X nach rechts, Y nach oben, Z nach hinten (Nase = −Z).

```json
{
  "frames": {
    "freestyle_5": {
      "motors": [[-79.5, 3.0, -79.5], [79.5, 3.0, -79.5], [-79.5, 3.0, 79.5], [79.5, 3.0, 79.5]],
      "motor_spin": ["cw", "ccw", "ccw", "cw"],
      "prop_height": 16.0,
      "stack": [0.0, 3.0, 0.0],
      "battery": [0.0, 32.0, 6.0],
      "video": [0.0, 12.0, -48.0],
      "camera_tilt_default": 25.0,
      "accessories": {
        "top": {"position": [0.0, 38.5, -62.5], "tilt": 20.0},
        "bottom": {"position": [0.0, -3.0, -56.0], "tilt": 30.0, "length": 34.0}
      }
    }
  },
  "motor_seat": {"m0802_22000": 7.0, "m1404_4600": 12.5, "m2004_3000": 13.5, "m2207_1750": 18.0, "m2306_1860": 18.0, "m2807_1300": 19.5}
}
```

- `motors`: Motor-Anbaupunkte (Reihenfolge vorne links, vorne rechts, hinten links, hinten rechts). Die Diagonale ist
  immer der Radstand; `race_5` ist ein gestreckter X-Rahmen (`stretch` 1,2 in `tools/assets/parts/layout.py`: ±68,8 mm
  seitlich, ±82,6 mm längs), alle anderen Rahmen sind quadratisch.
- `prop_height`: Höhe der Prop-Nabe über dem Motor-Anbaupunkt für den Vorlagenmotor des Rahmens.
- `motor_seat`: Sitzhöhe der Props je Motor; mit einem anderen Motor verschiebt der Renderer die Nabe um
  `motor_seat[Motor] − motor_seat[Vorlagenmotor]` (z. B. 2207 am 7-Zöller 1,5 mm tiefer). Seit 0.4.1 liest auch die
  Analyse „Props im Bild“ (`sim.FpvLayoutData`) `motor_seat` und `frames.*.motors/prop_height` aus
  `assets/<namensraum>/drone_layout.json` der Mod-Dateien (Client und Server gleich, ohne Ressourcenpakete); ohne Eintrag
  gilt Statorhöhe + 9 mm.
- `motor_spin`: Drehrichtung je Motor („Props in“ als Standard).
- `battery`: Akku oben (`battery.on_top` der Teile-Definition): Mitte der Unterseite des Packs; Akku unter dem Rahmen
  (`on_top: false`, Whoop und Race): **Aufhängepunkt** = Mitte der Oberseite des Packs an der Unterseite von Platte und Pad
  (`race_5` (0, −2,8, 4) = Bodenplatte −2 mm + Gummipad 0,8 mm, `whoop_65` (0, −3,7, 1)). Welche Seite gilt, entscheidet
  immer die Teile-Definition wie im Flugmodell, nicht das Vorzeichen in dieser Datei.
- `accessories`: Anbaupunkte der Zubehör-Halterungen, genau die Halterungen aus `FrameType#accessoryMounts` (Whoop nur `bottom`).
  `position` = Anbaupunkt (oben: Drehachse des Kameragelenks, 9–15 mm über dem Kamerakäfig vor dem Akku; unten: Oberseite des Streifens),
  `tilt` = Neigung um X in Grad (−45…90, positiv = Vorderkante hoch; unten 30° = LEDs strahlen schräg nach vorn unten, beim Whoop 90° =
  nach vorn), `length` = Streifenlänge in mm (nur unten; der Renderer streckt das Modell entlang X auf diese Länge). Die Werte stehen
  zugleich in `build.AccessoryAnchor` (Physik); JUnit prüft, dass beide gleich sind. Quelle: `tools/assets/parts/layout.py`.
- Einträge für jeden eingebauten Rahmen sind Pflicht; Rahmen anderer Namensräume brauchen keinen Eintrag (Layout aus der
  Definition, siehe „Teile aus anderen Namensräumen“).

## Teile aus anderen Namensräumen (Parts-API 0.4.0, drone-render)

Vertrag: `docs/design/parts-api.md` Abschnitt 10. Ein Teil `ns:pfad` der Kategorie `k`
(`frame|stack|motor|prop|video|battery|accessory`) bringt seine Modelle im eigenen Namensraum mit. Fehlt ein Modell,
zeichnet Propwash die **prozedurale Form aus der Definition** – ein Addon funktioniert also auch ganz ohne Modelle.
Eingebaute Teile benutzen weiter genau ihre bisherigen Dateien.

### Dateien je Teil

| Datei | Zweck | Pflicht |
|---|---|---|
| `assets/ns/items/drone_render/k/<pfad>.json` | Render-Definition an der Drohne (`{"model": {"type": "minecraft:model", "model": "ns:part/k/<pfad>"}}`) | nein |
| `assets/ns/models/part/k/<pfad>.json` + Textur `assets/ns/textures/item/part/k/<pfad>.png` | das Modell (Maßstab, Anbaupunkt und Achsen wie oben unter „Maßstab“) | nein |
| Props zusätzlich `<pfad>_ccw`, `<pfad>_blur`, `<pfad>_worn`, `<pfad>_damaged`, `<pfad>_broken` (Schäden auch `_ccw`) | Drehrichtung, Scheibe, Schadensstufen | nein (fehlt `_ccw` → cw-Modell, fehlt `_blur` → prozedurale Scheibe, fehlt eine Schadensstufe → unbeschädigt) |
| Akkus mit Riemen zusätzlich `<pfad>_under` (+ `_under_paint`) | hängende Variante für Rahmen mit dem Akku unten (Pack von y = −Höhe bis 0, Riemen um den Boden und bis 3,3 mm über den Anbaupunkt durch die Platte, Schnalle unten) | nein (fehlt sie, hängt Propwash das normale Modell mit seiner Oberkante an den Aufhängepunkt – bei Packs ohne Riemen sieht das gleich aus) |
| `<modell>_paint` (Render-Definition mit `tints`) + Eintrag in `assets/ns/drone_paint.json` | Lack-Variante | nein (ohne Variante bleibt ein Teil **mit Modell** in Originalfarbe; Teile ohne Modell färbt Propwash selbst, unten) |
| `assets/ns/items/<pfad>.json` | Inventar-Symbol | ja (entweder eigenes Modell oder Sonder-Renderer `propwash:part`, unten) |

- Ein Modell gilt als vorhanden, wenn Render-Definition **und** Modell-Datei existieren; der Resource-Reload prüft alle
  angemeldeten Teile samt Varianten (`PartModel.known()`), meldet fehlende eingebaute Modelle als Warnung und fremde nur als
  Zählung je Namensraum (Info). Nicht angemeldete Teile (`PartIndex.detached`, Vorschauen, Selbsttests) prüft
  `DroneRenderResources#hasModel` beim ersten Zugriff.
- `visual.render_model` (Definition) ersetzt die Render-Definition: `"propwash:drone_render/motor/m2207_1750"` benutzt das
  eingebaute Modell samt Varianten und Lack; eine beliebige Id `ns:weg/name` benutzt `assets/ns/items/weg/name.json`
  (Varianten hängen ihr Suffix an, z. B. `name_ccw`), dann wird nur die Render-Definition geprüft.
- `PartModel` (drone-render) bildet die Ids dynamisch: `ns:drone_render/k/<pfad>`, Name `ns:k/<pfad>` (für `propwash`
  ohne Namensraum, wie bisher `k/<pfad>`); `PartModel.all()` enthält nur die mitgelieferten Propwash-Modelle.
- Platzhalter für fehlende Teile (Addon entfernt) zeichnen immer die prozedurale Form ihres Ersatzteils.

### `assets/ns/drone_layout.json` und `drone_paint.json` je Namensraum

Beide Dateien werden in **jedem** Namensraum gelesen und zusammengeführt, `propwash` zuerst, dann die übrigen alphabetisch;
spätere Einträge ersetzen frühere für denselben Rahmen, Motor oder dasselbe Modell. Schlüssel ohne `:` meinen den eigenen
Namensraum, mit `:` eine volle Id – ein Ressourcenpaket oder Addon kann so auch fremde Rahmen optisch anpassen
(`"propwash:freestyle_5": {…}`). Das Flugmodell liest diese Dateien nie.

- `drone_layout.json` hat das Schema von oben. Für Rahmen anderer Namensräume ist jedes Feld optional, auch `motors`:
  fehlende Werte kommen aus der Definition, fehlende Zubehör-Halterungen aus deren Ankern. Eingebaute Rahmen brauchen
  weiterhin vier `motors`. In anderen Namensräumen darf `frames` ganz fehlen (Datei nur für `motor_seat`).
- `motor_seat` gilt auch für fremde Motoren (`{"m2806_1300": 16.1}`).
- Höhe der Prop-Nabe: `prop_height` des Rahmens (mit `motor_seat`-Ausgleich wie bisher) → sonst `motor_seat` des
  verbauten Motors → sonst Oberkante des Motor-Modells + 0,5 mm → sonst Glockenhöhe + 3 mm (prozedural).
- `drone_paint.json`: `{"models": {"k/<pfad>": [{"channel": "frame", "base": "#1b1d20"}, …]}}`, Kanäle und
  Referenz-Grau wie unter „Lackierung“; der Schlüssel entspricht `PartModel#name()`.

### Layout aus der Definition (`DroneLayout.fromDefinition`)

Gilt für jeden Rahmen ohne Eintrag in einer `drone_layout.json` (eingebaute Rahmen: nur, wenn die Datei fehlt – JUnit
`LayoutFromDefinitionTest` belegt, dass dann exakt die bisherigen Ersatzwerte herauskommen).

| Layout | Quelle |
|---|---|
| Motoren, Drehrichtung | `arms` (vorne links, vorne rechts, hinten links, hinten rechts; `spin`); ohne `arms` True-X aus dem Radstand auf Höhe 3/2,5/1,5 mm (Radstand ≥ 200/≥ 100/kleiner), „Props in“ |
| Stack | Mitte, Höhe der Motorauflage (mit `arms`: mittlere Arm-Höhe, mindestens 0) |
| Akku | oben: `battery.plate_mm` + 2 mm, 4 % des Radstands nach hinten; unten: hängt mit der Oberseite an der Platte (Packhöhe aus `pack_mm`, Modell oder Dichte) |
| Video | `camera.pos` + 10 mm nach hinten (Mitte der Kamera hinter der Linse), sonst (0, `electronics_mm`, −0,21 × Radstand) |
| Kamera-Uptilt | `camera.tilt_deg` (eingebaute: 12/10/28/38/20° = Werte der `drone_layout.json`; `drone.CameraMount` liest ihn ebenfalls aus der Definition; der Winkel der Drohne steht im Tuning und ist im Wartungsmenü einstellbar) |
| Zubehör | `accessory_anchors`, sonst Ersatzformel `AccessoryAnchor.fallback` |
| Prop-Höhe | eingebaute Rahmen: Glockenhöhe + 3 mm des Vorlagenmotors; fremde: je Motor (siehe oben) |

### Prozedurale Formen (`FallbackShapes`)

| Teil | Aus der Definition |
|---|---|
| Rahmen | Arme vom Zentrum zu jedem Motor (Breite 6 % des Radstands, 4–36 mm; Dicke 1,5/2,5/3 mm, ab 600 mm Radstand 6 mm), Motorpads (Radius 7 %, höchstens 26 mm); `h_frame`: zwei Längsholme durch die Motoren jeder Seite und ein Querträger statt Diagonalarmen; Ducts (`duct` vorhanden): Ringe mit Radius größter Prop / 2 + 1,5 mm um jeden Motor; Boden- und Deckplatte, Abstandshalter und Kamerakäfig mit der „Körperspanne“ (Radstand bis 300 mm, darüber 300 + 0,3 × Rest, damit X-Class-Rümpfe nicht riesig werden; Käfig höchstens ±30 mm) |
| Motor | Statormaße (`stator_mm` bzw. aus dem Label), Glocke Stator + 5 mm, Farbe `visual.accent` |
| Prop | Durchmesser, Blattzahl, Drehrichtung; Farbe `visual.accent`, sonst nach Material (Polycarbonat hell, Glasfaser-Nylon grau, Carbon-Nylon/Carbon dunkel) |
| Akku | `pack_mm` (sonst Masse/Dichte wie bisher), Farbe `visual.accent` (eingebaute: bisherige Farben) |
| Stack | Platinengröße `board_size_mm` (sonst aus der Halterung), `all_in_one` → eine Platine, sonst Turm aus ESC und FC |
| Video | Kamera `camera_width_mm`; Sender digital groß ab 15 g oder > 600 mW, analog klein bis 13 mm Kamerabreite |
| Zubehör | Maße, LEDs, Linse wie bisher `AccessoryType` |

JUnit `AddonShapesTest`: Deadcat, Stretched-X, Squashed-X, H-Frame, Duct-Cinelifter und X-Class 13″ ohne NaN, innerhalb der
Prop-Kreise, Arme bis zu den Motoren; eingebaute Formen unverändert.

Lack (seit Parts-API 1.4): Die prozeduralen Formen nehmen die Lackierung an. `DroneVisuals.paintShape` ersetzt die
Grundfarbe der Form je Lack-Kanal (Rahmen: Carbon bzw. Whoop-Kunststoff, `tpu`: Duct-Kunststoff, Motoren: Akzentfarbe,
Props je Position: Prop-Farbe, Akku, Kamera); andere Farben der Form (Platinen, Linse, Schrauben) bleiben. Die
umgefärbten Formen liegen in `PaintedShapes` (höchstens 512). Selbsttest „drone-render: Lackierung erreicht Teile mit
Form aus der Definition“ (Stretched-X-Rahmen blau, X-Class des Beispiels mit gelben Front-Props).

### Inventar-Symbol ohne eigenes Modell: Sonder-Renderer `propwash:part`

```json
{"model": {"type": "minecraft:special", "base": "propwash:item/part_base", "model": {"type": "propwash:part"}}}
```

- Zeichnet das Drohnen-Modell des Teils (`PartModel`, Props mit der Schadensstufe aus `propwash:part_condition`) oder
  dessen prozedurale Form. Welches Teil: `"part": "ns:pfad"` im Renderer-Objekt (fest), sonst die Komponente
  `propwash:part` des Stacks, sonst die Item-Id.
- Größe: das Teil wird so skaliert, dass seine Hülle in der GUI-Ansicht (45°/225°, Rahmen und Video um −20° um die
  Hochachse gedreht, also 45°/205° wie die eingebauten Whoop-Rahmen) genau einen Block füllt, und mittig gesetzt;
  `propwash:item/part_base` liefert die Anzeige-Transformationen (GUI 45°/225°, 0,94 ≈ 15 px, Boden, Rahmen, Regal, Hand,
  Kopf) und das Partikel. `base` darf durch ein eigenes Modell mit anderen `display`-Werten ersetzt werden. Die Drehung
  um die Hochachse gilt in jeder Ansicht (am Boden liegt das Teil trotzdem flach).
- Kategorie-Basismodelle (Parts-API 1.4): `propwash:item/part_base/<kategorie>` (`frame`, `stack`, `motor`, `prop`,
  `video`, `battery`, `accessory`) übernehmen die Hand-, Boden- und Rahmen-Größen der eingebauten Teile dieser Kategorie
  (`tools/assets/parts/part_bases.py` liest sie aus den eingebauten Item-Modellen); das Beispiel-Addon benutzt sie.
- Lesbarkeit: Symbole ohne Modell benutzen eigene Formen (`FallbackShapes.frameIcon`, `propIcon`): Rahmen-Arme und
  Motorpads mindestens 7,5 % des Radstands breit, Arme mindestens 1,2 % des Radstands dick (X-Class sonst 1 Pixel),
  Prop-Blätter 30 % breiter. An der Drohne, in der Werkbank-Vorschau und im Geist bleibt die maßstabsgetreue Form.
- Symbole werden je Teil und Schadensstufe zwischengespeichert (neu nach Resource-Reload).
- Item-Rahmen: `"wall": true` legt das Teil flach an die Wand wie die Rahmen- und Prop-Modelle (Abschnitt „Item-Rahmen und
  Regal“): Fußkreis = halbe Diagonale der Hülle in Draufsicht auf 0,5 Modell-Blöcke, Unterseite in der Modellmitte. Dazu
  gehört das Basismodell `propwash:item/part_wall` (Rahmen-Anzeige −90°/180°/0°, Skala 1,5, 0,5 px vor der Rückwand).
- Regal, Kopf und Item-Rahmen: `"box": true` passt das ganze Teil (längste Kante der Hülle) in einen Block statt der
  GUI-Ansicht; die Basis setzt dann dieselbe Größe wie bei den eingebauten Teilen (Abschnitt „Größenregel der Items“).
  Ohne `"box"` darf ein langes Teil (Akku, Zweiblatt) dort um bis zu etwa die Hälfte größer werden.
- Boden: `"ground": true` wie `"box"`, zusätzlich höchstens das Würfelmaß 0,3 Block: Skala höchstens
  `GROUND_BOX_BULK` (0,3 / 0,44 = 0,68) geteilt durch die Kubikwurzel des Hüllen-Volumens, 0,44 ist die Boden-Skala der
  Basis `part_base/<kategorie>` (JUnit prüft, dass keine Basis größer ist). Sperrige Teile (Motor, Action-Cam) liegen so
  wie bei den eingebauten Teilen nicht größer als ein Würfel mit 0,3 Block Kante am Boden.
- Hände: `"hand": true` setzt das Teil auf seine Größe in der Hand wie bei den Teilen mit Modell (Abschnitt
  „Größenregel der Items“, „Teile in der Hand“): die Vorderansicht der Hand-Drehung (10°/±45°, Motoren und Props als
  Zylinder gemessen) wird auf den Handfaktor f der längsten Kante gebracht, die Basis macht daraus 9 bzw. 7 px × f. Ohne
  `"hand"` bleibt ein Symbol in der Hand so groß wie vor 0.5.0 (GUI-Anpassung, unabhängig von der echten Größe). In der
  dritten Person bleibt der Versatz der Basis, kleine Symbole sitzen dort etwas vor der Hand.
- Der Generator (`addon.py --icons special`) schreibt ein `select` auf `minecraft:display_context`: Rahmen und Props im
  Fall `fixed` → `part_wall` mit `"wall": true`; `ground` → `part_base/<kategorie>` mit `"ground": true`; `on_shelf`,
  `head` (und `fixed` bei allen übrigen Kategorien) → `part_base/<kategorie>` mit `"box": true`; beide Hände in erster
  und dritter Person → `part_base/<kategorie>` mit `"hand": true` (Akkus nur in der dritten Person, in der ersten behalten
  sie ihre Sondergröße); sonst (GUI) `part_base/<kategorie>` ohne Zusatz.

### Drohnen-Item, Geist, Flugschule, Werkbank

Alle zeichnen dieselbe Baugruppe (`DroneAssembly`) und funktionieren damit mit jedem Bauplan. Drohnen-Items bleiben
zwischen 72 und 100 % der Freestyle-Größe; in Flugschul-Szenen werden Drohnen, deren Hülle mehr als 1,6 × so groß ist wie
die der Freestyle-Drohne, auf diese Größe verkleinert (`DroneVisuals.sceneFit`, X-Class). Werkbank-Vorschau und
Lackierung passen jede Hülle ein. Wird ein Drohnen-Item im Slot unter die halbe Größe verkleinert (X-Class), zeichnet
es prozedurale Rahmen und Props in der Symbolform (`frameIcon`, `propWide`: Blätter 30 % breiter), sonst wären Arme und
Props nur ein Pixel breit. `PropwashClientApi.renderPreview(…, pixelsPerMillimetre)` zeichnet ohne Einpassen im festen
Maßstab (`DronePreview.renderAtScale`).

Große Modelle: Render-Definitionen dürfen die Vanilla-`transformation` benutzen (Propwash wendet sie über
`ItemModel.update` an); ein verkleinertes X-Class-Modell erscheint so in echter Größe. Die Hülle eines Teils wird erst
ab ±8 Modell-Blöcken (±1024 mm) gekappt (`VisualHull.clampPartBounds`).

### Item-Rahmen und Regal

Im Item-Rahmen (`fixed`, auch Leuchtrahmen) hängen Drohnen, Rahmen-Teile und Props wie ein Bild an der Wand: flach, die
Oberseite (Kamera, Props) zeigt zum Betrachter, die Nase nach oben. Die acht Drehstufen des Rahmens drehen sie in der
Wandebene, am Boden und an der Decke liegen sie flach im Rahmen. Stacks und AIOs hängen ebenfalls flach (−90°/180°/0°),
aber in normaler Teilegröße (12 px), Akkus und Videomodule liegen quer (0°/90°/0°: Akku mit Aufschrift, Kamera im Profil),
Motoren und Zubehör behalten 0°/180°/0° (Front zeigt aus dem Rahmen). Vorher zeigten Akkus, Stacks und Video die
Stirnseite und ragten mit der Länge bis zu 1,5 Blöcke aus der Wand (Abschnitt „Größenregel der Items“).

**Größenregel (Fußkreis):** Maß ist der Fußkreis, der kleinste Kreis um die Mitte der Hülle in Draufsicht, der alles
enthält (Props als volle Scheibe mit ihrem Durchmesser, bei Prop-Modellen auch alle Schadensstufen). Weil der Kreis um den
Drehpunkt des Item-Rahmens liegt, bleibt das Teil in jeder der acht Drehstufen gleich groß und mittig.
- Drohnen-Item (`DroneItemRenderer`, `"wall": true`): Fußkreis-Durchmesser = Passfaktor × `WALL_DIAMETER` (1 Modell-Block);
  der Passfaktor ist derselbe wie im Inventar, `(Fußkreis / Fußkreis der Freestyle-Drohne)^0,3`, begrenzt auf 72–100 %.
  Die Anzeige `fixed` aus `propwash:item/drone` (−90°/180°/0°, Skala 1,5, Versatz 0/0/−0,5 px) und die Item-Rahmen-Skala
  0,5 machen aus einem Modell-Block 12 Pixel, das Außenmaß des Item-Rahmens. Ab 5″ (Freestyle, Long Range 7″, Deadcat,
  X-Class) füllen Drohnen den Rahmen bis zu den Prop-Spitzen, kleinere schrumpfen wie im Inventar (Whoop 65 mm 72 %,
  Cinewhoop 3″ 91 %, Race 5″ 99 %; Just More Parts: Whoop 75/85 mit Ducts 76/78 %, Cinewhoop 3,5″ 95 %, Long Range 4″
  93 %). In den Ecken bleibt Luft, gedreht um 45° reichen die Prop-Spitzen an die
  Rahmenkante.
- Rahmen-Teile und Props (`kit.compute_display`, Kategorien in `WALL_CATEGORIES`): Fußkreis 24 Item-Pixel
  (`WALL_DIAMETER_PX`), also wie die Inventar-Symbole immer randvoll; Drehung `WALL_ROTATION` −90°/180°/0°. Liegt die nötige
  Skala über 3,9, wandert der Rest wie bei den übrigen Ansichten in die Vor-Skalierung der Item-Definition (GUI, Hand,
  Boden und Kopf bleiben netto gleich groß).
- Abstand: die Unterseite liegt 0,5 Item-Pixel (`WALL_LIFT_PX`) vor der Rückwand des Item-Rahmens, nichts ragt hinein.
- Regal (`on_shelf`): Drohnen-Item, Rahmen und Props sind um 30° zum Betrachter geneigt (`SHELF_ROTATION`), flach liegend
  zeigten sie von vorn nur eine Kante; das Drohnen-Item füllt dabei 16 px (`CATEGORY_TARGET_PX`), die Teile wie bisher
  10 px. Stacks sind ebenso geneigt, Akkus und Video liegen quer (0°/90°/0°), Motoren und Zubehör bleiben bei 0°/180°/0°;
  alle Teile messen im Regal die längste Kante, damit nichts durch die Regalrückwand ragt.
- Prüfung: JUnit `ItemFrameDisplayTest` (Auswahl in `items/drone.json`, Drehung, Fußkreis, Abstand und Regal-Neigung aller
  Rahmen- und Prop-Modelle), `validate.py` und der Selbsttest „drone-render: Drohnen und Rahmen hängen im Item-Rahmen an
  der Wand“ (alle Vorlagen und Addon-Rahmen: Wand-Renderer nur im Rahmen, Fußkreis 72–100 %, Whoop kleiner als Long
  Range, Modell-Box innerhalb des Rahmens und vor der Rückwand; Rahmen und Props ebenso).

### Größenregel der Items

Jedes Item hat in jeder Ansicht eine Obergrenze, gemessen am Begrenzungsquader der gezeichneten Geometrie nach der
Anzeige-Transformation (1 = ein Block, in der GUI ein Slot). Die Werte stehen in `content.client.ItemSizeRule`; Vergleich
sind Vanilla-Items:

| Kontext | Ansicht (Breite, Höhe) | Tiefe | Vanilla-Bezug |
|---|---|---|---|
| `ground` (Boden) | ≤ 0,5 | ≤ 0,5 | flaches Item 0,5, Block 0,25 |
| `gui` | ≤ 1,04 (16-px-Slot, höchstens gut ein halbes Pixel darüber) | – | Item 1,0, Stein 0,98 |
| `fixed` (Item-Rahmen) | ≤ 1,5 (Außenmaß des Rahmens) | ≤ 1,0 | Item 1,0, Block 0,5 |
| `on_shelf` (Regal) | ≤ 1,1 | ≤ 2,0 (Regaltiefe) | Block 1,0 |
| `firstperson_*` | ≤ 0,9; Teile ≤ 0,6 × f | ≤ 0,9; Teile ≤ 0,95 × f | Schwert 0,90, gehaltener Stein 0,57 × 0,40 |
| `thirdperson_*` | ≤ 1,2; Teile ≤ 0,47 × f | ≤ 1,2; Teile ≤ 0,95 × f | Schwert 1,18, gehaltener Stein 0,53 × 0,61 |
| `head` | ≤ 1,0 | ≤ 1,3 (Brille um den Kopf) | Item 1,0 |

Am Boden gilt zusätzlich das **Würfelmaß** (Kubikwurzel aus Breite × Höhe × Tiefe, `ItemSizeRule.GROUND_BULK`) ≤ 0,3:
ein sperriges Teil liegt höchstens so groß wie ein Würfel mit 0,3 Block Kante, ein fallengelassener Vanilla-Block hat
0,25. Flache Items (Schwert, Karotte 0,20, Rahmen, Drohnen 0,20–0,29) berührt die Grenze nicht. Toleranz 2 %,
Mindestgröße der längsten Kante am Boden 0,15, in der GUI 0,5 (sonst 0,1). In den Händen gilt für Bauteile
zusätzlich die Teil-Grenze mit dem Handfaktor f ihrer echten Größe (Abschnitt „Teile in der Hand“ unten).

- **Teile mit Modell** (`kit.compute_display`): Boden, Item-Rahmen, Regal und Kopf (`BOX_CONTEXTS`) messen die längste
  Kante des gedrehten Quaders, GUI und Hände die Vorderansicht; in den Händen darf der Quader zusätzlich höchstens 14,4 px
  (0,9 Block, `HAND_BOX_PX`) lang sein (trifft nur Zweiblatt-Props, die sonst in den Bildschirm ragen). Bis 02.10. maß
  `compute_display` überall nur die Vorderansicht. Lange Teile, deren größte Ausdehnung in der Tiefe liegt, wuchsen
  dadurch am Boden, im Rahmen, im Regal und am Kopf: Akkus (1S 300 mAh am Boden 1,41 Blöcke, 4S/6S 0,70–0,74, im Rahmen
  bis 1,5 Blöcke aus der Wand), Stacks mit XT60-Kabel (0,84), Videomodule (0,71–0,77), der Race-Rahmen (0,51).
- **Sperrige Teile am Boden** (`kit.GROUND_BULK_PX` 4,8 px = 0,3 Block): am Boden ist die Skala zusätzlich höchstens
  4,8 px geteilt durch die Kubikwurzel des Quader-Volumens. Betrifft Motoren (vorher 0,44 × 0,39–0,44 × 0,44 Block, gut
  1,7-mal so breit und fünfmal so voluminös wie ein fallengelassener Stein, größer als ein ganzer 5″-Rahmen daneben; jetzt
  Würfelmaß 0,30, Kante 0,30–0,33), Action-Cams (vorher Würfelmaß 0,35–0,37) und die analoge Nano-Kamera (0,32); alle
  übrigen Teile bleiben gleich.
- **Drohnen-Item am Boden** (`DroneItemRenderer`, `"ground": true`, Basis `propwash:item/drone_ground` ohne Skalierung):
  längste Kante des Hüllen-Quaders in Draufsicht = 0,5 Block × (Fußkreis-Radius / 600 mm)^0,2 (`groundSize`,
  `GROUND_FULL_FOOTPRINT_MM`, `GROUND_EXPONENT`), höchstens 0,5 Block. Größenklassen bleiben sichtbar (gemessen): Whoop
  65 mm 0,31, Cinewhoop 3″ 0,37, Race 5″ 0,39, Freestyle 5″ 0,39, Long Range 7″ 0,41, X-Class 13″ des Beispiel-Addons 0,49.
  Am Boden zeichnet die Drohne das echte Modell (keine Ersatzformen). Vorher galt dort der Passfaktor des Inventars
  (72–100 %): ab 5″ waren alle Drohnen gleich groß (0,45), die X-Class sogar etwas kleiner (0,44).
- **Teile in der Hand** (06.10., `kit.hand_factor`, `ItemSizeRule.handFactor`): Bis dahin bekam jedes Teil in der Hand
  dieselbe Größe, Vorderansicht 9 px in der ersten und 7 px in der dritten Person, egal ob 10-mm-Whoop-Motor oder
  13″-Prop. Ein 2207-Motor (28 mm) war damit in der ersten Person 0,56 Block breit, breiter als ein gehaltener Steinblock
  (0,40) und wuchtiger als der Kopf einer Spitzhacke, in der dritten Person (0,44) breiter als der Arm. Jetzt richtet sich
  die Größe nach der echten Größe: Zielmaß 9 bzw. 7 px × Handfaktor f, f = (L / 180 mm)^0,6, begrenzt auf 0,25–1
  (`HAND_FULL_MM`, `HAND_EXPONENT`, `HAND_MIN_FACTOR`), L = längste Kante des Modells in Millimetern (1 Modell-Pixel = 8 mm).
  Ab 180 mm (5″-Rahmen, 7″-Props, X-Class) bleibt alles wie bisher. Der Exponent 0,6 liegt zwischen der Stauchung der
  Vanilla-Items (vom Nugget bis zur Spitzhacke etwa L^0,2) und echter Größe (L^1), Baugrößen bleiben so sichtbar;
  unter etwa 18 mm (f = 0,25) bleibt ein Teil gleich groß, damit Whoop-Motoren nicht verschwinden. Die Vorderansicht wird
  für beide Hände gemessen (`kit.hand_extent`; die linke Hand dreht −45° statt 45°, Zweiblatt-Props lagen dort vorher quer
  und 0,88 Block breit), die Kappe `HAND_BOX_PX` gilt ebenfalls × f. In der dritten Person rückt der Versatz mit f an die
  Hand (`GRIP_CONTEXTS`): kleine Teile liegen in der Faust, statt davor zu schweben. Akkus behalten in der ersten Person
  ihre Sondergröße (1,6 px, Bildprüfung 30.09.), in der dritten Person folgen sie der Regel. GUI, Boden, Item-Rahmen,
  Regal und Kopf bleiben unverändert (in allen neu erzeugten Modellen ändern sich nur die Hand-Einträge).
  Breite der Vorderansicht in Blöcken, erste / dritte Person, vorher → jetzt:

  | Teil | L | erste Person | dritte Person |
  |---|---|---|---|
  | Motor 0802 (Just More Parts: 0702, 1103) | 9–14 mm | 0,56 → 0,14 | 0,44 → 0,11 |
  | Motor 2207 | 28 mm | 0,56 → 0,18 | 0,44 → 0,14 |
  | Motor 2807 | 34 mm | 0,56 → 0,21 | 0,44 → 0,16 |
  | Motor 4214 (Just More Parts) | 51 mm | 0,56 → 0,26 | 0,44 → 0,20 |
  | Nano-Kamera, Greifer | 23 mm | 0,56 → 0,16–0,17 | 0,44 → 0,13 |
  | Action-Cam (schwer) | 61 mm | 0,56 → 0,29 | 0,44 → 0,23 |
  | Prop 31 mm / 5,1″ | 28 / 115 mm | 0,56 → 0,18 / 0,43 | 0,44 → 0,14 / 0,33 |
  | Prop 7″ Zweiblatt | 169 mm | 0,46 (0,87 tief, links 0,88 breit) → 0,54 | 0,44 → 0,42 |
  | Whoop-Rahmen 65 mm | 84 mm | 0,56 → 0,35 | 0,44 → 0,28 |
  | Stack 30×30, HD-Video | 72 / 99 mm | 0,56 → 0,33 / 0,39 | 0,44 → 0,25 / 0,30 |
  | Rahmen ab 5″ | ≥ 184 mm | 0,56 (gleich) | 0,44 (gleich) |
  | Akkus | 57–193 mm | 0,10 (gleich) | 0,44 → 0,24–0,44 |

  Prüfgrenze (`ItemSizeRule.partLimit`): Ansicht ≤ 0,6 × f in der ersten und ≤ 0,47 × f in der dritten Person, Tiefe
  ≤ 0,95 × f, Toleranz 2 %; die Zielmaße 0,5625 × f und 0,4375 × f liegen 6–7 % darunter. Die alten Motoren fallen
  deutlich durch: 2207 0,56 bei einer Grenze von 0,20 (dritte Person 0,44 bei 0,16), 4214 0,56 bei 0,29 (0,44 bei 0,22),
  1103 0,56 bei 0,15 (0,44 bei 0,12). Die allgemeinen Hand-Grenzen (Schwert) gelten weiter für alle übrigen Items.
  Drohnen-Items, Brillen, Gates, Kurs-Werkzeug, Funke, Werkbank und Ladegerät sind geprüft und unverändert: Drohnen
  0,37 (Whoop) bis 0,58 (5″) in der ersten Person, wie ihr Rahmen; flache Items und Blöcke wie Vanilla.
- **Symbole ohne Modell** (`propwash:part`): `"ground": true` am Boden, `"box": true` in Regal, Kopf und (außer
  Rahmen/Props) Item-Rahmen, `"hand": true` in den Händen (außer Akku in der ersten Person), Abschnitt „Inventar-Symbol
  ohne eigenes Modell“.
- **Prüfung:** JUnit `content.client.ItemDisplaySizeTest` (alle Item-Definitionen mit Modell aus Propwash und Beispiel-
  Addon in allen Kontexten samt Würfelmaß, Teile in den Händen mit der Teil-Grenze ihrer echten Größe, linke Hand mit
  gespiegelter Drehung, Akkus am Boden und quer im Rahmen, Motoren und Action-Cams am Boden wie ein Block, Boden-Skala der
  Kategorie-Basen, Größenklassen der Drohnen; `partsInHandFollowTheirRealSize`: Vorderansicht beider Hände = 9 bzw. 7 px ×
  f ± 3 %, `motorsInHandStaySmallerThanAHeldBlock`: jeder Motor in der ersten Person ≤ 0,3, in der dritten ≤ 0,25,
  kleinster ≥ 0,12, größter mindestens 1,3 × kleinster, `oldMotorHandSizesFailTheRule`: die gemessenen alten Maße von
  1103, 2207 und 4214 fallen durch); Selbsttest
  „content: Item-Größen aller Items in allen Anzeige-Kontexten“ (geht das Item-Register aller Namensräume außer
  `minecraft` durch, also auch geladene Addons wie Just More Parts, das Drohnen-Item mit allen Presets; misst die
  tatsächlich gezeichnete Geometrie über den Submit-Weg, `ItemBounds`, geeicht an Vanilla-Stein und -Schwert; Bericht
  `propwash-item-sizes.txt` im Spielordner mit Maßen, Würfelmaß, echter Teilegröße `part_mm` und Befund `OK`,
  `ZU GROSS`, `ZU SPERRIG`, `ZU KLEIN`; Bauteile in den Händen mit `partLimit`, L aus dem Modell in echter Größe der
  Item-Definition, bei `propwash:part` aus Modell oder Form des Teils), „content: Drohnen am Boden zeigen ihre
  Größenklasse“ und „content: Motoren in der Hand kleiner als ein gehaltener Stein, nach Baugröße“ (alle Motoren aller
  Namensräume: längste Kante ≤ 0,6 × Stein im selben Kontext, ein mindestens 10 % größerer Motor nie kleiner als ein
  kleinerer). Bildschirmfotos der Hände (`HandItemsShowcase`, nur mit `-PpropwashScreenshots=true`):
  `hand_first_person_<item>.png` (Item in der rechten, Eisenspitzhacke in der linken Hand; `…_vanilla` mit Spitzhacke und
  Stein), `hand_third_person_<item>.png` (Vorderansicht, Körper um 35° gedreht, Motoren 0802, 2207 und 4214),
  `hand_lineup_<gruppe>_<n>.png` (Rüstungsständer mit Spitzhacke, Stein und bis zu fünf Items je Bild: Motoren, Props,
  Rahmen, Akkus, Elektronik, Ausrüstung, Drohnen); mit Just More Parts kommen 1103 und 4214 dazu. Bildschirmfotos der
  Boden-Items (`GroundItemsShowcase`, nur mit
  `-PpropwashScreenshots=true`): `ground_items_NN.png` (alle Items als Boden-Items, je Seite 38 plus Stein und Schwert,
  sortiert nach Propwash, Beispiel-Addon, übrigen Addons und Id; im Repository ohne fremde Addons, mit Just More Parts im
  `mods`-Ordner des Selbsttests kommen vier Seiten dazu),
  `ground_items_compare_drones.png`, `…_parts.png`, `…_batteries.png`, `…_gear.png` (Reihen neben Stein, Eisenschwert
  und Karotte aus Spielerhöhe),
  Flugschule `flight_school_build_packs.png` und `flight_school_battery_fit.png` (Akkus in den Szenen).

### Props im FPV-Bild (`FpvProps`, `FpvPropGeometry`)

Die eigenen Props (Scheibe über der Unschärfe-Schwelle, sonst die stehenden Blätter im aktuellen Winkel) erscheinen im
FPV-Bild und beim Zuschauen dort, wo sie von der Objektivlage aus zu sehen wären: Objektiv = `camera.pos` der Definition,
sonst die Objektivlage des Flugmodells (`AirframeSpec.lens*` relativ zum Schwerpunkt), Prop-Naben aus dem Layout,
echter Maßstab (nicht die Drohnengröße der Welt). Gezeichnet wird relativ zur Kamera (Uptilt herausgerechnet), also ohne
Versatz durch die Bild-Latenz; die Geometrie wird um die Kamera so vergrößert, dass der nächste sichtbare Punkt hinter der
Near-Plane (0,05 Blöcke) liegt (höchstens × 12, perspektivisch unverändert). Nur Props, von denen ein Punkt im Sichtfeld
liegt, werden gezeichnet: der 5-Zöller zeigt bei 130° digital die Spitzen am unteren Rand, ein Deadcat mit Kamera vor den
Prop-Kreisen gar nichts. Drehende Props zeichnet das FPV-Bild nicht mit der `_blur`-Textur (64 px, harte Kante, blass),
sondern als eigene weiche Scheibe (`FpvProps.buildBlur`, 64 Segmente) in der Prop-Farbe (Lackierung → Grundfarbe der
Lack-Ebene aus `drone_paint.json` → `visual.accent`/Material) mit 85 % Helligkeit: Deckkraft 35 % an der Nabe, 25 % bei
85 % des Radius, am Rand 11 % (knapp über der Alpha-Schwelle 0,1 von `entityTranslucent`), damit die Spitzen weich
auslaufen und das Bild nicht aufhellen. Schalter „Props im FPV-Bild“ im Tab „Darstellung“ (Standard an); kein Einfluss
auf das Flugmodell.
Hinweis für Addon-Rahmen: Damit ein Deadcat wirklich keine Props zeigt, muss `camera.pos` vor den vorderen Prop-Kreisen
liegen (z. B. Kamera z −92 mm bei Vordermotoren z −30 mm und 5″-Props); liegt sie auf Höhe der Vordermotoren, sieht man bei
weitem Sichtfeld die Blattspitzen seitlich.

### Ducts im FPV-Bild (`FpvGuards`, `FpvGuardZone`, seit 0.5.0)

Was das 3D-Modell an Ducts zeigt, sieht man auch im FPV-Bild und beim Zuschauen – bei Whoops die vorderen Ducts in den
unteren Ecken, beim Cinewhoop ebenfalls unten in den Ecken, beim 5-Zöller nichts (kein `duct`). Gezeichnet wird keine
eigene Form, sondern die Flächen des echten Rahmenmodells (auch die Lack-Variante, also mit Lackierung):

- `QuadCapture` fängt die Flächen des Rahmen-Items ab (Item-Transformation eingerechnet), ohne Modell die prozedurale Form
  aus `FallbackShapes`. Nur Rahmen mit `duct` haben Guards.
- `FpvGuardZone` wählt aus: alle vier Ecken einer Fläche liegen im Ring um eine Motorachse (Abstand 0,97 × Radius des
  größten Props bis Radius + max(6 mm, 20 %)) **und** die Fläche wiederholt sich rundum – mindestens 5 Flächen mit gleichem
  Abstand zur Achse und gleicher Höhe (± 0,02 mm), wie die Segmente eines Rings. So bleiben Canopy, Kamerawangen,
  Speichen, Streben und Brücken draußen, auch wenn sie den Duct berühren. JUnit `FpvGuardsTest` prüft das an den
  eingebauten Modellen (jede Fläche von `duct`, `duct_lip`, `duct_rim` dabei, fremde Flächen unter 1 % der Fläche).
- Die Flächen hängen wie die Props an der Linse (Objektivlage, Uptilt, Sichtfeld der Kamera), ohne Bild-Latenz.
  Gemeinsame Tiefenabbildung mit den Prop-Scheiben (`FpvPropGeometry.pushScale`): jeder Eckpunkt rückt entlang seines
  Sehstrahls um den Faktor `nearScale` nach außen; was dann noch vor 55 mm läge, wird gleichmäßig in 52–55 mm gedrückt.
  Das Bild bleibt deckungsgleich, die Reihenfolge der Tiefen bleibt erhalten (vordere Duct-Wand verdeckt die Scheibe
  dahinter) und nichts fällt durch die Near-Plane (0,05 Blöcke). `nearScale` richtet sich seit 0.5.0 nach dem nächsten
  sichtbaren Prop- **oder** Guard-Punkt (höchstens × 12).
- **Eigener Durchgang wie die Vanilla-Hand** (`FpvPropPass`, seit 0.5.0): Props, Prop-Scheiben und Ducts der eigenen
  FPV-Drohne zeichnet nicht mehr der Welt-Durchgang, sondern ein eigener Durchgang am Platz der ausgeblendeten Hand
  (`GameRenderer#renderItemInHand`, Tiefenpuffer dort schon geleert), mit der Projektion der Welt (abgefangen in
  `renderLevel`) und derselben Kameradrehung. Vorher standen die um `nearScale` nach außen gerückten Ducts im Tiefentest
  hinter jedem Boden und jeder Wand, die näher lag als das Zwölffache: gelandet, beim Abheben, im Tiefflug und an Wänden
  verschwanden Ducts und Props ganz (Whoop 65 von 0 bis 50 mm über dem Boden, Cinewhoop 3″ bis 100 mm; Wand voraus bis
  200 bzw. 300 mm). Die Welt kann in die Ducts ohnehin nicht hinein (Kollisionshülle), wie bei der Hand gibt es also nichts,
  was davor liegen müsste. Mit aktivem Iris-Shaderpack bleibt der alte Weg (Welt-Durchgang), weil Iris die Hand selbst
  zeichnet; dort können Ducts am Boden weiter fehlen. Schlägt der eigene Durchgang fehl, fällt er für die Sitzung auf den
  alten Weg zurück (Log-Warnung). Selbsttest `drone: FPV-Kamera nie im Block: Ducts im FPV-Bild – …`: Whoop 65 und
  Cinewhoop 3″ gelandet im Gras und 0,6 Blöcke hoch schwebend vor der Wand (8 bzw. 15 cm Abstand), Ducts im Bild gleich
  der Analyse, und zur Gegenprobe mit dem alten Weg höchstens die Hälfte (die Szene verdeckt also wirklich).
- Nie im Iris-Schattenpass (`FpvProps.submit` bricht vorher ab) und nicht in der Messung der Prop-Abdeckung
  (`FpvProps.maskColor`). Eigener Schalter „Ducts im FPV-Bild“ im Tab „Darstellung“ neben „Props im FPV-Bild“ (beide Standard an, unabhängig voneinander; `DroneRenderConfig#fpvDucts`). Kein Einfluss auf
  Flugmodell, Kollision und die Warnung „Props im Bild“ (die zählt weiter nur Prop-Scheiben).
- Hinweis für Addon-Rahmenmodelle: Ducts aus Ringsegmenten (wie `kit.Model.ring`, ab 5 Segmenten) erscheinen ohne weiteres
  im FPV-Bild. Ein Guard aus wenigen großen Einzelteilen wird nicht erkannt und fehlt dort.
- Obergrenzen (JUnit `FpvGuardsTest`, Selbsttest „Guards im FPV-Bild“ für alle Vorlagen mit Ducts, Just-More-Parts-JUnit
  `FrameDataTest` für alle Duct-Rahmen des Add-ons): Objektiv mindestens 3 mm über der Duct-Oberkante, höchstens
  30/18/5 % Ducts bei 0/20/40° Uptilt, nichts in der oberen Bildhälfte. Ein Rahmen mit zu tiefer Kamera fällt damit auf.
- Wie viel Duct im Bild ist, ergibt sich allein aus Kamerahalter und Rahmen (Analyse des Selbsttests, 1708 × 960):

  | Drohne | Sichtfeld | 0° | 20° | 40° |
  |---|---|---|---|---|
  | Whoop 65 mm (Objektiv 7 mm über der Duct-Oberkante, 3 mm hinter den vorderen Duct-Mitten) | 125° | 21 % | 7 % | 0 % |
  | Cinewhoop 3″ (Objektiv 13 mm über der Duct-Oberkante, 2 mm hinter den Duct-Mitten, Käfig über den Ducts) | 104° | 15 % | 0 % | 0 % |
  | Just More Parts 1.1.0 Whoop 75 mm (Objektiv 6,8 mm über der Duct-Oberkante, 2,5 mm hinter den Duct-Mitten) | 126° | 21 % | 11 % | 0 % |
  | Just More Parts 1.1.0 Whoop 85 mm (6,9 mm darüber, 3 mm dahinter) | 126° | 21 % | 12 % | 0 % |
  | Just More Parts 1.1.0 Whoop 65 Lite (6,5 mm darüber, 5,5 mm dahinter; ohne Vorlage, gleiche Analyse außerhalb des Spiels) | 126° | 22 % | 10 % | 0 % |
  | Just More Parts 1.1.0 Cinewhoop 2,5″ (10 mm darüber, vor dem Akku) | 126° | 19 % | 5 % | 0 % |
  | Just More Parts 1.1.0 Cinewhoop 3,5″ (13,7 mm darüber, vor dem Akku) | 130° | 16 % | 15 % | 0 % |

  Bis zum Release-Kandidaten vom 08.10. saß das Objektiv der Duct-Rahmen zwischen den vorderen Ducts und unter oder knapp
  an deren Oberkante: eingebauter Whoop 65 34/21/5 %, Cinewhoop 3″ 58/42/26 %, Just More Parts Whoop 75 65/60/56 %,
  Whoop 85 46/32/16 %, Whoop 65 Lite 38/25/10 %, Cinewhoop 2,5″ 75/66/57 %, Cinewhoop 3,5″ 69/57/46 % – beim Cinewhoop und
  den Rahmen von Just More Parts zwei Wände über die volle Bildhöhe. Seitdem sitzt das Objektiv aller Duct-Rahmen über der
  Duct-Oberkante (Just More Parts: `docs/design/asset-style.md`, „Kamerahalter der Rahmen mit Ducts“). Eingebaut:
  Whoop 65 Objektiv (0, 9,7, −17,8) → (0, 16, −20) in einer um 7,8 mm höheren Haube (`frames.py`, `WHOOP_LENS`, Kamera-Ort
  in `drone_layout.json` (0, 13,9, −10,2)); Cinewhoop 3″ (0, 11,7, −36,8) → (0, 30, −48) in einem Käfig über den vorderen
  Ducts (Boden 0,4 mm über der Oberkante, `CINEWHOOP_LENS`, Kamera-Ort (0, 28,3, −38,2)), der Halter oben für die
  Action-Cam steigt mit (y 30,7 → 44,7). Folgen: Objektiv und Kamerapunkt der Kollisionshülle wandern mit (Airframe-Goldens
  der beiden Rahmen), „Props im Bild“ beim Standard-Uptilt Whoop 8,8 → 6,2 %, Cinewhoop 7,0 → 1,9 %, und ein Cinewhoop mit
  Action-Cam oben fliegt etwas anders, weil deren Masse 14 mm höher sitzt; alle anderen Flugwerte bleiben gleich.
- Hinweis für Addon-Rahmen mit Ducts: Das Objektiv gehört über die Duct-Oberkante, sonst stehen die Ducts im FPV-Bild als
  Wände an beiden Seiten. Faustregel: Kamerakörper samt Uptilt frei von Duct-Wand und Prop-Kreis, bei Whoops etwa 7 mm über
  der Oberkante und knapp hinter den vorderen Duct-Mitten.
- Selbsttest „drone-render: Guards im FPV-Bild – Analyse gegen gerendertes Bild“: Whoop, Cinewhoop und jede angemeldete
  Addon-Vorlage mit Ducts (mit Kreativ-FC, damit 0°, 20° und 40° Uptilt erlaubt sind) fliegen 100 Blöcke hoch im FPV,
  die Ducts werden in Schlüsselgrün gezeichnet und mit den gerasterten Guard-Flächen verglichen (Toleranz 0,5
  Prozentpunkte oder 8 %). Bei 0° müssen Ducts im Bild sein; bei 20° und 40° dürfen sie ganz fehlen, wenn auch das
  gerenderte Bild keine zeigt (Kamera über den Ducts). Bildschirmfotos `fpv_guards_whoop_65_<0|20|40>.png`, `fpv_guards_cinewhoop_3_<…>.png`,
  `drone_closeup_whoop_65.png`, `drone_closeup_cinewhoop_3.png`, `workbench_whoop.png`, `workbench_cinewhoop.png`; mit Just
  More Parts im `mods`-Ordner zusätzlich `fpv_guards_jmp_*` und `drone_closeup_jmp_*` (gehören ins Repository von Just
  More Parts).

### Ducts im Generator (`tools/assets/parts/ducts.py`, seit 0.5.0)

Gemeinsame Regeln für Whoop 65 mm, Cinewhoop 3″ und den Addon-Generator (Just More Parts hält dieselben Regeln in
`jmpstyle/duct.py`). D = größter Prop des Rahmens:

| Maß | Regel | Whoop 65 (D 31) | Cinewhoop 3″ (D 76) |
|---|---|---|---|
| Innenradius | D/2 + 1,1 mm (bis 50 mm Prop), sonst D/2 + 1,5 mm | 16,6 mm | 39,5 mm |
| Wand | 0,35 + 0,016 × D, 0,8–3 mm (vorher 1,3 bzw. 2,2 mm) | 0,85 mm | 1,57 mm |
| Lippe/Rand oben | Breite max(0,45; 0,55 × Wand), Höhe max(0,8; 0,02 × D) | 0,47 × 0,8 mm | 0,86 × 1,52 mm (Akzentfarbe) |
| Oberkante | Nabe + Blattspitzen (1,75 + 0,024 × D) + 1 mm (Whoop) bzw. 1,2 mm | 9,0 mm | 17,0 mm |
| Unterkante | Whoop: Unterkante Motorbecher (Motor − 1,9 mm), Cinewhoop: auf den Streben | −3,4 mm (vorher −4) | −0,6 mm |
| Segmente | 24 (vorher 16 bzw. 20) | | |

Der Addon-Generator setzt bei Rahmen mit Ducts Motorpads, Arme und Ducts auf die Motorhöhe, die Propwash für Rahmen ohne
eigenes Layout annimmt (`FpvRig.motorMountHeightMm`: 1,5 mm unter 100 mm Radstand, 2,5 mm bis 200 mm, darüber 3 mm, mit
`arms` deren Höhe), und nimmt für die Nabe einen typischen Motorsitz nach Propgröße (31 mm → 7 mm … 90 mm → 13,5 mm). Vorher
schwebten die Motoren 1,5–2,8 mm über den Pads und die Props ragten oben aus den Ducts. Flugmodell, Kollision und
Spaltmaß (`duct.clearance_mm`) hängen nicht am Modell und bleiben gleich.

### Generator für Addon-Modelle (`tools/assets/parts/addon.py`)

```bash
python3 tools/assets/parts/addon.py --namespace justmoreparts \
    --parts <addon>/src/main/resources/data/justmoreparts/propwash_parts \
    --out <addon>/src/main/resources/assets [--icons model|special|none] [--bbmodel <ordner>] [--keep]
python3 tools/assets/parts/addon_preview.py <addon>/src/main/resources/assets justmoreparts vorschau.png
```

- Liest dieselben Bauteil-JSONs wie `PropwashApi.parts().loadBundled` (`<kategorie>/<pfad>.json`, Feldnamen aus
  `docs/design/parts-api.md` §5) und erzeugt mit dem Baukasten der eingebauten Teile (`kit`, `paint`, `paintable`):
  Rahmen aus Radstand, `arms`, `shape` (`h_frame` mit Holmen), Ducts (Regeln „Ducts im Generator“), Akkuplatte, Kamerakäfig an `camera.pos` und
  TPU-Teilen in `visual.accent`; Motoren aus den Statormaßen (Glocke in der Akzentfarbe); Props aus Durchmesser,
  Blattzahl und Material inklusive `_ccw`, `_blur` und aller Schadensstufen; Akkus aus `pack_mm`, Zellzahl und Kapazität;
  Stacks aus Platinengröße und AIO/Turm; Video aus Kamerabreite und digital/analog. Zu jedem Modell entsteht die
  Lack-Variante samt `drone_paint.json`, Motoren bekommen `motor_seat` in `drone_layout.json`.
- `--icons model` schreibt `items/<pfad>.json` mit dem eigenen Modell (wie die eingebauten Teile), `special` mit dem
  Sonder-Renderer `propwash:part` (Rahmen und Props im Item-Rahmen mit `"wall": true`), `none` keine. Rahmen- und
  Prop-Modelle hängen im Item-Rahmen an der Wand (Abschnitt „Item-Rahmen und Regal“). Zubehör bekommt kein Modell (prozedural) und bei `model`/`special` das
  Sonder-Symbol.
- Vorlage: `tools/assets/parts/addon_example/` (Deadcat 5″, X-Class 13″, Motoren 2806.5 und 4214, 13″-Carbon-Zweiblatt,
  8S-Li-Ion, 30×30-Stack bis 8S, digitale HD-Einheit). Daraus sind die Test-Modelle im Namensraum `propwash_rendertest`
  (`common/src/devtest/resources/assets/propwash_rendertest`, nur Selbsttest) erzeugt:
  `python3 tools/assets/parts/addon.py --namespace propwash_rendertest --parts tools/assets/parts/addon_example
  --out common/src/devtest/resources/assets --icons none`.
- **Grenzen eines Minecraft-Modells:** Elemente dürfen nur zwischen −16 und 32 Modell-Einheiten liegen (1 Einheit = 8 mm,
  also etwa ±190 mm um die Mitte). Größere Teile – etwa Rahmen ab X-Class – bekommen vom Generator kein Modell (mit Warnung),
  sie werden aus der Definition gezeichnet. Propwash prüft fremde Modelle beim Laden der Ressourcen ebenso: liegt ein Element
  außerhalb, gilt das Modell als fehlend (Warnung im Log, Form aus der Definition statt des Minecraft-Fehlermodells).
- Wer Modelle lieber in Blockbench baut: `--bbmodel` schreibt die Quellen, Maßstab und Anbaupunkte wie oben; die Datei-
  und Id-Regeln dieser Tabelle gelten unverändert.

### Selbsttests und Bildschirmfotos

`drone.client.render.PartsRenderSelfTests` (am Ende von `DroneRenderSelfTests`): fremde Teile ohne Modell zeichnen die
Form aus der Definition (Welt, Drohnen-Item, Werkbank-Vorschau, Geist, Flugschul-Skalierung, Inventar-Symbol), Modelle aus
`propwash_rendertest` samt Varianten, `motor_seat` und Lack werden benutzt, die Teile des Beispiel-Addons
`propwash_example` zeichnen (mit oder ohne Modell), Sonder-Renderer `propwash:part` (Codec, Argument, Schadensstufe,
alle angemeldeten Teile), FPV-Prop-Geometrie (5″ sichtbar, Deadcat nicht) und der Standard des Schalters.
Bildschirmfotos (`PartsRenderShowcase`, eigener Platz 40 Blöcke neben dem Spieler): `drone_closeup_deadcat.png` und
`drone_closeup_xclass.png` (Deadcat- bzw. X-Class-Rahmen des Beispiel-Addons mit passenden Teilen auf einem Sockel, wie
die übrigen Nahaufnahmen mit Sichtfeld 30 aus 28° von oben; der Abstand ist 2,9 × der Drohnenradius, bei großen Drohnen
schwebt die Kamera; die X-Class trägt die Lackierung des Beispiel-Presets), `fpv_props_in_view.png` (5-Zöller schwebt im
FPV, digitale Brille, Props an den Bildrändern; der Folgeschritt prüft, dass Props gezeichnet wurden),
`fpv_props_over_water.png` (dieselbe Szene über einem 2 Blöcke tiefen See, 65 × 73 Blöcke, nur Start- und Pilotplatz bleiben
Gras; die drehenden Prop-Scheiben liegen vor dem Wasser, das Wasser muss darunter sichtbar bleiben; der See wird danach
zurückgebaut, der Folgeschritt prüft, dass Scheiben gezeichnet wurden), dieselbe Schwebeszene je Rahmenart
(`fpv_props_whoop.png`, `fpv_props_cinewhoop.png`, `fpv_props_race.png`, `fpv_props_long_range.png`, aus dem
Beispiel-Addon `fpv_props_stretched.png` und `fpv_props_deadcat.png`; Brille passend zum Videosystem, zeigt, wie viel
Prop Rahmenform und Kamerahalter ins Bild bringen) und
`inventory_addon_parts.png` (alle Teil-Items des Beispiel-Addons und Beispiel-Drohnen im Inventar; Maus fest auf einem
leeren Feld des Handwerksrasters, also kein Tooltip, HUD ausgeblendet, leerer Hotbar-Platz gewählt, Homepoint gelöscht).
Item-Rahmen (`ItemFrameShowcase`, eigene Wand 40 Blöcke neben dem Spieler): `item_frames_wall.png` (Übersicht),
`item_frames_small_drones.png` (Whoop, Cinewhoop, Race, Freestyle), `item_frames_large_drones.png` (Long Range, Deadcat
und X-Class des Beispiel-Addons), `item_frames_rotated_glow.png` (Drehstufen 1, 2, 4 und 7, Leuchtrahmen, lackierte und
beschädigte Drohne, unsichtbarer Rahmen), `item_frames_frame_parts.png` (alle Rahmen-Teile), `item_frames_other_parts.png`
(Props und übrige Teile), `item_frames_side.png` (schräg von der Seite), `item_frames_floor.png` und
`item_frames_ceiling.png` (Rahmen am Boden und an der Decke), `item_frames_addon_drones.png` und
`item_frames_addon_frames.png` (Presets und Rahmen aller geladenen Addons, je acht nebeneinander) und `drone_shelf.png`
(Drohnen und Teile im Regal).

## Zubehör

`tools/assets/parts/accessories.py` (Teil von `generate.py`, geprüft von `validate.py`, Vorschau in `docs/asset-sheets/parts_preview.png`
mit allen Rahmen samt Zubehör). Maße in mm wie `build.AccessoryType`; Nase = −Z.

| Modell | Aufbau | Anbaupunkt (0, 0, 0) |
|---|---|---|
| `accessory/led_strip` | Alu-Profil 32 × 3 × 8 mm (x ±16, y −3…0, z ±4), dunkler Diffusor unten, 8 LEDs als eigene Elemente `led` (2,4 × 0,4 × 2,4 mm, Mitten x = −14, −10 … 14, y −3,4…−3), Endkappen, Kabel am rechten Ende | Mitte der Oberseite |
| `accessory/action_cam_light` | nackte Action-Cam auf einer roten TPU-Schale (`tpu_cradle` 1,6 mm unter der Käfigplatte, Seitenlippen, Klettband quer): Käfigplatte, zwei Platinen mit Chips, vier Messing-Abstandshalter, Kern, Kühlkörper mit 7 Rippen oben (Körper 46 × 30 × 26 mm, y 5…35), Linsenmodul vorn mit Linse bis z −20, Stromstecker rechts mit Kabel nach unten (zum Flugakku), zwei Gelenkfinger nach unten | Gelenkachse |
| `accessory/action_cam_heavy` | große Action-Cam im Schutzrahmen (60 × 45 × 35 mm, y 5…50), Objektiv links vorn (x −11, bis z −23,5), kleines Front-Display, großes Rück-Display, Seitenklappe, roter Auslöser oben, zwei Gelenkfinger | Gelenkachse |
| `accessory/action_cam_flashback` | kompakte Kamera (42 × 28 × 24 mm, y 5…33) in dunklem Violettgrau mit schwarzen Seitenleisten, Objektiv mittig mit gelbem Ring (bis z −17), Rücklauf-Symbol vorn oben links, Rück-Display mit Zeitleiste und Keyframes, roter Auslöser und Modus-Taste oben, Buchse rechts, Stromstecker mit Kabel nach unten (Flugakku), zwei Gelenkfinger; lackierbar wie die Heavy (Gehäuse und Oberseite) | Gelenkachse |
| `accessory/mount_top` | roter TPU-Sockel (`tpu_red`) am Rahmen: Fuß 29,2 × 2,7 × 30 mm (y −8,7…−6, z −4…26, reicht vom Gelenk nach hinten über den Kamerakäfig), zwei Klemmbacken außen am Käfig (x ±12,8…14,6, 6,3 mm nach unten, z 2…24), Sockel und flache Strebe nach hinten, drei Finger (x −6, 0, 6; y −3…3), Schraube durch das Gelenk mit Amber-Rändelmutter rechts | Gelenkachse |

Die Kamera-Finger (x ±1,6…±4,4, y −2,6…5) greifen zwischen die drei Sockel-Finger. Der Renderer zeichnet den Sockel ungedreht und die Kamera
um die Gelenkachse um `tilt` gekippt – wie ein echter Action-Cam-Halter. Die Kameras sitzen vorn über dem Kamerakäfig und der FPV-Kamera,
der Sockel klemmt mit seinen Backen seitlich am Käfig. Die Anbaupunkte sind mit einer Kollisionsprüfung der Modelle gewählt (Kamera-, Sockel- und
Streifen-Elemente gegen Rahmen, Akku, Stack und FPV-Kamera des Presets): Light- und Heavy-Cam berühren bei der Standardneigung weder Akku noch
FPV-Kamera; `validate.py` (`check_accessory_clearance`) prüft jedes Kamera-Element samt Neigung gegen Hülle, Gurt und Schnalle aller
passenden Akkus (5″: 6S 1300, 6S 1100, 4S 650; 7″: Li-Ion 4000 und 6S 1300) mit 1 mm Abstand. Oben sitzt die Kamera auf dem vorn
über den Käfig ragenden Sockel (Gelenk: Cinewhoop y 44,7 z −47 10° – seit dem Kamerahalter über den Ducts, vorher y 30,7 –, Freestyle y 34,7 z −69 22°, Race y 26,7 z −67 20°,
Long Range y 38,7 z −68 20°), um 10–22° nach oben geneigt; absichtlich überlappen nur die Klemmbacken mit dem Käfig (≤ 0,5 mm), beim 7-Zöller der Fuß mit der Deckplatten-Vorderkante und die
schräg gestellten LED-Streifen mit der Bodenplatte (Vorderkante 2 mm eingelassen).

## Akku unter dem Rahmen (Race-Rahmen, seit 0.5.0)

Race-Drohnen tragen den Akku unter dem Rahmen, Freestyle- und Long-Range-Drohnen oben: so bauen echte Rennpiloten,
der Schwerpunkt sitzt tief zwischen Akku und Bodenplatte. Quelle ist `battery.on_top` der Teile-Definition (Flugmodell);
eingebaut tragen ihn `whoop_65` (Halteschiene, kein Riemen) und `race_5` unten, in Just More Parts die Race-Rahmen
(`role: race`, z. B. `race_5_stretched`).

- **Rahmen** (`frames.py` `build_race`): Riemenschlitze (x ±9…13, z −14…26) und ein Gummipad 16 × 44 mm (y −2,8…−2) an der
  Bodenplatte, die Bodenplatte zeigt ihr Schlitzmuster auch auf der Unterseite (`carbon_plate(…, both_sides=True)`); die
  Deckplatte (y 18…20, „RACE“) hat keine Riemenschlitze mehr, nur zwei Bohrungen.
- **Akku** (`batteries.py` `build_lipo(key, under=True)`): dieselben Maße, Leitungen und Stecker wie das Grundmodell, der
  Pack hängt von y = −Höhe bis 0 (Oberseite am Aufhängepunkt), Riemen `strap_band_under` unter dem Pack durch, an beiden
  Seiten hoch und als Band 0,8 mm über der Platte (nur oben, vorn, hinten sichtbar), rote Schnalle unten; das Etikett-Band
  liegt auf der sichtbaren Unterseite (`wrap(…, under=True)`, eigenes Material `wrap_<key>_under`, Lack-Kanal Akku). Varianten
  gibt es nur für Akkus mit Riemen (`b6s_1100`, `b6s_1300`); Packs ohne Riemen (1S, 4S 650, Li-Ion) hängt der Renderer mit
  dem normalen Modell an seiner Oberkante auf, das sieht gleich aus.
- **Renderer** (`DroneAssembly.batteryPart`): Akku unten → `<key>_under` am Aufhängepunkt, sonst normales Modell um seine
  Oberkante nach unten verschoben (`hangBelow`), sonst die prozedurale Form. Im FPV-Bild zeichnet Propwash von der eigenen
  Drohne nur Props und Ducts, der Akku ist dort nie zu sehen; in der dritten Person, auf dem Boden, in der Werkbank, im
  Item und in der Flugschule hängt er unter dem Rahmen.
- **Prüfungen**: `validate.py` (`check_battery_mounts`: Variante, Lack, Quelle, Pack hängt bei y = 0, kein Inventar-Item,
  Seite wie in der Definition, Aufhängepunkt ≤ 1 mm von `plate_mm`), JUnit `RaceBatteryAssetsTest`, Selbsttest „drone-render:
  Akku unter dem Race-Rahmen“ (Anbaupunkte, Modelle, Whoop unverändert, Unterkante im Bild ≤ 4 mm neben der des Flugmodells
  inkl. Riemen und Schnalle, Rahmen und Presets von Addons hängen ihren Akku am Aufhängepunkt auf). Bildschirmfotos
  `race_battery_side.png`, `race_battery_front.png`, `race_battery_closeup.png`, `race_battery_hover_below.png` (im
  Schwebeflug von unten, `race_battery_hover_below_freestyle.png` zum Vergleich), `race_battery_jmp_slim.png` (Race-Rahmen
  5″ Slim aus Just More Parts, nur mit dessen Jar in `fabric/runs/selftest-client/mods`) und `workbench_racer.png`.

## Akkus je Rahmen

Quelle im Code: `build.BatteryFit` (Werkbank, Akkutausch, Tooltips und Wartungsmenü benutzen dieselbe Regel). Prüfreihenfolge:
zu wenig Zellen → zu viele Zellen → zu schwer → schwach → passt. „Schwach“ ist erlaubt (Werkbank-Warnung
`BATTERY_UNDERSIZED`), alles andere ist nicht baubar (`BATTERY_FRAME`) bzw. lässt sich nicht einsetzen.

| Rahmen | Zellen | max. Akkumasse | schwach, wenn | `b1s_300_hv` | `b4s_650` | `b6s_1100` | `b6s_1300` | `b6s_4000_lion` |
|---|---|---|---|---|---|---|---|---|
| `whoop_65` | 1S | 10 g | — | passt | zu viele Zellen | zu viele Zellen | zu viele Zellen | zu viele Zellen |
| `cinewhoop_3` | 3S–4S | 120 g | — | zu wenig Zellen | passt | zu viele Zellen | zu viele Zellen | zu viele Zellen |
| `freestyle_5` | 4S–6S | 230 g | unter 6S | zu wenig Zellen | schwach | passt | passt | zu schwer (420 g) |
| `race_5` | 4S–6S | 230 g | unter 6S | zu wenig Zellen | schwach | passt | passt | zu schwer (420 g) |
| `long_range_7` | 6S | 500 g | unter 1300 mAh | zu wenig Zellen | zu wenig Zellen | schwach | passt | passt |

Alle Presets (`DroneBuild.PRESETS`) passen (JUnit `BatteryFitTest`).

`long_range_7` mit `b6s_1100` ist bewusst „schwach“ und nicht verboten (entschieden in Welle 2b): Schwebeflugzeit laut
`BuildAnalysis` 13,7 min statt 15,3 min (`b6s_1300`) bzw. 32,4 min (`b6s_4000_lion`).

## Welt-Assets (assets-world)

Alle Dateien werden per Skript erzeugt — nie von Hand bearbeiten, sondern Quelle oder Skript ändern:

```
python3 tools/assets/world/generate.py   # Quellen: assets-src/world/ (Palette + Pixel-Art), Rest prozedural
python3 tools/assets/world/validate.py   # JSON, Verweise, Atlanten, Größen, Gate-Maße, GUI-Slots → "WORLD ASSETS OK"
python3 tools/assets/world/preview.py    # docs/asset-sheets/world_preview.png (eigener Software-Renderer, Vanilla-UV-Regeln)
```

Module unter `tools/assets/world/`: `generate.py` (Werkbank, Brillen, Gates, Werkzeug, Slot-Symbole, Werkbank-GUI,
Icons), `remote.py` (Funke), `charger.py` (Ladegerät), `menus.py` (GUI Ladegerät/Wartung), `guikit.py` (gemeinsame
GUI-Palette und Zeichenhelfer), `modelrender.py` + `views.py` (Vorschau-Renderer; `views.py` bildet die Vanilla-Transformationen
für GUI, erste Person — `FirstPersonHandsAndItemsRenderer`, HUD-FOV 70° — und dritte Person — `ItemInHandLayer` — nach),
`preview_gameplay.py` (Vorschau-Abschnitte Funke, Ladegerät, GUIs).

Dazu prüft `common/src/test/java/de/lardtom/propwash/assets/world/WorldAssetsTest.java` dieselben Verträge per JUnit.
Im Spiel prüfen die Selbsttest-Schritte aus `assets.world.client.WorldAssetsSelfTests` (Sprites in Item-, Block- und
GUI-Atlas, gebackene Item-Modelle, Werkbank-Blockmodell, Brillen als räumliches Kopf-Modell, Gate-Modelle, GUI, Icon,
dazu „Funke und Ladegerät“: Funken-Modelle und Gimbal-Textur, Item-Modelle `remote`/`battery_charger` gebacken, also auch
der Special-Typ `propwash:remote_gimbals` aufgelöst, alle Blockzustände des Ladegeräts mit Modell, beide GUI-Texturen); sie werden
über `assets.world.client.WorldAssetsClient.init()` angemeldet, das `client.PropwashClient.init()` aufrufen muss.

| Asset | Dateien (unter `assets/propwash/`) | Hinweise |
|---|---|---|
| Werkbank | `blockstates/drone_workbench.json`, `models/block/drone_workbench.json`, `textures/block/drone_workbench_{top,front,side,bottom}.png`, `items/drone_workbench.json` | Blockzustand nur `""` (der Block von content hat keine Eigenschaften). Vorder-Textur (Schubladen) auf Nord und Süd, Lochwand auf Ost und West. Bekommt der Block `FACING`, liefert assets-world gedrehte Varianten nach. |
| Brillen | `items/goggles_{analog,digital,creative}.json`, `models/item/goggles_*.json` (flaches Icon), `models/item/goggles_*_worn.json` (3D-Modell), `textures/item/goggles_*.png` (16×16), `textures/item/goggles_*_worn.png` (64×64) | Item-Definition `minecraft:select` auf `minecraft:display_context`: `head` → `goggles_<art>_worn`, sonst das flache Icon. Kein Equipment-Asset (content setzt `Equippable` ohne `setAsset`), deshalb zeichnet Vanilla `CustomHeadLayer` das Modell auf dem Kopf. Maßstab siehe „Brillen-Modell“. |
| Gates | `models/gate/{small,medium,large}_{frame,lights,start_frame,banner}.json`, `textures/item/gate/frame.png` und `start_frame.png` (64×64), `textures/item/gate/lights.png` und `banner.png` (32×32) | Texturen liegen unter `textures/item/gate/`, **nicht** `textures/gate/`: JSON-Modelle finden ihre Texturen in 26.3 nur im Item- oder Block-Atlas (`MaterialBaker#bake`). Maße siehe Abschnitt „Gates“. |
| Gate-Items | `items/gate_{small,medium,large}.json`, `models/item/gate_*.json`, `textures/item/gate_*.png` | flache 16×16-Icons (Frontansicht, Seitenverhältnis wie das Gate) |
| Kurs-Werkzeug | `items/course_tool.json`, `models/item/course_tool.json` (`item/handheld`), `textures/item/course_tool.png` | Zielflagge am Stab |
| Zwischenteile | `items/<id>.json`, `models/item/<id>.json` (`item/generated`), `textures/item/<id>.png` (16×16) für `carbon_plate`, `copper_coil`, `circuit_board`, `lipo_cell`, `hd_chip` | Pixel-Art in `assets-src/world/pixel/materials.txt`, erzeugt von `tools/assets/world/materials.py` (Teil von `generate.py`, geprüft von `validate.py`): Carbonplatte mit Köperbindung und vier Bohrungen, Kupferspule mit Eisenkern, grüne Platine mit Leiterbahnen und Chip, LiPo-Pouchzelle mit zwei Anschlussfahnen, HD-Videochip mit Amethyst-Die und Goldkontakten |
| Werkbank-GUI | `textures/gui/drone_workbench.png` | siehe Abschnitt „Werkbank-GUI“ |
| Slot-Symbole | `textures/gui/sprites/gui/slot/{frame,stack,motor,prop,video,battery,drone,accessory_top,accessory_bottom}.png` | GUI-Atlas-Sprite-Id `propwash:gui/slot/<category>` (der Pfad unter `gui/sprites/` ist die Id, deshalb `gui/slot/…` doppelt). `drone` = Zerlege-Slot. 16×16, einfarbig `#555555` wie die Vanilla-Leerslot-Symbole, lesbar auf Vanilla-Grau und auf dem dunklen Slot `#1B1E23`. Keine eigene Atlas-Registrierung nötig (Vanilla-`atlases/gui.json` liest `gui/sprites` aus allen Namespaces). |
| Mod-Icon | `icon.png` (256×256), `pack.png` im Ressourcen-Stamm (128×128) | Quad-Draufsicht + Schriftzug PROPWASH |
| Funke | `items/remote.json`, `models/item/remote.json`, `models/item/remote_body.json`, `textures/item/remote.png` (64×64), `textures/entity/remote/gimbal.png` (16×16) | siehe Abschnitt „Funke“ |
| Ladegerät | `blockstates/battery_charger.json` (multipart), `models/block/battery_charger_{idle,charging,done}.json`, `models/block/battery_charger_bay_<0..3>_{empty,charging,full}.json`, `models/block/battery_charger_inventory.json`, `textures/block/battery_charger_{top,side,bottom,front_idle,front_charging,front_done,pack}.png` (32×32), `items/battery_charger.json` | siehe Abschnitt „Ladegerät“ |
| GUI Ladegerät/Wartung | `textures/gui/battery_charger.png`, `textures/gui/drone_service.png` (je 256×256) | siehe Abschnitt „GUI Ladegerät/Wartung“ |
| Signal-Repeater | `blockstates/signal_repeater.json` (12 Varianten `face`/`facing`), `models/block/signal_repeater.json`, `models/item/signal_repeater.json`, `items/signal_repeater.json`, `textures/block/signal_repeater_{base,body_side,body_top,antenna}.png` (32×32), `textures/block/signal_repeater_led.png` (16×32, animiert) | siehe Abschnitt „Signal-Repeater“ |

## Brillen-Modell

`CustomHeadLayer` setzt den Kopf-Mittelpunkt auf die Modellmitte `(8, 8, 8)` und skaliert mit 0,625: **16 Modell-Einheiten
= 10 Skin-Pixel**, also 1,6 Einheiten je Skin-Pixel (`GOGGLE_UNIT` im Generator). Der Kopf (8 Skin-Pixel) reicht damit von
1,6 bis 14,4, die Hut-Ebene (9 Pixel) von 0,8 bis 15,2. −Z (Norden) ist das Gesicht, +X die rechte Seite des Trägers, +Y
oben. Der Generator beschreibt die Teile in Skin-Pixeln relativ zur Kopfmitte unten (x/z −4…4, y 0…8, Augen bei y ≈ 3,5)
und rechnet um. Gemeinsam: Band 1,5 px hoch knapp außerhalb der Hut-Ebene (±4,5…5,0), Brillenkasten vor dem Gesicht ab
der Hut-Ebene (z −4,5) über Augenhöhe.

| Art | Brillenkasten (x, y, z in Skin-Pixeln) | Aufbauten |
|---|---|---|
| analog | ±4,75 × 2,5…6 × −7…−4,5, anthrazit, Glas blau, Amber-Leiste | Empfängermodul oben rechts, Stabantenne mit Amber-Kappe |
| digital | ±5 × 2…6 × −7,5…−4,5, hellgrau mit dunkler Front | zwei Paddel-Antennen an den oberen Ecken |
| creative | wie analog, violett mit schillerndem Glas | goldenes Modul mittig, zwei Antennen mit Magenta-Kappen |

Textur 64×64 mit 2 Texeln je Skin-Pixel, jede Fläche hat ihren eigenen Bereich (automatisch gepackt). Die Vorschau
(`preview.py`) zeichnet die Modelle mit derselben Umrechnung auf einen Kopf.

Lage am Kopf (Bildprüfung, achte Runde): Die Brille hängt vollständig an der Kopf-Pose. `CustomHeadLayer#submit` ruft
`root().translateAndRotate` und `translateToHead` (= `head.translateAndRotate`, also Drehpunkt am Hals samt `xRot`,
`yRot`, `zRot`) auf, bevor das `head`-Display gezeichnet wird; `PilotHeads` ändert nur `state.xRot`, das Modell und die
Ebene lesen denselben Render-State. Der Brillenkasten ragt 2,5 Skin-Pixel vor das Gesicht; bei 40° Kopfdrehung aus
Sicht der Drohne (`pilot_from_drone.png`) liegt er deshalb perspektivisch seitlich vor dem Gesicht, sitzt aber fest.
Prüfbild mit Kopf 45° gegen den Körper gedreht und angehoben: `pilot_head_turned.png`.

## Gates

Maße für race (Kollision) und für den Renderer. Alle Werte in **Blöcken**, relativ zum Entity-Ursprung (**Mitte der
Unterkante** des Gates), Ausrichtung **Süden**: die Öffnung zeigt nach ±Z (durchflogen wird entlang Z), die Breite liegt
auf X, oben ist +Y. Andere Ausrichtungen = Drehung um Y (Entity-`yRot`).

| Größe | Außen B × H | Öffnung B × H | Rahmenstärke | Tiefe (Z) |
|---|---|---|---|---|
| `small` | 3 × 3 | 2 × 2 | 0,5 | 0,5 |
| `medium` | 4 × 4 | 3 × 3 | 0,5 | 0,5 |
| `large` | 6 × 4 | 5 × 3 | 0,5 | 0,5 |

Kollisionsboxen (`W` = Breite, `H` = Höhe, `t` = 0,5 Rahmenstärke, `d` = 0,25 halbe Tiefe):

| Teil | X | Y | Z |
|---|---|---|---|
| Pfosten links | −W/2 … −W/2 + t | 0 … H | −d … d |
| Pfosten rechts | W/2 − t … W/2 | 0 … H | −d … d |
| Balken unten | −W/2 + t … W/2 − t | 0 … t | −d … d |
| Balken oben | −W/2 + t … W/2 − t | H − t … H | −d … d |
| Öffnung (Durchflug-Test) | −W/2 + t … W/2 − t | t … H − t | Ebene Z = 0 |

Zahlen: `small` Pfosten X −1,5…−1,0 und 1,0…1,5, Balken X −1…1, Y 0…0,5 und 2,5…3; `medium` Pfosten X −2…−1,5 und
1,5…2, Balken X −1,5…1,5, Y 0…0,5 und 3,5…4; `large` Pfosten X −3…−2,5 und 2,5…3, Balken X −2,5…2,5, Y 0…0,5 und 3,5…4.
Das ist genau die Aufteilung von `GateGeometry#boxes` (race); passende Werte für `GateSize(width, height, frame, depth)`:
`SMALL(3, 3, 0.5, 0.5)`, `MEDIUM(4, 4, 0.5, 0.5)`, `LARGE(6, 4, 0.5, 0.5)`.

Nur Optik, keine Kollision nötig: Eckverbinder (dunkles Metall nahe der Rahmenfarbe, nur helle Kante und Schrauben; früher amber, das stach aus der Ferne stärker hervor als das grüne Gate-Signal) über den Pfosten-Enden, Z −0,3125…0,3125 (ragen 1/16 vor und
hinter den Rahmen); Standfüße unter jedem Pfosten, X Pfosten ± 1/16 eingerückt, Y 0…0,125, Z −0,5…0,5. Die Ecken liegen
vollständig innerhalb der Pfosten-Boxen (X/Y), die Füße sind flach genug für den Boden.

**Modelle:** `propwash:gate/<size>_frame` und `propwash:gate/<size>_lights` im Maßstab **1:2** (8 Modell-Pixel = 1 Block),
Modellpixel `(8, 0, 8)` = Entity-Ursprung, X/Z-Mittelpunkt bei 8. Der Renderer skaliert ×2 um den Ursprung (race passt die
Größe ohnehin über die Modell-Bounding-Box an: `min(W / Modellbreite, H / Modellhöhe)` = 2). Die Rahmen-Bounding-Box ist
exakt Außenbreite × Außenhöhe (X/Y); in Z reicht sie wegen der Füße bis ±0,5. Rahmen ohne Tint; Lichter sind LED-Leisten
(vorn, hinten und auf der Innenseite rund um die Öffnung, dazu außen an beiden Pfosten und oben auf dem Querbalken, pro Block
ein Element; Breite 0,25 Blöcke mittig auf der 0,5 Blöcke breiten Rahmenfläche). Damit die Außenleisten nicht über das
Modellraster −16…32 hinausragen, sind Pfosten-Außenseiten und Balken-Oberseite um eine Leistenstärke (1/32 Block)
zurückgesetzt, die Leisten schließen bündig mit den Eckverbindern ab. Jedes Gate ist **ein** Entity mit genau einem
Lichter-Modell und einer Tint-Farbe, alle Segmente zeigen also immer denselben Zustand; die Leisten liegen vor den
Rahmenflächen, nie deckungsgleich, neutral weiß texturiert, alle Flächen
`tintindex` 0 und `light_emission` 15 — also volle Helligkeit auch ohne Vollhell-Lichtwert. Einfärbung über die
Item-Definition mit `minecraft:custom_model_data`-Tint (race: `items/race/gate_<size>_lights.json`). Rahmen und Lichter
teilen sich dieselbe Transformation.

**Start-/Ziel-Gate:** `propwash:gate/<size>_start_frame` hat genau die Elemente von `_frame`, nur mit der Textur
`item/gate/start_frame` (64 × 64, gleiche Aufteilung wie `frame.png`, Rohre und Frontflächen schwarz-weiß kariert in
2-Pixel-Feldern, Eckverbinder und Füße wie beim normalen Rahmen). `propwash:gate/<size>_banner` ist ein Banner über dem
Gate: Modell-Ursprung `(8, 0, 8)` = Mitte der **Oberkante** des Gates (der Renderer verschiebt um die Gate-Höhe), X über die
volle Außenbreite, Y 0…1 Block (8 Pixel), Z ±1/16 Block; je 0,5 Blöcke karierte Endkappen, dazwischen 1-Block-Felder mit
kariertem Streifen oben und unten und dunkler Carbon-Mitte, auf die der Renderer „START · ZIEL“ schreibt
(Textur `item/gate/banner`, 32 × 32). Beide gibt es für `small`, `medium`, `large`; race rendert sie über
`items/race/gate_<size>_start_frame.json` und `…_banner.json`. Nummern-Tafel, Bodenpfeile, rote Rückseiten-Markierungen und
der Lichtschein des Start-Gates zeichnet race im Code (`docs/design/race-line.md`).

**Leiter-Varianten (gestapelte Gates):** Zu `_frame`, `_lights` und `_start_frame` gibt es je Größe `…_legs` und
`…_stacked` (Item-Definitionen `items/race/gate_<size>_<teil>_<variante>.json`, Lichter mit demselben Tint).
`_legs` (unterstes Gate eines Stapels) = Rahmen ohne unteren Balken, Eckverbinder und Füße bleiben, Lichter ohne die
drei unteren Leisten. `_stacked` (Gate auf einem Gate) = ohne unteren Balken, ohne untere Eckverbinder und Füße; statt der
Eckverbinder sitzt je Pfosten ein T-Stück (`tee_left`/`tee_right`, Textur der Eckverbinder) über dem Balken des Gates
darunter: Y −1/32 … t + 1/32, Z ± (0,3125 + 1/32), X innen 1/32 über den Pfosten hinaus, außen 1/64 eingerückt (so liegt
keine Fläche deckungsgleich mit dem Eckverbinder oder der Außenleiste darunter bzw. dem eigenen Pfosten, und `large`
bleibt im Modellraster). Die Lichter behalten nur die innere untere Leiste (Farbe dieses Gates auf dem gemeinsamen Balken).
Das Rahmen-Bounding-Box der `_stacked`-Modelle reicht deshalb unter 0; race nimmt Maßstab und Versatz immer vom `_frame`.
`validate.py` prüft Vorhandensein, Raster, Form gleich Start-Rahmen, fehlenden unteren Balken, Füße bzw. T-Stücke.

### Kurs-Elemente

Rennflagge, Slalomstange und Tauchwürfel (race, `docs/design/course-elements.md`) entstehen in
`tools/assets/world/course.py` (Aufruf aus `generate.py`, geprüft in `validate.py` → `check_course`). Maßstab wie die
Gate-Teile: 8 Modellpixel = 1 Block, Ursprung `(8, 0, 8)` = Fuß des Elements, +X ist die linke Hand des Piloten, +Z
die Flugrichtung. Der Renderer passt die Teile mit dem Maßstab des mittleren Gates ein und kachelt sie auf die
eingestellte Höhe bzw. Breite (ganze und halbe Blöcke).

| Teil (`models/course/…`) | Form | Textur |
|---|---|---|
| `slalom_tube`, `_half` | Rohr 0,25 × 1 (½) × 0,25, mittig auf der Achse | `item/course/pole` rot-weiß geringelt |
| `flag_tube`, `_half` | Rohr wie oben | `item/course/pole` dunkles Carbon-Grau |
| `pole_base` | Fußplatte 0,75 × 0,125 und Manschette | Platte mit gelbem Ring, Manschette Metall |
| `pole_light` | Leuchtkappe 0,3125 × 0,125 auf der Spitze | `item/gate/lights`, Tint 0 und `light_emission` 15 |
| `flag_cloth_left`, `_right` | Tuch 1 × 2 × 1/16 neben dem Rohr (+X bzw. −X) mit zwei Klemmen; der Renderer staucht es auf die Tuchhöhe | `item/course/flag` (64 × 64) gelb mit Wendepfeil und Karo |
| `cube_bar`, `_half`, `cube_post`, `_half` | Rohr 0,25, Balken entlang X, Pfosten entlang Y | `item/course/pole` dunkles Grau |
| `cube_corner` | Eckwürfel 0,3125 | gelber Eckverbinder mit Schrauben |
| `cube_led`, `_half` | LED-Leiste oben auf dem Balken der Deckfläche | `item/gate/lights`, Tint 0 und `light_emission` 15 |

Item-Definitionen der Teile: `items/race/course_<teil>.json` (Leuchtteile mit `custom_model_data`-Tint wie die
Gate-Lichter: grün = nächstes Ziel, weiß sonst). Inventar-Symbole `item/race_flag`, `item/slalom_pole`, `item/dive_cube`
(16 × 16, flach wie die Gate-Items). Das Freiform-Gate (`docs/design/freeform-gate.md`) hat kein Modell in der Welt – sein
Leuchtumriss entsteht im Code (`race.client.FreeformGateRenderer`); sein Inventar-Symbol `item/freeform_gate` (Felsbogen
mit weißer Leuchtkante und blauem Richtungswinkel) entsteht ebenfalls in `course.py` (`FREEFORM_ICON`). Bodenpfeile, Kreuze, Pfeilbogen, Tauchpfeile, der Leuchtrahmen des Würfelausgangs
und die Nummern-Tafel zeichnet race im Code (`race.client.CourseElementRenderer`).

**Gate-Stange** (`docs/design/gate-pole.md`): kein eigener Modellsatz, sondern Gate-Teile aus `models/gate/piece/` –
`post_left`/`_half` und `led_post_left`/`_half` für Rohr und LED-Leisten (der Renderer verschiebt sie um −0,25 auf die
Achse) und drei Teile aus `generate.py` (`gen_gate_pole`, Textur `item/gate/frame`):

| Teil | Form | Textur |
|---|---|---|
| `pole_end` | quadratischer Verbinder 0,625 × 0,5 × 0,625 an jedem freien Ende | Seiten `conn_ew`, Deckflächen neues Feld `conn_cap` (10 × 10) |
| `pole_tee` | 3-Wege-Verbinder 0,6875 × 0,5625 × 0,6875 unter dem Fußende (umschließt den Verbinder des Trägers) | wie `pole_end` |
| `pole_foot` | Kreuzfuß aus zwei Platten 1 × 0,375 (quer 3/32 niedriger gegen Z-Fighting), an Wand und Decke als Flansch | `foot_top` (quer um 90° gedreht), `foot_ns`, `foot_ew` |

Item-Definitionen `items/race/gate_piece_pole_*.json`; die LED-Leisten bekommen die Farbe der Stange über den
`custom_model_data`-Tint. Inventar-Symbol `item/gate_pole` (16 × 16, `gate_pole_icon`): dunkles Rohr mit grüner
LED-Leiste, gelbe Verbinder oben und unten, Kreuzfuß.

## Werkbank-GUI

`textures/gui/drone_workbench.png` ist 512 × 256 groß; das Panel belegt **(0, 0) bis (312, 224)**, der Rest ist leer.
Zeichnen: `graphics.blit(RenderPipelines.GUI_TEXTURED, TEX, leftPos, topPos, 0, 0, 312, 224, 512, 256)`. Dunkler
Konfigurator-Stil (Anthrazit, Amber-Akzent). Die Koordinaten entsprechen `content.WorkbenchLayout` (312 × 224).

Slots (Panel-Koordinaten = `Slot#x/y`, linke obere Ecke der 16 × 16-Item-Fläche; der gezeichnete Slot-Rahmen liegt bei
x − 1, y − 1, 18 × 18):

| Slot | x, y | | Slot | x, y |
|---|---|---|---|---|
| Rahmen (Mitte) | 80, 64 | | Prop vorne links | 10, 30 |
| Stack | 80, 42 | | Prop vorne rechts | 150, 30 |
| Video (Nase) | 80, 20 | | Prop hinten links | 10, 98 |
| Akku | 80, 87 | | Prop hinten rechts | 150, 98 |
| Zubehör oben (Action-Cam) | 52, 64 | | Zubehör unten (LED-Streifen) | 108, 64 |
| Motor vorne links | 34, 30 | | Zerlegen (Drohne hinein) | 234, 100 |
| Motor vorne rechts | 126, 30 | | Ergebnis | 202, 100 |
| Motor hinten links | 34, 98 | | Inventar | 8 + 18·Spalte, 142 + 18·Zeile |
| Motor hinten rechts | 126, 98 | | Schnellleiste | 8 + 18·Spalte, 200 |

Flächen (x0, y0 bis x1, y1, exklusiv): Titelleiste 2, 2 – 310, 15 (Titel bei 8, 6; Amber-Marke bei 3–4); Bauplan-Feld
7, 17 – 169, 128 (Raster alle 8 px, Quad-Draufsicht: Mitte 88, 72, Arme zu den Motor-Slot-Mitten, Propellerkreise r = 16 mit
Drehrichtungspfeil „Props in“: vorne links cw, vorne rechts ccw, hinten links ccw, hinten rechts cw); Rumpf 75, 28 – 101, 109;
3D-Vorschau 176, 17 – 305, 91 (Verlauf mit Bodenraster); darunter links der Bau-Pfeil 171, 100 (24 × 16, kommt direkt aus dem
Bauplan-Feld und zeigt auf das Ergebnis: Teile → Drohne) und der Ergebnis-Rahmen 196, 94 – 224, 122 (Amber), rechts davon getrennt
das eigene Zerlege-Feld 229, 94 – 305, 122 mit Amber-Ring um den Slot (Zerlegen hat keinen Pfeil, damit es nicht wie „ergibt die
Drohne“ wirkt); Kennwerte-Feld 176, 124 – 305, 219 (Amber-Linie bei y = 125; content zeichnet seine Trennlinie bei 123 und Text ab 129 mit 4 px Innenabstand links/rechts); unter „Zerlegen“ steht klein „Drohne → Teile“, unten mittig im Bauplan die Legende der Drehrichtungspfeile (59 – 118, y 116); Inventar-Beschriftung bei
8, 131. Textfarben für den dunklen Hintergrund aus `ui.client.theme.Palette`: Beschriftung `TEXT` (`0xFFE8E8E8`), gedämpft `TEXT_MUTED` (`0xFFB0B0B0`), Akzent `ACCENT` (`0xFFFFBB00`); Flächen der Textur entsprechen `PANEL`/`FIELD`/`BORDER`
(die Vanilla-Standardfarbe `0xFF404040` ist darauf nicht lesbar).

## Funke

Handfunke im EdgeTX-Stil (`tools/assets/world/remote.py`, Vertrag `docs/design/gameplay-loop-v2.md` §11.2/§11.3).
Modellpixel, 16 = 1 Block; Front (Display, Gimbals) zeigt nach **+Z** (Süden), Antenne nach +Y. Textur 64×64
(`texture_size` [64, 64]), jede Fläche hat ihren eigenen Bereich (automatisch gepackt; Gehäuse 2 Texel je Pixel,
Display, Teller und Knüppel 4 Texel je Pixel).

| Teil (Elementname) | from | to | Hinweis |
|---|---|---|---|
| `housing` | (1.5, 3, 7) | (14.5, 10, 10) | Anthrazit, Kanten per Textur, Tasten + rote Status-LED unter dem Display, Akkufach hinten |
| `grip_left` / `grip_right` | (1.5, 0.5, 7) / (11, 0.5, 7) | (5, 3, 10) / (14.5, 3, 10) | Gummi, geriffelt |
| `display` | (6, 6.5, 10) | (10, 9.5, 10.1) | nur Fläche `south`, `light_emission` 6, LCD `#0E2A24` mit `#7CFFB2` (Kopfzeile, Quad-Symbol, zwei Trimm-Balken, Balken; bewusst **keine Ziffern**, weil eine fest eingebackene Spannung neben dem echten Funk-HUD falsch aussah) |
| `gimbal_plate_left` / `_right` | (2.25, 4.75, 10) / (10.25, 4.75, 10) | (5.75, 8.25, 10.25) / (13.75, 8.25, 10.25) | Amber-Ring um Metallfläche |
| `gimbal_left_shaft`, `gimbal_left_cap` | (3.75, 6.25, 10.25), (3.5, 6, 12.5) | (4.25, 6.75, 12.5), (4.5, 7, 13.25) | **nur** in `remote.json` |
| `gimbal_right_shaft`, `gimbal_right_cap` | (11.75, 6.25, 10.25), (11.5, 6, 12.5) | (12.25, 6.75, 12.5), (12.5, 7, 13.25) | **nur** in `remote.json` |
| `handle_left`, `handle_right`, `handle_bar` | (5, 10, 8), (10.5, 10, 8), (5, 11.5, 8) | (5.5, 11.5, 9), (11, 11.5, 9), (11, 12, 9) | Tragebügel (Metall), gemeinsame Box (5, 10, 8)–(11, 12, 9) |
| `switch_0..3` | x = 2.5, 4, 12, 13.5 (± 0.25), y 10–11 | z **9–9.5** | Amber-Spitzen; **nur** in `remote.json`, sonst bewegt sie `RemoteGimbalModel` (seit 0.5.0) |
| `antenna_joint` | (12.75, 10, 7.75) | (13.75, 11, 8.75) | Gelenk |
| `antenna` | (12.875, 11, 7.875) | (13.625, 15, 8.625) | dunkel, schlanker als die Vertragsbox (12.75…13.75, 7.75…8.75) |

Abweichungen vom Vertrag: Die Schalter sitzen bei z 9–9.5 statt 8.25–8.75, weil der Schalter bei x = 13.5 sonst in der
Antenne (x 12.75–13.75, z 7.75–8.75) stecken würde; der Tragegriff ist ein Bügel aus drei Elementen in der Vertragsbox
(plus 0,5 px Bügelstärke nach oben), die Antenne ist 0,75 px dick. Der Knüppelbereich bleibt unverändert.

**Gimbal-Drehpunkte** (Mitte der Tellerfläche, Neutralstellung der Knüppel): links **(4, 6.5, 10.25)**, rechts
**(12, 6.5, 10.25)**. `remote_body.json` ist `remote.json` ohne die vier Knüppel- und die vier Schalter-Elemente (gleiche
Reihenfolge, gleicher `display`-Block); der Validator prüft, dass kein Element von `remote_body` in eine Knüppel-Box ragt und
dass `remote.json` genau `switch_0..3` hat.

**Bewegte Teile im Special-Modell** (`RemoteGimbalModel`, seit 0.5.0 für den Empfänger-Assistenten): die Knüppel wie
bisher, die vier Schalter an derselben Stelle wie im Item-Modell (Drehpunkt am Fuß (x, 10, 9.25), Fuß 0,5 px Metall
`#5F6770`, Spitze 0,5 px Amber, ±40° gekippt; in Ruhe senkrecht wie im Item-Modell) und drei Tasten über den gemalten
Tasten unter dem Display ((7, 4.5), (8.5, 4.5), (10, 4.5), 1×1 px, 0,25 px vor der Front, 0,22 px Hub; die rechte Amber).
In der Hand und in der Flugschule stehen Schalter und Tasten in Ruhe, nur der Assistent bewegt sie.
Element-Gruppen (`groups`, Blockbench-Format, von Vanilla beim Laden ignoriert): `body` in beiden Modellen,
`gimbal_left`/`gimbal_right` nur in `remote.json` mit `origin` = Drehpunkt und je Schaft + Kappe als Kinder.

`textures/entity/remote/gimbal.png` (16×16) für `RemoteGimbalRenderer`: Zeilen 0–7 Schaft (Metall `#8C949E`, Zeile 0
heller, Spalten 0 und 15 dunkler), Zeilen 8–15 Kappe (Gummi `#1B1E23` mit leichtem Raster), Zeile 8 komplett Amber
`#FFBB00` (Ring). Eigene Bereiche (vom Schaft unberührt): Schalter-Fuß (6–7, 0) `#5F6770`, Schalter-Spitze (6–7, 2) Amber,
Tasten (9–11, 0–1) `#5F6770`, Amber-Taste (12–14, 0–1).

Item-Definition genau wie §11.3 der Design-Spec: `select` auf `display_context`, `gui|ground|fixed|on_shelf` →
`propwash:item/remote`, sonst `composite` aus `propwash:item/remote_body` und `minecraft:special` mit Basis
`propwash:item/remote_body` und Typ `propwash:remote_gimbals` (registriert der Code).

`display` (in beiden Modellen gleich; Werte im Vorschau-Renderer mit den Vanilla-Transformationen abgestimmt):

| Kontext | rotation | translation | scale | Ergebnis |
|---|---|---|---|---|
| `firstperson_righthand` / `_lefthand` | −34, ∓26, 0 | ∓2.5, 4.0, −2 | 0.36 | nur noch, wenn die Funke in der Nebenhand liegt und die Haupthand etwas hält (oder beim Benutzen eines Items bzw. Fernrohr): unten rechts, Front zur Kamera gedreht; links gespiegelt |
| `thirdperson_righthand` / `_lefthand` | 22, 0, 0 | −2.9, 3, 1.7 | 0.6 | nur noch das Halten mit **einer** Hand (Vanilla-Armhaltung `ITEM`: andere Hand belegt, Rüstungsständer, Mobs): Griff in der Hand, Funke waagerecht quer vor Hüfte/Bauch, Front zum Träger gekippt, Antenne oben. Beidhändig zeichnet `drone.client.RemoteHoldPose` die Funke selbst (siehe unten), dieses Display gilt dann nicht |
| `gui` | 22, 0, 0 | 0, 0.25, 0 | 0.92 | Frontansicht leicht von oben, Display und beide Gimbals klar, ≈ 90 % des Slots |
| `ground` | 0, 0, 0 | 0, 1.5, 0 | 0.24 | Bildprüfung: mit 0,4 lag die fallengelassene Funke größer als ein Ladegerät neben ihr |
| `fixed` / `on_shelf` | 0, 180, 0 | 0, 0, 0 | 0.6 / 0.8 | Front zeigt aus dem Rahmen |
| `head` | −78, 180, 0 | 0, 5.25, 0.5 | 0.4 | klein und fast flach auf dem Kopf liegend (vorher 0.625 aufrecht wie ein Hut); der Rundgang zeigt die Funke in der Hand eines Rüstungsständers mit Armen, auf dem Kopf die Brille |

Im Spiel geprüft (Integration Welle 2b, Bildschirmfotos aus dem Selbsttest in `docs/screenshots/`): Die erste Person war
mit Skalierung 0.68 zu groß (≈ 38 % Bildbreite, verdeckte im Sichtflug die Drohne) und ist jetzt 0.5 bei
Verschiebung (−3.5, 3.5, −3) (`items_remote_battery_charger.png`, `race_gates.png`). Dritte Person
(`pilot_third_person.png`, vor dem Bauch, Rückseite mit Antenne zum Betrachter), Kopf auf dem Rüstungsständer, Rahmen und
Boden (`remote_display_contexts.png`) passen ohne Änderung. Die Knüppel des `RemoteGimbalRenderer` stehen in den Tellern
(linker Knüppel ohne Funke ganz unten, Gas zu), auch in der Hand des eingefrorenen Piloten (`pilot_from_drone.png`).

Abschluss-Runde: In der ersten Person hält der Spieler die Funke jetzt mit beiden Händen, sobald die Nebenhand leer
ist (`mixin.drone.client.FirstPersonHandsAndItemsRendererMixin` am Anfang von `submitArmWithItem`, Logik in
`drone.client.RemoteFirstPerson`): Aufbau wie die Vanilla-Karte mit zwei Händen (gleiche Position und Neigungskurve
nach Blickwinkel, Arme über Vanilla `renderMapHand`), die Neigung beim Blick geradeaus aber nur 70° statt 85°, damit die
Front sichtbar bleibt; die Funke sitzt mit Skalierung 0,7 und 0,06 tiefer in der Kartenebene, die Griffe liegen in den
Händen. Beim Blick nach unten steht sie aufrecht vor dem Spieler (`remote_first_person.png`), beim Blick geradeaus liegt
sie flach unten in der Mitte (`remote_first_person_ahead.png`, `race_gates.png`). Gezeichnet wird sie mit eigenem
Render-State im Kontext `NONE`, das `firstperson`-Display oben gilt dafür nicht. Bildprüfung, vierte Runde: `RemoteFirstPerson.HOLD_SCALE`
0,6 (Review-Runde, vorher 0,78) verkleinert Funke und Hände gemeinsam um die Griffe, die Funke nimmt jetzt etwa ein Fünftel
der Bildbreite ein und endet deutlich über der Hotbar (vorher ≈ 34 %, dann ≈ 25 %; FOUNDATION §15 nennt denselben Wert).
Bildprüfung, achte Runde: Die Arme endeten unter den Griffen, die Funke wirkte wie auf den Unterarmen abgelegt. Jetzt
sitzen die Armenden über den Griffen (`HAND_Y` 0,46 statt 0,35, `HAND_X` 0,085, `HAND_Z` 0,035 zur Kamera hin), die
Daumen (Oberkante der Armquader) reichen bis an die Knüppel.
Bildprüfung, neunte Runde: Die Hände griffen noch nicht, die Funke schwebte zwischen den Fäusten und saß so hoch und mittig,
dass sie im Sichtflug den unteren Teil der Gates und den Boden davor verdeckte. Jetzt sitzt sie tiefer und näher (`AHEAD_Y` −0,41,
`DOWN_Y` −0,35, `DEPTH` 0,57, Neigung geradeaus −56°, nach unten −18°), die Hände liegen höher und weiter außen an den Griffen
(`HAND_Y` 0,52, `HAND_X` 0,07, `HAND_Z` 0,05) und sind nach innen (`HAND_ROLL` 14°) und zu den Knüppeln hin (`HAND_PITCH` −10°)
gedreht, sodass die Daumen auf den Knüppel-Tellern liegen und die obere Bildmitte frei bleibt.

Playtest 2026-09-25 (im Sichtflug in der ersten Person saß die Funke zu weit oben im Bild):
Beim Blick nach unten auf die Drohne stieg die Funke bis etwa ein Drittel der Bildhöhe, die Unterarme kamen waagerecht von
den Seiten, und ein Item in der Nebenhand wurde zusätzlich groß rechts gezeichnet. Neu (gilt vor den Absätzen oben):
- **Wann beidhändig** (`RemoteFirstPerson.held`): Funke in der Haupthand, egal was die Nebenhand hält, oder Funke in der
  Nebenhand bei leerer Haupthand; nicht beim Fernrohr und nicht, solange ein Item benutzt wird (dann Vanilla mit dem
  `firstperson`-Display). Das Nebenhand-Item wird währenddessen **nicht** gezeichnet (Mixin bricht `submitArmWithItem`
  für die Nebenhand ab) und erscheint wieder, sobald die Funke nicht mehr gehalten wird.
- **Lage:** `HOLD_SCALE` 0,5 (vorher 0,6), `AHEAD_Y` −0,36, `DOWN_Y` −0,35, `AHEAD_TILT` −50°, `DOWN_TILT` −36° (vorher −56°/−18°,
  die Funke richtete sich beim Blick nach unten auf und stieg). Oberkante des Gehäuses bei etwa 24 % der Bildhöhe von unten
  (geradeaus) bis 26 % (Blick ab 45° nach unten), Antennenspitze 30–34 %, Breite etwa 16 % der Bildbreite bei 16:9 (12 % bei
  21:9, 17,5 % bei 16:10). Die Front zeigt etwa 8° über die Blicklinie zum Auge, Knüppel und Display bleiben lesbar.
- **Arme:** Fäuste an den Griffen (`HAND_X` 0,05, `HAND_Y` 0,48, `HAND_Z` 0,08), jeder Arm dann um seinen Faustpunkt um
  `FOREARM_PITCH` 55° nach unten geschwenkt (Faustpunkt einmalig aus derselben Kette wie `renderMapHand` und dem Armmodell
  berechnet). Die Unterarme kommen dadurch schräg aus den unteren Bildecken, die Schulterenden liegen außerhalb des Bildes.
- **FOV und Seitenverhältnis:** Hände und Items werden mit fester HUD-FOV 70° gezeichnet (`Camera#calculateHudFov`), die
  Lage bezogen auf die Bildhöhe ist daher unabhängig vom FOV-Regler und vom Seitenverhältnis. Nachgerechnet mit dem
  Vorschau-Renderer (`modelrender.render_camera`, Kette wie im Mixin) bei 16:9, 16:10 und 21:9 und Blick 0°, 20°, 40°, 60°.
- **Dritte Person:** gleiche Regel (`RemoteHoldPose.holdingArm`, Funke in der Hand des Hauptarms oder im Nebenarm bei leerem
  Hauptarm), das Item der anderen Hand blendet `ItemInHandLayerMixin` aus. Armhaltung und Lage der Funke unverändert.
- Selbsttest „drone: Funke beidhändig in der Ego-Ansicht“ (mit Fackel in der Nebenhand) und „… in der dritten Person“ (ebenso)
  prüfen die Regeln; Bildschirmfotos `remote_first_person_los.png` (Sichtflug, Truhe in der Nebenhand, Sichtfeld 70) und
  `remote_first_person_los_fov110.png` (Sichtfeld 110).

Nachtrag Welle 2b (Bildprüfung): In der dritten Person hingen beide Arme herab und die Funke schwebte vor dem Bauch. Jetzt
hält der Spieler sie mit beiden Händen: `mixin.drone.client.HumanoidModelMixin` (`HumanoidModel#setupAnim`, TAIL) ruft
`drone.client.RemoteHoldPose` auf, sobald eine Hand die Funke hält — beide Arme `xRot` −50° (+0,4 rad beim Schleichen),
`yRot` ∓30° nach innen; eine Hand mit einem anderen Item bleibt bei Vanilla. Nicht beim Schwimmen, Gleiten, Schlafen oder
Benutzen eines Items. `views.py` bildet dieselbe Haltung für `propwash:item/remote` nach.

Playtest 2026-09-24 (in der dritten Person sah die Funke falsch gehalten aus): Die Funke hing weiter an der rechten
Hand (Display `thirdperson_righthand` relativ zum rechten Arm) und wirkte von der Seite und von hinten wie einhändig
rechts gehalten und verdreht. Neu (gilt vor dem Absatz oben):
- **Wann beidhändig:** nur Spieler (`AvatarRenderState`, also auch andere Spieler und der Pilot der Flugschule), eine
  Hand hält die Funke, die andere ist **leer** (seit 2026-09-25: Funke im Hauptarm genügt, das andere Item wird ausgeblendet), nicht Zuschauer, nicht beim Schwimmen, Gleiten, Schlafen, Riptide oder
  Benutzen eines Items (`RemoteHoldPose.holdingArm`). Egal ob Haupt- oder Nebenhand, Rechts- oder Linkshänder.
- **Arme** (`mixin.drone.client.HumanoidModelMixin`, `HumanoidModel#setupAnim` TAIL): beide `xRot` −40° (+0,4 rad beim
  Schleichen, dazu ±0,025 rad ruhiges Atmen, auf beiden Armen gleich), `yRot` ∓23° nach innen plus `body.yRot` (folgt dem
  Schlag-Schwung), `zRot` 0. Die Hände (Armende, Armraum (∓1; 9,5; 0) px) liegen dann bei (∓3,5; 9,3; −6,0) Modellpixeln:
  vor unterer Brust/Bauch, etwa 6 px vor dem Körper (Bildprüfung: bei −50° und Handpunkt 8 px wirkten die Arme von der
  Seite steil erhoben und die Funke schwebte über dem Unterarm). Echte Ellbogen gibt es im Blockmodell nicht, die Arme bleiben gerade.
- **Funke** (`mixin.drone.client.ItemInHandLayerMixin`, `ItemInHandLayer#submitArmWithItem` HEAD, abgebrochen nur für die
  Hand mit der Funke): gezeichnet mit eigenem Render-State im Kontext `NONE` (`RemoteHoldAccess#propwash$remoteHold` auf
  `AvatarRenderState`, gefüllt am Ende von `AvatarRenderer#extractRenderState` mit dem echten Stack, damit die eigenen
  Knüppel live mitlaufen; fehlt er, z. B. in der Flugschule, löst `submit` ihn aus der Stack-Kopie auf). Lage im
  Modellraum: Mitte der beiden Handpunkte am Armende (Armraum (∓1; 9,5; 0) px), `Y` = Mittel der Arm-`yRot`, `X` = Mittel der Arm-`xRot`
  minus −40° (Schleichen und Atmen kippen die Funke mit), dann `Z` 180°, `X` −55°, Skalierung 0,6, Griffpunkt (8; 3,5; 8,5)
  px des Modells in die Handmitte. Ergebnis: Funke mittig vor dem Körper, Querachse waagerecht, Fläche ≈ 35° gegen die
  Waagerechte, Front mit Knüppeln und Display nach oben zum Gesicht, Oberkante mit Antenne vorn oben, Griffe in beiden
  Händen, Breite ≈ 0,46 Blöcke.
- **Andere Fälle:** Rüstungsständer, Mobs und Spieler mit der Funke im Nebenarm bei belegtem Hauptarm nutzen das Display `thirdperson_*` (Tabelle
  oben) an der Vanilla-Hand. Erste Person, GUI, Boden, Rahmen und Kopf bleiben unverändert.
- Vorschau: `views.py` (`player_quads`, `one_handed`) bildet beides nach; Selbsttest „drone: Funke beidhändig in der dritten
  Person“ prüft Armhaltung, Lage und Neigung und dass die Funke gezeichnet wird, Bildschirmfotos `pilot_third_person.png`
  (vorn), `pilot_third_person_side.png` (Seite), `pilot_third_person_back.png` (F5 von hinten).

Akkus in der ersten Person (Nebenhand neben der Funke) waren mit 9 px Zielgröße riesig, weil der lange Pack schräg zur
Kamera zeigt: `tools/assets/parts/kit.py` setzt für die Kategorie `battery` in `firstperson_righthand` 1,6 px
(`CATEGORY_TARGET_PX`), dreht den Pack nur leicht (`CATEGORY_ROTATION` 5°, 20°, 0° statt 10°, 45°, 0°) und hebt ihn an
(`CATEGORY_OFFSET_PX` −0,4, 4,4, 1,13). So liegt er samt Stecker ganz im Bild und ist etwa halb so breit wie die Funke –
wie ein echter 6S-Pack neben einem Handsender; die übrigen Teile bekommen seit 06.10. 9 px × Handfaktor ihrer echten
Größe (Abschnitt „Größenregel der Items“, „Teile in der Hand“), die 1,6 px der Akkus bleiben.
Am Boden (`ground`) sind Akkus kleiner als andere Teile: 6 px statt 7 px auf der längsten Kante (`CATEGORY_TARGET_PX`),
also 0,37 Block. Die frühere Regel „4,2 px am Boden, 9 px im Rahmen“ (24.09.) maß nur die Stirnseite; die Länge des Packs
blieb unbegrenzt, ein 1S-Akku lag 1,4 Blöcke lang am Boden (Abschnitt „Größenregel der Items“).

Akku-Proportionen (Bildprüfung, achte Runde): Die 6S-Packs waren fast so hoch wie lang und standen wie ein Turm auf der
Drohne. Jetzt flach und lang wie echte Packs, Länge in Flugrichtung, Beschriftung auf den Längsseiten, Gurt quer um den
Pack: `b6s_1300` 78 × 36 × 34 mm (L × B × H, vorher 75 × 35 × 42), `b6s_1100` 74 × 34 × 31 mm (vorher 72 × 35 × 36).

## Ladegerät

`tools/assets/world/charger.py`, Vertrag §11.4. Modell mit Front nach **Norden** (−Z); der Blockzustand ist ein
`multipart`, der jedes Teil mit `y` = 0/90/180/270 für `facing=north|east|south|west` dreht: je `state=idle|charging|done`
das Gehäuse `propwash:block/battery_charger_<state>` (Display) und je Schacht `bay_<0..3>=empty|charging|full` das
Schachtmodell `propwash:block/battery_charger_bay_<i>_<zustand>` (LED und Akku im Schacht), zusammen 12 + 48 Teile. Die
Blockeigenschaften `bay_0..3` setzt `BatteryChargerBlockEntity` bei jeder Änderung des Inhalts und beim Laden
(`content.BayState.of(stack)`), damit der Block zeigt, was in welchem Schacht steckt. Die drei Gehäusemodelle haben
dieselbe Geometrie (Elternmodell `minecraft:block/block`), nur Front-Textur und Display-Leuchten unterscheiden sich.

| Teil (Elementname) | from | to | Hinweis |
|---|---|---|---|
| `housing` | (1, 0, 2) | (15, 5, 14) | Oberseite = Boden der Schächte |
| `lid_front`, `lid_back`, `lid_rib_0..4` | y 5–8 um die Schächte | | Deckel mit 4 Schächten |
| Schächte (Aussparungen) | x = Mitte ± 1.25 für Mitten 3.5, 6.5, 9.5, 12.5; z 5.5–10.5 | y 5–8 | je 2,5 × 3 × 5 px, Kontakte vorn |
| `front_plate` | (1.5, 1, 1.5) | (14.5, 6, 2) | Blende |
| `led_<i>` (Schachtmodell) | Mitten x = 12.5, 9.5, 6.5, 3.5 für Schacht 0–3 (± 0.75; von vorn gesehen wie im Menü von links nach rechts), y 4–5 | z 1.4–1.5 | nur Fläche `north`, Textur `#led` = `front_idle` (leer), `front_charging` (lädt), `front_done` (voll) |
| `pack_<i>` (Schachtmodell, nicht bei `empty`) | x = Mitte ± 1, y 5, z 6 | y 9.5, z 10 | Akku ragt 1,5 px aus dem Deckel; Textur `battery_charger_pack` (helle Hülle mit dunklem Etikett, oben schwarz mit Balancer-Kabel und gelbem Stecker). Die vier Seiten haben `tintindex` = Schachtnummer, die Oberseite keinen: content färbt die Hülle je Akku-Typ ein (siehe unten) |
| `display` | (2, 1.5, 1.4) | (7, 3, 1.5) | nur Fläche `north`; x 2–7 liegt vom Spieler aus gesehen rechts unten (siehe Hinweis) |

- Mini-Display: sitzt von vorn gesehen **rechts unten** (x 2–7; bei Blickrichtung Süd liegt Ost/+X links). Die Spec
  nannte zuerst „links unten“, ihre Koordinaten ergaben aber rechts unten; entschieden in Welle 2b: Koordinaten bleiben,
  die Spec ist angepasst. Links unten wäre x 9–14 (`charger.DISPLAY`, eine Zeile im Generator).
- Bounding-Box (1, 0, 1.4)–(15, 8, 14); alles außer Blende, LEDs und Display liegt in der Code-Form
  `Block.box(1, 0, 2, 15, 8, 14)` (Validator prüft beides).
- Gehäuse: `idle` Display aus, kein Leuchten; `charging` Display amber (Blitz + halb voller Akku); `done` Display grün
  (Haken + voller Akku); das Display leuchtet in `charging`/`done` mit `light_emission` 12.
- Schächte wie im Ladegerät-Menü: `empty` LED dunkelgrau, kein Akku; `charging` LED gelb `#FFB000`, Akku; `full` LED grün
  `#3BFF6A`, Akku. Leuchtende LEDs haben `light_emission` 12.
- Texturen 32×32 mit Standard-UV (2 Texel je Pixel): `front_<state>` (Nordfläche von Gehäuse, Deckel und Blende; LED-Farbe
  im Feld (0, 0)–(3, 2), das die LED-Flächen per `uv` nutzen), `top` (Deckel und Schachtböden; Feld (30, 0)–(32, 3) für
  die Schachtwände), `side` (Ost/West/Süd, Lüftungsschlitze, Amber-Linie), `bottom`. Partikel = `side`.
- Akkufarbe: Die Hülle der Akkus im Block hat dieselbe Farbe wie das Akku-Item (`content.Batteries.packColor`: 1S lila `#6B2FB3`,
  4S 650 orange `#F07C1E`, 6S 1100 rot `#D4312A`, 6S 1300 blau `#2F6FD6`, Li-Ion türkis `#1A8F8B`). Dafür meldet content je Schacht
  eine `BlockTintSource` an (`ClientRegistrar.blockTints`), die den Akku aus dem Block-Entity liest; ohne Block-Entity (Flugschule)
  bleibt die Hülle blau (6S 1300). Das Item-Icon färbt seine drei Akkus über `tints` in `items/battery_charger.json`
  (blau, rot, orange).
- Item: `items/battery_charger.json` → `propwash:item/battery_charger` (Elternmodell `block/battery_charger_inventory`:
  Gehäuse `charging` mit Schächten voll, lädt, lädt, leer, damit Akkus und LEDs das Icon auf dunklen Slots erkennbar
  machen; GUI-Ansicht größer und mittig, weil das Gerät nur halbe Blockhöhe hat). Gehäusefarben `#343941`/Kante `#565E69`, etwas heller als die GUI-Slots.

## Signal-Repeater

`tools/assets/world/repeater.py` (Teil von `generate.py`, geprüft von `validate.py` → `check_repeater`). Ein Modell
`propwash:block/signal_repeater`, gebaut für den Boden (Befestigung bei y = 0, Antennen nach oben). Der Blockzustand
dreht es wie einen Vanilla-Knopf: `face=floor` mit y = 0/90/180/270 für `facing=north|east|south|west`, `face=wall`
zusätzlich x = 90 (Oberseite zeigt von der Wand weg), `face=ceiling` mit x = 180 und um 180° gedrehtem `facing`; ohne
`uvlock`, die Texturen drehen mit.

| Teil (Elementname) | from | to | Hinweis |
|---|---|---|---|
| `base` | (3, 0, 4) | (13, 1, 12) | Montageplatte, Metall mit vier Schrauben, Unterseite mit `cullface` |
| `housing` | (4, 1, 5) | (12, 3.5, 11) | Gehäuse Anthrazit `#343941`, Amber-Linie `#FFBB00` an den Seiten wie beim Ladegerät, ohne Unterseite |
| `antenna_0`, `antenna_1` | (5, 3.5, 7.5) / (10, 3.5, 7.5) | (6, 8, 8.5) / (11, 8, 8.5) | Stummelantennen: SMA-Fuß gold, schwarze Hülle mit Rillen, rote Kappe |
| `led` | (7.25, 3.5, 9.25) | (8.75, 3.6, 10.25) | Status-LED auf dem Gehäuse, 0,1 Pixel darüber (kein Z-Fighting), `light_emission` 12, Textur animiert (1,5 s grün, 0,3 s gedimmt) |

- Begrenzung (3, 0, 4)–(13, 8, 12); alles außer der LED liegt in der Code-Form aus Körper (3, 0, 4)–(13, 3.5, 12) und
  Antennenleiste (5, 3.5, 7.5)–(11, 8, 8.5), die `SignalRepeaterBlock` für jede Lage dreht (`Shapes.rotateAttachFace`).
  Antennenpunkt für die Funkrechnung: Mitte, 6/16 Block vor der Befestigungsfläche.
- Texturen 32×32 mit Standard-UV (2 Texel je Pixel), jedes Element nimmt den Ausschnitt, an dem es im Block sitzt.
- Item: `models/item/signal_repeater.json` baut auf dem Blockmodell auf; GUI wie ein Block (30°/225°, Skala 1,0), am
  Boden 0,45, in der Hand 0,55 (erste und dritte Person, angehoben, damit das ganze Modul im Bild ist), im Item-Rahmen flach an der Wand.

## GUI Ladegerät/Wartung

`tools/assets/world/menus.py`, gleicher Stil und gleiche Palette wie die Werkbank-GUI (`guikit.py`). Beide Texturen
256×256, Panel ab (0, 0), Rest transparent. Zeichnen mit
`graphics.blit(RenderPipelines.GUI_TEXTURED, TEX, leftPos, topPos, 0, 0, W, H, 256, 256)`. Slot-Koordinaten wie
`Slot#x/y`; der Rahmen liegt bei x − 1, y − 1 (18 × 18). Titelleiste 2, 2 – W − 2, 15 mit Amber-Marke (Rückfall); im Spiel
übermalt der Code sie mit dem gemeinsamen Propwash-Kopf `ThemeDraw.brandedHeader(…, x + 2, y + 2, x + W − 2, 14, Titel, …)`:
Fläche `HEADER`, „PROPWASH FPV“, Trennstrich, Titel, Amber-Linie in Zeile 15 (Werkbank, Ladegerät, Wartung).

**`textures/gui/battery_charger.png`, Panel 176 × 207**

| Element | Koordinaten |
|---|---|
| Ladeschacht-Rahmen (Textur) | `(x − 5, 19) – (x + 21, 88)` für x = 26, 62, 98, 134 (1 px Rand `#323841`, oben eine Lampenfassung `(x + 3, 21) – (x + 13, 25)`) |
| Schachtlampe (Code) | `(x + 4, 22) – (x + 12, 24)`: leer aus, lädt = Amber pulsierend (1 Hz), voll = Grün |
| Akku-Slots | x = 26, 62, 98, 134; y = 30 (leere Slot-Fläche; das Akku-Symbol `gui/slot/battery` zeichnet Vanilla über `Slot#getNoItemIcon` nur bei leerem Slot) |
| Balkenrinne (Textur, innen leer `#0D0F12`) | `(x + 1, 51) – (x + 15, 75)`, 1 px Rahmen `#4A525E`, Skalenstriche rechts bei 25/50/75 % |
| Ladebalken (Code) | `(x + 2, 52) – (x + 14, 74)`, von unten |
| Prozent (Code, Kleinschrift) | zentriert bei y 78 (Trennlinie bei y 76, darunter 4 px Luft bis zum Schachtrand) |
| Redstone-Leiste (Textur: dunkles Feld `(7, 90) – (169, 111)`, Lampenfassung um `(11, 94) – (21, 98)`) | Code: Lampe `ThemeDraw.lamp` (aus/grün), ab x 25 zwei Zeilen bei y 92 und 101: Zustand („Redstone-Boost: aus/an“) und was ein Redstone-Signal bewirkt; Tooltip mit dem ganzen Satz |
| Inventar-Beschriftung | 8, 114 |
| Inventar / Schnellleiste | 8 + 18·Spalte, 125 + 18·Zeile / y 183 |

**`textures/gui/drone_service.png`, Panel 236 × 244** (Wartung der Drohne: Akku, die vier Props und der Kamerawinkel; Konstanten
`DroneServiceMenu`, `DroneServiceScreen`, `menus.SERVICE_*`)

| Element | Koordinaten |
|---|---|
| Prop-Feld (Blaupause mit Raster, Rahmen von oben: Arme, Rumpf `52, 44 – 76, 88` mit Amber-Strich vorn, Prop-Kreise) | 7, 18 – 121, 110 |
| Prop-Slots (wie die Drohne von oben, Positionen 0–3 wie `PartWear`) | vorne links 17, 33 · vorne rechts 95, 33 · hinten links 17, 75 · hinten rechts 95, 75 |
| Zustand je Prop (Code) | Balken `(x − 3, y + 19) – (x + 19, y + 21)` in der Farbe von `PartCondition.barColor`, Prozent bzw. „kaputt“ bei y + 23, zentriert unter dem Slot; ist der Text breiter als der Balken, bündig mit dessen Außenkante (linke Spalte links, rechte Spalte rechts); beschädigte Props (< 0,9) mit farbigem Slot-Rahmen, beim Tragen passender Props alle vier Amber |
| „▲ Vorne“ (Code) | zentriert über dem Prop-Feld bei y 21 |
| Rumpf-Beschriftung (Code) | Rahmen-Item des Builds mittig im Rumpf (Symbol 8 px über der Mitte), darunter „Rahmen“ in Kleinschrift; Tooltip über dem Rumpf nennt zuerst den Rahmen |
| Vorschaufeld (Code: `ThemeDraw.previewStage` + drehende Drohne) | 125, 18 – 229, 64 |
| Akku-Slot | 131, 81 (Amber-Ring 128, 78 – 150, 100; Akku-Symbol nur bei leerem Slot über `Slot#getNoItemIcon`) |
| Info-Feld (dunkel, 1 px Amber-Linie oben) | 154, 68 – 229, 110: Akku-Name, Ladung · Spannung, Ladebalken bei y + 27 |
| Hinweiszeile (Code) | 38, 112 (bündig mit dem Inventar, höchstens bis x 229): was ein Klick mit dem getragenen Item bewirkt (Amber = tauscht, Gelb = andere Sorte braucht vier, Rot = passt nicht) |
| Kamera-Leiste (dunkel, 1 px Amber-Linie oben) | 7, 124 – 229, 150; links das Winkel-Feld (Blaupause mit Grundlinie) 10, 126 – 34, 148: Code zeichnet den Bereich des Kamerahalters als Amber-Bogen, den Rahmen-Standard als grauen Punkt und den Winkel als Amber-Zeiger |
| Kamera-Leiste, Inhalt (Code) | „Kamerawinkel“ bei 38, 128, rechts in Kleinschrift Sichtfeld der Kamera und Props im Bild; Zeile bei y 137: `−` (12 × 11), Regler im Bereich des Halters („28° · Halter 0–50°“, Amber bei eigenem Winkel), `+`, `↺` (Standard) bis x 226 |
| Inventar-Beschriftung | 38, 154 (4 px Luft unter der Kamera-Leiste) |
| Inventar / Schnellleiste | 38 + 18·Spalte, 164 + 18·Zeile / y 222 |

Die Vorschau (`docs/asset-sheets/world_preview.png`, unterer Teil) zeigt beide GUIs mit nachgestellten Code-Inhalten
(Balken, Text, Items), dazu Funke (Ansichten, GUI-Icon, erste/dritte Person) und Ladegerät (Zustände, Ausrichtungen).
