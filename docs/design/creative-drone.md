# Kreativ-Drohne und Drohnen-Editor (Propwash 0.5.0)

Mit dem **Kreativ-Flugcontroller** wird aus jeder Drohne eine Kreativ-Drohne, deren Flugmodell sich frei einstellen
lässt: Massen und Trägheit, Motorpositionen, Propeller samt Schub- und Leistungsbeiwert, Motoren, Akku, ESC,
Luftwiderstand und Kamera. Die Physik bleibt dieselbe – nur ihre Eingangswerte sind frei. Der Editor ist ein Tab im
Konfigurator, im Propwash-Theme.

## 1. Für Spielerinnen und Spieler

### Der Kreativ-Flugcontroller
- Item `propwash:creative_fc` („Kreativ-Flugcontroller“ / „Creative Flight Controller“), nur im Kreativ-Inventar
  (Propwash-Tab bei den Stacks) und per `/give`. Kein Rezept, keine Truhenbeute, Recycling gibt nichts zurück.
- Passt als Stack in **jeden Rahmen** (auch Rahmen aus Add-ons) und verträgt 1–12S. Die übrigen Teile setzt du wie
  gewohnt in die Werkbank; sie bestimmen Aussehen, Videosystem, Brillen-Bindung und Zubehör.
- Die Editor-Werte gehören zum Flugcontroller: Beim Zerlegen nimmt er sie mit, beim nächsten Bauen bringt er sie wieder
  mit (Tooltip „Drohnen-Editor: N eigene Einstellungen gespeichert“, gezählt werden Werte und Basis-Teile).

### Der Drohnen-Editor
Konfigurator öffnen (Taste K) mit der Kreativ-Drohne in der Hand oder während du sie fliegst → Tab **„Drohnen-Editor“**.
Der Tab erscheint nur bei Kreativ-Drohnen.

- **Kennzahlen** oben: Gewicht, Schub/Gewicht, Schwebegas, Topspeed, Flugzeit, Maximalstrom – dieselbe Rechnung wie in
  der Werkbank, live aus den Editor-Werten. Darunter **Hinweise**, z. B. „Props überschneiden sich“, „Trägheit unter
  einem Viertel des automatischen Werts“, „Schneller als die Server-Grenze von 540 km/h“, dazu die Hinweise der Werkbank.
- **Vorlagen und Teilen**: Startwerte laden aus den eingebauten Teilen, jedem Preset (Whoop bis Long Range), Presets von
  Add-ons oder eigenen Vorlagen. „Merken“ speichert die aktuellen Werte unter einem Namen auf deinem Rechner
  (`config/propwash/creative_templates.json`), „Löschen“ entfernt eine eigene Vorlage. „Teilen-Code kopieren“ legt alle
  Werte als kurzen Code (`PWD1-…`) in die Zwischenablage, „Code einfügen“ liest ihn wieder ein – zum Teilen im Chat
  oder auf Discord.
- **Basis-Teile**: Von diesen Teilen kommen alle Werte, die du nicht selbst änderst, auch Dinge ohne eigenes Feld
  (Zellchemie und ihre Spannungskurve, Rahmenform, Ducts im Detail, Statorgröße und Wärmeverhalten der Motoren).
  „Eingebaut“ nimmt das Teil in der Drohne. Ein Preset als Vorlage setzt genau diese Basis-Teile – die Drohne fliegt
  dann bit-gleich wie das Preset.
- **Werte**, gruppiert: Masse und Trägheit · Geometrie und Motorpositionen · Propeller · Motoren · Akku · FC und ESC ·
  Luftwiderstand und Rahmen · Kamera. Klicken auf ‹ ›, Ziehen oder Mausrad ändert in Schritten (Shift: größere
  Schritte), Doppelklick tippt einen Wert ein, **Rechtsklick** stellt den Basis- bzw. Automatikwert wieder her. Eigene
  Werte sind hell, Basiswerte gedämpft.
- **Automatische Werte** („(auto)“): Trägheit, Motorpositionen, Seiten- und Draufsichtfläche und Kameraposition
  rechnet Propwash aus den übrigen Werten, solange du sie nicht selbst setzt. Sobald du eine Motorposition setzt,
  rechnet das Flugmodell mit freien Motorpositionen; jeder Motor bleibt in seinem Viertel.
