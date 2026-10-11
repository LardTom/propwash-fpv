# Realismus-Referenz: Akkus, Power-Stacks, Video

Referenzdaten für die Kalibrierung der Teile-Daten von Propwash und Just More Parts (Akkus, ESC/Stacks/AIOs,
Kamera/VTX/digitale Lufteinheiten und ihre Leistungsaufnahme). Reine Recherche, kein Code geändert.
Produktnamen stehen hier nur als Quellenangabe; Spielteile tragen weiterhin keine Marken.

**Konventionen**

- Dezimalkomma, Massen in g, Energie in Wh (nominal = Nennspannung × Nennkapazität), Leistung in W.
- Güte jeder Zahl: **A** = Herstellerangabe, **M** = unabhängige Messung (Review/Prüfstand), **S** = Sekundärquelle,
  Schätzung oder von mir abgeleitet (Rechenweg steht dabei). Quellen als `[Qn]`, Liste in Abschnitt 9.
- „Ladegerät-IR“ = Innenwiderstand, wie ihn Hobby-Ladegeräte anzeigen (kurzer Puls bzw. Wechselstrom), deutlich
  kleiner als der Widerstand, der über Sekunden unter Last wirkt (Abschnitt 2.4).

## 1. Kurzfassung je Größenklasse

| Klasse | Akku typisch | Akkumasse | Energie nominal | Power-Stack | Video-System | Flugzeit-Referenz |
|---|---|---|---|---|---|---|
| 65-mm-Whoop 1S | 1S 250–300 mAh LiHV, BT2.0 | 6,5–8,4 g | 0,95–1,14 Wh | AIO 1S 5 A/Motor, 3,0–3,6 g | analog 2–3 g, 1,2–1,6 W; digital 5–10 g, 4–6 W | analog 3:40–4:30 min zügig [Q100 M]; digital ≈ 2 min [Q114 S] |
| 75-mm-Whoop 1S | 1S 450–550 mAh LiHV | 12–14 g | 1,7–2,1 Wh | AIO 1S 5 A, 3–4 g | wie 65 mm | 4 min draußen gemischt, 5 min drinnen ruhig, 45 g AUW [Q101 M] |
| 2S-Whoop / Micro | 2S 300–680 mAh (LiHV), XT30/A30 | 16–34 g | 2,3–5,2 Wh | AIO 1–2S 12 A, 3,6–5,1 g | wie oben | keine belastbare Messung |
| 2,5–3″ Toothpick | 1S 450–750 / 3S 450–680 / 4S 450–680 mAh | 12–19 / 38–53 / 52–64 g | 1,7–2,9 / 5,1–7,8 / 6,8–10,3 Wh | AIO 2–4S 12–20 A, 5,8–6,6 g | analog 3–5 g; digital 5–10 g | 1S 3″: 4 min (300), 6 min (450), > 8 min (650) [Q102 M]; 1S 450 Schweben 5:30, mit Vollgas-Stößen < 3 min [Q103 M]; 4S 650 3″: 3:30–4 min Cruisen [Q104 M] |
| 3–3,5″ Cinewhoop/Freestyle | 4S 650–1000 / 6S 850–1100 mAh | 64–95 / 135–192 g | 9,9–15 / 19–24 Wh | 20×20-AIO oder -Stack 35–45 A, 8–22 g | digital HD 32–40 g | 4S 850: 4–5 min scharf, ≈ 7 min ruhig [Q105 M]; 6S-Cinewhoop 3,5″: ≈ 4 min gemessen gegen 10–12 min Herstellerangabe [Q106 M/A] |
| 5″ Freestyle/Race | 6S 1100–1300 (4S 1300–1500) | 175–215 (155–190) g | 24–30 (19–22) Wh | 30×30 50–60 A, 25–35 g | analog 10–15 g, 2–6 W; digital 32–40 g, 7–11 W | 6S 1300 scharf 4–5 min [Q107 S]; Ø-Strom dabei ≈ 15–20 A (abgeleitet) |
| 6–7″ Long Range | 6S 1800–2200 LiPo; 6S1P Li-Ion 3000–4500; 6S2P 6000–8400; 8S 2200 selten | 287–349; 314–430; 650–850; 440–480 g | 40–49; 65–97; 130–181; 65 Wh | 30×30 55–65 A (6S), 3–8S 60–80 A | digital HD 32–40 g, + GPS 5–10 g | 7″ 1,48 kg mit 6S2P 6000 Li-Ion: knapp 16 min bei 60–70 km/h, Schwebegas ≈ 30 % [Q108 M]; 6S 4000 Li-Ion: 15–22 min [Q109 S] |
| X-Class 10–13″ | 6S 4500–5200 LiPo, 8S 4500–5000, oft 2 × 6S in Reihe (12S) | 685–790 g (6S); ≈ 950–1050 g (8S, S) | 100–115 Wh (6S); 133–148 Wh (8S) | 4 × Einzel-ESC 80–120 A (Race 9,5–14,5 g, Industrie 50–117 g) oder 4-in-1 80 A ≈ 65 g | wie 5″ | nur Sekundärquellen: Strom 150–250 A an 12S, Spitze 150–180 km/h [Q110 S] |

Faustregeln aus den Quellen:

- Akku ≈ halbe Trockenmasse für agilen Freestyle [Q34 S].
- LiPo-Energiedichte fertiger FPV-Packs **125–155 Wh/kg** (nominal gerechnet 122–155, gemessen bei 15 C bis 3,3 V
  125–135) [Q17 M, Abschnitt 2.2]; Li-Ion-Packs **205–230 Wh/kg** nominal [Abschnitt 2.3] – also ≈ 1,5–1,6 × LiPo.
- Digitale Videosysteme kosten unabhängig von der Masse 4–7,5 W Grundlast; auf einem 1S-Whoop (≈ 10–20 W Ø) sind das
  30–40 % Flugzeit [Abschnitt 4.2, Q114].

## 2. Akkus

### 2.1 Chemie-Kennwerte, Landespannung, nutzbare Kapazität

| Größe | LiPo | LiHV | Li-Ion (18650/21700) | Quelle |
|---|---|---|---|---|
| Voll | 4,20 V | 4,35 V | 4,20 V | [Q34 A] |
| Nenn | 3,7 V | 3,8 V | 3,6 V | [Q1, Q24, Q26] |
| Lagerung | 3,80–3,85 V | 3,85 V | ≈ 3,6 V | [Q34] |
| Landen (Ruhe) | 3,5–3,6 V | 3,5–3,6 V | 2,8–3,0 V (unter Last), manche 2,5 V | [Q34, Q24 M] |
| Entnommen bis 3,5 V Ruhe | ≈ 86 % der Nennkapazität | wie LiPo | – | [Q40 S] |
| Datenblatt-Kapazität gilt bis | – | ab 4,35 V | 2,5 V bei 0,2 C | [Q31 A] |
| Gemessene Kapazität | 94–115 % der Angabe (Qualitätspacks) | 94 % (GNB 6S 1300 HV) | 91 % bei 0,5 C (Auline 6S 4000), 94–103 % bei 2 A bis 2,9 V (18650) | [Q4, Q11, Q25, Q27 M] |

Ruhespannung gegen Restladung (LiPo, je Zelle) [Q41 S]: 4,20 V = 100 %, 4,03 V = 80 %, 3,82 V = 50 %,
3,70 V = 30 %, 3,60 V = 20 %, 3,50 V = 10 %, 3,30 V = 0 %. Andere Tabellen weichen um ± 0,05 V ab.

LiHV-Mehrkapazität gegenüber Laden auf 4,20 V, gemessen an GNB 6S [Q11 M]: 1100 mAh: 1049 statt 909 mAh (+15 %),
1300 mAh: 1227 statt 1044 mAh (+18 %), 1500 mAh: 1375 statt 1180 mAh (+17 %). Mehrleistung vor allem in der ersten
Hälfte der Entladung, danach verhält sich LiHV wie LiPo [Q4 M]. Alterung: ≈ 3,8 % (LiPo) bzw. 5,4 % (LiHV)
Leistungsverlust in den ersten 100 Zyklen [Q34].

### 2.2 Packmassen LiPo/LiHV

| Pack | Masse | Energie nom. | Wh/kg | Güte | Quelle |
|---|---|---|---|---|---|
| 1S 300 mAh LiHV 75C, BT2.0 | 8,3 g | 1,14 Wh | 137 | A | [Q1] |
| 1S 300–320 mAh LiHV (3 Marken) | 8,2–8,4 g | 1,14–1,22 Wh | 137–148 | M | [Q4] |
| 1S 450 mAh LiHV 75C | 12,2 g | 1,71 Wh | 140 | A | [Q2] |
| 1S 550 mAh LiHV 75C | 14,0 g | 2,09 Wh | 149 | A | [Q3] |
| 1S 680 mAh LiHV 95C | 15,7 g | 2,58 Wh | 165 | M | [Q4] |
| 2S 450 mAh LiHV 80C, XT30 | 27–29 g | 3,42 Wh | 118–127 | A | [Q5] |
| 2S 550 mAh LiHV 100C, XT30 | 29,3 g | 4,18 Wh | 143 | M | [Q4] |
| 2S 680 mAh LiHV 95C, XT30 | 33,4 g | 5,17 Wh | 155 | M | [Q4] |
| 3S 450 mAh 75C (zwei Serien) | 38,1–43,5 g | 5,0 Wh | 115–131 | M | [Q4] |
| 3S 650 mAh LiHV 120C, XT30 | 53 g | 7,4 Wh | 140 | A | [Q6] |
| 3S 680 mAh LiHV 95C | 48,4 g | 7,8 Wh | 160 | M | [Q4] |
| 3S 1000 mAh LiHV 80C | 66,0 g | 11,4 Wh | 173 | M | [Q4] |
| 4S 450 mAh LiHV / LiPo | 52,1 / 56,8 g | 6,8 / 6,7 Wh | 131 / 117 | M | [Q4] |
| 4S 680 mAh LiHV 95C | 63,6 g | 10,3 Wh | 163 | M | [Q4] |
| 4S 850 mAh LiHV 120C, XT30 | 88 g | 12,9 Wh | 147 | A | [Q7] |
| 4S 850 mAh LiPo 100C | 95,4 g | 12,6 Wh | 132 | M | [Q4] |
| 4S 1300 mAh 150C | 156 g | 19,2 Wh | 123 | A | [Q8] |
| 4S 1300–1550 mAh (Generation 2017, 5 Packs) | 150–177 g / 157–189 g | 19–23 Wh | 115–130 | M | [Q9, Q10] |
| 6S 1050 mAh 150C | 199 g | 23,3 Wh | 117 | M | [Q11] |
| 6S 1100 mAh LiHV / LiPo | 177 / 192 g | 25,1 / 24,4 Wh | 142 / 127 | M | [Q11] |
| 6S 1300 mAh LiHV | 198 g | 29,6 Wh | 150 | M | [Q11] |
| 6S 1300 mAh 120–160C (drei Generationen) | 200–208 g ± 20 g | 28,9 Wh | 139–144 | A | [Q12] |
| 6S 1400 mAh 120C / 150C | 228–233 g | 31,1 Wh | 133–136 | A/M | [Q13, Q17] |
| 6S 1500 mAh LiHV / 1530 mAh LiPo | 222 / 251 g | 34,2 / 34,0 Wh | 154 / 135 | M | [Q11] |
| 6S 1800 mAh 150C | 287 g ± 20 g | 40,0 Wh | 139 | A | [Q14] |
| 6S 2200 mAh 95C / 70C | 321 / 349 g | 48,8 Wh | 152 / 140 | A | [Q15, Q16] |
| 6S 4500 mAh 95C / 25C | 685 / 650 g | 99,9 Wh | 146 / 154 | A | [Q23] |
| 6S 5000 mAh 20–70C | 710–790 g | 111 Wh | 141–156 | A | [Q22] |
| 8S 1050 mAh 150C | 241,8 g | 31,1 Wh | 129 | M | [Q17, Q18] |
| 8S 1530 mAh 160C | 330 g ± 7 g | 45,3 Wh | 137 | A | [Q19] |
| 8S 2000 / 2800 mAh | 406 / 540 g | 59,2 / 82,9 Wh | 146 / 153 | A | [Q21] |
| 8S 2600 mAh 120C | 515 g ± 16 g | 77,0 Wh | 149 | A | [Q20] |
| **8S 2200 mAh** (interpoliert) | **440–480 g** | 65,1 Wh | 135–148 | S | aus [Q19, Q20, Q21]: 330 g + 670 mAh × 0,173 g/mAh ≈ 446 g |
| 8S 5000 mAh (hochgerechnet) | ≈ 950–1050 g | 148 Wh | 141–156 | S | 6S 5000 [Q22] × 8/6 |
| 6S 550 / 750 / 850 mAh (hochgerechnet) | ≈ 90–100 / 120–135 / 135–150 g | 12,2 / 16,7 / 18,9 Wh | 125–140 | S | aus 6S 1050–1300 und 125–140 Wh/kg |

