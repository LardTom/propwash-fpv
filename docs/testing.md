# Tests: gefilterter Selbsttest, Schrittliste, Parallelbetrieb

Ergänzt `docs/FOUNDATION.md` §9 (Aufbau des Selbsttests, Rauchtests). Hier steht, wie man beim Entwickeln nur die
eigenen Schritte laufen lässt, wo die Präfixe stehen und was zwei gleichzeitige Minecraft-Instanzen beachten müssen.

## 1. Gefilterter Selbsttest

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest -PpropwashSelftestOnly='race:,tricks:'
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :neoforge:runClientSelftest -PpropwashSelftestOnly='osd:'
# Bildschirmfoto-Schritte gibt es nur auf Fabric und nur zusammen mit -PpropwashScreenshots=true:
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest -PpropwashScreenshots=true \
    '-PpropwashSelftestOnly=bildschirmfotos: Flugtraining'
```

- `-PpropwashSelftestOnly=<Präfixe>` wird zur System-Property `propwash.selftest.only` beider Loader. Präfixe durch
  Komma getrennt (`|` geht auch), Leerzeichen am Rand zählen nicht, Groß-/Kleinschreibung egal. Ein Schritt läuft, wenn
  sein Name mit einem der Präfixe beginnt. `!präfix` schließt aus, z. B. `'-PpropwashSelftestOnly=!bando,!bildschirmfotos'`
  (nur Ausschlüsse: alles andere läuft).
- `-PpropwashBandoSeed=<Seed>` wird zur System-Property `propwash.selftest.bandoSeed`: Die Bando-Schritte erzeugen ihre
  Welten und suchen ihre Plätze mit diesem Seed statt 20261003, z. B.
  `'-PpropwashSelftestOnly=bando: Welt mit Bauwerken,bando: Lage' -PpropwashBandoSeed=123456789` (etwa 2:30 min). Die
  Bildschirmfotos dazu überschreiben die des Test-Seeds in `docs/screenshots/`, also danach zurücksetzen.
- Präfixe mit Doppelpunkt angeben (`drone:`), sonst passt `drone` auch auf `drone-render:`. Ein Präfix darf länger sein
  als die Gruppe (`race: Gates an Wand`), dann laufen nur diese Schritte.
- Welt, Spawn und Einstellungen legt der Runner wie im vollen Lauf an (Flachwelt, Seed 1, Kreativ). Dazu laufen die
  Rahmen-Schritte, die mit eigenen Methoden angemeldet sind:
  - `SelfTest.baseStep(…)` läuft bei jeder Auswahl („dev: Propwash-Tasten auf Standard“, „dev: FPV-Darstellung auf Standard“, „dev: Kamera-Vibration aus (eigene Schritte schalten sie ein)“ – ein Test-Schalter in `FpvShake`, die Einstellung bleibt unverändert; Bildschirmfotos und Winkelprüfungen bleiben so ohne Zittern).
  - `SelfTest.setupStep(…)` läuft, sobald ein Schritt seiner Gruppe (Text vor dem ersten `:`) ausgewählt ist, an seiner
    Stelle in der Reihenfolge: „bildschirmfotos: Fenster und Szene vorbereiten“, „… Props im FPV-Bild für die bisherigen
    FPV-Bilder aus“, „bildschirmfotos: Aufräumen“ sowie „bildschirmfotos: Nahaufnahmen und FPV-Bilder – Fenster und Platz
    vorbereiten“ und „… – aufräumen“ (bis zum 08.10. als `bildschirmfotos (drone-render):` normale Schritte; gefiltert
    allein auf „bildschirmfotos: Guards im FPV-Bild“ scheiterten die Guard-Bilder dann mit „Platz nicht vorbereitet“);
    ebenso Vorbereiten und Aufräumen der Gruppen „bildschirmfotos (Hand-Items)“, „bildschirmfotos (Boden-Items)“ und
    „bildschirmfotos (Item-Rahmen)“ (bis zum 08.10. normale Schritte: gefiltert scheiterten die Item-Rahmen mit
    „Item-Rahmen-Wand nicht aufgebaut“, Hand- und Boden-Items liefen in einem Tick ohne Bild durch).
  - `SelfTest.switchStep(name, bis, …)` läuft, wenn zwischen ihm und dem Schritt `bis` ein ausgewählter Schritt liegt; der
    Schritt `bis` läuft dann mit. So stehen die drei Umschaltungen auf Deutsch samt „Sprache zurücksetzen“: ein
    gefiltertes `…_de`-Bild ist deutsch, die Sprache danach wieder englisch. Ebenso „drone: FPV-Kamera nie im Block:
    Prüfplatz mit Höhle, …“ bis „… aufräumen“: jedes einzeln gefilterte FPV- oder Ducts-Bild der Gruppe baut den Prüfplatz
    und räumt ihn wieder ab (bis zum 08.10. normaler Schritt; gefiltert allein auf „… Ducts im FPV-Bild“ scheiterten die
    vier Bilder mit „Prüfplatz nicht gebaut“).
- Neue Schritte, von denen andere Schritte einer Gruppe abhängen (Vorbereiten, Umschalten, Aufräumen), mit diesen
  Methoden anmelden, damit ein gefilterter Lauf dieselbe Ausgangslage hat. Abhängigkeiten über Gruppen hinweg
  (z. B. `werkbank:` braucht die deutsche Sprache aus `bildschirmfotos:`) deckt `switchStep` ab, sonst beide Präfixe
  angeben.
- Ergebnis: `Selbsttest (Fabric): PASS gefiltert: 23 von 547 Schritten (<Filter>)`. Passt der Filter auf keinen Schritt,
  endet der Lauf sofort ohne Welt mit `FAIL Filter '…' passt auf keinen Schritt`.
- Ein gefilterter Lauf ersetzt nie den vollen Lauf vor dem Zusammenführen oder Veröffentlichen (`docs/RELEASING.md`).
  Bildschirmfotos aus gefilterten Läufen können außerhalb der Szene leicht abweichen (Hand, Schnellleiste, Kleidung
  des Spielers stammen im vollen Lauf aus früheren Schritten); die Bilder für einen Commit kommen aus dem vollen Lauf.
- Die Umgebungsvariable `PROPWASH_SELFTEST_ONLY` gilt weiter; die Property hat Vorrang.
- Selbsttest-Instanzen starten mit Sichtweite 6 (`prepareRuns`). Der Server schickt Entities nur bis Sichtweite × 16 =
  96 Blöcke an den Client; Testbauten, deren Entities der Client sehen muss, stehen deshalb höchstens etwa 90 Blöcke
  vom Spieler entfernt. Steht in der `options.txt` eines Arbeitsbaums eine größere Sichtweite, fällt ein zu weit
  entfernter Testbau im gefilterten Lauf nicht auf, erst im vollen Lauf der Integration.
- Achtung, Grafik-Voreinstellung: In einem frischen Arbeitsbaum fehlt `graphicsPreset` in der `options.txt`; Minecraft
  nimmt dann „fancy“ und setzt beim Laden Sichtweite 16 und Simulationsweite 12 – die 6/5 aus `prepareRuns` gelten erst,
  wenn die Voreinstellung „custom“ ist (das wird sie, sobald ein Schritt eine Grafik-Einstellung ändert, also nach dem
  ersten vollen Lauf). Ein Testbau mehr als 80 Blöcke vom Spieler entfernt hat dann keine geladenen Entities mehr. So
  scheiterte die Iris-Prüfung (§5) nur in Arbeitsbäumen mit vollem Lauf; Testbauten, die weit weg entstehen, schicken
  den Spieler deshalb zuerst hin und warten, bis dort Entities geladen sind (`IrisSite.arrive`).
- Frisch zurückgeholter Spieler: Holt ein Schritt den Spieler von einem weit entfernten Testplatz zurück, sind im
  nächsten Tick erst die Chunks direkt um ihn geladen. Eine Bild-Szene, die relativ zum Spieler baut, wartet deshalb,
  bis die Chunks ihres Bauplatzes geladen sind und Entities ticken (`CourseEditSelfTests.EditScene.siteLoaded`). Im
  vollen Lauf mit Bildschirmfotos stehen die Bilder „Kurs bearbeiten“ direkt hinter den Gate-Stangen-Bildern (Platz
  rund 530 Blöcke entfernt); ohne das Warten landete das erste Gate in einem ungeladenen Chunk („0 neue Gates“).
  Gefiltert auf die eigenen Schritte fällt das nicht auf.
- Fenster für Bildschirmfotos: `Screenshots.resizeWindow` merkt sich Fenstergröße und GUI-Skala nur, wenn noch keine
  gemerkt ist, `Screenshots.restoreWindow` stellt sie zurück (beide schreiben die Größen ins Log). Zwei Bild-Schritte
  nacheinander mit nur einem gemeinsamen Zurückstellen („video: Aufnahme-Leistung – Bildschirmfoto Engpass-Hinweis“
  und „… Encoder-Anzeige“) überschrieben vorher die gemerkte Größe mit der schon vergrößerten: Das Fenster blieb für
  den Rest des vollen Laufs bei 1920 × 1080 (GUI 640 × 360) statt 854 × 480 (GUI 427 × 240), und „guide: Hinweise
  bleiben beim Drehen und Zoomen stehen“ scheiterte, weil der Hinweis in der größeren Ansicht einmal umzieht. Gefiltert
  auf eine der beiden Gruppen fällt das nicht auf.
- Diese Prüfung merkt sich außerdem GUI-Größe und Szenenfläche und misst neu, wenn sie sich zwischen zwei Messungen
  ändern (höchstens drei Neustarts); bei einem Umzug nennt die Meldung Ansicht und Szenenzeit.
- Oberfläche bei jeder GUI-Skala: `'-PpropwashSelftestOnly=ui-scale:'` (`UiScaleSelfTests`) stellt Fenster und Skala
  selbst um und danach zurück, Befunde stehen in `propwash-ui-scale.txt` im Run-Verzeichnis
  (`docs/design/ui-scaling.md`). Ein einzelnes Fenster: `'-PpropwashSelftestOnly=ui-scale: Fenster 854x480'`, dasselbe
  auf Deutsch: `'-PpropwashSelftestOnly=ui-scale: Deutsch: Fenster 854x480'` (stellt die Sprache um und zurück).
  Andere Fenstergrößen: `-PpropwashUiScaleWindows=770x480,640x1000@2` (Bildpunkte, Skala ohne Angabe 2). Nur einzelne
  Ansichten: `'-PpropwashUiScaleCases=Konfigurator im'` (Namensanfänge der Fälle aus `UiScaleCases`, durch Komma
  getrennt; wird zur System-Property `propwash.selftest.uiScaleCases`; passt keiner, scheitert der Schritt).
- GUI-Skala: `prepareRuns` setzt in Selbsttest- und Rauchtest-Instanzen `guiScale:0` (automatisch). Schritte, die für
  Bildschirmfotos eine Skala setzen (`Screenshots.guiScale`, Fenstergrößen der Flugschule), stellen sie danach zurück;
  wird ein Lauf aber mittendrin abgebrochen, blieb die Skala in der `options.txt` stehen. So lief am 08.10. ein voller
  Lauf mit Skala 2, und „guide: Hinweise bleiben beim Drehen und Zoomen stehen“ sah den Hinweis nach dem Zoomen
  umziehen (bei automatischer Skala wandern die Anker 13 bzw. 15 px, der Kasten steht).
- Der Lauf endet immer von selbst, auch nach FAIL oder Absturz (Regeln in `docs/FOUNDATION.md`, Selbsttest). Früher
  blieb ein Client nach einem Absturz während der Bando-Lage-Prüfung stehen: Der Server-Thread wartete in
  `getChunkFuture` auf einen Chunk, der nie fertig wurde, der Render-Thread im Notspeichern auf den Server
  (`IntegratedServer.halt` ← `Minecraft.emergencySave`); der Watchdog hätte erst 46 min nach dem Start gegriffen und
  nach einem schon geschriebenen Ergebnis gar nicht. Nachgestellt mit einer vorübergehenden Probe im gefilterten Lauf
  `-PpropwashSelftestOnly='bando: Welt mit Bauwerken,bando: Lage'` (am zweiten Platz der Lage-Prüfung einen Fehler
  melden, den Server-Thread in `managedBlock(() -> false)` schicken und den Client mit `delayCrash` abstürzen lassen):
  vorher hing der Prozess mit genau diesem Stack, bis er von Hand beendet wurde (nach 3:48 min); jetzt endet er 31 s
  nach dem Absturz mit `FAIL Spiel hängt in emergencySave …`. Eine zweite Probe verließ die Welt mitten in der
  Prüfung bei laufender Chunk-Anforderung: Der Schritt meldete `Server beendet, Prüfung bei … abgebrochen`, das Spiel
  beendete sich normal.

### Leistungsmessung der Action-Cam-Aufnahme

`-PpropwashSelftestOnly='video: Aufnahme-Leistung'` (`RecordingPerfSelfTests`) baut ein Testgelände aus 64 × 64
Blocksäulen neben dem Spieler, setzt eine Drohne mit Heavy-Cam ins FPV und fliegt sie Tick für Tick auf einem festen Kreis
(Lage direkt im Client-Simulator, gleiche Bahn in jedem Lauf). Gemessen wird 720p30 und 1080p60 mit dem
Propwash-Encoder, 1080p60 mit dem Hardware-Encoder (entfällt mit Logzeile, wenn die ffmpeg-Prüfung keinen findet) und
1080p60 mit einem einzigen Encoder-Thread als zu langsamer Rechner. Jeder Schritt schreibt die Messzeile von
`PipelineStats` ins Log (Spiel-FPS, Renderthread, GPU-Latenz, verlorene Bilder nach Ursache, Encoder-Tempo und Presets,
Warteschlange, Auslagerung, Heap, GC) und prüft: Video in Größe und Dauer richtig, kein verlorenes Bild,
Renderthread im Mittel unter 2 ms je Bild und im schlimmsten Bild (Auftrag oder Auslesen) unter 25 ms, beim
Hardware-Encoder Profil Main/High statt Constrained Baseline, beim knappen Encoder der Hinweis „Encoder“; meldet die
Aufnahme einen Engpass, darf ihr Tipp nicht das laufende Format vorschlagen (z. B. „720p“ oder „30 fps“ bei 720p30).
Jeder Messschritt prüft außerdem die Encoder-Anzeige: in den ersten Sekunden steht „Encoder: … (hardware, via ffmpeg)“
bzw. „Encoder: Propwash (CPU)“ in den Hinweisen, nach sechs Sekunden nicht mehr.
Mit Bildschirmfotos kommen `action_cam_bottleneck_hud`, `action_cam_encoder_hud` und
`configurator_action_cam` im 1920 × 1080-Fenster dazu – nach den Messschritten, damit die Messung im normalen Fenster
läuft. Werte hängen stark von der Last im zweiten Slot ab; für Vergleiche vorher/nachher beide Stände direkt
nacheinander laufen lassen (Zahlen in `docs/design/action-cam-recording.md`, „Messungen 0.5.0“). Für Messungen mit
Shaderpack wie in §5 Iris und Sodium ins Run-Verzeichnis legen.

### ffmpeg finden und anzeigen

`-PpropwashSelftestOnly='video: ffmpeg'` (`FfmpegSelfTests`, zwei Schritte, beide Loader): Der erste öffnet den Tab
*Action-Cam*, wartet die Prüfung ab und verlangt im Abschnitt „Schneller aufnehmen mit ffmpeg“ die passende Zeile
(gefunden mit Version, Pfad, Encoder und Testwert – oder „nicht gefunden“ mit Verweis auf die Anleitung), Pfadfeld,
„Test“ und „Guide“ sowie alle Texte der Anzeige in der Sprachdatei; ins Log schreibt er Zustand, Herkunft
(`CUSTOM`/`GAME_FOLDER`/`PATH`/`KNOWN`/`SHELL`), Pfad, Version und den `PATH` der Sitzung. Der zweite legt ein
Shell-Skript als falsches ffmpeg an (Version `0.0-selftest`, nur Software-Encoder) und prüft: Tippen allein testet
nichts, „Testen“ übernimmt den Pfad (Herkunft `CUSTOM`), eine Datei ohne Ausführungsrecht und ein falscher Pfad werden
gemeldet und fallen auf die automatische Suche zurück, `Enter` im Pfadfeld prüft, eine abgelegte Datei landet im Feld
und wird geprüft, der gespeicherte Pfad gilt für die Aufnahme, und die Aufnahme-Anzeige nennt den Grund für den
Propwash-Encoder (unter Windows übersprungen). Der erste Schritt öffnet außerdem über „Guide“ die Rückfrage zum Link
und kehrt in den Tab zurück.
`'-PpropwashSelftestOnly=bildschirmfotos: Action-Cam ffmpeg'` mit `-PpropwashScreenshots=true` liefert
`configurator_action_cam_ffmpeg` (Zustand dieses Rechners), `…_ffmpeg_missing` (Vorschau „nicht gefunden“) und
`…_ffmpeg_de`.

Start ohne den `PATH` des Terminals (wie aus dem Dock oder einem Launcher): das Slot-Skript ist ein zsh-Skript und liest
`~/.zshenv`, ein vorangestelltes `env PATH=…` wirkt deshalb nicht. Den `PATH` erst hinter dem Slot-Skript setzen:

```
<Slot-Skript> env PATH=/usr/bin:/bin:/usr/sbin:/sbin JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
    ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=video: ffmpeg,video: Aufnahme-Leistung'
