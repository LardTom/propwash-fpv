# Welt-Regeln (Gamerules) – Propwash 0.5.0

Auftrag des Besitzers (02.10.2026): detaillierte Gamerules für Server-Betreiber und Einzelspieler, die für alle gelten,
mit einer Oberfläche. Die physikrelevanten persönlichen Simulationsoptionen wandern dorthin; persönlich bleiben
Steuerung, OSD, FPV-Anzeige und Grafik.

## 1. Die Regeln

Alle Regeln sind echte Minecraft-Gamerules im Namensraum `propwash` (Register `minecraft:game_rule`), Kategorie
`propwash:propwash` („Propwash FPV“). Sie gelten pro Welt, werden mit der Welt gespeichert (`game_rules` in den
Weltdaten), lassen sich mit `/gamerule propwash:<regel> <wert>` setzen und erscheinen auch im Vanilla-Fenster „Spielregeln“
beim Anlegen einer Welt.

| Regel | Typ | Standard | Bereich | Gruppe | Wirkung | Physik* |
|---|---|---|---|---|---|---|
| `propwash:gravity_scale` | Zahl, % | 100 | 0–1000, Schritt 1 | Physik | Schwerkraft auf 9,81 m/s² (`SimTuning.gravityScale`) | ja |
| `propwash:prop_wash` | Schalter | an | – | Physik | Prop-Wash und Nachlauf (`propWash`, `wakeField`) | ja |
| `propwash:ground_effect` | Schalter | an | – | Physik | Bodeneffekt | ja |
| `propwash:hardware_limits` | Schalter | an | – | Physik | Hitzedrosselung, Hitzeverschleiß, ESC-Überspannung (`thermalLimits`, `escOvervoltage`) | ja |
| `propwash:wind` | Stufe | 1 | 0–3 | Wind und Wetter | 0 aus, 1 mild (wie bisher), 2 windig, 3 Sturm; Faktor = Stufe auf Wind und Böen (`windScale`) | ja |
| `propwash:turbulence` | Schalter | an | – | Wind und Wetter | Böen und Prop-Wash-Rauschen (`turbulenceScale`) | ja |
| `propwash:battery_scale` | Zahl, % | 100 | 0–1000 | Akku | Akkulaufzeit: 200 = doppelt so lange, 0 = unendlich (`batteryScale`, `infiniteBattery`) | ja |
| `propwash:battery_sag` | Schalter | an | – | Akku | Spannungseinbruch mit der Ladung | ja |
| `propwash:crash_damage` | Schalter | an | – | Absturz und Schaden | Crash-, Hitze- und ESC-Verschleiß der Teile | nein |
| `propwash:crash_damage_scale` | Zahl, % | 100 | 25–300 | Absturz und Schaden | Stärke des Crash-Verschleißes | nein |
| `propwash:crash_tolerance` | Zahl, % | 100 | 50–400 | Absturz und Schaden | Aufprallgeschwindigkeit, ab der ein Absturz zählt (`crashSpeedScale`) | ja |
| `propwash:crash_disarm` | Schalter | an | – | Absturz und Schaden | Motoren nach hartem Aufprall aus | ja |
| `propwash:prop_strikes` | Schalter | an | – | Absturz und Schaden | Propellertreffer | ja |
| `propwash:infinite_range_analog` | Schalter | aus | – | Video und Funk | Drohnen mit analogem Video: Bild und Funk reißen nie ab | nein |
| `propwash:infinite_range_digital` | Schalter | aus | – | Video und Funk | dasselbe für digitales Video | nein |
| `propwash:max_video_channels` | Zahl | 0 | 0–64 | Video und Funk | höchstens so viele Sender gleichzeitig, 0 = unbegrenzt | nein |
| `propwash:signal_repeaters` | Zahl | 4 | 0–4 | Video und Funk | höchstens so viele Signal-Repeater hintereinander in einer Verbindung, 0 = Repeater aus; der Client setzt die Regel um (`docs/design/signal-repeater.md`) | nein |
| `propwash:drone_hits_entities` | Schalter | an | – | Spiel | Drohnen prallen an Mobs und Spielern ab und verletzen sie (Modul interact, `docs/design/drone-interaction.md`) | nein |
| `propwash:drones_break_blocks` | Schalter | aus | – | Spiel | schnelle Drohnen zerbrechen Glas, Glasscheiben und verzierte Töpfe (Block-Tag `propwash:drone_breakable`), wenn die Energie reicht, und fliegen langsamer weiter; Rechte wie beim Abbauen durch den Piloten (`docs/design/drone-break-blocks.md`) | nein |
| `propwash:break_impact_scale` | Zahl, % | 100 | 0–300, Schritt 10 | Spiel | wie stark eine Drohne den Durchbruch durch einen zerbrechlichen Block spürt: Tempoverlust an die mitgerissenen Scherben, Drehstoß, Scherben in den vorderen Props; 0 = nur die Bruchenergie („Kein Stoß“). Ob ein Block bricht und wann die Drohne dabei abstürzt, ändert sie nicht (`docs/design/drone-break-blocks.md` §3.2) | nein |
| `propwash:allow_3d_mode` | Schalter | an | – | Spiel | Piloten dürfen den 3D-Modus fliegen, wenn sie ihn freigeschaltet haben; aus lässt ihn niemanden einschalten, etwa auf Rennservern (`docs/design/mode-3d.md`). Wie das Flugmodell setzt der Client die Regel um: Der Zustand der Drohne enthält den 3D-Modus nicht, der Server kann ihn nicht prüfen, ein veränderter Client umgeht die Regel (ebenso jede Physik-Regel); der Regeltext verspricht deshalb nichts für Server ohne Aufsicht | nein |
| `propwash:part_compatibility` | Schalter | an | – | Spiel | „Teile müssen zusammenpassen“: aus = jedes Teil passt zu jedem, die Drohne fliegt mit den echten Daten ihrer Teile (`docs/design/part-freedom.md`) | nein** |
| `propwash:autonomous_rescue` | Schalter | an | – | Spiel | GPS Rescue ohne Pilot: steigt der Pilot im Heimflug aus, fliegt die Drohne allein weiter und landet am Homepoint; aus = Motoren aus wie bisher (`docs/design/autonomous-rescue.md`) | nein |
| `propwash:freeform_gate_max_size` | Zahl, Blöcke | 20 | 1–64 | Spiel | Größte Öffnung eines Freiform-Gates je Richtung (20 = höchstens 20 × 20 Blöcke); gilt beim Setzen und Neu-Abtasten, gesetzte Gates bleiben (`docs/design/freeform-gate.md`) | nein |
| `propwash:drones_generate_chunks` | Schalter | an | – | Spiel | Drohnen erzeugen neues Gelände; aus = Drohnen laden nur schon erzeugte Chunks (lückenlos), am Rand bremst ein Grenzzaun die Drohne 4 Blöcke davor ab, OSD „WELTRAND“ (`docs/design/world-edge.md`) | nein |
| `propwash:save_assisted_laps` | Schalter | aus | – | Spiel | Runden und Rennen mit Flughilfe werden in einer eigenen Wertung „Mit Flughilfe“ gespeichert und überall markiert, nie als Rekord (`docs/design/assisted-laps.md`) | nein |
| `propwash:drone_trails` | Schalter | aus | – | Spiel | Drohnen-Trails: dünne, verblassende Spur hinter jeder fliegenden Drohne in ihrer Farbe, auch im FPV-Bild; jeder Spieler kann sie im Tab „Darstellung“ für sich ausschalten (`docs/design/drone-trails.md`) | nein |
| `propwash:invulnerable_pilots` | Schalter | aus | – | Spiel | Unverwundbare Piloten: wer gerade eine Drohne steuert, nimmt keinen Schaden (für Events mit geschützten Piloten); Leere und `/kill` wirken immer; aus = der Körper bleibt ein normaler Spieler (`docs/design/pilot-body.md`) |

