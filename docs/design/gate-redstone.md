# Gates geben Redstone-Signal (race, 0.5.0)

Anforderung (Besitzer, 08.10., sinngemäß, nach dem Test durch einen Rennpiloten): Renn-Gates sollen Redstone auslösen
können – fliegt eine Drohne durch ein Gate, versorgt das Gate den Block, auf dem es steht, mit Strom. Damit lassen
sich Lampen, Türen, Kolben, Notenblöcke oder Zähler an jeden Durchflug hängen.

## 1. Verhalten

- **Auslöser:** jeder gültige Durchflug, den der Server erkennt – dieselbe Prüfung wie bei der Zeitmessung
  (`RaceServer#onDroneMoved`, `GateGeometry#stageCrossing(0, …)`): nur in Flugrichtung (Pfeile am Boden), nur durch die
  Öffnung, keine Teleport-Sprünge (`LapTracker.MAX_SEGMENT`). Anders als die Zeitmessung zählt er auch im freien Flug
  und bei Gates, die zu keinem Kurs gehören. Nur gesteuerte Flüge lösen aus (wie Runden); eine Drohne im GPS Rescue
  ohne Pilot nicht.
- **Arten:** Gates (alle Größen und Lagen) und Freiform-Gates. Wendeflaggen, Slalomstangen und Tauchwürfel geben kein
  Signal – sie bestehen aus mehreren Stufen, ein Puls je Stufe wäre irreführend.
- **Puls:** volle Stärke 15 für die eingestellte Länge (Standard 10 Ticks = 0,5 s, 2–100 Ticks). Fliegt die nächste
  Drohne durch, bevor der Puls endet, beginnt er von vorn, ohne kurz auszugehen (wie eine Druckplatte, die belegt
  bleibt).
- **Wohin:** „starker“ Strom in genau einen Block wie bei einem Knopf an dessen Fläche – der Block wird stark versorgt
  und versorgt seinerseits Redstone-Staub, Lampen, Kolben, Verstärker, Komparatoren, Fackeln, Türen ringsum. Ist der
  Block selbst ein Bauteil (Lampe, Kolben, Trichter, Notenblock, TNT), reagiert er direkt. Halbe Blöcke, Glas und
  andere Nichtleiter geben den Strom wie bei einem Knopf nicht weiter.

| Lage | Block mit Strom |
|---|---|
| stehend (`STANDING`) | Block unter dem Befestigungspunkt (Mitte des Gates) |
| hängend (`HANGING`) | Deckenblock über dem Befestigungspunkt |
| liegend über einer Grube (`FLOOR_FLAT`) | Randblock unter der Mitte des unteren Balkens (die Seite, an der der Rahmen beginnt, `GateGeometry#worldX(0, frame/2, 0)`) |
| liegend unter einem Deckenloch (`CEILING_FLAT`) | Deckenblock über der Mitte des unteren Balkens |
| Wand (`WALL_DIVE`, `WALL_SIDE`, `WALL_WINDOW`) | Wandblock hinter dem Befestigungspunkt |
| gestapeltes Gate | Block unter dem untersten Gate des Stapels (alle Gates einer Leiter schalten denselben Block) |
| Freiform-Gate, senkrechte Öffnung | Randblock unter der untersten Zeile der Öffnung, in deren Mitte (bei gerader Zahl offener Felder das linke der beiden mittleren) |
| Freiform-Gate, waagerechte Öffnung (Schacht, Grube) | Randblock an der Nordkante (kleinstes z), mittig |

Der Befestigungspunkt ist `GatePlacement#mountPoint` (`docs/design/gate-mounting.md` §2) und bleibt beim freien Drehen
und bei eigener Öffnungsgröße gleich (`docs/design/gate-free-transform.md`). Liegende Gates nehmen nicht ihn: Sie liegen
praktisch immer über einer Grube oder unter einem Deckenloch, die Mitte der Öffnung ist dort Luft. Der Balken liegt
dagegen auf dem Rand auf (beim mittleren Gate mit 0,5 Blöcken Rahmen über einem 3 × 3-Loch genau auf dem Randblock).
Dreht man ein liegendes Gate, wandert der Zielblock mit dem Balken; der rote Rahmen im Einstell-Modus zeigt ihn. Liegt er genau auf einer Blockkante (gerade
Öffnungsbreite an der Wand, kleines liegendes Gate), zählt der Block mit der größeren Koordinate (Ost, Süd, oben).
Auf einer Stufe oder unter einer oberen Stufe ist es die Stufe selbst. Im Einstell-Modus ist der Block rot umrandet,
die Fläche zum Gate mit breiterem Rahmen; die Seite „Redstone“ nennt ihn („… gibt Strom in den rot umrandeten Block:
Gemeißelte Steinziegel“).

