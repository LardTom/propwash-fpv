# Gate einstellen im Spiel (race, 0.5.0)

Rückmeldung aus dem Spieltest von 0.5.0: Im Fenster „Gate einstellen“ war das Gate schlecht zu sehen, weil das Fenster
den Blick festhielt. Gewünscht war eine vollständige Ansicht: weiter mit WASD laufen und mit der Maus umsehen, die Maus
nur bei Bedarf für das Panel freigeben und das irgendwo als Tipp zeigen. Das Fenster ist deshalb durch einen
**Einstell-Modus** ersetzt, der als HUD läuft. Er gilt für Gates, Wendeflaggen, Slalomstangen, Tauchwürfel und
Freiform-Gates. Grundlage bleiben `docs/design/gate-free-transform.md`, `course-elements.md` und `freeform-gate.md`.

## 1. Bedienung

- **Starten:** Schleichen + Rechtsklick mit Gate-Item, Kurs-Element oder Kurs-Werkzeug (Haupt- oder Nebenhand) auf ein
  platziertes Gate (`GateEntity#interact` → `GateEntity#setEditor` → `race.client.GateEditor#start`). Es öffnet sich
  **kein** Screen: Die Welt bleibt ganz sichtbar, WASD, Springen, Schleichen und der Blick mit der Maus funktionieren
  weiter, das Spiel pausiert nicht.
- **Panel** rechts (Propwash-Theme, `race.client.GateEditPanel`, senkrecht mittig): 212 GUI-Pixel breit, für längere
  Titel wie „Freiform-Gate einstellen“ bis 234, damit es bei GUI-Skala 4 in 1080p höchstens die halbe Breite nimmt.
  Inhalt: Titel, Stand in der Welt samt Kurs und Nummer, Zeilen je Art, Status, Tastenhilfe.
  - Gate: Gieren, Nicken, Rollen (Abschnitt „Drehung“), Breite, Höhe (Abschnitt „Öffnung“).
  - Wendeflagge, Slalomstange, Tauchwürfel: Richtung, Seite/Umrunden/Ausgang, Wertung bzw. Breite, Höhe.
  - Freiform-Gate: Flugrichtung; darunter, ob die Blöcke die Öffnung noch so umschließen wie beim Setzen.
  - Gate und Freiform-Gate haben eine zweite Seite „Redstone“ (Abschnitt „Redstone-Signal“: Signal an/aus, Pulslänge,
    Nur im Rennen, `docs/design/gate-redstone.md` §2). Schleichen + Mausrad läuft über die Zeilen beider Seiten,
    rechts in der ersten Abschnittszeile steht „Redstone »“ bzw. „« Gate“ (mit freier Maus ein Knopf); das Panel ist
    auf beiden Seiten gleich hoch. Der Block, der den Strom bekommt, ist rot umrandet und unter den Zeilen mit Namen
    genannt; „Zurücksetzen“ stellt auf dieser Seite das Signal zurück.
  - Die gewählte Zeile hat einen gelben Balken links und einen hervorgehobenen Regler.
- **Mausrad:** ändert die gewählte Zeile. Winkel in 5°-Schritten auf dem 5°-Raster (33° → 35° → 40°), mit gedrückter
  Strg-Taste in 1°-Schritten; Größen in halben Blöcken (1–16); Seite/Ausgang/Richtung schalten weiter. Das Mausrad
  wechselt dabei nicht den Slot der Schnellleiste.
- **Schleichen + Mausrad:** wählt die Zeile (Rad nach unten = nächste Zeile, ringsum).
- **Gate drehen** (`R`): Gieren um die Dreh-Schrittweite aus dem Tab „Rennen“; mit Schleichen bei Kurs-Elementen die
  nächste Seite bzw. der nächste Ausgang, bei Gates die Gegenrichtung; beim Freiform-Gate kehrt `R` die Flugrichtung um.
- **Enter oder Rechtsklick (Benutzen-Taste):** übernehmen und den Modus verlassen. Ohne Änderung verlässt er den Modus nur.
  Ist die neue Stellung blockiert, nicht erlaubt oder zu weit weg, bleibt der Modus offen und eine rote Meldung nennt den
  Grund. Linksklick tut im Modus nichts (kein Abbauen aus Versehen).
- **Esc:** abbrechen, Änderungen verwerfen („Änderung verworfen“), ohne das Spielmenü zu öffnen; ein zweites Esc öffnet
  es wie gewohnt.
