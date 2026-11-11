# Geister und Replays der Bestenliste

Für Spieler, Server-Admins und Entwickler. Ab 0.6.0 zeichnet der Server für jede Zeit in der Bestenliste die Flugbahn
selbst auf und merkt sich dazu den Sharecode der Drohne, die sie geflogen ist. In der Bestenliste siehst du je Zeit den
Sharecode, kannst die Runde als Replay aus der FPV-Ansicht ansehen und ihren Geist als Gegner für deine nächsten Runden
wählen. Admins sehen sich bei Events die Replays an, um auffällige Zeiten zu prüfen, und löschen Zeiten.

Grundlagen: [`race-system.md`](race-system.md) (Wertung, Flughilfe), [`race-rules.md`](race-rules.md) (Rennstandard),
[`race-data.md`](race-data.md) (Renndaten im Weltordner).

## 1. Bestenliste: Sharecode, Replay und Geist

Bestenliste öffnen (Kurs-Werkzeug Rechtsklick in die Luft → Kurs-Einstellungen → „Öffnen“ bei Bestenliste, oder Tab „Rennen“ →
„Bestenliste öffnen“) und eine Zeit
anklicken. Unter der Liste erscheint:

- **Sharecode** der Drohne, die die Zeit geflogen ist (PW3, vollständig mit Lackierung, Tuning und Namen). Mit der Maus
  darüber zeigt die Vorschau wie bei Codes im Chat Bild, Teile und Gewicht; ein Klick auf den Code oder **Kopieren**
  legt ihn in die Zwischenablage, **Importieren** öffnet ihn im Tab „Sharecode“, dort lässt sich die Drohne bauen.
  Zeiten von vor 0.6.0 und übernommene Rekord-Geister haben keinen Code.
- **Replay**: die Runde aus der Sicht der Drohne (Abschnitt 2).
- **Gegen fliegen**: dieser Geist fliegt in deinen nächsten Runden auf dem Kurs mit (Abschnitt 3). Die gewählte Zeit
  trägt die Marke „Geist“; derselbe Knopf heißt dann **Eigener Geist** und schaltet zurück.
- **Zeit löschen** (nur Operatoren, Abschnitt 5).

Ist ein Knopf grau, nennt sein Tooltip den Grund: keine Aufzeichnung zu dieser Zeit, Welt-Regel, oder du fliegst gerade
(Replays gehen nicht während einer FPV-Sitzung, der freien Kamera oder beim Zuschauen).

## 2. Replay

Das Replay zeigt das Bild der Drohnenkamera mit der Linsenlage, dem Kamerawinkel und dem Sichtfeld der Drohne, die
die Zeit geflogen ist (ohne Fisheye und Bildstörungen), das HUD ist ausgeblendet. Welche Spur es abspielt – die des
Piloten oder die des Servers –, steht im Abschnitt „Woher die Spur kommt“ weiter unten; die Zeit kommt immer vom Server.

| Bedienung | Wirkung |
|---|---|
| **Abspielen / Pause** (Leertaste) | Wiedergabe anhalten und fortsetzen; am Ende beginnt sie von vorn |
| **«** / **»** (Pfeil links/rechts) | 5 Sekunden zurück bzw. vor |
| **Tempo** | 0,25×, 0,5×, 1×, 2×, 4× |
| Zeitleiste | Klicken oder Ziehen springt an die Stelle; je Gate des Kurses eine Marke: weiß = Gate-Durchgang, grün = Startgate und bei Punkt-zu-Punkt-Kursen das Ziel (bei Runden-Kursen ist das Startgate zugleich das Ziel, das Ende der Leiste) |
| **FPV / Hinter der Drohne** (Taste V) | FPV wie in der Brille; dahinter folgt die Kamera der Drohne von hinten, so lassen sich Abkürzungen beurteilen |
| **Zurück** (Esc) | zurück zur Bestenliste |

Oben links laufen Rundenzeit und Zielzeit mit. Operatoren haben im Replay auch **Zeit löschen**. „Gates: N“ über der
Zeitleiste zählt Start- und Ziel-Gate mit und nennt dieselbe Zahl wie Kurs-Einstellungen und Bestenliste. Findet das Replay
einen Durchgang in der Spur nicht, fehlt nur dessen Marke, die Zahl bleibt.

