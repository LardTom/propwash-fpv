# Add-on-API 1.6 — Zubehör, Aktionen, Physik, Flughilfen, Gimbal und HUD (Propwash 0.5.2)

Status: verbindlicher Vertrag der API-Nebenversion 1.6.0 (Propwash 0.5.2, Netzprotokoll 16). Ergänzt
[`parts-api.md`](parts-api.md) (Bauteil-Modell, Versionierung) und das Handbuch [`../api/addon-guide.md`](../api/addon-guide.md)
Abschnitt 20. Im Code stehen keine Kommentare; alle Erklärungen stehen hier.

Anlass: Nach 0.5.2 folgen zwei offizielle Add-ons – „Propwash: Mob Defense“ (Zubehör gegen Mobs, Entwurf
[`addon-mob-defense.md`](addon-mob-defense.md)) und eine Kino-Drohne im Stil von DJI (Entwurf
[`addon-cinematic.md`](addon-cinematic.md)). 1.6 liefert die Bausteine, die beide brauchen, ohne die Add-ons selbst zu
bauen. Alles ist optional: Ohne Add-on, das die neuen Punkte benutzt, verhält sich Propwash genau wie 0.5.1 (Flugmodell
bitgleich, JUnit `AddonFlightHooksTest#unusedHooksKeepTheFlightBitIdentical` und die Bewegungs-Goldens).

## 0. Überblick

| Baustein | API | läuft | Netz |
|---|---|---|---|
| Halterung vorne | `AccessorySlot.FRONT` (`front`) | beide | im Bauplan (Protokoll 16) |
| Zubehör-Verhalten | `PropwashApi.accessories()` → `AccessoryRegistry`, `AccessoryBehavior` | Server (Logik), beide (Inventar, Gewicht) | Entity-Daten + Item-Komponente |
| Zubehör-Inventar | `AccessoryInventory`, Fenster „Zubehör“ | Server schreibt, Clients lesen | synchronisiert an alle, die die Drohne sehen |
| Zubehör-Aktionen 1–4 | Schalterfunktionen, Tasten, `AccessoryBehavior#onAction`, Event `ACCESSORY_ACTION` | Client erkennt, Server führt aus | C2S `propwash:accessory_action` |
| Stöße und Schübe | `DronePhysics#impulse`, `#push` | Server bestimmt, das Flugmodell des Fliegenden wendet an | S2C `propwash:drone_push` |
| Kräfte im Flug | `FlightExtensions#registerForces`, `ForceProvider` | im Flugmodell auf beiden Seiten (Pilot-Client, Heimflug am Server) | – |
| Flughilfen | `FlightExtensions#registerAssist`, `FlightAssist` | im Flugmodell des Pilot-Clients | – |
| Treffer und Schaden | `PropwashEvents.DRONE_HIT_ENTITY`, `DRONE_DAMAGED` | Server | – |
| Drohnen-Abfragen | `PropwashApi.drones()` → `DroneAccess`, `Intercept` | beide | – |
| Gimbal-Kamera | `PropwashClientApi.registerGimbal`, `GimbalModel` | Client | – |
| HUD-Elemente | `PropwashClientApi.registerHudElement`, `HudElement` | Client | – |
| Eingaben lesen | `PropwashClientApi.input()` → `ClientInput` | Client | – |

Reines Java (ohne Minecraft-Typen, für spätere Ports): das ganze Paket `api.flight` (`FlightAssist`, `FlightState`,
`AssistOutput`, `ForceProvider`, `ForceSink`, `DroneProfile`, `FlightExtensions`, `GimbalModel`, `Intercept`,
`SensorDirection`, `StabilizationMode`) und intern `sim.AssistBridge`, `sim.ExternalLoads`.

## 1. Koordinaten, Einheiten, Vorzeichen

- **Welt**: Minecraft-Koordinaten, 1 Block = 1 m, Geschwindigkeiten in m/s (nicht Blöcke pro Tick), Kräfte in N,
  Stöße in N·s, Drehmomente in N·m.
