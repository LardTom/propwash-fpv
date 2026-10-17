# Runden mit Flughilfe speichern – Propwash 0.5.0

Auftrag des Besitzers (08.10.2026), sinngemäß: eine Welt-Regel, mit der auch Runden mit Flughilfe gültige, gespeicherte
Rundenzeiten werden – erkennbar markiert, dass eine Flughilfe im Spiel war. Ausgearbeitet: alle Gründe aus 0.5.0,
gespeichert und gezeigt in Bestenliste, persönlicher Bestzeit, Geist und Rennanzeige, immer markiert (Abzeichen und
Grund beim Darüberfahren), nie mit der Wertung ohne Flughilfe vermischt; aus = wie bisher; Weltdaten, Sync,
dedizierter Server, alte Daten laden unverändert.

## 1. Die Regel

`propwash:save_assisted_laps` (Schalter, Standard **aus** = wie bisher, Gruppe Spiel). Gründe (`race.LapAssist`, Maske
mit einem Bit je Grund):

| Grund | Erkannt | Text |
|---|---|---|
| `HOLD_MODE` | Client: ALTHOLD/POSHOLD aktiv | „Höhe oder Position halten war an“ |
| `RESCUE` | Client: GPS Rescue flog | „GPS Rescue hat geflogen“ |
| `WORLD_PHYSICS` | Client und Server: Physik-Welt-Regeln vom Standard verschieden | „Welt-Regeln verändern die Physik“ (live mit Regelnamen) |
| `CREATIVE_DRONE` | Client und Server: Kreativ-Flugcontroller | „Kreativ-Drohne“ |
| `PARTS_MISMATCH` | Client und Server: Drohne nur mit „Teile müssen zusammenpassen“ aus baubar | „die Drohne gibt es nur mit … aus“ |
| `GOGGLES_LATENCY` | Client: Kreativ-Brille mit eigener Latenz unter der des Videosenders | „Kreativ-Brille zeigt das Bild schneller …“ |
| `OTHER` (neu) | Meldung „mit Flughilfe“ ohne Grund (nur ein fremder oder alter Client) | „Flughilfe ohne genannten Grund“ |

Reichweite, Hindernisse und sauberes Bild der Kreativ-Brille und Signal-Repeater bleiben **keine** Flughilfe.

## 2. Verhalten

| Stelle | Regel aus (wie bisher) | Regel an |
|---|---|---|
| Server, Runde | nicht gespeichert | in der eigenen Wertung „Mit Flughilfe“ gespeichert (`Leaderboard#submitAssisted`, je Spieler und Klasse die beste, Gründe der Bestzeit als Maske), Chatzeile nur an den Piloten: „Persönliche Bestzeit mit Flughilfe … (Platz n mit Flughilfe, Klasse) – Gründe“ |
| Server, Rennen (alle Runden) | nicht gespeichert | eigene Bestzeit je Rundenzahl (`submitAssistedRace`), Chatzeile mit Gründen |
| Rekorde, Tafel am Kurs, goldener Geist, Rundenzähler „gewertete Runden“ | nur ohne Flughilfe | unverändert nur ohne Flughilfe |
| Bestenliste (`LeaderboardScreen`) | wie bisher | unter der Wertung ein eigener Abschnitt „Mit Flughilfe – zählt nie als Rekord“ mit bis zu 5 Zeilen (eigene Plätze, Abstand zur besten Zeit mit Flughilfe), jede Zeile mit dem Abzeichen „Flughilfe“, beim Darüberfahren „Mit Flughilfe geflogen: Gründe“; gibt es nur Zeiten mit Flughilfe, steht oben „Noch keine Rundenzeit ohne Flughilfe“; Klassen-Reiter filtern beide Abschnitte |
| Persönliche Bestzeit und Geist (Client) | Runde mit Flughilfe ersetzt den Geist nicht | eigener Geist mit Flughilfe je Kurs und Klasse (`<welt>/assisted/course_<id>_<klasse>.pwghost`), violett statt blau; in einer Runde mit Flughilfe fliegt er als Gegner und liefert die Splits, sonst der normale Geist; Meldung „Neue Bestzeit mit Flughilfe – Geist gespeichert“ |
| Rennanzeige | „Diese Runde zählt nicht …“, „Nicht gewertet: …“ | „Diese Runde zählt mit Flughilfe: …“, „Mit Flughilfe gewertet: …“, „Runde zählt ab jetzt mit Flughilfe: …“, dazu dauerhaft „FLUGHILFE“ (im FPV als OSD-Text in Zeile 4 Mitte, im Sichtflug als Abzeichen oben) |