```

Die Logzeile „ffmpeg im Tab: … | PATH der Sitzung: …“ zeigt, was das Spiel wirklich bekommen hat; die Herkunft muss dann
`KNOWN` heißen. JUnit dazu: `FfmpegDetectionTest` (Suchorte aller Systeme mit nachgebauten Ordnern, Anmelde-Shell mit
eigenem `HOME`, Versions- und Encoder-Liste, falsches Programm, echtes ffmpeg ohne Shell-`PATH` und über einen eigenen
Pfad – die beiden letzten entfallen ohne installiertes ffmpeg).

### Blackbox gegen die echten Werkzeuge

`tools/blackbox_check.sh` klont `betaflight/blackbox-tools` und `betaflight/blackbox-log-viewer` (Release-Tag
2025.12.1, wie auf blackbox.betaflight.com; anderer Stand mit `PROPWASH_EXPLORER_TAG`) nach
`build/blackbox-check/src`, baut `blackbox_decode`, installiert `esbuild` und `semver` lokal nach
`build/blackbox-check/node`, schreibt mit `BlackboxFixtureTest` Logs aus dem Flugmodell und vergleicht jedes Feld jedes
Frames aus `blackbox_decode` und aus dem Parser des Blackbox Explorers mit dem Flugmodell (Ende: `BLACKBOX-PRÜFUNG OK`,
etwa 2 min ohne Klonen). Der Explorer-Release greift schon beim Laden auf die Browser-Globals `$` (jQuery) und `semver`
zu; `tools/blackbox/explorer_check.mjs` setzt dafür einen kleinen jQuery-Ersatz und das `semver`-Paket. Zusätzlich
muss der Explorer jedes Log als Betaflight (Firmware-Typ 3) in Version 4.5.1 erkennen; ein Explorer-Abbruch oder eine
fehlende CSV-Ausgabe ist ein Fehler. Braucht git, make, cc, node, npm und python3; nichts wird global installiert. Selbsttest:
`-PpropwashSelftestOnly='blackbox:'`, Bildschirmfotos `'bildschirmfotos: Blackbox'` (`docs/design/blackbox.md`).

### Echte Blackbox-Logs gegen das Flugmodell

`tools/blackbox_calibration.sh` lädt öffentliche Betaflight-Logs in den ignorierten Cache `.cache/bbcal`, dekodiert sie
mit `blackbox_decode`, fliegt Schweben, Punch und Gas weg mit derselben Motor-Duty im Flugmodell nach und schreibt
`build/bbcal/report.md` (rund 20 s ohne Laden). `roundtrip` prüft den Weg mit Logs aus dem eigenen Blackbox-Schreiber.
Alles Weitere: `docs/tools/blackbox-calibration.md`.

## 2. Schrittliste

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest -PpropwashScreenshots=true -PpropwashSelftestList=true
```

