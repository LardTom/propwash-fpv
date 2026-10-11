# Flugmodell & Flight Controller — Designdokument

**Projekt:** Propwash FPV (Minecraft 26.3, Fabric + NeoForge, Java 25)
**Paket:** `de.lardtom.propwash.sim` (reines Java, nur `java.*` und `org.joml.*`)
**Stand:** 2026-09-23 · Entwurf „FC-Schwerpunkt“ (einer von drei unabhängigen Entwürfen)
**Status:** Design, noch kein Code. Alle Zahlen in diesem Dokument stammen aus einem nichtlinearen Prototyp genau dieser Gleichungen (Python, außerhalb des Repos). Die Java-Implementierung muss sie innerhalb der Toleranzen in Kapitel 12 reproduzieren.

---

## 0. Ziele, Nicht-Ziele, Kernentscheidungen

**Ziele**

1. Fühlt sich an wie ein echter Quad unter Betaflight: Rate-Loop mit PID (D auf Messung), Feedforward, I-Term-Relax, Anti-Windup, Airmode-Mixer mit Entsättigung, TPA, ANGLE/HORIZON als äußere Schleifen, Motor-Idle, Throttle-Kurve, Rates im BETAFLIGHT- und ACTUAL-Format.
2. Jede Build-Kombination aus der Werkbank fliegt **ohne Tuning** stabil: Default-PIDs werden aus der Physik der Teile abgeleitet (Kapitel 6).
3. Vertraute Zahlen: PID-Werte nutzen die öffentlich dokumentierten Betaflight-Skalen. Beim FREESTYLE-Preset landet die automatische Ableitung bei **P44/I76/D23** (Roll), also nahe an den Betaflight-Defaults. Die BF-Defaults 45/80/30 sind auf dem simulierten 5-Zöller selbst stabil (Phasenreserve 48°).
4. Physik aus den Teilen: Drehzahl, Strom, Spannungseinbruch, Schub und Motorträgheit ergeben sich aus KV, Wicklungswiderstand, Prop-Beiwerten, Akku-Innenwiderstand. Nichts davon wird nachträglich angepasst.
5. Deterministisch, allokationsfrei im Schritt, günstig: 2 kHz feste Substeps, bei 50 ms Frame etwa 100 Substeps und rund 0,1 ms CPU.

**Nicht-Ziele (v1):** Blattelement-Aerodynamik, CFD, RPM-Filter/Dynamic Notch (das simulierte Gyro-Rauschen ist bewusst leicht), Crash-Recovery, GPS-Rescue (das macht der spätere Autopilot über ANGLE-Sticks), Launch Control.

**Originalität:** Das Design ist eigenständig. Genutzt werden nur öffentliches Physik-/Ingenieurwissen (Impulstheorie, DC-Motormodell, Cheeseman–Bennett bzw. das Multirotor-Bodeneffektmodell nach Sanchez-Cuevas et al.) sowie öffentlich dokumentierte Betaflight-Formeln (Rates, Throttle-Kurve, PID-Skalen, Mixer-/Airmode-Konzept). Code aus anderen Mods wird weder gelesen noch nachgebaut.

---

## 1. Konventionen

### 1.1 Einheiten

SI durchgehend. 1 Block = 1 m, g = 9,81 m/s², Luftdichte ρ = 1,225 kg/m³. Drehzahlen intern in rad/s (ω) bzw. U/s (n = ω/2π) für Prop-Beiwerte. Die FC-Seite rechnet wie eine Firmware in **°/s** und **Mixer-Einheiten** (±1 = voller Motorbereich), die Umrechnung passiert genau an der Gyro-Grenze.

### 1.2 Koordinatensysteme

- **Welt:** Minecraft-Koordinaten, rechtshändig, +Y oben. Blickrichtung Süden = +Z.
- **Körper (Body):** rechtshändig, **+X = links**, **+Y = oben (Schubachse)**, **+Z = vorn (Nase)**. Es gilt X = Y × Z. Wer von hinten auf die Drohne schaut, hat +X auf der linken Seite. Das ist dieselbe Händigkeit wie in Minecraft: Wer nach Süden (+Z) schaut, hat Osten (+X) links.
- **Orientierung:** `Quaterniond q` dreht Körper → Welt: `v_welt = q · v_body`.
- **Heading:** `f = q·(0,0,1)`, `ψ = atan2(f.x, f.z)` in rad. ψ = 0 heißt Nase nach +Z. Minecraft: `yRot = −toDegrees(ψ)` (yRot 0 = Süden, −90 = Osten = +X ✓).
- **Euler für Telemetrie/OSD:** `l = q·(1,0,0)`. Roll = `asin(l.y)` (positiv = rechts runter), Pitch = `asin(−f.y)` (positiv = Nase runter).

### 1.3 Pilotenachsen (FC-Konvention) und Mapping

| FC-Achse | positiv bedeutet | Stick | Körper-Winkelgeschwindigkeit |
|---|---|---|---|
| ROLL | rechte Seite runter | Roll rechts | `+ω.z` |
| PITCH | Nase runter | Pitch nach vorn | `+ω.x` |
| YAW | Nase nach rechts (im Uhrzeigersinn von oben) | Yaw rechts | `−ω.y` |

`gyroFc = (deg(ω.z), deg(ω.x), −deg(ω.y))`, umgekehrt `ω_body = rad(pitch, −yaw, roll)`. Diese beiden Umrechnungen liegen **nur** in `FcAxes` und nirgends sonst.

### 1.4 Motorlayout (Quad X, Betaflight-Reihenfolge, Props-In)

Motoren sitzen bei `a = L/√2` in X und Z, `L = frame.armLengthMeters()`, Höhe 0 (Schublinie durch den Schwerpunkt).

| # | Position | r_i (Body) | Drehsinn von oben | s_i | Roll | Pitch | Yaw |
|---|---|---|---|---|---|---|---|
| M1 | hinten rechts | (−a, 0, −a) | CW | −1 | −1 | +1 | −1 |
| M2 | vorn rechts | (−a, 0, +a) | CCW | +1 | −1 | −1 | +1 |
| M3 | hinten links | (+a, 0, −a) | CCW | +1 | +1 | +1 | +1 |
| M4 | vorn links | (+a, 0, +a) | CW | −1 | +1 | −1 | −1 |

`s_i = +1` heißt: der Rotor dreht um +Y (gegen den Uhrzeigersinn von oben). Sein Reaktionsmoment auf den Rahmen ist `−s_i·K_t·I_i·ŷ`. Ein CCW-Rotor, der mehr Gas bekommt, dreht den Rahmen also nach rechts, und darum hat Yaw bei M2 und M3 den Wert +1. `propsOut = true` kehrt alle `s_i` und die Yaw-Spalte um.

Kontrolle der Vorzeichen: `τ = Σ r_i × (0, T_i, 0) = (−Σ z_i T_i, 0, Σ x_i T_i)`. Mehr Schub links (x > 0) ergibt τ_z > 0, also Roll rechts ✓. Mehr Schub hinten (z < 0) ergibt τ_x > 0, also Nase runter ✓.

---

## 2. Paket- und Klassenlayout

Ein flaches Paket `de.lardtom.propwash.sim`, etwa 35 Dateien. Die öffentliche API ist unten **fett** markiert, alles andere bleibt package-private. So bleiben die Interna austauschbar, und die Tests liegen im selben Paket.

```
de.lardtom.propwash.sim
├── **FlightSim**                 Fassade: step(), applyCollision(), telemetry(), reset(), Konfig-Setter
├── **PilotInput**                Sticks roll/pitch/yaw −1..1, throttle 0..1, mode, armSwitch, linkOk, turtleSwitch
├── **StabMode**                ACRO, ANGLE, HORIZON
├── **Environment**               groundDistance, wind, submerged, ambientTempC (vom Entity pro Frame befüllt)
├── **StepResult**                displacement (Welt), orientation, velocity, angularVelocity, substeps (wiederverwendet)
├── **CollisionReport**           hitX/hitY/hitZ, optional impactVelocity, onGround-Hinweis
├── **Telemetry**                 veränderlicher Snapshot (wiederverwendet), siehe Kap. 9
├── **SimEvent**, **SimEventType**, **ArmBlockReason**, **DisarmReason**
├── **BuildPreview**              Kennzahlen für die Werkbank (T/W, Hover, Flugzeit, Topspeed, Warnungen)
├── **StickMath**                 Hilfen für den Autopiloten: angleToStick(), hoverThrottleStick()
│
├── Konfiguration (JSON-serialisierbar, öffentliche Felder, siehe Kap. 10)
│   ├── **FlightControllerConfig** (Wurzel: rates, pid, filters, loop, version)
│   ├── **RateProfile**, **RateCurve**, **RatesType**
│   ├── **PidProfile**, **AxisPid**, **TpaMode**, **ItermRelaxMode**, **ThrottleLimitType**
│   ├── **FilterSettings**, **LoopSettings**, **PidLoopRate**
│   └── **SimSettings**           Gameplay-/Realismus-Schalter (Prop Wash an/aus, unendlicher Akku, …)
│
├── Modell (aus DroneBuild abgeleitet, unveränderlich)
│   ├── **AirframeSpec**           record: alle physikalischen Größen (Kap. 3), derive(DroneBuild)
│   ├── PhysicsConstants          die Ableitungs-Konstanten aus Kap. 3 (static final)
│   ├── BatteryCurve              OCV(soc) je Chemie
│   ├── **OperatingPoints**       stationäre Löser: Hover, Vollgas, Topspeed, Flugzeit
│   └── **PidAutoTuner**          Kap. 6 (LinearPlant, ComplexNum, Margins)
│
├── Physik (Zustand + Substep)
│   ├── RigidBodyState            p, v, q, ω (JOML, veränderlich)
│   ├── MotorBank                 4 Motoren + ESC + Batteriekopplung (Kap. 4.1–4.2)
│   ├── BatteryState              soc, mAh, V_pol, Filter für Warnungen
│   ├── RotorAero                 Schub, Einströmung, H-Kraft, Bodeneffekt, Prop Wash
│   ├── BodyAero                  Rumpfwiderstand
│   ├── ThermalModel              Motor-/ESC-Temperatur (optional, nur Telemetrie)
│   └── CollisionResponse         Impuls-Antwort, Crash-Erkennung, Bodenkontakt
│
├── Flight Controller
│   ├── FlightController          Orchestrierung pro PID-Tick
│   ├── FcAxes                    Achs-Mapping (Kap. 1.3), einzige Stelle mit Vorzeichen
│   ├── RcProcessor               Deadband, Interpolation, Rates, Throttle-Kurve, FF-Ableitung
│   ├── Rates                     statische Formeln BETAFLIGHT/ACTUAL
│   ├── ThrottleCurve             statisch
│   ├── AttitudeController        ANGLE/HORIZON (Tilt-Vektor)
│   ├── RatePid                   ein Objekt pro Achse
│   ├── Mixer                     Quad-X, Airmode-Entsättigung, Idle, Thrust-Linear, Sag-Komp.
│   ├── Arming                    Arm-/Disarm-Logik, Failsafe, Turtle (v2)
│   ├── GyroModel                 Rauschen + Vibration (seeded)
│   └── Pt1Filter, Pt2Filter      Filter mit festem dt
```

