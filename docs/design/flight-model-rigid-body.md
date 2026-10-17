# Flugmodell „Starrkörper + Motorphysik“ — Design

Paket `de.lardtom.propwash.sim` (reines Java 25 + JOML, keine Minecraft-Imports), läuft auf dem Client des Piloten.
Stand: 2026-09-23. Schwerpunkt dieses Entwurfs: physikalisch treuer Starrkörper mit vollem Trägheitstensor und
einem Modell pro Motor (Gegen-EMK, Strom, Drehmoment, Rotorträgheit), Propeller-Beiwerte mit Duct-Gewinn und
Schubverlust über den Fortschrittsgrad, Akku mit Chemie-Kurve, Innenwiderstand, Polarisation und C-Raten-Grenze,
ESC-Strombegrenzung und Erwärmung. Alle Parameter werden aus einem `DroneBuild` abgeleitet — also auch aus jeder
Kombination, die ein Spieler an der **Werkbank** zusammenbaut, nicht nur aus den 5 Presets.

Eigenständige Arbeit: Grundlage sind öffentliche Physik (Impulstheorie, Motorgleichungen, Starrkörper-Mechanik)
und die öffentlich dokumentierten Betaflight-Konzepte (Rates-Formeln, PID-Struktur, Mixer, Airmode, TPA,
I-Term-Relax). Keine Konstanten, Formate oder Strukturen aus anderen FPV-Mods.

---

## 0 Kurzfassung

- **Zustand**: Starrkörper (p, v, q, ω) + 4 Rotordrehzahlen + Akku (SoC, Polarisationsspannung) + Temperaturen
  (4 Motoren, 4 ESC-Kanäle) + FC-Zustände. Fester Physiktakt **2000 Hz**, FC im selben Takt, Frame-dt 0–50 ms wird
  über einen Akkumulator in Substeps zerlegt, Stick-Eingaben werden über den Frame linear interpoliert.
- **Motoren**: Spannungsgesteuertes DC-Ersatzmodell mit `Kt = 1/Kv`, Wicklungs- + ESC-Widerstand, Leerlaufstrom,
  Rotor- + Propträgheit → die Drehzahl-Zeitkonstante ergibt sich aus den Teilen (5"-Freestyle ≈ 11 ms,
  Whoop ≈ 52 ms). Rotordrehzahl wird **implizit** integriert.
- **Bus**: Akku (OCV-Kurve je Chemie, R0 + RC-Polarisation, C-Raten-Aufschlag) und die 4 ESC-Kanäle werden pro
  Substep **gekoppelt** gelöst (geschlossene Form + 1 Korrekturdurchlauf). Spannungseinbruch, ESC-Stromgrenze,
  aktives Bremsen und Rückspeisung fallen daraus heraus.
- **Gieren** = Reaktion auf das elektromagnetische Motormoment (stationär Luftwiderstand der Props, dynamisch der
  Beschleunigungs-Kick) plus Kreiseleffekte aus dem Rotordrall — keine künstliche Yaw-Konstante.
- **Aero**: Schub/Moment aus `ct/cp`, Fortschrittsgrad J (Schubverlust bei Fahrt), Duct-Gewinn mit Abklingen über
  J, Blockade durch Arme, Rotorwiderstand (H-Kraft), anisotroper Rumpfwiderstand, Bodeneffekt, Prop-Wash
  (Wirbelring-Zustand + Wirbel-Nachlauf-Partikel), Wind.
- **FC**: Betaflight-artige Rates (BETAFLIGHT, ACTUAL), PID mit P/I/D/FF, I-Term-Relax, TPA, Anti-Gravity, Airmode,
  Idle, Sag-Kompensation; ANGLE/HORIZON mit Kippfehler aus dem Schwerkraftvektor (singularitätsfrei).
  PID-Vorschlag pro Build aus der Streckenphysik (für Werkbank-Eigenbauten).
- **Kollision**: Entity bewegt sich mit MC-Blockkollision, meldet gesperrte Achsen → Stoßimpuls am echten
  Hüllpunkt (Tumble), Crash-Klassifikation, Prop-Strike; zusätzlich weicher Ruhekontakt pro Substep (Landen,
  Rutschen, Umkippen).
- **Kalibrierung**: Tabellen mit Schwebegas, Maximalschub, Schub/Gewicht, Topspeed, Flugzeit für alle 5 Presets;
  3 vorgeschlagene Datenkorrekturen an den Teile-Enums (Whoop-Motor-R, zwei Prop-cp).

---

## 1 Ziele, Nicht-Ziele, Randbedingungen

**Ziele**
1. Physikalisch nachvollziehbares Verhalten, das sich aus den Teiledaten ergibt (Gewicht, KV, R, Prop-Beiwerte,
   Akku) — Teiletausch an der Werkbank muss spürbare, plausible Folgen haben.
2. Stabil bei jedem Frame-dt 0–50 ms, deterministisch bei gleichem Seed und gleichen Eingaben.
3. ANGLE-Modus, der Lage-Sollwerte präzise und ohne Überschwingen verfolgt (Grundlage für Return-to-Home).
4. Telemetrie für OSD, Motor-Audio, RTH, Netzwerk-Sync.
5. Rechenaufwand < 0,3 ms pro Frame (Worst-Case 50 ms Frame = 100 Substeps).

**Nicht-Ziele (dieser Entwurf)**: Blade-Element-Modell, CFD-Nachlauf, echter Sensor-Fusion-Schätzer (optional
später), 3D-Modus/Turtle (nur als Erweiterung skizziert), Netzwerkprotokoll.

**Randbedingungen**: Keine `net.minecraft.*`-Imports. Keine Allokation im Substep (JOML-Objekte werden
wiederverwendet). Kein `Math.random` — nur der Sim-eigene, geseedete Zufallsgenerator.

---

## 2 Konventionen

| Thema | Festlegung |
|---|---|
| Einheiten | SI intern: m, s, kg, N, N·m, V, A, W, **rad** und rad/s. Grad und °/s nur in FC-Konfiguration, Rates und Telemetrie. 1 Block = 1 m. |
| Welt | Minecraft-Welt: +X Ost, +Y oben, +Z Süd, rechtshändig. Schwerkraft `g_w = (0, −9,81, 0)` m/s². |
| Körper | **+Z_b = Nase (vorne), +Y_b = oben (Schubrichtung), +X_b = links**, `X_b = Y_b × Z_b` (rechtshändig). Ursprung = Schwerpunkt (CoM). |
| Orientierung | `Quaterniond q`: Körper → Welt, `v_w = q·v_b`, `R = R(q)`. |
| Körperrate ω_b | ω_x > 0: Nase runter · ω_y > 0: Gieren nach links (von oben gegen den Uhrzeigersinn) · ω_z > 0: Rollen nach rechts. |
| FC-Achsen | Betaflight-Sinn: roll+ = rechts, pitch+ = Nase runter (Stick nach vorn), yaw+ = Nase nach rechts. `(roll, pitch, yaw) = (ω_z, ω_x, −ω_y)`. |
| Heading | `f = q·ẑ` (Nase in Welt), **ψ = atan2(f.x, f.z)**: 0 = Blick nach +Z (Süd), +90° = nach +X (Ost). |
| MC-Kamera | `yawMC = −ψ·180/π`, `pitchMC = −asin(f.y)·180/π`, Kamera-Roll `φ_cam = atan2(l.y, u.y)` mit `l = q·x̂`, `u = q·ŷ`. |
| Neigung | `g_b = q⁻¹·ŷ_w` (Welt-oben im Körper). Rollwinkel `φ = asin(g_b.x)`, Nickwinkel `θ = asin(−g_b.z)`, Gesamtneigung `tilt = acos(g_b.y)`. |
| Position | `p` = CoM in Weltkoordinaten. Entity-Fußpunkt = `p + (0, hullMin.y, 0)` (siehe §8). |
| Motoren | Index 0..3 = M1..M4 in der öffentlichen Betaflight-Quad-X-Nummerierung: **M1 hinten rechts, M2 vorne rechts, M3 hinten links, M4 vorne links.** |
| Drehrichtung | Standard „Props in“: M1 CW, M2 CCW, M3 CCW, M4 CW (von oben gesehen). `s_i = +1` für CCW (Drallvektor +Y_b), `−1` für CW. `MotorConfig.directionReversed` („Props out“) kehrt alle `s_i` um. |

Motorpositionen (geometrisch, relativ zur Rahmenmitte O auf Armebene, `c = armLength/√2`):
`M1 (−c, y_p, −c)`, `M2 (−c, y_p, +c)`, `M3 (+c, y_p, −c)`, `M4 (+c, y_p, +c)`; die Sim nutzt `r_i = pos_i − CoM`.

---

## 3 Zustand und Modellübersicht

```
PilotInput ──► FlightController ──► ESC-Duty d_i ──► Bus-Löser (Akku ⇄ 4 Kanäle) ──► Ströme I_i
   ▲             (Rates/Angle/Horizon → PID → Mixer)                                   │
   │                                                                                    ▼
 Gyro/Lage ◄── Starrkörper (p, v, q, ω_b) ◄── Kräfte/Momente ◄── Prop-Aero (T_i, Q_i, H_i) ◄── Rotor ω_i
                    ▲          ▲                                  ▲       ▲
      MoveResult ───┘  Environment (Boden, Wind) ────────────────┘   Thermik (R(T), Kt(T))
```

| Teilzustand | Größen | Einheit |
|---|---|---|
| Starrkörper | `p` (3), `v` (3, Welt), `q` (4), `ω_b` (3, Körper) | m, m/s, –, rad/s |
| Rotoren | `ω_i` (4, relativ zum Rahmen, ≥ 0) | rad/s |
| Akku | `SoC` ∈ [0,1], `V_1` (Polarisation), `mAhUsed` | –, V, mAh |
| Thermik | `T_m,i` (4), `T_esc,i` (4) | °C |
| FC | I-Terme, Filterzustände, Sollwert-Glättung, Arming-Zustand | – |
| Aero | Rauschfilter je Rotor (4×2), Wake-Partikel (≤ 100) | – |
| Kontakt | aktive Kontaktebenen (≤ 3) | – |

---

## 4 Parameterableitung aus `DroneBuild`

`ParamDerivation.derive(DroneBuild) → AirframeSpec` ist eine **reine Funktion** (keine Zustände), wird pro Build
einmal berechnet und gecacht (`Map<DroneBuild, AirframeSpec>`, 6000 mögliche Kombinationen). Die Werkbank nutzt
dieselbe Funktion für ihre Anzeige (§12).

### 4.1 Zusatzdaten, die die Teile-Enums nicht enthalten

Bis diese Felder ggf. in die Enums wandern, liegen sie sim-seitig in zwei Tabellen, geschlüsselt über die Enums
(`EnumMap`): `FrameLayout` und `PartPhysics`.

**`FrameLayout`** (mm, Körperachsen, y = 0 auf Armebene; „Anteil“ = Massenanteil des Rahmens in Armen bzw. Ducts,
der Rest ist Kern: Platten, Standoffs, Canopy):

| Rahmen | Akku | y Akkuplatte | y Stack | Kamera (y, z) | VTX (y, z) | Arm-/Duct-Anteil | k_top | e (Stoß) |
|---|---|---|---|---|---|---|---|---|
| WHOOP_65 | unten | 2 | 4 | (10, 14) | (10, 14) | 0,70 Ducts | 0,45 | 0,40 |
| CINEWHOOP_3 | oben | 22 | 10 | (15, 38) | (18, −22) | 0,60 Ducts | 0,45 | 0,40 |
| FREESTYLE_5 | oben | 30 | 12 | (15, 42) | (20, −30) | 0,55 Arme | 0,30 | 0,25 |
| RACE_5 | oben | 25 | 10 | (14, 42) | (18, −28) | 0,55 Arme | 0,30 | 0,25 |
| LONG_RANGE_7 | oben | 32 | 14 | (18, 52) | (22, −38) | 0,55 Arme | 0,30 | 0,25 |

**`PartPhysics`**:

| Teil | Zusatzwert |
|---|---|
| StackType | Grundlast FC+RX: AIO_WHOOP 0,3 W · AIO_20 0,6 W · STACK_30 0,8 W. Brownout-Spannung: 2,9 V · 6,0 V · 6,5 V. |
| VideoType | Leistungsaufnahme: ANALOG_NANO 0,9 W · ANALOG_RACE 3,0 W · DIGITAL_LITE 4,5 W · DIGITAL_HD 9,0 W. Integriert (Kamera+VTX eine Einheit): nur ANALOG_NANO. Masse-Split sonst 45 % Kamera / 55 % VTX. |
| MotorType | Statormaße aus `statorLabel`: erste 2 Ziffern = Durchmesser d_s, Rest = Höhe h_s ("2306.5" → 23 / 6,5 mm). |
| BatteryType | Dichte: LiPo/LiHV 2,0 g/cm³, Li-Ion 2,2 g/cm³. Seitenverhältnis x:y:z — 1S 1,8:1:5 · Li-Ion 0,9:0,6:1 · sonst 1:1,1:2. |

**Globale Modellkonstanten** (`ModelConstants`, alle dokumentiert und in Tests referenziert):

| Konstante | Wert | Bedeutung |
|---|---|---|
| `RHO` | 1,225 kg/m³ | Luftdichte (in `SimSettings` überschreibbar) |
| `BELL_MASS_SHARE`, `BELL_RIM` | 0,40; 0,75 | Anteil Glocke+Magnete an der Motormasse; Anteil der Masse am Außenradius |
| `PROP_J_FACTOR` | 0,22 | `J_prop = 0,22·m_p·R_p²` (verjüngte Blätter, schwere Nabe) |
| `I0_BASE`, `I0_PER_A` | 0,10 A; 0,03 | Leerlaufstrom `I0 = 0,10 + 0,03·I_max` |
| `DUCT_GAIN_MAX`, `DUCT_GAP`, `DUCT_FADE_J` | 0,25; 1,5 mm; 0,40 | Duct-Schubgewinn, Spaltmaß, Abklingen über J |
| `BLOCKAGE` | 0,95 offen / 0,97 Duct | Schubverlust durch Arme/Streben im Abstrom |
| `J0_OFFSET` | 0,05 | Nullschub-Fortschrittsgrad `J0 = P/D + 0,05` |
| `ESC_R_BASE`, `ESC_R_PER_A` | 3 mΩ; 0,15 Ω·A | `R_esc = 0,003 + 0,15/I_esc` (FETs + Leiterbahnen, in Reihe zum Motor) |
| `ESC_K_SW` | 0,01 | Schaltverluste `P_sw = k_sw·V_bus·|I|` |
| `ESC_BURST` | 1,3 | Spitzenstromgrenze = 1,3 × `escCurrentPerMotor` |
| `BAT_R1_FACTOR`, `BAT_TAU1` | 0,5; 5 s | Polarisation `R1 = 0,5·R0`, `τ1 = 5 s` |
| `BAT_C_PENALTY` | 0,5 | Widerstandsaufschlag über Dauer-C-Rate |
| `BAT_LOWSOC_GAIN` | 1,2 | Widerstandsanstieg unter SoC 0,25 |
| `C_H` | 0,08 | Rotorwiderstand (H-Kraft), dimensionslos |
| `SIDE_FACTOR`, `TOP_CD`, `C_ROT` | 1,3; 1,1; 1,0 | Seitenfläche, Beiwert Draufsicht, Rotationsdämpfung |
| `GE_K`, `GE_MAX` | 1,5 offen / 1,2 Duct; 1,35 | Bodeneffekt |
| `WASH_LOSS`, `WASH_T_NOISE`, `WASH_Q_NOISE`, `WASH_HZ` | 0,30; 0,25; 0,15; 12 Hz | Prop-Wash |
| `CP_MOTOR`, `CP_ESC` | 0,55 J/(g·K); 0,9 J/(g·K) × 0,5 | Wärmekapazitäten |

### 4.2 Masse, Schwerpunkt, Trägheitstensor

Massenelemente (Körperachsen, y = 0 auf Armebene, `a = wheelbase/2`, `c = a/√2`):

| Element | Position | Eigenträgheit |
|---|---|---|
| 4 Motoren | `(±c, H_m/2, ±c)`, `H_m = h_s + 0,35·d_s + 2 mm` | Punktmasse |
| 4 Props | `(±c, H_m + 2 mm, ±c)` | Punktmasse (Spin-Trägheit separat, §4.3) |
| Arme (offen) | 4 Stäbe von 0,15a bis a auf der Diagonale, y = −2 mm, Mitte bei 0,575a | `m·L²/12·(E − û ûᵀ)`, L = 0,85a |
| Ducts | 4 Ringe, Radius `R_p + 1,5 mm`, bei `(±c, H_m/2, ±c)` | axial `m r²`, quer `m r²/2` |
| Kern | Quader 0,16·wb × h × 0,45·wb, h = max(y_Akkuplatte, 6 mm), Mitte y = h/2 − 3 mm | Quader |
| Stack | `(0, y_Stack, 0)` | Quader 30 × 12 × 30 mm (skaliert mit wb/225) |
| Kamera / VTX | laut `FrameLayout` | Punktmassen |
| Akku | Quader aus Masse/Dichte/Seitenverhältnis; y = ±(y_Akkuplatte + h/2) | Quader `m/12·(…)` |

```
M      = Σ m_k                                   (muss exakt DroneBuild.totalMassGrams()/1000 sein)
CoM    = Σ m_k r_k / M
I_CoM  = Σ [ I_own,k + m_k (|r'_k|² E − r'_k r'_kᵀ) ],   r'_k = r_k − CoM          [kg·m²]
```
Der volle 3×3-Tensor wird gespeichert (inkl. Deviationsmomente, v. a. `I_yz` durch Kamera vorn / Akku oben) und
einmal invertiert (`Matrix3d.invert`). Die Hülle (§8) entsteht aus denselben Elementen.

**Ergebnisse** (Rahmenmitte → CoM in mm; Trägheit in kg·m²):

| Preset | Masse | CoM (x, y, z) | I_xx (Nicken) | I_yy (Gieren) | I_zz (Rollen) | I_yz | Hülle ½-Breite / Höhe / CoM über Boden |
|---|---|---|---|---|---|---|---|
| WHOOP | 29,6 g | (0; 2,1; 1,9) | 1,013e-5 | 1,694e-5 | 8,59e-6 | −4,4e-7 | 40 / 20 / 12 mm |
| CINEWHOOP | 231,7 g | (0; 17,0; 0,2) | 3,877e-4 | 6,389e-4 | 3,554e-4 | 4,0e-7 | 90 / 57 / 23 mm |
| FREESTYLE | 525,2 g | (0; 24,9; 0,1) | 1,550e-3 | 2,430e-3 | 1,409e-3 | 2,4e-6 | 145 / 76 / 31 mm |
| RACER | 430,2 g | (0; 22,3; 0,1) | 1,201e-3 | 1,982e-3 | 1,114e-3 | 7,3e-7 | 141 / 69 / 28 mm |
| LONG_RANGE | 846,2 g | (0; 31,0; 0,1) | 3,576e-3 | 5,971e-3 | 3,399e-3 | 2,7e-6 | 193 / 80 / 37 mm |

(Hülle ½-Breite = `c + R_p (+1,5 mm Duct)`, aufrecht; Höhe = Akkuoberkante bzw. Motorglocke bis Unterkante.)

### 4.3 Motor und ESC

```
Kv    = KV·π/30                       [rad/(s·V)]
Kt    = Ke = 1/Kv                     [N·m/A = V·s/rad]
R     = R_m(T) + R_esc                [Ω]      R_m(T) = R_m,25·(1 + 0,00393·(T − 25))
R_esc = 0,003 + 0,15 / escCurrentPerMotor        (Stack-abhängig: STACK_30 5,7 mΩ · AIO_20 9,0 mΩ · AIO_WHOOP 33 mΩ)
I0    = 0,10 + 0,03·I_max             [A]      Eisen-/Lagerverluste als Reibmoment Kt·I0
Kt(T) = Kt,25·(1 − 0,0012·(T − 25))            Magnetfluss sinkt mit Temperatur
r_b   = 0,6·d_s + 0,5 mm                       Glocken-Außenradius
J_bell= 0,40·m_motor·0,75·r_b²        [kg·m²]
J_prop= 0,22·m_prop·R_p²
J_r   = J_bell + J_prop
```

| Motor | Kv [rad/(s·V)] | Kt [mN·m/A] | R_m [Ω] | I0 [A] | r_b [mm] | J_bell [kg·m²] |
|---|---|---|---|---|---|---|
| M0802_22000 | 2303,8 | 0,434 | 0,30 (korr., §4.7) | 0,205 | 5,3 | 1,69e-8 |
| M1404_4600 | 481,7 | 2,076 | 0,14 | 0,64 | 8,9 | 2,14e-7 |
| M2207_1750 | 183,3 | 5,457 | 0,055 | 1,45 | 13,7 | 1,86e-6 |
| M2306_1860 | 194,8 | 5,134 | 0,060 | 1,36 | 14,3 | 1,90e-6 |
| M2807_1300 | 136,1 | 7,346 | 0,070 | 1,45 | 17,3 | 4,22e-6 |

**Drehzahl-Zeitkonstante** (linearisiert um den Schwebepunkt, spannungsgesteuert):
```
τ_m = J_r / ( Kt²/R + 2·kQ·ω_h )        [s]
```
Die echte Dynamik ist nichtlinear (ESC-Grenze, Bus-Einbruch, Bremsen) und wird nicht über τ_m, sondern über die
Gleichungen in §5.1–5.2 simuliert; τ_m dient der Kalibrierung und dem PID-Vorschlag.

**ESC-Kanal**: Duty `d ∈ [0,1]` (Mittelwert-Modell, `V_motor = d·V_bus`), aktives Bremsen („damped light“:
Strom darf negativ werden, solange armed), Spitzenstromgrenze `I_lim = 1,3·escCurrentPerMotor·derate(T_esc)`,
Schaltverluste `k_sw·V_bus·|I|`. Disarmed = Brücke offen: `I = 0`, Rotor läuft frei aus.

**Thermik** (pro Motor bzw. ESC-Kanal, 1 Knoten):
```
C_m    = 0,55 J/(g·K) · m_motor
R_th,m = 70 K / P_ref,   P_ref = (0,5·I_max)²·R_m + I0·(0,7·maxCells·3,7 V)     (70 K Übertemperatur bei halbem Maximalstrom im Luftstrom)
R_th(ω)= R_th,m · 3 / (1 + 2·min(ω/ω_ref, 1,5)),   ω_ref = Kv·0,7·maxCells·3,7 V  (ruhende Luft = 3-facher Widerstand)
C_esc  = 0,9 J/(g·K) · 0,5 · m_stack/4
R_th,e = 65 K / (I_esc²·R_esc + k_sw·maxCells·3,8 V·I_esc)                        (90 °C bei Nennstrom)
```

| Motor | C_m [J/K] | P_ref [W] | R_th,m [K/W] | τ_th [s] |   | Stack | C_esc [J/K] | R_th,e [K/W] | τ_th [s] |
|---|---|---|---|---|---|---|---|---|---|
| M0802 | 1,1 | 1,4 | 48 | 53 | | AIO_WHOOP | 0,36 | 54 | 19 |
| M1404 | 5,0 | 18,0 | 3,9 | 19 | | AIO_20 | 0,79 | 5,8 | 4,5 |
| M2207 | 18,2 | 50,4 | 1,39 | 25 | | STACK_30 | 2,03 | 2,2 | 4,4 |
| M2306 | 17,1 | 47,6 | 1,47 | 25 | | | | | |
| M2807 | 25,9 | 58,0 | 1,21 | 31 | | | | | |

### 4.4 Propeller

Die Enum-Beiwerte gelten laut `PropType` für Standschub ohne Duct: `T = ct·ρ·n²·D⁴`, `P = cp·ρ·n³·D⁵`, `n = ω/2π`.
```
kT0   = ct·ρ·D⁴/(4π²)·η_block          [N/(rad/s)²]    T = kT0·g_duct(J)·f_T(J)·… ·ω²
kQ    = cp·ρ·D⁵/(8π³)                  [N·m/(rad/s)²]  Q = kQ·f_P(J)·ω²          (cq = cp/2π)
g_duct,0 = 1 + 0,25·max(0, 1 − 6·δ/R_p),  δ = 1,5 mm      (nur ducted; kleiner Prop → relativ großer Spalt → wenig Gewinn)
P/D   = pitchInch·0,0254 / D ;  J0 = P/D + 0,05
```

