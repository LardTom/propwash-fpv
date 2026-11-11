# Signal: Gelände-Gedächtnis

Für Spieler. Bis 0.5.2 zählten Chunks, die dein Spiel gerade nicht geladen hat, beim Signal als freie Luft. Das betraf
Video, Steuer-Signal und Repeater-Strecken, sobald die Drohne weiter weg war als deine Sichtweite oder die Sicht der
Drohne folgte: Hügel, Wälder und Häuser zwischen dir und der Drohne schirmten dann nicht mehr ab. Ab 0.6.0 merkt sich
Propwash das Gelände jedes Chunks, den du gesehen hast, und rechnet damit weiter, wenn der Chunk nicht mehr geladen ist.
Nie gesehenes Gelände zählt weiter als offen.

Technik und Entscheidungen: [`design/0.6.0/fixes-polish.md`](design/0.6.0/fixes-polish.md) Punkt D (§3.D, §6.5,
E14, E15), Grenzen der Signal-Rechnung in [`design/signal-repeater.md`](design/signal-repeater.md) §8.

## 1. Was gemerkt wird

Je Chunk 16 × 16 Säulen, jede mit drei Werten (etwa 1 KB je Chunk):

| Wert | Bedeutung |
|---|---|
| Oberkante | höchster fester Block der Säule; alles darunter zählt als fest |
| Material | Funk-Material dieses Blocks (Erde, Sand, Holz, Mauerwerk, Wasser …); liegt oben Schnee oder Eis, das erste andere Material bis 4 Blöcke tiefer |
| Laubdach | Höhe des obersten Laubs über der Oberkante; dazwischen zählt alles als Laub |

Gesucht wird ab der Höhenkarte „bewegungshemmend“, die der Server ohnehin schickt, höchstens 32 Blöcke abwärts. Dabei
zählen Luft, Laub, Pflanzen und dünne Blöcke mit weniger als einem halben Block Inhalt nicht als Oberkante:
Schneeschichten, Teppiche, Zäune, Gitter, Scheiben, Schienen, Bambus. Halbe Stufen und Treppen zählen.
Liegt das Laub höher als 32 Blöcke über dem Boden (sehr hohe Bäume), sucht Propwash ab der Höhenkarte ohne Laub weiter.

## 2. Wann gemerkt und wann vergessen

- Gemerkt wird, wenn das Spiel einen Chunk verwirft – weil du dich entfernst, weil die Sicht der Drohne folgt oder weil
  du teleportiert wirst. Das kostet je Chunk im Mittel 0,01 bis 0,03 ms (gemessen im Selbsttest).
- Höchstens 4 096 Chunks (rund 4 MB); ist das Gedächtnis voll, fällt der am längsten nicht gebrauchte Chunk heraus.
- Beim Wechsel der Welt, des Servers oder der Dimension ist das Gedächtnis leer. Auf der Festplatte wird nichts
  gespeichert; nach einem Neustart beginnt es leer.
- Geladene Chunks haben immer Vorrang. Kommst du zurück, gilt wieder der echte Chunk; gemerkt wird er beim nächsten
  Verwerfen neu.

## 3. Was bleibt

Nur dein Spiel rechnet damit. Kein neues Netzpaket, keine Änderung am Server, an Welten, Kursen, Bestenlisten, Ghosts,
Share-Codes oder der Add-on-API. Ein Server mit 0.6.0 braucht dafür nichts.

## 4. Grenzen

- Nie gesehenes Gelände zählt als Luft. In der Praxis ist der Flugkorridor bekannt: Folgt die Sicht der Drohne, war
  jeder Chunk um ihre Bahn einmal geladen.
- Gemerktes Gelände kann veraltet sein (jemand baut dort ab oder an), bis du den Chunk wieder lädst.
- Ein Haus zählt als ein Block aus dem Material seines Dachs bis zum Boden. Weil Wände aus Holz, Mauerwerk und Beton
  höchstens 2 m Lauf zählen, schirmt ein gemerktes Haus meist etwas weniger ab als geladen mit zwei Wänden (Beispiel im
  Test: 9 dB statt 15 dB). Hügel, einzelne Wände und Wald liegen innerhalb von 3 dB des geladenen Werts.
- Höhlen, Überhänge und Tunnel unter der Oberkante zählen als fest.
- Mehrwege-Störungen am Piloten rechnen mit der Säule; Wasser, Glas, Eis und Laub reflektieren dabei so schwach wie
  geladen.

## 5. Prüfung

JUnit `video.link.TerrainColumnsTest` (Kodierung, Säulen über Pflanzen, Teppichen, Schnee, Eis und Laub, Verlust
gemerkt gegen geladen) und `video.link.TerrainCacheTest` (LRU, Obergrenze, Leeren); Selbsttests „signal: …“ in
`video.client.SignalMemorySelfTests` (beide Loader); Beitrittstest `PROPWASH_JOINTEST_SIGNAL=1` (Fall `signal`,
`dev.SignalJoinCheck`). Der Beitrittstest trennt erst, wenn nach dem Wegschicken alle Chunks am neuen Ort angekommen
sind (`signal_stream=settled`), sonst meldet der Server beim Trennen „Connection reset by peer“. Befehle in
[`testing.md`](testing.md) §1 „Signal-Gedächtnis“ und §3.

## 6. Englische Kernsätze (Website)

- Propwash remembers terrain you have seen: hills, forests and buildings keep blocking video and control link when they
  are too far away to stay loaded.
- Terrain you have never seen still counts as open air.
- Nothing is saved to disk and nothing changes on the server; loaded chunks always win over remembered ones.
- A remembered building counts as one block of its roof material, so it may block a little less than when it is loaded.
