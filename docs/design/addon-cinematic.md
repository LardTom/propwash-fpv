# Entwurf: Kino-Drohne im Stil von DJI — wie das Add-on die API 1.6 benutzt

Status: Entwurf für das zweite offizielle Add-on nach Propwash 0.5.2 (Arbeitsname „Propwash: Cinema“, Namensraum
`cinema`). Beschreibt, wie es mit den Bausteinen aus [`addon-api-1.6.md`](addon-api-1.6.md) gebaut wird; das Add-on
selbst ist nicht Teil von Propwash. Nur öffentliche API, `PropwashApi.requireVersion("cinema", "1.6")`.

Idee: Kamera-Drohnen, die sich wie eine DJI Mini/Air/Mavic fliegen – GPS-Positionshaltung, Höhe halten, weiche
Kino-Modi, stabilisierte 3-Achsen-Gimbal-Kamera, intelligente Flugmodi (Umkreisen, Wegpunkte, Rückkehr), Hinderniserkennung
und ein HUD wie in der DJI-App.

## 1. Teile

| Teil | Art | Besonderheit |
|---|---|---|
| Faltrahmen „Mini“, „Air“, „Mavic“ | Rahmen (JSON) | eigene Arm-Geometrie, Halterung `front` für den Gimbal unter der Nase (`accessory_anchors.front`), Akku hinten |
| Kino-Flight-Controller mit GPS | Stack (JSON) | aktiviert die Flughilfe (`DroneProfile#has("cinema:fc_…")`) |
| 3-Achsen-Gimbal-Kamera | Zubehör vorne | Masse und Stirnfläche wie das Vorbild, `flight_battery_w`; keine Action-Cam-Daten (die erlaubt die API nur auf der Halterung oben, siehe §7) |
| Kino-Akkus | Akku (JSON) | Li-Ion-artige Zellchemie (`CellChemistry`) für lange Flugzeiten |

Die Faltrahmen fliegen mit normalen Propwash-Motoren und Props; die Physik bleibt Propwashs Flugmodell – die
Flughilfe ist „nur“ der Flight-Controller.

## 2. Flughilfe (`FlightAssist`)

Anmeldung: `PropwashApi.flight().registerAssist("cinema:fc", profile -> profile.has(KINO_FC) ? new CinemaAssist(profile) : null)`.
Läuft mit 100 Hz im Flugmodell des Piloten und gibt über `AssistOutput` Ziele an Propwashs Regelkreise (ALTHOLD/POSHOLD
mit realistischen GPS- und Barometerfehlern). Modi auf den Zubehör-Aktionen (die Funke bleibt Propwashs Funke, die
Schalter heißen im Modi-Tab „Zubehör-Aktion 1–4“):

| Modus | Umschalten | Ausgaben |
|---|---|---|
| **Normal** (Standard) | – | Knüppel → Geschwindigkeit (`velocity`, `maxSpeed` 10 m/s), Gas → Steigrate (`climb` ±4 m/s), Gieren → `yawRate` 90 °/s; Knüppel los = `holdPosition` (bremst und hält wie eine DJI) |
| **Kino** | Aktion 1 kurz | wie Normal, aber Knüppel geglättet (Zeitkonstante 0,8 s), `maxSpeed` 3 m/s, `yawRate` 30 °/s |
| **Sport** | Aktion 1 lang | `sticks` + `mode(ANGLE)` mit größerem Winkel – manuelles Fliegen ohne Positionshaltung |
| **Umkreisen** (Point of Interest) | Aktion 2 | Mittelpunkt 8 m vor der Nase (oder per Blick gesetzt), `velocity` tangential + radiale Korrektur, `holdAltitude`, `heading` zur Mitte; Rollen-Knüppel ändert Tempo, Nicken den Radius |
| **Wegpunkte** | Aktion 3 setzt einen Punkt, lang = abfliegen | Liste im Add-on, `holdPosition` auf den nächsten Punkt mit `maxSpeed`, `heading` in Flugrichtung, Höhe je Punkt |
| **Rückkehr (RTH)** | Aktion 4 | `requestReturnHome()` – Propwashs GPS Rescue fliegt mit Sicherheitshöhe, Umweg um Hindernisse und Landeplatz-Prüfung |

Ohne GPS-Fix (`positionValid()` falsch, OSD „KEIN GPS“) fällt die Flughilfe in den ATTI-Modus: geglättete Knüppel mit
`mode(ANGLE)` und `climb` – die Drohne driftet mit dem Wind wie das Vorbild. Das Label (`label("CINE")`, „SPORT“,
„POI“, „WAYPT“, „RTH“) ersetzt im OSD die Flugmodus-Anzeige.

