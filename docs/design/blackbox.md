# Blackbox (Flugschreiber im Betaflight-Format)

Seit 0.5.0 schreibt Propwash auf Wunsch jeden scharfen Flug als Blackbox-Log im Format von Betaflight/Cleanflight
(`.bbl`, „Blackbox flight data recorder by Nicholas Sherlock“). Betaflight Blackbox Explorer und PIDtoolbox (über
`blackbox_decode`) öffnen die Logs ohne Anpassung; Tuning-Anleitung für Spieler: `docs/blackbox-tuning.md`.
Zuständige Module: blackbox (Format, Recorder, Schreib-Thread, Tab, Taste), sim (lesender Abgriff `FcListener`/`FcSample` an der
Regelschleife), input (Schalterfunktion „Blackbox“, Taste), ui (nur die Übersicht, siehe unten).

## Ablauf

1. `sim.DroneSim` ruft nach jedem Durchlauf der Regelschleife (2 kHz, 1 kHz oder 500 Hz nach `pidLoop`) einen gesetzten
   `FcListener` mit einem wiederverwendeten `FcSample` auf: Sollwerte, gefiltertes und rohes Gyro, P/I/D/F-Anteile,
   Knüppel nach Totband, Mischer-Gas, Motor-Duty nach Temperaturgrenze, Drehzahlen, Bus-Spannung, Akkustrom,
   Barometer, GPS-Empfänger, Modus, Failsafe-Stufe. Ohne Listener läuft nichts zusätzlich; der Abgriff liest nur –
   Bewegungs-Goldens und Kalibrierung bleiben bitgleich.
2. `blackbox.client.BlackboxSession` hängt den `BlackboxRecorder` an das Flugmodell der Pilot-Sitzung (je Bild
   geprüft, ohne Änderung an `PilotSession`) und gibt ihm je Bild Einstellungen, Schalter/Taste, RSSI aus der
   Funkverbindung, GPS-Rettung und Craft name.
3. Der Recorder (Spielthread) quantisiert nur in einen vorab angelegten `int`-Satz (`BlackboxSampler`) und legt ihn in
   einen Ringpuffer (`BlackboxRing`, 8192 Sätze, ein Erzeuger, ein Verbraucher). Keine Allokation je Abtastung
   (`BlackboxHotPathTest`: 40 000 Abtastungen, unter 1 kB). Kopf und Dateiname entstehen einmal beim Scharfschalten.
4. Der Schreib-Thread „Propwash Blackbox“ (`BlackboxWriter`, Daemon, parkt ohne Arbeit) kodiert Frames
   (`BlackboxEncoder` in einen wachsenden Puffer) und schreibt alle 32 kB oder 250 ms. Ist der Ring voll (Platte zu
   langsam), setzt der Recorder aus und macht am nächsten I-Frame mit einem `LOGGING_RESUME`-Ereignis weiter – so
   lesen die Werkzeuge den Log danach weiter.

## Format

Wie Betaflight 4.5.1 (Blackbox Explorer erkennt `Betaflight 4.5.1`, `blackbox_decode` liest Spannung und Strom ab
4.3.0 in Hundertsteln): Kopf `H Product:…`, `H Data version:2`, Felddefinitionen für I, P, H, G und S, danach die
Einstellungen. Frames I (Schlüsselbild), P (Differenz), S (seltene Werte), G/H (GPS, Home), E (Ereignisse).

| Feld | Einheit | I-Frame | P-Frame |
|---|---|---|---|
| `loopIteration` | Durchläufe seit Logbeginn | UVB | INC, NULL |
| `time` | µs (Flugmodell-Zeit) | UVB | STRAIGHT_LINE, SVB |
| `axisP/I/F[0–2]`, `axisD[0–1]` (D je Achse nur bei D-Wert > 0) | PID-Einheiten (Summe ±1000 = voller Mischerbereich) | SVB | PREVIOUS, SVB (I: TAG2_3S32) |
| `rcCommand[0–2]`, `[3]` | Knüppel ±500 nach Totband; Gas 1000–2000 | SVB/UVB | PREVIOUS, TAG8_4S16 |
| `setpoint[0–2]`, `[3]` | °/s; Mischer-Gas ×1000 | SVB | PREVIOUS, TAG8_4S16 |
| `vbatLatest`, `amperageLatest` | 0,01 V, 0,01 A | VBATREF/NEG_14BIT, SVB | PREVIOUS, TAG8_8SVB |
| `baroAlt`, `rssi` | cm; 0–1023 aus der Funkverbindung | SVB, UVB | PREVIOUS, TAG8_8SVB |
| `gyroADC[0–2]`, `gyroUnfilt[0–2]` | °/s gefiltert und roh (`gyro_scale` 1.0) | SVB | AVERAGE_2, SVB |
| `debug[0–3]` | `debug_mode` 6 = GYRO_SCALED: rohes Gyro, `[3]` = 0 | SVB | AVERAGE_2, SVB |
| `motor[0–3]` | DShot 48–2047 = Duty 0–100 % (Betrag, auch im 3D-Modus) | MINMOTOR/UVB, MOTOR_0/SVB | AVERAGE_2, SVB |
| `eRPM[0–3]` | eRPM/100 bei 14 Polen (`dshot_bidir` 1) | UVB | PREVIOUS, SVB |

