# Zuschauen mit OSD und Rennen des Piloten

Für Spieler und Server-Betreiber. Wer mit der Brille per `F7` (oder über die Kanalliste im Tab **Videokanal**) einer
Drohne zuschaut, sieht das OSD des Piloten so, wie der Pilot es eingerichtet hat, mit seinen Live-Werten, und im Rennen
das aktive Gate, den Countdown, die Runde und die Zeit des Piloten. Voraussetzung wie bisher: **OSD des Piloten zeigen**
ist an (Tab **Videokanal**, Standard an).

## 1. Was der Zuschauer sieht

- **OSD-Layout des Piloten:** Vorlage, eingeschaltete Elemente, ihre Plätze, Farben und Größen, Deckkraft, Rahmen und
  Hintergrund, Warnfarbe, Pitch-Leiter, dazu die Einheit (km/h oder mph) und der Stick-Mode des Piloten (welcher Stick
  Gas und Gieren trägt). Die eigenen Einstellungen bleiben unverändert; nach dem Zuschauen zeigt das OSD wieder das
  eigene Layout. Eigene Wahl bleiben „Vanilla-HUD im Flug ausblenden“ und die Bildstörung im OSD.
- **Live-Werte des Piloten:** Sticks (jeden Tick), Gas, Flugmodus, 3D und Turtle, Halten von Höhe oder Position,
  scharf oder nicht, Akku (Spannung, Zelle, Ladung, mAh, Strom, Leistung, Warnstufen), Flugzeit und Zeit seit dem
  Scharfschalten, Warnungen (Failsafe, Startsperren, Weltrand, im Block, Überlast, Motor heiß, ESC drosselt), Funk
  (Link, RSSI), Repeater (`RPT` mit der Güte je Abschnitt), Heimatpunkt, Figuren und Punkte, Label der Flughilfe und
  Phase der Rettung. Die Zuschauerzahl kommt wie bisher vom Server.
- **Rennen des Piloten:** Das nächste Gate leuchtet grün und zeigt seine Durchflugfläche, vor dem Start das Start-Gate,
  im Countdown rot (bernsteinfarben nach einem Frühstart). Countdown-Zahl und Startampel, „LOS!“, die Meldung jeder
  fertigen Runde und „ZIEL“ erscheinen wie beim Piloten im OSD-Stil. Runde, Rundenzeit, letzte und beste Runde, Gate
  und Split stehen im OSD, soweit das Layout des Piloten sie zeigt. Zwischen zwei Paketen laufen Rundenzeit, Countdown
  und Flugzeit weiter, die Anzeige springt also nicht im 20-Hz-Takt.
- **Eigenes Bild bleibt eigen:** Videosignal, Rauschen und Bildstörung, Flugbahn-Marker und Geschwindigkeitspfeil
  folgen dem eigenen Empfang und den Bildern, die der Zuschauer sieht.
- Ideallinie und die übrigen Wertungsflächen bleiben beim Zuschauen aus, ebenso die Flächen beim Bauen und in der Nähe
  des eigenen Körpers.

## 2. Wann übertragen wird

- Der Client des Piloten schickt nur, solange mindestens ein anderer Spieler zuschaut (Zuschauerzahl über 0): die
  Live-Werte jeden Client-Tick (20 je Sekunde, etwa 200 bis 250 Byte je Paket), das OSD-Layout beim Beginn und nach
  jeder Änderung (geprüft alle 10 Ticks). Ohne Zuschauer geht nichts über das Netz.
- Der Server leitet nur an Spieler weiter, die per `F7` genau diese Drohne schauen. Wer neu zuschaut, bekommt das
  zuletzt geschickte Layout sofort beim Start.
- Prüfungen am Server: Nur der Pilot der Drohne darf schicken (Entity-Nummer der eigenen Drohne), höchstens 30
  Live-Pakete je Sekunde mit einem Vorrat von 10 und höchstens 2 Layouts je Sekunde mit einem Vorrat von 4; mehr wird
  verworfen. Ein Layout hat höchstens 8192 Zeichen (wie im Sharecode).
