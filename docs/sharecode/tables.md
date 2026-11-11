# Teile-Tabellen für Sharecodes — Anleitung für Add-ons

Ab Propwash 0.6.0 gehören Sharecodes Propwash (`pw3.md`). Ein Add-on liefert nur eine **Teile-Tabelle** in seinem Jar.
Damit bekommen seine Teile kurze Nummern im Code, und Propwash erkennt beim Import, ob der Code zur installierten Version
passt, welche Version nötig ist und wo man das Add-on bekommt.

Ohne Tabelle funktioniert ein Add-on trotzdem: Seine Teile gehen dann als Text in den Code (länger, aber gültig), und
Propwash schreibt einmal einen Hinweis ins Log.

---

## 1. Datei

`src/main/resources/data/<namensraum>/propwash_share/table.json` im Common-Modul, also im Jar der Mod, deren Mod-Id der
Namensraum ist.

```json
{
  "format": 1,
  "namespace": "myaddon",
  "parts": {
    "frame": [
      {"id": "alpha_5", "since": "1.0.0", "fallback": "propwash:freestyle_5"},
      {"id": "beta_3", "since": "1.1.0", "fallback": "propwash:whoop_65"}
    ],
    "motor": [
      {"id": "m2207_old", "since": "1.0.0", "deprecated": "1.2.0", "replaced_by": "myaddon:m2207_1950"},
      {"id": "m2207_1950", "since": "1.2.0", "fallback": "propwash:m2207_1750"}
    ]
  }
}
```

| Feld | Regel |
|---|---|
| `format` | 1. Ein höheres Format ignoriert Propwash (Teile gehen als Text, Log-Warnung) |
| `namespace` | muss zum Ordner und zur Mod-Id passen. `propwash` gehört nur Propwash: eine Tabelle dafür aus einem anderen Jar wird verworfen |
| `short_id` | optional, nur mit Zuteilung durch das offizielle Verzeichnis (§5) |
| `parts.<art>` | Arten `frame`, `stack`, `motor`, `prop`, `video`, `battery`, `accessory`. Die Reihenfolge ist die Nummer im Code. Ids ohne Namensraum, nur `a–z`, `0–9`, `_`, `.`, `-` |
| `since` | Pflicht: Version des Add-ons, mit der der Eintrag dazukam; innerhalb einer Art nicht fallend |
| `deprecated`, `replaced_by` | gestrichenes Teil: Version des Streichens und volle Id des Ersatzes (gleiche Art, eigener Namensraum oder `propwash`, keine Kreise) |
| `fallback` | optional: volle Id eines eingebauten Propwash-Teils **gleicher Art**, das einspringt, wenn jemand den Code ohne dein Add-on öffnet |

---

## 2. Regeln

- Einträge kommen **nur hinten** dazu. Nie umsortieren, nie löschen, nie eine Nummer wiederverwenden. Sonst lesen ältere
  Codes ein falsches Teil, und Propwash meldet „Code passt nicht zur installierten Version“.
- Ein umbenanntes Teil bekommt einen **neuen** Eintrag am Ende; der alte bleibt und bekommt `deprecated` und `replaced_by`.
  Propwash ersetzt die alte Id beim Lesen, wenn sie nicht mehr angemeldet ist.
- Einträge mit `since` gleich der Version, an der du gerade arbeitest, sind noch unveröffentlicht und dürfen umgestellt
  werden. Nach dem Release ist die Reihenfolge eingefroren.
- Jedes Teil aus `data/<namensraum>/propwash_parts/` braucht einen Eintrag. Ein Teil ohne Eintrag geht als Pfad (`v = 0`)
  in den Code und kostet mehr Zeichen; Propwash warnt einmal je Add-on.
- Ein Eintrag ohne angemeldetes Teil und ohne `deprecated` ist ein Datenfehler (Log-Warnung).
- Teile, die du nur per Java (`PartRegistry#register`) anmeldest, gehören genauso in die Tabelle.

Mit `-Dpropwash.share.strict=true` und im Selbsttest meldet Propwash alle diese Hinweise als Fehler im Log
(`ShareTables#problems` hält sie für Prüfungen bereit).

