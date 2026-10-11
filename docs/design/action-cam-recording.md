# Action-Cam-Aufnahme

Eine Drohne mit Action-Cam (Zubehör oben, `docs/FOUNDATION.md` §15 „Zubehör“) nimmt echtes Video auf: das saubere
Kamerabild der Drohne **ohne Funkstörungen und ohne OSD**, auf Wunsch mit dem Motorgeräusch als Tonspur, gespeichert als MP4
(H.264 + AAC) unter
`<Spielverzeichnis>/propwash/recordings/propwash_JJJJ-MM-TT_HH-MM-SS.mp4`. Besitzer: video (Aufnahme, Encoder, Container,
Tab, Taste, Aufnahmen-Liste, Ton-Encoder), drone (Kamera-Akku am Entity, USB-Laden, Payload), content (Kamera-Akku am Item,
Werkbank, Tooltips), input (Schalterfunktion „Aufnahme“), audio (Motorton-Abgriff), osd (REC-Element), guide (Flugschul-Szene).

## Encoder-Entscheidung

Anforderung: reines Java in beiden Loadern, H.264 in MP4, Hintergrund-Thread, Spiel bleibt flüssig, Jar-Größe
vernünftig, Lizenz verträglich mit einer All-Rights-Reserved-Mod.

| Option | Lizenz | Größe | Bewertung |
|---|---|---|---|
| JCodec 0.2.5 (`org.jcodec:jcodec`) | BSD-2-Clause (FreeBSD), verträglich mit ARR, Copyright-Hinweis nötig | über 1 MB Jar, per Jar-in-Jar in beide Loader | H.264-Baseline-Encoder (CAVLC, I/P) und MP4-Muxer vorhanden, aber einfädig, langsam (1080p deutlich unter Echtzeit), seit 2020 ohne Release, kein AAC-Encoder |
| JavaCV/FFmpeg, Xuggler, Humble | LGPL/GPL, native Bibliotheken je Plattform | 20–60 MB | scheidet aus: nativ, groß, Lizenzpflichten |
| Eigener Encoder | eigener Code (ARR) | ≈ 60 kB Klassen | volle Kontrolle: parallele Slices, Wiederholbilder für konstante Bildrate, eigene Ratenregelung, kein Jar-in-Jar |

**Entscheidung: eigener Encoder** (`video.record.h264`, `video.record.mp4`). Gründe: Echtzeit bei 1080p60 braucht
Mehrkern-Kodierung (parallele Slices), die JCodec nicht hat; keine Abhängigkeit, kein Jar-in-Jar, keine
Lizenzhinweise; die Mod bleibt klein. Weil keine Fremdbibliothek dazukommt, ändern sich die Gradle-Skripte nicht
(Fabric `include`/NeoForge `jarJar` entfallen).

### Hardware-Encoder über ffmpeg (0.5.0, `video.record.external`)

Der eigene Encoder rechnet auf dem Prozessor. Bei detailreichen Bildern mit Shaderpack (Bitrate am Anschlag, viele
Weitsuchen) schafft er auf einem Apple M4 Pro mit 5 Threads rund 50 Bilder/s bei 1080p – ohne Spiel daneben. Läuft das
Spiel mit Shadern, Chunk-Bau und Server-Thread mit, fehlen die Kerne; auf Windows laufen die Encoder-Threads mit
niedrigster Priorität zudem oft auf den Effizienzkernen. Deshalb nutzt die Aufnahme, wenn möglich, den Video-Encoder der
Grafikkarte oder des Prozessors.

| Option | Bewertung |
|---|---|
| Native Anbindung über FFM/JNI (VideoToolbox, Media Foundation, NVENC-SDK) | je Plattform eigener, ungetesteter nativer Code; ein Fehler in einer Funktionstabelle beendet das ganze Spiel ohne Fehlermeldung; NVENC, Quick Sync und AMF sind auf dem Entwicklungsrechner nicht prüfbar |
| ffmpeg-Bibliotheken mitliefern | 20–60 MB je Plattform, LGPL/GPL-Pflichten |
| **Installiertes ffmpeg als eigener Prozess** | ein Weg für alle Plattformen (`h264_videotoolbox`, `h264_nvenc`, `h264_qsv`, `h264_amf`, `h264_vaapi`, `h264_mf`), nichts wird mitgeliefert, ein Absturz von ffmpeg beendet nur den Prozess; auf dem Entwicklungsrechner mit VideoToolbox vollständig prüfbar; Vorbild: ReplayMod rendert seit Jahren über ein externes ffmpeg |

**Entscheidung: ffmpeg als Prozess, streng optional.** `FrameEncoder` (`video.record`) ist die gemeinsame Schnittstelle;
`BuiltinEncoder` verpackt den eigenen Encoder, `FfmpegEncoder` schreibt die I420-Bilder roh in `stdin` und liest Annex B
aus `stdout` (`AnnexB` trennt NAL-Einheiten und Zugriffseinheiten auch über Puffergrenzen, wirft SPS/PPS/AUD aus den
Samples und merkt sich SPS/PPS für `avcC`). Muxer, Tonspur, Vorschaubilder und Aufnahmen-Liste bleiben dieselben.

- **Aufruf:** `-f rawvideo -pix_fmt yuv420p -video_size B×H -framerate fps` (BT.709, begrenzter Bereich) → `-c:v <encoder>`
  mit `-profile:v main`, GOP 2 s (`-g`), `-bf 0` (keine B-Bilder, also keine Umordnung), Zielbitrate
  `min(Obergrenze, 1,5 × typische Bitrate der Qualitätsstufe)`, `-maxrate`/`-bufsize` = Obergrenze der Stufe; NVENC
  zusätzlich `-rc vbr -cq <QP der Stufe + 1>` (konstante Qualität mit Deckel wie der eigene Encoder). Je Encoder gibt es
  eine zweite, schlichtere Variante (nur `-b:v`) für ältere ffmpeg-Versionen. VA-API mit `-vaapi_device
  /dev/dri/renderD128 -vf format=nv12,hwupload`.
- **Suche** (`FfmpegLocator`, Ergebnis mit Herkunft `CUSTOM`/`GAME_FOLDER`/`PATH`/`KNOWN`/`SHELL`): eingetragener Pfad
  (`ffmpegPath` in `action_cam.json`, Datei oder Ordner; Anführungszeichen und `~` erlaubt), dann
  `<Spielverzeichnis>/propwash/ffmpeg/`, dann absolute `PATH`-Einträge, dann übliche Installationsorte – macOS Homebrew
  (`/opt/homebrew/bin`, `/usr/local/bin`, dazu nicht verlinkte Kegs `opt/ffmpeg*/bin` wie `ffmpeg-full`), MacPorts
  (`/opt/local/bin`), `~/bin`, `~/.local/bin`, Nix; Linux `/usr/bin`, `/usr/local/bin`, `/bin`, `/snap/bin`, Flatpak-Exporte,
  Linuxbrew, `~/bin`, `~/.local/bin`, Nix; Windows WinGet (`Links` im Nutzerprofil und unter `Programme`, dazu
  `WinGet\Packages\*ffmpeg*` bis vier Ebenen tief, weil WinGet ohne Entwicklermodus keine Verknüpfung anlegt, sondern den
  Paketordner in den `PATH` schreibt), Scoop (`apps\ffmpeg\current\bin`, `shims`, auch unter `%SCOOP%`), Chocolatey
  (`%ChocolateyInstall%\bin`) und `Programme\ffmpeg\bin`. Zuletzt fragt `LoginShell` auf macOS und Linux die Anmelde-Shell
  (`$SHELL -l -c 'command -v ffmpeg'`, nur sh/bash/zsh/dash/ksh/fish, Ausgabe in eine Datei statt in eine Pipe, 4 s
  Frist, einmal je Sitzung): Programme, die aus dem Dock oder einem Launcher starten, erben den `PATH` des Terminals
  nicht. Ordner, in die jeder Nutzer schreiben darf (`C:\ffmpeg`, `C:\ProgramData\chocolatey` ohne installiertes
  Chocolatey – deshalb nur über die Umgebungsvariable), und relative `PATH`-Einträge werden bewusst nicht durchsucht:
  dort könnte ein fremdes Programm unter dem Namen ffmpeg liegen. Zeigt der eingetragene Pfad auf nichts Ausführbares,
  gilt die automatische Suche weiter, und der Tab sagt, warum (`CustomPath.MISSING` oder `NOT_EXECUTABLE` – eine
  heruntergeladene Datei ohne Ausführungsrecht). Im eigenen Ordner `propwash/ffmpeg/` setzt die Suche das
  Ausführungsrecht selbst; eine Datei unter einem eigenen Pfad wird nie verändert.
