# Flugmodell & Flight-Controller — Entwurf „Aero & Spielgefühl"

Paket `de.lardtom.propwash.sim` (reines Java 25, nur `java.*` + `org.joml.*`, keine Minecraft-Imports).
Status: Entwurf, noch kein Code. Alle Zahlen in Abschnitt 3 sind mit einem Python-Prototyp aus den Enum-Werten in
`de.lardtom.propwash.build` nachgerechnet (Skripte liegen nicht im Repo, sie dienten nur dazu, die Zahlen zu belegen).

Schwerpunkt dieses Entwurfs: aerodynamische Effekte (orientierungsabhängiger Widerstand, Propwash/VRS, Wake,
Bodeneffekt, Translational Lift, Wind) und Robustheit/Spielgefühl (fester Substep, Determinismus, NaN-Schutz,
keine Allokation, Kollisionsantwort mit Taumeln, Crash-Schwelle, saubere API für die Spielintegration).

Quellen/Originalität: nur öffentliches Wissen — Impulstheorie für Rotoren, Cheeseman-Bennett und Sanchez-Cuevas
(Bodeneffekt bei Multikoptern), DC-Motormodell, Coulomb-Reibung und Impulsverfahren für starre Körper, Betaflight-
Dokumentation für Rates-Formeln, PID-Struktur, Airmode und Mixer. Kein Code aus anderen Mods.

---

## 0. Leitentscheidungen (Kurzfassung)

| Thema | Entscheidung | Warum |
|---|---|---|
| Zeitschritt | fester Substep `h = 1 ms` (1 kHz; einstellbar auf 500/2000 Hz), Frame-dt wird über einen **Nanosekunden-Akku (long)** abgearbeitet | Frame-dt schwankt (0–50 ms); ein ganzzahliger Akku liefert unabhängig von der FPS immer dieselbe Substep-Folge |
| Integrator | semi-implizites (symplektisches) Euler-Verfahren für Translation; Drehung über den **Drehimpuls L im Weltsystem** plus exakte Exp-Map für das Quaternion; Motoren linear-implizit; Akku↔ESC-Schleife algebraisch gelöst | stabil, billig, ohne Energiedrift beim Taumeln; RK4 bringt bei unstetigem Regler und Kollisionen pro Frame nichts und kostet das Vierfache |
| Regler | läuft in jedem Substep (1 kHz), Sticks werden innerhalb des Frames linear interpoliert und danach mit einem PT2 geglättet (Auto-Cutoff aus der FPS) | Feedforward und D-Term sehen keine Treppenstufen im Frame-Takt |
| Plant-Normierung | PID-Ausgang wird mit `k_auth = α_ref/α_build` skaliert (Referenz FREESTYLE) | derselbe Default-Tune fliegt alle Builds; der Configurator zeigt Betaflight-ähnliche Zahlen |
| Propwash | zwei Quellen: analytischer **Wirbelringzustand (VRS)** aus der Sinkrate entlang der Schubachse + ein **Wake-Feld** aus 40 Wirbelpaketen (Kapseln) für „durch den eigenen Abwind stürzen" | beides allokationsfrei und billig; erzeugt Schubverlust + bandbegrenzte Turbulenz pro Motor, die der PID sichtbar ausregelt |
| Kollision | Minecraft bewegt die Entity mit Blockkollision; die Sim bekommt pro Frame einen `MoveReport` und rechnet pro getroffener Achse einen **Impuls am Hüllpunkt** (Restitution + Coulomb-Reibung → Taumeln); Crash, wenn die Normalgeschwindigkeit ≥ `frame.impactTolerance` ist | keine eigene Weltabfrage in der Sim, keine Tunnel-Effekte (MC sweept pro Achse) |
| Determinismus | `StrictMath` für sin/cos/exp/atan2, eigener seedbarer RNG (xorshift64*), von JOML nur die rein arithmetischen Operationen | bitgleiche Replays/Ghosts auf allen JVMs, unabhängig von `joml.fastmath` |
| Speicher | alle Zustände vorab alloziert (Felder/`double[4]`), Telemetrie und Events als vorab angelegte, veränderliche Objekte | 0 Byte Garbage pro Frame, messbar im Test |

---

## 1. Konventionen und Einheiten

- SI-Einheiten, 1 Block = 1 m, `g = 9.81 m/s²`, Luftdichte `ρ = 1.225 kg/m³` (über `SimEnvironment` änderbar).
- **Welt W** = Minecraft: +X Ost, +Y oben, +Z Süd (rechtshändig).
- **Körper B** (rechtshändig): **+X_B = links**, **+Y_B = oben (Schubrichtung)**, **+Z_B = vorne (Nase/Kamera)**.
  Ursprung = Schwerpunkt (CoM).
- Orientierung `q` (Quaterniond, normiert): Körper → Welt, `v_W = q ⊗ v_B ⊗ q*`, `R = R(q)`.
- Kurs `ψ = atan2(f.x, f.z)` mit `f = R·(0,0,1)`. ψ = 0 heißt Blick nach +Z (Süd), ψ wächst in Richtung +X (Ost),
  also bei einer Linksdrehung. Minecraft-Yaw in Grad = `−ψ·180/π`. Steht die Nase fast senkrecht (`|f.y| > 0.995`),
  wird der Kurs aus `−sign(f.y)·R·(0,1,0)` bestimmt (die Oberseite zeigt dann nach hinten bzw. vorn), damit er stetig bleibt.
- **FC-Achsen** (Betaflight-Semantik, Stick positiv = Rate positiv):
  Roll + = rechte Seite nach unten, Pitch + = Nase nach unten (Stick nach vorn), Yaw + = Nase nach rechts.
  Zuordnung zur Körper-Winkelgeschwindigkeit `ω_B = (ω_x, ω_y, ω_z)` in rad/s (Rechte-Hand-Regel):
  `roll = +ω_z`, `pitch = +ω_x`, `yaw = −ω_y`. (Geprüft: +ω_z hebt die linke Seite (+X), +ω_x senkt die Nase, +ω_y dreht die Nase nach +X, also nach links.)
- **Motoren** (Betaflight-Quad-X-Nummerierung, `s = a/√2`, `y_m` = Höhe der Rotorebene über dem CoM):

| Motor | Position `r_i` (B) | Drehsinn von oben, Props-in | `σ_i` (+1 = CCW) | Roll-Mix | Pitch-Mix | Yaw-Mix |
|---|---|---|---|---|---|---|
| M1 hinten rechts | (−s, y_m, −s) | CW | −1 | −1 | +1 | −1 |
| M2 vorne rechts | (−s, y_m, +s) | CCW | +1 | −1 | −1 | +1 |
| M3 hinten links | (+s, y_m, −s) | CCW | +1 | +1 | +1 | +1 |
| M4 vorne links | (+s, y_m, +s) | CW | −1 | +1 | −1 | −1 |

  Die Mixer-Spalten folgen aus der Geometrie: `roll_i = x_i/s`, `pitch_i = −z_i/s`, `yaw_i = σ_i`.
  Schubmoment `τ_B = Σ r_i × (0, T_i, 0) = Σ (−z_i·T_i, 0, x_i·T_i)`, Reaktionsmoment des Motors `τ_y = −σ_i·Q_i`.
  `FcConfig.propsOut = true` negiert alle `σ_i` und die Yaw-Spalte.

---

## 2. Architektur und Datenfluss

```
 Spiel (Client, pro gerendertem Frame)                        de.lardtom.propwash.sim
 ─────────────────────────────────────                        ─────────────────────────
 SimInput (Sticks, Modus, Arm)  ─┐
 SimEnvironment (Wind, Boden-   ─┼─► DroneSim.step(frameDt, in, env, out)
   abstand, Reibung, Fluid ...)  │      ├─ Eingänge prüfen, dt → ns-Akku → n Substeps (≤ 60)
                                 │      ├─ pro Substep (1 kHz):
                                 │      │    FlightController ─ Duty[4] ─► MotorBank ◄─► Battery (algebraisch)
                                 │      │    RotorAero (Zuströmung, TL, Bodeneffekt, Propwash) + BodyAero + Wind
                                 │      │    RigidBody.integrate (v, p, L, q)       + NaN-Wächter
                                 │      ├─ alle 4 Substeps (250 Hz): WakeField, Böen, Temperaturen
                                 │      └─ StepResult: Verschiebung dx,dy,dz (Welt), sichtbare Orientierung
 entity.move(dx,dy,dz)  ◄────────┘
 MoveReport (gewünscht/erreicht, Achsentreffer, Oberfläche) ─► DroneSim.afterMove(report)
                                        ├─ Position synchronisieren
                                        ├─ CollisionSolver: Impuls je Treffer-Achse (Hüllpunkt, e, μ)
                                        └─ Events: CRASH/HARD_IMPACT/PROP_STRIKE/LANDED …
 HUD/OSD/Sound/Configurator  ◄──── Telemetry (live) + EventQueue (Ringpuffer)
```

Die Sim läuft nur auf dem Client des Piloten. Zuschauer bekommen Position und Orientierung übers Netz; ein kompakter
`SimSnapshot` (nur primitive Felder) kann bei Bedarf mitgeschickt werden (Motordrehzahlen für Sound, Propwash für Effekte).

---

## 3. Parameterableitung aus `DroneBuild`

`AirframeSpec.derive(DroneBuild build, SimTuning t)` berechnet alle physikalischen Konstanten einmal (etwa 50 µs, beim
Bau bzw. Teiletausch an der Werkbank). Die Konstanten mit `k_…` liegen in `SimTuning` (JSON-fähig), damit sich
Spielgefühl und Realismus ohne Codeänderung einstellen lassen.

### 3.1 Masse und Geometrie
- `m = totalMassGrams()/1000` [kg]
- `a = frame.armLengthMeters()` [m], Motorversatz je Achse `s = a/√2`
- Propeller `D = prop.diameterMeters()`, `R = D/2`, Kreisfläche `A = πR²` [m²]
- Akku als Quader aus der Masse (Dichte 2.0 g/cm³, Seitenverhältnis L:B:H = 2.2:1.3:1), oben auf dem Rahmen:
  Mitte bei `y_b = 12 mm + H/2` über der Rahmenebene.
- Schwerpunkthöhe `y_com = (m_akku·y_b + (m_stack + m_video)·0.010 m)/m`. Die Rotorebene liegt 15 mm über der
  Rahmenebene: `y_m = 0.015 − y_com`.

