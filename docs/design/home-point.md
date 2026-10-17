# Homepoint im NBT der Drohne (rescue, drone, Propwash 0.5.2)

Stand 14.10.2026. Anforderung des Besitzers, sinngemäß: Der Homepoint von GPS Rescue soll in den Entity-Daten einer
Drohne abrufbar sein. Ob Admins ihn mit `/data merge` setzen dürfen, war offen (Tendenz ja, mit Prüfung: gleiche
Dimension, innerhalb der Weltgrenze).

Code: `rescue.HomePoint` (Datensatz, reines Java), `rescue.HomePointRules` (Prüfregeln, reines Java), `rescue.HomeSync`
(Abgleich am Client, reines Java), `rescue.HomePointCodecs` (NBT-Codec), `rescue.HomePoints` (Server: Paket vom Piloten,
Versand an den Piloten, Prüfung von `/data`), `rescue.net.HomePointC2S`/`HomePointS2C`, `rescue.client.RescueController`
(meldet und übernimmt), `drone.QuadcopterEntity` (Feld, Speichern, Laden, `postDataManipulated`) und der Mixin
`mixin.drone.EntityDataAccessorMixin` (Prüfung vor `/data`).

## 1. Daten

Schlüssel `HomePoint` in den Entity-Daten von `propwash:drone`:

```
/data get entity @e[type=propwash:drone,limit=1,sort=nearest] HomePoint
→ {x: -31.5d, y: -60.0d, z: -33.5d, dimension: "minecraft:overworld", game_time: 48213L, source: "arm"}
```

| Feld | Inhalt |
|---|---|
| `x`, `y`, `z` | Position (Kommazahlen), am Boden unter der Drohne bzw. an den Füßen des Piloten – genau der Punkt, den GPS Rescue anfliegt |
| `dimension` | Dimension, in der der Punkt liegt, z. B. `minecraft:overworld` |
| `game_time` | Spielzeit in Ticks beim Setzen, vergleichbar mit `/time query gametime` (20 Ticks = 1 Sekunde) |
| `source` | wer ihn gesetzt hat: `arm` (Scharfschalten), `manual` (Taste oder Schalter „Homepoint setzen“), `pilot` (Modus „Beim Piloten“: Stand beim Scharfschalten), `command` (Admin per `/data` oder `/summon`) |

Eine Drohne ohne Homepoint hat keinen Schlüssel `HomePoint` (`/data get … HomePoint` meldet dann „Found no elements“).
Der Homepoint wird mit der Drohne gespeichert und überlebt Neuladen, Neustart und den Wechsel der Dimension. Aufheben als
Item löscht ihn – wie beim Abstecken des Akkus am echten Copter beginnt die Drohne danach ohne Homepoint.

## 2. Wann er gesetzt wird

Die Regeln von GPS Rescue bleiben, wie sie sind (`RescueSupervisor`, Einstellung „Homepoint“ im Tab GPS Rescue). Neu ist
nur, dass der Client des Piloten jeden gesetzten Punkt an den Server meldet (`propwash:home_point_set`):

- **Erstes Scharfschalten**: beim ersten Scharfschalten einer Sitzung, `source: "arm"`.
- **Jedes Scharfschalten**: bei jedem Scharfschalten, `source: "arm"`.
- **Manuell**: per Taste oder Schalter „Homepoint setzen“ an der Drohne (`manual`); ohne Homepoint beim Scharfschalten (`arm`).
- **Beim Piloten**: GPS Rescue fliegt zum Piloten, der Punkt wandert mit ihm. Gespeichert wird der Stand beim
  Scharfschalten (`pilot`), damit Admins sehen, wo der Pilot war; jede Bewegung zu melden wäre nur Verkehr.
- „Homepoint setzen“ ohne Drohne (an der eigenen Position) gilt wie bisher für die nächste Drohne und landet beim
  Flugstart in deren `HomePoint` (`manual`).

Der Server nimmt den Punkt nur vom aktuellen Piloten der Drohne und nur mit endlichen Zahlen, in der Dimension der
Drohne, innerhalb der Weltgrenze und höchstens 64 Blöcke unter bzw. über der Welt. Er setzt Dimension und Spielzeit
selbst. Eine Reichweitengrenze wie beim Heimflug ohne Pilot gibt es nicht: Der gespeicherte Punkt ist nur Angabe, der
Server fliegt nicht danach (`ReturnPlan` prüft seinen eigenen Homepoint weiter mit Funkreichweite und Spawn-Schutz,
`docs/design/autonomous-rescue.md`).

