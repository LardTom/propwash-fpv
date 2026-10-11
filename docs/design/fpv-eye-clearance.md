# FPV-Auge nie im Block (drone)

Fehler aus dem Playtest vom 02.10.: Nach manchen Abstürzen und Landungen sah die FPV-Kamera unter den Boden, man konnte
wie mit X-Ray in Höhlen schauen. Gilt für das eigene FPV-Bild, das Zuschauen und Action-Cam-Aufnahmen (alle über
`FpvView.cameraPose` → `mixin.drone.client.CameraMixin`).

## 1. Ursache

Was man sieht, wenn das Auge oder die Nahebene im Block liegt: Die Flächen zwischen zwei festen Blöcken zeichnet
Minecraft gar nicht (Face-Culling gegen den Nachbarn), die Flächen des Blocks selbst zeigen nach außen und fallen von
innen weg (Rückseiten-Culling). Hinter dem Gras bleibt nur, was zu einer Höhle oder zum Grundgestein zeigt – Röntgenblick.
Drei Wege führen dorthin:

1. **Die Kollisionsbox dreht sich nicht mit.** `QuadcopterEntity#getDimensions` ist eine feste, achsparallele Box
   (Breite = Spannweite mit Props, Höhe = Hülle der aufrechten Drohne). Am Boden liegt der Schwerpunkt deshalb in jeder
   Lage `−hitboxFootOffset` (Whoop 15 mm, 5″ 25 mm, X-Class 22–38 mm) über der Boxunterkante. Die Kamera sitzt
   `lensForward` vor und `lensUp` über dem Schwerpunkt (5″ rund 50–60 mm vor, Deadcat und H-Frame 90–110 mm,
   X-Class 160–300 mm). Kippt die Drohne beim Crash mit der Nase nach unten, wandert das Auge um fast `lensForward`
   nach unten – bis 27 cm unter die Grasoberfläche. Die Hülle der Simulation (`AirframeSpec#hullY`, zwölf Punkte)
   dreht zwar mit, wirkt aber nur auf Drehmomente am Kontaktpunkt, nicht auf die Höhe der Box.
2. **Die Nahebene ist für ein FPV-Weitwinkel zu groß.** Vanilla rechnet mit `Camera.PROJECTION_Z_NEAR` = 0,05 m.
   Bei 104–160° horizontalem Sichtfeld (16:9) ist das Nahebenen-Rechteck 13–57 cm breit, seine Unterkante liegt bei
   waagerechtem Blick 3,6–16 cm unter dem Auge. Schon eine **aufrecht gelandete** Drohne mit dem Auge 2–3 cm über dem Gras schneidet so unten im Bild
   einen Streifen Boden weg.
3. **Schneeschichten.** `SnowLayerBlock` hat als Kollision eine Schicht weniger als die sichtbare Form
   (`getCollisionShape` = `SHAPES[layers − 1]`, `getVisualShape` = `SHAPES[layers]`): Entities sinken 12,5 cm in den
   Schnee ein, bei einer einzelnen Schicht sogar ganz. Jede Drohne hat dann das Auge im Schnee, auch aufrecht.

## 2. Messung (JUnit `sim.FpvEyeSurveyTest`)

Jede Drohne (eingebaute Vorlagen, Beispiel-Addon-Rahmen, mit `PROPWASH_CALIBRATION_ADDON` alle Just-More-Parts-Presets),
jede Szene (Boden, eine und vier Schneeschichten, Decke, Wand, Halbstufe vor der Drohne, Treppenstufe, Teppich über
Luft) und 224 Lagen (4 Gierwinkel × 7 Nickwinkel × 8 Rollwinkel), Kamerawinkel Standard/Minimum/Maximum des Halters,
Sichtfeld des Videomoduls und 160°. Die Box liegt dabei so, wie die Kollision sie hinstellt (Unterkante auf der
Oberfläche, an Wand oder Decke anliegend). Ausschnitt mit Standardwinkel und eigenem Sichtfeld – **A n** = Auge n mm im
Block, **N** = nur die Nahebene im Block, „–“ = frei; nach dem Pfeil: Verschiebung des Auges mit Schutz bzw. „n x“ =
Auge bleibt, Nahebene x mm:

| Drohne | aufrecht | kopfüber | Nase unten | Nase 45° unten | Decke | Wand, Nase 30° unten | Schnee 1 | Schnee 4 |
|---|---|---|---|---|---|---|---|---|
| Whoop 65 mm | N → n 25 | N → 9 mm | **A 3** → 17 mm | N → 8 mm | N → 14 mm | N → 2 mm | **A 24** → 112 mm | **A 101** → 112 mm |
| Cinewhoop 3″ | N → n 24 | N → n 11 | **A 24** → 37 mm | **A 12** → 25 mm | N → n 34 | **A 4** → 16 mm | **A 15** → 118 mm | **A 110** → 118 mm |
| Freestyle 5″ | N → n 31 | N → n 18 | **A 34** → 50 mm | **A 20** → 36 mm | N → n 29 | **A 9** → 23 mm | **A 20** → 113 mm | **A 105** → 113 mm |
| Race 5″ | – | N → n 15 | **A 29** → 46 mm | **A 15** → 29 mm | N → n 24 | **A 4** → 15 mm | **A 22** → 108 mm | **A 103** → 108 mm |
| Long Range 7″ | N → n 23 | N → n 27 | **A 34** → 49 mm | **A 23** → 40 mm | N → n 35 | **A 11** → 26 mm | **A 20** → 115 mm | **A 105** → 115 mm |
| Beispiel Deadcat 5″ | N → n 34 | N → n 22 | **A 79** → 95 mm | **A 54** → 69 mm | N → n 33 | **A 34** → 47 mm | **A 17** → 114 mm | **A 108** → 114 mm |
| Beispiel X-Class 13″ | – | N → 10 mm | **A 181** → 197 mm | **A 111** → 127 mm | – | **A 66** → 81 mm | **A 37** → 97 mm | **A 88** → 97 mm |
| JMP Whoop 75 mm | N → n 30 | N → 9 mm | **A 11** → 26 mm | N → 13 mm | N → 14 mm | N → 5 mm | **A 24** → 110 mm | **A 101** → 110 mm |
| JMP Deadcat 5″ | N → n 28 | N → n 19 | **A 86** → 102 mm | **A 59** → 75 mm | N → n 31 | **A 37** → 51 mm | **A 17** → 115 mm | **A 108** → 115 mm |
| JMP H-Frame 5″ | N → n 28 | N → n 16 | **A 69** → 85 mm | **A 43** → 60 mm | N → n 27 | **A 25** → 40 mm | **A 21** → 113 mm | **A 104** → 113 mm |
| JMP X-Class 13″ H | N → n 46 | N → n 19 | **A 268** → 284 mm | **A 179** → 196 mm | – | **A 117** → 132 mm | **A 33** → 101 mm | **A 92** → 101 mm |

Über alle 224 Lagen hat jede Drohne am Boden 56–96 Lagen mit dem Auge im Block (alle mit der Nase oder der Oberseite
zum Boden), im Schnee alle 224. Betroffen sind also nicht „einige“, sondern alle Drohnen; je weiter die Kamera vor dem
Schwerpunkt sitzt (Deadcat, H-Frame, X-Class), desto tiefer.

## 3. Lösung (`drone.client.EyeClearance`, `drone.client.FpvEyeGuard`)

Ein Schutz auf der Render-Seite, der jedes Bild gilt (wie Vanillas Kamera der dritten Person, `Camera#getMaxZoom`):

- **Wo:** `CameraMixin` setzt in `Camera#update` direkt nach `this.fov = calculateFov(…)` (FIELD `fov`, PUTFIELD) das
  Auge um, bevor das Culling-Frustum entsteht, und reicht die Nahebene über `@ModifyArg` an `setupPerspective` weiter.
  Ohne FPV-Pose (`FpvView.cameraPose` false) bleibt alles Vanilla.
- **Was zählt als fest:** die sichtbare Form (`BlockState#getVisualShape`, wie `ClipContext.Block.VISUAL` der dritten
  Person): Schnee mit voller Schichthöhe, Stufen, Treppen, Teppiche, Glas; Pflanzen, Pulverschnee (eigener Nebel) und
  Flüssigkeiten nicht. Unter Wasser sieht man wie bisher Wasser (Nebel, kein Röntgen: Wasser und Lava verdecken keine
  Nachbarflächen).
- **Prüfung:** die ganze Sichtpyramide vom Auge bis zur Nahebene (nicht nur die vier Ecken) gegen alle Boxen der
  sichtbaren Formen, exakt mit Trennachsen (26 Achsen: Weltachsen, Pyramidenflächen, Kreuzprodukte der Kanten), mit
  2 mm Abstand (`MARGIN`). Je Box liefert der Test direkt die größte Nahebene, bei der die Pyramide frei bleibt.
- **Reihenfolge:**
  1. Pyramide bis 50 mm frei und Sichtlinie Schwerpunkt → Auge frei: **nichts ändern** (freie Luft bitgleich, auch die
     Projektion: `0.05F`).
  2. Auge frei, aber die Nahebene berührt etwas: **nur die Nahebene näher** holen, bis sie frei ist, höchstens bis
     `MIN_NEAR` = 10 mm. Das Auge springt nicht, die Landung sieht aus wie vorher, nur ohne Streifen.
  3. Auge im Block, hinter einer dünnen Fläche (Teppich über Luft, Scheibe) oder Nahebene auch bei 10 mm nicht frei:
     **Auge verschieben**, auf dem kürzesten Weg entlang der Flächennormalen (Kandidaten aus den Flächen der Boxen,
     erweitert um die Pyramide, je Achse die acht nächsten, dazu acht Punkte auf der Linie zum Schwerpunkt) – der erste
     Kandidat, dessen Pyramide frei ist und den der Schwerpunkt ohne Block dazwischen sieht. Steckt der Schwerpunkt
     selbst im Schnee, zählt die Sichtlinie vom nächsten freien Punkt über ihm.
