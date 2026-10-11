# Bandos – verlassene Freestyle-Spots in der Welt

Bandos (FPV-Slang für *abandoned buildings*) sind verlassene Bauwerke, die selten in der Welt entstehen: Scheunen,
Lagerhallen, rostige Fabriken, Wassertürme, Raffinerien, eingestürzte Hallen, zugewucherte Betonskelette, Stege,
Bohrinseln, Förderanlagen und versunkene Lagerhallen. Sie haben keine Gates und keine Zeitmessung – sie sind zum freien
Fliegen da: Tore, Fenster, Dachlöcher, Laufstege und Lücken zwischen Trägern. Ab und zu steht eine Truhe mit
Propwash-Teilen darin.

Quellen: `bando.plan.*` (reines Java, keine Minecraft-Typen: Bauplan, Bausteine, Varianten, Paletten, Platzwahl,
Prüfungen), `bando.BandoStructure`/`BandoPiece`/`BandoBlocks`/`BandoModule` (Strukturtyp, Teil, Blockzustände,
Registrierung), `bando.BandoScene` + `bando.client.BandoClient` (Flugschule), `tools/data/bandos.py` (Strukturen, Tags,
Strukturset, Beute), JUnit `bando.*Test`, Selbsttest `bando.client.BandoSelfTests`/`BandoWorldTests`.

## Familien und Varianten

Je Biom-Familie gibt es eine Struktur `propwash:bando/<familie>`; welche Variante entsteht, entscheidet der Zufall des
Strukturstarts (bei der Küste das Gelände). Alle sieben stehen im Tag `#propwash:bandos`.

| Struktur | Biome | Varianten (Gewicht) | Gelände |
|---|---|---|---|
| `bando/field` | Ebenen, Sonnenblumenebene, Wiese, Wälder (Eiche, Blumen, Birke, alte Birke, Lichtwald), Savanne, Savannenplateau, Taiga (alle drei), Kirschhain | Scheune (3), Lagerhalle (3) | `beard_box`, Hang ≤ 7 |
| `bando/desert` | Wüste, Tafelberge (alle drei) | rostige Fabrik mit Schornstein (3), Wasserturm (2), Raffinerie (2) | `beard_box`, Hang ≤ 12 |
| `bando/snow` | verschneite Ebene, verschneite Taiga, Hain | eingestürzte Halle (Eishalle oder Depot) | `beard_box`, Hang ≤ 7 |
| `bando/jungle` | Dschungel, lichter Dschungel, Bambusdschungel | zugewuchertes Betonskelett | `beard_box`, Hang ≤ 12 |
| `bando/coast` | Strand, verschneiter Strand, Steinküste, Ozeane (warm, lauwarm, normal, kalt, jeweils auch tief) | Steg (flaches Ufer), Bohrinsel (Wasser ab 8 Blöcken Tiefe, 30 %) | keine Anpassung, Pfähle bis zum Grund, Gelände über dem Strand geräumt |
| `bando/mountain` | windgepeitschte Hügel, Geröll-, Wald- und Savannenhügel, steinige Gipfel | Förderanlage mit Förderturm, Seilrad, Schacht, Förderbrücke, Bunker und Maschinenhaus | `beard_box`, Hang ≤ 22 |
| `bando/swamp` | Sumpf | versunkene Lagerhalle (Boden 3 Blöcke unter dem Meeresspiegel, 2 Blöcke Wasser in der Halle) | `beard_box`, Hang ≤ 8 |

Dunkler Wald und bleicher Garten haben keine Bandos (zu dicht, dort stehen Waldanwesen), der Mangrovensumpf auch nicht:
seine Kronen wachsen von allen Seiten über die versunkene Halle und decken sie ganz zu (Selbsttest-Luftbild beim Spieltest
0.5.0). Eiszapfen-Biome und gefrorene Ozeane bekommen keine Bandos, und jeder Bando hält von ihnen Abstand (siehe „Abstand
zu Eiszapfen und Eisbergen“).

## Prozedural statt Vorlagen

Es gibt keine NBT-Vorlagen. Aus Variante und dem Seed des Strukturstarts entsteht jedes Mal ein eigener Bauplan
(`BandoGenerator.generate(variante, seed)`, deterministisch, in allen Chunks und nach dem Neuladen gleich):

- **Bauplan** (`BandoPlan`): dichtes Gitter bis 64 × 64 × 64 mit einer *Rolle* je Zelle (Boden, Wand, Stütze, Träger,
  Dach, Laufsteg, Geländer, Rohr, Tank, Kamin, Kette, Leiter, Schutt, Fracht, Laub, Ranken, Moos, Wasser, Truhe, Hof …)
  und Ausrichtung (Achse, Blickrichtung, Rankenseiten). Leere Zellen lassen das Gelände stehen, Luft räumt es aus. Für
  Pfähle, Stützen und Fundamente gibt es Spalten, die beim Setzen bis zum festen Grund verlängert werden.
- **Bausteine** (`Kit`, `Hall`): Hallen mit Stützenraster und Feldweite, Traufträgern und Bindern, Wandband mit
  Blechfeldern, Toren und Fensterbändern, Flach-, Pult-, Shed- oder Satteldach mit Löchern und abgedeckten Feldern,
  Laufsteg mit Geländer und Leiter, Kranbahn mit Kette, Einsturz eines Abschnitts; dazu Zylinder (Tanks, Silos,
  Kamine), Gitterturm mit Ringen und Diagonalen, Seilrad, schräge Förderbrücke, Stockwerke mit Deckenfeldern und offenem
  Treppenschacht, Pfahlreihen mit Unterzügen.
- **Varianten** setzen daraus zufällige Größen und Ausstattung zusammen (z. B. Lagerhalle: 15–21 breit, 4–6 Felder à
  5–6 Blöcke, 7–8 hoch, Flach- oder Pultdach, 70 % Laufsteg, 50 % Kran, 45 % Büro auf Stützen, 35 % eingestürztes
  Feld). Von 60 Seeds je Variante sind mindestens 90 % verschieden (JUnit).
- **Verfall mit Statik** (`Kit.settle`): Nach Löchern, Einstürzen und Verwitterung wird geprüft, was noch mit dem Boden
  verbunden ist (Flächennachbarn, Treppen auch über Eck; Leitern, Ketten und Laub tragen nichts). Alles andere fällt und
  liegt als Schutt darunter. Damit schwebt nichts – auch nicht nach einem eingestürzten Dachfeld.