Abhängigkeiten: `sim` → `build` (nur `DroneBuild` und die Enums lesen) → nichts. Keine Minecraft-Imports, keine Statics mit Zustand, kein `Math.random`, keine Uhrzeit. Zufall kommt aus einem `java.util.SplittableRandom(seed)` pro `FlightSim`.

---

## 3. Parameterableitung aus dem DroneBuild

`AirframeSpec.derive(DroneBuild build)` ist reine Funktion und günstig (< 0,1 ms). Die Konstanten stehen in `PhysicsConstants` und sind **keine** Spieler-Einstellungen.

### 3.1 Masse und Geometrie

```
m      = build.totalMassGrams() / 1000                          [kg]
L      = frame.armLengthMeters()                                [m]   (Wheelbase/2000)
a      = L / √2                                                 [m]   Motor-Offset in X und Z
D      = prop.diameterMeters(),  R_p = D/2,  A = π R_p²         [m, m²]
pitch  = prop.pitchInch · 0,0254                                [m]
```

### 3.2 Trägheitsmomente (diagonal, Hauptachsen = Körperachsen)

Deviationsmomente werden vernachlässigt (Quad X ist nahezu symmetrisch). Ixx = Pitch-Achse, Iyy = Yaw-Achse, Izz = Roll-Achse.

1. **Motor + Prop als Punktmassen**, `m_mp = (motor.mass + prop.mass)/1000`:
   `Ixx += 4 m_mp a²`, `Izz += 4 m_mp a²`, `Iyy += 8 m_mp a²`
2. **Rahmen**
   - offen: 55 % der Rahmenmasse als 4 Stäbe (Mitte → Motor): `Ixx += m_arm L²/6`, `Izz += m_arm L²/6`, `Iyy += m_arm L²/3`, `m_arm = 0,55 m_frame`
   - Ducts (`frame.ducted`): 60 % als Ringe um die Motorachsen, `R_d = R_p + 2 mm`, `m_d = 0,60 m_frame`:
     `Ixx += m_d (a² + R_d²/2)`, `Izz += m_d (a² + R_d²/2)`, `Iyy += m_d (2a² + R_d²)`
3. **Zentralkörper**: `m_c` = Rest-Rahmen + Stack + Video + Akku, als Quader `k × k × 2k` (X × Y × Z).
   `k = (V_akku/2)^(1/3)`, `V_akku = m_akku / ρ_akku` mit ρ = 2,0 g/cm³ (LiPo/LiHV) bzw. 2,9 g/cm³ (Li-Ion).
   `Ixx += 5 m_c k²/12`, `Iyy += 5 m_c k²/12`, `Izz += 2 m_c k²/12`

### 3.3 Rotor (Prop + Glocke)

```
d_stator = erste zwei Ziffern von motor.statorLabel in mm ("2207" → 22, "0802" → 8, "2306.5" → 23)
r_bell   = d_stator/2 + 1,5 mm
J_r      = 0,25 · m_prop · R_p²  +  0,8 · (0,45 · m_motor) · r_bell²        [kg·m²]
```

(0,25 statt 1/3 beim Prop, weil die Masse zur Nabe hin konzentriert ist. 45 % der Motormasse drehen mit, das ist die Glocke samt Magneten und Welle.)

### 3.4 Motor/ESC (DC-Modell)

```
K_v  = kv · 2π/60                  [rad/(s·V)]
K_t  = K_e = 1/K_v                 [N·m/A = V·s/rad]
R    = motor.resistanceOhm         [Ω]
I_0  = 0,025 · motor.maxCurrent    [A]   Leerlaufstrom (Reibung + Eisenverluste)
```

Die Elektrik (L/R ≈ 0,1 ms) wird als algebraisch behandelt. Das ESC legt `d·V_bus` an (d = Duty = Motor-Output 0..1). Ist es aktiv, bremst es auch (Damped Light, negativer Phasenstrom erlaubt).

### 3.5 Propeller

```
k_T = c_t · ρ · D⁴ · f_duct        [N/(U/s)²]       f_duct = 1,20 bei frame.ducted, sonst 1,0
k_Q = c_p · ρ · D⁵ / 2π            [N·m/(U/s)²]
T   = k_T n²,   Q = k_Q n²,   n = ω/2π
k_Tω = k_T/4π²,  k_Qω = k_Q/4π²    (pro (rad/s)²)
v_p = 1,15 · n · pitch             Steigungsgeschwindigkeit für die Einströmkorrektur
```

### 3.6 Akku

```
N      = battery.cells,  C = battery.capacityMah
R_b    = N · battery.cellResistanceOhm                    [Ω]
I_cont = battery.maxContinuousCurrent()                   [A]
P_q    = 0,35 W/g · video.massGrams + 0,5 W               Grundlast Videosender + FC
OCV    = N · U_zelle(soc, Chemie)  (Tabelle 3.6.1)
```

**3.6.1 Leerlaufspannung pro Zelle.** Die Form f(soc) wird zwischen `emptyCellVoltage` (soc = 0) und `fullCellVoltage` (soc = 1) linear interpoliert: `U = U_leer + f·(U_voll − U_leer)`.

| soc | 0 | 0,05 | 0,10 | 0,20 | 0,30 | 0,40 | 0,50 | 0,60 | 0,70 | 0,80 | 0,90 | 1,0 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| f LiPo/LiHV | 0 | 0,157 | 0,271 | 0,329 | 0,386 | 0,429 | 0,486 | 0,529 | 0,643 | 0,743 | 0,871 | 1 |
| f Li-Ion | 0 | 0,167 | 0,250 | 0,333 | 0,417 | 0,483 | 0,542 | 0,625 | 0,708 | 0,792 | 0,875 | 1 |

Tiefentladung (soc < 0): Von `U_leer` fällt die Spannung linear auf `cutoffCellVoltage` bei soc = −0,05 und dann auf 0,8·cutoff bei soc = −0,10 (Brownout, siehe 4.7).

### 3.7 Widerstand

```
A_z = frame.frontalAreaM2          (von vorn)
A_x = 1,1 · A_z                    (seitlich)
A_y = 2,5 · A_z  (Ducts: 3,5 · A_z) (von oben)
C_d = frame.dragCoefficient
C_H = 0,030                        Rotor-H-Kraft-Beiwert (Rotorwiderstand, Kap. 4.3)
```

### 3.8 Kollision

```
impactTolerance  = frame.impactTolerance                 [m/s]
restitution e = 0,25 (offen) / 0,40 (Ducts)
Reibung μ    = 0,50 (offen) / 0,30 (Ducts)
Hitbox       Breite = 2a + D,  Höhe = 1,1·k + 0,02 m   (für EntityDimensions)
```

### 3.9 Stationäre Betriebspunkte (`OperatingPoints`)

- **Motor stationär bei Duty d und Busspannung V:** Aus `K_t (dV − K_e ω)/R − K_t I_0 = k_Qω ω²` folgt die Quadratik
  `k_Qω ω² + (K_t K_e/R) ω − K_t (dV/R − I_0) = 0`, positive Wurzel.
- **Batteriekopplung:** Bisektion über V in [0, E] mit `V = E − R_b · (4 d · max(I_m,0) + P_q/V)`.
- **Hover:** Bisektion über d, bis `4 k_Tω ω² = m g`. Der Hover-Stick ergibt sich aus `d = idle + (1−idle)·stick`, bei linearer Throttle-Kurve.
- **Topspeed (Werkbank-Vorschau):** Punktmasse mit fester Nase-runter-Neigung θ, alle Motoren bei d = 1. Für jedes θ (0,1°-Raster) wird die Geschwindigkeit V gesucht, bei der die Horizontalkraft null ist. Topspeed ist das größte V, bei dem die Vertikalkraft noch ≥ 0 bleibt. Einströmung, H-Kraft und Rumpfwiderstand wie in Kap. 4.
- **Flugzeit-Schätzung:** 80 % Kapazität / Hover-Strom.

### 3.10 Ergebnisse für die 5 Presets

**Tabelle A — abgeleitete Parameter**

| Größe | Einheit | WHOOP | CINEWHOOP | FREESTYLE | RACER | LONG_RANGE |
|---|---|---|---|---|---|---|
| Masse m | g | 29,6 | 231,7 | 525,2 | 430,2 | 846,2 |
| Armlänge L | mm | 32,5 | 71,0 | 112,5 | 107,5 | 147,5 |
| a = L/√2 | mm | 23,0 | 50,2 | 79,5 | 76,0 | 104,3 |
| Ixx (Pitch) | 10⁻⁶ kg·m² | 8,28 | 337,9 | 1268,7 | 1033,6 | 3069,1 |
| Iyy (Yaw) | 10⁻⁶ kg·m² | 15,50 | 636,0 | 2357,9 | 1942,7 | 5749,1 |
| Izz (Roll) | 10⁻⁶ kg·m² | 7,64 | 314,0 | 1161,0 | 958,9 | 2835,6 |
| J_r | 10⁻⁹ kg·m² | 42,8 | 883,9 | 6398 | 6428 | 17531 |
| k_T (inkl. Duct) | N/(U/s)² | 1,358e−7 | 5,149e−6 | 4,198e−5 | 4,198e−5 | 1,107e−4 |
| k_Q | N·m/(U/s)² | 3,349e−10 | 2,867e−8 | 4,343e−7 | 4,343e−7 | 1,254e−6 |
| K_t = K_e | mN·m/A | 0,434 | 2,076 | 5,457 | 5,134 | 7,346 |
| R | Ω | 0,600 | 0,140 | 0,055 | 0,060 | 0,070 |
| I_0 | A | 0,088 | 0,450 | 1,125 | 1,050 | 1,125 |
| R_b (Pack) | mΩ | 60,0 | 40,0 | 33,0 | 36,0 | 108,0 |
| I_cont | A | 14 | 62 | 156 | 132 | 44 |
| P_q | W | 1,90 | 3,48 | 8,55 | 3,30 | 8,55 |
| v_h (Hover-Induktionsgeschw.) | m/s | 6,27 | 7,15 | 6,29 | 5,70 | 5,83 |
| Leerlaufdrehzahl, voller Akku | U/min | 95 700 | 77 280 | 44 100 | 46 872 | 32 760 |

**Tabelle B — Betriebspunkte (Idle 5,5 %)**

