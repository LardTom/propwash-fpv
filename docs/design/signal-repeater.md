# Signal-Repeater und Kreativ-Brille in Rennen (ab 0.5.0)

Rückmeldung aus der Community (sinngemäß): In einem großen Betonbau mit mehreren Etagen kommt man wegen des
Signalverlusts nicht einmal in die zweite Etage; mit der Kreativ-Brille geht es, aber dann zählen die Rundenzeiten nicht.
Gewünscht sind kleine Module, die man an großen Bauten anbringt, damit man drinnen eine Rennstrecke bauen kann – oder
Rundenzeiten mit der Kreativ-Brille. Der Besitzer hat zugestimmt: unendliche Reichweite zählt nicht als Flughilfe.

Code: Modul `relay` (Block, Block-Entity, Verzeichnis, Pfadsuche) und `relay.client` (Strecken fürs Bild und für den
Funk), Gründe für nicht gewertete Runden in `race.LapAssist` und `race.client.AssistText`.

## 1. Was die Kreativ-Brille ändert

| Wirkung | Wo | Zählt als Flughilfe? |
|---|---|---|
| Funk ohne feste Reichweite (nur die Server-Grenze, bei eigener Reichweite im Video-Tab ab dieser) | `RadioLink.freeRange`, `DronePiloting` | nein |
| Videoreichweite unbegrenzt oder eigene Reichweite | `VideoState#range` | nein |
| Hindernisse dämpfen das Bild nur mit „Hindernisse“ an | `VideoState#obstructs` | nein |
| sauberes Bild (keine Störungen bei unendlicher Reichweite, volle Bildqualität), optional eigenes Rauschen | `VideoState#onFrame` | nein |
| empfängt jedes Videosystem | `VideoSystem.receives` | nein |
| eigenes Sichtfeld statt der Kamera des Videomoduls | `VideoConfig#horizontalFov` | nein (Anzeige, wie die Wahl einer anderen Kamera) |
| eigene Latenz statt der des Videosenders | `VideoState#onFrame` | **ja, wenn sie kleiner ist als die des Senders** |
| Physik | – | schaltet seit 0.5.0 nichts mehr frei (`docs/design/world-rules.md`) |

Bis 0.4.0 schaltete die Kreativ-Brille die persönlichen Physik-Optionen frei; waren sie verändert (etwa unendlicher
Akku), zählte jede Runde als „mit Flughilfe“. Das war der Grund für die Meldung. Seit 0.5.0 gelten die Physik-Regeln für
alle als Welt-Regeln, die Brille ändert nur noch Funk und Bild.

Neu ist die Einstellung **„Latenz aus: Videomodul / eigenem Wert“** im Video-Tab der Kreativ-Brille (Standard
Videomodul, wie beim Sichtfeld). Mit „Videomodul“ zeigt die Brille das Bild so verzögert wie der Sender der Drohne
(analog 12 ms, digital 26–30 ms); mit eigenem Wert ist bis 0 ms alles möglich, aber eine Runde mit weniger Latenz als
der Sender zählt nicht. Ältere Einstellungen ohne das Feld bekommen „Videomodul“ (bis 0.4.0 stand die Latenz der
Kreativ-Brille auf 0 ms).

## 2. Wann eine Runde nicht zählt und was das Rennen dazu sagt

`race.LapAssist` (reines Java) bestimmt die Gründe als Bitmaske: Höhe/Position halten an, GPS Rescue aktiv,
Welt-Regeln verändern die Physik (`PhysicsRules#modified`), Kreativ-Drohne (Kreativ-Flugcontroller), die Drohne gibt es
nur ohne „Teile müssen zusammenpassen“, Kreativ-Brille mit weniger Latenz als der Sender. Freie Reichweite, sauberes Bild
und Repeater tauchen in der Rechnung bewusst nicht auf (JUnit `LapAssistTest`).

Die Rennanzeige nennt den Grund statt still nicht zu werten:

- beim Start der Runde, wenn schon ein Grund vorliegt: „Diese Runde zählt nicht für die Bestenliste: …“,
- sobald während der Runde ein neuer Grund dazukommt: „Runde zählt ab jetzt nicht: …“,
- bei der Runde: „Nicht gewertet: …“ (vorher nur „Mit Flughilfe geflogen – zählt nicht für die Bestenliste“).