- **Hof** (`BandoGenerator.yard`): An Land liegt bis 3 Blöcke um das Bauwerk lückenlos Kies, Pfad, Sandstein oder
  Pflaster, im Sumpf unter Wasser festgestampfter Schlamm; die Mischung der Palette sorgt für das fleckige Bild. Nur unter
  Bauteilen, die direkt auf dem Boden stehen (Zäune, Stützen), bleibt das Gelände – ein Zaun auf einem Pfad (15/16 hoch)
  schwebte sonst einen Pixel. Strukturen entstehen vor der Vegetation; auf diesem Boden wächst kein Baum, so bleiben Tore,
  Wände und das Dach frei (im Dschungel ohne Kies, auf Kies wächst Bambus). Der Hof liegt nie in der Luft: Fehlt auf
  der Bodenzeile das Gelände (siehe „Löcher unter der Bodenzeile“), füllt das Setzen die Spalte bis zum festen Grund mit
  Fundament auf und legt den Hof darauf. Bis zum Spieltest von 0.5.0 blieben 15 % des Hofs Gelände; in diesen Lücken
  konnten Bäume direkt neben der Wand wurzeln. Die Zufallszahl je Hofzelle wird weiter gezogen, damit Truhe und
  Ausstattung jedes Seeds gleich bleiben.
- **Fundamente** (`Kit.anchor`): Jede Spalte, deren unterstes Bauteil auf dem Boden steht (Bodenzeile oder direkt
  darüber), reicht beim Setzen bis zum festen Grund hinunter – Stützen und Zaunpfosten verlängern sich selbst, alles andere
  bekommt ein Fundament aus der Palette. Am Hang stehen Böden, Stützen und Förderbrücke so auf Pfeilern statt in der Luft.
- **Halbe Blöcke** (`Kit.solidify`, `Mat.carries`): Stufen und Schneeschichten tragen nichts. Schutt, Fracht, Truhen, Moos
  und Laub liegen nur auf vollen Blöcken oder auf dem Gelände; eine Dach- oder Schuttstufe, auf der trotzdem etwas steht
  (Kegeldach des Wasserturms, Kranbahn über dem Büro), wird beim Setzen zur Doppelstufe, ebenso eine obere Laufsteg-Stufe
  direkt über einer Stütze. Die Förderanlage baut Laufstege und Dachfirst aus Planken bzw. Tiefenschiefer-Ziegeln statt
  aus Stufen, die gelbe Bohrinsel hat Treppen statt Stufen. JUnit prüft das mit den echten Blockformen der Paletten.
- **Paletten** (`BandoPalettes`): je Variante ein bis zwei Stile (z. B. Fichtenscheune/rote Scheune, Kupfer- oder
  Betonhalle, Holz- oder Stahl-Wasserturm, Eishalle oder Depot, Holz- oder Steinsteg, rostige oder gelbe Bohrinsel).
  Jede Rolle hat gewichtete Blockzustände; welcher Block an einer Stelle steht, entscheidet ein Hash aus Position und
  Seed (in jedem Chunk gleich). Rostiges Metall ist gewachstes, geschnittenes Kupfer – es oxidiert nicht weiter und lässt
  sich nicht zu Barren zurückverarbeiten; keine Eisen-, Gold- oder Kupferblöcke, keine Spawner. Kein Block einer Palette
  ist Ziel einer Vanilla-Erzader oder -Scheibe (Stein, Andesit, Diorit, Granit, Tuff, Tiefenschiefer, Erde, Gras): diese
  Merkmale kommen im Schritt `underground_ores` nach den Bauwerken und wandelten solche Blöcke auch über dem Boden um –
  eine Andesit-Wand der Eishalle wurde zu Kies, auf dem Pilze wuchsen, Andesit im Hof hätte zu Erde werden können, auf
  der Gras und Bäume wachsen. Statt Andesit, Stein und Tuff stehen polierter Andesit, glatter Stein, Steinziegel und
  polierter Tuff in den Paletten; `BandoDataTest` liest die Ziele aus den Vanilla-Daten.

## Lage im Gelände

- **Bodenhöhe** (`BandoSite.level`): 5 × 5 Messpunkte über dem Grundriss der Teile, die auf dem Boden stehen; der Boden
  des Bandos liegt auf dem 40-%-Wert dieser Geländehöhen. Auf ebenem Gelände ersetzt die Bodenzeile genau den obersten
  Geländeblock (bündig), am Hang liegt der Boden etwas unter der Mitte: bergauf leicht eingegraben, bergab auf Fundamenten.
- **Geländeanpassung** (`beard_box`, `BandoSite.boxBottom`): Vanilla formt das Gelände unter der Unterkante der
  Bounding Box fest und räumt es in der ganzen Box frei. Die Box beginnt eine Zeile über dem Boden und umfasst den ganzen
  Plan (Grundriss samt Hof): darin ist über der Bodenzeile nie Gelände, die Bodenzeile selbst ist immer fest, rundherum
  läuft das Gelände in einer weichen Böschung auf die Bodenhöhe zu. Rechnung: Vanilla addiert den Beitrag nach `squeeze`,
  das Gelände liefert dort höchstens +0,458; `beard_box` liefert in der Box über dem Boden −0,557 (also immer Luft) und auf
  der Bodenzeile +0,492 (also immer fest). `BandoTerrainTest` prüft das mit dem echten `Beardifier` für jede Variante.
  Vorher (`beard_thin`) sank die Wirkung mit der Höhe über dem Boden schnell (3 Blöcke darüber nur noch −0,26): am Hang
  blieben Erdwände im Hof stehen, auf denen Bäume wuchsen (Förderturm am Hang: 22 Geländeblöcke in 11 Spalten, ein
  Baum mit der Krone über dem Dach; Raffinerie am Hang 108 Blöcke). Vor dem Spieltest von 0.5.0 begann die Box auf der
  Bodenzeile, Vanilla senkte das Gelände rundherum um einen Block ab und der Bando saß samt Hof einen Block zu hoch auf
  dem Gelände. Küste ohne Anpassung, Box ab Zeile 0.
- **Küste** (`BandoSite.below`): Steg und Bohrinsel entstehen nur, wo das Gelände über dem ganzen Grundriss höchstens auf
  Meeresspiegel + 1 liegt (Strand), geprüft am Rand und in der Mitte des Grundrisses alle 4 Blöcke entlang der Achse. Die
  Bodenzeile ist die Wasserlinie (Meeresspiegel − 1); der Strand darf bis eine Zeile über dem Meeresspiegel unter dem Steg
  liegen, darüber räumt das Setzen wie an Land. Vorher prüfte der Steg nur vier Punkte auf seiner Mittellinie und lief
  in Steilküsten hinein (Platz `pier/slope`: Stein bis 7 Blöcke über dem Wasser im Grundriss, Felsnasen über dem Steg).
  Gemessen an den 24 gefundenen Stegen: Hang 0–2 statt 0–12, 0 Geländeblöcke im Grundriss; Stege werden seltener
  (7 statt 11 in den 289 Zellen der Seltenheits-Messung), weil Steilküsten wegfallen.
