# Fisheye-Objektiv im FPV-Bild (0.5.2)

Echte FPV-Kameras sind Fischaugen. Propwash zeichnete bis 0.5.1 geradlinig (Minecraft-Projektion): Bei gleicher
Breite des Sichtfelds erscheint die Bildmitte dann um `tan(H/2) / (H/2)` kleiner – 1,41-fach bei 104° (digital Lite),
1,76-fach bei 125° (analog), 1,89-fach bei 130° (digital HD). Dinge vor der Drohne wirken weiter weg und langsamer;
`docs/design/fpv-feel.md` §2 und §6 nannten das als größten offenen Hebel für das Geschwindigkeitsgefühl. Seit 0.5.2
zeichnet Propwash das FPV-Bild wie eine echte Fischaugen-Optik, die Stärke stellt ein Regler ein (§2.1). Persönliche
Einstellung, kein Netzwerk-Feld, Flugmodell, Motion-Goldens und Kalibrierung unberührt.

## 1. Verhalten

- Tab „Video“, Regler **Fisheye** von 0 bis 100 % in Schritten von 5 % (`VideoConfig#lensStrength`, Datei
  `config/propwash/video.json`). **0 % = „aus (flach)“**: das geradlinige Bild von 0.5.1, ohne Entzerrungs-Pass.
  **Standard 80 %** = das Fisheye der ersten 0.5.2-Fassung (f-θ, §2), auch für bestehende Spieler. Die erste Fassung
  hatte einen Umschalter „Objektiv: Fisheye / Flach“ (`"lens"` in der Datei); beim Laden wird daraus 0 % (Flach) bzw.
  80 % (Fisheye), `VideoConfig#migrate`.
- Gilt für das eigene FPV-Bild, beim Zuschauen und mit der Kreativ-Brille. Die Breite des Sichtfelds bleibt die der
  Kamera im Videomodul (`camera_fov_deg`) bzw. das eigene Sichtfeld der Kreativ-Brille.
- Die Zeile unter den Einstellungen zeigt mit Fisheye Breite, Höhe und Diagonale des Bildes bei der eingestellten
  Stärke („Bildformat 16:9 mit Fisheye: 130° breit × 73° hoch, 149° diagonal“ bei 80 %), bei 0 % wie bisher
  Breite × Höhe.
- Videoeffekte (analog und digital), Bildformat 4:3, OSD, Props und Ducts im Bild, Scheinwerfer und Shaderpacks
  folgen der Stärke. Die Action-Cam nimmt bei jeder Stärke unverändert flach auf (§5).
- Wenn die Entzerrung nicht laufen kann (Render-Hook fehlt, Pipeline lässt sich nicht bauen, Fehler im Pass), zeichnet
  Propwash flach weiter; der Knopf „Shader erneut versuchen“ im Video-Tab setzt auch diesen Fehler zurück.

## 2. Optik

**Modell: äquidistantes Fischauge (f-θ).** Der Bildpunkt liegt im Abstand `r = f · θ` von der Bildmitte, `θ` ist der
Winkel des Sehstrahls zur Kameraachse. Die Brennweite `f` (Pixel je Radiant) folgt aus der Bildbreite `w` und dem
horizontalen Sichtfeld `H` des Moduls: `f = (w / 2) / (H / 2)`. Damit spannt das Modul wie bisher genau die Bildbreite.

Warum f-θ: Die Herstellerangaben in `docs/design/realism/power-video.md` §4.4 passen dazu. Bei f-θ verhalten sich
H, V und D wie die Bildmaße: 4:3 → H/D = 0,80, V/D = 0,60; 16:9 → H/D = 0,87. Gemessen: H/D bei 4:3 0,78–0,82, bei
16:9 0,85–0,86; Racer Nano 4 D 160° / V 97° (f-θ: 96°), HDZero Nano V3 D 155° / V 94° (f-θ: 93°). Eine geradlinige
Optik käme bei 160° Diagonale auf ein senkrechtes Sichtfeld von über 130°. Stereografisch oder flächentreu läge
dazwischen; f-θ ist die einfachste Abbildung, die zu allen Angaben passt.