## 2. Einstellungen je Gate

Im Einstell-Modus (`docs/design/gate-edit-mode.md`) hat das Panel bei Gates und Freiform-Gates eine zweite Seite
„Redstone“ (Abschnitt „Redstone-Signal“):

| Zeile | Werte | Standard |
|---|---|---|
| Signal | An / Aus | **An** |
| Pulslänge | 2–100 Ticks (Anzeige „10 Ticks · 0.5 s“), Mausrad 2 Ticks, mit Strg 1 Tick | 10 |
| Nur im Rennen | An / Aus | Aus |

- **Seiten:** Die erste Seite (Drehung, Öffnung) bleibt unverändert; die Redstone-Zeilen stehen auf einer eigenen Seite,
  weil das Panel bei GUI-Skala 4 in 1080p (480 × 270 GUI-Pixel) sonst nicht mehr passt. Schleichen + Mausrad läuft über
  die Zeilen beider Seiten; rechts in der ersten Abschnittszeile steht „Redstone »“ bzw. „« Gate“ (mit freier Maus ein
  Knopf). Das Panel ist auf beiden Seiten gleich hoch.
- **Zurücksetzen** stellt auf dieser Seite das Signal auf den Standard (an, 10 Ticks, jeder Durchflug).
- **Übernehmen:** Enter oder Rechtsklick schickt geänderte Signal-Werte als eigenes Paket `propwash:race_gate_signal`
  (Client → Server, Entity-Id und gepackte Werte) und danach wie bisher eine geänderte Lage. Ändert sich nur das
  Signal, zeigt die Statuszeile „Redstone-Signal geändert – Enter übernimmt“. Der Server prüft wie beim Einstellen
  (`GateMounting#signal`): Gate-Item, Freiform-Gate oder Kurs-Werkzeug in der Hand, Reichweite, Baurecht, bei
  Kurs-Gates Besitzer oder Operator; die Meldung lautet z. B. „Redstone-Signal: an · 10 Ticks (0.5 s)“.
- **Nur im Rennen:** Das Gate gibt nur ein Signal, wenn es zu einem Kurs gehört und der Pilot gerade ein Rennen auf
  diesem Kurs fliegt. Das Start-/Ziel-Gate (Gate 1) gibt bei jedem Durchflug in Flugrichtung ein Signal, weil genau
  dieser Durchflug Rennen startet und Runden beendet und die Startmeldung des Clients sonst je nach Reihenfolge der
  Pakete zu spät käme.
- **Abschalten** beendet einen laufenden Puls sofort; eine kürzere Pulslänge kürzt ihn.
- Neue Gates haben die Standardwerte. Gespeichert werden nur abweichende Werte; Kursdaten, Bestenlisten und
  Layout-Hash kennen das Signal nicht (es ändert die Strecke nicht).

## 3. Technik

- **Pakete und Klassen:** `race.GateSignal` (Werte, Packen in 9 Bit, Entscheidung „feuert“, reines Java),
  `race.GateSignalTarget` (Block und Fläche aus Geometrie oder Freiform-Fläche, bis auf `GateMount`/`FreeformBlocks`
  ohne Minecraft-Typen), `race.GateSignalMap` (Zähler je Sender-Block und Fläche, reines Java), `race.GateSignals`
  (Server: aktive Sender je Welt, Nachbar-Updates, Abfrage), Mixin `mixin.race.BlockStateBaseMixin`.
