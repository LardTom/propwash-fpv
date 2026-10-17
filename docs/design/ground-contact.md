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

### 2.2 Flugmodell: unscharfe Drohne fällt flach (0.5.0 bis 0.5.1)

Bis 0.5.1 hielt das Flugmodell am Boden nur die Höhe des Schwerpunkts; ein künstliches Moment (`sim.GroundRest`,
`SimTuning.restFlat`) legte eine unscharfe Drohne auf die nähere flache Lage, aufrecht oder auf dem Rücken. Seit 0.5.2
stützt der Boden die Drohne an ihren Kontaktpunkten (§6); sie fällt von selbst flach, wie eine echte Drohne. Regel,
Schalter und die zweite Golden-Datei `builtin_ground_rest.txt` sind entfallen.

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
- Seit 0.5.2 (§6) hat das Flugmodell echte Kontaktpunkte; liegt eine Drohne schief auf einer Kante, hebt sich der
  Schwerpunkt, und die Kollisionsbox schwebt um diese Höhe über dem Boden. Der Renderer stellt das Modell weiter auf
  seinen tiefsten Punkt, der Versatz ist dann nahe null.
- Die FPV-Kamera bleibt an der Physik; seit 0.5.2 steht sie damit bei schief liegender Drohne so hoch wie bei einer
  echten. Gegen den Blick durch Blöcke schützt weiter `FpvEyeGuard`.
- Renn-Geister (`DroneGhost`) und Drohnen in Flugschul-Szenen haben kein Entity und bleiben, wie sie waren.

## 5 Tests

- JUnit: `GroundContactTest` (jede Vorlage in 2 600 Lagen steht auf ihrem tiefsten Punkt, Versatz stufenlos, nichts bei
  ungültigen Werten), `GroundSettleTest`; seit 0.5.2 `GroundSolverTest` und `LandingFlipTest` (§6.5).
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

## 6 Seit 0.5.2: Landung auf dem Kopf

Anlass: Beim Landen lag die Drohne ungewöhnlich oft auf dem Rücken. Gewünscht war die physikalische Ursache, nicht
eine Regel, die die Drohne aufrichtet.

### 6.1 Messung

`LandingFlipTest` mit `LandingCharacterization` (reines Java, `BoxWorld`): Die Drohne startet vom Boden, schwebt in
1 m Höhe und wird knapp über der Fläche in einen Aufsetz-Zustand gesetzt – Sinkrate, Fahrt, Schräglage, Drehraten
und Reaktionszeit des Piloten zufällig aus einer Familie, fester Startwert. Der gescriptete Pilot hält die Sinkrate,
nimmt nach der Reaktionszeit das Gas weg, schaltet nach 0,3 bis 1,5 s aus (Familie „Motoren laufen weiter“: erst nach
3 s) und lässt die Drohne 3 s liegen. „Auf dem Kopf“ heißt über 120° Schräglage am Ende, „auf der Seite“ 60° bis
120°.

Anteil der Landungen, die auf dem Kopf enden, 0.5.1 → 0.5.2 (je Feld 160 Landungen: je 40 mit Acro und Angle, mit und
ohne Crash-Disarm; Halbblock und Stufe 40 Landungen mit Acro und Crash-Disarm):

| Landung | Whoop 65 | Cinewhoop 3″ | 5″ Freestyle | 5″ Race | 7″ |
|---|---|---|---|---|---|
| sanft senkrecht | 5 → 0 % | 0 → 0 % | 0 → 0 % | 0 → 0 % | 0 → 0 % |
| sanft mit Fahrt | 5 → 0 % | 0 → 0 % | 0 → 0 % | 0 → 0 % | 0 → 0 % |
| sanft, Motoren laufen 3 s weiter | 5 → 0 % | 0 → 0 % | 0 → 0 % | 0 → 0 % | 0 → 0 % |
| schnell rutschend | 2 → 1 % | 0 → 0 % | 0 → 0 % | 1 → 0 % | 0 → 0 % |
| hart (1,5–3 m/s) | 4 → 10 % | 5 → 0 % | 2 → 0 % | 0 → 1 % | 0 → 0 % |
| Absturz (3–7 m/s) | 5 → 18 % | 6 → 0 % | 8 → 0 % | 9 → 4 % | 8 → 0 % |
| Halbblock-Kante | 30 → 5 % | 48 → 0 % | 8 → 0 % | 5 → 2 % | 2 → 0 % |
| von einer Stufe gerutscht | 20 → 2 % | 52 → 0 % | 22 → 0 % | 10 → 2 % | 15 → 0 % |

