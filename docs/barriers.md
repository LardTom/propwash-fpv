# Drohnen-Barrieren: Drohnen-durchlässige Barriere und Drohnen-Sperre

Für Kursbauer, Server-Betreiber und Add-on-Autoren. Zwei unsichtbare Blöcke für den Kreativmodus, die wie die
Vanilla-Barriere funktionieren, aber zwischen Drohnen und allem anderen unterscheiden:

| Block | Id | Drohnen | Spieler, Mobs, Items, Pfeile, Fahrzeuge |
|---|---|---|---|
| **Drohnen-durchlässige Barriere** (EN „Drone-Passable Barrier“) | `propwash:drone_pass_barrier` | fliegen durch | werden aufgehalten wie von der Barriere |
| **Drohnen-Sperre** (EN „Drone Barrier“) | `propwash:drone_block_barrier` | prallen ab oder stürzen wie an einem festen Block | gehen durch |

Damit lassen sich Zuschauerbereiche abtrennen, durch die Drohnen fliegen dürfen, oder unsichtbare Kurswände bauen, die
nur Drohnen aufhalten.

## 1. Wie die Vanilla-Barriere

- Nur im Kreativ-Inventar (Propwash-Tab, nach dem RX-Block), kein Rezept, keine Beute, im Überleben nicht abbaubar
  (Härte −1, Sprengfestigkeit 3 600 000,8), von Kolben nicht bewegbar, kein Mob-Spawn darauf, wasserfüllbar.
- Unsichtbar. Im Kreativmodus zeigen beide wie die Vanilla-Barriere den Auswahlrahmen, sobald man auf sie zielt, auch
  mit leerer Hand oder einem anderen Item; man kann sie dann anvisieren, abbauen und daran weiterbauen. Wer im
  Kreativmodus einen der beiden Blöcke in der Haupt- oder Nebenhand hält, sieht zusätzlich **beide** Arten in der Nähe
  als schwebende Symbole (Markierungs-Partikel wie bei der Barriere: grüner Ring mit Drohne für die durchlässige Barriere, roter
  Ring mit Strich für die Sperre). Im Überleben gibt es weder Symbol noch Umriss: Die Blöcke stören dort weder Klicks
  noch den Blick. Im Kreativmodus fängt auch die Sperre Klicks ab, obwohl Spieler durch sie hindurchgehen; steckt der
  Kopf beim Durchgehen in ihr, zielt man auf sie selbst.
- Licht, Redstone und Videosignal gehen durch: kein Lichtverlust (auch kein Schatten), kein Redstone-Leiter (wie Glas,
  Redstone-Stufen werden nicht getrennt), kein Signalverlust für Video und Funke. Die FPV-Kamera stößt sich nicht an
  ihnen, auch in der Third-Person-Ansicht nicht.

## 2. Wer kollidiert

- Die Entscheidung trifft der Block selbst über den Kollisions-Kontext von Minecraft: ist das bewegte Entity eine
  Drohne (`QuadcopterEntity` oder eine Unterklasse), gilt die Drohnen-Regel, sonst die für alle anderen. Das gilt für
  jede Drohnenart, die über die normale Drohnen-Kollision fliegt: FPV, Sichtflug, Heimflug, autonome Flüge und
  Kamera-Drohnen anderer Add-ons. Die Freecam-Drohne hat ohnehin keine Kollision.
- Abfragen ohne Entity (Wegfindung der Mobs, Platzieren von Blöcken, Schnee, Regen, Partikel) sehen die durchlässige
  Barriere als festen Block und die Sperre als Luft, also wie „alle anderen“. Ausnahme sind Gates und Kurs-Elemente
  (Abschnitt 3): Für sie sind beide Barrieren volle Blöcke.
- Abfragen der Drohne ohne Entity-Kontext sehen die Barrieren wie die Drohne: Oberflächen-Antwort beim Aufprall
  (`SurfaceMaterials`, die Sperre antwortet wie ein normaler Block), Kontakt-Regeln der Add-ons (`BlockContact` meldet
  die Sperre), Heimflug (Hindernisse und Landefläche), der Hindernis-Sensor der Drohnen-API
  (`DroneAccess.obstacleDistance` und `scan` mit einer Drohne als `ignore`) und der Prop-Staub (über beiden Barrieren
  wirbelt nichts auf). Dafür gibt es `barrier.DroneBarriers.droneShape(...)` und den Kontext `DroneBarriers.DRONE_SIGHT`.
- **Server entscheidet:** Der Server prüft jedes Bewegungspaket der Drohne mit denselben Formen. Ein Client, der durch
  eine Sperre fliegen will (etwa mit einer Geist-Regel ohne Recht am Server), wird zurückgesetzt und bekommt die Meldung
  „Der Server erlaubt hier kein Fliegen durch Blöcke.“ Spieler prüft der Server wie bei jedem Block.

## 3. Rennen

Die Barrieren sind keine Kurselemente: keine Gates, keine Strafen, keine Wertung. Ein Flug durch die Drohnen-durchlässige
Barriere ist keine „Runde mit Hilfe“ (anders als eine Geist-Regel), ein Aufprall an der Drohnen-Sperre zählt wie an
jedem Block.