- **Grenzen**: Jeder Wert hat einen Bereich (im Tooltip). Was darüber hinausgeht, wird begrenzt, und du siehst kurz
  „… auf … begrenzt“. Die Grenzen halten das Flugmodell endlich und flugfähig; seltsame Drohnen sind trotzdem erlaubt.
- **Rates und PIDs** stellst du wie gewohnt in den Tabs „PID-Tuning“ und „Rates“ ein (Knöpfe unten im Editor). Die
  abgeleiteten PIDs passen sich nach dem Speichern an die neuen Werte an.
- **Speichern** (Fußleiste) überträgt die Werte auf die Drohne. Fliegst du gerade scharf, gelten sie nach dem
  Entschärfen.

### Regeln
- Ändern geht nur im **Kreativmodus**. Im Überlebensmodus (oder als Zuschauer) ist der Editor nur lesbar: Werte
  ansehen, Code kopieren und als eigene Vorlage merken geht, Speichern nicht. Der Server prüft das bei jeder Änderung.
- Im Mehrspielermodus rechnet der **Server** mit: Er begrenzt jeden Wert, verwirft unbekannte Werte und Teile und
  lehnt Werte ab, die kein endliches Flugmodell ergeben. Die geltende Grenze für das Höchsttempo (540 km/h) und die
  Welt-Regeln gelten weiter.
- Kreativ-Drohnen zählen **nicht für Bestenlisten** und Server-Rekorde; im Flugtraining gelten ihre Läufe als
  „unterstützt“.
- Der Kamera-Uptilt ist bei Kreativ-Drohnen frei von 0 bis 80° (im Editor und im Wartungsmenü), das Sichtfeld der
  Kamera stellt der Editor ein.
- Was gleich bleibt: Videosystem, Funk, Reichweite, Zubehör (Action-Cams, LED-Streifen, Greifer) und das Aussehen sind
  die eingebauten Teile. Ein 10-Zoll-Prop im Editor bleibt im Bild der eingebaute Prop.

### English summary for the website guide
The **Creative Flight Controller** (creative inventory only, no recipe) fits every frame and turns the drone into a
creative drone. Open the configurator with the drone in your hand or while flying it: the **Drone Editor** tab sets
masses and inertia, motor positions, props with thrust and power coefficient, motors (KV, resistance, current), battery
(cells, capacity, internal resistance, C rating), ESC current, drag areas and coefficient, ducts, impact tolerance and
the camera (position, uptilt, field of view). Key figures and notes update live. Start from the installed parts, any
preset or your own templates, keep templates and share them as a `PWD1-…` code. Right-click a value to go back to the
base or automatic value. Values are clamped to sane ranges, the physics stays the same. Only editable in creative mode –
the server checks every change –, read-only in survival, and creative drones do not count for leaderboards.

## 2. Technik

### Daten
- `build.DroneBuild` bekommt die Komponente `creative` (`creative.CreativeSpec`, sonst `CreativeSpec.EMPTY`). Der
  kanonische Konstruktor setzt sie auf `EMPTY`, sobald der Stack nicht `StackType.CREATIVE_FC` ist – wer den Stack
  tauscht, verliert die Werte. Die 6- und 7-stelligen Konstruktoren bleiben; `withBattery`, `withProp`,
  `withAccessories` und `PartCategory#with` behalten die Werte, `withCreative(spec)` setzt sie.
- `CreativeSpec(base, values)`: `base` = Basis-Teile je `PartKind` (nur `FRAME`, `STACK`, `MOTOR`, `PROP`, `BATTERY`;
  das Videosystem bleibt immer das eingebaute, damit Brillen, Funk und Bildformat stimmen), `values` = Wert je
  Schlüssel aus `creative.CreativeParam` in Anzeige-Einheiten (g, mm, mΩ, cm², g·cm²). Höchstens 96 Werte,
  Schlüssel bis 128 Zeichen, nur endliche Zahlen; leere Schlüssel, `-0.0` und fremde Teilearten fallen beim Bauen weg.