| Videomodul | H | Bild 16:9 senkrecht / diagonal (f-θ) | flach senkrecht | Bildmitte größer als flach |
|---|---:|---|---:|---:|
| digital Lite | 104° | 58,5° / 119° | 71,5° | 1,41 |
| analog Nano, Race | 125° | 70,3° / 143° | 94,4° | 1,76 |
| digital HD | 130° | 73,1° / 149° | 100,7° | 1,89 |
| Walksnail-Klasse (Beispiel-Addon) | 136° | 76,5° / 156° | 108,6° | 2,09 |

Bei 4:3 ist das Bild 4/3 so breit wie hoch: analog 125° → 93,8° senkrecht, 156° diagonal.

### 2.1 Stärke

Der Regler wählt eine Abbildung aus einer Familie, die geradlinig, stereografisch und f-θ enthält und darüber hinaus
weiter staucht (`FisheyeLens#map`, `#unmap`, `#derivative`):

```
q = 1 − Stärke / 0,8                      (0 % → q = 1, 80 % → q = 0, 100 % → q = −0,25)
r = f · tan(√q · θ) / √q                  für q > 0
r = f · θ                                 für q = 0
r = f · tanh(√−q · θ) / √−q               für q < 0
f = (Bildbreite / 2) / Abbildung(H / 2)   (H spannt bei jeder Stärke genau die Bildbreite)
```

- **0 %** (q = 1) ist geradlinig, also das flache Bild; Propwash schaltet dann den Pass ganz ab.
- **60 %** (q = 0,25) ist stereografisch.
- **80 %** (q = 0) ist f-θ, bitgleich mit der ersten 0.5.2-Fassung.
- **100 %** (q = −0,25) staucht den Rand stärker als flächentreu (equisolid), aber ohne den Knick der orthografischen
  Abbildung.

Für kleine Winkel ist jede Abbildung `θ + (q/3)·θ³`: die Tonnenverzeichnung wächst linear mit dem Regler, ebenso die
Bildmitte (Vergrößerung gegen flach):

| Stärke | Lite 104° 16:9: V / D, Mitte | analog 125° 16:9: V / D, Mitte | HD 130° 16:9: V / D, Mitte, Hochrechnung |
|---:|---|---|---|
| 0 % | 72° / 111°, 1,00 | 94° / 131°, 1,00 | 101° / 136°, 1,00, 1,00 |
| 20 % | 68° / 113°, 1,11 | 88° / 133°, 1,20 | 93° / 138°, 1,24, 1,32 |
| 40 % | 65° / 115°, 1,21 | 81° / 136°, 1,40 | 85° / 141°, 1,47, 1,69 |
| 60 % | 61° / 117°, 1,31 | 75° / 139°, 1,58 | 79° / 145°, 1,68, 2,15 |
| 80 % (Standard) | 59° / 119°, 1,41 | 70° / 143°, 1,76 | 73° / 149°, 1,89, 2,78 |
| 100 % | 56° / 122°, 1,51 | 66° / 149°, 1,93 | 68° / 155°, 2,09, 3,82 |

Stärker heißt also: Mitte größer, senkrecht weniger, in die Ecken mehr – wie bei echten Optiken mit stärkerer
Verzeichnung. **Warum 100 % hier endet:** Bei 100 % füllen alle eingebauten Kameras in 16:9 das Bild noch bis in die
Ecken (HD: Ecke 77,5° neben der Achse, Bildkreis erst ab 80°; 16:9 bis H = 133°), und die Mitte wird höchstens
3,8-fach hochgerechnet (§4). Noch stärker hieße schwarze Ecken bei eingebauten Kameras und eine Mitte, die 5-fach
und mehr hochgerechnet wird. In 4:3 bekommt analog (125°) ab 90 % einen kleinen Bildkreis in den Ecken (bei 100 % Ecke
83°), HD in 4:3 hat ihn ab 75 %.

