# Motor-Hitze: Prüfung und Umbau (Propwash 0.6.0, Phase A und B)

Stand: 2026-10-18, gehört zu Propwash 0.6.0 (Teil H in `docs/design/0.6.0/gameplay.md` §3.H). Vorgabe sinngemäß: die
Motor-Hitze für 0.6.0 noch einmal genau prüfen; gerade größere Kopter haben mit Hitze zu kämpfen. Phase A vermisst das
Modell, prüft es gegen Zielbänder und echte Hardware und stellt Texte richtig. Phase B (Umbau der Drosselung) wird nur
gebaut, wenn Phase A einen Befund nach der Regel in §3.H belegt.

**Ergebnis Phase A: Phase B ist nötig.** Befund 1 (Whoops werden bei Dauervollgas nicht heiß, und kein Satz
Kühlkonstanten ändert das) und Befund 2 (gedrosselt wird nach der Motortemperatur, auch wo der ESC kalt bleibt und echte
Firmware nie abregelt). Einzelheiten in Abschnitt 6.

**Ergebnis Phase B (umgesetzt, Abschnitt 8):** gedrosselt wird nur noch vom ESC nach seiner eigenen Temperatur, mit einer
Kennlinie wie BLHeli_32. Ein heißer Motor verliert etwas Drehmoment und verschleißt, regelt aber nicht ab. Whoops werden
bei Dauervollgas heiß, große Kopter erreichen bei Dauervollgas weiter ihre Hitzegrenze, und alle Zielbänder sind erfüllt.
Die Abschnitte 1 bis 7 beschreiben den Stand von Phase A (Umgebung 25 °C, Modell aus 0.5.2). Nachtrag nach der
Prüfung V12 (Abschnitt 8.7): der eingebaute Cinewhoop-Motor 1404 4600 KV kühlt nach seinem Datenblatt und ist nicht mehr
der heißeste eingebaute Motor.

## 1. Modell in 0.5.2 (geprüft im Code)

`sim.Thermal` rechnet je Motor eine Motor- und eine ESC-Temperatur als Ein-Körper-Modell (exakte Lösung je Schritt):

| Größe | Motor | ESC |
|---|---|---|
| Verlust | `I² · R_Motor + kt · I0 · ω` (Kupfer plus Leerlauf-/Eisenverlust) | `I² · R_ESC` |
| Kühlung | `(0,075 W/K + 1,5·10⁻⁴ W/K je rad/s · ω) · (m/33 g)^(2/3)`; Teile-JSON darf `cooling_w_per_k` und `cooling_w_per_k_per_rad_s` setzen | `0,02 W/K · (Stack/18 g) · (1 + ω/ω_Schweben)` |
| Wärmekapazität | `0,5 J/(g·K) · Motormasse` oder `heat_capacity_j_per_k` | `0,3 J/(g·K) · Stack/4` |

- **Drosselung:** hängt an der **Motortemperatur**. Ab `derate_start_c` (Standard 130 °C) sinkt die Leistungsgrenze
  linear bis 20 % bei `max_temp_c` (150 °C), Ereignis `MOTOR_DERATE` mit 10 K Hysterese, OSD „MOTOR OVERHEAT“, Meldung
  beim Beginn. Darüber Verschleiß mit `wear_per_second_at_max · x²`, über `max_temp_c` das Ereignis `MOTOR_OVERHEAT`.
- **ESC:** wird warm gerechnet, drosselt aber nie.
- **Kupfer:** Der Temperaturkoeffizient (0,393 %/K) wirkt nur mit `SimTuning.thermalEffects`, im Spiel aus.
- **Umgebung:** Das Flugmodell im Spiel rechnet mit 20 °C (`PilotSession`, Heimflug), die Werkbank mit 25 °C.
- **Werkbank-Schätzung:** `EnergyModel` rechnet im Flug zusätzlich mit Fahrtwind-Kühlung `1 + 0,6 · √(v / 10 m/s)`;
  das Flugmodell kennt diese Kühlung nicht. Nach einem aggressiven Akku zeigt die Werkbank deshalb 20–25 K weniger, als
  das Flugmodell erreicht (5″ Freestyle 55 statt 76 °C).

## 2. Echte Hardware (Recherche mit Quellen)

**Ein Brushless-Motor regelt nicht selbst ab.** Abgeregelt wird, wenn überhaupt, im ESC nach dessen eigener Temperatur:

| Firmware | Temperaturschutz ab Werk | Kennlinie | Quelle |
|---|---|---|---|
| BLHeli_32 | an, Schwelle 140 °C (Voreinstellung in BLHeliSuite32), einstellbar | gemessen im MCU; ab der Schwelle sinkt die Leistung, bei etwa Schwelle + 15 °C sind es 25 %, tiefer nicht (das Handbuch schreibt „150C“, gemeint ist erkennbar 15 °C) | Handbuch Rev32.x „Temperature Protection“ [1], Voreinstellung 140 °C [2] |
| AM32 | **aus** (`TEMPERATURE_LIMIT` = 255; gültig 70–140 °C) | über der Grenze sofort etwa 25 % der Höchst-Tastung, linear bis fast 0 bei Grenze + 10 K | `Src/main.c` und Konfigurator `src/eeprom.ts` [3] |
| Bluejay | **aus** seit 0.21.0 (Begründung in den Release-Notizen: der Schutz habe lange nicht funktioniert); wählbar 80–140 °C in 10-K-Schritten | Stufen etwa 80 %, 60 % (+5 K), 40 % (+10 K), 20 % (+15 K), Grenze ändert sich um eine Stufe je 256 ms | `src/Settings/BluejaySettings.asm`, `src/Modules/Scheduler.asm`, Release-Notizen [4] |
| Betaflight | regelt nicht nach Temperatur ab | nur OSD-Alarm aus der ESC-Telemetrie (`osd_esc_temp_alarm`, Kennbuchstabe „T“) und CPU-Alarm | `src/main/osd/osd_warnings.c` [5] |