### 3.2 Trägheitstensor (diagonal, im CoM)
Beiträge (Pitch = um X, Yaw = um Y, Roll = um Z):
- Motor+Prop als Punktmassen `m_mp` bei Radius a: `I_xx += 2 m_mp a²`, `I_zz += 2 m_mp a²`, `I_yy += 4 m_mp a²`
- Rahmen offen: 60 % Arme (Stäbe vom Zentrum bis a): `I_xx, I_zz += m_arm a²/6`, `I_yy += m_arm a²/3`;
  40 % Platte (Quadrat mit Kantenlänge 0.8a): `I_xx, I_zz += m_pl (0.8a)²/12`, `I_yy += m_pl (0.8a)²/6`
- Rahmen mit Ducts: 50 % Duct-Ringe (Radius `R + 4 mm`) bei a, 20 % Arme, 30 % Platte;
  Ringe: `I_xx, I_zz += m_ring (a²/2 + r_d²/2)`, `I_yy += m_ring (a² + r_d²)`
- Akku-Quader + Steiner-Anteil `m_akku (y_b − y_com)²` auf I_xx und I_zz; der übrige Rest wird mit `y_com²` verschoben.

### 3.3 Motor + ESC
- `K_v = KV·2π/60` [rad/(s·V)], `K_t = K_e = 1/K_v` [N·m/A = V·s/rad]
- `R_m = motor.resistanceOhm + R_esc` mit `R_esc` = 30 mΩ (AIO_WHOOP), 12 mΩ (AIO_20), 6 mΩ (STACK_30)
- Leerlaufstrom (Eisen- und Lagerverluste) `I_0 = 0.035·motor.maxCurrent`
- Stromgrenze pro Motor (Strombegrenzung im ESC) `I_lim = min(1.25·motor.maxCurrent, 1.5·stack.escCurrentPerMotor)`
- Rotorträgheit `J = 0.25·m_prop·R² + 0.5·(0.45·m_motor)·r_bell²` mit `r_bell = (Stator-Ø/2 + 3 mm)`.
  Der Stator-Ø kommt aus den ersten beiden Ziffern von `statorLabel` („2207" → 22 mm, „2306.5" → 23 mm).
- **Gameplay-Klemme für die Motorträgheit:** die Zeitkonstante im Schwebeflug
  `τ_m = J / (K_t·K_e/R_m + 2·k_Q·ω_hover)` wird auf `[6 ms, 40 ms]` begrenzt, indem `J_eff = J·clamp(τ_ziel/τ_m)`
  gesetzt wird. Betroffen ist nur der WHOOP: physikalisch 114 ms (0802-Motor mit 0,6 Ω), was sich zäh anfühlt;
  mit der Klemme sind es 40 ms. Er bleibt damit der trägste Build, ist aber fliegbar.

### 3.4 Propeller
Aus der Kennlinie in `PropType` (`T = c_T ρ n² D⁴`, `P = c_P ρ n³ D⁵`, n in U/s, ω = 2πn):
- `k_T = c_T·ρ·D⁴/(2π)² · k_duct` [N·s²/rad²], mit `k_duct = 1.20` bei Rahmen mit Ducts, sonst 1
- `k_Q = c_P·ρ·D⁵/(2π)³` [N·m·s²/rad²] (aus `Q = P/ω`)
- Steigung `p = pitchInch·0.0254` [m], Steigungsgeschwindigkeit `v_p(ω) = ω/(2π)·p` [m/s]
- Induzierte Geschwindigkeit im Schwebeflug `v_h = √(m g /(8 ρ A))` [m/s] (Referenz für VRS und Translational Lift)

### 3.5 Akku und Nebenverbraucher
- Zellenzahl n, `V_voll = n·chemistry.fullCellVoltage`
- `R_pack = n·cellResistanceOhm + 3 mΩ` (Kabel und Stecker), Polarisation `R_pol = 0.5·n·R_zelle`, `τ_pol = 4 s`
- Ruhespannung pro Zelle `OCV(SoC)`, lineare Interpolation in 10-%-Schritten (SoC 0 → 1):
  - LIPO: 3.30 3.58 3.68 3.73 3.77 3.81 3.85 3.91 3.98 4.07 4.20
  - LIHV: 3.30 3.60 3.71 3.77 3.82 3.87 3.93 4.00 4.08 4.19 4.35
  - LION: 2.80 3.20 3.36 3.46 3.55 3.63 3.72 3.81 3.92 4.05 4.20
  - Unter SoC 0 fällt die Kurve mit 3 V pro Einheit SoC weiter; die Spannung ist nach unten bei 0 begrenzt.
- Nebenverbraucher (FC, VTX, Kamera) `P_aux = 1.0 W + 0.3 W/g·video.massGrams()` → 2.2 W (ANALOG_NANO),
  3.4 W (ANALOG_RACE), 3.55 W (DIGITAL_LITE), 7.9 W (DIGITAL_HD)

### 3.6 Luftwiderstand und Hülle
- Anströmflächen im Körpersystem: `A_z = frame.frontalAreaM2` (von vorn), `A_x = 1.15·A_z` (von der Seite),
  `A_y = 1.4·A_z + k_disc·4πR²` (von oben) mit `k_disc` = 0.12 (offen, stehende Props) bzw. 0.40 (Ducts).
  `C_d = frame.dragCoefficient` für alle Achsen.
- Druckpunkt `r_cp = (0, 0, −0.1·a)` (leicht hinter dem CoM, das gibt etwas Windfahnen-Stabilität beim Gieren)
- Hüllpunkte für Kollisionen (Körpersystem, vor der CoM-Verschiebung): 4 Propspitzen (Motorposition + radial
  `R`, bzw. `R + 4 mm` bei Ducts, bei y = 15 mm), 4 Motorunterseiten (y = −6 mm), 2 Akkukanten oben (`z = ±L/2`),
  Kameranase (0, 12 mm, 0.6·s), Bodenplatte (0, −6 mm, 0). Das sind 12 Punkte in einem festen Array.
- Empfohlene Minecraft-Hitbox (achsparallel, dreht sich nicht mit): Breite `2s + D (+8 mm bei Ducts)`,
  Höhe `H_akku + 22 mm`. `maxUpStep = 0`.

### 3.7 Zahlen für die 5 Presets

Stationäre Werte mit vollem Akku, ohne Wind und ohne Bodeneffekt. „Duty" ist das effektive ESC-Tastverhältnis,
„Stick" der Gashebel bei 5,5 % Idle und linearer Gaskurve.

| Größe | WHOOP | CINEWHOOP | FREESTYLE | RACER | LONG_RANGE |
|---|---|---|---|---|---|
| Masse [g] | 29.6 | 231.7 | 525.2 | 430.2 | 846.2 |
| Arm a / Motorversatz s [mm] | 32.5 / 23.0 | 71.0 / 50.2 | 112.5 / 79.5 | 107.5 / 76.0 | 147.5 / 104.3 |
| Prop D / R [mm], Ducts | 31 / 15.5, ja | 76 / 38, ja | 130 / 65 | 130 / 65 | 178 / 89 |
| y_com / y_m [mm] | 6.9 / 8.1 | 8.7 / 6.3 | 12.2 / 2.8 | 11.8 / 3.2 | 16.8 / −1.8 |
| I_xx Pitch [10⁻⁶ kg·m²] | 9.5 | 345.6 | 1352.5 | 1102.0 | 3377.8 |
| I_yy Yaw [10⁻⁶ kg·m²] | 14.9 | 612.5 | 2398.4 | 1971.7 | 5980.2 |
| I_zz Roll [10⁻⁶ kg·m²] | 9.3 | 333.9 | 1291.6 | 1057.1 | 3184.5 |
| K_t [mN·m/A] | 0.434 | 2.076 | 5.457 | 5.134 | 7.346 |
| R_m [mΩ] / I_0 [A] / I_lim [A] | 630 / 0.12 / 4.38 | 152 / 0.63 / 22.5 | 61 / 1.58 / 56.3 | 66 / 1.47 / 52.5 | 76 / 1.58 / 56.3 |
| J [10⁻⁹ kg·m²] (J_eff) | 43.1 (15.1) | 852 | 5997 | 6008 | 16522 |
| τ_Motor im Schwebeflug [ms] | 114 → **40** | 27.2 | 11.7 | 14.3 | 21.6 |
| k_T [N·s²/rad²] | 3.439e-9 | 1.304e-7 | 1.0635e-6 | 1.0635e-6 | 2.8035e-6 |
| k_Q [N·m·s²/rad²] | 8.483e-12 | 7.263e-10 | 1.1002e-8 | 1.1002e-8 | 3.1769e-8 |
| Steigung p [mm] | 30.5 | 76.2 | 116.8 | 116.8 | 88.9 |
| V_voll [V] / R_pack [mΩ] | 4.35 / 63 | 16.80 / 43 | 25.20 / 36 | 25.20 / 39 | 25.20 / 111 |
| Kapazität [mAh] / Dauerstrom [A] | 300 / 13.5 | 650 / 61.8 | 1300 / 156 | 1100 / 132 | 4000 / 44 |
| Leerlaufdrehzahl KV·V [U/min] | 95 700 | 77 280 | 44 100 | 46 872 | 32 760 |
| Vollgas: Drehzahl [U/min] | 71 721 | 57 266 | 34 099 | 34 471 | 19 348 |
| Vollgas: Schub/Motor [gf] | 20 | 478 | 1382 | 1413 | 1173 |
| **Schub/Gewicht** | **2.67** | **8.26** | **10.53** | **13.13** | **5.55** |
| Vollgas: Akkustrom [A] | 5.1 | 54.5 | 112.5 | 121.2 | 79.7 |
| Vollgas: Busspannung [V] (pro Zelle) | 4.03 (4.03) | 14.46 (3.61) | 21.15 (3.52) | 20.47 (3.41) | 16.35 (2.73) |
| ESC- / Akku-Auslastung bei Vollgas | 24 % / 38 % | 53 % / 88 % | 50 % / 72 % | 53 % / 92 % | 35 % / **181 %** |
| Schweben: Duty / Stick | 0.545 / 0.519 | 0.279 / 0.237 | 0.250 / 0.206 | 0.213 / 0.168 | 0.272 / 0.229 |
| Schweben: Drehzahl [U/min] | 43 875 | 19 931 | 10 509 | 9 511 | 8 216 |
| Schweben: Akkustrom inkl. Aux [A] / Leistung [W] | 1.72 / 7 | 2.69 / 45 | 4.45 / 112 | 3.30 / 83 | 5.67 / 140 |
| v_h [m/s] | 6.27 | 7.15 | 6.29 | 5.70 | 5.83 |
| Flugzeit Schweben / Dauervollgas (80 % Kapazität) | 8.4 min / 2.5 min | 11.6 min / 34 s | 14.0 min / 33 s | 16.0 min / 26 s | 33.9 min / 2.4 min |
| α Roll / Pitch / Yaw pro Einheit Duty-Differenz [rad/s²] | 2346 / 2288 / 157 | 2377 / 2297 / 144 | 2533 / 2419 / 177 | 2850 / 2733 / 208 | 1881 / 1774 / 109 |
| k_auth Roll / Pitch / Yaw | 1.08 / 1.06 / 1.13 | 1.07 / 1.05 / 1.23 | 1.00 / 1.00 / 1.00 | 0.89 / 0.88 / 0.85 | 1.35 / 1.36 / 1.63 |
| A vorn / seitlich / oben [cm²], C_d | 35 / 40 / 61, 1.10 | 200 / 230 / 353, 1.00 | 120 / 138 / 232, 0.90 | 90 / 104 / 190, 0.75 | 160 / 184 / 343, 0.90 |
| Fallgeschwindigkeit flach, unscharf [m/s] | 8.4 | 10.3 | 20.1 | 22.0 | 20.9 |
| Höchstgeschwindigkeit horizontal [m/s] (km/h) bei Neigung | 11.8 (42) @53° | 25.4 (91) @76° | 45.4 (163) @73° | 51.9 (187) @74° | 30.6 (110) @51° |
| max. Steigrate senkrecht [m/s] | 9.0 | 23.3 | 40.8 | 47.1 | 23.0 |
| Neigung / Duty bei 10 m/s Reiseflug | 41° / 0.81 | 30° / 0.35 | 11° / 0.25 | 10° / 0.22 | 9° / 0.28 |
| VRS-Band Sinkrate (0.8–1.4·v_h) [m/s] | 5.0–8.8 | 5.7–10.0 | 5.0–8.8 | 4.6–8.0 | 4.7–8.2 |
| Bodeneffekt f(z=R) / f(z=D) / f(z=2D) | 1.098 / 1.038 / 1.013 | 1.107 / 1.042 / 1.014 | 1.114 / 1.044 / 1.014 | 1.118 / 1.045 / 1.014 | 1.118 / 1.045 / 1.014 |
| Bodeneffekt > 5 % unterhalb von | 2.6 cm | 6.7 cm | 11.9 cm | 12.1 cm | 16.6 cm |
| Crash-Geschwindigkeit (Rahmen) [m/s] | 14 | 11 | 8 | 7 | 8 |
| Restitution e₀ | 0.45 | 0.45 | 0.25 | 0.25 | 0.25 |
| Hitbox B × H [mm] | 85 × 33 | 184 × 46 | 289 × 55 | 282 × 53 | 387 × 64 |

