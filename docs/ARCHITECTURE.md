# Propwash FPV — Architektur

Minecraft 26.3, Fabric + NeoForge aus einer Codebasis (MultiLoader-Template), Java 25. Mod-ID `propwash`,
Paket `de.lardtom.propwash`. Vollständig eigenständig — kein Code, keine Konstanten, Formate oder Assets aus
anderen FPV-Mods.

## Module (Gradle)

| Modul | Inhalt |
|---|---|
| `common` | ~90 % des Codes: Spiellogik, Physik, UI, Rendering, Netzwerk-Payloads, Mixins, Assets |
| `fabric` | Einstieg + dünne Adapter (Registrierung, Events, Keybinds, HUD, Payload-Registrierung, Config-Pfad) |
| `neoforge` | dasselbe für NeoForge |

Plattform-Unterschiede laufen über Service-Interfaces in `de.lardtom.propwash.platform` (ServiceLoader,
`META-INF/services`). Kein Architectury.

## Quell-Sets

| Quell-Set | Inhalt | Im Release-Jar |
|---|---|---|
| `common/src/main` (+ `fabric/src/main`, `neoforge/src/main`) | die Mod | ja |
| `common/src/devtest` | Selbsttest (`dev.SelfTest`, Runner, alle `*SelfTests`), Start-/Server-Rauchtest, Bildschirmfoto-Rundgang, Beispiel-Addon der Parts-API (`dev.example`, Ressourcen in `common/src/devtest/resources`) | nein |
| `common/src/devtestMod`, `fabric/src/devtestMod`, `neoforge/src/devtestMod` | Hilfs-Mod `propwash_devtest` (nur Ressourcen): meldet `propwash.dev.mixins.json` an, damit Mixins aus `devtest` (`mixin/dev`) in Selbsttest und Rauchtest greifen – Testfenster ohne Mausgriff und Fokus (`docs/testing.md`) | nein |
| `common/src/test` | JUnit-Tests (reines Java, kein Spielstart) | nein |

Die Loader bauen `devtest` als eigenes Quell-Set gegen `main` (gleiche Pakete, deshalb Zugriff auf paketinterne
Klassen). Fabric hängt es nur an die Runs `runClientSelftest`, `runClientSmoke` und `runServerSmoke`; NeoForge führt es
als zweites Quell-Set der Mod in allen Dev-Runs, ohne Property bleibt es dort inaktiv. Der Mod-Code kennt
keine Klasse aus `devtest`: `core.DevExtensions` lädt `dev.DevBootstrap` (Common-`init()`) und
`dev.client.DevClientBootstrap` (Ende von `PropwashClient.init()`) per Reflection, falls vorhanden; im Release-Jar fehlen
sie, dann passiert nichts.

## Pakete in `common`

Maßgeblich für Einstiege, Verträge und Besitzer ist `docs/FOUNDATION.md`; Code-Regeln stehen in `docs/CONVENTIONS.md`.
Jedes Modul hat einen Common-Einstieg (`<modul>.<Name>Module`, Register, SoundEvents, Payloads) und einen Client-Einstieg
(`<modul>.client.<Name>Client`), auch wenn seine Logik nur clientseitig läuft. Die Spalte „Seite“ sagt, wo die Logik läuft.