---

## 3. Prüfwerkzeug `ShareTableCheck`

`de.lardtom.propwash.api.share.ShareTableCheck` liegt im Propwash-Jar (API-Paket, MIT-Lizenz) und braucht nur Gson. Es
läuft als `main` im Build deines Add-ons und lässt sich aus JUnit aufrufen (`ShareTableCheck.check(...)` liefert die
Befunde als Liste).

| Befehl | Wirkung |
|---|---|
| `check --resources <dir> --version <v> [--baseline <datei oder jar>]` | prüft; Exit-Code 1 bei Befunden, 2 bei falschem Aufruf |
| `add --resources <dir> --version <v>` | hängt jedes Teil aus `propwash_parts/` ohne Eintrag hinten an (je Art alphabetisch), setzt `since` und schlägt einen `fallback` vor |
| `freeze --resources <dir> --out <baseline>` | schreibt die Vergleichsdatei nach einem Release |
| `init --resources <dir> --namespace <ns> --version <v> [--short-id <n>]` | legt eine neue Tabelle an und nimmt alle vorhandenen Teile auf (wie `add`) |

**Befunde** (Feld `code`):

| Code | Bedeutung |
|---|---|
| `MISSING_ENTRY` | Teil ohne Eintrag |
| `ENTRY_REMOVED`, `ENTRY_MOVED`, `ENTRY_INSERTED` | gegen die Vergleichsdatei gelöscht, verschoben oder eingeschoben |
| `SINCE_DECREASING`, `SINCE_FUTURE`, `SINCE_INVALID` | `since` fällt (eingeschoben?), liegt nach der aktuellen Version oder ist keine Version |
| `DEPRECATED_WITHOUT_REPLACEMENT`, `REPLACEMENT_WITHOUT_DEPRECATED` | `deprecated` und `replaced_by` nur zusammen |
| `REPLACEMENT_INVALID`, `REPLACEMENT_KIND`, `REPLACEMENT_CYCLE` | Ersatz fehlt, ist von anderer Art oder bildet einen Kreis |
| `FALLBACK_INVALID` | `fallback` ist kein eingebautes Propwash-Teil gleicher Art |
| `RESERVED_NAMESPACE` | Namensraum `propwash` außerhalb von Propwash |
| `DUPLICATE_ID`, `INVALID_ID` | doppelte Id (auch über Arten hinweg), ungültige Zeichen |
| `SHORT_ID_UNKNOWN`, `SHORT_ID_CONFLICT` | `short_id` nicht im Verzeichnis-Abzug, dort einem anderen Namensraum zugeordnet, oder das Verzeichnis kennt eine, die Tabelle nennt keine |
| `NAMESPACE_MISMATCH`, `FORMAT`, `UNKNOWN_KIND`, `INVALID_JSON`, `NO_TABLE`, `VERSION_INVALID` | Datei- und Aufruffehler |

Ohne Vergleichsdatei fängt die Regel „`since` nicht fallend und höchstens die aktuelle Version“ eingeschobene Einträge
auch schon beim ersten Release. Als Vergleichsdatei taugt die Datei aus `freeze` oder der Jar des letzten Releases.

**Vorschlag für `fallback` (`add`, `init`):** Für Rahmen, Stack, Motor, Prop, Video und Akku nimmt das Werkzeug das Teil
derselben Art aus dem Propwash-Preset (Whoop, Cinewhoop, Freestyle, Racer, Long Range), das am besten passt: Rahmen nach
`role`, dann Radstand; Stack nach Halterung, dann Zellzahl und Strom; Motor nach Halterung, dann KV; Prop nach Durchmesser;
Video nach Übertragung (analog, digital), dann Masse; Akku nach Zellzahl, dann Kapazität. Zubehör bekommt das eingebaute
Zubehör mit gleichem Platz und ähnlichster Masse. Prüfe den Vorschlag; er ist ein Ersatz für Spieler ohne dein Add-on.
Für einen Platz ohne eingebautes Zubehör (vorn hat Propwash keins) schlägt es Zubehör eines anderen Platzes vor. Ein
solcher Ersatz passt nicht an die Halterung, deshalb lässt der Import das Zubehör dann weg.

