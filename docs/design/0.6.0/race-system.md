# Rennsystem 0.6.0 – Design (Teil A und Teil B)

Stand 19.10.2026, Basis `integration` nach `gate-legs` (Protokoll 17, API 1.7.0). Gilt für den Kern `propwash` (Fabric
und NeoForge). Teil A baut der Strang `race-a`, Teil B danach der Strang `race-b` auf dem gemergten Teil A. Dieses
Dokument legt für beide Teile Entscheidungen, Datenformat und Migration fest; Teil B ergänzt hier nur, was sich bei der
Umsetzung genauer ergibt.

| Teil | Thema | Strang |
|---|---|---|
| A1 | Rückkehr zum Homepoint startet kein Rennen | `race-a` |
| A2 | Wertungshöhe für Wendeflaggen und Slalomstangen, auch nach unten | `race-a` |
| A3 | Gate-Stangen mit Wertungsbereich, im Kurs zuweisbar | `race-a` |
| A4 | Freiform-Gate: Lücken im Rand ignorieren | `race-a` |
| A5 | Sichtbare Durchflug- und Wertungsflächen | `race-a` |
| A6 | Verschieben in kleinen Schritten im Einstell-Menü | `race-a` |
| B1 | Ein Gate mehrmals im Kurs, auch in Gegenrichtung | `race-b` |
| B2 | Gates kennen ihre Nachbarn: Stapeln auf Gates und Würfeln, Reihen, Würfel aus Gates | `race-b` |

---

## 1. Ziel und Vorgaben

Vorgaben sinngemäß (wie in den übrigen Dokumenten dieses Ordners, E1 in `fixes-polish.md`):

| Datum | Vorgabe | Quelle |
|---|---|---|
| 17.10. | Kehrt die Drohne zum Homepoint zurück, startet von selbst der Countdown von 3 s | Fehlermeldung im Community-Discord |
| 17.10. | Gates und Flaggen prüfen zu wenig Höhe: die Prüfung einer Flagge reicht nicht nach unten, hohe rechteckige Flaggen lassen sich nicht bauen | Fehlermeldung im Community-Discord |
| 18.10. | Gates sind untereinander nicht verträglich: Flaggen und Stangen lassen sich nicht sauber auf Gates und Würfel setzen, Gates nicht nebeneinander ohne doppelte Pfosten, ein Gate nicht mehrmals in einem Kurs, Gate-Stangen sind nur Deko | Rückmeldung im Community-Discord |
| 19.10. | Besitzer: Rückkehr zum Homepoint (Reset oder GPS-Rescue) darf nie einen Start auslösen; ein stehender Start beginnt nur beim Scharfschalten am Start-Gate, wie in 0.5.2 | Owner-Entscheidung |
| 19.10. | Besitzer: Wertungshöhe von Flaggen und Slalomstangen im Menü (Schleichen + Rechtsklick) einstellbar, auch nach unten | Owner-Entscheidung |
| 19.10. | Besitzer: Gate-Stangen bekommen einen Wertungsbereich wie Slalomstangen und Flaggen und kommen in Kurse | Owner-Entscheidung |
| 19.10. | Besitzer: Freiform-Gate mit Knopf zum Ignorieren eines unvollständigen Rands | Owner-Entscheidung |
| 19.10. | Besitzer: das aktive Gate zeigt eine Durchflugfläche wie das Freiform-Gate, Flaggen, Stangen und Gate-Stangen zeigen ihre Wertungsfläche – dezent, im FPV und in der dritten Person, auch mit Shaderpacks lesbar | Owner-Entscheidung |
| 19.10. | Besitzer: im selben Menü Gates in kleinen Schritten verschieben, Schrittweite wählbar, alle drei Achsen, mit Tasten und Knöpfen | Owner-Entscheidung |
| 19.10. | Besitzer: ein Gate darf mehrmals in einem Kurs vorkommen (Schleifen), auch in mehrere Richtungen | Owner-Entscheidung |
| 19.10. | Besitzer: Gates kennen ihre Nachbarn – Flaggen und Stangen auf Gates und Würfeln, Gates in Reihe ohne doppelt dicke Pfosten, Würfel aus Gates | Owner-Entscheidung |
| 19.10. | Besitzer: keine völlig freien Rechteck-Gates ohne Blöcke; Freiform-Gate und freie Größe decken das ab | Owner-Entscheidung |
| 19.10. | Gemeinsam: alte Kurse, Bestenlisten und Welten bleiben gültig; Kursformat additiv mit Formatversion; Migration mit Testwelten aus 0.5.2, 0.5.0 und 0.4.0 geprüft; Rennen auf dem dedizierten Server mit Beitrittstest | Owner-Entscheidung |

## 2. Umfang

### 2.1 Drin

- **Teil A:** A1 bis A6 aus der Tabelle oben, mit Formatversion 2 der Kursdatei, Tests und Spieler-Doku
  (`docs/race-system.md`).
- **Teil B:** B1 und B2, Platzierungsregeln und Kollision dazu, Migration alter Kurse auf Durchgänge.

### 2.2 Bewusst nicht drin

- Freie Rechteck-Gates ohne Blockbindung (Vorgabe 19.10.).
- Geneigte Flaggen und Stangen; Wertung bleibt eine senkrechte Halbebene.
- Wertung waagerechter Gate-Stangen (an der Wand): eine liegende Stange hat kein „links/rechts vorbei“, das man im Flug
  versteht. Nur stehende, hängende und aufgesetzte Stangen werten.
- Automatisches Verlängern der Wertung bis zum Boden: Boden ist beim Client und beim Server nicht immer gleich geladen,
  und ein abgebauter Block änderte still die Bedeutung von Bestenlisten.

---

## 3. Teil A – Architektur

### 3.A1 Rückkehr zum Homepoint startet kein Rennen

**Ursache (im Selbsttest nachgestellt):** Reset (`rescue.HomeResets`) merkte im Client einen stehenden Start vor
(`ClientRaceTracker#onHomeReset` setzte `resetStart`, Entscheidung aus `home-point.md` §7). Das nächste Scharfschalten
nahe am Start-Gate – der Homepoint liegt meist dort – öffnete deshalb die Startaufstellung mit 3 s Countdown, auch wenn
„Start beim Scharfschalten“ aus war. Für den Spieler sieht das aus, als starte die Rückkehr zum Homepoint das Rennen von
selbst. Der Selbsttest „race: Wertung – Reset scharf …“ schlägt mit dem alten Code genau an dieser Stelle fehl
(„Scharfschalten am Start nach dem Reset startet ohne Einstellung einen Countdown“). Der Selbsttest `reset:` prüfte das
alte Verhalten als gewollt. Ein zweiter, seltener Weg zur Scheinflanke: `PilotSession#onReset` übergibt der Entity nach
dem Reset bis zum nächsten Sim-Schritt noch die alte Telemetrie (scharf); hält die Physik, bis die Chunk-Spalte am Ziel
geladen ist (bis 5 s), sieht der Tracker „entschärft → scharf“, ohne dass der Pilot etwas tut. Beide Wege schließt die
Änderung unten.

**Neu:**
- `race.ArmEdge` (reines Java): eine Flanke zählt nur, wenn der Tracker die Drohne in **dieser** Sitzung vorher entschärft
  gesehen hat. Beim Sitzungsbeginn, bei einem Drohnenwechsel und nach einem Reset gilt der aktuelle bzw. ein scharfer
  Zustand als „vorher“ – erst ein entschärftes Bild und danach ein scharfes ist eine Flanke. Das deckt auch die Übernahme
  einer schon fliegenden Drohne (Heimflug am Server, Sitzung neu begonnen) ab.
- `race.StandingStart` (reines Java): Aufstellung nur bei echter Flanke, nahe am Start-Gate (`COUNTDOWN_RANGE` 24 Blöcke
  wie 0.5.2), ohne laufenden Lauf, nicht schon in der Aufstellung, und nur, wenn „Start beim Scharfschalten“ an ist oder am
  Start schon eine Aufstellung offen ist (Mehrspieler) – genau das Verhalten von 0.5.2 ohne den Reset-Sonderfall.
- `resetStart`/`resetStartPending` entfallen. Reset bricht wie bisher den Lauf mit `RESET` ab und verlässt die
  Aufstellung; GPS-Rescue ändert nichts am Rennen (Landung entschärft, Wiederscharfschalten folgt der Regel oben).
- Der Selbsttest `reset:` (Gruppe von `rescue`) prüfte ausdrücklich das alte Verhalten „stehender Start nach dem Reset“;
  er wird auf das neue umgestellt (Reset **scharf**, kein Countdown; Scharfschalten am Start ohne Einstellung startet
  nichts, mit Einstellung schon; zweiter Reset verlässt die Aufstellung). Das ist die einzige Datei außerhalb von
  `race/**`, die Teil A anfasst (Abweichung vom Dateibesitz, im Bericht genannt).

### 3.A2 Wertungsbereich: `race.ScoreArea`

Ein kleiner unveränderlicher Datensatz (reines Java) beschreibt alles, was an einem Kurs-Element außer Lage und Größe die
Wertung bestimmt:

| Feld | Bedeutung | Bereich | Standard |
|---|---|---|---|
| `role` | nur Gate-Stange: `NONE` (Deko), `PASS` (vorbeifliegen wie Slalomstange), `TURN` (umrunden wie Wendeflagge) | 0–2 | `NONE` |
| `side` | nur Gate-Stange: links oder rechts (vorbei bzw. herum) | 0–1 | links |
| `reach` | nur Gate-Stange: wie weit neben der Stange die Wertung reicht (bei Flagge/Slalomstange ist das die Breite) | 0,5–16 | 4 |
| `below` | Wertung reicht so weit **unter** den Fuß | 0–32, halbe Blöcke | 1 (= `CourseElement.BELOW`, Verhalten bis 0.5.2) |
| `above` | Wertung reicht so weit **über** die Spitze | 0–32, halbe Blöcke | 0 |
| `open` | nur Freiform-Gate: Lücken im Rand ignorieren (A4) | an/aus | aus |

- `normalized(kind)` lässt nur die Felder stehen, die für die Art gelten; `forCourse(kind)` zusätzlich ohne `open` und mit
  `PASS` statt `NONE` für Gate-Stangen (eine Stange im Kurs wertet immer).
- `pack()`/`unpack()` als 24 Bit in einem `int` (Netz und Entity-Daten), `isDefault()`.
- `GateGeometry` trägt das Feld (`score()`, `withScore`); `at`, `withTilt`, `withVariant`, `withLegs` behalten es,
  `sameAs`/`equals` vergleichen es, `hashCode` nur bei Abweichung (Hash alter Geometrien gleich).
- Die Halbebenen von Stange und Flagge (`GateGeometry#ray`) reichen von `−below` bis `Höhe + above` statt fest von −1 bis
  zur Spitze. Mit Standardwerten rechnet alles bitgleich wie 0.5.2 (JUnit vergleicht Stufen und Runden).

### 3.A3 Gate-Stangen mit Wertung und im Kurs

- Wertet eine Gate-Stange (`role` ≠ `NONE`) und steht senkrecht (`STANDING`, `HANGING`, auch auf einem Gate oder einer
  Stange), hat ihre Geometrie die Stufen einer Slalomstange (`PASS`) bzw. einer Wendeflagge (`TURN`) mit `reach` als
  Breite und `side` als Seite. `GateGeometry#scoringKind()` liefert dafür `POLE` bzw. `FLAG` (Zahl der Stufen,
  Prüfpunkte, strenges Auslassen); Art und Farbe der Stange bleiben `GATE_POLE`. Wandstangen werten nie.
