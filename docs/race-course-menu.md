# Kurs-Einstellungen (0.6.0)

Für Spieler. Alle Einstellungen eines Kurses stehen in **einem** Fenster, den **Kurs-Einstellungen**. Bisher lagen sie
verstreut: Name, Runden und Typ im Kurs-Fenster, Zeiten und Geister in der Bestenliste, Zurücksetzen und Archiv nur als
Befehl. Kursformat und Netzprotokoll (17) bleiben unverändert; das Bestenlisten-Paket trägt zusätzlich die Zahl aller
Zeiten des Kurses.

Technik und Entscheidungen: [`design/0.6.0/race-system.md`](design/0.6.0/race-system.md) §12.

## 1. Öffnen

- **Kurs-Werkzeug:** Rechtsklick in die Luft öffnet die Kurs-Einstellungen des Kurses, auf den das Werkzeug zeigt –
  ohne gebundenen Kurs die des nächsten Kurses (bis 256 Blöcke). Bisher öffnete sich hier die Bestenliste; sie ist jetzt
  einen Klick weiter („Öffnen“ in der Gruppe Bestenliste und Geister).
- **Kurs-Fenster:** Knopf **„Einstellungen“** oben rechts im Kurs-Fenster.
- Aus den Kurs-Einstellungen führt **„Reihenfolge …“** ins Kurs-Fenster und **„Öffnen“** in die Bestenliste; „Fertig“
  bzw. Esc führt jeweils zurück. Wer aus dem Kurs-Fenster kommt und dort wieder „Einstellungen“ drückt, landet im
  selben Menü – die Fenster stapeln sich nicht.
- Unverändert: Tab „Rennen“ → „Bestenliste öffnen“, in der Bestenliste der Knopf „Kurs bearbeiten“ (öffnet das
  Kurs-Fenster) und alle Befehle (`/propwash race name|laps|mode|reset|remove|delete|archive`, `/propwash course id`).

## 2. Inhalt

| Gruppe | Zeile | Was |
|---|---|---|
| Kurs | Name | bis 32 Zeichen, leer = „Kurs 3“; übernimmt nach kurzer Pause, mit Enter oder beim Schließen |
| | ID | die feste Kurs-ID mit Knopf „Kopieren“ (für alle, auch ohne Recht) |
| | Typ | „Runden“ oder „Punkt zu Punkt“ – ein Wechsel archiviert die Bestenliste wie ein Umbau |
| | Runden bzw. Ziel | Runden mit − und + (0 = endlos), bei Punkt zu Punkt das Ziel-Element |
| | Gates | Anzahl, „Reihenfolge …“ öffnet das Kurs-Fenster (Reihenfolge, Start, ⇄, ×) |
| | Ideallinie | „Sichtbar“ oder „Aus“ für alle Piloten auf diesem Kurs, siehe `docs/race-verdict.md` |
| | Werkzeug | „Gates anhängen“: das Kurs-Werkzeug zeigt danach auf diesen Kurs |
| Besitz und Rechte | Besitzer | wer den Kurs gebaut hat („niemand“ bei Kursen ohne Ersteller) |
| | Rechte | ob du ändern darfst (als Besitzer oder Operator) oder nur ansehen |
| Bestenliste und Geister | Bestenliste | Zahl aller Zeiten des Kurses (je Pilot und Klasse die beste, auch jenseits der besten zehn je Klasse; Zeiten mit Flughilfe nur, wenn die Welt-Regel sie speichert) und Rekord, „Öffnen“ zeigt die Bestenliste mit Teilen-Codes, Replays, Gegner-Geist und (für Operatoren) dem Löschen einzelner Zeiten |
| | Geister | dein Gegner-Geist auf diesem Kurs und was die Welt-Regel „Fremde Geister und Replays“ erlaubt |
| | Kreativ-FC | „Welt-Regel“, „Zählt als Flughilfe“ oder „Renntauglich“ für Runden mit Kreativ-Flugcontroller; stellen dürfen nur Admins (wer `/gamerule` darf), nicht der Ersteller, darunter steht, was gilt ([`race-rules.md`](race-rules.md) §9) |
| | Zeiten | „Alle Zeiten löschen“ (nur Operatoren, zweimal klicken) – wie `/propwash race reset <kurs>` |
| Löschen | Kurs | „Kurs löschen“ (zweimal klicken): Kurs, Bestenliste und Geister weg, die Gates bleiben stehen |

## 3. Rechte

- Ändern dürfen nur der **Besitzer** und **Operatoren** (wie im Kurs-Fenster und bei den Befehlen). Alle anderen sehen
  die Kurs-Einstellungen **schreibgeschützt**: oben ein Hinweis, warum (z. B. „gehört Patty“), alle Knöpfe zum Ändern
  sind gesperrt, das Namensfeld nimmt keinen Text an. ID kopieren, Kurs-Fenster ansehen und die Bestenliste öffnen
  geht für jeden.
- Im Abenteuer- und Zuschauermodus ist das Menü auch für den Besitzer schreibgeschützt.
- „Alle Zeiten löschen“ ist nur für Operatoren frei (der Befehl verlangt Stufe 2); „Kurs löschen“ folgt dem Löschrecht
  (Besitzer, Operator; einen gefundenen Kurs nur ein Operator).
- Der Server prüft jede Änderung selbst; auch ein veränderter Client kann fremde Kurse nicht ändern.

## 4. Bedienung

- Mausrad, Scrollbalken (ziehen), Bild auf/ab, Pos1 und Ende scrollen das Fenster, wenn es nicht ganz passt (kleine
  Fenster, GUI-Skala 3 und 4). Tab springt durch die Bedienelemente und scrollt sie ins Bild.
- Tooltips erklären jede Zeile; „Alle Zeiten löschen“ nennt den Befehl mit der Kursnummer.

## 5. Tests

- JUnit `race.CourseMenuTest`: Gruppen in fester Reihenfolge, jede Option genau einmal, Runden ↔ Ziel bei Punkt zu
  Punkt, schreibgeschützt für fremde Kurse, Kurse ohne Ersteller, Abenteuer und andere Dimension, Besitzer ändert ohne
  Zurücksetzen, Operator alles, gefundener Kurs ohne Löschen für den Besitzer, Befehl fürs Zurücksetzen, Zahl der Zeiten
  aus dem Bestenlisten-Paket (15 Zeiten in einer Klasse ergeben 15, nicht die 10 gesendeten Zeilen).
- Selbsttest `course-menu:` (Fabric und NeoForge, Bildschirmfotos auf Fabric): siehe `testing.md`.
- Beitrittstest `PROPWASH_JOINTEST_COURSE_MENU=1`: Gast ohne Recht am dedizierten Server, siehe `testing.md`.