S-Frame: `flightModeFlags` als Box-Bits von Betaflight 4.5 (ARM, ANGLE, HORIZON, FAILSAFE, GPSRESCUE, BEEPER, BLACKBOX,
AIRMODE, 3D), `stateFlags` (GPS_FIX_HOME, GPS_FIX, GPS_FIX_EVER), `failsafePhase` (IDLE, RX_LOSS_DETECTED, LANDING,
LANDED, GPS_RESCUE), `rxSignalReceived`, `rxFlightChannelsValid`. Geschrieben vor dem ersten Frame, bei jeder
Änderung und alle 256 I-Intervalle. Höhe/Position halten haben in 4.5 keine Box und fliegen als ANGLE.

GPS: Die Minecraft-Welt hat keine Erdkoordinaten. Die Position des simulierten GPS-Empfängers (mit Rauschen, 10 Hz)
wird um 0° N 0° O abgebildet: Breite = −Z / 111 319,49 m, Länge = X / 111 319,49 m (in 1e−7°), Höhe in dm, Tempo in
cm/s, Kurs in 0,1°. Home ist der erste Fix nach Logbeginn (wie Betaflight beim Scharfschalten), H-Frame zuerst und alle
128 I-Frames erneut, G-Frame bei geänderter Position oder Satellitenzahl.

Ereignisse: `SYNC_BEEP` nach dem ersten I-Frame, `LOGGING_RESUME` nach Pausen, `LOG_END` („End of log“) beim
Entschärfen. Bewusst nicht: `FLIGHTMODE` und `DISARM` – Blackbox Explorer kennt sie, `blackbox_decode` überliest ihre
Nutzdaten nicht und verliert danach den Takt bis zum nächsten I-Frame.

Takt: `looptime` 500 (Gyro 2 kHz), `pid_process_denom` = Teiler der Regelschleife, `I interval` = 32 ms in Durchläufen
(64 bei 2 kHz), `P interval` als `1/n` – die Schreibweise mit Schrägstrich lesen `blackbox_decode` und Blackbox
Explorer gleich (die ganzzahlige Schreibweise neuerer Betaflight-Fassungen liest `blackbox_decode` als 1/1).

Kopf mit dem Tune unter Betaflight-Namen (`BlackboxSetup`): `rollPID/pitchPID/yawPID` (P, I, D), `ff_weight`,
`levelPID` (Winkelstärke, Horizon-Stärke, Übergang), `rates_type` (0 Betaflight, 3 Actual) mit `rc_rates`, `rates`,
`rc_expo` in Betaflight-Schreibweise, `gyro_lpf1_static_hz`, `dterm_lpf1/2_static_hz` (PT1), `rc_smoothing_*`,
`iterm_relax*`, `anti_gravity_gain`, `tpa_*`, `pidsum_limit*`, `feedforward_max_rate_limit`, `angle_limit`,
`dshot_idle_value`, `motor_output_limit`, `throttle_limit_*`, `thrust_linear`, `vbat_sag_compensation`,
`thr_mid/thr_expo`, `motor_kv`, `vbatcellvoltage` (Abschalt-, Leer- und Voll-Spannung + 0,1 V wie Betaflights 4,30 V,
damit die Zellenerkennung bei vollem Akku stimmt). Filter, die das Flugmodell nicht hat (dynamischer Notch,
RPM-Filter, dynamische Tiefpässe), stehen auf 0. `Firmware type:Cleanflight`, `Firmware revision:Betaflight 4.5.1
compatible (Propwash v<Version>)`, `Board information:PWSH PROPWASH`, `Firmware date` = Logbeginn; ältere
PIDtoolbox-Fassungen schneiden den Kopf zwischen `Firmware revision`/`Firmware date` und `Craft name`/`acc_hardware` aus.

