# Realismus-Referenz: Motoren und Propeller

Stand 2026-09-30. Rolle: Referenz-Recherche „motors-props“ für die Kalibrierung der Part-Daten von Propwash und
*Just More Parts* sowie der Physik- und Analysekonstanten. Dieses Dokument ändert keinen Code und keine Teile-Daten;
es sammelt reale Messwerte mit Quelle, Bedingungen und Unsicherheit und leitet daraus Zielwerte je Größenklasse ab.

Anlass (Rückmeldung aus dem Playtest): Flugzeiten und Part-Werte wirken etwas daneben. Beispiel aus dem
Web-Konfigurator: „Deadcat LR 7″ · 8S“ mit 2200 mAh 8S zeigt 896,9 g, Schub/Gewicht 11,7 : 1, Schwebegas 21,7 %,
Schwebezeit 24,2 min, Reiseflug 10,5 min, Spitze 145 km/h, 137,5 A Vollgas, 124 °C Motortemperatur bei Vollgas im Stand.
Abschnitt 8 ordnet diese Zahlen gegen die Referenzen ein.

---

## 0. Kurzfassung: Kernzahlen je Klasse

Statischer Prüfstand, ein Motor, frischer Akku bzw. Netzteil, Umgebung 8–27 °C. „Last-Drehzahl“ = Drehzahl bei 100 %
geteilt durch KV × Versorgungsspannung. „η_ges“ ist der Gesamtgütegrad Schub → elektrische Leistung (Abschnitt 1.3),
„Flug“ ist die Schätzung für den Schwebeflug am echten Kopter (Rahmenverdeckung, Regelunruhe, Kabel).

| Klasse | typischer Antrieb | Schub 100 %/Motor | Strom 100 %/Motor | Last-Drehzahl | g/W 100 % | Schwebeschub/Motor | g/W Schwebe (Stand) | η_ges Schwebe Stand → Flug |
|---|---|---|---|---|---|---|---|---|
| Whoop 65 mm | 0702/0802, 19–30k KV, 1S, 31 mm 3-Blatt | 18–23 g (unabhängig bzw. M0703), 32–40 g (Herstellerangaben) | 2–5 A | 0,73–0,82* | 1,6–3,1 | 6–8 g | 1,7–3,2 | 0,10–0,19 → 0,12–0,18 |
| Whoop 75 mm | 0802/1002/1102, 1S–2S, 40 mm | 1S 30–35 g, 2S 80 g | 1S 5,5–6 A, 2S 9 A | 0,5–0,7* | 1,2–1,8 | 9–11 g | 1,6–1,9 | 0,09–0,12 (Hersteller, ungünstig) |
| Toothpick 2–2,5″ | 1103/1202.5, 7–11k KV, 2S–3S | 100–175 g | 7–11 A | 0,57–0,71* | 1,2–2,0 | 12–20 g | ≈ 2–3 | 0,10–0,20 |
| Toothpick 3″ | 1204/1303/1404, 2S–4S | 155–300 g | 6–8 A | 0,58–0,75* | 1,3–2,8 | 20–30 g | 3,3–6 (extrapoliert) | 0,15–0,28 |
| Cinewhoop 3″ (76 mm Duct) | 1404–2004/2203.5, 4S–6S | 700–800 g | 22–27 A | 0,66–0,91 | 1,2–2,2 | 60–80 g | 2,9–3,9 | 0,23–0,30 → 0,22–0,28 |
| Cinewhoop 3,5″ (90 mm) | 1804/2004/2203.5, 4S–6S | 580–870 g | 20–27 A | 0,56–0,72 | 1,7–2,6 | 90–110 g | 2,9–6,0 | 0,23–0,47 → 0,25–0,35 |
| 5″ Freestyle/Race | 2207/2306(.5), 6S 1690–2020 KV bzw. 4S 2400–2550 KV | 6S 1,35–2,0 kg, 4S 1,1–1,6 kg | 6S 25–50 A, 4S 30–51 A | 0,64–0,82 | 1,5–2,5 | 130–175 g | 2,7 (älter) … 5,1 (neu) | 0,20–0,37 → 0,22–0,30 |
| 6″ | 2306–2505, 6S | 1,6–1,8 kg | 33–42 A | 0,53–0,78 | 1,6–2,5 | 175 g | 3,0–7,2 (Median ≈ 5) | 0,18–0,44 |
| 7″ Long Range | 2806.5/2807/2808, 6S 1100–1500 KV | 1,4–2,5 kg | 28–47 A | 0,53–0,76 | 1,6–2,9 | 200–250 g | 3,6 … 9,7 (Streuung, s. 3.8) | 0,21–0,58 → 0,30–0,40 |
| X-Class 10–13″ | 3115/3120/4214, 6S–12S | 4,0–7,1 kg | 6S 70–84 A, 12S 31–69 A | 0,64–0,82 | 2,1–2,9 | 0,9–1,5 kg | 4,9–6,6 | 0,41–0,58 → 0,40–0,50 |

\* 9N12P-Kleinmotoren: Die Hersteller-Drehzahl ist vermutlich um den Faktor 6/7 zu niedrig (Abschnitt 1.4); die Spalte
nennt den korrigierten Bereich.

**Propeller-Beiwerte** (statisch, Definition wie `flight-model-spec` 4.4, Wellenleistung):

| Prop-Typ | c_t | c_p | FM | Propwash-Basis heute (c_t / c_p / FM) |
|---|---|---|---|---|
| 31 mm 3-Blatt, P ≈ 1,4–1,9″ | 0,25 (0,20–0,30) | 0,30 (0,22–0,37) | 0,25–0,35 | `p31_3b` 0,09 / 0,072 / 0,27 |
| 31 mm 4-Blatt | 0,22 (0,19–0,25) | 0,33 (0,25–0,41) | 0,20–0,26 | `p31_4b` 0,10 / 0,085 / 0,26 |
| 3″ 3-Blatt, P/D ≈ 0,93 (3028) | 0,20 (0,19–0,21) | 0,13 (0,12–0,15) | 0,45–0,51 | – |
| 3″ 3-Blatt, P/D ≈ 1,0 (3×3×3) | 0,28 (0,25–0,30) | 0,34 (0,30–0,37) | 0,30–0,32 | `p3_3b` 0,09 / 0,085 / 0,30 |
| 5″ 3-Blatt, P/D 0,85–0,95 | 0,18 (0,16–0,21) | 0,105 (0,09–0,13) | 0,45–0,57 | `p51_3b` 0,12 / 0,06 / 0,49 |
| 7″ 2-Blatt, P/D ≈ 0,6 | 0,14 (0,12–0,16) | 0,07 (0,06–0,085) | 0,41–0,62 | – |
| 7″ 2-Blatt, P/D ≈ 0,5 (geschätzt aus P/D-Trend) | 0,11 (0,10–0,12) | 0,05 (0,045–0,055) | 0,5–0,6 | `p7_2b` 0,09 / 0,036 / 0,53 |
| 7″ 3-Blatt, P/D ≈ 0,57 | 0,12–0,17 | 0,06–0,09 | 0,41–0,63 | – |
| 10″ 3-Blatt, P/D 0,5 | 0,16–0,17 | 0,073–0,076 | 0,62–0,66 | – |
| 13″ 3-Blatt, P/D 0,77 | ≈ 0,17 | unsicher | – | – |

**Befunde für die Kalibrierung** (Einzelheiten in Abschnitt 9):

1. Die c_t- und c_p-Werte der Basis-Props liegen absolut deutlich zu niedrig (5″ etwa −35 % bzw. −45 %, 7″ −10 bis −25 %
   bzw. −20 bis −35 %, 3″ und 31 mm um den Faktor 2,5–4). Die Gütezahl FM passt in allen Klassen. Folge: Die Sim braucht
   im Schweben und bei Vollgas zu hohe Drehzahlen; Vollgas-Schub, -Strom und Topspeed hängen dann an der falschen Stelle
   der Motorkennlinie.
2. Der Gesamtgütegrad im Schweben ist die Größe, die die Flugzeit bestimmt. Real: Whoop ≈ 0,12–0,18, 5″ ≈ 0,22–0,30,
   7″ ≈ 0,30–0,40, X-Class ≈ 0,40–0,50 (Flug). Die Basis-Physik liefert beim 5″ rechnerisch ≈ 0,34 im Stand
   (Abschnitt 9, Punkt 2); das ist das obere Ende der Herstellerdaten und ohne Einbauverluste.
3. Motor-Leerlaufverluste wachsen mit der Drehzahl: Aus gemessenem Drehmoment folgt ein wirksamer Leerlaufstrom von
   2–3,5 × dem 10-V-Datenblattwert im mittleren Gasbereich. Antriebswirkungsgrad (Akku → Welle): ≈ 0,45–0,65 bei 20 % Gas,
   0,65–0,80 bei 50 %, 0,60–0,80 bei Vollgas.
4. Duct-Wirkung (gleicher Motor, Prop mit und ohne Rahmen-Duct): +6 bis +23 % g/W bei gleichem Gas, Median ≈ +14 %;
   gleicher Schub bei gleicher Drehzahl, aber ≈ 10–17 % weniger Leistung. Ein reiner Schubfaktor 1,15 bei gleicher
   Drehzahl entspräche ≈ +23 % Effizienz und ist damit am oberen Rand.
5. Temperaturen: Nach 60 s Vollgas im Stand misst der Hersteller 72–102 °C Glocke bei 5″/7″ (ΔT ≈ 60–83 K),
   45–68 °C bei 1203–1404. Nach einem normalen Akku sind 40–60 °C üblich; 65–70 °C Glocke gelten als Obergrenze.

---

## 1. Konventionen und Methode

### 1.1 Beiwerte

Wie `docs/design/flight-model-spec.md` 4.4: `T = c_t·ρ·n²·D⁴`, `P = c_p·ρ·n³·D⁵`, `n` in Umdrehungen je Sekunde, `P` die
Wellenleistung. Gütezahl (Figure of Merit, statisch): `FM = c_t^1,5 / (√2 · c_p)`.

Die Prüfstände laufen bei 20–27 °C und geringer Höhe. Alle hier abgeleiteten Beiwerte sind mit `ρ = 1,19 kg/m³`
gerechnet. Die Beiwerte sind dimensionslos und direkt verwendbar; die Sim mit `ρ0 = 1,225` liefert bei gleicher
Drehzahl ≈ 3 % mehr Schub als der Prüfstand und ist im Schweben ≈ 1,5 % effizienter. Das liegt unter der Streuung der
Quellen.

### 1.2 Wellenleistung

- **Mit Drehmoment** (T-Motor-Tabellen mit Spalte „Torque“: V3115, V3120, Cine-Serie, PV4 2406, Cine77):
  `P_Welle = M·ω`. Die Auflösung ist 0,01 N·m, Werte unter 0,03 N·m werden nicht verwendet.
- **Ohne Drehmoment** über das Motormodell der Sim: `K_t = 1/K_v`, `R = R_m + 5 mΩ` (ESC), `I_0` = Datenblatt bei 10 V,
  ESC-Wirkungsgrad 0,95: `0,95·P_el = E·I + R·I²`, `E = U/min / KV`, `P_Welle = E·(I − I_0)`. Fehlt `R_m`, gilt die
  Skalierung aus 2.4. Unsicherheit ±15 % auf c_p; das Modell unterschätzt die realen Verluste bei hoher Drehzahl
  (Abschnitt 5), c_p und FM sind damit eher obere Werte.

### 1.3 Gesamtgütegrad η_ges