Gemessene Energiedichte bei 15 C bis 3,3 V je Zelle: 0,125–0,135 Wh/g für 6S und 8S gleichermaßen; die Packmasse
sagt die Leistung besser voraus als die mAh-Angabe [Q17 M]. Stecker und Kabel machen kleine Packs überproportional
schwer (2S 450 mit XT30: nur 118–127 Wh/kg).

### 2.3 Li-Ion-Packs und Zellen

| Pack / Zelle | Masse | Kapazität (gemessen) | Dauerstrom | Innenwiderstand | Güte | Quelle |
|---|---|---|---|---|---|---|
| 4S1P 18650 3000 mAh, XT30 | 196,5–198 g | – | 10C laut Händler | – | A | [Q28] |
| 4S1P 18650 3400 mAh | ≈ 200 g (gleich schwer wie 4S 1600 LiPo) | – | – | – | S | [Q24] |
| 6S1P 18650 3000 mAh | 314 g | 2991 mAh | 45 A | – | M | [Q26] |
| 6S1P 21700 4000 mAh | 398 g (63,5 g/Zelle) | ≈ 3650 mAh (0,5 C) | – | 7–8 mΩ/Zelle (Ladegerät), Hersteller 3,98 | M | [Q25] |
| 6S1P 21700 4000 mAh | 430 g | – | – | – | A | [Q29] |
| 6S1P 21700 4500 mAh | 428,7 g (≈ 68 g/Zelle) | 4442 mAh | 70 A | – | M | [Q26] |
| 6S2P 21700 8400 mAh | ≈ 849 g | – | 25C laut Händler | – | A | [Q30] |
| 8S1P 21700 4500 mAh (hochgerechnet) | ≈ 580–600 g | – | 45 A | – | S | 8 × 70 g + Kabel |
| Zelle 21700 P42A-Klasse 4200 mAh | 67–70 g | ≥ 4000 mAh bis 2,5 V | 45 A | ≤ 15 mΩ AC 1 kHz | A | [Q31] |
| Zelle 21700 P45B-Klasse 4500 mAh | 70 g | – | 45 A | 7 mΩ DC | A | [Q32] |
| Zelle 21700 P50B-Klasse 5000 mAh | ≤ 71 g | – | 60 A | 12,8 mΩ DC bei 50 % | A | [Q32] |
| Zelle 18650 P28A-Klasse 2800 mAh | 45–46 g | 2627 mAh (2 A, 2,9 V) | 35 A | 20 mΩ DC (10 A, 1 s) | A/M | [Q33, Q27] |
| Zelle 18650 VTC6-Klasse 3000 mAh | 46,6–46,9 g | 2939 mAh | 15 A (Händler bis 30 A) | 15 ± 1 mΩ DC voll, 8–12 mΩ Spez. | A/M | [Q33, Q27] |
| Zellen 18650 3000–3500 mAh (7 Typen) | 45,2–48,3 g | 94–103 % der Angabe | – | – | M | [Q27] |

Nominale Energiedichte daraus: 4S 18650 3000 (198 g) 218 Wh/kg, 6S 18650 3000 (314 g) 206 Wh/kg,
6S 21700 4000 (398 g) 217 Wh/kg, 6S 21700 4500 (429 g) 227 Wh/kg, 6S2P 8400 (849 g) 214 Wh/kg (abgeleitet, S).
Stromfähigkeit: LiPo „≈ 50 C Dauer“, Li-Ion „≈ 5 C“ im Flugbetrieb sinnvoll [Q24 S]; Zellen-Grenzen 10–12 C
(Datenblatt, 80 °C Abschaltung). Praktischer Dauerstrom eines 6S1P-21700-Packs für Long Range 20–25 A, Grenze 35–45 A
[Q109 S]. Li-Ion bricht unter hoher Last viel stärker ein; neuere Packs zeigen im Vergleich ≈ 0,5–0,8 V weniger
Einbruch bei 3 C [Q25 M, Q24].

### 2.3b Semi-Solid (hochvoltige Li-Ion-Variante)

Nur große UAV-Packs sind belegt; FPV-Größen (3000–5000 mAh) fanden sich nicht.

| Pack | Masse | Energiedichte | Dauer / Spitze | Zellspannung | Güte | Quelle |
|---|---|---|---|---|---|---|
| 6S 17000 mAh | 1444 g | 300 Wh/kg | 3 C (51 A) / 5 C (85 A) | 22,2 V nominal | A | [Q115] |
| 6S 30000 mAh | 2525 g ± 20 g | 275 Wh/kg | 3 C (90 A) / 5 C (150 A) | 22,2 V nominal | A | [Q115] |
| 6S 22000 mAh | 1963 g | 251,7 Wh/kg | 5 C / 10 C | HV-Serie 4,45 V voll | A | [Q116] |
| 6S 22000 mAh | 1720 g | 320 Wh/kg | 10 C | – | A | [Q117] |

Für kleine Packs sinkt die Dichte durch Gehäuse, Kabel und Stecker; die JMP-Werte (222–240 Wh/kg) sind damit
plausibel. Die Entladegrenze folgt der Li-Ion-Chemie (≈ 3,0 V), nicht LiPo (3,5 V) – die JMP-Chemie
`justmoreparts:lihv_ss` setzt `empty_v` 3,5 V und verschenkt damit Kapazität bzw. Laufzeit.

### 2.4 Innenwiderstand und Spannungseinbruch

| Pack | Messart | Wert je Zelle | Güte | Quelle |
|---|---|---|---|---|
| 6S 1300 moderne 120C, neu voll | Ladegerät | 1–3 mΩ | S | [Q42] |
| 6S 1300 nach ≈ 10 Zyklen | Ladegerät | 2–4 mΩ | S | [Q42] |
| 6S 1300–2200 „gesund“ | Ladegerät, 25 °C, 20 % entladen | ≤ 6 mΩ | S | [Q42] |
| 4S 1300–1550, Generation 2017 (5 Packs) | Ladegerät | 4,1–7,4 mΩ | M | [Q9, Q10] |
| 5000 mAh High-C, neu | – | 1–4 mΩ | S | [Q36] |
| 1S 300 mAh Whoop | Ladegerät | 20–30 mΩ | S | [Q43] |
| Micro 100–200 mAh (ältere Zellen) | – | ≈ 150 mΩ | S | [Q36] |
| 6S 21700 Li-Ion 4000 | Ladegerät | 7–8 mΩ | M | [Q25] |
| Zellen Li-Ion | Datenblatt | siehe 2.3: 7–20 mΩ DC | A | [Q31–Q33] |

Bewertungsgrenzen für ≈ 1500-mAh-Packs [Q35 M]: < 10 mΩ sehr gut, 10–15 mΩ in Ordnung, 15–20 mΩ alt, > 20 mΩ
ausmustern. Kleinere Packs liegen entsprechend höher.

Kenngröße „IR × Kapazität“ (abgeleitet, S) – damit lassen sich Werte zwischen den Größen skalieren:

| Zellart | Ladegerät-IR × Ah |
|---|---|
| moderne High-C-LiPo (6S 1300–1550) | 2–5 mΩ·Ah |
| ältere/günstige LiPo | 6–10 mΩ·Ah |
| 1S-Whoop-LiHV (250–550 mAh) | 6–9 mΩ·Ah |
| Li-Ion 21700 | 30–35 mΩ·Ah (Ladegerät) bzw. 31–64 mΩ·Ah (DC-Datenblatt) |
| Li-Ion 18650 | 45–60 mΩ·Ah (DC) |

Messart ist entscheidend: An derselben Zelle ergab ein 10-s-DC-Puls ≈ 22 mΩ, Wechselstrom 1 kHz 6,6 mΩ und 100 Hz
9,8 mΩ – Faktor ≈ 3,3 zwischen 10-s-DC und 1 kHz. Ein 1-s-Puls sieht Ohmschen Anteil und Ladungsdurchtritt, ein
10-s-Puls zusätzlich Diffusion [Q37 M]. Hobby-Ladegeräte liefern immer niedrigere Werte als die DC-Lastmethode [Q36].
Wärmere Zellen haben niedrigeren Widerstand [Q36].

Spannungseinbruch unter Last:

| Situation | Wert | Güte | Quelle |
|---|---|---|---|
| 1S 300 mAh LiHV, 4 A, nach 10 s | 3,92 V (BT2.0) / 3,84 V (PH2.0), voll 4,35 V | M | [Q38] |
| daraus wirksamer Widerstand Pack + Stecker | ≈ 0,09–0,11 Ω (abgeleitet: 0,43 V − ≈ 0,05 V OCV-Abfall, geteilt durch 4 A) | S | – |
| 6S frisch, Vollgas 100–120 A (5″) | 3–4 V gesamt, ≈ 0,6–0,9 V je Zelle „gesund“, 1 V je Zelle „obere Grenze“ | S | [Q44] |
| Rechenbeispiel 6S mit 18 mΩ Pack-IR bei 100 A | 1,8 V Einbruch | S | [Q36] |
| 5″ Durchschnitt / Spitze | 20–30 A Ø, 100 A+ Vollgas | S | [Q44] |
| Li-Ion 6S 3000–4000 bei 3 C | neuere Packs bis 0,5 V weniger Einbruch als ältere | M | [Q25] |

Abgeleitet (S): Der über 1–5 s wirksame Widerstand liegt bei ≈ 1,5–2,5 × Ladegerät-IR; für einen modernen
6S 1300 also 3–6 mΩ je Zelle, passend zu ≈ 0,4–0,6 V Einbruch je Zelle bei 100 A plus OCV-Abfall.

### 2.5 C-Rating: Aufdruck gegen Messung

| Größe | Wert | Güte | Quelle |
|---|---|---|---|
| Aufdruck FPV-Packs | 45–160 C (Burst „2 ×“) | A | [Q1–Q23] |
| Gemessene „effektive C“ 6S (bestes Pack, 6S 1800) | 74 C | M | [Q17] |
| Gemessene „effektive C“ 8S (bestes Pack) | 68 C | M | [Q17] |
| Burst-Leistung 6S / 8S 1300 mAh (gleiche Serie) | ≈ 1540 W / 2265 W (+33 %) | M | [Q17] |
| Aussage zu Micro-Packs | C-Angaben „oft für Marketing aufgeblasen“ | M | [Q4] |
| Li-Ion-Zellen | 10–12,5 C (P42A 45 A, P45B 45 A, P50B 60 A, P28A 35 A); 18650 VTC6 5 C | A | [Q31–Q33] |

Faustwert (S): wirksames Dauer-C eines guten LiPo ≈ 0,4–0,6 × Aufdruck, Burst (Sekunden) ≈ 50–75 C.

### 2.6 Stecker

| Stecker | Dauerstrom | Spitze | Kontaktwiderstand | Masse (Paar) | Quelle |
|---|---|---|---|---|---|
| PH2.0 | 4,5 A | – | ≈ 20 mΩ mehr als BT2.0 (abgeleitet aus 0,08 V bei 4 A) | 0,4 g | [Q45 A, Q38, Q39 M] |
| BT2.0 | 9 A | 15 A | – | 0,6 g | [Q45 A, Q39 M] |
| A30 | – | – | ≈ wie BT2.0 bei 12 A | 0,6 g | [Q39 M] |
| GNB27 | – | – | ≈ wie BT2.0 bei 12 A | 0,8 g | [Q39 M] |
| XT30 | 15 A (Datenblatt), Händler bis 30 A | – | 0,7 mΩ | 1,6 g | [Q46 A, Q39 M] |
| XT60 | 30 A (Herstellerwert mit 12 AWG), Händler 60 A | – | – | – | [Q47 S] |
| XT90 | 40–45 A | 90 A | 0,3 mΩ | – | [Q46 A, Q47 S] |

