# Kalibrierung Energie und Flugzeiten (Propwash 0.4.0, Just More Parts 1.0.0)

Stand: 2026-10-02. Öffentlich erscheinen alle Durchgänge als Propwash 0.4.0 und Just More Parts 1.0.0; die Abschnitte
nennen die internen Stände (Propwash 0.4.1–0.4.4, Just More Parts 1.0.0–1.0.5), in denen sie entstanden sind
(`docs/RELEASING.md`). Grundlage sind die drei Referenzen in diesem Ordner: [`motors-props.md`](motors-props.md),
[`power-video.md`](power-video.md) und [`builds.md`](builds.md). Anlass war die Rückmeldung, dass die
Flugzeiten nicht zu echten Zeiten passen und die Werte der Teile alle etwas danebenliegen. Harte Vorgabe für 0.4.2: **Die
Flugphysik bleibt unverändert** – Schub, Widerstand, Massen und Trägheiten der Simulation, Motorantwort, Rates/PID,
Handling und Topspeed sind bitgleich zu 0.4.1 (alle Bewegungs-Goldens unverändert). Kalibriert wurden ausschließlich
Energie- und Anzeigewerte.

Drei Durchgänge:

1. **Erste Kalibrierung** (0.4.2 / 1.0.4) – Energiebilanz `sim.EnergyModel`, vier Flugzeiten, Owner-Testfall.
2. **Nachkalibrierung nach dem Realismus-Review** (0.4.2 / 1.0.4, Dateien ersetzt) – der Review fand Klassenwerte und
   Owner-Tendenzen außerhalb der Referenzen und zu weite Prüffenster. Geändert: Blattinterferenz kleiner Props als
   Teillastverlust, Energiefaktoren der 2–4″-Props, Flugprofile Whoop/Race/Long Range/X-Class, Landekriterium,
   Fahrtwind in der Temperatur nach gemischtem Flug, drei Videoleistungen, engere Prüffenster und harte
   Owner-Prüfungen nach der Referenz.
3. **Korrektur nach dem zweiten Review** (0.4.3 / 1.0.5, §0) – Werkbank-Beschriftung, garantierte Reihenfolge der
   Flugzeiten, Vollgasströme kleiner Props und – mit Freigabe des Besitzers vom 30.09. – die flugwirksamen Daten der
   Teile und Presets, die der Review als unrealistisch meldete. Die Flugphysik (Code) ist unverändert; nur diese Teile
   fliegen anders. Nachtrag nach dem dritten Review (§0.7, 0.4.3 / 1.0.5 neu erstellt): Li-Ion-Long-Range im
   gemischten Flug, einheitlicher Luftwiderstand der Cinewhoop-Rahmen, Richtigstellungen.
4. **Restpunkte des vierten Reviews** (§0.8, Release Propwash 0.4.0 / Just More Parts 1.0.0) – 60-s-Temperaturen der
   korrigierten Cinewhoops nach ihren eigenen Prüfstandszeilen, die Geschwister-Props der korrigierten Familien mit
   derselben Korrektur (Blattzahl-Reihenfolge), Richtigstellungen in §0.4–§0.6, §2.4 und §2.5. Nur JMP-Daten ändern
   sich; Flug-Engine und Propwash-Daten sind unverändert.
5. **Akkuwarnungen** (§0.9, Release Propwash 0.4.0) – Warnung bei einem Schubstoß nur kurz, keine Warnung mit
   unendlichem Akku; Physik bitgleich.
6. **Nachtrag vom 02.10.** (§0.10, in Just More Parts 1.0.0) – der letzte offene Geschwister-Prop (`p89_30_3b_pc`) mit
   derselben Korrektur wie der 65-mm-Dreiblatt, Fahrtwind-Kühlung von zwei Toothpick-Motoren nach ihren eigenen
   Prüfstandszeilen. Wieder nur JMP-Daten.

## Nachtrag Propwash 0.5.0: Whoop-Widerstand, Prop-Verschleiß, Gewichtsanzeige

Entscheidung des Besitzers vom 05.10. nach Rückmeldungen eines Creators, der selbst fliegt. Flug-Engine unverändert;
geändert sind die Draufsicht der Whoop-Rahmen (Daten), das Schadensmodell der Props (Verschleiß, kein Flugmodell) und die
Anzeige.

- **Whoop-Rahmen** `propwash:whoop_65`, `justmoreparts:whoop_65_lite`, `justmoreparts:whoop_75_duct`: `top_area_m2`
  nach dem gemessenen Umriss (`flight-feel-gravity.md` §9). Geänderte Kalibrierzeilen: `propwash:whoop_65` Reisen 3:30 →
  3:38, gemischt 2:45 → 2:47, aggressiv 2:18 → 2:19, Topspeed 36 → 41 km/h, Strom gemischt 5,56 → 5,49 A;
  `justmoreparts:whoop75_1s` Reisen 5:58 → 6:11, gemischt 4:24 → 4:29, aggressiv 3:29 → 3:31, Topspeed 41 → 45 km/h;
  Owner-Testfall 3:19 / 3:07 / 2:03 / 1:40 (vorher 3:19 / 3:01 / 2:02 / 1:39), Topspeed 41 → 47 km/h – Schweben,
  Schwebegas 43,8 % und Ströme im Schweben gleich. Owner-Variante 3-Blatt aggressiv 1:57 liegt jetzt auf der Oberkante
  ihres Fensters (Markierung „!“ in `calibration.txt`, wie vorher schon 2-Blatt). Alle anderen 25 Presets und alle
  Einzeltausch-Varianten ohne Whoop-Rahmen sind byte-gleich (`calibration.txt`, `motion.txt`).
- **Nachtrag 08.10. (`flight-feel-gravity.md` §10):** Whoop-Draufsicht nach Rumpf und Blattfläche statt nach dem Umriss
  (`propwash:whoop_65` 0,0059 → 0,0041 m², Just More Parts Whoop 65 Lite/75/85 0,0039/0,0046/0,0065) und im
  angetriebenen Sinkflug ohne doppelten Blätter-Anteil. `calibration.txt`: nur Whoop-Zeilen – `propwash:whoop_65`
  Reisen 3:38 → 3:43, gemischt 2:47 → 2:48, aggressiv 2:19 → 2:20, Topspeed 41 → 47 km/h; Owner-Testfall
  3:19 / 3:11 / 2:03 / 1:41, Topspeed 47 → 54 km/h (3-Blatt aggressiv 1:58, weiter knapp über der Oberkante);
  `justmoreparts:whoop75_1s` Reisen 6:11 → 6:35, gemischt 4:29 → 4:38, aggressiv 3:31 → 3:35, Topspeed 45 → 57 km/h;
  `justmoreparts:whoop85_2s` Reisen 7:20 → 7:39, gemischt 5:26 → 5:34, aggressiv 4:19 → 4:22, Topspeed 57 → 77 km/h.
  Schweben, Schwebegas und Ströme im Schweben gleich; alle anderen Presets byte-gleich. `motion.txt` ändert sich für
  alle Bauarten in den Sinkflug-Abschnitten (Engine-Korrektur).
- **Prop-Verschleiß** nach Material (`prop-wear.md`): ändert keine Flugzeit und keine Bewegung mit neuen Teilen; nur
  `wear:*` und `worn:*` in `builtin_regression.txt`.
- **Gewicht:** Werkbank, Übersicht und Drohnen-Tooltip zeigen Trockengewicht und Abfluggewicht (mit Akku); der
  Maximalstrom steht in den Werkbank-Details (die Warnung „Akku überlastet“ bleibt). Prüfung des typischen 7″ in
  `builds.md` §17a: keine Datenänderung.
- **Endprüfung Release 0.5.0** (06.10.): `tools/calibration.sh` dreimal, je 10/10. A = Propwash 0.4.0 (`v0.4.0`) mit
  Just More Parts 1.0.0, B = Propwash 0.5.0 mit Just More Parts 1.0.0, C = Propwash 0.5.0 mit Just More Parts 1.1.0.
  A → B (nur Propwash-Code) ändert ausschließlich Zeilen mit dem Rahmen `propwash:whoop_65`: `calibration.txt` 6 Zeilen
  (Preset und die fünf Owner-Varianten), `motion.txt` 27 von 1245 Zeilen, `fall.txt` 2 Zeilen; dazu stehen in `fall.txt`
  zwei neue Zeilen für `justmoreparts:whoop75_1s` (neuer Testfall, keine Änderung am Flugmodell). B → C (nur JMP-Daten)
  ändert ausschließlich die JMP-Whoop-Rahmen: `calibration.txt` 1 Zeile (`justmoreparts:whoop75_1s`), `motion.txt`
  32 Zeilen (`justmoreparts:whoop_65_lite` 1, `justmoreparts:whoop_75_duct` 31), `fall.txt` die beiden Whoop-75-Zeilen.
  Alle übrigen Zeilen aller drei Dateien sind byte-gleich; mit den Standardwerten der Welt-Regeln ist die
  Whoop-Draufsicht damit die einzige Änderung am Flugmodell gegenüber 0.4.0.

## Nachtrag 08.10.: Abgleich mit einem echten 75-mm-Whoop auf 1S (Prüfung, keine Änderung)

Anlass: Rückmeldung des Besitzers nach dem ersten Flug mit einer echten BetaFPV Meteor75 Pro II O4 Wide (1102 21000 KV,
Gemfan 1811 mit 45 mm, DJI O4 Wide, LiHV 1S 480–680 mAh): Die echte Drohne gehe deutlich mehr ab als der 75-mm-Whoop der
Mod. Geprüft wurde, ob die Flugphysik kleine 1S-Brushless-Whoops systematisch unterschätzt. Ergebnis: **Schub, KV,
Spannungseinbruch und Prop-Wirkungsgrad stimmen; Flug-Engine und Daten bleiben unverändert** (Goldens, `motion.txt` und
`calibration.txt` byte-gleich). Neu ist nur die Prüfung `sim.WhoopThrustTableTest`.