**Bildkreis.** Eine einzige geradlinige Projektion kann höchstens knapp 90° neben die Achse sehen. Propwash rendert
deshalb bis höchstens **80°** neben der Achse (`FisheyeLens.MAX_FIELD_DEG`); dahinter liegt der Rand des Bildkreises,
mit 1,5 px weicher Kante schwarz – wie bei echten Optiken, deren Bildkreis kleiner als der Sensor ist. Das trifft nur
sehr weite Kameras: bei 80 % 16:9 ab H ≈ 140°, 4:3 ab H ≈ 128° (eingebaut: digital HD in 4:3, Kreativ-Brille mit
eigenem weitem Sichtfeld), bei 100 % 16:9 ab H ≈ 134°, 4:3 ab H ≈ 121°. Alle eingebauten Module in 16:9 füllen das
Bild bei jeder Stärke bis in die Ecken, analog in 4:3 bis 85 % (ab 90 % kleiner Bildkreis), digital HD in 4:3 bis 70 %.

## 3. Rendern und Entzerren

1. **Breiter rendern.** `VideoState#cameraFov` (aus `mixin.video.client.CameraMixin` auf `Camera#calculateFov`) gibt
   statt des flachen Sichtfelds das senkrechte Sichtfeld eines geradlinigen Bildes zurück, das alle Sehstrahlen des
   Fisheye-Bildes bis in die Ecken (oder bis zum Bildkreis) enthält (`FisheyeLens#renderVerticalDeg`). Das
   Seitenverhältnis bleibt das des Fensters; die Ecke des Bildes liegt dann genau in der Ecke des Renderbildes
   (radialsymmetrische Abbildung). Der Rest des Spiels sieht ein normales, nur breiteres Bild: Frustum-Culling,
   Himmel, Wolken, Partikel, Shaderpacks, Props im Bild und Scheinwerfer laufen ohne Änderung.
2. **Entzerren im Video-Pass** (`video.client.VideoPass`, Pipeline `propwash:pipeline/fpv_lens`, Shader
   `post/fpv_lens.fsh`, UBO `PropwashLens`: Fenstergröße, Bildbereich, `f`, Radius des Bildkreises in Pixeln, Tangens
   der halben Renderbreite und -höhe, Kantenbreite und `q` der Stärke). Für jeden Bildpunkt: Abstand zur Bildmitte →
   Winkel `θ = Umkehrung(r / f)` (§2.1, `atan` bzw. `atanh`) → Richtung `tan θ` → Stelle im Renderbild. Den Tangens nimmt der Pass aus dem tatsächlichen Sichtfeld der Kamera dieses
   Bildes, nicht aus der Einstellung – ändert ein anderer Mod das Sichtfeld, stimmt die Abbildung trotzdem.
3. **Reihenfolge am Hook** nach `GameRenderer#applyPostEffects`: Bild der Action-Cam (`ActionCamRecorder`, aus dem
   breiten Renderbild) → Entzerrung → Videoeffekte (`post/video_feed.fsh`, unverändert, auf dem entzerrten Bild) →
   HUD mit OSD. Mit Videoeffekten entzerrt der Pass direkt in sein Bild (`frame`), statt es zu kopieren; ohne
   Videoeffekte (digital mit vollem Signal, Kreativ-Brille) kopiert er das Renderbild und entzerrt zurück ins
   Hauptbild. Hält das digitale Bildtakt-Modell ein Bild fest, bleibt auch das entzerrte Bild stehen.
4. **Filter.** In der Bildmitte wird hochgerechnet (siehe §4): Catmull-Rom (9 bilineare Abtastungen) und, wo die
   Helligkeit an einer Kante springt, eine Kantenglättung entlang der Kante (4 + 4 Abtastungen), damit
   Treppenstufen des Renderbildes nicht vergrößert erscheinen. Am Rand wird verkleinert (radial bis 5-fach): dort
   mittelt der Pass bis zu vier Abtastungen entlang des Radius gegen Flimmern; dazwischen wird überblendet.
