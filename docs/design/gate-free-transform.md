# Gates frei drehen und frei groß (race, 0.5.0)

Anforderung (Besitzer, 05.10., aus dem Discord-Wunsch nach diagonalen Gates): Gates lassen sich frei um alle drei Achsen
drehen und bekommen eine eigene Größe. Die Taste „Gate drehen“ dreht weiter in Schritten, die Schrittweite ist
einstellbar (90/45/15/5°). Schleichen + Rechtsklick mit einem Gate-Item oder dem Kurs-Werkzeug auf ein platziertes
Gate startet „Gate einstellen“ mit Gieren, Nicken und Rollen in Grad, Breite und Höhe und einer
Live-Vorschau (seit dem Spieltest ein Einstell-Modus im Spiel, `docs/design/gate-edit-mode.md`); der Server entscheidet und lehnt blockierte Stellungen ab. Drohnen kollidieren mit orientierten Boxen
(OBB) statt achsparallelen, Zielen und Durchflug funktionieren in jeder Lage, Ideallinie, Markierungen, Startampel und
Stapelregeln passen sich an, alte Gates laden unverändert.

Grundlage ist das Lagemodell aus `docs/design/gate-mounting.md` (sieben Lagen, Richtung in 90°-Schritten, umgedreht).
Die freie Drehung kommt **obendrauf**, die Lagen bleiben.

## 1. Modell (`race.GateTilt`, `race.GateGeometry`)

- `GateTilt(yaw, pitch, roll)` in Grad, auf 0,1° gerundet: `yaw` und `roll` in (−180, 180], `pitch` in [−90, 90].
  `GateTilt.NONE` ist der Zustand aller Gates bis 0.4.0.
- Rahmen eines Gates: Der Grundrahmen (side, up, normal) aus Lage, Richtung und Umkehrung bleibt wie in 0.4.0. Darauf
  wirken nacheinander (in Gate-Koordinaten) **Rollen** um die Normale, **Nicken** um die Seitenachse des Grundrahmens
  und zuletzt **Gieren** um die Welt-Hochachse:
  `F = Ry(yaw) · B(Lage, Richtung, umgedreht) · Rside(−pitch) · Rnormal(roll)`.
  - Nicken positiv: die Flugrichtung zeigt nach oben, die Oberkante kippt zur Einflugseite. Die Ebene des Gates hängt
    nur vom Nicken ab, nicht vom Rollen.
  - Rollen positiv: aus Sicht des Piloten (Blick in Flugrichtung) im Uhrzeigersinn; 45° macht aus dem Quadrat die
    Raute.
  - Gieren dreht wie die Entity-Richtung (Minecraft-Gierwinkel) im Uhrzeigersinn von oben gesehen.
- **Drehpunkt** ist die Mitte der Öffnung im Grundrahmen. Danach setzt `GateGeometry` das Gate wieder auf seine Fläche:
  Es verschiebt den Rahmen entlang der Flächennormalen (Boden +Y, Decke −Y, Wand: aus der Wand heraus), bis der
  äußerste Punkt des Rahmens in diese Richtung wieder auf derselben Ebene liegt wie ohne Drehung. Eine Raute steht also
  mit der Spitze auf dem Boden, ein geneigtes Tauchtor berührt mit seiner Kante die Wand, ein geneigtes hängendes Gate
  hängt mit seinem höchsten Punkt einen Block unter der Decke.
- Die Entity-Position bleibt der Ursprung des **ungedrehten** Gates (wie in 0.4.0). `GateGeometry#originX/Y/Z` ist der
  Ursprung des gedrehten Rahmens; Durchflug, Boxen und Darstellung rechnen damit. Ohne Neigung ist alles bitgleich zu
  0.4.0 (eigener Pfad im Konstruktor, `JUnit GateFreeTransformTest#noTiltIsBitIdenticalToTheMountFrame`).
- `GateGeometry` ist seit 0.5.0 eine unveränderliche Klasse statt eines Records (Rahmen, Mitte und Ursprung werden einmal
  berechnet); Zugriffe, `equals`, `hashCode` und `toString` sind gleich geblieben, `sameAs` vergleicht mit Toleranz.
- **Gieren und Lagen mit vier Stellungen** (stehend, hängend): Der Gier-Anteil wird in Vierteldrehungen der Grundrichtung
  gefaltet, `tilt.yaw` bleibt in [0°, 90°). So ist ein zweimal um 45° gedrehtes Gate dasselbe wie ein einmal um 90°
  gedrehtes. Bei Wandlagen bleibt die Grundrichtung die Wandrichtung, `tilt.yaw` ist der Winkel dazu.