\* „Physik“: Weicht die Regel vom Standard ab, gilt die Physik als verändert (`PhysicsRules#modified`): Flüge zählen
nicht für Bestenlisten und Server-Rekorde, wie vorher bei veränderten persönlichen Optionen.

\*\* `part_compatibility` ändert keine Physik: Gültige Baupläne fliegen mit der Regel an und aus bitgleich und zählen
weiter. Nur Flüge mit einer Drohne, die es lediglich mit abgeschalteter Regel gibt, zählen nicht für Bestenlisten.

Mit allen Standardwerten rechnet das Flugmodell **bitgleich** wie 0.4.0 (`PhysicsRules.DEFAULT.toSimTuning()` ist Feld für
Feld `new SimTuning()`, JUnit `WorldRuleSpecsTest`, `BatteryScaleTest`; `tools/calibration.sh` mit `calibration.txt`,
`motion.txt` und `fall.txt` byte-gleich).

## 1a. Voreinstellungen (Realistisch, Training, Arcade)

Auftrag vom 05.10.2026, sinngemäß: ganz oben im Tab Welt-Regeln drei Voreinstellungen mit einem Klick; der Server
prüft, ändern dürfen nur Host und Operatoren; einzelne Regeln bleiben danach einstellbar, und der Tab zeigt, welche
Voreinstellung (falls eine) zu den aktuellen Werten passt. Jede Voreinstellung legt **jede** Katalog-Regel fest; was in
der Tabelle nicht steht, ist der Standardwert.