- **Abstand zu Eiszapfen und Eisbergen** (`BandoSite.keepsAway`, Biom-Tag `propwash:bando/keep_away`): Kein Bando
  entsteht, wenn in 12 Blöcken um seinen Plan ein Biom des Tags liegt (geprüft im Raster von 4 Blöcken = Auflösung der
  Biome). Im Tag stehen Eiszapfen, gefrorener Ozean und tiefer gefrorener Ozean. Eiszapfen setzt Vanilla im selben Schritt
  `surface_structures` wie die Bandos, aber nach ihnen; ein Eiszapfen reicht bis 3 Blöcke um seinen Ursprung, Eisberge bis
  11. Vorher wuchsen Eiszapfen in Hof und Halle (82 Blöcke Packeis an einer Halle im Eiszapfen-Biom), und ein Steg lehnte
  an einem Eisberg. Bewusst nicht gewählt: Bandos in einem späteren Schritt setzen (die Eiszapfen ragten über den Plan
  hinaus und blieben abgeschnitten stehen, Eiszapfen aus Nachbar-Chunks kämen trotzdem danach) oder die Merkmale per
  Mixin im Grundriss abschalten (greift in jedes Feature ein). Datenpakete erweitern den Tag wie jeden Biom-Tag.
- **Grundriss räumen** (`BandoPiece#clearAbove`, `BandoSite.siteColumns`, `BandoSite.clearFrom`): In jeder Spalte mit
  Bauteil oder Hof räumt das Setzen über der Bodenzeile (an der Küste über dem Strand, ab Meeresspiegel + 2), was in
  leeren Zellen des Plans noch fest steht (z. B. Findlinge, die Vanilla vor den Strukturen setzt); unter dem
  Meeresspiegel (Sumpf) mit Wasser statt Luft. Das gilt für jede Familie. Was dort keinen Halt mehr hat (Gras,
  Schneeschicht), fällt mit weg.
- **Löcher unter der Bodenzeile** (`BandoPiece#underpin`): `beard_box` macht die Bodenzeile nur fest, solange das Gelände
  dort nicht stärker als −0,49 ist. Höhleneingänge, Lavaseen, die Talseite steiler Hänge und der Grund unter der Halle im
  Sumpf liegen tiefer: Auf Seed 123456789 fehlte der Hof an 543 Stellen in 146 Plätzen (Förderanlage am Hang 170,
  Fabrik 131, Betonskelett 86, Lagerhalle 57, versunkene Halle 43, eingestürzte Halle 32), meist über Luft, sonst über
  Wasser oder Lava. In diesen Lücken wurzelten Eichen im Hof des Betonskeletts, und an der Förderanlage lag das Gelände am
  Rand im Median einen Block unter dem Boden. Jetzt füllt das Setzen jede Spalte von Grundriss und Hof, deren Bodenzeile
  im Plan Hof oder leer ist, in der dort aber Luft, Wasser, Lava oder eine Pflanze statt Gelände steht und die kein
  eigenes Fundament hat, bis zum festen Grund mit dem Fundament der Palette auf (höchstens 64 Blöcke, wie die
  Fundamente) und legt den Hof darauf; Spalten mit Zaun oder Stütze behalten ihr eigenes Fundament. Nicht an der Küste (dort ist die Bodenzeile die Wasserlinie). Bewusst nicht gewählt: Höhlen unter dem
  Bauplatz bei der Platzwahl erkennen (die Heightmaps der Platzwahl kennen keine Höhlen und keine Seen) oder Lücken
  offen lassen und nur die Prüfung lockern.
- **Nachbar-Chunks** (`BandoGuard`, `mixin.bando.WorldGenRegionMixin`): Vanilla setzt die Merkmale eines Chunks erst,
  wenn dieser Chunk an der Reihe ist, und sie dürfen 1 Chunk weit hinüberschreiben. Ein Findling (`forest_rock`,
  bemooster Bruchstein in Riesenbaum-Taigas, Schritt `local_modifications`) aus einem Nachbar-Chunk, der erst nach dem
  Bando-Chunk erzeugt wird, reicht bis 3 Blöcke hinüber (der Ursprung wandert bis 2 Blöcke, Radius 1) und blieb dann im
  Hof stehen; ein Lavasee (`lake_lava_surface`, Schritt `lakes`, in fast allen Biomen) reicht bis 8 Blöcke aus seinem
  Chunk und setzt Höhlenluft, Lava und Stein, ein Verlies (`monster_room`, Schritt `underground_structures`) bis 4 Blöcke
  mit Höhlenluft und Bruchstein. Gefunden auf Seed 123456789: Ein Lavasee aus dem Nachbar-Chunk fraß Boden und Stützenfuß
  einer eingestürzten Halle. Ob das passiert, hängt von der Reihenfolge der Chunk-Erzeugung ab.
  - Seit dem 08.10. verhindert Propwash den Schaden, statt ihn zu reparieren: Schreibt ein Merkmal aus einem
    Nachbar-Chunk in einen Chunk, der seine Merkmale schon hat (Status `features` oder weiter, der Bando steht dort also
    schon), und liegt der Block in einer Spalte von Grundriss oder Hof eines Bandos dieses Chunks – von 64 Blöcken unter
    der Plan-Unterkante (so tief reichen Fundamente) bis zur Oberkante –, lehnt `WorldGenRegion#setBlock` ihn ab (wie
    ein Block außerhalb des Schreibradius). Stämme, Blätter, Pflanzen und Schnee dürfen weiter hinein, Bäume und Bewuchs
    am Rand bleiben wie vorher.
  - Hat der Nachbar-Chunk seine Merkmale noch nicht, schreibt das Merkmal wie bei Vanilla; der Bando setzt sich später
    darüber (`BandoPiece#place` setzt jede Planzelle und räumt leere Zellen über dem Boden).
  - Merkmale des Bando-Chunks selbst: Vanilla setzt Bauwerke (Schritt `surface_structures`) vor Bäumen und Bewuchs
    (`vegetal_decoration`) desselben Chunks. Was danach in leere Zellen von Grundriss oder Hof gerät und weder Baum noch
    Bewuchs ist (gefunden auf Seed 77031: ein Bienennest an einer Fichte im Grundriss von `warehouse/n5`, ein Block über
    dem Boden), räumt `BandoGuard#tidy` nach allen Merkmalen dieses Chunks weg (`mixin.bando.ChunkGeneratorMixin`, Ende
    von `ChunkGenerator#applyBiomeDecoration`, `BandoPiece#tidy`). Auch dieser Schritt schreibt nur in den Chunk, der
    gerade erzeugt wird; Stämme, Blätter und Pflanzen bleiben wie beim Platzieren. Bis 08.10. erledigte das `clearSpill`
    nebenbei mit (und schrieb dabei in Nachbar-Chunks).
  - Die Prüfung kostet nur etwas bei Blöcken, die über den Chunk-Rand in einen fertigen Chunk geschrieben werden: dann
    die Referenzen dieses Chunks auf Bandos, deren Start (höchstens 8 Chunks entfernt, im Cache der Erzeugung) und ein
    Blick in die Spalten-Maske des Plans.
  - Vorher (bis 08.10.) räumte und besserte jeder Chunk am Bando nach seinen Bauwerken einen Streifen von 8 Blöcken in
    den Nachbar-Chunks aus (`clearSpill`). Das schrieb in Chunks, die der Licht-Thread schon las, und ließ das Spiel
    selten abstürzen (Abschnitt „Absturz im Licht-Thread“).
  - Bewusst nicht gewählt: das Ausbessern später im Server-Thread nachholen (bräuchte einen gespeicherten Merkzettel je
    Chunk und müsste warten, bis alle Nachbarn fertig geladen sind), Seen und Verliese am Bando ganz abschalten (greift in
    jedes Merkmal ein, auch weit weg vom Bau).
