# GPS Rescue ohne Pilot und bessere Hinderniserkennung – Propwash 0.5.0

Auftrag des Besitzers (05.10.2026, sinngemäß): Läuft GPS Rescue (Heimflug), soll die Drohne weiterfliegen, wenn der
Pilot sie verlässt – Steuerung beendet, Brille ab, abgemeldet –, bis sie gelandet ist, statt mit abgeschalteten Motoren
vom Himmel zu fallen. Bisher simuliert nur der Client des Piloten das Flugmodell; ohne Pilot fällt die Drohne als
einfache Entity. Dazu soll die Hinderniserkennung klüger werden: früh auf sichere Höhe steigen, vorausschauend prüfen,
um Bäume und Gebäude herumfliegen, keinen Landeplatz auf Wasser, Lava oder Laub wählen und bei einer Sackgasse schweben
oder landen statt endlos zu suchen. Eine Welt-Regel schaltet das Weiterfliegen ab (Standard an).

## 1. Ablauf für Spieler

1. Der Pilot startet den Heimflug (Taste J, Funkenschalter oder Failsafe bei Funkverlust).
2. Er steigt aus: Funke benutzen (Flug beenden), Funke weglegen, Spiel verlassen, Dimension wechseln, sterben oder die
   Reichweite des Servers verlassen. Die Brille abzusetzen wechselt wie bisher nur auf Sichtflug.
3. Der Server fliegt die Drohne mit demselben Flugmodell und demselben Autopiloten weiter, landet sie am Homepoint und
   schaltet ab. Der Pilot bekommt im Chat „„Freestyle 5"“ fliegt allein zum Homepoint und landet dort.“ und nach der
   Landung „… ist gelandet, 0 m vom Homepoint.“
4. Wer die gebundene Funke benutzt, während die Drohne allein fliegt, lässt sie sofort an Ort und Stelle landen (danach
   lässt sie sich wie gewohnt fliegen). Shift + Funke zeigt „… fliegt allein heim · noch 34 m“.

