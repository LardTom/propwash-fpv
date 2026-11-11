# Drohnen und Bosse

Für Spieler und Server-Betreiber. Ab 0.6.0 reagieren der Wärter, der Enderdrache, der Wither und die Windkugeln der Breeze
auf Drohnen. Bis dahin waren Drohnen für sie Luft: Eine Drohne ist kein Lebewesen, Vanilla wählt sie nie als Ziel, und
Stöße kamen bei einer geflogenen Drohne nur an, wenn ein Add-on den Rückstoß übernahm. Wunsch des Besitzers (rc.4.1,
Befund OT4-5). Gezielte Angriffe normaler Mobs bleiben Sache von Mob Defense; hier stehen nur Bosse, Wärter und die
Physik der Windkugeln.

## 1. Was passiert

### Wärter

- **Hören:** Der Wärter hört die Drohne über die Vibrationen aus [`drone-sculk.md`](drone-sculk.md) (scharf, Motoren ab
  10 % der Höchstdrehzahl, alle 10 Ticks `elytra_glide`, Absturz `hit_ground`). Wolle schirmt wie gewohnt ab. Ohne die
  Welt-Regel „Drohnen lösen Sculk aus“ hört er nichts.
- **Zorn:** wie bei Spielern. Jede gehörte Vibration bringt 35 Punkte, höchstens 150; der Zorn sinkt um 1 je Sekunde
  (20 Ticks); ab 80 ist der Wärter wütend. Ein Wärter hört höchstens alle 40 Ticks etwas, wütend ist er also nach der
  dritten Vibration (35, 68, 101), etwa 4 bis 6 Sekunden nach dem ersten Geräusch. Solange er eine Drohne hört, gräbt
  er sich nicht ein (Grab-Pause wie bei Spielern).
- **Angriff:** Wütend geht er zur Drohne. Ein Lebewesen als Ziel (Spieler, Mob) hat immer Vorrang. Ist die Drohne
  höchstens 15 Blöcke waagrecht und 20 senkrecht entfernt und in Sicht, lädt er den Schallschlag wie in Vanilla
  (Animation, Ladegeräusch, 34 Ticks), dann trifft der Strahl die Drohne, wenn sie noch in Reichweite ist. Der
  nächste Schallschlag kommt wie in Vanilla frühestens 100 Ticks nach dem Beginn des Ladens.
- **Treffer:** Schaden und Absturz nach den Crash-Regeln: Der Treffer zählt wie ein Aufprall mit der dreifachen
  Absturzgeschwindigkeit des Rahmens an allen Teilen (`crash_damage` und `crash_damage_scale` gelten), die Drohne wird
  mit 24 m/s in Strahlrichtung und 5 m/s nach oben weggeschleudert. Bei offenen Propellern brechen praktisch immer
  drei oder vier (Freestyle, Racer, Long Range), die Drohne stürzt ab; Propeller im Kanal (Whoop, Cinewhoop) behalten
  etwa 40 %. Motoren, Arme, Video und Elektronik nehmen ebenfalls Schaden. Der Treffer meldet `hit_ground` wie ein Absturz.
- **Dunkelheit im FPV-Bild:** Steht ein Wärter höchstens 20 Blöcke von der Drohne entfernt (sein Dunkelheits-Radius),
  pulsiert seine Dunkelheit durch das FPV-Bild: wie Vanillas Effekt „Dunkelheit“ mit 22 Ticks Einblenden, dem Puls alle
  4 Sekunden und 7 Sekunden Nachklang, nachdem die Drohne den Radius verlassen hat. Dazu dunkelt die Kamera nach,
  rauscht und verliert Farbe (Video-Effekt). Die Einstellung „Dunkelheitseffekt“ in den Zugänglichkeits-Optionen
  regelt die Stärke wie in Vanilla. Die Dunkelheit des Piloten selbst bleibt im FPV-Bild weiter aus.

### Enderdrache

- **Flügelschläge:** Gerät eine geflogene Drohne in den Bereich, in dem Vanilla Spieler mit den Flügeln wegstößt, wird
  sie waagrecht vom Drachen weggeschleudert (80 / Abstand m/s, höchstens 18 m/s, dazu 4 m/s nach oben; alle 5 Ticks
  einmal). Fliegt der Drache (Vanilla zählt Schweben und Sitzen nicht dazu), streift der Flügel die Drohne wie in
  Vanilla mit Schaden: 0,7-fache Absturzgeschwindigkeit, kein Absturz, höchstens jede Sekunde.