Familien: sanft 0,2–0,8 m/s Sinkrate, bis 8° schief (mit Fahrt 1–3 m/s, bis 15°), rutschend 4–8 m/s Fahrt, hart
1,5–3 m/s, Absturz 3–7 m/s Sinkrate und 2–10 m/s Fahrt. Halbblock: Die Drohne setzt mit der Mitte über der Kante eines
Stein-Halbblocks auf (0,5 m hoch, 1 m breit). Stufe: Sie setzt 4 cm vor der Kante auf und rutscht hinunter. Die
mittlere größte Schräglage nach dem Aufsetzen fiel ebenso, beim 5-Zöller nach harten Landungen von 30° auf 6°, beim
Cinewhoop von 79° auf 9°. Mit Just More Parts 1.1.0 (22 Vorlagen, je 20 Landungen) endet keine sanfte Landung auf
dem Kopf; hart, rutschend, Halbblock und Stufe höchstens 1 von 20.

### 6.2 Ursachen

1. **Ein einziger Stoßpunkt mit Haftreibung.** Beim Aufsetzen der Kollisionsbox wirkte der Stoß an *einem* Punkt: dem
   Mittel der Hüllpunkte im 3-mm-Band unter dem Schwerpunkt, bei der kleinsten Schräglage an einer einzelnen Ecke. Die
   Reibung hielt diesen Punkt fest; bei flachem Aufsetzen mit 0,3 m/s Fahrt drehte der Whoop danach mit über
   300 °/s, beim Aufsetzen auf eine Kante bekam die Drohne bis zur Kappung von 30 rad/s Drall. Bei einer echten
   Drohne fangen die anderen Füße die Drehung ab, im Modell gab es sie nicht.
2. **Am Boden hielt das Modell nur die Schwerpunkthöhe.** Die Hülle durfte durch den Boden drehen, ohne dass der
   Schwerpunkt steigen musste; eine Drehung kostete keine Energie. Das Aufrichtmoment wirkte nur unter 4 rad/s und
   unter halbem Schwebeschub, über 90° Schräglage legte es die Drohne auf den Rücken. Ein Drall aus 1. trug die Drohne
   so über 90° und dann flach auf den Rücken; Airmode und I-Anteil des Reglers am Boden drehten sie, weil nichts
   dagegenhielt, ebenso leicht weiter.
3. Stufen und Halbblöcke: Ein Aufsetzer auf einer Kante ist immer ein Ein-Punkt-Stoß, deshalb lagen hier bis zur
   Hälfte der Landungen auf dem Kopf.

Kein Befund: Crash-Erkennung und Crash-Disarm (die Familien bis „hart“ lösen keinen Crash aus, mit und ohne
Crash-Disarm gleich), Motor-Bremse und Prop-Schläge.

### 6.3 Lösung: Kontaktpunkte im Flugmodell

`sim.GroundSolver` (reines Java), aufgerufen aus `DroneSim` in jedem Substep (2 kHz) und beim Aufsetzen der Box.

- **Kontaktpunkte** (`AirframeSpec.groundX/Y/Z`, `groundUsed`): die zwölf Hüllpunkte (Prop-Spitzen, Motorböden, Akku,
  Kamera), dazu die vier Ecken der Akkufläche (bisher lag der Akku nur mit seiner Mittellinie auf, ein Race-Quad mit
  Akku unten kippte darauf bis 22° zur Seite) und bei Ducts der untere Rand der Ringe in Höhe des Rahmenbodens.
  Punkte, die innerhalb einer anderen Auflagefläche liegen (Rahmenmitte, Akku-Mittellinie, bei Ducts die Motorböden),
  zählen nicht mit – sie bremsen das Lösen, ohne etwas zu ändern.
- **Fläche**: die Ebene unter der Box, sobald sie aufsetzt, sonst der Strahl vom Schwerpunkt nach unten auf festen
  Boden (`SimEnvironment.solidDistance`, `ClipContext.Fluid.NONE` – Wasser trägt nicht). So berührt eine schiefe Drohne
  den Boden mit ihrer tiefsten Ecke, bevor die Box aufsetzt.
- **Stoß und Ruhe**: Je Punkt ein Normalimpuls ≥ 0 und Coulomb-Reibung bis μ·Normalimpuls, gelöst als projiziertes
  Gauss-Seidel (abwechselnde Reihenfolge, bis 64 Durchläufe, Abbruch bei Konvergenz, Warmstart aus dem vorigen
  Substep). Punkte, die in derselben Stoßdauer von 2 ms aufsetzen, teilen sich den Stoß – so verhält sich ein echter,
  nachgiebiger Rahmen; ein streng nacheinander gerechneter Stoß an zwei Ecken überschätzt den Drall. Rückprall ab
  0,4 m/s wie bisher (offene Rahmen 0,25, Ducts 0,40, mit der Abnahme bei hohen Geschwindigkeiten), der Akku ist
  weicher (0,15). Eindringen wird mit höchstens 0,3 m/s zurückgeschoben.