- **Maus frei:** Solange die Taste „Gate einstellen: Maus benutzen (halten)“ gedrückt ist (Standard **linke
  Alt-Taste**, auf dem Mac die linke Wahltaste ⌥; in den Steuerungs-Einstellungen änderbar), öffnet sich `race.client.GateEditCursorScreen`: dasselbe Panel mit echten
  Bedienelementen, der Mauszeiger ist frei. Regler ziehen, −/+ in der Dreh-Schrittweite bzw. 0,5 Block, Rechtsklick auf
  einen Regler = Ausgangswert, Pfeiltasten am gewählten Regler (Shift ×10), Mausrad über einer Zeile ändert diese Zeile,
  Tooltips erklären jede Zeile. Unten Knöpfe „Zurücksetzen“ (beim Freiform-Gate „Neu abtasten“), „Übernehmen“, „Aus dem
  Kurs“ (nur im Kurs, nur mit Kurs-Werkzeug) und „Abbrechen“. WASD, Springen, Schleichen und Sprinten gehen auch mit
  freier Maus weiter. Loslassen der Taste schließt das Panel wieder, ebenso ein Fokusverlust des Fensters (Alt+Tab).
- **Tastenhilfe:** Mausrad und Strg, Schleichen + Mausrad, Enter/Rechtsklick, Esc; beim Freiform-Gate mit nur einer Zeile
  stattdessen „Mausrad oder R: Flugrichtung umkehren“.
- **Hinweis zur freien Maus:** Direkt unter dem Kopf des Panels steht immer ein unterlegtes Feld „Alt links halten:
  Maus benutzen“ bzw. mit freier Maus „Alt links loslassen: umsehen“ (Tastenname aus der aktuellen Belegung, auf dem
  Mac „Linke ⌥ Wahltaste“). Die ersten drei Male, die ein Spieler den Modus öffnet, ist das Feld hervorgehoben (heller
  Grund, Rahmen, weiße Schrift) und pulsiert; sobald die Taste einmal benutzt wurde, bleibt es ruhig. Beim Start nennt
  außerdem sechs Sekunden lang ein Feld links neben dem Panel die Taste („Gate einstellen – Alt links halten, um die
  Maus zu benutzen“). Einzelheiten in Abschnitt 4a.
- **F1** blendet das Panel mit dem übrigen HUD aus – für den freien Blick aufs Gate.
- Der Modus endet von selbst, wenn das Gate verschwindet, der Spieler die Welt wechselt, stirbt, zuschaut, eine Drohne
  steuert, weder Gate noch Kurs-Werkzeug in der Hand hält („Gate einstellen beendet: kein Gate …“) oder sich mehr als
  `GateEditor.LEAVE_REACH` (32 Blöcke über die Interaktionsreichweite hinaus) entfernt.

## 2. Live-Vorschau

- Jede Änderung rechnet sofort die neue Stellung (`GateMounting#preview`) und prüft sie wie der Server
  (`GateItem#insideWorld`, `GateItem#free` mit Spielern und Drohnen), dazu alle 10 Ticks neu.
- **Passt die Stellung**, zeigt der Client das Gate selbst in der neuen Stellung – mit Rahmen, Lichtleisten, Nummern-Tafel
  und Markierungen wie im Kurs (`GateRenderer#submitEdited`), darauf ein grüner Pfeil in Flugrichtung. Das echte Gate
  wird so lange nicht gezeichnet (`GateRenderer#shouldRender` → `GateEditor#replaces`), die alte Stellung bleibt als
  blasser Umriss (`GateEditor.OLD_POSE_TINT`) sichtbar, weil dort bis zum Übernehmen noch die Kollision liegt.
  Beim Freiform-Gate erscheint die Fläche mit den Pfeilen in der neuen Flugrichtung.
- **Passt sie nicht** (blockiert, nicht erlaubt, gestapelt, zu weit weg), bleibt das echte Gate stehen und die neue
  Stellung erscheint als roter Geist mit rotem Pfeil wie bisher; die Statuszeile nennt den Grund.
- Die Platzierungs-Vorschau des Gate-Items (`GatePreview`, `FreeformPreview`) ist im Modus aus.

## 3. Server und Netz

- Übernehmen schickt wie bisher `GateEditC2S(APPLY)`, „Neu abtasten“ `RESCAN`, „Aus dem Kurs“ `UNLINK`. Der Server prüft
  alles selbst (Rechte, Kursbesitz, Werkzeug in der Hand, Platz, Stapel) und lehnt ab, was nicht geht.
- **Reichweite:** Damit man zum Ansehen zurücktreten kann, gelten für `edit`, `rescan` und `unlink`
  Interaktionsreichweite + `GateMounting.EDIT_REACH` (16 Blöcke) statt + 2. „Gate drehen“ auf ein anvisiertes Gate bleibt
  bei + 2. Der Client zeigt jenseits davon „Zu weit weg zum Übernehmen – geh näher heran“.
