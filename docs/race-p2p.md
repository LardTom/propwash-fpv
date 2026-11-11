# Rennsystem 0.6.0: Punkt-zu-Punkt-Rennen und Rechte am Kurs

Für Spieler und Server-Betreiber. Mit 0.6.0 gibt es neben Rennen über Runden auch Rennen **von Punkt zu Punkt**: Start
und Ziel sind verschiedene Elemente, die Zeit läuft vom Start bis zum Ziel. Außerdem darf einen Kurs nur noch ändern,
wem er gehört, oder ein Operator.

Teil A und B des Rennsystems: [`race-system.md`](race-system.md), [`race-topology.md`](race-topology.md). Renndaten und
Kurs-ID: [`race-data.md`](race-data.md), für Mods [`api/race-data.md`](api/race-data.md).

## 1. Runden oder Punkt zu Punkt

- **Kurs-Fenster** (Kurs-Werkzeug in die Hand, Rechtsklick in die Luft öffnet die Kurs-Einstellungen, dort „Reihenfolge …“;
  oder Bestenliste → „Kurs bearbeiten“): Rechts in der Zeile der Überschrift „Gates“ stehen die Knöpfe **„Runden“** und
  **„Punkt zu Punkt“**. Dieselben Knöpfe stehen in den Kurs-Einstellungen in der Zeile „Typ“
  ([`race-course-menu.md`](race-course-menu.md)).
- **Runden** ist das bekannte Verhalten: Nach dem letzten Element geht es wieder durch das Start-Gate, dort endet eine
  Runde, das Rennen nach der eingestellten Rundenzahl. Alle Kurse aus älteren Welten und jeder neue Kurs fahren Runden.
- **Punkt zu Punkt:** Das **erste Element** der Reihenfolge ist der **Start** (Start-Gate wie bisher), das **letzte** ist
  das **Ziel**. Es gibt keine Runden; die Zeile „Runden“ zeigt stattdessen das Ziel („Nr. 5 · Gate“). Ein Kurs braucht
  dafür mindestens zwei Elemente; mit nur einem fährt er weiter Runden.
- Das Ziel kann jedes Element sein: Gate, Freiform-Gate, Tauchwürfel, Flagge, Slalomstange oder wertende Gate-Stange.
  **Schleifen und Richtungen** gelten wie bei Runden: Das Ziel darf auch das Start-Gate sein (nach einer Schleife zurück
  durch den Start) oder ein Gate in Gegenrichtung.
- Ein **Wechsel des Modus** ist ein Umbau: Die Bestenliste wird wie bei jeder Änderung am Kurs archiviert („Layout 2“),
  die alten Zeiten bleiben als eigene Liste unter derselben Kurs-ID. Wechselst du zurück, beginnt wieder eine neue Liste.
- Per Befehl: `/propwash race mode <kurs> laps` bzw. `/propwash race mode <kurs> point_to_point` (Rechte wie im Fenster).

## 2. Fliegen von Punkt zu Punkt

- **Start** wie bei Runden: fliegender Start beim Durchflug des Start-Gates, stehender Start mit Startampel, wenn du am
  Start-Gate scharf schaltest und „Start beim Scharfschalten“ an ist oder andere Piloten sich aufstellen.
- Die Zeit **endet beim Durchflug des Ziels**. An jedem Element dazwischen zeigt das HUD wie gewohnt die Zwischenzeit
  (mit Abstand zum Geist oder zur Bestzeit), am Ende **„Ziel: 0:23.45“** und groß **„ZIEL“** – keinen Rundenzähler. Im
  OSD steht im Feld der Runde „ZIEL“.
- Die Ideallinie führt bis zum Ziel und nicht zurück zum Start.
- Für den nächsten Lauf fliegst du zurück zum Start und wieder durch das Start-Gate (frühestens 3 s nach dem Ziel).
- **In der Welt:** Das Start-Gate trägt „START“ (statt „START · ZIEL“), das letzte Element trägt „ZIEL“ auf seiner Tafel;
  mit dem Kurs-Werkzeug in der Hand zeigen die Namensschilder „… · Start“ und „… · Ziel“. Hängst du mit dem
  Kurs-Werkzeug ein Element an, meldet es „… · jetzt das Ziel“. Im Kurs-Fenster trägt die letzte Zeile das Abzeichen
  „ZIEL“.

## 3. Bestenliste, Geister, Flughilfe

- Die **Bestenliste** steht unter derselben Kurs-ID; jede Zeit ist die **Gesamtzeit von Start bis Ziel**. Die Zeile
  „Bestes Rennen“ entfällt, unten steht „Deine gewerteten Läufe“.
- **Geister**, persönliche Bestzeit, Kursrekord, Klassen, **Flughilfe-Prüfung** und **Rennstandard** funktionieren wie
  bei Runden: Der Server prüft jeden Lauf (alle Elemente in Reihenfolge, Mindestzeit über die Strecke vom Start bis zum
  Ziel, Flughilfe im Zeitraum des Laufs) und speichert ihn nur bestätigt.
- Mehrspieler-Rennen (Startaufstellung) laufen gleich: Platz im Ziel nach Reihenfolge des Zieldurchflugs.
- Renndaten: `courses.json` und jede Bestenlisten-Datei tragen `"mode": "laps"` oder `"mode": "point_to_point"`, die
  Schnittstelle `CourseInfo#mode()`.