Bei 12 A liegen BT2.0, A30, GNB27 und XT30 praktisch gleich; nur PH2.0 fällt ab [Q39 M]. Laut Hersteller verdoppelt
BT2.0 gegenüber PH2.0 bei 9 A die Laufzeit eines 1S 300 [Q38 A].

### 2.7 Akkuwarnungen im Flugcontroller

Betaflight bildet die Warnstufen aus der gefilterten Spannung unter Last: Unterschreitet sie
`vbat_warning_cell_voltage` (Standard 3,50 V je Zelle) um mehr als `vbat_hysteresis` (Standard 0,01 V) länger als die
eingestellte Dauer, geht der Zustand auf „Warnung“, darunter auf „kritisch“; steigt die Spannung wieder über die
Warnschwelle, geht er zurück auf „OK“ [Q131]. Ein Schubstoß mit vollem Akku löst also höchstens eine kurze Warnung aus,
die nach dem Stoß verschwindet. Propwash (`sim.BatteryAlarm`, `flight-model-spec.md` §5.10) macht es genauso, mit
2 s Dauer und 0,1 V Rückweg-Hysterese (die Simulation filtert mit 1 Hz und kennt keine Messspannung mit Rauschen). Abweichend
hält Propwash die Stufe, sobald auch die Ruhespannung des Akkus an der Schwelle liegt – dann ist der Akku wirklich leer,
und die Warnung soll bis zur Landung bleiben. Mit „unendlicher Akku“ (Kreativ) gibt es keine Warnungen; der
Spannungseinbruch unter Last wirkt weiter auf den Punch.

## 3. Power-Stacks, AIOs, ESCs

### 3.1 Masse und Strom

| Bauteil | Zellen | Strom/Motor Dauer (Burst) | Masse | Güte | Quelle |
|---|---|---|---|---|---|
| Whoop-AIO F4, 1S | 1S | 5 A (6 A für 3 s) | 2,96 g (Light) / 3,6–3,64 g | A | [Q50, Q51] |
| Whoop-5-in-1 (FC, ESC, VTX 0–400 mW, RX) | 1–2S | 12 A | 5,1 g | A | [Q52] |
| Toothpick-AIO F4 | 2–4S | 12 A | 6,2–6,5 g | A | [Q53] |
| Toothpick-AIO F4 / F405 | 2–4S | 20 A | 6,63 g / 5,76 g | A | [Q53] |
| 20×20-AIO | 2–6S | 35 A | 7,9 g | A | [Q54] |
| 20×20-AIO | 3–6S | 35 A | 9,8 g | A | [Q55] |
| 25,5×25,5-AIO | 3–6S | 40 A | 11–13,6 g | A | [Q56] |
| 20×20-Stack (FC + ESC) | 3–6S | 35 A | ≈ 15 g ohne Schrauben; ESC 9,6 g | A | [Q57] |
| 20×20-Stack (FC 4,6 g + ESC 10,6–10,9 g) | 3–6S | 45 A | 19,6–21,5 g | A | [Q58] |
| 20×20-4-in-1 | 2–6S | 33 A | 5 g | A | [Q68] |
| 30×30-Stack (FC + ESC mit Kühlblech) | 3–6S | 55 A (70 A) | 34 g gesamt, ESC 23,5 g | A | [Q59, Q70] |
| 30×30-4-in-1 mit Kühlblech | 3–6S | 55 A (75 A) | 17,5 g | A | [Q60] |
| 30×30-4-in-1 | 3–6S | 50 A / 60 A / 65 A (4–6S) | 13,8 / 15,7 / 15,8 g | A | [Q61] |
| 30×30-4-in-1 | 2–6S / 3–6S | 50 A / 55–60 A | 12,5 / 13,5–14 g | A | [Q68] |
| 30×30-4-in-1 | 2–8S | 65 A (80 A) | 15,3 g | A | [Q62] |
| 30×30-4-in-1 | 3–8S | 60 A (85 A) | 17,58 g | A | [Q63] |
| 30×30-4-in-1 | 3–8S | 80 A | ≈ 14,5 g (Platine) | A | [Q64] |
| 35×35/30×30-4-in-1 mit Alu-Deckel | 2–8S | 80 A | 65 g ± 10 g | A | [Q65] |
| Einzel-ESC X-Class | 2–8S | 80 A | 9,5 g ohne Kabel / 14,5 g mit | A | [Q66] |
| Industrie-Einzel-ESC 40 / 50 / 80 A | 3–6S | 40–80 A | 50 / 56 / 117 g | A | [Q67] |
| Industrie-Einzel-ESC 60 / 80 A | 6–14S | 60–80 A | 67 / 87 g ohne Kabel | A | [Q67] |
| X-Class-Aufbau 12S (Beispiel) | 12S | 4 × 120 A Einzel-ESC | – | M | [Q112] |

Typische Belegung je Klasse (S, aus den Tabellen): 65/75-mm-Whoop 5–6 A; 2S-Micro 12 A; Toothpick 12–20 A;
3–3,5″ 35–45 A; 5″ 45–60 A (3–6S); 7″ 6S 45–60 A, 8S 60–80 A (3–8S); X-Class 80–120 A Einzel-ESC. Ein Stack als
Spielteil (FC + ESC + Abstandshalter) wiegt für 30×30 **25–35 g**, für 20×20 **15–22 g**; XT60-Pigtail und
Low-ESR-Kondensator kommen erfahrungsgemäß mit einigen Gramm hinzu (S, ohne Messung).

### 3.2 Burst zu Dauer

| ESC | Dauer / Burst | Verhältnis | Quelle |
|---|---|---|---|
| Whoop 1S | 5 / 6 A (3 s) | 1,20 | [Q51] |
| 30×30 3–6S | 55 / 70 A | 1,27 | [Q70] |
| 30×30 3–6S | 55 / 75 A | 1,36 | [Q60] |
| 30×30 2–8S | 65 / 80 A | 1,23 | [Q62] |
| 30×30 3–8S | 60 / 85 A | 1,42 | [Q63] |

Spanne 1,2–1,42, Mittel ≈ 1,3.

### 3.3 Leitwiderstand der Endstufe

Der Strom fließt je Kommutierungsschritt durch einen High-Side- und einen Low-Side-FET; der Pfadwiderstand ist also
≈ 2 × R_DS(on) plus Leiterbahn (S).

| ESC | R_DS(on) je FET | Pfad ≈ 2 × FET | Quelle |
|---|---|---|---|
| Whoop 1S (verbreiteter N+P-Doppel-FET) | N 24–29 mΩ (4,5 V), 28–34 mΩ (2,5 V); P 50–61 mΩ | **≈ 75–95 mΩ** | [Q69 A, Q51 S] |
| 20×20 30 A 2–4S | 3,1 mΩ | ≈ 6,2 mΩ | [Q68 A] |
| 20×20 33 A 2–6S | 1,2 mΩ | ≈ 2,4 mΩ | [Q68 A] |
| 30×30 50 A 2–6S | 1,1 mΩ | ≈ 2,2 mΩ | [Q68 A] |
| 30×30 55–60 A | 0,70–0,75 mΩ | ≈ 1,4–1,5 mΩ | [Q68 A] |

ESC-Verluste insgesamt: 3–8 % der Leistung, besonders bei hoher PWM-Frequenz; ESC-Typ und -Einstellung ändern die
Effizienz von Motor + ESC um bis zu 10 % [Q48 S].

## 4. Video und weitere Verbraucher

### 4.1 Analog

| Bauteil | Masse | Leistungsaufnahme | Latenz | Güte | Quelle |
|---|---|---|---|---|---|
| Nano-Kamera 800TVL | 1,1 g (ohne Kabel) | 110 mA @ 5 V = 0,55 W | – | A | [Q84] |
| Nano-Kamera 1200TVL 14 mm | 2 g | – | – | A | [Q85] |
| Nano-Kamera 1200TVL | 3,9 g | 140 mA @ 5 V / 64 mA @ 12 V = 0,7–0,77 W | – | A | [Q86] |
| Nano-Kamera Racing | 4,8 g | 115 mA @ 5 V / 50 mA @ 12 V = 0,58–0,6 W | 4 ms | A | [Q87] |
| Micro-Kamera Racing | 5,8 g | wie Nano | 4 ms | A | [Q87] |
| Micro-Kamera 1200TVL | 4,5 g | 140 mA @ 5 V / 64 mA @ 12 V | – | A | [Q90] |
| Micro-Kamera Starlight | 5,9 g | – | – | A | [Q89] |
| Micro-Kamera Freestyle (vier Varianten) | 7,5–9 g | 95–200 mA @ 5 V (0,48–1 W), 45–220 mA @ 12 V | – | A | [Q88] |
| Whoop-Kombi Kamera + VTX 25–200 mW | 2,1–4,2 g (mit DVR 6,8 g) | – | – | M | [Q91] |
| VTX nano 1–3S, u.FL | 1 g | 25 mW: 210 mA, 400 mW: 390 mA (Eingang 3–13 V, Spannung nicht angegeben) | – | A | [Q80] |
| VTX 2–6S, MMCX | 8,7–9,2 g | 25 mW: 90–130 mA bei 22–14,8 V ≈ 2 W; 1000 mW+: 300–450 mA ≈ 6,6 W | – | A | [Q80] |
| VTX Mini 800 mW | 4,46 g (5,4 g mit Deckel) | bei 12 V: Pit 40 mA, 25 mW 95 mA (1,14 W), 200 mW 185 mA (2,22 W), 500 mW 250 mA (3,0 W), 800 mW 380 mA (4,56 W) | – | A | [Q81] |
| VTX 800 mW (drei Varianten) | 5,7–6,8 g | – | – | M | [Q82] |
| VTX Tiny 200 mW | 1,4 g | – | – | M | [Q82] |
| VTX Long Range 1–1,6 W | 12 g ohne Kabel | nicht angegeben (S: ≈ 7–10 W bei 1,6 W, aus den 1-W-Werten hochgerechnet) | – | A/S | [Q83] |

Faustformel analog (S, Fit an [Q81]): P_VTX ≈ 1,0 W + 4,3 × P_HF (W) bei 12 V; Kamera 0,5–1 W zusätzlich.
Whoop-VTX an 1S laufen näher an 0,6–1,0 W (25 mW) bzw. 1,6–2 W (400 mW) [Q80].

### 4.2 Digital

| System | Masse | Leistungsaufnahme | Latenz | Güte | Quelle |
|---|---|---|---|---|---|
| DJI O3 (Lufteinheit + Kamera) | 36,4 g (ohne Antennen); 39,5 g komplett; ohne Kamera 28 g, Antenne 3 g | max. ≈ 16 W (1,6 A @ 10 V), BEC ≥ 17–18 W empfohlen; typischer Flugwert nicht veröffentlicht | 28–40 ms | A/M | [Q71, Q72] |
| DJI O4 Pro | 32,0 g mit Kamera (ohne 15,6 g), Antenne 2,1 g/Stk.; 33–36 g komplett | scharf + Aufnahme @ 9 V: 25 mW 0,82 A (7,4 W), 100 mW 0,87 A, 400 mW 0,98 A, 700 mW 1,05 A, 1200 mW 1,16 A (10,4 W); unscharf 0,33 A (3 W); Race-Modus 0,71–1,02 A | 15–30 ms (brillenabhängig) | M | [Q73] |
| DJI O4 Lite | 8,2 g mit Kamera, 9,2 g mit Halter, Antenne 0,75 g | @ 5 V: 25 mW 0,98 A (4,9 W), 700 mW 1,2 A (6,0 W), Leerlauf 0,6 A (3 W); @ 9 V 0,67 A max. | 20–35 ms | M | [Q74] |
| Walksnail HD V2 Kit | 29,5 g (VTX 19 g, Kamera 7,5 g, Antenne 3 g) | – | 22 ms (SE) | M | [Q75] |
| Walksnail HD Pro Kit | 32–33,1 g (Kamera 9,5–10 g) | Netzteilangabe 12 V @ 1,4 A / 8 V @ 2,2 A (Versorgungsreserve, kein Verbrauch) | +< 5 ms ggü. V2 | M/A | [Q75, Q76] |
| Walksnail VTX (Einrichtungsanleitung) | 21–29,5 g je Version, Mini 1S 6,8 g | ≈ 4,5 W bei 1200 mW (9 V 0,5 A, 12 V 0,38 A) – im Vergleich zu DJI auffällig niedrig, unsicher | – | A | [Q76] |
| Walksnail Mini 1S Lite | 8,7 g (VTX 5,1 g, Kamera 1,8 g, Antenne 0,5 g) | @ 3,7 V: 25 mW 1,10 A (4,07 W), 200 mW 1,15 A, 350 mW 1,20 A (4,44 W); @ 5 V 0,85–1,05 A | 22 ms | M | [Q77] |
| HDZero Race V3 | 5,5 g | ≈ 1 A @ 5 V (25 mW) bis 0,5 A @ 12 V (200 mW), max. 5,8 W | ≈ 14 ms | M | [Q79] |
| HDZero Race/Whoop V1–V2 | 5,4–5,9 g | 410 mA @ 12 V (4,9 W) | – | A | [Q78] |
| HDZero Whoop Lite | 4,5 g | 5 W | – | A | [Q78, Q80b] |
| HDZero Freestyle V2 (1 W) | 22,3 g | 6–15 W je nach HF-Leistung | – | A | [Q78] |

