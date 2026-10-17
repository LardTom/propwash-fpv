# Realismus-Referenz: komplette Drohnen nach Klasse (Rechercheur „builds“)

Stand: 2026-09-30. Zweck: belastbare Vergleichswerte aus der echten Welt für die Kalibrierung der Bauteildaten und der
Analyse-/Physikkonstanten (Werkbank, Web-Konfigurator, `BuildAnalysis`). Hier stehen **ganze Drohnen**: Gewicht,
Schub/Gewicht, Schwebegas, Flugzeiten (Schweben, Reisen, gemischtes Fliegen, aggressiv/Rennen), Höchstgeschwindigkeit,
Ströme und Temperaturen je Größenklasse, jeweils mit Bedingungen, Quelle und Unsicherheit. Einzelteil-Datenblätter
(Motoren, Props, Akkus) sind nur so weit aufgenommen, wie sie Ganzdrohnen-Werte stützen.

Es wurde kein Code geändert. Alle abgeleiteten Zahlen sind als solche markiert; die Rechenwege stehen in Abschnitt 1.

---

## 0. Kurzfassung – Richtwerte je Klasse

Flugzeiten bis zur üblichen Landespannung unter Last (Abschnitt 12), analoges Video, ohne Actioncam, sofern nicht
anders angegeben. „Gemischt“ = typisches Fliegen dieser Klasse (Freestyle mit Pausen, Indoor-Runden, Cinematic mit
Positionswechseln). Spannen spiegeln die Streuung zwischen Quellen, Flugstilen und Aufbauten.

| Klasse (typischer Aufbau) | Abfluggewicht | S/G | Schwebegas | Schweben | Reisen | Gemischt | Aggressiv / Rennen | Höchstgeschw. | Vollgasstrom (in der Luft) |
|---|---|---|---|---|---|---|---|---|---|
| 65 mm 1S Whoop (0702, 31 mm, 1S 300 mAh LiHV) | 24–30 g | 4,5–6,5 : 1 | 30–35 % | 4:00–5:00 min (digital 3:00–3:20) | 3:00–3:30 (bis 4:00 ultraleicht) | 2:00–2:45 | 1:30–2:00 | 35–55 km/h (Schätzung) | 12–16 A |
| 75 mm 1S Whoop (0802/1102, 40–45 mm, 1S 450–550 mAh) | 33–46 g | 4–6 : 1 (Schätzung) | 31–33 % | 6–8 min (abgeleitet) | 5–7 min | 4–5:30 min | 3–4 min | 45–65 km/h (Schätzung) | 15–20 A (Schätzung) |
| 2,5–3″ Toothpick 3S/4S (1103–1204, 450–650 mAh) | 80–140 g | 6–9 : 1 (Schätzung) | 20–30 % (Schätzung) | 8–12 min (abgeleitet) | 6–8 min (650 mAh), 10–11 min Extremtest | 3–5 min (450), 5–7 min (650) | 3 min (450), 5 min (650) | 90–120 km/h (Schätzung) | 30–60 A (Schätzung) |
| 3,5″ Toothpick 4S (1404, 4S 750) | ≈ 220 g | – | – | – | 11–13 min (Herstellerangabe „smooth“) | 4–6 min | ≈ 4 min | 100–130 km/h (Schätzung) | – |
| 3″ Cinewhoop 4S (1404, 4S 550–1500) | 250–360 g | 4–6 : 1 (Schätzung) | 23 % (850) – 33 % (1500) | – | 4–5 min (550), 7–7:30 (850), 11 min (1500) | – | – | 80–100 km/h (Schätzung) | – |
| 3,5″ Cinewhoop 6S (2006/2105.5, 6S 1100–1550) | 460–660 g, mit Actioncam 600–820 g | 5–7 : 1 (Schätzung) | ≈ 28 % | – | 6–10 min ohne Cam | 4–7 min mit Actioncam | ≈ 4 min mit Actioncam | 100–150 km/h | 60–100 A (Schätzung) |
| 5″ Freestyle 6S 1100–1300 (2207 1750–1960 KV) | 600–700 g (mit Actioncam 780–870 g) | 8–12 : 1 | 19–28 % | 5:30–8:00 (abgeleitet) | 4:30–6:30 | 3:30–5:00 | 2:30–3:30 | 140–170 km/h | 120–180 A (Spitze) |
| 5″ Race 6S 1300–1400 (2207 1950–2050 KV) | 480–560 g (Spec-Klasse ≥ 533 g) | 12–16 : 1 (Schätzung) | 15–22 % (Schätzung) | ≈ 10–12 min (Herstellerangabe) | – | ≈ 3 min (Durchschnittspilot) | < 2 bis 3 min (Heat) | 160–200 km/h | 140–190 A (Spitze, Schätzung) |
| 7″ LR 6S LiPo 1800–2200 | 780–1220 g | 6–10 : 1 | 25–30 % | 10–17 min (abgeleitet) | 6–11 min | 4–7 min | 3–5 min | 130–160 km/h | 100–160 A |
| 7″ LR 6S Li-Ion 3600–8000 | 780–1650 g | 4–7 : 1 | 26–30 % | 30 min (8000, Herstellerangabe „no load“) | 14–19 min (3600–4000), 16–22 min (6000), 18–24 min (8000) | – (Li-Ion nicht für harte Manöver) | – | ≈ 110 km/h (Sag begrenzt) | 30–100 A je nach Pack (Zellgrenze) |
| 7″ auf 8S (2807/2808 ≈ 1000–1150 KV, 8S 1800–2200) | 850–1100 g | 9–13 : 1 | 20–28 % | 15–23 min (abgeleitet) | 9–13 min (abgeleitet) | 6–9 min (abgeleitet) | 4–6 min (abgeleitet) | 140–170 km/h (Schätzung) | 90–150 A |
| X-Class 10–13″ 8S (4214-Klasse, 8S 5000) | 3,2–4,5 kg | 5–8 : 1 | 25–35 % (Schätzung) | – | – | ≈ 5 min | 2–4 min (Schätzung) | 150–190 km/h | 150–250 A (12S, Herstellerangabe) |

---

## 1. Methode, Begriffe, Umrechnungen

- **Abfluggewicht (AUW)**: flugfertig mit Akku, ohne Actioncam, sofern nicht anders angegeben. Trockengewicht = ohne Akku.
- **Schwebegas**: Betaflight-Gaswert in %, den die Piloten im OSD ablesen. Er ist **nicht** identisch mit dem „Throttle“
  eines Motorprüfstands (Leerlauf-Offset, Gaskurve, Throttle-Limit), taugt aber als Tendenzwert.
- **Nutzbare Energie**: `E = Zellen × U_nenn × C × f`. U_nenn = 3,7 V (LiPo), 3,8 V (LiHV), 3,6 V (Li-Ion).
  f = Anteil der Kapazität bis zur üblichen Landespannung unter Last: 0,80 (LiPo, Landung ≈ 3,5 V/Zelle unter Last),
  0,85 (1S-LiHV, Landung 3,3–3,5 V unter Last), 0,88 (Li-Ion, Landung 2,8–3,1 V unter Last).
- **Implizite mittlere Leistung** `P = E / t` und **mittlerer Strom** `I = C × f / t`; **spezifische Leistung** `P / AUW`
  in W/g (Kehrwert: „Systemwirkungsgrad“ in g/W, inklusive Regler, Motor, Prop und Avionik).
- **Unsicherheit**: Herstellerangaben zur Flugzeit sind meist Schwebe- oder „no load“-Werte und liegen 20–50 % über
  echten Flügen. Tests auf Review-Seiten (vor allem oscarliang.com) sind gemessen, aber Einzelflüge mit persönlichem
  Stil (±15–25 %). Forenwerte sind Einzelfälle. Jede Tabelle nennt die Art der Quelle.
- Werte mit „(Schätzung)“ haben keine direkte Quelle und sind aus Physik oder Nachbarklassen abgeleitet.

---

## 2. 65 mm 1S Whoops

### 2.1 Gemessene und angegebene Werte