- **Prüfstand:** Die veröffentlichte Lastkennlinie des Motors (BetaFPV „1102 Brushless Motors (2026)“, Bild „Load
  Performance“, 30–100 % Gas bei 3,92–3,81 V am Netzteil, https://betafpv.com/products/1102-brushless-motors-2026) wird mit
  physikalisch plausiblen Teile-Daten nachgerechnet (R_Motor 0,11 Ω, R_ESC 0,05 Ω, c_t 0,196, c_p 0,17 für den 1811 mit
  1,1″ Steigung; gemeldete Drehzahl × 7/6 wie bei allen 9N12P-Motoren, `motors-props.md` §1.4). Schub −7 … +7 %, Drehzahl
  −3 … +2 % über 40–100 % Gas, Strom ab 80 % −9 … +1 %; im Teillastbereich zieht der echte Motor mehr Strom, das deckt
  `EnergyModel` ab. `WhoopThrustTableTest` hält die Kennlinie fest (Schub ±10 % und Drehzahl ±4 % ab 40 %, Strom ±10 % ab
  80 %).
- **Akku:** LAVA II 1S 480/580/680 bei 27/33/39 A (Entladekurven des Herstellers,
  https://betafpv.com/products/lava-ii-1s-battery) mit 7,9–11 mΩ Zellwiderstand: die ersten 10–15 s auf ±0,03 V genau,
  danach fällt die LiHV-Kurve von Propwash schneller (bis −0,1 V nach 30 s, −0,2 … −0,4 V im letzten Viertel). Wirkt auf
  alle LiHV-Akkus gleich und nur in der letzten Akkuminute; nicht geändert, weil die Flugzeit-Kalibrierung daran hängt.
- **Ganze Drohne** (Teile-Daten nach Hersteller, Abfluggewicht 55,6 g gegen 56,3 g gemessen): Schwebegas 28 % mit vollem
  Akku (Test: ≈ 28 %, https://oscarliang.com/betafpv-meteor75-pro-dji-o4-wide/), Schub/Gewicht 5,2 (Netzteil-Kennlinie
  ≈ 4,7), Flugzeit 580 mAh Schweben 5:13 / Reisen 4:50 / gemischt 3:29 / aggressiv 2:43 (Hersteller „bis 5 min“, Test
  4:00–4:30 entspannt, 3–4 min hart). Passt.
- **Was den Unterschied zum 75-mm-Preset von Just More Parts ausmacht:** (1) dessen Teile – 0802-Motoren mit gut halbem
  Schub, 450 mAh mit rund dreimal so hohem Zellwiderstand; (2) das abgeleitete Tuning: `PidAutoTune` begrenzt die
  Bandbreite bei Whoops über ω_c ≤ 4/τ_m (τ_m = 40 ms) und kommt für diesen Aufbau auf P 15 / I 12 / D 37 (Rollsprung
  0 → 600 °/s t90 111 ms); das Werks-Tuning der echten Drohne (P 40 / I 67 / D 26, Betaflight-Dump des Herstellers) fliegt
  auf derselben Physik stabil mit t90 42 ms und 8 % Überschwingen; (3) der Rumpfwiderstand im schnellen Flug (Draufsicht
  nach dem Umriss samt Prop-Scheiben, Abschnitt oben): Topspeed 54 km/h gegen eine Testerschätzung von ≈ 80 km/h.
- **Offen, nicht geändert:** (2) und (3) ändern die Bewegungsdaten aller Whoops und brauchen eine Messung bzw. die Freigabe
  des Besitzers: Bandbreiten-Grenze der Whoop-Ableitung lockern oder an echten BNF-Tunings ausrichten; Prop-Scheiben im
  angetriebenen Flug nicht als Platte rechnen, sobald ein gemessener Topspeed vorliegt (gerade Strecke mit Vollgas,
  Zeit aus dem Video). Ebenso notiert: die ESC-Eingangsgrenze von 80 % der Dauerangabe begrenzt einen 12-A-AIO mit
  frischem 680-mAh-Akku knapp (≈ −7 % Spitze), ein echter Bluejay-ESC begrenzt nicht.

## Nachtrag 08.10. (0.5.0): Steigflug-Schub nach Impulstheorie

Das Flugmodell rechnet den Schub im Steigflug jetzt mit Impuls- und Blattelementtheorie statt linear
(`flight-feel-gravity.md` §11). Anlass war der Abgleich mit 19 echten Betaflight-Logs (`docs/tools/blackbox-calibration.md`).

**Auswirkungen auf die Energie-Kalibrierung** (`tools/calibration.sh`, eingebaute Presets):
- Unverändert: Schweben, Schwebegas, Ströme und Schub/Gewicht (bei `λ = 0` bitgleich).
- Reise wird 0,5–1,3 % länger: Whoop 65 3:43 → 3:45, Cinewhoop 3″ 5:09 → 5:13, 5″ 7:56 → 7:59, Race 8:35 → 8:37,
  7″ 18:06 → 18:10.
- Gemischt und aggressiv werden 0–2 s länger.
- Topspeed der Werkbank steigt um 0–1 km/h.
- Alle Fenster halten.

**Just More Parts:**
- Mit dem neuen Modell lag `toothpick35_6s` über seinen Toothpick-Fenstern: gemischt 9:29 bei höchstens 9:27, Reise
  über 13,0 min.
- Korrektur in 1.1.0: c_p beider 89-mm-Props × 1,012 (Teilekatalog §13.4). Jetzt gemischt 9:24 und Reise 12,9 min;
  die Blattzahl-Reihenfolge im 3,5″-Cinewhoop bleibt.

**Offen aus dem Abgleich (Teiledaten, nicht geändert):**
- Die Beiwerte der Props 45–76 mm sind zu niedrig: `propwash:p3_3b` und die Just-More-Parts-Familien 45, 51, 65 und 76 mm.
- Echte Logs ergeben c_t etwa × 1,7–2,5 (Pavo Pico, 45-mm-Dreiblatt: c_t ≈ 0,216 gegen 0,0894).
- Das deckt sich mit den Referenzwerten aus §2.5. Für 0.5.1 bzw. Just More Parts 1.2 mit c_p aus Prüfstandsdaten,
  weil die Korrektur Strom, Motorwärme und Flugzeiten verschiebt.

## 0. Dritter Durchgang: Propwash 0.4.3 / Just More Parts 1.0.5

Entscheidung des Besitzers (30.09.): Die Flug-**Engine** (Schub-, Widerstands- und Trägheitsmodell, Motorantwort,
Flugcontroller) bleibt wie sie ist. Freigegeben ist die Korrektur der **Daten** der einzelnen Teile und Presets, deren
flugwirksame Werte der Review als unrealistisch gemeldet hat (Review Punkt 4): Topspeed der Cinewhoops, Schub des
3,5″-Cinewhoops, Schwebegas von 75-mm-Whoop, 2,5″-Cinewhoop und 4″-Li-Ion-Long-Range, Massen von
`justmoreparts:p76_30_2b_pc` und `justmoreparts:m2807_1500`, Temperatur des 1103 im Toothpick. Alle anderen Teile und
Presets fliegen bitgleich (§0.6).

### 0.1 Werkbank-Beschriftung (Review Punkt 1)

Die Kennzahlen-Zeile der Werkbank hat 119 GUI-Pixel (`WorkbenchLayout.STATS_X0/X1`, je 4 px Rand). „Flight time (mixed)“
ist 87 px breit, „5:40 min“ 38 px – zusammen 125 px, der Wert überdeckte das Ende der Beschriftung. Das Deutsche war
ebenfalls betroffen: „Flugzeit gemischt“ 85 px neben „17:50 min“ 44 px (Long-Range-Presets) = 129 px.

- Beschriftung neu: **„Flight (mixed)“ 65 px**, **„Flug gemischt“ 67 px**. Mit dem breitesten denkbaren Wert
  „88:58 min“ (44 px) und 4 px Abstand bleiben 71 px Platz.
- Die Werkbank kürzt eine Beschriftung, die trotzdem nicht passt, mit „…“ auf den Platz links vom Wert
  (`WorkbenchLayout.statLabelWidth`); Beschriftung und Wert überlappen nie mehr.
- Geprüft für beide Sprachen und alle sechs Kennzahlen: JUnit `content.WorkbenchStatsFitTest` misst mit den
  Glyphenbreiten der Minecraft-Schrift (aus `assets/minecraft/font` des Spiel-Jars) die breitesten Werte (8888 g,
  88,8 : 1, 100 %, 888 km/h, 88:58 min, 888 A, „–“) und alle Presets; der Selbsttest „content: Werkbank-Kennzahlen passen
  neben ihre Werte (Spielschrift)“ misst dasselbe mit der Schrift des laufenden Spiels, im Fabric-Bildschirmfoto-Lauf
  zusätzlich auf Deutsch. Die Breiten sind GUI-Pixel und damit bei jeder GUI-Skala gleich; die Bildschirmfotos
  `workbench_freestyle.png` (GUI 3), `workbench_freestyle_gui2.png`, `workbench_freestyle_gui4.png` und
  `workbench_freestyle_de.png` zeigen es.

### 0.2 Reihenfolge der Flugzeiten (Review Punkt 2)

Vorher konnten schwache Aufbauten (Schubstoß auf Vollgas begrenzt, billige Leerlaufanteile, Reisen unter Schwebeleistung
durch Translationsauftrieb) gemischt länger fliegen als Reisen oder aggressiv länger als gemischt. Über alle
Einzeltausch-Varianten der 27 Presets (jedes Teil jeder Kategorie einzeln getauscht, 1173 baubare Varianten mit
Flugzeit): 5 × aggressiv > gemischt, 13 × gemischt > Reisen, 10 × Reisen > Schweben.

Umsetzung (`EnergyModel.Points#evaluate`): Jedes Profil ist mindestens so fordernd wie das ruhigere. Je Ladungsschritt
gilt für Akkustrom, Last des ruhigen Abschnitts, mittlere Last und Motorwärme
`Reisen ≥ Schweben`, `gemischt ≥ Reisen`, `aggressiv ≥ gemischt` (Maximum). Weil alle Profile dieselben Ladungsschritte
durchlaufen, der Zeitschritt mit dem Strom fällt und die Spannung unter Last mit dem Strom sinkt, folgt daraus
`aggressiv ≤ gemischt ≤ Reisen ≤ Schweben` für **jeden** Aufbau – nicht nur für die geprüften. Physikalisch: Ein
Pilot, der mit einem schwachen Aufbau „gemischt“ fliegt, fliegt mindestens so hart wie beim Reisen; der Vorteil des
Translationsauftriebs geht beim Reisen in Kurven und Höhenhalten auf.

Property-Tests: Propwash `sim.FlightTimeOrderTest` (alle baubaren Kombinationen eingebauter Teile mit und ohne Zubehör,
alle Einzeltausch-Varianten der eingebauten Presets und – mit `tools/calibration.sh` – aller 27 Presets mit JMP-Teilen)
und JMP `bench.FlightTimeOrderTest` (1177 Varianten über die öffentliche API). Ergebnis: 0 Verstöße. Gegen die Klassen
von 0.4.2 schlägt der JMP-Test fehl – er findet die gemeldeten Fälle.

Wirkung auf die Presets: keine (alle Presets hielten die Reihenfolge schon). Beispiele aus dem Review: PW `freestyle_5`
mit PW `p3_3b` (Schub/Gewicht 1,4) vorher Schweben 5:15, Reisen 5:20, gemischt 5:43, aggressiv 5:54, jetzt alle vier
5:15 – der Aufbau kann kaum mehr als schweben; PW `whoop_65` mit `m1102_13500` vorher Schweben 2:54, Reisen 2:53,
gemischt 3:23, aggressiv 2:58, jetzt 2:54 / 2:53 / 2:53 / 2:52.

### 0.3 Vollgasströme kleiner Props (Review Punkt 3)

Der Größenfaktor `k_Prop` (§3.1) senkt den Strom kleiner Props, weil deren Gütezahl in den Daten zu niedrig ist. Er galt
auch bei Vollgas und drückte den Vollgasstrom unter die Prüfstände (`cinewhoop_3` 49 statt ≈ 68 A, `toothpick35_6s` 33
statt 46 A). Neu: ein zweiter Faktor für Volllast `k_Voll(D)` (31 mm 0,84 · 51 mm 1,05 · 76–102 mm 1,0 · ab 130 mm =
`k_Prop`), übergeblendet mit dem Quadrat des Schubanteils über dem Schweben:

`k(ω) = k_Prop + (k_Voll − k_Prop) · x²`, `x = clamp((ω² − ω_S²) / (ω_Voll² − ω_S²), 0, 1)`

mit `ω_Voll` = Drehzahl bei Vollgas im Stand und vollem Akku (neues Feld `AirframeSpec.fullLoadOmega`). Schweben und
Reisen bleiben praktisch unverändert (x ≈ 0), der Vollgasstrom folgt den Prüfständen. 31 mm (65-mm-Whoops, Owner-Fall)
und ab 130 mm (5″ bis X-Class) ändert sich nichts. Im Spiel nutzt der Akku-Ladezustand denselben Faktor (Mittel der
vier Motoren).

| Aufbau | Vollgas 0.4.2 | Vollgas 0.4.3 | je Motor | Referenz |
|---|---|---|---|---|
| `justmoreparts:whoop75_1s` | 13,5 A (34 g/Motor, 2,57 g/W) | 21,3 A (39 g, 1,99 g/W) | 5,3 A | 1S 5,5–6 A bei 30–35 g am Netzteil, in der Luft 5–15 % weniger; `builds.md` §0 15–20 A (Schätzung) |
| `justmoreparts:whoop85_2s` | 26,1 A (96 g, 2,13 g/W) | 31,8 A (96 g, 1,74 g/W) | 8,0 A | 2S 9,3 A bei 79 g am Netzteil (M1002 14000 KV), in der Luft 7,9–8,8 A; 1,2–1,8 g/W |
| `justmoreparts:toothpick2_2s` | 25,4 A | 33,0 A | 8,3 A | EX1103 11000 KV 2S 9,2 A (Netzteil), Klasse 7–11 A |
| `justmoreparts:toothpick3_4s` | 30,0 A | 40,8 A | 10,2 A | 0.4.1 38,4 A; Klasse 30–60 A |
| `justmoreparts:toothpick35_6s` | 33,1 A | 48,3 A | 12,1 A | 0.4.1 46,3 A |
| `propwash:cinewhoop_3` | 49,2 A | 67,4 A | 16,8 A | F1404 4600 KV 17,5 A |
| `justmoreparts:cinewhoop25_4s` | 21,1 A | 56,1 A | 14,0 A | mit Datenkorrektur §0.4 |
| `justmoreparts:cinewhoop35_6s` | 18,3 A | 63,0 A | 15,8 A | mit Datenkorrektur §0.4; `builds.md` §0 60–100 A |
| `justmoreparts:lr4_6s_lion` | 23,7 A | 47,0 A | 11,8 A | mit Datenkorrektur §0.4 |

Zwei Profile passen dazu (Energie, keine Flugphysik): 75–85-mm-Whoops stoßen mit 3 : 1 statt 3,5 : 1 (mit realistischen
Prop-Beiwerten sind 3,5 : 1 drei Viertel des Maximalschubs; `builds.md` §0: 75 mm gemischt 4–5:30, aggressiv 3–4 min),
Cinewhoops fliegen gemischt 83/12/5 statt 88/7/5 (Verhältnis gemischt/Schweben jetzt 1,3; `builds.md` §17 Cinematic
1,2–1,4, vorher 1,18–1,20).

### 0.4 Flugwirksame Datenkorrekturen (Review Punkt 4, Freigabe des Besitzers)

Grundsatz: Die Prop-Beiwerte der Propwash-Daten liegen absolut zu niedrig (`motors-props.md` §0 Befund 1: 3″ und 31 mm
um den Faktor 2,5–4), die Gütezahl stimmt. Die korrigierten Props bekommen deshalb `c_t · a` und `c_p · a^1,5` – die
Gütezahl `FM ∝ c_t^1,5 / c_p` bleibt gleich, die Schwebeleistung auch, aber der Motor sitzt an der richtigen Stelle
seiner Kennlinie (weniger Drehzahl, mehr Moment), sodass Schwebegas, Vollgasschub und Vollgasstrom den Prüfständen
folgen.

| Teil | Wert | vorher | nachher | Referenz |
|---|---|---|---|---|
| `propwash:cinewhoop_3` (Rahmen) | Stirnfläche / Draufsicht | 0,020 / (2,5 × 0,020 =) 0,050 m² | 0,012 / 0,020 m² | keine gemessene Fläche in den Referenzen; vorher (Standard für Ducts 2,5 × Stirnfläche) größer als die ganze Draufsicht samt Propscheiben, jetzt 1,67 × Stirnfläche (Propscheiben offen); Prüfung über die Höchstgeschwindigkeit |
| `justmoreparts:cw_25_duct` | Stirnfläche / Draufsicht | 0,014 / 0,035 m² | 0,009 / 0,015 m² | wie oben, 112 mm Radstand |
| `justmoreparts:cw_35_duct` | Stirnfläche / Draufsicht | 0,020 / 0,050 m² | 0,0137 / 0,0228 m² | Potenzgesetz durch die beiden Geschwister (Fläche ∝ Radstand^1,21, Draufsicht 1,67 × Stirnfläche), 158 mm Radstand; erste 0.4.3-Fassung 0,018 / 0,013 (§0.7) |
| `justmoreparts:p40_17_3b_pc` | c_t / c_p | 0,0915 / 0,0733 | 0,1555 / 0,1625 (a = 1,7) | 40 mm 3-Blatt 0,18–0,22 / 0,21–0,34 (`motors-props.md` §4.1) |
| `justmoreparts:p65_22_4b_bi` | c_t / c_p | 0,0937 / 0,0814 | 0,2061 / 0,2656 (a = 2,2); seit §0.8 0,1874 / 0,1961 | Duct-Props 0,26–0,46 / 0,18–0,47, 2″ 4-Blatt 0,34 / 0,43–0,47 (c_t unter der Referenz: §0.5) |
| `justmoreparts:p89_25_5b_pc_duct` | c_t / c_p | 0,1065 / 0,0762 | 0,2662 / 0,3012 (a = 2,5) | Duct-Props 0,26–0,46 / 0,18–0,47 |
| `justmoreparts:p102_30_3b_gfn` | c_t / c_p | 0,0946 / 0,0525 | 0,1703 / 0,1268 (a = 1,8) | 4023-3 (P/D 0,58) 0,15–0,17 / 0,08–0,10, P/D 0,75 höher |
| `justmoreparts:m2004_1700` | Wicklungswiderstand / Fahrtwind-Kühlung | 0,3114 Ω / 0,00013 | 0,221 Ω / 0,0002 W/(K·rad/s); seit §0.8 Kühlung 0,000236 | F2004 1700 KV 221 mΩ (§2.4 der 0.4.2); Glocke nach 60 s Vollgas nach der eigenen F2004-Zeile (`motors-props.md` §6.1: 61–72 °C bei 8 °C, ΔT 53–64 K, bei 25 °C ≈ 78–89 °C; die erste Fassung argumentierte mit F1408 und 5″, §0.8); Datenregel bis 2,5 × Standard (0,000236) |
| `justmoreparts:m1103_11000` | Fahrtwind-Kühlung | 0,000055 | 0,000022 W/(K·rad/s) | 1103–1404 nach 60 s Vollgas 45–68 °C (`motors-props.md` §6.1) |
| `justmoreparts:p76_30_2b_pc` | Masse | 1,2 g | 0,8 g | Gemfan 3018 0,75–0,84 g (`motors-props.md` §4.7) |
| `justmoreparts:m2807_1500` | Masse | 47,5 g | 53 g | 2807 ≈ 53 g (Stator-Regel `motors-props.md` §2.4) |

Wirkung auf die gemeldeten Werte:

| Aufbau | Wert | 0.4.2 | 0.4.3 | Referenz |
|---|---|---|---|---|
| `propwash:cinewhoop_3` | Topspeed | 62 km/h | 89 km/h | 3″ 80–100 km/h |
| `justmoreparts:cinewhoop25_4s` | Topspeed / Schwebegas / S/G | 55 km/h / 47,5 % / 4,0 | 85 km/h / 32,2 % / 5,8 | 80–100 km/h / 23–33 % / 4–6 |
| `justmoreparts:cinewhoop35_6s` | Topspeed / Schwebegas / S/G | 60 km/h / 47,8 % / 4,1 | 89 km/h / 29,1 % / 6,8 | 100–150 km/h mit 2030–2450 KV, mit 1700 KV drehzahlskaliert 92–104 km/h (§0.7, §2.5) / ≈ 28 % / 5–7 |
| `justmoreparts:cinewhoop35_6s` | Vollgasschub je Motor / Vollgasstrom | 357 g / 18,3 A | 594 g / 63,0 A | 580–870 g / 60–100 A (`builds.md` §0), 80–108 A (20–27 A je Motor) |
| `justmoreparts:whoop75_1s` | Schwebegas / S/G / Vollgasstrom | 43,6 % / 4,1 / 13,5 A | 34,9 % / 4,8 / 21,3 A | 31–33 % (Air75, 23000 KV) / 4–6 / 15–20 A |
| `justmoreparts:lr4_6s_lion` | Schwebegas / S/G / gemischt | 51,0 % / 3,6 / 17:50 | 37,0 % / 4,5 / 16:56 | 7″ Li-Ion 26–30 % / 4–7 (für 4″ siehe §2.5) / gemischt/Schweben ≈ 2 (§0.7) |
| `justmoreparts:toothpick2_2s` | Motor nach 60 s Vollgas | 38 °C | 53 °C | 1103–1404: 50–70 °C |
| `justmoreparts:p76_30_2b_pc` / `m2807_1500` | Masse | 1,2 g / 47,5 g | 0,8 g / 53 g | siehe oben |

Flugzeiten, Ströme und Temperaturen aller Presets vorher/nachher: §5. Alle Presets liegen in ihren Klassenfenstern
(§1); Shift-Hilfen, Prüfstands-Korridore und Pro/Contra-Prüfungen in JMP sind an die neuen Werte angepasst (JMP
`docs/design/parts-catalog.md` §13).

### 0.5 Warum nicht mehr?

- `whoop75_1s`: Mit a = 2,2 (c_t 0,20, mitten in der Referenz) schwebte er mit 31,7 %, aber der 6-A-AIO
  (`aio_whoop_1s_6a`, Eingangsgrenze 4,8 A je Motor) begrenzt schon ab a = 1,9 bei Vollgas (`ESC_CURRENT_LIMITED`, mit
  1S 550 ab a = 1,8). a = 1,7 ist der größte Wert ohne Begrenzung: 34,9 %. Die 31–33 % der Referenz gelten für den
  Air75 mit 23000 KV; der 0802 hat 19000 KV.
- `lr4_6s_lion`: Ein 4″-Prop am 1700-KV-Motor auf 6S schwebt die 443 g mit ≈ 13 000 U/min – ein Drittel der
  Leerlaufdrehzahl; mit Referenz-Beiwerten sind 37 % das physikalische Ergebnis dieser Kombination (schwerer 6S-3000-
  Li-Ion-Akku, kleine Props). Die 26–30 % der Referenz gelten für 7″. Mit dem höheren Schub kann der Aufbau im
  Schubstoß mehr Strom ziehen als der Li-Ion-Akku im Flugbetrieb sinnvoll liefert; das regelt die Energiebilanz (§0.7).
- `cinewhoop35_6s`: Mit a = 3,0 (72 A, 624 g) überschreitet der 2004 seine Drosselgrenze im Dauervollgas
  (`MOTOR_THERMAL`); a = 2,5 trifft Schub und die Untergrenze des Stroms nach `builds.md` §0. Die Höchstgeschwindigkeit
  begrenzt die Drehzahl des 1700-KV-Motors, nicht der Luftwiderstand (§0.7).
- `p65_22_4b_bi`: c_t 0,187 (seit §0.8; vorher 0,206) liegt unter der Duct-Referenz (0,26–0,46) und dem 2″-Vierblatt
  (0,34). Mehr c_t bei derselben Gütezahl bräuchte mehr c_p – der unveränderte `m1404_3800` (auch im `toothpick3_4s`)
  läge dann nach 60 s Vollgas über seiner F1404-Zeile (ΔT 25–44 K, §0.8); mehr c_t bei gleichem c_p hieße eine Gütezahl
  über der 2″-Vierblatt-Referenz (0,30–0,33). Gewählt ist die Gütezahl 0,33 (oberer Rand der 2″-Vierblatt-Referenz,
  innerhalb der Duct-Referenz 0,29–0,60) mit c_t = 2,0 × Datenwert: Schwebegas 33,1 %, S/G 6,0, 68 °C.

### 0.6 Beweis: alle anderen Teile und Presets fliegen bitgleich

- **Propwash-Goldens** (`airframe_golden.txt`, `builtin_regression.txt`): geändert sind nur die Zeilen des
  `cinewhoop_3`-Rahmens – 30 Spezifikationszeilen `cinewhoop_3/<Motor>/<Prop>`, `acro`/`angle`/`drop`/`preset` des
  Cinewhoop-Presets und seiner drei Zubehör-Varianten, `worn`, `full`, `full-thermal` des Cinewhoops – sowie die
  Analyse-Zeilen der Kombinationen mit `p3_3b` (76 mm, Vollgasstrom). Alle Spezifikations- und Bewegungszeilen von
  Whoop, Freestyle, Race und Long Range und alle `wear`-Zeilen sind unverändert.
- **Alle Presets mit allen Einzeltausch-Varianten** (neues Werkzeug `sim.MotionFingerprintTest`, läuft mit
  `tools/calibration.sh` und schreibt `build/calibration/motion.txt`: je Variante Hash der Flugphysik-Parameter wie in
  `airframe_golden.txt` und der Flugbahnen `acro`, `angle`, `drop` wie in `builtin_regression.txt`). 0.4.2 + JMP 1.0.4
  gegen 0.4.3 + JMP 1.0.5, 1245 Varianten (27 Presets, jedes Teil jeder Kategorie einzeln getauscht):
  **1004 Varianten ohne korrigiertes Teil alle bitgleich**, 240 Varianten mit korrigiertem Teil geändert (je Teil:
  `m2004_1700` 64, `cinewhoop_3` 46, `p102_30_3b_gfn` 46, `p89_25_5b_pc_duct` 38, `p65_22_4b_bi` 37, `p40_17_3b_pc` 34,
  `cw_25_duct` 34, `cw_35_duct` 34, `m1103_11000` 30, `p76_30_2b_pc` 5, `m2807_1500` 5), 1 Variante mit korrigiertem
  Teil unverändert (Toothpick mit 1S-AIO am 2S-Akku, startet nicht). Gegen die erste 0.4.3-Fassung (§0.7) sind nur die
  34 Varianten mit `cw_35_duct` anders. Die Energiebilanz (§0.2, §0.3, §0.7) ändert nur Analyse- und Akkuwerte;
  Flugbahnen, Drehzahlen und die übrige Telemetrie der Flugphysik bleiben gleich.
- Zusätzlich (Werkzeug des dritten Reviews, alle flugwirksamen Felder von `AirframeSpec` inklusive Thermik, Schwerpunkt
  und Prop-Steifigkeit, Flüge acro, abgenutzt, angle, drop und Vollgas bis leer mit und ohne Thermik, 1290 Varianten
  mit Zubehör): ohne korrigiertes Teil keine Abweichung in Parametern und Flugbahnen. Im Lauf „Vollgas im Stand bis der
  Akku leer ist“ unterscheiden sich 99 Varianten ohne korrigiertes Teil, weil der Akku im Spiel dem Vollgasstrom aus
  §0.3 folgt: im Akkustand und im Zeitpunkt von `BATTERY_LOW`, `BATTERY_CRITICAL` und `PACK_DEPLETED`. **Richtigstellung
  (vierter Review):** Das ist nicht alles. Erreicht der Akku im Spiel dabei `SOC_MIN`, löst die Brownout-Abschaltung aus
  (`BROWNOUT`, danach `DISARMED`) – der motorisierte Flug bei Dauervollgas endet dann früher. Der vierte Review fand das
  bei 16 von 2328 Aufbauten ohne korrigiertes Teil (alle mit 45–89-mm-Props: `p45_17_4b_bi`, `p45_19_3b_pc`,
  `p51_20_3b_pc`, `p76_25_3b_hard`, `p3_3b`, `p89_30_3b_pc`), z. B. `tp_3_x`/`aio_20`/`m1404_4600`/`p3_3b`/`digital_lite`/
  `b4s_650` 150 s → 127,5 s und `whoop_85_duct`/`m1102_13500`/`p45_17_4b_bi`/`b2s_300_hv` 138,0 s → 121,9 s. Bis zur
  Abschaltung ist die Flugphysik gleich; das ist das beabsichtigte Energieverhalten (der Akku liefert den realistischen
  Vollgasstrom und ist entsprechend früher leer).
- Geändert sind damit genau: `propwash:cinewhoop_3` (Preset und Rahmen) und die JMP-Presets `whoop75_1s`,
  `toothpick2_2s`, `cinewhoop25_4s`, `cinewhoop35_6s`, `lr4_6s_lion` sowie jede Variante, die eines der elf Teile
  enthält. Alle übrigen 21 Presets (auch „Deadcat LR 7″ · 8S“ und der Owner-Whoop) **fliegen** unverändert. Sie
  **rechnen** aber nicht alle gleich: Durch §0.3 ändern sich bei `whoop85_2s` gemischt/aggressiv 4:58 / 3:46 → 5:26 /
  4:19 und der Vollgasstrom 26,1 → 31,8 A, bei `toothpick3_4s` 6:07 / 4:27 → 5:54 / 4:07 und 30,0 → 40,8 A, bei
  `toothpick35_6s` 8:50 / 6:13 → 8:31 / 5:45 und 33,1 → 48,3 A; durch §0.7 gemischt von `propwash:long_range_7`,
  `lr7_deadcat_lion` und `lr8_6s_lion`. Unverändert in Flug und Analyse sind `propwash:whoop_65`, der Owner-Whoop,
  alle 5″-Presets, `lr6_6s`, „Deadcat LR 7″ · 8S“ und die vier X-Class-Presets.

### 0.7 Nachtrag nach dem dritten Review (0.4.3 / 1.0.5 neu erstellt)

Der dritte Review bestätigte §0.1–§0.6 und meldete vier Punkte. Behoben ohne Änderung der Flug-Engine; flugwirksam
geändert ist nur der freigegebene Rahmen `justmoreparts:cw_35_duct`.

**1. Li-Ion-Long-Range im gemischten Flug (Energiebilanz).** Mit den Referenz-Beiwerten des 4″-Props (§0.4) erreicht
`justmoreparts:lr4_6s_lion` den Schubstoß des Long-Range-Profils (26 % der Zeit bei 3,8 : 1) und zog dabei ≈ 31,5 A aus
dem 6S1P-3000-mAh-Li-Ion-Akku – 10,5 C, an der Zellgrenze. Gemischt fiel von 17:50 auf 11:52 min (0,65 W/g,
gemischt/Schweben 2,7). Die erste 0.4.3-Fassung prüfte Long Range mit Props bis 110 mm deshalb gegen die
Toothpick-Fenster und begründete das mit einem Verhältnis gemischt/Schweben von 2,0 – das stimmte nicht (2,7). Beides
ist zurückgenommen.

Die Ursache ist die Chemie, nicht die Größe: Auch die 7″- und 8″-Li-Ion-Presets lagen bei 2,6–3,0, die Referenz nennt
für 7″ Long Range ≈ 2 (`builds.md` §11). Belegt ist: Li-Ion „≈ 5 C im Flugbetrieb sinnvoll“, praktischer Dauerstrom
eines 6S1P-21700-Packs für Long Range 20–25 A (4000–4500 mAh, also 4,4–6,3 C), Zellgrenze 10–12 C (`power-video.md`
§2.3); „Li-Ion nicht für harte Manöver“ (`builds.md` §0). Neu (`EnergyModel.LOW_RATE_PRACTICAL_C`): Li-Ion-artige Zellen
(`usable_fraction` ab 0,87) werden im Schubstoß des **gemischten** Flugs höchstens mit 6 C belastet (oberes Ende des
praktischen Dauerstroms), im **aggressiven** Flug wie bisher höchstens mit dem Dauerstrom des Packs (C-Rate ×
Kapazität, Zellgrenze). Die Reihenfolge der Flugzeiten bleibt garantiert (§0.2). Nur Analysewerte ändern sich (gemischte
Flugzeit, mittlerer Strom, Landespannung, Motortemperatur nach gemischtem Flug); Flugphysik, Goldens und Akku im Spiel
sind unberührt, LiPo-Aufbauten ebenso.

| Aufbau | Akku | gemischt vorher → jetzt | W/g | gemischt/Schweben | Referenz |
|---|---|---|---|---|---|
| `propwash:long_range_7` | 6S 4000 Li-Ion | 11:02 → 15:02 | 0,49 → 0,36 | 2,6 → 1,9 | 7″ 6S 4000 Li-Ion 15–22 min (`power-video.md` §5, [Q109]) |
| `justmoreparts:lr4_6s_lion` | 6S1P 3000 Li-Ion | 11:52 → 16:56 | 0,65 → 0,46 | 2,7 → 1,9 | gemischt 0,38–0,5 (Toothpick) bzw. 0,4–0,5 W/g (7″), Verhältnis ≈ 2 (`builds.md` §11) |
| `justmoreparts:lr7_deadcat_lion` | 6S 4500 Li-Ion | 11:32 → 15:56 | 0,50 → 0,37 | 2,7 → 2,0 | wie `long_range_7`; Verhältnis ≈ 2 |
| `justmoreparts:lr8_6s_lion` | 6S2P 8400 Li-Ion | 13:36 → 18:18 | 0,53 → 0,39 | 3,0 → 2,2 | 7,5″/9″ mit 6S2P 6000 Li-Ion knapp 16 bzw. 18 min im Flug (`builds.md` §8), hier 40 % mehr Kapazität |

Aggressiv bleibt (z. B. `lr4_6s_lion` 10:30 bei 0,74 W/g, `long_range_7` 9:42). Prüffenster Long Range mit
Li-Ion-artigen Zellen, gemischt: 0,30–0,50 W/g statt 0,40–0,55. Für Li-Ion nennt `builds.md` §0 keinen gemischten Wert;
die Untergrenze folgt aus dem Verhältnis ≈ 2 bei Schweben 0,15–0,22 W/g (`builds.md` §11: 0,30–0,44) und aus
[Q109] (15–22 min mit 6S 4000 Li-Ion = 0,25–0,36 W/g für den 846-g-7″), die Obergrenze aus „0,4–0,5“ (`builds.md` §11).
Aggressiv mit Li-Ion bleibt 0,40–0,80 (§1). `lr4_6s_lion` wird wieder gegen die Long-Range-Fenster geprüft.

**2. Luftwiderstand `justmoreparts:cw_35_duct`.** Die erste 0.4.3-Fassung setzte Stirn-/Draufsicht 0,018 / 0,013 m² –
die Draufsicht kleiner als die eigene Stirnfläche und kleiner als beim 2,5″-Rahmen (0,015 m²), obwohl der Rahmen größer
ist; gewählt auf 103 km/h hin, die Angabe „Duct-Ringe und Rumpf ≈ 0,01–0,02 m²“ hatte keine Quelle. Die Referenzen
enthalten keine gemessenen Widerstandsflächen. Jetzt gilt für alle drei Cinewhoop-Rahmen dieselbe Regel: Draufsicht =
1,67 × Stirnfläche, Fläche ∝ Radstand^1,21 – das Potenzgesetz durch `justmoreparts:cw_25_duct` (112 mm, 0,009 / 0,015 m²)
und `propwash:cinewhoop_3` (142 mm, 0,012 / 0,020 m²). Für 158 mm ergibt das 0,0137 / 0,0228 m²; das ist auch mehr als
die wirksame Draufsicht des offenen 3,5″-Toothpick-Rahmens `tp_35_stretched` (Standard 2,9 × 0,0072 = 0,021 m²).
`cw_25_duct` und `cinewhoop_3` bleiben unverändert.

Folge für `justmoreparts:cinewhoop35_6s`: Topspeed 103 → 89 km/h, Reisen 9:58 → 9:47, gemischt 8:38 → 8:31, aggressiv
6:31 → 6:27 min; Masse, Schub, Schwebegas, Ströme und Temperaturen gleich. Die Quellen mit 100–150 km/h fliegen höhere
KV auf 6S: CineApe35 2006 2030 KV 110 km/h, Cinebot35 2105.5 2450 KV 140–150 km/h (`builds.md` §5), Cineon C35 101 km/h
ohne Motorangabe. Das Preset hat den F2004 mit 1700 KV; die Geschwindigkeit, bei der ein Prop keinen Schub mehr liefert,
wächst mit der Drehzahl. Drehzahlskaliert (1700/2030 bzw. 1700/2450) ergeben die Quellen 92 bzw. 97–104 km/h; 89 km/h
liegen 3 % unter dem unteren Wert (§2.5). Mehr Tempo bräuchte eine höhere KV oder Steigung, also andere Teile.

**3. Richtigstellung §0.6:** Die übrigen 21 Presets fliegen unverändert, rechnen aber nicht alle gleich (Liste jetzt in
§0.6).

**4. `justmoreparts:whoop75_1s` Vollgasstrom:** leicht außerhalb der Referenz, jetzt in §2.5 aufgeführt.

Beweis wie §0.6: gegen 0.4.2 + JMP 1.0.4 1004 Varianten ohne korrigiertes Teil bitgleich, gegen die erste 0.4.3-Fassung
nur die 34 Varianten mit `cw_35_duct` anders (im erweiterten Werkzeug des Reviews 35 von 1290).

### 0.8 Restpunkte des vierten Reviews (Release Propwash 0.4.0 / Just More Parts 1.0.0)

Der vierte Review bestätigte §0.1–§0.7 (Fenster, Owner-Fall, Reihenfolge, Bewegungs-Goldens, Sharecodes) und meldete:
die 60-s-Temperaturen der beiden korrigierten Cinewhoops lagen über ihren eigenen Prüfstandszeilen, die Blattzahl-
Reihenfolge der Familien mit nur einem korrigierten Prop war verdreht, dazu Fehler in §0.4, §0.6, §2.4 und §2.5.
Entscheidung des Besitzers: die freigegebenen Cinewhoop-Teile nachstimmen, die Geschwister-Props derselben Familien
einheitlich korrigieren; die Flug-Engine bleibt unberührt, nur Daten dieser Teile ändern sich, alle übrigen Teile und
Presets fliegen bitgleich. Geändert sind ausschließlich JMP-Daten:

| Teil | Wert | vorher | nachher | Grund |
|---|---|---|---|---|
| `justmoreparts:m2004_1700` | Fahrtwind-Kühlung | 0,0002 | 0,000236 W/(K·rad/s) | Obergrenze der Datenregel (2,5 × Standard); 60-s-Wert in der eigenen F2004-Zeile |
| `justmoreparts:p65_22_4b_bi` | c_t / c_p | 0,2061 / 0,2656 (FM 0,28) | 0,1874 / 0,1961 (FM 0,33) | 60-s-Wert des `m1404_3800` in seiner F1404-Zeile, Schwebegas und S/G am Rand der Referenz (§0.5) |
| `justmoreparts:p65_25_3b_pc` | c_t / c_p | 0,0877 / 0,0774 (FM 0,27) | 0,114 / 0,0877 (FM 0,35) | Geschwister: im Duct weniger Schub und Strom, länger in der Luft als der Vierblatt |
| `justmoreparts:p40_20_2b_pc` | c_t / c_p | 0,0804 / 0,0601 | 0,1367 / 0,1332 | Geschwister von `p40_17_3b_pc`: gleicher Faktor a = 1,7 (c_t · a, c_p · a^1,5) |
| `justmoreparts:p40_15_4b_pc` | c_t / c_p | 0,0984 / 0,0816 | 0,1673 / 0,1809 | wie oben |
| `justmoreparts:p40_14_5b_soft` | c_t / c_p | 0,1051 / 0,0882 | 0,1787 / 0,1955 | wie oben |
| `justmoreparts:p102_25_2b_pc` | c_t / c_p | 0,0763 / 0,0305 (FM 0,55) | 0,13 / 0,0763 (FM 0,49) | Geschwister von `p102_30_3b_gfn`: auf die Referenz Gemfan 4024-2 (c_t 0,13–0,14, c_p 0,07–0,075, FM 0,47–0,49) |

**Temperaturen nach den eigenen Zeilen** (`motors-props.md` §6.1, Harness bei 25 °C):

| Aufbau | Motor | 60 s vorher → nachher | Leistung je Motor | ΔT | Prüfstandszeile |
|---|---|---|---|---|---|
| `justmoreparts:cinewhoop25_4s` | `m1404_3800` | 82 → 68 °C | 177 W (vorher 201 W) | 43 K | F1404: 107–262 W, 45–68 °C bei 8–24 °C, ΔT 25–44 K |
| `justmoreparts:cinewhoop35_6s` | `m2004_1700` | 91 → 82 °C | 331 W | 57 K | F2004: 395–503 W, 61–72 °C bei 8 °C, ΔT 53–64 K (≈ 78–89 °C bei 25 °C) |
| `justmoreparts:lr4_6s_lion` | `m2004_1700` | 74 → 68 °C | 223 W | 43 K | wie oben, weniger Leistung |

Der `m1404_3800` steckt auch im unveränderten `toothpick3_4s` und darf deshalb nicht angepasst werden; die Temperatur
im 2,5″-Cinewhoop kommt über die Last des Props. Beim `m2004_1700` reicht die Kühlung allein (Prop und Schub von
`cinewhoop35_6s` bleiben in der Referenz, 594 g je Motor, 63 A). Die 331 W liegen unter der Leistung der F2004-Zeile;
ΔT 57 K liegt deshalb im unteren Teil des Bands, weniger lässt die Datenregel nicht zu (Standkühlung bleibt Standard).

**Folgen für `justmoreparts:cinewhoop25_4s`** (flugwirksam, freigegeben): Schwebegas 32,2 → 33,1 %, S/G 5,8 → 6,0,
Vollgas 56,1 → 48,1 A, Topspeed 85 → 87 km/h, Schweben 10:12 → 11:15, Reisen 8:48 → 9:50, gemischt 7:31 → 8:31,
aggressiv 5:30 → 6:25 min (0,28 / 0,32 / 0,37 / 0,49 W/g, alle im Cinewhoop-Fenster). `cinewhoop35_6s` und `lr4_6s_lion`
ändern nur Temperaturen (Kühlung); Flugzeiten, Ströme, Schub und Topspeed bleiben.

**Blattzahl-Reihenfolge** (mehr Blätter: mehr Schub und Strom, kürzere Flugzeit), gemessen in den Aufbauten, für die
die Familie gedacht ist (Schweben / Reisen / gemischt / aggressiv, S/G, Vollgasstrom):

| Familie | Aufbau | 2 Blätter | 3 Blätter | 4 Blätter | 5 Blätter |
|---|---|---|---|---|---|
| 40 mm | 75 mm 1S (`whoop75_1s`) | 7:11 / 6:09 / 4:34 / 3:38, 4,7, 19,8 A | 7:04 / 5:58 / 4:24 / 3:29, 4,8, 21,3 A | 6:33 / 5:30 / 4:05 / 3:15, 4,8, 22,6 A | 6:10 / 5:09 / 3:52 / 3:05, 4,7, 23,3 A (6-A-AIO begrenzt) |
| 40 mm | 85 mm 2S | 7:37 / 7:03 / 5:10 / 4:05, 6,3, 28,9 A | 7:34 / 6:56 / 5:04 / 3:59, 6,4, 31,6 A | 6:58 / 6:22 / 4:42 / 3:44, 6,5, 33,7 A | 6:32 / 5:57 / 4:27 / 3:33, 6,6, 35,5 A |
| 65 mm | 2,5″-Cinewhoop (`cinewhoop25_4s`) | – | 11:26 / 10:25 / 9:00 / 6:44, 4,8, 29,7 A | 11:15 / 9:50 / 8:31 / 6:25, 6,0, 48,1 A | – |
| 102 mm | 4″ 6S Li-Ion (`lr4_6s_lion`) | 34:43 / 26:04 / 18:07 / 13:30, 4,5 (497 g), 37,7 A | 32:35 / 23:01 / 16:56 / 10:30, 4,5 (502 g), 47,0 A | – | – |
| 102 mm | 4″ 6S Semi-Solid | 37:44 / 24:20 / 17:34 / 10:32, 6,0, 49,4 A | 35:27 / 20:33 / 16:09 / 8:28, 6,6, 67,4 A | – | – |

Vorher hatte im 75-mm-Whoop der korrigierte Dreiblatt mehr Schub (4,8) als Vier- und Fünfblatt (4,2 / 4,3, beide
`SLUGGISH_MOTORS`), im 2,5″-Cinewhoop brachte der Vierblatt 5,8 statt 3,8 S/G und flog länger als der Dreiblatt, und
der 4″-Zweiblatt zog weniger als halb so viel Strom wie der Dreiblatt. Wie die Familien korrigiert sind:

- **40 mm**: alle vier Props mit demselben a = 1,7 (Gütezahlen unverändert 0,30–0,31, in der 40-mm-Referenz 0,19–0,30
  am oberen Rand). Die Blattinterferenz der Energiebilanz (§3.1) macht mehr Blätter im Teillastbereich teurer.
- **65 mm**: Der Dreiblatt ist das Prop der 2,5″-Toothpicks (`tp_25_x` mit den unveränderten 1103/1202,5-Motoren). Mit
  a = 2,0 wie beim Vierblatt überhitzen diese (`MOTOR_THERMAL` schon ab a ≈ 1,5 auf 4S). Er bekommt deshalb c_t · 1,3 und
  eine Gütezahl etwas über der des Vierblatts (0,35 gegen 0,33; UIUC: die Gütezahl fällt ab vier Blättern, §4.2 der
  Referenz). Folge im 2,5″-Toothpick 4S 450: S/G 7,2 → 8,7, Schwebegas 25,2 → 21,3 %, Topspeed 91 → 98 km/h, Motor nach
  60 s 111 → 121 °C (ohne Warnung; seit §0.10 mit der Fahrtwind-Kühlung des `m12025_5500` 79 °C) – in `builds.md` §0
  (Toothpick 2,5–3″ 3S/4S: S/G 6–9, Schwebegas 20–30 %, 90–120 km/h); die JMP-Toothpick-Korridore sind auf diese Referenz
  erweitert (S/G bis 9, Schwebegas ab 20 %).
- **102 mm**: Der Zweiblatt bekommt die Referenzwerte des Gemfan 4024-2. Mit a = 1,8 wie der Dreiblatt (Gütezahl 0,55,
  über der Referenz) hätte er am strombegrenzten Li-Ion-Akku mehr Schub als der Dreiblatt (532 gegen 502 g).
- **89 mm**: `justmoreparts:p89_30_3b_pc` blieb in diesem Durchgang unverändert (Prop des damals unveränderten Presets
  `toothpick35_6s`; mit a = 2,5 wie der Duct-Fünfblatt würde das Preset den 20-A-AIO begrenzen und überhitzen,
  `ESC_CURRENT_LIMITED`, `MOTOR_THERMAL`). Im 3,5″-Cinewhoop folgten Schub und Strom der Blattzahl (S/G 3,8 gegen 6,8,
  24,8 gegen 63,0 A), die Flugzeit nicht (Schweben 10:26 gegen 11:25). Seit §0.10 ist er wie der 65-mm-Dreiblatt korrigiert
  (c_t · 1,3, Gütezahl 0,395); die Blattzahl-Reihenfolge gilt jetzt in allen vier Flugstilen.

Weitere Folgen in Varianten: Der 75-mm-Whoop mit `m0802_15500` am 2S-300-mAh-Akku zieht mit jedem 40-mm-Prop bei Vollgas
23–26 A; der 45C-Akku (13,5 A) meldet `BATTERY_OVERLOAD` und `HEAVY_SAG` – ehrliches Contra des kleinen Akkus, im
JMP-Referenzaufbau zugelassen. Mit 2S 450 mAh bleibt der Aufbau sauber.

**Beweis** (`tools/calibration.sh`, `sim.MotionFingerprintTest`, vorher = JMP-Stand des vierten Reviews gegen die
nachgestimmten Daten, 1245 Varianten: 27 Presets mit allen Einzeltausch-Varianten): **0 Varianten ohne geändertes Teil
anders**; 120 Varianten mit geändertem Teil anders (`m2004_1700` 58, `p65_22_4b_bi` 37, `p40_20_2b_pc`, `p40_15_4b_pc`,
`p40_14_5b_soft` je 6, `p65_25_3b_pc` 5, `p102_25_2b_pc` 4), 6 Varianten mit `m2004_1700` unverändert (Stacks bis 4S am 6S-Akku,
starten nicht). Geändert fliegen damit nur `cinewhoop25_4s` (Prop), `cinewhoop35_6s` und `lr4_6s_lion` (nur Motorwärme);
die übrigen 24 Presets – auch `toothpick35_6s`, der Owner-Whoop und „Deadcat LR 7″ · 8S“ – und alle Propwash-Goldens
(`airframe_golden.txt`, `builtin_regression.txt`, `builtin_definitions.txt`) sind bitgleich. Harness 5/5 in den
Klassenfenstern, Owner-Fall unverändert (3:19 / 3:01 / 2:02 / 1:39). Vorher/nachher-Dateien:
`calibration.txt` und `motion.txt` im Arbeitsprotokoll des Releases.

### 0.9 Akkuwarnungen (Rückmeldung vom 02.10.)

Mit unendlichem Akku kam trotzdem manchmal die Low-Battery-Warnung. Die Warnstufen kommen aus
der gefilterten Zellspannung unter Last; ein harter Schubstoß mit vollem Akku lag 2 s unter der Warnschwelle, und
`LOW`/`CRITICAL` blieben danach bis zum Reset stehen. Seit 0.4.0 (`sim.BatteryAlarm`, Flugmodell-Spezifikation §5.10,
Vergleich mit Betaflight in [`power-video.md`](power-video.md) §2.7): Die Stufe geht zurück, wenn die Spannung 2 s über
Schwelle + 0,1 V liegt, und hält nur, wenn auch die Ruhespannung an der Schwelle liegt; mit „unendlicher Akku“ gibt es
keine Warnungen, der Spannungseinbruch wirkt weiter. Nur Anzeige, Events und Pieptöne ändern sich, nicht die Physik.

Beweis wie §0.6 (`sim.MotionFingerprintTest`, 1245 Varianten mit den Just-More-Parts-Daten): Spezifikation, `acro` und
`angle` aller Varianten gleich, `drop` bei 3 Varianten anders (`fs_5_bash` mit 4S 450 mAh, `xclass_13` mit 6S 550 und 750
mAh – kleine Akkus, die im Fall-und-Wand-Lauf unter die Schwelle sacken). In deren Flugaufzeichnung unterscheiden sich
41 von 3892 Zeilen, ausschließlich in der Telemetrie-Spalte `batteryState` (die Warnung geht nach dem Stoß wieder auf
`OK`); alle Kräfte, Lagen, Drehzahlen, Ströme und Temperaturen sind bitgleich. Die eingebauten Goldens
(`builtin_regression.txt`, `airframe_golden.txt`) sind unverändert.

### 0.10 Nachtrag vom 02.10.: 89-mm-Dreiblatt und Toothpick-Motorwärme (Just More Parts 1.0.0)

Offen waren nach §0.8 die 89-mm-Familie (§2.5) und der 60-s-Wert des 2,5″-Toothpicks auf 4S (121 °C, §0.8 „65 mm“).
Entscheidung im Rahmen der Freigabe des Besitzers (Daten einzelner unrealistischer Teile, Flug-Engine unverändert);
geändert sind ausschließlich JMP-Daten:

| Teil | Wert | vorher | nachher | Grund |
|---|---|---|---|---|
| `justmoreparts:p89_30_3b_pc` | c_t / c_p | 0,0927 / 0,0658 (FM 0,34) | 0,1205 / 0,0845 (FM 0,395) | Geschwister des Duct-Fünfblatts (FM 0,364), korrigiert wie `p65_25_3b_pc`: c_t · 1,3 und eine Gütezahl etwas über der des Geschwisters |
| `justmoreparts:m1505_2500` | Fahrtwind-Kühlung | 0,00012 | 0,00016 W/(K·rad/s) (2 × Standard) | mit dem korrigierten Prop käme `toothpick35_6s` nach 60 s Vollgas auf 93 °C (ΔT 68 K bei ≈ 300 W je Motor; F1408: 145–386 W, ΔT 41–62 K); jetzt 78 °C (ΔT 53 K) |
| `justmoreparts:m12025_5500` | Fahrtwind-Kühlung | Standard (4,1·10⁻⁵) | 0,00008 W/(K·rad/s) (2 × Standard) | 2,5″-Toothpick 4S 450 nach 60 s 121 °C (ΔT 96 K bei ≈ 155 W je Motor; F1203: 57–136 W, ΔT 31–46 K); jetzt 79 °C (ΔT 54 K), 3S 450 61 °C, 3S 650 66 °C |

**Warum nicht mehr beim 89-mm-Dreiblatt?** Die Referenz HQ 3520-3 (P/D 0,57: c_t 0,17–0,18, FM 0,45–0,49, `motors-props.md`
§4.1) ergibt auf P/D 0,86 umgerechnet c_t ≈ 0,21, also a ≈ 2,3. Bei gleicher Gütezahl überhitzt schon ab a = 1,4 der
`m1505_3600` im 3,5″ auf 4S 1100 (`MOTOR_THERMAL`), ab a ≈ 1,8 begrenzt der 20-A-AIO (`ESC_CURRENT_LIMITED`,
`MOTOR_THERMAL`), und mit der nötigen Gütezahl (≈ 0,395) verlässt `toothpick35_6s` ab a ≈ 1,4 den JMP-Toothpick-Korridor
(S/G über 9). Die Gütezahl ist von zwei Seiten begrenzt:
unter 0,393 schwebt der Dreiblatt im 3,5″-Cinewhoop kürzer als der Fünfblatt, über 0,396 verlässt `toothpick35_6s` im
gemischten Flug das Toothpick-Fenster (0,38 W/g = 9:27 min). Gewählt: a = 1,3 wie beim 65-mm-Dreiblatt und FM 0,395 –
beide Grenzen knapp eingehalten (Schweben im Duct +0,4 %, gemischt 9:26 im Fenster 4:47–9:27).

| Aufbau | S/G | Schwebegas % | Schweben | Reisen | Gemischt | Aggressiv | I Vollgas A | Topspeed km/h | T60 / gemischt °C |
|---|---|---|---|---|---|---|---|---|---|
| `justmoreparts:toothpick35_6s` vorher | 7,6 | 28,0 | 13:52 | 12:02 | 8:31 (0,42 W/g) | 5:45 | 48,3 | 109 | 81 / 41 |
| `justmoreparts:toothpick35_6s` nachher | 8,9 | 24,1 | 15:05 | 12:54 | 9:26 (0,38 W/g) | 6:34 | 55,2 | 115 | 78 / 38 |

Topspeed 115 km/h liegt in `builds.md` §0 (3,5″-Toothpick 100–130 km/h); JMP prüft 3,5″-Toothpicks dafür mit einem eigenen
Korridor 100–130 km/h (2–3″ weiter 70–110 km/h).

**Blattzahl-Reihenfolge 89 mm** im 3,5″-Cinewhoop (`cw_35_duct`, `aio_20_6s_45a`, `m2004_1700`, `digital_hd_pro`, `b6s_850`;
Schweben / Reisen / gemischt / aggressiv, S/G, Vollgasstrom):

| Familie | Aufbau | 3 Blätter | 5 Blätter |
|---|---|---|---|
| 89 mm | 3,5″-Cinewhoop | 11:28 / 10:36 / 9:09 / 6:48, 4,7, 29,4 A (vorher 10:26 / 9:44 / 8:13 / 6:27, 3,8, 24,8 A) | 11:25 / 9:47 / 8:31 / 6:27, 6,8, 63,0 A |

Im 3,5″-Toothpick ist der Fünfblatt am 20-A-AIO strombegrenzt und überhitzt; dort ist er kein Vergleich.

**Beweis** (`tools/calibration.sh`, vorher = JMP `main` 1.0.0 vom 01.10., nachher = Branch `fix/open-items-data`; 1245
Varianten): **0 Varianten ohne geändertes Teil anders**; 55 anders, jede mit einem der drei Teile (`p89_30_3b_pc` 42,
`m1505_2500` 35, `m12025_5500` 7; Varianten mit zwei Teilen zählen doppelt), keine Variante mit einem der Teile
unverändert (die Motortemperatur ist Teil der Telemetrie der Bewegungs-Goldens, §2.4). Von den Presets fliegt nur
`justmoreparts:toothpick35_6s` anders; Owner-Fall und Propwash-Goldens bitgleich, Harness 9/9 Tests grün (alle Presets in
ihren Klassenfenstern).

## 1. Werkzeug: Kalibrier-Harness

- JUnit `sim.CalibrationHarness` und `sim.CalibrationHarnessTest` (Propwash): druckt für alle eingebauten Presets, alle
  22 JMP-Presets und den Owner-Testfall mit Varianten Gewicht, Schub/Gewicht, Schwebegas, spezifische Leistung im
  Schweben, die vier Flugzeiten mit Referenzbereich und spezifischer Leistung (W/g), Ströme, Landespannung, Grundlast,
  Topspeed und die drei Motortemperaturen. Prüft hart:
  - eingebaute Presets und (wenn die JMP-Daten erreichbar sind) alle JMP-Presets liegen in ihren Klassenbereichen;
  - der Owner-Testfall erfüllt die Referenzantwort (`builds.md` §15.2): Schweben (digital) 3:00–3:20, Reisen
    3:00–3:30, gemischt 2:00–2:30, aggressiv 1:30–2:00, Landespannung 3,3–3,5 V, Gewicht 25–30 g, Vollgasstrom
    12–16 A; 23000 statt 27000 KV gemischt und aggressiv +15–25 %; 3-Blatt statt 4-Blatt in allen vier Stilen
    +10–20 % und gemischt +15–30 s; 2-Blatt statt 4-Blatt Schweben und Reisen +10–20 %, gemischt und aggressiv +15–30 s
    (Prozent bis +30 %, siehe §2.4); 2-Blatt länger als 3-Blatt; Digital statt analog im Schweben −30–35 %;
  - flugwirksame Owner-Werte (Schub/Gewicht 4,1, Schwebegas 43,8 %) bleiben unverändert (`ownerWhoopKeepsItsFlightPhysics`,
    Abweichung zur Referenz in §2.3).
- Skript `tools/calibration.sh [pfad-zu-just-more-parts]`: lädt die JMP-Teile aus dem Nachbar-Projekt
  (`common/src/main/resources`, ohne Angabe `../propwash-justmoreparts` neben dem Hauptbaum), führt den Harness, die
  Reihenfolge-Prüfung `sim.FlightTimeOrderTest` über alle Einzeltausch-Varianten und `sim.MotionFingerprintTest` aus
  und gibt die Tabelle aus (Dateien `build/calibration/calibration.txt` und `build/calibration/motion.txt`, §0.6).
- In Just More Parts prüft `share.OwnerFlightTimeTest` denselben Owner-Fall mit denselben Grenzen direkt über den
  Sharecode und die öffentliche API.
- **Referenzbereiche je Klasse** als spezifische Leistung (W je g Abfluggewicht, Pack-Leistung inklusive Avionik,
  `builds.md` §11), für jedes Preset über seine nutzbare Energie in Minuten umgerechnet – so zählen Akku und Gewicht des
  Presets, nicht die eines „typischen“ Aufbaus:

| Klasse | Schweben | Reisen | Gemischt | Aggressiv | Quelle |
|---|---|---|---|---|---|
| 65-mm-Whoop 1S (Props bis 35 mm) | 0,45–0,72 | 0,50–0,85 | 0,75–1,10 | 1,10–1,50 | builds §2, §11 (digital bis 0,7 im Schweben) |
| 75–85-mm-Whoop | 0,33–0,45 | 0,37–0,55 | 0,47–0,70 | 0,60–0,90 | builds §0 (75 mm: 6–8 / 5–7 / 4–5:30 / 3–4 min bei 33–46 g, 1S 450–550) und §11 |
| Toothpick 2–3,5″ | 0,20–0,40 | 0,25–0,50 | 0,38–0,75 | 0,50–1,00 | builds §0, §4, §11 |
| Cinewhoop 2,5–3″ (Props bis 80 mm) | 0,20–0,35 | 0,25–0,39 | 0,30–0,55 | 0,45–0,75 | builds §5 (Cinelog30: 550 mAh 0,35–0,39, 850 mAh 0,28–0,29 W/g), §11 |
| Cinewhoop 3,5″ (Props über 80 mm) | 0,20–0,30 | 0,25–0,30 | 0,28–0,50 | 0,35–0,60 | builds §5 (CineApe35, Cinebot35 ≈ 0,25 W/g), §11 |
| 5″ Freestyle | 0,22–0,30 | 0,27–0,40 | 0,40–0,55 | 0,50–0,70 | builds §6, §11 |
| 5″ Race | 0,20–0,30 | 0,27–0,40 | 0,80–1,00 | 1,00–1,40 | builds §7 („≈ 3 min Durchschnittspilot“ = 0,89 W/g, 2 min = 1,33 W/g) |
| Long Range 4–8″ | 0,15–0,28 | 0,22–0,37 | 0,40–0,55 | 0,55–0,80 | builds §0, §9, §11 |
| X-Class 10–13″ | 0,12–0,20 | 0,14–0,30 | 0,40–0,55 | 0,55–0,90 | builds §10, §11 (≈ 0,44 W/g, „≈ 5 min“) |

Zwei dokumentierte Anpassungen:

- 65-mm-Whoops unter 25000 KV: gemischt und aggressiv ÷ 1,25 – Tendenz „niedrigere KV fliegt 15–25 % länger“
  (`builds.md` §2.3, §15.2). Betrifft `propwash:whoop_65` (0802, 22000 KV).
- Long Range mit Li-Ion-artigen Zellen (`usable_fraction` ab 0,87): aggressiv ab 0,40 (Untergrenze von gemischt der
  Long-Range-Klasse), weil der Schubstoß auf den Dauerstrom des Packs begrenzt ist („Li-Ion nicht für harte Manöver“,
  `builds.md` §0, §8); gemischt 0,30–0,50 W/g, weil der Schubstoß im gemischten Flug höchstens 6 C zieht (seit 0.4.3,
  §0.7: Verhältnis gemischt/Schweben ≈ 2 bei Schweben 0,15–0,22 W/g, `builds.md` §11; 7″ 6S 4000 Li-Ion 15–22 min,
  `power-video.md` §5).

Gegenüber der ersten Kalibrierung sind die Fenster enger: 65-mm-Whoop gemischt/aggressiv 0,75/1,10 statt 0,65/0,78,
75–85 mm aggressiv bis 0,90 statt 1,40, Cinewhoop Reisen bis 0,39 bzw. 0,30 statt 0,45, 5″ Race gemischt ab 0,80 statt
0,60, Long Range gemischt/aggressiv ab 0,40/0,55 statt 0,35/0,45 (mit Li-Ion gemischt 0,30–0,50 und aggressiv ab 0,40,
weil der Schubstoß dort begrenzt ist), X-Class gemischt ab 0,40 statt 0,35.

## 2. Gefundene Fehler

### 2.1 Systematisch (Modell), erste Kalibrierung

1. **Antriebswirkungsgrad zu gut.** Das Gleichstrom-Motormodell der Flugphysik (`I_0 = 0,03·I_max`, konstantes
   Leerlaufmoment, ESC-Widerstand) liefert im Schweben einen Gesamtgütegrad von 0,29 (Whoop), 0,34–0,38 (5″),
   0,40–0,42 (7″). Real im Flug: 0,12–0,18, 0,22–0,30, 0,30–0,40 (`motors-props.md` §0, §9). Ursache: fehlende
   Eisen-/Wirbelstrom- und Reglerverluste; der wirksame Leerlaufstrom liegt real beim 2–3,5-Fachen des Datenblattwerts,
   1S-Motoren haben 0,2–0,5 A statt 0,1 A. Folge: alle Schwebe- und Reisezeiten zu lang (Whoop 7,2 statt ≈ 4 min).
2. **Videoleistung aus der Masse.** `0,6 W + 0,3 W/g · Masse` gab digitalen Systemen 30–60 % zu wenig
   (`power-video.md` §6.3). Die Grundlast (4–7,5 W) hängt nicht an der Masse.
3. **Nutzbare Kapazität pauschal 80 % ohne Landespannung.** Li-Ion und 1S-LiHV verschenkten Kapazität, starke
   Belastung zog nichts ab (`builds.md` §12, `power-video.md` §2.1).
4. **„Reiseflug“ bei halber Höchstgeschwindigkeit.** Beim 7″ bedeutete das die 2,3-fache Schwebeleistung, beim 5″ die
   2,2-fache; real sind es 1,2–1,5 (`builds.md` §11). Beim Whoop war Reisen kaum teurer als Schweben.
5. **Keine Kennzahl für typisches Fliegen.** Es gab nur Schweben und Reisen; Piloten erleben „gemischt“ und
   „aggressiv“ (`builds.md` §17).
6. **Prop-Beiwerte der Kleinprops energetisch zu schlecht.** Die Gütezahl der 3″-Props der Datenbasis liegt bei 0,25–0,28
   (real 0,34–0,58), die der 10–13″-Props bei 0,60 (real 0,70–0,76). Für die Flugphysik bleiben die Beiwerte; die
   Energiebilanz gleicht die Größenklasse aus (`motors-props.md` §4.1, §4.8).
7. **Temperaturkennzahl „Vollgas im Stand bis der Akku leer ist“** ist kein realer Betriebsfall (`builds.md` §14, §16)
   und nannte 124 °C beim „Deadcat LR 7″ · 8S“.
8. **Akku-Anzeige im Flug** rechnete denselben zu kleinen Strom und zeigte deshalb unrealistische Restladung und
   Warnzeitpunkte.


### 2.2 Systematisch (Modell), Nachkalibrierung

9. **Blattinterferenz als fester Stromfaktor.** Die erste Fassung gab 31-mm-Vierblatt-Props pauschal +10 % Strom – bei
   jedem Gas. Real kostet das vierte Blatt vor allem im Teillastbereich (gleicher Schub: +10–20 % Leistung), bei Vollgas
   nur 2–5 % Wirkungsgrad (`builds.md` §2.3). Folge: 3-/2-Blatt nur +4 %/+6,5 % Schwebezeit statt +10–20 %, und der
   Vollgasstrom des Owner-Whoops lag mit 16,6 A über 12–16 A. Jetzt ein drehzahlproportionaler Verlust von 38 % der
   Schwebe-Wellenleistung je Blatt über drei (Props unter 45 mm): im Schweben voll, bei Vollgas ≈ 10 % der Wellenleistung.
10. **Energiefaktoren der 2–4″-Props zu hoch.** Toothpicks und Cinewhoops (51–102 mm) lagen im Reiseflug bei 0,35–0,41 W/g,
    real 0,25–0,39 (3″) bzw. 0,25–0,30 (3,5″). Größenfaktor jetzt 51 mm 0,78, 76 mm 0,72, 89 mm 0,67, 102 mm 0,80 (vorher
    0,87/0,78/≈ 0,84/0,90), 31 mm 0,84 statt 0,94 (zusammen mit 9).
11. **Whoop-Profil mit festem Schub/Gewicht.** Der Schubstoß bei 3,5 : 1 erreichte der 27000-KV-Owner-Whoop ohne Vollgas,
    der 23000-KV-Whoop nur mit Vollgas – deshalb flog 23000 KV gemischt nur +11 % länger (Referenz +15–25 %). Piloten
    fliegen 65-mm-Whoops mit Vollgasstößen; der Schubstoß ist jetzt Vollgas (5 : 1, höchstens Vollgas). 75–85-mm-Whoops
    (stärker, draußen geflogen) behalten ein ruhigeres Profil mit 3,5 : 1.
12. **Race, Long Range und X-Class zu sanft.** 5″ Race gemischt 4:08 statt ≈ 3 min (0,66 statt 0,89 W/g), „Deadcat LR 7″ ·
    8S“ gemischt 9:13 und aggressiv 6:38 (Referenz 7–9 bzw. 5–6 min), `lr6_6s` 7:56/5:54 (Grenze ≈ 7:55/5:39), X-Class 13″
    5:55 statt ≈ 5 min. Profile in §3.3 angehoben; Long-Range-Reisen 63 statt 60 km/h (MOZ7 V2: 60–70 km/h).
13. **Landung unter mittlerer Last verstärkt Unterschiede.** Die erste Fassung landete, sobald die Zellspannung unter der
    *mittleren* Last die Landespannung erreichte. Bei 1S-Whoops mit Vollgasstößen zog das den Flug überproportional vor
    (Owner 2-Blatt aggressiv +31 %, 23000 KV +28 %). Piloten lesen die Landespannung im ruhigen Flug ab; Einbrüche im
    Schubstoß lösen Warnungen aus, beenden den Flug aber nicht. Jetzt: Landung bei Landespannung unter der Last des
    ruhigen Flugabschnitts oder bei Abschaltspannung unter der mittleren Last (§3.2).
14. **Motortemperatur nach gemischtem Flug ohne Fahrtwind.** 5″ 68–75 °C, X-Class 81–92 °C (xclass12 sogar über seinem
    60-s-Wert), Referenz 40–60 °C, Obergrenze 65–70 °C (`builds.md` §14, `motors-props.md` §6.2). Die Analyse rechnet
    jetzt mit Fahrtwind-Kühlung (§3.5); die Thermik der Flugphysik bleibt unverändert.
15. **Harness zu weit.** Fenster und Owner-Prüfungen waren teils unter die Referenz gelockert (KV-Verhältnis ab 1,08,
    3-Blatt ab 10 s, Schwebegas bis 50 %), Schweben, Schub/Gewicht und Vollgasstrom wurden nicht geprüft. Jetzt wie §1.

### 2.3 Einzelteile (Daten)

- Energie-/Anzeige-Daten `power_w` für alle Videoteile, `usable_fraction` für die Chemien (Propwash LiPo 0,80, LiHV
  0,85, Li-Ion 0,88; JMP Semi-Solid 0,88). Nachkalibrierung innerhalb der Spannen von `power-video.md` §6.3 (Angaben dort
  inklusive ≈ 0,6 W Grundlast): `justmoreparts:digital_micro` 5,1 W statt 4,5 W (gesamt 5,7 W, Referenz 4,5–6,5 W;
  HDZero Whoop 5–6 W), `justmoreparts:analog_whoop_25mw` 0,7 W statt 1,3 W (gesamt 1,3 W, Referenz 1,3–2 W),
  `propwash:analog_nano` 1,9 W statt 1,5 W (gesamt 2,5 W, Referenz 1,5–2,5 W; 200 mW Sendeleistung). Damit kostet
  Digital- statt Analogvideo im Owner-Whoop 30 % Schwebezeit (Emax HDZero gegen analog: 35 %).
- JMP-Motoren mit zu heißem 60-s-Wert bekamen in der ersten Kalibrierung mehr Fahrtwind-Kühlung
  (`cooling_w_per_k_per_rad_s`): `m1404_3800` 0,00011, `m1505_2500` 0,00012, `m2004_1700` 0,00013, `m2507_1500` 0,00022,
  `m2807_1100` 0,00045 (vorher 0,00028), `m3115_900` 0,0006 (vorher 0,00043), `m4214_400` 0,0007. Referenz: Glocke nach
  60 s Vollgas im Stand 1203–1404 45–68 °C, F1408 60–81 °C, 2806,5–2808 81–99 °C (`motors-props.md` §6). **Richtigstellung:**
  Dieser Wert ist nicht „nur Temperatur“. Er speist auch die Thermik der Flugphysik (`sim.Thermal`, `DroneSim`:
  Leistungsgrenze, Drosselung ab `derate_start_c`, Verschleiß). In allen geprüften Presets und Varianten erreichen die
  Motoren die Drosselung nicht, das Flugverhalten ist dort gleich; bei Dauervollgas über der alten Drosselgrenze drosseln
  diese sieben Motoren jetzt später. Die JMP-Datenregel erlaubt bis 2,5 × Standardkühlung.
- Shift-Hilfen in JMP mit Zahlen nach der neuen Analyse; nach der Nachkalibrierung angepasst: `cw_25_duct` „rund 10 min
  Schweben“, `lr_4_x` „rund 31 min“, `b1s_450_hv` „über 7 min“, `b4s_1100` „über 14 min“, `b6s_750` „über 13 min“,
  `b6s_850` „über 10 min“, `m0802_19000` „7 min Schweben“, `m1404_3800` „nach 1 min Vollgas 59 statt 89 °C“. In 0.4.3 /
  1.0.5 nach den Datenkorrekturen (§0.4) erneut angepasst, Liste in JMP `docs/design/parts-catalog.md` §13.

### 2.4 Nicht geändert, weil flugwirksam – Entscheidung des Besitzers

Diese Werte weichen von der Referenz ab, ändern aber Masse, Spannung, Motorverhalten oder die Thermik der Flugphysik. Sie
bleiben unverändert; die Energiebilanz ist so gebaut, dass die Flugzeiten trotzdem stimmen. Mit der Freigabe vom 30.09.
in 0.4.3 / 1.0.5 korrigiert (§0.4): Wicklungswiderstand `justmoreparts:m2004_1700`, Schub/Gewicht und Schwebegas von
`justmoreparts:cinewhoop35_6s` (über `p89_25_5b_pc_duct`), außerdem die im zweiten Review gemeldeten Werte (Topspeed der
Cinewhoops, Schwebegas `whoop75_1s`/`cinewhoop25_4s`/`lr4_6s_lion`, Massen `p76_30_2b_pc`/`m2807_1500`, Temperatur
`m1103_11000`). Alle übrigen Zeilen gelten weiter.

| Teil / Wert | heute | Referenz | Wirkung einer Korrektur |
|---|---|---|---|
| `propwash:b1s_300_hv` Masse | 7,5 g | 8,2–8,4 g | Whoop 0,8 g schwerer |
| `propwash:stack_30` Masse | 18 g | 25–35 g | 5″/7″ schwerer |
| `propwash:aio_20` Masse | 7 g | 7,9–9,8 g | Cinewhoop schwerer |
| `propwash:digital_hd` Masse | 23 g | 32–40 g | Freestyle/LR schwerer |
| `propwash:m2004_3000` Masse | 12 g | 16–17 g | Cinewhoop-Motoren schwerer |
| `propwash:m0802_22000` Wicklungswiderstand | 0,20 Ω | 0,105–0,122 Ω | andere Motorkennlinie (Schub, Strom) |
| `justmoreparts:m0702_23000`, `m0702_27000` Masse | 1,8 g | 1,45–1,55 g | Owner-Whoop leichter |
| `justmoreparts:m12025_*` Masse | 4,7 g | 3,8 g | Toothpicks leichter |
| `justmoreparts:m2807_1100` Masse | 47,5 g (der gleich große `m2807_1500` hat seit 0.4.3 53 g) | ≈ 54 g | 7″/8″ schwerer, „Deadcat LR 7″ · 8S“ ≈ 26 g schwerer |
| `justmoreparts:m3115_*` Masse | 90 g | 113 g | X-Class 10″ schwerer |
| `justmoreparts:m4214_330/400/530` Masse / `max_current_a` | 128 g / 55, 65, 80 A | 211–256 g / 31–49 A | X-Class 12–13″ ≈ 0,4 kg schwerer, anderer Leerlaufstrom (`I_0 = 0,03·I_max`) |
| `justmoreparts:m2207_2550` Wicklungswiderstand | 25,9 mΩ | 43 mΩ | andere Motorkennlinie |
| `justmoreparts:m22075_2700` Wicklungswiderstand | 22 mΩ | ≈ 37 mΩ | andere Motorkennlinie |
| `justmoreparts:m23065_2450` Wicklungswiderstand | 27,8 mΩ | ≈ 51 mΩ | andere Motorkennlinie |
| `justmoreparts:m23065_2020` Wicklungswiderstand | 40,8 mΩ | ≈ 62 mΩ | andere Motorkennlinie |
| `justmoreparts:m1505_3600` Wicklungswiderstand / `max_current_a` | 125 mΩ / 25 A | 200 mΩ / 12–15 A | andere Motorkennlinie, Leerlaufstrom |
| `justmoreparts:digital_hd_pro` Masse | 24 g | 32–40 g | 5″/Cinewhoop 3,5″ schwerer |
| `justmoreparts:analog_lr_1600` Masse | 16 g | 20–25 g | LR schwerer |
| `justmoreparts:digital_micro` Masse | 4,8 g | 6–10 g | Whoop/Toothpick schwerer |
| `justmoreparts:stack_30_4s_55a` Masse | 16 g | ≈ 22 g | 5″ Race 4S schwerer |
| `justmoreparts:stack_20_6s_40a` Masse | 14 g | ≈ 17 g | schwerer |
| `justmoreparts:b2s_450_hv`, `b4s_1100`, `b4s_1500`, `b4s_1800` Masse | 8–15 % leicht | `power-video.md` §6.2 | schwerer |
| Zell-Innenwiderstand großer LiPos (z. B. `justmoreparts:b6s_5000` 2–4 ×), C-Werte (Aufdruck statt gemessen) | 1,5–4 × Referenz bzw. Aufdruck | `power-video.md` §2.4, §2.5 | anderer Spannungseinbruch → anderer Punch |
| ESC-Widerstand (`3 mΩ + 0,15 V/I`) | Whoop 2,5 × zu klein, große ESCs 2–3 × zu groß | `power-video.md` §3.3 | andere Motorantwort |
| `I0_FRACTION` 0,03 | 1S-Motoren 0,1 A | 0,2–0,5 A | Whoops drehen langsamer |
| Prop-Beiwerte c_t/c_p (absolut), z. B. `propwash:p3_3b`, `p7_2b` | 5″ −35/−45 %, 3″ und 31 mm Faktor 2,5–4 | `motors-props.md` §4.8 | Drehzahlen, Schwebegas, Topspeed |
| Prop-Beiwerte der übrigen Props (seit §0.8 sind die Geschwister `p40_20_2b_pc`, `p40_15_4b_pc`, `p40_14_5b_soft`, `p65_25_3b_pc`, `p102_25_2b_pc` korrigiert, seit §0.10 auch `p89_30_3b_pc`, alle Geschwister mit c_t weiter unter der Referenz) | wie oben | `motors-props.md` §4.1 | Varianten mit diesen Props fliegen anders; Pro/Contra der Familie (§2.5) |
| Folge: Schub/Gewicht und Schwebegas | Owner-Whoop 4,1 : 1 / 43,8 %; `propwash:whoop_65` 3,2 : 1 / 55 %; `propwash:cinewhoop_3` 36,4 %; `justmoreparts:xclass12_8s` Schwebegas 39,2 % | Owner 4,5–6 : 1 / 30–40 %; 65 mm 4,5–6,5 : 1 / 30–35 %; Cinewhoop 3″ 23–33 %; X-Class 25–35 % | Flugverhalten |
| Folge: Topspeed X-Class | `xclass10_6s` 124, `xclass12_8s` 113, `xclass13_8s` 117, `xclass13h_8s` 107 km/h | 150–190 km/h (`builds.md` §0, §10) | Flugverhalten |
| JMP `lihv_ss` Leerspannung | 3,5 V | ≈ 3,0 V | andere Spannungskurve |
| Thermik eingebauter Motoren (z. B. Cinewhoop `propwash:m1404_4600` 102 °C nach 60 s Vollgas im Stand) | – | F1404 45–68 °C | Die Motortemperatur ist Teil der Telemetrie in den Bewegungs-Goldens (`acro`, `angle`, `drop`); eine andere Kühlung würde diese Goldens ändern. Der 60-s-Wert der Werkbank ist bewusst genau die Thermik des Spiels. |
| Keine Fahrtwind-Kühlung im Vorwärtsflug in der Flugphysik | – | Flug 40–60 °C | wie oben; die Analyse rechnet den Fahrtwind nur für „nach gemischtem Flug“ (§3.5) |
| Motorspannung im Flug folgt weiter dem Ladezustand der Flugphysik (§3.4) | – | – | volle Kopplung würde die Spannungskurve im Flug und damit die Bewegungs-Goldens ändern |

### 2.5 Verbleibende Abweichungen

Stand Release 0.4.0 / 1.0.0 (§0.8), zuerst die Folgen der Datenkorrekturen (§0.4, §0.8), dann die Energie- und
Anzeigewerte:

- **Geschwister der korrigierten Props** (seit §0.8 korrigiert, die 89-mm-Familie seit §0.10; Blattzahl-Reihenfolge in §0.8
  und §0.10): Die Dreiblätter der Toothpicks liegen mit c_t weiter unter der Referenz, weil die Toothpick-Motoren mehr Last
  nicht vertragen – 65 mm c_t 0,114 (2″/3″-Dreiblatt 0,26–0,30), 89 mm c_t 0,1205 (3,5″ 0,17–0,18 bei P/D 0,57, ≈ 0,21
  bei P/D 0,86; mehr verbietet die Grenze der Gütezahl aus §0.10). Im 3,5″-Cinewhoop schwebt der Dreiblatt nur 0,4 % länger
  als der Fünfblatt (Reisen +8 %, gemischt +7 %, aggressiv +5 %), und `justmoreparts:toothpick35_6s` liegt mit gemischt
  9:26 (0,38 W/g) am Rand seines Fensters. Die eingebauten Props (`p3_3b` 3×3×3: Referenz 0,28–0,30 / 0,32–0,36) bleiben
  wie in §2.4. Über die Familien hinweg ist die Ordnung nach Durchmesser nicht überall stimmig (korrigierte 40-mm-Props
  gegen unkorrigierte 45-mm-Props: im 85-mm-Whoop 90–92 gegen 90–96 g Vollgasschub).
- **`justmoreparts:cinewhoop25_4s` Schwebegas 33,1 % und S/G 6,0** (seit §0.8): am oberen Rand von 23–33 % und 4–6; der
  60-s-Wert (68 °C, ΔT 43 K) liegt in der F1404-Zeile, c_t des Props unter der Duct-Referenz (§0.5).
- **`justmoreparts:whoop75_1s` Schwebegas 34,9 %** statt 31–33 %: begrenzt durch den 6-A-AIO (§0.5); Topspeed 41 km/h
  statt 45–65 km/h (Schätzung) – der Luftwiderstand von `whoop_75_duct` war nicht gemeldet und blieb.
- **`justmoreparts:cinewhoop35_6s` Vollgasstrom 63 A**: im Bereich von `builds.md` §0 (60–100 A), unter 20–27 A je
  Motor der Prüfstände; Motor nach 60 s Vollgas 82 °C (seit §0.8, F2004-Zeile bei 25 °C ≈ 78–89 °C; vorher 91 °C).
- **`justmoreparts:lr4_6s_lion` Schwebegas 37 %** (physikalische Folge der Kombination, §0.5); gemischt 16:56 min
  (0,46 W/g, gemischt/Schweben 1,9) und aggressiv 10:30 min (0,74 W/g) in den Long-Range-Fenstern (§0.7).
- **`justmoreparts:cinewhoop35_6s` Topspeed 89 km/h** mit den einheitlichen Cinewhoop-Flächen (§0.7): 3 % unter der
  drehzahlskalierten Referenz (CineApe35 2030 KV 110 km/h → 92 km/h mit 1700 KV); die ungeskalierte Spanne 100–150 km/h
  gilt für 2030–2450 KV.
- **`justmoreparts:whoop75_1s` Vollgasstrom 21,3 A**: je Motor 5,3 A wie die Prüfstände (1S 5,5–6 A am Netzteil, in der
  Luft 5–15 % weniger), die Summe liegt aber über der Schätzung 15–20 A (`builds.md` §0), und 4 × 39 g Vollgasschub bei
  79 W (3,70 V je Zelle) sind 1,99 g/W statt 1,2–1,8 g/W. Grund: Der korrigierte 40-mm-Dreiblatt (a = 1,7, §0.4/§0.5)
  gibt 39 g statt 30–35 g Vollgasschub. Weniger Strom würde g/W weiter von der Referenz entfernen, mehr Strom die Summe.
- **Verhältnis gemischt/Schweben der LiPo-Long-Range** `lr6_6s` und „Deadcat LR 7″ · 8S“ 2,4 (`builds.md` §11: ≈ 2):
  die absoluten Zeiten haben Vorrang (Deadcat LR 7″ · 8S gemischt 8:02 in 6–9 min, §4.2), unverändert seit 0.4.2.
- **`propwash:cinewhoop_3` Schwebegas 36,4 %** (Referenz 23–33 %) und 102 °C nach 60 s Vollgas: `p3_3b` und
  `m1404_4600` wurden nicht gemeldet und stecken in vielen eingebauten Kombinationen.
- **Profile 75–85-mm-Whoop**: Das Verhältnis aggressiv/Schweben liegt mit 2,0 (`whoop75_1s`) unter `builds.md` §11
  (2,5–3,0); die absoluten Zeiten aus §0 (gemischt 4–5:30, aggressiv 3–4 min) haben Vorrang, beide Angaben der Referenz
  widersprechen sich.
- **Reihenfolge der Flugzeiten bei sehr schwachen Aufbauten**: Kann ein Aufbau kaum mehr als schweben (S/G unter
  ≈ 1,5), sind alle vier Flugzeiten gleich (§0.2).
- **Hinweise aus dem Review, unverändert:** Landespannung von 6S/8S-LiPo-Aufbauten 3,52–3,61 V (Referenz ≈ 3,5 V unter
  Last); die analoge Owner-Variante schwebt 4:46 (Referenz 3:45–4:45) und reist 4:08 (bis 4:00 nur unter 17 g
  Trockengewicht; sie wiegt 17,4 g, weil der dokumentierte 7,5-g-Akku zu leicht ist, §2.4).

Die Referenzen widersprechen sich an einigen Stellen; die Kalibrierung trifft sie so eng wie möglich:

- **Owner 2-Blatt statt 4-Blatt**: Schweben +14 %, Reisen +15 %, gemischt +22 % (+27 s), aggressiv +27 % (+26 s) –
  Sekunden in der Referenz (+15–30 s), Prozent bei gemischt/aggressiv über +10–20 %. Grund: Der Zweiblatt zieht bei
  Vollgas 22 % weniger Strom (Referenz „20–30 % weniger bei gleichem Gas“), und jeder Vollgasanteil, der den KV-Unterschied
  (+15–25 %) trägt, verlängert den Zweiblatt genauso. Harness und `OwnerFlightTimeTest` prüfen deshalb dort bis +30 %.
- **Owner 23000 statt 27000 KV**: gemischt +16 % (+19 s), aggressiv +21 %. Die Prozent liegen in +15–25 %, die Sekunden
  knapp unter der Umrechnung „+20–40 s“ (bezogen auf 2:15 statt 2:02).
- **Owner gemischt 2:02 und Reisen 3:01** liegen am unteren Rand. Enger geht es nicht zugleich mit Schweben (digital)
  ≤ 3:20, Digital −30 % gegen analog und `propwash:whoop_65` Reisen ≤ 3:30 / gemischt ≤ 2:45: `whoop_65` ist analog und
  hat die schwächere 22000-KV-Kombination, fliegt also deutlich länger.
- **`propwash:whoop_65` Schweben 3:54** – unter „4:00–5:00 analog“ (§0), in „3:45–4:45“ (§15.2). Auf 28,1 g und 300 mAh
  umgerechnet ergeben die Emax-Schwebetests (25,2 g, 320 mAh, 5:03) ≈ 4:00, mit Vierblatt ≈ 3:45–3:55.
- **Motortemperatur nach gemischtem Flug** `xclass13h_8s` 68 °C, `xclass13_8s` 63 °C: über „typisch 40–60 °C“, unter der
  Obergrenze 65–70 °C; X-Class-Rennen sind der härteste Einsatz.
- **Cinewhoop `propwash:cinewhoop_3` 102 °C nach 60 s Vollgas**: Thermik der Flugphysik, siehe §2.4.

## 3. Umsetzung in Propwash 0.4.2 und 0.4.3 (`sim.EnergyModel`)

### 3.1 Akkustrom
Die Flugphysik liefert unverändert Busspannung, Motorströme und den Akkustrom ihres Gleichstrommodells. Die
Energiebilanz rechnet daraus den Strom, den der Akku wirklich liefert:

`I_Akku = I_Sim + (k(ω) − 1)·(I_Sim − P_aux/U) + (P_Avionik − P_aux + Σ P_Antrieb(ω_i)) / U`

- `P_Antrieb(ω) = P_Antrieb,S · ω/ω_Schweben` je Motor mit
  `P_Antrieb,S = 1,03 W · √(m_Motor/g) + 0,38 · max(0, Blätter − 3) · klein(D) · k_Q · ω_Schweben³`: Eisen-, Wirbelstrom-
  und Reglerverluste, die das Gleichstrommodell nicht kennt, plus die Blattinterferenz kleiner Props im Teillastbereich
  (`klein(D)` = 1 bis 35 mm, 0 ab 45 mm). Weil die Wellenleistung mit ω³ wächst, der Verlust aber nur mit ω, kostet das
  vierte Blatt im Schweben 38 %, bei Vollgas ≈ 10 % der Wellenleistung.
- `k_Prop` = Größenfaktor der Propeller-Energiebilanz, log-linear über 31/51/76/89/102/130/178/254/330 mm:
  0,84/0,78/0,72/0,67/0,80/1,02/1,0/0,93/0,93. Unterschiede zwischen Props derselben Größe bleiben erhalten.
- Seit 0.4.3 `k(ω) = k_Prop + (k_Voll − k_Prop)·x²` mit dem Schubanteil über dem Schweben
  `x = clamp((ω² − ω_S²)/(ω_Voll² − ω_S²))` und `k_Voll` log-linear auf denselben Stützstellen 0,84/1,05/1,0/1,0/1,0/
  1,02/1,0/0,93/0,93 (§0.3); im Spiel das Mittel der vier Motoren.
- `P_Avionik = 0,6 W (Flugcontroller, Empfänger) + Videoleistung + Zubehör`; Videoleistung aus `power_w`, sonst analog
  `1,2 W + 4,3 W/W · Sendeleistung`, digital `4,5 W + 0,1 W/g · Masse + 2,5 W/W · Sendeleistung` (Standard 200 mW).
- `P_aux` (alte Massenformel) bleibt in der Flugphysik unverändert, damit Busspannung und Handling bitgleich sind.

### 3.2 Nutzbare Energie und Landung
Integriert wird in 24 Ladungsschritten; Strom und Temperatur je Schritt beim mittleren Ladezustand. Seit 0.4.3 ist
jedes Profil je Schritt mindestens so fordernd wie das ruhigere (Akkustrom, Lasten und Wärme als Maximum mit Schweben
→ Reisen → gemischt → aggressiv, §0.2). Gelandet wird, sobald

- der Akku die nutzbare Kapazität (`usable_fraction`) abgegeben hat,
- die Zellspannung unter der Last des **ruhigen Flugabschnitts** (Schweben, Reisen bzw. der Reiseanteil von gemischt und
  aggressiv) die Landespannung erreicht – Mitte aus Leer- und Abschaltspannung der Chemie (LiPo/LiHV 3,4 V, Li-Ion 2,9 V)
  – oder
- die Zellspannung unter der **mittleren Last** des Profils die Abschaltspannung erreicht (LiPo/LiHV 3,3 V, Li-Ion 2,8 V).

Spannung = Leerlaufspannung beim Ladezustand minus Innenwiderstand mit C-Raten-Aufschlag und stationärer Polarisation,
geprüft an den Schrittgrenzen, linear interpoliert. Die Werkbank zeigt als „Landespannung“ die Zellspannung unter der
mittleren Last des gemischten Flugs beim Landen.

### 3.3 Flugzeiten (Definitionen)

| Kennzahl | Definition |
|---|---|
| Schweben | stationäres Schweben mit Abfluggewicht |
| Reisen | Geradeausflug mit klassentypischer Reisegeschwindigkeit, höchstens ein Anteil der Höchstgeschwindigkeit: Whoop 18,5 km/h (0,55), Toothpick 40 (0,45), Cinewhoop 30 (½), 5″ Freestyle 55 (0,4), 5″ Race 60 (0,4), Long Range 63 (0,55), X-Class 65 (½) |
| Gemischt | Zeitanteile Reisen / Schubstoß / Leerlauf je Rahmenrolle, Schubstoß mit festem Schub-Gewichts-Verhältnis (höchstens Vollgas): Whoop bis 35 mm 44/37/19 % bei 5 : 1 (also Vollgas), Whoop über 35 mm 75/20/5 bei 3 (0.4.2: 3,5), Toothpick 80/12/8 bei 4, Cinewhoop 83/12/5 bei 2,5 (0.4.2: 88/7/5), Freestyle 80/10/10 bei 5,5, Race 58/30/12 bei 6,5, Long Range 66/26/8 bei 3,8, X-Class 58/34/8 bei 4,2 |
| Aggressiv | Whoop bis 35 mm 46/49/5 bei 5 : 1 (Vollgas), Whoop über 35 mm 50/40/10 bei 3 (0.4.2: 3,5), Toothpick 65/23/12 bei 4,5, Cinewhoop 75/17/8 bei 3,5, Freestyle 72/14/14 bei 7, Race 50/38/12 bei 7, Long Range 58/32/10 bei 5, X-Class 45/45/10 bei 5 |

Weil der Schubstoß ein festes Vielfaches des Gewichts ist, fliegen starke Aufbauten (hohe KV, viel Schub) nicht
automatisch kürzer; wo der Stoß Vollgas braucht (65-mm-Whoops), zieht die höhere KV mehr Strom – wie in der Praxis.
Li-Ion-artige Zellen (`usable_fraction` ab 0,87) werden im Schubstoß des gemischten Flugs höchstens mit 6 C belastet
(seit 0.4.3, §0.7), im aggressiven Flug höchstens mit ihrem Dauerstrom.

### 3.4 Im Spiel
`PowerSystem` führt einen eigenen Akku-Ladezustand (`packSoc`, `packMah`, `packCurrent`) mit dem Strom aus §3.1. OSD,
Übersicht, Funken-HUD, Ladestand des Akku-Items, Warnungen (Akku schwach/kritisch/leer) und Rückholautomatik nutzen ihn
(`Telemetry.battery*`); angezeigte Spannung = Busspannung der Flugphysik minus Unterschied der Leerlaufspannung zwischen
Flugphysik- und Akku-Ladezustand. Ist der Akku 10 % unter leer (tiefentladen), schaltet die Drohne wie bei einem
Brownout ab. Die Motorspannung selbst folgt weiter dem Ladezustand der Flugphysik – so bleiben Flugverhalten und alle
Bewegungs-Goldens bitgleich. Beim nächsten Start übernimmt die Flugphysik den gespeicherten (Akku-)Ladestand.

### 3.5 Temperaturen
Die Flugphysik-Thermik bleibt. Die Werkbank zeigt „nach 60 s Vollgas im Stand“ (genau die Thermik des Spiels) und „nach
gemischtem Flug“; für den gemischten Flug rechnet die Analyse mit Fahrtwind: Kühlleitwert · (1 + 0,6 · √(v_Reise /
10 m/s)), also +43 % beim Whoop mit 18,5 km/h, +74 % beim 5″ mit 55 km/h, +81 % bei 65 km/h. Der alte Dauerwert (bis der
Akku leer ist) bleibt als `sustainedMotorTempC` für die Warnung `MOTOR_THERMAL`.

## 4. Ergebnis je Klasse

Referenzbereich = Klassenbereich aus §1 für Akku und Gewicht des Presets (mit den dort genannten Anpassungen).
„0.4.1“ = Propwash 0.4.1 / JMP 1.0.3, „erste Kalibrierung“ = erste Fassung von 0.4.2 / 1.0.4, „0.4.2“ = nach der
Nachkalibrierung (0.4.2 / 1.0.4), „0.4.3“ = Propwash 0.4.3 / JMP 1.0.5 (§0). Referenzbereich wie in 0.4.3 (geändert nur
Long Range mit Li-Ion gemischt 0,30–0,50 W/g, §0.7: `long_range_7`, `lr4_6s_lion`, `lr7_deadcat_lion`, `lr8_6s_lion`). Gemischt und aggressiv gab es in 0.4.1 nicht.
Release 0.4.0 / 1.0.0 (§0.8, §0.10): alle Zeilen wie 0.4.3 außer `cinewhoop25_4s` – Schweben 11:15, Reisen 9:50, gemischt
8:31, aggressiv 6:25 – und `toothpick35_6s` – Schweben 15:05, Reisen 12:54, gemischt 9:26, aggressiv 6:34.

| Aufbau | Klasse | Kennzahl | Referenzbereich | 0.4.1 | erste Kalibrierung | 0.4.2 | 0.4.3 |
|---|---|---|---|---|---|---|---|
| PW whoop_65 | 65-mm-Whoop 1S | Schweben | 2:52–4:36 | 7:13 | 4:11 | 3:54 | 3:54 |
| PW whoop_65 | 65-mm-Whoop 1S | Reisen | 2:26–4:08 | 6:16 | 3:57 | 3:30 | 3:30 |
| PW whoop_65 | 65-mm-Whoop 1S | Gemischt | 1:53–3:27 | – | 3:08 | 2:45 | 2:45 |
| PW whoop_65 | 65-mm-Whoop 1S | Aggressiv | 1:23–2:21 | – | 2:36 | 2:18 | 2:18 |
| PW cinewhoop_3 | Cinewhoop 2,5–3″ | Schweben | 4:36–8:03 | 4:57 | 5:13 | 5:34 | 5:34 |
| PW cinewhoop_3 | Cinewhoop 2,5–3″ | Reisen | 4:08–6:27 | 3:30 | 3:56 | 4:12 | 5:09 |
| PW cinewhoop_3 | Cinewhoop 2,5–3″ | Gemischt | 2:56–5:22 | – | 3:40 | 3:55 | 4:07 |
| PW cinewhoop_3 | Cinewhoop 2,5–3″ | Aggressiv | 2:09–3:35 | – | 2:29 | 2:40 | 2:41 |
| PW freestyle_5 | 5″ Freestyle | Schweben | 8:48–11:59 | 14:06 | 11:06 | 11:06 | 11:06 |
| PW freestyle_5 | 5″ Freestyle | Reisen | 6:36–9:46 | 6:29 | 7:56 | 7:56 | 7:56 |
| PW freestyle_5 | 5″ Freestyle | Gemischt | 4:48–6:36 | – | 5:40 | 5:40 | 5:40 |
| PW freestyle_5 | 5″ Freestyle | Aggressiv | 3:46–5:17 | – | 4:04 | 4:04 | 4:04 |
| PW race_5 | 5″ Race | Schweben | 9:05–13:37 | 16:13 | 12:07 | 12:07 | 12:07 |
| PW race_5 | 5″ Race | Reisen | 6:49–10:05 | 5:58 | 8:35 | 8:35 | 8:35 |
| PW race_5 | 5″ Race | Gemischt | 2:43–3:24 | – | 4:08 | 3:07 | 3:07 |
| PW race_5 | 5″ Race | Aggressiv | 1:57–2:43 | – | 2:44 | 2:23 | 2:23 |
| PW long_range_7 | Long Range 4–8″ | Schweben | 19:15–35:56 | 32:23 | 28:28 | 28:28 | 28:28 |
| PW long_range_7 | Long Range 4–8″ | Reisen | 14:34–24:30 | 22:44 | 18:06 | 18:06 | 18:06 |
| PW long_range_7 | Long Range 4–8″ | Gemischt | 10:47–17:58 | – | 12:00 | 11:02 | 15:02 |
| PW long_range_7 | Long Range 4–8″ | Aggressiv | 6:44–13:29 | – | 10:45 | 9:42 | 9:42 |
| JMP whoop75_1s | 75–85-mm-Whoop | Schweben | 5:54–8:02 | 11:40 | 6:34 | 7:11 | 7:04 |
| JMP whoop75_1s | 75–85-mm-Whoop | Reisen | 4:49–7:10 | 9:08 | 6:10 | 6:14 | 5:58 |
| JMP whoop75_1s | 75–85-mm-Whoop | Gemischt | 3:47–5:39 | – | 4:17 | 4:30 | 4:24 |
| JMP whoop75_1s | 75–85-mm-Whoop | Aggressiv | 2:57–4:25 | – | 3:16 | 3:32 | 3:29 |
| JMP whoop85_2s | 75–85-mm-Whoop | Schweben | 6:50–9:19 | 12:22 | 7:44 | 7:54 | 7:54 |
| JMP whoop85_2s | 75–85-mm-Whoop | Reisen | 5:35–8:19 | 7:24 | 7:29 | 7:20 | 7:20 |
| JMP whoop85_2s | 75–85-mm-Whoop | Gemischt | 4:24–6:33 | – | 4:50 | 4:58 | 5:26 |
| JMP whoop85_2s | 75–85-mm-Whoop | Aggressiv | 3:25–5:08 | – | 2:39 | 3:46 | 4:19 |
| JMP toothpick2_2s | Toothpick 2–3,5″ | Schweben | 7:38–15:16 | 10:58 | 7:56 | 8:39 | 8:39 |
| JMP toothpick2_2s | Toothpick 2–3,5″ | Reisen | 6:07–12:13 | 7:14 | 6:21 | 6:56 | 6:56 |
| JMP toothpick2_2s | Toothpick 2–3,5″ | Gemischt | 4:04–8:02 | – | 4:41 | 5:08 | 4:57 |
| JMP toothpick2_2s | Toothpick 2–3,5″ | Aggressiv | 3:03–6:07 | – | 3:12 | 3:36 | 3:20 |
| JMP toothpick3_4s | Toothpick 2–3,5″ | Schweben | 8:05–16:10 | 10:59 | 9:36 | 9:59 | 9:59 |
| JMP toothpick3_4s | Toothpick 2–3,5″ | Reisen | 6:28–12:56 | 6:56 | 7:37 | 7:57 | 7:57 |
| JMP toothpick3_4s | Toothpick 2–3,5″ | Gemischt | 4:19–8:31 | – | 5:48 | 6:07 | 5:54 |
| JMP toothpick3_4s | Toothpick 2–3,5″ | Aggressiv | 3:14–6:28 | – | 4:12 | 4:27 | 4:07 |
| JMP toothpick35_6s | Toothpick 2–3,5″ | Schweben | 8:59–17:58 | 13:39 | 11:56 | 13:52 | 13:52 |
| JMP toothpick35_6s | Toothpick 2–3,5″ | Reisen | 7:11–14:22 | 7:48 | 10:19 | 12:03 | 12:02 |
| JMP toothpick35_6s | Toothpick 2–3,5″ | Gemischt | 4:47–9:27 | – | 7:25 | 8:50 | 8:31 |
| JMP toothpick35_6s | Toothpick 2–3,5″ | Aggressiv | 3:36–7:11 | – | 5:09 | 6:13 | 5:45 |
| JMP cinewhoop25_4s | Cinewhoop 2,5–3″ | Schweben | 8:56–15:37 | 10:05 | 9:25 | 9:58 | 10:12 |
| JMP cinewhoop25_4s | Cinewhoop 2,5–3″ | Reisen | 8:01–12:30 | 7:53 | 7:37 | 8:06 | 8:48 |
| JMP cinewhoop25_4s | Cinewhoop 2,5–3″ | Gemischt | 5:41–10:25 | – | 7:13 | 7:41 | 7:31 |
| JMP cinewhoop25_4s | Cinewhoop 2,5–3″ | Aggressiv | 4:10–6:57 | – | 5:34 | 5:58 | 5:30 |
| JMP cinewhoop35_6s | Cinewhoop 3,5″ | Schweben | 8:36–12:53 | 9:26 | 9:02 | 10:40 | 11:25 |
| JMP cinewhoop35_6s | Cinewhoop 3,5″ | Reisen | 8:36–10:19 | 7:32 | 7:23 | 8:47 | 9:47 |
| JMP cinewhoop35_6s | Cinewhoop 3,5″ | Gemischt | 5:09–9:12 | – | 7:02 | 8:24 | 8:31 |
| JMP cinewhoop35_6s | Cinewhoop 3,5″ | Aggressiv | 4:18–7:22 | – | 5:29 | 6:37 | 6:27 |
| JMP bash5_6s | 5″ Freestyle | Schweben | 8:31–11:37 | 14:01 | 11:05 | 11:05 | 11:05 |
| JMP bash5_6s | 5″ Freestyle | Reisen | 6:24–9:28 | 6:05 | 7:40 | 7:40 | 7:40 |
| JMP bash5_6s | 5″ Freestyle | Gemischt | 4:39–6:24 | – | 5:34 | 5:34 | 5:34 |
| JMP bash5_6s | 5″ Freestyle | Aggressiv | 3:39–5:07 | – | 4:02 | 4:02 | 4:02 |
| JMP squashed5_6s | 5″ Freestyle | Schweben | 8:46–11:57 | 14:55 | 11:39 | 11:39 | 11:39 |
| JMP squashed5_6s | 5″ Freestyle | Reisen | 6:34–9:44 | 6:35 | 8:03 | 8:03 | 8:03 |
| JMP squashed5_6s | 5″ Freestyle | Gemischt | 4:47–6:34 | – | 6:02 | 6:02 | 6:02 |
| JMP squashed5_6s | 5″ Freestyle | Aggressiv | 3:45–5:15 | – | 4:29 | 4:29 | 4:29 |
| JMP deadcat5_6s | 5″ Freestyle | Schweben | 8:28–11:32 | 14:08 | 11:04 | 11:04 | 11:04 |
| JMP deadcat5_6s | 5″ Freestyle | Reisen | 6:21–9:24 | 6:49 | 7:55 | 7:55 | 7:55 |
| JMP deadcat5_6s | 5″ Freestyle | Gemischt | 4:37–6:21 | – | 5:51 | 5:51 | 5:51 |
| JMP deadcat5_6s | 5″ Freestyle | Aggressiv | 3:38–5:05 | – | 4:18 | 4:18 | 4:18 |
| JMP race5_4s | 5″ Race | Schweben | 8:36–12:54 | 16:07 | 11:32 | 11:32 | 11:32 |
| JMP race5_4s | 5″ Race | Reisen | 6:27–9:33 | 7:17 | 8:22 | 8:22 | 8:22 |
| JMP race5_4s | 5″ Race | Gemischt | 2:35–3:13 | – | 3:57 | 2:57 | 2:57 |
| JMP race5_4s | 5″ Race | Aggressiv | 1:51–2:35 | – | 2:31 | 2:14 | 2:14 |
| JMP race5_6s | 5″ Race | Schweben | 9:51–14:46 | 16:58 | 12:53 | 12:53 | 12:53 |
| JMP race5_6s | 5″ Race | Reisen | 7:23–10:57 | 5:49 | 9:56 | 9:56 | 9:56 |
| JMP race5_6s | 5″ Race | Gemischt | 2:57–3:42 | – | 4:23 | 3:15 | 3:15 |
| JMP race5_6s | 5″ Race | Aggressiv | 2:07–2:57 | – | 2:49 | 2:28 | 2:28 |
| JMP hframe5_6s | 5″ Freestyle | Schweben | 8:22–11:24 | 14:25 | 11:19 | 11:19 | 11:19 |
| JMP hframe5_6s | 5″ Freestyle | Reisen | 6:16–9:18 | 6:11 | 7:36 | 7:36 | 7:36 |
| JMP hframe5_6s | 5″ Freestyle | Gemischt | 4:34–6:16 | – | 5:39 | 5:39 | 5:39 |
| JMP hframe5_6s | 5″ Freestyle | Aggressiv | 3:35–5:01 | – | 4:09 | 4:09 | 4:09 |
| JMP lr4_6s_lion | Long Range 4–8″ | Schweben | 27:36–51:31 | 28:19 | 28:22 | 31:01 | 32:35 |
| JMP lr4_6s_lion | Long Range 4–8″ | Reisen | 20:53–35:07 | 24:46 | 23:23 | 25:39 | 23:01 |
| JMP lr4_6s_lion | Long Range 4–8″ | Gemischt | 15:27–25:45 | – | 16:43 | 17:50 | 16:56 |
| JMP lr4_6s_lion | Long Range 4–8″ | Aggressiv | 9:40–19:19 | – | 16:28 | 16:42 | 10:30 |
| JMP lr6_6s | Long Range 4–8″ | Schweben | 12:05–22:33 | 21:37 | 16:51 | 16:51 | 16:51 |
| JMP lr6_6s | Long Range 4–8″ | Reisen | 9:08–15:22 | 11:11 | 10:13 | 9:26 | 9:26 |
| JMP lr6_6s | Long Range 4–8″ | Gemischt | 6:09–8:27 | – | 7:56 | 6:58 | 6:58 |
| JMP lr6_6s | Long Range 4–8″ | Aggressiv | 4:14–6:09 | – | 5:54 | 5:08 | 5:08 |
| JMP lr7_deadcat_lion | Long Range 4–8″ | Schweben | 20:47–38:48 | 36:23 | 31:33 | 31:33 | 31:33 |
| JMP lr7_deadcat_lion | Long Range 4–8″ | Reisen | 15:44–26:27 | 26:44 | 21:06 | 20:43 | 20:43 |
| JMP lr7_deadcat_lion | Long Range 4–8″ | Gemischt | 11:38–19:24 | – | 12:28 | 11:32 | 15:56 |
| JMP lr7_deadcat_lion | Long Range 4–8″ | Aggressiv | 7:17–14:33 | – | 11:27 | 10:14 | 10:14 |
| JMP lr7_8s | Long Range 4–8″ | Schweben | 12:27–23:14 | 24:15 | 19:30 | 19:30 | 19:30 |
| JMP lr7_8s | Long Range 4–8″ | Reisen | 9:25–15:50 | 10:32 | 12:43 | 11:50 | 11:50 |
| JMP lr7_8s | Long Range 4–8″ | Gemischt | 6:20–8:43 | – | 9:13 | 8:02 | 8:02 |
| JMP lr7_8s | Long Range 4–8″ | Aggressiv | 4:21–6:20 | – | 6:38 | 5:45 | 5:45 |
| JMP lr8_6s_lion | Long Range 4–8″ | Schweben | 25:44–48:02 | 42:26 | 40:36 | 40:36 | 40:36 |
| JMP lr8_6s_lion | Long Range 4–8″ | Reisen | 19:28–32:45 | 31:55 | 27:55 | 27:35 | 27:35 |
| JMP lr8_6s_lion | Long Range 4–8″ | Gemischt | 14:25–24:01 | – | 15:00 | 13:36 | 18:18 |
| JMP lr8_6s_lion | Long Range 4–8″ | Aggressiv | 9:00–18:01 | – | 13:12 | 11:44 | 11:44 |
| JMP xclass10_6s | X-Class 10–13″ | Schweben | 15:38–26:03 | 23:54 | 21:32 | 21:32 | 21:32 |
| JMP xclass10_6s | X-Class 10–13″ | Reisen | 10:25–22:20 | 13:47 | 12:55 | 12:55 | 12:55 |
| JMP xclass10_6s | X-Class 10–13″ | Gemischt | 5:41–7:49 | – | 7:23 | 6:35 | 6:35 |
| JMP xclass10_6s | X-Class 10–13″ | Aggressiv | 3:28–5:41 | – | 4:24 | 4:30 | 4:30 |
| JMP xclass12_8s | X-Class 10–13″ | Schweben | 13:30–22:31 | 19:42 | 18:27 | 18:27 | 18:27 |
| JMP xclass12_8s | X-Class 10–13″ | Reisen | 9:00–19:18 | 14:58 | 14:14 | 14:14 | 14:14 |
| JMP xclass12_8s | X-Class 10–13″ | Gemischt | 4:55–6:45 | – | 6:47 | 6:00 | 6:00 |
| JMP xclass12_8s | X-Class 10–13″ | Aggressiv | 3:00–4:55 | – | 4:43 | 4:43 | 4:43 |
| JMP xclass13_8s | X-Class 10–13″ | Schweben | 11:22–18:57 | 18:02 | 17:04 | 17:04 | 17:04 |
| JMP xclass13_8s | X-Class 10–13″ | Reisen | 7:35–16:15 | 13:01 | 12:31 | 12:31 | 12:31 |
| JMP xclass13_8s | X-Class 10–13″ | Gemischt | 4:08–5:41 | – | 5:55 | 5:11 | 5:11 |
| JMP xclass13_8s | X-Class 10–13″ | Aggressiv | 2:32–4:08 | – | 3:37 | 3:46 | 3:46 |
| JMP xclass13h_8s | X-Class 10–13″ | Schweben | 10:35–17:38 | 16:08 | 15:28 | 15:28 | 15:28 |
| JMP xclass13h_8s | X-Class 10–13″ | Reisen | 7:03–15:07 | 11:47 | 11:28 | 11:28 | 11:28 |
| JMP xclass13h_8s | X-Class 10–13″ | Gemischt | 3:51–5:18 | – | 5:06 | 4:26 | 4:26 |
| JMP xclass13h_8s | X-Class 10–13″ | Aggressiv | 2:21–3:51 | – | 2:42 | 3:22 | 3:22 |

Alle Werte liegen in ihrem Bereich (harte Prüfung in `CalibrationHarnessTest`).

### 4.1 Owner-Testfall

In 0.4.3 unverändert (31-mm-Props: `k_Voll = k_Prop`; Profile der 65-mm-Whoops unverändert; alle Werte bitgleich).

Sharecode `PW1-040P7-…-X0`: `propwash:whoop_65`, `justmoreparts:aio_whoop_1s_6a`, `justmoreparts:m0702_27000`,
`propwash:p31_4b`, `justmoreparts:digital_micro`, `propwash:b1s_300_hv`.

| Kennzahl | Referenz | 0.4.1 | erste Kalibrierung | jetzt |
|---|---|---|---|---|
| Schweben (digital) | 3:00–3:20 (`builds.md` §0; analog 3:45–4:45) | 7:15 | 3:34 | 3:19 |
| Reisen (ruhig drinnen) | 3:00–3:30 | 5:50 | 3:24 | 3:01 |
| Gemischt | 2:00–2:30 | – | 2:26 | 2:02 |
| Aggressiv | 1:30–2:00 | – | 1:49 | 1:39 |
| Landespannung unter Last | 3,3–3,5 V | – | 3,45 V | 3,35 V |
| Gewicht | 25–30 g | 27,5 g | 27,5 g | 27,5 g (unverändert) |
| Vollgasstrom | 12–16 A | 12,5 A | 16,6 A | 15,4 A |
| Schub/Gewicht, Schwebegas | 4,5–6 : 1, 30–40 % | 4,1 / 43,8 % | 4,1 / 43,8 % | 4,1 / 43,8 % (flugwirksam, §2.4) |
| 23000 statt 27000 KV | gemischt und aggressiv +15–25 % (+20–40 s) | ±0 | gemischt +11 % (+16 s), aggressiv +24 % | gemischt +16 % (+19 s), aggressiv +21 % (+21 s) |
| 3-Blatt statt 4-Blatt | +10–20 % in allen Stilen, gemischt +15–30 s | +8 s | Schweben +4 %, Reisen +5 %, gemischt +9 % (+13 s), aggressiv +14 % | Schweben +11 %, Reisen +12 %, gemischt +14 % (+16 s), aggressiv +17 % (+17 s) |
| 2-Blatt statt 4-Blatt | +10–20 % in allen Stilen, gemischt +15–30 s | +31 s | Schweben +6,5 %, Reisen +7,8 %, gemischt +15 % (+22 s), aggressiv +25 % (+27 s) | Schweben +14 %, Reisen +15 %, gemischt +22 % (+27 s), aggressiv +27 % (+26 s) (§2.5) |
| Digital statt analog | Schweben −30–35 % (analog also +43–54 %) | −20 % | −25 % | −30 % (analog 4:46 statt 3:19, also +43 %) |

### 4.2 Owner-Beispiel „Deadcat LR 7″ · 8S“

In 0.4.3 unverändert (178-mm-Props, keine korrigierten Teile; Flugphysik und Analyse bitgleich).

| Kennzahl | Referenz | 0.4.1 | erste Kalibrierung | jetzt |
|---|---|---|---|---|
| Gewicht | 850–1100 g | 896,9 g | 896,9 g | 896,9 g |
| Schub/Gewicht | 9–13 | 11,7 | 11,7 | 11,7 |
| Schwebegas | 20–28 % | 21,7 % | 21,7 % | 21,7 % |
| Schweben | 15–23 min | 24,2 min | 19,5 min | 19,5 min |
| Reisen | 9–13 min (§0), 10–12 min (§16) | 10,5 min | 12,7 min | 11,8 min |
| Gemischt | 6–9 min (§0), 7–9 min (§9) | – | 9,2 min (über der Referenz) | 8,0 min |
| Aggressiv | 4–6 min (§0), 5–6 min (§9) | – | 6,6 min (über der Referenz) | 5,7 min |
| Topspeed | 140–170 km/h | 145 km/h | 145 km/h | 145 km/h |
| Vollgasstrom | 90–150 A | 137,5 A | 141,0 A | 141,0 A |
| Motortemperatur | 81–99 °C nach 60 s; nach Flug 40–60 °C | 124 °C („Dauervollgas“) | 95 °C nach 60 s, 52 °C nach gemischtem Flug | 95 °C nach 60 s, 41 °C nach gemischtem Flug |

### 4.3 Klassen-Stichproben aus dem Review

| Aufbau | Kennzahl | Referenz | erste Kalibrierung | 0.4.2 | 0.4.3 |
|---|---|---|---|---|---|
| `propwash:race_5` | gemischt | ≈ 3:04 (3 min bei 520 g, 6S 1300, skaliert) | 4:08 | 3:07 | unverändert |
| `justmoreparts:race5_6s` | gemischt | ≈ 3:20 | 4:23 | 3:15 | unverändert |
| `justmoreparts:race5_4s` | gemischt | ≈ 2:54 | 3:57 | 2:57 | unverändert |
| `justmoreparts:lr6_6s` | gemischt / aggressiv | ≤ 7:55 / ≤ 5:39 | 7:56 / 5:54 | 6:58 / 5:08 | unverändert |
| `propwash:whoop_65` | Reisen / gemischt / aggressiv | 3:00–3:30 / 2:00–2:45 / 1:30–2:00 (≤ 2:30 mit KV-Bonus) | 3:57 / 3:08 / 2:36 | 3:30 / 2:45 / 2:18 | unverändert |
| `propwash:cinewhoop_3` | Reisen | 0,25–0,39 W/g | 0,41 W/g | 0,38 W/g (4:12) | 0,31 W/g (5:09) |
| `justmoreparts:cinewhoop35_6s` | Reisen | 0,25–0,30 W/g (8:40–10:20) | 7:23 (0,35 W/g) | 8:47 (0,29 W/g) | 9:47 (0,26 W/g) |
| `justmoreparts:xclass13_8s` | gemischt | ≈ 5 min | 5:55 | 5:11 | unverändert |
| 5″ Freestyle | Motor nach gemischtem Flug | 40–60 °C | 68–70 °C | 50–51 °C | unverändert |
| 5″ Race | Motor nach gemischtem Flug | 40–60 °C (≤ 70 °C) | 65–75 °C | 53–60 °C | unverändert |
| X-Class | Motor nach gemischtem Flug | 40–60 °C, Obergrenze 65–70 °C, unter dem 60-s-Wert | 66/81/86/92 °C | 50/59/63/68 °C | unverändert |

## 5. Alle Presets

„R“ = Release Propwash 0.4.0 / JMP 1.0.0 (§0.8), nur wo anders als 0.4.3.

| Aufbau | Masse g | S/G | Schwebegas % | Schweben 0.4.1 → 1. Kal. → 0.4.2 → 0.4.3 | Reisen 0.4.1 → 1. Kal. → 0.4.2 → 0.4.3 | Gemischt 1. Kal. → 0.4.2 → 0.4.3 | Aggressiv 1. Kal. → 0.4.2 → 0.4.3 | I Schweben A 0.4.1 → 0.4.2 → 0.4.3 | I Vollgas A 0.4.1 → 0.4.2 → 0.4.3 | T Motor °C 60 s / gemischt: 1. Kal. → 0.4.2 → 0.4.3 |
|---|---|---|---|---|---|---|---|---|---|---|
| PW whoop_65 | 28,1 | 3,2 | 55,0 | 7:13 → 4:11 → 3:54 → 3:54 | 6:16 → 3:57 → 3:30 → 3:30 | 3:08 → 2:45 → 2:45 | 2:36 → 2:18 → 2:18 | 1,81 → 3,54 → 3,54 | 8,2 → 10,6 → 10,6 | 30 / 28 → 30 / 27 → 30 / 27 |
| PW cinewhoop_3 | 286,7 | 4,7 | 36,4 | 4:57 → 5:13 → 5:34 → 5:34 | 3:30 → 3:56 → 4:12 → 5:09 | 3:40 → 3:55 → 4:07 | 2:29 → 2:40 → 2:41 | 5,80 → 5,16 → 5,16 | 65,2 → 49,2 → 67,4 | 103 / 57 → 104 / 45 → 102 / 46 |
| PW freestyle_5 | 525,2 | 10,6 | 23,4 | 14:06 → 11:06 → 11:06 → 11:06 | 6:29 → 7:56 → 7:56 → 7:56 | 5:40 → 5:40 → 5:40 | 4:04 → 4:04 → 4:04 | 4,09 → 5,19 → 5,19 | 109,7 → 115,6 → 115,6 | 93 / 70 → 93 / 51 → 93 / 51 |
| PW race_5 | 430,2 | 13,3 | 19,2 | 16:13 → 12:07 → 12:07 → 12:07 | 5:58 → 8:35 → 8:35 → 8:35 | 4:08 → 3:07 → 3:07 | 2:44 → 2:23 → 2:23 | 3,01 → 4,03 → 4,03 | 118,3 → 124,7 → 124,7 | 94 / 75 → 94 / 59 → 94 / 59 |
| PW long_range_7 | 846,2 | 5,8 | 27,7 | 32:23 → 28:28 → 28:28 → 28:28 | 22:44 → 18:06 → 18:06 → 18:06 | 12:00 → 11:02 → 15:02 | 10:45 → 9:42 → 9:42 | 5,23 → 6,47 → 6,47 | 80,1 → 84,3 → 84,3 | 82 / 78 → 82 / 55 → 82 / 52 |
| JMP whoop75_1s | 32,9 | 4,1 → 4,8 | 43,6 → 34,9 | 11:40 → 6:34 → 7:11 → 7:04 | 9:08 → 6:10 → 6:14 → 5:58 | 4:17 → 4:30 → 4:24 | 3:16 → 3:32 → 3:29 | 1,68 → 2,89 → 2,93 | 12,6 → 13,5 → 21,3 | 36 / 31 → 36 / 29 → 49 / 32 |
| JMP whoop85_2s | 56,7 | 6,8 | 27,8 | 12:22 → 7:44 → 7:54 → 7:54 | 7:24 → 7:29 → 7:20 → 7:20 | 4:50 → 4:58 → 5:26 | 2:39 → 3:46 → 4:19 | 1,59 → 2,63 → 2,63 | 28,5 → 26,1 → 31,8 | 47 / 34 → 47 / 31 → 47 / 31 |
| JMP toothpick2_2s | 57,1 | 6,3 | 30,3 | 10:58 → 7:56 → 8:39 → 8:39 | 7:14 → 6:21 → 6:56 → 6:56 | 4:41 → 5:08 → 4:57 | 3:12 → 3:36 → 3:20 | 1,79 → 2,40 → 2,40 | 28,6 → 25,4 → 33,0 | 38 / 32 → 38 / 29 → 53 / 34 |
| JMP toothpick3_4s | 142,8 | 6,6 | 33,0 | 10:59 → 9:36 → 9:59 → 9:59 | 6:56 → 7:37 → 7:57 → 7:57 | 5:48 → 6:07 → 5:54 | 4:12 → 4:27 → 4:07 | 2,62 → 2,88 → 2,88 | 38,4 → 30,0 → 40,8 | 59 / 42 → 59 / 35 → 59 / 35 |
| JMP toothpick35_6s | 222,5 | 7,6 → R 8,9 | 28,0 → R 24,1 | 13:39 → 11:56 → 13:52 → 13:52 → R 15:05 | 7:48 → 10:19 → 12:03 → 12:02 → R 12:54 | 7:25 → 8:50 → 8:31 → R 9:26 | 5:09 → 6:13 → 5:45 → R 6:34 | 2,43 → 2,40 → 2,40 → R 2,20 | 46,3 → 33,1 → 48,3 → R 55,2 | 81 / 51 → 82 / 41 → 81 / 41 → R 78 / 38 |
| JMP cinewhoop25_4s | 193,3 | 4,0 → 5,8 → R 6,0 | 47,5 → 32,2 → R 33,1 | 10:05 → 9:25 → 9:58 → 10:12 → R 11:15 | 7:53 → 7:37 → 8:06 → 8:48 → R 9:50 | 7:13 → 7:41 → 7:31 → R 8:31 | 5:34 → 5:58 → 5:30 → R 6:25 | 3,73 → 3,77 → 3,69 → R 3,35 | 25,9 → 21,1 → 56,1 → R 48,1 | 47 / 39 → 47 / 34 → 82 / 38 → R 68 / 36 |
| JMP cinewhoop35_6s | 351,4 | 4,1 → 6,8 | 47,8 → 29,1 | 9:26 → 9:02 → 10:40 → 11:25 | 7:32 → 7:23 → 8:47 → 9:47 | 7:02 → 8:24 → 8:31 | 5:29 → 6:37 → 6:27 | 3,99 → 3,52 → 3,29 | 24,9 → 18,3 → 63,0 | 67 / 55 → 67 / 44 → 91 / 41 → R 82 / 39 |
| JMP bash5_6s | 541,8 | 11,5 | 21,6 | 14:01 → 11:05 → 11:05 → 11:05 | 6:05 → 7:40 → 7:40 → 7:40 | 5:34 → 5:34 → 5:34 | 4:02 → 4:02 → 4:02 | 4,11 → 5,20 → 5,20 | 124,9 → 131,3 → 131,3 | 91 / 70 → 91 / 51 → 91 / 51 |
| JMP squashed5_6s | 527,0 | 11,9 | 22,0 | 14:55 → 11:39 → 11:39 → 11:39 | 6:35 → 8:03 → 8:03 → 8:03 | 6:02 → 6:02 → 6:02 | 4:29 → 4:29 → 4:29 | 3,86 → 4,95 → 4,95 | 112,6 → 118,8 → 118,8 | 87 / 68 → 87 / 50 → 87 / 50 |
| JMP deadcat5_6s | 545,8 | 10,8 | 24,0 | 14:08 → 11:04 → 11:04 → 11:04 | 6:49 → 7:55 → 7:55 → 7:55 | 5:51 → 5:51 → 5:51 | 4:18 → 4:18 → 4:18 | 4,08 → 5,21 → 5,21 | 101,5 → 107,3 → 107,3 | 84 / 68 → 84 / 50 → 84 / 50 |
| JMP race5_4s | 413,1 | 11,1 | 21,9 | 16:07 → 11:32 → 11:32 → 11:32 | 7:17 → 8:22 → 8:22 → 8:22 | 3:57 → 2:57 → 2:57 | 2:31 → 2:14 → 2:14 | 4,12 → 5,76 → 5,76 | 124,7 → 133,0 → 133,0 | 75 / 65 → 75 / 53 → 75 / 53 |
| JMP race5_6s | 468,9 | 13,9 | 17,6 | 16:58 → 12:53 → 12:53 → 12:53 | 5:49 → 9:56 → 9:56 → 9:56 | 4:23 → 3:15 → 3:15 | 2:49 → 2:28 → 2:28 | 3,40 → 4,47 → 4,47 | 154,1 → 161,6 → 161,6 | 95 / 75 → 95 / 60 → 95 / 60 |
| JMP hframe5_6s | 552,0 | 11,9 | 21,1 | 14:25 → 11:19 → 11:19 → 11:19 | 6:11 → 7:36 → 7:36 → 7:36 | 5:39 → 5:39 → 5:39 | 4:09 → 4:09 → 4:09 | 4,00 → 5,09 → 5,09 | 125,6 → 132,1 → 132,1 | 91 / 69 → 91 / 51 → 91 / 51 |
| JMP lr4_6s_lion | 442,8 | 3,6 → 4,5 | 51,0 → 37,0 | 28:19 → 28:22 → 31:01 → 32:35 | 24:46 → 23:23 → 25:39 → 23:01 | 16:43 → 17:50 → 16:56 | 16:28 → 16:42 → 10:30 | 4,48 → 4,44 → 4,23 | 27,6 → 23,7 → 47,0 | 73 / 58 → 73 / 45 → 74 / 41 → R 68 / 39 |
| JMP lr6_6s | 693,2 | 10,1 | 25,5 | 21:37 → 16:51 → 16:51 → 16:51 | 11:11 → 10:13 → 9:26 → 9:26 | 7:56 → 6:58 → 6:58 | 5:54 → 5:08 → 5:08 | 4,51 → 5,79 → 5,79 | 109,3 → 114,4 → 114,4 | 93 / 68 → 93 / 50 → 93 / 50 |
| JMP lr7_deadcat_lion | 881,7 | 5,2 | 27,7 | 36:23 → 31:33 → 31:33 → 31:33 | 26:44 → 21:06 → 20:43 → 20:43 | 12:28 → 11:32 → 15:56 | 11:27 → 10:14 → 10:14 | 5,23 → 6,55 → 6,55 | 73,7 → 78,1 → 78,1 | 70 / 64 → 70 / 48 → 70 / 45 |
| JMP lr7_8s | 896,9 | 11,7 | 21,7 | 24:15 → 19:30 → 19:30 → 19:30 | 10:32 → 12:43 → 11:50 → 11:50 | 9:13 → 8:02 → 8:02 | 6:38 → 5:45 → 5:45 | 4,02 → 5,00 → 5,00 | 137,5 → 141,0 → 141,0 | 95 / 52 → 95 / 41 → 95 / 41 |
| JMP lr8_6s_lion | 1329,7 | 4,9 | 32,9 | 42:26 → 40:36 → 40:36 → 40:36 | 31:55 → 27:55 → 27:35 → 27:35 | 15:00 → 13:36 → 18:18 | 13:12 → 11:44 → 11:44 | 8,38 → 9,50 → 9,50 | 108,0 → 108,9 → 108,9 | 93 / 63 → 93 / 46 → 93 / 45 |
| JMP xclass10_6s | 1704,0 | 8,5 | 25,7 | 23:54 → 21:32 → 21:32 → 21:32 | 13:47 → 12:55 → 12:55 → 12:55 | 7:23 → 6:35 → 6:35 | 4:24 → 4:30 → 4:30 | 9,27 → 10,28 → 10,28 | 220,7 → 210,9 → 210,9 | 91 / 66 → 91 / 50 → 91 / 50 |
| JMP xclass12_8s | 2629,7 | 5,5 | 39,2 | 19:42 → 18:27 → 18:27 → 18:27 | 14:58 → 14:14 → 14:14 → 14:14 | 6:47 → 6:00 → 6:00 | 4:43 → 4:43 → 4:43 | 11,24 → 12,00 → 12,00 | 121,5 → 116,7 → 116,7 | 74 / 81 → 74 / 59 → 74 / 59 |
| JMP xclass13_8s | 3123,7 | 5,9 | 35,7 | 18:02 → 17:04 → 17:04 → 17:04 | 13:01 → 12:31 → 12:31 → 12:31 | 5:55 → 5:11 → 5:11 | 3:37 → 3:46 → 3:46 | 12,28 → 12,97 → 12,97 | 160,4 → 153,2 → 153,2 | 86 / 86 → 86 / 63 → 86 / 63 |
| JMP xclass13h_8s | 3355,7 | 5,7 | 35,4 | 16:08 → 15:28 → 15:28 → 15:28 | 11:47 → 11:28 → 11:28 → 11:28 | 5:06 → 4:26 → 4:26 | 2:42 → 3:22 → 3:22 | 13,72 → 14,31 → 14,31 | 181,0 → 172,3 → 172,3 | 93 / 92 → 93 / 68 → 93 / 68 |
| owner | 27,5 | 4,1 | 43,8 | 7:15 → 3:34 → 3:19 → 3:19 | 5:50 → 3:24 → 3:01 → 3:01 | 2:26 → 2:02 → 2:02 | 1:49 → 1:39 → 1:39 | 1,80 → 4,16 → 4,16 | 12,5 → 15,4 → 15,4 | 32 / 29 → 32 / 28 → 32 / 28 |

## 6. Goldens und Tests

Release 0.4.0 / 1.0.0, Nachtrag 2026-10-02 (Kamerahalter `race_5_stretched`, `props-in-view.md` §5.1): Propwash-Goldens unverändert
(nur JMP-Daten geändert). `tools/calibration.sh` mit JMP-Daten: `calibration.txt` byte-gleich, `motion.txt` mit
1245 Varianten – `acro`, `angle`, `drop` bitgleich, `spec` nur bei den 94 Varianten auf `race_5_stretched` anders
(`lensUp`, `lensForward`, Kamerapunkt der Hülle).

Release 0.4.0 / 1.0.0 (§0.8): Propwash-Goldens unverändert (nur JMP-Daten geändert). JMP: Pro/Contra-Prüfungen der
Geschwister-Props, `m2004_1700` 82 °C, `cw_25_duct` „rund 11 min Schweben“, `tp_25_x` „rund 100 km/h auf 4S“,
Toothpick-Korridore nach `builds.md` §0 (S/G bis 9, Schwebegas ab 20 %), Referenzaufbau „Whoop 75 2S 300“ mit
`BATTERY_OVERLOAD`/`HEAVY_SAG` als erlaubtem Contra, 0802/15500-Referenz auf 2S 450 digital.

0.4.3 (§0.6): `airframe_golden.txt` nur die 30 Spezifikationszeilen des `cinewhoop_3`-Rahmens und die vier
Cinewhoop-`analysis:`-Zeilen; `builtin_regression.txt` nur die Cinewhoop-Zeilen (`acro`, `angle`, `drop`, `preset` je
vier, `worn`, `full`, `full-thermal`) und die `analysis:`-Zeilen mit `p3_3b`; `builtin_definitions.txt` nur die
Aerodaten von `cinewhoop_3`. `DerivationTest`, `OpenLoopPhysicsTest` (Fallgeschwindigkeit 15,2 m/s, Punch 19,6 m/s),
`TopSpeedTest` (80–100 km/h geradeaus, Sturzflug bis 115 km/h) und `FlightCharacterizationTest` (80–100 km/h) mit den
Cinewhoop-Werten; `FrameGeometryTest` kopiert jetzt auch Seiten- und Draufsicht. Neu: `FlightTimeOrderTest`,
`MotionFingerprintTest`, `content.WorkbenchStatsFitTest`. JMP: `bench.FlightTimeOrderTest`, Korridore der Cinewhoops nach
`builds.md` §0 (S/G 4–7, Schwebegas 23–35 %, Topspeed 80–150 km/h), Pro/Contra-Prüfungen nach den neuen Werten.
Nachtrag §0.7: Goldens unverändert (die Analyse-Hashes enthalten gemischt und aggressiv nicht, `cw_35_duct` ist kein
eingebautes Teil); `DerivationTest` Long Range gemischt 15,04 statt 11,03 min (Spezifikation Tabelle 13); Harness mit dem
Li-Ion-Long-Range-Fenster statt der 4″-Sonderregel; neu `sim.LowRatePunchTest` (Li-Ion gemischt höchstens 6 C, aggressiv bis
zum Dauerstrom, LiPo unbegrenzt, eingebauter Long Range gemischt/Schweben 1,7–2,3); JMP `FrameBenchTest` 3,5″-Cinewhoop
85–95 km/h („rund 90 km/h“).

Bis 0.4.2:

- `airframe_golden.txt`: alle 150 Spezifikationszeilen (Flugphysik-Parameter) unverändert; nur `analysis:`-Zeilen
  (Analysewerte der Presets) bewusst neu (erste Kalibrierung 18, Nachkalibrierung 9).
- `builtin_regression.txt`: `acro`, `angle`, `drop`, `worn`, `wear` (je Frame Flugbahn, Lage, Drehzahlen, Telemetrie
  inklusive Motortemperatur) unverändert; neu sind nur `analysis:`-, `preset:`-Zeilen (Analysewerte) sowie `full:`/
  `full-thermal:` (Vollgas im Stand bis der Akku leer ist – endet mit den Akku-Warnungen der Energiebilanz; in der
  Nachkalibrierung nur Whoop und Cinewhoop, deren Prop-Energiefaktor sich geändert hat).
- `builtin_definitions.txt`: nur die Felder `usableFraction` (3 Chemien) und `powerW` (4 Videoteile, `analog_nano` jetzt
  1,9 W).
- `DerivationTest`-Tabelle (Spezifikation §13) mit den neuen Werten.
- `FlightCharacterization.hoverMinutes` misst im Flug bis zur nutzbaren Kapazität oder Landespannung des Akkus
  (dyn./stat. 0,95–1,05).
- `CalibrationHarnessTest`: Fenster nach §1, Owner-Prüfungen nach §1, `ownerWhoopKeepsItsFlightPhysics`.
- JMP: `OwnerFlightTimeTest` mit denselben Owner-Grenzen, Pro/Contra-Prüfungen und Zielkorridore mit den neuen Zahlen.

## 7. API

0.4.3 ändert die API nicht (Parts-API bleibt 1.2.0). Neu intern: `AirframeSpec.propFullFactor`, `fullLoadOmega`.

Parts-API 1.2.0 (Propwash 0.4.2): `VideoDefinition#powerW()` / `Builder#powerWatts(double)` / JSON `power_w`,
`CellChemistry#usableFraction()` / `effectiveUsableFraction()` / `landingCellVoltage()` / JSON `usable_fraction`,
`BuildStats` mit `mixedFlightTimeMin`, `aggressiveFlightTimeMin`, `hoverCurrentA`, `cruiseCurrentA`, `mixedCurrentA`,
`cruiseSpeedKmh`, `landingCellVoltage`, `avionicsPowerW`, `motorTempMinuteC`, `mixedMotorTempC` (alter Konstruktor bleibt).
Damit sind beide API-Wünsche aus `power-video.md` §7 erfüllt.