## 3. Beim Piloten ankommen

Beim Flugstart schickt der Server dem Piloten den gespeicherten Punkt (`propwash:home_point`). Der Client entscheidet
wie bisher beim Wechsel der Drohne (`HomeSync.onStart`):

- Hat der Pilot einen selbst gesetzten Homepoint („Homepoint setzen“) in dieser Dimension, behält er ihn und meldet ihn
  der Drohne.
- Sonst übernimmt er den Homepoint der Drohne, wenn er in derselben Dimension liegt – auch nach einem Neustart von
  Server oder Spiel. GPS Rescue, OSD-Pfeil und Marker zeigen ihn schon vor dem Scharfschalten.
- Danach gelten die Regeln aus Abschnitt 2: Im Modus „Erstes Scharfschalten“ ersetzt das erste Scharfschalten nach einem
  Neustart den gespeicherten Punkt (wie das Einstecken des Akkus am echten Copter), im Modus „Manuell“ bleibt er.

Ändert ein Admin den Punkt per `/data`, während jemand fliegt, bekommt der Pilot ihn sofort samt Hinweis „Homepoint auf
dem Server geändert“; ein laufender Heimflug fliegt den neuen Punkt an. Im Modus „Beim Piloten“ bleibt der Pilot das
Ziel.

Damit eine ältere Nachricht des Servers nie einen gerade gemeldeten Punkt überschreibt, zählt der Client seine
Meldungen, der Server quittiert die letzte (`QuadcopterEntity#homeAck`, beim Pilotenwechsel 0), und der Client nimmt nur
Nachrichten an, die seine letzte Meldung in dieser Sitzung schon kennen. Nachrichten zu Drohnen, die der Client noch
nicht kennt (Flugstart aus der Ferne), hebt er auf, bis die Sitzung beginnt.

## 4. Admins: `/data`

```
/data get entity @e[type=propwash:drone,limit=1,sort=nearest] HomePoint
/data merge entity @e[type=propwash:drone,limit=1,sort=nearest] {HomePoint:{x:100.5d,y:64d,z:-20.5d}}
/data modify entity @e[type=propwash:drone,limit=1,sort=nearest] HomePoint set value {x:0.5d,y:70d,z:0.5d}
/data remove entity @e[type=propwash:drone,limit=1,sort=nearest] HomePoint
/execute as @e[type=propwash:drone,tag=pw_owner_Steve] run data get entity @s HomePoint
/summon propwash:drone ~ ~ ~ {HomePoint:{x:0.5d,y:64d,z:0.5d}}
```

- Erlaubt ist, wer `/data` benutzen darf (Vanilla: Operator ab Stufe 2 oder ein Rechte-Mod).
- Pflicht sind `x`, `y`, `z` (ganze Zahlen gehen auch). `dimension` darf fehlen, dann gilt die der Drohne; steht sie da,
  muss es die der Drohne sein. `/data merge` führt verschachtelte Einträge zusammen, eine fehlende `dimension` bliebe
  dort also die alte: Liegt der alte Punkt in einer anderen Dimension als die Drohne (Portal, `/tp`) und nennt der
  Befehl neue Koordinaten, aber dieselbe alte Dimension, gilt die Dimension der Drohne (`HomePointRules.edit`). Eine
  andere fremde Dimension scheitert weiter.
- Geprüft wird vor dem Ändern (`EntityDataAccessorMixin` → `HomePoints.prepareEdit`): andere Dimension, außerhalb der
  Weltgrenze, mehr als 64 Blöcke unter oder über der Welt oder ein kaputter Eintrag (Text statt Zahl, unbekannte Herkunft)
  lassen den ganzen Befehl mit einer roten Meldung scheitern, die Drohne bleibt unverändert.
- Ein neuer Punkt bekommt `source: "command"` und die aktuelle Spielzeit. `game_time` und `source` sind nur lesbar: Ändert
  ein Befehl nur sie, bleiben die alten Werte.
- `/data merge` auf andere Schlüssel (etwa `CameraCharge`) scheitert nie am Homepoint, auch wenn der alte in einer
  anderen Dimension liegt.