- `CourseCheckpoints`, `LapTracker`, `GhostTrack` und `RaceServer` zählen Stufen über `scoringKind()` statt über die Art.
- **Kurs-Werkzeug:** Klick auf eine senkrechte Gate-Stange hängt sie an. Ist sie noch Deko, wird sie dabei zur
  Vorbeiflug-Stange; folgt sie auf eine Slalomstange oder Vorbeiflug-Stange, nimmt sie wie Slalomstangen die andere Seite
  („Slalom: links vorbei“). Wandstangen lehnt es ab, als Start ebenfalls (wie alle Elemente).
- Im Kurs lässt sich die Rolle nur zwischen „vorbei“ und „umrunden“ wechseln; „keine“ geht erst nach „Aus dem Kurs“.
- Darstellung: Die Stange zeigt im Kurs ihre Nummer auf einer Tafel und dieselben Bodenpfeile/Kreuze wie Slalomstange bzw.
  Flagge (geteilter Code in `CourseElementRenderer`) in Kursfarbe (grün als nächstes Ziel); die LED-Farbe der Stange
  bleibt, sie ist die Farbe, die der Spieler gewählt hat.

### 3.A4 Freiform-Gate: Lücken ignorieren

- **Beim Setzen:** Meldet die Flutfüllung „nicht ringsum geschlossen“, zeigt die Vorschau den Grund und darunter
  „Schleichen + Rechtsklick: Lücken ignorieren“. Hält der Spieler Schleichen, zeigt die Vorschau die Fläche, die dann
  entsteht (bernsteinfarbener Umriss), und Schleichen + Rechtsklick setzt das Gate mit `open = an`.
- **Im Menü:** Zeile „Rand“ mit „Lücken melden“ / „Lücken ignorieren“ (Knopf mit freier Maus, Mausrad sonst). „Neu
  abtasten“ nutzt die gewählte Art.
- **Fläche bei offenem Rand** (`FreeformFill#lenient`, reines Java): Vom Startfeld aus sucht die Füllung in der Ebene je
  Richtung (±a, ±b) den ersten festen Block innerhalb der Grenze. Fehlt eine Seite, kommt sie aus den vorhandenen Blöcken:
  fehlt oben, endet das Rechteck an der höchsten Stelle der seitlichen Pfosten; fehlt eine Seite links, am Ende des Bodens
  bzw. Sturzes – je Richtung aus den beiden quer liegenden Seiten, die es gibt. Fehlen beide Seiten einer Achse und gibt
  es keine quer liegende, bleibt es bei „nicht geschlossen“ (ein einzelner Pfosten ergibt kein Rechteck). Im Rechteck
  füllt die gewohnte Flutfüllung die offenen Felder; Blöcke darin bleiben Rand. Größer als die Welt-Regel →
  „zu groß“ wie bisher.
- **Warnung:** Gates mit `open = an` melden offene Kanten nicht mehr (kein rotes Pulsieren, kein Warndreieck dafür);
  zugebaute Felder melden sie weiter.
- Zählung, Kursdaten und Bestenlisten ändern sich nicht: gezählt wird wie immer die gespeicherte Fläche.

### 3.A5 Sichtbare Durchflug- und Wertungsflächen (`race.client.ScoreAreaRenderer`)

- Gezeichnet über `ClientBus.SUBMIT_WORLD_GEOMETRY` (wie Ideallinie und Gate-Vorschau), nicht im Entity-Renderer: eine
  Wertungsfläche reicht bis 16 Blöcke neben und 32 Blöcke unter die Stange, das Entity-Culling schnitte sie ab. Gates
  werden im Umkreis von 64 Blöcken um die Kamera gesucht. Renderart `eyes` wie Gate-LEDs und Freiform-Füllung: hell im
  Dunkeln, keine Schatten im Schattenpass von Iris (Level-Events erreichen ihn nicht, `docs/design/shader-packs.md`).
- **Aktives Gate:** das nächste Ziel im eigenen Rennen, vor dem Start (Phase `IDLE`, eigener Kurs) und im Countdown das
  Start-Gate. Seine Öffnung (ab `openingFloor()`, also beim Gate auf Beinen bis zum Boden) bekommt die Füllung des
  Freiform-Gates (16 % × Stärke 0,85) in Lichtfarbe.
- **Kurs-Elemente:** Flagge, Slalomstange und wertende Gate-Stange zeigen ihre Stufen als Fläche (Füllung 10 %) mit
  feinen Kanten (Band 0,04 Block, 55 %), damit die Fläche auch mit Shaderpacks, die Füllungen abschwächen, als Rahmen
  lesbar bleibt. Stärke: aktives Element 0,85; beim Bauen (Kurs-Werkzeug, Element oder Gate-Stange in der Hand, oder im
  Einstell-Menü) alle Elemente bis 32 Blöcke 0,6; sonst nur nahe Elemente (Annäherung 3 → 12 Blöcke wie beim
  Freiform-Gate) bis 0,5. Das Menü zeigt die geänderte Fläche des Entwurfs live.
- Schalter „Wertungsflächen zeigen“ im Tab „Rennen“ (Spalte Anzeige, Standard an; `race.json` `scoreAreas`).
- Beim Zuschauen (F7) wie bei der Ideallinie aus.

### 3.A6 Verschieben in kleinen Schritten

- Neue Seite „Position“ im Einstell-Menü (Schleichen + Rechtsklick) für Gates, Flaggen, Slalomstangen, Tauchwürfel und
  Gate-Stangen (nicht für Freiform-Gates, deren Fläche an Blöcke gebunden ist).
- Zeilen: **Schrittweite** (1/16, 1/4, 1 Block; gemerkt in `race.json` `nudgeStep`, Standard 1/4), **X (Ost)**, **Y
  (oben)**, **Z (Süd)** – jeweils der Versatz gegenüber der Stellung in der Welt, ±8 Blöcke.
- Bedienung: Mausrad auf der gewählten Zeile verschiebt um die Schrittweite (Strg: 1/16); mit freier Maus −/+ und Regler;
  **Tastatur** im Menü ohne freie Maus: Pfeiltasten verschieben waagerecht relativ zum Blick (auf die nächste Weltachse
  gerastet: ↑ vom Spieler weg, ← nach links), Bild↑/Bild↓ nach oben/unten – jeweils um die Schrittweite, Strg um 1/16.
  Mit freier Maus bleiben die Pfeiltasten wie gewohnt am Regler.
- Nachtrag (Befund PATTY-3, Besitzer 29.10.): **Strg + Umschalt** verschiebt um 1/32 Block (Pfeiltasten, Bild↑/↓,
  Mausrad, −/+); der Versatz rastet auf 1/32, die Grenze bleibt ±8. Strg + Umschalt + Mausrad wählt deshalb keine Zeile
  mehr, Schleichen + Mausrad allein weiter schon. Ohne freie Maus zeigt das Menü links neben sich den Tooltip der
  gewählten Zeile (einige Sekunden nach jedem Zeilenwechsel, Länge nach Textlänge), mit dem Strg-Schritt der Zeile
  (`propwash.race.edit.fine.*`); die Fußzeile nennt ihn je Seite (`propwash.race.edit.hint.value.angle|pulse|plain`).
- Nachtrag (Befund AUTO30-2): Beim Freiform-Gate folgt die erste Zeile der Fußzeile der gewählten Zeile
  (`GateEditPanel#freeformHintKey`): Flugrichtung `…hint.flip`, Ausrichtung `…hint.freeform.turn` (90°), Rand
  `…hint.freeform.toggle`, Seite Redstone wie bei den anderen Gates `…hint.value.pulse` (Strg: 1 Tick); darunter immer
  „Schleichen + Mausrad: Zeile wählen“. Die Höhe der Fußzeile reicht für die längste Zeile der Seite, das Menü springt
  beim Zeilenwechsel nicht.
- Weltachsen statt Gate-Achsen: Blöcke liegen achsparallel, und bei geneigten Gates wären lokale Achsen schräg.
- Server: `GateEditC2S` trägt den Versatz; `GateMounting#edit` prüft ±8 je Achse, Reichweite, Baurecht, Kursbesitz und
  Platz wie jedes Einstellen. Gates in einem Stapel und Träger von Gate-Stangen sind gesperrt (wie bisher beim Verstellen).
  Ein verschobenes Gate im Kurs ändert das Layout (Bestenliste wird archiviert, wie bei jedem Verstellen).

### 3.A7 Einstell-Menü: Seiten

| Art | Seite 1 | Seite 2 | Seite 3 |
|---|---|---|---|
| Gate | Drehung, Öffnung (wie bisher) | Redstone (wie bisher) | Position |
| Flagge, Slalomstange | Richtung, Seite, Wertung (Breite), Höhe | Wertung: nach unten, nach oben | Position |
| Tauchwürfel | wie bisher | Position | – |
| Gate-Stange | Richtung, Farbe, Länge | Wertung: Rolle, Seite, Reichweite, nach unten, nach oben | Position |
| Freiform-Gate | Flugrichtung, Rand | Redstone (wie bisher) | – |

Die Seiten stehen als Tabs in der ersten Zeile des Zeilenbereichs, an der Stelle des ersten Abschnittstitels (der Tab
nennt die Seite, das Panel bleibt so hoch wie mit dem früheren Seitenknopf): „Gate“ bzw. der Name des Kurs-Elements,
„Redstone“ oder „Wertung“, „Position“. Mit freier Maus sind sie Knöpfe, ohne freie Maus wechselt Tab zur nächsten und
Umschalt + Tab zur vorigen Seite (die Taste geht dann nicht an die Spielerliste); die Fußzeile nennt es
(„Esc: abbrechen · Tab: nächster Tab“). Die Seite Position erklärt unter den Zeilen Schrittweiten, Pfeiltasten nach
Blick und Drehen (erster Tab oder `R`), wie die Seite Redstone ihren Zielblock nennt. Verworfen: eine eigene Tab-Zeile
über dem ersten Titel – 16 GUI-Pixel mehr hätten das Gate-Menü bei 480 × 270 in die gedrängte Darstellung gezwungen.
Schleichen + Mausrad läuft wie bisher über die Zeilen aller Seiten. „Zurücksetzen“ gilt für die angezeigte Seite.

---

## 4. Teil B – Architektur (Strang `race-b`)

### 4.B1 Ein Gate mehrmals im Kurs

- Ein Kurs ist eine Liste von **Durchgängen**. Jeder Durchgang nennt ein Gate (UUID) und eine Richtung (`reverse`); dieselbe
  UUID darf mehrmals vorkommen. Die Rundenzählung prüft je Durchgang die Geometrie des Gates, bei `reverse` mit
  umgedrehter Normale (bei Elementen: gespiegelte Seite bzw. Ausgang gegenüber), Prüfpunkte werden wie heute je Durchgang
  und Stufe gezählt.
- Kurs-Werkzeug: Klick auf ein Gate, das schon im Kurs ist, hängt einen weiteren Durchgang an statt „schon im Kurs“; die
  Richtung folgt der Seite, von der der Spieler klickt (von hinten = Gegenrichtung). Entfernen und Umsortieren im
  Kurs-Fenster je Durchgang.