`η_ges = T^1,5 / (√(2·ρ·A) · P_el)` mit Scheibenfläche `A = π·D²/4`. Das ist `FM · η_Antrieb` und hängt nicht von der
Drehzahl ab – also auch nicht von Drehzahl-Messfehlern. Für die Flugzeit ist das die entscheidende Zahl:
`P_Schwebe = (m·g)^1,5 / (√(2·ρ·A_ges) · η_ges)` mit `A_ges` = Summe der Scheibenflächen.

Beispiel 5″ (D = 130 mm), 600 g: ideale Leistung ≈ 40 W; bei η_ges = 0,27 sind es ≈ 147 W elektrisch für die Motoren.

### 1.4 Drehzahlkorrektur bei 9N12P-Motoren (T-Motor-Tabellen)

Bei allen 9N12P-Motoren (M0703, M0802, M0803, M1002, M1103–M1106, F1203, F1204, F1303, F1404, F1408) ergeben die
T-Motor-Drehzahlen unplausibel hohe Beiwerte. Belege:

- derselbe Prop GF 4023-3: am 9N12P-Motor F1408 c_t = 0,227 / c_p = 0,153, an den 12N14P-Motoren F2004/F2203.5 c_t ≈ 0,150 / c_p ≈ 0,09;
- 3″-2-Blatt 3018 (P/D 0,6) am F1203: c_t 0,164; der vergleichbare UIUC-Prop GWS 3×2 (P/D 0,67) hat 0,137;
- ein 2″-3-Blatt am F1203 käme auf c_p ≈ 0,5 – das ist für einen offenen Prop physikalisch nicht erreichbar.

Die naheliegende Ursache: Drehzahl aus der Kommutierungsfrequenz mit 14 statt 12 Polen gerechnet. Mit dem Faktor 7/6
auf die Drehzahl (c_t / 1,36, c_p / 1,59, FM unverändert) passen alle Reihen zu den unabhängigen Trends. Die Tabellen in
Abschnitt 3 nennen die **gemeldete** Drehzahl, die Beiwerte in Abschnitt 4 sind **korrigiert**. Unsicherheit dieser
Korrektur: Sie ist geschlossen, nicht gemessen; die Whoop-Beiwerte tragen deshalb ±20 %.

### 1.5 Qualitätsstufen der Quellen

| Stufe | Bedeutung |
|---|---|
| A | unabhängiger Prüfstand oder wissenschaftliche Messung (MQTB, UIUC, Einzeltests) |
| B | Hersteller-Tabelle, in sich plausibel (T-Motor-Serien V, P, F90/F100, V3115/V3120, Cine) |
| C | Hersteller-Tabelle mit Widersprüchen oder Werbeangabe (einzelne Zeilen, Flugzeit-Angaben) |

Hersteller-Tabellen sind nach allen Vergleichen eher optimistisch: Bei gleichem Schub liegen die T-Motor-Werte für 5″
25–55 % über der älteren unabhängigen MQTB-Messung (Abschnitt 3.8).

---

## 2. Motoren: Datenblattwerte

`R_m` = Wicklungswiderstand Phase–Phase (T-Motor „Internal Resistance“), `I_0` = Leerlaufstrom bei der genannten
Spannung, `I_max`/`P_max` = Spitzenwerte des Herstellers (10 s, 60 s oder 1 s wie angegeben; meist die höchste Zeile
der Schubtabelle, keine thermische Dauerangabe). Masse mit Kabel.

### 2.1 Whoop und Kleinstmotoren (1S–2S)

| Motor | Stator | KV | Masse | R_m | I_0 | I_max | P_max | Quelle |
|---|---|---|---|---|---|---|---|---|
| T-Motor M0703 | 0703 | 19000 | 1,7 g | – | 0,5 A @10 V | 2,0 A (10 s) | 7,5 W | [TM-M0703] B |
| T-Motor M0802 | 0802 | 27000 | 1,78 g | – | 0,62 A @10 V | 5,65 A | 21 W | [TM-M0802] B |
| T-Motor M0803 | 0803 | 19000 | 1,91 g | – | 0,49 A @4 V | 4,4 A | 18,1 W | [TM-M0803a] B |
| T-Motor M0803 | 0803 | 22000 | 2,47 g | 122 mΩ | 0,65 A @10 V | 5,46 A (60 s) | 22,7 W | [TM-M0803b] B |
| RCinPower GTS V3 0802 | 0802 | 22000 | 1,97 g | 117 mΩ | 0,2 A @5 V | 5,8 A | – | [RCP-0802] B |
| BetaFPV 0702SE II / 0702 II | 0702 | 23000 / 27000 / 30000 | 1,45 / 1,47 / 1,55 g | – | – | – | – | [BF-0702] B |
| BetaFPV 0802SE | 0802 | 19500 / 23000 | 1,83 / 1,80 g | – | – | – | – | [BF-0802SE] B |
| T-Motor M1002 | 1002 | 14000 (2S) | 2,5 g | 185 mΩ | 1,3 A @2 V | 9,8 A | 70,9 W | [TM-M1002] B/C |
| T-Motor M1002 | 1002 | 19000 / 22000 (1S) | 2,5 g | 133 / 120 mΩ | 1,7 / 2,1 A @2 V | 6,1 / 6,2 A | 21,7 / 22,3 W | [TM-M1002] B/C |

Die M1002-Leerlaufströme „@2 V“ sind unplausibel hoch (Messspannung vermutlich falsch angegeben).

### 2.2 Toothpick, 2,5–4″ und Cinewhoop (2S–6S)

| Motor | Stator | KV | Masse | R_m | I_0 @10 V | I_max | P_max | Quelle |
|---|---|---|---|---|---|---|---|---|
| T-Motor M1103 | 1103 | 8000 / 11000 | 4,64 / 4,61 g | – | 0,48 / 0,52 A | 18,4 / 15,2 A | 284 / 188 W | [TM-M1103] C (Drehzahlspalte fehlerhaft) |
| Happymodel EX1103 | 1103 | 11000 | 3,8 g | – | 0,38 A @2 V | 9,2 A (Tabelle) | 68 W | [HM-EX1103] B |
| T-Motor M1104 | 1104 | 7500 | 5,6 g | – | 0,44 A | 13,1 A | 198 W | [TM-M1104] B |
| T-Motor M1106 | 1106 | 6000 | 7,2 g | – | 0,4 A | 14,9 A | 214 W | [TM-M1106] B |
| Flywoo Robo 1202.5 | 1202.5 | 11500 | 3,8 g | – | – | – | – | [FW-1202] B |
| T-Motor F1203 | 1203 | 7000 | 5,25 g | 198 mΩ | 0,64 A | 10,85 A (60 s) | 192 W | [TM-F1203] B |
| T-Motor F1204 | 1204 | 5000 / 6500 | 6,9 g | 240 / 140 mΩ | 0,7 / 0,9 A | 6 / 7 A | 72 / 60 W | [TM-F1204] B |
| T-Motor F1303 | 1303 | 5000 | 6,1 g | 280 mΩ | 0,32 A @4 V | 7,45 A | 87 W | [TM-F1303] B |
| T-Motor F1404 | 1404 | 2900 / 3800 / 4600 | 9,34 g | 310 / 195 / 138 mΩ | 0,4 / 0,5 / 0,6 A | 10,5 / 19 / 20 A | 262 / 304 / 316 W | [TM-F1404] B |
| TBS Podracer 1505 | 1505 | 3600 | 11 g | 200 mΩ | – | 12 A (15 A Burst) | – | [TBS-1505] B |
| BetaFPV 1505 | 1505 | 3600 | 13,08 g | – | – | – | – | [BF-1505] B |
| T-Motor F1408 II | 1408 | 2800 / 3950 | 14,9 g | 141 / 88 mΩ | 0,62 / 0,86 A | 15,3 / 22,4 A | 385 / 371 W | [TM-F1408] B |
| T-Motor P1604 | 1604 | 2850 / 3800 | 11,6 g | – | 0,4 / 0,8 A | 15,8 / 16,8 A | 395 / 278 W | [TM-P1604] B |
| T-Motor P1804 | 1804 | 2400 / 3400 | 13,8–14,9 g | – | 0,45–0,51 / 0,8 A | 16 / 20,2 A | 380 / 318 W | [TM-P1804] B |
| T-Motor F2004 | 2004 | 1700 / 3000 | 16,4 g | 221 / 90 mΩ | 0,38 / 0,88 A | 21,2 / 30,1 A | 503 / 460 W | [TM-F2004] B |
| T-Motor F2203.5 | 2203.5 | 1500 / 2850 / 3550 | 19,5–19,7 g | 235 / 90 / 76 mΩ | 0,3 / 0,75 / 1,29 A | 19,5 / 31,4 / 26,8 A | 462 / 734 / 412 W | [TM-F2203] B/C |
| T-Motor Cine20 (4S) | ≈ 1404 | – | 10 g | 94 mΩ | 0,7 A | 12 A (1 s) | 192 W | [TM-Cine20] B |
| T-Motor Cine25 (4S) | ≈ 1504 | – | 11 g | 141 mΩ | 0,7 A | 12 A (1 s) | 192 W | [TM-Cine25] B |
| T-Motor Cine30 (6S) | ≈ 2004 | – | 18 g | 120 mΩ | 1,1 A | 25 A (1 s) | 323 W | [TM-Cine30] B |
| T-Motor Cine35 (6S) | ≈ 2004 | – | 19,6 g | 140 mΩ | 0,8 A | 18 A (1 s) | 316 W | [TM-Cine35] B |

F2203.5 1500 KV: Die Tabelle meldet bei Vollgas mehr Drehzahl als KV × Spannung (Last-Drehzahl 1,06–1,09); KV oder
Drehzahl stimmen dort nicht.

### 2.3 5″ bis X-Class (4S–12S)