## 4. Wer einen Kurs ändern darf

- **Ändern** (Name, Rundenzahl, Modus, Reihenfolge, Start, Richtung, Elemente anhängen oder entfernen, Gates im Kurs
  einstellen, verschieben oder abbauen, Kurs-Werkzeug binden) dürfen nur der **Besitzer** des Kurses und **Operatoren**.
  Im Einzelspieler und beim Gastgeber einer LAN-Welt zählt der Gastgeber als Operator.
- Alle anderen sehen in der Bestenliste den Knopf **„Kurs ansehen“** statt „Kurs bearbeiten“. Das Fenster heißt dann
  „Kurs ansehen“: Infos, Gates, Bestenliste und **„Kopieren“ der Kurs-ID** gehen, alle Knöpfe zum Ändern sind aus, unten
  steht der Grund.
- Der **Server prüft jede Änderung** – Fenster, Paket, Befehl, Kurs-Werkzeug und Gate-Menü – und lehnt sie für alle
  anderen ab, auch wenn ein Client sie trotzdem schickt.
- **Kurse ohne Besitzer** (aus älteren Welten, Weltkurse) ändern nur noch **Operatoren** („… hat keinen Ersteller – nur ein
  Operator darf den Kurs ändern“). Bis 0.5.2 durfte das jeder in der Nähe des Kurses.
- Löschen bleibt wie bisher: Besitzer und Operatoren, gefundene Weltkurse nur Operatoren.

## 5. Technik

- Kursdatei `data/propwash_race.json`: neues Feld `mode` im Kurs, nur bei Punkt zu Punkt (`"point_to_point"`). Fehlt es
  oder ist es unbekannt, fährt der Kurs Runden. Formatversion bleibt 2.
- Netz: Der Kurs-Sync trägt den Modus im Kopfbyte (Bit 2 neben dem Merker der Kurs-ID); `CourseEditC2S` hat die neue
  Aktion `MODE` am Ende der Liste. Protokoll bleibt 17.
- Prüfsumme des Layouts: Bei Punkt zu Punkt kommt ein fester Beitrag hinzu, Runden-Kurse behalten ihre Prüfsumme (Geister
  und Bestenlisten aus älteren Welten bleiben gültig, Geister der Runden passen nicht zum Punkt-zu-Punkt-Layout).
- Zeitmessung (`LapTracker`, Punkt-zu-Punkt-Zweig): Ziel ist der letzte Durchgang, Rundenziel fest 1. Prüfung am Server
  (`LapVerifier#find` mit `pointToPoint`): Kette Start → Elemente → Ziel ohne Rückkehr zum Start; die Rundenmeldung darf
  am Ziel kommen (Abstand zum Ziel statt zum Start), die Mindestzeit rechnet die offene Strecke (`PointToPoint#pathLength`).
- Rechte: `CourseAccess#edit` (Besitzer oder Operator, sonst `NOT_OWNER` bzw. `OWNERLESS`), `CourseRegistry#canEdit` und
  `#isOperator` (Gamemaster oder Gastgeber) für alle Bearbeitungswege.

## 6. Tests

| Ebene | Inhalt |
|---|---|
| JUnit | `PointToPointTrackerTest` (fliegender und stehender Start, Zwischenzeit, Ziel, kein Rundenende im Runden-Modus, Reihenfolge, nächster Lauf, Ziel = Start-Gate nach Schleife, Ziel in Gegenrichtung, Moduswechsel bricht ab, ein Element, Geist-Abstand, Server-Prüfung, Ideallinie, Mindeststrecke), `PointToPointDataTest` (alte Kurse aus 0.4.0, 0.5.0, 0.5.2 = Runden ohne neues Feld, Moduswechsel als Layout-Wechsel mit gleicher ID, Speichern und Laden, unbekannter Modus, Netz, Renndaten und JSON), `CourseRightsTest`, `CourseAccessTest` |
| Selbsttest | Gruppe `race-p2p:` (`race.client.RaceP2pSelfTests`): Kurs bauen, Umschalten im Kurs-Fenster, Kennzeichnung in der Welt, Flug mit dem Client-Tracker (HUD „Ziel“), Lauf am Server mit Bestenliste und Renndaten, zurück auf Runden, Rechte mit Gast; Bildschirmfotos HUD und Kurs-Fenster englisch und deutsch |
| Beitrittstest | `PROPWASH_JOINTEST_RACE_P2P` (`dev.RaceP2pJoinCheck`, `race.client.RaceP2pJoinProbe`): Spieler A (Besitzer, am Server) baut und fliegt einen Punkt-zu-Punkt-Kurs, der beigetretene Spieler B sieht „Kurs ansehen“, sein Paket und sein Befehl werden abgelehnt; als Operator sieht er „Kurs bearbeiten“ und darf umschalten, danach nimmt ihm der Server das Operator-Recht sofort wieder ab (`race_p2p_op_revoked=yes`, `ops.json` bleibt leer) |

## 7. Englische Kernsätze (Website)

- Courses can now run point to point: the first element is the start, the last one the finish – no laps, the time stops
  when you fly through the finish.
- Switch between Laps and Point to point in the course window; switching archives the leaderboard like any rebuild.
- Only the course owner and operators may edit a course; everyone else can view it, see the leaderboard and copy the
  course ID. The server checks every edit. Courses without an owner can only be edited by operators.
