# Freiform-Gate (race, 0.5.0)

Anforderung (Besitzer, 06.10., sinngemäß): ein Gate ohne eigenen Rahmen, dessen Grenze die Blöcke ringsum sind – damit
sich in Gängen, Höhlen und Fenstern Renn-Gates beliebiger Form setzen lassen. Die Öffnung muss kein Rechteck sein. Eine
Höchstgröße, per Gamerule einstellbar, Standard etwa 20 × 20 Blöcke, verhindert, dass die halbe Welt zum Gate wird.

## 1. Bedienung

- Item `propwash:freeform_gate` „Freiform-Gate“ (16 je Stapel), im Kreativ-Tab nach dem Tauchwürfel, Rezept unten,
  Shift-Hilfe.
- **Setzen:** Rechtsklick auf einen Block am Rand einer Öffnung (Boden eines Gangs, Seitenwand, Fensterbank, Sturz). Die
  Startzelle ist die Zelle vor der angeklickten Fläche; ist der angeklickte Block selbst durchlässig (Gras, Blume,
  Fackel), ist er die Startzelle.
- **Ebene:** achsparallel durch die Startzelle. Ihre Normale steht **senkrecht zur angeklickten Fläche** (die
  angeklickte Fläche gehört also zum Rand) und ist von den zwei möglichen Achsen die, die der Blickrichtung am nächsten
  kommt. Boden, Decke, Fensterbank: Ebene quer zum Blick (Gang, Höhleneingang, Fenster). Seitenwand eines Gangs: quer zum
  Gang. Innenwand eines Schachts mit Blick nach unten: waagerechte Tauch-Ebene. Die **Flugrichtung** ist „von dir weg“
  (Vorzeichen des Blicks), `R` (Gate drehen) kehrt sie vor dem Setzen um (`propwash.race.element.freeform.away/toward`).
- **Fläche:** Flutfüllung in der Ebene ab der Startzelle über durchlässige Zellen, begrenzt durch Blöcke, die
  kollidieren (§2). Ergebnis und Gründe einer Ablehnung zeigt die Vorschau beim Zielen (weißer Umriss oder rote
  Startzelle plus Text unten in der Mitte) und das Overlay beim Klick:
  „Hier ist keine Öffnung …“, „… nicht ringsum geschlossen – Blöcke müssen sie innerhalb von 20 × 20 Blöcken begrenzen“,
  „Die Öffnung ist 21 × 20 Blöcke groß – erlaubt sind höchstens 20 × 20 (Welt-Regel)“, „… reicht in Chunks, die nicht
  geladen sind“, „Hier ist schon ein Freiform-Gate“.
- **Gesetzt:** `R` mit Blick auf die Fläche kehrt die Richtung um – mit dem Kurs-Werkzeug, oder mit einem Freiform-Gate
  in der Hand beim Schleichen (ohne Schleichen zielt man mit dem Freiform-Gate durch die Fläche hindurch, und `R` kehrt
  die Richtung der Vorschau um). Schleichen + Rechtsklick startet „Freiform-Gate einstellen“
  als Einstell-Modus im Spiel (`docs/design/gate-edit-mode.md`): Kopfzeile „In der Welt: Freiform-Gate · 6 × 5 Blöcke
  (24 Felder) · nach Norden“, Zeile „Flugrichtung“ (Mausrad oder `R` kehrt um, die Fläche zeigt die neue Richtung live,
  Enter übernimmt), Zustand der Öffnung; mit gehaltener Alt-Taste Knöpfe „Neu abtasten“, „Übernehmen“, „Aus dem Kurs“
  (nur mit Kurs-Werkzeug) und „Abbrechen“.
- **Abbauen:** Schleichen + Linksklick mit einem Freiform-Gate in der Hand oder Linksklick mit dem Kurs-Werkzeug; das
  Item fällt heraus. Mit leerer Hand lässt sich ein Freiform-Gate nicht treffen (§ Zielen).
- **Zielen:** Die Fläche fängt den Blick nur mit dem Kurs-Werkzeug in der Hand oder beim **Schleichen** mit einem
  Freiform-Gate in der Hand (`GateEntity#picksFreeform`, Mixin `ProjectileUtilMixin` reicht den Zielenden durch). Sonst
  klickt man durch das Gate hindurch auf Blöcke und Mobs dahinter – auch mit einem Freiform-Gate in der Hand, damit sich
  in einem Gang hinter einem gesetzten Gate weitere setzen lassen. Bearbeiten (Schleichen + Rechtsklick), Abbauen
  (Schleichen + Linksklick) und Umkehren (Schleichen + `R`) geschehen beim Schleichen ohnehin.