| Paket | Zweck | Seite |
|---|---|---|
| `build` | Bauteile (Rahmen, Stack, Motor, Prop, Akku, Video, optionales Zubehör: Action-Cams und LED-Streifen samt Halterungen) und `DroneBuild` + Presets; seit 0.4.0 registerbasiert (`PartIndex`, eingebaute Teile als Konstanten, fremde aus Addons) | beide |
| `api` | Öffentliche, versionierte API für Addons (`PropwashApi`, Bauteil-Definitionen, Baupläne, Import, Events, `api.client`-Erweiterungen); Vertrag `docs/design/parts-api.md`, Handbuch für Addon-Entwickler `docs/api/addon-guide.md` | beide/Client |
| `apiimpl` | Implementierung der API-Fassade (`BuildAccessImpl`, `ClientApiImpl`), Erweiterungs-Register (Werkbank, Kreativ-Tab, Presets), Import-Netzwerk (`propwash:api_import`/`api_import_result` mit Ratenbegrenzung) | beide/Client |
| `parts` | Register-Interna der Parts-API: JSON-Bauteile aus Mod-Ressourcen, Items fremder Teile, Handshake der Bauteil-Liste | beide/Client |
| `sim` | Flugmodell + Flight-Controller, reines Java (JOML), keine Minecraft-Imports | Client |
| `core` | Gemeinsame Daten (Codecs, Data-Components, `FlightTune`), Events (`core.event`, `core.client.ClientBus`), `ExtraCollider`, orientierte Kollisionsboxen (`OrientedBox`, `OrientedSweep`, reines Java), Core-Config | beide/Client |
| `registry` | Nur die Registrierungs-Abstraktion (`PropwashRegistries`, `RegHolder`, `client.ClientRegistrar`). Die Registereinträge selbst stehen in einer Klasse des jeweiligen Moduls (z. B. `DroneModule.DRONE`, `RaceModule.GATE`) | beide/Client |
| `join` | Beitritts-Prüfung: Add-on-Liste aus den Mod-Metadaten (`LocalJoinInfo`), Bericht je Add-on (`JoinReport`, reines Java), Texte für Trennung, Log und „Details kopieren“ (`JoinText`), Ablauf auf dem Server (`JoinCheck`) und Client-Übersicht (`client.JoinMismatchScreen`); läuft in der Konfigurationsphase vor dem Registerabgleich der Loader, `docs/design/join-check.md`; Add-ons nur beim Spieler sind für die Sitzung inaktiv statt Grund zur Trennung (Rechnung `ClientOnlyAddons`, Sitzung `AddonSession` mit dem Filter `build.PartAvailability`, Hinweis und Schutz der Kreativ-Pakete in `client.JoinClient`, `docs/design/client-only-addons.md`) | beide/Client |
| `network` | Payload-Abstraktion (`PropwashNet`, `client.ClientNet`) | beide/Client |
| `content` | Teil-Items (inkl. aller `part.propwash.*`-Texte), Drohnen-Item, Brillen, Werkbank (Block, Menü, Screen), Creative-Tab | beide/Client |
| `drone` | `QuadcopterEntity` (inkl. Item ↔ Entity: `loadFromStack`/`toStack`), Pilot-Session (Client), Kamera-Pose (Mixin auf `Camera#alignWithEntity`), FPV-Auge nie im Block (`drone.client.EyeClearance`/`FpvEyeGuard` in `Camera#update`, Nahebene bei Bedarf näher, `docs/design/fpv-eye-clearance.md`), Hand im FPV ausblenden, Pilotenkörper in der Welt sichtbar halten (Kopf im FPV ruhig und leicht gesenkt, `PilotHeads`), Pilotenkörper bleibt beim Steuern ein normaler Spieler (Schwerkraft, Schaden, Rückstoß; Eingaben gehen nicht an den Körper, Lage geht an den Server; Treffer-Hinweis im FPV `drone.client.PilotBodyHud`, Logik rein Java in `drone.PilotBodyAlert`/`PilotBodyRules`; Tod = Funkverlust fürs Failsafe; Welt-Regel `propwash:invulnerable_pilots`; Mixins `LocalPlayerMixin`, `ServerPlayerMixin`; `docs/design/pilot-body.md`), Funke in der dritten Person beidhändig und mittig vor dem Körper halten (`RemoteHoldPose`, Mixins auf `HumanoidModel#setupAnim`, `ItemInHandLayer#submitArmWithItem` und `AvatarRenderState`), Propwash-Staub unter tief fliegenden Drohnen (`drone.client.dust`, `docs/design/propwash-dust.md`), Drohnen-Trails hinter fliegenden Drohnen (reines Java in `drone.trail`, Zeichnen und Schalter in `drone.client.trail`, Welt-Regel `propwash:drone_trails`, `docs/design/drone-trails.md`), LED-Einstellungen und -Modi des LED-Streifens im Tuning (`drone.led`, `docs/design/led-strip.md`), Zustand des Funkenschalters „LEDs aus“ (`QuadcopterEntity#ledsOff`, synchronisiert), Empfänger-Bindung an den letzten Piloten, die über Werkbank, Aufheben und Neustart erhalten bleibt, und Prüfung der getragenen Brille beim Binden, beim Start und im Flug (passt sie zum Videosystem: FPV, keine Brille: Sichtflug, falsche Brille: abgelehnt bzw. kein Bild; reines Java in `drone.PilotRules` und `drone.bind`, `docs/design/receiver-binding.md`), Entity-Tags für Server-Admins (`pw_owner_<Name>` beim Platzieren, `pw_pilot_<Name>` beim Flugstart, `pw_flying` und `pw_autopilot` als Spiegel des Zustands, nach jedem Laden neu; reines Java in `drone.tag.DroneTagRules`, `docs/design/drone-tags.md`), Payloads | beide/Client |
| `race` | Gate-Entities mit Kollisionsteilen, Gate-Items, Kurs-Elemente (Wendeflagge, Slalomstange, Tauchwürfel), Freiform-Gates (Fläche aus Blöcken, `docs/design/freeform-gate.md`), Redstone-Signal beim Durchflug (`GateSignals`, Mixin `BlockStateBaseMixin` auf die Signal-Abfrage, `docs/design/gate-redstone.md`), Kurs-Werkzeug, Rennlogik, Zeitmessung, Geisterspuren (`GhostTrack`/`GhostCodec`), Bestenliste als Weltdaten (`RaceRecords`, `RecordGhosts`), Startaufstellung und Startampel (`RaceLobby`, `StartLights`), Bestenlisten-Fenster, Rennkurse in der Welt (Strukturtyp, Übernahme der Gates als Kurs, Tafel; `race.world`, `docs/design/world-courses.md`) | beide/Client |
| `bando` | Bandos: verlassene Freestyle-Spots als Strukturen `propwash:bando/<familie>` (Tag `#propwash:bandos`, Strukturset `propwash:bandos` mit der Platzierung der Rennkurse); Bauplan, Bausteine, Varianten, Paletten, Platzwahl und Prüfungen als reines Java (`bando.plan`), Strukturtyp, Teil und Blockzustände (`BandoStructure`, `BandoPiece`, `BandoBlocks`), Flugschul-Szene (`BandoScene`, angemeldet in `bando.client.BandoClient`), `docs/design/bandos.md` | beide/Client |
| `video` | Videoverbindung (Signal, Latenz, Störungen), FOV im FPV aus der Kamera des Videomoduls (Mixin auf `Camera#calculateFov`, `video.FovMath`), Bildformat 16:9/4:3 mit Rändern, OSD und Effekten im 4:3-Bereich (`video.ImageFormat`, `video.ImageArea`, `video.client.FpvImage`, `docs/design/image-format.md`), Post-Effekte, Posen-Puffer, FPV-Scheinwerfer des LED-Streifens (Tiefen-Pass vor der Hand, NaN-sicher mit Bildprüfung `video.HeadlightCheck`, Ersatz-Aufhellung im Overlay bei Shaderpacks oder kaputtem Bild, persönlich abschaltbar), Action-Cam-Aufnahme (eigener H.264- und AAC-Encoder und MP4-Container in `video.record`, Bildpuffer außerhalb des Heaps, Messwerte und Engpass-Erkennung `video.record.PipelineStats`/`RecordingDiagnosis`, optionaler Hardware-Encoder über ein installiertes ffmpeg in `video.record.external` (Suche mit Herkunft `FfmpegLocator`, Anmelde-Shell `LoginShell`, Prüfung `HardwareEncoders`; im Tab sichtbar mit eigenem Pfad und „Testen“), GPU-Aufnahme, Aufnahmen-Liste mit Vorschaubildern und Tab in `video.client.record`, `docs/design/action-cam-recording.md`), Kamerapfad der Action-Cam (Flashback) für die Replay-Mod Flashback (reines Java in `video.record.path`, Sitzung `video.client.record.FlashbackCam`, `docs/design/flashback-cam.md`), Videokanäle der Sender und Zuschauen (`video.VideoChannels`, `video.SpectateServer`, `video.client.SpectateSession`, Tab „Videokanal“), Abschattung der Videostrecke durch Hindernisse nach Material und Beugung bei 5,8 GHz (reines Java in `video.link`, Einordnung der Blöcke in `video.client.ClientBlockLoss`, `docs/design/vtx-obstruction.md`) | beide/Client |
| `relay` | Signal-Repeater: Block an Boden, Wand und Decke mit Block-Entity, Verzeichnis je Welt ohne Scans (`RepeaterIndex`), beste Kette Pilot → Repeater → Drohne als reines Java (`RelayRules`, `RelayPaths`, `RelayLinks`, `RelayCandidates`), Teilstrecken fürs Bild mit der Abschattung der Videostrecke (`relay.client.RelayVideo` in `VideoState`) und für den Funk nach Entfernung (`relay.client.RelayControl` in `PilotSession`), Welt-Regel `propwash:signal_repeaters`, `docs/design/signal-repeater.md` | beide/Client |
| `input` | Funke/Gamepad (SDL3 bzw. GLFW, je nach 26.3-Stand), Kanal-Assistent mit animierter Funke, die jeden Schritt vormacht (Abläufe als reines Java in `input.RemoteDemo`, Bild-im-Bild in `input.client.screen.remote`), Rückfrage, wenn die Gas-Richtung der Startlage oder der Richtung im Profil widerspricht, Kalibrierung, Konfigurator-Tabs Empfänger, Modi und Tasten, Einrichtungs-Frage für neue Geräte (einmal erkennen, einmal einrichten), macOS-Erkennung ohne Apples GameController-Framework (`SDL_JOYSTICK_MFI`, `docs/input-troubleshooting.md`), Tasten, die sich eine Belegung teilen dürfen (`KeyDefaults.SHARED`), eigene Befehle auf Schaltern und Tasten (`SwitchFunction.COMMAND_1…8`, Prüfung und Flanken als reines Java in `input.SwitchCommands`/`input.CommandTrigger`, Senden in `input.client.SwitchCommandRunner`, Seite „Befehle“ im Modi-Tab, `docs/design/switch-commands.md`), Eingabequelle „Maus + Tastatur“ (Maus → Knüppel als reines Java in `input.MouseSticks`, SDL-Mausereignisse mit Zeitstempel in `input.client.MouseCapture`, virtuelle Knüppel mit Testfläche und Assistenten-Schritt in `input.client.screen`, `docs/design/mouse-control.md`, Spieler: `docs/mouse-control.md`) | Client |
| `ui` | Konfigurator (Betaflight-Stil; die Tuning-Daten gehören drone, Zugriff über `drone.client.TuneAccess`), Theme, gemeinsame Widgets; Zahlen tippen in allen Zahlenfeldern und Reglern (Eingabe als reines Java in `ui.NumberEntry`, Anzeige-Skala der Regler in `ui.SliderScale`, gemeinsame Regler-Eingabe `ui.client.widget.SliderTyping`, SDL-Texteingabe je Feld), Suche über alle Tabs mit Vorschlägen (Rangfolge und Synonyme als reines Java in `ui.SettingsSearch`, Index aus den Widgets der Tabs in `ui.client.search`), eigene Rates- und PID-Presets mit Code `PWT1-` (`ui.TunePresets`, `ui.client.TunePresetStore`); `docs/design/config-input.md` | Client |
| `osd` | OSD im FPV (Elemente, Vorlagen, Editor-Tab, Deckkraft-Renderer), Funk-HUD im Sichtflug (`osd.client.RadioHud`) und das austauschbare OSD-Layout für Addons (`osd.OsdLayoutCodec`) | beide/Client |
| `rescue` | Homepoint, Return to Home, Hinderniserkennung (Sicherheitshöhe aus dem ganzen Weg, lokaler Wegplaner `RoutePlanner`, Landeplatz-Prüfung ohne Wasser, Lava und Laub), GPS Rescue ohne Pilot: steigt der Pilot im Heimflug aus, fliegt der Server die Drohne mit demselben Flugmodell weiter (`AutonomousReturns`, `ServerReturnFlight`, Plan `ReturnPlan` an der Entity, Welt-Regel `propwash:autonomous_rescue`, `docs/design/autonomous-rescue.md`) | beide/Client |
| `trick` | Freestyle-Tricks: Erkennung aus dem Flugmodell (Manöver-Fenster, Drehstriche, Gruppierung nach höchster Wertung), Repertoire von 59 Tricks in neun Familien (`TrickFamily`, Varianten über `TrickType.variantOf()`) samt Kombi-Tricks (Sbang-Kombo, Shinobi Flip, Immelmatt) und 3D-Tricks, eigene Automaten für Eject, Hammerhead, Maverick Loop und Knife-Edge-Varianten, Punkte, Kombos und Ketten, Suche im Katalog (`TrickCatalog`, alles reines Java), Tracker am Frame, Abstands-Strahlen, Tab „Freestyle“ nach Familien mit Suche, Filter und Erklärungen (`trick.client`), `docs/design/freestyle-tricks.md` | Client |
| `audio` | Motor-Synthesizer (Echtzeit-Stream, Abgriff für den Ton der Action-Cam über `audio.client.MotorAudioTap`), Hörweite und Abstandspegel für alle außer dem FPV-Piloten aus der Schallleistung der Drohne (`audio.SoundReach`, `docs/design/drone-sound.md`), Pieptöne, alle `SoundEvent`s (registriert in `audio.AudioModule`) und `assets/propwash/sounds.json` | beide/Client |
| `blackbox` | Flugschreiber im Betaflight-Format (`.bbl`): lesender Abgriff an der Regelschleife (`sim.FcListener`/`FcSample`), Quantisierung, Kopf mit dem Tune unter Betaflight-Namen, I/P/S/G/H/E-Frames (`BlackboxEncoder`, reines Java), Ringpuffer ohne Allokation und Schreib-Thread mit Speichergrenze (`BlackboxRecorder`, `BlackboxWriter`); Tab „Blackbox“, Schalterfunktion und Taste (`blackbox.client`), `docs/design/blackbox.md` | Client |
| `server` | Chunk-Streaming um die Drohne (Sichtanker, Chunk-Tickets, Mixins aus `network-entity.md` §4), Server-Config (per S2C an die Clients), Rechte und Grenzen (`server.ServerRules`), Welt-Regeln als Gamerules (`server.WorldRules`, Katalog in `server.rules`, Tab „Welt-Regeln“, `docs/design/world-rules.md`) | beide |
| `config` | `JsonConfig` (generisch); die Schemas definiert jedes Modul in seinem eigenen Paket | beide |
| `mixin.<modul>` | **alle** Mixins, je Modul ein Unterpaket mit eigener Config `propwash.<modul>.mixins.json` (Client-Mixins in `mixin.<modul>.client`) | beide/Client |
| `platform` | Service-Interfaces für Loader-Unterschiede (`Services`, `client.ClientServices`) | beide/Client |
| `dev` (nur Quell-Set `devtest`) | Selbsttest, Rauchtests und Bildschirmfoto-Rundgang, Beispiel-Addon `dev.example` (Namensraum `propwash_example`, benutzt nur die API); nicht im Release-Jar | beide/Client |
| `compat.<mod>` | optionale Integrationen: `compat.iris` (common, nur Reflection: Shaderpack-Status, Schattenpass, Pipeline-Zuordnung; was Propwash mit Shaderpacks anders zeichnet, steht in `docs/design/shader-packs.md`), `compat.modmenu` (nur Fabric-Modul, Mod Menu als `compileOnly`), `compat.lambdynamiclights` (common, API als `compileOnly`: LED-Streifen als echte Lichtquelle, `docs/design/led-strip.md`), `compat.flashback` (common, nur Reflection: Kamerapfad als Szene in Flashback, Drohne in der Wiederholung ausblenden, `docs/design/flashback-cam.md`) | Client |
| `interact` | Drohnen treffen Lebewesen (Spielregel `propwash:drone_hits_entities`, Treffer aus Masse × Tempo, Schadensart `propwash:drone_strike`) und der Greifer sammelt Items (Fracht, Tragkraft, Abwurf, Last über die Zubehörmasse im Flugmodell); reine Rechnung ohne Minecraft-Typen in `StrikeMath`, `StrikeRules`, `CargoPlan`, `ItemWeights`, `GripperSpec`, `CargoMass`; `docs/design/drone-interaction.md`; Drohnen zerbrechen Glas (Welt-Regel `propwash:drones_break_blocks`, Block-Tag `propwash:drone_breakable`, Energie gegen Schwelle aus Härte und Dicke in `BreakMath`/`BreakRules`, Vorhersage `interact.client.PilotBreaks`, Server `DroneBreaks` mit den Abbau-Ereignissen der Loader über `IPlatformHelper#allowBlockBreak`; `docs/design/drone-break-blocks.md`) | beide/Client |
| `creative` | Kreativ-Drohne und Drohnen-Editor: freie Flugmodell-Werte am Bauplan (`CreativeSpec`, Katalog `CreativeParam`), Flug-Bauplan über die Teile-Daten (`CreativeFlight`), Grenzen, Hinweise, Teilen-Code und Entwurf (reines Java bis auf Codecs und Teile-JSON), Payload `propwash:creative_edit` mit Prüfung am Server (`CreativeEdits`), Flugschul-Szene `creative_drone`; Tab „Drohnen-Editor“ und eigene Vorlagen in `creative.client`; `docs/design/creative-drone.md` | beide/Client |
| `guide` | Flugschule: Szenen-Modell und DSL (`guide.scene`, reines Java), sechzehn eingebaute Szenen samt Baukasten (`guide.scenes`; die Szene „Bandos“ meldet `bando.client.BandoClient` an, die Szene „Kreativ-Drohne“ `creative.client.CreativeClient`, nur im Kreativmodus sichtbar), Szenen von Addons mit eigenem Namensraum (`ns:name`, Texte `<ns>.guide.scene.…`, optional mit Sichtbarkeitsbedingung), Szenen-Player, Übersicht, Konfigurator-Tab, Halten-zum-Öffnen in Inventaren, Bild-im-Bild-Renderer für die 3D-Diorama (`guide.client`), ruhiges Hinweis-Layout mit Gedächtnis über die Bilder (einmal setzen, nur bei Konflikten langsam umziehen, bewegten Ankern aus dem Weg gehen; reines Java in `guide.layout`); Flugtraining zum Selberfliegen: Lehrplan, Auswertung der Versuche, Platzprüfung und Fortschritt als reines Java (`guide.training`), Sitzung, Hologramme, HUD und Fenster (`guide.client.training`, `docs/design/flight-training.md`) | beide/Client |
| `client` | `PropwashClient` (Client-Einstieg), `client.render.DronePreview` (gehört drone) | Client |