- **Prüfung** (`HardwareEncoders`, ein Hintergrund-Thread niedrigster Priorität, Ergebnis je Pfad und Format
  zwischengespeichert): `ffmpeg -version` (Versionsnummer für die Anzeige) und `ffmpeg -encoders` → Kandidaten der
  Plattform (macOS VideoToolbox; Windows NVENC, Quick Sync, AMF, Media Foundation; Linux NVENC, Quick Sync, VA-API) → je
  Kandidat und Variante eine echte Probekodierung von 90 Bildern im Zielformat über dieselbe Pipe: verlangt werden 90
  Zugriffseinheiten, ein Schlüsselbild am Anfang, SPS/PPS und mindestens 75 % der Ziel-Bildrate. Gemessen wird erst nach
  30 Bildern Anlauf – (90 − bis dahin gelieferte Bilder) / Zeit bis zum Ende –, also ohne Prozessstart und
  Encoder-Initialisierung. Vorher zählte der Start mit: auf dem Entwicklungsrechner ergab das bei 720p 54–111 Bilder/s
  statt 211 im Dauerbetrieb und bei 1440p60 80 statt 123; ein Encoder mit langsamer Initialisierung (NVENC lädt Treiber
  und legt einen Kontext an) wäre so trotz ausreichender Leistung durchgefallen. Ein in ffmpeg gelisteter, aber nicht
  vorhandener Encoder (NVENC ohne NVIDIA-Karte) fällt weiter durch. Zustände: `NO_FFMPEG` (nichts gefunden), `UNUSABLE`
  (Datei gefunden, startet aber nicht, antwortet nicht binnen 10 s oder liefert keine Encoder-Liste), `NO_HARDWARE`,
  `READY`, `BROKEN`. Die Prüfung startet, sobald ein FPV-Flug mit Action-Cam beginnt oder der Tab offen ist; eine
  Aufnahme, die vor dem Ergebnis startet, nimmt den eigenen Encoder und sagt das in der Anzeige. „Testen“ im Tab
  (`HardwareEncoders.retest`) verwirft alle Ergebnisse samt Ausfall-Sperre und Shell-Ergebnis und prüft **alle**
  Kandidaten statt nur bis zum ersten Treffer.
- **Ausfall:** Stirbt ffmpeg mitten in der Aufnahme, schließt der Job das MP4 mit allen bis dahin gelieferten Bildern ab
  (Dauer passend, Tonspur gekürzt), meldet „Hardware-Encoder ausgefallen – Video bis dahin gespeichert“ und sperrt den
  Hardware-Weg bis zum Neustart; scheitert er vor dem ersten Bild, schlägt die Aufnahme sauber fehl, die nächste nimmt den
  eigenen Encoder. Liefert ein Encoder weniger Bilder als Bildplätze, bekommt das letzte Bild die Restdauer (`stts`),
  das Video ist immer so lang wie die Aufnahme.
- **Bildwiederholung:** Lücken (Spiel langsamer als die Aufnahmerate) füllt der Hardware-Weg, indem er das letzte Bild
  noch einmal schickt; der Encoder macht daraus ein fast leeres P-Bild. So bleibt die Bildrate konstant, wichtig für
  Schnittprogramme.
- **Lizenz:** Propwash liefert ffmpeg nicht mit und bindet es nicht ein; es startet nur ein vom Spieler installiertes
  Programm. Keine Lizenzpflichten für Propwash.

### Bitstrom

- H.264 **Constrained Baseline** (Profil 66, Constraint-Flags 0+1), 4:2:0, 8 Bit, CAVLC, Level aus Bildgröße,
  Bildrate und Bitrate (720p30 → 3.1, 1080p60 → 4.2, 1440p60 → 5.1).
- SPS mit VUI: BT.709, begrenzter Bereich (16–235), Bildrate (`time_scale = 2000 · fps`), `max_num_reorder_frames = 0`,
  Zuschnitt für nicht durch 16 teilbare Höhen (1080 → kodiert 1088).
- I-Bilder: je Makroblock Intra 16×16 (V/H/DC/Plane) oder Intra 4×4 (9 Richtungen, Moduswahl mit Kosten für den
  vorhergesagten Modus, blockweise rekonstruiert) – bei detailreichen Minecraft-Texturen rund 20 % kleinere
  Schlüsselbilder bei gleicher Qualität; Chroma-Intra (DC/H/V/Plane). In P-Bildern nur Intra 16×16 (Intra 4×4 kostete dort
  doppelte Zeit ohne Gewinn). P-Bilder: P_L0_16x16 mit Viertelpixel-Bewegung
  (6-Tap-Halbpixel-Ebenen je Referenzbild, Mittelung für Viertelpositionen), P_Skip, Intra-Makroblöcke bei besserem
  SAD.
- **Bewegungssuche** bis ±168 Pixel je Bild (Rand der Referenzbilder 192 Pixel; die Halbpixel-Ebenen werden nur bis 3 Pixel
  um das Bild berechnet und nach außen kopiert, das ist bitgleich zur Randfortsetzung des Decoders): Prädiktoren (Median, Skip,
  linker/oberer/oberer rechter Nachbar, gleicher Makroblock sowie rechter und unterer Nachbar im Vorbild, globale Bewegung =
  Median aller Vektoren des Vorbilds), Hexagon-Abstieg (Radius 2) mit Rauten-Feinschritt und Eckpunkten, bei schlechtem Treffer
  (SAD über 5 je Pixel) eine Weitsuche mit 16 Punkten auf sechs Skalen (±4 … ±128 Pixel), danach Halb- und Viertelpixel.
  Bewegungshinweise überleben Schlüssel- und Wiederholbilder, damit der erste P-Frame danach die Bewegung sofort wiederfindet.
  P_8x8-Partitionen wurden gebaut und gemessen (bitgenau mit ffmpeg), brachten auf allen Testclips aber nur 0–2 % und kosteten
  5–10 % Zeit; sie sind deshalb nicht enthalten.
- **Adaptive Quantisierung** (je Makroblock `mb_qp_delta`): Versatz = Stärke · 1,04 · (log2(Energie + 1) − 14,43), Energie =
  Varianz-Summe von Luma 16×16 und beiden Chroma 8×8 (wie x264 „aq-mode 1“), dazu bis −2 · Stärke in dunklen Blöcken
  (Luma-Mittel unter 64, voll unter 24), begrenzt auf −10 … +8 und den QP-Bereich. Flache und dunkle Flächen bekommen damit
  feinere Stufen (kein Banding, keine stehenden Blöcke), Texturen etwas gröbere. Stärke 0,8 (`EncoderSettings.DEFAULT_AQ`).
  Makroblöcke ohne Delta (Skip, kein Rest) übernehmen den QP des Vorgängers – so rechnet ihn auch der Deblocking-Filter.
- Residuen: 4×4-Integer-Transformation, DC-Hadamard (Luma 16×16, Chroma 2×2), Totzonen-Quantisierung, Dezimierung
  kleiner Blöcke wie in üblichen Encodern.