Faustformel digital (S): P ≈ P₀ + k × P_HF mit P₀ = 4–7,5 W (Grundlast unabhängig von der Masse) und k ≈ 1,5–9 W/W
(DJI O4 Pro: 7,3 W + 2,6 W/W; O4 Lite: 4,9 W + 1,6 W/W; HDZero Freestyle: 6 W + 9 W/W).
Auf 65/75-mm-Whoops kostet das den Unterschied „2 min digital gegen 3 min analog“; ein digitales VTX zieht an 1S schon
bei 25 mW über 1 A [Q114 S].

### 4.3 Grundlast und Zubehör

| Verbraucher | Masse | Leistungsaufnahme | Güte | Quelle |
|---|---|---|---|---|
| Flugcontroller (ältere F1/F3/F4) | – | 44–93 mA (≈ 0,2–0,5 W bei 5 V) | M | [Q94] |
| FC + Empfänger + ESC-Logik gesamt | – | ≈ 0,5–1,5 W (abgeschätzt) | S | – |
| GPS M10 | 5 g | ≈ 25 mA @ 5 V (0,13 W) | S | [Q93] |
| GPS BN-880 mit Kompass | 10 g | 50 mA @ 5 V (0,25 W) | A | [Q93] |
| LED WS2812B | – | bis 50–60 mA je LED bei vollem Weiß (0,25–0,3 W), Animationen ≈ 20 mA (0,1 W) | M/S | [Q95] |
| Action-Cam ohne Akku (Bones-Bauart) | 54 g | @ 16,4 V: Leerlauf 2,46 W, 1080p60 4,76 W, 4K60 5,58 W | M | [Q91] |
| Action-Cam mit eigenem Akku | 153–155 g (Akku 33 g) | aus eigenem Akku, keine Flugakku-Last | A | [Q92] |

### 4.4 Kamera-Sichtfeld (FOV) der Videomodule

Nachtrag 02.10.2026 (Anforderung: das Sichtfeld kommt wie in echt vom Videomodul). Seit diesem Stand
bestimmt `camera_fov_deg` des Videomoduls das FPV-Bild und „Props im Bild“ (`video.FovMath.cameraHorizontalDegrees`);
die Brille hat kein eigenes Sichtfeld mehr (nur die Kreativ-Brille darf eines frei wählen).

**Welche Zahl:** Propwash zeichnet geradlinig (Minecraft-Projektion) und meint mit dem Sichtfeld den **horizontalen**
Winkel über die volle Bildbreite (`FovMath.verticalFromHorizontal`, 16:9-Fenster). Hersteller nennen meist das
**diagonale** Sichtfeld der Fischaugen-Optik, oft für 4:3 [Q129]. Wo sie H und D angeben, liegt H bei 4:3 bei
0,78–0,82 × D und bei 16:9 bei 0,85–0,86 × D; das horizontale Sichtfeld einer Optik ist in 4:3 und 16:9 gleich, 16:9
schneidet nur oben und unten ab [Q118, Q119, Q122]. Für Kameras ohne H-Angabe gilt deshalb H ≈ 0,80 × D (4:3) bzw.
0,85 × D (16:9) (S). Die DJI-Einheiten nennen zusätzlich die Kleinbild-Brennweite: 14 mm bei der O4 (Lite) ergeben
2 · atan(18 mm / 14 mm) = 104° horizontal über 36 mm Bildbreite (16:9 und 4:3 gleich breit), passend zur fast
verzeichnungsfreien Diagonale von 117,6° [Q124] (S).

| Kamera (Referenz) | Optik | Sichtfeld laut Hersteller | horizontal | Masse | Güte | Quelle |
|---|---|---|---|---|---|---|
| Nano-Kamera Racing (Razer Nano) | 1,8 mm, 14 mm Bauform | D 150° (4:3) / 140° (16:9) | **125°** | 3,9 g | A | [Q119] |
| Nano-Kamera Racing (Racer Nano 4) | 1,8 mm, 14 mm | D 160°, V 97° | **125°** | 4,5 g | A | [Q120] |
| Micro-Kamera Racing (Razer Micro) | 1,8 mm, 19 mm | D 155° (4:3) / 145° (16:9) | **125°** | 4,5 g | A | [Q118] |
| Micro-Kamera „natürliches Bild“ (Razer Mini V3) | 19–22 mm | – | **98°** | 12 g | A | [Q121] |
| Whoop-Kamera (C03) | 2,1 mm | 160° (4:3, diagonal) | ≈ 128° (S) | 1,5 g | A/S | [Q127] |
| Whoop-Kamera (Ant) | 1,8 mm | 165° (diagonal) | ≈ 132° (S) | 2 g | A/S | [Q128] |
| Digitale Nano-Kamera (HDZero Nano V3) | 14 mm | D 155°, V 94° (4:3) | **126°** | 2,2 g | A | [Q122] |
| Digitale Nano-Kamera (HDZero Nano 90 V2) | – | D 162°, V 101° (4:3) | **132°** | 5,2 g | A | [Q122] |
| Digitale Micro-Kamera (HDZero Micro V3) | 19 mm | D 157°, V 72° (16:9) | **133°** | 7,5 g | A | [Q122, Q123] |
| Digitale Kamera weitwinklig (HDZero Lux) | 14 mm | D 170° (16:9) | **145°** | 2,3 g | A | [Q122] |
| DJI O3 | 1/1,7″ | 155° | ≈ 126–132° (S) | 36,4 g | A/S | [Q130] |
| DJI O4 Pro | 1/1,3″, 12 mm KB | 155° | ≈ 126–132° (S) | 32 g | A/S | [Q124] |
| DJI O4 (Lite) | 1/2″, 14 mm KB | 117,6° | ≈ 104° (S) | 8,2 g | A/S | [Q124] |
| Walksnail Avatar HD Pro | 1/1,8″ | 160° | ≈ 136° (S) | 9,5 g (Kamera) | A/S | [Q125] |
| Walksnail Avatar HD Mini 1S Lite | 2,1 mm | 170° | ≈ 145° (S) | 8,7 g (Kit) | A/S | [Q126] |
| Faustregel Brennweite (1/3″, 4:3) | 1,8 / 2,1 / 2,5 mm | D 160–170° / 150–160° / 130–140° | ≈ 130 / 124 / 108° (S) | – | S | [Q129] |

**Sichtfeld je Videomodul** (vorher: Brillen-Standard analog 120°, digital 130°; die JMP-Werte standen schon in der
Datei, wirkten aber nicht auf das Bild):

| Videomodul | Bauform, Einsatz | vorher im Bild | vorher in der Datei | jetzt | Referenz |
|---|---|---|---|---|---|
| `propwash:analog_nano` | Nano 12 mm, Whoop | 120° | – | **125°** | Nano-Kameras 1,8 mm: H 125° [Q119, Q120] |
| `propwash:analog_race` | Micro 19 mm, Race | 120° | – | **125°** | Micro 1,8 mm: H 125° [Q118] |
| `propwash:digital_lite` | Lite-System 8,5 g, 14 mm | 130° | – | **104°** | DJI O4 (Lite) 8,2 g, 117,6° D, 14 mm KB → 104° [Q124] |
| `propwash:digital_hd` | HD-Einheit 23 g | 130° | – | **130°** | DJI O3/O4 Pro, 155° D → 126–132° [Q124, Q130] |
| `propwash_rendertest:hd_pro` (Beispiel-Addon) | HD Pro 28 g | 130° | 155° | **136°** | Walksnail Avatar HD Pro, 160° D → 136° [Q125] |
| `justmoreparts:analog_whoop_25mw` | Whoop-Kombi 2,2 g | 120° | 140° | **126°** | Whoop-Kameras 1,8–2,1 mm: H 125–128° [Q119, Q127] |
| `justmoreparts:analog_race_800` | Micro 19 mm, Race | 120° | 125° | **125°** | Micro 1,8 mm: H 125° [Q118] |
| `justmoreparts:analog_lr_1600` | Micro 19 mm, Long Range | 120° | 120° | **110°** | Long-Range-Optik 2,5 mm (D 130–140°) bzw. „natürliches Bild“ 98° → 110° [Q121, Q129] |
| `justmoreparts:digital_micro` | 4,8 g, 14 mm | 130° | 135° | **126°** | HDZero Nano V3: H 126° [Q122] |
| `justmoreparts:digital_low_latency` | 9,8 g, 19 mm, Race | 130° | 125° | **133°** | HDZero Micro V3: H 133° [Q123] |
| `justmoreparts:digital_hd_pro` | HD Pro 24 g | 130° | 130° | **130°** | DJI O4 Pro, 155° D → 126–132° (Referenz der HD-Einheiten in 4.2/6.3) [Q124] |
| `justmoreparts:digital_lr` | Long Range 19 g | 130° | 120° | **130°** | DJI O3, 155° D → 126–132° [Q130] |

Videomodule anderer Addons ohne `camera_fov_deg` behalten den alten Standard ihres Systems (analog 120°, digital
130°); Werte über 160° zeichnet Propwash mit 160° (geradlinige Projektion, `FovMath.MAX_HORIZONTAL_DEGREES`).

### 4.5 Funkstrecke: Dämpfung durch Hindernisse bei 5,8 GHz

Grundlage der VTX-Abschattung ab 0.5.0 (Modell in [`../vtx-obstruction.md`](../vtx-obstruction.md)). Frequenz 5,8 GHz,
λ = 5,17 cm. Dämpfung je Meter aus der komplexen Permittivität ε = ε′ − jε″: A = 8,686 · k₀ · Im√ε (P.2040 Gl. 23/26,
für die Baustoffe gleich der Näherung A ≈ 1636 · σ/√ε′ aus Gl. 27a); Grenzflächen bei senkrechtem Einfall
−20 · log₁₀(1 − |Γ|²) mit Γ = (1 − √ε)/(1 + √ε); dünne Scheiben als Platte mit Mehrfachreflexion [Q135].

