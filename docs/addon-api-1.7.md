# Add-on-API 1.7 (Propwash 0.6.0)

Für Add-on-Autoren und Server-Betreiber. Propwash 0.6.0 bringt die Add-on-API **1.7** mit der Ergänzung **1.8**
(`PropwashApi.API_VERSION` 1.8.0, Netzprotokoll 20). Sie schließt die Lücken, für die Add-ons bisher Mixins in
Propwash-Interna brauchten, und gibt den beiden offiziellen Add-ons nach 0.6.0 alle Bausteine. Alles ist optional: Ohne
Add-on verhält sich Propwash wie vorher. Ab dem ersten Release-Kandidaten ist die API eingefroren (nur noch Ergänzungen
für die offiziellen Add-ons); Fracht am Greifer und Zubehör einrücken heben die Nebenversion auf 1.8.

Dieser Text ist die Kurzfassung für die Website (Abschnittstitel deutsch mit englischem Titel in Klammern). Die
Anleitung mit Beispielen und allen Signaturen steht im Handbuch [`api/addon-guide.md`](api/addon-guide.md) Abschnitt 21
(Sharecodes: Abschnitt 13), die Änderungsliste in [`api/changes.md`](api/changes.md) (0.6.0), Technik und
Entscheidungen im Design [`design/0.6.0/addon-api-namespace.md`](design/0.6.0/addon-api-namespace.md) (§3, Tabelle
§3.19). Er löst den Vertrag von API-Stand A ab (`addon-api-1.7-contract.md`): In Propwash 0.6.0 haben alle Bausteine ihr
Verhalten.

## 1. Abhängigkeit (Dependency)

| Wo | Eintrag |
|---|---|
| Fabric (`fabric.mod.json`) | `"propwash": ">=0.6.0-"` |
| NeoForge (`neoforge.mods.toml`) | `versionRange = "[0.6.0-alpha,)"` |
| Laufzeit | `PropwashApi.requireVersion(modId, "1.7")`; mit Fracht am Greifer oder Zubehör einrücken `"1.8"` |

Beide Bereiche nehmen `0.6.0-dev.N`, `0.6.0-rc.N` und `0.6.0` an und lehnen 0.5.x ab. Nicht verwenden: `>=0.6.0`
(Fabric lehnt alle Vorabstände ab), `[0.6.0,)` (NeoForge lehnt alle `-rc`-Stände ab) und `[0.6.0-dev.N,)` (lehnt unter
NeoForge sogar `0.6.0` ab). `requireVersion` prüft die API, nicht die Mod-Version.

## 2. Bausteine (Building blocks)