**Ein heißer Motor verliert trotzdem Leistung, ohne dass ihn jemand drosselt:**

- Kupfer: +0,393 % Widerstand je Kelvin (bei 130 °C rund +41 % gegenüber 25 °C).
- Neodym-Magnete: Remanenz reversibel −0,12 %/K (Güte N, M), −0,11 (H), −0,10 (SH), −0,09 (UH); oberhalb der
  Einsatzgrenze der Güte auch dauerhaft: N 80 °C, M 100 °C, H 120 °C, SH 150 °C, UH 180 °C, EH 200 °C [6].
- Lackdraht: Isolierstoffklasse H 180 °C, N 200 °C, R 220 °C (IEC 60085); darüber droht ein Wicklungsschaden.

**Erfahrungswerte:** 5″-Motoren sind nach einem normalen Akku warm (um 40 °C), nach hartem Fliegen oder an heißen Tagen
heiß; was man nicht mehr anfassen mag (über etwa 60–70 °C), gilt als zu heiß [7]. Bei Whoops gelten Hitze und
Magnetverlust als Hauptursache für müde Motoren; sehr hoch drehende 0802-Motoren werden laut Händler „zu heiß“ [8].
Größere Kopter: Verluste wachsen etwa mit der Leistung, die Kühlfläche nur mit der Oberfläche; große Props drehen
langsamer und kühlen schlechter.

## 3. Messung

`sim.ThermalCharacterizationTest` (JUnit, läuft in `tools/fastcheck.sh test` mit den eingebauten Teilen, mit
Just-More-Parts-Daten über `tools/calibration.sh`, Tabelle in `build/calibration/thermal.txt`):

- **Baupläne:** alle Presets (5 eingebaute, 22 aus Just More Parts 1.1.1) und für jeden Motor ohne Preset ein typischer
  Bauplan: das Preset mit derselben Motor-Aufnahme und passender Zellenzahl, dessen Motor nach Masse und KV am nächsten
  liegt, mit dem Motor getauscht (muss baubar sein, schweben können und T/W ≥ 2 haben). 47 Zeilen, jeder der 40 Motoren
  kommt vor.
- **Rechnung:** die Thermik des Spiels (`Thermal#advance`) mit festem Arbeitspunkt aus `OperatingPoints`, Umgebung
  25 °C; Beharrung nach 2 h, Zeit bis `derate_start_c` (Motor) und bis 140 °C (ESC).
- **Fälle:** Schweben; 50 % Gas im Vorwärtsflug (Neigung so, dass die Höhe gehalten wird); Vollgas am Boden; Schweben mit
  1,5-facher Last; kleinstes Dauergas am Boden, ab dem die Schwelle erreicht wird; aggressiver Akku (Mischung der Werkbank,
  einmal mit, einmal ohne Fahrtwind-Kühlung); Vollgas bis der Akku leer ist.
- **Abgleich:** Für jede Zeile läuft das Flugmodell 600 s auf dem Prüfstand (Vollgas, Akku unendlich, Grenzen an). Die
  Drosselung beginnt dort höchstens 5 % (mindestens 2 s) neben der Rechnung, ohne Drosselung liegt die Endtemperatur
  höchstens 1,5 K daneben. Die Akku-Rechnungen stimmen für die eingebauten Presets exakt mit der Werkbank (`EnergyModel`) überein.

Auszug (°C, Umgebung 25 °C; M = Motor, E = ESC):

| Bauplan | Schwelle | Schweben M | 50 % vorwärts M | Vollgas Boden M/E | Zeit bis Schwelle | aggr. Akku | Vollgas bis leer |
|---|---|---|---|---|---|---|---|
| Whoop 65 mm (eingebaut) | 130 | 28 | 28 (hält Höhe nicht) | 30/36 | – | 29 | 30 |
| Cinewhoop 3″ (eingebaut) | 130 | 51 | 58 | 123/106 | – | 68 | 103 |
| 5″ Freestyle (eingebaut) | 130 | 62 | 73 | 124/67 | – | 76 | 93 |
| 5″ Race (eingebaut) | 130 | 58 | 72 | 143/73 | 62 s | 92 | 96 |
| 7″ Long Range (eingebaut) | 130 | 64 | 74 | 113/52 | – | 83 | 98 |
| JMP Whoop 75 mm, 0802 19000 KV | 130 | 30 | 32 | 53/79 | – | 39 | 49 |
| JMP 7″ 8S, 2807 1100 KV | 140 | 45 | 53 | 113/68 | – | 62 | 96 |
| JMP 7″ 8S, 2806.5 1050 KV (getauscht) | 130 | 58 | 70 | 168/61 | 38 s | 86 | 133 |
| JMP X-Class 10″, 3115 900 KV | 140 | 49 | 59 | 120/83 | – | 83 | 98 |
| JMP X-Class 12″, 4214 400 KV | 145 | 63 | 67 | 106/47 | – | 90 | 92 |
| JMP X-Class 13″ H, 4214 400 KV | 145 | 64 | 73 | 147/72 | 260 s | 105 | 112 |
| JMP X-Class 12″, 4214 530 KV (getauscht) | 145 | 72 | 90 | 175/86 | 124 s | 110 | 117 |

Kein ESC erreicht in irgendeinem Fall 140 °C; der heißeste liegt bei 117 °C (X-Class 10″ mit 3115 1050 KV).
Mit dem Akku drosselt nur ein getauschter 7″ (2806.5 1050 KV, 133 °C nach einem Akku Vollgas am Boden) und zwei
überlastete Kombinationen (1202.5 5500 KV im 3″-Cinewhoop, 1404 2750 KV im 3,5″-Toothpick).

## 4. Zielbänder