| Größe | WHOOP | CINEWHOOP | FREESTYLE | RACER | LONG_RANGE |
|---|---|---|---|---|---|
| Hover-Duty soc 1,0 / 0,5 | 0,539 / 0,602 | 0,276 / 0,302 | 0,247 / 0,271 | 0,211 / 0,231 | 0,269 / 0,311 |
| Hover-Stick (soc 0,5) | 0,579 | 0,262 | 0,228 | 0,187 | 0,271 |
| Hover-Drehzahl [U/min] | 43 875 | 19 931 | 10 509 | 9 511 | 8 216 |
| Hover-Strom [A] (soc 1,0) | 1,52 | 2,39 | 3,87 | 2,82 | 4,99 |
| Schwebezeit 80 % [min] | 9,4 | 13,1 | 16,1 | 18,7 | 38,4 |
| Vollgas statisch [U/min] | 72 590 | 58 498 | 34 897 | 35 332 | 19 809 |
| Busspannung Vollgas [V] | 4,03 | 14,62 | 21,48 | 20,82 | 16,62 |
| Busstrom Vollgas [A] | 5,3 | 54,6 | 112,6 | 121,7 | 79,5 |
| Max. Schub [N] | 0,79 | 19,58 | 56,81 | 58,23 | 48,25 |
| T/W soc 1,0 / 0,5 | 2,74 / 2,29 | 8,61 / 7,44 | 11,03 / 9,48 | 13,80 / 11,92 | 5,81 / 4,71 |
| Topspeed horizontal [m/s] (km/h) | 10,9 (39) | 21,0 (76) | 42,8 (154) | 49,3 (177) | 27,7 (100) |
| … bei Neigung | 42,3° | 60,3° | 67,8° | 71,4° | 54,3° |
| Fallgeschw. flach [m/s] | 5,9 | 7,3 | 17,7 | 20,2 | 19,4 |
| Motor-Zeitkonstante τ_m (Hover) | 109,2 ms | 26,1 ms | 11,3 ms | 13,9 ms | 21,2 ms |
| b_roll [rad/s² pro Mixer-Einheit] | 2527 | 2201 | 2436 | 2701 | 1814 |
| b_pitch | 2331 | 2046 | 2229 | 2505 | 1676 |
| b_yaw,eff (bei 20 rad/s) | 614 | 632 | 818 | 1040 | 581 |
| Bodeneffekt g bei z = D/2 / z = D | 1,098 / 1,038 | 1,107 / 1,042 | 1,114 / 1,044 | 1,118 / 1,045 | 1,118 / 1,045 |
| Hitbox B × H [m] | 0,077 × 0,034 | 0,176 × 0,050 | 0,289 × 0,061 | 0,282 × 0,059 | 0,387 × 0,066 |
| impactTolerance [m/s] | 14 | 11 | 8 | 7 | 8 |

Plausibilität: Topspeeds, T/W, Vollgasströme und Drehzahlen liegen im Bereich realer Quads der jeweiligen Klasse. Die LONG_RANGE-Li-Ion-Zelle fällt bei Vollgas unter Last auf rund 2,5 V/Zelle. Das ist gewollt: Li-Ion-Packs brechen bei Punch-outs real stark ein, und die Warnung BATTERY_CRITICAL feuert.

### 3.11 Kalibrierhinweise an die Teile-Daten (nicht blockierend)

- **WHOOP: τ_m = 109 ms** kommt fast vollständig aus `M0802_22000.resistanceOhm = 0,60 Ω`, denn `τ ≈ J_r·R/K_t²`. Damit fühlt sich der Whoop träge an (ACRO-Anstieg ≈ 110 ms). Mit 0,35 Ω wären es τ ≈ 69 ms, T/W 3,1 und Roll-P 26. Mit 0,30 Ω wären es τ ≈ 61 ms. Empfehlung: 0,30–0,35 Ω.
- **Flugzeiten** (Tabelle B) sind eher optimistisch, besonders bei WHOOP und CINEWHOOP. Stellschraube ist `PropType.powerCoefficient` der kleinen Props: c_p 0,06 → 0,09 bei P31_4B ergibt 7,5 statt 9,9 min. Das Flugmodell bleibt davon unberührt.

---

## 4. Physik pro Substep (h = 1/2000 s)

Zustand: `p, v` (Welt), `q`, `ω_b` (Body), `ω_i` (4 Rotoren), `soc, mAh, V_pol, I_bus,prev, V_prev`, Filterzustände, Temperaturen.

### 4.1 Busspannung (geschlossene Lösung, stabil)

Explizite Kopplung über den Strom des Vorschritts ist bei 5-Zöllern **instabil**, denn die Schleifenverstärkung `Σ d²·R_b/R ≈ 2,4 > 1`. Deshalb wird V pro Substep direkt gelöst:

```
E     = N · U_zelle(soc) − V_pol
R_eff = R_b · (1 + 0,5 · max(0, I_bus,prev / I_cont − 1))          Überlast erhöht den Innenwiderstand
I_q   = P_q / V_prev
M     = { i | ESC aktiv und d_i·V_prev − K_e ω_i > 0 }              Motoren, die Strom ziehen
V     = (E − R_eff·I_q + (R_eff/R)·Σ_M d_i K_e ω_i) / (1 + (R_eff/R)·Σ_M d_i²)
M neu bestimmen und V einmal nachrechnen, falls sich M geändert hat;  V = max(V, 0)
```

### 4.2 Motor (semi-implizit, unbedingt stabil)

```
ESC aktiv:
  ω_i⁺ = [ ω_i + (h/J_r)·( K_t·(d_i V/R − I_0·[ω_i>0]) − k_Qω ω_i² ) ] / (1 + h·K_t K_e/(R J_r))
  I_i  = (d_i V − K_e ω_i⁺)/R                           Phasenstrom (negativ = aktive Bremse)
ESC aus (disarmed/Brownout):
  ω_i⁺ = ω_i + (h/J_r)·(−K_t I_0 − k_Qω ω_i²),   I_i := I_0 (Lagerreibung wirkt auf den Rahmen)
ω_i⁺ = max(ω_i⁺, 0)   (Rückwärtslauf nur im Turtle-Modus, v2)
I_bus = Σ d_i·max(I_i, 0) + I_q
```

### 4.3 Rotor-Aerodynamik

Pro Rotor i, mit Luftgeschwindigkeit im Body `u = q⁻¹·(v − wind)`:

```
v_i   = u + ω_b × r_i                                  lokale Anströmung am Rotor
v_ax  = v_i.y                                          > 0: Steigen/Einströmung von oben
v_⊥   = (v_i.x, 0, v_i.z)
n_i   = ω_i/2π
φ_i   = clamp(1 − v_ax/(1,15·n_i·pitch), 0, 1,2)       (n_i < 1 U/s ⇒ φ_i = 1)
T_i   = k_Tω ω_i² · φ_i · g_i · (1 − 0,25 w_i) · (1 + 0,35 w_i ξ_i)
H_i   = −C_H · ρ · n_i · D³ · v_⊥                       Rotorwiderstand (H-Kraft), linear in v
```

Die Einströmkorrektur φ liefert automatisch Schubverlust beim Steigen und bei hoher Vorwärtsfahrt. Über `ω × r` dämpft sie außerdem Roll/Pitch (ein Rotor, der nach oben schwingt, verliert Schub). Einen zusätzlichen Drehdämpfungsterm gibt es deshalb nicht.

**Bodeneffekt** g_i (nur wenn `groundDistance` endlich und `(q·ŷ).y > 0,5`). Öffentliches Multirotor-Modell nach Sanchez-Cuevas et al. 2017, ohne Rumpfterm:

```
z   = max(groundDistance + (q·r_i).y, R_p/2)     Rotorhöhe über Grund
d   = 2a                                          Abstand benachbarter Rotoren
g_i = min(1,3, 1 / (1 − (R_p/4z)² − R_p²·z/√((d²+4z²)³) − (R_p²·z/2)/√((2d²+4z²)³)))
```

**Prop Wash / Wirbelring** w_i ∈ [0,1]:

```
v_h,i = √(max(T_i,prev, 1e−4) / (2ρA))             Induktionsgeschwindigkeit
r_i   = −v_ax / v_h,i                                Sinkverhältnis
μ_i   = |v_⊥| / v_h,i                                Vorwärtsverhältnis (bläst den Wash weg)
w_i   = propWashStrength · clamp01(1 − ((r_i − 0,9)/0,6)²) · exp(−(μ_i/0,8)²)
ξ_i   : PT1(15 Hz)-gefiltertes Gauß-Rauschen mit Einheitsvarianz:
        ξ ← ξ + k·(√((2−k)/k)·N(0,1) − ξ),   k = h/(h + 1/(2π·15))
```

Die zufälligen Schubschwankungen der einzelnen Rotoren erzeugen über die Hebelarme Roll-/Pitch-Störmomente. Der PID muss dagegen arbeiten, und genau das ist das bekannte Prop-Wash-Zittern beim Sinken in die eigene Luft. Telemetrie: `propWash = max_i w_i`. Zusätzlich skaliert w die Gyro-Vibration (Kap. 5.2).

### 4.4 Kräfte und Momente

```
F_b = Σ (H_i + T_i ŷ)  −  ½ ρ C_d · (A_x|u_x|u_x, A_y|u_y|u_y, A_z|u_z|u_z)
τ_b = Σ r_i × (H_i + T_i ŷ)                                      Schub/H-Kraft
    − Σ s_i K_t I_i ŷ                                            Motor-Reaktionsmoment (Yaw)
    − ω_b × (Σ s_i J_r ω_i ŷ)                                    Kreiselmoment der Rotoren
```

Das Yaw-Moment über `K_t·I_i` enthält sowohl das Prop-Widerstandsmoment als auch den Beschleunigungsanteil `J_r·dω/dt`. Deshalb reagiert Yaw anfangs mit einem kurzen Stoß, wie real.

### 4.5 Integration (semi-implizites Euler, Exponential-Map)

```
a_w   = q·(F_b/m) + (0, −g, 0)
v⁺    = v + h·a_w
Δp   += h·v⁺                            (Displacement des Frames, Kap. 7)
ω̇     = I⁻¹ (τ_b − ω_b × I ω_b)         (I diagonal)
ω_b⁺  = ω_b + h·ω̇
q⁺    = normalize(q ⊗ exp(½ ω_b⁺ h))    exakte Rotation um ω_b⁺, jeden Schritt normalisiert
f_b   = F_b/m                           Beschleunigungsmesser (spezifische Kraft)
gLoad = |f_b|/g ,  gLoadZ = f_b.y/g
```

### 4.6 Akku-Zustand

