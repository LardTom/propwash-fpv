# Realismus-Referenz: Props im FPV-Bild (Rechercheur „props-in-view“)

Stand: 2026-10-02 (Nachkorrektur des JMP-Race-Stretch-Rahmens, Abschnitt 5.1; Sichtfeld vom Videomodul und Endstand aller 30 Presets, Abschnitte 9 und 9.4). Anlass: Rückmeldung zu Propwash 0.4.0 und Just More Parts 1.0.0: In keiner
Drohne waren mehr Props im Bild zu sehen. Erwartet war, dass die Sichtbarkeit der Props am Rahmen und an der
Kameraposition hängt (bei der 5″-Grunddrohne Props im Bild, bei einer Race-Drohne mit eng stehenden Armen mehr als bei
einem normalen X-Rahmen).

**Nachtrag 02.10.2026 (Abschnitt 9):** Seitdem rechnen Bild und Analyse mit dem Sichtfeld der Kamera im Videomodul
statt mit dem Brillen-Sichtfeld, und der Kamerawinkel gehört zur Drohne (Wartungsmenü, Bereich je Rahmenklasse). Die
Werte in den Abschnitten 0–7 sind der Stand vom 01.10.; den Endstand von Propwash 0.4.0 / JMP 1.0.0 mit den
nachgezogenen Kamerahaltern von `whoop_65` und `cinewhoop_3` zeigt Abschnitt 9.4 – alle 30 Presets liegen in ihrem
Zielbereich.

Die Erwartung stimmt. Geändert wurden nur **Daten** (Kamerahalter der Rahmen, bei drei eingebauten Rahmen die Höhe der
gezeichneten Kamera); Flugmodell, Analyse-Code und Renderer sind unverändert, alle Bewegungs-Fingerabdrücke bleiben
bitgleich (Abschnitt 7).

---

## 0. Kurzfassung

- **Ursache.** Rahmen ohne `camera.pos` – alle fünf eingebauten und 16 der 22 JMP-Rahmen – bekamen das Objektiv der
  Altformel: 15 mm über dem Schwerpunkt, 0,7 × Motorabstand vor der Mitte. Mit dem Akku oben liegt der Schwerpunkt hoch;
  das FPV-„Auge“ schwebte dadurch meist **9–28 mm über der Propebene**, auf Höhe der Deckplatte oder des Akkus und bis zu
  38 mm über der gezeichneten Kamera. Von oben sieht man die Propscheiben nur als flache Ellipsen ganz unten im Bild; mit
  Uptilt rutschen sie hinaus. Deshalb zeigte der eingebaute Racer (38° Uptilt) 0 %, der JMP-Race-Stretch 0–1,9 %, und die
  übrigen Werte (9–21 %) lagen als blasse Flächen am unteren Rand, wo die weiche Unschärfe-Scheibe kaum auffällt.
- **Korrektur.** Jeder Rahmen hat jetzt einen Kamerahalter `camera.pos`: das Objektiv sitzt in der Kamera, bei X-Rahmen
  wenige Millimeter **unter** der Propebene (die Kamera hängt im Käfig zwischen den Platten, die Props sitzen oben auf den
  Motoren), bei Whoops knapp **über** den Ducts (Kamera im Canopy). Bei den eingebauten Rahmen liegt das Objektiv 10 mm
  vor der Mitte der gezeichneten Kamera auf ihrer optischen Achse; Race und Cinewhoop sitzen dafür 2 bzw. −1,5 mm anders
  im Käfig, die Whoop-Kamera 0,5 mm höher.
- **Ergebnis.** Die Props liegen jetzt als Keile links und rechts an der Horizontlinie (von unten gesehen) bzw. bei
  Whoops unten in den Ecken – so viel, wie Rahmenform und Kamerahalter hergeben: Deadcat und H-Frame 0 %, Squashed 3 %,
  True-X-5″ 8 %, Stretched-Race 11–13 %, Whoops 6–9 %, Cinewhoops 7 %, Toothpicks 4–8 %, Long Range 6–9 %, X-Class
  2–7 % (Tabelle Abschnitt 5). Mit **gleichen Teilen und gleicher Kamera** zeigt der Stretched-Race-Rahmen 13,4 %, der
  True-X 8,4 %, der Squashed 3,0 %, Deadcat und H 0 % – vorher war es umgekehrt (Stretched 0,7 %, True-X 11,7 %).
- **Prüfung.** Selbsttest „drone-render: Props im FPV-Bild – Analyse gegen gerendertes Bild“ mit allen 30 Presets
  (eingebaut, Beispiel-Addon, Just More Parts): gezählte Pixel und Analyse höchstens 0,08 Prozentpunkte auseinander; im
  Bildschirmfoto-Modus ein Bild je Rahmen (`docs/screenshots/fpv_coverage/`) und je eingebauter Klasse ein Flugbild
  (`docs/screenshots/fpv_props_*.png`).

---

## 1. Quellen (reale Welt)

