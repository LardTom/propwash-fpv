# Autonomer Flug mit Add-on-Flughilfe

Für Add-on-Autoren und Server-Betreiber. Mit API 1.7 kann ein Add-on eine Drohne am Server selbst fliegen lassen: Propwash
fliegt sie mit demselben Flugmodell wie im Heimflug, die Flughilfe des Add-ons sagt, wohin. So schwebt eine
Kamera-Drohne an Ort und Stelle, folgt ihrem Besitzer oder fliegt eine Mission weiter, wenn der Pilot die Brille abnimmt.

Technik und Entscheidungen: [`design/0.6.0/addon-api-namespace.md`](design/0.6.0/addon-api-namespace.md) §3.10, §3.19,
E21, E37, E39.

## 1. Starten

```java
AutonomyResult r = PropwashApi.drones().autonomy().start(drone, owner, new FollowMe(owner),
        AutonomyOptions.of(Identifier.fromNamespaceAndPath("meinaddon", "follow")).label("FOLLOW"));
```

- Nur am Server und im Server-Thread; `drone` muss eine Propwash-Drohne sein (sonst Ausnahme). Die Id liegt im eigenen
  Namensraum (`propwash` ist gesperrt).
- `AutonomyOptions`: `label` (höchstens 8 Zeichen, erscheint im OSD), `maxDuration` (1 s bis 60 min, Standard 60 min),
  `takeoff(m)` (1–30 m, Start vom Boden).

| Ergebnis | wann |
|---|---|
| `STARTED` | Flug läuft. Lief schon ein Add-on-Flug auf der Drohne, endet dieser mit `REPLACED` und der neue übernimmt nahtlos (gleiches Flugmodell, keine Lücke). |
| `PILOTED` | Die Drohne hat einen Piloten. |
| `DENIED` | Besitzer offline, in anderer Dimension, außerhalb der Funkreichweite (wie beim Pilotenstart) oder ohne Recht zu fliegen (`ServerRules.mayPilot`), oder Propwashs Heimflug läuft gerade (der hat Vorrang). |
| `NO_BUDGET` | Kein Platz im Budget (§5). Der Besitzer bekommt eine Meldung mit dem Wert der Welt-Regel. |
| `NOT_READY` | Die Drohne liegt nicht ruhig am Boden (§3), liegt am Boden, aber ohne `takeoff`, oder bei der Übergabe in der Luft ohne Akku. Den Grund nennt `notReadyReason` (unten). |
| `NOT_ARMABLE` | Mit `takeoff`: Schärfen am Boden scheitert (kein Akku, nicht waagerecht, im Wasser, Bau ungültig …). Der Besitzer bekommt den Grund als Meldung wie ein Pilot. |

`autonomy().active(drone)` nennt die Id des laufenden Flugs, `autonomy().stop(drone)` beendet ihn (`STOPPED`).

Warum `start` mit bestimmten Optionen `NOT_READY` ergibt, nennt `autonomy().notReadyReason(drone, options)` (seit API
1.7, `default`-Methode; nur am Server im Server-Thread wie `start`). Das Ergebnis ist leer, wenn diese Bedingungen den
Start nicht verhindern; andere Ergebnisse wie `PILOTED`, `DENIED` oder `NO_BUDGET` bleiben möglich. Läuft schon ein
Add-on-Flug auf der Drohne, ist es leer, weil `start` ihn ersetzt.

```java
AutonomyOptions options = AutonomyOptions.of(id("mission")).label("MISSION").takeoff(5.0);
AutonomyResult r = autonomy.start(drone, owner, mission, options);
if (r == AutonomyResult.NOT_READY) {
    Optional<AutonomyNotReady> why = autonomy.notReadyReason(drone, options);
}
```

| `AutonomyNotReady` | Bedeutung |
|---|---|
| `NOT_ON_GROUND` | Die Drohne liegt nicht ruhig am Boden: Sie fliegt ohne Pilot, fällt, schwebt knapp darüber oder bewegt sich noch. `start` fliegt nur aus dem Stand los; in der Luft übernimmt ein Add-on nur bei der Übergabe (§6). |
| `NO_TAKEOFF` | Die Drohne liegt am Boden, die Optionen haben aber kein `takeoff`. |
| `NO_BATTERY` | Übergabe in der Luft ohne Akku (nur über einen `ExitHandler`). Am Boden mit `takeoff` ist das Ergebnis ohne Akku `NOT_ARMABLE`. |
| `REMOVED` | Die Drohne ist entfernt. |

Akkustand und GPS-Fix prüft Propwash beim Start nicht (der Server fliegt ohne GPS-Fehler); eigene Prüfungen eines
Add-ons, etwa „Akku zu leer für die Mission“, meldet das Add-on selbst. `switch` über `AutonomyNotReady` immer mit
`default`, spätere Versionen können Gründe anhängen.

## 2. Was die Flughilfe am Server sieht und darf

Die Flughilfe (`FlightAssist`) läuft im Server-Thread, einmal je Rechenschritt des Server-Flugs (4–8 je Tick) und darf
dort Spieler und Welt lesen (Follow me hält die `ServerPlayer`-Referenz selbst).