- Netzprotokoll (11) und öffentliche API bleiben unverändert: kein neues Paket, keine neuen synchronisierten Daten.

## 4. Technik

- `race.client.GateEditModel` (reines Java bis auf `CourseElement`, `GateSize`, `GateTilt`): Zeilen je Art, Auswahl,
  Rasterschritte (`onGrid`), Gieren 0–360, Rollen über ±180 hinweg, Nicken bis ±90, Größen in halben Blöcken, Varianten
  ringsum, Freiform nur umkehren. JUnit `GateEditModelTest`.
- `race.client.GateEditor`: Sitzung (Gate, Modell, Vorschau, Status), Eingaben, Leerlauf-Prüfungen, HUD-Ebene
  `propwash:gate_edit`, Welt-Vorschau über `ClientBus.SUBMIT_WORLD_GEOMETRY`.
- Eingaben ohne Screen über drei Client-Mixins in `mixin.race.client`:
  - `MinecraftMixin` (Anfang von `handleKeybinds`): Benutzen-Klicks → übernehmen, Angriff leeren; nichts anderes wird
    angefasst, Bewegen, Schnellleiste per Zahl und Inventar bleiben normal.
  - `MouseHandlerMixin` (Anfang von `onScroll`): Mausrad mit Vanilla-Empfindlichkeit und eigenem `ScrollWheelHandler`
    zum Modus statt zur Schnellleiste.
  - `KeyboardHandlerMixin` (Anfang von `keyPress`): Enter und Esc beim Drücken; Wiederholungen derselben Taste werden bis
    zum Loslassen verschluckt, damit ein gehaltenes Esc nicht doch das Spielmenü öffnet.
  Alle drei greifen nur, solange der Modus läuft und kein Screen offen ist.
- `race.client.GateEditCursorScreen` (Theme, pausiert nicht, kein Hintergrund): öffnet sich beim Tick, wenn die Taste
  gedrückt ist; schließt beim Loslassen-Ereignis der Taste oder beim Übergang des Fensters von aktiv zu inaktiv. Beim
  Öffnen übernimmt er gehaltene Lauftasten (Vanilla lässt beim Öffnen eines Screens alle Tasten los), Lauf-, Sprung-,
  Schleich- und Sprinttasten setzt er auch mit freier Maus.
- Taste: `KeyDefaults.GATE_EDIT_CURSOR` (`key.propwash.gate_edit_cursor`, linke Alt-Taste, keine Vanilla-Belegung).

## 4a. Hinweis zur freien Maus (11.10.)

Anlass war eine Rückmeldung aus dem ersten Spieltest: Das Panel war gefunden, aber nicht, wie man darin die Maus
benutzt – die Zeile dazu stand als letzte von fünf unter der Tastenhilfe.

- **Feld oben im Panel** (`GateEditPanel#mouseTip`): erste Zeile unter dem Kopf, dunkelgelb unterlegt mit Akzentstreifen,
  Text „… halten: Maus benutzen“ (`propwash.race.edit.mouse.hold`) bzw. „… loslassen: umsehen“
  (`…mouse.release`). Die Zeile unter der Tastenhilfe entfällt, das Panel wird dadurch nur 5 GUI-Pixel höher; in der
  gedrängten Darstellung (z. B. 320 × 240) gar nicht. Passt der Text nicht in 212 GUI-Pixel, wird das Panel bis 234
  breit (`panelWidth`), danach bricht der Text um.
- **Hervorhebung die ersten drei Male:** `ui.HintMemory` (reines Java) zählt je Spieler und Hinweis, wie oft er gezeigt
  wurde; `ui.client.FirstUseHints` legt das in `config/propwash/hints.json` ab (Schlüssel: UUID des Spielers, höchstens
  32 Spieler, die zuletzt benutzten bleiben). `GateEditor.start` fragt `fresh(MOUSE_HINT, 3)` und zählt weiter; solange
  der Hinweis frisch ist, mischt `GateEditor.tipGlow()` (`HintMemory.pulse`, Periode 1,4 s) Grund, Rahmen und Schrift
  zwischen ruhig und hell. Öffnet die Taste das Panel mit freier Maus, gilt der Hinweis als gelernt
  (`GateEditor.mouseUsed` → `learned`), die Hervorhebung endet sofort und kommt nicht wieder.