| Baustein | Einstieg | Was es kann | Handbuch | Einzelheiten |
|---|---|---|---|---|
| Schalter und Achsen | `PropwashApi.switches()` (`SwitchSpec`, `AxisSpec`, `AddonSwitch`, `AddonAxis`), `PropwashClientApi.bindSwitchKey`, `bindAxisKeys`, `ClientInput#switchActive`, `#axis`, Event `ADDON_SWITCH_CHANGED`; für Selbsttests `PropwashClientApi.simulateRadio`, `bindSimulatedSwitch`, `bindSimulatedAction`, `setSimulatedButton`, `setSimulatedAxis`, `stopSimulatedRadio` | eigene Schalter, Taster und analoge Kanäle auf der Seite „Add-ons“ im Tab „Modi“, belegbar wie eingebaute Funktionen; mit `serverSync` auch am Server; Testfunke (Gamepad oder Funke) nur in der Entwicklungsumgebung, belegt auch Zubehör-Aktionen | §21.2 | [`addon-switches.md`](addon-switches.md) |
| Namen der Zubehör-Aktionen | `AccessoryBehavior#actionNameKey(int)` | „Zubehör-Aktion 1 – Werfer feuern“ im Tab „Modi“, Hinweis beim ersten Flug | §21.3 | [`addon-switches.md`](addon-switches.md) §2 |
| Namen der Flugmodi | `FlightAssist#modeNameKey(StabilizationMode)` | Meldung beim Moduswechsel und Hinweis „per Taste gewählt“ im Tab „Modi“ mit den Modi der Flughilfe, etwa „Mode: C (key – radio switch: S)“ einer Kamera-Drohne | §20.7 | [`addon-switches.md`](addon-switches.md) §2, §3 |
| OSD-Elemente und Warnungen | `PropwashClientApi.registerOsdElement(OsdElementSpec)`, `registerOsdWarning(Identifier, OsdWarning)` | Text im OSD-Raster, im OSD-Editor platzierbar; Warnungen im eingebauten Warnfeld | §21.4 | [`addon-osd.md`](addon-osd.md) |
| Kollisionsregeln | `DroneAccess#registerCollisionRule(Identifier, CollisionRule)`, `CollisionFlags`, `CollisionQuery`, `BlockContact` | durch Blöcke, Wasser, Entities und Ranken fliegen; Antwort berührter Flächen ändern; der Server wertet selbst aus | §21.5 | [`addon-collision.md`](addon-collision.md) |
| Werfen wie ein Dispenser | `AccessoryContext#dispense(int, DispenseOptions)` → `DispenseResult`, `AccessoryRegistry#triggerAction(…)` | Vanilla-Verhalten aus der Halterung in jeder Richtung, mit Bau-Rechten, ohne Verbrauch möglich, Rückstoß im Flugmodell; Auslösen am Server | §21.6 | [`accessory-gaps.md`](accessory-gaps.md) §1 |
| Greifer am Server (1.8) | `AccessoryRegistry#grabCargo(Entity, ServerPlayer)`, `#releaseCargo(Entity, ServerPlayer)`, `#cargo(Entity)` | Fracht mit dem eingebauten Greifer greifen und loslassen, auch ohne Pilot im Add-on-Flug, etwa an Wegpunkten eines Flugplaners; das Flugmodell rechnet die Fracht mit | §21.23 | [`addon-autonomy.md`](addon-autonomy.md) §11 |
| Inhalt sichtbar | `AccessoryBehavior#display(int)` → `ItemDisplay` | eingesetzte Items (Speer, Rakete) an der Drohne für alle Zuschauer | §21.7 | [`accessory-gaps.md`](accessory-gaps.md) §2 |
| Explosions- und Windstöße | Event `DRONE_KNOCKBACK` (`DroneKnockback`) | Stoß ins Flugmodell geflogener Drohnen, nur wenn ein Add-on ihn anfordert (Standard-Skala 0; Breeze-Windkugeln und Wither-Explosionen mit der Welt-Regel `drones_boss_interactions` Skala 1) | §21.8 | [`accessory-gaps.md`](accessory-gaps.md) §3 |
| Alle Drohnen, Kamerablick | `DroneAccess#loaded(ServerLevel)`, `#cameraPose(Entity)` → `CameraPose` | alle geladenen Drohnen eines Levels ohne Entity-Suche; Blick der FPV-Kamera am Server | §21.9 | [`accessory-gaps.md`](accessory-gaps.md) §4 |
| Welt-Regeln | `PropwashApi.worldRules()` (`registerBoolean`, `registerInt`, `presetValue`), `WorldRule#get` | Schalter- und Zahlenregeln im eigenen Namensraum, eigener Abschnitt im Tab „Welt-Regeln“, auch in Voreinstellungen | §21.10 | [`addon-world-rules.md`](addon-world-rules.md) |
| Daten an der Drohne | `PropwashApi.droneData()` (`DroneDataKey`, `Sync`) | typisierte Daten per Codec an Drohne und Item, optional an Zuschauer abgeglichen; ohne Add-on unverändert erhalten | §21.11 | [`drone-data.md`](drone-data.md) §1 |
| Autonomer Flug | `DroneAccess#autonomy()` (`start`, `stop`, `active`, `registerExitHandler`, `notReadyReason` mit `AutonomyNotReady`), `AutonomyOptions`, `AssistOutput#land()`, Event `AUTONOMY_ENDED` | der Server fliegt die Drohne mit der Flughilfe des Add-ons: Halten, Folgen, Punkt, Mission weiter nach dem FPV, Start vom Boden (auch auf hohem Zubehör, Gimbal oder Standbeinen), Landung; Ende mit dem Akku; Grund zu `NOT_READY` | §21.12 | [`addon-autonomy.md`](addon-autonomy.md) |
| Zweiter Operator | `DroneAccess#operators()` (`bind`, `unbind`, `operator`), `GimbalContext#operatorAxis`, `#operatorAction`, `FlightState#operatorAxis`, `ClientInput#operating()` | ein zweiter Spieler steuert mit seiner Funke den Gimbal; alle sehen dieselbe Lage | §21.13 | [`operator-gimbal.md`](operator-gimbal.md) |
| Gimbal in Aufnahme und Modell | `GimbalProvider#stabilizesRecording()`, `#modelPose(…)`, `#fovDegrees(…)`, `#showsProps(…)`, `#gimbalRotation(…)`, `#gimbalFovDegrees(…)`, `PropwashClientApi.recording()`, `spectate(Entity)` | Action-Cam vorn mit Gimbal-Lage, Kamerapfad, drehender Gimbal-Kopf, Brennweite, Gimbal-Bild ohne Props, Kamerapfad aus der Gimbal-Kamera (Lage und Brennweite) auch bei FPV-Bild, Aufnahme und Foto per API, Livebild der eigenen Drohne | §21.14 | [`operator-gimbal.md`](operator-gimbal.md) |
| Pilotenwechsel und Wartung | Event `PILOT_CHANGED`, `PropwashClientApi.registerServiceExtension(ServiceExtension)` | Wechsel des Piloten am Server; eigene Knöpfe in der Wartung | §21.15 | [`drone-data.md`](drone-data.md) §2 |
| Kompass | `FlightState#compassHeadingDeg()`, `#compassErrorDeg()`, `#compassHealthy()` | Kompassfehler des Flight Controllers in der Flughilfe | §20.7 | [`api-compass.md`](api-compass.md) |
| Neigungsgrenze | `FlightState#angleLimitDeg()`, `FlightState.DEFAULT_ANGLE_LIMIT_DEG`, `DroneView#angleLimitDeg()` | Neigung bei vollem Knüppel in ANGLE aus dem PID-Tab (10–85°, Standard 60°) für Knüppel und Bremswege der Flughilfe | §20.7 | [`addon-autonomy.md`](addon-autonomy.md) §2 |
| Zubehör-Form | `AccessoryDefinition#ledPathMm()`, `#hangsStraight()` (JSON `led_path`, `hangs_straight`) | LEDs entlang einer Linie; Zubehör ohne Neigung der Halterung | §3.4 | [`accessory-hang.md`](accessory-hang.md) |
| Zubehör einrücken (1.8) | `AccessoryDefinition#recessMm()`, `#dropMm()`, Builder `recess(double)`, `drop(double)` (JSON `recess_mm`, `drop_mm`) | Zubehör rückt nach hinten und unten, etwa unter den Rahmen statt vor die Nase; Modell, Flugmodell und `MountPose` folgen | §3.4 | [`accessory-hang.md`](accessory-hang.md) §8 |
| VTX-Antennen am Rahmen | `FrameDefinition.Builder#antennaAnchor(AnchorSpec)`, `AirframeGeometry#antennaAnchor()` (JSON `antenna_anchor`), `VideoDefinition.Builder#antenna(VideoAntenna)`, `VideoDefinition#antenna()` (JSON `antenna`), `VideoAntenna`, `VideoAntenna.none()` (JSON `"antenna": "none"`), `AntennaCap` | Antennenhalter ganz hinten hinter Rahmenkante und Akku mit Neigung (Standard 45°, sonst Rückfall 12 mm hinter der Deckplatte); Anzahl, Länge und Kappe der Antennen, sonst aus den VTX-Daten abgeleitet; mit `none()` keine externen Antennen (kein Lack-Platz „Antennenspitzen“, Masse und Funk unverändert); Antennen-Elemente im VTX-Modell lässt Propwash aus | §21.16 | [`ASSETS.md`](ASSETS.md) „VTX-Antennen hinten am Rahmen“ |
| Zubehör nur an bestimmten Rahmen | `AccessoryDefinition#frames()`, Builder `frames(Identifier...)` (JSON `frames`) | ein Zubehör passt nur an die genannten Rahmen, sonst Bauproblem `accessory_frame`; gebaute Drohnen behalten es | §3.4 | [`accessory-fix.md`](accessory-fix.md) §4 |
| Sharecodes | `PropwashApi.shareCodes()` (`ShareCodes`, `ShareResult`, `ShareOptions`), `ShareTableCheck`, Teile-Tabelle im Jar | Codes lesen, schreiben und umrechnen; kurze Codes für die Teile jedes Add-ons | §13 | [`sharecode/tables.md`](sharecode/tables.md) |
| Renndaten | `PropwashApi.race()` (`RaceData`, `BoardQuery`), `RaceEvents` | Kurse und Bestenlisten nach Kurs-ID lesen, Ereignisse für Runden, Rennen und Rekorde; JSON-Spiegel im Weltordner | §21.17 | [`api/race-data.md`](api/race-data.md) |
| Chunks um die Kamera-Drohne | `DroneAccess#cameraChunks()` (`CameraChunkSupply`: `open`, `close`, `closeAll`, `status`, `active`; `CameraChunkResult`, `CameraChunkStatus`), `PropwashClientApi.cameraChunks()` (`CameraChunkRequests`: `request`, `release`, `radius`, `answer`) | der Server schickt einem Spieler Chunks und Entities um seine eigene, weit entfernte Kamera-Drohne zusätzlich zu seiner Ansicht, damit Flashback die Umgebung aufnimmt; Radius bis 8 und Welt-Regel `camera_chunks`, 2 Lagen je Spieler, Ende bei Abmelden, Trennen, Weltwechsel und Tod | §21.22 | [`camera-chunks.md`](camera-chunks.md) |