- **Rahmen-Koordinaten** wie in den Bauteil-Daten: Ursprung Rahmenmitte auf der Stack-Auflage, **+X rechts, +Y oben,
  −Z vorn**; in den Bauteil-Daten in mm, in Physik und Events in m (`framePoint`).
- **Kompass**: 0° = Norden (−Z), 90° = Osten (+X), im Uhrzeigersinn steigend – wie die OSD-Anzeige.
- **Lage**: `pitchDeg` positiv = Nase hoch, `rollDeg` positiv = rechte Seite unten, `yawRateDps` positiv = im
  Uhrzeigersinn von oben. (Die OSD-Telemetrie intern rechnet Nicken mit umgekehrtem Vorzeichen; die API nicht.)
- **Knüppel**: Rollen, Nicken, Gieren −1…1, Gas 0…1 – wie der Pilot sie sieht, vor Rates und Expo.

## 2. Halterung vorne (`AccessorySlot.FRONT`)

- Neue Konstante am Ende von `AccessorySlot` (Schlüssel `front`) und `build.AccessoryMount.FRONT` (ohne Sockel).
  Zubehör vorne hängt unter dem Anbaupunkt und ragt nach vorn: Quader `x ∈ [−b/2, b/2]`, `y ∈ [−h, 0]`, `z ∈ [−t, 0]`
  (Rahmen-Koordinaten vor der Neigung). Das Flugmodell zählt Masse, Schwerpunkt, Trägheit und Stirnfläche wie bei den
  anderen Halterungen (`ParamDerivation`).
- Anbaupunkte der eingebauten Rahmen (mm, Neigung 0°): Cinewhoop (0; −3; −50), Freestyle (0; 0; −72), Race (0; 0; −66),
  Long Range (0; 0; −76). Der Whoop hat keine Halterung vorne. Tabelle in den Rahmen-JSONs (`accessory_anchors.front`),
  `drone_layout.json` und `tools/assets/parts/layout.py`; Rückfall für Rahmen ohne Tabelle `(0; 0; −0,3 × Radstand)`.
- Rahmen von Add-ons bekommen die Halterung mit `"accessory_mounts": [..., "front"]` bzw. Builder
  `accessoryMount(AccessorySlot.FRONT)` und optional `anchor(AccessorySlot.FRONT, new AnchorSpec(...))`.
- Werkbank: Slot 14 links neben „Zubehör oben“ (30; 64) mit Symbol `propwash:gui/slot/accessory_front`
  (`assets-src/world/pixel/slots.txt`), Texte `propwash.content.workbench.slot.accessory_front`,
  `…slot_hint.accessory_front`, `propwash.content.mount.accessory.front`. `DroneWorkbenchMenu.PART_SLOTS` ist damit 15.
- Ohne Zubehör vorne ändert sich nichts am Flugmodell; die Bauteil-Definitionen der eingebauten Rahmen bekommen die
  Halterung als neutrale Ergänzung (`PartJsonTest#builtinDefinitionsAreIdenticalTo032`).

## 3. Zubehör-Verhalten und Inventar

```java
PropwashApi.accessories().register(Identifier.fromNamespaceAndPath("mobdefense", "dispenser"), new AccessoryBehavior() {
    public int slots() { return 4; }
    public boolean accepts(int slot, ItemStack stack) { return stack.is(Items.ARROW) || stack.is(Items.WIND_CHARGE); }
    public void onAction(AccessoryAction action) { ... }
});
```

- Je Zubehör-Id höchstens ein Verhalten; der Namensraum `propwash` bleibt den eingebauten Teilen. Das Zubehör selbst
  ist ein normales Bauteil (`AccessoryDefinition`, Java oder JSON); das Verhalten hängt am Code, nicht am JSON.
- `slots()` 0–9 (`AccessoryBehavior.MAX_SLOTS`), `accepts(slot, stack)` und `maxStackSize(slot, stack)` filtern das
  Fenster „Zubehör“. Vorher lehnt Propwash selbst ab, was Vanilla nicht in Behälter-Items lässt
  (`Item#canFitInsideContainerItems`, z. B. Shulkerkisten) und Drohnen-Items mit eigenem Zubehör-Inhalt – sonst ließen
  sich Drohnen beliebig tief ineinander verschachteln. `payloadGrams(contents)` darf ein eigenes Gewicht liefern; leer heißt: Propwash rechnet mit den
  Item-Gewichten des Greifers (`interact.ItemWeights`, Behälter samt Inhalt), höchstens 2 kg je Halterung.