| Regel | Realistisch | Training | Arcade |
|---|---|---|---|
| `gravity_scale` | 100 % | 100 % | 100 % |
| `prop_wash` | an | an | **aus** |
| `ground_effect` | an | an | an |
| `hardware_limits` | an | **aus** | **aus** |
| `wind` | 1 mild | 1 mild | **0 aus** |
| `turbulence` | an | an | **aus** |
| `battery_scale` | 100 % | **0 = unendlich** | **0 = unendlich** |
| `battery_sag` | an | **aus** | **aus** |
| `crash_damage` | an | **aus** | **aus** |
| `crash_damage_scale` | 100 % | 100 % | 100 % |
| `crash_tolerance` | 100 % | 100 % | **300 %** |
| `crash_disarm` | an | an | **aus** |
| `prop_strikes` | an | **aus** | **aus** |
| `infinite_range_analog`, `infinite_range_digital` | aus | aus | **an** |
| `max_video_channels` | 0 = unbegrenzt | 0 | 0 |
| `signal_repeaters` | 4 | 4 | 4 |
| `drone_hits_entities` | an | an | **aus** |
| `drones_break_blocks` | aus | aus | aus |
| `break_impact_scale` | 100 % | 100 % | 100 % |
| `allow_3d_mode` | an | an | an |
| `part_compatibility` | an | an | **aus** |
| `autonomous_rescue` | an | an | an |
| `freeform_gate_max_size` | 20 | 20 | 20 |
| `drones_generate_chunks` | an | an | an |
| `save_assisted_laps` | aus | **an** | **an** |
| `drone_trails` | aus | **an** | **an** |
| `invulnerable_pilots` | aus | aus | aus |

Realistisch ist genau der Standard (Flüge zählen für Bestenlisten); Training und Arcade verändern die Physik
(`PhysicsRules#modified`), Flüge zählen dort nicht für die Wertung ohne Flughilfe, werden aber seit dem 08.10. in der
Wertung „Mit Flughilfe“ gespeichert (`save_assisted_laps` an). Eine neue Katalog-Regel steht in allen drei Voreinstellungen auf
ihrem Standard, bis sie in `RulePreset` eingetragen wird.

Bedienung: Der Klick schickt `WorldRulePresetC2S` und gilt **sofort** für alle (nicht erst beim Speichern); Entwürfe
für Katalog-Regeln im Tab verfallen dabei, weil die Voreinstellung sie ersetzt. Der Server prüft das Recht wie beim
Ändern einzelner Regeln, setzt alle Regeln, schreibt ins Log und schickt allen Spielern **eine** graue Chatzeile
(„X hat die Welt-Regeln auf die Voreinstellung „Training“ gestellt.“), nur wenn sich etwas geändert hat. Die Knöpfe
leuchten, wenn die angezeigten Werte (gespeichert plus Entwurf) genau zu einer Voreinstellung passen; darunter steht
„Entspricht der Voreinstellung Training“ oder „Eigene Regeln – entspricht keiner Voreinstellung“, mit Entwurf
„(noch nicht gespeichert)“. Ohne Recht fehlen die Knöpfe, die Zeile bleibt. Der Tooltip jedes Knopfs nennt Zweck und
alle Regeln, die vom Standard abweichen (aus den Daten erzeugt). Für Server ohne Oberfläche:
`/propwash rules preset <realistic|training|arcade>` (Rechtestufe 2); `/propwash rules` nennt die passende
Voreinstellung.

