# Rennkurse in der Welt

Fertige Rennkurse, die man beim Erkunden findet: eine Startfläche mit Tafel und Truhe, dazu 9–12 echte, schon
nummerierte Kurs-Elemente (Gates, Slalomstangen, Flaggen, Gate-Stangen, Tauchwürfel). Wer ankommt, kann sofort fliegen –
das nächste Ziel leuchtet grün, Zeiten landen in der Bestenliste des Kurses, und die Tafel zeigt den Kursrekord.

Seit 0.6.0 nutzen die Kurse in neuen Gebieten die Mechaniken des Rennsystems 0.6.0 (Vorgabe des Besitzers, Befund
WCOURSE-1): Schleifen und Rückwärts-Durchflüge, Flaggen und Stangen mit Wertung bis zum Boden, Tauchwürfel, Gate-Reihen
mit gemeinsamem Pfosten, Lagen im 1/16-Raster und einen Kurs von Punkt zu Punkt. Kurse, die in alten Welten schon
erzeugt oder angefangen sind, bleiben, wie sie sind (Abschnitt „Alte Welten“).

Quellen: `tools/data/world_courses.py` (einzige Quelle für Vorlagen, Strukturen, Strukturset, Tags, Truhen-Beute und die
Fluglinien der Tests), `race.world.*` (Strukturtyp, Übernahme der Elemente als Kurs, Tafel), Selbsttest
`race.client.WorldCourseSelfTests`, JUnit `race.world.WorldCourseDataTest`, `WorldCourseMechanicsTest` und
`WorldCourseSiteTest`.

## Varianten

| Struktur | Biome | Vorlage(n) ab 0.6.0 | Elemente (Durchgänge) | Modus | Fluglinie | Fläche | max. Hangneigung | Gewicht | Chance |
|---|---|---|---|---|---|---|---|---|---|
| `propwash:field_slalom` „Feldslalom“ | Ebene, Sonnenblumenebene, Wiese | `v2/field_slalom` | 7 Gates (4 × M, 1 × S, 2 × L als Reihe), 3 Slalomstangen (10) | Runden | ca. 142 Blöcke | 52 × 52 | 5 | 4 | 100 % |
| `propwash:canyon_run` „Canyon-Run“ | Tafelberge (alle drei), Wüste | `v2/canyon_run_badlands`, in der Wüste `v2/canyon_run_desert` | 6 Gates (3 × M, 2 × S, 1 × L), 2 Slalomstangen, 1 Wendeflagge (9) | Punkt zu Punkt | ca. 110 Blöcke | 31 × 66 | 6 | 3 | 100 % |
| `propwash:forest_weave` „Waldslalom“ | Wald, Blumenwald, Birkenwälder, Taiga, verschneite Taiga | `v2/forest_weave_oak`, Birkenwälder `v2/…_birch`, Taiga `v2/…_spruce` | 7 Gates (5 × M, 1 × S, 1 × L), 3 Gate-Stangen (10) | Runden | ca. 140 Blöcke | 50 × 50 | 6 | 4 | 100 % |
| `propwash:drone_arena` „Drohnen-Arena“ | Ebene, Savanne, verschneite Ebene | `v2/drone_arena` | 8 Gates (4 × M, 2 × S, 2 × L, Leiter aus dreien), 1 Flagge, 1 Tauchwürfel (12) | Runden | ca. 190 Blöcke | 44 × 44 | 4 | 1 | 35 % |

- **Feldslalom** (Einsteiger): Graswiese mit einem Erdpfad unter der Ideallinie. Nach dem Start-Gate drei Slalomstangen
  im Wechsel (rechts, links, rechts vorbei), ein Gate, dann eine **Haarnadel durch eine Gate-Reihe**: zwei große Gates
  teilen sich einen Pfosten, man fliegt durch das linke hinaus und durch das rechte zurück. Auf dem Rückweg drei Gates,
  dann über die Startfläche zurück zum Start. Alle Elemente stehen **bündig auf dem Trampelpfad** (1/16 Block tiefer als
  auf Gras, `y = 15/16`), statt wie in 0.5.2 1/16 darüber zu schweben. Heuballen in der Mitte, Laternen an Zaunpfosten.