`-PpropwashSelftestList=true` (System-Property `propwash.selftest.list`) startet den Client bis zum Titelbildschirm,
legt keine Welt an, schreibt `runs/selftest-client/propwash-selftest-steps.md` des Loaders (Gruppen-Tabelle und alle
Schritte in Reihenfolge, Rahmen-Schritte markiert) und gibt die Gruppen-Tabelle aus; Dauer etwa 12 s. Jeder normale
Selbsttest schreibt dieselbe Datei. Die Liste entsteht aus den wirklich angemeldeten Schritten; „angemeldet in“ ist die
Klasse, die den Schritt anmeldet.

Stand integration nach Welle 3 (Fabric mit Bildschirmfotos, 658 Schritte; NeoForge 395, dort ohne Bildschirmfotos):

| Präfix | in der Welt | nach dem Verlassen | angemeldet in |
|---|---:|---:|---|
| `dev:` | 5 | 0 | DevClientBootstrap |
| `core:` | 9 | 1 | CoreSelfTests |
| `content:` | 15 | 0 | ContentSelfTests, PaintSelfTests, ItemSizeSelfTests |
| `drone-render:` | 32 | 0 | PaintSelfTests, DroneRenderSelfTests, DroneItemSelfTests, LedSelfTests, PartsRenderSelfTests, FpvCoverageSelfTests, PartsRenderShowcase |
| `bildschirmfotos (Boden-Items):` | 3 | 0 | GroundItemsShowcase |
| `bildschirmfotos (Hand-Items):` | 4 | 0 | HandItemsShowcase |
| `survival:` | 5 | 0 | SurvivalSelfTests, SurvivalRecipeBookSelfTest |
| `parts:` | 7 | 0 | PartsSelfTests |
| `addon-sitzung:` | 3 | 0 | AddonSessionSelfTests über JoinSelfTests (Beispiel-Addon nur beim Client inaktiv, Schutz der Kreativ-Pakete, Trennen; mit `-PpropwashScreenshots=true` dazu drei Bildschirmfotos und der Sprachwechsel, `docs/design/client-only-addons.md` §7) |
| `beitritt:` | 3 | 0 | JoinSelfTests (die Übersicht scrollt mit Mausrad, Tasten und Scrollleiste, ein echter Linksklick öffnet den Link; mit `-PpropwashScreenshots=true` dazu drei Bildschirmfotos und der Sprachwechsel, `docs/design/join-check.md` §7) |
| `bildschirmfotos (Item-Rahmen):` | 14 | 0 | ItemFrameShowcase |
| `ui:` | 11 | 3 | LedSelfTests, UiSelfTests, OverlayMessageSelfTests (lange Meldungen über der Schnellleiste brechen um), ArmAngleSelfTests, ThemeSelfTests |
| `bildschirmfotos (drone-render):` | 2 | 0 | PartsRenderShowcase |
| `bildschirmfotos:` | 239 | 0 | PartsRenderShowcase, CrashFallSelfTests, AutonomousRescueSelfTests, InteractSelfTests, ShowcaseTour, WizardRemoteSelfTests, SwitchCommandSelfTests, GateMountSelfTests, ProTrickShowcase, ShadowSelfTests, RelayShowcase, HeadlightShowcase, TrainingShowcase, ApiShowcase, FlashbackCamShowcase, Mode3dShowcase, CreativeShowcase, GateFreeSelfTests, CourseElementSelfTests |
| `dust:` | 3 | 0 | DustSelfTests |
| `trails:` | 9 | 0 | TrailSelfTests (Drohnen-Trails, `docs/design/drone-trails.md` §4; mit Bildschirmfotos dazu 9 Schritte `bildschirmfotos: Drohnen-Trails – …`) |
| `drone:` | 73 | 1 | DroneSelfTests, CrashFallSelfTests, PlayLoopSelfTests, LedSwitchSelfTests, FlightModeSelfTests, Mode3dSelfTests, WearWeightSelfTests, CameraAngleSelfTests, CameraClipSelfTests, GroundContactSelfTests (`drone: Bodenkontakt`: jede Drohnen-Vorlage in 14 Lagen auf 9 Flächen, mit `-PpropwashScreenshots=true` dazu zehn Bildschirmfotos `bildschirmfotos: Bodenkontakt`, `docs/design/ground-contact.md`) |
| `bindung:` | 13 | 0 | BindSelfTests (Pilot im Empfänger, Brille tragen statt binden, falsche Brille; Bildschirmfotos `bildschirmfotos: Bindung – …` im Rundgang) |
| `parts-flight:` | 7 | 0 | PartsFlightSelfTests |
| `teile-freiheit:` | 3 | 0 | PartFreedomSelfTests |
| `race:` | 49 | 1 | RaceSelfTests, WorldCourseSelfTests, GateStackSelfTests, GateMountSelfTests, GateKeySelfTests, GateFreeSelfTests, CourseElementSelfTests, GateRedstoneSelfTests (`race: Redstone`, `docs/design/gate-redstone.md` §5) |
| `flughilfe:` | 3 | 0 | AssistedLapSelfTests (Welt-Regel „Runden mit Flughilfe speichern“, `docs/design/assisted-laps.md` §5) |
| `server:` | 16 | 1 | ServerSelfTests, WorldRulesSelfTests (Schwerkraft 0–1000 % getippt: `docs/design/realism/flight-feel-gravity.md` §10.6) |
| `weltrand:` | 1 | 0 | GenerationEdgeSelfTests (Welt-Regel „Drohnen erzeugen neues Gelände“, Grenzzaun am Rand, `docs/design/world-edge.md` §6) |
| `rescue:` | 12 | 0 | RescueSelfTests, AutonomousRescueSelfTests |
| `input:` | 27 | 0 | InputSelfTests, BindingSelfTests, OnboardingSelfTests, ThrottleWizardSelfTests, WizardRemoteSelfTests, SwitchCommandSelfTests, MacControllerSelfTests, MouseSelfTests (Maus + Tastatur, `docs/design/mouse-control.md` §6; mit Bildschirmfotos dazu 6 Schritte `bildschirmfotos: Maus + Tastatur, …`) |
| `relay:` | 4 | 0 | RelaySelfTests |
| `video:` | 32 | 5 | VideoSelfTests, HeadlightSelfTests, HeadlightNightSelfTests, ActionCamSelfTests, FlashbackCamSelfTests, SpectateSelfTests, ShadowSelfTests, CameraFovSelfTests, AspectSelfTests, VideoTabSelfTests (`video: Video-Tab`, Bildschirmfoto `bildschirmfotos: Video-Tab ohne Brille`) |
| `kamera:` | 8 | 0 | FpvFeelSelfTests (Latenzkette Knüppel bis Bild, Kamera-Vibration nur im Bild, Treffer-Neigung des Piloten nicht im FPV-Bild, bei hohem Tempo bleibt das Bild ruhig – Vollgas-Steigflug mit dem 5″ Freestyle, je Bild Kamera zu Rahmen, Posen-Zeitfehler und Drehrate, dasselbe mit leicht abgenutzten Teilen (Props 95 %, Motoren 97 %) und als Gegenprobe mit einem verbogenen Prop; die Schritte schalten selbst scharf und laufen auch einzeln hinter „Testdrohne im FPV“; mit `-PpropwashScreenshots=true` dazu `bildschirmfotos: Kamera-Vibration aus und an (Bildfolge)` nach `docs/screenshots/camera_shake/`, von git ignoriert; `docs/design/fpv-feel.md`) |
| `iris:` | 1 | 0 | IrisSelfTests (ohne Iris übersprungen; mit Iris dazu 14 Schritte `bildschirmfotos: Iris – …`, §5) |
| `audio:` | 4 | 1 | AudioSelfTests, AudioReachSelfTests |
| `osd:` | 6 | 1 | OsdSelfTests |
| `tricks:` | 7 | 1 | TrickSelfTests, ProTrickSelfTests |
| `interact:` | 12 | 0 | InteractSelfTests |
| `blockbruch:` | 11 | 0 | BlockBreakSelfTests (Welt-Regel „Drohnen zerbrechen Blöcke“, `docs/design/drone-break-blocks.md` §6; ohne Bildschirmfotos 9) |
| `glasbruch:` | 18 | 0 | GlassFeelSelfTests (Durchbruch durch Glas im FPV-Flug: Tempoverlust, Drehstoß, Kameraschlag, Scherben, `docs/design/drone-break-blocks.md` §3.2; dazu fünf Anflüge aus 50 Blöcken – dreimal bei 30 Bildern/s, einmal bei 60 und durch eine Glasröhre –, bei denen die Scheibe mindestens 15 Blöcke vorher freigegeben sein muss; ohne Bildschirmfotos 16) |
| `kreativ-drohne:` | 6 | 0 | CreativeSelfTests |
| `guide:` | 25 | 2 | GuideSelfTests, TrainingSelfTests, TrainingCourseSelfTests |
| `api:` | 18 | 0 | ApiSelfTests, ApiAdditionSelfTests, ApiClientSelfTests, ApiLootSelfTests |
| `assets-world:` | 6 | 0 | WorldAssetsSelfTests |
| `werkbank:` | 1 | 0 | ShowcaseTour |
| `bando:` | 6 | 13 | BandoSelfTests, BandoWorldTests, BandoPlacementTests |