## Datenfluss beim Fliegen

1. Spieler bindet die Funke per Klick an die platzierte Drohne und klickt mit der Funke in die Luft → Server vergibt die
   Pilotenrolle (drone, fragt `ServerRules.mayPilot`, Modus FPV mit getragener passender Brille, Sichtflug ohne Brille,
   mit falscher Brille keine Verbindung; `QuadcopterEntity.PILOT_CHANGED` → server setzt den Sichtanker und lädt Chunks um die
   Drohne). Im Sichtflug bleibt die Kamera beim Spieler, sein Blick folgt der Drohne, ein Funk-HUD zeigt die Telemetrie.
2. Client: `PilotSession` liest pro Frame die Funke (`input`), steppt `sim` mit Frame-dt (intern fester Takt),
   bewegt die Drohnen-Entity mit Blockkollision, meldet Treffer an `sim` zurück.
3. Client sendet Zustand (Position, Orientierung, armed, Motorleistung, Akku) an den Server; der Server prüft
   Plausibilität (inkl. `ServerRules`-Grenzen) und verteilt an andere Spieler.
4. Kamera: eigene Kamera-Pose (drone, Kamera-Mixin) aus dem Posen-Puffer (`video`, Latenz je Videosystem abzüglich des
   eigenen Darstellungswegs des Spiels – gemessen vom Kamerabild bis `present` plus halber Bildaufbau, `video.DisplayLatency` –,
   gerechnet vom Zeitstempel des Physik-Frames, nicht von der Uhrzeit beim Kamera-Update; jede Pose trägt die Zeit ihres
   Bilds, bis dorthin rechnet die Sim sie über den Rest unter einem Substep hoch), Roll aus der Orientierung, Uptilt der Drohne
   (`drone.CameraMount`, im Tuning der Drohne, im Wartungsmenü einstellbar), feine Kamera-Vibration nur im Bild
   (`video.CameraShake`, `drone.client.FpvShake`, `docs/design/fpv-feel.md`), danach der Schutz gegen Blick durch Blöcke
   (Sichtpyramide bis zur Nahebene frei, sonst erst Nahebene näher, dann Auge heraus; drone, `FpvEyeGuard`); FOV von der Kamera im Videomodul der Drohne
   (Kreativ-Brille optional eigenes) und Video-Effekt als Post-Pass (beides video).
