# Flugmodell — Flug-Charakterisierung der fünf Presets

Stand 2026-09-24 (Topspeed-Kalibrierung, vorher 2026-09-23 nach dem zweiten Review) · gemessen mit `FlightCharacterizationTest`
(`common/src/test/java/de/lardtom/propwash/sim/`), Messaufbau in `FlightCharacterization.java` · Bezug:
`flight-model-spec.md` (Kap. 13 und Implementation notes 29–44)

## Ergebnis in Kürze

- Alle fünf Presets liegen nach der Kalibrierung und den Review-Korrekturen in **allen** Zielbändern: 134 von 134
  Prüfungen grün, die gesamte Suite `:common:test` (100 Tests, inkl. aller 380 baubaren Werkbank-Kombinationen und der
  Review-Regressionstests) ist grün.
- Das zweite Review hat die Energiebilanz im ESC-Stromlimit korrigiert (Spec-Note 34): Motoren drehen etwas schneller
  hoch (Roll-Beschleunigung bei 50 % Gas +1 bis +8 %, Gier-t90 WHOOP 35 → 28 ms), alle stationären Werte, Sag,
  Punch und Flugzeiten bleiben gleich. Das Kontaktgedächtnis (Dauerkontakt) ändert nur die Zahl der CRASH-Events
  beim geflogenen FREESTYLE-Wandcrash. Die übrigen Korrekturen (Stoßzahl, NaN-Robustheit, Brownout-Event,
  Wasserwiderstand, Sag-Kompensation, Thrust-Linearisierung, HORIZON, PID-Ableitung) ändern keinen Messwert dieser
  Tabelle, weil die Messungen Standardwerte nutzen.
- Vorher lagen **WHOOP** (Schub/Gewicht 2,55, Schwebegas bei halbem Akku 62 %), **CINEWHOOP** (Schwebegas 28 %,
  Schub/Gewicht 8,0 — flog wie ein kleiner Freestyle-Quad) und **LONG_RANGE** (Topspeed 105 km/h) außerhalb.
  Außerdem gab es einen Flugfehler, der in keiner Zielliste stand: **Gas 0 → 100 % aus dem Leerlauf** löste beim
  LONG_RANGE bei jedem Ladestand einen Brownout (Disarm) aus, und die 5″-Presets sackten kurzzeitig auf 1,6–1,9 V
  pro Zelle.
- Behoben durch realistische Teile-Daten (7 Werte, Kap. 16 der Spec) und drei physikalisch begründete
  Modellkorrekturen (Nullschub-Geschwindigkeit, ESC-Eingangsstromgrenze, träger C-Raten-Aufschlag). Die PIDs sind
  **nicht** von Hand abgestimmt: `PidAutoTune` leitet sie weiterhin aus der Physik jedes Builds ab.
- Keine Schwingung und keine Instabilität bei Frame-dt 1–50 ms (auch mit zufälligem dt); die Ergebnisse sind bei
  gleichem Seed bitgleich.

## Reproduzieren

```
cd <repo>
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :common:test --tests 'de.lardtom.propwash.sim.FlightCharacterizationTest' --console=plain
```

Der Test druckt die Messtabelle und die Soll/Ist-Liste (Laufzeit ≈ 5 s) und schlägt fehl, sobald ein Wert sein
Zielband verlässt.

## Messverfahren

Alle Flüge laufen in `FlatWorld` (Ebene y = 0, optional eine Wand) mit der vollen `DroneSim`, 144 fps, Seed 42,
abgeleiteten PIDs und Standard-Rates (ACTUAL 70/670/0). Gyro-Rauschen und Prop-Wash sind aus, wenn nicht anders
angegeben.