- **Box**: Die Kollisionsbox bleibt das Entity. Liegt die Drohne schief auf einer Kante, hebt sich der Schwerpunkt wie
  bei einer echten Drohne, und die Box schwebt darüber (die Bodenprüfung `supportGap` rechnet diese Höhe ab). Reicht die
  Hülle nicht bis unter die Box (kopfüber bei Whoop und Race-Quad, Akku unten), stützt die Box an den tiefsten
  Hüllpunkten; die Drohne kippt so in ihre Ruhelage auf dem Rücken statt schief auf der Box zu stehen.
- **Einordnung** wie bisher: Aufprallgeschwindigkeit ist die Sinkgeschwindigkeit des Schwerpunkts beim ersten Kontakt
  (Crash, harter Aufprall, Bump, Landung), ein Prop-Schlag zählt, wenn eine Prop-Spitze neu aufsetzt, die Drohne über
  45° geneigt ist und der Prop dreht. Kontaktbits für Verschleiß und Schaden: Akku-Ecken zählen als Akku, Duct-Ränder
  als Motorboden.
- Entfallen: Aufrichtmoment, Reibung am Schwerpunkt (bleibt nur, wo allein die Box trägt), Dämpfungsmoment am Boden,
  `GroundRest` und `SimTuning.restFlat`.

### 6.4 Kalibrierung

Flug in der Luft ist bit-gleich zu 0.5.1: Analyse, Vollgas-Läufe und Verschleiß in `builtin_regression.txt`
unverändert; `tools/calibration.sh` mit Just More Parts 1.1.0 liefert `calibration.txt`, `fall.txt` und
`throttle_drop.txt` byte-gleich, in `motion.txt` bleiben alle 1 245 Spezifikationen gleich, nur die Hashes der Flüge
mit Bodenkontakt sind neu. `GroundSolverTest.groundOnlyActsWithinReach` prüft Frame für
Frame, dass eine Drohne in 8 cm und 40 cm Höhe gleich fliegt, ob der Boden bekannt ist oder nicht. In
`builtin_regression.txt` ändern sich nur die 59 Einträge mit Bodenkontakt (`acro`, `angle`, `drop`, `worn`); beim
Fall aus 8 m weicht der erste Frame beim Aufsetzen ab (5-Zöller Frame 193, Whoop 209). `PropWearTest` prüft 22 m/s in
die Wand jetzt mit 100 statt 20 Seeds bei unveränderter Schwelle von 80 %: 0.5.1 kam auf 85 von 100 (16 von 20, genau
die Schwelle), 0.5.2 auf 84 von 100 – der Prop-Schaden an der Wand ist gleich, nur die Zufallsfolge danach ändert
sich mit den Bodenstößen.

### 6.5 Tests

- JUnit `GroundSolverTest`: schief abgelegt (4°–60°, vier Achsen, jede Vorlage) fällt sie auf die Füße, ohne mehr als
  3 mm einzusinken, und schläft; ab 120° bleibt sie auf dem Rücken; mit 15 rad/s Drehung am Boden dreht sie nicht
  durch den Boden; flaches Aufsetzen mit 0,5–3 m/s Fahrt erzeugt keinen Drall (unter 30 °/s); scharf mit Rollbefehl
  kippt sie über die Kante und hebt dabei den Schwerpunkt; der Boden wirkt nur in Reichweite (bit-gleich ohne Boden);
  eine Wasseroberfläche trägt nicht.
- JUnit `LandingFlipTest` (je Fall 20 Landungen, Halbblock und Stufe je 100): sanft, mit Fahrt und „Motoren laufen
  weiter“ bei keiner Vorlage auf dem Kopf oder der Seite; rutschend höchstens 5 %; hart höchstens 5 % (Whoop 25 %, siehe
  6.6); Absturz höchstens 10 % (Whoop 35 %); Halbblock und Stufe zusammen auf dem Kopf oder der Seite höchstens 10 %.
  Beim Rutschen über eine Stufe (0,5 m tief) bleibt der Whoop selten auf der Seite liegen (mit 300 Landungen je Fall
  rund 0,5 %, mit und ohne das Akku-Modell von 0.5.2); 20 Landungen mit null Toleranz hingen deshalb nur am Startwert. Mit `PROPWASH_CALIBRATION_OUT` schreibt der Test die Tabelle nach
  `landing.txt`, `PROPWASH_LANDING_RUNS` ändert die Anzahl je Fall, `PROPWASH_LANDING_TRACE=<rahmen>,<ACRO|ANGLE>,<Familie
  0–5>,<FLAT|SLAB_EDGE|STEP>,<Nummer>` schreibt den Verlauf einer Landung nach `trace.txt`.
