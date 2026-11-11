# Renndaten für Server, Skripte und Bots (Add-on-API 1.7)

Für Server-Betreiber, Skript- und Mod-Autoren. Seit Propwash 0.6.0 legt Propwash seine Renndaten selbst offen: Jeder
Rennkurs hat eine eindeutige **Kurs-ID**, an ihr hängen die Bestenlisten. Eine Mod liest Kurse und Bestenlisten über
`PropwashApi.race()` und abonniert Ereignisse (Runde beendet, Rekord, …); ohne eigene Mod liegen dieselben Daten als
JSON im Weltordner. Es gibt keine Schreib-Schnittstelle: Zeiten entstehen nur durch echtes Fliegen.

Technik und Entscheidungen: [`design/0.6.0/race-system.md`](../design/0.6.0/race-system.md) §11. Für Spieler:
[`race-data.md`](../race-data.md). Stabil ist nur, was unter `de.lardtom.propwash.api` steht (hier `api.race`).

## 1. Die Kurs-ID

- Eine zufällige UUID (Version 4), vergeben beim Anlegen des Kurses und in `data/propwash_race.json` der Welt gespeichert
  (Feld `uuid` je Kurs).
- **Bleibt gleich** beim Umbenennen, beim Ändern der Rundenzahl, beim Umbauen (Gates hinzu, weg, umsortiert, gedreht,
  verschoben) und wenn die Bestenliste archiviert wird und der Kurs eine neue Layout-Nummer bekommt. Eine Kopie des
  Weltordners behält ihre IDs.
- **Neu** bekommt sie jeder neu angelegte Kurs, auch ein Weltkurs aus der Weltgenerierung. Ein gelöschter Kurs vererbt
  seine ID nicht. Kopierfunktionen für Kurse (Sharecode, Werkzeug) gibt es in 0.6.0 noch nicht; kommen sie, legen sie
  einen neuen Kurs an und bekommen damit eine neue ID.
- **Alte Welten** (0.4.0 bis 0.5.2): Beim ersten Laden mit 0.6.0 bekommt jeder Kurs eine ID, und die Kursdatei wird
  sofort gespeichert. Danach bleibt sie stabil. Kurse, Bestenlisten, Archive und Geister bleiben unverändert.
- Die fortlaufende **Kursnummer** (`number`, im Spiel „Kurs 3“) bleibt intern und als Anzeige erhalten. Nach außen
  adressiert die ID; die Nummer kann in einer anderen Welt einen anderen Kurs meinen. Die Befehle unter
  `/propwash race` nehmen die ID oder den Namen (§6); eine Nummer nehmen sie noch an, mit Hinweis auf die ID.
- Spieler sehen die ID im Kurs-Fenster (Kurs-Werkzeug, Knopf „Kopieren“) und mit `/propwash course id [Name|Nummer]`.

## 2. Zugriff aus einer Mod

```java
PropwashApi.requireVersion("mybot", "1.7");
RaceData race = PropwashApi.race();
List<CourseInfo> courses = race.courses(server);
Optional<BoardSnapshot> top = race.leaderboard(server, courseId, BoardQuery.laps().limit(10));
```

- **Nur am Server-Thread.** Jeder Aufruf mit einem anderen Thread wirft `IllegalStateException`. Von einem eigenen
  Thread aus: `server.submit(() -> race.leaderboard(server, id, query))` liefert ein `CompletableFuture`.
- **Unveränderliche Schnappschüsse.** Alle Ergebnisse sind Records mit unveränderlichen Listen. Sie bleiben gültig,
  wenn sich danach etwas ändert, und dürfen an andere Threads weitergegeben werden.
- **Versionsfeld:** `RaceData.VERSION` (heute 1) bzw. `race.version()`. Neue Felder kommen nur in neuen Nebenversionen
  der API dazu; das JSON-Schema hat eine eigene Nummer (§5).
- Ohne laufenden Server (Client-Welt geschlossen) liefern alle Methoden leere Listen bzw. `Optional.empty()`.