- Deblocking-Filter im Encoder nachgebildet, `disable_deblocking_filter_idc = 0`: gefiltert wird auch über Slice-Grenzen
  (vorher `2` – dort blieben alle 5–6 Makroblockzeilen waagerechte Kanten stehen, im Dunkeln deutlich sichtbar). Die Slices
  kodieren weiter parallel; danach filtert eine Wellenfront über das ganze Bild (Zeile r beginnt Makroblock x, sobald Zeile r−1
  bis x+1 fertig ist – dieselbe Reihenfolge wie im Decoder), mit QP je Makroblock (Mittel der beiden Seiten, Chroma über
  `QPc`).
- GOP 2 s (IDR), eine Referenz, `frame_num` fortlaufend. **Wiederholbilder** (`H264Encoder#repeat`): ein P-Slice nur aus
  Skip-Makroblöcken (≈ 10 Byte) füllt Lücken, wenn das Spiel langsamer läuft als die Aufnahmerate – das Video hat
  immer konstante Bildrate und die richtige Länge.
- **Ratenregelung: konstante Qualität mit Obergrenze** statt fester Bitrate. Jedes P-Bild bekommt den QP der Qualitätsstufe
  (Schlüsselbilder 2 besser), AQ verteilt innerhalb des Bildes. Ein Puffer mit der Obergrenze als Größe (1 s) zählt, was über
  der Obergrenze liegt: ab 25 % Füllung steigt der QP langsam, ab 50 % schneller, unter 10 % sinkt er zurück bis zur Stufe;
  zusätzlich schätzt die Regelung jedes Bild aus dem letzten gleichen Typ (Bits · 2^((QP_alt − QP)/6)) und hebt den QP
  vorab, wenn das Bild den Puffer über 90 % füllen würde. Wiederholbilder leeren den Puffer. QP-Bereich 12–46.
  Ruhige Szenen werden dadurch klein, schnelle und dunkle behalten ihre Qualität, solange die Obergrenze reicht.
- **Qualitätsstufen** (`video.record.RecordingQuality`, Tab „Action-Cam“, Standard „Standard“):

  | Stufe | QP | Obergrenze | 1080p60 typisch / höchstens |
  |---|---|---|---|
  | Hoch | 20 | 1,5 × Formatbitrate | ≈ 185 / 365 MB/min |
  | Standard | 23 | 1,0 × Formatbitrate (wie früher die feste Bitrate) | ≈ 110 / 240 MB/min |
  | Klein | 27 | 0,5 × Formatbitrate | ≈ 60 / 120 MB/min |

  Formatbitrate = `RecordingFormat.bitrate()` (0,26 Bit/Pixel bei 60 fps, 0,34 bei 30 fps). „Typisch“ ist eine Schätzung
  (0,20 / 0,12 / 0,065 Bit/Pixel, bei 30 fps × 1,25) – echte Flüge liegen je nach Szene zwischen etwa einem Drittel davon
  (Himmel, Höhle ohne Rauschen) und der Obergrenze (dichter Wald, Schnee mit Schatten).
- Presets je nach Rückstand der Warteschlange: Qualität (Viertelpixel), schnell (Halbpixel), schnellste (Vollpixel,
  weniger Intra-Prüfungen).

**Prüfung:** Während der Entwicklung wurde jede Änderung gegen **ffmpeg** (Dekodierung bitgenau gleich der
Encoder-Rekonstruktion, auch mit Deblocking, mehreren Slices, 1080-Zuschnitt und Wiederholbildern) und **Apple
AVFoundation** (`avconvert`, QuickLook-Vorschau) geprüft; `ffprobe` meldet „Constrained Baseline“, BT.709, 60/1 fps.
JUnit (`video.record.h264`, `video.record.mp4`, `video.record`) prüft Exp-Golomb, Emulation Prevention, Präfixfreiheit
aller CAVLC-Tabellen, Parse-Rückweg eines CAVLC-Blocks, SPS-Felder, Slice-Köpfe, GOP, Wiederholbilder, gleiche Bytes
bei 1 und 4 Threads, Rekonstruktions-PSNR, MP4-Boxen, Dauer, Bildzahl, `mdat`-Größe und die komplette Pipeline;
`EncoderQualityTest` prüft AQ (flache dunkle Flächen feiner als Rauschen, ohne AQ überall der Bild-QP), Qualitätsstufen,
eine Bewegung von 96 Pixeln je Bild (weit über der alten Grenze von 46) und die Ratenregelung (bleibt unter der Obergrenze
auf der Stufe, steigt nur bei Überlauf, kehrt zurück); `FfmpegDecodeTest` kodiert 24 Bilder mit 5 Slices, 3 Threads, AQ,
Sprung um 140 Pixel, Wiederholbild und wechselnden Presets, lässt **ffmpeg** dekodieren und verlangt bitgleiche Bilder und
„Constrained Baseline“ bei `ffprobe` (übersprungen, wenn ffmpeg fehlt).

**Qualitätsmessung** (`tools/encoder_quality.sh`, nur Analyse, braucht ffmpeg: kodiert Rohbilder oder ein Video mit dem
eigenen Encoder und vergleicht mit ffmpeg `psnr`/`ssim` gegen die Quelle; optional mit nachgebildetem Kamerarauschen).
Testclips je 2 s 1080p60: „Schwenk“ (Bildschirmfoto mit schnellem Schwenk bis 47 Pixel/Bild und Drehung), „scharf“
(Bildschirmfoto, langsamer), „Höhle“ (dunkles Bildschirmfoto mit Schwenk), „Eis“ und „dunkel“ (Ausschnitte aus echten
Aufnahmen, schon einmal kodiert, mit altem Kamerarauschen). Vorher = Encoder vor dieser Änderung bei gleicher Bitrate:

| Clip | Bitrate | vorher PSNR-Y / SSIM | nachher PSNR-Y / SSIM | schlechtestes Bild vorher → nachher |
|---|---|---|---|---|
| Schwenk | 9,7 Mbit/s | 36,6 dB / 0,943 | 39,7 dB / 0,984 | 32,8 → 38,8 dB |
| scharf | 5,7 Mbit/s | 41,3 dB / 0,979 | 41,5 dB / 0,981 | 39,0 → 41,0 dB |
| Höhle | 3,7 Mbit/s | 44,2 dB / 0,982 | 46,4 dB / 0,989 | 41,9 → 46,0 dB |
| Eis | 32 Mbit/s | 39,1 dB / 0,970 | 38,9 dB / 0,970 | 38,4 → 38,5 dB |
| dunkel | 37 Mbit/s | 42,7 dB / 0,980 | 42,4 dB / 0,977 | 40,7 → 40,2 dB |

(Der alte Encoder landete bei „gleicher Bitrate“ jeweils 1–3 % darüber.) Die beiden letzten Clips bestehen fast nur aus eingebranntem Rauschen; dort kostet AQ (gröbere Stufen auf „unruhigen“ Blöcken)
erwartungsgemäß 0,2–0,3 dB PSNR, ohne AQ ist der neue Encoder bei gleicher Bitrate gleich gut oder besser. Entscheidend ist
das Zusammenspiel mit dem neuen Kamerarauschen: dunkle Höhle mit Light-Cam, vorher (altes Rauschen, alter Encoder, feste
32 Mbit/s) 33,0 Mbit/s bei PSNR-Y 35,4 dB / SSIM 0,787 (Rauschen zu Blöcken und Flecken verschmiert, QP 26,5), nachher
(neues Rauschen, Standard) 4,9 Mbit/s bei 44,7 dB / 0,977; ohne Kamerarauschen 3,7 Mbit/s bei 46,4 dB / 0,989.
Tempo gegenüber vorher (4 Threads): rauschfreie Clips gleich schnell, stark verrauschte bis 15 % langsamer (mehr Suchpunkte,
Wellenfront-Deblocking).