| Prop | D [m] | P/D | J0 | kQ [N·m·s²] | J_prop [kg·m²] |
|---|---|---|---|---|---|
| P31_4B | 0,031 | 0,98 | 1,03 | 1,202e-11 (cp 0,085 korr.) | 1,85e-8 |
| P3_3B | 0,076 | 1,00 | 1,05 | 1,002e-9 (cp 0,080 korr.) | 5,72e-7 |
| P51_3B | 0,130 | 0,90 | 0,95 | 1,100e-8 | 4,00e-6 |
| P7_2B | 0,178 | 0,50 | 0,55 | 3,177e-8 | 1,185e-5 |

kT0 inkl. Blockade und Duct-Gewinn im Stand (vom Rahmen abhängig): WHOOP 3,071e-9 (g_duct 1,105) ·
CINEWHOOP 1,256e-7 (g_duct 1,191) · FREESTYLE/RACER 1,010e-6 · LONG_RANGE 2,663e-6 N·s².

Induzierte Geschwindigkeit im Schwebeflug (für Prop-Wash): `v_h = sqrt(T/(2ρ·π R_p²·g_duct))` — WHOOP 5,96 ·
CINEWHOOP 6,55 · FREESTYLE 6,29 · RACER 5,70 · LONG_RANGE 5,83 m/s.

### 4.5 Akku

```
R0     = cells·cellResistanceOhm                     [Ω]
R1     = 0,5·R0,  τ1 = 5 s,  C1 = τ1/R1              (Polarisation, erholt sich nach dem Punch)
I_cont = cRating·capacityMah/1000                    [A]  (= BatteryType.maxContinuousCurrent())
R_eff  = R0 · (1 + 1,2·max(0, 0,25 − SoC)/0,25) · (1 + 0,5·max(0, I_bat/I_cont − 1)²)
V_oc   = cells·[ V_empty + (V_full − V_empty)·u(SoC) ]
```
OCV-Formkurve `u(SoC)` (linear interpoliert; Stützstellen SoC = 1,0 / 0,9 / … / 0,1 / 0,05 / 0):

| Chemie | 1,0 | 0,9 | 0,8 | 0,7 | 0,6 | 0,5 | 0,4 | 0,3 | 0,2 | 0,1 | 0,05 | 0 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| LIPO / LIHV | 1,00 | 0,83 | 0,69 | 0,57 | 0,49 | 0,43 | 0,39 | 0,34 | 0,29 | 0,20 | 0,13 | 0 |
| LION | 1,00 | 0,88 | 0,78 | 0,68 | 0,60 | 0,52 | 0,44 | 0,36 | 0,27 | 0,16 | 0,09 | 0 |

(LiPo 4,20 V → 3,80 V bei 50 % → 3,50 V leer; Li-Ion flacher und tiefer.) `cutoffCellVoltage` ist die
Grenze für Warnungen und das Arming, nicht für die Kurve.

| Akku | Zellen | Ah | R0 [mΩ] | R1 [mΩ] | I_cont [A] | V_full / V_empty / V_cutoff (Pack) |
|---|---|---|---|---|---|---|
| B1S_300_HV | 1 | 0,30 | 60 | 30 | 13,5 | 4,35 / 3,50 / 3,30 |
| B4S_650 | 4 | 0,65 | 40 | 20 | 61,8 | 16,8 / 14,0 / 13,2 |
| B6S_1100 | 6 | 1,10 | 36 | 18 | 132 | 25,2 / 21,0 / 19,8 |
| B6S_1300 | 6 | 1,30 | 33 | 16,5 | 156 | 25,2 / 21,0 / 19,8 |
| B6S_4000_LION | 6 | 4,00 | 108 | 54 | 44 | 25,2 / 18,0 / 16,8 |

### 4.6 Rumpf- und Rotor-Aerodynamik

```
CdA_z (vorn)  = dragCoefficient·frontalAreaM2
CdA_x (Seite) = 1,3·CdA_z
CdA_y (oben)  = 1,1·k_top·wheelbase²          (k_top aus FrameLayout)
F_drag,b      = −½ρ·|v_b|·(CdA_x·v_bx, CdA_y·v_by, CdA_z·v_bz)            [N]   v_b = Rᵀ(v − wind)
H_i           = −C_H·ρ·D³·n_i·v_⊥,i                                         [N]   Rotorwiderstand je Rotor, C_H = 0,08
τ_rot         = −½ρ·C_ROT·(wheelbase/2)⁵·|ω_b|·ω_b                          [N·m] Rotationsdämpfung (Taumeln)
```

| Preset | CdA Seite / oben / vorn [cm²] | lineare Rotor-Dämpfung im Schwebeflug 4·C_H·ρ·D³·n_h [N/(m/s)] |
|---|---|---|
| WHOOP | 50 / 21 / 39 | 0,009 |
| CINEWHOOP | 260 / 100 / 200 | 0,058 |
| FREESTYLE | 140 / 167 / 108 | 0,155 |
| RACER | 88 / 153 / 68 | 0,140 |
| LONG_RANGE | 187 / 287 / 144 | 0,311 |

### 4.7 Vorgeschlagene Datenkorrekturen an den Teile-Enums

Mit den Originaldaten verfehlen zwei Presets die Plausibilitätsbänder (§13) deutlich:

| Wert | Ist | Vorschlag | Grund (Modellwert Ist → Vorschlag) |
|---|---|---|---|
| `MotorType.M0802_22000.resistanceOhm` | 0,60 | **0,30** | Whoop-Drehzahl-Zeitkonstante 93 ms → 52 ms; 0,60 Ω ergibt bei 22000 KV einen für die Baugröße ~10× zu schwachen Motor (R·KV² weit außerhalb der Skalierung der übrigen Motoren). |
| `PropType.P31_4B.powerCoefficient` | 0,060 | **0,085** | Figure of Merit 0,56 → 0,40 (winzige Props, kleine Reynoldszahl); Schwebezeit 6,6 → 5,5 min. |
| `PropType.P3_3B.powerCoefficient` | 0,058 | **0,080** | Schub/Gewicht Cinewhoop 8,1 → 7,1; Schwebezeit 9,6 → 7,6 min. |

Alle Zahlen in diesem Dokument gelten **mit** diesen Korrekturen. Werden sie nicht übernommen, bleiben die
Formeln gültig; nur die Tabellenwerte von WHOOP/CINEWHOOP und die zugehörigen Testziele ändern sich.

### 4.8 Abgeleitete Kennwerte der 5 Presets

Stationär gelöst (Bus gekoppelt, Idle 5,5 %, „Punch“ = volles Gas bei SoC 1,0 ohne Polarisation; Schwebeflug
mit eingeschwungener Polarisation). Schwebegas = Stick-Throttle bei linearer Throttle-Kurve.

| Kennwert | WHOOP | CINEWHOOP | FREESTYLE | RACER | LONG_RANGE |
|---|---|---|---|---|---|
| Masse | 29,6 g | 231,7 g | 525,2 g | 430,2 g | 846,2 g |
| Schub/Motor Schweben | 0,073 N | 0,568 N | 1,288 N | 1,055 N | 2,075 N |
| Drehzahl Schweben | 46 400 U/min | 20 300 | 10 780 | 9 760 | 8 430 |
| Motorstrom Schweben | 0,86 A | 2,82 A | 4,02 A | 3,60 A | 4,82 A |
| Schwebegas SoC 1,0 / 0,5 / 0,2 | 55 / 64 / 68 % | 25 / 28 / 30 % | 21 / 24 / 25 % | 17 / 20 / 21 % | 24 / 29 / 34 % |
| Akkustrom Schweben (SoC 0,5) | 2,64 A | 4,11 A | 5,17 A | 3,80 A | 7,10 A |
| Leerlaufdrehzahl (voll geladen) | 95 700 | 77 300 | 44 100 | 46 900 | 32 760 |
| Drehzahl Punch | 72 500 | 54 100 | 34 600 | 35 000 | 18 600 |
| **Max. Schub/Motor** | **0,18 N (18 g)** | **4,03 N (411 g)** | **13,26 N (1352 g)** | **13,58 N (1384 g)** | **10,10 N (1030 g)** |
| **Schub/Gewicht** | **2,44** | **7,10** | **10,3** | **12,9** | **4,87** |
| Motorstrom / Akkustrom Punch | 1,8 / 7,6 A | 16,1 / 65,6 A | 27,9 / 113 A | 30,2 / 122 A | 17,9 / 72,8 A |
| Busspannung Punch | 3,90 V | 14,17 V | 21,46 V | 20,81 V | 15,66 V |
| τ_m Schweben / Vollgas | 52 / 47 ms | 24 / 20 ms | 11,4 / 10,3 ms | 13,9 / 12,2 ms | 20,9 / 19,2 ms |
| Rotorträgheit J_r | 3,54e-8 | 7,86e-7 | 5,86e-6 | 5,90e-6 | 1,61e-5 kg·m² |
| Autorität B_roll / B_pitch | 2388 / 2025 | 1985 / 1820 | 2121 / 1928 | 2458 / 2279 | 1667 / 1584 rad/s² |
| B_yaw stationär / sofort | 206 / 1205 | 176 / 1366 | 168 / 3490 | 198 / 3726 | 109 / 1487 rad/s² |
| **Topspeed** (Vollgas, SoC 0,8) | **14,0 m/s (50 km/h)** bei 66° | **31,4 m/s (113 km/h)** bei 82° | **41,4 m/s (149 km/h)** bei 80° | **43,1 m/s (155 km/h)** bei 81° | **22,3 m/s (80 km/h)** bei 67° |
| **Flugzeit Schweben** (80 % Kapazität) | **5,5 min** | **7,6 min** | **12,1 min** | **13,9 min** | **27,0 min** |
| **Flugzeit Reiseflug** (½ Topspeed, SoC 0,5) | **4,8 min** (7,0 m/s) | **4,0 min** (15,7 m/s) | **4,5 min** (20,7 m/s) | **4,1 min** (21,5 m/s) | **16,8 min** (11,2 m/s) |

Autorität `B` = Winkelbeschleunigung pro Einheit Mixer-Ausgang am Schwebepunkt:
```
dω/dd   = (Kt·V_bus/R) / (Kt²/R + 2·kQ·ω_h)
dT/dc   = 2·kT0·g_duct·ω_h·(dω/dd)·(1 − idle)
B_roll  = 4·c·(dT/dc)/I_zz,   B_pitch = 4·c·(dT/dc)/I_xx
B_yaw,stat = 4·2·kQ·ω_h·(dω/dd)·(1 − idle)/I_yy,   B_yaw,sofort = 4·Kt·V_bus·(1 − idle)/(R·I_yy)
```
Gieren ist „sofort“ ~10–20× stärker als stationär: ein Duty-Sprung erzeugt sofort Strom und damit
Reaktionsmoment, bevor die Drehzahl nachzieht — das ist der typische Yaw-Kick echter Quads.

---

## 5 Dynamik-Gleichungen

### 5.1 Elektrischer Kreis (pro Substep)

Unbekannt ist `V_bus`; pro Kanal `G_i = 1/R_i`, Gegen-EMK `E_i = Ke(T_i)·ω_i`, Duty `d_i`.
```
I_i    = G_i·(d_i·V_bus − E_i)                                  Motorstrom [A] (armed; disarmed: 0)
I_bat  = Σ d_i·I_i + I_x,     I_x = (P_aux + k_sw·V_bus·Σ|I_i|)/V_bus
V_bus  = V_oc(SoC) − V_1 − R_eff·I_bat

⇒ Durchlauf 1 (geschlossen, I_x aus dem Vor-Substep):
V_bus  = ( V_oc − V_1 − R_eff·I_x + R_eff·Σ d_i·G_i·E_i ) / ( 1 + R_eff·Σ d_i²·G_i )
```
Durchlauf 2: Kanäle mit `|I_i| > I_lim,i` werden begrenzt (`I_i = ±I_lim`); der ESC senkt dafür den
effektiven Duty auf `d_i' = (E_i ± I_lim·R_i)/V_bus`, der Kanal zieht dann die Leistung `P_i = (E_i ± I_lim R_i)·I_lim`
aus dem Bus. `V_bus` wird einmal mit `Σ_unbegrenzt d_i G_i (d_i V_bus − E_i) + Σ_begrenzt P_i/V_bus` neu
berechnet (Fixpunkt, V_bus aus Durchlauf 1 im Nenner). `R_eff` nutzt `I_bat` des Vor-Substeps.
Grenzen: `V_bus ∈ [0, V_oc + 0,2·cells]` (Rückspeisung beim Bremsen lädt kurz). Brownout: `V_bus` unter der
Brownout-Spannung des Stacks für > 50 ms → Disarm `BROWNOUT` (1S-Whoop am Ende des Akkus!).

