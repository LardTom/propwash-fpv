# RX-Block – ein Kanal der Funke wird Redstone

Stand 0.6.0. Gesamtentwurf in `docs/design/0.6.0/gameplay.md` §3.C (Entscheidungen E16–E24, E36). Dieses Dokument
beschreibt, was gebaut ist, und nennt die Stellen, an denen die Umsetzung vom Entwurf abweicht.

## Verhalten

- Block `propwash:rx_block` (Paket `rx`), Gehäuse 12 × 6 × 12 Pixel mit zwei Antennen und einer LED oben.
- An einen **Spieler** gebunden, nicht an eine Funke. Genau ein Kanal je Block: logischer Knüppel (Roll, Nick, Gier,
  Gas nach Zuordnung und Kalibrierung), rohe Achse 1–32 („AUX-Achse“) oder Taste 1–32.
- Ausgabe Stärke 0–15 nur nach vorne, wie beim Repeater: Beim Setzen zeigt der Ausgang in die Blickrichtung des
  Spielers (waagrecht, Blockzustand `facing`, `output=front`). Vorne liegt das Signal schwach und stark an, ein Block
  davor leitet es also weiter; an den anderen fünf Seiten ist nichts, Redstone-Staub verbindet sich nur vorne. Blöcke,
  die vor dieser Änderung gesetzt und gespeichert wurden, laden mit `output=all` und `facing=north` und senden wie
  bisher schwach an alle sechs Seiten, wie ein Tageslichtsensor (siehe Abweichungen). Keine eigene Komparator-Ausgabe. Die Stärke steckt im Blockzustand `power`, die LED im Zustand `link` (`none` aus, `linked` grün,
  `lost` rot). Oben zeigt ein Anzeige-Balken mit 15 Feldern die Stärke: je Stufe ein Feld mehr (von der LED-Seite aus
  gesehen von links nach rechts), das Leuchten des Balkens ist gleich der Stärke (`light_emission` 0–15).
- Reichweite 1–128 Blöcke (Standard 64). Außer Reichweite, ohne Werte von der Funke oder bei entladenem Chunk behält der
  Block seine letzte Stärke.
- Drohne und RX-Blöcke folgen gleichzeitig denselben Knüppeln. Die Tastatur zählt nur in einer Flug-Sitzung (sonst
  läuft man mit ihr).

## Rechnung (reines Java, JUnit)

| Klasse | Inhalt |
|---|---|
| `RxSource` | Record `(Kind kind, int index)`, `STICK` 0–3, `AXIS` 0–31, `BUTTON` 0–31; Codec `{kind, index}`, Stream-Codec 2 Byte; Standard-Endpunkte je Art (Knüppel und Achse −1/1, Taste 0/1) |
| `RxMapping` | t = (v − tief) / (hoch − tief), begrenzt auf 0–1; Stärke = runden(16 · t) bis zur Mitte, darüber runden(8 + 14 · (t − 0,5)): die Mitte ist immer Stufe 8, darunter liegen 0–7, darüber 9–15 in gleich großen Stufen (die Endstufen 0 und 15 halb so breit, weil gerundet wird). Mit den Standard-Endpunkten ist Stufe 8 der Bereich −0,0625 bis 0,071 der Knüppelstellung, das Kanal-Totband (Knüppel melden darin genau 0) liegt also immer darin. tief > hoch dreht um; tief = hoch ist ein Schalter (v ≥ tief → 15); keine Hysterese, dieselbe Stellung gibt aus jeder Richtung dieselbe Stufe; kaputte Werte halten die bisherige Stärke |
| `RxOutput` | `front` oder `all`: Signal schwach und stark nur nach vorne bzw. schwach an alle Seiten, Staub-Verbindung nur vorne bzw. überall |
| `RxLink` | effektive Entfernung = Abstand Auge–Blockmitte + 0,5 je undurchsichtigem Vollblock auf der Linie (höchstens 64 gezählt, Augen- und Zielzelle nicht); in Reichweite bei ≤ Reichweite; über 128 Blöcke wird nicht verfolgt; Zellen in nicht geladenen Chunks zählen ohne Dämpfung und werden nie gelesen (Schnittstelle `RxLink.Cells` mit `loaded(chunkX, chunkZ)` vor jedem `opaque`) |
| `RxLearn` | gleitendes Fenster 1,5 s; Quelle mit dem größten Weg ab 30 %; Knüppel haben Vorrang, solange sie mindestens 80 % des Wegs der besten Achse oder Taste zeigen (eine Knüppelbewegung bewegt auch ihre rohe Achse); nach 12 s ohne Treffer Abbruch |
| `RxSettings` | Record `(owner, source, low, high, range)` mit MapCodec für die Block-Entity; Endpunkte auf −1…1, Reichweite auf 1–128 begrenzt; neue Quelle setzt die Standard-Endpunkte ihrer Art |
| `RxAccess` | `UNBOUND` (jeder bindet), `OWNER`, `OPERATOR` (Stufe 2), `FOREIGN` |
| `RxMask` | Masken Knüppel (4 Bit), Achsen (32 Bit), Tasten (32 Bit) |

