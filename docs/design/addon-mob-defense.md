# Entwurf: „Propwash: Mob Defense“ — wie das Add-on die API 1.6 benutzt

Status: Entwurf für das offizielle Add-on nach Propwash 0.5.2. Beschreibt, **wie** das Add-on mit den Bausteinen aus
[`addon-api-1.6.md`](addon-api-1.6.md) gebaut wird; das Add-on selbst ist nicht Teil von Propwash. Es benutzt nur die
öffentliche API (`de.lardtom.propwash.api`), Namensraum `mobdefense`, `PropwashApi.requireVersion("mobdefense", "1.6")`.

Idee: Zubehör im Vanilla-Stil, mit dem eine FPV-Drohne sich gegen Mobs wehrt – und Mobs, die auf Drohnen reagieren.

## 1. Zubehör

| Teil | Halterung | Inventar | Aktion | API |
|---|---|---|---|---|
| Werfer (Dispenser-Optik) | vorne (`AccessorySlot.FRONT`) | 4 Slots: Pfeile (auch Spektral-/Trankpfeile), Wurftränke, Windkugeln, TNT | Aktion 1 feuert den ersten belegten Slot, Aktion 2 wählt den nächsten Slot | `AccessoryBehavior` (`slots`, `accepts`, `onAction`), `MountPose#front()`/`forward()`, `DronePhysics#impulse` |
| Speer (Holz bis Netherit) | unten | – | – | Event `DRONE_HIT_ENTITY` |
| Feuerwerks-Booster | oben | 1 Slot: eine Feuerwerksrakete | Aktion 3 zündet | `DronePhysics#push(F, frameRelative = true, …, Brenndauer)` |
| Eimerhalter | unten | 1 Slot: Wasser- oder Lavaeimer | Aktion 4 gießt aus | `AccessoryBehavior#onAction`, Welt-Regel des Add-ons |

Alle Teile sind normale Bauteile (`AccessoryDefinition`, JSON im Add-on, Masse, Maße, Stirnfläche) – Werkbank, Analyse,
Flugmodell und Rendering bekommt das Add-on damit geschenkt. Die Beladung wiegt mit (`payloadGrams` mit realen Massen:
Pfeil 20 g, Trank 350 g, Windkugel 50 g, TNT 600 g – TNT macht eine 5″ träge, das ist gewollt).

### 1.1 Werfer
- `onAction(action)`: nur `number() == 1 && pressed()`; Slot über `inventory().firstFilled()` bzw. den mit Aktion 2
  gewählten; `inventory().take(slot, 1)`; Projektil an `mount().front()` + 0,15 m in `mount().forward()` mit
  Mündungsgeschwindigkeit plus Drohnentempo (`velocity()` / 20 pro Tick); Besitzer = `player()` (Schaden zählt als
  Spieler-Schaden, PvP-Regeln greifen).
- Rückstoß realistisch aus dem Impuls des Geschosses: `physics().impulse(forward × −m·v, Rahmenpunkt der Mündung)`
  – Pfeil ≈ 0,02 kg × 50 m/s = 1 N·s, Windkugel zusätzlich die Druckwelle (2–3 N·s, kippt eine Whoop um), TNT wird
  nur fallen gelassen (kein Rückstoß, nur weniger Masse).
- Sperrzeit je Slot im Add-on (Dispenser-Takt 4 Ticks); die Ratenbegrenzung von Propwash (20 Aktionen/s) schützt
  den Server zusätzlich.
- HUD: Munition je Slot und gewählter Slot als `HudElement` unten rechts im Bild (`PropwashApi.accessories()
  .contents(drone, FRONT)` ist auf dem Client synchronisiert).

### 1.2 Speer
- Listener auf `PropwashEvents.DRONE_HIT_ENTITY`: wenn `hit.slot() == BOTTOM` und `hit.accessory()` ein Speer ist,
  `hit.addDamage(k · closingSpeed)` mit `k` je Material (Holz 0,3 … Netherit 0,8) und `setKnockback` nach unten
  gerichtet. Treffer von vorn oder oben bekommen keinen Bonus (die Richtung kommt aus `framePoint`). Der Speer nutzt
  sich über die Schadens-Logik des Add-ons ab.