- `FlightState`: Position, Tempo und Lage sind die echten Werte; die geschätzten Werte sind gleich, `positionValid()` und
  `gpsFix()` sind immer wahr (der Server fliegt wie im Heimflug ohne GPS-Fehler). Pilotknüppel sind neutral (Roll,
  Nick, Gier 0, Gas 0,5), `pilotMode()` ist `ANGLE`, `action(n)` falsch. `obstacleDistance` liefert Strahlen am Server,
  wenn `sensorRange()` > 0 ist. `addonSwitch`/`addonAxis` liefern den zuletzt gemeldeten Zustand wie
  `AddonSwitch#active`/`AddonAxis#value`; nach dem Pilotenwechsel ist er leer (`false` bzw. `center`).
  `terrainLimit()` ist wahr, solange Propwash die Drohne am Rand des erzeugten Geländes festhält, weil die Flughilfe sie
  darüber hinaus schickt und der Besitzer dort kein Gelände erzeugen darf (§4); kehrt die Flughilfe binnen 1 s um, fliegt
  der Flug weiter. `angleLimitDeg()` ist die Winkelgrenze aus dem PID-Tab der Drohne (10–85°, Standard 60°): Knüppel der
  Flughilfe in ANGLE werden mit ihr in Neigung umgesetzt, voller Knüppel neigt um genau diesen Wert, wie am Client.
- `AssistOutput`: Propwash rechnet die Vorgaben selbst in Knüppel um, mit der echten Lage statt GPS-Schätzung:
  `holdPosition`, `velocity` (begrenzt durch `maxSpeed`, Standard 4 m/s, höchstens 20 m/s), `holdAltitude`, `climb`
  (±8 m/s), `heading` (Kompassgrad), `yawRate`, `label`. `sticks` gilt roh für Achsen ohne Vorgabe (mit `mode`).
  Ohne jede Vorgabe hält die Drohne Position und Höhe dort, wo die letzte Vorgabe endete.
- `land()`: Propwash landet senkrecht an Ort und Stelle wie im Heimflug, entschärft am Boden und beendet den Flug mit
  `LANDED`. Ist der Punkt unter der Drohne frei und eben, setzt sie genau dort auf (höchstens 0,3 m daneben), auch auf
  Blöcken mit ebener Oberseite in Teilhöhe (Landeplatz eines Add-ons, Teppich, Stufe; auch in den Boden eingelassen).
  Eben heißt: Unter der Drohne mit 0,05 Blöcken Rand liegen nur feste Blöcke mit voller, ebener Oberseite, deren
  Oberseiten höchstens 0,25 Blöcke auseinanderliegen, und darüber ist bis zur Drohne alles frei. Sonst (Treppe, Zaun,
  Kante zwischen Stufe und vollem Block unter der Drohne, Laub, Wasser, Lava oder Feuer im Rand von 0,4 Blöcken) sucht
  Propwash wie bisher einen Platz daneben. Dieselbe Prüfung gilt für die Landung am Homepoint im Heimflug (ohne Pilot
  und GPS Rescue des Piloten); dort darf die Oberseite zusätzlich höchstens 1 Block über dem Homepoint liegen, ein Dach
  darüber ist kein Landeplatz.
- `requestReturnHome()`: beendet den Add-on-Flug mit `STOPPED`, danach fliegt Propwash heim (§4).
- Eine Ausnahme in `update`, `reset` oder `sensorRange` beendet den Flug mit `ERROR`; Propwash landet an Ort und Stelle.

## 3. Start vom Boden

Mit `takeoff(m)` schärft Propwash die Drohne am Boden mit denselben Prüfungen wie beim Piloten, steigt senkrecht auf
die Höhe (über dem Startpunkt) und übergibt dann an die Flughilfe. Während des Steigens läuft die Flughilfe noch nicht,
`active()` nennt den Flug aber schon. Eine Drohne in der Luft steigt nicht.

„Am Boden“ heißt: Die Drohne liegt auf und ruht. Propwash misst dafür den Abstand der ganzen Kollisionsform zum Boden,
nicht nur den der Hitbox: Hitbox, Bodenpunkte des Rahmens, die Ecken von hängendem Zubehör (auch Gimbal) und die
Standbeine in der Ebene der tiefsten Zubehör-Ecke ([`accessory-hang.md`](accessory-hang.md) §6), unter jedem Punkt
mit derselben 1 mm dünnen Säule wie beim losen Abstellen (Blöcke, Teil-Blöcke, Gates und weicher Boden). Am Boden ist
die Drohne, wenn der kleinste Abstand unter 2 cm liegt und sie langsamer als 0,25 m/s ist. So startet eine Drohne,
die auf einem hohen Gimbal oder Zubehör steht (Kino-Drohne eines Kamera-Add-ons: Hitbox 0,162 m über dem Boden), oder
mit dem Zubehör über einem Loch auf ihren Standbeinen, aus dem Stand wie eine ohne Zubehör, für jeden Rahmen aus
Propwash und aus Add-ons. Schwebt sie 2 cm oder mehr darüber oder fällt sie, ist sie nicht am Boden (`NOT_ON_GROUND`).
Dieselbe Prüfung entscheidet bei der Übergabe (§6) und bei der Übernahme durch den Piloten (§4), ob die Drohne in der
Luft ist.