## 2. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Der Server simuliert die Drohne mit `DroneSim` und `RescueAutopilot` (dieselben Klassen wie am Client), 4–8 Teilschritte je Tick | gleiches Verhalten wie im gesteuerten Heimflug, Flugmodell bitgleich unverändert; ein Tick kostet in JUnit unter 2 ms (gemessen deutlich weniger) | eigenes vereinfachtes Flugmodell auf dem Server – zweites Verhalten, das auseinanderläuft |
| Der Client meldet den laufenden Heimflug (`RescueStatusC2S`: Homepoint, Einstellungen, Homepoint beim Piloten) bei Start, Änderung und Ende; der Server übernimmt nur, wenn die letzte Meldung „aktiv“ vom eben gelösten Piloten stammt | der Homepoint und die Einstellungen sind persönlich und liegen am Client; nur so weiß der Server beim Lösen (auch durch Server-Gründe wie Reichweite oder Abmelden) Bescheid | Übergabe-Paket erst beim Aussteigen – fehlt bei Server-seitigem Lösen und beim Abmelden |
| Übernahme beim Lösen des Piloten (`QuadcopterEntity.PILOT_CHANGED`), Start im nächsten Server-Tick der Drohne | alle Wege, die einen Piloten lösen, laufen über `setPilot(null)`; Aufheben oder Entfernen der Drohne startet nichts | jede Lösestelle einzeln anpassen |
| Startzustand: Lage und Position der Entity, Tempo aus dem letzten Flugpaket, scharf, Drehraten und Motoren aus der Ruhe (`rescue.Handover`) | mehr weiß der Server nicht; die Motoren laufen in wenigen Hundertstelsekunden hoch (JUnit: Lage und Tempo bleiben gleich, Landung ohne Aufprall) | den ganzen Sim-Zustand vom Client übertragen – großes Paket für ein paar Zentimeter Durchsacken |
| Ohne Pilot landet die Drohne immer, auch mit „Automatisch landen“ aus | niemand kann übernehmen; Schweben bis der Akku leer ist, hielte Chunks unnötig geladen | Schweben über dem Homepoint wie mit Pilot |
| Funke benutzen, während sie allein fliegt = sofort landen (`StartResult.RETURNING`) | sicherer Eingriff ohne Übergabe eines Flugzustands mitten in der Luft; danach normal fliegen | Übernahme in der Luft (scharfer Sim beim Client, Abgleich des Arm-Schalters) – riskant und eine große Änderung an `PilotSession`; möglich für später |
| Chunks: Die Drohne hält in jedem Tick, in dem sie fliegt, ein Ticket `propwash:drone` (Radius 2, wie beim Piloten) an ihrem Chunk; nur wenn alle Chunks in Radius 2 schon erzeugt sind | genau die Chunks um die Drohne, nicht ihr Weg; autonome Flüge erzeugen nie neue Chunks, auch wenn der Server das Erzeugen erlaubt | Weg bis zum Homepoint laden (viele Chunks), Chunks erzeugen ohne Spieler |
| Das Ticket gilt in jedem Tick für den Chunk vor **und** nach der Bewegung (beim Chunk-Wechsel beide) | Tickets wirken erst im nächsten Tick; ohne das Ticket am neuen Chunk stünde die Drohne nach dem Wechsel in einem Chunk, der nur Blöcke tickt, und bliebe stehen, sobald kein Spieler in der Nähe ist (im Selbsttest mit dem Spieler 320 Blöcke weg gefunden) | Ticket nur am Chunk vor der Bewegung |
| Der Autopilot sieht nicht geladene oder nicht freigegebene Chunks als Wand (`ServerRescueWorld`) | er fliegt nie in einen Chunk, in dem er nicht weiterticken könnte | ungeladene Chunks als Luft (so am Client) – die Drohne bliebe an der Grenze stehen |
| Entlädt der Chunk trotzdem (Server-Stopp, Spieler weg ohne Ticket, Welt geschlossen), pausiert der Flug sauber: Der Plan (`ReturnPlan`) liegt in den NBT-Daten der Drohne (`ReturnPlan`), beim nächsten Laden fliegt sie weiter | nichts geht verloren, kein Absturz beim Speichern | Flug beim Entladen abbrechen – Drohne hinge beim Laden ohne Motoren in der Luft und fiele |
| Budget: höchstens 8 Flüge je Server und 2 je Spieler gleichzeitig (`AutonomyBudget`); darüber sinkt die Drohne ohne Flugmodell senkrecht zu Boden (1,5 m/s) und schaltet ab, ohne eigenes Ticket | Rechenzeit und Chunk-Tickets bleiben begrenzt, auch bei vielen Spielern; die Drohne fällt trotzdem nicht | darüber hinaus wie bisher fallen; Budget als Server-Config (für 0.5.0 nicht nötig) |
| Höchstens 10 Minuten (12 000 Ticks) ohne Pilot, dann Landung an Ort und Stelle, nach weiteren 60 s aus | auch mit unendlichem Akku (Welt-Regel 0 %) endet jeder Flug | unbegrenzt |
| Homepoint aus der Meldung nur innerhalb der Funkreichweite des Piloten (`RadioLink.releaseRange` + 32) und außerhalb eines für ihn gesperrten Spawn-Bereichs; weiter als 1500 m von der Drohne → Landung an Ort und Stelle | ein veränderter Client kann die Drohne nicht weiter schicken, als der Pilot selbst fliegen dürfte | jede Meldung annehmen |
| Homepoint „Beim Piloten“: Der Server folgt dem Piloten, solange er online und in derselben Dimension ist (höchstens 1500 m), sonst gilt die letzte Position | so landet sie auch beim Weglaufen neben dem Piloten | feste letzte Position |
| Welt-Regel `propwash:autonomous_rescue` (Schalter, Standard an, Gruppe Spiel, keine Physik); aus = Motoren aus wie bisher, auch mitten im Flug | Server-Betreiber, die keine autonomen Drohnen wollen, schalten es ab | Server-Config (gehört laut Besitzer zu den Welt-Regeln) |
| Crash-Verschleiß im autonomen Flug rechnet der Server selbst (`DroneWear.apply` mit der Welt-Regel) | sonst wäre ein autonomer Absturz folgenlos | keinen Verschleiß |