Bei veränderten Welt-Regeln stehen die Regeln mit Namen dabei (`PhysicsRules.changed`, z. B. „Welt-Regeln verändern
die Physik (Schwerkraft, Wind)“), bei der Latenz beide Werte („Kreativ-Brille mit 0 ms Latenz statt 12 ms des
Videosenders“). Der Server wertet wie bisher: er prüft Physik, Bauplan und Kreativ-Drohne selbst und übernimmt das
Kennzeichen des Clients; Geister und Bestenlisten folgen derselben Bitmaske.

## 3. Der Signal-Repeater

Ein kleiner Block `propwash:signal_repeater` (10 × 8 × 8 Modellpixel mit zwei Stummelantennen und grüner Status-LED),
den man wie einen Knopf an Boden, Wand oder Decke setzt (`FaceAttachedHorizontalDirectionalBlock`). Er hält ohne
Untergrund nicht (fällt ab wie ein Knopf), hat keine Kollision (keine unsichtbare Falle auf der Ideallinie), wird von
Kolben abgeworfen und ist mit der Hand schnell abgebaut. Rezept im Reiter „Baumaterial“: Blitzableiter, zwei
Kupferspulen, Platine und Eisenbarren ergeben zwei Repeater.

### Reichweite und Kette

- Jeder Repeater reicht **128 Blöcke** (`RelayRules.RANGE_BLOCKS`); auf den letzten 16 Blöcken blendet er weich aus
  (Aufschlag −20 · log₁₀((128 − d)/16) dB), damit eine Strecke nicht hart abreißt.
- Höchstens **4 Repeater** hintereinander, einstellbar mit der Welt-Regel `propwash:signal_repeaters` (0–4,
  Standard 4, 0 = Repeater aus; Tab „Welt-Regeln“, Gruppe „Video und Funk“).
- Die längste Kette (5 × 128 = 640 Blöcke) bleibt unter der vollen Funkreichweite der Funke (800 Blöcke). Damit kann
  ein Repeater die Reichweite gegenüber der Funke nie vergrößern, und die Reichweiten-Prüfungen des Servers beim Start,
  im Flug und bei GPS Rescue bleiben ohne Repeater-Wissen richtig (JUnit prüft die Ungleichung).

### Bild

Jede Teilstrecke (Pilot → Repeater, Repeater → Repeater, Repeater → Drohne) rechnet mit **demselben Modell wie die
direkte Strecke**: Reserve 20 · log₁₀(Reichweite des Senders / Abstand) minus Abschattung durch Blöcke
(`ObstructionScan`, Fresnel-Zonen und Material, `docs/design/vtx-obstruction.md`) minus dem weichen Rand des Repeaters.
Der Repeater sendet auf dem System und Kanal der Drohne mit derselben Leistung weiter (transparent), deshalb gilt die
Reichweite des Videomoduls der Drohne für jede Teilstrecke.

Die Kette als Ganzes:

- **Digital** (dekodieren und neu senden): die schwächste Teilstrecke entscheidet (Minimum der Reserven).
- **Analog** (Rauschen läuft mit): die Rauschleistungen addieren sich, Reserve = −10 · log₁₀(Σ 10^(−r/10)). Zwei
  gleich gute Teilstrecken kosten 3 dB, vier 6 dB.

Der Client wählt die beste Kette mit höchstens so vielen Repeatern, wie die Welt-Regel erlaubt (geschichtetes
Bellman-Ford über höchstens 16 Kandidaten, `RelayPaths`; bei Gleichstand die Kette mit weniger Repeatern), und nimmt sie
nur, wenn sie besser ist als die direkte Strecke. Kandidaten sind Repeater, für die Abstand zum Piloten plus Abstand zur
Drohne höchstens (Repeater + 1) × 128 Blöcke ist (Ellipse um beide).

Die Repeater selbst dämpfen das Signal nicht (`ClientBlockLoss` zählt sie wie Luft), auch nicht als Hindernis auf
fremden Strecken.

### Funk

Der Funk (Steuerung) hat in Propwash keine Abschattung durch Blöcke – nur die Entfernung zählt (2,4 GHz und 900 MHz
gehen viel besser durch Wände als 5,8-GHz-Video). Der Repeater reicht auch den Funk weiter, mit demselben Modell je
Teilstrecke (`RadioLink.quality` nach Abstand, die schwächste Teilstrecke zählt); die Entfernungsgrenze des Servers gilt
für die direkte Entfernung zwischen Pilot und Drohne. Weil eine Kette nie länger als die Funkreichweite wird, ändert sich
dadurch normalerweise nichts. Nur mit der Kreativ-Brille und eigener kurzer Reichweite (Video-Tab) kann eine Kette den
Funk weiter tragen als direkt. Im Betonbau war das Problem immer das Bild, nie der Funk.

