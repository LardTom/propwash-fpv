# VTX-Leistung: einstellbare Sendeleistung, Auto, Strom und Wärme (ab 0.5.2)

Wie stark der Videosender einer Drohne sendet, was das für Reichweite, Durchdringung, Akku und Temperatur bedeutet und
wie der Spieler die Leistung wählt. Rechnung als reines Java in `video.vtx` (keine Minecraft-Typen), Anbindung an die
Bauteile in `video.VtxTables`, im Spiel in `video.client.VtxRuntime`. Für Spieler: [`../vtx-power.md`](../vtx-power.md).
Die Abschattung durch Hindernisse beschreibt [`vtx-obstruction.md`](vtx-obstruction.md), die Quellen der Strom- und
Leistungswerte stehen in [`realism/power-video.md`](realism/power-video.md) Abschnitt 4.1–4.2 und 7.

## Ausgangslage bis 0.5.1

Jedes Videomodul hatte eine feste Reichweite (`range_blocks`) und eine feste Leistungsaufnahme (`power_w`); das Feld
`vtx_power_mw` war reine Anzeige. Echte Sender lassen sich dagegen in Stufen einstellen (VTX-Tabelle in Betaflight,
Leistungsmenü der Brille bei DJI und Walksnail), und die Wahl kostet Strom und Wärme.

## Modell

### Leistungsstufen je Modul (`VtxTable`)

- `vtx_power_mw` ist ab 0.5.2 die **Nennleistung**: die Stufe, bei der `range_blocks` und `power_w` gelten. Eine Drohne
  ohne eigene Einstellung sendet mit *Auto* (Abschnitt Auto); mit der festen Stufe Nennleistung verhält sich alles
  bitgleich wie vor 0.5.2.
- `vtx_levels_mw` (neu, optional) listet die Stufen aufsteigend, höchstens acht, die Nennleistung muss dabei sein.
- Ohne Liste leitet Propwash eine Tabelle aus der Nennleistung ab (alle Stufen bis zur Nennleistung):
  analog bis 400 mW `25 · 100 · 200 · 400`, analog darüber `25 · 200 · 400 · 800 · 1600 · 2000`, digital
  `25 · 100 · 400 · 700 · 1200` (Stufen von DJI O4). Ein Modul mit `vtx_power_mw: 800` kann so 25–800 mW, ein
  25-mW-Whoop-Sender bleibt bei einer Stufe. Ohne `vtx_power_mw` gelten 200 mW (wie bisher in der Stromformel).

| Modul | Nennleistung | Stufen |
|---|---|---|
| `propwash:analog_nano` (Whoop) | 200 mW | 25 · 100 · 200 · 400 |
| `propwash:analog_race` (5″) | 400 mW | 25 · 200 · 400 · 800 · 1600 |
| `propwash:digital_lite` | 400 mW | 25 · 100 · 400 · 700 |
| `propwash:digital_hd` | 700 mW | 25 · 100 · 400 · 700 · 1200 |

Die Nennleistungen folgen aus den bisherigen `power_w`-Werten und den Faustformeln aus `realism/power-video.md`:
analog 3,5 W ≈ 0,75 W Kamera + 1,0 W + 4,3 × 0,4 W, Whoop 1,9 W ≈ 200 mW an 1S, digital 5,5 W ≈ O4 Lite bei 400 mW,
9,0 W ≈ O4 Pro bei 700 mW.

### Reichweite und Durchdringung

Mehr Leistung hebt die Link-Reserve um `10 · log10(P / P_Nenn)` dB – auf jedem Weg gleich, also gegen Entfernung und
Hindernisse. Im Freiraum entspricht das der Reichweite `range_blocks · √(P / P_Nenn)`: doppelte Leistung ≈ 1,41-fache
Reichweite und 3 dB mehr gegen eine Wand. `VideoState` rechnet die Qualität mit der skalierten Reichweite, Repeater-Ketten
ebenso (Repeater senden wie bisher mit der Reichweite des Drohnen-Senders). Die Hindernis-Abtastung reicht bis zur
höchsten Stufe des Moduls. Die Kreativ-Brille behält ihre eigene Reichweite aus den Einstellungen, die Welt-Regel
„unbegrenzte Reichweite“ bleibt unbegrenzt.

### Strom (`VtxTable#supplyW`, `DroneSim#setVideoPowerDelta`)