Der Server wertet jede **Runde** für sich: Gründe, die er selbst sieht (veränderte Welt-Physik, Kreativ-Drohne, Teile),
sammeln sich seit der letzten angenommenen Runde, dazu kommen die Gründe aus dem Rundenbericht des Clients. Das
Rennen (alle Runden) zählt als mit Flughilfe, sobald eine Runde es war. Bisher blieb eine Sitzung nach der ersten Runde
mit Flughilfe für alle weiteren Runden gesperrt, während die Rennanzeige jede Runde einzeln bewertete; jetzt passen
beide zusammen (auch mit Regel aus zählt eine saubere Runde nach einer mit Flughilfe wieder).

## 3. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Id `propwash:save_assisted_laps`, Standard aus | Namensschema der Welt-Regeln; aus = bisher | `propwashSaveAssistedLaps` |
| Eigene Wertung unter der normalen („ranked after“), eigene Plätze, nie Rekord | Zeiten mit Flughilfe sind sichtbar, verdrängen aber nie eine Zeit ohne Hilfe; wer nur mit Hilfe fliegt, sieht seine Zeiten trotzdem sofort | gemischte Liste mit Abzeichen (Höhe-halten-Runden schlügen echte Zeiten); eigener Filter-Reiter (versteckt, bei leerer Hauptliste verwirrend); Spalte „Hilfe“ in der Hauptliste |
| Eine Bestzeit mit Flughilfe je Spieler und Klasse, unabhängig von der ohne Hilfe | beide Wertungen sind getrennt; eine langsamere Zeit mit Hilfe soll trotzdem stehen | Zeit mit Hilfe verwerfen, wenn es eine bessere ohne gibt |
| Gründe als Bitmaske in den Weltdaten (`assist`), im Rundenbericht (`flags`: Bit 0 „mit Flughilfe“, ab Bit 1 die Gründe) und in jeder Zeile der Bestenliste | Abzeichen und Tooltip nennen den Grund der gespeicherten Zeit; das Paketformat des Berichts bleibt gleich | nur ein Ja/Nein-Feld |
| Tooltip mit allgemeinen Texten je Grund (`AssistText#storedReasons`) | die Live-Texte nennen aktuelle Welt-Regeln und Latenzen, die zur alten Zeit nicht passen müssen | Live-Texte |
| Weltdaten additiv: neue optionale Felder `assisted` und `assisted_races`, `assist` nur bei Zeilen mit Hilfe | alte Welten laden unverändert, ohne Zeiten mit Hilfe schreibt der Codec Byte für Byte dasselbe wie vorher (JUnit) | eigene `SavedData` |
| Regel aus: gespeicherte Zeiten mit Hilfe bleiben in den Weltdaten, werden aber nicht gesendet; `/propwash race reset <kurs> [spieler]` löscht sie mit; ein geändertes Kurs-Layout archiviert auch eine Liste nur mit Zeiten mit Hilfe | aus soll aussehen wie bisher, ohne Daten zu verlieren | beim Ausschalten löschen |
| Kein goldener Geist mit Flughilfe | der goldene Geist ist der Kursrekord | eigener Server-Geist mit Hilfe |
| Eigener persönlicher Geist mit Hilfe, violett | „Geister“ gehören laut Auftrag dazu; Farbe unterscheidet ihn sofort vom blauen und goldenen | Geist mit Hilfe überschreibt den normalen |
| Runde wird zu Beginn als mit/ohne Hilfe eingeordnet, der Geist wechselt mitten in der Runde nicht | ein springender Gegner wäre verwirrend | Wechsel bei jedem neuen Grund |
| Server wertet je Runde statt je Rennen | passt zur Rennanzeige, die schon je Runde bewertet; Server-Gründe werden bei jeder Bewegung neu erkannt, eine Hilfe gerät also nicht verloren | klebrige Sitzung beibehalten (Runde 2 nach Höhe-halten-Runde 1 wäre mit Regel an falsch als Hilfe markiert) |
| Training und Arcade setzen die Regel an | wer übt oder Arcade fliegt, will seine Zeiten sehen – dort zählt wegen veränderter Physik sonst keine Runde; Realistisch bleibt genau Standard | Regel in allen Voreinstellungen auf Standard |
| Netzprotokoll bleibt 11, API unverändert | 0.5.0 unveröffentlicht; Addons brauchen die Wertung nicht | 12; Lesezugriff in der API |