Die Höchstgeschwindigkeit ergibt sich aus Kräftegleichgewicht bei Vollgas mit Zuströmung, Translational Lift,
H-Kraft, Rumpfwiderstand und Spannungseinbruch (Abschnitt 4). Sie wird durch die Steigungsgeschwindigkeit (RACER/
FREESTYLE ≈ 67 m/s, LONG_RANGE nur 29 m/s wegen 3,5″-Steigung) und den Widerstand begrenzt.

### 3.8 Hinweise zu den Daten (für die Besitzer von `build`)
- **LONG_RANGE:** Bei Vollgas sackt der Li-Ion-Akku auf 2.73 V/Zelle ab (181 % des Dauerstroms). Das passt zur
  Warnung `BATTERY_WEAK` in `DroneBuild.warnings()`. Die Sim simuliert das ehrlich: starker Spannungseinbruch,
  aber kein Abschalten (Brownout erst unter 3.0 V Busspannung, betrifft praktisch nur 1S).
- **WHOOP/CINEWHOOP:** Die Schwebezeiten (8–12 min) liegen über realen Werten (3–5 min), weil `c_T/c_P` der
  kleinen Props so effizient sind wie bei 5″-Props (bei kleinen Reynoldszahlen sind echte Props schlechter).
  Vorschlag: `P31_4B.powerCoefficient` 0.060 → 0.085 und `P3_3B` 0.058 → 0.070, oder `I_0` für kleine Motoren
  erhöhen. Das Flugmodell übernimmt die Enum-Werte unverändert; hier wird nichts heimlich korrigiert.
- Die WHOOP-Motorzeitkonstante (114 ms) wird bewusst geklemmt (3.3).

---

## 4. Physikmodell (pro Substep, h = 1 ms)