- **Hang** (`BandoSite`): Die Familien-Grenze für den Hang gilt für die neun Prüfpunkte über dem ganzen Plan und für die
  25 Messpunkte über dem Grundriss; eine Klippe zwischen den Prüfpunkten verhindert den Bando.
- **Lose Teile** (`BandoPiece`): Schutt, Fracht, Moos und Laub, die auf dem Gelände liegen sollen, entstehen nur, wenn
  darunter ein tragender Block ist; eine Truhe bekommt notfalls einen Fundamentblock. Bleibt ein loses Teil deshalb aus
  (z. B. Fracht auf einem Pfad, der nur 15/16 hoch ist), entfernt das Setzen eine Pflanze an seiner Stelle, die dort
  keinen Halt hat (vorher stand an einer Scheune ein Grasbüschel auf dem Pfad).
- **Pflanzen**: Bandos entstehen im Schritt `surface_structures`, vor der Vegetation. Vanilla-Pflanzen brauchen Erde,
  Sand, Terrakotta oder Schlamm (Pilze im Dunkeln einen vollen Block) und landen deshalb nie auf Dächern, Stufen oder
  Laufstegen. Hat ein Nachbar-Chunk schon vorher Gras oder Blumen gesetzt, die auf dem Bando keinen Halt mehr haben,
  entfernt das Setzen sie (`BandoPiece#uproot`, doppelte Pflanzen ganz). Stämme eines Baums, der aus einem Nachbar-Chunk
  schon hineinragt, bleiben stehen – kein Block des Bandos ersetzt sie. Blätter weichen nur tragenden Bauteilen (Wand,
  Boden, Stütze, Träger, Dach …); Luft, Hof, lose Teile und Deko lassen sie stehen. Danach rechnet das Setzen den
  Blattabstand im Umkreis von 7 Blöcken neu (`BandoPiece#settleLeaves`, `LeafDistance`, wie Vanilla: Stämme 0, jedes
  Blatt eins mehr als sein nächster Nachbar, höchstens 7): Was von seinem Stamm abgeschnitten ist, bekommt den Abstand 7
  und zerfällt wie üblich nach kurzer Zeit, der Rest der Krone bleibt. Gelesen wird im ganzen Umkreis, geschrieben seit
  dem 08.10. nur im Chunk, der gerade erzeugt wird (kein Schreiben in Nachbar-Chunks, siehe „Absturz im Licht-Thread“);
  Blätter jenseits des Chunk-Rands behalten ihren Abstand. Vorher blieben die Blätter stehen und hielten die
  Bauteile ab: Im Bambusdschungel (Seed 987654321987) fehlten einem Betonskelett so 270 Bauteile, zwei Bodenblöcke
  hingen in der Luft. Blätter, die Vanilla selbst ohne Stamm setzt (eine Busch-Krone, die sich um eine Wand legt), haben
  den Abstand 7 und zerfallen ebenso.
  Im Dschungel lässt Vanilla selten Blätter eines Buschs ohne Stamm zurück, auch fern von jedem Bando (gemessen rund um
  die Lage-Plätze); reicht so ein Blätterhaufen über den Hof hinaus, zählt er wie eine Krone von außen. Auf vollen Blöcken bleibt, was dort wachsen darf
  (Laubstreu und Pilze am Hof, tote Büsche auf Terrakotta, Seegras am Boden der versunkenen Halle). Bäume wurzeln
  frühestens hinter dem Hof: im Hof ist kein Erdblock, über dem Boden kein Gelände. Ein Riesenbaum mit 2 × 2-Stamm, der
  direkt hinter dem Hof wurzelt, steht mit einer Stammhälfte auf dem Hofrand (Vanilla setzt den Stamm vom Ursprung aus
  nach Osten und Süden). Kronen von Bäumen hinter dem Hof dürfen wie bei Vanilla-Dörfern über Wand oder Dach ragen; der
  Selbsttest zählt sie mit Abstand des Stamms. Bewusst nicht gewählt:
  Bandos erst nach der Vegetation setzen (Bäume stünden dann schon im Grundriss, abgeschnittene Kronen schwebten) oder
  Vegetation per Mixin im Grundriss abschalten (greift in jedes Feature ein, auch in die anderer Mods).
- **Bekannter Rest**: Ein Wüstenbrunnen (Vanilla, etwa einer je 1000 Wüsten-Chunks, nur auf Sand) entsteht wie die
  Eiszapfen nach den Bauwerken; liegt sein Mittelpunkt 1–2 Blöcke neben dem Hof, reicht er in den Hof. Abschätzung: etwa
  einer von 800 Wüsten-Bandos. Der Abstand-Tag hilft hier nicht (der Brunnen steht im Wüsten-Biom selbst).

## Fliegen

- Durchflüge: JUnit zählt Öffnungen, durch die ein 2 × 2-Rohr gerade hindurchpasst (mindestens 4 Blöcke frei davor und
  dahinter, auf mindestens drei Seiten Bauwerk in höchstens 6 Blöcken). Jeder Bando hat mindestens 6 solche Öffnungen
  (Steg 4), im Mittel 10–90 je Variante; mindestens die Hälfte jeder Variante (außer Stegen) hat ein Loch nach oben.
- Jeder Bando hat eine gerade Fluglinie von mindestens 8 Blöcken durch das Bauwerk (`BandoViews`, 3 breit und 2 hoch,
  mit Wand, Dach oder Boden auf mindestens zwei Seiten). Der Selbsttest setzt dort die FPV-Kamera ein.
- Spalten unter Laufstegen und Böden sind mindestens 2 Blöcke hoch, Stützenraster 4–6 Blöcke, Fenster ab 2 × 2.

## Truhen

- 55 % der Bandos versuchen eine Truhe zu setzen, gemessen haben gut die Hälfte eine (JUnit verlangt 35–65 %). Die Plätze
  kommen von der Variante: Ecke am Boden, Dachboden, Laufsteg, Büro, Pumpenhaus, Turmplattform, Deck. Über der Truhe und
  vor ihr ist frei, sie steht auf festem Grund und ist vom Himmel aus erreichbar.