| Motor | Stator | KV | Masse | R_m | I_0 @10 V | I_max | P_max | Quelle |
|---|---|---|---|---|---|---|---|---|
| T-Motor Velox V2207 V2 | 2207 | 1750 / 1950 / 2550 | 35,1 / 35,2 / 34,3 g | 59 / 50 / 43 mΩ | 1,1 / 1,3 / 1,9 A | 36,6 / 45,8 / 38,6 A (60 s) | 804 / 1003 / 545 W | [TM-V2207] B |
| T-Motor Velox V2207.5 V2 | 2207.5 | 1750 / 1950 / 2550 | 34,6 / 35,3 / 35,6 g | 74 / 53,5 / 41 mΩ | 1,15 / 1,42 / 1,95 A | 35,7 / 39 / 38,1 A | 794 / 873 / 557 W | [TM-V2207.5] B |
| T-Motor F60 Pro V | 2207.5 | 1750 / 1950 / 2020 / 2550 | 34,3 / 33,9 / 33,8 / 33,3 g | – | 1,0 / 1,1 / 1,25 / 1,6 A | 40,5 / 49,3 / 52,7 / 50,7 A (10 s) | 1003 / 1216 / 1297 / 825 W | [TM-F60] B |
| T-Motor Pacer P2207 V3 | 2207 | 1750 / 1950 / 2080 / 2550 | 34,1–34,3 g | – | 1,2 / 1,35 / 1,4 / 2,25 A | 38,4 / 44 / 44 / 43,6 A | 952 / 1085 / 1037 / 713 W | [TM-P2207] B |
| T-Motor Velox V2306 V2 | 2306 | 1950 / 2400 | 32 g | 64 / 49 mΩ | 1,35 / 1,85 A | 38,1 / 34,1 A | 850 / 494 W | [TM-V2306] B |
| T-Motor Velox V2306 V3 | 2306 | 1500 / 1750 / 1950 / 2550 | 32,6–33,8 g | – | 0,7 / 0,85 / 1,3 / 1,95 A | 34,5 / 34,9 / 41,7 / 34,5 A | 1970 (8S) / 821 / 976 / 534 W | [TM-V2306v3] B |
| T-Motor Velox V2306.5 V2 | 2306.5 | 1950 / 2550 | 33,2 / 33,8 g | 67 / 47 mΩ | 1,28 / 1,72 A | 37,4 / 33 A | 902 / 490 W | [TM-V2306.5] B |
| T-Motor PV4 2406 | 2406 | 2060 | 36 g | 45 mΩ | 1,9 A | 48 A (1 s) | 1116 W | [TM-P2406] B |
| T-Motor P2505 | 2505 | 1850 | 31,25 g | 69 mΩ | 1,1 A | 47 A (60 s) | 1217 W | [TM-P2505] B |
| T-Motor F90 | 2806.5 | 1300 / 1500 | 46,6 / 46,7 g | 76 / 62 mΩ | 0,95 / 1,2 A | 45,1 / 47,4 A (60 s) | 1059 / 1089 W | [TM-F90] B |
| T-Motor F100 | 2808 | 1100 / 1350 | 67 / 66,5 g | 61 / 42 mΩ | 0,98 / 1,5 A | 45,6 / 61,7 A | 1176 / 1237 W | [TM-F100] B |
| T-Motor Velox V2808 | 2808 | 1300 / 1500 / 1950 | 60,5–61,1 g | – | 1,19 / 1,49 / 2,45 A | 62,4 / 74,3 / 82,3 A (10 s) | 1512 / 1782 / 1849 W | [TM-V2808] B |
| T-Motor Cine66 | 2812 | 925 / 1155 | 76,4 / 76,7 g | 69 / 47 mΩ | 1,05 / 1,41 A | 50,5 / 54,5 A | 1237 / 1320 W | [TM-Cine66] B |
| T-Motor Velox V3115 | 3115 | 400 / 640 / 900 / 1050 | 112,7–113,4 g | 145,4 / 67,1 / 38,1 / 33,8 mΩ | 0,59 / 1,10 / 1,57 / 1,79 A | 41 / 62 / 83 / 83,6 A (10 s) | 1982 / 1927 / 1894 / 1903 W | [TM-V3115] B |
| T-Motor Velox V3120 | 3120 | 500 / 700 | 146,7 / 148,1 g | 76,4 / 40,6 mΩ | 0,95 / 1,35 A | 68,9 / 79,4 A | 3242 / 2453 W | [TM-V3120] B |
| T-Motor 4214 | 4214 | 380 / 400 | 211,2 g | 63,3 mΩ | 1,3 A | 48,7 / 38,8 A | 1058 / 886 W (8S) | [TM-4214] C |
| iFlight XING 4214 | 4214 | 400 / 660 | 255,8 / 255,2 g (800-mm-Kabel) | 61,6 / 35,6 mΩ | – | 30,9 / 98 A | 742 / 2352 W | [IF-4214] B |

### 2.4 Skalierung über die Statorgröße

Aus allen Motoren mit Datenblatt-Widerstand (n = 45, Masse und Leerlaufstrom n = 71, Statorvolumen `V_st = π/4·d²·h` in mm³, d und h aus der
Bezeichnung):

| Größe | Näherung | Streuung | Anwendung |
|---|---|---|---|
| Motorkonstante `K_m = K_t/√R_m` | `K_m ≈ 2,24·10⁻⁵ · V_st^0,85` N·m/√W | ±13 % | `R_m ≈ (9,549 / (KV·K_m))²` für Motoren ohne Datenblatt-R |
| `R_m · KV²` | 0803: 6·10⁷ · 1204: 6·10⁶ · 1404: 2,8·10⁶ · 2004: 7·10⁵ · 2207: 1,9–2,8·10⁵ · 2806.5: 1,3·10⁵ · 2808: 7,5·10⁴ · 3115: 2,3–3,7·10⁴ · 4214: 1,0–1,6·10⁴ Ω·(U/min/V)² | – | schneller Plausibilitätscheck |
| Masse (mit Kabel) | `m ≈ 0,026 · V_st^0,91` g | ±7 % | Standardmasse neuer Motoren |
| Leerlaufstrom bei 10 V | `I_0 ≈ 2,2·10⁻⁸ · KV^1,3 · V_st` A (für KV < 5000) | ±18 % | Kleinstmotoren (1S) haben 0,2–0,5 A bei 4–5 V |
| `I_0 / I_max` | 0,015–0,05 ab 1103; 1S-Whoop 0,03–0,12 | – | vgl. Propwash `I0_FRACTION = 0,03` |
| Spitzenleistung je Masse | 1S-Whoop 9–12 W/g · 1103–2207 22–38 W/g · 2808–3115 17–30 W/g | – | Plausibilität `max_current_a` |

Die höhere Streuung bei `I_0` kommt aus der Messspannung: Bei 10 V dreht ein 2550-KV-Motor 25 500 U/min, ein 1750-KV-Motor
nur 17 500; die Eisenverluste wachsen etwa mit Frequenz^1,3.

### 2.5 Vergleich mit den Propwash-Basismotoren

| Basis-Teil | Propwash | Referenz | Bewertung |
|---|---|---|---|
| `m0802_22000` | 2,0 g · 0,20 Ω · 3,5 A | 1,8–2,5 g · 0,105–0,122 Ω · 5,5–6,5 A Spitze | Widerstand ≈ 1,7× zu hoch, Strom konservativ |
| `m1404_4600` | 9,0 g · 0,14 Ω · 18 A | 9,34 g · 0,138 Ω · 20 A | passt |
| `m2004_3000` | 12 g · 0,10 Ω · 24 A | 16,4 g · 0,090 Ω · 30 A | Masse ≈ 25 % zu niedrig |
| `m2207_1750` | 33 g · 0,055 Ω · 45 A | 34–35 g · 0,059–0,074 Ω · 36–41 A (60 s) | passt |
| `m2306_1860` | 31 g · 0,06 Ω · 42 A | 32–34 g · 0,064–0,067 Ω · 35–42 A | passt |
| `m2807_1300` | 47 g · 0,07 Ω · 45 A | 2806.5: 46,6 g · 0,076 Ω · 45 A; 2807 um 54 g; 2808 60–67 g | Masse eher 2806.5 als 2807 |

---

## 3. Schubtabellen (Prüfstand)

Format je Zelle: Gas: Schub · Strom · elektrische Leistung · Drehzahl (wie gemeldet) · g/W. Ein Motor, offener Prop
(außer Duct-Zeilen). T-Motor-Werte am Netzteil (Spannung fast konstant); in der Luft sackt der Akku ab, Vollgaswerte sind
dann 5–15 % niedriger.

### 3.1 Whoop 65–75 mm (1S–2S)

| Motor | Prop | Zellen | Messpunkte | Quelle |
|---|---|---|---|---|
| M0703 19000 KV | T-Motor M12199 (31 mm) | 1S 3,7 V | 20 %: 4 g · 0,4 A · 1 W · 17 217 U/min · 2,8 g/W; 40 %: 11 g · 0,8 A · 3 W · 28 087 · 3,7; 80 %: 19 g · 1,6 A · 6 W · 39 056 · 3,3; 100 %: 23 g · 2,0 A · 7,5 W · 44 120 · 3,1 | [TM-M0703] B |
| M0803 19000 KV | Gemfan 1219-3 (31 mm) | 1S 4,2 V | 50 %: 12 g · 1,4 A · 6 W · 36 954 · 2,0; 80 %: 23 g · 2,5 A · 10 W · 43 233 · 2,2; 100 %: 33 g · 3,8 A · 16 W · 54 506 · 2,1 | [TM-M0803a] B |
| M0803 19000 KV | Gemfan 35 mm 3-Blatt | 1S 4,2 V | 50 %: 18 g · 1,5 A · 6 W · 33 302 · 2,8; 80 %: 29 g · 2,7 A · 11 W · 44 966 · 2,6; 100 %: 40 g · 4,1 A · 17 W · 52 615 · 2,3 | [TM-M0803a] B |
| M0803 22000 KV | Gemfan 1219-3 / 35 mm-3 | 1S 4,2 V | 100 %: 35 g · 4,56 A · 19 W · 1,85 g/W bzw. 41 g · 5,28 A · 22 W · 1,89 g/W (keine Drehzahl) | [TM-M0803b] B |
| 0802 20000 KV | sieben 31-mm-Props | 1S | Maximalschub 17,7–20,0 g, 1,59–1,78 g/W bei Vollgas | [OL-Props] A |
| 0702SE II 23000 / 27000 / 0702 II 30000 | Gemfan 1219S-3 | 1S | Maximalschub 32 / 35,4 / 37,2 g | [BF-0702] C |
| M1002 19000 KV | Gemfan 1611-3 (40 mm) | 1S 3,7 V | 20 %: 4 g · 0,7 A · 2 W · 13 413 · 1,7; 50 %: 15 g · 2,5 A · 9 W · 24 030 · 1,6; 80 %: 24 g · 4,2 A · 15 W · 30 741 · 1,6; 100 %: 33 g · 5,9 A · 21 W · 35 435 · 1,55 | [TM-M1002] B |
| M1002 14000 KV | Gemfan 1611-3 (40 mm) | 2S 7,4 V | 20 %: 12 g · 0,8 A · 6 W · 23 060 · 1,9; 50 %: 38 g · 3,4 A · 25 W · 39 736 · 1,5; 80 %: 63 g · 6,4 A · 47 W · 49 849 · 1,3; 100 %: 79 g · 9,3 A · 67 W · 56 258 · 1,2 | [TM-M1002] B |

Einordnung: Die unabhängige Messung ([OL-Props]) liegt beim Maximalschub bei etwa der Hälfte der Herstellerangaben
(BetaFPV, T-Motor bei 4,2–4,35 V). Für die Sim ist 18–25 g je 0702/0802-Motor an 31 mm und frischem 1S der
realistische Vollgasschub; ein 25-g-Whoop hat damit Schub/Gewicht ≈ 3–4 und schwebt bei 30–45 % Gas (Air65: 30–35 %
[OL-Air65]).

### 3.2 Toothpick 2–3″ (2S–4S)