- **Canyon-Run** (Mittel, **von Punkt zu Punkt**): zwei Schluchten zwischen geschichteten Terrakotta-Wänden (in der Wüste
  Sandstein), getrennt durch einen Grat, zwei Felsbögen. Start in der linken Schlucht, zwei Slalomstangen, unter dem
  Bogen durch ein kleines Gate, am Ende eine **Wendeflagge** an der Spitze des Grats (links vorbei, außen herum), durch die
  rechte Schlucht zurück und Ziel im großen Gate an ihrem Ausgang. Keine Runden; die Bestenliste misst Start bis Ziel.
- **Waldslalom** (Mittel): Lichtung aus Pfad, Kies und gestampftem Lehm mit eigenen Bäumen (Eiche, Birke oder Fichte je
  Biom). Nach dem Start ein **Pfosten-Slalom** aus drei 6 Blöcke hohen, wertenden Gate-Stangen (rot, weiß, rot, Seite im
  Wechsel), danach webt man wie bisher um die Stämme durch sechs Gates. Die Stangen werten wie Slalomstangen vom Boden
  (1 Block darunter) bis zur Spitze; Gates und Stangen stehen bündig auf dem Pfad.
- **Drohnen-Arena** (die schwerste, seltener): verlassene Arena mit zerfallener Mauer, Tribüne, Lichtmasten und Start-Zelt.
  Nach dem Start eine **Stichstrecke**: durch ein Gate hinaus, um den 3 Blöcke hohen Zeitmessturm herum, auf dem eine
  **Flagge** steht, die **bis zum Boden wertet** (Wertung nach unten 4 Blöcke), und **rückwärts** durch dasselbe Gate
  zurück. Danach das Sockel-Gate, ein **Power-Loop** durch das große Gate (zweimal in gleicher Richtung, über den oberen
  Balken zurück), die Leiter aus drei gestapelten Gates im Zickzack nach oben und im Sturzflug von oben in den
  **Tauchwürfel** auf dem zweiten Sockel, Ausgang nach Norden, zum Schluss das große Gate an der Nordseite.

Alle Elemente sind achsparallel und liegen im 1/16-Raster. Die Fluglinie jeder Vorlage steht im Generator; er prüft, dass
sie jeden Durchgang in Reihenfolge und Richtung trifft (Gates in der Öffnung, Flaggen und Stangen auf der richtigen Seite,
Tauchwürfel oben hinein und am Ausgang hinaus), keine Blöcke und Elemente streift und kein anderes Gate durchfliegt.

## Seltenheit und Platzierung

- Ein gemeinsames Strukturset `propwash:world_courses` mit eigener Platzierung `propwash:world_course` (wie
  `random_spread`, zusätzlich eine Liste `avoid` von Abständen zu anderen Strukturen): **spacing 32, separation 10**
  Chunks (Zelle 512 × 512 Blöcke), Salt 1734215807. Pro Zelle wählt das Spiel gewichtet eine Variante; passt ihr Biom oder Gelände
  nicht, versucht es die übrigen (Vanilla-Verhalten bei mehreren Strukturen im Set).
- Eine Zelle bekommt nur dann einen Kurs, wenn alle neun Messpunkte des Grundrisses (Ecken, Kantenmitten, Mitte) im
  passenden Biom liegen, keiner unter Wasser steht und der Höhenunterschied höchstens `max_slope` beträgt (Feldslalom 10,
  Canyon-Run und Waldslalom 12, Arena 9 Blöcke; `beard_thin` gleicht den Rest aus). Dadurch
  landen Kurse auf ebenem, trockenem Land und nie halb in Fluss oder Ozean. Zusammen mit den Abständen zu anderen
  Strukturen bekommt nur ein kleiner Teil der Zellen einen Kurs: gemessen (Selbsttest, Seed 20260924, 441 Zellen) etwa
  3 %, also **etwa ein Kurs je 3000 Blöcke** Abstand. Der Selbsttest rechnet den mittleren Abstand aus dem Anteil der
  Zellen mit Kurs und verlangt 1000–4400 Blöcke. Die erste Schätzung (spacing 48, `max_slope` 4–6) ergab gemessen nur
  einen Kurs je 5000–7000 Blöcke und wurde deshalb gelockert.
- **Vorlagen-Format:** Die Palette der NBT-Vorlagen benutzt die Schlüssel von 26.3 (`id`, `properties`, wie
  `NbtUtils.writeBlockState`); mit den alten Schlüsseln `Name`/`Properties` liest 26.3 jeden Block als Luft, weil die
  Vorlage die aktuelle `DataVersion` trägt und kein DataFixer mehr läuft. `WorldCourseDataTest` liest die Palette über `id`.
