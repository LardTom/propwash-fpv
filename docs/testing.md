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
- Bando-Karte: `'-PpropwashSelftestOnly=bando: Karte,bando: Welt ohne,bando: Welt mit'` legt die beiden Bando-Welten an
  (ohne die übrigen Bando-Schritte) und prüft Handel und Truhen darin; nur `bando: Karte` scheitert mit „keine Welt“.
- `#von-bis` (oder `#n`) wählt Schritte nach ihrer Stelle in der Schrittliste (§2; 1-basiert, „In der Welt“ und „Nach
  dem Verlassen der Welt“ durchgezählt), z. B. `'-PpropwashSelftestOnly=#1-704,#1185-1205'`; mit Präfixen mischbar,
  `!#von-bis` schließt aus, Rahmen-Schritte laufen wie bei Präfixen. Die Stellen gelten nur für genau diese Liste
  (gleicher Stand, gleiche Schalter wie `-PpropwashScreenshots`, gleiche Jars in `mods/`); der Selbsttest in Teilen
  (§6) holt sie deshalb vor jedem Lauf neu. `SelfTestSelectionTest` (JUnit) und `tools/selftest_shards_test.py`
  prüfen die Auswahl mit denselben Fällen.
- Präfixe mit Doppelpunkt angeben (`drone:`), sonst passt `drone` auch auf `drone-render:`. Ein Präfix darf länger sein
  als die Gruppe (`race: Gates an Wand`), dann laufen nur diese Schritte.
- Welt, Spawn und Einstellungen legt der Runner wie im vollen Lauf an (Flachwelt, Seed 1, Kreativ). Dazu laufen die
  Rahmen-Schritte, die mit eigenen Methoden angemeldet sind:
  - `SelfTest.baseStep(…)` läuft bei jeder Auswahl („dev: Propwash-Tasten auf Standard“, „dev: FPV-Darstellung auf Standard“, „dev: Kamera-Vibration aus (eigene Schritte schalten sie ein)“ – ein Test-Schalter in `FpvShake`, die Einstellung bleibt unverändert; „dev: Objektiv flach (eigene Schritte schalten Fisheye ein)“ – Test-Schalter `FpvLens#testStrength` auf 0 %, das Video-Tab zeigt weiter den Standard 80 %; Bildschirmfotos und Winkelprüfungen bleiben so ohne Zittern; „dev: Hinweis zur freien Maus im Gate-Editor gilt als bekannt, Start-Hinweis aus (eigene Schritte schalten beides ein)“ – der Hinweis oben im Panel pulsiert sonst die ersten drei Male, und das Start-Feld stünde je nach Laufzeit halb ausgeblendet in den Bildern).
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
- Grafik-Voreinstellung: `prepareRuns` schreibt in Selbsttest- und Rauchtest-Instanzen auch `graphicsPreset:"custom"`
  (seit 0.6.0); 6/5 gelten damit schon beim ersten Start eines frischen Arbeitsbaums. Ohne diesen Eintrag nahm Minecraft
  „fancy“ und setzte beim Laden Sichtweite 16 und Simulationsweite 12, bis ein Schritt eine Grafik-Einstellung änderte –
  ein Testbau mehr als 80 Blöcke vom Spieler entfernt hatte dann keine geladenen Entities mehr (so scheiterte die
  Iris-Prüfung, §5, nur in Arbeitsbäumen mit vollem Lauf). Testbauten, die weit weg entstehen, schicken den Spieler
  weiter zuerst hin und warten, bis dort Entities geladen sind (`IrisSite.arrive`). Geprüft wird die Voreinstellung im
  Schritt „drehwelt: Selbsttest-Optionen greifen“ (§7).
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
- Namen der Bildschirmfotos: `Screenshots.capture` bereinigt jeden Namen je Ordnerstufe (`Screenshots.fileName`), damit
  sich `docs/screenshots/` auch unter Windows auschecken lässt. `< > : " \ | ? *` und Steuerzeichen werden zu `_`,
  ebenso Punkt oder Leerzeichen am Ende einer Stufe; reservierte Namen wie `CON`, `NUL`, `COM1` oder `LPT1` bekommen
  ein `_` angehängt; `/` bleibt der Trenner für Unterordner, gültige Namen bleiben gleich. „bildschirmfotos:
  Konfigurator-Tabs“ macht aus Tab-Ids mit Namensraum flache Namen (`ShowcaseTour.configuratorShot`:
  `propwash:sharecode` → `configurator_propwash_sharecode.png`), die Tab-Id selbst bleibt. JUnit
  `ShowcaseTourShotNameTest` prüft beides und dass kein Name unter `docs/` unter Windows ungültig ist (auch
  ungetrackte Dateien eines Laufs mit Bildschirmfotos zählen).
- Oberfläche bei jeder GUI-Skala: `'-PpropwashSelftestOnly=ui-scale:'` (`UiScaleSelfTests`) stellt Fenster und Skala
  selbst um und danach zurück, Befunde stehen in `propwash-ui-scale.txt` im Run-Verzeichnis
  (`docs/design/ui-scaling.md`). Ein einzelnes Fenster: `'-PpropwashSelftestOnly=ui-scale: Fenster 854x480'`, dasselbe
  auf Deutsch: `'-PpropwashSelftestOnly=ui-scale: Deutsch: Fenster 854x480'` (stellt die Sprache um und zurück).
  Andere Fenstergrößen: `-PpropwashUiScaleWindows=770x480,640x1000@2` (Bildpunkte, Skala ohne Angabe 2). Nur einzelne
  Ansichten: `'-PpropwashUiScaleCases=Konfigurator im'` (Namensanfänge der Fälle aus `UiScaleCases`, durch Komma
  getrennt; wird zur System-Property `propwash.selftest.uiScaleCases`; passt keiner, scheitert der Schritt). Die
  Zwischenfenster 768x480 und 912x512 prüfen nur die Fälle, die dort laufen sollen (`Case#between`); enthält die Auswahl
  keinen davon, melden sie „keine Ansicht geprüft“. Dann die Zwischenfenster ausschließen:
  `'-PpropwashSelftestOnly=ui-scale:,!ui-scale: Fenster 768x480,!ui-scale: Fenster 912x512,!ui-scale: Deutsch: Fenster 768x480,!ui-scale: Deutsch: Fenster 912x512'`.
- Flashback (nur Fabric): Liegt `Flashback-0.43.6-for-MC26.3.jar` in `fabric/runs/selftest-client/mods/`, laufen die
  Flashback-Schritte mit echter Aufnahme und Wiederholung, sonst nur die Schritte „ohne Flashback“. Gezielt:
  `'-PpropwashSelftestOnly=video: Action-Cam (Flashback),video: Drohne mit Action-Cam (Flashback),video: Tab Action-Cam passt,video: Flashback-Budget,bildschirmfotos: Fenster für die Flashback,bildschirmfotos: Fenster nach der Flashback'`
  (etwa 2 min). Die Wiederholung misst dabei die Eckpunkte von Flashbacks Zeitleiste bei UI-Skalierung 1 und 2
  (`FlashbackBudgetSelfTests#measureInReplay`), „Spline-Nachbildung“ schreibt `propwash-flashback-spline-golden.txt`
  ins Run-Verzeichnis (Quelle von `common/src/test/resources/flashback/catmull_rom_golden.txt`).
- GUI-Skala: `prepareRuns` setzt in Selbsttest- und Rauchtest-Instanzen `guiScale:0` (automatisch). Schritte, die für
  Bildschirmfotos eine Skala setzen (`Screenshots.guiScale`, Fenstergrößen der Flugschule), stellen sie danach zurück;
  wird ein Lauf aber mittendrin abgebrochen, blieb die Skala in der `options.txt` stehen. So lief am 08.10. ein voller
  Lauf mit Skala 2, und „guide: Hinweise bleiben beim Drehen und Zoomen stehen“ sah den Hinweis nach dem Zoomen
  umziehen (bei automatischer Skala wandern die Anker 13 bzw. 15 px, der Kasten steht).
- Sprache: `prepareRuns` setzt in Selbsttest- und Rauchtest-Instanzen auch `lang:en_us`. Deutsche Bildschirmfotos
  schalten die Sprache nur im Spiel um (`Screenshots.language`, zurück mit `Screenshots.restoreLanguage`); speichert
  dazwischen etwas die Spieleinstellungen, steht sonst `lang:de_de` in `options.txt`. So startete am 22.10. ein voller
  Lauf auf Deutsch, und „rescue: Konfigurator-Tab bei 427x240“, beide „video: ffmpeg …“ und „ui: Einstellungssuche …“
  scheiterten. Gezielt nachprüfen: in `options.txt` `lang:de_de` eintragen, dann
  `'-PpropwashSelftestOnly=rescue: Konfigurator-Tab,video: ffmpeg,ui: Einstellungssuche'` (12 Schritte, startet auf Englisch).
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
Renderthread im Mittel unter 2 ms je Bild und im 99. Perzentil von Auftrag und Auslesen höchstens 25 ms
(`RecordingStallCheck`), beim Hardware-Encoder Profil Main/High statt Constrained Baseline, beim knappen Encoder der
Hinweis „Encoder“; meldet die Aufnahme einen Engpass, darf ihr Tipp nicht das laufende Format vorschlagen (z. B. „720p“
oder „30 fps“ bei 720p30). Liegen mehr als 1 % der Bilder über 25 ms (bei 1080p60 über 8 s ab 5 von rund 480), scheitert
der Schritt mit „Renderthread hängt: 99. Perzentil …“; einzelne langsamere Bilder darunter stehen nur als „Ausreißer im
Renderthread, kein Fehler“ im Log. Bis 0.6.0-dev.10 galt die Grenze für das schlimmste Bild; im vollen Lauf mit wenig
freiem Speicher (60 MB frei, 7 GB Swap) scheiterte so ein einzelnes Auslesen von 26,95 ms bei 0,30 ms im Mittel. Jede
Messung schreibt dazu eine Zeile „Renderthread-Spitzen“ mit Anzahl über 25 ms, p99 und Maximum von Auftrag und Auslesen
und dem Speicherstand (System frei, Swap belegt, Heap-Spitze, GC während der Aufnahme).
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

### Akku-Entladekurven

`DischargeCurveTest` prüft das Akku-Modell gegen Fixtures in
`common/src/test/resources/de/lardtom/propwash/sim/discharge_curves.txt` (eine Zeile je Kurve: Name, Preset oder
Chemie, C-Rate – 0 heißt Ruhespannung –, Schlussspannung je Zelle, dann `Anteil=Volt±Toleranz`), dazu Form, C-Raten,
Li-Ion und den Punch am Ende des Packs; `battery_curves_0_5_1.txt` hält die Kurven von 0.5.1 zum Vergleich.
`VoltageDisplayTest` prüft die geglättete, blinkende OSD-Spannung. Den Plot `docs/design/realism/battery-discharge.svg`
schreibt `PROPWASH_BATTERY_PLOT=docs/design/realism/battery-discharge.svg tools/fastcheck.sh test --select-class
de.lardtom.propwash.sim.BatteryCurvePlot` neu (ohne die Variable wird der Test übersprungen).

### Drohnen-Tags

Die Entity-Tags für Server-Admins (`docs/design/drone-tags.md`) prüft der Server-Rauchtest bei jedem Lauf auf dem
dedizierten Server (`drone.DroneTagChecks`, Zeile „Drohnen-Tags: …“ im Log; `:fabric:runServerSmoke` rund 10 s in der
Testwelt). Im echten Flug mit Tod des Piloten und Failsafe-Heimflug prüft sie `'-PpropwashSelftestOnly=pilotenkörper:'`
(15 Schritte, rund 30 s in der Testwelt), die Regeln JUnit
`tools/fastcheck.sh test --select-class de.lardtom.propwash.drone.tag.DroneTagRulesTest`.

### Flughilfe je Pilot

Die Erkennung von Flughilfe (`docs/design/assisted-laps.md` §6) prüft der Server-Rauchtest bei jedem Lauf mit vier
Piloten auf dem dedizierten Server (`race.AssistServerChecks`, Zeile „Flughilfe je Pilot: …“ im Log): sauber,
Kreativ-Drohne, Teile ohne „Teile müssen zusammenpassen“ und Halten plus Brillen-Latenz vom Client, drei Runden, in
Runde 2 persönliche Schwerkraft 130 % je Pilot (seit rc.3, Grund `personal_rules`; Serverregeln machen keine Runde mehr
assisted); jede Runde muss genau mit ihren Gründen in der Wertung landen. Im Spiel:
`'-PpropwashSelftestOnly=flughilfe'` (alte und neue Schritte, rund 20 s in der Testwelt); mit echtem Client auf dem
dedizierten Server der Beitrittstest unten mit `PROPWASH_JOINTEST_ASSIST=1` an beiden Prozessen. JUnit:
`tools/fastcheck.sh test --select-class de.lardtom.propwash.race.AssistCheckTest` (dazu `AssistTimelineTest`,
`AssistReasonsTest`).

### Homepoint im NBT

`HomePoint` an der Drohne (`docs/design/home-point.md`) prüft der Server-Rauchtest nach 20 Ticks im festgehaltenen
Spawn-Chunk (`rescue.HomePointChecks`, Zeile „Homepoint: …“ im Log; Meldung des Piloten, Ablehnungen, `/data get`,
`merge`, `modify`, `remove`, Speichern und Laden). Im echten Flug mit Scharfschalten, `/data merge` beim Piloten und
Neuladen der Drohne: `'-PpropwashSelftestOnly=rescue: Homepoint im NBT'` (4 Schritte, rund 3 s in der Testwelt), das
Bildschirmfoto dazu mit `'-PpropwashSelftestOnly=rescue: Homepoint im NBT,bildschirmfotos: Homepoint im NBT'
-PpropwashScreenshots=true` (`home_point_command.png`). Die Regeln: `tools/fastcheck.sh test --select-class
de.lardtom.propwash.rescue.HomePointRulesTest --select-class de.lardtom.propwash.rescue.HomeSyncTest --select-class
de.lardtom.propwash.rescue.HomePointCodecsTest`.

### Spinnweben, Fäden, Ranken und Glocken

`'-PpropwashSelftestOnly=verfangen:,glocke:'` (`interact.client.SnagSelfTests`, beide Loader, rund 30 s in der
Testwelt): Block-Tags bei Server und Client, 5-Zoll mit 18 m/s in eine Spinnwebenwand (gefangen ohne Absturz, Hinweis,
Prop-Verschleiß am Server, mit Vollgas frei), Wirrranken mit 12 m/s (leicht gebremst, Blätter an den Props), Glocke
läutet (Statistik, leuchtender Plünderer, Schwierigkeit kurz auf „Leicht“, falls die Welt friedlich ist), langsames
Anstupsen läutet nicht. Mit `-PpropwashScreenshots=true` (nur Fabric) `snag_cobweb.png`, `snag_vines.png`,
`drone_bell.png` und `drone_bell_raider.png`; der Spieler steht dafür kurz neben der Drohne bzw. der Glocke. JUnit:
`tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.SnagFlightTest --select-class de.lardtom.propwash.interact.SnagRulesTest`
(`docs/design/drone-snags.md`).

### Zonen

Zonen (`docs/design/zones.md` §8) prüft der Server-Rauchtest bei jedem Lauf auf dem dedizierten Server
(`zone.ZoneServerChecks`, Zeile „Zonen: …“ im Log): Gast ohne Recht abgelehnt (Paket, Wiederholungen, Befehl), Operator
legt zwei Zonen an, Sync je Dimension, Weltdaten verlustfrei, Server-Gründe an drei Drohnen (rennfrei sauber,
Übungsplatz mit Zonennamen, außerhalb sauber). Im Spiel: `'-PpropwashSelftestOnly=zonen:'` (fünf Schritte, rund 20 s
in der Testwelt; „zonen: Tab Zonen …“ prüft, dass Überschrift und „Neue Zone hier“ im Tab „Zonen“ ohne Scrollen zu
sehen sind, auch bei größter GUI-Skala, dass die Suche mit „zone“, „Zone“, „zones“ und dem Zonennamen in den Tab führt
und dass ohne Operator-Recht keine fremden Zonen und keine Knöpfe erscheinen; Überschrift und Knopf in jeder
Fenstergröße bis 320 × 240 und in beiden Sprachen prüft `ui-scale:`, `docs/design/ui-scaling.md`), Bildschirmfotos mit `-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos: Zonen,bildschirmfotos: Sprache'`
(englisch und deutsch). Mit echtem Client auf dem dedizierten Server der Beitrittstest unten mit
`PROPWASH_JOINTEST_ZONES=1` an beiden Prozessen und `PROPWASH_JOINTEST_PILOT=1` am Server. JUnit:
`tools/fastcheck.sh test --select-package de.lardtom.propwash.zone` (dazu `sim.ZoneBatteryTest`).

### Zonen für alle und Freigabe je Welt-Regel (zones-open, 0.6.0)

Doku: `docs/zones-open.md`.

- JUnit: `zone.ZonesOpenTest` und `zone.ZoneMenuTest` (`tools/fastcheck.sh test --select-class
  de.lardtom.propwash.zone.ZonesOpenTest --select-class de.lardtom.propwash.zone.ZoneMenuTest`).
- Selbsttest `'-PpropwashSelftestOnly=zones-open:'` (`zone.client.ZonesOpenSelfTests`, beide Loader): Testplattform
  aus Glatter Steinplatte 13 × 13 Blöcke, 160 Blöcke östlich und 24 Blöcke über dem Spieler; Markierer per echtem
  Rechtsklick (`KeyMapping.click` auf „Benutzen“), Vorschau-Rahmen, Editor; Häkchen „Zonen“ im Tab mit „Speichern“;
  gesperrte Schwerkraft gespeichert, nicht wirksam (Client `ZonePhysics.at`, Server `ZoneServer.resolve`), ausgegraut im
  Editor, ein Paket mit anderem Wert ändert sie nicht; Rechte mit zwei Testspielern (`dev.ZonesOpenJoinCheck.fake`), der
  Gastgeber ist der Operator (im Einzelspieler mit erlaubten Befehlen gilt die Operator-Liste für andere Spieler nicht,
  den echten Operator prüft der Beitrittstest); Ansicht ohne Operator-Recht (nur am Client vorgespielt, danach schickt
  der Server den echten Stand); Tab „Zonen“ (Befund OWNP-2, `docs/zones-open.md` §3 und §7): Tab Welt-Regeln ohne
  Zonen-Knöpfe, Operator-Liste mit Besitzern, „Zeigen“, „Hinspringen“ auf den Boden der Zone, Markierer öffnet den Tab,
  „Löschen“ mit Rückfrage, Spieler-Liste nur mit eigenen Zonen, ohne `/tp` bzw. ohne Cheats kein „Hinspringen“
  (`IntegratedServer#setWorldAllowCommands`, danach zurück); „Hinspringen“ ohne Sturz (Befund AUTO18-1): Zone 90 Blöcke
  über der Plattform → Spieler auf der Plattform, Zone am Weltboden in Grundgestein (vier Blöcke, danach zurückgesetzt)
  → Ablehnung, Spieler bleibt stehen.
  Aufräumen entfernt Plattform, Testzonen, Sperre und Markierer. Bildschirmfotos (nur Fabric, mit
  `-PpropwashScreenshots=true`): `zones_open_marker`, `zones_open_editor_locked`, `zones_open_world_rules`, je `_de`
  (den Tab „Zonen“ zeigen `configurator_zones` und `configurator_zones_narrow_{en,de}`);
  danach vergleicht „options.txt behält nach den deutschen Bildschirmfotos die Sprache des Laufs“ die Zeile `lang:` in
  `options.txt` mit der Sprache im Spiel (Befund WA-W2-7: `Tutorial.setStep` in der Vorschau-Szene speicherte die
  Spieleinstellungen, während Deutsch aktiv war).
- Beitrittstest: Server mit `PROPWASH_JOINTEST_HOLD=600 PROPWASH_JOINTEST_ZONES_OPEN=1` (`dev.ZonesOpenJoinCheck`, Log
  `[Beitrittstest] zones-open: …`), Client mit `PROPWASH_JOINTEST_ZONES_OPEN=1` (`zone.client.ZonesOpenJoinProbe`),
  Erwartung
  `joined|zones_open_creatable=true|zones_open_admin=false|zones_open_owned=self|zones_open_race_legal=self|zones_open_other=denied|zones_open_operator=changed|zones_open_listed=own|zones_open_jump_button=hidden|zones_open_jump=denied|zones_open_locked=kept|zones_open_greyed=true|zones_open_deleted=own`.
  Spieler B und der Operator sind am Server simulierte Spieler; der Server legt eine Admin-Zone und eine Zone von B an
  (der Client darf beide nicht gelistet sehen), gibt die Schwerkraft nach dem Löschen der Zone wieder frei und entfernt
  die beiden Zonen.
- Der Server-Rauchtest Zonen (`zone.ZoneServerChecks`) schaltet „Spieler dürfen Zonen anlegen“ für die Gast-Prüfung aus
  und danach zurück; der Beitrittstest Zonen (`zone.client.ZoneJoinProbe`) prüft die Ablehnung an einer Admin-Zone.
- Befund PATTY-1 (verschachtelte Zonen, Zonen-Editor, Anzeige; `docs/zones-open.md` §9): JUnit `zone.ZoneLayersTest`
  und `zone.ZoneSourcesTest`; Selbsttest-Schritte in `zone.client.ZoneLayersSelfTests` (Gruppe `zones-open:`, nach
  „Hinspringen“): Mausrad scrollt den Zonen-Editor bei GUI 427 × 240 ohne Wert zu ändern, Scrollbalken greifbar;
  Umrisse nur mit Zonen-Markierer oder Kurs-Werkzeug (eigener Testplatz 220 Blöcke südlich, zwei verschachtelte Zonen),
  innere Zone hervorgehoben, äußere nur Kanten, Einstellung „Zonen anzeigen“ im Tab „Zonen“; verschachtelte Zonen mit
  „Weltwert erzwingen“ am Server und Client, OSD-Text, Editor-Werte, Entf und `/propwash zone set … world`.
  Bildschirmfotos `zones_open_editor_scroll` und `zones_open_nested` (je `_de`); `zones_border` schaltet für die
  Aufnahme „Zonen anzeigen: Immer“ ein und danach auf den Standard zurück.
- Befund FB1-8 (Zonen-Umrisse im Flug, `docs/zones-open.md` §9.3): JUnit `zone.client.ZoneViewConfigTest`,
  `zone.ZoneSourcesTest` und `zone.ZoneMenuTest`; Selbsttest-Schritt „Zonen-Umrisse im Flug …“ in
  `zone.client.ZoneLayersSelfTests` (Gruppe `zones-open:`, einzeln mit
  `'-PpropwashSelftestOnly=zones-open: Zonen-Umrisse im Flug'`, unter einer Minute): `zones.json` aus rc.3, FPV, F1,
  „Nur mit Werkzeug“, Sichtflug, zu Fuß. Bild `zones_open_flight_fpv` (je `_de`); auch `zones_fpv_notice` zeigt seitdem
  die Umrisse der Zone.

### LED-Pfad

Zubehör mit `led_path` (`docs/design/led-strip.md` „LED-Pfad“): `'-PpropwashSelftestOnly=drone-render: LED-Pfad'`
(beide Loader, ein Schritt, ohne Welt-Drohne) prüft am Beispiel-Zubehör `propwash_example:led_ring` Plätze auf dem Ring,
Leuchtformen, keine Streckung und alle LED-Modi scharf und unscharf. Bildschirmfoto `drone_closeup_led_path` (Ring im
Regenbogen-Modus aus der Nähe): `-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos: Drohne mit LED-Ring'`
(nur Fabric). JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.parts.LedPathTest --select-class
de.lardtom.propwash.drone.client.render.LedPathShapesTest`.

### Akku gesteckt

Stecker und Stromkabel an der Drohne ausgeblendet (`docs/battery-plug.md`, Regeln in `docs/ASSETS.md` „Akku gesteckt“):
`'-PpropwashSelftestOnly=drone-render: Akku gesteckt'` (beide Loader, drei Schritte in
`drone.client.render.BatteryPlugSelfTests`, ohne Welt-Drohne, unter 1 min). „… Stecker und Kabel an der Drohne
ausgeblendet“ zeichnet jede gültige Kombination der eingebauten Rahmen, Akkus und Stacks, jede Voreinstellung (mit Just
More Parts in `mods` auch deren) und den skalierten Beispielrahmen `propwash_rendertest:xclass_scaled` über
`DroneVisuals.submit` in einen Sammler (`PlugSubmitRecorder`) und verlangt: keine Fläche der Stecker des Akkus samt
Kabeln (eigene Regel im Test: Elemente `connector`, `connector_grip`, `bt_connector`, `balance_plug` und jedes `wire`, das
eines davon berührt) und keine Fläche der beiden `pigtail` gezeichnet, keine Zusatzform, alle übrigen Flächen des Akkus
gezeichnet.
Ohne den Fix scheitert er mit „Akku-Stecker 16 von 16 Flächen, Stack-Kabel 10 von 10 Flächen und 1 Zusatzformen
(Steckerpaar, Kabel) gezeichnet, erwartet 0“. „… Varianten und Masken aller eingebauten Teile auflösbar“ verlangt
`_mounted` samt Lack und zwei `pigtail` für jeden eingebauten Stack und eine Maske an jedem Akku-Modell (auch `_under`)
und prüft für alle 28 gültigen Kombinationen den Zustand (Stack `_mounted`, beide Masken, ohne Akku der Stack mit
Stecker). „… Rückfall ohne _mounted“ liest die Masken über einen Ressourcen-Manager, der das Grundmodell von `stack_30`
und das Render-Item von `aio_20` aus einem anderen geladenen Paket liefert (wie ein Ressourcenpaket, das nur das
Grundmodell ersetzt): beide zeichnen dann das bisherige Bild mit Steckern, `aio_whoop` bleibt ohne; dazu der
Beispiel-Stack `propwash_example:stack_30_8s` ohne `_mounted` und eine Drohne ohne Akku. Bildschirmfotos (nur Fabric):
`-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos (drone-render): Akku gesteckt'` schreibt
`battery_plugged_<rahmen>` (alle fünf eingebauten Rahmen schräg von hinten oben), `battery_plugged_closeup_freestyle_5`,
`battery_plugged_closeup_race_5` (Akku unten), `battery_plugged_item_gui` (Drohnen im Inventar, GUI-Skala 4) und
`battery_plugged_scaled_frame` (skalierter Beispielrahmen von hinten). Mit `PROPWASH_PLUG_SERIES=1` in der Umgebung kommt
die Bildreihe `battery-plug-series/NN_<rahmen>_<akku>` dazu (je Bild schräg von hinten und von der Seite: 5 Zoll
Freestyle, 3,5 Zoll Cinewhoop, 7 Zoll Long Range, Race und Whoop mit kurzem und langem Akku, XT60, XT30 und BT2.0, mit
Just More Parts in `mods` X-Class 10 und 13, Squashed, Bash, Race Stretched und Whoop 75); sie ist zum Ansehen da und wird
nicht eingecheckt. JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.drone.client.render.PlugAnchorsTest`
(Masken aller eingebauten Akkus und Stacks, Kabel ohne Stecker bleibt, Flächen nur ganz innerhalb eines Kastens, Masken im
Modell auch bei skaliertem Render-Item); Generator: `python3 tools/assets/parts/validate.py` (`check_stack_mounts`).

Drohnen-Item in allen Ansichten (seit Befund OWNP-1): `'-PpropwashSelftestOnly=drone-render: Drohnen-Item'` (beide Loader,
ohne Welt-Drohne, unter 1 min; Klasse `drone.client.render.DroneItemViewSelfTests`). „… in Hand, Kopf und Regal zeigt jede
Drohne vollständig wie in der Welt“ zeichnet jede Voreinstellung, jede Add-on-Voreinstellung, jeden Add-on-Rahmen ohne
Voreinstellung und jedes Add-on-Zubehör (an Freestyle und Long Range) in erster und dritter Person mit beiden Händen, auf
dem Kopf und im Regal und verlangt dieselben Teile an denselben Plätzen wie die Baugruppe in der Welt (keine Symbolform)
und 72–100 % der Freestyle-Größe; „… im Slot behält Rahmen-Modelle und jedes Anbauteil an seinem Platz“ verlangt für die
Anzeige `gui` den Renderer mit `"icon": true` und erlaubt dort nur Symbol-Props und die Symbolform eines Rahmens ohne
Modell. Mit den Jars von Just More Parts, dem Kamera-Add-on und Mob Defense in `fabric/runs/selftest-client/mods` sind es
65 Drohnen (390 Ansichten in der Hand). Kontaktbogen (nur Fabric): `-PpropwashScreenshots=true
'-PpropwashSelftestOnly=bildschirmfotos (Drohnen-Item)'` schreibt je Drohne ein Bild nach
`docs/screenshots/drone_item_sheet/` (erste Person, Slot in der Schnellleiste, Rüstungsständer, Item-Rahmen und Boden in
einem Bild; nicht einchecken).

### Schaltermaske

Die eingebauten Funktionen (`input.SwitchFunction`) stehen in einer `long`-Maske, Bit = Ordinalzahl (bis 64 Werte):
`RadioFrame#activeSwitches`/`readableSwitches`, `SwitchEvaluator#bit`/`evaluate`/`readable`/`isSet`, die Selektoren für
Flugmodus, 3D-Modus und Turtle, `TransmitterInput` und die Live-Anzeigen im Tab „Modi“ und im Empfänger-Assistenten.
JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.input.SwitchMaskTest` (Typen per Reflection, ein Bit je
Funktion, gesetzte Bits ab Position 31 ändern an keiner Funktion und keinem Selektor etwas, RadioFrame und RadioState
tragen sie unverändert, gespeicherte Profile mit Schlüsseln nach Funktionsnamen lesen wie bisher). Im Spiel:
`'-PpropwashSelftestOnly=input:'`. Die Zonen-Auflösung am Client liegt in `zone.client.ZonePhysics` (`at` für einen Punkt,
`update` für ein `ZoneField` mit Übergang, genutzt von `PilotSession`); geprüft mit `'-PpropwashSelftestOnly=zonen:'`.

### Flugbahn-Marker

OSD-Element `flight_path_marker` (`docs/flight-path-marker.md`): `'-PpropwashSelftestOnly=osd: Flugbahn-Marker'` (beide
Loader, zwei Schritte in `osd.client.FlightPathMarkerSelfTests`, rund 1 min). „… Vorwärtsflug im FPV …“ fliegt eine
FPV-Sitzung mit Test-Eingabe im Angle-Modus: Schweben in 5 m → 40 Ticks kein Marker; Vorwärtsflug ab 6 m/s → Marker
in mindestens 36 von 40 Ticks im Bild, unter der Bildmitte und an der Stelle, die die Projektion der Sim-Geschwindigkeit
ergibt; eine Test-Quelle mit Videoverlust → kein Marker, dieselbe ohne Verlust → Marker. „… beim Zuschauen (F7) …“
lässt den Server eine fremde Drohne mit 9 m/s geradeaus ziehen, schaut per Kanal-Anfrage zu und erwartet den Marker aus
den gezeigten Posen (OSD-Tempo im Mittel wie die gezeigte Fahrt auf ± 20 %, Richtung wie die Fahrt), nach dem
Anhalten keinen. Bildschirmfotos (nur Fabric):
`-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos: Flugbahn-Marker'` schreibt
`osd_flight_path_marker` (geradlinig), `osd_flight_path_marker_fisheye` und `osd_flight_path_marker_edge` (rückwärts,
gestrichelt am Rand). JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.osd.FlightPathMarkerTest`.
Vorschau im OSD-Editor: `tools/fastcheck.sh test --select-class de.lardtom.propwash.osd.client.FlightPathMarkerPreviewTest`
(600 s Animation der Vorschau in drei Rastern: Marker in jedem Bild 0,05–0,3 Bildhöhen unter der Mitte, höchstens
0,05 Bildbreiten seitlich und ohne Wandern).

Mit zwei echten Clients über LAN (wie „Zwei Clients über LAN“ in §3): `tools/remote_fpm_test.sh [fabric|neoforge]`
startet den Gastgeber (`runClientSelftest`, `PROPWASH_LANHOST_FPM=1`, `-PpropwashSelftestOnly=lan-fpm`), der die Welt im
LAN freigibt, dem Beobachter eine Kreativ-Brille aufsetzt und im FPV in 7 m einen weiten Kreis fliegt. Der Beobachter
(`runClientSmoke`, `PROPWASH_JOINTEST_SPECTATE_FPM=<sekunden>`, Standard 20 über `PROPWASH_FPM_SECONDS`) fragt den
Kanal an, schaltet den Marker in seinem OSD ein und zählt die Ticks mit OSD-Tempo ab 2 m/s, in denen er gezeichnet
wird. Ergebnis in `runs/smoke-client/jointest/spectate-fpm.txt` (`spectate_fpm=drawn|none` und Zähler) mit
Bildschirmfoto `spectate-fpm.png` aus dem Zuschauer-Bild; Erwartung `joined|spectate_fpm=drawn|!spectate_fpm=none`.
Beide Prozesse belegen je einen Client-Slot: `PROPWASH_MC_LAUNCH=<Slot-Skript> tools/remote_fpm_test.sh fabric`.

### Kamerawinkel im K-Menü

Abschnitt „Kamera dieser Drohne“ im Tab Video (`docs/camera-angle.md`): `'-PpropwashSelftestOnly=video: Kamerawinkel im
K-Menü'` (beide Loader, vier Schritte in `video.client.CameraAngleMenuSelfTests`, rund 30 s in der Testwelt). Ohne Drohne
ist die Zeile ausgegraut (kein Regler, Knöpfe ohne Wirkung, Fußzeile „Spieleinstellung“); mit gehaltener Freestyle-Drohne
ohne Brille: Regler auf 35° → Item-Tuning 35°, `+` → 36°, getippte 65° → mit Hinweis 50° (Halter), `−` → 49°, ein
Tuning mit 80° am K-Menü vorbei → der Server speichert 50°, `↺` → kein gespeicherter Winkel, 42° gesendet und Verwerfen
→ wieder der Standard; die Suche nach „uptilt“, „Kamerawinkel“, „camera angle“ und „Neigung“ findet die Zeile unter den
ersten sechs Treffern und der Sprung fokussiert den Regler sichtbar; im FPV (digitale Brille, digitale Freestyle-Drohne)
40° → FPV-Kamera und Drohne auf dem Server 40°, die Sitzung bleibt im FPV. Weil das offene K-Menü im Einzelspieler den
Server pausiert, prüft jeder Schritt den Wert nach dem Schließen; „live bei offenem Menü“ prüft der Beitrittstest. Der alte Weg bleibt mit
`'-PpropwashSelftestOnly=drone: Kamerawinkel pro Drohne'` geprüft. Die Zeile in jeder Fenstergröße und beiden Sprachen
prüft `ui-scale:` mit den Fällen „Konfigurator Kamerawinkel …“ (mit Drohne und Brille, mit Drohne ohne Brille, ohne
Drohne), gezielt: `-PpropwashUiScaleCases='Konfigurator Kamerawinkel,Konfigurator im Flug'`. Bildschirmfotos (nur
Fabric): `-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos: Kamerawinkel im K-Menü,bildschirmfotos:
Sprache'` schreibt `configurator_video_camera_angle`, `…_none` und `…_no_goggles`, jeweils auch `_de`. Mit echtem Client
auf dem dedizierten Server: Pilot-Beitrittstest (§3) – nach der FPV-Prüfung stellt der Client im K-Menü 40° ein, lässt
das Menü offen, bis die FPV-Kamera kippt, und schreibt `camangle=40` und `camangle_live=menu_open` (`after_close`, wenn
erst das Schließen wirkt); der Server loggt `[Beitrittstest] Kamerawinkel an der Drohne von …: 40° …`. Erwartung
zusätzlich `camangle=40|camangle_live=menu_open`. JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.drone.CameraMountTest`.

### Landungen

`'-PpropwashSelftestOnly=drone: Landung'` (beide Loader, sieben Schritte, mit den Rahmen-Schritten 15, rund 30 s in der
Testwelt) läuft allein: Ohne laufende Sitzung setzt „drone: Landungen – Testfeld mit Stein, Gras und Stufe“ eine eigene
Drohne drei Blöcke westlich des Spielers und startet den Sichtflug, „drone: Landungen – Testfeld abbauen“ beendet ihn und
entfernt sie (`docs/design/ground-contact.md`). Mit dem Mauer-Absturz davor wie im vollen Lauf (21 Schritte):

```sh
'-PpropwashSelftestOnly=drone: Drohne aus dem Item platzieren,drone: Funke und Brille binden,drone: Sichtflug starten,drone: Absturz an einer Mauer,drone: über die Mauerkrone,drone: Mauer des Absturztests,drone: Landung'
```

JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.LandingSelfTestSequenceTest --select-class
de.lardtom.propwash.sim.LandingFlipTest`.

### Slime und Wolle

Antwort der Flächen (`docs/design/surface-blocks.md`): `'-PpropwashSelftestOnly=drone: Slime,drone: Wolle'`
(`drone.client.SurfaceSelfTests`, beide Loader, neun Schritte, rund 17 s in der Testwelt, eigene Testdrohne mit
FPV-Sitzung, Testfelder 7 bis 37 Blöcke in +Z-Richtung neben dem Spieler, danach abgebaut): Fall aus 10 m auf Slime mit
zweitem Gipfel über 3 m (gemessen 3,9 m), Abstürze 0, Verschleiß gleich; Slime-Wand mit 10 m/s prallt ab (danach etwa −6
m/s); von unten mit 8 m/s gegen eine Slime-Decke 4 m über Steinboden: an der Decke 0 Treffer, kein Absturz, Verschleiß
gleich bis zum letzten Tick vor dem Boden, danach etwa −6 m/s (der Steinboden 13 Ticks später verschleißt die Drohne, das
Log nennt es); Wollwand mit 15 m/s ohne Absturz, scharf, Props unverändert; Fall aus 10 m auf Heuballen ohne Rückprall und
Verschleiß; 3 mm in der Wolle bleibt die ruhende Drohne mindestens 20 Ticks am Boden und in Ruhe (höchstens 1 mm
eingesunken); Start von Wolle nach 2 s höher als 2 m. Der Server-Rauchtest prüft bei jedem Lauf den autonomen Heimflug
mit 15 m/s gegen eine Wand aus Wolle, Slime und zur Kontrolle Stein (`rescue.SurfaceServerChecks`, Zeile „Slime und
Wolle: …“ im Log). JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.SurfaceResponseTest`; Goldens
und `tools/calibration.sh` bleiben gleich.

Honig (Owner-Entscheidung 21.10.2026, `docs/design/surface-blocks.md` §2a): `'-PpropwashSelftestOnly=drone: Honig'`
(dieselbe Klasse, vier Schritte mit Abbau): gegen eine Honigwand mit 6 m/s ohne Abprall (kleinstes vx danach nicht unter
−0,3 m/s), ohne Absturz und Verschleiß, danach entschärft an der Wand herunterrutschen (vy in 1,5 s zwischen −1,0 und
−0,15 m/s, jeden Tick an der Honigwand); Fall aus 10 m auf Honig ohne Rückprall (Gipfel unter 0,1 m), Absturz und
Verschleiß; Start von Honig mit Gas Schwebe + 0,05: klebt mindestens 12 und höchstens 40 Ticks, nach 3,5 s höher als
0,5 m. Gemessen 21.10. (Fabric und NeoForge gleich): kleinstes vx −0,01 m/s, Rutschen −0,55 bis −0,53 m/s und 1,0 m in
2 s, 30 von 30 Ticks an der Wand; Fall mit 11,5 m/s, zweiter Gipfel 0,00 m; klebt 24 Ticks (1,2 s), nach 3,5 s 8,7 m
hoch. Bildschirmfotos `surface_honey_wall.png` (beim Rutschen) und `surface_honey_takeoff.png` (beim Kleben) bei jedem
Lauf: mit `-PpropwashScreenshots=true` (Fabric) in `docs/screenshots/`, sonst in `screenshots/` im Run-Ordner des
Selbsttests (beide Loader). JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.HoneySurfaceTest`
(Wand, Rutschen, Start; Ausgangslage in `SurfaceResponseTest`).

### RX-Block

RX-Block (`docs/design/rx-block.md`, Spieler: `docs/rx-block.md`): `'-PpropwashSelftestOnly=rx:'` (zehn Schritte, neun davon
auf einem eigenen Platz bei Spawn + (1400, −1400), beide Loader, rund 1 min nach dem Laden der Welt): Block per Item setzen, mit der Test-Funke
(`SimulatedRadio`, Gas auf Achse 2) per Rechtsklick binden, Gas im Fenster anlernen, Endpunkte bei 1100 µs und 1900 µs
übernehmen; 50 % Gas → Stärke 7 oder 8, die Lampe daneben leuchtet (Client und Server); Reichweite 16 und 30 Blöcke
weg → hält 15, zurück folgt, 140 Blöcke weg → hält, zurück folgt; Block in einem Chunk, den der Test lädt, belegt und
wieder entlädt → während Gas-Änderungen bleibt der Chunk entladen und die Zahl der geladenen Chunks gleich, nach dem
Wiederladen ist die Stärke unverändert; Block direkt an der Außenkante des äußersten geladenen Chunks östlich des
Spielers (geladen, ohne Block-Ticks, Nachbar dahinter nicht geladen) → Gas-Änderungen ändern nichts, kein
Nachbar-Chunk wird geladen, Chunk-Zahl gleich; Pilot kommt auf 30 Blöcke näher → der Block folgt; FPV-Sitzung mit
derselben Funke → Drohne steigt und Block folgt; Fenster bei GUI 320 × 240 ohne Überlappung; fremder Block im Einzelspieler
(Spieler ist Operator) öffnet das Fenster, die Bindung bleibt – ohne Recht prüfen das Rauchtest und Beitrittstest;
Schleichen + Rechtsklick fragt nach und löst die eigene Bindung. Jeder Schritt stellt zuerst die Ausgangslage her
(FPV beendet, Funke in der Hand, Block gebunden), einzelne Schritte lassen sich deshalb auch allein filtern. Texte in allen Fenstergrößen und beiden
Sprachen: `'-PpropwashSelftestOnly=ui-scale:' -PpropwashUiScaleCases=RX-Block` (Zwischenfenster dabei ausschließen,
§1 „ui-scale“). Bildschirmfotos (nur Fabric):
`-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos: RX-Block'` (LEDs aus, grün, rot; Fenster groß,
klein und mit Bestätigung, englisch und deutsch; Kapitel `rx_block`). Server-Rauchtest bei jedem Lauf
(`rx.RxServerChecks`, Zeile „RX-Block: …“): NaN, unendlich, zu große Endpunkte und Reichweiten, fremder Block, zu weit
weg und nicht geladener Chunk abgelehnt (der Chunk bleibt dabei entladen), Werte ohne oder außerhalb des Bedarfs, alte
Folgenummer und mehr als 25 Pakete je Sekunde verworfen, Verzeichnis nach dem Entladen ohne den Block. Mit echtem
Client der Beitrittstest unten mit `PROPWASH_JOINTEST_RX=1` an beiden Prozessen. JUnit:
`tools/fastcheck.sh test --select-package de.lardtom.propwash.rx --select-class de.lardtom.propwash.guide.RxBlockSceneTest`.
Anzeige der Stärke (Befund OWNP-4): `'-PpropwashSelftestOnly=rx: Blockmodell'` (`rx.client.RxModelSelfTests`, ohne
Welt, 1 Tick) prüft an den gebackenen Modellen alle 48 Zustände: jede Stärke 0–15 mit eigenem Aussehen und eigener
Textur, Leuchten steigt mit der Stärke, LED weiter nach `link` unterscheidbar, keine fehlende Textur; JUnit
`RxBlockModelTest` prüft dasselbe an Blockzustand, Modellen und Texturen sowie das Item (gleich dem gesetzten Block bei
Stärke 0). Bildschirmfotos `rx_block_levels` und `rx_block_levels_night` (Stärke 0–15 am Tag und bei Nacht) laufen mit
`bildschirmfotos: RX-Block`.
Ausgang nach vorne (Befund OWNP6-1): `'-PpropwashSelftestOnly=rx: Ausgang,rx: Blockmodell zeigt die Richtung'`
(`rx.client.RxOutputSelfTests` auf dem Platz bei Spawn + (1400, −1400), rund 30 s; `RxModelSelfTests` ohne Welt): Block
von Hand nach Osten gesetzt, Lampen vorne, hinter der vorderen, rechts, hinten, oben, unten und Staub links → bei Gas
100 % nur die vordere und die dahinter an (starkes Signal wie beim Repeater), Staub verbindet sich nur vorne, bei 0 %
alles wieder aus; in jede der vier Blickrichtungen gesetzt → `facing` gleich der Blickrichtung, `output=front`; ein
alter Block, gelesen aus dem gespeicherten Zustand ohne `facing` und `output` (als Name allein und mit `power`/`link`),
hat `output=all`, `facing=north` und versorgt alle Seiten, aber nicht durch die Lampe vorne. An den gebackenen
Modellen: Stecker an der Seite von `facing`, alter Block mit Steckern an allen vier Seiten. JUnit `RxOutputTest` und
`RxBlockModelTest`. Mitte ist Stufe 8 (Befund OWNP6-2): JUnit `RxMappingTest` über die ganze Kennlinie in beiden
Anfahrrichtungen und über das Kanal-Totband; der Schritt „rx: 50 % Gas“ verlangt jetzt Stufe 8, von 0 % und von
100 % kommend.
Kreativ-RX-Block (Befund PATTY-2, `docs/rx-block.md` §4): `'-PpropwashSelftestOnly=rx: Kreativ'` (`rx.client.CreativeRxSelfTests`,
beide Loader, zwei Schritte, rund 10 s): Eigenschaften (Kreativ-Tab direkt nach dem RX-Block, nur er unbegrenzt,
Block-Entity des RX-Blocks, Härte −1, keine Beute, Modell, Name, Tooltip mit Reichweiten-Zeile, Fenster mit Titel
„Creative RX Block“ ohne Reichweiten-Regler); 400 Blöcke östlich des Platzes in einem gehaltenen Chunk ein
Kreativ-RX-Block mit Reichweite 1 und daneben ein RX-Block mit 128, beide auf AUX 8 → bei 100 % folgt nur der
Kreativ-RX-Block (15, LINKED), der RX-Block bleibt LOST bei 0, bei 0 % wieder 0; Chunk freigegeben und entladen →
AUX 8 bewegt sich 120 Ticks, der Chunk bleibt entladen, danach Stärke 0 gehalten. Fenster in allen Größen und beiden
Sprachen: `-PpropwashUiScaleCases=Kreativ-RX-Block` (Zwischenfenster ausschließen). Server-Rauchtest bei jedem Lauf
(`rx.CreativeRxServerChecks`, Zeile „Kreativ-RX-Block: …“): Bindung und Kanal, 20 000 Blöcke weg Bedarf nur für ihn,
ein Kreativ-RX-Block im Nether erzeugt keinen Bedarf in der Oberwelt. JUnit: `tools/fastcheck.sh test --select-class
de.lardtom.propwash.rx.CreativeRxRangeTest --select-class de.lardtom.propwash.rx.CreativeRxDataTest`.

### Motor-Hitze

Hitze-Werte und Texte (`docs/design/realism/motor-heat.md`, Phase B): `'-PpropwashSelftestOnly=drone: Motor-Hitze'`
(`drone.client.MotorHeatSelfTests`, beide Loader, vier Schritte, rund 4 s in der Testwelt, eigene Testdrohne im
Sichtflug vier Blöcke östlich und drei nördlich des Spielers, danach entfernt): Der Test-Haken setzt die Motoren 5 K über
`derate_start_c` bei kaltem ESC → Meldung „Motors very hot – they lose power and wear out“, OSD „MOTOR OVERHEAT“ ohne
„ESC OVERHEAT“, Leistungsgrenze 1,0; dann den ESC auf 147,5 °C bei kühlen Motoren → Meldung „ESC overheating – it
throttles back to protect itself“, OSD „ESC OVERHEAT“, Leistungsgrenze nach BLHeli_32 (gemessen 0,69 bei 146 °C);
Werkbank-Warnung, Teile-Tooltip, Welt-Regel `hardware_limits`, beide Meldungen und der OSD-Text in `en_us` und `de_de`
(Motor verliert Leistung, ESC regelt ab, nie „der Motor drosselt“), Werkbank-Warnung bei keinem Preset, aber beim 7″ mit
20-A-AIO und 6S-1300-LiPo.
Bildschirmfotos (Fabric, `-PpropwashScreenshots=true`, `'-PpropwashSelftestOnly=bildschirmfotos: Motor-Hitze'`):
`motor_heat_osd_motor`, `motor_heat_osd_esc` und `motor_heat_workbench`, je englisch und mit `_de` deutsch. Dazu
`parts-flight: überhitzter ESC drosselt, …` (ESC 160 °C → Grenze 25 %, Motoren über `max_temp_c` → Verschleiß am Item).
JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.ThermalCharacterizationTest` (Tabelle, Zielbänder
B1–B4 und Regel 2, Abgleich mit dem Prüfstand im Flugmodell, Werkbank-T60 der Presets gegen die Datenblätter aus
`motors-props.md` §6.1, kein Preset mit Hitzewarnung, 7″ mit eingebauten Teilen an der Hitzegrenze; mit den Daten von
Just More Parts über `tools/calibration.sh`, Tabelle in `build/calibration/thermal.txt`), `ThermalLimitsTest` (nur der
ESC regelt ab, heißer Motor verliert Drehmoment, Kennlinie) und `ThermalNeutralityTest`. Die Urteile der Zielbänder sind festgehalten: Ändert sich eines,
schlägt der Test fehl, und `motor-heat.md` wird nachgezogen.

### Druckplatten und Stolperdraht

`interact.DroneBlockTriggers` (`docs/drone-triggers.md`): `'-PpropwashSelftestOnly=interact:'` (beide Loader, mit den
übrigen `interact:`-Schritten; allein: `'-PpropwashSelftestOnly=interact: Block-Tag propwash,interact: Drohne drückt
Holz,interact: Drohne auf leichter,interact: Stein-Druckplatte,interact: Drohne löst Stolperdraht,interact: Drohne ohne
Nebenwirkungen,interact: Testfeld der Druckplatten'`, sieben Schritte in `interact.client.DroneTriggerSelfTests`, rund
10 s in der Testwelt). Die Drohnen stehen ohne Piloten auf einem Testfeld 34 Blöcke westlich und 6 südlich des Spielers;
ein Beobachter an `QuadcopterEntity.SERVER_TICK` liest nach jedem Drohnen-Tick die Blockzustände. Block-Tag am Server
(sieben Druckplatten und der Faden drin; Spinnwebe, Feuer, Seelenfeuer, Lava, Kaktus, Nether- und End-Portal,
Beerenstrauch, Pulverschnee, Haken, Blasensäule und Stein nicht); Holzplatte an spätestens im 5. Tick (Signal 15), 3
Blöcke weggesetzt nach höchstens 40 Ticks aus; leichte Wägeplatte Signal 1, 30 Ticks lang genau 1; Stein-Druckplatte 30
Ticks aus; zwei Haken mit drei Fäden gespannt, Haken an spätestens im 5. Tick, nach dem Wegsetzen aus; Feuer, Lava,
Kaktus, Nether-Portal und Spinnwebe 60 Ticks lang: Drohne im Block, nicht brennend, kein `DRONE_DAMAGED`, in der
Oberwelt, kein Portal-Vorgang, durch drei Spinnweben nach 6 Ticks genauso tief gefallen wie in Luft. Mit
`applyEffectsFromBlocks` statt des Tags (Weg des Pull Requests) scheitert der letzte Schritt (Feuer und Lava zerstören
die Drohne, Kaktus-Schaden, Portal, Spinnwebe bremst), ohne den Aufruf scheitern Holzplatte, Wägeplatte und
Stolperdraht. Mit echtem Client auf dem dedizierten Server der Beitrittstest unten mit `PROPWASH_JOINTEST_PLATES=1`.
JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.interact.DroneBlockTriggersTest`.

### Gestapelte Gates

Öffnung des Gates auf Beinen bis zum Boden (`docs/gate-legs.md`): `'-PpropwashSelftestOnly=race: Gate-Stapel,race:
Weltkurs-Leiter'` (beide Loader, fünf Schritte in `race.client.GateStackSelfTests`, rund 1 min; im Lauf mit `race:`
enthalten). „… Durchflug unten am Gate auf Beinen zählt (Client und Server)“ baut eine Leiter aus zwei mittleren Gates
und ein einzelnes Gate als Kurs (14 Blöcke westlich, 30 Blöcke nördlich des Spielers), prüft beim Client Rolle, Flag
`legs` und den Anbieter `ClientGateOpenings`, zählt eine gescriptete Runde in halber Rahmenhöhe unten durch die Leiter
mit einem eigenen `LapTracker` (ohne Anbieter keine Runde, mit Anbieter genau eine, Anbieter zweimal gefragt) und
lässt am Server dieselbe Runde über `RaceServer` prüfen (Bestenliste nimmt sie an, Redstone-Puls des unteren Gates,
kein Puls am einzelnen Gate beim Flug durch dessen Balken). „… einzelnes Gate zählt unten erst ab Rahmen“ ist die
Gegenprobe über die Geometrie auf Client und Server, „… nach Abbau des oberen Gates wieder Rahmen unten“ verlangt höchstens
10 Ticks auf Server und Client. „race: Weltkurs-Leiter: Rekorde und Ghost-Layout unverändert“ setzt die Drohnen-Arena,
trägt Rekord und Rekord-Geist ein, rechnet die Stapel neu und erwartet danach denselben Layout-Hash (auch beim Client),
dieselbe Bestenliste, keinen Archiv-Wechsel und Beine nur am untersten Gate der Leiter. JUnit:
`tools/fastcheck.sh test --select-class de.lardtom.propwash.race.GateLegsOpeningTest --select-class
de.lardtom.propwash.race.LapTrackerTest` (Layout-Hash von Leiter und Drohnen-Arena wie in 0.5.2). Mit echtem Client auf
dem dedizierten Server: Beitrittstest `stackgate` (§3).

#### Frisch gestapeltes Gate (Befund cov-race-1, 0.6.0)

- Selbsttest `'-PpropwashSelftestOnly=race: Gate-Stapel: frisch'` (beide Loader, im Lauf mit `race:` und `race: Gate-Stapel`
  enthalten, unter 1 min): „race: Gate-Stapel: frisch gestapelt: …“ in `race.client.GateStackSelfTests` legt ein mittleres
  Gate mit einem zweiten Gate als Kurs an (26 Blöcke westlich, 30 Blöcke nördlich des Spielers) und stapelt fünfmal ein
  Gate darauf und baut es wieder ab, jedes Mal in einer anderen der fünf Phasen des Nachrechnens (`tickCount + id`
  modulo 5 des unteren Gates beim Client). Verlangt: Server im Tick des Stapelns `LEGS` mit Flag `legs`, Client
  (`base()`, Flag `legs` und `ClientGateOpenings`) spätestens einen Tick nach dem Tick, in dem das neue Gate beim Client
  ankommt, und höchstens 5 Ticks ab dem Stapeln. Das Log nennt je Runde die Ticks bis zum neuen Gate und bis zu den
  Beinen. Vor der Berichtigung meldete der Client die Beine je nach Phase 0 bis 4 Ticks nach dem neuen Gate.

### Signal-Gedächtnis

Gelände nicht geladener Chunks beim Signal (`docs/signal-memory.md`): `'-PpropwashSelftestOnly=signal:'` (beide Loader,
vier Schritte in `video.client.SignalMemorySelfTests`, rund 5 s). „… Gelände außerhalb der Sichtweite gemerkt“ baut 640
Blöcke östlich und 640 Blöcke nördlich des Spielers eine Steinwand 5 × 30 × 20 Blöcke, 40 Blöcke vor dem Testplatz, misst
den Verlust A einer festen Strecke 20 Blöcke vor bis 20 Blöcke hinter der Wand (`ObstructionScan` mit `ClientBlockLoss`)
mit geladenen Chunks, schickt den Spieler so weit nach Westen, dass alle Chunks der Strecke den Chunk-Ausschnitt des
Clients verlassen (250 Blöcke bei Sichtweite 6, 401 bei 16), wartet, bis sie verworfen und gemerkt sind, und misst B:
A und B mindestens 20 dB, |A − B| ≤ 3 dB (gemessen 30,84 dB beide), Säulen der Wand mit Oberkante und Material Erde.
„… ohne Gedächtnis Luft (Gegenprobe)“ schaltet das Gedächtnis ab (0 dB wie in 0.5.2) und wieder ein (gleich B), eine
andere Welt sieht es nicht. „… geladene Chunks haben Vorrang vor dem Gedächtnis“ baut die Wand ab, holt den Spieler
zurück und erwartet höchstens 0,5 dB, während das Gedächtnis noch die alte Wand hält. „signal: Aufräumen“ stellt den
Spieler zurück. JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.video.link.TerrainColumnsTest
--select-class de.lardtom.propwash.video.link.TerrainCacheTest`. Mit echtem Client auf dem dedizierten Server:
Beitrittstest `signal` (§3).

### Gate-Hinweis

Hinweis beim ersten Gate einer Welt und Kapitel `gate_menu` (`docs/gate-hint.md`): `'-PpropwashSelftestOnly=race:
Gate-Hinweis'` (beide Loader, sechs Schritte in `race.client.GateHintSelfTests`, rund 5 s in der Testwelt, Testplatz 52/36
Blöcke neben dem Spieler mit Steinboden und einer Ziegelwand mit 2 × 2-Öffnung, danach wiederhergestellt). Jeder Schritt
setzt das Gate wie ein Spieler: Gate oder Freiform-Gate in die Hand, Fadenkreuz auf den Boden bzw. die Unterkante der
Öffnung, Rechtsklick über `KeyMapping.click`; die Erkennung in `GatePlaceHint` läuft also über den echten Klick und das
neue Gate-Entity. Geprüft: leerer Hinweis-Speicher → Fenster mit freiem Haken, Welt `sp_<Ordner>` gemerkt; zweites Gate in
derselben Welt → 40 Ticks kein Fenster (`SEEN_HERE`); zweite Welt (Test-Haken `GatePlaceHint.worldOverride`) → Fenster,
Klick auf „Nicht wieder anzeigen“ und „Verstanden“ → dritte Welt ohne Fenster (`NEVER`, auch für die erste Welt);
„Hinweise zurücksetzen“ im Tab Flugschule leert den Speicher → Fenster → „Kapitel öffnen“ zeigt `gate_menu` und setzt
„Nicht wieder anzeigen“; Freiform-Gate in der Öffnung → Fenster. Außerhalb dieser Schritte ist der Hinweis im Selbsttest
stumm (`GatePlaceHint.mute`), damit andere Schritte, die Gates setzen, kein Fenster sehen; der Speicher des Testspielers
wird nach den Schritten zurückgeschrieben. Das Kapitel läuft in `guide:` mit (alle Szenen, Anker bei allen
Fenstergrößen), das Fenster in `ui-scale:` als Fall „Gate-Hinweis“ (gezielt `-PpropwashUiScaleCases='Gate-Hinweis,Flugschule
Szene gate_menu'`), der Knopf über die Fälle des Konfigurators. Bildschirmfotos (nur Fabric): `-PpropwashScreenshots=true
'-PpropwashSelftestOnly=bildschirmfotos: Gate-Hinweis'` schreibt `gate_hint_window`, `gate_hint_window_small` (GUI
320 × 240) und `flight_school_gate_menu` (Seite Redstone mit leuchtender Lampe), jeweils auch `_de`. Beitrittstest auf dem
dedizierten Server: `PROPWASH_JOINTEST_GATEHINT=1` am Server (`dev.GateHintJoinCheck`) stellt den Spieler 3 s nach dem
Beitritt 40 Blöcke neben den Spawn (außerhalb des Spawn-Schutzes), gibt ihm Gates und bremst den Server danach bis zum
zweiten Gate oder 240 Ticks lang um 250 ms je Tick; derselbe Schalter am Client (`race.client.GateHintJoinProbe`) leert
den Hinweis-Speicher, setzt ein Gate und schreibt `gate_hint=shown`, `gate_hint_delay` (Ticks vom Klick bis zum Gate),
`gate_hint_window` und für ein zweites Gate `gate_hint_second=none`. Erwartung:
`PROPWASH_JOINTEST_EXPECT='joined|gate_hint=shown|gate_hint_second=none'`, Server-Log `[Beitrittstest] Gate-Hinweis: …`.
JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.race.client.GateHintMemoryTest --select-class
de.lardtom.propwash.guide.GateMenuSceneTest`.

### /propwash clear

Befehl zum Aufräumen (`docs/clear-command.md`): `'-PpropwashSelftestOnly=server: /propwash clear'`
(`server.client.ClearSelfTests`, beide Loader, ein Schritt, unter 1 s in der Testwelt): zwei Drohnen drei und fünf
Blöcke neben dem Spieler, eine davon im FPV geflogen (Kreativ-Brille, Funke in der Hand) → `/propwash clear` als
Spieler meldet die Zahl aller geladenen Drohnen; danach keine Drohne am Server und am Client, FPV-Sitzung beendet,
Kamera beim Spieler, keine Pilot-Verbindung und kein Sichtanker, im Chat „Your drone was removed.“ und „Removed 2 drones
(overworld: 2).“; Kopf und erster Platz der Schnellleiste werden danach zurückgesetzt. Der Schritt entfernt alle
geladenen Drohnen der Testwelt und steht deshalb am Ende der Anmeldung. Server-Rauchtest bei jedem Lauf ab Tick 62
(`server.ClearServerChecks`, Zeile „/propwash clear: …“): drei Drohnen mit Tag (eine von einem Test-Piloten geflogen,
eine im autonomen Heimflug mit Drohnen-Ticket, eine abgestellt), dazu eine Drohne ohne Tag und ein Marker mit Tag; Gast
ohne Recht → `/propwash clear` und `/propwash clear @e[tag=…]` ändern nichts; Operator → Selektor ohne Drohnen meldet 0,
Selektor mit Tag entfernt 3, Pilot-Sitzung und Heimflug beendet, nach 3 Ticks kein Drohnen-Ticket und nicht mehr
Tickets als vorher; Drohne ohne Tag und Marker bleiben. JUnit: `tools/fastcheck.sh test --select-class
de.lardtom.propwash.server.DroneClearTest`.

### Zonenname bei schmaler GUI

Kompakte Zonenzeile im Tab „Zonen“ (`docs/zone-row.md`): `'-PpropwashSelftestOnly=ui-scale: Zonenname,ui-scale:
Deutsch: Zonenname'` (beide Loader, etwa 1 min; im Lauf mit `ui-scale:` enthalten, unabhängig von
`-PpropwashUiScaleWindows` und `-PpropwashUiScaleCases`). Legt die Zone „Selbsttest Suche“ an, prüft sie bei 640 × 480
(Skala 2 und automatisch), 854 × 480 (2) und 1280 × 720 (3), englisch und deutsch, als Operator und als Besitzer (Befund
`TRUNCATED` auch für gekürzte Knöpfe der Zone), und räumt danach auf. Mit `-PpropwashScreenshots=true` (Fabric) dazu
`configurator_zones_narrow_en.png` und `_de.png`.

### Preset-Zeile neben einem langen Add-on-Tab

Tab-Titel von Add-ons machen die Seitenleiste des Konfigurators breiter, bei GUI 320 × 240 bis an ihre Grenze von 96
Punkten (Tab 204 statt 217–219 Punkte). Die Preset-Zeile in „PID-Tuning“ und „Rates“ (`ui.client.tab.PresetPanel`) rückt
die Beschriftung „Preset“ dann aus der Spalte der Regler auf ihre eigene Breite, sobald der längste Preset-Name sonst
nicht ungestaucht in die Auswahl passt. JUnit: `tools/fastcheck.sh test --select-class
de.lardtom.propwash.ui.client.tab.PidPresetRowTest` („Standard (from the build)“ bzw. „Standard (aus dem Aufbau)“ in
jeder Tab-Breite ab 204 Punkten ungestaucht, gemessen mit der Vanilla-Schrift; wo der Name passt, bleibt „Preset“ in der
Spalte der Gruppen). Im Spiel prüft `ui-scale:` in jeder Fenstergröße und Sprache den Fall „Konfigurator mit langem
Add-on-Tab“ (`ui.client.LongAddonTabUiScaleCase`): Ein Tab „Long add-on tab“ (84 Punkte wie der längste Tab-Titel der
Add-ons, nur während des Falls angemeldet) verbreitert die Seitenleiste; geprüft werden die Tabs PID-Tuning und Rates, die
Breite der Seitenleiste und am Ende von PID-Tuning das Preset „Standard“ ganz und ungestaucht. Dazu kommt der Tab
Welt-Regeln (Seite `world_rules race_standard`): Der Fall scrollt Stopp für Stopp zum Abschnitt Rennstandard, bis beide
Knöpfe ganz im sichtbaren Bereich stehen, und verlangt „Aktuelle Regeln übernehmen“ und „Propwash-Standard“ ganz und
ungekürzt (sonst `CUT` bzw. `HIDDEN` mit Knopf- und Zeilenbreite). JUnit dazu: `tools/fastcheck.sh test --select-class
de.lardtom.propwash.race.client.RaceStandardButtonsTest`. Gezielt (beide Loader, unter 1 min):

```sh
JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=ui-scale: Fenster,ui-scale: Deutsch: Fenster' \
    -PpropwashUiScaleWindows=640x480@2,680x480@2,854x480@2 '-PpropwashUiScaleCases=Konfigurator mit langem Add-on-Tab'
```

### Add-on-Schalter (A1)

Schalter und Achsen der Add-ons, Namen der Zubehör-Aktionen und Hinweis beim ersten Flug (`docs/addon-switches.md`).
JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.input.AddonSwitchesTest` (Schalter und Taster an
Funke und Taste, Gamepad-Rasten, Moduswahl am 3-Stufen-Schalter, 64 Schalter und 16 Achsen, Belegung `ns:pfad` und
`addonAxes` bleiben ohne Add-on, Totzone, Tastenrate und Quantisierung, Namen der Zubehör-Aktionen, Hinweis einmal je
Zubehörtyp, Pakete). Im Spiel: `'-PpropwashSelftestOnly=addon-api-17: A1'` (`input.addon.client.AddonSwitchSelfTests`,
beide Loader, etwa 2 min; braucht das Beispiel-Add-on): Seite „Add-ons“ mit simulierter Funke ohne Befund; Belegung
überlebt Abmelden des Schalters, Normalisieren und Speichern; im FPV schalten Funke (2000/1000 µs) und Taste G den
„Geist“ beim Client und am Server (`ADDON_SWITCH_CHANGED`); Achse mit Totzone 10 % (0,05 → 0, 0,55 → 0,5) und abwärts
per Taste H (60 °/s = 0,667 je Sekunde, Spielraum 0,5- bis 1,1-fach; gemessen 0,667), Server bekommt den Wert, höchstens 10
Achsen-Pakete je Sekunde; „Schalter umlegen“ erkennt Kanal 8 und „Speichern“ übernimmt die Belegung; Drohne mit „Signallicht“: Hinweis im Chat
genau einmal, Tab „Modi“ nennt „Zubehör-Aktion 1 – Blinken“, Ziffernblock 9 löst aus, Rechtsklick beendet den Flug;
Seite „Add-ons“ bei GUI 854 × 480 und 320 × 240 englisch und deutsch ohne Befund der Layout-Prüfung (Schalter und Achse
mit Funke, dazu ohne Funke). Mit
`-PpropwashScreenshots=true` (Fabric) dazu `modes_addons_{en,de}_{854x480,320x240}.png` und `accessory_hint_{en,de}.png`.

Namen der Flugmodi einer Flughilfe (`FlightAssist#modeNameKey`, `docs/addon-switches.md` §3): JUnit
`tools/fastcheck.sh test --select-class de.lardtom.propwash.input.addon.AddonModeNamesTest` (5 Tests: Flughilfe wie eine
Kamera-Drohne nennt N, C, S und ATTI in der Meldung englisch und deutsch und im Hinweis des Tabs „Modi“; ohne Namen,
leer, `null`, blank, mit Ausnahme und nur teilweise benannt je Modus Acro, Angle, Horizon). Im Spiel der erste Schritt
der Gruppe: „addon-api-17: A1 – Meldung beim Moduswechsel nennt die Flugmodi der Flughilfe (N/C/S wie eine
Kamera-Drohne), auch gegen den Modusschalter“ (`drone.client.AddonModeNameSelfTests`, etwa 3 s): Probe-Flughilfe
`propwash_example:mode_name_probe` nur während des Schritts, Testfunke mit Modusschalter in der Mitte (Horizon),
Acro-Taste → Meldung „Mode: S (key – radio switch: C)“ und Name „S“ für den Tab „Modi“, Horizon-Taste → „Mode: C“. Vor
dem Fix (Befund RC2-1) meldete der Schritt „Mode: Acro (key – radio switch: Horizon)“. Ohne Flughilfe prüft „drone:
Flugmodus mit Funke …“ weiter „Mode: Acro (key – radio switch: Horizon)“. Messung zu Befund RC2-1: JUnit 5 von 5 (vor dem
Fix 3 von 5 rot), Suite 4680 gefunden, 4666 grün, 0 Fehler; `addon-api-17: A1,drone: Flugmodus` Fabric und NeoForge je
39 von 39, `input:` je 47 von 47, `ui-scale:` NeoForge 53 von 53, Fabric in drei Läufen alle Schritte PASS (zwei Fenster
im ersten Lauf mit „Bestenliste leer – Testdaten wurden überschrieben“, im zweiten PASS).

Angehängte Werte an `SwitchFunction` (wie Noclip 0.1.x, `docs/addon-switches.md` §3): JUnit
`tools/fastcheck.sh test --select-class de.lardtom.propwash.input.ExtendedSwitchFunctionTest` (Bytecode-Suche über alle
Klassen: kein `switch` über `SwitchFunction`, `OsdElement` oder `ModesTab$Row` endet für einen unbekannten Wert in einer
`MatchException`; `SwitchFunction` mit angehängtem Wert `ADDON_EXTRA` in einem eigenen Klassenlader: Flugfunktion, kein
Befehl, nicht umschaltbar, Funke auf Schalter 3 bzw. am Gamepad Taster, Taste `key.propwash.addon_extra` ohne Belegung,
eine gespeicherte Belegung übersteht `DeviceProfile#sanitize` ohne Umschalten). Im Spiel hängt das Test-Mixin von
`propwash_devtest` (`mixin.dev.SwitchFunctionExtraMixin`) nur mit `PROPWASH_DEV_EXTRA_FUNCTION=1` den Wert
`PROPWASH_DEV_EXTRA` („Extra on/off“) an und macht ihn wie Noclip zu keinem Befehl; ohne die Variable bleibt
`SwitchFunction` unverändert und der Schritt wird übersprungen. „addon-api-17: A1 – per Mixin angehängte
Schalterfunktion …“ (`input.client.ExtraFunctionWizardSelfTests`, braucht das Beispiel-Add-on) ruft Funke, Taste,
Umschalten und Belegungsschlüssel für jeden Wert auf, geht im Empfänger-Assistenten mit simulierter Funke zur Seite
„Schalter“ und prüft bei GUI-Skala 1 bis 4 und bei GUI 427 × 240 (Fenster 854 × 480): Maus über jedem „Zuweisen“,
Zuhören auf den angehängten Wert (Funke zeigt Schalter 3), Layout-Prüfung ohne Befund und kein Knopf über dem
Arm-Hinweis; bei 427 × 240 fehlt der angehängte Wert in der Liste (kein Platz). Dazu `ui-scale:` mit dem Fall des
Assistenten in allen Fenstern:

```sh
PROPWASH_DEV_EXTRA_FUNCTION=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest \
    '-PpropwashSelftestOnly=addon-api-17: A1 – per Mixin,ui-scale:' '-PpropwashUiScaleCases=Empfänger-Assistent mit Funke'
```

Beitrittstest (§3, Schalter `PROPWASH_JOINTEST_ADDON17=1` an beiden Prozessen, Server `dev.Addon17JoinCheck`, Client
`input.addon.client.Addon17JoinProbe`): Der Server macht jeden Spieler beim Betreten zum Operator (vor der
Namensraum-Prüfung), loggt `addon_features`, startet nach 3 s eine FPV-Sitzung im Kreativmodus; jede Meldung eines
Schalters steht als `[Beitrittstest] Add-on-Schalter: ADDON_SWITCH_CHANGED …`, jede Bewegung der Achse um mindestens 0,05
als `[Beitrittstest] Add-on-Achse: …` im Server-Log. Der Client öffnet die Seite „Add-ons“, schaltet mit Taste G zweimal,
hält Taste H eine Sekunde und zählt die Server-Zeile der Namensraum-Prüfung (`ns_server_hint`) und eigene Hinweise
(`ns_client_hint`) im Chat. Ein Client ohne Probe-Mod kommt auf einen Server mit Probe-Mod nicht hinein: der
Register-Abgleich des Loaders lehnt das unbekannte Item `propwash:nsprobe_item` ab („Add-ons don't match this server“,
Namespace Probe fehlt). Deshalb läuft der Fall mit Probe auf beiden Seiten; der Client zählt dann seinen eigenen Hinweis
(1) getrennt von der Server-Zeile (1). Dass ein Client ohne Probe keinen eigenen Hinweis zeigt, prüft der zweite Fall.

```sh
# Fall addon17: Server mit Beispiel-Add-on und Probe-Mod (runServerSmoke endet wegen der erwarteten ERROR-Zeilen der Probe rot)
PROPWASH_JOINTEST_HOLD=600 PROPWASH_JOINTEST_ADDON17=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke -PpropwashNsProbe=true
PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=addon17 PROPWASH_JOINTEST_ADDON17=1 \
  PROPWASH_JOINTEST_EXPECT='joined|addon_features=propwash_example:ghost,propwash_example:gimbal_tilt|addon_page=active|addon_switch=on|addon_switch_after=off|addon_axis_local=moved|ns_server_hint=1|ns_client_hint=1' \
  JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke -PpropwashNsProbe=true
# Fall addon17-noaddon: Server ohne Beispiel-Add-on und ohne Probe, Client mit Beispiel-Add-on
PROPWASH_DEV_EXAMPLE=off PROPWASH_JOINTEST_HOLD=600 PROPWASH_JOINTEST_ADDON17=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=addon17-noaddon PROPWASH_JOINTEST_ADDON17=1 \
  PROPWASH_JOINTEST_EXPECT='joined|session_inactive=propwash_example|addon_features=none|addon_page=inactive|addon_switch=off|addon_switch_packets=0|addon_axis=0.00|ns_server_hint=0|ns_client_hint=0' \
  JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
```

Danach im Server-Log von addon17 `ADDON_SWITCH_CHANGED … propwash_example:ghost=true` und `=false`, `Add-on-Achse` und als
ERROR nur die Funde der Probe; in dem von addon17-noaddon keine Zeile `ADDON_SWITCH_CHANGED`. Dasselbe mit `:neoforge:`
(Port + 1).
Wem der Server beim Betreten das Operator-Recht gibt (nur Spielern, die noch keins haben), dem nimmt er es beim Verlassen
und beim Stopp wieder (`[Beitrittstest] Add-on-Schalter: … ist kein Operator mehr (…)`); `ops.json` im Run-Ordner des
Servers ist danach wie vorher, ein Beitrittstest danach (etwa race-p2p mit dem Spieler `Dev` auf NeoForge) findet keinen
übrig gebliebenen Operator.

### OSD-Elemente und Warnungen von Add-ons

Verhalten A2 der API 1.7 (`docs/addon-osd.md`): `'-PpropwashSelftestOnly=addon-api-17: A2'` (beide Loader, vier
Schritte in `osd.addon.client.AddonOsdSelfTests`, rund 1,5 min; ohne Beispiel-Add-on, `PROPWASH_DEV_EXAMPLE=off`,
bestehen die ersten drei ohne Prüfung). „… OSD-Editors …“ öffnet den OSD-Tab, erwartet das Element `propwash_example:ammo` aus
am Standardplatz 1/10, hakt es an, prüft die Vorschau „AMMO 12“, zieht es mit der Maus um drei Spalten und zwei
Zeilen, schiebt es mit den Pfeiltasten, schaltet es mit der Leertaste aus und an, prüft, dass die Kopie der
Einstellungen den Schlüssel behält, und dass Zurücksetzen den Standardplatz herstellt. „… im Flug …“ fliegt eine
Drohne mit dem Werfer des Beispiels und 12 Pfeilen im FPV auf 3 m, erwartet „AMMO 12“ an der im Editor gezogenen
Stelle und einen frischen `OsdContext` mit `fpv=true`, `lineOfSight=false`, danach „GHOST“ im Warnfeld (Ton Vorsicht)
und nach dem Entladen auf 2 Pfeile „AMMO 2“ in Gelb. „… im Sichtflug ohne Brille …“ (Befund MDA-2) fliegt dieselbe
Drohne ohne Brille im Sichtflug auf 3 m und erwartet „AMMO 12“ an derselben Stelle im Raster über dem ganzen Bild,
einen höchstens 5 Ticks alten `OsdContext` mit `fpv=false`, `lineOfSight=true` und der Drohne, dazu weiter das
Funk-HUD und kein Brillen-OSD (vorher: „Propwash ruft die Add-on-Elemente im Sichtflug nicht auf“, der letzte Kontext
stammte aus dem FPV). Mit `-PpropwashScreenshots=true` (Fabric) dazu `osd_addon_editor_{en,de}_{854x480,320x240}`,
`osd_addon_flight_{en,de}` und `osd_addon_line_of_sight_{en,de}` (Sichtflug mit Funk-HUD und „AMMO 12“ bei 1/10).
JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.osd.AddonOsdTest`.
„… in der schmalsten Tab-Breite bei GUI 320×240 …“ (Befund cov-combo-3) legt den OSD-Tab mit 204 px an, so schmal wie
neben einer Seitenleiste von 96 px durch einen langen Add-on-Tab, scrollt die Liste von oben bis unten, zeichnet beide
Seiten mit allen Bedienelementen und lässt keine Kürzung zu (vorher „Signal repeater (RPT)“ → „Signal repeat…“). JUnit
`osd.client.OsdListWidthTest` misst mit der Vanilla-Schrift auf Englisch und Deutsch, dass in jeder Tab-Breite ab 204 px
alle eingebauten Namen und „Weiter unten mehr – scrollen“ höchstens gestaucht werden. Gegenprobe mit den Jars von Mob
Defense und Kamera-Add-on in `runs/selftest-client/mods/`: `'-PpropwashSelftestOnly=ui-scale: Fenster und GUI-Skala
merken,ui-scale: Fenster 640x480 mit GUI-Skala 2,ui-scale: Sprache Deutsch,ui-scale: Deutsch: Fenster 640x480 mit
GUI-Skala 2,ui-scale: Sprache zurückstellen,ui-scale: Fenster und GUI-Skala zurückstellen'` (rund 4 min je Loader), im
OSD-Tab kein CUT.

Zuschauer-Banner und Warnfeld (Befund G-OT4C-1-1, 0.6.0): `'-PpropwashSelftestOnly=addon-api-17: A2 – Zuschauer-Banner'`
(beide Loader, Klasse `osd.addon.client.AddonOsdBannerSelfTests`, rund 1 min Spielzeit). Der Server setzt zwei Drohnen
mit simuliertem Analog-Sender vor den Spieler (Pilotin „Lotte“ und eine eigene) und gibt ihm die Analog-Brille. Die
Prüfung schaltet die Warnung GHOST des Beispiel-Add-ons ein und misst für Zuschauen (`SpectateSession#request`),
Kameramann (`operate` auf Lottes Drohne) und allein am Gimbal (`operate` auf der eigenen) in jedem Preset und jeder
GUI-Skala ab 320×240 die Fläche des Banners (`SpectateHud.lastBox()`) gegen den gezeichneten Rahmen des Warnfelds; jede
Überlagerung ist ein Befund (vorher 12 von 48 Bildern je Sprache, alle MINIMAL). Danach dasselbe auf Deutsch. Mit
`-PpropwashScreenshots=true` (Fabric) dazu `{spectate,operator,solo}_banner_warning_{en,de}` bei 1920×1080, GUI-Skala 3,
Preset MINIMAL. JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.video.client.SpectateBannerPlacementTest`
(alle Presets, neun GUI-Größen, fünf Bannerbreiten, verschobenes Warnfeld, Add-on-Elemente wie im Kamera-Add-on).

### Zuschauerzahl im OSD (Befund SPECCOUNT-1, 0.6.0)

Baustein `docs/spectator-count.md`. JUnit: `tools/fastcheck.sh test --select-class
de.lardtom.propwash.osd.SpectatorCountTest` (13 Fälle). Der Selbsttest `osd: Vorlagen ohne Überlappung` zeichnet das
Element mit der breitesten Zahl 9999 in jeder Vorlage. Beitrittstest auf dem dedizierten Server, je ein Server- und ein
Client-Slot (§3), zwei Fälle:

```sh
cd <WT> && PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_SPECCOUNT=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
cd <WT> && PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=speccount-pilot PROPWASH_JOINTEST_SPECCOUNT=1 \
  PROPWASH_JOINTEST_EXPECT='joined|speccount role=pilot|speccount seen=1,2,1,2|speccount packets=4|speccount drawn=yes|speccount hidden=yes|speccount ok' \
  JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
```

Danach `jointest.stop` in `runs/smoke-server/`. Fall 2 genauso mit `PROPWASH_JOINTEST_SPECCOUNT=2` an beiden
Prozessen, Label `speccount-watch` und `speccount role=watcher` statt `speccount role=pilot`; beides auch mit
`:neoforge:`.

- **Fall 1, Pilot:** `dev.SpecCountJoinCheck` startet 3 s nach dem Beitritt für den echten Spieler eine 5″-Freestyle im
  FPV (Kreativmodus, gebundene Kreativ-Brille) und holt dann drei Spieler ohne eigenen Client auf den Server
  (`placeNewPlayer` mit eingebetteter Verbindung, wie `AutonomyHomeJoinCheck`): `PwSpectate` mit Kreativ-Brille,
  `PwBrille` mit der zur Drohne passenden Brille und `PwGimbal` ohne Brille. Alle 4 s ein Schritt über die Wege des
  Spiels: PwSpectate schaut über die Kanalliste zu (`SpectateC2S` durch den angemeldeten Handler) → 1, PwBrille schaltet
  die Brille auf den Kanal → 2, PwGimbal wird Operator am Gimbal (`OperatorServer#bind`, Bild über `SpectatorLinks`) →
  weiter 2, PwBrille schaltet weg → 1 und zurück → 2. Der Server prüft nach jedem Schritt
  `SpectatorCountServer#count` und am Ende genau vier Pakete an den echten Spieler; ein Fehler steht als
  `FAIL Zuschauerzahl: …` im Ergebnis des Server-Rauchtests, Log `[Beitrittstest] Zuschauerzahl: …`.
- **Fall 2, Zuschauer:** Der Server stellt eine sendende Drohne vor den echten Spieler
  (`SpectateServer.simulateTransmitter`, Pilotname `PwSender`) und setzt ihm die Kreativ-Brille auf. Der Client drückt
  die Zuschauen-Taste, bis er den Kanal von PwSender sieht; er zählt selbst mit (1). Danach PwGimbal als Operator → 1,
  PwBrille auf den Kanal → 2, weg → 1, zurück → 2.
- `osd.client.SpecCountJoinProbe` zeigt in beiden Fällen das OSD mit der Vorlage Standard (`OsdHud.overrideConfig`,
  das gespeicherte Layout bleibt), merkt jede ankommende Zahl, prüft 10 Ticks nach jeder Änderung, dass das Element ab
  1 gezeichnet und bei 0 ausgeblendet ist, und endet 140 Ticks nach der letzten erwarteten Zahl, damit der Server
  seinen letzten Schritt noch prüft. Das Bildschirmfoto `runs/smoke-client/jointest/<label>.png` zeigt oben rechts das
  Auge mit der 2.
- **Gemessen am 29.10.** (Fabric und NeoForge, beide Fälle): Folge 1, 2, 1, 2 mit genau vier Paketen am Client, Server
  zählt nach jedem Schritt richtig (Operator nur mit Bild, nicht gezählt), Element in 358 bis 419 Ticks gezeichnet und
  in keinem gefehlt, bei 0 in 51 bis 52 Ticks ausgeblendet (Fall 1), Server-Rauchtest PASS; je Fall etwa 2 min, wenn
  beide Slots frei sind. Belegt ein anderer Lauf den zweiten Client-Slot, wartet der Client, und der Server hält so
  lange (`PROPWASH_JOINTEST_HOLD=1800`).

### Daten an der Drohne und Knöpfe in der Wartung

Add-on-API 1.7, B6 und B10 (`docs/drone-data.md`): `'-PpropwashSelftestOnly=addon-api-17: B6,addon-api-17: B10'` (beide
Loader, drei Schritte in `drone.data.client.DroneDataSelfTests`, rund 30 s in der Testwelt; der Präfix `addon-api-17: B1`
erfasst B10 mit). B6 Sync: Drohne drei Blöcke vor dem Spieler, Mission (`TRACKING`) und Protokoll (`NONE`) des Beispiels
plus roh die Daten eines nicht geladenen Add-ons `propwash_absent:route`; ein Wert über 2 KiB und Daten über 8 KiB je
Drohne abgelehnt ohne Änderung, Spieler als Drohne abgelehnt; Item, Pick-Block und neu gesetzte Drohne tragen
dieselben Daten; am Client nur die Mission, `NONE` liefert den Standard, `set` und `remove` werfen; Änderung und
`remove` am Server kommen am Client an. B6 fremde Daten: Drohne 4096 Blöcke entfernt im geladenen Chunk mit denselben
Daten, Chunk freigeben bis die Drohne entladen ist, `saveEverything`, Chunk neu laden → `AddonData` byte-gleich, Daten
von `propwash_absent` unverändert. B10: Wartung offen, ohne Proben genau der Knopf „Mission“ im Kopf, mit drei
Probe-Knöpfen höchstens drei und „Mehr“ mit dem Rest, alle Knöpfe zwischen Titel und Rand; „Mission“ öffnet das
Kind-Fenster mit der Wartung als Eltern-Fenster, Zurück führt in die offene Wartung, „Mehr“ öffnet die Liste, ihr
Eintrag öffnet mit der Wartung als Eltern-Fenster; `setPilot(Spieler)` und `setPilot(null)` lösen `PILOT_CHANGED` je
einmal aus, das Beispiel schreibt Mission (Flug 1) und Protokoll (2 Wechsel), der Client sieht die Mission. Beitrittstest
auf dem dedizierten Server: `PROPWASH_JOINTEST_ADDONDATA=1` an beiden Prozessen; der Server (`dev.AddonDataJoinCheck`)
setzt 3 s nach dem Beitritt eine Drohne mit Mission `join-1` und Protokoll vor den Spieler und ändert die Mission 5 s
später auf `join-2`; der Client (`drone.data.client.AddonDataJoinProbe`) schreibt `addondata tracking=<name> …`,
`addondata first=…`, `addondata none=hidden|leaked …` und `addondata synced=[…]`. Erwartung:
`PROPWASH_JOINTEST_EXPECT='joined|addondata tracking=join-2|addondata none=hidden'`, `PROPWASH_JOINTEST_LABEL=addondata`,
Server-Log `[Beitrittstest] Add-on-Daten: …`. JUnit: `tools/fastcheck.sh test --select-class
de.lardtom.propwash.drone.data.DroneDataTest`.

### Sharecode (PW3)

Oberfläche und Befehle (`docs/sharecode-ui.md`): `'-PpropwashSelftestOnly=share:'` (beide Loader,
`share.client.ShareSelfTests`): Tabellen geladen (Propwash und Beispiel-Add-on `propwash_example` mit `deprecated`,
`replaced_by`, `fallback`, ohne Kurzkennung; im Entwicklungslauf lädt `share.ShareDevTables` die Tabelle aus
`devtest/resources/data/propwash_example/propwash_share/table.json` nach, als wäre das Beispiel eine eigene Mod; keine
Meldungen), Werkbank → Code → Import (Kreativ, GUI-Skala 6), alter Code (PW1 und PW2, „Als PW3 kopieren“ gleich der
Golden-Umrechnung), fehlendes Add-on (`propwash_missing` mit Modrinth-Link, Kurzkennung 2 ohne JMP, Einkaufsliste mit
„braucht …“ im Überleben, Link-Knopf öffnet den Bestätigungsdialog, „Mit Ersatzteilen erzeugen“ im Tab), Zubehör
eines fehlenden Add-ons (Gimbal vorn, Box oben, Licht unten aus `propwash_mounts`, das nur im Verzeichnis steht: Rahmen
bis Akku über `fallback`, das Zubehör fällt mit „(entfernt)“ weg, Vorschau und „Mit Ersatzteilen erzeugen“ ohne
Zubehör), Prüfsumme falsch (Propwash-Tabelle mit zwei vertauschten Motoren, Banner, Teile gesperrt, „Teile trotzdem übernehmen“), freie
Adresse (P8 nur als Text), Konfigurator-Tab (Export und Import im Konfigurator; Tuning und Lack im selben Tab in einem Fenster ohne Pause, weil der Konfigurator im Einzelspieler den
Server anhält und die Import-Ratenbegrenzung mit Server-Ticks rechnet),
Befehle (`export`, `info`, `give`, `convert`, `import … substitutes`) und genau ein Sharecode-Knopf (mit Just More Parts
1.2.0 dazu der PW3-Export der JMP-Drohne gleich Vektor P2). `ui-scale:` prüft dazu das Fenster
(Import mit fehlendem Add-on, Export) und den Tab (`share.client.ShareUiScaleCases`, Befund `HIDDEN`, wenn
„Übernehmen“, „In die Zwischenablage“, der Knopf „Drohne erzeugen“/„Einkaufsliste“ oder die erste Add-on-Zeile mit
Problem nach dem Öffnen nicht ganz zu sehen ist). Mit `-PpropwashScreenshots=true` (Fabric) und
`'-PpropwashSelftestOnly=bildschirmfotos: Sharecode'` entstehen `docs/screenshots/share/share_<szene>_<fenster>_<en|de>.png`
für Export, Import PW3 und PW2, fehlende Add-ons, Link aus dem Code, Prüfsumme falsch, Ersatzteile, Einkaufsliste im
Überleben, freie Adresse und Tab bei 640 × 480 und 854 × 480 (GUI-Skala 2). Server-Rauchtest bei jedem Lauf ab Tick 70
(`share.ShareServerChecks`, Zeile „Sharecode: …“): Befehle angemeldet, `info`/`convert` am Server, PW1-Umrechnung, mit
Just More Parts der PW3-Export der JMP-Drohne gleich Vektor P2.

Beitrittstest `PROPWASH_JOINTEST_SHARE=1` (`dev.ShareJoinCheck` am Server, `share.client.ShareJoinProbe` am Client),
Server ohne Beispiel-Add-on, Client mit:

```sh
cd <WT> && PROPWASH_DEV_EXAMPLE=off PROPWASH_JOINTEST_HOLD=600 PROPWASH_JOINTEST_SHARE=1 JAVA_HOME=… …/mcslot.sh ./gradlew :fabric:runServerSmoke
cd <WT> && PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=share PROPWASH_JOINTEST_SHARE=1 \
  PROPWASH_JOINTEST_EXPECT='joined|share give ok|share missing_parts|share applied|share inactive|share substitute applied' \
  JAVA_HOME=… …/mcslot.sh ./gradlew :fabric:runClientSmoke
```

Ablauf: 3 s nach dem Beitritt `/propwash code give <PW3-Vektor „Night Ripper“> <spieler>` (Drohne mit Name im
Inventar), nach 7 s Überlebensmodus, leeres Inventar und offene Werkbank; der Client importiert eine Freestyle-Drohne
(`MISSING_PARTS` mit der Liste vom Server, der Server gibt die fehlenden Teile, zweiter Versuch `APPLIED`), dann einen
Code mit Rahmen aus `propwash_example` („auf diesem Server nicht aktiv“, ohne Ersatz `UNKNOWN_PARTS`), mit
„Ersatzteile verwenden“ wieder `MISSING_PARTS` → Teile → `APPLIED`. Ergebnis in `runs/smoke-client/jointest/share.txt`.

Just More Parts 1.1.x gesperrt (`docs/namespace-guard.md` §4, JUnit `guard.JustMorePartsLockTest`):
`justmoreparts-<loader>-26.3-1.1.1.jar` aus `propwash-justmoreparts/release/1.1.1/` nach `<loader>/runs/smoke-client/mods`
bzw. `<loader>/runs/smoke-server/mods` kopieren (nur lesen, nach dem Lauf wieder entfernen) und `runClientSmoke` und
`runServerSmoke` beider Loader starten (Fabric mit `CI=1`, sonst öffnet Fabric sein Fehlerfenster und wartet): Der
Start bricht nach 1 bis 3 s ab, bevor eine Welt entsteht. Fabric meldet „Incompatible mods found!“ und „Mod 'Propwash FPV'
(propwash) 0.6.0-dev.5 is incompatible with any version before 1.2.0- of mod 'Propwash: Just More Parts'
(justmoreparts), yet a conflicting version is present: 1.1.1!“ (eigener Grundtext ist in `fabric.mod.json` nicht
möglich; der von Fabric errechnete Lösungsvorschlag „Replace mod 'Propwash FPV' … with version 0.4.0 or later“ führt in
die Irre), NeoForge den Ladefehler „Mod propwash is incompatible with justmoreparts below 1.2.0-alpha / Currently,
justmoreparts is 1.1.1 / The reason is: Just More Parts 1.1.x does not work with Propwash 0.6.0 – please update to Just
More Parts 1.2.0. (Just More Parts 1.1.x passt nicht zu Propwash 0.6.0 – bitte auf 1.2.0 aktualisieren.)“; der
NeoForge-Client bleibt danach im Fehlerbildschirm stehen und wird von Hand beendet. Ohne das Jar starten beide
Rauchtests normal (gemessen 21.10.: Server-Rauchtest und Client-Rauchtest beider Loader PASS).

Sharecodes im Chat (`docs/sharecode-chat.md`): JUnit `tools/fastcheck.sh test --select-package
de.lardtom.propwash.share.client.chat`; im Selbsttest `share:` der Schritt „share: chat – …“
(`share.chat.ShareChatSelfTests`), Bildschirmfotos mit `-PpropwashScreenshots=true` und
`'-PpropwashSelftestOnly=bildschirmfotos: Sharecode im Chat'` nach `docs/screenshots/share-chat/`. Beitrittstest
`PROPWASH_JOINTEST_SHARE_CHAT=1` an beiden Prozessen (`share.chat.ShareChatJoinCheck` am Server schreibt 3 s nach dem
Beitritt per `/say` eine Zeile mit PW3-Code und per `tellraw` eine mit PW2-Code, nach 15 s noch einmal;
`share.chat.ShareChatJoinProbe` am Client findet den Code klickbar, klickt ihn im offenen Chat und prüft den Tab
„Sharecode“ mit dem Code, ohne etwas zu übernehmen):

```sh
cd <WT> && PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_SHARE_CHAT=1 JAVA_HOME=… …/mcslot.sh ./gradlew :fabric:runServerSmoke
cd <WT> && PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=share-chat PROPWASH_JOINTEST_SHARE_CHAT=1 \
  PROPWASH_JOINTEST_EXPECT='joined|share chat link ok|share chat import open' JAVA_HOME=… …/mcslot.sh ./gradlew :fabric:runClientSmoke
```

### Rennsystem 0.6.0, Teil A (Wertung, Flächen, Verschieben, Rennstart)

Spieler-Doku `docs/race-system.md`, Entwurf `docs/design/0.6.0/race-system.md`. JUnit: `tools/fastcheck.sh test
--select-class de.lardtom.propwash.race.StandingStartTest --select-class de.lardtom.propwash.race.ScoreAreaTest
--select-class de.lardtom.propwash.race.FreeformLenientTest --select-class de.lardtom.propwash.race.GateNudgeTest
--select-class de.lardtom.propwash.race.RaceFormatMigrationTest --select-class de.lardtom.propwash.race.client.GateEditModelTest`.
Im Spiel: `'-PpropwashSelftestOnly=race: Wertung'` (`race.client.RaceScoringSelfTests`, beide Loader, im Lauf mit
`race:` enthalten): Aufbau 34 Blöcke westlich und 30 Blöcke südlich des Spielers mit Start-Gate, Flagge auf einer Säule
aus drei Blöcken (Wertung 3 nach unten, gesetzt über `GateMounting#edit`), Slalomstange, Gate-Stange (vom Kurs-Werkzeug zur
Vorbeiflug-Stange mit Slalom-Seite gemacht), Wandstange (abgelehnt) und Freiform-Torpfosten ohne Querbalken (ohne
Schleichen abgelehnt, mit Schleichen 3 × 5 Felder); Runde 1,5 Blöcke über dem Boden unter der Flagge mit eigenem
`LapTracker` und über `RaceServer` (Bestenliste); Einstell-Menü mit echtem Mausrad und echten Tasten (Wertung nach unten,
Rolle „umrunden“, Pfeiltasten und Bild↑ verschieben, Rand am Freiform-Gate, Neu abtasten); `ScoreAreaRenderer` beim Bauen,
in der Nähe und am Start-Gate, Schalter aus; Kursdatei mit `"format": 2` und Wertung, NBT hin und zurück; FPV am
Start-Gate: Reset bei scharfer Drohne ohne Countdown, Scharfschalten danach nur mit „Start beim Scharfschalten“. Mit
`-PpropwashScreenshots=true` (Fabric, Präfix `bildschirmfotos: Wertung`) die Bilder `race_score_*.png`. Der Selbsttest
`reset:` prüft den Rennstart nach dem Reset ebenfalls mit scharfer Drohne.

Beitrittstest (§3, Schalter `PROPWASH_JOINTEST_RACEHOME=1` an beiden Prozessen, Server `dev.RaceHomeJoinCheck`, Client
`race.client.RaceHomeJoinProbe`):

```sh
PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_RACEHOME=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=racehome PROPWASH_JOINTEST_RACEHOME=1 \
  PROPWASH_JOINTEST_EXPECT='joined|racehome reset no start|racehome rearm no start|racehome arm start ok' \
  JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
```

Der Server baut 3 s nach dem Beitritt 24 Blöcke östlich zwei Gates als Kurs, setzt den Homepoint 5 Blöcke vor das
Start-Gate und startet FPV. Der Client schaltet dort scharf, schickt den Reset wie die Taste (`HomeResetInput#request`) und
verlangt 100 Ticks lang weder Aufstellung noch Countdown, schaltet danach ohne „Start beim Scharfschalten“ erneut scharf
(wieder nichts) und mit der Einstellung (Aufstellung am Server). Dasselbe mit `:neoforge:` (Port + 1).

### Rennsystem 0.6.0, Teil B (Durchgänge, Nachbarn, Stapeln)

Spieler-Doku `docs/race-topology.md`, Entwurf `docs/design/0.6.0/race-system.md` (§4.B3). JUnit: `tools/fastcheck.sh test
--select-class de.lardtom.propwash.race.CourseLoopTest --select-class de.lardtom.propwash.race.GateNeighboursTest
--select-class de.lardtom.propwash.race.GatePolesTest` (Durchgänge, Gegenrichtung je Art, Zwillinge im `LapTracker`,
Durchflug-Ereignisse im `LapVerifier`, Kursdatei und Netz mit `reverse`, Prüfsumme alter Kurse; Einrasten, geteilte
Pfosten, Ring, Deckel, Plätze auf Gates und Tauchwürfeln). Im Spiel: `'-PpropwashSelftestOnly=race-topo:'`
(`race.client.RaceTopologySelfTests`, beide Loader, angemeldet nach `race: Wertung`): Platz 70 Blöcke westlich und 40
nördlich des Spielers; Schleife A → B → A rückwärts mit dem Kurs-Werkzeug (zweiter Klick von hinten), Nummern „1 · 3←“
beim Client, Runde beim Client und am Server (`RaceServer`, Bestenliste), A vorwärts statt rückwärts ist eine Meldung
„falsche Richtung“ ohne „ausgelassen“; Reihe aus drei Gates per `GateItem#useOn` (Klick einen halben Block daneben rastet
ein), je Pfosten eine Kollisionsbox, Lage gesperrt, Drehen um 180° auf der Stelle; Würfel aus vier Gates (drei per Klick
über Eck eingerastet) und Deckel (liegende Vorliebe, Klick auf das Südgate), Kurs Start → Deckel abwärts → Ostseite mit ⇄
rückwärts als Ausgang, Runde beim Client und am Server; Flagge per Klick auf die Balkenmitte, Slalomstange auf eine Ecke
des Tauchwürfels, Runde durch das Gate und über ihm um die Flagge; Abbauen: Nachbarn bekommen ihren Pfosten zurück, der
Deckel bleibt, die Flagge kommt mit dem Gate ab. Mit `-PpropwashScreenshots=true` (Fabric, Präfix
`bildschirmfotos: Topologie`) die Bilder `race_topo_loop`, `race_topo_row`, `race_topo_cube`, `race_topo_flag_on_gate`
und `race_topo_course_window`.

Geteilte Pfosten nur einmal gezeichnet und Einrasten gedrehter Gates (Befund PATTY-4): JUnit `tools/fastcheck.sh test
--select-class de.lardtom.propwash.race.GateNeighboursRotatedTest --select-class de.lardtom.propwash.race.client.GateTilesTest`
(Einrasten Pfosten auf Pfosten in 18 Richtungen, Ring und Deckel gedreht, ungleich gedrehte Gates rasten nicht ein;
`GateTiles`: auf einer geteilten Seite kein Teil, auch keine Pfosten-Lichter, unter dem Deckel kein oberer Balken, jeder
Pfosten von Reihe und Würfel von genau einem Gate gezeichnet). Im Selbsttest `race-topo:` zusätzlich eine Reihe und ein
Würfel bei 30° per Klick (`race-topo: Gedreht`, Deckel mit derselben Drehung) und `race-topo: Darstellung` (am echten
`GateRenderer`: Reihe, Würfel und beide gedrehten Aufbauten haben je vier Pfosten, jeden zeichnet genau ein Gate samt
Lichtern, unter dem Deckel weder oberer Balken noch dessen Lichter); dazu die Bilder `race_topo_row_post`,
`race_topo_cube_corner`, `race_topo_turned_row` und `race_topo_turned_cube`.

Beitrittstest Schleifen-Rennen mit zwei Piloten (§3, Schalter `PROPWASH_JOINTEST_RACELOOP=1` an beiden Prozessen, Server
`dev.RaceLoopJoinCheck`, Client `race.client.RaceLoopJoinProbe`):

```sh
PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_RACELOOP=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=raceloop PROPWASH_JOINTEST_RACELOOP=1 \
  PROPWASH_JOINTEST_EXPECT='joined|raceloop passes ok|raceloop client lap ok|raceloop verdict counted|raceloop board 2 pilots' \
  JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
```

Der Server baut 3 s nach dem Beitritt 24 Blöcke östlich und 20 Blöcke über dem Boden die Schleife A → B → A rückwärts
(Kurs-Werkzeug des beigetretenen Spielers, zweiter Klick von hinten) und fliegt sie für einen zweiten, simulierten Piloten
(`PwLoopB`, Spieler ohne Client) und für den beigetretenen Spieler über `RaceServer#onDroneMoved`; beide Runden müssen
angenommen werden („Runde von … am Server angenommen“). Der Client prüft die Nummern „1 · 3←“ am Gate A, zählt die
Schleife mit dem synchronisierten Kurs im eigenen `LapTracker` und wartet auf die Bestenliste mit beiden Piloten. Zwei
echte Clients braucht der geschützte Lauf (§7, `mp3-ok`).

Kurs-Elemente sind nicht verschiebbar (Befund GATEPUSH-1): `'-PpropwashSelftestOnly=race-topo: Kolben'`
(`race.client.GatePushSelfTests`, beide Loader, angemeldet nach `RaceTopologySelfTests`, Platz 80 Blöcke östlich und 90
nördlich des Spielers, etwa 10 s): Gate, Flagge, Slalomstange, Tauchwürfel, Gate-Stange und Freiform-Gate in einem Kurs
„Kolbenprobe“; ein normaler Kolben drückt von unten gegen jedes Element, ein klebriger Kolben schiebt einen Schleimblock
und ein normaler Kolben einen Honigblock von der Seite gegen das Gate und fährt wieder ein – Lage am Server, beim Client
und in den Kursdaten unverändert, kein Schwung (`getDeltaMovement`); Wasser und Lava fließen von der Seite hinein, je eine
Explosion an jedem Element, der Spieler steht in der Flagge, Schweine, eine Lore und ein Boot fahren hinein – nichts
bewegt sich; zuletzt wird das Gate gespeichert, mit verschobener `Pos` aus den Speicherdaten neu erzeugt
(`EntityType.create`, derselbe Weg wie beim Chunk-Laden) und muss im ersten Tick auf die Kurslage zurück, mit genau einer
Logzeile „… und ist zurückgesetzt“ und derselben Lage beim Client. Ohne den Fix scheitern Kolben, Elemente und Laden, im
Kräfte-Schritt der Schwung aus dem Wasser.

Beitrittstest Kolben (§3, Schalter `PROPWASH_JOINTEST_GATEPUSH=1` an beiden Prozessen, Server `dev.GatePushJoinCheck`,
Client `race.client.GatePushJoinProbe`):

```sh
PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_GATEPUSH=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=gatepush PROPWASH_JOINTEST_GATEPUSH=1 \
  PROPWASH_JOINTEST_EXPECT='joined|gatepush late join ok|gatepush machine seen|gatepush present ok' \
  JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
```

Der Server baut 2 s nach dem Start, also vor dem Beitritt, 24 Blöcke östlich und 12 Blöcke über dem Boden den Kurs
„Kolbenprobe“ aus Gate A und Gate B (Chunks fest geladen) und zündet einen Kolben unter Gate A; 5 s nach dem Beitritt
schiebt ein klebriger Kolben einen Schleimblock gegen Gate B und zieht ihn wieder zurück. Der später verbundene Client
muss Gate A an der Lage aus den Kursdaten sehen (ohne den Fix: Client am alten Ort, Server und Kurs 1,01 Blöcke höher),
der anwesende Client Gate B unverändert; bewegt sich ein Gate am Server oder in den Kursdaten, schreibt der Server eine
Fehlerzeile und der Rauchtest scheitert. Dasselbe mit `:neoforge:`.

Gate-Menü mit Tabs und Tab Position: `'-PpropwashSelftestOnly=race-topo: Position-Tab'` (`race.client.GateNudgeTabSelfTests`,
beide Loader, angemeldet nach `GatePushSelfTests`, Platz 150 Blöcke östlich und 150 nördlich des Spielers, etwa 5 s): ein
Gate und eine Slalomstange; in der Anzeige ohne freie Maus stehen auf jeder Seite die Tabs „Gate“, „Redstone“, „Position“
nebeneinander in einer Zeile, Tab wechselt 1 → 2 → 3 → 1, Umschalt+Tab von 1 auf 3, ohne das Gate zu ändern, und die
Fußzeile nennt die Tab-Taste; der Tab Position erklärt Schrittweiten, Pfeiltasten und Drehen mit dem Namen des ersten Tabs
und der Taste von „Gate drehen“, die anderen Seiten nicht; jede Seite von Gate und Slalomstange passt in beiden
Darstellungen in 480 × 270 und 320 × 240, das Gate-Menü bleibt bei 480 × 270 ungedrängt; mit freier Maus öffnet ein Klick
auf jeden Tab seine Seite. Ohne den Fix scheitern Tabs, Erklärung und Klick. Bilder (nur Fabric, mit
`-PpropwashScreenshots=true` und `'-PpropwashSelftestOnly=race-topo: Position-Tab,bildschirmfotos: Position-Tab'`):
`race_nudge_tab_hud`, `race_nudge_tab_cursor`, `flight_school_gate_menu_position`, jeweils auch `_de`. Das Kapitel prüft
JUnit `GateMenuSceneTest` (fünf Schritte, Gate rastet im Schritt Position auf 1/16, Texte nennen 1/16, 1/4 und Position).

Stapelregeln (Befund PTP-1): JUnit `tools/fastcheck.sh test --select-class de.lardtom.propwash.race.GateStackRulesTest`
(breiteres Gate in Sitzhöhe eines schmaleren bildet keinen Stapel und steht auf eigenen Beinen, auch um 180° gedreht;
gleich breite und schmalere stapeln wie bisher; Position Y +0,5, Verbreitern, Drehen, schmaleres oder niedrigeres Gate
unter einem breiteren und ein fünftes Gate werden abgelehnt, Überlappung knapp unter der Sitzhöhe nicht; ohne den Fix 3
von 4 der ursprünglichen Fälle rot) und `GateStackingTest` (zu breite Größenpaare liegen nur noch in Sitzhöhe). Im Spiel:
`'-PpropwashSelftestOnly=race-topo: Stapelregeln'` (`race.client.GateStackRulesSelfTests`, beide Loader, angemeldet nach
`GateTurnSelfTests`, Platz 170 Blöcke westlich und 230 südlich des Spielers, Welt-Regel „Kurs-Elemente frei platzieren“
für die Dauer an, etwa 5 s): beide Gates eines Stapels melden beim Verbreitern und bei Position Y −0,5 im Menü
`STACKED` und am Server `STACK_LOCKED`; ein breites Gate (Öffnung 5,5) knapp unter der Oberkante eines schmalen meldet bei
Position Y +0,5 `TOO_WIDE` mit „Ein Gate passt nur auf ein gleich breites oder breiteres Gate“, der Server
`STACK_TOO_WIDE` und lässt es stehen, schmal gemacht passt es; ein alter Stapel aus rc.1 (breites Gate genau in
Sitzhöhe) steht auf Client und Server einzeln auf eigenen Beinen, das Menü prüft den neuen Zustand (breiter abgelehnt,
schmal frei, unten breiter frei, unten schmaler abgelehnt); ein großes Gate-Item auf einem Block in Sitzhöhe eines
mittleren Gates wird abgelehnt, ein kleines setzt sich und stapelt. Ohne den Fix scheitern die letzten drei Schritte
(`FREE statt TOO_WIDE`, alter Stapel `STACKED`, großes Gate gesetzt). Bilder (nur Fabric, mit `-PpropwashScreenshots=true`
und `'-PpropwashSelftestOnly=race-topo: Stapelregeln,bildschirmfotos: Stapelregeln'`): `race_stack_rules_refused`
(auch `_de`, Seite Position nach Ablauf des Zeilen-Tooltips) und `race_stack_rules_own_legs`.

Gestapelte Gates drehen (Befund RC3P-3, `docs/race-topology.md` §8): JUnit `tools/fastcheck.sh test --select-class
de.lardtom.propwash.race.GateStackTurnTest` (gedrehtes Gate auf dem Querbalken bei 5° bis 270° und auf gedrehtem unteren
Gate: Stapel, unten `LEGS`, oben `MOUNTED`, keine Überschneidung, Unterkante auf dem Balken; Stapel aus drei gedrehten
Gates; Nachrutschen behält die Auflage; breiteres Gate gedreht `TOO_WIDE`; gedreht in Sitzhöhe des gemeinsamen Balkens
kein Stapel, 180° wie bisher; Platzieren in jedem Schritt 90°, 45°, 15° und 5° bis achtmal, Drehpunkt Mitte der
Auflage; Mitdrehen der Gates darüber bei jeder Drehung, Zurückdrehen auf den gemeinsamen Balken, 24 × 15° ergibt den
Ausgangsstapel; `GateMounting#headingOnly`; Runde mit `LapTracker` durch zwei und drei Gates bei jeder Drehung über
`StackTurnRoute` (Kreisbahn um den Stapel); gedrehtes Gate zählt ab dem eigenen Balken, der Balken kollidiert;
Kursdatei mit `turn` lesen und schreiben, Netz-Codec, alter Stapel mit gleicher Prüfsumme wie bisher; ohne den Fix 4 von
5 der ersten Fälle rot). Im Spiel: `'-PpropwashSelftestOnly=race-topo: Stapel drehen'` (`race.client.GateStackTurnSelfTests`,
beide Loader, angemeldet nach `GateStackRulesSelfTests`, Platz 230 Blöcke westlich und 170 südlich des Spielers, etwa
10 s): zwei Gates (oberes per echtem Klick mit Dreh-Vorliebe 45°, der Gate-Hinweis erkennt es) und drei Gates (großes,
mittleres gleich ausgerichtet, kleines mit Vorliebe 90°); Auflage auf dem Querbalken ohne Schweben und Überschneidung mit
Hitbox und Kollision ab dem Balken (Client und Server); Dreh-Taste 45°, 90°, 15°, 5°, 90° mit Rückkehr auf den gemeinsamen
Balken bei 180°, Lagewechsel, Breite und Position `STACK_LOCKED`, Gate-Menü stellt 45° ein; Gate-Menü beim Client: obere
und mittlere Gates `FREE` beim Drehen, `STACKED` beim Verbreitern, unteres Gate um 30° mit „Übernehmen“ (echtes Paket),
beide Gates darüber drehen mit; Runde durch beide Stapel beim Client und am Server; Kollision und Hitbox des gedrehten
Gates; Abbau des mittleren Gates lässt das obere gedreht obenauf fallen. Ohne die Suche bis zur Oberkante in
`GateEntity#gateAbove` scheitern 9 von 12 Schritten (Stapel 1 statt 2). Angepasst: `race: Gates an Ort und Stelle drehen`
(gestapeltes Gate dreht mit der Taste um 90° quer obenauf statt um 180°) und `race: Gates frei drehen und Größe ändern`
(diagonales unteres Gate dreht mit 15° samt dem Gate darüber und lässt sich im Menü zurückdrehen). Bilder (nur Fabric,
mit `-PpropwashScreenshots=true` und `'-PpropwashSelftestOnly=race-topo: Stapel drehen,bildschirmfotos: Stapel drehen'`):
`race_stack_turn_45`, `race_stack_turn_90`, `race_stack_turn_menu`.
Stand nach Rebase auf integration (RC3P-3): JUnit 4901 gefunden, 4887 grün, 0 Fehler (`GateStackTurnTest` 13 von 13),
fastcheck all OK, strip_comments ohne Änderung, Kalibrierung byte-gleich (6 von 6 Dateien); Fabric 104 von 104 Schritten
(`race-topo:` ganz, die angepassten und die Stapel-Gruppen von `race:`, `race: Abdeckung – Gate-Stapel/Gate-Hinweis`,
`ui-scale:` 854×480 englisch und deutsch ohne Befund, Bilder), NeoForge 96 von 96; Beitrittstest `STACKGATE=2` auf beiden
Loadern und `STACKGATE=1` auf Fabric angenommen, Server-Rauchtest beider Loader PASS.

Vorschau-Geist beim Stapeln (Befund RC3P3-1, `docs/race-topology.md` §8): JUnit
`tools/fastcheck.sh test --select-class de.lardtom.propwash.race.client.GateStackPreviewTest --select-class
de.lardtom.propwash.race.client.GateKeyRouteTest` (Geist nach jedem Tastendruck in 90°, 45°, 15° und 5° bei vier
Drehungen des unteren Gates: Drehung zum unteren Gate, Sitzhöhe, Fuß wie das gesetzte Gate laut `GateStacking#base`,
Schritt wie `turnedBy` am Boden; Blick von hinten um 180° gedreht; Schleichen wechselt die Lage wie `nextMount`, Wand und
Decke bleiben; Tasten-Zweig `TURN_STACK`/`NEXT_MOUNT_STACK` mit Gate in der Hand am stehenden Gate, `ROTATE_GATE` mit
Kurs-Werkzeug und bei Gates ohne Stapel, Fenster und Flug unverändert). Im Spiel:
`'-PpropwashSelftestOnly=race-topo: Stapel-Vorschau'` (`race.client.GateStackPreviewSelfTests`, beide Loader, angemeldet
nach `GateStackTurnSelfTests`, Platz 260 Blöcke westlich und 200 südlich des Spielers, etwa 3 s): mittleres Gate am
Boden, Spieler mit kleinem Gate-Item davor, Vorliebe 45°; der Blick auf das Gate zeigt den Geist (`GatePreview#target`)
genau wie `GateStacking#stackOn`, frei, mit Fuß `MOUNTED` und dem Hinweisfeld wie am Boden („Floor · standing · +45°“ /
„R: rotate · sneak + R: change mount“); die echte Dreh-Taste dreht die Vorliebe auf 135° (auch am Server), das Gate im
Blick bleibt am Server gleich, Geist und Hinweis folgen; der echte Rechtsklick setzt das Gate genau an die Stelle des
Geists; Schleichen + Taste wechselt die Lage der Vorschau auf liegend und zurück, die Gates bleiben; mit dem
Kurs-Werkzeug dreht die Taste das Gate im Blick wie bisher, die Vorliebe bleibt. Ohne den Fix 5 von 7 Schritten rot
(„Geist beim Blick auf das Gate: fehlt“, „Dreh-Taste mit Gate-Item dreht die Vorschau nicht: 45.0° -> 45.0°“, danach
kein Geist und kein Stapel). Bild (nur Fabric, mit `-PpropwashScreenshots=true` und
`'-PpropwashSelftestOnly=race-topo: Stapel-Vorschau,bildschirmfotos: Stapel-Vorschau'`): `race_stack_preview`.
Stand (RC3P3-1): JUnit 4922 gefunden, 4908 grün, 0 Fehler (`GateStackPreviewTest` 3 von 3, `GateKeyRouteTest` 12 von
12), fastcheck all OK, strip_comments ohne Änderung, Kalibrierung byte-gleich (6 von 6 Dateien); Fabric und NeoForge je
`race-topo:` 51 von 51 Schritten und die Gruppen `race: Taste R`, `race: Gates …`, `race: Lagen`, `race: Gate-Stange`,
`race: Abdeckung – Gate-Stapel/Gate-Hinweis`, `race: Leiter`, `race: Kurs-Elemente` 44 von 44; Beitrittstest
`STACKGATE=2` auf beiden Loadern angenommen, Server-Rauchtest beider Loader PASS.

Dreh-Taste am Würfel-Deckel (Befund AUTO56-1, `docs/race-topology.md` §8): JUnit `GateStackPreviewTest` (gleicher
Aufruf wie oben) prüft, dass `GateKeyRoute#stackPreference` die liegende Vorliebe in Stellung 0 und 1 bei 90°, 45°, 15°
und 5° nicht ändert und Schleichen weiter auf stehend wechselt; am Deckel eines Rings aus vier Gates
(`GateStacking#lid`) zeigt `GatePreview#status` vor und nach der Taste „abwärts“ (auch rot), `#keys` nur
`propwash.race.lid.hint`; ein liegendes Gate am Boden zeigt weiter die Vorliebe und `propwash.race.mount.hint`. Im Spiel
hängt `race-topo: Stapel-Vorschau` vier Schritte „Deckel: …“ an (Ring 6 Blöcke östlich des ersten Gates, Spieler mit
mittlerem Gate-Item vor der Südseite, Vorliebe liegend): Deckel-Geist liegend abwärts mit Hinweis „Floor · lying flat ·
downwards“ / „Sneak + R: change mount“; die echte Dreh-Taste lässt Vorliebe (auch am Server), Hinweis, Geist und das Gate
im Blick unverändert; bei Vorliebe liegend aufwärts bleibt der Hinweis abwärts und der echte Rechtsklick setzt den Deckel
genau wie den Geist, abwärts. Ohne den Fix JUnit 1 von 4 und auf Fabric 3 der 4 Deckel-Schritte rot („Tasten-Zeile am
Deckel nennt das Drehen“, „Dreh-Taste ändert am Deckel die Vorliebe: liegend Stellung 0 -> FLOOR_FLAT Stellung 1,
Hinweis „Floor · lying flat · upwards“, Deckel-Geist down“, „Hinweis zeigt „… upwards“ statt … „downwards““).
Stand (AUTO56-1): JUnit 4925 gefunden, 4911 grün, 0 Fehler (1 übersprungen, 13 abgebrochen wie im Integrationsstand;
`GateStackPreviewTest` 6 von 6), fastcheck all OK, strip_comments ohne Änderung, Kalibrierung byte-gleich (6 von 6
Dateien); Fabric und NeoForge je `race-topo:` und `race: Taste R` 57 von 57 Schritten, Server-Rauchtest beider Loader
PASS.

### Renndaten: Kurs-ID, Schnittstelle und JSON-Spiegel (0.6.0)

Spieler-Doku `docs/race-data.md`, Schnittstelle und Schema `docs/api/race-data.md`, Entwurf
`docs/design/0.6.0/race-system.md` §11. JUnit: `tools/fastcheck.sh test --select-package de.lardtom.propwash.race.data`
(Kurs-ID parsen, vergeben und reparieren; Migration der Kursdatei-Formen 0.4.0, 0.5.0 und 0.5.2 mit sofortigem Speichern
und stabiler ID danach; Stabilität bei Umbenennen, Rundenzahl, Umbau und neuer Layout-Nummer; Schnappschüsse, Zonen-Hinweis,
Abfragen, Ereignisse; JSON gegen die Vergleichsdateien in `common/src/test/resources/race-data/`, neu schreiben mit
`PROPWASH_RACE_DATA_GOLDEN=1`; `CourseArgumentTest`: Kurs-Angabe der Befehle unter `/propwash race` per ID, Name und
alter Nummer, gleiche Namen, Vorschläge ohne Nummern, Befehl mit ID im Hinweis; Namen nur aus Ziffern oder in der Form
einer Kurs-ID werden nicht vorgeschlagen, jeder Vorschlag trifft seinen Kurs, auch bei `/propwash course id`; Kurs-Angabe
in Hilfetexten ist die ID, Befehle damit bringen keinen Hinweis auf Nummern, Befunde AUTO6-1 und AUTO6-2;
`/propwash race name <kurs> "Alpha Loop"` speichert `Alpha Loop`, Namen in Anführungszeichen aus alten Kursdateien werden
beim Laden bereinigt, gleich heißende Kurse werden nicht per Name vorgeschlagen und `delete`/`reset` treffen nie einen
anderen Kurs, auch nicht ungereinigt gesetzte Namen, Befund AUTO6B-1). Die echten Testwelten aus `world-fixtures` prüft
`LegacyWorldFixturesTest` nur mit
`PROPWASH_FIXTURES=<ordner mit 0.4.0, 0.5.0, 0.5.2>` (IDs für alle Kurse, Prüfsummen, Bestenlisten und Archive wie in
`expected.json`, Spiegel mit allen Layouts); ohne Variable wird er übersprungen.

Im Spiel: `'-PpropwashSelftestOnly=race-data:'` (`race.client.RaceDataSelfTests`, beide Loader): Kurs aus zwei Gates
24 Blöcke östlich und 20 Blöcke über dem Boden mit dem Kurs-Werkzeug, ID v4 am Server und gleich beim Client; Kurs-Fenster
mit Zeile „ID“, Klick auf „Kopieren“ legt die ID in die Zwischenablage und zeigt „Kopiert“; Umbenennen, Rundenzahl,
Umsortieren mit Archiv, Verschieben eines Gates ändern die ID nicht, das alte Layout ist über `PropwashApi.race()` unter
der ID abrufbar; `/propwash course id` mit Nummer, Name und ohne Angabe ergibt drei Chatzeilen mit anklickbarer ID
(`ClickEvent.CopyToClipboard`), ein unbekannter Name eine Fehlermeldung; jedes `<course>` unter `/propwash race`
(`leaderboard`, `archive`, `delete`, `laps`, `mode`, `name`, `reset`, `remove`) ist Text mit Vorschlägen (ID und Name,
bei `reset` in Anführungszeichen), `/propwash race leaderboard` über ID, Name und Nummer zeigt dreimal die Bestenliste,
`archive` über die ID das Archiv, und nur die Nummer bringt genau einen Hinweis mit dem Befehl samt ID zum Kopieren
(Befund OWNP6-5); die leere Bestenliste des umgebauten Kurses nennt „/propwash race archive <Kurs-ID>“, der Befehl zeigt
das Archiv ohne Hinweis auf die Nummer (Befund AUTO6-1); zwei simulierte Piloten fliegen Runde und Rennen
über `RaceServer`, geprüft werden Schnittstelle, Ereignisse in `ExampleRaceListener` und die Datei
`<welt>/propwash/race-data/leaderboards/<id>.json`. Mit `-PpropwashScreenshots=true` (Fabric) die Bilder
`race_data_course_window_en`, `race_data_command_en`, `race_data_course_window_de`, `race_data_command_de`.

Beitrittstest Renndaten mit zwei Piloten (§3, Schalter `PROPWASH_JOINTEST_RACEDATA=1` an beiden Prozessen, Server
`dev.RaceDataJoinCheck`, Client `race.client.RaceDataJoinProbe`):

```sh
PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_RACEDATA=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=racedata PROPWASH_JOINTEST_RACEDATA=1 \
  PROPWASH_JOINTEST_EXPECT='joined|racedata ok client' JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
```

Der Server baut 3 s nach dem Beitritt einen Kurs aus zwei Gates und lässt einen simulierten Piloten (`PwDataB`) und den
beigetretenen Spieler je eine Runde und ein Rennen über eine Runde fliegen. Danach prüft er Schnittstelle (Kurs und
Bestenliste nach ID), Ereignisse im Beispiel-Listener (Runden, Rennen, Bestzeiten, Rekord) und den JSON-Spiegel (Datei
unter der Kurs-ID mit beiden Piloten, Zeit gleich der Schnittstelle) und schickt das Ergebnis als Chatzeile
`[race-data] ok key=… json=2 timeMs=…`. Der Client wartet auf diese Zeile und den Kurs mit derselben ID, ruft
`/propwash course id <nummer>` auf und verlangt die anklickbare ID im Chat.

**Bestenliste ohne Grenzen (Befund LBALL-1).** JUnit `race.data.LeaderboardAllTest` und `race.data.LeaderboardPagesTest`
(`tools/fastcheck.sh test --select-package de.lardtom.propwash.race.data`). Im Selbsttest `race-data:` füllt
`race.client.RaceDataBoardSelfTests` die Bestenliste des Testkurses mit 120 Zeiten ohne (eigene auf Platz 87, drei
Klassen, 15 Piloten zusätzlich langsamer in `freestyle_5`) und 60 mit Flughilfe (eigene auf Platz 40) und prüft: erstes
Paket mit einer Seite, Bild ab lädt alle Plätze in Seiten zu 50 nach (mindestens drei Anfragen), jede Zeit gleich dem
Server, Klasse `freestyle_5` mit 15 nachgeladenen Plätzen; Klick auf die Kopfzeile klappt „Bestzeiten“ und „Mit
Flughilfe“ ein und aus, der Zustand bleibt bei Klassenwechsel, Kurswechsel und neu geöffnetem Fenster; „Meine Zeit (87)“
springt zur eigenen Zeile, „Meine Zeit“ für Platz 40 klappt den Abschnitt dafür auf; bei GUI-Skalierung 2, 3 und 4 sind
Kopfzeile und „Meine Zeit“ ganz zu sehen, mindestens drei Zeilen gezeichnet und `LayoutProbe.audit` ohne Befund. Mit
`-PpropwashScreenshots=true` (Fabric) die Bilder `race_data_board_expanded`, `_collapsed`, `_scrolled`, `_scale2`,
`_scale3` und `_scale4`, je `_en` und `_de`. Der Beitrittstest Renndaten füllt nach seinen Prüfungen dieselbe
Bestenliste (120 und 60, mit den zwei Piloten) am dedizierten Server; der Client öffnet sie, scrollt bis zum Ende und
vergleicht eigene Plätze und die Summe aller Zeiten mit der Zeile des Servers (`own=…/… sum=…/…`). Erwartung jetzt
`PROPWASH_JOINTEST_EXPECT='joined|racedata ok client|racedata board ok'`.
**Gemessen am 29.10.:** `race-data:` auf Fabric und NeoForge je 21 von 21 gefilterten Schritten (erstes Paket 51 von 120
ohne und 50 von 60 mit Flughilfe, danach drei Seiten; GUI-Skalierung 2/3/4 bei 854x480, 570x320, 427x240 mit 22, 12 und
7 Zeilen), Beitrittstest Renndaten auf beiden Loadern `racedata board ok records=120 assisted=60 pages=3`.

**„Alle“ mit einer Zeile je Pilot und Klasse (Befund ALLBOARD-1).** JUnit `race.data.LeaderboardClassRowsTest` (5;
scheiterte vor dem Fix: 71 statt 112 Zeilen in „Alle“, die 30-s-Zeile der zweiten Klasse fehlte in Liste, Flughilfe,
Teilen-Code und Schnittstelle, im Ereignis Platz 1 statt 2): Sortierung, Klassen- und Gesamtplatz je Zeile, eine Zeile je
Pilot innerhalb einer Klasse, erstes Paket und Seiten mit beiden eigenen Zeilen, „Meine Zeit“ auf der besten, Teilen-Code
und Geist je Zeile, Schnittstelle, JSON-Spiegel und Platz in `PERSONAL_BEST`/`LAP_COMPLETED`. `LeaderboardTest`,
`LeaderboardPagesTest`, `RaceSnapshotsTest` und `RaceDataJsonTest` prüfen die neue Regel (Vergleichsdatei
`leaderboards/5f0c2a8e-….json` mit Annas zweiter Klasse auf Platz 3). Die große Bestenliste des Selbsttests zählt in
„Alle“ jetzt 135 Zeilen (die 15 Piloten mit einer Zeit in `freestyle_5` stehen auf Platz 121 bis 135, jeder mit seiner
schnelleren Klasse weiter oben). Neuer Schritt „race-data: „Alle“ mit Bestzeit je Pilot und Klasse …“: eigene
Whoop-Zeit 20 s auf Platz 1, Race-Zeit 30 s auf Platz 5, Ben in zwei Klassen, mit Flughilfe Platz 1 und 3; je Zeile
Klasse, Klassen- und Gesamtplatz, eigener Teilen-Code und Geist, für die eigenen Zeilen Kopieren, Replay und Gegen fliegen
bereit, „Meine Zeit (1)“ und die Klassennamen ganz zu sehen, Klasse Race mit drei Zeilen (eigene auf Klassenplatz 3,
Gesamtplatz 5), `/propwash race leaderboard` und `BoardQuery.laps()` in derselben Reihenfolge. Bild
`race_data_board_classes_{en,de}`.
**Gemessen am 02.11.:** Fabric mit Bildschirmfotos 42 von 42 gefilterten Schritten PASS, NeoForge 22 von 22; JUnit-Suite
nach dem Rebase auf den Merge von PTP-3 4708 gefunden, 4694 grün, 0 Fehler. Der Beitrittstest Renndaten bleibt gleich
(seine Piloten fliegen je eine Klasse).

### Punkt-zu-Punkt-Rennen und Rechte am Kurs (race-p2p, 0.6.0)

Doku: `docs/race-p2p.md`.

- JUnit: `race.PointToPointTrackerTest`, `race.CourseRightsTest`, `race.CourseAccessTest`, `race.data.PointToPointDataTest`
  (`tools/fastcheck.sh test --select-class de.lardtom.propwash.race.PointToPointTrackerTest …`). Die Vergleichsdateien
  unter `common/src/test/resources/race-data/` tragen `"mode": "laps"`.
- Selbsttest `'-PpropwashSelftestOnly=race-verdict:'` (`race.client.RaceVerdictSelfTests`, beide Loader, unter 1 min, Ablauf in
  `docs/race-verdict.md`): Kurs mit Stange neben dem nächsten Gate am echten `RaceServer`, Warnung mit Knopf im Chat, Abkürzung
  zählt auf beiden Seiten, `/propwash race trim` kürzt, danach „verpasst“ im Client und `MISSED` vom Server, spätere Runden
  zählen wieder („2 von 3“), Gate-Menü warnt und kürzt, Ideallinie je Kurs, Lauflicht. Mit `-PpropwashScreenshots=true` die Bildfolge
  `race_verdict_chevrons_7`, `_14`, `_0` (hellster Pfeil fern, Mitte, am Gate) und `race_verdict_hud_*`, `race_verdict_gate_menu_*`,
  `race_verdict_course_menu_*` auf Englisch und Deutsch.
- Selbsttest `'-PpropwashSelftestOnly=race-p2p:'` (`race.client.RaceP2pSelfTests`, beide Loader, unter 1 min): Kurs aus
  drei Gates im Abstand von 30 Blöcken 24 Blöcke östlich und 20 Blöcke über dem Spieler (`dev.RaceP2pJoinCheck.build`),
  Umschalten im Kurs-Fenster per Klick (Rundenzahl ausgeblendet, Bestenliste der Runden als Layout 1 archiviert),
  Namensschilder „Start“ und „Ziel“, Flug mit dem Client-Tracker (eine Zwischenzeit, Ziel nach 3,00 s, HUD „Ziel: …“, kein
  Rundenzähler), Lauf am Server mit einem zweiten Spieler (`dev.RaceP2pJoinCheck.fly`, Rundenmeldung 60 Blöcke vom Start
  am Ziel) samt Bestenliste, Schnittstelle und JSON-Spiegel mit `point_to_point`, zurück auf Runden (Layout 3), Rechte mit
  einem Gast (Paket und Befehl abgelehnt, Kurs ohne Besitzer nur für Operatoren). Bildschirmfotos (nur Fabric, mit
  `-PpropwashScreenshots=true`): `race_p2p_hud_finish_en`, `_de`, `race_p2p_course_window_en`, `_de`. Die Gruppe
  `race: Kurs bearbeiten` prüft die neuen Rechte für Kurse ohne Ersteller mit.
- Beitrittstest (§3):

  ```sh
  PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_RACE_P2P=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
  PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=racep2p PROPWASH_JOINTEST_RACE_P2P=1 \
    PROPWASH_JOINTEST_EXPECT='joined|race_p2p_server=ok|race_p2p_b_button=view b_can_edit=false|race_p2p_b_packet_denied=yes b_command_denied=yes|race_p2p_op_button=edit op_can_edit=true|race_p2p_op_edit=yes|race_p2p_op_revoked=yes' \
    JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
  # danach jointest.stop in fabric/runs/smoke-server
  ```

  `dev.RaceP2pJoinCheck` (Log `[Beitrittstest] race-p2p: …`) legt 60 Ticks nach dem Beitritt mit Spieler A (`PwP2pA`, am
  Server) einen Kurs an, schaltet als Besitzer auf Punkt zu Punkt, fliegt ihn (9 s) und prüft die Rechte des beigetretenen
  Spielers B am Server; Ergebnis `[race-p2p] ok course=… mode=point_to_point … other=not_owner other_denied=yes
  ownerless=ownerless,ownerless owner=allowed` im Chat. 25 s danach macht er B zum Operator und nimmt ihm das Recht im
  selben Tick wieder ab, in dem B auf Runden umgeschaltet hat (Log `… ist kein Operator mehr (nach dem Umschalten auf
  Runden)`); aufgeräumt wird, sobald B den Server verlassen hat, 3 s nach dem Umschalten oder spätestens nach 70 s, und
  beim Stopp des Servers nimmt er ein noch gesetztes Recht zurück. `ops.json` ist danach leer, auch wenn `jointest.stop`
  direkt nach dem Client kommt; ein zweiter Lauf mit demselben Spieler (NeoForge: immer `Dev`) scheitert so nicht an
  „… ist schon Operator“. `race.client.RaceP2pJoinProbe` prüft bei B: Knopf „Kurs ansehen“, abgelehntes Paket (Modus und
  Name bleiben) und abgelehnter Befehl `/propwash race mode`, danach als Operator „Kurs bearbeiten“ und Umschalten auf
  Runden, zuletzt, dass der Server ihm das Operator-Recht binnen 2 s wieder nimmt (`race_p2p_op_revoked=yes`). Dasselbe
  mit `:neoforge:`.

### Rennwertung und freies Platzieren (race-rules, 0.6.0)

Doku: `docs/race-rules.md`.

- JUnit: `race.RaceRulesTest`, `race.RaceRuleSetTest`, `race.RaceStandardScoringTest`, `race.CourseOverlapTest`,
  `race.data.RaceRulesDataTest` (`tools/fastcheck.sh test --select-class de.lardtom.propwash.race.RaceRulesTest …`).
  `RaceRulesTest` listet jede Propwash-Welt-Regel als rennrelevant oder neutral; eine neue Regel ohne Eintrag dort lässt den
  Test fehlschlagen.
- Selbsttest `'-PpropwashSelftestOnly=race-rules:'` (`race.client.RaceRulesSelfTests`, beide Loader, unter 1 min): Kurs
  aus zwei Gates 24 Blöcke östlich und 20 Blöcke über dem Spieler (`dev.RaceDataJoinCheck.build`), Runden am Server
  vorgespielt (`dev.RaceRulesJoinCheck.fly`, Startzeiten steigen je Runde); „Disarm on crash“ aus und an je rennfrei,
  Tooltip und Flughilfe-Prüfung nennen die Regel neutral; Rennstandard = Serverregeln (seit rc.3,
  `docs/personal-rules.md`): Server-Wind 0 rennfrei mit Regelsatz und API `PropwashApi.race().serverRules`, persönlicher
  Wind 0 bei Server-Wind 1 mit Flughilfe „persönliche Regeln“, derselbe Wert wie der Server rennfrei, Vorabwarnung beim
  Client; Stufe Fest sperrt Zonen und persönliche Abweichung (gespeichert, wirkungslos), wieder Spieler wirkt, Befehl
  `/propwash rules tier`; Tab Welt-Regeln mit Schwerkraft 80 %: Vergleich nennt sie, „Aktuelle Regeln übernehmen“
  und „Propwash-Standard“ über die Knöpfe, Server und Client gleich; Testplattform aus Glatter Steinplatte 15 × 15 Blöcke,
  48 Blöcke östlich und 30 Blöcke über dem Spieler: großes Gate, darin ein Gate und eine Fahne nur mit der Regel
  „Kurs-Elemente frei platzieren“, Meldung ohne Regel nennt sie, Blöcke bleiben Hindernis, Vorschau beim Client frei.
  Bildschirmfotos (nur Fabric, mit `-PpropwashScreenshots=true`): `race_rules_standard`, `race_rules_standard_de`.
- Beitrittstest (§3):

  ```sh
  PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_RACE_RULES=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
  PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=racerules PROPWASH_JOINTEST_RACE_RULES=1 \
    PROPWASH_JOINTEST_EXPECT='joined|race_rules_server=ok|race_rules_synced=battery0|race_rules_editable=false|race_rules_denied=yes|race_rules_refused=yes|race_rules_preflight=battery|race_rules_reset=clean' \
    JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
  # danach jointest.stop in fabric/runs/smoke-server
  ```

  `dev.RaceRulesJoinCheck` (Log `[Beitrittstest] race-rules: …`) fliegt 60 Ticks nach dem Beitritt mit dem echten
  Spieler zwei Runden (Disarm aus 9 s, an 8 s), holt einen zweiten Spieler `PwPersonalB` auf den Server (eingebettete
  Verbindung wie beim Zuschauerzahl-Test), stellt Akku und Wind auf Stufe Spieler, gibt dem echten Spieler unendlich
  Akku und PwPersonalB Wind 0 (Server: Akku 100 %, Wind 1) und fliegt je eine Runde: beide mit Flughilfe genau ihres
  Grundes; danach Server-Akku 0 (der persönliche Wert zählt als Serverwert) und PwPersonalB zurückgesetzt, beide
  rennfrei, die alte 8-s-Runde bleibt rennfrei. Chat:
  `[race-rules] ok disarm_off=clean disarm_on=clean a_battery0=assisted[personal_rules:battery_scale=0/100] b_wind0=assisted[personal_rules:wind=0/1] a_server0=clean b_reset=clean old=clean`;
  25 s danach räumt er auf. `race.client.RaceRulesJoinProbe` prüft beim Client Stufe und eigenen Wert (Sync), dass
  Freigabe ändern ohne Recht und ein Wert für eine nicht freigegebene Regel abgelehnt werden (Meldungen im Chat, nichts
  geändert), die Vorabwarnung mit Akku 0 statt 100 und nach dem eigenen Zurücksetzen ohne Flughilfe.
  Dasselbe mit `:neoforge:`.
- Kurs-Elemente in Blöcken (27.10.): Schritt „race-rules: Gate und Fahne in Blöcken nur mit Welt-Regel …“ legt auf der
  Testplattform 3 × 3 Steinblöcke mit einer Fahne darin und 24 Blöcke westlich, 20 Blöcke über dem Spieler einen Kurs
  aus zwei Gates an, dessen zweites mit dem linken Pfosten in einer Steinsäule steht. Ohne die Regel lehnt die
  Platzierung beide mit der bisherigen Meldung ab (auch ein um 45° gedrehtes Gate im Stein ist blockiert), mit der Regel
  stehen beide, die Vorschau beim Client ist frei, und eine Runde durch das Gate im Block zählt rennfrei. „Blöcke bleiben
  Hindernis“ im Absatz zum Selbsttest gilt damit nur noch ohne die Regel.
- Oberfläche der persönlichen Regeln (rc.3 Teil 2, `docs/personal-rules.md` §8): JUnit `race.PersonalRulesUiTest`;
  `race-rules:` hat fünf Schritte mehr (`race.client.PersonalRulesUiSelfTests`: Schalter „Freigabe“, Tab „Meine Regeln“,
  Presets, Vorabwarnung mit „Race Legal anwenden“, Abzeichen „Serverregeln“) und mit `-PpropwashScreenshots=true` die
  Bilder `personal_rules_tiers`, `…_tiers_tooltip`, `…_my_rules`, `…_my_rules_locked`, `…_preflight`, `…_badge`,
  `…_badge_tooltip` (je auch `_de`). Die Presets der Schritte ersetzen kurz `config/propwash/personal_presets.json` des
  Test-Clients und stellen sie danach wieder her. ui-scale: Fall „Konfigurator mit persönlichen Regeln“ (alle Physikregeln
  für Spieler freigegeben, Wind fest) prüft Welt-Regeln, Meine Regeln und die Flughilfe-Prüfung, der Fall „Konfigurator
  mit gesperrten Welt-Regeln“ auch „Meine Regeln“:
  `'-PpropwashUiScaleCases=Konfigurator mit persönlichen,Konfigurator mit gesperrten'` (etwa 1 min, Zwischenfenster
  ausschließen wie oben). Die zones-open-Schritte zum Häkchen „Zonen“ prüfen seitdem den Schalter „Freigabe“ (Namen der
  Schritte unverändert).
- Befunde PERS1B-1, PERS1B-2 (03.11.): `race-rules:` hat zwei Schritte mehr (`race.client.PersonalRulesFlightSelfTests`,
  nach den Schritten der Oberfläche): „Server stellt Wind während der Runde um …“ fliegt am Server zwei Runden über
  `dev.RaceRulesJoinCheck.fly` mit Haltepunkt zur Rundenmitte (Bewegung über die Drohne, damit die Regel-Zeitleiste
  mitläuft); der Server stellt dort Wind 1 auf 0. Ohne persönliche Regeln zählt die 6,0-s-Runde rennfrei, mit
  persönlichem Wind 0 die 5,5-s-Runde mit Flughilfe `personal_rules:wind=0/1`. „Heimflug ohne Pilot …“ setzt eine
  Drohne 12 Blöcke über dem Spieler mit Heimflugplan des Spielers (persönliche Schwerkraft 50 %, Server 100 %) und
  verlangt im Flug am Server `SimTuning#gravityScale` 0,5. JUnit `race.PersonalRulesTest` hat 15 Fälle (Urteil im
  Moment, zusammengelegte Abschnitte, Gründe der Server-Zeitleiste), `race.BoardRowWireTest` prüft, dass eine
  Netzprotokollnummer genau ein Format der Zeilen der Bestenliste hat (17 = 0.6.0-rc.2, 18 = ab rc.3; 19 und 20 wie 18).
- Beitrittstest mit zwei echten Clients (Befund PERS1B-4, `docs/personal-rules.md` §7): Server mit
  `PROPWASH_JOINTEST_RACE_RULES=2`, dann beide Clients aus demselben Arbeitsbaum, der zweite mit eigener Run-Kennung
  (Client-Slots 1 und 2, Server in Slot 4):

  ```sh
  PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_RACE_RULES=2 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
  E='joined|race_rules_two role=|race_rules_two_synced=own|race_rules_two_flight=own|race_rules_two_assist=own|race_rules_two_lap=finished|race_rules_two_server=ok|race_rules_two_board=ok'
  PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=racerules-two-a PROPWASH_JOINTEST_RACE_RULES=2 PROPWASH_JOINTEST_EXPECT="$E" \
    JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke -PpropwashDevName=PwPersonalA
  PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=racerules-two-b PROPWASH_JOINTEST_RACE_RULES=2 PROPWASH_JOINTEST_EXPECT="$E" \
    JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke -PpropwashDevName=PwPersonalB -PpropwashRunTag=b
  # beide Clients gleichzeitig (B etwa 10 s nach A), danach jointest.stop in fabric/runs/smoke-server
  ```

  `dev.RaceRulesJoinCheck` wartet, bis `PwPersonalA` und `PwPersonalB` auf dem Server sind, stellt Server-Wind 1 und
  Akku 100 %, gibt Akku und Wind auf Stufe Spieler, baut den Kurs des Selbsttests 24 Blöcke östlich und 20 Blöcke über
  A und startet für beide FPV mit je einer Race-Drohne (Parkplatz neben dem Kurs). `race.client.RaceRulesTwoJoinProbe`:
  A setzt über das Paket unendlich Akku, B Wind 0; jeder prüft, dass nur sein eigener Wert ankommt
  (`race_rules_two_own={battery_scale=0}` bzw. `{wind=0}`), dass sein Flug am Client damit rechnet
  (`SimTuning`: A unbegrenzter Akku bei Wind 1, B Wind 0 mit Akku), dass die Flughilfe-Prüfung genau seinen Grund nennt,
  schärft und fliegt nach dem Zeichen des Servers eine echte Runde (8 m/s, Zustandspakete wie beim Fliegen, erst A, dann
  B); der Server wertet sie aus seinen eigenen Gate-Durchflügen. Server-Log und Chat
  `[race-rules] two result ok a=assisted[personal_rules:battery_scale=0/100] b=assisted[personal_rules:wind=0/1]`, beide
  Clients sehen in der Bestenliste beide Zeilen mit genau ihrem Grund; 25 s danach räumt der Server auf. Ergebnisse in
  `fabric/runs/smoke-client/jointest/racerules-two-a.txt` und `fabric/runs/smoke-client-b/jointest/racerules-two-b.txt`.
  Gemessen am 03.11.: Fabric und NeoForge je PASS, Server etwa 2 min, Clients je unter 1,5 min. Dasselbe mit
  `:neoforge:`. `PROPWASH_JOINTEST_RACE_RULES=1` bleibt der Test mit einem Client und `PwPersonalB` am Server.
- Befund PROTO18-1 (03.11.), Regeln nach Befund AUTO51-1: JUnit `race.ProtocolVersionTextsTest` prüft die Texte zur
  Version aus `gradle.properties` gegen `Constants.PROTOCOL_VERSION` und das vorige Release in `docs/api/changes.md`
  (`docs/RELEASING.md` §3). Nennen `CHANGELOG.md` unter *Compatibility*, `docs/RELEASE-NOTES.md`, `docs/api/changes.md`
  (Übersicht, Überschrift, Unterabschnitte), `docs/RELEASING.md` §3, die Texte der Add-on-API, das Design-Dokument des
  Releases oder `docs/modrinth/listing.md` eine Nummer für diese Version, muss es die heutige sein. Steigt die Nummer,
  verlangt er „client and server must both run <version>“ im CHANGELOG, „**Netzprotokoll N** (<vorher>: M)“ in den
  Release-Notizen und in `docs/api/changes.md` den Unterabschnitt „Netzprotokoll N“, der vor seiner Liste den Grund und
  jede übersprungene Nummer nennt (hier 17 bis rc.2), sofern diese keinen eigenen Unterabschnitt hat. Bleibt sie gleich,
  darf der CHANGELOG das nicht verlangen (wie 0.5.1: „0.5.0 and 0.5.1 play together“). Die Abschnitte des Releases
  sind erst mit der sauberen Nummer Pflicht (`docs/RELEASING.md` §4 Schritt 1), in `-dev`- und `-rc`-Ständen werden
  sie nur geprüft, wenn es sie gibt. Zehn weitere Fälle prüfen diese Regeln an einem kleinen Repo im Temp-Ordner,
  darunter ein Entwicklungsstand nach dem Release, ein Patch mit gleichem und ein Release mit steigendem Protokoll.
  Insgesamt sind es 16 Fälle. Die Website prüft die Nummer ihrer Texte mit `node tools/test-protocol-texts.mjs`.
- Befunde WA-W2f-2 bis -4 (05.11.), Serverregeln als Rennstandard (`docs/personal-rules.md` §4):
  „drone: Welt-Regel Schwerkraft wirkt im Flug“ prüft nach Schwebegas und Regel beim Client am Server, dass die Drohne
  des Piloten über Serverregeln, persönliche Regeln und Zonen (`PersonalRulesServer#base` und `ZoneField` wie in
  `ServerFlight`) mit 12,753 m/s² rechnet, `QuadcopterEntity#physicsModified` falsch bleibt und `RaceServer#serverRules`
  gegen den Rennstandard keinen Grund nennt; vorher verlangte der Schritt die Abweichungs-Marke und war seit PERS1 rot
  („Server kennt die veränderte Physik der Drohne nicht“). „bildschirmfotos: Rennen im FPV mit Flughilfe“ (und
  „… deutsch“) wartet beim Client auf die persönliche Schwerkraft 130 % (`ClientPersonalRules#assisting`) statt auf
  veränderte Serverregeln; das Bild zeigt den Grund „personal rules differ from the server rules: Gravity 130% instead
  of 100%“. Der ui-scale-Fall „Konfigurator mit persönlichen Regeln“ merkt sich beim Öffnen Zonen-Freigabe, Liste
  „Spieler“ und Werte aller Spieler (`dev.PersonalRulesDev#snapshot`) und stellt sie beim Schließen wieder her
  (`#restore`); vorher blieb Wind auf Fest, und auf NeoForge scheiterten danach B7 („Wind-Zone wirkt nicht“) und drei
  Schritte `zones-open:`. `race-rules:` hat dafür einen Schritt mehr („ui-scale-Fall … stellt beim Schließen Werte,
  Liste „Spieler“, Freigabestufen und Zonen-Freigabe wieder her“, Ausgangszustand persönliche Schwerkraft 50 % und
  Propwash fest; ohne Fix rot mit Wind fest, Propwash frei, ohne Wert und Liste). Gegenprobe:
  `'-PpropwashSelftestOnly=ui-scale: Fenster 854x480 mit GUI-Skala 2,addon-api-17: B7,zones-open:'` (etwa 6,5 min).
  Stand (WA2F): JUnit 4925 gefunden, 4911 grün, 0 Fehler (1 übersprungen, 13 abgebrochen wie im Integrationsstand),
  fastcheck all OK, strip_comments ohne Änderung, kein Hauptcode geändert (Kalibrierung und Goldens unberührt);
  Gegenprobe Fabric und NeoForge je 42 von 42, Schwerkraft-Kette mit `race-rules:` und `flughilfe:` je 41 von 41,
  Fabric mit Bildschirmfotos ui-scale 854x480 und „Rennen im FPV mit Flughilfe“ 20 von 20, Server-Rauchtest beider
  Loader PASS.

### Freecam-Drohne

Freecam (`docs/freecam.md`): `'-PpropwashSelftestOnly=freecam:'` (zwölf Schritte in `freecam.client.FreecamSelfTests`
auf einem eigenen Platz bei Spawn + (40, −36) mit einer Steinwand, beide Loader, rund 1,5 min nach dem Laden der Welt;
die Freecam-Einstellungen gehen für den Lauf auf Standard und danach zurück): Regel im Standard (aus) → F6 verweigert
mit Grund `RULE_OFF`, in der Übersicht des K-Menüs ist „Freecam starten“ ausgegraut, die Fußzeile nennt den Grund,
„Zur Welt-Regel“ öffnet den Tab Welt-Regeln mit hervorgehobener, sichtbarer Zeile `allow_freecam`; Regel an → F6
startet an der Augenposition (Abstand 0), Kurs in Blickrichtung, waagerecht, unscharf geparkt 40 Ticks, die Spielkamera
folgt, die Bewegungstaste W gibt der Freecam Gas, der Spieler bewegt sich nicht, `ClientNet#sentCount` bleibt gleich
(kein Propwash-Paket), kein neues Drohnen-Entity am Client und am Server; Ende per F6, Y und Knopf, danach Kamera am
Spieler; scharf durch drei Blöcke Wand ohne Crash, die Kamera steckt dabei im Block und bleibt, wo die Freecam ist
(`CameraClip` aktiv, kein Kamera-Schutz); an der Grenze der geladenen Chunks (aus dem Chunk-Cache des Clients)
Stopp und Warnung `freecam_edge` im OSD, schräg weiter gleitet sie entlang; Wind 3 → über 0,5 m Drift in 5 s im
Angle-Modus, Wind 0 → unter 0,1 m; K öffnet genau Freecam, Rates, Empfänger und Modi auf dem Tab Freecam, Kopfzeile
„Freecam · 5″“, die Suche findet nichts aus anderen Tabs; Preset 7″ im Flug übernimmt Position und Tempo exakt, die
Sim hat die Masse des 7″, scharf; OSD ohne Akku- und Link-Elemente, Name „FREECAM · 5″“, Vanilla-HUD aus, OSD aus →
nichts gezeichnet; Schaden → Ende `HURT`; Regel aus im Flug → Ende im selben Tick, danach Start verweigert. Fenster:
`'-PpropwashSelftestOnly=ui-scale:' -PpropwashUiScaleCases=Freecam-K-Menü` (Zwischenfenster dabei ausschließen, §1
„ui-scale“; vier Tabs in jeder Fenstergröße und beiden
Sprachen; die Übersicht mit „Zur Welt-Regel“ und „Freecam starten“ prüft der Fall „Konfigurator“). Bildschirmfotos
(nur Fabric): `-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos: Freecam'` (Übersicht mit
ausgegrautem Start, OSD vor der Wand, Warnung an der Grenze, K-Menü bei 854 × 480 und 320 × 240, englisch und deutsch).
Beitrittstest: `PROPWASH_JOINTEST_FREECAM=1` an beiden Prozessen. Der Server (`dev.FreecamJoinCheck`, Log
`[Beitrittstest] Freecam: …`) schaltet die Regel 20 s nach dem Beitritt an, 15 s später wieder aus und loggt 5 s danach
die Zahl neuer Drohnen; der Client (`freecam.client.FreecamJoinProbe`) versucht den Start bei Regel im Standard, startet
nach dem Einschalten, fliegt 10 s und wartet auf das Ausschalten. Erwartung:
`PROPWASH_JOINTEST_EXPECT='joined|freecam_denied=default_off|freecam_started=yes|freecam_server_packets=0|freecam_rule_off=ended'`,
`PROPWASH_JOINTEST_LABEL=freecam`, Server-Log `[Beitrittstest] Freecam: 0 neue Drohnen`. JUnit:
`tools/fastcheck.sh test --select-package de.lardtom.propwash.freecam`.
Ohne Platz, nach dem Aufräumen: „freecam: Sprung aus der Suche ab der Übersicht …“ (Befund WA-W2-2) öffnet das K-Menü auf
der Übersicht, springt per Enter auf die Totzone im Tab Rates und nach dem Weg zurück auf die Regel `allow_freecam` im Tab
Welt-Regeln; jedes Mal muss der Treffer Fokus und Markierung haben und im Bild stehen, die Fußzeile zeigt die
Freecam-Knöpfe nicht mehr, das Suchfeld gibt den Fokus ab. Dieselbe Ursache prüfen „ui: Einstellungssuche mit
Vorschlägen …“, „ui: Regler tippen – Sprung aus der Suche …“, „zonen: Tab Zonen …“ und „video: Kamerawinkel im
K-Menü – Suche …“.
Gleich danach „freecam: Sprung aus der Suche über die Grenze Übersicht ↔ Tab …“ (Befund AUTO10-1): ab der Übersicht per
Enter auf die erste Tastenbelegung im Tab Tasten (Treffer der Art Taste mit Fokus, Markierung und im Bild, Fußzeile ohne
Freecam-Knöpfe), dann per Tab-Treffer zurück in die Übersicht; dort hat der Tab in der neu aufgebauten Tab-Leiste den
Fokus, die Fußzeile zeigt die Freecam-Knöpfe wieder, das Suchfeld gibt den Fokus ab.
An der Chunk-Grenze (Befund WA-W2r-5) prüft `freecam.client.FreecamEdgeCheck` in jedem Tick die Chunk-Spalte der Freecam
selbst: Fehler nur, wenn ihr Chunk nicht geladen ist. Die Grenze der Chunk-Reihe des Spielers taugt nicht als Maß, denn
beim Gleiten wechselt die Freecam nach 3,5 Blöcken in die Nachbarreihe, die unter Last schon einen Chunk weiter geladen
sein kann (dann fliegt sie zu Recht weiter). Das Gleiten braucht Grenzkontakte und mindestens 1 Block in z; die Notiz nennt
die Reihen mit ihren Grenzen. JUnit: `FreecamEdgeCheckTest` (Gleiten mit `FreecamBounds` nachgespielt) und
`FreecamBoundsTest` (Chunks kommen und gehen, die Freecam bleibt in geladenen Spalten).
Scharfschalten in den Freecam-Schritten (Befund WAD-4): Die Sim der Freecam rechnet nur in Bildern
(`FreecamSession#onFrame`), die Schritte laufen in Ticks. Dauert ein Bild länger (Last, Ruckler), laufen danach mehrere
Ticks ohne Bild dazwischen. Kam dabei der Schalter vor dem ersten Bild der neuen Sim oder zusammen mit dem Gas an,
verlangte die Sim das Zurücksetzen des Schalters (Blocker 3: Schalter nicht zurückgesetzt, Gas nicht unten), und
„scharf in der Luft mit 4 m/s“ lief in die Zeitgrenze. `FreecamSelfTests#armAt` zählt deshalb Bilder
(`FreecamSession#frames`) statt Ticks: Platz setzen, Schalter aus, bis die Sim ein Bild gerechnet hat, dann Schalter an
mit Gas unten, bis sie nach einem weiteren Bild scharf ist, erst dann Gas; die Meldung nennt Blocker und Stufe. Der
Schritt „freecam: Ruckler beim Scharfschalten …“ hält den Client vor dem Start 400 ms an, sodass Start, Schalter und Gas
danach in einem Schwung Ticks ohne Bild dazwischen kommen; mit dem alten Ablauf nach Ticks war er 2 von 2 rot (Blocker 3).

### Werfen, Inhalt sichtbar, Stöße, alle Drohnen (B1–B4)

Add-on-API 1.7, B1–B4 (`docs/accessory-gaps.md`): `'-PpropwashSelftestOnly=addon-api-17: B1,addon-api-17: B2,addon-api-17: B3,addon-api-17: B4'`
(beide Loader, Klasse `interact.client.AccessoryGapsSelfTests` in der Arena von `interact:`, Beispiel `ExampleDispenser`;
der Präfix `addon-api-17: B1` erfasst B10 mit). B1 am Server über `triggerAction` ohne Client-Paket: Pfeil `FIRED` nach
vorn mit dem Spieler als Besitzer und 0,44 N·s, eigener Pfeil geschützt, `consume(false)` ohne Verbrauch, Wurftrank,
Windkugel (3,5 N·s, die abgestellte Drohne rollt zurück), TNT `PLACED` mit Vanilla-Hüpfer, Knochenmehl `USED` auf Weizen,
Wassereimer `REFUSED` und bleibt im Slot, Aktion 5 und Drohne ohne Zubehör → `false`; Abenteuermodus: TNT und
Knochenmehl `DENIED`, Pfeil `FIRED`; ein langsamer eigener Pfeil, den die Drohne einholt (wie bei Gegenwind), fliegt
durch die Hülle (`OwnShots.passed`), ebenso nach 40 Ticks ein zweiter Pfeil und eine eigene Windkugel, die zuvor 5 Blöcke
voraus lag (wie im schnellen Vorwärtsflug eingeholt, Befund MDA-4); Schaden 2,0 durch den eigenen Pfeil und 1,0 durch die
eigene Windkugel (Verursacher Spieler) kommt nicht an, dieselbe Windkugel aus der Hand des Spielers zerlegt sie; Schlag eines
Zombies und Pfeil eines Skeletts lösen `DRONE_DAMAGED` mit dem Mob als Verursacher aus (friedlich → leicht für den
Schritt). Im Sichtflug: Windkugel per Zubehör-Aktion 1, Rückstoß im Flugmodell über 1 m/s zurück; TNT bei 5 m/s
Vorwärtsflug mit dem Tempo der Drohne (Abweichung unter 1,5 m/s); Tab „Modi“ nennt „Werfer feuern“, der Hinweis kommt
beim ersten Flug genau einmal, beim zweiten nicht. B2: Speer unten und Rakete oben im Render-Zustand
(`DroneRenderState#accessoryDisplay`), nach Aktion 3 „Boost“ ist der Slot leer und nur der Speer bleibt; Bildschirmfotos
(nur Fabric, `-PpropwashScreenshots=true`) `accessory_display_side`, `accessory_display_front`,
`accessory_display_boosted` auf einem Platz bei Spawn + (40, 60). B3: Windkugel-Explosion 1,6 Blöcke hinter der
schwebenden Drohne: Listener ohne Skala → unter 0,3 m Versatz, Skala 1 → über 0,5 m, abgebrochen → unter 0,3 m.
B4: `loaded(level)` enthält neue Drohnen, ist unveränderlich, gleich der Entity-Suche, entfernte fehlen sofort;
`cameraPose` einer schwebenden Drohne drei Blöcke vor einem Enderman trifft dessen Kopf nach der Vanilla-Formel, um 30°
gedreht nicht, leer an anderen Entities und ohne Kamera. Server-Rauchtest: `[Server-Rauchtest] Werfer: …` (TNT eines
Gasts im Spawn-Schutz `DENIED`, als Operator `PLACED`, Pfeil ohne Client `FIRED`, Aktion 5 → `false`, `loaded` und
`cameraPose`). JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.accessory.dispense.DispenseMathTest
--select-class de.lardtom.propwash.drone.CameraPoseTest --select-class de.lardtom.propwash.accessory.ItemDisplayTest
--select-class de.lardtom.propwash.drone.KnockbackTest`.
B2 im eigenen FPV-Bild (Befund SPEAR3-1, `interact.client.AccessoryFpvSelfTests`, Platz bei Spieler + (−40, 60)):
`LONG_RANGE` mit Speer-Halterung und Eisenspeer auf Barrieren in einem Raum aus schwarzem Beton (Lichtblöcke, kein
Himmel), FPV-Flug mit Brille. Bilder vor dem Videoeffekt (`CleanFrameTap`) mit Speer, mit Speer ohne Props und Ducts im
FPV-Bild, ohne Speer ohne beide, ohne Speer, noch einmal ohne Speer (Rauschen) und mit einer Vanilla-Item-Anzeige
(`item_display`, Tag `propwash_fpv_spear_control`) in derselben Lage wie der Speer. Grenze 0,1 % des Bildes oder dreimal
das Rauschen; Kontrolle unter der Grenze → „Messung ungültig“. Fabric: mit Speer 1,32 % (x 45–55 %, y 77–94 %),
Kontrolle 1,32 % im selben Bereich, Rauschen 0,00 %; ohne den Fix 0,00 %.
B2 frei in der Luft (Befund AUTO29-1, gleiche Klasse und gleicher Raum, Kamera 0°): Bilder mit und ohne Speer erst über
Barrieren (Nahebene sinkt, Fabric 0,0153), dann über getöntem Glas (`TransparentBlock` ohne Sichtform, Nahebene bleibt
0,05 wie im Flug), dort auch mit Props und Ducts im FPV-Bild aus; Rauschen aus zwei Bildern ohne Speer über Glas. Über
Glas muss der Speer mindestens 90 % der Fläche über Barrieren haben; ist die Nahebene über Barrieren nicht abgesenkt oder
über Glas nicht 0,05, ist die Messung ungültig. Fabric: über Barrieren 4,36 % (x 40–60 %, y 56–83 %), über Glas 4,36 %,
mit Props und Ducts aus 4,36 %, Ausgleich an der Linse ×11,10; ohne den Fix 1,86 % gegen 0,19 %. Seit dem Fix misst
der Schritt „Speer im eigenen FPV-Bild“ mit Speer 5,20 % (x 38–62 %, y 72–100 %), die Kontrolle bleibt bei 1,32 %.
B2 beim Ruck der Kamera (Befund G-SPEAR3-1-2): Kamera-Vibration 1,0 über `FpvJoltProbe.intensity`, Ruck über
`FpvShake#jolt().hit` wie bei einem Aufprall mit 10 m/s; das Bild mit Ruck (Kamera laut `FpvJoltProbe.deviation()` um
mindestens 0,5° gedreht, bis zu acht Versuche) gegen das Bild ohne Ruck, Grenze wie oben. Fabric: Kamera um 2,42°
gedreht, 0,00 % anders; ohne den Fix 2,49° und 0,39 %. NeoForge misst dieselben Flächen (Ruck 2,46°, 0,00 %); ohne
`AccessoryDisplayState#keepNormals` war der Speer dort nach dem Ausgleich dunkler (NeoForge rechnet gebackene Normalen mit
der rohen Normalenmatrix) und über Glas mit 2,81 % gegen 3,71 % unter der Grenze. JUnit: `tools/fastcheck.sh test
--select-class de.lardtom.propwash.drone.client.render.AccessoryLensNearTest` (Sichtbarkeit, Ausgleich, Normalen und Bild
wie ohne Ausgleich).

### Autonomer Flug mit Add-on-Flughilfe (B7)

Add-on-API 1.7, B7 (`docs/addon-autonomy.md`): `'-PpropwashSelftestOnly=addon-api-17: B7'` (beide Loader, 18 Schritte in
`rescue.client.AutonomySelfTests` auf einem Platz bei Spieler + (12, 0) mit Steinboden, einer Zone mit Wind 3 und einer
mit 30 % Schwerkraft, zwei FREESTYLE-Drohnen A und B; braucht das Beispiel-Add-on). A mit Tag `propwash_example_hold`:
Pilot steigt im Testmodus auf 4 m, steigt mit der Funke aus → `ExampleFollow` startet „Halten“, A bleibt 10 s innerhalb
0,5 m. `ExampleFollow.follow` ersetzt das Halten (`REPLACED`), der Spieler wird 20 Blöcke mit 4,3 m/s versetzt → Abstand
höchstens 5 m. Zweiter Add-on-Flug desselben Spielers (B, `takeoff`) → `NO_BUDGET`. Weitere 24 Blöcke durch beide Zonen
→ Wind-Faktor 3 und g unter 4,9 m/s² im Server-Flug, Abstand höchstens 7 m (bei 30 % Schwerkraft beschleunigt die
Drohne langsamer). `land()` → am Boden entschärft, `LANDED`.
A mit Tag `propwash_example_follow`, Pilot steigt wieder aus → „Folgen“ startet, Höhenverlust in 3 s höchstens 0,3 m;
`stop()` → `STOPPED`, Propwash landet. B: Start vom Boden auf 5 m (±0,6), `land()` → `LANDED`; Start auf 2 m, Pilot
übernimmt → `PILOT`. Übernahme im Flug (Befund CAMP-1): B in 4 m im Add-on-Flug, der Pilot übernimmt mit Tastatur,
einmal ohne und einmal mit einer Flughilfe, die die Höhe hält (Gas-Mitte = Schweben) → die Drohne ist von Anfang an
scharf, sackt in 3 s höchstens 0,5 m ab, Tastatur-Gas steht auf Schwebegas bzw. in der Mitte. A mit 16 % Akku und
Homepoint am Startplatz: Start vom Boden → `BATTERY`, Heimflug über rund 45 m,
Landung höchstens 2 m vom Homepoint. B am Boden ohne `takeoff` → `NOT_READY`, ohne Akku → `NOT_ARMABLE`. Neigungsgrenze
(Befund GCCA3P-1): B mit `pid.angleLimitDeg` 30 → `DroneView#angleLimitDeg()` am Server 60° vorher und 30° danach, am
Client 30°; Add-on-Flug vom Boden auf 4 m mit einer Flughilfe, die `FlightState#angleLimitDeg()` liest und 0,6 s vollen
Nick-Knüppel in ANGLE gibt → 30° gemeldet, größte Neigung höchstens 4° neben 30°; danach `stop()`, Landung, Tune zurück.
Rund 3 min nach dem Laden der Welt. JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.rescue.AutonomousAssistTest`.
Welt-Regeln des Budgets (Befund OWN2810P-1, ein Schritt mehr, also 19): A fliegt einen Add-on-Flug, mit
`autonomous_flights_per_player` 2 ergibt B am Boden ohne `takeoff` `NOT_READY` (passt ins Budget), mit 1 und mit 0 ergibt B
mit `takeoff` `NO_BUDGET`, ebenso mit `autonomous_flights_total` 1; die Meldung in der Aktionsleiste (`Hud`) nennt den
Wert bzw. bei 0 die Regel; danach beide Regeln zurück auf den Standard. Schnell allein:
`'-PpropwashSelftestOnly=addon-api-17: B7 – Testfeld,addon-api-17: B7 – Pilot verlässt das FPV: Beispiel,addon-api-17: B7 – Budget,addon-api-17: B7 – Welt-Regeln'`
(rund 1 min nach dem Laden der Welt).

Beitrittstest „Autonomie ohne Besitzer“ (`dev.AutonomyOfflineJoinCheck`, Client `dev.client.AutonomyOfflineJoinProbe`):
`PROPWASH_JOINTEST_AUTONOMY_OFFLINE=<n>` (Standard 20) am Server startet n-mal an einem eigenen Platz ab x = 2408 einen
Add-on-Flug vom Boden (Flughilfe `propwash_example:offline_cruise`) und lässt den Besitzer trennen, reihum: 6 m hoch mit
6 m/s kurz vor einer Chunk-Kante, diagonal kurz vor einer Chunk-Ecke, in 24 m Höhe ohne Zwangs-Chunks (die Landung dauert
länger als die 15 s, nach denen eine Welt ohne aktives Ticket keine Objekte mehr tickt) und im Heimflug nach
`requestReturnHome()`. Geprüft: Ende `OWNER_OFFLINE` (Heimflug `STOPPED`), die Drohne tickt bis zur Landung ohne Lücke
(höchstens 10 Ticks) und wird nicht entladen, sie landet an Ort und Stelle bzw. höchstens 2,5 m vom Startplatz, 10 Ticks
nach der Landung ist kein Chunk-Ticket mehr gesetzt; jeder Durchgang schreibt eine Zeile
`[Beitrittstest] Autonomie ohne Besitzer i/n (…)`, ein Ausfall eine Fehlerzeile. Der Client mit demselben Schalter trennt
auf die Chat-Zeile `[autonomy-offline] leave i/n away <s>` und verbindet nach `<s>` Sekunden wieder; Erwartung
`PROPWASH_JOINTEST_EXPECT='joined|autonomy_offline=20/20'` (etwa 10 min, Server mit `PROPWASH_JOINTEST_HOLD=3600`; der
Wächter des Rauchtest-Clients richtet sich nach der Zeitgrenze des Beitrittstests). Ohne Client meldet
`PROPWASH_JOINTEST_AUTONOMY_OFFLINE_SIM=1` einen simulierten Besitzer an, der über denselben Weg wie ein Client trennt
(`onDisconnect`); der Server endet danach von selbst (20 Durchgänge etwa 8 min):

```sh
cd <WT> && PROPWASH_JOINTEST_HOLD=1200 PROPWASH_JOINTEST_AUTONOMY_OFFLINE=20 PROPWASH_JOINTEST_AUTONOMY_OFFLINE_SIM=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
```

Beitrittstest „weiter Flug ohne Pilot“ (`dev.AutonomyFarJoinCheck`, Befund cov-cam-b-2): `PROPWASH_JOINTEST_AUTONOMY_FAR=1`
am Server meldet einen simulierten Besitzer an, der am Start bleibt (Sichtweite 6, Simulationsdistanz 5), und fliegt an
fünf Plätzen ab x = 6408 je einen Add-on-Flug vom Boden (Flughilfe `propwash_example:far_cruise`, 8 m hoch): „Korridor“
300 m weit mit 8 m/s über sieben vorab erzeugte Chunk-Reihen (Ziel erreicht, kein Stillstand über 2 s), „Streifen, Regel
an“ 300 m über nur eine erzeugte Reihe wie im Kamera-Test mit der Welt-Regel „Drohnen erzeugen neues Gelände“ an (der
Server erzeugt den Rest; Ziel erreicht, kein Stillstand über 2 s, `FlightState#terrainLimit()` nie wahr), „Streifen,
Regel aus“ derselbe Streifen mit der Regel aus (Ende mit `TERRAIN_LIMIT` höchstens 8 m vor dem ersten Chunk ohne
erzeugte Umgebung, gemessen 4,0 m nach 97 m; die Flughilfe sah vorher `terrainLimit()`; der Besitzer bekam den Hinweis
`propwash.drone.autonomy.ended.terrain_limit`; danach Heimflug und Landung höchstens 4 m vom Start, gemessen 0,0 m),
„Folgen“ hinter dem Besitzer her, der 20 s lang mit 30 m/s davonläuft (höchstens 20 m/s, etwa 400 m), „Streifen, Regel
aus, ohne GPS“ derselbe Streifen mit der Regel aus, über der Drohne entsteht beim ersten `terrainLimit()` eine Decke aus
Stein 3–4 m über ihr bis an den Rand (41 Blöcke breit; Befund GCB2F-1: Ende mit `TERRAIN_LIMIT` und Hinweis, ohne
GPS-Empfang kein Heimflug, Landung höchstens 2 m von der Stelle des Endes, gemessen 0,0 m nach 8,7 s). Geprüft: die
Drohne tickt ohne Lücke (höchstens 10 Ticks), wird nicht entladen, der Flug bleibt bis zum Ziel aktiv, nach dem Ende ist
kein Chunk-Ticket mehr gesetzt; die Regel steht danach wieder an; jede Sekunde eine Zeile mit Chunk, Ladestufe,
Simulation und Tickets. Die Server-Konfig (`config/propwash/server.json` im Run-Ordner) steht für die Prüfung im
Speicher auf dem Standard und danach wieder auf dem alten Stand; vor jedem Start muss der simulierte Besitzer genau bei
„Regel an“ Gelände erzeugen dürfen, sonst FAIL mit den Werten der Konfig (Befund WAC-2: ein Run-Ordner mit
`"generateChunksPermissionLevel": 2` ließ „Streifen, Regel an“ wie „Regel aus“ fliegen, Tickets 6 ab Chunk 406). Ein
Ausfall macht den Rauchtest FAIL; der Server endet danach von selbst (etwa 4 min):

```sh
cd <WT> && PROPWASH_JOINTEST_HOLD=600 PROPWASH_JOINTEST_AUTONOMY_FAR=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
```

Beitrittstest „Heimflug außer Reichweite“ (`dev.AutonomyHomeJoinCheck`, Befund RTH-1): `PROPWASH_JOINTEST_AUTONOMY_HOME=1`
am Server meldet einen simulierten Besitzer an und fliegt an zwei Plätzen ab x = 8408 je einen Add-on-Flug vom Boden
(Flughilfe `propwash_example:home_range`) 40 m vom Homepoint der Drohne weg; dort hält sie 2 s, dann geht der Besitzer
40 m über die Funkreichweite hinaus. „Heimflug unter freiem Himmel“ (10 m hoch): Ende `OUT_OF_RANGE`, danach Propwashs
Heimflug, Landung höchstens 2,5 m vom Homepoint. „Steindecke ohne GPS“ (3 m hoch unter einer Decke aus Stein 5 m über
einem Steinboden, 25 × 25 Blöcke): Ende `OUT_OF_RANGE`, kein Heimflug, Landung höchstens 1,5 m von der Stelle. Nach jeder
Landung ist kein Chunk-Ticket mehr gesetzt. Ein Ausfall macht den Rauchtest FAIL; der Server endet danach von selbst
(etwa 2 min):

```sh
cd <WT> && PROPWASH_JOINTEST_HOLD=600 PROPWASH_JOINTEST_AUTONOMY_HOME=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
```

Für die Beitrittstests nur am Server (`PROPWASH_JOINTEST_AUTONOMY_OFFLINE_SIM`, `PROPWASH_JOINTEST_AUTONOMY_FAR`,
`PROPWASH_JOINTEST_AUTONOMY_HOME`) gilt beim Stopp: Ist die Prüfung noch nicht fertig (`PROPWASH_JOINTEST_HOLD`
abgelaufen oder `jointest.stop` von außen gelegt), meldet der Rauchtest „FAIL <Prüfung>: nach i von n Durchgängen nicht
fertig“, endet sie mit Ausfällen, ebenfalls FAIL statt „PASS dedizierter Server, Beitrittstest beendet“ (Befund WAC-3,
JUnit `dev.ServerSmokeStopTest`). Steht das Ergebnis einer Prüfung kurz bevor (`ServerOnlyCheck#pending`, bisher nur
Kamera-Chunks nach dem Trennen), schiebt `jointest.stop` den Stopp bis zu 10 s auf.

### Zweiter Operator und Gimbal in Aufnahme und Kamerapfad (B8, B9)

Add-on-API 1.7, B8 und B9 (`docs/operator-gimbal.md`): `'-PpropwashSelftestOnly=addon-api-17: B9'` (beide Loader, 14
Schritte in `drone.operator.client.OperatorGimbalSelfTests` auf einem Platz bei Spieler + (18, 0) mit Steinboden; braucht
das Beispiel-Add-on mit `propwash_example:gimbal_front`). FPV mit dem Gimbal vorn, Lage per Sim-Abzug gehalten: bei 30°
Rollen der Drohne rollen Kamerablick und Flashback-Kamerapfad unter 2° (starr über 25°), die Action-Cam nimmt per
`recording().start` mit Stabilisierung `off` auf; `fovDegrees` 40° → horizontal 40°, vertikal deutlich enger, 10° wird
auf 20° gekappt, NaN zurück; `recording().photo` legt ein PNG im Aufnahme-Ordner an. Getrennte FPV- und Gimbal-Kamera
(Befund APICAM-1, Beispiel-Gimbal mit `fpvImage(true)`): im FPV-Bild zeichnet `FpvProps` Props (je etwa 236 in 118
Bildern), im Gimbal-Bild (`showsProps` `false`) 0 Props in 0 Bildern; bei 30° Rollen und FPV-Bild auf dem Schirm
(rollt 33°) liegt der Kamerapfad aus `gimbalRotation` unter 2° und höchstens 1° neben der Gimbal-Kamera; bei
FPV-Bild mit dem Sichtfeld des Baus (130°) nimmt der Kamerapfad die Brennweite der Gimbal-Kamera aus
`gimbalFovDegrees` (40°, Befund AUTO7-1), ohne sie wieder das Sichtfeld des Schirms. Danach eigene Drohne im autonomen
Halten (`ExampleFollow.takeoff`), `spectate` → Zuschauer-Sitzung `WATCHING`, Bild `CONNECTED`, Foto
als zuschauender Besitzer, als Zuschauer dieselbe Props-Prüfung wie beim Piloten; `ExampleOperator.bind` →
Operator-Ansicht, ohne Funke über die Test-Eigenschaft
`propwash.test.operatorInput=1,0,0,0.5,1,4` (`docs/operator-gimbal.md` §9): der Beispiel-Gimbal schwenkt um mehr als
20°, beim Operator und am Server Achse 0 = 1, Gas 0,5, Aktionen 1 und 4, am Server `FlightState#operatorAxis(0)` = 1;
der ungültige Wert `1,0,0` wirkt nicht (Achsen 0, keine Aktion), danach ist die Eigenschaft wie vorher; Esc löst die
Bindung. Gimbal-Kopf in Drittperson (`modelPose` −45°/35°), mit
`-PpropwashScreenshots=true` Foto `gimbal_head` (Fenster 1920×1080). Rund 30 s nach dem Laden der Welt. JUnit:
`tools/fastcheck.sh test --select-class de.lardtom.propwash.drone.operator.OperatorChannelTest --select-class
de.lardtom.propwash.drone.operator.client.GimbalRecordingTest --select-class
de.lardtom.propwash.drone.operator.client.OperatorTestInputTest`.

Beitrittstest (§3): Server mit `PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_OPERATOR=1`, Client mit
`PROPWASH_JOINTEST_LABEL=operator PROPWASH_JOINTEST_OPERATOR=1 PROPWASH_JOINTEST_EXPECT='joined|operator a ok|operator b ok'`.
(a) Der Server lässt eine Gimbal-Drohne 2,5 Blöcke vor dem Spieler auf 4 m steigen und halten und bindet den Spieler
als Operator; der Client schickt Testachsen, loggt die Gimbal-Lage, macht `jointest/operator_operator.png` und gibt
mit Esc ab. (b) Der Server startet für den Spieler FPV auf einer zweiten Gimbal-Drohne und bindet einen simulierten
Spieler (`WorldRulesRights.guest`) als Operator, der 8 s lang erst schwenkt, dann neigt; der Client loggt jede Sekunde
`[Beitrittstest] Operator b: Gimbal-Lage beim Piloten …`. Server-Log `[Beitrittstest] Operator …`.

### Drohnen-Barrieren

Drohnen-durchlässige Barriere und Drohnen-Sperre (`docs/barriers.md`): `'-PpropwashSelftestOnly=barriers:'` (beide Loader,
13 Schritte in `barrier.client.BarrierSelfTests`, `BarrierCourseSelfTests` und `BarrierFlightSelfTests` auf einem eigenen
Platz bei Spawn + (−1600, 1600), rund
1:30 min nach dem Laden der Welt): Eigenschaften (Kreativ-Tab, Härte −1, keine Beute, unsichtbar, Licht 0, kein
Redstone-Leiter, Symbol, Tooltip); Rüstungsständer, Schwein und Item bleiben auf einer Lage durchlässiger Barriere
liegen und fallen durch eine Lage Sperre, ein Pfeil bleibt in der durchlässigen Barriere stecken und fliegt durch die
Sperre; der Spieler läuft (Taste vor) gegen die durchlässige Barriere und durch die Sperre, der Server sieht dieselbe
Stelle; im Kreativmodus Umriss, Treffer beim Anvisieren und Fadenkreuz auf der durchlässigen Barriere mit jeder Hand
(leer, Stein, durchlässige Barriere, Sperre), Symbole nur mit einer der beiden Barrieren in der Hand, im Überleben
(durchlässige Barriere in der Hand, leere Hand) weder Umriss noch Symbole; Himmelslicht
unter einem Dach aus
Barrieren 15, Redstone-Stufe nach oben unter einer Barriere nicht getrennt, Videosignal durch 3 Blöcke 0 dB (Gegenprobe
Stein); Kursbau (Befund PATTY-2, `'-PpropwashSelftestOnly=barriers: Gates'`, rund 1 s): mit Stein, durchlässiger
Barriere, Drohnen-Sperre und einem Rahmen aus durchlässiger Barriere mit einer Sperre unten in der Mitte (wie im
Spieltest) ist ein Freiform-Rahmen 3 × 1 geschlossen (`FreeformFill` OK 3 × 1), Gate und Slalomstange stehen oben auf
dem Block, ein Gate an der Westseite dockt als Wand-Gate an der Blockebene an, ein Gate zwischen zwei Säulen ist frei
und ein Block in seiner Öffnung sperrt den Platz; Drohne mit 6 m/s durch 3 Blöcke durchlässige Barriere (Server nimmt
an, Kamera-Schutz aus, keine Runde mit Hilfe), Abprall
mit 4 m/s und Absturz mit 14 m/s an der Sperre wie an Stein, Geist ohne Recht durch die Sperre wird vom Server
zurückgesetzt (braucht das Beispiel-Add-on, sonst übersprungen). Bildschirmfotos (nur Fabric):
`-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos: Drohnen-Barrieren'` (`barriers_markers`,
`barriers_hidden`, `barriers_outline` mit dem Umriss der Sperre ohne Barriere in der Hand, `tooltip_drone_pass_barrier`,
`tooltip_drone_block_barrier`, jeweils auch `_de`). Server-Rauchtest bei
jedem Lauf (`barrier.BarrierServerChecks`, Zeile „Drohnen-Barrieren: …“). JUnit:
`tools/fastcheck.sh test --select-package de.lardtom.propwash.barrier` (mit `BarrierNamesTest`: neue Namen, kein alter
mehr in Sprachdateien und Doku).

Beitrittstest (§3): Server mit `PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_BARRIERS=1`, Client mit
`PROPWASH_JOINTEST_LABEL=barriers PROPWASH_JOINTEST_BARRIERS=1
PROPWASH_JOINTEST_EXPECT='joined|barriers drone_pass=ok|barriers drone_block=ok|barriers player_pass=ok|barriers player_block=ok|barriers markers=ok'`.
Der Server (`dev.BarrierJoinCheck`, Log `[Beitrittstest] Barrieren: …`) baut 5 Blöcke südlich des Spielers 3 Blöcke
durchlässige Barriere und dahinter 2 Blöcke Sperre, startet FPV mit einer Freestyle-Drohne und setzt sie nach dem
Scharfschalten vor die durchlässige Barriere; der Client (`barrier.client.BarrierJoinProbe`) fliegt mit 6 m/s durch sie
hindurch bis an die Sperre. Danach beendet der Server die Sitzung, stellt den Spieler vor eine Wand aus durchlässiger
Barriere, dann vor eine aus Sperre; der Client läuft jeweils 30 Ticks gegen die Wand. Zum Schluss gibt der Server
Kreativmodus und eine durchlässige Barriere in die Hand,
der Client zählt die Symbole und prüft den Umriss.

### Gerade hängendes Zubehör (Add-on-API 1.7)

`docs/accessory-hang.md`: `'-PpropwashSelftestOnly=addon-api-17: gerade hängend'` (beide Loader, drei Schritte in
`interact.client.AccessoryHangSelfTests`, mit Bildschirmfotos sechs; Beispiel `ExampleDispenser`, Platz bei Spawn +
(−40, 60), der Spieler schaut als Zuschauer zu). Geprüft: `propwash_example:spear_holder` hat `hangsStraight`, der
Booster nicht; im Render-Zustand am `freestyle_5` (Speer-Halterung und Booster) und am Whoop (nur Speer-Halterung)
unten Neigung 0° und Streckung 1, der Speer ist sichtbar, oben am `freestyle_5` weiter 22°; am Server zeigt
`MountPose` unten waagerecht entlang der Nase, die Mündung liegt 30 mm vor und 5 mm unter dem Anbaupunkt, oben 22°
geneigt. Bildschirmfotos (nur Fabric, `-PpropwashScreenshots=true`): `accessory_hang_freestyle` und
`accessory_hang_whoop` von der Seite mit Speer, `accessory_hang_freestyle_empty` mit leerer Halterung schräg von unten. Weil die Speer-Halterung gerade hängt, zeigen auch `accessory_display_*` aus B2
sie ohne Neigung. JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.accessory.hang.HangsStraightTest`.

### Kompass im Flug-Zustand (Add-on-API 1.7)

`docs/api-compass.md`: `'-PpropwashSelftestOnly=addon-api-17: Kompass'` (ein Schritt in
`accessory.client.CompassSelfTests`, etwa 4 s). Der Schritt meldet Fühler-Kräfte `propwash_example:compass_probe` an,
die nur während des Schritts greifen, startet zwei `freestyle_5`-Flüge im FPV mit der Test-Eigenschaft
`propwash.test.compassErrorDeg` (3° und 40°), steigt jeweils auf 3 m und vergleicht Kurs, Kompasskurs, Fehler und Güte
aus `FlightState` mit dem Kompassfehler des Flugmodells (höchstens 0,5° Abstand); 3° gilt als gesund, 40° als gestört;
danach ist die Eigenschaft wie vorher. Zusammen mit den Flughilfe-Schritten: `'-PpropwashSelftestOnly=addon-api-17: Kompass,addon-api:'`. JUnit:
`tools/fastcheck.sh test --select-class de.lardtom.propwash.api.compass.CompassStateTest`.

### Zubehör-Befunde: Beladung, abgestellt feuern, Tempo (Add-on-API 1.7)

Grundlage: `docs/accessory-fix.md`. JUnit:
`tools/fastcheck.sh test --select-class de.lardtom.propwash.accessory.fix.PayloadLimitTest --select-class de.lardtom.propwash.accessory.fix.MuzzleTest --select-class de.lardtom.propwash.accessory.fix.MotionTrackTest --select-class de.lardtom.propwash.accessory.fix.OverloadDisplayTest --select-class de.lardtom.propwash.accessory.fix.AccessoryFramesTest --select-class de.lardtom.propwash.accessory.fix.CreativeTabNamespacesTest`.

- Selbsttest beider Loader: `'-PpropwashSelftestOnly=addon-api-17: Zubehör-Befunde'` (Klasse
  `interact.client.AccessoryFixSelfTests` in der Arena von `interact:`, Beispiel `ExampleDispenser`, etwa 15 s). Die
  Werfer-Drohne steht an freestyle_5 und cinewhoop_3 auf dem Boden: Vorderflächen-Mitte 10 bzw. 13 mm im Boden, Mündung
  13 mm darüber, Pfeil `FIRED`, TNT `PLACED` auf der Standfläche, Stein an der Mündung `BLOCKED`. Wartung mit
  Shulkerkiste voll Stein: „Beladung 89750 g · überladen“, Flugmodell 19958 g, Drohnen-Item „Überladen“. Im Sichtflug:
  vier volle Stein-Stapel (12,8 kg) bleiben bei 3 s Vollgas am Boden, die Shulkerkiste setzt die OSD-Warnung
  `ÜBERLADEN` und 20 kg ins Flugmodell, ein Stapel (3,2 kg) zählt voll und steigt. Danach Sichtflug mit 5,6 m/s: der
  Server misst 40 Ticks lang `DroneMotion.velocity` und bei Zubehör-Aktion 1 zwischen den Ticks
  `AccessoryContext#velocity()` (Klasse `dev.AccFixSpeedRecorder`): nie 0, Mittel 5,6 m/s ± 15 %, jeder Wert mindestens
  die Hälfte; das Log nennt dazu, in wie vielen Ticks die Entity-Position stand (so oft gab die alte Schätzung 0).
  Bildschirmfoto (nur Fabric, `-PpropwashScreenshots=true`): `accessory_overloaded`.
- Zubehör nur an bestimmten Rahmen (im selben Filter, Klasse `interact.client.AccessoryFramesSelfTests`, Test-Zubehör
  `propwash_example:cine_gimbal` aus JSON mit `frames` cinewhoop_3 und long_range_7): `builds().check` an jeder Vorlage
  mit Halterung vorn (cinewhoop_3 und long_range_7 baubar, freestyle_5 und race_5 `accessory_frame`), Tooltip „Passt zu“
  nur mit diesen Rahmen, Werkbank mit freestyle_5 lehnt das Gimbal per Klick und Shift-Klick ab und nennt im Hinweis die
  Rahmen, Cinewhoop baut, Gimbal vor dem Rahmen eingelegt bleibt nicht baubar, mit Regel aus baut der Freestyle. Danach
  eine Freestyle-Drohne mit Gimbal speichern und als Entity neu laden: Gimbal bleibt, Problem `ACCESSORY_FRAME`,
  Steuern verweigert (`parts_incompatible`), Zerlegen legt das Gimbal in die Halterung und per Shift-Klick ins
  Inventar, danach baut der Freestyle; PW3-Code meldet `accessory_frame`, `createDrone` lehnt ab, das Drohnen-Item
  nennt mit Shift das Problem. Zuletzt mit „Teile müssen zusammenpassen“ aus: Die Freestyle mit Gimbal darf gesteuert
  werden, ihr PW3-Code ist baubar mit Warnung `accessory_frame`, `createDrone` und der Import einer neuen Drohne
  (`ImportService`) bauen sie; mit Regel an lehnt derselbe Import ab (`INVALID`). JUnit
  `accessory.fix.AccessoryFramesTest` prüft dazu den Neustart in eigenen Klassenladern (Speicherstand, Netz-Bytes,
  PW3-Code vor der Einschränkung gespeichert) und beide Werte der Welt-Regel über `WorldRules`.
- Kreativ-Tab (im selben Filter, Klasse `interact.client.AccessoryTabSelfTests`): Das Beispiel-Add-on mit eigenem Tab
  `propwash:main-propwash_example` meldet seine 18 Teil-Items zusätzlich über `PropwashApi.creativeTab()` an, dazu je
  einen benannten Eintrag mit Papier (`minecraft`) und analoger Brille (`propwash`). `ContentTab.contents()` darf keines
  der 18 Teile enthalten, beide Kontroll-Einträge schon (Log: „Propwash-Tab mit … Einträgen ohne die 18 Teile …“). JUnit
  `accessory.fix.CreativeTabNamespacesTest` prüft dazu, wann ein Namensraum einen eigenen Tab hat (`extensionTab`,
  `propwash:main-<ns>` im Register, direkt angemeldeter Tab `<ns>:…`, nie `propwash` und `minecraft`).
- Beitrittstest mit Pilot auf dem dedizierten Server: `PROPWASH_JOINTEST_ACCFIX=1` an beiden Prozessen (Beispiel-Add-on
  an). Der Server (`dev.AccFixJoinCheck`, Log `[Beitrittstest] Tempo: …`) setzt 3 s nach dem Beitritt eine Freestyle-Drohne
  mit Beispiel-Werfer 4,5 Blöcke südlich vor den Spieler, gibt Kreativ-Brille und Funke und startet FPV. Der Client
  (`accessory.client.AccFixJoinProbe`) schärft, steigt auf 2 m und fliegt mit 5,6 m/s nach Osten (Sim-Tempo je Tick),
  alle 7 Ticks Zubehör-Aktion 1. Sobald die Entity-Position im Mittel der letzten 10 Ticks mindestens 5 m/s zeigt, misst
  der Server 40 Ticks und die Aktionen dazwischen und schickt das Ergebnis als Chat-Zeile `accfix speed=ok|fail …`, die
  der Client mit `accfix client pushed … ticks, … presses` in den Bericht schreibt. Erwartung:
  `PROPWASH_JOINTEST_EXPECT='joined|accfix speed=ok'`, `PROPWASH_JOINTEST_LABEL=accfix`.

### Add-on zeitweise entfernt

`docs/addon-placeholders.md`: `'-PpropwashSelftestOnly=platzhalter:'` (beide Loader, zwei Schritte in
`build.placeholder.client.PlaceholderSelfTests`, zusammen unter 1 s). Der erste Schritt baut am Server eine
`freestyle_5` mit Werfer vorn, Booster oben und Speer-Halterung unten aus `ExampleDispenser` und fünf Pfeilen im
Werfer, speichert sie als Entity, als Drohnen-Item und als Netz-Bytes, schaltet den Namensraum `propwash_example` ab
(wie ein fehlendes Add-on) und lädt alles neu: an jeder Halterung ein Platzhalter mit gleicher Id und passender
Halterung, Problem `missing_part`, Inhalt vorn erhalten, Netz-Bytes gleich. Mit wieder eingeschaltetem Namensraum
lädt er den ohne Add-on gespeicherten Stand: Originalteile, gleiche Masse. Der zweite Schritt hängt an die
gespeicherten Gamerules zwei Regeln eines unbekannten Add-ons an und verlangt Laden ohne Fehler, gleiche Werte nach dem
Speichern und die Regeln auch in `GameRuleMap.copyOf`. JUnit:
`tools/fastcheck.sh test --select-package de.lardtom.propwash.build.placeholder` (`PlaceholderSlotTest`,
`PlaceholderRestartTest` mit Neustarts in eigenen Klassenladern, `UnknownGameRulesTest`).
Neustart mit echter Welt (je Loader, etwa 1 min): nach einem `runServerSmoke` noch
`PROPWASH_DEV_EXAMPLE=off … ./gradlew :<loader>:runServerSmoke -x prepareServerSmoke` (Welt bleibt, Server ohne
Beispiel-Add-on) und danach ohne den Schalter dasselbe noch einmal; beide Läufe PASS ohne Fehlerzeile im Log (vorher
„Failed to parse saved data … game_rules“), und `world/data/minecraft/game_rules.dat` enthält nach dem Lauf ohne
Add-on weiter die Regeln `propwash_example:…`.

### Namensraum-Prüfung und Probe-Mod (Add-on-API 1.7)

Was geprüft wird und was Spieler sehen: `docs/namespace-guard.md`; Regeln für Add-ons: `ADDONS.md`; Ablehnung in der
API: `docs/api/addon-guide.md` §21.18.

- **JUnit:** `tools/fastcheck.sh test --select-package de.lardtom.propwash.guard` (`KeyClassifierTest`: Schlüssel mit
  dem Segment `propwash`, bekannte Schlüssel = Überschreiben; `ResourcePathsTest`: vorhandener Pfad und Tag-Ergänzung
  erlaubt, neuer Pfad = Fund, Spielerpakete nur Info; `EnumConstantsTest`: Original-Konstanten per Class-File-API gegen
  `values()`; `ReportTest`: Log-Format, Kappung auf 20 Ids, einmal je Start; `RefusalOriginTest`: Anmeldungen aus
  Propwash-Code sind keine Funde) und `--select-class de.lardtom.propwash.apiimpl.NamespaceRulesTest` (`propwash`
  reserviert, `propwash_example`, `minecraft`, `justmoreparts` nicht; Ausnahme an neuen Punkten, Verweigerung ohne
  Ausnahme an alten; Text nennt Mod und Id).
- **Selbsttest ohne Probe** (jeder normale Lauf, harte Prüfung auf Fehlalarme): `'-PpropwashSelftestOnly=guard:'` auf
  beiden Loadern verlangt **0 Funde**. Dieselbe Gruppe mit dem Jar von Just More Parts 1.2.0 in
  `fabric/runs/selftest-client/mods` (bzw. `neoforge/runs/…`): weiter 0 Funde und ein einziger Knopf „Sharecode“ in der
  Werkbank (mit Just More Parts 1.1.x startet Propwash 0.6.0 nicht, Abschnitt Sharecode). Gegenprobe für Propwashs eigene Fehlversuche:
  `'-PpropwashSelftestOnly=api: Presets,api: eigener Kreativ-Tab,guard:'` muss ebenfalls 0 Funde ergeben.
- **Probe-Mod `nsprobe`** (Quell-Set `common/src/nsprobe`, eigene Mod-Id, „Namespace Probe“): verletzt den Namensraum
  absichtlich in jeder Kategorie und liegt nur mit `-PpropwashNsProbe=true` im Spiel, nie im Release-Jar:

```sh
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=guard:' -PpropwashNsProbe=true
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :neoforge:runClientSelftest '-PpropwashSelftestOnly=guard:' -PpropwashNsProbe=true
```

  Erwartet je Kategorie genau die Ids der Probe (alle vom Mod `nsprobe`): `propwash:nsprobe_item`,
  `key.propwash.nsprobe`, `propwash.nsprobe.text`, die Tastenbelegung `key.propwash.nsprobe`, `propwash:nsprobe_ping`,
  `input.SwitchFunction.NSPROBE`, `osd.OsdElement.NSPROBE`, `data/propwash/recipe/nsprobe.json`,
  `assets/propwash/textures/item/nsprobe.png`, `/propwash nsprobe`, `propwash:nsprobe (config tab)` und
  `propwash:probe (hud element)`; die Tag-Ergänzung `data/propwash/tags/item/parts/frame.json` und die ersetzte Textur
  `assets/propwash/textures/item/carbon_plate.png` sind keine Funde. Schritte der Gruppe: Prüfung beim Start, Hinweis im
  Chat genau einmal, `F3+T` ohne zweiten Hinweis, Details-Fenster mit „Kopieren“ und „Schließen“, Absturzbericht mit
  Abschnitt „Propwash add-ons“, `/reload`, zweiter Weltbeitritt ohne Hinweis. Mit `-PpropwashScreenshots=true` (Fabric)
  die Bilder `guard_hint_{en,de}` und `guard_report_{en,de}_{854x480,320x240}`.
- **Dedizierter Server:** Ein Client ohne Probe kommt auf einen Server mit Probe nicht hinein (der Loader lehnt das
  unbekannte Item `propwash:nsprobe_item` ab). Den Server-Hinweis für Operatoren prüft deshalb der Beitrittstest
  `addon17` mit Probe auf beiden Seiten (Erwartung `ns_server_hint=1|ns_client_hint=1`), dass ein Client ohne Probe
  keinen eigenen Hinweis zeigt, der Fall `addon17-noaddon` (Abschnitt „Add-on-Schalter (A1)“ in §1). Danach im
  Server-Log als ERROR nur die Funde der Probe.
- **Laufzeit:** Jeder Lauf schreibt `Namespace check (…): n findings in x ms` ins Log; Ziel unter 50 ms mit Just More
  Parts.

### Drohnen skalieren (`drone-scale:`)

Selbsttest `'-PpropwashSelftestOnly=drone-scale:'` (beide Loader, 6 Schritte in `drone.client.DroneScaleSelfTests`, mit
`-PpropwashScreenshots=true` auf Fabric 10 mit den Fotos `drone_scale_025`, `drone_scale_100`, `drone_scale_300` und
`drone_scale_lineup`; Platz bei Spieler + (30, 30), rund 25 s nach dem Laden der Welt): `/attribute … minecraft:scale`
an der Drohne mit Grenzen 0,0625–16, Modifikator, fremdem Attribut, Speichern/Laden und Absetzen aus dem Item; Client
sieht 0,25×, 1× und 3× mit Hitbox und Renderzustand; FPV-Kamera skaliert um den Fuß; Gate mit Öffnung 1 × 1 (3×-7-Zoll
hängt, 1× und 0,25× fliegen durch); Runde mit 0,5× zählt als Flughilfe „Skalierung“, mit 1× ohne. JUnit:
`tools/fastcheck.sh test --select-class de.lardtom.propwash.drone.DroneScaleTest --select-class
de.lardtom.propwash.race.AssistScaleTest`. Beitrittstest (§3): Server mit `PROPWASH_JOINTEST_HOLD=1800
PROPWASH_JOINTEST_DRONESCALE=1`, Client mit `PROPWASH_JOINTEST_LABEL=dronescale PROPWASH_JOINTEST_DRONESCALE=1
PROPWASH_JOINTEST_EXPECT='joined|drone_scale=ok'` (`dev.DroneScaleJoinCheck`, `drone.client.DroneScaleJoinProbe`;
Einzelheiten in `docs/drone-scale.md` §5).

### Auto-Arm nach Reset (auto-arm, 0.6.0)

Doku: `docs/auto-arm.md`.

- JUnit: `sim.AutoArmTest` (`tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.AutoArmTest`); das Sprachmodul
  `auto-arm` ist in `osd.OsdModelTest` eingetragen (OSD-Texte `AUTO_ARM`, `AUTO_ARM_3D`).
- Selbsttest `'-PpropwashSelftestOnly=input: Auto-Arm'` (`input.client.AutoArmSelfTests`, beide Loader, etwa 1 min):
  Modi-Tab (Schalter „Auto-Arm“ nur bei „Scharf schalten“, Standard an, aus und an gespeichert), Funke (simuliert,
  Reset per Taste), Gamepad mit Arm-Taste als Umschalter und gehalten (Reset über die Back-Taste), Tastatur (Arm-Taste,
  Gas über die Gastasten; Reset, Absturz aus 30 m mit Welt-Regel „Absturzschaden“ aus, Reset, dann per Arm-Taste
  entschärft und Reset: bleibt entschärft), Einstellung aus (Reset bleibt entschärft, Schalter aus und an armt) und am
  Start-Gate mit „Start beim Scharfschalten“ (kein Countdown durch den Reset, Scharfschalten von Hand startet ihn). Je
  Fall nach dem Reset 50 Ticks mit Gas oben: entschärft, `PilotSession#autoArmPending`, OSD `AUTO_ARM` statt
  `ARM_THROTTLE`. Eigene Drohne 3 Blöcke vor dem Spieler, Kurs 8 und 24 Blöcke dahinter; Aufräumen in jedem Schritt.
  Bildschirmfotos (nur Fabric, mit `-PpropwashScreenshots=true`): `input_auto_arm_osd`, `input_auto_arm_osd_de` (eigene
  Schritte „Sprache Deutsch“ und „Sprache zurückstellen“) und `input_auto_arm_modes_tab`.
- Bisheriges Verhalten (Schalter aus und an nach dem Reset) prüfen `reset: Reset scharf …`, `race: Wertung – Reset
  scharf …` und die Beitrittstests `PROPWASH_JOINTEST_RACEHOME` und `PROPWASH_JOINTEST_COV_RACE`; sie schalten die
  Einstellung für ihren Lauf aus und danach zurück.
- Beitrittstest: Server mit `PROPWASH_JOINTEST_HOLD=600 PROPWASH_JOINTEST_AUTOARM=1` (`dev.AutoArmJoinCheck`, Log
  `[Beitrittstest] Auto-Arm: …`, Kurs mit Start-Gate 24 Blöcke westlich des Spielers, Homepoint 6 Blöcke davor),
  Client mit `PROPWASH_JOINTEST_AUTOARM=1` (`input.client.AutoArmJoinProbe`): scharf schalten, „Start beim
  Scharfschalten“ an, Gas hoch, Reset, 40 Ticks Gas oben, Gas unten. Erwartung
  `joined|autoarm waiting throttle up 40 ticks osd=true|autoarm armed after reset|autoarm no countdown|lobby=false|home=true`
  (der Server meldet per Chat `[auto-arm] armed after reset 1 ticks=… lobby=false home=true`).

### Neue Rezepte im Selbsttest Überleben (Befund WA-W2a-1, 0.6.0)

- JUnit `content.client.SurvivalSelfTestsRecipeListTest`
  (`tools/fastcheck.sh test --select-class de.lardtom.propwash.content.client.SurvivalSelfTestsRecipeListTest`): jede
  Rezeptdatei `common/src/main/resources/data/propwash/recipe/*.json` steht in `SurvivalSelfTests.craftableIds()`
  (Zwischenteile, eingebaute Teile ohne Kreativ-Stack, `OTHER_CRAFTABLE`) und jeder Eintrag dort hat eine Rezeptdatei.
  Ein neues Rezept braucht also neben `tools/data/survival.py` einen Eintrag in `OTHER_CRAFTABLE`, sonst scheitern im
  Spiel „survival: jedes Rezept lädt …“ (unerwartetes Rezept) und „survival: Rezeptbuch im Überlebensmodus …“
  (Mengenvergleich). Einträge dort als Kennung (`ContentIds`, `Constants.id(…)`), nicht über Klassen, die beim Laden
  Items registrieren: sonst lässt sich die Liste im JUnit ohne Registrierung nicht lesen.
- Selbsttest `'-PpropwashSelftestOnly=survival:'` (beide Loader, unter 1 min).

### Bando-Lage (`bando:` mit den Befund-Seeds)

```sh
JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=bando:' -PpropwashBandoSeed=1618033
JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=bando:' -PpropwashBandoSeed=900001
```

- Dasselbe mit `:neoforge:runClientSelftest`. Seed 1618033 (Scheune `barn/n17` an einer Schlucht mit Wasser) und
  900001 (Scheune `barn/n12` im Kirschhain) sind die Befund-Seeds der Bando-Lage aus 0.5.2
  ([`bando-placement.md`](bando-placement.md)); je Lauf rund 145 Plätze, etwa 3:30 min.
- „bando: Lage – Boden auf Geländehöhe, nichts schwebt, keine Pflanzen auf dem Bau“ prüft zusätzlich jedes Blatt im
  Bereich samt 4 Blöcken Rand; nicht gezählt im Median am Rand werden Randspalten, deren Gelände nach der Platzwahl
  abgetragen wurde (Höhle, Schlucht, See).
- Mit `-PpropwashScreenshots=true` macht „bando: Lage – Luftbilder der Stellen mit Befund“ Luftbild und Nahaufnahme der
  bekannten Befund-Stellen des Seeds und jedes auffälligen Platzes (`bando_befund_<seed>_…`, `m` für ein Minus im
  Seed). Die Bilder gehören nicht ins Repository; die übrigen Bando-Bildschirmfotos eines fremden Seeds überschreiben die
  des Test-Seeds, also mit `'-PpropwashSelftestOnly=bando:,!bando: Bildschirmfotos,!bando: Lage – Luftbilder jeder,!bando: Karte – Bildschirmfotos'`
  filtern oder `docs/screenshots/` danach zurücksetzen.

### Flugschul-Taste über einem Slot (Testweg der API, Befund MDA-10, 0.6.0)

Add-ons halten die Flugschul-Taste im Selbsttest über `PropwashClientApi.simulateFlightSchoolHold(slot)` und
`stopSimulatedFlightSchoolHold()` (Handbuch `docs/api/addon-guide.md` §14.3). Gezielt mit dem eingebauten Halten-Schritt:
`'-PpropwashSelftestOnly=guide: Taste halten'` (beide Loader, zwei Schritte, wenige Sekunden). „guide: Taste halten über
einem Slot nur über die API (Testweg für Add-ons)“ (`guide.client.FlightSchoolHoldApiSelfTests`) stellt den Spieler in
den Überlebensmodus und legt einen Propeller in Slot 9 des Inventars. Ausgelöst wird nur über die API. Geprüft:

- Ein negativer Slot wird abgelehnt.
- Halten über Slot 9 öffnet nach mindestens 650 ms mit Hinweis genau die Szenen von `flightSchoolScenes`, und „Zurück“
  führt ins Inventar.
- Ohne Loslassen öffnet nichts ein zweites Mal (30 Ticks).
- Über dem leeren Slot 10 erscheinen weder Hinweis noch Szene (30 Ticks).
- Nach `stopSimulatedFlightSchoolHold()` passiert nichts mehr (30 Ticks), und ein neues Halten öffnet wieder.

Danach sind Inventar und Spielmodus wie vorher. JUnit:
`tools/fastcheck.sh test --select-class de.lardtom.propwash.guide.SimulatedHoldTest` (nur in der Entwicklungsumgebung,
Slot statt Maus, Halte-Zeit und einmaliges Öffnen wie mit der echten Taste).

### Lage per `/tp` und `/rotate` (Befund TPROT-1, 0.6.0)

Doku: `docs/drone-rotation.md`.

- JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.drone.VanillaRotationTest --select-class
  de.lardtom.propwash.drone.net.DroneResetS2CTest` (Nase folgt dem Vanilla-Blick ohne Rollen, Gier 0 = Lage beim
  Erscheinen, Nick wie Vanilla begrenzt, nur eine Rotationsangabe dreht, Tempo im Weltsystem und Drehraten im
  Drohnensystem bleiben; alle Merker reisen, ein normaler Reset ist Byte für Byte wie vorher).
- Selbsttest `'-PpropwashSelftestOnly=drone: Lage per'` (`drone.client.TeleportRotationSelfTests`, beide Loader, vier
  Schritte, rund 6 s, Platz bei Spieler + (−14, −6)): stehende Drohne mit `/tp @e[…] ~ ~ ~ -90 0` (Server sofort, Client
  nach 2 Ticks ohne Rücksprung), `/rotate 45 0` (3 Ticks), `/tp ~ ~0.25 ~` ohne Rotation (Lage 20 Ticks gleich) und
  `/rotate <Drohne> facing ~3 ~ ~` (Gier −90); schwebende Drohne ohne Schwerkraft mit `/rotate 30 20` (Nick 20°,
  Rollen 0, hält 10 Ticks); fliegende Drohne im FPV mit 6 m/s nach Westen: `/rotate 90 0`, `/tp @e[…] ~ ~ ~ -90 0` und `/tp <Drohne> ~2 ~ ~ 180 0` – je nach 1 Tick beim
  Piloten, Tempo vorher −5,77 m/s, nachher −5,70 m/s (nur Luftwiderstand), Sprung an Ort und Stelle −0,29 m (ein Tick
  Flug), beim Teleport mit Versatz +1,87 bis +2,16 m, kein Absturz, scharf, Server-Lage beim Client übernommen und ohne
  Rücksprung. Bildschirmfotos `drone_tp_rotate_standing` und `drone_tp_rotate_flying` bei jedem Lauf (mit
  `-PpropwashScreenshots=true` auf Fabric in `docs/screenshots/`, sonst in `screenshots/` im Run-Ordner).
- Beitrittstest (§3): Server mit `PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_DRONE_ROTATION=1`
  (`dev.DroneRotationJoinCheck`, Log `[Beitrittstest] Lage per /tp und /rotate: …`), Client mit
  `PROPWASH_JOINTEST_LABEL=dronerotation PROPWASH_JOINTEST_DRONE_ROTATION=1 PROPWASH_JOINTEST_EXPECT='joined|drone_rotation=ok'`
  (`drone.client.DroneRotationJoinProbe`): 10 s nach dem Serverstart, vor jedem Beitritt, erzeugt der Server am Spawn
  die Drohne „propwash-rotation vorher“ mit Gier 0 und dreht sie per `/tp … ~ ~ ~ -90 0`; der später beitretende Client
  sieht sie schon beim ersten Mal mit Gier −90, auch im Bild. 3 s nach dem Beitritt erzeugt der Server 3 Blöcke vor dem
  Spieler „propwash-rotation <Spieler>“ mit Gier 0, nach je 3 s folgen `/tp … ~ ~ ~ -90 0`, `/rotate … 45 0` und
  `/tp … ~ ~0.2 ~` ohne Rotation; der Client dreht jeweils ohne Rücksprung nach und bleibt nach dem letzten Teleport bei
  45. Ergebnis 23.10. auf beiden Loadern: `drone_rotation before_join yaw=-90.0 render=-90.0 | … tp yaw=-90.0 |
  … rotate yaw=45.0 | … tp_plain yaw=45.0 | drone_rotation=ok`. Ist eine echte Funke angesteckt, fragt der Rauchtest-Client
  nach ihrer Einrichtung und meldet nie `joined` (der Selbsttest blendet Geräte aus, der Rauchtest nicht); dann die GUID
  im Run-Ordner unter `config/propwash/input.json` in `neverAsk` eintragen.

### Bildprüfung und Ersatz-Shader (`video: Bildprüfung`, fix-intel-video, 0.6.0)

Doku: `docs/video-check.md` (schwarzes FPV-Bild auf Intel-Grafik).

- JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.video.VideoCheckTest --select-class
  de.lardtom.propwash.video.client.VideoEffectsSettingTest`.
- Selbsttest `'-PpropwashSelftestOnly=video: Bildprüfung,video: Einstellung Videoeffekte'` (`video.client.VideoCheckSelfTests`,
  beide Loader, 2 Schritte, etwa 15 s nach dem Laden der Welt; Fabric mit `-PpropwashScreenshots=true` dazu die Fotos
  `video_check_fault_black` und `video_check_simple`): Bildprüfung, Shader-Kopie gegen Blit bitgleich, Last je Bild,
  FPV mit Kreativ-Brille und Fisheye 80 % bei Rauschen 0 und 30 % nicht schwarz (Pixel des Hauptbilds zurückgelesen),
  Fehlerbild nachgestellt, Umschalten auf ohne Shader bzw. Einfach mit Chat-Meldung, gehaltene Bilder im Ersatz-Shader,
  ungebundener HistorySampler, Knopf „Videoeffekte“ im Video-Tab. Den Fisheye-Weg prüfen zusätzlich
  `objektiv: Fisheye je Videosystem …` und `objektiv: Bildrate flach gegen Fisheye …`.

### Bildweg der Action-Cam-Aufnahme (`video: Action-Cam-Bildweg`, F-INTELREC-1, 0.6.0)

Doku: `docs/video-check.md` §5 (grüne Action-Cam-Aufnahme auf Intel-Grafik).

- JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.video.record.ImageGuardTest --select-class
  de.lardtom.propwash.video.record.RgbaI420Test --select-class de.lardtom.propwash.video.record.RecordingDiagnosisTest`.
- Selbsttest `'-PpropwashSelftestOnly=video: Drohne mit Action-Cam Heavy,video: Action-Cam-Bildweg'`
  (`video.client.record.ActionCamImageSelfTests`, angemeldet in `ActionCamSelfTests` nach dem Fisheye-Schritt, beide
  Loader, 4 Schritte nach dem Aufbau, zusammen etwa 7 s):
  - Normale Aufnahme bleibt auf dem GPU-Weg. Im selben Bild liest der Ersatzweg dieselben Bytes wie
    `copyTextureToBuffer`, die CPU-Umrechnung rechnet wie der Pack-Shader.
  - Fehler-Injektion `ActionCamCapture.Fault`: Rücklesen ohne Wirkung → Ersatzweg; Pack-Durchgang ohne Wirkung →
    CPU-Umrechnung; je mit HUD-Hinweis, Chat-Meldung, Vermerk in `RecordingDiagnosis` und Bild im Video.
  - Beide Durchgänge ohne Wirkung → Abbruch mit Toast und Chat, kein Video.

### Grafik-Kompatibilität und Ersatzwege (`compat:`, intel-compat, 0.6.0)

Doku: `docs/graphics-compat.md` (Direct State Access in Minecraft 26.3, Kompatibilitätsmodus für Intel unter Windows,
Prüfung der Grafikwege, Ersatzwege aller Kopier- und Rücklese-Stellen).

- JUnit: `tools/fastcheck.sh test --select-package de.lardtom.propwash.gpucompat` (`GpuCompatDecisionTest`,
  `GpuCompatSettingTest`, `GpuPathsTest`).
- Selbsttest `'-PpropwashSelftestOnly=compat:,video: Drohne mit Action-Cam Heavy'` (`gpucompat.client.GpuCompatSelfTests`
  mit `video.client.CompatVideoSelfTests`, dazu `video.client.record.CompatActionCamSelfTests` in `ActionCamSelfTests`;
  beide Loader, 8 Schritte, etwa 15 s nach dem Laden der Welt). Ohne „video: Drohne mit Action-Cam Heavy“ fehlt den beiden
  Action-Cam-Schritten die Drohne.
- Erzwungener Intel-Modus auf dem Mac: `PROPWASH_GPU_COMPAT_SIMULATE=intel-windows` (nur Entwicklungsumgebung) vor den
  Aufruf setzen; der Schritt „compat: Erkennung“ erwartet dann „an – Automatisch, Intel-Grafik unter Windows erkannt“.
  Mit `PROPWASH_GPU_COMPAT=on|off` erwartet er die Vorgabe.
- Fehler-Injektion auf Treiber-Ebene (`gpucompat.client.GpuFaults`, Mixin `mixin.dev.client.GlCommandEncoderFaultMixin`
  nur in `propwash.dev.mixins.json`): Blit ohne Wirkung, GPU-Rücklesen mit Nullen, klassisches Rücklesen mit Nullen.
  Bildschirmfotos und Messungen der Selbsttests über Vanillas `Screenshot` bleiben ausgenommen.

### Zuschauen mit OSD und Rennen des Piloten (`video: Zuschauen`, Befund RC2-4, 0.6.0)

Doku: `docs/spectate-feed.md`.

- JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.video.PilotFeedTest` (9 Tests).
- Selbsttest `'-PpropwashSelftestOnly=video: Zuschauen'` (`video.client.SpectateFeedSelfTests` mit den bisherigen
  Zuschauen-Schritten; beide Loader, mit `-PpropwashScreenshots=true` Bild `spectate_pilot_osd.png`). Schritt
  „Renn-Zustand, OSD-Layout und Sticks des Piloten beim Zuschauer“: Kurs aus drei mittleren Gates vor dem Spieler,
  simulierter Sender `Rennpilotin` (`dev.SimulatedPilotFeed`, Layout Rennen in Orange mit Sticks, mph, Mode 1); der
  Spieler schaut mit Analog-Brille zu, der Server schickt jeden Tick über `PilotFeedServer#simulate` 30 Ticks Countdown
  (Start-Gate rot mit Durchflugfläche) und 60 Ticks Rennen (zweites Gate grün mit Durchflugfläche, Start-Gate ohne,
  Runde, Rundenzeit und Gate gezeichnet, Roll-Stick mindestens 1,0 Weg, Sticks gezeichnet, Flugzeit und Akku des
  Piloten); danach zeigt das OSD wieder das eigene Layout in der eigenen Einheit, die eigenen Einstellungen sind
  unverändert. Schritt „Pilot schickt Renn-Zustand, OSD-Layout und Sticks nur mit Zuschauer“: FPV auf einer
  5″-Race-Drohne vor demselben Kurs, ohne Zuschauer 30 Ticks kein Paket, mit Zuschauerzahl 1 (am Client gesetzt) und
  Countdown 50 Ticks lang mindestens 18 Pakete je Sekunde am Server mit Kurs, Phase und Start-Gate, das Layout und die
  Einheit des Piloten am Server; mit Zuschauerzahl 0 wieder keine Pakete.
- Beitrittstest `watchsolo` (§7, „Nur Pilot B“) prüft den Weg über das Netz zum Zuschauer.

### Geräusche an der Drohne (`audio: an der Drohne`, Befund SND1-1, 0.6.0)

Prüft, dass ein Pilot im FPV Geräusche und Level-Events an der Drohne bekommt, auch wenn sein Körper weit weg steht
(`docs/drone-hearing.md`). Braucht den Beispiel-Werfer des Test-Add-ons.

- Selbsttest `audio: an der Drohne – FPV-Pilot hört Werfer, Geräusche und Explosion bei Körper 200 Blöcke entfernt`:
  Drohne mit Beispiel-Werfer im FPV, der Server verlegt sie in Schritten von 8 Blöcken 200 Blöcke nach Osten, feuert den
  Werfer (Level-Event 1002 an der Mündung) und spielt ein Geräusch an der Drohne, ein Geräusch am Drohnen-Entity, ein
  Geräusch mit dem Piloten als Ausgenommenem und eine kleine Explosion 8 Blöcke vor der Drohne; danach dasselbe 6 Blöcke
  neben dem Körper. Ein Test-Mixin (`mixin/dev/client/ClientPacketListenerMixin`) schreibt jedes empfangene Geräusch-,
  Level-Event- und Explosions-Paket mit (`audio.client.SoundPacketLog`). Verlangt in beiden Lagen je genau ein Paket der
  vier Arten und keins vom ausgenommenen Geräusch; dazu die Hörposition des Clients (Listener der Sound-Engine) an der
  Drohnenkamera: höchstens 0,25 Blöcke von der Kamera, 1,5 von der Drohne, mindestens 150 vom Körper, Blick und oben
  wie die Kamera. Ohne Audiogerät wird nur die Hörposition übersprungen. Ohne Fix: `far=0/4`, `near=4/4`.
- Beitrittstest am dedizierten Server: `PROPWASH_JOINTEST_DRONE_SOUND=1` an Server und Client (`dev.DroneSoundJoinCheck`,
  `audio.client.DroneSoundJoinProbe`), Ablauf wie im Selbsttest, gesteuert vom Server; der Client wartet auf die Zeile
  `dronesound server done …` im Chat und schreibt `dronesound far=4/4 near=4/4 exact=yes hearing=ok fpv=yes`.
  Erwartung: `PROPWASH_JOINTEST_EXPECT='joined|dronesound far=4/4 near=4/4 exact=yes|fpv=yes|!hearing=bad|!hearing=missing'`.
  Etwa 2 Minuten je Loader.

### Drohnen und Sculk (`sculk:`, Befund FB1-11, 0.6.0)

Prüft die Vibrationen fliegender Drohnen (`docs/drone-sculk.md`). Sechs Schritte in `interact.client.DroneSculkSelfTests`,
etwa 1 Minute im Spiel:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=sculk:'
```

Aufbau 36 Blöcke westlich und 24 südlich des Spielers: eine Kammer aus weißer Wolle (11 × 11, 15 hoch, Boden in der
Mitte Stein), darin ein offener Sculk-Sensor 3 Blöcke neben der Startfläche, ein Sensor rundum in Wolle 3 Blöcke auf
der anderen Seite und ein Kreischer ohne Wärter-Ruf (`can_summon` aus) 3 Blöcke neben dem offenen Sensor. Die Wolle
hält Geräusche von draußen ab. Ein Server-Tick-Beobachter zählt die aktiven Ticks beider Sensoren, die Frequenzen des
offenen Sensors und ob der Kreischer schreit. Ablauf: Whoop im FPV unbewaffnet 60 Ticks am Boden (nichts), scharf im
Leerlauf 40 Ticks (nichts), Schweben 1,5 m für 80 Ticks (offener Sensor mit Frequenz 4, Sensor in Wolle still,
Kreischer schreit), oben auf 10,5 m warten, bis der offene Sensor ruht, dann entschärft fallen lassen und 2 m über dem
Steinboden auf 25 m/s stoßen (Frequenz 2 nach dem gezählten Absturz), zuletzt mit der Welt-Regel
`drones_trigger_sculk` aus 80 Ticks schweben (nichts). Der Abbau stellt Blöcke, Ausrüstung und den alten Wert der
Regel wieder her. Ohne Fix: Schweben „offener Sensor neben der schwebenden Drohne bleibt still“, Absturz „ohne
HIT_GROUND“, Regel „fehlt“.

### Sculk nach Server-Flügen (`sculk: Mission`, Befund OT1-2, 0.6.0)

Prüft, dass eine Drohne nach der Landung eines Flugs ohne Pilot still bleibt (`docs/drone-sculk.md` §1 und §4). Fünf
Schritte in `interact.client.DroneSculkMissionSelfTests`, etwa 1,5 Minuten im Spiel; die Gruppe `sculk:` hat damit 11
Schritte:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=sculk: Mission'
```

Aufbau 36 Blöcke westlich und 24 nördlich des Spielers: eine Kammer aus weißer Wolle (19 × 9, 9 hoch, Boden Stein,
Seelaternen in der Decke gegen Mobs), darin 27 Sculk-Sensoren im Schachbrett und 3 Blöcke davor die Startfläche. Ein
Server-Tick-Beobachter zählt, wie oft ein Sensor neu aktiv wird, und merkt sich Ereignis und Quelle der Vibrationen
unterwegs. Je Schritt fliegt eine neue Drohne als Add-on-Flug `propwash_example:sculk_mission` vom Boden auf 3 m, über
die Sensoren und landet: Cinewhoop zwischen Sensoren, Freestyle auf der Kante eines Sensors, Racer fordert am Ziel den
Heimflug an und Propwash landet ihn auf der Startfläche. Verlangt: im Flug lösen Sensoren aus, Ende mit `LANDED` (beim
Heimflug nach der Übergabe mit `STOPPED`), Fallhöhe am Ende 0, von Tick 10 bis 210 nach dem Ende kein Sensor neu
aktiv, Drohne entschärft, Motoren 0, kein Flug ohne Pilot. Ohne Fix: je Schritt 52 bis 108 Auslösungen, Quelle
`minecraft:hit_ground` der Testdrohne, und eine Fallhöhe von etwa 3 m aus dem Flug.

## 2. Schrittliste

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest -PpropwashScreenshots=true -PpropwashSelftestList=true
```

`-PpropwashSelftestList=true` (System-Property `propwash.selftest.list`) startet den Client bis zum Titelbildschirm,
legt keine Welt an, schreibt `runs/selftest-client/propwash-selftest-steps.md` des Loaders (Gruppen-Tabelle und alle
Schritte in Reihenfolge, Rahmen-Schritte markiert) und gibt die Gruppen-Tabelle aus; Dauer etwa 12 s. Jeder normale
Selbsttest schreibt dieselbe Datei. Die Liste entsteht aus den wirklich angemeldeten Schritten; „angemeldet in“ ist die
Klasse, die den Schritt anmeldet.

Die Tabelle unten ist der Stand 0.5.x (Fabric mit Bildschirmfotos, 659 Schritte; NeoForge 396, dort ohne
Bildschirmfotos). 0.6.0 hat viele Gruppen dazubekommen, ihre Präfixe und Aufrufe stehen in den Abschnitten von §1 und §8;
der volle Lauf hatte beim Wellenabschluss W2b (22.10.2026) auf Fabric mit Bildschirmfotos 1 819 Schritte und auf
NeoForge 1 117, beide in Teilen (§6). Die aktuelle Tabelle schreibt `-PpropwashSelftestList=true`; in der Gruppe
`drone:` kamen zum Beispiel die drei Schritte „drone: Honig …“ dazu.

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
| `beitritt:` | 4 | 0 | JoinSelfTests (Add-ons ohne Client-Bedarf fehlen in der Übersicht, die Übersicht scrollt mit Mausrad, Tasten und Scrollleiste, ein echter Linksklick öffnet den Link; mit `-PpropwashScreenshots=true` dazu drei Bildschirmfotos und der Sprachwechsel, `docs/design/join-check.md` §7) |
| `bildschirmfotos (Item-Rahmen):` | 14 | 0 | ItemFrameShowcase |
| `ui:` | 11 | 3 | LedSelfTests, UiSelfTests, OverlayMessageSelfTests (lange Meldungen über der Schnellleiste brechen um), ArmAngleSelfTests, ThemeSelfTests |
| `bildschirmfotos (drone-render):` | 2 | 0 | PartsRenderShowcase |
| `bildschirmfotos:` | 245 | 0 | PartsRenderShowcase, CrashFallSelfTests, AutonomousRescueSelfTests, InteractSelfTests, ShowcaseTour, WaterproofTurtleShowcase, WizardRemoteSelfTests, SwitchCommandSelfTests, GateMountSelfTests, ProTrickShowcase, ShadowSelfTests, RelayShowcase, HeadlightShowcase, TrainingShowcase, ApiShowcase, FlashbackCamShowcase, Mode3dShowcase, CreativeShowcase, GateFreeSelfTests, CourseElementSelfTests |
| `dust:` | 3 | 0 | DustSelfTests |
| `trails:` | 10 | 0 | TrailSelfTests (Drohnen-Trails, `docs/design/drone-trails.md` §4; mit Bildschirmfotos dazu 9 Schritte `bildschirmfotos: Drohnen-Trails – …`; `lan-trails:` nur mit `PROPWASH_LANHOST`, siehe „Zwei Clients über LAN“) |
| `drone:` | 75 | 1 | DroneSelfTests, CrashFallSelfTests, PlayLoopSelfTests, LedSwitchSelfTests, FlightModeSelfTests, ModeSwitchSelfTests (`drone: Modusschalter`: Boxer-Belegung im Flug und im Modi-Tab, Schalter-Matrix; mit `-PpropwashScreenshots=true` dazu `modes_switch_boxer_angle` und `modes_switch_key_override`), Mode3dSelfTests, WearWeightSelfTests, CameraAngleSelfTests, CameraClipSelfTests, GroundContactSelfTests (`drone: Bodenkontakt`: jede Drohnen-Vorlage in 14 Lagen auf 9 Flächen, mit `-PpropwashScreenshots=true` dazu zehn Bildschirmfotos `bildschirmfotos: Bodenkontakt`, `docs/design/ground-contact.md`) |
| `wasserdicht:` | 2 | 0 | WaterproofTurtleSelfTests (Wasserschutz-Spray auf der Drohne und im Raster, Werkbank, Flug im Becken mit Bildschirmfoto `waterproof_underwater_fpv`; `docs/design/waterproof-turtle.md` §6) |
| `turtle:` | 1 | 0 | WaterproofTurtleSelfTests (kopfüber im Sichtflug: ohne Turtle gesperrt, Turtle-Flip, Motoren aus, Ausschalten entschärft; Bildschirmfoto `turtle_flip_los`) |
| `bindung:` | 13 | 0 | BindSelfTests (Pilot im Empfänger, Brille tragen statt binden, falsche Brille; Bildschirmfotos `bildschirmfotos: Bindung – …` im Rundgang) |
| `parts-flight:` | 7 | 0 | PartsFlightSelfTests |
| `teile-freiheit:` | 3 | 0 | PartFreedomSelfTests |
| `race:` | 49 | 1 | RaceSelfTests, WorldCourseSelfTests, GateStackSelfTests, GateMountSelfTests, GateKeySelfTests, GateFreeSelfTests, CourseElementSelfTests, GateRedstoneSelfTests (`race: Redstone`, `docs/design/gate-redstone.md` §5) |
| `flughilfe:` | 3 | 0 | AssistedLapSelfTests (Welt-Regel „Runden mit Flughilfe speichern“, `docs/design/assisted-laps.md` §5) |
| `zonen:` | 4 | 0 | ZoneSelfTests (Befehle und Sync, Flug durch eine Zone mit Übergang, Flughilfe und Akku, Editor, Aufräumen; `docs/design/zones.md` §8; mit Bildschirmfotos dazu 12 Schritte `bildschirmfotos: Zonen – …`) |
| `server:` | 16 | 1 | ServerSelfTests, WorldRulesSelfTests (Schwerkraft 0–1000 % getippt: `docs/design/realism/flight-feel-gravity.md` §10.6) |
| `weltrand:` | 1 | 0 | GenerationEdgeSelfTests (Welt-Regel „Drohnen erzeugen neues Gelände“, Grenzzaun am Rand, `docs/design/world-edge.md` §6) |
| `rescue:` | 12 | 0 | RescueSelfTests, AutonomousRescueSelfTests |
| `reset:` | 24 | 0 | HomeResetSelfTests (Reset zum Homepoint: Taste, Welt-Regel, Rennabbruch und stehender Start, ferner Homepoint im FPV, alte Tastenbelegung; `docs/design/home-point.md` §7; Server-Rauchtest `rescue.HomeResetChecks`), HomeSpaceSelfTests (`reset: freier Platz – …`: Homepoint auf Stufe, Treppe, Zaubertisch, Teppich, Schnee, Schlamm, Seelensand, Honigblock und vollem Block, unter einem Block, Block auf dem Homepoint mit FPV-Überlagerung und Reset daneben, Reset auf Schlamm und Honigblock im FPV ohne Überlagerung, ganz zugebaut; mit Bildschirmfotos `reset_fpv_in_block`) |
| `input:` | 27 | 0 | InputSelfTests, BindingSelfTests, OnboardingSelfTests, ThrottleWizardSelfTests, WizardRemoteSelfTests, SwitchCommandSelfTests, MacControllerSelfTests, MouseSelfTests (Maus + Tastatur, `docs/design/mouse-control.md` §6; mit Bildschirmfotos dazu 6 Schritte `bildschirmfotos: Maus + Tastatur, …`) |
| `relay:` | 4 | 0 | RelaySelfTests |
| `video:` | 32 | 5 | VideoSelfTests, HeadlightSelfTests, HeadlightNightSelfTests, ActionCamSelfTests, FlashbackCamSelfTests, SpectateSelfTests, ShadowSelfTests, CameraFovSelfTests, AspectSelfTests, VideoTabSelfTests (`video: Video-Tab`, Bildschirmfoto `bildschirmfotos: Video-Tab ohne Brille`), VideoCheckSelfTests (`video: Bildprüfung`, `video: Einstellung Videoeffekte`, `docs/video-check.md`), ActionCamImageSelfTests (`video: Action-Cam-Bildweg`, angemeldet in ActionCamSelfTests, `docs/video-check.md` §5) |
| `vtx-leistung:` | 7 | 0 | VtxPowerSelfTests (Reichweite je Stufe, Wand, Mehrstrom, Taste F10, OSD, Hitzeschutz, Auto digital; mit `-PpropwashScreenshots=true` dazu 9 Schritte `bildschirmfotos: VTX-Leistung – …`, `docs/design/vtx-power.md`) |
| `kamera:` | 8 | 0 | FpvFeelSelfTests (Latenzkette Knüppel bis Bild, Kamera-Vibration nur im Bild, Treffer-Neigung des Piloten nicht im FPV-Bild, bei hohem Tempo bleibt das Bild ruhig – Vollgas-Steigflug mit dem 5″ Freestyle, je Bild Kamera zu Rahmen, Posen-Zeitfehler und Drehrate, dasselbe mit leicht abgenutzten Teilen (Props 95 %, Motoren 97 %) und als Gegenprobe mit einem verbogenen Prop; die Schritte schalten selbst scharf und laufen auch einzeln hinter „Testdrohne im FPV“; mit `-PpropwashScreenshots=true` dazu `bildschirmfotos: Kamera-Vibration aus und an (Bildfolge)` nach `docs/screenshots/camera_shake/`, von git ignoriert; `docs/design/fpv-feel.md`) |
| `objektiv:` | 2 | 0 | FisheyeSelfTests (Fisheye-Objektiv: Marken an der berechneten Stelle je Videosystem und Bildformat bei 0, 80 und 100 % (analog auch 40 %), Bildmitte mit jeder Stärke größer, Bildrate flach gegen Fisheye; mit `-PpropwashScreenshots=true` dazu die Bilder `fisheye_*`, `docs/design/fisheye-lens.md` §8) |
| `iris:` | 1 | 0 | IrisSelfTests (ohne Iris übersprungen; mit Iris dazu 16 Schritte `bildschirmfotos: Iris – …`, darunter zwei mit Fisheye-Objektiv, §5) |
| `audio:` | 4 | 1 | AudioSelfTests, AudioReachSelfTests |
| `osd:` | 10 | 1 | OsdSelfTests, OsdOverlapSelfTests (`osd: Vorlagen ohne Überlappung`, dasselbe auf Deutsch zwischen `osd: Sprache Deutsch für die Vorlagen` und `osd: Sprache nach den Vorlagen zurücksetzen`) |
| `akku-sag:` | 1 | 0 | BatterySagSelfTests – Punch mit vollem und fast leerem Akku, OSD-Spannung blinkt nur am Ende des Packs (Bildschirmfotos: `bildschirmfotos: Akku-Sag`) |
| `tricks:` | 11 | 1 | TrickSelfTests, ProTrickSelfTests, TrickTutorialSelfTests (Trick-Tutorials: Szenen und Texte aller Tricks, Vorschau beim Hovern und Klick in den Player, Player mit Schritten, Pause, 0,5× und Stick-Cam, Stick-Cam in Mode 2 und Mode 1; mit `-PpropwashScreenshots=true` dazu 24 Schritte `bildschirmfotos: Freestyle-Tutorial …`, `docs/trick-tutorials.md`) |
| `interact:` | 12 | 0 | InteractSelfTests |
| `blockbruch:` | 11 | 0 | BlockBreakSelfTests (Welt-Regel „Drohnen zerbrechen Blöcke“, `docs/design/drone-break-blocks.md` §6; ohne Bildschirmfotos 9) |
| `glasbruch:` | 18 | 0 | GlassFeelSelfTests (Durchbruch durch Glas im FPV-Flug: Tempoverlust, Drehstoß, Kameraschlag, Scherben, `docs/design/drone-break-blocks.md` §3.2; dazu fünf Anflüge aus 50 Blöcken – dreimal bei 30 Bildern/s, einmal bei 60 und durch eine Glasröhre –, bei denen die Scheibe mindestens 15 Blöcke vorher freigegeben sein muss; ohne Bildschirmfotos 16) |
| `kreativ-drohne:` | 6 | 0 | CreativeSelfTests |
| `guide:` | 25 | 2 | GuideSelfTests, TrainingSelfTests, TrainingCourseSelfTests |
| `api:` | 18 | 0 | ApiSelfTests, ApiAdditionSelfTests, ApiClientSelfTests, ApiLootSelfTests |
| `addon-api:` | 11 | 0 | AddonApiSelfTests (API 1.6 mit dem Beispiel-Zubehör: Halterung vorne, Inventar, Fenster „Zubehör“, Treffer- und Schaden-Event, Abfragen und Hindernis-Scan, Werfer mit Rückstoß im Flug, Kino-Flughilfe mit Umkreisen, Gimbal und HUD im FPV; `docs/design/addon-api-1.6.md` §14; mit Bildschirmfotos 14, davon zwei Rahmen-Schritte für das Fenster) |
| `assets-world:` | 6 | 0 | WorldAssetsSelfTests |
| `werkbank:` | 1 | 0 | ShowcaseTour |
| `bando:` | 6 | 13 | BandoSelfTests, BandoWorldTests, BandoPlacementTests |
| `stickmode:` | 4 | 0 | StickModeSelfTests (Assistent in Mode 1–4, Empfänger-Tab samt Suche, Flugschule), StickOverlaySelfTests (Stick-Overlay im OSD); Bildschirmfotos `bildschirmfotos: Stick-Mode, …` |
| `preview:` | 9 | 1 | PreviewSelfTests (Vorschau-Build: Schalter gegen Konstante und Kennzeichen im Jar, Fenstertitel „Propwash Preview <Version>“, Wasserzeichen Bildpunkt für Bildpunkt im Spiel, mit F1, im Pausenmenü, mit Ressourcenpaket ohne Propwash-Texte und Minecraft-Schrift und mit eigenem `core/gui.fsh`, nach dem Verlassen im Titelbildschirm, in Action-Cam-Bild und Foto, immer mit der eigenen Pipeline; ohne Schalter nie aufgerufen; Abschnitt „Vorschau-Build“) |

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

Auf dem Entwicklungsrechner laufen Minecraft-Prozesse nur über ein Slot-Skript außerhalb des Repositorys; jeder
`run*`-Task läuft über dieses Skript. Es vergibt zwei Client-Slots fest, einen dritten nur auf Anfrage
(`tools/selftest-sharded.sh`) und bei freiem Speicher, zwei eigene Slots für dedizierte Server (ein Server weicht
notfalls auf einen freien Client-Slot aus) und setzt `PROPWASH_MC_SLOT=1` … `5`; Server-Tasks desselben Arbeitsbaums
laufen nie gleichzeitig (§6).

- Run-Verzeichnisse liegen je Arbeitsbaum (`fabric/runs/…`, `neoforge/runs/…`), ebenso Welten, Ergebnisdateien,
  Schrittliste und `docs/screenshots`. Zwei Arbeitsbäume kommen sich dort nicht in die Quere.
- Ports der Server-Rauchtests (`:fabric:runServerSmoke`, `:neoforge:runServerSmoke`): `-PpropwashServerPort=<n>` setzt
  den Fabric-Port, NeoForge nimmt `n + 1`. Ohne Property gilt `25600 + 10 × PROPWASH_MC_SLOT + 1` (Fabric) bzw. `+ 2`
  (NeoForge), also 25611/25612 in Slot 1, 25621/25622 in Slot 2, 25631/25632 in Slot 3 und 25641/25642 bzw.
  25651/25652 in den Server-Slots 4 und 5; ohne Slot 25601/25602. `prepareServerSmoke`
  schreibt den Port in `server.properties`, meldet ihn im Log und bricht vor dem Start ab, wenn der Port belegt ist.
  Es richtet nur die Loader ein, deren `runServerSmoke` im Aufruf läuft: Vorher schrieb ein NeoForge-Rauchtest im
  Server-Slot daneben auch die `server.properties` von Fabric neu (Fabric startete in Slot 4 mit dem Port von Slot 5)
  und löschte die Welt des laufenden Fabric-Servers. `prepareRuns` patcht ebenso nur die Clients im Aufruf.
  Arbeitsbäume ohne diesen Stand (0.5.2 und Stränge, die vor dem Zusammenführen von `infra/test-parallel` abzweigen)
  schreiben weiter beide Loader neu; das Slot-Skript lässt Server-Tasks desselben Arbeitsbaums deshalb nie gleichzeitig
  laufen (Arbeitsbaum-Sperre, §6).
  Der Selbst- und der Client-Rauchtest öffnen keinen Port (integrierter Server ohne LAN).
- `org.gradle.parallel` bleibt aus: zwei `run*`-Tasks in einem Gradle-Aufruf laufen nacheinander, sonst stünden zwei
  Spiele in einem Slot.
- Zwei Gradle-Aufrufe im selben Arbeitsbaum nicht gleichzeitig starten (gemeinsame `build/`- und Run-Verzeichnisse).
  Ausnahme: Läufe mit verschiedener Run-Kennung (`-PpropwashRunTag`, §6) über `tools/selftest-sharded.sh`, das die
  Teile erst startet, wenn eine Schrittliste alles gebaut hat, und ihre Gradle-Starts nacheinander staffelt.
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
  oder `propwash.smoketest`. Die Hilfs-Mod hängt seit 0.6.0 optional von Propwash ab und gibt
  `propwash:client = optional` an. Sie steht deshalb nicht in der Beitritts-Übersicht, und „beitritt: Add-on ohne
  Client-Bedarf steht nicht in der Übersicht“ liest daran die echten Metadaten beider Loader.
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
(`docs/design/join-check.md`). Belegt einen Server- und einen Client-Slot (§6): erst den Server starten, den Client erst, wenn der Server
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
- Flughilfe auf dem dedizierten Server (`docs/design/assisted-laps.md` §6): `PROPWASH_JOINTEST_ASSIST=1` am Server setzt
  3 s nach dem Beitritt Schwerkraft 130 %, Crash-Toleranz 200 % und Drohnen-Trails, nach 20 s wieder alles auf Standard
  (`dev.AssistJoinCheck`, Log `[Beitrittstest] Flughilfe: …`); derselbe Schalter am Client wartet auf beide Stände und
  schreibt die Gründe der Race-Tab-Prüfung dazu. Erwartung:
  `PROPWASH_JOINTEST_EXPECT='joined|assist_rules=personal_rules:gravity_scale=130/100+personal_rules:crash_tolerance=200/100|assist_after_reset=clean'`
  (seit rc.3 setzt der Server Schwerkraft und Crash-Toleranz als persönliche Regeln des Spielers, Serverregeln machen
  keine Runde mehr assisted).
- Zonen auf dem dedizierten Server (`docs/design/zones.md` §8): `PROPWASH_JOINTEST_ZONES=1` am Server (mit
  `PROPWASH_JOINTEST_PILOT=1`) lässt einen zweiten Spieler mit Recht (simuliert am Server, `dev.ZoneJoinCheck`, Log
  `[Beitrittstest] Zonen: …`) 2 s nach dem Beitritt eine rennfreie Schwerelos-Zone um den Piloten und einen Übungsplatz
  anlegen, nach 20 s die Zone auf Flughilfe schalten, nach 23 s die Server-Gründe der Drohne ins Log schreiben und nach
  30 s beide Zonen löschen; derselbe Schalter am Client (`zone.client.ZoneJoinProbe`) schickt selbst eine Zone (muss
  abgelehnt werden) und schreibt Empfang, Recht, Schwerkraft in der eigenen Sim und die Gründe dazu. Erwartung:
  `PROPWASH_JOINTEST_EXPECT='joined|zones_synced=2|zones_editable=false|zones_guest_edit=denied|zone_gravity=0|zone_race_legal_assist=clean|zone_assist=named|zone_left=world|zone_after_assist=clean'`.
- RX-Block auf dem dedizierten Server (`docs/design/rx-block.md`): `PROPWASH_JOINTEST_RX=1` am Server setzt 2 s nach dem
  Beitritt neben den Spieler einen an ihn gebundenen RX-Block (Gas) und einen fremden, legt einen zweiten eigenen Block
  in einem Chunk 30 Chunks entfernt an und entlädt ihn (`dev.RxJoinCheck`, Log `[Beitrittstest] RX: …`); sobald der
  Client die Haltestufe erreicht und der Chunk des zweiten Blocks wirklich entladen ist (erst dann prüft der Server das
  Verzeichnis), meldet der Server mit einem Smaragdblock (sonst Redstone-Block) zwei Blöcke westlich des Spielers, ob
  jener Chunk die ganze Zeit entladen blieb. Derselbe Schalter am Client (`rx.client.RxJoinProbe`)
  fährt mit der Test-Funke Gas 0 → 50 → 100 %, setzt die Reichweite auf 1 (hält), versucht den fremden Block zu ändern
  und liest das Zeichen. Erwartung:
  `PROPWASH_JOINTEST_EXPECT='joined|rx_demand=stick:3|rx_follow=ok|rx_hold=ok|rx_foreign_denied=ok|rx_unloaded_untouched=ok'`.
- Druckplatten auf dem dedizierten Server (`docs/drone-triggers.md`): `PROPWASH_JOINTEST_PLATES=1` an beiden
  Prozessen. Der Server (`dev.PlateJoinCheck`, Log `[Beitrittstest] Druckplatte: …`) legt 3 s nach dem Beitritt drei
  Blöcke östlich des Spielers eine Holzdruckplatte mit einer Eichentür östlich daneben, setzt eine Freestyle-Drohne
  0,75 Blöcke über die Platte, gibt Kreativ-Brille und Funke und startet FPV; die Drohne sinkt in der Sitzung des
  Clients auf die Platte, die Position kommt also vom Client. Der Client
  (`interact.client.DroneTriggerSelfTests#plateJoinProbe`) wartet auf die Sitzung und bis zu 60 s auf Platte und Tür und
  schreibt `plate powered|unpowered|missing`, `door open|closed|missing` und `plate_pilot=fpv|ended`. Erwartung:
  `PROPWASH_JOINTEST_EXPECT='joined|plate powered|door open|plate_pilot=fpv'`; der Server loggt dazu, nach wie vielen
  Ticks die Platte anging und dass die Drohne gesteuert und vom Client bewegt war.
- Gestapelte Gates auf dem dedizierten Server (`docs/gate-legs.md`): `PROPWASH_JOINTEST_STACKGATE=1` am Server baut 3 s
  nach dem Beitritt 24 Blöcke östlich eine Leiter aus zwei Gates als Kurs (eine Runde), stellt den Spieler davor und
  startet eine FPV-Sitzung (`dev.StackGateJoinCheck`, Log `[Beitrittstest] Gate-Stapel: …`, auch „Runde von … angenommen“).
  Derselbe Schalter am Client wartet, bis er das untere Gate auf Beinen erkennt, fliegt eine Sekunde später die Runde in
  halber Rahmenhöhe unten durch das untere Gate, oben durch das obere und wieder unten durch (6 m/s, Sim-Teleport je Tick)
  und schreibt `stackgate client=counted`, `stackgate client_legs=true`, `stackgate lap_ms=…` sowie
  `stackgate server=accepted`, sobald die Bestenliste des Servers die Runde zeigt (`server=missing` nach 30 s). Erwartung:
  `PROPWASH_JOINTEST_EXPECT='joined|stackgate client=counted|stackgate server=accepted'`, `PROPWASH_JOINTEST_LABEL=stackgate`.
  Mit `PROPWASH_JOINTEST_STACKGATE=2` an Server und Client (Befund RC3P-3) stapelt der Server das obere Gate mit der
  Dreh-Vorliebe 45°, dreht es mit der Dreh-Taste auf 90° und im Gate-Menü zurück auf 45° (Log `oberes Gate beim Stapeln …,
  Dreh-Taste … Gate-Menü …`); der Client fliegt auf einer Kreisbahn um den Stapel unten durch das untere Gate und quer
  durch das gedrehte obere (`race.StackTurnRoute`) und schreibt zusätzlich `stackgate client_upper=MOUNTED` und
  `stackgate client_turn=45.0`. Erwartung: `PROPWASH_JOINTEST_EXPECT='joined|stackgate client=counted|stackgate
  server=accepted|stackgate client_upper=MOUNTED'`, `PROPWASH_JOINTEST_LABEL=stackgate_turned`.
- Signal-Gedächtnis auf dem dedizierten Server (`docs/signal-memory.md`): `PROPWASH_JOINTEST_SIGNAL=1` am Server baut
  3 s nach dem Beitritt 40 Blöcke östlich des Spielers eine Steinwand 5 × 30 × 20 Blöcke und schickt ihn nach 25 s 250
  Blöcke nach Westen (mehr bei größerer Sichtweite, `dev.SignalJoinCheck`, Log `[Beitrittstest] Signal: …`). Derselbe
  Schalter am Client sucht die Wand, misst den Verlust einer Strecke durch sie mit geladenen Chunks, wartet, bis die
  Chunks verworfen und gemerkt sind, misst erneut und zur Gegenprobe ohne Gedächtnis. Danach wartet er, bis der
  Chunk-Strom am neuen Ort abgeschlossen ist (alle Chunks in Sichtweite um den Spieler geladen, Zahl geladener Chunks 40
  Ticks unverändert, höchstens 30 s), und trennt erst dann: Wer mitten im Strom trennt, lässt ungelesene Daten liegen, der
  Server loggt dann „Connection reset by peer“ als ERROR und `runServerSmoke` scheitert an der Log-Prüfung. Er schreibt
  `signal_memory=ok|fail`, `signal_loaded_db`, `signal_remembered_db`, `signal_without_memory_db`, `signal_chunks`,
  `signal_stream=settled|open`, `signal_stream_chunks`, `signal_stream_quiet_ticks` und `signal_stream_wait_ticks` (ok:
  beide mindestens 20 dB, Abweichung höchstens 3 dB, ohne Gedächtnis 0 dB und `signal_stream=settled`). Erwartung:
  `PROPWASH_JOINTEST_EXPECT='joined|signal_memory=ok'`, `PROPWASH_JOINTEST_LABEL=signal`.
- Kollisionsregeln auf dem dedizierten Server (`docs/addon-collision.md`): `PROPWASH_JOINTEST_COLLISION=1` an beiden
  Prozessen (Beispiel-Add-on an). Der Server (`dev.CollisionJoinCheck`, Log `[Beitrittstest] Kollision: …`) baut 3 s nach
  dem Beitritt 4 Blöcke südlich des Spielers eine 3 Blöcke dicke Steinwand, setzt eine Freestyle-Drohne davor, gibt
  Kreativ-Brille und Funke und startet FPV. Sobald die Drohne scharf ist, setzt er sie 2 m hoch vor die Wand:
  erst ohne Recht (Log „Kollision ohne Recht: … Rücksetzungen, collision.denied …“), dann mit dem Entity-Tag
  `propwash_example_ghost` (Log „Kollision mit Recht: Durchflug angenommen“). Der Client (`drone.collision.client.CollisionJoinProbe`)
  schärft, schwebt, schaltet den Geist über den Add-on-Schalter `propwash_example:ghost` ein (Taste G, `AddonSwitchC2S`),
  fliegt nach jeder Rücksetzung mit 6 m/s gegen die Wand, schaltet den Geist nach dem Durchflug wieder aus und schreibt
  `collision ghost switch|forced|off`, `collision denied seen|missing`, `collision passed|blocked`,
  `collision switch after off|on` und die Zahl der Rücksetzungen. Der Server erzwingt nichts (`ExampleGhost.force`
  bleibt aus), zählt `ADDON_SWITCH_CHANGED` des Geists und prüft am Ende beider Phasen, dass der Schalter am Server an
  ist (Log „Geist über den Add-on-Schalter (am Server an, …)“, sonst ERROR „Geist nicht über den Add-on-Schalter“).
  Erwartung: `PROPWASH_JOINTEST_EXPECT='joined|collision ghost switch|collision denied seen|collision passed'`,
  `PROPWASH_JOINTEST_LABEL=collision`.
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

### Zwei Clients über LAN (Trails anderer Piloten)

Prüft, was ein Mitspieler von einer Drohne sieht, die ein anderer Spieler per Hand fliegt – der Fall, den ein
Selbsttest mit einem Client nicht abdeckt (dort sind fremde Drohnen vom Server bewegt). Drei Prozesse (Server und
zwei Clients) wären einer zu viel, deshalb ist der erste Client der Gastgeber: sein integrierter Server ist derselbe
Server-Code wie auf einem dedizierten Server (Zustandspakete, `applyPilotState`, Entity-Tracking).

- `tools/remote_trails_test.sh [fabric|neoforge]` startet den Gastgeber (`runClientSelftest` mit
  `-PpropwashSelftestOnly=lan-trails`, Umgebungsvariable `PROPWASH_LANHOST=1`, Spieler `PwLanPilot`): er stellt eine
  Drohne auf den Boden, schaltet die Welt-Regel „Drohnen-Trails“ ein, gibt die Welt ohne Anmeldung im LAN frei und
  schreibt den Port nach `runs/selftest-client/lan-trails/ready`. Dann tritt der Beobachter bei (`runClientSmoke` mit
  `PROPWASH_JOINTEST=127.0.0.1:<port>`, `PROPWASH_JOINTEST_TRAIL=<sekunden>`, Spieler `PwLanWatcher`). Sobald er da ist,
  stellt der Gastgeber ihn 10 m neben den Startpunkt, startet eine FPV-Sitzung, schärft, steigt auf etwa 7 m und pendelt
  im Angle-Modus mit Nick ±0,3 alle 2,5 s vor und zurück (bis etwa 9 m/s), bis der Beobachter geht.
- Der Beobachter blickt auf die fremd gesteuerte Drohne und zählt ab dem Abheben `PROPWASH_JOINTEST_TRAIL` Sekunden
  (Standard im Skript 20, `PROPWASH_TRAIL_SECONDS`): Ticks in der Luft, Ticks mit gezeichnetem Trail, Proben, Ticks
  mit dem Flag „am Boden“. Ergebnis in `runs/smoke-client/jointest/remote-trails.txt` (`remote trail drawn|none`,
  `remote ground flag clear|stale`) mit Bildschirmfoto ohne HUD; Erwartung im Skript
  `joined|remote trail drawn|remote ground flag clear|!remote trail none` (`PROPWASH_TRAIL_EXPECT`).
- Beide Prozesse belegen je einen Minecraft-Slot; `PROPWASH_MC_LAUNCH=<Startskript>` stellt jedem Gradle-Aufruf ein
  Skript voran (zum Beispiel eine Slot-Warteschlange). Protokolle in `PROPWASH_TRAIL_LOGS` oder einem neuen Ordner
  unter `/tmp`; das Skript endet mit Fehler, wenn einer der beiden Läufe scheitert.

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

Zeitgrenze: Der Lauf in der Testwelt endet nach 6 s je ausgewähltem Schritt, mindestens nach 80 min
(`SelfTestRunner.runTimeoutTicks`, Untergrenze `GLOBAL_TIMEOUT_TICKS`; bis zur siebten Gegenprüfung fest 70 min, danach
bis zum Wellenabschluss W1 von 0.6.0 fest 80 min). Der Wächter beendet das Spiel 16 min nach dieser Grenze, bei 80 min
also wie bisher nach 96 min; die Zeile „Zeitgrenze … min für … Schritte in der Welt, Wächter nach … min“ im Log nennt
beide. Feste 80 min reichten im Wellenabschluss W1 nicht mehr: Teil 2 des Fabric-Laufs mit Bildschirmfotos (1293 Schritte
in der Welt) brach nach 86 min bei Schritt 1252 ab („Zeitüberschreitung in Phase RUN“), 42 Schritte liefen nicht. Gemessen
waren 4,1 s je Schritt auf Fabric und 4,8 s auf NeoForge; mit 6 s je Schritt liegt die Grenze dieses Teils bei 129 min
(`SelfTestRunnerTimeoutTest`). Zur siebten Gegenprüfung brauchte der volle Fabric-Lauf mit Bildschirmfotos 61:43 min; deshalb prüft
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
Seit 0.6.0 (Befund WA-W1-16) wartet er außerdem, bis die Drohne 5 Ticks lang langsamer als 0,05 m/s ist, und setzt
erst dann den Spieler daneben. Vorher wählte er den Platz, sobald sie aufsetzte, und verglich danach mit einem Platz,
der mit der rutschenden Drohne weiterwanderte: Rutschte sie nach dem Versetzen mehr als 0,2 m (Abpraller, verspätetes
Versetzen unter Last), war der Schritt verloren und scheiterte nach 61 Ticks mit „Spieler nicht versetzt“. Die
60 Ticks fürs Versetzen zählen jetzt ab dem Versetzen, die bis zu 100 Ticks Warten gehen nicht mehr davon ab.
`CrashFallPlacementTest` (JUnit) prüft Warten, Versetzen und beide Zeitgrenzen ohne Minecraft.

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

## 6. Slot-Pools und Selbsttest in Teilen

### Slot-Pools

Das Slot-Skript außerhalb des Repositorys (§3) wird wie bisher vor den Gradle-Aufruf gesetzt
(`<slot-skript> ./gradlew :fabric:runClientSelftest …`) und wählt den Pool aus den Task-Namen:

- **Client-Pool:** jeder `run*`-Task außer `runServer*`, `webExport` von Just More Parts und jeder Befehl, der nicht
  Gradle ist (Skripte, `java -jar …`). Slot 1 und 2 immer. Slot 3 nur mit `MCSLOT_GUARDED_CLIENT=1`
  und wenn der Speicher-Wächter zustimmt; die Variable setzt nur `tools/selftest-sharded.sh`, das einen Abbruch dort
  (Exit 75) wiederholt, und sie gilt nur für diesen einen Aufruf. Ketten und Agenten ohne sie bekommen nie Slot 3 und
  nie Exit 75. Stehen Server- und Client-Tasks im selben Aufruf, zählt der Client (samt Arbeitsbaum-Sperre).
- **Server-Pool:** `runServer` und `runServerSmoke` (dedizierter Server ohne Fenster). Slot 4 und 5, beide mit Wächter.
  Sperrt der Wächter oder sind beide belegt, nimmt der Server einen freien Slot 1 oder 2 wie beim alten Skript, wartet
  also nie länger als früher (Reserve dort mit dem Bedarf eines Servers). Ein Beitrittstest (§3) belegt so einen
  Server- und einen Client-Slot statt beider Client-Slots.
- **Arbeitsbaum-Sperre:** zwei Aufrufe mit Server-Task (oder `MCSLOT_POOL=server`) aus demselben Arbeitsbaum laufen nie
  gleichzeitig, aus verschiedenen Arbeitsbäumen schon (Slot 4 und 5). Server-Task heißt `runServer*` und jeder Task,
  der von `prepareServerSmoke` abhängt: bei Just More Parts auch `runWebExport` und `webExport` (dessen
  `prepareServerSmoke` schreibt auch das Web-Export-Verzeichnis neu); die beiden bleiben im Client-Pool. Ein Aufruf mit
  `prepareServerSmoke` allein nimmt keinen Slot, wartet aber auf die Sperre. `MCSLOT_POOL=none` hebt sie auf,
  `<slot-skript> --server-task <befehl>` zeigt 1 (mit Sperre) oder 0. Grund: `prepareServerSmoke` ohne den Fix aus §3
  schreibt `server.properties` beider Loader neu und löscht beide Welten, ein Fabric- und ein NeoForge-Rauchtest im selben
  Arbeitsbaum zerstören sich also gegenseitig. Auch mit dem Fix teilen sich zwei Läufe desselben Loaders ohne Run-Kennung
  ein Verzeichnis; die Sperre bleibt deshalb und kostet nur die etwa 15 s eines Rauchtests. Arbeitsbaum = Git-Wurzel des
  Aufrufverzeichnisses und jedes Verzeichnisses hinter `cd`, `-p` oder `--project-dir` im Befehl (auch in `sh -c '…'`;
  `<slot-skript> --trees <befehl>` zeigt sie). Ein Slot-Aufruf im Befehl eines anderen erbt dessen Sperren
  (`MCSLOT_TREES`) und wartet nicht auf sich selbst.
- **Sperren:** `flock(2)` auf `mc.slot.<n>.lock`, `mc.guard.lock` und `mc.tree.<pfad>.lock` im Verzeichnis des Skripts
  (gesetzt über `perl`, ohne `perl` startet nichts, Exit 69). Die Sperre gehört der offenen Datei: der Befehl und der
  Gradle-Client erben den Deskriptor, die Sperre hält, bis der letzte von ihnen endet, auch wenn das Slot-Skript selbst
  mit KILL stirbt (Minecraft erbt ihn nicht, Java schließt beim Start von Kindprozessen alle Deskriptoren über 2, aber
  der Gradle-Client wartet auf Minecraft; ein Gradle-Daemon hält ihn nie). Keine Pid-Prüfung, kein Altersablauf, keine
  verwaisten Slots; `zsystem flock` von zsh taugt dafür nicht (fcntl-Sperre, endet mit dem Prozess, Kinder erben sie
  nicht). `mc.slot.<n>/` enthält nur Angaben für `--status` (Pid, Pool, Arbeitsbaum, Befehl) und wird nur vom Besitzer
  entfernt; Slot-Verzeichnisse des alten Skripts (mkdir ohne flock) gelten, solange ihre Pid lebt.
- Gradle-Aufrufe ohne `run*`-Task (`build`, `test`, `publishToMavenLocal`; Gradle heißt: ein Wort `gradlew`, `gradle`
  oder `…/gradlew` im Befehl, auch in `sh -c '…'`) und `tools/selftest-sharded.sh` (holt für jeden Teil selbst einen
  Slot) laufen sofort ohne Slot. Andere Befehle ohne `run*`-Task (Skripte, `java -jar …`) nehmen einen Client-Slot;
  bis zum 17.10. liefen sie ungebremst. `MCSLOT_POOL=client|server|none` erzwingt einen Pool, etwa `server` für ein
  Skript, das selbst einen Server startet, oder `none` (sofort, ohne jede Sperre) für ein Skript ohne Minecraft.
- Ports nach `PROPWASH_MC_SLOT` setzt das `build.gradle` jedes Repositorys, nicht das Slot-Skript: Propwash
  `25600 + 10 × Slot + 1/2` (§3), ein weiteres Repository `25700 + 10 × Slot + 1/2`, Just More Parts `25611 + Slot`
  (beide Loader gleich). Zwei gleichzeitig belegte Slots teilen sich so keinen Port, auch über die drei Repositorys
  hinweg (Just More Parts in Slot 1 hat 25612 wie Propwash-NeoForge in Slot 1, beide laufen nie gleichzeitig). Das gilt
  nur für diese Formeln und nur mit Slot: ohne Slot-Skript liegt Just More Parts auf 25611 wie Propwash-Fabric in Slot 1.
  Bis zum 17.10. nahm das weitere Repository `25641 + Slot`, in Slot 1 also den Port des NeoForge-Servers von Propwash
  in Slot 4.
- **Wächter beim Start:** frei + löschbar + inaktiv (`vm_stat`), inaktiv aber höchstens bis zur Menge der Dateiseiten
  (`File-backed pages`; anonyme inaktive Seiten müsste macOS erst komprimieren oder auslagern), abzüglich der Reserve
  aller belegten Slots muss für Slot 3 mindestens 8 GB, für Slot 4 und 5 mindestens 2 GB betragen, und
  `kern.memorystatus_vm_pressure_level` muss normal (1) sein. Die Messungen unten entstanden noch mit frei + inaktiv +
  löschbar; das strengere Maß zählt nie mehr, die Schwellen bleiben. Reserve je belegtem Slot: erwarteter Bedarf seines
  Pools (Client 5 GB, Server 2 GB) minus dem, was seine Prozesse schon
  belegen (physischer Speicher laut `top` samt komprimiertem Anteil – RSS zählt den nicht). Ein Lauf, der noch im
  Gradle-Teil steckt, zählt so mit vollem Bedarf; ein zweiter Lauf direkt dahinter überbucht den Speicher nicht. Zu den
  Prozessen eines Slots gehören die Nachkommen des Slot-Skripts und jedes Minecraft mit seiner Umgebungsvariable
  `MCSLOT_HOLDER` (Minecraft unter einem langlebigen Gradle-Daemon ist kein Nachkomme). Schwellen über `MCSLOT_*`
  einstellbar (Kopf des Skripts).
- **Wächter im Lauf:** nur Slot 3 wird beendet (Exit 75), wenn zweimal hintereinander weniger als 1 GB frei ist oder der
  Druck kritisch (4) wird – lieber einen Teil neu starten als zwei hängende Clients (so geschehen, als zwei Clients den
  Speicher aufgebraucht hatten). Slot 1, 2, 4 und 5 beendet er nie, ein Server-Rauchtest einer Kette scheitert also
  nicht an Exit 75. Gradle-Daemons fasst das Skript nie an.
- `<slot-skript> --status` zeigt Slots (auch einen, dessen Slot-Skript beendet ist, während sein Befehl die Sperre
  noch hält), Arbeitsbaum-Sperren, Speicher und die Entscheidung beider Wächter, `--sample` eine CSV-Zeile
  (`--sample-header`, 18 Felder, am Ende Datei- und anonyme Seiten), `--guard client|server` die Entscheidung allein.
  Das Skript prüft sich mit einem eigenen Selbsttest ohne Minecraft (Platzhalter-Befehle, simulierter Speicher, 155
  Prüfungen): Pool-Wahl und Arbeitsbäume, Server-Tasks (`runWebExport`, `webExport`, `prepareServerSmoke` allein ohne
  Slot, aber mit Sperre), Befehle ohne Gradle im Client-Pool, `?` in `--status` ohne Glob, die Ports aller drei
  Repositorys, Slot 3 nur auf Anfrage und ohne Anfrage nie Exit 75, Sperre und Freigabe bei
  wenig Speicher und hohem Druck, Reserve je Pool, Ausweichen der Server auf Slot 1 und 2, Arbeitsbaum-Sperre (auch mit
  `cd`, gemischtem Aufruf und verschachtelt), fremde flock-Sperren, Slots des alten Skripts, Aufräumen nur der eigenen
  Angaben, KILL des Slot-Skripts (der Befehl hält den Slot, bis er endet), Abbruch mit TERM/INT, auch eines Wartenden,
  Abbruch durch den Wächter, eindeutige Ports und einen Stresstest: 30 Runden mit je 12 Wartenden (Clients, Clients mit
  Slot 3, Server, zwei davon aus demselben Arbeitsbaum), in jeder Runde zwei Besitzer mit KILL beendet, jeder Befehl
  belegt atomar ein Verzeichnis je Slot und Arbeitsbaum – 360 Belegungen, keine doppelt. Das vorige Skript
  (mkdir-Sperre mit Pid-Prüfung) vergab im selben Test in 5 Runden 13-mal einen Slot doppelt.

Messung am 17.10. während eines Release-Laufs (Fabric mit Bildschirmfotos und NeoForge gleichzeitig, 50 min lang bis
zum Bildschirmfoto-Rundgang, am Rechner lief Chrome, Probe alle 30 s): Fabric-Client bis 3,2 GB, NeoForge-Client bis
3,4 GB, die Gradle-Daemons zusammen bis 2,8 GB; frei + inaktiv + löschbar nie unter 5,6 GB (Mittel 6,1 GB, ohne Clients
etwa 9 GB), Druck immer normal, der Kompressor wuchs von 4,6 auf bis zu 8,7 GB, Swap blieb bei 3,7 GB. macOS fängt die
Clients also zum großen Teil durch Komprimieren anderer Programme auf. Ein dritter Client (knapp 4 GB samt Gradle) hätte
den Rest nahe an den Abbruch-Wert gebracht; der Wächter lässt Slot 3 bei dieser Last nicht zu (6 GB < 8 GB) und gibt
ihn frei, wenn am Rechner weniger läuft.

Messung mit Minecraft am 17.10. abends (zwei Läufe in Teilen über das Slot-Skript, 42 und 69 min, am Rechner liefen
Chrome und Discord, Probe alle 10 s mit `ps`, `top`, `vm_stat` und `memory_pressure`):

| Messgröße | Wert |
|---|---|
| Fabric-Client mit Bildschirmfotos | bis 4,6 GB physisch (RSS 3,3 GB) |
| NeoForge-Client | bis 3,7 GB physisch (RSS 2,6 GB) |
| Server-Rauchtest (Fabric, NeoForge) | bis 1,0 GB physisch (RSS 0,95 GB), etwa 15 s |
| ein Teil samt seinem Gradle (`--no-daemon`) | bis 5,1 GB – die Reserve von 5 GB passt |
| frei + inaktiv + löschbar mit zwei Teilen | mindestens 5,3 GB (frei 0,1, inaktiv 5,2), Mittel 6,6 GB |
| Speicherdruck, `memory_pressure` | immer normal, nie unter 43 % frei |
| Kompressor, Swap | bis 8,7 GB, Swap unverändert 3,5 GB |

Ein dritter Client (bis 5 GB) hätte den Rest unter den Abbruch-Wert von 1 GB gedrückt. Der Wächter ließ Slot 3 in
beiden Läufen nicht zu (nach Reserve 2,6 bis 3,7 GB, nötig 8 GB); die Schwelle bleibt. Ein Server-Rauchtest neben zwei
Clients kostet dagegen gut 1 GB für ein paar Sekunden: dafür reichen 2 GB nach Reserve. Mit den vorher verlangten 4 GB
lief neben zwei Clients nie einer. Neben zwei Clients laufen die beiden Server-Rauchtests so nacheinander im selben
Server-Slot (zusammen knapp 30 s), neben einem Client gleichzeitig in Slot 4 und 5. Beides lief mit `PASS` und den
Ports des jeweiligen Slots. Mit der Arbeitsbaum-Sperre laufen die beiden Server-Rauchtests eines Arbeitsbaums jetzt
immer nacheinander (zusammen knapp 30 s); gleichzeitig nur Rauchtests aus verschiedenen Arbeitsbäumen.

### Run-Kennung

`-PpropwashRunTag=<kennung>` (beide Loader) hängt `-<kennung>` an alle Run-Verzeichnisse
(`runs/selftest-client-<kennung>`, …): Mehrere Läufe desselben Tasks laufen so in einem Arbeitsbaum gleichzeitig, jeder
mit eigener Welt, eigenem Log und eigener Ergebnisdatei. `prepareRunTag` legt ein Verzeichnis beim ersten Mal aus dem
Basis-Verzeichnis an (`options.txt` samt `graphicsPreset`, `servers.dat`, `config/`; keine Welten, keine Logs) und
spiegelt vor jedem Start dessen `mods/` mit harten Links (Flashback, Just More Parts; die Jars werden nicht kopiert).
`prepareRuns` schaltet danach wie immer stumm. Ohne die Property ändert sich nichts. Erlaubt sind Buchstaben, Ziffern,
Punkt, Unter- und Bindestrich. `docs/screenshots` bleibt gemeinsam; die Teile schreiben verschiedene Bilder.

### Selbsttest in Teilen

```sh
PROPWASH_MC_LAUNCH=<slot-skript> tools/selftest-sharded.sh fabric --screenshots          # 2 Teile
PROPWASH_MC_LAUNCH=<slot-skript> tools/selftest-sharded.sh all --screenshots --shards 3
tools/selftest-sharded.sh all --screenshots --plan                                       # nur planen, ohne Minecraft
tools/selftest-sharded.sh fabric --clean                                                 # runs/*-shard-* löschen
```

1. Schrittliste mit `-PpropwashSelftestList=true` unter der Run-Kennung `shard-list` (baut dabei alles, danach haben die
   Teile nichts mehr zu kompilieren).
2. Plan (`tools/selftest_shards.py plan`): die Teile als Bereiche der Liste, als Filter `#von-bis` (§1).
3. Jeder Teil läuft mit Run-Kennung `shard-<n>` und `PROPWASH_SELFTEST_ONLY=<bereiche>` über das Slot-Skript (mit
   `MCSLOT_GUARDED_CLIENT=1`, also auch in Slot 3), Gradle mit `--no-daemon` (der Daemon des Teils endet mit ihm). Die
   Teile starten gestaffelt: der nächste startet Gradle erst, wenn das Spiel des vorigen läuft, so schreibt nie ein
   Gradle die Startdateien in `build/`, während ein anderes Spiel sie liest. Die Start-Sperre ist ein `flock(2)` auf
   `start.lock` im Protokoll-Ordner; sie gehört dem Teil, der sie hält, endet mit ihm (auch bei KILL) und wird nie
   gelöscht, Gradle erbt ihren Deskriptor nicht. Der Filter kommt als Umgebungsvariable, damit die Startdateien aller Teile gleich bleiben. Bricht das
   Slot-Skript einen Teil wegen Speichermangels ab (Exit 75), startet er neu (`--retries`, Standard 2).
4. Prüfung (`tools/selftest_shards.py check`): jeder Teil mit Gradle-Exit 0 und `PASS gefiltert` mit genau der
   geplanten Zahl Schritte, seine Schrittliste gleich der geholten, die PASS-Zeilen im Log genau die geplanten Schritte,
   und jeder Schritt der Liste in genau einem Teil bestanden (Rahmen-Schritte und Ziele der Umschalt-Schritte laufen in
   jedem Teil, der sie braucht). Die Run-Verzeichnisse der Teile bleiben zwischen Läufen bestehen; vor jedem Versuch
   löscht das Skript deshalb Ergebnisdatei und Schrittliste des Teils, und Ergebnisdatei, Schrittliste und Log
   (`latest.log`, rotierte Logs) zählen nur, wenn sie jünger sind als der Start des Versuchs (`--exit`/`--started` je
   Teil). Ein Teil, der vor dem Spiel scheitert (Kompilieren, Port, Abbruch), kann so nie mit dem `PASS` eines früheren
   Laufs durchgehen. Sonst endet das Skript mit FAIL. Dazu eine Speicher-Zusammenfassung aus `--sample` (ob ein dritter Client gepasst hat).
5. Protokolle unter `runs/selftest-sharded/<Zeit>/`: Liste, Plan, Gradle- und Spiel-Ausgabe je Teil, `memory.csv`,
   Prüfung.

`python3 tools/selftest_shards_test.py` prüft Planer und Prüfung und spielt den ganzen Ablauf mit einem Platzhalter statt
Gradle durch (zwei Teile gleichzeitig, fehlender Schritt muss scheitern, ein Teil mit Gradle-Exit 1 oder ohne neues
Ergebnis muss trotz `PASS` aus dem Lauf davor scheitern, Slot 3 nur für die Teile, Gradle ohne Deskriptor der
Start-Sperre, ohne Slot-Skript kein Start; dazu Ergebnis, Log und Schrittliste älter als der Start).

**Regel für die Schnitte.** Teile sind zusammenhängende Bereiche der Schrittliste; geschnitten wird nur zwischen
Gruppen: keine Gruppe (Text vor dem ersten Doppelpunkt) hat Schritte – ohne Rahmen-Schritte – vor und hinter einem
Schnitt. Innerhalb einer Gruppe hängen Schritte voneinander ab: „video: Flashback-Budget – OSD-Element in vier Zuständen“
braucht die Flugsitzung aus „video: Drohne mit Action-Cam (Flashback) im FPV“ und scheiterte gefiltert auf
`video: Flashback-Budget` allein. Bildschirmfoto-Schritte gehören zur Gruppe davor: „drone-render: Prop-Scheiben über
Wasser wurden gezeichnet“ prüft, was „bildschirmfotos: drehende Props im FPV-Bild über Wasser“ gezeichnet hat,
„bildschirmfotos: Drohne nach dem Absturz an der Mauer am Boden“ zeigt die Szene aus „drone: Absturz an einer Mauer …“.
Eine Folge von Bildschirmfoto-Schritten, die mit eigenem Vorbereitungs-Schritt (`setupStep`) beginnt, ist eigenständig
(Rundgang, Hand- und Boden-Items, Item-Rahmen). Schritte nach dem Verlassen der Welt laufen im Teil, der die Schritte
ihrer Gruppe in der Welt hat; die Flashback-Wiederholung samt ihren Fenster-Schritten also im Teil mit `video:`.
Verworfen wurde das Teilen nach Präfixen (etwa `ui-scale:,guide:` / `bildschirmfotos` / Rest): es reißt genau diese
Paare auseinander, und ein Rest-Filter aus Ausschlüssen kann einzelne Bildschirmfotos nicht zurückholen.
Wer einen Schritt anmeldet, der Zustand aus einer anderen Gruppe braucht, legt ihn in dieselbe Gruppe oder meldet die
Vorbereitung als `setupStep`/`switchStep` an (§1); sonst kann ein Schnitt dazwischen fallen.

**Aufteilung.** `--shards <n>` (Standard 2) sucht unter den sauberen Schnitten die Teile mit der kürzesten längsten
Laufzeit; die Dauer je Schritt stammt aus den Logs der letzten Läufe (`latest.log` und rotierte Logs aller Verzeichnisse
`runs/selftest-client*`, die 40 jüngsten), ein Schritt ohne Messung zählt 4,5 s (Mittel der vollen Läufe vom 19.10.;
bis dahin zählte der Median der gemessenen Schritte, und aus gefilterten Läufen schätzte der Planer Teil 2 von Fabric auf
20 statt 86 min). Je Teil kommen 2 min für Gradle, Start und Welt dazu. Ist ein Teil viel länger als die anderen (über
1,3 × Mittel) oder gibt es zu wenige saubere Schnitte, nennt der Plan die Gruppen mit Schritten vor und hinter der Mitte
(„Schnitte verhindern: race #347–#1301 …“, in `plan.json` unter `blockers`); läge ein Teil über seiner Zeitgrenze im
Spiel (wie im Runner 6 s je Schritt in der Welt, mindestens 80 min), warnt er. Stand 19.10.: Fabric und NeoForge haben
je nur einen sauberen Schnitt (nach `core:`), weil `race:`, `server:`, `video:` und `interact:` auch am Ende der Liste
Schritte haben (Gate-Hinweis, `/propwash clear`, Kamerawinkel im K-Menü, Druckplatten); Teil 2 trägt dann fast den
ganzen Lauf (Fabric mit Bildschirmfotos etwa 92 min, NeoForge etwa 59 min), der Lauf in Teilen spart nichts. `--shards guide:,bildschirmfotos` schneidet stattdessen
vor dem ersten Schritt mit diesem Namensanfang (nur an sauberen Stellen, `--allow-unclean` erzwingt es). Pläne vom 17.10.
mit den Laufzeiten des vollen Laufs vom 15.10. (Fabric mit Bildschirmfotos 81 min, NeoForge 54 min):

| Lauf | Teile | Schnitte | geschätzt |
|---|---:|---|---|
| Fabric mit Bildschirmfotos | 2 | vor `api:` | 43 + 42 min |
| Fabric mit Bildschirmfotos | 3 | vor `glasbruch:` und vor dem Rundgang | 31 / 32 / 24 min |
| NeoForge | 2 | vor `guide:` | 29 + 29 min |
| NeoForge | 3 | vor `akku-sag:` und vor `ui-scale:` | 25 / 13 / 21 min |

**Wann es sich lohnt** (gemessen am 17.10. über das Slot-Skript, am Rechner wurde gearbeitet). Für einen Loader allein
ja: Fabric mit Bildschirmfotos in zwei Teilen 42 statt 82 min (Teile 42 und 40 min, Schrittliste unter 1 min), NeoForge
in zwei Teilen etwa 28 statt 54 min (Teile je 27 min). Für beide Loader auf zwei Slots kaum: `all --screenshots` 69
statt 82 min, weil Fabric und NeoForge heute schon gleichzeitig laufen und Fabric der lange Pol ist. Slot 3 gab der
Wächter bei Arbeit am Rechner nicht frei; drei Teile liefen dann auf zwei Slots und dauerten länger als zwei. Die Pläne
lagen bis zu 2 min über den gemessenen Zeiten. Jeder Teil meldet am Ende seine Zeit im Slot und wie lange er davor auf
einen Slot gewartet hat.

**Release.** `docs/RELEASING.md` §4 Schritt 4 bleibt: volle Läufe ohne Filter mit dem Ergebnis genau `PASS`. Ein Lauf in
Teilen ist ein gefilterter Lauf und sieht nicht, was eine Gruppe für eine spätere hinterlässt (Beispiele in §1:
Fenstergröße, GUI-Skala, Chunks); Bildschirmfotos für einen Commit kommen aus dem vollen Lauf. Vorschlag: Prüfungen,
die nur einen Loader brauchen (etwa Fabric mit Bildschirmfotos nach einem Oberflächen-Fix), in Teilen
(`tools/selftest-sharded.sh fabric --screenshots`, 42 statt 82 min); für beide Loader in Teilen nur, wenn Slot 3
frei ist. Schritt 4 einmal voll – Fabric mit Bildschirmfotos und NeoForge gleichzeitig in Slot 1 und 2, die
Server-Rauchtests beider Loader daneben in den Server-Slots, die Client-Rauchtests danach.

Dauer der sechs stummen Läufe aus Schritt 4 (Release-Lauf und Messung vom 17.10.):

| Ablauf | Dauer |
|---|---|
| streng nacheinander (Fabric 82, NeoForge 54, vier Rauchtests je unter 30 s) | etwa 138 min |
| wie bisher: Fabric und NeoForge in Slot 1 und 2, Rauchtests danach in Slot 2 | etwa 82 min |
| dazu Server-Rauchtests in den Server-Slots | etwa 82 min (sie lagen schon im Schatten von Fabric) |
| beide Selbsttests in Teilen auf zwei Slots, Rauchtests daneben | etwa 70 min, aber gefiltert |

Die Server-Slots verkürzen den Release also nicht; sie halten nur die Client-Slots frei. Ein Release in Teilen spart
etwa 12 min und verliert das ungefilterte `PASS`.

**Aufräumen.** Die Verzeichnisse der Teile behalten ihre Welten (bis etwa 1 GB je Teil mit Bando-Welten);
`tools/selftest-sharded.sh <loader> --clean` löscht `runs/*-shard-*` des Loaders.

## 7. Prüf-Infrastruktur 0.6.0: zwei echte Clients, Altwelt-Umstieg, Drehwelt, Klon-Probe

Drei Werkzeuge für die Prüfbereiche V6 (Mehrspieler) und V7 (Altwelt-Umstieg) und für die Drehwelten der Shotliste.
Alle laufen über das Slot-Skript (§6); ihre Ausgaben liegen im Run-Ordner des Arbeitsbaums. Dazu kommt die Klon-Probe:
die JUnit-Tests nach einem frischen Klon des öffentlichen Stands.

### Dedizierter Server mit zwei echten Clients

```sh
S=<slot-skript>
PROPWASH_MC_SLOTS=$S $S tools/mp_dedicated_test.sh fabric        # oder neoforge
PROPWASH_MC_SLOTS=$S $S tools/mp_dedicated_test.sh fabric --keep-tree --timeout 240 --logs /tmp/mp
PROPWASH_MC_SLOTS=$S $S tools/mp_dedicated_test.sh fabric --client-loader neoforge    # NeoForge-Clients auf Fabric-Server
PROPWASH_MC_SLOTS=$S $S tools/mp_dedicated_test.sh neoforge --client-loader fabric    # Fabric-Client auf NeoForge-Server
# ohne $S davor startet sich das Skript selbst über $S neu (PROPWASH_MP_VIA_SLOTS=1), damit die Clients wie im Client-Pool laufen
```

- Ein dedizierter Server (`runServerSmoke`) und Pilot A (`runClientSmoke`) laufen aus diesem Arbeitsbaum, Pilot B
  (`runClientSmoke`) aus einem zweiten Arbeitsbaum desselben Commits (Standard `<arbeitsbaum>-mp-b`, das Skript legt ihn
  mit `git worktree add --detach` an, setzt einen vorhandenen auf den Commit und entfernt einen selbst angelegten am Ende,
  außer mit `--keep-tree`). Nicht eingecheckte Änderungen in einem der beiden Bäume brechen ab (Exit 2). Beide Bäume
  werden vorher gebaut (`classes`, `devtestClasses`, im Build-Pool), damit in den Slots nur noch gespielt wird.
- **Geschützter Fall** (drei Minecraft-Prozesse): das Skript startet nur, wenn `<slot-skript> --status` Slot 3 und
  Slot 5 frei, keinen laufenden Server und einen leeren Build-Pool zeigt. Dann fragt es unter der Wächter-Sperre
  (`mc.guard.lock`) nacheinander Slot 4 (Server), Slot 1 (Pilot A) und Slot 2 (Pilot B) an; vor jedem Slot muss
  `<slot-skript> --guard server` zustimmen, und die schon belegten Slots zählen mit ihrer Reserve (Server 2 GB, Client
  5 GB) mit. Für den dritten Slot heißt das: frei + löschbar + inaktiv mindestens 2 + 2 + 5 GB, also etwa 9 GB bei
  sonst ruhigem Rechner. Stimmt der Wächter für einen Slot nicht zu oder ist einer belegt, gibt das Skript alle wieder
  frei und wartet (`PROPWASH_MP_POLL`, Standard 30 s); es baut nie auf weniger Instanzen um und erzwingt nichts. Nach
  `--timeout` Minuten (Standard 120) ohne freien Fall endet es mit Exit 75, ohne etwas gestartet zu haben.
- Belegung wie beim Slot-Skript: `flock(2)` auf `mc.slot.<n>.lock` im Verzeichnis des Slot-Skripts (oder
  `MCSLOT_DIR`), Angaben für `--status` in `mc.slot.<n>/` (Pid, Pool, Arbeitsbäume, Befehl), dazu die
  Arbeitsbaum-Sperre des Servers. Jeder Prozess bekommt `PROPWASH_MC_SLOT=<n>` (Ports wie in §3, Server in Slot 4
  also 25641 bzw. 25642) und `MCSLOT_HOLDER=<pid>`, damit der Wächter seinen Speicher zuordnet; jeder Gradle-Aufruf
  erbt nur die Sperre seines eigenen Slots. Abbruch mit INT/TERM beendet alle drei Prozessbäume und gibt die Slots frei.
- **Ablauf.** Der Server startet mit `PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_MULTI=1`, die Clients erst, wenn er
  `[Server-Rauchtest] gestartet` meldet (Pilot B 10 s nach Pilot A). `dev.MultiJoinCheck` wartet, bis `PwPilotA` und
  `PwPilotB` (über `-PpropwashDevName`) auf dem Server sind, stellt nach 3 s beide in den Kreativmodus, baut 12 Blöcke
  neben dem Spawn einen Kurs aus drei mittleren Gates (10 Blöcke Abstand), stellt eine 5″-Race-Drohne davor, gibt
  Pilot A eine gebundene Analog-Brille und die Funke und startet FPV; Pilot B bekommt eine Analog-Brille, steht
  seitlich am Kurs und wird Operator (für `/propwash clear` am Ende; das Recht nimmt der Server ihm erst wieder, wenn
  Renn- und Gimbal-Drohne als entfernt gemeldet sind oder B geht). Beim Aufbau schaltet der Server den Mob-Spawn aus und
  entfernt alle feindlichen Mobs (Log `Mob-Spawn aus, feindliche Mobs entfernt: <n> {art=anzahl}`), sonst zerbrechen mit
  Mob Defense Schleime der Flachwelt die scharfe, tief schwebende Renndrohne (Befund V-RC4-3). Sobald der Server B als Zuschauer der Drohne von A sieht, legt er A ein Papier
  `propwash-jointest-watched` in Platz 9 (Zeichen für den Start). Log `[Beitrittstest] Mehrspieler: …` mit Kurs,
  Zuschauer und jeder Änderung der Bestenliste.
- `dev.client.MultiJoinProbe` als Pilot A (`PROPWASH_JOINTEST_MULTI=pilot`): wartet auf FPV und Kurs, schärft am Boden,
  wartet, bis keine Flughilfe mehr anliegt (sonst bis zu zweimal neu schärfen: ein Client, der unter Last spät fertig
  lädt, startet sonst mit Failsafe, und alle Runden zählen als Flughilfe), wartet höchstens 90 s auf das Zeichen und fliegt dann drei Runden mit fliegendem Start (12 m/s, die Drohne folgt der
  Bahn über `TrainingSelfTests.teleport`, Server und Zuschauer sehen echte Zustandspakete). Ab Runde 2 muss der
  persönliche Geist der ersten Runde zu sehen sein. Danach wartet der Pilot, bis seine Runde in der Bestenliste des
  Servers steht (der Server bestätigt jede Runde gegen seine eigenen Gate-Durchflüge), und hält die Drohne, bis seine
  Sitzung durch `/propwash clear` endet. Als Pilot B (`PROPWASH_JOINTEST_MULTI=watcher`): wartet auf die Brille und den
  Kanal von `PwPilotA`, drückt die Zuschauen-Taste, schaltet im eigenen OSD Flugbahn-Marker sowie Runde, Rundenzeit und
  Gate ein, zählt Ticks im Zuschauen, den Weg der Kamera und die Ticks, in denen Marker und Renn-Elemente gezeichnet
  werden, und bleibt, bis die Runde von A in der Bestenliste steht, danach noch 25 s, bis A alle drei Runden geflogen hat
  (288 m mit 12 m/s, 24 s). Vorher waren es 2 s: B schickte `/propwash clear` in die dritte Runde von A (`ABORT@3/0`),
  und mit 13 s startete die Gimbal-Drohne in Runde 3 (einmal `MISSED@3/2`).
- **Operator am Gimbal.** Danach beendet B das Zuschauen. Sobald der Server B nicht mehr als Zuschauer sieht, stellt er
  3 Blöcke vor B eine 5″-Freestyle mit dem Beispiel-Gimbal vorn (`propwash_example:gimbal_front`) und startet sie für
  Pilot A ins autonome Halten in 4 m Höhe (`ExampleFollow.takeoff`, Add-on-Flug am Server, `addon-autonomy`). 4 s später
  bindet er B als Operator (`ExampleOperator.bind`, `drones().operators()`, `operator-gimbal`). B sieht das Bild der
  Drohne über die Zuschauer-Sitzung des Operators, schickt Achsen (Schwenk 1,0, Neigung −0,6) und wartet, bis der
  Gimbal am eigenen Client mindestens 20° geschwenkt hat (mindestens 3 s). Noch als Operator schickt B dann
  `/propwash clear` (B ist dafür Server-Operator); das entfernt die Drohne von A und die Gimbal-Drohne, beendet die
  Sitzung von A, den autonomen Flug und die Operator-Bindung. Danach holen beide die Bestenliste neu.
- Ergebnis: `runs/smoke-client/jointest/multi-pilot.txt` (Baum A) und `multi-watcher.txt` (Baum B) mit Bildschirmfoto.
  Erwartung Pilot A `joined|multi role=pilot|multi watcher signal=seen|multi race finished|multi ghost shown|multi
  leaderboard ok|multi cleared`, Pilot B `joined|multi role=watcher|multi watched pilot|multi watcher marker=drawn|multi
  watcher racehud=drawn|multi watcher osd=pilot|multi watcher gate=lit|multi operator ok|multi operator released|multi
  cleared|multi leaderboard ok` (Pilot A schaltet dafür in seinem OSD Runde, Rundenzeit, Gate und Flugbahn-Marker ein,
  B sieht sie über das übertragene Layout; mindestens 100 Ticks zugeschaut, Kamera
  mindestens 20 Blöcke mitgewandert, Marker in mindestens 5 Ticks gezeichnet, Gimbal mindestens 20° geschwenkt,
  Zuschauen und Operator-Rolle nach `/propwash clear` beendet; die Zeile `multi operator drone=… pan=… pitch=… sent=…
  label=…` nennt Drohne, Gimbal-Lage, gesendete Eingaben und das Label des autonomen Flugs, das nur der Besitzer sieht).
  Das Skript verlangt im Server-Log die Zeilen „Drohne … entfernt (/propwash clear durch PwPilotB)“, „PwPilotB wird
  Operator am Gimbal der Drohne … im autonomen Halten (gebunden, …)“ und „Gimbal-Drohne … entfernt (/propwash clear
  durch PwPilotB), autonomer Flug beendet: …, Operator-Bindungen 0, …“ (dahinter Add-on-Flüge, das größte
  `FlightState#operatorAxis(0)` des autonomen Flugs am Server und die angenommenen Operator-Eingaben), vergleicht die Zeilen `multi board=` beider Clients nach dem
  Aufräumen (Name, Klasse, Zeit, Flughilfe je Eintrag; beide sehen dieselbe Bestenliste), gibt `multi watcher racehud=`
  aus und prüft die drei
  `logs/latest.log` auf Zeilen mit `/ERROR]`, `/FATAL]`, `Exception` oder `Caused by`; erwartet sind nur Meldungen zu
  Profil, Skin, Realms, Telemetrie, Erzähler und HTTP 401 eines Offline-Clients, die Warnung von Vanilla, wenn beide Clients
  in derselben Sekunde gehen und die Meldung „… left the game“ an einen schon geschlossenen Kanal geht
  (`StacklessClosedChannelException`; `PROPWASH_MP_ALLOW=<regex>` ergänzt
  die Liste), Zeilen eines Stapels (`at …`, `... n more`) zählen zu ihrer Kopfzeile. Kopien der Logs und die drei Gradle-Ausgaben liegen im Protokoll-Ordner. Exit 0 bestanden, 1 Befund, 2 Aufruf oder
  Aufbau, 75 Zeit abgelaufen.
- **Nur Pilot A** (normaler Beitrittstest nach §3 mit einem Server- und einem Client-Slot, ohne geschützten Fall):
  Server mit `PROPWASH_JOINTEST_HOLD=900 PROPWASH_JOINTEST_MULTI=solo`, Client mit `PROPWASH_JOINTEST_MULTI=solo`,
  `-PpropwashDevName=PwPilotA` und `PROPWASH_JOINTEST_EXPECT='joined|multi role=pilot solo|multi race finished|multi
  ghost shown|multi leaderboard ok'`. Der Server baut den Kurs, sobald Pilot A da ist, der Pilot wartet 1 s statt auf
  den Zuschauer. So lassen sich Rennen, Geist und Bestätigung der Runden am Server prüfen, ohne drei Prozesse. Die
  Zeile `multi assist lap=… now=…` nennt die Gründe, falls eine Runde als Flughilfe zählt; die Gründe, aus denen der
  Server eine Meldung verwirft, stehen mit `"debugLogging": true` in `runs/smoke-server/config/propwash/core.json`
  im Server-Log.
- **Nur Pilot B** (ebenso ohne geschützten Fall): Server mit `PROPWASH_JOINTEST_HOLD=900
  PROPWASH_JOINTEST_MULTI=watchsolo`, Client mit `PROPWASH_JOINTEST_MULTI=watchsolo`, `-PpropwashDevName=PwPilotB` und
  `PROPWASH_JOINTEST_EXPECT='joined|multi role=watcher solo|multi watched pilot|multi watcher marker=drawn|multi
  watcher racehud=drawn|multi watcher osd=pilot|multi watcher sticks=moving|multi watcher gate=lit|multi operator
  ok|multi operator released|multi cleared'`. Der Server baut den Kurs für Pilot B, macht ihn zum Operator,
  meldet die Drohne als Sender von `PwPilotA` an (`SpectateServer.simulateTransmitter`) und lässt sie mit 12 m/s um den
  Kurs fliegen (Nase um die Kamera-Neigung nach unten, die FPV-Kamera blickt waagerecht); dabei schickt er Zuschauern
  jeden Tick Renn-Zustand (nächstes Gate wechselt alle 2 s) und bewegte Sticks und beim Start das OSD-Layout des
  Piloten (`dev.SimulatedPilotFeed`: Rennen in Orange mit Sticks und Flugbahn-Marker, mph, Mode 1). Pilot B sucht den
  Kanal, schaut mit Marker zu und beendet nach 200 Ticks im Zuschauen mit mindestens 20 Blöcken Kameraweg das
  Zuschauen; sobald Renn-HUD, Layout des Piloten und Sticks (Roll mindestens 0,5 Weg in 40 Ticks mit Feed) da sind und
  das aktive Gate höchstens 30° neben der Blickrichtung der Kamera liegt (spätestens nach 6 s), macht er
  `jointest/<label>-pilot-osd.png`. Dann folgt der Operator am Gimbal
  wie oben, nur startet der Server die Gimbal-Drohne für B selbst ins autonome Halten (es gibt keinen Pilot A); B sieht
  deshalb auch das Label `HOLD`. B schickt als Operator `/propwash clear` und endet, sobald Zuschauen und Operator-Rolle
  vorbei sind. So lassen sich
  Marker beim Zuschauen, der Operator am Gimbal einer Drohne im autonomen Halten und das Aufräumen ohne drei Prozesse
  prüfen.
- **Gemessen am 18.10.** (bei voller Last durch andere Läufe): `solo` auf Fabric viermal und auf NeoForge einmal,
  `watchsolo` auf beiden Loadern je einmal. Pilot A fliegt die drei Runden in 18,0 s (beste 6,0 s, Server bestätigt
  jede Runde, Geist ab Runde 2), Pilot B schaut 200 Ticks zu, die Kamera wandert 108 bis 110 Blöcke mit; Server und
  Client je Lauf etwa 3 min. Einmal startete ein Client, der erst 70 s nach dem Kurs fertig geladen hatte, mit
  Failsafe, und der Server zählte alle Runden als Flughilfe (`RESCUE`, im `debug.log` des Servers „Rennmeldung …
  verworfen“); seitdem wartet der Pilot vor dem Start, bis keine Flughilfe mehr anliegt. Der geschützte Fall wurde in
  95 min nicht frei (16:33 bis 18:07): höchstens 8,4 GB frei bei nötigen 2 + 2 + 5 GB, zuletzt alle Slots leer und
  7,5 GB frei. Mehrmals bekam das Skript Slot 4, aber nicht Slot 1, dreimal Slot 4 und Slot 1, aber nicht Slot 2;
  jedes Mal gab es alle wieder frei und wartete weiter. Der Lauf gehört deshalb in einen reservierten Block nach einem
  Wellenabschluss oder in die Nacht.
- Ohne Slot-Skript (Rechner von Beitragenden): `--no-slots`; dann laufen die drei Prozesse ohne Belegung und Wächter.
- **Gemessen am 19.10.** (`watchsolo`, Fabric): 200 Ticks zugeschaut, Kamera 110,5 Blöcke mitgewandert, Marker in allen
  200 Ticks gezeichnet, Renn-Elemente in keinem; `/propwash clear` von `PwPilotB` entfernte 2 Drohnen (die simulierte
  und die Prüf-Drohne des Server-Rauchtests am Spawn) und meldete den Zuschauer ab, Server-Rauchtest PASS, beide Logs
  ohne Fehler. Der volle Lauf mit drei Prozessen und dem Aufräumen durch B nach der Runde von A steht noch aus (der
  geschützte Fall war nicht freigegeben).
- **Gemessen am 20.10.** (`watchsolo`, Fabric, mit Operator am Gimbal): 200 Ticks zugeschaut, Kamera 104 bis 110 Blöcke
  mitgewandert, Marker in 199 bis 200 Ticks; die Gimbal-Drohne startete ins Halten (`STARTED`), B wurde nach 4 s
  gebunden (3,6 m über dem Boden), der Gimbal schwenkte bis zum Anschlag 120° und neigte bis −90°, 62 Eingaben, Label
  `HOLD`; `/propwash clear` entfernte 3 Drohnen, der autonome Flug endete mit `STOPPED`, danach 0 Operator-Bindungen
  und 0 Add-on-Flüge, `FlightState#operatorAxis(0)` am Server höchstens 1,00. Die Operator-Ansicht endete aber erst
  201 Ticks nach dem Entfernen (`view_ended=201 ticks`), also über die Zeitgrenze des Zuschauens (`CONNECT_TIMEOUT_TICKS`,
  danach der Hinweis „Kein Bild: Kanal nicht erreichbar“) statt sofort mit dem Ende der Rolle: `OperatorClient#onTick`
  gibt die Rolle schon ab, wenn das Bild weg ist, und das spätere `OperatorRoleS2C` mit `NONE` erreicht
  `OperatorView#end` nicht mehr. Deshalb fehlt `multi operator released` (verlangt ist das Ende der Ansicht
  höchstens 40 Ticks nach dem Entfernen); das ist ein Befund an `operator-gimbal`. Der volle Lauf mit drei Prozessen
  steht weiter aus (Freigabe `mp3-ok` fehlte).
- **Gemessen am 20.10. nach der Korrektur der Operator-Ansicht** (`watchsolo`, Fabric und NeoForge): `multi operator
  released`, die Ansicht endet 0 Ticks nach dem Entfernen (`view_ended=0 ticks`), Kamera 110,0 bzw. 110,1 Blöcke
  mitgewandert, Marker in 199 Ticks. Ein Zwischenlauf auf NeoForge zeigte eine zweite Reihenfolge: `OperatorRoleS2C` mit
  `NONE` kam im selben Tick wie das Entfernen an, `SpectateSession#drone` war für die entfernte Drohne schon `null`, und
  `OperatorView#end` erkannte die eigene Sitzung nicht (wieder 201 Ticks). Seitdem erkennt `OperatorView#end` sie auch
  an der Drohne des Ziels; beide Reihenfolgen prüft der Selbsttest `addon-api-17: B9` ohne zweiten Prozess.
- **Renn-HUD beim Zuschauen** (Befund RC2-4, `docs/spectate-feed.md`): Vorher füllte
  `osd.client.LiveOsdSource#fillSpectating` keine Renndaten (`multi watcher racehud=missing ticks=0` in allen
  `watchsolo`-Läufen vom 19. und 20.10.). Jetzt schickt der Pilot Renn-Zustand, Live-Werte und OSD-Layout an seine
  Zuschauer; die Zeilen `multi watcher racehud=`, `osd=`, `sticks=`, `gate=` und `shot=` nennen Ticks, Layout,
  Stick-Weg, empfangene Pakete und das Bild.

### Gemischte Loader (Server-Loader ungleich Client-Loader)

`docs/design/cross-loader-join.md`. Der Loader vorn gilt für den Server, `--client-loader` für die Clients. Add-ons
laufen hier keine mit (mit Add-ons prüft der Release-Lauf: ihre Jars in `fabric/runs/smoke-server/mods` und
`neoforge/runs/smoke-client/mods` beider Arbeitsbäume, den zweiten mit `--tree-b` vorher anlegen).

- **Renndrohne verschwunden:** Der Server schreibt dann `Mehrspieler: Drohne <uuid> weg: Grund <RemovalReason> bei x y z,
  Drohnen-Item liegt da (zerbrochen)|keins, Mobs in 8 m: …`. Als „durch /propwash clear von PwPilotB“ gilt sie nur, wenn
  sie ohne Drohnen-Item verschwand, sonst heißt die Zeile `entfernt ohne /propwash clear`.
  `PROPWASH_JOINTEST_MULTI_MOBS=<n>` am Skript setzt n Schleime um den Schwebepunkt, sobald beide Piloten da sind (wie
  natürlich gespawnte Mobs vor dem Aufbau); mit Mob Defense in den Mod-Ordnern zeigt das den Befund V-RC4-3 ohne den
  Aufbau ohne Mobs: `Drohnen-Item liegt da (zerbrochen), Mobs in 8 m: {slime=4}`.
- **Menüs mit Zusatzdaten** (Befund V-RC4-1): `PROPWASH_JOINTEST_MENUS=1` an Server und Client, Erwartung
  `joined|menus=ok`. `dev.MenusJoinCheck` stellt 3 s nach dem Beitritt Drohnen im Kreis (2 Blöcke) um den Spieler: eine
  5″ mit Beispiel-Werfer, je Add-on-Namensraum eine 5″ mit dem ersten Zubehör mit Fächern und eine mit dem ersten Rahmen
  (höchstens 8). `dev.client.MenusJoinProbe` öffnet jede mit Schleichen + Rechtsklick (`service=DroneServiceScreen`) und,
  wenn sie Fächer hat, über den Knopf „Zubehör“ (`accessory=AccessoryScreen slots=<n>`). Server Fabric, Client NeoForge:

  ```sh
  PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_MENUS=1 JAVA_HOME=… $S ./gradlew :fabric:runServerSmoke
  PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=menus-cross PROPWASH_JOINTEST_EXPECT='joined|menus=ok' \
    PROPWASH_JOINTEST_MENUS=1 JAVA_HOME=… $S ./gradlew :neoforge:runClientSmoke
  ```

- **Fabric-Server, NeoForge-Clients:** derselbe Ablauf wie oben, Server und beide Clients zusätzlich mit
  `PROPWASH_JOINTEST_CROSSLOADER=1`. `dev.CrossLoaderJoinCheck` schreibt für jede Drohne ihre Daten (Teile, Zubehör, Lack,
  Tune, Inhalt) als `[Beitrittstest] gemischt: Drohne <uuid> Daten <prüfsumme> (…)` ins Server-Log,
  `dev.client.CrossLoaderJoinProbe` schreibt am Client `crossloader drone <uuid> <prüfsumme>` ins Ergebnis, das Skript
  vergleicht beide. Damit fällt eine falsche Zuordnung der Entity-Data-Serializer auf. Danach läuft der Schritt
  „Beitrittstest Einzelspieler nach dem Fabric-Server“: Register und Serializer-Nummern gleich wie vor dem Beitritt,
  Einzelspieler-Welt anlegen, Drohne setzen, der Client sieht sie. Das Log der Clients muss
  `Register an den Fabric-Server angeglichen` enthalten.
- **NeoForge-Server, Fabric-Client:** Pilot A (Fabric) tritt mit `PROPWASH_JOINTEST_EXPECT='this server runs neoforge|!joined'`
  bei und muss abgelehnt werden (`mixed-reject.txt`, Server-Log `PwPilotA … lost connection: This server runs NeoForge …`).
  Der Server muss danach weiterlaufen, und Pilot B tritt aus dem zweiten Arbeitsbaum mit dem Loader des Servers bei
  (`mixed-control.txt`, `joined`). Die Clients laufen nacheinander in Slot 1, das Skript belegt hier nur Slot 4 und Slot 1.
- **Gleicher Loader:** ohne `--client-loader` wie bisher.

Das Skript lässt die Zeile `[Netty Local IO #n/ERROR] … Exception caught in connection` mit
`java.nio.channels.ClosedChannelException` gelten: Sie kommt vom integrierten Server, wenn der Einzelspieler-Schritt die
Welt direkt nach der Drohne verlässt, nicht vom dedizierten Server.

Gemessen am 09.11. (ohne Add-ons, nur das Beispiel-Add-on): Fabric-Server mit zwei NeoForge-Clients PASS in 107 s, drei
Runden ohne Befund, Bestenliste bei beiden gleich, je Client 9 Register und 6 Entity-Data-Serializer angeglichen, 3 Drohnen
mit denselben Daten wie am Server, danach Einzelspieler mit Drohne; NeoForge-Server mit Fabric-Client PASS in 73 s.
Vorher war der Ablauf mit drei Instanzen nie ganz grün (siehe Pilot B oben). Ohne Rücksetzen im Client-Tick blieben die
Mod-Serializer nach dem Verlassen auf den Nummern von Fabric (`[44, 45, 46]` statt `[256, 257, 258]`).

JUnit: `join.LocalJoinInfoFilterTest`, `join.JoinTextTest`, `network.fabricsync.FabricRegistrySyncPayloadTest`
(`tools/fastcheck.sh test`) und `fabric.fabricsync.FabricWireFormatTest`, `fabric.platform.FabricClientOptionalTest`
(`./gradlew :fabric:test`, läuft in `./gradlew build`, nicht in `fastcheck`: braucht die Fabric API im Klassenpfad).

### Altwelt-Umstieg (`migration:`)

```sh
JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=migration:' \
    -PpropwashFixtureWorld=<ordner mit world.zip und expected.json> -PpropwashDevName=PropwashFixture
JAVA_HOME=… <slot-skript> ./gradlew :neoforge:runClientSelftest '-PpropwashSelftestOnly=migration:'   # ohne Testwelt
```

- `-PpropwashFixtureWorld=<pfad>` wird in `runClientSelftest` beider Loader zur System-Property
  `propwash.selftest.fixtureWorld` (relativ zur Wurzel des Repositorys). Erlaubt sind ein Ordner im Format der
  Testwelten (`world.zip`, `expected.json`, `config/propwash/*.json`, `client/ghosts/…`), eine Welt als Zip oder als
  Ordner; die Erwartungsdatei liegt daneben (`<name>.json`, `<name>.expected.json` oder `expected.json`).
- Die Testwelten schreibt je alter Stand ein eigener Schreib-Schritt gegen den Code dieses Stands (Strang
  `world-fixtures`, Gruppe `fixture:` auf einem Wegwerf-Zweig ab dem Tag); sie liegen außerhalb des Repositorys. Der
  Spieler muss derselbe sein wie beim Schreiben (`owner` in der Erwartung, hier `PropwashFixture`), sonst fehlen
  Inventar, Endertruhe und Hinweise; der Schritt nennt dann den passenden `-PpropwashDevName`. Umfang (`source.json`
  je Stand): 0.5.2 voll (Design §6.4, mit Teilen aus Just More Parts 1.1.1), 0.5.0 kleiner (ohne Add-ons, Zonen,
  Homepoint, Beschichtung und manuelle VTX-Leistung), 0.4.0 nur Drohnen, Gates und ein Kurs: 10 Drohnen aus 5 Presets
  (benannt, lackiert, Tuning, Zubehör, Verschleiß, Funke gebunden, ohne Akku), 7 Gates (Leiter, Doppel-Gate, zwei
  einzelne), ein Kurs mit Bestenliste, Archiv und zwei Rekord-Geistern, dazu die Client-Configs von 0.4.0 (0.4.0 kennt
  keine Kursnamen, Rundenzahl, frei gedrehten Gates und Welt-Regeln).
- Ohne Property erzeugt „migration: Testwelt im Test erzeugen …“ eine kleine Welt (vier Drohnen, eine davon benannt und
  lackiert, drei Gates als Kurs, ein gestapeltes Gate, geänderte Welt-Regeln, ein Geräteprofil mit Schaltern bis
  `COMMAND_8`) und schreibt Erwartung und Configs nach `runs/selftest-client/migration/generated/`. Das prüft nur den
  Ablauf, nicht den Umstieg.
- Ablauf: Welt-Kopie nach `saves/<Ordner aus der Erwartung>` (der Ordnername bleibt, damit persönliche Geister passen),
  öffnen, alle Werte vergleichen, neue Standards prüfen, speichern und verlassen, neu laden, erneut vergleichen –
  gegen die Erwartung und gegen den ersten Stand.
- **Werte.** Der Schritt erfasst dieselben Abschnitte wie die Erwartung mit dem heutigen Code (`worldRules.values`,
  `drones`, `gates`, `courses` samt Bestenliste, Archiv und Rekord-Geistern, `courseRegistry`, `zones`, `inventory`,
  `chest`, `owner`, `world.folder`, `world.levelName`) und verlangt jeden erwarteten Wert (neue Schlüssel dürfen dazu
  kommen). Listen mit `uuid`, `slot`, `id` oder `frame` werden über diesen Schlüssel zugeordnet, wenn er eindeutig ist,
  sonst nach Stelle; Item-SNBT wird als NBT verglichen. Toleranz 1e-4 (relativ), Positionen, `yRot` und
  `Orientation` 0,05. Nicht verglichen: Zeitstempel, Versionen, `disk`, `files`, SNBT und `geometry` der Entities
  (die Propwash-Schlüssel stehen in `propwashNbt`), `role`, `shareCodes` (prüft der nächste Schritt) und alles mit Teilen
  aus Just More Parts, wenn das Add-on nicht geladen ist (mit Hinweis „nicht geprüft“). Das Aussehen der Rekord-Geister
  (`recordGhosts[].look`) ist der Text des Records; ein leerer Kreativ-Bauplan (`, creative=CreativeSpec[base={}, values={}]`,
  in Ständen vor 0.5.0 nicht vorhanden) zählt dabei als derselbe Bau, ein gefüllter als Abweichung. Ausgaben:
  `migration/actual-1.json`, `actual-2.json`, `diff-1.txt`, `diff-2.txt`.
- **Codes der Drohnen als PW3** (`migration: Codes der Drohnen als PW3 exportieren …`, gemessen beim ersten Vergleich):
  Jede Drohne der Erwartung wird wie mit `/propwash code export` exportiert (`ShareService.encode` aus dem Item der
  Drohne, Ebenen Teile, Lack, Tuning, Name, Add-on-Infos an): ein PW3-Code für den Chat, der sich mit den installierten
  Tabellen ohne unbekannte Teile liest und genau diese Drohne ergibt. Stehen in der Erwartung alte Codes
  (`shareCodes.pw2`, `pw1`, ab der Testwelt 0.5.2 von JMP 1.1.1 geschrieben), rechnet `ShareConvert.toPw3` jeden um; die
  Umrechnung muss dieselben Teile wie die Drohne in der Welt ergeben, und der Export mit den Ebenen des alten Codes muss
  derselbe Text sein. Weil die Umrechnung nur vom alten Code abhängt, gilt das auch für die Codes, die JMP 1.1.1
  geschrieben hat (Design §6.4). Drohnen mit Teilen aus Just More Parts ohne das Add-on: „nicht geprüft“. Ausgabe:
  `migration/sharecodes.txt` (Abweichungen, je Drohne der PW3-Code und die umgerechneten alten Codes).
- **Neue Standards.** `allow_freecam` aus, und zwar dreifach: der Standard der Regel (`WorldRuleSpecs.ALLOW_FREECAM`)
  ist 0, die umgestiegene Welt hat 0 (in Welten vor 0.6.0 gibt es den Schlüssel nicht, die Erwartung nennt ihn nicht),
  und beim Client steht die Regel aus: `ClientWorldRules` kennt sie, und ein Start der Freecam (`FreecamSession#tryStart`,
  wie F6) wird mit `RULE_OFF` abgelehnt. Nach dem Neuladen vergleicht der zweite Vergleich die Regel mit dem ersten
  Stand (0). Fehlt die Regel, ist das eine Abweichung. Gemessen am 20.10. mit der Testwelt 0.5.2 auf Fabric und NeoForge:
  `allow_freecam=0 (Standard 0, in der alten Welt nicht gesetzt), Client: Regel aus, Freecam-Start abgelehnt mit
  RULE_OFF`, je 20 von 922 Schritten bestanden. Flugbahn-Marker im umgestiegenen OSD-Layout aus; Kamerawinkel jeder Drohne
  wie vorher; Geräteprofil mit dem heutigen Code gelesen: jeder belegte Schalter hat dasselbe Bit, dieselbe Quelle und
  schaltet seine Funktion an und wieder aus (gemeinsame Achse für Angle und Horizon, invertierte Taste), die Maske aller
  belegten Funktionen ist `functionMask` (als `long`, also auch über Bit 31) und das höchste Bit stimmt; dazu jede
  Client-Config der Testwelt mit Migration und Prüfung des heutigen Codes gelesen und gegen die alte Datei verglichen
  (Versionsnummern ausgenommen), eigenes OSD-Layout erhalten (nur, wenn die Erwartung es unter `checks.osd` nennt; den
  Marker prüft der Schritt an jeder gelesenen `osd.json`). Was nicht geprüft werden kann (keine Client-Configs, keine
  `osd.json` oder kein Geräteprofil neben der Testwelt), steht als „offen“ in `migration/defaults.txt` und im Log; das
  gilt nicht als bestanden für den Release (V7).
- **Ältere Stände (vor 0.5.2).** Was 0.5.2 beim Laden bewusst umzieht, erwartet der Vergleich in der neuen Form und
  nennt es unter „bewusste Umzüge“: Schalter `race_restart` heißt `reset` (`DeviceProfile.RENAMED`), `mouse.invertY`
  wird `invertPitch`, `flashbackDensity` wird `flashbackSimplify` (`Simplification.sliderOf`), und im OSD-Layout vor
  Version 4 bleiben die Positionen geparkter Elemente auf ihrem Grundplatz ungeprüft (Umzug auf die freien Plätze der
  Vorlage). Aus 0.5.0 stammen: die Physikfelder in `drone.json` (`propWash`, `groundEffect`, `batterySag`,
  `infiniteBattery`, `crashDisarm`, `propStrikes`, `wind`, `turbulence`, `hardwareLimits`, `crashTolerance`,
  `crashDamage`, `crashDamageStrength`, `gravity`) sind Welt-Regeln, `partWear` in `server.json` ist die Gamerule
  `crash_damage`, `memoryMb` in `action_cam.json` entfällt (Puffer nach Arbeitsspeicher); die alten Werte gelten nicht
  mehr und fehlen nach dem Umstieg. `visualScale` 1,75 (alter Standard) in `drone_render.json` vor Config-Version 2 ist
  danach 1,0 mit `sizeNotice` an; eigene Werte und Configs ab Version 2 bleiben. Die Schalter-Bits sind erst ab 0.5.2 fest: für ältere Stände verlangt die Prüfung dieselbe Belegung über den
  Namen und rechnet Maske und höchstes Bit mit der heutigen Nummerierung. Nicht geprüft (mit Hinweis) werden Felder aus
  dem Format des alten Stands ohne Wert (`vtxChannel` = NaN in 0.5.0) und das abgeleitete `preset`, wenn der Bau selbst
  gleich geblieben ist (der Racer bekam nach 0.5.0 andere Props).
- **Runde durch das unterste gestapelte Gate** (nach dem zweiten Vergleich, damit die neue Runde die Bestenliste der
  Erwartung nicht stört): Der Schritt nimmt aus der Erwartung den ersten Kurs mit einem Gate-Stapel (`stack` mit mehr
  als einem Gate, das unterste im Kurs), im Fixture 0.5.2 Kurs 1 mit Leiter und Doppel-Gate, unten auf Beinen die Gates 0
  und 3. Server: diese Gates haben `base=LEGS` und Öffnung ab dem Boden, die Kursdaten kein Flag `legs`. Client: der
  Anbieter `ClientGateOpenings` meldet genau diese Gates mit Beinen; ein `LapTracker` fliegt die Runde mit den Gates auf
  Beinen in halber Rahmenhöhe (`y + frame/2`, wo früher der untere Balken war) und zählt Start, alle Gates und die Runde,
  ohne Anbieter dagegen keine Runde. Würde die Gerade zum nächsten Gate ein Gate rückwärts oder ein anderes Gate
  vorwärts kreuzen (etwa vom letzten zurück zum ersten Gate, wenn der Kurs wie in der erzeugten Welt eine Reihe ist),
  fliegt der Weg seitlich am Kurs vorbei, sonst meldete der Tracker zu Recht `WRONG_WAY`. Danach meldet der Server
  eine Runde mit denselben Durchflügen (`RaceServer`, Start- und Rundenmeldung wie vom Client): angenommen, `lapsOf`
  des Spielers steigt um eins. Mit geänderten Welt-Regeln (erzeugte Welt: Schwerkraft 130 %, Wind) ist es eine Runde
  mit Flughilfe; sie zählt mit Eintrag unter den Runden mit Flughilfe oder, bei `save_assisted_laps` aus, ohne
  Speichern (Gründe im Log).
- **Für den Rückweg** setzt „migration: RX-Blöcke für den Rückweg nach 0.5.2 setzen“ zwei RX-Blöcke drei Blöcke neben
  den Spieler (der erste an ihn gebunden, Kanal Gas); nach dem Verlassen schreibt der letzte Schritt
  `migration/rueckweg/world.zip` (die umgestiegene, gespeicherte Welt), `config/propwash/*.json` (jede Client-Config der
  Testwelt mit dem heutigen Code gelesen und so gespeichert, wie 0.6.0 sie schreibt) und `back.json` (Ordner, Spieler,
  Positionen der RX-Blöcke, belegte Schalter des Geräteprofils).
- **Log ohne Fehler** (letzter Schritt, Design §6.4): Ab dem ersten Schritt der Gruppe („Testwelt im Test erzeugen“,
  bei einer Teilauswahl ab „Welt-Kopie und Erwartungsdatei bereitstellen“) hängt `dev.client.MigrationLog` einen
  Log4j-Abgriff an die Wurzel der Log-Konfiguration; er sieht alle Logger von Client und integriertem Server. Der
  Schritt nimmt ihn wieder ab und verlangt: keine Zeile mit ERROR oder FATAL und keine Warnung mit Ausnahme. Nicht
  gezählt werden die eigenen Ergebniszeilen des Selbsttests („[Selbsttest] FAIL …“, „PROPWASH SELFTEST …“; ein
  geworfener Schritt zählt), die Meldungen des Entwicklungs-Clients ohne Konto (Benutzer-Eigenschaften, Realms,
  Telemetrie, Sprachausgabe; dieselbe Liste wie in `tools/mp_dedicated_test.sh`) und, wenn Just More Parts nicht
  geladen ist, Zeilen mit `justmoreparts:` (Rezeptbuch des Spielers der Testwelt 0.5.2, „Tried to load unrecognized
  recipe …“). Damit der Abgriff nicht blind besteht, muss er Zeilen vom Server-Thread und von Propwash gesehen haben.
  Fehler vor der Gruppe (Start des Clients, etwa „Sharecode-Tabellen: …“ zum Beispiel-Add-on der Entwicklungsumgebung)
  gehören nicht zum Umstieg und liegen außerhalb. Ausgabe: `migration/log.txt` (unerwartet, Umgebung ohne Konto, ohne
  Just More Parts, je mit Thread, Stufe, Logger und Ausnahme).
- **Gemessen am 20.10.** (Fabric und NeoForge mit denselben Zahlen, je 14 von 14 Schritten der Gruppe bestanden):
  Testwelt 0.4.0 1047 Werte vor und nach dem Neuladen, 0 Abweichungen (2 nicht geprüft: abgeleitetes `preset`), die
  Umzüge aus 0.5.0 unter „bewusste Umzüge“; 0.5.0 2314 Werte; 0.5.2 mit Just More Parts 1.1.1 4147 Werte; 0.5.2 ohne
  das Add-on 2564 Werte (32 nicht geprüft); erzeugte Welt 504 Werte, jeweils 0 Abweichungen. „Log ohne Fehler“: 93 bis
  131 Zeilen im Abgriff, davon 48 bis 76 vom Server-Thread, 0 unerwartet; ohne Just More Parts stehen bei 0.5.2 die
  286 Zeilen „Tried to load unrecognized recipe: … justmoreparts:…“ unter „ohne Just More Parts“. Gegenprobe (Fabric):
  eine Kopie der Testwelt 0.4.0 mit vier beschädigten Chunks in `region/r.0.0.mca` (Kennung der Kompression 127)
  besteht alle Vergleiche, der Schritt „Log ohne Fehler“ endet aber mit FAIL und nennt 16 Zeilen („Failed to read chunk
  [0, 0]“ mit `EOFException` als Warnung des IO-Workers, „Failed to load chunk 0,0“ als Fehler des Servers).

#### Am dedizierten Server mit einem beitretenden Piloten

```sh
# Server (Slot 4 oder 5) im Hintergrund, Port aus „Starting Minecraft server on …:<port>“
PROPWASH_JOINTEST_HOLD=600 PROPWASH_JOINTEST_MULTI=migration PROPWASH_JOINTEST_FIXTURE=<ordner mit world.zip und expected.json> \
    JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
# Pilot mit dem Namen aus der Erwartung
PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=migration PROPWASH_JOINTEST_MULTI=migration \
    PROPWASH_JOINTEST_EXPECT='joined|migration server world=ok|migration server freecam=off|migration server player=ok|migration server legs=ok|migration client legs=true|migration client freecam=rule_off|migration lap counted|migration server lap=accepted|migration server saved=yes' \
    JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke -PpropwashDevName=PropwashFixture
# danach jointest.stop in fabric/runs/smoke-server/ legen
```

- `dev.MigrationJoinCheck` (über `PROPWASH_JOINTEST_MULTI=migration`, angemeldet in `MultiJoinCheck`): Beim Laden des
  Mods, vor der Welt, packt der Server die Testwelt nach `runs/smoke-server/<level-name>` (Standard `world`, das
  `prepareServerSmoke` vorher leert). Im ersten Tick (das Kursregister lädt erst mit dem Start-Ereignis des Rennmoduls)
  erfasst er die Welt-Daten (Regeln, Kurse samt Bestenlisten, Archiv und Geistern, Zonen, Truhe), bevor die Prüfungen
  des Server-Rauchtests laufen, lädt die Chunks der Drohnen und Gates und
  vergleicht dann alles außer `owner`, `inventory` und `world.folder` mit der Erwartung (`migration/server-world-diff.txt`,
  dazu `allow_freecam` aus wie im Selbsttest, als `freecam=off` im Papier). Der Pilot prüft nach dem Beitritt, bevor er
  fliegt, beim Client: Regel bekannt und aus, Start der Freecam mit `RULE_OFF` abgelehnt (`migration client
  freecam=rule_off`). Gemessen am 20.10. (Fabric, Testwelt 0.5.2): `freecam=off` am Server, `freecam=rule_off` beim
  Piloten, Runde in 11,6 s gezählt, gespeichert. Sobald der Spieler aus der Erwartung da ist, vergleicht er Besitzer, Inventar, Ausrüstung und
  Endertruhe (`server-player-diff.txt`), prüft die Gates auf Beinen, stellt eine 5″-Drohne vor Gate 0 des Stapel-Kurses,
  gibt dem Piloten Kreativ-Brille und Funke und startet FPV. Für die Runde setzt er die Physik-Regeln der Welt
  (`PhysicsRules.SPECS`, im Fixture 0.5.2 Schwerkraft 130 %) auf Standard, damit sie ohne Flughilfe zählt, und stellt sie
  danach zurück. Der Pilot (`MultiJoinProbe`, Rolle `migration`) fliegt den Kurs mit 6 m/s, die Gates auf Beinen in
  halber Rahmenhöhe; der Server zählt die Runde (`lapsOf` steigt, eine Runde mit Flughilfe steht als `assist=`). 10 s
  später speichert der Server und schreibt `migration/after/world.zip` und `expected.json` (Stand nach der Runde).
- Der Server-Rauchtest selbst prüft beim Start nur bis „Drohnen-Tags“: in der Testwelt 0.5.2 scheitert dort das
  Platzieren der Prüf-Drohne über Spawn (`DroneTagChecks`, Ursache offen), danach fehlt „[Server-Rauchtest] gestartet“
  und `runServerSmoke` endet mit FAIL. Für den Umstieg maßgeblich sind die Zeilen „[Beitrittstest] Migration: …“ und
  das Ergebnis des Piloten; der Pilot wartet daher auf „Migration: Welt am dedizierten Server“ statt auf „gestartet“.
- Die Ergebnisse gibt der Server dem Piloten als benanntes Papier in Platz 9 (`propwash-migration world=ok values=…
  player=ok course=1 bottom=[0,3] legs=ok lap=accepted saved=yes`), der Pilot schreibt sie als `migration server …`.
  Ein Papier aus dem gespeicherten Inventar (beim Neuladen) zählt erst, wenn der Server das neue ohne `lap=` gelegt hat.
- **Neu laden:** derselbe Lauf mit `PROPWASH_JOINTEST_FIXTURE=<arbeitsbaum>/fabric/runs/smoke-server/migration/after`
  startet den Server mit der gespeicherten Welt und vergleicht gegen den Stand nach der Runde (der Pilot fliegt dabei
  noch eine Runde).

- **Gemessen am 19.10.** (Fabric, Testwelt 0.5.2): Welt 2421 Werte, 0 Abweichungen (29 nicht geprüft, Teile aus Just
  More Parts), Spieler 142 Werte, 0 Abweichungen, Gates 0 und 3 von Kurs 1 auf Beinen, Runde in 11,6 s vom Client und vom
  Server gezählt, gespeichert; neu geladen: Welt 2456 Werte, Spieler 111 Werte, je 0 Abweichungen, Runde 2 → 3 ohne
  Flughilfe. Die Fehlerzeilen im Server-Log sind nur „Tried to load unrecognized recipe: justmoreparts:…“ (Rezeptbuch
  des Spielers ohne das Add-on).

#### Rückweg nach 0.5.2

Die umgestiegene Welt in 0.5.2 öffnen (niedrige Priorität, Design §6.4): 0.5.2 läuft aus einem Wegwerf-Arbeitsbaum des
Tags `v0.5.2` mit dessen eigenem Gradle über das Slot-Skript (`git worktree add --detach <pfad> v0.5.2`, danach
`git worktree remove --force <pfad>`).

1. `migration/rueckweg/world.zip` nach `fabric/runs/server/world` des Wegwerf-Baums entpacken (ohne den ersten
   Ordner im Zip), `eula.txt` mit `eula=true`, `server.properties` mit `online-mode=false`, `white-list=false`, eigenem
   `server-port`, `enable-rcon=true`, `rcon.port`, `rcon.password`.
2. Server: `JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServer` im Wegwerf-Baum, warten auf „Done (“.
3. Über RCON: `forceload add` für den Bereich der Testwelt, dann `execute if block <x> <y> <z> minecraft:air` für jede
   Position aus `back.json` (RX-Blöcke sind Luft), `execute if entity @e[type=propwash:drone]` und
   `@e[type=propwash:gate]` (Drohnen und Gates geladen).
4. Client 0.5.2: `config/propwash/*.json` aus `migration/rueckweg/` nach `fabric/runs/smoke-client/config/propwash/`,
   dann `PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=rueckweg PROPWASH_JOINTEST_EXPECT=joined
   PROPWASH_DEV_EXAMPLE=off … ./gradlew :fabric:runClientSmoke -PpropwashDevName=PropwashFixture`. Danach steht das
   Geräteprofil mit denselben Schaltern in `input.json` (0.5.2 hat es gelesen und nicht ersetzt), das Log hat keine
   Fehler zu den Configs.
5. `stop` über RCON, Log des Servers auf Fehler und Ausnahmen prüfen.

Gemessen am 19.10. mit der umgestiegenen Testwelt 0.5.2: der Server 0.5.2 lädt die Welt ohne Absturz, beide
RX-Positionen sind Luft (`Test passed`, darunter Gras), 36 Drohnen und 23 Gates geladen; im Log stehen nur die
erwarteten Zeilen zum unbekannten Block `propwash:rx_block` („Recoverable errors when loading section“, „Skipping block
entity with invalid type“). Der Client 0.5.2 liest die von 0.6.0 gespeicherte `input.json` und schreibt sie inhaltlich
gleich zurück (Geräteprofil mit allen zehn Schaltern). Der Beitritt des Clients scheiterte zuerst an der Whitelist des
Servers (`whitelist off` über RCON) und bekam danach keinen freien Slot mehr.

### Drehwelt (`drehwelt:`)

```sh
JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=drehwelt:'
# für die Drehwelt zum Drehen mit der X-Class aus Just More Parts: das Jar vorher nach fabric/runs/selftest-client/mods
```

- Legt nach dem Verlassen der Selbsttest-Welt die flache Welt `Propwash Selftest Drehwelt` an (Kreativ, Mittag, kein
  Wetter, keine Mobs), baut die Plätze der Shotliste entlang einer Straße am Spawn, schaltet die Welt-Regel
  `allow_freecam` ein, speichert und packt die Welt nach `runs/selftest-client/drehwelt/propwash-drehwelt.zip`; die
  Liste der Plätze mit Koordinaten und Stand steht in `drehwelt/drehwelt.txt` (auch im Zip). Jeder Platz hat am Rand
  zur Straße ein Schild mit Kennung, Stand und Koordinaten; am Start stehen zwei Übersichtsschilder.
- **Selbsttest-Optionen.** Direkt nach dem Anlegen verlangt „drehwelt: Selbsttest-Optionen greifen“ die Werte aus
  `prepareRuns`: Grafik-Voreinstellung `custom`, Sichtweite 6 und Simulationsdistanz 5 im Client und am integrierten
  Server. Ohne `graphicsPreset:"custom"` legt Minecraft 26.3 in einem frischen Run-Ordner „fancy“ an und setzt damit
  bei jedem Start 16/12; die Prüfung des RX-Tors lief dann in Strang-, Prüfer- und Bisect-Arbeitsbäumen anders als im
  Integrations-Arbeitsbaum. Deshalb schreibt `prepareRuns` die Voreinstellung mit (Selbsttest- und
  Rauchtest-Instanzen). Ein FAIL nennt Voreinstellung und beide Wertepaare, etwa „fancy, Client 16/12, Server 16/12“.
- Plätze (Norden, x von −144 nach Osten): R2 RX-Tor mit Lampenreihe (Kolbentor mit Inverter, 14 Lampen unter Redstone,
  der RX-Block als Quelle), R3 Dive-Spot (Turm 40 Blöcke, großes Gate am Fuß), R4 Slime- und Wolle-Parcours,
  R5 Holzplatte vor einer Tür und Stolperdraht mit Lampe auf dem Pfosten des Hakens, R6 Werkbank mit 5″ Freestyle und
  X-Class (Akku gesteckt ohne Stecker und Kabel), R11 Halle mit sechs kleinen Gates als Kurs und Whoop am Start,
  Gate-Leiter (gestapelte Gates aus `GateStackSelfTests.build`, unten auf Beinen). Süden: R1 Schlucht mit Turm (112
  Blöcke, Höhle am Ende, Turm 39 Blöcke hoch am Westende), R8 Hitze-Strecke (Rundkurs 172 × 60, am Start ein 7″ Long
  Range mit LiPo 6S 1300, der bei Dauervollgas heiß wird – „MOTOR OVERHEAT“ nach etwa 100 s,
  `docs/design/realism/motor-heat.md` B2b –, daneben der 7″ mit Li-Ion zum Vergleich, der kühl bleibt).
- **X-Class (R6):** genommen wird die Vorlage mit Rahmen-Rolle `x_class` und dem größten Radstand aus Just More Parts
  (mit dem Jar 1.1.1 `justmoreparts:xclass13h_8s`, 13″ H-Rahmen), sonst die aus dem Beispiel-Add-on
  (`propwash_example:xclass`, nur in Entwicklungsläufen).
  Das Schild am Platz nennt Vorlage und Herkunft. Die Drehwelt zum Drehen wird mit Just More Parts gebaut, sonst fehlen
  beim Öffnen ohne Beispiel-Add-on die Teile der X-Class.
- **Keine leeren Plätze.** Jeder Platz braucht seine Features (RX-Block, Slime und Wolle, Druckplatte und Stolperdraht,
  Flugbahn-Marker, Akku gesteckt ohne Stecker und Kabel, Durchflug unten, Freecam, Motor-Hitze, X-Class); fehlt eines,
  ist „drehwelt: Plätze bauen“ ein FAIL mit dem Namen des Features. `PROPWASH_DREHWELT_ALL=1` baut trotzdem alles (ohne
  RX-Block mit Hebel), nur für Läufe auf Ständen ohne diese Features.
- Geprüft wird jeder Platz in der Welt selbst (danach wieder im Ausgangszustand): RX-Tor mit einer Test-Funke (an den
  Spieler gebunden, Kanal Gas): Gas 100 % → Tor offen, 14 Lampen; Gas 50 % → Tor offen, weniger Lampen; Gas 0 % → Tor
  zu, keine Lampe; danach ungebunden, damit beim Drehen die eigene Funke bindet. Vollgas gibt die Test-Funke erst, wenn
  das Tor in Ruhe zu ist (Fackel an, beide Klebekolben ausgefahren, Kopf vorn, Stein im Tor, Platz tickt mit geladenen
  Entities, keine Lampe an), und Gas 0 % endet wieder in dieser Lage. Grund: Mit der Simulationsdistanz 5 der
  Selbsttest-Optionen tickt R2 nach dem Bauen nicht mehr, die Kolben fahren erst aus, wenn der Spieler am Tor steht. Kam
  das Signal mitten im Ausfahren, ließen die Klebekolben den noch fahrenden Stein im Tor liegen („zu lampen=14“). Ein
  Abbruch nennt deshalb Fackel, Kolben, Kopf und Tor je Reihe. Slime-Boden und -Wand federn
  (`SurfaceMaterials` → Rückprall 0,8), Wolle und Heuballen fangen ab (0,05). Eine Drohne auf der Holzplatte öffnet die
  Tür, eine im Stolperdraht schaltet Haken und Lampe; ohne Drohne Tür zu, Lampe aus. Auf der Hitze-Strecke stehen der
  7″ mit LiPo und der mit Li-Ion. An der Werkbank stehen 5″ und X-Class.
- **Freecam (R1).** „drehwelt: Freecam-Regel an“ setzt `allow_freecam` am Server auf 1 und wartet, bis der Client die
  Regel kennt und die Freecam starten dürfte (`FreecamSession#denialFromMenu` = `NONE`). Danach stellt sich der Spieler
  auf den Turm der Schlucht (Blick nach Osten über die Schlucht), startet die Freecam als 5″ wie mit F6, fliegt 4 s
  scharf geschaltet mit leichtem Nicken nach vorn, wechselt im Flug auf Whoop (wie im K-Menü) und beendet sie nach
  1,5 s; verlangt sind mindestens 2 Blöcke Flug, der Wechsel auf Whoop und „Freecam“ im OSD-Namen. Die vorher
  eingestellte Freecam-Größe bleibt danach erhalten.
- **Nach dem Zip** öffnet die Gruppe die gespeicherte Drehwelt noch einmal: Regel `allow_freecam` am Server 1, beim
  Client an und Start erlaubt, Whoop-Kurs (sechs Gates) und Kurs der Gate-Leiter im Kursregister. Damit ist in jeder
  ausgelieferten Drehwelt die Freecam-Regel an. Dann verlässt sie die Welt.
- **Gemessen am 20.10.:** Fabric mit Just More Parts 1.1.1 in `mods` 21 von 921 Schritten bestanden (X-Class
  `justmoreparts:xclass13h_8s`, Freecam als 5″ 16,6 Blöcke in 4 s vom Turm aus, Wechsel auf Whoop im Flug, nach dem
  Neuladen Regel an, Kurs 1 mit 6 und Kurs 2 mit 5 Gates), Zip 1,2 MB; NeoForge ohne Just More Parts ebenso 21 von 921
  (X-Class `propwash_example:xclass`).
- Die Gruppe wird im Wellenabschluss W2 neu gebaut; die Drehwelt ist nur zum Drehen da, ein Release braucht sie nicht.

### Klon-Probe (Tests nach dem Klon, Add-on-API 1.7 Teil E)

Grundlage: `docs/test-paths.md` (Pfade über `TestPaths`, Golden-Dateien über den Klassenpfad, zwei Spalten für
aarch64 und x86_64, `LoginShell.parse` ohne Betriebssystem-Abhängigkeit). Die Probe zeigt, dass ein frischer Klon des
öffentlichen Stands ohne lokale Umgebung grün ist.

1. Öffentlichen Stand prüfen (schnell, ohne Bauen): `python3 tools/public_export.py --check-only --no-build`
   (Pflichtdateien, keine internen Pfade, Sperrliste, keine Zitate, keine Datei über 95 MB).
2. Export in einen leeren Ordner und dort JUnit ohne Umgebung:

```sh
python3 tools/public_export.py --target /tmp/pw-export/export --no-build
cd /tmp/pw-export/export && env -i HOME=/tmp/pw-export/home GRADLE_USER_HOME=/tmp/pw-export/gradle PATH=/usr/bin:/bin \
  JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :common:test --no-daemon
```

   Grün heißt: kein Test braucht Dateien außerhalb des Repositorys, Programme aus der Login-Shell oder gebaute Dateien
   aus einem anderen Arbeitsbaum.
3. Fremdes Arbeitsverzeichnis: dieselben Testklassen mit der JUnit-Konsole aus `/tmp` (ohne Gradle) – grün für alle
   Tests, die `TestPaths` nutzen; Tests mit eigener Pfadsuche brauchen noch die Wurzel oder `common/` als
   Arbeitsverzeichnis (`docs/test-paths.md` §4).
4. Bit-Vergleich auf x86_64: dieselben Testklassen mit einem x86_64-JDK 25 unter Rosetta 2 (`org.gradle.java.home` auf
   das x86-JDK). Weichen Golden-Werte ab, gilt die zweite Spalte aus `docs/test-paths.md` §2.1.

JUnit dazu: `tools/fastcheck.sh test --select-class de.lardtom.propwash.testutil.TestPathsTest` und
`--select-class de.lardtom.propwash.video.record.external.FfmpegDetectionTest`.

## 8. Abdeckung des Prüfplans 0.6.0

Abdeckungs-Aufträge der Prüfung von 0.6.0 (Prüfplan in `docs/design/0.6.0/README.md` §6): je Auftrag die Schritte, ihre
Aufrufe und die gemessenen Läufe. Sie ändern keine Funktion, nur Testklassen mit „Cov“ im Namen, und laufen im vollen
Lauf mit.

### Abdeckung cov-flight

Prüfplan 0.6.0, Teil 1.1–1.7 (Flugbahn-Marker, Kamerawinkel im K-Menü, Slime und Wolle, Druckplatten, Landung, Motor-Hitze,
Signal-Gedächtnis) und Teil 4.4 Nr. 22 (JMP-1.1.1-Drohne fliegt). Alles ändert keine Funktion, nur Testklassen mit „Cov“ im
Namen.

```sh
tools/fastcheck.sh test --select-class de.lardtom.propwash.osd.CovFlightPathMarkerTest \
    --select-class de.lardtom.propwash.osd.CovFlightPathMarkerLangTest --select-class de.lardtom.propwash.drone.CovCameraMountTest \
    --select-class de.lardtom.propwash.sim.CovSurfaceResponseTest --select-class de.lardtom.propwash.sim.CovThermalScenarioTest \
    --select-class de.lardtom.propwash.video.link.CovTerrainColumnsTest
PROPWASH_CALIBRATION_ADDON=<entpacktes JMP-Jar> PROPWASH_CALIBRATION_OUT=<ordner> tools/fastcheck.sh test \
    --select-method 'de.lardtom.propwash.sim.CovThermalScenarioTest#bigCoptersWithJustMorePartsDerateInTheirBands'
```

- **JUnit**: `CovFlightPathMarkerTest` (Uptilt 0/30/45° mit der Freestyle-Kamera, flach und mit Fisheye 80/100 %, 4:3 und
  16:9, auch im Vorwärtsflug mit 20° Neigung; steiler Sinkflug weit unten, fast senkrecht gestrichelt am unteren Rand;
  randnahe Ziele bei Stärke 0/80/100 % und beiden Formaten gegen `OsdProjection` und die Abbildung von `FisheyeLens`),
  `CovFlightPathMarkerLangTest` (Name und Beschreibung in `common/src/lang/fpm`), `CovCameraMountTest` (Grenzen je
  `FrameRole` als Tabelle, 90° begrenzt, Kreativ-Drohne 0–80°), `CovSurfaceResponseTest` (Slime-Decke mit 8 m/s federt nach
  unten, keine Ereignisse), `CovTerrainColumnsTest` (Strecke über die Kuppe eines gemerkten Rückens wie geladen unter
  1 dB), `CovThermalScenarioTest` (Schweben mit fast Vollgas: Schwerkraft = höchster Anteil von S/G ab 90 % in Schritten
  von 1 %, bei dem die Drohne 60 s die Höhe hält; Cinewhoop regelt nach 60–120 s im ESC ab und sinkt; 7″ mit
  6S-1300-LiPo startet wie ein Spieler vom Boden bei 0,9 × S/G, höchstens der Regelgrenze 1000 %, und meldet nach 90–150 s
  `MOTOR_HOT` ohne Abregelung im ESC; mit Tag
  `addon-daten` die Zeitbänder für X-Class 10″, 7″ 8S und X-Class 12″ aus den Daten von Just More Parts, Ausgabe
  `cov_flight_thermal.txt` im Ausgabeordner, die Kalibrierungsdateien bleiben unberührt).
- **Selbsttest** (beide Loader, gefiltert mit dem Filter unten, rund 6 min): `osd.client.CovFlightPathMarkerSelfTests`
  (Editor mit ⌖, nicht verschiebbar, Farbe, Größe, Linien-Deckkraft; Drift, enge Kurve und Powerloop ohne Sprünge und
  Flackern; Fisheye 0/80/100 % mit 4:3 und 16:9 gegen das gerenderte Objektiv; kein Marker im Sichtflug-HUD; Vorschau
  im Editor 201 Ticks lang leicht unter der Mitte),
  `video.client.CovCameraAngleMenuSelfTests` („Standardwerte“ im Tab Video, Kreativ-Drohne mit Drohnen-Editor),
  `drone.client.CovSurfaceSelfTests` (Teppich, Bett, Schneeschicht, Woll-Platte und Woll-Stufe in keinem Tag und mit
  20 m/s wie Stein; Datenpaket `cov/cov-flight/moss_cushion` mit Moos in `drone_cushion`, Wand und Fall mit 15 m/s),
  `interact.client.CovDroneTriggerSelfTests` (abgestürzte Drohne hält die Holzplatte 40 Ticks, schwere Wägeplatte und
  Komparator 1, schneller Überflug, 20 geparkte Drohnen; Datenpakete `cov/cov-flight/triggers_empty` und `triggers_plates`:
  leere Liste löst nach `/reload` nichts aus, nur `#minecraft:pressure_plates` drückt Platten, aber keinen Stolperdraht),
  `drone.client.CovLandingSelfTests` (vier Landefälle bei 30 FPS
  und unbegrenzt), `drone.client.CovMotorHeatSelfTests` (ESC- und VTX-Warnung abwechselnd lesbar, ohne Überschneidung),
  `video.client.CovSignalMemorySelfTests` (FPV 350 Blöcke weg mit angehefteter Sicht und Statuszeile im Tab Server;
  Repeater-Strecke hinter gemerkter Wand mindestens 20 dB, unendliche Reichweite ohne Abschattung; Ruckler beim
  Teleport-Flug über 2000 Blöcke mit und ohne Gedächtnis), `drone.client.CovJmpFlightSelfTests` (nur mit einem Jar von Just
  More Parts in `mods/`, sonst übersprungen: Preset `justmoreparts:xclass13_8s` steigt und landet).

  ```sh
  '-PpropwashSelftestOnly=osd: Flugbahn-Marker im Editor,osd: Flugbahn-Marker – Vorschau,osd: Flugbahn-Marker – Drift,osd: Flugbahn-Marker – Fisheye,osd: Flugbahn-Marker – nicht im Sichtflug,video: Kamerawinkel im K-Menü – gehaltene Drohne: „Standardwerte“,video: Kamerawinkel im K-Menü – Kreativ,drone: Wolle – nicht dabei,drone: Wolle – Datenpaket,drone: Wolle – Abdeckung,interact: abgestürzte,interact: Drohne auf schwerer,interact: schneller,interact: 20 geparkte,interact: Datenpaket,interact: Abdeckung,drone: Landung bei 30 FPS,drone: Motor-Hitze – zusammen,signal: Reichweite über,signal: Repeater-Strecke,signal: Ruckler,signal: Abdeckung,drone: JMP-Drohne'
  ```

- **Bildschirmfotos** (nur Fabric, `-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos: Abdeckung'`):
  `cov_osd_flight_path_marker_edge_home` (rückwärts, gestrichelter Ring am Rand mit Home-Pfeil), `cov_osd_flight_path_marker_dive`
  (Sturzflug), `cov_motor_heat_osd_esc_vtx_01`/`_02` (ESC- und VTX-Warnung). Die Dateien gehören nicht ins Repository.
- **Server-Rauchtest** bei jedem Lauf (`rescue.CovSurfaceServerChecks.tickWithPlate`, Zeile „Heimflug landet auf Wolle,
  Slime und Druckplatte: …“): autonomer Heimflug ohne Pilot zum Homepoint auf Wolle, auf Slime und auf einer
  Holzdruckplatte vor einer Tür, Landung ohne Absturz, Prop-Schlag und Verschleiß; 25 Ticks nach der Landung ist die
  Platte gedrückt und die Tür offen.
- **Beitrittstests** (§3, `PROPWASH_JOINTEST_HOLD=1800`, Schalter an beiden Prozessen):
  `PROPWASH_JOINTEST_COV_SURFACE=1` (Server `dev.CovSurfaceJoinCheck` baut Slime-, Heu- und Woll-Felder; Client
  `drone.client.CovSurfaceJoinProbe` fällt aus 10 m auf Slime und fliegt gegen die Wollwand), Erwartung
  `joined|surface_slime=bounce|surface_wool=caught|surface_crashes=0`; `PROPWASH_JOINTEST_COV_LANDING=1` (zehn sanfte
  senkrechte Landungen, Server loggt die Neigung jeder Ruhelage), Erwartung `joined|landing_upright=10/10|landing_snapback=0`;
  `PROPWASH_JOINTEST_COV_HEAT=1` (Server setzt Schwerkraft 0,8 × S/G des Cinewhoops und Akkulaufzeit unendlich, Client
  heizt den ESC auf 147,5 °C), Erwartung `joined|heat_osd=esc_overheat|heat_limit=reduced|heat_sink=yes`.
- **LAN mit zwei Clients**: `PROPWASH_MC_LAUNCH=<Slot-Skript> tools/remote_cov_camangle_test.sh [fabric|neoforge]` –
  Gastgeber (`PROPWASH_LANHOST_COV_CAMANGLE=1`, `-PpropwashSelftestOnly=lan-camangle`) fliegt mit 40° und stellt 20° ein,
  der Beobachter (`PROPWASH_JOINTEST_COV_CAMANGLE=1`) schaut per F7 zu; Erwartung `joined|camangle_spectate=follows`.
- **ui-scale**: Fall „Konfigurator / osd mit Tooltip der Flugbahn-Marker-Beschreibung“
  (`osd.client.CovFlightPathMarkerUiScaleCase`, gezielt `'-PpropwashSelftestOnly=ui-scale:'
  '-PpropwashUiScaleCases=Konfigurator / osd mit Tooltip'`): Tooltip über dem Listeneintrag zeigt die ganze Beschreibung,
  ohne Abschneiden und im Fenster.
- Gesperrt bis zum Fix (Klassen fertig, ohne Anmeldung): `CovSurfaceSelfTests.ceilingStep` (Slime-Decke).
- **Absturz ohne Disarm** (Befund CRASHARM-1, `docs/design/ground-contact.md` §8): JUnit `sim.ArmedCrashRestTest`
  (`tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.ArmedCrashRestTest`, rund 15 s; mit
  `PROPWASH_CALIBRATION_OUT` schreibt er `crasharm_idle.txt` mit Weg, Schub und I-Anteil je Fall) und Selbsttest
  `'-PpropwashSelftestOnly=drone: Absturz ohne Disarm'` (`drone.client.CovCrashRestSelfTests`, beide Loader, rund 1 min;
  Bilder mit `-PpropwashScreenshots=true` und zusätzlich `bildschirmfotos: drone: Absturz ohne Disarm` im Filter).
  Vor dem Fix auf Fabric: Acro fuhr 22,5 Blöcke mit 25 m/s davon, Angle hielt auf dem Rücken 0,46 × Gewicht Schub mit
  I-Anteil 125 ‰; danach beide Loader 0,00–0,02 Blöcke, 0,05 × Gewicht (Leerlauf), I-Anteil 0.
- **Absturz ohne Disarm mit Welt-Regeln aus** (Befund CRASHARM2-1, `docs/design/ground-contact.md` §8.6;
  `crash_disarm`, `crash_damage`, `prop_strikes` aus, `battery_scale` 0): JUnit `sim.ArmedCrashRulesOffTest`
  (`tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.ArmedCrashRulesOffTest`, rund 20 s; fünf Presets, Acro
  und Angle, Airmode an und aus, Gas 0 und 30 %, Boden weich und hart, Wand weich und hart; mit
  `PROPWASH_CALIBRATION_OUT` die Tabellen `crasharm_rules_off_idle.txt`, beim harten Aufprall auf den Boden mit dem Weg
  unscharf, und `crasharm_rules_off_throttle.txt`). Ohne Fix rot: Props auf der Seite am Boden behielten 100 % ihres
  Schubs, danach 16 %. Selbsttest `'-PpropwashSelftestOnly=drone: Absturz ohne Disarm mit Welt-Regeln aus'`
  (`drone.client.CovCrashRulesSelfTests`, beide Loader, rund 35 s) und Beitrittstest mit demselben Ablauf
  (`drone.client.CovCrashRulesRun`, Server `dev.CovCrashRulesJoinCheck` setzt die Regeln, baut ein Feld mit Steinwand,
  startet eine Freestyle in FPV und protokolliert die Lage am Server und jede Rücksetzung; danach Regeln zurück):

  ```sh
  cd <WT> && PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_COV_CRASHRULES=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
  cd <WT> && PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=crashrules-fabric PROPWASH_JOINTEST_COV_CRASHRULES=1 \
    PROPWASH_JOINTEST_EXPECT='joined|crasharm_rules=applied|crasharm_rest=5/5|crasharm_resets=0' \
    JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
  ```

  Danach `jointest.stop` in `runs/smoke-server/`; dasselbe mit `:neoforge:`. Fünf Abstürze (Acro 75° schräg, Angle auf
  dem Rücken, Acro hart mit 8 m/s auf den Rücken, Acro gegen die Wand, Acro auf die Seite mit 30 % Gas), je 5 s: Weg ab
  1 s höchstens 1 Block, Schub höchstens 0,2 × Gewicht und I-Anteil höchstens 50 ‰ bei Gas 0, weiter scharf, kein
  Rücksetzen durch den Server. Gemessen am 03.11. auf Fabric und NeoForge, Einzelspieler und Server gleich: 0,00–0,01
  Blöcke, 0,05–0,06 × Gewicht, I-Anteil 0, der harte Aufprall als Absturz erkannt und scharf geblieben, 0 Rücksetzer.
- **Rutscher und Abpraller ohne Disarm** (Befunde AUTO52-1 und AUTO52-2, `docs/design/ground-contact.md` §8.7): JUnit
  `sim.ArmedCrashSkidAndBounceTest` (`tools/fastcheck.sh test --select-class
  de.lardtom.propwash.sim.ArmedCrashSkidAndBounceTest`, rund 20 s; mit `PROPWASH_CALIBRATION_OUT` schreibt er
  `crasharm_skid.txt` und `crasharm_bounce.txt` mit Weg, Höhe, Schubzeit und I-Anteil je Fall, scharf und unscharf)
  vergleicht 540 Rutscher und 1 440 Abpraller mit Gas 0 mit demselben Absturz, der beim Aufprall unscharf wird. Im
  Selbsttest `drone: Absturz ohne Disarm` laufen dazu drei Fälle je zweimal (erst unscharf ab dem Aufprall als Vergleich,
  dann scharf): Cinewhoop Acro 15° rutschend mit 12 m/s, Freestyle Angle 135° und Acro 90° mit 8 m/s vor; die Fälle
  setzen die Bauart am Server, „Bauart zurücksetzen“ stellt sie wieder her. Vor dem Fix auf Fabric 6,19 statt 4,74,
  18,25 statt 0,70 und 11,82 statt 1,29 Blöcke, danach 4,70 statt 4,54, 0,18 statt 0,38 und 0,30 statt 0,21 (NeoForge
  4,86 statt 4,58, 0,37 statt 0,61 und 0,19 statt 0,67), jeweils ohne Schub und mit I-Anteil 0.
- **Fast aufrecht an der Wand ohne Disarm** (Befund CRASHARM3-1, `docs/design/ground-contact.md` §8.8): JUnit
  `sim.ArmedCrashWallSweepTest` (`tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.ArmedCrashWallSweepTest`,
  rund 50 s; je Welt-Regel-Satz 3 600 Wandaufpralle mit Gas 0: alle fünf Presets, Acro, Angle und Horizon, 0,5–6 m/s,
  0–24° Neigung, Achse in 45°-Schritten; Sätze: alle vier Crash-Regeln aus, nur `crash_disarm` aus, dazu `prop_strikes`
  aus, dazu `crash_damage` aus; mit `PROPWASH_CALIBRATION_OUT` je Satz `crasharm_wall_<satz>.txt`). Erlaubt: am Block ab
  0,3 s nach jedem Aufprall (Kontaktgedächtnis 0,3 s) Schub im Mittel über 0,05 s höchstens 0,2 × Gewicht und I-Anteil
  höchstens 50 ‰, dasselbe in den letzten 2 s, Weg ab 1 s höchstens 1 Block. Dazu zwei Wächter: Abheben nach 1,5 s scharf
  im Leerlauf am Boden wie nach 0,05 s, kurzes Antippen der Wand (unter 0,1 s Kontakt) schwächt den Regler nicht. Ohne Fix
  rot: 2, 7, 3 und 7 von 3 600 (bis 2,77 × Gewicht), mit dem Regler vor AUTO52 580 von 3 600. Selbsttest
  `'-PpropwashSelftestOnly=drone: Absturz ohne Disarm an der Wand'` (`drone.client.CovCrashWallSelfTests`, beide Loader,
  rund 1 min) und Beitrittstest mit demselben Ablauf (`drone.client.CovCrashWallRun`, Server `dev.CovCrashWallJoinCheck`
  setzt die Welt-Regeln des Besitzers, baut eine 15 Blöcke lange Steinwand und startet einen Racer in FPV):

  ```sh
  cd <WT> && PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_COV_CRASHWALL=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
  cd <WT> && PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=crashwall-fabric PROPWASH_JOINTEST_COV_CRASHWALL=1 \
    PROPWASH_JOINTEST_EXPECT='joined|crashwall_rules=applied|crashwall_build=race_5|crashwall_rest=10/10|crashwall_resets=0' \
    JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
  ```

  Danach `jointest.stop` in `fabric/runs/smoke-server/`; dasselbe mit `:neoforge:` (`neoforge/runs/smoke-server/`). Zehn
  Racer-Wandfälle (Acro 4 m/s 3° Achse 60°, 4 m/s 12° Achse 15°, 0,5 m/s 18° Achse 75°, 1 m/s 15° Achse 45°, 0,5 m/s
  24° und 1 m/s 18° Achse 270°, 5 m/s 9° Achse 270°, 6 m/s 12° Achse 300°, 3 m/s 9° Achse 255°, Horizon 5 m/s 18° Achse
  225°), je 5 s ab dem ersten Kontakt: nahe am Block ab 0,3 s nach dem letzten Aufprall höchstens 0,2 × Gewicht und
  I-Anteil höchstens 50 ‰, in den letzten 2 s im Mittel höchstens 0,2 × Gewicht, Weg ab 1 s höchstens 1 Block, weiter
  scharf, kein Rücksetzen durch den Server. Gemessen am 04.11.: Selbsttest Fabric und NeoForge sowie Beitrittstest
  Fabric und NeoForge je 10 von 10, am Block höchstens 0,10 × Gewicht, I-Anteil höchstens 3 ‰, Weg höchstens 0,05 Blöcke,
  0 Rücksetzer. Auf dem Stand vor dem Fix (mit AUTO52) waren die zehn Fälle im Spiel schon grün (höchstens 0,13 ×
  Gewicht); den Rest zeigt nur der JUnit-Test.

### Abdeckung cov-world

Prüfplan 0.6.0, Teil 2.1 (Akku gesteckt), 2.2 (LED im Dunkeln, nur Bilder), 2.3 (Zonen), 2.6 (`/propwash clear`),
2.8–2.10 (RX-Block), 2.11 (Server und Mehrspieler) und 2.13 (Zuschauen mit GPS Rescue). Nur Testklassen mit „Cov“ im
Namen; keine Funktion geändert.

```sh
JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest \
    '-PpropwashSelftestOnly=drone-render: Abdeckung,zonen: Abdeckung,rx: Abdeckung,server: Abdeckung'
JAVA_HOME=… <slot-skript> ./gradlew :neoforge:runClientSelftest \
    '-PpropwashSelftestOnly=drone-render: Abdeckung,zonen: Abdeckung,rx: Abdeckung,server: Abdeckung'
tools/fastcheck.sh test --select-class de.lardtom.propwash.rx.CovRxTextTest \
    --select-class de.lardtom.propwash.server.CovClearTextTest --select-class de.lardtom.propwash.zone.CovZoneTextTest
```

- **Akku gesteckt** (`drone.client.render.CovBatteryPlugSelfTests`, ohne Welt-Drohne bis auf einen kurzen Welt-Teil):
  „drone-render: Abdeckung – Akku gesteckt in allen Darstellungen …“ baut für jede Voreinstellung mit ausgeblendeten Steckern den
  Zustand als Welt-Entity (`DroneVisuals.fillFromEntity` an einer gespawnten Drohne), in Hand/GUI, Item-Rahmen und am
  Boden (`DroneItemRenderer`), in der Werkbank (`DroneVisuals.previewState`), als Ghost (`DroneGhost`) und in der
  Flugschul-Szene (`guide.client.render.CovSceneDroneProbe`) und verlangt jeweils `powerLead.shown`, Masken für
  Akku-Stecker und Stack-Kabel und den `_mounted`-Stack. „… Klick auf den Stack ohne Stecker …“ klickt in sechs
  Drehungen der Lack-Vorschau auf Anker, Oberseite und Seiten des Stacks: in der Standard-Drehung 210° des Lack-Fensters
  wählt der Klick `PaintSlot.STACK`, wo es der Stack mit Stecker (zum Vergleich `powerLead.present` kurz aus) auch tut;
  die übrigen Drehungen stehen im Log. Mit Lack auf dem Stack wechselt der `_mounted`-Stack, die Masken bleiben dieselben.
  „… Stack-Item mit eigenem Stecker …“: GUI, Hand, Item-Rahmen, Boden und Kiste zeichnen für jeden eingebauten Stack das
  Grundmodell (mit Stecker-Element, ohne `pigtail`), nie `_mounted`, keine Zusatzform.
- **Bildschirmfotos** (nur Fabric, `-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos (drone-render): Abdeckung'`):
  `cov_battery_plugged_ghost_freestyle_5`, `cov_battery_plugged_ghost_race_5` (Ghost von hinten, über
  `ClientBus.SUBMIT_WORLD_GEOMETRY` mit `DroneGhost.submit` gezeichnet), `cov_battery_plugged_scene_freestyle_5`,
  `cov_battery_plugged_scene_race_5` (Flugschul-Szene). Iris (§5): `'-PpropwashSelftestOnly=bildschirmfotos: Iris'`
  nimmt mit `drone.client.render.CovBatteryPlugIrisShowcase` zusätzlich `cov_battery_plug_iris_{freestyle,race}_5_{shader,vanilla}`
  (Nahaufnahme am Morgen, Shaderpack an und aus). Die Dateien gehören nicht ins Repository.
- **Zonen** (`zone.client.CovZoneSelfTests`): „zonen: Abdeckung – leere Dimension …“ bringt den Spieler per
  `execute in minecraft:the_nether run tp` in den Nether, öffnet den Tab Zonen und verlangt eine leere Zonenliste,
  den gezeichneten Hinweis `propwash.zone.section.empty` und den Knopf „Neue Zone hier“; danach zurück.
- **`/propwash clear` im Spiel** (`server.client.CovClearSelfTests`, am Ende der Anmeldung, entfernt alle geladenen
  Drohnen): drei abgestellte Drohnen, eine im Nether mit Forceload-Ticket, eine im 640 Blöcke entfernten, entladenen
  Chunk; nach ruhiger Chunk-Zahl `/propwash clear` als Spieler: Rückgabe = geladene Drohnen, Meldung „… (overworld: n,
  the_nether: 1)“ im Chat, keine Drohnen- oder Teile-Items, Log-Zeile „/propwash clear von <Name>: entfernt …“
  (`server.CovLogCapture`, Appender am Propwash-Logger), Chunk-Zahl gleich, die ferne Drohne nach dem Wiederladen noch
  da. Sichtflug ohne Brille: Sitzung endet, kein Sichtanker, „Your drone was removed.“. Ohne Cheats
  (`IntegratedServer#setWorldAllowCommands(false)`): `clear` fehlt im Befehlsbaum des Clients, `/propwash clear` endet mit
  „Incorrect argument …<--[HERE]“, eine Drohne bleibt; danach Cheats zurück.
- **RX-Block** (`rx.client.CovRxSelfTests`, eigener Platz bei Spawn + (1520, −1520), Test-Funke mit 10 Achsen, AUX 9
  und 10 frei): Kreativ-Tab (RX-Block direkt hinter dem Signal-Repeater, Text `propwash.rx.tooltip` in `en_us`/`de_de`),
  Abbau im Überleben mit Eisenspitzhacke (Abbauzeit, genau ein Item ohne Daten, neu gesetzt ungebunden, LED aus), Status
  mit leerer Hand in der Aktionsleiste (gebunden und ungebunden) und Bauen mit Stein in der Hand, Ausgabe an allen Seiten
  (Lampe oben/unten/seitlich, Tür, Kolben, Staub; Lampe hinter Stein bleibt aus), drei Blöcke auf Gas, Roll und
  AUX-Achse 9 angelernt, AUX-Achse 10 im FPV (scharf, Flugmodus und Schalter unverändert), Tastatur außerhalb des Flugs
  ohne Wirkung und im FPV mit Wirkung, simuliertes Gamepad (`ProfileDefaults.gamepad`) lernt Gas an, Reichweite vom
  Körper (Drohne per Sim-Teleport mit 20 m/s 100 Blöcke weg folgt, Körper 30 Blöcke weg LOST), Funke abgemeldet (LOST
  nach etwa 100 Ticks, Fenster „Keine Funke verbunden …“), Nether und zurück (Chunk des Blocks festgehalten, Stärke
  gehalten), Reset, `/propwash clear` und Drohnenwechsel ohne Einfluss. Tooltip des Items
  (`CovRxSelfTests#tooltipStep`, ohne und mit Shift): `propwash.rx.tooltip` steht direkt unter dem Namen (Zeilen mit
  Leerzeichen verbunden), mit Shift folgen Benutzung und „Passt zu“, die breiteste Zeile ist höchstens 312 Punkte breit
  und passt so mit Rahmen in 320 GUI-Punkte.
- **Server-Rauchtest** (`dev.CovWorldServerChecks`, nach `server.ClearServerChecks`, Zeile „Abdeckung cov-world: …“,
  nicht im Beitrittstest): `rx.CovRxServerChecks` – zwei simulierte Spieler (für die Dauer der Prüfung über
  `server.CovPlayerListAccess` in der Spielerliste; NeoForge gibt `getPlayers()` nur lesbar heraus) binden je einen Block
  auf Gas und geben abwechselnd Gas, jeder Block folgt nur seinem Besitzer; `server.CovClearServerChecks` – die Kinder von
  „propwash“, die der Gast benutzen darf (das ist der Befehlsbaum, den sein Client bekommt), ohne `clear`, als Operator mit
  (`getCompletionSuggestions` am Server filtert Literale nicht nach Rechten und steht nur im Log), zwei Besitzer
  mit `pw_owner_…`-Tags, Nether-Drohne mit Forceload („Removed 2 drones (overworld: 1, the_nether: 1).“), Greifer mit
  fünf Bruchstein (genau die Ladung fällt), Drohne im entladenen Chunk (Rückgabe 0, Chunk-Zahl gleich, nach dem
  Wiederladen noch da), ein zweiter Operator bekommt die graue Admin-Zeile, Log-Zeilen mit Namen, Gates, Kurs,
  Bestenliste, RX-Block und Drohnen-Items im Inventar unverändert.
- **Beitrittstests**:
  - `PROPWASH_JOINTEST_COV_RX=1` am Server (`dev.CovRxJoinCheck`, Log `[Beitrittstest] RX-Abdeckung: …`), zwei
    Client-Läufe mit gleichem `-PpropwashDevName`: `PROPWASH_JOINTEST_COV_RX=1` stellt Stärke 10 ein und trennt
    (`joined|cov_rx_set=10`), der Server prüft während der Abwesenheit alle 20 Ticks Stärke 10 und setzt beim
    Wiederbeitritt einen Smaragd- bzw. Redstone-Block; `PROPWASH_JOINTEST_COV_RX=2` erwartet
    `joined|cov_rx_absent_held=ok|cov_rx_rejoin_linked=ok|cov_rx_follow_again=ok` (`rx.client.CovRxJoinProbe`).
  - `PROPWASH_JOINTEST_COV_WATCHRESCUE=1` an beiden Prozessen (`dev.CovWatchRescueJoinCheck`,
    `video.client.CovWatchRescueJoinProbe`): simulierter Sender PwPilotA wie `watchsolo`, der Zuschauer schaut mit
    Analog-Brille zu, nach 6 s löst der Server GPS Rescue aus (Homepoint auf einem Goldblock); Erwartung
    `joined|multi watched rescue`, das Server-Log nennt Landung und Abstand zum Homepoint.
  - `PROPWASH_JOINTEST_COV_VERSIONS=1` am Server 0.6.0 (`dev.CovVersionsJoinCheck`, zählt Beitritte): Propwash 0.5.2 aus
    einem abgelösten Wegwerf-Arbeitsbaum des Tags `v0.5.2` (eigenes Gradle über das Slot-Skript) als Client gegen den
    Server 0.6.0 und als Server gegen den Client 0.6.0, Erwartung `0.5.2|0.6.0|!joined` (Fabric, Übersicht mit beiden
    Versionen und Protokollen); NeoForge zeigt nur die Kanalzeilen mit den Protokollen (`docs/design/join-check.md` §6),
    Erwartung dort `Propwash FPV|version: 16|version: 17|!joined`. Server: `PROPWASH_JOINTEST_HOLD`, Port aus
    „Starting Minecraft server on …“, danach `jointest.stop`; der Wegwerf-Arbeitsbaum wird danach entfernt.
- **LAN-Gast** (`tools/remote_cov_languest_test.sh [fabric|neoforge]`, Muster `remote_trails_test.sh`): Der Gastgeber
  (`server.client.CovLanGuestHost`, nur mit `PROPWASH_LANHOST_COV_GUEST=1`) stellt eine Drohne ab und gibt die Welt mit
  `publishServer(LAN, false, port)` ohne Cheats für Gäste frei; der Gast (`server.client.CovLanGuestJoinProbe`,
  `PROPWASH_JOINTEST_COV_LANGUEST=1`) prüft Tab Welt-Regeln nur lesbar (ohne Regler), `clear` nicht im Befehlsbaum und
  `/propwash clear` abgelehnt; der Gastgeber prüft danach, dass seine Drohne noch steht. Erwartung im Skript
  `joined|cov_languest operator=no|cov_languest world_rules=readonly|cov_languest clear_in_tree=no|cov_languest clear_refused=yes`.

### Abdeckung cov-race

Prüfplan 0.6.0, Teil 2.4 Nr. 31–33 (gestapelte Gates: oberes Gate abbauen, frisch gestapelt, frei skaliert), Teil 2.5
Nr. 42–45 (Gate-Hinweis: Stapeln, Kurs-Elemente, eigene Belegung, anderes Fenster offen) und die Owner-Entscheidungen zum
Rennsystem vom 19.10. (R1–R9, im Prüfplan unter 2.14). Nur Testklassen mit „Cov“ im Namen; keine Funktion geändert. Was
`race-a` und `race-b` schon prüfen (`race: Wertung`, `race-topo:`, `migration:`, JUnit des Pakets `race`), steht hier nicht
noch einmal; die Cov-Schritte schließen die Lücken (zweiter Spieler, Randfälle, Neustart, Bilder).

```sh
tools/fastcheck.sh test --select-class de.lardtom.propwash.race.CovGateLegsScaledTest \
    --select-class de.lardtom.propwash.race.CovRaceScoringTest --select-class de.lardtom.propwash.race.data.CovLegacyCourseLapTest
PROPWASH_FIXTURES=<ordner mit 0.4.0, 0.5.0, 0.5.2> tools/fastcheck.sh test --select-class de.lardtom.propwash.race.data.CovLegacyCourseLapTest
JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=race: Abdeckung'
JAVA_HOME=… <slot-skript> ./gradlew :neoforge:runClientSelftest '-PpropwashSelftestOnly=race: Abdeckung'
```

- **JUnit**: `CovGateLegsScaledTest` (Gate mit eigener Öffnung wie aus dem Gate-Menü, `GatePlacement#edited`, gestapelt:
  unten `LEGS`, Öffnung ab dem Boden über die ganze Breite, oben `STACKED`, Öffnung ab dem Balken; Runde knapp über dem
  Boden am Rand der Öffnung zählt nur mit Beinen; breiteres Gate auf schmalem abgelehnt), `CovRaceScoringTest` (hohe
  Flagge, 7 Blöcke, 2,5 nach unten und 1,5 nach oben: Runde 0,1 Block innerhalb beider Grenzen zählt, 0,1 Block außerhalb
  nicht, ohne Einstellung nur 1 Block nach unten; Slalomstange mit Wertung nach unten; Gate-Stange im Kurs mit Reichweite
  3: Vorbeiflug bis 2,9 zählt, ab 3,1 nicht; Schleife A, B, B rückwärts, A rückwärts: Tafeln „1 · 4←“ und „2 · 3←“,
  HUD-Zähler 2/4, 3/4, 4/4, 1/4 je Durchgang eindeutig, B vorwärts statt rückwärts ist eine falsche Richtung ohne
  Auslassen, `LapVerifier` mit einem Durchflug je Durchgang, Bestenliste aus der Kursdatei mit der geflogenen Zeit,
  Richtungen nach dem Speichern), `CovLegacyCourseLapTest` (nur mit `PROPWASH_FIXTURES`, sonst übersprungen: in jeder
  Testwelt 0.4.0, 0.5.0 und 0.5.2 zählt jeder Kurs eine Runde über die Wegpunkte seiner Elemente; je Testwelt der Kurs mit
  Bestenliste: Prüfsumme wie in `expected.json`, alle erwarteten Einträge vorhanden und nach einer neuen Runde unverändert,
  Layout gleich, nach dem Speichern dieselbe Kurs-ID und Bestenliste).
- **Selbsttest Gate-Stapel** (`race.client.CovGateStackSelfTests`, Platz 1600 Blöcke westlich und südlich): Gate per
  echtem Rechtsklick mit einem Gate-Item auf ein Kurs-Gate gestapelt; ab dem Klick zählt der Schritt die Ticks, bis der
  Server `base=LEGS` und der Client über `ClientGateOpenings` die Beine meldet (stehen im Log), danach Runde in halber
  Rahmenhöhe unten durch beim Client (mit und ohne Anbieter) und am Server. Seit dem Fix zu Befund cov-race-1 gilt die
  strenge Grenze von 5 Ticks ab dem Klick (Prüfplan „spätestens nach einer Viertelsekunde“, angemeldet ist
  `CovGateStackSelfTests#strictFreshStep`; gemessen Server 1–2, Client 2 Ticks). Oberes Gate mit dem Kurs-Werkzeug
  abgebaut: unten höchstens 5 Ticks bis zum Rahmen (Server ab dem Abbau, Client ab dem Verschwinden des oberen Gates),
  danach zählt der Durchflug im unteren Balken nicht mehr.
  Gate auf Öffnung 5 × 4 gestellt (`GateMounting#edit` wie das Gate-Menü) und gestapelt: unten
  Öffnung ab dem Boden, oben ab dem Balken, Runde 0,3 Block innerhalb des Pfostens knapp über dem Boden beim Client und am
  Server.
- **Selbsttest Gate-Hinweis** (`race.client.CovGateHintSelfTests`, eigene Welt-Schlüssel über `GatePlaceHint#worldOverride`,
  Hinweis-Speicher am Ende zurückgestellt): unteres Gate stumm gesetzt, Speicher geleert, zweites Gate per echtem Klick auf
  den oberen Balken gestapelt: Fenster `SHOW`; Rennflagge, Slalomstange, Tauchwürfel und Gate-Stange per echtem Klick: je
  40 Ticks kein Fenster, Hinweis-Speicher und Zähler unverändert; Schleichen auf „G“ gelegt: das Fenster zeichnet „G“ und
  nicht mehr „Left Shift“ (`LayoutProbe#capture`), danach zurückgestellt; Gate gesetzt und gleich das Inventar geöffnet
  (im Kreativmodus ersetzt das Spiel es durch das Kreativ-Inventar): 60 Ticks kein Fenster, nach dem Schließen erscheint
  es; Gegenprobe mit 220 Ticks offenem Inventar (länger als `GatePlaceHint.PENDING_TICKS`): danach kein Fenster, die Welt
  gilt trotzdem als gesehen.
- **Selbsttest Rennsystem** (`race.client.CovRaceSystemSelfTests`, Platz 1700 Blöcke westlich und 1600 südlich, Kurse mit
  dem Kurs-Werkzeug, Server-Runden über `RaceServer` je mit eigenem Zeitfenster): R2 hohe Flagge auf drei Blöcken Säule,
  Wertung per echtem Mausrad im Menü 2,5 nach unten und 1,5 nach oben, Runden 0,1 Block innerhalb und außerhalb beider
  Grenzen beim Client und am Server; R3 Gate-Stange im Kurs (vom Kurs-Werkzeug „vorbei“) mit Slalomstange und Flagge,
  Runde 0,4 innerhalb der Reichweite zählt, 0,6 außerhalb nicht (Server nimmt sie nicht an); R4 Torpfosten ohne Querbalken,
  Freiform-Gate ohne Schleichen abgelehnt, mit Schleichen 3 × 5 Felder; Durchflug unten links, in der Mitte und oben
  rechts zählt, über der Fläche und neben den Pfosten nicht; ohne „Lücken ignorieren“ meldet der Rand offene Kanten; R7
  Schleife mit Klicks von hinten (A, B, B rückwärts, A rückwärts), Tafeln, Kurs-Fenster (Zeilen „… · again like 2 · ←
  backwards“ und „… · again like 1 · ← backwards“ in dieser Reihenfolge, mit Scrollen), HUD-Zähler über
  `ClientRaceTracker#feed` und `RaceState`, falsche Richtung, Bestenliste am Server mit der geflogenen Zeit; R8
  Slalomstange auf einer Ecke und Flagge auf einer Kante des Tauchwürfels, Gate-Stange auf einer Ecke eines Gates: Runde
  um die Stange auf dem Würfel, je eine Kollisionsbox in der Mitte jedes Aufsatzes, Abbau eines Aufsatzes lässt Träger und
  andere Aufsätze stehen; R6 Seite Position mit Schrittweite 1 Block (← Bild↑ ↓), 1/4 (Knöpfe + mit freier Maus, auf das
  Raster der Schrittweite) und 1/16 (↑ → Bild↑), Enter: Gate, Kollisionsboxen, Durchflugfläche und Kursdaten wandern genau
  mit; simulierter Gast ohne Operator-Recht: `NOT_OWNER`, im Abenteuermodus `NOT_ALLOWED`; R3/R6 Entity-Daten gespeichert
  und neu geladen, Kursdatei über `CourseRegistry` stop/start neu gelesen; R1 FPV am Start-Gate, Arm-Taste, Drohne
  12 Blöcke weg, GPS-Rescue (`RescueController#requestToggle`) fliegt zum Homepoint 5 Blöcke vor dem Start-Gate, danach
  Scharfschalten ohne „Start beim Scharfschalten“: nie Aufstellung oder Countdown; R5 `ScoreAreaRenderer` zeichnet die
  Durchflugfläche des Start-Gates im FPV und in der dritten Person (Zähler `framesDrawn`), ohne aktiven Kurs keine.
- **Bildschirmfotos** (nur Fabric, `-PpropwashScreenshots=true '-PpropwashSelftestOnly=race: Abdeckung – Rennsystem – Aufbau,bildschirmfotos: Abdeckung Rennsystem,race: Abdeckung – Rennsystem – aufräumen'`):
  `race_cov_areas_fpv`, `race_cov_areas_third_person`, `race_cov_areas_third_person_plain`, `race_cov_tall_flag`. Mit Iris
  (§5, `config/iris.properties` mit `enableShaders=false`, Filter zusätzlich `bildschirmfotos: Iris Rennsystem`,
  `race.client.CovRaceIrisShowcase`) dieselben Flächen mit und ohne Shaderpack: `race_cov_areas_{fpv,third_person}_{shader,vanilla}`.
  Die Dateien gehören nicht ins Repository.
- **Beitrittstest mit Neustart** (§3, Schalter `PROPWASH_JOINTEST_COV_RACE` an beiden Prozessen, Server
  `dev.CovRaceJoinCheck`, Client `race.client.CovRaceJoinProbe`, `PROPWASH_JOINTEST_HOLD=1800`). Lauf 1 mit `=1`: der
  Server nimmt dem Spieler das Operator-Recht (am Ende zurück), baut 24 Blöcke östlich den Kurs „CovRaceNeustart“
  (Start-Gate, Gate-Stange vom Kurs-Werkzeug zugewiesen, Gate) und für den simulierten zweiten Spieler `PwCovRaceB` einen
  eigenen Kurs; `PwCovRaceB` (nur für den Reset kurz in der Spielerliste, NeoForge schickt sonst Pakete an eine Verbindung
  ohne Kanal) fliegt zum Homepoint vor dem Start-Gate zurück, der echte Client schaut zu und sieht keine Aufstellung und
  keinen Countdown; danach fliegt der Client selbst FPV am Start-Gate: scharf, Reset, erneut scharf ohne Einstellung –
  kein Start, der Server sieht in beiden Fällen keine Aufstellung; mit dem Kurs-Werkzeug versucht der Client, das Gate von
  `PwCovRaceB` zu verschieben (abgelehnt, bleibt stehen), und verschiebt sein eigenes mit → → Bild↑ um −0,5/+0,25; der
  Server entfernt Drohnen anderer Prüfungen am Spawn und speichert. Lauf 2 mit `=2` und `-x prepareServerSmoke` (Welt
  bleibt): der Server findet den Kurs, Gate-Stange im Kurs mit „vorbei“, das verschobene Gate in Kursdatei und Entity an
  der neuen Lage, Kollision am Pfosten, eine Runde des Spielers wird angenommen; der Client zählt die Runde mit dem
  synchronisierten Kurs und den Vorbeiflug neben der Gate-Stange nicht.

  ```sh
  PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_COV_RACE=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
  PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=cov-race-1 PROPWASH_JOINTEST_COV_RACE=1 \
    PROPWASH_JOINTEST_EXPECT='joined|covrace spectator no start|covrace pilot reset no start|covrace pilot rearm no start|covrace server lobby clean|covrace guest refused|covrace nudged|covrace phase1 saved' \
    JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
  # danach jointest.stop in fabric/runs/smoke-server, dann Lauf 2 mit derselben Welt:
  PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_COV_RACE=2 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke -x prepareServerSmoke
  PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=cov-race-2 PROPWASH_JOINTEST_COV_RACE=2 \
    PROPWASH_JOINTEST_EXPECT='joined|covrace restart course ok|covrace restart lap ok' JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
  ```

  Dasselbe mit `:neoforge:`. Zwei echte Clients gleichzeitig braucht der geschützte Lauf (§7, `mp3-ok`). Lauf 2 endet als
  Server-Rauchtest mit „FAIL Drohnen-Tags: Platzieren fehlgeschlagen“ (die Prüfung `drone.DroneTagChecks` setzt auf der
  erhaltenen Welt des ersten Laufs keine Drohne mehr, Befund cov-race-2); Kriterium sind die Zeilen des Beitrittstests.
  Gemessen am 21.10. nach dem Rebase: Fabric und NeoForge je Lauf 1 und Lauf 2 mit allen erwarteten Zeilen.

### Abdeckung cov-pw3

Prüfplan 0.6.0, Teil 3.2–3.8 und 3.12 im Spiel (Sharecode PW3: Export, Import, Befehle, Mehrspieler, Rechte am
Web-Export), Teil 1.2 Nr. 30 (Kamerawinkel im Code), Teil 6.3 Nr. 20 (OSD-Eintrag eines Add-ons im Code) und die
Propwash-Läufe mit Just More Parts aus Teil 4. Nur Testklassen mit „Cov“ im Namen; keine Funktion geändert.

```sh
tools/fastcheck.sh test --select-class de.lardtom.propwash.share.CovPw3ShareTest
JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest \
    '-PpropwashSelftestOnly=share: Abdeckung cov-pw3,drone-render: Akku gesteckt – Akkuwechsel'
```

- **JUnit** `share.CovPw3ShareTest`: OSD-Ebene mit dem Eintrag `mobdefense:dispenser` (Platz 1, 12) liest sich ohne Fehler,
  der Eintrag bleibt beim Empfänger stehen; seit dem Fix zu Befund cov-pw3-1 steht er im Code und gilt auch beim
  Empfänger ohne das Add-on bzw. mit eigenem Platz (`addonOsdEntryTravelsInTheSharecode`); Kamerawinkel reist mit der Ebene Tuning (Deadcat-Vektor P3 35°, eigene
  Drohne 42° mit Tuning, ohne Tuning Rahmenstandard); Code mit dem neuen Motor `justmoreparts:m0802_27200` aus Just More
  Parts 1.2.0, gelesen mit installiertem JMP 1.1.1 ohne eigene Tabelle: gegen den aktuellen Verzeichnis-Abzug `NEWER`,
  „ab 1.2.0 – installiert ist 1.1.1“ (übersprungen, sobald der Abzug den Motor kennt), gegen einen Abzug mit den drei
  neuen Motoren (wie nach dem Abgleich) Name „0802-Motor, 27200 KV“, Ersatz `propwash:m0802_22000`. Gesperrt:
  `newerJmpMotorAfterTheSnapshotUpdateStillNamesBothVersions` (Befund cov-pw3-2).
- **Selbsttest** `share.client.CovShareSelfTests` („share: Abdeckung cov-pw3 – …“, beide Loader, ohne und mit dem Jar von
  Just More Parts 1.2.0 in `<loader>/runs/selftest-client/mods`): Export-Häkchen nach `ShareSettings.reset()` (OSD aus,
  ohne Namen gesperrter Name-Schalter mit `…toggle.name.none`), unfertige Drohne (Akku am Server entnommen: Code leer,
  `propwash.share.export.no_result`, Kopierknopf aus), keine Zeile „Add-on-Infos“ bei Propwash- und JMP-Teilen
  (Gegenprobe mit `propwash_example`), Add-on ohne Teile-Tabelle (Tabelle des Beispiels für den Schritt abgeschaltet,
  Zeile `no_table:propwash_example`), Rundlauf mit allen Ebenen in die Werkbank, „Add-ons in diesem Code“ mit JMP (P2:
  Just More Parts ab 1.0.0, installiert die geladene Version, Symbol `addon_ok`), Einkaufsliste im Überleben (Rahmen und
  zwei Motoren im Inventar, Summe 9, `MISSING_PARTS`, nach dem Einlegen vollständig, ohne Farbstoffe `MISSING_DYES` wie
  die Lack-Kosten, mit Farbstoffen `APPLIED`, Farbstoffe verbraucht), nur einzelne Ebenen (Tab und Werkbank; fehlender
  Tuning-Wert auf Standard, `vtx.channel` und Tasten unverändert), OSD-Layout übernehmen nur lokal (Import-Anfragen am
  Server über `PropwashEvents.BUILD_IMPORTED` gezählt, Gegenprobe zählt 1), Zuschauermodus (Fenster und Tab
  `NOT_ALLOWED`, `/propwash code import` mit `propwash.api.import.not_allowed`, Inventar, Werkbank und Drohne unverändert),
  Prüfsumme falsch mit nur Lack, Tuning und OSD ohne Bestätigung, Befehl `export` (Rückmeldung abgefangen,
  `ClickEvent.CopyToClipboard`, Standard ohne OSD, `parts,paint,name`, `no_drone`, `bad_layer`), Befehl `import` im
  Überleben (ohne Farbstoffe `missing_dyes`, mit Farbstoffen Lack und Tuning, `parts_at_workbench`), genau ein Tab
  „Sharecode“ und ein Werkbank-Knopf, Kamerawinkel (Deadcat-Code, auch ohne JMP).
  OSD-Eintrag eines Add-ons im Code (`addonOsdStep`, seit dem Fix zu Befund cov-pw3-1): `propwash_example:ammo` auf 4, 6
  steht in der OSD-Ebene und kommt nach dem Verschieben auf 20, 3 mit „OSD übernehmen“ wieder auf 4, 6.
  Deadcat-Code ohne passende Teile (seit dem Fix zu Befund cov-pw3-3, ohne JMP sind die Teile unbekannt): im Tab
  Tuning und OSD (`otherLayersStep`) bzw. Lack (`otherLayersPaintStep`) auf eine Propwash-Drohne, im Werkbank-Fenster
  nur Lack und Tuning ohne Ersatzteile (`otherLayersBenchStep`), `/propwash code import` im Überleben
  (`otherLayersCommandStep`); jeweils `APPLIED`, Teile (und Name) der Drohne unverändert. JUnit `ShareImportDataTest`:
  Ebenen ohne Teile tragen dann den Bauplan des Ziels, mit Teilen oder bekanntem Bauplan bleibt der des Codes.
  `drone.client.render.CovJmpBatteryPlugSelfTests` („drone-render: Akku gesteckt – Akkuwechsel in der Werkbank (Abdeckung
  cov-pw3)“, nur mit JMP): JMP-Preset `bash5_6s` (Rahmen `fs_5_bash`) mit lackiertem Akku, Akku am Server BT2.0 → XT60 → XT90 getauscht,
  nach jedem Tausch Stecker und Kabel des neuen Akkus ausgeblendet und Lack-Eintrag unverändert.
- **Server-Rauchtest** `share.CovPw3ServerChecks` (Zeile „Abdeckung cov-pw3: …“): `/propwash webexport` gilt für Stufe 4
  und die Konsole, nicht für Stufe 2 und 3; mit dem Jar von Just More Parts 1.2.0 in `<loader>/runs/smoke-server/mods` jedes
  JMP-Preset nur mit Teilen exportiert und mit `devtest/resources/cov/cov-pw3/jmp-preset-parts.json` verglichen (Umrechnung
  der PW2-Presets aus den Testvektoren; neue Presets ohne Vergleichswert stehen in der Zeile).
- **Beitrittstests** (§3), Server und Client mit demselben Schalter:

  ```sh
  PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_COV_SHARE=1 PROPWASH_JOINTEST_COV_WEBEXPORT=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
  PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=cov-share PROPWASH_JOINTEST_COV_SHARE=1 PROPWASH_JOINTEST_COV_WEBEXPORT=1 \
    PROPWASH_JOINTEST_EXPECT='joined|covwebexport op=no|covwebexport absent|covshare give=absent|covshare present=export,info,convert,import|covshare info answered|covshare convert answered|covshare osd no packet|covshare osd drone unchanged|covshare control counted|!covshare fail' \
    JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
  ```

  `dev.CovShareJoinCheck` nimmt dem Spieler beim Beitritt die Operator-Rechte, zählt seine Import-Anfragen und meldet
  den Befehl `/covpw3 give|imports|bench creative|bench survival` an (nur mit dem Schalter): Drohne „Cov-Share“ in die
  Hand, Zahl der Anfragen als Chatzeile `covpw3 imports=<n>`, Werkbank im gewählten Modus. Der Client
  (`share.client.CovShareJoinProbe`) prüft den Befehlsbaum (`give` fehlt, `export`, `info`, `convert` da), führt
  `info` und `convert` aus und erwartet die Antwort im Chat, übernimmt im Tab das OSD des Deadcat-Codes und vergleicht
  die Zahl der Anfragen vorher und nachher, Gegenprobe „Tuning übernehmen“ zählt eine Anfrage.
  `webexport.client.CovWebexportJoinProbe` prüft, dass `webexport` im Baum des Clients ohne Operator fehlt.
  `PROPWASH_JOINTEST_COV_CHATSHARE=1` (`dev.CovChatShareJoinCheck`, `share.client.CovChatShareJoinProbe`): Der Client
  schickt `/propwash code export` ab, nimmt den Code aus dem Kopier-Klick der eigenen Rückmeldung, sendet ihn per Chat,
  zieht ihn aus der eingehenden Chatzeile (`PW3-[0-9A-Za-z-]+`) und importiert ihn in der Werkbank, im Kreativmodus und im
  Überleben. Erwartung `joined|chatshare same=yes|chatshare click=yes|chatshare applied same_build=yes|chatshare missing_parts`.
  Mit einem Client ist der Empfänger derselbe Spieler; zwei echte Clients nur mit `flags/mp3-ok`.
- **Gemischte JMP-Versionen**: `PROPWASH_MC_SLOTS=<slot-skript> tools/remote_cov_jmpmixed_test.sh <loader> <jar 1.2.0>
  <jar 1.1.1>` (das Skript selbst nicht über das Slot-Skript starten: es holt Server- und Client-Slot einzeln): Server mit 1.2.0 und Client mit 1.1.1, danach Server mit 1.1.1. Seit Propwash 0.6.0
  Just More Parts 1.1.x sperrt, bricht die Seite mit 1.1.1 den Start mit der Sperrmeldung ab, ein Beitritt kommt nie
  zustande; Ergebnis in `build/cov-jmpmixed/`. Die Jars legt das Skript nur in die Run-Ordner und nimmt sie danach wieder
  heraus.

### Abdeckung cov-api

Prüfplan 0.6.0, Teil 6 (Seite „Add-ons“ im Tab „Modi“, Zubehör-Aktionen, OSD-Elemente, Welt-Regeln und Kollision von Add-ons,
Namensraum-Prüfung, Noclip 0.1.x) und Teil 8.10 Nr. 125 (Gamepad, nur der Propwash-Teil). Nur Testklassen mit „Cov“ im
Namen; keine Funktion geändert.

```sh
tools/fastcheck.sh test --select-class de.lardtom.propwash.input.addon.client.CovAccessoryHintTextTest \
    --select-class de.lardtom.propwash.input.client.screen.CovReceiverWizardAddonTest --select-class de.lardtom.propwash.zone.CovZoneAddonRulesTest
JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest \
    '-PpropwashSelftestOnly=addon-api-17: A1 – Abdeckung,addon-api-17: A2 – Abdeckung,addon-api-17: A3 – Abdeckung,addon-api-17: B5 – Abdeckung,input: Abdeckung,guard: Abdeckung,ui-scale: Abdeckung'
```

- **JUnit**: `CovAccessoryHintTextTest` (Zeile des Hinweises beim ersten Flug mit den Texten aus `common/src/lang`, englisch und
  deutsch: unbelegt in Gelb mit „nicht belegt – im Tab „Modi“ …“ und der Taste des Konfigurators, Taste als „Taste …“
  (Maustaste 4, SDL-Nummer 4), Funke allein und „Funke oder Taste“), `CovReceiverWizardAddonTest` (Schrittliste des
  Empfänger-Assistenten bei angemeldeten Add-on-Schaltern und -Achsen: nur eingebaute Flugfunktionen, beginnt mit ARM,
  kein Schlüssel eines Add-ons, ein weiterer Schalter ändert nichts), `CovZoneAddonRulesTest` (Zonen-Felder und Zeilen des
  Zonen-Editors sind genau die Physik-Regeln, Add-on-Ids fallen aus den Zonen-Werten).
- **Selbsttest** (beide Loader):
  - `input.addon.client.CovAddonSwitchSelfTests` („addon-api-17: A1 – Abdeckung: …“): ohne Add-on (`PROPWASH_DEV_EXAMPLE=off`,
    auch mit dem Jar von Just More Parts 1.2.0 in `mods/`) nur die Reiter „Funktionen“ und „Befehle“, keine Seite „Add-ons“,
    ohne Befund der Layout-Prüfung (mit Beispiel-Add-on übersprungen); Hinweis beim ersten Flug mit dem Signallicht Zeile für
    Zeile (Standardtasten, danach Aktion 1 zusätzlich auf Taster 5 der simulierten Funke: „Taster 5 oder Taste …“), englisch
    und deutsch (die deutsche Taste heißt in Minecraft „Num 9“); Zubehör-Aktion 1 ohne Taste und ohne Funke:
    `accessory_hints.json` geleert, genau ein Hinweis mit „nicht belegt – im Tab „Modi“ …“, danach in der Datei gemerkt;
    Zubehör-Aktion 1 auf Maustaste 4 und im Tab „Modi“ per „Schalter umlegen“ auf Taster 5, beide lösen je einmal aus
    (Signallicht +2), die Zeile heißt weiter „… – Blinken“; Gamepad-Profil (`ProfileDefaults.gamepad`) an einer simulierten
    Funke als Gamepad (`SimulatedRadio#asGamepad()`, Belegung über `AddonBindings.apply(…, raw.usesGamepadLayout(…))`,
    ohne Gamepad-Belegung scheitert der Schritt): drei Drücke mit Prellen
    (unten–oben–unten in einem Tick) schalten den Geist an/aus/an, genau drei `ADDON_SWITCH_CHANGED`, ein vorübergehend
    angemeldeter Taster `propwash_example:cov_hold` wirkt nur gehalten.
  - `input.client.CovWizardAddonSelfTests` („input: Abdeckung – Empfänger-Assistent …“): Assistent mit simulierter Funke ganz
    durchlaufen, jeden Knopf „Zuweisen“ der Schalterzeilen drücken und die abgefragte Funktion protokollieren; nur
    `SwitchFunction`-Werte, genau die Liste des Assistenten, kein Add-on-Schalter auf der Seite. Der Schritt „… mit Noclip 0.1.x:
    Maus über jede Schalterzeile ohne Absturz“ (`noclipHoverStep`, nur mit dem Noclip-Jar in `mods/`, sonst übersprungen)
    fährt mit der Maus über jedes „Zuweisen“ der Seite „Schalter“ (bis 0.5.2 `MatchException` in `RemoteDemo.forSwitch`).
  - `osd.addon.client.CovAddonOsdSelfTests` („addon-api-17: A2 – Abdeckung: …“): im Flug Elementfarbe `#00FFFF`, Deckkraft 100,
    Größe 2,0 (Glyphenhöhe aus dem Bildpuffer doppelt), nach dem Entladen CAUTION in Gelb (`OsdRenderer.AMBER`), mit
    Warnfarben aus in der Elementfarbe; die Ausschnitte liegen als `cov-api/osd_*.png` im Run-Ordner. Der Schritt leert
    zu Beginn den Chat und nimmt jedes Bild erst, wenn der Chat seit 10 Ticks leer ist (Befund WAD-5: im vollen Lauf lag der
    Titel des Zubehör-Hinweises aus A1 in Akzentfarbe über dem Element, gefiltert nie). Der Schritt davor („… Zubehör-Hinweise
    früherer Schritte stehen beim Start der Bildprüfung noch im Chat“) legt 12 solche Titelzeilen in den Chat; ohne das Leeren
    scheitert die Bildprüfung damit auch gefiltert.
  - `drone.collision.client.CovGhostSelfTests` („addon-api-17: A3 – Abdeckung: …“): Geist mit Recht, Drohne mitten in einer
    3 Blöcke dicken Wand festgehalten; die OSD-Warnung `INSIDE BLOCK` kommt erst, wenn der Geist in der Wand ausgeht (wie im
    Schritt „Schalter in der Wand aus“); Anteil fast schwarzer Pixel im FPV-Bild unter 5 %, Vergleichsbild im Zuschauermodus an
    derselben Kameralage (gleiches FOV, GUI aus): mittlerer Helligkeitsabstand im mittleren Bildbereich (16 × 9 Felder)
    höchstens 45 von 255; englisch und deutsch. Mit `-PpropwashScreenshots=true` die Fotos `ghost_inside_wall_{en,de}` und
    `ghost_inside_wall_spectator_{en,de}` (gehören nicht ins Repository).
  - „guard: Abdeckung – Noclip 0.1.x …“ (in `CovGhostSelfTests`, nur mit dem Noclip-Jar in `mods/`): FPV vor der Wand,
    Noclip über `key.propwash.noclip` oder, weil es diese Taste unter 0.6.0 nicht gibt, über einen Schalter der simulierten
    Funke (Kanal 8 auf `PWNOCLIP_NOCLIP`), mit 6 m/s gegen die Wand; das Log nennt „Noclip wirkt“ oder „Schalter ohne
    Wirkung“. Dazu im selben Lauf `drone: Landung`.
  - `server.client.CovAddonWorldRulesSelfTests` („addon-api-17: B5 – Abdeckung: …“): Vorschläge zu `/gamerule propwash_example:`
    am Server (`getCompletionSuggestions`) und im Befehlsbaum des Clients, `/gamerule propwash_example:launcher_cooldown 12` als
    Spieler, Rückmeldung im Chat, Wert im Regler des Tabs; Welt-Regeln → Tab Zonen → „Neue Zone hier“ → Zonen-Editor ohne Beispiel-Regel
    (Texte per `LayoutProbe`, nur Physik-Regelzeilen).
  - `guard.client.CovGuardSelfTests` („guard: Abdeckung – …“): im Details-Fenster „Regeln für Add-ons“ → Link-Abfrage
    (`ConfirmLinkScreen`) mit `NamespaceReportScreen.RULES_URL`, „Nein“ führt zurück, „Schließen“ schließt; Absturzbericht über
    `CrashReport.saveToFile` unter `crash-reports/`, Abschnitt „Propwash add-ons“ mit Add-ons samt Version, Mixins (sauber
    `none`, Noclip 0.1.2 `pwnoclip: 9 classes`) und Ergebnis der Namensraum-Prüfung (sauber `no findings`); Datenpaket
    `cov/cov-api/guard_pack` (geändertes Rezept `aio_20`, neues Rezept `cov_api_extra`) im Ordner `datapacks` der Testwelt,
    `/reload`, Welt verlassen und neu betreten: Funde und Hinweise unverändert, genau eine Info-Zeile „pack 'file/cov_api_guard'
    adds 1 new files“.
  - `guard.client.CovNoclipMenuSelfTests` („ui-scale: Abdeckung – …“): alle Konfigurator-Tabs, im Tab „Modi“ die Seiten „Befehle“,
    „Add-ons“ und „Funktionen“, im Tab OSD Optionen und Editor; Befunde der Layout-Prüfung nur im Log.
- **Server-Rauchtest** (`dev.CovApiServerChecks`, Zeile „Abdeckung cov-api: …“): `server.CovAddonRulesServerChecks` – Gast ohne
  Recht sieht `propwash_example:launcher_cooldown` nur lesbar, Tab-Paket und `/gamerule` abgelehnt; mit `/gamerule`, aber
  gesperrtem Knoten der Add-on-Regel weiter abgelehnt; nur mit freiem Knoten der Add-on-Regel geändert, die Schwerkraft mit
  gesperrtem Knoten nicht; als Operator geändert. `guard.CovGuardServerChecks` – Abschnitt „Propwash add-ons“ am dedizierten
  Server, unter Fabric über einen Absturzbericht wie bei einem echten Absturz (`fillSystemReport`, `saveToFile` unter
  `crash-reports/`), unter NeoForge über den Crash-Callable selbst (ein `CrashReport` schreibt dort „Preparing crash report
  with UUID“ als FATAL ins Log, der Rauchtest scheiterte daran). Mit dem Noclip-Jar in `runs/smoke-server/mods` endet der
  Rauchtest wegen der erwarteten ERROR-Zeile der Namensraum-Prüfung rot.
- **Beitrittstests** (§3, `PROPWASH_JOINTEST_HOLD=1800`, bei lange belegten Client-Slots 5400; die Clients nacheinander mit
  `< /dev/null`, sonst liest Gradle die Liste der Läufe aus der Standardeingabe):
  - `PROPWASH_JOINTEST_COV_ADDONRULES=1` an beiden Prozessen (Server `dev.CovAddonRulesJoinCheck`, Client
    `server.client.CovAddonRulesJoinProbe`, der Tab läuft bei offenem Konfigurator über `whileOpen`): der Spieler ist kein
    Operator, ein am Server simulierter Operator setzt die Abklingzeit auf 12; Erwartung
    `joined|rules_value=12|rules_section=readonly|rules_edit=denied|gamerule=denied` (der Client hat dann keinen Knoten
    `gamerule`, `gamerule_node=missing`; im nur lesbaren Tab steht der Wert ohne Bedienelement).
  - `PROPWASH_JOINTEST_COV_NOADDON=1` (Server mit `PROPWASH_DEV_EXAMPLE=off`, `dev.CovNoAddonJoinCheck`; Client mit
    Beispiel-Add-on, `drone.collision.client.CovNoAddonJoinProbe`): Geist per Taste G, danach direkt über die Kollisionsregel
    des Beispiels (`ExampleGhost.force`); Erwartung
    `joined|ghost_switch=inactive|collision=solid|resets=0|collision_forced=solid|resets_forced=0` (Regeln, die der Server
    nicht kennt, `AddonServerFeatures#knowsCollisionRule`, wirken beim Client nicht; vor dem Fix 9 Rücksetzungen).
  - `PROPWASH_JOINTEST_COV_NSOP=1` (Noclip-Jar nur in `runs/smoke-server/mods`, `dev.CovNsOpJoinCheck` macht Spieler mit
    `PwOp` am Namensanfang zu Operatoren, Client `guard.client.CovGuardJoinProbes#nsOp`): vier Client-Läufe mit
    `-PpropwashDevName=PwOp` (`ns_server_hint=1`), noch einmal (`=0`), `-PpropwashDevName=PwGast` (`=0`, `ns_operator=no`), nach
    einem Neustart des Servers `PwOp` (`=1`), immer `ns_client_hint=0`.
  - `PROPWASH_JOINTEST_COV_NOCLIP=1` (Noclip-Jar an Server und Client, `dev.CovNoclipJoinCheck`,
    `guard.client.CovGuardJoinProbes#noclip`): Erwartung `joined|noclip_fpv=held|ns_server_hint=1|ns_client_hint=1`; im
    Server-Log als ERROR nur die Zeile der Namensraum-Prüfung.

### Abdeckung cov-freecam

Prüfplan 0.6.0, Teil 1.8 (Freecam-Drohne) und Teil 2.9 Nr. 95 (RX-Block in der Freecam). Alles ändert keine Funktion,
nur Testklassen mit „Cov“ im Namen.

```sh
JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=freecam: Abdeckung'
JAVA_HOME=… <slot-skript> ./gradlew :neoforge:runClientSelftest '-PpropwashSelftestOnly=freecam: Abdeckung'
tools/fastcheck.sh test --select-class de.lardtom.propwash.freecam.client.CovFreecamConfigTest \
    --select-class de.lardtom.propwash.freecam.client.CovFreecamIntroHintTest \
    --select-class de.lardtom.propwash.freecam.client.CovGatePassesTest
```

- **Selbsttest** `freecam: Abdeckung – …` (`freecam.client.CovFreecamSelfTests`, `CovFreecamFlightSelfTests`,
  `CovFreecamIsolationSelfTests`, Hilfen `osd.client.CovFreecamOsdProbe` und `video.client.CovFreecamVideoProbe`) auf dem
  Platz der Freecam-Schritte (Spawn + (40, −36)); der erste Schritt sichert Freecam-Einstellungen, Hinweise und Eingaben,
  „aufräumen“ stellt alles zurück. Geprüft werden: Start mit F6 an der Augenposition (Gieren = Blickrichtung, Nick und
  Roll 0, 40 Ticks unscharf geparkt, Spieler still, kein Paket, Hand und Vanilla-HUD aus, OSD-Name „FREECAM · 5″“,
  Hinweis `freecam_intro`), Hinweis nur einmal und nach „Hinweise zurücksetzen“ (K → Flugschule) wieder, Ende per F6, Y
  und „Freecam beenden“ jeweils im nächsten Tick, in 20 m entschärft 60 Ticks unter 0,01 Block, Test-Funke, Testpad
  und Tastatur (W/S, Pfeiltasten) steuern die Sim, Modi per Schalter, Schultertaste und Ziffernblock, Suche nach
  „Action-Cam“ und „OSD“ (mit Gegenprobe im normalen K-Menü), Größe bei 15 m/s von 5″ auf 7″ (Position, Lage, Tempo und
  Drehraten gleich, scharf), Kamerawinkel und Rates je Größe, Sichtfeld für alle, OSD aus im nächsten Frame, Datei
  schreiben und neu lesen (`config/propwash/cov_freecam_probe.json`, danach gelöscht), Ende beim Wechsel in den Nether,
  sauberes Bild hinter der Wand und 14 Blöcke unter dem Platz im Boden (Dämpfung der Strecke vom Spieler gemessen,
  unter dem Boden mindestens 20 dB), Flugbahn-Marker mit 6 m/s und Seitenwechseln an der Projektion der
  Live-Geschwindigkeit, Prop-Wash wie eine `DroneSim` nach
  Welt-Regeln, Bodeneffekt dicht über dem Boden und keiner im Block, Zone mit 50 % Schwerkraft (weiche Blende hin und
  zurück, verglichen mit einem eigenen `ZoneField`), F7 beendet die Freecam und startet das Zuschauen, F6 im echten
  Flug verweigert, drei Gates eines Testkurses und ein Flip ohne Renn-Ereignis, Trick, Trail und Motorton, F9 und
  Ziffernblock 7 ohne Aufnahme und Blackbox, RX-Block auf Gas folgt der Test-Funke, nicht der Tastatur.
- **Bildschirmfoto** (nur Fabric): `-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos: Freecam Abdeckung'`
  nimmt `cov_freecam_start.png` (Start mit F6, Hand, HUD und OSD). Die Datei gehört nicht ins Repository.
- **Iris** (§5): `'-PpropwashSelftestOnly=bildschirmfotos: Iris Freecam'` mit Iris, Sodium und Complementary im
  Run-Verzeichnis baut bei Spawn + (−90, 70) einen Platz mit Baum, Säule und Dach in der Morgensonne und nimmt
  `cov_freecam_iris_01…03_shader.png` (Freecam an drei Stellen) und `cov_freecam_iris_04_vanilla.png`; ein Mensch
  prüft, ob Bild und Schatten der Freecam folgen. Ohne Iris melden sich die Schritte nicht an.
- **Beitrittstest** `PROPWASH_JOINTEST_COV_FREECAM=1` an beiden Prozessen (Server `dev.CovFreecamJoinCheck`, Log
  `[Beitrittstest] Freecam-Abdeckung: …`, Client `freecam.client.CovFreecamJoinProbe`): Der Server nimmt dem Spieler das
  Operator-Recht und setzt die Regel auf Standard, schaltet sie 15 s nach dem Beitritt an und 30 s nach dem neuen
  Beitritt zurück. Der Client öffnet K → Übersicht ohne Recht, startet nach dem Einschalten die Freecam, hält 400 ms an
  (Ruckler), schaltet nach Bildern scharf (`FreecamSelfTests#armAt`), fliegt 3 s ab dem Scharfschalten (ohne Scharfschalten
  nach 5 s, Log mit Blocker), trennt in der Freecam, tritt neu bei und läuft ein Stück. Erwartung:
  `PROPWASH_JOINTEST_EXPECT='joined|freecam_button=disabled|freecam_rule_jump=absent|freecam_reason=shown|world_rules=readonly|operator=no|freecam_started=yes|freecam_armed_in_flight=yes|freecam_end_on_disconnect=disconnect|freecam_after_rejoin=inactive|camera_after_rejoin=player|player_moves=yes'`,
  `PROPWASH_JOINTEST_LABEL=cov_freecam`, `PROPWASH_JOINTEST_HOLD=1800`.
- **JUnit** `freecam.client.CovFreecamConfigTest` (Rates und Kamerawinkel je Größe, Sichtfeld für alle mit Standard
  120°, Schreiben und Lesen mit den Einstellungen von `JsonConfig`, leere Datei gibt Standard, kaputte Werte werden
  bereinigt) und `CovFreecamIntroHintTest` (`freecam_intro` einmal, nach dem Zurücksetzen wieder).
- **Gate-Durchflüge** im Schritt „nichts wird gewertet“ zählt `freecam.client.CovGatePasses` auf dem ganzen Weg: vom
  gesetzten Punkt zum nächsten gemessenen (dazwischen schiebt der Wind die Freecam ein Stück) und von dort zum nächsten
  Punkt. Eine Ebene zählt halboffen, je Gate und Richtung einmal; verlangt sind 6 von 6. JUnit `CovGatePassesTest`
  spielt den Flug mit Abdrift in beide Richtungen und versetztem Start nach (Befund WA-W2a-3: im vollen Lauf auf
  NeoForge fehlte ein Durchflug, der genau auf der Ebene landete und dann abdriftete).
- **Scharfschalten nach Bildern** (Befund AUTO39-1): Alle Abdeckungs-Schritte, die die Freecam scharf schalten
  („Flugbahn-Marker“, „Prop-Wash“, „Zone mit halber Schwerkraft“, „nichts wird gewertet“, „in 20 m entschärft“,
  „Größe bei 15 m/s“, „F9 und Ziffernblock 7“), nutzen `FreecamSelfTests#armAt` wie die Freecam-Schritte (Befund WAD-4)
  statt Schalter an und Gas nach 5 Ticks. Damit das so bleibt, hält jeder dieser Schritte den Client vor dem Start
  400 ms an („Ruckler vor dem Start“), „F9 und Ziffernblock 7“ im Tick vor dem Scharfschalten. Mit dem alten Ablauf nach
  Ticks waren alle sieben rot (Blocker 3 bzw. „Freecam wurde zum Vergleich nicht scharf“), der Beitrittstest meldete
  `freecam_armed_in_flight=no`.

### Abdeckung cov-combo

Prüfplan-Punkte, die alle Add-on-Jars oder alte Welten brauchen: Teil 2.7 Nr. 70, 2.12 Nr. 119 und 123, 5.9 Nr. 51,
6.3 Nr. 21, 6.5 Nr. 32, 6.7 Nr. 41, 6.9 Nr. 57, 6.10 Nr. 60, 7.20 Nr. 138 und 8.14 Nr. 150. Nur Testklassen mit „Cov“
im Namen und neue Skripte; fremde Jars kommen nur in den Run-Ordner (`<loader>/runs/selftest-client/mods`,
`<loader>/runs/smoke-server/mods`) und danach wieder heraus.

- **Altwelt** `migration: Abdeckung cov-combo – …` (`dev.client.CovMigrationSelfTests`, nach den Schritten der Gruppe
  `migration:`, mit `'-PpropwashSelftestOnly=migration:'` dabei): Inhalt der Testwelt (Drohnen mit Namen, Lack, Tuning,
  Homepoint, Wasserschutz, VTX, Gates, Kurse, Bestenlisten, Geister, Zonen, Regeln, Geräteprofil; Zahlen in
  `cov-combo/inhalt-<ordner>.txt`), dann die umgestiegene Welt wieder öffnen und:
  - **Bando** (nur mit `disk.covComboBando` in der Erwartung, also der Testwelt 0.5.2): Die Chunks des in 0.5.2 gesetzten
    Bandos müssen als fertig gespeichert sein; der Blockhash (SHA-256 über alle Blockzustände im Bereich samt 4 Blöcken
    Rand bis zur Weltunterkante) muss dem aus 0.5.2 gleichen. Daneben, in vorher nicht gespeicherten Chunks, setzt der
    Schritt denselben Bauplan über derselben Grube mit dem heutigen Code: Die Form (Luft gegen Block) darf nur in den
    Hofspalten über eigenem Fundament abweichen, dort ist der Hof geschlossen, im alten Bando offen (neue Lage, nur in
    neuen Chunks). Das Material darf abweichen, die Palette hängt von der Lage ab.
  - **Kamera-Add-on** (nur mit seinem Jar): jede Regel `camdrones:*` auf ihrem Standard, `allow_autonomy` an,
    `max_altitude` 120.
  - **Alte Drohnen fliegen gleich**: je Drohne der Erwartung ein Flug-Fingerabdruck (`DroneSim` mit Flug-Bau, Tuning,
    Akku und Verschleiß der Drohne, 4 s mit festen Eingaben) in `cov-combo/flug-<ordner>-<loader>-ohne-addon.txt` bzw.
    `-mit-addon.txt`; der Lauf mit Add-on vergleicht mit dem ohne (im selben Run-Ordner zuerst ohne, dann mit dem Jar).
  - Log ohne Fehler seit dem Wiederöffnen (`cov-combo/log.txt`).
- **Testwelt 0.5.2 mit Bando**: Die Testwelt 0.5.2 enthält seit cov-combo einen in 0.5.2 gesetzten Bando (`barn`, Seed 1,
  bei 640, 0 über einer Grube bis zur Bedrock-Schicht, 27 Hofspalten über eigenem Fundament) und die Erwartung
  `disk.covComboBando` (nur ergänzt; `disk` vergleicht der Umstieg nicht). Geschrieben hat sie ein Schritt im
  Wegwerf-Arbeitsbaum ab dem Schreib-Schritt `fixture:` (Tag `v0.5.2`), der die Welt mit Just More Parts 1.1.1 öffnet, die
  Chunks erzeugt, die Grube gräbt, den Bando mit dem Code von 0.5.2 setzt, den Hash bildet und die Welt packt.
- **Eigene 0.5.2-Welt** (Prüfplan 2.12 Nr. 119 und 123): Eine Spielerwelt aus 0.5.2 wird wie eine Testwelt erfasst, im
  selben Wegwerf-Arbeitsbaum, als derselbe Spieler (`-PpropwashDevName=<Name>`, UUID des Kontos), mit Just More Parts 1.1.1
  und den Client-Configs des Spielers im Run-Ordner: alle Drohnen und Gates von der Platte gelesen, ihre Chunks geladen,
  gewartet, bis Kurse und Gates stabil sind (Gates von Welt-Kursen kommen erst beim Ticken in ihren Kurs), Drohnen mit
  Teilen eines nicht geladenen Add-ons ausgelassen, dann `world.zip`, `expected.json` mit dem Geräteprofil der eigenen
  `input.json`, die Configs und persönliche Geister abgelegt. Geprüft wird mit dem Jar von Just More Parts 1.2.0:

```sh
JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=migration:,!migration: Runde durch das unterste' \
    -PpropwashFixtureWorld=<ordner> -PpropwashDevName=<name>
```

  Die Runde durch das unterste gestapelte Gate braucht einen Kurs mit Gate-Stapel und bleibt bei Welten ohne einen
  draußen.
- **Zwei Add-ons im OSD-Editor** `addon-api-17: A2 – Abdeckung cov-combo: Gruppe Add-ons mit Mob Defense und
  Kamera-Add-on zusammen …` (`osd.addon.client.CovAddonComboSelfTests`, nur mit beiden Jars, sonst übersprungen): alle
  Elemente von `mobdefense` und `camdrones:mode`, `gimbal`, `mission` in der Gruppe Add-ons, Tooltip mit dem Namen des
  Add-ons (die beiden Namen verschieden), Zeile in der Liste wählt das Element, Pfeiltasten verschieben es, die Kopie der
  Einstellungen behält die Stelle. Kombi-Lauf mit beiden Jars in `mods/`:
  `'-PpropwashSelftestOnly=addon-api-17: A2,ui-scale:,share: Abdeckung cov-combo'` (beide Loader, etwa 20 min).
- **Namensraum ohne Fehlalarm** `guard: Abdeckung cov-combo – mit Just More Parts 1.2.0, Mob Defense und
  Kamera-Add-on …` (`guard.client.CovComboGuardSelfTests`, nur mit allen drei Jars): 0 Funde, kein Hinweis, im Log je eine
  Zeile `Namespace check (…): 0 findings` für `start`, `resources` und `server start`. Lauf `'-PpropwashSelftestOnly=guard:'`
  mit den drei Jars; am dedizierten Server dieselben Jars in `runs/smoke-server/mods` und `runServerSmoke`, im Log
  `Namespace check (start)` und `(server start)` mit 0 findings.
- **Add-on zeitweise entfernt**, drei Starts im selben Run-Ordner (`dev.client.CovAddonCycleSelfTests`, Gruppe
  `platzhalter: Abdeckung cov-combo – Start 1/2/3 …`, Beispiel-Add-on aus, Welt `Cov Addon-Zyklus`):

```sh
S=<slot-skript>; PROPWASH_MC_SLOTS=$S tools/remote_cov_addoncycle_test.sh fabric <jar mob defense> <jar kamera-add-on>
```

  Start 1 mit beiden Jars: Schalter und Achsen der Add-ons im Geräteprofil, OSD-Plätze ihrer Elemente, jede Add-on-Regel
  geändert, eine Drohne mit Werfer, Booster, Speer und 12 Pfeilen und eine Kamera-Drohne, beide mit Add-on-Daten; Stand
  nach dem Speichern in `cov-combo/addoncycle/start1.json`. Start 2 ohne die Jars: Welt ohne Fehler im Log, keine Seite
  „Add-ons“, keine Add-on-Regeln, eingebaute OSD-Elemente und Belegungen gleich, alle Add-on-Teile als Platzhalter, eine
  eingebaute Belegung geändert; nach dem Speichern stehen die Add-on-Schlüssel weiter in `osd.json`, `input.json` und
  `game_rules.dat`. Start 3 wieder mit: Belegungen, OSD-Plätze, Regeln und Drohnen (Bau, Inhalt, Add-on-Daten) wie nach
  Start 1, auch auf der Platte nach Start 2. Ergebnisse in `build/cov-addoncycle/`. Die Schritte eines Starts laufen nur,
  wenn der Filter diesen Start nennt (`platzhalter: Abdeckung cov-combo – Start <n>`, so ruft das Skript sie auf); im
  vollen Lauf und mit einem breiteren Filter wie `platzhalter:` sind sie übersprungen.
- **Neue Add-ons mit Propwash 0.5.2** (Prüfplan 6.9 Nr. 57): Wegwerf-Arbeitsbaum des Tags `v0.5.2`, je Add-on-Jar allein in
  `<loader>/runs/smoke-server/mods` und `runServerSmoke` über das Slot-Skript. Erwartet: Abbruch beim Laden mit einer
  Meldung, die `propwash` und `0.6.0` nennt (Fabric „requires version 0.6.0- or later of mod 'Propwash FPV' (propwash),
  but only the wrong version is present: 0.5.2“, NeoForge „Mod … requires propwash 0.6.0-alpha or above“), kein Eintrag in
  `crash-reports/`, keine Zeile `at de.lardtom.propwash` im Log.
- **Sharecode ohne Mob Defense** `share: Abdeckung cov-combo – …` (`share.client.CovComboShareSelfTests`): Mit dem Jar
  schreibt der erste Schritt die Codes einer Drohne mit Werfer, Booster und Speer mit und ohne Add-on-Infos
  (`cov/cov-combo/mobdefense-codes.json` in den Test- und Selbsttest-Ressourcen, ohne Infos kürzer); ohne das Jar nennt
  der Tab „Sharecode“ beim Code mit Infos Name, Version und Link aus dem Code, beim Code ohne Infos nur die Mod-Id, alle
  drei Halterungen „(entfernt)“, und „Mit Ersatzteilen erzeugen“ legt die Drohne ohne Halterungen ins Inventar. JUnit
  `share.CovComboMissingAddonsTest` liest dieselben Codes ohne die Tabelle von Mob Defense.
- **Release-Texte** (Prüfplan 5.9 Nr. 51): `python3 tools/check_release_texts.py --paket <release-paket> --ref v0.6.0
  --jmp-changelog <propwash-justmoreparts>/CHANGELOG.md --jmp-ref v1.2.0` (im Release); `--probe [--texte <ordner>]`
  baut ein Paket aus dem heutigen `CHANGELOG.md` und prüft mit Gegenproben, dass jede Prüfung einen Fehler erkennt.
- **Texte für Add-on-Bauer** (Prüfplan 6.10 Nr. 60): `python3 tools/public_export_texts_test.py [--gegenprobe]` ruft
  `tools/public_export.py --check-only --no-build` auf und prüft im ausgepackten Export `ADDONS.md`, den Absatz „Reserved
  namespace“ in README und NOTICE und die Fehlerbericht-Vorlage.
- **Gemessen am 23.10.** (Fabric und NeoForge mit denselben Zahlen, wo nicht anders genannt): `migration:` gegen die
  Testwelt 0.5.2 mit Bando 29 von 29 Schritten, 2564 Werte, 0 Abweichungen, Blockhash des alten Bandos gleich (3608 feste
  Blöcke), neuer Bando: Form an 54 Stellen anders, alle in den 27 Hofspalten über eigenem Fundament (alt 27 Löcher, neu
  27 geschlossen), 741 Blöcke nur im Material anders; mit dem Kamera-Add-on dazu 6 Regeln auf Standard und 13 Drohnen
  fliegen gleich wie ohne, Log 0 unerwartet. Testwelt 0.5.0 ebenfalls 29 von 29. Eigene 0.5.2-Welt (5 Drohnen, 39 Gates,
  6 Kurse, 7 geänderte Regeln, Geräteprofil mit 6 Schaltern) mit Just More Parts 1.2.0: 2890 Werte, 0 Abweichungen, 5 PW3-
  und 10 umgerechnete alte Codes gleich, 20 Client-Configs mit 467 Werten wie vorher, Schaltermaske 12113 (höchstes Bit 16),
  alle 6 Schalter schalten, 28 von 28 Schritten. Kombi-Lauf mit Mob Defense und Kamera-Add-on: Gruppe Add-ons mit 10
  Elementen (4 von `mobdefense`, 5 von `camdrones`, das Beispiel-Element), alle per Pfeiltaste verschoben; `guard:` mit
  den drei Jars 22 von 22 (0 Funde, 0 Hinweise, `start` 6–20 ms), am dedizierten Server `start` und `server start` mit
  0 findings; drei Starts je 12 von 12 (14 Schalter und 3 Achsen des Kamera-Add-ons, 9 OSD-Elemente, 16 Regeln, 10
  Platzhalter); Sharecode ohne Mob Defense: Code mit Infos 100, ohne 54 Zeichen, „Propwash: Mob Defense“ 1.0.0 mit Link
  bzw. nur `mobdefense`, drei Halterungen „(entfernt)“, Drohne ohne Halterungen im Inventar; die drei Add-ons brechen mit
  Propwash 0.5.2 auf beiden Loadern beim Laden ab, ohne Absturzbericht.

### Geister und Replays der Bestenliste (`race:`)

`'-PpropwashSelftestOnly=race: Gates platzieren,race: Runden,race: Geist der Bestrunde,race: Bestenliste,race: Geister,race: Sharecode,race: Replay,race: Fremden Geist,race: Welt-Regel,race: Zeit löschen,race: Aufräumen,race: Zustand nach'`
(`race.client.RaceGhostsSelfTests`, beide Loader, etwa 30 s): Plausibilitätsschritt prüft den Geist der Bestenliste
(Server-Spur im 20-ms-Raster, Bauplan, Sharecode, Kamerawinkel); ein zweiter Pilot „Rivale“ mit Spur durch alle Gates;
Bestenliste mit Code und Geist je Zeile, Hover-Vorschau am Code (Maus über dem Code), Kopieren in die Zwischenablage;
Replay des Rivalen: FPV mit Kamerawinkel 30°, Kamera an Linsenlage und -richtung, Abspielen mit 4×, 5 s zurück, Ansicht
hinter der Drohne mit gezeichnetem Geist, Zurück zur Bestenliste mit HUD wie vorher; „Gegen fliegen“ lädt den Geist
über das Netz und macht ihn zur Split-Referenz, zurück zum eigenen; Welt-Regel Aus (Admin: Replay ja, Gegner nein) und
Admins; Zeit löschen per Fenster (zweiter Klick) und per `/propwash race remove`, Datei weg, Protokollzeilen. Mit
`-PpropwashScreenshots=true` (Fabric) die Bilder `leaderboard_share_code`, `race_replay_fpv`, `race_replay_chase`.
Replay mit Vorlauf (Befund REPLAY1-1, Schritt „race: Replay beginnt vor dem Startgate“): die Spur des Rivalen hat 1 s
Anflug durchs Startgate; das Replay beginnt bei −1 s, die Kamera steht vor dem Startgate und schaut darauf, die Uhr zeigt
bis zum Gate 0:00.000 und läuft danach, Spulen zurück endet am Anfang des Vorlaufs, die Zeitleiste beginnt vor der Marke
des Startgates; Bild `race_replay_lead`.

Gate-Zahl im Replay (Befund FB1, Schritte „race: Replay zählt dieselben Gates …“ und „race-p2p: Replay zählt Start und Ziel
mit …“, Bild `race_replay_gate_count`): „Gates: N“, Bestenliste und Kurs nennen dieselbe Zahl, die Zeitleiste hat je Gate eine
Marke, die erste bei 0, bei Punkt zu Punkt die letzte am Ziel. JUnit: `race.ReplayMarksTest`.

Replay als Video (Befund FB1, `race.client.ReplayVideoSelfTests`, Schritte „race: Replay als Video: …“, im Filter `race: Replay`
enthalten, beide Loader, etwa 1 min mehr): Knopf aus mit Hinweis ohne ffmpeg und bei kaputtem ffmpeg (Zustand erzwungen über
`ReplayVideo#forceFfmpegState`), Knopf passt in 320×240, 427×240 und 640×360; Esc bricht ab und lässt keine Datei zurück, das
Replay bleibt offen, Schließen während der Aufnahme bricht ebenso ab; 20-s-Geist mit 1 s Vorlauf hinter der Drohne und ein
kürzerer in FPV: Datei `propwash_replay_…mp4` im Action-Cam-Ordner, Format und Qualität aus dem Tab Action-Cam, Länge = Vorlauf
plus Runde (±0,4 s), keine Tonspur. Bilder aus dem Video holt ffmpeg; der Schritt prüft Streuung, das Startgate im ersten
Bild (helle Pixel über dem Gras) und die Drohne in der Bildmitte (hinter der Drohne auf mindestens einem Bild, in FPV auf keinem).
Mit `-PpropwashScreenshots=true` (Fabric) die Bilder `race_replay_video_no_ffmpeg`, `race_replay_video_button`,
`race_replay_video_chase_progress` und die Einzelbilder aus den Videos `race_replay_video_chase`, `race_replay_video_fpv`.
Ohne ffmpeg auf dem Rechner prüfen die Schritte nur den Knopf. JUnit: `race.ReplayExportTest`.

Beitrittstest (§3, Schalter `PROPWASH_JOINTEST_RACE_GHOSTS=1` an beiden Prozessen, Server `dev.RaceGhostsJoinCheck`,
Client `race.client.RaceGhostsJoinProbe`):

```sh
PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_RACE_GHOSTS=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=raceghosts PROPWASH_JOINTEST_RACE_GHOSTS=1 \
  PROPWASH_JOINTEST_EXPECT='joined|raceghosts rows ok|raceghosts upload asked ok|raceghosts access player|raceghosts fetch ok|raceghosts remove refused ok|raceghosts ok' \
  JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
```

Der Server legt 3 s nach dem Beitritt mit dem Kurs-Werkzeug des Spielers einen Kurs an, fliegt für den beigetretenen
Spieler und einen zweiten, simulierten Piloten („Zweiter“) je eine Runde über `RaceServer#onDroneMoved` und legt eine
200-s-Spur mit Rauschen („Langstrecke“) dazu. Der Client erwartet Sharecode und Geist an beiden geflogenen Zeilen,
Replay- und Gegner-Recht ohne Lösch-Recht (kein Operator), lädt das Replay des zweiten Piloten und den langen Geist in
mindestens zwei Stücken, und ein Löschversuch ohne Operator-Recht lässt die Zeile stehen. Dasselbe mit `:neoforge:`.
Die lange Spur hat 1 s Vorlauf vor dem Startgate; nach dem Abruf prüft der Client ihn (`raceghosts fetch ok … lead=1000ms`,
Befund REPLAY1-1). Seit Netzprotokoll 20 bittet der Server nach der Runde des beigetretenen Spielers um dessen
Aufnahme (`GhostUploadS2C`); der Client hat die Runde nicht selbst geflogen und sagt ab (`raceghosts upload asked ok
declined=1`, Befund OT4-2) – damit gehen beide neuen Pakete über eine echte Verbindung.

Vorlauf auf dem echten Server-Weg (Befund AUTO50-1): Der Schritt „race: Geister der Bestenliste aus dem Server-Flug
beginnen vor dem Startgate“ (im Filter oben über `race: Geister`, rund 1 s) lässt einen simulierten Piloten („Anflug“)
über `RaceServer#onDroneMoved` 1,5 s auf das Startgate zufliegen. Die Startmeldung kommt erst 0,3 s vor dem Gate, die
Sekunde davor kennt der Server also nur aus dem Aufzeichnen außerhalb eines Rennens. Der Geist, den der Server für die
Bestenliste speichert, muss 1000 ms Vorlauf haben, und sein Anfang muss auf dem Anflug liegen; danach löscht der Schritt
Zeit und Geist wieder. Im Beitrittstest fliegen beide Runden genauso: Der Server prüft beide Geister (`… lead=1000ms` in
seiner Ergebniszeile), der Client das geholte Replay des zweiten Piloten (`raceghosts replay ok lead=1000ms`). Zeichnet
der Server erst ab der Startmeldung auf, sind es 260 ms; schneidet `storeGhost` ohne Vorlauf, sind es 0 ms. Beides lässt
den Schritt und den Beitrittstest scheitern.

### Gefüllte Kessel (`drone: Kessel`)

Befund OWNP6-7, `docs/drone-cauldrons.md`: `'-PpropwashSelftestOnly=drone: Kessel'` (`drone.client.CauldronSelfTests`,
beide Loader, sechs Schritte mit Abbau, rund 15 s in der Testwelt, eigene Whoop-Testdrohne ohne Wasserschutz, Testfeld
26 Blöcke westlich und ab 10 südlich des Spielers). Je Kesselart (Wasser Stufe 3, Lava, Pulverschnee Stufe 3, leer)
wird die Drohne auf Stein scharf geschaltet und erst in den passenden Block (Wasser, Lava, Pulverschnee, Luft in einer
Steinmulde), dann in den Kessel gesetzt, je 30 Ticks beobachtet. Wasser und Lava: im Block und im Kessel entschärft
mit `FLUID` (Wasser nach höchstens 15 Ticks, gemessen 6; Lava nach höchstens 2, gemessen 0) und Sperre `IN_FLUID`;
Pulverschnee und leer: bleibt scharf, keine Sperre. Pulverschnee (Befund DEC2810-1, `docs/drone-cauldrons.md` §3): Die
Whoop startet 0,5 m über dem Boden des Blocks bzw. des Beckens; im Block und im Kessel meldet `PilotSnags` die Bremse,
sie sinkt mindestens 0,3 m ein und dabei höchstens 1,5 m/s schnell (ohne Bremse fällt sie mit rund 2,7 m/s). Wasser,
Lava und leer: keine Bremse. Danach je ein Heimflug am Server (`rescue.CauldronServerFlights`, 20 Ticks) im Block und im
Kessel: bei Wasser und Lava `FLUID` (6 bzw. 1 Tick), sonst nicht wegen Flüssigkeit entschärft; bei Pulverschnee bremst
die Abtastung des Heimflugs (`ServerFlight.env.snag`), sonst nicht. Schritt „Füllhöhe“: Wasser- und
Pulverschneekessel jeder Stufe, Lava- und leerer Kessel; `DroneFluids#at` 1 cm über dem Boden und 1 cm unter der
Füllhöhe die Flüssigkeit (Pulverschnee keine), 1 cm darüber und in der Wand keine; die Füllhöhe liegt in der
Vanilla-Form des Inhalts (`getEntityInsideCollisionShape`), 1 cm darüber nicht; `SnagBlocks#shape` bremst nur im
Pulverschneekessel, dort 1 cm über dem Boden und 1 cm unter der Füllhöhe, nicht 1 cm darüber und nicht in der Wand;
`WaterSurface#relative` 0,3 und 1,3 Blöcke über dem Kesselboden wie im Wasserblock. Ohne den Fix scheitern die
Schritte zu Wasser und Lava („… entschärft die scharfe Drohne nicht wie der Wasserblock“) und zu Pulverschnee
(„Kontrolle Pulverschneeblock bremst nicht wie Pulverschnee …“). JUnit: `tools/fastcheck.sh test --select-class
de.lardtom.propwash.drone.CauldronContentTest --select-class de.lardtom.propwash.sim.CauldronFluidTest --select-class
de.lardtom.propwash.sim.PowderSnowFlightTest --select-class de.lardtom.propwash.interact.PowderSnowSnagTest`.

### Weltkurse mit den Mechaniken von 0.6.0 (Befund WCOURSE-1, 0.6.0)

Generator und Daten: `python3 tools/data/world_courses.py --check` (neue Vorlagen unter `world_course/v2/`, alte
byte-gleich, Fluglinien in `common/src/devtest/resources/worldcourse/lines.json`). JUnit:
`tools/fastcheck.sh test --select-class de.lardtom.propwash.race.world.WorldCourseMechanicsTest --select-class
de.lardtom.propwash.race.world.WorldCourseDataTest` (Mechaniken je Struktur, saubere Runde jeder Fluglinie mit dem echten
`LapTracker`, alte Vorlagen per SHA-256, keine Kurs-Nummer und UUID in den Vorlagen, Marker alt und neu, zwei Fundorte =
zwei Kurse mit getrennter Bestenliste).

Selbsttest `'-PpropwashSelftestOnly=race: Weltkurs'` (beide Loader, im Lauf mit `race:` enthalten; mit
`race: Weltkurs-Leiter` aus `GateStackSelfTests` sieben Schritte): „race: Weltkurs-Vorlagen …“ setzt die sieben neuen und
zwei alte Vorlagen (`world_course/drone_arena`, `world_course/field_slalom`) und prüft Durchgänge, Richtung, Modus,
bündigen Stand und Tafel; „race: Weltkurse sauber abgeflogen …“ setzt jede neue Vorlage (gedreht), fliegt ihre Fluglinie
beim Client mit `LapTracker` auf den Kursdaten aus dem Netz (Start, alle Durchgänge, Runde bzw. Ziel, nichts ausgelassen,
keine falsche Richtung oder Seite) und am Server über `RaceServer` bis in die Bestenliste; „race: Weltkurs zweimal
gesetzt …“ setzt zwei Drohnen-Arenen nebeneinander und verlangt zwei Kurse mit eigener Nummer und Kurs-ID, ohne
gemeinsames Gate, mit getrennter Bestenliste und eigener Tafel. Mit `'-PpropwashSelftestOnly=race: Weltkurs,bildschirmfotos:
Weltkurs' -PpropwashScreenshots=true` (Fabric) die Bilder `world_course_<vorlage>.png` (Blick von der Startseite, 22 Blöcke
hoch, Kurs-Werkzeug in der Hand).

### Statistiken im Statistikfenster (pw-stats, 0.6.0)

JUnit: `tools/fastcheck.sh test --select-package de.lardtom.propwash.stats` (13 Tests: `FlightTallyTest` Zeit je
Server-Tick, Pakete im selben Tick und bei 5 Hz, Lücken über 1 s, Strecke mit Rest unter 1 cm, Scharfschaltungen und
übernommene scharfe Drohne, leerer Akku einmal je Akku und nur im Flug, Höchsttempo in Zehntel km/h; `CrashCountTest`
Crash-Meldung je Sitzung und Vorrat am Server; `StatClassTest` Klassenzuordnung eingebauter und Add-on-Rahmen über ihre
Rolle, 16 eindeutige Namen, beide Sprachdateien vollständig, Formate).

Selbsttest `'-PpropwashSelftestOnly=stats:'` (beide Loader, 8 Schritte, etwa 1 min): Anmeldung (16 Einträge in
`minecraft:custom_stat` und `Stats.CUSTOM`, Formate, Texte, Kriterium der Anzeigetafel); echter FPV-Flug mit
Test-Knüppeln auf einer Freestyle-Drohne (steigen, 2,5 s vorwärts, bremsen, landen) – der Server zählt eine
Scharfschaltung, die Flugzeit gesamt und als Freestyle (70 bis 120 % der scharfen Client-Ticks), die Strecke (±30 % des
Client-Wegs) und das Höchsttempo (mindestens 80 % der Spitze der Simulation), keine andere Klasse; Crash: steigen auf
10 m, unscharf fallen lassen, der Server zählt so viele Crashes wie das OSD (`PilotSession#crashCount`) und wie gemeldet
(`StatsClient#sentCrashes`); Akku leer: im Schwebeflug den Akku der Simulation auf 0,2 % setzen, der Server sieht 0 % und
zählt genau einen leeren Akku, auch 2 s später; Freecam: 3 s Flug mit der Freecam-Drohne (Welt-Regel
`allow_freecam` kurz an) ändert keine Statistik; Runde und Rennen: Kurs mit zwei Gates
(`dev.RaceDataJoinCheck.build`), ein zweiter Spieler am Server fliegt eine Runde bis ins Ziel (`laps` und
`races_finished` je +1 nur bei ihm, vom Server geschobene Drohne ohne Flugzeit und Strecke), danach der Spieler selbst;
Statistikfenster: Vanilla-`StatsScreen`, Reiter Allgemein, alle 16 Propwash-Zeilen stehen beieinander und zeigen
Name und Wert wie am Server. Mit `-PpropwashScreenshots=true` (Fabric) bei 1920 × 1080 die Bilder
`stats_general_en.png` und `stats_general_de.png` (Liste auf die Propwash-Zeilen gerollt).

Beitrittstest (§3):

```sh
PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_STATS=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=stats PROPWASH_JOINTEST_STATS=1 \
  PROPWASH_JOINTEST_EXPECT='joined|stats ok' JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
# danach jointest.stop in fabric/runs/smoke-server; dasselbe mit :neoforge:
```

`dev.StatsJoinCheck` (Log `[Beitrittstest] Statistik: …`) setzt 60 Ticks nach dem Beitritt eine Freestyle-Drohne neben
den Spieler und startet FPV; `stats.client.StatsJoinProbe` schaltet scharf, steigt auf 10 m und lässt die Drohne
unscharf fallen. Ist sie 3 s unscharf, schickt der Server seine Werte (`[stats] server …`, gesamt und Änderung) und gibt
die Drohne frei; der Client öffnet das Statistikfenster und vergleicht jede Zeile mit dem Server. Ergebnis
`stats ok window=server flight_time=…,crashes=…,arms=1,flight_time_freestyle=… top=… km/h time=… rows=62-77 of 94`;
der Server zählt so viele Crashes wie das OSD.

Fortschritte im Tab „Propwash FPV“ (Befund FB1-14, `docs/pilot-stats.md` §4): JUnit `stats.advancement.FpvAdvancementFilesTest`
(28 Dateien unter `data/propwash/advancement/fpv/`, Wurzel mit Hintergrund, Baum, nur eigene Auslöser bzw.
`player_killed_entity`, Texte und Symbole) und `FpvTriggerLogicTest` (Abheben, Sturz, Bedingungen, Flugschule);
Selbsttest `stats.client.FpvAdvancementSelfTests` (5 Schritte im Präfix `stats:`): geladen und zurückgesetzt, Abheben
und FPV-Flug aus dem echten Flug des Schritts „Flug im FPV“, die übrigen 19 Fortschritte der Server-Seite über ihre
echten Wege (`dev.FpvAdvancementCases`, Befund AUTO61-2; 17 Wege, je Weg zurückgesetzt, ausgelöst und einzeln geprüft,
Pakete über den angemeldeten Handler mit Kodierung hin und zurück, am Ende alle 22 der Server-Seite erreicht): Runde,
eigener Kurs und Bestzeit über `RaceServer` (vorher 9 s eingetragen, 8 s geflogen), Werkbank mit Klick, Shift-Klick,
Zifferntaste und Wegwerfen (`DroneWorkbenchMenu`; je Weg genau ein `DRONE_ASSEMBLED` mit der gebauten Racer-Drohne samt
Lack, Tuning und Namen, Befund G-AUTO61-1-1), Ladegerät mit Klick und Shift-Klick (`BatteryChargerMenu`), Schnelltausch
des Akkus, Strecke, Höhe über Y 320 und Sturz über `SERVER_MOVED`, Crashes über `stats_crash`, Fracht über
`DroneCargo.grab`/`releaseBy`, Lackierung über
`PaintC2S`, Sharecode über `BuildAccessImpl.applyImport`, Zuschauen über `SpectateC2S`, Enderman über `DroneStrikeC2S`
(weicht aus, bleibt unverletzt), Wither und Enderdrache sterben am Todesstoß der Drohne; danach die 6 Fortschritte der
Client-Seite über die echte Verbindung (echter Flip im FPV-Flug über `TrickTracker`, zwei Runden über
`ClientRaceTracker.feed`, die zweite schneller als der Geist der ersten, Flugschule über `StatsClient` mit allen
Lektionen auf 3 Sternen, Power Loop und Kombo als Paket `stats_trick`); Tab am Client mit allen 28 erreicht; mit
`-PpropwashScreenshots=true` `stats_advancements_en.png` und `stats_advancements_de.png`. Der Beitrittstest
`PROPWASH_JOINTEST_STATS` setzt die Fortschritte vor dem Flug zurück, der Client schickt im Steigflug `stats_trick`
(Flip, Power Loop, Kombo), `stats_training` und `stats_ghost_beaten`, der Server gibt die Drohne frei, geht die Wege der
Server-Seite durch und hängt an die Änderung `fpv_loaded=28,fpv_flown=1,fpv_done=28` an; der Client prüft dazu alle 28
im Tab.

### Kamera-Chunks (`capture:`, bg-capture, 0.6.0)

JUnit: `capture.CaptureRulesTest` (Radius aus Anfrage und Welt-Regel, Kosten, Zulassung je Spieler, Server, Budget und
Überlast, Ende-Gründe, Speicher-Radius mit Stufen und Spielraum, Pause über 128 Chunks, Tickets, Anfragen je Fenster),
`capture.CameraTrackingViewTest` (Vereinigung, jeder Chunk genau einmal, Gleichheit), `capture.TrackingDiffTest`
(Anmelden, Abmelden, Verschieben und Leeren ändern nur echte Unterschiede, Vergleich mit Brute Force; Vanilla würde alle
Chunks vergessen), `capture.CaptureBookTest` (Lebenszyklus: anmelden, ändern, abmelden, Grenzen, Budget, Welt-Regel,
Pause, Ende aus jedem Grund und erneut anmeldbar).

Selbsttest `capture:` (8 Schritte, beide Loader; mit Flashback zwei mehr, nur Fabric):

```sh
cd <WT> && JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=capture:'
```

Die Gruppe beginnt an einer festen Startlage (325/-239, Chunk 20/-15, wie im vollen Lauf 20 Chunks östlich und 15
nördlich vom Spawn) und bringt den Spieler im Aufräumen an die Lage vor der Gruppe zurück; gefiltert und im vollen Lauf
gelten damit dieselben Lagen (Befund WA-W2d-4). Eine eigene Drohne (Tag des Spielers) und ein Rüstungsständer
`capture-marker` stehen mindestens 300 Blöcke und weiter
als Sichtweite + Radius + 6 Chunks vom Spieler; Anmeldung über `PropwashClientApi.cameraChunks().request(id, 3)`. Der
Client hat danach alle 81 Chunks, Drohne und Marker; Spieler, Kamera und eigener Chunk unverändert, die Lage liegt
außerhalb der wirksamen Sichtweite und ihr Abschnitt wird nicht gezeichnet; der Server zeigt Spieler plus Lage und
`status(...).complete()`. Danach: Drohne 6 Chunks weiter, neue Chunks da, alte frei; ohne unbegrenzte Reichweite
(`allowUnlimitedRange` kurz aus) pausiert die Lage und gibt ihre Chunks frei, danach kommt sie wieder; Abmelden gibt
Chunks, Drohne, Marker und den Zusatzspeicher frei, der Server hat keine Lage und keine Tickets mehr; Grenzen über die
Server-API (fremde Drohne, Rüstungsständer, dritte Lage, Radius 20 → 4, Welt-Regel 1 → Radius 1, Welt-Regel 0 beendet
beide Lagen und lehnt neue ab, entfernte Drohne beendet ihre Lage); Weltwechsel in den Nether beendet die Lage und das
neue Level hat Vanilla-Speicher; Tod beendet die Lage (Wiedereinstieg, zurück an den Startpunkt).

Liegt `Flashback-0.43.6-for-MC26.3.jar` in `fabric/runs/selftest-client/mods/`, kommen zwei Schritte dazu: Der Spieler
geht an einen festen, sonst unbenutzten Aufnahmeort (-3000/3000), erst wenn der Client dort angekommen ist und die
Chunks um ihn hat, nimmt Flashback auf (Schnellspeichern an), währenddessen wird eine Lage 300 Blöcke weit weg angemeldet
und kommt an, nach 3 s endet die Aufnahme und der Spieler geht zurück; nach dem Verlassen der Welt öffnet
`capture: Flashback-Wiederholung …` die Aufnahme, springt kurz vor ihr Ende und prüft im Wiederholungs-Server: alle
inneren Chunks der Lage haben Gelände, Drohne und Marker sind da, der Chunk in gleicher Entfernung auf der Gegenseite ist
leer (Ergebnis zuletzt `49 von 49 Chunks der Lage mit Gelände, Drohne da, Marker da, Gegenseite (Chunk -207) leer`).
Vorher nahm Flashback dort auf, wo der Spieler gerade stand: Nach dem Tod im Schritt davor kamen die Chunks am Spawn in
die Aufnahme, und stand der Spieler wie im vollen Lauf 20 Chunks vom Spawn, lag die Gegenseite genau dort
(`Gegenseite (Chunk 1) mit Gelände`, mit der festen Startlage auch gefiltert jedes Mal).

Beitrittstest (§3, dedizierter Server):

```sh
PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_CAPTURE=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=capture PROPWASH_JOINTEST_CAPTURE=1 \
  PROPWASH_JOINTEST_EXPECT='joined|capture ok' JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
# danach jointest.stop in fabric/runs/smoke-server; dasselbe mit :neoforge:
```

`dev.CaptureJoinCheck` (Log `[Beitrittstest] Kamera-Chunks: …`) setzt 60 Ticks nach dem Beitritt eine eigene Drohne mit
Marker 300 Blöcke weg und eine zweite neben den Spieler und meldet die Ids im Chat (`[capture] server spawned …`).
`capture.client.CaptureJoinProbe` meldet beide über die Client-API an (Radius 3 und 2), wartet auf alle Chunks, Drohne
und Marker, prüft eigene Ansicht und Hauptbild und meldet die weite Lage wieder ab; Ergebnis
`capture ok far=301 chunks=81 loaded=… freed=… storage=65->19 vanilla=19 near=2`. Der Client verlässt den Server mit der
nahen Lage; der Server prüft, dass die weite Lage nach dem Abmelden aus der Ansicht ist und das Trennen die nahe Lage
beendet (danach 0 Sitzungen, 0 Lagen, 0 Tickets), und meldet beim Stopp `PASS` oder `FAIL Kamera-Chunks: …`. Das
Ergebnis steht, sobald nach dem Trennen dieser saubere Stand erreicht ist (meist im selben Tick, spätestens nach
`LEAVE_CHECK` = 40 Ticks); ein `jointest.stop` direkt nach dem Client wartet bis zu 10 s darauf
(`ServerOnlyCheck#pending`, Log „Stopp … ms aufgeschoben“). Vorher lief die Prüfung fest 40 Ticks nach dem Trennen, und
ein Stopp direkt nach dem Client endete mit `FAIL Kamera-Chunks: Trennen nicht geprüft` (Befund WA-W2d-3, JUnit
`dev.CaptureJoinStopTest`).
Der Client meldet die beiden Lagen erst `CaptureJoinProbe.REQUEST_DELAY` = 100 Ticks nach der Chat-Zeile an, wie ein
langsamer Client nach dem Beitritt. Bis zum Abmelden hält der Server die weite Drohne mit Marker alle 20 Ticks mit einem
Ticket geladen (`CaptureJoinCheck.holdRadius`, Radius 2 wie beim Setzen, nach dem Abmelden Radius 1 bis zum Entfernen).
Vorher lief das einzige Ticket vom Setzen nach 40 Ticks ab. Kam die Anfrage später, war die Drohne am Server schon
entladen, und der Test endete mit `capture refused far=NOT_A_DRONE near=OPENED` und
`FAIL Kamera-Chunks: Lage nie vollständig gesendet – … 0 Chunks gesendet` (Befund WA-W2e-4, JUnit
`dev.CaptureJoinHoldTest`). Das Produkt verhält sich dabei richtig: Eine Drohne, die am Server nicht geladen ist,
bekommt keine Lage.
Ist am Rechner eine Funke angeschlossen, fragt der Client des Beitrittstests nach ihrer Einrichtung; solange das Fenster
offen ist, gilt er nicht als beigetreten und läuft in die Zeitgrenze. Abhilfe ohne Codeänderung: die GUID der Funke (Log
„frage nach Einrichtung von …“) in `neverAsk` von `<loader>/runs/smoke-client/config/propwash/input.json` eintragen.

### Stehendes Wasser im Kessel (`drone: Kessel`, Befund CAULD2-1)

`docs/drone-cauldrons.md` §1: Wind und Böen tragen eine Drohne im Wasser nicht mehr mit. Vier Schritte aus
`drone.client.CauldronDriftSelfTests` laufen mit `'-PpropwashSelftestOnly=drone: Kessel'` nach „Füllhöhe“ und vor dem
Abbau (Testfeld und Whoop von `CauldronSelfTests`, rund 25 s). Je Schritt wird die Whoop auf Stein scharf geschaltet,
erst in den Block (Wasser, Lava, Pulverschnee in der Steinmulde), dann in den Kessel gesetzt (Wasser und Lava 0,25 m
über dem Boden, Pulverschnee 0,5 m) und 50 Ticks ohne Knüppel beobachtet: seitlich höchstens 0,05 m/s (Fenster 5 Ticks
ab dem 10. Tick) und Versatz höchstens 0,05 m/s × Dauer + 2 cm. Wasserdicht im Wasser und im Pulverschnee bleibt sie
scharf, dann trägt Vollgas sie in höchstens 100 Ticks 1,5 m über den Block; ohne Wasserschutz und in Lava geht sie mit
`FLUID` aus. Das Log nennt je Ort Drift, Versatz, Wind am Ort (Kessel offen rund 0,79 m/s, Block windgeschützt
0,20 m/s) und die Ticks bis heraus. Ohne den Fix scheitern beide Wasser-Schritte: „Wasserblock (wasserdicht): Vollgas
trägt die Drohne nicht heraus“ und „Wasserkessel treibt seitlich ohne Knüppel … Versatz 0.351 m in 2.5 s“; Lava und
Pulverschnee bestehen auch ohne Fix. JUnit: `tools/fastcheck.sh test --select-class
de.lardtom.propwash.sim.CauldronStillWaterTest` (ohne den Fix seitlich 0,84 m/s wasserdicht und 0,85 m/s ohne
Wasserschutz).

### Funke im Flug anstecken (`input: Hotplug`, Befund HOTPLUG-1)

`docs/input-hotplug.md`: Ein im FPV-Flug angestecktes Gerät übernimmt ohne Verlassen der Ansicht. Drei Schritte aus
`input.client.HotplugSelfTests` mit `'-PpropwashSelftestOnly=input: Hotplug'` (je rund 50 Ticks, Fabric und NeoForge). Sie
stecken virtuelle SDL-Joysticks an (`SDL_AttachVirtualJoystick`): eine Funke mit der EdgeTX-USB-Kennung 1209:4f54 und
gespeicherter, eingerichteter Belegung (Gas umgekehrt) und im ersten Schritt vor dem Flug einen nicht eingerichteten
USB-Joystick mit vier Achsen in Ruhe (Gas 0,5, Sperre `THROTTLE_NOT_LOW` wie „THROTTLE NOT LOW“ im Discord-Bild). Ablauf: Flugstart in FPV
mit dem Joystick bzw. der Tastatur, Funke anstecken → in höchstens 20 Ticks offen und Quelle des Flugs, Gas 0,250 aus der
gespeicherten Belegung (Standard wäre 0,750), Meldung „Funke verbunden“ mit „(Profil geladen)“ sichtbar; abziehen → in
höchstens 20 Ticks kein Gerät offen, Verbindung 0, Sperre `FAILSAFE`, Meldung „Funke getrennt“, 20 Ticks lang kein
Rückfall auf den Joystick; wieder anstecken → die Funke fliegt weiter. Das Log nennt die Ticks bis zur Übernahme (Fabric
5 bzw. 1). Ohne den Fix scheitert der erste Schritt mit „Funke im Flug nach 21 Ticks nicht übernommen: … offen „Propwash
Hotplug USB Joystick“ … Sperren 2“; der Tastatur-Schritt besteht auch ohne Fix. Ist am Rechner ein echtes Gerät
angesteckt, überspringen alle Schritte mit Vermerk im Log. JUnit: `tools/fastcheck.sh test --select-class
de.lardtom.propwash.input.DeviceHotplugTest` (ohne den Fix 3 von 10 Fällen rot).

Der dritte Schritt (Befund AUTO27-1, rund 75 Ticks) prüft, dass ein im Flug angestecktes Gerät den Flug nur beim
Anstecken bekommen kann: Tastaturflug, Funke anstecken (übernimmt), dann den nicht eingerichteten Joystick anstecken →
die Funke behält den Flug 20 Ticks lang; Funke abziehen → Failsafe, Meldung „Funke getrennt“, 20 Ticks kein Rückfall auf
den weiter angesteckten Joystick; Funke wieder anstecken → sie fliegt weiter. Ohne den Fix scheitert er mit „Funke im Flug
abgezogen: nach 1 Ticks fliegt „Propwash Hotplug USB Joystick“ weiter statt Failsafe, obwohl der Joystick beim Abziehen
schon angesteckt war: … Verbindung verloren false, Sperren 2“.

### Kreativ-Drohne im Sharecode (`kreativ-drohne: Editor → PW3-Code → Import`, Befund PWD3-1)

`docs/sharecode-ui.md` §8: Der Drohnen-Editor kopiert PW3 mit der Ebene Kreativ-Werte. Ein Schritt in
`creative.client.CreativeSelfTests`, mit `'-PpropwashSelftestOnly=kreativ-drohne:'` (die Gruppe hat damit 7 Schritte; mit
`share:,ui-scale:` zusammen läuft er im Strang pw3-ui), Fabric und NeoForge, rund 10 Ticks. Ablauf: Kreativ-Drohne mit
Werten in die Hand (Kreativmodus), Editor öffnen, zwei Werte im Entwurf ändern, `shareCode()` muss `PW3-…` mit Ebene
`CREATIVE`, genau dem Entwurf und dem Kreativ-FC im Bauplan ergeben; im Editor wieder einfügen (gleicher Entwurf), alten
PWD1-Code einfügen (dessen Werte), PW3 ohne Kreativ-Werte muss abgelehnt werden. Dann Tab „Sharecode“: Zeile
`creative` da, `creative:survival` nicht; „Drohne erzeugen“ muss über `propwash:share_creative_create` eine zweite Drohne
mit genau den geteilten Werten ins Inventar legen (Server prüft). Danach Überlebensmodus: `creative:survival` muss
erscheinen, ein PWD1-Code im Tab liest sich als alter Code (`pwd1` und „Als PW3 kopieren“), die Umwandlung ergibt den
PWD1-Bauplan mit denselben Werten; `/propwash code import <code>` im Überleben darf die Kreativ-Werte der Drohne in der
Hand nicht ändern und keine Drohne erzeugen. Das Log nennt Länge und Text des PW3-Codes. Ohne den Fix schrieb der
Editor PWD1, der Schritt bräche am ersten Prüfpunkt ab („Editor kopiert keinen PW3-Code: PWD1-…“); der rote Test vor dem
Fix war JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.share.CreativeShareTest` (erste Fassung 2 von 2
rot, PWD1 wurde `OTHER_CODE`).

### Vorschau-Build (`preview:`, Befunde PREVMARK-1, PREVMARK2-1 und PREVMARK3-1)

`docs/preview-build.md`: Mit `-Ppropwash_preview=true` zeigt Propwash immer „Propwash Preview <Version> · not final“,
gezeichnet mit eigenem Shader aus dem Code, und das Fenster heißt „Propwash Preview <Version>“; der Schalter steckt als
Konstante `PreviewSwitch.ENABLED` im Code. Der Selbsttest bekommt den Schalter als
`propwash.selftest.preview` und prüft beide Richtungen; deshalb zwei Läufe je Loader (je etwa eine Minecraft-Minute; mit
Slot-Pools das Slot-Skript davor, §6):

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=preview:' -Ppropwash_preview=true -PpropwashScreenshots=true
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :neoforge:runClientSelftest '-PpropwashSelftestOnly=preview:' -Ppropwash_preview=true
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=preview:'
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :neoforge:runClientSelftest '-PpropwashSelftestOnly=preview:'
```

- „Build-Schalter, erzeugte Konstante und Kennzeichen im Jar stimmen überein“: `PreviewBuild.enabled()` muss dem
  Schalter entsprechen, das Kennzeichen `META-INF/propwash-build.properties` (nur zur Kennzeichnung) der Konstante und
  der Text die Mod-Version nennen.
- „Fenstertitel nennt den Vorschau-Build“: liest den Titel des Spielfensters (`SDL_GetWindowTitle`), vor und nach
  `Minecraft#updateTitle`; mit Schalter genau „Propwash Preview <Mod-Version>“, ohne Schalter ohne „Propwash Preview“.
  Die Szene im Titelbildschirm prüft den Titel nach dem Verlassen der Welt noch einmal.
- „im Spiel über dem HUD“, „bei ausgeblendetem HUD (F1)“, „im Pausenmenü“ und nach dem Verlassen „im Titelbildschirm“:
  je 20 Ticks. Mit Schalter muss der Aufruf aus `Gui#extractRenderState` laufen (sonst „Mixin nicht aktiv?“) und das
  Wasserzeichen im Bild stehen: Ein Bildschirmfoto des Hauptbilds wird an der festen Stelle (`PreviewStamp#place`)
  Schriftpunkt für Schriftpunkt mit der Pixelschrift (`PreviewFont`) verglichen; jeder Schriftpunkt muss hell sein (alle
  Farbkanäle ab 170), jeder Schattenpunkt dunkel (höchstens 90). In jeder Szene muss der GUI-Renderer die eigene
  Pipeline `PreviewPipeline.PIPELINE` abgefragt haben und `RenderSystem#getCompiledPipeline` für sie die mit dem Shader
  aus dem Code übersetzte liefern. Ohne Schalter darf weder das Wasserzeichen noch die Pipeline je aufgerufen werden.
  Mit Bildschirmfotos und Schalter: `docs/screenshots/preview_ingame.png`, `preview_ingame_f1.png`,
  `preview_menu.png`, `preview_title.png`.
- „in Action-Cam-Bild und Foto“: stempelt ein graues I420-Bild 1280 × 720 und ein Foto 640 × 360 im Spiel; mit
  Schalter müssen sich Bildpunkte unten rechts ändern, ohne keiner.
- „Ressourcenpaket überschreibt alle Propwash-Texte, leert die Standardschrift und ersetzt den GUI-Shader“: legt im
  Ordner `resourcepacks` das Paket `propwash-selftest-preview` an (jeder Schlüssel aller Propwash-Sprachdateien leer,
  `ascii.png`, `accented.png` und `nonlatin_european.png` der Minecraft-Schrift durchsichtig,
  `assets/minecraft/shaders/core/gui.fsh` verwirft Weiß und Schwarz mit Alpha 180/255, also genau die Farben des
  Wasserzeichens, und `assets/propwash/shaders/core/preview_mark.vsh`/`.fsh` verwerfen alles), wählt es aus und lädt
  die Ressourcen neu; geprüft wird, dass ein Propwash-Text leer, „Propwash Preview“ in der Minecraft-Schrift fast ohne
  Breite ist und die drei Shader aus dem Paket kommen. „Wasserzeichen bleibt mit diesem Ressourcenpaket unverändert“
  macht dieselbe Pixelprüfung im Spiel; dazu zeichnet eine HUD-Ebene des Selbsttests oben links eine graue Probe-Fläche
  mit einer Mitte in der Schriftfarbe des Wasserzeichens über die Vanilla-GUI-Pipeline, deren Mitte grau bleiben muss
  (Kanäle 100 bis 150, sonst wirkt der Paket-Shader nicht). Bildschirmfoto `preview_resourcepack.png`.
  „Ressourcenpaket wieder entfernt“ wählt es ab, lädt neu, löscht den Ordner und prüft Text und Schrift. Vor PREVMARK2-1
  (Wasserzeichen in der Minecraft-Schrift) waren auf Fabric 5 von 9 Schritten rot, mit dem Paket „Schrift 0/392 hell“;
  vor PREVMARK3-1 (Wasserzeichen mit dem Vanilla-Shader `core/gui`) 3 von 10: Fenstertitel im Spiel und im
  Titelbildschirm „Minecraft* 26.3 …“, mit dem Shader-Paket „Schrift 0/392 hell“ bei Probe `808080`.
- Ohne Schalter dürfen bestehende Bildschirmfotos und Goldens nicht abweichen (das Wasserzeichen fehlt dort ganz).

JUnit: `tools/fastcheck.sh test --select-package de.lardtom.propwash.preview` (28 Fälle; gegen den Stand vor PREVMARK2-1
`PreviewHardcodedTest` 8 von 8 rot, gegen den Stand vor PREVMARK3-1 6 von 28: zwei in `PreviewHardcodedTest`, drei in
`PreviewPipelineTest`, einer in `PreviewBuildTest`) und über Gradle
`./gradlew :common:test --tests 'de.lardtom.propwash.preview.*' -Ppropwash_preview=true` (und ohne Schalter): mit
Schalter prüft `PreviewHardcodedTest`, dass die Mixins Wasserzeichen, eigene Pipeline und Fenstertitel aufrufen, ohne
Schalter, dass ihre Handler und die Einstiege von `PreviewMark` leer sind und keine Klasse außerhalb von `preview` darauf
verweist; in beiden Fällen, dass `PreviewMark` nur mit `PreviewPipeline.PIPELINE` zeichnet. `PreviewPipelineTest`
prüft, dass beide Shader der Pipeline aus dem Code kommen (ohne `#include`, Fragment ohne `discard` und Uniforms) und
die Quelle für nichts anderes antwortet.

### Recyceln in der Werkbank (`zerlegen:`, `recyceln:`; 0.6.0, Befund RECYCLE-1)

Aufruf: `-PpropwashSelftestOnly=zerlegen:,recyceln:` auf Fabric und NeoForge, Bildschirmfotos nur Fabric mit
`-PpropwashScreenshots=true '-PpropwashSelftestOnly=bildschirmfotos: Werkbank Recyceln'`, Layout mit
`-PpropwashSelftestOnly=ui-scale: -PpropwashUiScaleCases=Werkbank`.

- `zerlegen:` (2): Das Zerlegen-Feld lehnt beschädigte, kaputte und neue Teile ab (Motor, Akku, Rahmen, Videoeinheit,
  Prop, Zubehör, Add-on-Motor, Motor im Format von 0.5.2, Teil eines fehlenden Add-ons, Erde): Das Teil bleibt an der
  Maus, es gibt keine Materialien. Eine Drohne wird zerlegt. Umschalt-Klick legt einen kaputten und einen Motor mit
  30 % auf den Motor-Platz, eine Drohne ins Zerlegen-Feld. Der Hinweis am Feld nennt den Tab Recyceln. Vor dem Fix war
  der erste Schritt auf Fabric rot (acht beschädigte oder kaputte Teile im Feld recycelt, Umschalt-Klick recycelte den
  kaputten Motor).
- `recyceln:` (8): Stufen und Materialien je Fall (neuer Motor, zwei Motoren mit 60 %, kaputter Motor, vier neue und
  ein kaputter Prop, Akku mit 30 %, Rahmen mit 70 %, kaputter Whoop-Rahmen) gegen das Rezept aus dem `RecipeManager`;
  Einlegen recycelt nichts, erst der Knopf, Mengen zwischen `least` und `most`; Kreativ-FC (ohne Rezept, `none`) und
  fehlendes Add-on mit eigenem Status und ohne Wirkung des Knopfs; volle Ausgabe (nichts), fast volle Ausgabe (genau
  2 von 16 Motoren), Schließen gibt Eingang und Ausgabe zurück; inaktive Plätze und Umschalt-Klick je Tab, auch ein
  Klick auf einen inaktiven Bauplatz; Rezeptwahl (erstes nach Rezept-ID, Rezept mit dem Teil als Zutat zählt nicht, Tag-Zutat als erstes Item); alle Teile der geladenen Add-ons (je Namensraum gezählt, mit Rezept oder
  Rückfall, im Log „recyceln-Selbsttest: …“); im echten Screen Tab-Wechsel per Paket (Server prüft den Tab),
  Vorschau nach dem Einlegen, Knopf, Rückweg in den Tab Bauen; Texte beider Sprachen.
- `recyceln: Drohne im Tab Recyceln …` (Befund AUTO32-1): echter Umschalt-Klick (`mouseClicked` mit Umschalt) auf eine
  Drohne im Inventar bei offenem Tab Recyceln. Die Drohne bleibt auf Client und Server an ihrem Platz, die Statuszeile
  zeigt `recycle.drone` in Warnfarbe. `checkTabs` prüft dasselbe am Server-Menü. Der Text-Schritt prüft
  `RecycleView.inputLines` (Drohne an der Maus: Titel, `not_a_part`, `drone`) und dass der Satz den Tab Bauen nennt.
  Vor dem Fix waren auf Fabric drei Schritte rot: Die Drohne wanderte ins Hotbar, die Statuszeile blieb bei „Nothing
  is recycled until you press Recycle.“, und der Schlüssel fehlte.
- Bildschirmfotos: `workbench_recycle` (hell), `workbench_recycle_night` (dunkel, kaputter Motor),
  `workbench_recycle_gui2`, `workbench_recycle_gui4`, `workbench_recycle_de`, `workbench_build_tab_de`.
- ui-scale: Fälle „Werkbank Recyceln“ (Motor 60 %), „Werkbank Recyceln leer“ und „Werkbank Recyceln Props“ (mit
  Reparatur-Hinweis) in allen Fenstern und Skalen, englisch und deutsch; „Werkbank“ prüft die Tabs über dem Fenster.
- `recyceln: Spieler weit weg von der Stelle der Werkbank …` und `recyceln: Spieler zurück …` (Befund W2E-3, damit 10
  Schritte): Die Werkbank-Schritte und Bildschirmfoto-Szenen setzen die Werkbank an die Basis der Szenen
  (`ShowcaseTour.base()` plus zwei Blöcke). Im vollen Lauf steht der Spieler nach `capture:` weit davon weg; der Server
  schloss das Menü im nächsten Tick (Reichweite wie in Vanilla), und sieben Schritte waren rot mit „Werkbank-Screen
  fehlt: null“, gefiltert grün. Der erste Schritt versetzt den Spieler 48 Blöcke neben die Stelle, damit das auch
  gefiltert so ist (vorher auf Fabric zwei Schritte rot). Erreicht der Spieler die Werkbank nicht, stellen die Schritte
  ihn an die Basis und danach zurück; die Szenen warten auf den Werkbank-Block beim Client.

JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.content.RecyclingTest` (8 Fälle; liest alle
Kern-Rezepte aus `data/propwash/recipe`).

### Feinschritt und Drehen (`race: Feinschritt und Drehen`, Befund PATTY-3)

`docs/race-system.md` §6 und §6a: fünf Schritte aus `race.client.GateTurnSelfTests` mit
`'-PpropwashSelftestOnly=race: Feinschritt und Drehen'` (zusammen rund 35 Ticks, Fabric und NeoForge, auch in `race:`
enthalten). Aufbau: Gate, Gate-Stange und ein liegendes Freiform-Gate über einer 3 × 2-Öffnung in einer Platte. Geprüft
wird: (1) Das Menü ohne freie Maus zeigt links neben sich den Tooltip der gewählten Zeile (Gieren, X, Schritt; Gate und
Gate-Stange), samt Strg-Schritt „1°“ bzw. „1/16“ und „1/32“, alles im Fenster. (2) Auf der Seite Position schieben
Strg+Umschalt+Bild↑, Strg+Bild↑, Bild↑ und Strg+Umschalt+Bild↓ auf 1/32, 1/16, 1/4 und 0,21875 Block; die Anzeige zeigt
„+0.21875“, Übernehmen setzt das Gate beim Client und am Server genau so hoch. (3) Das Modell `gate/piece/pole_foot` aus
dem geladenen Ressourcenpaket ist nach jeder Vierteldrehung gleich (`ModelSymmetry`, wie JUnit `GatePoleFootTest`), die
Stange kommt beim Client bei 0°, 90°, 180° und 270° an. (4) Die Zeile „Ausrichtung“ des liegenden Freiform-Gates dreht den
Redstone-Ausgang: rote Markierung, Server und Client zeigen bei 0°/90°/180°/270° den Nord-, Ost-, Süd- und Westrand,
Speichern und Laden behält 270°. Mit `-PpropwashScreenshots=true` (nur Fabric) und dem Bereich
`bildschirmfotos: Feinschritt und Drehen` entstehen `race_turn_pole_<0|90|180|270>`,
`race_turn_freeform_output_<0|90|180|270>` (ohne HUD, rote Markierung), `race_turn_freeform_menu` und
`race_turn_menu_tooltip`. Ohne den Fix scheitern die JUnit-Tests `GateEditModelTest` (1/32 rastet auf 1/16, keine Zeile
Ausrichtung), `GateEditTextsTest` (kein Strg-Schritt beschriftet), `GatePoleFootTest` (10 Flächen ohne Gegenstück) und
`GateSignalTargetTest` (bei 90° Nordrand statt Ostrand).

### Kurs-Einstellungen (`course-menu:`, race-b; 0.6.0, Befund COURSEMENU-1)

Doku: `docs/race-course-menu.md`. Aufruf: `-PpropwashSelftestOnly=course-menu:` auf Fabric und NeoForge,
Bildschirmfotos nur Fabric mit `-PpropwashScreenshots=true`, Layout mit `-PpropwashSelftestOnly=ui-scale:
'-PpropwashUiScaleCases=Kurs-Einstellungen,Kurs bearbeiten'
-PpropwashUiScaleWindows=854x480@2,1920x1080@2,1920x1080@3,1920x1080@4,640x480@2,800x600@2` (die Zwischenfenster 768×480
und 912×512 prüfen nur Fälle mit `between()`; mit dieser Auswahl fänden sie keine Ansicht und meldeten das als Fehler).
Ergebnis 0.6.0: in allen sechs GUI-Größen (320×240 bis 960×540) englisch und deutsch 0 Befunde in 4 Ansichten.

- JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.race.CourseMenuTest` (6 Fälle; der sechste prüft
  die Zahl der Zeiten im Menü gegen das Bestenlisten-Paket mit mehr als zehn Zeiten je Klasse, Befund AUTO31-2).
- Selbsttests, die die Bestenliste brauchen (`flughilfe:`, Bilder `leaderboard_assisted` und `leaderboard_course_edit`),
  schicken die Daten mit `RaceServer.sendLeaderboard(…, false)` und öffnen `LeaderboardScreen` im Client; `open = true`
  kommt vom Kurs-Werkzeug und öffnet die Kurs-Einstellungen (Befund AUTO31-1; vorher Fabric und NeoForge rot mit
  „Fenster=false Zeilen=0“ bzw. „Bestenliste … öffnet nicht“).
- `course-menu:` (11): Kurs aus drei Gates mit drei Zeiten; Kurs-Werkzeug mit Kurs in der Hand, Rechtsklick in die Luft
  öffnet die Kurs-Einstellungen (vor dem Fix: Bestenliste, Schritt rot; ebenso fehlte der Knopf „Einstellungen“ im
  Kurs-Fenster); vier Gruppen und alle Zeilen ohne Layout-Befund; Name (getippt, Enter), Runden (+), Typ Punkt zu Punkt
  und zurück, ID kopieren – der Server übernimmt es; Kurs-Fenster → „Einstellungen“ → zurück; Menü ↔ Kurs-Fenster ↔
  Bestenliste ohne Stapeln, alter Knopf „Kurs bearbeiten“, `/propwash race laps|name` erscheinen im offenen Menü;
  320×240: Mausrad, Ende, Pos1, Bild ab, Scrollbalken ziehen (`LayoutProbe.scrollbars`), keine Befunde; Abenteuermodus:
  schreibgeschützt, Tippen, Klicks und Menü-Aufrufe ändern nichts am Server; „Alle Zeiten löschen“ und „Kurs löschen“
  erst beim zweiten Klick, Gates bleiben stehen.
- Bildschirmfotos: `course_menu` (hell), `course_menu_night` (dunkel), `course_menu_gui2`, `course_menu_gui4`,
  `course_menu_view` (nur ansehen), `course_menu_de`, `course_menu_view_de`.
- Beitrittstest (§3):

  ```sh
  PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_COURSE_MENU=1 JAVA_HOME=… <slot-skript> ./gradlew :fabric:runServerSmoke
  PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=coursemenu PROPWASH_JOINTEST_COURSE_MENU=1 \
    PROPWASH_JOINTEST_EXPECT='joined|course_menu_tool=yes|course_menu_b_read_only=yes|course_menu_b_denied=yes|course_menu_op_edit=yes|course_menu=ok' \
    JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSmoke
  # danach jointest.stop in fabric/runs/smoke-server
  ```

  `dev.CourseMenuJoinCheck` (Log `[Beitrittstest] course-menu: …`) legt 60 Ticks nach dem Beitritt mit `PwMenuOwner`
  (am Server) den Kurs „Menü Beitritt“ (3 Gates, 3 Runden) an und gibt dem Gast ein Kurs-Werkzeug darauf. Der Gast
  (`race.client.CourseMenuJoinProbe`) öffnet mit Rechtsklick in die Luft die Kurs-Einstellungen, prüft sie
  schreibgeschützt (nur ID kopieren, Reihenfolge ansehen und Bestenliste offen), tippt, klickt alle gesperrten Knöpfe,
  ruft die Menü-Methoden auf und schickt rohe Pakete (Name, Runden, Typ, Löschen) und die Befehle `name` und `reset`;
  nach 6 s ist der Kurs unverändert. 30 s nach dem Beitritt wird er Operator, ändert den Namen im Menü auf „Vom
  Operator“ und verliert das Recht wieder. Auf NeoForge ebenso mit `:neoforge:`.

### Feinschritt und Drehen – Nachtrag (Befunde AUTO30-1 bis AUTO30-3, race-a)

`race.client.GateTurnSelfTests` hat drei Schritte mehr (zusammen rund 15 Ticks, Fabric und NeoForge, auch in `race:`
enthalten), Aufruf wie oben mit `'-PpropwashSelftestOnly=race: Feinschritt und Drehen'`:

- **Fußzeile des Freiform-Menüs** (AUTO30-2): für die Zeilen Flugrichtung, Ausrichtung, Rand und die drei Zeilen der
  Seite Redstone zeigt die Anzeige ohne Maus genau den passenden Hinweis („Mausrad oder R: Flugrichtung umkehren“,
  „Mausrad: um 90° drehen · R: …“, „Mausrad: umschalten · R: …“, „Mausrad: Wert ändern · Strg: 1 Tick“) und „Zeile
  wählen“, nichts davon unten aus dem Menü, ohne Layout-Befund an der Fußzeile; das Menü passt bei 480 × 270 und
  320 × 240, ist bei 854 × 480 ungedrängt und für jede Zeile der ersten Seite gleich hoch; die Hinweise passen englisch
  und deutsch höchstens zweizeilig und ohne Abschneiden ins Menü; Mausrad auf „Ausrichtung“ dreht um 90°.
- **Setzen aus der Blickrichtung** (AUTO30-3): auf einer zweiten 3 × 2-Öffnung setzt das Freiform-Gate-Item mit Blick nach
  Süden, Westen, Norden und Osten (Blick 60° nach unten, Kopfdrehung mitgesetzt) je ein liegendes Gate mit 0°, 90°, 180°
  und 270°; der Ausgang liegt am Nord-, Ost-, Süd- bzw. Westrand, also auf der Seite des Spielers.
- **Ausrichtung im Kurs** (AUTO30-1): das liegende Freiform-Gate kommt mit Bestzeit und Rekord-Geist in einen Kurs;
  „Ausrichtung“ 0° → 90° über das Menü lässt Layout, Archiv, Bestzeit, Rekord-Geist und Layout-Prüfsumme unverändert, die
  Kursdaten tragen 90°; danach archiviert „Flugrichtung umkehren“ wie bisher (Layout + 1, Archiv 1, Zeit und Geist weg).

JUnit: `FreeformHeadingLayoutTest` (Prüfsumme bei 0°/90°/180°/270° gleich, Umkehren und neue Öffnung ändern sie, andere
Gates unverändert), `FreeformPlaceFacingTest` (Blick → Vierteldrehung, Plan → Ausgang auf der Seite des Spielers für alle
vier Richtungen) und `GateEditTextsTest#freeformFooterFollowsTheSelectedRowAndPage`. Ohne die Fixes scheitern
`FreeformHeadingLayoutTest` (bei 90° andere Prüfsumme), `GateEditTextsTest` (Ausrichtung zeigt „Flugrichtung umkehren“)
sowie die Selbsttest-Schritte Fußzeile und Ausrichtung im Kurs („Layout 1 → 2, Archiv 1“); `FreeformPlaceFacingTest`
scheitert, wenn der Blick gespiegelt wird (Blick West ergibt dann 270°).

### VTX-Antennen hinten am Rahmen (`antenne:`, Befunde ANTENNA-1, ANTENNA3-1 und ANTENNA4-1)

`-PpropwashSelftestOnly=antenne:` auf Fabric und NeoForge, Bildschirmfotos nur Fabric mit `-PpropwashScreenshots=true`.
Die Schritte laufen über alle geladenen Rahmen und VTX; mit Just More Parts in `runs/selftest-client/mods/` (eine
Run-Kennung spiegelt `mods/` aus dem Basis-Ordner) prüfen sie also auch die Rahmen mit Rückfall-Halter und die
VTX-Modelle des Add-ons.

- **Antennen aller VTX hinten an jedem Rahmen, abgeleitet aus den VTX-Daten**: jeder Kern-Rahmen hat einen eigenen
  Antennenhalter; für jede Kombination aus Rahmen und VTX sitzt die Antennen-Form am Halter
  (`DroneLayout#antenna(frame)`, bei einem weiter nach hinten ragenden Akku 5 mm hinter ihm), ist höchstens
  1,2 × Radstand lang, ragt mindestens 15 mm bzw. 60 % ihrer Länge × cos(Neigung) über den Halter und 6 mm nach hinten,
  nicht höher als ihre Länge, die Hülle reicht bis zur Spitze, die Werkbank-Vorschau zeichnet sie, bei 60° Kamerawinkel
  bleibt sie gleich, Lack auf „Antennenspitzen“ färbt sie und der Lack-Platz steht zur Wahl.
- **Antennen frei von Akku, Props, Kamera und Zubehör**: je passendem Akku (mit dessen Fuß-Position) die Punkte jeder
  Antenne (ohne Halter) gegen die gezeichneten Elemente des Akkus (Stecker und Kabel, die das Stromkabel des Stacks
  ausblendet, zählen nicht), die Kamera-Elemente beim Standardwinkel, jedes passende Zubehör (0,5 mm Spiel) und die
  Prop-Kreise des größten passenden Props von 3 mm unter der Nabe bis 2 mm über das Prop-Modell.
- **Antennen 3/4 von hinten und seitlich vor freiem Grund, höchstens 1,2 × Radstand** (Befund ANTENNA4-1): mit dem Akku
  der Bildschirmfotos wird jede Antenne in vier Ansichten projiziert (3/4 von hinten links und rechts mit 25° Neigung wie
  die Werkbank-Vorschau, genau seitlich links und rechts); gezählt wird die Länge von Hülse, Stab und Kappe, die nicht
  vor oder hinter dem Umriss des Akkus liegt. Kern-Rahmen: seitlich je Antenne mindestens 80 %, 3/4 von hinten einzeln
  75 %, im Paar je 35 %; Add-on-Rahmen 80 % bzw. 40 %. Der Schritt meldet die sechs knappsten Add-on-Kombinationen.
- **Antennen-Elemente der Add-on-VTX ausgelassen**: Kern-VTX-Modelle haben keine Elemente `antenna*`; an der Drohne fehlen
  bei VTX-Modellen der Add-ons genau die Flächen ihrer Antennen-Elemente, keine andere, auch in der Lack-Variante, und die
  Drohne benutzt dieses Modell. Eine Fläche, die ganz in einem anderen Element liegt (Rückseite einer Buchse am Sender),
  bleibt und ist unsichtbar; höchstens eine je Antennen-Element. Immer dabei ist das Render-Testteil `propwash_rendertest:hd_pro` (vier Elemente
  `antenna`/`antenna_tip`); mit Just More Parts dazu dessen sieben VTX.
- **Antenne nur von außen, nicht im eigenen FPV-Bild**: je Kern-Preset und VTX; von außen (`DroneVisuals.submit`) sind
  Ecken in Messing (SMA) und TPU (Halter) dabei, im FPV-Zustand der eigenen Drohne (`QuadcopterRenderer.submit` mit
  `hidden`) keine.
- **FPV-Flug mit der eigenen Drohne ohne Antenne im Bild**: echte FPV-Sitzung mit dem Freestyle-Preset (digital_hd);
  `QuadcopterRenderer.extractRenderState` verbirgt die eigene Drohne, `submit` übergibt keine Antennen-Form; mit
  Bildschirmfotos `antenna_fpv_freestyle_5.png`. Danach räumt „FPV-Flug aufräumen“ Sitzung, Brille, Funke und Drohne weg.
- **Bildschirmfotos** `antenna_rear_<rahmen>_light.png` und `_dark.png`: je Kern-Rahmen alle VTX schräg von hinten
  (Gierwinkel 215° der Werkbank-Vorschau) auf hellem und dunklem Grund; mit VTX aus Add-ons heißen sie
  `antenna_rear_<rahmen>_addons_*` (alle VTX, drei Spalten). Nah: `antenna_close_<namensraum>_<rahmen>_light/_dark.png`
  je Kern-Rahmen und je Klasse eines Add-ons (erster Rahmen der Klasse) mit je einem Race-, Lite-, HD- und LR-VTX (ohne
  Add-on nur die ersten drei), oben 3/4 von hinten (Gierwinkel 215°, 15° Neigung), unten genau seitlich (270°, 0°),
  ausgeschnitten auf die hintere Hälfte mit Akku und Antenne, gleicher Maßstab je Zeile. Dazu
  `antenna_frame_<namensraum>_<rahmen>.png` je Rahmen aus Add-ons, sortiert nach Klasse (Spalten = je ein VTX der Klassen
  Whoop, Race, Lite, HD, Long-Range, Untertitel „Rückfall-Halter“ oder „eigener Halter“); im Repo liegen nur die Bilder
  ohne Add-on und die des Beispiel-Add-ons (`propwash_example`), die Bilder mit Just More Parts gehören nicht ins Repo.

JUnit: `build.antenna.AntennaDataTest` (Halter in jedem Kern-Rahmen, JSON-Hin- und Rückweg von `antenna_anchor` und
`antenna`, Ableitung für die Daten der Just-More-Parts-VTX, Längen je Klasse – Whoop 30–35 mm, Race und Lite 45–60 mm,
HD 2 × 85–100 mm, Long-Range 120–150 mm –, höchstens 1,2 × Radstand an kleinen Rahmen, schmaleres V an kleinen offenen
Rahmen, Vorgabe geht vor, alte Konstruktoren, Rückfall 12 mm hinter den gemessenen Top-Plates der Just-More-Parts-Rahmen)
und `VtxAntennaAssetsTest` (Kern-Modelle ohne Antennen und ohne `_antenna`-Dateien, Maße und Neigung je Kern-VTX, Fuß
hinter der Deckplatte mit Halter bis an die Platte, hinter der Rahmenkante und 5 mm hinter jedem passenden Kern-Akku,
Rückfall und Verschieben per `drone_layout.json`, Sichtbarkeit 3/4 von hinten und seitlich wie im Selbsttest
(`AntennaVisibility`) für alle Kern-Rahmen, passenden Kern-Akkus, Kern-VTX und die Daten der sieben Just-More-Parts-VTX,
Länge und Spreizung, Freiraum jeder Kern-Kombination gegen Akkus samt Stecker, Prop-Kreise, Kamera bei 0–80°, Sender,
Zubehör samt Sockel und Rahmen, Auslassen gedrehter Antennen-Elemente). Ohne den Fix ANTENNA4-1 scheitern 3 der 9 Tests
von `VtxAntennaAssetsTest` aus dem ersten Commit (68 von 121 Kombinationen verdeckt, Halter vor der Rahmenkante bzw. im
Akku, Whoop 65 mit Antennen bis 150 mm, V mit 50°).

Messung (Befund ANTENNA3-1, 30.10.): Fabric `antenne:` mit Bildschirmfotos 29 von 29 PASS (32 Kombinationen aus 8 Rahmen,
davon 3 des Beispiel-Add-ons mit Rückfall-Halter, und 4 VTX); mit Just More Parts 1.2.0-dev.1 in `mods/` auf Fabric und
NeoForge je 14 von 14 PASS (330 Kombinationen aus 30 Rahmen, 25 mit Rückfall-Halter, und 11 VTX; 8 Add-on-VTX ohne ihre
Antennen-Elemente gezeichnet, 11–32 Flächen je Modell ausgelassen). `antenne:`, `drone-render:`, `drone: Bodenkontakt` und
`content: Lackierung` ohne Bildschirmfotos: Fabric und NeoForge je 75 von 75 PASS. Server-Rauchtest beider Loader PASS.
Kalibrierung (mit den Daten von Just More Parts) 6 von 6 Dateien byte-gleich zum Integrationsstand.

Messung (Befund ANTENNA4-1, 30.10.): JUnit `VtxAntennaAssetsTest` 9 von 9 und `AntennaDataTest` 6 von 6 grün (vor dem Fix
3 von 9 rot); Fabric `antenne:` mit Bildschirmfotos ohne Add-on 46 von 46 PASS, mit Just More Parts 1.2.0-rc.1 in `mods/`
82 von 82 PASS (330 Kombinationen aus 30 Rahmen, 25 mit Rückfall-Halter, und 11 VTX; 22 Kombinationen hinter einen
überstehenden Akku geschoben; knappste Add-on-Kombination `cw_25_duct` + `digital_hd` mit 4S 650: seitlich 100 %, 3/4 von
hinten 51 %), NeoForge mit Just More Parts 15 von 15 PASS. `antenne:`, `drone-render:`, `drone: Bodenkontakt` und
`content: Lackierung` ohne Bildschirmfotos: Fabric und NeoForge je 76 von 76 PASS. Server-Rauchtest beider Loader PASS.
Kalibrierung (mit den Daten von Just More Parts) 6 von 6 Dateien byte-gleich zum Integrationsstand.

### Gate-Menü bei kleiner GUI und Rückkehr nach „race: Feinschritt und Drehen“ (Befund WAD-1, race-a)

Aus dem Wellenabschluss W2d: `ui-scale:` war auf beiden Loadern in 7 von 45 Schritten rot, und `flughilfe:` scheiterte
auf NeoForge direkt nach „race: Feinschritt und Drehen“.

- **ui-scale-Fall „Gate-Editor Anzeige“** (`UiScaleCases`): zeigt auf jeder Seite den Start-Hinweis zur freien Maus
  (vorher nur, wenn „race: Feinschritt und Drehen – Aufräumen“ ihn eingeschaltet liegen ließ) und schaltet ihn danach
  wieder aus. Gezielt: `'-PpropwashSelftestOnly=ui-scale: Fenster,ui-scale: Deutsch: Fenster,!ui-scale: Fenster 768x480,!ui-scale: Fenster 912x512,!ui-scale: Deutsch: Fenster 768x480,!ui-scale: Deutsch: Fenster 912x512'`
  mit `'-PpropwashUiScaleCases=Gate-Editor'` (rund 1 min auf Fabric). Vor dem Fix: Tooltip der Zeile Redstone unter dem
  Start-Hinweis (TEXT_OVERLAP, COVERED) bei 427×240, 480×270 und 400×300, Fußzeile der Seite Position gekürzt (CUT) bei
  320×240, 427×240 und auf Deutsch 480×270; danach 0 Befunde in allen zwölf Fenstern.
- **„race: Feinschritt und Drehen – Fußzeile …“** prüft jetzt auch die beiden Zeilen der Seite Position
  (`propwash.race.nudge.hint.move`, `propwash.race.nudge.hint`) englisch und deutsch auf Abschneiden; vorher rot mit
  „Fußzeile … (322 px) passt nicht in das Menü (200 px)“.
- **Aufräumen wartet auf den Ausgangspunkt**: „race: Feinschritt und Drehen – Aufräumen“ endet erst, wenn nach dem
  Rückteleport die Chunks im Umkreis von 3 um den Ausgangspunkt geladen sind und Entities ticken (`CovRaceSite#home`,
  höchstens 500 Ticks, sonst rot mit „Ausgangspunkt nach dem Rückteleport nicht geladen“). „flughilfe: Runden mit
  Flughilfe …“ wartet vor dem Aufbau ebenso auf die Chunks um den Spieler (höchstens 200 Ticks). Vorher setzte
  `flughilfe:` auf NeoForge Gates und Drohne in noch nicht geladene Chunks („abgelehnt: NOT_PILOTING“, danach
  „Abschnitt „Mit Flughilfe“ nicht gezeichnet: … Zeilen=0“). Gezielt: `'-PpropwashSelftestOnly=race: Feinschritt und Drehen,flughilfe:'`
  auf NeoForge.

JUnit: `GateRowTipTest` (Tooltip an seiner Zeile, nach oben gerückt bis über den Start-Hinweis, nie über den Rand,
`NO_ROOM`, wenn er darüber nicht passt) und `GateEditTextsTest` (eigene Zeile fürs Verschieben, Strg-Zeile mit 1/16 und
1/32).

### Lavakessel ohne seitliche Drift – Nachtrag (`drone: Kessel`, Befund WAD-3)

Der Schritt „Lavakessel ohne seitliche Drift“ aus `drone.client.CauldronDriftSelfTests` setzt die Whoop im Lavablock und
im Lavakessel jetzt direkt auf den Boden, nicht mehr 0,25 m darüber. Wasser bleibt bei 0,25 m, Pulverschnee bei 0,5 m.
Zusätzlich prüft der Schritt, dass die entschärfte Drohne in der Messung höchstens 2 cm fällt. Das Log nennt dafür je Ort
„fällt … m“. Die Grenzen für Drift (0,05 m/s) und Versatz bleiben gleich.

Ursache des Ausreißers im vollen Fabric-Lauf von WA-W2d (seitlich 0,058 m/s, Versatz 0,116 m; gefiltert 7 von 7 grün):
Die Drohne fiel in Lava frei auf den Kesselboden und traf mit etwa 1,8 m/s auf. Die Windrichtung hängt von der
Spielzeit ab (`WindModel`). Bei manchen Richtungen kippte die Whoop beim Aufprall (Sperre `TILTED`) und rutschte bis
12 cm, länger als die 10 Ticks Vorlauf vor der Messung. Nachgestellt wurde das mit Spielzeit 45000 (0,054 m/s, 0,116 m)
und mit der Windrichtung 75° zur Drohne (0,060 m/s, 0,117 m).

Ohne den Fix scheitert der Schritt jetzt in jedem Lauf mit „Lavablock: Drohne liegt zu Beginn der Messung nicht am
Boden, sondern fällt 0.24 m hinein“. Mit dem Fix ist er grün: 0,000 m/s, Versatz 0,000 m, fällt 0,000 m. Ein
einmaliger Durchlauf über 16 Spielzeiten (0 bis 45000) und 24 Windrichtungen (alle 15°) zeigte überall 0,000 m/s.
Dieser Durchlauf ist nicht eingecheckt.

### Heimflug auf die Druckplatte im Server-Rauchtest (Befund WAD-6, cov-flight)

Aus dem Wellenabschluss W2d: Der Fabric-Server-Rauchtest war einmal rot mit „Heimflug landet auf Wolle, Slime und
Druckplatte: Landung auf der Holzdruckplatte: Platte aus, Tür zu“, obwohl die Drohne 0,05 Blöcke neben dem Homepoint
gelandet war. Die Prüfung fliegt den Heimflug selbst (ohne `AutonomousReturns`, also ohne dessen Chunk-Tickets) und
verließ sich darauf, dass die Flughilfe-Prüfung die Chunks östlich des Spawns erzwingt. Die Platte drückt die Drohne erst
bei ihrem ersten Server-Tick (`DroneBlockTriggers`); unter Last war der Chunk nach 25 Ticks noch nicht simuliert.

- **Eigene, feste Startlage:** 56 statt 30 Blöcke östlich des Spawns, außerhalb der Chunks anderer Prüfungen.
- **Eigener Chunk:** Die Prüfung erzwingt den Chunk der Platte und den Chunk der gelandeten Drohne selbst, nur solange
  sie läuft, und nur, wenn er nicht schon erzwungen war.
- **Warten nach Zustand:** Erst nach dem ersten Server-Tick der gelandeten Drohne laufen die 25 Ticks bis „Platte
  gedrückt, Tür offen“ (unverändert). Die Zeile nennt, wann die Drohne zum ersten Mal getickt wurde, ihre Server-Ticks und
  ob der Chunk der Platte simuliert wird. Ohne Server-Tick bis Tick 375 ist das ein eigener Befund „Drohne … noch nicht
  vom Server getickt (Chunk der Platte …)“. Der Server-Rauchtest läuft so lange weiter, höchstens bis
  `CovSurfaceServerChecks.LIMIT_TICKS` (400). Sonst endet er wie bisher nach 100 Ticks.

Vor dem Fix war die Prüfung an der neuen Startlage in jedem Lauf rot („Platte aus, Tür zu (Drohne 0 Server-Ticks, Chunk
der Platte nicht simuliert)“). Danach ist sie grün, die Drohne tickt 3 Ticks nach der Landung zum ersten Mal. Im Versuch
war der Chunk 120 Ticks lang nur geladen, nicht simuliert. Die Prüfung wartete, und der Rauchtest war nach 149 Ticks grün.

### Deko-Drohnen mit drehenden Props (`drone-render: Deko-Blur`, Befund RC2-3)

- `drone-render: Deko-Blur: Drohnen-Item mit custom_model_data "blur" zeigt drehende Props in jeder Ansicht`
  (`DroneItemBlurSelfTests`): neun Drohnen-Items (aus der `/give`-Syntax gelesen, Freestyle, Race, lackierter Whoop mit
  gebrochenem Prop 0, derselbe mit entferntem Lack, Add-on-Prop mit und ohne `_blur`-Modell – Testteil
  `propwash_rendertest:p13_still` –, Freestyle ohne `custom_model_data` und mit anderem String) in neun Ansichten
  (`fixed`, `none`, `ground`, `gui`, beide Hände, dritte Person, Kopf, Regal). Je Prop muss `propBlur` genau dann gesetzt
  sein, wenn `blur` gesetzt ist, das Prop ein `_blur`-Modell hat und nicht gebrochen ist; drehende Props zeichnen das
  `_blur`-Modell und reichen durchscheinende Geometrie ein. Vor dem Fix rot („/give … in FIXED: Prop 0 steht still statt
  zu drehen“).
- `drone-render: Deko-Blur: … aufbauen`, `… zeichnen drehende Props in der Welt`, `… aufräumen` (`DroneBlurShowcase`,
  40 Blöcke nordwestlich des Spielers): acht Item-Rahmen, zwei `item_display` aus dem dokumentierten `/summon`, ein
  Boden-Item auf einem Sockel und eine Race-Drohne, die `/item modify … set_custom_model_data` in der Hand auf `blur`
  setzt. Gezählt werden die Zeichnungen mit `blur` je Modus (`DroneItemRenderer.blurredSubmits`): mit leerer Hand müssen
  Item-Rahmen, `item_display` und Boden-Item je mindestens einmal gezeichnet werden, danach mit Blick nach oben nur die
  Hand.
- Bildschirmfotos (Fabric, `bildschirmfotos (Deko-Blur):`): `drone_item_blur` (Wand, `item_display`, Boden-Item),
  `drone_item_blur_display` (`item_display` statisch neben `blur`).
- Aufruf: Fabric `'-PpropwashSelftestOnly=drone-render: Deko-Blur,bildschirmfotos (Deko-Blur)' -PpropwashScreenshots=true`,
  NeoForge `'-PpropwashSelftestOnly=drone-render: Deko-Blur'`; JUnit `drone.client.render.DroneItemBlurTest`.

### Propwash im Flashback-Kamerapfad (`video: Action-Cam (Flashback) – Propwash im Kamerapfad wie live`, Befund RC2-5)

Aus der Fix-Sammlung für rc.2: Die Flashback-Action-Cam zeigte gefühlt mehr Propwash als das Live-Bild. Ursache: Der
Kamerapfad hat eine Probe je Bild, `PathResampler.perTick` tastete die Lage aber ohne Tiefpass auf ganze Ticks (20 Hz)
ab. Schwingungen über 10 Hz falteten dabei zu langsamem Schaukeln (Ton-Test bei 144 Bildern pro Sekunde: 1° bei 17 Hz
→ 0,95° bei 3 Hz, bei 19 Hz → 0,94° bei 1 Hz, bei 13,3 Hz → 0,79° bei 6,7 Hz). Jetzt mittelt `perTick` die Drehung um
jeden Tick mit einem Lanczos-Fenster über ±3 Ticks (`design/flashback-cam.md`).

JUnit (vor dem Fix rot, danach grün):

```sh
cd <WT> && tools/fastcheck.sh test --select-class de.lardtom.propwash.video.record.path.AntiAliasTest \
  --select-class de.lardtom.propwash.sim.FlashbackPropwashTest
```

- `AntiAliasTest`: 1° bei 13,3–33 Hz ergibt im Export (Regler „Aus“, `FlashbackSpline` zu jeder Bildzeit) höchstens
  0,05° bei der Faltungsfrequenz (vorher „60 fps: 1° bei 13.3 Hz wird im Export 0.76° bei 6.7 Hz“); mehrere
  Propwash-Töne auf drei Achsen über einer langsamen Bewegung: Schein-Wackeln 0,5–3 Hz unter 0,05° (vorher 0,820°,
  Wackeln x1,18); 0,5–3 Hz bei 30, 60 und 144 Bildern pro Sekunde mit und ohne Zittern der Bildzeiten Faktor 0,95–1,01;
  gleichmäßige Drehung exakt; Position und Sichtfeld weiter am Tick abgetastet.
- `FlashbackPropwashTest` (Simulation, Whoop, Freestyle und Racer, 60 und 144 Bilder pro Sekunde, achtmal fallen lassen
  und im eigenen Abwind abfangen): im Propwash Schein-Wackeln 0,5–3 Hz 0,006–0,017° (vorher 0,078–0,232°), Wackeln
  0,5–8 Hz Faktor 0,97–0,99 (vorher 0,94–1,00), Standard-Vereinfachung Faktor 0,99–1,01.
- `CameraTrackTest#resamplesOntoWholeTicks` prüft jetzt die gefilterte Lage (25,18° statt der Punktprobe 26,67°),
  `TrackThinnerTest#liveCounterMatchesSavedTrackEveryTick` den Live-Zähler, der 3 Ticks später zählt.

Selbsttest (Fabric mit `Flashback-0.43.6-for-MC26.3.jar` in `fabric/runs/selftest-client/mods/`):

```sh
cd <WT> && JAVA_HOME=… <slot-skript> ./gradlew :fabric:runClientSelftest \
  '-PpropwashSelftestOnly=video: Action-Cam (Flashback),video: Drohne mit Action-Cam (Flashback),video: Tab Action-Cam passt,video: Flashback-Budget'
```

Der Schritt steigt 18 m, lässt die Drohne dreimal 0,7 s mit wenig Gas fallen, sinkt 1,3 s mit 1,05-fachem Schwebegas im
eigenen Abwind und steigt zurück; Kamerapfad mit Regler „Aus“. Danach vergleicht er Flashbacks eigene Interpolation der
Szene mit 120 Hz gegen den Pfad aus jedem Bild, nur dort, wo Propwash über 0,3 lag (mindestens 1 s), und schreibt die
Kurven nach `propwash-flashback-wobble.csv` im Run-Verzeichnis. Grenzen: Schein-Wackeln 0,5–3 Hz höchstens 0,01°,
Wackeln 0,5–8 Hz Faktor 0,9–1,05. Ohne den Tiefpass lag dieselbe Prüfung bei 0,024° (Zeile „2.8 s im Propwash (Spitze
0.67), Wackeln 0,5–8 Hz live 0.331°, in Flashback 0.317° (x0.96), über 12 Hz live 0.049°, Schein-Wackeln 0.050°, davon
langsam (0,5–3 Hz) 0.024°“). Im Selbsttest-Flug liegt nur wenig Zittern über 12 Hz (0,05–0,07°); in der Simulation mit
Whoop bei 60 Bildern pro Sekunde sind es 0,63° und das langsame Schein-Wackeln war 0,232°.

Messung (02.11., nach dem Rebase auf den Merge von RC2-1): Fabric mit `capture:` 33 von 33 PASS, Zeile „2.0 s im
Propwash (Spitze 0.73), Wackeln 0,5–8 Hz live 0.363°, in Flashback 0.355° (x0.98), über 12 Hz live 0.052°, Schein-Wackeln
0.021°, davon langsam (0,5–3 Hz) 0.001°, tiefster Punkt 8.7 Blöcke unter der Starthöhe“; Flip-Szene beim Anhängen
11,67° (Grenze 25°, vorher 8,86°). Alle `capture:`-Schritte samt den beiden mit Flashback PASS. NeoForge (ohne Flashback,
vor dem Rebase) `video: Action-Cam (Flashback)`, `video: Drohne mit Action-Cam (Flashback)`, `video: Tab Action-Cam
passt`, `video: Flashback-Budget` und `capture:` 24 von 24 PASS. JUnit-Suite 4686 gefunden, 4672 grün, 0 Fehler.
Kalibrierung 6 von 6 Dateien byte-gleich zum Integrationsstand.

### Turtle-Modus an Gate- und Blockkanten (`turtle:`, Befund PTP-3, cov-flight)

Aus dem Playtest-Forum rc.1: Eine Drohne lag kopfüber auf der Kante eines Gate-Rahmens, rollte im Turtle-Modus hin und
her und fiel nicht herunter. Die Bodensonde sah nur Blöcke, Gates nicht. Ohne Untergrund unter den Hüllpunkten hielt
allein die Hitbox die Drohne, ohne Kippmoment und im Turtle-Modus ohne Reibung. Das Flugmodell kannte den Boden außerdem
nur als unendliche Ebene. Jetzt prüft die Bodensonde (`drone.DroneGround`, im Client und im Server-Flug ohne Pilot)
auch Gate-Rahmen und füllt nahe am Boden ein Raster 5 × 5 unter der Hitbox (`SimEnvironment.support`). Liegt der
Schwerpunkt über der Kante hinaus, tragen nur die Kontaktpunkte über dem Untergrund, und die Hitbox stützt am Rand
(`sim.GroundSupport`). Ohne Raster rechnet das Flugmodell wie bisher.

- **JUnit** `sim.TurtleEdgeTest` mit der Testwelt `sim.LedgeWorld` (Blöcke und Gate-Rahmen, Hitbox-Kollision wie
  `BoxWorld`, Bodensonde mit Untergrund-Raster wie im Spiel): kopfüber im Turtle-Modus auf der Kante eines
  Gate-Balkens (5″, Race, Whoop, 7″) und einer Blockkante fällt die Drohne herunter und liegt am Boden still. Mitten
  auf dem Balken dreht Roll sie so schnell um wie am Boden (`TurtleModeTest#flip`, höchstens 0,15 s Abstand). Der
  Schwerpunkt 5 cm vor der Kante bleibt liegen, 6 cm dahinter kippt herunter, auch entschärft und kopfüber. Eine Lücke
  schmaler als die Drohne trägt. Auf flachem Boden rechnen Turtle-Flip und Landung mit und ohne Raster bitgleich. Ohne
  den Fix im Flugmodell sind 3 von 6 rot.
- **Selbsttest** (beide Loader, `'-PpropwashSelftestOnly=turtle:'`, 4 Schritte, rund 20 s nach dem Start):
  `drone.client.CovTurtleEdgeSelfTests` mit drei Schritten neben dem bestehenden Turtle-Schritt am Boden. Kopfüber auf
  der Kante des oberen Balkens eines Gates (MEDIUM) und auf einer Blockkante in 3 m Höhe, Knüppel mittig: Die Drohne
  fällt binnen 60 Ticks mehr als 1 m
  und liegt danach 40 Ticks still. Kopfüber mitten auf dem Gate-Balken: Turtle schaltet scharf, Roll dreht sie auf dem
  Balken um, Motoren aus, sie liegt 40 Ticks still oben. Das Log nennt Balkenrichtung, Knüppel und den Versatz je
  Abschnitt. Ohne den Fix fiel die Drohne an der Gate-Kante erst nach 283 Ticks durch Drift herunter. An der Blockkante
  lag sie still („liegt noch oben, Neigung 179.94, Tempo 0.00“). Mitten auf dem Balken drehte sie sich nicht um
  („Neigung 102.08“).
- **Kalibrierung und Goldens:** `tools/calibration.sh` vorher und nachher byte-gleich (`calibration.txt`,
  `battery_sag.txt`, `fall.txt`, `motion.txt`, `thermal.txt`, `throttle_drop.txt`). Die Goldens bleiben unverändert.

### Signal-Repeater überarbeitet (Befund RC2-2, fix-2110)

Die Gruppe `relay:` hat fünf Schritte mehr (`RelayFieldSelfTests`, angemeldet am Ende von `RelaySelfTests.register`,
zusammen 9). Sie bauen auf eigenen Plätzen 3200 Blöcke westlich des Spawns (Räume 3200, Freiland 1800 Blöcke nördlich).

- **„relay: Raum hinter Betonwand und U-Gang – …“:** geschlossener Betonraum 13 × 9 × 13 (Wände 3 dick, Tür 3 × 3 im
  Norden), Pilot 50 Blöcke vor der Tür, Drohne (Racer, analog) in der Südwest-Ecke; U-Gang aus Beton 45 × 5 × 67 (Gänge
  3 × 3, Schenkel 60 Blöcke auseinander). Sechs Proben über `VideoProbe` wie im Betonbau: ohne Repeater verrauscht
  (direkt 0,26 bei 16,3 dB), über einen Repeater unter dem Türsturz bzw. an der Türlaibung klar (1,00, Strecken 52,5 m
  und 6,7 m frei), U-Gang ohne und mit nur einem Repeater an der ersten Ecke verrauscht, mit zwei Repeatern (Decke und
  Wand) klar über zwei; die Balken an beiden Repeatern zeigen die Kette.
- **„relay: Signalmesser in der Hand und Balken am Repeater“:** zwei Boden-Repeater 40 Blöcke auseinander zeigen je vier
  Balken; der Repeater in der Hand misst 3,6 m vor einem davon 100 % („Signal to repeater: 100 % · 4 m“, Balken im
  Item), im geschlossenen Betonraum 54 m weg 35 %; ohne zweiten Repeater sind die Balken aus, bei Welt-Regel 0 zeigt die
  Aktionsleiste „Signal repeaters are off (world rule)“, weggelegt ist alles aus.
- **„relay: Freiland – geradeaus bis zum Abbruch, Repeater bei 60 % und 100 % …“:** FPV im Überlebensmodus (keine freie
  Reichweite) mit dem Cinewhoop (Digital Lite) und Digitalbrille, 30 m hoch geradeaus (Teleport-Schritte wie in
  `signal:`). Abbruch ist, wo das direkte Bild unter 0,1 fällt (545 Blöcke). Boden-Repeater bei 60 % und 100 %,
  zurück auf 45 %, dann auf 150 %: Bild über zwei Repeater 0,37 bei direkt 0,00, OSD „RPT2 37 63 76“, der Repeater bei 60 % ist
  gemerkt, obwohl sein Chunk beim Client entladen ist, die Balken am Repeater bei 100 % leuchten. Weil Test-Knüppel die
  Verbindungsstärke in `PilotSession` auf 1 setzen, rechnet der Schritt den Funk selbst über `RelayControl`.
- **„relay: Freiland – Funk über drei Repeater weiter als 1250 Blöcke …“:** dritter Repeater 550 Blöcke hinter dem bei
  100 %, Drohne 1250 + 60 Blöcke hinter dem Repeater bei 100 % (1855 Blöcke vom Piloten): Funk direkt 0, über die Kette
  voll, der Server gibt nicht frei; nach dem Abbau des dritten Repeaters gibt er die Drohne frei.
- **„relay: Freiland und Räume aufräumen“.**

Mit `-PpropwashScreenshots=true` dazu `bildschirmfotos: Signal-Repeater – Raum hinter Betonwand …`, `… U-Gang aus Beton …`,
`… Balken am Block und Signalmesser in der Hand` (`signal_repeater_room_fpv`, `signal_repeater_corner_fpv`,
`signal_repeater_signal_bars`) und im Freiland-Schritt `signal_repeater_field_rpt`. JUnit: `RelayRangeTest`,
`RepeaterMeterTest`, `RelayPathsTest` (Kandidaten nach Reichweite, Server-Kette). Entwurf:
`docs/design/signal-repeater.md` §9.

Ergebnisse (Minecraft auf dem Stand nach dem Merge von PTP-3, `ceccc0193`; JUnit und fastcheck nach dem Rebase auf
`9887da122`):

- **JUnit vorher/nachher:** `RelayRangeTest` auf dem Stand „Test zuerst“ (ohne Fix) 4 von 4 rot („ANALOG_NANO: keine
  Kette“, „Funk-Teilstrecke 300.0 ==> expected: <1.0> but was: <NaN>“), mit Fix grün. Suite `tools/fastcheck.sh test`:
  4715 gefunden, 4701 grün, 0 Fehler (1 übersprungen, 13 abgebrochen wie im Integrationsstand). Kalibrierung 6 von 6
  Dateien byte-gleich zum Integrationsstand, Goldens unverändert.
- **Selbsttest Fabric** (`relay:,signal:,drone:,share:,bildschirmfotos: Signal-Repeater`, mit Bildschirmfotos): 219 von
  219 PASS. **NeoForge** (`relay:,signal:,drone:,share:`): 205 von 205 PASS. Beide Loader messen dasselbe: Raum hinter
  Betonwand direkt 0,26, über einen Repeater unter dem Türsturz bzw. an der Türlaibung 1,00; U-Gang direkt 0,21, mit
  einem Repeater 0,21, mit zwei Repeatern um die Ecken 1,00 (Strecken 38, 60 und 39 m); Betonbau mit sechs Etagen
  (Treppenhaus) wie bisher 1,00 über zwei Repeater; Freiland Abbruch 545 Blöcke, bei 817 Blöcken Bild über zwei Repeater
  0,37 (Strecken 327 m 0,37, 218 m 0,63, 274 m 0,76), Funk über die Kette 1,00; Funk bei 1855 Blöcken über die Kette
  voll, Freigabe erst nach dem Abbau des dritten Repeaters.
- **Server-Rauchtest** Fabric und NeoForge PASS.

### Drohne steht auf Zubehör, FPV-Auge an der gezeichneten Kamera (Befund PTP-2)

`docs/accessory-hang.md` Abschnitt 6: `'-PpropwashSelftestOnly=drone: steht auf Zubehör'` (beide Loader, sieben Schritte
in `drone.client.AccessoryStandSelfTests`, etwa 1 Minute; Platz bei Spawn + (−64, 112), Beispiel-Add-on nötig, sonst
werden die Drohnen-Schritte übersprungen). Zwei Fälle aus dem Spieltest: `freestyle_5` mit `propwash_example:dispenser`
vorn und `propwash_example:spear_mount` unten auf Eichenstufen, `long_range_7` mit `propwash_example:gimbal_front` auf
Stufen aus glattem Stein. Lose abgestellt (0,3 m fallen gelassen) muss die tiefste Zubehör-Ecke auf der Stufe liegen
(± 2 mm) und das Modell an der Physik (± 2 mm); im FPV (Fernsteuerung und Brille, Sitzung im Winkelmodus, unscharf) darf
das Zubehör nicht in der Stufe stecken, das Auge vor dem Kameraschutz muss höchstens 1 mm neben der gezeichneten Kamera
(`placeAtDisplayedPose`) sitzen, es gibt keine Block-Überlagerung und die Kamera steckt nicht im Block. Danach prüft der
Schritt „FPV-Bild an Teil-Blöcken“ `FpvView#coverInsideBlock` an elf Kamerastellen: 5 mm über Stufen aus glattem Stein
und Eiche, 2 mm über drei Schneeschichten und über Teppich sowie in Glas frei; in beiden Stufen, im Schnee, im Teppich,
in der unteren Treppenstufe und in Stein abgedeckt. Ohne Fix steckte das Zubehör 105,9 mm bzw. 41,0 mm in der Stufe,
Modell und Auge lagen ebenso weit darüber bzw. darunter, und die Teil-Block-Prüfung deckte 5 mm über beiden Stufen ab und
in Schnee und Teppich nicht. Bildschirmfotos (nur Fabric, `-PpropwashScreenshots=true`): `accessory_stand_freestyle_side`,
`accessory_stand_gimbal_side` (von der Seite) und `accessory_stand_freestyle_fpv`, `accessory_stand_gimbal_fpv`.
JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.AccessoryGroundTest --select-class
de.lardtom.propwash.accessory.hang.AccessoryStandTest`.

Ergebnisse nach dem Rebase auf den Merge von RC2-2: JUnit-Suite 4721 gefunden, 4707 grün, 0 Fehler (1 übersprungen, 13
abgebrochen wie im Integrationsstand); ohne den Fix in `ParamDerivation`, `DronePhysique` und `QuadcopterEntity` sind 4
von 6 der neuen Tests rot. Kalibrierung (mit Just More Parts) 6 von 6 Dateien byte-gleich, Goldens unverändert.
Selbsttest Fabric `drone: steht auf Zubehör`, `addon-api-17: gerade hängend`, `addon-api-17: B2`, `addon-api-17: B4`,
`drone: Bodenkontakt`, `drone: FPV-Kamera nie im Block`, `drone: Landung` mit Bildschirmfotos 87 PASS und
`drone:,drone-render:` 213 PASS; NeoForge `drone:,drone-render:` zusammen mit den `addon-api-17`-Gruppen 240 Schritte, 231 PASS, 9 FAIL in einer Gruppe:
Auf NeoForge läuft `addon-api-17: gerade hängend` vor `drone: Landung bei 30 FPS und unbegrenzt` und lässt den Spieler
einen Block über dem Boden stehen; in dieser Auswahl findet das Testfeld der Landung dann keinen Boden („kein fester Boden
unter dem Testfeld“). Die Gruppe einzeln: 18 PASS. Server-Rauchtest beider Loader PASS.

### Schnelle Drehungen im Flashback-Kamerapfad (Befund AUTO44-1)

Aus der Prüfung von RC2-5: Der Tiefpass in `PathResampler.perTick` rechnet die Drehung im Tangentialraum um die Lage
des Ticks. Der Logarithmus bildet dort nur bis ±180° ab. Ab etwa 1200°/s dreht sich die Kamera im Fenster von ±3 Ticks
weiter, der Vektor sprang auf den anderen Zweig und die Mittelung lief über den Sprung. Jetzt wird der Logarithmus von
der Probe vor dem Tick aus nach beiden Seiten entfaltet: Je Probe gilt der Zweig, der der Nachbarprobe am nächsten liegt.
Ohne Sprung im Fenster rechnet `perTick` bitgleich wie vorher.

JUnit (vor dem Fix rot, danach grün):

```sh
cd <WT> && tools/fastcheck.sh test --select-class de.lardtom.propwash.video.record.path.AntiAliasTest
```

- `steadyTurnsStayExact`: gleichmäßige Drehung mit 500°/s, 1998°/s (Ratengrenze je Achse), 2826°/s (zwei Achsen) und
  3461°/s (drei Achsen) bei 30, 60, 144 und 240 Bildern pro Sekunde. Die Tick-Lage liegt höchstens 0,0001° (ab 144
  Bildern pro Sekunde) bzw. 0,002° neben der Drehung, gemessen höchstens 0,00095° (Rest der Simpson-Quadratur bei 30
  Bildern pro Sekunde). Vorher ab 1998°/s 0,16–3,09° (1998°/s bei 144 Bildern pro Sekunde: 2,29°).
- `fastTurnsWithRippleKeepTheirTicks`: Drehung um die Roll-Achse und um eine schräge Achse mit 1598–3461°/s, Rate
  schwankt um 20 % bei 5 Hz, 60 und 144 Bilder pro Sekunde. Die Tick-Lage liegt höchstens 3 % der Schwankung neben der
  Drehung, gemessen 0,02–0,24°. Vorher 3,79–18,30° (1998°/s bei 144 Bildern pro Sekunde: 11,83°).
- `fastTurnsExportLikePointSampledTicks`: dieselben Drehungen bis 2826°/s bei 144 Bildern pro Sekunde. Flashbacks
  Spline zu jeder Bildzeit liegt höchstens 0,5° weiter neben dem Bild als mit Punktproben am Tick. Gemessen: Roll
  1,10–1,95° gegen 1,21–2,14°, schräge Achse 11,44–37,59° gegen 11,32–37,50°. Vorher Roll 4,68–19,66°, schräge Achse
  bei 1998°/s 22,35° gegen 17,71°.

Ab 3600°/s (180° je Tick) kann ein Pfad mit einem Keyframe je Tick die Drehung grundsätzlich nicht zeigen, mit und ohne
Tiefpass. Deshalb prüft `fastTurnsExportLikePointSampledTicks` nur bis 2826°/s (mit Schwankung höchstens 3391°/s).

### Altwelt-Vergleich der Bestenliste und Pilotenzahl im Archiv (Befunde AUTO46-1 und AUTO46-2, race-data)

Seit „Alle“ eine Zeile je Pilot und Klasse hat (Befund ALLBOARD-1), liefert `Leaderboard#top(null, …)` Zeilen statt
Piloten. Zwei Stellen zählten damit falsch:

- **`migration:`** (`dev.MigrationValues#leaderboard`, auch im Beitrittstest `PROPWASH_JOINTEST_MULTI=migration`):
  `top` je Bestenliste und Archiv ist wieder die Bestzeit je Pilot (erste Zeile jedes Piloten in „Alle“, höchstens 50
  Piloten), so wie die Erwartungen der Testwelten 0.4.0, 0.5.0 und 0.5.2 geschrieben sind. Alle gespeicherten Zeilen
  vergleicht weiter `codec`. JUnit `dev.MigrationBoardTopTest` (3): Bestenliste wie in den Testwelten (PropwashFixture
  in zwei Klassen), Vergleich mit einer Erwartung mit einer Zeile je Pilot, 60 Piloten in zwei Klassen. Ohne Fix 5 statt
  4 Einträge, „courses[id=1].leaderboard.top: erwartet 4 Einträge, ist 5“ und 25 statt 50 Piloten.
- **`/propwash race archive`:** Die Zeile je Layout nennt die Zahl der Piloten mit Bestzeit. Ein Pilot mit Zeiten in
  mehreren Klassen zählt einmal. JUnit `race.data.ArchivePilotsTest` (2) ruft den Befehlscode mit einer Befehlsquelle,
  die die Meldungen sammelt. Ohne Fix stand dort 3 statt 2 Piloten (Anna in `race_5` und `whoop_65`, Ben) und 2 statt 1
  (Carl in zwei Klassen).

**Gemessen am 02.11.:** JUnit-Suite 4726 gefunden, 4712 grün, 0 Fehler (1 übersprungen, 13 abgebrochen wie im
Integrationsstand). Kalibrierung (mit Just More Parts) 6 von 6 Dateien byte-gleich, Goldens unverändert.

`migration:` auf Fabric:

| Testwelt | Werte | Abweichungen |
|---|---|---|
| 0.4.0 | 1047 | 0 |
| 0.5.0 | 2314 | 0 |
| 0.5.2 | 2564 | 0 |

Das gilt vor dem Speichern und nach dem Neuladen. Auf NeoForge mit der Testwelt 0.5.0 ebenso 2314 Werte und 0
Abweichungen. Je Lauf sind 28 von 29 Schritten PASS. Der eine FAIL ist „migration: neue Standards …: Config
race.json.ghost: fehlt; Config race.json.recordGhost: fehlt“; er hängt nicht mit diesen Befunden zusammen. Die Schalter
`ghost` und `recordGhost` hat die Geist-Wahl (race-ghosts-a) aus `race.json` genommen, die Prüfung führt das noch nicht
als bewussten Umzug.

Beitrittstest `PROPWASH_JOINTEST_MULTI=migration` (Fabric, Testwelt 0.5.2):

- Welt am dedizierten Server: 2421 Werte, 0 Abweichungen.
- Pilot: `world=ok`, Runde angenommen, gespeichert, PASS.
- Der Server-Rauchtest endet wie beschrieben mit FAIL bei „Drohnen-Tags“.

`race-data:` auf Fabric und NeoForge: je 22 von 22 gefilterten Schritten PASS. Darin zeigt `/propwash race archive`
„1 pilots“ für das umgebaute Layout.

### Turtle-Modus: Totzone am Rand der Hitbox (`turtle:`, Befund AUTO45-1, cov-flight)

Nachtrag zu PTP-3 aus der Gegenprüfung G-PTP-3: Das Untergrund-Raster 5 × 5 tastete nur bis ±0,4 × Hitbox-Breite
(Spalten bei (i − 2) × Breite/5). Lag der Schwerpunkt 65–95 % der halben Hitbox-Breite hinter einer Gate- oder
Blockkante, trug die Minecraft-Kollision die Hitbox noch auf dem äußeren Streifen, das Raster sah dort aber keinen
Untergrund. Das Flugmodell rechnete dann ohne Kante: Die Drohne lag kopfüber reglos oben, drehte sich im Turtle-Modus
endlos auf der Stelle oder schaukelte. Rutschte sie über die Kante nach außen, endete das Abgleiten genau in diesem
Streifen. Jetzt liegen die äußeren Spalten 1 µm innerhalb des Hitbox-Rands (`SimEnvironment.supportSpacing(Breite)` =
(Breite − 2 × `SUPPORT_RIM`)/4, genutzt von `drone.DroneGround` und der Testwelt `sim.LedgeWorld`). Die Kante bleibt
sichtbar, bis die Hitbox den Untergrund verlässt. Ohne Raster und auf flachem Boden rechnet das Flugmodell wie bisher.

- **JUnit** `sim.TurtleEdgeRimTest`: Schwerpunkt 65, 70, 75, 80, 85, 90, 95 und 99 % der halben Hitbox-Breite hinter
  der Kante eines Gate-Balkens und einer Blockkante, alle fünf Bauarten, Kante in alle vier Richtungen. Kopfüber mit
  Turtle und Knüppel mittig, kopfüber unscharf und aufrecht unscharf: Die Drohne fällt binnen 3 s mehr als 1 m und liegt
  nach 6 s still. Turtle muss scharf werden, solange sie noch auf der Kante liegt (bei 99 % kippt sie schon vorher).
  Dazu: Die äußere Spalte liegt weniger als 10 µm innerhalb der halben Breite. Ohne den Fix rot: Turtle 280 von 320,
  kopfüber unscharf 128 von 160, aufrecht unscharf 142 von 160 Fällen bleiben oben liegen.
- **Selbsttest** `drone.client.CovTurtleRimSelfTests` (beide Loader, `'-PpropwashSelftestOnly=turtle: kopfüber am Rand
  der Hitbox'`, 4 Schritte): 5″-Drohne kopfüber, Turtle scharf, Knüppel mittig, Schwerpunkt 70 und 95 % hinter der
  Kante des oberen Gate-Balkens (MEDIUM) und 85 und 95 % hinter einer Blockkante in 3 m Höhe. Erwartet: Sturz von mehr
  als 1 m binnen 60 Ticks, danach 40 Ticks Stillstand. Ohne den Fix (Fabric): Gate 70 % „liegt noch oben … Neigung
  42.09, Tempo 0.00, scharf true, Turtle true, Drehung 21.35°/Tick“, die drei anderen „Neigung 180.00, Tempo 0.00“.
  Mit dem Fix fällt die Drohne nach 11–15 Ticks und liegt still (Fabric und NeoForge).
- **Kalibrierung und Goldens:** `tools/calibration.sh` mit und ohne Fix byte-gleich (6 von 6 Dateien), Goldens
  unverändert.

Ergebnisse (Minecraft auf dem Stand nach dem Merge von PTP-2, `95033ddea`): Selbsttest Fabric
`turtle:,rescue:,interact:` 55 PASS und `drone:` 157 PASS, NeoForge `turtle:,rescue:,interact:` 55 PASS und `drone:`
157 PASS. Server-Rauchtest beider Loader PASS (Heimflug landet auf Wolle, Slime und Druckplatte). Nach dem Rebase auf
`9f31017d1`: `turtle:` auf Fabric und NeoForge je 16 PASS. Nach dem Rebase auf `c3b7fd999`: JUnit-Suite 4741
gefunden, 4727 grün, 0 Fehler (1 übersprungen, 13 abgebrochen wie im Integrationsstand), fastcheck all ohne Befund.

### Turtle-Modus an jeder Kante (`turtle: Kanten`, Befund PTP3B-1, cov-flight)

Nachtrag des Owners zu PTP-3: Nicht nur an Gates, an jeder Kante soll eine kopfüber liegende Drohne herunterkippen,
sobald der Schwerpunkt über die Auflage hinausragt oder Turtle sie dorthin dreht. Geprüft sind einzelner Block, Säule
1×1 (auch über Eck), Klippe (7 m, die Bodensonde unter dem Schwerpunkt trifft nichts), Halbblock-Stufe, Treppenstufe,
Zaunpfosten, Mauer, Gate-Balken, Blockkante, Dive-Cube-Balken und Slalomstange. Mit dem Stand nach PTP-3 lag die
Drohne nur auf Zaunpfosten und Mauern fest. Deren Kollisionsform ist 1,5 Blöcke hoch und gehört zum Block darunter.
Das Untergrund-Raster (`drone.DroneGround`) prüfte jede Spalte mit `level.clip`, also nur die Blöcke auf dem 0,5 m
kurzen Strahl, und traf die Oberkante nie. Das Raster blieb leer, es gab keinen Überhang. Jetzt tastet
`DroneGround#blockTop` je Spalte die Blöcke vom Start bis einen Block unter dem Strahlende ab und nimmt die höchste
Kollisionsfläche. Für Formen bis zu einem Block Höhe ist das Ergebnis dasselbe wie vorher, Zauntore zählen jetzt wie
Zäune. Das Flugmodell ist unverändert.

- **JUnit** `drone.DroneGroundTest` (Kollisionsformen ohne Welt: Vollblock, Zaunpfosten, Mauer, Halbblock, Treppe,
  Teppich auf einem Zaun, nichts in Reichweite, Start in einer Form). Mit dem alten Suchbereich (nur die Blöcke auf
  dem Strahl) sind 3 von 7 rot. `sim.TurtleAnyEdgeTest` mit der Testwelt `sim.LedgeWorld` für alle Orte mit 5″, Race,
  Whoop und 7″: Kopfüber, Turtle scharf, Knüppel mittig kippt die Drohne herunter und liegt unten still. Vollgas bei
  mittigen Knüppeln rechnet bitgleich wie ohne. Roll zur Kante rollt sie herunter, von der Kante weg dreht sie sich oben
  auf. Mitten auf einem einzelnen Block dreht sie sich auf und bleibt oben. Dazu `GroundSupport#overhang` für Kante,
  Ecke, Pfosten, Lücke, Stufe und Absatz unter 3 cm. Diese Fälle waren schon vor dem Fix grün.
- **Selbsttest** (beide Loader, `'-PpropwashSelftestOnly=turtle:'`, nur die Kanten mit `'turtle: Kanten'`):
  `drone.client.CovTurtleEdgeSelfTests` baut je Ort die Kante auf und stellt den Spieler seitlich davor, der Blick
  folgt der Drohne. Je Ort drei Schritte: (1) Schwerpunkt 6 cm über der Kante, Turtle-Schalter an, Scharfschalten,
  Knüppel mittig: Die Drohne fällt binnen 60 Ticks um mehr als die halbe Fallhöhe (höchstens 1 m) und liegt danach 40
  Ticks still. (2) Dasselbe mit Vollgas. (3) Schwerpunkt 5 cm auf der Auflage, Turtle scharf, Knüppel zur Kante mit
  Vollgas: Die Drohne rollt herunter oder dreht sich auf und liegt danach still. Die Ecke der Säule läuft nur mit (1).
  In (1) und (2) kippt die Drohne sofort. Scharf geschaltet wird gleich nach dem Auflegen, das Log nennt den
  Schwerpunkt dabei (2 cm über der Oberkante, die Drohne schon im Kippen). Vor dem Fix waren Zaunpfosten und Mauer mit
  (1) und (2) rot: „liegt noch oben, Schwerpunkt 0.02 über der Oberkante, Neigung 180.00, Tempo 0.00, scharf true,
  Turtle true“.
- **Bildschirmfotos** (Fabric mit `-PpropwashScreenshots=true`, die Schritte `turtle: Kanten – Fenster …` stellen das
  Fenster auf 1920 × 1080 und zurück): `turtle_edge_<ort>.png` auf der Kante, `turtle_edge_<ort>_down.png` unten,
  `turtle_edge_<ort>_toward_edge_rolling.png` beim Rollen über die Kante, dazu `turtle_edge_gate_middle_rolling.png`
  und `turtle_edge_gate_middle_up.png`. Auf Zaun und Mauer liegt die Drohne wie ein Spieler einen halben Block über dem
  sichtbaren Pfosten (Kollisionshöhe 1,5).
- **Kalibrierung und Goldens:** byte-gleich, das Flugmodell ist unverändert.
- **Ergebnisse** (nach dem Rebase auf den Merge von PTP-2, `95033ddea`): JUnit-Suite 4735 gefunden, 4721 grün, 13
  abgebrochen (Add-on-Daten fehlen), 1 übersprungen, 0 Fehler; fastcheck all OK, strip_comments ohne Änderung.
  Selbsttest `turtle:`, `rescue:`, `drone:` und `interact:` Fabric 232 von 232 und NeoForge 232 von 232 PASS, die
  Kanten mit Bildschirmfotos (Fabric) 45 von 45. Vor dem Fix `turtle: Kanten` Fabric 36 von 40, rot nur Zaunpfosten
  und Mauer mit Knüppeln mittig, mit und ohne Gas. Auf Zaun und Mauer fällt die Drohne jetzt nach 11 bis 12 Ticks.
  Server-Rauchtest Fabric und NeoForge PASS (Heimflug landet auf Wolle, Slime und Druckplatte). Kalibrierung 6 von 6
  Dateien byte-gleich zum Integrationsstand.

### Drohne steht eben auf hängendem Zubehör, kein Schweben an Stufenkanten (Befunde AUTO47-1 und AUTO47-2)

Aus der Prüfung von PTP-2. AUTO47-1: Mit den Zubehör-Bodenpunkten stand eine Drohne schräg auf Zubehör und Rahmen; der
Whoop 65 mit der Speer-Halterung von Mob Defense lag nach dem Fall aus 0,3 m auf dem Rücken, Cinewhoop und Freestyle
5″ mit Dispenser und Speer standen 27,5° schräg und ließen sich nicht scharf schalten (in rc.1 eben und scharf).
Jetzt hat das Flugmodell vier Standbeine an den Rahmenecken in der Ebene der tiefsten Zubehör-Ecke, und Zubehör-Ecken
und Standbeine federn nicht zurück. AUTO47-2: Lose abgestellt maß `QuadcopterEntity#standOnAccessories` die Lücke unter
der ganzen Hitbox; jetzt misst es den Boden unter jeder Zubehör-Ecke einzeln.

JUnit (vor dem Fix rot, danach grün):

```sh
cd <WT> && tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.AccessoryGroundTest --select-class de.lardtom.propwash.accessory.fix.PayloadLimitTest
```

- `buildsWithHangingAccessoriesRestLevelOnThemAndArm`: Dispenser vorn (62×58×64 mm, 150 g) und Speer-Halterung unten
  (24×18×90 mm, 40 g) in den Maßen von Mob Defense, einzeln und zusammen an allen passenden Rahmen, dazu Cine 15″ mit
  110×125×110-mm-Gimbal: nach dem Fall aus 0,3 m höchstens 1° geneigt, aufrecht, tiefste Zubehör-Ecke auf dem Boden
  (± 2 mm), scharf schaltbar. Vorher: „whoop_65+spear_mount: steht 180.0° schräg“.
- `hangingAccessoriesDoNotFlipDropsMoreOftenThanWithout`: 40 Fälle je Bau (0,3 und 1 m, Nick oder Roll −12° bis 12°,
  ohne und mit 1,5 m/s Fahrt); auf dem Rücken nicht öfter als derselbe Rahmen ohne Zubehör. Vorher Whoop 65 mit Speer 29
  von 40 gegen 10 ohne Zubehör. Standbeine allein, noch mit Rückprall, reichten nicht (Cinewhoop mit Dispenser 11 von
  56 gegen 0 in einer Sonde).
- `standingLevelOnTheAccessoryKeepsTheFrameFootprint`: die Bodenpunkte in der Ebene der tiefsten Zubehör-Ecke umschließen
  den Schwerpunkt. Vorher Freestyle 5″ mit Dispenser und Speer ohne Standfläche um den Schwerpunkt.
- `PayloadLimitTest#overloadedDronesStayStableAndStayOnTheGround` wieder mit der 58 mm hohen Box (seit PTP-2 durch eine
  4-mm-Platte ersetzt): vorher „cinewhoop_3 BOTTOM nicht scharf“.

Selbsttest `'-PpropwashSelftestOnly=drone: steht auf Zubehör'` (beide Loader, jetzt acht Schritte): Im FPV muss die
Drohne höchstens 1° geneigt stehen und darf keine Scharfschalt-Sperre wegen Neigung haben (vorher Freestyle 5″ 27,5° mit
Sperre, 7″ mit Gimbal 12,8°). Neuer Schritt: der 7″ mit Beispiel-Gimbal lose an der Nordkante der Stufen aus glattem
Stein, Hitbox 5 cm auf der Stufe, Gimbal über dem 0,5 m tieferen Boden; die Drohne muss auf der Stufe liegen (± 2 mm).
Vorher „Drohne schwebt 41,0 mm über der Stufe, obwohl unter dem Zubehör 500,0 mm frei sind“, jetzt Fuß 0,0 mm über der
Stufe, tiefste Zubehör-Ecke 459,0 mm über dem Boden daneben. Bildschirmfotos `accessory_stand_freestyle_fpv` und
`accessory_stand_gimbal_fpv` neu (eben, ohne „NOT LEVEL“).

Ergebnisse nach dem Rebase auf den Merge von PTP3B-1: JUnit-Suite 4758 gefunden, 4744 grün, 0 Fehler (1 übersprungen, 13
abgebrochen wie im Integrationsstand). Kalibrierung (mit Just More Parts) 6 von 6 Dateien byte-gleich, Goldens
unverändert (Bauten ohne tief hängendes Zubehör rechnen bitgleich). Selbsttest NeoForge `drone: steht auf Zubehör`,
`drone: Bodenkontakt`, `drone: FPV-Kamera nie im Block`, `drone: Landung`, `reset: freier Platz` 69 PASS; Fabric
dieselben Gruppen zusammen mit `addon-api-17: gerade hängend` 63 PASS und 9 FAIL nur in `drone: Landung bei 30 FPS und
unbegrenzt` (dieselbe Reihenfolge-Abhängigkeit wie bei PTP-2 beschrieben: kein fester Boden unter dem Testfeld), die
Gruppe einzeln 18 PASS. Vor dem Rebase: Fabric und NeoForge je 69 PASS in denselben Gruppen und je 19 PASS in
`addon-api-17: gerade hängend`, `B2`, `B4`; Server-Rauchtest beider Loader PASS.

### Altwelt-Umstieg: Geist-Schalter aus 0.5.x als `ghostMode` (`migration:`, Befund MIGGH-1, race-ghosts-a)

Die Geist-Wahl (race-ghosts-a) führt die Schalter `ghost` und `recordGhost` in `race.json` beim Laden zu `ghostMode`
zusammen (`GhostChoice#migrateConfig`: Bestrunde an → `OWN`, nur Rekord an → `BOARD`, beide aus → `OFF`). Der Schritt
„migration: neue Standards …“ kennt das jetzt als bewussten Umzug: `MigrationSelfTests#upgrade` nimmt beide Schalter aus
der Erwartung, rechnet sie mit `GhostChoice#migrate` in `ghostMode` um und vergleicht dieses Feld. Unter „bewusste
Umzüge“ steht dann „race ghost true, recordGhost true → ghostMode OWN (seit 0.6.0, ein Geist je Kurs)“. Ein anderes
`ghostMode` als das umgerechnete bleibt eine Abweichung; eine Erwartung, die schon `ghostMode` enthält (Testwelt ab
0.6.0), bleibt unverändert. Die Migration selbst ist unverändert.

JUnit `MigrationConfigMovesTest` (vier neue Tests): `ghostSwitchesOfOldStandsAreComparedAsTheGhostMode` (die `race.json`
der Testwelten 0.4.0, 0.5.0 und 0.5.2), `everyOldGhostPairHasItsMode` (`OWN`, `BOARD`, `OFF`),
`anotherGhostModeThanTheMoveIsADifference` („Config race.json.ghostMode: erwartet "OWN", ist "OFF"“) und
`currentGhostModeStaysWithoutMove`. Vorher waren die ersten drei rot mit „Config race.json.ghost: fehlt, Config
race.json.recordGhost: fehlt“, jetzt 11 von 11 grün.

Selbsttest `'-PpropwashSelftestOnly=migration:'` mit den Testwelten 0.4.0, 0.5.0 und 0.5.2
(`-PpropwashDevName=PropwashFixture`): vorher je Lauf 28 von 29 Schritten PASS (einziger FAIL „neue Standards …: Config
race.json.ghost: fehlt; Config race.json.recordGhost: fehlt“), jetzt auf Fabric und NeoForge mit allen drei Testwelten
je 29 von 29 PASS. Gruppe `race:` auf beiden Loadern 148 von 148 PASS.

### Gesteuerte Drohne schwebt nicht an Stufenkanten (Befund STAND3-1)

Aus der Gegenprüfung von AUTO47. Lose abgestellt lag die Drohne an einer Stufenkante schon richtig, gesteuert nicht:
Solange die Mitte gestützt war (`GroundSupport#overhang` false), prüfte `DroneSim` alle Bodenpunkte gegen eine Ebene.
Zubehör-Ecken über der Kante standen auf Stufenhöhe in der Luft, die Standbeine hoben den Rahmen eben an, und beim
FPV-Start sprang die Drohne hoch. Jetzt nimmt das Flugmodell für Zubehör-Ecken und Standbeine den Boden genau unter dem
jeweiligen Punkt (`DroneGround#fillPoints`, `SimEnvironment#pointSupport`), und ein Standbein trägt höchstens bis zu
der Höhe, auf der das Zubehör selbst Boden hat (`GroundSupport#capStandLegs`).

JUnit (vor dem Fix rot, danach grün):

```sh
cd <WT> && tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.AccessoryLedgeTest
```

- `pilotedDroneLiesOnTheStepWhenItsAccessoryHangsOverTheEdge`: Cinewhoop 3 mit Dispenser und Cine 15″ mit
  `gimbal_cine` im `LedgeWorld`, Mitte auf der Stufe, Zubehör 10 mm hinter der Kante über 0,5 m tieferem Boden;
  unscharf 3 s und scharf 1 s: Fuß der Hitbox auf der Stufe (± 2 mm), seit dem Start nie höher, höchstens 1° geneigt.
  Vorher „springt hoch: Fuß 57.5 mm“ bzw. „161.5 mm über der Erwartung“, jetzt 0,0 mm.
- `accessoryOverAShallowDropStandsOnTheLowerGround`: über 10 cm tieferem Boden liegt der Cinewhoop auf der Stufe, das
  162 mm tiefe Cine-Gimbal steht unten auf, der Rahmen eben 62 mm über der Stufe. Vorher 57,5 mm bzw. 99,5 mm zu hoch.
- `pilotedDroneStillStandsOnItsAccessoryOnTheStep`: mit dem Zubehör ganz oder nur 2 cm auf der Stufe steht die Drohne
  wie bisher auf dem Zubehör. Mit dem groben 5×5-Raster statt der Messung unter den Punkten sank das Cine-Gimbal hier
  162 mm in die Stufe.
- `pointSamplesAreCopiedSanitizedAndCleared`: Punkte überstehen `copyTo`, `GroundSupport#sanitize` verwirft sie ohne
  Lage, ohne Raster oder bei zu vielen Punkten, `clearSupport` leert sie.

Selbsttest beider Loader `'-PpropwashSelftestOnly=drone: steht auf Zubehör'`, neuer Schritt „7″ mit Gimbal vorn
gesteuert an der Kante einer Stufe aus glattem Stein, Mitte 66 mm auf der Stufe …“: lose, im FPV unscharf (40 Ticks) und
scharf (40 Ticks), nach der Sitzung und mit Bildschirmfotos im Sichtflug muss der Fuß der Hitbox auf der Stufe liegen
(± 2 mm, seit dem Start nie höher, höchstens 1° geneigt); Bild `accessory_stand_edge_piloted` (Sichtflug, Seitenansicht
der Kante). Vorher auf Fabric FAIL mit „im FPV unscharf: Fuß der Hitbox 40.5 mm über der Stufe, tiefste Zubehör-Ecke
-0.5 mm über der Stufe und 499.5 mm über dem Boden darunter“ (lose 0,0 mm), jetzt auf Fabric und NeoForge überall
0,0 mm, Gimbal 41 mm unter der Stufe und 459 mm über dem Boden darunter.

Ergebnisse: JUnit-Suite 4766 gefunden, 4752 grün, 0 Fehler (1 übersprungen, 13 abgebrochen wie im Integrationsstand).
Kalibrierung (mit Just More Parts) 6 von 6 Dateien byte-gleich, Goldens unverändert; auf ebenem Boden mit Raster und
Punkten rechnen 14 Bauten mit hängendem Zubehör in je 12 Fallversuchen bitgleich (Sonde). Selbsttest
`drone: steht auf Zubehör`, `drone: Bodenkontakt`, `drone: FPV-Kamera nie im Block`, `drone: Landung`,
`reset: freier Platz`, `turtle:`, `rescue: ohne Pilot` auf Fabric und NeoForge je 119 von 119 PASS,
`addon-api-17: gerade hängend` je 11 von 11 PASS, Server-Rauchtest beider Loader PASS (Heimflug landet auf Wolle,
Slime und Druckplatte).

### Landung bei 30 FPS direkt nach der Grundgruppe der Landungen (Befund WA-W2e-1, cov-flight)

Aus dem Wellenabschluss W2e: Mit `'-PpropwashSelftestOnly=drone: Landung'` scheiterte „drone: Landung bei 30 FPS und
unbegrenzt – Testfeld mit Stein, Gras und Stufe“ auf beiden Loadern jedes Mal mit „Sichtflug mit der eigenen Drohne
nicht gestartet: Pilot=Optional.empty“, die acht Landefälle danach mit „keine Sitzung für die Landung“. Allein und im
vollen Lauf war die Gruppe grün. Kein Fehler im Spiel, sondern im Test: „drone: Landungen – Testfeld abbauen“ beendet
die eigene Sitzung per Stopp-Anfrage, und der Server lässt danach für `DronePiloting.REMOTE_DEBOUNCE_TICKS` (10 Ticks)
keine weitere Start/Stopp-Aktion zu (gewollte Entprellung, `docs/design/gameplay-loop-v2.md` §4.2). Das Testfeld der
nächsten Gruppe benutzte die Funke schon 2 Ticks später, der Server verwarf den Druck. Jetzt wartet „… – Testfeld mit Stein, Gras und Stufe“ (beide Gruppen,
`LandingSelfTests#arenaStep`) ohne laufende Sitzung `REMOTE_DEBOUNCE_TICKS + 2` Ticks, bevor es die eigene Drohne setzt
und den Sichtflug startet, wie die übrigen Schritte, die nach einem Stopp die Funke benutzen. Mit laufender Sitzung
bleibt der Schritt unverändert.

`CovLandingSelfTests` hat dafür zwei neue Schritte vor dem Testfeld: „… – Vorlauf: eigene Drohne im Sichtflug“ und
„… – Vorlauf: Sitzung per Stopp beendet, Testfeld abgebaut“. Ohne laufende Sitzung (allein und im vollen Lauf) steht
das Testfeld der Gruppe damit immer direkt nach einem Stopp; vor dem Fix war die Gruppe allein so auf Fabric rot (9
FAIL wie im Befund), danach grün.

```sh
'-PpropwashSelftestOnly=drone: Landung'              # 27 Schritte mit den 8 Rahmen-Schritten
'-PpropwashSelftestOnly=drone: Landung bei 30 FPS'   # 20 Schritte
```

Ergebnisse: `drone: Landung` Fabric 5 von 5 und NeoForge 3 von 3 Läufen je 27 von 27 PASS (vorher 10 von 10 bzw. 5
von 5 rot), `drone: Landung bei 30 FPS` allein auf beiden Loadern je 20 von 20 PASS, mit dem Mauer-Absturz davor wie im
vollen Lauf (laufende Sitzung) Fabric 33 von 33 PASS. JUnit unverändert, Goldens und Kalibrierung nicht berührt.

### Flughilfe-Prüfung weit weg vom Spawn (Befund W2E-2)

Aus dem Wellenabschluss W2e: Im vollen Fabric-Lauf war „flughilfe-prüfung: Server wertet vier Piloten je Runde mit
genau ihren Gründen“ rot mit „pw_clean: Start abgelehnt: NOT_PILOTING“, gefiltert grün. Die Prüfung
(`race.AssistServerChecks`) setzt Gates und Drohnen am Spawn. Im vollen Lauf stand der Spieler nach den Race-Gruppen rund
12 Chunks davon entfernt (Sichtweite 6), die Chunks am Spawn waren entladen. Die Prüfung erzwang sie und setzte die
Drohnen im selben Server-Schritt; Minecraft macht die Entities eines eben geladenen Chunks aber erst mit einer späteren
Aufgabe des Server-Threads sichtbar (`ChunkHolder#scheduleFullChunkPromotion`), also fand `RaceServer.pilotedDrone` die
Drohne nicht. Kein Produktfehler: Im Spiel steht eine geflogene Drohne nie in einem Chunk, der im selben Schritt erst
geladen wird.

- **Feste Startlage:** Die Gruppe beginnt 180/-190 Blöcke vom Spawn (wie im vollen Lauf) und wartet, bis die zwölf
  Chunks der Prüfung am Spawn entladen sind. Im Aufräumen geht der Spieler an seine Lage vor der Gruppe zurück und wartet
  auf die Chunks im Umkreis von 3. Gefiltert war der Schritt damit vor dem Fix rot wie im vollen Lauf (Fabric 2 von 2,
  NeoForge 1 von 1).
- **Chunks vor der Prüfung:** Der Schritt hält die elf Chunks wie der Server-Rauchtest und dazu den Spawn-Chunk (nur,
  wenn er nicht schon gehalten wird), wartet höchstens 200 Ticks, bis alle zwölf geladen sind und ihre Entities ticken,
  und gibt sie nach der Prüfung wieder frei. Die Logzeile nennt die Wartezeit („Chunks der Prüfung am Spawn nach 2 Ticks
  geladen; Piloten je Runde: …“).
- Der Server-Rauchtest bleibt, wie er ist: Chunks ab Tick 1, Prüfung bei Tick 40, der Spawn-Chunk gehört der
  Rauchtest-Drohne.

Aufruf: `'-PpropwashSelftestOnly=flughilfe-prüfung:'` (5 statt 3 Schritte, je Loader rund 5 s in der Testwelt).

### Ranken-Balken und Drohne im Block (Befund VINE1-1, 0.6.0)

Discord-Bericht vom 03.11. (0.5.2): Drohne trifft einen Balken mit Ranken, Gas 0, danach Kamera im Balken und die Drohne
„wandert“ unter der Erde. Design und Ursache: `docs/design/drone-depenetration.md`.

```sh
'-PpropwashSelftestOnly=drone: Ranken-Balken'                  # 7 Schritte, mit Rahmen 15, rund 8 min je Loader
'-PpropwashSelftestOnly=drone: Ranken-Balken – Blockbruch'     # nur die vier Anflug-Schritte, rund 6 min
'-PpropwashSelftestOnly=drone: Ranken-Balken – Drohne,drone: Ranken-Balken – Steinblock'   # nur die drei Fälle im Block, rund 20 s
```

`interact.client.CovVineBeamSelfTests`, eigene Testdrohne (5-Zoll) mit FPV-Sitzung, Testfeld 60 Blöcke in +X neben dem
Spieler: Balken aus entrindeter Schwarzeiche in 3 m Höhe mit Ranken an Vorder- und Rückseite und darunter, Pfosten,
erhöhter Ruinenboden (bemooster Bruchstein, Grobe Erde, Moos, Steinziegel) mit Ranken an der Kante, Gras, blühendes
Azaleenlaub und eine Mauer mit Ranken dahinter.

- Vier Anflug-Schritte, je eine Kombination aus `drones_break_blocks` und `crash_disarm`: 21 Flüge (10, 18 und 25 m/s;
  mitten auf den Balken, unter der Kante durch die Ranken, von unten, von oben, schräg, mit 40° Neigung, unter dem Balken
  entlang), ab dem Start Gas 0 im Acro, Knüppel mittig, je 80 Ticks beobachtet. Rot, wenn die Box der Drohne in einem Tick
  mehr als 1 cm in einer Kollisionsform steckt oder die Kamera (`mainCamera().position()`) in einer Kollisionsform liegt;
  jede Zeile nennt Ende, Abstürze, tiefstes Eindringen und Ticks mit dem Auge im Block. Ergebnis 03.11.: mit Fix Fabric
  und NeoForge je 84 von 84 Flügen ohne Eindringen und ohne Auge im Block; vor dem Fix auf Fabric 78 Flüge in zwei
  Teilläufen ebenso (der Einstieg aus dem Video ließ sich so nicht nachstellen).
- Drei Fälle im Block (Standardregeln ohne Absturz-Entschärfen): Drohne 3 cm im Boden mit Gas 0 (kommt nach oben, war
  schon vor dem Fix grün), Steinblock in die schwebende Drohne gesetzt (weicht zur nächsten freien Seite aus) und Drohne
  0,6 m tief im Grasblock mit Gas 0 (steht danach auf dem Gras). Ohne Fix: Steinblock 0,455 m im Block bis zum Ende,
  Grasblock 0,455 m unter der Oberkante, beide rot; mit Fix alle grün, die Zeile nennt „Ausweichen 1“.
- Bilder (immer, ohne `-PpropwashScreenshots` in `screenshots/` im Run-Ordner): `vine_beam_approach.png` (FPV-Bild im
  Anflug auf den Balken), `vine_embedded_ground.png`, `vine_embedded_block.png`, `vine_embedded_deep.png` (FPV-Bild 20
  Ticks nach dem Rücksetzen bzw. dem Block).

Server-Rauchtest (beide Loader, `rescue.CovVineServerChecks`, Zeile „Ranken-Balken und Drohne im Boden: …“): Heimflug
aus einer Drohne, die 0,1 m in einer 2 Blöcke dicken und 4 Blöcke hohen Mauer steckt, darf keinen Server-Tick im Block
sein (ohne Fix 8 von 60 Ticks 0,1 m im Block, rot; mit Fix vor dem ersten Tick davor, grün), und Heimflug mit 15 m/s
gegen einen Balken mit Ranken (nie im Block). JUnit:
`tools/fastcheck.sh test --select-class de.lardtom.propwash.drone.CovDepenetrationTest --select-class de.lardtom.propwash.sim.CovPushOutTest`
(14 Tests). Die Gruppe `reset: freier Platz` (Block auf dem Homepoint mit Überlagerung) bleibt unverändert grün: eine
ruhende, entschärfte Drohne wird nicht herausgedrückt.

### Ranken-Balken von oben: Motoren im Leerlauf, Schwerkraft frei (Befund VINE2-1, 0.6.0)

Nachprüfung zu VINE1-1 mit der Beobachtung aus dem Video (Treffer von oben, Vollgas ohne Eingabe, danach keine
Schwerkraft); Ergebnis und Ursachen in `docs/design/drone-depenetration.md` §4. Alle Prüfungen: 5-Zoll, Acro, Airmode
eingerastet, Gas 0 und Knüppel mittig ab dem Treffer, Drohne vor jedem Sturz neu (ohne Verschleiß), Absturz-Entschärfen
aus.

- JUnit `tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.CovVineBeamTopTest` (rund 10 s): Welt mit
  Boden und Balken (1 Block breit, Oberkante 3 m), Ranken als Laub-Verfangfeld an beiden Seiten und darunter, Auflage je
  Messpunkt und Herausdrücken wie in der Pilotensitzung (`Depenetration.resolve` vor jeder Bewegung). 175 Stürze von oben
  (5 Treffpunkte, 5 Tempi bis 15 m/s, 7 Lagen bis kopfüber) und 40 Fälle, in denen die Drohne nach dem Treffer 3–90 cm
  im Balken steckt. Rot, wenn nach 0,3 s ein Stoß über 50 % Motorleistung länger als 0,3 s dauert, in den letzten 1,5 s
  von 5 s die Motoren über 5 % oder der Schub über 0,15 × Gewicht liegen, die Drohne frei ist und trotzdem als aufliegend
  gilt (mehr als 3 Frames) oder ohne Schub nicht fällt (mehr als 2 Frames), beim Sturz mehr als 1 mm eindringt oder im
  Balken länger als einen Frame stecken bleibt. Ohne den Regler-Teil von CRASHARM-1 sind 102 von 175 und 25 von 40 Fällen
  rot, ohne `Depenetration` 40 von 40 Fälle im Balken; mit dem Flugmodell aus `v0.5.2` 97 von 175 und 40 von 40.
- Selbsttest `'-PpropwashSelftestOnly=drone: Ranken-Balken – von oben'` (`interact.client.CovVineBeamTopSelfTests`,
  beide Loader, rund 4,5 min): Testfeld aus `interact.CovVineArena` (dasselbe wie VINE1-1), 48 Stürze aus 1,6 m über
  der Oberkante (waagerecht, kopfüber, auf der Seite, kopfüber mit 30° Nase tief; Treffpunkte Mitte, beide Kanten und
  5 cm daneben; 3/3, 9/6 und 6/10 m/s quer/abwärts), je 100 Ticks beobachtet von `interact.client.CovVineDropWatch`.
  Rot bei mehr als 0,5 s über 50 % Motorleistung oder einem Stoß über 0,3 s nach der Gnadenfrist, in den letzten 1,5 s
  Motoren über 5 %, Schub über 0,15 × Gewicht oder mehr als 1 Block Weg, mehr als einem Tick frei schwebend (ohne
  Ranken, ohne Schub, Fall langsamer als 0,4 g; Ticks, in denen das Flugmodell keine 20 ms weiterrechnet, zählen nicht),
  Eindringen über 1 cm, Auge im Block, einem Server-Rücksetzer oder ohne Airmode beim Treffer. Ohne CRASHARM-1 und
  VINE1-1 auf Fabric 26 von 48 rot (Drohne liegt auf dem Balken, Motoren 43 %, 3,6 × Gewicht).
- Beitrittstest `PROPWASH_JOINTEST_COV_VINE=1` (§3, an Server und Client, `PROPWASH_JOINTEST_HOLD=1800`): der Server
  (`dev.CovVineJoinCheck`) baut das Testfeld 20 Blöcke neben dem Spieler, startet die FPV-Sitzung und setzt die Drohne
  alle 120 Ticks auf den nächsten von 12 Startpunkten (`CovVineArena.joinFlights`), loggt je Sturz die Ticks im Block am
  Server; der Client (`interact.client.CovVineJoinProbe`) startet nach jedem Rücksetzen den Sturz und prüft wie der
  Selbsttest. Erwartung `joined|vine_top=12/12`.

### Kreativ-Flugcontroller im Rennen (`race-rules: Kreativ-FC`, Befund CFC1-1, race-rules)

Welt-Regel `propwash:creative_fc_race_legal` und Schalter je Kurs mit drei Stufen ([`race-rules.md`](race-rules.md) §9).

- JUnit: `race.CreativeFcRaceTest` (Welt-Regel aus/an × Kurs Welt-Regel/Flughilfe/renntauglich, nur
  `CREATIVE_DRONE` fällt weg, Rechte), `race.data.CreativeFcDataTest` (Kursdatei, Rechteprüfung am Server, Vermerk
  `creative_fc` an Runde und Rennen, alte Zeiten unverändert, Export). `race.CourseMenuTest` kennt die Zeile
  `CREATIVE_FC` (nur mit Recht `ADMIN`), die Vergleichsdateien unter `common/src/test/resources/race-data/` tragen
  `creativeFc` je Kurs und Eintrag (`PROPWASH_RACE_DATA_GOLDEN=1` schreibt sie neu).
- Selbsttest `'-PpropwashSelftestOnly=race-rules: Kreativ-FC'` (beide Loader, 4 Schritte, rund 5 s): sieben Runden mit
  Kreativ-FC am Server auf einem eigenen Kurs 40 Blöcke neben dem Kurs der Gruppe, Kurs-Einstellungen mit drei Stufen
  und Layout-Prüfung, Bestenliste mit Abzeichen „Kreativ-FC erlaubt“ und Vorabwarnung mit Kreativ-Drohne in der Hand
  (`AssistPreflight.check(minecraft, kurs)`), Aufräumen. Mit `-PpropwashScreenshots=true` (Fabric):
  `race_rules_creative_fc_menu`, `race_rules_creative_fc_badge`, `race_rules_creative_fc_badge_tooltip`, je auch `_de`.
- Beitrittstest `PROPWASH_JOINTEST_CREATIVE_FC=1` an Server und Client (`dev.CreativeFcJoinCheck`,
  `race.client.CreativeFcJoinProbe`, rund 30 s): Gast ohne Recht ist Besitzer des Kurses, die Stufen sind gesperrt, sein
  Paket lehnt der Server ab; als Operator stellt er „Renntauglich“; der Server fliegt mit ihm vier Runden mit Kreativ-FC
  und prüft, dass die renntaugliche Runde nach dem Wechsel so bleibt.

```sh
PROPWASH_JOINTEST_HOLD=600 PROPWASH_JOINTEST_CREATIVE_FC=1 JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runServerSmoke
PROPWASH_JOINTEST=127.0.0.1:<port> PROPWASH_JOINTEST_LABEL=creative-fc PROPWASH_JOINTEST_EXPECT='joined|creative_fc=ok' \
    PROPWASH_JOINTEST_CREATIVE_FC=1 JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSmoke
```

Ein angeschlossener echter Sender öffnet im Rauchtest-Client die Frage nach der Einrichtung; solange sie offen ist,
warten alle Beitrittsproben (die Probe läuft nur ohne offenen Bildschirm). Sender vorher abziehen.

### Kopfzeile der Bestenliste bei GUI 320 × 240 (`race-rules: Kreativ-FC`, Befund AUTO53-1, race-rules)

Mit „Serverregeln“ und „Kreativ-FC erlaubt“ zugleich steht „Serverregeln“ bei schmaler Kopfzeile kurz als „≠“
([`race-rules.md`](race-rules.md) §9).

- JUnit `race.client.HeaderBadgesTest`: Platz der beiden Abzeichen mit den Breiten aus der Prüfer-Sonde (Titel bis 169,
  Kopfzeile bis 304 bzw. 411), Rückfall „ganz“ → „≠“ → keins, strikte Grenze wie bisher.
- Selbsttest-Schritt „Bestenliste bei GUI 320×240 … beide Tooltips erscheinen“ in `'-PpropwashSelftestOnly=race-rules: Kreativ-FC'`
  (beide Loader, rund 2 s): Server-Schwerkraft 80 % und Kurs „Renntauglich“, dann Fenster 640 × 480 mit Skala 2 und
  automatisch sowie 854 × 480 mit Skala 2; beide Abzeichen da, innerhalb der Kopfzeile und ohne Überlappung, bei
  427 × 240 „Serverregeln“ in voller Länge, `LayoutProbe.audit` ohne Befund (kein gekürzter Text), Tooltip beider
  Abzeichen erscheint (Zähler `creativeTooltips()`/`rulesTooltips()` der Bestenliste). Mit `-PpropwashScreenshots=true`
  läuft er zusätzlich auf Deutsch und schreibt `race_rules_creative_fc_badge_narrow` (auch `_de`, 640 × 480).

### Dreizack 1,5 kg, Windkugel aus dem Werfer schwächer, VTX ohne Antennen (Befunde RC3P-1, RC3P-2, RC3P-6, accessory-gaps)

Neue Werte ([`accessory-gaps.md`](accessory-gaps.md) §1 und §3): Dreizack 1,5 kg in Beladung und Rückstoß (33,0 N·s),
Windkugel 1,0 + 0,8 = 1,8 N·s (die 3,5 N·s im Abschnitt „Werfen, Inhalt sichtbar, Stöße, alle Drohnen (B1–B4)“ gelten
nicht mehr), Windstoß einer Windkugel aus dem Werfer halb so stark wie Vanilla.

- JUnit `accessory.dispense.DispenseMathTest` (Tabelle, Dreizack, Windkugel 1,8 N·s, Faktor 0,5 nur für Werfer-Windkugeln),
  `accessory.dispense.TridentLoadTest` (1500 g je Dreizack, Flugmasse +3000 g mit zweien, Schub/Gewicht unter der
  Hälfte, für den Greifer zu schwer), `build.antenna.AntennaNoneTest` (`VideoAntenna.none()`, JSON `"antenna": "none"`
  hin und zurück, ohne Angabe weiter abgeleitet, Masse und Funk gleich), `drone.client.render.AntennaNoneRenderTest`
  (keine Teile, leere Antennen-Form an jedem Rahmen mit jedem VTX).
- Selbsttest beider Loader (rund 2 s): `'-PpropwashSelftestOnly=addon-api-17: B1 – Dreizack,addon-api-17: B3 – Windstoß,antenne: ohne Antennen'`
  (die Schritte liegen auch in den Gruppen `addon-api-17: B1`, `addon-api-17: B3` und `antenne:`).
  - `addon-api-17: B1 – Dreizack wiegt 1,5 kg …` (`interact.client.DispenseRecoilSelfTests`): `itemGrams` eines
    Dreizacks, zwei Dreizacke im Werfer einer schwebend abgestellten Freestyle-Drohne (Beladung, Flugmasse,
    Schub/Gewicht), Wurf eines Dreizacks (`recoilNs()` = 1,5 kg × Mündungstempo, Rückwärtstempo der Drohne), danach eine
    Windkugel mit 1,8 N·s. Ohne Fix rot mit 150 g, 13,2 N·s und 3,5 N·s.
  - `addon-api-17: B3 – Windstoß einer Windkugel aus dem Werfer …`: Je ein Steinblock 8 Blöcke voraus in zwei Bahnen,
    davor ein Zombie ohne KI und die Drohne; eine Windkugel aus dem Werfer und eine des Spielers werden vor den Block
    gesetzt und mit einem Tick zum Platzen gebracht; gemessen wird das Tempo von Zombie und Drohne direkt danach.
    Werfer-Windkugel 50 % ± 5 % der Spieler-Windkugel (gemessen 2,54 gegen 5,08 m/s am Zombie, 5,12 gegen 10,23 m/s an
    der Drohne); ohne Fix 100 %.
  - `antenne: ohne Antennen – …` (`drone.client.render.AntennaNoneSelfTests`): der eingebaute VTX mit den meisten
    Antennen (`digital_hd`) als Kopie mit `"antenna": "none"` aus dem Teile-JSON: Antennen-Form leer, Lack-Platz
    „Antennenspitzen“ fehlt in `PaintPicker.available`/`defaults`, Masse, Reichweite und Videolink gleich. Ohne Fix rot
    („Objekt erwartet“). Mit `-PpropwashScreenshots=true` (Fabric) `antenna_none_test_drone` (links mit, rechts ohne
    Antennen).

### Zubehör vorne sitzt am Rahmen (`addon-api-17: Zubehör am Rahmen`, Befund RC3P-5, acc-hang)

Die Anker vorn der eingebauten Rahmen liegen an der Vorderkante der Bodenplatte bzw. unter ihr
([`accessory-hang.md`](accessory-hang.md) §7). Soll: höchstens 1 mm zwischen gezeichnetem Zubehör und Rahmen,
höchstens 2 mm Überschneidung mit dem Quader aus `size_mm`.

- JUnit `accessory.hang.FrontAnchorFitTest` (2 Tests): alle eingebauten Rahmen mit Halterung vorne gegen ihre
  Modelldateien mit vier Quadern (62×58×64, 34×26×40, 12×10×20, 44×44×46) und die Messung an einfachen Quadern. Ohne den
  Fix 14 von 16 Kombinationen außerhalb (größte Lücke 10,0 mm am `race_5`), mit dem Fix 0.
- Selbsttest `'-PpropwashSelftestOnly=addon-api-17: Zubehör am Rahmen'` beider Loader (`interact.client.AccessoryFitSelfTests`,
  rund 1 min ohne Add-ons): „Zubehör vorne an jedem Rahmen“ misst jeden angemeldeten Rahmen mit jedem passenden Zubehör
  vorne gegen das gezeichnete Rahmenmodell (`drone.client.render.AccessoryFit`, Flächen aus `QuadCapture`), an eingebauten
  Rahmen dazu zwei Prüfquader; „Zubehör unten und oben“ misst gegen Rahmen, Akku, Stack, Kamera und Motoren (Abstand
  höchstens 1 mm, Überschneidung wird nur gemeldet). Eingebaute Rahmen müssen bestehen; Add-on-Rahmen stehen als
  „AUSREISSER“ und am Ende als „Add-on-Befund: <Rahmen> + <Zubehör> (<Halterung>): Abstand …, Anker (…) → Soll (…)“ im
  Log. Mit `-PpropwashScreenshots=true` (Fabric) die Bilder `accessory_fit_race`, `_freestyle`, `_cinewhoop`,
  `_long_range` (Dispenser von der Seite), mit Kamera-Add-on bzw. Just More Parts in `runs/selftest-client/mods/`
  zusätzlich `accessory_fit_camdrones_cine` und `accessory_fit_jmp_deadcat` (nicht ins Repo); mit Mob Defense nimmt der
  Schritt deren Dispenser.

Messung (04.11., nach dem Fix): ohne Add-ons Fabric 32 von 32 PASS (mit `addon-api-17: gerade hängend` und
`drone: steht auf Zubehör`, mit Bildschirmfotos), NeoForge 22 von 22 PASS; vorn 22 Paare an eingebauten Rahmen, größter
Abstand 0,0 mm, größte Überschneidung 0,6 mm. Vor dem Fix auf Fabric FAIL mit 17 von 22 Paaren außerhalb (größter
Abstand 10,0 mm). Kombi-Lauf mit Just More Parts 1.2.0-rc.2, Kamera-Add-on 1.0.0-rc.2 und Mob Defense 1.0.0-rc.2 auf
Fabric und NeoForge mit gleichen Messwerten: vorn 26 Paare an eingebauten Rahmen ohne Ausreißer, 93 an Add-on-Rahmen mit
63 Ausreißern (alle 18 Rahmen von Just More Parts mit Halterung vorne: Anker 2,4–8,4 mm zu weit vorn bzw. unten,
`camdrones:cine_frame` 4,5 mm unter der Nase); unten und oben 57 Paare an eingebauten Rahmen ohne Lücke über 0,1 mm.
Kalibrierung 6 von 6 Dateien byte-gleich.

### Start aus dem Stand auf hohem Zubehör, Grund zu NOT_READY (`addon-api-17: B7`, Befund RC3P-4, addon-autonomy)

Bis rc.3 galt eine Drohne für autonome Starts nur als „am Boden“, wenn ihre Hitbox weniger als 0,08 m über dem Boden
lag. Drohnen, die auf einem hohen Gimbal oder Zubehör stehen, bekamen `NOT_READY` (Kino-Drohne eines Kamera-Add-ons
0,162 m, Compact und Tour mit 360°-Gimbal 0,134 bzw. 0,140 m, Freestyle 5″ mit Speer 0,106 m). Jetzt misst
`rescue.GroundContact` den Abstand der ganzen Kollisionsform (Hitbox, Bodenpunkte, Zubehör-Ecken, Standbeine) und
verlangt Ruhe; `AutonomyAccess#notReadyReason` nennt den Grund ([`addon-autonomy.md`](addon-autonomy.md) §1, §3).

- JUnit `rescue.AutonomyGroundTest` (5 Tests, vor dem Fix alle rot): liegend am Boden für alle Presets ohne Zubehör,
  mit Speer unten und mit 360°-Gimbal vorn sowie Kino, Compact und Tour, gerade und um 90° gedreht; auf den Standbeinen
  mit dem Zubehör über einem Loch; 3, 5, 10 und 25 cm darüber schwebend nicht; berühren und bewegen ist nicht ruhen.
- JUnit `rescue.AutonomyNotReadyTest` (3 Tests, vor dem Fix 2 rot): `NOT_ON_GROUND`, `NO_TAKEOFF`, `NO_BATTERY` für
  Start und Übergabe, `notReadyReason` ist `default` und liefert bei älteren Implementierungen leer.
- Selbsttest-Schritt „addon-api-17: B7 – Start aus dem Stand auf hohem Zubehör“ (`rescue.client.AutonomyGroundSelfTests`,
  beide Loader, rund 15 s, eigener Platz 6 Blöcke neben dem Spieler): Freestyle 5″ mit Beispiel-Speer unten liegt lose
  0,106 m hoch auf dem Speer; 5 cm darüber `NOT_ON_GROUND` und `NOT_READY`, liegend ohne `takeoff` `NO_TAKEOFF` und
  `NOT_READY`, mit `takeoff` `STARTED`, steigt auf 5 m (gemessen 4,89 m), `land()` endet mit `LANDED`, die Drohne liegt
  wieder auf dem Speer und gilt als am Boden. Ohne Fix auf Fabric FAIL: „liegend ohne takeoff, Grund: erwartet
  NO_TAKEOFF, war NOT_ON_GROUND; liegend mit takeoff, Start: erwartet STARTED, war NOT_READY“.

```sh
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=addon-api-17: B7 – Start aus dem Stand'
```

Die Selbsttests 386, 388, 390 und 554 des Kamera-Add-ons (Kino vom Boden) prüfen dasselbe mit den Rahmen des Add-ons.

### Zubehör einrücken (`addon-api-17: Zubehör eingerückt`, Befund FB1-9)

`recess_mm` und `drop_mm` schieben Zubehör nach hinten und unten ([`accessory-hang.md`](accessory-hang.md) §8).

- JUnit `accessory.hang.AccessoryRecessTest` (11 Tests) und `sim.AccessoryRecessFlightTest` (1): ohne Felder bitgleich,
  Quader, Mitte, Mündung, Linse und LEDs verschoben, Zeichenposition passt zu den Ecken (auch geneigt), Ersatzform
  unverschoben, `MountPose`, Masse, Trägheit, Schwerpunkt und Bodenpunkte, Codec und Grenzen, gewollte Überlappung in
  `AccessoryFit`, Quader 62×58×64 mit 58 mm gegen Rahmen, Akku unter `race_5` und Halterung unten. Ohne den Fix 7 von 12
  rot (Quader −58 statt −59,5, Mündung 0 statt 58 mm versetzt, Messung sieht keine gewollte Überlappung).
- Selbsttest `'-PpropwashSelftestOnly=addon-api-17: Zubehör eingerückt'` beider Loader
  (`interact.client.AccessoryRecessSelfTests`, zwei Schritte, wenige Sekunden): `propwash_example:dispenser_recessed` als
  Modell und ein Prüfquader als Ersatzform an `freestyle_5`, `race_5`, `cinewhoop_3` und `long_range_7`: Position im
  Anzeige-Zustand, Anker unverändert, gezeichneter Abstand und Überschneidung im Soll, Hülle rückt mit. Danach je Rahmen
  ein Pfeil im Schwebeflug: startet an der Mündung, 6 mm vor dem Anker, fliegt nach vorn, Schutz vor Treffern an der eigenen
  Drohne aktiv.
- `addon-api-17: Zubehör am Rahmen` misst zusätzlich einen eingerückten Prüfquader an den eingebauten Rahmen und jedes
  angemeldete Zubehör, auch eingerücktes aus Add-ons. Mit `-PpropwashScreenshots=true` (Fabric) die Seitenbilder
  `accessory_recess_race`, `_freestyle`, `_cinewhoop`, `_long_range` (im Repo), mit Kamera-Add-on bzw. Just More Parts in
  `runs/selftest-client/mods/` auch `accessory_recess_camdrones_cine` und `accessory_recess_jmp_deadcat`.

```sh
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=addon-api-17: Zubehör eingerückt,addon-api-17: Zubehör am Rahmen'
```

### Arme und Motoren im FPV-Bild (`drone-render: Arme und Motoren im FPV-Bild`, Befund FB1-1)

Bis rc.3 zeichnete das FPV-Bild nur die Props: Lag eine Nabe im Bild, hing der Prop ohne Motor und Arm frei in der Luft
(Rahmen „5-Zoll-Race-Rahmen (Slim)“ ab 126°, viele Rahmen im Kreativ-FC bis 160°). Regeln in `docs/ASSETS.md` „Arme und
Motoren im FPV-Bild“.

- **JUnit `FpvArmsTest`:** `FpvArmZone` nimmt an den eingebauten Modellen jede Fläche von `motor_pad` und `motor_cup` und
  mindestens 90 % der Armfläche (`arm`, `arm_guard`, `spoke`), nie Kamerawangen, Canopy, Standoffs, Platten, Akku oder GPS, nie
  die Ringe der Ducts. Jeder Motor hängt an einem Arm, auch bei den Rückfall-Formen der Add-on-Rahmen (Deadcat, Stretched,
  Squashed, H, Ducted, X-Class). Raster bei 7 Kamerawinkeln und 4 Sichtfeldern: jede Nabe im Bild ist über Motor und Arm mit dem
  Bildrand verbunden, höchstens 20 % Bildanteil. Naben seitlich und längs auf den Motoren von `AirframeSpec` (0,6 mm).
  Kreativ-FC mit Basis-Rahmen: Flugmodell mit der Basis, Naben und Linse am eingebauten Rahmen. `armsVisible` sieht eine Fläche,
  die das Bild ohne Ecke darin kreuzt; die Arme ändern `nearScale` nicht.
- **`… (alle Rahmen, Positionen, Schalter)`** (ein Tick, je geladener Rahmen): Arme und Motoren haben Flächen, jeder Platz hat einen
  Arm, Abweichung Nabe zu Motor der Flugphysik, Kreativ-FC mit Basis `long_range_7` auf `race_5`, Schalter „Props im FPV-Bild“ und
  „Ducts im FPV-Bild“, keine Arme in den Messungen von Prop-Scheiben und Ducts, Platz ohne Arm zeigt keinen Prop.
- **`… – Props hängen sichtbar an den Armen`:** je Rahmen drei Bilder (eigener Kamerawinkel und eigenes Sichtfeld, Kamerawinkel und
  160° über den Kreativ-FC, 0° und 160°) mit `FpvProps.armMaskColor`: Pixel gleich der Analyse (`FpvGuardSelfTests.matches`), von
  jeder Nabe im Bild führt eine zusammenhängende Fläche zum Bildrand, höchstens 40 % des Bildes.
- **Bildschirmfotos** `fpv_arms_<rahmen>`, `…_160`, `…_160_0` (Fabric, `bildschirmfotos: Arme im FPV-Bild`); mit Just More Parts in
  `runs/selftest-client/mods` zusätzlich `fpv_arms_jmp_race_slim`, `_deadcat`, `_h_frame` (nicht ins Repo).

Messung (09.11.): ohne den Fix JUnit 5 Tests rot, auf Fabric beide Schritte FAIL (Naben im Bild 2, angebunden 0). Mit dem Fix und
Just More Parts 1.2.0-rc.3 auf Fabric und NeoForge je PASS: 30 Rahmen, 90 Bilder, 56 Naben im Bild, alle 56 angebunden, größte
Abweichung Nabe zu Motor 0,005 mm, Pixel gleich der Analyse auf 0,01 Prozentpunkte, größter Bildanteil 18,47 % (`fs_5_bash` bei 0° und
160°). `justmoreparts:race_5_stretched` bei 40° und 126°: 5,39 %, beide Naben angebunden. Bei Standard-Kamerawinkel und der im
Schritt gewählten Kamera (125–126°) zeigen alle anderen Rahmen weder Arm noch Motor (0,00 %); weitere Kameras misst der Schritt
nur über die 160°. Kalibrierung 6 von 6 Dateien byte-gleich zu WA-W2f, Goldens
unberührt. Ohne Add-ons gefiltert `drone-render:,drone:,video:,osd:,interact:,rescue:,signal:,objektiv:,iris:,antenne:,kamera:`:
NeoForge 391 von 391 PASS, Fabric zusätzlich mit `ui-scale:` 436 von 436 PASS.

```sh
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=drone-render: Arme,bildschirmfotos: Arme' -PpropwashScreenshots=true
```

### Greifer ohne Pilot: Fracht-API für Flugplaner (`addon-api-17: B7`, Befund FB1-10, addon-autonomy)

Bis rc.3 nahm der Greifer nur auf, wenn ein Pilot flog (`DroneCargo.tick`: `pilot().isEmpty()` beendete die Prüfung),
und ein Add-on erreichte ihn nicht (`triggerAction` braucht ein `AccessoryBehavior`, der Greifer ist eingebaut). Jetzt
greift und lässt ein Add-on ihn über `AccessoryRegistry#grabCargo`, `#releaseCargo` und `#cargo` los, auch im
Add-on-Flug ohne Pilot, und das Flugmodell des Server-Flugs rechnet die Fracht mit
([`addon-autonomy.md`](addon-autonomy.md) §11, Handbuch §21.23).

- JUnit `interact.CargoApiTest` (6 Tests, vor dem Fix 2 von 4 rot: „AccessoryBehaviors reicht cargo nicht an den Greifer
  weiter“, „Autopilot nach grabCargo nimmt nichts auf“): Fracht-API additiv als `default` mit leerem Ergebnis, `false`
  bzw. 0; Propwash überschreibt alle drei; mit Pilot nimmt der Greifer wie bisher auf, ohne Pilot nur nach `grabCargo`;
  der Griff bleibt 5 s offen und lässt sich schließen; Items mit Ziel-Spieler für Pilot oder Verursacher.
- Selbsttest-Schritt „addon-api-17: B7 – Fracht ohne Pilot“ (`rescue.client.AutonomyCargoSelfTests`, beide Loader, rund
  20 s, eigener Platz 6 bis 20 Blöcke neben dem Spieler): Freestyle 5″ mit Greifer startet mit `takeoff` 1,5 m einen
  Add-on-Flug ohne Pilot, schwebt 0,5 m über 4 Bruchstein an Wegpunkt A und nimmt 30 Ticks lang nichts auf; nach
  `grabCargo` (`true`) hält er sofort die 4 Bruchstein (`cargo()`), A ist leer, das Flugmodell am Server steigt von
  0,543 auf 0,743 kg (`FlightState#massKg`); in 1,5 m an Wegpunkt B gibt `releaseCargo` 4 zurück, `cargo()` ist leer,
  die 4 Bruchstein liegen bei B am Boden, 2 s kein neuer Griff, Flugmodell wieder 0,543 kg; `land()` endet mit `LANDED`
  und die Fracht bleibt liegen (Fabric und NeoForge je 424 Ticks). Ohne Fix auf Fabric FAIL: „grabCargo angenommen:
  erwartet true, war false“ (über A 0,52 m, 30 Ticks ohne Aufnahme).
- Die Schritte `interact:` zum Greifer mit Pilot (Aufnehmen, Abwerfen, Landen, Absturz) laufen unverändert.

```sh
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=addon-api-17: B7 – Fracht ohne Pilot'
```

Der Flugplaner eines Kamera-Add-ons baut darauf den Wegpunkt „Fracht: – / greifen / loslassen“ (eigener Selbsttest dort).

### Drehende Props behalten die Mitte (`drone-render: drehende Props behalten die Mitte …`, Befund OT4-1)

Bis rc.4.1 verlor das drehende Prop im FPV-Bild seine Mitte: über der weichen Scheibe fehlten Nabe, Mutter und Schraube,
unter der Scheibe stand nur die Motorglocke mit Welle. Die Ersatzform ohne `_blur`-Modell zeichnete die Nabe durchscheinend
und ohne Mutter. Regeln in `docs/ASSETS.md` „Props im FPV-Bild“.

- **Selbsttest `drone-render: drehende Props behalten die Mitte – Nabe und Mutter wie im Stand, Scheibe um die Motorachse
  (FPV-Bild und außen, alle Props)`** (`drone.client.render.CovPropBlurCenterSelfTests`, ein Tick, beide Loader): jeder
  geladene Prop auf dem Freestyle-Rahmen, dazu `propwash_rendertest:p13_still` (Modell ohne `_blur`) und ein Prop ganz ohne
  Modelle, je vorne links (cw) und vorne rechts (ccw), stehend (Ausgang 0) und drehend (Ausgang 1). Gezeichnet wird in einen
  Sammler (Flächen samt Lage, Textur und deckend/durchscheinend): außen über `DroneVisuals.submit` mit nur diesem Prop, im
  FPV-Bild über `FpvProps.submit` (170°, Kamerawinkel 20°, nur dieser Platz). Geprüft: die Mitte des drehenden Props hat
  deckende Flächen, jede davon liegt genau wie eine Fläche des stehenden Props (Lage auf 10⁻⁴, gleiche Textur), ihr Schwerpunkt
  auf der Motorachse; die Nabe sitzt über der Motorachse; außen reicht die Scheibe um die Achse bis zum Prop-Radius (5 %), im
  FPV-Bild liegt jeder Punkt der Scheibe auf dem Kreisring zwischen Nabe und Rand (Strahl durch das Objektiv auf die Ebene der
  Scheibe); die Mitte des `_blur`-Modells gleicht dem stehenden Modell (`_ccw` je Drehrichtung) ohne seine Blätter
  (`CovPropBlurCenterModels`, seit Befund G-OT4-1-1 ohne feste Namen: Scheibe = Elemente, deren Flächen alle eine Textur mit
  `force_translucent` tragen, Mitte = die übrigen; seit Befund G-OT6-1-1 legt allein das stehende Modell die Grenze fest:
  Radius der Mitte = weitester Abstand seiner nicht angestellten Elemente (ohne Drehung um x oder z) von der Motorachse
  x 8, z 8; ein Element des stehenden Modells außerhalb der Mitte ist Blatt, wenn es weiter hinausreicht, sonst fehlt es;
  der Befund nennt fehlende und zusätzliche Elemente mit Namen). Ersatzform zu einem Modell (`p13_still`): nur Mitte vorhanden und auf der Achse.
- **JUnit `drone.client.render.CovPropBlurCenterModelsTest`** (15 Fälle): alle 28 Prop-Paare von Propwash und
  `propwash_rendertest` (cw, `_ccw`, Lack) gleich; Klapp-Prop mit Schrauben außerhalb der Nabe und Blattspitzen
  `blade_tip` (10 Blattteile); Blätter ohne Namen; Scheibe unter anderem Namen; Scheibe ohne `force_translucent` wird nicht
  verglichen; gefunden werden fehlende Mutter, fehlende Schraube außerhalb der Nabe, verschobene Nabe und ein
  `_blur`-Modell nur mit Scheibe; seit Befund G-OT6-1-1 auch ein `_blur`-Modell ohne Nabe (p51_3b, alle 6 `hub`), ein
  Klapp-Prop ohne Schrauben (alle 4) bzw. ohne Nabe, ein Klapp-Prop mit `blade_tip` ohne Nabe und Schrauben (nur die 10
  Teile der Mitte fehlen, die Blattspitzen bleiben Blatt) und ein angestelltes Teil innerhalb der Mitte; dazu (Befund
  AUTO69-1) bei jedem der 28 Paare jedes weggelassene Teil der Mitte (64 Fälle: genau dieses Teil fehlt, nichts zusätzlich,
  Blattteile unverändert) und beim `_blur`-Modell nur mit Scheibe genau die 18 Teile der Mitte als fehlend, 12 Blattteile.
- **Bildschirmfotos** `cov_prop_blur_center_{fpv,side}_<drohne>_{still,blur}` (Fabric, `bildschirmfotos: Mitte drehender
  Props`; nicht ins Repo): Race 5″, Freestyle 5″ und mit Just More Parts Race 5″ (Slim) und Whoop 75 mm, je FPV-Bild über den
  Kreativ-FC mit 160° und 0° sowie von außen, schwebend unter der Unschärfe-Schwelle 1,0 (stehende Blätter) und mit 0,12.

Messung (10.11.): ohne den Fix auf Fabric FAIL mit 100 Befunden – 94 × „im FPV-Bild: Mitte fehlt“ (alle 47 Props, beide
Drehrichtungen), 6 × „außen: Mitte fehlt“ (Ersatzform: Beispiel-Prop, `p13_still`, Prop ohne Modelle). Mit dem Fix und Just
More Parts 1.2.0-rc.4 auf Fabric und NeoForge je PASS: 47 Props, 188 Fälle, 184 mit gleicher Mitte wie im Stand (die übrigen 4
`p13_still` mit Ersatzform), 62928 Strahlen der FPV-Scheibe auf dem Kreisring, 88 Modellpaare mit gleichen Elementen der Mitte;
gefiltert `drone-render:` je 67 von 67 PASS. Kalibrierung 6 von 6 Dateien byte-gleich, JUnit unverändert grün. Bildschirmfotos
neu nur, wo eine Nabe im Bild liegt: `fpv_arms_{race_5,freestyle_5,long_range_7,whoop_65}_160` und `…_160_0` (8 Bilder, ohne
Add-ons); die übrigen 21 Bilder von „Props/Guards/Arme im FPV-Bild“ zeigen keine Nabe und weichen nur im Rauschen ab.

Nachtrag (Befund G-OT4-1-1, 10.11.): Mit Just More Parts 1.2.0-rc.4 und dem Kamera-Add-on 1.0.0-rc.4 in `mods` scheiterte
der Schritt auf Fabric, obwohl die Darstellung stimmte: die Blattspitzen `blade_tip` dreier Klapp-Props zählten als fehlende
Mitte (6 Befunde „4 Elemente fehlen“, cw und ccw). Mit dem Fix auf Fabric und NeoForge je PASS: 50 Props, 200 Fälle, 196 mit
gleicher Mitte wie im Stand (die übrigen 4 `p13_still`), 66448 Strahlen, 94 Modellpaare; gefiltert `drone-render:` je 67 von
67 PASS. Ohne Add-ons auf Fabric vorher und nachher gleich, nachher auch auf NeoForge (9 Props, 36 Fälle, 32 mit gleicher
Mitte, 11904 Strahlen, 12 Modellpaare). `CovPropBlurCenterModelsTest` ohne den Fix 5 von 9 rot.

Nachtrag (Befund G-OT6-1-1, 10.11.): Die Grenze zwischen Mitte und Blatt kam aus der Mitte des geprüften `_blur`-Modells;
fehlte dort die ganze Nabe, wurde die Mitte kleiner und die fehlenden Nabenteile zählten als Blatt (ein `p51_3b_blur` ohne
die 6 `hub` bestand, bei `camdrones:fold_15` ohne Schrauben wurden nur 4 von 12 gemeldet). Jetzt kommt die Grenze aus dem
stehenden Modell. Auf Fabric mit `p51_3b_blur` und `p51_3b_blur_paint` ohne `hub` (nur im Worktree, danach
zurückgesetzt): FAIL „6 Elemente fehlen: hub, …“ für cw und ccw. Ohne Minecraft über alle 192 Prop-Paare von Propwash,
`propwash_rendertest`, Just More Parts und Kamera-Add-on (rc.4) 0 abweichend, das knappste Blattteil reicht 1,197-mal so
weit wie die Mitte; ohne `hub` bzw. ohne `screw` werden bei fold_6, fold_9 und fold_15 alle 8 bzw. 12 Elemente gemeldet.
Mit Just More Parts 1.2.0-rc.4 und dem Kamera-Add-on 1.0.0-rc.4 auf Fabric und NeoForge gefiltert `drone-render:` je 67 von
67 PASS mit denselben Zahlen wie oben (50 Props, 200 Fälle, 196, 66448 Strahlen, 94 Modellpaare), ohne Add-ons je 67 von 67
PASS (9 Props, 36 Fälle, 32, 11904 Strahlen, 12 Modellpaare). `CovPropBlurCenterModelsTest` ohne den Fix 3 von 14 rot.
Nachprüfung (Befund AUTO69-1, 10.11.): Mit der Grenze aus der Mitte des `_blur`-Modells (Stand vor dem Fix) scheitern die
beiden Fälle „jedes weggelassene Teil der Mitte“ (28 von 64 falsch, alle ohne Nabe) und „nur Scheibe“ (30 statt 18 fehlend,
die Blätter mitgezählt); mit dem Fix 15 von 15 grün. Ohne Minecraft über alle 192 Paare samt Just More Parts und
Kamera-Add-on (rc.4) jedes Teil der Mitte einzeln weggelassen: 428 Fälle, vor dem Fix 192 falsch (180 ohne Nabe, 12 ohne
Schrauben der Klapp-Props), mit dem Fix 0.

```sh
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=drone-render: drehende Props,bildschirmfotos: Mitte drehender Props' -PpropwashScreenshots=true
```

### Drohnen und Bosse (`bosses:`, Befund OT4-5, 0.6.0)

Wärter, Enderdrache, Wither und Windkugeln gegen Drohnen ([`drone-bosses.md`](drone-bosses.md)). JUnit
`interact.DroneBossesTest` (12) und `video.DarknessPulseTest` (4) rechnen Zorn, Schallschlag-Takt, Schaden nach den
Crash-Regeln, Stöße, Turbulenz, Drachenatem und die Dunkelheit im Kamerabild; vor dem Fix kompilierten sie nicht
(Regel `DRONES_BOSS_INTERACTIONS`, `BossHits`, `WardenGrudge`, `DarknessPulse` und `FeedInput#darkness` fehlten).

Selbsttest-Gruppe `bosses:` (`interact.client.DroneBossSelfTests`, 11 Schritte, etwa 1,5 Minuten im Spiel, beide
Loader). Aufbau 40 Blöcke östlich und 40 nördlich des Spielers eine Plattform aus glattem Stein (21 × 21) 25 Blöcke über
dem höchsten Boden; Spieler im Kreativmodus, Schwierigkeit normal, `mobGriefing` aus, Welt-Regeln Bosse und Sculk an,
Wind 0 und Turbulenz aus; vorher verschwinden alle Drachen und Wither der Welt. Jeder Schritt startet eine neue
Freestyle 5″ im FPV, setzt sie mit Blickrichtung auf den Boss an ihren Platz, schaltet scharf und hält die Höhe:

- Wärter 9 Blöcke vor der Drohne (mit Grab-Pause und Langsamkeit, damit er stehen bleibt), Drohne 1,5 m über der
  Plattform: Zorn 35, 68, 101, nach etwa 110 Ticks lädt er, 34 Ticks später trifft der Schallschlag; geprüft: Treffer,
  alle Propeller hin (schlechtester 0,00), Stoß beim Client, über 8 m/s (gemessen 24,5).
- Dunkelheit: Wärter ohne KI 10 Blöcke vor der Drohne; nach 22 Ticks volle Blende, Bild-Helligkeit 0,71, Rauschen 0,23;
  Regel aus, nach 161 Ticks (140 Nachklang + 22 Ausblenden) keine Dunkelheit mehr.
- Drache schwebt (`hover`), Drohne 8 Blöcke neben ihm im Flügelbereich: Flügelstoß und Turbulenz kommen an.
- Drache schwebt, Drohne wird in seinen Kopf gesetzt: Rammen, schlechtester Propeller unter 0,6, Stoß beim Client.
- Drache kreist 30 Blöcke weiter: er greift die Drohne an (`charging_player`, Ziel weniger als 3 m neben ihr).
- Wolke aus Drachenatem unter der Drohne (1,2 m): mindestens zwei Dosen, Video höchstens 0,75.
- Wither 12 Blöcke vor der Drohne: ein Seitenkopf schießt, der Schädel fliegt auf die Drohne (Ausrichtung über 0,9).
- Windkugel der Breeze 4 Blöcke vor der Drohne: Rückstoß weitergegeben, obwohl der Beispiel-Zuhörer den Faktor nicht
  setzt, Stoß beim Client, über 3 m/s.
- Regel aus: Wärter mit KI 8 Blöcke vor der Drohne 400 Ticks ohne Zorn und ohne Schallschlag, Windkugel stößt nicht.

Windkugeln von Spielern bleiben bei Skala 0 (E16): Der Schritt „addon-api-17: B3 – Windkugel neben schwebender Drohne:
ohne Skala kein Versatz …“ läuft mit der Regel an unverändert grün (Fabric und NeoForge).

Der Abbau stellt Blöcke, Regeln, Schwierigkeit, `mobGriefing`, Spielmodus und Ausrüstung wieder her. Mit
`-PpropwashScreenshots=true` (Fabric) entstehen `boss_warden_sonic_charge_fpv`, `boss_warden_sonic_boom_fpv`,
`boss_warden_darkness_fpv`, `boss_dragon_wing_fpv`, `boss_dragon_ram_fpv`, `boss_dragon_charge_fpv`,
`boss_dragon_breath_fpv` und `boss_wither_skulls_fpv`.

```sh
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :fabric:runClientSelftest -PpropwashScreenshots=true '-PpropwashSelftestOnly=bosses:'
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :neoforge:runClientSelftest '-PpropwashSelftestOnly=bosses:'
```

### Gesetzte und landende fremde Drohne (`drone: Gesetzte Drohne`, Befund OT8-1)

Eine frisch gesetzte Drohne versank bis zu 3 s im Boden bzw. stieg hoch und fiel zurück, eine landende konnte in einer
Lücke im Netz unter den Boden laufen ([`remote-motion.md`](remote-motion.md) §1 „Setzen, Ruhen, Landen“).

- JUnit `drone.RemoteMotionTest`: `placedDroneNeitherSinksNorRises` (FPV 1 cm Fall, Speer 10,6 cm, Kamera-Drohne
  16,2 cm Anheben, Paketfolge wie im Spiel, 100 Ticks), `restingDroneNudgedWithoutTempoStaysAtServerHeight`,
  `landingDroneDuringAGapStaysAboveGround` (3 Blöcke/s, Lücke 150, 300 und 600 ms, flacher Boden über den Haken
  `RemoteMotion.Ground`, Gegenprobe ohne Boden).
- Selbsttest „drone: Gesetzte Drohne sinkt nicht ein, steigt nicht und springt nicht …“
  (`drone.client.PlacedDroneSelfTests`, rund 5 s): Steinboden 6 Blöcke neben dem Spieler, gesetzt wie vom Spieler
  (Item auf die Oberseite): FPV-Drohne, Freestyle mit Beispiel-Speer unten und, wenn das Kamera-Add-on in
  `runs/selftest-client/mods/` liegt, dessen Preset mit dem tiefsten Zubehör (sonst steht „übersprungen“ im Log). Je
  Bild 100 Ticks lang: tiefster Punkt samt Zubehör nie unter dem Boden, gezeichnete Höhe nie unter oder über der Höhe
  am Server (jeder Server-Tick zählt), kein Schritt größer als die Bewegung am Server, am Ende auf 1 mm gleich.
- Selbsttest „drone: Gesetzte Drohne landet bei einer Lücke im Netz nicht unter dem Boden …“ (rund 3 s): FPV und Speer
  als Drohnen nur beim Client, Sinkflug mit 3 Blöcken/s, Pakete ab 100 ms vor dem Aufsetzen 300 ms verspätet; tiefster
  Punkt samt Zubehör nie unter dem Boden, am Ende in der Ruhelage.

Vorher auf Fabric (mit Kamera-Add-on 1.0.0): Speer 95,9 mm im Boden und 446,3 mm über der Server-Höhe, Kamera-Drohne
`camdrones:cine` 147,5 bzw. 685,2 mm; ohne Kappung landen beide 300 mm unter dem Boden. Danach alles 0,0 mm.

```sh
cp <Kamera-Add-on-Jar des Loaders> <WT>/<loader>/runs/selftest-client/mods/
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=drone: Gesetzte Drohne'
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :neoforge:runClientSelftest '-PpropwashSelftestOnly=drone: Gesetzte Drohne'
```

### Replay glatt bei 60 fps (`race: Replay glatt`, Befund OT9-1)

Replays, Videos aus „Als Video aufnehmen“ und Geister liefen in Stufen im 20-Hz-Takt: Der Rekorder des Piloten nahm die
Lage aus den Entity-Daten, die der Server nur je Tick und um die Laufzeit verspätet zurückmeldet
([`race-ghosts.md`](race-ghosts.md) „Woher die Spur kommt“). Dazu wich im Video die Replay-Zeit je Bild bis 10,8 ms ab,
wenn das Spiel nicht mit einem Vielfachen von 60 fps lief.

- `dev.CameraPathMeter` (devtest, auch für JUnit): Kamerabahn je Bild bei 60 fps, getrennt für Position und Lage
  (Winkel zwischen zwei Bildern); 20-Hz-Anteil = Betrag der Komponente mit Periode 3 Bilder ÷ Summe (0 glatt, 1 nur
  jedes dritte Bild bewegt), Sprungverhältnis = Median über je drei Folgebilder von größtem ÷ mittlerem Schritt,
  Autokorrelation Lag 1–3. Glatt heißt 20-Hz-Anteil ≤ 0,25 und Sprungverhältnis ≤ 1,8.
- JUnit `race.ReplaySmoothTest`: Runde des Piloten bei 60, 144 und 40 fps im Spiel (Bildzeit ± 2 ms) über
  `keptLap`, `GhostCodec` und `smoothPose` glatt, ebenso der 50-ms-Geist über `pose`; Gegenprobe mit einer Lage, die
  wie früher nur alle 50 ms und 30 ms verspätet wechselt (bleibt gestuft); Server-Spur aus 20-Hz-Paketen (Senden im
  Bildtakt ab 50 ms, Ankunft 25–29 ms später) glatt; Videozeit je Bild mit `FrameClock` bei 60, 75, 90, 120, 144, 165
  und 240 fps im Spiel höchstens 1 ms neben 16,7 ms (vorher bis 10,8 ms bei 75 fps, 6,5 ms bei 90, 4,9 ms bei 144).
- Selbsttest „race: Replay glatt bei 60 fps (Kamerabahn je Bild aus eigenem Flug)“ (`race.client.ReplaySmoothSelfTests`,
  rund 15 s, nach den übrigen Schritten von `RaceGhostsSelfTests`): FPV-Drohne vor dem Spieler, Steigflug auf 5 Blöcke,
  Kurve mit Nick 0,2 und Gier 0,35; 4,5 s Runde aus dem Rekorder des Clients (`keptLap`, 10 ms), kodiert wie beim
  Upload, im `ReplaySession` (FPV) je Bild bei 60 fps abgespielt und gemessen, dazu der Geist im Rennen. Mit ffmpeg
  nimmt der Schritt das Replay als Video auf und schreibt die Messung der Bilddifferenz (`tblend` difference,
  `signalstats` YAVG) ins Log; die Datei bleibt unter `runs/selftest-client/propwash/recordings/`.

| Messung | vorher | nachher |
|---|---|---|
| Replay FPV, Lage (Fabric / NeoForge) | 20-Hz-Anteil 0,82 / 0,70, Sprungverhältnis 8,39 / 10,42, Lag 1/2/3 −0,36/−0,40/0,68 | 0,00 / 0,00, 1,01 / 1,01, Lag 0,06/0,01/0,01 |
| Replay FPV, Position | 0,01–0,02, 1,16–1,20 | 0,02, 1,14 |
| Video YAVG (Fabric) | 20-Hz-Anteil 0,16, Sprungverhältnis 1,15, Lag −0,20 bei 2 und 0,45 bei 3 | 0,00, 1,01, Lag 0,99/0,96/0,92 |

```sh
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=race: Replay glatt'
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :neoforge:runClientSelftest '-PpropwashSelftestOnly=race: Replay glatt'
```

### Replay nach erneutem Durchflug durch das Startgate (`race: Replay nach erneutem Durchflug …`, Befund OT10-1)

Flog der Pilot vor dem Ende der Runde noch einmal durch das Startgate, lief die Uhr weiter, der Server begann seine Runde
aber am zweiten Durchflug: Spur des Servers und Replay waren um diese Zeit zu kurz, die richtige Aufnahme des Piloten
wurde mit `GATES` abgelehnt ([`race-ghosts.md`](race-ghosts.md) §2).

- JUnit `race.LapParityTest` (`startGateAgain…`, fünf Fälle: vor und nach Gate 1, in Runde 2 eines Rennens über drei
  Runden, Punkt-zu-Punkt, Schleife mit Gegenrichtung und mit zweitem Durchgang in gleicher Richtung): Server-Runde
  über `RaceServer.verifiedLap` beginnt höchstens 40 ms neben dem Start der Uhr des `LapTracker`.
- JUnit `race.StartGateAgainReplayTest`: Client (`LapTracker` und `GhostRecorder` bei 60 fps) und Server
  (`CourseCrossings`, `LapVerifier`, `GhostRecorder` bei 50 ms und 150 ms Latenz) fliegen dieselbe Bahn mit 20 Blöcken/s;
  je Runde Server-Start ± 20 ms am Start der Uhr, Spur des Servers, Aufnahme des Piloten und Replay (`GhostUploads.merge`)
  dauern so lang wie die Zeit (± 60 ms) und liegen alle 250 ms höchstens 1 Block neben dem Flug ab dem Start der Uhr,
  `GhostUploadCheck` nimmt die Aufnahme an; dazu `RaceServer.timedStart` (Wahl des Starts, Startmeldung, Fehlstart,
  zu schnelle Zeit) und Runde 2 ab dem Ziel der ersten.
- Selbsttest „race: Replay nach erneutem Durchflug durch das Startgate beginnt mit der Uhr“
  (`race.client.RaceStartAgainSelfTests`, rund 1 s): drei Gates, zweiter Start-Durchflug nach 5,5 s; gewertete Zeit,
  Spur des Servers, Herkunft `PILOT`, Dauer und Uhr im echten `ReplayScreen`.

Vorher: JUnit 10 von 10 rot (Server beginnt 2,5 bzw. 5,5 s nach der Uhr, Upload `GATES` nach Gate 1, `ACCEPTED` nur
davor); Selbsttest auf Fabric und NeoForge rot mit „Uhr 11.658, Bestenliste 11.658, Server-Spur 6.120, Upload GATES,
Herkunft SERVER, Replay bis 6.120, Uhr am Ende 6.120“. Danach grün: Server-Spur 11.660, Upload `ACCEPTED`, Herkunft
`PILOT`, Replay bis 11.660, Uhr am Ende 11.658.

```sh
cd <WT> && tools/fastcheck.sh test --select-class de.lardtom.propwash.race.StartGateAgainReplayTest --select-class de.lardtom.propwash.race.LapParityTest
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=race: Replay nach erneutem'
cd <WT> && JAVA_HOME=/opt/homebrew/opt/openjdk@25 <slot-skript> ./gradlew :neoforge:runClientSelftest '-PpropwashSelftestOnly=race: Replay nach erneutem'
```