| # | Quelle | Aussage (wörtlich oder sinngemäß) | Folgerung für Propwash |
|---|---|---|---|
| 1 | hobbydrone.cz, „FPV Frame Geometry 2026: True X, Deadcat, Stretch X Explained“ – https://www.hobbydrone.cz/blog/frame-geometry--deadcat--squished-x--or-true-x/ | True X: „Props often in shot at low camera angles … especially with wide-angle cameras“; Squished X: „Partially visible – depends on FOV and tilt“; Stretch X: „Props typically visible in shot“; Deadcat: „Completely clean footage“; H-frame: „Props usually visible in shot“; Micro/Toothpick: „Often clean depending on camera tilt“; Sichtbarkeit hängt an „FOV width, camera tilt angle, frame size, and mount used“ | Reihenfolge Stretched > True-X > Squashed > Deadcat; Toothpicks wenig |
| 2 | Oscar Liang, „What to Consider in FPV Drone Frames“ – https://oscarliang.com/fpv-drone-frames/ | Deadcat: „designed to keep the propellers out of the camera's view“; True-X (Manta 5 SE): „you will see the propellers in your FPV feed which is common for X frames“; Freestyle 20–30°, Racer bis 60° Uptilt | True-X 5″ zeigt sichtbar Props, Deadcat keine |
| 3 | Oscar Liang, Test AxisFlying Manta 5 SE – https://oscarliang.com/axisflying-manta-5-se-frame/ | „You might also get props in the camera's view because of how far back the camera is seated relative to the motors … if your camera angle is low, particularly with wide FOV cameras like the DJI O3.“ | Lage der Kamera zur Motorlinie entscheidet; weites FOV zeigt mehr |
| 4 | Oscar Liang, 5″-BNF-Vergleich DJI O4 Pro – https://oscarliang.com/5inch-bnf-dji-o4-pro-comparison-feb-2025/ | Deadcat „much better at keeping props out of the camera's view compared to a True-X“; True-X Afterburner: „You will, for sure, get props in camera's view.“ | True-X mit Digitalsystem: Props im Bild sind normal |
| 5 | Oscar Liang, Test FlyFishRC Volador VX5/VD5 – https://oscarliang.com/flyfishrc-volador-vx5-vd5-frames/ | VD5 (Deadcat): „the props are not visible in the ultra-wide recorded video of the O3 Air Unit, provided the correct mounting holes are used“ | Deadcat 0 %, auch bei weitem FOV, wenn die Kamera vorn sitzt |
| 6 | UAVMODEL, „FPV Frame Geometry: Deadcat vs True X vs Squashed X – Prop-View …“ – https://blog.uavmodel.com/fpv-frame-geometry-deadcat-vs-true-x-vs-squashed-x-prop-view-resonance-and-flight-feel-2026-guide/ | Deadcat „Zero props in shot“; True X sieht „two front props in the upper corners“; Squashed: „Front props partially visible … better than True X because the wider front stance pushes props outward“; Stretched „Props in shot“ | Squashed weniger als True-X, Stretched mehr |
| 7 | UAV Drone Academy, „True-X vs Stretched-X“ – https://www.uavdroneacademy.com/en/blog/true-x-vs-stretched-x-fpv-frame-layout | Dead Cat „moves the front propellers out of the camera's field of view“ | wie 2, 5 |
| 8 | GetFPV Learn, „All About Multirotor FPV Drone Frames“ – https://www.getfpv.com/learn/new-to-fpv/all-about-multirotor-fpv-drone-frame/ (nur über die Suchmaschine gelesen, Seite sperrt Abrufe) | H-Frame: „the H shape means the front propellers will stay out of your camera's field of vision“; X-Rahmen „push the front motors forward enough that you can see them“ | H-Frame mit Kamera vorn am Rumpf: 0 % (Quelle 1 widerspricht für H-Frames mit Kamera zwischen den Armen) |
| 9 | Rotorbuilds, „Pickle Toothpick 2022 3″“ – https://rotorbuilds.com/build/30459 (Suchtreffer) | „less than a millimeter of clearance between the props and the outer wall of the canopy“ | Toothpick-Props laufen dicht neben der Kamera, aber fast auf Objektivhöhe: schmale Keile |
| 10 | Canopy-Angebote für Tiny Whoops (Suchtreffer, z. B. https://makerworld.com/en/models/2860834-tiny-whoop-canopy-for-c03-camera-many-angles) | flache Canopies erlauben den Blick „down between the ducts“; niedriger Kamerawinkel zeigt die Ducts unten in den Ecken | schwach belegt; geometrisch klar (Kamera über den Ducts): Whoops zeigen Props/Ducts unten in den Ecken, mehr bei wenig Uptilt |

Die Quellen nennen keine Prozentwerte. Die Zielbereiche in Abschnitt 3 sind daraus abgeleitet und so gewählt, dass
„Props im Bild“ auffällt, aber kein Pilot so fliegen würde, dass die Props ein Viertel des Bilds verdecken (Warnung
`PROPS_IN_VIEW` erst ab 30 %).

---

## 2. Geometrie: wovon „Props im Bild“ abhängt

Propwash zeichnet drehende Props als flache Scheiben (Nabe bis Blattspitze) 0,55 × Nabenhöhe über dem Motor;
`CameraOcclusion` integriert ihre Fläche im 16:9-Bild der Brille exakt (Flugmodell-Spezifikation 17.7). Daraus folgt:

1. **Höhe des Objektivs gegen die Propebene** ist der stärkste Hebel. Liegt das Objektiv genau in der Propebene, sieht
   die Kamera die Scheiben hochkant (fast 0 %). Jeder Millimeter Abstand öffnet die Scheiben: beim 5″-Racer bringt 1 mm
   rund 4–5 Prozentpunkte. Reale Kameras sitzen im Käfig zwischen den Platten, die Props oben auf den Motoren – das
   Objektiv liegt bei X-Rahmen einige Millimeter **unter** der Propebene. Bei Whoops und vielen Cinewhoops mit Kamera im
   Canopy liegt es **darüber**.
2. **Vorderprops nah an der Kameraachse** (Stretched-X, schmale Race-Rahmen): die Innenkanten der Vorderscheiben laufen
   knapp vor der Kamera durch, die Keile reichen bis nahe an die Bildmitte. **Breite Vorderarme** (Squashed-X, Deadcat,
   H-Frame) schieben die Scheiben nach außen, über den Bildrand hinaus.
3. **Kamera vor der Motorlinie** (Deadcat 5″ 50 mm, H-Frame 22 mm, X-Class-H 50 mm vor den Vorderarmen): die Scheiben
   liegen seitlich hinter dem Objektiv – 0 %.
4. **Uptilt** wirkt je nach Lage: Objektiv **unter** den Props (X-Rahmen) – mehr Uptilt zeigt mehr von der
   Scheibenunterseite (eingebauter 5″: 0° 7,6 %, 15° 7,8 %, 30° 8,5 %, 45° 10,0 %); Objektiv **über** den Props (Whoop) –
   mehr Uptilt schiebt die Scheiben unten aus dem Bild (Whoop: 0° 9,4 %, 15° 8,9 %, 30° 3,8 %, 45° 0 %). Das deckt die
   Aussagen der Quellen „bei wenig Uptilt mehr Props“ für Kameras auf Propebenen-Höhe und darüber.
5. **Sichtfeld:** bei 90° sind die Props beim Race-5″ fast weg (1,0 %), bei 120° 11,2 %. Von 130° auf 150° wächst der
   Anteil beim Whoop deutlich (10,7 → 12,9 %), bei Cinewhoop und True-X-5″ kaum (+0,2 Prozentpunkte), beim Race-5″ und
   beim 7″ sinkt er leicht (10,7 → 9,6 bzw. 9,5 → 8,9 %), weil das breitere Bild mehr Himmel und Boden dazunimmt; die
   Props selbst stehen dann weiter innen im Bild.

---

## 3. Zielbereiche je Rahmenart (Standard-Uptilt; bis 01.10. Brillen-Sichtfeld analog 120°/digital 130°, seit 02.10. Sichtfeld der Kamera im Videomodul, Abschnitt 9)

| Rahmenart | Ziel | Begründung |
|---|---|---|
| Deadcat (5″, 7″) | 0 % (7″ mit 130°: nur Spitzen am Rand, ≤ 3 %) | Quellen 1, 2, 4–7; `lr_7_deadcat` bleibt wie vom Besitzer geflogen |
| H-Frame mit Kamera vorn am Rumpf | 0 % | Quelle 8; Kamera vor der Motorlinie |
| Squashed-X 5″ | 2–4 % | Quellen 1, 6: teilweise, weniger als True-X |
| True-X 5″ | 6–10 % | Quellen 2, 4: Props im Bild normal |
| Stretched-X / Race 5″ | 10–15 % | Quellen 1, 6: Props im Bild; mehr als True-X (Besitzer) |
| Long Range True-X 4–8″ | 5–10 % | wie True-X; größere Props weiter außen |
| X-Class True-X 10–13″ | 2–8 % | Props weit außen, Kamera im hohen Käfig |
| Whoop 65–85 mm | 5–9 % | Ducts/Props unten in den Ecken (Quelle 10) |
| Cinewhoop 2,5–3,5″ | 6–9 % | Props in Ducts knapp über dem Objektiv |
| Toothpick 2–3″ True-X | 3–6 % | Quelle 1 „often clean“; Props fast auf Objektivhöhe |
| Toothpick 3,5″ Stretched | 6–10 % | mehr als True-X-Toothpick |

---

## 4. Kamerahalter je Rahmen (Objektiv `camera.pos` [x, y, z] in mm, y nach oben, −z nach vorn)

„Propebene über dem Objektiv“: Abstand der Propscheiben (Nabe auf dem Motor laut `motor_seat` + 0,55 × Nabenhöhe) über
dem Objektiv im Preset-Aufbau; negativ = Objektiv über den Props.

### 4.1 Eingebaute Rahmen (Propwash)

| Rahmen | Objektiv vorher (Altformel) | Objektiv jetzt | Propebene über dem Objektiv vorher → jetzt | gezeichnete Kamera (`drone_layout.json` `video`) |
|---|---|---|---|---|
| `whoop_65` | (0; 15,7; −16,1) | (0; 10,1; −18,3), seit 9.4: (0; 9,7; −17,8) | −8,5 → −2,9 mm (seit 9.4: −2,5 mm) | (0; 8,0; −8,5), vorher 7,5, seit 9.4: (0; 7,6; −8,0) |
| `cinewhoop_3` | (0; 25,3; −35,1) | (0; 12,2; −36,8), seit 9.4: (0; 11,7; −36,8) | −10,3 → +2,7 mm (seit 9.4: +3,2 mm) | (0; 10,5; −27,0), vorher 12,0, seit 9.4: (0; 10,0; −27,0) |
| `freestyle_5` | (0; 37,0; −55,7) | (0; 17,2; −58,8) | −16,5 → +3,3 mm | (0; 12,5; −50,0) unverändert |
| `race_5` | (0; 34,8; −53,2) | (0; 18,7; −51,9) | −14,4 → +1,8 mm | (0; 12,5; −44,0), vorher 10,5 |
| `long_range_7` | (0; 43,5; −73,0) | (0; 17,4; −65,4) | −21,5 → +4,6 mm | (0; 14,0; −56,0) unverändert |

Regel: Objektiv = gezeichnete Kamera + 10 mm entlang der um den Uptilt gekippten Achse (`LayoutFromDefinitionTest`
prüft das auf 0,06 mm). Beispiel-Addon: Stretch-Race (0; 14,0; −64,0) → (0; 18,5; −64,0), 34,4 → 12,4 % (vorher über der
Warnschwelle); Deadcat (0; 14; −100) und die minimale X-Class ohne Kamerahalter unverändert.

### 4.2 Just More Parts (Rahmen ohne Kamerahalter bekamen einen, Lage 10 mm vor der bisherigen Kameramitte)

| Rahmen | Preset | Form | Objektiv vorher (y; z) | Objektiv jetzt (y; z) | Propebene über dem Objektiv vorher → jetzt [mm] |
|---|---|---|---|---|---|
| `whoop_65_lite` | Referenz `whoop65Lite` | True-X, Ducts | Altformel | (11,7; −17,5) | → −1,8 |
| `whoop_75_duct` | `whoop75_1s` | True-X, Ducts | (8,0; −26,0) | unverändert | 2,2 |
| `whoop_85_duct` | `whoop85_2s` | True-X, Ducts | (12,2; −21,0) | (13,4; −17,5) | −0,3 → −1,5 |
| `tp_2_x` | `toothpick2_2s` | True-X | (24,2; −22,3) | (13,2; −28,9) | −10,2 → 0,8 |
| `tp_25_x` | Referenz `toothpick25` | True-X | Altformel | (14,1; −33,1) | → 1,0 |
| `tp_3_x` | `toothpick3_4s` | True-X | (30,5; −30,9) | (16,9; −36,3) | −12,8 → 0,9 |
| `tp_35_stretched` | `toothpick35_6s` | Stretched-X | (12,0; −80,0) | unverändert | 4,4 |
| `cw_25_duct` | `cinewhoop25_4s` | Ducts | (31,1; −27,7) | (16,4; −33,5) | −13,3 → 1,4 |
| `cw_35_duct` | `cinewhoop35_6s` | Ducts | (31,9; −39,1) | (16,5; −43,2) | −13,2 → 2,2 |
| `fs_5_bash` | `bash5_6s` | True-X | (37,1; −55,9) | (20,6; −57,5) | −13,4 → 3,1 |
| `fs_5_squashed` | `squashed5_6s` | Squashed-X | (36,9; −49,0) | (16,9; −60,2) | −16,2 → 3,9 |
| `deadcat_5` | `deadcat5_6s` | Deadcat | (14,0; −105,0) | unverändert | 7,2 |
| `race_5_stretched` | `race5_4s`, `race5_6s` | Stretched-X | (33,7/36,4; −66,5) | (16,5; −47,5), bis 2026-10-01 (18,0; −59,6) | −13,7/−16,3 → 3,5 (bis 2026-10-01: 2,0) |
| `hframe_5` | `hframe5_6s` | H-Frame | (18,0; −92,0) | unverändert | 2,8 |
| `lr_4_x` | `lr4_6s_lion` | True-X | (45,1; −43,3) | (16,9; −46,8) | −26,4 → 1,9 |
| `lr_6_x` | `lr6_6s` | True-X | (42,0; −63,1) | (21,0; −63,6) | −17,8 → 3,2 |
| `lr_7_deadcat` | `lr7_deadcat_lion`, `lr7_8s` | Deadcat | (16,0; −78,0) | unverändert | 5,8/6,2 |
| `lr_8_x` | `lr8_6s_lion` | True-X | (53,2; −89,1) | (20,2; −85,6) | −27,9 → 5,1 |
| `xclass_10` | `xclass10_6s` | True-X | (49,5; −113,8) | (27,0; −106,6) | −15,1 → 7,4 |
| `xclass_12` | `xclass12_8s` | True-X | (49,6; −173,2) | (26,0; −157,0) | −12,4 → 11,2 |
| `xclass_13` | `xclass13_8s` | True-X | (45,9; −202,9) | (26,0; −182,2) | −8,8 → 11,2 |
| `xclass_13_h` | `xclass13h_8s` | H-Frame | (30,0; −300,0) | unverändert | 4,1 |

x ist überall 0. Die JMP-Rahmenmodelle wurden mit `style_generate.py --category frame --write` neu erzeugt (Kamerakäfig
und Toothpick-Nasenplatte folgen dem Kamerahalter; Whoops, Deadcats, H-Frames und `tp_35_stretched` byte-gleich).

---

## 5. Ergebnis: Props im Bild vorher → nachher

Werkbank-Wert (`BuildAnalysis.propsInViewPercent`, 16:9, 1080 Zeilen) und im Spiel gezählte Pixel (Selbsttest, Fenster
1708 × 960, Magenta-Maske vor dem Videoeffekt).

| Rahmen (Preset) | Art | vorher | nachher | im Spiel (Bild / Analyse) |
|---|---|---|---|---|
| `whoop_65` (WHOOP) | Whoop | 8,78 | 9,02 | 8,99 / 9,02 |
| `cinewhoop_3` (CINEWHOOP) | Cinewhoop | 20,87 | 7,28 | 7,27 / 7,29 |
| `freestyle_5` (FREESTYLE) | True-X 5″ | 11,73 | 8,37 | 8,35 / 8,38 |
| `race_5` (RACER) | Stretched 5″ | **0,00** | 11,20 | 11,15 / 11,21 |
| `long_range_7` (LONG_RANGE) | True-X 7″ | 19,50 | 9,47 | 9,44 / 9,48 |
| Beispiel `deadcat_5` | Deadcat | 0,00 | 0,00 | 0,00 / 0,00 |
| Beispiel `race_stretch_5` | Stretched | 34,4 | 12,42 | 12,35 / 12,43 |
| Beispiel `xclass_13` (ohne Kamerahalter) | X-Class | 3,63 | 3,63 | 3,62 / 3,63 |
| `whoop_65_lite` (Referenz) | Whoop | 11,63 | 5,88 | – |
| `whoop_75_duct` (`whoop75_1s`) | Whoop | 7,65 | 7,65 | 7,62 / 7,66 |
| `whoop_85_duct` (`whoop85_2s`) | Whoop | 1,35 | 6,20 | 6,18 / 6,20 |
| `tp_2_x` (`toothpick2_2s`) | Toothpick | 2,46 | 3,96 | 3,93 / 3,95 |
| `tp_25_x` (Referenz) | Toothpick | 11,87 | 5,80 | – |
| `tp_3_x` (`toothpick3_4s`) | Toothpick | 11,58 | 5,43 | 5,41 / 5,44 |
| `tp_35_stretched` (`toothpick35_6s`) | Toothpick Stretched | 7,61 | 7,61 | 7,53 / 7,61 |
| `cw_25_duct` (`cinewhoop25_4s`) | Cinewhoop | 17,36 | 6,81 | 6,79 / 6,82 |
| `cw_35_duct` (`cinewhoop35_6s`) | Cinewhoop | 19,93 | 6,85 | 6,83 / 6,86 |
| `fs_5_bash` (`bash5_6s`) | True-X 5″ | 11,72 | 7,94 | 7,92 / 7,95 |
| `fs_5_squashed` (`squashed5_6s`) | Squashed 5″ | 6,90 | 3,03 | 3,02 / 3,03 |
| `deadcat_5` (`deadcat5_6s`) | Deadcat | 0,00 | 0,00 | 0,00 / 0,00 |
| `race_5_stretched` (`race5_4s`) | Stretched 5″ | **1,93** | 11,31 (bis 2026-10-01: 9,77) | 11,28 / 11,32 |
| `race_5_stretched` (`race5_6s`, analog) | Stretched 5″ | **0,00** | 12,19 (bis 2026-10-01: 10,23) | 12,16 / 12,20 |
| `hframe_5` (`hframe5_6s`) | H-Frame | 0,00 | 0,00 | 0,00 / 0,00 |
| `lr_4_x` (`lr4_6s_lion`) | True-X 4″ | 8,93 | 6,16 | 6,13 / 6,15 |
| `lr_6_x` (`lr6_6s`) | True-X 6″ | 17,94 | 8,08 | 8,04 / 8,08 |
| `lr_7_deadcat` (`lr7_deadcat_lion`, `lr7_8s`) | Deadcat 7″ | 1,84 / 2,02 | 1,84 / 2,02 | 1,83 / 1,84, 2,01 / 2,02 |
| `lr_8_x` (`lr8_6s_lion`) | True-X 8″ | 17,11 | 6,61 | 6,59 / 6,61 |
| `xclass_10` (`xclass10_6s`) | X-Class | 14,06 | 7,07 | 7,05 / 7,07 |
| `xclass_12` (`xclass12_8s`) | X-Class | 3,48 | 3,32 | 3,32 / 3,33 |
| `xclass_13` (`xclass13_8s`) | X-Class | 1,65 | 2,32 | 2,32 / 2,33 |
| `xclass_13_h` (`xclass13h_8s`) | X-Class H | 0,00 | 0,00 | 0,00 / 0,00 |

### 5.1 Nachkorrektur Race-Stretch (2026-10-02, vor dem Upload von Propwash 0.4.0 / JMP 1.0.0)

Der JMP-Race-Stretch mit 4S (`race5_4s`) lag mit 9,77 % unter dem Ziel für Stretched-X (10–15 %, Abschnitt 3) und unter
dem eingebauten Racer (11,20 %). Korrigiert ist nur der Kamerahalter von `race_5_stretched`: Objektiv 1,5 mm tiefer und
12,1 mm weiter hinten, (0; 18,0; −59,6) → (0; 16,5; −47,5), Uptilt 40° unverändert. Die Propebene liegt damit 3,5 statt
2,0 mm über dem Objektiv, die Kamera sitzt im Käfig direkt vor den vorderen Abstandshaltern zwischen den weit
vorgezogenen Vorderarmen (typisch für gestreckte Race-Rahmen). Das Rahmenmodell ist mit
`style_generate.py --category frame --only race_5_stretched --write` neu erzeugt; nur der Kamerakäfig wandert mit (aus den
alten Daten erzeugt der Generator die alten Dateien byte-gleich).

Warum nicht nur tiefer: Alle Aufbauten auf demselben Rahmen wandern mit. Mit 2207.5-Motoren (höhere Glocke, Propebene
rund 1,2 mm höher) lag der Rahmen schon bei 15,15 %; 0,25 mm tiefer allein hätte `race5_4s` auf 10,9 % und diesen
Aufbau auf 16,2 % gebracht (über dem Ziel und über der Grenze 15 % + 5 % der Motor-Prüfstände). Eine Kamera weiter hinten
verkleinert den Abstand zwischen flachen und hohen Motoren: alle Race-Stretch-Aufbauten liegen jetzt zwischen 11 und 15 %.

| Aufbau auf `race_5_stretched` | Teile | vorher | nachher | im Spiel (Bild / Analyse) |
|---|---|---|---|---|
| `race5_4s` | 2306,5/2450 KV, 5,1″ hartes PC, Swift Link (130°) | 9,77 | **11,31** | 11,28 / 11,32 |
| `race5_6s` | 2306,5/2020 KV, 5,1″ Glasfaser-Nylon, Racecore 800 (120°) | 10,23 | **12,19** | 12,16 / 12,20 |
| Motor-Referenz `m2207_2550` | 2207, 5,1″ weiches PC, Clarity HD Pro | 12,99 | 13,40 | – |
| Motor-Referenz `m22075_2700` | 2207,5, 5,1″ Bi-Blend, Clarity HD Pro | 15,15 | 14,80 | – |
| FREESTYLE-Teile (Abschnitt 6) | 2207/1750, 5,1″ PC, Digital HD | 12,99 | 13,40 | – |

Alle anderen Rahmen und Presets sind unverändert (Selbsttest: jede Zeile der Tabelle oben wie vorher, höchstens
0,08 Prozentpunkte zwischen Bild und Analyse). Flugphysik: `tools/calibration.sh` mit JMP-Daten, 1245 Varianten –
`acro`, `angle` und `drop` bitgleich, `calibration.txt` byte-gleich; die Spezifikation ändert sich nur bei den 94
Varianten auf `race_5_stretched` und dort nur in `lensUp`, `lensForward` und dem Kamerapunkt der Hülle (12,1 mm weiter
hinten, 1,5 mm tiefer, ohne Kontakt in den Prüfflügen).

---

## 6. Gleiche Teile, gleiche Kamera: nur die Rahmenform

FREESTYLE-Teile (2207, 5,1″ Dreiblatt, Digitalbrille 130°) auf den 5″-Rahmen (JMP `FrameBenchTest`):

| Rahmen | vorher | nachher |
|---|---|---|
| `race_5_stretched` (Stretched-X) | 0,71 | **13,40** (bis 2026-10-01: 12,99) |
| `propwash:race_5` (Stretched, eingebaut) | 2,82 | 10,72 |
| `propwash:freestyle_5` (True-X) | 11,73 | 8,37 |
| `fs_5_bash` (True-X, 6-mm-Arme) | 11,68 | 7,94 |
| `fs_5_squashed` (Squashed-X) | 6,88 | 3,03 |
| `deadcat_5`, `hframe_5` | 0,00 | 0,00 |

Und mit **identischem** Kamerahalter (0; 17; −60), 30° Uptilt, 130° (JUnit
`CameraOcclusionTest#frameShapeDecidesHowMuchPropShowsWithTheSameCameraMount`): Stretched (±68/92 mm) 20,5 %, True-X
(±80/80) 9,5 %, Squashed (±95/65) 3,0 %, H (±105/70) 1,9 %, Deadcat (±100/55) 1,4 %. Die Reihenfolge entsteht also aus
der Geometrie, nicht aus den gewählten Kamerahöhen.

---

## 7. Prüfung

- Bewegungs-Fingerabdrücke (`tools/calibration.sh` mit JMP-Daten, `MotionFingerprintTest`, 1245 Varianten aller Presets
  mit allen Einzeltausch-Teilen): `acro`, `angle` und `drop` vor und nach der Änderung bitgleich; nur die
  Spezifikationszeile ändert sich (Objektiv, bei JMP-Rahmen mit eigenen Armen auch der Kamerapunkt der Hülle, der in
  keinem der Prüfflüge – Fall auf den Boden, Flug gegen die Wand – Kontakt hat). Bei eingebauten Rahmen ändern sich in `AirframeSpec` nur `lensUp` und `lensForward`
  (Feldvergleich aller 9000 eingebauten Kombinationen); `airframe_golden.txt` und `builtin_definitions.txt` sind deshalb
  neu geschrieben.
- JUnit: `CameraOcclusionTest` (Uptilt je nach Objektivlage, Rahmenform bei gleichem Kamerahalter, Bild gegen Analyse
  der eingebauten Presets), `LayoutFromDefinitionTest` (gezeichnete Kamera auf der Objektivachse),
  `FpvPropGeometryTest` (Renderer-Objektiv = Kamerahalter = Analyse). JMP: `FrameBenchTest` (Squashed 3 statt 8 %,
  Stretched mehr als True-X, Toothpicks, Deadcat LR 7″ 2 statt 8 %), `MotorBenchTest` (5″-Korridor 15 % + 5 % Toleranz
  auch mit dem höheren 2207.5 auf dem Race-Stretch).
- Selbsttest Propwash „drone-render: Props im FPV-Bild – Analyse gegen gerendertes Bild“ mit dem JMP-Jar im Mods-Ordner:
  alle 30 Presets innerhalb 0,08 Prozentpunkten; im Bildschirmfoto-Modus `docs/screenshots/fpv_coverage/<namensraum>_<rahmen>.png`
  (Magenta = gezählte Propscheiben). Flugbilder mit normal gezeichneten drehenden Props: `fpv_props_in_view.png` (True-X
  5″), `fpv_props_race.png`, `fpv_props_whoop.png`, `fpv_props_cinewhoop.png`, `fpv_props_long_range.png`,
  `fpv_props_stretched.png`, `fpv_props_deadcat.png` (beide Beispiel-Addon).
- Selbsttest JMP „frame: jeder Rahmen mit Kamerahalter, Props im FPV-Bild nach Rahmenform (Kamera-Aufbau der API)“:
  alle 22 Rahmen haben `camera.pos`, Objektiv der API = Kamerahalter, Presets außer Deadcat/H zwischen 1 und 15 %,
  gleiche Teile: Stretched > True-X > Squashed > Deadcat/H.

---

## 8. Hinweise

- Rahmen ohne `camera.pos` (z. B. die minimale Beispiel-X-Class) behalten die Altformel. Sie liegt fast immer über der
  Propebene; Addon-Rahmen sollten den Kamerahalter setzen (Addon-Handbuch, Abschnitt Rahmen).
- Bis 0.4.0 zeichnete das FPV-Bild weder Ducts noch Rahmen, nur die Propscheiben; bei Whoops und Cinewhoops standen sie
  stellvertretend für „Ducts im Bild“. Seit 0.5.0 zeichnet es die Ducts aus dem Rahmenmodell (`docs/ASSETS.md`, „Ducts im
  FPV-Bild“, mit dem Anteil je Drohne). „Props im Bild“ zählt weiter nur die Scheiben; die Kamerahalter dieser Tabelle
  sind auf Props abgestimmt, nicht auf Ducts.
- Die weiche Unschärfe-Scheibe ist innen 35 %, am Rand 11 % deckend (reine Darstellung, nicht geändert); „Props im
  Bild“ zählt die ganze Scheibe.

---

## 9. Nachtrag 02.10.2026: Sichtfeld vom Videomodul, Kamerawinkel pro Drohne

Anforderungen vom 02.10.: Das Sichtfeld kommt wie in echt vom Videomodul, und der Kamerawinkel
gehört an die Drohne statt in die globalen Simulations-Einstellungen. Seitdem rechnen FPV-Bild, Werkbank-Analyse, API
(`cameraView`) und Selbsttest mit dem **Sichtfeld der Kamera im Videomodul** (`camera_fov_deg`, Recherche in
`docs/design/realism/power-video.md` §4.4) statt mit dem Brillen-Sichtfeld analog 120°/digital 130°. Die Analyse
benutzt weiter den **Standard-Uptilt des Rahmens**; den eigenen Winkel einer Drohne zeigt das Wartungsmenü mit seinem
eigenen „Props im Bild“. Kamerahalter (`camera.pos`) und Rahmendaten sind **nicht** geändert.

### 9.1 Kamerahalter je Rahmenklasse (Bereich des Kamerawinkels)

Echte Rahmen halten die FPV-Kamera in Seitenplatten, Canopies oder TPU-Haltern mit begrenztem Verstellbereich; viele
Halter haben Rasten alle 5° [K3]. Propwash leitet den Bereich aus der Rahmenklasse ab (`drone.CameraMount.roleRange`) und
erweitert ihn immer um den Standard des Rahmens (`camera.tilt_deg`):

| Rahmenklasse | Bereich | typisch geflogen | Begründung |
|---|---|---|---|
| Whoop 65–85 mm | 10–35° | 15–20° | Canopy des Meteor75 Pro O4 „adjustable camera angle from 10° to 35°“ [K1], Testpilot fliegt 15–20° [K5] |
| Toothpick 2–3,5″ | 10–40° | 20–30° | Canopy/TPU-Halter wie Whoops, etwas steiler für schnelleren Flug (abgeleitet aus K1–K3) |
| Cinewhoop 2,5–3,5″ | 0–35° | 10–25° | Cinematic/Cruising 10–25° [K3], Halter im Duct-Rahmen mit wenig Spiel nach oben |
| Freestyle 5″ (True-X, Squashed, Deadcat, H) | 0–50° | 20–45° | „20–30° quite popular“, ideal einstellbar 0–45° [K2]; Freestyle 30–45° [K3]; ERA 5″: FPV-Kamera 0 bis rund 40° [K4] |
| Race 5″ | 15–70° | 45–60° | Racing 45–60°, „often run 50 degrees or more“ [K3], „hardcore racers … 60 degree“ [K2], einzelne 65–70° |
| Long Range 4–8″ | 0–40° | 15–25° | Cruising 15–25° [K3] |
| Cinelifter | 0–30° | 10–20° | ruhiger Kameraflug (abgeleitet aus K3) |
| X-Class 10–13″ | 0–50° | 25–35° | großer Race-/Freestyle-Rahmen mit Seitenplatten wie 5″ |

Mit den eingebauten Rahmen: Whoop 65 10–35° (Standard 12°), Cinewhoop 3″ 0–35° (10°), Freestyle 5″ 0–50° (28°), Race 5″
15–70° (38°), Long Range 7″ 0–40° (20°). Props im Bild am Rand des Bereichs (Analyse mit dem Sichtfeld der Kamera, Kamerahalter
nach Abschnitt 9.4): Whoop 8,3 % bei 10° → 3,6 % bei 35° (Objektiv über den Ducts), Cinewhoop 6,9 → 7,5 %, Freestyle 7,6 % bei 0° → 10,7 % bei 50°,
Race 8,9 % bei 15° → 0 % bei 70° (bei sehr steilem Winkel fallen die Scheiben unten aus dem Bild), Long Range 9,1 → 11,4 %.

Quellen (Zugriff 02.10.2026):
- [K1] https://betafpv.com/products/meteor75-pro-o4-brushless-whoop-quadcopter („adjustable camera angle from 10° to 35°“)
- [K2] https://oscarliang.com/fpv-drone-frames/ („For freestyle, 20-30 degree is quite popular“, „hardcore racers will tilt
  their camera all the way back at 60 degree“, ideal „between 0 degrees to 45 degrees“)