5. **Shaderpacks** (Iris/Oculus): Am Hook liegt das fertige Bild des Packs im Hauptbild; die Entzerrung läuft auch mit
   Shaderpack (geprüft mit Complementary Reimagined r5.9.3, §7). Die Videoeffekte bleiben dort wie bisher das
   Ersatz-Overlay (`docs/design/shader-packs.md` §3).

## 4. Schärfe der Bildmitte

Das Renderbild hat die Auflösung des Fensters, muss aber breiter sein als das Fisheye-Bild. In der Mitte werden
deshalb Bildpunkte hochgerechnet (`FisheyeLens#centreUpscale`):

| Videomodul | 16:9 (80 %) | 4:3 (80 %) | 16:9 (100 %) | 4:3 (100 %) |
|---|---:|---:|---:|---:|
| digital Lite 104° | 1,64-fach | 1,89-fach | 1,86-fach | 2,31-fach |
| analog 125° | 2,42-fach | 3,49-fach | 3,11-fach | 4,70-fach (Bildkreis) |
| digital HD 130° | 2,78-fach | 4,06-fach (Bildkreis) | 3,82-fach | 4,70-fach (Bildkreis) |

Unter 80 % sinkt die Hochrechnung bis 1,0 bei 0 % (§2.1); wer eine schärfere Mitte will, stellt weniger ein.

Bei 1080p entspricht die Mitte des HD-Bildes etwa einem 390-Zeilen-Bild, bei 1440p 520, auf einem Retina- oder
4K-Bildschirm 780 Zeilen; analog in 4:3 etwa 310 Zeilen bei 1080p – ungefähr so viel, wie ein echtes analoges Bild
nach Zeilensprung und Kell-Faktor zeigt (480 × 0,7 ≈ 340). Zum Rand hin wird es schärfer, bei echten Optiken ist es
umgekehrt. Die Kantenglättung nimmt die Treppen, das Bild wirkt in Bewegung wie ein Kamerabild.

Höhere Renderauflösung (Supersampling) wäre der direkte Weg, ist aber nicht sauber machbar: Minecraft 26.3 zeichnet
Welt und GUI in dasselbe fenstergroße Hauptbild und gibt es unskaliert aus; ein größeres Hauptbild nur für die Welt
hieße, das Ziel des Level-Renderers auszutauschen (Sodium, Iris und viele Mods lesen es direkt), ein größeres für
alles verschiebt Scissor-Rechtecke der GUI und kostet bei 1,5-fach schon 2,25-mal so viele Bildpunkte. Wer die
gestochen scharfe Mitte vorzieht, stellt den Regler niedriger oder auf 0 %.

## 5. Was sich anpasst

Alles folgt der eingestellten Stärke über das Objektiv des aktuellen Bildes (`FpvLens#rendered`, mit `FisheyeLens#bend`).

| Teil | mit Fisheye |
|---|---|
| OSD (`osd.OsdProjection#setupFisheye`, `OsdRenderer.View#fisheyeDeg` und `#fisheyeBend`) | Horizont, Leiter, Heimatpfeil und Marken rechnen mit der Abbildung der Stärke (`r = f · θ` bei 80 %) im Bildbereich; die Horizontlinie berührt den gebogenen echten Horizont an seinem der Mitte nächsten Punkt mit gleicher Neigung |
| Action-Cam (`ActionCamRecorder`) | bei jeder Stärke Ausschnitt wie im flachen Bild (`FisheyeLens#flatTangent`), also dieselbe Aufnahme wie bisher; sie stammt aus dem breiteren Renderbild und damit aus weniger Bildpunkten (HD 16:9: Tangens 3,16 statt 2,14 über die Fensterbreite, also 68 % der bisherigen Pixel je Richtung) |
| Kamerapfad für Flashback | unverändert bei jeder Stärke (eigenes Exportformat aus dem horizontalen Sichtfeld; Flashback zeichnet selbst geradlinig) |
| Props im Kamerabild in Werkbank und Wartungsmenü (`CameraOcclusion#fisheyePercent` mit Stärke) | Anteil für das eigene Objektiv bei der eingestellten Stärke (neu gerechnet, wenn sie sich ändert); Fischaugen-Abtastung (320 × 180), gegen Strahlen je Bildpunkt geprüft. Warnung der Bauanalyse und API-Wert bleiben die flache 16:9-Referenz (Eigenschaft des Bauplans). Eingebaute Drohnen 16:9 flach / Fisheye: Whoop 6,2 / 3,4 %, Cinewhoop 1,9 / 1,8 %, 5″ 8,4 / 6,7 %, Race 11,0 / 9,0 %, 7″ 9,5 / 8,8 % |
| Ersatz-Aufhellung des Scheinwerfers (`HeadlightOverlay`) | Größe nach dem Maßstab der Bildmitte (`VideoState#displayFov`) |
| FPV-Auge (`FpvEyeGuard`) | prüft die breitere Sichtpyramide; die Nahebene ist bei 50 mm breiter (HD 16:9 ±16 cm statt ±11 cm), rückt also in engen Lücken etwas öfter näher |
| Videoeffekte | liegen auf dem entzerrten Bild (Zeilen, Rauschen, Blöcke bleiben gerade wie beim echten Sender) |

