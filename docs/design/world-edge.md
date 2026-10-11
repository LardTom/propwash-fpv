# Drohnen und neue Chunks, Rand der erzeugten Welt – Propwash 0.5.0

Auftrag des Besitzers (08.10.2026), sinngemäß: eine Welt-Regel, die festlegt, ob Drohnen neue Chunks erzeugen dürfen.
Ist sie aus, laden Drohnen nur Gelände, das es schon gibt. Am Rand der erzeugten Welt soll nichts abstürzen, keine
Drohne endlos ins Leere fliegen und das FPV-Bild keine bleibenden Löcher haben; das Verhalten am Rand ist selbst zu
entscheiden und zu begründen.

## 1. Wie Drohnen Chunks laden (Stand vor der Regel)

| Fall | Was lädt | Erzeugt neue Chunks? |
|---|---|---|
| Fliegen mit unbegrenzter Reichweite (FPV und Sichtflug, `AnchorRules.Decision.ANCHOR`) | Sichtanker: Chunk-Tracking des Spielers um den Drohnen-Chunk (`ViewAnchors`, Mixins in `mixin.server`); Tickets `propwash:drone` (Radius 2, Simulation), `propwash:drone_view` (Sichtweite + 2, nur Laden), `propwash:drone_lookahead` (in Flugrichtung, nur Laden), `propwash:pilot_anchor` (Radius 2 am Körper) | ja, wenn der Spieler darf (`ServerRules.mayGenerateChunks`) |
| Fliegen mit begrenzter Reichweite (`LIMITED`) | Sicht des Körpers wie in Vanilla, dazu `propwash:drone` Radius 2 | ja (Ticket), der Körper sowieso |
| Zuschauen (`SpectatorLinks`) | Anker am beobachteten Drohnen-Chunk, Tickets nur, wenn der Pilot erzeugen darf | wie der Pilot |
| GPS Rescue mit Pilot | Flugmodell am Client, dieselben Tickets wie beim Fliegen | wie beim Fliegen |
| GPS Rescue ohne Pilot (`AutonomousReturns`) | `propwash:drone` Radius 2 über `RangeManager#keepSimulated`, nur wenn `RangeManager#generatedAround` stimmt; die Route meidet unerforschtes Gebiet | nie |
| Dedizierter Server | wie oben; `server.json` (`onlyGeneratedChunks`, `generateChunksPermissionLevel`, `hostGeneratesChunks`) beschränkt das Erzeugen auf Rechtestufen, Standard: alle | Standard ja |

Warum ein Ticket erzeugt: `addTicketWithRadius` setzt im Radius r die Stufe FULL. Ein Chunk, der schon als FULL gespeichert
ist, lädt über die Lade-Pyramide (`ChunkPyramid.LOADING_PYRAMID`, Nachbarn brauchen nur `INITIALIZE_LIGHT`, das jeder
fertige Chunk um sich hat) ohne Erzeugung; ein fehlender oder halbfertiger Chunk läuft durch die Erzeugungs-Pyramide
(`ChunkGenerationTask#canLoadWithoutGeneration`). Chunks außerhalb des Ticket-Radius (Stufe 34 und höher) entstehen
nur als Abhängigkeit eines FULL-Chunks. Ein Ticket erzeugt also genau dann nichts, wenn jeder Chunk in seinem Quadrat
schon fertig gespeichert ist.

Warum ein Chunk beim Client ankommt: Der Server schickt nur Chunks, die Blöcke ticken (`ChunkHolder#getChunkToSend` →
`getTickingChunk`, Stufe 32). Dafür müssen alle acht Nachbarn FULL sein. Ein erzeugter Chunk direkt neben einem
fehlenden (**Randchunk**) kann ohne Erzeugung also nie gesendet werden: Er lädt am Server, bleibt für den Client aber
ein Loch.

Bisher war das eingeschränkte Laden (nur mit `server.json`) grob: Das Sicht-Ticket schrumpfte auf den größten ganz
erzeugten Ring um die Drohne (`GeneratedArea.Scan#generatedRadius`), am Rand blieben so nur 5 × 5 Chunks sichtbar
(Löcher im FPV-Bild bis tief ins erzeugte Gebiet), und zwei Ringe vor dem ersten fehlenden Chunk wurde der Pilot
gelöst („Funkverbindung verloren: unerforschtes Gebiet“).

## 2. Die Regel

`propwash:drones_generate_chunks` (Schalter, Standard **an** = wie bisher, Gruppe Spiel). Aus: Keine Drohne erzeugt
neue Chunks – auch nicht die des Hosts oder eines Operators. An: Es gilt wie bisher `server.json` (Rechtestufen,
Standard alle). `ServerRules.mayGenerateChunks` = Regel an **und** `ServerRulesConfig#allowsGeneration`. Der Tab
„Server“ zeigt bei „Neue Chunks“ dann „Nein (Welt-Regel aus)“. Die Regel steht in keiner Voreinstellung anders als
Standard (Server-Organisation wie Videokanäle und GPS Rescue ohne Pilot).