- [K3] https://www.unmannedtechshop.co.uk/blogs/knowledge-base/fpv-drone-camera-angle-how-tilt-affects-flight (Anfänger
  15–25°, Freestyle 30–45°, Racing 45–60° „often run 50 degrees or more“, Cinematic 10–25°, Rasten alle 5°)
- [K4] https://itsfpv.de/products/era (FPV-Kamera 0 bis rund 40° verstellbar; über die Suchmaschine gelesen)
- [K5] https://oscarliang.com/betafpv-meteor75-pro-o4-whoop/ (Kommentar: „set it at around 15 to 20 degrees“)

### 9.2 Props im Bild nachher (Standard-Uptilt, Sichtfeld der Kamera)

Werkbank-Wert und im Spiel gezählte Pixel (Selbsttest „drone-render: Props im FPV-Bild – Analyse gegen gerendertes Bild“
mit dem JMP-Jar, Fenster 1708 × 960, alle 30 Presets innerhalb 0,08 Prozentpunkten; der Selbsttest prüft jetzt auch, dass
Bild und Analyse dasselbe Sichtfeld und denselben Uptilt benutzen). „vorher“ = Tabelle in Abschnitt 5
(Brillen-Sichtfeld).

| Rahmen (Preset) | Art | Videomodul | Sichtfeld vorher → jetzt | Props vorher | jetzt | im Spiel (Bild / Analyse) | Ziel (Abschnitt 3) |
|---|---|---|---|---|---|---|---|
| `whoop_65` (WHOOP) | Whoop | `analog_nano` | 120 → 125° | 9,02 | **9,87** | 9,85 / 9,88 | 5–9 % (knapp darüber) |
| `cinewhoop_3` (CINEWHOOP) | Cinewhoop | `digital_lite` | 130 → 104° | 7,28 | **5,91** | 5,89 / 5,92 | 6–9 % (knapp darunter) |
| `freestyle_5` (FREESTYLE) | True-X 5″ | `digital_hd` | 130 → 130° | 8,37 | 8,37 | 8,35 / 8,38 | 6–10 % |
| `race_5` (RACER) | Stretched 5″ | `analog_race` | 120 → 125° | 11,20 | 10,97 | 10,91 / 10,96 | 10–15 % |
| `long_range_7` (LONG_RANGE) | True-X 7″ | `digital_hd` | 130 → 130° | 9,47 | 9,47 | 9,44 / 9,48 | 5–10 % |
| Beispiel `deadcat_5` | Deadcat | `analog_race` | 120 → 125° | 0,00 | 0,00 | 0,00 / 0,00 | 0 % |
| Beispiel `race_stretch_5` | Stretched | `analog_race` | 120 → 125° | 12,42 | 12,36 | 12,30 / 12,37 | 10–15 % |
| Beispiel `xclass_13` (ohne Kamerahalter) | X-Class | `digital_hd` | 130 → 130° | 3,63 | 3,63 | 3,62 / 3,63 | 2–8 % |
| `whoop_75_duct` (`whoop75_1s`) | Whoop | `analog_whoop_25mw` | 120 → 126° | 7,65 | 8,64 | 8,60 / 8,64 | 5–9 % |
| `whoop_85_duct` (`whoop85_2s`) | Whoop | `digital_micro` | 130 → 126° | 6,20 | 6,15 | 6,14 / 6,15 | 5–9 % |
| `tp_2_x` (`toothpick2_2s`) | Toothpick | `analog_whoop_25mw` | 120 → 126° | 3,96 | 4,25 | 4,23 / 4,25 | 3–6 % |
| `tp_3_x` (`toothpick3_4s`) | Toothpick | `digital_micro` | 130 → 126° | 5,43 | 5,35 | 5,33 / 5,36 | 3–6 % |
| `tp_35_stretched` (`toothpick35_6s`) | Toothpick Stretched | `digital_micro` | 130 → 126° | 7,61 | 6,13 | 6,06 / 6,14 | 6–10 % |
| `cw_25_duct` (`cinewhoop25_4s`) | Cinewhoop | `digital_micro` | 130 → 126° | 6,81 | 6,67 | 6,63 / 6,66 | 6–9 % |
| `cw_35_duct` (`cinewhoop35_6s`) | Cinewhoop | `digital_hd_pro` | 130 → 130° | 6,85 | 6,85 | 6,83 / 6,86 | 6–9 % |
| `fs_5_bash` (`bash5_6s`) | True-X 5″ | `digital_hd_pro` | 130 → 130° | 7,94 | 7,94 | 7,92 / 7,95 | 6–10 % |
| `fs_5_squashed` (`squashed5_6s`) | Squashed 5″ | `digital_hd_pro` | 130 → 130° | 3,03 | 3,03 | 3,02 / 3,03 | 2–4 % |
| `deadcat_5` (`deadcat5_6s`) | Deadcat | `digital_hd_pro` | 130 → 130° | 0,00 | 0,00 | 0,00 / 0,00 | 0 % |
| `race_5_stretched` (`race5_4s`) | Stretched 5″ | `digital_low_latency` | 130 → 133° | 9,77 | **9,60** | 9,56 / 9,61 | 10–15 % (knapp darunter, vorher auch) |
| `race_5_stretched` (`race5_6s`) | Stretched 5″ | `analog_race_800` | 120 → 125° | 10,23 | 10,01 | 9,97 / 10,03 | 10–15 % |
| `hframe_5` (`hframe5_6s`) | H-Frame | `digital_hd_pro` | 130 → 130° | 0,00 | 0,00 | 0,00 / 0,00 | 0 % |
| `lr_4_x` (`lr4_6s_lion`) | True-X 4″ | `digital_micro` | 130 → 126° | 6,16 | 6,08 | 6,06 / 6,09 | 5–10 % |
| `lr_6_x` (`lr6_6s`) | True-X 6″ | `digital_lr` | 130 → 130° | 8,08 | 8,08 | 8,04 / 8,08 | 5–10 % |
| `lr_7_deadcat` (`lr7_deadcat_lion`, `lr7_8s`) | Deadcat 7″ | `digital_lr` | 130 → 130° | 1,84 / 2,02 | 1,84 / 2,02 | 1,83 / 1,84, 2,01 / 2,02 | ≤ 3 % (Spitzen am Rand) |
| `lr_8_x` (`lr8_6s_lion`) | True-X 8″ | `digital_lr` | 130 → 130° | 6,61 | 6,61 | 6,59 / 6,61 | 5–10 % |
| `xclass_10` (`xclass10_6s`) | X-Class | `digital_hd_pro` | 130 → 130° | 7,07 | 7,07 | 7,05 / 7,07 | 2–8 % |
| `xclass_12` (`xclass12_8s`) | X-Class | `digital_lr` | 130 → 130° | 3,32 | 3,32 | 3,32 / 3,33 | 2–8 % |
| `xclass_13` (`xclass13_8s`) | X-Class | `digital_lr` | 130 → 130° | 2,32 | 2,32 | 2,32 / 2,33 | 2–8 % |
| `xclass_13_h` (`xclass13h_8s`) | X-Class H | `digital_lr` | 130 → 130° | 0,00 | 0,00 | 0,00 / 0,00 | 0 % |