| Motor | Prop | Zellen | Messpunkte | Quelle |
|---|---|---|---|---|
| EX1103 11000 KV | Gemfan 2023 (2″ 3-Blatt) | 2S 7,4 V | 1,07 A: 23 g (2,9 g/W) · 3,17 A: 57 g (2,4) · 5,04 A: 82 g (2,2) · 7,23 A: 107 g (2,0) · 9,2 A: 122 g · 68 W (1,8) | [HM-EX1103] B |
| F1203 7000 KV | Gemfan 2023-3 | 2S 8,4 V | 50 %: 36 g · 2,2 A · 19 W · 21 697 · 1,9; 80 %: 83 g · 4,6 A · 39 W · 30 586 · 2,1; 100 %: 105 g · 6,8 A · 57 W · 35 830 · 1,85 | [TM-F1203] B |
| F1203 7000 KV | Gemfan 2023-3 | 3S 12,6 V | 50 %: 58 g · 3,3 A · 42 W · 1,4; 100 %: 158 g · 10,6 A · 133 W · 45 398 · 1,2 | [TM-F1203] B |
| M1104 7500 KV | Gemfan 3028-3 | 2S 8,4 V | 20 %: 24 g · 0,7 A · 6 W · 4,3; 40 %: 64 g · 2,2 A · 18 W · 3,4; 80 %: 176 g · 8,2 A · 68 W · 2,6; 100 %: 231 g · 12,7 A · 106 W · 26 811 · 2,2 | [TM-M1104] B |
| F1204 6500 KV | HQ 3020 | 2S 8,4 V | 50 %: 55 g · 1,8 A · 15 W · 17 420 · 3,6; 80 %: 117 g · 4,6 A · 38 W · 3,1; 100 %: 156 g · 7,0 A · 58 W · 28 338 · 2,7 | [TM-F1204] B |
| F1204 5000 KV | HQ 3020 | 3S 12,2 V | 50 %: 67 g · 1,5 A · 18 W · 19 201 · 3,7; 80 %: 132 g · 3,9 A · 48 W · 2,8; 100 %: 172 g · 6,0 A · 72 W · 29 834 · 2,4 | [TM-F1204] B |
| F1303 5000 KV | HQ 3018 | 3S 11,9 V | 50 %: 66 g · 1,8 A · 22 W · 22 735 · 3,0; 80 %: 133 g · 4,9 A · 58 W · 2,3; 100 %: 177 g · 7,5 A · 87 W · 34 651 · 2,0 | [TM-F1303] B |
| F1404 2900 KV | Gemfan 3028-3 | 4S 16,8 V | 50 %: 101 g · 1,5 A · 25 W · 18 626 · 4,0; 80 %: 209 g · 3,9 A · 66 W · 3,2; 100 %: 295 g · 6,4 A · 107 W · 31 327 · 2,8 | [TM-F1404] B |
| F1404 3800 / 4600 KV | Gemfan 3016 | 4S | 100 %: 325 g · 16,2 A · 255 W bzw. 345 g · 17,5 A · 274 W (≈ 1,3 g/W) | [TM-F1404] B |

### 3.3 Cinewhoop 3″ und 3,5″ (Duct)

„Ohne Rahmen“ = Prop allein am Prüfstand, „mit Rahmen“ = im Duct des Oddity-Rahmens (T-Motor-Messreihen). Temperaturen dieser
Reihe: Gehäuse nach 10 s bei 80 % Gas.

| Motor | Prop | Zellen | Messpunkte | Quelle |
|---|---|---|---|---|
| Cine30 | T76S-3 (76 mm) ohne Rahmen | 6S 24 V | 20 %: 57 g · 0,6 A · 15 W · 3,8 g/W; 50 %: 291 g · 6,0 A · 145 W · 26 198 · 2,0; 80 %: 577 g · 16,4 A · 390 W · 1,5; 100 %: 702 g · 24,9 A · 591 W · 40 346 · 1,19 | [TM-Cine30] B |
| Cine30 | T76S-3 mit Rahmen | 6S | 50 %: 284 g · 121 W · 2,35; 100 %: 699 g · 22,3 A · 524 W · 1,33 | [TM-Cine30] B |
| F2004 3000 KV | Gemfan D76 | 4S 16 V | 50 %: 253 g · 5,5 A · 86 W · 22 447 · 2,9; 80 %: 527 g · 15,0 A · 234 W · 2,25; 100 %: 795 g · 26,6 A · 410 W · 39 050 · 1,94 | [TM-F2004] B |
| F2203.5 2850 KV | HQ Duct 75 mm 6-Blatt | 4S 16 V | 50 %: 206 g · 3,6 A · 58 W · 21 610 · 3,5; 80 %: 492 g · 12,1 A · 191 W · 2,6; 100 %: 764 g · 22,6 A · 351 W · 40 260 · 2,2 | [TM-F2203] B |
| Cine35 | HQ T90-3 (90 mm) ohne Rahmen | 6S 24 V | 20 %: 60 g · 0,5 A · 12 W · 5,0; 50 %: 320 g · 4,3 A · 103 W · 21 895 · 3,1; 80 %: 665 g · 13,1 A · 313 W · 2,1; 100 %: 868 g · 21,4 A · 507 W · 36 002 · 1,7 | [TM-Cine35] B |
| Cine35 | Gemfan D90-3 ohne / mit Rahmen | 6S | 50 %: 315 g · 101 W (3,1) / 320 g · 90 W (3,6); 100 %: 863 g · 476 W (1,8) / 883 g · 394 W (2,2) | [TM-Cine35] B |
| P1804 3400 KV | Gemfan D90-3 | 4S 16,8 V | 20 %: 74 g · 1,5 A · 25 W · 3,0; 40 %: 185 g · 4,3 A · 69 W · 2,7; 80 %: 428 g · 14,9 A · 236 W · 1,8; 100 %: 584 g · 22,3 A · 352 W · 30 173 · 1,7 | [TM-P1804] B |

Duct-Wirkung (gleicher Motor, gleiches Gas, mit gegen ohne Rahmen, g/W bei 50 %): T90-3 +8 %, D90-3 +14 %, DT90-5 +11 %,
D63-3 +15 %, T76S-3 +17 %, DT76-3 +6 %, D75-3 +23 %. Bei 100 % gleicher Schub (±4 %) bei 10–17 % weniger Leistung. Drehzahl
bei gleichem Schub nahezu gleich (T90-3: 36 002 gegen 36 317 U/min bei 868 gegen 867 g), der Duct senkt also vor allem
das Moment.

### 3.4 4″ und 6″

| Motor | Prop | Zellen | Messpunkte | Quelle |
|---|---|---|---|---|
| F1408 II 2800 KV | Gemfan 4023-3 | 4S | 50 %: 240 g · 4,0 A · 67 W · 17 429 · 3,6; 80 %: 478 g · 10,6 A · 177 W · 2,7; 100 %: 626 g · 16,3 A · 271 W · 27 159 · 2,3 | [TM-F1408] B |
| F2004 3000 KV | Gemfan 4023 | 4S | 50 %: 281 g · 4,9 A · 79 W · 23 132 · 3,6; 80 %: 609 g · 14,4 A · 227 W · 2,7; 100 %: 890 g · 25,5 A · 395 W · 40 309 · 2,25 | [TM-F2004] B |
| F2004 1700 KV | T-Motor T4944 (4,9″) | 6S | 100 %: 945 g · 21,3 A · 503 W · 31 604 · 1,9 | [TM-F2004] B |
| Cine60 | T-Motor T6143-3 (6,1″) | 6S 24 V | 20 %: 117 g · 0,8 A · 20 W · 5,9; 50 %: 641 g · 6,8 A · 163 W · 15 190 · 3,9; 80 %: 1341 g · 20,3 A · 480 W · 2,8; 100 %: 1789 g · 33,4 A · 782 W · 24 354 · 2,3 | [TM-Cine60] B |
| P2505 1850 KV | T-Motor T6143-3 | 6S | 30 %: 342 g · 3,0 A · 76 W · 4,5; 50 %: 809 g · 13,1 A · 328 W · 2,5; 100 %: 1628 g · 41,6 A · 1015 W · 23 794 · 1,6 | [TM-P2505] B |

### 3.5 5″ (4S und 6S)

| Motor | Prop | Zellen | Messpunkte | Quelle |
|---|---|---|---|---|
| F60 Pro V 1950 KV | T-Motor T5147-3 | 6S 25,2 V | 20 %: 258 g · 2,6 A · 66 W · 11 979 · 3,9; 40 %: 636 g · 8,6 A · 215 W · 18 222 · 3,0; 80 %: 1560 g · 30,5 A · 758 W · 27 582 · 2,1; 100 %: 1990 g · 49,3 A · 1216 W · 31 401 · 1,64 | [TM-F60] B |
| F60 Pro V 1950 KV | Gemfan 51466-3 | 6S | 20 %: 255 g · 2,4 A · 61 W · 12 682 · 4,2; 40 %: 639 g · 8,5 A · 214 W · 3,0; 80 %: 1531 g · 28,2 A · 703 W · 2,2; 100 %: 1958 g · 46,9 A · 1159 W · 33 163 · 1,69 | [TM-F60] B |
| F60 Pro V 1750 KV | T-Motor T5147-3 | 6S | 20 %: 227 g · 2,1 A · 54 W · 11 132 · 4,2; 60 %: 911 g · 13,7 A · 342 W · 21 409 · 2,7; 100 %: 1883 g · 40,5 A · 1003 W · 30 527 · 1,88 | [TM-F60] B |
| F60 Pro V 2550 KV | T-Motor T5147-3 | 4S 16,8 V | 20 %: 188 g · 2,9 A · 49 W · 3,8; 60 %: 784 g · 18,3 A · 304 W · 2,6; 100 %: 1629 g · 50,7 A · 825 W · 28 250 · 1,98 | [TM-F60] B |
| V2207 V2 1750 KV | T-Motor T5147 | 6S | 50 %: 620 g · 6,8 A · 160 W · 18 107 · 3,9; 80 %: 1314 g · 23,3 A · 537 W · 2,45; 100 %: 1681 g · 36,6 A · 816 W · 29 021 · 2,06 | [TM-V2207] B |
| V2306 V2 2400 KV | T-Motor T5147 | 4S | 50 %: 477 g · 8,0 A · 125 W · 15 652 · 3,8; 80 %: 932 g · 22,8 A · 344 W · 2,7; 100 %: 1221 g · 34,1 A · 495 W · 24 526 · 2,5 | [TM-V2306] B |
| PV4 2406 2060 KV | T-Motor F5146 | 6S | 20 %: 128 g · 1,3 A · 31 W · 9 648 · 4,1; 50 %: 700 g · 9,5 A · 227 W · 20 897 · 3,1; 80 %: 1472 g · 28,1 A · 664 W · 2,2; 100 %: 1993 g · 46,6 A · 1087 W · 33 750 · 1,83 | [TM-P2406] B |
| Five33 Headsup 2207 (1845 KV gemessen, 29,3 g) | HQ V1S 5×4,3×3 PC | 6S ≈ 24 V | 25 %: 251 g · 3,76 A · 91 W · 12 733 · 2,8; 50 %: 720 g · 10,55 A · 255 W · 21 541 · 2,8; 75 %: 1352 g · 24,27 A · 581 W · 29 822 · 2,3; 100 %: 1960 g · 42,79 A · 1016 W · 36 070 · 1,9 | [MQTB-287] A |
| Five33 Headsup 2207 | Gemfan Hurricane 51466 | 6S | 25 %: 254 g · 92 W · 2,8; 50 %: 722 g · 269 W · 2,7; 100 %: 1786 g · 49,47 A · 1159 W · 33 796 · 1,5 | [MQTB-287] A |
| Brother Hobby R3 2207 (1690 KV gemessen, 30,5 g) | HQ V1S 5×4,3×3 PC | 6S ≈ 24 V | Leerlauf: 49 g · 1,44 A · 35 W · 5 690; 25 %: 170 g · 2,58 A · 62 W · 10 912 · 2,7; 50 %: 511 g · 6,87 A · 166 W · 18 810 · 3,1; 75 %: 994 g · 15,54 A · 373 W · 26 356 · 2,7; 100 %: 1523 g · 28,13 A · 668 W · 32 029 · 2,3 | [MQTB-236] A |
| Brother Hobby R3 2207 | zehn 5″-Props (5×3,7×4 … 5,1×5×3) | 6S | 100 %: 1349–1640 g bei 25–38 A; 50 %: 480–589 g bei 150–196 W (2,9–3,2 g/W) | [MQTB-236] A |

### 3.6 7″ Long Range (6S)