Messwerte (Apple M-Klasse, 1080p60, gemessen mit JIT-Anlauf, vor der Qualitätsrunde): harte Testszene mit Drehung und Rauschen bei 40 Mbit/s
6 Threads ≈ 48 fps, 3 Threads ≈ 31–39 fps, 1 Thread ≈ 15 fps; Flugszene aus einem Bildschirmfoto mit Rollen und Schwenk
5 Threads ≈ 64 fps. 720p braucht rund 40 % davon. Hängt der Encoder hinterher, schaltet er stufenweise auf schnellere
Presets und lagert Bilder aus; nach dem Stopp wird der Rest im Hintergrund fertig kodiert.

### Container

`Mp4Writer`: `ftyp` (isom/iso2/avc1/mp41) → `mdat` mit 64-Bit-Größe (beliebig lange Aufnahmen) → `moov` am Ende
(`mvhd`, `tkhd`, `mdhd` mit Zeitbasis `fps · 1000`, `hdlr vide`, `vmhd`, `dref`, `stsd`/`avc1` mit `avcC`, `colr nclx`
BT.709, `pasp`, `stts` lauflängenkodiert, `stss`, `stsc` ein Bild je Chunk, `stsz`, `stco`/`co64`). Geschrieben wird
`…mp4.part`, am Ende atomar umbenannt; beim ersten Aufnahmestart werden alte `.part`- und Auslagerungsdateien
gelöscht. `Mp4Reader` liest Boxen, Tabellen und SPS zurück (Tests und Selbsttest).

## Aufnahme im Spiel (`video.client.record`)

1. **Hook:** `mixin.video.client.GameRendererMixin` direkt nach `applyPostEffects()` ruft zuerst
   `ActionCamRecorder.onRenderedFrame(main)`, danach erst den Videoeffekt (`VideoPass`). Das Hauptbild enthält hier Welt
   und Shaderpack, aber noch keinen Videoeffekt und keine GUI/OSD.
2. **Takt:** `FrameClock` teilt die Aufnahmezeit in Bildplätze (30 oder 60 pro Sekunde). Pro gerendertem Bild wird
   höchstens ein Platz aufgenommen; Pause (Spielpause, Sichtflug, anderes Kamera-Entity) hält die Uhr an. Die
   Bildrate des Spiels wird nicht angefasst: zu viele Bilder werden übersprungen, fehlende im Video wiederholt.
3. **GPU (`ActionCamCapture`):** Pass 1 (`post/action_cam_frame`) zieht das Bild aus dem Hauptziel: 16:9-Ausschnitt,
   Verkleinerung mit 4 Abtastungen (beim Hochrechnen Catmull-Rom), Stabilisierungs-Warp (3×3-Rotation aus `Stabilizer`,
   Zoom), Bildcharakter.
   Pass 2 (`post/action_cam_yuv`) rechnet nach BT.709 (begrenzt) um und packt **I420** in eine RGBA8-Textur
   (Breite/4 × Höhe·1,5): Zeilen 0…H−1 Y, danach je zwei U-Zeilen pro Texturzeile, dann V. Eine einzige
   `copyTextureToBuffer`-Kopie liefert so genau den I420-Puffer (1,5 Byte/Pixel statt 4).
4. **Asynchron:** sechs Auslesepuffer (`MAP_READ | COPY_DST`, vorher vier) im Ring; der Rückruf läuft über
   `RenderSystem.executePendingTasks` erst, wenn der Fence erreicht ist (kein Warten der CPU auf die GPU). Ist kein
   Puffer frei, entfällt das Bild (wird im Video wiederholt) und zählt als Engpass „Grafikkarte“. Gemessen war die
   Auslese-Latenz in allen Läufen 0–1 Bilder; die zwei zusätzlichen Puffer (je 3,1 MB bei 1080p) sind Reserve für
   GPU-gebundene Shaderpacks, bei denen der Treiber bis zu drei Bilder vorausrechnet. Der Rückruf kopiert das gemappte Bild
   mit einer einzigen Bulk-Kopie in einen Pufferblock außerhalb des Heaps (gemessen 0,05–0,3 ms je Bild).
   Beim Start eines FPV-Flugs mit Action-Cam zeichnet `ActionCamCapture#prewarm` beide Pässe einmal in winzige Ziele:
   Apples OpenGL übersetzt einen Shader erst beim ersten Zeichnen, das kostete beim ersten Aufnahmebild einmalig rund
   70 ms (danach unter 2,5 ms).
5. **Warteschlange (`FrameQueue`):** Die Bildpuffer liegen seit 0.5.0 **außerhalb des Java-Heaps** in einer
   `Arena.ofShared()` (FFM, keine nativen Aufrufe) und werden wiederverwendet; freigegeben wird die Arena, sobald der Job
   endet und das letzte ausgeliehene Bild zurück ist. Vorher waren es `byte[]` im Heap (höchstens ein Fünftel davon,
   Standard 512 MB): bei 2–4 GB Heap fehlte dem Spiel bis zu ein Fünftel seines Speichers, und Bilder mit 3,1 MB sind für
   G1 „humongous“. Größe jetzt automatisch: Arbeitsspeicher / 16, 256 MB bis 2 GB (24 GB → 1,5 GB ≈ 480 Bilder 1080p);
   fest einstellbar mit `bufferMb` in `action_cam.json` (64–8192, 0 = automatisch; das alte `memoryMb` entfällt). Wird
   es voll, lagert ein Schreib-Thread die jüngsten Bilder nach `recordings/.spool/*.frames` aus (bis 16 GB bzw. freier
   Platz − 2 GB), direkt aus dem Pufferblock ohne Umkopieren.
   **Gegendruck (`Backpressure`):** Erst wenn Puffer und Auslagerung zu 60 % voll sind (ohne Auslagerung: der Puffer zu
   50 %), nimmt die Aufnahme nur noch jeden zweiten Bildplatz auf, ab 80 % jeden dritten, ab 90 % jeden vierten – immer
   nach der Platznummer, also gleichmäßig; die übrigen Plätze werden im Video wiederholt. Das ersetzt zufällige Lücken am
   Ende eines vollen Puffers durch eine gleichmäßig niedrigere Bildrate.
6. **Hintergrund (`RecordingJob`):** eigener Thread, Encoder nach Wahl (Tab „Action-Cam“ → Encoder: Automatisch =
   Hardware-Encoder, wenn die Prüfung bestanden ist, sonst Propwash). Der eigene Encoder nutzt
   `round(0,6 · Kerne) − 2` Threads, 1…12 (`RecordingResources#encoderThreads`; 8 Kerne → 3, 12 → 5, 16 → 8, 24 und mehr
   → 12; vorher `Kerne/2 − 1`, höchstens 6), mit niedrigster Priorität, einstellbar mit `encoderThreads` in
   `action_cam.json`. Halbpixel-Ebenen des Referenzbilds rechnet er nur noch, wenn das nächste Bild Subpixel-Suche nutzt
   (nicht im schnellsten Preset, das bei großem Rückstand läuft; spart dort rund ein Zehntel der Rechenzeit, bitgleich,
   geprüft mit dem ffmpeg-Dekodiertest). Nach dem Stopp wird die Warteschlange abgearbeitet, das Video bis zum Stopp-Zeitpunkt
   aufgefüllt, die Tonspur auf dieselbe Länge gebracht, `moov` geschrieben und umbenannt. Fortschritt im OSD, in den
   Aufnahme-Hinweisen und in der Aufnahmen-Liste, danach Chat-Link (Klick öffnet die Datei) und Toast. Beim Beenden des
   Spiels wartet ein Shutdown-Hook bis 20 s.