- Anzeige: Das Entity trägt alle Nummern (synchronisierter Text, z. B. „2 · 5↺“); Tafel, Namensschild, HUD („Gate 5/8“)
  und Kurs-Fenster zeigen den Durchgang eindeutig. Bodenpfeile je Richtung.
- Server und Client rechnen dieselben Durchgänge aus denselben Kursdaten (Netz: Merkerbit `reverse` je Eintrag).

### 4.B2 Gates kennen ihre Nachbarn

- **Stapeln auf Gates und Würfeln:** Flagge, Slalomstange und Gate-Stange sitzen auf der Oberkante stehender Gates
  (Mitte des oberen Balkens oder Ecke, wie die Sitzplätze der Gate-Stange) und auf Kanten und Ecken des Tauchwürfels.
  Die Wertung reicht über `below` (Teil A) nach unten durch das Gate hindurch, wenn der Kurs das will. Träger mit Aufsatz
  sind gesperrt wie heute bei Gate-Stangen.
- **Reihen ohne doppelte Pfosten:** Ein stehendes Gate direkt neben einem gleich hohen, gleich gedrehten Gate rastet auf
  dessen Pfosten ein (Pfostenmitte auf Pfostenmitte). Rein geometrisch wie das Stapeln, ohne gespeicherte Verbindung: Das
  rechte Gate zeichnet und kollidiert ohne eigenen linken Pfosten, die Öffnung reicht bis an den geteilten Pfosten.
- **Würfel aus Gates:** Vier stehende Gates, die sich an den Ecken einen Pfosten teilen, und ein liegendes Gate obenauf
  bilden einen Würfel. Jedes Gate bleibt ein eigenes Kurs-Gate (oben abwärts, Seite als Ausgang); der Tauchwürfel bleibt
  als fertiges Teil.
- **Platzierung und Kollision:** geteilte Teile kollidieren einmal; wird ein Nachbar abgebaut, bekommt das Gate seinen
  Pfosten bzw. Balken zurück (Rolle wird wie beim Stapeln alle 5 Ticks aus den Positionen nachgerechnet).

### 4.B3 Umsetzung (Strang `race-b`, was sich genauer ergeben hat)

- **Gegenrichtung** ist für jede Art dieselbe Regel: `GateGeometry#withReverse` dreht den Flugsinn um 180° um die
  senkrechte Achse durch den Ursprung (Seite und Normale gedreht, Oben bleibt). Der Rahmen eines Gates ist symmetrisch und
  bleibt daher gleich; Slalomstange, Flagge und wertende Gate-Stange werten wie um 180° gedreht (gespiegelte Seite in der
  Welt), der Tauchwürfel nimmt den gegenüberliegenden Ausgang, das Freiform-Gate die andere Seite. Der Kurs speichert nur
  das Merkerbit, die Geometrie entsteht beim Lesen (`CourseData.Gate#geometry`).
- **Kurs-Werkzeug:** Die Richtung eines weiteren Durchgangs folgt dem Blick beim Klick (`CourseOrder#repeatSide`:
  Blick gegen die Flugrichtung = rückwärts; beim Tauchwürfel zählt der Ausgang). Gehaltene Taste: derselbe Spieler am
  selben Gate innerhalb von 10 Ticks zählt einmal (`CourseToolItem.REPEAT_GUARD_TICKS`), damit kein Durchgang aus Versehen
  doppelt entsteht. Der erste Klick hängt wie bisher vorwärts an.
- **Kurs-Fenster:** Knopf ⇄ (`CourseEdits.Action.REVERSE`, am Ende der Aufzählung, Paket unverändert); × entfernt nur den
  Durchgang (`CourseRegistry#detachGate`), das Gate bleibt im Kurs, solange es weitere Durchgänge hat.
- **Zeichen für rückwärts** ist „←“ (z. B. „1 · 3←“): „↺“ fehlt in der Bitmap-Schrift von Minecraft und erschien nur
  winzig aus der Ersatzschrift.
- **Entity:** `GateEntity` hält `GateIndex` (kleinster Durchgang, wie bisher) und den synchronisierten Text
  `DATA_PASSES` („1,4r“; leer = genau ein Durchgang vorwärts, also wie bisher). NBT `Passes` nur, wenn der Text nicht leer
  ist. Tafel, Namensschild, grünes Licht, aktive Wertungsfläche und Beine (`ClientGateOpenings`) fragen `hasPass`.
- **Rundenzählung beim Client:** Durchgänge desselben Gates sind Zwillinge (`LapTracker#twins`, gleiche Geometrie ohne
  Richtung). Kreuzt ein Segment Zwillinge im selben Augenblick, zählt nur einer (der erwartete, sonst der erste), und eine
  falsche Richtung am erwarteten Durchgang verdrängt das „ausgelassen“ des gleichen Gates.
- **Prüfung am Server:** `RaceServer` wertet je Gate jeden Durchgang (mit `withReverse`) und gibt allen Treffern eines
  Gates im selben Augenblick dieselbe Ereignisnummer; `LapVerifier` lässt zwei aufeinanderfolgende Prüfpunkte nie aus
  demselben Durchflug kommen. So braucht A → B → A → A (Runde) wirklich vier Durchflüge.
- **Geteilte Pfosten:** stehend, weder geneigt noch gerollt (Gieren in jedem Schritt erlaubt), gleiche Höhe, gleiche
  Fußhöhe, gleiche Rahmenstärke und Tiefe; in einer Reihe gleich oder um 180° gedreht (Seitenachsen parallel,
  `GateStacking#parallel`: Kreuzprodukt unter `ALIGN_EPSILON` = 1e-4, also unter 0,006°), über Eck genau quer
  (`perpendicular`, Skalarprodukt unter 1e-4) und nur mit quadratischem Pfosten (Rahmen = Tiefe, bei allen Gate-Größen
  so). Den gemeinsamen Pfosten behält das Gate, dessen Mitte weiter westlich liegt (bei gleichem X weiter nördlich); das
  andere setzt das Bit `JOINT_LEFT`/`JOINT_RIGHT` und zeichnet und kollidiert ohne diesen Pfosten. Einrasten beim Setzen:
  höchstens 1 Block je Achse, nie so, dass der andere Pfosten im Nachbarn steckt.
- **Nur einmal zeichnen (Befund PATTY-4):** Die Teile-Aufteilung steht in `race.client.GateTiles`. Auf einer Seite mit
  `JOINT_LEFT`/`JOINT_RIGHT` zeichnet das Gate kein Teil, auch nicht die Pfosten-Lichter (vorher lagen die Lichtstreifen
  beider Gates deckungsgleich auf dem geteilten Pfosten: Z-Fighting); mit `JOINT_TOP` weder den oberen Balken noch dessen
  Lichter (die trägt der Deckel). Der geteilte Pfosten leuchtet in der Farbe des Gates, das ihn behält. Die
  Ersatzdarstellung ohne Modelle beachtet die Bits ebenso. Verworfen: die Lichtstreifen des Nachbarn nur auf der Seite
  seiner Öffnung zeichnen – die Teile-Modelle tragen alle vier Streifen in einem Stück, ein Schnitt bräuchte neue Modelle.
- **Gedreht (Befund PATTY-4):** Vorher galt Einrasten nur für Gates ohne Feindrehung (`tilt().none()`), ein mit
  45°-Schritt gesetztes Gate rastete nie ein. Jetzt prüft `jointable` nur `!tilted()` (kein Neigen, kein Rollen); die
  Pfostenlage folgt ohnehin der gedrehten Seitenachse. Der Deckel übernimmt die Feindrehung des angeklickten Gates und
  wird so verschoben, dass seine Unterkante wie bisher auf dem Balken liegt (Drehpunkt liegender Gates ist ihre Mitte).
- **Deckel:** liegendes Gate (`FLOOR_FLAT`) auf ein stehendes Gate mit quer stehendem Nachbarn geklickt
  (Lage-Vorliebe „liegend“). Größe: Breite des angeklickten Gates × Breite des Nachbarn (eigene Öffnung, falls keine
  Standardgröße). Die Seiten, deren oberer Balken unter einem Deckelbalken liegt, setzen `JOINT_TOP`: ohne oberen Balken,
  Pfosten bis Höhe − Rahmen. Prüfung beim Setzen lässt nur Überlappungen mit Teilen zu, die genau an der Deckeloberkante
  enden.
- **Plätze zum Aufsetzen:** stehendes Gate: zwei Ecken und die Balkenmitte; Tauchwürfel: vier Ecken und vier
  Kantenmitten; stehende Gate-Stange: oben. Flagge, Slalomstange und Gate-Stange setzen sich darauf
  (`GatePoles#seated`), Träger tragen `carrying` und drehen nur um 180°; verbundene Gates (`linked`) drehen auf der Stelle
  um 180° (`GateMounting#turnedInPlace`), Lage und Maße bleiben gesperrt.
- **Abweichung vom Dateibesitz:** Teil B braucht neben der Besitzliste die Dateien `GateGeometry`, `GateEntity`,
  `GateItem`, `CourseElementItem`, `GatePoleItem`, `GatePoles`, `GatePoleLinks`, `CourseRegistry`, `LapTracker`,
  `LapVerifier`, `RaceServer` und im Client `GateRenderer`, `GateRenderState`, `CourseElementRenderer`,
  `ScoreAreaRenderer`, `ClientGateOpenings`, `CourseEditScreen`, `GatePreview` (Strang `race-a` war gemergt, kein anderer
  Strang hat sie offen).
- **Nachtrag (Befund PTP-1, Playtest rc.1):** Ein Stapel mit einem breiteren Gate auf einem schmaleren entsteht nie.
  `GateStacking#restsOn` verlangt zusätzlich `fits` (oben höchstens so breit wie unten); die reine Sitzhöhe heißt
  `seatedOn`. Ein breiteres Gate in Sitzhöhe ist damit kein Teil des Stapels und steht auf eigenen Beinen (`BAR`), auch
  in Welten aus rc.1 (nichts gespeichert, alle 5 Ticks nachgerechnet). Jede Änderung prüft den **neuen** Zustand:
  `GateStacking#refusal(gate, andere)` meldet `TOO_WIDE`, wenn das Gate auf einem schmaleren säße oder ein breiteres
  trüge, und `TOO_HIGH` über vier Gates; `GateMounting#stackRefusal(level, gate, ohne)` sammelt dazu die stapelbaren
  Gates der Säule (größte Öffnung 16 plus 2 je Gate, also `MAX_STACK × 18` = 72 Blöcke nach oben und unten). Geprüft wird in
  `GateMounting#apply` (Einstellen und Drehen, Ergebnis `STACK_TOO_WIDE`/`STACK_TOO_HIGH`), im Gate-Menü beim Client
  (`GateEditor.Status.TOO_WIDE`/`TOO_HIGH`), in `GateItem#useOn` und in der Vorschau `GatePreview`; Meldungen sind die
  vorhandenen `propwash.race.gate.stack_too_wide`/`…stack_too_high`. Die Sperre gestapelter Gates (§3.A6) bleibt und gilt
  für jedes Gate eines Stapels gleich. „Kurs-Elemente frei platzieren“ erlaubt weiter Überlappung, aber keinen
  ungültigen Stapel. Verworfen: gestapelte Gates zum Einstellen freigeben und die Gates darüber mitführen – ändert das
  Bedienkonzept „erst den Stapel abbauen“ und braucht eine Entscheidung. Außerhalb der Besitzliste (für genau diesen
  Befund): `GateItem`, `GateEditor`, `GatePreview` (Strang `race-a`, gemergt).
