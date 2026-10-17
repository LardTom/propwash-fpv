# Flugfeeling „ein bisschen zu floaty“ und Regler „Schwerkraft“ (Propwash 0.4.0)

Stand: 2026-10-02, gehört zum noch nicht veröffentlichten Release 0.4.0. Anlass: Manchen Spielern ist das Fliegen ein
kleines bisschen zu floaty, sie wünschen sich etwas mehr Schwerkraft. Entscheidung vom 02.10.: **Analyse +
Regler**. Das Fallen, den Sturzflug und das Sinken im Leerlauf gegen echte Daten prüfen; nur einen eindeutig falschen
Modellwert gezielt korrigieren (Ausnahme von der Regel „Flug-Engine eingefroren“, nur mit Belegen); dazu ein Regler
„Schwerkraft“ (0,8–1,5×, Standard 1,0 = echte 9,81 m/s²). Die Schwerkraft selbst bleibt im Standard bei 9,81 m/s².

## 0. Ergebnis

- **Nachtrag 08.10. (0.5.0):** Die eigentliche Ursache: Der Widerstand von oben der Whoop-Rahmen war 1,4–2,2-mal zu
  hoch (Umriss-Regel aus §9), und im angetriebenen Sinkflug bremsten die Prop-Blätter doppelt (Rotormodell und
  Rumpfwiderstand). Beides ist korrigiert, Whoops fallen und sinken jetzt wie die Referenz aus Prüfstand und
  Windkanal-Daten; die Welt-Regel Schwerkraft reicht von 0 bis 1000 % in 1-%-Schritten (§10).
- **Nachtrag 0.5.0:** Die Whoop-Rahmen (65 und 75 mm) haben seit 0.5.0 eine Draufsicht nach dem gemessenen Umriss;
  der 65-mm-Whoop fällt jetzt in der Mitte der Referenz (§9).
- **Keine Korrektur (0.4.0).** Leerlaufdrehzahl und Idle-Schub, Rumpf- bzw. Vertikalwiderstand, Prop-Wash, Endgeschwindigkeiten
  flach und mit der Nase nach unten, Sturzflug mit Vollgas, Fallkurve aus dem Schweben und Punch-and-Cut liegen für alle
  Klassen im Bereich der Referenzen (§3). Kein Wert ist eindeutig falsch; der 65-mm-Whoop liegt an der Kante „eher zu
  viel Widerstand“, ohne Messdaten aber nicht eindeutig (§3.8). Bewegungs-Goldens und Kalibrierung sind byte-gleich.
- **Woher das „floaty“ kommt** (§4): In Minecraft fällt alles drei- bis sechzehnmal so schnell wie in echt – ein
  Spieler fällt in der ersten Sekunde 14,6 m, eine echte Drohne knapp 4,3 m. Echte FPV-Drohnen bremsen im Leerlauf
  stark (Rumpf und drehende Props; ein 5″ erreicht flach nur rund 16 m/s). Dazu verkleinert das Weitwinkel-Bild jede
  Bewegung. Das Modell gibt die echte Physik wieder; der Eindruck ist ein Maßstabs- und Sehgewohnheits-Effekt.
- **Regler „Schwerkraft“** im Tab „Simulation“ (§6): 0,80–1,50× in 0,05er-Schritten, Standard 1,00×. Seit 0.5.0 ist
  daraus die Welt-Regel `propwash:gravity_scale` (zuerst 80–150 %, seit 08.10. 0–1000 % in 1-%-Schritten, §10.6) im Tab „Welt-Regeln“ geworden
  (`docs/design/world-rules.md`); der folgende Text beschreibt den Stand von 0.4.0. Freigabe wie die
  übrigen Physik-Optionen (Kreativmodus oder Kreativ-Brille), ein Wert ≠ 1,0 gilt als veränderte Physik (keine
  Bestenlisten, keine Geister). Werkbank, Übersicht, Item-Tooltip, API und Flugzeiten rechnen weiter mit 1,0.

## 1. Messaufbau

Neues Werkzeug `common/src/test/…/sim/FallCharacterization.java` (JUnit `FallCharacterizationTest`). `tools/calibration.sh`
schreibt die Tabelle mit den Just-More-Parts-Daten nach `build/calibration/fall.txt`; ohne Addon-Daten misst der Test
nur die eingebauten Presets.

- Simulation wie im Spiel mit Standardeinstellungen (`new SimTuning()` = Standard von `DroneClientConfig`: Prop-Wash,
  Wake-Feld, Turbulenz, Spannungseinbruch an), kein Wind, Akku 80 %, Flight Controller mit Standardwerten (Motor-Idle
  5,5 %, Airmode an, im Schweben eingerastet), 144 fps, Start in 3000 m Höhe (kein Bodeneffekt).
- **Idle:** Drohne fest auf dem Prüfstand, alle Motoren auf dem Idle-Tastverhältnis (5,5 %), Schub nach 3 s.
- **Fall aus dem Schweben:** 3 s im Winkelmodus mit Höhenregler schweben, dann ACRO, Sticks mittig, Gas 0 %.
  Geschwindigkeit und Höhenverlust nach 0,5/1/2/3 s; Endgeschwindigkeit als Mittel der letzten 2 s von 40 s, dazu die
  Kraftanteile (Schub entlang der Hochachse, Rumpfwiderstand) und das Prop-Wash-Gewicht. Derselbe Fall ohne Prop-Wash.
- **Nase unten / kopfüber:** aus dem Schweben auf 90° Nase nach unten bzw. kopfüber (Schub nach unten) gestellt, ACRO
  hält die Lage, 0 % bzw. 100 % Gas, 40 s.
- **Steigen:** 100 % Gas aus dem Schweben, höchste Steiggeschwindigkeit in 15 s.
- **Punch-and-Cut:** 1 s 100 % Gas aus dem Schweben, dann 0 %; Zeit und Höhe bis zum Scheitel, verglichen mit dem reinen
  Wurf `v/g` und `v²/2g`.

## 2. Messwerte (vorher = nachher, Schwerkraft 1,0)

Fall aus dem Schweben bei 0 % Gas, Idle:

| Aufbau | Masse g | Schwebegas % | Idle U/min (Schweben U/min) | Idle-Schub statisch / im Sinkflug [% Gewicht] | v nach 0,5 / 1 / 2 / 3 s [m/s] | Höhe nach 0,5 / 1 / 2 / 3 s [m] |
|---|---|---|---|---|---|---|
| `propwash:whoop_65` | 28,1 | 50,9 | 4347 (43669) | 1,0 / 1,4 | 3,94 / 5,98 / 6,74 / 6,79 | 0,98 / 3,55 / 10,07 / 16,84 |
| `propwash:cinewhoop_3` | 286,7 | 34,0 | 3607 (24463) | 2,2 / 3,3 | 4,39 / 8,24 / 12,68 / 14,25 | 1,06 / 4,27 / 15,06 / 28,68 |
| `propwash:freestyle_5` | 525,2 | 22,0 | 2144 (10509) | 4,2 / 5,7 | 4,38 / 8,24 / 13,04 / 14,96 | 1,08 / 4,27 / 15,23 / 29,39 |
| `propwash:race_5` (seit 11.10. mit dem 2,9″-Prop) | 430,6 | 17,8 | 2273 (9438) | 5,8 / 7,9 | 4,31 / 8,25 / 13,69 / 16,30 | 1,05 / 4,22 / 15,47 / 30,65 |
| `propwash:long_range_7` | 846,2 | 24,6 | 1541 (8216) | 3,5 / 5,2 | 4,36 / 8,37 / 13,72 / 16,12 | 1,05 / 4,27 / 15,62 / 30,72 |
| `justmoreparts:whoop85_2s` | 56,7 | 25,7 | 5551 (29621) | 3,5 / 4,8 | 3,93 / 6,26 / 7,39 / 7,49 | 0,97 / 3,60 / 10,63 / 18,08 |
| `justmoreparts:bash5_6s` | 541,8 | 20,2 | 2403 (10985) | 4,8 / 6,5 | 4,34 / 8,13 / 12,73 / 14,51 | 1,07 / 4,22 / 14,97 / 28,75 |
| `justmoreparts:race5_6s` | 468,9 | 16,5 | 2507 (9726) | 6,6 / 9,0 | 4,29 / 8,27 / 14,10 / 17,19 | 1,05 / 4,21 / 15,65 / 31,49 |
| `justmoreparts:lr7_8s` | 896,9 | 20,4 | 1776 (8181) | 4,7 / 7,5 | 4,19 / 8,13 / 13,42 / 15,84 | 1,00 / 4,11 / 15,18 / 29,99 |
| freier Fall | | | | | 4,91 / 9,81 / 19,62 / 29,43 | 1,23 / 4,91 / 19,62 / 44,15 |

Endgeschwindigkeiten, Steigen und Punch-and-Cut:

| Aufbau | flach, Idle [m/s] | davon Schub / Rumpf [% Gewicht] | ohne Prop-Wash [m/s] | Nase unten, Idle [m/s] | kopfüber, Idle [m/s] | kopfüber, Vollgas [m/s] (km/h) | Steigen Vollgas [m/s] | Punch 1 s → v [m/s] | danach bis Scheitel [s / m] | nur Schwerkraft [s / m] |
|---|---|---|---|---|---|---|---|---|---|---|
| `propwash:whoop_65` | 6,8 | 1,4 / 98,6 | 6,8 | 10,7 | 6,9 | 11,3 (41) | 7,6 | 7,3 | 0,61 / 2,07 | 0,74 / 2,71 |
| `propwash:cinewhoop_3` | 14,9 | 3,3 / 96,7 | 14,9 | 19,3 | 15,2 | 25,2 (91) | 20,9 | 18,0 | 1,38 / 10,83 | 1,83 / 16,43 |
| `propwash:freestyle_5` | 15,9 | 5,7 / 94,3 | 15,9 | 27,0 | 16,4 | 37,2 (134) | 35,4 | 34,2 | 1,91 / 23,62 | 3,48 / 59,56 |
| `propwash:race_5` (seit 11.10. mit dem 2,9″-Prop) | 18,0 | 7,9 / 92,1 | 18,0 | 30,3 | 18,8 | 38,7 (139) | 39,5 | 38,8 | 2,17 / 30,46 | 3,96 / 76,75 |
| `propwash:long_range_7` | 17,5 | 5,2 / 94,8 | 17,5 | 29,8 | 18,0 | 29,6 (107) | 25,2 | 22,7 | 1,69 / 16,31 | 2,31 / 26,20 |
| `justmoreparts:whoop85_2s` | 7,5 | 4,8 / 95,2 | 7,5 | 12,0 | 7,7 | 16,2 (58) | 14,4 | 14,4 | 0,89 / 4,99 | 1,47 / 10,56 |
| `justmoreparts:bash5_6s` | 15,4 | 6,5 / 93,5 | 15,4 | 26,2 | 15,9 | 36,1 (130) | 35,8 | 34,9 | 1,89 / 23,38 | 3,56 / 62,10 |
| `justmoreparts:race5_6s` | 19,6 | 9,0 / 91,0 | 19,6 | 33,2 | 20,6 | 45,5 (164) | 48,6 | 46,5 | 2,47 / 40,05 | 4,74 / 110,26 |
| `justmoreparts:lr7_8s` | 17,3 | 7,5 / 92,5 | 17,3 | 29,5 | 18,5 | 37,8 (136) | 34,2 | 32,6 | 2,00 / 24,77 | 3,32 / 54,16 |

