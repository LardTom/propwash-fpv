# Action-Cam (Flashback) – Keyframe-Budget der Zeitleiste

Stand 14.10.2026, Flashback 0.43.6 (MC 26.3). Befund aus einem Absturz beim Öffnen einer Aufnahme (Propwash 0.5.1) und
Vorschlag für eine Anzeige im OSD und eine abschaltbare Obergrenze. **Umgesetzt in Propwash 0.5.2** – was davon
abweicht und was gemessen wurde, steht im Abschnitt [Umsetzung (0.5.2)](#umsetzung-052) am Ende. Grundlage ist
[`flashback-cam.md`](flashback-cam.md).

## Kurzfassung

- Flashbacks Zeitleiste kann nur eine begrenzte Zahl Keyframes gleichzeitig zeichnen. Liegen mehr in der sichtbaren
  Szene, beendet sich das Spiel beim Öffnen der Aufnahme ohne Crash-Report.
- Die Grenze ist fest: 65.536 Eckpunkte (Vertices) pro ImGui-Zeichenliste. Sie hängt nicht vom RAM ab, sondern von der
  UI-Skalierung in Flashback: etwa **2.048**, **1.365** oder **682** Keyframes, abzüglich einer Reserve für den Rest der
  Zeitleiste.
- Die Action-Cam schreibt standardmäßig einen Keyframe pro Tick. Ab etwa 1½ bis 2 Minuten Flug ist die Grenze erreicht.
  „Ausgedünnt“ hilft bei FPV-Flügen kaum (−14 % im gemessenen Flug).
- Vorschlag: Propwash rechnet während der Aufnahme aus, wie voll das Budget ist, und zeigt das im OSD an. Beim Speichern
  dünnt eine Obergrenze die Spur automatisch aus, bis sie passt. Die Obergrenze lässt sich abschalten, dann warnt das OSD
  nur.
- Ein Regler „Vereinfachung“ ersetzt die Auswahl „Jeder Tick / Ausgedünnt“. Eine Vorschau zeigt dazu den tatsächlichen
  und den gespeicherten Pfad übereinander, mit Abweichung, Keyframe-Zahl und der Fluglänge, die noch passt.

## Befund

### Was passiert ist

Aufnahme `2026-10-13T14_05_49` (Driftpark, 3.329 Ticks ≈ 2:46 min, Szene „Propwash · Flight 1“). Beim Öffnen in
Flashback lädt die Welt vollständig, im ersten Bild der Editor-Oberfläche kommt:

```
Dear ImGui Assertion Failed: draw_list->_VtxCurrentIdx < (1 << 16) && "Too many vertices in ImDrawList using 16-bit indices. Read comment above"
  at imgui.moulberry90.ImGui.render(ImGui.java:271)
  at com.moulberry.flashback.editor.ui.ReplayUI.drawOverlayInternal(ReplayUI.java:913)
```

Danach beendet sich die JVM. Es gibt keinen Crash-Report und kein `hs_err`, nur die Zeilen im `latest.log`.

Die ersten drei Versuche am selben Tag stürzten schon früher ab, mit „Chunky is not loaded“. Distant Horizons fragt beim ersten Chunk-Laden des
Replay-Servers Chunky ab, bevor Chunky gestartet ist. Das ist ein anderes Problem und durch Entfernen von Chunky erledigt.

### Ursache

- Flashback zeichnet seinen Editor mit Dear ImGui (imgui-java, Paket `imgui.moulberry90`). Die Indizes sind 16 Bit
  breit, und Flashbacks Renderer setzt `ImGuiBackendFlags_RendererHasVtxOffset` nicht. Deshalb darf **eine Zeichenliste
  höchstens 65.536 Eckpunkte** haben. Jedes ImGui-Fenster hat eine eigene Zeichenliste, also zählt das ganze
  Zeitleisten-Fenster zusammen.
- `TimelineWindow.renderKeyframes` zeichnet jeden Keyframe **aller Spuren der aktiven Szene**, der im sichtbaren
  Bereich liegt (`minTicks − 10` … `minTicks + availableTicks + 10`). Keyframes im selben Pixel werden nicht
  zusammengefasst. Beim Öffnen ist die Zeitleiste ganz herausgezoomt (`zoomMin 0`, `zoomMax 1`), also zählt der
  ganze Flug.
- Andere Szenen zählen nicht. Da die Action-Cam **eine Szene pro Flug** anlegt, gilt das Budget pro Flug.
- Der RAM (`-Xmx`) spielt keine Rolle.

### Was ein Keyframe kostet

`Keyframe.drawOnTimeline` zeichnet je nach Interpolation eine eigene Form. Propwash setzt die Kamera-Keyframes auf
`SMOOTH` und die Sichtfeld-Keyframes auf `HOLD`.

| Interpolation | Form |
|---|---|
| `SMOOTH` (Kamera-Spur) | `addCircleFilled(x, y, keyframeSize)`, gefüllter Kreis |
| `LINEAR` | zwei Dreiecke (Raute) |
| `EASE_*`, `HOLD` | drei Dreiecke, teils mit Rechteck |

`keyframeSize = ReplayUI.scaleUi(10) = (int) (10 × UI-Skalierung)`.

Bei einem Kreis mit automatischer Segmentzahl (ImGui 1.90) ergibt sich die Zahl der Eckpunkte so:

```
segmente  = clamp(aufrunden_gerade(ceil(π / acos(1 − 0,30 / r))), 4, 512)   // r = ceil(Radius), Fehler 0,30 px
schritt   = clamp(48 / segmente, 1, 12)                                      // Ganzzahl-Division, Tabelle mit 48 Punkten
punkte    = 48 / schritt                                                     // (+1 Probe, −1 doppelter Endpunkt)
eckpunkte = 2 × punkte                                                       // Kantenglättung der Füllung verdoppelt
```

| UI-Skalierung | Radius | Eckpunkte pro Keyframe | Grenze ohne Reserve |
|---|---|---|---|
| bis 1,5 | 10–15 px | 32 | 2.048 Keyframes |
| 1,75 bis 3,5 | 17–35 px | 48 | 1.365 Keyframes |
| ab 3,6 | ab 36 px | 96 | 682 Keyframes |

Mehr als 96 werden es nicht, weil die Kreistabelle nur 48 Punkte hat.

### UI-Skalierung

Laut Bytecode gilt `ReplayUI.getUiScale() = globalScale × contentScale`.

- `globalScale`: Flashbacks eigener Zoom der Editor-Oberfläche, 0,25 bis 4, in Schritten von 1/16. Er steht nicht in
  `config/flashback/flashback.json`. Vermutlich gilt nach jedem Spielstart wieder 1,0. Das ist noch zu prüfen.
- `contentScale`: `SDL_GetWindowDisplayScale(fenster)` geteilt durch die Pixeldichte, nach unten begrenzt und
  gerundet auf 1/16. Auf dem Mac mit Retina ergibt das vermutlich 1,0, weil ImGui dort in Punkten rechnet. Unter Windows ergibt es
  die Anzeigeskalierung, also 1,5 bei 150 % und 2,0 bei 200 %.

Typisch, aber nicht gemessen: Mac 1,0 (32 Eckpunkte), Windows-Laptop 1,25 bis 1,5 (32), 4K-Monitor mit 200 % (48).

### Reserve

Der Rest der Zeitleiste braucht ebenfalls Eckpunkte: Raster, Zeitbeschriftung (4 pro Zeichen), Spurnamen, Marker,
Export-Bereich, Wiedergabekopf und Knöpfe. Gemessen ist das noch nicht. Geschätzt sind einige tausend Eckpunkte, im
Vorschlag stehen 6.000 als vorsichtiger Wert, bis ein Selbsttest die echte Zahl liefert.

### Gemessene Aufnahme

| | Wert |
|---|---|
| Ticks im Flug | 3.329 |
| Keyframe-Dichte in der Konfiguration | „Ausgedünnt“ (`REDUCED`, 1 cm / 0,5°, höchstens 10 Ticks Abstand) |
| Kamera-Keyframes in der Szene | 2.854 (Lücken von 2 bis 4 Ticks durch das Ausdünnen) |
| Sichtfeld-Keyframes | 2 |
| Eckpunkte bei UI-Skalierung 1,0 | ≈ 91.000 + Reserve, Grenze 65.536 |

„Ausgedünnt“ hat bei diesem FPV-Flug nur 14 % der Keyframes eingespart, weil sich die Kamera fast jeden Tick um mehr als
1 cm oder 0,5° gegenüber der Geraden ändert. Mit „Jeder Tick“ wären es 3.329 gewesen.

### Falsche Annahme in `flashback-cam.md`

In der Entscheidungstabelle steht: „Höchstens 15 Minuten je Flug – Flashbacks Zeitleiste zeichnet jeden Keyframe;
18 000 Keyframes je Spur sind noch bedienbar“. Das stimmt mit Flashback 0.43.6 nicht. Der Selbsttest fliegt nur
kurz und hat die Zahl nie geprüft. Ob ältere Flashback-Versionen anders gezeichnet haben, ist ungeprüft.

### Sofortmaßnahme für die betroffene Aufnahme

`flashback/editor_states/5a20d0c1-dc05-4744-8a64-80471f98c378.json` wurde außerhalb des Spiels mit demselben Verfahren wie
`CameraTrack.thin` ausgedünnt (lineare Prüfung, Toleranz schrittweise angehoben): 2.854 → 920 Kamera-Keyframes,
höchstens etwa 6 cm und 3° Abweichung gegen die lineare Verbindung. Das Original liegt als `….json.bak` daneben. Im
Spiel ist das noch nicht geprüft.

## Vorschlag

### 1. Budget-Rechnung (`video/record/path/FlashbackBudget`, reines Java)

```
VERTEX_LIMIT = 65_536
RESERVE      = 6_000                      // bis zur Messung im Selbsttest
nutzbar      = VERTEX_LIMIT − RESERVE

kosten(uiScale) = eckpunkteKreis((int) (10 × uiScale))   // 32 / 48 / 96, Formel oben
belegt          = kameraKeyframes × kosten(s) + sichtfeldKeyframes × 96
anteil          = belegt / nutzbar                       // 1,0 = voll
maxKeyframes(s) = (nutzbar − sichtfeldKeyframes × 96) / kosten(s)
```

- Mit welcher UI-Skalierung gerechnet wird: `s = max(geschätzte Skalierung, 2,0)`. Die Szene wird beim Aufnehmen
  geschrieben, geöffnet wird sie vielleicht an einem anderen Bildschirm. Mit 2,0 als Untergrenze passen 48 Eckpunkte pro
  Keyframe, das deckt alles bis Skalierung 3,5 ab. Ergebnis: höchstens etwa **1.240 Kamera-Keyframes** pro Flug.
- Geschätzte Skalierung: gleiche Rechnung wie Flashback, also `SDL_GetWindowDisplayScale` durch Pixeldichte des
  Spielfensters (Minecraft 26.3 läuft auf SDL3). Ist `ReplayUI` geladen, wird zusätzlich `globalScale` per Reflection
  über `FlashbackBridge` gelesen, sonst 1,0.
- Liefert die Schätzung 3,6 oder mehr, rechnet das Budget mit 96 Eckpunkten, also etwa 620 Keyframes.
- Reine Funktion ohne Minecraft-Abhängigkeiten, damit sie im JUnit-Test gegen die Tabelle oben geprüft werden kann.

### 2. Live-Zählung während der Aufnahme

- `FlashbackCam.onClientTick` führt einen Zähler für Kamera-Keyframes mit, wie sie beim Speichern entstünden:
  - Regler auf „Aus“: ein Keyframe pro neuem Tick.
  - Sonst dasselbe gierige Verfahren wie `CameraTrack.thin` mit der Toleranz des Reglers (Abschnitt 5), nur
    fortlaufend. Ein Anker, neue Ticks werden gegen `fits(anker, j)` geprüft, höchstens so viele Prüfungen pro Tick, wie
    die Spanne der Stufe erlaubt (10 bis 40).
- Neue Abfrage `FlashbackCam.budget()` mit `anteil`, `keyframes`, `maxKeyframes` und `limitAktiv`.
- `LiveOsdSource.fillRecording` füllt daraus neue Felder in `OsdData`, etwa `flashbackBudget` (Anteil, `NaN` wenn keine
  Flashback-Aufnahme läuft) und `flashbackLimit` (Obergrenze an oder aus). Die Vorschaudaten in `OsdData` (Zeile ~387)
  bekommen einen Beispielwert für den OSD-Editor.

### 3. OSD-Element „Flashback-Zeitleiste“

- Neues `OsdElement.FLASHBACK_BUDGET` in `Group.STATUS`, standardmäßig an, Position in der rechten Spalte unter `RECORDING`
  (zum Beispiel `28, 3`, wenn `ON_TIME` aus ist, sonst ein freier Platz). Es ist nur sichtbar, solange die
  Flashback-Kamera aufnimmt (`OsdRenderer`-Sichtbarkeit wie bei `RECORDING`).
- Anzeige im Stil der übrigen Elemente: Kürzel plus Prozent, etwa `FB 43%`.

| Zustand | Farbe | Text |
|---|---|---|
| unter 75 % | normal | `FB 43%` |
| 75 bis 100 % | Vorsicht (`caution()`) | `FB 91%` |
| über 100 %, Obergrenze an | Vorsicht | `FB 100%` und ein Zeichen für „wird ausgedünnt“ |
| über 100 %, Obergrenze aus | Warnung, blinkend | `FB 130%` |

- Bei abgeschalteter Obergrenze und über 100 % zeigt `WARNINGS` zusätzlich „FLASHBACK VOLL“ (`OsdWarnings`), wie
  andere Betaflight-Warnungen.
- Bei eingeschalteter Obergrenze ist über 100 % kein Fehler, nur ein Hinweis, dass die Spur gröber wird. Die genaue
  Toleranz steht nach dem Speichern im Toast und im Tab.

### 4. Obergrenze beim Speichern (Standard: an)

- `CameraTrack.of(poses, density, maxKeyframes)`: Liegt die Zahl über `maxKeyframes`, werden Positions- und
  Winkeltoleranz schrittweise angehoben (Faktor 1,15, wie bei der Reparatur oben) und die Spanne auf 40 Ticks erweitert,
  bis die Spur passt. Mit 40 Ticks Spanne bleiben bei 15 Minuten höchstens 450 Keyframes, das passt immer.
- Rechnung auf dem Hintergrund-Thread wie heute (`supplyAsync`). Für 15 Minuten und mehrere Durchläufe liegt das im
  Bereich weniger hundert Millisekunden. Bei Bedarf kann eine Binärsuche über den Faktor das abkürzen.
- `Handover` bekommt die erreichte Toleranz dazu (`tolerance cm`, `tolerance °`). Der Toast nach dem Speichern nennt sie,
  wenn ausgedünnt wurde: „Kamerapfad für Flashback ausgedünnt: 1.240 Keyframes, bis 4 cm / 2°“.
- Abgeschaltet: Die Spur wird wie heute geschrieben. Liegt sie über dem Budget, warnt der Toast: „Diese Szene ist zu
  groß für Flashbacks Zeitleiste. Beim Öffnen der Aufnahme kann das Spiel abstürzen.“

### 5. Einstellungen: Regler „Vereinfachung“ und Obergrenze

**Regler „Vereinfachung“** (ersetzt die Auswahl „Jeder Tick / Ausgedünnt“):

- Ganz links steht „Aus“, das heißt ein Keyframe pro Tick. Rechts davon wächst die Toleranz logarithmisch von 0,5 cm bis
  20 cm (Position = 0,5 cm × 40^Stelle, Stelle 0 bis 1). Der Winkel ist gekoppelt wie heute (1 cm ↔ 0,5°, also 50° pro Meter). Die erlaubte Spanne zwischen zwei
  Keyframes steigt mit, von 10 auf 40 Ticks.
- Die Beschriftung zeigt den Wert, etwa „Vereinfachung: 4 cm / 2°“.
- Anhaltspunkte:

| Stelle | Position | Winkel | Spanne | entspricht |
|---|---|---|---|---|
| ganz links | – | – | 1 Tick | heute „Jeder Tick“ |
| ≈ 19 % | 1 cm | 0,5° | 10 Ticks | heute „Ausgedünnt“ |
| ≈ 67 % | 6 cm | 3° | 30 Ticks | etwa die Reparatur dieser Aufnahme (920 Keyframes) |
| ganz rechts | 20 cm | 10° | 40 Ticks | grob, für sehr lange Flüge |

- `RecordingConfig.flashbackSimplify` (0 bis 1) ersetzt `flashbackDensity`. Alte Konfigurationen werden übernommen:
  `EVERY_TICK` wird 0, `REDUCED` wird die Stelle für 1 cm. `KeyframeDensity` wird intern zu einer Toleranz mit
  Position, Winkel und Spanne.
- Standardwert: die Stelle für 1 cm, also das heutige „Ausgedünnt“. Mit der Obergrenze ist das bei kurzen Flügen so
  gut wie verlustfrei und spart bei langen Flügen das stärkere Ausdünnen. Ob der Standard so bleibt, entscheidet der
  Owner.

**Obergrenze:**

- `RecordingConfig.flashbackKeyframeLimit = true` (Standard), im Codec mit Standardwert, damit alte Konfigurationen
  weiter laden.
- `ActionCamTab`: Schalter „Flashback-Obergrenze“ direkt unter dem Regler, mit Tooltip (Text unten).
- Der Regler legt fest, wie stark **mindestens** vereinfacht wird. Die Obergrenze vergröbert beim Speichern nur dann
  weiter, wenn der Flug sonst nicht ins Budget passt.

### 6. Pfad-Vorschau „Tatsächlicher Pfad / Gespeicherter Pfad“

Unter dem Regler zeigt eine Vorschau, was die Einstellung mit einem echten Flug macht. Sie aktualisiert sich beim
Ziehen.

**Was sie zeigt:**

- **Tatsächlicher Pfad** (Actual path): die Kamera pro Tick, dünne helle Linie; zwischen den Ticks so verbunden, wie
  Flashback sie mit einem Keyframe je Tick („Aus“) abspielt (seit dem Spieltest 15.10., vorher gerade Stücke).
- **Gespeicherter Pfad** (Saved path): das, was Flashback aus den behaltenen Keyframes abspielt, in Propwash-Akzentfarbe.
  Die Keyframes selbst sind Punkte auf dieser Linie.
- **Blickrichtung**: alle 10 Ticks ein kurzer Strich für beide Pfade. Bei FPV fällt ein Winkelfehler im Video stärker
  auf als ein paar Zentimeter Versatz.
- **Größte Abweichung**: ein roter Ring um die Stelle, an der die beiden gezeichneten Linien am weitesten
  auseinanderliegen, je Ansicht: von oben der waagerechte Abstand der Pfade, von der Seite der Höhenunterschied zur
  gleichen Zeit (Abschnitt „Ring der größten Abweichung“ unten). Ist der Abstand auf dem Bildschirm größer als der
  Ring, verbindet eine dünne rote Linie die beiden Stellen. Die Legende nennt Art und Wert („Größter Abstand der
  Pfade: 0,42 Blöcke“ bzw. „Größter Höhenunterschied: …“).
- **Kennzahlen** in einer Zeile darunter, etwa:
  `1.180 Keyframes · Budget 95 % · neben der Bahn max. 3 cm · zur gleichen Zeit max. 0,2 Blöcke · Winkel max. 2,1°`
- **Fluglänge, die noch passt**: aus der Keyframe-Rate des Vorschauflugs hochgerechnet, etwa „Mit dieser Einstellung
  passen etwa 3:40 min in Flashbacks Zeitleiste“. Bei abgeschalteter Obergrenze ist das die wichtigste Zahl.

**Damit man etwas sieht:**

- Zentimeter sind auf einem 100-m-Flug unsichtbar. Die kompakte Ansicht zeigt deshalb links den ganzen Flug und rechts
  eine **Lupe** auf die Stelle der größten Abweichung, automatisch so gezoomt, dass beide Linien unterscheidbar sind.
- Umschalter für die Ansicht: von oben (X/Z) oder von der Seite (Strecke/Höhe).
- Ein Knopf „Größer“ öffnet einen eigenen Bildschirm mit Zoom und Verschieben per Maus. Er zeigt außerdem einen Graphen
  der Abweichung über die Zeit (Position und Winkel) und einen Schieber für die Zeit, der die Stelle auf beiden Pfaden
  markiert.

**Ring der größten Abweichung (Spieltest 15.10.):** Bis dahin saß der Ring an der größten Abweichung *zur gleichen
Zeit*. Auf einer schnellen Geraden ist das meist ein reiner Zeitversatz längs der Bahn (die gespeicherte Kamera ist
dort ein paar Zentimeter früher oder später), in der Draufsicht liegen beide Linien aufeinander. Die sichtbare
Abweichung an einer Kurve (der gespeicherte Pfad schneidet sie oder schwingt aus) blieb ohne Ring. Außerdem wurde nur
an ganzen Ticks gemessen, gezeichnet wird der gespeicherte Pfad aber mit vier Zwischenschritten je Tick. Jetzt misst
`PathPreview` den Abstand der beiden *gezeichneten* Kurven in beide Richtungen und an jedem Zwischenschritt
(`topGap`: waagerecht, `sideGap`: Höhe zur gleichen Zeit, `maxOffPath`: räumlich für die Kennzahl „neben der Bahn“).
Der tatsächliche Pfad zwischen den Ticks ist Flashbacks Spline durch jeden Tick; so ist bei „Aus“ jeder Abstand genau
0, und eine Abweichung, die Flashback auch mit jedem Tick hätte, gilt nicht als Fehler der Einstellung. Graph und
Kennzahl „zur gleichen Zeit“ zeigen weiter den Zeitversatz. Ursache war also die Messung des Rings, nicht das
Ausdünnen: der Prüfdurchgang hält den Fehler zur gleichen Zeit an jedem Tick unter Toleranz × 1,5, und damit auch den
räumlichen Abstand (JUnit `TrackThinnerTest`: `maxOffPath` ≤ Toleranz × 1,5 bei allen Reglerstellungen). Der Selbsttest
`video: Flashback-Budget – Pfad-Vorschau: Ring an der sichtbaren Abweichung einer Haarnadelkurve` zeigt das an einer
Haarnadelkurve wie im Spieltest (`flashback_preview_corner_gap.png`); die JUnit `PathPreviewGapTest` prüft, dass der Ring
an der Kurve sitzt, während die größte Abweichung zur gleichen Zeit auf der Geraden liegt und fast nur längs der Bahn
zeigt.

**Ein Ring, ein Zeitpunkt:** Der Graph „Abweichung über die Zeit“ markierte zunächst sein eigenes Maximum (größte
Abweichung zur gleichen Zeit) mit demselben roten Ring wie den Ring der größten sichtbaren Lücke auf dem Pfad. An der
Haarnadel zeigten beide Ringe dann auf verschiedene Zeitpunkte (Graph bei 0:00, Pfad und Zeit-Cursor bei 0:05). Jetzt
sitzt der Ring im Graphen genau zur Zeit der Lücke der aktuellen Ansicht (`PathPreviewDraw.gapTime`: `topGap` von
oben, `sideGap` von der Seite) auf der Kurve, zwischen zwei Ticks linear eingesetzt; ohne sichtbare Lücke gibt es
keinen Ring. Das Maximum zur gleichen Zeit steht weiter als Zahl im Kopf des Graphen („Position …“). Die Legende
„Größter Abstand der Pfade …“ bzw. „Größter Höhenunterschied …“ gilt damit für beide Ringe, und „Größte Abweichung“ springt mit dem Zeit-Cursor an denselben
Zeitpunkt. Der Haarnadel-Selbsttest prüft, dass Graph-Ring und Pfad-Ring in beiden Ansichten denselben Zeitpunkt
haben.

**Woher der Vorschauflug kommt:**

1. Der letzte Flug dieser Sitzung mit Flashback-Kamera (`FlashbackCam.lastPath()`, liegt pro Tick im Speicher).
2. Sonst ein mitgelieferter Beispielflug als Ressource, etwa 30 Sekunden mit Flip, Power-Loop und Gap-Durchflug, einmal
   aus einem echten Flug aufgenommen. So funktioniert die Vorschau auch vor dem ersten Flug.
3. Im Reparatur-Dialog (Abschnitt 8) die Keyframes der vorhandenen Szene. War die Aufnahme schon ausgedünnt, ist das
   der beste verfügbare „tatsächliche“ Pfad. Die Vorschau sagt das dann dazu.

**Genauigkeit:**

- Der gespeicherte Pfad wird mit derselben Interpolation gerechnet wie in Flashback. Das ist laut Bytecode eine
  zentripetale Catmull-Rom-Spline (`com.moulberry.flashback.spline.CatmullRom`, Gewicht `Abstand^0,5`,
  Barry-Goldman-Form mit den Keyframe-Zeiten), getrennt für Position und Winkel. Propwash bekommt dafür eine kleine
  eigene Nachbildung (`video/record/path/FlashbackSpline`), die per JUnit gegen Werte aus Flashback geprüft wird.
- „Neben der Bahn“ und „zur gleichen Zeit“ werden getrennt ausgewiesen. Flashback verteilt das Tempo zwischen
  Keyframes selbst, so steht es schon in `flashback-cam.md`. Eine Spur kann also genau auf der Bahn liegen und trotzdem
  0,2 Blöcke vor- oder nachlaufen.
- `CameraTrack.thin` prüft heute gegen die lineare Verbindung. Das ist eine Näherung. Ein zweiter Durchgang prüft jede
  Strecke gegen die Spline und setzt einen Keyframe in die Mitte, wo die Toleranz um mehr als 50 % überschritten wird.
  Dann stimmen Regler, Vorschau und Flashback überein.

**Technik:**

- Neu: `video/client/record/PathPreviewWidget` für die kompakte Ansicht im Tab und `PathPreviewScreen` für die große
  Ansicht. Linien als schmale Quads über `GuiGraphics`, in der großen Ansicht höchstens so viele Segmente, wie Pixel da
  sind (zusammengefasst nach Bildschirmspalte).
- Die Berechnung (Ausdünnen, Spline, Kennzahlen) läuft auf dem Hintergrund-Thread, 100 ms nach der letzten
  Reglerbewegung. Bis das Ergebnis da ist, bleibt die alte Ansicht stehen. Beim 30-Sekunden-Beispielflug dauert das
  wenige Millisekunden.
- Später denkbar: beide Pfade nach einem Flug in der Welt anzeigen, mit dem System der Drohnen-Spuren
  ([`drone-trails.md`](drone-trails.md)).

### 7. Erklärung für Spieler

Tooltip `propwash.video.record.tab.flashback_simplify.tooltip`:

> **Deutsch:** Wie stark der Kamerapfad für Flashback vereinfacht wird. „Aus“ speichert jeden Tick. Weiter rechts
> speichert Propwash nur die Punkte, die nötig sind, damit Flashbacks Kamera höchstens so weit vom echten Flug abweicht
> wie angezeigt. Die Vorschau darunter zeigt den Unterschied.
>
> **English:** How much the camera path is simplified for Flashback. "Off" saves every tick. Further right, Propwash
> only keeps the points needed so that Flashback's camera stays within the shown distance of the real flight. The
> preview below shows the difference.

Tooltip `propwash.video.record.tab.flashback_limit.tooltip`:

> **Deutsch:** Flashbacks Zeitleiste kann nur eine begrenzte Zahl Keyframes auf einmal zeichnen, je nach
> Bildschirmskalierung etwa 600 bis 1.900 pro Flug. Darüber stürzt das Spiel beim Öffnen der Aufnahme ab. Mit der
> Obergrenze dünnt Propwash lange Flüge beim Speichern so weit aus, dass sie passen; der Pfad wird dabei um wenige
> Zentimeter ungenauer. Wie voll der Speicher ist, zeigt das OSD-Element „Flashback-Zeitleiste“. Ohne Obergrenze bleibt
> jeder Keyframe erhalten, lange Flüge lassen sich dann aber eventuell nicht öffnen.
>
> **English:** Flashback's timeline can only draw a limited number of keyframes at once, roughly 600 to 1,900 per
> flight depending on display scaling. Beyond that the game crashes when the replay is opened. With the limit on,
> Propwash thins long flights when saving so they fit; the path becomes a few centimetres less exact. The OSD element
> "Flashback timeline" shows how full the budget is. Without the limit every keyframe is kept, but long flights may not
> open.

Für das Spielerhandbuch ergänzt `flashback-cam.md` einen Abschnitt „Lange Flüge“ mit demselben Inhalt und einem
Bildschirmfoto des OSD-Elements.

### 8. Bestehende Aufnahmen (optional)

Aufnahmen, die schon zu groß sind, öffnen nicht mehr. Möglich wäre ein Knopf „Für Flashback ausdünnen“ im
`RecordingsScreen`, der über `EditorStateManager.get(uuid)` die Propwash-Szenen der Aufnahme lädt, mit
`CameraTrack`-Verfahren und Budget ausdünnt und mit `reset()` speichert. Vorher wird die JSON als `.bak` gesichert. Das
geht ohne Eingriff in Flashbacks Wiedergabe. Der Knopf öffnet denselben Regler mit Pfad-Vorschau (Abschnitt 6) für genau
diese Aufnahme. Der Regler startet an der kleinsten Stufe, die ins Budget passt.

### 9. Meldung an Flashback

Die eigentliche Ursache liegt in Flashback. Der Editor könnte nahe beieinanderliegende Keyframes zu einem Symbol
zusammenfassen, wie es viele Schnittprogramme tun, oder `RendererHasVtxOffset` im Renderer unterstützen. Eine kurze
Meldung im Flashback-Issue-Tracker mit der Assertion und einer Beispielszene hilft allen Mods, die Kamerapfade schreiben.
Propwash braucht das Budget trotzdem, solange ältere Versionen im Umlauf sind.

## Tests

- JUnit `FlashbackBudgetTest`: Eckpunkte pro Radius gegen die Tabelle (10 → 32, 20 → 48, 40 → 96, 80 → 96),
  `maxKeyframes` bei Skalierung 1, 2 und 4, Anteil bei 0 und genau an der Grenze.
- JUnit `CameraTrackTest`: Ausdünnen mit `maxKeyframes` bleibt unter der Grenze, behält ersten und letzten Tick und
  meldet die erreichte Toleranz. Ohne Grenze ist das Ergebnis unverändert.
- Live-Zähler gegen `CameraTrack.of` am Ende derselben Aufnahme: gleiche Zahl bei Regler „Aus“, sonst höchstens
  wenige Keyframes Unterschied.
- JUnit `FlashbackSplineTest`: Nachbildung gegen Werte, die Flashbacks `CatmullRom.position` und `CatmullRom.value` für
  dieselben Keyframes liefern (einmal im Selbsttest erzeugt und als Tabelle eingecheckt).
- JUnit Regler: Abbildung 0 → „Aus“, 1 cm-Stelle → Toleranz von `REDUCED`, 1 → 20 cm / 10°; Übernahme alter
  Konfigurationen (`EVERY_TICK`, `REDUCED`).
- JUnit Vorschau-Kennzahlen: Bei Regler „Aus“ sind alle Abweichungen 0. Die gemeldete größte Abweichung liegt nie über
  der Toleranz × 1,5 (nach dem zweiten Durchgang).
- Selbsttest mit Flashback: Szene mit genau `maxKeyframes` öffnen, Eckpunkte der Zeitleiste über
  `ImGui.getWindowDrawList()` messen, Reserve kalibrieren. Danach mit `maxKeyframes + 10 %` bei abgeschalteter Grenze
  prüfen, dass Toast und OSD-Warnung kommen.
- Bildschirmfotos: OSD-Element in den vier Zuständen, Tab mit Regler, Schalter und Vorschau (Regler auf „Aus“, Mitte,
  ganz rechts), großer Vorschau-Bildschirm mit Lupe und Graph, jeweils deutsch und englisch.

## Offene Fragen

- Wie groß ist die Reserve wirklich? Erst die Messung zeigt, ob 1.240 als Obergrenze bleibt.
- Speichert Flashback `globalScale` doch irgendwo? Falls ja, sollte das Budget diesen Wert lesen.
- Verhält sich Flashback vor 0.43.6 anders? Davon hängt ab, ob die Zahl in `flashback-cam.md` früher gestimmt hat.
- Soll das OSD-Element auch ohne laufende Aufnahme etwas zeigen, etwa den Stand des letzten Flugs, oder nur während der
  Aufnahme?
- Standard des Reglers: das heutige „Ausgedünnt“ (1 cm) oder eher „Aus“, damit sich für bestehende Spieler nichts ändert?
- Passt die Vorschau in den Tab Action-Cam, oder bekommt der Tab nur Regler, Kennzahlen und den Knopf „Vorschau“?
- Wer nimmt den Beispielflug für die Vorschau auf, und welche Manöver soll er enthalten?

## Umsetzung (0.5.2)

Umgesetzt wie vorgeschlagen, mit diesen Abweichungen und Ergänzungen:

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Sichtfeld-Keyframes (`HOLD`) kosten 32 statt 96 Eckpunkte | `HOLD` zeichnet drei bis vier geglättete Dreiecke (je 6) und ein Rechteck (4), also höchstens 28 | 96 (überschätzt, sobald sich das Sichtfeld oft ändert) |
| Nutzbar sind `65.535 − Reserve` Eckpunkte | die Assertion verlangt `_VtxCurrentIdx < 65.536`, 65.536 Eckpunkte wären schon zu viel | `65.536 − Reserve` |
| Spanne des Reglers = 5 Ticks je Zentimeter, begrenzt auf 10…40 | trifft alle drei Anhaltspunkte der Tabelle (1 cm → 10, 6 cm → 30, 20 cm → 40) mit einer Formel | lineare Spanne über die Reglerstellung (6 cm ergäbe 28) |
| Zweiter Durchgang prüft gegen die Spline **zur gleichen Zeit** (Position) und den Winkel, teilt an der Stelle der größten Abweichung statt in der Mitte und wiederholt das bis alles passt | damit hält der gespeicherte Pfad die Toleranz × 1,5 auch im Tempo, die Vorschau zeigt genau das; die Stelle der größten Abweichung konvergiert schneller als die Mitte | Mitte der Strecke; nur „neben der Bahn“ prüfen (Tempo-Fehler bis 0,3 Blöcke blieben) |
| Winkel-Spielraum: die unvermeidbare Abweichung der Euler-Winkel an den beiden Keyframes einer Strecke (`EulerPath`, ≤ 2° nahe der Senkrechten) wird vom Winkelfehler abgezogen | sonst setzt die Prüfung bei Flips jeden Tick als Keyframe, obwohl auch „Aus“ nicht genauer wäre | feste Untergrenze von 2° für alle Winkel |
| Obergrenze sucht den Faktor erst durch Verdoppeln, dann 6 Schritte Halbierung (geometrisch); passt selbst 1024-fach nicht, wird gleichmäßig verteilt | 15 Minuten bei UI-Skalierung 4 rechnen so in etwa einer Sekunde im Hintergrund, und es passt immer | Faktor 1,15 Schritt für Schritt (bis zu 25 Durchläufe) |
| Live-Zähler nutzt dasselbe fortlaufende Verfahren wie das Speichern (`TrackThinner`), nur ohne den abschließenden Prüfdurchgang | bei „Aus“ gleiche Zahl, sonst wenige Keyframes Unterschied (Selbsttest: 70/71, 15/15, 16/17) | eigener Schätzer; alle paar Sekunden komplett neu rechnen |
| UI-Skalierung: Anzeigeskalierung des Spielfensters (`SDL_GetWindowDisplayScale` / Pixeldichte, auf 1/16 abgerundet) mal Flashbacks Editor-Zoom; den Zoom liest Propwash nur, wenn in dieser Sitzung schon eine Wiederholung offen war | `ReplayUI.newGlobalScale` ist nicht gespeichert und startet mit 1,0; ein Zugriff vorher würde Flashbacks Editor-Klasse unnötig initialisieren | Zoom immer per Reflection lesen |
| OSD-Element im Vorlagen-Platz unter REC (MINIMAL 28/5, STANDARD und RACE 28/6), Grundposition 28/3, in FULL 15/4 | REC sitzt je Vorlage anders; in FULL ist die rechte Spalte voll | überall 28/3 (überdeckt in FULL „ON“) |
| Testschalter `FlashbackCam.overrideBudget` für die vier OSD-Zustände | wie `PilotSession.setTestInput`; die Zustände sonst nur mit minutenlangen Aufnahmen | Zustände nur im OSD-Editor |
| Vorschau nimmt den letzten Flug erst ab 10 Sekunden, sonst den Beispielflug | sehr kurze Flüge (Testflüge, Fehlstarts) zeigen nichts Typisches | ab 2 Sekunden |
| Ring der Vorschau am größten Abstand der gezeichneten Linien je Ansicht (von oben waagerecht, von der Seite Höhe), gemessen an jedem der vier Zwischenschritte je Tick in beide Richtungen; tatsächlicher Pfad zwischen den Ticks als Flashbacks Spline durch jeden Tick (Spieltest 15.10.) | der Ring soll dort sitzen, wo man den Unterschied sieht; bei „Aus“ liegen beide Linien genau aufeinander | Ring zur gleichen Zeit (markiert unsichtbare Zeitversätze auf Geraden); räumlicher 3-D-Abstand für beide Ansichten (von oben wäre eine Höhenabweichung wieder unsichtbar); tatsächlicher Pfad als gerade Stücke (meldet an jeder engen Kurve Zentimeter, die auch „Aus“ hätte) |
| Ausdünnen unverändert | die sichtbare Abweichung bleibt schon jetzt unter Toleranz × 1,5 (Prüfung zur gleichen Zeit schließt den räumlichen Abstand ein); Ursache im Spieltest war der Ring | Zwischenschritte auch im Prüfdurchgang (mehr Rechenzeit bei jedem Regler-Schritt, in synthetischen Extremkurven höchstens 3 % über der Grenze) |
| Beispielflug: 29 Sekunden aus der echten Simulation (Freestyle-Drohne, Flip, Power-Loop, Rolle, Split-S, Kurven mit 10–37 m/s), einmal mit `SampleFlightTest` erzeugt (`PROPWASH_WRITE_SAMPLE_FLIGHT=<Datei>`) und als `assets/propwash/flashback/sample_flight.bin` (18 KB) eingecheckt | funktioniert vor dem ersten Flug, bleibt gleich, auch wenn sich die Physik ändert | zur Laufzeit simulieren; von Hand im Spiel fliegen |
| Reparatur in einem eigenen Bildschirm „Flashback-Aufnahmen prüfen“ (Knopf „Aufnahmen prüfen“ im Tab Action-Cam) statt im `RecordingsScreen` | der `RecordingsScreen` zeigt Propwashs MP4-Videos, Flashback-Aufnahmen sind etwas anderes | Knopf im `RecordingsScreen` |
| Reparatur liest die Editorzustände nur als JSON (Liste, Vorschau) und schreibt über Flashbacks eigene Klassen (`EditorStateManager`), nur Spuren in Propwash-Farbe; Sicherung `<uuid>.json.bak` (gibt es die schon, `.bak2` …) | Lesen ohne Flashbacks Cache zu füllen, Schreiben im Format, das Flashback selbst erzeugt; fremde Szenen bleiben unberührt | alles per JSON schreiben |

### Messung im Selbsttest

`video: Action-Cam (Flashback) – Flashback-Wiederholung folgt dem Kamerapfad` öffnet die Wiederholung in Flashback 0.43.6,
hängt zwei Budget-Szenen an (1.238 Kamera-Keyframes auf mehrere Spuren verteilt, dieselben Spuren mit je einem
Keyframe), schaltet zwischen ihnen um und liest nach jedem Bild die Eckpunkte der größten ImGui-Zeichenliste aus
Flashbacks eigenem ImGui-Kontext (außerhalb von Flashbacks Bild ist kein ImGui-Kontext aktiv, ein direkter Aufruf von
`ImGui.getDrawData()` stürzt ab). Gemessen am 14.10.2026, Fenster 1920 × 1080 auf dem Mac (Anzeigeskalierung 1):

| | UI-Skalierung 1 | UI-Skalierung 2 (Editor-Zoom 2) |
|---|---|---|
| Echte Propwash-Szene (71 Kamera- und 2 Sichtfeld-Keyframes) | 2.546 Eckpunkte | 3.686 Eckpunkte |
| 18 Spuren mit je einem Keyframe | 1.036 | 1.132 |
| 18 Spuren mit zusammen 1.238 Keyframes (volles Budget) | 40.080 | **59.696** von 65.536 – öffnet ohne Absturz |
| Eckpunkte je Kamera-Keyframe | 32,00 (gerechnet 32) | 48,00 (gerechnet 48) |
| Reserve einer Propwash-Szene (gemessen minus Keyframes) | 210 | 214 |

Die Formel stimmt exakt. Die Reserve bleibt trotzdem bei 6.000: Sie hängt von Fensterbreite (Raster und
Zeitbeschriftung), Markern, Spurzahl und Flashback-Version ab, und eine kleinere Reserve brächte nur 5–7 % mehr
Keyframes (bei 3.000: 1.301 statt 1.238). Die frühere Zahl der Kurzfassung (≈ 1.240) gilt also.

### Spline

`FlashbackSpline` ist bitgleich mit Flashbacks `CatmullRom` (Selbsttest mit 400 Zufallsfällen und an jeder
aufgezeichneten Szene gegen `KeyframeTrack#createKeyframeChange`: 0 Abweichung). 150 der Fälle liegen als
`common/src/test/resources/flashback/catmull_rom_golden.txt` für `FlashbackSplineTest` bei.

### Bildschirmfotos

`docs/screenshots/`: `osd_flashback_budget_normal.png`, `_caution`, `_thinning`, `_full` (und `_full_de`),
`action_cam_flashback_simplify_0.png`, `_50` (und `_50_de`), `_100`, `flashback_preview_large.png` (und `_de`),
`flashback_preview_large_worst.png` (und `_de`), `flashback_preview_large_side.png`, `flashback_rescue_list.png` (und `_de`),
`flashback_rescue_preview.png`. Flashbacks eigene ImGui-Oberfläche ist in Bildschirmfotos des Spiels nicht enthalten,
deshalb gibt es von der Zeitleiste nur die Messwerte.

### Antworten auf die offenen Fragen

- Reserve: gemessen gut 200 Eckpunkte, bleibt aus Vorsicht bei 6.000 (siehe oben).
- `globalScale`: nicht gespeichert, `ReplayUI.newGlobalScale` startet bei jedem Spielstart mit 1,0 (Bytecode).
- OSD-Element ohne laufende Aufnahme: zeigt nichts; der Stand des letzten Flugs steht in der Statuszeile des Tabs
  („Zuletzt ausgedünnt auf … Keyframes“).
- Standard des Reglers: 1 cm (das frühere „Ausgedünnt“) für neue Einstellungen; wer eine Einstellung hat, behält ihre
  Bedeutung (`EVERY_TICK` → „Aus“, `REDUCED` → 1 cm). Mit der Spline-Prüfung hält 1 cm auch das Tempo.
- Vorschau im Tab: ja, kompakt (Pfade, Lupe, Kennzahlen), groß über „Größer“.
- Beispielflug: aus der Simulation erzeugt (siehe Tabelle), keine Aufnahme von Hand nötig.

### Offene Punkte

- Ältere Flashback-Versionen als 0.43.6 sind nicht geprüft.
- Meldung an Flashback (Abschnitt 9) ist noch nicht geschrieben.
- Die Website (`propwashfpv.com/docs/guide`) erklärt die Flashback-Kamera von Hand; dort fehlt noch ein Satz zu langen
  Flügen und zum OSD-Element.