```
mAh  += I_bus · h / 3,6
soc   = 1 − mAh / C
V_pol += h·(0,6·R_b·I_bus − V_pol)/8 s       Polarisation: Sag erholt sich langsam nach dem Punch
U_last = V/N, gefiltert PT1 1 Hz → Warnungen:
   U_last < chemistry.emptyCellVoltage  (≥ 2 s)   → BATTERY_LOW (je Unterschreitung; Rückweg mit 0,1 V Hysterese, hält bei leerem Akku)
   U_last < chemistry.cutoffCellVoltage (≥ 2 s)   → BATTERY_CRITICAL (wie oben, Stand 0.4.0: flight-model-spec §5.10)
   E/N  < 0,8 · cutoff                            → BATTERY_DEAD: ESC aus, Disarm (Brownout)
SimSettings.infiniteBattery: soc bleibt 1, mAh zählt trotzdem (Telemetrie); keine Akkuwarnungen
```

### 4.7 Temperatur (optional, nur Telemetrie in v1)

```
Motor:  C_m dT/dt = I_i² R + K_t I_0 ω_i − (T − T_amb)·G_m
        C_m = 0,5 J/(g·K) · m_motor,   G_m = 0,004 W/(g·K) · m_motor · (1 + 4 ω_i/ω_nl)
ESC:    gleiche Form, P = I_i² · 0,004 Ω,  C = 0,3 J/(g·K)·m_stack/4,  G = 0,002·m_stack/4·(1 + 3 ω_i/ω_nl)
```

(FREESTYLE: Hover → ≈ 50 °C Motor, Dauervollgas → > 100 °C. Nur Anzeige, keine Leistungsminderung.)

### 4.8 Sonderfälle

- `environment.submerged` → Schub × 0, Widerstand × 20, Disarm `SUBMERGED`.
- `ω_i`-Obergrenze: `1,2 · ω_nl`, als NaN-/Überlaufschutz.
- NaN-Wächter: Ist nach einem Substep ein Zustandswert nicht endlich, setzt die Sim auf den letzten gültigen Zustand zurück, disarmt (`DisarmReason.SIM_FAULT`) und loggt einmal.

---

## 5. Flight Controller

### 5.1 Timing

- Physik: fest **2000 Hz** (h = 0,5 ms).
- PID-Loop: `LoopSettings.pidLoop` ∈ {HZ_2000 (Default), HZ_1000, HZ_500}. Der FC läuft auf jedem 1./2./4. Substep, die Motor-Outputs werden dazwischen gehalten (Zero-Order-Hold).
- `dtPid = divider · h`. Alle FC-Filter werden mit dtPid initialisiert.
- Die FC-Latenz wird im Auto-Tuner als `T_d = 1/f_pid + 0,5/f_phys` modelliert.

### 5.2 Gyro-Modell

```
gyroRaw = gyroFc(ω_b)                                  (°/s, Kap. 1.3)
        + σ_white · N(0,1)                              σ_white = filters.gyroNoiseDps (0,25)
        + σ_vib · (ω̄/ω_nl)² · (1 + 3·propWash) · ν     σ_vib = filters.gyroVibrationDps (2,0)
ν       : PT1(180 Hz)-gefiltertes Einheitsrauschen (breitbandige Rahmenvibration unterhalb Nyquist)
gyro    = PT1(filters.gyroLpfHz = 250)(gyroRaw)         (0 = aus)
```

Ein Beschleunigungsmesser wird nicht simuliert. Für ANGLE/HORIZON nutzt der FC die **wahre Lage q** (perfekter Schätzer), denn Drift-Artefakte machen im Spiel keinen Spaß.

### 5.3 RC-Pipeline

1. **Deadband** (roll/pitch `rcDeadband`, yaw `yawDeadband`): `x' = sign(x)·max(0, |x|−db)/(1−db)`.
2. **Interpolation (RC Smoothing):** Sticks kommen einmal pro gerendertem Frame. Innerhalb eines Frames mit n Substeps wird linear von den Sticks des Vorframes zu den aktuellen interpoliert: `s(k) = lerp(s_prev, s_cur, (k+1)/n)`. Der Setpoint ist damit stetig, und die FF-Ableitung bleibt pro Frame konstant und endlich.
3. **Rates → Setpoint** (°/s) je Achse, siehe 5.4. Gekappt bei ±`rateLimitDps` (1998).
4. **Throttle-Kurve → Limit** (5.4.3). Heraus kommt `thr ∈ [0,1]`, das in den Mixer geht.
5. **FF-Ableitung:** `spRate = (sp_rc − sp_rc,prev)/dtPid`, geglättet mit PT1(`ffSmoothingHz` = 40).

### 5.4 Formeln (öffentlich dokumentiert, eigenständig implementiert)

#### 5.4.1 BETAFLIGHT-Rates (`rcRate`, `superRate`, `rcExpo`)

```
a = |x|                                   (vor dem Expo!)
x = x·a³·expo + x·(1 − expo)              falls expo > 0
r = rcRate;  falls r > 2:  r = r + 14,54·(r − 2)
rate = 200 · r · x
rate = rate / clamp(1 − a·superRate, 0,01, 1)      falls superRate > 0
```

#### 5.4.2 ACTUAL-Rates (`centerRate`, `maxRate`, `actualExpo`)

```
a     = |x|
e     = a · (x⁵·expo + x·(1 − expo))
rate  = x·centerRate + max(0, maxRate − centerRate)·e
```

Referenzwerte (Tests A1):

| x | BF 1,0/0,70/0 | BF 1,0/0,70/0,5 | ACTUAL 70/670/0 | ACTUAL 70/670/0,5 |
|---|---|---|---|---|
| 0,25 | 60,61 | 30,78 | 55,00 | 36,32 |
| 0,50 | 153,85 | 86,54 | 185,00 | 114,69 |
| 1,00 | 666,67 | 666,67 | 670,00 | 670,00 |

Außerdem: BF rcRate 2,5, super 0, x = 0,1 → 195,4 °/s.

#### 5.4.3 Throttle-Kurve (`throttleMid` m, `throttleExpo` e, Limit)

```
Δ = t − m;   y = (Δ > 0) ? 1 − m : (Δ < 0 ? m : 1)
t' = m + Δ·(1 − e + e·Δ²/y²)
Limit: OFF → t';  SCALE → t'·limit;  CLIP → min(t', limit)
```

Referenz: m = 0,4, e = 0,5: t = 0,7 → 0,5875 und t = 0,2 → 0,275. m = 0,5, e = 0 → Identität.

### 5.5 Flugmodi

**ACRO:** Setpoint = Rates(Stick) auf allen drei Achsen.

**ANGLE (Tilt-Vektor, robust für den Autopiloten):**

```
Zielwinkel: φ_t = rollStick · angleLimit,  θ_t = pitchStick · angleLimit        (linear, KEIN Expo)
Heading-Rahmen: ψ aus q (Kap. 1.2)
u_t,H = (−sin φ_t,  cos φ_t cos θ_t,  cos φ_t sin θ_t)          Ziel-„oben“ im Heading-Rahmen
u_t   = R_y(ψ) · u_t,H ,   u = q·ŷ                              (Welt)
c = u × u_t;   α = atan2(|c|, u·u_t);   e_w = c/|c|·α   (|c| < 1e−9 ⇒ 0)
e_b = q⁻¹ · e_w                                                  (e_b.y ≡ 0)
ω_cmd = K_a · e_b                          K_a = angleStrength/10  [1/s]
      + angleFeedforward · (dθ_t/dt, 0, dφ_t/dt)   (PT1 30 Hz geglättet, Body x = Pitch, z = Roll)
      + q⁻¹ · (0, −rad(yawRate(yawStick)), 0)      Yaw erdbezogen (Rates wie ACRO)
|ω_cmd.x|, |ω_cmd.z| ≤ angleMaxRateDps (500)
Setpoints = gyroFc-Mapping von ω_cmd
```

Vorteile gegenüber Euler-Differenzen: kein Gimbal-Lock, korrekt auch in Rückenlage (kürzester Weg zurück), und Yaw bei Schräglage bleibt erdbezogen. Das RC-FF des Rate-PID ist in ANGLE auf Roll/Pitch **aus**; es ersetzt den Winkel-FF. Yaw-FF bleibt an.

Der Autopilot fährt ANGLE mit `stick = StickMath.angleToStick(zielWinkelDeg, angleLimitDeg)` (linear, geklemmt). Mit `angleFeedforward = 1,0` folgt FREESTYLE einer ±20°-/0,5-Hz-Sinusvorgabe mit 0,07° RMS Fehler, ohne FF sind es 4,2° (Kap. 12).

**HORIZON** (Selbstnivellierung, die mit dem Stick ausblendet):

```
sp_acro = Rates(Sticks)                             (wie ACRO, inkl. RC-FF)
w_stick = clamp01(1 − max(|rollStick|, |pitchStick|) / horizonStickTransition)   (0,75)
w_att   = clamp01(1 − tiltDeg / horizonLimitDeg)                                   (135°)
e_b     = Tilt-Fehler wie ANGLE mit Ziel „level“ (φ_t = θ_t = 0)
sp      = sp_acro + w_stick·w_att·(horizonStrength/10)·e_b   (nur Roll/Pitch)
```

Kleine Stickausschläge ergeben eine stabile Schräglage proportional zum Stick. Voller Stick ergibt reines ACRO (Flips/Rolls möglich), und in Rückenlage zieht HORIZON nicht zurück.

### 5.6 Rate-PID je Achse (Betaflight-Struktur, BF-Skalen)

Skalen (öffentlich dokumentiert, damit Zahlen vertraut wirken): `Kp = P·0,032029`, `Ki = I·0,244381`, `Kd = D·0,000529`, `Kf = F·0,013754/100`. Fehler in °/s, Summe in „PID-Einheiten“, Mixer-Eingang = Summe/1000.

```
g      = gyro[a]                                                (°/s, gefiltert)
err    = sp[a] − g

// I-Term-Relax (Typ SETPOINT, Achsen itermRelax ∈ {OFF, RP, RPY})
spLp   = PT1(itermRelaxCutoffHz = 15)(sp[a]);   spHp = |sp[a] − spLp|
relax  = aktiv ? max(0, 1 − spHp/itermRelaxThresholdDps(40)) : 1

// Anti-Gravity (nur Roll/Pitch)
agSig  = PT1(10 Hz)(|dthr/dt|)                                   (1/s)
agMul  = antiGravity ? 1 + (antiGravityGain/10)·clamp01(agSig/5) : 1

// I mit bedingter Integration (Anti-Windup)
dI     = Ki·agMul·err·relax·dtPid
wenn nicht (mixerSaturiert und sign(dI) == sign(I[a])):  I[a] = clamp(I[a] + dI, ±iTermLimit(400))
wenn !iTermAktiv:  I[a] = 0                                     (siehe 5.8)

// TPA (tpaMode D oder PD)
tpa    = 1 − tpaRate·clamp01((thr − tpaBreakpoint)/(1 − tpaBreakpoint))
P      = Kp·err·(tpaMode == PD ? tpa : 1)

// D auf Messung (kein Setpoint-Kick)
dg     = PT1(dtermLpf2Hz = 200)(PT1(dtermLpf1Hz = 100)(g))
D      = −Kd·(dg − dg_prev)/dtPid · tpa

// Feedforward (nur RC-Anteil, siehe 5.5)
F      = Kf·spRate_gefiltert
         Dämpfung an der Rate-Grenze: gleiche Richtung wie sp und |sp| > ffMaxRateLimit·maxRate
         ⇒ F ·= clamp01((maxRate − |sp|)/((1 − ffMaxRateLimit)·maxRate))

sum    = clamp(P + I[a] + D + F, ±(a == YAW ? pidSumLimitYaw(400) : pidSumLimit(500)))
pid[a] = sum / 1000
```