### 5.2 Rotordynamik

```
τ_shaft,i = Kt(T_i)·(I_i − I0·σ(ω_i))          σ(ω) = ω/(|ω| + 1)  (glattes Vorzeichen)
J_r·ω̇_i   = τ_shaft,i − Q_i(ω_i)
```
Implizites Euler, linearisiert (stabil auch für den Whoop und beliebige Werkbank-Kombinationen):
```
f  = (τ_shaft − Q)/J_r
∂f/∂ω = −( Kt·Ke/R + 2·kQ·f_P·ω )/J_r        (bei aktiver Stromgrenze: −2·kQ·f_P·ω/J_r)
ω_i ← max(0, ω_i + h·f / (1 − h·∂f/∂ω))
```

### 5.3 Propeller-Aerodynamik (je Rotor)

```
v_i,b  = Rᵀ(v − wind) + ω_b × r_i                    lokale Anströmung am Rotorkopf (Körper)
V_a,i  = v_i,b · ŷ                                    axial, > 0 = Steigen (Luft strömt von oben durch die Scheibe)
v_⊥,i  = v_i,b − V_a,i·ŷ                              in der Rotorebene
n_i    = ω_i/2π,   J_i = clamp( V_a,i / (max(n_i, 5)·D), −1,5·J0, 1,5·J0 )

f_T(J) = J ≥ 0: max(−0,1, 1 − J/J0)       J < 0: min(1,3, 1 − 0,6·J/J0)      (Sinken erhöht Schub bis VRS)
f_P(J) = clamp(1 − 0,5·(J/J0)·|J/J0|, 0,1, 1,3)
g_duct(J) = 1 + (g_duct,0 − 1)·clamp(1 − J/0,40, 0, 1)                      (Duct hilft nur bei wenig Fahrt)
f_μ    = 1 + 1,2·min(μ, 0,3)²,   μ = |v_⊥|/(ω_i·R_p + 1)                     (Translationsauftrieb)

T_i = kT0·g_duct(J)·f_T·f_μ·g_ge,i·(1 − 0,30·w_i)·(1 + 0,25·w_i·ξ_i)·ω_i²          [N]
Q_i = kQ·f_P·(1 + 0,15·w_i·ξ'_i)·ω_i²                                             [N·m]
```
`g_ge,i` Bodeneffekt (§5.4), `w_i` Prop-Wash-Stärke 0..1 und `ξ_i` bandbegrenztes Rauschen (§5.5).
Das Moment `Q_i` wirkt auf den Rotor (bremst ihn), nicht direkt auf den Rahmen — der Rahmen spürt die Reaktion
über `τ_shaft` (§5.6). Weil `v_i,b` den Term `ω_b × r_i` enthält, entsteht die aerodynamische Rolldämpfung
(steigende Seite verliert Schub) ohne eigene Konstante.

### 5.4 Bodeneffekt

`Environment.groundDistance` = Abstand CoM → erste feste Blockoberseite senkrecht darunter (∞ ab 5 m).
```
u_y   = (q·ŷ).y                                       nur wirksam, wenn u_y > 0,5
z_i   = (groundDistance + (R·r_i).y) / u_y            Rotorabstand entlang der Schubachse
z_eff = max(z_i, 0,6·R_p)
g_ge,i = min(GE_MAX, 1 / (1 − GE_K·(R_p/(4·z_eff))²))    GE_K = 1,5 offen / 1,2 Duct, GE_MAX = 1,35
inGroundEffect = max_i g_ge,i ≥ 1,03
```

### 5.5 Prop-Wash und Wirbel-Nachlauf

**Wirbelring-Zustand (eigener Abstrom beim Sinken)** je Rotor:
```
v_h,i  = sqrt( max(T_i,0) / (2ρ·π R_p²·g_duct) )        aktuelle induzierte Geschwindigkeit
μ_z    = −V_a,i / max(v_h,i, 0,5)                        > 0 beim Sinken in den eigenen Abstrom
μ_xy   = |v_⊥,i| / max(v_h,i, 0,5)                       Querströmung bläst den Nachlauf weg
w_i,vrs = exp(−((μ_z − 1)/0,5)²) · exp(−(μ_xy/0,8)²) · clamp(T_i/(0,25·T_hover), 0, 1)
```
**Wirbel-Nachlauf („dirty air“ nach Split-S, 180°-Wende, Durchfliegen der eigenen Spur)**: `WakeField` mit
≤ 100 Partikeln. Alle 20 ms wird ein Partikel bei `p − (q·ŷ)·0,5a` erzeugt: Geschwindigkeit
`0,3·v_Luft − (q·ŷ)·v_h`, Stärke `T_ges/(m·g)`, Radius `0,8·(c + R_p)`, Lebensdauer 2 s, Stärke-Zerfall
`exp(−Alter/1,2 s)`, Geschwindigkeit `·exp(−Alter/1 s)` plus Wind. Bei 200 Hz wird je Rotor die Summe
`u_wake,i = Σ Stärke·v_h·K(d/Radius)` (K = glatter Kern, 0 ab d ≥ Radius) gebildet; sie wird als zusätzlicher
Abstrom von `V_a,i` abgezogen und erhöht `w_i = clamp(w_i,vrs + 0,8·min(1, u_wake,i/v_h), 0, 1)`.
Eigene Partikel ab 0,3 s Alter wirken (sonst träfe man ständig den gerade erzeugten Abstrom); das Feld ist als
Schnittstelle so gebaut, dass später auch fremde Drohnen Partikel einspeisen können.

**Wirkung**: Schubverlust `0,30·w_i`, Schubrauschen `±0,25·w_i`, Momentrauschen `±0,15·w_i` (s. §5.3), dazu ein
kleines Rahmenmoment `τ_wash = 0,02·m·g·a·w̄·ξ_vec`. `ξ` = weißes Rauschen durch zwei PT1 bei 12 Hz, auf
Effektivwert 1 normiert, pro Rotor eigener Strom. Telemetrie `propWashIntensity = PT1_10Hz(max_i w_i)`.

### 5.6 Kräfte, Momente, Starrkörper

```
F_b   = Σ_i ( T_i·ŷ + H_i ) + F_drag,b + F_contact,b                        [N]   Körper
F_w   = R·F_b + m·g_w                                                       [N]   Welt
τ_b   = Σ_i r_i × (T_i·ŷ + H_i)  −  Σ_i s_i·τ_shaft,i·ŷ  +  τ_rot + τ_wash + τ_contact,b      [N·m]
h_r   = Σ_i s_i·J_r·ω_i·ŷ                                                   Rotordrall (Körper)
I·ω̇_b = τ_b − ω_b × (I·ω_b + h_r)                                           Euler-Gleichung mit Kreiselterm
```
Herleitung Gieren: Gesamtdrall `H = I·ω_b + h_r`; äußeres Moment auf die Props ist `−s_i·Q_i·ŷ`; mit
`J_r·ω̇_i = τ_shaft − Q` kürzt sich Q heraus und es bleibt die Reaktion `−s_i·τ_shaft,i` auf den Rahmen.
Stationär (τ_shaft = Q) ist das das Luftwiderstandsmoment der Props, beim Beschleunigen zusätzlich `J_r·ω̇`.

**Integration (semi-implizit / symplektisch, h = 0,5 ms)**:
```
v   ← v + h·F_w/m
p   ← p + h·v ;  Δp_frame += h·v
ω_b ← ω_b + h·I⁻¹·( τ_b − ω_b × (I·ω_b + h_r) )
q   ← q ⊗ exp(½·ω_b·h)  (Körperrate → Rechtsmultiplikation, exakte Achsen-Winkel-Rotation)
q   ← q/|q|
```
Spezifische Kraft (Beschleunigungsmesser): `f = (F_w − m·g_w)/m`; `gLoad = PT1_20Hz(f·(q·ŷ))/9,81` (1,0 im
Schwebeflug), `gLoadTotal = |f|/9,81`.

### 5.7 Thermik (mit 100 Hz integriert, exakte Exponential-Lösung)

```
P_m,i   = I_i²·R_m(T_m,i) + |τ_I0|·ω_i            (Kupfer + Eisen/Reibung, τ_I0 = Kt·I0)
C_m·Ṫ_m = P_m − (T_m − T_amb)/R_th(ω_i)
P_e,i   = I_i²·R_esc + k_sw·V_bus·|I_i|
C_esc·Ṫ_e = P_e − (T_e − T_amb)/R_th,e·(1 + min(ω_i/ω_ref,1))      (Propwind kühlt den Stack mit)
```
Folgen: `R_m(T)` steigt (+0,393 %/K), `Kt(T)` sinkt (−0,12 %/K). ESC-Derating: `I_lim` linear von 1,3·I_esc bei
110 °C auf 0,6·I_esc bei 140 °C. Warnschwellen Motor 100 °C, ESC 100 °C. Mit `SimSettings.damage`: Motor > 150 °C
→ Kt dauerhaft −10 %, > 180 °C → Motor tot; ESC > 160 °C oder Überspannung `V_oc > 1,1·stack.maxCells·4,35 V`
→ Kanal tot. `T_amb` aus `Environment.ambientTemperature` (Standard 20 °C).

### 5.8 Akku-Zustand

```
SoC      ← SoC − I_bat·h / (3600·C_Ah)               (C_Ah = capacityMah/1000)
mAhUsed  ← mAhUsed + I_bat·h/3,6
V_1      ← V_1·e^(−h/τ1) + R1·I_bat·(1 − e^(−h/τ1))
Ruhespannung (Schätzung für OSD/Arming) = V_oc(SoC)/cells
```
Warnstufen aus der gefilterten Zellspannung unter Last (PT1 1 Hz): `LOW` < `emptyCellVoltage`, `CRITICAL` <
`cutoffCellVoltage`, `EMPTY` bei SoC ≤ 0,03. `SimSettings.infiniteBattery` (Creative) hält SoC konstant, Sag bleibt,
warnt aber nicht. Stand 0.4.0 (Rückweg mit Hysterese, Halten bei leerem Akku): `flight-model-spec.md` §5.10.

### 5.9 Wind, Rauschen, Zufall

- `Environment.wind` (Welt, m/s) plus Böen: je Achse PT1(0,5 Hz)-gefiltertes Rauschen × `turbulence`·(0,3·|wind| + 0,5 m/s).
- Gyro-Vibration (optional, `SimSettings.vibration` 0..1): je Motor Sinus bei `ω_i` und `2ω_i`, Amplitude
  `vibration·4 °/s·(ω_i/ω_h)²`, feste Phasen pro Motor, plus weißes Rauschen 0,1 °/s eff. Damit werden
  Gyro-/D-Term-Filter und RPM-Filter sinnvoll; Standard 0,3.
- `SimRandom`: xoshiro256** (eigene Implementierung), Seed aus `SimSettings.seed`; alle Rauschquellen ziehen
  in fester Reihenfolge → deterministisch.

---

## 6 Flight Controller

### 6.1 Takt und Signalweg

FC läuft im Physiktakt (2000 Hz, `SimSettings.fcDivider = 1`). Pro FC-Schritt:
`StickPositions (interpoliert) → Rates/Angle/Horizon → Sollrate sp [°/s] → PID (Gyro gefiltert) → Mixer → c_i → Duty d_i`.
Die Motorbefehle wirken ab dem nächsten Substep (1 Sample Verzögerung, wie echter Loop).

Gyro: `gyro = (ω_z, ω_x, −ω_y)·180/π + Vibration`, dann PT1 `gyroLpf1Hz` (250), optional 2. PT1, optional
RPM-Notch (je Motor Grundwelle + 2. Harmonische, Frequenz exakt aus `ω_i`, Q = 5). D-Term: eigene PT1
`dtermLpf1Hz` (100) + optional 2. Stufe. Lage für ANGLE: ideal aus `q` (ein Schätzer mit Beschleunigungsmesser
ist als Option vorgesehen, nicht Standard).

### 6.2 Rates (Stick x ∈ [−1, 1] → Sollrate [°/s])

**BETAFLIGHT** (rcRate r, superRate s, rcExpo e):
```
x_e = x·|x|³·e + x·(1 − e)
r'  = r > 2 ? r + 14,54·(r − 2) : r
ω   = 200·r'·x_e ;   falls s > 0:  ω = ω / clamp(1 − |x|·s, 0,01, 1)
```
Beispiel r = 1,0, s = 0,70, e = 0: Vollausschlag 666,7 °/s.