## 4. Technik

| Datei | Inhalt |
|---|---|
| `race/LapAssist` | `OTHER`, `ALL` |
| `race/Leaderboard` | `Entry#assist`, Liste `assisted`, `AssistedRace`, `submitAssisted`, `submitAssistedRace`, `assistedTop/View/Rank`, `bestAssisted`, `assistedRaceBest`, `times`; `clear`, `removePlayer`, Umbenennen decken beide ab |
| `race/RaceRecords#archiveCurrent` | archiviert auch Listen nur mit Zeiten mit Hilfe |
| `race/CourseRegistry` | `submitAssistedLap`, `submitAssistedRace` |
| `race/RaceServer` | `Session#lapAssist`/`raceAssist`, `serverAssist(drone)`, Speichern je Regel, `assistReasons`, `leaderboardPayload(…, assisted)` |
| `race/RacePayloads` | `RaceReportC2S#assistFlags/assistMask`, `BoardRow#assist` |
| `race/client/ClientRaceTracker` | Gründe im Bericht, Texte je Regel, Wahl des Geists (`GhostLibrary#useAssisted`), `savesAssisted()` |
| `race/client/GhostLibrary`, `GhostStore`, `GhostRenderer` | zweiter persönlicher Geist, eigene Datei, violette Farbe |
| `race/client/LeaderboardScreen`, `ClientLeaderboards`, `AssistText` | Abschnitt, Abzeichen, Tooltip |
| `race/client/RaceHud` | Anzeige „FLUGHILFE“ |

## 5. Tests

- JUnit `AssistedLapsTest` (getrennte Wertung, nie Rekord, beste Zeit je Klasse, Grund `OTHER`, Rennen getrennt,
  Kürzen je Klasse, Zurücksetzen und Entfernen, Umbenennen, alte Weltdaten laden unverändert und schreiben keine neuen
  Felder, Rundlauf mit Zeiten mit Hilfe, Bericht und Zeilen mit Gründen), `LapAssistTest`, `LeaderboardTest`,
  `RulePresetTest`.
- Selbsttest (beide Loader): „flughilfe: Runden mit Flughilfe nur mit Welt-Regel gespeichert, getrennt und markiert“
  (eigener Kurs aus zwei Gates; Regel aus: nichts gespeichert; Regel an: Runde mit Höhe halten und GPS Rescue mit beiden
  Gründen gespeichert, Wertung ohne Hilfe leer; die nächste Runde ohne Hilfe zählt normal; Rennen getrennt; Schwerkraft
  130 % erkennt der Server selbst als Flughilfe; Bestenliste sendet die Zeilen nur mit Regel an), „flughilfe:
  Bestenliste zeigt den Abschnitt „Mit Flughilfe““ (Fenster öffnet, Abschnitt gezeichnet, keine Vermischung, Gründe),
  „flughilfe: Aufräumen“.
- Bildschirmfotos (Fabric): `leaderboard_assisted`, `leaderboard_assisted_de` (Tooltip unter der letzten Zeile mit
  Hilfe), `race_fpv_assisted`, `race_fpv_assisted_de` (Runde mit Schwerkraft 130 % im FPV: „FLUGHILFE“ im OSD und der
  Grund in der Rennanzeige), `configurator_world_rules_save_assisted_laps_de`.

## 6. Erkennung prüfen und erklären – Propwash 0.5.2

Auftrag des Besitzers (14.10.2026), sinngemäß: Manche Spieler bekamen trotz richtiger Einstellungen die Meldung, mit
Flughilfe zu fliegen. Sichtbar machen, welche Einstellung das auslöst, die Erkennung prüfen, auch mit mehreren Spielern.

### 6.1 Prüfung der Erkennung (Fehlalarme)