- Angepasst: `CollisionTest` (Aufprall aus der Telemetrie statt aus dem Box-Stoß), `CrashFreezeTest` und der
  Selbsttest `drone: Absturz an einer Mauer` messen „am Boden“ am tiefsten Punkt der Drohne statt an der Box.
- Selbsttest `drone: Landung …` (`LandingSelfTests`): im Spiel senkrecht und mit Fahrt auf Stein, mit Fahrt in kurzes
  Gras und auf die Kante eines Stein-Halbblocks, Acro, nach dem Aufsetzen 1 s scharf mit Gas null – die Drohne muss
  aufrecht (unter 30°) mit dem tiefsten Punkt auf der Fläche liegen. Bildschirmfoto `landing_slab_edge.png`.
  Seit dem Spieltest vom 15.10. beginnt jede Landung unbeschädigt (vorher trug die Drohne die zufälligen Schäden aus
  dem Mauer-Absturz davor mit), und beide Rücksetzungen (auf den Boden, über die Fläche) gelten erst, wenn nicht nur
  die Entity, sondern auch die Simulation am Ziel ist und unter 5° liegt; sonst endet der Schritt mit „Rücksetzen …
  kommt nicht in der Simulation an“ statt später mit „liegt nicht aufrecht“. Die Meldung jeder Landung nennt die Lage
  der Simulation vor dem Scharfschalten und vor dem Kippen. `drone: Landung nach Kopflage – Rücksetzen erreicht die
  Simulation` legt die Drohne sechsmal kopfüber ab (mal noch nicht ruhend, mal schlafend, 0/10/20 Ticks Wartezeit) und
  landet danach wie „senkrecht auf Stein“.
  Anlass war ein Ausreißer (NeoForge, voller Lauf, 1 von rund 30 Läufen beider Loader): „senkrecht auf Stein“ lag
  danach mit 180° auf dem Kopf. Die Vermutung, die Simulation behalte die Kopflage aus dem Mauer-Absturz, ließ sich
  nicht bestätigen: Im Spiel kam die Rücksetzung in allen Durchgängen an (Simulation 0,0° vor dem Kippen), und im
  Rechenmodell kippt derselbe Fall weder mit zufälligen Schäden (400 Läufe), mit einem einzelnen zerstörten Teil noch
  bei 8–1000 Bildern pro Sekunde. Tritt er wieder auf, zeigt die Meldung, in welcher Phase die Lage falsch war.
- Stand der Läufe (14.10.): JUnit 2 789 Tests (dazu die 6 Tests mit Add-on-Daten mit Just More Parts 1.1.0), Fabric voll
  619 Schritte, Fabric gefiltert mit Bildschirmfotos 169 Schritte (`drone:`, `rescue:`, `guide:`, `parts-flight:`,
  `interact:` und die Bilder zu Landung, Absturz, Bodenkontakt, Heimflug und Lande-Lektion), NeoForge gefiltert
  101 Schritte (`drone:`, `rescue:`).

### 6.6 Grenzen

- Der Whoop überschlägt sich nach harten Landungen und Abstürzen jetzt öfter als in 0.5.1 (hart 4 → 10 %, Absturz
  5 → 18 %): Er ist sehr leicht, setzt mit dem schmalen Akku unter dem Rahmen auf und prallt mit den Ducts ab. In 0.5.1
  hatte ihn erst das künstliche Aufrichtmoment wieder auf die Füße gestellt. Im Angle-Modus fängt der Regler ihn
  meist ab (hart 5 %).
- Eine Ebene je Drohne: die höhere aus Box-Auflage und Strahl unter der Mitte. Hängt eine Ecke über einer Kante,
  trägt die Ebene sie weiter (wie die Box bisher).
- Die Höhe der Ducts ist nicht bekannt; ihr unterer Rand sitzt in Höhe des Rahmenbodens.

## 7 Seit 0.5.2: Abstürze enden nicht mehr fast immer aufrecht

Anlass: Nach dem Spieltest von 0.5.2 lag die Drohne nach Abstürzen gefühlt immer richtig herum. Gewünscht ist das
Verhalten eines flachen Kastens: sanfte Landungen bleiben aufrecht (§6), harte Treffer, Einschläge mit der Nase,
Mauern, Überschläge und Taumeln enden auf der Seite, auf der die Drohne zur Ruhe kommt – oft auf dem Rücken. Keine
Regel, die die Drohne aufrichtet oder umwirft.

### 7.1 Messung