- Speicherformat: Feld `creative` im Bauplan-Codec (`lenientOptionalFieldOf`, fehlt bei leeren Werten – alte und normale
  Baupläne sind bytegleich), Item-Komponente `propwash:creative_spec` am Flugcontroller. Netz: der Bauplan-Stream
  schreibt die Werte nur nach einem Kreativ-FC, normale Baupläne sind auf dem Draht bytegleich zu 0.4.0
  (`SaveFormatGoldenTest`, `MissingPartsCodecTest`).
- Teilen-Code: `PWD1-` + Base64url (ohne Padding) eines Deflate-Stroms (raw) aus Zeilen `base.<art>=<teil>` und
  `<schlüssel>=<zahl>`; höchstens 4096 Zeichen Code und 16 KiB Text, unbekannte Schlüssel überlebt der Code und fallen
  erst beim Begrenzen weg (ältere Versionen lesen neuere Codes so weit wie möglich).

### Rechenweg (Physik unverändert)
- `creative.CreativeFlight#flight(build)` baut den **Flug-Bauplan**: Basis-Teile auflösen (unbekannte → eingebautes
  Teil), dann je Teil, an dem ein Wert hängt, die Teile-Daten über `PartJson.encode` als JSON holen, die Felder
  ersetzen (Schlüssel wie in `docs/design/parts-api-data.md`) und mit `PartJson.decode` + `PartIndex.detached` ein
  losgelöstes Teil mit gleicher Id bauen. Damit gelten alle Prüfungen der Teile-Daten, und die Ableitung in
  `sim.ParamDerivation` rechnet genau wie für Add-on-Teile. Teile ohne Wert bleiben dieselben Objekte.
- Der Flug-Stack bekommt immer 1–12S und eine Brownout-Spannung von höchstens der 1S-Grenze, wenn der Flug-Akku 1S hat,
  sonst die des Basis-Stacks – ein 6S-Basis-Stack mit 1S-Akku würde sonst nie starten, ein 1S-Stack mit 6S sofort
  durchbrennen.
- Motorpositionen: ohne eigene Werte kommen sie vom Basis-Rahmen (explizite Arme, beim Ändern des Radstands
  mitskaliert) bzw. aus `MotorArm.trueX(Radstand, 0)`; sobald ein Wert gesetzt ist, schreibt der Editor explizite Arme
  (Drehrichtungen vom Basis-Rahmen bzw. `MotorArm.PROPS_IN`), fehlende Achsen aus den Automatikwerten. Die Bereiche der
  Schlüssel halten jeden Motor in seinem Viertel (mindestens 5 mm von der Mitte).
- Trägheit: einzige Überschreibung nach der Geometrie. `ParamDerivation#compute(source, flight, inertia)` setzt nach
  `lens` die Werte je Achse (Rollen = `inertiaZz`, Nicken = `inertiaXx`, Gieren = `inertiaYy`, NaN = automatisch), alle
  davon abhängigen Größen (Steuerwirkung, abgeleitete PIDs) rechnen danach. Für normale Baupläne ist `source == flight`
  und `inertia == null` – Ergebnis bitgleich (`AirframeGoldenTest`, `BuiltinRegressionTest`, Kalibrierung, Bewegungs-
  und Fall-Fingerabdrücke unverändert).
- `AirframeSpec.build` bleibt der eingebaute Bauplan (Wear, Bindung, Gleichheit mit der Entity), `buildKey` hängt
  `/creative/<fc>/<hash>` an, damit die PID-Ableitung (`PidAutoTune`) je Werte-Satz neu rechnet. `buildable` kommt vom
  eingebauten Bauplan.
- Zwischenspeicher: Flug-Baupläne, `AirframeSpec` und `BuildAnalysis` bearbeiteter Baupläne liegen in kleinen
  LRU-Speichern (`sim.BoundedCache`, 64/128/32 Einträge), damit Entwürfe im Editor den Speicher nicht füllen.
- Sichtfeld: `VideoState#horizontalFov` und `FlashbackCam` nehmen das Videomodul des Flug-Bauplans, das Videosystem
  selbst (`VideoState#camera`) bleibt das eingebaute. Uptilt: `CameraMount.range(DroneBuild)` gibt bei Kreativ-Drohnen
  0–80° (Standard bleibt der des Rahmens); alle Stellen mit Bauplan nutzen diese Überladung.