`mixerSaturiert` stammt aus dem Mixer des vorherigen PID-Ticks (5.7).

### 5.7 Mixer

```
mix_i = pid_roll·M_roll,i + pid_pitch·M_pitch,i + pid_yaw·M_yaw,i        (Tabelle 1.4)
range = max(mix) − min(mix)
sat   = false
wenn range > 1:  mix /= range;  sat = true                  (Entsättigung: Verhältnisse bleiben)
wenn airmodeAktiv:
    thr = clamp(thr, −min(mix), 1 − max(mix))              (immer lösbar, da range ≤ 1)
sonst:
    wenn thr > 0,5: thr = min(thr, 1 − max(mix))
    (unten wird nicht verschoben: bei Nullgas fehlt die halbe Autorität, klassisches Non-Airmode-Gefühl)
o_i   = thr + mix_i;   wenn o_i ∉ [0,1]: sat = true;   o_i = clamp01(o_i)
o_i   = thrustLinear > 0 ? TL(o_i) : o_i
        TL(x) = √(x/tl + B²) − B,   B = (1 − tl)/(2 tl)      (löst tl·y² + (1−tl)·y = x)
o_i  *= sagComp,   sagComp = 1 + vbatSagCompensation·(N·U_voll/V_filt − 1), geklemmt [1, 1,5]
d_i   = idle + (1 − idle)·motorOutputLimit·clamp01(o_i)     (armed)
d_i   = 0                                                    (disarmed: ESC aus)
```

Referenzfälle (Tests A3), Idle 0,055:

| Fall | Eingang | Ergebnis d_1..d_4 |
|---|---|---|
| A: Airmode, thr 0, Roll +0,2 | mix (−0,2, −0,2, 0,2, 0,2) | thr → 0,2; d = (0,055, 0,055, 0,433, 0,433) |
| B: kein Airmode, thr 0, Roll +0,2 | gleich | thr bleibt 0; d = (0,055, 0,055, 0,244, 0,244) |
| C: Airmode, R 0,5, P 0,4, Y 0,2 | mix (−0,3, −0,7, 1,1, −0,1), range 1,8 | mix/1,8; thr → 0,3889; o = (0,2222, 0, 1, 0,3333); sat = true |

### 5.8 Arming, Disarm, Failsafe, iTerm-Freigabe

**Armen** geschieht auf der **steigenden Flanke** von `armSwitch`, wenn keine Sperre vorliegt. Die Sperrgründe (`EnumSet<ArmBlockReason>`) werden **jeden Frame** neu ausgewertet und in der Telemetrie gezeigt, wie die Arming-Disable-Flags im BF-OSD:

| ArmBlockReason | Bedingung |
|---|---|
| THROTTLE_NOT_LOW | throttle-Stick > `armThrottleMax` (0,05) |
| TILTED | Neigung > `armMaxTiltDeg` (25°), außer Turtle-Schalter aktiv |
| PACK_DEPLETED | soc ≤ 0 **oder** Ruhespannung/Zelle ≤ cutoff |
| BUILD_INVALID | `!build.buildable()` |
| ARM_SWITCH_NOT_RESET | Schalter war beim Spawn, nach einem Crash oder bei einer anderen Sperre schon an; erst aus-/einschalten |
| CRASH_COOLDOWN | < 1,0 s seit Crash |
| FAILSAFE | `linkOk == false` |
| SUBMERGED | im Wasser |

**Disarm-Gründe:** SWITCH, CRASH, FAILSAFE, BATTERY_DEAD, SUBMERGED, SIM_FAULT, RESET.

**iTerm und Airmode-Freigabe:** Nach dem Armen ist `airmodeAktiv = false`. Es rastet ein, sobald `thr ≥ airmodeStartThrottle` (0,25). Das verhindert Hüpfen und Windup am Boden. `iTermAktiv = armed && (airmode ? airmodeAktiv : thr > 0,05)`. Beim Disarm werden I, alle Filter und Latches zurückgesetzt.

**Failsafe:** Ist `linkOk` länger als 0,3 s false, folgt Stufe 1: ANGLE, Sticks mittig, throttle = Hover-Stick − 0,05 (langsames Sinken), für 1,5 s. Danach Stufe 2: Disarm (FAILSAFE). Kommt der Link zurück, erhält der Pilot die Kontrolle erst, wenn sein Throttle-Stick den Failsafe-Wert ±0,1 kreuzt oder der Arm-Schalter neu gesetzt wird.

**Turtle / Crash-Flip (v2, Schnittstelle schon vorsehen):** `turtleSwitch` + Armen in Rückenlage (TILTED entfällt). Die Stickrichtung wählt zwei Motoren, die **rückwärts** mit |Stick|·0,5 Duty laufen. Rückwärtsschub = 0,5·k_T·n².

---

## 6. Automatische Default-PIDs aus der Physik

### 6.1 Linearisierte Strecke (um Hover, soc = 0,5, Idle 5,5 %)

```
B      = K_t K_e/R + 2 k_Qω ω_h                    elektrische + aerodynamische Dämpfung
τ_m    = J_r / B                                   Motor-Zeitkonstante
dω/dd  = (K_t V_h/R) / B                           stationäre Drehzahl pro Duty
dT/dω  = 2 k_Tω ω_h ,   dQ/dω = 2 k_Qω ω_h
b_roll  = 4a·(dT/dω)·(dω/dd)·(1−idle) / Izz        [rad/s² pro Mixer-Einheit]
b_pitch = 4a·(dT/dω)·(dω/dd)·(1−idle) / Ixx
k_y     = 4·(dω/dd)·(1−idle) / Iyy

Roll/Pitch:  G(s) = b / (s·(1 + τ_m s))
Yaw:         G(s) = k_y·(dQ/dω + J_r s) / (s·(1 + τ_m s))     Nullstelle durch J_r·dω/dt-Reaktion
Regler:      C(s) = (Kp + Ki/s)·F_g(s) + Kd·s·F_g(s)·F_d1(s)·F_d2(s),   F = PT1 der Filter
Totzeit:     e^(−s·T_d),  T_d = 1/f_pid + 0,5/f_phys
L(s)       = G(s)·C(s)·e^(−s T_d)                    (geöffnet am Streckeneingang)
```

SI ↔ BF: `Kp_SI = P·0,032029·(180/π)/1000` (Mixer pro rad/s), entsprechend I, D und F (`F·0,013754/100·(180/π)/1000`).

### 6.2 Algorithmus (`PidAutoTuner.derive(AirframeSpec, FilterSettings, LoopSettings)`)

1. **Roll/Pitch (Polvorgabe plus Reserven-Suche):**
   - Start: ω_n = 3/τ_m.
   - Für ζ ∈ {0,60; 0,65; …; 1,50}: `Kp = ω_n² τ_m / b`, `Kd = max(0, (2ζ ω_n τ_m − 1)/b)`, `Ki = Kp·ω_i` mit `ω_i = clamp(0,1·ω_n, 6, 25)` rad/s.
   - Frequenzgang auf 800 log-verteilten Punkten zwischen 2 und 16 000 rad/s, Phase entrollt. Bestimmt werden ω_c (letzter Abwärts-Durchgang durch |L| = 1), Phasenreserve PM = 180° + ∠L(ω_c) und Amplitudenreserve GM beim ersten ∠L ≤ −180° oberhalb ω_c.
   - Unter allen ζ mit **PM ≥ 50° und GM ≥ 8 dB** wird das mit der größten PM genommen. Gibt es keins: ω_n ·= 0,97 und wiederholen.
   - `Kf = 1,5/b` (FF-Überdeckung γ = 1,5, gleicht die Motorlatenz teilweise aus).
2. **Yaw:** Kd = 0, `Ki = 12·Kp`. Kp wird geometrisch (×1,03) erhöht, solange PM ≥ 55°, GM ≥ 8 dB **und** ω_c ≤ 0,6·ω_c,roll gelten. `Kf = 1,0/b_y,eff` mit `b_y,eff = 20·|G_yaw(j·20)|`.
3. **ANGLE:** `angleStrength = 10·clamp(ω_n/12, 4, 10)` (äußere Schleife mindestens Faktor 12 langsamer als die innere).
4. **TPA:** `tpaBreakpoint = max(0,35, hoverStick + 0,15)`, damit TPA nie im Schwebeflug greift. `tpaRate = 0,65`, Modus D.
5. Runden auf ganze BF-Werte, klemmen: P ∈ [5, 200], I ∈ [5, 250], D ∈ [0, 150], F ∈ [0, 500].
6. Aufwand: ≈ 800 × 19 × (≤ 40) komplexe Auswertungen, < 20 ms in Java, deterministisch, pro Build-Key + Filter + Loop gecacht.

### 6.3 Ergebnisse (PID-Loop 2 kHz, Filter-Defaults)

| | WHOOP | CINEWHOOP | FREESTYLE | RACER | LONG_RANGE |
|---|---|---|---|---|---|
| Roll P/I/D/F | 18 / 14 / 89 / 75 | 59 / 74 / 52 / 86 | **44 / 76 / 23 / 78** | 41 / 65 / 25 / 70 | 65 / 86 / 52 / 105 |
| Pitch P/I/D/F | 19 / 15 / 96 / 82 | 64 / 80 / 56 / 93 | 48 / 83 / 25 / 85 | 44 / 70 / 27 / 76 | 70 / 93 / 56 / 114 |
| Yaw P/I/D/F | 30 / 47 / 0 / 207 | 39 / 61 / 0 / 201 | 20 / 32 / 0 / 155 | 17 / 27 / 0 / 122 | 38 / 59 / 0 / 218 |
| ω_n / ζ (Roll) | 27 / 1,30 | 96 / 0,90 | 132 / 0,90 | 121 / 0,90 | 101 / 0,90 |
| Roll: ω_c, PM, GM | 63 rad/s, 74°, 19,5 dB | 152, 50°, 11,6 dB | 176, 51°, 10,5 dB | 170, 51°, 10,8 dB | 155, 51°, 11,6 dB |
| Yaw: ω_c, PM, GM | 37, 80°, 32,5 dB | 88, 96°, 24,3 dB | 103, 114°, 21,7 dB | 100, 109°, 22,4 dB | 91, 101°, 23,9 dB |
| angleStrength | 40 | 80 | 100 | 100 | 84 |
| tpaBreakpoint | 0,73 | 0,41 | 0,38 | 0,35 | 0,42 |
| Roll bei 500-Hz-Loop | 18 / 14 / 81 / 75 | 36 / 36 / 38 / 86 | 30 / 44 / 18 / 78 | 27 / 34 / 19 / 70 | 43 / 45 / 39 / 105 |