- `/data remove … HomePoint` löscht ihn; der Pilot hat dann keinen Homepoint, bis die Regeln aus Abschnitt 2 einen setzen.
- `/summon` mit `HomePoint` nimmt den Punkt ungeprüft; fehlen Dimension oder Spielzeit, setzt Propwash die der Drohne
  bzw. die aktuelle Spielzeit ein.

## 5. Entscheidungen

- **`/data merge` darf ihn setzen, mit Prüfung vor dem Ändern.** Admins brauchen keinen neuen Befehl; die Prüfung läuft
  vor dem Laden, damit ein ungültiger Wert den Befehl sichtbar scheitern lässt. Verworfen: still verwerfen (Minecraft
  meldet „Entity-Daten geändert“, obwohl nichts passiert ist) und ein eigener Befehl `/propwash home` (zweiter Weg für
  dasselbe).
- **Server speichert, Client fliegt.** Der Autopilot läuft weiter am Client des Piloten, der Punkt dort wirkt sofort,
  auch mit Verzögerung im Netz. Verworfen: den Homepoint nur auf dem Server führen – jedes Scharfschalten würde erst nach
  einer Rundreise wirken.
- **Eigene Pakete statt synchronisierter Entity-Daten.** Nur der Pilot braucht den Punkt; ein Feld in
  `SynchedEntityData` hätte jedem Zuschauer alle Homepoints geschickt und einen eigenen Serializer (Registereintrag)
  gebraucht. Netzprotokoll 15 → 16.
- **Spielzeit statt Uhrzeit.** `game_time` gehört zur Welt, läuft in Pausen nicht weiter und passt zu
  `/time query gametime`. Verworfen: Unix-Zeit des Servers (hängt an der Uhr des Rechners, im Einzelspieler wenig
  aussagekräftig).
- **Rescue-Regeln unverändert.** Der Modus „Erstes Scharfschalten“ setzt nach einem Neustart neu, wie bisher. Verworfen:
  einen gespeicherten Punkt als „schon scharfgeschaltet“ zu werten – das hätte das Verhalten von GPS Rescue geändert.
- **Kein Homepoint im Item.** Aufheben und neu Platzieren beginnt ohne Punkt. Verworfen: eigene Item-Komponente (neuer
  Registereintrag im Beitrittsabgleich) oder `minecraft:custom_data` (Kollision mit Daten von Mapmakern).
- **Kein Eintrag in der öffentlichen API.** Add-ons lesen den Punkt wie jede Entity-Angabe aus den gespeicherten Daten;
  `API_VERSION` bleibt.

## 6. Tests

- JUnit `HomePointRulesTest` (Prüfregeln, Befehls-Änderung mit Zeitstempel, nur lesbare Felder, fremde Dimension),
  `HomeSyncTest` (Quittung, Entscheidung beim Flugstart), `HomePointCodecsTest` (NBT-Schlüssel, Handeingaben mit ganzen
  Zahlen und ohne Dimension, kaputte Einträge, Pakete), `RescueSupervisorTest#armEdgeIsReportedOnceInEveryMode`.
- Server-Rauchtest (`:fabric:runServerSmoke`, `:neoforge:runServerSmoke`, `rescue.HomePointChecks`) auf dem dedizierten
  Server mit Ersatzspielern: Meldung des Piloten beim Scharfschalten; Ablehnung von Nicht-Piloten, NaN, Herkunft
  `command`, fehlender Herkunft, Weltgrenze und Höhe; `/data get` zeigt alle Felder; Speichern und Laden; `/data merge`
  und `/data modify` mit `command` und neuer Spielzeit; abgelehnte Befehle (andere Dimension, Weltgrenze, Höhe, Text,
  unbekannte Herkunft) ändern nichts; nur `game_time`/`source` geändert → alte Werte; fremde Dimension blockiert anderes
  `/data merge` nicht; `/data remove`; Pilotwechsel setzt die Quittung zurück, der Punkt bleibt.