- **Start-Hinweis:** `GateEditor.extractNotice` zeichnet 6 s lang (die letzten 0,6 s ausblendend) ein Hinweis-Feld im
  Propwash-Stil in die Mitte der freien Fläche links vom Panel, 68 GUI-Pixel über dem unteren Rand (Höhe der
  Vanilla-Zeile über der Schnellleiste), umbrochen auf die freie Breite. Bleiben links weniger als 110 GUI-Pixel
  (320 × 240), entfällt es; das Feld im Panel steht immer da. Es verschwindet, sobald die freie Maus benutzt wird.
- **Tastenname:** `input.client.KeyNames` nennt die Wahltasten des Mac „Linke ⌥ Wahltaste“ / „Left ⌥ Option“ (Vanilla:
  „Wahltaste links“). Das Zeichen ⌥ kommt aus dem eigenen Font `propwash:keys` (`assets/propwash/font/keys.json`:
  eine 8 × 8-Glyphe, sonst Verweis auf `minecraft:default`), den `KeyNames` nur diesem Tastennamen mitgibt.
- **Hilfetexte:** Shift-Tooltips von Gate, Kurs-Element und Kurs-Werkzeug nennen die Taste in der Zeile zum
  Einstellen (`… einstellen, <Taste>: Maus`), die Flugschul-Szene „Gates frei drehen“ zeigt sie als zweiten
  Tastenhinweis („Halten: Maus benutzen“) und im Text; Spieler-Doku `docs/RELEASE-NOTES.md`.
- **Entscheidungen:**
  - Feld oben statt hervorgehobener Zeile unten: Man liest das Panel von oben, und unten stand die Zeile zwischen vier
    grauen. Verworfen: die Zeile nur einfärben (war schon gelb und wurde übersehen).
  - Eigenes Feld links vom Panel statt der Vanilla-Zeile über der Schnellleiste: Die ist auf die Bildmitte zentriert und
    lief bei 640 × 360 unter das Panel, bei 480 × 270 beginnt das Panel genau in der Bildmitte.
  - Start-Hinweis bei jedem Öffnen, Hervorhebung nur die ersten drei Male: Der kurze Hinweis stört nicht, das Pulsieren
    auf Dauer schon. Verworfen: Hinweis nur die ersten Male (wer nach Wochen wieder baut, hat die Taste vergessen).
  - Zählung je Spieler in eigener Datei statt eines Feldes in den Renn-Einstellungen: Die Einstellungen werden als
    Arbeitskopie bearbeitet und gespeichert, ein Zähler darin ginge beim Speichern verloren; die Datei hat Platz für
    weitere Hinweise.
  - Es bleibt bei der linken Alt-Taste allein; der Name nennt die Seite. Verworfen: beide Alt-Tasten annehmen (AltGr
    meldet sich unter Windows zusätzlich als Strg, und Strg ist im Modus der Feinschritt).
  - Eigene Glyphe für ⌥: Die Unifont-Glyphe aus dem Vanilla-Font war bei GUI-Skala 3 ein unlesbarer Fleck.
  - In den Flugschul- und Tooltip-Texten ohne das Zeichen ⌥ („Mac: Wahltaste“), weil diese Texte im Vanilla-Font
    stehen.
- **Tests:** JUnit `ui.HintMemoryTest` (Zählung je Spieler, Lernen, Vergessen, kaputte Datei, Obergrenzen, Puls),
  `input.client.KeyNamesTest` (Wahltasten mit Zeichen in beiden Sprachen); Selbsttest „race: Gate einstellen im Spiel
  …“ prüft dazu: beim ersten Öffnen hervorgehoben und gezählt, Start-Hinweis nennt Titel und Taste und liegt links
  vom Panel über der Schnellleiste, Feld unter dem Kopf und über den Zeilen bei 480 × 270 (nicht gedrängt, einzeilig)
  und 320 × 240, nach dem ersten Gebrauch der Taste ruhig, drei Mal frisch und beim vierten nicht mehr. Der
  Rahmen-Schritt „dev: Hinweis zur freien Maus im Gate-Editor gilt als bekannt, Start-Hinweis aus …“ sorgt dafür, dass
  alle anderen Schritte und Bildschirmfotos den ruhigen Zustand zeigen. Bildschirmfotos `gate_edit_mouse_tip` und
  `gate_edit_mouse_tip_de` zeigen den hervorgehobenen Zustand mit Start-Hinweis.

## 5. Tests

- JUnit `race.client.GateEditModelTest`: Zeilen je Art, 5°-Raster und Strg-Feinschritt, Umbruch von Gieren und Rollen,
  Grenze beim Nicken, halbe Blöcke 1–16, Zeilenwahl ringsum, Varianten der Elemente, Freiform umkehren, Zurücksetzen,
  Rasterschritte von beliebigen Werten.
