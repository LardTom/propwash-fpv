# Grafik-Kompatibilität für Intel-Grafik unter Windows (0.6.0)

Für Entwickler und Support. Ergänzt `docs/video-check.md` (schwarzes Bild in der Brille, §1–4; grüne Action-Cam-Aufnahme,
§5). Beide Fehler traten nur auf Intel-Grafik unter Windows auf (Discord-Meldungen 18.–24.10.2026: UHD 620, HD 520,
UHD 630) und wurden in 0.6.0 zuerst Stelle für Stelle behoben. Dieses Dokument beschreibt den gemeinsamen Hintergrund im
OpenGL-Teil von Minecraft 26.3, den Kompatibilitätsmodus, die Prüfung der Grafikwege und die Ersatzwege aller
Propwash-Stellen, die kopieren, blitten oder zurücklesen.

Echte Intel-Hardware gab es beim Bau nicht. **Belegt** ist, was im Quelltext von Minecraft 26.3 steht und was die
Selbsttests auf macOS (Apple M4 Pro, OpenGL 4.1 Metal) messen. **Nachgestellt** ist das Treiberverhalten: Die Selbsttests
lassen Kopie oder Rücklesen auf Treiber-Ebene ohne Wirkung laufen, so wie es die Fehlerbilder aus dem Discord nahelegen.

## 1. Direct State Access im OpenGL-Teil von Minecraft 26.3

Der OpenGL-Teil liegt in `com.mojang.renderpearl.backend.opengl`. Zwei Fabriken entscheiden einmal beim Anlegen des
Geräts (`GlDevice`-Konstruktor), welchen Weg das ganze Spiel nimmt:

- `BufferStorage#create(GLCapabilities, Set, forceMutable)`: `Immutable` (`glBufferStorage`, Lese- und Schreibpuffer
  dauerhaft gemappt, `PERSISTENT | COHERENT`), wenn der Treiber `GL_ARB_buffer_storage` meldet und `forceMutable` falsch
  ist; sonst `Mutable` (`glBufferData`, Abbilden erst beim Lesen). Vanilla setzt `forceMutable` bei Intel Gen 7
  (`GlHeuristics#couldBeIntelGen7`: HD 2500/4000, „HD Graphics“) und NVIDIA. Die UHD 620/630 und HD 520 (Gen 9/9.5) sind
  nicht dabei.
- `DirectStateAccess#create(GLCapabilities, Set, GlHeuristics)`: `Core` (Direct State Access), wenn der Treiber
  `GL_ARB_direct_state_access` meldet und kein OpenGL über DirectX 12 läuft (Windows auf ARM, Renderer „D3D12 …“); sonst
  `Emulated` (klassisch binden, dann aufrufen).

| Aufgabe | `Core` | `Emulated` |
|---|---|---|
| Puffer anlegen, füllen, ändern | `glCreateBuffers`, `glNamedBufferData/SubData/Storage` | `glGenBuffers`, binden, `glBufferData/SubData`, `glBufferStorage` |
| Puffer abbilden, freigeben, spülen | `glMapNamedBufferRange`, `glUnmapNamedBuffer`, `glFlushMappedNamedBufferRange` | binden, `glMapBufferRange`, `glUnmapBuffer`, `glFlushMappedBufferRange` |
| Puffer kopieren | `glCopyNamedBufferSubData` | an `COPY_READ`/`COPY_WRITE` binden, `glCopyBufferSubData` |
| Framebuffer anlegen | `glCreateFramebuffers` | `glGenFramebuffers` |
| Texturen anhängen | `glNamedFramebufferTexture`, ohne zu binden (Bind-Ziel 0) | Framebuffer binden, `glFramebufferTexture2D`, alten Framebuffer zurück |
| Blit | `glBlitNamedFramebuffer` | `READ`/`DRAW` binden, `glBlitFramebuffer`, zurückbinden |

Alles andere geht in beiden Fällen klassisch: Texturen hochladen (`writeToTexture`, `copyBufferToTexture`:
`glTexSubImage2D`), Zeichnen, Programme, Sampler.

Wer welchen Weg benutzt (`GlCommandEncoder`):