- **Kurs:** Das Kurs-Werkzeug hängt Freiform-Gates wie Gates an; ein Freiform-Gate darf **Start und Ziel** sein
  (`CourseElement#startable`).

## 2. Flutfüllung (`race.FreeformFill`, reines Java)

| Frage | Entscheidung |
|---|---|
| Nachbarschaft | **4er-Nachbarschaft** (Kanten). Zwei Blöcke, die sich nur an einer Ecke berühren, schließen: Durch eine Blockecke passt keine Drohne. |
| durchlässig | Kollisionsform leer (`BlockState#getCollisionShape(…, CollisionContext.empty())`) **und** keine Flüssigkeit. Luft, Gras, Blumen, Fackeln, Schilder, Druckplatten, Schienen, Spinnweben, Schnee-Schicht 1 sind offen. Druckplatten und Stolperdrähte in der Öffnung bleiben benutzbar: Gates lösen sie nicht aus (`GateEntity#isIgnoringBlockTriggers`). |
| Rand | alles, was kollidiert: Vollblöcke, Stufen, Treppen, Zäune, Mauern, **Glasscheiben und Gitter**, **Laub** (die Drohne prallt daran ab), Teppich. |
| Flüssigkeiten | Rand. Wasser und Lava haben keine Kollision, ein Gate, das in einen See reicht, wäre nie geschlossen, und unter Wasser fliegt keine Drohne. Bewässerte durchlässige Blöcke (Seegras) zählen deshalb auch als Rand. |
| Grenze | Breite und Höhe der umschließenden Box ≤ Welt-Regel `propwash:freeform_gate_max_size` (Standard 20, also 20 × 20 erlaubt, 21 abgelehnt). |
| offen oder zu groß | Die Füllung läuft bis zur Prüfgröße `max(Grenze + 2, 2 × Grenze + 8)` (höchstens 128). Endet sie davor, ist die Öffnung geschlossen, aber zu groß („21 × 20 Blöcke“); läuft sie darüber, außerhalb der Bauhöhe oder der Weltgrenze, ist sie offen. |
| nicht geladen | Erreicht die Füllung einen nicht geladenen Chunk, wird abgelehnt (das Ergebnis wäre zufällig). |
| doppelt | In derselben Ebene und Zelle darf nur ein Freiform-Gate liegen (`FreeformBlocks#occupant`). Neben normalen Gates und Elementen ist es erlaubt. |

## 3. Speicherung und Netz

- **`race.FreeformRegion`** (reines Java, unveränderlich): Achse der Normale (x/y/z), Breite und Höhe der Box (1–128),
  Startzelle (relativ) und eine **Bitmaske** der Zellen (Zeile für Zeile, niedrigstes Bit zuerst; 20 × 20 = 50 Bytes,
  64 × 64 = 512 Bytes). Zeilen laufen entlang der „a“-Achse (x bei Normale y oder z, z bei Normale x), Spalten entlang „b“
  (y bei senkrechten Ebenen, z bei der Tauch-Ebene). Text: `f1:z:6x5:2,0:<Base64>`; ungültige Texte (Größe, Länge,
  Startzelle nicht gesetzt) ergeben `null`.
- **Gate-Entity:** Art `CourseElement.FREEFORM` (Variante 0 = Flug in +Achse, 1 = in −Achse) und die Fläche als
  synchronisierter Text `DATA_REGION` (Vanilla-`STRING`, kein eigener Serializer). Die Position der Entity ist der
  **Anker**: Mitte der Box in der Ebene, auf halber Blocktiefe (`Ebene + 0,5`). Die Fläche ist relativ zum Anker, ein
  `/tp` nimmt sie also mit. NBT `Kind = "freeform"`, `Variant`, `Region`; eine kaputte `Region` lädt als Einzelfeld.
  Keine Kollisionsteile; die Entity-Box ist die Blockschicht der Fläche (Sichtbarkeit, Reichweite beim Klicken).
- **Weltdaten:** `CourseRegistry.GateRecord#region` (Text, nur bei Freiform-Gates; Gates aus 0.4.0 bleiben Byte für
  Byte gleich).