- **Größe:** Breite und Höhe der **Öffnung** von 1 bis 16 Blöcken in halben Schritten (`GateSize.snapOpening`); der
  Rahmen (0,5 Block je Seite) kommt außen dazu. Die Items klein/mittel/groß bleiben Startgrößen (2 × 2, 3 × 3, 5 × 3)
  und bestimmen Rahmenstärke und Tiefe.

## 2. Bedienung

- **Taste „Gate drehen“** (`race.client.GateMountKeys`): Schrittweite im Tab „Rennen“ (`RaceConfig.gateRotateStep`,
  Standard 90°). Stehende und hängende Gates drehen um den Schritt, mit 90° genau wie bisher. Liegende Gates und Wandlagen
  kehrt die Taste weiter um (Schritt egal). Vor dem Setzen merkt sich die Vorschau den Feinwinkel je Fläche
  (`GatePlacement.Preference#floorFine/ceilingFine`, geht mit `GatePreferenceC2S` an den Server). Gestapelte Gates drehen
  weiter nur um 180° (mit kleinerem Schritt: „Gestapelte Gates drehen nur um 180°“).
- **Gate einstellen** (seit dem Spieltest von 0.5.0 ein Einstell-Modus im Spiel statt eines Fensters, ausführlich in
  `docs/design/gate-edit-mode.md`): Schleichen + Rechtsklick mit Gate-Item oder Kurs-Werkzeug (Haupt- oder Nebenhand) auf
  ein Gate (`GateEntity#interact` startet `race.client.GateEditor` beim Client, der Server tut dabei nichts). Die Welt
  bleibt sichtbar, man läuft und schaut weiter; ein Panel rechts zeigt die Zeilen.
  - Zeilen Gieren (Kompass 0–359°), Nicken (−90…90°), Rollen (−180…180°), Breite und Höhe der Öffnung; Mausrad in
    5°-Schritten (Strg: 1°) bzw. 0,5 Block, Schleichen + Mausrad wählt die Zeile. Mit gehaltener Alt-Taste ist die Maus
    frei: Regler (ziehen, Pfeiltasten, Shift ×10, Rechtsklick = Standard) und −/+ in der Dreh-Schrittweite bzw. 0,5 Block.
  - Live-Vorschau in der Welt: passt die neue Stellung, steht das Gate dort mit allen Teilen und einem grünen Pfeil in
    Flugrichtung, die alte Stellung bleibt als blasser Umriss; sonst ein roter Geist samt Pfeil, die Statuszeile nennt
    den Grund. Die Prüfung beim Client ist dieselbe wie beim Server (`GateItem#free` mit Spielern und Drohnen), alle 10
    Ticks neu.
  - **Enter** oder Rechtsklick schickt `GateEditC2S(APPLY)` und beendet den Modus, **Esc** verwirft. Mit freier Maus:
    **Zurücksetzen** stellt Neigung auf 0° und die Öffnung auf die Item-Größe, **Aus dem Kurs** (nur mit Kurs-Werkzeug in
    der Hand) schickt `GateEditC2S(UNLINK)`.
  - Der Modus endet, wenn das Gate verschwindet, man sich weit entfernt oder weder Gate noch Werkzeug in der Hand hat.
- Bisher entfernte Schleichen + Rechtsklick mit dem Kurs-Werkzeug ein Gate aus dem Kurs. Das ist jetzt der Knopf „Aus dem
  Kurs“ im Panel (mit freier Maus); Shift-Tooltips, Flugschul-Text und Kurs-Werkzeug-Tooltip sagen das.

## 3. Server (`race.GateMounting`)

- `edit` prüft wie `rotate`: Reichweite (Interaktionsreichweite + 16, `GateMounting.EDIT_REACH`; `rotate` bleibt bei + 2), `mayBuild`, Kursbesitz (`CourseRegistry.canEdit`),
  Gate-Item oder Kurs-Werkzeug in der Hand (nur im Paket-Handler), keine Bearbeitung im Stapel, Platz
  (`GateItem#insideWorld`, `GateItem#free` ohne das Gate selbst, mit Spielern und Drohnen). Werte werden geklemmt und
  gerundet, die Befestigung (`GatePlacement.mountPoint`) bleibt, das Gate wächst und schrumpft darum herum (kein neues
  Raster, damit es nicht springt).