## Speicherformat (Block-Entity)

```
{
  "Format": 1,
  "Bind": { "pilot": [I; …] },          fehlt = ungebunden (Codec von drone.bind.ReceiverBind)
  "Source": { "kind": "stick", "index": 3 },   fehlt = kein Kanal
  "Low": -1.0f, "High": 1.0f,
  "Range": 64
}
```

Kaputte Felder (falsche Quelle, `NaN`, Reichweite 9000) werden beim Laden ersetzt statt den Block zu verwerfen
(`lenientOptionalFieldOf`). Das Update-Paket an die Clients trägt zusätzlich `OwnerName` (Name aus der Spielerliste
oder dem Namens-Cache des Servers), gespeichert wird er nicht. Bruch droppt einen leeren RX-Block (Beute-Tabelle ohne
Daten).

## Netzwerk (Protokoll 17, im Basis-Commit gesetzt)

| Payload | Richtung | Inhalt | Prüfung |
|---|---|---|---|
| `propwash:rx_demand` | S2C | `RxMask` | – |
| `propwash:rx_input` | C2S | Folgenummer (varint), `RxMask`, je Knüppel- und Achsbit ein `short` (−32767…32767 ↔ −1…1), Tasten als Bitfeld | nur mit Bedarf, Masken ⊆ Bedarf, höchstens 25 je Sekunde (Rest verworfen), Folgenummer muss steigen |
| `propwash:rx_config` | C2S | `BlockPos`, Aktion `BIND`, `UNBIND`, `SOURCE` (Quelle), `LOW`/`HIGH` (float), `RANGE` (varint) | Abstand ≤ Interaktionsreichweite + 1, Chunk mit Block-Ticks, Rechte, Endpunkte endlich in −1…1, Reichweite ganzzahlig 1–128 |

## Server (`RxServer`)

- Alle 2 Ticks: je Welt und je gebundenem Besitzer, der online und in derselben Welt ist, jeder Block, dessen Chunk
  Blöcke tickt: `level.shouldTickBlocksAt(pos)` **und** `ServerChunkCache#isPositionTicking` (der Chunk ist
  wirklich auf Block-Tick-Stufe). `isLoaded` genügt nicht: Es gilt auch für die geladenen Randchunks, deren Nachbarn
  fehlen können und in die `setBlock` Nachbar-Updates schicken würde. Außerhalb davon kein `getBlockState`, kein
  `setBlock`, kein Ticket (E36). Die Block-Entities kommen aus `RxIndex` (je Welt, nach Besitzer, Eintrag bei
  `clearRemoved`, Austrag bei `setRemoved`), es wird nichts gescannt.
- Link alle 10 Ticks je Block neu, sonst aus dem Cache. Die Linie liest Blöcke nur in Chunks mit Block-Ticks über
  `getChunkNow` (lädt nie).
- Mit Link und frischen Werten (≤ 100 Ticks alt, Quelle im letzten Paket enthalten) Stärke rechnen; Blockzustand nur bei
  Änderung setzen (`UPDATE_ALL`). Ohne Link `link = lost`, Stärke bleibt. Ohne Kanal `link = none`.