`CrashOutcomeTest` mit `CrashCharacterization` (reines Java, `BoxWorld`, 144 Bilder/s, fester Startwert): Die Drohne
schwebt, wird in einen Ausgangszustand gesetzt und fliegt mit Acro, Crash-Disarm an (Standard der Welt-Regel) in den
Boden oder gegen eine Mauer. Der gescriptete Pilot nimmt 0,1–0,4 s nach der ersten Berührung das Gas weg und schaltet
0,2–0,8 s später aus; danach 4 s liegen lassen. „Auf dem Kopf“ heißt über 120° Schräglage, „auf der Seite“ 60°–120°
(an der Mauer angelehnt wird getrennt gezählt). Fälle:

- sanfte Landung und von einer Stufe gerutscht / Halbblock-Kante: die Familien aus §6.1 (sanft, mit Fahrt), Acro oder
  Angle, mit Crash-Disarm;
- Fall aus 15–60 cm mit Motoren aus, bis 20° schief, bis 1 m/s Fahrt;
- flach in den Boden: Bahn 10°–30° abwärts, Nase 0°–30° tiefer als die Bahn, bis 20° gerollt, 5, 10 und 20 m/s;
- steil in den Boden: Bahn 45°–80°, sonst wie flach;
- Kurve mit einem Prop in den Boden: 50°–90° Schräglage, 5–15 m/s;
- gegen eine Mauer: 5, 10, 20 m/s, 0,5–2,5 m hoch, bis 40° schräg, Nase 15°–50° unten;
- kopfüber aufgeschlagen: 150°–180° Schräglage, 1–5 m/s Sinkrate, bis 6 m/s Fahrt;
- Taumeln nach einem Prop-Schlag: zufällige Lage, 10–25 rad/s um eine zufällige Achse, aus 1–3 m, 2–10 m/s, Motoren aus.

Anteil auf dem Kopf (in Klammern auf der Seite), dieselben Startwerte für alle drei Stände:

| Fall (5″ Freestyle, je 100) | 0.5.1 | 0.5.2 | jetzt |
|---|---|---|---|
| sanfte Landung | 0 % | 0 % | 0 % |
| Fall aus 15–60 cm, Motoren aus | 4 % | 0 % | 0 % |
| von einer Stufe gerutscht | 11 % | 0 % | 0 % |
| Halbblock-Kante | 4 % | 1 % | 1 % |
| flach in den Boden, 5 m/s | 0 % | 0 % | 0 % |
| flach in den Boden, 10 m/s | 2 % | 0 % | 11 % |
| flach in den Boden, 20 m/s | 70 % | 0 % | 14 % |
| steil in den Boden, 5 m/s | 48 % | 82 % | 93 % |
| steil in den Boden, 10 m/s | 43 % | 57 % | 48 % |
| steil in den Boden, 20 m/s | 53 % | 60 % | 51 % |
| Kurve, Prop in den Boden | 18 % | 37 % | 37 % |
| Mauer 5 m/s | 65 % | 58 % (5 %) | 56 % (4 %) |
| Mauer 10 m/s | 53 % | 56 % | 50 % (1 %) |
| Mauer 20 m/s | 55 % | 41 % | 46 % |
| kopfüber aufgeschlagen | 100 % | 100 % | 100 % |
| Taumeln nach Prop-Schlag | 48 % | 45 % | 49 % |

Im Mittel der elf Absturz-Fälle (flach, steil, Kurve, Mauer, Taumeln) liegt der 5-Zöller jetzt in 41 % der Fälle auf
dem Kopf, in 0.5.2 waren es 40 %, in 0.5.1 41 % – der Unterschied liegt fast nur bei den flachen Einschlägen. Bei den
Mauer-Fällen zählt „auf der Seite“ auch an die Mauer gelehnte Drohnen. Andere Vorlagen (je 40, 0.5.1 → 0.5.2 →
jetzt):