**Kopfzeile `Firmware revision` (Entscheidung vom 08.10., „v“ vom 09.10.):** Das Log gibt sich nicht als echtes
Betaflight aus, sondern nennt sich „Betaflight 4.5.1 compatible (Propwash v0.5.0)“. Der Anfang `Betaflight 4.5.1` muss
bleiben: `blackbox_decode` liest Name und Version nur als erstes und zweites Wort und skaliert danach die Spannung (ab
4.3.0 in 0,01 V, sonst 0,1 V) und die GPS-Höhe (ab 4.0.0 in dm); ohne erkannte Version zeigte es die Spannung zehnfach.
Das „v“ vor der Propwash-Version muss ebenfalls bleiben: Der veröffentlichte Blackbox Explorer (2025.12.1, auch
blackbox.betaflight.com) sucht die Version mit `/(.*flight).* (\d+)\.(\d+)(\.(\d+))*/i` und nimmt damit das letzte
„Leerzeichen, Ziffer.Ziffer“ im Text – ohne „v“ wäre das die Propwash-Version, und der Explorer behandelte das Log wie
Betaflight 0.5 (falsche Spannung und Zellenzahl, alte Feldtabellen). Erst der unveröffentlichte Explorer-master sucht
gezielt `Betaflight <Version>`. `Firmware type:Cleanflight` bleibt, weil beide Parser sonst die Gyro-Skalierung von
Baseflight nehmen; echtes Betaflight schreibt denselben Wert. Geprüft mit `tools/blackbox_check.sh` gegen
blackbox-tools und den Explorer-Release 2025.12.1 (Fixtures mit Versionsangabe): beide Parser lesen alle Felder ohne
Fehler, Spannung höchstens 0,005 V daneben, der Explorer meldet Betaflight 4.5.1 (die Prüfung verlangt Firmware-Typ 3
und Version 4.5.1; mit dem alten Kopf ohne „v“ liest der Explorer 0.5.0, und sie schlägt fehl; Stand 09.10.). Der
Explorer-Release braucht beim Laden die Browser-Globals `$` und `semver`, die `tools/blackbox/explorer_check.mjs`
dafür setzt. `BlackboxFirmwareRevisionTest` prüft
die Kopfzeile gegen die Versionssuche von blackbox_decode, Explorer 2025.12.1 und Explorer-master.
Verworfen: „Propwash 0.5.0 (Betaflight 4.5.1 compatible)“ – Explorer 2025.12.1 erkennt dann Cleanflight und zeigt die
Spannung zehnfach, `blackbox_decode` erkennt keine Version (es erwartet „Betaflight“ als erstes Wort). Verworfen auch
„… (Propwash 0.5.0)“ ohne „v“ (siehe oben).

**Herkunft des Codes (Prüfung vom 08.10.):** Schreiber (`BlackboxBuffer`, `BlackboxEncoder`, `BlackboxSetup`,
`BlackboxLayout`), Test-Decoder (`BlackboxDecoder` im JUnit-Quellset) und Kalibrier-Auswertung (`calibration.*`) wurden mit
den GPL-Quellen von Betaflight (`blackbox_encoding.c`, `blackbox.c`), blackbox-tools (`parser.c`, `decoders.c`) und
dem Blackbox Explorer (`flightlog_parser.js`, `decoders.js`, `imu.js`) verglichen. Gemeinsam sind nur Formatfakten:
Feldnamen, Kopfschlüssel, Kodierungen, Prädiktoren, Bit-Layouts, die Frame-Planung `I interval`/`P interval`. Der
Schreiber ist eigenständig aufgebaut (eigene Zerlegung, kein übernommener Ablauf). Zwei Teile waren erkennbar
angelehnt und sind neu aus der Formatbeschreibung geschrieben: der Test-Decoder (Ablauf und Namen wie
`parseFrame`/`applyPrediction`/`shouldHaveFrame` aus blackbox-tools, `tag8x4s16` Zeile für Zeile wie
`streamReadTag8_4S16_v2`; jetzt Feldgruppen-Plan, Delta-Durchgang und Vorhersage-Durchgang getrennt, `TAG8_4S16` als
Halbbyte-Strom) und die Lageschätzung der Kalibrierung (`Attitude`, Drehmatrix und Beschleunigungsfenster wie
`rotateVector` im Explorer; jetzt Rodrigues-Drehung um den Drehratenvektor, eigenes Fenster 3/4–4/3 g, exakte
Diskretisierung des Komplementärfilters). Das Kalibrier-Ergebnis ist mit beiden Fassungen gleich. GPL-Werkzeuge
laufen nur als eigene Programme beim Prüfen (`tools/blackbox_check.sh`, `tools/blackbox_calibration.sh` klonen und bauen
sie im Build- bzw. Cache-Ordner), nichts davon liegt im Repository oder im Jar.