| Motor | Prop | Zellen | Messpunkte | Quelle |
|---|---|---|---|---|
| F90 1300 KV (2806.5) | HQ 7×4×3 | 6S 24 V | 30 %: 353 g · 2,1 A · 52 W · 8 192 · 6,8; 50 %: 948 g · 8,8 A · 213 W · 12 801 · 4,45; 80 %: 1865 g · 27,7 A · 659 W · 2,8; 100 %: 2361 g · 45,1 A · 1059 W · 19 617 · 2,23 | [TM-F90] B |
| F90 1300 KV | Gemfan 7042 | 6S | 30 %: 320 g · 1,8 A · 43 W · 7,4; 50 %: 836 g · 7,2 A · 174 W · 13 240 · 4,8; 100 %: 2117 g · 41,3 A · 971 W · 21 357 · 2,18 | [TM-F90] B |
| F100 1100 KV (2808) | Gemfan 7040-3 | 6S | 30 %: 402 g · 2,5 A · 62 W · 9 515 · 6,5; 50 %: 714 g · 6,0 A · 149 W · 12 541 · 4,8; 80 %: 1399 g · 16,3 A · 406 W · 3,4; 100 %: 2034 g · 28,5 A · 700 W · 20 433 · 2,9 | [TM-F100] B |
| V2808 1300 KV | Gemfan 7040-3 | 6S | 20 %: 180 g · 2,0 A · 51 W · 7 976 · 3,5; 40 %: 601 g · 6,5 A · 164 W · 12 281 · 3,7; 80 %: 1808 g · 27,9 A · 691 W · 2,6; 100 %: 2467 g · 47,4 A · 1161 W · 23 322 · 2,13 | [TM-V2808] B/C |
| V2306 V3 1500 KV | Gemfan 7042-2 (2-Blatt) | 6S | 20 %: 120 g · 0,9 A · 21 W · 5 832 · 5,7; 50 %: 685 g · 9,1 A · 218 W · 13 078 · 3,15; 100 %: 1435 g · 39,2 A · 918 W · 18 702 · 1,56 | [TM-V2306v3] B |
| Cine66 925 KV (2812) | HQ 8×4×3 · HQ 9×5×3 | 6S | 100 %: 2451 g · 32,2 A · 801 W · 17 668 bzw. 2923 g · 50,5 A · 1237 W · 13 296 | [TM-Cine66] B |

### 3.7 X-Class 9–13″

| Motor | Prop | Zellen | Messpunkte | Quelle |
|---|---|---|---|---|
| V3115 900 KV | T-Motor T10,5×5×3 | 6S | 20 %: 305 g · 1,9 A · 45 W · 3 767 · 6,8; 50 %: 1782 g · 15,9 A · 377 W · 8 391 · 4,7; 100 %: 4735 g · 83,3 A · 1894 W · 13 479 · 2,5 | [TM-V3115] B (Zeilen 60–80 % mit fehlerhafter Leistungsspalte) |
| V3115 400 KV | T-Motor T10,5×5×3 | 12S 48 V | 20 %: 272 g · 0,7 A · 35 W · 7,8; 50 %: 1646 g · 6,7 A · 321 W · 8 220 · 5,1; 80 %: 3555 g · 23,3 A · 1111 W · 3,2; 100 %: 4817 g · 41,8 A · 1982 W · 13 713 · 2,4 | [TM-V3115] B |
| V3115 1050 KV | HQ 9×5×3 | 6S | 20 %: 298 g · 2,3 A · 55 W · 5,4; 50 %: 1582 g · 17,3 A · 411 W · 9 564 · 3,85; 80 %: 3359 g · 51,9 A · 1207 W · 2,8; 100 %: 4409 g · 83,6 A · 1899 W · 15 568 · 2,3 | [TM-V3115] B |
| V3120 500 KV | HQ 10×5×3 | 12S | 20 %: 436 g · 1,5 A · 70 W · 6,2; 50 %: 2227 g · 10,9 A · 521 W · 9 730 · 4,3; 80 %: 4876 g · 36,4 A · 1730 W · 2,8; 100 %: 6716 g · 68,0 A · 3200 W · 16 260 · 2,1 | [TM-V3120] B |
| T-Motor 4214 400 KV | C13×10×3 | 8S 29,6 V | 30 %: 505 g · 1,74 A · 39 W · 2 828; 50 %: 1391 g · 6,70 A · 160 W · 4 550 · 8,7; 80 %: 3140 g · 22,6 A · 533 W · 5,9; 100 %: 4414 g · 38,75 A · 886 W · 7 966 · 5,0 | [TM-4214] C: η_ges ≈ 0,70 und gemessenes Moment ≈ elektrische Leistung – physikalisch zu gut |
| T-Motor 4214 380 KV | C15×8×3 | 8S | 50 %: 1816 g · 200 W · 9,1; 100 %: 5641 g · 48,7 A · 1058 W · 7 538 · 5,3 | [TM-4214] C |

### 3.8 Streuung zwischen Quellen (5″ und 7″)

Bei ähnlichem Schub (5″, 170–260 g je Motor) liefern die Quellen:

| Quelle | Hardware | Schub | g/W | η_ges |
|---|---|---|---|---|
| MQTB R3 2207, 25 % | älterer ESC (XRotor 40A, „Damped Light“), 2017–2019 | 170 g | 2,7 | 0,20 |
| MQTB Five33 2207, 25 % | dito | 251 g | 2,8 | 0,24 |
| T-Motor F60 Pro V, 20 % | aktueller Hersteller-Prüfstand | 227–258 g | 3,9–4,2 | 0,34–0,37 |

Das MQTB-Leerlaufmessband zeigt die Ursache: 1,85–1,92 A bei 6 700 U/min, T-Motor 2,1–2,6 A erst bei 11 000–12 700 U/min.
Ältere ESC und Motoren verbrauchen im Teillast-Bereich deutlich mehr. Für die Sim ist ein Mittelwert angemessen.

Beim 7″ ist die Spanne noch größer: V2808 (Tabelle ab 20 %) ergibt bei 225 g je Motor ≈ 3,6 g/W, F90 und F100 (Tabellen
erst ab 30 %, 320–400 g) ergeben extrapoliert 8–10 g/W. Der Hersteller-Flugzeitwert eines 7,5″-Long-Range (Abschnitt 7)
liegt bei ≈ 5,3 g/W gesamt – das ist der beste Anker.

---

## 4. Propeller-Beiwerte

### 4.1 Abgeleitete Beiwerte je Prop (Mittelwert 35–85 % Gas)

`c_p` aus Drehmoment (tq) oder Motormodell (mm, Abschnitt 1.2). 9N12P-Reihen drehzahlkorrigiert (Abschnitt 1.4).

| Prop | D | P/D | Blätter | c_t | c_p | FM | Grundlage |
|---|---|---|---|---|---|---|---|
| 31 mm, Gemfan 1219 / T-Motor M12199 | 31 mm | ≈ 1,5 | 3 | 0,25–0,30 | 0,26–0,32 (mm) | 0,28–0,46 | M0703, M0803 |
| 31 mm, Gemfan 1220 | 31 mm | ≈ 1,6 | 4 | 0,245 | 0,41 (mm) | 0,21 | M0803 |
| 35 mm, Gemfan 35-3 / 35-4 | 35 mm | – | 3 / 4 | 0,22 / 0,19 | 0,22 / 0,25 (mm) | 0,34 / 0,24 | M0803 |
| 40 mm, Gemfan 1611-3 / 1614-3 | 40 mm | 0,7–0,9 | 3 | 0,18–0,22 | 0,21–0,34 (mm) | 0,19–0,30 | M1002 (1S und 2S) |
| 45 mm 2-Blatt | 45 mm | – | 2 | 0,13–0,14 | 0,13–0,16 (mm) | 0,22–0,29 | M1002 |
| 2″ Gemfan 2015-2 | 51 mm | 0,75 | 2 | 0,11–0,12 | 0,10–0,12 (mm) | 0,22–0,30 | M1002 |
| 2″ Gemfan 2023-3 | 51 mm | 1,15 | 3 | 0,26 | 0,33–0,38 (mm) | 0,25–0,29 | F1203 |
| 2″ Gemfan 2020-4 | 51 mm | 1,0 | 4 | 0,34 | 0,43–0,47 (mm) | 0,30–0,33 | F1203 |
| 3″ Gemfan 3018-2 / HQ 3018 | 76 mm | 0,6 | 2 | 0,09–0,12 | 0,06–0,08 (mm) | 0,30–0,37 | F1203, F1303 |
| 3″ Gemfan 3016 / HQ 3020 | 76 mm | 0,53–0,67 | 2–3 | 0,12 | 0,07–0,08 (mm) | 0,38–0,41 | F1204 |
| 3″ Gemfan 3028-3 | 76 mm | 0,93 | 3 | 0,19–0,21 | 0,12–0,15 (mm) | 0,45–0,51 | F1404, F1408 |
| 3″ HQ 3×3×3 | 76 mm | 1,0 | 3 | 0,28–0,30 | 0,32–0,36 (mm) | 0,31–0,32 | F1404 3800/4600 |
| 3,5″ HQ 3520-3 | 89 mm | 0,57 | 3 | 0,17–0,18 | 0,10–0,12 (mm) | 0,45–0,49 | F1404, F1408 |
| 4″ Gemfan 4024-2 / 4042-2 | 102 mm | 0,6 / 1,05 | 2 | 0,13–0,14 | 0,07–0,075 (mm) | 0,47–0,49 | F1404, F1408 |
| 4″ Gemfan 4023-3 | 102 mm | 0,58 | 3 | 0,15–0,17 | 0,08–0,10 (mm) | 0,42–0,55 | F1404, F1408, F2004, F2203.5 |
| 5″ low pitch T5143 | 130 mm | 0,84 | 3 | 0,14–0,17 | 0,09–0,12 (mm) | 0,38–0,51 | V2207, V2306, P2207 |
| 5″ HQ 5×4,3×3 | 127 mm | 0,86 | 3 | 0,165–0,177 | 0,105–0,115 (mm) | 0,43–0,47 | MQTB (A) |
| 5″ T5146 / T5147 / Gemfan 51466 | 130 mm | 0,90–0,92 | 3 | 0,18–0,22 | 0,09–0,14 (mm) | 0,43–0,61 | V2207, V2207.5, V2306, F60 |
| 5″ F5146 / P49436 | 124–130 mm | 0,9 | 3 | 0,17–0,19 | 0,089–0,107 (tq) | 0,54–0,57 | PV4 2406 (A-nah, Drehmoment) |
| 5″ 4-Blatt 5×3,7×4 | 127 mm | 0,74 | 4 | 0,198 | – | – | MQTB R3 75 % |
| 5″ HQ 5040-6 | 127 mm | 0,8 | 6 | 0,37 | 0,38–0,40 (mm) | 0,39–0,42 | V2808 |
| 6″ T6143-3 / HQ 6×3×3 | 152–155 mm | 0,5–0,7 | 3 | 0,11–0,15 | 0,055–0,09 | 0,34–0,56 | Cine60 (tq), P2505, V2808 |
| 6″ HQ 6×4,3 | 152 mm | 0,72 | 2 | 0,137 | 0,061 (tq) | 0,59 | Cine60 |
| 7″ Gemfan 7042-2 | 178 mm | 0,6 | 2 | 0,12–0,16 | 0,06–0,085 (mm) | 0,41–0,62 | F90, V2306 V3 |
| 7″ Gemfan 7040-3 | 178 mm | 0,57 | 3 | 0,12–0,135 | 0,059–0,074 (mm) | 0,41–0,56 | F100, V2808, P2505 |
| 7″ HQ 7×4×3 | 178 mm | 0,57 | 3 | 0,173 | 0,081–0,090 (mm) | 0,57–0,63 | F90 |
| 7,5″ T-Motor C7,5×4,6×3 | 190 mm | 0,61 | 3 | 0,163 | 0,095 (mm) | 0,49 | V2306 V3 |
| 8″ Gemfan 8040-3 / HQ 8045-3 | 203 mm | 0,5 / 0,56 | 3 | 0,13–0,14 / 0,17–0,19 | 0,07–0,08 / 0,11 (mm) | 0,44–0,56 | V2808, F100, Cine66 |
| 9″ HQ 9050-3 | 229 mm | 0,56 | 3 | 0,19 | 0,091 (tq) | 0,64–0,65 | V3115 |
| 9,5″ T9,5×5×3 | 241 mm | 0,53 | 3 | 0,155 | 0,068 (tq) | 0,62–0,64 | V3115 |
| 10″ HQ 1050-3 | 254 mm | 0,5 | 3 | 0,16–0,17 | 0,073–0,076 (tq) | 0,62–0,66 | V3115, V3120 |
| 10″ HQ 1045-3 | 254 mm | 0,45 | 3 | 0,12–0,13 | 0,048–0,051 (tq) | 0,60–0,64 | V3115 |
| 10,5″ T10,5×5×3 | 267 mm | 0,48 | 3 | 0,144–0,151 | 0,059–0,064 (tq) | 0,64–0,67 | V3115, V3120, Cine77 |
| 11″ HQ 1145-3 | 279 mm | 0,41 | 3 | 0,121–0,129 | 0,046–0,051 (tq) | 0,64–0,66 | V3120 |
| 12″ 12×4 | 305 mm | 0,33 | 2 | 0,082 | 0,026–0,027 (tq) | 0,62–0,63 | Cine77 |
| 13″ C13×10×3 | 330 mm | 0,77 | 3 | ≈ 0,17 | ≈ 0,08 (tq, unplausibel) | – | 4214 (C) |