| Drohne | Motor / Prop | Trocken / AUW | Akku | Flugzeit | Bedingung | Quelle (Art) |
|---|---|---|---|---|---|---|
| BetaFPV Air65 Racing | 0702SE 27000 KV, Gemfan 1219S-3 (3-Blatt) | 17,25 g / ≈ 25,6 g | 1S 300 mAh LiHV (8,3 g) | ≈ 4:00 | „aggressiv“ 50–60 % Gas, 96 kHz PWM, Indoor | [OL Air65](https://oscarliang.com/betafpv-air65/) (Review) |
| BetaFPV Air65 Freestyle | 0702SE 23000 KV, HQ 31 mm Ultralight | 17,25 g / ≈ 25,6 g | 1S 300 mAh LiHV | ≈ 4:30 | wie oben | [OL Air65](https://oscarliang.com/betafpv-air65/) (Review) |
| BetaFPV Air65 Racing | 0702SE 27000 KV | 17,25 g | 1S 300 mAh LiHV | 2:00–2:45 | normaler Flug | [prowhooper](https://prowhooper.com/ultimate-beginner-whoop-guide/) (Guide) |
| BetaFPV Air65 Freestyle | 0702SE 23000 KV | 17,25 g | 1S 300 mAh LiHV | 2:30–3:30 | normaler Flug | [prowhooper](https://prowhooper.com/ultimate-beginner-whoop-guide/) (Guide) |
| Happymodel Mobula6 2024 | SE0702 28000 KV | 17,8 g / ≈ 26,1 g | 1S 300 mAh LiHV | ≈ 3:30; 3:10 mit 260 mAh | Indoor | [OL Mobula6 2024](https://oscarliang.com/happymodel-mobula6-2024/) (Review) |
| Happymodel Mobula6 2024 | SE0702 28000 KV | 17,8 g | 1S 300 mAh | 2:00–2:30 | „indoor flying“ | [Happymodel](https://www.happymodel.cn/index.php/2024/02/24/mobula6-2024-1s-65mm-ultra-light-micro-fpv-whoop-bnf/) (Hersteller) |
| Emax Tinyhawk Aero Freestyle | 0702 27000 KV, HQ 31 mm 3-Blatt 1.2×1.1×3 | 16,31 g / ≈ 25,2 g | 1S 320 mAh HV (8,85 g) | 2:38 indoor, 2:36 outdoor | aktiv, VTX 25 mW | [unmanned.tech Review](https://blog.unmanned.tech/emax-tinyhawk-aero-1s-whoop-review/) (Review) |
| Emax Tinyhawk Aero Freestyle | wie oben | wie oben | wie oben | 1:52 (sanft), 1:40–1:45 (hart, geschätzt) | VTX 400 mW | [unmanned.tech Review](https://blog.unmanned.tech/emax-tinyhawk-aero-1s-whoop-review/) (Review) |
| Emax Tinyhawk Aero (alle Versionen) | 27000 / 30000 / 35000 KV | 16,3–16,9 g analog, 18,8–19,3 g HDZero | 1S 320 mAh HV | Schweben analog 5:03 / 5:07 / 4:48, HDZero 3:17 / 3:10 / 2:54 | kontrollierter Schwebetest, voll geladen | [Emax](https://shop.emax-usa.com/products/emax-tinyhawk-aero-65mm-1s-fpv-racing-whoop-bnf-elrs) (Hersteller) |
| BetaFPV Air65 II Freestyle / Racing / Champion | 0702 25000 / 30000 / 36000 KV | 17,8 / 17,7 / 16,6 g | 1S 320 mAh | 5:00 / 4:30 / 4:40 | Herstellerangabe | [BetaFPV Air65 II](https://betafpv.com/products/air65-ii-brushless-whoop-quadcopter) (Hersteller) |
| BetaFPV Air65 II | wie oben | wie oben | 1S 320 / 280 mAh | ≈ 4:00 / 3:00 | Review | [OL Air65 II](https://oscarliang.com/betafpv-air65-ii/) (Review) |
| 65 mm allgemein | – | – | 1S 300 mAh | „2 bis 3 Minuten, je nachdem wie hart“ | Einsteiger-Guide | [prowhooper](https://prowhooper.com/ultimate-beginner-whoop-guide/) |
| 65–75 mm allgemein | – | – | 1S | 2–5 min | Händler-Wissensseite | [Unmanned Tech Flugzeit](https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-drone-flight-time-how-to-get-more-minutes-in-the-air) |

### 2.2 Schub, Schwebegas, Ströme

| Größe | Wert | Quelle |
|---|---|---|
| Schwebegas Mobula6 2024, 300 mAh voll | ≈ 30 % | [OL Mobula6 2024](https://oscarliang.com/happymodel-mobula6-2024/) |
| Schwebegas Air65, 300 mAh voll | 35 % Freestyle (23000 KV), 30 % Racing (27000 KV) | [OL Air65](https://oscarliang.com/betafpv-air65/) |
| Max. Schub 0702SE II 23000 / 27000 KV, 0702 II 30000 KV (GF 1219S-3, 1S) | 32 / 35,4 / 37,2 g je Motor | [BetaFPV 0702 II](https://betafpv.com/products/0702-ii-brushless-motors) (Hersteller) |
| Max. Schub gesamt Tinyhawk Aero 27000 / 30000 / 35000 KV | 142 / 147,6 / 174 g → S/G 5,6 / 5,9 / 6,7 : 1 bei ≈ 25 g | [Emax](https://shop.emax-usa.com/products/emax-tinyhawk-aero-65mm-1s-fpv-racing-whoop-bnf-elrs) |
| S/G Air65 II Champion | 6,3 : 1 | [BetaFPV Air65 II](https://betafpv.com/products/air65-ii-brushless-whoop-quadcopter) |
| Motorgewicht 0702 / 0702SE | 1,45–1,55 g / 1,35–1,47 g | [BetaFPV 0702](https://betafpv.com/products/0702-brushless-motors) |
| ESC-Dauerstrom der AIOs | 5–6 A je Motor (Mobula6, Air65, Meteor65), Air65 II 12 A | Herstellerseiten wie oben |
| Stromaufnahme Meteor65 Pro 2022 (3-Blatt, Vollgas) | „11-ish amps“ | Suchauszug intofpv (Seite 403) – niedrige Sicherheit |
| Akku 1S 300 mAh LiHV | 8,2–8,3 g, 1,14 Wh | [BetaFPV LAVA 300](https://betafpv.com/products/lava-1s-300mah-75c-battery-5pcs), [OL Micro-LiPo](https://oscarliang.com/whoop-toothpick-lipo-battery/) |

### 2.3 Tendenzen KV und Blattzahl

- **KV**: Air65 Freestyle (23000 KV) fliegt auf 300 mAh „im Schnitt 30 bis 40 Sekunden länger“ als Racing (27000 KV)
  ([OL Air65](https://oscarliang.com/betafpv-air65/)); prowhooper: 2:30–3:30 gegen 2:00–2:45. Also **+15–25 %
  Flugzeit** für 23000 gegenüber 27000 KV beim selben Stil; 27000 KV zieht im Mittel entsprechend mehr Strom.
  Hohe KV (30000–36000) „verbrennen Akkus schneller“ ([prowhooper](https://prowhooper.com/ultimate-beginner-whoop-guide/)).
- **Blattzahl**: 31-mm-Props auf dem Prüfstand (0802 20000 KV, Vollgas): HQ 31×3 17,7 g bei 1,70 g/W, HQ 31×4 18,0 g
  bei 1,67 g/W; Gemfan 1219×3 20,0 g bei 1,78 g/W, Gemfan 1220×4 19,5 g bei 1,70 g/W
  ([OL Prop-Shootout](https://oscarliang.com/tiny-whoop-prop/)) → 4-Blatt bei Vollgas nur 2–5 % schlechter.
  Beim gleichen Gas ziehen Vierblatt-Props 20–30 % mehr Strom als Zweiblatt, Dreiblatt verliert 10–15 % Wirkungsgrad
  gegen Zweiblatt ([Unmanned Tech Props](https://www.unmannedtechshop.co.uk/blogs/knowledge-base/propeller-guide-tri-blade-vs-bi-blade-vs-quad-blade-fpv));
  Forenbericht: 0702 mit Zweiblatt 20 % weniger Strom als Dreiblatt (Suchauszug intofpv, niedrige Sicherheit).
  → Leichtere 2-/3-Blatt-Props bringen auf 300 mAh **+10–20 % bzw. etwa +20–30 s**.
- **Video**: HDZero statt analog kostet beim Schweben ≈ 35 % Flugzeit (5:03 → 3:17, +2,5 g und ≈ 5 W VTX), 400 mW
  statt 25 mW analog ≈ 30 % (2:38 → 1:52), siehe Abschnitt 13.

### 2.4 Abgeleitete Leistung und Ströme (Abschnitt-1-Methode, f = 0,85)

| Datenpunkt | AUW | t | P | W/g | g/W | I mittel |
|---|---|---|---|---|---|---|
| Tinyhawk Aero, Schweben analog | 25,2 g | 5:07 | 12 W | 0,48 | 2,1 | 3,2 A |
| Tinyhawk Aero, Schweben HDZero | 27,8 g | 3:17 | 19 W | 0,68 | 1,5 | 5,0 A |
| Air65 Freestyle / Racing (oscarliang.com) | 25,6 g | 4:30 / 4:00 | 13 / 15 W | 0,50 / 0,57 | 2,0 / 1,8 | 3,4 / 3,8 A |
| Mobula6 2024 (oscarliang.com) | 26,1 g | 3:30 | 17 W | 0,64 | 1,6 | 4,4 A |
| Air65 Freestyle / Racing (prowhooper, Mitte) | 25,6 g | 3:00 / 2:23 | 19 / 24 W | 0,76 / 0,96 | 1,3 / 1,0 | 5,1 / 6,4 A |
| Tinyhawk Aero aktiv 25 mW | 25,3 g | 2:38 | 24 W | 0,93 | 1,1 | 6,2 A |
| Tinyhawk Aero 400 mW sanft / hart | 25,3 g | 1:52 / 1:42 | 33 / 36 W | 1,31 / 1,44 | 0,8 / 0,7 | 8,7 / 9,6 A |

Folgerung: Ein 65-mm-Whoop braucht **schon im Schweben ≈ 0,45–0,5 W/g** (Systemwirkungsgrad ≈ 2 g/W inklusive
Avionik), typisches Fliegen liegt beim **1,8- bis 2-fachen**, aggressives beim **2,5- bis 3-fachen** der Schwebeleistung.

---

## 3. 75 mm 1S Whoops

| Drohne | Motor / Prop | Trocken / AUW | Akku | Flugzeit | Schwebegas | Quelle |
|---|---|---|---|---|---|---|
| BetaFPV Air75 | 0802SE 23000 KV, Gemfan 40 mm 2-Blatt | 21 g / 33,5 g (450) / 35,2 g (550) | 1S 450 / 550 mAh | 5:30–6:00 (450), knapp 7:00 (550) | 31 % (450), 33 % (550) | [OL Air75](https://oscarliang.com/betafpv-air75/) (Review) |
| Happymodel Mobula7 1S | 0802 20000 KV | 24,1 g / 36,9 g | 1S 450 mAh | ≈ 4:30 (Vergleich im Air75-Review), „4–5 min“ | – | [OL Air75](https://oscarliang.com/betafpv-air75/), [OL Mobula7 1S](https://oscarliang.com/mobula7-1s-whoop/) |
| BetaFPV Meteor75 Pro | 1102 22000 KV, Gemfan 45 mm 3-Blatt | 31,1 g / 45,3 g | 1S 550 mAh | ≈ 4:00 draußen gemischt, ≈ 5:00 indoor zurückhaltend; ≈ 3:30 mit 450 mAh | – | [OL Meteor75 Pro](https://oscarliang.com/betafpv-meteor75-pro-whoop-analog/) (Review) |
| 75 mm allgemein | – | – | 1S 450 mAh | 2:30–4:00 | – | [prowhooper](https://prowhooper.com/ultimate-beginner-whoop-guide/) |

Abgeleitet: Air75 ≈ 15 W (0,45 W/g, 4 A), Mobula7 ≈ 19 W (0,53 W/g, 5 A), Meteor75 Pro 21 W indoor / 27 W draußen
(0,47 / 0,59 W/g, 5,6 / 7 A). Größere Props (40–45 mm) sind pro Gramm deutlich effizienter als 31 mm.

---

## 4. Toothpicks 2,5–3,5″ (3S/4S)

| Drohne / Aufbau | Motor / Prop | Trocken / AUW | Akku | Flugzeit | Bedingung | Quelle |
|---|---|---|---|---|---|---|
| Richtwerte 2,5″ | 1102–1104, 65 mm | < 60 g trocken; AUW < 80 g (2S), < 90 g (3S) | 3S 300–450 mAh | – | Leitfaden | [OL 2,5″ Toothpick](https://oscarliang.com/ultralight-micro-quad-toothpick/) |
| Lookup-Tabelle 2,5″ Zweiblatt 3S / 4S | 1203–1303 6000–8000 KV / 1303–1404 4500–6000 KV | 45–65 g / 55–90 g trocken | 3S 450–550 / 4S 450–650 | – | Leitfaden | [OL Lookup](https://oscarliang.com/table-prop-motor-lipo-weight/) |
| GEPRC Phantom (2,5″) | 1103 8000 KV | 55 g / ≈ 80 g | 3S 300 mAh | – | Review | [OL Phantom](https://oscarliang.com/geprc-phantom-toothpick/) |
| GEPRC SMART HD (2,5″) | 1105 5000 KV, G2523 | 92 g | 3S/4S 450 mAh (max. 4S 450) | – | Hersteller | [GEPRC SMART HD](https://geprc.com/product/geprc-smart-hd-toothpick-fpv/) |
| BetaFPV HX115 HD (3″) | 1105 5000 KV, HQ 3020×2 / 3030×3 | 74 g / ≈ 110 g | 3S 450 mAh | ≈ 3:00 | Review, normaler Flug | [OL HX115 HD](https://oscarliang.com/betafpv-hx115-hd-micro-quad/) |
| 3″ 3S, Hyperlite UL3 | 1204 5000 KV | 79,8 g / 131,4 g | 3S 650 mAh (51,6 g) | > 7:00 gemischt mit Cruisen, ≈ 5:00 hart | Baubericht | [RotorBuilds 30794](https://rotorbuilds.com/build/30794) |
| „Sanchez“ 3″ 4S | 1404 4300 KV, 3025×2 | ≈ 140 g AUW (mit 650) | 4S 650 mAh | > 7:00 bis 3,7 V Ruhe; 12+ min mit 850 mAh | Baubericht, sanft | [RotorBuilds 18513](https://rotorbuilds.com/build/18513) |
| Flexrc Simple X 120 (3″) | 1404 3600 KV, T3×3×3 | – | 4S 520 mAh | ≥ 11 min | Maximal-Test, Cruisen | [RotorBuilds 19798](https://rotorbuilds.com/build/19798) |
| BetaFPV X-Knight 35 (3,5″) | 1404 3800 KV | 142,9 g / ≈ 225 g | 4S 750 mAh | 11–13 min ruhig, 4–6 min Freestyle | Hersteller | Suchauszug BetaFPV / [BetaFPV X-Knight 35](https://betafpv.com/products/x-knight-35-fpv-quadcopter) |
| Happymodel Crux35 (3,5″) | EX1404 3500 KV | 87 g analog / 108 g HD | 4S 750 mAh | „9–15 min“ | Hersteller, sehr optimistisch | [Happymodel Crux35](https://www.happymodel.cn/index.php/2021/08/30/happymodel-crux35-crux35-digital-hd-3-5-inch-4s-micro-freestyle-fpv-racing-drone/) |
| BetaFPV HX115 SE (3″ 1S, Vergleich) | – | 39,1 g / 51,4 g | 1S 450 mAh | Schweben 5:30, „Vollgas-Fliegen“ < 3:00 | Review | [OL HX115 SE](https://oscarliang.com/betafpv-hx115-se/) |
| 3″/Toothpick allgemein | – | – | – | 4–8 min | Händler-Wissensseite | [Unmanned Tech Flugzeit](https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-drone-flight-time-how-to-get-more-minutes-in-the-air) |

Abgeleitet (f = 0,80): HX115 HD 3S 450 ≈ 80 W (0,73 W/g, 7,2 A); 3″ 3S 650 gemischt 49 W (0,38 W/g), hart 69 W
(0,53 W/g); X-Knight 35 Freestyle 107 W (0,47 W/g), ruhig 44 W (0,20 W/g, Herstellerwert).
Akkugewichte: 3S 450 ≈ 35–38 g (Schätzung), 3S 580 41,8 g, 3S 750 50,3 g, 4S 580 55 g, 4S 650 ≈ 71 g, 4S 680 63,6 g
([OL Micro-LiPo](https://oscarliang.com/whoop-toothpick-lipo-battery/), [RotorBuilds 19798](https://rotorbuilds.com/build/19798)).

---

## 5. Cinewhoops (2,5–3,5″, 4S/6S)

| Drohne | Motor / Prop | Trocken / AUW | Akku | Flugzeit | Schwebegas | Speed | Quelle |
|---|---|---|---|---|---|---|---|
| BetaFPV Pavo25 (2,5″ 4S) | 1404 4500 KV, D63-3 | 155 g / 236 g / 272 g mit GoPro 6 | 4S 750 mAh | ≈ 4:30 aggressiv ohne Cam; ≈ 3:40 mit Hero10 Bones (cruisen) | – | – | [OL Pavo25](https://oscarliang.com/betafpv-pavo25-whoop/) (Review; Hersteller 7,5–8,5 min) |
| GEPRC Cinelog30 V3 (3″ 4S) | 1404 3850 KV, DT76×3 | 192 g / 249 g (550) / 286 g (850) | 4S 550 / 850 / 1500 | 4–5 min / 7:00–7:30 / 11 min | 23 % (850), 33 % (1500) | – | [OL Cinelog30 V3](https://oscarliang.com/geprc-cinelog30-v3/) (Review) |
| DarwinFPV CineApe35 (3,5″) | 2006 2030 KV (6S) / 3400 KV (4S), D90S-3 | 278 g | 6S 1100 | ≈ 10 min ohne Cam, 6–7 min mit GoPro Hero 11 | ≈ 28 % | 110 km/h | [OL CineApe35](https://oscarliang.com/darwinfpv-cineape35/) (Review) |
| DarwinFPV CineApe35 4S | 2006 3400 KV | – | 4S 1500 | 7 min ohne Last, 5,5 min mit GoPro 8 | – | – | Suchauszug Herstellerangabe |
| BetaFPV Pavo35 (3,5″ 6S) | 2006 2400 KV, D90S-3 | 238 g / 465 g / 620 g mit Hero 11 | 6S 1100 | ≈ 4 min mit GoPro (Hersteller 10–12 min) | ≈ 28 % mit GoPro | – | [OL Pavo35](https://oscarliang.com/betafpv-pavo35/) (Review) |
| GEPRC Cinelog35 V2 (3,5″ 6S) | 2105.5 2150 KV, DT90 | 309 g | 6S 1300 | bis ≈ 6 min mit GoPro Hero 11 | – | – | [OL Cinelog35 V2](https://oscarliang.com/geprc-cinelog35-v2/) (Review) |
| GEPRC Cinebot35 (3,5″ 6S) | 2105.5 2450 KV, T90×3 | 391 g / 583 g (1100) / 659 g (1550) / 814 g mit Hero 11 | 6S 1100–1550 | 5–8 min langsames Cruisen, knapp 10 min mit 1550 sanft | – | 140–150 km/h | [OL Cinebot35](https://oscarliang.com/geprc-cinebot35/) (Review) |
| Axisflying Cineon C35 V3 | – | – | – | – | – | 28 m/s (101 km/h) | Suchauszug Hersteller |
| BetaFPV Pavo20 Pro II 4S (2″) | – | – | 4S | – | – | ≈ 100 km/h (Schätzung des Reviews) | [OL Pavo20 Pro II](https://oscarliang.com/betafpv-pavo20-pro-ii-4s/) |
| Richtwerte 3″ / 3,5″ Cinewhoop | 1404/2004 bzw. 2004–2106 | 180–300 g / 250–350 g trocken | 4S 850–1300 / 6S 1000–1300 | – | – | – | [OL Lookup](https://oscarliang.com/table-prop-motor-lipo-weight/) |
| Hover-Fenster Cinewhoop | – | – | – | – | 25–35 % üblich | – | Suchauszug zu [OL Cinewhoop](https://oscarliang.com/cinewhoop/) |

Abgeleitet (f = 0,80): Cinelog30 850 / 1500 ≈ 83 / 97 W (0,29 / 0,27 W/g); CineApe35 ohne Cam ≈ 117 W (0,25 W/g);
Cinebot35 1550 sanft ≈ 165 W (0,25 W/g); Pavo35 mit GoPro ≈ 293 W (0,47 W/g, 13 A); Pavo25 aggressiv ≈ 118 W (0,50 W/g).
Cinematic-Fliegen liegt bei ≈ 0,25–0,3 W/g, Fliegen mit Actioncam und Freestyle-Anteil bei ≈ 0,45–0,5 W/g.

---

## 6. 5″ Freestyle 6S (1100–1300 mAh)

| Drohne / Aufbau | Motor / Prop | Trocken / AUW | Akku | Flugzeit | Schwebegas | Speed | Quelle |
|---|---|---|---|---|---|---|---|
| GEPRC Vapor D5 | 2207E 1960 KV, Gemfan 5136 | 488,4 g / 698 g / 865 g mit Hero 13 | 6S 1100 LiPo | ≈ 3 min aggressiv, ≈ 4 min „easier throttle“ | ≈ 19 % | ≈ 165 km/h | [OL Vapor D5](https://oscarliang.com/geprc-vapor-d5-o4/) (Review) |
| GEPRC Vapor D5 | wie oben | – | 6S 4000 Li-Ion | 12–13 min | – | – | [OL Vapor D5](https://oscarliang.com/geprc-vapor-d5-o4/) |
| GEPRC Vapor D5 | – | Motoren „kaum warm“ nach 3 min aggressivem Freestyle | – | – | – | – | [OL Vapor D5](https://oscarliang.com/geprc-vapor-d5-o4/) |
| iFlight Nazgul Evoque F5 | 2207 1750 KV | 421 g / 780 g mit 6S 1100 und GoPro 7 | 6S 1100 | – | – | – | [OL Evoque F5](https://oscarliang.com/iflight-nazgul-evoque-f5/) |
| 5″-BNF-Vergleich 2025 (7 Modelle) | 2207 1750–2050 KV | 391–490 g trocken | – | – | – | – | [OL BNF-Vergleich](https://oscarliang.com/5inch-bnf-dji-o4-pro-comparison-feb-2025/) |
| GEPRC Mark5 | 2107.5 1960 KV | 392 g trocken | 6S 1300–1500 | „6–8 min je nach Stil“ (Händler) | – | – | [OL Mark5](https://oscarliang.com/geprc-mark5/), Suchauszug |
| 5″ auf 8S (Manta 5 SE) | 2207 1600 KV | 385,4 g / 642,2 g | 8S 1100 LiPo (256,7 g) | ≈ 5:40 | – | – | [OL 8S-Build](https://oscarliang.com/8s-fpv-drone-build/) (Baubericht) |
| 5″ allgemein | – | – | 4S 1500 / 6S 1100 | 5–7 min | Einsteiger-Guide (gemäßigt) | – | Suchauszug [OL FPV-Guide](https://oscarliang.com/fpv-drone-guide/) |
| 5″ allgemein | – | – | – | 3–6 min Freestyle; 6–12 min effizienter Cruiser | Händler-Wissensseite | – | [Unmanned Tech Flugzeit](https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-drone-flight-time-how-to-get-more-minutes-in-the-air) |
| 5″ Top-Speed | – | – | 6S | – | – | ≈ 150 km/h typisch, > 200 km/h Spezialaufbau | [Unmanned Tech Speed](https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-drone-top-speed-how-fast-real-world-by-build-class) |

Weitere Richtwerte:

| Größe | Wert | Quelle |
|---|---|---|
| Motor/KV 6S Freestyle | 2306–2308, 1700–2100 KV; trocken 300–450 g; 6S 1000–1300 mAh | [OL Lookup](https://oscarliang.com/table-prop-motor-lipo-weight/) |
| Prüfstand T-Motor F60 Pro V 1950 KV, T5146-3, 6S | 20 %: 2,5 A / 260 g / 63 W (4,1 g/W); 40 %: 8,3 A / 646 g / 208 W (3,1 g/W); 60 %: 17,0 A / 1009 g / 426 W; 80 %: 29,0 A / 1544 g / 722 W; 100 %: 46,8 A / 1966 g / 1155 W (1,70 g/W); Spitze (10 s) 48,3 A, 1306 W | Suchauszug T-Motor-Datenblatt ([T-Motor Store](https://store.tmotor.com/goods.php?id=1237)) |
| → S/G bei 700 g | ≈ 11 : 1 (4 × 1966 g, statisch, voller Akku) | abgeleitet |
| Vollgasstrom 5″ 6S | 30–45 A je Motor, 120–180 A gesamt | Suchauszüge ([intofpv Amp draw](https://intofpv.com/t-amp-draw-on-2207-motors), [x-teamrc 2207](https://www.x-teamrc.com/2207-1960kv-motor-5-inch-fpv/)), niedrige bis mittlere Sicherheit |
| Akkugewichte 6S | 1100 mAh 177–199 g, 1300 mAh 198 g, 1500 mAh 222 g (LiHV), 1530 mAh LiPo 251 g | [OL GNB-Test](https://oscarliang.com/gnb-6s-lihv-lipo-batteries/) |
| Echte Kapazität 6S LiHV 1100 / 1300 / 1500 | 1049 / 1227 / 1375 mAh ab 4,35 V; nur 909 / 1044 / 1180 mAh ab 4,20 V (bis 3,1 V, 2C) | [OL GNB-Test](https://oscarliang.com/gnb-6s-lihv-lipo-batteries/) |

Abgeleitet (f = 0,80): Vapor D5 aggressiv 391 W (0,56 W/g, 17,6 A), ruhiger 293 W (0,42 W/g, 13,2 A);
8S-5″ 276 W (0,43 W/g). Schweben (aus Mach-R5-Herstellerwert, Abschnitt 7): ≈ 0,24–0,3 W/g → ein 700-g-5″ braucht
≈ 170–210 W inklusive Avionik; 6S 1100 reicht damit für ≈ 5,5–7 min Schweben. Gemischtes Fliegen ≈ 1,7- bis 2-fache,
aggressives ≈ 2- bis 2,3-fache Schwebeleistung.

---

## 7. 5″ Race 6S

| Drohne / Quelle | Werte | Art |
|---|---|---|
| iFlight Mach R5 Sport | 300 g trocken, ≈ 525 g mit 6S 1400; 2207 2050 KV; Schweben ≈ 12 min mit 6S 1400 „no load“; max. 208 km/h; 55-A-ESC | Hersteller ([Defiance RC](https://www.defiancerc.com/products/iflight-mach-r5-sport-6s-race-drone)) |
| iFlight Mach R5 (Vorgänger) | 338 g trocken, XING2 2506 1850 KV, 6S 1300 empfohlen | [OL Mach R5](https://oscarliang.com/iflight-mach-r5/) |
| MultiGP Freedom Spec | 5″, beliebiger 2207-Motor, max. 6S, **mindestens 533 g**, Drehzahlgrenze 18 000 RPM | [MultiGP Klassen](https://www.multigp.com/class-specifications/) |
| MultiGP Open | max. 305 mm Diagonale, **max. 800 g AUW**, max. 4,35 V/Zelle | [MultiGP Klassen](https://www.multigp.com/class-specifications/) |
| Race-Richtwerte | 2207 1900–2300 KV auf 6S, 6S 1000–1300 mAh, **trocken 250–300 g** | [OL Lookup](https://oscarliang.com/table-prop-motor-lipo-weight/) |
| Race-Shoppingliste | 2207, 6S 1600–2200 KV, 6S 1300–1400 mAh, HQ R38 (5.1×3.8×3) | [FPVKnowItAll](https://www.fpvknowitall.com/fpv-shopping-list-five-inch-racing/) |
| Race-Prop flach (Playtest 10.10.2026) | HQProp 5129 R29: 5,1×2,9×3, 4,4 g; auf demselben Motor gleicher Standschub wie 5,1×4,6×3 bei 3–4 % mehr Strom | [Kiwi Quads](https://kiwiquads.co.nz/product/hqprop-5129-r29-poly-carbonate-propeller-grey/), [T-Hobby P22.6-7](https://www.ligpower.com/product/p22_6-7-kv1980-fpv-motor.html); `motors-props.md` §12 |
| Race-Rahmen True-X | TransTEC Lightning Real X V2: 215 mm, Arme 5 mm, 87 g; Hyperlite F3LX 59 g; FlightOne Skinny Kid 61 g; Five33 LightSwitch 45 g | [TransTEC](https://www.myfpvstore.com/yima), [F3LX](https://www.myfpvstore.com/5mwp), [Skinny Kid](https://www.myfpvstore.com/quadcopter-frames/flightone-skinny-kid-racing-drone-frame/), [LightSwitch](https://racedayquads.com/products/five33-lightswitch-5-racing-frame-kit-lite-t700-arms) |
| Flugzeit Rennen | „1300 mAh hält im Schnitt etwa drei Minuten; ein Profi leert ihn in unter zwei, ein langsamer Einsteiger kommt auf bis zu fünf“ | Suchauszug [GetFPV Akku-Guide](https://www.getfpv.com/learn/new-to-fpv/all-about-multirotor-fpv-drone-battery/) (Seite 403), mittlere Sicherheit |
| Flugzeit Rennen | „die meisten Race-Quads 2–3 min bei aggressivem Flug, 5″ etwa 3:30 ohne Alarm mit 1300 mAh“ | Suchauszug [intofpv](https://intofpv.com/t-flight-time-and-battery-specs) (403), niedrige Sicherheit |
| Top-Speed Race | 130–160 km/h üblich, 145–161 km/h „race quads“; DRL Racer4 90 mph (145 km/h) | Suchauszüge ([MultiGP](https://www.multigp.com/class-specifications/)), [DRL Racer4](https://www.coolthings.com/drl-racer4-street-90mph-racing-drone/) |

Abgeleitet: Mach R5 Sport Schweben ≈ 124 W bei 525 g (0,24 W/g, 4,2 g/W, Herstellerwert – echte Schwebezeiten eher
10–20 % kürzer). Rennen auf 6S 1300 bei ≈ 520 g: 3 min → 462 W (0,89 W/g, 21 A Mittel), 2 min → 693 W (1,33 W/g,
31 A Mittel) = **3,7- bis 5,5-fache Schwebeleistung**.

---

## 8. 7″ Long Range 6S (LiPo und Li-Ion)

| Drohne / Aufbau | Motor / Prop | Trocken / AUW | Akku | Flugzeit | Bedingung | Quelle |
|---|---|---|---|---|---|---|
| GEPRC MOZ7 V2 (7,5″) | 2809 1450 KV, HQ 7.5×3.7×3 | 843 g gemessen (750 g Angabe) / 1218 g (6S 2200 LiPo) / 1265 g (6S 4000 Li-Ion 21700) / 1480 g (6S 6000 Li-Ion 18650 6S2P) | 6S 6000 Li-Ion | knapp 16 min, > 15 km | Wind 15–20 mph; Reise 60–70 km/h bei ≈ 35 % Gas; Schwebegas 30 % (6000) / 26 % (4000); Top ≈ 110 km/h; Hersteller „≈ 25 min“ | [OL MOZ7 V2](https://oscarliang.com/geprc-moz7-v2/) (Review) |
| 150-$-7″ (Eigenbau) | 2806.5 1300 KV, Gemfan 7040×3 | 473 g trocken, ≈ 770 g AUW (Akku geschätzt ≈ 300 g) | 6S 3600 Li-Ion | 18:40 bei ≈ 12 km/h Wind (16,1 km); 14:30 bei ≈ 27 km/h Wind (13,05 km) | Landung **2,8 V/Zelle** | [OL 150-$-7″](https://oscarliang.com/150-dollar-7inch-fpv-drone/) (Baubericht) |
| iFlight Chimera7 Pro V2 (7,5″ Deadcat) | 2809 1250 KV, 7.5×3.7×3 | 705 g trocken | 6S 8000 Li-Ion 21700 (≈ 840 g) | „≈ 30 min, no load“ | Herstellerangabe = Schweben | [iFlight Chimera7 Pro V2](https://shop.iflight.com/Chimera7-Pro-V2-6S-Pro1947) |
| Chimera9 ECO (9″) | – | ≈ 1400 g AUW | 6S2P 6000 Li-Ion (608 g) | 18 min bis 3,1 V/Zelle (+2–4 min möglich) | Review | [FirstQuadcopter Fullsend 6000](https://www.firstquadcopter.com/reviews/fullsend-6000mah-li-ion-review/) |
| Darwin129 (7″ BNF) | – | – | 4S 1500 LiPo | knapp 9 min (derselbe Akku auf 5″ ≈ 6 min) | Review | [OL Long-Range-Guide](https://oscarliang.com/long-range-fpv-flying-mini-quad/) |
| 7″ allgemein | – | – | – | 10–30+ min je nach LiPo/Li-Ion, Nutzlast, Wind | Händler-Wissensseite | [Unmanned Tech Flugzeit](https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-drone-flight-time-how-to-get-more-minutes-in-the-air) |
| Richtwerte 7″ | 2806–3106; 1200–1400 KV (6S Freestyle, 6S 2200–3000, 400–500 g trocken); LR 6S 980–1300 KV mit Li-Ion 2500–4000 | – | – | – | Leitfaden | [OL Lookup](https://oscarliang.com/table-prop-motor-lipo-weight/) |

Akkugewichte und Energiedichte:

| Akku | Gewicht | Energie | Wh/kg | Quelle |
|---|---|---|---|---|
| 6S 1800 LiPo (150C / 120C) | 287 / 302 g | 40 Wh | 132–139 | Suchauszug Tattu/CNHL |
| 6S 2200 LiPo 95C | 321 g (±20) | 48,8 Wh | 152 | [Pyrodrone Tattu 2200](https://pyrodrone.com/products/tattu-2200mah-6s-95c-22-2v-r-line-lipo-battery-for-7-quads-xt60) |
| 6S 3300 LiPo (35–150C) | 434–540 g | 73 Wh | 135–168 | Suchauszug Foxtech/Ovonic/Tattu |
| 6S1P 3000 Li-Ion 18650 | 314 g | 66,6 Wh | 212 | [OL Lumenier NAV](https://oscarliang.com/lumenier-nav-6s-3000mah-li-ion/) |
| 6S2P 6000 Li-Ion 18650 | 608–637 g | 130–133 Wh | 210–213 | [FirstQuadcopter](https://www.firstquadcopter.com/reviews/fullsend-6000mah-li-ion-review/), [OL Lumenier NAV](https://oscarliang.com/lumenier-nav-6s-3000mah-li-ion/) |
| 6S2P 8000 Li-Ion 21700 | 840 g (±10) | 173 Wh | 206 | Suchauszug iFlight Fullsend 8000 |
| Li-Ion Belastbarkeit | 6S1P 3000: 30 A dauernd / 50 A Spitze; 6S2P 6000: 60 / 100 A; Innenwiderstand 13–15 mΩ je Zelle bzw. 9–11 mΩ | [OL Lumenier NAV](https://oscarliang.com/lumenier-nav-6s-3000mah-li-ion/), [FirstQuadcopter](https://www.firstquadcopter.com/reviews/fullsend-6000mah-li-ion-review/) |

Abgeleitet (Li-Ion f = 0,88): 150-$-7″ ruhig 220 W (0,28 W/g, 10 A), windig 283 W (0,37 W/g); MOZ7 V2 im Wind 428 W
(0,29 W/g, 20 A); Chimera9 ECO 380 W (0,27 W/g); Chimera7-Schwebeangabe 304 W bei 1545 g (0,20 W/g, 5,1 g/W).
Reisen kostet beim 7″ ≈ 1,3- bis 1,5-mal die Schwebeleistung (MOZ7: 35 % Reisegas gegen 26–30 % Schwebegas).

---

## 9. 7″ auf 8S

Direkte Messungen für 7″-Quads auf 8S sind selten; die Klasse ist physikalisch aber gut ableitbar.

| Größe | Wert | Herleitung / Quelle |
|---|---|---|
| Motoren | 2807–2808 mit ≈ 1000–1150 KV (gleiche Leerlaufdrehzahl wie 1300–1500 KV auf 6S) | KV × Spannung; 8S-Beispiel 5″: 2207 1600 KV statt ≈ 1950 KV auf 6S ([OL 8S-Build](https://oscarliang.com/8s-fpv-drone-build/)) |
| Akku 8S 1100 / 1050 LiPo | 256,7 g / 242 g | [OL 8S-Build](https://oscarliang.com/8s-fpv-drone-build/), [Tattu R-Line 8S 1050](https://genstattu.com/tattu-r-line-version-5-0-1050mah-8s-150c-29-6v-lipo-battery-pack-with-xt60-plug/) |
| Akku 8S 2200 LiPo | ≈ 410–460 g (Schätzung: 4/3 × 6S 2200 = 428 g) | abgeleitet aus [Tattu 6S 2200](https://pyrodrone.com/products/tattu-2200mah-6s-95c-22-2v-r-line-lipo-battery-for-7-quads-xt60) |
| Energie 8S 2200 | 65,1 Wh nominal ≈ 6S 2930 mAh; nutzbar (f 0,80) ≈ 52 Wh | abgeleitet |
| „8S 1100 hat ähnlich viel Energie wie 6S 1500“ | bestätigt die Wh-Rechnung | [OL 8S-Build](https://oscarliang.com/8s-fpv-drone-build/) |
| Ströme | bei gleicher Leistung 6/8 = 75 % der 6S-Ströme | Physik |
| Flugzeit | wie ein 6S-7″ mit gleicher Energie und gleichem Gewicht; die Zellzahl allein verlängert nichts | Energiebilanz |
| 8″ auf 6S 2200 LiPo | 7–10 min je nach Stil | Suchauszug Ampow/Ovonic Roam, niedrige Sicherheit |
| 10″ auf 6S 2200 LiPo | ≈ 8 min | [RotorBuilds Rekon10](https://rotorbuilds.com/build/31710/) |

Abgeleitete Richtwerte für einen leichten 7″ auf 8S 2200 (≈ 900 g): Schweben ≈ 135–200 W → 15–23 min;
Reisen 60 km/h ≈ 250–300 W → 10–12 min; gemischt/Freestyle ≈ 0,4–0,5 W/g → 360–450 W → 7–9 min; aggressiv
≈ 0,55–0,65 W/g → 5–6 min; Höchstgeschwindigkeit 140–170 km/h; S/G 9–13 : 1.

---

## 10. X-Class und große Quads (10–13″, 8S/12S)

| Drohne / Quelle | Werte | Art |
|---|---|---|
| X-Class-Regeln | 800–1200 mm Motor-zu-Motor-Diagonale, mindestens 3 Motoren, keine Grenze für Prop, Motor, Zellzahl | [X Class Rules](https://shop.arura.ai/pages/rules-standards) |
| X-Class (AMA Model Aviation) | Props bis 15″, „Geschwindigkeiten bis 100 mph“ (161 km/h), Preis fast 2000 $ | [Model Aviation](https://www.modelaviation.com/x-class-drone-racing) |
| „Big 8S X Class“ (Eigenbau) | 1000 mm, Omen 700 KV, Gemfan 1310-3 (13″), 80-A-ESCs 8S, 2 × 4S 5000 in Serie; ≈ 2100 g trocken (AUW ≈ 3,2 kg geschätzt); „etwa 5 min Flugzeit mit voller Ladung“ | [RotorBuilds 22860](https://rotorbuilds.com/build/22860) |
| „Sloth DD“ X-Class (Eigenbau) | KO Method X 5025 430 KV, Gemfan 1310 13″ 3-Blatt, 200-A-ESCs, 12S 5000–5200; 2725 g trocken, ≈ 4,5 kg AUW (Akkus ≈ 1600 g, Props ≈ 180 g) | [RotorBuilds 26283](https://rotorbuilds.com/build/26283) |
| Tarot 680 X-Class (Eigenbau) | 4215 520 KV, 13×12, APD 120 A 12S; zuerst 8S (2 × 4S 1500) | [RotorBuilds 22654](https://rotorbuilds.com/build/22654) |
| X-Class-Überblick (Akkuhersteller) | 8S–12S, 300–500 KV auf 13″, ESCs 100–120 A+, **150–180 km/h in Renn-Trimm**, 150–250 A auf 12S; AUW-Angabe „5–15 kg“ gilt für Lastträger, nicht für Racer | [Tattu X-Class](https://www.tattuworld.com/resources/what-is-an-x-class-fpv-drone.html) (Marketing, mittlere Sicherheit) |
| 4214-Motoren | iFlight XING 4214: 255 g, 400 KV (61,6 mΩ, 30,9 A, 742 W) / 660 KV (35,6 mΩ, 98 A, 2352 W, 7,9 kg Schub); Axisflying AZ4214 380 KV: 246 g, 54 mΩ, 65 A, 1617 W, 4,58 kg Schub | [iFlight XING 4214](https://shop.iflight.com/xing-x4214-2-8s-x-class-fpv-nextgen-motor-pro802), [Axisflying AZ4214](https://www.axisflying.com/products/az4214-v2) |
| Motorklassen-Überblick | 3115: 9–11″, 6S, 900–1100 KV, ≈ 130 g, 4,8 kg Schub, AUW 1,5–2,5 kg; 4214: 13″, 6–10S, 380–400 KV, 3,2–4,0 kg Schub, AUW 3–4 kg, 1300–1700 W | [x-teamrc Motorgrößen](https://www.x-teamrc.com/fpv-motor-size-guide-2207-vs-3115-vs-4214-vs-4218/) (Händler) |
| iFlight Nazgul XL10 V6 (10″ 6S) | 860 g trocken, 3314 900 KV, HQ 10×4.5×3, 420 mm; „≈ 40 min mit 6S2P 8000 no load“; 140 km/h | [iFlight XL10 V6](https://shop.iflight.com/XL10-V6-6S-Pro1974) (Hersteller) |
| Rekon10 (10″) | 3110 1250 KV, 10×5×3; 26–30+ min mit 4S 8400 Li-Ion; ≈ 8 min mit 6S 2200 LiPo | [RotorBuilds 31710](https://rotorbuilds.com/build/31710/) |
| Axisflying Manta 10 X Lite | 3115 900 KV, 3,8 kg Abflug mit 2,5 kg Nutzlast, 5–10 min | Suchauszug Hersteller |
| Axisflying Manta 13 X Lite / DarwinFPV Toruk13 (12S) | 10–15 min, 100 km/h / 7 min mit 6 kg Nutzlast, 150 km/h, 4320 350 KV | Suchauszüge Hersteller |
| Größenklassen-Überblick | 10″: 10–20 min, 140–160 km/h; 13″: 15–30 min (8S 5000–12000), 100–150 km/h | [Grepow 10/13/15″](https://www.grepow.com/blog/10-inch-fpv-vs-13-inch-fpv-vs-15-inch-fpv-what-is-the-difference.html) (Akkuhersteller, Lastträger) |

Abgeleitet: Big 8S X-Class ≈ 1,4 kW Mittel bei ≈ 3,2 kg (0,44 W/g, ≈ 48 A auf 8S) – dieselbe spezifische Leistung wie
5″-Freestyle. XL10-Schwebeangabe ≈ 228 W bei 1,7 kg (0,13 W/g, 7,5 g/W): große Props sind im Schweben pro Gramm
etwa doppelt so effizient wie 5″. X-Class-Renntempo (Schätzung): 2–4 min pro 8S 5000.

---

## 11. Klassenübergreifend: spezifische Leistung je Flugstil

Aus den Abschnitten 2–10 (f wie in Abschnitt 1). Das ist die kompakteste Kalibriergröße: Flugzeit =
nutzbare Energie / (spezifische Leistung × AUW + Avionik).

| Klasse | Schweben | Reisen / Cinematic | Gemischt | Aggressiv / Rennen |
|---|---|---|---|---|
| 65 mm 1S Whoop, analog | 0,45–0,5 W/g (≈ 2,0–2,2 g/W) | 0,55–0,65 | 0,75–1,0 | 1,1–1,45 |
| 65 mm 1S Whoop, digital | 0,65–0,7 | – | – | – |
| 75 mm 1S Whoop | ≈ 0,35–0,4 (abgeleitet) | 0,45–0,47 | 0,5–0,6 | ≈ 0,6–0,7 |
| Toothpick 2,5–3,5″ | ≈ 0,2–0,25 (abgeleitet) | 0,2–0,38 | 0,38–0,5 | 0,5–0,75 |
| Cinewhoop 3–3,5″ | ≈ 0,2–0,25 (abgeleitet) | 0,25–0,3 | 0,45–0,5 (mit Actioncam) | – |
| 5″ Freestyle / Race | 0,24–0,3 | ≈ 0,3–0,35 | 0,42–0,5 | 0,55–0,6 Freestyle, 0,9–1,3 Rennen |
| 7″ LR | 0,15–0,22 (leicht 0,15–0,18, schwer mit Li-Ion ≈ 0,2) | 0,27–0,37 | ≈ 0,4–0,5 (abgeleitet) | – |
| 10″ LR | ≈ 0,13 (Herstellerwert) | – | – | – |
| X-Class 13″ | – | – | ≈ 0,44 | – |

Verhältnis mittlere Leistung / Schwebeleistung (für Leistungsprofile der Flugzeit-Metriken):

| Stil | Whoop 65/75 mm | 5″ Freestyle | 5″ Race | 7″ LR |
|---|---|---|---|---|
| Reisen | 1,2–1,4 | 1,2–1,4 | – | 1,3–1,5 (bei 60–70 km/h, Wind mehr) |
| Gemischt | 1,8–2,0 | 1,7–2,0 | – | ≈ 2 (Freestyle-Anteil) |
| Aggressiv / Rennen | 2,5–3,0 | 2,0–2,3 | 3,7–5,5 | – |

Wichtig: Kleine Klassen sind pro Gramm **deutlich ineffizienter** (31-mm-Props bei niedriger Reynoldszahl, Ducts,
Avionik-Grundlast von 1–6 W auf nur 25 g). Ein einziger Systemwirkungsgrad über alle Klassen führt zwangsläufig zu
zu langen Whoop- und zu kurzen LR-Zeiten.

---

## 12. Landespannung, nutzbare Kapazität, Sag

| Chemie / Klasse | Richtwert | Quelle |
|---|---|---|
| LiPo allgemein | landen bei ≈ 3,5 V/Zelle **unter Last** (erholt sich danach höher); voll 4,2 V; „leer“ 3,0 V; Lagerung 3,80–3,85 V | [OL LiPo-Guide](https://oscarliang.com/lipo-battery-guide/) |
| LiHV | voll 4,35 V/Zelle | [OL LiPo-Guide](https://oscarliang.com/lipo-battery-guide/) |
| 1S Whoop | Warnung ≈ 3,3 V, „land now“ ≈ 3,0 V; Piloten landen bei 3,3–3,4 V; nach ruhigem Flug Erholung auf ≈ 3,49 V | Suchauszüge intofpv (403), [OL Air65](https://oscarliang.com/betafpv-air65/) empfiehlt 3,2 V |
| Nutzbarer Anteil LiPo | ≈ 86 % bis 3,5 V Ruhe (Beispielrechnung) | [OL Akkukapazität](https://oscarliang.com/how-to-choose-battery-for-quadcopter-multicopter/) |
| 6S LiHV real | echte Kapazität 5–8 % unter Nennwert; nur auf 4,20 V geladen fehlen weitere ≈ 14 % | [OL GNB-Test](https://oscarliang.com/gnb-6s-lihv-lipo-batteries/) |
| Li-Ion | Landung 2,8–3,1 V/Zelle unter Last; 2,8 V im 150-$-7″; 3,1 V im Chimera9-Test mit Reserve für 2–4 min | [OL 150-$-7″](https://oscarliang.com/150-dollar-7inch-fpv-drone/), [FirstQuadcopter](https://www.firstquadcopter.com/reviews/fullsend-6000mah-li-ion-review/) |
| Li-Ion Sag | Zellen unterscheiden sich um ≈ 0,4 V Sag (P28A gegen NAV); Li-Ion begrenzt Vollgas auf 30–60 A je Pack | [OL Lumenier NAV](https://oscarliang.com/lumenier-nav-6s-3000mah-li-ion/) |

Hinweis zum Spiel: Die Chemie-Daten nutzen heute Abschaltwerte 3,3 V (LiPo/LiHV) und 2,8 V (Li-Ion) und
`flightTimeMinutes` rechnet pauschal mit 80 % der Kapazität. Die Referenz spricht für 80 % (LiPo), 85 % (1S-LiHV,
Landung 3,3–3,5 V unter Last) und 85–90 % (Li-Ion) – aber mit Sag unter Last, der bei Whoops und Race-Quads die
Landung vorzieht.

---

## 13. Avionik- und Videostrom (Grundlast)

| Komponente | Leistung | Quelle |
|---|---|---|
| Analog-VTX 25 mW / 200 mW / 600 mW | ≈ 130 / 230 / 550–700 mA (bei 5 V) | Suchauszug VTX-Guides (mittlere Sicherheit) |
| FC + Empfänger + Analogkamera (Whoop-AIO) | ≈ 1–1,5 W (Schätzung) | – |
| HDZero Whoop V2 / Race V3 / ECO | 5–6 W (5,3 / 5,5 / 4,5 g) | [HDZero VTX](https://docs.hd-zero.com/vtx-summary) |
| HDZero Freestyle V2 | 6–15 W | [HDZero VTX](https://docs.hd-zero.com/vtx-summary) |
| DJI O4 Lite | 4,90 W (25 mW) … 6,0 W (700 mW); 8,5 g | [OL O4 Lite](https://oscarliang.com/dji-o4-air-unit-lite/) |
| DJI O4 Pro | 7,4 W (25 mW) … 10,5 W (1200 mW); unbewaffnet ≈ 3 W; 32 g mit Kamera | [OL O4 Pro](https://oscarliang.com/dji-o4-air-unit-pro/) |
| DJI O3 | bis ≈ 16 W; 39,5 g | [OL O3](https://oscarliang.com/dji-o3-air-unit-fpv-goggles-2/) |
| Walksnail Avatar HD | ≈ 7,4 W bei 700 mW | Suchauszug Caddx |
| Wirkung im 65-mm-Whoop | HDZero statt analog: Schweben 5:03 → 3:17; 400 statt 25 mW analog: 2:38 → 1:52 | [Emax](https://shop.emax-usa.com/products/emax-tinyhawk-aero-65mm-1s-fpv-racing-whoop-bnf-elrs), [unmanned.tech](https://blog.unmanned.tech/emax-tinyhawk-aero-1s-whoop-review/) |

Beim Whoop ist die Video-Grundlast (1,5–6 W) ein großer Teil der Schwebeleistung (12 W); beim 5″ (≈ 190 W Schweben)
sind 10 W Digital-Video ≈ 5 %.

---

## 14. Motortemperaturen

| Größe | Richtwert | Quelle |
|---|---|---|
| Nach normalem Flug | „warm, nicht heiß“; Fingertest: Glocke mehr als 10 s angenehm haltbar (≈ 45–55 °C) | [OL Motorhitze](https://oscarliang.com/mini-quad-motors-overheat/), [Unmanned Tech Motorhitze](https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-motor-overheating-troubleshooting-guide) |
| Nach 3 min aggressivem 5″-Freestyle | „kaum warm“ (Vapor D5) | [OL Vapor D5](https://oscarliang.com/geprc-vapor-d5-o4/) |
| Tuning-Grenze Glocke | ≈ 70 °C nach aggressivem 2-min-Flug; > 60 °C am Motorfuß = Wicklungen leiden | Suchauszug (niedrige Sicherheit) |
| Magnete | N52 max. 80 °C, **N52H 120 °C**, N52SH 150 °C (Dauerbetrieb ≥ 20 K darunter auslegen) | [Radial Magnet Klassen](https://radialmagnet.com/neodymium-magnet-temperature-classes/) u. a. |
| Magnet-/Drahtklassen in FPV-Motoren | N52H/N52HS-Magnete, Wicklungsdraht 220 °C (iFlight XING, FlyFish Flash), GEPRC SPEEDX2 bis 360 °C | Suchauszüge Herstellerseiten |
| Herstellertests „Spitze“ | Spitzenstrom/-leistung nur für 10 s angegeben (T-Motor F60 Pro V: 48,3 A / 1306 W über 10 s) | Suchauszug T-Motor |

Einordnung: „Vollgas im Stand über den ganzen Akku“ ist kein realer Betriebsfall. Ein 7″ auf 8S 2200 wäre bei
≈ 137 A nach ≈ 45 s leer (80 % von 2,2 Ah); Hersteller begrenzen Vollgastests auf 10–30 s. Reale Zielwerte: nach
typischem Flug 40–60 °C, nach harten Vollgaspassagen 60–80 °C; ≥ 100–120 °C bedeutet Schaden an N52H-Magneten.

---

## 15. Owner-Testfall (harte Prüfung): 65-mm-Whoop 0702 27000 KV, 31 mm 4-Blatt, 1S 300 mAh LiHV

Sharecode `PW1-040P7-XB1WG-MCZJ6-F5Y43-ECV5W-97WSK-4FGZR-0S2Z3-SM99K-E3K1Q-60V37-2HQ60-T30C3-3CGN3-0DWD6-S563R-ACSD6-CTB29-SY4WS-KJE5Z-46DSJ-A06N3-8D1KH-37JGR-30CG1-RPR1H-X0`.
Spiel heute: ≈ 7 min. Owner: echte Teile 2–3 min.

### 15.1 Überprüfung der Referenzantwort

| Aussage der Referenzantwort | Befund | Belege |
|---|---|---|
| Ruhiges Indoor-Cruisen 3:00–3:30 (bis ≈ 4 min unter 17 g trocken) | **bestätigt**; ultraleichte Aufbauten (17,25 g, 3-Blatt) erreichen 4:00–4:30 bei sehr zurückhaltendem Flug | Mobula6 2024 ≈ 3:30; Air65 FS 2:30–3:30; Air65 (oscarliang.com) 4:00–4:30; Air65 II 4:00 mit 320 mAh |
| Gemischtes Fliegen 2:00–2:30 | **bestätigt** (Quellen streuen bis 2:45) | Tinyhawk Aero 27000 KV 2:36–2:38; Air65 Racing 27000 KV 2:00–2:45; Happymodel 2:00–2:30; „2 bis 3 Minuten“ |
| Aggressiv/Vollgas-Freestyle 1:30–2:00 | **bestätigt** | Tinyhawk Aero 1:40–1:52 (mit 400 mW); „27000 KV mit 1219S leert 300 mAh bei Vollgas in unter 3 min“ (Suchauszug) |
| Landung ≈ 3,3–3,5 V unter Last | **bestätigt** (Untergrenze 3,2 V laut oscarliang.com) | Abschnitt 12 |
| 27000 KV zieht bei hoher Drehzahl spürbar mehr Strom als 23000 KV | **bestätigt**: 23000 KV fliegt 30–40 s (+15–25 %) länger | [OL Air65](https://oscarliang.com/betafpv-air65/), [prowhooper](https://prowhooper.com/ultimate-beginner-whoop-guide/) |
| 4-Blatt 31 mm: mehr Grip, aber mehr Widerstand/Strom als 2-/3-Blatt | **bestätigt**, Stärke vom Gasbereich abhängig: bei Vollgas nur 2–5 % Wirkungsgradverlust, bei gleichem Gas 20–30 % mehr Strom als Zweiblatt | Abschnitt 2.3 |
| Leichtere 2-/3-Blatt-Props ≈ 20–30 s mehr | **plausibel** (+10–20 % auf 2:00–2:30) | Abschnitt 2.3 |

### 15.2 Referenzwerte für die harte Prüfung

| Größe | Sollbereich | Anmerkung |
|---|---|---|
| AUW | 25–30 g | trocken 17–21 g (analog) + Akku 8,3 g |
| Schub/Gewicht | 4,5–6 : 1 | 0702 27000 KV ≈ 32–36 g je Motor mit 31 mm |
| Schwebegas | 30–40 % | 4-Blatt/27000 KV eher 30–35 % |
| Schweben (Schwebetest) | 3:45–4:45 | Emax-Schwebetests 4:48–5:07 mit 320 mAh und 16,3–16,9 g; 300 mAh und 4-Blatt ziehen ab |
| Reisen (ruhig indoor) | 3:00–3:30 (≤ 4:00 bei < 17 g trocken) | – |
| Gemischt / typisch | 2:00–2:30 | Quellen bis 2:45 |
| Aggressiv / Vollgas-Freestyle | 1:30–2:00 | – |
| Mittlere Ströme (f 0,85 → 255 mAh) | Schweben 3,2–4,1 A, Reisen 4,4–5,1 A, gemischt 6,1–7,7 A, aggressiv 7,7–10 A | abgeleitet |
| Vollgasstrom | 12–16 A | 4 × 3–4 A; AIO-ESCs 5–6 A je Motor dauerhaft |
| Landespannung | 3,3–3,5 V unter Last | – |
| Tendenz 23000 statt 27000 KV | +15–25 % in „gemischt“ und „aggressiv“ (+20–40 s) | – |
| Tendenz 2-/3-Blatt statt 4-Blatt | +10–20 % in allen Stilen (+15–30 s bei gemischt) | – |
| Tendenz Digital-Video statt analog | −30–35 % | Abschnitt 13 |
| Höchstgeschwindigkeit | 35–55 km/h | Schätzung, keine Messung gefunden |

---

## 16. Plausibilitätsprüfung Owner-Beispiel „Deadcat LR 7″ · 8S“ (8S 2200 mAh)

| Spielwert | Referenz | Bewertung |
|---|---|---|
| 896,9 g | 8S 2200 ≈ 410–460 g → trocken ≈ 440–490 g; leichter Analog-7″ (150-$-Build 473 g trocken); GPS/Digital-BNFs 600–850 g trocken | plausibel für leichten Aufbau |
| S/G 11,7 : 1 | 9–13 : 1 auf 8S (7″-LR auf 6S Li-Ion nur 4–7) | plausibel, oberer Bereich |
| Schwebegas 21,7 % | 20–28 % | plausibel |
| Schweben 24,2 min | 15–23 min (0,15–0,22 W/g × 900 g ≈ 135–200 W; 52 Wh nutzbar) | **etwas zu lang** (oberer Rand +5 %, Bereichsmitte +25 %) |
| Reisen 10,5 min | 10–12 min (≈ 0,28–0,33 W/g bei 60 km/h) | passt |
| gemischt/Freestyle | 7–9 min | fehlt heute als Metrik |
| Top 145 km/h | 140–170 km/h (LiPo) | plausibel |
| Vollgasstrom 137,5 A | 90–150 A auf 8S; 8S 2200 bei 65C = 143 A Nennlast | plausibel |
| Motortemperatur 124 °C „Vollgas im Stand“ | realer Betriebsfall existiert so nicht; Akkureichweite bei Vollgas ≈ 45 s; nach realen Flügen 40–80 °C | Metrik missverständlich, Wert über N52H-Grenze |

Fazit: Beim 7″ liegen Gewicht, Schub, Geschwindigkeit, Strom und Reisezeit im realen Bereich, die Schwebezeit ist etwas
zu optimistisch. Der große Fehler liegt bei den kleinen Klassen (Whoop: Spiel ≈ 7 min gegen real 2–3 min gemischt,
4–5 min reines Schweben).

---

## 17. Vorschlag für die Flugzeit-Metriken (Eingabe für die Kalibrierung)

Aus den Daten oben; die Umsetzung entscheidet die Kalibrierung.

| Metrik | Definition (Vorschlag) | Leistung relativ zu Schweben | Referenz-Plausibilität |
|---|---|---|---|
| Schweben | konstantes Schweben beim AUW, Avionik an, bis Landespannung unter Last | 1,0 | Hersteller-Schwebeangaben minus 10–20 % |
| Reisen | ruhiger Geradeausflug mit klassentypischer Reisegeschwindigkeit (Whoop indoor 15–25 km/h, Toothpick 40–60, Cinewhoop 30–50, 5″ 60–80, 7″ 50–70, X-Class 80–100; Schätzungen außer 7″) | 1,2–1,5 | MOZ7 V2 60–70 km/h bei 35 % Gas |
| Gemischt / typisch | klassentypisches Fliegen: Whoop Indoor-Runden, 5″ Freestyle mit Pausen, 7″ Cruise mit Tauchern, Cinewhoop Cinematic | Whoop 1,8–2,0; 5″/3,5″ 1,7–2,0; Cinewhoop cinematic 1,2–1,4 | Abschnitte 2–8 |
| Aggressiv / Rennen (optional) | Rennpace bzw. hartes Freestyle | Whoop 2,5–3,0; 5″ Freestyle 2,0–2,3; 5″ Race 3,7–5,5 | Abschnitte 2, 6, 7 |

Randbedingungen für alle Metriken: nutzbare Kapazität bis Landespannung unter Last inklusive Sag (LiPo ≈ 80 %,
1S-LiHV ≈ 85 %, Li-Ion 85–90 %, bei hoher C-Last weniger), Avionik-Grundlast nach Abschnitt 13, Akku-Istkapazität
≈ 5–8 % unter Nennwert.

---

## 17a. Gewichtsprüfung 7″ (Propwash 0.5.0)

Anlass: Ein Creator, der selbst 7″ fliegt (damals auf 0.3.x), fand das Gewicht seines 7″ unplausibel. Die Werkbank
zeigte nur „Gewicht“ – das Abfluggewicht mit Akku; Piloten nennen meist das Trockengewicht. Seit 0.5.0 zeigen Werkbank,
Konfigurator-Übersicht und Drohnen-Tooltip **Trockengewicht** (ohne Akku) und **Abfluggewicht (mit Akku)** getrennt.

Nachgebaut: LR-Rahmen (`propwash:long_range_7`), 4 × 2807-Motor 1300 KV, 4 × 7″-Zweiblatt, 30×30-Stack, digitales
HD-Video, 6S 1300–2200.

| Teil | Propwash | echte Teile (Quelle) |
|---|---|---|
| 7″-LR-Rahmen | 170 g | TBS Source One V6 7″ 173 g, GEPRC Crocodile7 153/174 g (HD) ([FDG-7]) |
| Motor 2807 | 4 × 47 g | Emax ECO II 2807 47,6 g ohne Kabel, BrotherHobby Avenger 2806.5 41 g, XING 2806.5 54,3 g ([FDG-7]) |
| 7″-Prop, 2 Blätter | 4 × 6,8 g | HQ 7×4×3 9,1 g (Dreiblatt, §4.7 in `motors-props.md`) |
| 30×30-Stack | 18 g | 20–30 g (Schätzung, FC 7–10 g + 4-in-1-ESC 12–20 g) |
| HD-Video | 23 g | 23–36 g je nach System (Schätzung) |
| **Trockengewicht** | **426 g** | Richtwert 7″ 400–500 g trocken ([OL Lookup](https://oscarliang.com/table-prop-motor-lipo-weight/), §8); Eigenbau 473 g (150-$-7″, §8); BNF mit GPS, schwerem Rahmen und Haltern 600–850 g (Chimera7 Pro V2 705 g, MOZ7 V2 843 g, §8) |
| + 6S 1300 LiPo (210 g) | **636 g** | 6S 1300 198 g (§6) → ≈ 600–700 g |
| + 6S 1800 (Just More Parts, 281 g) | 708 g | 6S 1800 287–302 g → ≈ 690–800 g |
| + 6S 2200 (Just More Parts, 337 g) | 763 g | 6S 2200 321 g → ≈ 720–820 g; §0 nennt 780–1220 g (aus BNF mit GPS) |
| + 6S 4000 Li-Ion (Preset, 420 g) | 846 g | 6S 4500 Li-Ion 21700 429 g → ≈ 830–930 g Eigenbau |

**Ergebnis:** Jedes Teil liegt im Bereich echter Teile, das Trockengewicht im Richtwert für einen schlanken 7″-Eigenbau.
Leichter als ein BNF-Long-Range ist er, weil Propwash Kleinteile nicht mitzählt: GPS (8–16 g), Empfänger, XT60-Kabel mit
Kondensator, Kabel, TPU-Halter, Akkuriemen – zusammen 40–70 g. Das gilt für alle Klassen gleich (5″ Freestyle 315 g
trocken gegen Richtwert 300–450 g und BNF 391–490 g; 5″ Race 255 g gegen 250–300 g). **Keine Datenänderung in 0.5.0:**
Nur den 7″ schwerer zu machen wäre gegenüber den anderen Klassen und den Just-More-Parts-Rahmen (Deadcat 7″ ist dort 15 g
schwerer als der eingebaute LR-Rahmen) inkonsistent; Kleinteile für alle Klassen änderten den Flug jeder Drohne und alle
kalibrierten Flugzeiten. Vorschlag für später: Kleinteile je Klasse als eigener Posten (Werkbank-Details „Kleinteile“),
mit neuer Kalibrierung.

[FDG-7]: https://fpvdroneguide.com/blog/7-inch-long-range-fpv-drone-build (Bauanleitung 7″ mit Teilegewichten,
abgerufen 2026-10-05)

## 18. Quellen (Auswahl, alle abgerufen 2026-09-30)

- oscarliang.com: [Air65](https://oscarliang.com/betafpv-air65/), [Air65 II](https://oscarliang.com/betafpv-air65-ii/),
  [Mobula6 2024](https://oscarliang.com/happymodel-mobula6-2024/), [Tiny-Whoop-Props](https://oscarliang.com/tiny-whoop-prop/),
  [Air75](https://oscarliang.com/betafpv-air75/), [Meteor75 Pro](https://oscarliang.com/betafpv-meteor75-pro-whoop-analog/),
  [Mobula7 1S](https://oscarliang.com/mobula7-1s-whoop/), [2,5″ Toothpicks](https://oscarliang.com/ultralight-micro-quad-toothpick/),
  [3″ Toothpicks](https://oscarliang.com/3-inch-toothpicks/), [Lookup-Tabelle](https://oscarliang.com/table-prop-motor-lipo-weight/),
  [Phantom](https://oscarliang.com/geprc-phantom-toothpick/), [HX115 HD](https://oscarliang.com/betafpv-hx115-hd-micro-quad/),
  [HX115 SE](https://oscarliang.com/betafpv-hx115-se/), [Micro-LiPos](https://oscarliang.com/whoop-toothpick-lipo-battery/),
  [Pavo25](https://oscarliang.com/betafpv-pavo25-whoop/), [Pavo35](https://oscarliang.com/betafpv-pavo35/),
  [Cinelog30 V3](https://oscarliang.com/geprc-cinelog30-v3/), [Cinelog35 V2](https://oscarliang.com/geprc-cinelog35-v2/),
  [CineApe35](https://oscarliang.com/darwinfpv-cineape35/), [Cinebot35](https://oscarliang.com/geprc-cinebot35/),
  [Cinewhoops 2026](https://oscarliang.com/cinewhoop/), [Vapor D5](https://oscarliang.com/geprc-vapor-d5-o4/),
  [Mark5](https://oscarliang.com/geprc-mark5/), [Evoque F5](https://oscarliang.com/iflight-nazgul-evoque-f5/),
  [5″-BNF-Vergleich](https://oscarliang.com/5inch-bnf-dji-o4-pro-comparison-feb-2025/), [8S-Build](https://oscarliang.com/8s-fpv-drone-build/),
  [GNB-6S-Test](https://oscarliang.com/gnb-6s-lihv-lipo-batteries/), [Mach R5](https://oscarliang.com/iflight-mach-r5/),
  [MOZ7 V2](https://oscarliang.com/geprc-moz7-v2/), [150-$-7″](https://oscarliang.com/150-dollar-7inch-fpv-drone/),
  [Long-Range-Guide](https://oscarliang.com/long-range-fpv-flying-mini-quad/), [Lumenier NAV](https://oscarliang.com/lumenier-nav-6s-3000mah-li-ion/),
  [LiPo-Guide](https://oscarliang.com/lipo-battery-guide/), [Akkukapazität](https://oscarliang.com/how-to-choose-battery-for-quadcopter-multicopter/),
  [Motorhitze](https://oscarliang.com/mini-quad-motors-overheat/), [O3](https://oscarliang.com/dji-o3-air-unit-fpv-goggles-2/),
  [O4 Lite](https://oscarliang.com/dji-o4-air-unit-lite/), [O4 Pro](https://oscarliang.com/dji-o4-air-unit-pro/)
- Hersteller: [BetaFPV Air65 II](https://betafpv.com/products/air65-ii-brushless-whoop-quadcopter),
  [BetaFPV 0702](https://betafpv.com/products/0702-brushless-motors), [BetaFPV 0702 II](https://betafpv.com/products/0702-ii-brushless-motors),
  [BetaFPV LAVA 300](https://betafpv.com/products/lava-1s-300mah-75c-battery-5pcs), [BetaFPV X-Knight 35](https://betafpv.com/products/x-knight-35-fpv-quadcopter),
  [Happymodel Mobula6 2024](https://www.happymodel.cn/index.php/2024/02/24/mobula6-2024-1s-65mm-ultra-light-micro-fpv-whoop-bnf/),
  [Happymodel Crux35](https://www.happymodel.cn/index.php/2021/08/30/happymodel-crux35-crux35-digital-hd-3-5-inch-4s-micro-freestyle-fpv-racing-drone/),
  [Emax Tinyhawk Aero](https://shop.emax-usa.com/products/emax-tinyhawk-aero-65mm-1s-fpv-racing-whoop-bnf-elrs),
  [GEPRC SMART HD](https://geprc.com/product/geprc-smart-hd-toothpick-fpv/), [iFlight Chimera7 Pro V2](https://shop.iflight.com/Chimera7-Pro-V2-6S-Pro1947),
  [iFlight XL10 V6](https://shop.iflight.com/XL10-V6-6S-Pro1974), [iFlight XING 4214](https://shop.iflight.com/xing-x4214-2-8s-x-class-fpv-nextgen-motor-pro802),
  [Axisflying AZ4214](https://www.axisflying.com/products/az4214-v2), [HDZero VTX](https://docs.hd-zero.com/vtx-summary),
  [Mach R5 Sport (Defiance RC)](https://www.defiancerc.com/products/iflight-mach-r5-sport-6s-race-drone),
  [Tattu 6S 2200 (Pyrodrone)](https://pyrodrone.com/products/tattu-2200mah-6s-95c-22-2v-r-line-lipo-battery-for-7-quads-xt60),
  [Tattu 8S 1050](https://genstattu.com/tattu-r-line-version-5-0-1050mah-8s-150c-29-6v-lipo-battery-pack-with-xt60-plug/)
- Reviews und Guides anderer Seiten: [unmanned.tech Tinyhawk Aero](https://blog.unmanned.tech/emax-tinyhawk-aero-1s-whoop-review/),
  [prowhooper](https://prowhooper.com/ultimate-beginner-whoop-guide/),
  [Unmanned Tech Flugzeit](https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-drone-flight-time-how-to-get-more-minutes-in-the-air),
  [Unmanned Tech Speed](https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-drone-top-speed-how-fast-real-world-by-build-class),
  [Unmanned Tech Props](https://www.unmannedtechshop.co.uk/blogs/knowledge-base/propeller-guide-tri-blade-vs-bi-blade-vs-quad-blade-fpv),
  [Unmanned Tech Motorhitze](https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-motor-overheating-troubleshooting-guide),
  [FirstQuadcopter Fullsend 6000](https://www.firstquadcopter.com/reviews/fullsend-6000mah-li-ion-review/),
  [FPVKnowItAll Race](https://www.fpvknowitall.com/fpv-shopping-list-five-inch-racing/),
  [MultiGP Klassen](https://www.multigp.com/class-specifications/), [X Class Rules](https://shop.arura.ai/pages/rules-standards),
  [Model Aviation X Class](https://www.modelaviation.com/x-class-drone-racing), [Tattu X-Class](https://www.tattuworld.com/resources/what-is-an-x-class-fpv-drone.html),
  [x-teamrc Motorgrößen](https://www.x-teamrc.com/fpv-motor-size-guide-2207-vs-3115-vs-4214-vs-4218/),
  [Grepow 10/13/15″](https://www.grepow.com/blog/10-inch-fpv-vs-13-inch-fpv-vs-15-inch-fpv-what-is-the-difference.html),
  [Radial Magnet Klassen](https://radialmagnet.com/neodymium-magnet-temperature-classes/), [DRL Racer4](https://www.coolthings.com/drl-racer4-street-90mph-racing-drone/)
- Baubericht-Datenbank: [RotorBuilds 30794](https://rotorbuilds.com/build/30794), [18513](https://rotorbuilds.com/build/18513),
  [19798](https://rotorbuilds.com/build/19798), [31710](https://rotorbuilds.com/build/31710/), [22860](https://rotorbuilds.com/build/22860),
  [26283](https://rotorbuilds.com/build/26283), [22654](https://rotorbuilds.com/build/22654)
- „Suchauszug“ bedeutet: Aussage stammt aus dem Suchindex, die Seite selbst war nicht abrufbar (meist HTTP 403 bei
  intofpv.com/getfpv.com) oder nur als Bild vorhanden. Diese Werte haben niedrige bis mittlere Sicherheit und sind nur
  als Stütze neben gemessenen Werten verwendet.