7. **Messwerte und Engpass (`PipelineStats`, `RecordingDiagnosis`):** Jede Aufnahme zählt Renderthread-Kosten (Auftrag
   und Auslesen je Bild), GPU-Latenz, Bildplätze und vom Spiel ausgelassene Plätze, verlorene Bilder nach Ursache
   (Auslese-Ring, Pufferspeicher, Ausdünnen), Encoder-Zeit und Presets, Schreibzeit, Warteschlange, Auslagerung, Heap und
   GC. Einmal je Sekunde bewertet der Rekorder das letzte Fenster: verlorene Bilder durch vollen Puffer → **Zwischenspeicher**
   (Tipp je nach Lage: Platz frei machen oder Encoder entlasten), dabei Auslagerung über 0,8 s je Sekunde → **Datenträger**,
   Auslese-Ring voll → **Grafikkarte**, Rückstand über eine Sekunde und nicht fallend → **Encoder** (nichts geht verloren,
   mit geschätzter Zusatzdauer beim Speichern = Rückstand / Encoder-Tempo), über 5 % vom Spiel ausgelassene Plätze →
   **Spiel**. Der Hinweis erscheint in den Aufnahme-Hinweisen mit genau einem Tipp, Wechsel und der stärkste Engpass der
   Aufnahme stehen im Log, nach jedem Video eine Messzeile mit allen Werten. Vorher meldete die Anzeige „Rechner zu langsam
   – Bilder fehlen“, sobald auch nur ein Bild ausgelagert war – obwohl dabei nichts verloren geht –, und zählte verlorene
   Bilder doppelt.

### Messungen 0.5.0 (vorher/nachher)

Selbsttest „Aufnahme-Leistung“ (`RecordingPerfSelfTests`): Testgelände aus 64 × 64 Säulen mit 1–7 Blöcken
(Steine, Erze, Holz, Laub, Bücherregale), die Drohne mit Heavy-Cam fliegt einen festen Kreis (Radius 22, 11 m/s,
Rollen ±22°, Nicken 20–36°), Stabilisierung „Glätten“, Fenster 1708 × 960, Fabric, Spiel bei 120 FPS. „Vorher“ ist
derselbe Test auf dem Stand vor dieser Änderung (nur Messung eingebaut), beide Läufe direkt nacheinander, während im
zweiten Slot ein anderes Minecraft lief (Last 10–13 auf 12 Kernen) – Encoder-Werte schwanken zwischen Läufen um
± 20 %, die Vergleiche gelten innerhalb eines Paars.

| 1080p60, 10 s | vorher | nachher, Propwash-Encoder | nachher, VideoToolbox |
|---|---|---|---|
| Encoder (Bilder je Sekunde Rechenzeit) | 49,7 | 50,2 | 593 (Pipe, 1,7 ms je Bild) |
| Rückstand höchstens / Speichern nach dem Stopp | 98 Bilder / 2,3 s | 190 Bilder / 2,1 s | 4 Bilder / 0,2 s |
| Renderthread je Bild | 0,35 ms | 0,44 ms | 0,33 ms |
| verlorene Bilder | 0 | 0 | 0 |
| Heap-Spitze / GC während der Aufnahme | 1034 MB / 10× 38 ms | 892 MB / 4× 20 ms | 915 MB / 1× 7 ms |

| 1080p60, 8 s, Encoder auf 1 Thread (zu langsamer Rechner) | vorher | nachher |
|---|---|---|
| Hinweis | „Rechner zu langsam – Bilder fehlen“ nach 3,7 s | „Encoder schafft 19 von 60 Bildern/s – nichts geht verloren“, „Speichern dauert danach ca. 4 s länger“, Tipp |
| verlorene Bilder | 0 | 0 |
| ausgelagert auf den Datenträger | 187 Bilder (580 MB) | 0 (Puffer 967 MB außerhalb des Heaps) |
| Encoder / Speichern nach dem Stopp | 12,5 B/s / 30,8 s | 19,4 B/s / 17,1 s |
| Heap-Spitze / GC | 1281 MB / 10× 84 ms | 903 MB / 5× 56 ms |

Nachprüfung mit 720p30 und dem schlimmsten Bild (zweites Paar, derselbe Selbsttest, „vorher“ = Stand `84d0810a` mit
nur der Messung, „nachher“ = Release-Stand mit format-gerechtem Tipp; beide direkt nacheinander, im zweiten Slot lief
dabei die Bando-Prüfung eines fremden Seeds mit Weltgenerierung). „Schlimmstes Bild“ ist der längste einzelne Auftrag
bzw. das längste Auslesen im Renderthread während der Aufnahme; der Selbsttest verlangt seitdem höchstens 25 ms
(`MAX_RENDER_THREAD_STALL_MS`) zusätzlich zum Mittel unter 2 ms.

| 720p30, 8 s | vorher | nachher, Propwash-Encoder |
|---|---|---|
| Renderthread je Bild (Auftrag + Auslesen) | 0,334 ms (0,239 + 0,095) | 0,425 ms (0,303 + 0,122) |
| schlimmstes Bild: Auftrag / Auslesen | 3,07 ms / 9,74 ms | 2,06 ms / 13,36 ms |
| längste Bildzeit des Spiels | 24,3 ms | 19,8 ms |
| verlorene Bilder / vom Spiel ausgelassen | 0 / 0 | 0 / 0 |
| Encoder / Speichern nach dem Stopp | 118,5 B/s / 0,2 s | 77,7 B/s / 0,2 s |
| Heap-Spitze / GC | 731 MB / 4× 15 ms | 650 MB / 2× 6 ms |

| 1080p60, 10 s | vorher | nachher, Propwash-Encoder | nachher, VideoToolbox |
|---|---|---|---|
| Renderthread je Bild (Auftrag + Auslesen) | 0,371 ms (0,245 + 0,125) | 0,341 ms (0,237 + 0,104) | 0,277 ms (0,207 + 0,070) |
| schlimmstes Bild: Auftrag / Auslesen | 0,89 ms / **32,59 ms** | 1,15 ms / 0,95 ms | 1,01 ms / 0,80 ms |
| längste Bildzeit des Spiels | 41,4 ms | 16,3 ms | 15,1 ms |
| verlorene Bilder / vom Spiel ausgelassen | 0 / 1 | 0 / 0 | 0 / 0 |
| Encoder / Speichern nach dem Stopp | 73,0 B/s / 0,2 s | 54,1 B/s / 1,3 s | 470,5 B/s / 0,2 s |
| Heap-Spitze / GC | 824 MB / 6× 50 ms | 710 MB / 5× 20 ms | 737 MB / 1× 3 ms |

Lesart: Das Mittel im Renderthread liegt vorher wie nachher bei 0,3–0,45 ms; die Unterschiede von ±0,1 ms wechseln
zwischen den Paaren die Richtung (erstes Paar 1080p60 0,35 → 0,44 ms, dieses Paar 0,37 → 0,34 ms) und sind
Messrauschen. Der Unterschied liegt im schlimmsten Bild: vorher hing das Auslesen bei 1080p60 einmal 32,6 ms,
nachher höchstens 1,2 ms; die 13,4 ms bei 720p30 nachher fielen in die erste Aufnahme nach dem Start (im vollen Lauf,
nach anderen Aufnahmen, höchstens 3,8 ms). Mit der alten Grenze nur für das Mittel wäre der Hänger von vorher nicht aufgefallen; mit 25 ms
schlägt der Test bei ihm fehl und lässt dem Ausreißer der ersten Aufnahme knapp den doppelten Spielraum. Das
Encoder-Tempo hängt hier an der Weltgenerierung im zweiten Slot (vorher ruhigere Phase, nachher Chunk-Erzeugung) und
taugt in diesem Paar nicht zum Vergleich. Unter voller Last (voller Fabric-Lauf, zweites Minecraft und JUnit
gleichzeitig) maß die Nachprüfung 720p30 0,365 ms (schlimmster Auftrag 3,80 ms), 1080p60 0,530 ms, VideoToolbox
0,308 ms, jeweils ohne verlorenes Bild.