| Stelle | Befund | Folge |
|---|---|---|
| Physik-Meldung im Zustandspaket (`DroneStateC2S#physicsModified`, `PilotSession`, `QuadcopterEntity#applyPilotState`) | blieb nach einer veränderten Welt-Regel **bis zum Entschärfen** gesetzt, auch wenn alle Regeln wieder auf Standard standen | **Fehlalarm:** Der Server wertete jede weitere Runde als Flughilfe („Welt-Regeln verändern die Physik“ ohne Regelnamen), während die Rennanzeige des Clients eine saubere Runde zeigte; mit Regel aus fehlte die Zeit einfach. Behoben: die Meldung zeigt die Physik, mit der die Sim gerade rechnet (nicht klebrig) |
| Server-Gründe je Runde (`RaceServer.Session#lapAssist`) | sammelten sich bei jeder Bewegung, gelöscht erst beim Annehmen der Runde; wartete die Runde auf die Server-Bestätigung (`PENDING`), landeten Gründe der nächsten Runde in ihr, und beim Annehmen kam der Zustand der *aktuellen* Drohne dazu | **Fehlalarm** bei gleichzeitigem Wechsel (z. B. Admin ändert eine Regel kurz nach dem Ziel) und umgekehrt fehlende Gründe in der nächsten Runde. Behoben: `AssistTimeline` merkt Gründe mit Zeitstempel, die Runde nimmt genau ihr Zeitfenster `[Start, Ziel]` des Servers |
| Welt-Physik am Client und am Server | beide werten dieselben synchronisierten Regeln aus; der Client meldete aber auch Welt-Physik, Kreativ-Drohne und Teile im Rundenbericht | Der Server ist jetzt maßgeblich für alles, was er selbst sieht (`LapAssist.SERVER_REASONS`); aus dem Bericht übernimmt er nur Höhe/Position halten, GPS Rescue, Brillen-Latenz und „ohne Grund“. Ein veralteter oder fremder Client kann so keine Welt-Physik einschleppen |
| Standardwerte (`RuleSpec`, `PhysicsRules`, Voreinstellungen) | alle Regeln einzeln, ausgeschrieben, als Text hin und zurück und alle Voreinstellungen geprüft: „Realistisch“ und jede ausgeschriebene Standardbelegung sind sauber, Schwerkraft genau 100 % zählt nie; Werte sind ganze Zahlen, es gibt keine Rundung | kein Fehler, JUnit sichert es ab |
| Regeln ohne Physik | `crash_damage`, `drone_trails`, `invulnerable_pilots`, `part_compatibility`, `save_assisted_laps`, Reichweite, Repeater, Blockbruch usw. zählen nicht | kein Fehler, JUnit für jede Regel |
| „Teile müssen zusammenpassen“ aus | zählt nur, wenn die Drohne wirklich nur ohne die Regel baubar ist | kein Fehler; der Grund nennt jetzt das Problem („Propeller zu groß für den Rahmen“) |
| Kreativ-Brille | Umschalten auf „eigene Latenz“ startete bei 0 ms, also unter jedem Videosender | **Falle:** schon das Umschalten machte jede Runde zur Flughilfe. Behoben: der Regler startet bei der Latenz des Videosenders (auf 5 ms aufgerundet) |
| Flugschule (`TrainingSession`) | eigene Prüfung ohne Brillen-Latenz | jetzt dieselbe Prüfung wie im Rennen, mit Gründen in der Anzeige |
| Zustand je Spieler | Server-Sitzungen je UUID, Physik-Meldung je Drohne, Rücksetzen beim Pilotenwechsel und Verlassen; am Client alles beim Trennen geleert | kein Übersprechen zwischen Piloten (JUnit mit drei Piloten, Server-Prüfung mit vier) |
| Beitritt | vor dem ersten Abgleich gelten am Client die Standardwerte; der Server kennt seine Regeln von Anfang an | eine Runde in den ersten Ticks wertet der Server richtig; der Race-Tab zeigt „Welt-Regeln noch nicht empfangen“ |
| Darstellungsgröße der Drohne, Eingabequelle (Maus, Tastatur, Gamepad), Angle/Horizon | keine Flughilfe | unverändert, im Race-Tab als Info genannt (Eingabe) |

### 6.2 Gründe mit Details

