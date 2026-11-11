# Rennsystem 0.6.0, Teil B: Schleifen, Reihen, Würfel und Stapeln

Für Spieler. Mit 0.6.0 darf ein Gate mehrmals in einem Kurs vorkommen – auch in Gegenrichtung –, Gates rasten
nebeneinander auf einen gemeinsamen Pfosten ein, aus vier Gates und einem liegenden Gate wird ein Tauchwürfel, und
Flaggen und Slalomstangen lassen sich auf Gates und Tauchwürfel setzen.

Teil A (Rennstart, Wertung nach unten, sichtbare Flächen, Verschieben): [`race-system.md`](race-system.md). Technik und
Entscheidungen: [`design/0.6.0/race-system.md`](design/0.6.0/race-system.md).

## 1. Ein Gate mehrmals im Kurs (Schleifen)

- Klickst du mit dem **Kurs-Werkzeug** ein Gate an, das schon im Kurs ist, hängt es einen **weiteren Durchgang** an
  (bisher kam „schon im Kurs“). So fliegst du z. B. A → B → A zurück → A.
- Die **Richtung** folgt deinem Blick beim Klick: Schaust du so aufs Gate, wie du durchfliegen willst, gilt die gleiche
  Richtung; klickst du **von hinten**, wird der Durchgang **rückwärts** geflogen. Das gilt für Gates, Freiform-Gates und
  alle Elemente:
  - Gate: rückwärts durch die Öffnung,
  - Slalomstange, Flagge und wertende Gate-Stange: von der anderen Seite her, also wie um 180° gedreht,
  - Tauchwürfel: oben hinein und am **gegenüberliegenden** Ausgang hinaus.
- Ein Gate kann auch zweimal hintereinander in gleicher Richtung im Kurs stehen (Power-Loop über das Gate). Hältst du
  die Taste gedrückt, zählt das Werkzeug das nur einmal; für einen zweiten Durchgang kurz loslassen und erneut klicken.
- **Anzeige:** Die Tafel am Gate und das Namensschild (Kurs-Werkzeug in der Hand) zeigen alle Nummern, Rückwärts mit
  ←, z. B. **„1 · 3←“**. Am Start-Gate steht „START · 3←“. Bodenpfeile gibt es für jede Richtung, in der das Gate
  geflogen wird. Das nächste Ziel leuchtet grün, egal welcher seiner Durchgänge gerade dran ist; im HUD zählt
  „Gate 3/3“ die Durchgänge.
- **Kurs-Fenster** (Rechtsklick in die Luft mit dem Kurs-Werkzeug öffnet die Kurs-Einstellungen, dort „Reihenfolge …“): Jede Zeile ist ein
  Durchgang. Eine Wiederholung steht als „… · noch einmal wie 1“, ein rückwärts geflogener Durchgang als
  „… · ← Gegenrichtung“. Der Knopf **⇄** dreht die Richtung eines Durchgangs um, **×** nimmt nur diesen Durchgang aus dem
  Kurs (das Gate bleibt drin, solange es weitere Durchgänge hat), ↑ ↓ und „Start“ wie bisher.
- Fliegst du das richtige Gate in der falschen Richtung, meldet das Rennen **falsche Richtung** – nicht „ausgelassen“.
- Die Richtung gehört zum Kurs: Umdrehen oder ein weiterer Durchgang archiviert die Bestenliste wie jede Änderung am
  Kurs.

## 2. Gates in einer Reihe ohne doppelte Pfosten

- Setzt du ein **stehendes Gate** direkt neben ein gleich hohes stehendes Gate (höchstens einen Block daneben oder
  hinein), **rastet es ein**: Pfostenmitte auf Pfostenmitte. Die Vorschau zeigt die eingerastete Lage, eine Meldung
  bestätigt es.
- Den gemeinsamen Pfosten zeichnet und spürt (Kollision) nur **eines** der beiden Gates, es gibt keinen doppelt dicken
  Pfosten mehr. Die Öffnung jedes Gates reicht wie gewohnt bis an den Pfosten.
- Auch die Lichter des gemeinsamen Pfostens kommen nur von diesem einen Gate, deshalb flimmert der Pfosten nicht
  (kein Z-Fighting). Er leuchtet in der Farbe des Gates, dem er gehört.
- Das funktioniert auch über Eck (Gates quer zueinander) und mit gestapelten Gates in jeder Reihe des Stapels
  (Abschnitt 9).