- **Kursdaten:** `CourseData.Gate#region`, Merkerbit 8, danach Achse, Breite, Höhe, Startzelle, Bitmaske.
  `layoutHash` nimmt die Fläche auf. `CourseRegistry#joinBatches` teilt die Kurs-Synchronisation zusätzlich nach
  geschätzter Größe (≤ 512 KiB je Paket), damit auch 256 Freiform-Gates von 64 × 64 je Kurs unter der Paketgrenze bleiben.
- **Netzprotokoll bleibt 11:** 0.5.0 ist unveröffentlicht, 11 ist bereits das Protokoll von 0.5.0; neu sind ein
  Entity-Datenfeld, das Merkerbit in `race_courses` und die Aktion `RESCAN` von `race_gate_edit`.
- Parts-API unverändert (das Freiform-Gate ist kein Teil der API; die Flugschul-Szene ist dieselbe wie bisher).

## 4. Zählung (Client und Server gleich)

- `GateGeometry.freeform(anker, fläche, variante)`: Seite = a-Achse, oben = b-Achse, Normale = ±Achse, Ursprung =
  Ecke der Box in der Ebene. Die eine Stufe ist eine `Passage` mit Zellmaske: Ein Wegstück zählt, wenn es die Ebene in
  Flugrichtung schneidet und der Schnittpunkt in einer **gespeicherten** Zelle liegt (`floor(seite)`, `floor(oben)`).
  `LapTracker` (Client) und `RaceServer`/`LapVerifier` (Server) rechnen mit derselben `Passage` aus denselben Daten
  (Entity bzw. Kursdaten mit gleichem Anker und gleicher Maske, JUnit prüft Gleichheit und Runden).
- Freiform-Gates zählen streng wie Gates (`strictMiss`: als nicht nächstes Ziel „verpasst“); rückwärts durch das nächste
  Freiform-Gate meldet „Falsche Richtung“ wie bei Gates.
- Der Server sucht Gates eines Wegstücks im um `max(CourseElement.REACH, FreeformReach)` vergrößerten Bereich;
  `FreeformReach` wächst mit der größten geladenen Fläche (halbe Diagonale + 1), ebenso die Reichweite beim Zielen.
  Die Kollisionsabfragen (`ExtraCollider`) wachsen dadurch **nicht**, weil Freiform-Gates keine Kollisionsteile haben.
- Ideallinie: ein Wegpunkt im **sichersten Feld** der Fläche – dem Feld mit dem größten Abstand (4er-Schritte) zum Rand,
  bei Gleichstand dem Schwerpunkt am nächsten. Bei L-Formen und Ringen liegt der Schwerpunkt sonst im Fels. Derselbe Punkt
  ist die „Mitte“ für Mindestrundenzeit, Startwahl und Ideallinie.

## 5. Wenn sich die Blöcke ändern

- Die Fläche bleibt **wie gespeichert**, gezählt wird nur sie. Bestenlisten sollen nicht still ihre Bedeutung ändern,
  weil jemand einen Block abbaut, und Client und Server sollen ohne Blockabfragen dasselbe zählen.
- Der Client prüft alle 20 Ticks (`FreeformGateRenderer#view`), ob der Rand noch geschlossen ist und ob Felder zugebaut
  wurden (`FreeformFill#check`). Offene Stellen im Rand leuchten **rot pulsierend**, in der Mitte erscheint ein
  **bernsteinfarbenes Warndreieck mit „!“** (bis 32 Blöcke Abstand). Das Fenster nennt die Zahl offener Stellen und
  zugebauter Felder.
- „Neu abtasten“ füllt vom gespeicherten Startfeld aus neu (Grenze der Welt-Regel von jetzt); klappt es, übernimmt das
  Gate die neue Fläche (im Kurs wird die Bestenliste wie bei jeder Änderung archiviert), sonst bleibt alles, wie es war,
  und das Overlay nennt den Grund.

## 6. Darstellung (`race.client.FreeformGateRenderer`, `FreeformView`)

- Kein Rahmen. Entlang jeder Randkante (zu Läufen zusammengefasst) eine **LED-Leiste**: leuchtende Röhre 0,07 × 0,07,
  0,06 vor der Blockfläche, dazu ein weicher Schein in der Ebene (0,34 tief) und auf der Blockfläche (±0,42 entlang der
  Flugrichtung), alles im Render-Typ `eyes` (additiv, unabhängig vom Licht, also auch nachts und in Höhlen gut sichtbar).
- Farben wie bei Gate-LEDs (`GateRenderer#lightColor`): grau ohne Kurs, weiß im Kurs, grün als nächstes Ziel bzw.
  pulsierend als Start, rot und bernstein während des Countdowns am Start-Gate. Das Start-Gate trägt eine gestrichelte
  (karierte) Leiste und die Tafel „START“.