- **Dünne Blöcke:** Teppiche, Moosteppich und Laternen haben eine Kollisionsform; das Generator-Skript prüft sie in den
  Zellen jedes Gates wie volle Blöcke und setzt dort keinen Moosteppich.
- Die Arena ist bewusst seltener: Gewicht 1 von 12 und zusätzlich nur 35 % Chance je passender Zelle.
- `/locate` prüft wie bei Vanilla-Außenposten zuerst nur das Raster und lädt Kandidaten-Chunks bis zur Stufe
  „Strukturstarts“; die Abstände greifen dabei, das Ergebnis stimmt also mit der echten Welt überein.
- **Andere Strukturen bleiben unangetastet:** `avoid` hält Abstand zu jedem *möglichen* Platz (Raster, unabhängig vom
  Biom) von Dörfern (8 Chunks), Plünderer-Außenposten (6), Waldanwesen (8), Wüsten- und Dschungeltempeln, Iglus,
  Hexenhütten, verlassenen Lagern (je 4), Portalruinen und Pfadruinen (je 3). Die Kurse sind so groß, dass eine
  Überlappung sonst das andere Bauwerk anschneiden würde (Kurse werden im selben Schritt nach den Vanilla-Strukturen
  gesetzt). Keine Mob-Spawner, keine Spawn-Überschreibungen, keine Tresore.
- **Gelände:** `terrain_adaptation: beard_thin` wie bei Dörfern und verlassenen Lagern – das Gelände unter dem Kurs wird
  aufgefüllt und darüber abgetragen, zum Rand hin weich übergeblendet. Die Vorlage setzt zusätzlich die ganze Fläche bis
  9–12 Blöcke über dem Boden auf Luft, damit keine Gate-Öffnung zugeschüttet ist.
- **Keine Bäume oder Kakteen im Kurs:** Strukturen werden vor der Vegetation gesetzt. Rund um jedes Gate (±3 Blöcke) und
  im ganzen Canyon, Wald und der Arena liegen deshalb nur Böden, auf denen nichts wächst (Pfad, Kies, gestampfter Lehm,
  Stein, Terrakotta, Sandstein). Sand gibt es nur direkt an Wänden (Kakteen brauchen freie Nachbarn). Beim Feldslalom
  bleibt abseits der Strecke Gras; dort kann selten ein Baum wachsen, aber nie an einem Gate.
- **Keine schwebenden oder vergrabenen Gates:** jeder Pfosten steht auf einem vollen Block (unter Pfad liegt ein Kies-
  bzw. Lehmblock als Fundament), Rahmen und Öffnung sind frei, und 6 Blöcke vor und hinter jeder Öffnung ist der Anflug
  frei. Das prüfen das Generator-Skript, JUnit und der Selbsttest in der Welt.
- **Abschalten:** Server-Regel `worldCourses` in `config/propwash/server.json` (Standard `true`); `false` verhindert neue
  Kurse in noch nicht erzeugten Gebieten, schon erzeugte bleiben. `/propwash reload` übernimmt die Änderung,
  `/propwash rules` zeigt sie.
- **Finden:** `/locate structure #propwash:world_courses` (alle) oder z. B. `/locate structure propwash:drone_arena`.

## Vom Bauwerk zum Kurs

Die Elemente in den Vorlagen tragen `Course 0`, `GateIndex -1` und einen Marker `WorldCourse` (Variante, Platz im Kurs,
Position und Blickrichtung in der Vorlage, Position der Tafel in der Vorlage). Seit 0.6.0 kommen zwei optionale Felder
dazu: `Passes` nennt alle Plätze eines Elements im Kurs im Format des Kurs-Werkzeugs (`"1,3r"` = Platz 1 vorwärts und
Platz 3 rückwärts; fehlt es, gilt genau der `Slot` vorwärts), `Mode` den Modus (`point_to_point`; fehlt es, Runden).
Das erste übernommene Element legt den Kurs im Modus des Markers an; ein Element mit mehreren Durchgängen reiht sich mit
allen Plätzen auf einmal ein (`CourseRegistry#insertPasses`). Wertung (`ScoreRole`, `ScoreSide`, `ScoreBelow`), Art
(`Kind`, `Variant`) und Länge der Gate-Stangen (`OpeningHeight`) stehen wie bei gesetzten Elementen im Entity-NBT. Sobald ein solches Gate zum ersten Mal
tickt (also wenn ein Spieler in der Nähe ist), rechnet `WorldCourseSite` aus seiner Weltposition und Drehung den Ursprung
der Vorlage und ihre Drehung zurück (gleiche Formeln wie `StructureTemplate.transform`, JUnit gegen Vanilla). Der
Ursprung ist der Schlüssel des Fundorts: das erste Gate legt einen besitzerlosen Kurs an (`CourseRegistry.createWorldCourse`),
jedes weitere Gate desselben Fundorts reiht sich nach seinem Platz ein (`insertGate`) – egal, in welcher Reihenfolge die
Chunks laden. Danach ist der Marker weg und das Gate ein ganz normales Kurs-Gate.