Das Prop-Wash-Gewicht beim Sinken im Leerlauf ist in allen Fällen 0,00 (Spalte „ohne Prop-Wash“ gleich).

## 3. Vergleich mit realen Daten

### 3.1 Idle-Drehzahl und Idle-Schub

- Betaflight: `dshot_idle_value` Standard 550 = 5,5 % Motor-Idle; ohne Dynamic Idle bekommen die Motoren bei Gas 0 immer
  diesen Wert ([Betaflight: Dynamic Idle][bf-idle]). Propwash: `PidProfile.motorIdlePercent = 5,5`, alle Presets.
- Drehzahl: „At 5.5 %, motors spin at approximately 2200–2800 RPM at idle“ für 5″ ([UAVMODEL][uav-idle]); die Betaflight-Seite
  rechnet im Beispiel mit 2000 U/min bei rund 5 %. Propwash: 2144–2507 U/min für die 5″-Aufbauten, also am unteren Rand.
- Idle-Schub als Anteil des Gewichts ≈ (Idle-Drehzahl / Schwebedrehzahl)²: mit 2200–2800 U/min und 10 509 U/min
  Schwebedrehzahl 4,4–7,1 % für den 5″-Freestyle; Propwash 4,2 % statisch, 5,7 % im Sinkflug (Schub steigt bei Anströmung
  von unten bis 1,3×). Zu viel Idle-Schub macht eine Drohne „hängend und treibend statt sauber fallend“ ([UAVMODEL][uav-idle]) –
  Propwash liegt eher darunter. **Nicht zu viel Idle-Schub.**

### 3.2 Endgeschwindigkeit flach (Bauch nach unten)

- Gemessen: DJI Phantom 3 (1,22 kg) fällt flach mit rund **60 ft/s = 18,3 m/s**, auf der Kante mit 84 ft/s = 25,6 m/s
  (CFD und Flugtest der UAH, [FAA ASSURE A4, S. 136][assure]). Daraus `C_D·A = 2mg/(ρv²) ≈ 0,058 m²`.
- Frei drehende Props erhöhen den Widerstand um bis zu **110 %** ([Hammer et al. 2023][hammer]).
- Abschätzung mit derselben Methode, die beim Phantom 3 aufgeht (Draufsicht des Rumpfs × C_D 1,05–1,2, plus Blattfläche
  stehender Props, bis zur Obergrenze von Hammer für drehende Props):

| Klasse | Propwash `C_D·A_y` [m²] | Abschätzung `C_D·A` [m²] | Referenz flach | Propwash |
|---|---|---|---|---|
| 65-mm-Whoop (28 g, Ducts, Umriss 0,0055–0,0069 m²) | 0,0096 | 0,006–0,009 | 7,1–8,7 m/s | 6,8 m/s |
| Cinewhoop 3″ (287 g) | 0,020 | 0,020–0,028 | 12,9–15,1 m/s | 14,9 m/s |
| Freestyle 5″ (525 g) | 0,031 | 0,023–0,035 | 15,5–19,0 m/s | 15,9 m/s |
| Race 5″ (430 g) | 0,020 | 0,020–0,027 | 16,0–18,6 m/s | 18,0 m/s |
| Long Range 7″ (846 g) | 0,042 | 0,034–0,045 | 17,0–20,0 m/s | 17,5 m/s |

Bis auf den Whoop liegt jeder Wert im Bereich. Beim Sinken im Leerlauf tragen die Props 1,4–9 % des Gewichts, der
Rumpfwiderstand den Rest (`A_y = 2,9·A_z` offen bzw. `2,5·A_z` mit Ducts enthält die bremsende Wirkung der drehenden Props).

### 3.3 Nase nach unten

Höherer Anstellwinkel senkt den Widerstand um **40–85 %** ([Hammer et al. 2023][hammer]); die Phantom-3-Werte flach/Kante
ergeben 49 % weniger. Propwash: Nase unten 27,0 statt 15,9 m/s beim 5″ – `C_D·A` 65 % kleiner, Whoop 60 %, Cinewhoop
40 %, Long Range 65 %. Im Bereich. Zum Vergleich: ein 1,4-kg-Quad senkrecht mit stehenden Props 29,3 m/s, mit drehenden
40,5–42,7 m/s ([ICAS 2020][icas]).

### 3.4 Sturzflug mit Vollgas

Kopfüber mit Vollgas (Schub nach unten): 5″ Freestyle 134 km/h, Race 152 km/h (seit dem 11.10.2026 mit dem flachen
2,9″-Prop 139 km/h, `motors-props.md` §12), Long Range 107 km/h. Ein typischer
6S-5″-Freestyle fliegt rund 150 km/h ([Unmanned Tech][utech]); `TopSpeedTest` hält den Vollgas-Sturzflug wie bisher bei
höchstens 1,15× der Geradeaus-Höchstgeschwindigkeit. Unverändert und plausibel.

### 3.5 Fallkurve aus dem Schweben

Referenzkurve aus Endgeschwindigkeit `v_t` und Idle-Anteil `i`: `v(t) = v_t·tanh(g(1−i)t/v_t)`,
`h(t) = v_t²/(g(1−i))·ln cosh(g(1−i)t/v_t)`, mit den Bereichen aus §3.1/§3.2. Das Auslaufen der Motoren vom Schwebe- auf
den Idle-Schub kostet zusätzlich etwa `g·τ_m/2` (0,07 m/s beim 5″, 0,2 m/s beim Whoop).

| Klasse | Referenz v nach 0,5 / 1 / 2 / 3 s [m/s] | Propwash | Referenz Höhe nach 1 / 2 s [m] | Propwash |
|---|---|---|---|---|
| Whoop 65 | 4,2–4,4 / 6,2–7,0 / 7,0–8,5 / 7,1–8,7 | 3,94 / 5,98 / 6,74 / 6,79 | 3,8–4,1 / 10,6–12,1 | 3,55 / 10,07 |
| Cinewhoop 3″ | 4,5–4,7 / 8,0–8,5 / 11,6–12,9 / 12,6–14,5 | 4,39 / 8,24 / 12,68 / 14,25 | 4,3–4,5 / 14,5–15,5 | 4,27 / 15,06 |
| Freestyle 5″ | 4,4–4,6 / 8,2–8,7 / 12,8–14,4 / 14,6–17,2 | 4,38 / 8,24 / 13,04 / 14,96 | 4,3–4,5 / 15,1–16,4 | 4,27 / 15,23 |
| Race 5″ | 4,4–4,6 / 8,1–8,6 / 12,9–14,2 / 14,9–16,8 | 4,30 / 8,25 / 13,69 / 16,30 | 4,2–4,5 / 15,0–16,2 | 4,22 / 15,47 |
| Long Range 7″ | 4,5–4,7 / 8,4–8,9 / 13,5–14,8 / 15,7–17,8 | 4,36 / 8,37 / 13,72 / 16,12 | 4,4–4,6 / 15,7–16,7 | 4,27 / 15,62 |

Mit dem Auslauf-Abzug liegen Cinewhoop, 5″ und 7″ im Bereich; nach 1 s haben sie 84–85 % der Geschwindigkeit und 86–87 %
der Höhe des freien Falls. Der Whoop liegt an der Unterkante (§3.8).

### 3.6 Prop-Wash

Prop-Wash (Wirbelring, Wake-Feld) senkt im Modell nur den Schub (`f_wash = 1 − WASH_LOSS·w`); er bremst einen Fall also
nie, er beschleunigt ihn höchstens. Im Leerlauf-Fall ist sein Gewicht 0, weil der Wirbelring erst ab 25 % des
Schwebeschubs und bei Sinkverhältnissen um 1 entsteht. **Kein zu starkes Bremsen durch Prop-Wash.**

### 3.7 Punch-and-Cut

Nach 1 s Vollgas und Gas weg steigt der 5″ noch 1,9 s und 23,6 m – ein reiner Wurf wären 3,5 s und 59,6 m. Bei 34 m/s
bremst der Widerstand mit über 4 g; die Drohne kehrt also schneller um als ein geworfener Stein, wie bei echten Quads.
Bei kleinen Geschwindigkeiten (Whoop, 7 m/s) ist der Unterschied kleiner (0,61 statt 0,74 s).

### 3.8 Der 65-mm-Whoop

`C_D·A_y = 0,0096 m²` (A_y 0,00875 m² bei C_D 1,1) ist größer als `C_D × Umriss` mit C_D 1,1–1,3 (0,006–0,009 m²); die
Fallkurve liegt an bzw. knapp unter der Unterkante der Referenz (nach 1 s 5,98 statt 6,0–6,8 m/s mit Auslauf-Abzug,
Endgeschwindigkeit 6,8 statt 7,1–8,7 m/s). Nicht korrigiert:

- keine Messdaten eines fallenden Whoops gefunden, die Referenz ist eine Abschätzung; Ring- und Napfformen mit Ducts
  und drehenden Props können C_D 1,4–1,6 erreichen, dann liegt der Wert im Bereich;
- `A_y` wirkt auch im Vorwärtsflug (Topspeed-Fenster 30–42 km/h) und in der Reise-Energie; eine Änderung würde die
  kalibrierten Whoop-Flugzeiten und den Owner-Testfall (3:19 / 3:01 / 2:02 / 1:39) verschieben.

Wer Whoops fallfreudiger mag, nimmt den Regler: bei 1,10–1,15× liegt die Whoop-Fallkurve im Referenzbereich (1,15×: nach
1 s 6,57 m/s und 3,98 m, Endgeschwindigkeit 7,3 m/s). Eine
Messung (Whoop aus 10 m fallen lassen, Video mit Zeitstempel) würde die Frage klären.

**Nachtrag 0.5.0 (korrigiert, §9):** Ein Creator, der selbst Whoops fliegt, wünschte sich sinngemäß etwas mehr Schwung im
Sturzflug. Mit dem gemessenen Umriss eines 65-mm-Whoops liegt die Draufsicht deutlich unter `2,5 × Stirnfläche`; seit
0.5.0 tragen die Whoop-Rahmen eine eigene `top_area_m2`, die Fallkurve liegt jetzt in der Mitte der Referenz.

## 4. Woher das „floaty“ kommt