5. OSD, Rennzeit und RTH lesen nur aus `PilotSession`/`sim`-Telemetrie.

## Spielinhalte

- **Werkbank** (content, samt Screen und allen Widgets): Slots in Drohnen-Draufsicht (Rahmen, Stack, 4 Motoren, 4 Props, Video, Akku, dazu
  optional Zubehör oben – Action-Cam – und unten – LED-Streifen; `docs/FOUNDATION.md` §15 „Zubehör“), 3D-Vorschau,
  Werte (Trockengewicht, Abfluggewicht mit Akku, Schub/Gewicht, Schwebegas, Topspeed, Flugzeit; Details per Maus),
  Fehler/Warnungen aus `DroneBuild.problems()/warnings()`.
  Fertige Drohne entnehmen; bestehende Drohne hineinlegen zerlegt sie wieder.
- **Lackierung** (content, drone, drone-render): Knopf „Lackierung“ in der Werkbank; jedes Teil, jeder Prop einzeln, Farbstoffe,
  Oberflächen, Hex-Farben, Vorlagen, Kopieren/Einfügen; kostet im Überlebensmodus Farbstoffe, haftet am Rahmen, synchronisiert
  (`docs/FOUNDATION.md` §15 „Lackierung“).
- **Drohnen-Item** (content) trägt `DroneBuild`, Akkustand und `FlightTune` als Data Components; platzieren = Entity
  (`QuadcopterEntity#loadFromStack`), aufheben = Item (`QuadcopterEntity#toStack`). Überall, wo das Item erscheint (Inventar,
  Hand, Boden, Rahmen, Kreativ-Tab, Werkbank-Ausgabe), zeichnet der Sonder-Renderer `propwash:drone_build`
  (`drone.client.render.DroneItemRenderer`) den gespeicherten Bauplan samt Lackierung und Prop-Schäden, je Bauplan
  zwischengespeichert und auf Slot-Größe gebracht. Im Item-Rahmen hängt die Drohne wie ein Bild flach an der Wand
  (Oberseite zum Betrachter, Nase oben, Fußkreis füllt den Rahmen; Rahmen-Teile und Props ebenso, im Regal geneigt;
  `docs/ASSETS.md` „Item-Rahmen und Regal“). Am Boden liegt sie nach ihrer Größenklasse 0,3 (Whoop) bis 0,5 Block
  (X-Class) groß; jedes Item bleibt in jeder Ansicht unter den Grenzen aus `content.client.ItemSizeRule`
  (`docs/ASSETS.md` „Größenregel der Items“).
- **Brillen**: analog, digital, creative (Kopf-Slot). Brille muss zum Videosystem passen (`VideoSystem.receives`) und an
  die Drohne gebunden sein, sonst Sichtflug.
- **Funke** (`propwash:remote`, drone): EdgeTX-artiger Handsender mit bewegten Knüppeln, Schaltern und Tasten
  (Special-Item-Renderer `propwash:remote_gimbals`), bindet sich an eine Drohne, startet und beendet das Fliegen.
- **Scharf-Winkel** (drone, ui, sim): je Drohne im Tab „Konfiguration“ wie `small_angle` in Betaflight (0–180°, Standard
  25°, 180° = scharf in jeder Lage, auch in der Luft), Tuning-Schlüssel `pid.armMaxAngleDeg`.