**Anker:** FREESTYLE ≈ BF-Defaults (45/80/40 bzw. 47/84/46). Die BF-Defaults 45/80/30 haben auf FREESTYLE PM 48° und GM 8,9 dB, sind also stabil. Das bestätigt, dass Physik und Skalierung zusammenpassen.
**Whoop:** Die träge Motor-Zeitkonstante (3.11) erzwingt D > P. Das ist physikalisch korrekt, sieht im Configurator aber ungewohnt aus und verschwindet mit R ≈ 0,3 Ω.
**Neu ableiten**, wenn sich Build (Werkbank), Filter oder Loop-Rate ändern und `pidProfile.source == DERIVED`. Hat der Spieler selbst getunt (`CUSTOM`), bleibt sein Profil, und der Configurator bietet „Auf abgeleitete Werte zurücksetzen“ an.

---

## 7. Integrationsschritt pro Frame

```java
StepResult step(double frameDt, PilotInput in, Environment env)
```

```
dt = clamp(frameDt, 0, settings.maxFrameDt = 0,05)       längere Hänger werden verworfen (kein Aufholen)
arming.onFrame(in, state)                                 Flanken, Sperrgründe, Failsafe-Timer, Events
rc.beginFrame(in)                                         s_prev ← s_cur; s_cur ← deadband(in)
acc += dt;  n = floor(acc / h);  acc −= n·h               Restzeit wird mitgenommen (≤ 0,5 ms Lag)
disp = 0
for k in 0 until n:
    s = lerp(s_prev, s_cur, (k+1)/n)                      RC-Interpolation
    if (tick++ % divider == 0) d[] = fc.update(s, dtPid)  Rates/Modi/PID/Mixer (Kap. 5)
    physics.substep(h, d[], env)                          Kap. 4.1–4.6, disp += h·v⁺
    battery/thermal/propwash-Events sammeln
telemetry.update(...)
result.displacement = disp;  orientation = q;  velocity = v;  substeps = n
return result                                             (dasselbe Objekt jeden Frame, keine Allokation)
```

- `n = 0` (sehr kleines dt): kein Physikschritt, die Sticks werden trotzdem übernommen.
- **Frameraten-Unabhängigkeit:** Physik und FC sehen immer h = 0,5 ms. Nur der Verlauf der Stick-Interpolation hängt von den Frame-Grenzen ab.
- **Determinismus:** gleicher Seed, gleiche Eingabefolge und gleiche dt-Folge ergeben bitgleiche Zustände.
- **Threading:** Eine `FlightSim` gehört einem Thread (Render-Thread des Piloten-Clients). Die Telemetrie ist ein Snapshot für denselben Thread. Für andere Threads (HUD-Sound o. Ä.) gibt es `telemetry().copyTo(Telemetry)`.
- **Position:** Die Sim führt eine interne Position (Summe der Displacements) nur für Tests und `reset()`. Autoritativ ist die Entity-Position. `groundDistance` liefert das Entity per Raycast.

---

## 8. Kollisionen

### 8.1 Protokoll

1. `StepResult r = sim.step(dt, input, env)`
2. Entity: `move(MoverType.SELF, r.displacement)` (Minecraft löst die Kollision achsweise auf)
3. Entity vergleicht gewünschtes und tatsächliches Displacement je Achse → `CollisionReport { hitX, hitY, hitZ, impactVelocity? }` → `sim.applyCollision(report)`

Die Auftreffgeschwindigkeit nimmt die Sim aus ihrer eigenen Geschwindigkeit am Frame-Ende. Ein `impactVelocity` im Report, falls vorhanden, hat Vorrang.

### 8.2 Antwort pro getroffener Achse

```
n        = −sign(v_achse) · ê_achse                        Wandnormale (zeigt von der Wand weg)
Kontakt  = Kandidatenpunkte in Body-Koordinaten:
           8 Prop-Spitzen (r_i ± R_p in X bzw. Z), Ober-/Unterseite (0, ±H/2, 0)
           r_c = Mittelwert aller Kandidaten, deren Tiefe −(q·p)·n höchstens 5 mm hinter der tiefsten liegt
           (flache Landung ⇒ r_c ≈ Mitte ⇒ kein Drehstoß)
v_c      = v + ω_w × r_c,w ;   v_n = v_c·n  (< 0: auf die Wand zu)
Crash?   (armed und −v_n ≥ impactTolerance) oder (armed und |v_c,t| ≥ 1,5·impactTolerance und −v_n > 0,5)
         ⇒ Event CRASH(speed, n), Disarm CRASH, CRASH_COOLDOWN + ARM_SWITCH_NOT_RESET
         sonst −v_n ≥ 0,4·impactTolerance ⇒ Event HARD_HIT
Ruhekontakt: −v_n < 0,6 m/s ⇒ keine Rückfederung (e = 0), sonst e aus 3.8
Normalimpuls (Starrkörper, diagonale Trägheit):
    j = −(1+e)·v_n / (1/m + n·((I_w⁻¹ (r_c × n)) × r_c))
    v += j n/m ;   ω_b += I⁻¹ · q⁻¹·(r_c,w × j n) · κ     (κ = 1, |Δω| ≤ 25 rad/s)
Reibungsimpuls: t = Tangentialrichtung von v_c;  j_t = −min(μ·j, |v_c,t| / (1/m + …)) analog
```

### 8.3 Bodenkontakt, Taumeln, Liegen

- `onGround`: hitY mit n = +Y in diesem Frame. Nach **2 Frames in Folge**, und wenn |v| < 1 m/s, gilt der **Ruhekontakt**:
  - `v.y = max(v.y, 0)`, horizontale Reibung `v_xz *= exp(−μ·g·dt/max(|v_xz|, 0,1))`.
  - Ausrichten auf die nächste stabile Lage: Ziel-„oben“ ist +Y, wenn `(q·ŷ).y ≥ 0`, sonst −Y (Rückenlage, Turtle nötig). Dann `ω_b ← ω_b·exp(−10·dt) + 6·e_b` (Tilt-Fehler wie ANGLE). Die Drohne kippt auf Bauch oder Rücken und bleibt liegen, statt schief zu schweben.
  - Armed mit Schub < Gewicht: I bleibt 0 (5.8). Übersteigt der Schub das Gewicht, hebt sie ab, dann folgen kein Y-Treffer und kein Ruhekontakt.
- Nach einem Crash (disarmed) läuft die Physik weiter: Schwerkraft, Widerstand, Rückfederung mit Drehstoß. Die Drohne taumelt realistisch zu Boden.
- **Prop-Strike (optional):** Nimmt ein armed HARD_HIT einen Prop-Kandidaten als r_c, verliert der nächste Motor 0,2 s lang 30 % Schub (Event PROP_STRIKE).

---

## 9. Telemetrie und Events

`Telemetry` ist veränderlich und wird wiederverwendet. Alle Arrays haben die Länge 4, Index = Motor M1..M4.

| Feld | Einheit | Bedeutung |
|---|---|---|
| `motorRpm[]` | U/min | ω_i·60/2π |
| `motorOutput[]` | 0..1 | Duty nach Mixer/Idle (für OSD/Blackbox) |
| `motorCurrent[]` | A | Phasenstrom I_i |
| `motorThrust[]` | N | T_i |
| `motorTempC[]`, `escTempC[]` | °C | optional (4.7) |
| `totalCurrent` | A | I_bus |
| `packVoltage`, `cellVoltage` | V | unter Last |
| `cellVoltageRest` | V | OCV/N (Ruhe) |
| `mAhUsed`, `chargeFraction` | mAh, 0..1 | soc (geklemmt ≥ 0 für die Anzeige) |
| `batteryState` | OK / LOW / CRITICAL / DEAD | |
| `propWash` | 0..1 | max_i w_i |
| `inGroundEffect`, `groundEffectFactor` | bool, ≥ 1 | Faktor > 1,03 |
| `gLoad`, `gLoadZ` | g | |f_b|/g, f_b.y/g |
| `speed`, `verticalSpeed` | m/s | |
| `rollDeg`, `pitchDeg`, `headingDeg` | ° | Kap. 1.2 |
| `gyroDps[3]`, `setpointDps[3]` | °/s | FC-Achsen |
| `pidP[3]`, `pidI[3]`, `pidD[3]`, `pidF[3]` | PID-Einheiten | für Configurator-Graphen |
| `throttleOut` | 0..1 | nach Kurve/Airmode-Verschiebung |
| `mixerSaturated` | bool | |
| `armed`, `mode`, `airmodeActive` | | |
| `armBlockReasons` | EnumSet | laufend ausgewertet (OSD-Warnungen) |
| `lastDisarmReason` | enum | |
| `hoverThrottleStick` | 0..1 | Schätzung für den aktuellen soc (für Autopilot/Failsafe) |
| `lastImpactSpeed` | m/s | |
| `failsafeStage` | 0/1/2 | |

**Events** (`List<SimEvent>`, wird jeden Frame per `drainEvents(Consumer)` geleert):
ARMED, DISARMED(reason), ARM_BLOCKED(reasons), CRASH(speed, normal), HARD_HIT(speed), GROUND_TOUCH(speed), PROP_STRIKE(motor), BATTERY_LOW, BATTERY_CRITICAL, BATTERY_DEAD, FAILSAFE_STAGE1, FAILSAFE_STAGE2, AIRMODE_ACTIVE.

---

## 10. Konfigurationsobjekte

Plain Java: öffentliche, veränderliche Felder, No-Arg-Konstruktor, Defaults im Feld-Initializer, `copy()` (tief), `sanitize()` (klemmt nach dem Laden auf gültige Bereiche und ersetzt `null` durch Defaults). Enums werden per Name serialisiert. `version` erlaubt spätere Migrationen. Keine Getter-Pflicht, keine Final-Felder, keine Records (Gson/Codec-freundlich). Brüche (0..1) sind `double`, PID-Werte BF-ähnlich.