- **Gedrehte Gates** rasten genauso ein, in jeder Drehung (auch in feinen Schritten wie 15° oder 45°, siehe
  „Gate drehen: Schritt“): Stell das neue Gate mit derselben Drehung daneben, dann rastet es Pfosten auf Pfosten ein.
  Über Eck muss es genau quer stehen (90° weiter). Gates mit unterschiedlicher Drehung (etwa 30° neben 45°) und
  geneigte oder gerollte Gates rasten nicht ein.
- Gates, die sich einen Pfosten teilen, lassen sich nur **auf der Stelle um 180° drehen**; Größe, Lage und Position
  bleiben gesperrt, bis der Nachbar abgebaut ist.
- Baust du einen Nachbarn ab, bekommt das andere Gate seinen Pfosten sofort zurück.

## 3. Würfel aus Gates (Tauchwürfel ohne Sonderteil)

1. Vier stehende Gates gleicher Höhe im Quadrat aufstellen – über Eck rasten sie auf die gemeinsamen Eckpfosten ein.
2. Die Lage des Gate-Items auf **liegend** stellen (Schleichen + Drehen-Taste, wie bei einem Gate auf dem Boden, auch mit
   Blick auf eine Seite) und eine der Seiten **anklicken**: Ein liegendes Gate legt sich als **Deckel** auf die oberen
   Balken. Seine Größe passt sich dem Quadrat an, auch bei großen oder eigenen Gate-Größen.
3. Der Deckel wird **von oben nach unten** durchflogen. Jede Seite bleibt ein eigenes Gate: Nimm den Deckel und eine Seite
   als Ausgang in den Kurs; zeigt die Seite nach innen, drehst du ihren Durchgang im Kurs-Fenster mit ⇄ um (oder klickst
   sie von innen nach außen blickend an).

- Ohne quer stehenden Nachbarn sagt das Spiel „Ein Deckel braucht …“. Der fertige **Tauchwürfel** als Element bleibt
  weiterhin verfügbar.
- Balken und Ecken unter dem Deckel zeichnen und kollidieren nur einmal (der Deckel trägt sie), auch die Lichter der
  oberen Balken kommen nur vom Deckel.
- Auch ein **gedrehtes Quadrat** wird ein Würfel: Der Deckel übernimmt die Drehung der Seite, auf die du klickst.

## 4. Flaggen und Stangen auf Gates und Tauchwürfeln

- Mit **Flagge** oder **Slalomstange** in der Hand ein stehendes Gate rechtsklicken: Das Element sitzt auf der
  **Ecke** oder der **Mitte des oberen Balkens** – je nachdem, wo du klickst. Auf einem **Tauchwürfel** gibt es die vier
  Ecken und die Mitten der vier Kanten. Die Vorschau zeigt den Platz.
- **Gate-Stangen** können jetzt ebenfalls auf die Balkenmitte und auf Tauchwürfel.
- Die Richtung des Elements folgt wie am Boden deinem Blick, die Seite der Vorschau-Einstellung.
- Die Wertung beginnt wie am Boden einen Block unter dem Fuß. Soll sie durch das Gate hindurch bis zum Boden reichen,
  stellst du im Menü auf der Seite **Wertung** „nach unten“ höher (Teil A).
- Ein Gate oder Tauchwürfel mit etwas darauf lässt sich nur um 180° drehen. Baust du ihn ab, kommt der Aufsatz mit ab.

## 5. Alte Welten

- Kurse, Bestenlisten und Geister aus 0.4.0, 0.5.0 und 0.5.2 bleiben gültig: ein Kurs ohne Wiederholung und ohne
  Rückwärts-Durchgang hat dieselbe Prüfsumme wie vorher. Die Kursdatei schreibt `"reverse": true` nur, wo es gilt.
- Server und Client brauchen beide 0.6.0.

## 6. Gates und Kurs-Elemente bleiben stehen

- Kolben schieben weder Gates noch Flaggen, Slalomstangen, Tauchwürfel, Gate-Stangen oder Freiform-Gates – auch nicht
  mit Schleim- oder Honigblock. Wasser- und Lavaströmung, Explosionen, Spieler, Mobs, Loren und Boote bewegen sie
  ebenfalls nicht. Die Lage gehört zum Kurs und zu seiner Bestenliste; verschieben kannst du ein Element nur im Menü
  (Schleichen + Rechtsklick, Teil A), und das zählt wie jeder Umbau.
- Steht ein Element beim Laden nicht an der Lage aus den Kursdaten (etwa weil es vor 0.6.0 ein Kolben verschoben hat),
  setzt der Server es dorthin zurück und meldet das einmal im Server-Log. Danach sehen alle Spieler es an derselben
  Stelle, auch wer erst später beitritt.

## 7. Gestapelte Gates: Breite und Einstellen