- Der Treffer selbst bleibt Propwashs Kollision (Abprall, Prop-Strikes, Spielregel `propwash:drone_hits_entities`).

### 1.3 Feuerwerks-Booster
- Aktion 3 nimmt die Rakete aus dem Slot und ruft `physics().push(new Vec3(0, 0, −F), true, Vec3.ZERO, t)` mit
  `F` ≈ 12 N und `t` nach Flugdauer der Rakete (1–3 s); `frameRelative` schiebt entlang der Nase, auch im Looping.
- Optisch: Funken am Heck über eigene Partikel des Add-ons (Client, `PropwashClientApi.input().drone()`).

### 1.4 Eimer
- Aktion 4 setzt Wasser oder Lava an `mount().at(0, −0,2, 0)` (Server, mit den Bau-Rechten des Piloten), der Eimer
  wird leer. Nur, wenn die Welt-Regel `mobdefense:allow_buckets` es erlaubt.

## 2. Welt-Regeln des Add-ons

Eigene Gamerules im Namensraum `mobdefense` (Vanilla-Gamerules; Propwashs Tab „Welt-Regeln“ zeigt nur Propwash-Regeln,
das Add-on bekommt einen eigenen Konfigurator-Tab über `registerConfigTab` im Propwash-Theme):
- `mobdefense:mobs_react_to_drones` (an) – Mob-Verhalten aus Abschnitt 3,
- `mobdefense:allow_buckets` (aus), `mobdefense:allow_tnt` (an), `mobdefense:projectile_damage_scale` (100 %).

## 3. Mobs reagieren auf Drohnen

Drohnen sind keine `LivingEntity`; das Add-on hängt deshalb eigene KI-Ziele an (Mixin oder Loader-Event beim Spawnen):

| Mob | Ziel im Add-on | API |
|---|---|---|
| Zombie, Husk, Drowned | laufen zur nächsten fliegenden Drohne in 16 Blöcken, schlagen zu, wenn sie tief fliegt | `drones().nearestDrone(mob, 16, true, true)` |
| Skelett, Stray, Bogged | zielen mit Vorhalt und schießen | `drones().aim(eye, drone, 1,6, 0,05)` → Pfeil mit `shoot(aim…)` |
| Blaze, Ghast | Feuerbälle mit Vorhalt | `aim(…, speed, 0)` |
| Phantom | verfolgt die Drohne statt des Spielers, wenn sie höher als 10 Blöcke fliegt | `dronesNear(...)`, `view.velocity()` |
| Creeper | zündet neben tief fliegenden Drohnen | `view.center()` |

Treffer auf die Drohne laufen über `DRONE_DAMAGED`: Das Add-on kann den Schaden je Quelle skalieren (z. B. Pfeile
zerbrechen eine Whoop sofort, eine X-Class hält drei aus) oder Panzerung als Zubehör anbieten (`setAmount`).

## 4. Netz, Server, Mehrspieler

- Alles Weltverändernde läuft am Server (Aktionen kommen geprüft an, Pilot und Epoche stimmen).
- Rückstoß und Booster gehen über `DronePhysics` an den Client des Piloten; eine Vorwegnahme auf dem Client ist
  möglich (`physics(drone)` beim Piloten), Mob Defense verzichtet darauf, weil die Laufzeit (≈ 100 ms) zum
  Dispenser-Takt passt.
- Das Add-on muss auf Server und Client installiert sein (Teile im Handshake).

## 5. Tests im Add-on

JUnit für Massen, Rückstoß-Impulse und Speer-Schaden (reine Rechnung), Selbsttest-Schritte nach dem Muster
`addon-api:` (Werfer feuert, Rückstoß kommt an, Speer-Treffer, Zombie läuft zur Drohne, Skelett trifft eine
schwebende Drohne mit Vorhalt).