- **Akkus**: jeder Akku ist ein Item mit eigenem Ladestand (Ladebalken), passt nur zu bestimmten Rahmen (`build.BatteryFit`);
  Tausch per Schleichen + Rechtsklick auf die Drohne (Wartungsmenü oder direkt mit Akku in der Hand).
- **Bildformat** (video, osd): persönlich 16:9 (füllt das Fenster, Standard), 4:3 (schwarze Ränder, gleiches
  horizontales Sichtfeld, oben und unten mehr Bild, OSD im 4:3-Bild) oder Auto (analog 4:3, digital 16:9); Tab „Video“.
- **Kamera**: das FPV-Sichtfeld kommt von der Kamera im Videomodul (`camera_fov_deg`, horizontal), der Kamerawinkel
  gehört zur Drohne: im Wartungsmenü einstellbar im Bereich des Kamerahalters der Rahmenklasse (`drone.CameraMount`),
  gespeichert im Tuning der Drohne, beim Zerlegen am Rahmen; gilt mit jeder Brille, beim Zuschauen und in Aufnahmen.
- **Ladegerät** (`propwash:battery_charger`, content): 4 Schächte, lädt über Zeit (Server-Config, Redstone schneller),
  Zustand am Block sichtbar. Nur hier laden sich Akkus.
- **Shift-Tooltips**: alle Propwash-Items erklären sich mit gedrückter Shift-Taste (`content.ItemHelp`).
- **Überlebensmodus** (content, `docs/design/survival.md`): alles außer Kreativ-Brille und Kreativ-Flugcontroller ist herstellbar; Zwischenteile
  (Carbonplatte, Kupferspule, Platine, LiPo-Zelle, HD-Videochip) verbinden Vanilla-Rohstoffe mit den Bauteilen, das Rezeptbuch
  schaltet Rezepte über Fortschritte frei. Teile, Akkus, Zwischenteile und selten eine fertige Whoop-Drohne liegen in
  Truhen passender Strukturen (`content.ChestLoot`; Fabric per Loot-Event, NeoForge per Global Loot Modifier).
- **Props reparieren** (content, `docs/design/survival.md` „Props reparieren“): zwei beschädigte Props derselben Sorte
  ergeben im Crafting-Raster (Sonderrezept `propwash:special/prop_repair`) oder am Schleifstein (`mixin.content`) einen
  Prop mit addiertem Zustand plus 5 %, höchstens neu (`build.PropRepair`); gilt auch für Props von Addons.
- **Bodenkontakt** (drone, sim; `docs/design/ground-contact.md`): Die Kollisionsbox der Drohne ist fest und aufrecht.
  Der Renderer stellt das Modell am Boden auf seinen tiefsten Punkt (`drone.client.render.GroundContact`, Abstand zur
  Fläche aus `QuadcopterEntity.groundGap`), unscharfe Drohnen fallen im Flugmodell flach (`sim.GroundRest`), lose
  Drohnen am Server (`drone.GroundSettle`), und auf Blöcken im Tag `propwash:drone_surface` (Schnee, Seelensand,
  Schlamm, Honig) zählt die sichtbare Form als Aufstandsfläche (`drone.SoftGround`).
- **Gates**: Entity mit Kollisionsteilen (Pfosten/Balken), mehrere Größen, am Boden, an Wänden (Tauchtor, seitlich,
  Fenster) und unter Decken (hängend, liegend), mit Vorschau beim Zielen und der Taste „Gate drehen“ in Schritten von 90,
  45, 15 oder 5° (`docs/design/gate-mounting.md`); Schleichen + Rechtsklick startet „Gate einstellen“ als Einstell-Modus im
  Spiel ohne Fenster (Panel als HUD, Mausrad, Alt für freie Maus, `race.client.GateEditor`,
  `docs/design/gate-edit-mode.md`): frei gieren, nicken und rollen, Öffnung 1–16 Blöcke, Live-Vorschau, Prüfung beim
  Server; schräge Teile kollidieren als OBB, auch im
  Flugmodell mit der echten Normale (`docs/design/gate-free-transform.md`); das nächste Gate des eigenen Rennens
  leuchtet grün, die übrigen weiß (clientseitig pro Spieler). Kurs-Werkzeug nummeriert Gates. Kurs-Gates tragen ihre
  Nummer auf einer Tafel, Leuchtpfeile am Boden vor der Einflugseite und rote Markierungen hinten; Gate 1 ist das
  Start-/Ziel-Gate (kariert, Banner „START · ZIEL“). Im gestarteten Rennen zeigt eine Ideallinie den Weg durch die
  nächsten Gates (`docs/design/race-line.md`). Jeder gültige Durchflug (auch im freien Flug) gibt einen Redstone-Puls
  in den Block, auf dem das Gate steht oder an dem es hängt (liegendes Gate: Randblock unter dem unteren Balken,
  Freiform-Gate: Randblock unter der Öffnung; heruntergezählt am Ende des Server-Ticks); an/aus,
  2–100 Ticks und „Nur im Rennen“ je Gate auf der Seite „Redstone“ von „Gate einstellen“ (`race.GateSignals`,
  `docs/design/gate-redstone.md`).
- **Kurs-Elemente** (race, `docs/design/course-elements.md`): Wendeflagge (links oder rechts herum), Slalomstange (Seite
  links oder rechts, das Kurs-Werkzeug wechselt sie beim Anhängen nacheinander) und Tauchwürfel (oben hinein, durch die
  eingestellte Seite hinaus) sind Gate-Entities mit Art und Variante, stehen auf dem Boden, gehören wie Gates zu Kursen
  und zählen in Stufen (`CourseElement`, `Passage`, `GateGeometry#stages`), auf dem Server als Prüfpunkte
  (`CourseCheckpoints`); Bodenpfeile, Nummern-Tafel und Ideallinie über Wegpunkte (`race.client.CourseElementRenderer`).
- **Gate-Stange** (race, `docs/design/gate-pole.md`): eine einzelne Gate-Seite (Art `GATE_POLE` der Gate-Entity, kein
  Kurs-Element) mit Profil, Verbindern und LED-Leisten der Gates; steht am Boden, ragt aus Wänden, hängt von Decken,
  sitzt per Rechtsklick auf der oberen Ecke stehender Gates (3-Wege-Verbinder) und wird zu Türmen bis vier Stangen
  verlängert (Sitzplätze als reines Java in `race.GatePoles`, Träger und Abnehmen in `race.GatePoleLinks`); Länge 1–16,
  16 LED-Farben, Rendern aus Gate-Teilen in `race.client.GatePoleRenderer`.
- **Freiform-Gate** (race, `docs/design/freeform-gate.md`): Gate ohne Rahmen – Rechtsklick auf den Rand einer Öffnung
  (Gang, Höhleneingang, Fenster, Schacht), eine Flutfüllung in der achsparallelen Ebene (4er-Nachbarschaft, Rand =
  kollidierende Blöcke und Flüssigkeiten) ergibt die Fläche, höchstens so groß wie die Welt-Regel
  `propwash:freeform_gate_max_size` (Standard 20 × 20). Gespeichert als Bitmaske relativ zum Anker (`race.FreeformRegion`,
  reines Java), gezählt nur in gespeicherten Feldern (Client und Server dieselbe `Passage`), darf Kursstart sein; Leucht-
  umriss aus LED-Leisten, Warnung bei verändertem Rand, „Neu abtasten“ beim Einstellen (`race.client.FreeformGateRenderer`,
  `GateEditor`).