- **Speicher**: `AccessoryContents` (je Halterung bis zu 9 Stapel) liegt synchronisiert an der Drohne
  (Entity-Daten, gespeichert unter `AccessoryContents`) und wandert beim Aufheben als Komponente
  `propwash:accessory_contents` ins Drohnen-Item und beim Platzieren zurück. Zerlegt die Werkbank eine Drohne, gehen
  die Inhalte ins Inventar des Spielers (volle Taschen: vor die Füße). Verschwindet eine Drohne ohne Item (Kreativ-Abbau,
  `/kill`, Spielregel ohne Drops), ist auch der Inhalt weg – wie die Drohne selbst.
- **Gewicht im Flug**: Die Beladung macht das Zubehör im Flugmodell schwerer (in 5-g-Schritten, wie der Greifer),
  auf dem Pilot-Client und im Heimflug am Server; Werkbank und Analyse zeigen die leere Drohne. Treffer rechnen mit der
  beladenen Masse.
- **Fenster „Zubehör“**: Knopf im Kopf der Wartung (Schleichen + Rechtsklick auf die Drohne), nur wenn ein Zubehör
  ein Inventar hat. Je Halterung eine Zeile mit Name und Beladung in Gramm, darunter das Spieler-Inventar, im
  Propwash-Theme. Mehrere Spieler und der Pilot dürfen gleichzeitig zugreifen; Änderungen am Server (z. B. Schüsse)
  erscheinen sofort im offenen Fenster. Das Fenster liest vor jedem Zugriff den Stand der Drohne neu und schreibt nur
  die Plätze zurück, die es selbst geändert hat (`accessory.AccessoryContainer`); zwei Klicks im selben Tick oder ein
  Schuss zwischen zwei Klicks verdoppeln oder verlieren deshalb nichts (Selbsttest
  „addon-api: zwei Fenster und ein Schuss teilen sich das Zubehör-Inventar“).
- **Lesen auf beiden Seiten**: `PropwashApi.accessories().contents(entity, slot)` (Kopien),
  `contents(droneItem, slot)`, `accessoryAt(entity, slot)`; **schreiben nur am Server** über
  `inventory(entity, slot)` → `AccessoryInventory` (`get` liefert Kopien, `set`/`take` schreiben sofort und
  synchronisieren).
- `tick(context)` läuft am Server in jedem Tick jeder geladenen Drohne mit diesem Zubehör.

`AccessoryContext` (am Server): `level()`, `drone()`, `accessory()`, `slot()`, `inventory()`, `pilot()`, `flying()`
(Pilot oder Heimflug), `armed()`, `actionHeld(n)`, `mount()`, `velocity()` (m/s, aus der Bewegung zwischen den Ticks),
`physics()`. `MountPose` nennt in Weltkoordinaten den Anbaupunkt, die Mitte und die Vorderseite des Zubehörs
(`front()`, Mündung eines Werfers) sowie `forward()`, `up()`, `right()` der Halterung samt Neigung; `at(r, u, f)`
verschiebt vom Anbaupunkt.

## 4. Zubehör-Aktionen 1–4

- Vier neue Schalterfunktionen `SwitchFunction.ACCESSORY_1…4` (vor den eigenen Befehlen; Tab „Modi“, Seite „Flug“;
  der Empfänger-Assistent fragt sie nicht ab, sie sind Sache der Add-ons) als Taster und vier Tasten „Zubehör-Aktion 1–4“ (Standard Ziffernblock 9, 0, Enter und +; Ziffernblock 8 ist
  der Turtle-Modus). Gedrückt = Schalter an oder Taste unten, ohne offenes Fenster.