| Kennwert | Verfahren |
|---|---|
| Schub, Schub/Gewicht | `BuildAnalysis`: alle Motoren Duty 1, soc 1,0, Standschub |
| Schwebegas | statisch (`OperatingPoints`) und geflogen: ANGLE, Höhenregler auf Gas, Mittel über 10 s, soc 1,0 und 0,5 |
| Topspeed ACRO | Vollgas, die Neigung regelt die Höhe (steigt die Drohne, kippt sie weiter): höchste haltbare Neigung, Mittel der letzten 3 s von 25 s, soc 0,8 |
| Topspeed ANGLE | Neigung min(60°, ACRO-Neigung − 1°), Höhenregler auf Gas, 20 s |
| Steigen | aus dem Schweben Gas 100 % (echter Akku, soc 1,0): v_y nach 1 s, Maximum in 5 s, Spitzen-g |
| Roll-Sprung | ACRO aus dem Schweben, Stick für 600 °/s: t90, Überschwingen, ±5 %; zusätzlich 0,3 s nach Gas 50 % |
| Gier | voller Gier-Stick (670 °/s): t90, erreichte Rate, Überschwingen, größte Beschleunigung |
| ANGLE-Sprung | 0 → 30° Roll: t90, Überschwingen, Zeit bis dauerhaft ±1° |
| Schwebestabilität | ANGLE, Sticks 0, **Standard-Tuning mit Gyro-Rauschen, Prop-Wash und Wake-Feld**, 10 s: Drift, Neigung, Drehrate, Höhe |
| Flugzeit | geflogen bis 80 % Kapazität: Schweben (Höhenregler) und 50 % Gas geradeaus (die Neigung hält die Höhe) |
| Sag | Zellspannung nach 1 s Vollgas, tiefste Zellspannung, Spitzenstrom; Punch aus Gas 0 (0,4 s Gas 0, dann 100 %) bei soc 1,0 und 0,5 |
| Prop-Wash | ANGLE, 3 s senkrechtes Sinken mit geregelten 5 m/s (Prop-Wash und Wake an), dann Abfangen; Vergleich ohne Wash |
| Crash | Wand frontal mit 0,7 × und 1,3 × `frame.impactTolerance`; zusätzlich im ANGLE-Vorwärtsflug (45°) gegen eine Wand in 80 m, danach 20 s Auslauf |
| Frame-dt | Schweben (mit Rauschen) und Roll-Sprung bei 1, 4, 8,3, 16,7, 33,3, 50 ms und mit zufälligem dt 1–50 ms |
| Determinismus | Schwebestabilität zweimal mit gleichem Seed, Vergleich aller Messwerte bitweise |

## Messwerte nach der Kalibrierung