Das Replay beginnt bis zu 1 Sekunde **vor dem Startgate**, damit du siehst, wie die Drohne hindurchfliegt. In dieser
Zeit steht die Uhr auf 0:00.000; sie läuft erst ab dem Gate-Durchflug, die Rundenzeit bleibt dieselbe. Zeitleiste,
Spulen und der Neustart am Ende schließen den Vorlauf ein, die grüne Marke am Anfang ist das Startgate. Ab der zweiten
Runde ist der Vorlauf die letzte Sekunde der Runde davor. Er ist kürzer oder fehlt, wenn der Server vorher weniger
gesehen hat: direkt nach dem Einsetzen oder Teleportieren der Drohne und bei einem Start aus dem Stand ohne Bewegung.
Zeiten, die vor diesem Update gespeichert wurden, beginnen wie bisher am Startgate. Als Gegner im Rennen fliegt ein
Geist weiter erst ab dem Startgate los.

Fliegst du vor dem Ende der Runde noch einmal durch das Startgate (etwa nach einer Kehre oder einer Schleife zurück),
beginnt die Uhr nicht neu, sie läuft weiter. Replay, Video, der Geist im Rennen und die Aufnahme, die dein Spiel dem
Server schickt, beginnen trotzdem am **ersten** Durchflug, mit dem die Uhr losgelaufen ist, und enden im Ziel: Das
Replay dauert so lang wie die Zeit in der Bestenliste, und die Uhr im Replay zeigt am Ende genau diese Zeit. Das gilt
für Rundkurse, Schleifen und Punkt-zu-Punkt-Kurse und in jeder Runde eines Rennens; ab der zweiten Runde beginnt die
Runde am Ziel-Durchflug der Runde davor, egal wie oft du danach noch durch das Startgate fliegst. Auch die Flughilfen
prüft der Server über genau diese Zeit.

### Woher die Spur kommt

Bei schlechter Verbindung des Piloten kommen seine Bewegungspakete beim Server in Schüben an: erst eine Pause, dann
mehrere auf einmal. Eine Spur nur aus diesen Paketen hätte im Replay kurze Standbilder und Sprünge. Deshalb gilt seit
Netzprotokoll 20:

- **Der Pilot nimmt jede Runde selbst auf**, 100 Proben je Sekunde (Raster 10 ms, aus jedem Bild seines Spiels), und
  behält die letzten vier Runden im Speicher. Position und Lage kommen beide aus der Flugsimulation seines Spiels, nicht
  aus dem, was der Server je Tick zurückmeldet; so laufen Replay, Video und Geist auch bei 60 fps und mehr ohne Stufen
  im 20-Hz-Takt.
- **Kommt eine Zeit in die Bestenliste**, bittet der Server den Piloten um diese Runde. Der Client schickt sie im
  Hintergrund in Stücken zu 8 KiB (im laufenden Rennen nur jeden vierten Tick, sonst jeden Tick; eine Minute Runde sind
  meist 10–60 KB) und höchstens zwei Runden auf einmal. Am Rennen ändert das nichts: Runden, Gates und Zeiten zählt der
  Server wie bisher selbst, der Upload kommt erst nach seinem Urteil.
- **Der Server prüft die Spur gegen seine eigene** und nimmt sie nur, wenn alles passt: Dauer gleich der Rundenzeit,
  Tempo höchstens 165 Blöcke je Sekunde (keine Teleports), Start, jedes Gate in Reihenfolge zur selben Zeit wie in
  seiner Spur, Ziel, Spurlänge, und jede seiner Proben liegt nahe der Spur des Piloten und umgekehrt. Die Toleranz
  wächst mit dem Ping (höchstens 300 ms angerechnet) und dem Abstand seiner Bewegungspakete, an einer Pause um deren
  Länge. Weil sich hinter einem verlorenen Paket die nächsten stauen, können auch die Proben bis eine Pausenlänge nach
  der Pause noch so alt sein wie die Pause; für sie gilt dieselbe Zugabe nach hinten (je Pause höchstens 1,5 s), auch
  für die Zeit an jedem Gate. Der Abstand zur Spur bleibt höchstens 1,5 Blöcke, auch im Renntempo. Liegen seine Proben
  400 ms am Stück oder länger weiter weg, gilt das als Umweg, auch wenn es weniger als 2 % aller Proben sind. Im
  Renntempo (35 und 40 Blöcke je Sekunde) mit 6 % Verlust und Stockungen von 400 bis 600 ms nimmt der Server echte
  Runden an, ein Umweg von 2 s nicht.