| Methode | Ergebnis |
|---|---|
| `courses(MinecraftServer)` | alle Kurse des Servers, nach Nummer |
| `courses(ServerLevel)` | die Kurse einer Dimension |
| `course(server, UUID id)` | Kurs nach ID |
| `courseByNumber(server, int)` | Kurs nach Nummer (für Befehle; nach außen die ID benutzen) |
| `leaderboard(server, UUID id, BoardQuery)` | Bestenliste nach ID, Kategorie, Layout, Klasse und Limit |

### 2.1 `CourseInfo`

| Feld | Bedeutung |
|---|---|
| `id` | Kurs-ID (UUID) |
| `number` | Kursnummer in dieser Welt |
| `name` | Name oder `""`; `label()` ergibt den Namen oder `#3` |
| `owner`, `ownerName` | Besitzer (UUID optional, Name) |
| `dimension` | z. B. `minecraft:overworld` |
| `layout`, `layouts` | aktuelle Layout-Nummer und alle abrufbaren (archivierte und aktuelle, aufsteigend) |
| `laps` | Rundenzahl; `LAPS_PILOT_CHOICE` (−1) = jeder Pilot wählt selbst, `LAPS_ENDLESS` (0) = endlos; gilt nur bei `mode` `LAPS` |
| `mode` | `CourseMode.LAPS` (Runden, alte Kurse) oder `CourseMode.POINT_TO_POINT` (erstes Element Start, letztes Ziel, keine Runden); `pointToPoint()` als Abkürzung. Bei Punkt zu Punkt ist jede Zeit der Bestenliste (`TimeKind.LAP`) die Gesamtzeit von Start bis Ziel |
| `elements`, `checkpoints` | Zahl der Elemente (Durchgänge) und der Prüfpunkte einer Runde |
| `worldCourse` | `true` für Kurse aus der Weltgenerierung |
| `start` | Block des Start-Gates (optional, leer ohne Elemente) |
| `zones` | Zonen-Hinweis: `ZoneHint.status` `NONE` (keine Zone am Kurs), `RACE_LEGAL` (nur rennfreie Zonen oder Zonen ohne Abweichung vom Rennstandard), `ASSIST` (mindestens eine Zone, deren Werte als Flughilfe zählen); `zones` nennt jede Zone, die ein Element des Kurses enthält (`id`, `name`, `raceLegal`, `changesPhysics` – die Zone setzt mindestens eine rennrelevante Regel auf einen anderen Wert als den Rennstandard) |
| `record` | Kursrekord (schnellste Runde ohne Flughilfe im aktuellen Layout), optional |

### 2.2 `BoardQuery` und `BoardSnapshot`

```java
BoardQuery.laps()                      // Runden, ohne Flughilfe, aktuelles Layout, 10 Einträge, beste Zeit je Pilot und Klasse
BoardQuery.laps().assisted()           // Runden mit Flughilfe (eigene Liste wie im Spiel)
BoardQuery.laps().frame("race_5")      // nur eine Klasse (Rahmen), jeder Pilot mit seiner Bestzeit darin
BoardQuery.laps().layout(2).limit(50)  // archiviertes „Layout 2“, bis zu 50 Einträge
BoardQuery.laps().limit(BoardQuery.MAX_LIMIT)  // alle Einträge
BoardQuery.races(3)                    // Rennen über 3 Runden (Gesamtzeit), ohne Flughilfe
```

- `layout(0)` bzw. ohne Angabe: das aktuelle Layout. Ein archiviertes Layout bleibt unter derselben Kurs-ID abrufbar,
  solange es im Archiv steht (die letzten fünf). Unbekanntes Layout: `Optional.empty()`.
- `limit` ab 1 ohne Obergrenze; `BoardQuery.MAX_LIMIT` (`Integer.MAX_VALUE`) liefert alle Einträge. Die Bestenliste
  selbst hat keine Grenze je Klasse, gespeichert ist jede Zeit (je Pilot, Klasse und Regel-Satz die beste).
  `BoardSnapshot.total` nennt die Zahl aller Einträge vor dem Limit, `frames` die Klassen mit Zeiten,
  `raceLapCounts` die Rundenzahlen, für die es Rennzeiten gibt.
