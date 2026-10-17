# Änderungen für Addon-Autoren

Was sich je Propwash-Release für Addons, Datenformate, Netzprotokoll und Werkzeuge ändert. Die Kurzfassung für Spieler
steht in [`CHANGELOG.md`](../../CHANGELOG.md), die technischen Einzelheiten der Spielerseite in
[`docs/RELEASE-NOTES.md`](../RELEASE-NOTES.md). Verbindlich bleiben der Vertrag
[`docs/design/parts-api.md`](../design/parts-api.md) (Versionsgeschichte §3.1 und §3.4), die JSON-Referenz
[`docs/design/parts-api-data.md`](../design/parts-api-data.md) und das Handbuch [`addon-guide.md`](addon-guide.md).
Wann welche Nummer steigt, regelt [`docs/RELEASING.md`](../RELEASING.md).

## Übersicht

| Propwash | Parts-API | Netzprotokoll | Für Addons |
|---|---|---|---|
| 0.5.2 | 1.6.0 | 16 | Halterung vorne, Zubehör mit Verhalten, Inventar und Aktionen 1–4, Stöße und Kräfte im Flugmodell, Events für Treffer und Schaden, Drohnen-Abfragen, Flughilfen, Gimbal-Kamera, HUD-Elemente; Leistungsstufen des Videosenders (`vtx_levels_mw`); Homepoint im NBT, Zonen, Block-Tags für Ranken, Bando-Karte, wasserdichte Drohnen, Gründe der Flughilfe im Detail |
| 0.5.1 | 1.5.0 | 15 | nichts Neues für Addons; Drohnen tragen Entity-Tags für Server-Admins |
| 0.5.0 | 1.5.0 | 15 | fünf Szenen-Konstanten, Umkehrschub der Props, Welt-Regeln, Schadensart, Bando-Beute, Beitritts-Übersicht aus den Metadaten, Addons nur beim Spieler inaktiv statt Trennung (`isAddonActive`), API unter MIT, Konfigurator-Tabs scrollen von selbst (`ConfigTab.reportBottom`, `scrollsItself`, `ConfigTabHost.contentShift`) |
| 0.4.0 | 1.4.0 | 8 | erste öffentliche API |
| 0.3.2 | – | 6 | keine API |
| 0.3.1 | – | 6 | keine API |
| 0.3.0 | – | 6 | keine API |
| 0.2.1 | – | 5 | keine API |
| 0.2.0 | – | 5 | keine API |
| 0.1.0 | – | 2 | keine API |

Client und Server mit verschiedener Protokollnummer trennt schon der Handshake mit einer klaren Meldung, die beide
Versionen nennt.

---

## 0.5.2 — Parts-API 1.6.0, Netzprotokoll 16

Bausteine für die beiden offiziellen Add-ons nach 0.5.2 („Propwash: Mob Defense“ und eine Kino-Drohne im Stil von DJI),
dazu Leistungsstufen des Videosenders. Vertrag [`docs/design/addon-api-1.6.md`](../design/addon-api-1.6.md), Handbuch
[`addon-guide.md`](addon-guide.md) Abschnitt 20, Entwürfe [`addon-mob-defense.md`](../design/addon-mob-defense.md) und
[`addon-cinematic.md`](../design/addon-cinematic.md). Alles ist additiv: Addons für 1.0–1.5 laufen unverändert,
*Just More Parts* braucht keine Änderung (1.1.1 übernimmt nur die neue Entladekurve). Abfrage:
`PropwashApi.isCompatible("1.6")`.

### Parts-API 1.6.0
- **Halterung vorne**: `AccessorySlot.FRONT` (JSON `"slot": "front"`, Rahmen `"accessory_mounts": [..., "front"]`,
  Anbaupunkt `accessory_anchors.front`). Zubehör vorne hängt unter der Nase und ragt nach vorn. Alle eingebauten Rahmen
  außer dem Whoop haben sie; die Werkbank hat einen fünfzehnten Slot (`DroneWorkbenchMenu.PART_SLOTS` = 15). Wer
  `AccessorySlot.values()` durchläuft oder per `switch` unterscheidet, bekommt einen Wert mehr.
- **Zubehör-Verhalten** (`api.accessory`): `PropwashApi.accessories()` → `AccessoryRegistry` mit `register(id,
  AccessoryBehavior)`, `behavior`, `accessoryAt`, `contents` (Entity und Item), `inventory` (Server), `itemGrams`.
  `AccessoryBehavior` (alles Default-Methoden): `slots()` 0–9, `accepts`, `maxStackSize`, `payloadGrams`,
  `onAction(AccessoryAction)`, `tick(AccessoryContext)`; `AccessoryContext`, `AccessoryAction`, `AccessoryInventory`,
  `MountPose`. Inventare liegen synchronisiert an der Drohne und als Komponente `propwash:accessory_contents` am
  Drohnen-Item; die Beladung wiegt im Flugmodell mit; Fenster „Zubehör“ in der Wartung.
- **Zubehör-Aktionen 1–4**: neue Schalterfunktionen und Tasten (Standard Ziffernblock 9, 0, Enter und +), C2S
  `propwash:accessory_action` mit Prüfung von Pilot, Epoche und Rate am Server.
- **Drohnen** (`api.drone`): `PropwashApi.drones()` → `DroneAccess` (`isDrone`, `view` → `DroneView`, `dronesNear`,
  `nearestDrone`, `physics` → `DronePhysics` mit `impulse`/`push`, `obstacleDistance`, `scan` → `ObstacleScan`, `aim`).
  Stöße gehen über S2C `propwash:drone_push` an den Piloten.
- **Flug** (`api.flight`, reines Java): `PropwashApi.flight()` → `FlightExtensions` (`registerAssist`, `registerForces`),
  `FlightAssist`, `FlightState`, `AssistOutput`, `ForceProvider`, `ForceSink`, `DroneProfile`, `StabilizationMode`,
  `SensorDirection`, die Helfer `GimbalModel` und `Intercept`.
- **Events**: `PropwashEvents.DRONE_HIT_ENTITY` (`EntityHit`, Schaden und Rückstoß änderbar), `DRONE_DAMAGED`
  (`DroneDamage`, Menge änderbar, abbrechbar), `ACCESSORY_ACTION`.
- **Client**: `PropwashClientApi.registerHudElement(id, HudElement)` (`api.client.hud`: `HudElement`, `HudContext`,
  `FlightTelemetry`), `registerGimbal(id, GimbalProvider)` (`api.client.camera`: `GimbalProvider`, `GimbalContext`),
  `input()` → `ClientInput`.
- Videoteile geben ihre Sendeleistungs-Stufen an: `VideoDefinition#vtxLevelsMw()` (`List<Integer>`, aufsteigend,
  1–10000 mW, höchstens `VideoDefinition.MAX_VTX_LEVELS` = 8, muss `vtx_power_mw` enthalten), Builder
  `vtxLevels(int...)`, im Teile-JSON `"vtx_levels_mw": [25, 200, 400, 800]`. Die bisherigen Konstruktoren ohne das Feld
  bleiben. Ohne Liste leitet Propwash die Stufen aus `vtx_power_mw` ab (analog bis 400 mW 25/100/200/400, darüber
  25/200/400/800/1600/2000, digital 25/100/400/700/1200, jeweils bis zur Nennleistung).
- **Verhaltensänderung:** `vtx_power_mw` war bis 1.5 reine Anzeige und ist jetzt die Nennleistung: die Stufe, bei der
  `range_blocks` und `power_w` gelten (0 = 200 mW). Eine Drohne ohne eigene Einstellung sendet mit *Auto* (analog wie
  digital, Module mit mehr als einer Stufe); Auto regelt zwischen der niedrigsten und der höchsten Stufe. Bei fester
  Nennleistung ändert sich für bestehende Teile nichts; höhere Stufen heben die Link-Reserve um 10·log10(P/P_Nenn) dB,
  kosten Strom (analog 4,3 W, digital 2,5 W je W Sendeleistung) und Wärme. Teile, deren `vtx_power_mw` nicht ihre
  höchste Stufe ist, geben `vtx_levels_mw` an. Abfrage: `PropwashApi.isCompatible("1.6")`.