- **Passt sie**, zeigen Replay, Video und der Geist im Rennen von da an die Spur des Piloten (Herkunft „Pilot“ in der
  Ablage); Aussehen, Klasse, Kamerawinkel, Regeln und vor allem die Zeit bleiben die des Servers, der Vorlauf höchstens so
  lang wie in der Server-Spur. **Passt sie nicht oder kommt sie nicht** (Abbruch, Verbindung weg, Runde nicht mehr im
  Speicher), bleibt die Spur des Servers. Bestzeit und Bestenliste hängen nie an der hochgeladenen Spur; wer fälscht,
  kann höchstens ein anderes Bild derselben echten Runde liefern.

Die Spur des Servers hat 50 Proben je Sekunde, die des Piloten 100 (Runden über 4 Minuten bzw. 2 Minuten etwas weniger,
höchstens 12 001 Proben einschließlich Vorlauf; reicht der Platz nicht, wird zuerst der Vorlauf kürzer). Der Server
sieht die Drohne so oft, wie ihr Pilot Bewegungspakete schickt; schwankende Ankunftszeiten glättet er vor dem Schneiden.
Sind die Chunks am Kurs beim Zuschauer nicht geladen, fliegt das Replay durch leere Welt – am besten in der Nähe des
Kurses öffnen.

### Als Video aufnehmen

**Als Video aufnehmen** (oben rechts im Replay) macht aus der Zeit ein MP4: Das Replay springt an den Anfang des Vorlaufs,
spielt die Runde einmal mit 1× in der gewählten Ansicht (FPV oder hinter der Drohne) ab und nimmt sie über den Rekorder
der Action-Cam auf.

- **Format:** Auflösung, Bildrate und Qualität aus dem Tab *Action-Cam*, ohne Bildstabilisierung und ohne Ton (ein Geist
  macht keine Motorgeräusche). Encoder wie bei der Action-Cam; die Einstellung *Encoder* gilt auch hier.
- **Datei:** `propwash_replay_<Datum>_<Uhrzeit>.mp4` im Action-Cam-Ordner `propwash/recordings/`, sie erscheint auch in der
  Aufnahmen-Liste. Das Video ist so lang wie das Replay, also Runde plus Vorlauf. Fenster, Zeitleiste und Mauszeiger sind
  nicht im Bild.
- **Während der Aufnahme:** Die Zeitleiste läuft als Fortschritt rot mit, daneben stehen Ansicht, Format und Zeit. Die übrige
  Bedienung ist gesperrt. Sinkt die Bildrate des Spiels, wiederholt das Video Bilder; Drohne und Uhr bleiben im richtigen
  Takt, die Länge stimmt. Jedes Videobild zeigt genau seinen Zeitpunkt im Video (bei 60 fps alle 16,7 ms Replay-Zeit),
  auch wenn das Spiel mit einer Bildrate läuft, die kein Vielfaches davon ist (etwa 75, 90 oder 144 fps).
- **Abbrechen:** **Esc** oder **Abbrechen (Esc)** beendet die Aufnahme und löscht sie, das Replay bleibt offen. Wer das
  Replay auf anderem Weg verlässt oder die Verbindung verliert, bricht die Aufnahme ebenfalls ab.
- **Danach:** Das Replay steht am Ende, die Zeile über der Zeitleiste und eine Meldung nennen die Datei. Bis das Video
  fertig gespeichert ist, bleibt der Knopf aus.

Ohne ffmpeg ist der Knopf aus, sein Tooltip nennt den Weg zur Installation
([`recording-ffmpeg.md`](recording-ffmpeg.md)). Dasselbe gilt, wenn das gefundene ffmpeg nicht startet, und solange die
Prüfung läuft. Grau ist er auch, solange die Action-Cam aufnimmt oder das Replay noch lädt.

## 3. Geist im Rennen

Im Rennen fliegt immer genau **ein** Geist mit, die Zwischenzeiten zeigen den Abstand zu ihm. Einstellung im Tab
„Rennen“ → **Geist**:

| Wert | Geist |
|---|---|
| eigener (Standard) | deine persönliche Bestrunde mit der Drohnenklasse (blau); in Runden mit Flughilfe dein Geist mit Flughilfe (violett), wenn die Welt solche Runden speichert |
| aus Bestenliste | der Kursrekord deiner Drohnenklasse (golden) |
| aus | kein Geist; die Zwischenzeiten messen weiter gegen deine Bestrunde |