- Rundenzeiten ohne `frame` sind die Gesamtwertung „Alle“ wie im Spiel: je Pilot und Klasse seine Bestzeit als eigener
  Eintrag, alle nach Zeit sortiert, `rank` zählt jede Zeile. Mit `frame(…)` gibt es je Pilot genau eine Zeile.
- Kategorien wie im Spiel getrennt: `Category.CLEAN` (ohne Flughilfe) und `Category.ASSISTED` (mit Flughilfe, nur wenn
  die Welt-Regel „Runden mit Flughilfe speichern“ beim Fliegen an war). Die Einordnung folgt dem aktuellen Rennstandard
  (§2.4).

### 2.3 `BoardEntry`

| Feld | Bedeutung |
|---|---|
| `rank` | Platz in dieser Abfrage (1 …) |
| `player`, `name` | Spieler-UUID und Name beim Fliegen |
| `timeMillis`, `timeNanos` | Zeit in Millisekunden (gerundet wie die Anzeige im Spiel) und genau |
| `dateMillis` | Zeitpunkt (Unix-Millisekunden); `0` = unbekannt (Rennzeiten aus Welten vor 0.6.0) |
| `frame` | Klasse, z. B. `race_5`, `whoop_65`, `justmoreparts:race_5_stretched`; `""` = unbekannt (Altdaten). Anzeigename: `PropwashApi.text("en_us", "propwash.race.class." + frame)` |
| `kind`, `laps` | `TimeKind.LAP` (eine Runde, `laps` = 1) oder `TimeKind.RACE` (Gesamtzeit über `laps` Runden) |
| `category` | `CLEAN` oder `ASSISTED` |
| `assistReasons` | Gründe der Flughilfe als Schlüssel: `hold_mode`, `rescue`, `world_physics`, `creative_drone`, `parts_mismatch`, `goggles_latency`, `other`, `zone_physics` |
| `assistDetails` | Einzelheiten (`reason`, `subject`, `value`, `reference`), z. B. `hold_mode`/`altitude`, `zone_physics`/`gravity_scale` mit Zonen-Id in `reference`, `other`/Id der Add-on-Flughilfe; bei `world_physics` steht in `reference` der Wert des Rennstandards |
| `rules` | Regel-Satz der Zeit (`RuleValue`: `rule` als `namespace:pfad`, `value`, `zone` – `0` = Welt, sonst Zonen-Id –, `raceLegalZone`): die wirksamen Werte der rennrelevanten Regeln während der Runde bzw. des Rennens, ohne Welt-Werte gleich dem Propwash-Standard; leer = alle auf dem Propwash-Standard oder eine Zeit aus einer Welt vor 0.6.0. `propwash:too_many_rules` = Satz übergelaufen (zählt nie rennfrei). Siehe [`../race-rules.md`](../race-rules.md) |

### 2.4 Rennstandard

Ab 0.6.0-rc.3 ist der Rennstandard für neue Zeiten die Serverregeln ([`../personal-rules.md`](../personal-rules.md)):
`PropwashApi.race().serverRules(server)` liefert die aktuellen Serverregeln als `RaceStandardInfo` (`defaults()` falsch
= Abzeichen „Serverregeln“), `BoardEntry#serverRules()` die Serverregeln beim Fliegen der Zeit (nur Abweichungen vom
Propwash-Standard; leer bei Zeiten vor rc.3), im JSON `serverRules` je Datei und je Eintrag (`null` bei alten Zeiten).
Persönliche Abweichungen stehen als `personal_rules` in `assistReasons` mit `reference` = Serverwert. Der unten
beschriebene gespeicherte Rennstandard wertet nur noch alte Zeiten.