## 4. Dauer und Ende

Ein Add-on-Flug läuft, bis der Akku kritisch wird, nicht 10 Minuten wie der Heimflug. Kritisch heißt: Warnstufe
`CRITICAL` oder `EMPTY` des Flugmodells oder weniger als 15 % Ladung (Reserve für Heimflug und Landung).
`maxDuration` kürzt das; 60 Minuten sind die harte Grenze, auch über Wechsel der Flughilfe hinweg und für Akkus aus
Datenpaketen. Eine Welt-Regel dafür gibt es nicht.

Jedes Ende meldet `PropwashEvents.AUTONOMY_ENDED(drone, owner, id, reason)` am Server:

| `AutonomyEnd` | Anlass | danach |
|---|---|---|
| `PILOT` | ein Spieler startet eine Pilotensitzung | der Pilot fliegt nahtlos weiter (unten) |
| `LANDED` | nach `land()` am Boden entschärft | Drohne liegt |
| `BATTERY` | Akku kritisch | Propwashs Heimflug, sonst Landung an Ort und Stelle |
| `TIMEOUT` | `maxDuration` oder 60 min erreicht | wie `BATTERY` |
| `OWNER_OFFLINE` | Besitzer nicht mehr auf dem Server | Landung an Ort und Stelle |
| `OUT_OF_RANGE` | Besitzer in anderer Dimension oder außerhalb der Funkreichweite (wie beim Piloten) | Propwashs Heimflug wie bei einer GPS-Drohne nach Funkverlust (Return to Home); ohne GPS-Empfang an der Stelle der Drohne Landung an Ort und Stelle |
| `STOPPED` | `stop(drone)`, `requestReturnHome()`, Drohne entfernt oder entladen | Landung an Ort und Stelle, nach `requestReturnHome()` Heimflug |
| `ERROR` | Ausnahme in der Flughilfe, Absturz (Drohne in der Luft entschärft) | Landung an Ort und Stelle |
| `REPLACED` | neuer Start auf derselben Drohne | der neue Flug |
| `TERRAIN_LIMIT` | 1 s am Rand des erzeugten Geländes gegen die Wand gedrückt, obwohl der Besitzer kein Gelände erzeugen darf (unten) | wie `OUT_OF_RANGE`: Propwashs Heimflug, ohne GPS-Empfang an der Stelle der Drohne Landung an Ort und Stelle |

Übernahme durch den Piloten: Startet ein Spieler eine Pilotensitzung, während Propwash die Drohne in der Luft fliegt
(Add-on-Flug oder die Landung danach), übernimmt er sie wie bei der Übergabe in §6 nahtlos: Position, Tempo und Lage
bleiben, die Drohne ist scharf, die Motoren drehen mit Schwebedrehzahl, sie sackt nicht ab. Eine Flughilfe des Add-ons
beim Piloten sieht schon beim ersten Aufruf die Höhenschätzung (`estimatedY()`, `estimatedVy()`) wie im laufenden Flug;
die GPS-Schätzung (`estimatedX/Z/Vx/Vz`, `positionValid()`) ist wie bei jedem Start erst nach dem ersten GPS-Fix gültig.
Das Gas von Tastatur, Maus und Gamepad mit Gas „Schrittweise“ steht danach passend zur Flughilfe: hält eine Flughilfe
des Add-ons beim Piloten die Höhe (`holdAltitude`, `climb`, Mitte = Schweben), in der Mitte, sonst auf Schwebegas. Bei
einer Funke und bei Gas „Obere Hälfte“ oder „Voller Weg“ gilt die Knüppelstellung. Ist der Scharf-Schalter bei der
Übernahme aus, bleibt die Drohne scharf wie nach der Arm-Taste, bis der Pilot den Schalter einschaltet (danach gilt er
wieder) oder die Arm-Taste drückt. Am Boden beginnt die Sitzung wie bisher entschärft.

Heimflug heißt: Ziel ist der Plan, den der Pilot beim Verlassen gemeldet hatte, sonst der Homepoint der Drohne in
derselben Dimension, sonst der Startpunkt eines Starts vom Boden. Dafür muss die Welt-Regel „Heimflug ohne Pilot“ an sein;
weiter als 1500 Blöcke landet die Drohne sofort. Ohne Ziel oder ohne Regel landet sie an Ort und Stelle. Während dieser
Landung zählt der Flug noch im Budget, `active()` ist leer.