- **Stetigkeit:** Die Verschiebung ist der kürzeste Ausweg, also stetig in der Lage (JUnit: Absinken in 1-mm-Schritten
  verschiebt das Auge nie mehr als 1 mm). Es gibt kein Glätten über mehrere Bilder – das ließe für ein paar Bilder
  Röntgenblick durch.
- **Shaderpacks:** Mit aktivem Iris-Shaderpack (`IrisCompat.shaderPackActive`) bleibt die Nahebene bei 50 mm, der
  Schutz verschiebt dann das Auge statt die Nahebene zu verkürzen. Iris liegt nicht in `.ref`; Shaderpacks rechnen
  Tiefe gewöhnlich mit einer festen Nahebene zurück, deshalb vorsichtshalber keine andere.
- **Kosten:** in freier Luft rund 0,1 µs Rechnung plus ein paar `getBlockState` um die Pyramide; mit Auge im Block im
  Mittel 16 µs (Schnee und Boden, gemessen in JUnit).

### Warum die Nahebene kleiner werden darf

26.3 rechnet mit umgekehrter Tiefe (`Projection#getMatrix` vertauscht near/far) in einem `D32_FLOAT`-Puffer
(`MainTarget`). Mit `isZZeroToOne` (Vulkan, OpenGL mit `GL_ARB_clip_control`) ist die Genauigkeit fast unabhängig von
der Nahebene. Auf macOS-OpenGL fehlt `GL_ARB_clip_control` (`GlHeuristics#createDeviceInfo`); dort wächst der kleinste
Tiefenschritt in der Ferne mit 1/near, bei 10 mm also fünfmal gröber als bei Vanilla. Deshalb nur bei Bedarf und nur
so weit wie nötig: gelandet typisch 20–35 mm, 10 mm nur in engen Lagen; in freier Luft bleibt es bei 50 mm.

## 4. Entscheidungen

- **Render-Schutz statt Kollisionsbox.** Die Box mit der Lage zu drehen oder die Kamera als Hüllpunkt in die Höhe der
  Box zu rechnen, wäre der physikalisch „richtige“ Weg, ändert aber Bodenkontakt, Abprallen und Absturzlagen – also
  Bewegungs-Goldens – und überschneidet sich mit `fix/crash-freeze` (Sim/Entity). Die Kamera ist bei den eingebauten
  Rahmen schon heute innerhalb der aufrechten Box (Höhe aus der Hülle), das hilft aber nicht, weil sich die Box nicht
  dreht. Kollision, Hülle und Goldens bleiben unverändert.
- **Sichtbare Form statt Kollisionsform**: nur so ist Schnee abgedeckt; gleiche Wahl wie Vanillas dritte Person.
- **Ganze Pyramide statt vier Ecken**: eine Blockkante kann zwischen den Ecken in die Nahebene ragen.
- **Erst Nahebene, dann Auge**: bei Landungen (der häufigste Fall) bleibt der Blickpunkt, wie er war.
- **Kürzester Weg statt immer zum Schwerpunkt**: zum Schwerpunkt hin wandert das Auge bei Nase-unten-Lagen zusätzlich
  weit nach hinten; der kürzeste Weg hebt es nur aus dem Boden. Die Sichtlinie zum Schwerpunkt verhindert trotzdem,
  dass das Auge durch dünne Flächen auf die andere Seite rutscht. Die Punkte auf der Linie zum Schwerpunkt bleiben als
  Kandidaten (wie bei Vanillas dritter Person) für enge Lagen.
- **Kein Glätten** (siehe oben).

## 5. Tests

- JUnit `drone.client.EyeClearanceTest`: freie Luft bitgleich (500 Zufallslagen), Landung nur mit näherer Nahebene,
  Auge unter dem Boden, Teppich über Luft und dünne Scheibe ohne Durchrutschen, Schnee, Ecke aus Boden und Wand,
  Shaderpack-Modus, stetiges Absinken, 3000 Zufallslagen in einer Welt mit Wand, Decke, Stufe und Treppe, Nahebene genau
  so klein wie nötig.
- JUnit `sim.FpvEyeSurveyTest`: die Messung oben als Prüfung – ohne Schutz klemmt jede Drohne (Schnee immer, Nase im
  Boden), mit Schutz in keiner Lage, keinem Kamerawinkel und keinem Sichtfeld. Mit `PROPWASH_EYE_SURVEY=<datei>`
  schreibt er die Tabellen.
- Selbsttest `drone.client.CameraClipSelfTests` (beide Loader): Prüfplatz mit Höhle unter dem Gras, Schnee 1 und 4,
  Wand, Treppe, Halbstufe und Decke; alle Vorlagen und Beispiel-Addon-Drohnen in zehn Lagen gegen die echten
  Blockformen (auch im Shaderpack-Modus); sechs echte FPV-Sitzungen in kritischen Lagen (Pose über den Simulations-
  Schnappschuss eingefroren) prüfen Kamera, Projektion und Nahebene von Vanilla. Mit Bildschirmfotos
  `fpv_clip_*.png`; dieselben Lagen ohne Schutz (Röntgenblick) zum Vergleich unter `build/fpv-clip-vorher/` (nicht im
  Repository).
