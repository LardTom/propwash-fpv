# Bodenkontakt: Props, Ducts und Akku versinken nicht im Boden

Stand 0.5.0. Anlass: Am Boden steckten Props und Prop-Guards oft sichtbar im Boden. Gewünscht war eine Lösung ohne
Licht- und Schattenfehler.

## 1 Befund

Die Kollisionsbox der Drohne ist fest und aufrecht (`AirframeSpec.hitboxWidth` × `hitboxHeight`). Ihr Fuß ist der
tiefste Hüllpunkt in aufrechter Lage, der Schwerpunkt liegt am Boden immer `comAboveFoot` über dem Fuß – egal, wie die
Drohne geneigt ist. Der Renderer dreht das Modell um den Schwerpunkt und richtet seinen tiefsten Punkt am tiefsten
Punkt der Flugmodell-Hülle aus (`DroneVisuals.placeInWorld`). Aufrecht passt das genau, geneigt ragt die untere
Hälfte durch die Fläche, auf der die Box steht.

Wie tief (Hüllpunkte unter dem Fuß der Box, gemessen mit dem Flugmodell):

| Lage | Whoop 65 | Cinewhoop 3″ | 5″ Freestyle | 5″ Race | 7″ |
|---|---|---|---|---|---|
| 36° geneigt (Angle-Modus, Knüppel gehalten) | 2 mm | 32 mm | 54 mm | 27 mm | 78 mm |
| auf der Seite | 21 mm | 66 mm | 101 mm | 94 mm | 136 mm |
| kopfüber | schwebt 5 mm | 20 mm | 16 mm | schwebt 11 mm | 11 mm |
| nach einem Crash liegen geblieben (11,5° schief) | 0 mm | 5 mm | 15 mm | 0 mm | 20 mm |

X-Class 13″ schräg gekippt: bis 49 cm.

Wann das auftrat:

1. **Scharf am Boden mit Knüppelausschlag** (Angle-Modus, Airmode) und bei **schräger Landung**: Der Flugregler hält
   die Neigung, der Boden hält im Flugmodell nur die Höhe des Schwerpunkts.
2. **Nach einem Crash**: Die unscharfe Drohne richtete sich nur kriechend auf (Aufrichtmoment `m·g·a·sin θ` gegen das
   Reibmoment des Bodenkontakts) und schlief ein, sobald sie langsamer als 0,1 rad/s drehte – bei 11,5° Schräglage,
   nach etwa 2,5 s. So blieb sie liegen, die tiefe Seite 15–20 mm im Boden.
3. **Lose Drohne** (Pilot hört mitten im Flug auf, Funkverlust ohne Rückflug): Sie fiel mit ihrer letzten Fluglage zu
   Boden und behielt sie für immer.
4. **Kopfüber**: Was über dem Schwerpunkt höher ist als die Box unter ihm (Akku oben), steckte im Boden; flache Oberseiten
   (Whoop, Racer mit Akku unten) schwebten.
5. **Schneeschicht, Seelensand, Schlamm, Honig**: Ihre Kollisionsform ist niedriger als das, was man sieht (Schneeschicht,
   Seelensand und Schlamm 12,5 cm, Honig 6,25 cm). Eine 3 bis 7 cm hohe Drohne verschwand darin ganz und ließ
   sich nicht mehr anklicken, weil der Block vor ihr getroffen wird.

Kein Befund: aufrecht ruhend auf Stein, Stufen, Treppen und Teppich (Kollisionsform gleich sichtbarer Form), Akku unter
dem Rahmen und Kamera (die Hüllpunkte kennen beide), Drohnengröße 1,0× (ausgerichtet wird am tiefsten Punkt, das gilt
für jede Darstellungsgröße), Interpolation (die eigene Drohne wird je Frame gesetzt, bei fremden zählt der Versatz
zwischen Tick- und Bildposition mit, siehe 2.1).