Konfigurator-Hover (`ui: Konfigurator-Hover`, `ui.client.HoverSweepSelfTests`, beide Loader): öffnet jeden
Konfigurator-Tab ohne und mit simulierter Funke mit sehr langem Namen, bei der Fenstergröße des Laufs, bei 427 × 240
(kleinste Größe) und bei 960 × 540, dazu mit „Maus + Tastatur“ als Eingabe bei 427 × 240, und zeichnet den Screen für jede Mausposition im 6-px-Raster über das ganze Fenster
neu, samt Tooltips; Tabs mit mehr Inhalt werden bis zu sechs Seiten weit gescrollt. Jeder Tab-Absturz und jede
Ausnahme beim Zeichnen lässt den Schritt mit Tab, Seite, Mausposition und den ersten Zeilen des Fehlerberichts
fehlschlagen. Der Schritt „Empfänger-Status zeigt den ganzen Namen“ prüft, dass die Statuszeile den Gerätenamen ungekürzt über
mehrere Zeilen zeigt (kein Tooltip nötig), und fährt danach die echte Maus darauf. Dauer je Größe etwa 10–20 s (90 000–210 000 Positionen).

Flugschul-Hinweise (`guide: Hinweise …`, `guide.client.GuideSelfTests`, beide Loader): „bleiben beim Drehen und Zoomen
stehen“ hält die Lektion „Überlebensmodus“ an, dreht die Ansicht um 14°, dann um −6° mit 7° Neigung und 15 % Zoom und
verlangt, dass jeder Kasten an seiner Stelle bleibt, während die Anker wandern (Bilder `flight_school_labels_turn_0…2`).
„ruhig in allen Lektionen (abspielen, drehen, zoomen)“ spielt jede angemeldete Lektion – auch die von Addons, etwa Just More
Parts aus `runs/selftest-client/mods` – mit vierfacher Geschwindigkeit ab, dreht und zoomt dabei jeden Tick und prüft in
jedem Tick: Zeigelinie am projizierten Anker, Ziel jedes Kastens in der Ansicht, ohne Überlappung, ohne verdeckten Anker
und frei von Tastenhinweisen; am Ende keine zwei Hinweise für dieselbe Stelle und kein Ausblenden; je Lektion höchstens
`2 × Hinweise + 6` Umzüge und kein Kasten schneller als 40 px je Tick. Mit Bildschirmfotos hält der Schritt bei 30 % und
65 % und am Ende an, lässt die Hinweise 40 Ticks zur Ruhe kommen und legt die Bilder unter
`docs/screenshots/flight_school_labels/` ab (von git ignoriert, nur zur Durchsicht).

## 3. Zwei Instanzen gleichzeitig

Auf dem Entwicklungsrechner dürfen höchstens zwei Minecraft-Prozesse zugleich laufen. Ein Slot-Skript außerhalb des
Repositorys vergibt die Slots und setzt `PROPWASH_MC_SLOT=1` bzw. `2`; jeder `run*`-Task läuft über dieses Skript.

- Run-Verzeichnisse liegen je Arbeitsbaum (`fabric/runs/…`, `neoforge/runs/…`), ebenso Welten, Ergebnisdateien,
  Schrittliste und `docs/screenshots`. Zwei Arbeitsbäume kommen sich dort nicht in die Quere.
- Ports der Server-Rauchtests (`:fabric:runServerSmoke`, `:neoforge:runServerSmoke`): `-PpropwashServerPort=<n>` setzt
  den Fabric-Port, NeoForge nimmt `n + 1`. Ohne Property gilt `25600 + 10 × PROPWASH_MC_SLOT + 1` (Fabric) bzw. `+ 2`
  (NeoForge), also 25611/25612 in Slot 1 und 25621/25622 in Slot 2; ohne Slot 25601/25602. `prepareServerSmoke`
  schreibt den Port in `server.properties`, meldet ihn im Log und bricht vor dem Start ab, wenn der Port belegt ist.
  Der Selbst- und der Client-Rauchtest öffnen keinen Port (integrierter Server ohne LAN).
- `org.gradle.parallel` bleibt aus: zwei `run*`-Tasks in einem Gradle-Aufruf laufen nacheinander, sonst stünden zwei
  Spiele in einem Slot.
- Zwei Gradle-Aufrufe im selben Arbeitsbaum nicht gleichzeitig starten (gemeinsame `build/`- und Run-Verzeichnisse).
- Unter Last (zweites Spiel, Gradle) dauert der Start einer FPV-Sitzung manchmal länger als 8 s. Schritte, die viele
  Sitzungen nacheinander starten, warten deshalb auf die Sitzung länger als auf das Bild: „drone-render: Props im
  FPV-Bild …“ wartet je Drohne bis 300 Ticks, startet die Sitzung dann einmal neu und meldet erst nach dem zweiten
  Versuch „keine FPV-Sitzung“ (vorher eine feste Grenze von 160 Ticks, einmal im vollen NeoForge-Lauf parallel zu
  Fabric überschritten).

### Testfenster ohne Mausgriff und Fokus

Selbsttest- und Rauchtest-Clients (`runClientSelftest`, `runClientSmoke` beider Loader) laufen im Hintergrund, während
am Rechner gearbeitet wird. Sie fassen weder den Zeiger des Systems noch den Fokus an. Für Spieler und für `runClient`
ändert sich nichts: alles liegt im Quell-Set `devtest`, in der Hilfs-Mod `propwash_devtest` und im Build.

- **Ursache des springenden Zeigers.** Minecraft 26.3 greift die Maus über `InputConstants.grabMouse`/`releaseMouse` –
  aus `MouseHandler` bei jedem Öffnen und Schließen eines Bildschirms, aus `Window.onResize` bei jeder Größenänderung
  mit gegriffener Maus. Beide rufen `SDL_WarpMouseInWindow`, und SDL setzt den Zeiger des Systems auch dann in die
  Fenstermitte, wenn das Fenster nicht vorn liegt. `Window.focused` beginnt mit `true` und kippt erst mit einem
  Fokus-Ereignis: ein Fenster, das nie den Fokus hatte, hält sich für aktiv und greift weiter. Gemessen an einem
  Selbsttest alter Bauart im Hintergrund: 60 Sprünge in die Fenstermitte in 75 s, das Terminal blieb durchgehend vorn.
- **Kein Mausgriff.** `mixin/dev/client/InputConstantsMixin` bricht `grabMouse` und `releaseMouse` am Anfang ab – kein
  `SDL_WarpMouseInWindow`, kein relativer Mausmodus, egal von wo der Aufruf kommt. Der Zustand im Spiel bleibt derselbe
  (`MouseHandler.isMouseGrabbed()`, `setScreen(null)`, Zeiger in der Fenstermitte); die Schritte merken nichts.