- **Rennkurse in der Welt** (race, `docs/design/world-courses.md`): selten findet man fertige Kurse – Feldslalom,
  Canyon-Run, Waldslalom und die seltenere Drohnen-Arena – mit nummerierten Gates, gewachster Tafel (Name, Kursnummer,
  Rekord) und einer bescheidenen Truhe; `/locate structure #propwash:world_courses`, abschaltbar mit der Server-Regel
  `worldCourses`.
- **Bandos** (bando, `docs/design/bandos.md`): selten stehen verlassene Bauwerke in der Welt, je Biom-Familie eigene
  Varianten (Scheune, Lagerhalle, Fabrik, Wasserturm, Raffinerie, eingestürzte Halle, Betonskelett, Steg, Bohrinsel,
  Förderanlage, versunkene Lagerhalle), jedes Mal prozedural aus Bausteinen gebaut und ins Gelände gesetzt (Boden bündig,
  Fundamente am Hang, nichts auf halben Blöcken), ohne Gates; ab und zu eine Truhe mit
  gebrauchten Teilen (`propwash:chests/bando`). Folgen „Bauwerke generieren“ und Datenpaketen wie Vanilla-Strukturen;
  `/locate structure #propwash:bandos`.
- **Runden je Kurs und Kurse bearbeiten** (race, `docs/design/course-edit.md`): jeder Kurs trägt Name und Rundenzahl
  (`CourseLaps`: −1 = Einstellung des Piloten, 0 = endlos), neue Kurse übernehmen die Standard-Rundenzahl ihres
  Erbauers. Fenster „Kurs bearbeiten“ hinter der Bestenliste (`race.client.CourseEditScreen`): umbenennen, Rundenzahl,
  Gates und Elemente verschieben, Start und Ziel verlegen, herausnehmen, Kurs-Werkzeug auf den Kurs stellen, Kurs
  löschen. Der Server prüft jede Änderung (`CourseEdits`, Rechte in `CourseAccess`, Reihenfolge-Regeln in
  `CourseOrder`, alles reines Java); Layout-Änderungen archivieren Zeiten und Geister, Rennzeiten zählen je Rundenzahl.
- **Geister-Rennen und Bestenliste** (race, FOUNDATION §15 „Geister-Rennen“): persönliche Bestrunde je Kurs und
  Drohnenklasse als Geist (Client-Datei), Server-Rekord je Klasse als goldener Geist, Split-Abstand zum Geist an jedem
  Gate, der Geist zeigt die Drohne, die die Runde geflogen ist (Bauplan und Lackierung in der Spur, `race.GhostLook`), Bestenliste je Kurs in den Weltdaten (Top 10 mit Klasse und Datum, Theme-Fenster), Startaufstellung mehrerer
  Piloten mit Startampel und Frühstart-Strafe. Mit der Welt-Regel „Runden mit Flughilfe speichern“ eine eigene Wertung
  „Mit Flughilfe“ unter der normalen (Abzeichen und Gründe beim Darüberfahren, nie Rekord), ein eigener violetter Geist
  und „FLUGHILFE“ in der Rennanzeige (`docs/design/assisted-laps.md`).
- **Flugschule** (guide): animierte 3D-Lektionen im eigenen Theme-Screen (Diorama aus Blöcken, Drohne, Pilot, Gates,
  Items; Hinweise mit Zeigelinien, Rahmen, Tasten-/Maus-Symbole; Kamera per Ziehen/Mausrad; Zeitleiste mit Schritten).
  Öffnen: Taste (Standard M) über einem Propwash-Item im Inventar halten, in der Welt drücken (Übersicht) oder
  Konfigurator-Tab „Flugschule“. Neunzehn Szenen: Drohne bauen, Funke binden und Brille aufsetzen, FPV und Sichtflug, Akkus und
  Ladegerät, Gates und Rennen, Gates frei drehen (mit Freiform-Gate), Flaggen, Slalom und Tauchwürfel, Freestyle-Tricks, Videosysteme, Action-Cam, Greifer, Konfigurator (Knüppel, Rates, PID, Modi), GPS Rescue und
  Failsafe, Höhe und Position halten, 3D-Modus, Absturz und Reichweite, Props wechseln und Schäden, Überlebensmodus, Bandos; im Kreativmodus dazu
  Kreativ-Drohne und Drohnen-Editor.
- **Drohnen treffen Lebewesen** (interact, drone, `docs/design/drone-interaction.md`): Mobs und Spieler sind für die
  geflogene Drohne Hindernisse wie Blöcke (Abprall, Absturz, Prop-Strikes, Verschleiß); der Server rechnet Schaden und
  Rückstoß aus Masse × Annäherungstempo (höchstens 5 Herzen), Spieler nur mit PvP, Verursacher ist der Pilot. Spielregel
  `propwash:drone_hits_entities` (Standard an). Keine Blöcke werden verändert.
- **Drohnen zerbrechen Blöcke** (interact, sim, `docs/design/drone-break-blocks.md`): mit der Welt-Regel
  `propwash:drones_break_blocks` (Standard aus) zerbrechen schnelle Drohnen Glas, Glasscheiben und verzierte Töpfe (Tag
  `propwash:drone_breakable`), wenn ½·m·v² entlang der Normalen die Schwelle aus Härte und Dicke erreicht (Scheibe 36 J,
  Glasblock 120 J); der Client holt für Blöcke vor der Drohne vorab eine Freigabe beim Server und sagt den Bruch nur mit
  Freigabe voraus (sonst prallt die Drohne ab wie an einer Wand, auch in Spawn-Schutz und Claims schon beim ersten
  Treffer) und fliegt mit dem Rest-Tempo weiter (`DroneSim#breakThrough`,
  verlorenes Tempo zählt als Aufprall); der Durchbruch ist zu spüren: mitgerissene Scherben teilen den Impuls, der Stoß
  kippt die Drohne, vordere Props verlieren Drehzahl (`sim.BreakImpulse`, `sim.BreakSolver`, Stärke über die Welt-Regel
  `propwash:break_impact_scale`), dazu beim Piloten Kameraschlag (`video.ImpactJolt`), mitfliegende Scherben und Klirren
  (`interact.client.BreakEffects`); der Server prüft wie beim Abbauen durch den Piloten (Spawn-Schutz, Spielmodus,
  Abbau-Ereignisse der Loader, Rahmen von Freiform-Gates) und lehnt sonst mit Grund ab.
- **Greifer** (interact, content, drone-render): Zubehör für die untere Halterung, nimmt im Tiefflug liegende Items auf
  (bis 400 g und 4 Stapel, höchstens die halbe Schubreserve), die Last wirkt über die Zubehörmasse im Flugmodell; Abwurf
  per Taste „Greifer öffnen“, Funkenschalter „Fracht abwerfen“, beim Landen und bei harten Abstürzen.
