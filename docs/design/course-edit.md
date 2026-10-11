# Runden je Kurs und Kurse bearbeiten

Stand 0.5.0, Netzprotokoll 15. Paket `race` (`CourseLaps`, `CourseNames`, `CourseOrder`, `CourseAccess`, `CourseEdits`,
`CourseRegistry`), Client `race.client.CourseEditScreen` und `LeaderboardScreen`.

## 1. Was der Spieler bekommt

- Jeder Kurs hat **seine eigene Rundenzahl** und einen **Namen**. Ein Rennen auf dem Kurs geht für alle Piloten über
  diese Rundenzahl, egal was sie selbst eingestellt haben.
- Die Einstellung *Runden (Standard)* im Tab *Rennen* ist die Vorgabe für Kurse, die man neu anlegt. Der erste Klick
  mit dem Kurs-Werkzeug schreibt sie in den Kurs („Neuer Kurs 4 angelegt (3 Runden) …“).
- **Kurs bearbeiten**: Kurs-Werkzeug, Rechtsklick in die Luft → Bestenliste → *Kurs bearbeiten* (oder Tab *Rennen* →
  *Bestenliste*). Das Fenster ändert Name und Rundenzahl, schiebt Gates und Elemente nach vorn oder hinten, legt Start
  und Ziel auf ein anderes Gate, nimmt Gates heraus, stellt das Kurs-Werkzeug zum Anhängen auf den Kurs und löscht den
  Kurs. Gates selbst (Lage, Größe, Redstone) stellt weiter *Gate einstellen* ein.
- Befehle für Server: `/propwash race laps <runden> <kurs>` und `/propwash race name <kurs> [name]`;
  `/propwash race laps <runden>` ohne Kurs setzt wie bisher die eigene Standard-Rundenzahl.

## 2. Rundenzahl

| Wert im Kurs | Bedeutung |
| --- | --- |
| `-1` (`CourseLaps.UNSET`) | Kurs ohne eigene Rundenzahl: es gilt die Einstellung des Piloten. So laden Kurse aus 0.4.0 und gefundene Kurse – sie verhalten sich wie vorher, bis jemand eine Rundenzahl einstellt. |
| `0` (`ENDLESS`) | Freies Training: Rundenzeiten zählen, das Rennen endet nie. |
| `1`–`99` | Rennen über so viele Runden. |

- `CourseLaps.resolve(kurs, eigene)` liefert das Rundenziel; der Client setzt es in jedem Bild (`ClientRaceTracker`),
  eine Änderung gilt also auch im laufenden Rennen sofort.
- Der Server nimmt eine Zielmeldung erst an, wenn die Rundenzahl des Kurses geflogen ist
  (`CourseLaps.finishAllowed`, `RaceServer.finish`); vorher gilt sie als „außer der Reihe“. Rundenzeiten zählen
  unabhängig davon einzeln.
- Die eigene Standard-Rundenzahl schickt der Client beim Beitritt und nach jeder Änderung an den Server
  (`propwash:race_course_defaults`), damit der Server sie beim Anlegen eines Kurses kennt (`CourseEdits.defaultLaps`).
  Kommt nichts an, bleibt der neue Kurs ohne eigene Rundenzahl.

## 3. Wertung

- **Rundenzeiten und Geister** gelten je Kurs, Layout und Drohnenklasse – unabhängig von der Rundenzahl. Eine Runde
  ist dieselbe Strecke, ob das Rennen über 3 oder 5 Runden geht; getrennte Listen je Rundenzahl würden nur die
  Bestenliste zerstückeln.
- **Rennzeiten** (Gesamtzeit) gelten je Kurs, Layout **und Rundenzahl** (`Leaderboard.raceBest(pilot, runden)`). Ein
  Rennen über 3 Runden und eines über 5 stehen nie in derselben Wertung. Die Bestenliste zeigt das eigene beste Rennen
  über die aktuelle Rundenzahl des Kurses.
