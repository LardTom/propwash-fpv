# Gefüllte Kessel

Für Spieler und Server-Betreiber. Eine Drohne in einem gefüllten Kessel verhält sich wie in seinem Inhalt: Wasser wie
Wasser, Lava wie Lava, Pulverschnee wie Pulverschnee. Bis 0.6.0 hatten gefüllte Kessel keine Wirkung, eine Whoop flog in
einen Wasser- oder Lavakessel, und nichts passierte (Befund OWNP6-7, Discord 26.10.2026). Pulverschnee bremst Drohnen
seit der Entscheidung vom 28.10.2026 (Befund DEC2810-1) wie Vanilla-Entities, als Block und im Kessel.

## 1. Was passiert

- **Wasserkessel**: Liegt der Schwerpunkt der Drohne im Becken unterhalb der Füllhöhe, ist sie im Wasser. Eine Drohne
  ohne Wasserschutz geht wie im Wasserblock nach 0,3 s aus (Grund `FLUID`) und lässt sich dort nicht scharf schalten
  (Sperre `IN_FLUID`). Eine wasserdichte Drohne schwimmt wie im Wasserblock, die Wasseroberfläche liegt auf der
  Füllhöhe.
- **Stehendes Wasser** (Befund CAULD2-1, Playtest 28.10.2026): Wind und Böen wirken nur auf den Teil der Drohne, der in
  der Luft ist. Vorher rechnete das Flugmodell den 400-fachen Wasserwiderstand gegen die Luftbewegung, das Wasser floss
  also mit dem Wind. Im offenen Kessel (Wind am Boden rund 0,8 m/s, bei Regen bis rund 3 m/s) zog es die Drohne an die
  Kesselwand, im windgeschützten Wasserblock mit einem Viertel davon. Eine wasserdichte Whoop kam im Selbsttest so mit
  Vollgas in 5 s nicht aus einem Wasserblock heraus. Jetzt steht das Wasser im Kessel wie im Block: ohne Knüppel keine
  seitliche Drift (gemessen höchstens 0,002 m/s), die Steuerung bleibt, Vollgas trägt eine wasserdichte Drohne heraus.
  Eine Drohne ohne Wasserschutz geht weiter aus und sinkt senkrecht.
- **Lavakessel**: wie Lava, die Drohne geht sofort aus und lässt sich darin nicht scharf schalten. Sie fängt kein Feuer
  und nimmt keinen Schaden, genau wie in einem Lavablock.
- **Pulverschneekessel**: wie ein Pulverschneeblock, also keine Flüssigkeit: Die Drohne bleibt scharf. Im Pulverschnee
  wird sie aber stark gebremst und sinkt langsam ein, wie Vanilla-Entities (Abschnitt 3). Mit Gas kommt sie wieder heraus.
- **Leerer Kessel**: keine Wirkung.
- Dasselbe gilt für Heimflug, Sinkflug und Add-on-Flüge am Server.

## 2. Füllhöhe

Gezählt wird der Innenraum des Kessels (2 bis 14 Pixel waagrecht), vom Boden des Beckens (4 Pixel) bis zur Oberkante
des Inhalts, so wie Vanilla den Inhalt für Entities formt:

| Kessel | Stufe 1 | Stufe 2 | Stufe 3 |
|---|---|---|---|
| Wasser, Pulverschnee | 9 px | 12 px | 15 px |
| Lava | – | – | 15 px |

Über der Füllhöhe und in der Wand ist keine Flüssigkeit und kein Pulverschnee. Eine Whoop am Boden des Beckens liegt
bei jeder Stufe im Inhalt.

## 3. Pulverschnee

Vanilla bremst ein Entity im Pulverschnee jeden Tick auf null und lässt nur das 0,9-Fache (waagrecht) bzw. 1,5-Fache
(senkrecht) der Bewegung dieses Ticks zu. Für das Flugmodell der Drohne heißt das eine lineare Bremse von
1 / (1,5 × 0,05 s) ≈ 13,3/s auf jede Bewegung, gerechnet wie bei Spinnweben an den vier Props und der Rahmenmitte
(Werkstoff `POWDER_SNOW`, `docs/design/drone-snags.md`). Kein Festhalten, keine Last an den Props, kein Verschleiß.

| Was | Wirkung (JUnit `PowderSnowFlightTest`) |
|---|---|
| Leerlaufgas | Whoop und 5-Zoll sinken mit rund 0,7 m/s ein (frei fallend nach 1,5 s über 5 m/s) |
| 5-Zoll mit 20 m/s hinein | steht nach rund 1,4 m, kein Aufprall, kein Absturz |
| Whoop mit 5 m/s hinein | steht nach rund 0,3 m |
| Vollgas | steigt heraus, aber langsamer als in Luft |

- **Pulverschneeblock:** der ganze Block, über das Block-Tag `propwash:drone_snag_powder_snow` (Datenpakete können
  Blöcke dazunehmen oder herausnehmen). Eine neue Kollisionsform bekommt der Block dadurch nicht.
- **Pulverschneekessel:** nur das Becken (2 bis 14 Pixel waagrecht) vom Boden (4 Pixel) bis zur Füllhöhe der Stufe
  (9, 12 oder 15 Pixel). Darüber und in der Wand bremst nichts.
- Bröckchen und Klang (wie bei Spinnweben, an scharfen Drohnen mit drehenden Props) kommen auch im Kessel vom
  Pulverschnee.

## 4. Für Server-Betreiber