- Jede Flanke (drücken, loslassen) geht als `propwash:accessory_action` (Drohnen-Id, Reset-Epoche, Nummer, gedrückt) an
  den Server. Der prüft: der Absender fliegt diese Drohne, die Epoche stimmt, beim Drücken höchstens 8 auf einmal und
  dann 20 je Sekunde (`accessory.ActionGate`); ein Loslassen zählt nur, wenn die Aktion gerade gehalten ist (also ihr
  Drücken angenommen wurde), sonst ließe sich die Grenze mit Loslassen-Paketen umgehen. Danach bekommen alle Verhaltens-Zubehörteile der Drohne `onAction` mit
  `number()`, `pressed()` und `player()`, anschließend feuert `PropwashEvents.ACCESSORY_ACTION` – auch ohne
  Verhalten (z. B. für Add-ons, die eigene Server-Logik haben). Den gehaltenen Zustand je Drohne liefert
  `actionHeld(n)`; er erlischt beim Pilotenwechsel.
- Im Flugmodell sieht eine Flughilfe die gehaltenen Aktionen als `FlightState#action(n)` (Client, ohne Umweg über den
  Server), im Client liest `PropwashClientApi.input().action(n)` sie.

## 5. Stöße, Schübe und Kräfte

- `PropwashApi.drones().physics(drone)` → `DronePhysics`:
  - `impulse(J, framePoint)` – Stoß in N·s in Weltrichtung an einem Rahmenpunkt (m); ändert Geschwindigkeit und Drall
    sofort. Rückstoß eines Werfers: `impulse(forward.scale(-p), punktDerMündung)`.
  - `push(F, frameRelative, framePoint, seconds)` – Kraft in N über eine Dauer, in Welt- oder Rahmenrichtung (eine
    Feuerwerksrakete schiebt mit `frameRelative = true` immer nach vorn, auch wenn die Drohne sich dreht).
  - Grenzen: 50 N·s, 500 N, 30 s, Hebelarm 2 m; ungültige Werte werden verworfen, zu große gekappt; höchstens 16
    laufende Schübe je Flugmodell.
- **Wohin**: Fliegt ein Spieler die Drohne, geht der Stoß als `propwash:drone_push` an seinen Client und wirkt dort im
  Flugmodell (mit der Laufzeit des Netzes, typisch 50–150 ms); im Heimflug ohne Pilot direkt im Server-Flugmodell; eine
  abgestellte Drohne bekommt den Stoß als Geschwindigkeit (Kraft × Dauer). Auf dem Client des Piloten darf ein Add-on
  `physics(drone)` auch direkt rufen, um einen eigenen Rückstoß vorwegzunehmen.
- **Kräfte im Flug**: `PropwashApi.flight().registerForces(id, profile -> provider oder null)`. `ForceProvider#apply`
  läuft im Nav-Takt (100 Hz) im Flugmodell auf dem Pilot-Client und im Heimflug am Server mit demselben `FlightState`;
  die Summe aus `addForce`, `addForceAt` und `addTorque` wirkt bis zum nächsten Aufruf. Beispiele: Auftrieb eines
  Ballons, Luftwiderstand eines Fallschirms, ein Eimer, der beim Ausgießen leichter wird. Wirft ein Anbieter eine
  Ausnahme, schaltet das Flugmodell alle Kräfte ab und meldet den Fehler im Log.
- Bitgleich: Ohne Stöße, Schübe und Anbieter rechnet das Flugmodell genau wie vorher (die Zusätze laufen nur bei
  aktiven Lasten).

## 6. Events