Die Bänder aus §3.H mit Zahlen; „warm“ ab 45 °C, „heiß“ ab 55 °C (lässt sich nicht mehr lange anfassen):

| Band | Prüfung | Ergebnis |
|---|---|---|
| B1 5″ Freestyle nach aggressivem Akku warm bis heiß, unter der Schwelle | 45 °C bis Schwelle − 20 K | **erfüllt**: 73–76 °C |
| B2a 7″ im Reiseflug deutlich unter der Schwelle | 50 % Gas vorwärts ≤ Schwelle − 40 K | **erfüllt**: 53–74 °C |
| B2b 7″ bei Dauervollgas oder schwerer Last über der Schwelle | Beharrung Vollgas am Boden oder 1,5-fache Last ≥ Schwelle | **verfehlt** bei 4 von 5: eingebauter 7″ 113/130 °C, JMP 2807 1100 KV 113/140 und 106/140, 2806.5 1300 KV 86/130; nur der getauschte 1050 KV erreicht 168 °C. Schwere Last bleibt bei 48–69 °C |
| B3 X-Class und Cinelifter erreichen die Schwelle bei Dauerlast als erste | alle bei Dauervollgas über der Schwelle, mit weniger Dauergas als jede andere Klasse | **verfehlt**: 5 von 8 X-Class-Zeilen erreichen die Schwelle nie, darunter die Presets 10″, 12″ und 13″; das Preset 13″ H kommt nach 260 s knapp darüber (erst ab 99 % Dauergas). Zuerst erreicht sie ein getauschter 3,5″-Toothpick (1404 2750 KV, 57 % Dauergas). Cinelifter: weder der Kern noch JMP haben einen Rahmen dieser Rolle; der nächste Ersatz (Cinewhoop 3,5″ mit 1,5-facher Last) bleibt bei 47 °C |
| B4 Whoops werden bei Dauervollgas heiß | Beharrung Vollgas am Boden ≥ 55 °C | **verfehlt** bei allen 7: 65 mm 30–32 °C (also 5–7 K über der Umgebung), 75/85 mm 38–53 °C |
| Regel 2a: 5″ drosselt mit normalem Akku nicht | aggressiver Akku und Vollgas bis leer unter der Schwelle | **erfüllt**: 74–96 °C |
| Regel 2b: gedrosselt wird nur, wo auch ein echter ESC abregeln würde | wer bei Dauervollgas die Schwelle erreicht, hat einen ESC über 140 °C | **verfehlt** bei allen 9: z. B. eingebauter 5″ Race (Motor 143 °C, ESC 73 °C), X-Class 13″ H (147/72), 7″ 1050 KV (168/61) |

## 5. Reichen Kühlkonstanten?

Der Test sucht über die Konstanten des Kerns: Grundkühlung 0–0,3 W/K, drehzahlabhängige Kühlung 0–3·10⁻⁴ W/K je
rad/s, je in 60 Schritten, und Wärmekapazität 0,4/0,5/0,7/0,9 J/(g·K) (0,9 = reines Aluminium, mehr gibt es nicht).
Das Gesetz Kühlung ∝ Masse^(2/3) bleibt fest; es ist die Physik der Oberfläche, keine Stellschraube. Von 14 880 Sätzen
halten 10 806 die erfüllten Bänder B1, B2a, Regel 2a und die bestehende Zusage „kein eingebauter Bauplan drosselt mit
einem Akku Vollgas“ (`ThermalNeutralityTest`).

- **B4:** Der heißeste Whoop unter diesen Sätzen erreicht 41 °C (Grundkühlung 0,29 W/K, fast ohne Drehzahl-Anteil,
  0,9 J/(g·K)). 55 °C sind mit Kühlkonstanten nicht erreichbar. Grund: Der 65-mm-Whoop setzt bei Vollgas nur 1,0 W je
  Motor in Wärme um. Für 55 °C müsste seine Kühlung auf 18 % fallen, und bei gleichem Größengesetz würde der 5″ mitfallen
  und schon mit einem Akku drosseln. Zudem kühlt der drehzahlabhängige Anteil kleine, sehr schnell drehende Motoren
  besonders gut, obwohl ihre Glocke langsamer an der Luft entlangläuft als die eines 5″.
- **B2b:** Für den eingebauten 7″ wäre es mit Konstanten erreichbar (468 Sätze, z. B. ohne Grundkühlung und
  1,6·10⁻⁴ W/K je rad/s). Die JMP-7″ und alle JMP-X-Class bringen eigene Kühlwerte mit; dort wirken Konstanten des Kerns
  nicht (B3 ebenso).
- **Regel 2b:** ist keine Frage von Konstanten. Solange die Drosselung an der Motortemperatur hängt, drosselt das Spiel
  bei kaltem ESC.

Deshalb ändert Phase A keine Konstante. Ein Teilausgleich für B2b würde Kalibrierung und Werkbank-Werte jetzt ändern und
in Phase B noch einmal. `tools/calibration.sh` ist byte-gleich zu `integration` 61315d73, neu ist nur `thermal.txt`.

## 6. Entscheidung nach §3.H

- **Befund 1 (Zielband verfehlt, Konstanten bringen es nicht hinein):** B4. Whoops werden bei Dauervollgas nicht heiß
  (30–53 °C), mit keinem Satz Kühlkonstanten über 41 °C beim 65-mm-Whoop. B3 und B2b sind für die JMP-Teile ebenfalls
  verfehlt, deren Kühlwerte aus den Teile-Daten kommen.
- **Befund 2 (Drosselung, wo echte Hardware nicht abregelt):** Bei Dauervollgas am Boden drosseln 9 Baupläne, darunter
  der eingebaute 5″ Race nach 62 s, obwohl ihr ESC höchstens 117 °C erreicht. BLHeli_32 regelt erst ab 140 °C ab, AM32 und
  Bluejay ab Werk gar nicht. Umgekehrt erreicht kein ESC im Modell je 140 °C. Mit dem Modell von heute käme also auch
  eine Drosselung nur im ESC (Phase B) nie zum Zug.