- Ausgang nach vorne (`output=front`): Ändert sich die Stärke oder die Richtung, meldet `RxBlock#onPlace` dem Block
  vorne und dessen Nachbarn die Änderung (wie `DiodeBlock#updateNeighborsInFront`), damit das starke Signal durch den
  Block vorne ankommt; beim Abbauen ebenso (`affectNeighborsAfterRemoval`). Der Block vorne liegt höchstens einen
  Block weit weg, seine Nachbarn höchstens zwei: alles im eigenen oder im direkt benachbarten Chunk, der bei einem
  Chunk mit Block-Ticks immer geladen ist.
- Alle 20 Ticks je Spieler der Bedarf: Quellen aller eigenen Blöcke derselben Welt innerhalb Reichweite + 16 Blöcke;
  geändert → `rx_demand`. Nach jeder Einstellung sofort für alten und neuen Besitzer.
- Bindung lösen setzt Stärke 0 und `link = none` (der Block folgt dann niemandem mehr).

## Client (`rx.client`)

- `RxClient`: sendet nur bei Bedarf, im Client-Tick (höchstens 20-mal je Sekunde), nur bei Änderung um mehr als 1/512
  des Wegs (Taste: jede Änderung), sonst alle 2 s als Lebenszeichen. Werte aus `RadioState` (`RxRadio`): Gas wird von
  0…1 auf −1…1 umgerechnet, damit alle Quellen denselben Bereich haben; Tastatur nur in einer Flug-Sitzung.
- Rechtsklick mit der Funke: Client öffnet das Fenster (ungebunden, eigener Block, als Operator), Server bindet einen
  ungebundenen Block an den Spieler und meldet einen fremden Block in der Aktionsleiste. Rechtsklick ohne Funke (leere
  Hand): Zustand in der Aktionsleiste; mit einem anderen Item in der Hand wird normal gebaut.
- Schleichen + Rechtsklick mit der Funke: Vanilla lässt den Block beim Schleichen mit Item aus und gäbe den Klick an die
  Funke (Status der Drohne). Ein Client-Mixin (`mixin.rx.client.MultiPlayerGameModeMixin` auf
  `MultiPlayerGameMode#useItemOn`) fängt genau diesen Fall ab und öffnet das Fenster mit der Bestätigung „Bindung
  lösen“; ungebunden bindet er stattdessen per `rx_config BIND`, ein fremder Block meldet den Besitzer.
- `RxScreen` (Propwash-Theme, eine Spalte bis 300 Punkte): Titel mit LED, „Gebunden an“, Kanal-Auswahl als Ausweg,
  „Kanal anlernen“, Hinweiszeile, Live-Balken der echten Stärke, „Als 0 % / 100 % übernehmen“ (nebeneinander, wenn
  beide passen), Endpunkte, Reichweiten-Regler (Eingabe tippbar, sendet nach 5 Ticks Ruhe), Status, unten „Bindung
  lösen“ und „Fertig“. Inhalt scrollt, Höhen der wechselnden Texte nach dem längsten möglichen Text der Sprache.

## Assets und Daten

- `tools/assets/world/rx_block.py` erzeugt Texturen (32 × 32), drei Gehäusemodelle (`rx_block`, `_linked`, `_lost`, LED
  mit Leuchten in den beiden letzten), 16 Balkenmodelle `rx_block_power_0` bis `_15` mit je eigener Textur
  `block/rx_block_power_<n>` und Leuchten `n`, das Inventarmodell `rx_block_inventory` (Gehäuse ohne Link mit Balken bei
  Stärke 0) für das Item und den Blockzustand; ausführen mit einem Python mit Pillow.
- Ausgangsstecker `rx_block_output` (Kupfer, roter Kontakt vorne, roter Pfeil oben, Textur `block/rx_block_output`):
  ragt an der Vorderseite unter der LED aus dem Gehäuse. Die Modelle zeigen nach Norden; `facing` dreht den ganzen Block
  (`y` = 0, 90, 180, 270 für Norden, Osten, Süden, Westen). `output=front` setzt einen Stecker in Richtung `facing`,
  `output=all` (alter Block ohne Richtung) vier, je einen an jeder Seite. Das Item zeigt den Block mit Stecker nach
  Norden.
- Blockzustand als Multipart: ein Teil nach `link` (Gehäuse mit LED), ein Teil nach `power` (Balken), beide je `facing`
  gedreht, dazu der Stecker nach `output` und `facing`. Kein
  Block-Entity-Renderer und keine Einfärbung im Code, alles kommt aus Blockzustand, Modellen und Texturen. Ein
  Ressourcenpaket ändert eine Stufe über ihre Textur oder ihr Modell, das Gehäuse über die drei Gehäusemodelle.