### Anzeige

OSD-Element **„RPT“** (Gruppe Funk und Video, in den Vorlagen Minimal, Standard und Rennen links unter dem Videokanal,
nur sichtbar, solange das Bild über Repeater läuft): „RPT2 97 91 88“ = zwei Repeater im Weg, Signal der drei
Teilstrecken vom Piloten zur Drohne in Prozent; Farbe nach der schwächsten Teilstrecke. Die Zeile „VID“ zeigt wie
bisher das Signal, das ankommt.

### Runden

Runden über Repeater zählen ganz normal: Repeater sind Teil der Strecke, keine Flughilfe (`LapAssist` kennt sie nicht).

## 4. Verzeichnis und Leistung

- Das Block-Entity des Repeaters trägt sich beim Laden des Chunks (`BlockEntity#clearRemoved`) in ein Verzeichnis je
  Welt ein und beim Abbauen oder Entladen (`setRemoved`) wieder aus (`RepeaterIndex`, Zellen zu 128 × 128 Blöcken).
  Das läuft auf Client und Server gleich, ohne Welt-Scans und ohne eigenes Netzpaket: der Client kennt die Repeater aus
  den Chunk-Daten, die Vanilla ohnehin schickt.
- Bild: jede Teilstrecke hat ihre eigene `ObstructionScan`; der Client rechnet mit eigenem Budget von 24 000
  Blockzellen je Tick. Teilstrecken zur Drohne werden laufend neu gerechnet, zum Piloten alle 10 Ticks oder wenn er sich
  bewegt, zwischen zwei Repeatern alle 100 Ticks; nicht mehr gebrauchte Strecken werden nach 200 Ticks vergessen. Ohne
  Repeater in der Nähe kostet das nichts.
- Funk: reine Abstandsrechnung über dieselben Kandidaten, nur wenn der direkte Funk nicht ohnehin voll ist.
- Netzprotokoll und API bleiben unverändert (der Block kommt über die Register-Synchronisation der Loader, die
  Welt-Regel als weiterer Eintrag im bestehenden Paket `propwash:world_rules`).

## 5. Modell und Assets

Erzeugt von `tools/assets/world/repeater.py` (Teil von `generate.py`, geprüft von `validate.py`), Maße in
`docs/ASSETS.md` „Signal-Repeater“. Ein Modell für alle Lagen, gedreht wie ein Vanilla-Knopf. Die LED liegt 0,1 Pixel
über dem Gehäuse (kein Z-Fighting), leuchtet mit `light_emission` 12 und blinkt langsam (animierte Textur: 1,5 s an,
0,3 s gedimmt); der Block selbst gibt kein Licht ab. Als normales Blockmodell wirft er mit Shaderpacks (Iris) einen
Schatten wie jeder Block. In der Hand und am Boden hält er die Größenregel der Items ein (JUnit `ItemDisplaySizeTest`,
Selbsttest „content: Item-Größen …“).

## 6. Prüfungen

- JUnit `RelayPathsTest`: Kette nur mit Repeatern, schwächste Teilstrecke und Rauschsumme, Hop-Grenze und Deckel bei
  4, keine doppelten Repeater, Gleichstand mit weniger Repeatern, fehlende Strecken, weicher Rand, längste Kette unter der
  Funkreichweite, Funk über Repeater gegen eigene Reichweite der Kreativ-Brille und gegen die Server-Grenze, Betonbau
  mit Treppenhaus als Zahlenbeispiel, Kandidaten-Ellipse. `LapAssistTest`: Kreativ-Brille ohne Grund, Latenz unter dem
  Sender als Grund, Repeater ohne Grund, alle übrigen Gründe. `PhysicsRulesChangedTest`: genau die Physik-Regeln werden
  genannt.