- **Gedachter Zeiger.** `MouseHandler.resyncMousePosition` liest sonst nach jedem Bildschirmwechsel die Position des
  echten Zeigers. Im Testlauf liefert `dev.client.DetachedMouse` die Stelle des letzten abgefangenen Griffs, vor dem
  ersten die Fenstermitte (`mixin/dev/client/MouseHandlerMixin`). Dort lag der echte Zeiger bisher, solange niemand die
  Maus bewegte – Bildschirmfotos bleiben gleich, und wo der echte Zeiger steht, spielt keine Rolle mehr.
- **Echte Maus und Fokus erreichen das Spiel nicht.** `DetachedMouse.install()` schaltet in SDL die Ereignisse
  Mausbewegung, Maustasten, Mausrad, Zeiger betritt/verlässt das Fenster und Fokus gewonnen/verloren ab
  (`SDL_SetEventEnabled`). Wer mit dem Zeiger über das Testfenster fährt oder hineinklickt, dreht keine Kamera und
  löst keinen Hover aus. Die Schritte speisen ihre Eingaben direkt in `MouseHandler` und `MouseCapture` ein und sind
  davon nicht betroffen; das Fenster hält sich durchgehend für aktiv.
- **Kein Fokus beim Start.** Die vier Runs setzen `SDL_MAC_BACKGROUND_APP=1` und `SDL_WINDOW_ACTIVATE_WHEN_SHOWN=0`
  (`propwashTestWindowEnv` im Wurzel-Build). Unter macOS bekommt der Prozess kein Dock-Symbol und lässt sich nicht
  aktivieren (Aktivierungsart „prohibited“ statt „regular“); das Fenster öffnet sich hinter dem aktiven Fenster und
  rendert normal. Als Umgebungsvariablen gelten die Hinweise vor dem ersten Fenster, bei NeoForge also schon für das
  frühe Ladefenster. `pauseOnLostFocus:false` schreibt weiterhin `prepareRuns` in die `options.txt`.
- **Zuschauen.** `-PpropwashTestWindowFront=true` lässt die beiden Hinweise weg: das Fenster startet wie ein normaler
  Client mit Dock-Symbol und lässt sich nach vorn holen. Den Zeiger greift der Lauf auch dann nicht; Tastendrücke im
  aktiven Testfenster kommen aber im Spiel an.
- **Anmeldung der Mixins.** Mixins aus `devtest` brauchen eine Mod, die ihre Config beim Start anmeldet –
  `Mixins.addConfiguration` aus dem Mod-Start kommt zu spät (Mixin wählt nachgemeldete Configs nur, solange noch keine
  Klasse umgewandelt wurde). Das übernimmt die Hilfs-Mod `propwash_devtest` im Quell-Set `devtestMod`
  (`fabric/src/devtestMod`, `neoforge/src/devtestMod`, gemeinsame `propwash.dev.mixins.json` in
  `common/src/devtestMod`). Fabric lädt sie in den Client-Läufen mit `devtest` (`"environment": "client"`), NeoForge
  nur in `clientSelftest` und `clientSmoke` (`loadedMods`); die Mixins greifen ohnehin erst mit `propwash.selftest`
  oder `propwash.smoketest`. Die Hilfs-Mod nennt Propwash bewusst nicht als Abhängigkeit: jede Mod, die von Propwash
  abhängt, gilt als Add-on und stünde in der Beitritts-Übersicht („beitritt: Übersicht nach einer Absage …“ schlägt
  dann fehl).
- **Absicherung.** Der Basis-Schritt „dev: Testfenster ohne Mausgriff und Fokus“ läuft in jedem gefilterten und vollen
  Selbsttest; am Ende jedes Laufs, auch im Rauchtest, prüft `SelfTestRunner` noch einmal. Fehlt die Config oder sind
  die SDL-Ereignisse wieder an, endet der Lauf mit FAIL. Das Log nennt die Zahl der abgefangenen Griffe:
  `[Selbsttest] 17 Mausgriffe und 17 Freigaben ohne Systemzeiger`.
- **Messung.** Zeiger und vorderste App während eines gefilterten Selbsttests von außen abgetastet (alle 20 ms):
  Fabric `input:` (30 Schritte, 73 s) und `race:` (83 Schritte, 43 s), NeoForge `input:` (30 Schritte, 75 s) – Zeiger
  vor, während und nach dem Lauf an derselben Stelle, keine einzige Bewegung, vorderste App unverändert, Testprozess
  durchgehend „prohibited“ und nie aktiv. Dieselben 63 Bildschirmfoto-Schritte mit altem und neuem Stand: Oberfläche,
  Tooltips und Hover pixelgleich (Unterschiede nur im Welt-Hintergrund, in der drehenden Drohnen-Vorschau und im
  ffmpeg-Status, der im Hintergrund ermittelt wird), Laufzeit 131 s gegen 132 s. Voller Fabric-Lauf mit
  Bildschirmfotos: 1041 Schritte PASS in 66:06 min (vorher 1040 in 65:49 min), 1066 Griffe und 1012 Freigaben
  abgefangen – so oft wäre der Zeiger sonst gesprungen; in den abgetasteten 50 min stand er still.

### Beitrittstest mit zwei Prozessen (Server und Client mit verschiedenen Mods)

Prüft, was ein Spieler beim Verbinden sieht, wenn Server und Client andere Add-ons oder Protokolle haben
(`docs/design/join-check.md`). Belegt beide Minecraft-Slots: erst den Server starten, den Client erst, wenn der Server
auf seinem Port lauscht (der Build ist dann aktuell, die beiden Gradle-Aufrufe teilen sich nur noch die Ausgaben).

- Server: `PROPWASH_JOINTEST_HOLD=<sekunden> ./gradlew :fabric:runServerSmoke -PpropwashServerPort=<n>` – der
  Server-Rauchtest schaltet die Whitelist aus und läuft, bis `jointest.stop` im Run-Ordner liegt oder die Zeit um ist
  (eigener Wächter-Thread, ein leerer 26.3-Server macht nach 60 s keine Ticks mehr).
- Client: `PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=<fall> ./gradlew :fabric:runClientSmoke` – der
  Rauchtest-Client verbindet sich vom Titelbildschirm aus, wartet auf das Ergebnis (Welt betreten oder ein stehender
  Bildschirm), schreibt Klasse, Titel, alle Texte und `detailsText()` nach `runs/smoke-client/jointest/<fall>.txt`
  und ein Bildschirmfoto `<fall>.png`. `PROPWASH_JOINTEST_EXPECT=<a>|<b>|!<c>` verlangt Textstellen (ohne
  Groß-/Kleinschreibung, `!` = darf nicht vorkommen, `joined` = Beitritt geklappt).
- Pilot auf dem dedizierten Server (`docs/design/pilot-body.md`): `PROPWASH_JOINTEST_PILOT=1` am Server lässt 3 s nach dem
  Beitritt eine FPV-Sitzung 8 Blöcke über dem Boden im Überlebensmodus starten (`dev.PilotJoinCheck`, Log
  `[Beitrittstest] Pilot…`); `PROPWASH_JOINTEST_PILOT=<sekunden>` am Client wartet auf die Sitzung, bleibt so lange im
  FPV und schreibt `pilot fpv held|ended`, `body landed|floating` und `body hurt|unhurt` dazu. Erwartung:
  `PROPWASH_JOINTEST_EXPECT='joined|pilot fpv held|body landed|body hurt|!flying'`. Der Server braucht eine lange
  `PROPWASH_JOINTEST_HOLD` (z. B. 1800), weil der Client auf einen freien Slot warten kann; `jointest.stop` beendet ihn.
- Ausstattung je Prozess: `PROPWASH_DEV_EXAMPLE=off` lässt das Beispiel-Addon weg (auch auf dem Server und in der
  Einzelspielerwelt: seine Beute-Einspeisung `propwash_example:inject/simple_dungeon` hat Ladebedingungen beider Loader
  und fehlt dann, statt das Laden der Register mit einem unbekannten Item abzubrechen), `PROPWASH_DEV_PROTOCOL=<n>` meldet ein
  anderes Netzwerkprotokoll (auf NeoForge auch für den Kanal-Abgleich), echte Add-ons als Jar in `runs/smoke-server/mods`
  bzw. `runs/smoke-client/mods` (z. B. Just More Parts 1.0.0 und 1.1.0 für den Versionsfall).
- Die Port-Regel oben gilt: NeoForge nimmt `n + 1`.
- Nach einem Beitritt (`joined`) schreibt der Client den Zustand der Add-on-Sitzung dazu: `session_inactive=<namensräume>`,
  `session_addons=…`, `session_parts=<n>`, `visible_inactive_parts|scenes|workbench|tabs|creative=<n>` (alles 0, wenn
  nichts Inaktives durchrutscht) und `toast=…`. Fälle für Add-ons nur beim Spieler
  (`docs/design/client-only-addons.md`): Spieler mit Just More Parts auf einem Server ohne erwartet
  `joined|session_inactive=justmoreparts|visible_inactive_parts=0|…`, umgekehrt `Install Propwash: Just More Parts 1.1.0|!joined`,
  beide mit Just More Parts `joined|!session_inactive=justmoreparts`. Danach beide `logs/latest.log` auf Fehler prüfen.

