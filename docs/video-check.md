# Schwarzes FPV-Bild auf Intel-Grafik, Bildprüfung und Ersatz-Shader (0.6.0)

Für Entwickler und Support. Zwei Meldungen aus dem Discord (18. und 23.10.2026): Auf Intel UHD Graphics 620 (Treiber
3.3.0 Build 31.0.101.2134, Dell Latitude 5500) und Intel HD 520 (HP EliteBook 840 G3) blieb das Bild in der Brille
schwarz, das OSD war zu sehen. Propwash 0.5.2, Fabric, Kreativ-Brille. Umgehung eines Spielers: Bildrauschen der
Kreativ-Brille auf einen Wert über 0 stellen, dann ist das Bild da. Der zweite Spieler: „vor einer Woche lief es noch“
(0.5.2 erschien am 17.10.).

## 1. Ursache

- 0.5.2 brachte das Fisheye (`lensStrength`, Standard 80 %). Ist das Bild sauber (Kreativ-Brille, Rauschen 0, volles
  Signal), braucht es keinen Effekt-Durchgang (`EffectParams#needsPass` ist falsch). `VideoPass` nimmt dann den Weg
  „nur Objektiv“: das Hauptbild wird mit `RenderTarget#copyColorFrom` in ein Zwischenbild kopiert, danach zeichnet die
  Entzerrung (`fpv_lens`) das Zwischenbild zurück ins Hauptbild.
- `copyColorFrom` ist in 26.3 `GlCommandEncoder#copyTextureToTexture`: ein `glBlitNamedFramebuffer` über die beiden
  Hilfs-Framebuffer des Encoders, sobald der Treiber `GL_ARB_direct_state_access` meldet (der Intel-Treiber aus dem Log
  tut das, macOS nicht). Vanilla 26.3 ruft `copyColorFrom` und `copyTextureToTexture` nirgends auf; der Weg ist also
  durch das Spiel selbst auf keinem Treiber abgedeckt. Anders als der einzige Vanilla-Blit (`presentTexture`) setzt er
  vorher weder Farb- noch Tiefenmaske.
- Mit Rauschen > 0 läuft der Effekt-Durchgang. Mit Fisheye entzerrt er dann direkt vom Hauptbild ins Zwischenbild
  (Shader) und zeichnet es mit `video_feed` zurück: kein Blit. Genau dieser Unterschied passt zum Fehlerbild: Rauschen 0
  schwarz, Rauschen > 0 sichtbar.
- Ein Blit, der still nichts schreibt (kein GL-Fehler: sonst hätte `copyTextureToTexture` geworfen und das Fisheye wäre
  mit „Fisheye-Objektiv: Fehler im Render-Pass“ abgeschaltet worden; das Log zeigt auch keine KHR_debug-Meldung, die
  bei `glDebugVerbosity` 1 jede Meldung hoher Stufe ausgibt), lässt das beim Anlegen schwarz gelöschte Zwischenbild
  stehen; die Entzerrung zeichnet es über das Spielbild. Der Selbsttest stellt das nach (§4).
- Nicht die Ursache: `VideoOverlay` und `BreakupPreview` lassen bei Rauschen ≤ 0 den Rausch-Durchgang samt
  `ensureNoiseTexture()` aus. Beide zeichnen über das GUI (Ersatz-Overlay bzw. Vorschau im Video-Tab) und können das
  Bild nur abdecken, wenn sie etwas zeichnen. Bei 0 zeichnen sie nichts.
- Ungebundene Sampler: `video_feed` bindet `InSampler` und `HistorySampler` in jedem Bild und jedem Weg. Fehlt eine
  Bindung, wirft das Frontend nur bei strenger Prüfung (`FrontendGpuDevice.STRICT_VALIDATION`, nur in der
  Entwicklungsumgebung, unter NeoForge-Dev an, unter Fabric-Dev aus) „Missing uniform …“. Im fertigen Spiel liest der
  Shader dann, was zuletzt an der Textur-Einheit hing. Der Selbsttest erzwingt das (§4).