- Jeder darf die Gates abbauen (Schleichen + Linksklick) und mitnehmen; der Kurs verliert dann dieses Gate. Explosionen
  (Creeper, TNT) schaden Gates nicht und schieben sie nicht.
- Kurse ohne Besitzer ändern seit 0.6.0 nur Operatoren (`docs/race-p2p.md` §4).

### Eine eigene Kurs-ID je Fundort

Die Vorlage trägt nie eine Kurs-Nummer oder Kurs-ID: jedes Element hat `Course 0`, und Minecraft gibt beim Setzen jedem
Entity eine neue UUID. Der Schlüssel eines Fundorts ist der Ursprung der Vorlage in der Welt; zwei Fundorte derselben
Struktur haben verschiedene Ursprünge und bekommen deshalb zwei Kurse mit eigener Nummer, eigener Kurs-ID (UUID) und
eigener Bestenliste. Belegt mit JUnit (`WorldCourseMechanicsTest#templatesNeverCarryACourseIdOrUuid`,
`#twoFindsOfTheSameStructureBecomeTwoCoursesWithTheirOwnLeaderboards`) und dem Selbsttest „race: Weltkurs zweimal
gesetzt …“ (zwei Drohnen-Arenen nebeneinander in der Welt).

### Alte Welten

Ein Strukturstart speichert den Namen seiner Vorlage. Liegt ein Kurs aus 0.5.x am Rand des Erkundeten, erzeugt das Spiel
die fehlenden Chunks später aus derselben Vorlage. Deshalb bleiben die alten Vorlagen unter ihren alten Namen
(`world_course/<vorlage>.nbt`) byte-gleich erhalten (JUnit prüft die SHA-256 aus 0.4.0–0.5.2), und neue Strukturstarts
nutzen die Vorlagen unter `world_course/v2/`. Schon übernommene Kurse sind normale Kurse in `propwash_race.json` und
ändern sich nicht; alte Marker ohne `Passes` und `Mode` lesen und speichern sich unverändert.

## Tafel an der Startfläche

Ein gewachstes Schild (nicht editierbar) an der Startfläche zeigt:

1. Name des Kurses (übersetzt, fett),
2. „Kurs #12“ (Nummer für Befehle und Bestenliste),
3. „Rekord 42.318“ bzw. „Noch kein Rekord“,
4. Name des Rekordhalters.

Aktualisiert wird die Tafel beim Übernehmen des Kurses, bei jedem neuen Kursrekord, beim Zurücksetzen der Bestenliste
und wenn ein Umbau die Bestenliste archiviert. War der Chunk der Tafel gerade nicht geladen, holt das nächste Gate-Tick
es nach (nach einem Serverstart einmal für alle Weltkurse).

## Beute – bewusst bescheiden

Eine Truhe an der Startfläche, zwei Pools:

| Tabelle | Gewöhnlicher Pool | Bonus-Pool (1 Wurf) |
|---|---|---|
| `chests/world_course/common` (Feldslalom, Canyon, Wald) | 2–4 Würfe: Props (2–4), LiPo-Zellen, Kupferspulen, Carbonplatten, Platine, 1S-/4S-Akku (Lagerladung 40 %), kleine Motoren (0802, 1404); Brot, Faden, Fackeln, Kupferbarren | 14 %: 2207-Motoren, Analog-Race-Cam, 20×20-AIO, 6S 1100, mittleres Gate |
| `chests/world_course/arena` (Arena) | 3–5 Würfe, etwas mehr Teile (inkl. 2004-Motoren) | 22 %: dazu großes Gate, Action-Cam Light, selten eine fertige Whoop-Drohne |