## 3. Bessere Hinderniserkennung (gilt mit und ohne Pilot)

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Sicherheitshöhe aus dem ganzen Weg: beim Start schaut der Autopilot den geraden Weg bis 256 m über die Höhenkarte ab (1-m-Schritte, Fußabdruck + Sicherheitsabstand) und steigt gleich auf höchsten Punkt + Abstand + 0,5 m | ein Bergrücken weit hinten führt nicht mehr zu Treppenstufen und Bremsen kurz davor (JUnit `distantRidgeIsClimbedBeforeTheCruise`) | nur vorausschauend 12–30 m (bisher, bleibt zusätzlich) |
| Lokaler Wegplaner `RoutePlanner`: A* auf dem Blockraster um die Drohne (±20 Blöcke waagerecht, −6/+12 senkrecht, 8 Nachbarn + hoch/runter, Ecken nicht schneiden), höchstens 2500 Knoten, Belegung je Zelle einmal geprüft, Weg mit Sichtlinien geglättet; zuerst mit halbem Sicherheitsabstand, dann eng (für Türen) | findet Wege aus Hallen durch Türen und um Mauern unter Decken, die gerades Ausweichen nicht findet; begrenzte Kosten, nur bei Bedarf und höchstens einmal je Sekunde und Ziel | vollständige 3D-Karte, Planung über den ganzen Weg |
| Ziele: „freier Himmel“ (Ausweichen unter Dach) und „Richtung Homepoint mit 10 m freier Sicht“ (Ausweichen im Flug) | eine Suche für beide Fälle; Teilwege, wenn das Ziel außerhalb liegt (mindestens 2 m näher), danach neu planen | nur ein festes Zielfeld |
| Wegfolge mit Querabweichungs-Regler (bleibt auf der freien Linie), 3 m/s, in engen Durchlässen 1,5 m/s | Wegpunkte mit großem Radius schnitten Türrahmen an | Wegpunkt für Wegpunkt mit Positionsregler (stockend) |
| Landeplatz-Prüfung: unter dem Fußabdruck nur feste, ebene Blöcke; Wasser, Laub (`#leaves`) und uneben gelten als schlecht, Lava, Feuer, Lagerfeuer, Magma, Kaktus, Beerenstrauch, Pulverschnee, Wither-Rose und Tropfstein als gefährlich (`RescueBlocks`, `Ground`); Suche bis 12 m um den Homepoint, zuerst ±2 Blöcke um seine Höhe, dann bis 24 Blöcke tiefer (Homepoint auf einer Baumkrone → Boden darunter) | ein Homepoint im Teich, auf der Baumkrone oder neben Lava endet nicht mehr im Wasser oder Feuer | nur überdachte Homepoints verlegen (bisher) |
| Festgefahren: nach drei Versuchen ohne mindestens 3 m Fortschritt gibt der Autopilot auf – mit Pilot schwebt er (Meldung „GPS Rescue findet keinen Weg – die Drohne schwebt, übernimm die Steuerung“), ohne Pilot landet er an Ort und Stelle (fester Boden in bis zu 8 m, sonst darunter) | kein endloses Hin und Her; der Pilot kann übernehmen, ohne Pilot endet der Flug am Boden | endlos neu versuchen (bisher) |

## 4. Technik

