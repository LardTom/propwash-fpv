# Bildformat 16:9 / 4:3 im FPV (Propwash 0.5.0)

Wunsch aus der Community: ein 4:3-Schalter in den Einstellungen. Analoge FPV-Kameras liefern ein 4:3-Bild, viele
Brillen zeigen es mit schwarzen Rändern links und rechts. Propwash zeigt das FPV-Bild bisher immer fensterfüllend.

## Verhalten

- Persönliche Einstellung **Bildformat** im Konfigurator-Tab „Video“ (`VideoConfig#imageFormat`, Datei
  `config/propwash/video.json`): `AUTO`, `WIDE` (16:9) oder `FOUR_THREE` (4:3). Standard ist **16:9**.
- **16:9** ist genau das bisherige Bild: es füllt das ganze Fenster, das Sichtfeld rechnet bitgleich wie vor 0.5.0.
- **4:3** zeigt das Bild wie eine 4:3-Kamera: dieselbe Breite des Sichtfelds, oben und unten mehr Bild, schwarze Ränder
  links und rechts. Ist das Fenster schmaler als 4:3, liegen die Ränder oben und unten.
- **Auto** wählt nach dem Videosystem des Senders: analog 4:3, digital 16:9. Beim Zuschauen zählt der Sender der
  beobachteten Drohne, mit der Kreativ-Brille ebenso der Sender der Drohne.
- Das OSD sitzt im 4:3-Bild (Raster 30 × 16 über die 4:3-Fläche), ebenso die Vorschau im OSD-Editor und die
  Kacheln „So zerfällt das Bild“ im Video-Tab. Die Bildstörungen (analog und digital) liegen im 4:3-Bild.
- Aufnahmen der Action-Cam bleiben 16:9 mit demselben Sichtfeld wie im 16:9-Bild; der Kamerapfad für Flashback hat sein
  eigenes Format und bleibt unberührt.

## Rechnung

Das Sichtfeld `H` der Kamera im Videomodul (`camera_fov_deg`, horizontal) spannt die **Breite des Bildes**. Minecraft
rechnet die Projektion mit dem senkrechten Sichtfeld über die ganze Fensterhöhe und dem Seitenverhältnis des Fensters.
Für ein Bild der Breite `w` (Pixel) in einem Fenster der Höhe `h_F` gilt deshalb

    tan(V_F / 2) = tan(H / 2) · h_F / w

(`FovMath.verticalFromHorizontal(H, ImageArea)`, Seitenmaß `ImageArea#spanAspect()` = `w / h_F`). Bei 16:9 ist
`w` die Fensterbreite, also exakt die bisherige Rechnung. Bei 4:3 im breiten Fenster ist `w = 4/3 · h_F` und das Bild
so hoch wie das Fenster:

    tan(V / 2) = tan(H / 2) · 3/4

– genau das senkrechte Sichtfeld einer echten 4:3-Kamera mit derselben horizontalen Öffnung. Das gerenderte Fenster
reicht seitlich über das Bild hinaus; diese Streifen decken die Ränder ab.

| Kamera `H` | 16:9 senkrecht | 4:3 senkrecht | Fensterbreite bei 4:3 (verdeckt) |
|---|---|---|---|
| 104° (Lite) | 71,5° | 87,7° | 119,3° |
| 120° | 88,5° | 104,8° | 133,2° |
| 125° (analog) | 94,4° | 110,5° | 137,3° |
| 130° (HD) | 100,7° | 116,3° | 141,4° |
| 160° (Grenze) | 145,2° | 153,5° | 164,9° |

### Bildbereich in Pixeln (`video.ImageArea`)

- Pillarbox (Fenster breiter als 4:3): Breite `w = W − 2 · round((W − 4/3 · h_F) / 2)`, also auf ganze Pixel und mit
  **gleich breiten Rändern** links und rechts; `x = (W − w) / 2`.
- Letterbox (Fenster schmaler als 4:3): Höhe entsprechend, gleich hohe Ränder oben und unten. Dann ist `w` die
  Fensterbreite und das Fenster-Sichtfeld gleich dem 16:9-Fall; das OSD rechnet sein Sichtfeld auf die Bildhöhe um
  (`FovMath.verticalWithin`).
- Alles in Bildpunkten des Framebuffers; das OSD-Raster ist derselbe Bereich geteilt durch die GUI-Skala, die Ränder
  zeichnet die HUD-Ebene in Bildpunkten. Damit passt alles bei jeder GUI-Skala und Fenstergröße.

### Was im 4:3-Bereich liegt

- **Videoeffekte** (`post/video_feed.fsh`): neuer Uniform-Vektor `Area` (Ursprung und Größe als Anteil des Fensters).
  Der Shader rechnet in Bildkoordinaten (`areaCoord`), tastet nur innerhalb des Bildes ab (`sampleIn`/`sampleHistory`,
  am Rand geklemmt) und schreibt außerhalb Schwarz. Zeilen, Vignette, Sync-Versatz, Rollbalken, Blockartefakte und
  Körnung beziehen sich so auf das 4:3-Bild. Bei 16:9 ist `Area = (0, 0, 1, 1)` und die Rechnung unverändert.
- **Ränder** (`VideoOverlay`, HUD-Ebene `propwash:video_feed`, sichtbar auch mit F1): zwei schwarze Flächen, immer, auch
  ohne Shader-Pass. Das Ersatz-Overlay (Shaderpacks, einfaches Overlay) zeichnet im Bildbereich.
- **Scheinwerfer**: der Tiefen-Pass rechnet mit der echten Projektion; die Ersatz-Aufhellung bleibt auf der Bildmitte
  und wird von den Rändern beschnitten.