- **Befund 3** ist damit nicht nötig; die Texte sind unabhängig davon in Phase A neutral (Abschnitt 7).

**Phase B nötig.** Hinweise für den Umbau, die aus den Zahlen folgen:

1. Drosselung nur über den ESC wie im Design (§3.H, Phase B). Die Schwelle je Stack-Klasse folgt BLHeli_32: 140 °C,
   linear bis 25 % bei +15 K. Gleichzeitig muss die ESC-Erwärmung so kalibriert werden, dass 7″, X-Class und
   Cinelifter unter Dauerlast dort ankommen, 5″ mit normalem Akku aber nicht. Heute bleibt jeder ESC unter 117 °C.
2. Für B4 reicht keine Konstante. Die Motor-Kühlung sollte an der Luftgeschwindigkeit an der Glocke hängen
   (ω · Glockenradius) statt an ω allein, die Verluste kleiner Motoren sind zu prüfen. Dateien: `sim/Thermal`,
   `sim/ParamDerivation#thermal` (gehören laut §10.1 zu Phase B).
3. Fahrtwind-Kühlung: Werkbank (`EnergyModel.AIRFLOW_COOLING`) und Flugmodell angleichen.
4. Umgebung: Werkbank 25 °C, Flugmodell 20 °C; einheitlich oder bewusst getrennt dokumentieren.
5. JMP: Kühlwerte der X-Class- und 7″-Motoren mit den neuen Bändern abgleichen (eigener JMP-Strang).
6. `ThermalCharacterizationTest` hält die heutigen Urteile fest (B2b, B3, B4, Regel 2b verfehlt). Phase B ändert die
   erwarteten Urteile mit.

## 7. Texte (Phase A)

Texte, die behaupteten, der Motor regle selbst ab, sind jetzt neutral formuliert. Das stimmt im heutigen Modell und in
Phase B:

| Schlüssel | Englisch | Deutsch |
|---|---|---|
| `propwash.drone.motor_derate` | Motors very hot – they lose power and wear out | Motoren sehr heiß – sie verlieren Leistung und verschleißen |
| `propwash.content.workbench.warning.motor_thermal` | Sustained full throttle in place: motors very hot, lose power and wear out | Dauervollgas im Stand: Motoren sehr heiß, verlieren Leistung und verschleißen |
| `propwash.parts.tooltip.thermal_limits` | loses power from %s °C, at most %s °C | Leistung sinkt ab %s °C, höchstens %s °C |
| `gamerule.propwash.hardware_limits.description` | On: very hot motors lose power and wear out, … Off: hot motors keep their full power and do not wear, no burnt ESC. | An: Sehr heiße Motoren verlieren Leistung und nutzen sich ab, … Aus: Heiße Motoren behalten ihre volle Leistung und nutzen sich nicht ab, kein durchgebrannter ESC. |

Die OSD-Warnung „MOTOR OVERHEAT“/„MOTOR ÜBERHITZT“ bleibt. Modell, Ereignisse, Warnungen, Goldens und Kalibrierung sind
unverändert. Noch mit dem alten Wortlaut, außerhalb dieses Strangs: `docs/modrinth/description.md` („motors throttle
back“) und die Website.

## 8. Phase B: Umbau

### 8.1 Modell

`sim.Thermal` rechnet weiter je Motor eine Motor- und eine ESC-Temperatur (Ein-Körper-Modell, exakte Lösung je Schritt).
Neu sind Verluste, Kühlung und die Folgen:

| Größe | Motor | ESC |
|---|---|---|
| Verlust | `I² · R_Motor + kt · I0 · ω + 0,35 · V_Stator · ω²` (der letzte Teil sind drehzahlabhängige Zusatzverluste: Wirbelströme, Lager, Luftreibung; `V_Stator` aus Durchmesser und Höhe des Stators) | `I² · R_ESC + 0,002 · U_Bus · I` (Leitungs- und Schaltverluste) |
| Kühlung | `(0,1 W/K + 0,0105 W/K je m/s · ω · r_Glocke) · (m/33 g)^(2/3)`, `r_Glocke = 0,625 · Statordurchmesser`; Teile-JSON mit `cooling_w_per_k`/`cooling_w_per_k_per_rad_s` wie bisher | `0,0275 W/K · (Platine/36 mm)² · (1 + 0,2 · ω/ω_Schweben)`; Platine nach Halterung: 30×30 → 36 mm, 20×20 → 26 mm, Whoop-AIO → 25,5 mm, sonst aus der Masse; Rahmen mit Duct × 0,6 |
| Fahrtwind | beide Kühlungen × `1 + 0,6 · √(v/10 m/s)` mit der Luftgeschwindigkeit, im Flugmodell und in der Werkbank gleich | |
| Wärmekapazität | `0,5 J/(g·K) · Motormasse` oder `heat_capacity_j_per_k` | `0,5 J/(g·K) · Stack/4` |

- **Drosselung nur im ESC:** über 140 °C sinkt die Leistungsgrenze linear bis 25 % bei 155 °C (Kennlinie BLHeli_32, siehe
  Abschnitt 2). Ereignis `ESC_DERATE` (1 = Beginn über 140 °C, 0 = Ende unter 130 °C), Meldung „ESC überhitzt – er regelt
  zum Selbstschutz ab“, OSD „ESC OVERHEAT“/„ESC ÜBERHITZT“. Eine Schwelle für alle Stacks: das Teile-Modell kennt keine
  ESC-Firmware, und BLHeli_32 nutzt ab Werk dieselbe Schwelle für jede Größe. ESCs ohne Abregelung (AM32, Bluejay ab
  Werk) gibt es deshalb nicht.