Weitere Läufe nachher (ruhigere Maschine): Propwash-Encoder 1080p60 51,8 B/s ohne Verlust, 720p30 80,5 B/s; VideoToolbox
451–618 B/s; NeoForge gleich (51,6 / 618 B/s, 0 verloren). Mit Iris und Complementary Reimagined (Fenster 1708 × 960):
VideoToolbox 422 B/s, Spiel 114 FPS, 0 verloren; der Propwash-Encoder fiel bei voll ausgelasteter Maschine auf 40 B/s,
die Anzeige meldete den Encoder-Engpass, verloren ging nichts.

Encoder allein (`EncoderBench`, 4 s echter Flug mit Shaderpack als I420, 1080p60, Standard 32,3 Mbit/s, ohne Spiel):
Propwash-Encoder 1 Thread 13,3 B/s, 3 → 32,0, 5 → 50,6, 8 → 62,2, 11 → 68,3 (Preset „schnell“ mit 5 Threads 64,6,
„schnellste“ 71,0); VideoToolbox über ffmpeg dieselben 240 Bilder in 1,2 s (≈ 200 B/s) bei 0,3 s Prozessorzeit
insgesamt – der eigene Encoder braucht dafür rund 18 s Prozessorzeit. Auf einem Laptop mit 32 logischen Kernen
(wie im Bericht aus der Community) nimmt der eigene Encoder jetzt 12 statt 6 Threads.

### Kameras

| | Light (nackte FPV-Cam) | Heavy (Action-Cam im Gehäuse) |
|---|---|---|
| Aufnahme | 720p/1080p, 30/60 fps | 720p/1080p/1440p, 30/60 fps |
| Stabilisierung | keine (rohes Bild) | Wackler glätten (Zoom 1,15) oder Glätten + Horizont (Zoom 1,6), je bei Stärke 50 % |
| Bild | weicher (Schärfe −0,35), Rauschen in sehr dunklen Bereichen ±1,8 Stufen | leicht nachgeschärft (+0,18), Rauschen ±1 Stufe |
| Strom | 4 W aus dem Flugakku (Flugmodell `P_aux`), stoppt bei leerem Flugakku | eigener Akku 6,6 Wh: 4,4 W (bis 1080p) … 6,6 W (1440p) → ca. 90 / 60 min |

Bildcharakter aus `ActionCamSpec` (`video.record.CameraLook`): Schärfe = (Bildqualität − 0,9) · 3,5, Rauschamplitude =
Rauschen bei wenig Licht · 0,025 (Light 0,014, Heavy 0,0075 – vorher · 0,12, also fast fünfmal so stark), gewichtet mit
`1 − smoothstep(0; 0,2; Luma)` (vorher bis Luma 0,55, also bis in die Mitteltöne), Farbrauschen nur noch 10 % statt 45 % des
Helligkeitsrauschens, jedes Bild neu. Schalter **Kamerarauschen** im Tab (Standard an) setzt die Amplitude auf 0.
Das alte Rauschen war die Hauptursache der „rauschigen“ Videos: zufälliges Rauschen je Pixel und Bild lässt sich nicht
vorhersagen, verschlang in dunklen Szenen über 80 % der Bitrate, trieb den QP hoch und wurde dabei zu wandernden Flecken und
Blöcken verschmiert. Das neue Rauschen liegt unter der Quantisierungsstufe mittlerer Qualität, bleibt als feines Korn nur in
sehr dunklen Bildteilen und kostet bei Standard rund 30 % mehr Bits als gar kein Rauschen.

**Diagnose der Aufnahmen vom 24.09.** (`propwash_2026-09-24_16-51-36.mp4` bei 19,2 s, `…_16-52-34.mp4` bei 33,2 s,
Iris-Shaderpack): Die Bildkette war in Ordnung (richtiges Bild vor Videoeffekt und OSD, BT.709 begrenzt, 2×2-Chroma-Mittel,
kein Skalierungsfehler). Die Artefakte kamen aus drei Quellen: (1) Kamerarauschen der Light-Cam in dunklen Bildteilen –
der Encoder brauchte die ganze Bitrate dafür (QP 26–30 statt 16–20 in hellen Szenen) und zerlegte es in Flecken;
(2) waagerechte Kanten an jeder Slice-Grenze (Deblocking nur innerhalb der Slices); (3) fester Bitrate-Topf – in schnellen
oder dunklen Szenen stieg der QP bis 35, in ruhigen fiel er auf 16 (große Qualitätssprünge).

### Stabilisierung (`video.record.Stabilizer`, reines Java)

Aus der tatsächlichen Kameralage `q` (Minecraft `Camera#rotation`, Kamera → Welt) wird eine geglättete Lage `s`
nachgeführt (`slerp` mit `1 − e^(−dt/τ)`). Mit Horizont wird aus `s` die Lage mit gleicher Blickrichtung und
waagerechtem Horizont gebaut. Die Ausgabe-Kamera `o` blickt mit kleinerem Sichtfeld (Zoom) aus derselben Position;
der Shader bildet jeden Ausgabestrahl mit `M = R(q⁻¹ · o)` auf das gerenderte Bild ab (reine Drehung = Homographie).
Damit nie Ränder sichtbar werden, prüft eine Binärsuche die vier Ecken und vier Kantenmitten und zieht `o` notfalls
Richtung `q` (die Korrektur „schlägt an“); die Glättung wird dabei mitgezogen, damit sie nach schnellen Manövern nicht
hinterherschwingt.

**Stärke (0.5.0, Tab „Action-Cam“, 0–100 % in 10er-Schritten, Standard 50 % = genau das Verhalten davor):**
τ = 0,04 s + 0,2 s · Stärke (50 % → 0,14 s), Zoom „Glätten“ = 1 + 0,3 · Stärke (50 % → 1,15, 100 % → 1,3, 0 % → 1,0),
Zoom „Glätten + Horizont“ = 1,25 + 0,7 · Stärke (50 % → 1,6). Der Horizont hält, solange cos θ + (16/9) · sin θ ≤ Zoom
(Ausgabebild um θ gedreht passt ins Bild): 0 % ≈ ±8°, 50 % ≈ ±22°, 100 % ≈ ±43°.

**Warum zur Aufnahmezeit und nicht als Export in der Aufnahmen-Liste:** Ein Export müsste das fertige H.264-Video
dekodieren, verzerren und neu kodieren – eine zweite verlustbehaftete Kodierung (Generationsverlust), dazu ein
H.264-Decoder, den Propwash nicht hat. Zur Aufnahmezeit wird das Spielbild in voller Fensterauflösung genau einmal
abgetastet und einmal kodiert, und die Kameralage ist für jedes Bild exakt bekannt; ein zweites Rendern der Welt
braucht es nicht. Schärfe kostet die Stabilisierung nur, wenn der Ausschnitt mehr Ausgabepixel braucht, als das Fenster
liefert (Fensterbreite / Zoom < Videobreite): Ein 2560er-Fenster gibt einem 1080p-Video bis Zoom 1,33 volle Schärfe.
Der Tab zeigt das für das aktuelle Fenster an („ohne Schärfeverlust“ bzw. „um 15 % hochgerechnet“). Muss hochgerechnet
werden, tastet der Shader jetzt mit Catmull-Rom (neun bilineare Abtastungen) statt mit vier gemittelten Abtastungen ab;
im CPU-Modell (`docs/screenshots`-Bilder flächengemittelt verkleinert und wieder vergrößert) +1,0 bis +1,5 dB PSNR bei
×1,29 und ×1,6 gegenüber vorher (z. B. 33,2 → 34,6 dB und 32,0 → 33,0 dB).