| Klasse (`RfMaterial`) | Blöcke im Spiel | Material und Parameter | dB/m | je Durchgang | gerader Block | Güte | Quelle |
|---|---|---|---|---|---|---|---|
| Laub (`FOLIAGE`) | alle Blätter | P.833-4 §3.2 in Laub: R₀ = 0,2 · f = 1,16 dB/m, R∞ = 1,27/f^0,63 = 0,42 dB/m, k = 27,9 dB (A_min 10 m²), Deckel A_m ≈ 50 dB | – | – | 1,15 (5 Blöcke 5,6, 10 Blöcke 10,7, 30 Blöcke 27,9) | A/S | [Q134, Q133] |
| dünne Pflanzen (`PLANT`) | Gras, Blumen, Getreide, Ranken, Setzlinge, Spinnweben | Laub mit halber Dichte (Annahme) | – | – | 0,58 | S | – |
| Stamm (`TRUNK`) | Stämme und Holz, Kaktus, Pilzstiel | P.2040 Holz ε′ 1,99, σ = 0,0047 · f^1,0718 (trockenes Holz; frisches ist feuchter und dämpft noch stärker) | 35,9 | 0,26 | 36,1 – undurchdringlich, Beugung zählt | A | [Q135] |
| Holzwand (`TIMBER`) | Bretter, Holztreppen, -stufen, -zäune, -türen, Schilder | P.2040 Holz, 0,15 m Wandstärke je Block (Annahme) | 5,4 je Block | 0,26 | 5,6 (Innenwände gemessen 3,6–5,6) | A/S | [Q135, Q137] |
| Mauerwerk (`MASONRY`) | Bruchstein, Ziegel, Steinziegel, Terrakotta, Mauern, alles Unbekannte | P.2040 Ziegel ε′ 3,91, σ = 0,0238 · f^0,16, 0,35 m Wandstärke | 9,1 je Block | 0,99 | 10,1 (Ziegel-Außenwand gemessen 10,2) | A/M | [Q135, Q137] |
| Beton (`CONCRETE`) | Beton, Betonstufen und -treppen | P.2040 Beton ε′ 5,24, σ = 0,0462 · f^0,7822, 0,25 m | 32,6 je Block | 1,47 | 34,1 | A/S | [Q135] |
| Erde und Fels (`EARTH`) | Stein, Tiefenschiefer, Erde, Gras-, Moos-, Schlammblock, Kies, Ton, Erze, Netherrack, Lava | P.2040 mitteltrockener Boden ε′ = 15 · f^−0,1, σ = 0,035 · f^1,63 | 282,7 | 3,31 | 286 – undurchdringlich | A | [Q135] |
| Sand (`SAND`) | Sand, roter Sand, Trockenbeton | P.2040 sehr trockener Boden ε′ 3, σ = 0,00015 · f^2,52 | 11,9 | 0,65 | 12,5 | A | [Q135] |
| Glas (`GLASS`) | Glas, Buntglas, Scheiben, Glowstone-artige | P.2040 Glas ε′ 6,31, σ = 0,0036 · f^1,3394, als Fensterscheibe 6 mm | – | 3,2 je Scheibe | 3,2 | A/S | [Q135] |
| Metall (`METAL`) | Eisen-, Gold-, Kupferblöcke, Ambosse, Kessel, Schienen | P.2040 Metall σ = 10⁷ S/m | ≈ 4 · 10⁶ | – | undurchdringlich | A | [Q135] |
| Metallgitter (`METAL_MESH`) | Gitter, Ketten, Kupferroste | Drahtgitter mit Maschen über λ/2 (Annahme) | – | 6 | 6 | S | – |
| Wasser (`WATER`) | Wasser, wassergeflutete Blöcke, Seetang, Korallenblöcke, Schleim | P.527 Süßwasser 15 °C: ε = 71,6 − j26,1 | 1602 | 8,8 | undurchdringlich | A | [Q136] |
| Eis (`ICE`) | Eis, Packeis, Blaueis | P.527 Eis −5 °C: ε′ 3,184, ε″ 5,5 · 10⁻⁴ | 0,16 | 0,72 | 0,88 | A | [Q136] |
| Schnee (`SNOW`) | Schneeblock, Pulverschnee, Schneeschicht | P.527 trockener Schnee 0,3 g/cm³: ε′ 1,57 | 0,045 | 0,11 | 0,16 | A | [Q136] |
| Stoff (`CLOTH`) | Wolle, Teppiche, Betten, Banner | P.2040 Deckenplatte (Faserstoff) ε′ 1,48 als Ersatz | 9,8 | 0,08 | 9,9 | S | [Q135] |

Beugung um Hindernisse nach P.526 [Q132]: Radius der ersten Fresnel-Zone r₁ = √(λ · d₁ · d₂ / (d₁ + d₂)) (§2.1),
Beugungsparameter ν = h · √(2/λ · (1/d₁ + 1/d₂)) = √2 · h/r₁ (Gl. 26), Messerkante J(ν) = 6,9 + 20 · log₁₀(√((ν − 0,1)² + 1) +
ν − 0,1) für ν > −0,78 (Gl. 31; 6,0 dB bei ν = 0, 13,9 dB bei ν = 1), Hindernis endlicher Breite als Summe der Kanten
(§5.1: Amplituden für den kleinsten, Leistungen für den mittleren Verlust), beliebige Formen als Summe von Rechtecken
(§5.2). Vegetation: Gesamtverlust einer Baumkrone ist die Leistungssumme aus Beugung über und neben der Krone, Bodenweg
und Durchgang (P.833-10 §3.2.1.5 Gl. 16); der Durchgang ist durch den leichteren Weg außen herum begrenzt (§3.1).

Messungen bei 5,85 GHz in und um Wohnhäuser und Bäume [Q137 M]: Laubbäume wirken wie „schwebende Massen“ mit
typisch 10–13 dB Zusatzdämpfung, Nadelbaum-Reihen 11–16 dB (bei 1,5 m Empfängerhöhe 11,5 dB, unter einer Laubkrone
hindurch nur 4,4 dB), ein kleiner Laubbaum 3,5 dB; Ziegel-Außenwand 10,2 dB, Innenwände 3,6–5,6 dB (typisch 4,7 dB),
Abschattung durch ein ganzes Haus im Beispiel 10,8 dB, Fenster sind die Wege mit wenig Dämpfung. RET-Parameter aus
P.833-10 Tabelle 8 bei 5,5 GHz (Extinktion 0,2–0,56 /m, also 0,9–2,4 dB/m für den kohärenten Anteil) stützen die
Anfangssteigung des Laubmodells [Q133].

## 5. Flugzeit-Referenzen aus Tests

| Fluggerät | AUW | Akku | Flugzeit | Bedingungen | Güte | Quelle |
|---|---|---|---|---|---|---|
| 65 mm, 0702 23000KV, 31 mm | 17,25 g trocken (≈ 25,5 g mit 300 mAh) | 1S 260 / 300 LiHV | 4:00 / 4:30 min | konstant 50–60 % Gas, zügig, analog 400 mW | M | [Q100] |
| 65 mm Race, 0702 27000KV | – | 1S 260 / 300 LiHV | 3:40 / 4:00 min | wie oben | M | [Q100] |
| 65 mm | 32 g mit Akku | 1S 300 LiHV | 2–2,5 min | Serienzustand, PH2.0-Zeit | M | [Q38] |
| 65 mm digital | 23,5 g | 1S 300 | ≈ 2 min | draußen, 25 mW | S | [Q114] |
| 75 mm, 1102 22000KV, 45 mm 3-Blatt | 31,1 g trocken, 45,3 g AUW | 1S 550 | ≈ 4 min / ≈ 5 min | draußen gemischt / drinnen ruhig | M | [Q101] |
| 3″ 1S Toothpick | – | 1S 300 / 450 / 650 | 4 / 6 / > 8 min | allgemein, „sehr effizient“ | M | [Q102] |
| 3″ 1S Toothpick | – | 1S 450 | 5:30 Schweben, < 3 min mit Vollgas-Stößen | – | M | [Q103] |
| 3″ Freestyle digital | – | 4S 650 | 3:30–4 min | ruhig | M | [Q104] |
| 3″ Freestyle | – | 4S 850 | 4–5 min / ≈ 7 min | scharf / ruhig | M | [Q105] |
| 3,5″ Cinewhoop 6S | 275 g laut Hersteller (mit O3) | 6S 1050–1400 | ≈ 4 min gemessen (Herstellerangabe 10–12 min) | – | M/A | [Q106] |
| 5″ Freestyle | 600–750 g | 6S 1300 / 6S 1100 | 4–5 min scharf / 4–7 min | – | S | [Q107] |
| 7″ LR, 2809 1450KV, 7,5″ 3-Blatt, O4 Pro | 1218 / 1265 / 1480 g | 6S 2200 LiPo / 6S1P 4000 Li-Ion / 6S2P 6000 Li-Ion | knapp 16 min (6000 Li-Ion), > 15 km | Reise 60–70 km/h ≈ 35 % Gas, Schweben ≈ 30 %, Spitze ≈ 110 km/h | M | [Q108] |
| 7″ LR | – | 6S 4000 Li-Ion | 15–22 min, 18–22 min bei 40–50 km/h | – | S | [Q109] |
| 4″ LR | – | 4S 18650 | ≈ 30 min | ruhiges Cruisen | S | [Q24] |

Abgeleiteter Durchschnittsstrom (S): 65-mm-Whoop ≈ 3,2 A (≈ 11–12 W), 75-mm-Whoop ≈ 5–6 A (≈ 20 W),
5″ scharf ≈ 15–20 A (≈ 330–440 W bei 22 V), 7″-Li-Ion-Reise (1,48 kg, bei 80–90 % Entnahme) ≈ 390–440 W bzw.
260–300 W/kg.

## 6. Abgleich mit den heutigen Teiledaten

Stand: `integration` (Propwash 0.4.1) und Just More Parts im Nachbarprojekt. Nur Befund, keine Änderung.

### 6.1 Modellkonstanten (`sim.ModelConstants`, `sim.ParamDerivation`, `sim.OperatingPoints`)

| Stelle | Heute | Referenz | Befund |
|---|---|---|---|
| Nutzbare Kapazität Flugzeit | fest 80 % der Nennkapazität | LiPo 80–86 % bis 3,5 V Ruhe [Q40]; Li-Ion 85–90 % bis 2,8–3,0 V [Q24, Q25] | LiPo passend (eher knapp); Li-Ion 5–10 % zu vorsichtig |
| OCV-Kurve LiPo | SoC 0 = 3,5 V, 20 % Rest = 3,70 V | 20 % Rest ≈ 3,60 V, 10 % ≈ 3,50 V, 0 % ≈ 3,30 V [Q41] | Modell setzt „leer“ bei 3,5 V Ruhe; real sind dort noch ≈ 10 % drin |
| `WIRING_R` | 3 mΩ | Stecker XT30/XT60/XT90 0,3–0,7 mΩ, PH2.0 ≈ 20 mΩ [Q38, Q46] | für XT-Stecker plausibel (Kabel inbegriffen); PH2.0-Packs bräuchten ≈ 20 mΩ mehr |
| `POL_FRACTION` 0,5, τ 5 s | Polarisation = 50 % des Ohmschen Anteils | 10-s-DC ≈ 3,3 × 1-kHz-AC [Q37]; 1–5 s ≈ 1,5–2,5 × Ladegerät-IR | Größenordnung stimmt, wenn `cell_resistance_ohm` ≈ 1–1,7 × Ladegerät-IR ist |
| `ESC_R_BASE + ESC_R_PER_A/I` | 3 mΩ + 0,15 V/I: 5 A → 33 mΩ, 20 A → 10,5 mΩ, 55 A → 5,7 mΩ | Whoop 1S ≈ 75–95 mΩ, 30–33 A 2,4–6,2 mΩ, 55–60 A 1,4–1,5 mΩ (+ Leiterbahn) [Q68, Q69] | Whoop-ESC ≈ 2,5 × zu niedrig, große ESCs ≈ 2–3 × zu hoch |
| `ESC_BURST` | 1,3 | 1,2–1,42 [Abschnitt 3.2] | passend |
| `AUX_BASE_W + AUX_PER_GRAM_W × Video-Masse` | 0,6 W + 0,3 W/g | Grundlast 0,5–1,5 W; Video siehe 6.3 | analog passend, digital klein zu niedrig (Leistung hängt nicht an der Masse) |

### 6.2 Akkus