Datenrate bei 1/1 und 2 kHz: rund 70 kB/s, etwa 4 MB pro Flugminute (Messung `freestyle_2k_full`: 33,7 Byte je Frame).

## Spiel

- Tab „Blackbox“ (Reihenfolge 320, nach Rates): *Loggen, wenn scharf* (Standard aus), *Lograte* 1/1, 1/2, 1/4, 1/8
  der Regelschleife (Standard 1/1), *Speicher für Logs* 128 MB – 4 GB (Standard 512 MB), Liste der neuesten Logs
  (Name, Zeit, Dauer, Größe; der laufende Log hervorgehoben; bis zu acht, in einem niedrigen Fenster nur so viele, dass
  der Status-Abschnitt samt Hinweisen ohne Scrollen sichtbar bleibt, mindestens drei), *Ordner öffnen*, Status (aus,
  bereit, wartet auf Schalter, zeichnet auf, pausiert, Speicher voll, letzter Log, gelöschte alte Logs, übersprungene
  Abtastungen, Fehler) und Hinweise. Persönlich je Spieler in `config/propwash/blackbox.json`, Logs in
  `<Spielverzeichnis>/propwash/blackbox/propwash_JJJJ-MM-TT_HH-MM-SS_<Craft name>.bbl`.
- Eine Datei je Scharfschalten (wie Betaflight auf SD-Karte); Disarm schließt sie mit `LOG_END`.
- Schalterfunktion „Blackbox“ (Tab „Modi“): belegt, wird nur geloggt, solange der Schalter an ist (wie Modus BLACKBOX);
  bei Failsafe wird immer geloggt. Nicht im Empfänger-Assistenten (optional, wie in Betaflight ein Modus für
  Fortgeschrittene). Ohne Schalter pausiert die Taste „Blackbox pausieren / fortsetzen“ (Standard Num 7) und setzt fort;
  jedes Scharfschalten beginnt wieder mit laufender Aufzeichnung. Pause und Fortsetzen nur an I-Frame-Grenzen, mit
  `LOGGING_RESUME`.
- Speichergrenze: Vor einem neuen Log werden die ältesten Logs gelöscht, bis der Ordner unter drei Viertel der Grenze
  liegt; während des Flugs wird weiter gelöscht, und erreicht der laufende Log allein die Grenze, endet er sauber
  („Speichergrenze erreicht“), bis zum nächsten Scharfschalten.
- Nichts geht an den Server: kein Netzwerkprotokoll, keine Welt-Regel.

## Übersicht: Motorbalken

Im Tab „Übersicht“ zeichnete die Live-Telemetrie bei schmaler Spalte (unter 260 px, z. B. rechte Spalte bei GUI-Skala 3
im 1920×1080-Fenster) die Motorbalken M1–M4 unter die fünf Zeilen, berechnete die Panelhöhe aber nur für die Zeilen –
die Balken lagen unter dem Rahmen. Höhe und Balkenlage kommen jetzt aus `OverviewTab.telemetryHeight`/`motorBarsTop`
(einspaltig 104 px, zweispaltig 72 px), die Balken reichen bis 6 px vor den rechten Rand
(`OverviewTelemetryLayoutTest`). Der Zeilenabstand der Balken folgt der kleinen Schrift (bei GUI-Skala 2 standen die
Beschriftungen M1–M4 aneinander), zweispaltig bekommen die Werte drei Fünftel der Breite, und die Lage-Zeile zeigt kein
„-0°“ mehr. Die übrigen Panels (Analyse, Hinweise, Drohne, Hinweis ohne Flug) rechneten ihre Höhe schon aus ihren Zeilen.
Mit dem 21. Tab ragte die Tab-Leiste bei GUI-Skala 4 im 1080p-Fenster (Standard „Auto“) in die Fußzeile; die Zeilen
rücken jetzt bis auf Schrifthöhe + 1 (9 px statt 11 px) zusammen, bevor sie überlaufen.

## Prüfung