`race.AssistDetail` (Grund, Gegenstand, Wert, Bezug) und `race.AssistReasons` (Maske plus Details, je Gegenstand der
neueste Wert, sortiert nach Grund, höchstens 16) beschreiben jeden Grund genau:

| Grund | Gegenstand | Text (Beispiel) |
|---|---|---|
| Welt-Physik | Regelname, Wert, Standard | „Welt-Regeln weichen vom Standard ab: Schwerkraft 130 % statt 100 %, Wind Sturm statt mild“ |
| Welt-Physik (nur Client) | `client` | „die Physik deines Clients wich vom Server ab (Welt-Regeln noch nicht abgeglichen)“ |
| Höhe/Position halten | `altitude`/`position` | „Höhe halten war an“ (im Race-Tab „ist an“) |
| GPS Rescue | `failsafe`/`switch`/`key` | „GPS Rescue hat geflogen (Failsafe nach Signalverlust)“ |
| Kreativ-Drohne | `creative_fc` | „Kreativ-Drohne (Kreativ-Flugcontroller)“ |
| Teile | Problem (`DroneBuild.Issue`) | „die Drohne gibt es nur mit „Teile müssen zusammenpassen“ aus: Propeller zu groß für den Rahmen“ |
| Brillen-Latenz | Latenz, Videosender | „Kreativ-Brille mit 0 ms Latenz statt 28 ms des Videosenders“ |
| Zonen-Physik | Regelname, Wert, Nummer der Zone | „Zone „Übungsplatz“ verändert die Physik: Schwerkraft 0 % statt 100 %“ |
| Sonstiges (Add-on-Flughilfe) | Id der Flughilfe | „die Flughilfe cinema:assist aus einem Add-on hat mitgeflogen“ |

Die Texte baut `race.AssistWords` (Server und Client gleich). Gezeigt werden sie in der Rennanzeige (Start, neuer
Grund, Ziel), in den Chatzeilen des Servers, im Tooltip der Bestenliste (gespeichert mit der Zeit, also die Werte von
damals; alte Einträge ohne Details zeigen die allgemeinen Texte wie bisher), in der Flugschule und im Race-Tab.

### 6.3 „Warum zählt meine Runde mit Flughilfe?“ im Race-Tab

Abschnitt **Flughilfe-Prüfung** unter den Einstellungen (`race.client.AssistPreflight`, Kategorien als reines Java in
`race.AssistChecklist`): eine Zusammenfassung (grün „Alles auf Standard: Deine Runden zählen normal.“ oder gelb) und je
Kategorie eine Zeile mit Punkt (grün in Ordnung, gelb Grund, grau Info): Welt-Physik, Drohne (gesteuert oder in der
Hand), Brille (getragen), Höhe/Position halten (im Flug der echte Zustand, sonst die Schalter der Funke), GPS Rescue,
Eingabe, Wertung (Regel „Runden mit Flughilfe speichern“) und, solange nötig, „Welt-Regeln noch nicht empfangen“. Die
Prüfung läuft wie im Rennen (dieselbe `AssistCheck.evaluate`), alle 250 ms neu.

### 6.4 Server-Prüfung je Runde

- Bei jeder Bewegung merkt `RaceServer` die Server-Gründe der Drohne mit Zeitstempel (`AssistTimeline`, gleiche
  Gründe verschmelzen zu Spannen, höchstens 64). Die Runde nimmt die Spannen in ihrem Server-Zeitfenster, dazu die
  Client-Gründe aus dem Bericht (`AssistCheck.lapVerdict`). Ohne Beobachtung im Fenster (nur Testpfade) gilt wie
  bisher der Zustand der Drohne beim Annehmen.
- Weicht das Urteil des Servers vom Bericht ab, bekommt nur der Pilot eine graue Chatzeile: „Server-Prüfung, Runde 2:
  zählt mit Flughilfe – Kreativ-Drohne“ bzw. „… zählt ohne Flughilfe (dein Client meldete: …)“. So sind
  Unterschiede zwischen Rennanzeige und Bestenliste nicht mehr stumm.
- Das Rennen (alle Runden) zählt mit Flughilfe, wenn eine angenommene Runde es tat; die Gründe aller Runden werden
  zusammengefasst gespeichert.