`PropwashApi.race().standard(server)` liefert `RaceStandardInfo` (Server-Thread): `rules` – jede rennrelevante Regel mit
`value` (Wert des Rennstandards) und `defaultValue` (Propwash-Standard), `neutral` – Ids der neutralen Regeln,
`changedAtMillis` und `changedBy` der letzten Änderung (`0`/`""` = nie geändert). `defaults()` ist `true`, solange jeder
Wert dem Propwash-Standard entspricht; `value(id)` liefert den Wert einer Regel. Eine Zeit zählt als `CLEAN`, wenn sie keine
festen Gründe hat und jeder Eintrag in `rules` außerhalb rennfreier Zonen dem aktuellen Rennstandard entspricht. Ändert
ein Operator den Standard, ordnet Propwash alle Zeiten neu ein; die Abfragen und der JSON-Spiegel liefern danach die neue
Einordnung (kein eigenes Ereignis).

## 3. Ereignisse

Alle in `RaceEvents`, angemeldet mit `RaceEvents.<NAME>.register(listener)`, ausgelöst am Server-Thread. Die Nutzdaten
sind unveränderliche Records. Ohne angemeldeten Listener baut Propwash keine Schnappschüsse (keine Last). Wirft ein
Listener, protokolliert Propwash den Fehler und ruft die übrigen trotzdem auf.

| Ereignis | Nutzdaten | Wann |
|---|---|---|
| `LAP_COMPLETED` | `LapResult`: Kurs (ID, Nummer, Name, Layout), Spieler (UUID, Name), `lap` (Runde im Lauf), Zeit, Klasse, Kategorie und Gründe, `saved`, `personalBest`, `classRecord`, `courseRecord`, `rank` (Platz der Zeile des Piloten in dieser Klasse in der Gesamtwertung „Alle“ der Kategorie, −1 = nicht gespeichert) | jede vom Server bestätigte Runde, auch mit Flughilfe |
| `RACE_FINISHED` | `RaceResult`: wie oben mit `laps`, Gesamtzeit, `personalBest`, `heatPlace` (Platz im Mehrspieler-Lauf, 0 = keiner) | ein Rennen über die eingestellte Rundenzahl ist im Ziel |
| `PERSONAL_BEST` | `PersonalBest`: Kurs, Layout, neuer Eintrag (`BoardEntry`, Runde oder Rennen; bei Runden ist `rank` der Platz dieser Zeile in „Alle“), `previousMillis` (leer = erste Zeit) | neue persönliche Bestzeit in der Klasse (Runde) bzw. für die Rundenzahl (Rennen), je Kategorie |
| `COURSE_RECORD` | `CourseRecord`: Kurs, neuer Rekord, `previous` (alter Rekord, `rank` 0) | neue schnellste Runde ohne Flughilfe im aktuellen Layout |
| `COURSE_CREATED` | `CourseInfo` | Kurs angelegt (Kurs-Werkzeug, Weltkurs) |
| `COURSE_CHANGED` | `CourseChange`: `kind` (`LAYOUT`, `NAME`, `LAPS`, `OWNER`), neuer Stand, `archivedLayout` (> 0: diese Bestenliste wurde gerade archiviert) | Umbau, Wechsel zwischen Runden und Punkt zu Punkt (`LAYOUT`), Umbenennen, Rundenzahl, Besitzer |
| `COURSE_DELETED` | `DeletedCourse`: ID, Nummer, Name, Dimension | Kurs gelöscht |

Reihenfolge bei einer Runde: erst `PERSONAL_BEST` und gegebenenfalls `COURSE_RECORD` (beim Eintragen), danach
`LAP_COMPLETED` mit denselben Angaben in den Merkern. `COURSE_CHANGED` mit `LAYOUT` kommt bei jedem Umbau, auch beim
Bauen eines neuen Kurses Gate für Gate; eine neue Layout-Nummer gibt es nur, wenn die alte Bestenliste Zeiten hatte.
Bei einem Punkt-zu-Punkt-Kurs ist `LAP_COMPLETED` der Lauf von Start bis Ziel (`lap` 1) und `RACE_FINISHED` folgt
mit `laps` 1 und derselben Zeit.

## 4. Beispiel: eine kleine Bot-Mod

Die Mod meldet Rekorde in einem Discord-Kanal und beantwortet „Top 10“-Anfragen. Netzwerk gehört nie auf den
Server-Thread: Ereignisse wandern in eine Warteschlange, ein eigener Thread schickt sie weiter.