## 6. Leistung

Selbsttest `objektiv: Bildrate flach gegen Fisheye bei niedriger und hoher Grafik` (Fabric, Fenster 1920 × 1080,
HD-Freestyle schwebend 5 m über einem Feld aus Säulen, je Grafikstufe fünf Paare flach/Fisheye zu 3 s, im Wechsel):

| Lauf | Sichtweite (Grafik) | flach: B/s, Median je Bild | Fisheye: B/s, Median je Bild | Mehraufwand je Bild, Median der 5 Paare (Spanne) |
|---|---|---:|---:|---:|
| Fabric | 6 (niedrig) | 741, 1,18 ms | 741, 1,20 ms | +0,08 ms (−0,30 bis +0,32) |
| Fabric | 16 (hoch: Wolken, Umgebungsverdeckung, Schatten) | 575, 1,69 ms | 491, 1,90 ms | +0,13 ms (−0,08 bis +0,71) |
| NeoForge | 6 (niedrig) | 679, 1,33 ms | 667, 1,36 ms | +0,12 ms (−0,54 bis +0,43) |
| NeoForge | 16 (hoch) | 474, 2,07 ms | 448, 2,26 ms | +0,10 ms (−0,23 bis +1,02) |

Fisheye kostet auf diesem Mac also rund 0,1 ms je Bild, bei 144 Bildern je Sekunde (6,9 ms) unter 2 %, bei 60 B/s
unter 1 %. Die Mittelwerte der Bildrate streuen stärker als der Mehraufwand selbst (einzelne Abschnitte mit
Nachladen, im zweiten Slot lief ein anderes Minecraft); maßgeblich ist der paarweise Vergleich direkt
nacheinander.

Der Mehraufwand besteht aus dem breiteren Sichtfeld (mehr Geometrie im Frustum) und einem Vollbild-Pass mit 9 bis 17
Texturzugriffen je Bildpunkt, dazu zwei Bildpuffer in Fenstergröße (bei 1080p je 8 MB), die der Video-Pass auch für
die Videoeffekte nutzt. Mit Shaderpack wurde die Bildrate nicht gemessen, der Pass ist derselbe.

