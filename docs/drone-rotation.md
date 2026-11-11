# Drohnen per `/tp` und `/rotate` drehen

Vanilla-Befehle, die die Blickrichtung einer Entity setzen, drehen auch eine Drohne. Die Drohne führt ihre Lage als
eigenes Quaternion (Feld `Orientation`, synchronisiert); die Befehle setzen dieses Feld jetzt mit:

```mcfunction
/tp @e[type=propwash:drone,limit=1,sort=nearest] ~ ~ ~ -90 0
/rotate @e[type=propwash:drone,limit=1,sort=nearest] 45 0
/rotate @e[type=propwash:drone,limit=1,sort=nearest] facing ~ ~ ~5
/execute as @e[type=propwash:drone] at @s run tp @s ~ ~ ~ 180 10
```

## 1. Regeln

- Lage = Gier aus der Vanilla-Gier (`yRot`), Nick aus dem Vanilla-Nick (`xRot`, positiv = Nase nach unten wie beim
  Spieler, wie in Vanilla auf ±90° begrenzt), Rollen 0. Gier 0 = Nase nach Süden (+Z), −90 = Osten (+X), 90 = Westen,
  180 = Norden.
- Ein Teleport ohne Rotationsangabe (`/tp … ~ ~ ~`, `/tp … <x> <y> <z>`) ändert die Lage nicht. Wie in Vanilla bringt
  dagegen `/tp <Drohne> <Entity>` die Blickrichtung des Ziels mit, `/spreadplayers` setzt die eigene Gier (gerade,
  ohne Rollen), ein quer stehendes Netherportal dreht um 90°.
- Relative Angaben (`~10 ~`) rechnet Vanilla gegen die Blickrichtung des Befehlsgebers, mit `execute as … at @s`
  also gegen die Drohne selbst.
- `facing` (bei `/tp` und `/rotate`) dreht die Drohne ebenso.
- Der Server entscheidet: Die neue Lage steht sofort im synchronisierten Lagefeld. Jeder Client sieht sie, auch wer
  später dazukommt (Entity-Daten beim Erscheinen). Zuschauer drehen über die zwei Interpolationsschritte der Drohne
  gleichmäßig nach, ohne Rücksprung.
- Am Boden richtet sich eine gekippte Drohne wie bisher auf (24° je Tick); ein Nick ist also nur in der Luft zu sehen.
  Vanilla-`/tp` meldet die Drohne danach als „am Boden“, deshalb richtet sich auch eine schwebende Drohne ohne Schwerkraft
  nach einem Teleport mit Nick im nächsten Tick auf; `/rotate` tut das nicht.

## 2. Fliegende Drohne mit Pilot

- Der Server zählt wie beim Reset die Reset-Epoche hoch: Flugzustände, die der Pilot noch mit der alten Lage geschickt
  hat, verwirft er, bis der Client die neue Lage übernommen hat. So springt die Lage weder beim Server noch bei Zuschauern
  zurück.
- Der Pilot bekommt das Paket `propwash:drone_reset` mit zwei Merkern: **Tempo behalten** (Geschwindigkeit im
  Weltsystem bleibt, der Drehimpuls wird mitgedreht, die Drehraten im Drohnensystem bleiben also gleich) und **Position
  behalten** (bei `/rotate` und bei einem Teleport ohne Ortswechsel bleibt die eigene Position des Piloten, sonst gilt
  die Server-Position des Teleports). Scharf bleibt scharf, kein Absturz, kein Verschleiß; das FPV-Bild schneidet wie
  beim Reset.
- Paketformat: Statt des bisherigen Wahrheitswerts `home` steht dort ein Merker-Byte (Bit 0 Heim-Reset, Bit 1 Tempo
  behalten, Bit 2 Position behalten). Ein normaler Reset ist damit Byte für Byte wie vorher.
- Teleport ohne Rotation einer geflogenen Drohne: unverändert.

## 3. Grenzen

- Server-Flug (GPS-Rescue-Heimflug, Autonomie eines Add-ons): Der Flugregler des Servers führt die Lage selbst. Eine
  Drehung ohne Ortswechsel überschreibt er im nächsten Tick; ein Teleport um mehr als 0,75 m setzt den Flug wie bisher
  neu auf, dann mit der neuen Lage und Tempo 0 (`rescue.ServerFlight`).
- Unverändert: Flugmodell, Kalibrierung und Goldens, Reset und Homepoint (`QuadcopterEntity#resetTo`), Speicherformat.

## 4. Code

- `drone.QuadcopterEntity#teleportSetPosition`, `#forceSetRotation` und `#lookAt` (Haken, nur auf dem Server) rufen
  `#turnToVanillaRotation`: Lage setzen, bei einer geflogenen Drohne Epoche hoch und `DronePiloting#sendTurn`.
- `drone.VanillaRotation`: `attitude(yRot, xRot, dest)`, `turns(…)` (keine Drehung nur bei relativer Gier und relativem
  Nick ohne Änderung, so kodiert Vanilla „keine Rotationsangabe“), `carryMotion(SimSnapshot, Quaterniondc)`.
- `drone.net.DroneResetS2C` (Merker), `drone.client.PilotSession#onReset`, `drone.client.DroneClient#onReset`.

## 5. Tests

- JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.drone.VanillaRotationTest --select-class
  de.lardtom.propwash.drone.net.DroneResetS2CTest`.
- Selbsttest `'-PpropwashSelftestOnly=drone: Lage per'` (beide Loader, vier Schritte in
  `drone.client.TeleportRotationSelfTests`, Platz bei Spieler + (−14, −6)): stehende Drohne mit `/tp ~ ~ ~ -90 0`,
  `/rotate 45 0`, `/tp ~ ~0.25 ~` ohne Rotation und `/rotate … facing ~3 ~ ~`; schwebende Drohne ohne Schwerkraft mit `/rotate 30 20`; fliegende
  Drohne im FPV mit 6 m/s nach Westen mit `/rotate 90 0`, `/tp ~ ~ ~ -90 0` und `/tp ~2 ~ ~ 180 0`. Bildschirmfotos
  `drone_tp_rotate_standing` und `drone_tp_rotate_flying` (mit `-PpropwashScreenshots=true` auf Fabric in
  `docs/screenshots/`, sonst in `screenshots/` im Run-Ordner).
- Beitrittstest (`docs/testing.md` §3): Server mit `PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_DRONE_ROTATION=1`,
  Client mit `PROPWASH_JOINTEST_LABEL=dronerotation PROPWASH_JOINTEST_DRONE_ROTATION=1
  PROPWASH_JOINTEST_EXPECT='joined|drone_rotation=ok'` (`dev.DroneRotationJoinCheck`, `drone.client.DroneRotationJoinProbe`):
  eine vor dem Beitritt gedrehte Drohne erscheint beim Client gleich mit der neuen Lage, eine Drohne vor dem Spieler
  folgt `/tp`, `/rotate` und einem Teleport ohne Rotation.