```java
public final class RaceBot {
    private static final ExecutorService OUT = Executors.newSingleThreadExecutor();

    public static void init() {
        PropwashApi.requireVersion("racebot", "1.7");
        RaceEvents.COURSE_RECORD.register(record -> OUT.execute(() -> post(
                "Neuer Rekord auf " + record.courseName() + ": " + record.entry().name() + " in " + record.entry().timeMillis() + " ms")));
        RaceEvents.RACE_FINISHED.register(race -> {
            if (race.personalBest()) {
                OUT.execute(() -> post(race.playerName() + " fliegt " + race.laps() + " Runden in " + race.timeMillis() + " ms"));
            }
        });
    }

    public static CompletableFuture<List<BoardEntry>> topTen(MinecraftServer server, UUID course) {
        return server.submit(() -> PropwashApi.race().leaderboard(server, course, BoardQuery.laps().limit(10))
                .map(BoardSnapshot::entries).orElse(List.of()));
    }

    private static void post(String text) {
    }
}
```

`post` schickt den Text z. B. an einen Discord-Webhook. Die Kurs-ID für die Anfrage kopiert der Spieler im Kurs-Fenster
oder mit `/propwash course id`. Das Beispiel im Repository: `dev/example/ExampleRaceListener` (protokolliert Runden,
Rennen, Bestzeiten und Rekorde).

Abhängigkeit wie für jede API-1.7-Mod: Fabric `"propwash": ">=0.6.0-"`, NeoForge `versionRange = "[0.6.0-alpha,)"`.

## 5. JSON-Spiegel ohne eigene Mod

Propwash schreibt dieselben Daten als JSON in den Weltordner, damit Server-Skripte und Bots sie direkt lesen können:

```text
<welt>/propwash/race-data/courses.json
<welt>/propwash/race-data/leaderboards/<kurs-id>.json
```

- **Wann:** gebündelt nach Änderungen – 2 s nach der letzten Änderung, spätestens 10 s nach der ersten –, beim
  Serverstart einmal vollständig und beim Stoppen. Nie im Tick-Takt. Änderungen an Zonen holt eine Prüfung alle 60 s
  nach. Unveränderte Dateien werden nicht neu geschrieben.
- **Wie:** in eine Nachbardatei `*.tmp` und dann per atomarem Ersetzen. Ein Leser sieht immer eine vollständige Datei.
  Geschrieben wird auf einem eigenen Hintergrund-Thread, die Daten stammen vom Server-Thread.
- Gelöschte Kurse verlieren ihre Datei; beim Start werden Dateien ohne Kurs aufgeräumt.
- Kodierung UTF-8, Zeilenende `\n`, eingerückt mit zwei Leerzeichen. Fehlende Werte sind `null`.

### 5.1 `courses.json` (Schema 1)

```json
{
  "schema": 1,
  "courses": [
    {
      "id": "5f0c2a8e-3b1d-4c6e-9a7f-1b2c3d4e5f60",
      "number": 1,
      "name": "Stapel-Kurs",
      "owner": "00000000-0000-4000-8000-0000000000a1",
      "ownerName": "Anna",
      "dimension": "minecraft:overworld",
      "layout": 2,
      "layouts": [1, 2],
      "laps": 3,
      "mode": "laps",
      "creativeFc": {"course": "world", "worldRule": false, "raceLegal": false},
      "elements": 3,
      "checkpoints": 3,
      "worldCourse": false,
      "start": {"x": -49, "y": -60, "z": -40},
      "zones": {"status": "race_legal", "list": [{"id": 4, "name": "Schwerelos", "raceLegal": true, "changesPhysics": true}]},
      "record": {"rank": 1, "player": "00000000-0000-4000-8000-0000000000a1", "name": "Anna", "timeMs": 30250, "time": "30.250",
                 "date": 1792300000000, "frame": "race_5", "assist": [], "assistDetails": []},
      "leaderboard": "leaderboards/5f0c2a8e-3b1d-4c6e-9a7f-1b2c3d4e5f60.json"
    }
  ]
}
```