| Befehl | DSA-Weg | Vanilla 26.3 | Propwash |
|---|---|---|---|
| Render-Pass | Framebuffer aus `FrameBufferCache`, einmal per DSA angehängt, dann gebunden | ständig | ständig (alle Video-, Objektiv-, Scheinwerfer-, Aufnahme- und Bild-im-Bild-Durchgänge) |
| `clearColorTexture`, `clearDepthTexture` | Hilfs-Framebuffer `drawFbo`, per DSA angehängt und gebunden | ständig | Prüf- und Zwischenbilder |
| `writeToBuffer`, `copyToBuffer` | `glNamedBufferSubData`, `glCopyNamedBufferSubData` | ständig (Chunks über `StagingBuffer`) | Uniform-Puffer |
| `copyTextureToBuffer` (Rücklesen) | Textur per DSA an `readFbo`, an `READ_FRAMEBUFFER` binden, `glReadPixels` in den Auslesepuffer (auf Intel Gen 9 dauerhaft gemappt), danach klassisch abhängen | nur Bildschirmfoto (F2), Textur-Ausgabe zum Debuggen, Tracy | Bildprüfung, Scheinwerfer-Prüfung, Action-Cam, Foto der API |
| `copyTextureToTexture` (Kopie) | beide Texturen per DSA an `readFbo`/`drawFbo` (nicht gebunden), `glBlitNamedFramebuffer` | nie (`RenderTarget#copyColorFrom` und `#copyDepthFrom` ruft Vanilla nicht auf) | bis 0.5.2 Brille (Fisheye-Weg) und Scheinwerfer; seit 0.6.0 nur noch, wenn der Ersatz-Shader fehlt |
| `presentTexture` | Bild per DSA an `drawFbo`, `glBlitNamedFramebuffer` ins Fenster, vorher Farb- und Tiefenmaske gesetzt | jedes Bild | – |

Folgerungen:

- Die Fehlerbilder passen zu den beiden Wegen, die Vanilla selbst kaum benutzt: Blit zwischen den Hilfs-Framebuffern
  (schwarzes Zwischenbild, `video-check.md` §1) und Rücklesen über `readFbo` in einen dauerhaft gemappten Puffer (nur
  Nullen, `video-check.md` §5). Chunks, Render-Pässe und das Bild im Fenster gehen auf denselben Karten.
- Der Blit ins Fenster geht, der Blit zwischen zwei Hilfs-Framebuffern nicht. Der Unterschied: Im Fehlerfall sind beide
  Framebuffer nur per DSA angehängt und nie gebunden, und vorher ist keine Farbmaske gesetzt. Welcher Teil auf dem
  Intel-Treiber versagt, lässt sich ohne Gerät nicht entscheiden; beides fällt mit dem nachgebildeten Weg und den
  Ersatzwegen unten weg.
- macOS meldet weder `GL_ARB_direct_state_access` noch `GL_ARB_buffer_storage` (OpenGL 4.1) und nimmt immer `Emulated`
  und `Mutable`. Auf macOS laufen alle Wege, die auf Intel versagen.
- Unter Vulkan (26.3 kann beides) gibt es diese Fabriken nicht; der Kompatibilitätsmodus wirkt dort nicht.

## 2. Kompatibilitätsmodus

Paket `de.lardtom.propwash.gpucompat` (Entscheidung, Einstellung, Testbild) und `…gpucompat.client`, Mixins in
`mixin/gpucompat/client` (`propwash.gpucompat.mixins.json`, nur Client):

- `BufferStorageMixin`: setzt `forceMutable`, wenn der Modus an ist. Ergebnis: veränderliche Puffer wie bei Intel Gen 7
  und NVIDIA, kein Persistent Mapping (`DeviceFeatures#persistentMapping` falsch, `GlTransientMemory.Fallback`).
- `DirectStateAccessMixin`: liest `GL_ARB_direct_state_access` als falsch, wenn der Modus an ist. Ergebnis:
  `DirectStateAccess.Emulated` für alle Puffer- und Framebuffer-Aufrufe.
- Beide melden außerdem, was der Treiber anbietet und welche Klasse entstanden ist (`GpuCompatHooks#status`). Ist der
  Modus aus, geben sie den Wert unverändert zurück: derselbe OpenGL-Weg wie ohne Propwash, also auch dieselben Bilder und
  dieselbe Leistung.

Zusammen ist das genau der Weg, den macOS nimmt. Er kostet nur ein paar zusätzliche Bind-Aufrufe und das Abbilden der
Puffer beim Lesen; Vanilla fährt NVIDIA-Karten ohnehin so.

Entscheidung (`GpuCompatDecision#decide`, einmal beim Anlegen des Geräts):