- **Nachtrag (Befund RC3P-3, Owner 04.11., rc.3): gestapelte Gates drehen.** Ersetzt für das Drehen die Sperre aus
  PTP-1 („gestapelte Gates zum Einstellen freigeben … braucht eine Entscheidung“ – die Entscheidung liegt jetzt vor).
  - *Was:* Das obere Gate dreht um die senkrechte Achse durch seine Mitte, die zugleich die Mitte der Auflage auf dem
    unteren Gate ist, in denselben Schritten wie Boden-Gates (`GatePlacement#validStep`, `turnedTo`: Vierteldrehung plus
    Feindrehung in `GateTilt.yaw`). Beim Stapeln gilt die Boden-Drehung der Platzier-Vorliebe relativ zum unteren Gate
    (`GateStacking#placementTurn`, `stackOn(top, size, yaw, turn)`; ohne Vorliebe wie bisher gleich oder 180°), danach
    die Dreh-Taste (`GateMounting#rotate`, Schritt aus der Einstellung) und die Zeile „Gieren“ im Gate-Menü
    (`GateMounting#edit`, `headingOnly`). Alles andere bleibt im Stapel gesperrt (`STACK_LOCKED`, Text
    `propwash.race.mount.stack_locked` und `…edit.status.stacked` angepasst).
  - *Auflage:* `GateStacking#aligned` (Kurs gleich oder um 180°, Toleranz 1° wie bisher) behält den gemeinsamen Balken
    (`seat(lower)`, Fuß `STACKED`). Jede andere Drehung (`crossed`) legt das obere Gate mit **eigenem unterem Balken**
    obenauf: `seat(upper, lower) = lower.y + lower.height`, Fuß `MOUNTED` (Balken, keine Füße), `Base.of(supported,
    covered, crossed)`. So schwebt nichts (der eigene Balken kreuzt den Querbalken in dessen Mitte), und Beine und Balken
    überschneiden sich nie (alles liegt über der Oberkante des unteren Gates). Das untere Gate bleibt `LEGS`.
    `GateEntity#gateAbove` sucht dafür von der Sitzhöhe bis zur Oberkante.
  - *Passt drauf:* unverändert „oben höchstens so breit wie unten“ (`fits`), unabhängig von der Drehung; `TOO_HIGH`
    zählt gedrehte Gates mit. `sameColumn` verlangt nur noch dieselbe Mitte, die Ausrichtung entscheidet die Sitzhöhe.
  - *Mitdrehen:* `GateStacking#turned(column, index, heading)` dreht das Gate und alle darüber um denselben Winkel, setzt
    das gedrehte Gate auf seinen Sitz auf dem Gate darunter und verschiebt die darüber um dieselbe Höhe (relative Drehung
    und Auflagen bleiben). `GateMounting#columnTurn`/`turnRefusal`/`applyTurn` prüfen jedes bewegte Gate (Welt, Stapel,
    Platz ohne die bewegten Gates: `GateItem#freeSkipping`, `GateCollisions#collectSkipping`, `GateMounting#columnSkipping`;
    verbundene Gates mit ihren geteilten Pfosten), Zugriff und Kursbesitz für jedes Gate, und melden Kursänderungen je
    Gate (`gateRotated`). Mit Aufsatz (Flagge, Stange, Gate-Stange) oder geteiltem Pfosten im bewegten Teil nur 180°
    (`STACK_TURN_LOCKED`, `propwash.race.stack.turn_locked`); die Dreh-Taste nimmt dann 180°, bei verbundenen Gates wie
    bisher bei jedem Schritt.
  - *Abbauen:* `GateStacking#dropDelta`: Das erste Gate darüber fällt auf seinen Sitz auf dem Gate unter dem abgebauten
    (oder auf dessen Fußhöhe), alle darüber um dieselbe Höhe; bei gleich ausgerichteten Stapeln wie bisher.
  - *Gate-Menü:* Vorschau des gedrehten Gates mit seinem neuen Fuß und der Gates darüber als Geister
    (`GateEditor#turn`, `GateRenderer#submitEdited` mit Fuß); Status wie am Server (`TOO_WIDE`, `TOO_HIGH`, `BLOCKED`,
    `TURN_LOCKED`).
  - *Unverändert:* Kursformat, Netz, Layout-Hash alter Kurse (die Drehung steht wie bei Boden-Gates in `turn`), Wertung
    (gedreht aufliegendes Gate zählt ab seinem Balken, unten auf Beinen bis zum Boden), Gate-Hinweis (jedes neue Gate).
  - *Verworfen:* gedrehtes Gate auf dem gemeinsamen Balken ohne eigenen Balken (Beine stünden neben dem Querbalken in der
    Luft oder steckten in ihm); eigener Balken in Höhe des gemeinsamen (Balken durchdringen sich, Z-Fighting); Breite
    nach der Projektion auf das untere Gate (ein gleich breites Gate wäre bei 5° bis 15° je nach Größe „zu breit“).
    Der zuerst offene Vorschau-Geist beim Stapeln ist umgesetzt (nächster Nachtrag, RC3P3-1).
  - *Außerhalb der Besitzliste* (für genau diesen Befund, Stränge gemergt): `GateEntity` (Rolle `crossed`, Suche nach
    oben, Nachrutschen), `GateItem` (Stapeln mit Drehung, `freeSkipping`), `GateCollisions` (`…Skipping`),
    `client/GateEditor`, `client/GateRenderer` (Vorschau), Sprachdatei `lang/race` (zwei geänderte Texte), Selbsttests
    `GateMountSelfTests`, `GateFreeSelfTests` (Erwartung „nur 180°“ bzw. „gesperrt“ durch das neue Verhalten ersetzt),
    `GateStackSelfTests` (Beitrittsprobe), `dev/StackGateJoinCheck` und `dev/JoinTestEnv` (Schalterwert 2).
- **Nachtrag (Befund RC3P3-1, Entscheidung Steuerung 04.11.): Vorschau-Geist beim Stapeln – umgesetzt.** Gestapelte
  Gates werden wie einzelne Gates gedreht: vorher sehen, mit der Taste drehen, setzen.
  - *Geist:* Mit einem Gate-Item (nicht Kurs-Element, nicht Freiform-Gate) zeigt der Blick auf ein Gate denselben Geist
    wie am Boden (`GatePreview`, gleicher Renderer, Pfeil, rot ohne Platz). Lage, Fuß und Ablehnung kommen aus
    `GateItem#stackFor` (`GateStacking.Stack`, `StackRefusal`), das auch der Rechtsklick nutzt; der Geist ist also genau
    das Gate, das der Klick setzt (Drehung zum unteren Gate aus `placementTurn`, `stackYaw` nach Blickrichtung, Fuß
    `STACKED` oder `MOUNTED`). Rot bei `OCCUPIED`, `TOO_WIDE`, `TOO_HIGH`, `BLOCKED`; bei `STANDING_ONLY` (Wand, Decke,
    liegend, geneigt, Kurs-Element) kein Geist. Lage liegend auf einem stehenden Gate bleibt der Deckel
    (`GateItem#lidPath`, Geist wie bisher, ohne Ring keiner).
  - *Taste:* Gate-Item in der Hand und Blick auf ein stehendes Gate (`GateMountKeys#stacking`) → `GateKeyRoute`
    `TURN_STACK` dreht die Vorliebe der Fläche Boden in den Schritten der Einstellung, Schleichen `NEXT_MOUNT_STACK`
    wechselt ihre Lage (stehend ↔ liegend = Deckel), beides wie am Boden (`stackPreference` = `turnedBy`/`nextMount`).
    Ohne Gate-Item (Kurs-Werkzeug) und bei Gates ohne Stapel dreht die Taste wie bisher das Gate im Blick. Entschieden
    nach Hand und Gate, nicht nach sichtbarem Geist: Sonst schaltete die Taste beim Wechsel auf liegend ohne Ring (kein
    Geist) unbemerkt auf das Gate im Blick um.
  - *Hinweisfeld:* wie beim Setzen am Boden (`GatePreview#status`, `#keys`): Lage und Drehung der Fläche Boden, darunter
    „Taste: drehen · Schleichen + Taste: Lage wechseln“.
  - *Texte:* `propwash.help.gate.use.4` und `propwash.race.mount.need_tool` nennen für platzierte Gates das
    Kurs-Werkzeug (mit Gate-Item dreht die Taste am stehenden Gate jetzt die Vorschau).
  - *Unverändert:* Klick, Server, Netz, Kursformat; Flaggen, Slalom- und Gate-Stangen auf Gates (eigene Vorschau, die
    Taste dreht dort wie bisher das Gate darunter; nicht Teil dieses Befunds).
  - *Tests:* JUnit `GateStackPreviewTest`, `GateKeyRouteTest`; Selbsttest `race-topo: Stapel-Vorschau`
    (`GateStackPreviewSelfTests`), Bild `race_stack_preview`.
  - *Außerhalb der Besitzliste* (für genau diesen Befund, Strang `race-a` gemergt): `GateItem`, `client/GatePreview`,
    `client/GateKeyRoute`, Sprachdatei `lang/race` (zwei geänderte Texte), Selbsttest-Anmeldung in `RaceSelfTests`.
- **Nachtrag (Befund AUTO56-1): Dreh-Taste am Deckel – umgesetzt.** Mit Lage liegend und Blick auf eine Seite eines
  Gate-Rings (`GateItem#lidPath`) bleibt der Tasten-Zweig `TURN_STACK` (entschieden nach Hand und Gate, wie oben), aber
  `GateKeyRoute#stackPreference` ändert die Vorliebe nur, wenn ihre Boden-Lage stapelbar ist (`GateMount#stackable`, also
  stehend); liegend bleibt sie unverändert. Grund: Der Deckel liegt immer `FLOOR_FLAT` ohne `flipped` (`GateStacking#lid`,
  `GateItem#lidFor` liest keine Vorliebe), vorher schaltete die Taste die Stellung auf 1 und das Hinweisfeld zeigte
  „aufwärts“, während Geist und gesetzter Deckel abwärts blieben. Das Hinweisfeld nimmt am Deckel die Richtung aus der
  Deckel-Geometrie (`GatePreview.Target#lid`, `GateMounting#describeDirection`), die Tasten-Zeile nennt nur den
  Lagewechsel (`propwash.race.lid.hint`, Sprachdatei `lang/race-b`). Unverändert: Klick, Server, Netz, Deckel-Geometrie,
  Schleichen + Taste (liegend → stehend). Tests: JUnit `GateStackPreviewTest`, Selbsttest `race-topo: Stapel-Vorschau –
  Deckel: …` (vier Schritte). Außerhalb der Besitzliste (Strang `race-a` gemergt): `client/GatePreview`,
  `client/GateKeyRoute`.