Die Light-Cam bleibt ohne Stabilisierung: Sie ist die nackte FPV-Kamera, die stabilisierte Kamera ist die Heavy.

### Strom der Heavy

- Datenkomponente `propwash:camera_charge` (0…1) an Zubehör-Item und Drohnen-Item; Entity-Feld (synchronisiert und
  gespeichert, `CameraCharge`), `loadFromStack`/`toStack`, Werkbank überträgt den Stand beim Bauen und Zerlegen.
- Beim Aufnehmen rechnet der Client ab (`CamPower.drain`, nur echte Aufnahmezeit) und meldet den Stand alle 2 s und beim
  Stopp mit `CameraChargeC2S` (Server: nur Pilot, nur sinkend, nur Drohnen mit Heavy).
- **Laden:** abgestellt (kein Pilot, entschärft) mit eingesetztem Flugakku lädt die Heavy per USB aus dem Flugakku:
  voll in 120 s (spielgerafft wie das Ladegerät), Wirkungsgrad 85 %, der Flugakku bleibt über 15 % (`CamPower.usbCharge`,
  `drone.CameraCharging`). Beispiel 6S 1300: eine volle Kamera-Ladung kostet ≈ 26 % des Flugakkus.

## Ton (`video.record.aac`, `video.record.AudioLane`, `audio.client.MotorAudioTap`)

Auf Wunsch (Tab „Action-Cam“, **Ton aufnehmen**, Standard an) bekommt das MP4 eine AAC-LC-Tonspur mit dem Motorgeräusch der
aufnehmenden Drohne – wie das Mikrofon einer echten Action-Cam am Rahmen. Andere Spielgeräusche (Wind, Blöcke, Musik) sind nicht dabei.

- **Quelle:** Der eigene Motor-Synthesizer (`audio.client.MotorStream`, 48 kHz mono, Stücke zu 720 Samples) meldet jedes erzeugte
  Stück vor der Raumwirkung an `MotorAudioTap`, zusammen mit der Entity-Id der Drohne. Der Rekorder hört nur seine Drohne ab
  (Ringpuffer 2 s, Audio-Pump-Thread → Client-Tick). Ist der Motorton stumm (Lautstärke 0, Sound-Modus ohne Synthesizer) oder die
  Drohne außer Hörweite, füllt der Rekorder mit Stille auf – die Spur bleibt immer so lang wie das Bild.
- **Takt:** Je Client-Tick kommen die neuen Samples in den Job, solange die Aufnahme nicht pausiert (in der Pause werden sie
  verworfen). Weicht die Summe um mehr als 120 ms von der Aufnahmezeit (`FrameClock`, ohne Pausen) ab, wird mit Stille aufgefüllt
  bzw. übersprungen. `AudioLane` im Hintergrund-Job verwirft den Ton vor dem ersten Bild (`firstSlot`), kodiert laufend und füllt beim
  Abschluss auf genau `Bildplätze × 48000 / fps` Samples auf.
- **Encoder (eigener Code):** AAC-LC, nur lange Blöcke mit Sinusfenster, MDCT über eine 512-Punkte-FFT (gegen die Definition
  getestet), Quantisierung mit Skalenfaktor je Band aus dem Band-RMS plus einem globalen Qualitätswert (konstanter Rauschabstand je
  Band, Bänder unter ≈ −80 dBFS entfallen, Grenzfrequenz 17 kHz), Codebuchwahl je Band per dynamischer Programmierung über die
  Abschnittskosten, Escape-Codes bis ±8191, Ratenregelung auf 160 kbit/s (VBR, höchstens 6144 Bit je Bild). Tabellen aus ISO/IEC
  14496-3 (Huffman-Codebücher 1–11, Skalenfaktor-Code, Bandgrenzen 48 kHz); beim Übernehmen auf Präfixfreiheit und Kraft-Summe 1
  geprüft.
- **Container:** zweite Spur `soun` mit `mp4a`/`esds` (AudioSpecificConfig `0x11 0x88`), Edit-Liste mit Vorlauf 1024 Samples (das
  erste AAC-Bild ist Einschwingen) und Länge = Bildlänge, `sgpd`/`sbgp` mit `roll` −1. Ohne die Roll-Gruppe schneidet Apples
  AVFoundation 2112 statt 1024 Samples ab (Ton 23 ms zu früh), mit ihr stimmt die Lage auf das Sample.
- **Prüfung:** JUnit dekodiert jedes Bild mit einem eigenen Referenz-Decoder (Huffman, Dequantisierung, IMDCT nach Norm) und prüft
  Rauschabstand, Escape-Codes, Stille, Bitgrenzen; `Mp4AudioTrackTest` prüft Spuren, Edit-Liste und Längen. Während der Entwicklung:
  **ffmpeg** dekodiert fehlerfrei, genau so viele Samples wie aufgenommen, Versatz 0, Pegel 0,98, Rauschabstand ≈ 23 dB bei einem
  motorähnlichen Signal (Grundton + Obertöne + Rauschen) und ≈ 35 dB bei reinen Tönen; **Apple AVFoundation** (`AVAssetReader`)
  spielt die Datei ab, liefert dieselbe Sample-Zahl mit Versatz 0; `afinfo` erkennt AAC 48 kHz mono.

## Bedienung

- **Taste** „Action-Cam: Aufnahme starten/stoppen“ (Standard **F9**, kollidiert mit keiner Vanilla- oder Propwash-Taste) im FPV-Flug;
  frei belegbar im Konfigurator-Tab „Tasten“ unter „Funktionen“.
- **Funke:** Funktion **Aufnahme** im Modi-Tab (`RecordTrigger`): ein **Schalter** nimmt auf, solange er an ist (steht er beim
  Flugstart schon an, startet die Aufnahme sofort); ein **Taster** startet und stoppt bei jedem Druck, auch als Umschalter belegt
  (Gamepads belegen Taster standardmäßig so). Stoppt die Aufnahme von selbst (Akku leer, Flug beendet), startet der nächste
  Tasterdruck neu – kein doppeltes Drücken.
- **Beim Scharfschalten:** Start beim Arm, Stopp beim Disarm (nur für so gestartete Aufnahmen).
- Aufnahme nur im FPV-Bild; im Sichtflug oder bei Spielpause pausiert sie.
- **Meldungen** als Propwash-Toasts (gleicher Token, ersetzen sich): „● Aufnahme läuft“ mit Kamera, Format und Stabilisierung,
  „Aufnahme gestoppt – Video wird gespeichert …“ (Akku leer in Warnfarbe, Fehler als „Aufnahme abgebrochen“), „Aufnahme nicht
  möglich“ mit Grund, „Video gespeichert“ mit Dauer · Format · Größe; zusätzlich ein Chat-Link, der die Datei öffnet.
- **REC-Anzeige im OSD:** OSD-Element „Aufnahme (REC)“ oben rechts in der ersten Zeile innerhalb des Sicherheitsrands (in allen
  Vorlagen an, im Tab „OSD“ aus- oder einblendbar und verschiebbar; gespeicherte OSD-Einstellungen der Version 2 rücken REC und die
  darunter verschobenen Elemente nach, solange sie noch auf ihrem alten Platz stehen): blinkender roter Punkt und Laufzeit `m:ss` (wie in der Aufnahmen-Liste) in Warnfarbe, pausiert zwei Balken in Warnfarbe, nach dem Stopp `SPEICHERN nn%`. Es ist wie
  das ganze OSD nie im Video.
- **Aufnahme-Hinweise** oben in der Mitte (Tab-Schalter, Standard an): nur wenn es etwas zu melden gibt – Pause (z. B. im Sichtflug),
  Kamera-Akku unter 30 %, der Engpass mit einem Tipp (Spiel, Grafikkarte, Encoder mit Zusatzdauer beim Speichern,
  Datenträger, Zwischenspeicher; Zeilen umbrochen auf höchstens 250 GUI-Pixel, damit das OSD frei bleibt) – und außerhalb
  des FPV der Speicherfortschritt. Bild: `docs/screenshots/action_cam_bottleneck_hud.png`.
