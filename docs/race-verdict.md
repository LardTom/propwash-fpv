# Rundenwertung: Gleichlauf, Urteil an den Piloten, Pass-Zonen

Stand 0.6.0. Grundlage: Discord-Meldung von Tjerdenk und Elias/Rez (rc.3, 07.11.) zu Kurs 65: Pole 25 steht direkt neben
Gate 26. Wer nur durch das Gate flog, sah im Client alle Runden und Zeiten, der Server zählte nichts, und niemand sagte
dem Piloten warum.

## 1. Gleichstand: gleiche Reihenfolge auf beiden Seiten

Die Pass-Ebene einer Stange liegt oft in derselben Ebene wie das Gate daneben (Mitte Blockboden, Richtungen auf Achsen
gerastet). Ein Durchflug trifft dann beide im selben Moment, mit exakt gleichem `t`. Bis rc.3 sortierte der Client das
kleinere Element zuerst, der Server das größere. `LapVerifier.find` fand die Kette nicht mehr.

Jetzt gilt auf beiden Seiten (`CourseCrossings`, `LapTracker`): gleiches `t` geht aufsteigend nach Prüfpunkt
(`CourseCheckpoints.index`). Enthält der Gleichstand im Rundenkurs den letzten Prüfpunkt und Start/Ziel, läuft die
Reihenfolge über das Kursende: letztes Element, dann Start/Ziel. Punkt-zu-Punkt-Kurse haben kein solches Ende.

`CourseCrossings.collect` ist die Erkennung des Servers, ohne Entity und ohne Register, damit JUnit sie treiben kann. Der
Server prüft weiter alle Elemente, ohne „nächstes Element“, ohne falsche Seite und ohne Rücknahme. Damit ist er bewusst
großzügiger als der Client und kann eine Runde des Clients nicht mehr wegen der Reihenfolge ablehnen. Der Server misst jetzt
wie der Client an der Drohnenmitte (`getBbHeight() / 2` über dem Fußpunkt). Vorher konnte ein Durchflug knapp über dem
unteren Balken im Client zählen und auf dem Server nicht.

Bewusst nicht geändert: Fliegt jemand durch das Gate und die Pass-Ebene der *nächsten* Stange liegt in derselben Ebene,
wertet der Client nur das Gate, weil die Stange beim Beginn des Segments noch nicht dran war. Die Stange braucht einen
eigenen Durchflug. Der Server hätte beide; er ist also nur großzügiger, ein Client-Lauf bleibt gültig. Das hält die Bestzeiten
bestehender Kurse vergleichbar.

## 2. Urteil an den Piloten

Neues Paket `LapVerdictS2C` (`race_lap_verdict`, Server zu Client): Kurs, Runde, Urteil (`COUNTED`, `MISSED`, `REJECTED`,
`RACE`), Element des ersten fehlenden Prüfpunkts, Zahl der gewerteten Runden.

| Urteil | Wann | Anzeige (HUD und Chat) |
|---|---|---|
| `COUNTED` | Runde bestätigt | keine, die Bestenliste meldet sich selbst |
| `MISSED` | Keine Kette nach 2 s, fehlendes Element bekannt | „Runde 1 nicht gewertet – Slalomstange 25 verpasst“ |
| `REJECTED` | Andere Ablehnung (zu weit weg, Reihenfolge, unplausible Zeit) | „Runde 1 nicht gewertet – der Server konnte sie nicht bestätigen“ |
| `RACE` | Nach dem Zielbericht | „2 von 3 Runden gewertet“, nur wenn nicht alle zählen |

Punkt zu Punkt heißt es „Lauf nicht gewertet“, die Zusammenfassung entfällt.

`LapVerifier.firstMissing` geht die Kette vom ersten Start-Durchflug vorwärts und nennt den ersten Prüfpunkt, den es nicht
findet; `CourseCheckpoints.element` macht daraus das Element.

**Neuansatz statt Reset.** Eine nicht bestätigte Runde setzt die Sitzung nicht mehr zurück. Der Server zählt sie als
geflogen, merkt sie als ausgelassen und setzt beim letzten Durchflug durch Start/Ziel neu an (`LapVerifier#restart`). Spätere
Runden zählen also. Die Rennzeit über alle Runden kommt nur in die Bestenliste, wenn jede Runde gewertet ist. Andere
Ablehnungen setzen die Sitzung weiter zurück.

Das Paket ist Teil von Netzprotokoll 19, das der Wellenabschluss W2g anhebt. Ebenfalls geändert: `CourseEditC2S` (neue
Aktion `RACE_LINE`) und `CourseSyncS2C` (Kopf-Bit 16 in `CourseData`).

## 3. Warnung im Kurs-Editor