**ACTUAL** (center C, max M, expo e):
```
ω = x·C + max(0, M − C)·|x|·(x⁵·e + x·(1 − e))
```
Beide Kurven sind ungerade und monoton; `RatesCurve.inverse(ω)` löst per Bisektion (30 Iterationen) — gebraucht
vom Autopiloten. Harte Grenze 1998 °/s.

### 6.3 Throttle

```
t0 = Stick 0..1
t  = mid + (t0 − mid)·(1 − expo + expo·((t0 − mid)/(t0 ≥ mid ? 1 − mid : mid))²)       Kurve (mid, expo)
Limit: SCALE → t·limit,  CLIP → min(t, limit)
```
`ThrottleCurve.inverse` für den Autopiloten; `telemetry.hoverThrottleEstimate` ist der Stickwert, der beim
aktuellen Akkuzustand Schweben ergibt (aus der stationären Motorlösung, ohne Bodeneffekt).

### 6.4 Raten-PID (je Achse roll/pitch/yaw, Einheiten °/s)

Gains sind Betaflight-ähnliche Zahlen; die Skalierung ist so gewählt, dass die Größenordnungen vertraut wirken:
```
e      = sp − gyro
P      = (P·3,2e-5)·e·tpa_P
I     += (I·2,44e-4)·e·relax·boost_AG·h            |I| ≤ itermLimit (0,4); Anti-Windup s. u.
D      = −(D·5,3e-7)·d(gyro_D)/dt·tpa_D            D auf Messung (kein Setpoint-Kick)
FF     = (F·6,8e-8)·d(sp_ff)/dt                     sp_ff = PT1(ffSmoothingHz = 30)(sp)
sum    = clamp(P + I + D + FF, ±pidSumLimit)        roll/pitch 0,5; yaw 0,4
```
Einheit von `sum`: Mixer-Anteil (1,0 = voller Motorbereich). FF = 100 entspricht beim FREESTYLE-Preset ≈ 83 %
der idealen Trägheits-Vorsteuerung `1/B`.

- **I-Term-Relax** (`itermRelax` OFF/RP/RPY, Typ SETPOINT): `hpf = sp − PT1(itermRelaxCutoffHz = 15)(sp)`,
  `relax = max(0, 1 − |hpf|/40 °/s)`. Typ GYRO: dieselbe Formel mit `gyro` statt `sp`.
- **TPA** (`tpaMode` D oder PD, `tpaRate` 0,65, `tpaBreakpoint` 0,35): `tpa = 1 − tpaRate·clamp((t − bp)/(1 − bp), 0, 1)`.
- **Anti-Gravity** (`antiGravityGain` 0..2, Standard 1): `boost_AG = 1 + 3·gain·clamp(|ṫ_f|/5 s⁻¹, 0, 1)`, `ṫ_f` = PT1(15 Hz) der Throttle-Ableitung. Physikalisch nötig, weil CoM und Schubebene nicht zusammenfallen (Nickmoment bei Gasstößen).
- **Anti-Windup**: Ist der Mixer gesättigt (`motorSaturated`), wird I nur integriert, wenn es |I| verkleinert.
- **Reset**: I = 0 und Filter auf Gyro-Wert beim Arming; ohne Airmode zusätzlich solange `t < 0,05`.

### 6.5 ANGLE (Selbstnivellierung)

Stick → Ziel-Neigung (**linear, ohne Rates/Expo**, damit der Autopilot exakt invertieren kann):
```
A     = angleLimit (Standard 60°)
g_t,x = sin(x_roll·A),  g_t,z = −sin(x_pitch·A)
falls |(g_t,x, g_t,z)| > sin A: beide skalieren auf Betrag sin A        (Gesamtneigung ≤ A, „runder“ Stick)
g_t,y = sqrt(1 − g_t,x² − g_t,z²)
```
Fehler singularitätsfrei aus dem Schwerkraftvektor (gilt auch rückenlage-nah):
```
g_b   = q⁻¹·ŷ_w
axis  = g_b × g_t,   α = atan2(|axis|, g_b·g_t)
ω_lvl = −K_a·α·axis/|axis|            K_a = angleStrength/10 [1/s] (Standard 60 → 6/s)
ω_lvl.y = 0                            (Nivellieren nur über Rollen/Nicken)
```
Entartung: ist `|axis| < 1e-6` und `g_b·g_t < 0` (exakt kopfüber), wird die Roll-Achse ẑ gewählt.
Beweis der Richtung: ein weltfester Vektor ändert sich im Körper mit `ġ_b = −ω × g_b`; mit obigem ω zeigt
`ġ_b` in Richtung der zu `g_b` senkrechten Komponente von `g_t`. Für kleine Winkel entspricht das exakt
`sp_roll = K_a·(φ_t − φ)`, `sp_pitch = K_a·(θ_t − θ)`.

Angle-Feedforward: `+ (angleFeedforward/100)·d(Ziel)/dt` (Ziel über `ffSmoothingHz` geglättet).
Gieren erdbezogen (`angleEarthRef` 0..1, Standard 1): Gierstick → Rates-Kurve → Welt-Gierrate ψ̇ (rechts
positiv) → Körperrate `ω_yaw = −ψ̇·g_b`; gemischt `(1 − k)·ψ̇·(−ŷ) + k·ω_yaw`. Summe in FC-Achsen
umrechnen, Betrag je Achse ≤ `angleMaxRate` (Standard 500 °/s). Danach normaler Raten-PID — dessen I-Term gleicht
CoM-Versatz und Wind aus, daher bleibender Winkelfehler ≈ 0.

### 6.6 HORIZON

```
w  = horizonStrength/100 · clamp(1 − max(|x_roll|,|x_pitch|)/(horizonStickLimit/100), 0, 1)
                         · clamp(1 − tilt/horizonTiltLimit, 0, 1)
sp = Rates(x) + w·ω_lvl(Ziel = waagerecht)
```
Standard: Strength 50, StickLimit 75 %, TiltLimit 135°. Mittelstick = nivelliert, Vollausschlag = Flip.

### 6.7 Mixer, Airmode, Idle, Sag-Kompensation

Mixer-Koeffizienten werden **aus der Geometrie** berechnet (auch für schiefe Eigenbauten):
```
roll_i  = x_i / max|x|,    pitch_i = −z_i / max|z|,    yaw_i = s_i        (x_i, z_i relativ zum CoM)
→ Props-in-Standard: M1 (−1, +1, −1)  M2 (−1, −1, +1)  M3 (+1, +1, +1)  M4 (+1, −1, −1)
```
Ablauf:
1. `a_i = roll_i·U_r + pitch_i·U_p + yaw_i·U_y`.
2. `range = max a − min a`; falls `range > 1`: `a_i /= range` (Autorität vor Gas), `motorSaturated = true`.
3. Airmode an: `t' = clamp(t, −min a, 1 − max a)` (Gas wird verschoben, volle Regelung auch bei 0 % Gas).
   Airmode aus: `t' = t`.
4. `m_i = clamp(t' + a_i, 0, 1)` (Sättigung → `motorSaturated`).
5. Sag-Kompensation (`vbatSagCompensation` 0..100 %, Standard 0): `m_i = min(1, m_i·(1 + k·(V_ref/V_bus,f − 1)))`,
   `V_ref = cells·3,95 V`, `V_bus,f` = PT1 (1 Hz).
6. Duty `d_i = idle + (1 − idle)·m_i` (armed, `motorIdle` Standard 5,5 %), disarmed `d_i = 0` + Brücke offen.

### 6.8 Arming / Disarming

Arming bei steigender Flanke von `PilotInput.armSwitch`. Blocker werden laufend als `EnumSet<ArmBlocker>`
berechnet (OSD kann sie jederzeit zeigen):

| ArmBlocker | Bedingung |
|---|---|
| `THROTTLE_NOT_LOW` | Throttle-Stick > 0,05 |
| `TILTED` | Neigung > `smallAngle` (25°) |
| `PACK_DEPLETED` | SoC ≤ 0,03 oder Ruhespannung/Zelle < `cutoffCellVoltage` |
| `BUILD_INVALID` | `!build.buildable()` (Werkbank lässt so etwas eigentlich nicht heraus) |
| `ARM_SWITCH_ON` | Schalter war beim Start/nach Crash schon an — erst aus, dann wieder an |
| `CRASH_COOLDOWN` | < 1,0 s seit Crash-Disarm |
| `HARDWARE_DAMAGED` | Motor/ESC tot (nur mit `damage`) |

`DisarmReason`: `SWITCH`, `CRASH`, `BROWNOUT`, `DAMAGE`, `RESET`. Ergebnis eines Arm-Versuchs:
`ArmResult(boolean armed, EnumSet<ArmBlocker> blockers)`, zusätzlich Event `ArmRefused`.

### 6.9 PID-Vorschlag für beliebige Builds

`PidProfile.suggestFor(AirframeSpec)` — für Presets und jeden Werkbank-Eigenbau:
```
T_d  = 3,5 ms                      (Loop + Filter + ESC-Totzeit)
ω_c  = min(150 rad/s, 0,5/T_d, 5/τ_m)
Kp   = ω_c/B,   Kd = Kp·min(τ_m, 30 ms),   Ki = 12·Kp        (SI, Mixer-Anteil pro rad/s …)
P = Kp/(3,2e-5·57,296) ; I = Ki/(2,44e-4·57,296) ; D = Kd/(5,3e-7·57,296) ; F = 100
Yaw: P, I wie Roll; D = 0; F wie Roll
```
Ergebnis (gerundet; im 1-Achsen-Referenzmodell mit Motorlag, 1 Sample Totzeit, Gyro-PT1 250 Hz, D-PT1 100 Hz,
I-Relax, Sollwert-Glättung 30 Hz geprüft):

| Preset | Roll P/I/D | Pitch P/I/D | Yaw P/I/D | 500 °/s-Sprung t90 / Überschwingen (FF 0 → FF 100) | 30°-ANGLE-Sprung t90 |
|---|---|---|---|---|---|
| WHOOP | 22 / 35 / 40 | 26 / 41 / 47 | 22 / 35 / 0 | 75 ms / 9,0 % → 66 ms / 5,7 % | 0,41 s, < 0,1 % |
| CINEWHOOP | 39 / 62 / 56 | 43 / 67 / 61 | 39 / 62 / 0 | 66 ms / 4,7 % → 61 ms / 3,7 % | 0,41 s |
| FREESTYLE | 37 / 58 / 25 | 40 / 64 / 28 | 37 / 58 / 0 | 42 ms / 1,2 % → 35 ms / 0,7 % | 0,40 s |
| RACER | 32 / 50 / 27 | 34 / 54 / 29 | 32 / 50 / 0 | 48 ms / 1,8 % → 40 ms / 1,1 % | 0,40 s |
| LONG_RANGE | 47 / 74 / 59 | 49 / 77 / 62 | 47 / 74 / 0 | 61 ms / 3,8 % → 57 ms / 3,1 % | 0,41 s |

Die Presets verwenden genau diese Vorschläge als Standard-PID-Profil.

### 6.10 Autopilot-Kontrakt (Return-to-Home)

Der Autopilot fährt ANGLE mit Stick-Befehlen. Garantien der Sim:
1. Stick → Neigung ist linear (`x = asin(g_t,x)/A` bzw. `asin(−g_t,z)/A`), Gier-Stick → Gierrate über
   `RatesCurve.inverse`, Throttle über `ThrottleCurve.inverse` und `hoverThrottleEstimate`.
2. Hilfsfunktion `AutopilotMath.sticksFor(targetRollDeg, targetPitchDeg, yawRateDegS, throttle, FlightConfig)`.
3. Verfolgung (Testkriterien §14): 30°-Sprung t90 ≤ 0,5 s, Überschwingen ≤ 2°, bleibender Fehler ≤ 0,3°
   (auch mit 3 m/s Seitenwind ≤ 1°); Rampen bis 60 °/s folgt sie mit ≤ 1,5° Schleppfehler.
4. Telemetrie liefert Lage (roll/pitch/heading), Geschwindigkeit (Welt), Vertikalgeschwindigkeit,
   `hoverThrottleEstimate`, Bodenabstand (vom Entity durchgereicht).

---

## 7 Integrationsschritt