## 2. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Ids `propwash:<name>` (snake_case) statt `propwashGravityScale` | In 26.3 sind Gamerules Registereinträge mit Identifier wie `minecraft:fall_damage`; der Namensraum ist das Präfix, `/gamerule` zeigt `propwash:…` | camelCase-Namen im Namensraum `minecraft` – widerspricht 26.3 und könnte mit Vanilla kollidieren |
| Eigene Regeln über `PropwashRegistries.list(Registries.GAME_RULE)` mit dem öffentlichen `GameRule`-Konstruktor | gleich auf Fabric und NeoForge, keine Loader-API nötig, Typen BOOL/INT wie Vanilla (Befehl, Weltanlage, Speichern funktionieren) | Fabric `GameRuleBuilder` und NeoForge-Hilfen – zwei Wege für dasselbe |
| Kategorie `propwash:propwash` als Record ohne `GameRuleCategory.register` | `register` ist nicht thread-sicher (NeoForge lädt Mods parallel); die Reihenfolge braucht nur die Sortierung im Vanilla-Fenster | `RegisterGameRuleCategoryEvent` nur auf NeoForge |
| Eigene Synchronisation (`WorldRulesS2C`) an **alle** Spieler | Vanilla schickt Gamerules nur Operatoren auf Anfrage; die Physik rechnet aber jeder Client selbst | Vanilla-`ClientboundGameRuleValuesPacket` |
| Eigenes Änderungspaket (`WorldRuleEditC2S`) statt `ServerboundSetGameRulePacket` | Der Host im Einzelspieler soll auch ohne Cheats ändern dürfen (Vorgabe für die Welt-Regeln); Vanilla verlangt Rechtestufe 2. Der Server prüft Recht, Namensraum und Wertebereich | Vanilla-Paket – sperrt Einzelspieler ohne Cheats |
| Wer ändern darf: Host des integrierten Servers (Einzelspieler, LAN-Host) oder Rechtestufe 2 (Operator, im Einzelspieler mit Cheats) | wie `/gamerule`, plus Host | nur Operatoren |
| Änderungen aus dem Tab bekommen **alle** Spieler als graue Chatzeile („X hat die Welt-Regel „Schwerkraft“ auf 120 % gesetzt“) | Die Regeln ändern die Physik aller; niemand soll überrascht werden | nur Operatoren benachrichtigen (Vanilla) |
| Tab „Welt-Regeln“ ersetzt den Tab „Simulation“ (Order 800) | Nach dem Umzug bliebe „Simulation“ leer | beide Tabs |
| Tab listet **jede** Gamerule im Namensraum `propwash` oder in der Propwash-Kategorie aus dem Register | Regeln anderer Module erscheinen ohne Änderung am Tab; Schalter, Zahl (Regler bis 1000 Schritte, sonst Zahlenfeld) | feste Liste im Tab |
| Wind als Stufe 0–3, Faktor = Stufe auf das wetterabhängige Windmodell (mittlerer Wind und Böen) | einfach zu erklären, Stufe 1 = bisher (bitgleich); Sturm bei Gewitter ≈ 29 m/s in 10 m Höhe | eigene Grundwinde je Stufe unabhängig vom Wetter |
| Turbulenz bleibt ein eigener Schalter (Böen **und** Prop-Wash-Rauschen) | wirkt auch ohne Wind im eigenen Abwind | in die Windstufe falten |
| Akkulaufzeit skaliert den Verbrauch (`mAhUsed`, `packMah` ÷ Faktor) | Ladung, mAh im OSD, Akku-Item und Ladegerät bleiben stimmig; Faktor 1 ist exakt bitgleich | die Kapazität skalieren – müsste an jeder Umrechnung Ladung ↔ mAh mitgeführt werden |
| 0 % Akku = unendlich (wie der alte Schalter), 1–9 % erlaubt | Vorgabe: 0 steht für unendlich; sehr kleine Werte sind ein bewusster Spaß der Betreiber | Mindestwert 10 % |
| Schwerkraft 0–1000 % in 1-%-Schritten (bis 08.10.: 80–150 % in 5er-Schritten) | feines Einstellen für Server, die Drohnen schwerer oder leichter mögen; 0 % = schwerelos, die Regler rechnen mit mindestens 1 % als Bezug (`flight-feel-gravity.md` §10.6) | 80–150 % lassen; 5er-Schritte |
| Crash-Schaden als Schalter **plus** Schadensstärke 25–300 % | Besitzer will an/aus; die Stärke war bisher nur mit Kreativ-Brille einstellbar und gehört zur selben Sache | Stärke 0 = aus in einer Regel |
| `crash_damage` ersetzt die Server-Regel `partWear` (server.json) und den Schalter der Kreativ-Brille; `partWear = false` aus 0.4.0 wird einmal als `crash_damage = false` übernommen (Fix-Runde 0.5.0) | eine Stelle für alle; Hitze- und ESC-Verschleiß hängen wie bisher an derselben Regel; ein Server, der Verschleiß abgeschaltet hatte, soll nach dem Update nicht still wieder Crash-Schaden haben | `partWear` behalten; Wert nur ignorieren und im Changelog darauf hinweisen |
| Die Kreativ-Brille schaltet keine Physik mehr frei; Kreativmodus und Kreativ-Brille behalten nur ihre freie Funkreichweite und die eigenen Video-Einstellungen | Welt-Regeln gelten für alle | Freischaltung behalten |
| Unendliche Reichweite: Video **und** Funk ohne Grenze, Hindernisse egal (wie die Kreativ-Brille ohne eigene Reichweite); die Entfernungsgrenze des Servers (`server.json`) gilt weiter | „unendlich“ soll sich auch so anfühlen; Strahlen über 1000+ Blöcke zu verfolgen wäre teuer | nur die Entfernung ignorieren, Hindernisse weiter dämpfen |
| Kreativ-Brille mit eigener Reichweite behält ihre persönliche Reichweite | sie ist eine bewusste Übungseinstellung des Spielers | Regel überstimmt sie |
| Videokanäle = höchstens N gleichzeitige Sender (Drohnen mit Pilot und geladenem Akku, auch im Sichtflug), sonst kein Start („Kanaltafel“ am Rennplatz); wer fliegt, fliegt weiter; 0 = unbegrenzt (Standard, wie bisher) | ohne Störungsmodell zwischen Kanälen hätte eine reine Kanal-Obergrenze keine Wirkung | geteilte Kanäle ohne Folgen |
| Veränderte Physik-Regeln zählen wie bisher nicht für Bestenlisten; der Server prüft das zusätzlich selbst (`RaceServer#modifiedPhysics`) | Rekorde bleiben über Regeländerungen hinweg vergleichbar | Rekorde je Regelsatz (später möglich) |
| Bestehende Server-Config-Regeln (`allowSpectating`, `worldCourses`, Rechte, Chunk-Budget …) bleiben in `server.json` | sie sind Betreiber-Technik bzw. Weltgenerierung, nicht Teil dieses Auftrags | alles in Gamerules |
| Keine API-Erweiterung für die Welt-Regeln | Addons brauchen die Werte (noch) nicht; die Parts-API steigt in 0.5.0 nur wegen neuer Flugschul-Szenen auf 1.5.0 | Lesezugriff in `PropwashApi` |
| Voreinstellungen legen alle Regeln des Katalogs fest (28), auch Spielregeln wie 3D-Modus, GPS Rescue ohne Pilot und Videokanäle | Vorgabe „alle Gamerules“; nur so ist „passt zu Training“ eindeutig und der Export vollständig. Server-Organisation (Videokanäle, 3D erlaubt, GPS Rescue ohne Pilot, Größe der Freiform-Gates) und Schwerkraft bleiben in allen dreien auf Standard, weil sie keine Schwierigkeit sind | Voreinstellungen nur für Schwierigkeits-Regeln, Rest unverändert lassen – dann passt eine Welt zu mehreren Voreinstellungen zugleich |
| Training: Wind **mild** (Standard), Turbulenz und Prop-Wash an, Crash-Toleranz 100 %, Absturz entschärft an | Training nimmt die Folgen (Schaden, Propellertreffer, Akku, Spannungseinbruch, Hitzedrosselung), nicht Wind und Luft; Steuerung und Luft fliegen sich wie unter Realistisch, nur die Leistung lässt über den Flug nicht nach (kein Spannungseinbruch, keine Hitzedrosselung). Der Text der Voreinstellung sagt das so („die Drohne behält die volle Leistung“), nicht mehr „das Flugverhalten bleibt wie bei Realistisch“ | Wind aus – Schweben wäre unecht leicht, der Wechsel zu Realistisch ein Bruch |
| Training: Hardware-Grenzen aus | mit unendlichem Akku fliegt man beliebig lange; Hitzedrosselung wäre dann eine Strafe ohne Lernwert | an lassen |
| Training: Reichweite bleibt begrenzt, Drohnen prallen weiter an Lebewesen ab | Reichweite und Hindernisse gehören zum Fliegenlernen (Flugschule „Absturz, Reichweite, Signal“) | unendliche Reichweite schon im Training |
| Arcade: Crash-Toleranz 300 % und Absturz entschärft aus | „großzügig“: erst dreimal so harte Aufschläge zählen als Absturz, und auch dann laufen die Motoren weiter – Arcade soll nicht unterbrechen | 400 % (Maximum, Drohne prallt dann fast wie ein Gummiball) oder entschärft an lassen |
| Arcade: Wind, Turbulenz und Prop-Wash aus, unendliche Reichweite, Drohnen fliegen durch Lebewesen, Teile frei | ruhige Luft, nichts reißt ab, nichts bremst; Teile-Freiheit ist Vorgabe | Schwerkraft senken – ändert das Flugverhalten zu stark |
| Klick gilt sofort über ein eigenes Paket `propwash:world_rule_preset` (Name statt Werte) | „ein Klick“; der Server kennt die Werte selbst und meldet die Änderung als **eine** Chatzeile statt bis zu 14 | Voreinstellung nur in den Entwurf schreiben (zwei Klicks, „Speichern“ schließt den Konfigurator) oder als `WorldRuleEditC2S` mit allen Werten (Chat-Flut) |
| Keine Rückfrage vor dem Anwenden | Vorgabe ein Klick; alle sehen die Chatzeile, „Realistisch“ stellt den Standard wieder her | Bestätigungsfenster |
| „Passt zu“ vergleicht die angezeigten Werte (gespeichert plus Entwurf) | die Knöpfe zeigen sofort „eigene Regeln“, sobald man einen Regler bewegt; mit Entwurf steht „(noch nicht gespeichert)“ dabei | nur gespeicherte Werte – Anzeige und Regler widersprächen sich |
| Netzprotokoll bleibt 11 | 0.5.0 ist unveröffentlicht und zählt schon einmal auf 11 hoch; das neue Paket kommt in derselben Version | 12 |
| Voreinstellungen im Export `gamerules.json` (`presets`, Format bleibt 1) | Docs-Seite kann die Tabelle aus dem Code erzeugen; der Schlüssel ist neu, alte Leser stören sich nicht | eigene Datei |
| `drones_generate_chunks` (08.10.): Standard an, in allen Voreinstellungen an; Details in `docs/design/world-edge.md` | an ist das bisherige Verhalten; Server-Organisation wie Videokanäle | Arcade ohne Erzeugung |
| `save_assisted_laps` (08.10.): Standard aus, Training und Arcade an; Details in `docs/design/assisted-laps.md` | dort zählt wegen veränderter Physik sonst keine Runde; Realistisch bleibt genau Standard | in allen Voreinstellungen Standard |
| `drone_trails` (08.10.): Standard aus, Training und Arcade an; Details in `docs/design/drone-trails.md` | rein optisch; die Übungs- und Spaß-Voreinstellungen zeigen die eigene Linie und die anderen Piloten, Realistisch bleibt genau Standard | in allen Voreinstellungen Standard |
| `invulnerable_pilots` (09.10.): Standard aus, in allen Voreinstellungen aus; Details in `docs/design/pilot-body.md` | Schutz der Piloten ist eine Entscheidung des Servers für Events, keine Flug-Voreinstellung; Realismus ist der Standard | Training an (würde Übungsflüge im Überlebensmodus unrealistisch machen) |
| `drones_break_blocks` (08.10.): Standard aus, in allen Voreinstellungen aus; Details in `docs/design/drone-break-blocks.md` | verändert die Welt dauerhaft, keine Physik-Regel; eine Voreinstellung soll keine Blöcke freigeben | Arcade an |
| `break_impact_scale` (09.10.): 100 % in allen Voreinstellungen, Gruppe Spiel direkt hinter `drones_break_blocks`, zählt nicht als geänderte Physik | wirkt nur zusammen mit der Bruch-Regel und nur im Augenblick des Durchbruchs; Bestenlisten hängen an den Flug-Regeln | in die Gruppe Absturz; als Physik-Regel werten; Arcade 0 % (dort ist die Bruch-Regel ohnehin aus) |