### 9.3 Ergebnis und gemeldete Abweichungen

- Die Reihenfolge der Rahmenformen hält: Deadcat und H-Frame 0 %, Squashed (3,0 %) < True-X (7,9–8,4 %) <
  Stretched/Race (9,6–12,4 %); mit gleichen Teilen (FREESTYLE-Teile, HD-Kamera 130° wie vorher) unverändert Stretched
  13,0 % > True-X 8,4 % > Squashed 3,0 % > Deadcat/H 0 % (`FrameBenchTest`, JMP-Selbsttest).
- **Außerhalb der Zielbereiche (gemeldet, Kamerahalter in diesem Schritt nicht geändert; erledigt in Abschnitt 9.4):**
  - `propwash:whoop_65` 9,9 % (Ziel 5–9 %): die Nano-Kamera hat jetzt 125° statt der Brille 120°.
  - `propwash:cinewhoop_3` 5,9 % (Ziel 6–9 %): das Lite-Digitalsystem (DJI-O4-Klasse) hat nur 104° statt 130°.
  - `justmoreparts:race5_4s` (Race-Stretch mit `digital_low_latency` 133°) 9,6 % (Ziel 10–15 %, vorher schon 9,8 %).
  Alle drei liegen weniger als einen Prozentpunkt neben dem Bereich; die Zielbereiche aus Abschnitt 3 sind ohne
  Prozentangaben aus den Quellen abgeleitet. Vorschlag für den Besitzer: Whoop-Bereich auf 5–10 %, Cinewhoop auf 5–9 %
  erweitern oder die Kamerahalter von `whoop_65`/`cinewhoop_3` anpassen (nicht Teil dieses Auftrags).