- Beutetabelle `propwash:chests/bando` (normaler Beutetabellen-Weg, also auch Fabric `LootTableEvents`, NeoForge
  Global Loot Modifier und Datenpakete): 2–4 Würfe aus Props, Motoren 0802/1404, Zellen, Spulen, Carbonplatten,
  Platinen, 1S-/4S-Akkus (Lagerladung 40 %), LED-Streifen, dazu Faden, Eisennuggets, Kupferbarren, Redstone, Fackeln;
  Props und Motoren sind teils gebraucht (`propwash:part_condition` 0,6 bzw. 0,7). Bonus-Wurf mit 16 %: 2207-Motoren
  (gebraucht), Analog-Race-Cam, 20×20-AIO, Freestyle-Rahmen (gebraucht), 6S 1100, Action-Cam Light. Keine Gates und keine
  Spitzenteile. Gemessen 2,55 Propwash-Stapel je Truhe, 43 % davon gebraucht.
- Addons hängen eigene Beute ein: `PropwashApi.loot().inject(ResourceKey.create(Registries.LOOT_TABLE,
  Identifier.fromNamespaceAndPath("propwash", "chests/bando")), eigeneTabelle)`.

## Seltenheit und Platzwahl

- Strukturset `propwash:bandos` mit der Platzierung `propwash:world_course` der Rennkurse (wie `random_spread`, dazu
  `avoid`): **spacing 44, separation 16** Chunks, Salt 1964823117. Abstand zu möglichen Plätzen von Waldanwesen (6),
  Ozeanmonumenten (4), Wüsten- und Dschungeltempeln, Iglus, Hexenhütten (je 2) und Rennkursen (4). Dörfer und
  Plünderer-Außenposten stehen seit dem Nachtest von 0.5.0 nicht mehr hier, sondern in `avoid_overlap` (unten): Der
  Startabstand von 4 bzw. 3 Chunks reichte nicht, Dorfstraßen reichen bis etwa 5 Chunks weit, und auf Seed −5872310457
  lag eine Straße eines Taiga-Dorfs im Rahmen eines Förderturms (`mine_head`).
- **Keine Überschneidung mit Vanilla-Bauwerken** (`avoid_overlap` in jeder Bando-Struktur, `BandoStructure#overlapsOthers`,
  `BandoSite.reachChunks`): Nach der Platzwahl prüft der Start, ob in der Nähe ein Start einer der Strukturen aus
  `avoid_overlap` liegen kann, und verzichtet dann auf den Bando. Je Eintrag steht, wie weit (`reach`, Blöcke über den
  Start-Chunk hinaus) dieses Bauwerk höchstens reicht: Ruinenportale 16, Schiffswracks 32, Ozeanruinen 32 (Ruinen-Gruppen
  bis 16 Blöcke neben dem Start-Chunk plus Vorlage), Pfadruinen 48 (Jigsaw-Größe 7 und `bury`), verlassene Lager 32
  (Jigsaw mit `beard_thin`, neu in 26.3), Dörfer und Plünderer-Außenposten je 96 (Jigsaw bis 80 Blöcke um den Start,
  dazu `beard_thin` bis 12 Blöcke um jedes Teil; `BandoDataTest` verlangt für beide mindestens 96 und dass sie nicht
  zusätzlich in `avoid` stehen). Geprüft werden alle möglichen Start-Chunks des Sets, deren Bereich den Plan
  samt Hof berührt (`random_spread` mit Häufigkeit wie Vanilla), und nur dort, wo eine Struktur des Sets im Biom stehen
  darf (Biom in Chunk-Mitte und Nordwest-Ecke auf Geländehöhe). So kosten Schiffswracks und Ozeanruinen nur Bandos an der
  Küste, Pfadruinen und Lager nur in ihren Biomen. Auslöser: Ein Ruinenportal setzt das ganze Portal samt Netherrack, wenn
  der Chunk seiner Mitte erzeugt wird, auch in Nachbar-Chunks (`RuinedPortalPiece#postProcess`); wurde dieser Chunk nach
  dem Bando erzeugt, lagen Netherrack, Magma und weinender Obsidian in Hof und Grundriss eines Wasserturms (Seed
  123456789). Alle 21 Vanilla-Struktursets von 26.3 sind eingeordnet: gemieden über `avoid` oder `avoid_overlap`, oder unterirdisch
  bzw. in anderen Dimensionen (Minenschächte, vergrabene Schätze, antike Städte, Prüfungskammern, Festungen,
  Netherfestungen und -fossilien, Endstädte). Ausnahme mit Absicht: Die Minenschächte der Tafelberge
  (`mineshaft_mesa`) liegen in den Badlands bis an die Oberfläche. Wo ein Gang oder Raum den Bando schneidet, setzt der
  Bando seine Blöcke darüber (Seed 77777777: Fabrik und Raffinerie, im Schnitt nur Bando-Blöcke); offene Gänge dürfen
  am Rand neben dem Bando liegen. Sie über `avoid_overlap` zu meiden hieße, gut die Hälfte aller Bandos zu streichen:
  `avoid_overlap` unterscheidet nur Sets, und das Set `minecraft:mineshafts` enthält auch die gewöhnlichen Minenschächte
  unter fast jedem Biom (Start mit 0,4 % je Chunk, Gänge bis etwa 80 Blöcke weit). Die Lage-Prüfung im Selbsttest zählt
  Randspalten, deren Gelände in einem fremden Bauwerk liegt, deshalb nicht zum Median der Randhöhe. `BandoDataTest` und `bandos.py --check` schlagen fehl, sobald ein neues
  Vanilla-Set dazukommt, das nirgends steht. Bewusst nicht gewählt: die Sets in `avoid` der Platzierung des Struktursets
  aufnehmen (prüft nur mögliche Chunks ohne Biom; mit einem Abstand, der die Bauwerke sicher trennt, gäbe es gut ein Viertel
  weniger Bandos, auch fern jeder Küste) oder je Familie feste Listen im Code (Datenpakete könnten sie nicht ändern).
- Je Zelle versucht das Spiel die Familien in gewichteter Reihenfolge; es entsteht nur ein Bando, wenn das Gelände passt
  (`BandoSite`):
  - Land: neun Messpunkte über den Grundriss, alle trocken und im Biom der Familie, Höhenunterschied höchstens der Hang
    der Familie; Bodenhöhe wie unter „Lage im Gelände“, nicht unter dem Meeresspiegel.
  - Sumpf: neun Messpunkte im Sumpf, Grund zwischen Meeresspiegel − 6 und + 2; der Hallenboden liegt 3 unter dem
    Meeresspiegel, das abgetragene Gelände läuft voll Wasser.
  - Steg: das Land-Ende liegt auf dem Ufer (Meeresspiegel − 1 bis + 1), nach 9 Blöcken beginnt Wasser, am Ende mindestens
    3 Blöcke tief; der Steg dreht sich zum Wasser und darf sich dafür bis 16 Blöcke entlang seiner Achse verschieben.
    Das Biom der Struktur muss an Land-Ende, Mitte und Wasser-Ende passen (Mitte seit 08.10.: vorher stand auf Seed 42
    ein Steg quer über einer Flussmündung, Mitte im Biom „Fluss“). Die Mitte prüft `BandoSite#centered` seit 09.10. an
    allen acht Ecken ±2 Blöcke um die Mitte auf Höhe der Wasserlinie: Die Biom-Abfrage im Spiel verschiebt jeden Punkt
    zufällig in eine der angrenzenden 4×4×4-Biomzellen, ein einzelner Messpunkt in der Mitte traf deshalb manchmal
    „Meer“, während die Selbsttest-Abfrage an derselben Stelle „Fluss“ fand.
  - Bohrinsel: alle Messpunkte mindestens 6, höchstens 40 Blöcke tief; nur 30 % der passenden Zellen, sonst lägen auf
    den großen Ozeanen zu viele.