- **Turbulenz beim Vorbeiflug:** Im Umkreis von 14 Blöcken um den Drachen drückt sein Luftstrom die Drohne alle 5 Ticks
  für 0,25 s, weg vom Drachen, mit zufälliger Böe und nach unten. Die Kraft wächst mit der Nähe (quadratisch) und mit
  dem Tempo des Drachen, bis etwa das 2,4-fache des Gewichts der Drohne. Sie geht als Kraft in das Flugmodell, wie
  Wind. Auch ein schwebender Drache schlägt mit den Flügeln; nur wenn er auf dem Portal sitzt, ruht der Luftstrom.
- **Rammen:** Trifft Kopf oder Hals des Drachen eine Drohne, ist das ein harter Absturz (2,2-fache
  Absturzgeschwindigkeit) und die Drohne fliegt mit dem Tempo des Drachen plus 18 m/s weg. Offene Propeller sind
  danach meist hin.
- **Angriff:** Kreist der Drache und fliegt eine scharfe Drohne höchstens 48 Blöcke entfernt, greift er sie mit
  Wahrscheinlichkeit 1/3 je Sekunde an: Er wechselt in Vanillas Sturzflug auf ein Ziel (`charging_player`) und
  verfolgt die Drohne, bis er auf 10 Blöcke heran ist (höchstens 10 Sekunden). Der nächste Angriff auf eine Drohne
  kommt frühestens 20 Sekunden nach dem Beginn des letzten.
- **Drachenatem:** Eine Drohne in einer Wolke aus Drachenatem (Drachen-Feuerball oder Atem auf dem Portal, bis 2 Blöcke
  über der Wolke) verliert jede Sekunde 15 % an Video und Flugcontroller, 10 % am Akku, 8 % an jedem Motor und 5 % an
  jedem Propeller, mal `crash_damage_scale`. Nach 7 Sekunden ist das Video aus. Das gilt auch für abgestellte
  Drohnen.

### Wither

- Die beiden Seitenköpfe schießen ihre schwarzen Schädel auch auf geflogene Drohnen in Reichweite (20 Blöcke
  waagrecht, 8 senkrecht, höchstens 30 Blöcke) und in Sicht, je Kopf alle 2 bis 3 Sekunden. Ein Lebewesen als Ziel
  des Kopfes hat Vorrang. Ein Schädeltreffer richtet wie jeder Treffer Vanilla-Schaden an (8, eine Drohne zerbricht ab
  6), die Explosion stößt die Drohne weg.

### Windkugeln der Breeze

- Windkugeln der Breeze stoßen geflogene Drohnen weg, auch ohne Add-on. Bisher gab Propwash den Rückstoß einer
  Explosion nur an den Piloten weiter, wenn ein Add-on (Mob Defense) ihn übernahm ([`accessory-gaps.md`](accessory-gaps.md)
  §3, Standard-Skala 0). Mit der Regel an setzt Propwash den Faktor für Breeze-Windkugeln selbst auf 1; ein Add-on kann
  ihn wie bisher ändern oder abbrechen. Dasselbe gilt für die Explosion eines Wither-Schädels und des Withers selbst.
- Windkugeln von Spielern (Hand, Dispenser-Block, Werfer an einer Drohne) bleiben wie bisher Sache eines Add-ons: Auf
  Renn-Servern wären sie ein Griefing-Weg (E16 in `accessory-gaps.md`).

## 2. Für Server-Betreiber

- Welt-Regel `propwash:drones_boss_interactions` („Bosse kämpfen gegen Drohnen“, Gruppe Spiel, Standard an), setzbar im
  Tab „Welt-Regeln“ oder mit `/gamerule propwash:drones_boss_interactions false`. Aus: Wärter, Drache und Wither lassen
  Drohnen in Ruhe, kein Dunkelheits-Puls im FPV-Bild, Breeze-Windkugeln stoßen Drohnen nur, wenn ein Add-on den
  Rückstoß übernimmt. Die Regel zählt für Rennen (aus = leichter), alle drei Voreinstellungen lassen sie an.