- Selbsttest `rescue: Homepoint im NBT – …` (beide Loader) im echten Flug: Scharfschalten → Punkt an der Drohne gleich
  dem am Client (`arm`); `/data merge` → Client übernimmt; abgelehnte Befehle → Client unverändert; Aussteigen, Client
  vergisst alles (wie beim Trennen), Drohne gespeichert und neu geladen, erneuter Flugstart → Client übernimmt den Punkt
  vor dem Scharfschalten. Mit `-PpropwashScreenshots=true` `docs/screenshots/home_point_command.png` (Marker am neuen
  Punkt und Hinweis „Homepoint auf dem Server geändert“).

## 7. Reset zum Homepoint (seit dem Spieltest vom 15.10.)

Bis dahin gab es „Rennen neu starten“ (Rücktaste, Schalterfunktion `RACE_RESTART`). Die Funktion lief nur im Client
(`ClientRaceTracker`): Während eines Laufs brach sie ihn ab, sonst stellte sie nahe am Start-Gate (24 Blöcke) für einen
stehenden Start auf, weiter weg tat sie nichts. Die Drohne blieb immer, wo sie war, und es gab keine Meldung – im
Spieltest sah das aus, als funktioniere die Taste nicht. Ersetzt durch **Reset**:

- **Auslöser:** Schalterfunktion `RESET` und Taste „Reset“ (Rücktaste) im Tab Modi; gespeicherte Belegungen wandern mit
  (Funke: `race_restart` → `reset` in `DeviceProfile.RENAMED`; Taste: `key_key.propwash.race_restart` → `key_key.propwash.reset`
  in `options.txt` per `KeyDefaults.migrateOptions`, eingehängt in `Options.dataFix` über `OptionsMixin`, eine schon
  gesetzte neue Belegung gewinnt). Gamepad-Standard bleibt „Back“.
- **Server entscheidet** (`rescue.HomeResets`, reine Regeln in `HomeResetRules`): nur der Pilot der Drohne, nur mit
  Welt-Regel `propwash:allow_reset`, höchstens einmal je Sekunde (20 Ticks). Ziel ist der `HomePoint` der Drohne in ihrer
  Dimension, sonst der Startpunkt des letzten Scharfschaltens (`QuadcopterEntity.launchPoint`, nur im Speicher); ohne
  beides Meldung „noch kein Homepoint“. Das Ziel muss innerhalb von Weltgrenze und Bauhöhe liegen und dort, wo der Pilot
  auch hinfliegen dürfte: in seiner Funkreichweite (`RadioLink.releaseRange` wie beim Fliegen), nicht im geschützten
  Spawn-Bereich, und ohne Recht zum Erzeugen von Gelände nur, wenn der ganze Umkreis des Tickets schon erzeugt ist
  (`RangeManager.generatedStatus`, Radius 3 Chunks; unbekannt = warten ohne Ticket). Ist es zugebaut, sucht der Server bis 4 Blöcke darüber in Viertelschritten Platz,
  sonst Meldung.
- **Laden:** Der Server setzt ein `DRONE_TICKET` (Radius 2) am Ziel und versetzt erst, wenn dort Entities ticken
  (`isPositionEntityTicking`), höchstens 200 Ticks lang, sonst Meldung. Vorher versetzt, würde die Drohne in einem
  ungeladenen Entity-Abschnitt verschwinden. Danach verfolgt der Pilot seine Drohne 100 Ticks lang auch, wenn ihm der
  Chunk noch nicht geschickt wurde (`TrackedEntityMixin`), sonst entfernte der Client die Drohne und die Sitzung endete.
- **Versetzen:** `QuadcopterEntity.HOME_RESET` (race beendet den Server-Lauf, verlässt Startaufstellung und Heat; interact
  wirft Fracht am alten Ort ab), dann entschärft, Motoren aus, `resetTo(…, home = true)` gerade mit dem bisherigen Kurs und
  ohne Geschwindigkeit. `DroneResetS2C` trägt `home`: der Client des Piloten entschärft die Sim (Arm-Schalter muss einmal
  aus), hält die Physik bis zu 5 s an, bis die Chunk-Spalte am Ziel da ist, und feuert `PilotSession.HOME_RESET`.