GPS-Empfang nach `OUT_OF_RANGE` und `TERRAIN_LIMIT`: Propwash misst beim Ende an der Stelle der Drohne die Himmelssicht
wie das GPS des Piloten (17 Strahlen bis 96 m nach oben, nur geladene Chunks). Reicht sie nicht für 5 Satelliten, etwa
unter einer Steindecke, in einer Höhle, unter Wasser oder im Nether, landet die Drohne an Ort und Stelle wie bisher; eine
Wand neben der Drohne nimmt den Empfang nicht. Den Heimflug meldet Propwash dem Besitzer selbst („… fliegt allein zum Homepoint und
landet dort.“). `AUTONOMY_ENDED` feuert erst nach dieser Entscheidung.

Solange Propwash eine Drohne ohne Pilot fliegt (Add-on-Flug, die Landung danach, Heimflug, Sinkflug), hält der Server
ihren Chunk und die schon erzeugten Nachbar-Chunks geladen und tickend, auch ohne Spieler in der Nähe oder in der
Dimension. Trennt der Besitzer, landet die Drohne an Ort und Stelle bzw. fliegt den Heimflug zu Ende; erst danach gibt
der Server die Chunks frei.

Neues Gelände: Ein Add-on-Flug folgt der Welt-Regel „Drohnen erzeugen neues Gelände“ wie ein Pilot. Maßgeblich ist der
Besitzer (`ServerRules.mayGenerateChunks`: Regel an und, falls die Server-Config das Erzeugen auf Rechtestufen
beschränkt, die Stufe des Besitzers); geprüft wird in jedem Tick.

- **Erzeugen erlaubt (Standard):** Propwash hält die Chunks um die Drohne auch über Gelände, das noch kein Spieler
  geladen hat, und lädt bei mehr als 3 m/s bis 4 Chunks voraus (4 s Flug); der Server erzeugt das Gelände dort wie für
  einen Piloten. Eine Mission über 300 Blöcke ohne Spieler in der Nähe erreicht so ihr Ende ohne Stillstand.
- **Erzeugen nicht erlaubt:** Ein Chunk tickt nur, wenn alle Chunks in Radius 2 um ihn erzeugt sind. Ein Chunk, für den
  das nicht gilt, ist eine Wand, wie für den Heimflug-Autopiloten: Propwash bremst die Drohne mit derselben Grenzlogik wie
  beim Piloten (`EdgeFence`) und hält sie 4 m davor an; seitlich und zurück fliegt sie frei. Drückt die Flughilfe sie
  gegen die Wand, ist `FlightState#terrainLimit()` wahr; nach 1 s endet der Flug mit `TERRAIN_LIMIT`
  (`AUTONOMY_ENDED`, Paket an den Besitzer, Meldung „Drohne: Weltrand erreicht – Drohnen dürfen hier kein neues Gelände
  erzeugen“). Danach fliegt Propwash heim wie nach `OUT_OF_RANGE`; ohne GPS-Empfang an der Stelle der Drohne (etwa unter
  einem Dach), ohne Ziel oder ohne die Regel „Heimflug ohne Pilot“ landet die Drohne an Ort und Stelle. Schweben vor der
  Wand, Flug an ihr entlang und Umkehren beenden den Flug nicht; am Rand stehen bleibt die Drohne höchstens diese 1 s.

Ergebnis je Wert der Regel für eine Mission ins nie geladene Gelände: an – die Mission erreicht ihr Ende; aus – Ende mit
`TERRAIN_LIMIT` etwa 4 m vor dem Rand, Hinweis an den Besitzer, danach Heimflug bzw. Landung an Ort und Stelle.

Auch beim Erzeugen hält die Wand die Drohne vor einem Chunk, dessen Umgebung noch entsteht; sie wartet dort, bis der
Server das Gelände fertig hat, und endet dort nie. Das gilt für jede Ausgabe der Flughilfe, auch für rohe Knüppel.
Propwashs eigener Heimflug erzeugt weiterhin nie Gelände.

Add-on-Flüge werden nicht gespeichert: Beim Stoppen des Servers enden sie ohne Ereignis, nach dem Neustart liegt die Drohne
wie jede Drohne ohne Pilot (lose Physik). Ein Add-on, das eine Mission fortsetzen will, startet sie neu.

## 5. Budget

Add-on-Flüge teilen sich das Budget des Heimflugs, Heimflüge haben Vorrang. Wie viele Add-on-Flüge gleichzeitig laufen
dürfen, stellen zwei Welt-Regeln im Tab „Welt-Regeln“ (Gruppe Spiel) bzw. mit `/gamerule` ein:

| Welt-Regel | Standard | Bereich | Bedeutung |
|---|---|---|---|
| `propwash:autonomous_flights_per_player` „Autonome Flüge je Spieler gleichzeitig“ | 1 | 0–8 | Add-on-Flüge eines Besitzers; 0 = keine |
| `propwash:autonomous_flights_total` „Autonome Flüge gesamt“ | 6 | 0–32 | Add-on-Flüge auf dem ganzen Server; 0 = keine |