- Ein Gate steht nur dann **auf** einem anderen, wenn es höchstens so breit ist wie das Gate darunter, und ein Stapel
  hat höchstens vier Gates. Das gilt für jeden Weg zum Stapel: Gate-Item auf ein Gate, Gate-Item auf einen Block,
  dessen Oberkante genau auf der Sitzhöhe eines Gates liegt, und Verschieben, Verbreitern, Erhöhen oder Drehen im
  Gate-Menü.
- Das Gate-Menü prüft immer die **neue** Lage: Säße das Gate so auf einem schmaleren Gate oder läge ein breiteres auf
  ihm, ist „Übernehmen“ gesperrt und das Menü sagt „Ein Gate passt nur auf ein gleich breites oder breiteres Gate“
  (bei mehr als vier Gates „Höchstens 4 Gates übereinander“). Die Vorschau beim Setzen wird dann rot, der Server lehnt
  ebenso ab.
- Gates, die schon in einem Stapel stehen, verhalten sich alle gleich: Breite, Höhe, Lage und Position bleiben gesperrt
  („Im Stapel geht nur Drehen – sonst erst den Stapel abbauen“), egal ob du das untere oder das obere
  anklickst. Drehen geht, siehe Abschnitt 8.
- Mit der Welt-Regel **„Kurs-Elemente frei platzieren“** dürfen Gates weiter ineinander stehen, etwa ein breites Gate
  knapp unter der Oberkante eines schmalen. Ein Stapel mit einem breiteren Gate auf einem schmaleren entsteht dabei nie.
- Welten aus 0.6.0-rc.1, in denen schon ein breiteres Gate auf einem schmaleren sitzt, bleiben ladbar: Das breite Gate
  gehört dann nicht zum Stapel und steht auf **eigenen Beinen** (mit eigenem unterem Balken und Füßen), das schmale hat
  wieder seinen vollen Rahmen. Beide lassen sich einzeln einstellen.

Bilder: `docs/screenshots/race_stack_rules_refused.png` (deutsch `…_de.png`) und `race_stack_rules_own_legs.png`.

## 8. Gestapelte Gates drehen

- Ein Gate auf einem Gate lässt sich um die senkrechte Achse drehen, in denselben Schritten wie ein Gate am Boden
  (Einstellung „Gate drehen: Schritt“: 90°, 45°, 15° oder 5°). Drehpunkt ist die Mitte des Balkens, auf dem es liegt.
- **Beim Stapeln:** Mit einem Gate in der Hand zeigt der Blick auf ein stehendes Gate denselben Vorschau-Geist wie am
  Boden: das neue Gate obenauf, genau so, wie der Rechtsklick es setzt – in seiner Drehung zum Gate darunter, gleich
  ausgerichtet auf dem gemeinsamen Balken oder quer mit eigenem Balken, mit Pfeil in Flugrichtung und rot, wenn es nicht
  passt. Das Hinweisfeld darunter nennt Lage und Drehung wie beim Setzen am Boden und bei Rot den Grund: „höchstens 4
  Gates übereinander“, „breiter als das Gate darunter“, „auf diesem Gate sitzt schon etwas“ oder „kein Platz“ (ein
  Block oder ein anderes Kurs-Element ist im Weg).
- **Drehung wählen:** Solange du so auf ein stehendes Gate blickst, dreht die Dreh-Taste die Vorschau in denselben
  Schritten wie am Boden, nicht das Gate im Blick; Schleichen + Dreh-Taste wechselt die Lage (liegend = Deckel, siehe
  Abschnitt 3). Es ist dieselbe Einstellung wie am Boden: Die Drehung gilt danach auch für Gates am Boden und umgekehrt.
  Ohne eingestellte Drehung steht das neue Gate wie bisher gleich ausgerichtet oder um 180° gedreht (je nach
  Blickrichtung).
- **Deckel (Lage liegend):** Blickst du so auf eine Seite eines Gate-Quadrats, zeigt der Geist den Deckel. Seine Richtung
  gibt der Würfel vor (immer von oben nach unten), deshalb ändert die Dreh-Taste hier nichts. Das Hinweisfeld nennt die
  Richtung des Deckels („Boden · liegend · abwärts“), auch wenn du am Boden „aufwärts“ eingestellt hast, und darunter
  nur „Schleichen + Taste: Lage wechseln“.
- **Danach:** Mit dem **Kurs-Werkzeug** in der Hand dreht die Dreh-Taste das gestapelte Gate im Blick, oder im Gate-Menü
  (Schleichen + Rechtsklick, Zeile „Gieren“). Alles andere (Größe, Neigung, Lage, Position) bleibt im Stapel gesperrt.
  Gates an Wand und Decke sowie liegende Gates dreht die Taste auch mit einem Gate in der Hand wie bisher.