Keine Diamanten, kein Gold, keine Verzauberungen, keine Spitzenteile (Digital HD, 2306-Motoren, 6S 1300, Li-Ion,
digitale Brille, Race-Rahmen, 30×30-Stack, HD-Chip, Heavy-Cam). Zum Vergleich: Tempel, Festung, Antike Stadt und
Tresor der Prüfungskammer liefern diese Spitzenteile (`docs/design/survival-tables.md`); eine Weltkurs-Truhe entspricht
eher einem Dorf-Werkzeugschmied plus ein paar Ersatzprops. Der Selbsttest würfelt jede Tabelle 600-mal und verlangt
1,2–6 Propwash-Stapel je Truhe und 4–30 % Truhen mit besserem Teil; JUnit prüft die Pools.

## Prüfungen

- `python3 tools/data/world_courses.py --check`: Dateien aktuell (gzip mit fester Stufe 9, gleiche Bytes mit jeder
  Python-Version), Blöcke und Biome gibt es in 26.3, Elemente frei, bündig auf festem Grund (Oberkante des Blocks, beim
  Trampelpfad 15/16) und im 1/16-Raster, Gates mit freiem Anflug (4 Blöcke), Abstand zwischen Durchgängen 5–30 Blöcke
  (außer Schleife und Leiter), Fluglinie 80–260 Blöcke (Punkt zu Punkt 60–260), trifft alle Durchgänge in Reihenfolge,
  streift nichts, keine Böden neben Elementen, auf denen Bäume oder Kakteen wachsen, Tafel und Truhe vorhanden, Beute
  im Rahmen; die alten Vorlagen nach den Regeln von 0.5.2.
- JUnit `WorldCourseMechanicsTest`: jede Struktur nutzt die Mechaniken von 0.6.0 (Canyon-Run von Punkt zu Punkt mit
  Wendeflagge, Arena mit Schleife, Rückwärts-Durchflug, Tauchwürfel und Flagge bis zum Boden, Waldslalom mit Pfosten-Slalom,
  Feldslalom mit Slalomstangen und Gate-Reihe, 1/16-Lagen); jede Fluglinie ergibt mit dem echten `LapTracker` genau eine
  saubere Runde (bzw. Start bis Ziel); alte Vorlagen byte-gleich und nicht mehr in den Strukturen; Vorlagen ohne
  Kurs-Nummer und UUID; Marker alter und neuer Vorlagen; zwei Fundorte = zwei Kurse.
- JUnit `WorldCourseDataTest`: liest die NBT-Vorlagen mit Minecrafts `NbtIo` und prüft sie mit der echten
  `GateGeometry` (lückenlose Kursreihenfolge, eine Variante, eine Tafel mit Kursnamen, genau eine Truhe mit vorhandener
  Beutetabelle, keine Spawner), Strukturen, Set, Tags, Beute und Übersetzungen. `WorldCourseSiteTest`: Drehungen gegen
  `StructureTemplate.transform`, Ursprung aus jedem Gate.
- Selbsttest (`race: Weltkurs…`): Strukturen, Tag und Vorlagen geladen; im echten Overworld-Generator (fester Seed)
  wird jede Variante wie mit `/locate` gefunden und die Regel `worldCourses=false` verhindert die Erzeugung; jede der
  sieben Vorlagen und zwei alte Vorlagen werden in die Testwelt gesetzt (reihum in allen vier Drehungen), die Elemente
  werden zum Kurs mit allen Durchgängen in der richtigen Reihenfolge und Richtung und im richtigen Modus, stehen bündig
  auf dem Grund mit freier Öffnung, die Tafel zeigt Name, Nummer und – nach einer Testrunde – den Rekord, eine Explosion
  lässt das Gate stehen; jede der sieben Vorlagen wird entlang ihrer Fluglinie abgeflogen (Runde beim Client mit
  `LapTracker`, am Server über `RaceServer` bis in die Bestenliste); zwei Arenen nebeneinander ergeben zwei Kurse mit
  eigener ID und Bestenliste; die Beute wird gewürfelt. Mit `-PpropwashScreenshots=true` (Fabric) je Vorlage ein Bild
  `docs/screenshots/world_course_<vorlage>.png`.