1. **Minecraft-Maßstab.** Spieler fallen mit 0,08 Blöcken/Tick² (`Attributes.GRAVITY`) und 2 % Luftwiderstand je Tick
   (`LivingEntity.BASE_VERTICAL_AIR_DRAG = 0.98`), also mit **32 m/s²** am Anfang: nach 1 s 26 m/s und 14,6 m, nach 2 s
   50 m. Items und fallende Blöcke: 0,04 = 16 m/s². Eine echte 5″-Drohne fällt in derselben Sekunde 4,3 m. Wer an
   Minecraft gewöhnt ist, empfindet echte Schwerkraft neben Blöcken als langsam – die Physik der Drohne ist korrekt, der
   Vergleich im Kopf nicht.
2. **Echte FPV-Drohnen sind „luftig“.** Ein 5″ wiegt 17–22 kg je m² Widerstandsfläche (Phantom 3: 21 kg/m²) und kommt flach
   nur auf 16–19 m/s. Nach 2 s hat er zwei Drittel der Geschwindigkeit des freien Falls; ein Whoop schon nach 0,5 s nur
   80 %.
3. **Weitwinkel-Bild.** Die Kamera zeigt 104–136° horizontal (Videomodul); weit entfernte Blöcke wandern langsam durchs
   Bild, das Absinken wirkt kleiner als mit dem Standard-Sichtfeld 70 des Spielers.
4. **Andere Simulatoren** halten die Schwerkraft fest: die Entwickler von Liftoff („Gravity is a constant and should never
   be a variable in any simulator that aims for realism“) und Velocidrone („You won't ever find a gravity slider in
   velocidrone“) lehnen einen Schwerkraft-Regler ab ([Steam: Liftoff][liftoff-sliders]); Velocidrone bietet stattdessen
   Widerstandsregler für vorn, oben und seitlich ([Velocidrone-Einstellungen][velo]). Ein Liftoff-Entwickler führt den
   „Drop“-Eindruck auf das Idle zurück: Mindestgas 1100 statt 1000 verlängerte einen Fall von 8,4 auf 12,6 s
   ([Steam: Liftoff][liftoff-drop]). Propwash nutzt das Betaflight-Idle von 5,5 % und liegt bei der Drehzahl am unteren
   Rand (§3.1).

## 5. Korrekturen

Keine. Geprüft und unverändert: Motor-Idle 5,5 % (`PidProfile`), Schubanstieg beim Sinken (`DESCENT_GAIN`, `DESCENT_CAP`),
Vertikalfläche (`TOP_AREA_OPEN` 2,9, `TOP_AREA_DUCT` 2,5, Rahmendaten), Prop-Wash (`WASH_LOSS_*`, Wirbelring-Gewicht),
Rumpfwiderstand. Deshalb ändern sich keine Goldens, keine Kalibrierung, kein Schwebegas und keine Flugzeit; der
Owner-Testfall bleibt bei 3:19 Schweben / 3:01 Reisen / 2:02 gemischt / 1:39 aggressiv, Schwebegas 43,8 %.

## 6. Regler „Schwerkraft“

**Bedienung:** Tab „Simulation“, linke Spalte unter „Hardware-Grenzen“: „Schwerkraft: 1.00×“, 0,80–1,50× in 0,05er-Schritten.
Tooltip: „Faktor auf die Erdschwerkraft (9,81 m/s²). 1,0 = echte Erdschwerkraft. Höher: Die Drohne fällt schneller und
braucht mehr Gas zum Schweben. Ändert die Physik, Flüge zählen dann nicht für Bestenlisten. Werkbank und Übersicht rechnen
immer mit 1,0.“ (`propwash.drone.config.gravity`, `…gravity.tooltip`, de_de und en_us).

**Speicherung und Freigabe:** `DroneClientConfig.gravity` (Datei `drone.json`, auf 0,8–1,5 und 0,05er-Schritte
gerundet, ungültig → 1,0) → `SimTuning.gravityScale` (`sanitize` 0,8–1,5). Wie Crash-Toleranz, Wind und die übrigen
Physik-Optionen nur im Kreativmodus oder mit Kreativ-Brille wirksam (`PhysicsAccess.unlocked`, sonst gilt 1,0); ein Wert
≠ 1,0 zählt in `physicsModified()` – der Client meldet das wie bisher über `DroneStateC2S`, der Server wertet die Runde
nicht für Bestenliste und Geister (`RaceServer`), der Client-Renntracker zählt sie als unterstützt. Kein neues Netzpaket,
**Netzwerkprotokoll bleibt 8**, öffentliche API unverändert (**API_VERSION bleibt 1.4.0**). Wirkt sofort, auch im Flug
(`PilotSession.markConfigChanged` → `DroneSim.setTuning`).

**Was der Faktor ändert** (`g = 9,81 × gravityScale`, `DroneSim.gravity()`):

- Beschleunigung in `DroneSim.substep`, Normalkraft, Reibung und Aufrichten am Boden;
- Schwebegas-Schätzung (`updateHoverEstimate`: `ω_h·√Faktor`, Strom mit `OperatingPoints.hoverCurrent(p, Faktor)`), damit
  Failsafe-Landung, ALTHOLD/POSHOLD und GPS-Rettung mit dem richtigen Schwebegas starten; ändert sich der Faktor, wird
  die Schätzung sofort neu gerechnet;
- `NavHold` (Beschleunigungsmesser, Steig-/Sinkgrenzen, Neigung ↔ Beschleunigung) und `RescueAutopilot` (Schub/Gewicht
  = Analysewert / Faktor, Neigung ↔ Beschleunigung);
- Telemetrie „Schub/Gewicht“ (1,0 = Schweben).

**Was bei 9,81 bleibt:** G-Anzeige im OSD und die G-Last der Tricks (in Normal-g, wie ein echter Beschleunigungsmesser),
die Bezugsgrößen von Wirbelring und Wake-Feld (feste Luftrahmen-Skala, der stärkere Schub wirkt über die echte Schubkraft),
`BuildAnalysis` mit Werkbank, Konfigurator-Übersicht, Item-Tooltip, `BuildStats` der API, Flugzeiten und Motorklang. Die
Werkbank beschreibt das echte Teil; sie soll nicht von einer persönlichen Client-Einstellung abhängen, und Server/API
kennen diese Einstellung nicht.

**Wirkung** (gleiche Messung wie §2):

| Aufbau | Faktor | Schwebegas % | v nach 1 s [m/s] | Höhe nach 1 s [m] | flach, Idle [m/s] | Nase unten [m/s] | Steigen Vollgas [m/s] |
|---|---|---|---|---|---|---|---|
| Whoop 65 | 0,8 | 44,5 | 5,10 | 2,93 | 6,1 | 9,6 | 8,1 |
| Whoop 65 | 1,0 | 50,9 | 5,98 | 3,55 | 6,8 | 10,7 | 7,6 |
| Whoop 65 | 1,2 | 57,0 | 6,76 | 4,12 | 7,5 | 11,8 | 7,0 |
| Whoop 65 | 1,5 | 65,4 | 7,79 | 4,92 | 8,3 | 13,2 | 6,1 |
| Freestyle 5″ | 0,8 | 19,0 | 6,64 | 3,41 | 14,1 | 24,1 | 36,0 |
| Freestyle 5″ | 1,0 | 22,0 | 8,24 | 4,27 | 15,9 | 27,0 | 35,4 |
| Freestyle 5″ | 1,2 | 24,7 | 9,79 | 5,12 | 17,5 | 29,7 | 34,9 |
| Freestyle 5″ | 1,5 | 28,5 | 12,00 | 6,36 | 19,7 | 33,3 | 34,0 |

Endgeschwindigkeiten wachsen mit √Faktor, die Fallhöhe der ersten Sekunde fast linear, das Schwebegas mit dem nötigen
Schub (Schub ∝ Faktor, Drehzahl ∝ √Faktor). Aufbauten mit wenig Schub/Gewicht können bei 1,5× nicht mehr schweben – das ist
gewollt und physikalisch.

## 7. Prüfung

- Faktor 1,0: `tools/calibration.sh` mit Just-More-Parts-Daten – `calibration.txt` und `motion.txt` (alle
  Einzeltausch-Varianten aller 27 Presets, Bewegungs-Fingerabdrücke) byte-gleich zum Stand vor der Änderung.
- JUnit `sim.GravityScaleTest`: Standard 1,0 = 9,81 m/s² und bitgleiche Flugbahn mit explizitem 1,0; freier Fall bei 0,8/1,2/1,5
  exakt `g·Faktor`; Schwebegas bei 1,2 höher, Schub/Gewicht im Schweben 1,0, Schätzung trifft das gemessene Schwebegas;
  Schub im Arbeitspunkt × 1,2; Fall bei 1,2 schneller, Endgeschwindigkeit × √1,2; Faktor im Flug ändern rechnet die
  Schätzung sofort neu; ALTHOLD hält bei 1,4× die Höhe; Klemmen, Runden und `physicsModified`.
- JUnit `sim.FallCharacterizationTest`: Messtabelle (§2) bei 1,0 und 1,2 mit Plausibilitätsprüfungen.
- Selbsttest „drone: Schwerkraft-Regler wirkt im Flug“ (beide Loader): Faktor 1,3 im Flug → `DroneSim.gravity()` 12,753 m/s²,
  Schwebegas-Schätzung steigt, veränderte Physik gemeldet, zurück auf 1,0. Bildschirmfotos `configurator_simulation.png`,
  `configurator_simulation_gravity.png` und `configurator_simulation_gravity_de.png` (Regler auf 1,25× mit Tooltip; seit
  0.5.0 ersetzt durch `configurator_world_rules_gravity.png` und `configurator_world_rules_gravity_de.png`).