Konstanten: `h = 1/2000 s`, `MAX_FRAME_DT = 0,05 s`, `MAX_SUBSTEPS = 100`.

```java
// Pro gerendertem Frame (Client, PilotSession):
sim.setInput(input, frameDt);            // Ziel-Eingabe; Interpolationsrampe über frameDt
for (slice : slices(frameDt))            // Empfehlung s. u.; ohne Slicing: ein Aufruf mit frameDt
    StepResult r = sim.step(slice, env);
    Vec3 moved = entity.moveWithCollisions(r.displacement());
    sim.applyMove(MoveResult.of(r.displacement(), moved, surface));

// step(dt, env):
dt = clamp(dt, 0, MAX_FRAME_DT)          // Lag-Spitze > 50 ms: Zeitdilatation statt Nachholen
acc += dt
n = min((int) floor(acc/h), MAX_SUBSTEPS);  acc -= n*h
Δp = 0
repeat n:
   tIn = min(1, (inputClock += h) / inputRampDt)
   u   = lerp(prevInput, targetInput, tIn)          // Sticks/Throttle linear; Schalter ab erstem Substep
   substep(u, env)                                   // s. u.
   Δp += v*h
events → StepResult;  return StepResult(Δp, hullMin, hullMax, events)

// substep(u, env):
 1  Sensorik: Gyro aus ω_b (+ Vibration), Filter
 2  FC (jeder fcDivider-te Substep): Arming-Logik, Rates/Angle/Horizon, PID, Mixer → c_i, d_i
 3  Bus-Löser (§5.1) mit d_i der *vorigen* FC-Ausgabe → V_bus, I_i, I_bat
 4  Aero je Rotor (§5.3–5.5): lokale Anströmung, J, Bodeneffekt, Wash → T_i, Q_i, H_i
 5  Rotoren implizit (§5.2) → ω_i
 6  Kontaktkräfte weich (§8.3) → F_contact, τ_contact
 7  Kräfte/Momente, Starrkörper (§5.6): v, p, ω_b, q
 8  Akku (§5.8): SoC, mAh, V_1
 9  alle 20 Substeps (100 Hz): Thermik, Wake-Partikel bewegen/erzeugen, Warnstufen, Brownout-Zähler
10  Telemetrie-Akkumulatoren (g-Last, Wash-Intensität, Maxima)
```

**Slicing-Empfehlung für das Entity**: große Frame-Verschiebungen verschlechtern das Kollisions-Timing
(Stoß wird erst am Frame-Ende angewendet). `maxSlice = clamp(0,25 m / max(|v|, 0,001), 0,002, 0,05) s`; bei
40 m/s und 50 ms Frame sind das 8 Slices (je ein `move`). Eingabe-Interpolation läuft über `setInput` und ist
davon unabhängig.

**Kosten**: ~250 Gleitkomma-Ops Rotoren+Bus, ~150 Starrkörper, ~200 FC pro Substep → < 1,5 µs (JIT).
60 fps ≈ 33 Substeps ≈ 50 µs/Frame; Worst-Case 100 Substeps < 0,2 ms. Keine Allokation im Substep.

**Genauigkeit**: 2 kHz reichen für τ_m ≥ 5 ms (Rotor ohnehin implizit), Kontaktfeder 12 Hz (ω_n·h = 0,04) und
Flips mit 2000 °/s (0,017 rad pro Substep, exakte Quaternion-Exponentialabbildung).

---

## 8 Kollisionen

### 8.1 Kontrakt Entity ↔ Sim

- `StepResult.displacement` (Welt, m) = Summe `v·h` des Aufrufs. Das Entity bewegt sich damit per
  Blockkollision (`move`).
- `StepResult.hullMin/hullMax`: Welt-AABB der **aktuell rotierten** Hülle relativ zum CoM. Empfehlung: das
  Entity nutzt `width = 2·max(|hullMin.x|, hullMax.x, |hullMin.z|, hullMax.z)`, Höhe `hullMax.y − hullMin.y`,
  Fußpunkt `p + (0, hullMin.y, 0)` und aktualisiert die Dimensionen pro Frame. So berührt der tiefste echte
  Hüllpunkt den Boden (gekippte Landung, Prop-Spitzen). Mit fester AABB funktioniert das Modell auch (§8.3,
  Rückfall), sieht aber bei großer Neigung ungenauer aus. Feste Referenz-AABB aufrecht: siehe §4.2 Tabelle.
- `MoveResult(requested, actual, hitX, hitY, hitZ, surfaceFriction = 0,6, surfaceRestitution = NaN, hitEntity)`:
  Achse gilt als getroffen, wenn `|actual_a − requested_a| > 1e-7`. `hitEntity` für Gates o. ä.

### 8.2 Stoß (einmal pro `applyMove`)

1. `p ← p_Start + actual` (Entity ist autoritativ für die Position).
2. Je getroffene Achse `a` (Reihenfolge Y, X, Z): Kontaktnormale `n = −sign(requested_a)·ê_a` (zeigt vom Hindernis weg).
3. Kontaktpunkt `r_c` = Stützpunkt der Hülle in Richtung `−n`: Minimum von `(R·r_k)·n` über 8 Kernquader-Ecken
   (inkl. Akku/Kamera) und 4 Rotorscheiben (analytischer Tiefstpunkt: `m_i + R_p'·(−n_b − (−n_b·ŷ)ŷ)/|…|`).
4. `v_c = v + ω_w × r_c,w`, `u = −v_c·n`. Nur wenn `u > 0` (Annäherung):
```
e   = u < 0,3 m/s ? 0 : (isNaN(surfaceRestitution) ? e_Rahmen : surfaceRestitution)     (e_Rahmen 0,25 offen / 0,40 Duct)
j   = (1 + e)·u / ( 1/m + n·((I_w⁻¹(r_c × n)) × r_c) )           I_w = R·I·Rᵀ
t̂   = tangentiale Kontaktgeschwindigkeit normiert,  j_t = min(μ·j, |v_c,t| / (1/m + t̂·((I_w⁻¹(r_c × t̂)) × r_c)))
J   = j·n − j_t·t̂ ;   v += J/m ;   ω_w += I_w⁻¹(r_c × J) ;   ω_b = Rᵀ·ω_w
```
   Ein außermittiger Treffer (Prop-Spitze, Akku-Ecke) erzeugt so automatisch Drehimpuls → Taumeln.
5. Kontaktebene für §8.3 merken: `n` und Lage der AABB-Fläche (`d = n·p − Abstand CoM→AABB-Fläche`).

### 8.3 Weicher Ruhekontakt (in jedem Substep)

Für jede aktive Ebene (≤ 3) und jeden Hüllpunkt k (12 Punkte wie oben): Eindringtiefe `δ = d − n·(p + R r_k)`.
```
k_c = m·(2π·12 Hz)²,  c_c = 2·0,8·sqrt(k_c·m)            (pro Punkt durch Anzahl eindringender Punkte geteilt)
F_n = max(0, k_c·min(δ, 0,01 m) − c_c·(v_k·n))
F_t = −μ·F_n·v_k,t / max(|v_k,t|, 0,05 m/s)             (regularisierte Coulomb-Reibung)
F_contact += F_n·n + F_t ;   τ_contact += (R r_k) × (F_n n + F_t)
```
Wirkung: Aufsetzen ohne Zittern, Rutschen, Umkippen über eine Kante, Liegenbleiben auf dem Rücken. Die Ebene
verfällt, wenn der nächste `MoveResult` die Achse nicht mehr meldet **und** kein Hüllpunkt näher als 2 cm ist,
oder sie > 0,5 m entfernt ist. Rückfall bei fester AABB: `d = min(AABB-Fläche, aktueller Stützpunkt)`, damit
gekippte Hüllpunkte nicht schlagartig 5–10 cm tief „im Boden“ starten. `telemetry.onGround` = Bodenebene mit
`F_n > 0`.

Stand 0.5.0 bis 0.5.1: Umgesetzt war der Rückfall mit fester AABB – der Boden hielt die Höhe des Schwerpunkts, ein
Aufrichtmoment `m·g·a·sin θ` brachte die Drohne in die flache Lage (unscharf mit vollem Hebel, `GroundRest`).

Stand 0.5.2: Kontaktpunkte statt Federn (`sim.GroundSolver`, `ground-contact.md` §6). Je Punkt ein Normalimpuls ≥ 0
und Coulomb-Reibung, gelöst als projiziertes Gauss-Seidel in jedem Substep (Warmstart, bis 64 Durchläufe), die Ebene
aus der AABB-Auflage oder dem Strahl auf festen Boden. Die Federn oben (12 Hz) wurden verworfen: Ein harter Aufsetzer
mit 3 m/s hätte 4 cm Eindringen gebraucht, und die Kappung bei 1 cm hätte die Drohne weich und schwammig durch den
Boden drehen lassen. Die AABB bleibt das Entity; liegt die Drohne schief, schwebt sie um die Hubhöhe über dem Boden.
Der Stoß beim Aufsetzen der AABB läuft über denselben Löser (nicht mehr über §8.2, das für Wände und Decken bleibt).
Seit der zweiten Runde von 0.5.2 (`ground-contact.md` §7) fällt die Rückprallzahl am Boden über 0,3 · Crash-Geschwindigkeit
mit (v/v₀)^(−1/4) statt linear um bis zu 60 %, und Prop-Spitzen, Motorböden und Kamera-Nase haken bei einem harten
Einschlag ein (Reibungszahl bis ×5, solange der Punkt am Boden bleibt).

### 8.4 Klassifikation und Folgen

| Ereignis | Bedingung | Folge |
|---|---|---|
| `Crash` | `u ≥ frame.impactTolerance` | Disarm `CRASH` (wenn `SimSettings.crashDisarm`), Event mit Stoßgeschwindigkeit, Normale, Kontaktpunkt; mit `damage`: je Prop 30 % Chance auf −10…40 % Wirkungsgrad |
| `PropStrike` | Stützpunkt ist eine offene Rotorscheibe (nicht ducted), `ω_i > 0,3·ω_h`, `u ≥ 1 m/s` oder tangential ≥ 3 m/s | `ω_i ← 0,3·ω_i`; Rahmen-Impuls `J_s = J_r·0,7·ω_i/R_p` am Randpunkt entgegen der Blattbewegung `−(s_i ŷ × r̂)` → heftiger Kick; Event |
| `Bump` | 0,5 ≤ `u` < impactTolerance | nur Event (für Sound/Kamera) |
| Landung | Bodenebene aktiv, `u` < 1,5 m/s, Neigung < 30°, Schub < Gewicht | `onGround`, kein Event-Spam |

Nach einem Crash läuft die Physik weiter: Motoren laufen frei aus, der Rahmen fällt und taumelt mit dem Drall
aus dem Stoß, prallt mit `e` ab und kommt über §8.3 zur Ruhe.

---

## 9 Telemetrie und Events

`Telemetry` ist ein wiederverwendetes Objekt mit öffentlichen Feldern (Lesen jederzeit, `copy()` für Snapshots):

| Gruppe | Felder |
|---|---|
| Zeit/Modus | `simTime` [s], `armed`, `mode` (ACRO/ANGLE/HORIZON), `armBlockers` (EnumSet), `lastDisarmReason` |
| Motoren (`MotorTelemetry[4]`, M1..M4) | `rpm`, `output` (Mixer-Befehl 0..1), `duty` (0..1), `currentA`, `thrustN`, `shaftTorqueNm`, `rpmFraction` (ω/ω_Leerlauf), `motorTempC`, `escTempC`, `currentLimited`, `damaged`, `propEfficiency` |
| Akku | `packVoltage` (unter Last), `cellVoltage` (unter Last), `cellVoltageResting`, `totalCurrentA`, `powerW`, `mAhUsed`, `chargeFraction`, `batteryState` (OK/LOW/CRITICAL/EMPTY) |
| Flug | `rollDeg`, `pitchDeg`, `headingDeg`, `tiltDeg`, `groundSpeed`, `airspeed`, `verticalSpeed`, `gLoad`, `gLoadTotal`, `propWashIntensity` (0..1), `inGroundEffect`, `groundEffectGain`, `onGround`, `thrustToWeightNow`, `hoverThrottleEstimate` |
| FC | `setpointDegS[3]`, `gyroDegS[3]`, `pidP/I/D/F[3]`, `pidSum[3]`, `throttleOut`, `motorSaturated` |
| Crash | `lastImpactSpeed`, `crashCount`, `propStrikeCount` |

