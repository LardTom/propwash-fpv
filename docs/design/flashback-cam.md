# Action-Cam (Flashback) – Kamerapfad für die Replay-Mod Flashback

Stand 03.10.2026, Propwash 0.5.0. Idee des Besitzers: eine Action-Cam wie Light und Heavy, die nur mit der Mod
[Flashback](https://modrinth.com/mod/flashback) (nur Fabric, Quelltext öffentlich, alle Rechte vorbehalten) etwas
aufnimmt. Statt eines Videos zeichnet sie den Weg der FPV-Kamera auf und übergibt ihn an Flashback als Kamerapfad, den
man im Flashback-Editor rendern, schneiden und ändern kann.

## Ablauf für Spieler

1. Werkbank: Action-Cam (Flashback) in den Slot „Zubehör oben“ (wie Light und Heavy, nicht beim Whoop).
2. Fliegen (FPV oder Sichtflug) und den Aufnahme-Knopf drücken: Taste „Aufnahme“ (Standard F9) oder die Funktion
   „Aufnahme“ auf einem Schalter oder Taster der Funke. Mit „Beim Scharfschalten“ startet sie beim Scharfschalten.
3. Nimmt Flashback noch nicht auf, startet der Knopf auch eine Flashback-Aufnahme („Flashback mitstarten“,
   Standard an). Toast „● Kamerapfad für Flashback“, im OSD zählt ● REC mit, im Sichtflug zeigt ein kleines Feld oben
   „FLASHBACK 00:12 · Kamerapfad“.
4. Noch einmal drücken (oder entschärfen, landen und Sitzung beenden, Flashback-Aufnahme beenden): Der Pfad wird als
   Szene „Propwash · Flug 1“ (mit Drohnennamen „Propwash · Nighthawk · Flug 1“) an Flashback übergeben. Hat die Kamera die
   Flashback-Aufnahme gestartet, beendet sie sie auch; Flashback speichert sie wie gewohnt (Schnellspeichern oder Dialog).
5. In Flashback die Aufnahme öffnen: Die Szene ist schon gewählt, hat eine Spur „FPV-Kamera“ (Position, Gier, Neigung,
   Roll) und eine Spur „FPV-Sichtfeld“, der Export-Bereich ist genau der Flug. Weitere Flüge derselben Aufnahme werden
   eigene Szenen („Flug 2“ …). Auf der Zeitleiste markiert „Propwash: Flug N“ den Start.

Ohne Flashback (NeoForge oder Fabric ohne die Mod) gibt es das Item trotzdem; der Aufnahme-Knopf zeigt nur den Hinweis
„Action-Cam (Flashback) braucht die Mod Flashback“, und Tooltip, Tab und Flugschule sagen es vorher.

## Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Item immer registriert, auf beiden Loadern | keine Registerunterschiede zwischen Server und Client, Welten und Drohnen bleiben ladbar, wenn Flashback fehlt (Vorgabe für die Action-Cam) | Item nur mit Flashback registrieren |
| Name „Action Cam (Flashback)“ / „Action-Cam (Flashback)“ | Namensschema der Teile (`docs/design/part-naming.md` §3.7: Gerät, Modell in Klammern) | „Flashback Action Cam“ |
| Anbindung nur per Reflection in `compat.flashback.client.FlashbackBridge`, beim ersten Zugriff vollständig aufgelöst | keine Abhängigkeit beim Bauen (Flashback ist ARR, das Jar ist 200 MB groß), eine geänderte Flashback-Version fällt sofort und sichtbar auf („nicht unterstützt“ im Tab und im Log) statt mitten im Flug | `compileOnly` über Modrinth-Maven im Fabric-Modul mit Plattform-Dienst |
| Übergabe als **eigene Szene je Flug** im Editorzustand der Aufnahme: `EditorStateManager.get(uuid)` → Szene anhängen (eine noch leere Standard-Szene wird ersetzt) → `reset()` speichert `flashback/editor_states/<uuid>.json` | nutzt Flashbacks eigene Klassen und Speicherung, keine Eingriffe in Wiedergabe oder Aufnahmeformat; eigene Arbeit des Spielers in anderen Szenen bleibt unberührt; Export-Bereich passt sofort | Keyframes erst beim Öffnen der Wiederholung einfügen (Haken in Flashbacks Wiedergabe, eigene Nebendateien); eigene Aktionen im Aufnahme-Stream (unbekannte Aktionen brechen das Laden); Spuren in die Standard-Szene mischen |
| Ein Aufnahme-Knopf für alle Kameras (F9, Funktion „Aufnahme“); nur mit Flashback zusätzlich der Abschnitt „Flashback“ im Tab Action-Cam | wie bei einer echten Funke: der Knopf löst die Kamera aus, die auf der Drohne sitzt; keine zweite Taste, die mit F9 konkurriert | eigene Taste, die nur mit Flashback erscheint |
| „Flashback mitstarten“ (Standard an): startet Flashback, wenn es noch nicht aufnimmt, und beendet die Aufnahme mit dem Kamerapfad | ein Flug = eine Wiederholung ohne Umweg über Flashbacks Menü; nimmt der Spieler selbst auf, hängen sich die Flüge an seine Aufnahme an und sie läuft weiter | immer manuell starten (Hinweis „Flashback nimmt nicht auf“ bleibt für „aus“) |
| Zeitbasis: geschriebene Ticks der Flashback-Aufnahme (`Recorder.writtenTicks`, privates Feld) plus Teil-Tick des Bildes; eine Probe pro Bild, danach auf ganze Ticks abgetastet | stimmt mit Flashbacks Zeitleiste überein, Pausen in Flashback und im Spiel fallen heraus; pro Bild statt pro Tick, weil Ticks im Bildraster ruckeln | Probe am Tick-Ende |
| Lage vor dem Abtasten auf ganze Ticks tiefpassgefiltert (seit 0.6.0, Befund RC2-5): `PathResampler.perTick` mittelt die Lage um jeden Tick mit einem Lanczos-Fenster über ±3 Ticks (`ANTI_ALIAS_TICKS`), gerechnet auf der Drehung (Tangentialraum um die Lage des Ticks) über den Pfad aus den Proben jedes Bildes; dreht sich die Kamera im Fenster weiter als 180°, wird der Logarithmus entfaltet (von der Probe vor dem Tick aus nach beiden Seiten je Probe der Zweig, der der Nachbarprobe am nächsten liegt, Befund AUTO44-1); Position und Sichtfeld bleiben am Tick abgetastet | Keyframes je Tick zeigen nur Schwingungen bis 10 Hz. Ungefiltert wurde Propwash-Zittern über 10 Hz zu langsamem Wackeln gefaltet (1° bei 17 Hz → 0,95° bei 3 Hz, bei 19 Hz → 0,94° bei 1 Hz); Flashback zeigte dadurch ein Schaukeln, das es live nicht gab. Gefiltert: über 14 Hz unter 1,2 % Rest, bis 5 Hz wie vorher (Faktor 0,95 durch Flashbacks Spline); gleichmäßige Drehungen bleiben exakt, auch an der Ratengrenze (1998°/s je Achse, alle drei zusammen 3461°/s; Rest der Quadratur unter 0,001°). Ohne Entfaltung sprang der Vektor ab etwa 1200°/s auf den anderen Zweig (1998°/s mit 20 % Schwankung: Tick-Lage 11,8° falsch, gleichmäßig 2,3°) | ungefiltert abtasten; Dreieck über ±1 Tick (bei 5 Hz nur noch Faktor 0,76); Keyframes zwischen den Ticks (Flashback kennt nur ganze Ticks) |
| Pose: Schwerpunkt der Drohne aus der Physik + Linsen-Offset + Uptilt, **ohne** Videolatenz; Sichtfeld aus dem Videomodul (bzw. Kreativ-Brille) als vertikaler Wert für 16:9 | die Kamera sitzt in der Wiederholung in der Drohne, nicht 30 ms dahinter; 16:9 wie die Videos der anderen Action-Cams und Flashbacks Standard-Export | das angezeigte, verzögerte FPV-Bild; Sichtfeld fürs Seitenverhältnis des Fensters |
| Winkel im Format von Minecraft/Flashback (Gier, Neigung in −90…90, Roll lokal um die Blickachse); nahe der Senkrechten werden Gier und Roll so verteilt, dass Flashbacks Interpolation auf dem kürzesten Weg nicht verdreht (`EulerPath`, Keyframe-Fehler ≤ 2°) | Flips führen durch die Senkrechte; ohne Korrektur dreht sich das Bild dort um bis zu 180° | Quaternionen (Flashback kennt nur Winkel) |
| Keyframes (seit 0.5.2): Regler „Vereinfachung“ von „Aus“ (jeder Tick) bis 20 cm / 10°, Standard 1 cm / 0,5° (das frühere „Ausgedünnt“); jede Strecke wird zusätzlich gegen Flashbacks eigene Spline geprüft und bei mehr als dem 1,5-Fachen der Toleranz geteilt | gespeicherter Pfad bleibt auch zur gleichen Zeit in der angezeigten Toleranz (früher bis 0,3 Blöcke vor oder nach), Details in [`flashback-keyframe-budget.md`](flashback-keyframe-budget.md) | Auswahl „Jeder Tick / Ausgedünnt“ (nur lineare Prüfung, Tempo schwankte in Zeitlupe) |
| Sichtfeld-Spur mit HOLD-Keyframes am Anfang, bei Änderungen über 0,05° und am Ende | Flashback wendet einen einzelnen Keyframe nur an genau seinem Tick an | ein Keyframe pro Tick |
| Drohne in der Wiederholung ausblenden, wenn die Kamera in ihr sitzt (`ReplayView`: näher als 0,5 Blöcke, oder näher als 0,5 Blöcke + 2 Ticks Flugweg und nicht vor der Kamera) | gemessen: die wiedergegebene Drohne läuft etwa einen Tick hinter dem Kamerapfad; so sieht man nie das Innere der Drohne, freie Kameras, die auf die Drohne schauen, sehen sie weiter | Flashbacks „beim Export ausblenden“ (gilt für alle Szenen und nicht in der Vorschau) |
| Höchstens 15 Minuten je Flug; Keyframes je Flug durch das Budget der Zeitleiste begrenzt (Obergrenze beim Speichern, Standard an) | Flashbacks Zeitleiste zeichnet jeden Keyframe der Szene in eine ImGui-Liste mit höchstens 65.536 Eckpunkten, darüber stürzt das Spiel beim Öffnen ab (0.43.6; die frühere Annahme „18 000 Keyframes sind bedienbar“ war falsch) | keine Grenze |
| Werte des Bauteils: oben, 36 g, 42 × 28 × 24 mm, 3 W am Flugakku, Stirnfläche 0,0012 m²; kein `action_cam`-Block (nimmt kein Video auf) | kleine Kamera mit IMU und Speicher, leichter als die Light; ohne Videoformat-Zeilen im Tooltip | eine Video-Kamera, die zusätzlich den Pfad schreibt |
| Rezept: Uhr, zwei Glasscheiben, HD-Videochip, zwei Kupferbarren, Platine; nicht in Truhen | „Zeit“ als Erkennungszeichen; ohne Flashback nutzlos, deshalb kein Fundstück | Truhen-Beute wie Light und Heavy |
| Kein Netzwerk, keine Server-Logik, kein neues Protokoll, keine API-Änderung | Flashback läuft nur auf dem Client; Teil-Liste und Registerabgleich decken das neue Item ab | Synchronisieren des Kamerapfads |

## Code

| Datei | Inhalt |
|---|---|
| `build/AccessoryType#ACTION_CAM_FLASHBACK`, `FLASHBACK_CAM_ID`, `FLASHBACK_MOD_ID`, `isFlashbackCam()`; `build/Accessories#flashbackCam()` | Bauteil (Daten in `data/propwash/propwash_parts/accessory/action_cam_flashback.json`, `sort` 3) |
| `video/record/path/*` (reines Java, JOML) | `PathSample`, `CameraPathBuffer` (kompakte Proben, steigende Zeit, eine Quaternion-Halbkugel), `PathResampler` (`perTick`, `at`), `CameraAngles` (Quaternion ↔ Gier/Neigung/Roll wie `Camera#setRotation` + `rotateZ`), `EulerPath` (Winkel ohne Verdrehen), `CameraTrack` (Kamera- und Sichtfeld-Keyframes, Ausdünnen, Obergrenze), `PathKeyframe`, `FovKeyframe`, `TickPose`; seit 0.5.2 `FlashbackBudget`, `Simplification`, `FlashbackSpline`, `TrackThinner`, `PathFrames`, `LiveTrackCounter`, `PathPreview`, `SamplePath` (siehe [`flashback-keyframe-budget.md`](flashback-keyframe-budget.md)); `KeyframeDensity` nur noch zum Übernehmen alter Einstellungen |
| `compat/flashback/client/FlashbackBridge` | Reflection auf Flashback 0.43.x: Status (`ABSENT`/`READY`/`INCOMPATIBLE` mit Grund), Recorder, Pause, Schreibbereitschaft, geschriebene Ticks, UUID der Aufnahme, Aufnahme starten und beenden, Marker, Szene schreiben, Datenordner |
| `compat/flashback/client/FlashbackScene`, `ReplayView` | Daten einer Szene; Ausblenden der Drohne in der Wiederholung (`QuadcopterRenderer#shouldRender`) |
| `video/client/record/FlashbackCam` | Sitzung: Start (Ergebnisse `STARTED`, `NO_SESSION`, `NO_CAMERA`, `NEEDS_FLASHBACK`, `INCOMPATIBLE`, `IN_REPLAY`, `NOT_RECORDING`, `FLASHBACK_REFUSED` …), Proben in `ClientBus.FRAME_START` nach `PilotSession`, Stopp-Gründe (Knopf, Entschärfen, Sitzungsende, Flashback beendet, 15 min, Trennen), Rechnen im Hintergrund, Übergabe auf dem Render-Thread, Toasts |
| `video/client/record/ActionCamRecorder` | leitet Taste und Funkenschalter an `FlashbackCam`, wenn die Drohne die Action-Cam (Flashback) trägt |
| `video/client/record/RecordingConfig` (`action_cam.json`) | `flashbackAutoRecord` (Standard an), `flashbackSimplify` (0–1, Standard 1 cm), `flashbackKeyframeLimit` (Standard an); ein altes `flashbackDensity` wird beim Laden übernommen |
| `video/client/record/ActionCamTab`, `RecordingHud`, `osd/client/LiveOsdSource` | Abschnitt „Flashback“ (nur mit Flashback), Statuszeilen, Hinweis; trägt die Drohne die Action-Cam (Flashback), heißt der Abschnitt der Videoeinstellungen „Videoaufnahme (Action-Cam (Light) und (Heavy))“, der Hinweis beschreibt den Kamerapfad (FPV und Sichtflug, kein Video) und die Statuszeile sagt entweder „zeichnet den Kamerapfad auf“ (Flashback bereit) oder „nimmt nichts auf“ (Flashback fehlt oder passt nicht), nie beides; Feld im Sichtflug; REC im OSD |
| `content/PartItem`, `PartTooltips` | Statuszeile „Flashback erkannt“/„Braucht die Mod Flashback (Fabric)“, Zeile „Aufnahme: Kamerapfad für Flashback“ |
| `platform/IPlatformHelper#modVersion` | Version einer anderen Mod (Fabric, NeoForge) |
| `guide/scenes/ActionCamScene` | fünfter Schritt „Kamerapfad für Flashback“ |

## Lange Flüge (für Spieler)

Flashbacks Zeitleiste kann nur eine begrenzte Zahl Keyframes auf einmal zeichnen – je nach Bildschirmskalierung etwa
600 bis 1.900 pro Flug. Darüber beendet sich das Spiel beim Öffnen der Aufnahme. Propwash passt deshalb auf:

- Während der Aufnahme zeigt das OSD-Element „Flashback-Zeitleiste“ (`FB 43%`), wie voll die Zeitleiste mit diesem Flug
  wäre. Ab 75 % wird es gelb, über 100 % erscheint bei eingeschalteter Obergrenze ein kleines Zeichen „wird
  ausgedünnt“.
- Mit der „Flashback-Obergrenze“ (Standard an, Tab Action-Cam) dünnt Propwash lange Flüge beim Speichern weiter aus,
  bis sie passen. Der Toast nennt dann die erreichte Genauigkeit, etwa „bis 4 cm / 2°“.
- Ohne Obergrenze bleibt jeder Keyframe erhalten. Wird es zu viel, blinkt `FB` rot, die Warnung „FLASHBACK VOLL“
  erscheint und der Toast warnt nach dem Speichern.
- Der Regler „Vereinfachung“ legt fest, wie genau der Pfad mindestens bleibt. Die Vorschau darunter zeigt deinen letzten
  Flug (ab 10 Sekunden, sonst einen Beispielflug): tatsächlicher Pfad hell, gespeicherter Pfad gelb, die größte
  Abweichung rot umkreist, dazu Keyframes, Budget und wie viele Minuten mit dieser Einstellung passen. „Größer“ öffnet
  eine Ansicht mit Zoom, Zeit-Schieber und dem Verlauf der Abweichung.
- Alte Aufnahmen, die schon zu groß sind: „Aufnahmen prüfen“ im Tab Action-Cam listet die Propwash-Szenen aller
  Flashback-Aufnahmen und dünnt eine zu große Szene mit Vorschau aus. Die alte Datei bleibt als `.bak` daneben liegen.

![OSD-Element „Flashback-Zeitleiste“ bei aktiver Obergrenze](../screenshots/osd_flashback_budget_thinning.png)

## Grenzen

- Flashback speichert Kamera-Keyframes mit Euler-Winkeln und ganzzahligen Ticks. Bei sehr schnellen Flips (über etwa
  600°/s) weicht das Bild zwischen zwei Ticks um bis zu etwa 20° ab; an den Keyframes stimmt es auf 2°.
- Flashbacks Interpolation verteilt das Tempo zwischen Keyframes selbst: genau unter 4 cm, ausgedünnt bis etwa 0,3 Blöcke
  vor oder nach der echten Position (die Bahn selbst bleibt auf etwa 1 cm).
- Die Drohne in der Wiederholung kommt aus Flashbacks Aufnahme der Entity (Position pro Tick, Lage vom Server); sie läuft
  etwa einen Tick hinter der Kamera. Nahaufnahmen mit freier Kamera näher als 0,5 Blöcke blenden sie aus.
- Schwingungen schneller als etwa 10 Hz (feines Propwash-Zittern) kann ein Pfad mit einem Keyframe je Tick nicht zeigen. Seit
  0.6.0 fallen sie vor dem Abtasten weg, statt als langsames Schaukeln im Export zu landen; über 6 Hz zeigt der Export
  daher etwas weniger als das Live-Bild. Gemessen am Propwash-Flug der Simulation (`FlashbackPropwashTest`): Regler „Aus“
  Wackeln 0,5–8 Hz Faktor 0,97–0,99 des Live-Bilds, Schein-Wackeln 0,5–3 Hz höchstens 0,02° (vorher 0,08–0,23°).
- Mit Vereinfachung hält der gespeicherte Pfad die Toleranz ein, schwingt dazwischen aber auf seiner eigenen Spline:
  Standard (1 cm / 0,5°) Faktor 0,98–1,03, bei 50 % bis 1,2, bei 100 % bis 1,35 des Live-Wackelns (Schein-Wackeln
  0,5–3 Hz im Propwash: Standard etwa 0,1°, 50 % etwa 0,6°, 100 % etwa 1,8°). Wer Propwash genau wie live sehen will,
  nimmt „Aus“; die Obergrenze für lange Flüge vergröbert ebenso.
- Getestet mit Flashback 0.43.6 für Minecraft 26.3. Ändert Flashback Klassen oder Felder, meldet der Tab „nicht
  unterstützt“ mit Grund, und die Kamera nimmt nichts auf.

## Prüfungen

- Budget (0.5.2): `FlashbackBudgetTest`, `SimplificationTest`, `FlashbackSplineTest` (auch gegen die im Selbsttest mit
  Flashbacks `CatmullRom` erzeugte Tabelle), `TrackThinnerTest`, `SampleFlightTest`, `OsdFlashbackBudgetTest`; Selbsttests
  `video: Flashback-Budget – …` (siehe [`flashback-keyframe-budget.md`](flashback-keyframe-budget.md)).
- JUnit: `CameraAnglesTest`, `EulerPathTest` (Flips ohne Verdrehen: ohne Korrektur über 90°, mit unter 25°),
  `CameraTrackTest` (Abtastung, Ausdünnen, Sichtfeld, Puffer), `ReplayViewTest`.
- Tiefpass vor dem Abtasten (0.6.0, Befund RC2-5): `AntiAliasTest` (1° bei 13,3–33 Hz ergibt im Export unter 0,05° bei
  der Faltungsfrequenz, vorher bis 0,95°; 0,5–3 Hz bei 30, 60 und 144 Bildern pro Sekunde mit und ohne Bildzeit-Zittern
  Faktor 0,95–1,01; gleichmäßige Drehung exakt von 500°/s bis 3461°/s bei 30–240 Bildern pro Sekunde; schnelle Drehungen
  bis 3461°/s mit 20 % Schwankung bei 5 Hz: Tick-Lage höchstens 3 % der Schwankung neben der Drehung, Export bis 2826°/s
  höchstens 0,5° schlechter als mit Punktproben, Befund AUTO44-1; Position und Sichtfeld unverändert),
  `FlashbackPropwashTest` (Whoop, Freestyle und Racer fallen achtmal in den eigenen Abwind, 60 und 144 Bilder pro
  Sekunde: Schein-Wackeln 0,5–3 Hz im Propwash unter 0,05°, Wackeln 0,5–8 Hz Faktor 0,93–1,03, Standard-Vereinfachung
  unter 1,05). Auswertung in `PathWobble` (devtest: Abweichung von der geglätteten Live-Lage, Bandpass per FFT).
- Selbsttest „video: Action-Cam (Flashback) – Propwash im Kamerapfad wie live“ (Fabric mit Flashback): 18 m steigen, dann
  dreimal 0,7 s mit wenig Gas fallen lassen, 1,3 s mit 1,05-fachem Schwebegas im eigenen Abwind sinken und zurück auf
  die Starthöhe; Kamerapfad mit Regler „Aus“. Danach Flashbacks eigene Interpolation der Szene mit 120 Hz gegen den Pfad
  aus jedem Bild, nur in Zeiten mit Propwash über 0,3 (mindestens 1 s). Grenzen: Schein-Wackeln 0,5–3 Hz höchstens
  0,01° (gemessen 0,001°, ohne Tiefpass 0,024°), Wackeln 0,5–8 Hz Faktor 0,9–1,05. Die Kurven stehen in
  `propwash-flashback-wobble.csv` im Run-Verzeichnis.
- Selbsttest (Fabric mit Flashback im Mod-Ordner des Selbsttests): echter Flug; beim Schweben in den ersten Sekunden der
  Aufnahme wird jede neue Probe des Kamerapfads mit der echten Spielkamera desselben Bildes verglichen (Position, Blick-
  und Oben-Richtung, waagerechtes Sichtfeld aus `Camera#getFov` und Fenster-Seitenverhältnis) und der Uptilt unabhängig
  gegen die Lage der Drohne (`QuadcopterEntity#orientation`) geprüft, damit ein falsches Vorzeichen oder ein fehlender
  Linsen-Versatz auffällt; dann Szene in Flashbacks eigenem Editorzustand
  geladen und mit Flashbacks eigener Interpolation gegen den Flug verglichen, Anhängen an eine laufende Aufnahme mit
  Flip, Flashback-Aufnahme beendet und gespeichert, Wiederholung in Flashback geöffnet: Kamera folgt dem Pfad, Drohne
  um die Kamera ausgeblendet. Ohne Flashback (NeoForge, Fabric ohne Jar): Hinweis statt Aufnahme. Auf beiden Wegen prüft
  „Tab Action-Cam passt zur Action-Cam (Flashback) der Drohne“ Hinweis und Statuszeilen des Tabs mit der geflogenen Drohne.
- Bildschirmfotos: `drone_closeup_action_cam_flashback.png`, `tooltip_action_cam_flashback.png` (deutsch `_de`),
  `configurator_action_cam_flashback.png`, `flashback_cam_fpv.png`, `flashback_cam_handed_over.png`,
  `flashback_replay_fpv.png`, `flashback_replay_path.png`, ohne Flashback `flashback_cam_needs_flashback.png`.