### 6.5 Daten und Netz

- Weltdaten additiv: `assist_details` an Einträgen mit Flughilfe und an Rennen mit Flughilfe, nur wenn es Details
  gibt; alte Welten laden unverändert, ohne Details schreibt der Codec dasselbe wie vorher.
- Netzprotokoll **16**: `race_report` und die Zeilen von `race_leaderboard` tragen die Details (je Zeile und je Bericht
  höchstens 16, Gegenstand nur `[a-z0-9_]`, beim Grund „Sonstiges“ zusätzlich `:./-` für Add-on-Ids, höchstens 48
  Zeichen, mehr bricht das Lesen ab). API unverändert.

### 6.6 Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Physik-Meldung nicht mehr klebrig, die Runde wertet ihr Zeitfenster | eine Regel, die mitten in der Runde galt, macht genau diese Runde zur Flughilfe – nicht alle bis zum Entschärfen; der vorher mitgenommene Akkustand ist ein Drohnenzustand wie jeder andere | klebrig bis zum Entschärfen (Fehlalarm), klebrig bis Rennende |
| Server maßgeblich für Welt-Physik, Kreativ-Drohne und Teile | er kennt Regeln und Bauteile selbst; ein Client mit veraltetem Stand kann so keinen Fehlalarm erzeugen | Client-Gründe ungeprüft übernehmen (bisher) |
| Physik-Meldung des Clients bleibt ein Grund („Physik deines Clients wich ab“) | rechnete der Client wirklich mit anderer Physik, war die Runde nicht vergleichbar | Meldung ganz ignorieren |
| Härtere Einstellungen (mehr Wind, weniger Akku, mehr Schwerkraft) zählen weiter | andere Physik ist nicht vergleichbar, unabhängig von der Richtung; der Text sagt jetzt genau „Wind Sturm statt mild“ | nur erleichternde Abweichungen zählen (Einordnung je Regel strittig, Hauptwertung mit gemischter Physik) |
| Details als Gegenstand plus zwei Zahlen statt fertiger Texte | Sprache des Lesers, kompakt in Weltdaten und Paketen, neue Gründe ohne Formatwechsel | Texte speichern; nur die Maske (zu ungenau) |
| Chatzeile nur bei Abweichung zwischen Server und Bericht | erklärt genau die Fälle, die bisher verwirrten, ohne jede Runde zu melden | Urteil jeder Runde senden; eigenes Paket |
| Race-Tab statt eigenem Fenster | dort stehen die Renn-Einstellungen, die Prüfung ist ohne Klick sichtbar | eigener Knopf mit Fenster; Prüfung nur im Rennen |
| Flugschule nutzt dieselbe Prüfung (Brillen-Latenz zählt dort jetzt auch) | ein Begriff „Flughilfe“ überall | eigene Liste der Flugschule |
| Eigene Brillen-Latenz startet bei der Latenz des Senders | Umschalten allein soll keine Runde entwerten; wer weniger will, stellt es bewusst ein | Startwert 0 ms (bisher) |
| Netzprotokoll 16 | Pakete tragen neue Felder | Details in freie Bits der Maske packen (zu wenig Platz für Werte) |

### 6.7 Tests

- JUnit `AssistCheckTest` (jede Physik-Regel mit Wert und Standard, jede andere Regel zählt nie, ausgeschriebene
  Standardwerte, Schwerkraft genau 100 %, alle Voreinstellungen, Halten, Rescue mit Auslöser, Kreativ-Drohne, Teile,
  Brille, Client-Physik, gleiche Maske wie `LapAssist.of`), `AssistTimelineTest` (keine Gründe aus der Zeit vor der
  Runde, Zurücksetzen mitten im Flug markiert nur die betroffene Runde, wartende Runde bekommt keine Gründe der nächsten,
  Server maßgeblich, ohne Beobachtung der Zustand beim Annehmen, Grenzen, drei Piloten), `AssistReasonsTest`
  (Normalisierung, Codec, Weltdaten, Pakete, zu lange Listen), `CreativeLatencyStartTest`.