- Selbsttest (Fabric und NeoForge):
  - `race: Gate einstellen im Spiel mit Mausrad, Laufen, freier Maus, Enter und Esc`: echtes Schleichen + Rechtsklick
    startet den Modus ohne Screen; Block im Weg → rot, Enter übernimmt nicht; Mausrad über `MouseHandler#onScroll`
    (Slot bleibt), Schleichen + Mausrad wählt Zeilen; das Gate wird live ersetzt; der Spieler läuft mit der Vorwärts-Taste
    weg und der Modus bleibt; das Panel passt in 480 × 270 GUI-Pixel (GUI-Skala 4 bei 1080p); die Taste „Maus frei“ öffnet
    das Panel mit Mauszeiger, ein Klick auf + bei Breite ändert den Wert und wählt die Zeile, Loslassen schließt es;
    Enter über `KeyboardHandler#keyPress` übernimmt beim Server; Esc verwirft ohne Spielmenü.
  - `race: Kurs-Elemente im Spiel einstellen, Ausgang per Mausrad, Rechtsklick und Enter`: Tauchwürfel, Zeile Ausgang per
    Schleichen + Mausrad, Rechtsklick übernimmt, Schleichen + `R` stellt zurück, Enter übernimmt.
  - `race: Freiform-Gate im Spiel einstellen kehrt die Richtung um`: Mausrad kehrt um (live ersetzt), Enter übernimmt,
    `R` kehrt zurück.
- Bildschirmfotos (Fabric): `gate_edit_mode`, `gate_edit_mode_de`, `gate_edit_cursor` (freie Maus über „Rollen“ mit
  Tooltip), `course_element_edit_mode`, `course_element_edit_mode_de`, `freeform_gate_edit_mode`.
  Die Schritte von `gate_edit_mode` und `gate_edit_mode_de` prüfen dazu, dass die Titel aller Arten ungekürzt in den
  Kopf des Panels passen.

## 6. Entscheidungen

- **HUD-Modus statt Fenster.** Verworfen: das Fenster behalten und nur durchsichtiger machen (Blick und Laufen blieben
  gesperrt) sowie eine Kamera, die frei um das Gate fliegt (neue Steuerung, die man erst lernen müsste).
- **Alt halten statt Shift.** Shift ist Schleichen und wird im Modus für die Zeilenwahl gebraucht. Alt hat in Vanilla keine
  Belegung, liegt neben der linken Hand und ist änderbar. Verworfen: Umschalten per Tippen (man vergisst, dass die Maus
  frei ist) und Shift (Schleichen wäre nicht mehr möglich).
- **Panel mit freier Maus als eigener, nicht pausierender Screen.** Die Bedienelemente sind dieselben Regler und Knöpfe
  wie in allen Propwash-Fenstern (Ziehen, Tooltips, Tastatur, Bildschirmleser). Verworfen: Klicks ohne Screen selbst
  auswerten (Mixin in `MouseHandler#onButton`, eigene Ziehlogik, keine Tooltips).
- **Schleichen + Mausrad wählt die Zeile.** Die rechte Hand bleibt an der Maus, die linke an WASD. Verworfen: Pfeiltasten
  (rechte Hand müsste wandern, liegen bei Tastatursteuerung auf Nicken und Rollen) und Zahltasten (wechseln den Slot).
- **5°-Raster, Strg für 1°.** Übliche Winkel (15°, 30°, 45°) liegen auf dem Raster, und ein Rad-Klick ist immer sichtbar.
  Verworfen: die Dreh-Schrittweite aus dem Tab „Rennen“ (Standard 90°, fürs Nicken zu grob); die gibt es weiter über `R`
  und −/+.
- **Übernehmen beendet den Modus.** Die Vorschau zeigt schon vorher genau das Ergebnis. Verworfen: offen bleiben wie das
  alte Fenster (man müsste den Modus zusätzlich mit Esc verlassen, das sonst verwirft).
- **Gate live in der neuen Stellung zeichnen**, alte Stellung als blasser Umriss. Verworfen: nur den weißen Geist über das
  stehende Gate legen wie im alten Fenster (zwei Gates übereinander, schwer zu lesen).
- **Größere Reichweite beim Übernehmen (+16 Blöcke).** Wer zum Ansehen zurücktritt, soll nicht wieder hingehen müssen.
  Verworfen: unbegrenzt (fremde Kurse aus der Ferne verstellen) und + 2 wie bisher (widerspricht der freien Ansicht).
- **Linksklick tut nichts.** Mit dem Kurs-Werkzeug würde er sonst das Gate abbauen, an dem man gerade arbeitet.