- Ein Kurs-Gate bleibt im Kurs; `CourseRegistry#gateRotated` übernimmt Neigung und Größe wie jede andere Änderung
  (Bestenliste ins Archiv, sobald Zeiten existieren).
- Platzprüfung geneigter Gates: Die ganze Platte des Gates (Breite × Höhe × Tiefe, auch die Öffnung) als OBB gegen
  die Kollisionsformen der Blöcke, die Rahmenteile als OBB gegen die Teile anderer Gates und gegen Spieler und Drohnen,
  senkrechte Gurte hängender Gates gegen Blöcke. Achsparallele Gates ohne Neigung prüfen wie in 0.4.0.

## 4. Kollision, Zielen, Durchflug

- `core.OrientedBox` (reines Java): Hülle, Trennachsen-Test gegen Boxen und andere OBBs (15 Achsen), Strahl-Test.
  `core.OrientedSweep`: wischt eine bewegte achsparallele Box gegen OBBs (Trennachsen mit Eintritts- und Austrittszeit),
  hält einen Abstand von 1E-5 und gleitet entlang der Fläche weiter (bis zu vier Kontakte je Bewegung). Steckt die Box
  schon in einer OBB, darf sie hinaus, aber nicht tiefer hinein.
- `ExtraCollider#collectOrientedColliders` liefert achsparallele Teile weiter als AABB, schräge als OBB.
  `QuadcopterEntity#move` wischt erst gegen die OBBs, dann wie bisher achsenweise gegen die AABBs; der Vanilla-Pfad
  (`mixin.race.EntityMixin`) gibt Drohnen nur noch die achsparallelen Teile. Spieler bekommen für schräge Teile eine
  Näherung aus Boxen von höchstens 0,25 Block entlang der längsten Achse (`GateGeometry.segments`).
- **Flugmodell:** `MoveReport#contactNormalX/Y/Z` (Standard NaN) trägt die Berührungsnormale eines schrägen Kontakts.
  `PilotSession` setzt sie und misst die Achs-Treffer dann gegen die bereits gleitende Bewegung; `DroneSim#afterMove`
  löst den Kontakt mit `CollisionSolver#resolve` an der echten Normalen (Abprall, Reibung, Prop-Strike, Einordnung
  Stoß/Crash, Boden bei Normale mit Y > 0,7, Kontaktgedächtnis wie bei den Achsen). Ohne Normale ist der Pfad unverändert:
  Bewegungs-Goldens und Kalibrierung bleiben bitgleich. Ein streifender Treffer an einem diagonalen Gate prallt damit ab,
  statt als Frontalaufprall zu zählen (`JUnit ObliqueContactTest`).
- **Zielen:** `GateEntity#pickBox` trifft schräge Teile per Strahl-OBB und gibt Vanilla eine winzige Box am Treffpunkt;
  durch die Öffnung und in leere Ecken der Hülle trifft nichts. `mixin.race.client.ProjectileUtilMixin` sucht große Gates
  zusätzlich in der Reichweite `ExtraCollider.reach()`, damit auch der ferne Pfosten eines 16 × 16-Gates anvisierbar ist.
- **Reichweite der Abfragen:** `ExtraCollider.reach()` startet bei 8 Blöcken und wächst, sobald ein großes oder geneigtes
  Gate geladen ist (höchstens 64).
- **Durchflug:** `GateGeometry#crossing` rechnet bei geneigten Gates in 3D (Vorzeichenwechsel zur Ebene, Öffnungsrechteck
  im gedrehten Rahmen); Client (`LapTracker`, Geister-Splits) und Server (`RaceServer`, `LapVerifier`) benutzen sie
  unverändert. `GateGeometry#upright` (stehend und ungeneigt) ersetzt `GateMount#upright` in der 2D-Abkürzung und in der
  Ideallinie (`RaceLine`).

## 5. Stapeln, Markierungen, Startampel

- Stapeln nur auf stehende, **ungeneigte** Gates (`GateStacking.stackable`); diagonale Stapel (gleicher Gier-Anteil)
  bleiben möglich, das aufgesetzte Gate übernimmt den Gier-Anteil. Geneigte Gates bekommen die Variante ohne Füße
  (`GateStacking.placedBase` = `MOUNTED`).
- Nummern-Tafel, Banner, Rückseiten-Marken und Halo hängen am gedrehten Rahmen. Die Leuchtpfeile liegen bei geneigten
  Gates eben auf dem Boden unter der Öffnungsmitte (Höhe des tiefsten Rahmenpunkts) und zeigen in die waagerechte
  Flugrichtung; bei fast senkrechter Flugrichtung (Tauchtor) bleiben sie wie bisher in der Gate-Ebene.
