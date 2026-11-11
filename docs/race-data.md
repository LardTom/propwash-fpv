# Renndaten: Kurs-ID, Bestenlisten für Server und Bots

Für Spieler und Server-Betreiber. Mit 0.6.0 hat jeder Rennkurs eine eindeutige **Kurs-ID**. An ihr hängen seine
Bestenlisten, und unter ihr finden Server-Skripte, Discord-Bots und andere Mods die Zeiten. Für Mod- und Skript-Autoren:
[`api/race-data.md`](api/race-data.md). Technik und Entscheidungen: [`design/0.6.0/race-system.md`](design/0.6.0/race-system.md) §11.

## 1. Die Kurs-ID finden und kopieren

- **In den Kurs-Einstellungen** (Kurs-Werkzeug in die Hand, Rechtsklick in die Luft, siehe [`race-course-menu.md`](race-course-menu.md))
  und **im Kurs-Fenster** (dort „Reihenfolge …“ oder Bestenliste → „Kurs bearbeiten“ bzw. „Kurs ansehen“ bei fremden Kursen). Unter Name und Rundenzahl steht die Zeile **ID** mit der Kennung, rechts der
  Knopf **„Kopieren“**: Er legt die ID in die Zwischenablage und zeigt kurz „Kopiert“. Das geht auch bei Kursen, die du
  nicht bearbeiten darfst.
- **Im Chat:** `/propwash course id` zeigt die ID des nächsten Kurses (bis 256 Blöcke), `/propwash course id 3` die von
  Kurs 3 und `/propwash course id Canyon` die des Kurses „Canyon“. Ein Klick auf die ID kopiert sie; der Tooltip nennt die
  Datei mit der Bestenliste. Den Befehl darf jeder benutzen, er ändert nichts.

## 2. Was die ID verspricht

- Sie **bleibt gleich**, wenn du den Kurs umbenennst, die Rundenzahl änderst, Gates hinzufügst, entfernst, umsortierst,
  drehst oder verschiebst – auch wenn die Bestenliste dabei archiviert wird und der Kurs „Layout 2“ wird. Die alten
  Layouts bleiben unter derselben ID abrufbar.
- Ein **neuer Kurs** bekommt eine neue ID, ein gelöschter Kurs nimmt seine mit. Eine **Kopie der Welt** (Weltordner
  kopiert, Server umgezogen) behält ihre IDs.
- **Alte Welten** aus 0.4.0 bis 0.5.2 bekommen beim ersten Start mit 0.6.0 für jeden Kurs eine ID. Kurse, Bestenlisten
  und Geister bleiben wie sie waren.
- Die **Kursnummer** („Kurs 3“) bleibt als Anzeige, in Befehlen nimmst du die ID oder den Namen (siehe §2.1).

### 2.1 Kurs-ID in den Befehlen unter `/propwash race`

Alle Befehle mit einem Kurs – `leaderboard`, `archive`, `laps`, `mode`, `name`, `reset`, `remove` und `delete` – nehmen
die **Kurs-ID** oder den **Kursnamen**, etwa `/propwash race leaderboard 3f2504e0-4f89-41d3-9a0c-0305e82c3301` oder
`/propwash race leaderboard Canyon Run`. Die Tab-Taste schlägt die IDs und Namen aller Kurse vor, der Tooltip nennt
Name und Nummer. Groß- und Kleinschreibung zählt beim Namen nicht.

- Steht nach dem Kurs noch etwas (`mode`, `name`, `reset`, `remove`), kommt ein Name mit Leerzeichen in
  Anführungszeichen: `/propwash race mode "Canyon Run" point_to_point`. Die Vorschläge setzen sie schon.
- Heißen mehrere Kurse gleich, gilt der Kurs in deiner Dimension; gibt es dort mehrere, nennt der Befehl alle mit ihrer
  ID und ändert nichts, und die Tab-Taste schlägt den Namen nicht vor. Dann die ID angeben.
- `/propwash race name <kurs> "Alpha Loop"` speichert den Namen ohne die umschließenden Anführungszeichen (`'…'`
  ebenso), der Kurs heißt dann `Alpha Loop`. Namen, die ältere Versionen samt Anführungszeichen gespeichert haben,
  werden beim Laden der Welt genauso bereinigt.
- **Alte Befehle mit Nummer** (`/propwash race leaderboard 3`, auch in Befehlsblöcken) laufen weiter. Danach kommt ein
  Hinweis im Chat bzw. in der Ausgabe des Befehlsblocks: „Kurs 3 per Nummer angegeben – Nummern in Befehlen sind
  veraltet …“ mit dem ganzen Befehl samt Kurs-ID; ein Klick kopiert ihn. Die Nummer gilt nur in dieser Welt, die ID ist
  überall eindeutig. Eine Angabe nur aus Ziffern gilt deshalb weiter als Nummer, auch wenn ein Kurs so heißt. Solche
  Namen (und Namen in der Form einer Kurs-ID) schlägt die Tab-Taste nicht vor; den Kurs erreichst du über seine ID.