| Event | Wann | Was |
|---|---|---|
| `DRONE_HIT_ENTITY(EntityHit)` | Server, nach jeder gültigen Kollision einer geflogenen Drohne mit einem Lebewesen, die die Spielregeln erlauben (`propwash:drone_hits_entities`, PvP, Sperrzeit je Ziel) – auch unter der Mindestgeschwindigkeit, dann mit `baseDamage` 0 | `relativeVelocity` (m/s), `closingSpeed`, `normal`, `framePoint` (Kontaktpunkt in Rahmen-Koordinaten), `slot`/`accessory` (Halterung in Richtung des Kontakts, Kosinus ≥ 0,5 zur Mitte des Zubehörs), `baseDamage`; `setDamage`, `addDamage` (0–40), `setKnockback` (0–3). Schaden 0 = kein Treffer |
| `DRONE_DAMAGED(DroneDamage)` | Server, wenn die Drohne Schaden nimmt (`hurtServer`: Mobs, Pfeile, Explosionen, Spieler) | `source`, `amount`; `setAmount`, `cancel`. Spieler zerlegen eine Drohne weiterhin mit einem Schlag, wenn nicht abgebrochen; sonst zerbricht sie bei 6 Schadenspunkten |
| `ACCESSORY_ACTION(player, drone, number, pressed)` | Server, nach jeder geprüften Zubehör-Aktion | – |

Listener laufen auf dem Server-Thread; Ausnahmen werden geloggt und brechen den Ablauf nicht ab.

## 7. Drohnen-Abfragen und Hindernisse

`PropwashApi.drones()` → `DroneAccess`:
- `isDrone(entity)`, `view(entity)` → `DroneView` (Pilot, fliegt/Heimflug, scharf, Schwerpunkt, Geschwindigkeit in m/s,
  Vorne/Oben, Masse samt Beladung, Zubehör je Halterung).
- `dronesNear(level, center, radius, flyingOnly)` (bis 128 Blöcke, nach Abstand sortiert),
  `nearestDrone(mob, range, requireSight, flyingOnly)` (Sicht über die Mob-Sensorik).
- `obstacleDistance(level, origin, direction, range, entities, ignore)`, `scan(drone, range)` → `ObstacleScan` mit sechs
  Abständen in Rahmenrichtungen (`SensorDirection`: vorn, hinten, links, rechts, oben, unten; bis 64 m).
- `aim(shooter, drone, speedPerTick, gravityPerTick)` → `Intercept.Aim` (Richtung und Flugzeit in Ticks) mit Vorhalt
  und Ausgleich der Schwerkraft; `Intercept.aim(…)` gibt es als reine Rechnung.

Drohnen sind keine `LivingEntity`: Vanilla-Ziele (`setTarget`) gehen nicht. Mobs reagieren über eigene Ziele des
Add-ons, die `nearestDrone` und `aim` benutzen (Entwurf Mob Defense §3). Pfeile, Feuerbälle und Windkugeln treffen
Drohnen wie andere Entities und laufen dabei durch `DRONE_DAMAGED`.

## 8. Flughilfen

```java
PropwashApi.flight().registerAssist("cinema:assist", profile -> profile.has("cinema:flight_controller") ? new CinemaAssist() : null);
```

- Je Drohne gilt höchstens eine Flughilfe: die erste Fabrik in Anmeldereihenfolge, die nicht `null` liefert.
  `DroneProfile` nennt die Teile als Ids (`frame`, `stack`, `motor`, `prop`, `video`, `battery`, `accessories` je
  Halterung) und die Masse.
- `update(FlightState, AssistOutput)` läuft im Nav-Takt (100 Hz, alle 20 Teilschritte) **im Flugmodell des
  Pilot-Clients**, solange ein Spieler fliegt – nicht während GPS Rescue und nicht im Heimflug am Server (dort fliegt
  Propwashs Autopilot). `reset()` beim Anhängen; eine Ausnahme schaltet die Flughilfe für diesen Flug ab (Hinweis im Spiel,
  Eintrag im Log).
- `FlightState` liefert Wahrheit (Lage, Position, Geschwindigkeit) und – wie ein echter Flight-Controller – die
  Schätzungen aus GPS und Barometer (`estimatedX/Y/Z`, `estimatedVx/Vy/Vz`, `positionValid`, `satellites`), dazu Höhe
  über Grund, Akku, Pilotknüppel, Pilotmodus, Zubehör-Aktionen und Hindernisabstände.