Ducted-Props (Cinewhoop) in dieser Definition (Durchmesser = Prop): c_t 0,26–0,46 und c_p 0,18–0,47 bei FM 0,29–0,60
(Cine25/30/35 mit Drehmoment, F1408/F2004/F2203.5/P1804 mit Motormodell). Die Duct-Props sind hochsteigend und breit; das
Oddity-Duct selbst ändert c_t kaum und senkt c_p um ≈ 10–15 % (Abschnitt 3.3).

### 4.2 Einfluss der Blattzahl (UIUC, gleiche Blattgeometrie, 5″)

Statische Messungen der University of Illinois, Mittelwert der oberen 40 % des Drehzahlbereichs [UIUC-V2]:

| Prop | 2 Blätter c_t / c_p / FM | 3 Blätter | 4 Blätter | Faktor 3 : 2 | Faktor 4 : 2 |
|---|---|---|---|---|---|
| DA4022 5×3,75 | 0,151 / 0,094 / 0,44 | 0,207 / 0,131 / 0,51 | 0,245 / 0,166 / 0,52 | c_t 1,37 · c_p 1,39 | c_t 1,62 · c_p 1,77 |
| DA4052 5×3,75 | 0,117 / 0,074 / 0,38 | 0,150 / 0,099 / 0,42 | 0,175 / 0,117 / 0,44 | c_t 1,28 · c_p 1,34 | c_t 1,50 · c_p 1,58 |
| NR640 5″ 15° | 0,089 / 0,046 / 0,41 | 0,118 / 0,061 / 0,47 | 0,137 / 0,080 / 0,45 | c_t 1,33 · c_p 1,33 | c_t 1,54 · c_p 1,74 |

Faustregel: `c_t ∝ B^0,7`, `c_p ∝ B^0,8`. Die FM wächst von 2 auf 3 Blätter leicht und fällt ab 4 wieder. Mehr Blätter
heißt bei gleichem Durchmesser mehr Schub je Drehzahl, mehr Moment, weniger Drehzahl bei gleichem Motor und etwas
geringere Endgeschwindigkeit.

### 4.3 Einfluss der Steigung (UIUC DA4052, 5″, 2 Blätter)

| P/D | c_t | c_p | FM |
|---|---|---|---|
| 0,53 (5×2,65) | 0,089 | 0,039 | 0,48 |
| 0,75 (5×3,75) | 0,117 | 0,074 | 0,38 |
| 0,98 (5×4,92) | 0,137 | 0,107 | 0,34 |

Weitere UIUC-Kleinprops (2 Blätter): GWS 2,5×1 0,074 / 0,033 · GWS 3×2 0,137 / 0,069 · GWS 3×3 0,195 / 0,142 ·
GWS 5×4,3 0,150 / 0,076 · Micro Invent 3,2×2,2 0,121 / 0,057 · MIT 5×4 0,179 / 0,116. Faustregel statisch: c_t steigt
mit P/D etwa linear, c_p etwa quadratisch; hohe Steigung kostet im Schweben Effizienz und bringt Endgeschwindigkeit.

### 4.4 Reynolds-Zahl und Größe

- UIUC misst 5″-Props bei 1 500–8 000 U/min (Re ≈ 20–60 k). FPV-Props laufen bei 10 000–35 000 U/min; dort liegen FM und
  c_t höher (APC-Rechnung 5×4,5E: FOM 0,60 bei 10 000, 0,63 bei 30 000 U/min [APC]).
- Kleine Props haben systematisch schlechtere FM: Whoop 0,2–0,35 (Prüfstands-Projekt für 1S-Whoops nennt 0,2–0,3 als
  typisch [WTS]), 3–5″ 0,4–0,55, 7″ 0,45–0,6, 9–12″ 0,6–0,67.

### 4.5 Vorwärtsfahrt: Nullschub-Geschwindigkeit

APC-Rechendaten (`J = V/(n·D)`) [APC]:

| Prop | P/D | J bei c_t/2 | J bei c_t = 0 | J₀ − P/D |
|---|---|---|---|---|
| APC 5×4,5E | 0,90 | 0,75 | 1,09 | 0,19 |
| APC 5×5E | 1,00 | 0,85 | 1,17 | 0,17 |
| APC 7×4E | 0,57 | 0,45 | 0,72 | 0,15 |
| APC 13×10E | 0,77 | 0,64 | 0,93 | 0,16 |

Der Nullschub liegt bei `V₀ ≈ (P + 0,15…0,19·D)·n`; der Schub hat sich bei ≈ 0,62–0,72·J₀ halbiert. Propwash nutzt
`ZERO_LIFT_PITCH = 0,20` – das liegt am oberen Rand und ist vertretbar.

### 4.6 Material

Belastbare Zahlen gibt es kaum; die Hersteller vergleichen nicht sauber. Gesicherte Aussagen:

| Material | Steifigkeit | Effizienz, Schub | Haltbarkeit | Geräusch | Quelle |
|---|---|---|---|---|---|
| Polycarbonat (PC, „durable“) | weich; Blattspitzen biegen bei Vollgas, wirksame Steigung sinkt | im Teillastbereich wie GF (MQTB: HQ 5×4×3 GF 1511 g gegen HQ V1S 5×4,3×3 PC 1523 g bei 28 A), oben etwas weniger Schubspitze | biegt statt zu brechen, bricht sauber | etwas weicher | [MQTB-236], [UAV-Prop] |
| Glasfaser-Nylon (GF) | steifer | behält die Steigung bei hoher Drehzahl, knackigere Gasannahme | spröder, splittert eher | – | [UAV-Prop] |
| Kohlefaser (CF) | sehr steif, leicht | formstabil bei hoher Last; im Test eher leicht kürzere Flugzeit und etwas weniger Schub als gleiche Plastikprops | zerspringt scharfkantig | lauter, härter | [OL-CF], [UAV-Prop] |

Plausible Sim-Abstufung: gleiche c_t/c_p im mittleren Bereich, PC mit Schubverlust von einigen Prozent bei sehr hoher
Blattbelastung (Vollgas), GF neutral, CF neutral bis leicht schlechter, aber höhere Steifigkeit (Vibration, Crash
zerstört den Prop). Die Größe des PC-Abfalls ist geschätzt (2–5 %), nicht gemessen.

Seit 0.5.0 folgt der Crash-Verschleiß diesem Verhalten (PC verbiegt sich, Glasfaser-Nylon und hartes PC brechen, Carbon
zerspringt selten und gibt den Stoß an den Motor weiter): [`prop-wear.md`](prop-wear.md).

### 4.7 Propmassen

| Prop | Masse | Quelle |
|---|---|---|
| Gemfan 1219S 31 mm 3-Blatt | 0,18 g | Händlerangabe [GF-1219] |
| Gemfan 2023S 2″ 3-Blatt | 0,8 g | Händlerangabe [GF-2023] |
| Gemfan 3018 3″ 2-Blatt | 0,75–0,84 g | Händlerangabe [GF-3018] |
| Gemfan D90 3,5″ 3-Blatt (Duct) | 2,3 g | Händlerangabe [GF-D90] |
| Gemfan Hurricane 51466 | 4,2 g | Händlerangabe [GF-51466] |
| HQ 7×4×3 | 9,1 g | Händlerangabe [HQ-7x4x3] |
| T-Motor C8,5×5×3 (GF-Nylon) | 14,8 g | Hersteller [TM-Cprop] |
| T-Motor C9,5×5×3 (GF-Nylon) | 21,3 g | Hersteller [TM-Cprop] |
| HQ MacroQuad 10×5×3 | 23,3 g | Händlerangabe [HQ-10x5x3] |
| Gemfan 1310-3 (13″) | 41 g | Händlerangabe [GF-1310] |
| HQ X-Class 13×12×3 | 49,8 g | Händlerangabe [HQ-13x12x3] |

Zum Vergleich Propwash: `p31_3b` 0,28 g, `p3_3b` 1,8 g, `p51_3b` 4,3 g, `p7_2b` 6,8 g – passt.

### 4.8 Vergleich mit den Propwash-Basisprops

| Basis-Prop | Propwash c_t / c_p / FM | Referenz c_t / c_p / FM | Abweichung |
|---|---|---|---|
| `p31_3b` (31 mm, P 1,4″) | 0,09 / 0,072 / 0,27 | 0,25 / 0,30 / 0,28–0,35 | c_t ≈ −64 %, c_p ≈ −75 %, FM passt |
| `p31_4b` (31 mm, P 1,2″) | 0,10 / 0,085 / 0,26 | 0,22 / 0,33 / 0,21–0,26 | c_t ≈ −55 %, c_p ≈ −75 %, FM passt |
| `p3_3b` (76 mm, P 3,0″, P/D 1,0) | 0,09 / 0,085 / 0,30 | 0,28 / 0,34 / 0,31 | c_t −68 %, c_p −75 %, FM passt |
| `p51_3b` (130 mm, P 4,6″) | 0,12 / 0,06 / 0,49 | 0,18–0,20 / 0,09–0,12 / 0,45–0,57 | c_t −35 %, c_p −45 %, FM passt |
| `p7_2b` (178 mm, P 3,5″, P/D 0,5) | 0,09 / 0,036 / 0,53 | 0,10–0,12 / 0,045–0,055 / 0,5–0,6 (aus 7042 und P/D-Trend 4.3) | c_t −10 bis −25 %, c_p −20 bis −35 %, FM passt |

---

## 5. Antriebswirkungsgrad und Leerlaufverluste

### 5.1 Aus gemessenem Drehmoment (Akku-Gleichstrom → Welle)