## 8. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Keine Modellkorrektur (0.4.0) | Alle Werte im Bereich der Referenzen; nur der Whoop an der Kante, ohne Messdaten | Whoop-`A_y` senken (verschiebt Topspeed und kalibrierte Flugzeiten auf Basis einer Schätzung) – in 0.5.0 mit dem gemessenen Rahmen-Umriss nachgeholt (§9) |
| Standard bleibt 1,0 = 9,81 m/s² | Vorgabe; echte Physik als Grundlage, wie Liftoff und Velocidrone | Standard auf 1,1 (wäre eine versteckte Physikänderung) |
| Regler 0,8–1,5×, Schritt 0,05 | Vorgabe; 0,05 trifft die Referenzbreite (±5–10 %) | feinere Schritte |
| Gesperrt wie die übrigen Physik-Optionen, ≠ 1,0 = veränderte Physik | Fairness in Rennen; gleiches Muster wie Crash-Toleranz und Wind, ohne neues Paket | eigene Server-Regel (kein Bedarf: veränderte Physik zählt ohnehin nicht für Bestenlisten) |
| Werkbank, Übersicht, API, Flugzeiten mit 1,0 | Sie beschreiben das echte Teil; Server und API kennen die Client-Einstellung nicht | Analyse mit Faktor (Werkbank-Werte je Spieler verschieden, `BuildStats` nicht mehr reproduzierbar) |
| Schwebegas-Schätzung, ALTHOLD/POSHOLD, GPS-Rettung, Failsafe mit Faktor | Sonst sinken Failsafe-Landung und Höhenhalten bei mehr Schwerkraft ab | Schätzung bei 1,0 lassen |
| G-Anzeige in Normal-g | Wie ein echter Beschleunigungsmesser: im Schweben bei 1,2× zeigt er 1,2 g | auf die eingestellte Schwerkraft normieren |
| Wirbelring- und Wake-Bezug bei 9,81 | Feste Luftrahmen-Skala; der größere Schub bei mehr Schwerkraft wirkt über die echte Schubkraft | Bezug mit Faktor (der Wash würde bei mehr Schwerkraft relativ schwächer) |
| Netzwerkprotokoll 8 und API 1.4.0 bleiben | Keine neuen synchronisierten Daten, keine API-Änderung | – |
| Messwerkzeug als JUnit, Ausgabe über `tools/calibration.sh` | Wiederholbar, gleiche Infrastruktur wie Kalibrierung und Bewegungs-Goldens | eigenes Skript |

## 9. Nachtrag 0.5.0: Whoop-Rahmen mit Draufsicht nach dem Umriss

Entscheidung des Besitzers vom 05.10.: nur die Widerstandsdaten der Whoop-Rahmen (65/75 mm, eingebaut und Just More
Parts) Richtung gemessener Werte korrigieren, 5″ und 7″ bleiben unverändert. Flug-Engine unverändert; geändert sind nur
Daten (`aero.top_area_m2`).

**Umriss (gemessen):** BetaFPV Air65 II: 82,6 × 82,6 mm, Duct-Innendurchmesser 33 mm, Radstand 65 mm ([BF-Air65II]).
Vier Ducts mit 36,6 mm Außendurchmesser auf 46 mm Motorabstand = 0,0042 m², dazu die Mitte zwischen den Ducts zu drei
Vierteln gefüllt (Canopy, FC, Akku) 0,0008 m² → **Umriss ≈ 0,0050 m²** (die Abschätzung in §3.8 nahm den umschreibenden
Rahmen 0,0055–0,0069 m²). Methode wie beim Phantom 3 (§3.2): feste Fläche (Duct-Ringe, Motoren, Streben, Canopy, stehende
Blätter ≈ 0,0035 m²) × C_D ≈ 1,38 → 0,0048 m², mit der Bremswirkung langsam drehender Props bis +50 % → 0,0048–0,0072 m²,
Mitte **C_D·A_y ≈ 0,0065 m²**. Mit dem Rahmen-C_D 1,1 bleibt die Draufsicht `top_area_m2 = 0,0065/1,1 ≈ 0,0059 m²`
(1,18 × Umriss). Für die anderen Whoops dieselbe Regel `1,18 × Umriss` mit derselben Duct-Geometrie (Duct außen = Prop
+ 5,6 mm, Mitte zu drei Vierteln gefüllt):

| Rahmen | Umriss | `top_area_m2` vorher (2,5 × Stirnfläche) → jetzt | C_D·A_y vorher → jetzt |
|---|---|---|---|
| `propwash:whoop_65` (31-mm-Props) | 0,0050 m² | 0,00875 → **0,0059** | 0,0096 → 0,0065 m² |
| `justmoreparts:whoop_65_lite` (31 mm, Stirnfläche 0,0034) | 0,0049 m² | 0,0085 → **0,0058** | 0,0094 → 0,0064 m² |
| `justmoreparts:whoop_75_duct` (40 mm) | 0,0074 m² | 0,01125 → **0,0087** | 0,0124 → 0,0096 m² |

Unverändert: Stirnfläche und C_D (Vorwärtsflug ohne Neigung, Nase-unten-Sturz), alle Cinewhoops, 5″, 7″, Race, X-Class
und der 85-mm-Whoop von Just More Parts (nicht Teil der Entscheidung; er liegt mit 7,5 m/s Endgeschwindigkeit ebenfalls
eher am Widerstands-Rand, ein Umriss-Wert wäre ≈ 0,0109 m², Topspeed dann 63 statt 57 km/h).

**Geänderte Zahlen** (`tools/calibration.sh`, Schwerkraft 1,0; vorher → nachher):

| Aufbau | v nach 0,5/1/2/3 s [m/s] | Höhe nach 1/2 s [m] | flach, Idle [m/s] | kopfüber Idle / Vollgas [m/s] | Steigen [m/s] | Punch 1 s → v [m/s] |
|---|---|---|---|---|---|---|
| `propwash:whoop_65` | 3,94/5,98/6,74/6,79 → **4,10/6,71/8,11/8,26** | 3,55/10,07 → 3,79/11,43 | 6,8 → **8,3** | 6,9/11,3 → 8,3/13,5 | 7,6 → 8,9 | 7,3 → 8,2 |
| `justmoreparts:whoop75_1s` | 3,81/5,71/6,37/6,41 → **3,93/6,19/7,20/7,29** | 3,42/9,60 → 3,58/10,47 | 6,4 → **7,3** | 6,5/12,2 → 7,4/13,6 | 9,7 → 10,8 | 9,7 → 10,7 |

Referenz Whoop 65 (§3.5): 4,2–4,4 / 6,2–7,0 / 7,0–8,5 / 7,1–8,7 m/s und 3,8–4,1 / 10,6–12,1 m – jetzt im Bereich
(nach 0,5 s mit dem Auslauf-Abzug von 0,2 m/s, die Höhe nach 1 s mit 3,79 m an der Unterkante). Nase unten bleibt bei
10,7 bzw. 10,2 m/s.

| Aufbau | Topspeed | Schweben | Reisen | gemischt | aggressiv |
|---|---|---|---|---|---|
| `propwash:whoop_65` (Preset) | 36 → **41 km/h** | 3:54 | 3:30 → 3:38 | 2:45 → 2:47 | 2:18 → 2:19 |
| Owner-Testfall (0702 27000 KV, 31 mm 4-Blatt, 1S 300 LiHV) | 41 → 47 km/h | 3:19 | 3:01 → 3:07 | 2:02 → 2:03 | 1:39 → 1:40 |
| Owner 23000 KV | 37 → 43 km/h | 3:19 | 3:01 → 3:07 | 2:21 → 2:23 | 2:00 → 2:01 |
| Owner 3-Blatt | 41 → 47 km/h | 3:42 | 3:23 → 3:30 | 2:18 → 2:19 | 1:55 → 1:57 |
| Owner 2-Blatt | 40 → 47 km/h | 3:47 | 3:29 → 3:36 | 2:28 → 2:30 | 2:05 → 2:06 |
| Owner analog | 41 → 47 km/h | 4:46 | 4:08 → 4:20 | 2:26 → 2:28 | 1:57 → 1:59 |
| `justmoreparts:whoop75_1s` | 41 → **45 km/h** | 7:04 | 5:58 → 6:11 | 4:24 → 4:29 | 3:29 → 3:31 |

Schwebegas, Schweben, Ströme beim Schweben und Vollgas, Temperaturen und Schub/Gewicht bleiben gleich; Topspeed und
Reisen steigen, weil die Draufsicht im geneigten Vorwärtsflug mitwirkt. Referenz Topspeed 65 mm 35–55 km/h, 75 mm
45–65 km/h (`builds.md` §0, Schätzungen). Die Rotations-Dämpfung durch den Rumpf (`C_D·A_y·L³/4`) sinkt mit; sie ist beim
Whoop gegen den Regler klein.

**Bewusst angepasste Prüfwerte:** Propwash `FlightCharacterizationTest` Whoop-Topspeed 30–40 → 30–46 km/h,
`TopSpeedTest` Geradeaus 30–42 → 30–50 km/h, Vollgas-Sturzflug Whoop bis 1,2 × Geradeaus (andere Klassen 1,15 ×) und
60 km/h (vorher 50): Mit Schub/Gewicht 3,2 zieht im Sturzflug die Schwerkraft relativ viel mehr mit als beim 5″
(`(T + W)/√(T² − W²)` = 1,38 gegen 1,11); `OpenLoopPhysicsTest` (flach 6,8 → 8,3 m/s, Punch 8,1 → 9,3 m/s),
`DerivationTest` (Topspeed, Neigung, Flugzeiten des Whoops), `FallCharacterizationTest` prüft jetzt den Whoop gegen die
Referenz (nach 1 s 6,2–7,0 m/s, flach 7,1–8,7 m/s) und misst zusätzlich `justmoreparts:whoop75_1s`. Just More Parts: Korridor
Whoop-Topspeed 35–60 → 35–65 km/h (75 mm auf 2S jetzt 64 km/h), Aussage des 0702-27000-KV-Motors „mehr Topspeed (48 statt
43 km/h)“ (vorher 41 statt 38). Goldens: nur `whoop_65` (Propwash `airframe_golden.txt`, `builtin_regression.txt`,
`builtin_definitions.txt`); in `motion.txt` ändern sich nur Zeilen mit `propwash:whoop_65`,
`justmoreparts:whoop_65_lite` und `justmoreparts:whoop_75_duct`.

[BF-Air65II]: https://betafpv.com/products/air65-ii-brushless-whoop-frame

## 10. Nachtrag 08.10. (0.5.0): die eigentliche Ursache – Whoop-Widerstand und Blätter im Sinkflug

Anlass: Spieler finden die Drohnen weiterhin zu floaty. Ein Pilot, der eine echte BetaFPV Meteor75 Pro II fliegt,
flog dieselbe Drohne in Propwash sofort sauber, in echt fällt sie ihm beim Gaswegnehmen aber deutlich schneller, als er
es aus Propwash gewohnt ist. Auftrag: die Welt-Regel Schwerkraft auf 0–1000 % erweitern (§10.6) und die echte Ursache
mit Zahlen finden; das Flugmodell nur dort ändern, wo die Zahlen es belegen. **Ergebnis:** zwei Fehler, beide machen
Drohnen beim Sinken zu langsam – (1) der Widerstand von oben der Whoop-Rahmen war 1,4–2,2-mal zu hoch (Daten),
(2) im angetriebenen Sinkflug bremsten die Prop-Blätter doppelt (Flug-Engine). Alles andere stimmt (§10.2).

### 10.1 Messaufbau und Referenzmodell