- **Füllung:** hauchdünne Fläche (16 % Deckkraft × Stärke), wenn die eigene Drohne (oder der Spieler) näher als 12 Blöcke
  ist (voll ab 3 Blöcken) oder das Gate das nächste Ziel im eigenen Rennen ist.
- **Markierungen:** drei animierte Leuchtwinkel vor dem sichersten Feld zeigen die Flugrichtung; die Nummern-Tafel sitzt
  auf dem Randblock über diesem Feld (vorne Nummer in Lichtfarbe, hinten klein und grau mit rotem Rand).
- Leistung: Umriss und Zeilenläufe werden je Fläche einmal berechnet (`FreeformView`, alle 20 Ticks neu wegen der
  Randprüfung); ein 20 × 20-Rechteck sind 4 Leisten und 20 Füllstreifen, unregelmäßige Höhleneingänge einige Dutzend.
  Halo und Schein entfallen ab 64 Blöcken Abstand.

## 7. Welt-Regel `propwash:freeform_gate_max_size`

Zahl 1–64, Standard 20, Gruppe „Spiel“, keine Physik. In allen drei Voreinstellungen (Realistisch, Training, Arcade)
**20**: Sie ist eine Bau-Grenze für Server-Betreiber, keine Schwierigkeit – wie Videokanäle, 3D-Modus und GPS Rescue ohne
Pilot bleibt sie überall auf dem Standard. Sie gilt beim Setzen und Neu-Abtasten; gesetzte Gates bleiben bei einer
späteren Senkung erhalten. Tab „Welt-Regeln“, `/gamerule`, `/propwash rules` und der Export `gamerules.json` übernehmen
sie ohne weitere Änderung.

## 8. Flugschule

Neuer Schritt „Freiform-Gate“ am Ende von „Gates frei drehen“ (`GateFreeScene`): Ein Felsen mit Höhleneingang entsteht,
der Pilot klickt mit dem Freiform-Gate auf den Rand, der Umriss leuchtet, `R` kehrt die Richtung um, eine Drohne fliegt
durch und das Gate leuchtet grün; Hinweis auf die Höchstgröße. JUnit `FreeformSceneTest` prüft, dass die Flutfüllung der
Blöcke im Diorama genau die gespeicherte Fläche ergibt und der Flug sie genau einmal vorwärts durchquert.

## 9. Rezept

| Item | Anzahl | Muster | Zutaten |
|---|---|---|---|
| `freeform_gate` | 1 | `GRG` / `R·R` / `GRG` | Glowstone-Staub, Redstone |

Ein Ring aus Licht und Strom um eine leere Mitte – die Öffnung bringt die Welt mit. Erzeugt mit `tools/data/survival.py`
(Gruppe `propwash_course`), Inventar-Symbol aus `tools/assets/world/course.py` (Felsbogen mit leuchtendem Rand und
Richtungswinkel).

## 10. Tests

- JUnit `race.FreeformGateTest` (20): Gang, L-Form, runder Höhleneingang, Pfeiler als Loch, offen (bis zum Himmel, ins
  Freie), Startzelle blockiert, nicht geladen, Grenze 20/21 in beiden Richtungen und 1/2, Ecken-Lecks (4er-Nachbarschaft,
  Treppe), senkrechte und waagerechte Ebenen, Wahl von Ebene und Richtung aus Fläche und Blick, Durchflug nur in Feldern
  und nur vorwärts, Text und Bytes hin und zurück, Kursdaten über das Netz samt Layout-Prüfsumme, Weltdaten, Runde mit
  `LapTracker` und `LapVerifier`, falsche Richtung und Auslassen, Randprüfung, LED-Läufe, Paketgröße, Welt-Regel.
  `guide.FreeformSceneTest` (2).
- Selbsttest `race.client.FreeformGateSelfTests` (Präfix `race: Freiform`): Setzen im Gang, Höhleneingang und Schacht,
  Ablehnungen (doppelt, offen, ohne Platz, 21 × 20) und Welt-Regel 21, Drohne fliegt frei durch; Client-Abgleich,
  Zielen nur mit Kurs-Werkzeug oder beim Schleichen mit Freiform-Gate, Umriss; Kurs mit Freiform-Start, Runde beim Client und Server (Abkürzung abgelehnt,
  Bestenliste); Drehen, Lücke, Neu abtasten, NBT; Einstellen im Spiel kehrt die Richtung per Mausrad und `R` um.