Mit **Gegen fliegen** in der Bestenliste wählst du für einen Kurs eine bestimmte Zeit (golden, auch aus einer anderen
Klasse). Die Wahl gilt je Kurs und Welt bzw. Server und bleibt nach einem Neustart erhalten; ohne Wahl gilt die
Einstellung. Der frühere zusätzliche Rekord-Geist fliegt nicht mehr mit; die alten Schalter „Geist der Bestrunde“ und
„Rekord-Geist“ werden beim ersten Start übernommen (Bestrunde an → eigener, nur Rekord an → aus Bestenliste, beide aus →
aus). Ist die gewählte Zeit nicht mehr da oder nicht erlaubt, fliegt dein eigener Geist mit.

## 4. Welt-Regel „Fremde Geister und Replays“

`/gamerule propwash:foreign_ghosts <0|1|2>`, Tab „Welt-Regeln“, Gruppe Spiel; rennneutral.

| Wert | Replay fremder Zeiten | Gegen fremde Geister fliegen |
|---|---|---|
| Alle (2, Standard) | alle | alle |
| Admins (1) | nur Operatoren | nur Operatoren |
| Aus (0) | nur Operatoren | niemand |

Die eigenen Zeiten gehen immer. Der Server prüft bei jedem Abruf; die Bestenliste graut die Knöpfe passend aus.

## 5. Zeiten löschen (Admins)

Nur Operatoren (im Einzelspieler und im LAN der Gastgeber). Im Fenster: Zeit anklicken → **Zeit löschen** → **Wirklich
löschen?** innerhalb von 3 s noch einmal klicken; dasselbe im Replay. Per Befehl:

```
/propwash race remove <kurs> <pilot> [klasse]
```

Gelöscht werden alle Zeiten des Piloten in der Klasse (ohne Klasse: in allen Klassen; im Fenster nur im Abschnitt der
Zeile, also mit oder ohne Flughilfe) samt Aufzeichnung. Die Liste rückt nach, ein Rekord geht an die nächstbeste Zeit.
Jede Löschung steht als Zeile in `<welt>/propwash/race-data/removals.log`:

```
2026-10-25T10:12:03Z removed by=Admin(…uuid…) via=window course=3 key=<kurs-id> pilot=Ben(…uuid…) class=race_5 section=clean times=1 best=12.345
```

## 6. Ablage im Weltordner

```
<welt>/propwash/race-data/ghosts/<kurs-id>/<spieler-uuid>_<klasse>_<zeit>_<datum>.pwghost
<welt>/propwash/race-data/ghosts/<kurs-id>/archive/<layout>/…
<welt>/propwash/race-data/removals.log
```

- Eine Datei je gespeicherter Zeit (Deflate, Kopf `PWE1` mit Pilot, Klasse, Zeit, Datum, Flughilfe, Layout, Kamerawinkel,
  Sharecode, Herkunft, dazu die Spur im Format `PWG1`; Spuren mit Vorlauf in Version 4 mit der Länge des Vorlaufs, ältere
  Versionen ohne dieses Feld laufen ab dem Startgate). Eine 60-Sekunden-Runde braucht etwa 10–20 KB; höchstens 528 KiB
  je Datei, eine Spur, die mit Vorlauf zu groß würde, wird ohne Vorlauf gespeichert.
- Verlässt eine Zeit die Liste (bessere Zeit, Liste voll, gelöscht), verschwindet ihre Datei. Ein Layout-Wechsel
  verschiebt die Geister nach `archive/<layout>/` und behält so viele alte Layouts wie die Bestenliste (fünf). Wird der
  Kurs gelöscht, verschwindet sein Ordner.
- Rekord-Geister aus 0.5.x werden beim Start übernommen, wenn ihre Zeit noch in der Liste steht und das Layout passt
  (ohne Sharecode und Kamerawinkel; das Replay nimmt dann 20°).
- Der Abruf läuft in Stücken zu 24 KiB, höchstens 2 Stücke je Tick und Spieler, höchstens 6 Abrufe je 10 Sekunden.
- Die Spur des Piloten kommt in Stücken zu 8 KiB, höchstens 512 KiB, höchstens 30 Stücke je Sekunde und Spieler (mehr
  bricht den Upload ab), höchstens zwei offene Uploads je Spieler, jeder verfällt nach 120 Sekunden. Herkunft in der
  Datei: `SERVER` (eigene Spur), `RECORD_GHOST` (übernommen aus 0.5.x) oder `PILOT` (Spur des Piloten, geprüft).

## 7. Für Entwickler

Klassen, Pakete und Tests: `docs/FOUNDATION.md`, Abschnitt „Geister und Replays der Bestenliste“. Die API (`api.race`)
ist unverändert; Geister und Sharecodes stehen nicht in den Renndaten.