| Teil | Masse heute | Referenz | IR heute (je Zelle) | Referenz-Vorschlag | C heute | Befund |
|---|---|---|---|---|---|---|
| `propwash:b1s_300_hv` | 7,5 g | 8,2–8,4 g | 40 mΩ | 33–50 mΩ | 45 | Masse ≈ 10 % zu leicht |
| `propwash:b4s_650` | 78 g | 64–80 g (4S 650–680) | 10 mΩ | 9–15 mΩ | 95 | passend |
| `propwash:b6s_1100` | 175 g | 177–192 g | 6 mΩ | 3,6–5,5 mΩ | 120 | passend, IR leicht hoch |
| `propwash:b6s_1300` | 210 g | 198–208 g | 5,5 mΩ | 3,1–4,6 mΩ | 120 | passend, IR leicht hoch |
| `propwash:b6s_4000_lion` | 420 g | 398–430 g | 12 mΩ | 11–18 mΩ | 11 (44 A) | passend |
| `justmoreparts:b1s_250_hv` … `b1s_750_hv` | 6,3–18,8 g | 6,5–19 g | 22–50 mΩ | 13–60 mΩ (750 → 250 mAh), PH2.0 +20 mΩ | 45–75 | passend |
| `justmoreparts:b2s_300_hv`, `b2s_450_hv`, `b2s_650` | 16,3 / 24,4 / 35,6 g | ≈ 17–19 (S) / 27–29 / 33–36 g | 45 / 30 / 18 mΩ | 20–33 / 13–22 / 9–15 mΩ | 45 / 75 / 80 | `b2s_450_hv` ≈ 12 % zu leicht |
| `justmoreparts:b3s_450`, `b3s_650` | 40,0 / 57,7 g | 38–44 / 48–53 g | 20 / 14 mΩ | 13–22 / 9–15 mΩ | 75 / 90 | passend |
| `justmoreparts:b4s_450`, `b4s_850` | 53,3 / 95,3 g | 52–57 / 88–95 g | 20 / 11 mΩ | 13–22 / 7–12 mΩ | 80 / 100 | passend |
| `justmoreparts:b4s_1100` | 120,6 g | ≈ 130–135 g (S, aus 4S 1300 = 156 g) | 9 mΩ | 3,6–5,5 mΩ | 100 | ≈ 8 % zu leicht, IR hoch |
| `justmoreparts:b4s_1500` | 160,9 g | 175–190 g | 5,5 mΩ | 2,7–4 mΩ | 120 | ≈ 10–15 % zu leicht |
| `justmoreparts:b4s_1800` | 190,3 g | ≈ 195–215 g (S) | 5 mΩ | 2,2–3,3 mΩ | 100 | eher leicht |
| `justmoreparts:b6s_550` / `750` / `850` | 97,7 / 130,1 / 145,2 g | ≈ 90–100 / 120–135 / 135–150 g (S) | 18 / 13 / 11 mΩ | 11–18 / 8–13 / 7–12 mΩ | 80–100 | passend |
| `justmoreparts:b6s_1500` / `1800` / `2200` | 237,9 / 281,4 / 336,8 g | 222–251 / 287 / 321–349 g | 5 / 4,5 / 4 mΩ | 2,7–4 / 2,2–3,3 / 1,8–2,7 mΩ | 120 / 100 / 75 | Masse passend, IR leicht hoch |
| `justmoreparts:b6s_5000` | 740 g | 710–790 g | 3 mΩ | 0,8–1,5 mΩ | 60 | Masse passend, IR hoch |
| `justmoreparts:b8s_2200` | 449,1 g | 440–480 g | 4 mΩ | 1,8–2,7 mΩ | 100 | Masse passend, IR leicht hoch |
| `justmoreparts:b8s_5000` | 986,7 g | ≈ 950–1050 g (S) | 3 mΩ | 0,8–1,5 mΩ | 60 | Masse passend, IR hoch |
| `justmoreparts:b6s_3000_lion` | 301,4 g | 314 g | 20 mΩ | 18–25 mΩ (18650) | 12 | passend |
| `justmoreparts:b6s_4500_lion` | 445,9 g | 428,7 g | 16 mΩ | 10–16 mΩ (21700) | 10 (45 A; Pack-Angabe 70 A) | passend |
| `justmoreparts:b6s_8400_lion` | 843,9 g | ≈ 849 g | 9 mΩ | 5,4–8,3 mΩ (2P) | 10 | passend |
| `justmoreparts:b8s_4500_lion` | 594,5 g | ≈ 580–600 g (S) | 16 mΩ | 10–16 mΩ | 10 | passend |
| `justmoreparts:b6s_3000_ss` | 312,0 g (222 Wh/kg bei 3,85 V nominal) | Semi-Solid kleiner Packs unbelegt; große UAV-Packs 250–320 Wh/kg | 7 mΩ | – | 25 | C zu hoch: Semi-Solid-UAV-Packs 3–5 C Dauer (eine Serie wirbt mit 10 C), Spitze 5–10 C [Q115–Q117] |
| `justmoreparts:b8s_5000_ss` | 641,7 g (240 Wh/kg) | wie oben | 6 mΩ | – | 20 | C zu hoch, siehe oben |

Die `c_rating`-Werte liegen durchweg auf Aufdruck-Niveau (45–120 C); gemessen erreichen die besten 6S/8S-Packs
im Burst 68–74 C [Q17]. Wenn die Strombegrenzung im Modell greifen soll, gehören wirksame Werte hinein
(≈ 0,4–0,6 × Aufdruck für Dauerlast).

### 6.3 Video

| Teil | Masse heute | Leistung heute (0,6 W + 0,3 W/g) | Referenz Masse | Referenz Leistung (inkl. ≈ 0,6–1 W Grundlast) |
|---|---|---|---|---|
| `propwash:analog_nano` | 4,0 g | 1,8 W | 2–4 g | 1,5–2,5 W |
| `propwash:analog_race` | 8,0 g | 3,0 W | 10–15 g (Micro-Kamera 4,5–6 g + VTX 4,5–6,8 g + Antenne) | 2,5–6,5 W (25–800 mW) |
| `propwash:digital_lite` | 8,5 g | 3,15 W | 8,2–10 g | 5–7 W |
| `propwash:digital_hd` | 23 g | 7,5 W | 32–40 g | 8–12 W |
| `justmoreparts:analog_whoop_25mw` | 2,2 g | 1,26 W | 2–3 g | 1,3–2 W |
| `justmoreparts:analog_race_800` | 9,5 g | 3,45 W | 10–15 g | 5,5–6,5 W bei 800 mW |
| `justmoreparts:analog_lr_1600` | 16 g | 5,4 W | 20–25 g (12 g VTX + Kamera + Antenne) | 7,5–11 W (S) |
| `justmoreparts:digital_micro` | 4,8 g | 2,0 W | 6–10 g | 4,5–6,5 W |
| `justmoreparts:digital_low_latency` | 9,8 g | 3,5 W | 8–11 g | 6–7 W |
| `justmoreparts:digital_lr` | 19 g | 6,3 W | 20–36 g | 8–15 W |
| `justmoreparts:digital_hd_pro` | 24 g | 7,8 W | 32–40 g | 8–11 W |

Befund: Digitale Systeme sind im Modell bei der Leistung um 30–60 % zu niedrig (am stärksten die kleinen), die
HD-Einheiten ≈ 10 g zu leicht. Analoge Werte passen bis ≈ 200 mW; die 800/1600-mW-Teile ziehen bei voller
Sendeleistung deutlich mehr.

### 6.4 Power-Stacks

| Teil | Masse heute | Referenz | Befund |
|---|---|---|---|
| `propwash:aio_whoop` (5 A, 1–2S) | 3,2 g | 2,96–3,64 g | passend |
| `propwash:aio_20` (25 A, 2–6S) | 7,0 g | 7,9–9,8 g (35 A) | leicht |
| `propwash:stack_30` (55 A, 3–6S) | 18,0 g | 25–35 g | ≈ 30 % zu leicht |
| `justmoreparts:aio_whoop_1s_6a` / `aio_whoop_2s_12a` | 2,6 / 3,6 g | 2,96–3,64 / 3,6–5,1 g | passend |
| `justmoreparts:aio_tp_4s_20a` / `aio_tp_6s_20a` | 5,0 / 6,2 g | 5,76–6,63 g | passend bis leicht |
| `justmoreparts:aio_20_4s_35a` / `aio_20_6s_45a` | 8,5 / 11,0 g | 7,9–9,8 / 11–13,6 g | passend |
| `justmoreparts:stack_20_6s_40a` | 14,0 g | 15–21,5 g | leicht |
| `justmoreparts:stack_30_4s_55a` / `stack_30_6s_60a` / `stack_30_6s_80a` | 16 / 21 / 27 g | 25–35 g (30×30 mit FC) | 4S-Stack leicht, Rest passend |
| `justmoreparts:stack_30_8s_65a` | 25 g | 15,3–17,6 g ESC + 7–10 g FC | passend |
| `justmoreparts:stack_30_8s_100a` | 44 g | 4-in-1 80 A mit Deckel 65 g + FC; 4 × Einzel 80 A 38–58 g + FC | eher leicht |

Burst-Werte der JMP-Stacks (1,22–1,33 × Dauer) liegen im Referenzband.

### 6.5 Plausibilisierung des Besitzer-Beispiels (Stromseite)

„Deadcat LR 7″ · 8S“ mit 8S 2200: 896,9 g, Schweben 24,2 min, Reise 10,5 min, Vollgas 137,5 A.

- Pack 449,1 g liegt im Referenzband 440–480 g. 8S an einem 7″ ist selten; üblich sind 6S-LiPo 1800–2200 oder
  6S-Li-Ion [Q34, Q108].
- Energie 65,1 Wh nominal, 80 % davon 52 Wh. 24,2 min Schweben entsprechen Ø ≈ 129 W (≈ 144 W/kg). Grobe
  Strahltheorie-Abschätzung (S): 0,9 kg an 4 × 7″ brauchen ideal ≈ 53 W; mit Gütegrad 0,5–0,6 und Motor/ESC
  0,75–0,85 ergeben sich 105–150 W – das Modell liegt damit im oberen, eher optimistischen Teil.
- 10,5 min Reise entsprechen Ø ≈ 300 W (≈ 330 W/kg); die 7″-Li-Ion-Messung ergibt bei 60–70 km/h ≈ 260–300 W/kg
  [Q108, abgeleitet]. Das Modell ist beim Reiseflug also eher zu pessimistisch, beim Schweben eher zu optimistisch.
- 137,5 A aus 2200 mAh sind 62 C – oberhalb dessen, was 8S-Packs im Test als effektives Burst-C schaffen (68 C
  für das beste Pack [Q17]); deutlicher Einbruch auf ≈ 3,3–3,5 V je Zelle wäre realistisch.

## 7. Vorschläge für die Kalibrierung (Power/Video)

1. **Akkumassen:** wo in 6.2 „zu leicht“ steht, auf die Referenzmitte heben (b1s_300_hv ≈ 8,3 g, b2s_450_hv ≈ 28 g,
   b4s_1100 ≈ 130 g, b4s_1500 ≈ 180 g, b4s_1800 ≈ 205 g).
2. **`cell_resistance_ohm`:** als Modellwert ≈ 1,2–1,7 × Ladegerät-IR (dann trifft die Polarisation 0,5 × obendrauf
   den Sekundenwert) und über „Widerstand × Ah“ skalieren: High-C-LiPo ab 1000 mAh 4–6 mΩ·Ah, Micro-LiPo/LiHV
   300–850 mAh (2–6S) 6–10 mΩ·Ah, 1S-Whoop-LiHV 10–15 mΩ·Ah, Li-Ion 21700 45–70 mΩ·Ah, 18650 55–75 mΩ·Ah
   (bei 2P durch die Gesamtkapazität teilen). Die Referenzspalte in 6.2 ist so gerechnet.
3. **`c_rating`:** als wirksamen Dauerwert pflegen (LiPo 40–75, Whoop 30–50, Li-Ion 8–15), nicht als Aufdruck.
4. **ESC-Widerstand:** Whoop-AIOs ≈ 60–90 mΩ, Toothpick 12–20 A ≈ 7–15 mΩ, 30–45 A ≈ 3–6 mΩ, 50–80 A ≈ 1,5–3 mΩ;
   je Teil über `esc_resistance_ohm` oder als neue Standardformel. Fit an Abschnitt 3.3 (S):
   R_ESC ≈ 1,5 mΩ + 2,0 Ω·A² / I_Dauer² → 5 A 81 mΩ, 12 A 15 mΩ, 20 A 6,5 mΩ, 33 A 3,3 mΩ, 60 A 2,1 mΩ.
5. **Video-Leistung:** nicht mehr aus der Masse ableiten. Analog ≈ 0,5–1 W Kamera + 1,0 W + 4,3 × P_HF; digital
   ≈ 4–7,5 W + 1,5–3 × P_HF (DJI/Walksnail), HDZero-Freestyle bis 15 W. Massen der HD-Einheiten auf 32–36 g.
6. **Nutzbare Kapazität:** je Chemie statt fest 0,8 – LiPo/LiHV 0,80–0,85, Li-Ion 0,85–0,90.
7. **Stack-Massen:** `propwash:stack_30` ≈ 26–30 g, `justmoreparts:stack_20_6s_40a` ≈ 17 g,
   `justmoreparts:stack_30_4s_55a` ≈ 22 g.