- Gemessen (Selbsttest, Seed 20261003, 289 Zellen um den Ursprung): etwa ein Bando je 1600 Blöcke Abstand (gemessen 53 von 289 Zellen, einer je 1644 Blöcke; mit Startabstand statt `avoid_overlap` für Dörfer und Außenposten 54, vor `avoid_overlap` 58, vor den Regeln für Küste und Eiszapfen 61, vor der Hangprüfung über dem Grundriss 65), also so
  selten wie Plünderer-Außenposten. Der Selbsttest verlangt 650–2200 Blöcke und jede Familie.

## Vanilla-Einstellungen, Datenpakete, /locate

- Bandos sind gewöhnliche Strukturen: mit *Bauwerke generieren: Aus* (`generate-structures=false`) entsteht keiner, und
  `/locate` findet keinen (Selbsttest mit eigener Welt). Es gibt bewusst keinen eigenen Schalter.
- Abschalten oder ändern per Datenpaket wie bei Vanilla: Biom-Tags `propwash:has_structure/bando/<familie>` leeren
  (keine Bandos dieser Familie), das Strukturset `propwash:bandos` überschreiben (Abstand, Gewichte, Familien), in den
  Strukturen `propwash:bando/<familie>` die Liste `avoid_overlap` ergänzen (z. B. um Bauwerke anderer Mods:
  `{"other_set": "<set>", "reach": <Blöcke>}`) oder die Beutetabelle ersetzen.
- `/locate structure #propwash:bandos` findet den nächsten, `/locate structure propwash:bando/desert` usw. eine Familie.
  `/locate` prüft wie bei Vanilla nur Strukturstarts (keine Bauarbeit) und stimmt mit der Welt überein.
- Server: reine Weltgenerierung, keine neuen Pakete, keine Änderung am Netzwerkprotokoll oder an der API.

## Kosten der Weltgenerierung

Gemessen im Selbsttest (Fabric, Apple Silicon, mit Bildschirmfotos im selben Lauf):

- Start (Platzwahl samt Bauplan) im Mittel 0,6–1,0 ms je Versuch (0,9 ms mit den 25 Messpunkten für Boden und Hang,
  1,0 ms mit den Prüfpunkten der Küste und dem Abstand zu Eiszapfen und Eisbergen, 1,02 ms mit `avoid_overlap`; die
  Prüfung läuft erst nach einer gültigen Platzwahl und braucht je möglichem Start in Reichweite eine Höhenabfrage),
  nur einmal je Rasterzelle von 44 × 44 Chunks.
- Setzen eines ganzen Bandos (bis ~3000 Blöcke) 18–22 ms in einer laufenden Welt (21 ms mit Fundamenten); in der
  Weltgenerierung verteilt sich das auf die 4–16 Chunks, die er berührt. Der Bauplan liegt als `SoftReference` am Teil;
  beim Laden aus dem Speicherstand wird er erst neu berechnet, wenn ein Chunk ihn noch braucht (Größe steht im NBT).
- Umgelegt auf alle Chunks: etwa 0,012–0,014 ms je Chunk, unter 0,1 % der Chunk-Erzeugung (16–21 ms je Chunk im selben
  Lauf).
- Vergleich mit gleichen Chunks: der Selbsttest erzeugt dieselben 7 × 7 Chunks um je einen Bando jeder Familie einmal in
  einer Welt ohne Bauwerke und einmal mit (gleicher Seed): 18,5 ms gegenüber 17,9 ms je Chunk, Faktor 1,03 (enthält
  auch die übrigen Bauwerke der Welt mit Bauwerken, etwa Minenschächte). Der Selbsttest verlangt höchstens den Faktor 3
  (die Messung schwankt mit der Last des Rechners) und unter 1 % Kosten des Bando-Codes.

## Absturz im Licht-Thread (behoben am 08.10.)

Seit dem 03.10. stürzte das Spiel selten beim Erzeugen neuer Welt ab, immer im Licht-Thread:
`MissingPaletteEntryException` (z. B. „Missing Palette entry for index 14“ in `LinearPalette.valueFor`) bzw.
`state is null` in `BlockLightEngine.checkNode`, beim Lesen eines `ProtoChunk`. In Selbsttests traf es etwa jeden
zehnten langen Lauf mit Bando-Welten, auf beiden Loadern.

**Ursache:** Der Licht-Thread liest jeden Chunk, sobald dessen Merkmale fertig sind (`ServerChunkCache#getChunkForLighting`
liefert ab Status `features`), und ein Block, der danach in diesen Chunk geschrieben wird, stößt über
`ProtoChunk#setBlockState` eine Licht-Prüfung an (`checkBlock`). Geschrieben wird dabei aus einem Worldgen-Thread, gelesen
im Licht-Thread, ohne Sperre: Bekommt ein Abschnitt beim Schreiben einen neuen Blockzustand, trägt `LinearPalette` ihn
ein und `PalettedContainer` speichert den neuen Index; sieht der Licht-Thread den Index vor dem Paletten-Eintrag (auf
Apple Silicon mit schwacher Speicherordnung gut möglich), fehlt der Eintrag – genau der Fehler aus den Absturzberichten.
Vanilla-Merkmale tun das auch, aber nur mit wenigen Blöcken über den Chunk-Rand. Bis zum 08.10. räumte und besserte jeder
Chunk am Bando nach seinen Bauwerken einen Streifen von 8 Blöcken in den Nachbar-Chunks aus (`clearSpill`, `restore`,
dazu der Blattabstand in `settleLeaves`), und zwar gerade in Nachbar-Chunks mit Status `features` oder weiter – also in
Chunks, die der Licht-Thread schon las. Das waren je Lauf einige Dutzend bis gut hundert Blöcke, oft neue Blockzustände
(Planken, Zierleisten, Dachstufen) in fast leeren Abschnitten über dem Gelände, wo die Palette noch klein und linear ist.

**Behebung:** Bandos schreiben nur noch in den Chunk, der gerade erzeugt wird – den sieht der Licht-Thread noch nicht.
`clearSpill` und `restore` sind entfernt, `settleLeaves` liest weiter im Umkreis, setzt aber nur Blätter in diesem
Chunk. Den Schutz vor Seen, Findlingen und Verliesen aus Nachbar-Chunks übernimmt `BandoGuard` beim Schreiben (Abschnitt
„Nachbar-Chunks“ oben): Er lehnt solche Blöcke ab, statt sie später zu reparieren, und schreibt selbst nichts.