- Shader-Werte: Im Video-Shader war `smoothstep(1.0, 0.86, x)` laut GLSL undefiniert (erste Kante größer als die
  zweite). Ersetzt durch `smoothstep(0.0, 1.0 - 0.86, 1.0 - x)`, auf Treibern mit der üblichen Formel bitgleich.
  Sonst keine undefinierten Werte: alle lokalen Variablen gesetzt, Teiler mindestens 1 bzw. 1e-3 (Bildanteile > 0),
  kein `pow`, `log` oder `normalize`.
- GL-Zustand: Alle Durchgänge laufen jetzt als Render-Pass. Der Pass setzt Framebuffer, Viewport, Scissor und
  Draw-Buffer, die Pipeline Programm, Tiefe, Culling, Farbmaske, Blending und Polygon-Modus, jede Zeichnung Textur-
  Einheit, Textur und Sampler. Zustand aus dem vorigen Bild wirkt also nicht mehr; der Blit war der einzige Schritt
  ohne diese Vorbereitung.

## 2. Behebung

- `VideoPass` kopiert nie mehr per Blit. Die Kopie ist eine Zeichnung mit dem Ersatz-Shader
  `post/video_feed_simple.fsh` (Pipeline `propwash:pipeline/video_feed_simple`, nur `InSampler`, `NEAREST`):
  `fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0)`. Bei gleicher Größe ist das Ergebnis bitgleich zum Blit
  (Selbsttest: 0 von 2 073 600 Bildpunkten anders bei 1920×1080). Nur wenn die Pipeline fehlt, bleibt der Blit.
- Kosten: gleich. Selbsttest mit 20 zusätzlichen Kopien je Bild bei 1920×1080 (Apple M4 Pro): Blit 8,738 ms, Shader-Kopie
  8,633 ms je Bild; NeoForge bei 1708×960 8,432 gegen 8,322 ms.

## 3. Bildprüfung, Einfach und Einstellung

- Einmal beim ersten FPV-Bild und nach jedem Neuladen der Shader (Ressourcen, Ressourcenpaket; erkannt am neuen
  kompilierten Pipeline-Objekt) schickt `VideoSelfCheck` ein Testbild (96×54, vier Farbfelder, `VideoCheck`) durch
  Kopie, Fisheye (80 %), `video_feed` sauber und mit 50 % Rauschen sowie durch den Ersatz-Shader und liest vier
  Messpunkte je Bild zurück (`copyTextureToBuffer`, nach dem Fence). Kontrolle ist das Testbild selbst.
- Auswertung (`VideoCheck#evaluate`): Kontrolle falsch → nicht auswertbar (Rücklesen kaputt, nichts ändern); voller
  Durchgang richtig (Toleranz 40 je Kanal) und mit Rauschen hell genug (mindestens 35 % der erwarteten Helligkeit,
  höchstens die Hälfte der Punkte dunkel) → bestanden; sonst Ersatz-Shader richtig → „voller Weg kaputt“, sonst „alles
  kaputt“. NaN landet in RGBA8 als 0 oder Unsinn und fällt so als schwarz oder falsch auf.
- Stufen (`VideoConfig.EffectRenderer#level`): **Automatisch** (Standard) → voll, bei „voller Weg kaputt“ Einfach, bei
  „alles kaputt“ ohne Shader. **Voll** → immer voll (die Prüfung schreibt nur ins Log). **Einfach** (gespeichert weiter
  als `OVERLAY`, alte Konfigurationen behalten ihre Wahl) → Einfach, bei „alles kaputt“ ohne Shader.