Felder wie `CourseInfo` (§2.1); `mode` ist `laps` oder `point_to_point`; `zones.status` ist `none`, `race_legal` oder `assist`; `leaderboard` ist der Pfad der
Bestenlisten-Datei relativ zu `race-data/`. `creativeFc` (nur im JSON, nicht in der API) nennt die Kurs-Einstellung für
den Kreativ-Flugcontroller (`course`: `world`, `assist` oder `legal`), die Welt-Regel `propwash:creative_fc_race_legal`
(`worldRule`) und was daraus gilt (`raceLegal`: `true` = Runden mit Kreativ-FC zählen ohne Flughilfe,
[`race-rules.md`](../race-rules.md) §9); dasselbe Objekt steht in jeder Bestenlisten-Datei. Neben `courses` steht `raceStandard`:

```json
"raceStandard": {"defaults": false, "values": {"propwash:wind": 0}, "changedAt": 1792300000000, "changedBy": "Anna"}
```

`values` nennt nur Regeln, deren Wert vom Propwash-Standard abweicht; `changedAt` ist `null`, solange der Standard nie
geändert wurde. Dasselbe Objekt steht in jeder Bestenlisten-Datei.

### 5.2 `leaderboards/<kurs-id>.json` (Schema 1)

```json
{
  "schema": 1,
  "course": "5f0c2a8e-3b1d-4c6e-9a7f-1b2c3d4e5f60",
  "number": 1,
  "name": "Stapel-Kurs",
  "mode": "laps",
  "layout": 2,
  "layouts": [
    {
      "layout": 2,
      "current": true,
      "archivedAt": null,
      "elements": 3,
      "laps": {"clean": [ … ], "assisted": [ … ]},
      "classes": {"clean": {"race_5": [ … ], "whoop_65": [ … ]}, "assisted": {"race_5": [ … ]}},
      "races": {"clean": {"3": [ … ]}, "assisted": {"2": [ … ]}}
    },
    {"layout": 1, "current": false, "archivedAt": 1792299999900, "elements": 2, "laps": { … }, "classes": { … }, "races": { … }}
  ]
}
```

- `layouts`: zuerst das aktuelle Layout, danach das Archiv vom neuesten zum ältesten. `archivedAt` in Unix-Millisekunden.
- `laps.clean` / `laps.assisted`: Gesamtwertung „Alle“, je Pilot und Klasse seine beste Runde als eigener Eintrag, alle
  nach Zeit sortiert (ein Pilot mit Zeiten in zwei Klassen steht zweimal da, jede Zeile mit eigenem `rank`).
- `classes.<kategorie>.<klasse>`: Wertung je Klasse, je Pilot seine beste Runde in dieser Klasse.
- `races.<kategorie>.<runden>`: Gesamtzeiten je Rundenzahl.
- Jede Liste enthält alle Einträge (keine Obergrenze, früher höchstens 500 bzw. 50 je Klasse).
- Ein Eintrag:

```json
{"rank": 1, "player": "00000000-0000-4000-8000-0000000000b2", "name": "Ben", "laps": 3, "timeMs": 93000, "time": "1:33.000",
 "date": 1792300000060, "frame": "race_5", "assist": [], "assistDetails": []}
```

`laps` steht nur bei Rennen. `time` ist die Anzeige wie im Spiel, `date` ist `null`, wenn unbekannt; `assist` und
`assistDetails` wie in §2.3. `creativeFc` ist `true`, wenn die Zeit mit einem Kreativ-Flugcontroller geflogen und
renntauglich gewertet wurde (ältere Zeiten `false`). `rules` ist der Regel-Satz der Zeit (§2.3), z. B.
`"rules": [{"rule": "propwash:wind", "value": 0, "zone": null, "raceLegalZone": false}]` (`zone` ist `null` für die Welt).
Nach einer Änderung des Rennstandards schreibt der Spiegel alle Dateien neu, nach einer Änderung der Welt-Regel
`propwash:creative_fc_race_legal` bei der nächsten Prüfung (alle 60 s). Die vollständigen Vergleichsdateien der JUnit-Tests liegen unter
`common/src/test/resources/race-data/`.