- *Propwash: Just More Parts* braucht keine Änderung: seine Videoteile nennen ihre Höchstleistung als `vtx_power_mw` und
  bekommen daraus Stufen bis dorthin (z. B. `analog_race_800` 25–800 mW, `analog_whoop_25mw` fest 25 mW).

### Netzprotokoll 16
0.5.0 und 0.5.1 (Protokoll 15) trennt der Handshake beim Beitreten mit klarer Meldung; Client und Server brauchen beide
0.5.2. Gründe:
- Add-on-API: Payloads `propwash:accessory_action` (C2S) und `propwash:drone_push` (S2C), Entity-Daten-Serializer und
  Item-Komponente `propwash:accessory_contents`, Halterung `front` im Bauplan.
- Videosender: Nennleistung und Stufen in den Bauteil-Definitionen (geänderter Abgleich der Bauteil-Liste), neue
  Tuning-Schlüssel `vtx.powerMw`, `vtx.powerAuto`, `vtx.lowPowerDisarm` (Gruppe `TuneGroup.VTX`,
  [`docs/design/vtx-power.md`](../design/vtx-power.md)).
- Homepoint: `propwash:home_point_set` (Pilot → Server: Entity, Zähler, x/y/z, Herkunft) und `propwash:home_point`
  (Server → Pilot: Entity, Quittung, „per `/data` geändert“, Punkt oder keiner).
- Ranken und Glocken: C2S `propwash:drone_snag_wear` (Prop-Verschleiß beim Verfangen) und `propwash:drone_bell`.
- Zonen: `propwash:zones` (S2C, Zonen der Dimension und Recht) und `propwash:zone_edit` (C2S, Speichern/Löschen).
- Flughilfe: `propwash:race_report` und die Zeilen von `propwash:race_leaderboard` tragen die Gründe im Detail (Grund,
  Gegenstand, Wert, Bezug – z. B. Welt-Regel `gravity_scale` 130 statt 100,
  [`docs/design/assisted-laps.md`](../design/assisted-laps.md) §6), je Bericht und je Zeile höchstens 16; neuer Grund
  `zone_physics` (Bit 7).
- Register-Einträge, die die Loader abgleichen: Items `propwash:bando_map` und `propwash:waterproof_spray`, Kartensymbol
  `propwash:bando` (`minecraft:map_decoration_type`), Data Component `propwash:waterproof_coating`; synchronisiertes
  Entity-Feld der Beschichtung.

### Race-Preset: True-X-Rahmen und neues eingebautes Prop
- Neues eingebautes Bauteil `propwash:p51_29_3b` (5,1″ Durchmesser, 2,9″ Steigung, 3 Blätter, 4,4 g, `ct` 0,122,
  `cp` 0,065). Es steht als **letztes** eingebautes Prop im Register (`PropType.values()`, `parts().all(PROP)`); die
  Reihenfolge der bisherigen Teile bleibt. Tabellen, die eingebaute Teile nummerieren (Sharecodes), hängen es an.
  Addons, die auch mit Propwash 0.4.0 laufen sollen, dürfen es nicht fest in Presets nennen – dort fehlt es.
- `propwash:race_5` meldet `shape: true_x` statt `stretched_x`, und sein Bild ist True-X (Motoren ±76,0 mm). Ohne
  `arms` flog der Rahmen schon immer so; Flugdaten, Kamera und Anbaupunkte ändern sich nicht. „Props im Kamerabild“
  nennt für Bauten auf `race_5` weniger als vorher (Preset 6,4 % statt 11,0 %).
- Das eingebaute Preset `RACER` nutzt den neuen Prop (Topspeed 147 statt 168 km/h). Diese Änderung allein berührt
  weder `API_VERSION` noch Netzprotokoll. Einzelheiten: [`docs/design/realism/motors-props.md`](../design/realism/motors-props.md) §12.

### Flughilfen, Zubehör-Fenster und Aktionen (Verhalten)
- Eine Add-on-Flughilfe (`FlightExtensions#registerAssist`), die gerade etwas ausgibt (Knüppel, Modus, Höhe, Position,
  Tempo, Gieren), macht die Runde zur Runde mit Flughilfe: Grund `other` mit der Id der Flughilfe als Gegenstand
  (Zeichen `[a-z0-9_:./-]`, höchstens 48). Hält sie Höhe oder Position, ohne dass der Pilot selbst Halten geschaltet
  hat, steht nur sie in der Wertung, nicht mehr „Höhe halten war an“. Kräfte (`registerForces`) zählen nicht.
- Das Fenster „Zubehör“ liest vor jedem Zugriff den Stand der Drohne neu und schreibt nur selbst geänderte Plätze
  zurück; Schreiben über `AccessoryInventory` am Server und mehrere offene Fenster vertragen sich damit. Shulkerkisten
  (`Item#canFitInsideContainerItems`) und Drohnen-Items mit eigenem Zubehör-Inhalt nimmt es nie an, auch wenn
  `accepts` die Vorgabe `true` liefert.
- Ein Loslassen (`pressed() == false`) erreicht `onAction` und `ACCESSORY_ACTION` nur, wenn die Aktion gerade gehalten
  ist; doppelte Loslassen-Pakete fallen weg.

### Daten, Welt und Befehle
- Drohnen speichern ihren GPS-Rescue-Homepoint unter `HomePoint`: `{x, y, z, dimension, game_time, source}` mit
  `source` = `arm`, `manual`, `pilot` oder `command` ([`docs/design/home-point.md`](../design/home-point.md)).
  `/data merge`, `modify` und `remove` prüfen ihn vor dem Ändern (andere Dimension, außerhalb der Weltgrenze, mehr als
  64 Blöcke unter bzw. über der Welt oder ein kaputter Eintrag scheitern mit Meldung); `game_time` und `source` setzt
  Propwash selbst. Beschichtete Drohnen speichern `Waterproof`, Zubehör-Inhalte `AccessoryContents`.
- Zonen: Weltdaten `data/propwash/zones.dat`, Befehl `/propwash zone` ([`docs/design/zones.md`](../design/zones.md)).
  Bestenlisten speichern additiv `assist_details` an Zeiten mit Flughilfe.
- Block-Tags `propwash:drone_snag_web` (fängt und hält, Spinnweben), `propwash:drone_snag_string` (ruckt an den Props,
  Stolperdraht) und `propwash:drone_snag_foliage` (bremst leicht: Ranken, Leuchtflechte, Wurzeln, Moos); gewirkt wird
  mit der Umrissform des Blocks ([`docs/design/drone-snags.md`](../design/drone-snags.md)).
- Bando-Karte: Item-Modifikator `propwash:bando_map` (Entdeckerkarte zum nächsten unerkundeten Bando, verwirft die Karte
  ohne Ziel; die Beutetabellen `propwash:chests/bando` und `propwash:inject/village_cartographer` verweisen darauf),
  Handel `propwash:cartographer/3/emerald_and_compass_bando_map` im Vanilla-Tag `#minecraft:cartographer/level_3`,
  Bando-Karte im Tag `#minecraft:clonable_maps`. Eigene Strukturen eines Addons kartiert ein Datenpaket mit
  `minecraft:exploration_map` und `"decoration": "propwash:bando"` ([`docs/design/bandos.md`](../design/bandos.md)).