- **Nachtrag (Befund OT4-4, Owner rc.4.1): gestapelte Gates nebeneinander – umgesetzt.** Ohne „Kurs-Elemente frei
  platzieren“ kam beim zweiten Stapel direkt neben einem anderen „kein Platz“: Ein gestapeltes Gate entstand immer mit
  vollem Rahmen (`GateStacking#stackFor`, ohne Nachbarn), sein Pfosten steckte im Pfosten des gleich hohen Nachbarn, und
  die Platzprüfung (`GateItem#free`) lehnte ab. Am Boden gab `GateItem#snapped` dem neuen Gate dagegen schon das Bit des
  geteilten Pfostens.
  - *Was:* `GateStacking#joined(gestapelt, Nachbarn)` setzt beim Stapeln die Bits `JOINT_LEFT`/`JOINT_RIGHT` für jede Seite,
    deren Pfostenmitte genau auf der eines verträglichen Nachbarn liegt (`placementJoints`, Regeln wie am Boden: gleiche
    Höhe, Fußhöhe, Rahmen und Tiefe, parallel oder über Eck mit quadratischem Pfosten). Kein Einrasten: Die Lage gibt die
    Säule vor. `GateItem#stackFor` prüft Platz mit diesem Rahmen, Vorschau und Klick nutzen denselben Weg. Nach dem Setzen
    rechnet `GateItem#refreshAround` die Nachbarn sofort nach (wem der Pfosten gehört, entscheidet wie bisher
    `GateStacking#joints`: das westliche bzw. nördliche Gate), Meldung `propwash.race.row.joined` wie am Boden.
  - *Fünftes Gate:* Die Vorschau sagte bei jeder Ablehnung „kein Platz“. `GatePreview.Target` trägt jetzt die
    `StackRefusal`; das Hinweisfeld nennt `TOO_HIGH` („höchstens 4 Gates übereinander“), `TOO_WIDE` und `OCCUPIED` mit
    eigenem Text (`propwash.race.stack.preview_too_high`, `…_too_wide`, `…_occupied`, Sprachdatei `lang/race-b`).
    `BLOCKED` (Block, Kurs-Element, nicht verträglicher Nachbar) bleibt „kein Platz“, ohne Baurecht bleibt „hier darfst
    du nicht bauen“. Der Klick meldete schon `propwash.race.gate.stack_too_high`.
  - *Geprüft:* Kurs-Werkzeug trifft beim Klick auf einen Rahmen das Gate, dem der Rahmen gehört; Öffnungen und
    Wertungsflächen ändern sich durch geteilte Pfosten nicht (die Öffnung endet wie bisher am Pfosten), Rundenzählung beim
    Client (`LapTracker`) und am Server (`RaceServer`) zählt jedes Gate eines 2×2 einzeln.
  - *Unverändert:* Netzprotokoll, Kursformat, NBT, Layout-Prüfsumme (Bits stehen nicht in den Kursdaten), Platzierung am
    Boden, Deckel, gedrehte Stapel.
  - *Verworfen:* gestapelte Gates einrasten lassen wie am Boden (die Säule bestimmt die Lage, ein Versatz trennte das Gate
    vom Stapel); die Platzprüfung beim Stapeln für Nachbarn abschalten (echte Kollisionen müssen „kein Platz“ bleiben).
  - *Tests:* JUnit `GateStackRowTest` (2×2 in vier Bau-Reihenfolgen, alle Höhenpaare 1–4 in beide Richtungen, drei Stapel
    in einer Reihe, Ecke für alle Gate-Größen, echte Kollisionen und Grenze 4, Runde durch das 2×2), `GateStackLimitPreviewTest`;
    Selbsttest `race-topo: Stapel nebeneinander` (`GateStackRowSelfTests`) mit Bildern `race_topo_stack_2x2`,
    `race_topo_row_3x1`, `race_topo_stack_heights`, `race_topo_stack_corner`, `race_topo_stack_limit` (deutsch `…_de`).
  - *Außerhalb der Besitzliste* (für genau diesen Befund, Strang `race-a` gemergt): `GateItem` (`stackFor`, `stackOn`),
    `client/GatePreview` (`Target#refusal`, `status`), Selbsttest-Anmeldung in `RaceSelfTests`.

---

## 5. Datenformat und Migration

### 5.1 Kursdatei `data/propwash_race.json`

- Neues Feld `format` (Zahl) auf oberster Ebene. Fehlt es, ist es Format 1 (0.4.0 bis 0.5.2). 0.6.0 schreibt Format 2.
  Format 2 ist eine reine Erweiterung: Ein Leser von Format 1 überspringt die neuen Felder (Gson), alte Felder behalten
  Namen und Bedeutung.
- Teil A: `GateRecord.score` (Objekt, nur wenn vom Standard der Art abweichend): `role` (`"pass"`/`"turn"`), `side`
  (`"left"`/`"right"`), `reach`, `below`, `above` – jeweils nur die Felder der Art (`ScoreArea.forCourse`).
  Gate-Stangen stehen mit `kind = "gate_pole"`, `variant` = Farbe und `mount` wie andere Elemente im Kurs.
- Teil B: `GateRecord.reverse` (nur wenn `true`); dieselbe `uuid` darf mehrfach vorkommen. Format-1-Kurse lesen sich als
  Durchgänge vorwärts, je UUID einmal.
- Laden repariert wie bisher (ungültige Einträge fallen weg). Unbekannte Formatversionen > 2 laden mit Warnung im Log
  so weit wie möglich und werden beim Speichern als 2 geschrieben.

### 5.2 Entity-NBT (`GateEntity`)

Neue Schlüssel nur bei Abweichung vom Standard der Art: `ScoreRole`, `ScoreSide`, `ScoreReach`, `ScoreBelow`,
`ScoreAbove`, `OpenFrame`. Fehlen sie, gilt der Standard (alte Welten laden unverändert). Synchronisiert über ein
`int`-Datenfeld `DATA_SCORE` (`ScoreArea#pack`).

### 5.3 Netz

- Protokoll bleibt **17** (Basis-Commit von 0.6.0, `docs/design/0.6.0/README.md`).
- `CourseData.Gate` bekommt `score` mit Merkerbit 16 (`VarInt` der gepackten Wertung, nur bei Abweichung) und
  `reverse` mit Merkerbit 32 (ohne weitere Bytes).
- `GateEditC2S` trägt zusätzlich die gepackte Wertung und den Versatz (drei `float`); die Variante wird mit 4 Bit gelesen
  (vorher 2 Bit – Farben der Gate-Stange über 3 gingen im Menü verloren).
- `FreeformGateItem` liest „Lücken ignorieren“ aus Schleichen beim Benutzen (kein neues Paket).

### 5.4 Layout-Prüfsumme und Bestenlisten

`CourseData#layoutHash` nimmt die Wertung nur auf, wenn sie vom Standard abweicht, und `reverse` nur, wenn gesetzt.
Kurse aus 0.4.0, 0.5.0 und 0.5.2 behalten damit Prüfsumme, Bestenliste, Rekord-Geister und persönliche Geister. Eine
geänderte Wertung oder ein verschobenes Gate ist eine Layout-Änderung (Archiv wie bei jeder Änderung).

Nachtrag (Befund AUTO30-1): Die Ausrichtung liegender Freiform-Gates (Befund PATTY-3) bestimmt nur den Redstone-Ausgang;
Öffnung, Durchflugfläche und Flugrichtung hängen nicht daran. Sie zählt deshalb wie die Einstellungen der Seite Redstone
nicht zum Layout: `CourseData#yawShapesLayout` ist für sie falsch, `layoutHash` nimmt dort 0 statt der Drehung (alte
liegende Freiform-Gates stehen auf 0°, ihre Prüfsumme bleibt byte-gleich), und `CourseRegistry#gateRotated` übernimmt eine
reine Drehung über `reconcile` in die Kursdaten (`GateRecord#yaw`), ohne Bestenliste und Rekord-Geister zu archivieren.
Umkehren der Flugrichtung, Neu einlesen und jede andere Änderung bleiben Layout-Änderungen. Verworfen: nur im Tooltip
auf das Archivieren hinweisen – eine Änderung ohne Einfluss auf den Flug soll keine Zeiten kosten.

### 5.5 Migration – Prüfung

- JUnit `race.RaceFormatMigrationTest`: Kursdateien in der Form von 0.4.0 (ohne `mount`), 0.5.0 (Elemente, Freiform),
  0.5.2 (Gate-Stangen nicht im Kurs) lesen, Prüfsummen unverändert, Speichern schreibt `format: 2` und keine neuen Felder
  bei Standardwerten, Lesen eines Format-2-Kurses mit Wertung und wieder Schreiben ist verlustfrei.
- Selbsttest `migration:` mit den Testwelten aus `world-fixtures` (0.5.2, 0.5.0, 0.4.0): Kurse, Bestenlisten, Archive,
  Rekord-Geister und Prüfsummen wie in der Erwartung.

---

## 6. UI und Texte

- Neue Texte nur in `common/src/lang/race-a/` (Teil A) bzw. `race-b/` (Teil B), Englisch und Deutsch.
- Einstell-Menü: Zeilen und Tooltips je neuer Zeile, Seitenknöpfe, Status „Verschoben um …“, Meldungen des Servers
  („Gate-Stange im Kurs: Rolle „keine“ geht erst nach „Aus dem Kurs““, „Zu weit verschoben – höchstens 8 Blöcke je Achse“).
- Kurs-Werkzeug: „Gate-Stange #3 in Kurs 2 · Slalom: links vorbei“, „Gate-Stangen an der Wand werten nicht“.
- Freiform: Hinweis in der Vorschau, Zeile „Rand“, Ergebnis „Freiform-Gate gesetzt (Lücken ignoriert) …“.
- Tab „Rennen“: Schalter „Wertungsflächen zeigen“ mit Tooltip.

## 7. Tests

| Ebene | Teil A | Teil B |
|---|---|---|
| JUnit | `ArmEdgeTest`, `StandingStartTest` (Reset scharf, Sitzungsbeginn scharf, echte Flanke, Einstellung, Aufstellung offen); `ScoreAreaTest` (Packen, Normalisieren, Bereiche); `ScoreGeometryTest` (Halbebenen nach unten/oben, bitgleich mit Standard, Gate-Stange vorbei/umrunden, Wandstange ohne Stufen, Prüfpunkte, Runde mit `LapTracker` und `LapVerifier`, Geister-Splits); `FreeformLenientTest` (U-Form, umgedrehtes U, L-Form, zwei Pfosten, ein Pfosten abgelehnt, Grenze); `GateNudgeTest` (Versatz, Grenzen, Raster); `RaceFormatMigrationTest`; `CourseElementDataTest` erweitert (Netz mit Wertung) | Kursreihenfolge mit Wiederholungen und Richtungen, Nachbarschaft/Stapeln, Migration |
| Selbsttest | Gruppe `race:` (`race.client.RaceScoringSelfTests`): jede Funktion im Spiel, Bildschirmfotos der Flächen (FPV und dritte Person) und des Menüs | Gruppe `race-topo:` mit Bildschirmfotos (Schleife, Würfel, Flagge auf Gate, Reihe) |
| Beitrittstest | `PROPWASH_JOINTEST_RACEHOME` (`dev.RaceHomeJoinCheck`, `race.client.RaceHomeJoinProbe`): Reset scharf am Start startet keinen Countdown, Scharfschalten danach ohne Einstellung auch nicht, mit Einstellung schon (Aufstellung am dedizierten Server), auf Fabric und NeoForge | Schleifen-Rennen mit zwei Spielern |
| Server-Rauchtest | `runServerSmoke` beider Loader | ebenso |

## 8. Entscheidungen

