# Teile-Freiheit – Welt-Regel „Teile müssen zusammenpassen“ (Propwash 0.5.0)

Auftrag des Besitzers (03.10.2026, sinngemäß): Man soll abschalten können, dass Teile nicht zusammenpassen, damit sich
jedes Teil mit jedem anderen kombinieren lässt. Die Drohne fliegt dann mit den echten Daten der gewählten Teile.

## 1. Die Regel

| Regel | Typ | Standard | Gruppe | Wirkung |
|---|---|---|---|---|
| `propwash:part_compatibility` („Teile müssen zusammenpassen“, „Parts must fit“) | Schalter | an | Spiel | an = wie bisher; aus = jede Kombination ist baubar und fliegt |

Sie ist eine normale Welt-Regel aus dem Katalog `server.rules.WorldRuleSpecs` und erscheint damit von selbst im Tab
„Welt-Regeln“, in `/gamerule`, im Vanilla-Fenster beim Anlegen der Welt, in `/propwash rules` und im Export
`gamerules.json` für die Website (`docs/design/world-rules.md` §4). Sie ist **keine** Physik-Regel
(`PhysicsRules#modified` bleibt falsch): Gültige Baupläne fliegen mit der Regel an und aus bitgleich.

## 2. Was aufgehoben wird und was bleibt

Grundsatz: Aufgehoben wird jede Prüfung, die nur sagt „das passt in echt nicht zusammen“. Es bleibt, was das
Datenmodell oder die Bedeutung eines Slots braucht, und was eine eigene Welt-Regel hat.

| Prüfung | Regel aus | Warum |
|---|---|---|
| `MOTOR_MOUNT` – Motor passt nicht auf den Rahmen | aufgehoben | Bohrbild ist reine Kompatibilität; Arme und Motorposition kommen vom Rahmen |
| `PROP_TOO_LARGE` – Props zu groß, überlappen | aufgehoben | Flugmodell rechnet mit Durchmesser und Armlänge, Überlappung ist nur Optik |
| `PROP_TOO_SMALL` – Props zu klein für den Rahmen | aufgehoben | dito |
| `PROP_MOUNT` – Prop-Nabe passt nicht auf die Motorwelle | aufgehoben | Befestigung ist reine Kompatibilität |
| `STACK_MOUNT` – Stack passt nicht in den Rahmen | aufgehoben | Lochabstand ist reine Kompatibilität |
| `BATTERY_FRAME` – Zellzahl außerhalb der Rahmenklasse, Akku zu schwer (`BatteryFit`) | aufgehoben | Grenzen der Rahmenklasse; die Masse rechnet das Flugmodell trotzdem ehrlich mit |
| `ACCESSORY_MOUNT` – Rahmen hat keine Halterung oben/unten | aufgehoben | Zubehör sitzt dann am Ersatz-Anbaupunkt `AccessoryAnchor.fallback` (Rendering, Masse, Kamera wie bisher bei Addon-Rahmen ohne Tabelle) |
| Akku-Schnelltausch und Akku-Platz im Wartungsmenü (`BatteryFit`) | aufgehoben | dieselbe Regel wie in der Werkbank |
| Prop-Tausch im Wartungsmenü: zu groß, zu klein, falsche Nabe (`PropService.Fit.TOO_LARGE`, `TOO_SMALL`, `WRONG_MOUNT`) | aufgehoben | dieselben Regeln wie `PROP_TOO_LARGE`, `PROP_TOO_SMALL`, `PROP_MOUNT`; vorher prüfte das Wartungsmenü nur die Größe nach oben, sodass sich mit Addon-Teilen auch bei Regel an eine Drohne bauen ließ, deren Teile nicht passen |
| Import `NEW_DRONE`, `BuildAccess#createDrone`, Kreativ-Tab-Presets von Addons | aufgehoben | folgen `problems(rule)` |
| Server-Start-Sperre „Teile passen nicht“ | nur bei Regel an | siehe §3 |
| `MISSING_PART` – Teil aus einem nicht installierten Addon | **bleibt** | ohne Definition gibt es keine echten Daten zum Fliegen |
| Slot nimmt nur seine Kategorie (Rahmen in den Rahmen-Slot …) | **bleibt** | harte Datenregel: `DroneBuild` hat genau ein Teil je Kategorie |
| Zubehör nur in den Platz seiner Halterung (Action-Cam oben, LED/Greifer unten) | **bleibt** | die Halterung ist Eigenschaft des Zubehörs (`AccessoryType.mount`), `Accessories` hat einen Platz je Halterung |
| je vier gleiche Motoren und Props | **bleibt** | Datenmodell und Flugmodell kennen nur eine Sorte je Drohne |
| Warnungen (`MOTOR_OVERVOLTAGE`, `STACK_VOLTAGE`, `ESC_UNDERSIZED`, `BATTERY_WEAK` …) und Analyse-Warnungen | bleiben **Hinweise** | sie haben nie gesperrt |
| ESC brennt bei zu vielen Zellen durch, Hitze-Drosselung | **eigene Regel** `propwash:hardware_limits` | siehe §4 |
| Brille muss zum Videosystem passen | **bleibt** | kein Bauteil der Drohne, sondern Funkstrecke zwischen Drohne und Brille |
| Greifer-Tragkraft, Kanal-Grenze, Rechte | **bleiben** | Spielregeln anderer Module, keine Teile-Kompatibilität |

