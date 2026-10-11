# Rennkurse in der Welt

Fertige Rennkurse, die man beim Erkunden findet: eine Startfläche mit Tafel und Truhe, dazu 7–9 echte, schon
nummerierte Propwash-Gates. Wer ankommt, kann sofort fliegen – das nächste Gate leuchtet grün, Zeiten landen in der
Bestenliste des Kurses, und die Tafel zeigt den Kursrekord.

Quellen: `tools/data/world_courses.py` (einzige Quelle für Vorlagen, Strukturen, Strukturset, Tags und Truhen-Beute),
`race.world.*` (Strukturtyp, Übernahme der Gates als Kurs, Tafel), Selbsttest `race.client.WorldCourseSelfTests`,
JUnit `race.world.WorldCourseDataTest` und `WorldCourseSiteTest`.

## Varianten

| Struktur | Biome | Vorlage(n) | Gates | Runde | Fläche | max. Hangneigung | Gewicht | Chance |
|---|---|---|---|---|---|---|---|---|
| `propwash:field_slalom` „Feldslalom“ | Ebene, Sonnenblumenebene, Wiese | `field_slalom` | 7 (5 × M, 1 × S, 1 × L) | ca. 108 Blöcke | 52 × 52 | 5 | 4 | 100 % |
| `propwash:canyon_run` „Canyon-Run“ | Tafelberge (alle drei), Wüste | `canyon_run_badlands`, in der Wüste `canyon_run_desert` | 7 (4 × M, 2 × S, 1 × L) | ca. 105 Blöcke | 31 × 66 | 6 | 3 | 100 % |
| `propwash:forest_weave` „Waldslalom“ | Wald, Blumenwald, Birkenwälder, Taiga, verschneite Taiga | `forest_weave_oak`, Birkenwälder `…_birch`, Taiga `…_spruce` | 8 (5 × M, 2 × S, 1 × L) | ca. 114 Blöcke | 50 × 50 | 6 | 4 | 100 % |
| `propwash:drone_arena` „Drohnen-Arena“ | Ebene, Savanne, verschneite Ebene | `drone_arena` | 10 (4 × M, 4 × S, 2 × L), zwei auf Sockeln, drei als Leiter | ca. 100 Blöcke | 44 × 44 | 4 | 1 | 35 % |

- **Feldslalom** (leicht): Graswiese mit einem Erdpfad als Rundkurs, Gates im Slalom, ein großes Wende-Gate, Heuballen
  in der Mitte, Laternen an Zaunpfosten. Startfläche aus Andesit mit weißer Markierung, Heuballen, Laterne.
- **Canyon-Run** (mittel): zwei Schluchten zwischen geschichteten Terrakotta-Wänden (in der Wüste Sandstein), getrennt
  durch einen Grat; zwei Felsbögen überspannen die Schluchten, im Norden ein großes Wende-Gate. Hin durch die linke,
  zurück durch die rechte Schlucht.
- **Waldslalom** (mittel): Lichtung aus Pfad, Kies und gestampftem Lehm mit eigenen Bäumen (Eiche, Birke oder Fichte je
  Biom) genau zwischen den Gates – man webt um die Stämme herum, die Kronen beginnen über den Gates. Ein liegender
  Stamm und ein bemooster Felsen als Deko.
- **Drohnen-Arena** (schwer, seltener): verlassene Arena mit zerfallener Mauer, Tribüne, Lichtmasten (einer umgeknickt),
  einem zerfallenen Zeitmessturm und einem halb eingestürzten Start-Zelt. Zwei Gates stehen auf 2 Blöcke hohen Sockeln,
  kleine Gates verlangen saubere Linien. Auf der Gegengeraden steht eine Leiter aus drei Gates (mittleres Gate, darauf
  zwei kleine), die im Zickzack nach oben durchflogen wird: unten Richtung Norden, in der Mitte zurück nach Süden, oben
  wieder nach Norden, danach im Sturzflug zum Sockel-Gate. Im Generator `Course.stack(unten, yaw, größe)`; gestapelte
  Gates brauchen keinen festen Grund, zählen beim Gate-Abstand nicht (senkrecht übereinander) und stehen nicht auf einer
  Blockoberkante (Unterkante = Oberkante des Gates darunter − 0,5).

Alle Kurse sind geschlossene Runden (Gate *n* → Gate 1), jedes Gate ist achsparallel und zeigt in Flugrichtung.

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

Die Gates in den Vorlagen tragen `Course 0`, `GateIndex -1` und einen Marker `WorldCourse` (Variante, Platz im Kurs,
Position und Blickrichtung in der Vorlage, Position der Tafel in der Vorlage). Sobald ein solches Gate zum ersten Mal
tickt (also wenn ein Spieler in der Nähe ist), rechnet `WorldCourseSite` aus seiner Weltposition und Drehung den Ursprung
der Vorlage und ihre Drehung zurück (gleiche Formeln wie `StructureTemplate.transform`, JUnit gegen Vanilla). Der
Ursprung ist der Schlüssel des Fundorts: das erste Gate legt einen besitzerlosen Kurs an (`CourseRegistry.createWorldCourse`),
jedes weitere Gate desselben Fundorts reiht sich nach seinem Platz ein (`insertGate`) – egal, in welcher Reihenfolge die
Chunks laden. Danach ist der Marker weg und das Gate ein ganz normales Kurs-Gate.

- Jeder darf die Gates abbauen (Schleichen + Linksklick) und mitnehmen; der Kurs verliert dann dieses Gate. Explosionen
  (Creeper, TNT) schaden Gates nicht und schieben sie nicht.
- Kurse ohne Besitzer darf jeder mit dem Kurs-Werkzeug bearbeiten; wer das tut, übernimmt ihn wie bisher.

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

- `python3 tools/data/world_courses.py --check`: Dateien aktuell, Blöcke und Biome gibt es in 26.3, Gates frei, auf
  festem Grund und mit freiem Anflug, Abstand zwischen Gates 8–30 Blöcke, Runde 80–260 Blöcke, keine Böden neben Gates,
  auf denen Bäume oder Kakteen wachsen, Tafel und Truhe vorhanden, Beute im Rahmen.
- JUnit `WorldCourseDataTest`: liest die NBT-Vorlagen mit Minecrafts `NbtIo` und prüft sie mit der echten
  `GateGeometry` (lückenlose Kursreihenfolge, eine Variante, eine Tafel mit Kursnamen, genau eine Truhe mit vorhandener
  Beutetabelle, keine Spawner), Strukturen, Set, Tags, Beute und Übersetzungen. `WorldCourseSiteTest`: Drehungen gegen
  `StructureTemplate.transform`, Ursprung aus jedem Gate.
- Selbsttest (`race: Weltkurse …`): Strukturen, Tag und Vorlagen geladen; im echten Overworld-Generator (fester Seed)
  wird jede Variante wie mit `/locate` gefunden und die Regel `worldCourses=false` verhindert die Erzeugung; jede der
  sieben Vorlagen wird in die Testwelt gesetzt (reihum in allen vier Drehungen), die Gates werden zum Kurs in der richtigen
  Reihenfolge, stehen auf festem Grund mit freier Öffnung, die Tafel zeigt Name, Nummer und – nach einer Testrunde – den
  Rekord, eine Explosion lässt das Gate stehen; die Beute wird gewürfelt.