| Fall | Whoop 65 | Cinewhoop 3″ | 5″ Race | 7″ |
|---|---|---|---|---|
| sanfte Landung | 5 → 0 → 0 % | 0 → 0 → 0 % | 0 → 0 → 0 % | 0 → 0 → 0 % |
| Fall aus 15–60 cm | 55 → 25 → 25 % | 95 → 0 → 0 % | 12 → 0 → 0 % | 0 → 0 → 0 % |
| von einer Stufe gerutscht | 22 → 8 → 8 % | 55 → 0 → 0 % | 8 → 0 → 0 % | 5 → 0 → 0 % |
| Halbblock-Kante | 20 → 2 → 2 % | 30 → 0 → 0 % | 2 → 0 → 0 % | 2 → 8 → 8 % |
| flach in den Boden, 10 m/s | 2 → 5 → 5 % | 8 → 0 → 0 % | 8 → 8 → 5 % | 0 → 0 → 8 % |
| steil in den Boden, 10 m/s | 12 → 60 → 50 % | 40 → 52 → 42 % | 48 → 38 → 20 % | 62 → 58 → 52 % |
| Kurve, Prop in den Boden | 0 → 15 → 15 % | 0 → 30 → 30 % | 0 → 2 → 2 % | 12 → 32 → 30 % |
| Mauer 10 m/s | 40 → 28 → 28 % | 52 → 48 → 50 % | 25 → 25 → 28 % | 45 → 52 → 55 % |
| kopfüber aufgeschlagen | 92 → 100 → 100 % | 62 → 80 → 80 % | 100 → 100 → 100 % | 98 → 100 → 100 % |
| Taumeln nach Prop-Schlag | 65 → 42 → 50 % | 45 → 38 → 58 % | 55 → 38 → 45 % | 42 → 48 → 55 % |

Im Spiel (Selbsttest `drone: Absturz-Statistik …`, Standard-Drohne, Acro, Crash-Disarm an, Steinboden und
Steinmauer, feste Folge der Ausgangszustände; die Bildzeiten schwanken, deshalb ist ein Lauf nicht bit-gleich
wiederholbar), Anzahl auf dem Kopf (auf der Seite):

| Fall | 0.5.2 Fabric | jetzt Fabric, nur Statistik | jetzt Fabric, alle `drone:` | jetzt NeoForge, alle `drone:` |
|---|---|---|---|---|
| sanft abgesetzt | 0/8 | 0/8 | 0/8 | 0/8 |
| flach 8–14 m/s | 0/10 | 3/10 | 0/10 | 1/10 |
| steil 6–12 m/s | 5/10 | 3/10 | 3/10 | 5/10 |
| Mauer 6–12 m/s | 3/10 (2) | 5/10 (1) | 4/10 (1) | 6/10 (1) |
| taumelnd aus 1–2,5 m | 5/10 | 5/10 | 5/10 | 6/10 |
| kopfüber aufgeschlagen | 8/8 | 8/8 | 8/8 | 8/8 |

Das Spiel verhält sich wie die Rechnung ohne Minecraft; danach dreht der Turtle-Modus die kopfüber liegende Drohne in
allen Läufen nach 11 Ticks wieder um.

### 7.2 Befund

Im Code von 0.5.2 gibt es kein Aufrichtmoment und keine Regel, die eine Seite bevorzugt (geprüft: `DroneSim`,
`GroundSolver`, `CollisionSolver`; `GroundSettle` gilt nur für Drohnen ohne Pilot und legt sie auf die nähere große
Fläche, oben wie unten). Nase voran, Mauern, Taumeln und Kopflagen endeten schon in 0.5.2 zufällig. Aufrecht blieben
dagegen alle flachen Einschläge – bei 20 m/s in 0.5.1 noch 70 % auf dem Kopf, in 0.5.2 keiner. Ursachen:

1. **Physik eines flachen Kastens:** Ein Normalstoß an einer tiefen Kante dreht die Drohne immer zurück zur Fläche (er
   hebt die tiefe Seite an). Kopfüber endet sie nur, wenn der Drall einen weiteren Bodenstoß übersteht (Rückprall) oder
   wenn sie über eine Kante stolpert, weil ein Teil im Boden hängen bleibt. 0.5.1 ließ die Hülle reibungsfrei durch den
   Boden drehen; der Drall des ersten Stoßes trug die Drohne einfach weiter herum (§6.2). Das war falsch und ist mit
   den Kontaktpunkten von 0.5.2 weg – damit sind aber auch die Überschläge nach flachen Einschlägen verschwunden.
2. **Rückprall bei hohen Geschwindigkeiten fast null:** Ab 30 % der Crash-Geschwindigkeit fiel die Rückprallzahl
   linear um bis zu 60 % (offene Rahmen 0,25 → 0,10, Akku 0,15 → 0,06). In 0.5.1 spielte das kaum eine Rolle, weil
   der Boden die Drehung nicht bremste. Mit echten Kontaktpunkten nimmt jeder zweite Stoß (das andere Ende des Rahmens
   schlägt auf) der Drohne fast den ganzen Drall.
3. **Teile rutschen statt sich zu verhaken:** Jeder Punkt hatte Gleitreibung μ 0,5. Prop-Spitzen und Arm-Enden, die
   mit hoher Geschwindigkeit aufschlagen, rutschten weiter, statt einzuhaken und die Drohne darüber stolpern zu lassen.