Leistungsaufnahme des Videosystems je Stufe: `power_w + k · (P − P_Nenn)` mit k = 4,3 W/W analog (Fit an einen
800-mW-Sender bei 12 V) und 2,5 W/W digital (wie `EnergyModel`, DJI O4 Pro gemessen 2,6 W/W), nie unter 0,25 W. Der
Unterschied zur Nennleistung geht als Zusatzlast in das Flugmodell (`DroneSim#setVideoPowerDelta`, addiert zur
Avionik-Last in `extraPower`). Bei Nennleistung ist der Zusatz genau 0 und wird gar nicht addiert. Bewegungs-Goldens und
Kalibrierung rechnen das reine Flugmodell ohne Sender (`VtxUnit` läuft nur im Spiel beim Piloten) und bleiben bitgleich,
Workbench-Werte (Flugzeiten) gelten weiter bei Nennleistung. Im Spiel ändert *Auto* den Zusatz je nach Stufe.

| Modul | Stufe | Reichweite | Video-System | am Boden ohne Luftstrom, 22 °C | dasselbe, 40 °C (Wüste) | Schweben | Reiseflug |
|---|---|---|---|---|---|---|---|
| analog_nano | 25 mW | 88 | 1,1 W (−0,8) | nie | nie | 33 °C | 28 °C |
| | 200 mW | 250 | 1,9 W | nie | 3,9 min | 39 °C | 31 °C |
| | 400 mW | 354 | 2,8 W (+0,9) | 4,0 min | 2,0 min | 45 °C | 34 °C |
| analog_race | 25 mW | 95 | 1,9 W (−1,6) | nie | nie | 32 °C | 27 °C |
| | 400 mW | 380 | 3,5 W | nie | 4,3 min | 39 °C | 31 °C |
| | 800 mW | 537 | 5,2 W (+1,7) | 4,1 min | 2,1 min | 46 °C | 34 °C |
| | 1600 mW | 760 | 8,7 W (+5,2) | 1,7 min | 1,1 min | 60 °C | 42 °C |
| digital_lite | 25 mW | 75 | 4,6 W (−0,9) | nie | 4,5 min | 37 °C | 30 °C |
| | 700 mW | 397 | 6,2 W (+0,8) | nie | 2,2 min | 40 °C | 31 °C |
| digital_hd | 25 mW | 98 | 7,3 W (−1,7) | nie | 8,4 min | 37 °C | 30 °C |
| | 700 mW | 520 | 9,0 W | nie | 4,6 min | 39 °C | 31 °C |
| | 1200 mW | 681 | 10,2 W (+1,2) | nie | 3,7 min | 40 °C | 31 °C |

„Reichweite“ ist `range_blocks` der Stufe (Blöcke, freie Sicht; dort rauscht das Bild schon, weg ist es analog erst
bei doppelter, digital bei 1,26-facher Entfernung); die Zeiten bis zum Hitzeschutz bei 85 °C gelten für eine Drohne,
die unscharf am Boden steht.

### Wärme (`VtxHeat`)

Ein Körper mit Wärmekapazität 1 J/(g·K) × Modulmasse, Wärmequelle = Leistungsaufnahme − abgestrahlte HF-Leistung,
Kühlung `h = h_still · (1 + 1,2 · √v)` mit v = Fluggeschwindigkeit durch die Luft plus Propellerluft (6 m/s · √mittlere
Motorleistung, nur scharf). Integriert wird exakt (`T = T∞ + (T − T∞) · e^(−h·dt/C)`), also unabhängig von der Bildrate.

`h_still` folgt aus einem **Auslegungspunkt**: Bei Nennleistung und 22 °C erreicht jedes Modul am Boden ohne Luftstrom
75 °C – Hersteller legen Kühlkörper so aus, dass ein Sender seine Standardleistung auf dem Tisch übersteht. Mehr
Leistung, ein heißes Biom oder beides überhitzt ihn ohne Luftstrom, im Flug bleibt er kühl. Bei digitalen Einheiten
dominiert die Grundlast, eine höhere Stufe heizt dort kaum mehr; analog verdoppelt 1600 mW die Wärme.

Umgebung: Biom-Temperatur an der Drohne, `6 + 20 · t` °C, begrenzt auf −15…40 °C (Ebene 22 °C, Wald 20 °C, Taiga
11 °C, Schnee 6 °C, Dschungel 25 °C, Wüste und Nether 40 °C).

**Hitzeschutz** (nur mit der Welt-Regel *Hardware-Grenzen* `propwash:hardware_limits`, wie die Motor-Grenzen; setzt eine
Zone die Regel, gilt der Wert der Zone, `PilotSession#appliedPhysics`): ab 85 °C
regelt der Sender eine Stufe herunter, danach alle 4 s eine weitere, solange er heiß bleibt; ab 75 °C gibt er die
Stufen im selben Takt wieder frei. Ab 80 °C zeigt das OSD „VTX ÜBERHITZT“. Kein Schaden, kein Abschalten.