| Kennwert | WHOOP | CINEWHOOP | FREESTYLE | RACER | LONG_RANGE |
|---|---|---|---|---|---|
| Masse [g] | 28,1 | 286,7 | 525,2 | 430,2 | 846,2 |
| Max. Schub pro Motor [g] | 22 | 335 | 1397 | 1430 | 1227 |
| Schub/Gewicht | 3,18 | 4,67 | 10,64 | 13,30 | 5,80 |
| Schwebegas statisch soc 1,0 / 0,5 [%] | 47,8 / 55,0 | 32,2 / 36,4 | 20,6 / 23,4 | 16,7 / 19,2 | 22,8 / 27,7 |
| Schwebegas geflogen soc 1,0 / 0,5 [%] | 47,7 / 54,8 | 32,1 / 36,3 | 20,6 / 23,4 | 16,7 / 19,1 | 22,8 / 27,6 |
| Topspeed ACRO geflogen [km/h] (Neigung) | 37 (56,1°) | 62 (65,2°) | 141 (75,1°) | 168 (76,8°) | 109 (58,2°) |
| Topspeed ACRO stationär [km/h] | 36 | 62 | 140 | 168 | 108 |
| Topspeed ANGLE geflogen [km/h] (Neigung) | 37 (55,1°) | 62 (60,0°) | 108 (60,0°) | 120 (60,0°) | 109 (57,2°) |
| Steigen 0→100 %: v_y nach 1 s / max. [m/s] | 8,1 / 8,3 | 14,9 / 15,3 | 36,3 / 37,3 | 44,1 / 45,3 | 24,8 / 27,0 |
| Steigen: Spitzen-g | 2,74 | 4,19 | 9,29 | 11,23 | 5,44 |
| Roll 0→600 °/s: t90 [ms] 144 / 60 fps | 111 / 117 | 76 / 83 | 49 / 50 | 56 / 67 | 76 / 83 |
| Roll Überschwingen [%] 144 / 60 fps | 2,2 / 2,2 | 4,1 / 3,6 | 0,3 / 0,2 | 0,8 / 0,5 | 3,1 / 2,5 |
| Roll ±5 % erreicht [ms] | 132 | 83 | 49 | 56 | 83 |
| Roll 0→600 °/s bei 50 % Gas: t90 [ms] / Überschw. [%] | 111 / 1,8 | 76 / 3,6 | 35 / 0,2 | 35 / 0,3 | 69 / 1,6 |
| Roll bei 50 % Gas: max. Beschleunigung [°/s²] | 13719 | 16067 | 30617 | 33114 | 18987 |
| Gier voller Stick (670 °/s): t90 [ms] | 28 | 69 | 83 | 69 | 83 |
| Gier max. Rate [°/s] / Überschw. [%] | 677 / 1,0 | 679 / 1,3 | 686 / 2,4 | 683 / 1,9 | 677 / 1,1 |
| Gier max. Beschleunigung [°/s²] | 34414 | 14904 | 32516 | 34250 | 16013 |
| ANGLE 0→30°: t90 [ms] | 208 | 160 | 181 | 174 | 167 |
| ANGLE 30°: Überschwingen [°] / ±1° erreicht [ms] | −0,07 / 576 | 0,00 / 375 | 0,00 / 312 | 0,00 / 319 | 0,00 / 361 |
| Schweben ANGLE 10 s, Sticks 0: Drift [m] | 0,00 | 0,00 | 0,00 | 0,00 | 0,00 |
| Schweben: Neigung RMS / max [°] | 0,01 / 0,03 | 0,00 / 0,01 | 0,00 / 0,01 | 0,00 / 0,01 | 0,00 / 0,01 |
| Schweben: Drehrate RMS [°/s] / Höhe σ [cm] | 0,26 / 4,3 | 0,12 / 1,5 | 0,21 / 0,4 | 0,22 / 0,3 | 0,26 / 0,8 |
| Flugzeit Schweben geflogen / stationär [min] | 7,2 / 7,2 | 4,9 / 5,0 | 14,1 / 14,1 | 16,2 / 16,2 | 32,4 / 32,4 |
| Flugzeit 50 % Gas geradeaus [min] (Fahrt [km/h]) | – (–) | 2,5 (38) | 3,7 (88) | 3,0 (108) | 11,9 (75) |
| Zelle Ruhe / nach 1 s Vollgas [V] | 4,34 / 3,96 | 4,19 / 3,45 | 4,20 / 3,60 | 4,20 / 3,53 | 4,20 / 3,17 |
| Sag Vollgas max. [V/Zelle] / Spitzenstrom [A] | 0,73 / 16 | 0,92 / 80 | 1,07 / 176 | 1,16 / 176 | 2,22 / 176 |
| Punch aus Gas 0: min. Zelle soc 1,0 / 0,5 [V], Brownout | 3,61 / 3,12, nein | 3,30 / 2,91, nein | 3,13 / 2,73, nein | 3,04 / 2,64, nein | 1,97 / 1,39, nein |
| Prop-Wash 5 m/s senkrecht sinken: Spitze / Mittel | 1,00 / 0,85 | 1,00 / 0,94 | 1,00 / 0,97 | 1,00 / 0,99 | 1,00 / 0,99 |
| Prop-Wash: Drehrate RMS mit / ohne [°/s] | 17,5 / 0,0 | 9,5 / 0,0 | 21,8 / 0,0 | 21,5 / 0,0 | 20,3 / 0,0 |
| Prop-Wash: max. Neigung [°] / v_y-Fehler RMS [m/s] | 1,8 / 0,12 | 0,6 / 0,28 | 1,4 / 0,76 | 1,4 / 0,79 | 1,4 / 0,78 |
| Prop-Wash: Höhenverlust beim Abfangen [m] | 2,37 | 1,59 | 0,96 | 1,03 | 1,10 |
| Crash-Schwelle [m/s] | 14,0 | 11,0 | 8,0 | 7,0 | 8,0 |
| Wand mit 0,7 × Schwelle | HARD_IMPACT, armed | HARD_IMPACT, armed | HARD_IMPACT, armed | HARD_IMPACT, armed | HARD_IMPACT, armed |
| Wand mit 1,3 × Schwelle | CRASH, disarmed CRASH | CRASH, disarmed CRASH | CRASH, disarmed CRASH | CRASH, disarmed CRASH | CRASH, disarmed CRASH |
| Geflogen gegen Wand [km/h]: Ergebnis (CRASH-Events inkl. Bodenaufschlag) | 35: HARD_IMPACT (0x) | 50: CRASH (1x) | 79: CRASH (3x) | 84: CRASH (4x) | 86: CRASH (3x) |
| … danach disarmed (CRASH) / in Ruhe am Boden | nein / nein | ja / ja | ja / ja | ja / ja | ja / ja |
| Frame-dt 1–50 ms: Neigung RMS max [°] | 0,01 | 0,00 | 0,00 | 0,00 | 0,00 |
| Frame-dt 1–50 ms: Roll-Überschw. / Restwelligkeit max [%] | 2,2 / 2,8 | 4,3 / 0,3 | 0,4 / 0,4 | 0,8 / 0,4 | 3,2 / 0,7 |
| Determinismus (gleicher Seed, bitgleich) | ja | ja | ja | ja | ja |