### API-Wünsche (für den Basis-Schritt)

- Videoleistung als Datum: JSON `"power_w"` in `video`, Record-Komponente `OptionalDouble powerW()` in
  `de.lardtom.propwash.api.part.VideoDefinition` und `VideoDefinition.Builder#powerWatts(double watts)`; Ableitung nutzt
  sie vor der Massenformel. Heute kann ein Addon die Leistungsaufnahme seines Videosystems nicht angeben.
- Nutzbarer Anteil je Chemie: JSON `"usable_fraction"` in `chemistry` (Standard 0,8) mit
  `ChemistryDefinition#usableFraction()` bzw. Builder `usableFraction(double fraction)`, damit Li-Ion-Packs ihre
  flachere Endphase ausspielen.

## 8. Unsicherheiten

- Innenwiderstände sind stark messabhängig (Faktor 2–3 zwischen Ladegerät und 10-s-DC); die Tabellen geben
  Ladegerätwerte an, die Vorschläge in Abschnitt 7 rechnen auf den Sekundenwert um.
- „Effektives C“ [Q17] ist über eine Spannungsgrenze definiert, die nicht öffentlich ist; nur als Obergrenze nutzen.
- DJI O3 hat keine veröffentlichte Flugmessung; die 8–11 W sind aus den O4-Pro-Messungen und „≈ 40 % mehr als Vista“
  [Q71] abgeleitet. Walksnail-Angaben (4,5 W) sind Herstellerwerte und wirken niedrig.
- X-Class-Werte stammen überwiegend aus Herstellertexten (Startmassen „5–15 kg“ halte ich für Transport-/Cinelifter,
  Race-X-Class eher 2–5 kg; ein Suchtreffer zu [Q113] nennt 2,23 kg AUW, die Originalseite war nicht abrufbar).
- Flugzeiten hängen stark am Flugstil; Reviews nennen meist nur Minuten ohne verbrauchte mAh.

## 9. Quellen

Zugriff am 30.09.2026.

- [Q1] https://betafpv.com/products/lava-1s-300mah-75c-battery-5pcs
- [Q2] https://betafpv.com/products/lava-1s-450mah-75c-battery-4pcs
- [Q3] https://betafpv.com/products/lava-1s-550mah-75c-battery-4pcs
- [Q4] https://oscarliang.com/whoop-toothpick-lipo-battery/
- [Q5] https://www.racedayquads.com/products/gaoneng-gnb-7-6v-2s-450mah-80c-lihv-micro-battery-long-type-xt30
- [Q6] https://rotorgeeks.com/gnb-3s-650mah-xt30
- [Q7] https://www.racedayquads.com/products/gaoneng-gnb-11-4v-3s-850mah-120c-lihv-micro-battery-xt30-1
- [Q8] https://www.progressiverc.com/products/tattu-r-line-v5-4s-1300mah-150c
- [Q9] https://oscarliang.com/acehe-formula-4s-95c-lipo-batteries/
- [Q10] https://oscarliang.com/vcanz-4s-1350-1550mah-75c-95c-lipo-batteries/
- [Q11] https://oscarliang.com/gnb-6s-lihv-lipo-batteries/
- [Q12] https://genstattu.com/tattu-r-line-v6-1300mah-6s-160c-22-2v-st-lipo-battery-with-xt60-plug/ ,
  https://www.readymaderc.com/products/details/tattu-r-line-v4-1300mah-6s-130c-xt60-lipo ,
  https://www.getfpv.com/tattu-r-line-version-3-0-1300mah-6s-120c-lipo-battery.html
- [Q13] https://genstattu.com/ta-rl3-120c-1400-6s1p.html
- [Q14] https://genstattu.com/tattu-r-line-version-5-0-1800mah-6s-150c-22-2v-lipo-battery-pack-with-xt60-plug/
- [Q15] https://genstattu.com/tattu-2200mah-6s-95c-22-2v-r-line-lipo-battery-with-xt60-plug-for-7-quad/
- [Q16] https://chinahobbyline.com/products/cnhl-gplus-series-2200mah-22-2v-6s-70c-lipo-battery-with-xt60-plug
- [Q17] https://blog.unmanned.tech/exploring-6s-vs-8s-battery-performance/
- [Q18] https://genstattu.com/tattu-r-line-version-5-0-1050mah-8s-150c-29-6v-lipo-battery-pack-with-xt60-plug/
- [Q19] https://pyrodrone.com/products/gaoneng-gnb-1530mah-29-6v-8s-160c-lipo-battery-xt60
- [Q20] https://pyrodrone.com/products/gaoneng-gnb-2600mah-29-6v-8s-120c-lipo-battery-xt60
- [Q21] https://maxamps.com/products/lipo-2000-8s-29-6v-battery-pack , https://maxamps.com/products/lipo-2800-8s-29-6v-battery-pack
- [Q22] https://chinahobbyline.com/products/cnhl-gplus-series-5000mah-22-2v-6s-70c-lipo-battery-with-xt90-plug ,
  https://chinahobbyline.com/products/cnhl-black-series-5000mah-22-2v-6s-65c-lipo-battery-with-xt90-plug ,
  https://chinahobbyline.com/products/cnhl-5000mah-22-2v-6s-20c-lipo-battery-with-xt90-plug
- [Q23] https://www.progressiverc.com/products/tattu-r-line-6s-4500mah-95c ,
  https://genstattu.com/tattu-4500mah-22-2v-25c-6s1p-lipo-battery-pack-with-xt60-plug.html
- [Q24] https://oscarliang.com/li-ion-battery-long-range/
- [Q25] https://oscarliang.com/auline-6s-4000mah-45a/
- [Q26] https://oscarliang.com/auline-v45-6s-3000mah-s70-4500mah-li-ion-battery/
- [Q27] https://oscarliang.com/best-18650-li-ion-battery/
- [Q28] https://www.getfpv.com/flywoo-explorer-lionpack-18650-4s-3000mah-14-8v-li-ion-battery.html ,
  https://flywoo.net/products/flywoo-explorer-molicell-p30b-18650-4s1p-3000mah-lionpack-battery
- [Q29] https://iflight-rc.eu/en/products/fullsend-6s-4000mah-li-ion-battery
- [Q30] https://www.getfpv.com/upgrade-energy-dark-lithium-v2-21700-8400mah-6s-22-2v-25c-li-ion-battery-xt60.html
- [Q31] https://www.dnkpower.com/wp-content/uploads/2022/08/Molicel-INR21700P42A-datasheet.pdf
- [Q32] https://www.aboutenergy.io/voltt-battery-library/molicel-p45b , https://www.aboutenergy.io/cell-library-/molicel-inr-21700-p50b
- [Q33] https://www.epectec.com/downloads/INR-18650-P28A-Product-Data-Sheet.pdf , https://www.18650batterystore.com/products/sony-vtc6
- [Q34] https://oscarliang.com/lipo-battery-guide/
- [Q35] https://oscarliang.com/when-retire-lipo-battery/
- [Q36] https://www.rchelicopterfun.com/lipo-internal-resistance.html
- [Q37] https://ionworks.com/blog/acir-vs-dcir
- [Q38] https://oscarliang.com/betafpv-meteor65-bt2/
- [Q39] https://oscarliang.com/micro-battery-connectors-testing/
- [Q40] https://oscarliang.com/how-to-choose-battery-for-quadcopter-multicopter/
- [Q41] https://www.ufinebattery.com/blog/4s-lipo-battery-voltage-chart-full-nominal-storage-and-cutoff-explained/
- [Q42] https://intofpv.com/t-tattu-r-line-v3-1300mah-6s-120c , https://chinahobbyline.com/blogs/news/best-battery-for-5-inch-quad ,
  https://www.alibaba.com/product-insights/how-to-choose-the-best-6s-lipo-batteries-a-complete-buyer-s-guide.html
- [Q43] https://greyarro.ws/t/1s-lipo-resistance/54432
- [Q44] https://zbotic.in/drone-battery-voltage-sag-why-it-happens-and-how-to-fix-it/
- [Q45] https://betafpv.com/products/bt2-0-connectors-10pcs , https://www.getfpv.com/learn/fpv-product-reviews-and-comparisons/betafpv-bt2-0-connector/
- [Q46] https://media.digikey.com/pdf/Data%20Sheets/DFRobot%20PDFs/FIT0586_Web.pdf ,
  https://media.digikey.com/pdf/Data%20Sheets/DFRobot%20PDFs/FIT0588_Web.pdf
- [Q47] https://docs.holybro.com/power-module-and-pdb/power-module/connector-and-wire-rating ,
  https://connectorsupplier.com/meet-the-connector-xt-connectors/
- [Q48] https://www.researchgate.net/publication/318294444_Experimental_Testing_of_Electronic_Speed_Controllers_for_UAVs ,
  https://www.tytorobotics.com/blogs/articles/what-is-the-average-efficiency-of-an-electric-motor
- [Q50] https://betafpv.com/products/f4-1s-5a-aio-brushless-flight-controller-elrs-2-4g
- [Q51] https://intofpv.com/t-some-notable-aios-for-1s-2s-toothpicks , https://betafpv.com/products/f4-1s-5a-aio-brushless-flight-controller
- [Q52] https://www.getfpv.com/happymodel-x12-aio-5-in-1-board-f4-fc-12a-esc.html
- [Q53] https://betafpv.com/products/f4-2-4s-aio-brushless-fc-12a-for-toothpick ,
  https://betafpv.com/products/toothpick-f4-20a-aio-brushless-flight-controller ,
  https://betafpv.com/products/toothpick-f405-2-4s-aio-brushless-flight-controller-20a-blheli_32-v4
- [Q54] https://www.getfpv.com/jhemcu-ghf420aio-35a-blheli-s-2-6s-aio-whoop-flight-controller.html
- [Q55] https://pyrodrone.com/products/hglrc-zeus35-aio-3-6s-f4-flight-control-35a-bls-4in1-esc-20x20-1
- [Q56] https://www.speedybee.com/speedybee-f405-aio-40a-bluejay-25-5x25-5-3-6s-flight-controller/ ,
  https://www.getfpv.com/speedybee-f405-v2-aio-f405-fc-40a-3-6s-bluejay-esc-25-5x25-5.html
- [Q57] https://www.speedybee.com/speedybee-f405-mini-bls-35a-20x20-stack/ , https://oscarliang.com/speedybee-f405-mini-fc-stack/
- [Q58] https://www.getfpv.com/hglrc-zeus-f745-20x20-stack-f722-fc-45a-bl32-4-in-1-esc.html ,
  https://www.getfpv.com/hglrc-zeus-f745-v2-stack-f722-mini-fc-45a-3-6s-blheli-s-esc.html
- [Q59] https://wrekd.com/products/speedybee-f405-v4-bls-3-6s-30x30-stack-combo-f405-fc-8bit-55a-4in1-esc ,
  https://www.speedybee.com/speedybee-f405-v4-bls-55a-30x30-fc-esc-stack/
- [Q60] https://www.getfpv.com/t-motor-f55a-pro-ii-55a-3-6s-blheli32-4-in-1-esc-w-bec.html
- [Q61] https://holybro.com/products/tekko32-f4-4in1-50a-esc , https://holybro.com/products/tekko32-f4-4in1-60a-esc ,
  https://holybro.com/products/tekko32-f4-metal-4in1-65a-esc-65a
- [Q62] https://www.getfpv.com/dys-65a-2-8s-am-32-4-in-1-esc-30x30.html
- [Q63] https://www.readymaderc.com/products/details/86280-tbs-lucid-4in1-8s-60a-esc
- [Q64] https://www.readymaderc.com/products/details/87220-newbeedrone-hummingbird-305-4in1-esc-80a-3-8s-am32-30x30-built-for-durability-reliability
- [Q65] https://shop.iflight.com/BLITZ-E80-4-IN1-Pro-ESC-G2-Pro1770 ,
  https://www.getfpv.com/iflight-blitz-e80-pro-g2-80a-4-in-1-blheli-32-esc-35x35-30x30.html