| Einstellung | Ergebnis |
|---|---|
| **Automatisch** (Standard) | an, wenn Hersteller oder Renderer „Intel“ enthält und Windows läuft (auch Arc und OpenGL über D3D12); sonst aus (macOS, Linux/Mesa, NVIDIA, AMD) |
| **An** | immer an |
| **Aus** | immer aus |

- Einstellung im Konfigurator, Tab „Konfiguration“, Abschnitt „Grafik“: „Grafik-Kompatibilität: Automatisch/An/Aus“
  mit Tooltip. Darunter steht der Stand seit dem Start („Seit dem Start an (Intel-Grafik unter Windows erkannt): …“)
  und, wenn die Wahl den Weg ändern würde, „Die Änderung wirkt nach einem Neustart von Minecraft.“ Findet die Prüfung
  der Grafikwege (§3) einen Fehler, steht das dort auch. Gespeichert in `config/propwash/gpu-compat.json`
  (`{"mode": "AUTO"}`), unbekannte Werte werden zu Automatisch.
- Vorgabe ohne Menü (Support, Start-Skripte): System-Property `-Dpropwash.gpuCompat=on|off|auto` oder Umgebungsvariable
  `PROPWASH_GPU_COMPAT` (Property vor Variable; auch `an`/`aus`/`true`/`false`). Die Einstellung zeigt dann „Vorgegeben
  über PROPWASH_GPU_COMPAT.“
- Nur in der Entwicklungsumgebung: `PROPWASH_GPU_COMPAT_SIMULATE=intel-windows` lässt die Erkennung „Intel(R) UHD
  Graphics 620 unter Windows“ sehen (Selbsttest mit erzwungenem Intel-Modus auf dem Mac).
- Genau eine Logzeile beim Start, etwa:
  `Grafik-Kompatibilität: an – Automatisch, Intel-Grafik unter Windows erkannt (Intel / Intel(R) UHD Graphics 620, Windows). Treiber bietet Direct State Access: ja, Buffer Storage: ja; OpenGL-Weg wie auf macOS: DSA nachgebildet (Emulated), Puffer veränderlich (Mutable)`.
  Vanillas Zeile `Using graphics device extensions: …` nennt dann weder `GL_ARB_direct_state_access` noch
  `GL_ARB_buffer_storage`.

## 3. Prüfung der Grafikwege

`GpuPathCheck` schickt einmal je Sitzung, beim ersten Bedarf (Bildprüfung, Scheinwerfer-Prüfung, Foto), ein Testbild
(16 × 8, jedes Byte ungleich 0, `GpuPaths#pattern`) über alle Wege und vergleicht Byte für Byte:

1. Hochladen (`writeToTexture`, klassisch).
2. Rücklesen über die GPU: `copyTextureToBuffer`, `glFinish`, Puffer abbilden.
3. Rücklesen klassisch: `ClassicReadback`, ein eigener Framebuffer, an den die Textur einmal klassisch angehängt ist,
   `glReadPixels` in Hauptspeicher (ohne DSA, ohne Hilfs-Framebuffer, ohne Pixelpuffer; vorher `GlReadback` der
   Action-Cam aus F-INTELREC-1, jetzt gemeinsam benutzt).
4. Kopie per Blit (`copyTextureToTexture` in ein schwarz gelöschtes Ziel), gelesen über einen funktionierenden Weg.
5. Kopie per Shader (`VideoPass#simple` mit `video_feed_simple`), ebenso gelesen.

Jeder Weg ergibt „ok“, „nur Nullen“, „falsche Werte“, „Fehler“ oder „nicht geprüft“. Der Rücklese-Weg für alle
Stellen (`GpuPaths.Health#readPath`): GPU, solange das GPU-Rücklesen nicht kaputt ist; sonst klassisch; liefern beide
nichts, „keiner“. Log: `Grafikwege geprüft (… ms): Rücklesen über die GPU …, klassisch …; Kopie per Blit …, per Shader
…; Rücklesen über …; Grafik: …; Grafik-Kompatibilität: …` (Info, bei einem Fehler Warnung). Kosten auf dem Mac 0,4–3,6 ms,
einmal. Unter Vulkan prüft sie nichts und lässt den GPU-Weg stehen.

`GuardedReadback#read` liest danach für die Stellen über den gewählten Weg: GPU asynchron wie bisher, klassisch synchron
im selben Bild, „keiner“ meldet sofort „kein Bild“, schreibt eine Warnung ins Log und einmal je Sitzung eine rote
Chat-Meldung mit dem Tipp „Grafik-Kompatibilität“ auf „An“ zu stellen und neu zu starten, bei zwei Grafikkarten
Minecraft auf der stärkeren zu starten.