- Startampel und Startaufstellung hängen nur an Kurs und Zeit, nicht an der Lage, und brauchen keine Änderung.

## 6. Darstellung (`race.client.GateRenderer`)

- Rahmen-Quaternion aus dem gedrehten Rahmen, vorher Versatz auf `origin` (`GateRenderer#toFrame`).
- **Eigene Größen** zeichnet der Renderer aus Teilen: Ecken, T-Stücke, Pfosten- und Balkenstücke zu 1 und ½ Block,
  Füße, Lichtleisten, Banner-Kappen und -Felder (`models/gate/piece/*.json`, Item-Definitionen `race/gate_piece_*`,
  erzeugt von `tools/assets/world/generate.py#gen_gate_pieces` aus denselben Elementen wie die fertigen Modelle; geprüft in
  `validate.py#check_gate_pieces`). Standardgrößen zeichnen die fertigen Modelle wie bisher.
- Geneigte hängende Gates: senkrechte Gurte von den Pfostenköpfen bis zur Decke aus Klemme, gestreckter Stange und
  Deckenplatte (`hanger_clamp`, `hanger_strap`, `hanger_ceiling_plate`).
- Flugschul-Gates (`guide.client.render.SceneGateVisual`) nehmen Nicken und Rollen aus dem Darsteller und eine eigene
  Öffnung aus `ActorSpec.Gate#openingWidth/openingHeight`.

## 7. Speicherung, Netz, Kompatibilität

- Entity-NBT: `Tilt` (`GateTilt.CODEC`, nur wenn nicht `NONE`), `OpeningWidth`/`OpeningHeight` (nur bei eigener Größe).
  Alte Welten laden unverändert.
- Synchronisiert: `DATA_TILT` (Vector3f Gieren/Nicken/Rollen) und `DATA_OPENING` (Breite und Höhe in halben Blöcken,
  0 = Item-Größe).
- Kurs-Daten (`propwash_race.json`): `GateRecord.turn/pitch/roll/width/height` (fehlend = 0 = wie bisher);
  `CourseData.Gate` überträgt sie mit einem Merkerbyte. `layoutHash` bezieht Neigung und Größe nur ein, wenn sie vom
  Standard abweichen → Layouts, Geister und Bestenlisten bestehender Kurse bleiben gültig.
- **Netzprotokoll 10 → 11**: zwei Entity-Datenfelder, `CourseData` mit Merkerbyte, `GateRotateC2S` mit Schritt,
  `GatePreferenceC2S` mit Feinwinkeln, neues `GateEditC2S` (`propwash:race_gate_edit`). Die öffentliche API ist nicht
  betroffen (`API_VERSION` bleibt).

## 8. Flugschule und Texte

- Neue Szene `gate_free` „Gates frei drehen“ (`guide.scenes.GateFreeScene`) direkt nach „Gates, Kurs und Rennen“: Gate
  einstellen (Schleichen + Rechtsklick), Gieren 45° → Nicken 25° → Rollen 45° (Raute), eigene Größen (6 × 1,5 und
  1,5 × 5), Drehen in 45°-Schritten. Öffnet sich über Gate-Items und das Kurs-Werkzeug.
- Texte in `common/src/lang/race` und `common/src/lang/guide` (de_de, en_us).

## 9. Tests

- JUnit `core.OrientedBoxTest`: Hülle, Trennachsen gegen Boxen und OBBs, Strahl-Test, Wischen mit Gleiten, kein
  Durchtunneln dünner Pfosten bei hoher Geschwindigkeit (500 Zufallsfälle), Berühren und Entlanggleiten, Herauskommen ohne
  tieferes Eindringen, achsparallele OBB wie Achsen-Wischtest.
- JUnit `sim.ObliqueContactTest`: frontal gegen eine gedrehte Wand crasht wie gegen eine gerade, streifend an einem
  diagonalen Gate prallt ab statt zu crashen, Tempo entlang der Fläche bleibt, die Drohne gleitet weiter; die alte
  Achsen-Meldung hätte denselben Treffer als Crash gezählt.
