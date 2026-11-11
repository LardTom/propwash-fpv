# Vorschau-Builds für Spieltester

Für Spieltester und den Release-Ablauf. Ein Vorschau-Build ist ein Release-Kandidat (`0.6.0-rc.N`), der mit dem
Build-Schalter `-Ppropwash_preview=true` gebaut ist. Er zeigt immer gut lesbar, aber dezent

> Propwash Preview 0.6.0-rc.N · not final

unten rechts im Bild, damit Bildschirmfotos und Videos aus Spieltests nie mit dem fertigen Release verwechselt werden
(Owner-Entscheidung vom 28.10.2026, Befund PREVMARK-1). Das Spielfenster heißt dazu „Propwash Preview 0.6.0-rc.N“
(Befund PREVMARK3-1). Spieltester können das Wasserzeichen nicht abschalten: Es steckt beim Bauen fest im Code und wird
mit einem eigenen Shader aus dem Code gezeichnet (Befunde PREVMARK2-1 und PREVMARK3-1, Abschnitt 2). Release-Builds
(Schalter aus, Standard) zeigen nichts davon.

![Wasserzeichen im Spiel](screenshots/preview_ingame.png)

## 1. Wo das Wasserzeichen steht

- **Im Spiel** als oberste Schicht über allem: über HUD, OSD, Videobild und Chat, mit und ohne Brille, auch bei
  ausgeblendetem HUD (F1).
- **In Menüs und im Titelbildschirm:** über jedem Fenster, Overlay und Toast, im Titelbildschirm eine Zeile über
  „Copyright Mojang AB“.
- **In Bildschirmfotos (F2) und Bildschirmaufnahmen** (OBS & Co.), weil es Teil des Bildes ist.
- **In Action-Cam-Aufnahmen** (MP4 unter `propwash/recordings/` im Spielordner) und in **Fotos** der Add-on-API
  (`RecordingControl#photo`): unten rechts ins Bild gestempelt, weiß mit Schatten, ein Schriftpunkt je 360 Pixel
  Bildhöhe (bei 1080p 3 × 3 Bildpunkte, Großbuchstaben 21 Bildpunkte hoch).
- **Mit Ressourcenpaketen**, auch wenn ein Paket alle Propwash-Texte leert, die Minecraft-Schrift ersetzt oder die
  Shader der GUI austauscht (`docs/screenshots/preview_resourcepack.png`: das Paket des Selbsttests verwirft in
  `core/gui.fsh` genau die Farben des Wasserzeichens, die graue Probe-Fläche oben links zeigt, dass es wirkt – ihre
  weiße Mitte fehlt –, das Wasserzeichen bleibt). Was ein Paket nicht ändern kann, steht in Abschnitt 2.
- **Im Fenstertitel:** „Propwash Preview 0.6.0-rc.N“ statt „Minecraft 26.3 …“, im Spiel, in Menüs und im
  Titelbildschirm. Der Titel kommt nur aus dem Code (`PreviewBuild.TITLE`), ohne Sprachdatei.

Der Text ist bewusst englisch und unübersetzt, die Version ist die des Builds (`0.6.0-rc.N`). Im Spiel hat die Schrift
unabhängig von der GUI-Skalierung dieselbe Größe in Bildpunkten: ein Bildpunkt je Schriftpunkt bis 809 Pixel
Fensterhöhe, zwei ab 810 (also bei 1080p), drei ab 1350, vier ab 1890 (`PreviewStamp#screenScale`).

## 2. Warum Spieltester es nicht abschalten können

1. **Der Schalter wirkt nur beim Bauen.** Gradle erzeugt bei jedem Build die Klasse `preview/PreviewSwitch` mit
   `public static final boolean ENABLED` und `public static final String VERSION` (Aufgabe `generatePreviewSwitch` in
   `common/build.gradle`) und kompiliert sie statt der Vorlage in `common/src/main/java`. Der Compiler setzt die Werte
   als Konstanten in jede Klasse ein, die sie benutzt; zur Laufzeit liest Propwash den Schalter nirgends: kein Manifest,
   keine Datei im Jar, keine Systemeigenschaft, keine Umgebungsvariable, keine Einstellung, keine Spielregel, keine
   Taste, kein Befehl.
2. **Text, Schrift und Farbe kommen aus dem Code.** Der Text besteht aus festen Zeichenketten und der Version aus dem
   Build. Gezeichnet wird er in einer eigenen Pixelschrift (`preview/PreviewFont`, 5 × 7 mit Unterlängen) als
   einfarbige Rechtecke, nicht mit der Minecraft-Schrift und ohne Sprachdatei oder Textur. Farbe und Schatten sind fest
   (`0xB4FFFFFF` und `0xB4000000`), die Größe hat ein festes Minimum von einem Bildpunkt je Schriftpunkt.
