# Drohnen und Sculk

Für Spieler und Server-Betreiber. Ab 0.6.0 hören Sculk-Sensoren, Sculk-Kreischer und Wärter fliegende Drohnen. Bis
dahin gab eine geflogene Drohne absichtlich keine Vibration ab: Ihre Bewegung kommt vom Client oder aus der eigenen
Physik, nicht aus der Vanilla-Bewegung, und `QuadcopterEntity#getMovementEmission` liefert `NONE`. Spielereignisse
gab es nur beim Glasbruch, beim Aufstellen und beim Werfer. Wunsch des Besitzers vom 09.11.2026.

## 1. Was auslöst

- **Flug:** Solange die Drohne scharf ist und ihre Motoren im Mittel mindestens 10 % der Höchstdrehzahl laufen, meldet
  der Server alle 10 Ticks (jede halbe Sekunde) das Spielereignis `minecraft:elytra_glide` an der Drohne, wie bei einem
  Spieler im Gleitflug (Frequenz 4 am kalibrierten Sensor und am Komparator). Das gilt für geflogene Drohnen und für
  autonome Flüge (GPS Rescue ohne Pilot, Autopilot von Add-ons).
- **Leerlauf:** Eine scharfe Drohne ohne Gas bleibt still. Mit dem Standard-Leerlauf (Motor-Idle 5,5 %) drehen die
  Motoren mit etwa 4 % der Höchstdrehzahl. Jede schwebende Drohne liegt deutlich über 10 %: Racer etwa 18 %, Freestyle
  21 %, Long Range 22 %, Cinewhoop 27 %, Whoop 40 % (JUnit `DroneVibrationsTest`).
- **Absturz:** Zählt die Flugphysik einen Absturz (derselbe Zähler wie die Statistik „Abstürze“), meldet der Server
  `minecraft:hit_ground` an der Drohne (Frequenz 2). Betroffener Block ist der Block unter der Drohne. Landet sie auf
  Wolle oder Teppich, dämpft Vanilla das Ereignis.
- **Abgestellte Drohnen:** Fällt eine Drohne ohne Pilot (eigene Physik des Servers), meldet Vanilla beim Aufsetzen
  `hit_ground` wie bei jedem Entity. Das war schon vor 0.6.0 so. Als Fallhöhe zählt nur der Weg, den die Drohne
  wirklich gefallen ist (siehe §4).
- **Nach der Landung eines Server-Flugs:** Landet ein Flug ohne Pilot (Autopilot oder Flugplaner-Mission eines Add-ons,
  Heimflug, Sinkflug ohne Budget) und entschärft, bleibt die Drohne still, auch wenn sie mitten zwischen Sensoren liegt.
  Ein Server-Flug lässt keine Fallhöhe zurück; das Aufsetzen am Ende des Flugs darf wie jedes Aufsetzen einmal
  `hit_ground` melden. Bis 0.6.0-rc.4 lösten die Sensoren danach weiter aus (Befund OT1-2, Ursache in §4).
- **Nicht:** unbewaffnete Drohnen, auch mit Pilot, und scharfe Drohnen im Leerlauf.

## 2. Was Vanilla regelt

- **Wolle** zwischen Drohne und Sensor schirmt ab (Block-Tag `minecraft:occludes_vibration_signals`), Wolle und Teppich
  unter dem Aufprall dämpfen (`minecraft:dampens_vibrations`). Reichweite: Sculk-Sensor 8 Blöcke, kalibrierter Sensor
  und Wärter 16.
- **Sculk-Kreischer** schreien in Vanilla nur für Spieler, für Projektile und geworfene Items von Spielern und für
  Fahrzeuge mit einem Spieler am Steuer. Propwash rechnet eine Drohne mit Pilot dem Piloten zu, so wie ein Fahrzeug
  seinem Steuernden. Die Warnstufe steigt beim Piloten, ab Stufe 4 erscheint ein Wärter am Kreischer. Dunkelheit
  bekommen wie in Vanilla nur Spieler in der Nähe des Kreischers. Drohnen ohne Pilot (abgestellt oder autonom) lösen
  keinen Kreischer aus, genau wie Mobs.
- **Wärter** werden auf die Drohne nicht wütend, sie ist kein Lebewesen. Sie gehen aber zu der Stelle, an der sie die
  Drohne gehört haben. Den Piloten greift ein Wärter nur an, wenn er ihn selbst bemerkt.

## 3. Für Server-Betreiber

- Welt-Regel `propwash:drones_trigger_sculk` („Drohnen lösen Sculk aus“, Gruppe Spiel, Standard an), setzbar im Tab
  „Welt-Regeln“ oder mit `/gamerule propwash:drones_trigger_sculk false`. Aus: Drohnen sind für Sculk und Wärter
  lautlos, auch beim Glasbruch und beim Aufsetzen abgestellter Drohnen. Die Regel ist für Rennen neutral, sie ändert weder
  Physik noch Wertung. Alle drei Voreinstellungen lassen sie an.