### Grenzen und Schutz
- `CreativeParam` legt Bereich, Schritt und Nachkommastellen fest; die Bereiche liegen innerhalb der Prüfungen der
  Teile-Daten (z. B. KV 100–60000, Zellen 1–12, Ct 0,01–0,4, Trägheit 1–5 000 000 g·cm²).
- `CreativeLimits.sanitize` begrenzt, rundet, verwirft unbekannte Schlüssel und nicht auflösbare Basis-Teile und meldet
  jede Änderung als `Note` (Client: Hinweis im Editor, Server: still).
- `CreativeEdits.usable` (Server): Flugmodell muss Masse und Trägheit > 0 und nur endliche Kennwerte haben, sonst
  Ablehnung mit Meldung. Im Flug fängt `DroneSim` weiterhin jeden nicht endlichen Zustand ab (`SIM_FAULT`).
- `CreativeCheck` (nur Hinweise): Props überschneiden sich (Durchmesser > Motorabstand), Trägheit < ¼ oder > 4× des
  automatischen Werts, Schub/Gewicht > 25, Topspeed über `StateValidation.PHYSICS_MAX_SPEED`.
- JUnit `CreativeRobustnessTest`: 160 zufällige Werte-Sätze (auch alle Werte gleichzeitig, logarithmisch über die
  Bereiche) und vier Grenzfälle (alles Minimum, alles Maximum, „Rakete“, „Ziegelstein“) auf allen Presets: Kennwerte
  endlich, 2,6 s Flug mit Schweben, Flips und Vollgas ohne `SIM_FAULT`.

### Server und Netz
- Payload `propwash:creative_edit` (`creative.net.CreativeEditC2S`: Entity-Id oder `HELD_ITEM`, Nebenhand, Werte,
  Uptilt oder NaN), registriert in `creative.CreativeModule` (`PropwashCommon.init` nach `InteractModule`). Der
  Server verlangt Kreativmodus (`Player#isCreative`), für Entities dieselbe Regel wie beim Tuning (eigener Pilot oder
  frei und höchstens `DronePiloting.TUNE_REACH` entfernt), einen Bauplan mit Kreativ-FC und ein brauchbares
  Flugmodell. `Constants.PROTOCOL_VERSION` = 11.
- Während des Flugs übernimmt `PilotSession#syncConfiguration` den neuen Bauplan wie jeden Teiletausch erst entschärft
  (`DroneSim#setBuild`).
- Bestenlisten: `RaceServer.modifiedPhysics`, `ClientRaceTracker` und `TrainingSession` werten Kreativ-Drohnen als
  veränderte Physik.

### Client
- `creative.client.CreativeClient` (`PropwashClient.init` nach `InteractClient`): Konfigurator-Tab
  `creative_editor` (`CreativeEditorTab`, Reihenfolge 290 vor „PID-Tuning“, Speicherort Drohne, nur lesbar ohne
  Kreativmodus; die Fabrik gibt ohne Kreativ-Drohne `null` zurück, der Tab fehlt dann) und die Flugschul-Szene
  `creative_drone` (`creative.CreativeScene`, nur im Kreativmodus sichtbar, verknüpft mit dem Item).
- `creative.CreativeDraft` hält den Entwurf: Werte gleich dem Basis-/Automatikwert fallen weg, Basis-Teile gleich dem
  eingebauten Teil ebenso, Vorlagen werden beim Laden begrenzt.
- Eigene Vorlagen: `creative.client.CreativeTemplates` (`JsonConfig` `creative_templates`, höchstens 64, Namen bis 32
  Zeichen, gespeichert als Teilen-Code; wird erst beim ersten Öffnen des Editors geladen).

### Assets und Daten
- Modell und Textur aus `tools/assets/parts/stacks.py#build_creative_fc` (24-mm-Platine, violett, Goldring, drei LEDs),
  `layout.STACK_KEYS` enthält `creative_fc`; Teile-Daten `data/propwash/propwash_parts/stack/creative_fc.json`
  (6 g, 60 A, 1–12S, AIO, `rarity: epic`, `recycle: none`, `sort: 9`).