Nachtrag aus der Gegenprüfung (09.10.): „Kein Befund aufrecht“ stimmte nur gegen die Hülle. Die Hülle nahm je Teil die
acht Ecken seines Quaders; beim um 28° geneigten Videomodul (Kamera, Sender, Antennen) liegt eine leere Quader-Ecke
weit unter dem Modell, und die Platzierung stellte diesen Punkt auf den Fuß der Box. Aufrecht ruhende Drohnen
schwebten deshalb sichtbar: 5″ Freestyle 27 mm, 7″ 17 mm, 11 von 20 Rahmen aus Just More Parts 9–28 mm; Mantelrahmen in
Schräglage 7–20 mm (Quader-Ecke außerhalb der runden Mäntel). Der Selbsttest sah es nicht, weil er mit derselben Hülle
maß. Siehe 2.5.

## 2 Lösung

### 2.1 Renderer: Modell steht auf seinem tiefsten Punkt

`drone.client.render.GroundContact` (reines Java), `DroneVisuals.restOnGround`, `QuadcopterEntity.groundGap`.

- `lowestAboveFoot` = Höhe des tiefsten Hüllpunkts über dem Fuß der Box in der gezeichneten Lage (negativ: ragt
  darunter, positiv: schwebt darüber).
- `gap` = freier Weg unter der Box bis zur nächsten Fläche (Blöcke, weiche Flächen aus 2.4, Gate-Teile), höchstens so
  weit gesucht, wie das Modell reichen kann. Gemessen wird von einer um 2 cm angehobenen Box aus, damit eine Position,
  die durch Rundung hauchdünn im Block liegt, die Fläche nicht übersieht.
- `rise` = Bildposition minus Tick-Position (Interpolation fremder Drohnen).
- Versatz des Modells nach oben: `max(0, −lowestAboveFoot − (gap + rise))`, nach unten für schwebende Lagen:
  `−max(0, lowestAboveFoot − (gap + rise))`.

Der Versatz wächst stufenlos, je näher die Fläche kommt: 1 m über dem Boden 0, beim 40° gerollten 5-Zöller 2 cm über
dem Boden 41 mm, am Boden 61 mm. Es gibt keinen Sprung beim Aufsetzen. Aufrechte Drohnen (Versatz unter 0,5 mm) fragen
die Welt gar nicht ab, die eigene FPV-Drohne auch nicht (ihr Rumpf wird nicht gezeichnet).

Das sieht aus wie eine echte Drohne, die über ihre Aufstandskante kippt: Die tiefe Seite bleibt am Boden, der
Schwerpunkt hebt sich.

### 2.2 Flugmodell: unscharfe Drohne fällt flach

`sim.GroundRest`, `SimTuning.restFlat` (Standard an).

Ab 3,4° Schräglage (`sin θ ≥ 0,06`) wirkt am Boden das volle Moment der Aufstandskante `m·g·a` statt `m·g·a·sin θ`,
zwischen 1,15° und 3,4° weich eingeblendet, darunter unverändert. Nur unscharf, nur am Boden, nur unter 4 rad/s und
unter halbem Schwebeschub (die Bedingungen des bisherigen Aufrichtmoments). Ergebnis: Aus 30° liegt die Drohne nach
0,3 s flach (vorher 2,5 s bis 11,5°), schläft nach gut einer Sekunde mit unter 0,5° Schräglage ein; kopfüber
entsprechend flach auf dem Rücken.

Flug, Start, Landung und alles mit scharfen Motoren bleiben bit-gleich:

- `builtin_regression.txt` ist unverändert und gilt weiter – der Test rechnet sie mit `restFlat = false`.
- `builtin_ground_rest.txt` hält die Flüge mit der Regel fest (54 Einträge).
- `BuiltinRegressionTest.groundRestLeavesArmedFlightUntouched` vergleicht beide Läufe Frame für Frame: gleich bis zum
  ersten Frame, in dem die Drohne unscharf am Boden liegt.

### 2.3 Lose Drohne kippt flach

`drone.GroundSettle` (reines Java), aufgerufen aus `QuadcopterEntity.tickLoosePhysics`.