Beim Kursbau zählen beide Barrieren wie volle Blöcke (Owner-Entscheidung nach dem Spieltest vom 29.10.):
- Gates, Gate-Stangen und Kurs-Elemente (Rennflagge, Slalomstange, Tauchwürfel) stehen oben auf einer Barriere, Wand- und
  Decken-Gates docken an ihren Seiten an, und in eine Barriere hinein lässt sich nichts setzen (rote Vorschau wie bei
  Stein).
- Ein Freiform-Gate darf einen Rahmen aus Barrieren haben, auch gemischt aus beiden Arten und anderen Blöcken; die
  Öffnung endet an der Barriere, es gibt keine Warnung „Rahmen offen“.
- Technisch fragen Gate- und Freiform-Platzierung mit dem Kontext `barrier.DroneBarriers.COURSE` (`Collider.COURSE`,
  beide Barrieren fest) und `DroneBarriers.sturdyForCourse(...)` statt ohne Entity. Die Kollision im Flug ändert sich
  dadurch nicht: Durch einen Rahmen aus der durchlässigen Barriere fliegt eine Drohne hindurch, an einem aus der
  Drohnen-Sperre prallt sie ab.

## 4. Tests

- JUnit: `barrier.BarrierCollisionTest` (Entscheidung je Entity-Art: Drohnen und Add-on-Drohnen gegen Spieler,
  Rüstungsständer, Pfeil, Schneeball, Zombie, Schwein, Item, fallender Block, Lore, Boot, Gate; beide Barrieren fest für
  `Collider.COURSE`), `barrier.BarrierDataTest` (Modelle, Symbole, kein Rezept, keine Beute, Texte),
  `barrier.BarrierNamesTest` (Namen „Drone-Passable Barrier“ und „Drohnen-durchlässige Barriere“ in Sprachdateien,
  CHANGELOG, Release-Notizen und Doku, kein alter Name mehr).
- Selbsttest `'-PpropwashSelftestOnly=barriers:'` auf beiden Loadern, eigener Platz bei Spawn + (−1600, 1600):
  Eigenschaften; Rüstungsständer, Schwein und Item bleiben auf der durchlässigen Barriere liegen und fallen durch die
  Sperre, ein Pfeil bleibt in der durchlässigen Barriere stecken und fliegt durch die Sperre; der Spieler läuft gegen die
  durchlässige Barriere und durch die Sperre
  (Server gleich); im Kreativmodus Umriss und Fadenkreuz auch mit leerer Hand oder Stein, Symbole nur mit Barriere in
  der Hand, im Überleben nichts; Licht, Redstone-Stufe und
  Videosignal; Kursbau mit beiden Barrieren wie mit Stein (Freiform-Rahmen 3 × 1 geschlossen, auch mit einer Sperre unten
  in einem Rahmen aus der durchlässigen Barriere; Gate und Slalomstange stehen oben drauf, Wand-Gate dockt an der Seite
  an, Block in der Öffnung sperrt den Platz); Drohne fliegt mit 6 m/s durch 3 Blöcke durchlässige Barriere (Server
  nimmt an, kein Kamera-Schutz, keine Hilfe);
  Abprall mit 4 m/s und Absturz mit 14 m/s an der Sperre wie an Stein; Geist ohne Recht wird vom Server zurückgesetzt.
- Server-Rauchtest bei jedem Lauf (Zeile „Drohnen-Barrieren: …“): Drohne, Spieler, Rüstungsständer, Schwein, Zombie,
  Item und Pfeil gegen beide Barrieren am dedizierten Server.
- Beitrittstest `PROPWASH_JOINTEST_BARRIERS=1` an Server und Client, Ablauf in [`testing.md`](testing.md).
- Bildschirmfotos (nur Fabric): `-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos: Drohnen-Barrieren'`
  (`barriers_markers`, `barriers_hidden`, `barriers_outline`, Shift-Tooltips englisch und deutsch).

## 5. Assets

`tools/assets/world/barriers.py` erzeugt die beiden 16 × 16-Symbole (`textures/item/<id>.png`), die Blockmodelle (nur
die Partikel-Textur), die Item-Modelle (`item/generated`), die Item-Definitionen und die Blockzustände.

## 6. Englische Kernsätze (Website)

- Two invisible creative-mode blocks that work like the vanilla barrier: the **Drone-Passable Barrier** lets drones fly
  through and stops players, mobs, items and arrows; the **Drone Barrier** stops only drones, which bounce off or crash
  as on any solid block.
- Creative inventory only (Propwash tab): no recipe, no drops, cannot be mined in survival.
- Invisible in survival. In creative mode they show a selection outline when you aim at them, like the vanilla barrier,
  and can be targeted, broken and built on; holding one of the two blocks also shows both kinds as icons.
- Light, redstone and the video signal pass through both blocks unharmed.
- Works for every drone, including camera drones from add-ons; the server checks every drone movement.
- They are not part of races: flying through a Drone-Passable Barrier does not mark the lap as assisted.
- Gates and course elements treat both barriers as full blocks: they stand on them, dock to their sides, and a freeform
  gate can use barriers as its frame.