- JUnit `sim.ThrottleDropCharacterizationTest` (Ausgabe `throttle_drop.txt` über `tools/calibration.sh`): wie im Spiel
  (`new SimTuning()`, 144 fps, Akku 80 %, 3000 m Höhe, kein Wind), 3 s Schweben im Winkelmodus mit Höhenregler, dann
  ACRO, Sticks mittig, Gas auf 0 bzw. 20 %. Gemessen: mittlere Beschleunigung nach 0,1 und 0,25 s, Sinkgeschwindigkeit
  nach 0,5 und 1 s, Zeit für 10 m, Endgeschwindigkeit (Mittel 28–30 s), Kraftanteile, Wirbelring-Gewicht und
  Sinkverhältnis; dazu 1 s Vollgas aus dem Schweben (Punch) und der Topspeed wie `TopSpeedTest`.
- Aufbauten: ein Meteor-ähnlicher Whoop aus Herstellerdaten (Rahmen 80 mm, 1102 21000 KV, 45-mm-Dreiblatt, 1S 580 LiHV,
  O4 Wide; als Test-Fixture, einmal mit der bisherigen Umriss-Regel 0,011 m², einmal mit der Methode aus §10.3
  0,0058 m²), eingebaut Whoop 65, Cinewhoop 3″ und Freestyle 5″, Just More Parts Whoop 75 1S und Whoop 85 2S.
- **Referenzmodell** (im selben Test, eindimensional, Schritt 0,5 ms, unabhängig von Rotor- und Rumpfmodell der Sim):
  - Schub im Stand je Gasstellung aus dem Motor-/Propmodell (`TestStand`, Betaflight-Mischung 5,5 % Idle + 94,5 % × Gas).
    Dieses Modell trifft die veröffentlichte Lastkennlinie des 1102-21000-KV-Motors mit 45-mm-Dreiblatt auf ±7 %
    (`WhoopThrustTableTest`, `realism/calibration.md`).
  - Motorträgheit mit der physikalischen Zeitkonstante `motorTauPhys` (ohne die 40-ms-Kappe der Sim).
  - Rotor im Sinkflug nach Windkanal-Messungen kleiner Propeller bei fester Drehzahl ([Shetty & Selig 2011][shetty],
    APC Thin Electric 10×5, zeitgemittelter Schub, J = −0,15 … −0,73, statisches C_T 0,095 angenommen), als Faktor über
    dem Sinkverhältnis x = Sinkgeschwindigkeit / induzierte Geschwindigkeit des aktuellen Schubs: 0,92 bei x = 0,6,
    0,79 bei 1,07 (Minimum im Wirbelring), 1,04 bei 1,5, 1,39 bei 2,1, 1,93 bei 2,96 (Ende der Messung). Darüber, also
    im Leerlauf-Fall, zählt die Bremswirkung der drehenden Blätter (größerer Wert von beiden).
  - Rumpf 1,2 × Rumpffläche, drehende Blätter 1,66 × Blattfläche (§10.3), Schwerkraft 9,81 m/s².
  - Bandbreite: alle Flächen × 0,8 bzw. × 1,25.
  - Grenzen: eine Propellerfamilie (10″, kleine Reynoldszahl), das statische C_T nicht gemessen (±10 % auf den Faktor);
    ein Stick-Wert weit unter dem Schwebegas (Whoop 65 bei 20 %) liegt jenseits der Messung.

### 10.2 Geprüft und richtig

| Prüfpunkt | Befund |
|---|---|
| Zeitmaßstab | `PilotSession` steppt mit der echten Frame-Zeit (`Util.getNanos`) in festen 0,5-ms-Schritten; Frames über 100 ms werden gekappt, Sim-Sekunden sind echte Sekunden bis hinunter zu 10 fps |
| Schwerkraft | je Substep `a = F/m − g` mit 9,81 × Faktor; freier Fall exakt `g·t` (`GravityScaleTest`, auch bei 0–1000 %) |
| Maßstab | 1 Block = 1 m, die Sim rechnet in Blockkoordinaten |
| Schwebegas | Meteor 30,0 % bei 80 % Akku (28 % voll), echt ≈ 28 % ([OL-M75]); Whoop 65 50,7 %, Cinewhoop 34,2 %, 5″ 22,0 %; Referenz jeweils ±0,5 Punkte |
| Idle | 5,5 % DShot-Idle, im Fall etwa 3700 U/min (Meteor), Idle-Schub 1,4–5,7 % des Gewichts (§3.1) |
| Gaskurve | Mitte 0,5, Expo 0, linear wie der Betaflight-Standard und der Meteor-Dump |
| Schub bei wenig Drehzahl | Meteor-Prüfstand bei 30 % Gas +6 %, ab 40 % −7 … 0 %; das echte C_T steigt von 30 auf 60 % Gas um 4 % (Reynoldszahl), bei 20 % Gas schätzt Propwash den Schub wenige Prozent zu hoch – zu klein für eine Änderung (§10.8) |
| Schub bei Vollgas (0.5.0) | Kennlinie auf ±7 %, unverändert |
| Motorträgheit | Whoops 45–50 ms physikalisch, in der Sim auf 40 ms gekappt, dazu etwa 10 ms RC-Glättung des Gaskanals wie in Betaflight; nach dem Gaswegnehmen 0,59–0,79 g Beschleunigung nach 0,1 s (Referenz 0,70–0,88 g), nach 0,25 s 0,80–0,88 g (0,85–0,93 g) |
| Airmode bei 0 % | hält Idle und Regelung wie Betaflight |
| Wirbelring, Prop-Wash | Schubgewinn im Sinkflug × Wirbelring-Verlust liegt im Wirbelring bis zu 10 % über, im Windmühlen-Bereich (x > 2) bis zu 33 % unter der Windkanal-Kurve; den fehlenden Anstieg dort ersetzt der Blätter-Anteil des Widerstands von oben (§10.4) – kein eindeutiger Fehler |
| Bodeneffekt | Cheeseman-Bennett mit Mehrrotor-Gliedern, Kappe 1,3, Reichweite etwa ein Rotordurchmesser (Whoop wenige Zentimeter) |
| Auftrieb | Translational Lift nur mit Querströmung, im senkrechten Sinkflug 0 |

### 10.3 Fehler 1: Widerstand von oben der Whoops (Daten)

Die Whoop-Regel aus §9 („1,18 × Umriss“) zählt die Prop-Scheiben in den Ducts wie eine volle Platte. Pro Umriss ergibt
das `C_D·A_y` = 1,30 – der bewährte Cinewhoop 3″ (Fallkurve und Endgeschwindigkeit seit 0.4.0 im Referenzbereich) liegt
bei 0,76. Eine Methode für alle Klassen: **`C_D·A_y = 1,2 × Rumpf + 1,66 × Blattfläche`** – Rumpf = Draufsicht ohne die
offene Prop-Scheibe (Arme bzw. Duct-Ringe, Motorglocken, Streben, Mitte mit Platten, Canopy, Akku), Blattfläche =
Blattzahl × Blattfläche (Solidität 0,13–0,16 bei 5–7″, 0,2 bei 3″, 0,22–0,4 bei Whoop-Props), 1,66 = stehende Blätter
(Platte 1,2) × 1,4 für die Drehung, angepasst am 5″-Freestyle, in der Spanne „drehende Props bis +110 %“ ([hammer]).

| Aufbau | Rumpf m² | Blätter m² | Methode `C_D·A_y` | Propwash vorher | nachher |
|---|---|---|---|---|---|
| Freestyle 5″ (eingebaut) | 0,0141 | 0,0085 | 0,031 | 0,031 (Anpassung) | unverändert |
| Long Range 7″ | 0,0192 | 0,0090 | 0,038 | 0,042 (+10 %) | unverändert |
| Race 5″ | 0,0110 | 0,0066 | 0,024 | 0,020 (−19 %) | unverändert |
| Cinewhoop 3″ | 0,0109 | 0,0036 | 0,019 | 0,020 (+5 %) | unverändert |
| Whoop 65 (eingebaut) | 0,0021 | 0,0012 | 0,0045 | 0,0065 (+44 %) | **0,0045** (`top_area_m2` 0,0059 → 0,0041) |
| Just More Parts Whoop 65 Lite | 0,0019(5) | 0,0012 | 0,0043 | 0,0064 | **0,0043** (0,0058 → 0,0039) |
| Just More Parts Whoop 75 | 0,0025 | 0,0013 | 0,0051 | 0,0096 (+88 %) | **0,0051** (0,0087 → 0,0046) |
| Just More Parts Whoop 85 | 0,0033 | 0,0019 | 0,0071 | 0,0154 (+117 %) | **0,0072** (2,5 × Stirnfläche → 0,0065) |
| Meteor-ähnlicher Whoop (Herstellerdaten) | 0,0034 | 0,0014 | 0,0064 | 0,0121 (+89 %) | Empfehlung `top_area_m2` 0,0058 |

Die vier bewährten Klassen liegen −19 … +10 % um die Methode, alle Whoops +44 … +117 % darüber; beim 85-mm-Whoop lag
`2,5 × Stirnfläche` sogar über einer vollen Platte seines Umrisses (`1,17 × 0,0092 m²`). Unabhängige Bestätigung: mit
dem Umriss-Wert erreicht der Meteor-Whoop 56 km/h, mit dem Methodenwert 69 km/h; ein Testbericht schätzt die echte
Meteor75 Pro II auf rund 80 km/h ([OL-M75], ohne GPS). Rumpf- und Blattflächen sind aus Fotos und Maßen geschätzt
(±25 %, das ist die Bandbreite der Referenz).

### 10.4 Fehler 2: Blätter bremsen im angetriebenen Sinkflug doppelt (Flug-Engine)

`A_y` enthält den Anteil der drehenden Blätter – richtig, wenn die Props im Leerlauf von unten angeströmt werden
(Windmühlen-Bremse; dort gibt das Rotormodell nur 1,3 × Idle-Schub). Sinkt die Drohne aber mit Gas langsamer als etwa
das 1,5-fache der induzierten Geschwindigkeit, rechnet das Rotormodell die Kräfte an den Blättern schon selbst
(Schubgewinn bis +30 % aus dem steileren Anstellwinkel, Wirbelring-Verlust). Der Blätter-Anteil des Rumpfwiderstands kam
obendrauf: bei 20 % Gas 9–13 % des Gewichts zusätzliche Bremse, Sinken 4–15 % langsamer als die Referenz.

Korrektur (`DroneSim.descentTopScale`, `BodyAero.drag` mit Faktor auf die Draufsicht): Wird die Drohne von unten
angeströmt (Sinkflug entlang der Hochachse) und ist der 3D-Modus nicht aktiv, entfallen
`BLADE_DRAG_SHARE` = 40 % des Widerstands von oben, solange das Sinkverhältnis (Sinkgeschwindigkeit / induzierte
Geschwindigkeit des aktuellen Gesamtschubs) unter `WINDMILL_SINK_LOW` = 1,5 liegt; bis `WINDMILL_SINK_HIGH` = 3,0 blendet
der Anteil weich wieder ein (Smoothstep). 40 % = Blätteranteil nach der Methode (0,31–0,47 je Klasse, Mittel 0,40);
1,5 = Ende des Wirbelrings in der Windkanal-Kurve, 3,0 = Ende der Messung. Unverändert: Leerlauf-Fall (Sinkverhältnis
weit über 3), Steigen, Vorwärtsflug und Topspeed (Anströmung von oben), Schweben, Werkbank, Flugzeiten, 3D-Modus.