```java
public class FlightControllerConfig {
    public int version = 1;
    public RateProfile rates = new RateProfile();
    public PidProfile pid = new PidProfile();
    public FilterSettings filters = new FilterSettings();
    public LoopSettings loop = new LoopSettings();
}

public enum RatesType { BETAFLIGHT, ACTUAL }

public class RateCurve {
    // BETAFLIGHT
    public double rcRate = 1.0, superRate = 0.70, rcExpo = 0.0;
    // ACTUAL
    public double centerRate = 70, maxRate = 670, actualExpo = 0.0;   // °/s, °/s, 0..1
}

public class RateProfile {
    public RatesType type = RatesType.ACTUAL;
    public RateCurve roll = new RateCurve(), pitch = new RateCurve(), yaw = new RateCurve();
    public double rcDeadband = 0.0, yawDeadband = 0.0;                // Stickanteil
    public double rateLimitDps = 1998;
}

public enum TpaMode { PD, D }
public enum ItermRelaxMode { OFF, RP, RPY }
public enum ThrottleLimitType { OFF, SCALE, CLIP }
public enum PidSource { DERIVED, CUSTOM }

public class AxisPid { public double p, i, d, f; }

public class PidProfile {
    public PidSource source = PidSource.DERIVED;
    public String derivedForBuild = "";            // Build-Key, für den abgeleitet wurde
    public AxisPid roll  = pid(45, 80, 40, 120);   // Platzhalter, überschrieben von PidAutoTuner
    public AxisPid pitch = pid(47, 84, 46, 125);
    public AxisPid yaw   = pid(45, 80, 0, 120);
    public double pidSumLimit = 500, pidSumLimitYaw = 400, iTermLimit = 400;
    // Feedforward
    public double ffSmoothingHz = 40, ffMaxRateLimit = 0.90;
    // ANGLE / HORIZON
    public double angleStrength = 50, angleLimitDeg = 60, angleFeedforward = 1.0, angleMaxRateDps = 500;
    public double horizonStrength = 50, horizonStickTransition = 0.75, horizonLimitDeg = 135;
    // I-Term
    public ItermRelaxMode itermRelax = ItermRelaxMode.RP;
    public double itermRelaxCutoffHz = 15, itermRelaxThresholdDps = 40;
    public boolean antiGravity = true;  public double antiGravityGain = 80;
    // TPA
    public TpaMode tpaMode = TpaMode.D;  public double tpaRate = 0.65, tpaBreakpoint = 0.35;
    // Mixer / Motoren
    public boolean airmode = true;  public double airmodeStartThrottle = 0.25;
    public double motorIdle = 0.055, thrustLinear = 0.0, vbatSagCompensation = 0.0, motorOutputLimit = 1.0;
    public boolean propsOut = false;
    // Throttle
    public double throttleMid = 0.5, throttleExpo = 0.0, throttleLimit = 1.0;
    public ThrottleLimitType throttleLimitType = ThrottleLimitType.OFF;
    // Arming
    public double armMaxTiltDeg = 25, armThrottleMax = 0.05;
}

public class FilterSettings {
    public double gyroLpfHz = 250, dtermLpf1Hz = 100, dtermLpf2Hz = 200;   // PT1, 0 = aus
    public double gyroNoiseDps = 0.25, gyroVibrationDps = 2.0;            // 0 = aus
    public boolean rcInterpolation = true;
}

public enum PidLoopRate { HZ_2000(1), HZ_1000(2), HZ_500(4); public final int divider; }
public class LoopSettings { public PidLoopRate pidLoop = PidLoopRate.HZ_2000; }

public class SimSettings {                 // nicht im BF-Configurator, sondern Spiel-/Serveroptionen
    public boolean propWash = true;  public double propWashStrength = 1.0;
    public boolean groundEffect = true, batterySag = true, infiniteBattery = false;
    public boolean crashDisarm = true;  public double crashSpeedMultiplier = 1.0;
    public boolean thermal = true, wind = true;
    public double maxFrameDt = 0.05;
}
```

Wertebereiche für `sanitize()`: P/I/D/F [0, 250/250/200/1000], Rates center [0, 2000], max [center, 1998], superRate [0, 0,99], rcRate [0,01, 2,55], Expos [0, 1], angleLimit [10, 85], angleStrength [0, 200], Idle [0, 0,20], throttleMid [0,05, 0,95], TPA-Rate [0, 1], Filter 0 oder [20, 1000] Hz.

`FlightSim.setConfig(FlightControllerConfig)` übernimmt eine Kopie. Filter-/Loop-Änderungen initialisieren die Filterzustände neu, aber nicht während des Fluges: Die Änderung wird beim nächsten Disarm wirksam, wie Betaflight, das beim Speichern neu startet.

---

## 11. Einbindung in die Mod

### 11.1 Werkbank (Drohne zusammenbauen)

Die Drohne wird auf einer **Werkbank** aus Teilen zusammengebaut. Das Ergebnis ist ein `DroneBuild`, gespeichert im Drohnen-Item zusammen mit der `FlightControllerConfig`. Die Sim liefert der Werkbank-Oberfläche eine Live-Vorschau:

```java
BuildPreview p = BuildPreview.of(build);   // < 25 ms inkl. Auto-Tuner, pro Build-Key gecacht
p.massGrams, p.thrustToWeight, p.hoverThrottleStick, p.hoverCurrentA, p.flightTimeMin,
p.topSpeedMs, p.motorTimeConstantMs, p.maxRollAccelDps2, p.derivedPids, p.warnings
```

Zusätzliche Sim-Warnungen neben `DroneBuild.warnings()`: `UNDERPOWERED` (T/W < 1,5), `SLUGGISH_MOTORS` (τ_m > 80 ms), `BATTERY_SAG` (Vollgasstrom > 1,3·I_cont), `CUTOFF_UNDER_LOAD` (Zelle bei Vollgas < cutoff). Wird der Build geändert und steht `pid.source == DERIVED`, leitet die Werkbank die PIDs sofort neu ab.

### 11.2 Drohnen-Entity (Client des Piloten)

- Pro gerendertem Frame (nicht pro Tick): Input pollen → `Environment` füllen (Raycast nach unten bis 1 m für `groundDistance`, Wasser-Check, Wind) → `step` → `move` → `applyCollision` → Pose setzen.
- Rendering nutzt direkt `orientation` (Quaternion). Kamera = Drohnen-Quaternion × Kamera-Uptilt.
- Der Server erhält 20-Hz-Posen (Position, Quaternion, Motor-RPM für Sound) und interpoliert für Zuschauer. Die Sim läuft nur beim Piloten.

### 11.3 Autopilot (Return-to-Home, später)

Der Autopilot setzt `PilotInput` im Modus ANGLE: `roll/pitch = StickMath.angleToStick(zielDeg, angleLimitDeg)`, `yaw` als Rate-Stick, `throttle` aus einer Höhenregelung um `telemetry.hoverThrottleStick`. Mit `angleFeedforward = 1,0` und der Tilt-Vektor-Regelung ist die Lagefolge präzise (Kap. 12, C8).

---

## 12. Testplan (JUnit 5, `common/src/test/java/de/lardtom/propwash/sim/`)

Wenn nicht anders angegeben: Gyro-Rauschen aus, Prop Wash aus, soc 1,0, abgeleitete PIDs, PID-Loop 2 kHz, Seed 42. Harness: `TestRig` (nur in Tests). Es enthält eine Ebene `y = 0` mit achsweiser AABB-Auflösung und erzeugt `CollisionReport` wie die Entity. Dazu `TestStand`: Pose fixiert, für Motor-/Schubmessungen.

### A. Formeln und Bausteine (exakt)

| # | Test | Kriterium |
|---|---|---|
| A1 | Rates BF/ACTUAL, Tabelle 5.4.2 + rcRate 2,5 | ±0,01 °/s |
| A2 | Throttle-Kurve, Referenzwerte 5.4.3; SCALE/CLIP | ±1e−9 |
| A3 | Mixer-Fälle A/B/C (5.7) inkl. `sat` | ±1e−4 |
| A4 | PT1-Sprung: nach τ = 1/(2π f_c) | 63,2 % ±0,5 % |
| A5 | Konventionen: Roll-Moment (M3/M4 mehr) ⇒ ω.z > 0 und linke Seite hoch; M2/M3 mehr ⇒ ω.y < 0 (Nase rechts); q = 90° um +Y ⇒ heading +90°, MC-yRot −90 | exakt / 1e−9 |
| A6 | OCV-Tabellen an den Stützstellen; geschlossene Busspannung = Bisektions-Lösung | 1e−6 V |
| A7 | `AirframeSpec` aller 5 Presets vs. Tabelle A | Masse ±0,05 g; I, J_r, k_T, k_Q ±1 % |
| A8 | `OperatingPoints` vs. Tabelle B (Hover-Duty, Drehzahlen, Ströme, T/W, Topspeed) | ±1 % (Topspeed ±3 %) |
| A9 | `PidAutoTuner` vs. Tabelle 6.3 | P/I/D/F je ±10 % oder ±3; Roll/Pitch PM ≥ 50°, GM ≥ 8 dB; Yaw PM ≥ 55° |
| A10 | BF-Defaults 45/80/30 auf FREESTYLE (Margin-Rechner) | PM ≥ 45°, GM ≥ 8 dB |
| A11 | Arming: jeder ArmBlockReason einzeln, Flankenlogik, ARM_SWITCH_NOT_RESET nach Crash, Failsafe-Stufen-Timing (0,3 s / +1,5 s) | exakt |
| A12 | Configs: alle Felder public und nicht final, `copy()` ist tief, `sanitize()` klemmt und ersetzt null, JSON-Roundtrip (Gson im Test-Classpath) ergibt gleiche Werte | exakt |

### B. Physik (offener Kreis)

| # | Test | Kriterium |
|---|---|---|
| B1 | Freier Fall disarmed aus Ruhe (flach), 1 s, FREESTYLE | v_y = −v_t·tanh(g·t/v_t) = −8,91 m/s ±1 % (v_t = 17,7); mit Widerstand aus: −9,81 ±1e−3 |
| B2 | Endgeschwindigkeit flach fallend (20 s) | Tabelle B „Fallgeschw.“ ±3 % |
| B3 | TestStand Vollgas, 2 s | Drehzahl, Busstrom, Schub = Tabelle B ±2 % |
| B4 | TestStand: Duty-Sprung Hover → Hover + 0,1 | 63-%-Zeit der Drehzahländerung = τ_m ±15 % |
| B5 | Hover-Strom und mAh nach 60 s Hover (ANGLE) | Tabelle B ±3 %; mAh = I·60/3,6 ±3 % |
| B6 | Bodeneffekt FREESTYLE bei z = D/2 und z = D | 1,114 / 1,044 ±0,002 |
| B7 | Prop Wash FREESTYLE (Hover-Duty, Anströmung vorgegeben): sinkt 5,5 m/s senkrecht / steht / 10 m/s horizontal + 5,5 sinkend | w ≥ 0,9 / w = 0 / w < 0,1 |
| B8 | Determinismus: zwei Sims, gleicher Seed + Eingaben, 10 s mit Rauschen und Prop Wash | bitgleich (`Double.doubleToRawLongBits`) |
| B9 | Frameraten-Robustheit: gleiche Stick-Choreografie (5 s ACRO) mit dt = 1/30, 1/60, 1/144 und Zufalls-dt ∈ [0, 50 ms] | Endposition Abweichung < 5 cm, Lage < 1° |
| B10 | Energie/Stabilität: 60 s Hover mit Rauschen + Prop Wash, dt = 50 ms | kein NaN, |q| = 1 ±1e−12 |
| B11 | Akku: LONG_RANGE Vollgas 1 s | BATTERY_CRITICAL-Event nach ≤ 3 s; FREESTYLE Vollgas: Busspannung 20,5–22,0 V |