### 2.1 Hinderniserkennung
`sensorRange()` = 15 m → `FlightState#obstacleDistance` in sechs Richtungen. Bremsen: geplante Geschwindigkeit in
Richtung eines Hindernisses auf `max(0, (d − 1,5 m) · 1,2)` begrenzen, unter 1,5 m stehen bleiben und `label("STOP")`.
Seitlich und oben gleich; nach unten hält eine Mindesthöhe von 0,5 m (Landung nur mit Gas ganz unten). Im
Beispiel-Add-on ist der Stopp vorn umgesetzt (`ExampleAccessories.CineAssist`).

### 2.2 Wind und Realismus
Propwashs Wind, Böen und Prop-Wash wirken weiter; die Regelkreise gleichen sie mit Verzögerung aus. Die Welt-Regeln
(Schwerkraft, Wind, Akku) gelten wie für jede Drohne.

## 3. Gimbal

`PropwashClientApi.registerGimbal("cinema:gimbal", provider)` mit `api.flight.GimbalModel`:
- Modus Folgen (Standard): Horizont immer gerade, Richtung folgt der Drohne weich (0,6 s); Sperren: Richtung fest
  (Zubehör-Aktion lang); FPV-Modus: Rollen folgt dem Rahmen.
- Nicken −90…+20° über eine eigene Taste des Add-ons (Mausrad bei gehaltener Taste, wie das Rad an der DJI-Funke) oder
  den Knüppel-Kanal im Sport-Modus; mechanische Grenzen relativ zum Rahmen (Nicken −135…+60°, Rollen ±45°, Gieren ±100°).
- Die Bildvibration entfällt (Gimbal), Zuschauer sehen dasselbe Bild.

## 4. HUD im Stil der DJI-App

`PropwashClientApi.registerHudElement("cinema:hud", element)` mit `replacesOsd(ctx) = true`, solange eine Kino-Drohne
geflogen wird:
- oben: Flugmodus und GPS (Satelliten), Akku in % und Restzeit, Funk- und Videosignal,
- unten links: H (Höhe über dem Home-Punkt), D (Abstand), H.S./V.S. (Tempo, Steigen) aus `FlightTelemetry`,
- unten rechts: Mini-Karte mit Home-Pfeil (`homeBearingDeg`), Gimbal-Nickwinkel,
- Ränder bei Hindernissen (rot unter 3 m) aus den Abständen der Flughilfe,
- Aufnahme-Punkt, wenn eine Action-Cam auf der Halterung oben aufnimmt (Propwashs Aufnahme läuft über die
  Action-Cam-Logik; die Gimbal-Kamera vorne selbst nimmt nicht auf, siehe §7).

## 5. Fernsteuerung

Die Kino-Drohne nutzt Propwashs Funke und Eingaben (Funke, Gamepad, Maus + Tastatur). Eine eigene DJI-Funke als Item
und eigene Schalterfunktionen sind ein offener Punkt der API (siehe [`addon-api-1.6.md`](addon-api-1.6.md) §15); bis dahin
reichen die vier Zubehör-Aktionen und eigene Tasten des Add-ons.

## 6. Tests im Add-on

JUnit mit dem reinen Flugmodell über `FlightAssist` (Umkreisen hält Radius und Höhe, Wegpunkte werden erreicht, Bremsen
vor Hindernissen), `GimbalModel` (Horizont gerade bei 30° Rollen), Selbsttest-Schritte nach dem Muster der Gruppe
`addon-api:` (Kino-Flughilfe, Umkreisen, Gimbal und HUD im FPV).

## 7. Grenzen der API 1.6 und offene Punkte

- **Action-Cam-Daten nur oben.** `AccessoryDefinition` lehnt `action_cam` auf jeder anderen Halterung als `top` ab
  (Fehler beim Anmelden der Teile). Die Gimbal-Kamera vorne ist deshalb eine reine FPV-Kamera; wer aufnehmen will,
  setzt zusätzlich eine Action-Cam auf die Halterung oben.
- **Aufnahmen sind nicht stabilisiert.** Der Gimbal (`registerGimbal`) dreht nur das FPV-Bild; die Action-Cam-Aufnahme
  nutzt ihn nicht und zeigt die Lage des Rahmens wie bei jeder Action-Cam. Stabilisierte Aufnahmen, das Kernstück eines
  DJI-artigen Add-ons, gehen mit der API 1.6 also nicht.
- Offen für eine spätere API-Nebenversion: Action-Cam-Daten auch vorne erlauben und die Lage des Gimbals an die
  Aufnahme weitergeben. Bis dahin bleibt der Entwurf bei der FPV-Gimbal-Kamera vorne plus Action-Cam oben.