- Einfach: kein `video_feed`, kein Fisheye (`lensReady` falsch, die Kamera rendert flach), Störungen über das
  Ersatz-Overlay (`VideoOverlay`), LED-Ausleuchtung über die Overlay-Aufhellung. Gehaltene Bilder einer digitalen
  Verbindung zeigt der Ersatz-Shader (beim neuen Bild Kopie, sonst das gehaltene Bild zurück). Ohne Shader: wie Einfach,
  aber keine gehaltenen Bilder. Mit aktivem Shaderpack bleibt es wie bisher beim Ersatz-Overlay; Einfach zeichnet dort
  auch keine gehaltenen Bilder.
- Meldung: bei Automatisch einmal je Spielsitzung im Chat „Videoeffekte auf dieser Grafikkarte vereinfacht …“
  (`propwash.video.simplified.chat`) und im Log `Videoeffekte auf dieser Grafikkarte vereinfacht (einfacher Ersatz-Shader|ohne Shader): <Messwerte> (Grafik: …)`.
  Bestanden: `Video-Bildprüfung bestanden: …`; nicht auswertbar: `Video-Bildprüfung nicht auswertbar, …`. Jede Zeile
  nennt die Messpunkte (`x,y=r/g/b`), den KHR_debug-Stand (`GL_KHR_debug an, Stufe <glDebugVerbosity>` oder „nicht
  verfügbar“) und neue OpenGL-Meldungen des Geräts aus der Prüfung.
- Video-Tab: Knopf „Videoeffekte: Automatisch/Voll/Einfach“ mit Tooltip je Wert. Hat die Prüfung vereinfacht, steht
  unter der Vorschau „Videoeffekte auf dieser Grafikkarte vereinfacht …“, und „Shader erneut versuchen“ erscheint und
  prüft neu (`VideoPass#retryAll`). Der Ausgang der Prüfung überdauert den Weltwechsel.
- Mehr Treiber-Meldungen für eine Fehlersuche: in `options.txt` `glDebugVerbosity:4` (Vanilla schaltet damit alle
  KHR_debug-Stufen an); die Prüfung schreibt die neuen Meldungen in ihre Logzeile.

## 4. Tests

- JUnit: `video.VideoCheckTest` (Muster, Messpunkte im Bildkreis und im eigenen Farbfeld auch nach der Entzerrung,
  bestanden, Toleranz, schwarz, falsche Farben, alles schwarz, nicht auswertbar, dunkles Rauschbild) und
  `video.client.VideoEffectsSettingTest` (Reihenfolge und Texte der drei Werte, Stufen je Prüfungsausgang, gespeicherte
  Werte samt `OVERLAY` und unbekannter Wert, Übersetzungen):
  `tools/fastcheck.sh test --select-class de.lardtom.propwash.video.VideoCheckTest --select-class de.lardtom.propwash.video.client.VideoEffectsSettingTest`.
- Selbsttest `'-PpropwashSelftestOnly=video: Bildprüfung,video: Einstellung Videoeffekte'` (`VideoCheckSelfTests`,
  beide Loader, etwa 15 s nach dem Laden der Welt): FPV mit Kreativ-Brille, Freestyle-Drohne und Fisheye 80 %.
  Bildprüfung bestanden; Shader-Kopie gegen Blit bitgleich; Last je Bild Blit gegen Shader-Kopie; Rauschen 0 (Weg
  Kopie + Fisheye) und 30 % (Effekt-Durchgang) nicht schwarz, gemessen an 45 Punkten des zurückgelesenen Hauptbilds;
  Fehlerbild nachgestellt (Kopie ohne Wirkung: Rauschen 0 ganz schwarz, Rauschen > 0 sichtbar); Prüfung damit „alles
  kaputt“ → ohne Shader, Chat-Meldung, Bild wieder da, Objektiv flach; schwarzer Video-Shader → „voller Weg kaputt“ →
  Einfach, Bild da, Overlay an; Ersatz-Shader hält Bilder bei Digital mit 35 % Signal; HistorySampler nicht gebunden
  (bei strenger Prüfung Fehler und Ersatz-Overlay, sonst läuft der Durchgang weiter, Bild nicht schwarz); danach
  „Shader erneut versuchen“. Zweiter Schritt: Knopf im Video-Tab läuft Automatisch → Voll → Einfach → Automatisch.