| Nr. | Was | Warum | Verworfen |
|---|---|---|---|
| R1 | Reset merkt keinen stehenden Start mehr vor | Vorgabe 19.10.; der automatische Start kam aus Vormerken plus Scheinflanke | Vormerken behalten und nur die Scheinflanke beheben (Start nach Reset bliebe ohne Einstellung möglich, widerspricht der Vorgabe) |
| R2 | Flanke nur nach gesehenem „entschärft“ in derselben Sitzung (`ArmEdge`) | deckt die Scheinflanke nach dem Reset, Sitzungsbeginn mit fliegender Drohne und Drohnenwechsel in einer Regel ab, alles im Paket `race` | Telemetrie in `PilotSession#onReset` auffrischen (fremder Besitz, deckt Sitzungsbeginn nicht ab) |
| R3 | Wertung als eigener Datensatz `ScoreArea` | die Variante der Gate-Stange ist die Farbe (4 Bit voll); ein Datensatz für alle Arten hält Netz, NBT und Kursdatei einfach | weitere Varianten; eigene Element-Art „wertende Stange“ (doppelter Code) |
| R4 | Wertung nach unten und oben, 0–32 Blöcke, Standard 1 und 0 | deckt hohe Flaggen auf Säulen und auf Gates ab, Standard = Verhalten bis 0.5.2 | Verlängern bis zum Boden (Blöcke ändern still die Wertung); absolute Höhen (gehen beim Verschieben kaputt) |
| R5 | Gate-Stange wertet als Slalomstange oder Wendeflagge, nur senkrecht | Rollen, die Spieler schon kennen, gleiche Stufen und Pfeile; liegende Stangen haben kein verständliches „links/rechts“ | eigene Stufenform; Wandstangen werten |
| R6 | Kurs-Werkzeug macht eine Deko-Stange zur Vorbeiflug-Stange mit Slalom-Seite | ein Klick wie bei Slalomstangen; Rolle danach im Menü änderbar | Ablehnen, bis die Rolle im Menü gesetzt ist (ein Schritt mehr, schwer zu finden) |
| R7 | Lücken ignorieren: Rechteck aus den vorhandenen Blöcken, darin Flutfüllung | Torpfosten, Bögen und L-Formen ergeben die Fläche, die man sieht; ein einzelner Pfosten bleibt abgelehnt | freies Rechteck ohne Blöcke (Vorgabe: nicht); Konvexe Hülle (unvorhersehbar bei Höhlen) |
| R8 | Lücken ignorieren beim Setzen mit Schleichen + Rechtsklick, im Menü als Knopf | kein neues Item und kein neues Paket; Schleichen ist am Block frei | eigenes Item „offenes Freiform-Gate“ |
| R9 | Flächen über `SUBMIT_WORLD_GEOMETRY` mit `eyes`, Füllung + Kanten | kein Abschneiden durch Entity-Culling, lesbar bei Nacht und mit Shaderpacks, kein Schatten | im Entity-Renderer; nur Füllung (mit manchen Packs zu blass) |
| R10 | Element-Flächen nur aktiv, nahe oder beim Bauen | dezent im FPV, Slalom-Reihen bleiben ruhig | immer alle Flächen |
| R11 | Verschieben in Weltachsen, Schrittweite 1/16, 1/4, 1 | Blöcke sind achsparallel; drei Schritte decken fein, mittel und blockweise ab | Gate-Achsen; freie Schrittweite |
| R12 | Pfeiltasten blickbezogen, auf Weltachsen gerastet | man denkt in „nach links/weg“; gerastet bleibt es auf X/Z | feste Zuordnung Pfeil ↔ X/Z (hängt von der Blickrichtung ab, verwirrt) |
| R13 | ±8 Blöcke je Achse und Einstellvorgang | Feinarbeit, kein Teleport fremder Gates; weiter geht es mit erneutem Öffnen | unbegrenzt; nur ±1 Block |
| R14 | Kursdatei mit `format: 2`, neue Felder nur bei Abweichung | alte Welten und Bestenlisten bleiben Byte für Byte gültig, ältere Leser überspringen neue Felder | neue Datei; Migration beim Laden mit Umschreiben aller Kurse |
| R15 | Protokoll bleibt 17 | 0.6.0 ist unveröffentlicht und hat 17 schon im Basis-Commit | eigener Sprung |
| R16 | Durchgänge statt Kopie-Gates (Teil B) | ein Gate, eine Kollision; Schleifen ohne Tricks mit übereinander gesetzten Gates | Kopie-Entity am selben Ort |
| R17 | Nachbarschaft rein geometrisch wie das Stapeln (Teil B) | kein gespeicherter Verbund, Abbauen und Nachrechnen wie bekannt | gespeicherte Verbindungen zwischen Gates |

## 9. Dateibesitz

- `race-a`: Paket `race` (darin `GateEntity`, `GateItem`, `GatePole*`, `CourseElement*`, `FreeformGate*`, `Freeform*`,
  `GateGeometry`, `GateSize`, `GatePlacement`, `GateMounting`, `RacePayloads`, `CourseData`, `CourseRegistry`,
  `CourseToolItem`, `CourseCheckpoints`, `LapTracker`, `GhostTrack`, `RaceServer` für die Stellen oben, neu `ScoreArea`,
  `ArmEdge`, `StandingStart`), `race.client.**`, Selbsttest-Gruppe `race:`, Beitrittstest `racehome`, Texte
  `common/src/lang/race-a/`, Doku `docs/race-system.md`. Außerhalb: Anpassung des Selbsttests `reset:` (A1).
- `race-b`: `GateStacking`, `GateMount*`, `CourseOrder`, `CourseData` (Durchgänge), `CourseCheckpoints`, `CourseLaps`,
  `CourseEdits`, `CourseToolItem` (Reihenfolge/Zuweisung), Gruppe `race-topo:`, Texte `common/src/lang/race-b/`.

## 10. Risiken und offene Punkte

- Shaderpacks, die `eyes` als reine Emission ohne Alpha zeichnen, machen Füllungen kräftiger; die Kanten bleiben dünn.
  Geprüft lokal mit Complementary Reimagined (Fabric, `docs/testing.md` §5); NeoForge-Iris stürzt im Entwicklungslauf ab.
- Verschieben auf Halbblöcke kann Gates aus dem Raster der Platzierung nehmen; Stapeln und Aufsetzen rechnen mit den
  echten Positionen und bleiben richtig, das Aufsetzen auf ein verschobenes Gate folgt dessen Lage.
- Der volle Mehrspieler-Fall mit zwei echten Clients braucht den geschützten Lauf (`mp3-ok`); Teil A prüft den Ablauf mit
  einem Client am dedizierten Server, der Fall mit zwei Clients läuft im Wellenabschluss.

---

## 11. Nachtrag: Renndaten für Server und Bots (Strang `race-data`)

Stand 19.10.2026, Basis `integration` nach `race-b`. Vorgabe des Besitzers (19.10.): Es gibt **kein** Server-Add-on.
Propwash legt die Renndaten selbst klar offen: jeder Rennkurs bekommt eine eindeutige Kennung, an ihr hängen die
Bestenlisten; Server bauen damit eigene Dinge, ein Discord-Bot holt sich die Daten über eine eigene kleine Mod. Wichtig
ist nur, dass die Daten erreichbar sind. Muss in 0.6.0. Doku: [`docs/api/race-data.md`](../../api/race-data.md) (Mods,
JSON-Schema), [`docs/race-data.md`](../../race-data.md) (Spieler und Server-Betreiber).

### 11.1 Entscheidungen

| Nr. | Was | Warum | Verworfen |
|---|---|---|---|
| D1 | Kurs-ID ist eine zufällige UUID (v4), beim Anlegen vergeben, als `uuid` im Kurs in `data/propwash_race.json` | eindeutig über Welten und Server hinweg, ohne zentrale Vergabe; überlebt Umbenennen und Umbau, weil nichts davon sie berührt; eine Welt-Kopie behält sie mit der Datei | aus Nummer und Weltname ableiten (Nummern wiederholen sich, Namen ändern sich); aus Layout-Prüfsumme (ändert sich bei jedem Umbau); v3 aus dem Namen (Umbenennen) |
| D2 | Alte Kurse bekommen beim ersten Laden eine ID, die Kursdatei wird **sofort** gespeichert (`CourseRegistry.start`) | sonst bekäme ein Kurs nach einem Absturz vor dem nächsten Speichern eine andere ID | erst beim regulären Speichern (Absturzfenster); IDs aus der Kursnummer ableiten (Kopien zweier Welten kollidierten) |
| D3 | Fehlende, kaputte, leere (`0…0`) und doppelte IDs werden beim Laden ersetzt; die erste gültige behält ihre | handbearbeitete oder zusammenkopierte Dateien dürfen die Eindeutigkeit nicht brechen | Laden abbrechen |
| D4 | Die Kursnummer bleibt intern und für Befehle; `RaceRecords`, `RecordGhosts` und persönliche Geister bleiben nach Nummer gespeichert, nach außen adressiert die ID (`CourseRegistry#course(UUID)`) | Altwelten bleiben Byte für Byte gültig, Geister-Dateien (`course_<n>_…`) passen weiter | Bestenlisten auf die UUID umschlüsseln (Migration aller Weltdaten und Client-Geister) |
| D5 | Neue ID für jeden neu angelegten Kurs, auch Weltkurse und beim Wiederanlegen aus Gate-Daten (`CourseRegistry#reconcile`); Kopierfunktionen gibt es in 0.6.0 nicht, kommen sie, legen sie über `createCourse` an | ein kopierter Kurs ist ein anderer Kurs mit eigener Bestenliste | ID im Gate-NBT mitführen (Kopien von Gates trügen sie mit) |
| D6 | Formatversion der Kursdatei bleibt 2, `uuid` ist ein zusätzliches Feld | Format 2 ist unveröffentlicht und additiv; ältere Leser überspringen es | Format 3 |
| D7 | Die ID reist im Kurs-Sync (`CourseData.key`, ein Merker-Boolean und 16 Bytes nach dem Besitzernamen); Protokoll bleibt 17 | das Kurs-Fenster zeigt sie ohne neues Paket; 0.6.0 ist unveröffentlicht (R15) | eigenes Anfrage-Paket |
| D8 | Bestenlisten werden nach außen über ID, Kategorie (ohne/mit Flughilfe), Layout, Klasse und Limit abgefragt; archivierte Bestenlisten bleiben als „Layout n“ unter derselben ID abrufbar | entspricht dem, was das Spiel zeigt; ein Bot kann auch alte Layouts nennen | nur das aktuelle Layout |
| D9 | Rennzeiten (Gesamtzeit über n Runden) speichern zusätzlich Name, Klasse und Datum (`Leaderboard.RaceInfo`, optionale Felder `name`, `frame`, `date` im Codec) | „Datum, Rahmen/Klasse“ je Eintrag; ältere Rennzeiten haben sie nicht (`date` 0, `frame` leer, Name aus den Rundenzeiten) | neue Datei für Rennzeiten |
| D10 | API nur lesend, nur am Server-Thread (sonst `IllegalStateException`), Ergebnisse unveränderliche Records | keine Sperren im Renncode; Bots holen sich Daten mit `server.submit`; Zeiten entstehen nur durch echtes Fliegen | thread-sichere Kopien bei jeder Änderung (Last ohne Abnehmer); Schreib-API |
| D11 | Ereignisse als `PropwashEvent` in `api.race.RaceEvents`, Nutzdaten als Records ohne Live-Objekte (Spieler-UUID und Name statt `ServerPlayer`); ohne Listener wird kein Schnappschuss gebaut; Fehler eines Listeners werden protokolliert, die übrigen laufen weiter | keine Last pro Tick; Bots dürfen die Daten an andere Threads geben | Listener mit `ServerPlayer` (taugt nicht für unveränderliche Schnappschüsse) |
| D12 | `COURSE_RECORD` nur für die schnellste Runde ohne Flughilfe im aktuellen Layout; Klassenrekorde stehen als Merker in `LapResult` | „neuer Kursrekord“ wie die Meldung im Spiel; Runden mit Flughilfe werden nie Rekord | eigenes Ereignis je Klasse |
| D13 | JSON-Spiegel unter `<welt>/propwash/race-data/`: `courses.json` und `leaderboards/<kurs-id>.json`, Feld `schema` (1) | ohne Mod lesbar (Skripte, Bots, `jq`); eine Datei je Kurs hält Änderungen klein | eine große Datei; SQLite (Abhängigkeit) |
| D14 | Schreiben gebündelt: 2 s nach der letzten Änderung, spätestens 10 s nach der ersten, plus Start und Stopp; Zeitgeber und Schreiben auf einem eigenen Daemon-Thread, Daten am Server-Thread erstellt (`server.execute`), unveränderte Dateien übersprungen, atomar per `*.tmp` und `ATOMIC_MOVE` | nie im Tick-Takt, kein Code pro Tick; Leser sehen nie halbe Dateien | Schreiben im Tick-Ereignis; Schreiben beim Weltspeichern (bis 5 min alt) |
| D15 | Zonen-Hinweis je Kurs: Zonen derselben Dimension, die einen Elementpunkt (Fuß oder 1 Block darüber) enthalten; Status `ASSIST`, wenn eine davon nicht rennfrei ist und die Physik ändert, sonst `RACE_LEGAL`, ohne Zone `NONE`; Zonen-Änderungen holt eine Prüfung alle 60 s (Versionszähler von `ZoneData`) in den Spiegel | Bots können vorher sagen „Runden hier zählen als Flughilfe“, ohne die Zonenlogik nachzubauen; kein Haken in `ZoneServer` (fremder Besitz) | genaue Prüfung entlang der Ideallinie (teuer, trotzdem nur Hinweis) |
| D16 | Spieler sehen die ID im Kurs-Fenster (Zeile „ID“, Knopf „Kopieren“, für jeden, auch ohne Bearbeitungsrecht) und mit `/propwash course id [Nummer\|Name\|ID]` (ohne Angabe: nächster Kurs; Rechte wie `/propwash race courses`, also keine) | die ID muss für Server-Betreiber und Spieler leicht greifbar sein | nur im Befehl; eigener Rechte-Knoten |