`FlightState#addonSwitch(String)`, `#addonAxis(String)` und `GimbalContext#axis(String)` liefern dieselben Werte wie
`AddonSwitch#active` bzw. `AddonAxis#value` für die Drohne (Handbuch §21.2). Das OSD-Layout über
`PropwashClientApi.osd()` und in Sharecodes nimmt die Einträge der Add-on-Elemente mit (Handbuch §12).

## 3. Bestehende Schnittstellen (Existing interfaces)

Jede neue Methode an einer Schnittstelle aus 1.6 ist eine `default`-Methode mit neutralem Ergebnis. Eigene
Implementierungen und Test-Doubles (`FlightState`, `AssistOutput`, `FlightAssist`, `GimbalContext`, `GimbalProvider`,
`ClientInput`, `AccessoryBehavior`, `AccessoryContext`, `AccessoryRegistry`, `DroneAccess`) kompilieren und laufen
unverändert. Alle Signaturen der API 1.6 bleiben, auch die bisherigen Konstruktoren von `AccessoryDefinition` (12 und
13 Argumente) und der Konstruktor von `DroneView` mit 11 Argumenten (Neigungsgrenze 60°).
Records aus 1.7 entstehen nur über Builder und Fabriken (`SwitchSpec.builder`, `AxisSpec.builder`,
`OsdElementSpec.builder`, `AutonomyOptions.of`, `DispenseOptions.defaults`, `ItemDisplay.at`, `ShareOptions.defaults`,
`BoardQuery.laps`) und bekommen in späteren Nebenversionen Felder; neue Enum-Konstanten kommen nur ans Ende.