- **Strom ohne Block:** Ein Gate ist eine Entity, Redstone fragt nur Blöcke. Der Puls gilt deshalb als Signal eines
  gedachten Knopfs im Block vor der Befestigungsfläche (Sender, `GateSignalTarget#emitter…`) in Richtung des
  Zielblocks. Das Mixin hängt sich mit `@ModifyReturnValue` an `BlockStateBase#getSignal` und `#getDirectSignal`:
  Fragt die Welt den Sender in genau dieser Richtung, liefert sie 15. So wirkt der Puls über die normalen Wege von
  Vanilla (`SignalGetter#getDirectSignalTo`, `hasNeighborSignal`, `getBestNeighborSignal`) und bei NeoForge über
  `shouldCheckWeakPower`; Blöcke im Sender-Feld selbst (Gras, Staub) bleiben unverändert.
- **Kosten:** Ohne laufenden Puls kostet die Abfrage ein `volatile`-Lesen und `isEmpty()`. Mit Puls eine
  Identitäts-Map je Welt und eine `Long2IntOpenHashMap` (Block → Flächen-Maske), neu veröffentlicht nur beim Ein- und
  Ausschalten. Durchflüge prüft der Server in derselben Entity-Abfrage wie die Zeitmessung (sie nimmt jetzt auch Gates
  mit Signal ohne Kurs mit); Gates ohne laufenden Puls tun im Tick nichts zusätzlich.
- **Nachbar-Updates:** beim Ein- und Ausschalten wie bei einem Knopf `updateNeighborsAt` am Sender und am Zielblock
  (mit `ExperimentalRedstoneUtils#initialOrientation`), nur wenn beide Chunks geladen sind. Teilen sich mehrere Gates
  denselben Zielblock und dieselbe Fläche, zählt `GateSignalMap` mit; ausgeschaltet wird erst beim letzten.
- **Lebensdauer:** Der Puls zählt am Ende jedes Server-Ticks herunter (`GateSignals#tick` über `ServerBus.TICK_END`,
  für jedes Gate mit laufendem Puls, `GateEntity#signalTick`), nicht im Entity-Tick des Gates: Ein Chunk kann geladen
  sein, ohne dass seine Entities ticken – etwa wenn der Pilot außerhalb der Simulationsweite fliegt und nur der Chunk
  der Drohne ein Ticket mit Entity-Ticks hat (`AnchorRules.DRONE_TICKET_RADIUS` 2 ergibt Stufe 31 nur in der Mitte, der
  Nachbar-Chunk hat 32 = nur Block-Ticks). Im ersten Entwurf zählte der Entity-Tick; dann blieb der Puls stehen, sobald
  die Drohne den Chunk des Gates im Puls verließ, Lampe oder Kolben blieben an und weitere Durchflüge gaben keine neue
  Flanke. Der Puls folgt dem Gate, wenn es während des Pulses verschoben oder gedreht wird. Abbauen, Entladen des Chunks
  oder Wechsel der Welt beenden den Sender sofort mit Nachbar-Update (`GateEntity#onRemoval`). Die Restlänge speichert
  das Gate als NBT `SignalPulse`; lädt der Chunk wieder (auch nach einem Neustart mitten im Puls), meldet der erste
  Entity-Tick das Gate wieder an, der Sender geht wieder an und der Puls endet sauber – keine Lampe bleibt hängen.
  Serverstart und -stopp leeren beide Listen.
- **NBT der Gate-Entity** (nur wenn ungleich Standard): `RedstoneSignal` (bool), `PulseTicks` (int), `SignalRaceOnly`
  (bool); `SignalPulse` (int, nur während eines Pulses). Alte Gates laden mit Signal an und 10 Ticks.
- **Netz:** ein weiteres synchronisiertes `int` der Gate-Entity (gepackte Werte, für das Panel) und das Paket
  `propwash:race_gate_signal` – deshalb Netzprotokoll 14. An der Parts-API ändert sich nichts.

## 4. Entscheidungen