## 7. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Ein Bild breiter rendern und im Video-Pass entzerren | ein einziger Durchgang, alles andere bleibt Vanilla-Rendering; funktioniert mit Sodium und Iris | Würfelkarte (sechs Durchgänge, sechsfache Last); Verzerrung im Vertex-Shader (gerade Blockkanten blieben gerade, bricht jedes Shaderpack) |
| f-θ mit dem H des Videomoduls über die Bildbreite | passt zu den Herstellerangaben (H/V/D), H bleibt die bekannte Zahl im Tab und in der Werkbank | stereografisch (weniger Verzeichnung, passt nicht zu V/D); Fisheye mit dem D der Hersteller (würde alle Werte neu verteilen) |
| Standard Fisheye, auch für bestehende Spieler | Realismus ist das Ziel, Bildmitte 1,4–1,9-fach größer ist der größte Hebel fürs Tempo-Gefühl, Mehraufwand klein; Umschalten jederzeit im Video-Tab | Standard flach (wer nie in den Tab schaut, merkt nichts vom Gewinn); Umstellung nur für neue Spieler |
| ein Regler für alle Kameras (erst ein Schalter Fisheye/Flach, nach dem Spieltest ein Regler) | einfach, persönlich, kein Netzwerk- oder API-Feld; wer das Fisheye zu stark oder zu schwach findet, stellt es ein | Objektiv je Videomodul über die Parts-API (später möglich, siehe §9) |
| Bildkreis ab 80° neben der Achse | hält die Hochrechnung in der Mitte begrenzt, sieht aus wie echte Ultraweitwinkel mit dunklen Ecken | bis 89° rendern (Mitte 5- bis 10-fach hochgerechnet); weite Kameras flach zeichnen |
| keine höhere Renderauflösung | nicht ohne Eingriff in das Hauptbild von Minecraft machbar (§4) | Hauptbild vergrößern; Welt in eigenes Ziel umleiten |
| Catmull-Rom plus Kantenglättung in der Mitte, radiale Mittelung am Rand | Mitte ohne vergrößerte Treppen und ohne Weichzeichner, Rand ohne Flimmern | nur bilinear (weich und treppig); Lanczos (Ringe an Blockkanten, teurer) |
| Entzerrung vor den Videoeffekten | die Optik formt das Bild, der Sender stört es danach; Zeilen und Blöcke bleiben gerade | Effekte vor der Entzerrung (gebogene Zeilen und Blöcke) |
| Entzerrung auch mit Shaderpack und mit dem Ersatz-Overlay | das Hauptbild am Hook ist fertig gezeichnet (mit Complementary geprüft); Fisheye ist eine Eigenschaft der Kamera, nicht des Effekts | Fisheye nur ohne Shaderpack |
| Action-Cam-Aufnahme bleibt flach mit gleichem Ausschnitt | eigene Kamera mit eigener Optik; Aufnahmen sollen nicht von der Brille abhängen | Aufnahme mit Fischauge der FPV-Kamera |
| Props im Bild in Werkbank und Wartungsmenü nach dem eigenen Objektiv, Warnung und API flach | Zahl passt zum eigenen Bild; die Warnung ist eine Eigenschaft des Bauplans | API-Wert je Spieler |
| Selbsttests sonst flach über einen Test-Schalter (`FpvLens#testStrength`, 0 %) | bestehende Pixel- und Winkelprüfungen bleiben gültig, die Einstellung des Spielers bleibt unverändert (wie bei der Kamera-Vibration) | alle Prüfungen auf Fisheye umrechnen |
| Stärke als Familie `tan(√q·θ)/√q` … `tanh(√−q·θ)/√−q` mit q = 1 − Stärke/0,8 | eine Formel ohne Fallunterscheidung im Verlauf, enthält flach (0 %), stereografisch (60 %) und f-θ (80 %, bitgleich mit der ersten Fassung); die Verzeichnung (θ³-Glied q/3) und die Bildmitte wachsen linear mit dem Regler; nie gefaltet | lineare Mischung von flach und f-θ (keine echte Optik, nicht über f-θ hinaus); q mit `sin` statt `tanh` (orthografisch faltet bei 90°); Parameter linear in k statt k² (Regler um den Standard fast wirkungslos, unten sehr empfindlich) |
| Standard 80 %, 100 % etwas stärker als flächentreu | der Standard bleibt das Bild der ersten Fassung; 100 % ist das Stärkste, bei dem alle eingebauten 16:9-Kameras noch das ganze Bild füllen und die Mitte höchstens 3,8-fach hochgerechnet wird | 100 % = f-θ (nichts stärker möglich); 100 % = orthografisch (schwarze Ecken bei allen eingebauten Kameras, Mitte 5- bis 6-fach hochgerechnet) |
| Regler in 5-%-Schritten, 0 % schaltet den Pass ab | 21 Stellungen reichen, 80 % liegt darauf; 0 % ist genau das alte flache Bild ohne Umweg über die Entzerrung | Schritte von 1 % (Zahl ohne sichtbaren Unterschied); 0 % als Entzerrung mit q = 1 (gleiche Abbildung, aber weicher durch das Nachfiltern) |
| alte Einstellung „Flach“ → 0 %, „Fisheye“ → 80 % beim Laden | niemand verliert seine Wahl; die Datei wird erst beim nächsten Speichern umgeschrieben | Feld `lens` behalten (zwei Felder für eine Sache) |
| Action-Cam und Flashback-Pfad bleiben bei jeder Stärke flach | eigene Kamera mit eigener Optik wie bisher; der Ausschnitt kommt bei jeder Stärke aus dem breiteren Renderbild | Aufnahme mit der Stärke der Brille |