## 3. Technik

| Datei | Inhalt |
|---|---|
| `server/rules/RuleSpec` | reines Java: Name, Typ (BOOL/INT), Standard, Bereich, Gruppe, Anzeige (TOGGLE/PERCENT/LEVELS/NUMBER), Schrittweite, Sonderwert 0; Sprachschlüssel `gamerule.propwash.<name>`, `.description`, `.value.<n>`; `parse`/`serialize` wie `/gamerule` |
| `server/rules/WorldRuleSpecs` | Katalog aller Propwash-Gamerules in Anzeige-Reihenfolge (`all()`, `byName`) |
| `server/rules/RuleValues` | unveränderliche Werte je Name; ungültig oder fehlend → Standard |
| `server/rules/PhysicsRules` | Physik-Regeln → `SimTuning`; `DEFAULT`, `modified()` |
| `server/rules/RuleEffects` | Crash-Schaden und -Stärke, unendliche Reichweite je Videosystem (CREATIVE wie analog), Kanalgrenze |
| `server/WorldRules` | Registrierung (`ServerModule.init`), `values/get/on/physics/crashDamage/infiniteRange(server …)`, `set`, `mayEdit`, `propwashRules()` (Register, Namensraum oder Kategorie), Sync jeden Tick an jeden Spieler bei Änderung von Werten oder Recht, Paket-Handler |
| `server/WorldRulesS2C`, `WorldRuleEditC2S` | `propwash:world_rules` (darf ändern + Liste Id/Wert als Text, höchstens 256), `propwash:world_rule_edit` (höchstens 64) |
| `server/WorldRuleText` | Name, Wert (an/aus, Stufenname, „120 %“, Sonderwert), Bereich |
| `server/rules/RulePreset` | reines Java: `REALISTIC`, `TRAINING`, `ARCADE` (Id klein geschrieben), `value(spec)` (nicht genannt = Standard), `ruleValues()`, `differences()` (vom Standard abweichend, Katalog-Reihenfolge), `matches(values)`, `changes(values)`, `match(values)`, `byId(id)`; Sprachschlüssel `propwash.server.world_rules.preset.<id>` und `.description` |
| `server/WorldRulePresetC2S` | `propwash:world_rule_preset` (Name, höchstens 32 Zeichen); `WorldRules#preset` prüft Recht und Namen, `applyPreset(server, preset[, wer, name])` setzt alle Regeln, meldet einmal im Chat und synchronisiert, `matchingPreset(server)`. Abgelehnte Änderungen und Voreinstellungen (ohne Recht) melden Logzeile, rote Chatzeile und Neusendung der Regeln höchstens zweimal je 5 s und Spieler (`ImportRateLimit` 2 je 100 Ticks), weitere werden still verworfen – ein veränderter Client kann so das Log nicht fluten |
| `server/client/ClientWorldRules` | Werte am Client (`values`, `physics`, `editable`, `version`), bis zum ersten Paket Standardwerte, beim Trennen geleert |
| `server/client/WorldRulesTab` | Tab „Welt-Regeln“ (`world_rules`, Order 800, `Storage.MIXED`, Fußzeile „Gilt für alle in dieser Welt“), oben Abschnitt „Voreinstellungen“ (drei Knöpfe, `presetButton`, `applyPreset`, `matchingPreset` über `displayedValues`, Zeile „Entspricht …“), Entwurf bis Speichern, Rechtsklick = Standard, Tooltip mit Beschreibung, Standard, Bereich und Befehl; ohne Recht alles gesperrt mit Hinweis, „Standard“-Knopf ausgeblendet |