### 10.5 Vorher/nachher gegen die Referenz

Beschleunigung nach unten in m/s² (freier Fall 9,81), Geschwindigkeiten in m/s, Zeiten in s; Referenz in Klammern mit
Bandbreite. Freier Fall: nach 1 s 9,81 m/s, 10 m in 1,43 s.

| Aufbau | Gas | a 0,25 s | v nach 1 s | 10 m | Endgeschwindigkeit |
|---|---|---|---|---|---|
| Meteor-ähnlich (0,011 → 0,0058) | 0 % | 7,76 → 7,86 (8,34) | 6,67 → 7,59 (7,81) | 1,83 → 1,67 (1,64; 1,61–1,68) | 8,4 → 11,6 (11,8; 10,5–13,2) |
| | 20 % | 3,34 → 3,35 (3,55) | 3,48 → 3,87 (4,22) | 2,71 → 2,40 (2,28) | 5,5 → 8,5 (8,1; 7,6–8,4) |
| Whoop 65 | 0 % | 8,20 → 8,27 (8,48) | 6,71 → 7,30 (7,40) | 1,83 → 1,72 (1,70; 1,65–1,76) | 8,3 → 9,9 (10,0; 8,9–11,2) |
| | 20 % | 6,21 → 6,27 (6,43) | 5,50 → 6,26 (6,33) | 2,09 → 1,90 (1,87) | 7,1 → 8,5 (jenseits der Messung) |
| Cinewhoop 3″ | 0 % | 8,46 → 8,48 (8,94) | 8,24 → 8,25 (8,49) | 1,58 → 1,58 (1,55) | 14,9 → 14,9 (15,5; 13,9–17,4) |
| | 20 % | 4,67 → 4,68 (4,99) | 5,16 → 5,30 (5,45) | 2,06 → 2,01 (1,98) | 10,6 → 11,8 (11,0; 10,3–11,6) |
| Freestyle 5″ | 0 % | 8,65 → 8,68 (9,09) | 8,24 → 8,26 (8,53) | 1,58 → 1,58 (1,55) | 15,9 → 15,9 (16,5; 14,7–18,4) |
| | 20 % | 1,22 → 1,22 (1,33) | 1,26 → 1,27 (1,47) | 3,85 → 3,76 (3,49) | 7,4 → 8,3 (8,4; 8,2–8,6) |
| Just More Parts Whoop 75 1S | 0 % | 7,82 → 7,96 (8,31) | 6,19 → 7,24 (7,42) | 1,94 → 1,72 (1,70) | 7,3 → 10,0 (10,2; 9,1–11,4) |
| | 20 % | 3,89 → 3,92 (4,11) | 3,94 → 4,53 (4,74) | 2,67 → 2,28 (2,25) | 5,1 → 7,6 (7,2; 6,8–7,5) |
| Just More Parts Whoop 85 2S | 0 % | 7,81 → 7,95 (8,33) | 6,26 → 7,45 (7,70) | 1,92 → 1,69 (1,66) | 7,5 → 11,0 (11,3; 10,1–12,6) |
| | 20 % | 2,42 → 2,43 (2,59) | 2,56 → 2,85 (3,10) | 3,15 → 2,70 (2,57) | 4,8 → 7,5 (7,5; 7,1–7,8) |

Schwebegas (vorher = nachher, Referenz): Meteor 30,0 (30,1), Whoop 65 50,7 (51,1), Cinewhoop 34,2 (34,2),
5″ 22,0 (22,1), Whoop 75 32,3 (32,4), Whoop 85 25,7 (25,8) %. Punch (Steiggeschwindigkeit nach 1 s Vollgas): Meteor
12,2 → 14,7, Whoop 65 8,2 → 9,0, Whoop 75 10,7 → 13,1, Whoop 85 14,4 → 19,0 m/s, Cinewhoop und 5″ unverändert (18,0 und
34,2). Topspeed (Werkbank): Whoop 65 41 → 47 km/h, Whoop 75 45 → 57, Whoop 85 57 → 77; Meteor-ähnlich geradeaus mit
frischem Akku (`TopSpeedTest`) 56 → 69 km/h; Cinewhoop, 5″ und 7″ unverändert. Mit dem Umriss-Wert 0,011 und nur der Engine-Korrektur
sinkt der Meteor-Whoop bei 20 % Gas mit 6,7 statt 5,5 m/s.

Nach der Korrektur liegen alle Endgeschwindigkeiten im Leerlauf in der Bandbreite der Referenz, die Zeiten für 10 m
±0,03 s daneben, das Sinken mit 20 % Gas −1 … +7 %. In den ersten 0,1–0,25 s bleibt die Sim 5–10 % unter der
Referenz: RC-Glättung und die Antwort des Reglers, die das Referenzmodell nicht hat.

### 10.6 Welt-Regel Schwerkraft 0–1000 %

- `propwash:gravity_scale` 0–1000 % in 1-%-Schritten (vorher 80–150 % in 5er-Schritten), Standard bleibt 100 %
  (= 9,81 m/s²). Im Tab „Welt-Regeln“ ein Regler mit Tippen (Spanne 1000 = Grenze für Regler, darüber wäre es ein
  Zahlenfeld), per `/gamerule` und für die Website über `tools/export_rules.sh`. `SimTuning.GRAVITY_MIN/MAX` 0/10.
- Jeder Wert ≠ 100 zählt weiter als veränderte Physik (`PhysicsRules.modified`): keine Bestenliste, Runden nur mit
  „Runden mit Flughilfe speichern“ in der Wertung „Mit Flughilfe“.
- 0 % ist schwerelos: Idle-Schub hebt die Drohne langsam. Damit nichts durch null teilt, rechnen Schub/Gewicht
  (Telemetrie, Höhenhalten), ALTHOLD/POSHOLD, GPS-Rettung und die Tragkraft des Greifers mit mindestens 1 % als Bezug
  (`SimTuning.referenceGravity`); bei 100 % ist das bitgleich. Über dem Schub/Gewicht der Drohne hebt sie nicht ab.
- Netzwerkprotokoll und API bleiben: der Wert reist wie bisher als Zahl in `propwash:world_rules`; 0.5.0 ist noch
  nicht veröffentlicht, kein älterer Client kennt die Regel.

### 10.7 Wahrnehmung (keine Physik)

- **Minecraft-Maßstab:** Spieler fallen mit anfangs 32 m/s² (nach 1 s 14,6 m); eine Drohne nach dem Gaswegnehmen 3,9–4,3 m.
- **Sichtfeld:** die O4 Wide zeigt 159° diagonal, in Propwash 127° horizontal; das Weitwinkel-Bild lässt Sinken und
  Fallen kleiner wirken als das Spieler-Sichtfeld 70.
- **Größe der Drohne im Bild:** auf Sicht ist ein 8-cm-Whoop vor 1-m-Blöcken winzig, Sinkgeschwindigkeiten sind schwer
  zu schätzen.
- **Akku und Tuning:** ein echter Akku sackt im Flug ab, das Schwebegas wandert nach oben (Propwash rechnet das mit
  „Spannungseinbruch“); die echte Meteor fliegt mit einem deutlich schärferen Werks-Tuning als das abgeleitete
  Propwash-Tuning.

Daran wird die Physik nicht angepasst.

### 10.8 Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Whoop-Draufsicht nach „1,2 × Rumpf + 1,66 × Blattfläche“ (Daten von Whoop 65, Just More Parts Whoop 65 Lite/75/85) | Eine Methode, die 5″, 7″, Race und Cinewhoop auf −19 … +10 % trifft; die Umriss-Regel zählte die offenen Duct-Scheiben als Platte, beim 85 mm über einer vollen Platte | Umriss-Regel lassen (Endgeschwindigkeit der Whoops 17–34 % unter der Referenz); alle Rahmen neu herleiten (5″/7″/Cinewhoop liegen schon im Bereich) |
| Engine: Blätter-Anteil 40 % im angetriebenen Sinkflug ausblenden (x < 1,5, weich bis 3,0) | Doppelte Zählung der Blätter, 9–13 % des Gewichts bei 20 % Gas; Leerlauf-Fall, Vorwärtsflug und Topspeed bleiben | Blätter-Anteil immer ausblenden (5″-Topspeed stiege weit über die echten 150 km/h); Rotor-Kurve im Sinkflug durch die Windkanal-Kurve ersetzen (ein Propeller-Datensatz, ±10 % Normierung, ändert Wirbelring und 3D-Modus mit) |
| Anteil 0,4 und Grenzen 1,5/3,0 aus Methode und Messung, nicht angepasst | Herleitung statt Abgleich; Ergebnis −1 … +7 % zur Referenz | auf die Referenz einpassen (Referenz selbst ±10 %) |
| Nicht im 3D-Modus | umkehrbare Props haben eigene Anström-Logik | auf beide Richtungen erweitern (keine Daten) |
| Rotormodell im Wirbelring, C_T bei kleiner Drehzahl, Motor-Kappe 40 ms, Idle 5,5 % unverändert | im Rahmen der Messunsicherheit bzw. richtig (§10.2) | – |
| Schwerkraft 0–1000 % in 1-%-Schritten, Standard 100 % | Vorgabe; feines Einstellen, Tippen im Regler | 5er-Schritte; Standard über 100 % (versteckte Physikänderung) |
| Bezug mindestens 1 % für Regler und Quotienten | schwerelos ohne Division durch null, bei 100 % bitgleich | 0 % verbieten (Vorgabe 0–1000) |
| Kein neues Netzwerkprotokoll, API unverändert | gleiche Daten, nur größerer Bereich, 0.5.0 unveröffentlicht | Protokoll erhöhen |
| Werkbank und Flugzeiten weiter mit 100 % | beschreiben das echte Teil (§6) | – |
| Meteor-Daten nur als Test-Fixture aus Herstellerangaben | das Add-on ist kein Teil von Propwash | Add-on als Abhängigkeit |

