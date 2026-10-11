# Ideallinie und Gate-Markierungen (race)

Anforderung: Bei Rennen verbindet eine Linie die Tore miteinander, damit man sieht, wo es langgeht – aber nur bei einem
gestarteten Rennen. Dazu: Start-/Ziel-Gates mit eigenem Look, jedes
Kurs-Gate mit Flugrichtung und Nummer.

## Sichtbarkeit

- Die Rennlinie gibt es, solange `LapTracker#racing()` gilt, also nach dem Startsignal des Countdowns oder dem
  fliegenden Start durch Gate 1, bis zum Ziel oder Abbruch (`RESTART`, `TIMEOUT`, `LEFT_COURSE`, `COURSE_CHANGED`,
  `SESSION_ENDED`).
- **Vor dem Start** (Phase `IDLE` oder Countdown, Pilot-Sitzung aktiv, Drohne höchstens 48 Blöcke vom Start-Gate)
  führt dieselbe Linie von der Drohne durch das Start-Gate und weiter (`RaceLine.approach`); das Start-Gate leuchtet
  grün. So passt das Bild zum Text „Flieg durch das Start-/Ziel-Gate, um die Uhr zu starten“. Im Bau-Modus mit dem
  Kurs-Werkzeug, weit weg vom Kurs und nach dem Ziel ist sie weg.
- **Sichtlinie:** Ein Raycast von der Drohne zur Mitte des nächsten Gates prüft jedes Bild, ob Blöcke dazwischen
  liegen; dann blendet die ganze Linie in 0,2 s aus (und wieder ein, sobald das Gate frei sichtbar ist). Die Linie
  läuft so nie sichtbar durch Fels, etwa aus einer Höhle neben dem Kurs.
- Rennzustand ist rein clientseitig (`ClientRaceTracker`): jeder Pilot sieht nur die Linie seines eigenen Rennens,
  andere Spieler sehen nichts davon.
- Einstellungen im Tab „Rennen“ (`RaceConfig`): `raceLine` (Standard an) und `raceLineGates` (1–8, Standard 3). F1
  (HUD aus) blendet die Linie wie den Homepoint-Marker aus.

## Verlauf (`race.RaceLine`, reines Java, JUnit `RaceLineTest`)

- Punkte = Mitte der Gate-Öffnung (`GateGeometry#centerY`), Reihenfolge = Kursreihenfolge ab dem nächsten Gate
  (`order`, mit Umlauf). Zwischen zwei Gates ein kubischer Hermite-Abschnitt; die Tangente an jedem Gate ist die
  Flugrichtung des Gates (`normalX/normalZ`, waagerecht) mal Sehnenlänge × `TANGENT_SCALE` (0,8). Damit läuft die
  Linie durch jede Öffnung senkrecht in Pfeilrichtung, glatt statt Zickzack, und steht ein Gate falsch herum, macht
  sie genau die Schleife, die man fliegen muss.
- Anfang: ein eigener Abschnitt von der Drohne (Mitte der Box, interpoliert) zum nächsten Gate, Start-Tangente halbe
  Sehne Richtung Gate. Er blendet über die ersten `LEAD_IN_FADE` = 3,5 Blöcke ein, damit im FPV nichts vor der Linse
  hängt, und ist grün hervorgehoben (`highlight` 1 bis zum nächsten Gate, danach über 0,2 Gate-Längen weich weg).
- Vorausschau: `min(N, verbleibende Gates)` Gates. Gibt es danach noch Gates, folgt ein Schwanz bis 45 % des nächsten
  Abschnitts, der auf 0 ausblendet; in der letzten Runde endet die Linie deckend im Ziel-Gate. Mit der Entfernung (in
  Gates) wird sie bis 35 % dunkler.
- `remainingGates(n, runde, rundenziel, nächstes, holeshot)` zählt die noch zu fliegenden Durchflüge einschließlich
  Ziel (Holeshot beim stehenden Start zählt mit), `passedGates` die schon geflogenen. Dahinter zeigt die Linie
  höchstens einen Abschnitt schwach (`BEHIND_ALPHA` 0,3), nie vor den Start zurück.
- Abtastung alle ≤ 0,4 Blöcke (obere Schranke über das Bézier-Kontrollpolygon), höchstens 320 Schritte je Abschnitt.

## Darstellung (`race.client.RaceLineRenderer`)

- Angemeldet an `ClientBus.SUBMIT_WORLD_GEOMETRY`, kamerarelativ, ein `submitCustomGeometry` mit `RenderTypes.eyes`
  (Vanilla-Pipeline `EYES`: voll hell, Tiefentest ohne Tiefenschreiben, durchscheinend, Nebel). Dadurch verdecken Blöcke
  die Linie, die Linie verdeckt nichts, und der Video-Effekt (Post-Pass bzw. Ersatz-Overlay) liegt darüber.