- **Heißer Motor:** über `derate_start_c` keine Leistungsgrenze mehr. Das Drehmoment je Ampere sinkt linear bis −10 % bei
  `max_temp_c` (`PowerSystem#torqueScale`), Verschleiß wie bisher, über `max_temp_c` das Ereignis `MOTOR_OVERHEAT`.
  Ereignis `MOTOR_HOT` (statt `MOTOR_DERATE`) mit 10 K Hysterese, Meldung „Motoren sehr heiß – sie verlieren Leistung
  und verschleißen“, OSD „MOTOR OVERHEAT“ ohne Grenze.
- **Welt-Regel `propwash:hardware_limits` aus:** keine Abregelung, kein Drehmomentverlust, kein Verschleiß.
- **Umgebung einheitlich 20 °C:** das Flugmodell (`PilotSession`, Heimflug, `SimEnvironment`) rechnete schon immer mit
  20 °C, nur die Werkbank mit 25 °C. 20 °C ist die Bezugstemperatur nach ISO 1 und ein gewöhnlicher Flugtag; die
  Werkbank zeigt damit dieselben Temperaturen, die man im Flug erlebt. Geändert ist nur `BuildAnalysis.REFERENCE_AMBIENT_C`.
- **Werkbank-Warnung `motor_thermal`:** erscheint, wenn bei Dauervollgas im Stand bis zum leeren Akku der Motor seine
  Schwelle oder der ESC 140 °C erreicht. Text: „Dauervollgas im Stand überhitzt: heiße Motoren verschleißen, ein heißer
  ESC regelt ab“.
- **Teile-JSON:** `derate_start_c` heißt jetzt „ab hier verliert der Motor Leistung“, `max_temp_c` „ab hier starker
  Verschleiß“. Namen, Werte und API bleiben gleich, keine neuen Felder.

### 8.2 Warum diese Kühlung

- **Glocke statt ω allein (Befund 1):** die Luft streicht mit `ω · r` an der Glocke entlang. Ein 0802-Whoop-Motor dreht
  zwar schnell, seine Glocke ist aber klein; mit ω allein kühlte das Modell ihn wie keinen anderen Motor. Dazu kommen die
  drehzahlabhängigen Zusatzverluste, die bei kleinen, sehr schnell drehenden Motoren groß sind (echte 0802 haben einen
  Leerlaufstrom um 0,3–0,4 A bei 1S, das Modell bisher 0,1 A). Erst beides zusammen bringt Whoops auf 61–98 °C.
- **ESC nach Platinengröße:** die Wärme verlässt den ESC über die Platine; ein schwerer 100-A-Stack auf 30×30 kühlt nicht
  besser als ein leichter auf 30×30, er hat nur kleinere Widerstände. Der Propellerstrahl erreicht den Stack in der
  Rahmenmitte kaum (Faktor 1 + 0,2 · ω/ω_Schweben statt 1 + ω/ω_Schweben), Ducts schirmen ihn zusätzlich ab („enge Rahmen
  ohne Luftstrom“).
- **Schaltverluste:** steigen mit der Spannung; 8S-Kopter (X-Class, 7″ mit 8S) heizen ihren ESC dadurch stärker.

### 8.3 Messung vorher/nachher (Dauervollgas am Boden)

Beharrung Motor/ESC in °C, Drosselbeginn im Prüfstand des Flugmodells (600 s, Akku unendlich). Vorher: Phase A,
Umgebung 25 °C, Drosselung nach Motortemperatur. Nachher: Phase B, Umgebung 20 °C, Abregelung nur im ESC. ESC-Werte über
250 °C sind abgeschnitten; im Spiel regelt der ESC vorher ab.

| Klasse | Bauplan | Vorher Motor/ESC | Vorher Drosselung | Nachher Motor/ESC | Nachher Abregelung (ESC) |
|---|---|---|---|---|---|
| Whoop 65 mm | eingebaut | 30/36 | – | 61/31 | – |
| Whoop 75 mm | JMP | 53/79 | – | 98/63 | – |
| Whoop 85 mm | JMP | 51/68 | – | 95/74 | – |
| Cinewhoop 3″ | eingebaut | 123/106 | – | 67/209 | nach 72 s (bis 76 %) |
| Cinewhoop 3,5″ | JMP | 94/53 | – | 93/152 | nach 257 s |
| 5″ Freestyle | eingebaut | 124/67 | – | 136/118 | – |
| 5″ Race | eingebaut | 143/73 | nach 60 s (Motor) | 151/133 | nach 165 s (bis 96 %) |
| 5″ Race 6S | JMP | 141/83 | nach 62 s (Motor) | 150/175 | nach 77 s |
| 7″ Long Range, Li-Ion | eingebaut | 113/52 | – | 101/78 | – |
| 7″ Long Range, LiPo 6S 1300 | eingebaute Teile | – | – | 137/129 | – (Motor über der Schwelle nach 97 s) |
| 7″ 8S | JMP | 113/68 | – | 117/169 | nach 112 s |
| 8″ Li-Ion | JMP | 106/60 | – | 105/116 | – |
| X-Class 10″ | JMP | 120/83 | – | 123/250 | nach 67 s (bis 61 %) |
| X-Class 12″ | JMP | 106/47 | – | 109/150 | nach 351 s |
| X-Class 13″ | JMP | 130/62 | – | 133/218 | nach 126 s |
| X-Class 13″ H | JMP | 147/72 | nach 259 s (Motor) | 148/250 | nach 94 s (bis 69 %) |

Cinewhoop 3″ mit dem Kühlwert aus dem Datenblatt (Abschnitt 8.7); bis dahin 190/209 °C und Abregelung nach 63 s bis 72 %.
Mit einem normalen Akku: 5″ nach aggressivem Akku 52–53 °C (vorher 73–76 °C ohne Fahrtwind im Flugmodell), 7″ im
Reiseflug 37–45 °C, 5″ Schweben 54–60 °C, Whoop 65 mm nach aggressivem Akku 44 °C.