## 4. Die Stellen in Propwash

| Stelle | Kopie / Rücklesen | Schutz seit 0.6.0 | bei kaputtem Weg |
|---|---|---|---|
| Brille, Fisheye-Weg und gehaltene Bilder (`VideoPass`) | Kopie | Shader-Kopie statt Blit (fix-intel-video), Blit nur ohne Ersatz-Shader | Bild sichtbar; Bildprüfung schaltet sonst auf Einfach oder ohne Shader |
| Bildprüfung (`VideoSelfCheck`) | Rücklesen (4 Bilder) | `GuardedReadback` | GPU kaputt → klassisch, Ergebnis wie sonst („Rücklesen über klassisch“ im Log); beide kaputt → „nicht auswertbar: kein Weg liefert ein Bild“, Effekte bleiben, Chat-Meldung |
| Scheinwerfer (`HeadlightPass`) | Kopie des Hauptbilds | Shader-Kopie statt Blit (neu, bitgleich, `blitCopies` bleibt 0) | Bild unverändert |
| Scheinwerfer-Prüfung (`HeadlightProbe`) | Rücklesen (Streifen vorher/nachher) | `GuardedReadback` (vorher eigene Puffer) | klassisch im selben Bild; beide kaputt → Prüfung bleibt offen, Chat-Meldung |
| Action-Cam (`ActionCamCapture`) | Rücklesen je Bild | Bildprüfung je Bild und Wege GPU → Ersatzweg → CPU (F-INTELREC-1) | Aufnahme mit Bild; ohne Weg Abbruch mit Toast und Chat, kein Video |
| Vorschaubild der Aufnahmen (`RecordingThumbnails`) | – | entsteht aus geprüften Aufnahmebildern auf der CPU | wie Action-Cam |
| Foto der API (`RecordingControl#photo`) | Rücklesen | `GuardedScreenshot` (GPU wie Vanilla, sonst klassisch) | Foto über den Ersatzweg; ohne Weg kein Foto, Chat „Foto nicht gespeichert …“ |
| Vorschau-Bildstörung im Video-Tab (`BreakupPreview`), Ersatz-Overlay (`VideoOverlay`) | – | nur GUI-Zeichnungen und Textur-Upload | nicht betroffen |
| F7-Zuschauerbild (`SpectateSession`) | – | dasselbe Hauptbild durch `VideoPass` wie die Brille | wie Brille |
| Flashback-Kamera (`FlashbackCam`) | – | speichert nur Kamerapfade; Bilder erzeugt Flashback selbst | nicht betroffen |
| Werkbank, Konfigurator, Lackierung, Wartung, Share-Vorschau im Chat, Drohnen-Vorschau der API (`DronePreview`), Flugschule (`SceneRenderer`), Funken-Vorschau (`RemoteDemoRenderer`) | – | Bild-im-Bild von Vanilla: Render-Pass in eine eigene Textur, dann als GUI-Fläche gezeichnet | nicht betroffen (Selbsttest: 0 Kopien, 0 Rücklesungen) |
| Add-on-Haken (HUD, OSD, Werkbank-Erweiterung, Drohnen-Vorschau) | – | GUI-Zeichnungen; die API gibt keine Render-Ziele heraus | nicht betroffen |
| Vanilla-Bildschirmfoto (F2) | Rücklesen | nur der Kompatibilitätsmodus | nicht in Propwash abgesichert |

## 5. Tests

- JUnit (`tools/fastcheck.sh test --select-package de.lardtom.propwash.gpucompat`, 17 Tests):
  `GpuCompatDecisionTest` (Intel-Chips aus den Meldungen, Arc, NVIDIA, AMD, Apple, Mesa unter Linux, Intel-Mac, leere
  Namen, An/Aus, Property vor Variable, Simulation, Logtext), `GpuCompatSettingTest` (drei Werte in Knopf-Reihenfolge,
  gespeicherte und unbekannte Werte, Texte beider Sprachen ohne überzählige Schlüssel) und `GpuPathsTest` (Testbild,
  Vergleich ok/Nullen/falsch/fehlt, Wahl des Rücklese-Wegs, Logtext).