- Band aus Kern (0,07 Blöcke breit) und Schein (0,3 Blöcke, 22 %), je Punkt zur Kamera gedreht (Seite =
  Tangente × Blickrichtung), beide Windungen, damit Backface-Culling nichts wegnimmt. Dazu Pfeil-Chevrons alle 2,4 Blöcke,
  die mit 3,2 Blöcken/s in Flugrichtung wandern (Rennuhr, steht in der Pause). Farbe hellblau `0xA8E6FF`, bis zum
  nächsten Gate im Grün des nächsten Gates.
- Nahe an der Kamera (unter 0,9 Blöcke, weich bis 3,1) und ab 176 Blöcken blendet sie aus.
- **Shaderpacks (Iris/Oculus):** Die Linie ist normale Entity-Geometrie mit einem Vanilla-Render-Type; Iris zeichnet
  `eyes` mit seinem Programm für leuchtende Entity-Teile (wie Spinnenaugen), also mit Tiefe und leuchtend. Nicht im
  Spiel mit Iris geprüft (keine Iris-Quellen in `.ref`). Mit „Verbesserte Transparenz“ (OIT) schneidet die Vanilla-Pipeline
  Pixel unter 10 % Deckkraft ab, die weichen Enden werden dann etwas kürzer.
- Die Action-Cam-Aufnahme zeigt die Linie mit (sie ist Teil des Weltbilds).

## Gate-Markierungen (`race.client.GateRenderer`)

- `GateRenderer.marking(gate)`: `NONE` ohne Kurs, `START` für Kursindex 0, sonst `NUMBER`. Da `gateIndex` synchronisiert
  ist, folgen alle Markierungen dem Umnummerieren mit dem Kurs-Werkzeug sofort; Gates ohne Kurs bleiben schlicht.
- **Flugrichtung = Gate-Normale** (`GateGeometry#normalX/normalZ`, Blickrichtung beim Setzen). Die Rundenlogik zählt nur
  Durchflüge in diese Richtung (`GateGeometry#crossing(…, forward = true)` im `LapTracker` und im `RaceServer`); ein
  Durchflug von der Rückseite durch das nächste Gate meldet „falsche Richtung“ und zählt nicht.
- Einflugseite: drei flache Leucht-Chevrons am Boden 0,7–3 Blöcke vor dem Gate, Spitze zum Gate, Breite bis 2,2 Blöcke,
  ein Lichtlauf wandert zum Gate hin (`chevronBrightness`), Farbe = Gate-Licht (grün = nächstes Gate).
- Nummern-Tafel (nicht beim Start-Gate): 1 × 0,8 Blöcke (klein 0,9 breit) mittig auf dem oberen Balken, dunkel mit
  leuchtendem Rand in Gate-Farbe; Einflugseite Nummer 0,5 Blöcke hoch in Gate-Farbe, Rückseite klein in Grau.
- Rückseite: rote Leuchtquadrate auf den oberen Eckverbindern und ein roter Strich an der Tafel.
- Start-/Ziel-Gate: Modelle `gate/<größe>_start_frame` (Form wie der Rahmen, Textur `item/gate/start_frame`, schwarz-weiß
  kariert) und `gate/<größe>_banner` (1 Block hohes Banner auf dem oberen Balken, karierte Enden und Streifen, dunkle
  Mitte), Aufschrift `propwash.race.gate.banner` („START · ZIEL“) auf beiden Seiten, dazu ein pulsierender Lichtschein
  innen um die Öffnung auf beiden Seiten. Fehlen die Modelle, zeichnet der Renderer ein schlichtes Banner.

## Prüfung

- JUnit `race.RaceLineTest`: Zählung der verbleibenden/geflogenen Gates, Reihenfolge mit Umlauf, Linie trifft jede
  Gate-Mitte in Kursreihenfolge, läuft in Pfeilrichtung (und `crossing` zählt den Durchflug entlang der Linie), kein Knick
  über 15° je Abtastschritt, Einblenden an der Drohne, Ausblenden nach N Gates, Ende im Ziel-Gate, schwacher Rest dahinter,
  Lebenszyklus (vor dem Start, Countdown, Rennen, Abbruch, Ziel), falsche Seite zählt nicht.
- Selbsttest „race: Gate-Markierungen folgen der Nummerierung“ (Start-Gate mit kariertem Rahmen und Banner, Nummern,
  Gate aus dem Kurs genommen → unmarkiert und die anderen rücken nach, Reihenfolge wiederhergestellt) und
  „race: Ideallinie nur im gestarteten Rennen“ (ohne Rennen und im Countdown höchstens zum Start-Gate, im Rennen ab der Drohne durch
  die Mitte von Gate 2 und höchstens N Gates weit, Schalter aus blendet aus, nach dem Abbruch weg).
- Bildschirmfotos (Rundgang): `race_gates.png` (vor dem Start), `gate_start_finish.png`, `gate_markings.png`, `gate_exit_side.png`,
  `race_line_third_person.png` (Sichtflug im Rennen, dritte Person), `race_line_fpv.png` (FPV im Rennen); die Szenen
  schlagen fehl, wenn die Linie im Rennen nicht gezeichnet wird.