- **Rennen:** `ClientRaceTracker` bricht den Lauf mit `AbortReason.RESET` ab („Rennen abgebrochen: zurück zum
  Homepoint“), verlässt die Startaufstellung und vergisst die letzte Position, damit die Sprung-Strecke kein Gate
  durchquert. Auf dem Server setzt `RaceServer.onHomeReset` die Sitzung zurück; eine danach gemeldete Runde ist
  `OUT_OF_SEQUENCE`. Das erste Scharfschalten nach einem Reset nahe am Start stellt für einen stehenden Start auf (wie mit
  „Start beim Scharfschalten“), ebenso Scharfschalten, während dort eine Aufstellung offen ist.

### Entscheidungen

- **Rückfall Startpunkt statt Pilot.** Ohne Homepoint (Modus „Manuell“ ohne gesetzten Punkt) geht es zum Punkt des
  letzten Scharfschaltens. Verworfen: die Position des Piloten (steht oft in Gebäuden oder auf Blöcken, die Drohne landete
  in ihm) und „Reset verweigern“ (die Drohne flog ja schon von irgendwo los).
- **Nie über Dimensionen.** Liegt der Homepoint in einer anderen Dimension, gilt der Startpunkt, sonst Meldung. Verworfen:
  mitnehmen – der Pilot bliebe zurück, die Sitzung endete, und die Drohne läge allein in einer anderen Welt.
- **Reset ersetzt „Rennen neu starten“ ganz,** auch als Auslöser für stehende Starts: Reset bringt die Drohne zum Start,
  das Scharfschalten danach stellt auf. Verworfen: zwei Funktionen (eine Taste mehr für fast dasselbe).
- **Welt-Regel standardmäßig an, in allen Voreinstellungen an.** Reset bricht jeden Lauf ab und verschafft so keinen
  Vorteil in Rennen; Fracht bleibt am alten Ort, damit Reset kein Transportweg ist. Server, auf denen der Rückweg
  zum Spiel gehört (Survival, Erkundung), schalten sie aus. Verworfen: „Realistisch“ aus – Realistisch ist genau der
  Standard, und Simulatoren wie Liftoff oder Velocidrone haben Reset ebenfalls.
- **Nur wohin der Pilot fliegen dürfte.** Den Homepoint meldet der Client (`home_point_set`) – ein veränderter Client
  könnte beliebige Koordinaten schicken und Reset wäre sonst ein Sprung an jeden Ort samt Erzeugen von Gelände. Deshalb
  gelten dieselben Grenzen wie im Flug (Funkreichweite, Spawn-Schutz, Recht zum Erzeugen). Verworfen: Homepoints nur nahe
  der Drohne annehmen – „Homepoint setzen“ ohne Drohne an der eigenen Position wäre dann kaputt.
- **Schaden und Akku bleiben.** Reset ist kein Reparieren. Verworfen: Akku auffüllen (das macht die Welt-Regel *Akku*).
- **Kurze Sperrzeit ohne Meldung** gegen Tastenprellen und Chunk-Laden im Dauerfeuer.

### Tests

- JUnit `HomeResetRulesTest` (Ziel, Rückfall, Dimension, Sperrzeit, Grenzen, Freiraum, gerade Lage, Welt-Regel und
  Voreinstellungen, Texte), `KeyRenameTest` (alte Taste und Schalterbelegung), `race.ResetAbortTest` (Abbruch, Sprung
  zählt keine Runde, neuer Lauf durch Gate 1).
- Selbsttest `reset: …` (beide Loader): Taste setzt die Drohne gerade und entschärft zurück; Welt-Regel aus verweigert,
  an erlaubt; laufender Lauf bricht mit `RESET` ab, Server lehnt eine Runde danach ab, Scharfschalten am Start stellt
  auf, Reset verlässt die Aufstellung; ferner Homepoint (400 Blöcke) im FPV hin und per Taste zurück, Chunk geladen,
  Drohne fällt nicht durch; alte Belegung in `options.txt` wird übernommen.
- Server-Rauchtest (`rescue.HomeResetChecks`): nur der Pilot, Welt-Regel aus, Paket, Sperrzeit, ohne Punkt, Punkt im
  Nether, Punkt außer Funkreichweite, ungeladener ferner Punkt wartet (Drohne bleibt sichtbar), vorgeladener ferner Punkt
  sofort. Der Test lädt dafür den ganzen Prüf-Umkreis (Ticket-Radius + 1) vor und wartet, bis alle diese Chunks voll
  geladen sind; so besteht er unabhängig davon, ob der Testpilot laut `server.json` Gelände erzeugen darf.