- JUnit `race.GateFreeTransformTest`: bitgleich ohne Neigung, Rahmen orthonormal und rechtshändig bei jeder Neigung,
  Gate bleibt auf seiner Fläche, Vorzeichen von Nicken und Rollen, Durchflug bei beliebigen Winkeln (nur vorwärts, nicht
  durch den Pfosten), Teile frei von der Öffnung und in der Hülle, Näherung für Spieler, senkrechte Gurte, Umkehren
  behält die Ebene, kleine Schritte falten in Vierteldrehungen, Vorschau-Feinwinkel, Einstellen behält den
  Befestigungspunkt, Öffnungen in halben Blöcken, Stapelregeln, Kursdaten im Paket und Prüfsummen.
- JUnit `guide.FlightSchoolStoriesTest#freeGateTurnsTiltsRollsAndGrowsAsTheTextSays`.
- Selbsttest `race.client.GateFreeSelfTests` (beide Loader): zehn Gates per Item und Einstellung (diagonal, zurück-
  diagonal, geneigt, Raute, schräges Tauchtor, gerollt hängend, 1 × 1, 8 × 2, 3 × 9,5, 12 × 12), Lage, Fläche, Boxen,
  Fußvariante, Ablehnung durch einen Block, aus der Ferne und im Stapel, Taste mit 15° und 45°, Stapel auf diagonalem
  Gate, Kursdaten; Client-Synchronisation; Zielen auf Raute, leere Hüllen-Ecke und fernen Pfosten des 12 × 12-Gates;
  Drohne prallt am schrägen Pfosten ab und fliegt frei durch die Öffnung; Einstell-Modus über echtes Schleichen +
  Rechtsklick mit Mausrad, Laufen, freier Maus, Enter und Esc (Einzelheiten in `docs/design/gate-edit-mode.md` §5),
  Vorschau blockiert und frei, Übernehmen beim Server; NBT neu und alt. Für die Client-Schritte steht der Spieler am Rand
  des Testbaus (sonst liegt er bei Sichtweite 6 außerhalb der Entity-Reichweite), danach kehrt er zurück. Der Testbau für
  die Bildschirmfotos entsteht erst, wenn seine Chunks Entities ticken: Gates in nur kurz geladenen Chunks speichert
  Minecraft weg und lädt sie später mit neuen IDs. Bricht der Aufbau ab, räumt er seine Gates selbst weg.
- Bildschirmfotos (Fabric): `gate_free_diagonal`, `gate_free_tilted`, `gate_free_dive`, `gate_free_hanging`,
  `gate_free_sizes`, `gate_edit_mode`, `gate_edit_mode_de`, `gate_edit_cursor`, `flight_school_gate_free`,
  `flight_school_gate_free_size`.

## 10. Entscheidungen

- **Neigung als Zusatz zur Lage** statt eines freien Quaternions für alles: alte Gates, Wand- und Deckenlagen,
  Platzierung und Raster bleiben, wie sie sind. Verworfen: Lagen durch drei Euler-Winkel ersetzen (alte Kurse und das
  Befestigungsmodell hätten sich geändert).
- **Drehen um die Öffnungsmitte und danach auf die Fläche setzen.** Verworfen: Drehen um den Befestigungspunkt (eine Raute
  würde in den Boden kippen, der Server müsste fast jede Rollung ablehnen).
- **Größe als lichte Öffnung**, nicht als Außenmaß: Piloten fliegen durch die Öffnung, die Items heißen 3 × 3 usw. nach dem
  Außenmaß, die Öffnung ist ein Block kleiner. Verworfen: Außenmaß 1–16 (eine 1-Block-Öffnung wäre nicht möglich).
- **Schrägen Kontakt im Flugmodell mit echter Normale**, nur wenn er vorkommt. Verworfen: nur geometrisch gleiten und den
  Treffer als Achs-Treffer melden (ein streifender Treffer zählte als Frontalaufprall und würde crashen).
- **Eigene Größen aus Teilmodellen** derselben Pipeline. Verworfen: das fertige Modell nicht gleichmäßig strecken (Pfosten
  würden dicker) und schlichte Farbboxen (passen nicht zu den Gates).
- **Kurs-Werkzeug + Schleichen startet das Einstellen**, Entfernen aus dem Kurs ist ein Knopf im Panel. Verworfen: das alte
  Entfernen beim Werkzeug lassen (widerspricht der Vorgabe, beide Hände starten das Einstellen).
- **Gestapelte Gates lassen sich nicht einstellen.** Verworfen: Einstellen mit Neuberechnung des Stapels (Größenänderungen
  verschieben alle Gates darüber).

## 11. Offene Punkte

- Freie Position (Versatz) beim Einstellen und frei wählbarer Abstand hängender Gates zur Decke.
- Gate-Items mit gespeicherter Größe/Neigung (Kopieren eines eingestellten Gates).