Die aufgehobenen Probleme bleiben sichtbar: Werkbank (Überschrift „Welt-Regel erlaubt:“ ohne Punkt, darunter die
Probleme in Gelb, betroffene Slots gelb statt rot, Hinweis am Ergebnis; reicht der Platz der Liste nicht, endet die
letzte angeschnittene Zeile mit „…“ und „+N weitere …“ zeigt alles als Tooltip), Drohnen-Tooltip (Statuszeile „Passt in
echt nicht (Welt-Regel)“, mit Shift „In echt nicht baubar (Welt-Regel):“ und die Probleme), Übersicht im Konfigurator
(„… (in echt nicht baubar)“ in Gelb),
Wartungsmenü („… – erlaubt (Welt-Regel)“, „Es passt jedes Teil.“). Ist die Regel an und sind es nur
Kompatibilitätsprobleme, nennt der Hinweis an der Werkbank die Welt-Regel.

## 3. Wer entscheidet (Mehrspieler)

Der Server. Das Werkbank-Ergebnis berechnet das Menü auf dem Server mit der Gamerule des Servers
(`WorldRules.partCompatibility(level)`); schaltet jemand die Regel um, während das Menü offen ist, rechnet
`DroneWorkbenchMenu#broadcastChanges` das Ergebnis neu. Ebenso Import (`ImportService`), Akku-Schnelltausch
(`DroneInteractions`) und Wartungsmenü (`DroneServiceMenu`). Der Client sieht die Regel über das normale
Welt-Regel-Paket (`ClientWorldRules#partCompatibility`, über `WorldRules.clientValues` auch für gemeinsamen Code wie das
Werkbank-Menü und den Drohnen-Tooltip) und zeigt dasselbe an; ein Client mit falscher Vorstellung bekommt kein Ergebnis.

Fliegen: Ist die Regel an, lehnt der Server den Start einer Drohne ab, deren Teile nicht zusammenpassen
(`ServerRules#refusesParts`, Meldung `propwash.server.deny.parts_incompatible`); Drohnen mit fehlendem Addon-Teil
behalten das bisherige Verhalten (Start ja, scharf schalten nein). Das Flugmodell auf dem Client prüft beim scharf
Schalten dieselbe Regel (`SimTuning.partCompatibility`, `DroneSim#buildable`): Wer fliegt, fliegt weiter, wenn die Regel
während des Flugs angeht; nach dem Entschärfen lässt sich die Drohne nicht wieder scharf schalten (`BUILD_INVALID`).

Bestehende unmögliche Drohnen bleiben Items, wenn die Regel wieder angeht: Tooltip „Nicht flugfähig“, Zerlegen an der
Werkbank geht.

Bestenlisten: Flüge mit einer Drohne, die nur mit der Regel aus baubar ist (`!DroneBuild#buildable()`), zählen wie
veränderte Physik nicht (`RaceServer#modifiedPhysics`, `ClientRaceTracker`, Flugtraining). Der Rekord je Rahmenklasse
bleibt so vergleichbar.

## 4. Überspannung: zwei unabhängige Regeln

`part_compatibility` sagt nur, ob man Teile **zusammenbauen** darf. Was danach elektrisch passiert, regelt weiter
`propwash:hardware_limits`:

| `part_compatibility` | `hardware_limits` | 8S-Akku am 1S-Whoop-AIO |
|---|---|---|
| an | an/aus | nicht baubar (`BATTERY_FRAME`), Start abgelehnt |
| aus | an | baubar; beim scharf Schalten brennt der ESC durch (`ESC_BURNED`, `HARDWARE_FAULT`), wie bisher bei einem 6S-Akku am 4S-Stack |
| aus | aus | baubar und fliegt mit 8S: Motoren drehen weit über ihrer Grenze, keine Hitzedrosselung |

So bleibt jede Regel bei ihrer Frage, und wer „alles erlaubt“ will, schaltet beide aus.

## 5. Flugmodell

Das Flugmodell rechnet jede Kombination mit den echten Daten ihrer Teile; es gibt keinen Sonderfall für unmögliche
Baupläne. Neu ist nur, welche Prüfung das scharf Schalten sperrt (`AirframeSpec.assemblable` = keine harten Probleme).
Geprüft wurde, dass dabei nichts kippt:

- Alle 2250 Kombinationen der eingebauten Rahmen, Stacks, Motoren, Props und Akkus: `AirframeSpec` und `BuildAnalysis`
  ohne Ausnahme; Masse, Schub, Ströme, Topspeed, Ansprechzeit, Roll-Beschleunigung und Crash-Tempo endlich, die
  „nicht verfügbar“-Werte (Schweben, Flugzeiten bei Drohnen, die nicht schweben) höchstens NaN, nie unendlich.
- 432 Flüge über 5 s mit Vollgas, Rolle, Flip und Gier aus 3 Rahmen × 2 Stacks × 3 Motoren × 3 Props × 4 Akkus (1S bis 12S)
  mit Action-Cam (Heavy) und Greifer, je mit und ohne Hardware-Grenzen: Zustand, Akku, Motoren und Telemetrie in jedem
  Frame endlich, kein `SIM_FAULT`.
- Darunter 13″-Props am Whoop (dreht kaum an, hebt nicht ab) und 8S am 1S-AIO (dreht weit über die 1S-Drehzahl).

Damit waren keine zusätzlichen Klemmen oder Schutzwerte nötig; das bestehende Rückrollen bei NaN (`SIM_FAULT`) bleibt
die letzte Sicherung. Gültige Baupläne rechnen bitgleich: `tools/calibration.sh` (`calibration.txt`, `motion.txt`,
`fall.txt`) byte-gleich zum Stand vor der Regel, `AirframeGoldenTest` unverändert.

## 6. API (Parts-API 1.5.0)

- `BuildAccess#partCompatibility()` (Default-Methode, `true`): ob auf dieser Seite gerade die Regel an ist (Server:
  Gamerule, Client: synchronisierter Wert, ohne Welt: an).
- `BuildAccess#check(spec)` folgt der Regel: Bei aus stehen die aufgehobenen Probleme unter `warnings()` statt
  `problems()`, `buildable()` ist wahr. Mit Standardregel unverändert.
- `BuildAccess#createDrone(data)` folgt der Regel (wirft bei aus nur noch für unbekannte Teile, Zubehör im falschen
  Platz und fehlende Teile).

Addons für 1.0–1.4 laufen unverändert; bei abgeschalteter Regel sehen sie unmögliche Baupläne als baubar mit Warnungen.

## 7. Technik

| Datei | Inhalt |
|---|---|
| `build/PartCompatibility` | reines Java: `ENFORCED`/`LIFTED`, `blocks(issue)`, `blocking/lifted(issues)`, `batteryFits(frame, battery)` |
| `build/DroneBuild` | `problems(rule)`, `liftedProblems(rule)`, `buildable(rule)`, `Issue#compatibility()`; `problems()`/`buildable()` bleiben „passt in echt“ |
| `server/rules/WorldRuleSpecs`, `RuleEffects#partCompatibility` | Katalog-Eintrag und Abbildung `RuleValues` → `PartCompatibility` |
| `server/WorldRules` | `partCompatibility(server | level | aktuelle Seite)`, `values(level)`, `current()`, `clientValues(...)` |
| `content/DroneWorkbenchMenu`, `DroneAssembly`, `BuildSlots.Check`, `client/WorkbenchReport`, `client/DroneWorkbenchScreen`, `BuildTexts` | Werkbank und Texte |
| `drone/DroneInteractions`, `DroneServiceMenu`, `PropService` (`fit(rule, build, prop, n)`), `client/DroneServiceScreen`, `QuadcopterItem` | Wartung, Akku-Tausch, Tooltip |
| `server/ServerRules#refusesParts` | Start-Sperre bei Regel an |
| `sim/SimTuning.partCompatibility`, `AirframeSpec.assemblable`, `DroneSim#buildable` | scharf Schalten |
| `race/RaceServer`, `race/client/ClientRaceTracker`, `guide/client/training/TrainingSession` | keine Bestenliste für unmögliche Drohnen |
| `guide/scenes/DroneBuildScene` | Flugschule „Drohne bauen“, Schritt „Werte und Warnungen“: Hinweis-Callout zur Welt-Regel |