- `AssistOutput` (jeder Takt beginnt leer = der Pilot fliegt):
  - `sticks(r, p, y, gas)` und `mode(ACRO|ANGLE|HORIZON)` ersetzen die Pilotknüppel (Kino-Glättung, Begrenzungen).
  - `holdAltitude(y)` oder `climb(m/s)` (±8 m/s) – Höhe über den Regelkreis von ALTHOLD.
  - `holdPosition(x, z)` oder `velocity(vx, vz)` mit `maxSpeed` (0–20 m/s, Standard 4) – Position über den Regelkreis
    von POSHOLD mit denselben Sensorfehlern; ohne GPS-Fix fällt das auf Höhe halten zurück (OSD „KEIN GPS“).
  - `yawRate(°/s)` oder `heading(Kompass)` – Gieren (P-Regler, bis 180 °/s, über die Rates der Drohne).
  - `label(text)` – bis 8 Zeichen, ersetzt im OSD die Flugmodus-Anzeige (z. B. „CINE“, „ORBIT“).
  - `requestReturnHome()` – startet GPS Rescue wie der Schalter.
- `sensorRange()` > 0 (bis 64 m) lässt den Client in jedem Bild sechs Strahlen aus der Drohnenmitte werfen; die
  Abstände stehen in `obstacleDistance(richtung)` (`+∞` = frei, `NaN` = nicht gemessen).

Eine Flughilfe, die gerade etwas ausgibt (Knüppel, Modus, Höhe/Position/Tempo, Gieren), macht die Runde zur Runde
mit Flughilfe: Grund „Sonstiges“ mit der Id der Flughilfe („die Flughilfe cinema:assist aus einem Add-on hat
mitgeflogen“, `AssistCheck.withAddon`, `DroneSim#assistEngaged`, 0,25 s Nachlauf). Hält sie Höhe oder Position,
während der Pilot selbst kein Halten geschaltet hat, wird das nicht mehr als eingebautes „Höhe halten war an“
gemeldet, sondern nur als Add-on-Flughilfe. Kräfte (`registerForces`) zählen nicht: sie sind Physik des Zubehörs.

Wiederverwendet: die Schätzer und Regelkreise von ALTHOLD/POSHOLD (`sim.NavHold`), der Heimflug von GPS Rescue
(`requestReturnHome`) und die Rates der Drohne. Orbit, Wegpunkte und Kino-Modus sind damit wenige Zeilen im Add-on
(Beispiel: `dev.example.ExampleAccessories.CineAssist`).

## 9. Gimbal-Kamera

- `PropwashClientApi.registerGimbal(id, provider)`; `GimbalProvider#appliesTo(drone)` wählt (erster Treffer),
  `orient(context, rotation)` setzt die Weltlage der FPV-Kamera. `GimbalContext` liefert die Lage des Rahmens zum
  angezeigten Bild (Posen-Puffer mit Videolatenz), den Kamerawinkel und die Zeit seit dem letzten Bild.
- Gilt für den Piloten im FPV und für Zuschauer. Die Kamera bleibt an der Objektivposition der Drohne; die
  Bildvibration entfällt, solange `suppressesShake()` wahr ist (Standard).
- `api.flight.GimbalModel` ist ein fertiger 3-Achsen-Gimbal ohne Minecraft-Typen: Modi `FOLLOW` (Horizont gerade,
  Richtung folgt weich), `LOCK` (Richtung fest), `FPV` (Rollen folgt dem Rahmen), Nickbereich, mechanische Grenzen
  relativ zum Rahmen, Nachführzeit und Raten.

## 10. HUD-Elemente und Telemetrie

- `PropwashClientApi.registerHudElement(id, element)`; `HudElement#active(context)` (Standard: FPV oder Sichtflug),
  `render(graphics, context)`, `replacesOsd(context)` blendet das Propwash-OSD aus (für ein komplettes HUD im Stil
  von DJI).
- `HudContext`: Drohne, FPV/Sichtflug/Zuschauen, Bildbereich in GUI-Pixeln (4:3-Bild mit Rändern beachtet),
  `FlightTelemetry` (scharf, Modus und Flughilfe, Position, Tempo, Steigrate, Kompass, Nicken, Rollen, Gas, Akku,
  Home-Punkt mit Abstand, Höhe und Richtung, GPS, Funk- und Videosignal, Flugzeit, Knüppel) und die Schrift.