---

## 4. Gradle

Im Common-Modul deines Add-ons:

```groovy
tasks.register('checkShareTable', JavaExec) {
    classpath = sourceSets.main.compileClasspath
    mainClass = 'de.lardtom.propwash.api.share.ShareTableCheck'
    args 'check', '--resources', file('src/main/resources'), '--version', project.version,
         '--baseline', file('sharecode-baseline.json')
}
tasks.named('check') { dependsOn 'checkShareTable' }
```

Ablauf je Release:

1. Neue Teile anlegen, dann `add --resources src/main/resources --version <neue version>`.
2. `fallback`-Vorschläge prüfen, Build mit `checkShareTable` grün.
3. Nach dem Release: `freeze --resources src/main/resources --out sharecode-baseline.json` und die Datei einchecken.

Propwash prüft seine eigene Tabelle in JUnit (`ShareTableTest`) gegen
`common/src/test/resources/sharecode/propwash-baseline.json`.

---

## 5. Offizielles Verzeichnis und Kurzkennungen

Das offizielle Add-on-Verzeichnis auf propwashfpv.com vergibt kurze Kennungen ohne Doppelte: **1 = `propwash`,
2 = `justmoreparts`**, danach fortlaufend. Eine Kurzkennung spart im Code den vollen Namensraum. Aufnahme nur nach Prüfung
(Lizenz der Assets, Namensraum, Tabelle); frag auf Discord.

Propwash trägt einen **Verzeichnis-Abzug** im Jar (`data/propwash/propwash_share/directory.json`) mit Namen, Links,
Tabellen, Teilenamen und `fallback` der offiziellen Add-ons. Damit nennt Propwash auch ohne das Add-on dessen Namen und
Teile und kann Ersatzteile vorschlagen. Für Add-ons im Verzeichnis schreibt Propwash keine Info-Felder in den Code; alle
anderen bekommen Name und Link aus ihren Mod-Metadaten. Setze deshalb als Startseite (`homepage` bzw. `displayURL`) deine
Modrinth- oder CurseForge-Seite: Nur diese Links sind im Spiel anklickbar.

Der Abzug entsteht bei jedem Propwash-Release mit

```sh
python3 tools/sharecode/sync_directory.py --site <website>/assets/data/addons/directory.json \
    --addon justmoreparts=<jar von JMP> [--addon <ns>=<jar>] [--check]
```

Das Skript liest je Add-on die Tabelle und die Sprachdateien (`part.<ns>.<id>` in `en_us` und `de_de`) aus dem Jar.
`DirectorySnapshotTest` prüft danach: Kurzkennungen eindeutig, 1 und 2 fest, Tabellen nur angehängt gegenüber dem Abzug des
letzten Releases (`directory-baseline.json`), jeder `fallback` ein eingebautes Teil gleicher Art.

---

## 6. Was der Spieler sieht

- Import mit fehlendem Add-on: „Braucht X“, mit Version „braucht X ab V“, Link zu Modrinth oder CurseForge, Einkaufsliste
  im Überleben und auf Wunsch Ersatzteile (`fallback`, sonst ein passendes Propwash-Preset für Rahmen, Stack, Motor,
  Prop, Video und Akku). Zubehör bekommt nur seinen `fallback` und nur, wenn der an dieselbe Halterung passt; sonst
  fällt nur das Zubehör weg („→ (entfernt)“), Rahmen, Motoren, Akku und das übrige Zubehör behalten ihre Teile oder
  ihren Ersatz. Das gilt für jede Halterung (oben, unten, vorn).
- Import mit falscher Prüfsumme: „Code passt nicht zur installierten Version von X“; die Teile sind gesperrt, bis der
  Spieler bestätigt.
- Teile eines Add-ons, das nur beim Spieler installiert und auf dem Server nicht aktiv ist: „auf diesem Server nicht
  aktiv“, Ersatz möglich.