Seit der Korrektur 45 der Spezifikation (Gyro-Vibration bei der Rotorfrequenz statt 180-Hz-Rauschen, 2026-09-24)
geändert: Schwebe-Drift (vorher 0,03 m), Neigung (vorher 0,02 / 0,04–0,05°), Drehrate (vorher 0,81 / 0,91 / 0,92 /
0,93 / 0,97 °/s) und Frame-dt-Neigung (vorher 0,02°). Alle übrigen Werte messen mit abgeschaltetem Gyro-Rauschen und
bleiben gleich.

Gegenüber dem Stand vor dem zweiten Review geändert, bis auf die letzte Zeile durch die korrigierte Energiebilanz im
ESC-Stromlimit:
Roll-Beschleunigung bei 50 % Gas (vorher 13603 / 14979 / 28323 / 30945 / 18521 °/s²), Gier-Beschleunigung (vorher
33301 / 14250 / 28883 / 30045 / 15513 °/s²), Gier-t90 WHOOP 35 → 28 ms und CINEWHOOP 76 → 69 ms, Roll-t90 CINEWHOOP
83 → 76 ms, ANGLE-t90 CINEWHOOP/FREESTYLE/RACER/LONG_RANGE je −7 ms, Roll-Überschwingen um bis zu 0,4 Punkte kleiner,
Spitzen-g beim Steigen +0,02 bis +0,06 g. Der geflogene FREESTYLE-Wandcrash zählt 4 statt 3 CRASH-Events (inkl.
Bodenaufschlägen), weil anhaltender Wandkontakt durch das Kontaktgedächtnis nicht mehr bei jedem Frame abprallt und
die Drohne danach anders zu Boden fällt; Ergebnis (CRASH, Disarm, Ruhe am Boden) unverändert. Sag, Spitzenstrom,
Punch, Schwebegas, Topspeeds und Flugzeiten sind unverändert.

## Soll/Ist der Hauptziele

Schwebegas gilt bei halbem Akku (soc 0,5), wie die Werkbank es anzeigt (`BuildAnalysis.hoverThrottlePercent`).
Topspeed = ACRO, geflogen.