| Frage | Entscheidung | Verworfen |
|---|---|---|
| Standard | **An**: ohne Redstone in der Nähe passiert nichts, und so funktioniert es ohne Suchen | Aus (niemand fände die Funktion) |
| Strom wie | starker Strom in den Befestigungsblock über einen gedachten Sender und ein Mixin auf die Signal-Abfrage | unsichtbarer Block in der Welt (verändert die Welt, stört Platzieren und Kollision), Partikel-Rückmeldung bei jedem Durchflug (Unruhe im Rennen) |
| Erneuter Durchflug im Puls | Puls beginnt von vorn, ohne Lücke | getrennte Pulse mit erzwungener Pause (Kolben ruckeln, Lücke unter 1 Tick ist für Lampen unsichtbar) |
| Gegenrichtung | kein Signal (wie die Zeitmessung) | beide Richtungen (widerspricht den Pfeilen am Gate) |
| Kurs-Elemente | kein Signal | Signal je Stufe (Flagge gäbe drei Pulse) |
| „Nur erste Drohne“ | nicht umgesetzt: bräuchte eine Rangfolge je Lauf und Runde | – |
| Komparator mit Infos zum letzten Durchflug | nicht umgesetzt: Komparatoren lesen Blöcke und Behälter, nicht Entities; dafür wäre ein Eingriff in die Komparator-Logik nötig | – |
| Ort der Einstellungen | eigene Seite im Einstell-Panel | Zeilen auf der ersten Seite (Panel passte bei GUI-Skala 4 nicht mehr), eigenes Fenster (gegen den Einstell-Modus ohne Fenster) |
| Herunterzählen | am Ende des Server-Ticks für alle Gates mit laufendem Puls | Entity-Tick des Gates (bleibt in Chunks ohne Entity-Ticks stehen), Ablauf-Spielzeit je Sender (Restpuls wäre nach dem Speichern von der Weltzeit abhängig) |
| Liegende Gates | Randblock unter bzw. über dem unteren Balken | Block unter der Öffnungsmitte (dort ist bei Grube oder Deckenloch Luft, das Signal ginge ins Leere), Ausweichen je nach Block in der Welt (Ziel hinge von Blöcken ab, die sich ändern, und wäre auf dem Client schwerer zu zeigen) |

## 5. Tests

- JUnit: `GateSignalTest` (Packen, Grenzen, „feuert“, Sekunden-Text), `GateSignalTargetTest` (jede Lage und Richtung,
  Stufe, geneigt und mit eigener Öffnung, liegende Gates in allen Größen, Richtungen und gespiegelt am Randblock unter
  dem Balken, Freiform senkrecht/waagerecht/mit Lücke, aus einer echten Füllung),
  `GateSignalMapTest` (geteilte Sender), `GateEditModelTest` (Seiten, Zeilen, Mausrad).
- Selbsttest `race: Redstone – …` (`race.client.GateRedstoneSelfTests`): Testbau mit stehendem Gate auf einem Block mit
  Staub nach beiden Seiten, Lampe und Kolben, Tauchtor an einer Wand mit vier Lampen um den Wandblock, Freiform-Fenster
  mit einer Lampe als Rand; Durchflug über `QuadcopterEntity.SERVER_MOVED` wie im echten Flug. Geprüft: Strom gleich
  beim Durchflug, Restpuls in jedem beobachteten Tick genau `10 − Ticks`, Strom genau solange der Puls läuft, Lampe an,
  Kolben ausgefahren, danach alles aus; 40 Ticks, Abschalten mitten im Puls, ausgeschaltet, Gegenrichtung, „Nur im
  Rennen“ ohne Kurs, Verlängern ohne Lücke, Rechte (ohne Werkzeug, zu weit weg); Panel mit zwei Seiten passt in
  480 × 270, roter Rahmen beim Client, Übernehmen per Paket; liegendes Gate über einer 3 × 3-Grube und unter einem
  3 × 3-Deckenloch: Zielblock ist ein Vollblock am Rand, eine Lampe daneben geht an und wieder aus; Puls im Chunk ohne
  Entity-Ticks (Spieler 600 Blöcke weg, Drohnen-Ticket am Nachbar-Chunk): Restpuls zählt jeden Tick herunter, danach
  Draht, Lampe und Sender aus; Speichern/Laden mit Restpuls, Abbauen mitten im Puls.
- Bildschirmfotos (`bildschirmfotos: Gate-Redstone – …`): `gate_redstone.png`, `gate_redstone_wall.png`,
  `gate_redstone_editor.png`.