## 3. Verhalten ohne Erzeugung

1. **Laden ohne Löcher** (`server/GeneratedCover`, reines Java): Ist das Sicht-Quadrat um die Drohne nicht ganz erzeugt,
   setzt der Server statt eines schrumpfenden Tickets mehrere `propwash:drone_view`-Tickets. Ihre tickenden Kerne
   (Ticket-Radius − 1) decken zusammen jeden **inneren** Chunk im Sicht-Quadrat ab (erzeugt und alle acht Nachbarn
   erzeugt), ihr FULL-Rand liegt nur auf erzeugten Chunks. So bekommt der Client alles, was er ohne Erzeugung bekommen
   kann, und nichts entsteht. Gierig von oben links: für jeden noch nicht abgedeckten inneren Chunk das größte ungerade
   Quadrat aus inneren Chunks, das ihn enthält (Suche 16 Chunks zurück), Ticket-Radius = halbe Seite + 1, höchstens
   256 Tickets (dann die nächsten zuerst). Ein gerader Rand kostet etwa 10–40 Tickets, ganz erzeugtes
   Gebiet bleibt ein Ticket wie bisher. Solange Chunks noch geprüft werden, bleiben die Tickets des letzten
   vollständigen Stands zusätzlich stehen (sie lagen nur auf erzeugten Chunks). Chunks mit unbekanntem Stand lädt
   nichts.
2. **Rand-Maske an den Piloten** (`server/EdgeMask`, Paket `propwash:generation_edge` = `GenerationEdgeS2C`): 13 × 13
   Chunks um den Drohnen-Chunk, ein Bit je gesperrtem Chunk: sicher fehlend (gespeichert nicht FULL) oder ein Randchunk
   neben einem sicher fehlenden; unbekannte zählen nicht.
   Gesendet nur bei Änderung (Drohnen-Chunk oder Prüfstand), ohne Einschränkung einmal „aus“, beim Lösen ebenso.
   Die Maske reicht mindestens 96 Blöcke vor die Drohne. Der Speicher der Prüfungen (`GeneratedChunks`) vergisst
   „fehlt“ nach 30 s und erfährt über einen Mixin auf `ChunkMap#onFullChunkStatusChange` sofort, wenn ein Chunk FULL
   wird – etwa weil ein Spieler hinläuft oder Drohnen mit Regel an dort Gelände erzeugt haben. Sonst hielte ein
   veralteter Eintrag dort den Zaun hoch, ließe ein Loch und könnte den Piloten lösen.
3. **Grenzzaun am Client** (`server/EdgeFence` reines Java, `drone/client/EdgeGuard`): Aus der Maske werden die freien
   Kanten fehlender Chunks zu Strecken zusammengefasst (gerade Ränder sind eine Strecke, konkave Ecken zwei). Vor jedem
   Schritt des Flugmodells begrenzt der Zaun die Geschwindigkeit **auf jede Kante zu** auf
   `√(2 · 20 m/s² · (Abstand − 4 m))`: Die Drohne bremst wie mit 2 g auf 4 Blöcke vor dem ersten gesperrten Chunk ab
   (dem letzten erzeugten vor dem Rand, also 20 Blöcke vor dem ersten fehlenden) und bleibt dort, immer über Gelände,
   das der Client hat; quer zur Kante gleitet sie ungebremst, wegfliegen geht immer. Innerhalb der 4 Blöcke schiebt der Zaun mit
   bis zu 3 m/s zurück (4/s je Meter), damit eine dauernd gegen den Zaun drückende Drohne nicht Bild für Bild weiter
   hineinkriecht. Eingriff über `DroneSim#setHorizontalVelocity`, nur wenn eine Maske da ist – sonst rechnet das
   Flugmodell bitgleich wie vorher. Gilt für Pilot, Höhe/Position halten und GPS Rescue gleichermaßen.
4. **Anzeige:** 32 Blöcke vor dem Rand oder sobald der Zaun bremst, zeigt das OSD „WELTRAND“ / „WORLD EDGE“ (Warnung,
   blinkt nicht, im FPV und im Funk-HUD des Sichtflugs), dazu einmal ein Hinweis (Toast, wieder frühestens nach 20 s
   ohne Warnung): Rand der erzeugten Welt, Welt-Regel aus, die Drohne bremst.