Kein Befund, aber mitgemessen: Im **Angle-Modus** richtet der Regler die Drohne am Boden auf, solange sie scharf ist
(steil 5 m/s: Acro 82 %, Angle 0 % auf dem Kopf, wenn erst der Pilot nach 0,3–1,2 s ausschaltet) – ein echter
Flugregler tut das auch. Bei **niedriger Bildrate** enden Mauer-Treffer öfter aufrecht, weil die Mauer nur einmal je
Bild stößt (5″, Mauer 5/10/20 m/s bei 30 Bildern/s auf dem Kopf: 0.5.1 33/37/30 %, 0.5.2 45/20/15 % bei 40 Flügen,
jetzt 38/36/30 %; ab 60 Bildern/s wie bei 144). Das gab es schon in 0.5.1.

### 7.3 Lösung

Nur der Bodenkontakt (`sim.GroundSolver`) ändert sich:

- **Rückprall nach dem Stoßgesetz für elastisch-plastischen Kontakt:** Über v₀ = max(0,3 · Crash-Geschwindigkeit,
  0,4 m/s) fällt die Rückprallzahl mit (v/v₀)^(−1/4) statt linear um 60 %. Bei Crash-Geschwindigkeit bleiben 74 % statt
  40 %, bei dreifacher noch 56 %. Darunter (alle sanften Landungen) ist nichts anders.
- **Verhaken beim Einschlag:** Prop-Spitzen, Motorböden (Arm-Enden) und die Kamera-Nase – nicht der Akku, nicht die
  Duct-Ränder – haken ein, wenn sie schneller als 0,3 · Crash-Geschwindigkeit aufschlagen: Ihre Reibungszahl steigt um
  den Faktor 1 + 4 · s (s von 0 bei 0,3 · Crash-Geschwindigkeit bis 1 bei Crash-Geschwindigkeit), offene Rahmen also
  bis μ 2,5, und bleibt so, bis der Punkt den Boden wieder verlässt. So graben sich harte, spitze Teile ein und lassen
  sich erst lösen, wenn sie freikommen; der flache Akku und runde Ducts rutschen weiter.
- Mauer-Treffer (`CollisionSolver`) behalten ihr bisheriges Rückprallgesetz: an ihnen hängt der Prop-Schaden
  (`PropWearTest`, 22 m/s in die Mauer).

### 7.4 Kalibrierung

Flug in der Luft bleibt bit-gleich (`GroundSolverTest.groundOnlyActsWithinReach`): Beide Änderungen greifen erst, wenn
ein Kontaktpunkt schneller als 0,3 · Crash-Geschwindigkeit (mindestens 0,4 m/s) auf den Boden trifft.
`builtin_regression.txt`: 64 von 158 Einträgen neu, alle mit Bodenstoß (`acro` 21, `angle` 16, `drop` 22, `worn` 5);
Analyse und Vollgas-Läufe unverändert. `tools/calibration.sh` mit Just More Parts 1.1.1: `calibration.txt`,
`fall.txt`, `throttle_drop.txt` und `battery_sag.txt` byte-gleich mit 0.5.2; in `motion.txt` bleiben alle 1 254
Spezifikationen gleich, neu sind die Hashes der Flüge mit Bodenstoß (`acro` 1 092, `angle` 868, `drop` 1 254).
`LandingFlipTest` und `PropWearTest` bestehen unverändert.

### 7.5 Tests

- JUnit `CrashOutcomeTest` (alle Fälle aus 7.1, 5″ je 240, andere Vorlagen je 120, rund 10 s): sanfte Landungen
  mindestens 98 % aufrecht; Fall aus geringer Höhe mindestens 95 % (Whoop 70 %); Stufe und Halbblock mindestens 85 %;
  kopfüber aufgeschlagen mindestens 75 % auf dem Kopf; Taumeln 25–75 %; Mauer und steil bei 10 m/s mindestens 15 % auf
  dem Kopf; frei auf der Seite höchstens 3 % (Whoop 10 %). Für den 5-Zöller zusätzlich: flach 5 m/s höchstens 10 %,
  flach 10 und 20 m/s je mindestens 5 % und 20 m/s nicht seltener als 5 m/s, steil 5 m/s mindestens 60 %, steil 10/20
  und Mauer 5/10/20 je 25–75 %, Kurve mindestens 20 %, im Mittel der Abstürze mindestens 30 % auf dem Kopf.
  `PROPWASH_CRASH_RUNS` ändert die Anzahl, `PROPWASH_CRASH_SEED` den Startwert (Standard 11), `PROPWASH_CRASH_ONLY=<Rahmen,…>`
  wählt Vorlagen, `PROPWASH_CRASH_FPS` die Bildrate, `PROPWASH_CRASH_MODE=ANGLE` den Flugmodus; mit
  `PROPWASH_CALIBRATION_OUT` entsteht `crash.txt`, `PROPWASH_CRASH_TRACE=<rahmen>,<Fall 0–15>,<Nummer>` schreibt den
  Verlauf eines Absturzes nach `crash-trace.txt`.