3. **Auch der Shader kommt aus dem Code.** Die Vanilla-GUI zeichnet Flächen mit dem Shader `core/gui`, den Minecraft
   aus den Ressourcen lädt; ein Ressourcenpaket mit eigener `assets/minecraft/shaders/core/gui.fsh` konnte damit
   gezielt die Bildpunkte des Wasserzeichens verwerfen, auf dem Bildschirm, in F2-Fotos und in OBS-Aufnahmen, während
   HUD und Fadenkreuz blieben (Befund PREVMARK3-1). Das Wasserzeichen nutzt deshalb eine eigene Pipeline `preview/client/PreviewPipeline` (`propwash:pipeline/preview_mark`)
   mit Vertex- und Fragment-Shader als Quelltext im Code, ohne `#include`, ohne `discard`, ohne Tiefentest und mit der
   Farbe unverändert aus dem Code. Propwash übersetzt sie selbst über `GpuDevice#compilePipeline` mit einer eigenen
   Shader-Quelle; der Shader-Lader von Minecraft kennt sie nicht, ein Neuladen der Ressourcen übersetzt sie nicht neu.
   `mixin/core/client/RenderSystemMixin` gibt dem GUI-Renderer in `RenderSystem#getCompiledPipelineNullable` für diese
   Pipeline nur die eigene Übersetzung. Shader unter `assets/propwash/shaders/` in einem Paket bleiben unbenutzt.
   Das Wasserzeichen ist die letzte Zeichnung ins Hauptbild; danach kopiert Minecraft das Bild ohne Shader ins Fenster
   (`GpuSurface#blitFromTexture`) bzw. für F2 in die Datei, auch dort kann kein Paket-Shader mehr eingreifen.
4. **Nichts blendet es aus.** Es gibt keinen Schalter in den Einstellungen; F1, Menüs, Bildschirmfotos und Aufnahmen
   zeigen es.
5. **Im Release ist der Code-Pfad leer.** Ohne Schalter ist `ENABLED` `false`; der Compiler lässt den Code dahinter weg.
   Die Mixins in `Gui#extractRenderState`, `RenderSystem#getCompiledPipelineNullable` und `Minecraft#createTitle` rufen
   nichts auf, Action-Cam und Foto stempeln nicht, der Fenstertitel bleibt der von Minecraft.

Grenzen:
- Wer die Klassen im Jar verändert, Propwash selbst baut oder eine andere Mod mit eigenem Code dazulädt (etwa eine, die
  das Rendern ganz ersetzt), kann alles ändern, auch Wasserzeichen und Fenstertitel. Dagegen hilft kein Build-Schalter.
- Ein Ressourcenpaket kann alles verändern, was unter dem Wasserzeichen liegt (Welt, HUD, Menüs), auch mit eigenen
  Shadern, aber nicht das Wasserzeichen selbst: Es wird zuletzt und mit dem eigenen Shader gezeichnet; auf hellem
  Grund bleibt der schwarze Schatten sichtbar, auf dunklem die weiße Schrift.
- Aufnahmen der Action-Cam und Fotos über die API stempelt Propwash unabhängig davon im Speicher ein.