### Auto (`VtxAuto`, Standard ab dem Spieltest vom 15.10.)

*Auto* ist die Einstellung jeder Drohne ohne eigene Wahl, analog wie digital (Module mit mehr als einer Stufe; ein Modul
mit nur einer Stufe sendet fest). Auto wählt die niedrigste Stufe, bei der die Link-Reserve (bester Weg direkt oder über
Repeater, inklusive Abschattung) das Ziel erreicht. Hoch geht es nach 0,25 s direkt auf die nötige Stufe, herunter nur
eine Stufe je 2 s und erst ab 3 dB mehr Reserve (keine Pendelei an der Schwelle). Ohne Messung (keine Videoverbindung)
bleibt die Stufe. Im Sichtflug ohne Brille schätzt Auto die Reserve aus der Entfernung und – seit der Nachbesserung nach
dem Spieltest – aus der Abschattung, die die Steuerstrecke ohnehin abtastet (`video.client.ControlLinkScan`,
[`vtx-obstruction.md`](vtx-obstruction.md)).

| System | Wer regelt | Messgröße | Ziel |
|---|---|---|---|
| digital | der Sender selbst (DJI, Walksnail: Rückkanal der Brille) | Reserve der Videoverbindung | 10 dB = 4 dB sauberes Digitalbild + 6 dB Puffer |
| analog | der Flugcontroller über SmartAudio/Tramp | Signalstärke der Fernsteuerung am Empfänger (RSSI/LQ, ELRS/Crossfire-Telemetrie); sie läuft denselben Weg wie das Video | 15 dB = 12 dB rauschfreies Analogbild + 3 dB Puffer |

Mit dem breiteren digitalen Fenster (sauber ab +7 dB, Stufe „sauber“ ab +4 dB) liegt das digitale Ziel von 10 dB 6 dB
über der Stufe „sauber“.

Ein analoger Sender kann sein eigenes Bild nicht messen, wohl aber kann der Flugcontroller seine Leistung im Flug
umstellen (SmartAudio, Tramp; INAV kann das zum Beispiel per Programmierlogik nach RSSI oder Entfernung). Der Empfänger
auf der Drohne misst die Funkverbindung vom Piloten; Entfernung, Wände und Repeater wirken auf sie wie auf das Video.
Propwash rechnet dafür denselben Weg wie für das Video (`VideoState#ratedMarginDb`). Die Steuerstrecke selbst wird seit
der Nachbesserung nach dem Spieltest mit derselben Abschattung gerechnet (0,6 × Dämpfung bei 2,4 GHz,
[`vtx-obstruction.md`](vtx-obstruction.md)); der Flugcontroller rechnet die gemessene Stärke auf das Video um. Weil das
nur indirekt ist, hält Auto analog mehr Reserve über dem rauschfreien Bild als digital. Beispiel Analog Race
(Nennleistung 400 mW, `range_blocks` 380) im Freien: bis etwa 17 Blöcke 25 mW, bis 48 Blöcke 200 mW, bis 68 Blöcke
400 mW, bis 95 Blöcke 800 mW, darüber 1600 mW.

Verworfen: Auto analog nur nach der Entfernung (GPS) ohne Hindernisse – in Bandos und hinter Häusern wäre das Bild bei
Auto schlechter als mit der festen Nennleistung, und das wäre als Standard ein Rückschritt; Auto analog weglassen und
die Nennleistung als Standard behalten – widerspricht der Vorgabe „Auto für jede Drohne“.

### Niedrig, wenn unscharf

Wie `vtx_low_power_disarm` in Betaflight: *Aus*, *Immer* (unscharf immer niedrigste Stufe) oder *Bis zum ersten
Scharfschalten* (danach volle Leistung, damit man eine abgestürzte Drohne wiederfindet). Gilt auch für Auto.

## Einstellung je Drohne

Die Wahl steht im Tuning der Drohne (`FlightTune`, Gruppe `TuneGroup.VTX`) wie Kanal und LEDs und reist mit Item,
Entity und Werkbank:

| Schlüssel | Bedeutung |
|---|---|
| `vtx.powerMw` | feste Stufe in mW; fehlt sie, gilt *Auto*. Passt sie nicht zum Modul (Modul getauscht), gilt die nächstliegende Stufe (logarithmisch). Eine gespeicherte Stufe bleibt fest, auch wenn sie der Nennleistung entspricht. |
| `vtx.powerAuto` | 1 = Auto ausdrücklich gewählt (gewinnt vor `vtx.powerMw`; die Auswahl „Auto“ setzt es und entfernt `vtx.powerMw`) |
| `vtx.lowPowerDisarm` | 1 = immer, 2 = bis zum ersten Scharfschalten |

Bedienung:

- Tab **Videokanal**, Abschnitt **Sendeleistung (VTX)**: Leistung („Auto (Standard)“ und die Stufen), *Niedrig, wenn
  unscharf*, Erklärung mit Reichweite, Leistungsaufnahme und Zeit bis zum Überhitzen am Boden, im Flug eine Live-Zeile
  (Stufe, Temperatur, Watt, Hitzeschutz).
- Schalterfunktion **VTX-Leistung** (Modi-Tab, wie ein Taster): jede Flanke schaltet eine Stufe weiter, nach der höchsten
  auf Auto, von Auto auf die niedrigste – wie eine VTX-Tabelle auf einem Kanal. Taste **F10** („VTX-Leistung
  umschalten“) macht dasselbe. Ein Hinweis-Toast nennt die neue Stufe; die Wahl geht sofort in den Sender und als
  Tuning an den Server.
- OSD-Element **Sendeleistung (VTX)**: `400MW`, mit Auto `AUTO 25MW`, im Hitzeschutz in Warnfarbe. In den Vorlagen
  Minimal, Standard und Rennen links unter dem Kanal (1/7), im klassischen Layout neben dem Kanal (8/6).

## Mehrspieler und Zuschauen

Die Einstellung ist Teil des synchronisierten Tunings. Zuschauer rechnen ihr Bild mit der eingestellten Stufe der Drohne;
bei Auto mit der Nennleistung, denn die aktuelle Auto-Stufe, *Niedrig, wenn unscharf* und der Hitzeschutz laufen nur
beim Piloten und werden nicht übertragen (eine unscharf am Boden stehende Drohne sendet beim Piloten mit 25 mW, beim
Zuschauer mit der eingestellten Stufe). Fliegt der Server die Drohne selbst zurück (GPS Rescue ohne Pilot,
`ServerReturnFlight`), rechnet er den Mehr- oder Minderverbrauch der eingestellten Stufe wie der Client ein (bei Auto
die Nennleistung). Die neue Bauteil-Angabe ändert die Definitionen (Handshake der Bauteil-Liste): Netzprotokoll 16,
Parts-API 1.6.0.

## Tests

- JUnit: `VtxTableTest` (Tabellen, Ableitung, Gewinn, Strom, bitgleiche Nennleistung, Auto ab zwei Stufen),
  `VtxPowerSettingsTest` (leere Einstellung = Auto auf jedem System, feste Stufe bleibt fest, Umschalten über Auto,
  Bereinigen), `VtxAutoTest` (herunter langsam, hoch schnell, Hysterese, Ziel analog 15 dB und digital 10 dB),
  `VtxHeatTest` (Auslegungspunkt, Zeiten, Luftstrom, Biome), `VtxUnitTest` (feste Nennleistung wie vorher, leere
  Einstellung regelt analog, Mehrstrom, Niedrig-wenn-unscharf, Auto, Hitzeschutz
  nur eine Stufe je 4 s), `VtxTablesTest` (eingebaute Module, Validierung), `VideoPowerDeltaTest` (Flugmodell bitgleich
  bei 0, Mehrverbrauch bei +6 W), `VtxPowerTuneTest` (Schlüssel überleben den Weg über den Server).
- Selbsttest `vtx-leistung:` (Fabric und NeoForge): Reichweite je Stufe (Qualität = Formel aus Reserve + Gewinn, steigt
  mit der Leistung), Wand – mehr Leistung dringt durch, Mehrstrom, Taste und OSD im Flug, Hitzeschutz und Warnung,
  Auto regelt digital herunter. Bildschirmfotos `bildschirmfotos: VTX-Leistung` (Tab digital mit Auto, Tab analog mit
  1600 mW, OSD im Hitzeschutz; englisch und deutsch).
- Messwerte im Selbsttest (Analog Race, 32 Blöcke, Flachwelt): freie Sicht 19,8 dB Reserve bei Nennleistung → Signal
  25 mW 0,86, 400 und 1600 mW 1,0; hinter einer 1 Block dicken Steinwand (27 dB) 25 mW 0,00, 400 mW 0,002, 1600 mW
  0,30. Mehrlast bei 800 mW 1,72 W; Digital HD mit Auto erreicht 5 Blöcke vor dem Piloten nach 6 s 25 mW (−1,69 W).