Beide sind für die Rennwertung neutral und nicht in Zonen änderbar (Häkchen „in Zonen“ aus). Neben den Add-on-Flügen
bleibt für Heimflüge immer ein Platz je Spieler und zwei am Server frei: insgesamt fliegen höchstens max(2, je Spieler + 1)
Drohnen eines Spielers und max(8, gesamt + 2) am Server ohne Pilot. Mit den Standardwerten ist das wie bisher: Add-on-Flüge
belegen höchstens einen der zwei Plätze je Spieler und höchstens sechs der acht je Server. Ist kein Platz frei, gibt
`start` (und die Übergabe beim Verlassen des FPV, §6) `NO_BUDGET`, und der Besitzer bekommt in der Aktionsleiste eine
Meldung mit dem Wert, etwa „Drohne: kein weiterer autonomer Flug – höchstens 1 je Spieler gleichzeitig (Welt-Regel
„Autonome Flüge je Spieler gleichzeitig“)“ bzw. „… höchstens 6 auf dem Server gleichzeitig …“ oder bei 0 „autonome
Flüge sind in dieser Welt aus“. Ein Add-on muss dafür keine eigene Meldung zeigen. Eine niedrigere Regel beendet keine
laufenden Flüge, sie gilt für den nächsten Start. Chunk-Tickets wie im Heimflug.

## 6. Übergabe beim Verlassen des FPV

```java
PropwashApi.drones().autonomy().registerExitHandler(id, (drone, pilot) ->
        missionLaeuft(drone) ? Optional.of(AutonomyRequest.of(new Mission(pilot), AutonomyOptions.of(id).label("MISSION")))
                : Optional.empty());
```

Verlässt der Pilot die Ansicht, ohne dass ein anderer übernimmt, fragt Propwash die Handler in Anmeldereihenfolge, vor
dem eigenen Verhalten. Der erste Handler mit Antwort gewinnt: In der Luft startet der Flug nahtlos (Position, Tempo,
Lage, Motoren auf Schwebedrehzahl; die Drohne sackt nicht ab), am Boden nur mit `takeoff`. Scheitert der Start (etwa
`NO_BUDGET`) oder antwortet kein Handler, gilt Propwashs Verhalten wie in 0.5.2 (Heimflug bzw. Motoren aus). Ein Handler,
der eine Ausnahme wirft, wird übersprungen.

## 7. Anzeige beim Besitzer

- Paket `propwash:autonomy_status` (Entity, aktiv, Label, Grund des Endes) an den Besitzer. Schaut er der Drohne zu,
  steht das Label im OSD an der Stelle des Flugmodus.
- Start und Ende als Meldung in der Aktionsleiste: „Drohne fliegt selbstständig (FOLLOW)“, „Drohne: Akku schwach“ usw.
  (Texte in `common/src/lang/addon-autonomy/`).

## 8. Zonen und Funkreichweite

Zonen wirken wie im Heimflug und im FPV-Flug (Schwerkraft, Wind, Akku, Physik-Regeln, mit Überblendung an den Rändern).
Einen Flug außerhalb der Funkreichweite gibt es nicht (E39): Start nur in Startreichweite, Ende mit `OUT_OF_RANGE` jenseits
der Reichweite, in der ein Pilot getrennt würde. Danach fliegt Propwash die Drohne heim wie eine echte GPS-Drohne beim
Funkverlust; ohne GPS-Empfang an ihrer Stelle landet sie an Ort und Stelle (§4).

## 9. Beispiel und Tests

- Beispiel-Add-on: `dev/example/ExampleFollow` – Übergabe `propwash_example:mission` für Drohnen mit dem Entity-Tag
  `propwash_example_hold` („Halten“) oder `propwash_example_follow` („Folgen“, 2,5 m hinter dem Piloten, Höhe wie bei der
  Übergabe, Blick zum Piloten); `hold`, `follow`, `takeoff`, `land`, `stop` für den Selbsttest; protokolliert
  `AUTONOMY_ENDED`.
- JUnit: `rescue.AutonomousAssistTest` (Halten, Folgen, Übergabe ohne Höhenverlust, Übernahme durch den Piloten mit und
  ohne Flughilfe, Schätzung, Höhe, Tempo und Lage beim ersten Aufruf der Flughilfe und Halten der geschätzten Höhe über je
  200 Seeds, Wechsel, Ende mit dem Akku nach mehr als 10 Minuten, `maxDuration` und 60 min, Start vom Boden mit
  `land()`, alle Gründe, Ausnahmen, Wind und geringe Schwerkraft, Budget mit Vorrang des Heimflugs, Budget aus den
  Welt-Regeln mit 0, 1, 2 und 3 je Spieler sowie gesamt 1 und 10, Meldung mit dem Wert in Englisch und Deutsch).
- Selbsttest-Schritt B7 zu den Welt-Regeln des Budgets (Befund OWN2810P-1): A fliegt einen Add-on-Flug, mit Regelwert 2
  passt der zweite Flug von B ins Budget (`NOT_READY` am Boden ohne `takeoff` statt `NO_BUDGET`), mit Regelwert 1 wird
  die zweite Mission abgelehnt (`NO_BUDGET`), ebenso mit 0 und mit gesamt 1; die Meldung in der Aktionsleiste nennt
  jeweils den Wert; danach beide Regeln zurück auf den Standard.