## 3. Bauen

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew clean build -Ppropwash_preview=true
```

- Erlaubt sind nur `true` und `false` (Standard). Mit einer Release-Nummer ohne Vorab-Suffix (`0.6.0`) bricht der Build
  ab: ein Vorschau-Build trägt die Nummer seines Release-Kandidaten. Die Version darf nur Ziffern, Buchstaben und
  `. + _ -` enthalten (sie steht im Wasserzeichen).
- Die Jars heißen `propwash-<loader>-26.3-<version>-preview.jar`. Zur Kennzeichnung liegen darin
  `META-INF/propwash-build.properties` mit `preview=true` und das Manifest-Attribut `Propwash-Preview: true`; das Spiel
  liest beides nicht. Gegenüber den Jars des Release-Kandidaten unterscheiden sich außerdem `preview/PreviewSwitch` und
  die Klassen, in die der Compiler die Konstante einsetzt (`preview/PreviewBuild`, `preview/client/PreviewMark`,
  `mixin/core/client/GuiMixin`, `mixin/core/client/RenderSystemMixin`, `mixin/core/client/MinecraftMixin`,
  `video/client/record/ActionCamRecorder`, `apiimpl/client/RecordingControlImpl`).
- `publishToMavenLocal` bricht mit dem Schalter ab; Add-on-Entwickler bekommen immer die Jars ohne Wasserzeichen.
- Ohne Schalter steht im Jar `preview=false` und das Manifest-Attribut `Propwash-Preview: false`.

Ablage: `release/internal/<version>-preview/` neben `release/internal/<version>/` (`docs/RELEASING.md` §4).

## 4. Für Entwickler

| Datei | Vertrag |
|---|---|
| `build.gradle` | liest `-Ppropwash_preview` (nur `true`/`false`, sonst Abbruch; mit `true` nur bei einer Version mit `-`), setzt `ext.propwashPreview` und für den Selbsttest `propwash.selftest.preview` |
| `common/build.gradle` | `generatePreviewSwitch` schreibt `build/generated/sources/previewSwitch/java/…/PreviewSwitch.java` aus der Vorlage (`ENABLED = <Schalter>`, `VERSION = "<Version>"`, Abbruch bei anderen Zeichen in der Version), `compileJava` nimmt diese statt der Vorlage, Bereitstellung als Konfiguration `commonPreviewSwitch`; JUnit bekommt `propwash.preview.expected` |
| `build-logic/…/multiloader-loader.gradle` | fabric und neoforge kompilieren die erzeugte Klasse statt der Vorlage |
| `build-logic/…/multiloader-common.gradle` | ersetzt `${propwash_preview}` in `META-INF/propwash-build.properties`, Manifest `Propwash-Preview` (beides nur zur Kennzeichnung), Jar-Zusatz `preview`, Maven-Veröffentlichung gesperrt |
| `preview/PreviewSwitch` | Vorlage mit `ENABLED = false` und `VERSION = ""`; gilt ohne Gradle (`tools/fastcheck.sh`, IDE) |
| `preview/PreviewBuild` | `ENABLED` (Konstante aus `PreviewSwitch`), `enabled()`, `TEXT` = `watermark(PreviewSwitch.VERSION)`, `watermark(version)` = „Propwash Preview <Version> · not final“ (leere Version „?“), `TITLE` = `windowTitle(PreviewSwitch.VERSION)` = „Propwash Preview <Version>“ |
| `preview/PreviewFont` | Pixelschrift im Code: Ziffern, Buchstaben, `. + _ - · ?` und Leerzeichen, 9 Zeilen (7 über der Grundlinie), unbekannte Zeichen als „?“; `raster(text)` mit zusammengefassten Rechtecken |
| `preview/PreviewStamp` | Farben `TEXT_ARGB`/`SHADOW_ARGB`, `MIN_SCALE`, `screenScale(fensterhöhe)` = `max(1, round(h / 540))`, `place(raster, breite, höhe, anhebung)` (Ecke unten rechts, Rand 4 Schriftpunkte), `stampScale(bildhöhe)` = `max(1, round(h / 360))`, `mask(raster, bildhöhe)`, `stampI420` für Action-Cam-Bilder (Luma aufhellen, Schatten abdunkeln, Farbe neutral), `stampArgb` für Fotos; nur die Ecke unten rechts ändert sich |
| `preview/client/PreviewMark` | `extract(graphics, resourcesLoaded)` zeichnet in zwei neuen Schichten (`nextStratum`, erst Schatten, dann Schrift) die Rechtecke der Pixelschrift in Fensterpixeln mit `PreviewPipeline.PIPELINE`; `stampFrame`, `stampImage` für Aufnahme und Foto; Zähler für den Selbsttest; ohne Schalter beginnt jede dieser Methoden mit `return` |
| `preview/client/PreviewPipeline` | `PIPELINE` (`propwash:pipeline/preview_mark`, Shader `propwash:core/preview_mark`, Bindungen wie die Vanilla-GUI: `Globals`, `Projection`, `DynamicTransforms`, `POSITION_COLOR`, Quads, durchscheinend, ohne Tiefentest), `SOURCE` (Shader-Quelle im Code, antwortet nur für die beiden eigenen Shader, keine Includes), `compiled()` (übersetzt einmal über `GpuDevice#compilePipeline` mit `SOURCE`, Abbruch mit Meldung, falls das scheitert), `lookup(pipeline)`; Zähler `compiles()`, `lookups()` für den Selbsttest |
| `mixin/core/client/GuiMixin` | ruft nur mit Schalter `PreviewMark.extract` in `Gui#extractRenderState` vor `applyCursor`, also nach HUD, Fenster, Overlay, Toasts und Debug-Anzeige |
| `mixin/core/client/RenderSystemMixin` | nur mit Schalter: `RenderSystem#getCompiledPipelineNullable` liefert für `PreviewPipeline.PIPELINE` die eigene Übersetzung, bevor Minecraft in seinen Pipeline-Caches mit Shadern aus den Ressourcen sucht |
| `mixin/core/client/MinecraftMixin#propwash$previewTitle` | nur mit Schalter: `Minecraft#createTitle` liefert `PreviewBuild.TITLE` (gilt für den ersten Titel beim Start und jedes `updateTitle`) |
| `video/client/record/ActionCamRecorder#accept`, `apiimpl/client/RecordingControlImpl#photo` | stempeln nur mit Schalter, nach der Leer-Bild-Prüfung bzw. vor dem Speichern |