- **Schwellen mit binomialer Toleranz (seit dem Spieltest vom 15.10.):** Die Schwellen beschreiben die wahre Rate, nicht
  die Stichprobe. Vorher wurde der beobachtete Anteil direkt verglichen; einige Zellen liegen nahe an ihrer Schwelle
  (Whoop Mauer 10 m/s rund 17 % auf dem Kopf bei verlangten 15 %, Whoop Halbblock rund 94 % aufrecht bei 85 %), mit 40
  Läufen bestand der Test deshalb nur mit dem festen Startwert 11 sicher (Startwerte 1, 2026 und 99991 fielen durch).
  Jetzt ist jede Schwelle ein einseitiger exakter Binomialtest: Eine Zelle fällt nur durch, wenn die Zählung bei der
  Schwelle als wahrer Rate mit höchstens 0,1 % Wahrscheinlichkeit vorkäme (`ALPHA`). Bänder (25–75 %) sind zwei
  solche Tests. „Flach 20 m/s nicht seltener als 5 m/s“ ist ein Zwei-Stichproben-Test, das Mittel der Abstürze ein
  z-Test, beide auf derselben Stufe. Damit ein echter Rückschritt trotzdem auffällt, laufen mehr Flüge (5″ 240, andere
  120): `toleranceKeepsFalseAlarmsRareAndStillCatchesRegressions` rechnet für jede verwendete Schwelle nach, dass ein
  Rückschritt um 15 Prozentpunkte (5″) bzw. 20 Prozentpunkte (andere Vorlagen) mit mindestens 90 % erkannt wird, und
  `checksPassAtTheThresholdAndFailOnRegressions` prüft die Auswertung mit erfundenen Zählungen (genau an der Schwelle
  besteht, z. B. Whoop Mauer mit 2 % auf dem Kopf, sanfte Landung mit 8 % Überschlägen oder Taumeln mit 12 % fällt
  durch). Gegenprobe: 40 verschiedene Startwerte und Bildraten 60/240 bestehen. Läuft ein Fall von der Stufe so
  unglücklich, dass die Drohne erst nach der regulären Zeit aufsetzt (z. B. Prop an der Stufenkante, Drohne springt
  hoch), rechnet `LandingCharacterization` bis zu 8 s weiter, statt mit „keine Landung“ abzubrechen; alle Läufe, die
  vorher aufsetzten, bleiben bit-gleich.
- Selbsttest `drone: Absturz-Statistik …` (`CrashOutcomeSelfTests`): Steinfeld mit Mauer, je Fall 8–10 Flüge mit fester
  Folge, scharf in Acro (Taumeln ohne Motoren), vor jedem Flug unbeschädigt; sanft abgesetzt muss immer aufrecht
  enden, steil, Mauer und Taumeln je mindestens 2 von 10 und kopfüber mindestens 4 von 8 auf dem Kopf. Danach dreht
  der Turtle-Modus eine kopfüber liegende Drohne mit Roll wieder um (im Lauf nach 11 Ticks).
- Stand der Läufe (15.10.): JUnit 3 121 Tests (3 115 bestanden, die 6 mit Add-on-Daten mit Just More Parts 1.1.1
  bestanden), Fabric gefiltert mit Bildschirmfotos 179 Schritte (`drone:`, `turtle:`, `rescue:`, `guide:`,
  `parts-flight:`, `interact:` und die Bilder zu Absturz, Landung und Turtle), NeoForge gefiltert 104 Schritte
  (`drone:`, `turtle:`).

### 7.6 Grenzen

- Flache Einschläge enden weiter meist aufrecht (10 m/s 11 %, 20 m/s 14 %): Ein flacher Kasten, der flach aufschlägt,
  rutscht und dreht sich um die Hochachse, statt sich zu überschlagen. Gras, Wurzeln und Erdklumpen, an denen echte
  Drohnen häufiger hängen bleiben, kennt der Boden nicht; jede Fläche hat dieselbe Reibung (nur Eis, Schleim und Honig
  weichen ab).
- Der Whoop überschlägt sich nach einem Fall aus 15–60 cm in einem Viertel der Fälle: sehr leicht, Ducts mit Rückprall
  0,40, schmaler Akku unter dem Rahmen. Unverändert gegenüber 0.5.2.
- Eine stromlos fallende Drohne behält ihre Lage, die Luft dreht sie nicht (Widerstand greift am Schwerpunkt).