- Rezept im Generator `tools/data/survival.py`: Blitzableiter oben, Kupferbarren links und rechts, Platine in der Mitte,
  Redstone unten → 1 (Rezeptbuch „Ausrüstung“, siehe Abweichungen). Beute-Tabelle `blocks/rx_block`, Tag
  `minecraft:mineable/pickaxe`.
- Kreativ-Tab: hinter dem Signal-Repeater. Flugschul-Kapitel `rx_block` (`guide.scenes.RxBlockScene`, fünf Schritte).
- Item-Tooltip (`RxBlockItem`): direkt unter dem Namen `propwash.rx.tooltip`, umbrochen von `RxText.tooltipLines` auf
  möglichst wenige, möglichst gleich lange Zeilen bis 48 Zeichen (in einer Zeile wäre der englische Satz 321 Punkte
  breit und passte nicht in 320 GUI-Punkte). Die erste Zeile ist die Kurzzeile von `ItemHelp` über
  `propwash.rx.tooltip.line` („%s“), die übrigen sind Statuszeilen und zählen so im Zeilenbudget mit. Mit Shift folgen
  wie bei anderen Blöcken Erklärung, „Benutzung“ und „Passt zu“ aus `propwash.help.rx_block.*`; eine eigene Kurzzeile
  `propwash.help.rx_block.summary` gibt es nicht mehr.

## Abweichungen vom Entwurf

- **Kapitel-Anmeldung:** `RxBlockScene` meldet `rx.client.RxClient` über `GuideScenes.register` an (wie Bandos und
  Kreativ-Drohne), nicht mit einer Zeile in `GuideScenes.bootstrap`. Grund: `FlightSchoolScenesTest` prüft die feste
  Reihenfolge der eingebauten Kapitel; das Kapitel steht so am Ende der Liste.
- **Link-Schnittstelle:** eigene `RxLink.Cells` statt `ObstructionTracer.World`, weil der RX-Block Vollblöcke zählt
  und keine Funk-Materialien in dB braucht; die geforderte Prüfung „nur geladene Chunks lesen“ steckt in `loaded`.
  Der Server meldet dort nur Chunks mit Block-Ticks als geladen (strenger als `isLoaded`).
- **Rezeptbuch-Reiter:** „Ausrüstung“ statt „Redstone“, weil `SurvivalRecipeBookTest` nur Werkbank, Bauen und
  Ausrüstung kennt; ein Redstone-Reiter bräuchte eine Zeile in diesem Test.
- **Anzeige der Stärke (Owner-Playtest 23.10., Befund OWNP-4):** Der Entwurf (`gameplay.md` §3.C) sah nur die LED im
  Modell vor, die Stärke war nicht zu sehen. Jetzt zeigt der Block die Stärke 0–15 wie eine Redstone-Lampe oder ein
  Kabel: Balken mit 15 Feldern und mit der Stärke steigendes Leuchten, per Blockzustand und Modell-Varianten. Das
  Gehäuse ist unverändert, die drei Gehäusemodelle sind byte-gleich.
- **Ausgang nach vorne (Owner-Playtest 27.10., Befund OWNP6-1):** Der Entwurf (`gameplay.md` §3.C, Entscheidung E20)
  sah ein schwaches Signal an allen sechs Seiten vor, der Block hatte keine Richtung. Jetzt wird er wie der Repeater
  ausgerichtet: Ausgang in Blickrichtung beim Setzen, Signal schwach und stark nur nach vorne, Staub verbindet sich nur
  vorne, Stecker mit Pfeil im Modell. Übergang: Blockzustände ohne `output` (alle bis dahin gesetzten RX-Blöcke, auch
  der als bloßer Name gespeicherte Grundzustand) laden über den Grundzustand mit `output=all`, `facing=north` und
  arbeiten unverändert an allen Seiten; keine bestehende Schaltung bricht. Wer die Richtung will, baut den Block ab und
  setzt ihn neu (er droppt ohne Einstellungen, also neu binden). Deshalb ist der Grundzustand des Blocks `output=all`,
  gesetzt wird immer `output=front`; Code, der einen gerichteten Block braucht, nimmt `RxBlock.facing(state, richtung)`.
  Die Zahl der Blockzustände steigt von 48 auf 384, Client und Server brauchen also denselben Stand.