Für Kessel gibt es kein Block-Tag. Gezählt werden die drei Vanilla-Kessel `minecraft:water_cauldron`,
`minecraft:lava_cauldron` und `minecraft:powder_snow_cauldron`; Kessel aus anderen Mods wirken wie ein leerer Kessel.
Welche Blöcke als Pulverschnee bremsen, steht im Block-Tag `propwash:drone_snag_powder_snow` (Standard:
`minecraft:powder_snow`).

## 5. Technik

- `drone/DroneFluids#at` liefert die Flüssigkeit am Schwerpunkt für das Flugmodell am Client (`PilotSession`) und am
  Server (`ServerFlight`): zuerst wie bisher der Flüssigkeitszustand des Blocks, sonst der Kessel.
- `drone/CauldronContent` (ohne Minecraft-Klassen) kennt Füllhöhe, Becken und Oberfläche je Inhalt.
- `drone/WaterSurface#relative` meldet im Wasserkessel und im Block darüber die Oberfläche relativ zum Schwerpunkt, wie
  bei einem Wasserblock.
- Stehendes Wasser: `sim/DroneSim` rechnet die Anströmung von Rumpf und Props gegen das Medium. Wind und Böen gehen
  darin nur mit dem Kehrwert des Widerstandsfaktors ein: `1 / (1 + Eintauchgrad × 399)` für wasserdichte Drohnen,
  `1/400` ohne Wasserschutz im Wasser, in Luft 1. Der Eintauchgrad wird dafür vor der Anströmung bestimmt. Das gilt für
  jeden Wasserblock und jeden Wasserkessel, am Client wie im Heimflug am Server. In Luft bleibt alles bitgleich.
- Pulverschnee: `CauldronContent#snag` nennt den Werkstoff des Inhalts (nur Pulverschnee hat einen),
  `interact/SnagBlocks` gibt für den Pulverschneekessel das Becken bis zur Füllhöhe als Form zurück (`SnagBlocks#shape`,
  auch für Bröckchen und Klang in `SnagEffects`, dort mit dem Aussehen des Pulverschnees, `SnagBlocks#look`). Die
  Abtastung `SnagSampler` läuft am Client (`PilotSnags`) und am Server (`DroneSnags.Flight` in `ServerFlight`) wie für
  Spinnweben.
- Ohne Kessel und ohne Pulverschnee bleibt alles bitgleich; Goldens und Kalibrierung ändern sich nicht.
- Nicht enthalten: Eine lose Drohne ohne Pilot fällt in einem Wasserkessel nicht langsamer (wie Vanilla-Items, die im
  Kessel auch nicht schwimmen), und das FPV-Bild zeigt im Kessel keinen Unterwasser- oder Lava-Nebel.

## 6. Tests

- JUnit: `CauldronContentTest` (Füllhöhe, Becken, Wand, Oberfläche, Whoop am Boden, nur Pulverschnee bremst) und
  `CauldronFluidTest` (Flugmodell: Wasserkessel entschärft nach 0,3 s, Lavakessel sofort, Pulverschneekessel nicht),
  `sim.PowderSnowFlightTest` (Einsinken, Bremsen, Herauskommen mit Vollgas, im Kessel nur im Becken bis zur Füllhöhe)
  und `interact.PowderSnowSnagTest` (Tag, Werkstoff aus dem Vanilla-Faktor, kein Halten, keine Prop-Last, kein
  Verschleiß).
- Selbsttest `drone: Kessel` (`drone.client.CauldronSelfTests`, beide Loader): je Kesselart eine scharfe Whoop im
  passenden Block und im Kessel, dazu der Heimflug am Server (`rescue.CauldronServerFlights`). Bei Pulverschnee startet
  die Whoop 0,5 m über dem Boden und muss im Block wie im Kessel gebremst langsam einsinken; der Heimflug am Server
  bremst dort auch. Wasser, Lava und der leere Kessel bremsen nicht. Der Schritt „Füllhöhe“ prüft jede Stufe gegen die
  Vanilla-Form des Inhalts und beim Pulverschnee die Form der Bremse. Aufruf und Erwartung in `docs/testing.md`.
- Stehendes Wasser (Befund CAULD2-1): JUnit `sim.CauldronStillWaterTest` (Whoop und 5-Zoll, wasserdicht im Wasserkessel
  und im Wasserblock bei Wind 0,8 und 3,1 m/s ohne Knüppel höchstens 0,05 m/s seitlich; ohne Wasserschutz entschärft,
  sinkt senkrecht; Nicken kippt und bewegt die Drohne im Kessel, Vollgas trägt sie heraus; in der Luft trägt der Wind sie
  weiter). Selbsttest `drone: Kessel` mit vier Schritten aus `drone.client.CauldronDriftSelfTests`: wasserdicht und ohne
  Wasserschutz im Wasserblock und Wasserkessel, dazu Lava- und Pulverschneekessel, je 2,5 s ohne Knüppel, höchstens
  0,05 m/s seitlich; wo die Drohne scharf bleibt, trägt Vollgas sie 1,5 m über den Block.
- Lava mit fester Startlage (Befund WAD-3): Im Lava-Schritt liegt die entschärfte Drohne im Lavablock und im Lavakessel
  von Anfang an auf dem Boden und darf in der Messung höchstens 2 cm fallen. Vorher fiel sie aus 0,25 m frei auf den
  Boden. Der Wind dreht mit der Spielzeit (`WindModel`), und bei manchen Richtungen kippte die Whoop beim Aufprall und
  rutschte bis 12 cm. Nach 10 Ticks Vorlauf zählte das als Drift: 0,058 m/s im vollen Fabric-Lauf, gefiltert grün. Lava
  selbst zieht die Drohne nicht seitlich. Mit fester Startlage bleibt sie bei 16 Spielzeiten und 24 Windrichtungen
  ruhig liegen (0,000 m/s, Versatz 0,000 m).