## 4. Build-Geschwindigkeit

- **Build-Cache an** (`org.gradle.caching=true` in `gradle.properties`, Cache unter `~/.gradle/caches/build-cache-1`
  für alle Arbeitsbäume): Kompilieren (main, devtest) und Javadoc kommen bei gleichen Eingaben aus dem Cache, auch im
  ersten Lauf eines neuen Arbeitsbaums. Gemessen: `assemble compileDevtestJava` nach `clean` 24 s ohne, 7 s mit
  Cache-Treffern. Tests (`Test`-Tasks) kommen nie aus dem Cache, weil sie auch Dateien lesen, die keine Task-Eingaben
  sind (`docs/`, `common/src/lang`, Addon-Daten über `PROPWASH_CALIBRATION_ADDON`).
- **Konfigurations-Cache aus**: Die eigenen `doFirst`/`doLast`-Blöcke (Ergebnisprüfung, Dev-Spieler, `prepareRuns`,
  `prepareServerSmoke`) greifen zur Ausführungszeit auf `project`/`gradle` zu; Gradle meldet dafür schon bei
  `prepareRuns prepareServerSmoke` 7 Probleme. Die Konfiguration dauert mit laufendem Daemon nur etwa 0,4 s, der Umbau
  lohnt nicht.
- **Keine vorab erzeugte Selbsttest-Welt**: Anlegen und Betreten der Flachwelt dauert 3–5 s; eine gespeicherte Welt
  könnte Zustand aus älteren Ständen mitbringen (Gamerules, Entities, Datenversion) und die Schritte verfälschen.

Gemessene Zeiten (Gradle-Aufruf über das Slot-Skript, Daemon warm, Code schon kompiliert):