- **Flugtraining** (guide, `docs/design/flight-training.md`): fünf Kapitel mit zwanzig Lektionen zum Selberfliegen
  (Grundlagen, Vorwärtsflug, Rennen, Freestyle, Fortgeschritten) mit der eigenen Drohne. Der Kurs erscheint als Hologramm
  dort, wo die Drohne gelandet und abgeschaltet steht (nur beim eigenen Client, Platzprüfung im Gelände, keine Änderung an der
  Welt); Ringe über `GateGeometry#crossing`, Runden über `LapTracker`, Tricks über `TrickSession`. Sterne, Bestzeiten und
  Freischaltung je Spieler in `config/propwash/flight_training.json`. Öffnen: Knopf „Flugtraining“ in der Flugschule oder Taste M
  im Flug.
- **3D-Modus** (sim, input, drone, osd, guide, server; `docs/design/mode-3d.md`): umkehrbare Motoren wie mit 3D-ESCs –
  einmal im Tab „Modi“ freischalten, dann auf Funkenschalter oder Taste (Ziffernblock 6) legen. Gasmitte = Nullschub
  (Totzone ±50 µs), darunter drehen die Motoren rückwärts; scharf nur mit Gas in der Mitte, auch kopfüber. Umkehrschub aus
  den Prop-Daten (`reverse_thrust`, normale Props 55 %). Kombinierbar mit Acro, Horizon und Angle; Rettung, Failsafe und
  Höhe/Position halten fliegen ohne 3D. OSD „ACRO 3D“ und Gas −100 … +100; Welt-Regel `propwash:allow_3d_mode`.
- **Höhe und Position halten** (sim, drone, input, osd, ui, guide): Flugmodi ALTHOLD und POSHOLD wie Betaflight 4.6/INAV,
  auf Schalter (Modi-Tab, Assistent) oder Tasten (Ziffernblock 4/5). Beide nivellieren selbst und regeln auf simulierte
  Sensoren (Barometer mit Drift, Bodeneffekt- und Staudruckfehlern; GPS mit Satelliten je nach freiem Himmel, 10 Hz,
  Latenz und Random Walk; Kompassfehler), halten also nie perfekt. Ohne GPS-Fix verweigert POSHOLD (OSD „KEIN GPS“) und
  hält nur die Höhe. Werte im Tab „PID-Tuning“, Modell in `docs/design/flight-model-spec.md` 6.12.
- **GPS Rescue ohne Pilot** (rescue, drone, server; `docs/design/autonomous-rescue.md`): Steigt der Pilot im Heimflug aus
  (Funke benutzen oder weglegen, abmelden, Reichweite), fliegt der Server die Drohne mit demselben Flugmodell und
  Autopiloten weiter und landet sie am Homepoint; geladen bleiben nur die Chunks um die Drohne, unerforschtes Gebiet
  meidet sie, höchstens 8 Flüge je Server und 2 je Spieler; die gebundene Funke lässt sie sofort landen; Welt-Regel
  `propwash:autonomous_rescue`. Der Heimflug steigt gleich auf die Sicherheitshöhe des ganzen Wegs, plant um Bäume und
  Gebäude (`RoutePlanner`) und landet nicht auf Wasser, Lava oder Laub.
- **Bauteil-Verschleiß** (sim, drone, content): Jeder harte Absturz nutzt alle Teile der Drohne ab (Props zuerst, Motoren
  viel länger, Kamera und Rahmen noch länger, Stack und Akku am längsten; `docs/design/flight-model-spec.md` 9.6).
  Leichte Rempler federt ein Prop weg; wie er kaputtgeht, hängt vom Material ab (`sim.PropFailure` aus Steifigkeit und
  Haltbarkeit: PC verbiegt sich, hartes PC und Glasfaser brechen in Stücken, Carbon zerspringt selten und dann ganz;
  `docs/design/realism/prop-wear.md`). Der
  Zustand liegt als `propwash:part_wear` an Drohne und Item, einzelne Teile tragen `propwash:part_condition`. Kaputte
  oder abgenutzte Teile wirken im Flugmodell (Schub, Vibration, kaputter Prop = nicht fliegbar), im Video (Artefakte,
  kaputte Kamera = schwarz) und im OSD (Warnungen, Statistik). Props tauscht man im Feld im Wartungsmenü (Schleichen + Rechtsklick auf die Drohne, Akku und
  vier Props wie von oben), alles andere in der Werkbank; kaputte Teile recycelt das Zerlegen-Feld. Abschalten und Stärke für die ganze Welt
  über die Welt-Regeln `propwash:crash_damage` und `propwash:crash_damage_scale`.
- **Creative-Modus-Einstellungen**: freie Video-/Reichweiten-Einstellungen mit Creative-Brille; im Kreativmodus
  oder mit Creative-Brille gilt keine feste Funkreichweite (`drone.RadioLink.freeRange`), nur die Server-Grenze und die
  Reichweite aus dem Video-Tab. Reichweite, Hindernisse und Bildstörungen der Kreativ-Brille zählen in Rennen nicht als
  Flughilfe, nur eine eigene Latenz unter der des Videosenders (Standard: Latenz vom Videomodul); zählt eine Runde
  nicht, nennt die Rennanzeige den Grund (`race.LapAssist`, `docs/design/signal-repeater.md`).
- **Signal-Repeater** (relay, video, drone, osd; `docs/design/signal-repeater.md`): kleiner Block für Boden, Wand und
  Decke, reicht Bild und Funk 128 Blöcke weit weiter, Ketten bis zu vier Repeatern (Welt-Regel
  `propwash:signal_repeaters`); jede Teilstrecke rechnet mit Entfernung und Abschattung wie die direkte Strecke, die
  beste Kette gewinnt, digital zählt die schwächste Teilstrecke, analog addiert sich das Rauschen. OSD „RPT“ mit Signal
  je Teilstrecke; Runden über Repeater zählen.
- **Welt-Regeln** (server, drone, video, race, sim; `docs/design/world-rules.md`): echte Gamerules `propwash:…` für alle
  Spieler einer Welt – Schwerkraft (0–1000 % in 1-%-Schritten, `SimTuning.gravityScale`; Werkbank und Analyse rechnen immer mit 100 %,
  `docs/design/realism/flight-feel-gravity.md`), Akkulaufzeit (0 = unendlich), Wind 0–3, Turbulenz, Prop-Wash,
  Bodeneffekt, Spannungseinbruch, Hardware-Grenzen, Crash-Schaden und -Stärke, Crash-Toleranz, Absturz entschärft,
  Propellertreffer, unendliche Reichweite analog/digital, die Zahl gleichzeitiger Videosender, Signal-Repeater je Verbindung und „Teile müssen
  zusammenpassen“ (aus = jedes Teil passt zu jedem, `build.PartCompatibility`, `docs/design/part-freedom.md`), „Drohnen zerbrechen Blöcke“ (`docs/design/drone-break-blocks.md`), „Drohnen erzeugen
  neues Gelände“ (aus = Drohnen laden nur erzeugte Chunks, Grenzzaun und OSD „WELTRAND“ am Rand, `server.GeneratedCover`,
  `server.EdgeFence`, `drone.client.EdgeGuard`, `docs/design/world-edge.md`) „Runden mit Flughilfe speichern“ (eigene
  Wertung „Mit Flughilfe“, nie Rekord, `docs/design/assisted-laps.md`) und „Drohnen-Trails“ (dünne Spur hinter jeder
  fliegenden Drohne, persönlich abschaltbar im Tab „Darstellung“, `docs/design/drone-trails.md`). Katalog als reines Java
  (`server.rules`), Sync an alle Clients, Tab „Welt-Regeln“ (ändern: Host oder wer `/gamerule` ausführen darf, also Operatoren oder Admins mit Recht aus einem Rechte-Mod, `server.rules.RuleAccess`; sonst nur lesbar) mit den
  Voreinstellungen Realistisch, Training und Arcade ganz oben (`server.rules.RulePreset`, gelten sofort, auch per
  `/propwash rules preset`), Export für die Website (`tools/export_rules.sh`). Abweichende Physik-Regeln zählen als veränderte Physik (keine Bestenlisten).