- Selbsttest: Gruppe `addon-api-17: B7` (Fabric und NeoForge), darin die Übernahme durch den Piloten in 4 m mit Tastatur,
  einmal ohne und einmal mit einer Flughilfe, die die Höhe hält (scharf von Anfang an, höchstens 0,5 m Höhenverlust in
  3 s, Gas auf Schwebegas bzw. in der Mitte), und `land()` über Teilhöhen-Blöcken (Teppich in den Boden eingelassen,
  Teppich und Stufe auf dem Boden, Drohne 0,15 m neben der Mitte gestartet: höchstens 0,3 m neben dem Punkt von `land()`
  auf der Oberseite des Blocks) sowie der Heimflug zum Homepoint auf denselben Blöcken (Homepoint 0,22 m neben der
  Mitte, Start 4 m daneben vom Boden, Ende des Add-on-Flugs mit `TIMEOUT` nach 8 s: höchstens 0,3 m neben dem Homepoint
  auf der Oberseite).
- JUnit: `rescue.AutonomousAssistTest#landOnAPartialHeightBlockTouchesDownWhereLandWasCalled` (eingelassener Landeplatz
  mit 2 Pixeln, der wie in Minecraft nicht in der Höhenkarte steht, Teppich und Stufe für 5″ und 7″, je 0,18 m neben der
  Mitte: höchstens 0,3 m neben dem Punkt von `land()`; Treppe ohne ebene Oberseite: Landung daneben) und
  `#homeLandingOnAPartialHeightBlockTouchesDownOnTheHomepoint` (dieselben Blöcke, Heimflug aus 12 m ohne Pilot und als
  GPS Rescue des Piloten, Homepoint 0,22 m neben der Mitte: höchstens 0,3 m daneben auf dem Block; Dach aus Stufen
  2 Blöcke über dem Homepoint: keine Landung auf dem Dach).
- JUnit: `rescue.FlightStateAngleLimitTest` (Neigungsgrenze: Standard 60° für eigene Implementierungen, Client und
  Server-Flug melden die Grenze aus dem Tuning, voller Knüppel in ANGLE neigt um sie, `DroneView` gleich dem Flugmodell);
  Selbsttest-Schritt B7 „Neigungsgrenze 30° aus dem PID-Tab“ (`DroneView` am Server und am Client, `FlightState` im
  Add-on-Flug, größte Neigung bei vollem Knüppel höchstens 4° neben 30°).
- JUnit: `rescue.FlightFenceTest` (Wand vor Chunks ohne erzeugte Umgebung: Maske, offenes Gelände, Streifen aus einer
  erzeugten Reihe, 20 m/s, seitlich und zurück frei; Grenze nur beim Drücken gegen die Wand; Vorausschau 0 bis 4
  Chunks) und `rescue.AutonomousAssistTest` (ohne Erzeugen Ende mit `TERRAIN_LIMIT` 1 s nach dem Erreichen der Grenze,
  vorher `terrainLimit()` in der Flughilfe, Folge Heimflug mit GPS-Empfang, sonst Landung an Ort und Stelle; mit Erzeugen
  Warten ohne Ende; Umkehren an der Grenze; Meldung für jedes Ende in Englisch und Deutsch).
- Beitrittstest „weiter Flug ohne Pilot“ (`dev.AutonomyFarJoinCheck`, `PROPWASH_JOINTEST_AUTONOMY_FAR=1`, nur Server,
  simulierter Besitzer): 300 m über einen Korridor aus erzeugten Chunks, 300 m über einen Streifen aus einer erzeugten
  Reihe mit Regel an (Ziel erreicht, kein Stillstand über 2 s), derselbe Streifen mit Regel aus (Ende `TERRAIN_LIMIT`
  vor dem Rand, Hinweis an den Besitzer, Heimflug zum Start), hinter dem mit 30 m/s davonlaufenden Besitzer her und noch
  einmal der Streifen mit Regel aus unter einer Steindecke am Rand (Ende `TERRAIN_LIMIT`, Hinweis, ohne GPS-Empfang kein
  Heimflug, Landung an Ort und Stelle); die Server-Konfig steht für die Prüfung auf dem Standard, der Test hängt also
  nicht an `config/propwash/server.json` im Run-Ordner (Befund WAC-2); siehe `docs/testing.md`.
- JUnit: `rescue.AutonomousAssistTest#outOfRangeFliesHomeLikeAGpsDroneAndLandsInPlaceWithoutGps` (Tabelle der Folgen
  für jeden Grund mit und ohne GPS-Fix), `rescue.GpsReceptionTest` (freier Himmel, Steindecke, Wand auf einer Seite,
  Schwelle von 5 Satelliten wie beim GPS des Piloten).