### 11.2 Umsetzung

- **Kern:** `race.CourseId` (Parsen mit und ohne Bindestriche, Vergabe und Reparatur beim Laden), Feld `uuid` und
  `Course#key()` in `CourseRegistry.Course`, `CourseRegistry#course(UUID)`, `CourseRegistry.offline(Path, …)` (Laden einer
  Kursdatei ohne Server, für Werkzeuge und JUnit; ohne Server entfallen Netz, Meldungen und Weltkurs-Tafeln).
- **Schnappschüsse:** `race.export.RaceSnapshots` baut `CourseInfo`, `BoardSnapshot` und `BoardEntry` aus Kurs,
  `RaceRecords` und Zonen; API (`apiimpl.RaceDataImpl`) und JSON (`race.export.RaceDataJson`) nutzen denselben Code.
  Millisekunden runden wie `RaceFormat` (halbe auf).
- **Ereignisse:** `race.export.RaceDataEvents`; `CourseRegistry` meldet Anlegen, Ändern (Layout, Name, Runden,
  Besitzer) und Löschen sowie Bestzeiten und Rekorde beim Eintragen, `RaceServer` meldet „Runde beendet“ (jede bestätigte
  Runde, auch mit Flughilfe, mit `saved`) und „Rennen beendet“ (mit Platz im Mehrspieler-Lauf).
- **Spiegel:** `race.export.RaceDataMirror`, gestartet am Ende von `CourseRegistry.start`, gestoppt in
  `CourseRegistry.stop` (letztes vollständiges Schreiben). Markiert wird bei jedem Kurs-Sync (`broadcast`), beim
  Eintragen und Zurücksetzen von Zeiten und beim Löschen; nur Änderungen der eigenen Registry zählen.
- **Befehl:** `race.CourseIdCommands`, in `server.ServerCommands` nur angehängt.

### 11.3 Tests

| Ebene | Inhalt |
|---|---|
| JUnit `race.data` | `CourseIdTest` (Parsen, v4, Reparatur, Netz mit ID, Prüfsumme ohne ID), `CourseIdMigrationTest` (Formen 0.4.0/0.5.0/0.5.2: IDs beim ersten Laden, gespeichert, danach stabil, Prüfsummen gleich, Welt-Kopie, Doppelte), `CourseIdStabilityTest` (Umbenennen, Runden, Umsortieren, Umdrehen, Entfernen, neue Layout-Nummern, archivierte Layouts unter der ID, neue Kurse neue IDs), `RaceSnapshotsTest`, `RaceEventsTest`, `RaceDataJsonTest` (Vergleichsdateien unter `common/src/test/resources/race-data/`, neu schreiben mit `PROPWASH_RACE_DATA_GOLDEN=1`), `LegacyWorldFixturesTest` (die Testwelten aus `world-fixtures` mit `PROPWASH_FIXTURES=<ordner>`, sonst übersprungen) |
| Selbsttest | Gruppe `race-data:` (`race.client.RaceDataSelfTests`): Kurs anlegen, ID im Kurs-Fenster und Kopieren, Stabilität im Spiel, Befehl mit Nummer, Name und ohne Angabe, zwei Piloten fliegen (API, Ereignisse im Beispiel, JSON-Spiegel), Bildschirmfotos englisch und deutsch |
| Beitrittstest | `PROPWASH_JOINTEST_RACEDATA` (`dev.RaceDataJoinCheck`, `race.client.RaceDataJoinProbe`): zwei Piloten fliegen am dedizierten Server eine Runde und ein Rennen, Ereignisse kommen im Beispiel-Listener an, der JSON-Spiegel enthält die Zeit unter der Kurs-ID, der Client sieht die ID und bekommt sie per Befehl anklickbar |

### 11.4 Abweichung vom Dateibesitz

Außer der Besitzliste brauchte der Strang: `race.Leaderboard` (Name, Klasse und Datum der Rennzeiten, D9),
`race.RaceServer` (zwei Aufrufe für „Runde beendet“ und „Rennen beendet“, Klasse beim Rennen), `race.CourseData` (ID im
Sync, D7), `api.PropwashApi` (eine Methode `race()`), `race.client.CourseEditScreen` (nur die Zeile „ID“). Kein anderer
offener Strang hatte diese Dateien.

---

## 12. Nachtrag: Kurs-Einstellungen in einem Menü (Befund COURSEMENU-1, Strang `race-b`)

Owner 29.10. nach dem Playtest: Kurs-Einstellungen in einem übersichtlichen Menü bündeln, erreichbar aus dem
Kurs-Werkzeug und dem Kurs-Fenster; nur Besitzer und Operatoren ändern, alle anderen sehen es schreibgeschützt; alte
Wege bleiben; Kursformat unverändert. Gestrichen: Gates bis zum Rennstart ausblenden.

| Nr. | Was | Warum | Verworfen |
|---|---|---|---|
| M1 | Eigenes Fenster `race.client.CourseMenuScreen` neben dem Kurs-Fenster | Das Kurs-Fenster ist eine Liste mit eigener Rollleiste; eine zweite Rollleiste drumherum wäre verschachtelt. Die Reihenfolge bleibt im Kurs-Fenster, das Menü verweist darauf („Reihenfolge …“) | Kurs-Fenster zum Menü umbauen (verschachteltes Scrollen, alle Tests des Kurs-Fensters neu); Tabs |
| M2 | Kurs-Werkzeug: Rechtsklick in die Luft öffnet das Menü statt der Bestenliste (Client-Entscheidung beim `open`-Paket) | „erreichbar aus dem Kurs-Werkzeug“ ohne neues Paket; die Bestenliste ist einen Klick weiter, ältere Server öffnen so auch das Menü | Schleichen + Rechtsklick (belegt: Kurs beenden); neues Paket |
| M3 | Gruppen Kurs · Besitz und Rechte · Bestenliste und Geister · Löschen als Logik in `race.CourseMenu` | per JUnit prüfbar, eine Stelle für Reihenfolge und Rechte je Zeile | Gruppen nur im Bildschirm-Code |
| M4 | „Alle Zeiten löschen“ schickt den vorhandenen Befehl `/propwash race reset <kurs>` | gleiche Rechteprüfung (Stufe 2) und Meldung wie bisher, kein neues `CourseEdits.Action` (eine unbekannte Aktion fiele bei älteren Servern auf `RENAME` zurück) | neue Aktion im Kurs-Paket |
| M5 | Schreibschutz im Client in jedem Bedienelement und in jeder Methode, der Server prüft wie bisher | Nicht-Besitzer können nichts auslösen, auch nicht über Tastatur oder Tests | nur ausgegraut |
| M6 | Zahl der Zeiten im Menü aus `LeaderboardS2C#times` (Befund AUTO31-2): der Server schickt `Leaderboard#size` plus, wenn die Welt-Regel Runden mit Flughilfe speichert, `assistedSize` als `VarInt` nach `access`; `CourseMenu.times` liest sie | Das Paket trägt nur die besten zehn je Klasse und die eigenen Zeilen, gezählte Zeilen ergaben bei 15 Zeiten „10 Zeiten“; Protokoll bleibt 17 (R15, 0.6.0 unveröffentlicht); ältere Konstruktoren setzen die Zahl auf die Zeilenzahl | alle Zeilen schicken (bis 50 je Klasse und Paket bei jeder Runde größer); eigenes Paket; Text „Top 10“ |

Dateien außerhalb der Besitzliste von `race-b` (für genau diesen Befund): `race.client.CourseMenuScreen` (neu),
`race.client.ClientLeaderboards` (Öffnen), `race.client.CourseEditScreen` (Knopf „Einstellungen“), `race.CourseMenu`
(neu), Texte `lang/race/` (Tooltip des Kurs-Werkzeugs und der Bestenliste im Tab Rennen), Spieler-Doku der Stränge
`race-data`, `race-p2p`, `race-ghosts` (Weg zum Kurs-Fenster), ui-scale-Fälle in `dev.client.UiScaleCases` (angehängt).

Für die Befunde AUTO31-1 und AUTO31-2 zusätzlich: `race.RacePayloads` (`LeaderboardS2C#times`), `race.RaceServer`
(`leaderboardPayload` setzt die Zahl) und die Selbsttests `race.client.AssistedLapSelfTests` (`flughilfe:`, Bild
`leaderboard_assisted`) und `race.client.CourseEditSelfTests` (Bild `leaderboard_course_edit`): Sie öffneten die
Bestenliste über das `open`-Paket des Kurs-Werkzeugs, das seit M2 das Menü öffnet; jetzt schickt der Server die Daten
ohne `open` und der Test öffnet die Bestenliste im Client, wie die übrigen Bestenlisten-Tests.

---

## 13. Nachtrag: Bestenliste ohne Grenzen (Befund LBALL-1, Strang `race-data`)