Lesende Stellen: `PilotSession` (Tuning beim Start und bei jeder neuen Version, `physicsModified`, Funkqualität,
Aufprall- und Hitzeverschleiß senden), `DroneWear` (Server: Schalter und Stärke aus der Regel, die Stärke im
`ImpactC2S` ist nur noch Information), `DronePiloting.freeRange(spieler, drohne)` (Start-, Halte- und Abbruchreichweite),
`VideoState#range` (unendlich für das Sendersystem), `ServerRules.pilotDenialReason` (Kanäle belegt →
`propwash.server.deny.channels_full`, gezählt mit `SpectateServer.transmittersExcept`; Teile passen nicht bei Regel an →
`propwash.server.deny.parts_incompatible`), `RaceServer`/`ClientRaceTracker` (veränderte Physik). Wo
`part_compatibility` gelesen wird (Werkbank, Import, Akku-Tausch, Wartung, API, scharf Schalten), steht in
`docs/design/part-freedom.md` §7.

## 4. Eine neue Welt-Regel anlegen

1. Eine Zeile in `server/rules/WorldRuleSpecs`, z. B.
   `public static final RuleSpec DRONE_HITS_ENTITIES = add(RuleSpec.bool("drone_hits_entities", true, Group.GAMEPLAY));`
2. Sprachschlüssel `gamerule.propwash.drone_hits_entities` und `.description` (bei Stufen und Sonderwert 0 auch
   `.value.<n>`) in `de_de` und `en_us` des eigenen Moduls.