- JUnit: `rescue.AutonomyGroundTest` (Befund RC3P-4): Alle Propwash-Presets ohne Zubehör, mit Speer unten (120 mm) und
  mit 360°-Gimbal vorn (125 mm), dazu Kino, Compact und Tour eines Kamera-Add-ons mit 360°-Gimbal, gerade und um 90°
  gedreht: auf dem Zubehör liegend am Boden (Hitbox bis 0,162 m über dem Boden), mit dem Zubehör über einem Loch auf
  den Standbeinen am Boden, 3, 5, 10 und 25 cm darüber schwebend nicht; den Boden berühren und sich bewegen ist nicht
  ruhen. `rescue.AutonomyNotReadyTest`: Gründe für den Start aus dem Stand und die Übergabe, `notReadyReason` als
  `default`-Methode (ältere Implementierungen kompilieren und liefern leer), Reihenfolge der Konstanten.
- Selbsttest-Schritt B7 „Start aus dem Stand auf hohem Zubehör“ (`rescue.client.AutonomyGroundSelfTests`, Befund
  RC3P-4): Freestyle 5″ mit Beispiel-Speer unten liegt lose 0,106 m hoch auf dem Speer; 5 cm darüber `NOT_ON_GROUND`
  und `NOT_READY`, liegend ohne `takeoff` `NO_TAKEOFF`, mit `takeoff` `STARTED` und Steigen auf 5 m, nach `land()` Ende
  `LANDED`, liegt wieder auf dem Speer und gilt als am Boden.
- JUnit: `interact.CargoApiTest` (Befund FB1-10): Fracht-API als `default`-Methoden mit neutralem Ergebnis, Propwash
  reicht sie an den Greifer weiter, ohne Pilot nimmt der Greifer nur nach `grabCargo` auf, der Griff bleibt 5 s offen und
  lässt sich schließen, Items mit Ziel-Spieler für Pilot oder Verursacher.
- Selbsttest-Schritt B7 „Fracht ohne Pilot“ (`rescue.client.AutonomyCargoSelfTests`, Befund FB1-10): Freestyle 5″ mit
  Greifer startet mit `takeoff` ohne Pilot, schwebt 0,5 m über 4 Bruchstein an Wegpunkt A und nimmt 30 Ticks lang nichts
  auf; nach `grabCargo` hält er die 4 Bruchstein (`cargo()`), das Flugmodell am Server ist 0,2 kg schwerer
  (`FlightState#massKg`, 0,543 → 0,743 kg); an Wegpunkt B in 1,5 m gibt `releaseCargo` 4 zurück, die Fracht liegt bei B
  am Boden, 2 s kein neuer Griff, Flugmodell wieder 0,543 kg; danach `land()` mit `LANDED`, die Fracht bleibt liegen.
- Beitrittstest „Heimflug außer Reichweite“ (`dev.AutonomyHomeJoinCheck`, `PROPWASH_JOINTEST_AUTONOMY_HOME=1`, nur
  Server, simulierter Besitzer): Der Besitzer verlässt die Funkreichweite, unter freiem Himmel fliegt die Drohne heim und
  landet am Homepoint, unter einer Steindecke landet sie an Ort und Stelle; siehe `docs/testing.md`.
- JUnit: `dev.ServerSmokeStopTest` (Befund WAC-3): Ist eine der Prüfungen nur am Server („Autonomie ohne Besitzer“ mit
  simuliertem Besitzer, „weiter Flug ohne Pilot“, „Heimflug außer Reichweite“) beim Stopp nicht fertig oder endet sie
  mit Ausfällen, meldet der Rauchtest FAIL.

## 10. Intern

- `rescue.ServerFlight`: Flugmodell des Server-Flugs (Zonen, Wind, Bodenabstand, Kontakt-Haken der Kollisionsregeln,
  Verschleiß, Schreiben ins Entity, Schärfen am Boden). `ServerReturnFlight` (Heimflug) und `AddonAutonomy.Flight` sind
  zwei Fälle davon.
- `rescue.ServerFlightAssist`: Ablauf eines Add-on-Flugs (Steigen, Flughilfe, Landung), Ende mit Akku und Dauer.
  Die Landung nimmt den Lande-Teil von `RescueAutopilot` mit der Stelle der Drohne als Ziel; `targetFits` prüft das Ziel
  mit echten Oberseiten (`RescueWorld#groundTop`, am Server und Client aus der Drohnen-Form des Blocks über
  `RescueBlocks.top`, `NaN` ohne volle ebene Oberseite) und ohne die Höhenkarte, die dünne Blöcke wie Teppich oder einen
  Landeplatz mit 2 Pixeln nicht kennt; passt es, landet die Drohne dort, sonst sucht `chooseLandingSpot` wie im
  Heimflug.
  `ServerFlightControl` (Regler aus dem Heimflug) und `ServerFlightState` (`FlightState`/`AssistOutput` am Server).
- `rescue.FlightChunks`: Chunk-Tickets eines Flugs ohne Pilot (Chunk der Drohne und jeder Nachbar, dessen Umgebung in
  Radius 2 erzeugt ist; beim Add-on-Flug mit erlaubtem Erzeugen alle Nachbarn und ein Vorausschau-Ticket mit Radius 3
  bis 4 Chunks in Flugrichtung über `RangeManager#keepAhead`); merkt sich erzeugte Chunks je Flug. `rescue.FlightFence`:
  Wand des Add-on-Flugs vor Chunks ohne erzeugte Umgebung (Reichweite 3 Chunks, alle 5 Ticks und bei jedem
  Chunk-Wechsel neu), wirkt in jedem Rechenschritt auf das Tempo des Flugmodells; `atLimit()` meldet das Drücken gegen
  die Wand höchstens 5 m davor, `ServerFlightAssist#terrainLimit` zählt es (abklingend) und beendet den Flug nach 1 s
  mit `TERRAIN_LIMIT`, wenn der Besitzer nicht erzeugen darf.