- [Q66] https://www.getfpv.com/iflight-blitz-e80-80a-2-8s-blheli-32-single-esc.html , https://shop.iflight.com/BLITZ-E80-Single-ESC-Pro-1797
- [Q67] https://www.hobbywingdirect.com/collections/xrotor-pro-esc , https://www.hobbywing.com/en/products/xrotorh60a14sfoc
- [Q68] https://docs.robofusion.net/esc/bluejay-4in1-esc-user-manual , https://micoair.com/esc/
- [Q69] https://www.vishay.com/docs/64832/sia517dj.pdf
- [Q70] https://www.racedayquads.com/products/speedybee-bls-8bit-55a-3-6s-30x30-4in1-esc
- [Q71] https://oscarliang.com/dji-o3-air-unit-fpv-goggles-2/
- [Q72] https://www.dji.com/support/product/o3-air-unit
- [Q73] https://oscarliang.com/dji-o4-air-unit-pro/ , https://www.dji.com/o4-air-unit/specs
- [Q74] https://oscarliang.com/dji-o4-air-unit-lite/
- [Q75] https://oscarliang.com/walksnail-avatar-hd-pro-v2-kit/ , https://www.getfpv.com/walksnail-avatar-hd-pro-kit-32gb-w-gyro.html
- [Q76] https://oscarliang.com/setup-avatar-fpv-system/ , https://www.caddxfpv.com/products/walksnail-avatar-hd-vtx-v2-only
- [Q77] https://oscarliang.com/walksnail-avatar-hd-mini-1s-lite-kit/
- [Q78] https://fpvwiki.co.uk/hdzero-vtx-modules , https://oscarliang.com/hdzero-freestyle-v2-vtx/
- [Q79] https://oscarliang.com/hdzero-race-v3-vtx/
- [Q80] https://www.team-blacksheep.com/media/files/tbs-unify-pro32-manual.pdf
- [Q80b] https://docs.hd-zero.com/whoop-lite
- [Q81] https://cubefpv.com/product/analog-vtx-rush-tank-mini-ultimate-800mw/
- [Q82] https://oscarliang.com/rush-vtx-tank-plus-race-mini-tiny/
- [Q83] https://www.getfpv.com/rushfpv-rush-tank-solo-5-8ghz-vtx.html
- [Q84] https://www.progressiverc.com/products/runcam-nano-3 , https://oscarliang.com/runcam-nano-3/
- [Q85] https://flywoo.net/products/caddx-ant-1200tvl-1-8mm-fpv-nano-camera-14x14
- [Q86] https://www.getfpv.com/foxeer-razer-nano-1200tvl-1-8mm-fpv-camera-ntsc.html
- [Q87] https://www.getfpv.com/foxeer-predator-5-micro-1000tvl-m8-1-7mm-fpv-camera-full-case.html ,
  https://www.getfpv.com/foxeer-predator-5-nano-1000tvl-1-7mm-fpv-camera-pad-plug.html
- [Q88] https://shop.runcam.com/runcam-phoenix-2/ , https://shop.runcam.com/runcam-phoenix-2-special-edition/ ,
  https://shop.runcam.com/runcam-phoenix-2-sp/ , https://shop.runcam.com/runcam-phoenix-2-spv5/
- [Q89] https://www.getfpv.com/caddx-ratel-2-micro-starlight-1200tvl-low-latency-fpv-camera.html
- [Q90] https://www.getfpv.com/foxeer-razer-micro-1200tvl-1-8mm-fpv-camera.html
- [Q91] https://oscarliang.com/gopro-10-bones/ (Action-Cam) und https://oscarliang.com/aio-fpv-camera-vtx-combo/ (Whoop-Kombis)
- [Q92] https://community.gopro.com/s/article/HERO11-Black-Camera-Size-And-Weight?language=en_US
- [Q93] https://blog.uavmodel.com/fpv-drone-gps-module-comparison-bn-880-vs-m10-vs-hglrc/ ,
  https://www.racedayquads.com/products/rdq-bn-880-flight-control-gps-module-dual-module-compass-with-cable
- [Q94] https://blog.quadmeup.com/how-much-power-flight-controller-consumes/
- [Q95] https://cdn.sparkfun.com/assets/e/6/1/f/4/WS2812B-LED-datasheet.pdf , https://www.eevblog.com/forum/projects/ws2812b-leds-power-consumption/
- [Q100] https://oscarliang.com/betafpv-air65/
- [Q101] https://oscarliang.com/betafpv-meteor75-pro-whoop-analog/
- [Q102] https://oscarliang.com/t-motor-feather120/
- [Q103] https://oscarliang.com/betafpv-hx115-se/
- [Q104] https://oscarliang.com/holybro-kopis-mini/
- [Q105] https://oscarliang.com/deepspace-seeker3/
- [Q106] https://oscarliang.com/betafpv-pavo35/ , https://betafpv.com/products/pavo35-brushless-whoop-quadcopter
- [Q107] https://www.ligpower.com/blog/best-fpv-freestyle-drone-motors-2025.html , https://chinahobbyline.com/blogs/news/best-battery-for-5-inch-quad ,
  https://mattyfleisch.com/fpv-lipo-batteries/
- [Q108] https://oscarliang.com/geprc-moz7-v2/
- [Q109] https://blog.uavmodel.com/fpv-long-range-drone-build-li-ion-battery-gps-endurance-and-efficiency-optimization-2026-guide/ ,
  https://iflight-rc.eu/en/products/fullsend-6s-4000mah-li-ion-battery
- [Q110] https://www.tattuworld.com/resources/what-is-an-x-class-fpv-drone.html
- [Q111] https://shop.xclass.racing/pages/rules-standards (Klasse 800–1200 mm Motorabstand diagonal)
- [Q112] https://rotorbuilds.com/build/22654
- [Q113] https://blog.ampow.com/fpv-x-class-racing/ , https://shop.iflight.com/xing-x4214-2-8s-x-class-fpv-nextgen-motor-pro802
- [Q114] https://intofpv.com/t-small-65-75mm-digital-whoops-managing-flight-times
- [Q115] https://www.grepow.com/semi-solid-state-battery/tattu-g-tech-6s-17000mah-22-2v-5c-lipo-drone-nmc-battery.html ,
  https://www.grepow.com/semi-solid-state-battery/tattu-g-tech-6s-30000mah-22-2v-5c-lipo-drone-nmc-battery.html
- [Q116] https://www.foxtechfpv.com/foxtech-diamond-6s-22000mah-semi-solid-state-li-ion-battery.html ,
  https://www.foxtechfpv.com/diamond-hv-series-semi-solid-state-li-ion-battery-6s-16000-mah-44000-mah.html
- [Q117] https://www.herewinpower.com/solution/drones/
Q118–Q130 (Abschnitt 4.4): Zugriff am 02.10.2026.

- [Q118] https://www.foxeer.com/1200tvl-foxeer-micro-razer-fpv-camera-pal-ntsc-switchable-1-8mm-lens-4ms-latency-g-265
  (FOV-H 125°, FOV-D 155° in 4:3 und 145° in 16:9, 1,8 mm, 4,5 g, 19 mm)
- [Q119] https://www.foxeer.com/foxeer-razer-nano-1200tvl-1-8mm-lens-low-latency-fpv-camera-g-273 (FOV-H 125°, FOV-D 150°/140°,
  3,9 g, 14 mm)
- [Q120] https://shop.runcam.com/runcam-racer-nano-4/ (FOV D 160° H 125° V 97°, 4,5 g, 14 mm)
- [Q121] https://www.foxeer.com/foxeer-razer-mini-v3-fpv-camera-g-593 (FOV-H 98°, 4:3, 12 g)
- [Q122] https://docs.hd-zero.com/camera-summary (Nano 90 V2 D 162° H 132° V 101°, Micro V3 D 157° H 133° V 72° 16:9, Nano V3
  D 155° H 126° V 94°, ECO D 150° H 120°, Lux D 155°/H 126° 4:3 und D 170°/H 145° 16:9)
- [Q123] https://docs.hd-zero.com/camera-micro-v3 (D 157° H 133° V 72° bei 16:9, 7,5 g)
- [Q124] https://www.dji.com/o4-air-unit/specs (O4: Sichtfeld 117,6°, 14 mm KB-Brennweite, 8,2 g; O4 Pro: 155°, 12 mm, 32 g)
- [Q125] https://www.caddxfpv.com/products/walksnail-avatar-hd-pro-camera (Sichtfeld 160°, 9,5 g; über die Suchmaschine gelesen)
- [Q126] https://www.caddxfpv.com/products/walksnail-avatar-hd-mini-1s-lite-kit (Sichtfeld 170°, 2,1 mm; über die Suchmaschine gelesen)
- [Q127] https://www.tinywhoop.com/products/betafpv-c03-camera (2,1 mm, 160°, 1,48 g; über die Suchmaschine gelesen)
- [Q128] https://www.caddxfpv.com/products/caddx-ant-analog-camera (1,8 mm, 165°, 2 g; über die Suchmaschine gelesen)
- [Q129] https://oscarliang.com/fpv-camera-lens/ (Sichtfeld meist diagonal; 1/3″ 4:3: 1,6 mm 170°+, 1,8 mm 160–170°, 2,1 mm
  150–160°, 2,3 mm 140–150°, 2,5 mm 130–140°)
- [Q130] https://www.dji.com/o3-air-unit/specs, https://oscarliang.com/dji-o3-air-unit-fpv-goggles-2/ (Sichtfeld 155°)

- [Q131] https://github.com/betaflight/betaflight/blob/master/src/main/sensors/battery.c (`batteryUpdateVoltageState`,
  Standardwerte `vbatwarningcellvoltage` 350, `vbathysteresis` 1), Zugriff am 02.10.2026

Q132–Q137 (Abschnitt 4.5): Zugriff am 03.10.2026.

- [Q132] ITU-R P.526-16 (11/2025), Propagation by diffraction: §2.1 Fresnel-Ellipsoide, §2.7 Fresnel-Integrale
  (Boersma-Koeffizienten), §4.1 Messerkante Gl. 26–31, §5.1 Schirm endlicher Breite Gl. 71–72, §5.2 Rechteck-Blenden
  und zusammengesetzte Schirme – https://www.itu.int/dms_pubrec/itu-r/rec/p/R-REC-P.526-16-202511-I!!PDF-E.pdf
- [Q133] ITU-R P.833-10 (09/2021), Attenuation in vegetation: §2.1 Wald mit Sättigung A_m = A₁ · f^α (Mulhouse
  A₁ 1,15 dB, α 0,43; St. Petersburg A₁ 1,37 dB, α 0,42 → 47,8 bzw. 52,2 dB bei 5,8 GHz), §3.1 Begrenzung durch den Weg
  außen herum, §3.2.1.5 Gl. 16, Tabellen 5–8 (RET-Parameter bis 12,5 GHz) –
  https://www.itu.int/dms_pubrec/itu-r/rec/p/R-REC-P.833-10-202109-I!!PDF-E.pdf
- [Q134] ITU-R P.833-4 (04/2003), §3.2 „Above 5 GHz“: A = R∞ · d + k · (1 − exp(−(R₀ − R∞) · d/k)), Tabelle 1 in Laub
  a 0,2, b 1,27, c 0,63, k₀ 6,57, R_f 0,0002, A₀ 10 – https://www.itu.int/dms_pubrec/itu-r/rec/p/R-REC-P.833-4-200304-S!!PDF-E.pdf
- [Q135] ITU-R P.2040-3 (08/2023), Effects of building materials and structures: Tabelle 3 (Beton, Ziegel,
  Gipskarton, Holz, Glas, Deckenplatte, Spanplatte, Sperrholz, Marmor, Metall, drei Bodenarten), Gl. 26–27 –
  https://www.itu.int/dms_pubrec/itu-r/rec/p/R-REC-P.2040-3-202308-S!!PDF-E.pdf
- [Q136] ITU-R P.527-6 (09/2021), Electrical characteristics of the surface of the Earth: §5.1.1 Süßwasser (Debye-Modell
  Gl. 5–13), §5.1.3.1 Eis (Gl. 28–34), §5.1.4.1 trockener Schnee (Gl. 51–53) –
  https://www.itu.int/dms_pubrec/itu-r/rec/p/R-REC-P.527-6-202109-I!!PDF-E.pdf
- [Q137] G. Durgin, T. S. Rappaport, H. Xu: Measurements and Models for Radio Path Loss and Penetration Loss In and Around
  Homes and Trees at 5.85 GHz, IEEE Transactions on Communications 46 (11), 1998, S. 1484–1496 –
  https://propagation.ece.gatech.edu/ECE3065/tutorials/Project05/TransCom98.pdf