| Lauf | Schritte | Dauer |
|---|---:|---:|
| Fabric, Liste (`-PpropwashSelftestList=true`) | – | 12 s |
| Fabric mit Bildschirmfotos, gefiltert `tricks:,osd:,bildschirmfotos: Werkbank mit FREESTYLE` | 23 von 547 | 47 s |
| NeoForge, gefiltert `tricks: Konfigurator,osd:` | 9 von 354 | 26 s |
| Fabric, Server-Rauchtest | – | 12 s |
| Fabric mit Bildschirmfotos, voll | 547 | etwa 18 min (Spiel 18:15 min) |
| Fabric ohne Bildschirmfotos, voll (Stand 0.5.0-Vorbereitung, 375 Schritte) | 375 | 8:30 min |
| NeoForge, voll (339 Schritte) | 339 | 7:35 min |
| Fabric mit Bildschirmfotos, voll (nach Welle 2) | 622 | 22 min (in der Welt 19:15 min) |
| NeoForge, voll (nach Welle 2) | 383 | 11 min |
| Fabric mit Bildschirmfotos, voll (nach Welle 3) | 658 | 23:48 min (in der Welt 21:09 min) |
| NeoForge, voll (nach Welle 3) | 395 | 11:21 min (in der Welt 10:03 min) |
| Fabric mit Bildschirmfotos, voll (Release 0.5.0, parallel zu NeoForge im zweiten Slot) | 658 | 23:30 min |
| NeoForge, voll (Release 0.5.0, eigener Arbeitsbaum im zweiten Slot) | 395 | 11:33 min |
| Fabric mit Bildschirmfotos, voll (Release 0.5.0 mit Freiform-Gate, parallel zu NeoForge im zweiten Slot) | 673 | 24:03 min |
| NeoForge, voll (Release 0.5.0 mit Freiform-Gate, eigener Arbeitsbaum im zweiten Slot) | 402 | 11:39 min |
| Fabric mit Bildschirmfotos, voll (Release 0.5.0 mit Owner-Feedback, parallel zu NeoForge im zweiten Slot) | 690 | 25:58 min |
| NeoForge, voll (Release 0.5.0 mit Owner-Feedback, eigener Arbeitsbaum im zweiten Slot) | 408 | 11:47 min |
| Fabric mit Bildschirmfotos, gefiltert `bando:` (mit den Schritten `bando: Lage – …`) | 20 von 676 | 4:29 min |
| Fabric mit Bildschirmfotos, gefiltert `bando:` (Bandos im Gelände mit `beard_box`) | 20 von 690 | 4:36–5:11 min |
| Fabric mit Bildschirmfotos, voll (Release 0.5.0 mit Bandos im Gelände, parallel zu NeoForge im zweiten Slot) | 690 | 26:02 min |
| Fabric mit Bildschirmfotos, gefiltert `bando:` (Lage-Prüfung an allen 168 Plätzen) | 20 von 690 | 5:44–5:47 min |
| Fabric ohne Bildschirmfotos, gefiltert `bando:` | 17 von 408 | 3:16 min |
| NeoForge, gefiltert `bando:` | 17 von 408 | 3:08 min |
| Fabric mit Bildschirmfotos und Iris, gefiltert `iris:,bildschirmfotos: Iris` | 19 von 704 | 1:36–1:44 min |
| NeoForge, voll (Release 0.5.0 mit Bandos im Gelände, eigener Arbeitsbaum im zweiten Slot) | 408 | 11:49 min |
| Fabric mit Bildschirmfotos, gefiltert `ui: Regler tippen,ui: Zahlen in Rates,ui: Einstellungssuche,drone: Kamerawinkel,video: Sichtfeld` und die Bilder `bildschirmfotos: Regler GPS-Rettung`/`… Lautstärke in Prozent` (alle Regler tippen: 238 Ticks) | 17 von 712 | 43 s |
| dasselbe mit `rescue:` | 29 von 712 | 2:30 min |
| NeoForge, gefiltert `ui: Regler tippen,ui: Zahlen in Rates,ui: Einstellungssuche,drone: Kamerawinkel` | 7 von 415 | 42 s |
| Fabric ohne Bildschirmfotos, gefiltert `ui:,osd:,input:,audio:,race:,drone: Spielablauf,drone: Kamerawinkel,video: Sichtfeld` | 123 von 415 | 2:17 min |
| NeoForge, derselbe Filter | 123 von 415 | 2:20 min |
| Fabric, gefiltert `bando: Welt mit Bauwerken,bando: Lage` (Lage-Prüfung 168 Plätze mit Tickets statt Laden im Server-Thread, ohne Bildschirmfotos) | 5 von 412 | 2:35 min |
| Fabric mit Bildschirmfotos, voll (Release 0.5.0 mit Einstellungs-Fixes, parallel zu NeoForge im zweiten Slot) | 705 | 28:34 min |
| NeoForge, voll (Release 0.5.0 mit Einstellungs-Fixes, eigener Arbeitsbaum im zweiten Slot) | 412 | 13:45 min |
| Fabric mit Bildschirmfotos, gefiltert `bando:` (Bandos auf jedem Seed: Auffüllen, Ausbessern, `avoid_overlap`, Kamera mit freier Sicht) | 20 von 705 | 6:40 min |
| Fabric, gefiltert `bando: Welt mit Bauwerken,bando: Lage` mit `-PpropwashBandoSeed` (123456789, −5872310457, 987654321987, 42; 142–160 Plätze) | 5 von 412 | 2:13–2:40 min |
| Fabric, gefiltert `bando: Welt mit Bauwerken,bando: Lage` mit `-PpropwashBandoSeed` (Nachprüfung 0.5.0: 20261003, 123456789, −5872310457, 987654321987, 42; 143–166 Plätze, zweiter Slot belegt) | 5 von 431 | 2:03–2:36 min |
| dasselbe mit Bildschirmfotos (314159, 77777777, −98765; 156–179 Plätze) | 6 von 737 | 3:45–4:30 min |
| Fabric, gefiltert `video: Aufnahme-Leistung` (mit Prüfung des schlimmsten Bilds und des Tipps je Format) | 8 von 431 | 1:33 min |
| Fabric mit Bildschirmfotos, voll (Release 0.5.0 nach der Nachprüfung, im zweiten Slot zuerst Bando-Seeds, dann NeoForge) | 737 | 32:59 min |
| NeoForge, voll (derselbe Stand, eigener Arbeitsbaum im zweiten Slot) | 431 | 16:01 min |
| NeoForge, gefiltert `bando:` (Bandos auf jedem Seed) | 17 von 412 | 3:48 min |
| Fabric mit Bildschirmfotos, gefiltert `video: Aufnahme-Leistung` | 9 von 698 | 1:45 min |
| NeoForge, gefiltert `video: Aufnahme-Leistung,video: Action-Cam,video: Drohne mit Action-Cam` | 17 von 415 | 1:44 min |
| Fabric mit Bildschirmfotos, voll (Release 0.5.0 mit Signal-Repeater, Action-Cam-Aufnahme, Empfänger-Tooltip, Bandos auf jedem Seed und tippbaren Reglern, parallel zu NeoForge im zweiten Slot) | 737 | 31:56 min |
| NeoForge, voll (derselbe Stand, eigener Arbeitsbaum im zweiten Slot) | 431 | 15:57 min |
| Fabric mit Bildschirmfotos, gefiltert `content:,bildschirmfotos (Hand-Items):` mit dem Jar von Just More Parts 1.1.0 in `fabric/runs/selftest-client/mods` (Item-Größen und Motoren in der Hand auch für Add-on-Teile) | 20 von 737 | 1:18 min |
| Fabric ohne Bildschirmfotos, gefiltert `audio:` (mit „audio: Hörweite je Drohnengröße“, 670 Ticks) | 6 von 432 | 52 s |
| NeoForge, derselbe Filter | 6 von 432 | 54 s |
| Fabric mit Bildschirmfotos und Just More Parts 1.1.0 in `fabric/runs/selftest-client/mods`, gefiltert `guide: Hinweis,guide: alle Szenen,guide: Bildschirmfotos` (Flugschul-Hinweise: 23 Lektionen samt Addon-Lektionen vierfach schnell, drehend und zoomend; 258 Hinweise, 83 Umzüge) | 6 von 739 | 8:38 min |
| NeoForge mit Just More Parts 1.1.0 in `neoforge/runs/selftest-client/mods`, gefiltert `guide: Hinweis,guide: alle Szenen` (258 Hinweise, 103 Umzüge) | 5 von 433 | 5:10 min |
| Fabric mit Bildschirmfotos, gefiltert `drone-render: Guards im FPV-Bild,drone-render: Props im FPV-Bild,drone-render: Prop-Scheiben,bildschirmfotos (drone-render),bildschirmfotos: Guards im FPV-Bild,bildschirmfotos: Nahaufnahme,bildschirmfotos: Props im FPV-Bild,bildschirmfotos: drehende Props,bildschirmfotos: Werkbank mit WHOOP,bildschirmfotos: Werkbank mit CINEWHOOP` mit dem Jar von Just More Parts in `fabric/runs/selftest-client/mods` (Ducts im FPV-Bild: 18 Analysen, 12 FPV-Bilder, 6 Nahaufnahmen) | 42 von 759 | 2:25 min |
| NeoForge, gefiltert `drone-render: Guards im FPV-Bild,drone-render: Props im FPV-Bild,drone-render: Prop-Scheiben` | 8 von 433 | 35 s |
| Fabric mit Bildschirmfotos und Just More Parts 1.1.0 in `fabric/runs/selftest-client/mods`, gefiltert `drone-render: Guards im FPV-Bild,bildschirmfotos: Guards im FPV-Bild,bildschirmfotos: Nahaufnahme` (Vorbereiten und Aufräumen laufen als Rahmen-Schritte mit) | 27 von 456 | 1:18 min |
| Fabric, gefiltert `bando:` mit `-PpropwashBandoSeed=77031` (160 Plätze, Förderanlage erst in Ring 18) | 18 von 456 | 3:24 min |
| Fabric mit Bildschirmfotos, gefiltert `trails:,bildschirmfotos: Drohnen-Trails` (ohne Iris 1:02 min, mit Iris und Complementary nach §5 0:54 min) | 25 | 1 min |
| Fabric mit Bildschirmfotos, voll (Release 0.5.0 vom 08.10., in der Welt 36:56 min, NeoForge-Lauf im zweiten Slot) | 813 | 43:00 min |
| NeoForge, voll (Release 0.5.0 vom 08.10., Fabric-Lauf im zweiten Slot) | 456 | 18–21 min |
| Fabric mit Bildschirmfotos, gefiltert `drone: Bodenkontakt,bildschirmfotos: Bodenkontakt` (8 Drohnen-Vorlagen × 9 Flächen × 14 Lagen, zehn Bilder; mit Just More Parts 1.1.0 und einem weiteren Add-on in `mods` 29 Vorlagen in 2:00 min) | 23 | 1:21–1:48 min |
| Fabric mit Bildschirmfotos, Iris, Sodium und Complementary Reimagined, gefiltert `iris:,bildschirmfotos: Iris,drone: Bodenkontakt,bildschirmfotos: Bodenkontakt` (Bodenkontakt mit aktivem Shaderpack, Bilder nur gesichtet) | 38 | 4:34 min |
| NeoForge, voll (Zweig `fix/ground-clip`: Bodenkontakt, weiche Flächen, lose Drohnen; zweiter Slot belegt) | 530 | 24 min |
| Fabric mit Bildschirmfotos, gefiltert `video: ffmpeg,video: Aufnahme-Leistung,video: Action-Cam,video: Kamera-Akku,video: Drohne mit Action-Cam,video: Aufnahmen-Liste,video: Heavy-Cam,video: Tab Action-Cam,bildschirmfotos: Action-Cam ffmpeg`, gestartet ohne den `PATH` des Terminals | 38 von 938 | 2:07 min |
| NeoForge, derselbe Filter ohne die Bildschirmfotos, dazu `ui:` | 50 von 527 | 3:37 min |
| Fabric mit Bildschirmfotos, gefiltert `race: Kurs bearbeiten,bildschirmfotos: Kurs bearbeiten` (Runden je Kurs, Fenster „Kurs bearbeiten“, vier Bilder) | 23 von 943 | etwa 1:30 min |
| Fabric mit Bildschirmfotos, gefiltert `race:,flughilfe:,relay:` und die Renn-Bilder (`bildschirmfotos: Gate-Kurs`, `Ideallinie`, `Geist`, `Whoop-Geist`, `Bestenliste`, `Rennen`, `Kurs`, `Slalomstangen`, `Wendeflagge`, `Tauchwürfel`) | 119 von 943 | etwa 3:30 min |
| NeoForge, gefiltert `race:,flughilfe:` | 86 von 534 | etwa 2 min |
| Fabric mit Bildschirmfotos, voll (Release 0.5.0 Runde 5 mit neuer Bindung, Kurs bearbeiten, Glasbruch, Bodenkontakt, ffmpeg-Abschnitt und GUI-Skalen; parallel zu NeoForge im zweiten Slot) | 1008 (977 + 31) | 52:21 min |
| NeoForge, voll (derselbe Stand) | 574 (545 + 29) | 28:47 min |
| Fabric mit Bildschirmfotos, voll (nach der Gegenprüfung: deutsche GUI-Prüfung, Anflüge aus 50 Blöcken, Kamera mit abgenutzten Teilen, Hülle gegen Netz; NeoForge und Rauchtests zeitweise im zweiten Slot) | 1032 (1001 + 31) | 56:30 min, davon 50:31 min in der Testwelt |
| NeoForge, voll (nach der Gegenprüfung, parallel zu Fabric) | 598 (569 + 29) | 33 min |
| Fabric mit Bildschirmfotos, gefiltert `ui-scale:` (zehn Größen und Sprachen × 115 Ansichten: dazu Modi-Zeilen, längster Schritt je Szene, Gate-Editor beide Seiten mit und ohne freie Maus) | 31 | 7:20 min |
| Fabric mit Bildschirmfotos, voll (nach der zweiten Gegenprüfung; NeoForge und Rauchtests im zweiten Slot) | 1032 (1001 + 31) | 57:45 min, davon 51:43 min in der Testwelt |
| NeoForge, voll (nach der zweiten Gegenprüfung, parallel zu Fabric) | 598 (569 + 29) | 34 min |
| Fabric mit Bildschirmfotos, gefiltert `ui-scale:` (zehn Größen und Sprachen × 164 Ansichten: dazu Konfigurator mit simulierter Funke samt Modi-Zeilen und mit Maus-Steuerung) | 31 | 9:45 min |
| Fabric, gefiltert `ui-scale:` mit `-PpropwashUiScaleWindows=770x480,684x512,912x512,640x1000,1920x800` (fünf Zwischengrößen, Englisch und Deutsch) | 15 | etwa 10 min |
| Fabric mit Bildschirmfotos, voll (nach der dritten Gegenprüfung; NeoForge und Rauchtests im zweiten Slot) | 1032 (1001 + 31) | 60:07 min, davon 54:02 min in der Testwelt |
| NeoForge, voll (nach der dritten Gegenprüfung, parallel zu Fabric) | 598 (569 + 29) | 36:29 min |
| Fabric mit Bildschirmfotos, gefiltert `ui-scale:` (zwölf Größen und Sprachen × 171 Ansichten: dazu Fenster 800 × 600, gefüllte Bestenliste, Kurs mit 14 Gates, Drohne mit langer Funke oder Maus, OSD-Optionen, Welt-Regeln ohne Schreibrecht; blättert auch durch Ansichten ohne angeschnittenen Text) | 39 | 12:31 min |
| Fabric mit Bildschirmfotos, voll (nach der vierten Gegenprüfung; NeoForge und Rauchtests im zweiten Slot, zeitweise ein dritter Client eines anderen Arbeitsbaums) | 1040 (1009 + 31) | 63:28 min, davon 57:21 min in der Testwelt (`ui-scale:` 12:26 min) |
| NeoForge, voll (nach der vierten Gegenprüfung, parallel zu Fabric) | 606 (577 + 29) | 40:30 min, davon 35:55 min in der Testwelt |
| Fabric mit Bildschirmfotos, gefiltert `ui-scale:,input: Assistent fragt bei` mit `-PpropwashUiScaleWindows=640x480@2,854x480@2,770x480@2,800x600@2,960x540@2,1280x720@2` (sechs Größen, Englisch und Deutsch × 186 Ansichten: dazu Funke nach dem Öffnen angesteckt, Rückfragen des Empfänger-Assistenten, leere Bestenliste) | 20 | etwa 15:30 min |
| Fabric mit Bildschirmfotos, gefiltert `ui-scale:` mit `-PpropwashUiScaleWindows=640x480@2,1920x1080@4,854x480@2` (drei Größen, Englisch und Deutsch × 194 Ansichten: dazu acht ffmpeg-Zustände im Tab „Action-Cam“) | 13 | etwa 8 min |
| Fabric mit Bildschirmfotos, voll (nach der fünften Gegenprüfung; NeoForge, Rauchtests und Add-on-Läufe im zweiten Slot) | 1040 (1009 + 31) | 65:49 min, davon 59:52 min in der Testwelt (`ui-scale:` 15:22 min mit 194 Ansichten) |
| NeoForge, voll (nach der fünften Gegenprüfung, parallel zu Fabric) | 606 (577 + 29) | 42:13 min, davon 38:51 min in der Testwelt |
| Fabric mit Bildschirmfotos, voll (mit Testfenster ohne Mausgriff und Fokus; gefilterte NeoForge- und Fabric-Läufe und Rauchtests im zweiten Slot) | 1041 (1010 + 31) | 66:06 min, davon 60:05 min in der Testwelt; 1066 Mausgriffe und 1012 Freigaben abgefangen |
| Fabric mit Bildschirmfotos, gefiltert `ui-scale:` mit `-PpropwashUiScaleCases='Konfigurator im'` und zwanzig Fenstern über `-PpropwashUiScaleWindows` (Englisch und Deutsch × 8 Ansichten in der Pilotensitzung) | 48 | 2:37 min |
| Fabric mit Bildschirmfotos, gefiltert `ui-scale:` (sechzehn Größen und Sprachen: zwölf × 202 Ansichten, dazu 384 × 240 und 456 × 256 Punkte auf Englisch und Deutsch × 38 Ansichten mit Funke und Pilotensitzung) | 44 | etwa 17 min; mit allen 202 Ansichten auch in den beiden Zwischengrößen 21:25 min |
| Fabric mit Bildschirmfotos, voll (nach der sechsten Gegenprüfung; NeoForge und Rauchtests im zweiten Slot) | 1045 (1014 + 31) | 67:37 min, davon 61:38 min in der Testwelt (`ui-scale:` 17:06 min); 1142 Mausgriffe und 1084 Freigaben abgefangen |
| NeoForge, voll (nach der sechsten Gegenprüfung, parallel zu Fabric) | 611 (582 + 29) | 43:39 min, davon 40:21 min in der Testwelt; 926 Mausgriffe und 906 Freigaben abgefangen |
| Fabric mit Bildschirmfotos, gefiltert `ui-scale:` mit `-PpropwashUiScaleWindows=640x480@2,854x480@2` (zwei Größen, Englisch und Deutsch × 202 Ansichten: dazu je Ansicht die Maus über jedem Widget, Text und Slot und der Zug an jeder Scrollleiste) | 12 | etwa 6 min; die Tooltips kosten je Größe und Sprache 6–8 s für rund 2050 Tooltips |
| Fabric mit Bildschirmfotos, voll (nach der siebten Gegenprüfung; NeoForge, Rauchtests und Add-on-Läufe im zweiten Slot) | 1046 (1015 + 31) | 67:44 min, davon 61:43 min in der Testwelt (`ui-scale:` 17:14 min mit Tooltips und Scrollleisten); 1142 Mausgriffe und 1084 Freigaben abgefangen |
| NeoForge, voll (nach der siebten Gegenprüfung, parallel zu Fabric) | 612 (583 + 29) | 43:33 min, davon 40:14 min in der Testwelt (`ui-scale:` 16:17 min); 926 Mausgriffe und 906 Freigaben abgefangen |