- Wasserdichte Drohnen ([`docs/design/waterproof-turtle.md`](../design/waterproof-turtle.md)): Item
  `propwash:waterproof_spray` (Haltbarkeit 100, 10 je Drohne), Data Component `propwash:waterproof_coating` (Signatur
  der Elektronik aus den Teile-IDs von Rahmen, Stack, Motor, Video und Zubehör; Teile aus Addons zählen automatisch),
  Sonderrezept `propwash:waterproof_coating`, Welt-Regel `propwash:waterproof_drones` (Standard aus, „Arcade“ an).
- Die Welt-Regel `propwash:crash_disarm` heißt im Spiel „Nach Absturz abschalten“ / „Disarm on crash“; ID und Wert
  bleiben.

### Schalterfunktionen, Tasten und OSD
- Neue Schalterfunktionen vor den eigenen Befehlen: `VTX_POWER` (Taste `key.propwash.vtx_power`, F10), `TURTLE`
  (`switches.turtle`, Taste `key.propwash.drone_turtle`, Ziffernblock 8; die Blackbox setzt dafür Betaflights
  `FLIP_OVER_AFTER_CRASH`) und `ACCESSORY_1`–`ACCESSORY_4` (Ziffernblock 9, 0, Enter und +). `COMMAND_1…8` rücken damit
  sechs Ordnungszahlen weiter; gespeichert wird weiter nach Namen.
- Neue OSD-Elemente `vtx_power` und `flashback_budget`, Warnungen `vtx_hot` und `flashback_full`; eigene OSD-Layouts
  per `OsdLayoutCodec` kennen die Schlüssel ab 0.5.2.

### Umstellung
Nichts nötig. Wer eine Halterung je Rahmen erwartet hat, prüft `FrameDefinition#accessoryMounts()` auf `FRONT`; wer
`AccessorySlot.values()` oder `SwitchFunction.values()` durchläuft, bekommt Werte mehr. Videoteile, deren
`vtx_power_mw` nicht ihre höchste Stufe ist, geben `vtx_levels_mw` an.

## 0.5.1 — Parts-API 1.5.0, Netzprotokoll 15 (unverändert)

Fehlerbehebung ohne API-, Protokoll- oder Datenformat-Änderung; Addons für 0.5.0 laufen unverändert, Just More Parts
1.1.0 passt. 0.5.0 und 0.5.1 spielen in beiden Richtungen zusammen.

- Drohnen (`propwash:drone`) tragen gewöhnliche Entity-Tags: `pw_owner_<Name>`, `pw_pilot_<Name>`, `pw_flying` und
  `pw_autopilot` (nur auf dem Server, mit der Drohne gespeichert, [`docs/design/drone-tags.md`](../design/drone-tags.md)).
  Addons dürfen sie lesen (`entityTags()`); die beiden Zustands-Tags gleicht Propwash in jedem Tick ab, von außen
  gesetzte halten also nur einen Tick.
- Wer den Tab „Welt-Regeln“ bearbeiten darf, folgt jetzt dem Recht für `/gamerule` (Bedingungen der Befehlsknoten);
  ein Addon oder Rechte-Mod, das die Bedingung des `gamerule`-Knotens ersetzt, wirkt damit auch auf den Tab.

---

## 0.5.0 — Parts-API 1.5.0, Netzprotokoll 15

### Parts-API 1.5.0
- Zwei neue eingebaute Flugschul-Lektionen: `FlightSchoolScenes.TRICKS` (`propwash:tricks`, „Freestyle-Tricks“, nach
  `RACE`) und `FlightSchoolScenes.GRIPPER` (`propwash:gripper`, „Greifer: Items einsammeln“, nach `ACTION_CAM`).
  `FlightSchoolScenes.ALL` enthält beide in Anzeigereihenfolge; Addons, die `ALL` durchlaufen, sehen zwei Szenen mehr.
  Eigene Items oder Teile lassen sich mit `linkFlightSchoolItems`/`linkFlightSchoolParts` damit verknüpfen. Abfrage:
  `PropwashApi.isCompatible("1.5")`.
- Dritte neue Lektion `FlightSchoolScenes.MODE_3D` (`propwash:mode_3d`, „3D-Modus“, nach `HOLD`); `ALL` enthält sie.
- Vierte neue Lektion `FlightSchoolScenes.GATE_FREE` (`propwash:gate_free`, „Gates frei drehen“, nach `RACE`); `ALL`
  enthält sie. Additiv, deshalb bleibt die API bei 1.5.0 (0.5.0 ist noch nicht veröffentlicht).
- Fünfte neue Lektion `FlightSchoolScenes.COURSE_ELEMENTS` (`propwash:course_elements`, „Flaggen, Slalom und
  Tauchwürfel“, nach `GATE_FREE`); `ALL` enthält sie. Ebenfalls additiv in 1.5.0; eigene Kurs-Items von Addons lassen
  sich mit `linkFlightSchoolItems` an die Lektion hängen.
- Props können ihren Umkehrschub für den 3D-Modus angeben: `PropDefinition#reverseThrust()` (`OptionalDouble`, 0,2–1,0),
  Builder `reverseThrust(r)`, im Teile-JSON `"reverse_thrust": 1.0`. Ohne Angabe rechnet das Flugmodell mit 0,55
  (gewölbter Prop rückwärts angeströmt); symmetrische 3D-Props geben ≈ 0,95–1,0 an und meist kleinere `ct`/`cp` als
  gleich große normale Props. Der bisherige Konstruktor ohne das Feld bleibt; Addon-JSON ohne `reverse_thrust` lädt
  unverändert, ältere Propwash-Versionen ignorieren das Feld mit einer Warnung „unbekanntes Feld“.
- `BuildAccess#partCompatibility()` (Default-Methode, `true`): ob gerade die Welt-Regel `propwash:part_compatibility`
  („Teile müssen zusammenpassen“) gilt. `BuildAccess#check` und `createDrone` folgen ihr: Ist sie aus, stehen die
  aufgehobenen Probleme (`motor_mount`, `prop_too_large`, `prop_too_small`, `prop_mount`, `stack_mount`, `battery_frame`,
  `accessory_mount`) unter `warnings()`, `buildable()` ist wahr und `createDrone` wirft nur noch bei fehlenden Teilen oder
  Zubehör im falschen Platz. Mit der Standardregel ändert sich nichts. Wer „in echt baubar“ braucht (Web-Export,
  Vergleiche), prüft zusätzlich `warnings()` oder läuft ohne Welt (dort gilt immer der Standard).
- Sonst ändert sich an der API nichts: Addons für 1.0 bis 1.4 laufen unverändert weiter (Just More Parts 1.0.0 verlangt
  `requireVersion("1.4")`).
- Die Lektion „Bandos: verlassene Spots“ (`propwash:bandos`) meldet Propwash wie eine Addon-Szene an; sie steht nicht in
  `FlightSchoolScenes.ALL`.
- Flugschul-Hinweise in Addon-Szenen (ohne Änderung am Addon): Kästen stehen ruhig und ziehen nur bei Konflikten langsam um.
  Zwei Regeln gelten für jede Szene: Ein neuerer `callout` am selben Anker (gleicher Akteur oder beide fest, Versatz
  < 0,5 Blöcke) und auf derselben Seite löst den älteren ab; die bis zum Schluss offenen Hinweise des letzten Schritts
  bleiben bei „Fertig“ stehen, ohne vorher auszublenden. Additiv im exportierten `guide.scene.*`:
  `SceneState.CalloutView#cue()` (Nummer des Cues, der alte Konstruktor ohne sie bleibt) und `#layoutId()`,
  `Scene#cueId`, `Scene#cueEnd` (Ende samt Ablösung), `Scene.supersedes`, `Scene.SAME_ANCHOR`. Die API bleibt bei 1.5.0.

