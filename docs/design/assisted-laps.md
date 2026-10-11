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