Owner 29.10. (Bild der Bestenliste von Kurs 6, Kategorie „Alle“: nur 1 Rekord und 5 mit Flughilfe zu sehen): Die
Bestenliste zeigt **alle** Zeiten, in jeder Klasse und in „Alle“, ohne und mit Flughilfe; die Liste scrollt wie die
anderen Propwash-Fenster, der Server liefert seitenweise nach; beide Abschnitte sind einklappbar, der Zustand bleibt in
der Sitzung; die eigene Zeile bleibt auffindbar; Schnittstelle, JSON-Spiegel und Befehle liefern ebenfalls alle Zeiten.
Ersetzt M6 „Verworfen: alle Zeilen schicken“ (§12): statt alle Zeilen in einem Paket gibt es Seiten.

| Nr. | Was | Warum | Verworfen |
|---|---|---|---|
| L1 | Keine Grenze je Klasse in der Wertung (`Leaderboard.MAX_PER_CLASS` 50) und im Speicher (`MAX_STORED_PER_CLASS`, `MAX_STORED_RACES` je 200); erhalten bleibt `MAX_RULE_SETS` (8 Regel-Sätze je Pilot und Klasse) | „alle Zeiten“: der 51. Pilot einer Klasse fehlte in der Liste, der 201. wurde gelöscht; jede Zeit braucht eine vom Server bestätigte Runde, das Wachstum folgt den Piloten (je Pilot und Klasse höchstens 8 ohne und 1 mit Flughilfe) | große, aber endliche Grenze (wäre wieder eine Top-N-Liste) |
| L2 | `Leaderboard#ranked(abschnitt)` vergibt Klassen- und Gesamtplatz in einem Durchgang; `Leaderboard#revision` ändert sich bei jedem Neuberechnen (globaler Zähler, auch eine neue Bestenliste nach Layout-Wechsel hat eine neue) | Plätze kommen vom Server und stimmen über Seitengrenzen; eine Seite einer anderen Revision passt nicht mehr zu den schon geladenen | `rank()` je Zeile (quadratisch); Prüfsumme der Liste |
| L3 | Erstes Paket `LeaderboardS2C`: Platz 1 bis 50 von „Alle“ beider Abschnitte, alle eigenen Zeiten, der Rekord jeder Klasse (für Klassen-Chips und Rekord-Geist), dazu `BoardTotals` (Revision, Zahl je Abschnitt in „Alle“ und je Klasse) | Kopfzeilen zeigen die Zahl sofort, die Liste hat ihre volle Höhe, `GhostLibrary#recordKey` findet den Klassenrekord wie bisher | jede Klasse mit eigener erster Seite (Paket wächst mit den Klassen) |
| L4 | Weitere Seiten: `LeaderboardPageC2S(kurs, revision, alle/klasse, abschnitt, versatz)` → `LeaderboardPageS2C(kurs, revision, zeilen)`, je Seite höchstens 50 Zeilen und 512 KiB (geschätzt, lange Sharecodes kürzen die Seite); Anfragen höchstens 10/s je Spieler; veraltete Revision oder leere Seite → frisches erstes Paket, gedrosselt wie normale Anfragen (4/s); Zahlen beim Lesen auf 1 000 000 begrenzt | große Bestenlisten sprengen kein Paket (Grenze für Pakete an den Client 1 MiB); keine Last ohne Scrollen | alles auf einmal; Seiten der Gesamtliste statt je Ansicht (Klassen mit langsamen Zeiten bräuchten viele Seiten) |
| L5 | Client: Zeilen aller Seiten in einem `LeaderboardS2C` (`merge`, gleiche Revision, Schlüssel Abschnitt + Klasse + Klassenplatz), `BoardIndex` (Plätze einer Ansicht, dünn besetzt), `BoardPager` (eine Anfrage zur Zeit, 120 ms Abstand, 3 s Wartezeit, 5 Versuche je Seite, feste Testdaten ohne Revision fragen nie nach) | alte Leser von `rows()` (Menü, Geister, Auswahl) arbeiten weiter; Seiten erst, wenn ihre Plätze ins Bild scrollen | eigener Zwischenspeicher je Ansicht |
| L6 | Fenster: Liste mit allen Plätzen, nur sichtbare Zeilen gezeichnet, nicht geladene als „…“; Kopfzeilen „▼ Bestzeiten (n)“ und „▼ Mit Flughilfe – zählt nie als Rekord (n)“, Klick klappt ein/aus (`BoardSections`, statisch für die Sitzung); die Kopfzeile des Abschnitts bleibt beim Scrollen oben stehen (`BoardLayout#sticky`), darunter wird abgeschnitten; „Meine Zeit (Platz)“ rechts in der Kopfzeile springt zur eigenen Zeile und klappt dafür auf; die abgesetzte eigene Zeile unter den Top 10 entfällt | wie die anderen Menüs (Mausrad, Scrollbalken, Bild auf/ab, Pos1/Ende über `ScrollArea`); Einklappen auch tief in der Liste erreichbar | Knopf „Meine Zeit“ in der Fußleiste (zu eng bei 320 breit auf Deutsch) |
| L7 | `BoardQuery.MAX_LIMIT` = `Integer.MAX_VALUE` (vorher 500), der JSON-Spiegel schreibt jede Zeit; `/propwash race leaderboard [page <n>] [<kurs>]` zeigt alle Zeiten zu 10 je Seite mit anklickbarer nächster Seite | Schnittstelle, Dateien und Befehle ohne Top-N; Chat bleibt lesbar | alle Zeilen in den Chat |
| L8 | Protokoll bleibt 17 | 0.6.0 ist unveröffentlicht (R15) | eigener Sprung |

Dateien außerhalb der Besitzliste von `race-data` für genau diesen Befund (gemergte Stränge `race-a`, `race-b`,
`race-ghosts-a`, `race-p2p`): `race.Leaderboard` (L1, L2), `race.RacePayloads` (L3, L4), `race.RaceServer`
(Seiten, Drossel), `race.RaceModule` und `race.client.RaceClient` (Anmeldung der Seiten-Pakete, angehängt),
`race.RaceCommands` (L7), `race.client.ClientLeaderboards`, `race.client.LeaderboardScreen` (L5, L6), Selbsttests
`race.client.RaceSelfTests` und `dev.client.UiScaleCases` (erwarteten die Grenze von 10), JUnit `race.LeaderboardTest`,
`race.AssistedLapsTest`, `race.CourseMenuTest`, `race.data.LeaderboardGhostsTest` (prüften die Grenzen). Neu:
`race.LeaderboardPages`, `race.BoardIndex`, `race.BoardPager`, `race.client.BoardLayout`, `race.client.BoardSections`.

Tests: JUnit `race.data.LeaderboardAllTest` (scheiterte vor dem Fix: 50 statt 120 je Klasse, 200 statt 260 gespeichert,
Platz 11 fehlte im ersten Paket, 50 statt 600 in Schnittstelle und JSON) und `race.data.LeaderboardPagesTest` (Seiten,
Ränge über Seitengrenzen, Zahlen, Revision, Anfrage-Takt, Paketgröße, Lage beim Einklappen, Einklapp-Zustand,
Befehlsseiten); Selbsttest `race-data:` mit 120 Zeiten ohne und 60 mit Flughilfe (Scrollen lädt alle Plätze gleich dem
Server, Klasse mit nachgeladenen Plätzen, Einklappen über Klasse, Kurs und Neuöffnen, „Meine Zeit“, GUI-Skalierung 2
bis 4, Bilder `race_data_board_*`); Beitrittstest `PROPWASH_JOINTEST_RACEDATA` mit derselben Bestenliste am dedizierten
Server (zwei Piloten, Seiten über das Netz, Summen und eigene Plätze gleich dem Server).

---

## 14. Nachtrag: „Alle“ mit einer Zeile je Pilot und Klasse (Befund ALLBOARD-1, Strang `race-data`)

Owner 02.11.: In „Alle“ steht die Bestzeit jedes Piloten **je Klasse** als eigene Zeile, alle nach Zeit sortiert. Fliegt
ein Pilot in Klasse A 30 s und in Klasse B 20 s, steht die 20-s-Zeile weiter oben und die 30-s-Zeile weiter unten, jede
mit eigener Klasse, eigenem Platz, Teilen-Code, Replay und Geist. Innerhalb einer Klasse bleibt es bei einer Zeile je
Pilot (Bestzeit). Gilt für „Bestzeiten“ und „Mit Flughilfe“, das Nachladen in Seiten, „Meine Zeit“, Schnittstelle und
JSON-Spiegel sowie die Chat-Ausgaben mit Plätzen. Ersetzt in L2 (§13) den Gesamtplatz −1 für Zeilen, die nicht die beste
des Piloten sind.

| Nr. | Was | Warum | Verworfen |
|---|---|---|---|
| A1 | `Leaderboard#top(null, …)` nimmt jede Zeile der Wertung (sie ist schon je Pilot und Klasse zusammengefasst), `ranked(abschnitt)` gibt jeder Zeile ihren Gesamtplatz (Index + 1) | eine Regel für Fenster, Seiten, Summen, Befehl, Schnittstelle und JSON; die Zeilen tragen schon heute je Eintrag `GhostKey` (Pilot, Klasse, Zeit, Datum), Teilen-Code und Geist | eigene Liste nur für „Alle“ |
| A2 | `rank(pilot, null)` und `BoardIndex#selfRank` = beste eigene Zeile; alle eigenen Zeilen bleiben hervorgehoben; Tooltip „Zu deiner besten Zeit auf Platz n springen“ | Sprung auf die beste eigene Zeile (Vorgabe) | Sprung der Reihe nach durch alle eigenen Zeilen |
| A3 | `Leaderboard#allRank(pilot, klasse, abschnitt)`: Platz der Zeile einer Klasse in „Alle“; `PERSONAL_BEST` (Runde) und `LAP_COMPLETED` nennen diesen Platz | der Eintrag im Ereignis ist die Zeile der geflogenen Klasse, ihr Platz muss zu ihrer Zeit passen | Platz der besten eigenen Zeile (passte nicht zur Zeit des Eintrags) |
| A4 | Chat „Persönliche Bestzeit … (Platz n, Klasse)“ bleibt der Klassenplatz; `/propwash race leaderboard` listet „Alle“ mit der neuen Regel | die Meldung nennt die Klasse ausdrücklich | Gesamtplatz in der Meldung |
| A5 | Protokoll 17, Paketformat, NBT, Kursformat und JSON-Schema 1 unverändert (`overallRank` gab es schon je Zeile, nur der Wert ändert sich) | 0.6.0 ist unveröffentlicht | neues Feld |

Rennzeiten über mehrere Runden (`races`) bleiben je Pilot und Rundenzahl ohne Klasse gespeichert (D9); sie sind keine
„Alle“-Liste und ändern sich nicht.

Dateien außerhalb der Besitzliste von `race-data` für genau diesen Befund (gemergte Stränge `race-a`, `race-b`):
`race.Leaderboard` (A1–A3), `race.export.RaceSnapshots` und `race.export.RaceDataEvents` (A3, gehören zu `race-data`),
JUnit `race.LeaderboardTest` (prüfte die alte Regel). Tests: JUnit `race.data.LeaderboardClassRowsTest` (scheiterte vor
dem Fix: 71 statt 112 Zeilen in „Alle“, Zeile der zweiten Klasse fehlte in Liste, Teilen-Code und Schnittstelle, Platz 1
statt 2 im Ereignis), `LeaderboardPagesTest`, `RaceSnapshotsTest`, `RaceDataJsonTest` (Vergleichsdatei um die zweite Klasse
von Anna ergänzt) auf die neue Regel umgestellt; Selbsttest `race-data:` „„Alle“ mit Bestzeit je Pilot und Klasse …“ und
Bild `race_data_board_classes_{en,de}`.