| Preset | Kennwert | Ziel | vorher | jetzt | |
|---|---|---|---|---|---|
| WHOOP | Schub/Gewicht | 3–4 | 2,55 | 3,18 | ok |
| WHOOP | Schwebegas [%] | 45–60 | 62,1 | 54,8 | ok |
| WHOOP | Topspeed [km/h] | 30–40 | 37 | 37 | ok |
| CINEWHOOP | Schwebegas [%] | 35–45 | 27,6 | 36,3 | ok |
| CINEWHOOP | Topspeed [km/h] | 80–100 (seit 0.4.3; vorher 50–70) | 62 | 90 | ok |
| CINEWHOOP | Schub/Gewicht (Plausibilität) | 3,5–7 | 8,01 | 4,67 | ok |
| FREESTYLE | Schub/Gewicht | 8–12 | 10,64 | 10,64 | ok |
| FREESTYLE | Schwebegas [%] | 20–30 | 23,4 | 23,4 | ok |
| FREESTYLE | Topspeed [km/h] | 130–150 | 164 | 141 | ok |
| RACER | leichter / mehr Schub/Gewicht / weniger Schwebegas als FREESTYLE | ja | ja | 431 g · 13,1 · 19,0 % | ok |
| RACER | Topspeed [km/h] | 140–160 (bis 10.10.2026 mit dem 4,6″-Prop: 160–180) | 193 | 147 (4,6″-Prop: 168) | ok |
| RACER | schneller, mehr Punch, mehr Rollbeschleunigung (50 % Gas) als FREESTYLE | ja | – | 147 km/h · 41,1 m/s · +8 % | ok |
| LONG_RANGE | Schwebegas [%] | 25–35 | 28,1 | 27,6 | ok |
| LONG_RANGE | Topspeed [km/h] | 100–120 | 122 | 109 | ok |
| LONG_RANGE | Flugzeit Schweben [min] / 50 % Gas im Verhältnis zu FREESTYLE | ≥ 20 / ≥ 2× | 31,9 / – | 32,4 / 3,2× | ok |

Für **jedes** Preset zusätzlich geprüft (alle ok): Roll-t90 ≤ 150 ms und Überschwingen ≤ 8 % (144 und 60 fps);
Gier-t90 ≤ 150 ms, Überschwingen ≤ 10 %; ANGLE 30°: t90 ≤ 300 ms, Überschwingen ≤ 1°, ±1° nach ≤ 800 ms;
Schweben mit Rauschen und Wash: Drift ≤ 0,5 m in 10 s, Neigung RMS ≤ 0,5°, Drehrate RMS ≤ 3 °/s; Flugzeit geflogen
= stationär ±5 %; Zelle nach 1 s Vollgas ≥ Abschaltspannung; kein Brownout beim Punch aus Gas 0 (soc 1,0 und 0,5);
Prop-Wash beim Sinken: Spitze ≥ 0,7, Drehrate RMS ≥ 5 °/s, Neigung ≤ 15°; Wand mit 0,7 × Schwelle kein CRASH und
weiter armed, mit 1,3 × CRASH und Disarm; nach einem geflogenen Crash disarmed und in Ruhe am Boden; Frame-dt 1–50 ms:
Neigung RMS ≤ 0,5°, Roll-Überschwingen ≤ 10 %, Restwelligkeit ≤ 5 %; bitgleiche Wiederholung.

## Was geändert wurde

**1. Teile-Daten (`de.lardtom.propwash.build`)** — Keys und Struktur unverändert, nur Zahlen:

| Teil | Feld | alt → neu | Grund |
|---|---|---|---|
| `MotorType.M0802_22000` | `resistanceOhm` | 0,60 → 0,20 | reale 0802-Motoren 0,1–0,3 Ω; nötig für Schub/Gewicht ≥ 3 |
| `PropType.P31_4B` | `powerCoefficient` | 0,060 → 0,085 | kleine Props bei kleiner Reynoldszahl weniger effizient |
| `PropType.P3_3B` | `thrustCoefficient` / `powerCoefficient` | 0,105 / 0,058 → 0,090 / 0,085 | 3″-Dreiblatt mit Steigung = Durchmesser; ≈ 335 g pro Motor wie reale 1404/4S-Messungen (≈ 350 g) |
| `FrameType.WHOOP_65` | `massGrams` | 5,5 → 4,0 | reale 65-mm-Rahmen inkl. Canopy 3–4 g |
| `FrameType.CINEWHOOP_3` | `massGrams` | 95 → 150 | Ducts, Bumper, Kamerakäfig; reale 3″-Cinewhoops wiegen trocken 200–250 g |
| `BatteryType.B1S_300_HV` | `cellResistanceOhm` | 0,060 → 0,040 | aktuelle 300-mAh-HV-Zellen; sonst Brownout beim Punch mit halbem Akku |
| `BatteryType.B6S_4000_LION` | `cellResistanceOhm` | 0,018 → 0,012 | Hochstrom-21700-Zellen (P42A-Klasse) |

**2. Modell (`de.lardtom.propwash.sim`)**

- **Nullschub-Geschwindigkeit** `v_0 = (pitch + 0,20·D)·ω/2π` statt `1,15·pitch·ω/2π` (`ModelConstants.ZERO_LIFT_PITCH`).
  Der Zuschlag entspricht dem Nullauftriebswinkel gewölbter Blätter (≈ 5° bei 0,75 R). Für Props mit Steigung ≈
  Durchmesser ändert sich praktisch nichts, flache Props (7×3,5) bekommen realistische Fahrt: LONG_RANGE
  105 → 121 km/h.
- **ESC-Eingangsstromgrenze** `I_in = 0,8·escCurrentPerMotor` zusätzlich zur Phasenstromgrenze `1,3·escCurrentPerMotor`
  (wie die Strommessung auf der DC-Seite realer ESCs; neues Feld `AirframeSpec.escInputLimit`). Nach dem zweiten
  Review gilt sie für den wirklichen Eingangsstrom `d_eff·I = (R_m·I + K_e·ω)·I/V` (vorher fälschlich `I ≤ I_in/d`),
  und ein begrenzter Kanal zieht nur so viel Akkustrom, wie der Motor an Leistung aufnimmt. Sie greift im Schweben
  und bei stationärem Vollgas bei keinem Preset und kappt nur das Hochdrehen (5″: 275–279 → 176 A Akkustrom; ohne
  sie brownt der WHOOP beim Punch aus Gas 0 bei soc 0,5 aus).
- **C-Raten-Aufschlag über den Erwärmungsstrom** (`PT1` 2 s, `ModelConstants.C_RATE_TAU`, Snapshot-Feld
  `batteryHeatCurrent`) statt über den Strom des vorigen Substeps. Kurze Spitzen erhöhen den Innenwiderstand nicht
  mehr; der Li-Ion-Akku kippt beim Punch nicht mehr in die Mitkopplung aus Strom und Widerstand. Stationäre Werte
  bleiben gleich.
- `BuildAnalysis.ESC_CURRENT_LIMITED` prüft die wirksame Grenze bei Vollgas.

**3. PIDs** — keine Handabstimmung. `PidAutoTune` leitet aus den neuen Parametern ab (Spec Kap. 7), z. B. WHOOP
Roll 21/17/52/120, CINEWHOOP 60/71/95/120, FREESTYLE unverändert 42/49/31/120; Phasenreserve aller 160 schwebefähigen
Werkbank-Builds ≥ 62,5° (2 kHz).