## 4. Namensraum `propwash` (Reserved namespace)

Der Namensraum `propwash` gehört Propwash (Regeln für Add-ons: [`ADDONS.md`](../ADDONS.md), Prüfung beim Start:
[`namespace-guard.md`](namespace-guard.md)).

- **Neue Punkte aus 1.7** und Punkte, die schon in 1.6 warfen (Teile, Zellchemien, Zubehör-Verhalten, HUD, Gimbal,
  Flughilfen, Kräfte, Presets, Beute-Tabellen, Kreativ-Tabs): `IllegalArgumentException` mit dem Text
  `Mod '<mod>' tried to register 'propwash:<pfad>' (<art>): the namespace propwash is reserved for Propwash. Use your mod id.`
- **Bisher tolerante Punkte** (Konfigurator-Tabs, Werkbank-Erweiterungen, Flugschul-Szenen): Die Anmeldung wird ohne
  Ausnahme verweigert, mit demselben Text als Fehler im Log. Ein Add-on, das mit 0.5.2 startete, bricht so nicht ab.
- Beim Start und beim Neuladen meldet Propwash fremde Einträge unter `propwash` (Registry-Ids, Übersetzungsschlüssel,
  Tastenbelegungen, Netzwerk-Pakete, angehängte Enum-Werte, Daten- und Asset-Dateien, `/propwash`-Unterbefehle) im Log
  und einmal je Spielstart im Chat, ohne Absturz. Ressourcen- und Datenpakete der Spieler dürfen Propwash-Dateien
  weiter überschreiben.
- Reserviert ist nur genau `propwash`; `propwash_example` oder `propwashy` sind eigene Namensräume.
- Jeder Crash-Report hat den Abschnitt „Propwash add-ons“ mit Add-ons, Mods mit Mixins in Propwash-Interna und den
  Funden der Prüfung.