- Ein H-Frame mit 136°-Kamera (Walksnail-HD-Pro-Klasse) zeigt 0,1 % Propspitzen am Rand; deshalb ist JMPs „Clarity HD
  Pro“ der DJI-O4-Pro-Klasse (130°) zugeordnet, die Referenz der HD-Einheiten in `power-video.md` §4.2/§6.3.
- Flugphysik bitgleich: `tools/calibration.sh` mit JMP-Daten (1245 Varianten, acro/angle/drop und Spezifikationszeile
  gleich, `calibration.txt` identisch), `AirframeGoldenTest` unverändert.

### 9.4 Endstand Propwash 0.4.0 / JMP 1.0.0: Kamerahalter nachgezogen, alle 30 Presets im Zielbereich

Nach dem Zusammenführen aller Zweige (Sichtfeld vom Videomodul, Race-Stretch-Kamerahalter aus Abschnitt 5.1) lagen noch
`propwash:whoop_65` (9,87 %) und `propwash:cinewhoop_3` (5,91 %) außerhalb ihres Zielbereichs; `justmoreparts:race5_4s`
liegt mit dem nachkorrigierten Race-Stretch-Halter (Abschnitt 5.1) und der 133°-Kamera bei 11,08 % und brauchte nichts
mehr. Geändert sind nur die Kamerahalter der beiden eingebauten Rahmen (Objektiv und gezeichnete Kamera um denselben
Versatz, die Regel „Objektiv = gezeichnete Kamera + 10 mm auf der Achse“ gilt weiter); Uptilt, Rahmenmodelle und alle
anderen Daten sind unverändert:

| Rahmen | Objektiv vorher → jetzt | gezeichnete Kamera vorher → jetzt | Props vorher → jetzt | Ziel |
|---|---|---|---|---|
| `whoop_65` | (0; 10,1; −18,3) → (0; 9,7; −17,8) | (0; 8,0; −8,5) → (0; 7,6; −8,0) | 9,87 → **8,81 %** | 5–9 % |
| `cinewhoop_3` | (0; 12,2; −36,8) → (0; 11,7; −36,8) | (0; 10,5; −27,0) → (0; 10,0; −27,0) | 5,91 → **7,00 %** | 6–9 % |

- **Whoop:** 0,4 mm tiefer und 0,5 mm weiter hinten (Objektiv 2,5 statt 2,9 mm über den Ducts). Nur tiefer (9,6 mm)
  hätte 8,25 % ergeben – dann sähe der 65-mm-Whoop weniger Ducts als der JMP-Whoop 75 (8,64 %), dessen Shift-Hilfe
  „Kamera vorn: weniger Ducts im Bild“ verspricht (JMP `FrameBenchTest`). 8,81 % liegt im Ziel und darüber.
- **Cinewhoop:** 0,5 mm tiefer (Propebene 3,2 statt 2,7 mm über dem Objektiv). Die DJI-O4-Lite-Klasse hat mit 104° das
  engste Sichtfeld; mit dem tieferen Objektiv sieht sie die Duct-Props wie vorher die 130°-Brille (7,28 %).