**Nachweis:** Der Selbsttest bekam `-PpropwashBandoSeed=<Seed>` (Bando-Welten mit anderem Seed). Mit der Behebung
(Stand vom 09.10., mit `BandoGuard#tidy` und der Biom-Prüfung der Stegmitte) liefen die Bando-Schritte
(`-PpropwashSelftestOnly=bando:`, darin die Lage-Prüfung mit gut 140 Plätzen und mehreren tausend voll erzeugten
Chunks je Lauf) auf den Seeds 42, 77031, 123456789, −5872310457 und 987654321987 je einmal auf Fabric und NeoForge,
also 10 Läufe: alle 19 von 19 Schritten bestanden, kein neuer Absturzbericht, keine `MissingPaletteEntryException` im
Log; dazu der Standard-Seed 20261003 in den vollen Selbsttests beider Loader für das Release. Vorher (Läufe vom 03.10.
bis 08.10. mit `clearSpill`) gab es fünf Absturzberichte mit diesem Fehler; der Stand vom 08.10. abends ohne `tidy`
fiel auf Seed 77031 (Bienennest im Grundriss) und auf Seed 42 (Steg über einer Flussmündung) durch.

Bewusst nicht gewählt: die Paletten-Zugriffe des Licht-Threads per Mixin synchronisieren (greift tief in Vanilla und
kostet bei jeder Licht-Abfrage), das Ausbessern später im Server-Thread nachholen (Merkzettel je Chunk, Warten auf alle
Nachbarn).

## Tests

- JUnit `BandoPlanTest`: gleicher Seed = gleicher Plan, Vielfalt, Größe, nichts schwebt, Durchflüge, Fluglinie,
  Truhen (Anteil, offen, erreichbar, auf Grund), jede benutzte Rolle hat Blöcke in jeder Palette.
  `BandoSiteTest`: Drehung und Rückrechnung wie Minecraft, Platzregeln für Land, Hang, Wasser, Biom, Steg, Bohrinsel,
  Sumpf, Steg nicht in Steilküste oder Felsen, Bohrinsel nicht über einer Insel, 12 Blöcke Abstand zu Biomen des Tags
  `propwash:bando/keep_away`, `reachChunks` findet jeden Start, dessen Bauwerk den Bando berühren kann. `BandoDataTest`:
  Strukturen, Tags, Set, Beute, Blöcke gibt es in 26.3, kein Block ist Ziel einer Vanilla-Erzader oder -Scheibe, jedes
  Vanilla-Strukturset ist gemieden oder als unterirdisch eingetragen, `bandos.py --check`. Die Vanilla-Daten liest der
  Test aus dem Minecraft-Jar auf dem Klassenpfad (vorher suchte er `.ref/vanilla` in den Elternordnern und übersprang
  zwei Prüfungen still, wenn der Arbeitsbaum nicht im Hauptbaum lag). `LeafDistanceTest`: Blattabstand wie Vanilla.
  `BandoSceneTest`: Flugschul-Szene (Texte, Längen, Hinweise, Anker im Bild). `BandoPreviewTest` zeichnet mit
  `PROPWASH_BANDO_PREVIEW=<ordner>` isometrische Vorschaubilder aller Varianten (für die Website und zum Entwerfen).
  `BandoSupportTest`: nichts schwebt mit den echten Blockformen jeder Palette (Stufen oben oder unten, Doppelstufe,
  Treppe, Schneeschicht, Teppich, Truhe, Laterne; Berührung nur über volle Flächen), lose Teile und Deko liegen auf
  vollen Blöcken, jedes stehende Teil hat ein Fundament bis zum Grund, die Förderanlage hat keine tragenden Stufen.
  `BandoSiteTest` dazu: Boden im Hang auf dem 40-%-Wert, Unterkante der Bounding Box eine Zeile über dem Boden.
- Selbsttest in der Testwelt: Registrierung und alle 114 Blockzustände, Suche im echten Overworld-Generator (wie
  `/locate`, Seltenheit), alle 11 Varianten je zweimal gesetzt und geprüft (auch: nichts schwebt, siehe unten; vorher
  steht in jedem Grundriss Gras auf dem Boden, das danach Halt haben muss), Beute 600-mal gewürfelt.
- Selbsttest nach dem Verlassen der Testwelt (zwei eigene Welten, gleicher Seed): ohne Bauwerke kein Bando und kein
  `/locate`-Treffer, Zeitmessung; mit Bauwerken `/locate structure #propwash:bandos`, jede Familie gefunden,
  Zeitmessung derselben Chunks, jede Familie vor Ort geprüft (≥ 99 % der Bauteile stehen, Truhe mit Beutetabelle, Biom,
  Geländer verbunden, nichts schwebt, keine Pflanzen ohne Halt oder auf dem Dach) und mit `-PpropwashScreenshots=true`
  je Familie ein Luftbild und ein FPV-Bild (`docs/screenshots/bando_<familie>_<variante>_aerial.png` und `…_fpv.png`).
  Die Fundorte und ihre Reihenfolge (Familien, dann weitere Varianten in fester Reihenfolge) sind in jedem Lauf gleich,
  damit auch der Blickwinkel gleich bleibt; vor jedem Luftbild endet die FPV-Sitzung des vorigen Bandos, und das Bild
  entsteht erst, wenn der Spieler an Position und Blickrichtung des Luftbilds steht. Vorher zeigte
  `bando_coast_pier_aerial` je nach Lauf den Steg von der anderen Seite oder nur Himmel. Für das FPV-Bild steht der
  Pilot 8 Blöcke hinter der Drohne; liegt dort ein Hindernis zwischen ihm und der Drohne, sucht der Schritt näher und
  seitlich einen Platz mit freier Sicht (vorher war `bando_jungle_concrete_frame_fpv` schwarz, 60 dB Dämpfung, VID 0).
  Das Log nennt je FPV-Bild Verbindung, Abstand und Dämpfung.