## 5. Umstieg von Mixins (Moving off mixins)

Das Community-Add-on *Noclip* (0.1.x) schaltet die Kollision der Drohne per Mixins in Propwash-Interna ab. Mit 1.7 geht
dasselbe mit einem Schalter (`PropwashApi.switches()`), einer Kollisionsregel (`CollisionFlags.GHOST`, Rechte am Server)
und einer OSD-Warnung, ohne Mixin und ohne Einträge im Namensraum `propwash`. Das vollständige Beispiel (rund 60 Zeilen)
mit der Tabelle, welche Mixin-Ziele die API ersetzt, steht im Handbuch §21.19.

## 6. Was für Mixin-Add-ons bricht (What breaks for mixin add-ons)

Stabil ist nur `de.lardtom.propwash.api`. Mixins in Interna bleiben erlaubt, auf eigenes Risiko; eine
Kompatibilitätsschicht gibt es nicht. 0.6.0 ändert unter anderem die Schaltermaske (`int` → `long` in `RadioFrame`,
`SwitchEvaluator` und den Aufrufern), verallgemeinert den Heimflug zu `rescue.ServerFlight`, ändert den Flugweg in
`PilotSession` und `DroneSim` (Kollisionsregeln, Oberflächen, Motor-Hitze, Freecam) und hängt das OSD-Element
`FLIGHT_PATH_MARKER` an. Ein Mixin mit `require` auf eine geänderte Stelle bricht den Start ab. Die Stellen, auf die
Noclip 0.1.x zielt, hat 0.6.0 mit Name und Deskriptor behalten, wo es ging; die Namensraum-Prüfung meldet seine
angehängten Enum-Werte und Schlüssel trotzdem. Einzelheiten: Handbuch §21.20.

## 7. Renndaten für Server und Bots (Race data for servers and bots)

Ein Server-Add-on gibt es nicht. Stattdessen legt Propwash seine Renndaten selbst offen: Jeder Kurs hat eine stabile
Kurs-ID (UUID), an ihr hängen die Bestenlisten. Eine Mod liest Kurse und Bestenlisten über `PropwashApi.race()` (nur
lesend, Server-Thread, unveränderliche Schnappschüsse) und abonniert `RaceEvents` (Runde, Rennen, persönliche Bestzeit,
Kursrekord, Kurs angelegt, geändert, gelöscht). Ohne eigene Mod liegen dieselben Daten als JSON im Weltordner unter
`propwash/race-data/`. Schema und Beispiel einer Bot-Mod: [`api/race-data.md`](api/race-data.md).

## 8. Nicht in 1.7 (Not in 1.7)

Eigene Funken-Items, mehr als ein Operator je Drohne, Add-on-Welt-Regeln in Zonen, Schwerkraft oder Wind je Drohne per
API, freie Grafik im OSD-Raster (dafür `HudElement`), Zubehör auf der Use-Taste, Add-on-Daten in Sharecodes,
autonomer Flug außerhalb der Funkreichweite, Lautstärke am Server, Flugmodell-Prüfstand, Rahmen-Rolle `CAMERA`,
Kamera-Haken für VR, Schreiben in Bestenlisten.

## 9. Englische Kernsätze (Website)

- Propwash 0.6.0 ships add-on API 1.8.0: switches and axes, OSD elements and warnings, collision rules, dispensing,
  item display, knockback events, world rules, drone data, autonomous flight, a second operator, gimbal recording,
  service buttons, share codes and race data (1.7), plus gripper cargo and recessed accessories (1.8).
- Add-ons no longer need mixins into Propwash internals for switches, OSD readouts, collision or autonomous flight.
- Every method added to an existing 1.6 interface is a default method, so add-ons built for 1.6 keep working.
- Depend on `>=0.6.0-` (Fabric) or `[0.6.0-alpha,)` (NeoForge) and call `PropwashApi.requireVersion(modId, "1.7")`
  (`"1.8"` for gripper cargo or recessed accessories). Don't use `>=0.6.0` or `[0.6.0,)`: they reject pre-releases.
- The namespace `propwash` is reserved for Propwash. Registrations under it are refused; new API points throw.
- Share codes belong to Propwash now: an add-on only ships a part table in its jar.
- There is no server add-on. Servers and bots read courses and leaderboards by course id through
  `PropwashApi.race()`, or straight from the JSON files in the world folder.
- Mixins into internals are still allowed at your own risk; internals change between releases without notice.