## 8. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Id `propwash:part_compatibility` (an = Teile müssen passen) | gleiche Form wie alle Welt-Regeln (`docs/design/world-rules.md` §2); Standard an = bisheriges Verhalten | `propwashPartCompatibility` (camelCase), negierte Regel „Teile-Freiheit an“ |
| Gruppe „Spiel“ | wie `drone_hits_entities` eine Spielregel, keine Physik; keine neue Gruppe nur für eine Regel | neue Gruppe „Werkbank“ |
| Keine Physik-Regel; stattdessen zählt nur der einzelne Flug mit unmöglicher Drohne nicht | gültige Drohnen fliegen bitgleich, ihre Rekorde bleiben fair, auch wenn die Regel aus ist | ganze Welt als „veränderte Physik“ markieren |
| `MISSING_PART`, Slot-Kategorie, Zubehör-Halterung des Teils und vier gleiche Motoren/Props bleiben | Datenmodell und fehlende Daten, keine Kompatibilität | alles freigeben (bräuchte gemischte Motoren im Flugmodell) |
| Aufgehobene Probleme als gelbe Hinweise statt sie zu verstecken | Spieler sehen, was in echt nicht ginge | Probleme ganz ausblenden |
| In der Werkbank eine einzeilige Überschrift „Welt-Regel erlaubt:“ ohne Punkt, angeschnittene Zeilen mit „…“ | die Fehlerliste hat drei Zeilen zu 110 px; ein Zusatz je Zeile oder eine zweizeilige Überschrift ließ kein einziges Problem mehr sichtbar | „(in echt nicht baubar)“ hinter jeder Zeile, Überschrift „Passt in echt nicht (erlaubt):“ |
| Statuszeile „Passt in echt nicht (Welt-Regel)“ im Drohnen-Tooltip ohne Shift, Einzelheiten mit Shift | man sieht es der Drohne im Inventar sofort an, ohne den Tooltip aufzublähen | nur im Shift-Tooltip |
| Überspannung bleibt bei `hardware_limits` | zwei unabhängige Fragen: zusammenbauen dürfen und elektrisch überleben | ESC-Brand an die neue Regel koppeln |
| Server-Start-Sperre nur bei Regel an und nur für Kompatibilitätsprobleme | klare Meldung statt stillem `BUILD_INVALID`; fehlende Addon-Teile behalten ihr Verhalten | keine Server-Prüfung (Client allein) |
| Wer fliegt, fliegt weiter, wenn die Regel angeht | wie bei der Kanal-Grenze; kein Absturz durch einen Regelwechsel | sofort entschärfen |
| Wartungsmenü prüft bei Regel an alle drei Prop-Probleme (zu groß, zu klein, falsche Nabe) | sonst entstünde bei Regel an im Feld eine Drohne, die der Server danach nicht starten lässt; betrifft nur Addon-Teile (eingebaute Rahmen haben kein Mindestmaß, eingebaute Props passen auf jede Welle) | nur „zu groß“ wie bisher |
| Brille ↔ Videosystem bleibt Pflicht | betrifft die Funkstrecke, nicht den Bau der Drohne | mit freigeben |
| Keine Klemmen im Flugmodell | die Prüfungen über alle Kombinationen zeigen endliche Werte; jede Klemme hätte gültige Baupläne verändern können | vorsorglich Drehzahl/Strom begrenzen |
| API-Erweiterung in 1.5.0 statt 1.6.0 | 0.5.0 ist unveröffentlicht und hebt die API ohnehin auf 1.5.0; rein ergänzend (Default-Methode) | eigene Version 1.6.0 |
| Netzprotokoll bleibt 10 | die Regel reist im bestehenden Welt-Regel-Paket (Liste aus Id und Wert), kein neues Paket, kein neues Feld | Protokoll 11 |