### C. Regelkreis (FC + Physik), Sollwerte aus dem Prototyp

C1–C3 ohne FF, Setpoint-Sprung bei t = 0,1 s aus dem Hover. Anstieg = 10→90 %, Einschwingen = letzter Austritt aus ±5 %.

| # | Test | WHOOP | CINEWHOOP | FREESTYLE | RACER | LONG_RANGE |
|---|---|---|---|---|---|---|
| C1 | Roll 400 °/s: Prototyp Anstieg / Überschw. / Einschw. | 107,5 ms / 14,2 % / 461 ms | 34,0 / 1,5 / 49 | 24,5 / 0,4 / 37 | 27,5 / 0,6 / 40 | 35,0 / 1,7 / 52 |
| | **Kriterium** | ≤ 135 / ≤ 20 % / ≤ 700 | ≤ 43 / ≤ 6 % / ≤ 75 | ≤ 31 / ≤ 5 % / ≤ 60 | ≤ 35 / ≤ 5 % / ≤ 60 | ≤ 44 / ≤ 7 % / ≤ 80 |
| C2 | Pitch 400 °/s: Prototyp | 108,5 / 14,8 / 466 | 33,5 / 1,5 / 49 | 25,0 / 0,5 / 37 | 27,5 / 0,7 / 41 | 35,5 / 1,9 / 53 |
| | **Kriterium** | wie C1 | wie C1 | wie C1 | wie C1 | wie C1 |
| C3 | Yaw 200 °/s: Prototyp | 41,0 / 5,4 / 142 | 57,5 / 3,4 / 95 | 99,5 / 5,2 / 420 | 94,5 / 4,6 / 132 | 71,5 / 3,2 / 113 |
| | **Kriterium** | ≤ 60 / ≤ 10 % / ≤ 250 | ≤ 75 / ≤ 10 % / ≤ 200 | ≤ 125 / ≤ 10 % / ≤ 550 | ≤ 120 / ≤ 10 % / ≤ 250 | ≤ 95 / ≤ 10 % / ≤ 200 |
| C4 | Stick-Flick 0→1 in 50 ms (ACTUAL 70/670), Lag bei 50 % / 90 %, **mit FF**: Prototyp | 39,5 / 107 ms, Ü 8,7 % | 9,5 / 13,5, 0,2 % | 4,5 / 5,0, 2,0 % | 6,0 / 6,5, 0 % | 9,0 / 13,0, 0 % |
| | **Kriterium** | Lag90 ≤ 130, Ü ≤ 15 % | Lag90 ≤ 20, Ü ≤ 6 % | Lag90 ≤ 10, Ü ≤ 6 % | Lag90 ≤ 10, Ü ≤ 6 % | Lag90 ≤ 20, Ü ≤ 6 % |
| | und FF senkt Lag90 gegenüber F = 0 um mindestens | 5 % | 30 % | 50 % | 50 % | 30 % |
| C5 | ANGLE 30°-Sprung Roll: Prototyp Anstieg / Einschw. / Endfehler | 480 / 835 ms / 0,55° | 274 / 399 / 0,01° | 221 / 315 / 0,00° | 219 / 315 / 0,00° | 254 / 375 / 0,01° |
| | **Kriterium** (Default angleFeedforward 1,0; Überschwingen ≤ 5 % überall) | ≤ 600 / ≤ 1100 / ≤ 1,0° | ≤ 340 / ≤ 520 / ≤ 0,5° | ≤ 280 / ≤ 410 / ≤ 0,5° | ≤ 280 / ≤ 410 / ≤ 0,5° | ≤ 320 / ≤ 490 / ≤ 0,5° |
| C6 | Flip: 360° bei vollem Roll-Stick (670 °/s) | 571 ms | 559 | 553 | 554 | 565 |
| | **Kriterium** | ≤ 620 ms | ≤ 600 | ≤ 600 | ≤ 600 | ≤ 610 |
| C7 | Punch-out 1 s Vollgas aus Hover (ANGLE level): Prototyp v_y / Spitzen-g | 6,4 m/s / 2,2 g | 17,4 / 7,0 | 36,1 / 9,6 | 42,8 / 11,3 | 16,4 / 3,7 |
| | **Kriterium** | 5–8 / 1,9–2,5 | 14–21 / 6–8 | 30–42 / 8,5–11 | 36–49 / 10–12,5 | 13–19 / 3,2–4,3 |
| C8 | ANGLE-Sinus ±20° / 0,5 Hz (Autopilot-Folge), RMS-Fehler nach 1 s: Prototyp FF 1,0 (FF 0) | 0,95° (8,46°) | 0,12° (5,12°) | 0,07° (4,20°) | 0,08° (4,20°) | 0,11° (4,89°) |
| | **Kriterium** mit angleFeedforward 1,0 | ≤ 1,5° | ≤ 0,5° | ≤ 0,5° | ≤ 0,5° | ≤ 0,5° |
| C9 | Hover 4 s ANGLE mit Hover-Stick aus OperatingPoints | |v_y| ≤ 0,3 m/s, Lage ≤ 0,5° | ← | ← | ← | ← |
| C10 | 500-Hz-Loop mit dafür abgeleiteten PIDs, Roll-Sprung 400 °/s: Prototyp | 100,5 / 12,7 % / 428 | 40,5 / 1,8 / 58 | 30,5 / 0,6 / 45 | 33,0 / 0,8 / 48 | 40,0 / 1,4 / 59 |
| | **Kriterium** | Überschw. ≤ 20 %, Einschw. ≤ 650 | ≤ 7 %, ≤ 90 | ≤ 5 %, ≤ 70 | ≤ 5 %, ≤ 75 | ≤ 7 %, ≤ 90 |
| C11 | Hover 10 s mit Gyro-Rauschen + Prop Wash an (ANGLE) | RMS-Lagefehler < 1°, keine Motor-Sättigung > 5 % der Zeit | ← | ← | ← | ← |
| C12 | Sinken 6 m/s senkrecht, dann Hover-Gas (ACRO, Sticks mittig) | propWash-Spitze ≥ 0,7; Rate-Fehler-RMS in 0,5 s nach dem Gasgeben > 3 × Wert ohne Prop Wash (Prop Wash ist spürbar); kein Absturz | ← | ← | ← | ← |
| C13 | Airmode: aus dem Hover Gas auf 0, Roll-Setpoint 300 °/s | Airmode: Rate nach 150 ms ≥ 270 °/s (WHOOP ≥ 220; Prototyp 299 bzw. 245) und `throttleOut` > 0 (verschoben); ohne Airmode: `throttleOut` = 0 und min(d) = idle | ← | ← | ← | ← |
| C14 | I-Term am Boden: armed, Throttle 0,1, 5 s auf der Ebene | I = 0, keine Bewegung > 1 cm, airmodeActive = false | ← | ← | ← | ← |

Hinweis zu C5: Die Prototyp-Werte gelten für angleFeedforward 0. Mit dem Default 1,0 ändern sie sich um weniger als 5 %, außer WHOOP (Anstieg 235 ms statt 480 ms). Die Kriterien gelten für beide Einstellungen.

### D. Kollisionen (`TestRig` mit Ebene)

| # | Test | Kriterium |
|---|---|---|
| D1 | FREESTYLE disarmed, flach aus 1 m fallen lassen (v ≈ 4,4 m/s < impactTolerance) | erster Rückprall v_y = 0,25·v ±5 %; Ruhe (|v| < 0,05) innerhalb 2 s; kein CRASH; Lage aufrecht < 2° |
| D2 | armed mit 12 m/s horizontal gegen eine Wand (X-Achse) | CRASH-Event (speed ≈ 12), disarmed, Grund CRASH; Arm-Versuch ⇒ CRASH_COOLDOWN, danach ARM_SWITCH_NOT_RESET bis zum Umschalten |
| D3 | Schleifen an der Wand: 6 m/s tangential, 1 m/s normal | kein CRASH; Tangentialgeschwindigkeit sinkt (Reibung) |
| D4 | disarmed mit 45° Roll und 5 m/s auf den Boden | |Δω| > 3 rad/s direkt nach dem Aufprall; nach 3 s |(q·ŷ).y| > 0,98 (Bauch oder Rücken) |
| D5 | Grenzfall impactTolerance: v_n = 0,99·impactTolerance / 1,01·impactTolerance | HARD_HIT ohne Disarm / CRASH |
| D6 | Landung armed im Hover-Gas, dann Gas auf 0 | GROUND_TOUCH, kein CRASH, ruhig liegend, weiter armed |

### E. Performance

| # | Test | Kriterium |
|---|---|---|
| E1 | 10 000 Frames mit dt = 50 ms (100 Substeps), nach JIT-Warmup | ≤ 0,3 ms pro Frame auf dem Dev-Rechner (Richtwert, im CI nur loggen) |
| E2 | Allokationen pro Frame (`ThreadMXBean.getThreadAllocatedBytes`) | < 64 Byte/Frame im Mittel über 1000 Frames |
| E3 | `BuildPreview.of` für alle 5 Presets (kalter Cache) | < 50 ms pro Build |

---

## 13. Offene Punkte und Erweiterungen

1. **Teile-Kalibrierung** (3.11): Whoop-Motorwiderstand und die c_p der kleinen Props bestimmen Whoop-Gefühl und Flugzeiten. Das ist Sache der Teile-Daten, das Flugmodell bleibt unverändert.
2. **Wind/Turbulenz:** Die Schnittstelle (`Environment.wind`) ist da. Ein Böenmodell (Dryden-ähnlich, seeded) folgt später in `SimSettings.wind`.
3. **Turtle-Modus** (5.8), **D-Min**, **Throttle-Boost**, **Dynamic Idle** (RPM-geregelt) sind als v2-Felder vorgesehen und bis dahin ausgeblendet.
4. **Blackbox:** Die Telemetrie enthält schon P/I/D/F je Achse. Ein Ringpuffer (letzte 10 s bei 500 Hz) für Configurator-Graphen ist ein kleiner Zusatz.
5. **Prop-Schäden:** Ein Crash könnte Props beschädigen (k_T −20 %, Vibration ↑), bis man auf der Werkbank repariert. Das bindet das Gameplay an die Werkbank.
6. **Server-Plausibilität:** Der Server kann mit `AirframeSpec` grob prüfen, ob gemeldete Geschwindigkeiten physikalisch möglich sind (Topspeed + Fallgeschwindigkeit + Toleranz).