### Beitritts-Übersicht je Addon
- Passt beim Verbinden ein Addon nicht (fehlt, andere Version, andere Teiledaten), sieht der Spieler jetzt eine Karte je
  Addon mit **Name, Mod-Id, Version auf dem Server und bei ihm, Aktion und Link** statt einer Liste von Teile-Ids. Die
  Angaben kommen aus den Loader-Metadaten des Addons: `name`/`displayName`, `version`, `contact.homepage` (Fabric,
  sonst `contact.sources`) bzw. `displayURL` (NeoForge, sonst `modUrl`). Ohne Name zeigt die Übersicht die Mod-Id, ohne
  Link keinen Link. Anleitung: `addon-guide.md` Abschnitt 1.3.
- Als Addon zählt jede Mod, die in ihren Metadaten von `propwash` abhängt (auch optional), und jede Mod, der Teile
  gehören. Teile werden über den Namensraum (= Mod-Id empfohlen) oder über `loadBundled(modId)` zugeordnet; sonst stehen
  sie unter „Unbekannte Teile (<namensraum>)“.
- Propwash prüft jetzt in der Konfigurationsphase **vor** dem Registerabgleich von Fabric und NeoForge. Fehlt ein Addon
  mit Teilen, kommt also die Propwash-Übersicht statt „Received N registry entries that are unknown“ bzw. „The server
  sent registries with unknown keys“. Addons ohne Teile blockiert Propwash nicht; trennt danach der Loader, erscheint
  die Übersicht trotzdem mit dessen Meldung darunter.
- Neuer Konfigurations-Kanal `propwash:join_info`, deshalb Netzprotokoll 13. An der API ändert sich nichts.

### Addon nur beim Spieler: für die Sitzung inaktiv
- Hat nur der Spieler ein Addon mit Teilen, trennt der Server nicht mehr: Das Addon ist beim Spieler für die Sitzung
  inaktiv (Teile, Teil-Items, eigener Kreativ-Tab, Kreativ-Einträge, Presets, Werkbank-Erweiterungen,
  Konfigurator-Tabs `<modid>:…`, Flugschul-Szenen `<modid>:…`; Importe mit seinen Teilen antworten lokal
  `UNKNOWN_PARTS`), ein Toast sagt es dem Spieler. Fehlt das Addon dem Spieler oder weichen Teiledaten eines Addons
  beider Seiten ab, bleibt es wie bisher Pflicht. Ablauf: `docs/design/client-only-addons.md`, Regeln für Addons, die auf
  Servern optional sein wollen (Ids im eigenen Namensraum, direkt registrierte Items, optionale Kanäle auf NeoForge):
  `addon-guide.md` Abschnitt 17.1.
- Neu (additiv, deshalb weiter 1.5.0): `PropwashClientApi.isAddonActive(String modId)` – `false`, solange der Spieler
  mit einem Server verbunden ist, dem dieses Addon fehlt.
- `PropwashApi.parts().all()`, `get`, `chemistries()` und `builds().presets()` lassen inaktive Teile und Presets in der
  Sitzung weg; auf dem Server und im Einzelspieler ändert sich nichts. Netzprotokoll und Beitrittsdaten bleiben gleich
  (13).

### Item-Größen in der Hand
- Teile werden in der Hand nach ihrer echten Größe gezeigt (Handfaktor f = (L / 180 mm)^0,6, 0,25–1, `kit.hand_factor`):
  vorher bekam jedes Teil 9 px (erste Person) bzw. 7 px (dritte Person), ein 28-mm-Motor war so groß wie ein 5″-Rahmen.
  Eigene Modelle aus `kit.compute_display` bekommen das beim nächsten Erzeugen (nur die Hand-Einträge ändern sich); Just
  More Parts ist neu erzeugt. Symbole ohne Modell nehmen in den Händen `"hand": true` (`propwash:part`, additiv;
  `addon.py --icons special` schreibt es), ohne das Feld bleibt die bisherige Größe. Der Selbsttest „content: Item-Größen
  aller Items in allen Anzeige-Kontexten“ misst Bauteile in der Hand jetzt gegen die Grenze ihrer echten Größe
  (`ItemSizeRule.partLimit`): ein Addon-Motor mit der alten Handgröße fällt dort durch.

### Ducts im FPV-Bild
- Rahmen mit `duct` zeigen ihre Ducts jetzt im FPV-Bild. Propwash nimmt dafür die Flächen des Rahmenmodells (auch eigener
  Addon-Modelle), die ringförmig um eine Motorachse liegen (Abstand 0,97 × größter Prop-Radius bis Radius + max(6 mm,
  20 %)) und sich rundum mindestens fünfmal gleich wiederholen – also Ringsegmente wie aus `kit.Model.ring`. Ein Guard
  aus wenigen großen Einzelteilen erscheint dort nicht. Keine neue API, keine Pflicht; Details in
  [`docs/ASSETS.md`](../ASSETS.md), „Ducts im FPV-Bild“.
- `addon.py` erzeugt Ducts nach neuen Regeln (`tools/assets/parts/ducts.py`: Wand 0,35 + 0,016 × Prop, schmaler Rand,
  Oberkante knapp über den Blattspitzen) und setzt bei Rahmen mit Ducts Pads und Arme auf die Motorhöhe, die Propwash für
  Rahmen ohne Layout annimmt (1,5/2,5/3 mm). Bereits erzeugte Modelle bleiben gültig.

### Akku unter dem Rahmen
- Der Renderer nimmt die Seite des Akkus jetzt aus `battery.on_top` der Rahmen-Definition – derselbe Wert, mit dem das
  Flugmodell rechnet. Mit `on_top: false` hängt der Pack mit seiner Oberseite an der Platte (`plate_mm` = Unterseite von
  Bodenplatte und Pad), wie bisher schon bei Rahmen ohne Layout-Eintrag. Keine neue API, `API_VERSION` und Netzprotokoll
  bleiben; der eingebaute `race_5` trägt ihn jetzt unten (Flugmodell und Goldens nur für `race_5` neu).
- Neue, freiwillige Modell-Variante `<pfad>_under` (+ `_under_paint` und Eintrag in `drone_paint.json`) für Akkus mit
  Riemen: Pack von y = −Höhe bis 0, Riemen um den Boden. Fehlt sie, hängt Propwash das normale Modell an seiner Oberkante
  auf. `tools/assets/parts/batteries.py` hat dafür `build_lipo(key, under=True)`, `strap_band_under` und
  `wrap(…, under=True)`. Details: [`docs/ASSETS.md`](../ASSETS.md), „Akku unter dem Rahmen“.
- Einträge `frames.<rahmen>.battery` in einer eigenen `drone_layout.json` meinen für Rahmen mit dem Akku unten jetzt den
  Aufhängepunkt (Oberseite des Packs), nicht mehr die Unterseite. Just More Parts und das Beispiel-Addon haben keine
  solchen Einträge.

### Lizenz und Quellcode
- Ab 0.5.0 ist der Quellcode öffentlich. Die Parts-API (`de.lardtom.propwash.api` samt Unterpaketen), das
  Beispiel-Addon (`dev.example`, Namensraum `propwash_example`) und die Doku stehen unter der **MIT-Lizenz**
  (`LICENSE-MIT` nennt die Pfade); Addons dürfen daraus frei kopieren und haben jede eigene Lizenz. Der übrige Code
  (auch `apiimpl` und `parts`) steht unter der **PolyForm Shield License 1.0.0** mit Zusatzrechten (`LICENSE`).
- Jedes Jar (auch Quellen- und Javadoc-Jar in mavenLocal) enthält `LICENSE`, `LICENSE-MIT` und `NOTICE`; die
  Lizenzangabe in `fabric.mod.json`, `neoforge.mods.toml` ist `LicenseRef-PolyForm-Shield-1.0.0 AND MIT`, das
  Maven-POM nennt beide Lizenzen.