- **Physik:** Spezifikation aller 9000 eingebauten Kombinationen ohne `lensUp`/`lensForward` bitgleich (Feldvergleich);
  `airframe_golden.txt` ändert sich nur in den 66 Einträgen von `whoop_65`/`cinewhoop_3`, `builtin_definitions.txt` nur im
  Kamerahalter. `tools/calibration.sh` mit JMP-Daten: 1245 Varianten, `acro`, `angle` und `drop` bitgleich,
  `calibration.txt` byte-gleich, die Spezifikationszeile nur bei den 73 Varianten auf diesen beiden Rahmen anders.

Endstand aller 30 Presets (Selbsttest „drone-render: Props im FPV-Bild – Analyse gegen gerendertes Bild“ mit dem
JMP-Jar, Fenster 1708 × 960; Werkbank = `BuildAnalysis.propsInViewPercent`, 16:9, 1080 Zeilen):

| Rahmen (Preset) | Art | Kamera | Werkbank | im Spiel (Bild / Analyse) | Ziel (Abschnitt 3) |
|---|---|---|---|---|---|
| `whoop_65` (WHOOP) | Whoop | `analog_nano` 125° | **8,81** | 8,79 / 8,82 | 5–9 % |
| `cinewhoop_3` (CINEWHOOP) | Cinewhoop | `digital_lite` 104° | **7,00** | 6,97 / 7,00 | 6–9 % |
| `freestyle_5` (FREESTYLE) | True-X 5″ | `digital_hd` 130° | 8,37 | 8,35 / 8,38 | 6–10 % |
| `race_5` (RACER) | Stretched 5″ | `analog_race` 125° | 10,97 | 10,91 / 10,96 | 10–15 % |
| `long_range_7` (LONG_RANGE) | True-X 7″ | `digital_hd` 130° | 9,47 | 9,44 / 9,48 | 5–10 % |
| Beispiel `deadcat_5` | Deadcat | `analog_race` 125° | 0,00 | 0,00 / 0,00 | 0 % |
| Beispiel `race_stretch_5` | Stretched | `analog_race` 125° | 12,36 | 12,30 / 12,37 | 10–15 % |
| Beispiel `xclass_13` (ohne Kamerahalter) | X-Class | `digital_hd` 130° | 3,63 | 3,62 / 3,63 | 2–8 % |
| `whoop_75_duct` (`whoop75_1s`) | Whoop | `analog_whoop_25mw` 126° | 8,64 | 8,60 / 8,64 | 5–9 % |
| `whoop_85_duct` (`whoop85_2s`) | Whoop | `digital_micro` 126° | 6,15 | 6,14 / 6,15 | 5–9 % |
| `tp_2_x` (`toothpick2_2s`) | Toothpick | `analog_whoop_25mw` 126° | 4,25 | 4,23 / 4,25 | 3–6 % |
| `tp_3_x` (`toothpick3_4s`) | Toothpick | `digital_micro` 126° | 5,35 | 5,33 / 5,36 | 3–6 % |
| `tp_35_stretched` (`toothpick35_6s`) | Toothpick Stretched | `digital_micro` 126° | 6,13 | 6,06 / 6,14 | 6–10 % |
| `cw_25_duct` (`cinewhoop25_4s`) | Cinewhoop | `digital_micro` 126° | 6,67 | 6,63 / 6,66 | 6–9 % |
| `cw_35_duct` (`cinewhoop35_6s`) | Cinewhoop | `digital_hd_pro` 130° | 6,85 | 6,83 / 6,86 | 6–9 % |
| `fs_5_bash` (`bash5_6s`) | True-X 5″ | `digital_hd_pro` 130° | 7,94 | 7,92 / 7,95 | 6–10 % |
| `fs_5_squashed` (`squashed5_6s`) | Squashed 5″ | `digital_hd_pro` 130° | 3,03 | 3,02 / 3,03 | 2–4 % |
| `deadcat_5` (`deadcat5_6s`) | Deadcat | `digital_hd_pro` 130° | 0,00 | 0,00 / 0,00 | 0 % |
| `race_5_stretched` (`race5_4s`) | Stretched 5″ | `digital_low_latency` 133° | **11,08** | 11,05 / 11,09 | 10–15 % |
| `race_5_stretched` (`race5_6s`) | Stretched 5″ | `analog_race_800` 125° | **11,73** | 11,71 / 11,75 | 10–15 % |
| `hframe_5` (`hframe5_6s`) | H-Frame | `digital_hd_pro` 130° | 0,00 | 0,00 / 0,00 | 0 % |
| `lr_4_x` (`lr4_6s_lion`) | True-X 4″ | `digital_micro` 126° | 6,08 | 6,06 / 6,09 | 5–10 % |
| `lr_6_x` (`lr6_6s`) | True-X 6″ | `digital_lr` 130° | 8,08 | 8,04 / 8,08 | 5–10 % |
| `lr_7_deadcat` (`lr7_deadcat_lion`, `lr7_8s`) | Deadcat 7″ | `digital_lr` 130° | 1,84 / 2,02 | 1,83 / 1,84, 2,01 / 2,02 | ≤ 3 % (Spitzen am Rand) |
| `lr_8_x` (`lr8_6s_lion`) | True-X 8″ | `digital_lr` 130° | 6,61 | 6,59 / 6,61 | 5–10 % |
| `xclass_10` (`xclass10_6s`) | X-Class | `digital_hd_pro` 130° | 7,07 | 7,05 / 7,07 | 2–8 % |
| `xclass_12` (`xclass12_8s`) | X-Class | `digital_lr` 130° | 3,32 | 3,32 / 3,33 | 2–8 % |
| `xclass_13` (`xclass13_8s`) | X-Class | `digital_lr` 130° | 2,32 | 2,32 / 2,33 | 2–8 % |
| `xclass_13_h` (`xclass13h_8s`) | X-Class H | `digital_lr` 130° | 0,00 | 0,00 / 0,00 | 0 % |