- Bildschirmfotos (Fabric, `-PpropwashScreenshots=true`): `video_check_fault_black` (nachgestelltes Fehlerbild, OSD auf
  Schwarz wie im Discord) und `video_check_simple` (Einfach mit Rauschen als Einblendung und Chat-Meldung).
- Die Fehler (`VideoPass.Fault`: Kopie ohne Wirkung, schwarzer Video-Shader, HistorySampler nicht gebunden) gibt es nur
  für die Selbsttests im selben Paket; im Spiel sind sie nicht erreichbar.

## 5. Grüne Action-Cam-Aufnahme auf Intel-Grafik (0.6.0)

Meldung aus dem Discord (24.10.2026, „recording of action cam is green“): Propwash 0.5.2, NeoForge, Laptop mit GTX 1650
und Intel UHD Graphics 630 (Treiber 27.20.100.9168), Minecraft lief über die Intel-Grafik. Das Video kam vom eingebauten
Encoder: 1920×1080, 1713 Bilder, Ton läuft, jedes Bild hat Y = U = V = 0 (ffmpeg `signalstats`: YMIN = YMAX = UMIN = UMAX =
VMIN = VMAX = 0). Y, U und V auf 0 ergeben in YUV ein sattes Grün.

### 5.1 Ursache

Ohne Intel-Hardware über Ausschluss eingegrenzt und im Selbsttest nachgestellt (§5.4):

- **Kein schwarzes Bild.** `post/action_cam_yuv` rechnet Y = (16 + 219 · Luma) / 255, also mindestens 16, und für Schwarz
  U = V = 128. Ein Frame-Durchgang ohne Wirkung ergäbe ein schwarzes Video (Y 16, U/V 128), keine Nullen. Nullen in allen
  drei Ebenen heißen: Die gepackte Textur wurde nie beschrieben, oder das Rücklesen hat nichts gelesen.
- **Rücklesen.** `GlCommandEncoder#copyTextureToBuffer` hängt die gepackte Textur per DSA (`glNamedFramebufferTexture`)
  an den Hilfs-Framebuffer `readFbo` des Encoders und liest mit `glReadPixels` in den Auslesepuffer. Danach hängt sie
  die Textur mit dem klassischen `glFramebufferTexture2D` wieder ab.
  - Intel-Treiber melden `GL_ARB_direct_state_access` und `GL_ARB_buffer_storage` (Log der UHD 620 aus §1; vom Rechner
    mit der UHD 630 gibt es nur ein Log nach dem Wechsel auf die GTX 1650). Vanilla nimmt nur bei Intel Gen 7 und NVIDIA
    veränderliche Puffer; auf der UHD 630 ist der Auslesepuffer also dauerhaft gemappt (`MAP_READ | PERSISTENT | COHERENT`).
  - Es ist dasselbe Hilfs-Framebuffer-Paar, dessen Blit auf Intel still nichts schrieb (§1). Seine Anhänge wechseln bei
    jedem Aufruf, DSA- und klassische Aufrufe gemischt.
  - Vanilla 26.3 nutzt `copyTextureToBuffer` nur für einzelne Bildschirmfotos. Die Aufnahme liest bis zu 60-mal je
    Sekunde in sechs wiederverwendete Puffer.
- **Zeichnen.** Frame- und Pack-Durchgang laufen als Render-Pass über die Framebuffer aus `FrameBufferCache`. Die werden
  einmal angelegt und nie umgehängt. So zeichnet das ganze Spiel, auch der Video-Durchgang, der auf Intel-Grafik mit
  Rauschen > 0 ein Bild zeigt (§1). Nicht ganz auszuschließen bleibt, dass der Intel-Treiber den
  Pack-Shader falsch übersetzt (Schreiben `result[k]` mit Schleifenindex) und nichts schreibt. Den Fall deckt die
  CPU-Umrechnung ab (§5.2).