- Selbsttest (Präfix `relay:`):
  - „Repeater an Boden, Wand und Decke setzen, Verzeichnis und Abbau“: Rechtsklick wie ein Spieler auf Boden, Wand und
    Decke, Lage und Antennenpunkt im Verzeichnis von Client und Server, funkdurchlässig, Wand-Repeater fällt ohne Halt
    ab, Verzeichnis nach dem Abbau.
  - „Betonbau mit sechs Etagen – Bild nur über Repeater, Grenze aus der Welt-Regel“: geschlossener Würfel aus weißem
    Beton (17 × 17 × 31, Decken in 5 Blöcken Abstand, Treppenhaus 5 × 5 in einer Ecke), Pilot im Erdgeschoss, Drohne in
    der obersten Etage (bis zur Nachbesserung der Abschattung in 0.5.2 drei Etagen; seit ein Betonblock wie eine
    Hauswand zählt, 9 dB, kommen zwei Decken bei 11 m noch klar durch – erst fünf Decken, 45 dB, schneiden das Bild ab,
    [`vtx-obstruction.md`](vtx-obstruction.md#nachbesserung-in-052-spieltest-hauswand-statt-bunker)). Ohne Repeater und mit nur dem unteren Repeater ist das Bild weg; mit Repeater unten am Treppenhaus
    und unter dem Dach klar über zwei Repeater (jede Teilstrecke frei); Welt-Regel 1 und 0 schneiden die Kette ab;
    Drohne in zwei anderen Ecken, Wand-Repeater statt Decke, Digitalbild; Kosten je Tick; Funk unverändert.
  - „Runde mit Kreativ-Brille zählt, Latenz unter dem Sender nennt den Grund“: echte FPV-Sitzung mit getragener
    Kreativ-Brille an einem Kurs aus drei Gates, zwei Runden ohne Grund gewertet; mit 0 ms eigener Latenz nicht gewertet,
    die Rennanzeige nennt „0 ms statt 12 ms“; der Server nimmt eine Runde mit Kreativ-Brille in die Bestenliste.
- Messwerte der Läufe vom 06.10.2026 (Fabric und NeoForge gleich): Pilot im Erdgeschoss, Drohne zwei Decken höher,
  direkt 60 dB (Deckel) und kein Bild. Über den Boden-Repeater am Treppenhaus und den Repeater unter dem Dach drei freie
  Teilstrecken mit 14,2 m, 13,3 m und 13,0 m, je 0,0 dB, Bild 1,00; mit dem Wand-Repeater 14,2 m, 12,3 m und 14,4 m, ebenfalls frei.
  Mit nur einem erlaubten Repeater bleibt der Weg Pilot → Dach-Repeater durch beide Decken (31 dB Beugung am Rand des
  Treppenhauses), Bild 0,01. Kosten der Teilstrecken je Tick im Mittel 0,14 ms, höchstens 1,7 ms (beim Aufbau, wenn
  alle Strecken neu sind). Der Funk bleibt bei 1,00.
- Messwerte nach der Nachbesserung der Abschattung (15.10.2026, Fabric und NeoForge, Gebäude mit sechs Etagen): direkt
  45,5 dB durch fünf Decken (Ecken 48,1 dB), kein Bild; über Boden-Repeater und Dach-Repeater drei freie Teilstrecken
  mit 14,2 m, 28,3 m und 13,0 m, je 0,0 dB, Bild 1,00; mit dem Wand-Repeater 14,2 m, 27,2 m und 14,4 m. Mit nur dem
  unteren Repeater oder einem erlaubten Repeater kein Bild. Kosten der Teilstrecken je Tick im Mittel 0,22 ms,
  höchstens 1,8 ms (Fabric) bzw. 5,5 ms (NeoForge, ein einzelner Ausreißer). Strahlen durch Beton enden jetzt nicht mehr
  nach zwei Blöcken am Deckel von 60 dB; mit vielen Repeatern rund um einen geschlossenen Betonbau erreicht die Abtastung
  deshalb öfter ihr Budget von 24 000 Zellen je Tick und verteilt eine Runde auf zwei Ticks. Der Funk bleibt bei 1,00
  (Steuerstrecke 0,6 × 45,5 dB ≈ 27 dB bei 25 m, volle LQ).
- Bildschirmfotos (`-PpropwashScreenshots=true`): `signal_repeater_mounts_day`, `signal_repeater_mounts_night` (Boden,
  Wand, Decke; nachts nur die LEDs), `signal_repeater_in_hand`, `signal_repeater_fpv_without` (FPV zwei Decken über dem
  Piloten: Rauschen) und `signal_repeater_fpv_with` (über zwei Repeater klar, „RPT2“ im OSD).

## 7. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Freie Reichweite, Hindernisse aus und sauberes Bild der Kreativ-Brille zählen nicht als Flughilfe | Vorgabe des Besitzers; sie ändern nur die Verbindung, nicht das Fliegen | Kreativ-Brille pauschal als Flughilfe |
| Latenz der Kreativ-Brille unter der des Senders zählt als Flughilfe, Standard ist „Latenz vom Videomodul“ | 0 ms schlägt jeden echten Aufbau (analog 12 ms, digital 26–30 ms); mit dem Standard zählen Runden sofort, wie gewünscht | Latenz nie werten (Vorteil in Bestenlisten); bei 0 ms Standard jede Kreativ-Runde verwerfen (die Meldung bliebe bestehen) |
| Eigenes Sichtfeld zählt nicht | Anzeige wie die Wahl einer anderen Kamera, im selben Bereich wie echte Kameras | als Flughilfe werten |
| Rennanzeige nennt jeden Grund, beim Start, beim Auftreten und bei der Runde | „zählt nicht“ ohne Grund war die eigentliche Verwirrung | nur der allgemeine Satz bei der Runde |
| Repeater als Block mit Block-Entity, Verzeichnis über `clearRemoved`/`setRemoved` | Verzeichnis je Chunk ohne Scans, auf Client und Server gleich, keine Loader-Ereignisse nötig | Chunk-Ereignisse und Palettensuche je Loader; Point-of-Interest-Typ (Register je Loader, Plattenzugriffe) |
| Keine eigene Synchronisation, kein neues Netzpaket | der Client kennt alle Blöcke seiner Chunks; das Bild rechnet ohnehin der Client | Server schickt Repeater-Listen (Protokoll 12, nur für Fälle außerhalb der Sichtweite) |
| Reichweite 128 Blöcke mit 16 Blöcken weichem Rand | genug für jede Halle und jedes Treppenhaus, klein genug für ein „kleines Modul“ | 64 (zu knapp für große Hallen); 256 (Langstrecken-Relais, ein anderes Feature); harter Abriss |
| Höchstens 4 Repeater, Welt-Regel 0–4, Standard 4 | vier Treppenhäuser reichen für fünf Etagen; Server ohne Repeater stellen 0 ein; (4 + 1) × 128 < 800 hält die Server-Prüfungen gültig | feste Grenze ohne Regel; bis 8 Repeater (Kette länger als die Funkreichweite, Server müsste Ketten kennen); Begrenzung der Anzahl je Welt |
| Jede Teilstrecke mit dem Modell der direkten Strecke und der Reichweite des Videomoduls der Drohne | „gleiches Modell“ ist leicht zu verstehen, die Kalibrierung je Videoteil bleibt | eigene Sendeleistung des Repeaters (zweite Kalibrierung) |
| Digital: schwächste Teilstrecke; analog: Rauschsumme | so verhalten sich Dekodieren-und-Senden bzw. ein analoger Umsetzer | überall Minimum (analog zu gut); Produkt der Qualitäten (ohne physikalischen Sinn) |
| Funk über Repeater nur nach Entfernung | der Funk hatte nie eine Abschattung; sie jetzt einzuführen würde bestehende Hallen-Strecken mit Failsafes überraschen | Abschattung auch für den Funk |
| Kein Kollisionskörper | ein Repeater unter der Decke wäre sonst eine unsichtbare Falle für Drohnen | Kollision wie ein Block |
| Repeater zählen beim Signal wie Luft | Antenne und Gehäuse sollen die eigene Strecke nicht dämpfen; der Block ist winzig | Metall-Einordnung über den Blockklang |
| Status-LED als blinkende Textur, kein Blocklicht | sichtbarer Puls ohne Block-Entity-Renderer, kein Einfluss auf Licht und Mob-Spawns | Licht-Level am Block; LED nach Nutzung (bräuchte Server-Wissen oder Renderer) |
| Rezept ergibt zwei Repeater im Reiter Baumaterial | Innenstrecken brauchen mehrere; der Reiter passt zu Gates und Kurs-Elementen | ein Repeater je Rezept |
| Keine eigene Flugschul-Szene | die Lektion „Absturz, Reichweite, Signal“ erklärt schon Abschattung; Shift-Hilfe und OSD reichen | neue Szene |
| Training (Flugtraining) bewertet Flughilfen unverändert | Sterne im Training sind persönlich, die Latenz der Kreativ-Brille ist dort kein Thema | `LapAssist` auch im Training |

## 8. Grenzen

- Der Client rechnet nur mit Repeatern und Blöcken in geladenen Chunks; Repeater außerhalb der Sichtweite des Clients
  fehlen in der Kette (wie Hindernisse, die er nicht kennt). Innenstrecken liegen immer in der Sichtweite.
- Die Welt-Regel `propwash:signal_repeaters` setzt der Client um (wie das Flugmodell); ein veränderter Client könnte
  mehr Repeater nutzen. Das betrifft nur das Bild, nicht Start, Reichweite oder Wertung.
- Mehrwege-Störungen (`Multipath`) rechnen bei einer Kette ohne den Teil-Schatten der direkten Strecke, sonst wie bisher.