## 8. Tests

- JUnit `video.FisheyeLensTest`: Bildbreite = H des Moduls, Winkel linear zum Radius, Bildmitte 1,41/1,76/1,89-fach,
  V und D wie echte Kameras, jeder Bildpunkt im Bildkreis hat Quelle im Renderbild (16:9, 4:3, 21:9, Hochkant,
  Retina, H 60–160°), Renderbild nicht breiter als nötig, Bildkreis für weite Optiken, Projektion und Umkehrung,
  Filter-Fußabdruck. `osd.OsdProjectionFisheyeTest`: Winkel linear, Horizont um den Winkel statt den Tangens versetzt,
  Leiter und Horizont-Azimut passen, Horizontlinie tangential am gebogenen Horizont.
  `sim.CameraOcclusionFisheyeTest`: Props-Anteil mit Fisheye gleich der Strahlrechnung je Bildpunkt, schmale Optik
  ≈ flach, auch bei 5/40/100 %; 1 % ≈ flach.
- JUnit zur Stärke (seit dem Spieltest 15.10.): `video.FisheyeStrengthTest` – 0 % geradlinig, 60 % stereografisch,
  80 % bitgleich mit der ersten Fassung (Brennweite, Bildkreis, V), 100 % = `2·tanh(θ/2)`; Umkehrung und Ableitung
  passen zur Abbildung; H spannt bei jeder Stärke die Bildbreite; Mitte wächst, Höhe sinkt, Diagonale wächst mit der
  Stärke; 5 % fast flach; bei 100 % füllen alle eingebauten 16:9-Kameras das Bild (16:9 bis 133°), Mitte höchstens
  3,8-fach hochgerechnet; jeder Bildpunkt im Bildkreis hat Quelle (vier Fensterformen, H 60–160°, sieben Stärken);
  Projektion und Umkehrung; Filter-Fußabdruck. `osd.OsdProjectionFisheyeTest#lensStrengthBendsTheProjectionLikeTheVideoLens`:
  OSD-Projektion gleich `FisheyeLens#project` bei 20/80/100 %, Horizont und Leiter passen.
  `video.client.VideoConfigLensStrengthTest`: Standard 80, „FLAT“ → 0, „FISHEYE“ → 80, neues Feld hat Vorrang,
  Grenzen 0–100, Kopie und Speichern.