- Fehlen die Live-Werte länger als 1 s (Lag, Pilot mit älterem Propwash), zeigt der Zuschauer wie früher die Werte aus
  der Drohne (Akku, Lage, Geschwindigkeit) mit Sticks in der Mitte, ohne Renn-Zustand; ohne Layout des Piloten das
  eigene Layout.
- Der Operator am Gimbal (`operator-gimbal`) sieht weiter sein eigenes OSD.

## 3. Netz

| Paket | Richtung | Inhalt |
|---|---|---|
| `propwash:video_pilot_feed` | Client → Server | Entity der eigenen Drohne, Live-Werte und Renn-Zustand |
| `propwash:video_watched_feed` | Server → Zuschauer | dasselbe für die geschaute Drohne |
| `propwash:video_pilot_look` | Client → Server | OSD-Layout (Format des OSD-Sharecodes), Einheit, Stick-Mode |
| `propwash:video_watched_look` | Server → Zuschauer | dasselbe für die geschaute Drohne |

Alle vier sind optional: Server und Clients schicken sie nur, wenn die Gegenseite sie kennt. Das Netzprotokoll bleibt
17; ein Server oder Client ohne die Pakete verhält sich wie in 0.6.0-rc.1.

## 4. Tests

- JUnit `video.PilotFeedTest`: was der Pilot im OSD sieht, kommt beim Zuschauer an (Sticks, Akku, Modus, Warnungen,
  Rennen, Figuren, Repeater), beide Pakete verlustfrei, Zeiten laufen zwischen zwei Paketen weiter, die Uhr des
  Zuschauers zählt die Flugzeit des Piloten nicht doppelt, das Layout des Piloten ersetzt nur das gezeigte OSD,
  zu große Layouts und Fluten werden abgewiesen.
- Selbsttest `video: Zuschauen – Renn-Zustand, OSD-Layout und Sticks des Piloten beim Zuschauer` (simulierter Sender
  mit Kurs aus drei Gates): Countdown mit rotem Start-Gate, dann Rennen mit grüner Durchflugfläche am zweiten Gate,
  Layout Rennen in Orange, mph, Mode 1, bewegte Sticks, Runde und Zeit gezeichnet; danach wieder das eigene OSD,
  eigene Einstellungen unverändert. Mit `-PpropwashScreenshots=true` Bild
  [`screenshots/spectate_pilot_osd.png`](screenshots/spectate_pilot_osd.png): Zuschauer vor dem Start-Gate, zweites
  Gate grün mit Durchflugfläche, OSD des Piloten in Orange mit Runde, Zeit, Gate 2/3, mph und Sticks.
- Selbsttest `video: Zuschauen – Pilot schickt Renn-Zustand, OSD-Layout und Sticks nur mit Zuschauer`: im FPV ohne
  Zuschauer kein Paket, mit Zuschauer mindestens 18 je Sekunde am Server mit Kurs, Phase und Start-Gate, Layout und
  Einheit wie beim Piloten; danach wieder keine Pakete.
- Beitrittstest `watchsolo` (`docs/testing.md`): Der Server lässt den simulierten Piloten Renn-Zustand, Sticks und
  Layout schicken; erwartet sind `multi watcher racehud=drawn`, `multi watcher osd=pilot`, `multi watcher
  sticks=moving` und `multi watcher gate=lit`, Bild `jointest/<label>-pilot-osd.png` mit dem grünen aktiven Gate und
  seiner Durchflugfläche vor der Kamera, dem OSD des Piloten und den Sticks (beide Loader).
- Zwei echte Clients: `tools/mp_dedicated_test.sh` verlangt für Pilot B zusätzlich `racehud=drawn`, `osd=pilot` und
  `gate=lit`.