| Motor | 20 % Gas | 30 % | 50 % | 70 % | 100 % | Quelle |
|---|---|---|---|---|---|---|
| PV4 2406 2060 KV, 5″, 6S | 0,33–0,63 | 0,55–0,60 | 0,65–0,67 | 0,65–0,70 | 0,66–0,69 | [TM-P2406] |
| Cine20/25 (4S, 2–2,5″ Duct) | 0,25–0,50 | 0,47–0,64 | 0,56–0,72 | 0,67–0,77 | 0,65–0,76 | [TM-Cine20], [TM-Cine25] |
| Cine30/35 (6S, 3–3,5″ Duct) | streut | 0,46–0,87 | 0,52–0,90 | 0,60–0,81 | 0,50–0,73 | [TM-Cine30], [TM-Cine35] |
| Cine60 (6S, 6″) | 0,41–0,74 | 0,63–0,77 | 0,70–0,78 | 0,74–0,77 | 0,68–0,73 | [TM-Cine60] |
| Cine77 (12S/8S/6S, 9,5–12″) | 0,59–0,74 | 0,69–0,81 | 0,72–0,83 | 0,71–0,84 | 0,58–0,80 | [TM-Cine77] |
| V3115/V3120 (6S–12S, 8,5–11″), Median in Klammern | 0,42–0,69 (0,53) | 0,50–0,71 (0,65) | 0,66–0,79 (0,74) | 0,68–0,81 (0,75) | 0,61–0,82 (0,70) | [TM-V3115], [TM-V3120] |

Das Motormodell mit Datenblatt-R und -I₀ ergibt dagegen 0,76–0,82 für 5″ im mittleren Bereich – also etwa 10–15
Prozentpunkte zu gut.

### 5.2 Wirksamer Leerlaufstrom

Rechnet man die gemessenen Verluste (Akkuleistung minus Wellenleistung minus Kupfer mit Datenblatt-R, ESC 95 %) in einen
Leerlaufstrom um, ergibt sich (V3115, PV4 2406):

| Motor | I₀ Datenblatt @10 V | wirksam bei 20–30 % | bei 50 % | bei 100 % |
|---|---|---|---|---|
| V3115 400 KV | 0,59 A | 0,9–1,3 A | 1,2–1,6 A | 1,0–5 A |
| V3115 640 KV | 1,10 A | 2,2–3,2 A | 2,7–3,0 A | 3,3–8,6 A |
| V3115 900 KV | 1,57 A | 3,3–3,7 A | 3,6–8 A | 8–17 A |
| PV4 2406 2060 KV | 1,9 A | 1,8–3,4 A | 4,3 A | 8,8 A |

Der Faktor 2–3,5 im mittleren Bereich enthält ESC-Schaltverluste, Wirbelströme unter Last und die Ungenauigkeit des
Datenblatt-R. Propwash rechnet `I_0 = 0,03·I_max` (5″: 1,35 A, V3115 900 KV: 2,5 A) ohne Drehzahlabhängigkeit.

### 5.3 Drehzahl unter Last bei Vollgas

`U/min(100 %) / (KV · U_Versorgung)`:

| Klasse | Bereich | Median | Quelle |
|---|---|---|---|
| Whoop 1S (9N12P, korrigiert) | 0,63–0,82 | ≈ 0,75 | M0703, M0803, M1002 |
| 3″ 4S (1404/1408, korrigiert) | 0,64–0,87 | ≈ 0,75 | F1404, F1408 |
| 5″ 6S | 0,64–0,82 | ≈ 0,72 | V2207, V2306, F60, P2207, MQTB (0,79–0,82) |
| 5″ 4S | 0,66–0,74 | ≈ 0,70 | V2207/2306 2400–2550 KV |
| 7″ 6S | 0,53–0,77 | ≈ 0,68 | F90, F100, V2808 |
| X-Class | 0,64–0,82 | ≈ 0,72 | V3115, V3120 |

Das ist die Größe, die Vollgasschub, Vollgasstrom und Endgeschwindigkeit festlegt; in der Sim ergibt sie sich aus c_t,
c_p, KV und Widerständen. Mit den heutigen, zu niedrigen c_t/c_p der Basis-Props dreht die Sim bei Vollgas zu hoch.

---

## 6. Temperaturen

### 6.1 Hersteller-Prüfstand: Glocke nach 60 s Vollgas im Stand

| Motor | Leistung 100 % | Umgebung | Glocke | ΔT | Quelle |
|---|---|---|---|---|---|
| F1203 (2S/3S) | 57–136 W | 21 °C | 52–67 °C | 31–46 K | [TM-F1203] |
| F1204 (2S/3S) | 58–72 W | 8 °C | 45–53 °C | 37–45 K | [TM-F1204] |
| F1404 (4S–6S) | 107–262 W | 8–24 °C | 45–68 °C | 25–44 K | [TM-F1404] |
| F1408 II (3S–6S) | 145–386 W | 19 °C | 60–81 °C | 41–62 K | [TM-F1408] |
| F2004 | 395–503 W | 8 °C | 61–72 °C | 53–64 K | [TM-F2004] |
| F2203.5 | 350–650 W | 8–15 °C | 64–70 °C | 51–62 K | [TM-F2203] |
| V2207 V2 (6S/4S) | 500–930 W | 19 °C | 92–101 °C | 73–82 K | [TM-V2207] |
| V2207.5 V2 | 510–870 W | 12 °C | 72–91 °C | 60–79 K | [TM-V2207.5] |
| V2306 V2 | 440–850 W | 19–25 °C | 89–102 °C | 64–83 K | [TM-V2306] |
| V2306.5 V2 | 440–900 W | 12 °C | 85–92 °C | 73–80 K | [TM-V2306.5] |
| P2505 | 450–1190 W | 16 °C | 77–95 °C | 61–79 K | [TM-P2505] |
| F90 (2806.5, 7″) | 830–1090 W | 16–27 °C | 81–99 °C | 65–77 K | [TM-F90] |
| F100 (2808) | 700–1240 W | 26 °C | 89–98 °C | 63–72 K | [TM-F100] |
| Cine66 (2812) | 780–1600 W | 26 °C | 85–99 °C | 59–73 K | [TM-Cine66] |

Die Cine-Serie und PV4 2406 geben das Gehäuse nach nur 10 s bei 80 % an (63–152 °C, PV4 112–116 °C); diese Werte passen
nicht zu den 60-s-Werten der übrigen Serien und sind vermutlich an der Wicklung gemessen (Stufe C).

### 6.2 Grenzen und Praxis

- Magnete in FPV-Motoren: N52H (höchste Dauertemperatur 120 °C) oder N52SH (150 °C) [MAG]. Normales N52 nur 80 °C.
- Praxis: Nach einem Akku sollen sich die Glocken warm, nicht heiß anfühlen; 65 °C (2205–2306) bzw. 70 °C (2207–2507)
  Glockentemperatur gelten als Obergrenze [UTS-Heat].
- Propwash-Standards `derate_start_c = 130`, `max_temp_c = 150` passen zu SH-Magneten, wenn die Sim die
  Wicklungstemperatur meint. Eine Glocke von 85–100 °C nach 60 s Vollgas im Stand ist real; im Dauerbetrieb im Stand
  steigt die Wicklung weiter.

---

## 7. Flugzeit-Anker (Hersteller, Stufe C)

Nutzbare Energie: LiPo 85 % der Nennenergie (3,7 V je Zelle), Li-Ion 85–90 % (3,6 V je Zelle). Elektronik 2–8 W
abgezogen.

| Kopter | Abfluggewicht | Akku | Angabe | mittlere Leistung | g/W gesamt | η_ges Flug | Quelle |
|---|---|---|---|---|---|---|---|
| Whoop 65 mm (Air65, 0702 23000 KV, HQ 31 mm) | ≈ 25 g (17,25 g + Akku, geschätzt) | 1S 300 mAh LiHV | 4:30 min „aggressiv, 50–60 % Gas“ | ≈ 13 W | ≈ 1,9 | ≈ 0,18 (bei ≈ 8 g/Motor) | [OL-Air65] A |
| Meteor65 (0802SE 19500 KV) | ≈ 26–28 g (geschätzt) | 1S 300 mAh | 4,5 min (Hersteller) | ≈ 13 W | ≈ 2,0 | ≈ 0,12–0,15 | [BF-0802SE] C |
| Cine25 (2,5″ Duct, 4S) | 220 g | 4S 850 mAh | 14 min | ≈ 46 W | ≈ 4,8 | ≈ 0,4 | [TM-Cine25] C |
| Cine30 (3″ Duct, 6S) | 450 / 564 g | 6S 1350 / 3000 mAh | 13 / 20 min | ≈ 118 / 170 W | 3,8 / 3,3 | ≈ 0,37–0,39 | [TM-Cine30] C |
| Cine35 (3,5″ Duct, 6S) | 520 / 634 g | 6S 1350 / 3000 mAh | 15 / 28 min | ≈ 102 / 121 W | 5,1 / 5,2 | ≈ 0,47–0,53 | [TM-Cine35] C |
| 7,5″ Long Range (2809 1250 KV, HQ 7,5×3,7×3) | ≈ 1 600 g (705 g + 6S2P 21700) | 6S 8000 mAh Li-Ion | „ca. 30 min Schwebezeit ohne Last“ | ≈ 300 W | ≈ 5,3 | ≈ 0,40 (400 g/Motor) | [IF-C7] C |

Die Cine-Angaben liegen über den eigenen Prüfstandswerten der Motoren (η_ges Stand 0,23–0,39) und sind als Obergrenze
zu lesen; Flugzeiten realer Cinewhoops mit Kamera liegen darunter. Der Long-Range-Wert (η_ges ≈ 0,40 bei 400 g je
Motor) ist der beste Anker für 7–7,5″; bei kleinerer Scheibenbelastung (≈ 225 g je Motor) liegt η_ges tiefer, weil
die Festverluste stärker wiegen.

---

## 8. Einordnung des Besitzer-Beispiels „Deadcat LR 7″ · 8S“

| Größe | Konfigurator | Referenz | Bewertung |
|---|---|---|---|
| Schub/Gewicht 11,7 : 1 (≈ 2,62 kg/Motor bei 897 g) | – | 7″ an 6S 1100–1300 KV: 2,0–2,5 kg je Motor bei 28–47 A (F90, F100, V2808); ein 8S-7″ mit ≈ 1000 KV liegt ähnlich | am oberen Rand, eher 9–11 |
| Vollgasstrom 137,5 A (≈ 34 A bzw. ≈ 1 kW je Motor) | – | F90 1300 KV 6S: 45 A, 1059 W, 2361 g | plausibel |
| Schwebegas 21,7 % | – | bei Schub/Gewicht 10–12 schweben reale Kopter bei ≈ 20–30 % | plausibel |
| Schwebezeit 24,2 min | – | 224 g/Motor, η_ges Flug 0,30–0,38 → 146–183 W → 18–23 min mit 55 Wh nutzbar (8S 2200 mAh LiPo) | ≈ 5–35 % zu lang |
| Reiseflug 10,5 min | – | Mehrrotorer brauchen bei mäßiger Fahrt zunächst weniger, erst bei hoher Fahrt mehr Leistung als im Schweben [PWR] | Verhältnis Reise/Schwebe 0,43 heißt ≈ 2,3-fache Schwebeleistung – nur bei hoher Reisegeschwindigkeit plausibel |
| Spitze 145 km/h | – | Nullschub-Fahrt 7×4″ bei 20 000 U/min ≈ (4 + 0,15…0,19·7)″·n ≈ 155–160 km/h; reale Spitze geschätzt 75–90 % davon | plausibel, eher obere Grenze |
| Motortemperatur 124 °C Vollgas im Stand | – | Glocke nach 60 s: 81–99 °C (F90); Wicklung im Dauerbetrieb höher | als Wicklungs-Endwert vertretbar, als Glocke nach 1 min zu hoch |

---

## 9. Folgerungen für die Kalibrierung

Hinweise, keine Vorgabe für den Code:

1. **Prop-Beiwerte anheben** auf die Werte aus 0 bzw. 4.1, jeweils mit dem passenden c_p, sodass FM in der Spanne der
   Klasse bleibt. Kontrollgröße nach der Änderung: Drehzahl unter Last bei Vollgas (5.3) und Schwebedrehzahl. 5″ bei
   130–150 g je Motor: ≈ 8 500–9 800 U/min (hochgerechnet aus MQTB-Leerlauf 70 g bei 6 700 U/min und F60 Pro V 227 g
   bei 11 132 U/min); das Basis-Freestyle-Preset schwebt heute mit ≈ 10 500 U/min.
2. **Gesamtgütegrad im Schweben prüfen** (1.3) gegen die Flugwerte je Klasse: Whoop 0,12–0,18, Toothpick 0,15–0,22,
   Cinewhoop 0,22–0,33, 5″ 0,22–0,30, 7″ 0,30–0,40, X-Class 0,40–0,50. Rechenbeispiel Basis-Freestyle (525 g):
   `k_T`, `k_Q`, `K_t`, `R_m`, `I_0` aus `flight-model-spec` 4.3/4.4 ergeben im Stand ≈ 23,6 W je Motor für 131 g Schub
   (η_ges ≈ 0,34) – obere Grenze der Herstellerdaten, ohne Einbauverluste.
3. **Leerlaufverluste drehzahlabhängig** machen oder über den Antriebswirkungsgrad (5.1) prüfen: ≈ 0,45–0,65 bei 20 % Gas,
   0,65–0,80 bei 50 %, 0,60–0,80 bei 100 %. Der Whoop braucht relativ mehr: 1S-Motoren haben 0,2–0,5 A Leerlauf bei 4–5 V
   (`I0_FRACTION = 0,03` ergibt 0,105 A).
4. **Duct**: +6 bis +23 % Effizienz (Median +14 %) bei gleichem Gas; die Wirkung sitzt im Moment, nicht im Schub bei
   gleicher Drehzahl. Ein Schubfaktor 1,15 bei gleicher Drehzahl liegt am oberen Rand.
5. **Temperatur**: Zielbild für 5″/7″ im Stand bei 20 °C: ≈ 85–100 °C Glocke nach 60 s Vollgas; nach einem Freestyle-Akku
   40–60 °C; 1103–1404: ≈ 50–70 °C nach 60 s Vollgas.
6. **Massen**: `m2004_3000` 12 g → real 16–17 g; `m2807_1300` 47 g entspricht einem 2806.5 (echte 2807: ≈ 54 g).
7. **Akkudurchhang** gehört nicht zu diesem Dokument, verändert aber alle Vollgaswerte: Die Tabellen hier sind am Netzteil
   gemessen.

---

## 10. Unsicherheiten

| Punkt | Wirkung | Größe |
|---|---|---|
| Hersteller-Optimismus (T-Motor gegen MQTB) | g/W im Teillastbereich | +25 bis +55 % |
| Drehzahlkorrektur 9N12P (7/6) | c_t / c_p der Kleinprops | ±20 % |
| Wellenleistung aus Motormodell | c_p, FM | ±15 %, eher zu hoch |
| Extrapolation unter den kleinsten Messpunkt (Schwebe-g/W) | 3″-, 5″-, 7″-Schwebewerte | ±20 % |
| Luftdichte Prüfstand 1,17–1,21 | c_t, c_p | ±2 % |
| Einbauverluste im Flug (Rahmen, Kabel, Regelunruhe) | η_ges Flug | −5 bis −20 % gegen Stand, geschätzt |
| Materialeinfluss | Schub bei Vollgas | 2–5 %, geschätzt |

---

## 11. Quellen

Die T-Motor-Tabellen stammen von den Produktseiten der T-Motor-Marke T-Hobby; die Originalseiten
(`t-hobby.com`) sperren automatische Abrufe, die Tabellen sind über den Vertriebspartner LIGPOWER mit identischer
Seitenstruktur abrufbar. Abruf 2026-09-30.

- [TM-M0703] https://www.ligpower.com/product/m0703-fpv-motor.html
- [TM-M0802] https://www.ligpower.com/product/m0802-kv27000-fpv-motor.html
- [TM-M0803a] https://www.ligpower.com/product/m0803-kv19000-fpv-motor.html
- [TM-M0803b] https://www.ligpower.com/product/m0803-kv22000-fpv-motor.html
- [TM-M1002] https://www.ligpower.com/product/m1002-fpv-uav-motor.html
- [TM-M1103] https://www.ligpower.com/product/m1103-fpv-motor.html
- [TM-M1104] https://www.ligpower.com/product/m1104-fpv-motor.html
- [TM-M1106] https://www.ligpower.com/product/m1106-fpv-motor.html
- [TM-F1203] https://www.ligpower.com/product/f1203-fpv-motor.html
- [TM-F1204] https://www.ligpower.com/product/f1204-fpv-motor.html
- [TM-F1303] https://www.ligpower.com/product/f1303-fpv-motor.html
- [TM-F1404] https://www.ligpower.com/product/f1404-fpv-motor.html
- [TM-F1408] https://www.ligpower.com/product/f1408-2-fpv-motor.html
- [TM-P1604] https://www.ligpower.com/product/p1604-fpv-motor.html
- [TM-P1804] https://www.ligpower.com/product/p1804-fpv-motor.html
- [TM-F2004] https://www.ligpower.com/product/f2004-fpv-motor.html
- [TM-F2203] https://www.ligpower.com/product/f2203_5-fpv-motor.html
- [TM-Cine20] https://www.ligpower.com/product/cine-20-2-inch-cinewhoop-motor.html
- [TM-Cine25] https://www.ligpower.com/product/cine-25-cinewhoop-motor.html
- [TM-Cine30] https://www.ligpower.com/product/cine-30-3-inch-cinewhoop-motor.html
- [TM-Cine35] https://www.ligpower.com/product/cine-35-cinewhoop-motor.html
- [TM-Cine60] https://www.ligpower.com/product/cine-60-6-inch-cinematic-fpv-motor.html
- [TM-Cine66] https://www.ligpower.com/product/cine66-fpv-motor.html
- [TM-Cine77] https://www.ligpower.com/product/fpv-motor-cine77.html
- [TM-Cprop] https://www.ligpower.com/product/cine-8-5-9-5-10-5-professional-cinematic-propeller.html
- [TM-V2207] https://www.ligpower.com/product/v2207-v2-fpv-motor.html
- [TM-V2207.5] https://www.ligpower.com/product/v2207_5-v2-fpv-motor.html
- [TM-F60] https://www.ligpower.com/product/f60prov-fpv-motor.html
- [TM-P2207] https://www.ligpower.com/product/p2207-v3-fpv-motor.html
- [TM-V2306] https://www.ligpower.com/product/v2306-v2-fpv-motor.html
- [TM-V2306v3] https://www.ligpower.com/product/v2306-v3-kv1750-fpv-motor.html
- [TM-V2306.5] https://www.ligpower.com/product/v2306_5-v2-fpv-motor.html
- [TM-P2406] https://www.ligpower.com/product/p2406-fpv-freestyle-motor.html
- [TM-P2505] https://www.ligpower.com/product/p2505-fpv-motor.html
- [TM-F90] https://www.ligpower.com/product/f90-fpv-motor.html
- [TM-F100] https://www.ligpower.com/product/f100-fpv-motor.html
- [TM-V2808] https://www.ligpower.com/product/v2808-fpv-motor.html
- [TM-V3115] https://www.ligpower.com/product/V3115-fpv-motor.html
- [TM-V3120] https://www.ligpower.com/product/V3120-fpv-motor.html
- [TM-4214] https://uav-en.tmotor.com/2026/Motor_0126/1339.html
- [IF-4214] https://shop.iflight.com/xing-x4214-2-8s-x-class-fpv-nextgen-motor-pro802
- [IF-C7] https://shop.iflight.com/Chimera7-Pro-V2-6S-Pro1947
- [MQTB-287] https://www.miniquadtestbench.com/assets/components/motordata/motorinfo.php?uid=287 (Five33 Headsup 2207; EagleTree-eRPM-Sensor, XRotor 40A, 4 ms Abtastung)
- [MQTB-236] https://www.miniquadtestbench.com/assets/components/motordata/motorinfo.php?uid=236 (Brother Hobby R3 2207)
- [HM-EX1103] https://www.happymodel.cn/index.php/2022/09/05/bassline-spare-part-ex1103-kv11000-brushless-motor/
- [RCP-0802] https://www.unmannedtechshop.co.uk/products/rcinpower-gts-v3-0802-whoop-1s-drone-brushless-motor
- [BF-0702] https://betafpv.com/products/0702-ii-brushless-motors
- [BF-0802SE] https://betafpv.com/products/0802se-22000kv-brushless-motors
- [BF-1505] https://betafpv.com/products/1505-3600kv-brushless-motors
- [TBS-1505] https://www.readymaderc.com/products/details/tbs-pod-racer-brushless-motor-1505-3600kv
- [FW-1202] https://www.team-blacksheep.com/products/product:7839
- [OL-Props] https://oscarliang.com/tiny-whoop-prop/
- [OL-Air65] https://oscarliang.com/betafpv-air65/
- [OL-CF] https://oscarliang.com/carbon-fibre-props-plastic-propeller/
- [UAV-Prop] https://blog.uavmodel.com/fpv-drone-propeller-selection-guide-pitch-material-and-blade-count-for-every-flying-style-2026/
- [WTS] https://github.com/einsz/whoop_thrust_stand
- [UIUC-V2] https://m-selig.ae.illinois.edu/props/volume-2/propDB-volume-2.html (Dateien `data/da4022_5x3.75_*`, `data/da4052_5x*`, `data/nr640_5_15deg_*`, `data/gwsdd_*`, `data/mi_3.2x2.2_*`, `data/mit_5x4_*`, jeweils `_static_`)
- [APC] https://www.apcprop.com/files/PER3_5x45E.dat, `PER3_5x5E.dat`, `PER3_7x4E.dat`, `PER3_10x5E.dat`, `PER3_13x10E.dat`
- [GF-1219] https://www.getfpv.com/gemfan-31mm-1219s-3-blade-propeller-set-of-8-1mm-shaft.html
- [GF-2023] https://www.getfpv.com/gemfan-hurricane-2023s-3-blade-propeller-set-of-8.html
- [GF-3018] https://www.getfpv.com/gemfan-hurricane-3018-durable-2-blade-propeller-1-5-2mm-shaft-set-of-8.html
- [GF-D90] https://www.getfpv.com/gemfan-d90-90mm-3-5-ducted-3-blade-propeller-m5-shaft-set-of-4.html
- [GF-51466] https://www.getfpv.com/gemfan-hurricane-51466-propeller-set-of-4.html
- [HQ-7x4x3] https://shop.iflight.com/hq-7x4x3-3-blade-prop-cw-ccw-3sets-6pairs-pro1236
- [HQ-10x5x3] https://www.getfpv.com/hqprop-macroquad-10x5x3-3-blade-propeller-set-of-4.html
- [GF-1310] https://www.getfpv.com/gemfan-1310-3-3-blade-x-class-propellers-set-of-2.html
- [HQ-13x12x3] https://www.getfpv.com/hqprop-x-class-13x12x3-propeller-ccw-single.html
- [MAG] https://radialmagnet.com/neodymium-magnet-temperature-classes/
- [UTS-Heat] https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-motor-overheating-troubleshooting-guide
- [PWR] https://arxiv.org/abs/2209.04128 (Leistungsmodell und Flugmessung Mehrrotorer: Leistung fällt ab dem Schweben zunächst und steigt bei hoher Fahrt)