- **Name und Rundenzahl ändern die Wertung nicht**: kein neues Layout, nichts wird archiviert.
- **Jede Änderung am Layout** – Reihenfolge, Start und Ziel, Gate herausnehmen oder anhängen, Gate versetzen, drehen
  oder in der Größe ändern – beendet das Layout: Rundenzeiten und Rennzeiten wandern ins Archiv
  (`RaceRecords.archiveCurrent`, die letzten 5 Layouts, `/propwash race archive <kurs>`), die Rekord-Geister werden
  gelöscht, die Layout-Nummer steigt, alle Spieler in der Dimension lesen eine Meldung. Gab es noch keine Zeiten,
  bleibt die Nummer. Zeiten aus verschiedenen Layouts mischen sich nie; die persönlichen Geister auf dem Client tragen
  den Layout-Hash und passen danach ebenfalls nicht mehr.
- Die leere Bestenliste eines neuen Layouts verweist auf das Archiv („Noch keine Rundenzeit auf Layout 2 – ältere
  Zeiten: /propwash race archive 4“).

## 4. Rechte

Eine Logik für Server, Fenster und Befehle (`CourseAccess`, reines Java):

| Wer | Ändern | Löschen |
| --- | --- | --- |
| Ersteller des Kurses | ja, überall in der Dimension | ja (gefundene Kurse nicht) |
| Operator (Befehlsrecht 2) | ja | ja |
| Host im Einzelspieler / LAN | wie jeder andere | ja |
| Kurs ohne Ersteller (aus alten Welten, gefundene Kurse) | jeder, der höchstens 256 Blöcke von einem Gate steht | nur Operator oder Host |
| Abenteuer- und Zuschauermodus, andere Dimension | nie | nie |

- Das sind die Regeln des Kurs-Werkzeugs (Ersteller oder Operator; Kurse ohne Ersteller darf jeder anfassen), ergänzt
  um das, was ein Fenster nötig macht: mit dem Werkzeug steht man am Gate, das Fenster erreicht jeden Kurs der Welt.
  Deshalb braucht ein Kurs ohne Ersteller die Nähe, und Abenteuer- oder Zuschauermodus sind gesperrt.
- Das Fenster übernimmt einen Kurs ohne Ersteller nicht (das Kurs-Werkzeug tut es beim Anhängen weiterhin): wer einen
  gefundenen Kurs nur umbenennt, soll nicht alle anderen aussperren.
- Wer nicht ändern darf, sieht das Fenster als *Kurs ansehen* mit dem Grund in der Fußzeile; der Server prüft jede
  Änderung noch einmal und antwortet mit demselben Text.
- Höchstens 80 Änderungen in 2 Sekunden je Spieler, Namen werden bereinigt (`CourseNames.clean`: höchstens 32
  Zeichen, keine Formatierungs- und Steuerzeichen, Leerzeichen zusammengefasst).

## 5. Start und Ziel, Elemente

- Start und Ziel ist immer das erste Gate. *Start* im Fenster dreht die Reihenfolge, bis das gewählte Gate vorn steht
  (`CourseOrder.rotate`): die Strecke bleibt dieselbe, nur die Zählung beginnt woanders.
- Start und Ziel muss ein Gate oder Freiform-Gate sein. Verschieben, *Start* und Herausnehmen sind gesperrt, wenn
  danach eine Flagge, Slalomstange oder ein Tauchwürfel vorn stünde (`CourseOrder.canMove`/`canStart`/`canRemove`);
  der Server lehnt dasselbe mit „Start und Ziel muss ein Gate oder Freiform-Gate sein“ ab.
- Herausnehmen lässt das Gate in der Welt stehen (ohne Nummer), Löschen des Kurses ebenso.
- Anhängen bleibt beim Kurs-Werkzeug: *Gates anhängen* stellt ein Werkzeug aus dem Inventar auf den Kurs, danach hängt
  jeder Rechtsklick auf ein Gate oder Element es ans Ende. An eine andere Stelle kommt es mit den Pfeilen im Fenster.
- Gate-Stangen sind Bauteile und kein Kurs-Element; sie tauchen in der Liste nicht auf.

