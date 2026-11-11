# Drohne steckt im Block: Herausdrücken (drone, Befund VINE1-1)

Fehlerbericht aus Discord vom 03.11.2026 (öffentliche 0.5.2): Eine Drohne trifft einen Holzbalken mit Ranken, der Pilot
geht auf Gas 0. Danach liegt die FPV-Kamera im Balken, die Drohne fällt zum Boden und „wandert“ rund 8 s durch den
Erdboden (Röntgenblick, immer wieder der Untertitel „Block breaking“ der Ranken), bis sie entschärft und kopfüber neben
dem Bauwerk liegt.

## 1. Was im Spiel passiert

- **Flug ins Hindernis:** Der Client bewegt die Drohne mit `Entity#move` (Vanilla-Kollision gegen die Kollisionsformen der
  Blöcke). Ranken, Gras und Kletterpflanzen haben keine Kollisionsform, sie wirken nur als Verfangen
  (`docs/design/drone-snags.md`); Laub ist fest. In 78 Anflügen vor und 168 nach dem Fix (Selbsttest unten:
  Balken aus entrindeter Schwarzeiche mit Ranken an Seiten und Unterseite, 10, 18 und 25 m/s, sieben Richtungen, Gas 0 im
  Acro, jede Kombination aus `drones_break_blocks` und `crash_disarm`, Fabric und NeoForge) endet die Drohne nie im Block
  und das FPV-Auge liegt nie im Block. Der Einstieg aus dem Video ließ sich auf dem 0.6.0-Stand so nicht nachstellen.
- **Einmal im Block, kein Ausweg:** Steckt die Box der Drohne in einem Block (Block auf die Drohne gesetzt, Rücksetzen oder
  Teleport in einen Block, Server-Reset an eine überholte Stelle, größere Box nach dem Skalieren), ignoriert
  `VoxelShape#collide` jede Form, die die Box schon schneidet. Die Drohne sinkt in der Zelle bis zum nächsten Nachbarn,
  kommt seitlich nicht heraus und bleibt dort; die Kamera sieht das Blockinnere (0.5.2: Röntgenblick wie im Video, 0.6.0:
  Vanilla-Überlagerung aus `FpvView#coverInsideBlock`). Das ist im Selbsttest ohne Fix reproduzierbar (Steinblock in die
  schwebende Drohne: 0,455 m im Block bis zum Ende; 0,6 m tief im Grasblock: bleibt 0,455 m unter der Oberkante). Der
  Server lehnt jede Bewegung im Block ab und setzt die Drohne auf seine Lage zurück – ebenfalls im Block.
- **Bruch:** Mit `drones_break_blocks` zerbrechen nur Glas und Töpfe; jeder Block kostet seine Bruchenergie
  (`BreakMath#of`, `DroneSim#breakThrough`). Ein Tunnel durch Gelände ist damit ausgeschlossen (JUnit unten).

## 2. Lösung (`drone.Depenetration`)

Vor jeder Bewegung prüft die Flugsitzung, ob die Box mehr als 1 mm (`TOLERANCE`) in einer Kollisionsform steckt. Wenn ja,
sucht `Depenetration#resolve` den kürzesten Ausweg entlang der sechs Achsrichtungen, höchstens 1,5 Blöcke (`REACH`):

1. **Nur durch die eigenen Blöcke:** Erlaubt ist ein Weg nur, wenn er außer den Blöcken, in denen die Box schon steckt,
   keinen Block berührt. Die Drohne verlässt also den Block, in dem sie steckt, und kommt nie durch eine fremde Wand,
   Decke, einen Boden oder eine Drohnen-Barriere auf die andere Seite. Bei gleichem Weg gewinnt „nach oben“, dann
   waagerecht, zuletzt „nach unten“.
2. **Sonst bleibt sie:** Gibt es keinen solchen Weg (ganz zugebaut, unter einem Deckel zwischen Nachbarblöcken), ändert
   sich nichts; dann gelten die Überlagerung im FPV-Bild und der Reset auf den Homepoint (`docs/design/home-point.md` §7).
   Eine erste Fassung ging dann senkrecht bis 4 Blöcke durch fremde Blöcke nach oben; das hätte Decken und
   Drohnen-Barrieren überwinden lassen und entfiel.

Der Ausweg verschiebt Entity und Flugmodell gleich (`DroneSim#pushOut`: Lage, Bildlage, Wirbel- und Navigationsbezug),
nimmt nur das Tempo in den Block hinein weg, behält das Tempo entlang der Fläche, zählt keinen Absturz und keinen
Aufprall. Pulverschnee zählt nicht (seine Form hängt vom Fallen ab und gehört zum Verfangen), ungeladene Chunks zählen
als fest.

Wo es wirkt:

- **Client, eigene Flugsitzung** (`PilotSession#moveEntity`), solange die Kollision mit Blöcken gilt (nicht im Geist-Modus
  der Add-on-Kollisionsregeln) und das Flugmodell nicht ruht. Eine ruhende, entschärfte Drohne, auf die jemand einen
  Block setzt, bleibt wie bisher im Block (Überlagerung, Reset); sobald sie aufwacht oder scharf geschaltet wird, kommt sie
  heraus.
- **Server, autonomer Flug** (`ServerFlight#move`, Heimflug und Rettung), mit denselben Bedingungen.
- **Server, abgelehnte Bewegung** (`DronePiloting#reject`): Meldet der Client eine freie Lage, die der Server wegen des
  Weges aus dem Block ablehnt, setzt der Reset die Drohne an den Ausweg, den der Server selbst aus seiner Lage findet
  (dieselben Regeln, also nie durch fremde Blöcke), statt zurück in den Block. Steckt auch die gemeldete Lage im Block,
  bleibt der Reset wie bisher.

Kosten: in freier Luft ein Blick auf die 1–8 Blöcke unter der Box je Bewegungsschritt; nur im Block die Suche über rund
100 Formen.

## 3. Tests

- JUnit `drone.CovDepenetrationTest` (frei und Berührung bleiben, 3 cm und 0,6 m im Boden nach oben, unter einem Deckel
  über einem Hohlraum weder durch Deckel noch Boden, ohne Deckel nach oben, Stein in der Luft zur nächsten Seite, Decke
  und Wand, teilweise in einer dicken Mauer zurück davor, Halbstufe, Gleichstand nach oben, zugebaut bleibt) und `sim.CovPushOutTest` (Lage und Bild verschoben, nur Tempo in den Block weg,
  Unsinn ignoriert; Glasbruch kostet je Block seine Energie, höchstens `E/E_Block` Blöcke).
- Selbsttest `drone: Ranken-Balken – …` (`interact.client.CovVineBeamSelfTests`) und Server-Rauchtest
  „Ranken-Balken und Drohne im Boden“ (`rescue.CovVineServerChecks`), Aufrufe in `docs/testing.md`.

## 4. Nachprüfung: Treffer von oben, Vollgas und keine Schwerkraft (Befund VINE2-1)

Im Video gibt die Drohne beim Treffer auf den Balken von oben Vollgas, obwohl der Pilot auf Gas 0 geht und die Knüppel
loslässt, und scheint danach keine Schwerkraft mehr zu haben; erst dann steckt sie im Balken. Das OSD zeigt dazu einen
Spannungseinbruch von 24,9 V auf 22,9 V über rund 1,7 s, also echte Last an den Props.

Nachgestellt mit demselben Szenario (5-Zoll, Acro, Airmode eingerastet, Gas 0 ab dem Treffer, Balken 1 Block breit mit
Ranken an den Seiten und darunter, Stürze von oben aus mehreren Lagen und Treffpunkten, dazu eine Drohne, die nach dem
Treffer im Balken steckt):

- **Stand 0.5.2** (Flugmodell aus dem Tag `v0.5.2`): Die Drohne liegt nach dem Treffer auf oder am Balken, der Regler
  kämpft gegen die blockierte Lage, I- und P-Anteil treiben die Motoren mit eingerastetem Airmode dauerhaft hoch, bis
  rund 2 × Gewicht Schub bei Gas 0 (97 von 175 Stürzen); einmal stützt die Drohne 9 cm über dem Balken 4,5 s lang, ohne
  zu fallen; steckt sie im Balken, bleibt sie darin (40 von 40) und drückt mit bis 3,7 × Gewicht. Das ist die Abfolge
  aus dem Video.
- **Stand 0.6.0:** Der Dauerschub ist der Fehler aus CRASHARM-1 (Regler kennt den Blockkontakt, `docs/design/ground-contact.md`
  §8), das Steckenbleiben der aus VINE1-1 (§2), das Stützen über der Kante beseitigt die Auflage je Messpunkt
  (`sim.GroundSupport`). Ohne den Regler-Teil von CRASHARM-1 sind 102 von 175 Stürzen und 25 von 40 Fällen im Balken
  wieder rot (bis 2,7 bzw. 3,7 × Gewicht), ohne `Depenetration` bleiben 40 von 40 Fällen im Balken stecken.
- Mit beiden Fixes bleibt nur das kurze Zucken durch Airmode (längster Stoß über 50 % Motorleistung 0,08 s, im Spiel
  0,15–0,2 s), danach Leerlauf (5 % Schub), die Drohne fällt frei und dringt nie ein. Ein neuer Fix ist nicht nötig;
  wie die Drohne unter 0.5.2 in den Balken kam, bleibt offen (§1).

Tests: JUnit `sim.CovVineBeamTopTest`, Selbsttest `drone: Ranken-Balken – von oben`
(`interact.client.CovVineBeamTopSelfTests`) und Beitrittstest `PROPWASH_JOINTEST_COV_VINE`, Aufrufe in
`docs/testing.md`.