- Lage im Gelände (`BandoPlacementTests`, Schritte `bando: Lage – …`): sucht im echten Generator bis Ring 26 je Variante
  bis zu 24 Plätze und prüft jeden (gemessen 168 Plätze, darunter 24 Stege und 24 Bohrinseln an Strand, Steinküste,
  kaltem und verschneitem Ufer), also Ebene, Wald, Wüste, Tafelberge, Schnee, Dschungel, Strand, Ozean, Berge und Sumpf.
  Vorher prüfte der Schritt je Variante nur den flachsten und den steilsten Platz (19); die Nachprüfung fand an weiteren
  Plätzen Eiszapfen, Stege in Steilküsten und Pflanzen auf dem Bau. Für jeden Platz werden die Chunks samt Nachbarn
  vollständig erzeugt (mit Vegetation) und geprüft (`BandoGroundCheck`):
  - nichts schwebt: Suche von den Blöcken auf dem Gelände aus über Nachbarn, die sich mit ihrer echten Form berühren
    (unten und oben volle Höhe, seitlich überlappend; Treppen auch über Eck);
  - Boden auf Geländehöhe: das Gelände neben jedem Block der Bodenzeile (Bau oder Hof) liegt im Median auf Bodenhöhe;
  - Pflanzen: keine ohne Halt (auf Stufen, Treppen, Zäunen), keine auf Dächern, Blätter nur als Teil einer Baumkrone mit
    Stamm (die Suche geht wie der Blattabstand von Vanilla auch durch Bando-Laub) oder als zerfallendes Laub (Abstand 7);
    Blätter ohne Stamm, deren Haufen über Grundriss und Hof hinausreicht, gelten als fremder Busch; Pflanzen auf vollen
    Blöcken, Baumkronen, zerfallendes Laub, fremde Büsche und Seegras werden gezählt;
  - Grundriss und Hof (alle Spalten mit Bauteil oder Hof, jede Familie; an der Küste über dem Strand): über der Bodenzeile kein Gelände in leeren
    Zellen des Plans (volle Blöcke außer Stamm und Laub; Fundamentpfeiler unter erhöhten Teilen zählen zum Bando), keine
    Pflanze auf solchem Gelände, kein Baum, dessen Stamm-Ursprung in Grundriss oder Hof steht (unterster Stammblock,
    bei 2 × 2-Stämmen die Ecke im Nordwesten; liegende Stämme umgestürzter Bäume zählen nicht). Kronen von Bäumen
    hinter dem Hof, die auf dem Bau liegen, werden je Baum mit dem Abstand des Stamms vom Hof gemeldet. Vor der Korrektur
    meldete der Schritt am Förderturm am Hang 22 Geländeblöcke und den Baum auf der Erdwand.
  Dieselben Prüfungen laufen für jede Familie in der echten Welt (`BandoWorldTests`) und in der Testwelt.
  Mit `-PpropwashScreenshots=true` je Variante vom flachsten und vom steilsten Platz ein Luftbild von der tiefsten Seite
  (`bando_placement_<variante>_flat.png` und `…_slope.png`). Die Kamera (`BandoCamera`, auch für die Luftbilder der
  Familien) geht vom gewünschten Blickwinkel aus und weicht in Schritten von 22,5° und 8 Blöcken Höhe aus, bis die
  Sichtlinien zur Mitte und zu den acht Ecken des Bandos frei von Blättern, Stämmen, Bambus und Gelände sind (vorher
  zeigte `concrete_frame_slope` auf Seed 123456789 nur Laub). Der Schritt dauert mit 168 Plätzen etwa 1,5 min. Vorher (ohne die Korrektur) fand derselbe Schritt an jedem Landplatz Gelände im Median einen
  Block unter dem Boden, schwebende Laufstege, Kegeldächer, Moos, Laub und Fracht.
- Andere Seeds: `-PpropwashBandoSeed=<Seed>` erzeugt die Bando-Welten (und die Suche in der Testwelt) mit diesem Seed statt
  20261003, z. B. `-PpropwashSelftestOnly='bando: Welt mit Bauwerken,bando: Lage' -PpropwashBandoSeed=123456789`. Vor
  dieser Runde hielt die Lage-Prüfung nur auf dem Test-Seed; auf drei anderen Seeds fand sie 7 schlechte Plätze von 455
  (Ruinenportal im Hof, Bäume in Lücken des Hofs, Stütze über einem Lavasee, Boden unter Blättern, Förderanlage auf dem
  Gelände). Jetzt grün auf 20261003 (168 Plätze), 123456789 (142), −5872310457 (152), 987654321987 (151) und 42 (160),
  jeweils alle elf Varianten. Die Nachprüfung von 0.5.0 fand auf 314159 einen schwebenden Heuballen (gestapelte Fracht
  über einem Pfad im Hof) und auf 77777777 eine Raffinerie „auf dem Gelände“ (Randspalten in offenen Minenschächten der
  Tafelberge). Nach den Korrekturen grün auf acht Seeds: 20261003 (166 Plätze), 123456789 (143), −5872310457 (158),
  987654321987 (149), 42 (156), 314159 (179), 77777777 (174) und −98765 (156; dort findet die Suche bis Ring 26 keinen
  Förderturm, der Selbsttest nennt ihn und prüft die übrigen zehn Varianten). Dazu eine Wegwerf-Prüfung auf den ersten
  fünf Seeds, die jedes Teil eines fremden Bauwerks meldet, dessen Rahmen einen Bando schneidet: kein Dorf mehr (vorher
  eine Taiga-Dorfstraße im Förderturm auf −5872310457), nur ein vergrabener Schatz unter einem Steg (unterirdisch) und
  Gänge eines offenen Tafelberg-Minenschachts an einem Wasserturm (Seed 42, im Schnitt nur Leiter und Stämme des Turms).
- Nachprüfung vom 08.10. (Seeds 1, 987654321, 20261003 sauber): Auf Seed 77031 stand eine Fichte im Grundriss einer
  eingestürzten Halle (`collapsed_hall`, Hang 7), gewachsen auf dem Schutt. Ursache: Der Schutt der Schnee-Paletten war
  Schneeblock, und Vanilla setzt nach den Bauwerken Fichten genau auf Schneeblock und Pulverschnee (`spruce_on_snow`,
  `pine_on_snow`, Filter `matching_blocks` eine Ebene tiefer). Der Schutt ist jetzt voller Schnee (`snow`, 8 Schichten),
  der gleich aussieht; JUnit `BandoDataTest.paletteBlocksAreNoGroundForLaterVanillaTrees` liest den Untergrund aller
  Vanilla-Features (`placed_feature`, `feature`) und lehnt jede Palette ab, die einen davon enthält. Danach grün auf
  77031 (160 Plätze, alle elf Varianten).
- Ebenda fand „bando: Weltgenerator findet jede Familie …“ die Familie Berge nicht bis Ring 14 (rund ±9800 Blöcke):
  Förderanlagen brauchen windgepeitschte Hügel oder steinige Gipfel mit Hang ≤ 22 und sind selten (auf dem Test-Seed die
  erste bei rund 10 km, auf 77031 bei 15,6 km; `/locate` findet sie). Die Suche geht für fehlende Familien jetzt bis
  Ring 40 (rund ±28 000 Blöcke) und probiert ab Ring 15 nur noch die fehlenden Familien; auf einem anderen Seed als dem
  Test-Seed meldet sie eine Familie, die auch dort fehlt, statt zu scheitern (wie die Lage-Prüfung). Dass Förderanlagen
  auf manchen Seeds weit weg liegen, bleibt so (mehr Biome oder steilere Hänge für die Berge wären eine Änderung der
  Weltgenerierung).

## Portierung

`bando.plan` kennt keine Minecraft-Typen; Paletten sind Blockzustand-Texte. Für 1.21.1/26.2 sind nur die Paletten
anzupassen (z. B. `iron_chain` → `chain`, Beton-Stufen und -Treppen gibt es dort nicht) und der dünne Teil in
`BandoStructure`/`BandoPiece` (Strukturtyp, Codec, Platzierung).