## 6. Daten und Netz

- `propwash_race.json` der Welt: je Kurs zusätzlich `name` und `laps`; fehlende Felder laden als leer bzw. `-1`.
- `propwash:race_courses` trägt je Kurs Name, Rundenzahl, Ersteller (UUID und Name) – damit das Fenster ohne
  Rückfrage weiß, was es anbieten darf.
- `propwash:race_course_edit` (Client → Server): Kurs, Aktion (`RENAME`, `LAPS`, `MOVE`, `START`, `REMOVE`,
  `DELETE`, `BIND_TOOL`), zwei Zahlen, Text. `propwash:race_course_defaults` (Client → Server): Standard-Rundenzahl.
- `propwash:race_leaderboard` trägt zusätzlich die Rundenzahl des Kurses für den Betrachter, sein bestes Rennen darüber
  und die Zahl archivierter Layouts.
- Die neuen Felder und Pakete heben das Netzprotokoll auf 15. Die Parts-API ändert sich nicht.

## 7. Entscheidungen

| Entscheidung | Warum | Verworfen |
| --- | --- | --- |
| Rundenzeiten je Kurs und Layout, Rennzeiten zusätzlich je Rundenzahl | Eine Runde ist von der Rundenzahl unabhängig; nur die Gesamtzeit hängt daran | Eigene Bestenliste und eigene Geister je Rundenzahl – leert die Liste bei jeder Änderung der Rundenzahl |
| Rundenzahl ändern archiviert nichts | Die Strecke bleibt dieselbe | Rundenzahl als Teil des Layouts |
| Layout-Änderung archiviert, statt zu löschen oder zu markieren | Alte Zeiten bleiben nachlesbar und mischen sich trotzdem nicht | Zeiten in der Liste als „altes Layout“ markieren – unübersichtlich, Rekord wäre nicht mehr eindeutig |
| Kurse ohne eigene Rundenzahl folgen dem Piloten | Welten aus 0.4.0 verhalten sich wie vorher | Beim Laden 3 Runden eintragen – ändert bestehende Kurse ungefragt |
| Bearbeiten als Fenster hinter der Bestenliste | Die Bestenliste ist schon der Ort für „diesen Kurs“ und vom Kurs-Werkzeug wie vom Tab *Rennen* erreichbar | Knopf in *Gate einstellen* – das Panel hat bei 480 × 270 keinen Platz für eine weitere Zeile |
| Start verlegen dreht die Reihenfolge | Ein Klick, die Strecke bleibt | Gate für Gate nach vorn schieben |
| Fenster übernimmt Kurse ohne Ersteller nicht | Umbenennen eines gefundenen Kurses soll niemanden aussperren | Erster Bearbeiter wird Ersteller, wie beim Anhängen mit dem Werkzeug |
| Gefundene Kurse löscht nur ein Operator oder der Host | Sie gehören zur Welt, Tafel und Truhe hängen daran | Wer sie übernommen hat, darf sie löschen |

## 8. Tests

- JUnit: `CourseLapsTest`, `CourseNamesTest`, `CourseOrderTest`, `CourseAccessTest`, `CourseEditDataTest` (Pakete,
  Kursdaten, alte Dateien).
- Selbsttest `race: Kurs bearbeiten – …` (`CourseEditSelfTests`, beide Loader): Kurs mit der Standard-Rundenzahl des
  Erbauers anlegen, Rennen endet erst nach der Rundenzahl, Name und Rundenzahl im Fenster ohne neue Bestenliste,
  Reihenfolge ändern archiviert Zeiten und Geister, Flagge und Gate anhängen, Start verlegen, Element herausnehmen,
  Rennen auf dem neuen Layout über die neue Rundenzahl, Rechte (fremder Kurs, Kurs ohne Ersteller nah und fern,
  Abenteuermodus, Ersteller aus der Ferne, Operator), Löschen lässt die Gates stehen.
- Bildschirmfotos: `course_edit`, `course_edit_de`, `leaderboard_course_edit`, `leaderboard_course_edit_de`.