- Schaden folgt `crash_damage` und `crash_damage_scale`, Treffer am Wither-Schädel dem Drohnen-Schaden aus Vanilla.
- Mob Defense: Dessen Reaktionen auf normale Mobs bleiben, wie sie sind. Ein eigener Angriff von Mob Defense auf
  Wither, Drache, Wärter oder Breeze würde den Kern doppeln.
- Kein neues Netzpaket, das Netzprotokoll bleibt. Stöße und Kräfte gehen über das vorhandene Paket `drone_push`,
  `WorldRulesS2C` trägt einen Eintrag mehr, das Register `minecraft:game_rule` eine Regel mehr. Der Client erkennt
  Wärter in seiner Nähe selbst.
- Kosten: je Tick ein Blick auf die Drachen der Welt und die Wärter, die eine Drohne gehört haben; je geflogener Drohne
  alle 10 Ticks eine Suche nach Withern, je Drohne jede Sekunde eine Suche nach Atemwolken; je Client-Tick im FPV eine
  Suche nach Wärtern im Umkreis von 20 Blöcken.

## 3. Technik

- `interact.DroneBosses` (Server): `serverTick` am Ende jedes Server-Ticks (angemeldet in `InteractModule`),
  `wardenAngered` aus `mixin.drone.WardenMixin` (Kopf von `Warden#increaseAngerAt(Entity, int, boolean)`),
  `dragonWing` und `dragonHead` aus `mixin.drone.EnderDragonMixin` (Kopf von `EnderDragon#knockBack` und `#hurt(ServerLevel, List)`),
  Wither-Schüsse über `mixin.drone.WitherBossAccessor` (`performRangedAttack(int, double, double, double, boolean)`).
  Ein Treffer (`hit`) rechnet den Verschleiß, gibt ihn auch an das Flugmodell eines Server-Flugs, stößt die Drohne über
  `accessory.DronePushes` und meldet bei einem Absturz `DroneVibrations.crashed`.
- `interact.BossHit` (Arten mit Absturz-Faktor, Stoß und Auftrieb), `interact.BossHits` (Rechnung: Aufprall-Probe für
  `sim.CrashDamage`, Drachenatem, Stöße, Turbulenz), `interact.WardenGrudge` (Zorn je Drohne mit Abbau, Ladezeit und
  Pause des Schallschlags).
- `drone.Knockback#forwards`: Rückstoß an den Piloten, wenn ein Add-on zuhört oder die Regel an ist und die Quelle
  eine Windkugel der Breeze, ein Wither-Schädel oder der Wither ist (`DroneBosses.pushingSource`); dann ist der Faktor
  vorab 1.
- Client: `video.DarknessPulse` (Einblenden, Nachklang, Puls wie Vanilla), `video.client.BossDarkness` (Wärter im
  Umkreis, Tick aus `VideoState#onTick`), `FpvView#clearPilotLightEffects` setzt Helligkeit und Dunkelheits-Skala des
  Lichts, `FeedInput#darkness` und `FeedModel` dunkeln das Kamerabild ab und lassen es rauschen.

## 4. Tests

- JUnit `interact.DroneBossesTest` (12): Regel (Standard an, Gruppe Spiel, zählt für Rennen, in allen Voreinstellungen
  an, Texte), Zorn wie bei Spielern (35, 68, 101; Abbau; Höchstwert 150), Wahl der Drohne mit dem meisten Zorn,
  Schallschlag-Takt (34 Ticks laden, 66 Ticks Pause, Reichweite 15/20), Schallschlag an allen fünf eingebauten
  Drohnen, Crash-Regeln (0 % = kein Schaden, 300 % mehr als 100 %), Rammen ja, Flügel nur Streifschaden, Drachenatem
  (Video nach 7 Sekunden aus), Stöße und Grenze 50 N·s, Turbulenz, Weitergabe des Rückstoßes ohne Add-on.
- JUnit `video.DarknessPulseTest` (4): Einblenden und Nachklang, Puls mit der Vanilla-Periode, dunkleres und
  rauschendes Bild, ohne Wärter byte-gleich wie vorher.
- JUnit `race.RaceRulesTest`: Regel als rennrelevant eingeordnet.
- Selbsttest-Gruppe `bosses:` (11 Schritte, `interact.client.DroneBossSelfTests`), Bildschirmfotos `boss_*_fpv`.