Events (`sealed interface SimEvent`, Records, pro `step`/`applyMove` gesammelt):
`Armed`, `Disarmed(DisarmReason)`, `ArmRefused(EnumSet<ArmBlocker>)`, `Crash(double impactSpeed, Vector3dc normal,
Vector3dc contactPointBody, boolean disarmed)`, `PropStrike(int motor, double speed)`, `Bump(double speed)`,
`Landed`, `BatteryWarning(BatteryState)`, `MotorOverheat(int motor)`, `EscOverheat(int motor)`,
`HardwareDamaged(int motor, Kind kind)`, `Brownout`.

Verbraucher: OSD (Akku, Lage, Blocker), Motor-Audio (`rpm`, `currentA`, `propWashIntensity` für Rauschanteil),
RTH (Lage, Geschwindigkeit, Schwebegas), Netzwerk (Snapshot `DroneState` + `chargeFraction` + `rpm[4]`).

---

## 10 Konfigurationsobjekte

Alle Klassen: öffentliche, veränderliche Felder, parameterloser Konstruktor, Defaults in den Feldinitialisierern,
`copy()` und `sanitize()` (klemmt alle Werte in gültige Bereiche, damit JSON-Fehleingaben nichts sprengen).
Keine Minecraft-Typen → Gson/Codec-tauglich.

```java
public final class FlightConfig {                  // Wurzel, pro Drohne gespeichert
    public RateProfile rates = new RateProfile();
    public PidProfile pid = new PidProfile();
    public FilterSettings filters = new FilterSettings();
    public MotorConfig motors = new MotorConfig();
    public static FlightConfig defaultsFor(AirframeSpec p);   // PID-Vorschlag §6.9
}

public final class RateProfile {
    public RatesType type = RatesType.ACTUAL;         // BETAFLIGHT | ACTUAL
    public RateCurve roll = RateCurve.actual(200, 670, 0.54), pitch = RateCurve.actual(200, 670, 0.54),
                     yaw = RateCurve.actual(200, 500, 0.40);
    public double throttleMid = 0.5, throttleExpo = 0.0;        // Betaflight führt diese im Rate-Profil; wir auch
    public ThrottleLimitType throttleLimitType = ThrottleLimitType.OFF;   // OFF | SCALE | CLIP
    public double throttleLimit = 1.0;
    public static final class RateCurve {
        public double rcRate = 1.0, superRate = 0.70, rcExpo = 0.0;       // BETAFLIGHT
        public double centerRate = 200, maxRate = 670, expo = 0.54;       // ACTUAL (°/s, °/s, 0..1)
    }
}

public final class PidProfile {
    public AxisPid roll = new AxisPid(37, 58, 25, 100), pitch = new AxisPid(40, 64, 28, 100),
                   yaw = new AxisPid(37, 58, 0, 100);
    public static final class AxisPid { public double p, i, d, ff; }
    public double pidSumLimit = 0.5, pidSumLimitYaw = 0.4, itermLimit = 0.4;
    public ItermRelax itermRelax = ItermRelax.RP;       // OFF | RP | RPY
    public ItermRelaxType itermRelaxType = ItermRelaxType.SETPOINT;   // SETPOINT | GYRO
    public double itermRelaxCutoffHz = 15;
    public TpaMode tpaMode = TpaMode.D;                  // D | PD
    public double tpaRate = 0.65, tpaBreakpoint = 0.35;
    public double antiGravityGain = 1.0;                 // 0 = aus
    public double angleStrength = 60, angleLimitDeg = 60, angleFeedforward = 50, angleEarthRef = 1.0,
                  angleMaxRateDegS = 500;
    public double horizonStrength = 50, horizonStickLimit = 75, horizonTiltLimitDeg = 135;
    public double smallAngleDeg = 25;                    // Arming-Neigung
    public double ffSmoothingHz = 30;
    public static PidProfile suggestFor(AirframeSpec p);
}

public final class MotorConfig {
    public boolean airmode = true;
    public double motorIdle = 0.055;                     // Anteil Duty
    public boolean directionReversed = false;            // Props out
    public double vbatSagCompensation = 0;               // 0..100 %
}

public final class FilterSettings {                      // optional wirksam; bei vibration = 0 kaum Einfluss
    public double gyroLpf1Hz = 250, gyroLpf2Hz = 0, dtermLpf1Hz = 100, dtermLpf2Hz = 0;
    public boolean rpmFilter = true;  public int rpmHarmonics = 2;  public double rpmFilterMinHz = 80;
}

public final class SimSettings {                         // Spielregeln/Creative, nicht im Konfigurator
    public int physicsHz = 2000, fcDivider = 1;
    public double airDensity = 1.225, gravity = 9.81;
    public boolean propWash = true, wakeField = true, groundEffect = true, batterySag = true,
                   infiniteBattery = false, motorHeating = true, damage = false, crashDisarm = true;
    public double vibration = 0.3;
    public long seed = 0;
}
```

---

## 11 Klassen- und API-Layout

```
de.lardtom.propwash.sim
├── DroneSim                  Fassade (einziger Einstieg für PilotSession)
├── PilotInput                roll, pitch, yaw ∈ [−1,1], throttle ∈ [0,1], mode, armSwitch, (später: turtle)
├── StabMode                ACRO, ANGLE, HORIZON
├── Environment               groundDistance, wind (Vector3d), turbulence, airDensity, ambientTemperature
├── StepResult                displacement, hullMin, hullMax, events (List<SimEvent>, wiederverwendet)
├── MoveResult                requested, actual, hitX/Y/Z, surfaceFriction, surfaceRestitution, hitEntity
├── DroneState                Snapshot p, v, q, ω_b, rotorOmega[4], armed, charge (Netzwerk, Teleport)
├── Telemetry, MotorTelemetry
├── ArmBlocker, DisarmReason, ArmResult, BatteryState
├── SimEvent                  sealed + Records
├── SimSettings
├── config/                   FlightConfig, RateProfile, PidProfile, MotorConfig, FilterSettings, Enums
├── model/
│   ├── AirframeSpec           record: alle abgeleiteten Konstanten (Masse, CoM, I, I⁻¹, r_i, s_i, Motor-, Prop-,
│   │                         Akku-, ESC-, Thermik-, Aero-Parameter, Hülle, Mixer-Koeffizienten, τ_m, B)
│   ├── ParamDerivation       DroneBuild → AirframeSpec (rein, gecacht)
│   ├── FrameLayout, PartPhysics, ModelConstants
│   ├── MassModel             Massenelemente → CoM, Tensor, Hülle
│   ├── CellCurve             OCV-Formkurven je Chemie
│   ├── PowerBus              Bus-Löser §5.1 (Akku + 4 ESC-Kanäle)
│   ├── BatteryState, RotorSet (ω_i, implizites Update), ThermalNode
│   ├── PropAero              §5.3 (J, Duct, Translationsauftrieb, Rotorwiderstand)
│   ├── GroundEffect, PropWash, WakeField
│   ├── BodyAero              Rumpfwiderstand, Rotationsdämpfung, Wind/Böen
│   ├── RigidBody             Integration §5.6
│   ├── Contact               Stoß §8.2 + weicher Kontakt §8.3 + Klassifikation §8.4
│   └── SimRandom, BandNoise
├── fc/
│   ├── FlightController      Takt, Modi, Arming, Verdrahtung
│   ├── RatesCurve            BETAFLIGHT/ACTUAL + inverse
│   ├── ThrottleCurve         + inverse
│   ├── AxisPid               P/I/D/FF, Relax, TPA, Anti-Gravity, Anti-Windup
│   ├── Leveler               ANGLE/HORIZON (Schwerkraftvektor-Fehler)
│   ├── Mixer                 Geometrie-Koeffizienten, Airmode, Sag-Komp., Idle
│   ├── GyroModel             Vibration + Filterkette
│   ├── Pt1, Biquad           Filter (Notch/LPF)
│   └── AutopilotMath         Stick-Inversion für RTH
└── perf/
    ├── PerformanceEstimator  stationäre Löser (Schweben, Punch, Topspeed, Flugzeit) für die Werkbank
    └── PerformanceEstimate   record
```

Öffentliche Fassade:
```java
public final class DroneSim {
    public DroneSim(DroneBuild build, FlightConfig config, SimSettings settings);
    public AirframeSpec params();
    public void reset(Vector3dc comPosition, double headingRad);    // am Boden, disarmed, Rotoren stehen
    public void rebuild(DroneBuild build);                          // nur disarmed; Pose bleibt
    public void setConfig(FlightConfig config);                     // Konfigurator „Speichern“; PID-Zustände reset
    public void setSettings(SimSettings settings);
    public void setBatteryCharge(double fraction);                  // Akku aus dem Item-Data-Component
    public void setInput(PilotInput input, double frameDt);
    public StepResult step(double dt, Environment env);
    public void applyMove(MoveResult result);
    public ArmResult lastArmResult();
    public Telemetry telemetry();
    public DroneState state();
    public void teleport(Vector3dc p, Quaterniondc q, Vector3dc v); // Server-Korrektur, Respawn
}
```
Einfädig (Render-Thread), keine Locks. `AirframeSpec` ist unveränderlich und darf von der Werkbank (auch
serverseitig, `common`) parallel benutzt werden.

---

## 12 Werkbank-Anbindung

Die Werkbank zeigt Trocken- und Abfluggewicht, Schub/Gewicht, Flugzeit und Topspeed (siehe `ARCHITECTURE.md`). Dafür:
```java
PerformanceEstimate PerformanceEstimator.estimate(DroneBuild build)   // < 2 ms, gecacht
record PerformanceEstimate(double massGrams, double thrustToWeight, double maxThrustPerMotorGrams,
        double hoverThrottle, double hoverCurrentA, double punchCurrentA, double punchPackVoltage,
        double topSpeedMs, double hoverFlightTimeMin, double cruiseFlightTimeMin,
        double motorTimeConstantMs, boolean canHover, EnumSet<PerfWarning> warnings)
```
Gleiche Gleichungen wie §5 im Gleichgewicht (Bisektion auf ω bzw. V_bus, Topspeed über Nickwinkel-Suche
20°–88° in 0,5°-Schritten, siehe §4.8). `PerfWarning`: `CANNOT_HOVER` (T/W < 1,15), `HEAVY_SAG` (Punch-Zellspannung
< 3,3 V), `ESC_LIMITED` (Punch-Strom > ESC-Grenze), `MOTOR_HOT` (Punch-Kupferverlust > 3·P_ref),
`SLUGGISH` (τ_m > 60 ms). Damit sieht der Spieler vor dem Fliegen die Folgen der `warnings()` aus `DroneBuild`
(zu kleiner ESC, schwacher Akku, Überspannung). Die Sim selbst simuliert diese Folgen ebenfalls, statt sie zu
verbieten: Überspannung → höhere Drehzahl und Ströme → Erwärmung; zu kleiner ESC → Stromgrenze/Derating;
schwacher Akku → starker Sag, ggf. Brownout.

---

## 13 Kalibrierziele

Zwei Ebenen: (a) **Designwerte** aus §4.8 — die Java-Implementierung muss sie innerhalb der Toleranz treffen;
(b) **Plausibilitätsbänder** aus öffentlich bekannten Größenordnungen realer Drohnen — die Designwerte liegen darin.

| Ziel | Toleranz Impl. | WHOOP | CINEWHOOP | FREESTYLE | RACER | LONG_RANGE |
|---|---|---|---|---|---|---|
| Schwebegas (Stick, SoC 0,5) | ±0,02 abs. | 0,64 (Band 0,50–0,70) | 0,28 (0,25–0,40) | 0,24 (0,20–0,30) | 0,20 (0,15–0,25) | 0,29 (0,25–0,35) |
| Max. Schub/Motor | ±3 % | 18 g (15–22) | 411 g (300–450) | 1352 g (1200–1700) | 1384 g (1200–1700) | 1030 g (900–1300) |
| Schub/Gewicht | ±3 % | 2,44 (2,0–3,0) | 7,1 (5–8) | 10,3 (8–12) | 12,9 (10–14) | 4,9 (4–6) |
| τ_m Schweben | ±15 % | 52 ms (30–60) | 24 ms (15–30) | 11 ms (8–20) | 14 ms (8–20) | 21 ms (15–30) |
| Topspeed | ±7 % | 50 km/h (40–60) | 113 km/h (90–125) | 149 km/h (130–165) | 155 km/h (140–175) | 80 km/h (75–110) |
| Flugzeit Schweben | ±10 % | 5,5 min (4–6) | 7,6 min (6–9) | 12,1 min (8–13) | 13,9 min (9–14) | 27 min (22–30) |
| Flugzeit Reiseflug | ±10 % | 4,8 min (3–5) | 4,0 min (3,5–5) | 4,5 min (3,5–5,5) | 4,1 min (3–4,5) | 16,8 min (14–20) |