| Datei | Inhalt |
|---|---|
| `rescue/RescueAutopilot` | neu `setAutonomous`, `landNow(world)`, `stuck()`, `gaveUp()`, `following()`, `landX/landZ`; Weg aus `RoutePlanner` (`ThreadLocal`, je Thread ein Planer), Landeplatz `spotQuality`/`columnGround`, Sicherheitshöhe `pathAltitude` |
| `rescue/RoutePlanner` | A*-Planer (reines Java, `RescueWorld`), `Goal` mit `estimate`/`reached` |
| `rescue/Ground`, `RescueWorld#ground` | Art des Blocks für die Landeplatz-Prüfung (Standard: fest/nichts aus `solid`) |
| `rescue/RescueBlocks` | Einordnung eines `BlockState` (Fluid, `#leaves`, Gefahren, Kollisionsform) für Client und Server |
| `rescue/ReturnPlan` | Plan an der Entity (Besitzer, Homepoint, beim Piloten, Höhe, Tempo, Sinkrate, Hinderniserkennung, Abstand, Mindestentfernung, sofort landen, Ticks), NBT `ReturnPlan` (`QuadcopterEntity.RETURN_PLAN_KEY`) |
| `rescue/net/RescueStatusC2S` | `propwash:rescue_status`: Entity, aktiv, Homepoint (x/y/z), beim Piloten, Einstellungen; nur vom Piloten der Drohne angenommen |
| `rescue/AutonomousReturns` | Server: Meldungen, Übernahme bei `PILOT_CHANGED`, Tick-Hook `QuadcopterEntity#setServerAutopilot`, Budget, Absinken, Zeitgrenze, Chat-Meldungen, Aufräumen bei `TICK_END`/`STOPPED` |
| `rescue/ServerReturnFlight` | ein Flug: Sim aus der Entity, Umwelt (Bodenabstand, Fluid, Luftdichte, Wind über `drone.WindModel`), Bewegen mit `Entity#move`, Rückmeldung an den Sim, Entity-Zustand (Lage, scharf, Akku, Motoren, Tempo), Verschleiß, Neuabgleich nach Teleport |
| `rescue/ServerRescueWorld` | `RescueWorld` über `ServerLevel` (nur geladene, freigegebene Chunks; eigener Chunk immer) |
| `rescue/AutonomyBudget`, `rescue/Handover` | reines Java: Budget je Server/Spieler, Sim-Zustand aus Entity-Lage und -Tempo |
| `rescue/client/RescueController` | meldet den Heimflug (Start, Homepoint > 0,5 m bewegt höchstens alle 250 ms, Einstellungen, Ende) und zeigt „findet keinen Weg“ |
| `drone/QuadcopterEntity` | `returnPlan()`/`setReturnPlan`/`returningHome()`, Hook `setServerAutopilot` in `tickServer` (statt loser Physik), NBT, neuer Pilot löscht den Plan |
| `drone/DronePiloting` | `StartResult.RETURNING` (Funke → sofort landen, Meldung), Shift-Status beim Heimflug |
| `server/RangeManager` | `generatedAround(level, x, z)`, `keepSimulated(level, chunk)` (Ticket über den gemeinsamen `TicketLedger`) |
| `server/ServerRules#forbiddenSpawnArea(spieler, level, pos)` | Spawn-Schutz für eine Position |
| `server/rules/WorldRuleSpecs.AUTONOMOUS_RESCUE` | Welt-Regel `propwash:autonomous_rescue` |
| `drone/WindModel` | von `drone.client` nach `drone` (nur `Level`), damit der Server denselben Wind rechnet |

Netzwerkprotokoll 11 (neues Paket). Keine API-Änderung. Das Flugmodell ist unverändert (Goldens und Kalibrierung
bitgleich).

## 5. Grenzen

- Der Server prüft den Weg nicht gegen gesperrte Spawn-Bereiche (nur den Homepoint); mit der Standard-Einstellung
  `allowPilotingInSpawnProtection = true` gibt es keine.
- Treffer an Lebewesen rechnet nur der gesteuerte Flug (Client); autonome Drohnen weichen Blöcken aus, nicht Mobs.
- Andere Spieler können eine allein fliegende Drohne nicht übernehmen, nur mit der gebundenen Funke landen lassen.
- Kurz nach der Übernahme sackt die Drohne um wenige Zentimeter durch, weil die Motoren aus der Ruhe hochlaufen.

## 6. Tests

- JUnit `RescueRoutingTest`: Homepoint im Teich, auf Lava, auf der Baumkrone, Halle mit Tür, Mauer unter breiter Decke,
  Bergrücken weit hinten, Höhle mit Pilot (schwebt) und ohne (landet), sofort landen, ohne „Automatisch landen“,
  A* um ein U (Budget, Weg frei, Laufzeit), Landeplatz-Einordnung.
- JUnit `AutonomousHandoverTest`: Pilot steigt im Heimflug aus → Server-Flug landet am Homepoint, Lage und Tempo
  bleiben bei der Übergabe, Hindernis nach der Übergabe umflogen, Plan bereinigt Einstellungen und landet immer,
  Codec, Budget, Kosten je Server-Tick.
- Bestehende `RescueAutopilotTest`, `RescueSupervisorTest` unverändert grün.
- Selbsttest Fabric (`rescue: ohne Pilot – …`): echter Heimflug am Client, Funke benutzt → Server fliegt weiter und landet
  am Homepoint (mindestens 8 m gestiegen); Welt-Regel aus → Motoren aus, Drohne am Boden; in einer Halle mit Tor, Funke
  abgelegt → Drohne fliegt durchs Tor und landet am Homepoint; Entity gespeichert, entladen, 20 Ticks weg, neu geladen,
  Spieler 320 Blöcke weg → fliegt weiter und landet. Mit `-PpropwashScreenshots=true` zusätzlich
  `rescue_autonomous_landing.png` (Landeanflug ohne Pilot) und `rescue_autonomous_door.png` (Drohne im Tor).