- Selbsttest `objektiv: Fisheye je Videosystem und Bildformat – Marken an der berechneten Stelle, Bildmitte größer`
  (Fabric und NeoForge): schwebt vor einer Schachbrettwand mit 35 magentafarbenen Marken, je Fall (analog 16:9,
  Lite 16:9, HD 16:9, analog 4:3) ein Bild flach und eines mit Fisheye ohne HUD. Erwartete Lage und Fläche jeder Marke
  aus der Kamerapose dieses Bildes und dem Objektiv-Modell (flächengewichteter Schwerpunkt über 8 × 8 Zellen), gefunden
  über zusammenhängende Magenta-Flecken. Seit dem Spieltest 15.10. je Fall zusätzlich 100 % (analog auch 40 %); die
  Bildmitte muss mit jeder Stärke größer werden. Ergebnis Fabric 15.10. (Lage Median / größte): analog 0 % 1,00 / 1,23 px,
  40 % 1,18 / 1,39, 80 % 1,10 / 1,49, 100 % 1,08 / 1,59; Lite 80 % 0,51 / 0,60, 100 % 0,68 / 0,78; HD 80 % 0,31 / 1,00,
  100 % 0,31 / 1,58; analog 4:3 80 % 1,21 / 2,64, 100 % (Bildkreis 80°) 1,19 / 2,50. Bildmitte gegen flach: analog
  1,39 / 1,74 / 1,90-fach bei 40 / 80 / 100 % (erwartet 1,39 / 1,73 / 1,90), Lite 1,36 / 1,45 (1,37 / 1,45), HD 1,78 /
  1,97 (1,81 / 1,98), analog 4:3 1,73 / 1,88 (1,73 / 1,90). Bilder `fisheye_<fall>_fisheye_100.png` und
  `fisheye_analog_fisheye_40.png`. NeoForge 15.10.: Lage im Median 0,1–1,2 px, höchstens 2,3 px; Bildmitte analog
  1,39 / 1,75 / 1,92, Lite 1,36 / 1,44, HD 1,79 / 1,96, analog 4:3 1,70 / 1,87. Ergebnis Fabric der ersten Fassung: Lage im Median 0,1–1,2 px daneben, höchstens 2,6 px;
  Marke in der Bildmitte mit Fisheye 1,74-fach (analog, erwartet 1,73), 1,36-fach (Lite, 1,37), 1,78–1,82-fach (HD,
  1,81–1,82), 1,72–1,73-fach (analog 4:3); NeoForge 1,75 / 1,36 / 1,79 / 1,70. Analog liegt die Lage im Median rund
  1 px daneben (Farbversatz des analogen Effekts), digital 0,1–0,5 px. Mit Complementary Reimagined (Iris) dieselben
  Prüfungen bestanden (Median 0,1–0,7 px). Mit Bildschirmfotos dazu je Fall die Bilder mit HUD und Props
  (`fisheye_<fall>_flat.png`, `fisheye_<fall>_fisheye.png`) und analog/HD mit schwachem Signal
  (`fisheye_<fall>_weak_signal.png`, Videoeffekte auf dem entzerrten Bild).
- `objektiv: Bildrate flach gegen Fisheye bei niedriger und hoher Grafik` (§6).
- `video: Action-Cam mit Fisheye-Objektiv – gleicher Ausschnitt wie flach, Aufnahme läuft weiter`: Ausschnitt
  (Tangens) mit Fisheye 80 % und 100 % gleich flach, Quelle breiter (HD: Tangens 2,14 flach, 3,16 bei 80 %, 3,92 bei
  100 %), MP4 mit Bildern aus allen Abschnitten.
- Iris (lokal, `docs/testing.md` §5): `bildschirmfotos: Iris – FPV tief über Wasser mit Fisheye-Objektiv` mit und ohne
  Shaderpack.

## 9. Offen

- **Objektiv je Kamera:** Die DJI-O4-Lite-Klasse (digital Lite) hat laut Hersteller fast verzeichnungsfreie 117,6°
  Diagonale bei 14 mm Kleinbild-Brennweite – eher geradlinig als Fischauge. Ein optionales Feld `camera_lens` im
  Videomodul (Parts-API, additive Minor-Version) könnte eine Stärke je Kamera festlegen; der Regler wäre dann ein
  Faktor darauf.
- **Schärfere Mitte** nur mit höherer Renderauflösung (§4) oder einem zweiten, schmalen Durchgang für die Mitte
  (doppelte Last).
- **4:3 im 16:9-Fenster** rendert seitlich wie flach mehr als nötig; eine Projektion mit eigenem Seitenverhältnis
  würde die Mitte waagerecht schärfer machen, senkrecht nicht.
- **NeoForge mit Iris** ist wie bisher nicht prüfbar (Iris stürzt im NeoForge-Entwicklungslauf ab,
  `docs/design/shader-packs.md` §4); der Code ist gemeinsam.
- Action-Cam mit eigener Weitwinkel-Optik (GoPro „Wide“) statt flachem Ausschnitt.
