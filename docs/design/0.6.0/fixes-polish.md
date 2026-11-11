# 0.6.0 – Fixes und Pflege

Stand 17.10., Basis Tag `v0.5.2` (Protokoll 16, API 1.6.0). Jeder Punkt ist gegen den Code von `v0.5.2` geprüft;
was dort schon behoben ist, steht in §2.3 und fällt weg. Begriffe: „Gate auf Beinen“ = unterstes Gate eines Stapels
(`GateStacking.Base.LEGS`), „Buchse“ = Stecker am Akkukabel, „Stecker“ = Gegenstück am Pigtail der Drohne.

| Punkt | Inhalt | Strang (§10) |
|---|---|---|
| A | Gestapelte Gates: Öffnung des untersten Gates zählt bis zum Boden | `gate-legs` |
| B | Akku gesteckt: Buchse und Stecker als ein Paar, Kabel geführt, alle Rahmen und Akkuplätze, auch im Web | `battery-plug` (+ J3, S3 im Website-Dokument) |
| C | Druckplatten und Stolperdraht reagieren auf Drohnen (Portierung von PR #1) | `block-triggers` |
| D | Signal: nicht geladene Chunks zählen nicht mehr als Luft (Gelände-Gedächtnis) | `signal-memory` |
| E | Web-Konfigurator: Hover im Lack-Modus auf unlackierbaren Flächen | S3 im Website-Dokument |
| F | Zonenname bei GUI-Breite 320 und Doku-Kleinigkeiten | `ui-doc-polish` |
| G | Bando-Lage: keine schwebenden Bauteile am Hang, kein Laub ohne Stamm (nur neu erzeugte Chunks) | `bando-placement` |

---

## 1. Ziel und Vorgaben

**Ziel:** 0.6.0 räumt die offenen Fehler und Kleinigkeiten aus dem 0.5.2-Playtest und den letzten Prüfrunden ab,
ohne bestehende Welten, Kurse, Bestenlisten, Ghosts oder Share-Codes zu brechen. Alles muss sich automatisch oder durch
unabhängige Prüfer vollständig prüfen lassen (§6).

Die Vorgaben stehen hier **sinngemäß** mit Quelle und Datum (Entscheidung E1: `docs/design` wird öffentlich exportiert,
Konventionen und Export-Prüfung erlauben keine wörtlichen Zitate aus privaten Notizen und Nachrichten):

| Punkt | Vorgabe (Quelle) |
|---|---|
| A | Fix erst in 0.6.0, nicht in 0.5.2. Beim untersten Gate eines Stapels fehlt der untere Balken; der Bereich, in dem er saß, zählt beim Durchflug nicht, die Erkennung behandelt ihn weiter als Rahmen. Die Öffnung des untersten Gates soll bis zum Boden bzw. zur Sockeloberkante reichen, wenn der Balken fehlt (Roadmap-Notiz 17.10., Playtest-Video vom 15.10.). Die Fahnen-Prüfung nach unten ist gewollt und bleibt. |
| B | Der Akku wirkt nicht angesteckt: Akku-XT60 und Pigtail-XT60 sind zwei getrennte gelbe Stecker statt eines gesteckten Paars. Stecker gesteckt zeigen, Kabel sinnvoll führen, für alle Akkuplätze (oben, unter dem Rahmen), alle Rahmen und im Web-Konfigurator (Roadmap-Notiz 17.10., Playtest-Bild vom 15.10.). |
| C | Externer PR #1 von bluepanee (Druckplatten reagieren auf Drohnen, 6 Zeilen, CLA bestätigt) übernehmen – nur per Portierung ins private Repo, weil der Export die Historie anhängt – und den Autor nennen (Roadmap 15.10., Plan nach 0.5.2). Den PR selbst nicht kommentieren und nicht mergen. |
| D | Nicht geladene Chunks zählen beim Signal als Luft, seit 0.5.0; für die nächste Version (Roadmap 15.10., Plan nach 0.5.2, Abschnitt Pflege). |
| E | Fehlender Hover im Paint-Modus auf unlackierbaren Flächen (Roadmap 15.10., Plan nach 0.5.2). |
| F | Bei 320 Punkten Breite werden Zonenname und Detailzeile stark gekürzt („Selbsttest Suche“ wird zu „Selb…“), für 0.6.0 notiert (Ergebnis der 0.5.2-Fix-Runde). `docs/RELEASE-NOTES.md` 0.5.2 beginnt fälschlich mit „Großes Update auf 0.5.1.“ (Roadmap 15.10.). „Rennen neu starten“ ist mit 0.5.2 entfallen (ersetzt durch „Reset“, Roadmap 15.10.). |
| G | Bando-Lage auf 2 von 8 Seeds (Scheune am Hang schwebt bzw. Kirschblatt ohne Stamm), seit 0.5.0; für die nächste Version (Roadmap 15.10.). |
| alle | 0.6.0 wird vor dem Release gründlich geprüft: viele unabhängige Prüfer, Mehrspieler auf dediziertem Server, Bildschirmfotos, lieber einmal zu viel (Plan nach 0.5.2). Website bei jedem Produkt mitziehen. |

## 2. Umfang

### 2.1 Drin
- **A** Zählung der Öffnung des Gates auf Beinen ab Boden, auf Server und Client gleich; alle Gate-Arten geprüft (§3.A).
- **B** Gestecktes Paar aus Akku-Buchse und Stack-Stecker mit Kabelführung im Drohnen-Renderer (Welt, Item, Werkbank,
  Ghosts, Flugschul-Szenen), Stack-Variante `_mounted`, Vertrag für JMP und Website.
- **C** Druckplatten (Holz, Wägeplatten) und Stolperdraht reagieren auf Drohnen; Nennung von bluepanee.
- **D** Client-seitiges Gelände-Gedächtnis für Video- und Steuer-Signal (auch Repeater-Strecken).
- **E** Hover im Lack-Modus des Web-Konfigurators auch auf Flächen ohne Lackplatz.
- **F** Zonenzeile im Tab „Welt-Regeln“ bei schmaler GUI; Satz in den Release-Notizen (über die Integration);
  Bildschirmfoto mit verdecktem „Schließen“; verbliebene Erwähnungen von „Rennen neu starten“; FOUNDATION-Folgeänderungen
  aller Punkte.
- **G** Bando-Lage auf 2 von 8 geprüften Seeds (Scheune am Hang schwebt, Kirschblatt ohne Stamm), seit 0.5.0.

### 2.2 Bewusst nicht drin
- Dimensionswechsel der Drohne durch Portale (laut Discord-Auswahl nach 0.6.0); C ruft deshalb **nicht** die
  vollständige Vanilla-Blockwirkung auf.
- Kaktus-Schaden, Feuer-Immunität, Vanilla-Spinnweben-Bremse, Blasensäulen für Drohnen (Nebenwirkungen des PR-Wegs, §3.C).
- Server-seitiges Geländeprofil oder Schätzung aus dem Weltgenerator für nie gesehene Chunks (§9, E14).
- Speichern des Gelände-Gedächtnisses auf der Festplatte.
- Eigene Park-Positionen je Rahmen für das Steckerpaar (nur als optionaler Schalter, nicht für eingebaute Rahmen).
- Änderung der Akku-Modelle (Items behalten ihren Stecker).
- Pro-Fläche-Auswahl im Lackier-Bild des Spiels (das Spiel wählt nach Teil; E bleibt beim Web-Konfigurator).
- Der Landungs-Ausreißer im Selbsttest (1 von 9 Läufen) gehört nicht hierher: eigener Strang `landing-stability`
  (Übersicht `README.md` §6.1). Die Bando-Lage ist Punkt G.

### 2.3 Gegen `v0.5.2` geprüft: schon erledigt (fällt weg)
| Punkt | Befund in `v0.5.2` |
|---|---|
| Flashback-Graph-Ring zeigt auf einen anderen Zeitpunkt als der Pfad-Ring | behoben in `f4457acf` (Ring im Graphen „Abweichung über die Zeit“ sitzt zum selben Zeitpunkt wie die größte sichtbare Lücke, je Ansicht; Haarnadel-Selbsttest prüft beide Ansichten). Prüflauf der Fix-Runde: Graph-Ring und Pfad-Ring beide bei Tick 108. |
| CrashOutcomeTest-Schwellen hängen am Seed | behoben in `0961b941` (einseitige exakte Binomialtests mit höchstens 0,1 % Fehlalarm je Schwelle, mehr Läufe, Startwert über `PROPWASH_CRASH_SEED`, 40 Startwerte grün). |
| FOUNDATION.md nennt den Knopf im Empfänger-Assistenten anders als das Spiel | behoben in `a844747c` („Sticks wie Mode n“ / „Use Mode n sticks“). |
| Flashback-Doku-Bild veraltet | in der letzten 0.5.2-Runde neu aufgenommen (`flashback_preview_corner_gap.png`). |

### 2.4 Übergeben an andere Dokumente
- **Drei Tests, die nach einem Klon des öffentlichen Repos fehlschlagen** (`loginShellOutputIsReadFromTheLastPathLine`,
  `groundRestFliesBitIdentical`, `builtinPartsAnalyseAndFlyBitIdentical`): Strang `test-portability` in
  `addon-api-namespace.md` (§3.18, E29, E30). Hinweis dorthin aus dieser Prüfung:
  - Der Login-Shell-Test scheitert unter Windows, weil `Path.of("/opt/tools/bin/ffmpeg").isAbsolute()` dort `false`
    ist (`LoginShell.parse` verlangt einen absoluten Pfad).
  - Die beiden Bit-Tests: In `sim/` und `build/` stehen 28 Aufrufe von `Math.sin/cos/tan/exp/log/pow/atan…` in 12
    Dateien (u. a. `BreakSolver`, `GaussMarkov`, `Barometer`, `GpsReceiver`, `NavHold`, `WearEffects`, `CrashDamage`),
    obwohl `docs/design/flight-model-aero-feel.md` für Determinismus `StrictMath` verlangt. Das ist die
    wahrscheinlichste Ursache für Abweichungen zwischen arm64 und x86_64. `Math.hypot` ist unkritisch (ruft
    `StrictMath.hypot`). Die Entscheidung dort (zweite Golden-Spalte statt `StrictMath`) bleibt maßgeblich.
- **Website-Texte** (Spielerhandbuch, Changelog-Seite): Strang S5 in `website-jmp.md`; die Bausteine stehen in §7.
- **JMP-Stacks und Web-Viewer für B, Hover-Fix E**: Stränge J3 und S3 in `website-jmp.md`; dieser Abschnitt legt
  den Vertrag fest (das Website-Dokument sagt selbst, dass der Vertrag des Kern-Strangs gilt).

---

## 3. Architektur und Datenformate

### 3.A Gestapelte Gates: Öffnung des Gates auf Beinen bis zum Boden

**Ursache (bestätigt):** `race/GateGeometry#insideOpening(side, up)` prüft `up > frame` für jede Rolle. Seit dem
Stapeln (`race/GateStacking.Base`) hat das Gate auf Beinen (`LEGS`: `covered && !supported`) keinen unteren Balken mehr
– weder als Modell (`gate/*_legs`) noch als Kollision (`GateGeometry#boxes/parts`, `base.bottomBar()` ist `false`).
FOUNDATION.md beschreibt das bisher sogar ausdrücklich („Bei einem Gate auf Beinen zählt die Öffnung weiterhin erst ab
Rahmenstärke über dem Boden“). Der Verdacht `GateItem` (`foot = geometry.frame()` bei `STACKED`) betrifft nur
Platzierung und Freiraum (`GateItem#bounds`, `#freeOriented`) und ist dort richtig: Der Fuß-Bereich eines gestapelten
Gates ist der Balken des Gates darunter. `GateItem` bleibt unverändert. Die Füße (`foot_left/right`) liegen innerhalb
der Pfosten (Modell `medium_frame_legs`: x −7,5…−4,5 im Pfosten −8…−4) und ragen nicht in die Öffnung; „Sockeloberkante“
fällt damit mit dem Boden zusammen.

**Alle Gate-Arten:**

| Art / Rolle | Unterkante der zählenden Öffnung bisher → neu |
|---|---|
| Stehendes Gate, einzeln (`BAR`) | Rahmen → Rahmen (Balken vorhanden) |
| Stehendes Gate, unterstes im Stapel (`LEGS`) | Rahmen → **0 (Boden)** |
| Stehendes Gate, gestapelt (`STACKED`) | Rahmen → Rahmen (dort liegt der Balken des Gates darunter) |
| Frei skaliertes stehendes Gate | wie oben je Rolle |
| Wand-, Decken-, Liege-, Hänge-Gates (`MOUNTED`, nicht stapelbar) | unverändert |
| Gekippte Gates (`MOUNTED`) | unverändert |
| Freiform-Gate (Zellen, kein Rahmen) | unverändert |
| Gate-Stange (`GATE_POLE`, nicht kursfähig, kein Durchflug) | unverändert |
| Kurs-Elemente Stange, Flagge, Würfel (eigene Passagen) | unverändert |
| Weltkurs-Leiter der Drohnen-Arena (drei Gates) | unterstes Gate: Fix greift |
| Flugschul-Ringe und Szenen | unverändert (keine Stapel-Zählung) |

**Umsetzung ohne Daten- und Protokolländerung** (Entscheidung E5):
- `race/GateGeometry`: neues Feld `legs` (Standard `false`), `withLegs(boolean)`, `legs()`, `openingFloor()` =
  `legs ? 0.0 : frame`. `insideOpening(side, up)` nutzt `openingFloor()`. Überladung
  `crossing(x0, y0, z0, x1, y1, z1, forward, boolean legs)` ohne neue Objekte (für die Schleife im Tracker).
  `equals`/`hashCode` nehmen `legs` auf. Kursdaten (`CourseData.Gate#geometry`) setzen `legs` nie; damit bleiben
  `LapTracker#sameLayout`, `CourseData#layoutHash`, Bestenlisten und Ghosts unberührt.
- `race/GateEntity#geometry()`: Gate-Art `GATE` mit `base() == LEGS` → `withLegs(true)`. Damit zählen auf dem Server
  automatisch Rundenprüfung (`RaceServer`, Treffer aus der Entity-Geometrie) und Redstone-Signal (`GateSignals`).
  `geometryAt` (Kollision) bleibt ohne Flag. `RaceServer` und `CourseRegistry` werden nicht angefasst.
- `race/GateOpenings` (neu, reines Java): `boolean legs(int gateIndex)`, Konstante `NONE` (immer `false`).
- `race/LapTracker`: `setOpenings(GateOpenings)`; in der Durchflug-Schleife erst wie bisher prüfen; nur wenn das Segment
  die Gate-Ebene im Streifen `0 < up ≤ frame` innerhalb der Pfosten schneidet, den Anbieter fragen und mit
  `legs = true` erneut prüfen (der Anbieter wird also nur bei Durchflügen ganz unten gefragt). Rückwärts-Prüfung
  (`BACKWARD`) genauso.
- `race/client/ClientGateOpenings` (neu): sucht zum Kurs-Gate `i` das Client-Entity (`GateEntity` im Quader ±0,25
  Block um den Gate-Ursprung, gleiche `courseId()`/`gateIndex()`) und meldet `base() == LEGS`. Nicht geladen →
  `false` (bisheriges Verhalten). Beim Durchflug ist die Umgebung der Drohne geladen, sonst gäbe es kein FPV-Bild.
  `covered`/`supported` rechnet der Client heute schon alle 5 Ticks aus den synchronisierten Positionen.
- `race/client/ClientRaceTracker`: setzt den Anbieter beim Kurswechsel (eine Zeile).
- `race/GhostTrack#splits(gates)`: Überladung mit `GateOpenings`, damit Zwischenzeiten von Ghosts, die unten durch
  das Gate auf Beinen flogen, nicht fehlen; die drei Aufrufer in `race/client/GhostLibrary` geben
  `ClientGateOpenings` mit.

### 3.B Akku gesteckt

> **Variante 2 gilt** (Befund PLUG-1, 23.10.): Sitzt der Akku in der Drohne, zeichnet Propwash keine Stecker und keine
> Kabel. Ausgeblendet werden am Akku `connector`, `connector_grip`, `bt_connector`, `balance_plug` und jedes `wire`, das
> eines davon berührt, am Stack `_mounted` die beiden `pigtail` (`PlugMask` und `MaskedItemCollector`, gemessen im
> Modellraum vor der `transformation`); das Web blendet dieselben Elemente aus. Das gesteckte Paar mit Kabelführung
> (Vertrag Punkt 3 und 4, Rahmen-Heck in 3.B.1, 3.B.2, `PowerLeadRoute` und `power_lead` in 3.B.3 und 3.B.4) ist
> verworfen: Ein Prototyp mit denselben Modellen fand am 7″ Long Range und an der X-Class mit 6S 2200 keinen Weg am
> GPS-Mast vorbei, an Race 5″, 5″ Freestyle und zwei JMP-Rahmen lief das Kabel als Schleife oder im Zickzack um Platte
> und Heck. Es bleiben Vertrag Punkt 1 und 2, der Rückfall aus Punkt 5 (ohne `_mounted`, `_mounted` aus einem anderen
> Paket, ohne Buchse, ohne Akku) und die Element-Namen für Buchse und `pigtail`; ein vorhandener `power_lead`-Eintrag
> wird ignoriert. Maßgeblich sind [`battery-plug.md`](../../battery-plug.md) und [`ASSETS.md`](../../ASSETS.md) „Akku
> gesteckt“. Zeile B in §5 trägt den Text nach Variante 2; §2.1 B, §6.3 (Route, Paar, Steckerfarbe) und die Zeile zu
> `power_lead.bend_mm` in §8 sind damit überholt.

**Befund:** Jedes Akku-Modell bringt Kabel und Buchse am hinteren Ende mit (Elemente `connector` + `connector_grip`
für XT-Bauformen, `bt_connector` für BT2.0), jedes Stack-Modell sein Pigtail mit eigenem Stecker (`wire` ×2 +
`connector` bzw. `bt_connector`). `DroneAssembly` setzt beide Modelle unabhängig → zwei lose Stecker (Playtest-Bild).
Betroffen: 5 eingebaute Rahmen (Akku oben: `cinewhoop_3`, `freestyle_5`, `long_range_7`; unten: `race_5`, `whoop_65`),
5 Akkus (XT60 ×3, XT30, BT2.0), 4 Stacks (`stack_30` XT60, `aio_20` XT30-Größe, `aio_whoop` und `creative_fc` BT2.0),
dazu 30 JMP-Akkus und 12 JMP-Stacks.

**Vertrag (Kern, JMP und Web gleich; ersetzt die Annahme A.2 in `website-jmp.md`):**
1. **Akku-Modelle bleiben unverändert.** Die Buchse des Akkus ist die eine Hälfte des Paars. Sie besteht aus den
   Elementen namens `connector` (+ `connector_grip`) oder `bt_connector`; ihre Steckseite ist die Fläche mit dem
   größten z (dort sitzt heute die Kontakt-Textur). Gilt für die Grundvariante und `_under`.
2. **Stacks bekommen die Variante `_mounted`** (und `_mounted_paint`, wo es `_paint` gibt): wie das Grundmodell, aber
   ohne `connector`, `connector_grip`, `bt_connector`; die beiden Stromkabel heißen `pigtail`. Item-Definitionen
   `items/drone_render/stack/<id>_mounted.json` (+ `_paint`). Vorbild ist `_under` der Akkus. Das Stack-Item in Hand,
   Regal und am Boden bleibt wie heute.
3. **Den Stecker und das Kabel zeichnet der Renderer** (Kern und Web nach derselben Regel, 3.B.2) aus: Buchse des
   Akkus (Quader), Pigtail-Enden des Stacks (Lage, Durchmesser), Rahmen-Heck, optional `power_lead` im
   Rahmen-Eintrag von `drone_layout.json`.
4. **Steckerart und Farbe:** `connector` aus der Akku-Definition (Teile-JSON, bei JMP gesetzt, z. B. `PH2.0`),
   normalisiert ohne Leer- und Bindestriche; fehlt er (eingebaute Akkus), aus dem Element-Namen (`bt_connector` →
   BT2.0, sonst XT). Gelb `#e8b923` für XT30/XT60/XT90/A30/AS150, weiß `#dedbd2` für BT2.0/BT3.0/PH2.0; Kabel rot
   `#c62828` und schwarz `#1c1c1c` (Werte aus `tools/assets/parts/paint.py`). Größe immer aus der Buchse, nie aus
   einer Tabelle.
5. **Rückfall auf das Bild von heute** (Grundmodell des Stacks, kein Kabel), wenn: `_mounted` fehlt oder aus einem
   anderen Ressourcenpaket kommt als das Grundmodell des Stacks (ein Paket, das nur das Grundmodell ersetzt, sieht
   damit aus wie gewollt); die Buchse oder genau zwei `pigtail` nicht gefunden werden; die Drohne keinen Akku zeigt
   (`batteryMissing`); Ersatzformen gezeichnet werden.

#### 3.B.1 Umrechnung und Anker
Ein Modell wird mit seinem Punkt (8, 8, 8) auf die Teilposition gesetzt, 1 Modell-Einheit = 8 mm
(`DroneVisualScale.MILLIMETRES_PER_MODEL_UNIT`, im Web `MM_PER_MODEL_UNIT`). Drohnen-mm = Teilposition +
(Modellkoordinate − 8) × 8 je Achse. Probe: Buchse `b6s_1300` (Modell 7…9 × 11,25…12,25 × 14,625…16,56) → 16 mm breit,
z 53…68,5 mm (Pack 78 mm, Kabel 14 mm) – passt zum Generator.

- Buchse: Vereinigung der Quader `connector` + `connector_grip` bzw. `bt_connector`; Steckseite `zB = maxZ`, Länge
  `LB = maxZ − minZ` des Körpers (ohne Griff).
- Pigtail-Enden: je `pigtail`-Element Mittelpunkt (x, y) und `maxZ`; Durchmesser = kleinere Kante des Querschnitts.
- Rahmen-Heck `zR`: größtes z aller Rahmen-Elemente, deren x-Bereich den Streifen |x| ≤ 10 mm berührt (Arme,
  Motoren und Props zählen nicht).

#### 3.B.2 Kabelführung (alle Werte in Drohnen-mm)
- **Stecker:** gleicher Querschnitt wie die Buchse, z von `zB` bis `zS = zB + 0,8·LB`; Fuge 0,5 mm in der
  Steckerfarbe × 0,7 am Anfang; bei XT eine Griffleiste oben wie `connector_grip`.
- **Austritt:** hinten am Stecker (`zS`), zwei Adern in Steckermitte, Abstand wie am Pigtail; rot auf der Seite des
  roten Pigtail-Kabels.
- **Biegestelle:** `zK = max(zP, zR, zS) + 3`, wobei `zP` = größtes Pigtail-Ende. `power_lead.bend_mm` (optional,
  `[x, y, z]`) ersetzt die Biegestelle.
- **Je Ader drei achsparallele Stücke:** (1) vom Pigtail-Ende nach hinten bis `zK` in Pigtail-Höhe; (2) an `zK`
  senkrecht auf Steckerhöhe, dabei seitlich auf das x der Ader am Stecker; (3) von `zK` nach vorn bis `zS`.
  Stücke unter 0,1 mm entfallen. Durchmesser = Pigtail-Durchmesser.
- Ein `Shape` je Bau, gecacht wie die Montage (`DroneRenderResources#assembly`); nicht lackierbar, nicht wählbar im
  Lackier-Bild.

#### 3.B.3 Klassen und Dateien (Kern)
- `drone/client/render/PlugAnchors` (neu, reines Java + Gson): liest Modell-JSON nach Element-Namen → `Socket`
  (Quader, Steckerart aus dem Namen), `Pigtail` (zwei Enden, Durchmesser), `bodyRearZ` (Rahmen).
- `drone/client/render/PowerLeadRoute` (neu, reines Java/JOML): Eingaben aus 3.B.1 → Liste farbiger Quader. Ohne
  Minecraft testbar; dieselben Konstanten nutzt das Web.
- `drone/client/render/PowerLead` (neu): baut das `Shape` mit `ShapeBuilder#boxBetween`.
- `drone/client/render/DroneRenderResources`: liest beim Neuladen die Anker für Akku-Modelle (inkl. `_under`),
  Stack-`_mounted` und Rahmen; merkt je Modell das Quellpaket (`Resource#sourcePackId`).
- `drone/client/render/PartModel`: `MOUNTED_SUFFIX = "_mounted"`, `stackMounted(StackType)`.
- `drone/client/render/DroneAssembly`: Stack mit `_mounted`, wenn der Vertrag erfüllt ist; neues `powerLead()`.
- `drone/client/render/DroneVisuals`, `DroneRenderState` (ein Feld `powerLead`), `guide/client/render/SceneDroneVisual`
  (Flugschul-Szenen) zeichnen das Shape; `PaintPicker` ignoriert es.
- `drone/client/render/DroneLayoutParser` + `DroneLayout`: optionales `"power_lead": {"bend_mm": [x, y, z]}` je Rahmen.
- Generator: `tools/assets/parts/stacks.py` (Bau ohne Stecker, Kabel heißen `pigtail`), `generate.py` (Varianten und
  Item-Definitionen), `validate.py` (jeder Stack mit Stecker hat `_mounted`; `_mounted` ohne Stecker-Element und mit
  genau zwei `pigtail`); erzeugte Dateien `models/part/stack/*_mounted*.json`, `items/drone_render/stack/*_mounted*.json`.

#### 3.B.4 JMP und Web (Umsetzung in J3 und S3 des Website-Dokuments)
- **JMP (J3):** Das Werkzeug erzeugt `_mounted` (+ `_paint`) für die 20 Stack-Modelle mit Stecker und benennt die
  Stromkabel `pigtail`. Die 78 Akku-Modelle bleiben, wie sie sind. Die JMP-Generatoren benennen Stecker heute schon
  `connector`/`bt_connector` (`jmpstyle/battery.py`, `jmpstyle/stack.py`). Park-Positionen nur, wenn ein
  Bildschirmfoto es verlangt (`power_lead.bend_mm` im JMP-Rahmen-Eintrag).
- **Web (S3):** `tools/build-configurator-models.mjs` übernimmt `mounted`/`mounted_paint` (wie `under`) und schreibt
  zu jedem kompakten Modell die Anker `socket` (Quader, Art), `pigtail` (zwei Enden, Durchmesser) und `bodyRearZ`;
  `assets/js/configurator/assembly.js` nutzt für den Stack die Variante `mounted` und fügt ein Stück
  `{ role: 'power_lead', procedural: 'power_lead' }` nach `PowerLeadRoute` hinzu (gleiche Konstanten);
  `viewer.js` zeichnet es (Kategorie `stack`, kein Lackplatz). Der Web-Export enthält die Varianten automatisch,
  weil er alle Asset-Dateien übernimmt; die Element-Namen bleiben in den Roh-Modellen erhalten.

### 3.C Druckplatten und Stolperdraht (Portierung von PR #1)

**PR #1 (gelesen, unverändert offen):** `QuadcopterEntity` bekommt `fireImmune() → true` und ruft am Ende von
`tickServer` `applyEffectsFromBlocks()`. Grund im Code: Drohnen bewegen sich auf dem Server nicht über `Entity#move`
(Positionen kommen vom Client oder aus der eigenen Physik), also ruft niemand die Block-Wirkungen auf; eine
Druckplatte beginnt ihre Prüfung aber nur über `entityInside`. Danach zählt die Platte selbst weiter
(geplante Ticks), solange etwas auf ihr liegt.

**Warum nicht 1:1:** Die Vanilla-Blockwirkung bringt mehr als Druckplatten: Nether- und End-Portale wechseln die
Dimension (für Drohnen erst nach 0.6.0 vorgesehen), Kaktus ruft `hurtServer` auf (Drohnen nehmen Schaden bis zum
Zerbrechen), Spinnweben bremsen zusätzlich zur eigenen Spinnweben-Physik aus 0.5.2, Blasensäulen stoßen, Feuer und
Lava zünden (deshalb im PR `fireImmune`, das aber Drohnen auch gegen alle Feuer-Schadensarten immun macht, etwa
Feuerbälle).

**Portierung (gleiche Wirkung für Druckplatten, keine Nebenwirkungen):**
- Block-Tag `data/propwash/tags/block/drone_triggers.json`: `#minecraft:pressure_plates`, `minecraft:tripwire`.
  Datenpakete können ihn erweitern oder mit `replace` leeren.
- `interact/DroneBlockTriggers` (neu): `tick(QuadcopterEntity drone, ServerLevel level)`. Nur wenn sich die Drohne seit
  der letzten Prüfung bewegt hat (> 1·10⁻⁴ Block) oder im ersten Tick; dann für jede Blockposition im
  Kollisionsquader der Drohne (um 1·10⁻³ vergrößert), deren Zustand im Tag liegt:
  `state.entityInside(level, pos, drone, InsideBlockEffectApplier.NOOP, true)` (genaue Signatur beim Bau gegen 26.3
  prüfen). Letzte Position in einer `WeakHashMap` der Klasse, nur Server-Thread.
- `drone/QuadcopterEntity#tickServer`: ein Aufruf `DroneBlockTriggers.tick(this, level)` nach `syncFlightTags`.
  Kein `fireImmune`.
- Vanilla-Regeln gelten: Holz- und Wägeplatten zählen jedes Entity (wie fallengelassene Items), Stein-Druckplatten nur
  Lebewesen; ein Stolperdraht löst aus. Wie bei anderen schnellen Entities muss die Drohne in einem Tick im
  Auslösebereich der Platte sein (1/4 Block hoch); Landen und tiefes Schweben lösen sicher aus.
- Parkende Drohnen kosten nichts (Prüfung nur bei Bewegung) – wichtig wegen der Discord-Meldung zu vielen abgestellten
  Drohnen.
- Nennung: `CHANGELOG.md`, `docs/RELEASE-NOTES.md` und die Commit-Nachricht nennen „bluepanee (Pull Request #1)“.
  Den PR schließt der Projektinhaber selbst mit einem Dank; sonst kommentiert oder merged ihn niemand.
- **Stolperdraht:** Der PR ruft die Vanilla-Blockwirkung auf, und die löst auch Stolperdraht aus. Die Portierung behält
  genau diese Wirkung (ein Eintrag im Tag, per Datenpaket entfernbar) und lässt alles andere weg (E20).

### 3.D Signal: Gelände-Gedächtnis statt Luft

**Befund:** `video/client/ClientBlockLoss#medium` gibt `Medium.AIR` zurück, wenn `getChunkNow` keinen Chunk liefert.
Das nutzen Video (`VideoState`), Steuer-Signal (`ControlLinkScan`, Reichweite bis 1 200 Blöcke) und Repeater
(`RelayVideo`). Folgt die Sicht der Drohne oder ist die Drohne weiter als die Sichtweite entfernt, fehlen dem Client
die Chunks dazwischen; Hügel und Häuser zählen als Luft.

**Umsetzung (rein clientseitig):**
- `video/link/TerrainColumns` (neu, reines Java): 16 × 16 Säulen je Chunk mit `short top`, `byte canopy` (Laub über
  `top`, 0–255 Blöcke), `byte material` (Ordinalzahl von `RfMaterial`). `medium(lx, y, lz)`: `y ≤ top` →
  `Medium.full(material)`, `top < y ≤ top + canopy` → `FOLIAGE`, sonst `AIR`. Etwa 1 KB je Chunk.
- `video/link/TerrainCache` (neu, reines Java): LRU (Zugriffsreihenfolge) über `ChunkPos#asLong`, höchstens 4 096 Chunks
  (≈ 4 MB), `clear()`.
- `video/client/TerrainMemory` (neu): `remember(LevelChunk)`: je Säule ab der Heightmap `MOTION_BLOCKING` (geht an den
  Client) abwärts über Laub, Pflanzen, Schneeschichten und Teppiche (Einordnung mit `ClientBlockLoss`), höchstens 32
  Blöcke; `top` = erster Block, dessen Medium nicht Luft, Laub oder Pflanze ist; `material` = dessen Material, bei
  Schnee oder Eis oben das erste andere Material bis 4 Blöcke tiefer; `canopy` = höchstes Laub über `top`. Löschen bei
  Welt- oder Dimensionswechsel.
- Haken: Mixin `mixin/video/client/ClientChunkCacheMixin` am Anfang der Methode, mit der der Client einen Chunk
  verwirft (`ClientChunkCache#drop(ChunkPos)`, Name beim Bau gegen 26.3 prüfen); Eintrag in
  `propwash.video.mixins.json` (Client).
- `ClientBlockLoss#medium` (und `Multipath.Reflectivity`): kein Chunk → `TerrainMemory#columns(cx, cz)`; vorhanden →
  Medium der Säule, sonst wie bisher `AIR`. Geladene Chunks haben immer Vorrang.
- Nie gesehenes Gelände bleibt Luft (dokumentierte Grenze). In der Praxis ist der Flugkorridor bekannt: Folgt die
  Sicht der Drohne, war jeder Chunk um ihre Bahn einmal geladen und wird beim Verwerfen gemerkt.
- Kosten: beim Verwerfen etwa 256 Säulen × wenige Blöcke (unter 0,1 ms); beim Messen eine Map-Abfrage je
  Chunk-Wechsel (wie heute der Chunk-Cache in `ClientBlockLoss`).

### 3.E Web-Konfigurator: Hover im Lack-Modus (Umsetzung in S3)

**Befund (`assets/js/configurator/viewer.js`):** `describe()` setzt `paintSlot = info.groups[group] || info.main`.
Auf einer Fläche ohne Tönungsgruppe (`group = −1`) eines lackierbaren Teils gilt der Hauptplatz des Teils. Das passt
zum Spiel (dort wählt ein Klick nach Teil). `applyHighlight()` hellt im Lack-Modus aber nur Flächen mit Gruppe auf –
genau die Fläche unter dem Zeiger bleibt dunkel. Beschriftung und Klick stimmen, nur der Hover fehlt.

**Fix:** `describe()` liefert zusätzlich `mesh` und `bare = group < 0 && info.faces`. `applyHighlight()`: Bei einem
Treffer im Lack-Modus mit `bare` bekommt das Mesh unter dem Zeiger zusätzlich `highlightAll = level × 0,6` (ganzes
Teil leicht, Flächen des Platzes voll). Beschriftung, Klick und Teile ganz ohne Platz („nicht lackierbar“) bleiben.
Nur `viewer.js`, keine neuen Texte.

### 3.F Zonenzeile bei schmaler GUI und Doku-Kleinigkeiten

**Befund:** `zone/client/ZoneSection#extract` gibt dem Namen `textRight − x − 12 − kindWidth − GAP` (mit
`BUTTON_WIDTH` 74 und der Art rechts, z. B. „zählt als Flughilfe“). Bei 320 Punkten Breite bleiben wenige Pixel.

**Fix:** Reicht der Platz nicht (`font.width(name) > nameRoom` und `nameRoom < NAME_MIN_WIDTH = 96`), wird die Zeile
kompakt: Zeile 1 nur der Name über die volle Breite bis zum Knopf, Zeile 2 die Art in Zonenfarbe, dann „ · “ und die
Detailzeile (gekürzt wie bisher). Höhe `ENTRY` bleibt 26. Breite Fenster sehen aus wie bisher. Dieselbe Prüfung für
den Titel in `ZoneEditPanel`, nur falls der Selbsttest dort eine Kürzung findet.

**Doku-Kleinigkeiten:**
- `docs/RELEASE-NOTES.md`, Abschnitt 0.5.2, erster Satz: „Großes Update auf 0.5.1.“ → „Großes Update nach 0.5.1.“
  (Website und Plattform-Texte enthalten den Satz nicht; geprüft.) Die Datei schreibt nur die Integration: Der Strang
  liefert den Satz in seinem Bericht, die Integration trägt ihn ein (E22).
- „Rennen neu starten“ gibt es seit 0.5.2 nicht mehr; „Reset“ bringt die Drohne zum Homepoint und bricht die Runde ab
  (Welt-Regel „Reset erlauben“). Der Strang sucht verbliebene Erwähnungen in `docs/`, Flugschul-Texten und
  Sprachdateien und ersetzt sie durch „Reset“; für die Website liefert er die Textstellen an `site-docs`.
- Bildschirmfoto `configurator_world_rules_readonly_de.png` (und `_en`): Der Wind-Tooltip verdeckt den gelben Knopf
  „Schließen“. Der Bildschirmfoto-Schritt hovert künftig die Zeile „Schwerkraft“; beide Bilder neu.
- FOUNDATION.md: Abschnitt „Gates stapeln“ (Satz zur Öffnung, Zeile `GateGeometry#boxes`), Drohnen-Renderer
  (`DroneAssembly`, Varianten), Signal und `interact` (Tag `drone_triggers`); `docs/ASSETS.md` (Variante `_mounted`);
  `docs/design/signal-repeater.md` §8 (Grenze geladener Chunks).

### 3.G Bando-Lage

**Befund (0.5.0 bis 0.5.2):** Auf 2 von 8 geprüften Seeds sitzt ein Bando falsch: Eine Scheune am Hang schwebt teilweise
über dem Gelände, und an einem Kirschbaum bleibt Laub ohne Stamm stehen. Beteiligt sind die Platzwahl
(`bando/plan/BandoSite`, Hang-Grenze der Familie, gemessen mit `WORLD_SURFACE_WG`), das Unterfüttern und die Fundamente
in `bando/BandoPiece` (`underpin`, `foundation`) und die Laub-Prüfung (`bando/plan/LeafDistance`).

**Vorgehen:** Der Strang reproduziert beide Fälle mit den Seeds aus der Bando-Prüfung von 0.5.2, findet die Ursache und
behebt sie an der Stelle, die sie verursacht; denkbar sind (1) Hang über den ganzen Grundriss messen (Ecken und Mitte,
nicht nur die Mitte), (2) Fundament je Randspalte bis zum ersten tragenden Geländeblock führen (mit Obergrenze; reicht
sie nicht, wird der Platz verworfen), (3) Laub im Freiräume-Bereich samt Rand ohne Verbindung zu einem Stamm entfernen.

**Abnahme:** Auf acht festen Seeds und allen Bando-Familien kein Bauteil ohne tragenden Kontakt und kein Laub mit
Abstand 7 zum nächsten Stamm im Bereich des Bandos plus 4 Blöcke Rand; Bando-Bauplan je Seed sonst unverändert
(JUnit über `BandoGenerator`, Selbsttest `bando:` mit Bildschirmfotos). Wirkt nur in neu erzeugten Chunks.

---

## 4. Kompatibilität

| Punkt | Alte Welten / Daten | Kurse, Bestenlisten, Ghosts, Codes | Server/Client | Netzwerk | API |
|---|---|---|---|---|---|
| A | keine neuen Daten | Kursdaten und `layoutHash` unverändert → alle Rekorde und Ghosts bleiben; die Öffnung wird nur größer, keine bisher gültige Runde wird ungültig | Server zählt aus der Entity, Client aus dem geladenen Entity; beide 0.6.0 | keine Änderung | keine |
| B | keine | keine | rein clientseitig; Ressourcenpakete, die nur das Grundmodell eines Stacks ersetzen, behalten ihr Bild (Quellpaket-Regel) | keine | keine; Add-on-Doku nennt die optionale Variante `_mounted` und die Element-Namen (Ressourcen-Konvention) |
| C | keine | keine | nur Server; jeder 0.6.0-Client sieht die Blockzustände wie immer | keine | keine; Datenpakete steuern den Tag |
| D | nichts gespeichert | keine | nur Client | keine | keine |
| E | – | – | nur Website | – | – |
| F | keine | keine | nur Client | keine | keine |
| G | bestehende Chunks unverändert, nur neu erzeugte Bandos sitzen richtig | keine | nur Server (Weltgenerierung) | keine | keine |

- **Protokoll:** Keiner dieser Punkte braucht eine neue Protokollnummer. 0.6.0 hebt sie aus anderen Gründen
  (Basis-Commit, einmal).
- **API (SemVer):** keine Änderung an `de.lardtom.propwash.api`; die API-Version setzt der API-Strang.
- **JMP 1.1.1 mit Propwash 0.6.0 (Altmodus, Übersicht `README.md` §3.5):** JMP-Stacks ohne `_mounted` zeigen das alte
  Bild (Rückfall-Regel), nichts bricht. JMP 1.2.0 bringt die Varianten (J3).
- **Rückweg 0.6.0 → 0.5.2:** keine neuen Speicherdaten, also nichts zu beachten.

## 5. UI und Texte

- **Keine neuen Sprachschlüssel.** A, B, C, D ändern Verhalten bzw. Bild; E nur Hervorhebung; F nur Anordnung.
- **Propwash-Theme:** Die kompakte Zonenzeile nutzt `ThemeText`/`Palette` wie bisher (Art in Zonenfarbe,
  Detail `TEXT_FAINT`, Streifen `ROW_STRIPE`).
- **GUI-Skalierung (F):** Prüfmatrix wie im ui-scale-Selbsttest: 640×480 mit Skala 2 und Auto (GUI 320×240),
  854×480 mit Skala 2 (GUI 427×240), 1280×720 mit Skala 3, je Englisch und Deutsch, mit und ohne Recht.
- **Texte für Changelog und Handbuch** (EN maßgeblich im `CHANGELOG.md`, DE in den Release-Notizen und der Website):

| Punkt | EN | DE |
|---|---|---|
| A | The bottom gate of a stack counts all the way down to the ground – where its bottom bar is missing, flying through counts. Courses, records and ghosts stay as they are. | Das unterste Gate eines Stapels zählt bis zum Boden – wo sein unterer Balken fehlt, zählt der Durchflug. Kurse, Rekorde und Ghosts bleiben, wie sie sind. |
| B | Plugged-in batteries no longer show plugs or cables: with the battery in, the drone hides the battery's plug and leads and the stack's power leads instead of two loose plugs side by side – on every frame, with the battery on top or underneath, and in the web configurator. | Gesteckte Akkus zeigen keine Stecker und Kabel mehr: Sitzt der Akku, blendet die Drohne Stecker und Kabel des Akkus und die Stromkabel des Stacks aus, statt zwei lose Stecker nebeneinander zu zeigen – an jedem Rahmen, mit dem Akku oben oder unten, und im Web-Konfigurator. |
| C | Drones press wooden and weighted pressure plates and trip tripwires, like dropped items do (stone plates still need a player or mob). Thanks to bluepanee for the fix (pull request #1). | Drohnen drücken Holz- und Wägeplatten und lösen Stolperdrähte aus, wie fallengelassene Items (Stein-Druckplatten brauchen weiter Spieler oder Mobs). Danke an bluepanee für den Fix (Pull Request #1). |
| D | Signal: terrain you have seen but that is no longer loaded (far away from you) now blocks video and control link like it should, instead of counting as open air. | Signal: Gelände, das du schon gesehen hast, das aber nicht mehr geladen ist (weit weg von dir), schirmt Video und Steuerung jetzt ab, statt als freie Luft zu zählen. |
| E | Configurator: in paint mode the part under the pointer lights up, also where it can't be painted. | Konfigurator: Im Lack-Modus leuchtet das Teil unter dem Zeiger auf, auch an Stellen ohne Lack. |
| F | World rules: zone names stay readable in small windows. | Welt-Regeln: Zonennamen bleiben auch in kleinen Fenstern lesbar. |

## 6. Tests

### 6.1 Grundsatz: vollständig automatisch prüfen
- Jeder Strang liefert JUnit (reine Logik), Selbsttest-Schritte auf **Fabric und NeoForge**, wo Minecraft nötig ist,
  und Bildschirmfotos auf Fabric, wo es um Optik geht.
- Mehrspieler: Server-Logik (A, C) und Chunk-Verwerfen (D) zusätzlich im **Beitrittstest mit zwei Prozessen**
  (dedizierter Server + Client, `docs/testing.md` §3) nach dem Muster `PROPWASH_JOINTEST_<FALL>=1` mit
  `dev.<Fall>JoinCheck`. Belegt einen Server- und einen Client-Slot; nur über das Slot-Skript.
- Optik (B, F): Bildschirmfotos prüfen zwei unabhängige Prüfer anhand der Abnahmekriterien unten und gegen das
  Playtest-Bild vom 15.10.; ein Prüfer sieht die Bilder ohne die Erklärung des Strangs.
- Website (B-Web, E): Tests immer **mit gesetztem `JMP_WEB_EXPORT`** laufen lassen, sonst überspringen
  `test-configurator` und `test-configurator-render` alle Prüfungen, die den Export brauchen (Befund der 0.5.2-Runde).
- Vor dem Merge: `python3 tools/strip_comments.py`, `fastcheck`, gefilterter Selbsttest beider Loader mit den Präfixen
  des Strangs; vor dem Release der volle Lauf beider Loader (Integration).

### 6.2 A – Gestapelte Gates
- **JUnit** `race/GateLegsOpeningTest` (neu): `insideOpening` bei `legs` für `up` in (0, frame] wahr, ohne `legs`
  falsch; `STACKED`/`BAR` unverändert; `crossing` mit und ohne Flag, vorwärts und rückwärts; `equals` unterscheidet
  `legs`; `CourseData.Gate#geometry` setzt `legs` nie; `layoutHash` vor und nach unverändert. `LapTrackerTest`:
  Runde durch den unteren Streifen zählt nur mit Anbieter `legs = true`, der Anbieter wird bei Durchflügen weiter oben
  nicht gefragt (Zähler im Test-Anbieter).
- **Selbsttest** (`race:`, beide Loader, in `race/client/GateStackSelfTests`):
  - „race: Gate-Stapel: Durchflug unten am Gate auf Beinen zählt (Client und Server)“ – Leiter aus zwei Gates als
    Kurs, gescriptete Strecke in halber Rahmenhöhe durch das untere Gate: Client-Tracker zählt, Server-Treffer da,
    Redstone-Puls des Gates kommt.
  - „race: Gate-Stapel: einzelnes Gate zählt unten erst ab Rahmen“ (Gegenprobe über die Geometrie, ohne Durchflug
    durch den Balken).
  - „race: Gate-Stapel: nach Abbau des oberen Gates wieder Rahmen unten“ (nach höchstens 10 Ticks).
  - „race: Weltkurs-Leiter: Rekorde und Ghost-Layout unverändert“ (Drohnen-Arena: `layoutHash` und Bestenliste vor und
    nach dem Laden mit 0.6.0 gleich).
- **Dedizierter Server:** `PROPWASH_JOINTEST_STACKGATE=1` (`dev.StackGateJoinCheck`): Server baut die Leiter als Kurs,
  der Client fliegt in der FPV-Sitzung die Strecke durch den unteren Streifen. Erwartung
  `joined|stackgate client=counted|stackgate server=accepted`.
- **Abnahme:** Durchflug in 0,5 × Rahmenhöhe zählt auf Client und Server, Bestenliste nimmt die Runde an; einzelne
  Gates verhalten sich wie in 0.5.2; Leiter-Bild `gate_stacks.png` unverändert (byte-gleich).

### 6.3 B – Akku gesteckt
- **JUnit:** `PlugAnchorsTest` liest alle eingebauten Akku-, Stack- und Rahmen-Modelle aus den Ressourcen: jede Buchse
  gefunden, jeder `_mounted` ohne Stecker-Element und mit genau zwei `pigtail`, `bodyRearZ` je Rahmen plausibel.
  `PowerLeadRouteTest` für alle gültigen Kombinationen der eingebauten Teile (Rahmen × Akku × Stack nach den
  Kompatibilitätsregeln): Kabel beginnen auf den Pigtail-Enden (< 0,1 mm), Stecker liegt bündig an der Buchse (Fuge 0),
  kein Stück der Länge 0, kein Kabelstück schneidet den Akku-Pack-Quader, Gesamtlänge < 250 mm. Drei
  Referenz-Kombinationen mit festen Erwartungswerten (`freestyle_5` + `b6s_1300` + `stack_30`; `race_5` + `b6s_1100` +
  `aio_20`, Akku unten; `whoop_65` + `b1s_300_hv` + `aio_whoop`) – **dieselben Zahlen** stehen im Web-Test (Parität).
- **Generator:** `validate.py` läuft in `fastcheck`.
- **Selbsttest:** „drone-render: Akku gesteckt – Varianten und Anker aller eingebauten Teile auflösbar“ (beide Loader);
  „drone-render: Akku gesteckt – Rückfall ohne _mounted“ (Test-Ressourcenpaket ersetzt nur das Grundmodell).
- **Bildschirmfotos** (Fabric, `bildschirmfotos (drone-render): Akku gesteckt – …`): je Rahmen eine Ansicht schräg von
  hinten oben (`battery_plugged_<rahmen>.png`, 5 Bilder), eine Nahaufnahme des Paars am 5″ und eine am Race-Rahmen
  (Akku unten), eine Drohne als Item in der GUI. Bilder anderer Schritte mit Drohnen ändern sich mit; der Prüfer
  verwirft nur Render-Rauschen.
- **Abnahme (Prüfer, ohne Erklärung des Strangs):** je Bild genau ein Steckerpaar, keine zweiten losen Stecker; Kabel
  durchgehend vom Stack zum Stecker; kein Kabel sichtbar durch Carbonplatten (Toleranz 1 Pixel); Steckerfarbe passt
  zur Buchse; Whoop mit weißem BT2.0-Paar.
- **JMP (J3):** dieselben Bildschirmfotos für je einen JMP-Rahmen jeder Klasse; **Web (S3):** Render-Test für jede
  gültige Kombination (ein Paar, Kabelenden auf den Ankern < 0,5 Modell-Pixel), Referenz-Kombinationen wie oben,
  Bildschirmfoto des Viewers neben dem Spielbild.

### 6.4 C – Druckplatten
- **JUnit:** Tag-Datei enthält genau `#minecraft:pressure_plates` und `minecraft:tripwire`; Bewegungs-Schwelle der
  Klasse (reine Logik ausgelagert, `DroneBlockTriggers.moved(previous, now)`).
- **Selbsttest** (`interact:`, beide Loader):
  - „interact: Drohne drückt Holzdruckplatte“ (ruhende Drohne auf Eichen-Platte → Strom innerhalb von 5 Ticks;
    Drohne weg → aus).
  - „interact: Drohne auf leichter Wägeplatte gibt Signal 1“.
  - „interact: Stein-Druckplatte bleibt bei Drohne aus“.
  - „interact: Drohne löst Stolperdraht aus“ (zwei Haken mit Faden → Strom).
  - „interact: Drohne ohne Nebenwirkungen an Feuer, Lava, Kaktus, Portal und Spinnwebe“ (nicht brennend,
    `damageTaken` 0, gleiche Dimension, Spinnweben-Bremse wie 0.5.2).
- **Dedizierter Server:** `PROPWASH_JOINTEST_PLATES=1` (`dev.PlateJoinCheck`): Server legt Holzplatte + Tür, der Client
  schwebt seine gesteuerte Drohne (Position vom Client) auf die Platte. Erwartung `joined|plate powered|door open`.

### 6.5 D – Signal
- **JUnit:** `TerrainColumnsTest` (Kodierung, `medium` je Höhe, Laub, Grenzen), `TerrainCacheTest` (LRU,
  Obergrenze, `clear`), `ObstructionTracer` mit Test-Welt: unbekannt = Luft, gemerkt = Verlust wie geladen (±3 dB).
- **Selbsttest** (`signal:`, beide Loader):
  - „signal: Gelände außerhalb der Sichtweite gemerkt“ – Steinwand 30 × 20 rund 120 Blöcke entfernt; Verlust A mit
    geladenem Chunk messen (gleiche Punkte, `ObstructionScan` mit `ClientBlockLoss`), Spieler 250 Blöcke wegschicken
    (Chunks verworfen), Verlust B messen: |A − B| ≤ 3 dB und B ≥ 20 dB.
  - „signal: ohne Gedächtnis Luft (Gegenprobe)“ – Test-Schalter aus → B = 0 dB (zeigt das alte Verhalten).
- **Dedizierter Server:** `PROPWASH_JOINTEST_SIGNAL=1` (`dev.SignalJoinCheck`): Server baut die Wand und teleportiert
  den Spieler; Client misst wie oben. Erwartung `joined|signal_memory=ok`.

### 6.6 E – Hover (Web, S3)
- Pick-/Render-Test: Hover auf eine Fläche ohne Gruppe eines lackierbaren Teils → Mesh unter dem Zeiger
  `highlightAll > 0`, Flächen des Platzes voll, Beschriftung zeigt den Platz; Hover auf ein Teil ohne Platz wie bisher;
  Teile-Modus unverändert.

### 6.7 F – Zonenzeile und Doku
- **Selbsttest** (`ui-scale:`): neue Prüfung „Zonenname ganz“ (Befund `TRUNCATED`) für eine Zone „Selbsttest Suche“ in
  der Matrix aus §5; Bildschirmfotos `configurator_world_rules_zones_narrow_en.png` und `_de.png` (GUI 320×240).
- Bildschirmfoto-Schritt der Welt-Regeln ohne Recht: „Schließen“ frei (Prüfer).
- `grep` in der Release-Prüfung: kein „Update auf 0.5.1“ in `docs/RELEASE-NOTES.md`.

### 6.8 G – Bando-Lage
- **JUnit:** `BandoPlacementTest` über acht feste Seeds je Familie: kein Bauteil ohne tragenden Kontakt, kein Laub ohne
  Stamm (Abstand 7) im Bereich plus Rand; Baupläne der übrigen Seeds byte-gleich zum Stand vor dem Fix.
- **Selbsttest** `bando:` (Fabric und NeoForge): Welt mit den beiden Befund-Seeds erzeugen, beide Bandos laden, Prüfung
  wie im JUnit an den echten Blöcken; Bildschirmfotos (Fabric) beider Stellen vorher/nachher.

## 7. Doku und Website

**Repository (je Strang):**
- `CHANGELOG.md` (Texte aus §5), `docs/RELEASE-NOTES.md` (Absatz je Punkt, C mit Nennung), FOUNDATION.md (§3.F),
  `docs/ASSETS.md` (B), `docs/design/signal-repeater.md` §8 (D), `docs/testing.md` (neue Beitrittstest-Fälle und
  Selbsttest-Gruppen), `docs/api/addon-guide.md` (B: optionale Variante `_mounted`, Element-Namen `connector`,
  `connector_grip`, `bt_connector`, `pigtail`).

**Website (Strang S5 bzw. S3 in `website-jmp.md`):**
- Spielerhandbuch, Abschnitt Gates/Stapeln: Satz aus A. Abschnitt Welt: Druckplatten und Stolperdraht (C), mit
  Hinweis auf den Tag `propwash:drone_triggers` für Server-Betreiber. Abschnitt Signal: „Propwash merkt sich Gelände,
  das du gesehen hast; nie gesehenes Gelände zählt als offen.“ (D).
- Teile-Format für Add-ons: Variante `_mounted` und Element-Namen (B).
- Changelog-Seite: aus `CHANGELOG.md`, Nennung von bluepanee bleibt erhalten.
- Konfigurator: B-Web und E nach §3.B.4 und §3.E.

## 8. Risiken

| Risiko | Folge | Gegenmaßnahme |
|---|---|---|
| A: Client findet das Gate-Entity nicht (Chunk am Rand) | unterer Streifen zählt auf dem Client nicht, Server schon → Runde fehlt | beim Durchflug ist die Umgebung geladen; Selbsttest und Beitrittstest prüfen beide Seiten; Fallback ist das Verhalten von 0.5.2 |
| A: Client rechnet `covered` bis 5 Ticks später als der Server | Runde direkt nach dem Stapeln könnte nur auf einer Seite zählen | vernachlässigbar (0,25 s); Beitrittstest wartet 1 s nach dem Bau |
| A: Rekord-Fairness | neue Runden dürfen den unteren Streifen nutzen | gewollt (Fix); alte Rekorde bleiben gültig, weil die Öffnung nur wächst |
| B: Kabel schneidet Rahmenteile bei JMP-Rahmen | sichtbare Fehler | Bildschirmfotos je Klasse; `power_lead.bend_mm` als Ausweg; Prüfer-Kriterium |
| B: Bildschirmfotos aller Drohnen ändern sich | große Bild-Diffs, Rauschen | Prüfer verwirft Render-Rauschen wie in 0.5.2; Bilder nur aus dem vollen Lauf |
| B: Parallele Arbeit an `DroneRenderState` und `FallbackShapes` außerhalb dieses Strangs | Merge-Konflikt | B ändert `FallbackShapes` nicht und in `DroneRenderState` nur ein neues Feld; die Integration achtet darauf |
| B: Kern, JMP und Web rechnen verschieden | Web-Bild weicht ab | gleiche Konstanten, Referenz-Kombinationen mit identischen Zahlen in beiden Tests |
| C: Redstone-Fallen und Türen reagieren auf Drohnen | unerwünschtes Öffnen auf Servern | Vanilla-Regel wie Items; Tag per Datenpaket leer machbar; im Handbuch erklärt |
| C: Signatur `entityInside` in 26.3 anders | Bau bricht | beim Bau gegen 26.3 prüfen (in §3.C markiert) |
| D: Gemerktes Gelände ist veraltet (jemand baut ab) | Signal etwas zu schlecht | nur bis zum nächsten Laden; besser als Luft |
| D: Gebäude zählen als Vollblock bis zum Dach | in Städten pessimistisch | Verlust ist ohnehin bei 60 dB gedeckelt; Seitenstrahlen finden Wege über Dächer |
| D: Mixin-Ziel in 26.3 anders benannt | Bau bricht | beim Bau prüfen; Mixin `require = 1` |
| Gemeinsame Dateien (`CHANGELOG.md`, `docs/RELEASE-NOTES.md`, `FOUNDATION.md`, `QuadcopterEntity`, `GateEntity`) | Merge-Konflikte | jeder Strang hängt nur an; kleine Hunks; Reihenfolge in §10 |

## 9. Entscheidungen

| Nr. | Was | Warum | Verworfen |
|---|---|---|---|
| E1 | Vorgaben sinngemäß mit Quelle statt wörtlich | `docs/design` wird öffentlich exportiert; Konventionen und Export-Prüfung verbieten wörtliche Zitate aus privaten Notizen | wörtliche Zitate |
| E2 | Flashback-Graph-Ring, CrashOutcomeTest und FOUNDATION-Knopf fallen weg | in `v0.5.2` behoben (Commits in §2.3) | erneut anfassen |
| E3 | Clone-Tests nicht hier, sondern im Strang `test-portability` | dort vollständig geplant (inkl. x86-Lauf); doppelte Stränge würden kollidieren | eigener Strang hier |
| E4 | Unterkante der Öffnung des Gates auf Beinen = 0 (Boden) | Füße liegen in den Pfosten, nichts ragt in die Öffnung | Fußhöhe als Unterkante |
| E5 | Flag `legs` nur in Entity-Geometrie und Client-Anbieter, nicht in Kursdaten | keine Protokoll-, Speicher- oder Layout-Änderung, Bestenlisten und Ghosts bleiben; `RaceServer`/`CourseRegistry` bleiben beim Server-Spiel-Strang | Flag in `CourseData`/`GateRecord` mit Sync (Protokoll, `layoutHash`-Frage, fremde Dateien) |
| E6 | Client fragt das Entity nur bei Durchflügen im unteren Streifen | keine Entity-Suche je Tick und Gate | Suche bei jedem Kurs-Sync oder jedem Tick |
| E7 | `GateItem` bleibt | `foot = frame` bei `STACKED` ist dort Platzierung und richtig | Änderung an `GateItem` |
| E8 | Akku-Modelle bleiben, Stacks bekommen `_mounted` | Items behalten ihren Stecker; viel weniger Modell-Änderungen (Kern 4, JMP 12 statt 98); Vorbild `_under` | Stecker aus allen Modellen entfernen und Paar frei parken (Annahme im Website-Dokument) |
| E9 | Anker aus Element-Namen, nicht als Zusatzdaten in Render-Definitionen | Namen gibt es in Kern- und JMP-Modellen schon; Web liest dieselben Roh-Modelle | Anker-Felder in `items/drone_render/…` |
| E10 | Paar sitzt an der Buchse des Akkus, Kabel als drei achsparallele Stücke je Ader | sichtbar wie an echten Quads, passt zum Voxel-Stil, einfach zu prüfen | schräge oder gebogene Kabelstücke; Paar im Rahmen am Stack (dort verdeckt) |
| E11 | Rückfall auf das heutige Bild, `_mounted` nur aus demselben Ressourcenpaket | fremde Pakete und Add-ons ohne Variante sehen nie kaputt aus | immer `_mounted` aus Propwash |
| E12 | Druckplatten über Tag `propwash:drone_triggers` statt `applyEffectsFromBlocks` | gleiche Wirkung ohne Portale, Kaktus-Schaden, doppelte Spinnweben-Bremse; erweiterbar per Datenpaket | PR 1:1 übernehmen; zusätzlich alle Nebenwirkungen einzeln abschalten (bricht bei jedem Vanilla-Update) |
| E13 | Kein `fireImmune` | im PR nur wegen der Vanilla-Blockwirkung nötig; würde Drohnen gegen jeden Feuerschaden immun machen | übernehmen |
| E14 | Signal: Client-Gedächtnis gesehener Chunks, nie Gesehenes bleibt Luft | ohne Protokoll und Server-Last; Flugkorridor ist praktisch immer bekannt | Server-Geländeprofil (Pakete, Server-CPU); Schätzung aus dem Weltgenerator (ignoriert Bauten); Speichern auf der Platte |
| E15 | Säule = Oberkante + Laubdach + Material, 4 096 Chunks LRU | 4 MB, schnell, genügt für Hügel und Wälder | volle Blockkopie (zu groß); 4×4-Raster (zu grob an Kanten) |
| E16 | Hover-Fix: Teil unter dem Zeiger leicht aufhellen, Klick bleibt Teil → Platz | entspricht dem Spiel; kleinste Änderung | Klick auf unlackierbare Stelle sperren (weicht vom Spiel ab) |
| E17 | Kompakte Zonenzeile nur bei Platzmangel | große Fenster und bestehende Bildschirmfotos bleiben gleich | Knopf immer schmaler; Art weglassen |
| E18 | Release-Notizen: „Großes Update nach 0.5.1.“ | gemeint ist der Abstand zu 0.5.1 | Satz streichen |
| E19 | Keine neue Welt-Regel für Druckplatten | Vanilla-Verhalten wie Items, Tag reicht | Welt-Regel „Drohnen lösen Blöcke aus“ |
| E20 | Stolperdraht bleibt im Tag | der PR löst ihn über die Vanilla-Blockwirkung ebenfalls aus; ein Eintrag, per Datenpaket entfernbar | nur Druckplatten (weicht vom PR ab) |
| E21 | Bando-Lage in 0.6.0 beheben, nur für neue Chunks | für die nächste Version vorgemerkt; bestehende Welten bleiben, wie sie sind | nur als bekannte Kleinigkeit führen; bestehende Bandos umbauen |
| E22 | `docs/RELEASE-NOTES.md` schreibt nur die Integration | eine Sammeldatei mit einem Schreiber; der Strang liefert den Satz | Strang ändert die Datei selbst |

## 10. Arbeitspakete

Repos: `propwash` (Kern), `propwash-justmoreparts` (JMP), `propwash-site` (Website). Branches vom aktuellen
Integrationsstand 0.6.0 (Übersicht `README.md` §4.1). Minecraft-Minuten = Selbsttest- und Beitrittstest-Zeit des
Strangs; Läufe nur über das Slot-Skript (ein Beitrittstest belegt einen Server- und einen Client-Slot).

| Strang | Branch / Repo | Besitzt | Abhängigkeiten | MC-Min. | Größe |
|---|---|---|---|---|---|
| `gate-legs` (A) | `fix/0.6.0-gate-legs` / propwash | `race/GateGeometry` (nur Feld `legs`, `withLegs`, `openingFloor`, `insideOpening`, `crossing`-Überladung, `equals`/`hashCode`), `race/GateEntity#geometry()`, `race/GateOpenings` (neu), `race/LapTracker` (Anbieter, Durchflug-Schleife), `race/client/ClientGateOpenings` (neu), eine Zeile in `race/client/ClientRaceTracker`, `race/GhostTrack#splits`-Überladung und ihre Aufrufer in `race/client/GhostLibrary`; Tests `race/GateLegsOpeningTest` (neu), `LapTrackerTest` (Ergänzung), Selbsttests in `race/client/GateStackSelfTests`, `dev/StackGateJoinCheck` (neu); FOUNDATION-Abschnitt „Gates stapeln“ | keine; nicht `GateItem`, `RaceServer`, `CourseRegistry`, `CourseData` | 20 | S–M |
| `block-triggers` (C) | `fix/0.6.0-block-triggers` / propwash | `interact/DroneBlockTriggers` (neu), `data/propwash/tags/block/drone_triggers.json` (neu), ein Aufruf in `drone/QuadcopterEntity#tickServer`, Selbsttests `interact/client/DroneTriggerSelfTests` (neu), `dev/PlateJoinCheck` (neu); FOUNDATION-Zeile `interact` | keine; nach `accessory-gaps` (API-Dokument) rebasen, falls zuerst gemergt | 15 | S |
| `signal-memory` (D) | `fix/0.6.0-signal-memory` / propwash | `video/link/TerrainColumns`, `video/link/TerrainCache`, `video/client/TerrainMemory` (alle neu), `video/client/ClientBlockLoss`, `mixin/video/client/ClientChunkCacheMixin` (neu) + Eintrag in `propwash.video.mixins.json`, Tests `video/link/TerrainColumnsTest`, `TerrainCacheTest`, Selbsttests `video/client/SignalMemorySelfTests` (neu), `dev/SignalJoinCheck` (neu); `docs/design/signal-repeater.md` §8 | keine; RX-Block-Strang nutzt `ObstructionTracer.World` ohne `ClientBlockLoss` zu ändern | 20 | M |
| `battery-plug` (B Kern) | `fix/0.6.0-battery-plug` / propwash | `drone/client/render/PlugAnchors`, `PowerLeadRoute`, `PowerLead` (neu), `DroneRenderResources` (Anker, Quellpaket), `PartModel` (`_mounted`), `DroneAssembly`, `DroneVisuals`, ein Feld in `DroneRenderState`, `DroneLayoutParser`/`DroneLayout` (`power_lead`), `guide/client/render/SceneDroneVisual`, `PaintPicker` (ignorieren); Generator `tools/assets/parts/stacks.py`, `generate.py`, `validate.py`; erzeugte `models/part/stack/*_mounted*`, `items/drone_render/stack/*_mounted*`; Tests `PlugAnchorsTest`, `PowerLeadRouteTest`; Selbsttest- und Bildschirmfoto-Schritte `drone-render: Akku gesteckt …`; `docs/ASSETS.md`, `docs/api/addon-guide.md` | keine; liefert den Vertrag §3.B an J3 und S3; nicht `FallbackShapes` | 25 | M |
| `ui-doc-polish` (F) | `fix/0.6.0-ui-doc-polish` / propwash | `zone/client/ZoneSection` (ggf. `ZoneEditPanel`), Prüfung „Zonenname ganz“ in `dev/client/UiScaleSelfTests` (nur anhängen), Bildschirmfoto-Schritt Welt-Regeln ohne Recht (Hover-Zeile), neue Bilder `configurator_world_rules_zones_narrow_*`, `configurator_world_rules_readonly_*`, Textstellen „Rennen neu starten“ in `docs/` und Sprachdateien; den Satz für `docs/RELEASE-NOTES.md` liefert der Strang im Bericht | keine | 10 | S |
| `bando-placement` (G) | `fix/0.6.0-bando-placement` / propwash | `bando/plan/BandoSite`, `bando/BandoPiece` (`underpin`, `foundation`), `bando/plan/LeafDistance`, `BandoPlacementTest` (neu), Selbsttest `bando:` (Ergänzung) | keine | 20 | S–M |
| J3 `jmp-xt60` (B JMP) | `feat/1.2.0-jmp-xt60` / propwash-justmoreparts (Website-Dokument) | wie dort, aber nach Vertrag §3.B: nur `_mounted`-Varianten der Stacks, Akku-Modelle unverändert | `battery-plug` in der Integration (Format, Renderer) | 15 | S |
| S3 `site-configurator` (B Web, E) | `site/0.6.0-site-configurator` / propwash-site (Website-Dokument) | Teil-Aufgaben §3.B.4 und §3.E in `tools/build-configurator-models.mjs`, `assets/js/configurator/assembly.js`, `viewer.js`, Tests | `battery-plug` + J3 im Web-Export | 0 | Anteil M |

**Reihenfolge:**
1. **Parallel:** `gate-legs`, `block-triggers`, `signal-memory`, `battery-plug`, `ui-doc-polish`, `bando-placement`.
   Keine Überschneidung der Dateien untereinander; gemeinsame Dokumente (`CHANGELOG.md`, FOUNDATION) nur anhängen,
   `RELEASE-NOTES.md` nur über die Integration.
2. **Danach:** J3 und der B/E-Anteil von S3, sobald `battery-plug` in der Integration ist und ein Web-Export mit den
   `_mounted`-Varianten vorliegt.
3. **Integration:** voller Selbsttest beider Loader, alle drei neuen Beitrittstest-Fälle, Website-Tests mit
   `JMP_WEB_EXPORT`, zwei unabhängige Prüfer für die Bilder aus B und F.

**Minecraft-Bedarf gesamt:** etwa 110 Minuten in den Strängen (A 20, B 25, C 15, D 20, F 10, G 20), dazu J3 15 Minuten
und der volle Lauf der Integration.