- Doku für Addon-Autoren mit Referenz aller öffentlichen Typen und dem Teile-Format: [propwashfpv.com/docs](https://propwashfpv.com/docs/).

### Welt-Regeln
- 28 Gamerules `propwash:…` (Liste in `docs/design/world-rules.md`, Kategorie `propwash:propwash`) gelten für alle Spieler
  einer Welt. Ein Addon kann sie serverseitig wie jede Gamerule lesen (`server.getGameRules()`); die Ids sind stabil.
- `ESC_BURNED` feuert nur noch mit der Welt-Regel `propwash:crash_damage` (vorher Server-Regel `partWear`), die
  Hitze-Drosselung hängt an `propwash:hardware_limits` statt an einer persönlichen Einstellung.
- `propwash:allow_3d_mode` (Schalter, Standard an): aus hält den 3D-Modus für alle Piloten aus.
- `propwash:part_compatibility` (Schalter, Standard an, Gruppe „Spiel“): aus = jedes Teil passt zu jedem, auch
  Addon-Teile untereinander; Drohnen-Items, Werkbank, Import und `BuildAccess` folgen dem Wert des Servers. Das
  Speicherformat ändert sich nicht, die Regel reist im bestehenden Paket `propwash:world_rules`.
- `propwash:autonomous_rescue` (Schalter, Standard an, Gruppe „Spiel“): Steigt der Pilot im Heimflug aus, fliegt der
  Server die Drohne allein heim. Speicherformat der Drohne: optionaler Eintrag `ReturnPlan` (nur während eines solchen
  Flugs), ältere Drohnen laden unverändert.
- `propwash:drone_trails` (Schalter, Standard aus, Gruppe „Spiel“, keine Physik): Drohnen-Trails, nur Client-Optik;
  die Parts-API ändert sich nicht.
- `propwash:gravity_scale` reicht von 0 bis 1000 % in 1-%-Schritten (vorher 80–150 % in 5er-Schritten), Standard
  100 %. Der Wert reist wie bisher als Zahl im Paket `propwash:world_rules`; jeder Wert ≠ 100 zählt als veränderte Physik.

### Flugmodell: Whoop-Widerstand, Sinkflug und Steigflug
- Der Widerstand von oben (`aero.top_area_m2`) eines Whoop-Rahmens zählt Rumpf und Blattfläche statt des ganzen Umrisses:
  `C_D · top_area ≈ 1,2 × Rumpf (Duct-Ringe, Motoren, Streben, Mitte) + 1,66 × Blattfläche der Props`. Die frühere
  Faustregel „1,18 × Umriss“ zählte die Prop-Scheiben in den Ducts wie eine volle Platte und lässt Whoops zu langsam
  fallen; eingebaut `propwash:whoop_65` 0,0059 → 0,0041 m², Just More Parts 1.1.0 passt seine Whoops an. Herleitung und
  Beispiele in `docs/design/realism/flight-feel-gravity.md` §10.
- Im angetriebenen Sinkflug (Drohne sinkt langsamer als das 1,5-fache der induzierten Geschwindigkeit ihres Schubs)
  entfallen 40 % des Widerstands von oben, weil das Rotormodell die Blätter dort selbst rechnet; bis zum 3-fachen blendet
  der Anteil wieder ein. Daten, API und Speicherformat ändern sich nicht.
- Schub im Steigflug nach Impuls- und Blattelementtheorie statt linear: Steigt ein Prop entlang seiner Achse, verliert er
  etwa halb so viel Schub wie bisher (Anfangssteigung `−r/(2r − 1)`, `r` = Nullschub-Geschwindigkeit aus `pitch_in` und
  Durchmesser geteilt durch die induzierte Geschwindigkeit aus `ct`); im Vorwärtsflug blendet der Effekt aus. Prop-Daten
  (`ct`, `cp`, `pitch_in`) behalten ihre Bedeutung, Schweben und Werkbank-Schub bleiben gleich; Add-ons, deren Presets
  knapp an Flugzeit-Fenstern liegen, sehen Reise und gemischt um 0,5–3 % länger (`flight-feel-gravity.md` §11). Daten, API
  und Speicherformat ändern sich nicht.

### Tuning: Scharf-Winkel
- Neuer Tuning-Schlüssel `pid.armMaxAngleDeg` (0–180°, Schritt 1, Standard 25°; 180° = scharf in jeder Lage, wie
  `small_angle` in Betaflight), Katalog in `docs/design/parts-api.md` §11.5. `TuneGroup.of` ordnet ihn `PID` zu, er steckt
  also in `TuneGroup.FLIGHT`: Standard-Importe (`tuneOnHeldDrone`, `onHeldDrone`) und Sharecodes, die `FLIGHT` übertragen,
  nehmen ihn mit. Just More Parts 1.0.x schreibt ihn ohne Änderung als Schlüssel mit Namen (Index 0) und liest ihn so;
  wer einen eigenen Sharecode mit fester Schlüssel-Tabelle hat, kann ihn hinten anhängen. Ältere Propwash-Versionen
  verwerfen den unbekannten Schlüssel beim Bereinigen. Keine neue API-Version: Die Java-Schnittstelle ändert sich nicht.

### Treffer und Greifer
- Neues eingebautes Zubehör `propwash:gripper` (untere Halterung, 18 g). Es steht wie jedes Teil in
  `#propwash:parts/accessory`; seine Greifer-Eigenschaften (Tragkraft, Aufnehmen) sind noch nicht für Addon-Zubehör
  freigegeben.
- Welt-Regel `propwash:drone_hits_entities` (Schalter, Standard an).
- Block-Tag `propwash:drone_surface` (Schnee, Seelensand, Schlamm, Honig): Für diese Blöcke zählt bei Drohnen die
  sichtbare Form als Aufstandsfläche, damit sie nicht im Block verschwinden. Datenpakete und Addons tragen dort eigene
  Blöcke ein, deren Kollisionsform niedriger ist als ihr Modell (`design/ground-contact.md`).
- Welt-Regel `propwash:break_impact_scale` (0–300 %, Standard 100): wie stark eine Drohne den Durchbruch durch einen
  zerbrechlichen Block spürt. Blöcke aus Datenpaketen und Addons im Tag `propwash:drone_breakable` bekommen Stoß und
  Scherben ohne weitere Angaben aus Härte und Dicke.
- Welt-Regel `propwash:drones_break_blocks` (Schalter, Standard aus) und Block-Tag `propwash:drone_breakable` (Glas,
  Scheiben, verzierter Topf, optional `#c:glass_blocks`/`#c:glass_panes`): Datenpakete und Addons ergänzen dort eigene
  zerbrechliche Blöcke, die Schwelle folgt aus Härte und Dicke der Kollisionsform, über Härte 1 bricht nichts. Brüche
  laufen durch die Abbau-Ereignisse der Loader (Fabric `PlayerBlockBreakEvents`, NeoForge `BreakBlockEvent`) mit dem
  Piloten als Spieler; ein eigenes API-Ereignis gibt es nicht (`docs/design/drone-break-blocks.md`).
- Schadensart `propwash:drone_strike` (direkt die Drohne, Verursacher der Pilot) und Ergänzungen der Tags
  `minecraft:no_knockback` und `minecraft:panic_causes`. Datenpakete können die Schadensart wie jede andere taggen.
- Speicherformat der Drohne: optionale Liste `Cargo` (Item-Stapel), ältere Drohnen laden unverändert.
- Drohnen ohne Greifer oder ohne Fracht fliegen bitgleich wie in 0.4.0; `AccessoryAnchor` rechnet für alle bisherigen
  Zubehöre unverändert.

### Action-Cam (Flashback)
- Neues eingebautes Zubehör `propwash:action_cam_flashback` (obere Halterung, 36 g) in `#propwash:parts/accessory`. Es hat
  keinen `action_cam`-Block (`isCamera()` ist falsch) und zeichnet nur einen Kamerapfad für die Replay-Mod Flashback auf;
  die Anbindung läuft rein per Reflection und ist nicht Teil der API.

### Bandos
- Neue Beutetabelle `propwash:chests/bando` (Truhen in Bandos) als Ziel für `PropwashApi.loot().inject(…)`; die Id ist
  stabil. Neue Strukturen `propwash:bando/<familie>` mit Tag `#propwash:bandos` und Strukturset `propwash:bandos`.

### Props reparieren
- Zwei beschädigte Props derselben Sorte ergeben im Crafting-Raster oder am Schleifstein einen Prop mit addiertem
  Zustand plus 5 % (höchstens neu). Das gilt ohne Zutun auch für Props von Addons, denn ihre Items sind Propwashs
  `PartItem`s mit `propwash:part`; Props verschiedener Sorten lassen sich nie kombinieren. Das Ergebnis ist eine Kopie des
  ersten Props mit allen Komponenten und neuem `propwash:part_condition`.
- Neuer Rezepttyp `propwash:prop_repair` (Sonderrezept ohne Felder, nicht im Rezeptbuch) und Rezept
  `propwash:special/prop_repair`; Datenpakete können es mit einer eigenen Datei gleichen Namens ersetzen. Der Schleifstein
  läuft über ein Mixin auf `GrindstoneMenu`; Addons, die selbst in `computeResult` eingreifen, sehen bei Props das
  Propwash-Ergebnis.
- Flugschul-Szenen: Item-Akteure von Teilen zeigen jetzt den Zustand aus `propCondition` (Platz 0), neue Kurzform
  `StepBuilder#itemCondition(actor, condition)`. Bestehende Szenen ändern sich nicht, solange sie Item-Akteuren keinen
  Zustand geben.
- Keine neue API-Version und kein neues Netzpaket.

### Konfigurator-Tabs
- Die neue Suche im Konfigurator durchsucht auch Tabs von Addons: Beschriftungen und Tooltips der Widgets, die ein Tab in
  `build()` anlegt, landen automatisch im Index (der Tab wird dafür einmal mit einem eigenen `ConfigTabHost` aufgebaut,
  `build()` sollte also keine Nebenwirkungen haben). Neu und optional: `ConfigTab#reveal(AbstractWidget)` scrollt einen
  gefundenen Widget ins Bild; Standard ist `false` (kein Scrollen). Bestehende Tabs laufen unverändert. Einzelheiten:
  [`docs/design/config-input.md`](../design/config-input.md).
- Der freigegebene Regler `ui.client.config.StepSlider` ist jetzt tippbar (Fokus per Klick, Tab oder Sprung aus der
  Suche, dann Ziffern, Enter, Esc), auch in Addon-Tabs, ohne Änderung am Addon. Getippt wird in der Einheit des
  Rohwerts. Zeigt die Beschriftung etwas anderes, sagt das Addon es mit den neuen Methoden: `percent()` (Rohwert 0–1,
  Anzeige in Prozent), `display(faktor, versatz)` (Anzeige = Rohwert × Faktor + Versatz), `typedRange(min, max)`
  (tippbarer Rohbereich), `percentRange()` (Hinweis mit %) oder `untyped()` (nur Maus und Pfeile wie bisher). Alle
  geben den Regler zurück. Im Konfigurator verwirft Esc eine laufende Eingabe; in einem eigenen Fenster schließt
  Vanilla bei Esc sofort – wer das nicht will, fragt in `keyPressed` vorher `slider.isEditing()` und ruft
  `slider.cancelEdit()`. Additiv, deshalb bleibt die API bei 1.5.0.

### Netzprotokoll 15
- Brille ohne Binden: Die Komponente `propwash:receiver_bind` überträgt den Piloten (`pilot`, optional). Brillen tragen keine Komponente `propwash:bound_drone` mehr; FPV oder Sichtflug entscheidet der Server aus
  der getragenen Brille und dem Videosystem der Drohne (`docs/design/receiver-binding.md`). Die Parts-API ändert sich
  nicht; Addons mit eigenen Videomodulen brauchen nichts zu tun, ihre Module zählen über `VideoLink` als analog oder
  digital.
- Runden je Kurs und Kurse bearbeiten: neue Pakete `propwash:race_course_edit` (Client → Server: Kurs, Aktion, zwei
  Zahlen, Text) und `propwash:race_course_defaults` (Client → Server: eigene Standard-Rundenzahl);
  `propwash:race_courses` trägt je Kurs zusätzlich Name, Rundenzahl und Ersteller, `propwash:race_leaderboard` die
  Rundenzahl des Kurses, das beste Rennen des Betrachters darüber und die Zahl archivierter Layouts. Kursdaten in
  `propwash_race.json` mit `name` und `laps` (fehlend: kein Name, Rundenzahl des Piloten). Die Parts-API ändert sich
  nicht (`docs/design/course-edit.md`).

### Netzprotokoll 14
- Gates geben Redstone-Signal: neues Paket `propwash:race_gate_signal` (Client → Server, Entity-Id und gepackte
  Signal-Werte), ein weiteres synchronisiertes `int` der Gate-Entity (an/aus, Pulslänge, „Nur im Rennen“). Gate-NBT
  `RedstoneSignal`, `PulseTicks`, `SignalRaceOnly` nur bei Abweichung vom Standard, `SignalPulse` während eines Pulses.
  Ein Mixin auf `BlockStateBase#getSignal`/`#getDirectSignal` liefert den Puls; Mods, die diese Methoden selbst
  überschreiben oder umleiten, sehen ihn über den Rückgabewert. Die Parts-API ändert sich nicht
  (`docs/design/gate-redstone.md`).
- Gate-Stange: neuer Wert `GATE_POLE` im vorhandenen Art-Byte der Gate-Entity (`docs/design/gate-pole.md`). Ein Client
  mit Protokoll 13 könnte ihn nicht lesen; die Nummer 14 deckt das mit ab.

### Netzprotokoll 12
- Neue Pakete `propwash:world_rules` (Server → Client), `propwash:world_rule_edit`, `propwash:drone_strike`,
  `propwash:cargo_release`, `propwash:drone_break`, `propwash:drone_break_clear` und `propwash:rescue_status`
  (Client → Server), `propwash:drone_break_denied` und `propwash:drone_break_clearance` (Server → Client); `propwash:server_rules` ohne `partWear`; zwei
  neue synchronisierte Daten der Drohnen-Entity (Fracht). Client und Server brauchen beide 0.5.0.
- Gates frei drehen (Protokoll 11): neues Paket `propwash:race_gate_edit` (Client → Server), `race_gate_rotate` mit
  Schrittweite, `race_gate_preference` mit Feinwinkeln, Kursdaten im `race_courses` mit Neigung und Größe, zwei neue
  synchronisierte Daten der Gate-Entity (Neigung, Öffnung). Nichts davon ist Teil der Parts-API.
- Kurs-Elemente (Protokoll 11): ein weiteres synchronisiertes Byte der Gate-Entity (Art und Variante: Gate, Wendeflagge,
  Slalomstange, Tauchwürfel), `race_gate_preference` und `race_gate_edit` mit einem Byte für die Variante, Kursdaten im
  `race_courses` mit Art und Variante (Merkerbit 4). Gate-NBT `Kind`/`Variant` und die Felder `kind`/`variant` der
  Kurse in den Weltdaten gibt es nur bei Elementen. Nichts davon ist Teil der Parts-API.
- Kreativ-Drohne (Protokoll 11): neues Paket `propwash:creative_edit` (Client → Server); der Bauplan-Stream trägt nach
  einem Kreativ-FC zusätzlich dessen Werte, das Item trägt sie als Komponente `propwash:creative_spec`. Das eingebaute
  Teil `propwash:creative_fc` steht im Tag `#propwash:parts/stack`; Addons, die über alle Stacks laufen und nur echte
  Teile meinen, lassen es über seine Id aus. Die Parts-API ändert sich nicht.
- Welt-Regel-Voreinstellungen (Protokoll 11): neues Paket `propwash:world_rule_preset` (Client → Server, Name der
  Voreinstellung); `gamerules.json` aus `tools/export_rules.sh` hat zusätzlich `presets` (Format bleibt 1). Die Parts-API
  ändert sich nicht.
- Empfänger-Bindung (Protokoll 12, vereinfacht mit Protokoll 15): neue Item-Komponente `propwash:receiver_bind`
  (`pilot`, optional) an Drohnen-Item und Flugcontroller, Entity-NBT `ReceiverBind`. Addons, die Drohnen-Items oder Stacks kopieren, nehmen die Komponente wie jede andere mit; `DroneData`
  und Teilen-Codes tragen sie nicht. Die Parts-API ändert sich nicht.
- Welt-Rand und Wertung mit Flughilfe (Protokoll 12): neues Paket `propwash:generation_edge` (Server → Client, Maske
  der schon erzeugten Chunks um die Drohne, wenn die Welt-Regel `propwash:drones_generate_chunks` aus ist);
  `race_report` trägt ab Merkerbit 1 die Gründe der Flughilfe, jede Zeile in `race_leaderboard` eine Zahl für diese
  Gründe. Die Parts-API ändert sich nicht.

---

## 0.4.0 — Parts-API 1.4.0, Netzprotokoll 8

### Einstieg
- Öffentliche, versionierte Parts-API im Paket `de.lardtom.propwash.api`. Die API-Version zählt unabhängig von der
  Mod-Version.
- Abhängigkeit auf beiden Loadern deklarieren: Fabric `"propwash": ">=0.4.0"`, NeoForge `versionRange = "[0.4.0,)"` mit
  `side = "BOTH"`. Beim Start `PropwashApi.requireVersion(modId, "1.4")` aufrufen, oder mit der kleinsten API-Version,
  deren Funktionen das Addon benutzt.
- Maven: `./gradlew publishToMavenLocal` veröffentlicht `de.lardtom:propwash-common-26.3:0.4.0`,
  `de.lardtom:propwash-fabric-26.3:0.4.0` und `de.lardtom:propwash-neoforge-26.3:0.4.0` mit Quellen und Javadoc.
- Einstieg und Beispiele: [`addon-guide.md`](addon-guide.md), Vertrag [`parts-api.md`](../design/parts-api.md),
  JSON-Schema [`parts-api-data.md`](../design/parts-api-data.md), Teilnamen
  [`part-naming.md`](../design/part-naming.md).

### API-Stände in 0.4.0
Die internen Stände von 0.4.0 haben die API von 1.0 bis 1.4 gebracht. Öffentlich erscheint nur 1.4.0; Addons, die nur
ältere Funktionen benutzen, dürfen eine kleinere Nebenversion verlangen.

| API | Inhalt |
|---|---|
| 1.0 | Teile in Java oder JSON, Items, Presets, Baupläne, Lack, Tuning-Gruppen, OSD-Layouts, ein vom Server geprüfter Import, Events, Werkbank-Knöpfe mit Unterfenstern, Konfigurator-Tabs, Flugschul-Szenen |
| 1.1 | `BuildAccess#cameraView(spec)`: FPV-Kamera und Prop-Scheiben, wie Propwash sie zeichnet |
| 1.2 | eigene Kreativ-Tabs (`PropwashApi.extensionTab(ns)`), `VideoDefinition#powerW()` / JSON `power_w`, `CellChemistry#usableFraction()` / JSON `usable_fraction` für das Energiemodell; `BuildStats` mit allen vier Flugzeiten, Strömen, Landespannung, Grundlast und beiden Motortemperaturen |
| 1.3 | Flugschul-Verknüpfungen für Items jedes Namensraums: `PropwashClientApi.linkFlightSchoolItems`, `linkFlightSchoolParts`, `flightSchoolScenes(stack)`, `openFlightSchool(stack, parent)`, Szenen-Ids in `FlightSchoolScenes`; Addon-Akkus, -Videoeinheiten, -Stacks und -Props bekommen die passende eingebaute Szene automatisch |
| 1.4 | Truhen-Beute, Server-, Daten- und Analyse-Ergänzungen, Werkbank und Client, Kamera (folgende Abschnitte; vollständige Tabelle in `parts-api.md` §3.4) |

### Neu in 1.4: Truhen-Beute ohne Loader-Code
- `PropwashApi.loot().inject(target, addonTable)` hängt eine Addon-Beutetabelle in eine Vanilla-Tabelle (oder jede
  andere), auf beiden Loadern gleich. Propwash fügt einen Pool hinzu, der die Addon-Tabelle würfelt, so wie die eigenen
  Teile in Dorf-, Verlies- und Prüfungskammer-Truhen kommen.
- Während der Mod-Initialisierung registrieren; `loot().tablesFor(target)` listet, was in eine Tabelle eingehängt ist
  (`parts-api.md` §6.6).

### Neu in 1.4: Server, Daten, Analyse
- Seltenheit aus dem Teil-JSON (`"rarity"`, `PartBasics#withRarity`), `MotorThermal.builder()`.
- Tooltip-Zeile *Stiffness* / *Steifigkeit* für Props, deren Steifigkeit vom Material-Standard abweicht.
- Mehr Analysewerte in `BuildStats`: `propwashNoise`, `fullThrottleCellVoltage`, Roll-, Nick- und Gier-Autorität, die
  Warnliste `BuildStats.WARNINGS`.
- Event `PropwashEvents.ESC_BURNED`; `BuildAccess#partCondition`, `#paintCost` und `#footprintMm`.
- Akkus mit Teilladung (`PartItems#stack(id, charge)`), Import-Ebene `NAME`, fertige Drohne vom Client erzeugen
  (`PropwashClientApi.sendCreateDrone`, nur Kreativ oder Operator).
- Propwashs eigener JSON-Codec (`PartRegistry#toJson`/`fromJson`) und Sprachtexte auch auf dem Server
  (`PropwashApi.text`).
- Stabilitätsregel für Records: Wertrecords haben einen stabilen kanonischen Konstruktor (`parts-api.md` §3.3).

### Neu in 1.4: Werkbank und Client
- Die Werkbank hält ihren Titel lesbar und nennt Addons den freien Platz im Kopf (`WorkbenchContext#headerSpace`).
  Knöpfe können eine Kurzbeschriftung mitbringen oder ein Symbol von 13 × 13 Pixeln sein; `missingParts(data)` listet,
  was dem Spieler fehlt.
- `OsdLayoutAccess#validate`, `PropwashClientApi.openConfigTab`, `renderPreview(…, pixelsPerMillimetre)` im festen
  Maßstab, das Theme-Textfeld `TextField` und `forceShiftHelp` für Bildschirmfotos und Selbsttests.
- Basismodelle je Kategorie `propwash:item/part_base/<kategorie>`. Lack erreicht jetzt auch Addon-Teile, die aus ihrer
  erzeugten Form gezeichnet werden; skalierte Modelle sehr großer Teile sind zugesichert.
- Der Katalog der Tuning-Schlüssel und alle Bau-Warnungen stehen im Vertrag (`parts-api.md` §11.5, §9.6).

### Neu in 1.4: Kamera (Verhaltensänderung)
- `camera_fov_deg` eines Videoteils (`VideoDefinition#cameraFovDeg`, horizontal) bestimmt jetzt das FPV-Sichtfeld und
  die Analyse „Props im Kamerabild“. Einheiten ohne den Wert behalten den alten Standard ihres Systems (analog 120°,
  digital 130°). Abfragbar mit `PropwashApi.isCompatible("1.4")`.
- Der Kamerawinkel (Uptilt) ist ein Wert je Drohne im Tuning (`TuneSpec.CAMERA_UPTILT` = `camera.uptiltDeg`, Gruppe
  `CAMERA`), begrenzt auf den Kamerahalter des Rahmens. `CameraView#uptiltRange()` liefert Minimum, Maximum und
  Rahmen-Standard; `BuildAccess#cameraView(spec, uptiltDeg)` rechnet die Ansicht für einen anderen Winkel,
  `BuildAccess#uptiltRange(frame)` den Bereich eines Rahmens.
- Importe mit der Tuning-Gruppe `CAMERA` tragen den Winkel; ohne Winkel gilt der Standard des Rahmens
  (`parts-api.md` §11.6).

### Hinweise zur Umstellung
- **Kamerahalter setzen:** Rahmen sollten `camera.pos` angeben, die Lage des Objektivs: bei X-Rahmen meist 1–5 mm unter
  der Propebene, bei Whoops knapp über den Ducts. Ohne den Wert setzt die alte Formel das Objektiv 15 mm über den
  Schwerpunkt, meist über die Props, und das FPV-Bild zeigt kaum Props. Hintergrund und Werte aller Rahmen:
  [`docs/design/realism/props-in-view.md`](../design/realism/props-in-view.md).
- **Eingebaute Teile als Vorlage:** Sie behalten ihre Ids (`propwash:m2207_1750`) und sind einfache JSON-Definitionen
  unter `data/propwash/propwash_parts/`. Addon-Teile laden aus den eigenen Ressourcen mit
  `PropwashApi.parts().loadBundled(modId)`; aus Welt-Datenpaketen werden sie nicht gelesen.
- **Modelle:** Modelle, Lack-Varianten und Inventarsymbole kommen aus dem Namensraum des Addons; ohne sie zeichnet
  Propwash eine aus der Definition erzeugte Form. `tools/assets/parts/addon.py` erzeugt Modelle aus Teil-JSONs
  ([`docs/ASSETS.md`](../ASSETS.md)).
- **Item-Rahmen:** Rahmen- und Prop-Modelle hängen flach an der Wand (`display.fixed` mit Drehung −90/180/0, Fußkreis
  von 24 Item-Pixeln, erzeugt von `kit.compute_display`). Symbole ohne Modell nutzen `propwash:part` mit
  `"wall": true` und das Basismodell `propwash:item/part_wall` im Kontext `fixed` (`addon-guide.md` §6). Drohnen aus
  Addon-Teilen hängen automatisch an der Wand.
- **Item-Größen:** `kit.compute_display` misst am Boden, im Item-Rahmen, im Regal und am Kopf den ganzen gedrehten
  Quader (nur im Inventar und in der Hand die Vorderansicht); sperrige Teile liegen am Boden höchstens wie ein Würfel
  von 0,3 Block. Symbole ohne Modell (`propwash:part`) nehmen `"ground": true` am Boden und `"box": true` im Regal, am
  Kopf und im Item-Rahmen; `addon.py --icons special` schreibt beides. Grenzen je Anzeige-Kontext: `docs/ASSETS.md`
  („Größenregel der Items“). Der Selbsttest prüft die Items jedes geladenen Addons.
- **Teilnamen:** eingebaute und Addon-Teile folgen einem Schema (Kenngröße und Gerät zuerst, Zusätze in Klammern,
  Shift-Kurzbeschreibung mit Kurzkennung): [`part-naming.md`](../design/part-naming.md). Item-Ids, Spielstände und
  Sharecodes ändern sich dadurch nicht.
- **Fehlende Addons:** Fehlt ein Addon, zeigen seine Teile *Unknown part*; die Drohne lässt sich nicht scharf
  schalten, und Zerlegen gibt für diese Teile nichts zurück. Nach dem erneuten Installieren ist die Drohne wieder
  vollständig.
- **Server-Beitritt:** Client und Server vergleichen ihre Teile und nennen fehlende oder abweichende Teile, statt
  später zu scheitern.

### Datenformate
- Gates: Entity-NBT `Mount` (Standard `standing`) und `Flipped` (Standard `false`), Kursdaten
  (`propwash_race.json`) mit `GateRecord.mount` und `flipped`. Alte Welten und Kurse laden als stehende Gates; Layouts,
  Geister und Bestenlisten bleiben gültig ([`gate-mounting.md`](../design/gate-mounting.md) §7).
- Kamerawinkel: im Tuning der Drohne (`camera.uptiltDeg`, nur gespeichert, wenn er vom Rahmen-Standard abweicht); beim
  Zerlegen bleibt er am Rahmen-Item. Die frühere globale Uptilt-Einstellung und das Sichtfeld je Brille entfallen, alte
  Werte werden ignoriert.
- Welten, Drohnen, Items, Rezepte, Beute, Presets, Geister und Einstellungen aus 0.3.x laden unverändert.

### Netzprotokoll 6 → 8
- 7 war ein interner Stand (Parts-API). 8 bringt die neue Bedeutung der Import-Bitmasken (Ebene `NAME` = Bit 3, Ziel
  `NEW_DRONE` = 2), das synchronisierte Lage-Byte der Gates, die Lage in `CourseData` und zwei neue C2S-Pakete.
  Client und Server brauchen beide 0.4.0.

### Werkzeuge
- `tools/calibration.sh` gibt die Analyse aller eingebauten und Addon-Presets gegen die Referenzbereiche aus, prüft
  die Reihenfolge der Flugzeiten und schreibt für jede Variante einen Bewegungs-Fingerabdruck. Damit lässt sich
  zwischen zwei Versionen belegen, welche Aufbauten anders fliegen
  ([`docs/design/realism/calibration.md`](../design/realism/calibration.md)).

### Veralten
- In 1.4 ist nichts als veraltet markiert. Künftige Ankündigungen stehen hier und in `parts-api.md` §3.3, mindestens
  eine Nebenversion vor der Entfernung in der nächsten Hauptversion.

---

## 0.3.2 — keine API, Netzprotokoll 6
- Rekord-Geister, die ein 0.3.2-Server speichert, tragen die geflogene Drohne. Clients mit 0.3.1 oder älter zeigen
  sie nicht. Geister älterer Versionen nutzen die aktuelle Drohne derselben Klasse, sonst ein passendes Preset.
- Drohnen-Items werden aus den gespeicherten Drohnendaten gezeichnet; gleiche Drohnen teilen sich ein Symbol.

## 0.3.1 — keine API, Netzprotokoll 6
- Gestapelte Gates: Jedes Gate bleibt ein eigenes Kursgate mit Nummer; Welten und Kurse laden unverändert.

## 0.3.0 — keine API, Netzprotokoll 6
- Der Lack steht am Rahmen der Drohne, wird an alle Spieler synchronisiert und bleibt beim Zerlegen am Rahmen. Alte
  Drohnen behalten ihre Originalfarben.
- Bestenlisten liegen als Weltdaten je Kurs und Drohnenklasse; alte Bestenlisten werden einmal übernommen.
- Jeder Videosender hat einen Kanal; der Server verteilt freie Kanäle.
- Neue Server-Regeln in `config/propwash/server.json`: `worldCourses` (keine neuen Weltkurse in ungeneriertem Gelände)
  und `allowSpectating`.

## 0.2.0 und 0.2.1 — keine API, Netzprotokoll 5
- Jedes Teil trägt seinen Zustand; Teile aus 0.1.0 starten in perfektem Zustand. Zubehör verschleißt nicht.
- Zubehör-Plätze (Action-Cam oben, LED-Streifen unter der Nase) und die LED-Einstellungen stehen auf der Drohne.
- Neue Server-Regel `partWear`. Optionale Integration LambDynamicLights (Fabric und NeoForge).
- 0.2.1 ändert keine Formate und spielt mit 0.2.0 zusammen.

## 0.1.0 — keine API, Netzprotokoll 2
- Erstes Release. Server-Regeln in `config/propwash/server.json`, Befehle unter `/propwash`; abweichende Versionen von
  Client und Server werden beim Verbinden mit klarer Meldung abgewiesen.