- Elemente zeichnen nach dem OSD in einer eigenen Ebene (`propwash:addon_hud`), nie über Propwash-Fenstern.

## 11. Fernsteuerung und Eingaben

`PropwashClientApi.input()` → `ClientInput`: fliegt der Spieler, welche Drohne, Knüppel und Zubehör-Aktionen. Eigene
Funken-Modelle oder zusätzliche Schalterfunktionen je Add-on gibt es in 1.6 nicht (offener Punkt): die vier
Zubehör-Aktionen decken Feuern, Moduswechsel und Kamera ab, weitere Tasten meldet ein Add-on selbst bei Minecraft an.

## 12. Netz und Speicher

- **Netzprotokoll 16** (0.5.1: 15): neue Payloads `propwash:accessory_action` (C2S) und `propwash:drone_push` (S2C), die
  Halterung `front` im Bauplan, der Entity-Daten-Serializer `propwash:accessory_contents` und die Komponente
  `propwash:accessory_contents`. Clients und Server 0.5.1 und 0.5.2 trennt der Handshake mit klarer Meldung.
- Welten, Drohnen und Items aus 0.5.x laden unverändert (ohne Inhalt, ohne Zubehör vorne). Add-ons für API 1.0–1.5
  laufen unverändert; *Just More Parts* braucht keine Änderung.

## 13. Sicherheit und Autorität

- Alles, was die Welt ändert (Projektile, Inventar, Schaden), läuft am Server. Der Client meldet nur Flanken; der Server
  prüft Pilot, Epoche und Rate. Stöße kommen nur vom Server (oder vom Add-on-Code des Piloten selbst).
- Flughilfen und Kräfte laufen wie das ganze Flugmodell beim Piloten; die Plausibilitätsprüfung des Servers für
  Position und Tempo gilt unverändert.

## 14. Tests

- JUnit: `sim.AddonFlightHooksTest` (bitgleich ohne Erweiterung, Stoß, Schub, Kräfte im Nav-Takt, Positionsziel,
  Umkreisen mit Kurs, Gierrichtung, Knüppel-Glättung und Aktionen, Ausfall, Hindernisse und Heimflug-Wunsch),
  `api.flight.GimbalModelTest`, `api.flight.InterceptTest`, `accessory.ActionGateTest`, `accessory.MountPosesTest`,
  `accessory.FlightHooksTest`, angepasst `PartJsonTest`, `BuildSlotsTest`, `ApiContractTest`.
- Selbsttest-Gruppe `addon-api:` mit dem Beispiel-Add-on (`dev.example.ExampleAccessories`, Client
  `ExampleAccessoriesClient`): Version und Halterung, Inventar speichern und synchronisieren, Wartung → Fenster
  „Zubehör“, Treffer unten mit Speer und Schaden-Event, Abfragen und Hindernis-Scan, Werfer feuert mit Rückstoß im
  Flug, Kino-Flughilfe mit Umkreisen, Gimbal und HUD im FPV; Bildschirmfotos `addon_api_*`.

## 15. Offene Punkte

- Eigene Funken (Item, Modell, Belegung) für Add-ons; heute nutzt die Kino-Drohne die Propwash-Funke.
- Platzierbare Add-on-Elemente im OSD-Editor (heute zeichnet ein HUD-Element selbst, mit eigener Einstellung im Add-on).
- Hindernis-Scan im Server-Heimflug (dort plant Propwashs Autopilot selbst um Hindernisse).
- Ein eigenes 3D-Modell für Zubehör vorne der eingebauten Teile gibt es nicht (es gibt kein eingebautes Zubehör vorne).
- Action-Cam-Daten gibt es nur auf der Halterung oben, und der Gimbal dreht nur das FPV-Bild, nicht die Aufnahme.
  Stabilisierte Aufnahmen einer Gimbal-Kamera vorne (Kino-Add-on, `addon-cinematic.md` §7) brauchen eine spätere
  API-Nebenversion.
- Zubehör-Tasten werden einmal je Tick gelesen; ein Antippen unter 50 ms kann verloren gehen.