## 5. Tests

- JUnit: `tools/fastcheck.sh test --select-package de.lardtom.propwash.preview` (`PreviewHardcodedTest` 9,
  `PreviewPipelineTest` 3, `PreviewFontTest` 5, `PreviewStampTest` 7, `PreviewBuildTest` 4). `PreviewHardcodedTest`
  liest die kompilierten Klassen: `PreviewSwitch` hat nur Konstanten (`ConstantValue`), keine Klasse liest `PreviewSwitch` oder
  `PreviewBuild.ENABLED` zur Laufzeit, kein Hauptcode nennt Kennzeichen-Datei, Manifest-Attribut, Systemeigenschaft oder
  Umgebungsvariable, der Wasserzeichen-Code benutzt weder Dateien, Systemeigenschaften, Umgebung, Einstellungen, Tasten,
  Befehle, Spielregeln, Sprachdateien, Minecraft-Schrift, Texturen noch Mod-Metadaten; eine frisch geladene
  `PreviewBuild` bleibt bei gegenteiliger Kennzeichen-Datei, Manifest und Systemeigenschaften beim Build-Wert; ohne
  Schalter enthalten die Handler der drei Mixins (Wasserzeichen, Pipeline, Fenstertitel) und die Einstiege von
  `PreviewMark` keinen Code, mit Schalter rufen sie `PreviewMark.extract`, `PreviewPipeline.lookup` und
  `PreviewBuild.TITLE` auf; `PreviewMark` zeichnet nur mit `GuiGraphicsExtractor#fill(PreviewPipeline.PIPELINE, …)`,
  nie mit der Vanilla-GUI-Pipeline, und kein Wasserzeichen-Code greift auf `RenderPipelines`, `ShaderManager` oder
  `PipelineCache` zu. `PreviewPipelineTest` lädt die Pipeline: zwei Shader im Namensraum `propwash`, beide als Quelltext
  aus dem Code ohne `#include`, der Fragment-Shader ohne `discard` und ohne Uniforms, die Quelle antwortet nicht für
  `core/gui` oder fremde Kennungen und liefert keine Includes, Bindungen wie die Vanilla-GUI und kein Tiefentest.
  `PreviewBuildTest` prüft den Fenstertitel. Über Gradle mit und ohne Schalter:
  `./gradlew :common:test --tests 'de.lardtom.propwash.preview.*' -Ppropwash_preview=true` (und ohne).
- Selbsttest `preview:` (10 Schritte, `preview.client.PreviewSelfTests`), einmal mit und einmal ohne Schalter, beide
  Loader (`docs/testing.md`, Abschnitt „Vorschau-Build“). Mit Schalter prüft er den Fenstertitel und das Wasserzeichen
  Bildpunkt für Bildpunkt gegen die Pixelschrift, auch mit einem Ressourcenpaket, das alle Propwash-Texte und die
  Standardschrift leert, `core/gui.fsh` durch einen Shader ersetzt, der Weiß und Schwarz mit Alpha 180/255 verwirft, und
  unter `propwash` eigene `core/preview_mark`-Shader ablegt, die alles verwerfen. Eine Probe-Fläche in den Farben des
  Wasserzeichens über die Vanilla-GUI-Pipeline belegt, dass der Paket-Shader wirkt; in jeder Szene muss der
  GUI-Renderer die eigene Pipeline abgefragt und die mit dem Shader aus dem Code übersetzte bekommen haben. Mit
  `-PpropwashScreenshots=true` und Schalter entstehen `docs/screenshots/preview_ingame.png`, `preview_ingame_f1.png`,
  `preview_menu.png`, `preview_resourcepack.png` und `preview_title.png`.