5. **Harte Grenze am Server** (unverändert): Bewegungspakete in nicht geladene Chunks verwirft `DronePiloting` und setzt
   die Drohne zurück – ein veränderter Client ohne Zaun kommt nicht weiter. Gelöst wird der Pilot nur noch, wenn der
   Chunk der Drohne **selbst** fehlt (`AnchorRules`: `missingWithin(0)`, praktisch nur nach einem Teleport); vorher
   reichten zwei Ringe.
6. **Simulation am Rand:** Das Ticket `propwash:drone` (Radius 2, Entity tickt) braucht ganz erzeugte 5 × 5 Chunks und
   fehlt deshalb im letzten erreichbaren Chunk vor dem Zaun. Die geflogene Drohne bewegt das Flugmodell am Client; am Server
   tickt dort nur ihr Greifer nicht (Fracht bleibt hängen, fällt nicht). Abgestellte Drohnen am Rand verhalten sich wie
   jede Entity in einem Rand-Chunk.

## 4. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Id `propwash:drones_generate_chunks`, Standard an | Namensschema der Welt-Regeln (`docs/design/world-rules.md` §2); an = bisheriges Verhalten | `propwashDronesGenerateChunks` (camelCase passt nicht zu 26.3) |
| Aus gilt für alle, auch Host und Operatoren; `server.json` regelt nur, wenn die Regel an ist | eine Welt-Regel gilt für alle; Betreiber, die nur Gäste beschränken wollen, haben weiter die Rechtestufen | Regel nur für Spieler ohne Rechte; `server.json` abschaffen |
| Weicher Zaun mit Bremskurve statt Lösen oder harter Wand | kein Absturz, kein Signalverlust, kein Schaden, die Drohne fliegt danach normal weiter; fühlt sich an wie das Geofence von INAV | Pilot lösen (bisher, harsch); unsichtbare Kollisionswand (Aufprall, Crash-Schaden); Failsafe/Absinken am Rand (Drohne verloren) |
| Randchunks (erzeugt, aber neben einem fehlenden) zählen zum Zaun | der Server sendet nur tickende Chunks, ein Randchunk tickt erst, wenn sein fehlender Nachbar entsteht; ohne diese Regel flöge die Drohne über Gelände, das der Client nie bekommt (Loch unter der Drohne, Kollision mit unsichtbaren Blöcken) | Randchunk von Hand senden (Blockänderungen kämen nicht an); Nachbarn bis FULL erzeugen (bricht die Regel) |
| Stopp 4 Blöcke vor dem ersten gesperrten Chunk, Bremsen mit 20 m/s² | nah genug, dass man den Rand sieht, Abstand für den Hitbox-Rand; 20 m/s² hält eine Drohne mit 55 m/s innerhalb der Maske an | Stopp schon 2 Chunks vorher (damit das Simulationsticket bleibt) – zu weit vom sichtbaren Rand |
| Zurückschieben im Stopp-Bereich (bis 3 m/s) | ohne kriecht eine dauernd drückende Drohne je Bild um a·Δt² weiter (JUnit `fullSpeedIntoAStraightEdgeStopsBeforeIt` fiel ohne durch) | nur auf 0 begrenzen |
| Kanten zu Strecken zusammenfassen und nur von außen wirken lassen | Ecken benachbarter Chunks bremsten sonst das Gleiten an einer geraden Kante | jeden fehlenden Chunk als eigenes Quadrat |
| Unbekannte Chunks sind kein Zaun | die Prüfung dauert Ticks; ein Zaun aus Unbekannten würde bei jedem Chunk-Wechsel kurz bremsen; der Server bleibt hart | Unbekannt = Rand |
| Lösen nur noch im fehlenden Chunk selbst | der Zaun hält die Drohne draußen; das Lösen zwei Ringe vorher war die einzige Grenze und traf auch `server.json`-Einschränkungen | Lösen beibehalten |
| Mehrere Sicht-Tickets aus Quadraten statt eines schrumpfenden Tickets | keine Löcher: alles schon Erzeugte im Sicht-Quadrat ist geladen und wird gesendet, nichts anderes | Einzel-Tickets je Chunk (bis 1000+); Quadtree auf festem Raster (viele Einzel-Tickets am Rand des Sicht-Quadrats) |
| Kein Vorausladen ohne Erzeugung | das Sicht-Quadrat ist schon Sichtweite + 2 Chunks groß; geladen wird nur Gespeichertes (schnell) | Vorausladen auch abdecken |
| Chunk wird FULL → sofort „erzeugt“ im Speicher (Mixin) | Regel an/aus im Spiel und Spieler, die Gelände erzeugen, machen alte „fehlt“-Einträge sonst bis zu 30 s falsch (im Selbsttest: Pilot über erzeugtem Gelände gelöst) | nur alle 30 s vergessen (wie bisher); beim Umschalten der Regel alles vergessen (hilft nicht gegen Spieler) |
| GPS Rescue ohne Pilot unverändert | meidet unerforschtes Gebiet schon immer und erzeugt nie | an die Regel koppeln |
| Netzprotokoll bleibt 11 | 0.5.0 ist unveröffentlicht und zählt schon auf 11 (`world-rules.md` §2) | 12 |