- **Videokanäle und Zuschauen** (video, drone, server, osd): Jeder Videosender hat einen Kanal (analog R1–R8/F1–F8, digital
  CH1–CH8), den der Server beim Start automatisch frei vergibt oder der fest eingestellt ist (Tab „Videokanal“, OSD-Element
  „VTX“). Mit aufgesetzter Brille schaltet die Taste „Zuschauen“ (F7) durch die aktiven Kanäle anderer Piloten: Kamera an
  deren Drohne, Bildstörungen nach dem eigenen Abstand, OSD des Piloten optional, keine Steuerung; Esc, Rechtsklick oder
  der letzte Kanal beenden. Der Server verankert die Sicht des Zuschauers an der Drohne (Chunks, Entities); Server-Regel
  `allowSpectating`. Vertrag in `docs/FOUNDATION.md` §15 „Videokanäle und Zuschauen“.
- **Action-Cams** (video, drone, content): Drohnen mit Action-Cam oben nehmen im FPV per Taste F9 oder Funkenschalter
  „Aufnahme“ ein MP4-Video des sauberen Kamerabilds auf (ohne OSD und Funkstörungen), gespeichert unter
  `propwash/recordings`. Light: bis 1080p60, roh, am Flugakku. Heavy: bis 1440p60, stabilisiert/Horizont, eigener Akku, der
  abgestellt per USB aus dem Flugakku lädt. Tab „Action-Cam“ im Konfigurator. Die Action-Cam (Flashback) nimmt kein Video auf, sondern den Weg der FPV-Kamera
  als Szene für die Replay-Mod Flashback (nur Fabric; ohne Flashback nur ein Hinweis), mit demselben Knopf.
- **Freestyle-Tricks** (trick, osd, guide): 59 Tricks in neun Familien – Flips und Rollen (einfach bis dreifach,
  segmentiert), Snap Roll, Korkenzieher, Vanny Roll, Rewind und Flip-Rewind, Juicy Flick, Rubik's Cube, Power Loop mit Power
  Flip, Power Roll, Inverted 360 Power Loop und Side Loop, Maverick Loop, Barani, Immelmann und Immelmatt, Hammerhead, Matty
  Flip, Anti/Half/Power Matty, Split-S, 540-Split-S und Splitback, Yaw-Spins (aufrecht und kopfüber), Coin Drop, Trippy Spin,
  McTwist, Kopfüber hängen, Knife Edge, Reverse Knife Edge und Wall Ride, Sturzflug, Rückwärts-Sturzflug und Tornado, Orbit,
  Gap und Proximity, dazu die neue Schule (Eject, Sbang-Kombo, Funky Split, Shinobi Flip, Guillotine, Side Trippy) und im
  3D-Modus Rückenflug, Schweben kopfüber, Tic-Toc, 3D-Flip, 3D-Rolle und Power Loop (3D) – werden aus dem Flugmodell erkannt, auch
  unsauber, langsam, mit Achsen-Mischung und direkt aneinandergereiht, und mit Kombo-Multiplikator, Ketten- und Stil-Bonus
  gewertet; dezentes OSD-Popup (verkettete Tricks in einer Meldung), Punkte und beste Kombo in der Flugstatistik. Tab
  „Freestyle“: Automatisch (an bei Freestyle und Whoops), An, Aus, alle Tricks nach Familien mit Punkten, Suche, Filter und
  Erklärung; Flugschul-Lektion
  „Freestyle-Tricks“ (`docs/design/freestyle-tricks.md`).
- **Kreativ-Drohne und Drohnen-Editor** (creative, content, sim, ui; `docs/design/creative-drone.md`): der
  Kreativ-Flugcontroller (nur Kreativ-Inventar, kein Rezept) passt in jeden Rahmen; mit ihm zeigt der Konfigurator den
  Tab „Drohnen-Editor“: Massen, Trägheit, Motorpositionen, Propeller (auch Ct/Cp), Motoren, Akku, ESC, Luftwiderstand,
  Ducts und Kamera frei, Vorlagen aus Teilen, Presets und eigenen Vorlagen, Teilen-Code `PWD1-…`. Gleiche Ableitung wie
  für Teile-Daten, nur die Trägheit ist eine Überschreibung; Grenzen und Hinweise im Editor, Prüfung am Server, ändern
  nur im Kreativmodus, keine Bestenlisten.
- **Blackbox** (blackbox, sim, input; `docs/design/blackbox.md`, Spieler: `docs/blackbox-tuning.md`): im Tab „Blackbox“
  eingeschaltet, schreibt jeder scharfe Flug einen Betaflight-kompatiblen Log (`propwash/blackbox/*.bbl`, Lograte 1/1–1/8
  der Regelschleife, Speichergrenze mit Löschen der ältesten Logs) für Betaflight Blackbox Explorer und PIDtoolbox;
  Schalterfunktion „Blackbox“ wie der Modus BLACKBOX, Taste „Blackbox pausieren / fortsetzen“ (Num 7). Nur Client.
- **Eigene Befehle** (input, `docs/design/switch-commands.md`): acht Funktionen „Eigener Befehl 1–8“ im Tab „Modi“ (Seite
  „Befehle“) mit einem Minecraft-Befehl beim Einschalten und optional einem beim Ausschalten, je Funke im Geräteprofil
  gespeichert, auch auf Tasten. Laufen wie im Chat getippt mit den Rechten des Spielers, nur im Flug und ohne offenes
  Fenster, 0,5 s Sperrzeit je Schalter, höchstens 5 auf einmal und dann einer je Sekunde.
- **Name der Drohne** (drone): Konfigurator-Tab „Konfiguration“ (per `CraftNameC2S`), Namensschild an der platzierten
  Drohne oder Amboss; gebundene Funken im Inventar übernehmen den Namen.

## Regeln für alle Beiträge

- Code-Konventionen in `docs/CONVENTIONS.md` gelten verbindlich — insbesondere: **kein Kommentar im Code**, Mixins zentral unter `de.lardtom.propwash.mixin.<feature>`.
- Kein Code aus FPVCraft oder anderen ARR-Mods, auch nicht „inspiriert abgetippt“.
- APIs nur aus den 26.3-Quellen (`.ref/`) bzw. den Notizen in `docs/api/` — nie aus dem Gedächtnis.
- Client-Klassen nie aus Server-Code laden.
- Texte über Sprachdateien (`de_de`, `en_us`); deutsche Texte mit Umlauten.
- Test-Instanzen stumm starten (Master 0.001, alle Kategorien 0).