- Bildschirmfotos (Präfix `bildschirmfotos: Freiform`): `freeform_gate_cave(_night).png` (Höhleneingang als
  Start-Gate, Tag und Nacht), `freeform_gate_corridor(_night).png` (Gang mit Gate Nr. 2), `freeform_gate_preview.png`
  (Vorschau am Fenster mit Ecken), `freeform_gate_edit_mode.png`, `freeform_gate_changed.png` (Lücke im Rand rot,
  Warndreieck), Flugschule `flight_school_gate_free_freeform.png`.

## 11. Entscheidungen

| Frage | Entscheidung | Warum | Verworfen |
|---|---|---|---|
| Entity | `GateEntity` mit Art `FREEFORM` | Kurs, Nummer, Abbau, Besitz, Weltdaten, Synchronisation und Fenster gibt es schon | eigene Entity-Art |
| Ebene | senkrecht zur angeklickten Fläche, von den zwei Achsen die dem Blick nächste | die angeklickte Fläche ist Teil des Rands; robust in Gängen, an Fenstern und für Schächte | Ebene senkrecht zur Flächennormale (Boden-Klick ergäbe eine liegende Ebene) oder nur nach Blick (Seitenwand-Klick könnte eine Ebene parallel zur Wand ergeben, die nie geschlossen ist) |
| Lage der Ebene | Mitte der Startzellen-Schicht | Durchflug zählt mittig im Durchgang, eine Blockschicht | Vorderkante der Schicht |
| Nachbarschaft | 4er | Ecken lassen keine Drohne durch | 8er (Diagonalen würden auslaufen) |
| Laub, Scheiben, Gitter | Rand | sie kollidieren, auch für die Drohne | offen |
| Flüssigkeiten | Rand | sonst nie geschlossen, kein Unterwasserflug | offen |
| Grenze | Box ≤ N × N, Welt-Regel 1–64, Standard 20 | Vorgabe; 64 × 64 bleibt bei 512 Bytes je Gate | Zellzahl als Grenze (schwer zu erklären) |
| Voreinstellungen | alle 20 | Bau-Grenze, keine Schwierigkeit | Arcade größer |
| Speicherung | Bitmaske (Text Base64 für NBT/JSON, Bytes im Netz), relativ zum Anker | kompakt, schnell, einfach zu prüfen | Lauflängen (kaum kleiner bei Höhlen, mehr Code) |
| Sync | Vanilla-`STRING`-Datenfeld | kein Serializer-Register auf zwei Loadern | eigener `EntityDataSerializer` |
| Blöcke ändern sich | Fläche bleibt, Warnung + „Neu abtasten“ | Bestenlisten bleiben stabil, Client und Server zählen gleich | Fläche folgt den Blöcken automatisch |
| Richtung | Variante = Vorzeichen der Achse; `R` kehrt um | eine Ebene hat nur zwei Richtungen | Gieren wie bei Gates |
| Start | erlaubt | ein Höhleneingang als Start und Ziel ist natürlich; Startampel und Aufstellung brauchen nur die Ebene | wie Kurs-Elemente verbieten |
| Zielen | mit Kurs-Werkzeug, mit Freiform-Gate nur beim Schleichen | ohne Werkzeug soll das Gate unsichtbar für Klicks sein (Bauen durch die Öffnung); mit dem Freiform-Gate setzt man ohne Schleichen weitere Gates hinter einem gesetzten, beim Schleichen bearbeitet man es | immer zielbar; mit Freiform-Gate immer zielbar (dann ließe sich hinter einem Gate nichts setzen) |
| Ideallinie | sicherstes Feld | Schwerpunkt liegt bei L-Formen im Fels | Schwerpunkt, Mitte der Box |
| Protokoll | bleibt 11 | 0.5.0 unveröffentlicht | 12 |
| Flugschule | Schritt in „Gates frei drehen“ | „Gates und Rennen“ hat schon 7 Schritte und fast 60 s, die Obergrenze der Szenen | eigene Szene (bräuchte eine neue API-Konstante) |

## 12. Portierbarkeit

`FreeformRegion`, `FreeformFill`, `FreeformReach`, die Freiform-Geometrie in `GateGeometry`, `Passage` und
`client.FreeformView` sind reines Java ohne Minecraft-Typen und wandern für 1.21.1/26.2 unverändert mit;
`FreeformBlocks` ist die einzige Brücke zur Welt (Blockzustand, Kollisionsform, Flüssigkeit, geladen, Weltgrenze).