- Alle 30 Presets liegen im Zielbereich aus Abschnitt 3; Bild und Analyse höchstens 0,08 Prozentpunkte auseinander.
- Reihenfolge der Rahmenformen: Deadcat und H-Frame 0 % < Squashed 3,0 % < True-X 7,9–8,4 % < Stretched/Race
  11,0–12,4 %; Whoop 65 (8,8 %) zeigt mehr Ducts als Whoop 75 (8,6 %) und Whoop 85 (6,2 %).
- Maskenbilder: Propwash `docs/screenshots/fpv_coverage/propwash_*.png` und `propwash_example_*.png`, JMP
  `docs/screenshots/fpv_coverage/justmoreparts_*.png`.

### 9.5 Nachtrag 08.10.2026: Kamera über den Ducts (Propwash 0.5.0 / JMP 1.1.0)

Befund der Release-Prüfung: Mit Kamera zwischen den vorderen Ducts und unter deren Oberkante standen die Ducts im
FPV-Bild als Wände über die volle Bildhöhe (eingebauter Cinewhoop 3″ 58 % bei 0°, Rahmen von Just More Parts bis 75 %).
Vorgabe des Besitzers: Ducts wie in einem echten Whoop-Bild, nur unten in den Ecken. Deshalb sitzt das Objektiv aller
Duct-Rahmen jetzt über der Duct-Oberkante (Tabelle und Regel: [`ASSETS.md`](../../ASSETS.md) „Ducts im FPV-Bild“).
Dadurch liegen die Props unter dem Objektiv und zeigen sich weniger:

| Rahmen (Preset) | Objektiv vorher → jetzt | Props im Bild vorher → jetzt (Bild / Analyse) | Ducts im Bild 0/20/40° |
|---|---|---|---|
| `whoop_65` (WHOOP) | (0; 9,7; −17,8) → (0; 16; −20) | 8,81 → **6,20 / 6,22 %** | 21 / 7 / 0 % |
| `cinewhoop_3` (CINEWHOOP) | (0; 11,7; −36,8) → (0; 30; −48) | 7,00 → **1,92 / 1,93 %** | 15 / 0 / 0 % |

Der Whoop bleibt im Zielbereich (5–9 %). Der Cinewhoop liegt unter seinem Ziel von 6–9 % aus Abschnitt 3 („Props in Ducts
knapp über dem Objektiv“); das Ziel setzte die Kamera zwischen die Ducts, also genau die Lage mit den Wänden im Bild.
Entscheidung: Für Rahmen mit Ducts gilt die Kamera über den Ducts vor dem Ziel „Props im Bild“; Ziel für Cinewhoops mit
Kamera über den Ducts 1–9 % (Just More Parts 1.1.0: Cinewhoop 2,5″ 2,4 %, 3,5″ 13,3 %). Verworfen: Objektiv knapp über
der Oberkante zwischen den Ducts (mehr Props im Bild, aber 27–33 % Ducts bei 0° und die Kamera passt bei z −48 nicht
zwischen die vorderen Ducts).