- **Mitte ist Stufe 8 (Owner-Playtest 27.10., Befund OWNP6-2):** Der Entwurf rechnete runden(15 · t) mit 0,3 Stufen
  Hysterese. Die Mitte lag damit genau auf der Grenze 7/8 (7,5), und die Hysterese hielt dort die alte Stufe: von 0
  kommend blieb 7, von 15 kommend 8. Jetzt ist die Mitte fest Stufe 8, beide Hälften sind gleichmäßig geteilt, und es
  gibt keine Hysterese mehr. Gegen Flackern genügt, dass die Funke nur bei Änderung um mehr als 1/512 des Wegs sendet;
  das Lebenszeichen alle 2 s schickt den genauen Wert, danach gilt die Stufe der tatsächlichen Stellung.
- **Mixin:** zusätzlich `propwash.rx.mixins.json` (angemeldet in `fabric.mod.json` und `neoforge.mods.toml`) für
  Schleichen + Rechtsklick.

## Tests

- JUnit: `RxMappingTest`, `RxLinkTest`, `RxSourceTest`, `RxPayloadTest`, `RxSettingsTest` (NBT hin und zurück),
  `RxLearnTest`, `RxAccessTest`, `RxUnloadProbeTest`, `RxTooltipTest` (Umbruch des Tooltips), `guide.RxBlockSceneTest`,
  `RxBlockModelTest` (Blockzustand: jede Stärke mit eigenem Aussehen und eigener Textur, Leuchten steigt, LED folgt
  weiter `link`, Item gleich dem gesetzten Block bei Stärke 0, jede Richtung dreht den ganzen Block, Stecker an der
  Seite von `facing`, alter Block mit Steckern an allen vier Seiten), `RxOutputTest` (Signal, starkes Signal und
  Staub-Verbindung je Richtung und Art), `RxMappingTest` mit der ganzen Kennlinie in beiden Anfahrrichtungen, Mitte 8
  über das Kanal-Totband und gleichmäßigen Stufen.
- Selbsttest `rx:` (`rx.client.RxSelfTests`, dazu `rx.client.RxModelSelfTests`: dieselben Regeln an den gebackenen
  Modellen im Spiel, ohne fehlende Textur, Stecker an der richtigen Seite, und `rx.client.RxOutputSelfTests`: von Hand
  in jede Blickrichtung gesetzt, Lampe vorne und die Lampe dahinter an, Lampen rechts, hinten, oben, unten und Staub
  links aus, Staub verbindet nur vorne; ein alter Block, aus dem gespeicherten Zustand ohne Richtung gelesen, sendet an
  alle Seiten, aber nicht durch die Lampe vorne) und Bildschirmfotos `bildschirmfotos: RX-Block` (`rx.client.RxShowcase`,
  mit `rx_block_levels` und `rx_block_levels_night`: Stärke 0–15 am Tag und bei Nacht),
  `ui-scale:`-Fälle „RX-Block“ und „RX-Block Bindung lösen“, Server-Rauchtest `rx.RxServerChecks`, Beitrittstest
  `PROPWASH_JOINTEST_RX=1` (`dev.RxJoinCheck`, `rx.client.RxJoinProbe`); Abläufe in `docs/testing.md`.
- Entladen im Beitrittstest (`rx.RxUnloadProbe`): `hasChunk` meldet den Chunk des zweiten Blocks schon als fehlend,
  sobald das Ticket gelöst ist. Entladen wird er erst später, wenn `ChunkMap` die Entlade-Warteschlange abarbeitet,
  bei rund 1000 Nachbar-Chunks des Tickets oft erst nach Sekunden. Erst dann ruft das Spiel `setRemoved` an der
  Block-Entity auf, und erst dann verlässt sie `RxIndex`. Der Test gilt deshalb erst als entladen, wenn die beim Setzen
  gemerkte Block-Entity entfernt ist (bis zu 120 s, sonst „Chunk des zweiten Blocks entlädt nicht“). Erst danach prüft
  er das Verzeichnis. Den Chunk fragt er dafür nicht ab, er wird also nicht wieder geladen.