- JUnit `de.lardtom.propwash.blackbox`: Kodierungen gegen die Betaflight-Bytefolge, Rundreise mit einem eigenen
  Test-Decoder gegen das echte Flugmodell (jeder Frame, jedes Feld höchstens ±0,5 Quantisierung vom Flugmodell),
  Lograten, Pause/Fortsetzen, ein Log je Scharfschalten, Speichergrenze, keine Allokation im Spielthread.
- `tools/blackbox_check.sh`: klont `betaflight/blackbox-tools` und `betaflight/blackbox-log-viewer` nach
  `build/blackbox-check/src` (nur lokal), baut `blackbox_decode`, installiert `esbuild` und `semver` nach
  `build/blackbox-check/node`, schreibt Fixtures aus dem Flugmodell (`BlackboxFixtureTest`, Freestyle 2 kHz 1/1,
  Freestyle 1 kHz 1/2, Whoop 500 Hz 1/4, Racer mit Pause und zwei Logs in einer Datei), dekodiert sie mit
  `blackbox_decode` und mit dem Parser des Blackbox Explorers (`tools/blackbox/explorer_check.mjs` bündelt
  `src/flightlog.js` für Node) und vergleicht jedes Feld jedes Frames mit dem Flugmodell (`tools/blackbox/compare.py`).
  Ergebnis 08.10.2026: alle 40 Felder in 40 033 + 4 009 + 1 003 Frames, 0 kaputte Frames in beiden Werkzeugen,
  größte Abweichung 0,5 (Rundung). `blackbox_decode` meldet bei jedem vollständigen Log „Data file contained no
  events“ – das steht im Code der Tools vor dem Ende jedes Logs, auch bei echten Betaflight-Logs.
  Dazu die 25 Logs aus den Selbsttest-Läufen im Spiel (Fabric und NeoForge, mit GPS-Frames und Pausen per Taste): beide
  Werkzeuge lesen sie ohne kaputten Frame, `blackbox_decode` meldet nur die gewollten Lücken der Pausen.
- Selbsttest `blackbox:` (Flug mit Blackbox, Taste pausiert und setzt fort, ausgeschaltet kein Log, nach dem Verlassen
  kein offener Log) und Bildschirmfotos `bildschirmfotos: Blackbox …` (Übersicht nach dem Flug bei GUI-Skala 2, 3, 4
  und deutsch, Tab mit Logs englisch und deutsch).

## Entscheidungen

- **Kompatibilität mit Betaflight 4.5.1 melden** (Kopf „Betaflight 4.5.1 compatible (Propwash v…)“) statt eines
  eigenen Firmware-Namens: beide Werkzeuge schalten Feldnamen, Einheiten und Flag-Namen über die Firmware-Version; mit
  einem fremden Namen fallen sie auf Altformate zurück. 4.5 statt der
  neuesten Fassung, weil PIDtoolbox und `blackbox_decode` 4.5-Logs sicher lesen.
- **Volle Regelschleife als Standard** (1/1 = 2 kHz): Rauschanalyse bis 1 kHz für die Vibration der Motoren (Whoops
  drehen hoch); ohne Flash-Grenze kostet das nur Plattenplatz. Kleinere Raten wählbar.
- **Standard aus**: rund 4 MB pro Flugminute für Spieler, die nie tunen, wären Verschwendung.
- **Datei je Scharfschalten** statt mehrerer Logs in einer Datei: einfacher zu finden und zu löschen; beide Werkzeuge
  lesen auch mehrere Logs je Datei (geprüft).
- **Älteste Logs löschen** statt beim vollen Speicher aufzuhören: Betaflight hört auf, und dann fehlt genau der neue
  Flug. Die Grenze ist einstellbar und der Tab sagt, was gelöscht wurde.
- **debug im Modus GYRO_SCALED** (rohes Gyro, wie `gyroUnfilt`): ältere PIDtoolbox-Fassungen lesen das ungefilterte
  Gyro nur aus `debug[0–2]`; kostet rund 5 Byte je Frame.
- **Kein Beschleunigungssensor** (`accSmooth`): Achsen und Vorzeichen müssten zur IMU des Explorers passen; das
  Flugmodell liefert die Lage ohnehin genau, für das Tuning zählt das Gyro.
- **Schreiben im eigenen Thread mit Ringpuffer** statt Puffern im Spielthread: Plattenzugriffe dürfen kein Bild
  verzögern; bei Überlauf lieber eine Lücke mit `LOGGING_RESUME` als ein ruckelndes Spiel.