### 5.3 Lesen ohne Java

```sh
jq -r '.courses[] | "\(.id)  \(.name)  Rekord: \(.record.time // "-") \(.record.name // "")"' world/propwash/race-data/courses.json
jq -r '.layouts[0].laps.clean[] | "\(.rank). \(.name) \(.time)"' world/propwash/race-data/leaderboards/<kurs-id>.json
```

Ein Bot liest die Dateien, wenn sich ihr Änderungszeitpunkt ändert. Die Kurs-ID bleibt bei Umbauten gleich; ein neues
Layout erkennt er an `layout` bzw. am ersten Eintrag von `layouts`.

### 5.4 Versionen

`schema` steigt nur bei inkompatiblen Änderungen (Feld entfernt oder umgedeutet). Neue Felder kommen ohne neue Nummer
dazu; Leser überspringen unbekannte Felder.

## 6. Befehl und Kurs-Werkzeug

- **Kurs-Einstellungen** (Kurs-Werkzeug: Rechtsklick in die Luft) und **Kurs-Fenster** (dort „Reihenfolge …“, oder Bestenliste →
  „Kurs bearbeiten“ bzw. „Kurs ansehen“): Zeile „ID“ mit der Kurs-ID und dem Knopf „Kopieren“ (legt die ID in die Zwischenablage). Ansehen und
  Kopieren darf jeder, auch ohne Recht zum Bearbeiten.
- **`/propwash course id`** zeigt die ID des nächsten Kurses (bis 256 Blöcke), **`/propwash course id <Nummer>`** bzw.
  **`<Name>`** die eines bestimmten Kurses, auch eine ID ist als Angabe erlaubt. Die ID im Chat ist anklickbar und landet
  in der Zwischenablage; der Tooltip nennt die Bestenlisten-Datei. Wie `/propwash race courses` und
  `/propwash race leaderboard` ohne besondere Rechte (nur lesend). Bei gleichen Namen listet der Befehl alle.
- **`/propwash race leaderboard | archive | laps <runden> | mode | name | reset | remove | delete <kurs>`**: `<kurs>` ist
  die Kurs-ID oder der Kursname (Tab-Vervollständigung mit beiden, Tooltip mit Name und Nummer).
  `leaderboard` zeigt alle Zeiten seitenweise zu 10 (`/propwash race leaderboard page <n> [<kurs>]`, Fußzeile
  „Seite n von m · k Zeiten“ mit anklickbarer nächster Seite). Vor weiteren Angaben
  (`mode`, `name`, `reset`, `remove`) steht ein Name mit Leerzeichen in Anführungszeichen. Ein Name, der in der eigenen
  Dimension mehrfach vorkommt, wird abgelehnt und nicht vorgeschlagen; der Befehl listet die Kurse mit ID. Kursnamen
  stehen nie in umschließenden Anführungszeichen: Propwash nimmt sie beim Umbenennen ab (auch bei
  `/propwash race name <kurs> "Alpha Loop"`) und beim Laden älterer Kursdateien, `CourseInfo.name()` und `courses.json`
  liefern also `Alpha Loop`. Vorschläge und Suche lesen Anführungszeichen nach derselben Regel. Eine Kursnummer wird weiter
  angenommen (alte Befehlsblöcke), danach folgt der Hinweis `propwash.race.course_arg.number_hint` mit dem ganzen Befehl
  samt ID zum Kopieren (`ClickEvent.CopyToClipboard`). Rechte wie bisher. Eine Angabe nur aus Ziffern ist immer eine
  Nummer, eine in der Form einer ID immer eine ID; Kursnamen dieser Form fehlen deshalb in den Vorschlägen beider
  Befehle, der Kurs bleibt über die ID erreichbar. Hilfetexte im Spiel nennen den Kurs mit der ID, etwa die leere
  Bestenliste „/propwash race archive <Kurs-ID>“.