- **Warum nie bei uns.** macOS meldet weder DSA noch Buffer Storage (OpenGL 4.1). Dort laufen Hilfs-Framebuffer und
  Auslesepuffer klassisch. Auf NVIDIA erzwingt Vanilla veränderliche Puffer ohne Persistent Mapping.
- **Bestätigung bei einem Spieler.** Ab 0.6.0 zeigt das Log, welcher Schritt versagt. Steht dort `leeres Aufnahmebild
  (Y-Ebene 0) über Bildweg gpu, weiter über read` und das Video hat ein Bild, war es das Rücklesen. Folgt
  `über Bildweg read, weiter über cpu`, war es der Pack-Durchgang.

### 5.2 Behebung

- **Prüfung jedes Bilds.** `video.record.ImageGuard` prüft jedes gelesene Bild, bevor es in den Encoder geht. Ist die
  Y-Ebene durchgehend 0, ist das Bild leer. Bei einem gültigen Bild endet die Prüfung am ersten Byte, kostet also nichts.
  Leere Bilder kommen nie ins Video.
- **Drei Bildwege** (`video.record.ImagePath`), der erste leere Frame schaltet weiter:
  1. `GPU`: wie bisher Pack-Shader und `copyTextureToBuffer`, asynchron mit sechs Puffern.
  2. `READ` (Ersatzweg): Pack-Shader, dann `glReadPixels` aus einem eigenen Framebuffer (`GlReadback`), an den die
     gepackte Textur einmal klassisch angehängt ist. Das Lesen läuft synchron in einen Speicherblock, ohne DSA, ohne
     Hilfs-Framebuffer und ohne Pixelpuffer.
  3. `CPU`: Das Kamerabild aus dem Frame-Durchgang über denselben Weg als RGBA lesen. `video.record.RgbaI420` rechnet es
     auf der CPU byte-gleich zum Pack-Shader in I420 um (JUnit gegen eine Abschrift des Shaders, Selbsttest gegen die
     GPU: 0 Bytes anders).
- **Alte Bilder.** Bilder des alten Wegs, die beim Umschalten noch unterwegs sind, verwirft die Aufnahme. Auf einer
  betroffenen Karte fehlen so nur die ersten ein bis drei Bilder; das Video beginnt etwa 0,1 s später, der Ton bleibt
  synchron.
- **Nur mit OpenGL.** Ersatzweg und CPU-Umrechnung gibt es nur mit OpenGL. Unter Vulkan bricht schon der erste leere
  Frame die Aufnahme ab.
- **Abbruch.** Liefert kein Weg ein Bild, bricht die Aufnahme ab:
  - Toast „Aufnahme abgebrochen – Die Grafikkarte liefert nur leere Bilder“.
  - Rote Chat-Meldung mit dem Hinweis, Minecraft auf der stärkeren von zwei Grafikkarten zu starten.
  - Ohne ein einziges gutes Bild wird der Job verworfen: keine Datei, kein Vorschaubild. Sonst wird das Video bis dahin
    gespeichert.
- **Meldungen beim Umschalten.**
  - Chat (gelb), einmal je Spielsitzung und Weg.
  - HUD während der Aufnahme: „Ersatzweg: …“ bzw. „CPU-Umrechnung: Die Grafikkarte lieferte leere Aufnahmebilder“.
  - Log beim Umschalten: `Action-Cam: leeres Aufnahmebild (Y-Ebene 0) über Bildweg gpu, weiter über read (Grafik: …)`.
  - Log beim Speichern: `aufgenommen über Bildweg read`.
  - Log beim Abbruch: eine Fehlerzeile mit allen versuchten Wegen, den Zählern und der Grafikkarte.