Zeitgrenze: Der Lauf in der Testwelt endet nach 80 min (`SelfTestRunner.GLOBAL_TIMEOUT_TICKS`; bis zur siebten
Gegenprüfung 70 min), der Wächter beendet das Spiel nach 96 min (vorher 86). Der volle Fabric-Lauf mit Bildschirmfotos braucht davon inzwischen 61:43 min; deshalb prüft
`ui-scale:` die beiden Zwischengrößen nur mit 38 statt 202 Ansichten (`docs/design/ui-scaling.md`). Mit 50 min Grenze brach der volle Fabric-Lauf mit Bildschirmfotos nach 981 von 1001 Schritten
ab („Zeitüberschreitung in Phase …“), die Bando-Schritte nach dem Verlassen der Welt scheiterten dann an der fehlenden
Vorarbeit. Wer dem vollen Lauf weitere lange Schritte gibt, prüft die Laufzeit.

Blackbox-Ordner: Jeder Lauf schreibt Logs nach `<Run-Verzeichnis>/propwash/blackbox`. Nach mehreren Läufen im selben
Arbeitsbaum erreichte der Ordner die Speichergrenze von 512 MB (1133 Logs); der Tab „Blackbox“ zeigte dann die Zeile
über gelöschte alte Logs, passte nicht mehr ohne Scrollen ins Bild, und die Schritte „bildschirmfotos: Blackbox – Tab
mit Logs“ scheiterten. Der Rahmen-Schritt `dev: Blackbox-Logs früherer Läufe entfernen` leert den Ordner deshalb zu
Beginn jedes Selbsttests.

Zeitflanke im Bildschirmfoto „Drohne nach dem Absturz an der Mauer am Boden“: Der Schritt prüfte direkt nach dem
Fensterwechsel, ob die Drohne liegt, und scheiterte unter Last einmal, als sie noch mit 0,4 m/s nachrutschte. Er wartet
jetzt bis zu 100 Ticks; liegt sie dann nicht, scheitert er wie vorher.

## 5. Iris-Prüfung (lokal, nichts davon ins Repository)

Iris, Sodium und Shaderpacks gehören nicht in den Build und nicht ins Repository. Für die Prüfung mit Shaderpack
kommen sie nur in das Run-Verzeichnis des Selbsttests (`fabric/runs/` ist ignoriert) und werden danach wieder entfernt,
damit spätere Läufe ohne Iris laufen. Shaderpacks wie Complementary erlauben die private Nutzung, nicht die
Weitergabe.

```sh
R=fabric/runs/selftest-client
mkdir -p $R/mods $R/shaderpacks $R/config
cp <Ordner>/iris-fabric-1.11.7+mc26.3.jar <Ordner>/sodium-fabric-0.9.2+mc26.3.jar $R/mods/
cp <Ordner>/ComplementaryReimagined_r5.9.3.zip $R/shaderpacks/
printf 'shaderPack=ComplementaryReimagined_r5.9.3.zip\nenableShaders=true\ndisableUpdateMessage=true\n' > $R/config/iris.properties
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest -PpropwashScreenshots=true \
    '-PpropwashSelftestOnly=iris:,bildschirmfotos: Iris'
rm $R/mods/iris-*.jar $R/mods/sodium-*.jar
```

- Fabric Loader lädt die Jars aus `mods/` auch im Entwicklungslauf (26.3 ist nicht obfuskiert, kein Remapping).
- `iris:` prüft den Schattenpass (ohne Iris oder ohne aktives Pack übersprungen), `bildschirmfotos: Iris` schaltet das
  Pack über `IrisApiConfig#setShadersEnabledAndApply` an, nimmt die Bilder `iris_*_shader.png`, schaltet es aus,
  nimmt `iris_*_vanilla.png` und stellt den Ausgangszustand wieder her. Das Pack wählt `config/iris.properties`.
- Der Platz am See liegt etwa 200 Blöcke vom Spieler entfernt. Bevor See, Gates und Item-Rahmen entstehen, fährt der
  Spieler hin und wartet, bis die Chunks dort Entities führen (höchstens 400 Ticks); vorher endete der Lauf in einem
  Arbeitsbaum nach vollem Lauf mit „Gate bei … nicht gefunden: 0“ (siehe §1, Grafik-Voreinstellung).
- Dauer etwa 1:30 min (19 Schritte). Ohne Iris melden sich die Bildschirmfoto-Schritte gar nicht an; die Bilder in
  `docs/screenshots/iris_*.png` stammen deshalb aus diesem lokalen Lauf, nicht aus dem vollen Selbsttest.
- NeoForge: Iris 1.11.7 für NeoForge stürzt im NeoForge-Entwicklungslauf mit Shaderpack ab (Iris-intern, siehe
  `docs/design/shader-packs.md` §4). Den Warnbildschirm beim Laden (veraltetes `logoFile` von Iris) übergeht der
  Selbsttest-Runner selbst.
