# Geräusche an der Drohne (Hören durch die Drohne)

Für Spieler, Server-Betreiber und Add-on-Autoren. Wer durch eine Drohne schaut, hört die Welt dort, wo die Drohne ist –
auch wenn die eigene Spielfigur weit weg steht. Vorher schickte der Server positionsgebundene Geräusche nur an Spieler,
deren Spielfigur in Reichweite der Quelle stand: Feuerte ein Pilot im FPV 200 Blöcke von seinem Körper entfernt einen
Werfer ab, hörte er nichts (Befund SND1-1, 0.6.0).

## 1. Wer an der Drohne hört

| Spieler | Hörpunkt am Server |
|---|---|
| Pilot im FPV | Körper und Drohne |
| Zuschauer per F7 oder Videokanal, zweiter Operator am Gimbal | Körper und die Drohne, durch die er schaut |
| Pilot auf Sicht, alle anderen | nur der Körper (wie Vanilla) |

Für jedes Geräusch zählt der nähere der beiden Hörpunkte. Die Freecam ist nicht dabei: Der Server erfährt nichts von ihr
(`docs/freecam.md`).

## 2. Was ankommt

Alles, was der Server an Spieler in Reichweite einer Stelle schickt:

- Geräusche (`Level.playSound`, `playSeededSound`, `Entity.playSound`, also auch Mobs, Blöcke und Zubehör),
- Level-Events in 64 Blöcken (Werfer und Dispenser, Blockbruch, Türen, Rauch des Werfers),
- Block-Events in 64 Blöcken (Notenblock, Kiste, Kolben),
- Explosionen in 64 Blöcken (Knall und Partikel; der Rückstoß gilt weiter nur dem Körper).

Regeln:

- **Reichweite wie Vanilla:** Lautstärke × 16 Blöcke bzw. die feste Reichweite des Geräuschs, 64 Blöcke für Level-,
  Block-Events und Explosionen – gemessen vom näheren Hörpunkt.
- **Keine Doppelten:** Jeder Spieler bekommt jedes Paket höchstens einmal, auch wenn Körper und Drohne beide in
  Reichweite sind.
- **Ausgenommene bleiben ausgenommen:** `playSound(player, …)` und `levelEvent(player, …)` schicken dem genannten Spieler
  nichts; sein Client spielt den Ton selbst (Vanilla-Vorhersage, in Propwash etwa, wenn die Drohne einen Block
  zerbricht).
- Nur in derselben Dimension, nur auf dem Server-Thread; ohne Piloten und Zuschauer läuft Vanilla unverändert.

## 3. Was der Spieler hört

Der Client hört an der Kamera. Im FPV und beim Zuschauen ist das die Drohnenkamera mit Lage, Blickrichtung und oben der
Drohne; Abschwächung und Richtung rechnet der Client also von der Drohne aus. Ein Geräusch neben dem Körper ist im FPV
leise oder nicht zu hören, so als stünde der Pilot an der Drohne.

## 4. Für Add-on-Autoren

- Geräusche einer Drohnen-Aktion an der Drohne oder am Ziel abspielen, mit `null` als ausgenommenem Spieler:
  `level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), …)`, `level.levelEvent(null, …)` oder
  `drone.playSound(…)`. Den Rest erledigt Propwash.
- Nicht an der Position des Spielers abspielen und nicht nur an den Piloten schicken (`player.playNotifySound`, eigenes
  `ClientboundSoundPacket`): Die Hörposition liegt im FPV an der Drohne, der Ton käme vom Körper.
- Wer den Piloten ausnimmt und ihm das Paket selbst schickt, bekommt weiter genau ein Paket beim Piloten. Ab dieser
  Version ist das unnötig.
- Hinweis-Töne (OSD, Funke, Planer) bleiben Oberflächen-Töne (`SimpleSoundInstance.forUI`) ohne Ort.

## 5. Nicht dabei

- Globale Level-Events (Wither, Enderdrache, Endportal): Sie hört jeder Spieler, die Richtung rechnet Vanilla weiter vom
  Körper.
- Partikel (`sendParticles`) und die Riss-Anzeige beim Abbauen haben eigene Reichweiten vom Körper.
- Die Freecam (siehe §1) und Kamera-Lagen für Flashback (`docs/camera-chunks.md`): Wer nicht durch die Drohne schaut,
  hört weiter am Körper.

## 6. Technik

- `mixin/audio/PlayerListMixin`: In `PlayerList.broadcast(except, x, y, z, range, dimension, packet)` liefern
  `getX`/`getY`/`getZ` je Spieler den näheren Hörpunkt (`@WrapOperation`, Wahl einmal je Spieler über `@Share`). Die
  Abfrage `player != except`, die Dimension und der Vergleich mit `range` bleiben Vanilla. Darüber laufen
  `playSeededSound` (beide Formen), `levelEvent` und die Block-Events.
- `mixin/audio/ServerLevelMixin`: In `ServerLevel.explode` misst `distanceToSqr` den kleineren Abstand aus Körper und
  Drohne.
- `audio.DroneHearing` sucht die Drohne des Spielers (`SpectatorLinks.watched`, sonst `DronePiloting.pilotedBy` mit
  `PilotMode.FPV`), `audio.HearingPoints` hält die Regeln ohne Minecraft-Typen (JUnit).
- Die NeoForge-Ereignisse `PlayLevelSoundEvent` laufen vorher unverändert; ein abgesagtes Geräusch bleibt abgesagt.

## 7. Tests

JUnit `audio.HearingPointsTest` (Körper weit weg und Drohne nah → bekommt; beide nah → genau einmal; Zuschauer →
bekommt; ausgenommener Spieler → nicht; Pilot auf Sicht → nicht; Reichweite nach Lautstärke). Selbsttest
`audio: an der Drohne – …` (beide Loader) und Beitrittstest `PROPWASH_JOINTEST_DRONE_SOUND` am dedizierten Server (beide
Loader); Einzelheiten in `docs/testing.md`, Abschnitt „Geräusche an der Drohne“.