## 9. Tests

- JUnit `build/PartCompatibilityTest`: jede bisher abgelehnte Kombination (`MOTOR_MOUNT`, `PROP_TOO_LARGE`,
  `PROP_TOO_SMALL`, `PROP_MOUNT`, `STACK_MOUNT`, `BATTERY_FRAME`, `ACCESSORY_MOUNT`) mit Regel an abgelehnt und aus
  erlaubt (Bauplan, Werkbank-Slots, Werkbank-Bericht samt Farben, Übersicht, API-Prüfung, Start-Sperre);
  13″-Props am Whoop, 8S am 1S-AIO und „alles falsch“ (Whoop mit 30×30-Stack, 2807, 13″, 8S, Heavy-Cam und Greifer);
  fehlende Teile bleiben gesperrt; Slots nehmen weiter nur ihre Kategorie; Wartung; Regel ist keine Physik.
- JUnit `sim/PartFreedomFlightTest`: scharf Schalten nach Regel (auch Umschalten im Flug), gültige Presets mit Regel an
  und aus bitgleich, fehlende Teile nie scharf, 13″ am Whoop, 8S am 1S-AIO mit und ohne Hardware-Grenzen, 432 absurde
  Flüge und alle 2250 eingebauten Kombinationen (§5). Mit Addon-Daten (`PROPWASH_CALIBRATION_ADDON`, Tag `addon-daten`)
  zusätzlich jedes Teil aus Just More Parts: jeder Rahmen mit jedem Prop und jeder Motor mit jedem Akku (endliche
  Analyse) und 120 zufällige Kombinationen je mit und ohne Hardware-Grenzen 3 s im Flug ohne `SIM_FAULT`.
- Selbsttest (beide Loader, `content.client.PartFreedomSelfTests`):
  - „teile-freiheit: Werkbank, Import und API folgen der Welt-Regel des Servers“: echtes Werkbank-Menü auf dem Server mit
    einer unmöglichen Drohne (Whoop-Rahmen mit 30×30-Stack, 2807-Motoren, 7″-Props und 6S). Regel an: kein Ergebnis,
    API-Prüfung mit den vier Problemen, Import `NEW_DRONE` → `INVALID`, am Client kein Ergebnis und rote Fehler. Regel aus
    bei offenem Menü: Ergebnis mit genau diesem Bauplan, API-Prüfung baubar mit den Problemen als Warnungen,
    `createDrone` und Import `NEW_DRONE` legen die Drohne an, am Client Ergebnis und gelbe Hinweise. Regel wieder an bei
    offenem Menü: Ergebnis verschwindet auf Server und Client.
  - „teile-freiheit: Akku-Schnelltausch folgt der Welt-Regel“: 6S am Whoop per Schleichen + Rechtsklick, mit Regel an
    `REJECT_BATTERY` und kein Tausch, mit Regel aus `QUICK_SWAP` und der Whoop trägt den 6S-Akku.
  - „teile-freiheit: unmögliche Drohne startet nur mit Regel aus und fliegt“: Regel an → `pilotDenialReason` =
    `parts_incompatible`, `tryStart` = `DENIED`; Regel aus → Sichtflug startet, scharf schalten, steigt über 2 m mit
    endlichem Zustand; Regel an im Flug → bleibt scharf; nach dem Entschärfen sperrt `BUILD_INVALID` das scharf Schalten.
- Selbsttest „Werkbank-Kennzahlen passen neben ihre Werte“ (englisch und deutsch): die Überschrift „Welt-Regel erlaubt:“
  passt in eine Zeile der Fehlerliste (`WorkbenchLayout.issueTextWidth()`).
- Bildschirmfotos (Fabric): `workbench_part_freedom` (Werkbank mit derselben unmöglichen Drohne bei Regel aus, Tooltip der
  Ergebnis-Drohne mit der Statuszeile „Wouldn't fit in reality (world rule)“), `workbench_part_freedom_de` (deutsch, Liste
  mit der Überschrift „Welt-Regel erlaubt:“), `configurator_world_rules_part_compatibility` (Tab mit der Regel auf aus
  und ihrem Tooltip), `flight_school_build_rule` (Flugschule „Drohne bauen“, Hinweis zur Welt-Regel am zu schweren Akku).