### 8.4 Zielbänder in Phase B

| Band | Prüfung in Phase B | Ergebnis |
|---|---|---|
| B1 | 5″ Freestyle nach aggressivem Akku (mit Fahrtwind) 45 °C bis Schwelle − 20 K | erfüllt, 52–53 °C |
| B2a | 7″ im Reiseflug (50 % Gas, mit Fahrtwind) ≥ 40 K unter der Schwelle | erfüllt, 37–45 °C |
| B2b | 7″ mit LiPo bei Dauervollgas am Boden oder 1,5-facher Last an der Hitzegrenze (Motor über der Schwelle oder ESC regelt ab) | erfüllt, 117–169 °C |
| B3 | X-Class- und Cinelifter-Presets erreichen bei Dauervollgas am Boden die Hitzegrenze | erfüllt (alle vier X-Class-Presets, drei davon über den ESC) |
| B4 | Whoops bei Dauervollgas ≥ 55 °C | erfüllt, 61–98 °C |
| R2a | 5″ mit einem normalen Akku (aggressiv und Vollgas bis leer) ohne Hitzegrenze | erfüllt, Motor 76–101 °C, ESC ≤ 89 °C |
| R2b | nur ein ESC über 140 °C begrenzt die Leistung, ein heißer Motor allein nie | erfüllt |

Abweichungen von der Prüfung in Phase A, mit Grund:

- **Hitzegrenze statt Motorschwelle (B2b, B3):** in Phase B drosselt nur der ESC. „Kämpft mit Hitze“ heißt deshalb: der
  Motor erreicht seine Schwelle (Leistungsverlust, Verschleiß) oder der ESC regelt ab.
- **B2b nur mit LiPo:** ein 6S-Li-Ion-Akku bricht bei Vollgas auf etwa 2,6 V je Zelle ein (eingebauter 7″: 15,6 V statt
  25 V); Motor und ESC bekommen so wenig Strom, dass sie kühl bleiben. Das ist auch in echt so. Geprüft wird B2b deshalb
  an 7″ mit LiPo, dazu eine Zeile mit eingebauten Teilen (Long-Range-7″ mit 6S-1300-LiPo).
- **B3 ohne „als erste“ und nur Presets:** überlastete Tausch-Kombinationen und kleine AIO-ESCs in Rahmen mit Duct
  erreichen ihre Hitzegrenze mit weniger Dauergas als X-Class. Beim eingebauten Cinewhoop ist es seit Abschnitt 8.7 nur
  noch der ESC (73 % Dauergas, unendlicher Akku); Ducts mindern im Modell nur die Kühlung des ESC, nicht die des Motors. Die
  X-Class 12″ (28 A je Motor an einem 100-A-ESC) erreicht sie erst bei 95 % Dauergas. Getauschte Motoren mit wenig KV
  (3115 640 KV, 4214 330 KV) bleiben kühl, wie in echt: genau dafür wählt man wenig KV.
- **R2b:** prüft, dass die Grenze unter 140 °C am ESC immer 1,0 ist. Ein heißer Motor zieht mit weniger Drehmoment
  etwas mehr Strom; deshalb regelt der ESC im Prüfstand bei 5″ Race und zwei JMP-5″ nach drei bis fünf Minuten
  Dauervollgas leicht ab, obwohl die einfache Rechnung den ESC knapp unter 140 °C sieht.

### 8.5 Kalibrierung und Goldens

- `tools/calibration.sh`: `fall.txt`, `throttle_drop.txt`, `battery_sag.txt` byte-gleich. `calibration.txt`: nur die
  Spalten T60, TFlug, TDauer (Motortemperaturen der Werkbank) ändern sich, alle übrigen Spalten byte-gleich.
  `motion.txt`: Spezifikations-Hashes byte-gleich; die Flug-Hashes ändern sich, weil die Telemetrie Motor- und
  ESC-Temperatur mitschreibt. Mit ausgeblendeten Temperaturen sind 1219 von 1254 Zeilen byte-gleich; die 35 übrigen sind
  JMP-Tausch-Kombinationen, deren Motoren schon in 0.5.2 im Flug über die Schwelle kamen und gedrosselt wurden (jetzt:
  Drehmomentverlust statt Grenze). `thermal.txt` ist neu aufgebaut (Abschnitt 8.3).
- `builtin_regression.txt`: 84 Flüge neu (Temperaturen in der Telemetrie). Mit ausgeblendeten Temperaturen byte-gleich
  bis auf `full:cinewhoop_3` und die sechs `full-thermal:`-Läufe (150 s Vollgas im Stand). Spalte für x86_64 aus dem
  Rosetta-Lauf, wieder 68 Zeilen.
- `airframe_golden.txt` (Stand Phase B, seit Abschnitt 8.7 wieder byte-gleich zum Stand davor): nur die vier
  `analysis:`-Zeilen des Cinewhoop-Presets (Warnung `MOTOR_THERMAL`); ohne diese
  Warnung byte-gleich. Der eingebaute Cinewhoop erreicht bei Dauervollgas im Stand nach einem Akku 147 °C am Motor; die
  übrigen Presets bleiben ohne Hitzewarnung.

### 8.6 Offen

- Just More Parts: die eigenen Kühlwerte der 7″- und X-Class-Motoren stammen aus Phase A; mit ihnen erfüllen die JMP-Presets
  die Bänder, die X-Class 12″ aber nur knapp über den ESC (150 °C). Abgleich im JMP-Strang.
- Website, `gamerules.json` und `docs/modrinth/description.md` übernehmen die neuen Texte im jeweiligen Strang.
- `docs/api/changes.md` (0.6.0) nennt die neue Bedeutung von `derate_start_c` und `max_temp_c` (Text aus Abschnitt 8.7,
  „Texte“); die Seite `parts-format` der Website ebenso.