**Kalibrierprozess**, falls ein Wert das Band verlässt (Reihenfolge, damit Stellschrauben sich nicht gegenseitig
verdecken): 1. Masse/Trägheit (Layout) → 2. Prop `ct` (Schub/Gewicht) → 3. Prop `cp` (Schwebestrom, Flugzeit) →
4. Motor `R`/Trägheitsfaktoren (τ_m) → 5. Rumpf-CdA / `C_H` (Topspeed) → 6. Akku `R0` (Sag). Globale Konstanten
nur ändern, wenn mehrere Presets gleichsinnig abweichen. Jede Änderung aktualisiert die Tabellen hier und die
Testwerte (§14) gemeinsam.

---

## 14 Testplan (JUnit 5, `common/src/test/java/de/lardtom/propwash/sim/`)

Alle Tests deterministisch (`seed = 42`, `vibration = 0` außer wo genannt). Hilfsklasse `SimHarness` fliegt ohne
Minecraft: Boden als Ebene y = 0 (liefert `groundDistance` und simuliert `MoveResult` durch Klemmen), feste
Frame-Raten oder Zufalls-dt.

**Parameter (`ParamDerivationTest`)**
1. Masse = `DroneBuild.totalMassGrams()` ±0,01 g für alle 5 Presets und 500 zufällige baubare Kombinationen.
2. Trägheitstensor symmetrisch, positiv definit, Dreiecksungleichung `I_xx + I_yy ≥ I_zz` (zyklisch); Diagonale
   und CoM je Preset ±1 % zur Tabelle §4.2.
3. `Kt·Kv = 1` (±1e-12), `τ_m`, `B_*`, kT0, kQ ±1 % zu §4.3/4.4/4.8.
4. Alle 6000 Kombinationen: alle Parameter endlich, `derive` + `estimate` zusammen < 10 s.

**Motor/Bus (`PowerBusTest`, `RotorTest`)**
5. Ohne Prop (kQ = 0) und ideale Quelle: Leerlaufdrehzahl = `Kv·V − I0·R·Kv` ±0,5 %.
6. Stationär bei Duty 0,5: ω = analytische Lösung ±0,5 %; `V_bus` erfüllt §5.1 ±1 mV.
7. Duty-Sprung Schwebe → +10 %: 63 %-Zeit = τ_m ±15 %.
8. ESC-Grenze: Sprung 0 → 100 % beim FREESTYLE: Motorstrom nie > 1,3·55 A + 0,5 A; mit halbierter Grenze
   dauert der Hochlauf länger (monoton).
9. Aktives Bremsen: Duty 100 % → Idle: Drehzahl fällt auf < 50 % in < 3·τ_m; Batteriestrom kurz negativ;
   disarmed: Auslauf deutlich langsamer (> 5× länger bis 10 % Drehzahl).

**Akku (`BatteryTest`)**
10. OCV monoton in SoC, Endpunkte = `full/emptyCellVoltage`·cells exakt.
11. Konstant 10 A für 360 s: `mAhUsed` = 1000 ±0,1 %; SoC-Abnahme passend zur Kapazität ±0,1 %.
12. Stromsprung 0 → I_cont: sofortiger Einbruch `R0·I` ±2 %, nach 5·τ1 `(R0 + R1)·I` ±2 %; Erholung nach Lastende
    mit τ1 ±10 %.
13. Über C-Rate (2·I_cont): Einbruch ≥ 1,4× des linearen Werts.

**Starrkörper (`RigidBodyTest`)**
14. Freier Fall 1 s (Motoren aus, Aero aus): `v_y = −9,81` ±0,005 m/s, `Δy = −4,905` ±0,01 m.
15. Momentfreie Rotation mit asymmetrischem Tensor (instabile Mittelachse, Dzhanibekov): |Drall| und Energie
    driften über 10 s < 0,5 %; `||q| − 1| < 1e-9`.
16. Vorzeichen: nur M3+M4 (links) mehr Schub → ω_z > 0 (Roll rechts); nur M1+M3 (hinten) → ω_x > 0 (Nase runter);
    nur CCW-Motoren M2+M3 hochfahren → ω_y < 0 (Gieren rechts); Heading einer Nase nach +X = +90°.
17. Kreiseleffekt: bei drehenden Rotoren mit Netto-Drall ≠ 0 erzeugt eine Nick-Rate eine Roll-Beschleunigung
    `≈ −ω × h_r / I` ±5 %.

**Aero (`AeroTest`)**
18. Schubverlust: bei J = J0/2 ist `T = 0,5·T_stand` ±2 %; Duct-Gewinn bei J ≥ 0,4 = 1.
19. Bodeneffekt: `g_ge(∞) = 1`, bei z = R_p Gewinn = 1/(1 − GE_K/16) ±0,5 %; Schweben in 0,5·R_p über Grund
    braucht ≥ 5 % weniger Duty.
20. Prop-Wash: senkrechtes Sinken mit v = v_h bei Schwebeschub → `propWashIntensity > 0,6`; Vorwärtsflug 10 m/s
    → < 0,1; im Sinken ist der Raten-Fehler (RMS, ACRO, Sticks mittig) ≥ 3× so groß wie im Schwebeflug.
21. Wake: Split-S (180° in 1 s zurück durch die eigene Spur) → Intensität > 0,3 beim Durchflug.

**Kalibrierung (`CalibrationTest`, parametrisiert über die 5 Presets)**
22. Schwebegas: Höhenregler (PI auf Throttle) hält 20 s; gemittelter Stickwert = Tabelle §13 ±0,02.
23. Max. Schub/Motor (fixiert, Vollgas, SoC 1,0, 0,3 s nach Sprung): ±3 %.
24. Topspeed: ACRO, Vollgas, Lage-Halter auf Tabellen-Nickwinkel, Höhen-Korrektur über Nickwinkel ±3°,
    nach 15 s Horizontalgeschwindigkeit ±7 %.
25. Flugzeit Schweben bis 80 % Verbrauch: ±10 % (≈ 3,2 Mio. Substeps beim LR, < 10 s Rechenzeit; `@Tag("slow")`).

**Flight Controller (`RatesCurveTest`, `PidTest`, `ModeTest`, `MixerTest`, `ArmingTest`)**
26. Rates: BETAFLIGHT r 1,0 / s 0,7 / e 0 → Vollausschlag 666,67 °/s ±0,01; ACTUAL 200/670 → Vollausschlag 670,
    Steigung bei 0 = 200 °/s je Einheit ±0,5 %; `inverse(curve(x)) = x` ±1e-6 für 1000 Werte.
27. ACRO-Sprung 0 → 500 °/s (Sollwert-Glättung 30 Hz), Default-PID inkl. FF 100 je Preset: t90 ≤ 1,3 × Tabellenwert
    „FF 100“ aus §6.9, Überschwingen ≤ 12 %, Restfehler nach 0,3 s < 2 %.
28. Gieren: Sprung 0 → 300 °/s: t90 ≤ 150 ms (FREESTYLE, RACER), ≤ 300 ms (WHOOP); Überschwingen ≤ 10 %.
29. ANGLE: 0 → 30° Rollen: t90 ≤ 0,5 s, Überschwingen ≤ 2°, bleibender Fehler nach 1,5 s ≤ 0,3°; mit 3 m/s
    Seitenwind ≤ 1°; Heading-Drift bei Gier-Stick 0 über 10 s ≤ 2°; Rampe 60 °/s Schleppfehler ≤ 1,5°.
30. ANGLE aus Rückenlage (Umschalten bei 170° Roll): nach 1,0 s Neigung < 10°, keine NaN.
31. HORIZON: Sticks mittig → Neigung nach 1 s < 3°; Vollausschlag Roll → > 360° in 1 s.
32. Airmode: 0 % Gas, voller Roll-Stick → erreichte Rate ≥ 80 % Sollrate; Airmode aus → ≤ 40 %.
33. Mixer: Geometrie-Koeffizienten = Tabelle §6.7; `directionReversed` kehrt nur die Yaw-Spalte.
34. Arming: Throttle 0,1 → `THROTTLE_NOT_LOW`; 40° Neigung → `TILTED`; SoC 0,02 → `PACK_DEPLETED`;
    Build mit `problems()` → `BUILD_INVALID`; nach Crash ohne Schalter-Zyklus → `ARM_SWITCH_ON`; WHOOP bei SoC 0,06
    armen und Vollgas halten → Disarm `BROWNOUT`, bevor SoC 0 erreicht ist (6S-Presets: nie Brownout über SoC 0,05).

**Kollision (`ContactTest`)**
35. Fall aus 2 m auf Boden, aufrecht, disarmed: erste Rücksprunghöhe = e²·h ±25 %; nach 2 s |ω| < 0,05 rad/s,
    |v| < 0,01 m/s, Drift < 1 cm; Endlage aufrecht.
36. 5 m/s senkrecht in eine Wand (FREESTYLE, impactTolerance 8): kein Crash, `Bump`; 10 m/s → `Crash` + disarmed.
37. Außermittiger Treffer (Vorwärtsflug 4 m/s, Wand trifft nur die rechte vordere Prop-Spitze): danach ω_y < 0
    (Gieren nach rechts — die getroffene rechte Seite wird zurückgedrückt, die linke läuft weiter), |ω| > 2 rad/s.
38. Prop-Strike bei drehenden offenen Props: betroffener Rotor ≤ 30 % Drehzahl, Event, Rahmen-Kick > 5 rad/s;
    bei Duct-Rahmen kein Prop-Strike.
39. Auf 45° gekippt abgesetzt: kippt in < 1,5 s auf die Füße zurück bzw. auf den Rücken (je nach Seite), ohne
    Energiezuwachs (Summe kin.+pot. Energie monoton fallend ±1 %).

**Robustheit und Zeitschritt (`StabilityTest`, `DeterminismTest`, `PerfTest`)**
40. Zufalls-dt 0–50 ms, 60 s Freestyle-Skript mit Crashes: keine NaN/Inf, |ω_i| ≤ 1,05·Leerlaufdrehzahl.
41. Frame-Raten-Unabhängigkeit: gleiches Stick-Skript bei 30, 60, 144 fps und Zufalls-dt → Endposition nach
    5 s innerhalb 5 cm, Lage innerhalb 1°.
42. Determinismus: zweimal gleiches Skript + Seed → bitgleicher `DroneState` nach 10 s.
43. dt = 0 ändert nichts; dt = 0,2 s wird auf 0,05 s geklemmt.
44. Leistung: 10 s Simzeit (20 000 Substeps) nach JIT-Aufwärmen < 40 ms auf dem Entwicklungsrechner; 0 Byte
    Allokation pro Substep (Messung über `ThreadMXBean.getThreadAllocatedBytes`, Toleranz 1 KB für 10 000 Substeps).
45. Werkbank-Robustheit: 500 zufällige baubare Builds mit `PidProfile.suggestFor`: 30 s ANGLE-Schweben ohne
    Divergenz (|ω_b| < 3 rad/s nach 2 s) oder `PerfWarning.CANNOT_HOVER` gesetzt.

---

## 15 Offene Punkte und Erweiterungen

- **Turtle/Crashflip**: Modus bei Rückenlage + Schalter: Rotoren rückwärts (`s_i` invertiert, Schub −ŷ,
  Wirkungsgrad 0,5), Stick wählt die Motorpaare. Passt in Mixer und PropAero ohne Strukturänderung.
- **Lageschätzer** mit Beschleunigungsmesser (ANGLE driftet in langen Kurven wie beim echten FC) — optional.
- **Fremde Wake-Partikel** anderer Drohnen (Multiplayer-„dirty air“) über `WakeField.inject`.
- **Decken-/Wandeffekt** (Ansaugen unter Decken) analog zum Bodeneffekt, wenn das Entity Abstände nach oben
  liefert.
- **Datenmigration**: `FrameLayout`/`PartPhysics`-Werte können als Felder in die Teile-Enums wandern, sobald die
  Werkbank sie anzeigen will.
- **Dynamic Idle / RPM-basiertes Idle** und `D_min` als spätere PID-Optionen.