- `tools/data/survival.py`: `creative_fc` in `NOT_CRAFTABLE` und `CREATIVE_PARTS` (steht im Tag `#propwash:parts/stack`,
  schaltet wie jedes Propwash-Item Rezepte frei, kein Rezept, keine Beute).
- Eingebaute Teile werden mit ihrer `rarity` registriert (bisher hatte kein eingebautes Teil eine).

### Tests
- JUnit: `CreativeFlightTest` (Preset als Vorlage bitgleich, Werte gleich der Basis bitgleich, jede Wirkung, Arme,
  Kamera-Automatik, Begrenzen, Codecs, Teilen-Code, Zwischenspeicher), `CreativeDraftTest`, `CreativeSceneTest`,
  `CreativeRobustnessTest`. Golden- und Bestandstests über alle Kombinationen nehmen den Kreativ-FC aus (er ist kein
  echtes Teil), damit ihre Werte bitgleich bleiben.
- Selbsttest `kreativ-drohne:` (6 Schritte): Item, Werkbank und Zerlegen, Speichern über den Server, nur lesbar im
  Überlebensmodus, Teilen-Code und Vorlagen, Flug mit Editor-Werten und Änderung nach dem Entschärfen.
- Bildschirmfotos (`-PpropwashScreenshots=true`): `configurator_creative_editor[_arms|_props].png`, deutsch
  `configurator_creative_editor_de.png`, `…_arms_de.png`, `…_camera_de.png`, `…_readonly_de.png`,
  `tooltip_creative_fc_de.png`, `workbench_creative_fc_de.png`, aus der Flugschule `flight_school_creative.png` und
  `flight_school_creative_editor.png`.

### Entscheidungen
| Entscheidung | Warum | Verworfen |
|---|---|---|
| Werte als Teile-Daten durch dieselbe Ableitung | Physik bleibt dieselbe, alle Prüfungen gelten, eine Vorlage fliegt bitgleich | eigene Kennwerte direkt in `AirframeSpec` schreiben (abgeleitete Größen würden auseinanderlaufen) |
| Werte am Bauplan, gespeichert am FC | alle 25 Stellen mit `AirframeSpec.of(build)` rechnen ohne Umbau richtig, Zerlegen verliert nichts | eigene Entity-/Item-Komponente neben dem Bauplan (jede Stelle müsste sie mitnehmen) |
| Nur Abweichungen speichern, Basis-Teile wählbar | kleine Codes, Rechtsklick = zurück, Presets bitgleich | immer alle Werte vollständig (Arme würden den Rechenweg alter Rahmen wechseln) |
| Videosystem nie aus der Vorlage | Brillen, Funk, Bildformat und Videokanal hängen am eingebauten Sender | Video als Basis-Teil (analoge Brille an „digitaler“ Physik) |
| Editor als Konfigurator-Tab | gleiche Drohnen-Auswahl, Speichern/Verwerfen, Theme und Lesemodus wie die Welt-Regeln | eigener Bildschirm (doppelte Logik für Hand/Flug, Speichern und Rückfragen) |
| Ändern nur im Kreativmodus, auch nicht für Operatoren im Überleben | so beschlossen; Kreativmodus ist ohnehin Operator-Sache | Operatoren immer erlauben |
| ESC 1–12S und angepasste Brownout-Spannung im Flug-Stack | Zellzahl im Editor frei, ohne Durchbrennen oder Dauer-Neustart | Zellbereich des Basis-Stacks behalten |
| Kreativ-Drohnen nie in Bestenlisten | freie Physik ist kein fairer Vergleich | nur bei bearbeiteten Werten ausschließen |
| Blattzahl ändert Schub nicht | im Modell kommen Schub und Drehmoment aus Ct/Cp; die Blattzahl wirkt nur auf Verluste kleiner Props und Klang (Tooltip sagt es) | Ct/Cp aus der Blattzahl schätzen (zweite, ungeprüfte Modellannahme) |
| Uptilt im Tuning, Bereich 0–80° | ein Wert für Editor, Wartungsmenü und Kamera | eigener Uptilt-Wert in den Editor-Werten (zwei Quellen) |