Geänderte Prüfwerte und Goldens: Whoop 65 `airframe_golden.txt`, `builtin_definitions.txt`, Bewegungs-Goldens aller
eingebauten Drohnen im Sinkflug (`builtin_regression.txt`: acro, angle, drop, worn); `DerivationTest` Whoop-Topspeed
41,5 → 47,2 km/h, Neigung 53,1 → 59,3°, Reisen/gemischt/aggressiv 3,63/2,78/2,32 → 3,71/2,81/2,34 min; `TopSpeedTest`
Whoop 30–50 → 30–56 km/h, Sturzflug bis 1,25 × und 68 km/h (vorher 1,2 × und 60); `FlightCharacterizationTest`
Whoop-Topspeed 30–46 → 30–52 km/h; `OpenLoopPhysicsTest` flach 8,3 → 10,0 m/s, Punch 9,3 → 10,3 m/s;
`FallCharacterizationTest` Whoop nach 1 s 6,2–7,0 → 6,9–7,9 m/s, Endgeschwindigkeit 7,1–8,7 → 8,9–11,2 m/s (Referenz
§10.5). In `calibration.txt` ändern sich nur die Whoop-Zeilen (Topspeed, Reisen +2–6 %, gemischt und aggressiv +1 %),
in `motion.txt` alle Bauarten in den Sinkflug-Abschnitten.

### 10.9 Offene Punkte

- Meteor-ähnliche Whoops in Add-ons, die die Umriss-Regel übernommen haben: `top_area_m2` nach §10.3 neu rechnen
  (Meteor-Daten 0,011 → 0,0058 m²).
- Toothpicks (Just More Parts, Standard 2,9 × Stirnfläche): die Methode ergibt für den 2″-Toothpick etwa 30 % weniger
  Widerstand von oben (0,0091 → 0,0063 m²); nicht Teil dieser Korrektur, für 0.5.1 mit der vollständigen Rahmentabelle.
- Rotor im Wirbelring bis 10 % zu viel Schub gegenüber der Windkanal-Kurve, Abfall des C_T bei kleiner Drehzahl: erst mit
  Messungen an Whoop-Props.
- Messvorschlag mit der echten Meteor: aus 10 m Höhe Gas von Schwebe auf 20 % bzw. 0 %, DVR-Video mit Zeitstempel und
  Blackbox (Baro): Zeit für 10 m und Sinkgeschwindigkeit; dazu 50 m Vollgas für den Topspeed.

## 11. Nachtrag 08.10. (0.5.0): Abgleich mit echten Blackbox-Logs – Schub im Steigflug

Nach den Korrekturen aus §10 wurde das Flugmodell mit dem Kalibrier-Werkzeug (`docs/tools/blackbox-calibration.md`)
gegen 19 öffentliche Betaflight-Logs vom 75-mm-Whoop bis zum 7″ geprüft. Das Werkzeug fliegt dieselbe Motor-Duty im
Flugmodell nach (Prop-Wash und Nachlauf an, nur ohne Rauschen) und misst echt und Sim mit demselben Code. Ziel: Die Sim
soll für alle Größen innerhalb der Messunsicherheit liegen, ohne Ausnahmen für einzelne Drohnen und ohne Rauschen
nachzubilden.

### 11.1 Befund vor der Änderung

- **Sinkflug mit Gas** (1,5–3,5 m/s, relativ zum Schweben, also unabhängig von Gewicht und Prop-Beiwerten): echt/Sim
  0,98–1,02 für alle Größen. Nach §10 „schwebt“ die Sim beim Sinken nicht mehr. Der frühere Befund 0,91–0,97 kam aus der
  ersten Fassung des Werkzeugs, die ohne Prop-Wash nachflog.
- **Fall nach Gas weg** (Beschleunigung 0,12–0,30 s nach dem Gas weg): Whoop 6,7 / 7,3, 2″ 7,0 / 6,7, 2,5″ 10,1 / 10,5,
  5″ 9,3 / 9,9 m/s² (echt / Sim). Die Sim fällt eher etwas schneller; die Streuung der einzelnen Logs ist größer als
  der Unterschied (Whoop 5,0–9,4 m/s²).
- **Steigflug: die Sim verliert zu viel Schub.** Echt/Sim relativ zum Schweben bei 1,5–3,5 m/s Steigen 1,03 (5″) bis
  1,36 (2,5″), bei 3,5–8 m/s 1,08 bis 1,75. Das gilt für alle Größen und hängt nicht vom Gewicht ab.

### 11.2 Ursache und Korrektur: Impuls- und Blattelementtheorie im Steigflug

Bisher fiel der Schub bei gleicher Drehzahl linear mit dem Fortschrittsgrad: `T/T₀ = 1 − λ`, `λ = V/V₀`, `V₀` die
Nullschub-Geschwindigkeit (`(Steigung + 0,2·D)·n`). Das zählt die ganze Steiggeschwindigkeit als zusätzliche Anströmung
am Blatt. Nach der Impulstheorie sinkt beim Steigen aber die induzierte Geschwindigkeit mit; die Anströmung am Blatt
wächst nur um einen Teil der Steiggeschwindigkeit.

Kombiniert (Blattelement linear in der Anströmung, Impuls am Rotor), alles in Einheiten der induzierten Geschwindigkeit
`v₀ = √(k_T·ω²/(2ρA))` bei dieser Drehzahl:

- Blattelement: `T/T₀ = (r − s − u)/(r − 1)`, mit `r = V₀/v₀`, `s = V/v₀ = λ·r`, `u = v/v₀`
- Impuls: `T/T₀ = u·(s + u)`
- Lösung: `u = 2c/(b + √(b² + 4c))` mit `k = 1/(r − 1)`, `b = s + k`, `c = (r − s)·k`

Eigenschaften:

- Schweben (`λ = 0`) bleibt `1`, Nullschub bei `λ = 1` bleibt.
- Anfangssteigung `−r/(2r − 1)` statt `−1`, also etwa halb so viel Verlust.
- `r` liegt bei den eingebauten Props zwischen 2,9 (7″) und 5,6 (31-mm-Dreiblatt).
- Neue Größe am Flugmodell: `AirframeSpec.inducedPerOmega` = `√(k_T/(2ρA))`. Bei Schwebe-Drehzahl ergibt sie genau
  die bisherige `hoverInducedVelocity`; `RotorMomentumTest` prüft das.

**Vorwärtsflug:** Strömt die Luft quer durch die Scheibe, reagiert die induzierte Geschwindigkeit kaum noch auf die
axiale Anströmung. Nach Glauert ist ihre Empfindlichkeit relativ zum Schweben `w(μ) = 1 − μ²/√(μ⁴ + 4)` mit
`μ = V_quer / v_h`. Daraus folgt:

- senkrecht `w = 1`, bei `μ = 1` noch 0,55
- im Reiseflug und bei Topspeed (`μ ≈ 2`) etwa 0,1–0,15
- der Schub wird zwischen linearer Formel und Impulslösung mit `w` gemischt

Damit bleiben Vorwärtsflug und Topspeed, deren Widerstand gegen veröffentlichte Topspeeds kalibriert ist (§3, §10),
praktisch wie bisher.

**Unverändert:** Sinkflug (`λ < 0`, Zuschlag `DESCENT_GAIN` und Wirbelring), Drehmoment (`Q_CLIMB`), Duct-Abklingen
und rückwärts drehende Props im 3D-Modus (lineare Formel, eigene Anström-Logik, keine Daten).

### 11.3 Vorher/nachher gegen die echten Logs

Mediane je Größe, echt / Sim. Das Flugmodell vorher ist `fix/physics-floaty` mit §10. Steigflug und Sinkflug sind
echt/Sim relativ zum Schweben (1 = gleich, über 1 = die Sim hat weniger Schub).

| Größe (Logs) | Steigflug 1,5–3,5 m/s vorher → jetzt | Steigflug 3,5–8 m/s | Sinkflug 1,5–3,5 m/s | Fall nach Gas weg [m/s²] | Punch [g] |
|---|---|---|---|---|---|
| Whoop 75 mm 1S (5) | – | – | – | 6,7 / 7,3 → 7,2 | 2,24 / 2,61 → 2,66 |
| 2″-Cinewhoop (4) | 1,16 → 1,12 | 1,75 → 1,58 | 1,00 → 1,00 | 7,0 / 6,7 → 6,7 | – |
| 2,5″ 3S (2) | 1,04 → 0,99 | 1,61 → 1,32 | 0,99 → 0,99 | 10,1 / 10,5 → 10,7 | – |
| (3″-Cinewhoop) (1) | 1,13 → 1,09 | – | 0,99 → 0,99 | – | – |
| 5″ 6S (2) | 1,01 → 0,97 | 1,08 → 1,00 | 0,98 → 0,98 | 9,3 / 9,9 → 9,9 | – |
| (7″ LR) (2) | 1,18 → 1,12 | 1,39 → 1,27 | 0,98 → 0,98 | – | – |

Größen in Klammern haben nur geschätzte Bauten.

- **5″** (Bau bekannt) liegt im Steigflug jetzt bei 0,97–1,00.
- **Kleine Props und 7″** verlieren beim schnellen Steigen echt noch weniger Schub als nach der Impulstheorie. Möglich
  sind Strömungsablösung am stehenden Blatt (der statische Schub hoher Steigungen ist dadurch kleiner) und die falschen
  Beiwerte dieser Props (§11.4). Das ist nicht nachgebildet, weil es keinen hergeleiteten Parameter dafür gibt.

### 11.4 Was die Logs sonst zeigen und warum es so bleibt

| Befund | Bewertung | Entscheidung |
|---|---|---|
| Props 45–76 mm zu schwach: Schwebe-Drehzahl der Sim 30–85 % zu hoch, Schwebe-Duty 2,5″/3″ 46–48 statt 31 % | Teiledaten, kein Engine-Fehler. Der 40-mm-Zweiblatt von Just More Parts (c_t × 1,7 seit 1.0.5) trifft den echten Meteor75 auf 5 % Drehzahl. Zwei unabhängige Anker für den 45-mm-Dreiblatt: die Pavo-Pico-Logs ergeben c_t ≈ 0,216, der Prüfstand des 1811 (`WhoopThrustTableTest`, c_t 0,196) skaliert ≈ 0,22, Daten 0,0894 | Nicht in dieser Korrektur: Die Beiwerte von `propwash:p3_3b` und der Just-More-Parts-Familien 45–76 mm verschieben Strom, Motorwärme und Flugzeit-Kalibrierung (`calibration.md` §2.4/§2.5). Vorschlag für 0.5.1 / Just More Parts 1.2: c_t × 2–2,5 mit c_p aus Prüfstandsdaten. Die Logs bestimmen nur c_t sicher, c_p hängt an den unsicheren Motordaten |
| `DESCENT_GAIN` 0,5 → 0,1 (Vorschlag des Werkzeugs) | Kam aus der ersten Fassung ohne Prop-Wash; jetzt 0,98–1,00 | unverändert |
| Schnelles Sinken mit Gas 5–8 m/s: 5″/7″ echt/Sim 1,07–1,21, 2″ 0,95 | Richtung je Größe verschieden, 18–148 Werte, Wirbelring-Bereich | unverändert, die Wirbelring-Kurve liegt im Windkanal-Band (§10.2) |
| Motor bremst echt schneller (2,5″: 21 statt 34 ms) | Die Sim bremst schon aktiv (negativer Phasenstrom wie „damped light“); der Rest ist Prop-Last und Trägheit, also Teiledaten | unverändert |
| Endgeschwindigkeit 5″ im Fall echt 6,0 m/s (Fit) | ein Log, kurzes Fenster, Geschwindigkeit aus Acc-Integration | nicht eingepasst |
| Whoop-Punch Sim 19 % stärker | Teiledaten (c_p des 40-mm-Props laut Werkzeug × 1,24) und 1S-Spannungseinbruch | unverändert |