- Selbsttests (beide Loader): „flughilfe-prüfung: Server wertet vier Piloten je Runde mit genau ihren Gründen“ (vier
  Piloten auf dem integrierten Server: sauber, Kreativ-Drohne, Teile, Halten und Brille vom Client samt falsch gemeldeter
  Welt-Physik; drei Runden, in Runde 2 Schwerkraft 130 %; jede Runde landet genau mit ihren Gründen in der Wertung),
  „… Race-Tab nennt jeden Grund vor dem Flug mit Wert und Standard“, „… Physik nach dem Zurücksetzen im Flug sofort
  wieder ohne Flughilfe“ (scharf schwebend: Schwerkraft 130 % erkannt, nach dem Zurücksetzen ohne Entschärfen Client
  und Server wieder sauber – mit dem alten Verhalten schlägt der Schritt fehl).
- Server-Rauchtest (dedizierter Server): dieselbe Prüfung mit vier Piloten, Logzeile „Flughilfe je Pilot: …“.
- Beitrittstest mit echtem Client (`PROPWASH_JOINTEST_ASSIST=1` an Server und Client, `docs/testing.md`): der Server
  setzt nach dem Beitritt Schwerkraft 130 %, Crash-Toleranz 200 % und Drohnen-Trails, der Race-Tab-Check des Clients
  nennt genau die beiden Physik-Regeln mit Wert und Standard und ist nach dem Zurücksetzen wieder sauber.
- Bildschirmfotos (Fabric): `configurator_race_assist_check`, `configurator_race_assist_clean` und je `_de`.

### 6.8 Nach der Gegenprüfung von 0.5.2

- **Übergänge von Zonen zählen.** Schwerkraft und Wind einer Zone gleiten über ihre Übergangszeit (bis 5 s); so lange
  wirkt eine nicht rennfreie Zone auch nach dem Verlassen, nach dem Löschen und beim Einflug in eine rennfreie Zone
  nach. Client und Server zählen sie, solange ihr Gewicht über 0 liegt (`ZoneBlend#details`, `ZoneField#blendDetails`;
  der Server führt dafür je Pilot ein eigenes `ZoneField`). Beim Einflug in eine rennfreie Zone über veränderter
  Welt-Regel zählt die Welt-Regel, bis die Zone voll wirkt. Rennfreie Zonen zählen nie, auch nicht im Übergang.
- **Add-on-Flughilfen zählen.** Gibt die Flughilfe eines Add-ons gerade etwas aus (Knüppel, Modus, Höhe, Position,
  Tempo, Gieren; `DroneSim#assistEngaged` mit 0,25 s Nachlauf), meldet der Client den Grund „Sonstiges“ mit ihrer Id.
  Hält sie Höhe oder Position, ohne dass der Pilot selbst Halten geschaltet hat, entfällt „Höhe halten war an“ – sonst
  stünde die eingebaute Funktion in der Wertung. Kräfte von Add-ons zählen nicht.
- **Bestenliste:** jede Zeile trägt bis zu 16 Details wie der Bericht; die Voreinstellung „Arcade“ (neun Regeln) plus
  Zone und Kreativ-Drohne passt damit vollständig in den Tooltip.
- **Rennanzeige im Sichtflug** und Zonen-Hinweise brechen lange Gründe um, statt über den Fensterrand zu laufen.
- **Heats:** die Platzierungszeile an alle Piloten nennt eine Flughilfe samt Gründen („Platz 2: Alex – 0:41.200 (mit
  Flughilfe: …)“).
- **Grenzen:** Gespeichert wird die Nummer der Zone, ihren Namen sucht der Text erst beim Anzeigen in den Zonen der
  eigenen Dimension. Eine Bestenliste aus einer anderen Dimension, eine umbenannte und eine gelöschte Zone zeigen
  deshalb „Zone #n“ bzw. den heutigen Namen. Den Namen mitzuspeichern hieße, Weltdaten und Paket um ein Feld zu
  erweitern, für einen seltenen, gut lesbaren Fall.
- **Tests:** JUnit `ZoneTransitionAssistTest` (Austritt, Löschen, Einflug aus einer Übungszone in eine rennfreie Zone,
  rennfreie Zone über veränderter Welt, rennfreie Zone im Übergang nie, Laden mit mehr als 128 Zonen) und
  `AddonAssistReasonTest` (Grund mit Id, Halten der Flughilfe nicht doppelt, Zeichen der Id, Zeile mit elf Details
  über das Netz).