`PassZoneOverlap` meldet eine Überdeckung, wenn alle drei Punkte zutreffen:

- Das Element wertet über eine Pass-Zone: Slalomstange, Flagge oder Gate-Stange mit Rolle.
- Der Nachbar im Kurs (vorheriges oder nächstes Element, im Rundenkurs auch über das Kursende) hat eine Öffnung: Gate oder Freiform-Gate.
- Zone und Öffnung überlappen, zeigen in dieselbe Flugrichtung (Winkel unter etwa 45°), und die Öffnung liegt höchstens 1,5
  Blöcke von der Zonen-Ebene entfernt.

Ein normaler Slalom mit mehr Abstand warnt nicht, auch wenn er in Flugrichtung fluchtet.

- **Beim Anhängen** (`CourseToolItem`): Chat-Hinweis mit Knopf `[Auf X kürzen]`, der `/propwash race trim <kurs> <element>`
  ausführt. Der Befehl prüft dieselben Rechte wie das Gate-Menü (`GateMounting.edit`).
- **Im Gate-Menü** (`GateEditor`, `GateEditPanel`): Hinweiszeile, solange die Vorschau überdeckt, dazu mit freier Maus der Knopf
  „Auf X kürzen“. Er setzt nur den Wert im Menü, Übernehmen bleibt beim Spieler. Die Zeile erscheint auch, wenn ein Gate in
  die Zone einer Nachbar-Stange geschoben wird, dort ohne Knopf.

X ist die freie Länge bis zur Öffnung, abgerundet auf den Schritt der Stange. Passt das nicht mehr (kürzer als 0,5 Blöcke
bei Gate-Stangen, kürzer als 1 Block bei Slalomstange und Flagge), steht statt des Knopfs „Stange weiter wegstellen oder
Wende-Stange nehmen“.

## 4. Ideallinie je Kurs

`CourseData#raceLine` (Standard an, Kursdatei `raceLine: false` nur wenn aus, nicht in der Layout-Prüfsumme). Bearbeiten im
Kurs-Fenster, Zeile „Ideallinie“, für alle mit Bearbeiten-Recht (`CourseEditC2S` mit `RACE_LINE`). `RaceLineRenderer.enabled()`
verlangt Client-Einstellung und Kurs-Merker.

## 5. Lauflicht der Boden-Pfeile

`ChevronWave` rechnet die Helligkeit. Das Licht wandert zu kleineren `stepsLeft`, daher:

- `approach(i)`: Pfeil `i` zählt vom Ziel aus (Gate, Slalomstange, Freiform-Gate). Hier lief das Licht bis rc.3 verkehrt.
- `leaving(i, n)`: Pfeil `i` zählt vom Element weg (Bogen der Flagge mit Einflug-Pfeil, Ausgang und Einflug des Tauchwürfels).
  Das war schon richtig und ist unverändert.

## 6. Tests

- JUnit: `race.LapParityTest` (dieselben Segmente durch `LapTracker` und Server-Erkennung: Stange neben dem nächsten Gate,
  Gate-Stange, Stange am Kursende neben Start/Ziel, ganze Blockreihe, Flagge mit drei Stufen, Stange hinter dem Gate,
  gekürzte Reichweite, falsche Seite und Rücknahme, Gate in beide Richtungen, Punkt zu Punkt), `race.LapVerifierTest`,
  `race.LapVerdictTest`, `race.PassZoneOverlapTest`, `race.data.CourseRaceLineTest`, `race.client.ChevronWaveTest`.
- Selbsttest `'-PpropwashSelftestOnly=race-verdict:'` (`race.client.RaceVerdictSelfTests`, beide Loader), siehe `docs/testing.md`.
- Beitrittstest `PROPWASH_JOINTEST_RACELOOP=1`: Zeile `raceloop verdict counted` zeigt, dass das Urteil über das echte Netz ankommt.

## 7. Nur Assisted-Zeiten auf Kurs 65

Die Bestenliste zeigt fünf Piloten, alle „Assisted“. Aus dem Code lässt sich das für den Community-Server nicht entscheiden, die
Gründe stehen aber je Zeit in der Bestenliste. Persönliche Regeln zählen nur, wenn ihr Wert vom aktuellen Wert des Servers
abweicht (`RaceRuleSet#personalMismatches`, `RaceStandards.server`). Andere Wege zu „Assisted“: eine Zone mit anderen Regeln über
dem Kurs, ein Kreativ-FC bei ausgeschalteter Welt-Regel oder Kurs-Regel, Probleme im Bauplan der Drohne, eine geänderte Größe.
Nachsehen: Hinweistext am Abzeichen in der Bestenliste oder `leaderboards/<kurs-id>.json` im JSON-Spiegel.