## 5. Technik

| Datei | Inhalt |
|---|---|
| `server/rules/WorldRuleSpecs` | `DRONES_GENERATE_CHUNKS` |
| `server/ServerRules#mayGenerateChunks` | Regel und `server.json` |
| `server/GeneratedCover` | reines Java: `cover(lookup, x, z, radius)` → Quadrate (`Square`, `contains` = FULL, `ticks` = gesendet), `interior`, `MAX_SQUARES` 256 |
| `server/EdgeMask` | reines Java: 13 × 13 Bits gesperrter Chunks (fehlend oder Randchunk), `scan`, `blocked`, `any` |
| `server/EdgeFence` | reines Java: Strecken aus der Maske, `distance`, `limit(x, z, v)`, `allowedApproach`; `STOP_DISTANCE` 4, `BRAKE` 20, `WARN_DISTANCE` 32, `RETURN_RATE` 4, `RETURN_MAX` 3 |
| `server/GenerationEdgeS2C` | `propwash:generation_edge` (eingeschränkt, Mitte, drei `long`) |
| `server/RangeManager` | Abdeckung statt schrumpfendem Sicht-Ticket (`coverFor`, `wantView`, letzter vollständiger Stand in `PilotLink#stableCover`), Prüfradius mindestens 6, `sendEdge` |
| `server/AnchorRules` | Lösen über unerforschtem Gebiet nur im fehlenden Chunk selbst |
| `server/GeneratedChunks`, `mixin/server/ChunkMapMixin` | `markGenerated`: ein Chunk, der FULL wird, gilt sofort als erzeugt (ein Prüfergebnis „fehlt“ überschreibt das nicht mehr) |
| `server/client/ClientGenerationEdge` | Maske und Zaun am Client, beim Trennen geleert |
| `drone/client/EdgeGuard` | Zaun vor jedem Schritt (`PilotSession`), Warnung, Hinweis, Zähler für Tests |
| `sim/DroneSim#setHorizontalVelocity` | einzige neue Stelle im Flugmodell, nur vom Zaun aufgerufen |
| `osd/OsdWarnings` | `WORLD_EDGE` (`OsdData#worldEdge`) |

## 6. Tests

- JUnit: `GeneratedCoverTest` (ganz erzeugt = ein Ticket, gerader Rand ≤ 40 Tickets und jeder innere Chunk tickt,
  Unbekannte nie, zufällige Inseln exakt, Randchunks ticken nie, Schachbrett ohne Ticket, viele kleine Inseln gedeckelt),
  `EdgeFenceTest` (Maske mit Randchunks, Bremskurve, voller Schub gegen einen geraden
  Rand mit 5–55 m/s hält vor der Stopplinie, Gleiten ohne Bremsen, Wegfliegen frei, konkave Ecke, einzelnes Loch
  umfliegen, Kanten hinter der Drohne, Paket), `AnchorRulesTest` (Lösen nur im fehlenden Chunk), `OsdModelTest`
  (Warnung), `RulePresetTest`, `WorldRulesExportTest` (Texte, Export).
- Selbsttest „weltrand: Drohne bremst am Rand der erzeugten Welt, nichts Neues entsteht, keine Löcher“ (beide Loader):
  Regel aus, Rand der erzeugten Welt in Richtung +X suchen (drei Chunk-Reihen, daraus die Zaunlinie), FPV-Sitzung,
  Drohne 44 Blöcke vor den Zaun setzen, scharf schalten und 8 s lang mit 25 m/s gegen den Rand drücken. Geprüft: Pilot
  bleibt verbunden, Drohne bleibt vor der Stopplinie und erreicht sie, Zaun bremst, OSD meldet `WORLD_EDGE`, Server
  lässt nicht erzeugen, die Chunks hinter dem Rand bleiben ungespeichert, jeder innere Chunk in 7 × 7 um die Drohne
  ist am Server geladen und beim Client da.
  Danach Regel an: Zaun verschwindet, die Drohne fliegt über den Rand, der Chunk dort entsteht.
- Bildschirmfotos (Fabric): `world_edge_fpv`, `world_edge_fpv_de` (Drohne am Zaun, Blick schräg an der Kante entlang:
  links das erzeugte Gelände bis zum Zaun, rechts dahinter nichts; Kreativ-Brille, damit das Bild bei jeder Entfernung
  sauber ist), `configurator_world_rules_drones_generate_chunks`.