### 11.5 Auswirkungen in der Sim

Eingebaute Presets, frischer Akku, vorher → jetzt:

| | Whoop 65 | Cinewhoop 3″ | 5″ Freestyle | 5″ Race | 7″ LR |
|---|---|---|---|---|---|
| Punch 1 s Vollgas aus Schweben, v_y [m/s] | 10,1 → 10,7 | 19,4 → 20,4 | 36,3 → 38,7 | 44,1 → 47,1 | 24,8 → 27,0 |
| Spitzen-g im Punch | 2,78 → 2,84 | 4,25 → 4,33 | 9,29 → 9,60 | 11,23 → 11,71 | 5,44 → 5,72 |
| Topspeed geradeaus [km/h] | 51 → 52 | 94 → 95 | 147 → 148 | 175 → 176 | 113 → 114 |
| Sturzflug senkrecht mit Vollgas [km/h] | 61 → 63 | 107 → 111 | 153 → 161 | 183 → 192 | 121 → 127 |
| Reisen [min] (Werkbank) | 3:43 → 3:45 | 5:09 → 5:13 | 7:56 → 7:59 | 8:35 → 8:37 | 18:06 → 18:10 |

Weitere Werte:

- Unverändert: Schweben, Schwebegas, Ströme, Schub/Gewicht, Fall im Leerlauf, Sinken mit 20 % Gas und die
  `ThrottleDropCharacterization`.
- Gemischt und aggressiv werden 0–2 s länger.
- Just-More-Parts-Presets: Reisen +0,5–3 %, Topspeed 0–3 km/h höher.

Der senkrechte Vollgas-Sturzflug wird schneller, weil die Props dort rein axial angeströmt werden. Der Cinewhoop
(Schub/Gewicht 4,7) kommt damit auf 1,17 × Geradeausflug. Die Grenze in `TopSpeedTest` steigt für ihn von 1,15 auf
1,2 ×, wie beim Whoop (1,25 ×). Begründung: Mit wenig Schub/Gewicht zieht die Schwerkraft im Sturz anteilig mehr, allein
das ergibt `√((S/G + 1)/√(S/G² − 1))` = 1,11 für den Cinewhoop. Die Kappe von 115 km/h bleibt.

### 11.6 Just More Parts

- Mit dem neuen Steigflug-Schub lag `toothpick35_6s` über seinen Fenstern: gemischt 9:29 bei höchstens 9:27, Reise über
  13,0 min. Das Preset lag schon vorher am Rand (9:26).
- Korrektur im Add-on (Zweig `fix/physics-floaty`, Teilekatalog §13.4): c_p × 1,012 für beide 89-mm-Props
  (`p89_30_3b_pc`, `p89_25_5b_pc_duct`). Derselbe Faktor an beiden, damit die Blattzahl-Reihenfolge im
  3,5″-Cinewhoop bleibt.
- Drei Aussagen mit Zahlen: `m0702_27000` „57 statt 51 km/h“ (vorher 56 statt 50), `m2004_1700` „83 °C nach 1 min“
  (vorher 82), Whoop-Topspeeds in CHANGELOG und Release-Notes 59 bzw. 79 km/h.

### 11.7 Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Steigflug nach Impuls- und Blattelementtheorie, ohne freie Parameter | physikalisch hergeleitet, wirkt auf alle Größen gleich; trifft den 5″ (Bau bekannt) auf 0,97–1,00 | `1 − 0,5·λ` aus dem Werkzeug (passt nur in der Steigung bei λ = 0, nicht bei großem λ); ein Faktor je Größe |
| Kopplung mit Glauerts Empfindlichkeit bei Schwebe-Strömung `μ = V_quer/v_h` | senkrecht volle Wirkung, im Vorwärtsflug fast keine; Topspeeds und Flugzeit-Kalibrierung bleiben | Kopplung mit der induzierten Geschwindigkeit bei aktueller Drehzahl (Topspeed +3–5 %, Race-5″ 195 km/h über dem Just-More-Parts-Korridor von 190 km/h, Zweiblatt/Dreiblatt-Reihenfolge der 6″-Props kippt); Kopplung über den Anteil `v/v_h` (Entwurf, blendet zu langsam aus: Reise +1–4 %, `toothpick35_6s` gemischt 9:35) |
| Drehmoment im Steigflug unverändert | ein flacheres Drehmoment (nach Impulstheorie) würde den Steigflug-Abgleich verschlechtern; keine Daten, die es trennen | `Q_CLIMB` mitziehen |
| Sinkflug, Wirbelring und `DESCENT_GAIN` unverändert | Sinkflug echt/Sim 0,98–1,00 | `DESCENT_GAIN` 0,1 |
| Prop-Beiwerte 45–76 mm nicht in dieser Korrektur | Daten statt Engine; verschieben Energie- und Wärmekalibrierung; c_p aus den Logs nicht sicher | einen größenabhängigen Faktor im Modell (wäre eine Ausnahme je Größe) |
| Sturzflug-Grenze Cinewhoop 1,15 → 1,2 × | Folge der Physik, die Schwerkraft allein trägt 1,11 bei | Kopplung im Sturz abschwächen |
| Just More Parts: c_p × 1,012 an beiden 89-mm-Props | kleinste Änderung, die beide Toothpick-Fenster und die Blattzahl-Reihenfolge hält | Fenster lockern; nur den Dreiblatt ändern (Reihenfolge kippt) |
| Kein neues Netzwerkprotokoll, API unverändert | nur Rechnung im Client, keine neuen Daten | – |

Geänderte Prüfwerte und Goldens:

- `airframe_golden.txt` (18 Einträge) und `builtin_regression.txt`: Analyse (Topspeed, Reise) und Flug aller
  eingebauten Drohnen. Schweben und Standschub bleiben bitgleich (bei `λ = 0` gilt weiter die lineare Formel), deshalb
  ist der Motorklang (`MotorTimbreRegressionTest`) unverändert.
- `TopSpeedTest`: Sturzflug-Grenze Cinewhoop 1,2 ×.
- `TrickVariantScripts.coinDrop`: 2,5 statt 2,0 s Leerlauf vor der Drehung. Nach dem stärkeren Punch steigt die Drohne
  länger nach, die Drehung fiel sonst in den Scheitelpunkt. Die Bedingung des Klassifizierers bleibt.
- Neu `RotorMomentumTest`: Schweben, Nullschub, Monotonie, Anfangssteigung `−r/(2r−1)`, Glauert-Kopplung, Sinkflug
  unverändert, `inducedPerOmega` gegen `hoverInducedVelocity`.

[shetty]: https://m-selig.web.engr.illinois.edu/pubs/ShettySelig-2011-AIAA-2011-1254-LRN-VSR-Props.pdf
[OL-M75]: https://oscarliang.com/betafpv-meteor75-pro-dji-o4-wide/

## Quellen

- [bf-idle]: Betaflight-Doku „Dynamic Idle“ – https://betaflight.com/docs/wiki/guides/current/Dynamic-Idle (Standard 5,5 %,
  5″ `dyn_idle_min_rpm` 30–40, Beispiel 2000 U/min)
- Betaflight Pull Request #13936 (`dshot_idle_value` → `motor_idle`, Standard 550) – https://github.com/betaflight/betaflight/pull/13936
- [uav-idle]: UAVMODEL, „Betaflight Dynamic Idle Setup: Idle Percentage, DShot Idle, and Low-Throttle Stability“ –
  https://blog.uavmodel.com/betaflight-dynamic-idle-setup-idle-percentage-dshot-idle-and-low-throttle-stability-2026-guide/
  (2200–2800 U/min bei 5,5 %, zu hohes Idle „hängt und treibt“)
- [assure]: FAA ASSURE, Task A4 „UAS Ground Collision Severity Evaluation“, Final Report, S. 136 –
  https://assureuas.com/wp-content/uploads/2021/06/A4-Final-Report.pdf (Phantom 3: 60 ft/s flach, 84 ft/s Kante)
- [hammer]: T. Hammer et al., „Free fall drag estimation of small-scale multirotor unmanned aircraft systems using
  computational fluid dynamics and wind tunnel experiments“, CEAS Aeronautical Journal 2023 –
  https://doi.org/10.1007/s13272-023-00702-w (frei drehende Props bis +110 % Widerstand, Anstellwinkel −40 bis −85 %)
- [icas]: „Numerical study of quad-rotor aircraft performance under adverse situations“, ICAS 2020 –
  https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf (1,4 kg senkrecht: 29,3 m/s mit stehenden,
  40,5–42,7 m/s mit drehenden Props)
- [utech]: Unmanned Tech, „FPV Drone Speed: 5-Inch, Racing & DJI Top Speeds Compared“ –
  https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-drone-top-speed-how-fast-real-world-by-build-class
- [liftoff-sliders]: Steam, Liftoff, „The case of the Physics Adjustment Sliders and Simulation Accuracy“ –
  https://steamcommunity.com/app/410340/discussions/0/2828702372996606980/
- [liftoff-drop]: Steam, Liftoff, Diskussion zu Widerstand und Fallverhalten –
  https://steamcommunity.com/app/410340/discussions/0/1729827777357636882
- [velo]: Velocidrone-Einstellungen (Drag Front/Top/Side) – https://www.apollomaniacs.com/ipod/howto_ar_drone_velocidrone_tuning_en.htm
- Minecraft 26.3 (Quellen in `.ref/vanilla`): `Attributes.GRAVITY` 0,08, `LivingEntity.BASE_VERTICAL_AIR_DRAG` 0,98,
  `ItemEntity`/`FallingBlockEntity#getDefaultGravity` 0,04.

[bf-idle]: https://betaflight.com/docs/wiki/guides/current/Dynamic-Idle
[uav-idle]: https://blog.uavmodel.com/betaflight-dynamic-idle-setup-idle-percentage-dshot-idle-and-low-throttle-stability-2026-guide/
[assure]: https://assureuas.com/wp-content/uploads/2021/06/A4-Final-Report.pdf
[hammer]: https://doi.org/10.1007/s13272-023-00702-w
[icas]: https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf
[utech]: https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-drone-top-speed-how-fast-real-world-by-build-class
[liftoff-sliders]: https://steamcommunity.com/app/410340/discussions/0/2828702372996606980/
[liftoff-drop]: https://steamcommunity.com/app/410340/discussions/0/1729827777357636882
[velo]: https://www.apollomaniacs.com/ipod/howto_ar_drone_velocidrone_tuning_en.htm