### 8.7 Nachtrag nach der Prüfung V12 (20.10.)

**Cinewhoop zu heiß.** Der eingebaute 1404 4600 KV kam bei Dauervollgas im Stand nach einem Akku auf 147 °C (ΔT 127 K,
Beharrung 190 °C), das Preset warnte. Der Herstellerprüfstand misst nach 60 s Vollgas 25–44 K (T-Motor F1404,
`motors-props.md` §6.1); der F1404 4600 KV mit GF3016 zieht 17,54 A bei 15,64 V (274 W) und hat dann 50 °C bei 8 °C
Umgebung (ΔT 42 K), mit HQ 3×3×3 bei 316 W 45 °C [9]. Das Spiel rechnet fast am selben Arbeitspunkt: 14,6 A je Motor bei
13,0 V (190 W), Verluste 41,5 W (Kupfer 29,7 W, Leerlauf 5,9 W, Zusatzverluste 5,9 W). Widerstand (0,14 Ω) und
Leerlaufstrom (0,54 A) stimmen mit dem Datenblatt (138 mΩ, 0,6 A) überein; zu klein war die Kühlung. Die Größenregel
`(m/33 g)^(2/3)` gab dem 9-g-Motor 0,24 W/K bei Vollgas, das Datenblatt verlangt rund 0,9 W/K. Ducts und enge Rahmen
sind nicht der Grund: im Modell mindern sie nur die Kühlung des ESC.

Die Größenregel bleibt, weil Whoop, 5″ und 7″ ihre Datenblätter treffen. Der 1404 bekommt wie die 1404-Motoren von Just
More Parts einen eigenen Kühlwert im Teile-Datum: `"thermal": {"cooling_w_per_k_per_rad_s": 0.00016}` (statt
3,9·10⁻⁵ aus der Größenregel; Grundkühlung weiter 0,1 W/K · (m/33 g)^(2/3)). Danach:

| Preset | Werkbank T60 | ΔT | Datenblatt nach 60 s |
|---|---|---|---|
| Whoop 65 mm | 55 °C | 35 K | F1203/F1204 31–46 K |
| Cinewhoop 3″ | 61 °C (vorher 147 °C) | 41 K | F1404 25–44 K |
| 5″ Freestyle | 100 °C | 80 K | V2207 V2 73–82 K |
| 5″ Race | 101 °C | 81 K | V2306 V2 64–83 K |
| 7″ mit 6S-1300-LiPo | 91 °C | 71 K | F90 65–77 K |
| 7″ (Preset, Li-Ion) | 78 °C | 58 K | F90 65–77 K; der Li-Ion-Akku bricht auf 15,6 V ein |

Cinewhoop sonst: Beharrung Motor 67 °C, Schweben 34 °C, nach aggressivem Akku 35 °C, gemischter Flug 31 °C. Der ESC
bleibt, wie er war: der 20-A-AIO im Duct kommt mit unendlichem Akku auf 209 °C Beharrung und regelt nach 72 s ab (bis
76 %), mit dem 650-mAh-Akku erreicht er 88 °C. Kein Preset warnt mehr vor Hitze. JUnit:
`ThermalCharacterizationTest#workbenchMinuteMatchesTheDatasheets` und `#noPresetWarnsOfHeat`.

Kalibrierung mit den Daten von Just More Parts 1.1.1: `fall.txt`, `throttle_drop.txt`, `battery_sag.txt` byte-gleich; in
`calibration.txt` ändern sich nur T60, TFlug und TDauer des Cinewhoops (147/56/147 → 61/31/61 °C); in `thermal.txt` nur
die Zeile des Cinewhoops; in `motion.txt` die 43 Zeilen mit `propwash:m1404_4600` (Temperaturen in der Telemetrie, mit
ausgeblendeten Temperaturen byte-gleich bis auf zwei JMP-Tausch-Kombinationen mit dem 1404 an 6S, deren Motor vorher
über die Schwelle kam). Goldens: `airframe_golden.txt` byte-gleich zum Stand vor Phase B, `builtin_regression.txt` 15
Cinewhoop-Flüge (mit ausgeblendeten Temperaturen byte-gleich bis auf `full:` und `full-thermal:`),
`builtin_definitions.txt` die Zeile des 1404.

**Größere Kopter.** Mit eingebauten Teilen bleibt das 7″-Preset kühl (Beharrung 101/78 °C), weil sein Li-Ion-Akku unter
Vollgas einbricht; das ist auch in echt so. Mit 6S-1300-LiPo trifft der 7″ das Datenblatt des F90 und kommt bei
Dauervollgas über seine Schwelle (Beharrung 137 °C nach 97 s), der ESC des 30×30-Stacks bleibt unter 140 °C. Das Modell
bleibt deshalb unverändert. Hitze im Spiel zeigen diese Aufbauten (Vollgas im Stand, 20 °C):

| Aufbau | Werkbank | Prüfstand im Flugmodell (unendlicher Akku) |
|---|---|---|
| 7″ mit 20-A-AIO und 6S-1300-LiPo, sonst wie das Preset (nur eingebaute Teile) | Warnung, ESC 175 °C bei leerem Akku | ESC regelt nach 25 s ab, bis 61 % |
| 7″ mit 2306 1860 KV, 7″-Prop und 6S-1300-LiPo (eingebaut, überlastet) | Warnung, Motor 147 °C nach einem Akku | Motor über der Schwelle nach 18 s, ESC regelt nach 30 s ab, bis 54 % |
| 7″ mit 20-A-AIO, Preset-Akku Li-Ion | ESC 138 °C bei leerem Akku | ESC regelt nach 56 s ab, bis 68 % |
| JMP 7″ 8S (`lr7_8s`) | Akku vorher leer (98/89 °C) | ESC regelt nach 112 s ab, bis 87 % |
| JMP X-Class 10″ (`xclass10_6s`) | ESC 140 °C bei leerem Akku | ESC regelt nach 67 s ab, bis 61 % |
| JMP X-Class 13″ H (`xclass13h_8s`) | Akku vorher leer (112/127 °C) | ESC regelt nach 94 s ab, bis 69 % |