## Grenzen

- Kein Pit-Mode (0 mW zum Einrichten); *Niedrig, wenn unscharf* deckt den Zweck am Boden ab.
- Repeater senden mit der Reichweite des Drohnen-Senders: Die Stufe der Drohne skaliert jeden Abschnitt einer
  Repeater-Kette, auch Repeater → Repeater und Repeater → Pilot. Echte Repeater hätten eine eigene Leistung; Auto dreht
  deshalb auch dann hoch, wenn der Engpass zwischen Repeater und Pilot liegt (bewusste Vereinfachung).
- Keine Störung anderer Kanäle durch hohe Leistung (Propwash kennt keine Kanalstörung).
- Der Sender kühlt nur, solange geflogen wird: Nach dem Ende einer Sitzung beginnt er beim nächsten Flug wieder bei
  Umgebungstemperatur.
- Die Schalterfunktion ist ein Schritt je Flanke; eine Zuordnung von Schalterstellungen zu Stufen (Betaflight-Befehl
  `vtx`) gibt es nicht.

## Entscheidungen

| Was | Warum | Verworfen |
|---|---|---|
| `vtx_power_mw` = Nennleistung, bei der Reichweite und Strom gelten | Goldens und Kalibrierung bleiben bitgleich; JMP-Module (Nennleistung = Maximum) passen ohne Änderung | Standard „höchste Stufe“ |
| Standard ist *Auto* für jede Drohne (Spieltest 15.10.); fehlt `vtx.powerMw`, gilt Auto, eine gespeicherte Stufe bleibt fest | so stellen echte Piloten ein; Drohnen aus 0.5.x, neue Drohnen, Vorlagen und Sharecodes haben keine Einstellung und bekommen Auto, ausdrückliche Wahl bleibt | Standard Nennleistung (vorher; „1000 mW (Standard)“ wirkte wie eine Empfehlung); alte Drohnen per Migration markieren (sie haben gar keinen Wert, der zu unterscheiden wäre) |
| Stufen als optionales Bauteil-Datum plus abgeleitete Tabelle | Addons können echte VTX-Tabellen angeben, ohne Angabe gibt es trotzdem sinnvolle Stufen | feste Tabelle nur nach Systemtyp |
| Analog-Race 25–1600 mW, Whoop 25–400 mW, digital nach DJI O4 | entspricht den Vorgaben und echten Modulen; 2000 mW nur für Module mit 2-W-Nennleistung | 2 W für den 8-g-Race-Sender (unrealistisch für Masse und Kühlung) |
| Auto auch analog, geregelt vom Flugcontroller nach der Funkverbindung, Ziel 15 dB | realistisch über SmartAudio/Tramp und RSSI; derselbe Weg wie das Video hält Auto in Bandos und hinter Wänden mindestens so gut wie die Nennleistung | Auto nur digital (Vorgabe: Auto für jede Drohne); analog nur nach GPS-Entfernung (schlechter als Nennleistung hinter Hindernissen) |
| Wärme mit Auslegungspunkt 75 °C bei Nennleistung | Standardleistung überhitzt am Boden bei Raumtemperatur nie, mehr Leistung, Wüste oder beides schon – wie im echten Betrieb | Wärmeleitwert je Gramm (Standard-Cinewhoop wäre nach 3 min am Boden überhitzt) |
| Hitzeschutz drosselt stufenweise, kein Schaden | spürbar, aber fair; echte Sender mit Schutz regeln herunter | Abschalten oder Defekt |
| Hitzeschutz an die Welt-Regel *Hardware-Grenzen* gebunden | gleiche Logik wie Motor-Überhitzung; *Arcade* schaltet beides ab | eigene Welt-Regel |
| Schalter schaltet je Flanke eine Stufe weiter (zyklisch, digital mit Auto) | passt zum bestehenden Ein/Aus-Schaltersystem; wie der Knopf am Sender | Schalterstellungen → Stufen (großer Umbau des Eingabesystems) |
| Taste F10 | frei, keine Vanilla-Belegung, neben F7–F9 (Zuschauen, LEDs, Aufnahme) | F6 (Vanilla-Taste) |
| Biom-Temperatur als Umgebung | Wüste und Nether heizen spürbar, Schnee kühlt; eine Zeile im Client | feste 22 °C |
| Zuschauer rechnen mit der eingestellten Stufe | kein neues Paket nötig | aktuelle Auto-Stufe übertragen (Protokoll und Bandbreite) |