- Selbsttest-Gruppe `compat:` (beide Loader). Fehler-Injektion auf Treiber-Ebene nur in der Entwicklungsumgebung:
  `mixin.dev.client.GlCommandEncoderFaultMixin` (`propwash.dev.mixins.json`) lässt `copyTextureToTexture` nichts tun
  und füllt bei `copyTextureToBuffer` den Zielpuffer mit Nullen (wie der Treiber, der still nichts tut);
  `GpuFaults` schaltet das und `ClassicReadback.Fault` (klassisches Rücklesen liefert Nullen) und zählt die Aufrufe.
  Die Messungen der Selbsttests selbst (Vanilla-`Screenshot` aus einer `*SelfTests`-Klasse) bleiben davon ausgenommen.
  - `compat: Erkennung …`: Entscheidung wie erwartet (aus Einstellung, Vorgabe und Simulation nachgerechnet), Mixins
    gegriffen, Klassen passen zu Vanillas Erweiterungsliste, genau eine Logzeile.
  - `compat: Grafikwege …`: sechs Fälle (ohne Fehler, Blit, GPU-Rücklesen, beides, klassisch auch, alles).
  - `compat: Einstellung …`: Knopf Automatisch → An → Aus → Automatisch, Hinweis auf den Neustart nur, wenn sich der Weg
    ändert, Speichern in `gpu-compat.json`, Entscheidung bleibt bis zum Neustart.
  - `compat: Brille und Foto …`: FPV mit Kreativ-Brille, Fisheye 80 %, Rauschen 0 (der Weg aus dem Discord), mittags.
    Blit ohne Wirkung → Bild sichtbar, 0 Blits; GPU-Rücklesen mit Nullen → Bildprüfung bestanden über den Ersatzweg; kein
    Rücklese-Weg → nicht auswertbar mit Grund, Effekte bleiben, Bild sichtbar, Chat-Meldung; Foto über den Ersatzweg
    sichtbar, ohne Weg kein Foto und Meldung.
  - `compat: Scheinwerfer nachts …`: der ganze Schritt „video: FPV-Scheinwerfer bei Nacht …“ mit Blit ohne Wirkung und
    GPU-Rücklesen mit Nullen: Aufhellen, Prüfung über den Ersatzweg, schwarzes Bild wird erkannt, Ersatz-Aufhellung.
  - `compat: Bild-im-Bild …`: Konfigurator mit Drohnen-Vorschau 40 Ticks lang, alle Fehler an: 0 Kopien, 0 Rücklesungen.
  - `compat: Action-Cam …` (angemeldet in `ActionCamSelfTests` nach den Bildweg-Schritten, braucht „video: Drohne mit
    Action-Cam Heavy im FPV“): GPU-Rücklesen mit Nullen → Aufnahme über den Ersatzweg mit Bild; GPU und klassisch Nullen →
    Abbruch mit Meldung, kein Video.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=compat:,video: Drohne mit Action-Cam Heavy'
PROPWASH_GPU_COMPAT_SIMULATE=intel-windows JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :neoforge:runClientSelftest '-PpropwashSelftestOnly=compat:,video: Drohne mit Action-Cam Heavy'
PROPWASH_GPU_COMPAT=on JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=compat:'
```

Grenzen der Nachstellung: Auf dem Mac nimmt OpenGL schon ohne Propwash `Emulated` und `Mutable`. Der Selbsttest mit
erzwungenem Intel-Modus belegt deshalb Entscheidung, Mixins, Logzeile und dass der Weg danach `Emulated`/`Mutable` ist,
aber nicht das Umschalten von `Core` auf `Emulated` und nicht, dass der Intel-Treiber damit richtig arbeitet. Das zeigt
erst das Log eines Spielers.

## 6. Bestätigung bei einem Spieler

Aus `latest.log` eines Intel-Rechners mit 0.6.0:

- `Grafik-Kompatibilität: an – Automatisch, Intel-Grafik unter Windows erkannt …` und eine Extensions-Zeile ohne
  `GL_ARB_direct_state_access`: Modus aktiv.
- `Grafikwege geprüft …: Rücklesen über die GPU ok, klassisch ok; Kopie per Blit ok …`: Mit dem nachgebildeten Weg
  arbeitet der Treiber richtig. Steht dort mit „Aus“ (Einstellung oder `-Dpropwash.gpuCompat=off`) „Rücklesen über die
  GPU nur Nullen“ oder „Kopie per Blit nur Nullen“, ist die Ursache auf DSA-Ebene belegt.
- Bleibt es auch mit „An“ bei „nur Nullen“, liegt es nicht an DSA; dann tragen die Ersatzwege (§4).