Eine Drohne ohne Piloten, die am Boden liegt, dreht sich je Tick um höchstens 24° auf die nähere flache Lage
(aufrecht oder auf dem Rücken), der Kurs bleibt. Aus 90° sind das vier Ticks. In der Luft bleibt die Lage, wie sie ist.

### 2.4 Weiche Flächen: Schnee, Seelensand, Schlamm, Honig

`drone.SoftGround`, Block-Tag `propwash:drone_surface`.

Für Blöcke im Tag zählt für Drohnen die sichtbare Form als Kollisionsform (zusätzlich zur Vanilla-Kollision, im
selben Schritt wie die Gate-Teile). Die Drohne liegt obenauf, lässt sich anklicken, die FPV-Kamera steht über dem
Schnee. Eine Drohne, die schon im Block steckt (alte Welt, frisch gefallener Schnee), kommt frei heraus – Formen, in
denen die Box schon liegt, halten sie nicht fest. Wer auf einer solchen Fläche liegt, gilt als am Boden
(`onGround`), die Fallgeschwindigkeit wird gelöscht; das gilt jetzt auch auf Gate-Balken.

Datenpakete können das Tag erweitern oder Blöcke herausnehmen. Pulverschnee ist bewusst nicht dabei: Er lässt alles
durch, was keine Lederstiefel trägt.

### 2.5 Hülle aus dem gezeichneten Netz

`drone.client.render.VisualHull` führt für den Bodenkontakt eigene Punkte (`lowestPoint`), getrennt von den
Quader-Ecken, die weiter Größe, Sichtkugel und Vorschau bestimmen (dort ändert sich nichts):

- **Feste Teile** (Rahmen, Stack, Akku, Videomodul mit Kameraneigung, Zubehör, Motoren): alle Eckpunkte des
  gezeichneten Modells (`ItemStackRenderState.visitExtents`), bei Teilen ohne Modell die Punkte ihrer Ersatzform.
  Doppelte Punkte fallen weg; über 4 096 Punkten bleiben nur die äußersten in 4 096 Richtungen (`HullMath.support`,
  Verlust unter 0,5 mm bei 180 mm Radius). Die größte Hülle im Test hat 4 542 Punkte.
- **Props** je Platz und Zustand – ganz, abgenutzt, beschädigt, gebrochen, drehend (Scheibe) –, so wie der Renderer
  das Modell wählt (`DroneVisuals.propVariants`): Rotationskörper des gezeichneten Modells (Umriss aus Radius und
  Höhe jedes Eckpunkts in 17 Richtungen, je Umrisspunkt ein Ring aus 32 Punkten, der den Kreis von außen umschließt).
  Ein Prop versinkt damit in keiner Lage; das frühere Achteck auf Nabenhöhe ließ Blattspitzen bis 4–9 mm, verbogene
  Blätter bis 25 mm im Boden.

Gemessen mit Just More Parts 1.1.0 und einem weiteren Add-on, 174 Aufbauten (29 Vorlagen, jede auch mit jedem
Zubehör) × 5 Prop-Zustände × 888 Lagen: das Netz liegt höchstens 0,17 mm unter der Hülle, die Hülle höchstens 0,80 mm
unter dem Netz, aufrecht 0,00 mm.

## 3 Licht und Schatten

Verschoben wird nur das Modell (eine Translation vor dem Zeichnen). Das Licht kommt weiter von der Entity-Position, der
runde Schatten bleibt am Entity, und der Schattenpass von Iris zeichnet denselben Zustand – Modell und Schatten bewegen
sich gemeinsam. Es gibt keinen Tiefentrick und keine eigene Render-Schicht, also nichts, was durch Blöcke scheinen oder
falsch beleuchtet sein könnte. Auf einer Schneeschicht zeichnet Minecraft für kein Entity einen runden Schatten; das
ist bei Drohnen jetzt genauso.

## 4 Grenzen