- **Wie es aufliegt:** Gleich ausgerichtet oder um 180° gedreht teilen sich die Gates wie bisher einen Balken. Jede
  andere Drehung legt das obere Gate mit seinem **eigenen unteren Balken** quer auf den oberen Balken des Gates darunter
  – eine Rahmenstärke höher, ohne zu schweben und ohne dass sich Beine und Balken überschneiden. Drehst du es zurück auf
  0° oder 180°, sitzt es wieder auf dem gemeinsamen Balken.
- **Breite:** Auch gedreht passt ein Gate nur auf ein gleich breites oder breiteres Gate; ein Stapel hat höchstens vier
  Gates.
- **Unteres Gate drehen:** Alle Gates darüber drehen mit, ihre Drehung zueinander bleibt. Sitzt oben eine Flagge, Stange
  oder Gate-Stange, oder teilt sich ein Gate des Stapels einen Pfosten mit einem Nachbarn, dreht der Stapel nur um 180°.
- **Wertung:** Ein gedreht aufliegendes Gate zählt ab seinem eigenen Balken, das unterste Gate des Stapels weiter bis zum
  Boden. Kollision, Zielen, Gate-Menü, Kurs-Werkzeug, Kursdaten, Bestenliste und Ghosts kennen jede Drehung; die Drehung
  gehört zum Layout des Kurses (Drehen archiviert die Bestenliste wie jede Änderung).
- **Alte Welten und Kurse** bleiben unverändert: Bisherige Stapel sind gleich ausgerichtet oder um 180° gedreht und sitzen
  weiter auf dem gemeinsamen Balken.
- Baust du ein Gate aus der Mitte ab, fällt alles darüber nach und behält seine Drehung; es liegt dann so auf dem Gate
  darunter, wie es dazu gedreht ist.

Bilder: `docs/screenshots/race_stack_turn_45.png` (zwei Gates, oberes 45°), `race_stack_turn_90.png` (drei Gates,
oberes 90° quer), `race_stack_turn_menu.png` (Gate-Menü dreht das obere Gate) und `race_stack_preview.png`
(Vorschau-Geist beim Stapeln um 45° gedreht, Hinweisfeld mit Lage und Drehung).

## 9. Gestapelte Gates nebeneinander

- Stapel dürfen direkt nebeneinander stehen, ohne „Kurs-Elemente frei platzieren“: Steht neben dem Gate, auf das du
  stapelst, auf gleicher Höhe schon ein gleich hohes Gate, setzt sich das neue Gate auf dessen Pfosten. Den gemeinsamen
  Pfosten zeichnet und spürt nur eines der beiden Gates, wie in einer Reihe am Boden (Abschnitt 2).
- Das gilt in jeder Stapelhöhe bis vier, egal welcher Stapel zuerst wächst und auf welcher Seite der Nachbar steht, und
  über Eck bei quer stehenden Stapeln. Unterschiedlich hohe Stapel nebeneinander gehen ebenso; oberhalb des niedrigeren
  Nachbarn hat das Gate wieder seinen eigenen Pfosten.
- Die Vorschau ist grün und zeigt das Gate auf dem geteilten Pfosten; nach dem Klick meldet das Spiel wie am Boden
  „Gate auf den Pfosten des Nachbarn eingerastet“.
- Echte Hindernisse bleiben „kein Platz“: ein Block im Rahmen, ein Kurs-Element oder ein Nachbar, dessen Pfosten nicht
  genau auf dem eigenen liegt (anders hoch, anders groß, schräg gedreht).
- Ein fünftes Gate auf einem Stapel geht nicht: Die Vorschau ist rot mit „höchstens 4 Gates übereinander“, der Klick
  meldet dasselbe.
- Kurs-Werkzeug, Wertung und Rundenzählung behandeln jedes Gate eines solchen Gitters einzeln: Ein Klick auf den Rahmen
  trifft das Gate, zu dem der Rahmen gehört (den geteilten Pfosten trägt das westliche bzw. nördliche Gate), und eine
  Runde zählt nur das Gate, durch dessen Öffnung du fliegst.

Bilder: `docs/screenshots/race_topo_stack_2x2.png` (zwei Stapel zu je zwei Gates mit geteiltem Pfosten),
`race_topo_row_3x1.png` (drei Gates in einer Reihe), `race_topo_stack_heights.png` (Stapel der Höhe 4, 3, 2 und 1
nebeneinander), `race_topo_stack_corner.png` (zwei Stapel über Eck) und `race_topo_stack_limit.png` (fünftes Gate
abgelehnt, deutsch `…_de.png`).