- **OSD** (`OsdHud#grid`): Raster, Projektion (Horizont, Home-Pfeil) und Sync-Versatz des Analog-OSD im Bildbereich;
  Renn-Meldungen im OSD-Stil nutzen dieselbe Textgröße und die OSD-Zeile im Bildbereich (`RaceHud`), ebenso das
  Banner beim Zuschauen (`SpectateHud`: Zeile, Mitte und größte Breite aus dem Raster des Bildbereichs).
- **Props im Bild**: Werkbank und Wartungsmenü zeigen den Anteil für das eigene Bildformat (4:3-Zeile in der Werkbank).
  Die Warnung der Bauanalyse und die Werte der API bleiben die 16:9-Referenz (Eigenschaft des Bauplans). Der
  Selbsttest vergleicht in 4:3 die Analyse mit den Pixeln im 4:3-Bereich.

### Was unverändert bleibt

- Flugmodell und Physik (kein Bezug zum Bild), Bauanalyse, API-Werte, Netzwerkprotokoll (die Einstellung ist persönlich).
- **Action-Cam**: der Ausschnitt rechnet mit dem 16:9-Sichtfeld (`ImageArea#wideTangentY`), die Aufnahme zeigt also
  dasselbe wie im 16:9-Bild. Weil das Fenster bei 4:3 ein größeres Sichtfeld zeigt, stammt der Ausschnitt aus weniger
  Bildpunkten (bei 1920 × 1080 aus 1440 × 810) – ähnlich wie der Zuschnitt der Stabilisierung.
- **Kamerapfad für Flashback**: eigenes Exportformat 16:9 aus dem horizontalen Sichtfeld.
- **FPV-Auge** (`FpvEyeGuard`): prüft die Sichtpyramide des ganzen Fensters, bei 4:3 also etwas großzügiger.

## Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Drei Werte Auto / 16:9 / 4:3 | Auto passt zu Piloten, die analog und digital fliegen, wie eine echte Brille | nur zwei Werte; ein Format je Brille |
| Standard 16:9 | hält das bisherige Bild für alle bestehenden Spieler; Ränder kosten im Spiel Bildfläche und wirken ohne Erklärung wie ein Fehler | Standard Auto (analoge Piloten hätten plötzlich Ränder) |
| 16:9 heißt „füllt das Fenster“ | genau das bisherige Verhalten auch bei 16:10 oder 21:9 | echtes 16:9 mit Rändern auf 16:10-Bildschirmen |
| horizontales Sichtfeld bleibt, senkrecht wächst | das Videomodul gibt `H` horizontal an; so verhält sich eine 4:3-Kamera mit derselben Optik | gleiches senkrechtes Sichtfeld (4:3 würde schmaler); diagonales Sichtfeld |
| Ränder als HUD-Ebene in Bildpunkten, zusätzlich Schwarz im Shader | wirkt auch ohne Shader-Pass, mit Shaderpacks und bei jeder GUI-Skala | eigener Viewport für die Welt (greift tief in den Renderer, bricht mit anderen Mods) |
| Zuschauen nach eigener Einstellung, Auto nach dem Sender der beobachteten Drohne | persönliche Anzeige, kein Netzwerk-Feld, kein neues Protokoll | Format des Piloten übertragen |
| Action-Cam behält ihr 16:9-Sichtfeld | eigene Kamera mit eigenem Format; Aufnahmen sollen nicht von der Brille abhängen | Aufnahme aus dem ganzen Fenster (breiteres Bild bei 4:3) |
| Werkbank zeigt Props für das eigene Format, Warnung bleibt 16:9 | Zahl passt zum eigenen Bild; die Warnung ist eine Eigenschaft des Bauplans | Warnung je Spieler |

## Tests

- JUnit `video.ImageAreaTest`: Auflösung von Auto, Bildbereich für 16:9-, 21:9-, 16:10-, 5:4- und Hochkant-Fenster
  (gleiche Ränder, 4:3 auf ±1 Pixel), Bildbreite = Sichtfeld des Moduls, Bildhöhe = echte 4:3-Kamera, 16:9 bitgleich zur
  alten Rechnung, Ausschnitt der Action-Cam, Props-Analyse 4:3 deckt die 16:9-Fläche ab.
- Selbsttest „video: Bildformat im Video-Tab, 4:3-Vorschau im OSD-Editor“ und „video: Bildformat 16:9, 4:3 und Auto –
  Sichtfeld, Bildbereich, Balken und OSD im FPV“ (analoge und digitale Drohne, Kamera-Sichtfeld, OSD-Raster, Pixel der
  Ränder); „drone-render: Props im FPV-Bild 4:3“ vergleicht Analyse und Bild im 4:3-Bereich; der Zuschau-Selbsttest
  prüft, dass Auto dem analogen Sender folgt.
- Bildschirmfotos `fpv_format_wide` / `fpv_format_4_3` aus demselben ruhigen Schwebeflug in einem Schritt, kurz
  hintereinander (auch GUI-Skala 2 und 4; das Protokoll nennt die Abweichung der Kameralage zum 16:9-Bild), `headlight_cave` / `headlight_cave_4_3` (gleiche Lage in der Höhle), `video_4_3_analog_stage3`,
  `video_4_3_analog_stage4`, `video_4_3_digital_breakup`, `video_auto_digital_breakup`, `spectate_view_4_3`,
  `configurator_osd_4_3`, `configurator_video_format_auto`, Ordner `fpv_coverage_4_3` (sauberes Bild ohne Effekte,
  auf den 4:3-Bereich zugeschnitten).