3. Lesen: Server `WorldRules.on(server, WorldRuleSpecs.DRONE_HITS_ENTITIES)`, Client
   `ClientWorldRules.INSTANCE.on(WorldRuleSpecs.DRONE_HITS_ENTITIES)`.

Registrierung, Synchronisation, Tab, `/gamerule`, `/propwash rules` und Export folgen von selbst. Eine Gamerule, die ein
Modul direkt im Namensraum `propwash` registriert, zeigt der Tab ebenfalls (Schalter oder Zahl), der Selbsttest
„server: Welt-Regeln registriert, im Katalog und am Client“ verlangt dann aber den Katalog-Eintrag, damit der Export für
die Website vollständig ist. Zählt die neue Regel zur Physik, gehört sie zusätzlich in `PhysicsRules`.

## 5. Export für die Website

`tools/export_rules.sh [ziel]` (JUnit `WorldRulesExportTest`) schreibt `build/web-export/gamerules.json`:
Format 1, Mod-Version, Befehl, Kategorie und Gruppen (de/en) und je Regel Id, Name, Gruppe, Typ (`boolean`/`integer`),
Anzeige, Standard, Minimum, Maximum, Schrittweite, Einheit, `changes_physics`, Titel und Beschreibung de/en,
Wertnamen und ein Beispielbefehl; dazu `presets` mit Id, Titel und Beschreibung de/en, `changes_physics`, allen
Werten (`values`, Schalter als `true`/`false`), `differs_from_default` und dem Befehl. Der Test schlägt fehl, wenn ein
Text in einer Sprache fehlt. Die Docs-Seite auf
propwashfpv.com/docs liest diese Datei.

## 6. Migration

- `config/propwash/drone.json`: Die Felder `propWash`, `groundEffect`, `batterySag`, `infiniteBattery`, `crashDisarm`,
  `propStrikes`, `wind`, `turbulence`, `hardwareLimits`, `crashTolerance`, `crashDamage`, `crashDamageStrength`, `gravity`
  werden beim Laden ignoriert und beim nächsten Speichern entfernt; nur `stateSendHz` bleibt.
- `config/propwash/server.json`: Steht dort `"partWear": false` (aus 0.4.0), setzt der erste Serverstart mit 0.5.0 einmal
  `propwash:crash_damage` auf `false` (Logzeile „partWear = false aus 0.4.0 als Welt-Regel … übernommen“;
  `server.LegacyServerSettings`, `ServerModule#carryOverPartWear`). Danach fehlt der Schlüssel in der Datei, die Regel
  gilt wie jede andere. Auf einem dedizierten Server ist das die Welt des Servers; im Einzelspieler die erste Welt, die
  danach startet (andere Welten behalten den Standard). `"partWear": true` oder ein fehlender Schlüssel ändern nichts.