**4. Tests** — neu: `FlightCharacterization` (Messaufbau) und `FlightCharacterizationTest` (Tabelle + 134 Zielprüfungen).
Angepasst: Referenztabellen in `DerivationTest`, `ClosedLoopTest`, `OpenLoopPhysicsTest`, `FacadeTest` (neue
Ableitungswerte), der Bus-Bisektionstest (wirksame Stromgrenze, Erwärmungsstrom), der Prüfstand-Vollgastest (12 s
Einschwingen wegen des Erwärmungsstroms), D18 `BATTERY_CRITICAL` bei LONG_RANGE-Dauervollgas ≤ 5 s statt ≤ 3 s (jetzt
4,4 s) und der Snapshot-Vergleich (neues Feld). Die Spec (Kap. 1, 4, 5.2, 7, 10.3, 12, 13, 14, 16, Notes 7/10/13/14,
29–33) ist nachgezogen.

## Knapp im Ziel, Grenzen, Beobachtungen

Nichts liegt außerhalb eines Zielbands. Knapp oder bewusst so:

- **Topspeeds (Kalibrierung 2026-09-24, Spec-Note 45/46):** die Zielbänder folgen realen Werten (6S-5″-Freestyle
  130–150 km/h, 5″-Racer 160–180, 7″-Long-Range 100–120, Cinewhoop 80–100 seit 0.4.3, 65-mm-Whoop 30–40). „vorher“
  in der Tabelle oben ist bei den Topspeeds der Stand vor dieser Kalibrierung. Mit frischem Akku (`infiniteBattery`,
  ohne `batterySag`) fliegen die Presets 39 / 94 / 147 / 175 / 113 km/h, im Vollgas-Sturzflug 44 / 107 / 153 / 182 / 121.
  Seit dem 11.10.2026 fliegt der RACER mit dem flachen 5,1×2,9×3-Prop: Zielband 140–160 km/h (Race-Quads real
  130–160 km/h, `realism/builds.md` §7), gemessen 147 km/h, mit frischem Akku 153, im Sturzflug 166; mit dem
  4,6″-Prop auf demselben Rahmen bleiben es 168 / 175 / 192 km/h (`realism/motors-props.md` §12).
- **CINEWHOOP**: Schwebegas 36,3 % (Untergrenze 35 %, bei vollem Akku 32 %). Topspeed seit 0.4.3 90 km/h statt 62 km/h:
  Der Rahmen `cinewhoop_3` hat jetzt eine realistische Draufsicht (0,020 statt 0,050 m², Stirnfläche 0,012 statt
  0,020 m²; `realism/calibration.md` §0.4) – der Luftwiderstand der Ducts begrenzte die Geschwindigkeit vorher auf
  „Cinewhoop 50–70 km/h“, real sind 80–100 km/h (`realism/builds.md` §0).
- **Flugzeiten Schweben** sind bei WHOOP (7,2 min) und den 5″-Presets (14–16 min) eher lang, dafür gab es kein Ziel.
  Mit 50 % Gas liegen die 5″-Presets realistisch bei 3–4 min, LONG_RANGE bei 12 min. Wer es kürzer will, hebt
  `P51_3B.powerCoefficient`; das verschiebt aber das Freestyle-Kalibrierziel.
- **RACER** braucht aus dem Schweben 56 ms bis 90 % der Rollrate, FREESTYLE 49 ms. Grund ist die niedrigere
  Schwebedrehzahl (Motor-τ 15 statt 12 ms). Bei 50 % Gas sind beide gleich schnell (35 ms), der RACER rollt dort mit
  8 % mehr Beschleunigung.
- **Punch aus Gas 0**: LONG_RANGE sackt für ≈ 0,1 s auf 1,4–2,0 V pro Zelle ab (Li-Ion bei 176 A), ohne Brownout.
  Das ist auch mit korrigierter Energiebilanz so: in der Mitte des Hochdrehens nehmen die Motoren wirklich so viel
  Leistung auf, dass die Eingangsgrenze greift. Der WHOOP brownt erst unter soc ≈ 0,2 aus (gewollt: leerer 1S-Akku
  plus Punch).
- **WHOOP** erreicht seine Crash-Schwelle (14 m/s) im Flug nie, Wandtreffer sind höchstens `HARD_IMPACT`. Das ist
  gewollt, Whoops überstehen so etwas.