- **Konfigurator-Tab „Action-Cam“** (Order 530, scrollt): Auflösung, Bildrate, Qualität (volle Breite, „Standard · ≈ 110 MB/min“ für das
  eingestellte Format, Tooltip mit Obergrenze), Encoder (Automatisch / Propwash (CPU)), Stabilisierung, Stärke (mit
  Ausschnitt-Faktor), Ton aufnehmen, Beim Scharfschalten, Aufnahme-Hinweise, Kamerarauschen; „Aufnahmen ansehen …“ und
  „Ordner öffnen“; Abschnitt **„Schneller aufnehmen mit ffmpeg“** (unten); Status der aktuellen Drohne, Encoder der
  nächsten Aufnahme (Hardware-Encoder über ffmpeg oder Propwash mit Thread-Zahl und Grund), Schärfe des
  Stabilisierungs-Ausschnitts beim aktuellen Fenster, Kamera-Akku, Speicherfortschritt, letzter Fehler; Hinweis auf
  Taste, Funke und OSD-Element. Bild: `docs/screenshots/configurator_action_cam.png`.
- **Abschnitt „Schneller aufnehmen mit ffmpeg“** – immer sichtbar, auch ohne Drohne und bei Encoder „Propwash (CPU)“:
  - Statuszeile in Farbe: „ffmpeg 9.0.2 gefunden (Pfad) – Hardware-Encoder: VideoToolbox (Test 1080p60: 236 Bilder/s)“
    (grün; nach „Testen“ alle funktionierenden Encoder des Rechners), „ffmpeg nicht gefunden – installiere es für
    schnelleres Aufnehmen (Knopf ‚Anleitung‘)“, „… gefunden, aber kein Hardware-Encoder funktioniert (…)“, „ffmpeg unter
    … startet nicht (…)“, „Hardware-Encoder ausgefallen (…)“; Zusatzzeilen bei Encoder „Propwash (CPU)“, bei falschem
    eigenem Pfad und bei noch ungeprüfter Eingabe.
  - Feld **ffmpeg-Pfad** (leer = automatisch; Datei oder Ordner; eine auf das Spielfenster gezogene Datei landet im Feld
    und wird sofort geprüft – `ConfigTab#filesDropped`, vom Konfigurator aus `Screen#onFilesDrop` weitergereicht).
    Tippen prüft nichts: erst **Testen** oder `Enter` (sonst startete jeder Tastendruck eine Probekodierung).
  - **Testen** sucht neu und prüft alle Encoder; **Anleitung** öffnet `propwashfpv.com/docs/guide/#video-ffmpeg`
    (Vorlage des Textes: `docs/recording-ffmpeg.md`). War beim Öffnen des Tabs kein ffmpeg bekannt, sucht er einmal von
    selbst neu – wer ffmpeg bei laufendem Spiel installiert, muss nichts drücken.
  - Kein Dateidialog: Minecraft 26.3 liefert keinen mit (tinyfd ist nicht mehr dabei), der von SDL3
    (`SDL_ShowOpenFileDialog`) ruft je nach System aus einem anderen Thread zurück und lässt sich im stummen Selbsttest
    nicht bedienen. Textfeld, Einfügen und Ablegen einer Datei decken den Fall ab und sind prüfbar.
  Bilder: `configurator_action_cam_ffmpeg.png`, `…_ffmpeg_missing.png`, `…_ffmpeg_de.png`.
- **Encoder in der Aufnahme-Anzeige** (`RecordingHud`): In den ersten sechs Sekunden jeder Aufnahme steht dort
  „Encoder: VideoToolbox (Hardware, über ffmpeg)“ oder „Encoder: Propwash (CPU)“ mit dem Grund (ffmpeg nicht gefunden,
  Prüfung lief noch, kein nutzbarer Hardware-Encoder); die Kopfzeile jedes Hinweises nennt ihn neben dem Format
  („1080p60 · VideoToolbox“, „1080p60 · CPU“). Bewusst nicht dauerhaft: Das OSD-Element „REC“ bleibt die ständige
  Anzeige, ein festes Feld in der Bildmitte würde beim Fliegen stören. Bild: `docs/screenshots/action_cam_encoder_hud.png`.
- **Flugschule:** Szene „Action-Cam: Videos aufnehmen“ (`action_cam`), geöffnet auch über beide Action-Cam-Items (Taste M).

## Aufnahmen-Liste und Vorschaubilder

- `RecordingsScreen` (Theme-Fenster, aus dem Tab): alle `*.mp4` im Aufnahme-Ordner, neueste zuerst, je Zeile Vorschaubild mit Dauer,
  Datum und Uhrzeit (aus dem Dateinamen, sonst Änderungszeit), Dauer · Format (z. B. 1080p60) · Größe (· mit Ton), Dateiname;
  unvollständige Dateien als „Datei unvollständig oder beschädigt“. Darüber die laufende Aufnahme und jedes Video, das gerade
  gespeichert wird, mit Fortschrittsbalken. Knöpfe **Ordner öffnen**, **Löschen** (zweiter Klick innerhalb von 3 s), **▶ Abspielen**
  (öffnet den Systemplayer über SDL), **Fertig**. Die Liste liest die Dateien im Hintergrund (`RecordingLibrary`, Metadaten aus
  `moov`, zwischengespeichert nach Größe und Änderungszeit) und aktualisiert sich alle 2 s und nach jedem gespeicherten Video.
- **Vorschaubilder:** Der Rekorder rechnet das erste aufgenommene Bild und das Bild nach 1 s auf der CPU aus I420 in 256×144 um
  (`Thumbnail`, BT.709) und schreibt `recordings/.thumbs/<name>.png` im IO-Pool; bekommt das Video beim Speichern einen anderen Namen,
  wandert das Vorschaubild mit, scheitert das Speichern, wird es gelöscht. Verwaiste Vorschaubilder räumt der erste Aufnahmestart weg.

## Grenzen

- Ton nur vom eigenen Motor-Synthesizer der aufnehmenden Drohne, mono; keine Umgebungsgeräusche, keine anderen Drohnen.
- Aufgenommen wird das Bild der FPV-Kamera (ihr Sichtfeld und Uptilt), nicht ein zweiter Blickwinkel der Action-Cam;
  ein zweites Rendern der Welt wäre zu teuer. Sichtfeld und Winkel der Action-Cam wirken über Ausschnitt und
  Stabilisierung.
- Ist das Fenster kleiner als die Aufnahmeauflösung (bzw. als Aufnahmeauflösung × Zoom der Stabilisierung), wird
  hochgerechnet (Catmull-Rom).
- Der Hardware-Encoder braucht ein installiertes ffmpeg. Geprüft ist der Weg auf macOS mit VideoToolbox (JUnit mit echter
  Aufnahme und `ffprobe`, Selbsttest Fabric und NeoForge, mit und ohne Iris, auch mit dem `PATH` eines aus dem Dock
  gestarteten Programms); NVENC, Quick Sync, AMF, VA-API und Media Foundation laufen über denselben Code mit eigenen
  Argumenten, sind aber nur durch die Probekodierung beim Spieler abgesichert. Die Suchorte für Windows und Linux sind
  mit nachgebauten Ordnern in JUnit geprüft, nicht auf echten Installationen.
- Läuft der Launcher als Flatpak oder Snap, sieht das Spiel das ffmpeg des Systems nicht; dann hilft nur ein statisches
  ffmpeg in `propwash/ffmpeg/` oder der eigene Pfad.
- Die Liste zeigt nur Videos direkt im Aufnahme-Ordner; „Abspielen“ braucht einen Player, der MP4 (H.264 + AAC) öffnet – das kann
  jedes aktuelle System.