- Zeigt die Bestenliste eines umgebauten Kurses noch keine Zeit, nennt sie den Befehl für die älteren Zeiten gleich mit
  der Kurs-ID: „Ältere Zeiten: /propwash race archive <Kurs-ID>“.

### 2.2 Alle Zeiten in der Bestenliste

Die Bestenliste hat keine Obergrenze: Sie zeigt jede Zeit, in „Alle“ und in jeder Klasse, ohne und mit Flughilfe. Auch
der Server behält alle Zeiten (je Pilot und Klasse die beste), nicht nur die besten 50 einer Klasse.

- **„Alle“ mit einer Zeile je Pilot und Klasse:** In „Alle“ steht die Bestzeit jedes Piloten in jeder Klasse als eigene
  Zeile, alle nach Zeit sortiert. Fliegt ein Pilot in der Klasse Race 30 s und in der Klasse Whoop 20 s, steht die
  Whoop-Zeile weiter oben und die Race-Zeile weiter unten, jede mit ihrer Klasse, ihrem Platz, ihrem Teilen-Code, ihrem
  Replay und ihrem Geist. Innerhalb einer Klasse bleibt es bei einer Zeile je Pilot (seine Bestzeit dort); eine
  schnellere Runde in derselben Klasse ersetzt sie. Das gilt für „Bestzeiten“ und „Mit Flughilfe“, für das Nachladen
  beim Scrollen, für den Befehl im Chat, die Schnittstelle und den JSON-Spiegel. Vor diesem Stand zeigte „Alle“ nur die
  beste Zeile jedes Piloten.

- **Scrollen** wie in den anderen Propwash-Fenstern: Mausrad, Scrollbalken ziehen, Bild auf/ab, Pos1 und Ende. Zuerst
  kommen die ersten 50 Plätze beider Abschnitte, die eigenen Zeiten und der Rekord jeder Klasse; weitere Plätze holt das
  Fenster beim Scrollen in Seiten zu 50 nach (noch nicht geladene Plätze stehen kurz als „…“ da). Die Plätze stimmen
  über die Seitengrenzen hinweg, weil der Server sie vergibt.
- **Einklappen:** Beide Abschnitte haben eine Kopfzeile mit Pfeil und Anzahl, etwa „▼ Bestzeiten (120)“ und
  „▼ Mit Flughilfe – zählt nie als Rekord (37)“. Ein Klick klappt den Abschnitt ein oder aus. Das gilt für die ganze
  Spielsitzung: auch nach dem Wechsel von Kurs oder Klasse und nach dem Schließen und Öffnen bleibt er so. Beim Scrollen
  bleibt die Kopfzeile des Abschnitts oben stehen.
- **Meine Zeit:** Hast du in der gezeigten Liste eine Zeit, steht rechts in der Kopfzeile „Meine Zeit (87)“ mit deinem
  Platz. Ein Klick springt zu deiner (hervorgehobenen) Zeile und klappt den Abschnitt dafür auf. Hast du in „Alle“
  Zeiten in mehreren Klassen, sind alle deine Zeilen hervorgehoben und der Sprung geht zur besten.
- **Im Chat:** `/propwash race leaderboard [<kurs>]` zeigt die ersten 10 Plätze und darunter „Seite 1 von 12 · 120
  Zeiten“ mit einem Klick auf „[nächste Seite]“; `/propwash race leaderboard page <n> [<kurs>]` zeigt Seite `n`.
- Der JSON-Spiegel und die Schnittstelle für Mods liefern ebenfalls alle Zeiten (§3, §4).

## 3. Für Server-Betreiber: die Dateien

Propwash schreibt die Renndaten zusätzlich als JSON in den Weltordner:

```text
world/propwash/race-data/courses.json                 alle Kurse mit ID, Name, Besitzer, Layout, Rundenzahl, Rekord
world/propwash/race-data/leaderboards/<kurs-id>.json  Bestenlisten des Kurses: Runden, Klassen, Rennen, archivierte Layouts
```

- Geschrieben wird kurz nach einer Änderung (gebündelt, spätestens nach 10 Sekunden), beim Start und beim Stoppen des
  Servers – nie in jedem Tick. Jede Datei wird vollständig ersetzt; ein Skript liest nie eine halbe Datei.
- Die Dateien sind nur ein Spiegel. Änderungen daran wirken nicht zurück und werden beim nächsten Schreiben ersetzt.
- Jede Liste enthält alle Zeiten, ohne Obergrenze.
- Aufbau und Beispiele: [`api/race-data.md`](api/race-data.md) §5.

## 4. Für Mod-Autoren

`PropwashApi.race()` liefert Kurse und Bestenlisten nach ID, `RaceEvents` meldet Runden, Rennen, Bestzeiten, Rekorde und
Änderungen an Kursen. Ein Beispiel für eine kleine Bot-Mod steht in [`api/race-data.md`](api/race-data.md) §4.