Das Bildschirmfoto `motor_heat_workbench` zeigt den ersten Aufbau mit der ganzen Liste der Hinweise im Tooltip über
„+1 weitere…“ (die Hitzewarnung steht dort hinter „ESCs zu schwach für die Motoren“). JUnit:
`ThermalCharacterizationTest#sevenInchReachesItsHeatLimitWithBuiltinParts`.

**Texte.** `derate_start_c` (`MotorThermal#derateStartC`) heißt seit Phase B „ab hier verliert der Motor Leistung und
verschleißt“ (Drehmoment bis −10 % bei `max_temp_c`), `max_temp_c` „ab hier starker Verschleiß“; abgeregelt wird nur im
ESC ab 140 °C. So stehen sie jetzt im Add-on-Handbuch (`docs/api/addon-guide.md` §3.1), in `docs/design/parts-api.md`
(Tabelle der Motorfelder, §9.4) und in der Fehlermeldung des Records.

### 8.8 Nachtrag nach der Abdeckung cov-flight (20.10.): heißer Motor, kühler ESC

Prüfplan 1.6 „Heißer Motor, kühler ESC“: 7″ mit 6S-1300-LiPo, Schwerkraft etwa 90 % des S/G aus der Werkbank, rund zwei
Minuten fast Vollgas schweben. Das S/G der Werkbank ist 11,67, 90 % davon wären 1050 %; die Welt-Regel endet bei 1000 %.
Bei 1000 % hebt der 7″ vom Boden ab und schwebt mit 95–97 % Gas (der LiPo bricht auf rund 19,7 V ein). Der Motor kommt
nach 126 s über 130 °C (`MOTOR_HOT`, höchstens 132 °C in 150 s, Drehmoment mindestens 0,99), der ESC kommt auf höchstens
119 °C und regelt nicht ab, die Drohne hält die Höhe. Das Modell bleibt deshalb unverändert.

Die Abdeckung hatte die Drohne in 60 m Höhe mit Motoren im Leerlauf gestartet. Schon bei 957 % fällt sie dabei, bevor
die 7″-Props hochgedreht haben, und bei Vollgas fehlt dem Regler die Reserve für die Lage: so galten 945 % (91 % Gas)
als höchste schwebbare Schwerkraft. Bei 91 % Dauergas bleibt der Motor knapp unter der Schwelle (Beharrung 127 °C); über
130 °C kommt er erst ab etwa 93 % Dauergas (Beharrung nach Gas am Boden: 90 % 127 °C, 94 % 131 °C, 100 % 137 °C). JUnit:
`ThermalCharacterizationTest#sevenInchLipoGetsHotHoveringAtTheGravityLimit` (Start vom Boden wie ein Spieler, 150 s); die
Abdeckung `CovThermalScenarioTest#longRangeLipoHoverMotorHotEscCool` startet seitdem ebenso vom Boden.

## Quellen

1. BLHeli_32 Handbuch Rev32.x, Abschnitt „Temperature Protection“: <https://github.com/bitdump/BLHeli/blob/master/BLHeli_32%20ARM/BLHeli_32%20manual%20ARM%20Rev32.x.pdf>
2. Voreinstellung 140 °C in BLHeliSuite32 („the default of 140“): <https://discuss.ardupilot.org/t/does-anyone-build-big-quads-these-days-need-help-with-blheli-32-settings/39148>;
   Abregelung bis 25 % etwa 15 °C über der Schwelle: <https://discuss.ardupilot.org/t/drone-falls-of-from-the-sky-during-autotune/99500>
3. AM32: <https://github.com/am32-firmware/AM32/blob/main/Src/main.c> (Abschnitt `degrees_celsius > eepromBuffer.limits.temperature`),
   Standardwert 255 = aus: <https://github.com/am32-firmware/AM32/blob/main/Src/DroneCAN/DroneCAN.c>, <https://github.com/am32-firmware/am32-configurator/blob/master/src/eeprom.ts>
4. Bluejay: <https://github.com/bird-sanctuary/bluejay/blob/main/src/Settings/BluejaySettings.asm>,
   <https://github.com/bird-sanctuary/bluejay/blob/main/src/Modules/Scheduler.asm>, Release v0.21.0: <https://github.com/bird-sanctuary/bluejay/releases>
5. Betaflight OSD-Warnungen: <https://github.com/betaflight/betaflight/blob/master/src/main/osd/osd_warnings.c>
6. Neodym-Güten und Temperaturkoeffizienten: <https://www.stanfordmagnets.com/ndfeb-magnets-temperature-ratings.html>,
   <https://e-magnetsuk.com/introduction-to-neodymium-magnets/temperature-ratings/>
7. Erfahrungen zu 5″-Motortemperaturen: <https://intofpv.com/printthread.php?tid=21175>, <https://intofpv.com/printthread.php?tid=17172>
8. Whoop-Motoren: <https://intofpv.com/printthread.php?tid=18249>, <https://tinywhoop.com/products/copy-of-802-27000kv-tiny-whoop-danger-onesies-brushless-motors-bottle-rockets-1>
9. Ligpower F1404 (Prüfstand, Glocke nach 1 min Vollgas; 4600 KV mit GF3016: 15,64 V, 17,54 A, 274 W, 50 °C bei 8 °C;
   mit HQ 3×3×3: 316 W, 45 °C; 138 mΩ, 0,6 A Leerlauf bei 10 V, 9,34 g): <https://www.ligpower.com/product/f1404-fpv-motor.html>
