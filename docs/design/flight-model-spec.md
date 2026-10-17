# Flugmodell & Flight Controller — verbindliche Implementierungsspezifikation

Paket `de.lardtom.propwash.sim` (Java 25, nur `java.*` und `org.joml.*`, keine Minecraft-Imports)
Tests `common/src/test/java/de/lardtom/propwash/sim/` (JUnit 5)
Stand 2026-09-23 · ersetzt als Umsetzungsgrundlage die drei Entwürfe
`flight-model-rigid-body.md` (im Folgenden **RB**), `flight-model-flight-controller.md` (**FC**) und
`flight-model-aero-feel.md` (**AF**). Die Entwürfe bleiben als Begründungs-Hintergrund liegen; bei Widerspruch gilt
dieses Dokument.

**Herkunft der Zahlen.** Alle Kennwerte in Kapitel 4, 7 und 13 wurden mit einem Python-Prototyp genau der hier
festgelegten Gleichungen aus den Enum-Werten in `de.lardtom.propwash.build` nachgerechnet (Skripte außerhalb des
Repos). Stationäre Werte (Schweben, Vollgas, Topspeed, Flugzeit) sind exakt reproduzierbar. Regelkreis-Werte
stammen aus einem einachsigen Modell mit denselben Motor-, Akku-, Filter- und PID-Gleichungen. Die 3D-Simulation
weicht davon durch Kopplungen ab, deshalb sind ihre Toleranzen weiter (Kapitel 13).

**Originalität.** Grundlage sind nur öffentliche Physik (Impulstheorie, DC-Motormodell, Starrkörpermechanik,
Impulsverfahren, Bodeneffektmodell nach Sanchez-Cuevas et al.) und die öffentlich dokumentierten Betaflight-Konzepte
(Rates-Formeln BETAFLIGHT/ACTUAL, Throttle-Kurve, PID-Struktur und -Skalen, Airmode/Mixer, TPA, I-Term-Relax,
Anti-Gravity). Kein Code, keine Konstanten und keine Strukturen aus anderen FPV-Mods.

**Werkbank.** Die Drohne wird an der Werkbank aus Teilen zusammengebaut (`ARCHITECTURE.md`, Paket `content`). Jede
dort mögliche Kombination ist ein `DroneBuild`, nicht nur die 5 Presets. Daraus folgt für die Sim:
(1) alle Parameter werden aus den Teilen abgeleitet, nichts ist auf Presets zugeschnitten;
(2) jeder baubare Build fliegt mit automatisch abgeleiteten PIDs stabil (Kapitel 7);
(3) die Werkbank bekommt mit `BuildAnalysis` dieselben Gleichungen als stationäre Vorschau (Kapitel 12).

**Code-Regeln des Projekts gelten** (`docs/CONVENTIONS.md`): kein Kommentar im Code (auch nicht in Tests), englische
Bezeichner, keine Wildcard-Imports. Alle Erklärungen stehen hier.

---

## Inhalt

0. Kurzfassung
1. Entscheidungen und Konfliktauflösung
2. Konventionen
3. Klassen und öffentliche API
4. Parameterableitung aus `DroneBuild`
5. Physik pro Substep
6. Flight Controller
7. Automatische PID-Ableitung
8. Zeitschritt, Determinismus, Performance
9. Kollisionen und Bodenkontakt
10. Telemetrie und Events
11. Konfigurationsobjekte
12. `BuildAnalysis` für die Werkbank
13. Kalibrierziele pro Preset
14. Testplan mit Abnahmekriterien
15. Umsetzungsreihenfolge
16. Offene Punkte, Datenempfehlungen, Erweiterungen
17. Beliebige Bauteile (Parts-API 0.4.0)

---

## 0. Kurzfassung

- **Takt:** Physik fest 2000 Hz (h = 0,5 ms), FC in jedem Substep (einstellbar 2k/1k/500 Hz). Frame-dt 0–50 ms wird
  über einen **Nanosekunden-Akkumulator** in Substeps zerlegt (max. 100), Sticks werden innerhalb des Frames linear
  interpoliert. Gleiche Gesamtzeit ergibt unabhängig von der Frame-Aufteilung denselben Zustand.
- **Starrkörper:** Position/Geschwindigkeit in Welt, Drehimpuls **L im Weltsystem**, Orientierung als Quaternion
  mit exakter Exponentialabbildung; diagonaler Trägheitstensor mit Schwerpunkthöhe aus einem einfachen Massenmodell.
- **Antrieb:** DC-Motormodell je Motor (K_t = 1/K_v, Wicklung + ESC-Widerstand, Leerlaufstrom, Rotorträgheit),
  implizite Drehzahlintegration, Akku ↔ 4 ESC **geschlossen gelöst** (sonst instabil), ESC-Stromgrenze, aktives
  Bremsen. Gieren entsteht aus dem Motor-Reaktionsmoment inkl. Beschleunigungs-Kick.
- **Aero:** Schub mit Einströmkorrektur (Steigen/Fahrt senkt, Sinken hebt), Duct-Gewinn mit Abklingen, Translational
  Lift, Rotor-H-Kraft, orientierungsabhängiger Rumpfwiderstand, Bodeneffekt pro Rotor, Prop-Wash (Wirbelring pro
  Rotor, Phase 2 zusätzlich Wake-Feld), Wind und Böen.
- **FC:** Betaflight-artig: Rates BETAFLIGHT/ACTUAL, Throttle-Kurve, PID mit D auf Messung, FF, I-Term-Relax,
  Anti-Windup, Anti-Gravity, TPA, Airmode-Mixer, Idle, Sag-Kompensation, ANGLE/HORIZON über den Kippvektor.
- **Autopilot-tauglich:** ANGLE ist linear in Stick → Winkel, hat Winkel-FF 100 % und verfolgt ±20°/0,5 Hz mit
  < 1° RMS. `StickMath` invertiert Rates, Winkel und Throttle; Telemetrie liefert den Schwebegas-Stick.
- **Default-PIDs** werden pro Build in geschlossener Form aus der Streckenphysik abgeleitet (FREESTYLE:
  Roll P42 I49 D31 F120).
- **Kollision:** Entity bewegt sich mit MC-Blockkollision (feste AABB), meldet Achsentreffer; die Sim antwortet mit
  Stoßimpuls am Hüllpunkt (Taumeln), Coulomb-Reibung, Crash-Klassifikation über `frame.impactTolerance`, Prop-Strike,
  Bodenruhe mit Aufricht-Moment und Schlafzustand.
- **Performance:** keine Allokation pro Frame, primitive Felder, JOML nur an der API-Grenze; ≈ 1,5 µs pro Substep.

---

## 1. Entscheidungen und Konfliktauflösung

Jede Zeile nennt, was die drei Entwürfe vorschlagen, was gilt und warum.