- Bestehende Welten bekommen beim Laden alle Regeln mit Standardwerten.
- Netzwerkprotokoll 11 in Propwash 0.5.0 (neue Pakete, `ServerRulesS2C` ohne `partWear`).

## 7. Tests

- JUnit: `WorldRuleSpecsTest` (Katalog, Grenzen, Parsen, Standard = bitgleiches Tuning, welche Regeln Physik sind,
  Wirkungen), `BatteryScaleTest` (200 % halbiert den Verbrauch, 50 % verdoppelt ihn, 0 = unendlich, Standard bitgleich),
  `WorldRulesExportTest` (Texte de/en, echte Umlaute, Export), `WorldRulesPayloadTest` (Pakete, Grenzen, Textwerte),
  angepasst `RealismTogglesTest`, `GravityScaleTest`, `CameraJitterTest`.
- Selbsttest (beide Loader): „server: Welt-Regeln registriert, im Katalog und am Client“, „… per /gamerule erreicht den
  Client“ (Wind 3 wirkt auf die Physik, 999 % abgelehnt), „server: Tab Welt-Regeln ändert eine Regel über den Server“
  (Akku 200 %, Spannungseinbruch aus über Tab und Speichern), „… nimmt nur gültige Propwash-Regeln an“ (`minecraft:pvp`,
  999 %, Text als Zahl, unbekannte Regel), „… ohne Recht nur lesbar“, „… Server lehnt Änderungen eines Spielers ohne
  Recht ab“ (`WorldRulesRights`: ein zweiter, echter `ServerPlayer` ohne Operator-Rechte schickt sein Paket durch
  `WorldRules#edit`, der Server lehnt ab und die Regel bleibt; danach 40 weitere Änderungen und Voreinstellungen
  hintereinander, alle abgelehnt), „… Videokanäle begrenzt die Sender“
  (`tryStart` → `DENIED`), „… unendliche Reichweite je Videosystem“ (im Überleben, analog/digital getrennt),
  „… Crash-Schaden aus verhindert Verschleiß“ (echtes `ImpactC2S`, aus = neu, an mit 300 % = abgenutzt) und
  „drone: Welt-Regel Schwerkraft wirkt im Flug“ (130 %, Schwebegas steigt, Server kennt veränderte Physik); die drei
  Schritte „teile-freiheit: …“ für `part_compatibility` (`docs/design/part-freedom.md` §9).
- JUnit `RulePresetTest` (Realistisch = Standard bitgleich, Training ohne Folgen bei gleichem Flugverhalten, Arcade ⊇
  Training, gültige Werte für jede Regel, eindeutiger Abgleich, eine geänderte Regel = eigene Regeln, Texte de/en),
  `WorldRulesPayloadTest` (Paket mit Namensgrenze), `WorldRulesExportTest` (Voreinstellungen im Export).
- Selbsttest „server: Welt-Regel-Voreinstellungen im Tab setzen alle Regeln über den Server“ (Entwurf Wind 3 → Klick
  Training verwirft ihn, Training und Arcade kommen über den Server an, Wind 2 danach = eigene Regeln, Realistisch zurück),
  „… per /propwash rules preset“ (Arcade, unbekannter Name ändert nichts, Realistisch); „… ohne Recht nur lesbar“ ohne
  Knöpfe; `WorldRulesRights` lehnt die Voreinstellung eines Gasts ab und übernimmt sie vom Operator (auch im
  Server-Rauchtest).
- Server-Rauchtest (dedizierter Server, beide Loader): derselbe Gast ohne Recht wird abgelehnt; als Operator
  (`PlayerList#op`, Rechte wie bei `/gamerule`) übernimmt der Server seine Änderung, danach wieder entzogen und
  zurückgesetzt.
- Welt-Regeln vom 08.10.: JUnit und Selbsttests „weltrand: …“, „flughilfe: …“ und „blockbruch: …“ in
  `docs/design/world-edge.md` §6, `docs/design/assisted-laps.md` §5 und `docs/design/drone-break-blocks.md` §6;
  Bildschirmfotos `configurator_world_rules_drones_generate_chunks`, `configurator_world_rules_save_assisted_laps_de`
  und `configurator_world_rules_drones_break_blocks` (en/de); Drohnen-Trails: JUnit und Selbsttests „trails: …“ in
  `docs/design/drone-trails.md` §4, Bildschirmfoto `configurator_world_rules_drone_trails`.
- Bildschirmfotos (Fabric): `configurator_world_rules`, `configurator_world_rules_gravity`, `_wind`, `_readonly` und
  deutsch `_gravity_de`, `_video_de`, `_readonly_de`; `configurator_world_rules_part_compatibility` (Regel aus mit
  Tooltip); `configurator_world_rules_presets` (Training aktiv, Tooltip Arcade), deutsch `_presets_de` (Arcade) und
  `_presets_training_de`.