Notation: `v_W` Geschwindigkeit, `w_W` Wind am Ort der Drohne, Luftgeschwindigkeit `u_B = Rᵀ(v_W − w_W)`,
Rotor i: lokale Luftgeschwindigkeit `u_i = u_B + ω_B × r_i`, axial `v_ax,i = u_i·ŷ` (> 0 = Rotor bewegt sich
„nach oben" gegen die Luft, wie beim Steigen), Scheibenebene `u_ed,i = u_i − v_ax,i·ŷ`, `v_ed,i = |u_ed,i|`.

### 4.1 Motor und ESC (elektromechanisch, pro Motor)
```
Klemmenspannung   U_i = d_i · V_bus                                   [V]   (d_i = Duty 0..1)
Strom             I_i = clamp((U_i − K_e·ω_i)/R_m, −0.3·I_lim, I_lim) [A]   (negativ = aktives Bremsen)
Momente           J_eff·dω_i/dt = K_t·I_i − Q_i − K_t·I_0·sgn(ω_i)    [N·m]
Luftmoment        Q_i = k_Q·ω_i²·f_Q,i·(ρ/1.225)
```
Linear-implizites Euler-Verfahren (für jedes h stabil, ohne exp):
`c_i = K_t·K_e/R_m (nur ohne Strombegrenzung) + 2·k_Q·ω_i·f_Q`, `ω_i ← max(0, ω_i + h·f(ω_i)/(1 + h·c_i/J_eff))`.
Obergrenze `ω_i ≤ 1.15·K_v·V_voll`. Im Turtle-Modus ist `ω_i < 0` erlaubt (Abschnitt 7.7).
Das auf den Rahmen wirkende Reaktionsmoment ist `τ_y,i = −σ_i·K_t·(I_i − I_0·sgn ω_i)`. Es enthält automatisch das
Luftmoment **und** das Beschleunigungsmoment `J·ω̇`. Genau das gibt Yaw-Autorität beim Hochdrehen und den typischen
Yaw-Ruck beim Gasstoß.

### 4.2 Akku: algebraische Schleife Akku ↔ ESC
Eine naive Kopplung mit der Busspannung des vorigen Substeps ist **instabil**: Die Schleifenverstärkung
`R_int·Σd_i²/(R_m·η)` liegt beim FREESTYLE bei Vollgas bei 2.4 (> 1), die Spannung würde oszillieren. Deshalb wird
`V_bus` pro Substep geschlossen gelöst (η_ESC = 0.97):
```
I_bat = Σ d_i·I_i/η + P_aux/V_bus,prev
V_bus = n·OCV(SoC) − V_pol − R_int·I_bat,   R_int = R_pack·(1 + 0.5·(1 − SoC)²)
⇒ V_bus = (n·OCV − V_pol + R_int·Σ d_i K_e ω_i/(R_m η) − R_int·I_aux) / (1 + R_int·Σ d_i²/(R_m η))
```
Danach werden die `I_i` mit Begrenzung berechnet. Greift eine Begrenzung, folgt ein zweiter Durchgang mit den
begrenzten Strömen (für die betroffenen Motoren ist I dann fest). Anschließend:
`mAh += I_bat·h/3.6`, `SoC = 1 − mAh/Kapazität`, `V_pol ← V_pol + (I_bat·R_pol − V_pol)·(1 − e^(−h/τ_pol))`
(Koeffizient vorberechnet). `I_bat ≥ −0.2·Dauerstrom` (Rekuperation begrenzt).
**Brownout:** `V_bus < 3.0 V` länger als 5 ms → FC-Neustart → Disarm `BROWNOUT` (0.5 s Sperre). Das trifft den
1S-WHOOP bei leerem Akku und Vollgas, 6S-Builds praktisch nie.

### 4.3 Rotorschub mit Zuströmung
```
T_i = k_T·ω_i²·(ρ/1.225) · f_ax,i · f_tl,i · f_ge · f_wash,i          [N]
f_ax = max(0, 1 − v_ax/v_p)                  für v_ax ≥ 0 (Steigen: Blattanstellwinkel sinkt)
f_ax = 1 + 0.5·min(−v_ax/v_p, 0.6)           für v_ax < 0 (Sinken: mehr Anstellwinkel, max. +30 %)
f_Q  = max(0.05, 1 − 0.6·v_ax/v_p) bzw. 1 + 0.3·min(−v_ax/v_p, 0.6)   (Last sinkt beim Steigen schwächer als der Schub)
v_p  = max(1 m/s, ω_i/(2π)·p)
```
Durch den Term `ω_B × r_i` in `u_i` entsteht automatisch **Rotordämpfung**: Beim Rollen bewegt sich die eine Seite
nach oben (weniger Schub), die andere nach unten (mehr Schub), und das dämpft die Drehung.
Weil die Motorlast bei hoher Vorwärtsgeschwindigkeit sinkt, drehen die Motoren bei Speed höher („Props entlasten").

### 4.4 Translational Lift und Rotor-H-Kraft
- `μ_e,i = v_ed,i/v_h`, `f_tl,i = 1 + k_tl·μ²/(1 + μ²)` mit `k_tl` = 0.12 (offen) bzw. 0.06 (Ducts)
  → ab etwa 1·v_h (≈ 6 m/s) spürbar mehr Schub; „Aufschwimmen" beim Übergang vom Schweben in den Vorwärtsflug.
- H-Kraft (Blattschlag/Rotorwiderstand, linear in der Geschwindigkeit):
  `F_H,i = −κ_H·T_i·u_ed,i/max(ω_i·R, 5 m/s)`, `κ_H = 0.7`. Sie greift am Rotor an (Hebel `r_i`) und liefert bei
  kleinen Geschwindigkeiten den Hauptteil des Widerstands (FREESTYLE bei 10 m/s ≈ 0.5 N, Rumpf ≈ 0.66 N →
  11° Neigung, siehe Tabelle).

### 4.5 Bodeneffekt (Multikopter-Näherung nach Sanchez-Cuevas, ohne Rumpfterm)
```
z   = max(env.wakeSurfaceDistance + y_m, 0.5·R)  (Raycast des Spiels vom CoM entlang −ŷ_B; + y_m = ab Rotorebene)
d   = 2s (Abstand benachbarter Rotoren)
f_ge = 1 / (1 − (R/4z)² − R²·z/((d² + 4z²)^1.5) − ½R²·z/((2d² + 4z²)^1.5)),   auf ≤ 1.4 begrenzt
f_ge ← 1 + groundEffectScale·(f_ge − 1)
```
Weil der Raycast entlang der Schubachse läuft, entsteht derselbe Effekt auch an Wänden, wenn man seitlich daran
entlang „reitet". Telemetrie `inGroundEffect = f_ge > 1.02`.
Ein Raycast pro Frame reicht; ein Rotor über einer Kante wird nicht getrennt behandelt (siehe Abschnitt 14).

### 4.6 Propwash: Wirbelringzustand + Wake-Feld + Turbulenz
**(a) Wirbelringzustand (VRS)** aus mittlerer Axial- und Scheibengeschwindigkeit (CoM):
```
μ_d = −v̄_ax/v_h (Sinken entlang der Schubachse), μ_e = v̄_ed/v_h
w_vrs = S(0.25, 0.75, μ_d) · (1 − S(1.5, 2.3, μ_d)) · exp(−μ_e²) · (0.4 + 0.6·clamp(ΣT/(m g), 0, 1))
S(a,b,x) = smoothstep
```
Seitliche Fahrt bläst den Wirbelring weg (`exp(−μ_e²)`: bei 1.5·v_h seitlich bleiben nur noch 10 %).

**(b) Wake-Feld** (Ringpuffer mit 40 „Wirbelpaketen", reine `double[]`-Felder):
- Takt 40 ms (auf der Substep-Uhr, damit deterministisch). Ausgestoßen wird nur bei `ΣT > 0.15·m g`.
  Neues Paket: Mittelpunkt `c = p − 0.5·D·ŷ_W`, induzierte Geschwindigkeit nach Impulstheorie mit Steigen
  `v_i = −v_c/2 + √((v_c/2)² + ΣT/(8ρA))` (`v_c = max(0, v̄_ax)`), Paketgeschwindigkeit `u = w_W − 2·v_i·ŷ_W`
  (Fernfeld ≈ 2·v_i), Stärke `st = clamp(ΣT/(m g), 0, 2)/2`, Radius `r₀ = s + R` (s = Motorversatz).
- Bei 250 Hz: `c += u·Δt`, `u ← w + (u − w)·e^(−Δt/0.8 s)`, `r = r₀(1 + 0.8·Alter)`, `st ← st·e^(−Δt/1.2 s)`;
  verworfen wird bei `st < 0.03`.
- Abfrage bei 250 Hz über **Kapseln** zwischen aufeinanderfolgenden Paketen (so entstehen bei schnellem Flug keine
  Lücken). Pakete jünger als 0.15 s werden übersprungen (den aktuellen Abwind deckt (a) ab):
  `overlap = 1 − S(0.6r, 1.3r, Abstand)`, Richtung `γ = 0.25 + 0.75·clamp(−(v_W − u)·(R·ŷ)/(0.5·v_h), 0, 1)`
  (voll wirksam, wenn man sich entgegen der eigenen Schubrichtung in den Abwind bewegt), `w_wake = max(st·overlap·γ)`.
- Klassische Szenarien: Punch-out → Flip → zurück durch die eigene Säule stürzen; Split-S; Powerloop über dem
  Gate; langsames senkrechtes Sinken.

**(c) Wirkung**
```
w = propwashScale · (1 − (1 − w_vrs)(1 − w_wake)),   auf [0, 1] begrenzt
f_wash,i = (1 − k_loss·w) · (1 + σ_t·w·n_i(t))
k_loss = 0.35 (offen) / 0.20 (Ducts),   σ_t = 0.30 / 0.15 · turbulenceScale
```
`n_i(t)` ist pro Motor unabhängiges, bandbegrenztes Rauschen (Biquad-Bandpass, Güte 0.7, Mittenfrequenz
`f_t = 12 Hz·√(0.127/D)` → WHOOP 24 Hz, 5″ 12 Hz, 7″ 10 Hz, auf Effektivwert 1 normiert). Gespeist wird es vom
seedbaren `SimRandom` (Summe zweier Gleichverteilungen). Die unterschiedlichen Schübe erzeugen Roll-, Pitch- und
Yaw-Störungen, die der Regler mit dem D-Term ausregelt. Das ergibt das sichtbare Propwash-Wackeln im Kamerabild,
ohne dass eine Bewegung „gescriptet" ist. Telemetrie: `propwash` = PT1(10 Hz) von w, dazu `vrs` und `wake` einzeln.

### 4.7 Rumpfwiderstand (orientierungsabhängig) und Rotationsdämpfung
```
F_body,B = −½·ρ·C_d·dragScale · (A_x·u_x, A_y·u_y, A_z·u_z) · |u_B|     [N], angreifend bei r_cp
τ_cp     = r_cp × F_body,B
τ_rot,k  = −c_rot·ω_k·|ω_k|,  c_rot = ½·ρ·C_d·A_y·a³/4                   [N·m]  (nur fürs unscharfe Taumeln relevant)
```
Weil die Kraft pro Achse quadratisch ist, ist sie nicht exakt entgegengerichtet zu `u` (eine schräg angeströmte
Platte erzeugt Querkraft). Das ist gewollt und fühlt sich bei Messerflug und Kurven natürlich an. Invariante für
den Test: `F·u ≤ 0`.

### 4.8 Wind und Böen
- `env.windX/Y/Z` [m/s] mit Höhenprofil `w(h) = w_ref·clamp((max(h, 0.5)/10)^0.143, 0.35, 1.3)`, h = Höhe über Grund.
- Böen: pro Achse PT1-gefiltertes Rauschen (τ = 1.2 s, Takt 250 Hz, SimRandom), Standardabweichung
  `σ_g = gustIntensity·(0.5 + 0.3·|w_ref|)`, vertikal halbiert. `env.windShelter` (0..1, z. B. aus einem Raycast
  gegen den Wind) skaliert beides.

### 4.9 Kreiselmoment der Rotoren
`h_rot = Σ σ_i·J_eff·ω_i·ŷ` (Drehimpuls der Rotoren), `τ_gyro = −ω_B × h_rot`. Das ist klein, aber bei
Yaw-Drehungen mit schnellen Roll- und Pitch-Manövern sichtbar.

### 4.10 Starrkörper-Integration
```
F_W = R·(Σ (T_i·ŷ + F_H,i) + F_body,B) + m·(0, −g, 0)
τ_B = Σ r_i × (T_i·ŷ + F_H,i) + Σ (0, τ_y,i, 0) + τ_cp + τ_rot + τ_gyro + τ_ground (7.6)

v_W ← v_W + h·F_W/m ;  p ← p + h·v_W                       (semi-implizit)
L_W ← L_W + h·R·τ_B ;  ω_W = R·I_B⁻¹·Rᵀ·L_W                  (Kreiselterm ω×Iω steckt implizit in der Rotation von R)
q   ← exp(½·h·ω_W) ⊗ q ;  q normieren ;  ω_B = Rᵀ·ω_W
exp: θ = |ω_W|·h;  θ < 1e-9 ? (1, ½h·ω_W) : (cos θ/2, sin θ/2 · ω̂)      (StrictMath)
```
Beschleunigungsmesser: `a_spec,B = Rᵀ(F_W − m·g_W)/m`, `gLoad = a_spec,B·ŷ/9.81` (Schweben = 1.0).
Fehlerbetrachtung: freier Fall nach 1 s → 0.1 % Positionsfehler; die Rotation ist bei konstantem ω im Schritt
exakt; der Drehimpuls bleibt ohne äußeres Moment exakt erhalten.

---

## 5. Flight-Controller

### 5.1 Signalfluss pro Substep
```
Sticks (Frame) ─ Interpolation im Frame ─ Deadband ─ PT2-RC-Smoothing ─┬─ Rates-Kurve (ACRO)
                                                                        ├─ Winkelsollwert (ANGLE/Autopilot)
                                                                        └─ Mischung (HORIZON)
   → Sollrate r_sp [°/s] ─ PID (P, I mit iterm relax + Anti-Gravity, D auf Messwert, FF) ─ ×k_auth
   → Mixer (Desaturierung, Airmode, Idle, Gaskurve, Throttle Limit, Sag-Kompensation) → Duty d_i
Gyro = ω_B in FC-Achsen [°/s] → PT1 (gyroLpfHz); optional Rauschen; D-Term-PT1/PT2 (dtermLpfHz)
```

### 5.2 Stick-Aufbereitung
- Pro Frame werden `roll, pitch, yaw ∈ [−1, 1]` und `throttle ∈ [0, 1]` gelesen. NaN wird zu 0, alles wird begrenzt.
- Im Substep k von n: `x = x_prev + (x_cur − x_prev)·(k+1)/n` (linear im Frame).
- RC-Smoothing: PT2 mit Cutoff `f_rc = clamp(FPS_f/2.5, 15, 60) Hz` (FPS_f = PT1-gefilterte Framerate) bzw. fest
  `filters.rcSmoothingHz`. Getrennter PT1 (gleicher Cutoff) für die Feedforward-Ableitung.
  Das ist für variable FPS entscheidend: Ohne Glättung würden FF und D bei jedem Frame-Wechsel springen.

### 5.3 Rates (Formeln aus der öffentlichen Betaflight-Doku)
**BETAFLIGHT** (pro Achse `rcRate`, `superRate`, `expo`; x = Stick):
```
x' = x·|x|³·expo + x·(1 − expo)
rc = rcRate > 2 ? rcRate + 14.54·(rcRate − 2) : rcRate
r  = 200·rc·x'
superRate > 0:  r = r / clamp(1 − |x'|·superRate, 0.01, 1)
```
**ACTUAL** (`centerDps`, `maxDps`, `expo`):
```
e = |x|·(x⁵·expo + x·(1 − expo))
r = x·centerDps + max(0, maxDps − centerDps)·e
```
Ergebnis begrenzt auf ±1998 °/s. Referenzwerte für den Test: BF (1.0/0.7/0) → x=1: 666.7 °/s, x=0.5: 153.8 °/s;
ACTUAL (70/670/0) → x=1: 670 °/s, x=0.5: 185 °/s. `RateCurves.inverse(...)` (Bisektion, 30 Schritte) steht dem
Autopiloten zur Verfügung.

### 5.4 ANGLE, HORIZON, Autopilot-Sollwerte
**ANGLE** (auf Quaternion-Basis, robust auch bei großen Winkeln und in Rückenlage):
```
Soll: φ_sp = roll·angleLimit, θ_sp = pitch·angleLimit  (linear, ohne Expo; der Autopilot kann das exakt invertieren)
q_des = q_heading(ψ_ist) ⊗ q_pitch(θ_sp) ⊗ q_roll(φ_sp)
q_e   = q* ⊗ q_des ;  e_B = 2·sgn(q_e.w)·(q_e.x, q_e.y, q_e.z)  [rad]  → FC-Achsen (roll = e_z, pitch = e_x)
r_sp,roll/pitch = k_ang·e[°] + angleFF·dφ_sp/dt,  k_ang = angleStrength·0.2 [1/s]
Yaw: yawStick → Rate um die Welt-Hochachse (angleEarthRef = 100 %) → in Körperraten umrechnen und addieren
r_sp begrenzen auf ±angleRateLimit (Standard 800 °/s)
```
Weil der Kurs aus der aktuellen Lage stammt, kämpft ANGLE nicht gegen Yaw. Aus Rückenlage wird auf kürzestem Weg
aufgerichtet.

**HORIZON:** `λ = levelStrength/100 · max(0, 1 − max|stick|/limitSticks) · max(0, 1 − Neigung/limitDeg)`,
`r_sp = (1 − λ)·r_acro + λ·r_angle`. Bei mittigen Sticks verhält er sich wie ANGLE, bei vollem Ausschlag wie ACRO
(Flips möglich).

**Autopilot-Schnittstelle:** Ist `SimInput.attitudeCommand = true`, werden `cmdRollDeg`, `cmdPitchDeg`,
`cmdYawRateDps` (Welt-Hochachse) und `cmdThrottle` (0..1, vor der Gaskurve) direkt als ANGLE-Sollwerte genommen.
Sie laufen durch dieselbe PT2-Glättung (Cutoff `filters.autopilotSmoothingHz`, Standard 15 Hz) und dasselbe
Winkel-FF. Die Telemetrie liefert `attitudeErrorDeg` und `hoverThrottleEstimate` (gleitender Mittelwert des
Gashebels im stationären Flug) für den Höhenregler des Autopiloten. Unterschied zu Sticks: kein Deadband, keine
Winkelbegrenzung über `angleLimit` hinaus (bis 80°).

### 5.5 PID
Ausgang in **Duty-Einheiten** (Anteil der Motor-Ausgangsspanne) pro Achse, dann mit Plant-Normierung multipliziert:
```
e = r_sp − gyro_f                                             [°/s]
P = Kp·3.2e-5·e
I += Ki·2.44e-4·e·h·relax·AG      (Anti-Windup: eingefroren bei Mixer-Sättigung in Fehlerrichtung, |I| ≤ itermLimit)
D = −Kd·5.3e-7·PT(d gyro_f/dt)    [°/s²]    (D auf dem Messwert, kein Sollwertsprung)
F = (Kf/100)·(1/α_axis)·d r_sp,f/dt   [rad/s² → Duty]   (Kf = 100 heißt: ideales inverses Plant-Feedforward)
u = k_auth·TPA·(P + D) + k_auth·I + F,   k_auth = clamp(α_ref/α_axis, 0.25, 4)
```
- `α_axis` = Winkelbeschleunigung pro Einheit Duty-Differenz im Schwebeflug (Tabelle 3.7), `α_ref` =
  (2533, 2419, 177) rad/s² (FREESTYLE-Referenz, fest in `SimTuning`, damit spätere Preset-Änderungen den Tune
  nicht verschieben). `pid.authorityNormalization = false` → `k_auth = 1` (die „rohe" Physik).
- **iterm relax** (Modus OFF/RP/RPY): `relax = max(0, 1 − |HP_15Hz(r_sp)|/itermRelaxThresholdDps)`. Das ist im
  Prototyp der entscheidende Baustein: Ohne relax gab es 11–25 % Überschwingen, mit relax 1–2 %.
- **Anti-Gravity:** `AG = 1 + (antiGravityGain/20)·clamp(|HP_5Hz(throttle)|·8, 0, 1)` (Standard 80 → bis 5×).
- **TPA:** `TPA = 1 − (tpaRate/100)·clamp((thr − tpaBreakpoint)/(1 − tpaBreakpoint), 0, 1)` auf P und D. Das passt
  zur Physik: Die Autorität steigt mit ω, bei Vollgas etwa 3× so hoch wie beim Schweben. Standard 65 % ab 0.35.
- I-Term wird zurückgesetzt, solange disarmed ist oder (ohne aktiven Airmode) `throttle < 0.05`.

### 5.6 Mixer
```
Gaskurve: x = t − mid; y = x > 0 ? 1 − mid : mid;  t' = mid + x·(1 − expo + expo·x²/y²)
Throttle Limit: SCALE → t'·limit, CLIP → min(t', limit)
a_i  = roll_i·u_r + pitch_i·u_p + yaw_i·u_y
span = max a − min a;  span > 1 → a_i /= span  (Verhältnis bleibt erhalten, Sättigungsflag für den I-Anti-Windup)
Airmode aktiv (nach erstem Gas > airmodeStartThrottle seit dem Armen): t'' = clamp(t', −min a, 1 − max a)
sonst: t'' = t'
m_i = clamp(t'' + a_i, 0, 1)
d_i = (idle + (1 − idle)·m_i)·motorOutputLimit · SagComp,   SagComp = min(1/d_max, 1 + c·(n·4.2/V_bus,f − 1))
disarmed: d_i = 0 (Motoren laufen mit aktivem Bremsen aus)
```

### 5.7 Armen, Disarmen, Sperrgründe
Armen erfolgt auf die **steigende Flanke** von `armSwitch`, wenn `armBlockers == 0`. Sonst gibt es das Event
`ARM_BLOCKED` mit der Bitmaske. `telemetry.armBlockers` wird in jedem Frame aktualisiert (fürs OSD
„CANNOT ARM: THROTTLE").

| Bit | `ArmBlocker` | Bedingung |
|---|---|---|
| 0 | `ARM_SWITCH_NOT_RESET` | Schalter war beim Start/nach Disarm an und wurde noch nicht umgelegt |
| 1 | `THROTTLE_NOT_LOW` | Gas > 0.05 (Stick bzw. cmdThrottle) |
| 2 | `TILTED` | Neigung > `smallAngleDeg` (25°) |
| 3 | `PACK_DEPLETED` | Ruhespannung pro Zelle < cutoff + 0.05 V |
| 4 | `BUILD_INVALID` | `!build.buildable()` |
| 5 | `CRASH_COOLDOWN` | < 1 s seit Crash-Disarm |
| 6 | `FAILSAFE` | `env.failsafe` (Funkverbindung weg) |
| 7 | `BROWNOUT_REBOOT` | < 0.5 s seit Brownout |
| 8 | `IN_FLUID` | im Wasser/Lava |

`DisarmReason`: `SWITCH, CRASH, BROWNOUT, FAILSAFE, SIM_FAULT, FLUID, FORCED`.
Failsafe: 0.3 s die letzten Sticks halten, dann ANGLE eben mit `0.9·hoverThrottleEstimate`, nach 1.5 s Disarm.
Der spätere Return-to-Home-Autopilot ersetzt Stufe 2.

### 5.8 Filter
Gyro-PT1 250 Hz, D-Term PT1 100 Hz (optional PT2), RC-Smoothing automatisch. Gyro-Rauschen ist standardmäßig
aus (`gyroNoiseDps = 0`); Unruhe kommt aus der Turbulenz (4.6). Motorvibrationen, RPM-Filter und Dynamic Idle
werden nicht modelliert.

### 5.9 Default-Tune und Prototyp-Nachweis
Default (alle Builds, dank k_auth): Roll P45 I80 D40 F100, Pitch P47 I84 D46 F105, Yaw P45 I80 D0 F100,
iterm relax RP 15 Hz/40 °/s, angleStrength 60, angleFF 50 %.
Einachsiger nichtlinearer Prototyp (Motor-DC-Modell mit J_eff, 1 kHz, Gyro-PT1 250 Hz, D-PT1 100 Hz,
RC-Smoothing aus der FPS, Sprung 0 → 500 °/s bzw. 0 → 20°):

| Build | Rate: Anstieg 10–90 % (60/144 fps) | Überschwingen | Einschwingen ±5 % | Nachlauf Gyro↔Soll | ANGLE 20°: t90 / Überschw. / ±5 % |
|---|---|---|---|---|---|
| WHOOP | 33 / 29 ms | 1.6 / 1.9 % | 54 / 49 ms | 15 / 16 ms | 133 ms / 0 % / 193 ms |
| CINEWHOOP | 35 / 33 ms | 0.7 / 1.3 % | 57 / 55 ms | 15 / 21 ms | 139 ms / 0 % / 211 ms |
| FREESTYLE | 44 / 38 ms | 1.4 / 1.8 % | 68 / 63 ms | 11 / 14 ms | 99 ms / 0 % / 149 ms |
| RACER | 43 / 37 ms | 1.3 / 1.7 % | 67 / 62 ms | 11 / 15 ms | 102 ms / 0 % / 158 ms |
| LONG_RANGE | 35 / 34 ms | 0.8 / 1.5 % | 58 / 57 ms | 15 / 22 ms | 143 ms / 0 % / 217 ms |

Per-Build-Defaults über `FcConfig.defaultsFor(build)`: Idle WHOOP 7 %, CINEWHOOP 6 %, sonst 5.5 %;
ACTUAL maxDps WHOOP 800, CINEWHOOP 500, FREESTYLE 670, RACER 720, LONG_RANGE 500; angleLimit CINEWHOOP und
LONG_RANGE 45°, sonst 55°.

---

## 6. Integrationsschritt

```
step(frameDt, in, env, out):
  1  Eingänge prüfen: dt nicht endlich oder < 0 → 0; dt > 0.050 → 0.050 + Event FRAME_HITCH;
     Sticks NaN → 0 und begrenzen; env prüfen (NaN-Wind → 0, Bodenabstand NaN → +∞, ρ ∈ [0.3, 1.5])
  2  accNs += round(dt·1e9);  n = min(accNs / hNs, MAX_SUBSTEPS = 60);  accNs −= n·hNs
     (bei Kappung: Rest verwerfen, Sim läuft kurz langsamer als Echtzeit; bleibt deterministisch)
  3  Frame-Snapshot für den NaN-Rollback (~80 doubles, arraycopy in ein vorab alloziertes Array)
  4  Stick-Ziele übernehmen (prev ← cur, cur ← in); FPS-Schätzer; Arm-Logik (Flanken, Sperrgründe)
  5  for k in 0 … n−1:
       a  FC.update(h, interpolierte Sticks, gyro, q, V_bus, thr) → d[4]
       b  Rotor-Luftgrößen (u_i, v_ax, v_ed) aus dem aktuellen Zustand; f_ax, f_Q, f_tl, f_ge
       c  Akku/ESC algebraisch lösen → V_bus, I_i; MotorBank implizit → ω_i; Akku integrieren
       d  VRS (jeder Substep), Wake/Böen/Temperaturen (jeder 4. Substep, fester Zähler), Turbulenz n_i
       e  Kräfte/Momente (4.3–4.9), Bodenkontakt-Aufrichtung (7.6, nur wenn grounded)
       f  RigidBody.integrate; Grenzen: |v| ≤ 150 m/s, |ω| ≤ 70 rad/s, ω_i-Grenzen
       g  Wächter: isFinite(Summe von v, L, q, ω_i, V_bus); schlägt er fehl → Rollback, SIM_FAULT, Disarm, break
       h  Telemetrie-Spitzenwerte (max g, max Strom) aktualisieren
  6  Rest-Extrapolation für die Darstellung: τ = accNs·1e-9 (< 1 ms)
       p_vis = p + v·τ ;  q_vis = exp(½τ·ω_W) ⊗ q
  7  out.dx,dy,dz = p_vis − lastEntityPos ;  out.q = q_vis ;  out.wakeRayDir = −ŷ_W ;  Telemetrie füllen

afterMove(report):   (jeden Frame aufrufen, auch ohne Kollision)
  1  Korrektur = erreicht − gewünscht;  p += Korrektur;  lastEntityPos = report.entityPos
  2  Treffer-Achsen → CollisionSolver (Abschnitt 7), Events, ggf. Disarm
  3  grounded-Buchhaltung (LANDED/TAKEOFF, Schlafzustand), Wake-Feld bei Teleport verschieben
```
Ein Nanosekunden-Overload `step(long frameNanos, …)` macht Tests exakt.
Das Wake-Feld, die Böen und das Turbulenzrauschen laufen auf der Substep-Uhr, nie auf der Frame-Uhr. Nur so ist
der Zustand bei konstanten Eingaben unabhängig davon, wie die Zeit auf Frames verteilt wird (Test K2).

**Spielseite (Hinweise für die Integration):** `step` pro gerendertem Frame auf dem Client-Hauptthread (nicht im
20-Hz-Tick), danach `entity.move(MoverType.SELF, …)` und dann `afterMove`. Nach dem Move `xo/yo/zo = x/y/z`
setzen, damit Minecraft nicht zusätzlich interpoliert. Bei Pause wird `step` nicht aufgerufen.
Nach einem Teleport oder einer Serverkorrektur `sim.teleport(x, y, z, keepVelocity)` aufrufen: Das Wake-Feld wird
mitverschoben, damit kein Schein-Propwash entsteht.

**CPU-Budget:** Pro Substep etwa 1 200 Flops, ~10 sqrt und 3 StrictMath-Aufrufe, also ≈ 1–2 µs nach dem JIT.
Die Zahl der Substeps pro Sekunde hängt nicht von der FPS ab:

| FPS | Substeps/Frame | Kosten/Frame (≈1.5 µs/Substep) | Anteil an 1 Kern |
|---|---|---|---|
| 60 | 16.7 | 25 µs | 0.15 % |
| 144 | 6.9 | 10 µs | 0.15 % |
| 240 | 4.2 | 6 µs | 0.15 % |
| 20 (Ruckler) | 50 | 75 µs | — |

---

## 7. Kollisionen

### 7.1 MoveReport (vom Spiel befüllt, vorab alloziert)
`requestedX/Y/Z` (= out.dx…), `actualX/Y/Z` (Rückgabe von `move`), `collidedX/Y/Z`
(`|actual − requested| > 1e-7` pro Achse), `entityX/Y/Z` (Position nach dem Move), `surfaceFriction` (Standard
0.6; Eis 0.1), `surfaceRestitution` (−1 = Rahmenwert, Slime z. B. 0.8), optional `entityContact` mit Normale und
Geschwindigkeit des anderen Objekts (Spieler oder Mob).
Die Normale einer Treffer-Achse ist `n = −sign(requested_k)·ê_k`.

### 7.2 Impulsverfahren pro Treffer-Achse
Reihenfolge nach absteigendem `|v·n|` (meist Y zuerst):
```
Hüllpunkt:  r = R·r_hull[j] mit minimalem (R·r_hull[j])·n;  alle Punkte innerhalb von 3 mm davon werden gemittelt
            (flache Landung → Kontakt in der Mitte → kein Scheindrehmoment)
v_c = v + ω_W × r ;  v_n = v_c·n ;  v_n ≥ 0 → nichts zu tun (trennt sich)
e   = |v_n| < 0.4 m/s ? 0 : e₀·restitutionScale·(1 − 0.6·S(0.3·v_crash, v_crash, |v_n|))
K_n = 1/m + n·((I_W⁻¹(r × n)) × r)
j_n = −(1 + e)·v_n/K_n ;  v += j_n·n/m ;  L += r × j_n·n
Reibung: v_t = v_c' − (v_c'·n)·n ;  t̂ = v_t/|v_t| ;  K_t analog ;  j_t = min(μ·j_n, |v_t|/K_t) ;  v −= j_t·t̂/m ; L −= r × j_t·t̂
```
Taumeln entsteht von selbst über `r × j`: Wer mit einem Arm an einer Kante hängen bleibt, dreht sich.

### 7.3 Crash-Schwelle
`v_imp = |v·n|` (CoM-Normalgeschwindigkeit vor dem Impuls, für Spieler nachvollziehbar).
`v_imp ≥ frame.impactTolerance·crashSpeedScale` → Event `CRASH(v_imp)`, und bei `fc.crashDisarm` Disarm `CRASH`.
`≥ 0.5·v_crash` → `HARD_IMPACT`; `≥ 1 m/s` → `BUMP`. Eine Schwelle pro Rahmen (WHOOP 14, CINEWHOOP 11,
FREESTYLE 8, RACER 7, LONG_RANGE 8 m/s); `crashSpeedScale` ist der Schwierigkeitsregler (Einsteiger 1.5).

### 7.4 Prop-Strike
Ist der Kontaktpunkt eine Propspitze, dreht der Motor mit > 30 % ω_max und liegt die Normale nicht entlang −ŷ_B
(also kein Aufsetzen auf der Unterseite): `ω_i *= 0.2`, zusätzlicher Yaw-Impuls `ΔL_y = −σ_i·J·ω_i·0.8` und
Event `PROP_STRIKE(i)` (für Sound und Funken). Props nehmen dabei keinen Schaden (siehe Abschnitt 14).

### 7.5 Ruhen, Schlafen, kein Zittern
`grounded` = in den letzten 2 Frames eine Y-Kollision mit `n_y > 0.7`. Disarmed, `|v| < 0.05` und `|ω| < 0.1`
für 0.5 s → **Schlafzustand**: v und L werden genullt und die Integration pausiert (Motoren und Akku laufen weiter).
Aufgeweckt wird durch Armen, einen Impuls oder den Verlust von `grounded`.

### 7.6 Aufrichten am Boden
Ein Punktimpuls pro Frame kann eine schräg liegende Drohne nicht sauber ablegen. Solange `grounded`,
`|v| < 0.5`, `|ω| < 4` und der Schub < 0.5·mg ist, wirkt in den Substeps ein Feder-Dämpfer-Moment zur nächsten
stabilen Lage (aufrecht oder Rückenlage, je nach Vorzeichen von `ŷ_B·ŷ_W`):
`τ_W = k_r·(±ŷ_B × ŷ_W)_horizontal − c_r·ω_W,horizontal`, `k_r = m·g·s`, `c_r = 2√(k_r·I_xx)`, dazu Yaw-Reibung
`−μ·m·g·s·sgn(ω_y)` (geglättet).

### 7.7 Turtle-Modus (optional, `fc.turtleEnabled`)
Wird disarmed in Rückenlage am Boden `turtleSwitch` + Arm betätigt, laufen die Motoren rückwärts
(`ω < 0`, Schub `−0.5·k_T·ω²`, max. 60 % Ausgang). Die Stickrichtung wählt das Motorpaar, das die gewünschte Seite
anhebt. Hat sich die Drohne aufgerichtet (`ŷ_B·ŷ_W > 0.5`), wird automatisch disarmed.

### 7.8 Fluide
`env.fluid = WATER`: Widerstand ×400, Auftrieb 0, Motoren gestoppt nach 0.3 s, Disarm `FLUID`, Event
`WATER_DAMAGE`. Lava: sofort Disarm plus Event.

---

## 8. Robustheit

- **Determinismus:** `StrictMath.sin/cos/exp/atan2/pow`; `Math.sqrt` ist IEEE-exakt und erlaubt. Kein `Math.fma`
  (Java führt ohnehin keine FMA-Kontraktion durch). Von JOML nur `Vector3d`/`Quaterniond`/`Matrix3d` mit
  add/sub/mul/dot/cross/length/normalize/conjugate/transform. **Nicht** `rotateAxis`, `rotationXYZ`,
  `integrate`, `slerp`, denn die gehen über `org.joml.Math` und nutzen bei gesetztem `joml.fastmath`
  Lookup-Tabellen. Zufall nur aus `SimRandom` (xorshift64*, Seed im Konstruktor und im Snapshot).
- **NaN-/Inf-Wächter:** eingangsseitig (Abschnitt 6, Schritt 1) und einmal pro Substep eine Summenprüfung. Im Fehlerfall
  Rollback auf den Frame-Anfang, v = L = 0, Disarm `SIM_FAULT`, Event. Mehr als 3 Fehler pro Sekunde →
  harter Reset: aufrecht, in Ruhe, an der aktuellen Entity-Position.
- **Klemmen:** |v| ≤ 150 m/s, |ω| ≤ 70 rad/s, ω_i ∈ [0, 1.15·K_v·V_voll], SoC ∈ [−0.2, 1], Temperaturen ∈ [−40, 250] °C,
  `f_ge ≤ 1.4`, `f_ax ≥ 0`, `k_auth ∈ [0.25, 4]`. `FcConfig.sanitize()` begrenzt alle Konfigurationsfelder
  (JSON kann Unsinn enthalten).
- **Keine Allokation:** keine Lambdas, kein Boxing, keine Iteratoren, keine Strings in `step`/`afterMove`.
  Events als Ringpuffer mit primitiven Feldern.
- **Parameterwechsel** (Werkbank) nur disarmed: `setParams` gibt sonst `false` zurück. Die Konfiguration darf
  jederzeit gewechselt werden (sie wird per `copyFrom` kopiert, dann `sanitize`).

---

## 9. Öffentliche API (Entwurf, Signaturen)

```java
package de.lardtom.propwash.sim;

public final class DroneSim {
    public DroneSim(AirframeSpec params, FcConfig config, SimTuning tuning, long seed);
    public void reset(double x, double y, double z, double headingRad);   // disarmed, in Ruhe, Akku voll
    public void resetBattery(double chargeFraction);
    public void teleport(double x, double y, double z, boolean keepVelocity);
    public boolean setParams(AirframeSpec params);                         // nur disarmed
    public void setConfig(FcConfig config);                               // copyFrom + sanitize
    public void step(double frameDtSeconds, SimInput in, SimEnvironment env, StepResult out);
    public void step(long frameNanos, SimInput in, SimEnvironment env, StepResult out);
    public void afterMove(MoveReport report);
    public void forceDisarm(DisarmReason reason);
    public Telemetry telemetry();        // lebendes Objekt, gilt bis zum nächsten step
    public EventQueue events();          // pro Frame leeren
    public void copyStateTo(SimSnapshot dst);
    public void copyStateFrom(SimSnapshot src);
    public void getVisibleOrientation(Quaterniond dst);
    public void getBodyAxes(Vector3d left, Vector3d up, Vector3d forward);
}

public final class AirframeSpec   { public static AirframeSpec derive(DroneBuild b, SimTuning t); /* public final Felder aus 3.x */ }
public record BuildAnalysis(...) { public static BuildAnalysis of(DroneBuild b, SimTuning t); }   // Abschnitt 12
public final class SimInput      { public double roll, pitch, yaw, throttle; public StabMode mode;
                                   public boolean armSwitch, turtleSwitch, attitudeCommand;
                                   public double cmdRollDeg, cmdPitchDeg, cmdYawRateDps, cmdThrottle; }
public final class SimEnvironment{ public double airDensity = 1.225, windX, windY, windZ, gustIntensity, windShelter,
                                   heightAboveGround = Double.POSITIVE_INFINITY, wakeSurfaceDistance = Double.POSITIVE_INFINITY,
                                   ambientTempC = 20; public Fluid fluid = Fluid.NONE; public boolean failsafe; }
public final class StepResult    { public double dx, dy, dz; public final Quaterniond orientation = new Quaterniond();
                                   public double wakeRayDirX, wakeRayDirY, wakeRayDirZ; public int substeps; public boolean armed, sleeping; }
public final class MoveReport    { /* siehe 7.1 */ }
public enum StabMode { ACRO, ANGLE, HORIZON }
```
Interne Klassen (package-private): `RigidBody`, `MotorBank` (SoA `double[4]`), `Battery`, `RotorAero`, `BodyAero`,
`WakeField`, `Turbulence`, `Wind`, `FlightController`, `RateCurves`, `PidAxis`, `Mixer`, `CollisionSolver`,
`HullShape`, `Pt1`, `Pt2`, `Biquad`, `SimRandom`, `SimMath` (Exp-Map, smoothstep, Achsen-Umrechnung).
Konfiguration: `FcConfig`, `RateProfile`, `RateCurve`, `PidProfile`, `PidGains`, `FilterSettings`, `SimTuning`.

---

## 10. Telemetrie und Events

`Telemetry` (nur primitive Felder und `double[4]`, pro Frame befüllt):

| Gruppe | Felder |
|---|---|
| Zustand | `time`, `substeps`, `armed`, `mode`, `armBlockers` (Bitmaske), `lastDisarmReason`, `grounded`, `sleeping`, `faults` |
| Motoren | `motorRpm[4]`, `motorOutput[4]` (Mixer 0..1), `motorDuty[4]`, `motorCurrent[4]`, `motorTempC[4]`, `escTempC`, `mixerSaturated` |
| Akku | `batteryCurrent` [A], `packVoltage`, `cellVoltage` (unter Last), `restingCellVoltage`, `mAhUsed`, `chargeFraction` (SoC), `powerW`, `batteryWarning` (0/LOW/CRITICAL) |
| Aero | `propwash` (0..1, geglättet), `vrs`, `wake`, `groundEffectFactor`, `inGroundEffect`, `translationalLift`, `totalThrustN`, `thrustToWeightNow`, `windAtDroneX/Y/Z` |
| Flug | `gLoad`, `gLoadMax` (seit dem Armen), `speed`, `airspeed`, `verticalSpeed`, `heightAboveGround`, `headingDeg`, `rollDeg`, `pitchDeg`, `gyroDps[3]`, `setpointDps[3]`, `attitudeErrorDeg`, `hoverThrottleEstimate` |
| Regler (Blackbox/Configurator) | `pTerm[3]`, `iTerm[3]`, `dTerm[3]`, `fTerm[3]`, `throttleOut` |
| Crash | `crashCount`, `lastImpactSpeed`, `lastImpactNormalX/Y/Z` |

Temperaturmodell: Motor `C = 0.5 J/(g·K)·m_motor`, Verluste `I²·R_motor + 0.5·I_0·K_e·ω`, Kühlung
`G = (0.15 + 3e-4·ω)·(m_motor/33 g)^(2/3)` W/K (FREESTYLE bei Vollgas: ≈ +46 K stationär, τ ≈ 14 s). ESC analog
mit `I²·R_esc`. Standardmäßig ohne Rückwirkung; mit `thermalEffects` steigt R_m um 0.39 %/K,
Event `MOTOR_OVERHEAT` ab 150 °C.

`EventQueue`: Ringpuffer mit 32 Plätzen (`type` ordinal, `time`, `value`, `motor`), mit Überlaufzähler.
Events: `ARMED, DISARMED, ARM_BLOCKED(bits), CRASH(v), HARD_IMPACT(v), BUMP(v), PROP_STRIKE(i), LANDED, TAKEOFF,
BATTERY_LOW, BATTERY_CRITICAL, BROWNOUT, ESC_OVERCURRENT(i), MOTOR_OVERHEAT(i), FRAME_HITCH(dt), SIM_FAULT,
TURTLE_START, WATER_DAMAGE`. Akku-Warnungen mit Hysterese: LOW bei einer Zelle unter Last < 3.5 V (LION 3.2)
für 2 s, CRITICAL < 3.3 V (LION 3.0).

---

## 11. Konfigurationsobjekte (öffentliche, veränderliche Felder, JSON-fähig)

Alle Klassen haben einen Konstruktor ohne Argumente mit Defaults, `copyFrom(other)` und `sanitize()`.
Einheiten wie im Betaflight-Configurator (°/s, %, Hz, Grad).

**`FcConfig`**: `RateProfile rates`, `PidProfile pid`, `FilterSettings filters`, `boolean propsOut = false`,
`double smallAngleDeg = 25`, `boolean crashDisarm = true`, `double motorOutputLimitPercent = 100`,
`boolean turtleEnabled = true`, `static FcConfig defaultsFor(DroneBuild)`.

**`RateProfile`**: `RatesType type = ACTUAL` (`BETAFLIGHT`, `ACTUAL`), `RateCurve roll, pitch, yaw`,
`double rcDeadband = 0.0`, `double yawDeadband = 0.0`.
**`RateCurve`**: BETAFLIGHT `rcRate = 1.0` [0.01–2.55], `superRate = 0.70` [0–0.99], `expo = 0.0` [0–1];
ACTUAL `centerDps = 70` [10–1000], `maxDps = 670` [10–1998], `actualExpo = 0.0` [0–1].

**`PidProfile`**:
| Feld | Default | Bereich |
|---|---|---|
| `roll/pitch/yaw` (`PidGains p, i, d, ff`) | 45/80/40/100 · 47/84/46/105 · 45/80/0/100 | 0–250 |
| `angleStrength` | 60 | 0–200 |
| `angleLimitDeg` | 55 | 10–80 |
| `angleFeedforwardPercent` | 50 | 0–200 |
| `angleEarthRefPercent` | 100 | 0–100 |
| `angleRateLimitDps` | 800 | 100–1998 |
| `horizonLevelStrength` / `horizonLimitSticks` / `horizonLimitDeg` | 75 / 0.75 / 135 | 0–200 / 0.1–1 / 10–180 |
| `itermRelax` (OFF/RP/RPY) / `itermRelaxCutoffHz` / `itermRelaxThresholdDps` | RP / 15 / 40 | – / 1–50 / 5–200 |
| `itermLimit` (Duty) | 0.30 | 0.05–1 |
| `antiGravityGain` | 80 | 0–250 |
| `tpaRatePercent` / `tpaBreakpoint` | 65 / 0.35 | 0–100 / 0–1 |
| `airmode` / `airmodeStartThrottle` | true / 0.25 | – / 0–1 |
| `motorIdlePercent` | 5.5 | 0–20 |
| `throttleMid` / `throttleExpo` | 0.5 / 0.0 | 0–1 / 0–1 |
| `throttleLimitType` (OFF/SCALE/CLIP) / `throttleLimitPercent` | OFF / 100 | – / 25–100 |
| `authorityNormalization` | true | – |
| `vbatSagCompensationPercent` | 0 | 0–100 |

**`FilterSettings`**: `gyroLpfHz = 250` (0 = aus), `dtermLpfHz = 100`, `dtermLpfType = PT1` (PT1/PT2),
`rcSmoothingAuto = true`, `rcSmoothingHz = 30`, `feedforwardSmoothingHz = 30`, `autopilotSmoothingHz = 15`,
`gyroNoiseDps = 0`.

**`SimTuning`** (Spielgefühl und Realismus, nicht Teil des FC-Profils; Schwierigkeits-Presets
„Einsteiger/Realistisch" setzen diese Werte): `substepHz = 1000` (500/1000/2000), `motorTauMinMs = 6`,
`motorTauMaxMs = 40`, `propwashScale = 1`, `turbulenceScale = 1`, `groundEffectScale = 1`,
`translationalLiftScale = 1`, `dragScale = 1`, `windScale = 1`, `crashSpeedScale = 1`, `restitutionScale = 1`,
`wakeFieldEnabled = true`, `thermalEffects = false`, dazu die Ableitungskonstanten aus Abschnitt 3
(`ductThrustGain = 1.20`, `propInertiaFactor = 0.25`, `idleCurrentFraction = 0.035`, `hForceKappa = 0.7`,
`alphaRef = {2533, 2419, 177}` …).

---

## 12. Anbindung an die Werkbank (Zusammenbau)

Die Drohne wird an einer Werkbank aus Teilen zusammengebaut. Die Sim liefert dafür `BuildAnalysis.of(build)`, die
Kennwerte für das Werkbank-UI mit denselben Modellen wie der Flug (stationäre Löser aus 4.1–4.3, etwa 1–3 ms,
gecacht pro `DroneBuild`-Record):
`massGrams, thrustToWeight, hoverThrottlePercent, hoverCurrentA, hoverMinutes, fullThrottleCurrentA,
fullThrottleSeconds, topSpeedMs, climbRateMs, motorResponseMs, escLoadPercent, batteryLoadPercent,
sagCellVoltageAtFull, crashSpeedMs, groundEffectHeightCm`.
Beim Überfahren eines Teils im Werkbank-UI kann man damit Differenzen anzeigen („+1.8 T/W, −40 s Flugzeit").
Die Werte in Tabelle 3.7 sind genau diese Ausgabe für die 5 Presets. Gebaut (Parameter neu abgeleitet) wird nur
disarmed; ein nicht baubarer Build (`problems()` nicht leer) liefert Parameter, lässt sich aber nicht armen
(`BUILD_INVALID`).

---

## 13. Testplan (JUnit 5, `common/src/test/java/de/lardtom/propwash/sim/`)

Alle Tests laufen ohne Minecraft. Eine Testwelt `FlatWorldMover` (Ebene y = 0 plus optionale Wand) imitiert
`entity.move` achsweise und befüllt `MoveReport`.

**A – Ableitung (`DerivationTest`)**
- A1 Masse aller Presets exakt (±0.01 g); I_xx/I_yy/I_zz, k_T, k_Q, K_t, J ±0.5 % gegenüber Tabelle 3.7.
- A2 T/W, Schwebe-Duty, Vollgasstrom ±2 %; Höchstgeschwindigkeit und Steigrate ±8 %; Schwebezeit ±5 %.
- A3 WHOOP: τ_eff = 40 ms ±1 ms; alle anderen Builds unverändert (J_eff = J).

**B – Motor/ESC (`MotorBankTest`)**
- B1 Stationäre Drehzahl bei d = 1 stimmt mit dem geschlossenen Löser überein (±0.5 %).
- B2 Sprung Duty Schweben → 1: 63-%-Zeit = τ_eff aus der Tabelle ±15 %; Auslauf 1 → Idle mindestens 20 % schneller
  (aktives Bremsen).
- B3 Strombegrenzung greift (I ≤ I_lim zu jeder Zeit); ESC_OVERCURRENT-Event höchstens 1× pro 0.5 s.
- B4 Stabilität: auch mit h = 5 ms keine Oszillation (monoton steigende Drehzahl beim Sprung).

**C – Akku (`BatteryTest`)**
- C1 Konstant 20 A für 30 s → mAh = 166.67 ±0.1 %.
- C2 Stromsprung 0 → 100 A (FREESTYLE): sofortiger Einbruch = 100 A·R_int ±2 %, danach Polarisation mit
  τ = 4 s ±10 %, Erholung nach dem Sprung ebenso.
- C3 Algebraische Schleife: FREESTYLE-Vollgassprung → V_bus ohne Oszillation (Vorzeichenwechsel der Ableitung ≤ 1,
  Überschwingen < 0.05 V).
- C4 Brownout: WHOOP bei SoC 0.02 und Vollgas → BROWNOUT innerhalb 1 s; 6S-Builds bei SoC 0 und Vollgas → nie.

**D – Aerodynamik (`RotorAeroTest`, `BodyAeroTest`)**
- D1 `f_ax(v_ax = v_p) = 0`, `f_ax(0) = 1`, Sinken max. 1.3.
- D2 `f_ge`: Tabellenwerte ±0.001, `f_ge(∞) = 1`, ≤ 1.4.
- D3 VRS: Sinken 1.0·v_h mit Schwebeschub → `w_vrs ≥ 0.95`; Sinken 0.1·v_h → ≤ 0.01; 1.0·v_h sinkend + 1.5·v_h seitlich → ≤ 0.12.
- D4 Rumpfwiderstand: 10 000 zufällige Lagen/Geschwindigkeiten → `F·u ≤ 0`; unscharfer flacher Fall →
  Endgeschwindigkeit ±3 % der Tabelle.
- D5 Translational Lift monoton in μ, Grenzwert 1 + k_tl.

**E – Wake-Feld (`WakeFieldTest`)**
- E1 FREESTYLE: 1.5 s Vollgas senkrecht, Flip 180°, mit Gas 0.2 zurückstürzen → `wake`-Spitze ≥ 0.5 innerhalb von 2 s.
- E2 Horizontalflug mit 10 m/s über 10 s → `wake` ≤ 0.1.
- E3 Nach 5 s Idle am Boden sind alle Pakete verfallen; Ringpuffer-Überlauf ohne Allokation.

**F – Starrkörper (`RigidBodyTest`)**
- F1 Freier Fall 1 s → Δy = −4.905 m ±0.2 %.
- F2 Momentfreie Drehung ω₀ = (10, 20, 5) rad/s mit anisotropem I über 10 s: |L| exakt erhalten (rel. 1e-12),
  Rotationsenergie-Drift < 0.5 %, `| |q| − 1 | < 1e-12`.
- F3 Konstantes Moment → Winkelbeschleunigung τ/I ±0.1 %.

**G – Regler (`RateCurvesTest`, `MixerTest`, `ArmingTest`, `PidTest`)**
- G1 Rates-Referenzwerte aus 5.3 (±0.01 °/s), Inverse: `rate(inverse(r)) = r` ±0.1 °/s.
- G2 Mixer: reines Roll-Signal → M3/M4 hoch, M1/M2 runter, Summe konstant; span > 1 → Verhältnisse erhalten;
  Airmode bei Gas 0 hält die Differenz; ohne Airmode wird bei Gas 0 begrenzt.
- G3 Armen: jeder Sperrgrund einzeln gesetzt → genau dieses Bit, kein Armen, Event ARM_BLOCKED; Flankenlogik.
- G4 iterm relax: Rampe des Sollwerts → I-Term wächst um < 10 % des Werts ohne relax.

**H – Closed Loop (`FlightLoopTest`, volle `DroneSim`, alle 5 Presets, 60 und 144 fps)**
- H1 Rate-Sprung 500 °/s: Anstieg 10–90 % ≤ 60 ms, Überschwingen ≤ 8 %, Einschwingen ±5 % ≤ 120 ms,
  Nachlauf bei 50 % ≤ 30 ms, Nebenachsen-Übersprechen ≤ 30 °/s.
- H2 ANGLE-Sprung 20°: t90 ≤ 180 ms, Überschwingen ≤ 5 %, Einschwingen ±5 % ≤ 300 ms, Restfehler ≤ 0.3° nach 1 s.
- H3 Autopilot-Sollwert: Sinus 20° / 0.5 Hz auf Roll und Pitch → RMS-Fehler ≤ 2°; Yaw-Rate 90 °/s um die
  Welt-Hochachse bei 30° Neigung → Kursrate ±5 %.
- H4 Schweben in ANGLE, Seitenwind 5 m/s als Sprung → Lagefehler ≤ 2° nach 0.5 s (die Drohne driftet mit, die Lage hält).
- H5 Propwash-Szenario FREESTYLE (ACRO): auf 30 m steigen, 1.5 s Gas 0, dann Schwebegas → `propwash`-Spitze ≥ 0.6,
  Lagestörung RMS 1–10° (sichtbar, aber beherrschbar), Neigung nie > 30°, kein Crash.
- H6 „Alle Presets fliegbar": Start in ANGLE, 20 m vorwärts, Flip in ACRO, Landung mit 1 m/s → kein Crash, kein Fault,
  Endlage aufrecht.

**I – Kollisionen (`CollisionTest`, mit `FlatWorldMover`)**
- I1 Unscharf flach aus 1 m fallen lassen: erster Rücksprung ≤ e₀²·1 m + 2 cm; Ruhe (Schlafzustand) nach ≤ 3 s;
  danach über 5 s Drift < 1 mm und kein Event.
- I2 Wand frontal mit 5 m/s: Normalgeschwindigkeit danach = −e·5 ±10 %; Tangentialgeschwindigkeit sinkt gemäß μ.
- I3 Crash-Schwelle: 0.95·v_crash → kein CRASH; 1.05·v_crash → CRASH und Disarm (für jeden Rahmen).
- I4 Schräger Treffer mit Arm/Propspitze → |ω| nach dem Stoß > 3 rad/s; PROP_STRIKE-Event am richtigen Motor.
- I5 Armed mit Idle auf den Boden setzen (2 m/s) → kein Crash, liegt aufrecht still; kopfüber gelandet → bleibt
  in Rückenlage; Turtle → innerhalb 3 s aufrecht.
- I6 Keine Kollision mit NaN (Report mit NaN-Werten wird ignoriert und gezählt).

**J – Robustheit (`RobustnessTest`)**
- J1 dt ∈ {0, 1e-9, 0.016, 0.05, 0.2, NaN, −1, +∞} → Zustand endlich, Substeps ≤ 60, FRAME_HITCH bei > 50 ms.
- J2 NaN in Sticks und Environment → wie 0/Default behandelt.
- J3 Test-Hook injiziert NaN in v → Rollback im selben Frame, SIM_FAULT, disarmed, nächster Frame sauber.
- J4 30 min simuliert mit zufälligen Sticks und Modi → immer endlich, `|q| = 1 ±1e-9`, mAh monoton.

**K – Determinismus (`DeterminismTest`)**
- K1 Zwei Instanzen, gleicher Seed, gleiche Eingabe- und dt-Folge (inkl. Kollisionen) → bitgleicher Snapshot nach
  120 s (`doubleToRawLongBits`).
- K2 Frame-Split-Invarianz: konstante Eingaben, gleiche Gesamtzeit in ns, verteilt als 60 fps / 144 fps /
  zufällige dt → bitgleicher Zustand.
- K3 Konvergenz: gleiche Flugfolge mit 1 kHz und 2 kHz → Positionsabweichung < 5 cm nach 5 s ruhigem ANGLE-Flug.

**L – Leistung und Speicher (`PerfTest`, `@Tag("perf")`)**
- L1 Allokation: nach dem Aufwärmen (2 000 Frames) 10 000 Frames mit allen Effekten → allozierte Bytes des
  Threads ≤ 64 B (gemessen mit `com.sun.management.ThreadMXBean`; nur im Test, nicht im Hauptcode).
- L2 60 s simuliert (60 000 Substeps) laufen in < 300 ms (≤ 5 µs/Substep) auf CI; die Zahl wird mitgeloggt.

---

## 14. Bewusst nicht (oder später) modelliert
- Prop-Schäden/Unwucht (würde Gyro-Rauschen und RPM-Filter sinnvoll machen), Motor-Desync, Akkutemperatur.
- Getrennter Bodeneffekt pro Rotor (4 Raycasts, z. B. über einer Kante) und Deckensog.
- Rumpfauftrieb bei Messerflug; Downwash auf andere Drohnen (Multiplayer: das Wake-Feld eines Mitspielers
  könnte über den Snapshot geteilt werden).
- Luftdichte über die Höhe (Minecraft-Höhen spielen kaum eine Rolle; `airDensity` bleibt ein Environment-Feld).