- **Diagnose.** `RecordingDiagnosis#image` vermerkt den Bildweg: `GPU`, `READ`, `CPU` oder `NONE` (abgebrochen).
  `describe()` hängt „Bildweg …“ an, `ActionCamRecorder#finishedDiagnosis` hält den Stand der letzten Aufnahme.
- **Standard bleibt der GPU-Weg.** Er ist asynchron und auf allen geprüften Karten richtig. Das synchrone Lesen als
  Standard kostete jede Karte Bildzeit, und die Prüfung schaltet auf einer betroffenen Karte ohnehin nach dem ersten
  leeren Bild um.

### 5.3 Kosten

Renderthread je aufgenommenem Bild, Apple M4 Pro, 720p30, Fabric (Zusammenfassung der Aufnahmen im Log, Selbsttest §5.4):

| Weg | Renderthread je Bild |
|---|---|
| GPU (Standard, mit Prüfung) | 0,37 ms |
| Ersatzweg (`glReadPixels` der gepackten Textur, synchron) | 1,74 ms |
| CPU-Umrechnung samt Lesen des Kamerabilds | 4,11 ms |

Bei 1080p ist es etwa das 2,25-Fache. Das synchrone Lesen wartet, bis die Grafikkarte das Bild fertig hat; auf einer
schwachen Grafik kann das mehr kosten. Die Prüfung selbst kostet bei einem gültigen Bild nichts messbar (endet am
ersten Byte).

### 5.4 Tests

- **JUnit:** `video.record.ImageGuardTest` (leere Y-Ebene, schwarzes Bild des Shaders ist nicht leer, Umschalten
  GPU → READ → CPU → Abbruch, verworfene Bilder alter Wege, ohne OpenGL sofort Abbruch), `video.record.RgbaI420Test`
  (BT.709 begrenzt, Zeilen von unten, byte-gleich zur Abschrift des Pack-Shaders) und `RecordingDiagnosisTest`
  (Bildweg-Vermerk, Texte in beiden Sprachen).

  ```sh
  tools/fastcheck.sh test --select-class de.lardtom.propwash.video.record.ImageGuardTest \
    --select-class de.lardtom.propwash.video.record.RgbaI420Test \
    --select-class de.lardtom.propwash.video.record.RecordingDiagnosisTest
  ```

- **Selbsttest** (`video.client.record.ActionCamImageSelfTests`, beide Loader, nach dem Fisheye-Schritt der Action-Cam):

  ```sh
  '-PpropwashSelftestOnly=video: Drohne mit Action-Cam Heavy,video: Action-Cam-Bildweg'
  ```

  1. Normale Aufnahme: Sie bleibt auf dem GPU-Weg. Im selben Bild liest der Ersatzweg dieselben Bytes wie
     `copyTextureToBuffer`, und die CPU-Umrechnung weicht höchstens 2 ab (gemessen 0).
  2. Rücklesen ohne Wirkung (Fehlerbild Intel): Umschalten auf den Ersatzweg mit HUD, Chat und Diagnose; das Video hat
     ein Bild, der Himmel ist oben.
  3. Pack-Durchgang ohne Wirkung: Der Ersatzweg ist auch leer, also CPU-Umrechnung; das Video hat ein Bild.
  4. Beide Durchgänge ohne Wirkung: Abbruch binnen 60 Ticks mit Toast und Chat, kein Video, Diagnose `NONE`.
- **Vor dem Fix:** Schritt 2 und 3 scheiterten mit „Video ohne Bild (Y 0, Cb 0, Cr 0)“, Schritt 4 mit „grünes Video
  gespeichert“. Das ist dasselbe Bild wie das Video aus dem Discord.
- **Fehler-Injektion:** `ActionCamCapture.Fault` (`READBACK`: Auslesepuffer erhält Nullen statt der Kopie; `PACK`:
  Pack-Durchgang ohne Wirkung; `PASSES`: beide Durchgänge ohne Wirkung) gibt es nur für die Selbsttests im selben Paket.