- Kein neues Netzpaket, das Netzprotokoll bleibt. Das Welt-Regel-Paket `WorldRulesS2C` trägt einen Eintrag mehr, das
  Register `minecraft:game_rule` hat eine Regel mehr.
- Kosten: ein Vergleich je Drohne und Tick, alle 10 Ticks ein Spielereignis je fliegender Drohne.

## 4. Technik

- `interact.DroneVibrations`:
  - `tick(drone, level)` steht in `QuadcopterEntity#tickServer` nach `DroneBlockTriggers.tick`.
  - `crashed(drone)` rufen `stats.PilotStats#onCrash` (Paket `CrashC2S`, nach der Prüfung, ob der Absender die Drohne
    fliegt, nur für vom Budget zugelassene Abstürze) und `rescue.ServerFlight#fly` (autonome Flüge) auf.
  - `muted(drone)` ist `QuadcopterEntity#dampensVibrations`: Mit der Regel aus verwirft jeder Vibrations-Empfänger die
    Ereignisse der Drohne.
  - `pilot(entity)` liefert dem Mixin `mixin.drone.SculkShriekerBlockEntityMixin` an
    `SculkShriekerBlockEntity#tryGetPlayer` den Piloten.
- Fallhöhe (`QuadcopterEntity#checkFallDamage`, `DroneVibrations.fallMoved`): Vanillas `Entity#move` setzt eine
  Bewegung nicht um, wenn die Kollision sie auf weniger als etwa 0,3 mm kürzt (Länge im Quadrat unter 1e-7), ruft
  `checkFallDamage` aber trotzdem mit dem gekürzten Weg auf. Eine Drohne, die ein Server-Flug einen Hauch über dem Boden
  abstellt (gemessen bis 9e-7 m), fiel so bei jedem Schwerkraft-Schritt der eigenen Physik (alle 10 Ticks im
  Ruhezustand) „ein Stück“, ohne sich zu bewegen, und meldete jedes Mal `hit_ground`. Jetzt zählt nur der Weg zwischen
  der Höhe vor und nach `super.move`.
- Server-Flüge setzen die Fallhöhe nach jeder eigenen Bewegung zurück (`rescue.ServerFlight#move`,
  `rescue.AutonomousReturns#descend`). Vorher sammelte ein Flug alle Sinkstrecken (etwa 3 m in einer kurzen Mission)
  und gab sie beim ersten Aufsetzen nach dem Flug als `hit_ground` ab. Drohnen mit Pilot setzt der Server nur per
  Position, dort gab es nie eine Vanilla-Fallhöhe.
- Quelle des Spielereignisses ist immer die Drohne. `getMovementEmission` bleibt `NONE`, es gibt also keine Schritt-,
  Schwimm- und Flattergeräusche aus der Vanilla-Bewegung.

## 5. Tests

- JUnit `interact.DroneVibrationsTest`: Schwelle und Takt; Leerlauf und Schweben aller fünf eingebauten Drohnen in der
  Flugphysik, mit den Motorwerten, die der Client an den Server schickt; Welt-Regel (Standard an, neutral, in allen
  Voreinstellungen an, Texte in Deutsch und Englisch); Fallhöhe mit Vanillas Kollision (`Shapes.collide`): eine
  Drohne 1e-9 bis 3e-4 m über einem Block bewegt sich nicht und meldet kein `hit_ground` mehr, ein echter Fall (3 cm
  bis 2 m) zählt weiter.
- Selbsttest-Gruppe `sculk:` (`interact.client.DroneSculkSelfTests`, Aufruf in `docs/testing.md`): eine Kammer aus
  Wolle mit einem offenen Sculk-Sensor, einem Sensor in Wolle und einem Kreischer. Geprüft wird: Eine unbewaffnete
  Drohne am Boden löst nichts aus. Eine scharfe Drohne im Leerlauf löst nichts aus. Eine schwebende Drohne löst den
  offenen Sensor mit Frequenz 4 aus, den Sensor in Wolle nicht, und der Kreischer schreit für den Piloten. Ein Absturz
  aus 10,5 m löst `hit_ground` (Frequenz 2) aus. Mit der Welt-Regel aus bleibt alles still.
- Selbsttest-Schritte `sculk: Mission …` (`interact.client.DroneSculkMissionSelfTests`): eine Wollkammer mit 27
  Sculk-Sensoren im Schachbrett. Ein Add-on-Flug (Kennung `propwash_example:sculk_mission`) startet vom Boden auf 3 m,
  fliegt über die Sensoren und landet: Cinewhoop zwischen Sensoren, Freestyle auf der Kante eines Sensors, Racer über
  den Heimflug von Propwash zurück auf der Startfläche. Verlangt: Sensoren hören den Flug, Ende mit `LANDED`, danach
  keine Fallhöhe, 10 s (Ticks 10 bis 210 nach dem Ende) kein Sensor löst aus, Drohne entschärft, Motoren 0, kein Flug
  ohne Pilot.