- Stehende Props zählen als Rotationskörper ihres Modells (2.5). In gekippter Lage kann die Blattspitze bis etwa 2 cm
  über dem Boden stehen, wenn kein Blatt nach unten zeigt.
- Fracht im Greifer gehört weder zur sichtbaren noch zur physikalischen Hülle: Steht eine scharfe Drohne mit Fracht
  am Boden, hängt der Gegenstand bis zum Lösen (10 Ticks nach dem Entschärfen) im Boden. Richtig lösen lässt sich das
  nur mit Fracht in der Kollisionshülle des Flugmodells; das Bild allein anzuheben ließe die Drohne über dem Boden
  schweben. Offen für 0.5.1.
- Die Fläche ist die Ebene unter der Kollisionsbox. Ragt eine gekippte Drohne über eine Stufenkante, wird sie trotzdem
  angehoben, obwohl dort Luft wäre.
- Scharf am Boden hält der Flugregler die Lage wie bisher; nur das Bild steht richtig. Echte Kontaktpunkte an der Hülle
  (`flight-model-rigid-body.md` §8.3) wären ein Umbau des Bodenkontakts im Flugmodell.
- Die FPV-Kamera bleibt an der Physik und wird nicht angehoben; gegen den Blick durch Blöcke schützt weiter
  `FpvEyeGuard`.
- Renn-Geister (`DroneGhost`) und Drohnen in Flugschul-Szenen haben kein Entity und bleiben, wie sie waren.

## 5 Tests

- JUnit: `GroundContactTest` (jede Vorlage in 2 600 Lagen steht auf ihrem tiefsten Punkt, Versatz stufenlos, nichts bei
  ungültigen Werten), `GroundRestTest` (fällt aus 4° bis 88° flach und schläft, kopfüber flach, scharf unverändert,
  unter 1,15° bit-gleich), `GroundSettleTest`, `BuiltinRegressionTest` (drei Tests, siehe 2.2).
- Selbsttest `drone: Bodenkontakt` (`GroundContactSelfTests`): Prüfplatz mit Stein, Stufe, Treppe, Schneeschicht 1 und
  3, Teppich, Seelensand, Schlamm und Honig. Jede Drohnen-Vorlage (eingebaut, Beispiel-Addon und alle angemeldeten
  Add-on-Vorlagen) fällt auf jede Fläche, liegt auf der sichtbaren Oberfläche und steht in 14 Lagen mit ihrem tiefsten
  Punkt darauf (± 1,5 mm); zur Gegenprobe wird gemessen, wie tief sie ohne Korrektur steckt. Der tiefste Punkt kommt
  dabei aus dem gezeichneten Netz (`MeshProbe`: jeder Eckpunkt jedes Teils, Props als Rotationskörper), nicht aus der
  Hülle des Renderers. Der Schritt „Hülle folgt dem gezeichneten Netz“ vergleicht beide für jede Vorlage, jedes
  Zubehör und jeden Prop-Zustand in 888 Lagen (Netz höchstens 0,5 mm unter der Hülle, Hülle höchstens 1,5 mm unter dem
  Netz). JUnit `HullMathTest` deckt die Rechnung ab. Dazu: in der Luft kein
  Versatz, 2 cm über dem Boden anteilig, lose gekippte Drohnen liegen nach dem Fall flach.
- Bildschirmfotos `ground_contact_<tilted|inverted|nose|snow|soul_sand>_<before|after>.png`
  (`bildschirmfotos: Bodenkontakt`, nur Fabric mit `-PpropwashScreenshots=true`).
- Mit Iris, Sodium und Complementary Reimagined (Abschnitt 5 in `docs/testing.md`): `iris:` und `drone: Bodenkontakt`
  bestehen mit aktivem Shaderpack, die Bilder der angehobenen Drohnen zeigen weder Schatten- noch Lichtfehler.
- Stand der Läufe: Fabric gefiltert 23 Schritte, mit Just More Parts 1.1.0 und einem weiteren Add-on 29 Vorlagen, NeoForge
  voll 530 Schritte, JUnit 2 600 Tests.