- **Prop-Wash** ist beim senkrechten Sinken mit 5 m/s bei allen Presets voll ausgeprägt (Intensität 1,0, weil
  v_h = 5,7–7,4 m/s). ANGLE hält die Neigung unter 2°, das Schütteln liegt bei 10–22 °/s RMS, beim Abfangen gehen
  1–2,4 m Höhe verloren.
- Auch auf dem künstlichen Prüfstand (Vollgas-Sprung aus ω = 0 ohne FC) brownt seit der korrigierten Energiebilanz
  kein Preset mehr aus (soc 1,0 und 0,5; tiefste Zelle WHOOP 3,63 / 3,15 V, LONG_RANGE 1,98 / 1,41 V). Vorher zog der
  stehende Motor im Stromlimit rechnerisch `d·I_max` statt der wirklich aufgenommenen Leistung aus dem Akku.

## Zweites Review: Korrekturen ohne Einfluss auf die Messwerte

Alle mit Regressionstest, Details in den Spec-Notes 34–44:

| Befund | Korrektur | Nachweis |
|---|---|---|
| ESC im Stromlimit zog `d_cmd·I_max` aus dem Akku | `I_bat = Σ (R_m·I + K_e·ω)·I/V`, Eingangsgrenze auf `d_eff·I` | FREESTYLE-Sprung: 1. Substep 71,6 A = Motorleistung/V (vorher 176 A gegen 37 A), Energie Akku/Last 1,0000 bei allen Presets |
| Stoßzahl konnte > 1 werden | `e ≤ 0,95`, `surfaceRestitution ≤ 1` | Fall aus 1 m mit `restitutionScale 4`: Energie steigt nicht mehr, Drohne kommt zur Ruhe |
| Einmaliges NaN → `SIM_FAULT` in jedem Frame | `handleFault` verwirft allen abgeleiteten Zustand, NaN-feste Rückkopplungen | 120 Frames ohne Fehler nach dem Fehler, erneutes Armen und Schweben |
| NaN-Position unbemerkt | Wächter mit Position, `copyStateFrom` repariert, `afterMove` prüft je Achse | kein NaN in `dx/dy/dz`, Fehler gezählt |
| Dauerkontakt an Decke/Wand = Stoß in jedem Frame | Kontaktgedächtnis: nur Annäherung zu Frame-Beginn zählt | RACER unter der Decke, 3 s: 1 Ereignis bei 20, 60 und 144 fps (vorher 59 `HARD_IMPACT` bei 20 fps), kein Crash-Disarm |
| `BROWNOUT` alle 0,5 s bei leerem 1S-Akku | Event einmal pro Unterspannungsphase | 10 min disarmed: 1 Event (vorher 909) |
| `config()`/`tuning()` lieferten die internen Objekte | liefern Kopien | Bearbeiten der Kopie wirkt nicht, kein NaN im FC |
| Wasserwiderstand kehrte v um | semi-implizit | WHOOP, `dragScale 2`, −20 m/s ins Wasser: nie aufwärts |
| DERIVED-PIDs ignorierten `pidLoop`/`gyroLpfHz` | Schlüssel `derivedForFilters` | 500 Hz / 100 Hz: Roll-P 23 statt 42 |
| Sag-Kompensation klemmte alle Motoren auf 100 % | Ausgangsbereich wie Betaflight verkleinern | soc 0,3, Vollgas + Rolle: 0 gesättigte Frames, Stopp nach 21 ms wie ohne Kompensation |
| Thrust-Linearisierung verschob das Schwebegas | Gas-Kompensation vor dem Mixer | Schwebegas WHOOP 54,9 %, FREESTYLE 23,4 % bei TL 0 und 40, Airmode aktiv |
| HORIZON zog nach waagerecht | Ziel = Stick-Winkel (Betaflight 4.4) | Roll-Stick 0,1/0,2/0,3: HORIZON 9,0/23,8/59,2° gegen ANGLE 6,0/12,0/18,0° |