| # | Thema | RB | FC | AF | **Entscheidung** | Begründung |
|---|---|---|---|---|---|---|
| 1 | Physiktakt | 2 kHz | 2 kHz | 1 kHz | **2 kHz fest** | D-Term und 1-Sample-Latenz wie echter 2k-Loop; Kosten bei 50-ms-Frames < 0,2 ms |
| 2 | Frame-dt-Akku | double | double | long ns | **long ns (AF)** | bitgleiche Frame-Split-Invarianz |
| 3 | Rotation | Körper-Euler + Kreiselterm | Körper explizit | Drehimpuls L in Welt | **L in Welt (AF)** | exakte Drall-Erhaltung beim Taumeln, Stoßimpulse wirken direkt auf L |
| 4 | Trägheit | voller Tensor, Layout-Tabelle | diagonal, grob | diagonal + CoM-Höhe | **diagonal + CoM-Höhe (AF) mit Akkulage je Rahmen (RB)** | Deviationsmomente < 1 %, voller Tensor lohnt den Aufwand nicht |
| 5 | Rotorebene vs. CoM | Versatz | 0 | Versatz | **Versatz y_m** | H-Kraft erzeugt Nickmoment, Anti-Gravity wird physikalisch sinnvoll |
| 6 | Leerlaufstrom I_0 | 0,10 + 0,03·I_max | 0,025·I_max | 0,035·I_max | **0,03·I_max** | Mittelwert; RB überbewertet Kleinstmotoren |
| 7 | ESC-Widerstand | 3 mΩ + 0,15/I_esc | – | Tabelle | **3 mΩ + 0,15 Ω·A / I_esc (RB)** | gilt für jeden Stack |
| 8 | Stromgrenze | 1,3·I_esc | – | min(1,25·I_mot, 1,5·I_esc) | **Phase 1,3·escCurrentPerMotor, Eingang (DC-Seite, d_eff·I) 0,8·escCurrentPerMotor** | der ESC begrenzt; `motor.maxCurrent` ist eine thermische Angabe; die Eingangsgrenze (Kalibrierung 2026-09-23) verhindert Brownouts beim Hochdrehen aus dem Leerlauf |
| 9 | Whoop-Motor träge (τ ≈ 120 ms) | Datenkorrektur | Hinweis | τ-Klemme 40 ms | **τ-Klemme 40 ms über J_eff (AF)**, Datenkorrektur nur Empfehlung (Kap. 16) | Datenhoheit liegt bei `build`; Whoop muss fliegbar sein |
| 10 | Einströmung | J/J0 | 1 − v/(1,15·n·p) | 1 − v/(n·p), Sinkbonus | **v_0 = n·(pitch + 0,20·D) (Kalibrierung 2026-09-23, vorher 1,15·n·p), Sinkbonus und Q-Faktor (AF)** | eine Formel; der Zuschlag 0,20·D ist der Nullauftriebswinkel gewölbter FPV-Blätter (≈ 5° bei 0,75 R), er hebt flache Props (7×3,5) stärker als steile |
| 11 | Duct-Gewinn | spaltabhängig, fadet | 1,20 konst. | 1,20 konst. | **1,15, fadet über λ (RB-Idee)** | Ducts helfen nur bei wenig Fahrt |
| 12 | H-Kraft | C_H·ρ·D³·n·v | dito, 0,03 | κ·T·v/(ωR + 5) | **κ_H·T·v⊥/(ωR + 5), κ_H = 1,0 (AF-Form)** | proportional zum Schub, wie in der Rotortheorie |
| 13 | Translational Lift | über μ = v/(ωR) | – | über v/v_h | **über v/v_h (AF)** | wirkt im richtigen Geschwindigkeitsbereich (≈ 6 m/s) |
| 14 | Bodeneffekt | Cheeseman-Bennett | Sanchez-Cuevas je Rotor, ≤ 1,3 | Sanchez-Cuevas CoM, ≤ 1,4 | **Sanchez-Cuevas je Rotor, ≤ 1,3 (FC)** | Quad-Modell, Kippen wirkt pro Rotor |
| 15 | Prop-Wash VRS | Gauß je Rotor | Fenster je Rotor | Smoothstep am CoM | **Gauß je Rotor mit Schubschwelle (RB)** | glatt, rotorweise → Momentenstörungen entstehen von selbst |
| 16 | Wake-Gedächtnis | 100 Partikel | – | 40 Kapseln | **32 Kapseln (AF), Phase 2** | Umfang: v1 ohne, Schnittstelle vorbereitet |
| 17 | Wash-Stärke | Verlust 0,30, Rauschen 0,25 | 0,25 / 0,35 | 0,35/0,20 · 0,30/0,15 | **Verlust 0,30 offen / 0,20 Duct, Rauschen 0,30 / 0,15** | Mittelwerte, Ducts dämpfen |
| 18 | Rumpfflächen | Seite 1,3, oben aus k_top·wb² | 1,1 / 2,5 | 1,15 / 1,4 + Scheibe | **A_x = 1,15·A_z, A_y = 2,9·A_z offen / 2,5·A_z Duct** (Kalibrierung 2026-09-24, vorher 1,8 offen) | auf reale Topspeeds kalibriert; A_y ist die ganze Draufsicht inkl. Arme, Motorglocken, Propblätter und Action-Cam, der Schubverlust durch die Anströmung der Propscheiben steckt in Zeile 10 |
| 19 | Druckpunkt | – | – | 0,1·L hinter CoM | **entfällt** | Wirkung marginal |
| 20 | OCV-Kurve | normiert | normiert | absolut (ignoriert Enum) | **normiert zwischen `empty` und `full` (RB-Tabelle)** | Enum-Spannungen sind maßgeblich |
| 21 | Polarisation | 0,5·R0, 5 s | 0,6·R, 8 s | 0,5·R, 4 s | **0,5·R_pack, τ = 5 s**; seit 0.5.2 zusätzlich ein schneller Anteil: 0,35·R_pack bauen sich mit τ = 0,5 s auf (Entscheidung 49) | Mitte |
| 22 | Nebenverbraucher | Tabelle | 0,35 W/g + 0,5 | 1,0 + 0,3 W/g | **0,6 W + 0,3 W/g·m_video** | Whoop sonst unrealistisch belastet |
| 23 | Brownout | Stack-Spannung, 50 ms | E/N < 0,8·cutoff | V_bus < 3,0 V, 5 ms | **V_bus < 2,9 V (1S-Stacks) bzw. 5,5 V, 5 ms** | Stack-abhängig, praktisch nur 1S |
| 24 | PID-Skalen | BF | BF | BF + k_auth | **P/I/D in BF-Skalen; F physikalisch normiert** | F = 100 bedeutet auf jedem Werkbank-Build dasselbe |
| 25 | Default-PIDs | geschl. Formel | Frequenzgang-Suche | globaler Tune × k_auth | **geschlossene Formel pro Build (RB-Idee, Konstanten per Prototyp verifiziert), Quelle DERIVED/CUSTOM (FC)** | robust, deterministisch, billig; k_auth versteckt Physik; Margin-Suche nur im Test |
| 26 | ANGLE | Schwerkraftvektor | Kippvektor im Heading-Rahmen | Quaternion-Fehler | **Kippvektor (FC) mit Twist-Heading** | singularitätsfrei, linear invertierbar |
| 27 | angleStrength-Skala | /10 | /10 | ·0,2 | **K_a = angleStrength/10 [1/s]** | Mehrheit |
| 28 | Winkel-FF Default | 50 % | 100 % | 50 % | **100 %** | Autopilot: 0,5° statt > 4° RMS |
| 29 | Autopilot-Eingang | Sticks | Sticks | direkte Lagebefehle | **nur Sticks + `StickMath`** | ein Codepfad |
| 30 | RC-Glättung | Interp. + FF-PT1 | Interp. | Interp. + PT2 auto aus FPS | **Interp. + PT2 fest 40 Hz + FF-PT1 30 Hz** | feste Grenzfrequenz → FPS-unabhängiger |
| 31 | Rates-Default | ACTUAL 200/670/0,54 | 70/670/0 | 70/670/0 | **ACTUAL 70/670/0 alle Achsen** | BF-Standard |
| 32 | BF-Super-Rate | – | abs(x) vor Expo | abs(x') nach Expo | **abs(x) vor Expo (FC)** | so ist die öffentliche Formel |
| 33 | Kollision Ruhe | weicher Kontakt je Substep | 2-Frame-Heuristik | Impuls + Aufrichten + Schlaf | **Impuls + Bodenbedingung + Aufrichten + Schlaf (AF)** | weicher Kontakt braucht verlässliche Kontaktebenen, die MC nicht liefert (Kanten, Leere) |
| 34 | Kontaktpunkt | Stützpunkt Box + Scheiben | Mittel der Kandidaten | 12 Hüllpunkte, 3 mm | **12 Hüllpunkte, 3 mm (AF)** | einfach, flache Landung ohne Scheindrehmoment |
| 35 | Crash | Normal-v ≥ impactTolerance | + Schrammen 1,5× | Normal-v | **Normal-v ≥ impactTolerance, zusätzlich Schrammen (FC)** | Wand-Schrammen bei Vollgas soll crashen |
| 36 | Prop-Strike | Rahmen-Kick | 0,2 s Schubverlust | ω·0,2 + Yaw-Impuls | **ω·0,2 + Yaw-Impuls (AF), nur offene Props** | sichtbar, einfach |
| 37 | Zufall | xoshiro | SplittableRandom | xorshift64* | **xorshift64* + StrictMath (AF)** | Zustand im Snapshot sicherbar, JVM-unabhängig |
| 38 | Events | sealed Records | Liste | Ringpuffer primitiv | **Ringpuffer (AF)** | allokationsfrei |
| 39 | Arm-Sperren | EnumSet | EnumSet | Bitmaske | **Enum `ArmBlocker` + int-Bitmaske** | allokationsfrei, OSD-freundlich |
| 40 | Thermik | Rückwirkung + Schaden | nur Anzeige | optional | **Anzeige; Rückwirkung optional (Phase 3); kein Schaden** | Umfang |
| 41 | Gyro-Rauschen | Vibration 0,3 + RPM-Filter | 0,25 + 2,0 | 0 | **weiß 0,2 °/s + Vibration 1,0 °/s als Sinus je Rotor bei ω_i, nur PT1-Filter** | lebendig genug, keine Notch-Filter in v1; Vibration liegt wie beim echten Quad bei der Rotorfrequenz (Korrektur 45) |
| 42 | Paketstruktur | Unterpakete | flach | flach | **flach** | Tests im selben Paket erreichen package-private Interna |
| 43 | Namen | DroneSim, PilotInput, Environment, MoveResult | FlightSim, CollisionReport | DroneSim, SimInput, MoveReport | **`DroneSim`, `PilotInput`, `SimEnvironment`, `MoveReport`** | eindeutig, kein Namenskonflikt |
| 44 | Airmode-Start | 25 % | 25 % | 25 % | **abgeleitet: clamp(0,9·Schwebe-Stick, 10 %, 25 %)** | RACER schwebt bei 17 %, sonst kein I-Term im Schweben |
| 45 | TPA | – | D 65 % ab max(0,35; Hover + 0,15) | 65 % ab 0,35 | **Modus D, 65 %, Breakpoint max(0,35; Hover + 0,15) (FC)** | im Prototyp auch ohne TPA stabil bis 0,9 Gas; TPA greift nie im Schweben |

Bewusste Abweichungen von Betaflight: F-Skala (Zeile 24), PT2-RC-Glättung mit fester Grenzfrequenz, kein Dynamic
Notch/RPM-Filter, kein D-Min, kein Throttle-Boost, perfekter Lageschätzer (wahre Orientierung).

---

## 2. Konventionen

### 2.1 Einheiten

SI intern: m, s, kg, N, N·m, V, A, W, rad, rad/s. 1 Block = 1 m. `g = 9,81 m/s²`, `ρ0 = 1,225 kg/m³`
(`SimEnvironment.airDensity` überschreibt ρ). Grad und °/s nur in FC-Konfiguration, FC-Rechnung und Telemetrie.
Der FC rechnet wie eine Firmware in **°/s** und **Mixer-Einheiten** (±1 = voller Motorbereich); die Umrechnung
passiert nur in `FcAxes`.

### 2.2 Koordinatensysteme

| Größe | Festlegung |
|---|---|
| Welt W | Minecraft: +X Ost, +Y oben, +Z Süd, rechtshändig. Schwerkraft `(0, −g, 0)` |
| Körper B | **+X_B = links, +Y_B = oben (Schubrichtung), +Z_B = vorn (Nase)**, rechtshändig, X = Y × Z, Ursprung = Schwerpunkt (CoM) |
| Orientierung | `q` normiert, Körper → Welt: `v_W = q·v_B`, `R = R(q)` |
| Heading (Telemetrie) | `f = q·(0,0,1)`, **ψ = atan2(f.x, f.z)**; ψ = 0 → Nase nach +Z (Süd), ψ = +90° → Nase nach +X (Ost). Minecraft-`yRot` = −ψ in Grad |
| Twist-Heading (ANGLE) | `ψ_t = 2·atan2(q.y, q.w)` (Weltkomponenten von q), auf (−π, π] gebracht. Gleich ψ bei Roll = 0, stetig durch 90° Neigung, nur bei exakt 180° Neigung undefiniert → letzter gültiger Wert |
| Neigung | `u = q·ŷ` (Körper-oben in Welt), `tilt = acos(clamp(u.y))` |
| Roll/Pitch (Telemetrie) | `l = q·x̂`: `roll = asin(clamp(l.y))` (positiv = rechte Seite unten), `pitch = asin(clamp(−f.y))` (positiv = Nase unten) |
| Minecraft-Kamera | `yRot = −ψ·180/π`, `xRot = −asin(f.y)·180/π` (positiv = nach unten), Kamera-Roll `atan2(l.y, u.y)` |

### 2.3 FC-Achsen

| FC-Achse | positiv heißt | Körperrate |
|---|---|---|
| ROLL | rechte Seite runter | `+ω_B.z` |
| PITCH | Nase runter (Stick nach vorn) | `+ω_B.x` |
| YAW | Nase nach rechts (von oben im Uhrzeigersinn) | `−ω_B.y` |

`gyroFc = (deg(ω.z), deg(ω.x), −deg(ω.y))`, umgekehrt `ω_B = rad(pitch, −yaw, roll)`. Nur `FcAxes` enthält diese
Vorzeichen.

### 2.4 Motoren (Quad X, Betaflight-Nummerierung, Props-in)

`s = L/√2` mit `L = frame.armLengthMeters()`; `y_m` = Höhe der Rotorebene über dem CoM (Kap. 4.2).

| Index | Motor | Position r_i (B) | Drehsinn von oben | σ_i | Roll | Pitch | Yaw |
|---|---|---|---|---|---|---|---|
| 0 | M1 hinten rechts | (−s, y_m, −s) | CW | −1 | −1 | +1 | −1 |
| 1 | M2 vorn rechts | (−s, y_m, +s) | CCW | +1 | −1 | −1 | +1 |
| 2 | M3 hinten links | (+s, y_m, −s) | CCW | +1 | +1 | +1 | +1 |
| 3 | M4 vorn links | (+s, y_m, +s) | CW | −1 | +1 | −1 | −1 |

`σ_i = +1`: Rotor dreht um +Y_B (CCW von oben). Mixer-Spalten: `roll_i = x_i/s`, `pitch_i = −z_i/s`, `yaw_i = σ_i`.
Reaktionsmoment auf den Rahmen `τ_y,i = −σ_i·Q_react,i`: mehr Gas auf CCW-Rotoren dreht den Rahmen nach rechts.
`PidProfile.propsOut = true` negiert alle `σ_i` und die Yaw-Spalte. (`s` ohne Index ist der Motorversatz.)
Vorzeichenprobe: `τ_B = Σ r_i × (0, T_i, 0) = (−Σ z_i T_i, 0, Σ x_i T_i)`. Mehr Schub links (x > 0) → τ_z > 0 = Roll
rechts. Mehr Schub hinten (z < 0) → τ_x > 0 = Nase runter.

---

## 3. Klassen und öffentliche API

### 3.1 Layout (ein flaches Paket `de.lardtom.propwash.sim`)

Öffentlich (**fett**), alles andere package-private und nur für die Sim und ihre Tests.

| Datei | Rolle |
|---|---|
| **`DroneSim`** | Fassade, einziger Einstieg für `PilotSession` |
| **`PilotInput`**, **`StabMode`**, **`Axis`** | Eingabe (auch vom Autopiloten befüllt) |
| **`SimEnvironment`**, **`Fluid`** | Umwelt pro Frame vom Entity |
| **`StepResult`**, **`MoveReport`** | Kollisions-Kontrakt Entity ↔ Sim |
| **`Telemetry`**, **`BatteryState`** | Live-Werte |
| **`EventQueue`**, **`SimEventType`**, **`ArmBlocker`**, **`DisarmReason`** | Ereignisse, Sperrgründe |
| **`ImpactLog`**, **`ImpactSample`**, **`CrashDamage`**, **`WearEffects`** | Aufprall-Protokoll und Bauteil-Verschleiß (9.6) |
| **`SimSnapshot`** | primitiver Zustand (Netzwerk, Teleport, Determinismus-Tests) |
| **`AirframeSpec`** | unveränderliche physikalische Parameter eines Builds |
| **`BuildAnalysis`**, **`BuildWarning`** | Werkbank-Kennwerte |
| **`PidAutoTune`** | abgeleitete Default-PIDs |
| **`RateCurves`**, **`ThrottleCurve`**, **`StickMath`** | Formeln für Configurator-Graphen und Autopilot |
| **`FlightControllerConfig`**, **`RateProfile`**, **`RateCurve`**, **`RatesType`**, **`PidProfile`**, **`AxisPid`**, **`PidSource`**, **`ItermRelaxMode`**, **`TpaMode`**, **`ThrottleLimitType`**, **`FilterSettings`**, **`PidLoopRate`**, **`SimTuning`** | Konfiguration (Kap. 11) |
| `ParamDerivation`, `ModelConstants`, `FrameGeometry`, `CellCurve` | Ableitung (Kap. 4) |
| `OperatingPoints` | stationäre Löser (geteilt von `BuildAnalysis`, `PidAutoTune`, Laufzeit-Schwebeschätzung) |
| `PowerSystem` | Akku, Bus-Löser, 4 ESC/Motoren (SoA `double[4]`) |
| `RotorAero`, `BodyAero`, `GroundEffect`, `PropWash`, `WakeField` (Phase 2), `Gusts` | Aerodynamik |
| `RigidBody` | Zustand p, v, q, L und Integration |
| `CollisionSolver`, `HullShape` | Stöße, Bodenruhe, Schlafen |
| `FlightController`, `RcProcessor`, `AttitudeController`, `AxisPidController`, `Mixer`, `Arming`, `GyroModel`, `FcAxes` | FC |
| `Thermal` (Phase 3) | Temperaturen |
| `Pt1`, `Pt2`, `SimRandom`, `SimMath` | Werkzeuge |

Abhängigkeiten: `sim` → `build` (nur lesend) → nichts. Kein Zugriff auf Minecraft, keine statischen veränderlichen
Felder außer dem thread-sicheren Parameter-Cache.

### 3.2 Fassade

```java
public final class DroneSim {
    public static final int PHYSICS_HZ = 2000;
    public static final long SUBSTEP_NANOS = 500_000L;
    public static final int MAX_SUBSTEPS = 100;
    public static final double MAX_FRAME_DT = 0.05;

    public DroneSim(DroneBuild build, FlightControllerConfig config, SimTuning tuning, long seed);
    public DroneSim(AirframeSpec params, FlightControllerConfig config, SimTuning tuning, long seed);

    public AirframeSpec params();
    public void reset(double x, double y, double z, double headingRad);
    public void setBatteryCharge(double chargeFraction);
    public boolean setBuild(DroneBuild build);
    public void setConfig(FlightControllerConfig config);
    public void setTuning(SimTuning tuning);

    public void step(double frameDtSeconds, PilotInput input, SimEnvironment env, StepResult out);
    public void step(long frameNanos, PilotInput input, SimEnvironment env, StepResult out);
    public void afterMove(MoveReport report);

    public void teleport(double x, double y, double z, boolean keepVelocity);
    public void forceDisarm(DisarmReason reason);
    public void requireArmSwitchReset();

    public Telemetry telemetry();
    public EventQueue events();
    public ImpactLog impacts();
    public PartWear wear();
    public void setWear(PartWear wear);
    public void copyStateTo(SimSnapshot dst);
    public void copyStateFrom(SimSnapshot src);
    public void getPosition(Vector3d dst);
    public void getVelocity(Vector3d dst);
    public void getAttitude(Quaterniond dst);
}
```

Semantik:

| Methode | Verhalten |
|---|---|
| `reset` | CoM an (x, y, z), aufrecht mit Heading, v = L = 0, Rotoren stehen, disarmed, FC-Zustände und Filter auf 0, Akku unverändert, `ARM_SWITCH_NOT_RESET` gesetzt, falls der Schalter im nächsten Input an ist |
| `setBatteryCharge` | SoC = clamp(f, 0, 1), `mAhUsed = (1 − SoC)·capacity`, V_pol = 0 (Akku aus dem Item-Data-Component) |
| `setBuild` | nur disarmed (sonst `false`): Parameter neu ableiten (Cache), Rotoren stoppen; ist `config.pid.source == DERIVED`, PIDs neu ableiten; Pose bleibt |
| `config()` / `tuning()` | liefern **Kopien**; Änderungen wirken nur über `setConfig`/`setTuning` (die kopieren und `sanitize()` aufrufen). So kann kein Aufrufer ungeprüfte Werte (z. B. `horizonStickTransitionPercent = 0`) am Sanitizer vorbei in den FC schreiben |
| `setConfig` | tiefe Kopie + `sanitize()`. Filter- und Loop-Änderungen initialisieren Filter neu; armed werden sie bis zum nächsten Disarm zurückgestellt (wie BF, das beim Speichern neu startet) |
| `step` | Kap. 8.2. `out` wird überschrieben, keine Allokation |
| `afterMove` | jeden Frame nach `entity.move` aufrufen, auch ohne Treffer (Kap. 9) |
| `teleport` | Position setzen (Server-Korrektur, Respawn), optional v = L = 0; Wake-Pakete werden mitverschoben; weckt aus dem Schlaf |
| `forceDisarm` | sofortiger Disarm mit Grund, Event |
| `requireArmSwitchReset` | nur disarmed (armed ohne Wirkung): wie nach `reset` wird der nächste Input geprüft; ist `armSwitch` dann an, gilt `ARM_SWITCH_NOT_RESET` samt Event `ARM_BLOCKED`, erst aus, dann an armt. Der Client ruft es auf, wenn sich die Quelle des Arm-Schalters ändert (anderes Gerät, Funke verbunden, Belegung/Invertierung gespeichert), damit eine Umstellung nie selbst armt |
| `telemetry()` | lebendes Objekt, gültig bis zum nächsten `step`; `copyTo` für andere Threads |
| `events()` | Ereignisse von `step` + `afterMove`; werden zu Beginn des nächsten `step` geleert |
| `impacts()` | Aufpralle von `afterMove` (9.6); wie `events()` zu Beginn des nächsten `step` geleert |
| `setWear` | Zustand der Bauteile übernehmen (`build.PartWear`, null = neu) und die Wirkungen aus 9.6 sofort setzen; bei `hardwareFault` wird armed sofort mit `HARDWARE` entschärft |

Einfädig (Render-Thread des Piloten). `AirframeSpec` und `BuildAnalysis` sind unveränderlich und thread-sicher
(Werkbank, auch serverseitig).

### 3.3 Ein-/Ausgabeobjekte (veränderlich, vom Aufrufer wiederverwendet)

```java
public enum StabMode { ACRO, ANGLE, HORIZON }
public enum Axis { ROLL, PITCH, YAW }
public enum Fluid { NONE, WATER, LAVA }

public final class PilotInput {
    public double roll, pitch, yaw;
    public double throttle;
    public StabMode mode = StabMode.ACRO;
    public boolean armSwitch;
    public boolean linkOk = true;
    public boolean altHold, posHold;
    public boolean mode3d;
}

public final class SimEnvironment {
    public double groundDistance = Double.POSITIVE_INFINITY;
    public double windX, windY, windZ;
    public double gustIntensity;
    public double airDensity = 1.225;
    public double ambientTempC = 20.0;
    public Fluid fluid = Fluid.NONE;
    public double skyView = 1.0;
}

public final class StepResult {
    public double dx, dy, dz;
    public final Quaterniond orientation = new Quaterniond();
    public int substeps;
    public boolean armed;
    public boolean sleeping;
}

public final class MoveReport {
    public double requestedX, requestedY, requestedZ;
    public double actualX, actualY, actualZ;
    public boolean hitX, hitY, hitZ;
    public double impactVelocityX = Double.NaN, impactVelocityY = Double.NaN, impactVelocityZ = Double.NaN;
    public double surfaceGrip = 1.0;
    public double surfaceRestitution = -1.0;
    public double supportGap = Double.NaN;
}
```

- `roll/pitch/yaw ∈ [−1, 1]`, `throttle ∈ [0, 1]`, NaN → 0, außerhalb → geklemmt.
- `groundDistance`: Abstand CoM → erste feste Oberfläche senkrecht darunter (Welt −Y), vom Entity per Raycast
  (Reichweite 5 m, sonst +∞). Wird für Bodeneffekt, Höhe über Grund und das Aufrichten genutzt.
- `gustIntensity ∈ [0, 1]`, `airDensity` geklemmt auf [0,3; 1,5].
- `surfaceGrip`: 1,0 = normaler Block, Eis ≈ 0,1, Slime ≈ 1,2. **Achtung:** Minecraft-`getFriction()` ist eine
  Rutschigkeit (0,6 normal, 0,98 Eis); das Entity rechnet `grip = clamp((1 − slip)/0,4, 0,05, 1,5)` um.
- `surfaceRestitution < 0` → Rahmenwert; Slime z. B. 0,8.
- `impactVelocity*`: optional, NaN = Sim nimmt ihre eigene Geschwindigkeit.
- `supportGap`: freie Höhe unter der Hitbox nach der Bewegung, gemessen vom Bewegenden mit denselben Kollisionsformen
  wie die Bewegung (Blöcke, Kollisions-Entities, Weltgrenze, Gate-Kollisionsteile), Messweite 5 cm, sonst der Wert der
  Messweite. Nur nötig, solange die Sim grounded ist oder schläft (9.4, 9.5); NaN = unbekannt, dann gilt die alte
  Höhen-Heuristik ohne Auflageprüfung.

### 3.4 Werkzeuge für Configurator und Autopilot

```java
public final class RateCurves {
    public static double rateDps(RatesType type, RateCurve r, double stick);
    public static double stickFor(RatesType type, RateCurve r, double rateDps);
    public static double maxRateDps(RatesType type, RateCurve r);
}
public final class ThrottleCurve {
    public static double apply(PidProfile p, double stick);
    public static double inverse(PidProfile p, double throttle);
}
public final class StickMath {
    public static double angleToStick(double angleDeg, PidProfile pid);
    public static double stickToAngle(double stick, PidProfile pid);
    public static double rateToStick(double rateDps, RateProfile rates, Axis axis);
    public static double throttleToStick(double throttle, PidProfile pid);
}
```

`stickFor` und `inverse` per Bisektion (40 Schritte) auf der monotonen Kurve, Ergebnis geklemmt.
`angleToStick = clamp(angleDeg/angleLimitDeg, −1, 1)` (linear, Kap. 6.5).

---

## 4. Parameterableitung aus `DroneBuild`

`AirframeSpec ParamDerivation.derive(DroneBuild b)` ist eine reine Funktion (< 0,1 ms), gecacht in einer
`ConcurrentHashMap<DroneBuild, AirframeSpec>` (der Record hat Wert-Gleichheit). Sie liefert für **jede** der 6000
Kombinationen endliche Werte, auch für nicht baubare (`problems()` nicht leer) und absurde (6S am Whoop).
Öffentlicher Einstieg: `AirframeSpec.of(DroneBuild)`.

### 4.1 Modellkonstanten (`ModelConstants`, `static final`, keine Spieler-Einstellungen)

| Name | Wert | Bedeutung |
|---|---|---|
| `G` | 9,81 m/s² | Schwerkraft |
| `RHO0` | 1,225 kg/m³ | Referenz-Luftdichte der Prop-Beiwerte |
| `DUCT_GAIN` | 1,15 | Standschub-Gewinn durch Ducts |
| `DUCT_FADE` | 0,40 | Duct-Gewinn ist bei λ ≥ 0,40 verschwunden |
| `ZERO_LIFT_PITCH` | 0,20 | Nullschub-Geschwindigkeit `v_0 = (pitch + 0,20·D)·ω/2π` |
| `DESCENT_GAIN`, `DESCENT_CAP` | 0,5; 0,6 | Sinken erhöht Schub bis +30 % |
| `Q_CLIMB`, `Q_MIN`, `Q_DESCENT` | 0,6; 0,05; 0,3 | Lastmoment-Faktor über λ |
| `K_TL_OPEN`, `K_TL_DUCT` | 0,12; 0,06 | Translational Lift |
| `KAPPA_H`, `H_TIP_OFFSET` | 1,0; 5 m/s | Rotor-H-Kraft |
| `SIDE_AREA`, `TOP_AREA_OPEN`, `TOP_AREA_DUCT` | 1,15; 2,9; 2,5 | Rumpfflächen relativ zu `frontalAreaM2` |
| `GE_CAP`, `GE_MIN_UP` | 1,3; 0,5 | Bodeneffekt-Grenze; nur wenn `u.y > 0,5` |
| `I0_FRACTION` | 0,03 | `I_0 = 0,03·motor.maxCurrent` |
| `ESC_R_BASE`, `ESC_R_PER_A` | 0,003 Ω; 0,15 Ω·A | `R_esc = 0,003 + 0,15/escCurrentPerMotor` |
| `ESC_BURST` | 1,3 | `I_lim = 1,3·escCurrentPerMotor` (Phasenstrom) |
| `ESC_INPUT_LIMIT` | 0,8 | `I_in = 0,8·escCurrentPerMotor` (Eingangsstrom `d_eff·I = (R_m·I + K_e·ω)·I/V` je ESC) |
| `PROP_J`, `BELL_J`, `BELL_EXTRA` | 0,25; 0,30; 2,5 mm | Rotorträgheit |
| `ROTOR_PLANE_EXTRA` | 8 mm | Rotorebene = Statorhöhe + 8 mm über Armebene |
| `OMEGA_MAX_FACTOR` | 1,15 | `ω_max = 1,15·K_v·V_full` |
| `MOTOR_TAU_MAX` | 0,040 s | Klemme der Motor-Zeitkonstante über J_eff |
| `WIRING_R` | 0,003 Ω | Kabel + Stecker |
| `POL_FRACTION`, `POL_TAU` | 0,5; 5 s | langsame Polarisation (zusätzlich zu R_pack) |
| `SAG_FAST_FRACTION`, `SAG_FAST_TAU` | 0,35; 0,5 s | Anteil von R_pack, der sich erst mit τ aufbaut; sofort wirken nur 65 % (aus echten Logs, Entscheidung 49) |
| `C_RATE_PENALTY`, `C_RATE_TAU` | 0,5; 2 s | Widerstandsanstieg über Dauerstrom, getrieben vom mit 2 s gefilterten Akkustrom (Erwärmung) |
| `LOW_SOC_KNEE`, `LOW_SOC_EXPONENT` | 0,15; 1,5 | Widerstandsanstieg bei leerem Akku: `L = clamp((0,15 − soc)/0,15, 0, 1)^1,5` (Entscheidung 50) |
| `LOW_SOC_OHMIC_GAIN`, `LOW_SOC_FAST_GAIN`, `LOW_SOC_SLOW_GAIN` | 1,5; 6; 4 | Faktor `1 + Gain·L` je Anteil: bei leerem Akku ohmsch ×2,5, schnell ×7, langsam ×5 |
| `AUX_BASE_W`, `AUX_PER_GRAM_W` | 0,6 W; 0,3 W/g | FC + RX + Video |
| `BATTERY_DENSITY` | 2,0 g/cm³ LiPo/LiHV, 2,4 g/cm³ Li-Ion | Akku-Quader |
| `BATTERY_ASPECT` | L : B : H = 2,2 : 1,3 : 1 | Länge entlang Z_B |
| `RESTITUTION` | 0,25 offen / 0,40 Duct | Stoßzahl |
| `RESTITUTION_MAX` | 0,95 | Obergrenze der wirksamen Stoßzahl (nach Oberfläche und `restitutionScale`) |
| `FRICTION` | 0,50 offen / 0,30 Duct | Reibwert Rahmen (× `surfaceGrip`) |
| `BOUNCE_MIN` | 0,4 m/s | darunter kein Rückprall |
| `BROWNOUT_V` | 2,9 V wenn `stack.minCells == 1`, sonst 5,5 V | FC-Versorgung |
| `V_MAX`, `OMEGA_BODY_MAX` | 150 m/s; 100 rad/s | Sicherheitsklemmen |
| `WASH_LOSS`, `WASH_NOISE` | 0,30/0,20; 0,30/0,15 (offen/Duct) | Prop-Wash |

### 4.2 Geometrie, Masse, Schwerpunkt, Trägheit

```
m      = build.totalMassGrams()/1000                 [kg]   (exakt, Test B1)
L      = frame.armLengthMeters();   s = L/√2
D      = prop.diameterMeters();     R = D/2;   A = πR²
pitch  = prop.pitchInch·0,0254                       [m]
d_st, h_st aus motor.statorLabel: erste 2 Zeichen = Durchmesser [mm], Rest = Höhe [mm]
         ("2207" → 22/7, "0802" → 8/2, "2306.5" → 23/6,5, "1404" → 14/4, "2807" → 28/7)
y_rot  = h_st + 8 mm                                 Rotorebene über der Armebene
```

`FrameGeometry` (sim-seitige `EnumMap<FrameType, …>`, mm, relativ zur Armebene y = 0):

| Rahmen | Akku | Akkuplatte y_pl | Elektronik y_e (Stack + Video) |
|---|---|---|---|
| WHOOP_65 | unten | −3 | 4 |
| CINEWHOOP_3 | oben | 20 | 10 |
| FREESTYLE_5 | oben | 30 | 12 |
| RACE_5 | unten (seit 0.5.0, vorher oben bei 25) | −2,8 | 10 |
| LONG_RANGE_7 | oben | 32 | 14 |
| künftige Rahmen (Rückfall) | oben, außer Radstand < 100 mm | 0,13·Radstand | 0,05·Radstand |

Akku-Quader: `V = m_batt/ρ_batt`, `H = ∛(V/2,86)`, `B = 1,3·H`, `Lb = 2,2·H`; Mitte `y_b = y_pl + H/2` (oben) bzw.
`y_pl − H/2` (unten).

Massenpunkte und Körper (y über der Armebene): Rahmen bei 0; Motoren bei `y_rot/2`; Props bei `y_rot`; Akku bei
`y_b`; Stack + Video als Punktmasse bei `y_e`; jedes Zubehör (`build.Accessories`, optional) als Quader mit Mitte
`c_k = AccessoryAnchor.of(frame, mount).centerOf(type)` (Rahmensystem in mm, X rechts/Z hinten → Körpersystem mit x = −X, z = −Z; die Neigung
der Halterung steckt in der Mitte, der Quader selbst bleibt achsparallel).

```
y_com = (m_b·y_b + m_e·y_e + 4·m_mot·y_rot/2 + 4·m_prop·y_rot + Σ_k m_k·c_k,y)/m        (Rahmen trägt 0 bei)
y_m   = y_rot − y_com                                                   Rotorebene relativ zum CoM
y'_k  = y_k − y_com
```

Diagonaler Tensor im CoM (Ixx = Nicken, Iyy = Gieren, Izz = Rollen):

| Element | Ixx | Iyy | Izz |
|---|---|---|---|
| 4 Motoren (m_mot) und 4 Props (m_prop) als Punkte bei (±s, y', ±s) | 4m(s² + y'²) | 8m·s² | 4m(s² + y'²) |
| Rahmen offen: 60 % Arme (Stäbe 0…L auf den Diagonalen) | m_a(L²/6 + y_com²) | m_a·L²/3 | wie Ixx |
| Rahmen offen: 40 % Platte (Quadrat 0,8·L) | m_p((0,8L)²/12 + y_com²) | m_p(0,8L)²/6 | wie Ixx |
| Rahmen Duct: 50 % Ringe (r_d = R + 3 mm) bei den Motoren | m_r(s² + r_d²/2 + y_com²) | m_r(2s² + r_d²) | wie Ixx |
| Rahmen Duct: 20 % Arme, 30 % Platte | wie oben | wie oben | wie oben |
| Akku-Quader (B entlang X, H entlang Y, Lb entlang Z) | m_b((H² + Lb²)/12 + y'_b²) | m_b(B² + Lb²)/12 | m_b((B² + H²)/12 + y'_b²) |
| Stack + Video (Punkt) | m_e·y'_e² | 0 | m_e·y'_e² |
| Zubehör k (Quader B_k × H_k × T_k, B entlang X, unten mit Streckung `lengthMm/widthMm`) bei (x_k, y'_k, z_k) | m_k((H_k² + T_k²)/12 + y'_k² + z_k²) | m_k((B_k² + T_k²)/12 + x_k² + z_k²) | m_k((B_k² + H_k²)/12 + x_k² + y'_k²) |

**Akku unter dem Rahmen (Race 5″, seit 0.5.0).** Race-Drohnen tragen den Akku unter dem Rahmen (`battery.on_top: false`,
`plate_mm` −2,8 = Bodenplatte 2 mm + Pad 0,8 mm), Freestyle- und Long-Range-Drohnen oben. Nur die Daten ändern sich, die
Formeln oben sind dieselben. Beim Racer-Preset (6S 1100): Schwerpunkt 19,8 → −4,2 mm (24 mm tiefer, jetzt zwischen Akku und
Bodenplatte), Rotorebene −5,3 → +18,7 mm über dem Schwerpunkt, Ixx (Nicken) 1155,6 → 1087,1 und Izz (Rollen) 1110,6 → 1042,2 · 10⁻⁶ kg·m² (−6 %, die
Masse liegt enger um den Schwerpunkt, weil der Akku nicht mehr 25 mm über der Bodenplatte auf der Deckplatte sitzt),
Roll-/Nick-Autorität 2289/2200 → 2439/2338 (+6 %), Auto-PIDs Roll 36/42/33 → 34/40/31 und Nick 37/44/34 → 35/41/32, Hülle
59,3 → 48,6 mm hoch mit dem Akku als tiefstem Punkt (Fuß −22,8 → −29,9 mm unter dem Schwerpunkt). Folgen im Flug: die
H-Kräfte der Rotoren greifen jetzt über dem Schwerpunkt an (bei Vorwärtsfahrt ein leichtes Aufbäumen, das der Regler
ausgleicht, statt vorher ein leichtes Abtauchen), Bauchlandungen treffen den Akku (`CrashDamage`, Hüllpunkt unten), und
weil oben nichts mehr über die Props ragt, streifen die Props eine Decke, die der Akku vorher abgefangen hat (JUnit
`NumericalRobustnessReviewTest.raceDroneWithTheBatteryUnderTheFrameTouchesACeilingWithItsProps`). Stirnfläche und
Luftwiderstand bleiben (der Akku verdeckt von vorn gleich viel), Schwebegas, Topspeed und Flugzeit ändern sich nicht
(`tools/calibration.sh`: `calibration.txt` gleich, `motion.txt` und `fall.txt` nur für `race_5`).

Das Zubehör ist der einzige Anteil mit Abstand längs Z: Eine Action-Cam sitzt vorn **und** hoch über dem Schwerpunkt und erhöht Nick- und
Rollträgheit deutlich (Freestyle 5″ mit Heavy-Cam 60 × 45 × 35 mm: Ixx +50 %, Izz +17 %, Schwerpunkt 22 → 30 mm). Der Schwerpunkt bleibt in x/z bei 0
(keine Trimmung nötig, Deviationsmomente vernachlässigt wie oben).

### 4.3 Motor, ESC, Rotor

```
K_v   = kv·2π/60        [rad/(s·V)];   K_t = K_e = 1/K_v   [N·m/A]
R_esc = 0,003 + 0,15/stack.escCurrentPerMotor            (STACK_30 5,7 mΩ · AIO_20 9,0 mΩ · AIO_WHOOP 33 mΩ)
R_m   = motor.resistanceOhm + R_esc
I_0   = 0,03·motor.maxCurrent
I_lim = 1,3·stack.escCurrentPerMotor                    Phasenstrom-Grenze (Hardware)
I_in  = 0,8·stack.escCurrentPerMotor                    Eingangsstrom-Grenze (Strommessung auf der DC-Seite)
I_max(V, ω) = min(I_lim, I_root),  (R_m·I_root + K_e·ω)·I_root = V·I_in     wirksame Phasenstrom-Grenze
      Im Stromlimit senkt der ESC sein wirksames Tastverhältnis auf d_eff = (R_m·I + K_e·ω)/V ≤ d; sein
      Eingangsstrom ist d_eff·I = (Motorleistung inkl. Kupfer- und ESC-Verlust)/V ≤ I_in (energieerhaltend)
r_bell= (d_st/2 + 2,5 mm)
J     = 0,25·m_prop·R² + 0,30·m_mot·r_bell²              [kg·m²]
ω_max = 1,15·K_v·cells·chemistry.fullCellVoltage
ω_h   = √(m·g/(4·k_T·DUCT_GAIN_eff))                     Schwebedrehzahl (DUCT_GAIN_eff = 1,15 bzw. 1)
τ_phys= J/(K_t²/R_m + 2·k_Q·ω_h)                         Drehzahl-Zeitkonstante im Schweben
J_eff = J·min(1, 0,040/τ_phys);   τ_m = J_eff/(K_t²/R_m + 2·k_Q·ω_h)
```

`J_eff` ersetzt `J` überall (Dynamik, Reaktionsmoment, Kreiselterm). Betroffen ist bei den Presets nur der WHOOP.

### 4.4 Propeller

Die Enum-Beiwerte gelten für Standschub ohne Duct: `T = c_t·ρ·n²·D⁴`, `P = c_p·ρ·n³·D⁵`, `n = ω/2π`.

```
k_T   = c_t·ρ0·D⁴/(4π²)          [N·s²]      (ohne Duct-Gewinn)
k_Q   = c_p·ρ0·D⁵/(8π³)          [N·m·s²]    (Q = P/ω)
v_0(ω)= max(1 m/s, (pitch + 0,20·D)·ω/2π)    Nullschub-Axialgeschwindigkeit
v_h   = √(m·g/(8·ρ0·A·DUCT_GAIN_eff))        induzierte Geschwindigkeit im Schweben
f_turb= 12 Hz·√(0,127/D)                     Wash-Rauschfrequenz (5″: 12 Hz, Whoop: 24 Hz)
k_tl  = 0,12 offen / 0,06 Duct
```

### 4.5 Akku und Nebenverbraucher

```
N      = battery.cells;   C = battery.capacityMah
R_pack = N·cellResistanceOhm + 0,003;   R_1 = 0,5·R_pack;   τ_1 = 5 s
I_cont = battery.maxContinuousCurrent()
V_full = N·fullCellVoltage
P_aux  = 0,6 W + 0,3 W/g·video.massGrams() + Σ accessory.flightBatteryWatts
         (ANALOG_NANO 1,8 · ANALOG_RACE 3,0 · DIGITAL_LITE 3,15 · DIGITAL_HD 7,5 W; LED-Streifen +1,5 W, Action-Cam Light +4 W,
          Action-Cam Heavy +0 W – eigener Akku)
```

Leerlaufspannung je Zelle `OCV(soc) = empty + (full − empty)·u(soc)` mit linear interpolierter Form `u`:

| soc | 0 | 0,05 | 0,1 | 0,2 | 0,3 | 0,4 | 0,5 | 0,6 | 0,7 | 0,8 | 0,9 | 1,0 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| LIPO | 0 | 0,16 | 0,27 | 0,33 | 0,37 | 0,40 | 0,43 | 0,50 | 0,61 | 0,73 | 0,86 | 1 |
| LIHV | 0 | 0,14 | 0,25 | 0,31 | 0,35 | 0,39 | 0,43 | 0,49 | 0,57 | 0,69 | 0,83 | 1 |
| LION | 0 | 0,22 | 0,31 | 0,38 | 0,43 | 0,47 | 0,52 | 0,60 | 0,68 | 0,78 | 0,88 | 1 |

In Volt (LiPo 3,50–4,20 V): 3,61 V bei 5 %, 3,69 V bei 10 %, 3,73 V bei 20 %, 3,80 V bei 50 %, 3,93 V bei 70 %, 4,10 V
bei 90 %; Li-Ion (3,00–4,20 V): 3,26 / 3,37 / 3,46 / 3,62 / 3,82 / 4,06 V. Die Werte bei 50 % sind seit 0.3.2 unverändert,
weil Schwebe-Arbeitspunkt, Steuerautorität und Auto-PIDs dort abgeleitet werden (Entscheidung 50, Vergleich mit
Tabellen in `docs/design/realism/power-video.md` 2.4b).

Unter soc = 0 (Tiefentladung): linear von `empty` auf `cutoff` bei soc = −0,05, dann auf `0,8·cutoff` bei
soc = −0,10; soc wird bei −0,10 geklemmt.

### 4.6 Aerodynamische Flächen, Kollision, Hülle

```
A_z = frame.frontalAreaM2 + Σ accessory.frontalAreaM2;  A_x = 1,15·A_z;  A_y = 2,9·A_z (offen) bzw. 2,5·A_z (Duct);  C_d = frame.dragCoefficient
c_rot = ½·ρ0·C_d·A_y·L³/4                           Rotationsdämpfung (nur beim Taumeln relevant)
impactTolerance = frame.impactTolerance;  e0 = 0,25/0,40;  μ0 = 0,50/0,30   (offen/Duct)
```

**Hülle:** 12 Punkte im Körpersystem (relativ zum CoM), Reihenfolge fest (Index = Kontakt-ID):

| k | Punkt | Koordinaten |
|---|---|---|
| 0–3 | Propspitzen M1–M4 (außen auf der Diagonale) | (±(s + r_t/√2), y_m, ±(s + r_t/√2)), `r_t = R` (+3 mm bei Duct) |
| 4–7 | Motorunterseiten M1–M4 | (±s, −y_com − 3 mm, ±s) |
| 8–9 | Akku oben vorn/hinten (bzw. unten beim Whoop) | (0, y_b ± H/2 − y_com, ±Lb/2) |
| 10 | Kameranase | (0, y_e − y_com, 0,8·s) |
| 11 | Bodenplatte | (0, −y_com − 3 mm, 0) |

Obere Zubehör-Halterung: Liegt die höchste Ecke eines oberen Zubehörs (`AccessoryAnchor.corners`) über der Akku-Oberseite (nur Rahmen mit
Akku oben), ersetzt sie Punkt 8 (y und z der Ecke, x = 0). Damit liegt eine Drohne mit Action-Cam beim Überschlag auf Kamera und Akku-Hinterkante
auf, und Hitbox-Höhe und Fußpunkt wachsen mit.

**Entity-Hitbox** (achsparallel, fest, dreht nicht mit): Breite `2s + D (+6 mm Duct)`, Höhe = `max_k y_k − min_k y_k`,
Fußpunkt `hitboxFootOffset = min_k y_k` (negativ, CoM → AABB-Unterkante). `maxUpStep = 0`.

### 4.7 `AirframeSpec` (öffentliche, finale Felder; Arrays nur über Zugriffsmethoden)

```java
public final class AirframeSpec {
    public static AirframeSpec of(DroneBuild build);
    public final DroneBuild build;  public final String buildKey;  public final boolean buildable;
    public final double massKg, armLength, motorOffset, propDiameter, propRadius, discArea, propPitch;
    public final boolean ducted;
    public final double comHeight, rotorPlaneOffset;
    public final double inertiaXx, inertiaYy, inertiaZz;
    public final double kv, kt, motorResistance, escResistance, idleCurrent, escCurrentLimit, escInputLimit;
    public final double rotorInertia, rotorInertiaEff, omegaMax, hoverOmega, motorTauPhys, motorTau;
    public final double kThrust, kTorque, ductGain, zeroThrustPerOmega, translationalLiftGain, hoverInducedVelocity, washNoiseHz;
    public final int cells;  public final double capacityMah;  public final BatteryType.Chemistry chemistry;
    public final double packResistance, polarizationResistance, continuousCurrent, fullPackVoltage, auxPower, brownoutVoltage;
    public final double areaX, areaY, areaZ, dragCoefficient, rotationalDamping;
    public final double impactTolerance, restitution, friction;
    public final double hitboxWidth, hitboxHeight, hitboxFootOffset;
    public final double authorityRoll, authorityPitch, authorityYawSteady, authorityYawInstant;
    public double motorX(int i); public double motorY(int i); public double motorZ(int i); public int spin(int i);
    public double mixRoll(int i); public double mixPitch(int i); public double mixYaw(int i);
    public int hullCount(); public double hullX(int k); public double hullY(int k); public double hullZ(int k);
    public int hullMotor(int k);
}
```

`buildKey` = Teile-Keys mit `/` verbunden (mit Zubehör zusätzlich `/` + `Accessories.key()`, z. B. `/top=action_cam_heavy`). Zusätzliche Felder
`accessoryPower` (W am Flugakku, in `auxPower` enthalten) und `accessoryMassKg`. `zeroThrustPerOmega = (pitch + 0,20·D)/2π`. `hullMotor(k)` = Motorindex für
Propspitzen (k = 0–3), sonst −1. Die Autoritäten (Kap. 4.8) werden bei soc = 0,5 berechnet und von `PidAutoTune`
genutzt.

### 4.8 Autorität und Motordynamik (linearisiert im Schweben, soc = 0,5, Idle 5,5 %)

```
B_m     = K_t²/R_m + 2·k_Q·ω_h
dω/dd   = (K_t·V_h/R_m)/B_m                 V_h = Busspannung im Schweben (OperatingPoints, soc 0,5)
dT/dω   = 2·k_T·DUCT_GAIN_eff·ω_h
b_roll  = 4·s·(dT/dω)·(dω/dd)·(1 − idle)/Izz          [rad/s² pro Mixer-Einheit]
b_pitch = 4·s·(dT/dω)·(dω/dd)·(1 − idle)/Ixx
b_yaw,stat = 4·(2·k_Q·ω_h)·(dω/dd)·(1 − idle)/Iyy     stationär (Prop-Luftmoment)
b_yaw,inst = 4·K_t·V_h·(1 − idle)/(R_m·Iyy)           sofort (Strom-Kick vor dem Drehzahlanstieg)
```

### 4.9 Abgeleitete Werte der 5 Presets

| Größe | WHOOP | CINEWHOOP | FREESTYLE | RACER | LONG_RANGE |
|---|---|---|---|---|---|
| Masse [g] | 28,1 | 286,7 | 525,2 | 430,6 | 846,2 |
| s [mm] | 23,0 | 50,2 | 79,5 | 76,0 | 104,3 |
| y_com / y_m [mm] | 0,7 / 9,3 | 10,3 / 1,7 | 22,0 / −7,0 | −4,2 / 18,7 | 28,5 / −13,5 |
| Ixx / Iyy / Izz [10⁻⁶ kg·m²] | 7,99 / 13,63 / 7,75 | 473,1 / 820,8 / 461,4 | 1469,5 / 2398,5 / 1408,6 | 1089,6 / 1976,3 / 1044,6 | 3567,8 / 5934,4 / 3396,6 |
| J / J_eff [10⁻⁹ kg·m²] | 46,4 / 36,7 | 893,5 | 6346 | 6470 | 17304 |
| k_T [N·s²] (ohne Duct) | 2,8656e−9 | 9,3169e−8 | 1,0635e−6 | 1,0812e−6 | 2,8035e−6 |
| k_Q [N·m·s²] | 1,2018e−11 | 1,0643e−9 | 1,1002e−8 | 1,1919e−8 | 3,1769e−8 |
| K_t [mN·m/A] / R_m [mΩ] | 0,434 / 233,0 | 2,076 / 149,0 | 5,457 / 60,7 | 5,134 / 65,7 | 7,346 / 75,7 |
| I_0 / I_lim / I_in [A] | 0,105 / 6,5 / 4,0 | 0,54 / 32,5 / 20,0 | 1,35 / 71,5 / 44,0 | 1,26 / 71,5 / 44,0 | 1,35 / 71,5 / 44,0 |
| R_pack [mΩ] / I_cont [A] / P_aux [W] | 43 / 13,5 / 1,80 | 43 / 61,8 / 3,15 | 36 / 156 / 7,50 | 39 / 132 / 3,00 | 75 / 44 / 7,50 |
| v_h [m/s] | 5,69 | 7,42 | 6,29 | 5,70 | 5,83 |
| τ_phys → τ_m [ms] | 50,5 → **40,0** | 26,0 | 12,3 | 15,2 | 22,6 |
| b_roll / b_pitch [rad/s²] | 2557 / 2481 | 1353 / 1320 | 1969 / 1887 | 2445 / 2344 | 1491 / 1419 |
| b_yaw stat / inst | 231 / 1928 | 151 / 949 | 150 / 3195 | 187 / 3377 | 93 / 1301 |
| Hitbox B × H [mm], Fuß [mm] | 83 × 24, −14,6 | 182 × 47, −13,3 | 289 × 66, −25,0 | 282 × 49, −29,9 | 387 × 74, −31,5 |

---

## 5. Physik pro Substep (h = 0,5 ms)

Zustand: `p, v` (Welt), `q`, `L_W` (Welt), `ω_i[4]`, `soc, mAhUsed, V_pol, I_heat, V_bus,prev`, Filter- und
Rauschzustände, Kontaktzustand, Temperaturen.

### 5.1 Luftgeschwindigkeiten

```
wind_W = windScale·(env.wind) + gust_W                         (Kap. 5.9)
u_B    = Rᵀ·(v − wind_W)                                        Luftanströmung des Rumpfs (Körper)
ω_B    = I⁻¹·Rᵀ·L_W
u_i    = u_B + ω_B × r_i                                        lokale Anströmung am Rotor i
v_ax,i = u_i.y                                                  > 0: Luft kommt von oben (Steigen/Fahrt mit Neigung)
u⊥,i   = (u_i.x, 0, u_i.z),   v⊥,i = |u⊥,i|
λ_i    = v_ax,i / v_0(ω_i)
```

### 5.2 Akku ↔ ESC (geschlossene Lösung; explizite Kopplung ist bei 5″ instabil, Schleifenverstärkung ≈ 2,4)

```
E      = N·OCV(soc') − V_fast − V_pol soc' = soc (batterySag = true) bzw. 1 (batterySag = false: Akku bleibt bis zuletzt wie voll)
L      = clamp((0,15 − soc')/0,15, 0, 1)^1,5                      Anstieg bei leerem Akku, unter soc 0 kein weiterer Zuwachs
R_int  = R_pack·(0,65 + 0,5·max(0, I_heat/I_cont − 1))·(1 + 1,5·L)   Sofort-Anteil (gilt immer, auch ohne batterySag)
R_fast = 0,35·R_pack·(1 + 6·L)        R_slow = 0,5·R_pack·(1 + 4·L)    bauen sich mit 0,5 s bzw. 5 s auf (5.10)
stationär (Analyse, Flugzeiten): R_int + R_fast = R_pack·(1 + 0,5·max(0, I/I_cont − 1))·(1 + 1,5·L) + 0,35·R_pack·4,5·L, dazu R_slow;
   über soc 0,15 ist das bitgleich der Wert von 0.5.1 (R_pack·Rate + 0,5·R_pack)
I_heat ← I_heat + (|I_bat| − I_heat)·(1 − e^(−h/2 s))           Erwärmung; kurze Stromspitzen erhöhen R_int nicht
I_aux  = P_aux / max(V_bus,prev, 1 V)
aktive Kanäle: armed und kein Brownout; sonst Brücke offen (I_i = 0)
Kanalzustände (Active Set, Start: alle frei, bis 6 Durchgänge):
  frei:            I_i = (d_i·V − K_e·ω_i)/R_m                Eingangsstrom d_i·I_i
  Phasengrenze:    I_i = ±I_lim fest                           Eingangsstrom P_i/V,  P_i = (R_m·I_i + K_e·ω_i)·I_i
  Eingangsgrenze:  I_i = I_root(V, ω_i) (Kap. 4.3)             Eingangsstrom I_in (konstant)
Bus je Durchgang:  V = E − R_int·(I_aux + n_in·I_in + Σ_frei d_i·I_i + P_ph/V),  P_ph = Σ_Phasengrenze P_i
  → A·V² − B·V + R_int·P_ph = 0,   A = 1 + (R_int/R_m)·Σ_frei d_i²,
     B = E − R_int·(I_aux + n_in·I_in) + (R_int/R_m)·Σ_frei d_i·K_e·ω_i
     V = (B + √(B² − 4·A·R_int·P_ph))/(2A);  Diskriminante < 0 (Leistung über der Akkugrenze): V = B/(2A)
  danach jeden Kanal neu einordnen: I_frei > I_max(V, ω_i) → Eingangsgrenze (falls I_root < I_lim) bzw.
  Phasengrenze; I_frei < −I_lim → Phasengrenze; Ende, wenn sich keine Zuordnung mehr ändert
V_bus  = clamp(V, 0, E + 0,1·N)   (bei Klemmung Zuordnung und Ströme mit dem geklemmten V neu)
I_bat  = I_aux + Σ_akt d_eff,i·I_i = I_aux + Σ_akt (R_m·I_i + K_e·ω_i)·I_i/V_bus
         energieerhaltend: V_bus·I_bat = Σ (I_i²·R_m + K_e·ω_i·I_i) + P_aux; kann beim aktiven Bremsen kurz negativ werden
```

`d_i` ist das ESC-Tastverhältnis aus dem vorigen FC-Tick (1 Sample Latenz, Kap. 8.2).

### 5.3 Rotordynamik (linear-implizit, für jedes h stabil)

```
Q_i    = k_Q·(ρ/ρ0)·f_Q(λ_i)·ω_i²
f_Q(λ) = λ ≥ 0: max(0,05; 1 − 0,6·λ)        λ < 0: 1 + 0,3·min(−λ; 0,6)
aktiv:     τ_sh = K_t·(I_i − I_0·sg(ω_i)),    ∂f/∂ω = −(g_el + 2·k_Q·f_Q·ω_i)/J_eff
           g_el = K_t·K_e/R_m (frei), 0 (Phasengrenze), K_t·K_e·I_i/(2·R_m·I_i + K_e·ω_i) (Eingangsgrenze)
inaktiv:   τ_sh = −K_t·I_0·sg(ω_i),          ∂f/∂ω = −2·k_Q·f_Q·ω_i/J_eff
f      = (τ_sh − Q_i)/J_eff;   sg(ω) = ω/(|ω| + 1)     glattes Vorzeichen
ω_i⁺   = clamp(ω_i + h·f/(1 − h·∂f/∂ω), 0, ω_max)
Q_react,i = Q_i + J_eff·(ω_i⁺ − ω_i)/h        Reaktionsmoment auf den Rahmen (Luftmoment + Beschleunigungs-Kick)
```

Rückwärtslauf gibt es in v1 nicht (Turtle-Modus: Kap. 16).

### 5.4 Schub und Rotorkräfte (mit ω_i⁺)

```
f_lin(λ)  = λ ≥ 0: max(0, 1 − λ)            λ < 0: 1 + 0,5·min(−λ; 0,6)
v_0,i     = ω_i⁺·√(k_T/(2ρ0·A));  r = V_0/v_0;  s = λ·r;  k = 1/(r − 1)       (seit 0.5.0)
f_mom(λ)  = k·(r − s − u),  u = 2c/(b + √(b² + 4c)),  b = s + k,  c = (r − s)·k   Impuls + Blattelement
w(μ)      = 1 − μ²/√(μ⁴ + 4)                                                   Glauert, μ wie bei f_tl
f_ax(λ)   = 0 < λ < 1 und 1,05 < r ≤ 10⁶ und vorwärts drehend: f_lin + w·(f_mom − f_lin);  sonst f_lin
g_duct(λ) = ungeducted: 1;  Duct: λ ≤ 0: 1,15;  sonst 1 + 0,15·clamp(1 − λ/0,40, 0, 1)
μ_i       = v⊥,i / v_h;   f_tl = 1 + translationalLiftScale·k_tl·μ²/(1 + μ²)
f_ge,i    = Bodeneffekt (5.5);  f_wash,i = Prop-Wash (5.6)
T_i       = k_T·(ρ/ρ0)·g_duct·f_ax·f_tl·f_ge,i·f_wash,i·ω_i⁺²                       [N]
H_i       = −KAPPA_H·T_i·u⊥,i/(ω_i⁺·R + 5 m/s)                                    [N, Vektor in der Rotorebene]
```

`ω_B × r_i` in `u_i` erzeugt die aerodynamische Roll-/Nickdämpfung ohne eigene Konstante.

### 5.5 Bodeneffekt (Sanchez-Cuevas, ohne Rumpfterm; nur wenn `groundEffect` und `u.y > 0,5`)

```
Δy_frame = p.y − p.y(Frame-Anfang)                          der Raycast-Wert gilt am Frame-Anfang
z_i  = max(env.groundDistance − Δy_frame + (R·r_i).y, 0,5·R)
d    = 2s
den  = 1 − (R/(4z))² − R²·z/√((d² + 4z²)³) − ½·R²·z/√((2d² + 4z²)³)
f_ge = den ≤ 1/1,3 ? 1,3 : 1/den;   f_ge ← 1 + groundEffectScale·(f_ge − 1)
inGroundEffect = max_i f_ge,i > 1,02
```

FREESTYLE: f_ge(z = D/2) = 1,114, f_ge(z = D) = 1,044.

### 5.6 Prop-Wash

**Wirbelringzustand je Rotor** (mit Schub des vorigen Substeps `T_i,prev`, `T_hov = m·g/4`):
```
v_h,i  = √(max(T_i,prev, 1e−4)/(2·ρ·A·DUCT_GAIN_eff))
ν_i    = −v_ax,i/v_h,i                                   Sinkverhältnis entlang der Schubachse
μ_w,i  = v⊥,i/v_h,i                                      Querströmung bläst den Ring weg
w_vrs,i= exp(−((ν_i − 1)/0,5)²)·exp(−(μ_w,i/0,8)²)·clamp(T_i,prev/(0,25·T_hov), 0, 1)
w_i    = propWashScale·(1 − (1 − w_vrs,i)(1 − w_wake))   auf [0, 1] geklemmt   (w_wake = 0 ohne WakeField)
f_wash,i = (1 − WASH_LOSS·w_i)·(1 + WASH_NOISE·turbulenceScale·w_i·n_i)
```
`n_i`: je Rotor eigenes bandbegrenztes Rauschen mit Effektivwert 1: `n ← n + k·(√((2 − k)/k)·N − n)`,
`k = h/(h + 1/(2π·f_turb))`, `N` aus `SimRandom.gauss()`. Telemetrie `propWash = PT1(10 Hz)(max_i w_i)`.
Die ungleichen Schübe erzeugen über die Hebelarme Roll-/Nick-/Gierstörungen, die der PID sichtbar ausregelt; nichts
davon ist gescriptet.

**Wake-Feld (Phase 2, `WakeField`):** Ringpuffer mit 32 Paketen (primitive Arrays: Mitte, Geschwindigkeit, Stärke,
Radius, Alter). Alle 80 Substeps (40 ms), wenn `ΣT > 0,15·m·g`: neues Paket bei `c = p − 0,5·D·u` mit
`v_i = −v_c/2 + √((v_c/2)² + ΣT/(8ρA))` (`v_c = max(0, mittleres v_ax)`), Geschwindigkeit `wind_W − 2·v_i·u`,
Stärke `clamp(ΣT/(m g), 0, 2)/2`, Radius `r_0 = s + R`. Alle 8 Substeps (250 Hz): `c += vel·Δt`,
`vel ← wind + (vel − wind)·e^(−Δt/0,8 s)`, `r = r_0·(1 + 0,8·Alter)`, `st ← st·e^(−Δt/1,2 s)`, verwerfen bei
`st < 0,03`. Abfrage bei 250 Hz über Kapseln zwischen aufeinanderfolgenden Paketen, Pakete jünger als 0,15 s
übersprungen: `overlap = 1 − smoothstep(0,6r, 1,3r, Abstand)`,
`γ = 0,25 + 0,75·clamp((vel − v)·u/(0,5·v_h), 0, 1)`, `w_wake = max(st·overlap·γ)`. `teleport` verschiebt alle Pakete.

### 5.7 Rumpfwiderstand und Rotationsdämpfung

```
F_body,B = −½·ρ·C_d·dragScale·(A_x·u_x, A_y·u_y, A_z·u_z)·|u_B|        am CoM
           semi-implizit je Körperachse: F_a ← F_a/(1 + h·k_a/m),  k_a = ½·ρ·C_d·dragScale·A_a·|u_B|
           (in Luft < 0,1 % Unterschied; die Relativgeschwindigkeit kehrt sich nie um, auch nicht im Wasser)
τ_rot,B  = −c_rot·(ω_x|ω_x|, ω_y|ω_y|, ω_z|ω_z|)
```
Die Kraft ist nicht exakt entgegengesetzt zu `u` (schräg angeströmte Platte), es gilt aber immer `F·u ≤ 0`.

**Blätter im angetriebenen Sinkflug (seit 08.10., 0.5.0):** `A_y` enthält den Anteil der drehenden Blätter
(Windmühlen-Bremse im Leerlauf). Bei Anströmung von unten (`u_y < 0`) und ohne 3D-Modus gilt
`A_y,eff = A_y·(1 − 0,4·(1 − S(x)))` mit `x = −u_y / v_i`, `v_i = √(T_ges/4 / (2ρ·A_Scheibe·Duct-Gewinn))` und
`S` = Smoothstep von 1,5 bis 3,0 (`ModelConstants.BLADE_DRAG_SHARE`, `WINDMILL_SINK_LOW/HIGH`,
`DroneSim.descentTopScale`). Darunter rechnet das Rotormodell die Kräfte an den Blättern selbst (Schubgewinn im
Sinkflug, Wirbelring); ohne Schub ist `S = 1`. Herleitung: `docs/design/realism/flight-feel-gravity.md` §10.4.

### 5.8 Kräfte, Momente, Integration

```
F_B = Σ_i (T_i·ŷ + H_i) + F_body,B
τ_B = Σ_i r_i × (T_i·ŷ + H_i)  −  Σ_i σ_i·Q_react,i·ŷ  −  ω_B × h_rot  +  τ_rot,B
h_rot = Σ_i σ_i·J_eff·ω_i⁺·ŷ                                         Rotordrall (Kreiselmoment)
τ_ground,W: Weltmoment der Bodenruhe (Kap. 9.4), sonst 0

v    ← v + h·(R·F_B/m + (0, −g, 0))          g = 9,81·SimTuning.gravityScale (Standard 1,0)
p    ← p + h·v;     Δp_frame += h·v
L_W  ← L_W + h·(R·τ_B + τ_ground,W)
ω_W  = R·I⁻¹·Rᵀ·L_W
q    ← normalize(exp(½·h·ω_W) ⊗ q)          exp: θ = |ω_W|·h; θ < 1e−9 → (1, ½h·ω_W), sonst (cos θ/2, sin θ/2·ω̂)
ω_B  = I⁻¹·Rᵀ·L_W                          (mit neuem R)
```
Klemmen: `|v| ≤ 150 m/s`, `|ω_B| ≤ 100 rad/s` (L entsprechend skalieren).
Beschleunigungsmesser (spezifische Kraft) `f_B = F_B/m`: `gLoad = PT1(20 Hz)(f_B.y)/g₀` (Schweben 1,0),
`gLoadTotal = |f_B|/g₀`, immer in Normal-g (`g₀ = 9,81`), auch mit `gravityScale ≠ 1`.

### 5.9 Böen

Alle 8 Substeps (250 Hz), je Weltachse PT1-gefiltertes Rauschen, Zeitkonstante 1,2 s, Effektivwert
`σ = env.gustIntensity·(0,5 + 0,3·|wind|)·windScale·turbulenceScale`, vertikal halbiert (Formel wie `n_i` in 5.6).
Böen brauchen also Wind **und** Turbulenz: der Schalter „Turbulenz“ (`turbulenceScale = 0`) lässt den stetigen Wind
stehen und nimmt nur die Böen weg, „Wind“ (`windScale = 0`) nimmt beides weg (Korrektur 46).

### 5.10 Akkuzustand

```
mAhUsed += I_bat·h/3,6/batteryScale;   soc = infiniteBattery ? soc : max(−0,10, 1 − mAhUsed/C)
V_fast   ← V_fast + (R_fast·I_bat − V_fast)·(1 − e^(−h/0,5 s))    schneller Anteil: Einbruch wächst im Punch, kommt nach ~1 s zurück
V_pol    ← V_pol + (R_slow·I_bat − V_pol)·(1 − e^(−h/5 s))        langsamer Anteil (Koeffizienten vorberechnet)
Zelle unter Last U_L = V_bus/N, PT1 1 Hz gefiltert →
   U_L < emptyCellVoltage für ≥ 2 s   → BatteryState.LOW, Event BATTERY_LOW
   U_L < cutoffCellVoltage für ≥ 2 s  → CRITICAL, Event BATTERY_CRITICAL
   soc ≤ 0                           → EMPTY, Event PACK_DEPLETED (bleibt bis Reset)
   Rückweg (sim.BatteryAlarm): LOW/CRITICAL gehen wieder aus, wenn U_L 2 s lang über Schwelle + 0,1 V liegt –
   außer der Akku ist wirklich leer: Ruhespannung OCV(soc) ≤ Schwelle + 0,1 V hält die Stufe fest. Jede neue
   Unterschreitung meldet das Event erneut (Schubstoß mit vollem Akku: kurze Warnung, keine Dauerwarnung).
   infiniteBattery: keine Warnstufen, keine Events, Zustand immer OK; Sag wirkt weiter. Einschalten im Flug löscht
   gehaltene Stufen. (Vergleich Betaflight: realism/power-video.md §2.7.)
Brownout: V_bus < brownoutVoltage länger als 5 ms → Disarm BROWNOUT, 0,5 s Neustart-Sperre (bleibt gesetzt, solange
   V_bus darunter liegt). Event BROWNOUT einmal pro Unterspannungsphase: erneut erst, wenn V_bus zwischendurch über
   1,05·brownoutVoltage stieg, oder wenn die Drohne armed ist
```

### 5.11 Temperaturen (Phase 3, Standard nur Telemetrie)

Alle 8 Substeps mit exakter Exponentiallösung:
```
Motor: C = 0,5 J/(g·K)·m_mot;  P = I²·R_motor + K_t·I_0·ω;  G = (0,15 + 3e−4·ω)·(m_mot/33 g)^(2/3) W/K
ESC:   C = 0,3 J/(g·K)·m_stack/4;  P = I²·R_esc;  G = 0,02·(m_stack/18 g)·(1 + ω/ω_h) W/K
T ← T_∞ + (T − T_∞)·e^(−Δt·G/C),   T_∞ = T_amb + P/G
```
Mit `thermalEffects`: `R_motor(T) = R_motor,25·(1 + 0,00393·(T − 25))`; Event `MOTOR_OVERHEAT` über 150 °C
(Hysterese 10 K). Kein Hardware-Schaden in v1. Seit 0.4.0: Parameter je Motor aus `MotorThermal`, Drosselung und
Hitzeverschleiß über `derateStartC` (Kapitel 17.4).

### 5.12 Sonderfälle

- `env.fluid == WATER`: Widerstand ×400, Schub 0 nach 0,3 s, Disarm `FLUID`; `LAVA`: sofort Disarm `FLUID`.
  Der Rumpfwiderstand wird deshalb semi-implizit angewendet (5.5), sonst kehrt ×400 die Geschwindigkeit ab
  h·c·|u| > 1 um (WHOOP: ab 24 m/s).
- Disarmed: Brücke offen, Rotoren laufen mit Lagerreibung und Luftmoment aus.
- NaN-Wächter und Klemmen: Kap. 8.4.

---

## 6. Flight Controller

### 6.1 Takt und Signalweg

Der FC läuft auf jedem `divider`-ten Substep (`FilterSettings.pidLoop`: HZ_2000 → 1, HZ_1000 → 2, HZ_500 → 4),
`dt_pid = divider·h`. Alle FC-Filter werden mit `dt_pid` initialisiert. Pro FC-Tick:

```
Sticks (interpoliert, Kap. 8.3) → Deadband → PT2-RC-Glättung → Throttle-Kurve
      → Modus (ACRO / ANGLE / HORIZON) → Sollraten sp[3] [°/s] (+ Winkel-FF)
Gyro (Kap. 6.9) → PT1 gyroLpfHz → gyro_f[3]
PID je Achse → pid[3] [Mixer-Einheiten] → Mixer → dutyNext[4]
```

Das berechnete `dutyNext` wirkt ab dem **nächsten** Substep (1 Sample Latenz wie ein echter Loop); zwischen
FC-Ticks wird es gehalten. Disarmed: `duty = 0`, Brücke offen.

### 6.2 RC-Aufbereitung

1. **Deadband** (roll/pitch `rcDeadband`, yaw `yawDeadband`): `x' = sign(x)·max(0, |x| − db)/(1 − db)`.
2. **PT2-Glättung** auf roll, pitch, yaw, throttle mit `rcSmoothingHz` (Standard 40 Hz; 0 = aus). PT2 = zwei
   PT1 in Reihe mit je `f·1,553774` (−3 dB der Kaskade bei f). PT1: `y += k·(x − y)`, `k = dt/(dt + 1/(2π·f))`.
3. **Throttle-Kurve** (`throttleMid` m, `throttleExpo` e): `Δ = t − m`, `y = Δ > 0 ? 1 − m : (Δ < 0 ? m : 1)`,
   `t' = m + Δ·(1 − e + e·Δ²/y²)`. Limit: `OFF` → t', `SCALE` → t'·limit, `CLIP` → min(t', limit).
   Ergebnis `thr ∈ [0, 1]`. Referenz m = 0,4, e = 0,5: t = 0,7 → 0,5875; t = 0,2 → 0,275.

### 6.3 Rates (Stick x ∈ [−1, 1] → °/s, öffentlich dokumentierte Formeln, eigenständig implementiert)

**BETAFLIGHT** (`rcRate` r, `superRate` sr, `rcExpo` e):
```
a  = |x|                                   (vor dem Expo)
x' = x·a³·e + x·(1 − e)
r' = r > 2 ? r + 14,54·(r − 2) : r
ω  = 200·r'·x';   falls sr > 0: ω = ω / clamp(1 − a·sr, 0,01, 1)
```
**ACTUAL** (`centerRateDps` C, `maxRateDps` M, `actualExpo` e):
```
ω = x·C + max(0, M − C)·|x|·(x⁵·e + x·(1 − e))
```
Beide ungerade und monoton; Ergebnis auf ±1998 °/s geklemmt. `RateCurves.maxRateDps` = Wert bei x = 1.

| x | BF 1,0/0,70/0 | BF 1,0/0,70/0,5 | ACTUAL 70/670/0 | ACTUAL 70/670/0,5 |
|---|---|---|---|---|
| 0,25 | 60,61 | 30,78 | 55,00 | 36,32 |
| 0,50 | 153,85 | 86,54 | 185,00 | 114,69 |
| 1,00 | 666,67 | 666,67 | 670,00 | 670,00 |

Zusätzlich BF rcRate 2,5, super 0, x = 0,1 → 195,40 °/s.

### 6.4 ACRO

`sp[a] = Rates(x_a)` auf allen Achsen, Raten-FF (6.7) an allen Achsen.

### 6.5 ANGLE (Kippvektor, singularitätsfrei, linear invertierbar)

```
φ_t = x_roll·angleLimitDeg,  θ_t = x_pitch·angleLimitDeg            linear, ohne Expo (nach RC-Glättung)
u_t,H = (−sin φ_t,  cos φ_t·cos θ_t,  cos φ_t·sin θ_t)               Ziel-„oben“ im Heading-Rahmen (X links, Z vorn)
u_t   = R_y(ψ_t)·u_t,H                                                ψ_t = Twist-Heading (Kap. 2.2)
u     = q·ŷ
c     = u × u_t;   α = atan2(|c|, u·u_t)
e_W   = |c| > 1e−9 ? c/|c|·α : (u·u_t < 0 ? (q·ẑ)·π : 0)             exakt kopfüber: um die Rollachse
e_B   = q⁻¹·e_W;   e_B.y = 0                                          Nivellieren nur über Roll/Nick
ω_lvl = K_a·e_B,   K_a = angleStrength/10  [1/s]
ω_ff  = (angleFeedforwardPercent/100)·rad(PT1_ff(dθ_t/dt), 0, PT1_ff(dφ_t/dt))    Körper x = Nick, z = Roll
ψ̇_cmd = Rates(x_yaw)  [°/s, positiv = Nase rechts]
k     = angleEarthRefPercent/100
ω_yaw = k·Rᵀ·(0, −rad(ψ̇_cmd), 0) + (1 − k)·(0, −rad(ψ̇_cmd), 0)      Gieren erdbezogen
ω_cmd = ω_lvl + ω_ff + ω_yaw;   |ω_cmd.x|, |ω_cmd.z| ≤ rad(angleMaxRateDps)
sp    = FcAxes.toFc(ω_cmd)
```
`PT1_ff` = PT1 mit `feedforwardSmoothingHz`. In ANGLE ist der Raten-FF (6.7) auf Roll/Nick **aus** (der
Winkel-FF ersetzt ihn), auf Gieren an. Der Raten-PID-I-Term gleicht CoM-Versatz und Wind aus, der bleibende
Winkelfehler geht gegen 0. Aus Rückenlage wird auf kürzestem Weg aufgerichtet.

### 6.6 HORIZON

```
sp_acro = Rates(x) (wie ACRO, Raten-FF an)
w       = (horizonStrength/100)·clamp(1 − max(|x_roll|, |x_pitch|)/(horizonStickTransitionPercent/100), 0, 1)
                               ·clamp(1 − tiltDeg/horizonLimitDeg, 0, 1)
e_B     = Kippfehler wie ANGLE mit dem Stick-Ziel φ_t = x_roll·angleLimitDeg, θ_t = x_pitch·angleLimitDeg
sp      = sp_acro + w·(angleStrength/10)·deg(FcAxes(e_B))           nur Roll/Nick
```
Wie Betaflight bis 4.4 (`errorAngle = stick·angle_limit − attitude`): der Nivellier-Anteil zieht zum Stick-Winkel,
nicht nach waagerecht, deshalb neigt HORIZON bei kleinem gehaltenem Stick mindestens so weit wie ANGLE
(FREESTYLE, Roll-Stick 0,1/0,2/0,3: ANGLE 6/12/18°, HORIZON 9/24/59°). Mittelstick: nivelliert; Vollausschlag:
reines ACRO (Flips möglich); in Rückenlage zieht HORIZON nicht zurück.

### 6.7 Raten-PID je Achse (Betaflight-Struktur)

Skalen (BF-Zahlen, Fehler in °/s, Ausgang in Mixer-Einheiten):
`Kp = P·0,032029e−3`, `Ki = I·0,244381e−3`, `Kd = D·0,000529e−3`.
**F ist physikalisch normiert:** `F = 100` bedeutet ideale Trägheits-Vorsteuerung dieses Builds,
`Kf = (F/100)·(π/180)/b_ff` mit `b_ff` = `authorityRoll`, `authorityPitch`, `authorityYawInstant`.

```
err   = sp − gyro_f
relax = Achse in itermRelax ? max(0, 1 − |sp − PT1_relax(sp)|/itermRelaxThresholdDps) : 1     PT1_relax: itermRelaxCutoffHz
AG    = Roll/Nick: 1 + (antiGravityGain/20)·clamp(8·|thr − PT1_5Hz(thr)|, 0, 1);  Gieren: 1
dI    = Ki·err·relax·AG·dt_pid
I     = saturatedPrev && sign(dI) == sign(I) ? I : clamp(I + dI, ±itermLimit/1000)
I     = iTermActive ? I : 0
tpa   = 1 − (tpaRatePercent/100)·clamp((thr − tpaBreakpoint)/(1 − tpaBreakpoint), 0, 1)
P     = Kp·err·(tpaMode == PD ? tpa : 1)
g_D   = PT1_d2(PT1_d1(gyro_f))                         dtermLpf1Hz, dtermLpf2Hz (0 = Stufe aus)
D     = −Kd·(g_D − g_D,prev)/dt_pid·tpa                D auf Messung, kein Setpoint-Kick
spd   = PT1_ff((sp_ff − sp_ff,prev)/dt_pid)            sp_ff = Raten-Anteil von sp (ANGLE Roll/Nick: 0)
F     = Kf·spd;   falls sign(spd) == sign(sp) und |sp| > (ffMaxRateLimitPercent/100)·maxRate:
        F ·= clamp((maxRate − |sp|)/((1 − ffMaxRateLimitPercent/100)·maxRate), 0, 1)
sum   = P + I + D + F
pid   = clamp(sum, ±(Achse == YAW ? pidSumLimitYaw : pidSumLimit)/1000)
satSum= |sum| ≥ Grenze
```

`saturatedPrev` = `satSum` oder Mixer-Sättigung (6.8) des vorigen Ticks.
`iTermActive = armed && (airmode ? airmodeLatched : thr > 0,05)`; `airmodeLatched` rastet ein, sobald
`thr ≥ airmodeStartThrottlePercent/100` seit dem Armen. Beim Disarm werden I, alle Filter und Latches
zurückgesetzt; beim Armen starten die Filter auf dem aktuellen Gyro-Wert.

### 6.8 Mixer

```
a_i   = mixRoll_i·pid_r + mixPitch_i·pid_p + yawSign·mixYaw_i·pid_y        yawSign = propsOut ? −1 : 1
range = max a − min a;  sat = false;  falls range > 1: a_i /= range, sat = true     Autorität vor Gas
thrustLinearPercent tl > 0:  thr_c = k·thr² + (1 − k)·thr,  k = tl/100     (sonst thr_c = thr)
airmode && airmodeLatched:  t = clamp(thr_c, −min a, 1 − max a)
sonst:                      t = thr_c > 0,5 ? min(thr_c, 1 − max a) : thr_c
o_i   = t + a_i;  o_i ∉ [0, 1] → sat = true;  o_i = clamp(o_i, 0, 1)
thrustLinearPercent tl > 0:  o_i = √(o_i/k + B²) − B,  B = (1 − k)/(2k)
Sag-Kompensation (Betaflight-Art: Ausgangsbereich verkleinern, nach dem Mixer wird nichts geklemmt):
      g   = clamp((V_bus,f/N − U_empty)/(U_full − U_empty), 0, 1),   V_bus,f = PT1(1 Hz)(V_bus)
      att = (vbatSagCompensationPercent/100)·(U_full − U_empty)/U_full·g        U_full/U_empty: Zellchemie
d_i   = idle + (1 − idle)·(motorOutputLimitPercent/100)·(1 − att)·o_i,   idle = motorIdlePercent/100     (armed)
```

`thr_c` ist die exakte Umkehr der Linearisierung am Gaspunkt: ohne Regleranteil gilt `o_i = thr`, das Schwebegas
bleibt mit Thrust-Linearisierung gleich (Betaflight kompensiert das Gas ebenso vor dem Mixer), linearisiert wird nur
die Differenz der Motoren. Die Sag-Kompensation verkleinert bei vollem Akku den Ausgangsbereich (LiPo: bis −16,7 %)
und gibt ihn mit sinkender Spannung frei; die Airmode-Reserve des Mixers bleibt erhalten.

Referenzfälle (Idle 5,5 %, Gas 0):

| Fall | Eingang | Ergebnis d_1..d_4 |
|---|---|---|
| A: Airmode aktiv, Roll +0,2 | a = (−0,2, −0,2, 0,2, 0,2) | t = 0,2; d = (0,055; 0,055; 0,433; 0,433) |
| B: Airmode aus, Roll +0,2 | gleich | t = 0; d = (0,055; 0,055; 0,244; 0,244) |
| C: Airmode aktiv, R 0,5, P 0,4, Y 0,2 | a = (−0,3; −0,7; 1,1; −0,1), range 1,8 | a/1,8; t = 0,3889; o = (0,2222; 0; 1; 0,3333); d = (0,2650; 0,055; 1; 0,3700); sat = true |

### 6.9 Gyro-Modell

```
gyro_raw = FcAxes.toFc(ω_B)·(180/π)
         + gyroNoiseDps·N                                       weiß, je Achse
         + Σ_i a_i·(cos φ_i, sin φ_i, ±0,5·(cos φ_i | −sin φ_i))  Vibration je Rotor (Roll, Pitch, Yaw; Yaw: gerade i cos, ungerade −sin)
a_i      = gyroVibrationDps·√(2/4)·(1 + 3·propWash)·(ω_i/ω_h)²·g_AA(ω_i)·g_dec(ω_i)
φ_i      = θ_i + i·137,5°,   θ_i += ω_i·h je Substep           Rotorwinkel, feste Phasenlage je Motor
g_AA(ω)  = (1 − x²)² für x = ω·h/π < 1, sonst 0                 Anti-Aliasing: bis zur Nyquist-Grenze des 2-kHz-Takts auf 0
g_dec(ω) = min(1, |sin(N·ω·h/2) / (N·sin(ω·h/2))|)              N = PID-Teiler (1/2/4): Mittelung über die Gyro-Samples
gyro_f   = PT1(gyroLpfHz)(gyro_raw)                             0 = aus
```
Die Vibration ist deterministisch (keine Zufallszahlen) und liegt bei der Drehfrequenz jedes Rotors: im Schweben
140–730 Hz je nach Build (LONG_RANGE bis WHOOP), bei Vollgas darüber; ab 1 kHz (Nyquist des 2-kHz-Takts) blendet
`g_AA` sie aus, statt sie als Scheinfrequenz ins Regelband zu falten. Bei unterschiedlich schnellen Rotoren ist der
Effektivwert auf Roll und Pitch im Mittel wie vorher `gyroVibrationDps·(ω/ω_h)²·(1 + 3·propWash)`, auf Yaw die Hälfte.
Gyro-PT1, D-Term-Filter und die Trägheit von Motoren und Rahmen lassen von dieser Frequenz kaum etwas bis zur Lage
durch, genau wie beim echten Quad. Rauschwerte aus `SimTuning`; in Tests 0. Kein Beschleunigungsmesser: ANGLE und
HORIZON nutzen die wahre Orientierung.

### 6.10 Arming, Disarm, Failsafe

Armen auf die **steigende Flanke** von `armSwitch`, wenn keine Sperre besteht, sonst Event `ARM_BLOCKED` mit
Bitmaske. Sperren werden zu Beginn jedes `step` neu ausgewertet und in `telemetry.armBlockers` gezeigt
(OSD „CANNOT ARM: …“).

| Bit | `ArmBlocker` | Bedingung |
|---|---|---|
| 0 | `ARM_SWITCH_NOT_RESET` | Schalter war bei `reset`, nach `requireArmSwitchReset`, nach Crash-/Failsafe-/Brownout-Disarm oder bei bestehender Sperre schon an; erst aus, dann an |
| 1 | `THROTTLE_NOT_LOW` | Throttle-Stick > `armThrottleMax` (0,05) |
| 2 | `TILTED` | Neigung > `armMaxTiltDeg` (25°) |
| 3 | `PACK_DEPLETED` | soc ≤ 0,03 |
| 4 | `BUILD_INVALID` | `!build.buildable()` |
| 5 | `CRASH_COOLDOWN` | < 1,0 s seit Crash-Disarm |
| 6 | `FAILSAFE` | `linkOk == false` |
| 7 | `REBOOTING` | < 0,5 s seit Brownout |
| 8 | `IN_FLUID` | `env.fluid != NONE` |
| 9 | `HARDWARE_FAULT` | Flugcontroller/Stack oder Akku kaputt (`WearEffects.hardwareFault`, 9.6) |
| 10 | `THROTTLE_NOT_CENTERED` | nur 3D (6.13): Gas außerhalb `0,5 ± deadband3d`; dafür entfällt `THROTTLE_NOT_LOW`, und `TILTED` gilt nur zwischen `armMaxTiltDeg` und 180° − `armMaxTiltDeg` |

`DisarmReason`: `NONE` (Startwert), `SWITCH, CRASH, BROWNOUT, FAILSAFE, FLUID, SIM_FAULT, FORCED, HARDWARE`.

**Failsafe:** `linkOk == false` länger als `failsafeDelayS` (0,3 s) → Stufe 1: ANGLE, Sticks mittig,
`throttle = hoverThrottleStick − 0,05` für `failsafeLandingS` (1,5 s) → Stufe 2: Disarm `FAILSAFE`. Kommt der Link
in Stufe 1 zurück, übernimmt der Pilot sofort. Der spätere Return-to-Home-Autopilot ersetzt Stufe 1 (er setzt
einfach selbst `PilotInput` und `linkOk = true`).

### 6.11 Autopilot-Kontrakt (Return-to-Home)

Der Autopilot befüllt `PilotInput` im Modus ANGLE:
- `roll/pitch = StickMath.angleToStick(zielWinkelDeg, pid)`: linear, der Zielwinkel ist nach der RC-Glättung exakt
  `stick·angleLimitDeg`.
- `yaw = StickMath.rateToStick(gierRateDps, rates, Axis.YAW)`: erdbezogene Gierrate (angleEarthRef 100 %).
- `throttle` aus eigener Höhenregelung um `telemetry.hoverThrottleStick` (Schwebe-Stick für den aktuellen
  Akkuzustand, 10 Hz aktualisiert, ohne Bodeneffekt).
- Garantien (Tests D5–D8): 30°-Sprung t90 ≤ 1,3 × Tabellenwert, Überschwingen ≤ 1°, bleibender Fehler ≤ 0,3°
  (mit 3 m/s Seitenwind ≤ 1°), Sinusfolge ±20°/0,5 Hz RMS ≤ 1°, Heading-Drift ohne Gier-Stick ≤ 2° in 10 s.
- Telemetrie liefert Lage, Heading, Geschwindigkeit, Vertikalgeschwindigkeit, Höhe über Grund und `attitudeErrorDeg`.

### 6.12 ALTHOLD und POSHOLD (`NavHold`, `Barometer`, `GpsReceiver`)

Nach Betaflight 4.6 ALTHOLD/POSHOLD und INAV: beide Modi erzwingen die Selbstnivellierung (ANGLE), laufen im FC
als eigener Navigationstakt mit **100 Hz** (`NavHold.DIVIDER` = 20 Substeps) und sind absichtlich **nicht perfekt**:
Sie regeln auf simulierte Sensoren, nie auf die wahre Position.

**Eingang:** `PilotInput.altHold`, `PilotInput.posHold` (POSHOLD schließt ALTHOLD ein), `SimEnvironment.skyView`
(0 = kein Himmel … 1 = freier Himmel, Standard 1; drone misst ihn mit `drone.client.SkyView`: 17 Strahlen, 96 Blöcke,
Zenit 1,5, 60° × 8 Gewicht 1, 30° × 8 Gewicht 0,6, `ClipContext.Block.COLLIDER` + Flüssigkeiten, alle 0,5 s; Dimension mit
Decke = 0). Eigener Zufallsgenerator (`seed` abgeleitet), damit der Rest der Simulation deterministisch gleich bleibt.

**Barometer:** Höhe = wahre Höhe + Drift (Gauss-Markov σ 0,35 m, τ 30 s) + Bodenfehler
`−0,45 m · clamp(T/W, 0, 2) · e^(−h/0,5 m)` unter 4 m + Staudruckfehler `+0,004 · v_Luft²` (m, v in m/s) +
Rauschen σ `0,3 m + 0,2 m · e^(−h/1 m)·T/W + 0,3 m · Prop-Wash`, danach PT1 mit 0,1 s Verzögerung.
**Beschleunigungssensor:** Specific Force der Simulation, Bias je Achse (σ 0,08 m/s², τ 60 s), Rauschen
σ 0,25 m/s² · (1 + 2 · Verschleiß-Vibration); horizontal um den Kompassfehler gedreht.

**GPS (`GpsReceiver`):** sichtbare Satelliten `(18 + 1,5 · Konstellation) · skyView^1,6` (Konstellation
Gauss-Markov σ 1, τ 150 s); verfolgte Satelliten steigen um 1,1/s (Kaltstart ab 0), fallen um 5/s; Himmel wird mit
τ 1,5 s geglättet. 3D-Fix ab 5 Satelliten, HDOP `(0,55 + 22/n²) · (1 + 1,2 · (1 − skyView))`. Messungen 10 Hz mit
180 ms Latenz (Ringpuffer der wahren Position), Fehler = Random Walk (Gauss-Markov τ 45 s, σ 1,3 m · HDOP) + weißes
Rauschen 0,4 m · HDOP, vertikal × 1,6; Geschwindigkeit ± 0,08 m/s · HDOP. Ohne Fix gibt es keine Messung; nach 1 s
ohne Fix ist die Horizontalschätzung ungültig.

**Kompass:** fester Kursfehler je Sitzung ±(1,5° + 6° · u²), mit 10 % Wahrscheinlichkeit zusätzlich 20–45°
(magnetische Störung → deutliches „Toilet Bowl“), dazu Wandern σ 1,5° (τ 40 s).

**Schätzer:** vertikal Komplementärfilter (Beschleunigung integriert, Korrektur mit Baro bzw. 80 % Baro + 20 %
GPS-Höhe bei Fix, Gewinne 2,4/s und 1,44/s²); horizontal Beschleunigung integriert, bei jeder GPS-Messung Korrektur
Position × 0,15, Geschwindigkeit × 0,15.

**Modi:** `requested` = POSHOLD, wenn `posHold`, sonst ALTHOLD, wenn `altHold`. Nicht scharf oder Failsafe-Stufe 1
→ OFF. POSHOLD nur mit Fix, `sats ≥ minSatellites` und gültiger Schätzung, sonst ALTHOLD und
`telemetry.posHoldDenied` (OSD „KEIN GPS“); kommt der Fix zurück, schaltet POSHOLD mit neuer Stelle wieder ein.
Beim Einschalten: Zielhöhe = geschätzte Höhe, Gas-Referenz = Knüppelmitte (`midThrottle`, Betaflight) oder die
Gasstellung beim Einschalten, geklemmt auf `Totband + 0,08 … 1 − Totband − 0,08` (Standard, INAV), damit Steigen und Sinken immer erreichbar bleiben; POSHOLD beginnt mit Bremsen.
Am Boden (Bodenkontakt) und ohne Steigwunsch gibt der Regler das Gas des Piloten durch und setzt Ziel und I zurück
(Landeerkennung): Einschalten am Boden hüpft nicht, Gas über die Haltestellung hebt ab, Gas zu landet und bleibt stehen.

**Höhenregler:** Gas außerhalb `Referenz ± throttleDeadbandPercent` → Steig-/Sinkrate linear bis
`climbRateCms`, Zielhöhe wandert mit (höchstens ±1 m vor der Schätzung). `v_soll = climb + kP·Δh` (geklemmt auf
die Steigrate), `a = kV·Δv + I` (I geklemmt ±4 m/s², a in −0,5 g … 0,8 g), Gas = `ThrottleCurve⁻¹(hover · √((1 + a/g)/cos Neigung))`.
`hover` ist der Schwebe-Stick der Simulation mit einem festen Kalibrierfehler von ±6 % je Sitzung, den das I langsam
ausgleicht. kP = 0,9 · altP/50, kV = 2,2 · altD/40, kI = 0,9 · altI/30.

**Positionsregler:** Roll/Nick über `stickDeadbandPercent` → der Pilot steuert selbst (ANGLE), Ziel folgt der
Schätzung; nach dem Loslassen bremsen (Soll-Geschwindigkeit 0), bis die geschätzte Geschwindigkeit < 0,4 m/s oder
3 s vergangen sind, dann gilt die neue Stelle. Sonst `v_soll = kP·Δp` (höchstens 4 m/s), `a = kV·Δv + I` (I nur
ungesättigt und nicht beim Bremsen, höchstens 2,5 m/s²), begrenzt auf `g · tan(posMaxAngleDeg)`. Die Beschleunigung
wird mit dem geschätzten Kurs (wahr + Kompassfehler) in Nick/Roll umgerechnet (`atan(a/g)`), der Stickwert =
Winkel/`angleLimitDeg`, vor dem FC um das RC-Totband vorverzerrt (`DroneSim.undeadband`). kP = 0,5 · posP/30,
kV = 1,4 · posD/40, kI = 0,35 · posI/20. Gier bleibt beim Piloten.

**Ergebnis (JUnit `HoldModesTest`, FREESTYLE, sechs Seeds):** ALTHOLD bleibt 30 s in ±2 m (typisch ±0,3–0,6 m,
Standardabweichung > 3 cm), steigt/sinkt mit ≤ 1,5 m/s; POSHOLD bleibt 30 s innerhalb 4 m (typisch 1–2 m); ohne
Himmel (skyView 0,15) kein Fix → ALTHOLD + `posHoldDenied`; Satellitenverlust bricht POSHOLD ab, freier Himmel holt es
zurück; 6 m/s Wind versetzt die Drohne, sie kriecht zurück; Start und Landung mit eingeschaltetem ALTHOLD; 50° Kompassfehler kreist sichtbar (> 1 Umlauf in 60 s).

Konfiguration `FlightControllerConfig.hold` (`HoldSettings`, Tuning-Schlüssel `hold.*`):

| Feld | Standard | Bereich |
|---|---|---|
| `altP`, `altI`, `altD` | 50, 30, 40 | 0–200 |
| `climbRateCms` | 150 | 20–1000 cm/s |
| `throttleDeadbandPercent` | 10 | 2–40 % |
| `midThrottle` | false | Knüppelmitte statt Gas beim Einschalten |
| `posP`, `posI`, `posD` | 30, 20, 40 | 0–200 |
| `posMaxAngleDeg` | 20 | 5–45° |
| `stickDeadbandPercent` | 8 | 2–30 % |
| `minSatellites` | 8 | 5–12 |

Telemetrie: `holdMode`, `holdRequested`, `posHoldDenied`, `posHoldReady`, `gpsSatellites`, `gpsFix`, `gpsHdop`,
`altitudeEstimate`, `baroAltitude`, `holdTargetAltitude`, `holdPositionError` (wahr, nur Tests/Debug),
`gpsPositionError` (wahr, nur Tests/Debug), `headingErrorDeg`. `teleport` und `copyStateFrom` verschieben die
Schätzungen mit, `reset` setzt sie zurück (Satelliten bleiben).

### 6.13 3D-Modus (umkehrbare Motoren, Propwash 0.5.0)

Ausführlich mit Betaflight-Vergleich und Entscheidungen in `docs/design/mode-3d.md`. Kurzfassung des Vertrags:

- `PilotInput.mode3d` schaltet den FC auf 3D; `PilotInput.throttle` bleibt die Knüppelstellung 0…1, 0,5 ist die Mitte.
  Failsafe Stufe 1 und ALTHOLD/POSHOLD fliegen ohne 3D (`Telemetry.mode3d` = falsch).
- Gas: Totzone `FlightControllerConfig.deadband3d` (0,05 = ±50 µs, `[0; 0,2]`). Unter `0,5 − d` Richtung rückwärts,
  Betrag `(0,5 − d − t)/(0,5 − d)`; über `0,5 + d` vorwärts, Betrag `(t − 0,5 − d)/(0,5 − d)`; dazwischen Betrag 0 in der
  letzten Richtung (unscharf vorwärts). Auf den Betrag wirkt die Gaskurve, Airmode und TPA wie vorwärts; Anti-Gravity
  folgt dem Wert mit Vorzeichen. Nach jeder Umkehr bleibt der I-Anteil 0,25 s aus.
- Rückwärts bekommt der Mischer `−roll, −pitch, −yaw`, das Tastverhältnis ist `−(idle + range·o)`; die Hitze-Drossel
  begrenzt den Betrag.
- Scharf schalten: Gas `|t − 0,5| ≤ d` (sonst `ArmBlocker.THROTTLE_NOT_CENTERED`), Neigung ≤ `armMaxTiltDeg` oder
  ≥ 180° − `armMaxTiltDeg`.
- Motor und Rotor (nur solange `reversible`: 3D gewählt, ein Rotor dreht rückwärts oder ein Tastverhältnis ist negativ,
  sonst unveränderter Rechenweg): Strom und Begrenzung für die Fahrtrichtung gespiegelt; `ω ∈ [−ω_max; ω_max]`;
  Luftmoment `kQ·fq·ω·|ω|` mit `kQ_rück = (0,6 + 0,4·r)·kQ`; unter `0,04·ω_max` Antriebsmoment 40–100 %. Schub
  `sign(ω)·kT_dir·…·ω²` mit `kT_rück = r·kT`, ohne Duct-Gewinn, Einströmverhältnis gegen die Schubrichtung mit
  `v₀_rück = √r·v₀`; H-Kraft, Wirbelring und Biegeverlust mit Beträgen; Bodeneffekt je Rotor, wenn seine Schubrichtung
  nach unten zum Boden zeigt; Nachlauf mit der Schubrichtung des Gesamtschubs. `r` = `AirframeSpec.reverseThrust` aus
  `PropDefinition.reverseThrust()`, ohne Angabe 0,55.
- Telemetrie: `mode3d`; `throttleOut` mit Vorzeichen (−1…1); `motorRpm`, `motorThrustN`, `motorCurrentA`,
  `totalThrustN`, `thrustToWeight` mit Vorzeichen der Drehrichtung.

---

## 7. Automatische PID-Ableitung (`PidAutoTune`)

```java
public final class PidAutoTune {
    public static PidProfile derive(AirframeSpec params, FilterSettings filters, PidProfile base);
}
```
Liefert eine Kopie von `base`, in der die **abgeleiteten Felder** ersetzt sind: P/I/D/F aller Achsen,
`angleStrength`, `tpaBreakpoint`, `airmodeStartThrottlePercent`, `source = DERIVED`, `derivedForBuild = buildKey`,
`derivedForFilters = "<pidLoop>@<gyroLpfHz>"`.
Alle anderen Felder bleiben. Deterministisch, < 1 ms, gecacht pro (Build, pidLoop, gyroLpfHz).
`FlightControllerConfig.defaultsFor(params)` = Standardwerte + `derive`. Ändert der Spieler an der Werkbank Teile
oder im Configurator `pidLoop`/`gyroLpfHz` und ist `source == DERIVED`, wird neu abgeleitet (Schlüssel
`derivedForBuild` + `derivedForFilters`; stehen Filteränderungen armed noch aus, fliegt der FC bis zum Disarm mit den
für die aktiven Filter abgeleiteten Werten); bei `CUSTOM` bleibt sein Tune, der Configurator bietet
„auf abgeleitete Werte zurücksetzen“.

**Regel** (Pol-Kompensation der Motorzeitkonstante durch D, Durchtrittsfrequenz durch Totzeit und Motor begrenzt):
```
T_d  = 1,5/f_pid + 0,5/f_phys + 1/(2π·gyroLpfHz)                (2 kHz, 250 Hz: 1,637 ms)
ω_c  = min(150, 0,40/T_d, 4/τ_m)                                  [rad/s]
Roll/Nick (b = b_roll bzw. b_pitch):
  Kp = ω_c/b;   Kd = Kp·min(τ_m, 0,040);   Ki = Kp·clamp(0,06·ω_c, 4, 12);   F = 120
Gieren:
  Kp = min(2,0·ω_c/b_yaw,inst, 40/b_yaw,stat);   Ki = 10·Kp;   Kd = 0;   F = 120
BF-Werte: P = Kp/(0,032029e−3·57,29578),  I = Ki/(0,244381e−3·57,29578),  D = Kd/(0,000529e−3·57,29578)
          gerundet, geklemmt P ∈ [5, 200], I ∈ [5, 250], D ∈ [0, 150]
angleStrength             = clamp(round(10·ω_c/14), 20, 120)
tpaBreakpoint             = min(0,9, max(0,35, hoverStick(soc 0,5) + 0,15)), auf 0,01 gerundet
airmodeStartThrottlePercent = clamp(round(90·hoverStick(soc 1,0)), 10, 25)
```

Ergebnis für die Presets (2-kHz-Loop, Standardfilter):

| | WHOOP | CINEWHOOP | FREESTYLE | RACER | LONG_RANGE |
|---|---|---|---|---|---|
| ω_c [rad/s] | 100 | 150 | 150 | 150 | 150 |
| Roll P/I/D/F | 21/17/52/120 | 60/71/95/120 | 42/49/31/120 | 33/39/31/120 | 55/65/75/120 |
| Pitch P/I/D/F | 22/17/53/120 | 62/73/97/120 | 43/51/32/120 | 35/41/32/120 | 58/68/79/120 |
| Yaw P/I/D/F | 57/74/0/120 | 145/190/0/120 | 51/67/0/120 | 48/63/0/120 | 126/165/0/120 |
| angleStrength | 71 | 107 | 107 | 107 | 107 |
| tpaBreakpoint | 0,70 | 0,51 | 0,38 | 0,35 | 0,43 |
| airmodeStart [%] | 25 | 25 | 19 | 15 | 21 |
| itermRelaxCutoffHz | 7 | 15 | 15 | 15 | 15 |
| Phasen-/Amplitudenreserve Roll | 72° / 17,1 dB | 63° / 13,5 dB | 63° / 13,0 dB | 63° / 13,1 dB | 63° / 13,4 dB |

Über alle 380 baubaren Werkbank-Kombinationen: Phasenreserve ≥ 62°, Amplitudenreserve ≥ 12 dB (2 kHz); bei
1 kHz/500 Hz ≥ 56° bzw. ≥ 9 dB. Das große D beim Whoop (D > P) ist die physikalische Folge des trägen Motors.

**Reserven-Rechnung (für Test D16):**
`L(jω) = (Kp + Ki/(jω) + Kd·jω/(1 + jω/ω_d))·b/(jω·(1 + jω·τ_m))·e^(−jω·T_del)/(1 + jω/ω_g)` mit
`T_del = 1,5/f_pid + 0,5/f_phys`, `ω_g = 2π·gyroLpfHz`, `ω_d = 2π·dtermLpf1Hz`; 3000 log-verteilte Punkte
2–20 000 rad/s, Phase entrollt; ω_c = letzter Abwärtsdurchgang durch |L| = 1, PM = 180° + ∠L(ω_c), GM beim ersten
∠L ≤ −180° oberhalb 0,5·ω_c.

---

## 8. Zeitschritt, Determinismus, Performance

### 8.1 Konstanten

`h = 0,5 ms` (`SUBSTEP_NANOS = 500 000`), `MAX_SUBSTEPS = 100`, `MAX_FRAME_DT = 0,05 s`.

### 8.2 `step`

```
step(double dt, …):      events.clear(); dt nicht endlich oder < 0 → 0; weiter mit advance(round(min(dt, 0,2)·1e9), …)
step(long frameNanos, …): events.clear(); weiter mit advance(max(frameNanos, 0), …)
advance(long frameNanos, in, env, out):
  0  frameNanos > 50 ms → Event FRAME_HITCH (Wert = ursprüngliches dt), frameNanos = 50 ms
  1  Eingaben säubern (Sticks NaN → 0 und klemmen; env: NaN-Wind → 0, groundDistance NaN → +∞,
     airDensity klemmen)
  2  accNs += frameNanos;  n = accNs / SUBSTEP_NANOS;  falls n > 100: n = 100, accNs = 0 (Zeitdehnung statt Aufholen)
     sonst accNs −= n·SUBSTEP_NANOS
  3  Frame-Snapshot für den NaN-Rollback (≈ 120 doubles, System.arraycopy in ein vorab angelegtes Array)
  4  Stick-Rampe setzen (8.3); Arming-Logik (Flanken, Sperren, Failsafe-Timer, Modus); p_y0 = p.y für den Bodeneffekt
  5  für k = 0 … n−1:
       a  Sticks interpolieren; falls (fcTick++ % divider == 0): FlightController.update → dutyNext[4]
       b  Luftgrößen (5.1), Bus (5.2) mit dutyCur, Rotoren (5.3), Schub/Kräfte (5.4–5.7)
       c  Bodenruhe (9.4) falls grounded; Integration (5.8) falls nicht schlafend
       d  Akku (5.10); Brownout-Zähler
       e  jeder 8. Substep (feste Zählung): Böen, Wake-Feld, Thermik, Akku-Warnfilter; jeder 200.: Schwebe-Stick-Schätzung
       f  NaN-Wächter (8.4); Telemetrie-Maxima; dutyCur = dutyNext
  6  Anzeige-Extrapolation: τ = accNs·1e−9;  p_vis = p + v·τ;  q_vis = exp(½τ·ω_W) ⊗ q
  7  out.dx/dy/dz = p_vis − p_vis,prev;  p_vis,prev = p_vis;  out.orientation = q_vis;  out.substeps = n;  Telemetrie
```

Wake-Feld, Böen, Rauschen und Warnfilter laufen ausschließlich auf der Substep-Uhr, nie auf der Frame-Uhr. Nur so ist
der Zustand bei konstanten Eingaben unabhängig davon, wie die Zeit auf Frames verteilt wird (Test G6).

### 8.3 Stick-Interpolation

Pro `step`-Aufruf: `from` = zuletzt angewendeter Wert (Ende der vorigen Rampe), `to` = neue Eingabe. Im Substep k
von n: `x = from + (to − from)·(k + 1)/n`. Bei n = 0 bleibt die Rampe offen (die Änderung geht nicht verloren).
Schalter (`armSwitch`, `mode`, `linkOk`) wirken ab dem ersten Substep des Aufrufs.

### 8.4 Robustheit

- **NaN-Wächter** nach jedem Substep: `isFinite(Σ p + Σ v + Σ L + Σ q + Σ ω_i + V_bus)`. Sonst: Rollback auf den
  Frame-Snapshot, v = L = 0, Disarm `SIM_FAULT`, Event, Abbruch des Frames. Zusätzlich wird jeder abgeleitete Zustand
  außerhalb des Snapshots verworfen (Schub des Vorschritts, Prop-Wash-Gewichte, Wake-Pakete, Böen, Gyro-Modell,
  g-/Wash-/Zellfilter, FC-Filter und I-Terme, Wicklungswiderstände), damit ein einmaliges NaN nicht über Rückkopplungen
  in jedem weiteren Substep wieder auftaucht; der Schub des Vorschritts wird nur endlich gespeichert.
  > 3 Fehler pro Sekunde → harter Reset (aufrecht, in Ruhe, an der aktuellen Position).
- **Klemmen:** |v| ≤ 150 m/s, |ω_B| ≤ 100 rad/s, ω_i ∈ [0, ω_max], soc ∈ [−0,10, 1], Temperaturen ∈ [−40, 250] °C,
  f_ge ≤ 1,3.
- `MoveReport` mit NaN oder einer nicht endlichen Korrektur `actual − requested` auf irgendeiner Achse wird ignoriert
  und in `telemetry.faults` gezählt.
- `copyStateFrom` repariert nicht endliche Felder (Position: aktuelle, v/L/ω: 0, Lage: aktuelle, Temperaturen:
  Umgebung, soc: aktueller, Busspannung: Leerlaufspannung) und zählt die Reparatur in `telemetry.faults`.
- Alle Konfigurationen laufen beim Setzen durch `sanitize()` (Kap. 11).

### 8.5 Determinismus

- `StrictMath` für sin, cos, atan2, exp, pow, cbrt, log; `Math.sqrt/abs/min/max` sind IEEE-exakt und erlaubt; kein
  `Math.fma`.
- Interne Rechnung auf primitiven `double`-Feldern (Vektoren als x/y/z-Felder, Quaternion als w/x/y/z,
  Motoren als `double[4]`). JOML-Typen nur an der API-Grenze (Kopie in `Quaterniond`/`Vector3d`), weil JOML je nach
  Systemeigenschaften (`joml.fastmath`, `joml.useMathFma`) andere Rechenwege nimmt.
- Zufall nur aus `SimRandom` (xorshift64*, `long`-Zustand im `SimSnapshot`); feste Ziehreihenfolge pro Substep:
  Wash-Rauschen M1–M4, Gyro weiß x/y/z, (jeder 8.) Böen x/y/z. Die Gyro-Vibration zieht keine Zufallszahlen.
  `gauss()` = (u1 + u2 + u3 + u4 − 2)·√3 (Summe von 4 Gleichverteilungen, Varianz 1).
- Gleicher Seed + gleiche Eingabe- und dt-Folge + gleiche `MoveReport`s → bitgleicher Zustand.

### 8.6 Allokation und Kosten

- In `step` und `afterMove`: kein `new`, kein Autoboxing, keine Lambdas, keine Iteratoren, keine Strings, kein
  `EnumSet.of`, keine Varargs. Events als Ringpuffer mit primitiven Feldern. Alle Filter, Arrays und Hilfsvektoren
  werden im Konstruktor angelegt.
- Filterkoeffizienten (`k`, `e^(−h/τ)`) werden bei Konfigurations- oder Parameterwechsel vorberechnet, nie im Substep.
- Budget: ≈ 1 200 Gleitkomma-Operationen, ≈ 12 `sqrt`, 3–5 `StrictMath`-Aufrufe pro Substep → ≈ 1,5 µs nach dem
  JIT. 60 fps ≈ 33 Substeps ≈ 50 µs pro Frame; Worst Case 100 Substeps < 0,2 ms.

---

## 9. Kollisionen und Bodenkontakt

### 9.1 Kontrakt Entity ↔ Sim (pro gerendertem Frame, Client des Piloten)

```
1  env befüllen: Raycast nach unten (5 m) → groundDistance, Wind, Fluid
2  sim.step(frameDt, input, env, result)
3  vorher = Position; entity.move(MoverType.SELF, Vec3(result.dx, result.dy, result.dz)); actual = Position − vorher
   (move ist void; nur wenn canSimulateMovement(); MC löst achsweise auf; QuadcopterEntity#move clippt zusätzlich gegen
   Gate-Formen — siehe docs/api/network-entity.md, Abschnitt 2.4 und Gate-Kollision)
4  report: requested = result.d*, actual, hitA = |actual_a − requested_a| > 1e−7, surfaceGrip/Restitution
5  sim.afterMove(report)
6  xo/yo/zo = x/y/z (keine zusätzliche MC-Interpolation); Rendering nutzt result.orientation
```
Entity-Hitbox fest aus `AirframeSpec` (Breite, Höhe, `hitboxFootOffset`), `maxUpStep = 0`, keine Schwerkraft
und kein Widerstand durch Minecraft. Bei Pause wird `step` nicht aufgerufen. Bei sehr schnellem Flug darf das
Entity den Frame in Scheiben aufteilen (jeweils `step` + `move` + `afterMove`); empfohlen ab 0,5 m pro Frame.

### 9.2 Stoß pro getroffener Achse (in `afterMove`)

```
1  p += actual − requested                                        Entity ist autoritativ für die Position
2  Achsen mit Treffer, sortiert nach |v_a| absteigend (v_a = impactVelocity_a falls endlich, sonst v_a)
   n = −sign(requested_a)·ê_a  (requested_a == 0 → Achse überspringen)
3  Kontaktpunkt r (Welt, relativ zum CoM): unter den 12 Hüllpunkten R·r_k das Minimum von (R·r_k)·n;
   Mittelwert aller Punkte innerhalb 3 mm davon (flache Landung → Mitte → kein Scheindrehmoment)
4  v_c = v + ω_W × r;  v_n = v_c·n;  v_n ≥ 0 → nichts tun (trennt sich)
5  v_imp = −(v·n)  (CoM-Normalgeschwindigkeit vor dem Stoß, für die Klassifikation 9.3)
6  s = min(|v_n|, v_app);  e = s < 0,4 ? 0 : clamp(e0'·restitutionScale·(1 − 0,6·smoothstep(0,3·v_cr, v_cr, s)), 0, 0,95)
       e0' = surfaceRestitution ≥ 0 ? min(surfaceRestitution, 1) : e0,  v_cr = impactTolerance·crashSpeedScale
       v_app = ∞, außer bei anhaltendem Kontakt (siehe unten)
7  K_n = 1/m + n·((I_W⁻¹(r × n)) × r),  I_W⁻¹ = R·I⁻¹·Rᵀ
   j_n = −(1 + e)·v_n/K_n;   v += j_n·n/m;   L_W += r × (j_n·n)
8  Reibung: v_c neu; v_t = v_c − (v_c·n)·n; |v_t| > 1e−6: t̂ = v_t/|v_t|, K_t analog,
   j_t = min(μ0·surfaceGrip·j_n, |v_t|/K_t);   v −= j_t·t̂/m;   L_W −= r × (j_t·t̂)
9  Prop-Strike prüfen (9.5); Achse Y mit n.y > 0 → groundedFrames = 2, Bodenhöhe merken
10 ω_B aus L_W neu; |Δω| begrenzt auf 30 rad/s pro Stoß; Schlafzustand verlassen, wenn |j_n| > 0,05·m m/s
```
Außermittige Treffer (Propspitze, Akkukante) erzeugen über `r × j` von selbst Drall → Taumeln.

**Anhaltender Kontakt:** Drückt die Drohne unter Schub gegen Decke oder Wand, baut sich innerhalb jedes Frames
v·n = a·dt auf, das das Entity blockiert. Ohne Gedächtnis wäre das jeden Frame ein neuer Stoß mit v_imp = a·dt, also
bei 20 fps `HARD_IMPACT` in jedem Frame und Crash-Disarm ohne echten Aufprall. Deshalb merkt sich `afterMove` je Achse
die Normale der letzten Treffer (2 Frames). Trifft dieselbe Achse mit derselben Normale erneut, zählt für Klassifikation
(9.3) und Stoßzahl nur die Annäherung zu Frame-Beginn `v_app = max(0, −v_Frame-Beginn·n)`, also nicht die im Frame
aufgebaute Geschwindigkeit. Der Stoß selbst nimmt weiter die ganze Normalgeschwindigkeit heraus (die Reibung entspricht
so im Mittel μ·Normalkraft). `teleport` löscht das Gedächtnis.

### 9.3 Klassifikation und Folgen

| Ereignis | Bedingung (v_cr = impactTolerance·crashSpeedScale) | Folge |
|---|---|---|
| `CRASH` | `v_imp ≥ v_cr` oder (`abs(v_t,CoM) ≥ 1,5·v_cr` und `v_imp ≥ 0,5`); bei anhaltendem Kontakt `v_imp = min(v_imp, v_app)` | Event (Wert = v_imp); armed und `crashDisarm` → Disarm `CRASH`, Sperren `CRASH_COOLDOWN` + `ARM_SWITCH_NOT_RESET` |
| `HARD_IMPACT` | `0,5·v_cr ≤ v_imp < v_cr` | Event (Kamera/Sound) |
| `BUMP` | `1 m/s ≤ v_imp < 0,5·v_cr` | Event |
| `LANDED` | Übergang in grounded mit `v_imp < 1,5 m/s`, Neigung < 30° | Event, kein Spam (Hysterese über groundedFrames) |
| `TAKEOFF` | grounded endet | Event |

Nach einem Crash läuft die Physik weiter: Motoren laufen aus, der Rahmen fällt und taumelt mit dem Drall aus dem
Stoß, prallt ab und kommt über 9.4 zur Ruhe.

### 9.4 Bodenruhe (in den Substeps, solange `groundedFrames > 0`)

`groundedFrames` wird bei jedem Y-Treffer mit n = +Y auf 2 gesetzt und in jedem `afterMove` ohne solchen Treffer um
1 verringert. Ohne Treffer bleibt es bei 2, solange die Hitbox-Unterkante auf der gemerkten Bodenhöhe liegt (±2 mm),
`v.y ≤ 0` ist **und** `supportGap ≤ 2 mm` meldet, dass wirklich etwas darunter liegt. Liegt sie auf der Bodenhöhe, aber
ohne Auflage (über eine Kante gerutscht, Block darunter entfernt, Server-Reset in die Luft), endet grounded sofort
(`TAKEOFF`) und die Schwerkraft wirkt im nächsten Frame. Mit `N = max(0, m·g − (R·ΣT·ŷ).y)` (Normalkraft):

1. **Bodenbedingung:** `v.y < 0` und `p.y ≤ Bodenhöhe − hitboxFootOffset + 1 mm` → `v.y = 0`.
2. **Gleitreibung horizontal:** `Δv = μ0·surfaceGrip·N/m·h` entgegen `v_xz`, ohne Vorzeichenwechsel.
3. **Aufrichten** (nur wenn `|ω_B| < 4 rad/s` und `ΣT < 0,5·m·g`): Ziel ±Y, je nach Vorzeichen von `u.y`
   (aufrecht oder Rückenlage);
   `τ_W = k_r·(u × (±ŷ))_horizontal − c_r·ω_W,horizontal`, `k_r = m·g·s`, `c_r = 2·√(k_r·Ixx)`.
4. **Gierreibung:** `τ_W.y −= μ0·surfaceGrip·N·s·tanh(ω_W.y/0,2)`.

### 9.5 Schlafzustand und Prop-Strike

**Schlafen:** disarmed, grounded, `|v| < 0,05 m/s` und `|ω| < 0,1 rad/s` für 0,5 s → v = L = 0, Integration
pausiert (Motoren und Akku laufen weiter). Wecken durch Armen, Stoß, Ende von grounded, `teleport`, `setBuild` und
fehlende Auflage (`supportGap > 2 mm`, dann endet auch grounded). Ohne Auflagemessung (NaN) weckt wie früher der
Bodenabstand-Strahl (`groundDistance + hitboxFootOffset > 2 cm`).

**Prop-Strike** (nur `!ducted`, `SimTuning.propStrikes`): Kontaktpunkt ist eine Propspitze (Hüllindex 0–3), der Motor
dreht mit > 0,3·ω_h, und die Normale liegt nicht entlang der Körper-Unterseite (`(Rᵀn).y < 0,7`):
`ω_i ← 0,2·ω_i`, zusätzlicher Gier-Drall `ΔL_W = −σ_i·J_eff·0,8·ω_i,vorher·(R·ŷ)`, Event `PROP_STRIKE` (Motor i).
Den Prop-Schaden durch den Strike rechnet `CrashDamage` (9.6) nach Drehzahl² und Anfluggeschwindigkeit; die
Strike-Physik selbst ist seit 0.5.0 unverändert.

### 9.6 Aufprall-Protokoll und Bauteil-Verschleiß

**Protokoll (`ImpactLog`, `impacts()`):** Jeder aufgelöste Stoß mit `v_imp ≥ 1 m/s` oder einem Prop-Strike landet als
`ImpactSample(speed, tangential, normalX/Y/Z, contacts, struck, strikeSpin)` im Protokoll (16 Plätze, bei Überlauf
fliegt der schwächste Eintrag ohne Strike): `speed` = v_imp, `tangential` = |v_t| des Schwerpunkts, Normale im
**Körpersystem** (`Rᵀ·n`, zeigt vom Hindernis zur Drohne; Sim-Körper: +Z vorn, +X links), `contacts` = Bitmaske der
Hüllpunkte im Kontakt (0–3 Propspitzen je Sim-Motor, 4–7 Motor-Unterseiten, 8/9 Akku vorn/hinten, 10 Elektronik/Kamera,
11 Unterseite), `struck` = Bitmaske der Sim-Motoren mit Prop-Strike, `strikeSpin` = ω/ω_h des schnellsten
getroffenen Props. `sanitized()` klemmt Werte (Geschwindigkeit ≤ 150 m/s, Spin ≤ 3, Normale normiert, sonst +Y).

**Zustand (`build.PartWear`):** unveränderlich, je Bauteil 0 (kaputt) … 1 (neu), auf 1/1000 gerundet: vier Arme, Stack,
vier Motoren, vier Props (Positionen wie die Werkbank: vorne links, vorne rechts, hinten links, hinten rechts;
`PartWear.simMotor(position)` = 3, 1, 2, 0), Video, Akku. `damaged` < 0,9, `critical` < 0,4, `broken` = 0.
`slot(i)`/`fromSlots` folgen der Werkbank (Rahmen = schlechtester Arm, 2–5 Motoren, 6–9 Props, 10 Video, 11 Akku).

**Schaden (`CrashDamage.apply(wear, build, sample, strength, seed)`, reine Funktion, deterministisch je Seed):**
`v_eff = √(v_imp² + (0,3·v_t)²)`, Schwelle `v_0 = max(1,5; 0,2·impactTolerance)`, Schwere
`s = max(0, v_eff − v_0)/impactTolerance`, Dosis `d = s²·strength` (`strength` 0 … 3, 0 = kein Schaden). Je Bauteil
`Δ = d·Gewicht/Widerstand·Streuung(0,75 … 1,25)`; ab `v_eff ≥ impactTolerance` (Crash) mindestens 0,0015 (so
nimmt bei jedem Crash jedes Teil sichtbar Schaden).

| Bauteil | Widerstand | Gewicht (Richtung/Kontakt) |
|---|---|---|
| Prop | 7,5 | Spitze im Kontakt 1,5, Motor-Unterseite 0,6, sonst 0,35; Treffer von oben ≥ 1; vordere Props ≥ Frontanteil `max(0, −n_z)`; von unten ohne Spitze ≤ 0,25. Ergibt die **Last**, Verlust nach Material (unten) |
| Motor | 70 | Spitze 1,2, Unterseite 1,0, von oben 0,8, sonst 0,35; bei Spitzenkontakt oder Strike × `1 + 0,5·βσ` (steife Props geben den Stoß weiter, Carbon × 1,5) |
| Kamera/Video | 150 | `max(0,3; 1,5·Frontanteil)`, Elektronik im Kontakt 1,5 |
| Arm | 200 | Spitze oder Unterseite 1,5, sonst 0,3 |
| Stack | 450 | Unterseite im Kontakt 1,0, sonst 0,6 |
| Akku | 500 | Akkuseite getroffen (Akkupunkte oder oben/unten je nach Einbaulage) 1,2, sonst 0,5; bei getroffenem Akku und `d > 6` bläht er mit 15 … 60 % Wahrscheinlichkeit auf (− 0,3 … 0,6) |

**Props nach Material (seit 0.5.0, `CrashDamage.propLoss`, `sim.PropFailure`, Hintergrund
`docs/design/realism/prop-wear.md`):** Last `L = (d·Gewicht/7,5·Streuung + Strike)·(Ducts 0,35)/Haltbarkeit` mit
Strike `0,045·min(strikeSpin; 2)²·(0,2 + v_eff/impactTolerance)·strength·Streuung` für getroffene Motoren. Unter der
elastischen Grenze `L ≤ 0,08` bleibt der Prop heil. Darüber mit Überlast `ü = L − 0,08`, Sprödigkeit
`β = max(σ; smoothstep(0,95; 1,2; Steifigkeit)/√Haltbarkeit)` und Splittern `σ = smoothstep(1,3; 2,2; Steifigkeit)`:
Verlust `ü·((1 − β) + 0,25·β(1 − σ) + 0,05·βσ)`; mit Wahrscheinlichkeit `β(1 − σ)·(1 − e^(−ü/0,4))` zusätzlich ein Riss
von `0,3 + 0,3·u` (u gleichverteilt); mit Wahrscheinlichkeit `βσ·smoothstep(0,08; 0,8; L)` zerspringt der Prop (Verlust 1).
Drei Würfe je Prop und Aufprall, immer in derselben Reihenfolge (deterministisch je Seed). Anzeige im Tooltip
(`PropFailure.Mode`): `BENDS` (β < 0,3), `BENDS_OR_CRACKS` (β < 0,7), `CRACKS`, `SHATTERS` (βσ ≥ 0,5).

Im Mittel (Crashs mit 0,8 … 1,6·impactTolerance, vier Richtungen, Strikes mit 0,8 … 1,8 × Schwebedrehzahl an den
Spitzen im Kontakt, JUnit `PropWearTest`) wird ein offener PC-Prop nach etwa 17 Crashs kritisch und nach 26 kaputt
(weiches PC 28/44, Bi-Blend 22/33, hartes PC und Glasfaser 12/19, Carbon nach 72 auf einen Schlag); Ducts halten ihre
Props in diesem Bereich heil. Motoren, Kamera, Rahmen, Stack und Akku rechnen wie in 0.4.0 (im selben Aufbau wird der
Motor vorne links nach etwa 75 Crashs kritisch, mit Carbon-Props nach 65).

**Wirkung (`WearEffects`, über `setWear`):**
- Schub je Motor × `propThrust·motorThrust·armThrust`: abgenutzter Prop `1 − ((1 − β)·0,2 + β·0,1)·(1 − c)` (verbogen
  kostet mehr Schub als angebrochen), kaputter Prop 0,005;
  verbogener Motor `1 − 0,06·(1 − c)`; angeknackster Arm `1 − 0,03·(1 − c)`, gebrochener Arm 0,05.
- Last (Drehmoment) am Rotor × `propLoad`: `1 − 0,08·(1 − c)`, kaputter Prop 0,08 (dreht fast frei hoch, Gier-Moment
  fehlt → die Drohne ist nicht mehr fliegbar).
- Kaputter Motor (c = 0): Duty 0.
- Vibration je Position: Prop `(1 − β)·0,7·(1 − c)^1,5 + β·1,2·(1 − c)` (angebrochen schüttelt mehr; kaputt 1), Motor
  `0,7·(1 − c)`, Arm `0,3·(1 − c)`, Summe ≤ 4; β kommt aus `AirframeSpec.propBrittleness`; der
  Gyro bekommt `+3 °/s` Vibrationsrauschen je Vibrationseinheit, `telemetry.vibration = Summe·(ω̄/ω_h)²`.
- Stack < 0,3: zusätzliches Gyro-Rauschen bis 3 °/s. Akku: Innenwiderstand × `1 + 2·(1 − c)`.
- `hardwareFault` (Stack oder Akku = 0): Arm-Sperre `HARDWARE_FAULT`, armed → Disarm `HARDWARE`.

Wer den Zustand speichert, synchronisiert und den Schaden verrechnet, steht in `docs/FOUNDATION.md` §15
(„Bauteil-Verschleiß“); die Sim kennt nur `PartWear`.

---

## 10. Telemetrie und Events

### 10.1 `Telemetry` (öffentliche Felder, wiederverwendet, `copyTo(Telemetry)`)

```java
public final class Telemetry {
    public double time;
    public int substeps, armBlockers, failsafeStage, faults, crashCount, impactCount, propStrikeCount;
    public boolean armed, airmodeActive, grounded, sleeping, motorSaturated, inGroundEffect;
    public StabMode mode = StabMode.ACRO;
    public HoldMode holdMode = HoldMode.OFF, holdRequested = HoldMode.OFF;
    public boolean posHoldDenied, posHoldReady, gpsFix;
    public int gpsSatellites;
    public double gpsHdop, altitudeEstimate, baroAltitude, holdTargetAltitude, holdPositionError, gpsPositionError, headingErrorDeg;
    public DisarmReason lastDisarmReason = DisarmReason.NONE;
    public BatteryState batteryState = BatteryState.OK;
    public final double[] motorRpm = new double[4], motorOutput = new double[4], motorDuty = new double[4];
    public final double[] motorCurrentA = new double[4], motorThrustN = new double[4];
    public final double[] motorTempC = new double[4], escTempC = new double[4];
    public double totalCurrentA, packVoltage, cellVoltage, cellVoltageResting, mAhUsed, chargeFraction, powerW;
    public double propWash, propWashVrs, propWashWake, groundEffectFactor, totalThrustN, thrustToWeight;
    public double gLoad, gLoadTotal, gLoadMax, speed, airspeed, verticalSpeed, heightAboveGround;
    public double rollDeg, pitchDeg, headingDeg, tiltDeg;
    public final double[] gyroDps = new double[3], setpointDps = new double[3];
    public final double[] pTerm = new double[3], iTerm = new double[3], dTerm = new double[3], fTerm = new double[3];
    public double throttleOut, hoverThrottleStick, attitudeErrorDeg, lastImpactSpeed, vibration;
    public void copyTo(Telemetry dst);
}

public enum BatteryState { OK, LOW, CRITICAL, EMPTY }
public enum ArmBlocker {
    ARM_SWITCH_NOT_RESET, THROTTLE_NOT_LOW, TILTED, PACK_DEPLETED, BUILD_INVALID, CRASH_COOLDOWN, FAILSAFE, REBOOTING, IN_FLUID;
    public int bit();
    public boolean isSet(int mask);
}
public enum DisarmReason { NONE, SWITCH, CRASH, BROWNOUT, FAILSAFE, FLUID, SIM_FAULT, FORCED }
public enum SimEventType {
    ARMED, DISARMED, ARM_BLOCKED, CRASH, HARD_IMPACT, BUMP, PROP_STRIKE, LANDED, TAKEOFF,
    BATTERY_LOW, BATTERY_CRITICAL, PACK_DEPLETED, BROWNOUT, ESC_CURRENT_LIMIT, MOTOR_OVERHEAT,
    FAILSAFE, AIRMODE_ACTIVE, FRAME_HITCH, SIM_FAULT
}
```

Bedeutung und Einheiten:

| Gruppe | Feld | Einheit / Bedeutung |
|---|---|---|
| Zustand | `time`, `substeps`, `armed`, `mode`, `armBlockers` (int-Bitmaske, `ArmBlocker.isSet(mask)`), `lastDisarmReason`, `airmodeActive`, `failsafeStage` (0/1/2), `grounded`, `sleeping`, `faults` | s, –, … |
| Motoren (`double[4]`, Index M1..M4) | `motorRpm`, `motorOutput` (Mixer 0..1 vor Idle), `motorDuty` (ESC 0..1), `motorCurrentA` (Phase), `motorThrustN`, `motorTempC`, `escTempC`; `motorSaturated` | U/min, … |
| Akku | `totalCurrentA`, `packVoltage`, `cellVoltage` (unter Last), `cellVoltageResting` (OCV), `mAhUsed`, `chargeFraction` (= max(0, soc)), `powerW`, `batteryState` (OK/LOW/CRITICAL/EMPTY) | A, V, mAh, 0..1, W |
| Aero | `propWash` (0..1, PT1 10 Hz), `propWashVrs`, `propWashWake`, `inGroundEffect`, `groundEffectFactor`, `totalThrustN`, `thrustToWeight` (ΣT/(m·g) aktuell) | – |
| Flug | `gLoad` (Schweben = 1), `gLoadTotal`, `gLoadMax` (Maximum von `gLoadTotal` seit dem Armen, ungefiltert), `speed`, `airspeed`, `verticalSpeed`, `heightAboveGround` (Hitbox-Unterkante), `rollDeg`, `pitchDeg`, `headingDeg`, `tiltDeg` | g, m/s, m, ° |
| FC | `gyroDps[3]`, `setpointDps[3]`, `pTerm[3]`, `iTerm[3]`, `dTerm[3]`, `fTerm[3]` (Mixer-Einheiten·1000 = BF-„PID-Einheiten“), `throttleOut`, `hoverThrottleStick`, `attitudeErrorDeg` | °/s, … |
| Crash | `crashCount`, `impactCount`, `lastImpactSpeed`, `propStrikeCount` | –, –, m/s, – |
| Verschleiß | `vibration` (9.6) | – |

`impactCount` zählt jeden gemeldeten Stoß ab 1 m/s (`BUMP`, `HARD_IMPACT`, `CRASH`) seit dem Erzeugen der Sim, monoton wie
`crashCount`. Die Trick-Erkennung (`docs/design/freestyle-tricks.md`) erkennt daran Berührungen auch dann, wenn ein Frame in
mehrere `step`-Aufrufe zerfällt und die `EventQueue` dazwischen geleert wird.

### 10.2 `EventQueue` (Ringpuffer, 32 Plätze, primitiv)

```java
public final class EventQueue {
    public int size();
    public SimEventType type(int index);
    public double time(int index);
    public double value(int index);
    public int motor(int index);
    public int overflowCount();
}
```

| `SimEventType` | `value` | `motor` |
|---|---|---|
| `ARMED` | – | −1 |
| `DISARMED` | `DisarmReason.ordinal()` | −1 |
| `ARM_BLOCKED` | Bitmaske | −1 |
| `CRASH`, `HARD_IMPACT`, `BUMP` | Aufprallgeschwindigkeit [m/s] | −1 |
| `PROP_STRIKE` | Drehzahl vorher [U/min] | Motorindex |
| `LANDED`, `TAKEOFF` | Vertikalgeschwindigkeit | −1 |
| `BATTERY_LOW`, `BATTERY_CRITICAL`, `PACK_DEPLETED` | Zellspannung | −1 |
| `BROWNOUT` | Busspannung | −1 |
| `ESC_CURRENT_LIMIT` | Strom (höchstens 1× pro 0,5 s und Motor) | Motorindex |
| `MOTOR_OVERHEAT` | Temperatur | Motorindex |
| `FAILSAFE` | Stufe | −1 |
| `AIRMODE_ACTIVE` | – | −1 |
| `FRAME_HITCH` | ursprüngliches dt [s] | −1 |
| `SIM_FAULT` | – | −1 |

Verbraucher: OSD (Akku, Lage, Sperren), Motor-Audio (`motorRpm`, `motorCurrentA`, `propWash`), RTH (Lage,
Geschwindigkeit, Schwebe-Stick), Netzwerk (20 Hz: Position, Orientierung, `armed`, `motorRpm`, `chargeFraction`).

### 10.3 `SimSnapshot`

```java
public final class SimSnapshot {
    public double px, py, pz, vx, vy, vz, qw, qx, qy, qz, lx, ly, lz;
    public final double[] rotorOmega = new double[4], motorTempC = new double[4], escTempC = new double[4];
    public double soc, mAhUsed, polarizationVoltage, lastBatteryCurrent, batteryHeatCurrent, lastBusVoltage;
    public boolean armed, sleeping;
    public int groundedFrames;
    public long rngState, accumulatorNanos;
}
```
Nur der physikalische Zustand (Welt-CoM, Welt-Drehimpuls). `copyStateFrom` setzt FC-Filter und I-Terme konsistent
zurück (Filter starten auf dem aktuellen Gyro-Wert). Determinismus-Tests vergleichen alle Felder bitweise.

---

## 11. Konfigurationsobjekte

Alle Klassen: öffentliche, veränderliche, nicht finale Felder; parameterloser Konstruktor; Defaults im
Feld-Initialisierer; `copy()` (tief) und `sanitize()` (klemmt auf gültige Bereiche, ersetzt `null` durch Defaults).
Enums per Name. Keine Records, keine Getter-Pflicht → Gson/Codec-tauglich. Einheiten wie im Betaflight-Configurator
(°/s, %, Hz, Grad, BF-PID-Zahlen); Anteile 0..1 nur dort, wo auch BF Dezimalzahlen zeigt (Throttle-Mid/Expo,
TPA-Breakpoint, Deadband).

```java
public final class FlightControllerConfig {
    public int version = 1;
    public RateProfile rates = new RateProfile();
    public PidProfile pid = new PidProfile();
    public FilterSettings filters = new FilterSettings();
    public HoldSettings hold = new HoldSettings();
    public double armMaxTiltDeg = 25.0;
    public double armThrottleMax = 0.05;
    public double deadband3d = 0.05;
    public double failsafeDelayS = 0.3;
    public double failsafeLandingS = 1.5;
    public static FlightControllerConfig defaultsFor(AirframeSpec params);
    public FlightControllerConfig copy();
    public void sanitize();
}

public enum RatesType { BETAFLIGHT, ACTUAL }

public final class RateCurve {
    public double rcRate = 1.0;
    public double superRate = 0.70;
    public double rcExpo = 0.0;
    public double centerRateDps = 70.0;
    public double maxRateDps = 670.0;
    public double actualExpo = 0.0;
}

public final class RateProfile {
    public RatesType type = RatesType.ACTUAL;
    public RateCurve roll = new RateCurve();
    public RateCurve pitch = new RateCurve();
    public RateCurve yaw = new RateCurve();
    public double rcDeadband = 0.0;
    public double yawDeadband = 0.0;
}

public final class AxisPid {
    public double p, i, d, ff;
    public AxisPid() {}
    public AxisPid(double p, double i, double d, double ff) { this.p = p; this.i = i; this.d = d; this.ff = ff; }
}

public enum PidSource { DERIVED, CUSTOM }
public enum ItermRelaxMode { OFF, RP, RPY }
public enum TpaMode { D, PD }
public enum ThrottleLimitType { OFF, SCALE, CLIP }

public final class PidProfile {
    public PidSource source = PidSource.DERIVED;
    public String derivedForBuild = "";
    public String derivedForFilters = "";
    public AxisPid roll = new AxisPid(42, 49, 31, 120);
    public AxisPid pitch = new AxisPid(43, 51, 32, 120);
    public AxisPid yaw = new AxisPid(51, 67, 0, 120);
    public double pidSumLimit = 500, pidSumLimitYaw = 400, itermLimit = 400;
    public double feedforwardSmoothingHz = 30, ffMaxRateLimitPercent = 90;
    public double angleStrength = 107, angleLimitDeg = 60, angleFeedforwardPercent = 100;
    public double angleMaxRateDps = 500, angleEarthRefPercent = 100;
    public double horizonStrength = 50, horizonStickTransitionPercent = 75, horizonLimitDeg = 135;
    public ItermRelaxMode itermRelax = ItermRelaxMode.RP;
    public double itermRelaxCutoffHz = 15, itermRelaxThresholdDps = 40;
    public double antiGravityGain = 80;
    public TpaMode tpaMode = TpaMode.D;
    public double tpaRatePercent = 65, tpaBreakpoint = 0.35;
    public boolean airmode = true;
    public double airmodeStartThrottlePercent = 25;
    public double motorIdlePercent = 5.5, thrustLinearPercent = 0, vbatSagCompensationPercent = 0, motorOutputLimitPercent = 100;
    public boolean propsOut = false;
    public double throttleMid = 0.5, throttleExpo = 0.0;
    public ThrottleLimitType throttleLimitType = ThrottleLimitType.OFF;
    public double throttleLimitPercent = 100;
}

public enum PidLoopRate { HZ_2000, HZ_1000, HZ_500 }

public final class FilterSettings {
    public PidLoopRate pidLoop = PidLoopRate.HZ_2000;
    public double gyroLpfHz = 250, dtermLpf1Hz = 100, dtermLpf2Hz = 0, rcSmoothingHz = 40;
}

public final class SimTuning {
    public boolean propWash = true;  public double propWashScale = 1.0, turbulenceScale = 1.0;
    public boolean wakeField = true;
    public boolean groundEffect = true;  public double groundEffectScale = 1.0;
    public double translationalLiftScale = 1.0, dragScale = 1.0, windScale = 1.0;
    public boolean batterySag = true, infiniteBattery = false;
    public boolean crashDisarm = true;  public double crashSpeedScale = 1.0, restitutionScale = 1.0;
    public boolean propStrikes = true, thermalEffects = false, sleepEnabled = true;
    public double gyroNoiseDps = 0.2, gyroVibrationDps = 1.0;
    public double gravityScale = 1.0;          g = 9,81·gravityScale, gravity()
}
```

`gravityScale` kommt seit 0.5.0 aus der Welt-Regel `propwash:gravity_scale` (0–1000 % in 1-%-Schritten, Standard 100 % = 9,81 m/s²,
≠ 100 % = veränderte Physik; `docs/design/world-rules.md`). Er wirkt auf die Beschleunigung (5.8), die Bodenruhe (9.4), die
Schwebegas-Schätzung (`ω_h·√gravityScale`), `NavHold` und die GPS-Rettung; G-Last-Telemetrie, Wirbelring-/Wake-Bezug und
`BuildAnalysis` bleiben bei 9,81 m/s². Analyse und Begründung: `docs/design/realism/flight-feel-gravity.md`.

`SimTuning` gehört nicht in den Configurator, sondern in die Welt-Regeln (`server.rules.PhysicsRules`, seit 0.5.0):
`propWash`/`wakeField`, `groundEffect`, `batterySag`, `infiniteBattery` und `batteryScale` (Akkulaufzeit, Verbrauch ÷
Faktor, 1 = bitgleich), `crashDisarm`, `propStrikes`, `turbulenceScale`, `windScale` (Windstufe 0–3), `crashSpeedScale`,
`thermalLimits`/`escOvervoltage`, `gravityScale`.

**Wertebereiche für `sanitize()`**

| Feld | Bereich |
|---|---|
| rcRate / superRate / rcExpo | [0,01; 2,55] / [0; 0,99] / [0; 1] |
| centerRateDps / maxRateDps / actualExpo | [10; 1998] / [centerRateDps; 1998] / [0; 1] |
| rcDeadband, yawDeadband | [0; 0,2] |
| P / I / D / FF | [0; 250] / [0; 250] / [0; 200] / [0; 500] |
| pidSumLimit(Yaw) / itermLimit | [100; 1000] / [0; 1000] |
| feedforwardSmoothingHz / ffMaxRateLimitPercent | [5; 200] / [0; 100] |
| angleStrength / angleLimitDeg / angleFeedforwardPercent | [0; 200] / [10; 85] / [0; 200] |
| angleMaxRateDps / angleEarthRefPercent | [100; 1998] / [0; 100] |
| horizonStrength / horizonStickTransitionPercent / horizonLimitDeg | [0; 200] / [10; 100] / [10; 180] |
| itermRelaxCutoffHz / itermRelaxThresholdDps | [1; 50] / [5; 200] |
| antiGravityGain | [0; 250] |
| tpaRatePercent / tpaBreakpoint | [0; 100] / [0; 0,95] |
| airmodeStartThrottlePercent | [0; 50] |
| motorIdlePercent / thrustLinearPercent / vbatSagCompensationPercent / motorOutputLimitPercent | [0; 20] / [0; 100] / [0; 100] / [25; 100] |
| throttleMid / throttleExpo / throttleLimitPercent | [0,05; 0,95] / [0; 1] / [25; 100] |
| Filter-Frequenzen | 0 oder [20; 1000] Hz; rcSmoothingHz 0 oder [5; 200] |
| armMaxTiltDeg / armThrottleMax | [5; 180] / [0; 0,3] |
| SimTuning-Skalen | [0; 4]; crashSpeedScale [0,25; 10]; gravityScale [0,8; 1,5]; Rauschen [0; 20] |

---

## 12. `BuildAnalysis` für die Werkbank

```java
public record BuildAnalysis(
        double massGrams, double thrustToWeight, double maxThrustPerMotorGrams,
        double hoverThrottlePercent, double hoverCurrentA, double hoverFlightTimeMin, double cruiseFlightTimeMin,
        double fullThrottleCurrentA, double fullThrottleCellVoltage, double escLoadPercent, double batteryLoadPercent,
        double topSpeedAcroKmh, double topSpeedAngleKmh, double motorResponseMs, double rollAccelDps2,
        double crashSpeedMs, Set<BuildWarning> warnings) {
    public static BuildAnalysis of(DroneBuild build);
}
public enum BuildWarning { CANNOT_HOVER, UNDERPOWERED, SLUGGISH_MOTORS, HEAVY_SAG, ESC_CURRENT_LIMITED, BATTERY_OVERLOAD }
```

Dieselben Gleichungen wie die Laufzeit (geteilte statische Funktionen aus `RotorAero` und `PowerSystem`), stationär
gelöst in `OperatingPoints`; `warnings` als `Set.copyOf(EnumSet)`. Cache `ConcurrentHashMap<DroneBuild, …>`;
< 20 ms kalt pro Build, damit die Werkbank beim Überfahren eines Teils Differenzen zeigen kann
(„+1,8 Schub/Gewicht, −40 s Flugzeit“). Server-tauglich (reines Java).

| Wert | Definition |
|---|---|
| hoverThrottlePercent | Stick bei soc 0,5 (lineare Kurve, Idle 5,5 %, Polarisation stationär); > 100 → NaN und `CANNOT_HOVER` |
| hoverCurrentA | Akkustrom im Schweben bei soc 1,0 – seit 0.4.2 aus der Energiebilanz (Kap. 18) |
| maxThrustPerMotorGrams / thrustToWeight | alle Motoren Duty 1, soc 1,0, mit ganzem R_pack (schneller Anteil aufgebaut), ohne langsame Polarisation, statisch, Duct-Gewinn 1,15 |
| fullThrottleCurrentA / fullThrottleCellVoltage | derselbe Punkt; Strom seit 0.4.2 aus der Energiebilanz (Kap. 18), Zellspannung, `HEAVY_SAG` und `BATTERY_OVERLOAD` weiter aus der Flugphysik; `escLoadPercent = 100·I_phase/escCurrentPerMotor`, `batteryLoadPercent = 100·I_Akku/I_cont` |
| topSpeedAcroKmh | Horizontalflug, Vollgas, soc 0,8 mit stationärer Polarisation: größte Geschwindigkeit, bei der Vollgas die Höhe noch hält (Bisektion über die Neigung) |
| topSpeedAngleKmh | Horizontalflug mit Neigung = min(angleLimitDeg Standard 60°, Neigung aus ACRO), Gas so, dass die Höhe gehalten wird |
| hoverFlightTimeMin | seit 0.4.2: Schweben bis zur nutzbaren Kapazität oder Landespannung unter Last, Energiebilanz (Kap. 18.3) |
| cruiseFlightTimeMin | seit 0.4.2: Geradeausflug mit klassentypischer Reisegeschwindigkeit (Kap. 18.3); bis 0.4.1 bei 50 % von topSpeedAcro |
| mixed-/aggressiveFlightTimeMin | seit 0.4.2: Profile aus Reisen, Schubstoß und Leerlauf je Rahmenrolle (Kap. 18.3) |
| cruise-/mixedCurrentA, cruiseSpeedKmh, landingCellVoltage, avionicsPowerW | seit 0.4.2: mittlerer Akkustrom der Profile, Reisegeschwindigkeit, Zellspannung unter Last bei der Landung (gemischt), Grundlast |
| motorTempMinuteC / mixedMotorTempC | seit 0.4.2: Motortemperatur nach 60 s Vollgas im Stand bzw. am Ende eines gemischten Flugs (Thermik wie im Flug, 25 °C) |
| motorResponseMs | τ_phys (ungeklemmt; die Werkbank zeigt die echte Trägheit) |
| rollAccelDps2 | 0,5·b_roll in °/s² (Autorität bei halbem PID-Ausschlag) |

**Stationäre Löser** (Iterationszahlen verbindlich, damit Java und Prototyp übereinstimmen):
- Motor bei Duty d, Busspannung V, Anströmung: Bisektion über ω ∈ [0, ω_max], 60 Schritte, Nullstelle von
  `K_t·(min((dV − K_e ω)/R_m, I_max(V, ω)) − I_0) − Q(ω)`.
- Bus: Bisektion über V ∈ [0,05·E, E], 50 Schritte, mit `I_bat = 4·(R_m·I + K_e·ω)·I/V + P_aux/V`, `R_int(I_bat)`, V_pol stationär
  `R_1·I_bat` (falls polarisiert).
- Schweben: `ω_h` geschlossen, `I_h = k_Q·ω_h²/K_t + I_0`, gedämpfte Fixpunktiteration auf V (100 Schritte, Faktor ½).
- Horizontalflug bei Neigung θ und Geschwindigkeit V (Körpergleichgewicht): `ΣT = m·g·cos θ + D_y`,
  `m·g·sin θ = H + D_z` mit `D_y = ½ρC_dA_y·V²·sin θ`, `D_z = ½ρC_dA_z·V²·cos θ`, `H = KAPPA_H·ΣT·V·cos θ/(ω R + 5)`,
  `v_ax = V·sin θ`, `v⊥ = V·cos θ`; ω aus ΣT per Bisektion (40 Schritte), Fixpunkt über ΣT (12 Schritte);
  V(θ) per Bisektion über [0, 150] m/s (50 Schritte); Neigung per Bisektion (40 Schritte) mit
  `ΣT_nötig ≤ ΣT_voll(V, θ)`.

**Warnungen:** `CANNOT_HOVER` (T/W < 1,15), `UNDERPOWERED` (T/W < 2,0), `SLUGGISH_MOTORS` (τ_phys > 60 ms),
`HEAVY_SAG` (fullThrottleCellVoltage < cutoffCellVoltage), `ESC_CURRENT_LIMITED` (Phasenstrom bei Vollgas
≥ I_max(1) = min(I_lim, I_in)), `BATTERY_OVERLOAD` (Akkustrom > 1,3·I_cont). Sie ergänzen `DroneBuild.warnings()`; die Sim verbietet
nichts, sie simuliert die Folgen (Sag, Stromgrenze, Brownout, Wärme).

---

## 13. Kalibrierziele pro Preset

Stand nach der Kalibrierung vom 2026-09-23 (Teile-Daten aus Kap. 16 übernommen, Modelländerungen in den
Implementation notes 29–33, Messprotokoll und Plausibilitätsziele in `flight-model-characterization.md`). Die Werte
stammen aus der Java-Implementierung; die ursprünglichen Prototyp-Werte stehen in der Versionsgeschichte. Die Spalte
„Toleranz“ gilt für die Java-Implementierung. Stationäre Werte werden über `BuildAnalysis`/`OperatingPoints`
geprüft, dynamische über die volle `DroneSim` mit Testwelt.

| Kennwert | WHOOP | CINEWHOOP | FREESTYLE | RACER | LONG_RANGE | Toleranz |
|---|---|---|---|---|---|---|
| Masse [g] | 28,1 | 286,7 | 525,2 | 430,6 | 846,2 | ±0,01 g |
| **Schwebegas** Stick soc 1,0 / 0,5 [%] | **47,8 / 55,0** | **32,2 / 36,4** | **20,6 / 23,4** | **16,6 / 19,0** | **22,8 / 27,7** | ±1,5 Punkte |
| Schwebedrehzahl [U/min] | 43 669 | 24 463 | 10 509 | 9 438 | 8 216 | ±1 % |
| Schwebe-Akkustrom soc 1,0 [A] (seit 0.4.2 Energiebilanz) | 3,54 | 5,16 | 5,19 | 4,13 | 6,47 | ±3 % |
| **Max. Schub pro Motor [g]** | **22** | **335** | **1397** | **1407** | **1227** | ±3 % |
| **Schub/Gewicht** | **3,18** | **4,67** | **10,64** | **13,07** | **5,80** | ±3 % |
| Vollgas: Drehzahl [U/min] | 77 915 | 52 868 | 34 284 | 34 119 | 19 785 | ±1,5 % |
| Vollgas: Akkustrom [A] (seit 0.4.2 Energiebilanz) / Zelle unter Last [V] | 10,6 / 4,00 | 67,4 / 3,48 | 115,6 / 3,54 | 130,3 / 3,40 | 84,3 / 2,79 | ±3 % / ±0,05 V |
| **Topspeed ACRO [km/h]** (Neigung) | **36** (46,4°) | **89** (62,8°) | **140** (71,0°) | **147** (67,8°) | **108** (54,3°) | ±5 % / ±2° |
| **Topspeed ANGLE 60° [km/h]** | **36**¹ | **84** | **108** | **123** | **108**¹ | ±5 % |
| **Flugzeit Schweben** (Kap. 18) [min] | **3,9** | **5,6** | **11,1** | **11,8** | **28,5** | ±5 % |
| **Flugzeit Reisen** (18,5/30/55/60/63 km/h, höchstens Anteil von v_top) [min] | **3,5** | **5,2** | **7,9** | **7,8** | **18,1** | ±5 % |
| **Flugzeit gemischt / aggressiv** [min] | **2,75 / 2,3** | **4,1 / 2,7** | **5,7 / 4,1** | **2,9 / 2,2** | **15,0 / 9,7** | ±5 % |
| τ_m im Schweben [ms] (τ_phys) | 40,0 (50,5) | 26,0 | 12,3 | 15,2 | 22,6 | ±5 % |
| Punch 1 s Vollgas aus Schweben: v_y [m/s] / Spitzen-g (seit 0.5.0 Steigflug nach Impulstheorie) | 10,7 / 2,84 | 20,4 / 4,33 | 38,7 / 9,60 | 41,1 / 11,21 | 27,0 / 5,72 | ±10 % |
| Fallgeschwindigkeit flach, disarmed [m/s] | 6,8 | 15,2 | 16,4 | 18,8 | 18,0 | ±3 % |
| Topspeed geradeaus, frischer Akku (`infiniteBattery`, ohne `batterySag`) [km/h] | 52 | 95 | 148 | 153 | 114 | Bänder in `TopSpeedTest` |
| Sturzflug senkrecht, Vollgas, frischer Akku [km/h] | 63 | 111 | 161 | 166 | 127 | ≥ geradeaus, ≤ 1,15 × geradeaus (Whoop 1,25 ×, Cinewhoop 1,2 ×) |
| **Rollraten-Antwort** t90 0 → 500 °/s [ms], 144 fps | **111** | **76** | **49** | **56** | **76** | ≤ 1,25 × Wert + 5 ms |
| … bei 60 fps | 117 | 83 | 50 | 50 | 83 | ≤ 1,25 × Wert + 5 ms |
| Überschwingen Roll-Sprung [%] | 1,7 | 4,4 | 0,5 | 0,9 | 3,3 | ≤ 8 % |
| Gier-Sprung 0 → 300 °/s: t90 [ms] | 21 | 28 | 21 (60 fps: 83) | 21 | 28 | ≤ 150 ms, Überschw. ≤ 10 %, ±5 % nach ≤ 250 ms |
| ANGLE-Sprung 0 → 30°: t90 [ms] | 208 | 167 | 188 | 181 | 174 | ≤ 1,3 × Wert |
| Flip 360° bei vollem Roll-Stick [ms] | 583 | 576 | 569 | 569 | 576 | ≤ 620 ms |
| ANGLE-Sinus ±20°/0,5 Hz, RMS-Fehler [°] 144 / 60 fps | 0,39 / 0,73 | 0,45 / 0,87 | 0,49 / 0,92 | 0,48 / 0,91 | 0,46 / 0,89 | ≤ 1,0° / ≤ 1,5° |

¹ Neigung aus ACRO (46,4°, 57,1° bzw. 58,0°) liegt unter dem Winkel-Limit, ANGLE ist daher so schnell wie ACRO.

**RACER seit dem 11.10.2026 (0.5.0):** Das Race-Preset fliegt mit dem flachen Dreiblatt `p51_29_3b` (5,1×2,9×3) auf dem
True-X-Rahmen statt mit `p51_3b` (5,1×4,6×3). Die RACER-Spalten dieser Tabelle und der Tabelle in Abschnitt 4.9 zeigen
den neuen Stand (stationäre Zeilen, Punch, Topspeeds); die dynamischen Zeilen darunter (Rollraten-Antwort bis
ANGLE-Sinus) bleiben in ihren Toleranzen. Mit dem 4,6″-Prop auf demselben Rahmen gelten weiter die alten Werte
(Schub/Gewicht 13,30, Vollgas 34 683 U/min und 124,7 A, Topspeed 168 km/h, Punch 47,1 m/s, Flugzeit 12,1 / 8,6 / 3,1 /
2,4 min) – dieser Aufbau steht als `RACER_040` weiter in den Goldens. Herleitung und Quellen:
`docs/design/realism/motors-props.md` §12.

**Messdefinitionen.** Rollraten-Antwort: Schwebeflug in ACRO bei soc 0,5 (`infiniteBattery`), Schwebe-Stick,
Frames zu 1/144 s; zum Zeitpunkt t0 springt der Roll-Stick an einer Frame-Grenze auf den Wert für 500 °/s
(ACTUAL 70/670/0: 0,8564); t90 = Zeit bis die wahre Rollrate 450 °/s erreicht; Überschwingen = max. Rate/500 − 1
innerhalb 300 ms. Gieren analog mit 300 °/s (Stick 0,6556). ANGLE: Roll-Stick 0 → 0,5 (angleLimit 60°), t90 auf
den Rollwinkel. Flip: Roll-Stick 0 → 1, Zeit bis 360° Rollwinkel. Sinus: Roll-Stick = (20/60)·sin(2π·0,5·t),
RMS des Winkelfehlers ab t = 1 s über 4 s. Topspeed dynamisch: ANGLE mit Neigung θ (Stick θ/angleLimit), Höhe über
PI auf Throttle gehalten, Geschwindigkeit nach 15 s.

**Kalibrierprozess**, falls ein Wert sein Plausibilitätsband verlässt (Reihenfolge, damit Stellschrauben einander
nicht verdecken): 1. Masse/Geometrie → 2. `ct` (Schub/Gewicht) → 3. `cp` und `I_0` (Schwebestrom, Flugzeit) →
4. Motor-R und Trägheitsfaktoren (τ_m) → 5. Rumpfflächen und `KAPPA_H` (Topspeed) → 6. Akku-R (Sag). Globale
Konstanten nur ändern, wenn mehrere Presets gleichsinnig abweichen. Jede Änderung aktualisiert Kapitel 4.9, 7 und 13
und die Testwerte gemeinsam.

---

## 14. Testplan mit Abnahmekriterien

Wenn nicht anders angegeben: Seed 42, `gyroNoiseDps = gyroVibrationDps = 0`, `propWash = false`,
`wakeField = false`, abgeleitete PIDs, 2-kHz-Loop, soc 1,0. Testhilfen nur in `src/test`:
- **`FlatWorld`**: Ebene y = 0 plus optionale Wand (x = const); bewegt die feste Hitbox achsweise wie Minecraft
  (Y zuerst, dann X/Z), befüllt `MoveReport` (inkl. `supportGap`) und liefert `groundDistance`.
- **`BoxWorld`**: beliebige achsparallele Quader (Boden, Podeste, Mauern, Decken); Kollision wie Minecraft (Y zuerst,
  dann die größere Waagerechte), `supportGap` und `groundDistance` wie im Spiel.
- **`TestStand`**: fixiert die Pose (Integration aus) für Motor-, Akku- und Schubmessungen.
- **`AltitudeHold`**: PI-Regler auf Throttle (für Schwebegas, Topspeed, Tracking-Tests).

Keine Kommentare in Testcode; sprechende Testnamen.

### A — Formeln und Bausteine (exakt)

| # | Test | Kriterium |
|---|---|---|
| A1 | Rates-Tabelle 6.3 + rcRate 2,5 | ±0,01 °/s; `stickFor(rateDps(x)) = x` ±1e−6 für 1000 Werte |
| A2 | Throttle-Kurve Referenzwerte 6.2; SCALE/CLIP; `inverse(apply(t)) = t` | ±1e−9 |
| A3 | Mixer-Fälle A/B/C (6.8) inkl. `sat`; `propsOut` kehrt nur die Yaw-Spalte | ±1e−4 |
| A4 | PT1-Sprung nach τ = 1/(2πf): 63,2 %; PT2 bei f: −3 dB | ±0,5 % / ±0,3 dB |
| A5 | Konventionen: Schub M3/M4 ↑ → ω_z > 0 und linke Seite hebt sich; M1/M3 ↑ → ω_x > 0; CCW (M2/M3) Duty ↑ → Nase rechts (ω_y < 0); q = 90° um +Y → heading +90°, MC-yRot −90 | exakt / 1e−9 |
| A6 | Twist-Heading = ψ für 1000 zufällige Heading/Kipp-Kombinationen mit Roll 0; stetig bei Pitch-Durchgang durch ±90° | 1e−12 / Sprung < 1e−6 |
| A7 | OCV: Endpunkte = full/empty exakt, monoton, Tiefentladung bis −0,10 | exakt |
| A8 | Konfigurationen: Felder public und nicht final, `copy()` tief, `sanitize()` klemmt und ersetzt null, JSON-Roundtrip (Gson im Test-Classpath) | exakt |
| A9 | `SimRandom.gauss`: Mittelwert, Varianz über 10⁶ Ziehungen | 0 ±0,01, 1 ±0,02 |
| A10 | `EventQueue` Überlauf: 100 Events → 32 behalten, `overflowCount = 68`, keine Allokation | exakt |

### B — Ableitung und stationäre Kennwerte

| # | Test | Kriterium |
|---|---|---|
| B1 | Masse = `totalMassGrams()` für alle 6000 Kombinationen | ±1e−9 g |
| B2 | Tabelle 4.9: I, J, k_T, k_Q, K_t, R_m, I_0, I_lim, R_pack, v_h, τ, b_* | ±0,5 % |
| B3 | Kapitel 13, stationäre Zeilen (Schwebegas, Drehzahlen, Ströme, Schub, T/W, Topspeeds, Flugzeiten) über `BuildAnalysis` | Toleranzspalte |
| B4 | alle 6000 Kombinationen: `AirframeSpec` und `BuildAnalysis` endlich (NaN nur bei `CANNOT_HOVER` in hover-/Flugzeitfeldern) | exakt; Gesamtzeit < 30 s |
| B5 | `PidAutoTune` Presets = Tabelle 7 | ±1 BF-Einheit |
| B6 | Hitbox und Hülle = Tabelle 4.9 | ±1 mm |
| B7 | WHOOP τ_m = 40,0 ms, J_eff = 15,1e−9; alle anderen Presets J_eff = J | ±0,5 % |
| B8 | Werkbank-Konsistenz: `BuildAnalysis.of` zweimal → identisches Objekt aus dem Cache; < 20 ms kalt | exakt / Zeit |

### C — Physik im offenen Kreis

| # | Test | Kriterium |
|---|---|---|
| C1 | Freier Fall 1 s, disarmed, `dragScale = 0` | v_y = −9,81 ±0,005 m/s; Δy = −4,905 ±0,01 m |
| C2 | flach fallend disarmed 20 s | Endgeschwindigkeit Tabelle 13 ±3 % |
| C3 | TestStand Vollgas 2 s | Drehzahl, Akkustrom, Schub = Tabelle 13 ±2 % |
| C4 | TestStand Duty-Sprung Schweben → Schweben + 0,1 | 63-%-Zeit der Drehzahl = τ_m ±15 % |
| C5 | aktives Bremsen: Vollgas → Idle | Drehzahl < 50 % in < 3·τ_m; Akkustrom kurz negativ; disarmed-Auslauf bis 10 % mindestens 5× länger |
| C6 | ESC-Grenze: Build mit `ESC_UNDERSIZED`-Warnung, Vollgassprung | Phasenstrom nie > I_lim + 1e−9; Event `ESC_CURRENT_LIMIT` ≤ 2× pro Sekunde |
| C7 | Akku: konstant 10 A für 360 s | mAh = 1000 ±0,1 %; Sprung 0 → I_cont: Einbruch R_pack·I ±2 %, nach 25 s (R_pack + R_1)·I ±2 %, Erholung mit τ = 5 s ±10 % |
| C8 | Bus geschlossen vs. Bisektion; FREESTYLE-Vollgassprung | ±1 mV; keine Oszillation (≤ 1 Vorzeichenwechsel der Ableitung) |
| C9 | Bodeneffekt FREESTYLE z = D/2 / z = D / ∞ | 1,114 / 1,044 / 1,000 ±0,002 |
| C10 | Einströmung: f_ax(λ = 1) = 0, Sinken max. 1,3; H-Kraft entgegen u⊥ | exakt |
| C11 | VRS: Sinken 1,0·v_h bei Schwebeschub → w ≥ 0,95; 0,1·v_h → ≤ 0,02; 1,0·v_h + 1,5·v_h quer → ≤ 0,05 | – |
| C12 | Rumpfwiderstand: 10 000 zufällige Lagen/Geschwindigkeiten | F·u ≤ 0 |
| C13 | momentfreie Rotation, ω0 = (10, 20, 5) rad/s, anisotropes I, 10 s | abs(L) relativ 1e−12; Energiedrift < 0,5 %; abs(abs(q) − 1) < 1e−12 |
| C14 | Kreiselmoment: Nickrate bei drehenden Rotoren mit Netto-Drall | Roll-Beschleunigung = −(ω × h_rot)/I ±5 % |
| C15 | Gier-Kick: Duty-Sprung auf CCW-Rotoren | Spitzen-Gierbeschleunigung ≥ 3× stationärer Wert |
| C16 | Punch 1 s aus Schweben (ANGLE waagerecht, Throttle-Stick 1) | v_y und `gLoadMax` Tabelle 13 ±10 % |

### D — Flight Controller im geschlossenen Kreis (`DroneSim` + `FlatWorld`, alle 5 Presets)

| # | Test | Kriterium |
|---|---|---|
| D1 | Schweben ANGLE mit `hoverThrottleStick`, 4 s in 5 m Höhe | abs(v_y) ≤ 0,3 m/s, Neigung ≤ 0,5° |
| D2 | Schwebegas über `AltitudeHold`, 10 s gemittelt, soc 1,0 und 0,5 (`infiniteBattery`) | Tabelle 13 ±1,5 Punkte |
| D3 | Rollraten-Antwort (Messdefinition 13) 144 und 60 fps; Pitch analog | t90 ≤ 1,25 × Tabelle + 5 ms; Überschwingen ≤ 8 %; Restfehler nach 300 ms < 2 % |
| D4 | Gier-Sprung 0 → 300 °/s | t90 ≤ 150 ms, Überschwingen ≤ 10 %, ±5 % nach ≤ 250 ms |
| D5 | ANGLE 0 → 30° | t90 ≤ 1,3 × Tabelle; Überschwingen ≤ 1°; Fehler nach 1,5 s ≤ 0,3°; mit 3 m/s Seitenwind ≤ 1° |
| D6 | ANGLE-Sinus (Messdefinition 13) | RMS ≤ 1,0° (144 fps), ≤ 1,5° (60 fps) |
| D7 | ANGLE aus Rückenlage (170° Roll, Umschalten aus ACRO) | Neigung < 10° nach 1,0 s, keine NaN |
| D8 | ANGLE Heading: Gier-Stick 0, 10 s Vorwärtsflug mit 30° Nick | Heading-Drift ≤ 2°; Gier-Stick für 90 °/s bei 30° Querneigung → Kursrate ±5 % |
| D9 | HORIZON: Sticks mittig nach 30° Neigung | Neigung < 3° nach 1 s; voller Roll-Stick → > 360° in 1 s |
| D10 | Flip 360° | ≤ 620 ms |
| D11 | Airmode: aus dem Schweben Gas auf 0, Roll-Sollwert 300 °/s | Airmode: Rate ≥ 270 °/s nach 150 ms (WHOOP ≥ 200) und `throttleOut` > 0; ohne Airmode: `throttleOut` = 0, min(d) = Idle |
| D12 | I-Term am Boden: armed, Gas 0,1, 5 s | alle I = 0, `airmodeActive = false`, Bewegung < 1 cm |
| D13 | Topspeed dynamisch (Messdefinition 13): ANGLE 60° und ACRO-Neigung θ* − 2° | = stationärer Wert (bei gleicher Neigung) ±5 % |
| D14 | Anti-Windup: 1 s Vollausschlag gegen die Pidsum-Grenze, dann Stick mittig | Überschwingen der Gegenrichtung ≤ 10 % der vorherigen Rate |
| D15 | Arming: jeder `ArmBlocker` einzeln (genau dieses Bit, kein Armen, `ARM_BLOCKED`); Flanke; `ARM_SWITCH_NOT_RESET` nach Crash; `CRASH_COOLDOWN` 1,0 s; Failsafe Stufe 1 nach 0,3 s, Disarm nach 1,8 s | exakt ±1 Substep |
| D16 | PID-Reserven (Kap. 7) für alle 380 baubaren Kombinationen, Roll und Nick, bei 2 kHz, 1 kHz und 500 Hz | PM ≥ 50°, GM ≥ 8 dB |
| D17 | 1-kHz- und 500-Hz-Loop mit dafür abgeleiteten PIDs, Roll-Sprung | Überschwingen ≤ 12 %, keine Dauerschwingung |
| D18 | Akku: LONG_RANGE Vollgas | `BATTERY_CRITICAL` nach ≤ 5 s; WHOOP bei soc 0,06 Vollgas → `BROWNOUT` vor soc −0,10; 6S-Presets bis soc −0,05 nie Brownout |
| D19 | Werkbank-Robustheit: alle 380 baubaren Kombinationen mit abgeleiteten PIDs, 20 s ANGLE-Schweben (`AltitudeHold`) | abs(ω_B) < 3 rad/s nach 2 s, keine NaN — oder `CANNOT_HOVER` gesetzt |

### E — Kollisionen (`FlatWorld`)

| # | Test | Kriterium |
|---|---|---|
| E1 | FREESTYLE disarmed flach aus 1 m (v = 4,43 m/s) | erster Rückprall = e·v mit e aus 9.2 Schritt 6 (= 0,205) ±10 %; kein CRASH; Ruhe (abs(v) < 0,05) ≤ 2 s; Schlaf ≤ 3 s; Neigung < 2°; danach 5 s Drift < 1 mm |
| E2 | FREESTYLE armed frontal gegen die Wand mit 3 / 5 / 10 m/s (impactTolerance 8) | `BUMP` / `HARD_IMPACT`, jeweils weiter armed / `CRASH` + Disarm `CRASH` |
| E3 | Crash-Schwelle 0,99·v_cr / 1,01·v_cr, jeder Rahmen | kein CRASH / CRASH |
| E4 | Schrammen: 6 m/s tangential, 1 m/s normal | kein CRASH; Tangentialgeschwindigkeit sinkt gemäß μ |
| E5 | Außermittiger Treffer (nur rechte vordere Propspitze an der Wand, Vorwärtsflug 4 m/s) | abs(ω) > 3 rad/s direkt danach; Gieren nach rechts (ω_y < 0) |
| E6 | Prop-Strike offen / Duct | Event am richtigen Motor, ω_i ≤ 0,2·vorher / kein Prop-Strike |
| E7 | 45° gekippt auf den Boden (disarmed, 2 m/s) | nach 3 s abs(u.y) > 0,98 (Bauch oder Rücken), kinetische + potentielle Energie monoton fallend ±1 % |
| E8 | Landung armed mit 1 m/s, dann Gas 0 | `LANDED`, kein Crash, bleibt armed, abs(v) < 0,01 m/s nach 1 s, kein Zittern |
| E9 | `MoveReport` mit NaN | ignoriert, `faults` + 1, Zustand unverändert |
| E10 | `BoxWorld`: disarmed über eine 3-m-Kante gerutscht (2/4/8 m/s); auf die Mauerkrone gesetzt (6/14/20 m/s, sinkend 0,3/3 m/s); frontal und schräg (0/30/60°) mit 9–40 m/s gegen eine Wand; gegen die Decke; jeweils Crash-Disarm und Prop-Strikes an/aus | nie länger als 0,1 s ohne Auflage, ohne Sinken und ohne Schub ≥ 0,9·m·g; am Ende auf dem Boden, Lücke ≤ 1 cm, abs(v) < 0,1 m/s |
| E11 | `BoxWorld`: armed mit 0 / 50 / 70 % Schwebegas über eine 2-m-Blockkante; schlafende Drohne, Podest entfernt; Server-Reset neben das Podest (+4 m / −1,5 m) | sinkt hinter der Kante in 0,6 s um > 0,3 m, bleibt armed; fällt und liegt am Ende auf dem Boden |
| E12 | `BoxWorld`: armed, Höhe gehalten, leichter Stoß mit 3 m/s gegen die Wand | kein CRASH, bleibt armed und nicht grounded, Höhe ±0,5 m, mit Vollgas danach > 1 m Steigen in 0,5 s |

### F — Prop-Wash (`propWash = true`)

| # | Test | Kriterium |
|---|---|---|
| F1 | FREESTYLE ACRO: 6 m/s senkrecht sinken, dann Schwebegas, Sticks mittig | `propWash`-Spitze ≥ 0,7; Raten-Fehler-RMS in 0,5 s ≥ 3× Wert ohne Wash; Neigung nie > 30°; kein Crash |
| F2 | Vorwärtsflug 10 m/s | `propWash` ≤ 0,1 |
| F3 | Schweben 10 s mit Wash und Gyro-Rauschen (ANGLE) | RMS-Lagefehler < 1°, Motor-Sättigung < 5 % der Zeit |
| F4 | Phase 2 Wake: 1,5 s Vollgas senkrecht, Flip 180°, mit Gas 0,2 zurückfallen | `propWashWake`-Spitze ≥ 0,5 innerhalb 2 s; 10 s Horizontalflug 10 m/s → ≤ 0,1; nach 5 s Idle am Boden alle Pakete verfallen |

### G — Robustheit, Determinismus, Leistung

| # | Test | Kriterium |
|---|---|---|
| G1 | dt ∈ {0, 1e−9, 0,016, 0,05, 0,2, NaN, −1, +∞} | Zustand endlich, Substeps ≤ 100, `FRAME_HITCH` genau bei 0,2 (NaN, −1, +∞ zählen als 0) |
| G2 | NaN in Sticks und Umwelt | wie 0 bzw. Standard behandelt |
| G3 | Test-Hook setzt v = NaN | Rollback im selben Frame, `SIM_FAULT`, disarmed, nächster Frame sauber |
| G4 | 60 s Zufalls-Skript (Sticks, Modi, Crashes), dt zufällig 0–50 ms | endlich, abs(abs(q) − 1) ≤ 1e−9, ω_i ≤ ω_max, mAh monoton |
| G5 | Determinismus: zwei Instanzen, gleicher Seed, gleiche Eingaben/dt/Reports, 120 s mit Rauschen und Wash | `SimSnapshot` bitgleich (`doubleToRawLongBits`) |
| G6 | Frame-Split-Invarianz: konstante Eingaben, gleiche Gesamtzeit in ns als 60 fps / 144 fps / Zufalls-dt | bitgleich |
| G7 | Frameraten-Robustheit: gleiche Stick-Choreografie (5 s ACRO) bei 30/60/144 fps | Endposition ≤ 5 cm, Lage ≤ 1° |
| G8 | Leistung: 20 000 Substeps nach 2000 Frames Aufwärmen | < 40 ms auf dem Entwicklungsrechner (CI: nur loggen, harte Grenze 150 ms) |
| G9 | Allokation (`com.sun.management.ThreadMXBean.getThreadAllocatedBytes`): 10 000 Frames mit allen Effekten | ≤ 64 Byte pro Frame im Mittel |

---

## 15. Umsetzungsreihenfolge (ein Implementierer)

| Phase | Inhalt | Tests | Umfang (grob) |
|---|---|---|---|
| 1 | `SimMath`, `Pt1`/`Pt2`, `SimRandom`, `FcAxes`, `RateCurves`, `ThrottleCurve`, `StickMath`, alle Konfigurationsklassen mit `copy`/`sanitize` | A | 1 Tag |
| 2 | `ModelConstants`, `FrameGeometry`, `CellCurve`, `ParamDerivation`, `AirframeSpec`, `OperatingPoints`, `BuildAnalysis`, `PidAutoTune` | B, D16 | 2 Tage |
| 3 | `PowerSystem`, `RotorAero`, `BodyAero`, `GroundEffect`, `RigidBody`, `DroneSim.step` mit Akkumulator, Snapshot, NaN-Wächter | C, G1–G3, G6 | 3 Tage |
| 4 | `FlightController`, `RcProcessor`, `AxisPidController`, `Mixer`, `AttitudeController`, `Arming`, `GyroModel`, Telemetrie, Events | D | 3 Tage |
| 5 | `CollisionSolver`, `HullShape`, Bodenruhe, Schlafen, Prop-Strike, `FlatWorld` | E | 2 Tage |
| 6 | `PropWash` (VRS, Rauschen), Böen, Gyro-Rauschen | F1–F3, G4, G5, G7–G9 | 1 Tag |
| 7 (Phase 2) | `WakeField` | F4 | 1 Tag |
| 8 (Phase 3) | `Thermal` mit optionaler Rückwirkung | eigener Test: FREESTYLE Dauervollgas → Motor > 100 °C nach 60 s | ½ Tag |

Etwa 4 500 Zeilen Hauptcode und 2 500 Zeilen Tests. Jede Phase endet grün; `./gradlew :common:test` bleibt
lauffähig. Langsame Tests (B4, D19, G8) bekommen `@Tag("slow")`.

---

## 16. Offene Punkte, Datenempfehlungen, Erweiterungen

**Teile-Daten (`build`) — am 2026-09-23 übernommen.** Die Sim übernimmt die Enum-Werte unverändert. Die früheren
Empfehlungen dieses Kapitels wurden bei der Flug-Charakterisierung (`flight-model-characterization.md`) umgesetzt und
um die Punkte ergänzt, ohne die WHOOP, CINEWHOOP und LONG_RANGE ihre Plausibilitätsziele verfehlten:

| Teil | Feld | alt → neu | Begründung |
|---|---|---|---|
| `MotorType.M0802_22000` | `resistanceOhm` | 0,60 → 0,20 | reale 0802-Motoren liegen bei 0,1–0,3 Ω; mit 0,60 erreichte der WHOOP nur T/W 2,6 |
| `PropType.P31_4B` | `powerCoefficient` | 0,060 → 0,085 | 31-mm-Vierblatt bei kleiner Reynoldszahl weniger effizient (Kap.-16-Empfehlung) |
| `PropType.P3_3B` | `thrustCoefficient` / `powerCoefficient` | 0,105 / 0,058 → 0,090 / 0,085 | 3″-Dreiblatt mit Steigung = Durchmesser: viel Leistungsbedarf, wenig Standschub; ≈ 335 g pro Motor wie reale 1404-Messungen |
| `FrameType.WHOOP_65` | `massGrams` | 5,5 → 4,0 | reale 65-mm-Rahmen inkl. Canopy 3–4 g |
| `FrameType.CINEWHOOP_3` | `massGrams` | 95 → 150 | Ducts, Bumper und Kamerakäfig; reale 3″-Cinewhoops wiegen trocken 200–250 g |
| `BatteryType.B1S_300_HV` | `cellResistanceOhm` | 0,060 → 0,040 | aktuelle 300-mAh-HV-Zellen; sonst Brownout beim Hochdrehen bei halbem Akku |
| `BatteryType.B6S_4000_LION` | `cellResistanceOhm` | 0,018 → 0,012 | Hochstrom-21700-Zellen (P42A-Klasse) (Kap.-16-Empfehlung) |

Alle übrigen Enum-Werte sind unverändert. Weitere Datenwünsche aus der Charakterisierung: keine.

**Erweiterungen (nicht v1):**
- Turtle/Crash-Flip: Rotoren rückwärts (`ω < 0`, Schub `−0,5·k_T·ω²`, max. 60 % Duty), Stick wählt das
  Motorpaar; passt in Mixer und `RotorAero` ohne Strukturänderung.
- Lageschätzer mit simuliertem Beschleunigungsmesser (ANGLE driftet in langen Kurven wie beim echten FC).
- Fremde Wake-Pakete anderer Drohnen (Multiplayer „dirty air“) über `WakeField.inject`.
- Deckensog und Wandeffekt analog zum Bodeneffekt, wenn das Entity weitere Abstände liefert.
- Prop-Schäden nach Crashs (k_T −20 %, Vibration hoch), Reparatur an der Werkbank; Motor-/ESC-Schaden bei
  Überhitzung.
- RPM-Filter und Dynamic Notch, sobald Prop-Schäden echte Vibrationsspitzen erzeugen; D-Min; Dynamic Idle.
- Blackbox-Ringpuffer (10 s bei 500 Hz) für Configurator-Graphen; die Telemetrie liefert P/I/D/F schon je Achse.
- Server-Plausibilität: `AirframeSpec` + `BuildAnalysis` begrenzen gemeldete Geschwindigkeiten grob
  (Topspeed, Fallgeschwindigkeit, Steigleistung + Toleranz).

---

## 17. Beliebige Bauteile (Parts-API 0.4.0)

Seit 0.4.0 liest das Flugmodell jedes Bauteil aus seiner Definition (`api.part.*Definition`, Vertrag
`docs/design/parts-api.md` Kap. 5 und 9). Für alle eingebauten Teile ist das Ergebnis **bitgleich** zu 0.3.2; das
beweisen drei Golden-Tests: `AirframeGoldenTest` (alle Felder von `AirframeSpec` für 9000 Kombinationen, `BuildAnalysis`
der Presets mit Zubehör), `BuiltinRegressionTest` (`BuildAnalysis` aller 680 baubaren eingebauten Kombinationen; Flüge
Acro, Angle mit Wind und Böen, Fall auf den Boden und gegen eine Wand mit Prop-Strikes, abgenutzte Drohne; Vollgas bis
leer mit und ohne `thermalEffects`; Crash-Verschleiß und Verschleißwirkung – Hash über die ganze Telemetrie je Frame,
Events und Aufpralle) und `audio/MotorTimbreRegressionTest`. Die Referenzwerte wurden vor dem Umbau mit dem Code von 0.3.2
erfasst. Neue Felder (`AirframeSpec`, `Telemetry`, `BuildAnalysis`) und neue Events/Warnungen fließen nicht in diese
Hashes ein.

### 17.1 Was aus der Definition kommt

| Wert | Quelle | Standard (= 0.3.2) |
|---|---|---|
| Statormaße | `MotorDefinition.statorDiameterMm/HeightMm` | aus dem Label wie bisher |
| Zellkurve, Grenzen | `CellChemistry.ocvShape`, `full/empty/cutoff` (`CellCurve`, jetzt öffentlich) | LiPo-/Li-Ion-Kurve aus 4.5 |
| Akkudichte | `CellChemistry.densityGramsPerCm3` | 2,0 bzw. 2,4 g/cm³ |
| Akku-Quader | `BatteryDefinition.packSize` (Länge entlang Z, Breite X, Höhe Y) | aus Masse/Dichte, 2,2 : 1,3 : 1 |
| ESC | `escResistanceOhm`, `escBurstAmps`, `brownoutVoltage` | 0,003 + 0,15/I, 1,3·I, 2,9/5,5 V |
| Rumpfflächen | `FrameAero.sideAreaM2/topAreaM2` (+ Zubehör × 1,15 bzw. × 2,9/2,5) | Stirnfläche × 1,15 / 2,9 / 2,5 |
| Ducts | `DuctSpec` (Schubgewinn, Spaltmaß, Translationsauftrieb, Wash-Verlust/-Rauschen, Rückprall, Reibung) | Konstanten aus 4.1 |
| Massenverteilung Rahmen | `AirframeGeometry.massSplit` | 60/40/0 offen, 20/30/50 Duct |
| Objektiv | `AirframeGeometry.cameraMm` → `lensRight/lensUp/lensForward` relativ zum Schwerpunkt | 15 mm über, 0,7·s vor dem Schwerpunkt |
| Standard-Uptilt, Kamera-FOV | `cameraTiltDeg`, Brillen-Sichtfeld der Videoverbindung | Rahmenwert, analog 120° / digital 130° |
| Hitze | `MotorThermal` je Motor (17.4) | heutige Konstanten |
| Prop-Steifigkeit | `PropDefinition.stiffness` s (17.5) | 1,0 |
| Haltbarkeit | `PartBasics.durability` aller Teile (17.6) | 1,0 |

### 17.2 Rahmen mit eigener Arm-Geometrie (`AirframeGeometry.arms`)

Nur Rahmen mit `arms` nehmen diesen Pfad; alle anderen (auch alle eingebauten) rechnen wie in Kapitel 4 True-X aus dem
Radstand. Umrechnung Rahmen → Sim: `x = −X/1000`, `y = Y/1000`, `z = −Z/1000`; Position p → Motor `PartWear.simMotor(p)`
(vorne links → 3, vorne rechts → 1, hinten links → 2, hinten rechts → 0); `cw` → spin −1, `ccw` → +1.

- **Schwerpunkt dreidimensional**: Rahmen im `frameCenterOfMassMm`, Akku und Elektronik über der Rahmenmitte, Motoren bei
  `y_Arm + y_rot/2`, Props bei `y_Arm + y_rot`, Zubehör im Anker (mit x/z). Motorpositionen relativ zum Schwerpunkt.
- **Trägheit**: Punktmassen Motoren/Props; vier Arme als Stäbe Rahmenmitte → Motor (`massSplit.arms/4` je Arm,
  ∫(a + t(b−a))² dt = (a² + ab + b²)/3); Platte als Rechteck 0,8/√2 × Umriss der Motoren; Duct-Ringe (Radius R + Spalt)
  um jeden Motor; Akku als Quader; Elektronik als Punkt; Zubehör als Quader. Ein explizites True-X ergibt dieselben Werte
  wie der Altpfad (Test `FrameGeometryTest`, relativ 1e−9).
- **Mixer** wie ein Betaflight-Custom-Mixer: `mixRoll_i = x_i/max|x|`, `mixPitch_i = −z_i/max|z|`, `mixYaw_i = spin_i`.
  **Gas-Spalte**: Schubgewichte `w = 1 − Aᵀ(AAᵀ)⁻¹A·1` mit den Zeilen x, z, spin (kleinste Abweichung von 1, die Nick-,
  Roll- und Giermoment des Gases um den Schwerpunkt aufhebt), Gas-Gewicht `√(w/max w)` (Schub ∝ Drehzahl², Drehzahl ∝
  Tastverhältnis), mindestens 0,2. Symmetrische Rahmen behalten exakt 1 (`uniformThrottle`, alter Mixer-Code). Mit
  Gewichten begrenzt der Mixer das Gas so, dass `t·w_i + a_i` in [0, 1] bleibt (Airmode wie bisher).
- **Autoritäten**: Hebel `L_roll = Σ x_i·mixRoll_i`, `L_pitch = Σ (−z_i)·mixPitch_i` statt `4·s` (True-X: 4·s).
- **Kennmaße**: `motorOffset` = Mittel von (|x|+|z|)/2, `armLength` = mittlerer Horizontalabstand, `motorSpacing`
  (Bodeneffekt) = mittlerer Abstand zum nächsten Motor; True-X: s, L, 2s.
- **Hülle**: Propspitzen radial vom Schwerpunkt, Motorunterseiten auf der tiefsten Armebene, Akku-Kanten und Bodenplatte
  unter der Rahmenmitte, Kameranase aus `cameraMm` (sonst 0,8 × vorderster Motor). **Hitbox**: `2·max(|x|, |z|) + 2·r_t`.

### 17.3 Akku, ESC-Spannungsbereich

Akkus mit bis zu 12 Zellen (API) laufen ohne Sonderfall (Bus, Sag, Vbat-Kompensation je Zelle). **ESC-Überspannung**:
Hat der Akku mehr Zellen als `stack.maxCells` (`AirframeSpec.escOvervoltage`), brennt der ESC beim **ersten
Scharfschalt-Versuch** durch: Event `ESC_BURNED` (Wert = Zellen), `ARM_BLOCKED` mit `HARDWARE_FAULT`,
`Telemetry.escBurned`; die Sim bleibt bis zu einem neuen Bauplan gesperrt. `PilotSession` meldet es per
`HardwareWearC2S`, der Server (`DroneWear.applyHardware`, nur mit der Welt-Regel `propwash:crash_damage` und nur, wenn der Bauplan auf dem
Server wirklich überspannt ist) setzt den Stack-Zustand auf 0 → kaputter Stack = Hardwarefehler wie nach einem Crash.
Abschaltbar mit `SimTuning.escOvervoltage` (Welt-Regel `propwash:hardware_limits`). Keine eingebaute baubare Kombination ist überspannt (`EscVoltageTest`). Unterspannung bleibt wie bisher ein
Brownout-Thema. Werkbank-Warnung `BuildWarning.ESC_OVERVOLTAGE`.

### 17.4 Hitze: Drosselung und Verschleiß

`MotorThermal` je Motor: Wärmekapazität C (0,5 J/(g·K) × Masse), Kühlung `G = (G₀ + G_ω·ω)·k` (Standard G₀ = 0,075,
G_ω = 1,5·10⁻⁴, k = (m/33 g)^⅔; eigene Werte in W/K ersetzen G₀ bzw. G_ω·k mit k = 1), `derateStartC` (130),
`maxTempC` (150), `wearPerSecondAtMax` (0,002). Mit `SimTuning.thermalLimits` (Standard an):

- Leistungsgrenze je Motor `L = 1 − 0,8·min(1, (T − T_start)/(T_max − T_start))` über `T_start`; das Tastverhältnis zum
  ESC wird auf L begrenzt (der FC merkt es wie bei einem echten ESC nur am Ergebnis). Bei `T_max` bleiben 20 %.
- Events `MOTOR_DERATE` (Wert 1 = Beginn über `T_start`, 0 = Ende unter `T_start − 10 K`), `MOTOR_OVERHEAT` jetzt bei
  `maxTempC` des Motors (eingebaut 150 °C wie bisher).
- Verschleiß `wearPerSecondAtMax/durability × min(1, (T − T_start)/(T_max − T_start))²` je Sekunde, gesammelt und als
  `MOTOR_THERMAL_WEAR` (Wert = Menge ≥ 0,002, Motor) ausgegeben; `PilotSession` sendet höchstens einmal je Sekunde,
  der Server zieht es vom Motor-Zustand ab (je Paket höchstens `Rate × max(1 s, Abstand) × 1,5`, absolut 0,25).
- Telemetrie `motorPowerLimit[4]`, `motorDerating`.

**Neutralität (`ThermalNeutralityTest`)**: Keine der 680 baubaren eingebauten Kombinationen erreicht 130 °C beim Schweben
bis leer (quasistatisch, höchstens 76 °C) und im echten Vollgas-Steigflug bis leer (volle Sim, 25 °C, alle Presets und alle
Kombinationen mit quasistatischem Vollgas im Stand über 110 °C; heißeste: `long_range_7` + 2306/1860 + 7″ + 6S 1300 mit
125 °C). Im Stand mit Vollgas (also ohne Steigflug) kämen einige überpropte 7″-Kombinationen (2207/2306 an 7″-Props)
bis 152 °C – dort drosselt 0.4.0 jetzt, und die Werkbank warnt (`MOTOR_THERMAL`). Die Grenzwerte der eingebauten Motoren
mussten nicht angehoben werden.

### 17.5 Props: Material und Steifigkeit

Steifigkeit s (PC 1,0 · GFN 1,2 · CFN 1,4 · Carbon 2,2): Propwash-Rauschen `washNoise / √s`; Wirkungsgrad unter Last
`Schub × (1 − 0,04·(1/s − 1)·min(4, (ω/ω_h)² − 1)/4)` (im Flugmodell und in `RotorAero.thrust` für die Analyse). s = 1
lässt beides unverändert.

### 17.6 Haltbarkeit

`CrashDamage.apply` teilt die Dosis je Teil durch `basics.durability` des Teils (Props, Motoren, Rahmen-Arme, Video,
Stack, Akku inkl. Aufblähen); die Mindestabnutzung eines Crashs bleibt. Hitzeverschleiß teilt durch die Haltbarkeit des
Motors.

### 17.7 Analyse (`BuildAnalysis`)

- `sustainedMotorTempC`: heißeste Motortemperatur bei **Vollgas im Stand, bis der Akku leer ist** (bzw. Beharrung, wenn
  der Akku länger hält), 25 °C, quasistatisch in 40 Ladungs-Abschnitten (`OperatingPoints.fullThrottleMotorTemp`; weicht
  weniger als 5 K von der Sim ab). Warnung `MOTOR_THERMAL` über `derateStartC` – sie fängt „KV × Spannung zu hoch“ und
  überpropte Motoren. Text und Analysewert sagen ausdrücklich „Dauervollgas im Stand“: im Vollgas-Steigflug entlasten
  sich die Props, dort erreicht keine eingebaute Kombination die Grenze (Neutralität oben).
- `propsInViewPercent` (ab 0.4.1): Anteil eines 16:9-Bilds, den die Prop-Scheiben im FPV-Bild verdecken – mit genau
  dem Aufbau, den der Renderer zeichnet (`sim.FpvRig`, rein): Objektiv = `cameraMm` des Rahmens (ohne Angabe wie
  `FpvPropGeometry.lens`: `lensRight/Up/Forward` über dem Render-Schwerpunkt), Uptilt = Standard des Rahmens
  (`drone.CameraMount.defaultUptilt`; der eigene Winkel einer Drohne zählt nur im Wartungsmenü und im Bild selbst,
  `CameraOcclusion.percent(rig, uptilt, …)`), Motorpositionen aus `drone_layout.json` (`frames`) bzw. den Armen oder dem
  Legacy-Quadrat, Nabenhöhe wie `DroneAssembly.propHeight` (`motor_seat` der Motoren aus `assets/<ns>/drone_layout.json`
  der Mods, sonst Statorhöhe + 9 mm), Scheibe 0,55 × Nabenhöhe (2,75 mm ab 50 mm Prop, sonst 1,65 mm) über der Nabe,
  Kreisring von der Nabe (`max(4, 0,09 · D) / 2`) bis zur Blattspitze (`D / 2`), geradlinige Projektion mit dem
  horizontalen **Sichtfeld der Kamera im Videomodul** (`VideoDefinition.cameraFovDeg`, ohne Angabe analog 120°,
  digital 130°; `FovMath.cameraHorizontalDegrees`, wie `VideoState.fpvFov` mit analoger und digitaler Brille; seit dem
  Nachtrag vom 02.10.2026 – vorher das Brillen-Sichtfeld der Einstellungen). Integration zeilenweise: 1080 Bildzeilen, je Zeile schneidet die Zeilenebene jede
  Scheibe in einer Sehne gleicher Tiefe, deren x-Intervall exakt projiziert und vereinigt wird (`CameraOcclusion`);
  Punkte näher als 0,25 mm Tiefe zählen nicht (wie der Renderer). Warnung `PROPS_IN_VIEW` ab **30 %** (vorher 20 % –
  die alte Rechnung mit Rotorebene aus dem Flugmodell, `cameraFovDeg` und 96 × 54 Strahlen lag bis zu 18 Prozentpunkte
  neben dem Bild und meldete dünne Scheiben als 0 %).
- Gegenprobe im Spiel: Selbsttest `drone-render: Props im FPV-Bild – Analyse gegen gerendertes Bild` fliegt jedes Preset
  (eingebaut, Beispiel-Addon und alle angemeldeten Addon-Presets) 100 Blöcke über dem Boden im FPV, zeichnet die
  Propscheiben deckend in Magenta (`FpvProps.maskColor`), liest das Bild vor dem Videoeffekt (`CleanFrameTap`) und
  vergleicht die Magenta-Pixel mit `CameraOcclusion` bei Fenster-Seitenverhältnis und -Höhe (Toleranz 1 Prozentpunkt oder
  8 %, sichtbar gegen 0 % ist immer ein Fehler).
- `ESC_OVERVOLTAGE` wie 17.3.

Kamerahalter (0.4.0 öffentlich, nach der Rückmeldung des Besitzers): alle eingebauten Rahmen setzen `camera.pos`, das
Objektiv liegt 10 mm vor der Mitte der gezeichneten Kamera auf ihrer gekippten Achse und damit wenige Millimeter unter
der Propebene (Whoop: knapp über den Ducts). Vorher galt die Altformel (15 mm über dem Schwerpunkt), die das FPV-Auge
8–22 mm über die Propebene setzte; der Racer zeigte 0 %. Nur `lensUp`/`lensForward` ändern sich, die Flugphysik nicht
(Bewegungs-Fingerabdrücke bitgleich). Recherche, Zielbereiche und alle Rahmen: `docs/design/realism/props-in-view.md`.
Eingebaute Presets (Bild gemessen / Analyse bei 1708 × 960): WHOOP 9,0 % (8,99 / 9,02), CINEWHOOP 7,3 % (7,27 / 7,29),
FREESTYLE 8,4 % (8,35 / 8,38), RACER 11,2 % (11,15 / 11,21; seit dem 11.10.2026 als True-X 6,4 %, 6,36 / 6,39),
LONG_RANGE 9,5 % (9,44 / 9,48) – keine Warnung.

### 17.8 Charakterisierung (Tests `AddonCharacterizationTest`, `FrameGeometryTest`, Teile nur über `PartIndex.detached`)

| Drohne | Masse | T/W | Schwebegas | Schweben / Reise [min] | Top [km/h] | Motor °C | Props im Bild | Roll/Nick-Autorität [rad/s²] |
|---|---|---|---|---|---|---|---|---|
| X-Class 13″ 8S 2200 (4214/400, 13×7 CFN, 1450-g-Rahmen) | 2567 g | 5,3 | 32 % | 9,6 / 7,2 | 79 | 82 | 0,1 % | 451 / 450 |
| 7″ 8S Li-Ion 4500 (2807/1050) | 993 g | 6,0 | 28 % | 39,6 / 27,4 | 115 | 106 | 14,5 % | 1459 / 1219 |
| 2″-Toothpick 1S 300 HV (1103/8000) | 40 g | 2,0 | 77 % | 5,7 / 5,6 | 30 | 32 | 4,2 % | 1372 / 1357 |
| 2″-Toothpick 2S 300 | 49 g | 4,9 | 40 % | 9,2 / 7,0 | 54 | 41 | 8,0 % | 2778 / 2681 |
| Deadcat 5″ (FREESTYLE-Teile) | 518 g | 10,8 | 23 % | 14,3 / 6,5 | 140 | 94 | 0,0 % | 1708 / 1860 |
| Stretched-X 5″ (70/95 mm) | 518 g | 10,8 | 23 % | 14,3 / 6,5 | 140 | 94 | 14,2 % | 2127 / 1701 |
| Squashed-X 5″ (95/70 mm) | 518 g | 10,8 | 23 % | 14,3 / 6,5 | 140 | 94 | 8,4 % | 1756 / 2019 |
| H-Frame 5″ (110/60 mm) | 518 g | 10,8 | 23 % | 14,3 / 6,5 | 140 | 94 | 5,0 % | 1574 / 2143 |
| Überlastet: 2207/1750 an 7″ auf 8S 1300 | 657 g | 16,4 | 10 % | 21,3 / 5,3 | 153 | 159 | 20,3 % | 3195 / 3082 |

„Props im Bild“ dieser Prüfrahmen gilt für ihre Kamera aus der Altformel (kein `camera.pos`, außer Deadcat); die
ausgelieferten Rahmen stehen in `docs/design/realism/props-in-view.md`.

Deadcat: Schwerpunkt 4,2 mm hinter der Rahmenmitte, Gas-Gewichte vorn 1,000 / hinten 0,921. Im geschlossenen Kreis
(ANGLE mit Höhenhalter, 8 s, ohne Rauschen) bleiben Deadcat, Stretched- und Squashed-X unter 1,5° Neigung und 0,3 m/s
Drift, in ACRO ohne Knüppel unter 3° Lageänderung; die X-Class schwebt mit weniger als 2° und folgt einem Roll-Kommando
innerhalb 0,5 s mit mehr als 70 % der Soll-Rate. Die X-Class bleibt mit τ_m = 17,7 ms unter der Klemme von 40 ms; der
feste Sim-Takt bleibt stabil (keine NaN, keine Sim-Fehler).

---

## 18. Energiebilanz, Flugzeiten und Akku-Anzeige (0.4.2, 0.4.3)

Kalibrierung, Quellen und Vorher/Nachher stehen in [`realism/calibration.md`](realism/calibration.md). Grundsatz: Die
Flugphysik (Kap. 4–5, alle Parameter in `AirframeSpec`, die Busspannung, Motorströme, Schub, Drehzahlen) ist bitgleich
zu 0.4.1. `sim.EnergyModel` rechnet daneben, was der Akku wirklich liefert.

### 18.1 Akkustrom
```
I_Akku = I_Sim + (k(ω) − 1)·max(0, I_Sim − P_aux/U) + (P_Avionik − P_aux + Σ P_Antrieb(ω_i)) / U
P_Antrieb(ω) = P_Antrieb,S · ω / ω_Schweben                            (je Motor, nur aktiv)
P_Antrieb,S  = 1,03 W · √(m_Motor / g)                                  (Eisen-, Wirbelstrom-, Reglerverluste)
             + 0,38 · max(0, Blätter − 3) · klein(D) · k_Q · ω_Schweben³  (Blattinterferenz kleiner Props im Teillastbereich)
k_Prop       = Größenfaktor(D), log-linear: 31 mm 0,84 · 51 0,78 · 76 0,72 · 89 0,67 · 102 0,80 · 130 1,02 · 178 1,0 ·
               254 0,93 · 330 0,93
k_Voll       = Volllastfaktor(D), dieselben Stützstellen: 0,84 · 1,05 · 1,0 · 1,0 · 1,0 · 1,02 · 1,0 · 0,93 · 0,93 (seit 0.4.3)
k(ω)         = k_Prop + (k_Voll − k_Prop)·x²,  x = clamp01((ω² − ω_S²)/(ω_Voll² − ω_S²))
               ω_Voll = Drehzahl bei Vollgas im Stand, voller Akku (AirframeSpec.fullLoadOmega); im Spiel Mittel über die vier Motoren
klein(D)     = clamp01((45 mm − D) / 10 mm)
P_Avionik    = 0,6 W + P_Video + Σ accessory.flightBatteryWatts
P_Video      = power_w, sonst analog 1,2 W + 4,3·P_HF, digital 4,5 W + 0,1 W/g·Masse + 2,5·P_HF (P_HF in W, Standard 0,2 W)
```
`P_aux` (Kap. 4.5) bleibt Teil der Flugphysik und bestimmt weiter die Busspannung. Die Blattinterferenz wächst nur
linear mit der Drehzahl, die Wellenleistung kubisch: Im Schweben kostet das vierte Blatt eines 31-mm-Props 38 % der
Schwebe-Wellenleistung, bei Vollgas (≈ 1,9 · ω_Schweben) nur noch ≈ 10 % (Referenz: bei Vollgas 2–5 % schlechter,
bei gleichem Schub 10–20 % mehr Leistung).

### 18.2 Akku im Flug (`PowerSystem.integratePack`, je Substep)
```
packMah += I_Akku·h/3,6;   packSoc = infiniteBattery ? packSoc : max(−0,10, 1 − packMah/C)
U_Anzeige = V_bus − (E(restingSoc(soc)) − E(restingSoc(packSoc)))
Warnungen: U_Anzeige/N (PT1 1 Hz) < emptyCellVoltage ≥ 2 s → LOW, < cutoffCellVoltage → CRITICAL, packSoc ≤ 0 → EMPTY
packSoc ≤ −0,10 → Abschaltung wie Brownout (Akku tiefentladen)
```
Telemetrie `batteryCurrentA`, `batteryVoltage`, `batteryCellVoltage`, `batteryCellResting`, `batteryMahUsed`,
`batteryCharge`, `batteryPowerW`; die bisherigen Felder (`totalCurrentA`, `packVoltage`, `mAhUsed`, `chargeFraction`, …)
beschreiben weiter den Zustand der Flugphysik. `setBatteryCharge` setzt beide Ladezustände; das Akku-Item speichert
`batteryCharge`.

### 18.3 Flugzeiten in der Analyse
Nutzbar ist `usable_fraction · C` der Chemie (LiPo 0,80, LiHV 0,85, Li-Ion 0,88; ohne Angabe 0,88 bei Leerspannung unter
3,25 V, 0,85 ab 4,3 V Vollspannung, sonst 0,80). Integration in 24 Ladungsschritten; je Schritt Akkustrom (18.1) und Last
der Flugphysik beim mittleren Ladezustand des Schritts. Gelandet wird, sobald

- die nutzbare Kapazität abgegeben ist,
- die Zellspannung unter der Last des ruhigen Flugabschnitts (Schweben bzw. Reisen, bei gemischt/aggressiv der
  Reiseanteil) die Landespannung (Leer + Abschalt)/2 erreicht – so liest der Pilot die Spannung ab – oder
- die Zellspannung unter der mittleren Last des Profils die Abschaltspannung erreicht.

Spannungen werden an den Schrittgrenzen geprüft und linear interpoliert; gemeldet wird die Zellspannung unter mittlerer
Last beim Landen. Seit 0.4.3 ist je Ladungsschritt jedes Profil mindestens so fordernd wie das ruhigere (Akkustrom, Last
des ruhigen Abschnitts, mittlere Last und Motorwärme als Maximum in der Kette Schweben → Reisen → gemischt → aggressiv).
Weil alle Profile dieselben Ladungsschritte durchlaufen, gilt damit für jeden Aufbau aggressiv ≤ gemischt ≤ Reisen ≤
Schweben.

| Profil | Inhalt |
|---|---|
| Schweben | `OperatingPoints.hover` |
| Reisen | Geradeausflug (Kap. 12) mit `min(v_Klasse, Anteil·v_top)`: Whoop 18,5 km/h/0,55, Toothpick 40/0,45, Cinewhoop und Cinelifter 30/0,5, Freestyle 55/0,4, Race 60/0,4, Long Range 63/0,55, X-Class 65/0,5 |
| Gemischt / aggressiv | Zeitmittel aus Reisen, Schubstoß (statisch, festes Schub/Gewicht, höchstens Vollgas; Li-Ion-artige Zellen gemischt höchstens 6 C, aggressiv höchstens Dauerstrom) und Leerlauf (Duty 5,5 %); Anteile und Schubstoß je Rolle (Whoops getrennt nach Props bis 35 mm und größer) in `realism/calibration.md` §3.3 |

Temperaturen: Motor-Wärme `I²·R + K_t·I_0·ω` und Kühlung wie Kap. 5.11; `motorTempMinuteC` = 60 s Vollgas im Stand
(40 Ladungsschritte, genau die Thermik der Flugphysik), `mixedMotorTempC` = Ende des gemischten Flugs mit Fahrtwind:
Kühlung · (1 + 0,6·√(v_Reise / 10 m/s)); `sustainedMotorTempC` = Vollgas im Stand bis der Akku (Energiebilanz) leer ist,
Grundlage der Warnung `MOTOR_THERMAL`. Der Fahrtwind wirkt nur in der Analyse; die Thermik der Flugphysik (und damit die
Temperatur-Telemetrie im Flug) bleibt unverändert.

## Implementation notes

Stand der Umsetzung im Paket `de.lardtom.propwash.sim` (Tests unter `common/src/test/java/de/lardtom/propwash/sim/`).
Alle stationären Tabellenwerte aus Kapitel 4.9, 7 und 13 werden vom Java-Code exakt reproduziert (Massen, Trägheiten,
Motor- und Akkukennwerte, Autoritäten, Default-PIDs, Schwebegas, Schub, T/W, Topspeeds, Flugzeiten). Die dynamischen
Kennwerte (Rollraten-Antwort, Gier-Sprung, ANGLE-Sprung, Flip, Sinusfolge, Punch) liegen innerhalb der Toleranzen.
Abweichungen und Präzisierungen, jeweils mit Grund:

**Physik und Numerik**

1. **Rotationsintegration (5.8):** statt `ω_W` aus der alten Orientierung wird eine iterierte Mittelpunktsregel verwendet
   (2 Iterationen mit Halbschritt-Orientierung, dann exakte Exponentialabbildung). Mit der Formel aus 5.8 driftet die
   Energie in Test C13 um 61 % statt < 0,5 %.
2. **VRS-Breite (5.6):** `exp(−((ν − 1)/0,45)²)` statt `/0,5`. Mit 0,5 ergibt Sinken mit 0,1·v_h ein Gewicht von
   0,039, Test C11 verlangt ≤ 0,02. Die anderen beiden C11-Kriterien bleiben erfüllt.
3. **ESC-Stromgrenze (5.2):** statt höchstens zwei Durchgängen eine konsistente Active-Set-Iteration (bis 5 Durchgänge,
   begrenzte Kanäle werden auch wieder freigegeben). Nur so gilt „Phasenstrom nie > I_lim“ (C6) auch dann, wenn die
   Busspannung nach dem Begrenzen stark fällt.
4. **Allokationsfreie Trigonometrie:** `StrictMath.sin/cos` allokieren im JDK (FdLibm) bei jedem Aufruf ein
   `double[2]`, das nur verschwindet, wenn der JIT die Methode einbettet (G9 lag bei 1,7–2,2 kB pro Frame). Im
   Substep-Pfad wird deshalb `SimMath.sin/cos` benutzt: Quadrantenreduktion mit zweiteiligem π/2 und Taylor-Kern
   (reine IEEE-Arithmetik, deterministisch). Das Twist-Heading für ANGLE wird trigonometriefrei aus (q.w, q.y) gebildet:
   `cos ψ_t = (w² − y²)/(w² + y²)`, `sin ψ_t = 2wy/(w² + y²)`.
5. **Thermik (5.11, Phase 3):** Motor-Wärmeleitwert halbiert, `G = (0,075 + 1,5e−4·ω)·(m_mot/33 g)^(2/3)`. Mit
   `0,15 + 3e−4·ω` erreicht FREESTYLE bei Dauervollgas nur ≈ 70 °C (T∞), der Phase-3-Test verlangt > 100 °C nach 60 s.
   Jetzt: ≈ 110 °C nach 60 s, T∞ ≈ 120 °C, `MOTOR_OVERHEAT` (150 °C) wird im Dauerbetrieb nicht erreicht.
6. **Bodenruhe (9.4):** (a) zusätzlich zu `v.y = 0` wird im Ruhekontakt auch die abwärts gerichtete Beschleunigung
   des Substeps auf 0 geklemmt, damit der Schwerpunkt nicht in den Boden kriecht; (b) Kontakt bleibt „grounded“, solange
   die Hitbox-Unterkante auf der gemerkten Bodenhöhe liegt (±2 mm) und `v.y ≤ 0`, auch ohne neuen Y-Treffer. Ohne das
   meldet die ruhende Entity nie einen Treffer, `groundedFrames` läuft ab und der Schlafzustand (E1) wird nie erreicht;
   (c) die Kontaktreibung `μ0·grip·N·s·tanh(ω/0,2)` wirkt nicht nur um Y, sondern auch um X und Z. Die feste AABB kann
   Rollen über die Arme nicht abbilden; ohne diese Dämpfung rollt eine mit > 4 rad/s gelandete Drohne minutenlang
   „durch“ den Boden (widerspricht „kommt über 9.4 zur Ruhe“);
   (d) **seit 2026-10-02 nur mit Auflage:** (b) bestätigte sich selbst. Die Bodenbedingung hält v.y = 0 auf der gemerkten
   Höhe, also blieb die Unterkante dort und (b) verlängerte grounded ohne Ende, auch wenn darunter nichts mehr lag.
   Rutschte eine Drohne nach einem Absturz, Aufsetzen oder Streifen über eine Blockkante, eine Mauerkrone oder ein
   Podest hinaus, stand sie auf unsichtbarem Boden in der Luft (Spieltest: „man bleibt teilweise in der Luft stehen;
   erst wenn man neu armed …“ – erst Gas über dem Schwebegas hob sie von der Höhe ab); eine schlafende Drohne wurde
   vom Bodenabstand-Strahl zwar geweckt, die Bodenbedingung hielt sie aber weiter fest. Jetzt verlangt (b) die
   gemessene Auflage `MoveReport.supportGap` (3.3), fehlende Auflage beendet grounded sofort und weckt aus dem Schlaf.
   Auf ebenem Boden ändert sich nichts: Bewegungs-Goldens (`BuiltinRegressionTest`, `MotionFingerprintTest`) und
   Kalibrierung sind byte-gleich. Regressionstest `sim/CrashFreezeTest` mit `sim/BoxWorld` (Hitbox-Kollision wie
   Minecraft an beliebigen Quadern); im Spiel die Selbsttest-Schritte „Absturz an einer Mauer in der Luft“ und „über die
   Mauerkrone gerutscht“ (`drone.client.CrashFallSelfTests`).
7. **Brownout bei Vollgassprung aus dem Leerlauf (behoben 2026-09-23, siehe 31 und 32):** Vorher brach LONG_RANGE bei
   einem Sprung 0 → 100 % aus dem Leerlauf bei jedem Ladestand unter 5,5 V ein, der WHOOP ab soc 0,2, und 5″-Presets
   sackten kurzzeitig auf 1,6–1,9 V pro Zelle. Mit ESC-Eingangsstromgrenze und träge wirkendem C-Raten-Aufschlag
   gibt es beim Punch aus Gas 0 bei soc 1,0 und 0,5 keinen Brownout mehr (WHOOP erst ab soc ≈ 0,2, realistisch).
   Seit Korrektur 34 (Energiebilanz im Stromlimit) löst auch ein direkter Vollgassprung auf dem Prüfstand aus dem
   Stand (ω = 0, ohne FC) bei soc 1,0 und 0,5 keinen Brownout mehr aus (tiefste Zelle WHOOP 3,63/3,15 V, LONG_RANGE
   1,98/1,41 V); der Prüfstand dreht trotzdem vorher auf Schwebedrehzahl hoch (Punkt 14).
8. **Brownout-Erkennung auch disarmed:** Der FC hängt am Akku. Ein 1S-Akku an einem Stack mit `minCells ≥ 2` (oder ein
   leerer 1S-Akku) hält den FC deshalb dauerhaft in `REBOOTING` (sperrt das Armen). Das Event `BROWNOUT` kommt dabei
   nur einmal pro Unterspannungsphase (siehe 38), nicht mehr alle 0,5 s.
9. **`propsOut`:** negiert wie spezifiziert σ_i in der Physik (Reaktionsmoment, Kreiselterm, Prop-Strike-Drall) und
   die Gier-Spalte im Mixer; beides zusammen hält die Gierregelung stabil (FacadeTest).

**Flight Controller und PID-Ableitung**

10. **I-Term-Relax-Grenzfrequenz wird mit abgeleitet (Kap. 7):**
    `itermRelaxCutoffHz = clamp(round(15·(ω_c/150)²), 3, 15)`. CINEWHOOP, FREESTYLE, RACER und LONG_RANGE bleiben bei
    15 Hz, WHOOP 7 Hz. Grund: Beim WHOOP (Motor τ 40 ms) integriert der I-Anteil während des 100-ms-Anstiegs
    auf und baut sich nur langsam ab; der Restfehler 300 ms nach dem Rollsprung lag bei 3,6 % (D3 verlangt < 2 %),
    jetzt 1,6 %. `PidAutoTune.derive` setzt das Feld zusätzlich zu den in Kap. 7 genannten.
11. **Stickwert 300 °/s (Messdefinition 13):** Mit ACTUAL 70/670/0 ist der Stick für 300 °/s **0,6512**, nicht 0,6556
    (70·x + 600·x² = 300). Der Test rechnet den Stick über `RateCurves.stickFor`.
12. **Heading-Vorzeichen:** ψ = atan2(f.x, f.z) wächst von oben gesehen gegen den Uhrzeigersinn. Gieren nach rechts
    (FC-Yaw positiv) verkleinert ψ; D8 prüft deshalb −90 °/s Kursrate für +90 °/s Gier-Sollwert.
13. **Reserven D16:** Von den 380 baubaren Kombinationen haben 220 `CANNOT_HOVER` (z. B. 1S-Akku an 6S-Motoren,
    31-mm-Props am Cinewhoop; vor der Kalibrierung 2026-09-23: 216). Dort existiert kein Schwebe-Arbeitspunkt, die
    Linearisierung ist sinnlos und die Gains laufen in die Klemmen. D16 prüft deshalb die 160 schwebefähigen Builds:
    minimale Phasenreserve 62,5° / 55,9° / 57,4° und Amplitudenreserve 12,4 / 9,4 / 9,0 dB bei 2 kHz / 1 kHz / 500 Hz,
    genau wie in Kap. 7 behauptet. D19 überspringt
    `CANNOT_HOVER` wie spezifiziert.

**Testdurchführung (Absicht unverändert, Ablauf präzisiert)**

14. **C3 Prüfstand-Vollgas:** Die Tabellenwerte gelten ohne Polarisation. Nach 12 s Vollgas (der Erwärmungsstrom aus
    Punkt 32 hat sich dann eingestellt) wird deshalb gegen den
    stationären Löser mit gleichem soc **und gleicher aktueller Polarisation** verglichen (±2 %); die Tabellenwerte
    selbst prüft B3. Der Prüfstand dreht vorher auf Schwebedrehzahl hoch (siehe Punkt 7).
15. **C4 Motorzeitkonstante:** mit steifem Bus (`PowerSystem.stiffBus`, nur für Tests; `batterySag = false` behält seit
    Punkt 45 den Innenwiderstand). τ_m in 4.3 setzt einen steifen Bus voraus; mit Akku-Innenwiderstand
    ist die effektive elektrische Dämpfung kleiner (FREESTYLE 16,5 ms statt 12,3 ms).
16. **C6 ESC-Grenze:** Keine der 380 baubaren Kombinationen hat die Warnung `ESC_UNDERSIZED`. Geprüft wird die Grenze
    an FREESTYLE-Duty-Sprüngen über `DroneSim` (Events ≤ 1 pro 0,5 s und Motor) und zusätzlich an der nicht baubaren
    Kombination CINEWHOOP_3/AIO_20/M2207 direkt im `PowerSystem`.
17. **C8:** Vorzeichenwechsel der Spannungsableitung zählen erst ab 1 mV pro Substep; die langsame Polarisation ist
    keine Oszillation.
18. **D13 Topspeed dynamisch:** Neigungen `min(60°, θ* − 2°)` und `θ* − 2°`; genau bei θ* reicht der Schub nicht, um
    die Höhe mit einem Regler zu halten. Geschwindigkeit als Mittel der Sekunden 12–15. Abweichung zum stationären
    Wert ≤ 1,6 %.
19. **D18 WHOOP:** Bei soc 0,06 tritt der Brownout schon beim Hochdrehen auf Vollgas auf (soc 0,06 > −0,10).
20. **E-Tests:** E2/E3/E5/E6 setzen die Geschwindigkeit im ANGLE-Schwebeflug direkt; E3 nutzt 1-ms-Frames mit 1 mm Abstand
    zur Wand, damit der Luftwiderstand die ±1-%-Schwelle nicht verfälscht. E4 prüft den Tangentialverlust gegen die
    Obergrenze μ0·(1 + e)·v_n.
21. **F4 Wake-Feld (Phase 2):** Implementiert wie in 5.6 beschrieben, das Szenario „1,5 s Vollgas, Flip, zurückfallen,
    Spitze ≥ 0,5 in 2 s“ ist mit den Konstanten aber nicht erreichbar: FREESTYLE steigt nach 1,5 s Vollgas mit 42 m/s
    noch 25 m weiter, junge Pakete wandern mit 2·v_i nach unten, alte sind nach τ = 1,2 s zu schwach; γ ist in
    Rückenlage auf 0,25 begrenzt. Der Test prüft deshalb das Gedächtnis der Säule direkt (Fallen in eine frische
    Vollgas-Säule ≥ 0,5), ≤ 0,1 bei 10 s Horizontalflug mit 10 m/s und den Verfall aller Pakete nach 5 s Leerlauf am
    Boden. Das Abstimmen der Wake-Konstanten ist eine Spielgefühl-Entscheidung.
22. **G4:** mAh ist wegen der Rekuperation beim aktiven Bremsen (5.2) nicht streng monoton; der Test erlaubt
    Rückgänge bis 1 mAh.
23. **G7:** Die Stick-Interpolation (8.3) verzögert Eingaben um einen halben Frame. Zwischen 30 und 144 fps sind das
    ≈ 13 ms, die Endposition verschiebt sich um v·13 ms. Mit einer Choreografie aus ±0,2 und 1-s-Segmenten ergeben sich
    25 cm, der Test nutzt ±0,1 in 0,5-s-Segmenten (Abweichung < 5 cm, Lage < 1°).

**API und Build**

24. `AirframeSpec` hat zusätzlich `phaseResistance` (R_m), `hoverVoltage` und `hoverThrust()`. `DroneSim` bietet
    zusätzlich `config()` und `tuning()` (beide liefern Kopien, siehe 40). `EventQueue` hat `count(type)` und `contains(type)`; bei Überlauf bleiben die
    32 neuesten Einträge.
25. `sanitize()`-Bereiche für die in der Tabelle fehlenden Felder: `failsafeDelayS` [0,05; 10], `failsafeLandingS`
    [0; 60].
26. Keine Kommentare im Code gemäß `docs/CONVENTIONS.md`.
27. `common/build.gradle`: `testImplementation` für `org.joml:joml:1.10.9` und `com.google.code.gson:gson:2.14.0`
    (die Minecraft-Bibliotheken liegen nur auf dem Compile-Classpath, nicht auf dem Test-Classpath).
28. Performance: 20 000 Substeps in 8–14 ms (≈ 0,5 µs pro Substep); Allokation im Test G9 unter 64 Byte pro Frame.

**Kalibrierung aus der Flug-Charakterisierung (2026-09-23, `flight-model-characterization.md`)**

29. **Teile-Daten:** Kap. 16 (Tabelle) ist übernommen; Kapitel 4.9, 7 und 13 und die Testtabellen zeigen die neuen Werte.
30. **Nullschub-Geschwindigkeit:** `V0_FACTOR = 1,15` ist ersetzt durch `v_0 = (pitch + 0,20·D)·ω/2π`
    (`ZERO_LIFT_PITCH`). Der Zuschlag entspricht dem Nullauftriebswinkel gewölbter Blätter (≈ 5° bei 0,75 R) und ist
    für Props mit Steigung ≈ Durchmesser gleich dem alten Faktor 1,15–1,2; flache Props (7×3,5) erreichen dadurch
    realistische Fahrt (LONG_RANGE Topspeed 105 → 121 km/h, FREESTYLE 161 → 164, RACER 188 → 193).
31. **ESC-Eingangsstromgrenze:** zusätzlich zur Phasenstromgrenze `I_lim = 1,3·escCurrentPerMotor` begrenzt jeder ESC
    seinen Eingangsstrom `d_eff·I` auf `I_in = 0,8·escCurrentPerMotor` (Strommessung auf der DC-Seite, wie die
    Stromschutzfunktion realer ESCs). Seit der Korrektur 34 gilt dabei `d_eff = (R_m·I + K_e·ω)/V`, also
    `|I| ≤ I_max(V, ω) = min(I_lim, I_root)` mit `(R_m·I_root + K_e·ω)·I_root = V·I_in` (vorher fälschlich `I_in/d`).
    Im Schweben und bei stationärem Vollgas greift sie bei keinem Preset (stationäre Werte unverändert); beim
    Hochdrehen begrenzt sie den Akkustrom der 5″-/7″-Presets auf 176 A. Sie bleibt nötig: ohne sie steigt der
    Akkustrom beim Hochdrehen mit korrekter Bilanz auf 275–279 A (5″) und der WHOOP brownt beim Punch aus Gas 0 bei
    soc 0,5 aus. `ESC_CURRENT_LIMIT`-Events kommen weiterhin nur von der Phasenstromgrenze; `ESC_CURRENT_LIMITED` in
    `BuildAnalysis` prüft `I_max(V, ω)` im Vollgaspunkt. `AirframeSpec.escInputLimit` ist neu. Eine drehzahlabhängige
    Anlaufbegrenzung wurde probiert und verworfen: sie kappt die Spitzen erst, wenn sie bis ≈ 60 % ω_max reicht, und
    nimmt dann im Schweben Regelreserve weg (Gier-Beschleunigung −40 %).
32. **C-Raten-Aufschlag über Erwärmungsstrom:** `R_int` steigt nicht mehr mit dem Strom des vorigen Substeps, sondern mit
    `I_heat = PT1(2 s)(|I_bat|)` (Snapshot-Feld `batteryHeatCurrent`). Die Stationärlöser sind unverändert (dort gilt
    I_heat = I_bat). Ohne diese Trägheit kippte der Li-Ion-Akku beim Punch in eine Mitkopplung (mehr Strom → mehr
    Widerstand → Zusammenbruch). Folge: LONG_RANGE-Punch 1 s aus dem Schweben 23,9 → 26,9 m/s, `BATTERY_CRITICAL` bei
    Dauervollgas nach 4,4 s statt ≤ 3 s (D18 prüft jetzt ≤ 5 s).
33. **Flug-Charakterisierung als Test:** `FlightCharacterizationTest` misst für alle Presets Schub, Schwebegas,
    Topspeeds (geflogen), Steigen, Rollen/Gieren/ANGLE-Sprünge, Schwebestabilität mit Rauschen und Wash, Flugzeiten,
    Sag, Punch aus Gas 0, Prop-Wash beim senkrechten Sinken, Crashs, Frame-dt 1–50 ms und Determinismus, druckt die
    Tabelle und prüft 134 Plausibilitätsziele.

**Korrekturen nach dem zweiten Review (2026-09-23)** — jeweils mit Regressionstest (`review/*Test`,
`RobustnessTest`, `OpenLoopPhysicsTest`)

34. **Energiebilanz im ESC-Stromlimit:** Ein begrenzter Kanal zog bisher `d_cmd·I_max` aus dem Akku, obwohl der Motor
    nur `(R_m·I + K_e·ω)·I` aufnimmt (FREESTYLE: 176 A statt 37 A im ersten Substep eines Vollgassprungs, +22 %
    Energie aus dem Nichts). Jetzt gilt `I_bat = I_aux + Σ (R_m·I_i + K_e·ω_i)·I_i/V` für jeden Kanal; der Bus wird mit
    drei Kanalzuständen gelöst (frei, Phasengrenze = konstante Leistung, Eingangsgrenze = konstanter Strom, 5.2,
    quadratisch in V). Energie Akku = Motoren + Bordnetz bei allen Presets auf 1e−4 genau. Die Eingangsgrenze ist
    `d_eff·I ≤ I_in` statt `I ≤ I_in/d_cmd`; beim Hochdrehen darf der Phasenstrom deshalb bis `I_lim` gehen, solange
    die Leistung passt (FREESTYLE: 71,5 A statt 44 A am Anfang). Folge: Motoren drehen schneller hoch
    (Roll-Beschleunigung bei 50 % Gas +1 bis +8 %, Gier-t90 WHOOP 35 → 28 ms, CINEWHOOP 76 → 69 ms), die
    stationären Werte, die Sag-Werte beim Punch und die Flugzeiten bleiben gleich, weil die Eingangsgrenze in der
    Mitte des Hochdrehens tatsächlich greift (31). `OperatingPoints` und `BuildAnalysis` rechnen genauso.
35. **Stoßzahl ≤ 0,95:** `e = clamp(e0'·restitutionScale·…, 0, 0,95)`, `surfaceRestitution` wird auf [0; 1] begrenzt.
    Vorher erlaubten `restitutionScale` bis 4 und `surfaceRestitution` bis 2 Werte bis e = 8: ein Fall aus 1 m sprang
    höher als die Starthöhe und kam nie zur Ruhe.
36. **Einmaliges NaN vergiftet nichts mehr:** Der Rollback stellte nur die Snapshot-Felder zurück; Schub des
    Vorschritts, Wash-Gewichte, Wash-/g-Filter und die Gyro-Vibration behielten das NaN und lösten danach in jedem
    Frame `SIM_FAULT` aus, bis `reset()`. Jetzt verwirft `handleFault` allen abgeleiteten Zustand (8.4), der Schub des
    Vorschritts wird nur endlich gespeichert und `vortexRingWeight` weist NaN ab.
37. **Position im NaN-Wächter, geprüfte Snapshots:** Der Wächter summiert jetzt auch p; `copyStateFrom` repariert nicht
    endliche Felder; `afterMove` prüft die Korrektur je Achse statt der Summe (Überlauf −1e308/+1e308).
38. **Anhaltender Kontakt ist kein neuer Stoß (9.2):** Vorher löste eine unter Schub gegen die Decke gedrückte RACER
    bei 20 fps in 3 s 59 `HARD_IMPACT` aus, mit `crashSpeedScale 0,7` sogar Crash-Disarm; bei 144 fps nichts. Jetzt
    bei 20/60/144 fps genau ein Ereignis beim ersten Anschlag, danach Ruhe. Brownout-Event nur einmal pro
    Unterspannungsphase (5.10, Punkt 8): ein leerer 1S-Akku auf der Werkbank meldete sonst alle 0,5 s `BROWNOUT`.
39. **Wasserwiderstand semi-implizit (5.5):** explizit kehrte ×400 die Geschwindigkeit ab h·c·|u| > 1 um (WHOOP mit
    `dragScale 2` tauchte mit −20 m/s ein und kam mit +13,6 m/s wieder heraus).
40. **`config()`/`tuning()` liefern Kopien:** vorher die internen Objekte; Feldänderungen umgingen `sanitize()` (z. B.
    `horizonStickTransitionPercent = 0` → 0/0 im FC), Gain-Änderungen wirkten dagegen gar nicht.
41. **DERIVED folgt Loop und Gyro-Filter:** neu abgeleitet wird auch, wenn sich `pidLoop` oder `gyroLpfHz` ändern
    (`PidProfile.derivedForFilters`). Mit den alten 2-kHz-Werten fiel die Phasenreserve bei 500 Hz auf 30–43°.
42. **Sag-Kompensation wie Betaflight (6.8):** statt `min(1, o·sag)` nach dem Mixer wird der Ausgangsbereich bei vollem
    Akku verkleinert. Vorher klemmten bei Vollgas alle vier Motoren auf 100 % und der Quad konnte eine Rolle nicht
    abbremsen (FREESTYLE 250 ms statt 49 ms bis Stillstand).
43. **Thrust-Linearisierung mit Gas-Kompensation (6.8):** das Gas wird vor dem Mixer mit der exakten Umkehr der
    Linearisierung kompensiert. Vorher sank das Schwebegas mit TL 40 von 23,4 auf 16,2 % (FREESTYLE), fiel unter die
    abgeleitete Airmode-Startschwelle, und Airmode samt I-Term blieben im Schweben aus. Jetzt 23,4 % bei TL 0 und 40.
44. **HORIZON zieht zum Stick-Winkel (6.6):** vorher nach waagerecht, dadurch im Mittelbereich steifer als ANGLE
    (Roll-Stick 0,1: HORIZON 2,9° gegen ANGLE 6,0°). Jetzt wie Betaflight 4.4: 9,0° gegen 6,0°.

**Korrekturen nach dem Spieltest „Bild wackelt bei schnellem Flug“ (2026-09-24)** — Regressionstests
`drone/client/CameraJitterTest`, `sim/GyroModelTest` und `sim/RealismTogglesTest`

45. **Gyro-Vibration bei der Rotorfrequenz (6.9):** Die Vibration war PT1(180 Hz)-gefiltertes Rauschen mit dem Faktor
    `(ω̄/ω_h)²`. Damit lag sie genau im Band, auf das P- und D-Term reagieren, und wuchs bei Vollgas auf das 6- bis
    8-Fache: der FC regelte gegen Rauschen, die Motoren schüttelten den Rahmen wirklich (FREESTYLE bei 185 km/h:
    Drehrate RMS 16 °/s, Kamera-Winkelzittern 0,04–0,10° RMS und bis 0,3° von Frame zu Frame, bei 60 fps gut sichtbar).
    Kein Realismus-Schalter erreicht die Vibration, deshalb wackelte das Bild auch mit Prop-Wash, Wind und Turbulenz aus.
    Jetzt je Rotor ein Sinus bei ω_i mit Anti-Aliasing und Mittelung über den PID-Teiler; Effektivwert im Mittel gleich.
    Folge: Drehrate RMS bei Vollgas 0,14 °/s, Kamera-Winkelzittern ≤ 0,001° RMS; Schweben mit Rauschen und Wash:
    Drehrate RMS 0,81–0,97 → 0,12–0,26 °/s; FREESTYLE-Vollgas-Reisegeschwindigkeit 185 → 197 km/h (das Gegen-Regeln
    kostete Schub). Die Charakterisierung (`flight-model-characterization.md`) ist nachgezogen.
46. **Schalter „Turbulenz“ nimmt die Böen weg (5.9):** Böen hingen nur an `windScale`; mit Wind an und Turbulenz aus
    blieben sie. Jetzt `σ·windScale·turbulenceScale`. Der Client füllt `env.wind` immer mit dem Weltwind
    (`drone.client.WindModel`); ob er wirkt, entscheidet allein `SimTuning.windScale`. Vorher las `WindModel` den
    Schalter direkt aus der Config und umging damit die Sperre der Physik-Hilfen außerhalb von Kreativ.

**Topspeed-Kalibrierung (2026-09-24)** — Anlass: Spieltest „die Standard-5″ fliegt 200 km/h“

47. **`batterySag = false` behält den Innenwiderstand:** vorher entfielen mit dem Schalter `R_int` und `V_pol`, der Akku
    lieferte bei jeder Last die vollen 4,2 V je Zelle (FREESTYLE mit unendlichem Akku 202 km/h statt 159). Jetzt entfällt
    nur das Absinken der Ruhespannung über den Ladestand (`OCV(1)` statt `OCV(soc)`, auch in `cellVoltageResting` und
    im Schwebe-Schätzer); Einbruch unter Last, Polarisation und C-Raten-Aufschlag bleiben (5.2). `RealismTogglesTest` prüft
    seitdem, dass der Einbruch unter Last auch mit `batterySag = false` bleibt; `CameraJitterTest` misst ab 100 km/h.
48. **Draufsicht `TOP_AREA_OPEN` 1,8 → 2,9:** Topspeeds lagen 10–15 % über realen Werten. Der Schubabfall über die
    Einströmung (`1 − λ`, λ = v_ax/v_0, Zeile 10) begrenzt die Fahrt nur schwach, weil die Props dabei entlasten und
    hochdrehen; die Endgeschwindigkeit bestimmt der Widerstand der Draufsicht, gegen die die Drohne bei 70–75° Neigung
    fliegt. 2,9·A_z·C_d = 0,031 m² beim 5″ entspricht ≈ 260 cm² Draufsicht (Arme, Motoren, Akku, stehende Propblätter)
    mit C_d ≈ 1,2. Schwebeflug, Schub, Punch-Beschleunigung (Spitzen-g), Drehraten und die Schubkurve bei niedriger
    und mittlerer Fahrt sind unverändert; Duct-Rahmen (WHOOP, CINEWHOOP) sind nicht betroffen.

| km/h | WHOOP | CINEWHOOP | FREESTYLE | RACER | LONG_RANGE |
|---|---|---|---|---|---|
| Übersicht (stationär, soc 0,8) alt → neu | 36 → 36 | 62 → 62 | 164 → 140 | 193 → 168 | 121 → 108 |
| geradeaus, frischer Akku, ohne Sag, alt → neu | 43 → 39 | 78 → 65 | 202 → 147 | 243 → 175 | 169 → 113 |
| geradeaus, Standard, Akku ab 100 % (Mittel 27–30 s) alt → neu | 37 → 37 | 60 → 60 | 159 → 136 | 186 → 162 | 124 → 110 |
| Sturzflug senkrecht Vollgas, frischer Akku, alt → neu | 47 → 44 | 83 → 72 | 205 → 153 | 245 → 182 | 171 → 121 |
| Sturzflug ohne Gas alt → neu | 25 → 25 | 35 → 35 | 75 → 59 | 86 → 68 | 82 → 65 |

Nebenwirkungen: Punch-Geschwindigkeit nach 1 s −12 % (FREESTYLE 41,2 → 36,4 m/s, die Spitzen-g bleiben),
Beschleunigung aus dem Schweben auf 100+ km/h etwas langsamer (FREESTYLE nach 1 s 133 → 114 km/h), flache
Fallgeschwindigkeit disarmed 20,8 → 16,4 m/s. Neuer Test `TopSpeedTest` (Geradeausflug mit frischem Akku,
Sturzflug, `batterySag = false`).

49. **Akku-Sag mit zwei Zeitkonstanten und starkem Anstieg bei leerem Akku (0.5.2, Rückmeldung eines FPV-Piloten: Spannung
    fällt zu gleichmäßig, bei fast leerem Akku fehlt der große Einbruch im Punch):** Vorher wirkte der ganze `R_pack` sofort,
    dazu eine langsame Polarisation; der Einbruch war bei vollem und fast leerem Akku praktisch gleich groß (5″, Vollgas im
    freien Flug: 0,58 V je Zelle bei 100 %, 0,63 V bei 15 %) und sprang beim Loslassen sofort zurück. Jetzt (5.2, 5.10):
    - `R_pack` wird geteilt: 65 % sofort, 35 % mit τ = 0,5 s. Die echten Logs mit Stromsensor zeigen als Sofort-Widerstand
      je Zelle 3,3–3,6 mΩ (5″ 6S 1300), 3,2 mΩ (7″ 6S 2200), 27–28 mΩ (2S 450 HV); das Modell hatte 6,0 / 4,4 / 36–38 mΩ und
      hat jetzt 3,9–4,0 / 2,9 / 21 mΩ (`docs/tools/blackbox-calibration.md`, Abschnitt Akku). Die langsame Polarisation
      (0,5·R_pack, 5 s) bleibt.
    - Unter 25 % Ladung (seit Entscheidung 50: unter 15 % mit `L^1,5`) wachsen alle drei Anteile mit `L³`, der schnelle am stärksten (ohmsch ×2,5, schnell ×7, langsam ×5
      bei leer; bei 15 % erst +10 / +38 / +26 %, bei 10 % +32 / +130 / +86 %). Die Form folgt Ersatzschaltbildern für
      Lithium-Polymer-Zellen (Widerstände steigen exponentiell unter ~15 % Ladung); die echten Logs reichen nicht unter
      ~14 % und zeigen bis dorthin keinen Anstieg – das passt zum späten Knie, die Höhe unter 10 % ist aus der Literatur.
    - Die Welt-Regel „Spannungseinbruch“ aus nimmt auch diesen Anstieg weg (Akku bis zuletzt wie voll).
    - Dauerwerte über 25 % Ladung sind bitgleich (alle 150 Airframe-Goldens unverändert): Schub/Gewicht, Vollgasstrom,
      Topspeed und Schweben der Werkbank ändern sich nicht. Flugzeiten der 27 Kalibrier-Aufbauten: 24 unverändert bis
      ±0,5 %, größte Abweichung +2,3 % (aggressiv, 7″ Li-Ion), weil das Knie später, dann steiler einsetzt.
    - Erklärte Änderungen im Flug: Der erste Moment eines Punches mit vollem Akku ist kräftiger (Spitze 2,79 → 2,98 g Whoop,
      4,20 → 4,69 g Cinewhoop, 9,34 → 10,44 g Freestyle, 11,27 → 12,84 g Race, 5,51 → 6,47 g Long Range; Geschwindigkeit
      nach 1 s +7 bis +11 %), nach rund 2 s ist der Einbruch wie vorher. Mit fast leerem Akku ist der Punch schwächer
      (Freestyle bei 10 %: 29,6 N statt 40,9 N nach 0,5 s). Alle Flug-Goldens (`builtin_regression.txt`,
      `builtin_ground_rest.txt`) sind neu.

    Vollgas aus dem Schweben im freien Flug, Einbruch je Zelle nach 0,5 s (in Klammern nach 1,5 s), Stand nach Entscheidung 50,
    `tools/calibration.sh` schreibt die ganze Tabelle nach `build/calibration/battery_sag.txt`:

    | Ladung | Whoop 65 (1S) | Cinewhoop 3″ (4S) | Freestyle 5″ (6S) | Race 5″ (6S) | Long Range 7″ (6S Li-Ion) |
    |---|---|---|---|---|---|
    | 100 % | 0,25 (0,28) V | 0,57 (0,65) V | 0,51 (0,56) V | 0,59 (0,65) V | 0,83 (0,86) V |
    | 50 % | 0,20 (0,22) V | 0,49 (0,54) V | 0,43 (0,47) V | 0,50 (0,54) V | 0,70 (0,69) V |
    | 20 % | 0,20 (0,21) V | 0,48 (0,53) V | 0,42 (0,46) V | 0,49 (0,53) V | 0,66 (0,66) V |
    | 10 % | 0,31 (0,37) V | 0,72 (0,92) V | 0,64 (0,78) V | 0,74 (0,89) V | 0,88 (0,93) V |
    | 5 % | schaltet ab (unter 2,9 V) | 0,89 (1,08) V | 0,86 (1,00) V | 0,97 (1,12) V | 1,00 (1,06) V |

    Erholung beim Freestyle mit 10 %: 0,1 s nach dem Loslassen fehlen noch 0,36 V, nach 1 s 0,16 V, nach 5 s 0,11 V (in 0.5.1
    0,05 V nach 0,1 s). Verworfen: (a) den Widerstand voller Akkus senken, damit ein voller Akku „kaum“ einbricht – die
    echten Logs zeigen bei Vollgas auch mit vollem Akku 0,4–0,8 V je Zelle, und Schub, Topspeed und Flugzeiten sind auf
    diesen Widerstand kalibriert; (b) der Anstieg schon ab 30–40 % Ladung – die Logs zeigen bis 14 % keinen, und die
    Flugzeiten der LiHV- und Li-Ion-Aufbauten würden um mehrere Prozent kürzer; (c) eine schärfere Ruhespannungskurve – das
    Knie ist schon steil (letzte 5 % fünfmal so steil wie die Mitte), die Quellen sind sich über 10–20 % nicht einig;
    (d) Akkutemperatur (echte Packs werden im Flug um 30–50 % niederohmiger) – das Flugmodell kennt keine Akkutemperatur,
    sie müsste mit dem Akku-Item gespeichert und übertragen werden. Tests: `BatterySagTest`, `OpenLoopPhysicsTest`
    (Zeitkonstanten, Punch-Spitzen), `BatteryWarningTest`; Selbsttest „akku-sag:“.

50. **Entladekurve wie ein echter 6S-Entladetest (0.5.2, Rückmeldung desselben Piloten mit dem Diagramm eines Entladetests
    bei 15 C, rund 20 6S-LiPos mit 1200–1800 mAh):** Echt: Start bei 25,2 V, schneller Abfall auf rund 24 V in den ersten
    5 %, dann lange flach bis rund 21,5 V, in den letzten rund 10 % ein steiles Knie bis 18,6 V (3,1 V je Zelle); ein
    Gasstoß kurz vor Schluss lässt die Spannung um 2–3 V mehr einbrechen und sie kommt danach zurück. Das Modell nach
    Entscheidung 49 knickte zu früh und zu weich ab (6S 1300 bei 15 C: 3,45 V je Zelle bei 90 % der Entladung, 3,32 V bei
    95 %), die Ruhespannung von LiPo lag zwischen 10 und 90 % 0,02–0,05 V unter veröffentlichten Tabellen, die von Li-Ion
    unter 30 % bis 0,2 V unter NMC/NCA-Zellen. Jetzt:
    - **Ruhespannung (4.5):** LiPo nach den üblichen Tabellen (3,61 / 3,69 / 3,73 V bei 5 / 10 / 20 %, 4,01 / 4,10 V bei
      80 / 90 %), LiHV und Li-Ion nur unter 50 % angehoben (LiHV 3,62 / 3,71 V bei 5 / 10 %, Li-Ion 3,26 / 3,37 / 3,46 V bei
      5 / 10 / 20 %). Der Wert bei 50 % bleibt bei allen drei gleich: Dort leitet `ParamDerivation` Schwebespannung und
      Steuerautorität ab und `PidAutoTune` die PIDs – so bleiben Handling, Auto-PIDs und alle Airframe-Werte außer den
      Flugzeiten bitgleich.
    - **Anstieg bei leerem Akku (5.2):** erst unter 15 % statt 25 %, dafür mit Exponent 1,5 statt 3 (bei 10 %: ohmsch
      +29 %, schnell +115 %, langsam +77 %; bei 5 % +82 / +327 / +218 %; bei 0 % wie bisher ×2,5 / ×7 / ×5). Die echten
      Logs zeigen bis rund 11–14 % keinen Anstieg (Pavo Pico, `docs/tools/blackbox-calibration.md`).
    - **Ergebnis bei 15 C (6S 1300, `DischargeCurveTest`, Plot `docs/design/realism/battery-discharge.svg`):** 23,9 V
      nach 5 %, 21,8 V bei 50 %, 21,1 V bei 90 %, 20,3 V bei 95 %, 18,6 V bei 96 % der Nennkapazität; die letzten 10 % fallen
      knapp 11-mal so steil wie die Mitte (0.5.1: 5,6-mal, 20,2 V bei 90 %). Bei 1 / 5 / 30 C liefert der Pack 106 / 104 / 91 % der Nennkapazität bis
      3,1 V. Punch am Ende des Packs (5″ Freestyle, 6 % Ladung, 1 s Vollgas aus dem Schweben): 5,4 V Einbruch statt 2,7 V
      bei 50 %, 1 s nach dem Loslassen sind 4,4 V zurück, nach 3 s fehlen noch 0,6 V.
    - **OSD (`osd.VoltageDisplay`):** Die angezeigte Spannung ist wie bei Betaflight geglättet (`vbat_display_lpf_period`
      30, τ ≈ 0,48 s) und blinkt gelb bzw. rot, solange sie unter Warn- bzw. Abschaltspannung der Zellchemie liegt
      (Rückweg 0,03 V). Am Ende des Packs blinkt sie so bei jedem Punch und hört danach wieder auf; ein voller Akku blinkt
      im Punch nicht. Warnungen, Alarme und Ereignisse (`BatteryAlarm`, 2 s) bleiben unverändert.
    - **Flugzeiten** der 32 Kalibrier-Aufbauten gegen 0.5.1: Median +0,4 %, eingebaute Presets −0 bis +1,2 %, Long Range
      7″ aggressiv +4,1 %, Li-Ion-Aufbauten aus Just More Parts aggressiv +6 bis +10 % (die alte Li-Ion-Kurve ließ sie zu
      früh landen); alle bleiben in ihren Referenzbereichen, die Antwort des Besitzers zum 65-mm-Whoop gilt weiter.

    Verworfen: (a) die langsame Polarisation senken (`POL_FRACTION` 0,5 → 0,25), damit die Mitte bei 15 C so hoch liegt
    wie im Diagramm – die 5″-Logs sprechen dafür, die Whoop-, Pavo- und Firefly-Logs dagegen (dort bricht das Modell
    schon jetzt weniger ein als echt), und die Topspeeds der Whoops würden steigen; (b) die Ruhespannung bei 50 % auf die
    Tabellenwerte heben (+0,04 V) – das ändert Steuerautorität und Auto-PIDs aller Drohnen, also das Handling, für einen
    Unterschied, der unter der Streuung echter Packs liegt; (c) die Warnstufen selbst ohne Verzögerung schalten wie
    Betaflight mit `vbat_duration_for_warning` 0 – dann flackern Warntext, Töne und Ereignisse bei jedem Punch ab der
    Hälfte des Akkus; (d) die Anzeige ungeglättet lassen – die Rohspannung springt beim Anfahren der Motoren für 50 ms
    weit tiefer, als ein Flugcontroller sie zeigt. Offen: Akkutemperatur (ein 15-C-Entladetest erwärmt den Pack, der
    Innenwiderstand sinkt dabei; das Modell liegt deshalb in der Mitte des Tests rund 0,4 V unter dem Diagramm).
