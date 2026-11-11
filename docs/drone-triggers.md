# Druckplatten und Stolperdraht

Für Spieler und Server-Betreiber. Drohnen drücken Holz- und Wägeplatten und lösen Stolperdrähte aus, so wie
fallengelassene Items. Stein-Druckplatten (auch die aus poliertem Schwarzstein) brauchen weiter einen Spieler oder ein
Mob. Den Fix hat bluepanee mit Pull Request #1 vorgeschlagen; Propwash übernimmt die Wirkung auf Druckplatten und
Stolperdraht ohne die übrigen Blockwirkungen.

Technik und Entscheidungen: [`design/0.6.0/fixes-polish.md`](design/0.6.0/fixes-polish.md) §3.C, §6.4, E12, E13,
E19 und E20.

## 1. Was auslöst

- **Holzdruckplatten** (alle Holzarten): jede Drohne, ob geflogen, abgestellt oder abgestürzt.
- **Wägeplatten**: zählen die Drohne als ein Entity. Eine Drohne auf der leichten (goldenen) Wägeplatte gibt Signal 1,
  auf der schweren (eisernen) ebenfalls 1.
- **Stein-Druckplatten**: bleiben aus, wie bei Items.
- **Stolperdraht**: ein gespannter Faden zwischen zwei Haken löst aus, sobald die Drohne den Faden berührt.
- Wie bei anderen schnellen Entities muss die Drohne in einem Tick im Auslösebereich sein (eine Viertel-Blockhöhe über
  der Platte). Landen, tiefes Schweben und langsames Darüberrollen lösen sicher aus, ein schneller Überflug in einem
  Block Höhe nicht.
- Solange die Drohne auf der Platte liegt, bleibt sie gedrückt; fliegt sie weg, geht die Platte nach ihrer üblichen
  Zeit aus (Holzplatten nach höchstens einer Sekunde, Wägeplatten und Stolperdraht nach höchstens einer halben).

## 2. Was nicht passiert

Die Drohne löst nur die Blöcke im Tag aus. Feuer und Lava zünden sie nicht an, Kakteen beschädigen sie nicht, Nether-
und End-Portale schicken sie nicht in eine andere Dimension, Spinnweben bremsen nur über die eigene Spinnweben-Physik
aus 0.5.2 (keine zusätzliche Vanilla-Bremse), Blasensäulen stoßen sie nicht. Drohnen bleiben gegen Feuerschaden
genauso empfindlich wie vorher (kein Feuer-Immun).

## 3. Für Server-Betreiber

- Welche Blöcke Drohnen auslösen, steht im Block-Tag `propwash:drone_triggers`
  (`data/propwash/tags/block/drone_triggers.json`): `#minecraft:pressure_plates` und `minecraft:tripwire`.
- Ein Datenpaket kann ihn erweitern oder leeren. Leeren (Drohnen lösen nichts mehr aus):

  ```json
  { "replace": true, "values": [] }
  ```

  als `data/propwash/tags/block/drone_triggers.json` im Datenpaket. Nur Druckplatten, ohne Stolperdraht:
  `{ "replace": true, "values": ["#minecraft:pressure_plates"] }`.
- Weitere Blöcke im Tag bekommen den Aufruf `entityInside` mit einem leeren Effekt-Sammler: Effekte, die Vanilla
  über den Sammler anwendet (Feuer, Lava, Gefrieren), wirken auch dann nicht. Blöcke, die direkt handeln (Portale,
  Kakteen, Beerensträucher), wirken wie bei jedem Entity; solche Blöcke gehören nicht in den Tag.
- Es gibt keine eigene Welt-Regel dafür. Redstone-Fallen und Türen hinter Holzplatten reagieren also auf Drohnen wie
  auf Items; wer das nicht will, nimmt Stein-Druckplatten oder leert den Tag.
- Kosten: Der Server prüft nur Drohnen, die sich seit der letzten Prüfung bewegt haben (mehr als 0,0001 Block).
  Abgestellte Drohnen kosten nichts, auch wenn viele herumstehen.
- Es gibt kein neues Netzpaket und keine neuen Speicherdaten; Clients sehen die Blockzustände wie immer.

## 4. Technik

- `interact.DroneBlockTriggers#tick(drone, level)` steht in `QuadcopterEntity#tickServer` nach `syncFlightTags` und
  vor `SERVER_TICK`. Drohnen bewegen sich auf dem Server nicht über die Vanilla-Bewegung mit Blockwirkung (Positionen
  kommen vom Client oder aus der eigenen Physik), darum ruft sonst niemand `entityInside` auf.
- Prüfung im ersten Tick und danach nur bei Bewegung (`moved(vorher, jetzt)`, Abstand über 1·10⁻⁴ Block); die letzte
  Position liegt in einer `WeakHashMap` der Klasse (nur Server-Thread).
- Geprüft wird jede Blockposition im Kollisionsquader der Drohne (um 1·10⁻³ vergrößert), deren Zustand im Tag liegt,
  und deren Form für das Hineinragen (`getEntityInsideCollisionShape`, wie Vanilla) den Quader berührt. Für Druckplatten
  ist das der ganze Block, für Stolperdraht der Faden selbst; eine Drohne über dem Faden löst also nicht aus. Dann
  `state.entityInside(level, pos, drone, InsideBlockEffectApplier.NOOP, true)`; danach zählen Platte und Faden selbst
  weiter (geplante Ticks), solange etwas darauf liegt.

## 5. Tests

- JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.interact.DroneBlockTriggersTest` (Tag-Datei
  enthält genau die zwei Einträge, Bewegungs-Schwelle).
- Selbsttest beider Loader: `'-PpropwashSelftestOnly=interact:'`, die Schritte „interact: Block-Tag …“, „… Drohne
  drückt Holzdruckplatte“, „… leichter Wägeplatte gibt Signal 1“, „… Stein-Druckplatte bleibt bei Drohne aus“, „…
  Drohne löst Stolperdraht aus“, „… ohne Nebenwirkungen an Feuer, Lava, Kaktus, Portal und Spinnwebe“ und „… Testfeld der
  Druckplatten abbauen“ (`interact.client.DroneTriggerSelfTests`).
- Dedizierter Server: Beitrittstest mit `PROPWASH_JOINTEST_PLATES=1` an beiden Prozessen (`dev.PlateJoinCheck`), siehe
  `docs/testing.md` §3.

## 6. Englische Kernsätze (Website, Changelog)

- Drones press wooden and weighted pressure plates and trip tripwires, like dropped items do (stone plates still need a
  player or mob). Thanks to bluepanee for the fix (pull request #1).
- Only pressure plates and tripwire react: fire, lava, cacti, portals, cobwebs and bubble columns do nothing new to
  drones.
- Server owners: the block tag `propwash:drone_triggers` lists what drones trigger. A data pack can extend it or empty
  it with `"replace": true`.
- Parked drones cost nothing: the server only checks drones that moved.