- `rescue.GpsReception`: GPS-Empfang am Server für die Folge von `OUT_OF_RANGE` und `TERRAIN_LIMIT` (gemessen nur, wenn
  die Folge davon abhängt: `ServerFlightAssist#needsGps`; Himmelssicht wie
  `drone.client.SkyView`, Schwelle 5 Satelliten wie `sim.GpsReceiver`; ungeladene Chunks zählen als offen, es wird kein
  Chunk geladen); `ServerFlight#gpsFix` misst am Schwerpunkt der Drohne.
- `rescue.GroundContact`: „am Boden“ für Start, Übergabe und Übernahme (§3): kleinster Abstand von Hitbox
  (`QuadcopterEntity#supportGap`) und jedem genutzten Bodenpunkt unter dem Fuß der Hitbox
  (`QuadcopterEntity#columnGap`, Säule wie `standOnAccessories`), Grenzen `TOUCH` 0,02 m und `REST_SPEED` 0,25 m/s;
  `AddonAutonomy#notReady` entscheidet über `NOT_READY` und den Grund für `begin` und `notReadyReason` gemeinsam.
- `rescue.AddonAutonomy`: Start, Wechsel, Ende, Budget, Übergabe, Meldungen, Paket. Haken: `AutonomousReturns#onPilotChanged`
  (Übergabe vor dem Heimflug), `QuadcopterEntity#tickServer` (Server-Autopilot für jede Drohne ohne Pilot),
  `AutonomousReturns.flying/sim` gelten auch für Add-on-Flüge (Zubehör, Stöße).
- Client: `rescue.client.AutonomyLabels` (Label je Entity), eine Zeile in `LiveOsdSource` beim Zuschauen.
- Übernahme durch den Piloten: `rescue.PilotTakeover` (scharf und in der Luft → `Handover.place` mit dem Tempo der Entity,
  dann `DroneSim#settleForTakeover`: Akku mit Spannungsabfall unter Schwebelast, Höhenschätzung ohne Rauschen der ersten
  Probe, Höhen-Integrator eingeschwungen; `DroneSim#navTick` befüllt die Höhenschätzung mit `NavHold#primeSettled` schon
  vor dem ersten Aufruf der Flughilfe; Gas-Ausgangswert), Haken in `PilotSession#start` und nach dem ersten
  Rechenschritt der Sitzung (`TransmitterInput#presetThrottle` für Tastatur und schrittweises Gamepad-Gas).

## 11. Fracht mit dem Greifer (seit 0.6.0, Befund FB1-10)

Der Greifer (`propwash:gripper`) ist fest eingebaut und hat kein `AccessoryBehavior`, `triggerAction` erreicht ihn also
nicht. Ein Flugplaner greift und lässt ihn deshalb über drei `default`-Methoden an `AccessoryRegistry` los, auch im
Add-on-Flug ohne Pilot (Handbuch §21.23):

- `grabCargo(drone, actor)` → `boolean`: öffnet den Greifer 5 s lang (`GripperSpec.GRAB_TICKS`), greift sofort, was in
  Reichweite liegt, sonst alle 4 Ticks; der Griff schließt nach der ersten Aufnahme, beim Loslassen, beim Entschärfen
  und ohne Greifer. `false` ohne Greifer, entschärft, am Client oder außerhalb des Server-Threads. Reichweite, Sicht,
  Tragkraft und Stapel wie beim Piloten (`docs/design/drone-interaction.md` §2.3); Items mit Ziel-Spieler nur für den
  Piloten oder `actor`.
- `releaseCargo(drone, actor)` → `int`: lässt los wie die Taste des Piloten (`COMMAND`, mit dem Schwung der Drohne,
  danach erst 1,5 Blöcke weiter wieder ein Griff, das Abgelegte ignoriert der Greifer 60 s) und gibt die Zahl der Items
  zurück.
- `cargo(drone)` → `List<ItemStack>`: Kopien der Fracht, am Client leer.

Ohne `grabCargo` nimmt eine Drohne ohne Pilot nichts auf, auch nicht im Heimflug; mit Pilot greift der Greifer wie
bisher selbst. Meldungen in der Aktionsleiste gehen ohne Pilot an den Spieler, der zuletzt gegriffen oder losgelassen
hat. `ServerFlight#followCargo` übernimmt eine geänderte Fracht im Flug ins Flugmodell (`AccessoryPayload#flightBuild`,
`DroneSim#setAirframe` bei gleichem Rahmen), wie der Client des Piloten; Rechte prüft der Aufrufer. Kein neues Paket,
das Netzprotokoll bleibt.
