# Share-Codes für alle Add-ons (PW3) — Design 0.6.0

| | |
|---|---|
| Status | Entwurf zur Umsetzung, Stand 17.10.2026 |
| Gilt für | Propwash FPV 0.6.0 (API 1.7.0), Propwash: Just More Parts 1.2.0, Website propwashfpv.com (Web-Konfigurator, Umrechner, Add-on-Verzeichnis) |
| Ausgang | Tag `v0.5.2` (Propwash), Tag `v1.1.1` (JMP), Website-Stand `cfe6713` |
| Vorgänger | PW1 (`sharecode.md`, Revision 1.2) und PW2 (`sharecode-pw2.md`, Revision 2.0), beide bisher im JMP-Repository unter `docs/design/` |
| Normative Spezifikation | entsteht in Strang A als `docs/sharecode/pw3.md`; dieses Dokument legt Aufbau, Regeln und Entscheidungen fest |

---

## 1. Ziel und Vorgaben

**Ziel:** Ein Sharecode beschreibt jede Drohne vollständig, egal aus welchen Add-ons ihre Teile kommen. Der Codec
gehört Propwash selbst, jedes Add-on liefert nur eine Teile-Tabelle. Alte Codes bleiben lesbar, neue Codes sind
gegen Versionsunterschiede abgesichert und sagen beim Import genau, was fehlt und wo man es bekommt.

Roadmap-Notizen (Stand 17.10.), die für dieses Thema maßgeblich sind (Herkunftsvermerke vereinheitlicht):

> **0.6.0 (Vorgabe 14.10.): Share-Codes für alle Add-ons.**
> - **Codec in die Basis-Mod:** Er wandert aus JMP nach Propwash, JMP liefert nur noch seine Teile-Tabelle. Das ändert die alte Entscheidung „Sharecode im Addon“.
> - **Nummern pro Add-on statt global:** Der Code nennt jeden Namensraum (= Mod-Id) einmal, danach pro Teil eine Nummer aus der Tabelle des Add-ons. Die Tabelle liegt im Jar und wird nur hinten erweitert, nie umnummeriert. Ein mitgeliefertes Prüfwerkzeug schlägt beim Bauen Alarm, gestrichene Teile bleiben als „veraltet“ mit Ersatz-Verweis.
> - **Kurze Kennungen:** Optional vergibt das offizielle Add-on-Verzeichnis auf propwashfpv.com kurze Kennungen ohne Doppelte, nicht eingetragene Add-ons nutzen den vollen Namensraum.
> - **Prüfsumme:** Pro Add-on trägt der Code eine kleine Prüfsumme der Tabelle und meldet „Code passt nicht zur installierten Version“.
> - **Fehlende Add-ons:** Der Code trägt optional Name, Version und Link mit, beim Import erscheint der Hinweis „braucht X 1.2“, dazu Survival-Einkaufsliste und Creative-Ersatz.
> - **Web-Konfigurator:** Add-on-Jar lokal im Browser laden (nichts hochladen) und ein offizielles Verzeichnis mit Web-Exporten. Mob Defense und das DJI-Addon nutzen das.
> - **Neues Präfix PW3 (Vorgabe 14.10.):** Das neue Format bekommt das Präfix `PW3-`.
>   - Die Website erkennt alte Codes (PW1 und PW2) und rechnet sie automatisch in PW3 um. Das ist eindeutig, weil Codes vor 0.6.0 nur Teile aus Propwash und JMP enthalten können.
>   - Entschieden (14.10.): Auch die Mod liest PW1/PW2 weiter und schreibt nur noch PW3.
>   - Fremdteile, die in PW2 als Text-Id kodiert sind, wandern als voller Namensraum mit.
> - **Nach der Umstellung, Pflicht:**
>   - Website und JMP anpassen: JMP verliert den eigenen Codec und liefert nur noch seine Teile-Tabelle, dafür gibt es einen neuen JMP-Release.
>   - Discord-Announcement, das die Umstellung genau erklärt. Es bittet die User, ihre Codes im Drohnen-Channel (<#1554530913365397614>) auf der Website in PW3 umzurechnen und neu zu posten.

Aus dem Plan nach 0.5.2 (Stand 17.10.), Phase 2:

> **Share-Codes PW3:** Codec in der Basis, eine Tabelle pro Add-on, Prüfsumme, fehlende Add-ons anzeigen. PW1/PW2 lesen, nur noch PW3 schreiben. Website rechnet alte Codes um und lädt Add-on-Jars lokal, dazu ein offizielles Verzeichnis. JMP-Release ohne eigenen Codec.
> … gründliche Prüfung …: alle Share-Code-Generationen. … Website: Changelog, Doku, PW3-Umrechner, Add-on-Verzeichnis …

Aus dem Entwurf der Add-on-Richtlinien (17.10., Namensraum `propwash` generell reserviert):

> Ab Propwash 0.6.0 erkennen Sharecodes jedes Teil an seinem Namensraum. Ein Teil im fremden Namensraum macht jeden Code kaputt, der es enthält.

Aus der Discord-Auswahl vom 17.10. (sinngemäß, Feld „Ziel“):

- Punkt 32, Whoop-Motoren mit sehr hoher KV (30000+ KV für 1S): ja, in die nächste JMP-Version, die mit 0.6.0 kommt.
- Gemeldet und der Roadmap zugeordnet: Der Import eines Sharecodes scheitert ohne Just More Parts ohne jeden Hinweis.
  PW3 löst das mit der Anzeige fehlender Add-ons.

Zusätzlich gilt aus dem Plan: 0.6.0 wird vor dem Release vollständig geprüft. Abschnitt 6 beschreibt eine Prüfung, die
ohne Handtests auskommt.

---

## 2. Umfang

### 2.1 Drin

1. **Format PW3** (Präfix `PW3-`, Base62, Rahmen mit 16-Bit-Prüfsumme wie PW2) mit einem Block **Namensräume**: jeder
   Namensraum einmal (Kurzkennung oder voller Name), je Namensraum Prüfsumme, Mindestversion und optional Name und
   Link. Teile verweisen auf ihren Namensraum und eine Nummer aus dessen Tabelle. Lack, Tuning, OSD, Name und
   Erweiterungen sind bitgleich zu PW2 kodiert.
2. **Codec in Propwash** (Paket `share`), JMP verliert ihn. Propwash liest PW1, PW2 und PW3 und schreibt nur PW3.
3. **Teile-Tabelle je Namensraum** als Datei im Jar (`data/<namensraum>/propwash_share/table.json`): nur hinten
   erweitert, Einträge mit `since`, optional `deprecated` + `replaced_by` und `fallback`. Die Tabelle von Propwash liegt
   im Propwash-Jar, die von JMP im JMP-Jar.
4. **Prüfwerkzeug** `ShareTableCheck` im API-Paket (MIT): prüft beim Bauen eines Add-ons, ergänzt neue Teile hinten,
   friert die Tabelle zum Release ein. Dazu Prüfungen zur Laufzeit beim Start.
5. **Kurzkennungen** aus dem offiziellen Verzeichnis (1 = `propwash`, 2 = `justmoreparts`, danach fortlaufend), mit
   einem **Verzeichnis-Abzug** im Propwash-Jar (Namen, Links, Tabellen, Ersatzteile der offiziellen Add-ons).
6. **Import mit fehlenden Add-ons:** Hinweis „braucht X ab 1.2“ mit Name, Version und Link, Warnung bei falscher
   Prüfsumme, Einkaufsliste für Überleben, Ersatzteile (Kreativ und Werkbank).
7. **Oberfläche und Befehle in Propwash:** Knopf „Sharecode“ in der Werkbank, Fenster „Sharecode“, Konfigurator-Tab
   `propwash:sharecode`, Befehle `/propwash code export|import|info|give|convert`. Funktionsumfang wie bisher in JMP,
   erweitert um Punkt 6 und das Umrechnen.
8. **API** `de.lardtom.propwash.api.share` (Lesen, Schreiben, Umrechnen für andere Mods, etwa den Server-Mod) als Teil
   von API 1.7.0.
9. **JMP 1.2.0:** ohne Codec, Oberfläche und Befehl (nur noch ein Hinweis bei `/jmp code`), mit Teile-Tabelle,
   Prüfwerkzeug im Build und neuen Whoop-Motoren mit 30000+ KV.
10. **Website:** PW3 lesen und schreiben, PW1/PW2 beim Einfügen und per Link automatisch umrechnen, eigene
    Umrechner-Seite für ganze Discord-Beiträge, Add-on-Jar lokal im Browser laden, offizielles Add-on-Verzeichnis mit
    Web-Exporten.
11. **Discord-Announcement** zur Umstellung (Anhang A).

### 2.2 Bewusst nicht drin

| Nicht drin | Grund |
|---|---|
| PW1 oder PW2 schreiben (Mod und Website) | Vorgabe: nur noch PW3 schreiben |
| Rückweg PW3 → PW2 für Spieler auf 0.5.x | nicht gefordert; ältere Versionen melden „Code aus einer neueren Version“, Update löst es |
| ~~Werte der Kreativ-Drohne im Sharecode~~ | **Nachtrag 28.10. (Owner, Befund PWD3-1): doch in 0.6.0**, als Revision 3.1 mit der Erweiterungs-Ebene 0 (`docs/sharecode/pw3.md` §3.6, §7.1); API unverändert, ein internes Paket `propwash:share_creative_create` statt einer Änderung an `BuildSpec`/`ImportRequest` |
| Teile, die ein Add-on nur per Java (`PartRegistry#register`) anmeldet, im Browser | der Jar-Loader liest nur Daten-Dateien; solche Teile gehen im Code trotzdem, die Website meldet sie als „nur im Spiel“ |
| Hochladen von Jars auf den Server der Website | Vorgabe: nichts hochladen |
| Automatische Aufnahme ins Verzeichnis | Aufnahme nur nach Prüfung durch lardtom (Lizenz der Assets, Namensraum, Tabelle) |
| Eigene Kurzkennungen ohne Verzeichnis | Doppelte wären nicht zu verhindern |
| Gradle-Plugin für das Prüfwerkzeug | Veröffentlichen eines Plugins kostet mehr als es bringt, ein `JavaExec`-Aufruf reicht |
| Neue Netzwerk-Pakete | nicht nötig, Abschnitt 3.14 |
| Kurzkennungen für Lack-Farben, OSD-Wörterbuch oder Tuning-Schlüssel je Add-on | diese Tabellen gehören Propwash; Fremdes geht wie in PW2 als Text |

---

## 3. Architektur und Datenformate

### 3.1 Überblick

```
             Propwash-Jar                               Add-on-Jar (z. B. JMP)
  data/propwash/propwash_share/table.json      data/justmoreparts/propwash_share/table.json
  data/propwash/propwash_share/directory.json  data/justmoreparts/propwash_parts/**.json
                 │                                              │
                 └──────────────► share.ShareTables ◄───────────┘   (beim Start, nach dem Einfrieren der Teile)
                                        │
          ┌─────────────────────────────┼──────────────────────────────┐
     share.ShareFormat           share.Pw3Codec                share.legacy.Pw1Reader / Pw2Reader
     (Präfix erkennen)           (lesen + schreiben)           (nur lesen, Tabellen eingefroren)
          │                             │
          └──────────► share.ShareDecoded (Inhalt + Bericht je Namensraum)
                                        │
        share.MissingAddons · share.Substitutes · share.ShareConvert
                                        │
   share.client (Fenster, Tab, Werkbank-Knopf) · share.ShareCommands · api.share.ShareCodes
```

Der Codec ist reines Java ohne Minecraft-Typen bis auf `Identifier` (wie heute in JMP). Tabellen und Verzeichnis
liest `share.ShareTables` direkt aus den Mod-Jars über `Services.PLATFORM.readModResources(modId, "data", ".json")`,
nicht über Datenpakete: Ein Datenpaket kann eine Tabelle weder ersetzen noch verbiegen, Server und Client haben
dieselben Tabellen wie ihre Jars.

### 3.2 Textform

```
PW3-<base62>
```

- Präfix `PW3-` (Vorgabe). Danach die Base62-Zeichen wie in PW2 (Alphabet `0-9A-Za-z`, Groß- und Kleinschreibung
  zählt), Bytes als große Zahl mit fester Länge `z(n)`.
- **Einlesen (tolerant):** Leerraum entfernen, Länge über 4000 → `TOO_LONG`. Präfix ohne Rücksicht auf Groß- und
  Kleinschreibung: `PW3-`, `PW3` und `pw3-` gelten gleich, alle `-` im Rest werden entfernt. `PW1…` und `PW2…` gehen
  an die alten Leser (3.9). `PWT1-` (Tuning-Preset) und `PWD1-` (Drohnen-Editor) ergeben den neuen Status
  `OTHER_CODE` mit einem Hinweis, wo der Code hingehört. `PW` mit einer Zahl ab 4 → `UNSUPPORTED_VERSION`.
- **Ohne Präfix:** Besteht der Text nur aus Base62-Zeichen, liest Propwash ihn als PW3-Rumpf, wenn Rahmen, Prüfsumme
  und Bitstrom gültig sind; sonst `INVALID_PREFIX`. Grund: Ein Doppelklick in Discord oder im Browser markiert nur den
  Teil nach dem Bindestrich.
- URL der Website: `configurator/#PW3-…` (der Bindestrich ist URL-sicher).

### 3.3 Rahmen

Wie PW2: Nutzdaten (Bitstrom, mit 0-Bits auf ganze Bytes aufgefüllt), dahinter 2 Bytes mit den unteren 16 Bit der
CRC-32 (IEEE) über die Nutzdaten, big-endian. Rahmen über 2500 Bytes → `TOO_LARGE`, unter 3 Bytes → `TRUNCATED`,
Prüfsumme falsch → `CHECKSUM`. Kein Versionsbyte: Spätere Revisionen von PW3 bleiben über Erweiterungs-Ebenen lesbar,
ein inkompatibles Format bekäme `PW4-`.

### 3.4 Bitstrom

Grundtypen wie PW2 §4.1 (`bit`, `u(n)`, `eg(k)`, `zz`, `idx(n)`, `text`). Neu ist `idtext`:

| Typ | Kodierung |
|---|---|
| `idtext` | `eg(3)` Länge − 1, dann je Zeichen `u(6)` als Index in `abcdefghijklmnopqrstuvwxyz0123456789_.-/` (40 Zeichen; Index ≥ 40 → `CORRUPT`). Für Namensräume (1–64 Zeichen, ohne `/`), Pfade (1–128 Zeichen) und Projekt-Slugs (1–64 Zeichen) |

#### 3.4.1 Aufbau

```
bit hatLack  bit hatTuning  bit hatOsd  bit hatName  bit hatErweiterungen
NAMENSRÄUME                       (immer)
TEILE                             (immer)
LACK      wenn hatLack            (bitgleich PW2 §4.4)
TUNING    wenn hatTuning          (bitgleich PW2 §4.5, Schlüssel-Tabelle PW2 + Anhänge)
OSD       wenn hatOsd             (bitgleich PW2 §4.6, gleiches Wörterbuch)
text NAME wenn hatName            (bitgleich PW2)
ERWEITERUNGEN wenn hatErweiterungen (bitgleich PW2 §4.2)
```

#### 3.4.2 NAMENSRÄUME

```
eg(0) n − 1                          (1 ≤ n ≤ 16, sonst CORRUPT)
je Namensraum i, in der Reihenfolge des ersten Vorkommens in TEILE:
    eg(1) s                          s = 0: idtext namensraum folgt; s ≥ 1: Kurzkennung s (3.6)
    [idtext namensraum]              nur bei s = 0; [a-z0-9_.-], nicht propwash bei s = 0 (CORRUPT),
                                     nicht pw2.unknown, nicht pw3.unknown
    bit hatTabelle                   1: Teile dieses Namensraums als Tabellen-Nummer
    [u(10) prüfsumme]                nur bei hatTabelle (3.7)
    bit hatVersion
    [version]                        eg(0) major, eg(0) minor, eg(0) patch, bit hatZusatz, [text zusatz (1–32)]
    bit hatInfo
    [text name]                      1–48 Zeichen
    [u(2) linkart, link]             0 kein Link · 1 Modrinth: idtext slug · 2 CurseForge: idtext slug
                                     · 3 https-Adresse: text ohne „https://“ (1–200 Zeichen, druckbares ASCII)
```

Ein Namensraum, auf den kein Teil verweist, wird gelesen und in `skipped` als `namespace_unused:<i>` vermerkt. Doppelte
Namensräume → `CORRUPT`.

#### 3.4.3 TEILE

```
ref(FRAME) ref(STACK) ref(MOTOR) ref(PROP) ref(VIDEO) ref(BATTERY)
eg(0) zubehörAnzahl (≤ 255), je Eintrag: eg(0) platz, ref(ACCESSORY)
```

Plätze wie PW2 (0 `top`, 1 `bottom`, 2 `front`, nur anhängen), unbekannte und doppelte Plätze wie PW2.

`ref(art)`:

```
idx(n) namensraum
hatTabelle = 1:  eg(3) v    v = 0: idtext pfad folgt (Teil fehlt in der Tabelle)
                            v ≥ 1: Eintrag v − 1 der Tabelle dieser Art im Namensraum
hatTabelle = 0:  idtext pfad
```

- Liegt v hinter dem Ende der installierten Tabelle (oder fehlt das Add-on), heißt das Teil intern
  `pw3.unknown:<namensraum>/<art>/<v>` (Anzeige `?<art>.<v>` mit dem Add-on-Namen), gilt als unbekannt und wird beim
  erneuten Schreiben mit derselben Nummer und dem unveränderten Kopf des Namensraums geschrieben (3.7).
- Kodierer: Steht die Id in der Tabelle ihrer **Feld-Art** und ihres Namensraums, wird die Nummer geschrieben, sonst
  `v = 0` mit Pfad. Ein Teil im falschen Feld wird beim Lesen als `wrongKind` gemeldet wie bisher.

#### 3.4.4 LACK, TUNING, OSD, NAME

Unverändert aus PW2 übernommen, auch alle Exp-Golomb-Parameter, die eingefrorene Farbtabelle (51 Farben) und das
eingefrorene OSD-Wörterbuch (8429 Bytes, CRC-32 `0xb87eac81`). Die Tuning-Schlüssel-Tabelle (1–57 aus PW2) wird nur
hinten erweitert, wenn 0.6.0 neue Flug-Schlüssel bringt; Schlüssel fremder Namensräume gehen als Text. So bleibt der
Code für diese Ebenen von PW2 übertragbar, und der PW2-Code in Java und JavaScript wird wiederverwendet.

#### 3.4.5 Grenzen

Wie PW2 (4000 Zeichen, 2500 Bytes Rahmen, 256 Tuning-Einträge, 8192 Zeichen OSD-JSON, 48 Zeichen Name), dazu
16 Namensräume, Namensraum 64 Zeichen, Pfad 128, Slug 64, Link 200, Versionszusatz 32. Ein Leser wirft nie eine
Ausnahme nach außen, prüft jede Länge gegen die restlichen Bits und legt keinen Speicher nach Angaben aus dem Code an.

#### 3.4.6 Erweiterungen

Format wie PW2. Vergeben: `e = 0` für die Werte der Kreativ-Drohne. Nachtrag 28.10. (Befund PWD3-1): 0.6.0 schreibt sie
als Revision 3.1 (Ebene `CREATIVE`, `docs/sharecode/pw3.md` §3.6); ohne Kreativ-Werte bleibt jeder Code bytegleich.

### 3.5 Teile-Tabelle je Namensraum

**Datei:** `data/<namensraum>/propwash_share/table.json` im Jar der Mod, deren Mod-Id der Namensraum ist.

```json
{
  "format": 1,
  "namespace": "justmoreparts",
  "short_id": 2,
  "parts": {
    "frame": [
      {"id": "cw_25_duct", "since": "1.0.0", "fallback": "propwash:cinewhoop_3"},
      {"id": "cw_35_duct", "since": "1.0.0", "fallback": "propwash:cinewhoop_3"}
    ],
    "motor": [
      {"id": "m0702_23000", "since": "1.0.0", "fallback": "propwash:m0802_22000"},
      {"id": "m0702_30000", "since": "1.2.0", "fallback": "propwash:m0802_22000"}
    ],
    "prop": [
      {"id": "p40_14_5b_soft", "since": "1.0.0", "deprecated": "1.3.0", "replaced_by": "justmoreparts:p40_14_5b_pc"}
    ]
  }
}
```

| Feld | Regel |
|---|---|
| `format` | 1; unbekanntes höheres Format → Tabelle ignorieren, Teile gehen als Text, Log-Warnung |
| `namespace` | muss zum Ordner passen; `propwash` nur im Propwash-Jar |
| `short_id` | optional, vom Verzeichnis vergeben (3.6) |
| `parts.<art>` | Arten `frame, stack, motor, prop, video, battery, accessory`; Reihenfolge = Nummer; Ids ohne Namensraum |
| `since` | Pflicht: Version des Add-ons, mit der der Eintrag dazukam; in der Liste nicht fallend |
| `deprecated`, `replaced_by` | gestrichenes Teil: Version des Streichens und volle Id des Ersatzes (gleiche Art, Ziel existiert, keine Kreise) |
| `fallback` | optional: volle Id eines eingebauten Propwash-Teils gleicher Art, das als Ersatz dient, wenn das Add-on fehlt |

**Regeln:** Einträge kommen nur hinten dazu; nie umsortieren, nie löschen, nie eine Nummer wiederverwenden. Ein
umbenanntes Teil bekommt einen neuen Eintrag, der alte wird `deprecated` mit `replaced_by` (ersetzt die bisherige
Alias-Tabelle `PartAliases`). Ein gestrichenes Teil wird beim Lesen nur dann durch den Ersatz ersetzt, wenn die alte Id
nicht mehr angemeldet ist; gemeldet in `renamed`.

**Laden** (`share.ShareTables`, nach dem Einfrieren des Teile-Registers): je Mod aus `resourceModIds()` die Dateien
`data/*/propwash_share/table.json` lesen. Eine Tabelle zählt nur, wenn Ordner, Feld `namespace` und Mod-Id
übereinstimmen; Tabellen für `propwash` aus fremden Jars werden verworfen (Log-Fehler, passend zur Reservierung des
Namensraums im Design-Dokument der Add-on-API dieses Ordners). Danach je angemeldetem Teil prüfen:

- Teil ohne Eintrag in der Tabelle seines Namensraums → Log-Warnung einmal je Add-on, das Teil geht als Pfad (`v = 0`).
- Add-on mit Teilen, aber ohne Tabelle → einmal Log-Hinweis, alle Teile gehen als Text (Code länger, aber gültig).
- Eintrag ohne angemeldetes Teil und ohne `deprecated` → Log-Warnung (Datenfehler des Add-ons).
- Im Selbsttest und mit `-Dpropwash.share.strict=true` wird jede dieser Meldungen zum Fehler.

**Tabelle von Propwash:** entsteht aus der PW2-Tabelle (`Pw2Tables`), gefiltert auf `propwash:`, in PW2-Reihenfolge;
`since` 0.4.0, für `creative_fc`, `action_cam_flashback`, `gripper` und `p51_29_3b` 0.5.0; neue Teile von 0.6.0 hinten.
**Tabelle von JMP:** ebenso aus PW2 gefiltert auf `justmoreparts:`, `since` aus den Tags `v1.0.0`/`v1.1.0` von JMP,
dann die neuen Whoop-Motoren mit `since` 1.2.0. Damit ist die Zuordnung PW2 → PW3 eine feste, prüfbare Abbildung.

### 3.6 Kurzkennungen und Verzeichnis-Abzug

- Kurzkennung = ganze Zahl ≥ 1, vergeben vom offiziellen Verzeichnis auf propwashfpv.com, nie wiederverwendet.
  Fest: **1 = `propwash`, 2 = `justmoreparts`**. `eg(1)` kostet für 1 zwei Bit, für 2–5 vier Bit.
- **Verzeichnis-Abzug** `data/propwash/propwash_share/directory.json` im Propwash-Jar: je offiziellem Add-on
  Namensraum, Kurzkennung, Name (en_us/de_de), Modrinth- und CurseForge-Slug, neueste bekannte Version und seine
  Tabelle samt Teilenamen (en_us/de_de) und `fallback`. Erzeugt aus den Verzeichnisdaten der Website mit
  `tools/sharecode/sync_directory.py`, bei jedem Propwash-Release neu. Größe für JMP rund 15 KB.
- **Auflösen beim Lesen:** (1) Abzug, (2) installierte Tabellen mit `short_id`, die der Abzug nicht kennt und die genau
  ein Add-on beansprucht. Widerspricht eine installierte Tabelle dem Abzug, gilt der Abzug (Log-Warnung). Unbekannte
  Kurzkennung → „Add-on Nr. s aus dem offiziellen Verzeichnis (neuer als diese Propwash-Version)“ mit Verweis auf
  `propwashfpv.com/addons/`.
- **Schreiben:** Kurzkennung nur, wenn sie nach denselben Regeln eindeutig ist, sonst der volle Namensraum.

### 3.7 Prüfsumme und Mindestversion

- **Prüfsumme je Namensraum** (`u(10)`): untere 10 Bit der CRC-32 über die UTF-8-Zeilen `<art>:<v>:<pfad>\n` aller im
  Code benutzten Tabellen-Einträge dieses Namensraums, sortiert nach Art (Reihenfolge wie TEILE) und Nummer, ohne
  Doppelte. Sie deckt nur die benutzten Einträge ab: Ein Leser mit neuerer Tabelle (hinten erweitert) kommt auf
  denselben Wert, ein umnummeriertes Teil oder ein fremdes Add-on unter gleichem Namensraum fällt auf.
- **Ergebnis je Namensraum:** `MATCH`; `MISMATCH` („Code passt nicht zur installierten Version von X“); `NEWER`
  (Nummer hinter dem Tabellenende, „braucht X ab V“); `MISSING` (Add-on fehlt); `NO_TABLE` (Teile als Text, nichts zu
  prüfen).
- **Mindestversion:** Der Kodierer schreibt die größte `since` der benutzten Einträge, ohne Einträge die installierte
  Version. So steht dort „braucht JMP ab 1.0.0“, wenn nur alte Teile vorkommen, statt der zufällig installierten
  Version.
- **Neu schreiben mit unbekannten Einträgen:** Enthält der Inhalt `pw3.unknown`-Teile eines Namensraums, übernimmt der
  Kodierer dessen Kopf (Prüfsumme, Version, Info) unverändert, solange sich die Teile dieses Namensraums nicht geändert
  haben; sonst lehnt er mit `UNKNOWN_ENTRIES` ab („enthält Teile aus einer neueren Version von X“).

### 3.8 Lesen, Ergebnis, fehlende Add-ons

`share.ShareDecoded` (Ausbau des JMP-Typs):

| Feld | Inhalt |
|---|---|
| `status`, `format` | Status wie PW2 plus `OTHER_CODE`; Format `PW1`, `PW2` oder `PW3` |
| `content` | `ShareContent` (Bauplan, Lack, Tuning, OSD, Name), Ids nach `replaced_by` |
| `namespaces` | je Namensraum: Id, Kurzkennung, installierte Version, Mindestversion, Prüfsummen-Ergebnis, Name und Link (aus Code, Abzug oder Mod-Metadaten `ModMeta`), vertrauenswürdiger Link ja/nein |
| `unknownParts`, `wrongKind`, `renamed`, `skipped` | wie bisher |
| `inactiveParts` | Teile von Add-ons, die nur beim Spieler installiert und in dieser Sitzung inaktiv sind (`build.PartAvailability`) |

**Links:** Klickbar sind nur Modrinth- und CurseForge-Projekte (Linkart 1 und 2) und die Links des Abzugs, immer über
den Bestätigungsdialog von Minecraft. Eine freie https-Adresse (Linkart 3) erscheint nur als Text mit dem Hinweis
„Lade Add-ons nur von Modrinth oder CurseForge“. Name und Adresse werden nur als Literal angezeigt (`join.SafeText`).

**Einkaufsliste (Überleben):** wie bisher in JMP (`WorkbenchContext#missingParts`, Werkbank-Slots und Inventar).
Unbekannte Teile stehen mit Namen aus dem Abzug, sonst als „Motor Nr. 12“, und dem Vermerk „braucht X ab V“.

**Ersatzteile** (`share.Substitutes`, reines Java, deterministisch): je unbekanntem oder inaktivem Teil
1. `fallback` des Eintrags (aus installierter Tabelle oder Abzug), wenn es in der Sitzung verfügbar ist und
   `builds().check` keine Probleme meldet;
2. sonst die Presets von Propwash der Reihe nach: das erste Preset, dessen Teile an allen unbekannten Stellen einen
   Bauplan ohne Probleme ergeben;
3. sonst kein Ersatz („kein passendes Ersatzteil“), die Ebene Teile ist dann nicht übernehmbar.

Kreativ: Knopf „Mit Ersatzteilen erzeugen“. Werkbank (beide Modi): Schalter „Ersatzteile verwenden“, die
Einkaufsliste rechnet dann mit dem Ersatz. Jeder Ersatz wird als Zeile „alt → neu“ gezeigt, der Name der Drohne bleibt.

**Prüfsumme falsch:** Teile werden gezeigt, die Ebene Teile ist gesperrt, bis der Spieler „Trotzdem übernehmen“
bestätigt. Lack, Tuning, OSD und Name gehen immer.

### 3.9 PW1/PW2 lesen und umrechnen

- `share.legacy.Pw1Reader` und `Pw2Reader`: die Leser aus JMP (`ShareCodec` für PW1, `Pw2Codec`), nur lesen, mit
  eingefrorenen Tabellen (`Pw1Tables`, `Pw2Tables`). Keine Kodierer im Mod-Code; die Testvektoren beider Formate
  liegen als Golden-Dateien in den Tests.
- `share.ShareConvert#toPw3(alt)`: liest den alten Code und schreibt PW3 **nur mit eingefrorenen Daten**: Tabelle von
  Propwash und JMP-Tabelle aus dem Abzug, Mindestversion aus deren `since`, keine Info-Felder. Fremde Text-Ids aus PW2
  gehen als voller Namensraum (`s = 0`, `hatTabelle = 0`, Pfad als Text). Das Ergebnis hängt also nur vom alten Code ab
  und ist in Mod und Website bytegleich.
- Nicht umrechenbar (Status `NOT_CONVERTIBLE` mit Begründung): Teile `pw2.unknown:…` (`?frame.200`) und Namensräume
  `pw1.unknown.N`. Beides kann nur ein von Hand gebauter Code enthalten.
- Wer einen alten Code im Spiel übernimmt und die Drohne neu exportiert, bekommt PW3 mit den installierten Tabellen
  (ggf. kürzer als die Umrechnung, weil ein installiertes Fremd-Add-on dann Nummern nutzt). Das ist gewollt.

### 3.10 Prüfwerkzeug `ShareTableCheck`

Klasse `de.lardtom.propwash.api.share.ShareTableCheck` (MIT, im Propwash-Jar, reines Java mit Gson), aufrufbar per
`main` und aus JUnit (`ShareTableCheck.check(...)` liefert eine Liste von Befunden).

| Befehl | Wirkung |
|---|---|
| `check --resources <dir> --version <v> [--baseline <datei oder jar>]` | prüft, Exit-Code ≠ 0 bei Befunden |
| `add --resources <dir> --version <v>` | hängt jedes Teil aus `propwash_parts/` ohne Eintrag hinten an (je Art alphabetisch), setzt `since`, schlägt `fallback` nach der Preset-Regel vor |
| `freeze --resources <dir> --out <baseline>` | schreibt die Vergleichsdatei nach einem Release |
| `init --resources <dir> --namespace <ns> --version <v>` | legt eine neue Tabelle an |

Befunde: Teil ohne Eintrag; Eintrag gelöscht, verschoben oder eingeschoben (gegen die Vergleichsdatei; ohne sie über
`since` nicht fallend und `since` ≤ aktuelle Version); `deprecated` ohne gültiges `replaced_by`, Kreis, falsche Art;
`fallback` kein eingebautes Propwash-Teil gleicher Art; Namensraum `propwash` außerhalb von Propwash; doppelte Ids;
ungültige Zeichen; `short_id` nicht im Abzug oder dort einem anderen Namensraum zugeordnet. Einträge mit `since` =
aktuelle Version gelten als unveröffentlicht und dürfen noch umgestellt werden.

Gradle in einem Add-on (Common-Modul):

```groovy
tasks.register('checkShareTable', JavaExec) {
    classpath = sourceSets.main.compileClasspath
    mainClass = 'de.lardtom.propwash.api.share.ShareTableCheck'
    args 'check', '--resources', file('src/main/resources'), '--version', project.version,
         '--baseline', file('sharecode-baseline.json')
}
tasks.named('check') { dependsOn 'checkShareTable' }
```

Propwash prüft die eigene Tabelle in JUnit (`ShareTableTest`) gegen `common/src/test/resources/sharecode/propwash-baseline.json`.

### 3.11 Klassen und Pakete in Propwash

| Paket / Datei | Inhalt | Seite |
|---|---|---|
| `share` | `ShareModule` (Laden nach dem Einfrieren), `ShareFormat` (Präfix, Weiche), `Pw3Codec`, `Pw3Params`, `Base62`, `BitReader`, `BitWriter`, `IdText`, `ShareContent`, `ShareDecoded`, `ShareStatus`, `NamespaceReport`, `ShareTable`, `ShareTableJson`, `ShareTables`, `TableChecksum`, `AddonDirectory`, `MissingAddons`, `Substitutes`, `ShareConvert`, `ShareCommands`, `ShareServer` | beide |
| `share.legacy` | `Pw1Reader`, `Pw1Tables`, `Pw2Reader`, `Pw2Tables` (eingefroren) | beide |
| `share.client` | `ShareClient` (meldet Werkbank-Knopf und Tab über die bestehenden Register an), `ShareScreen`, `ShareConfigTab`, `ShareImportView`, `AddonsPanel`, `SubstitutePanel`, `ShoppingList`, `ShareLines`, `ShareScene`, `ShareSettings` | Client |
| `api.share` | `ShareCodes` (Fassade), `ShareResult`, `ShareOptions`, `ShareTableCheck` | beide |
| `data/propwash/propwash_share/` | `table.json`, `directory.json` | Jar |
| `common/src/lang/share/` | `de_de.json`, `en_us.json` | Client |
| `docs/sharecode/` | `pw3.md` (normativ), `pw2.md`, `pw1.md` (aus JMP übernommen, eingefroren), `tables.md` (Anleitung für Add-ons) | Doku |

Werkbank-Knopf und Tab hängen sich über `WorkbenchExtension` und `registerConfigTab` ein: Der Strang ändert keine
Datei in `content` oder `ui`. `PropwashClientApi.openConfigTab("justmoreparts:sharecode")` leitet auf
`propwash:sharecode` um, damit alte Aufrufe weiter funktionieren.

### 3.12 API (Teil von API 1.7.0)

```java
public interface ShareCodes {
    ShareResult decode(String text);
    String encode(DroneData data, ShareOptions options);
    ShareResult convert(String legacyCode);
}
```

`PropwashApi.shareCodes()` liefert die Fassade. `ShareResult` ist ein Record (Status, Format, `Optional<DroneData>`,
OSD-JSON, Namensraum-Berichte, unbekannte Teile, Text des umgerechneten Codes). `ShareOptions` wählt Ebenen und
„Add-on-Infos mitschreiben“. Grund für die API: Server-Mod und Discord-Bot können Codes von Sieger-Drohnen ausgeben oder
Drohnen per Code verteilen, ohne Propwash-Interna anzufassen. Das Format selbst ist in `docs/sharecode/pw3.md` offen
beschrieben.

### 3.13 Befehle

`/propwash code export [<ebenen>]`, `import <code>`, `info <code>`, `give <code> [<spieler>]` mit Rechten und Verhalten
wie bisher `/jmp code` (PW1-Spezifikation §9), dazu `convert <code>`: rechnet PW1/PW2 um und schreibt den PW3-Code
anklickbar in den Chat (Stufe 0). `info` nennt zusätzlich je Namensraum Version, Prüfsummen-Ergebnis und Link.
`give` und `import` im Kreativmodus nutzen auf Wunsch Ersatzteile (`… import <code> substitutes`).

### 3.14 Netzwerk

Keine neuen Pakete. Der Client liest Codes selbst und übernimmt über die vorhandenen Wege (`propwash:api_import`,
`sendCreateDrone`, `WorkbenchContext#missingParts`); der Server prüft wie bisher Rechte, Kosten und Teile. Befehle lesen
am Server mit den Tabellen des Servers. Dieser Strang ändert das Netzwerkprotokoll nicht (andere 0.6.0-Stränge erhöhen es
ohnehin).

### 3.15 Just More Parts 1.2.0

- Entfernt: Paket `share` (Codec, Tabellen, Oberfläche, Einkaufsliste), Sprachdateien `common/src/lang/share/`, Tests
  im Paket `share` (der Flugzeit-Test `OwnerFlightTimeTest` zieht in ein anderes Paket um).
- `/jmp code …` bleibt als Hinweis: „Sharecodes gehören jetzt zu Propwash: `/propwash code …`“ (anklickbarer Vorschlag),
  ohne eigene Ausführung. Weg in 1.3.0.
- Neu: `data/justmoreparts/propwash_share/table.json` (Kurzkennung 2, alle 143 Teile, `fallback` je Teil),
  `sharecode-baseline.json`, Gradle-Aufgabe `checkShareTable` in `check`.
- **Whoop-Motoren mit 30000+ KV für 1S** (Discord-Auswahl Punkt 32): mindestens 0702 mit etwa 30000 KV und etwa
  33000 KV, dazu ein 0802 mit etwa 30000 KV, wenn die Daten es tragen. Werte nach Herstellerdaten ohne Markennamen,
  Beleg in `docs/design/parts-catalog.md`, Prüfstand im Test (Drehzahl, Strom, Wärme auf 1S HV). Ans Ende der
  Motor-Tabelle mit `since` 1.2.0.
- Abhängigkeit: Propwash ab 0.6.0 einschließlich der Vorabstände (`propwash_version_range=[0.6.0-alpha,)`, Fabric
  `>=0.6.0-`; `[0.6.0,)` bzw. `>=0.6.0` lehnten `0.6.0-rc.N` bzw. alle Vorabstände ab, Übersicht `README.md` §3.2), API ab 1.7.
- Web-Export (`devtest`, Paket `export`) entfällt; den Export für Website und Verzeichnis erzeugt `/propwash webexport`
  in der Basis (Format 2, `website-jmp.md` P1), Orakel ist dieser Export mit geladenem JMP (Übersicht `README.md` K4).

### 3.16 Website

**PW3 im Konfigurator** (`assets/js/configurator/sharecode3.js`, Port von `Pw3Codec`, bytegleich): Lesen und Schreiben,
Tabellen aus den Katalogen je Namensraum. `sharecode.js` wird zur Weiche (PW1, PW2, PW3, `PWT1`/`PWD1`-Hinweis). Ein
eingefügter alter Code oder ein Link `#PW2…` wird sofort umgerechnet; Hinweis „Alter Code umgerechnet“ mit Knopf
„PW3 kopieren“, die Adresse wechselt auf `#PW3-…`. Geschrieben wird nur noch PW3.

**Umrechner** `/convert/` (Seite, zweisprachig, Übersicht `README.md` K5): ein Textfeld für ganze Discord-Beiträge; alle `PW1-…`/`PW2…` darin
werden gefunden und an Ort und Stelle ersetzt, darunter je Code eine Zeile (umgerechnet, nicht umrechenbar mit Grund),
Knopf „Alles kopieren“. Rechnet im Browser, schickt nichts weg.

**Kataloge je Namensraum:** `tools/build-configurator-data.mjs` und `-models.mjs` teilen ihre Logik mit dem Browser
(`assets/js/configurator/catalog-build.js`, ohne `fs`). Ergebnis: `assets/data/configurator/catalog.json` (Propwash:
Regeln, Tuning, Lack, FPV, Teile von `propwash`) und je Add-on `assets/data/addons/<ns>/<version>/catalog.json` samt
Modellen. JMP wird wie heute immer geladen.

**Jar lokal laden** (`jar.js`, `partjson.js`, `addon-catalog.js`): Datei per Auswahl oder Ziehen; ZIP-Verzeichnis
lesen, Einträge mit dem vorhandenen `inflate.js` entpacken; gelesen werden nur `data/*/propwash_parts/**.json`,
`data/*/propwash_share/table.json`, `assets/*/lang/{en_us,de_de}.json`, Modelle, Render-Definitionen und Texturen.
`partjson.js` ist ein Port von `parts.PartJson` (Standardwerte und abgeleitete Felder), damit ein Teil aus dem Jar
dieselben Katalogdaten ergibt wie aus dem Web-Export. Nie ausgeführt wird Code aus dem Jar. Grenzen: Jar bis 64 MB,
höchstens 20000 Einträge, je JSON 2 MB, je Textur 4 MB und 1024 px Kante, entpackt insgesamt 128 MB, Verhältnis
entpackt/gepackt je Eintrag höchstens 100. Der Jar bleibt nur im Speicher des Tabs (kein IndexedDB, kein Cookie).

**Offizielles Verzeichnis** `addons/` (Seite) mit `assets/data/addons/directory.json`:

```json
{
  "format": 1,
  "addons": [
    {"namespace": "propwash", "short_id": 1, "name": {"en_us": "Propwash FPV", "de_de": "Propwash FPV"},
     "official": true, "modrinth": "propwash-fpv", "curseforge": "<slug>",
     "versions": [{"version": "0.6.0", "table_crc": "…", "catalog": "propwash/0.6.0/"}]},
    {"namespace": "justmoreparts", "short_id": 2, "name": {"en_us": "Propwash: Just More Parts", "de_de": "Propwash: Just More Parts"},
     "official": true, "modrinth": "propwash-just-more-parts", "curseforge": "<slug>",
     "versions": [{"version": "1.2.0", "table_crc": "…", "catalog": "justmoreparts/1.2.0/"}]}
  ]
}
```

Die Web-Exporte des Verzeichnisses erzeugt dieselbe Jar-Logik in Node aus dem veröffentlichten Jar (SHA-256 im
Verzeichnis vermerkt): kein Spielstart nötig, Browser und Verzeichnis rechnen gleich. Alte Versionen bleiben im
Verzeichnis, damit ein Code mit falscher Prüfsumme gegen die passende ältere Tabelle gelesen werden kann. Ein Code mit
Kurzkennung eines Verzeichnis-Add-ons lädt dessen Katalog automatisch (gleiche Herkunft, keine fremden Anfragen).
Mob Defense und das Kamera-Drohnen-Add-on kommen so ins Verzeichnis (Kurzkennungen 3 und 4 bei Aufnahme).

### 3.17 Längen (Schätzung, Strang A misst und trägt sie in `pw3.md` ein)

| Inhalt | PW2 | PW3 (geschätzt) |
|---|---|---|
| nur Teile, Propwash-Preset | 13 | etwa 17 |
| nur Teile, JMP-Preset | 13–14 | etwa 26 |
| JMP-Preset mit BF-Tuning und Name | 72–75 | etwa 85 |
| alles mit OSD | 115–118 | etwa 128 |

Mehrkosten je Add-on: Kurzkennung, 10 Bit Prüfsumme, Mindestversion (rund 25 Bit, 4 Zeichen). Alles passt weiter in
eine Chatzeile (256 Zeichen).

---

## 4. Kompatibilität

| Programm | liest | schreibt |
|---|---|---|
| Propwash 0.6.0 (mit oder ohne JMP 1.2.0) | PW1, PW2, PW3 | **PW3** |
| Propwash 0.5.x mit JMP 1.1.1 | PW1, PW2; PW3 → „Code aus einer neueren Version“ (`UNSUPPORTED_VERSION`, schon in PW2 §2 vorgesehen) | PW2 |
| Propwash 0.5.x ohne JMP | keine Codes | — |
| Website ab 0.6.0 | PW1, PW2, PW3 | **PW3** |
| 1.21.1-Backport (später) | Empfehlung: PW3 lesen und schreiben, eingebaute JMP-Teile mit ihren `justmoreparts:`-Ids und Tabellen-Nummern, damit Codes zwischen den Versionen passen | — |

- **Alte Welten:** Codes stehen nirgends im Spielstand. Drohnen, Items und Ids bleiben unverändert, JMP streicht kein
  Teil. Nichts zu migrieren.
- **Alte Codes:** PW1 und PW2 bleiben für immer lesbar, in Mod und Website. Ihre Tabellen sind eingefroren.
- **JMP 1.1.x mit Propwash 0.6.0 (Altmodus):** keine Unverträglichkeit in den Metadaten, damit ein Update nur von
  Propwash das Spiel nicht unstartbar macht. Propwash erkennt JMP unter 1.2.0 (nur `major.minor.patch` zählt, Vorabstände
  von 1.2.0 sind kein Altmodus) und meldet dann die Werkbank-Erweiterung von JMP für Share-Codes
  (`de.lardtom.justmoreparts.share.client.ShareWorkbenchExtension`) nicht an: Es gibt nur einen Knopf „Sharecode“.
  PW3 schreibt die Basis; JMP-Teile gehen über die JMP-Tabelle aus dem Verzeichnis-Abzug (§3.6), der Text ist also
  derselbe wie mit JMP 1.2.0. `/jmp code` bleibt der alte Befehl von JMP 1.1.x und schreibt PW2, das alle lesen.
  Log-Warnung und einmal je Spielstart ein Chat-Hinweis mit der Bitte, JMP 1.2.0 zu installieren (Übersicht
  `README.md` §3.5).
- **JMP 1.2.0 mit Propwash 0.5.x:** startet nicht (Abhängigkeit ab `0.6.0-` bzw. `[0.6.0-alpha,)`); deshalb erscheint
  Propwash 0.6.0 vor JMP 1.2.0.
- **Server und Client:** Die Beitrittsprüfung verlangt dieselben Add-on-Versionen, also dieselben Tabellen. Add-ons nur
  beim Spieler sind in der Sitzung inaktiv; ihre Teile meldet der Import als „auf diesem Server nicht aktiv“, Ersatz ist
  möglich.
- **API:** neues Paket `api.share` und `PropwashApi.shareCodes()` → Nebenversion, gemeinsam mit den anderen Strängen
  **API 1.7.0**. Nichts Bestehendes ändert sich.
- **Netzwerkprotokoll:** von diesem Strang unverändert.
- **Web-Export:** Format 2 in der Basis (`website-jmp.md` P1); der Abschnitt `sharecode/` enthält die PW3-Vektoren.

---

## 5. Oberfläche und Texte

Propwash-Theme (dunkle Flächen, Akzentfarbe, Widgets aus `ui.client`), alles in `de_de` und `en_us`, deutsch mit
Umlauten. Muss bei GUI 320×240 ohne Abschneiden funktionieren: Spalten werden zu einer, Listen scrollen zeilenweise,
die Prüfung `ui-scale` bekommt Fenster und Tab dazu (Befund `HIDDEN`, wenn „Übernehmen“ oder der Add-on-Hinweis nach
dem Öffnen nicht ganz zu sehen ist). Symbole als Sprites, keine Emojis.

**Orte:** Werkbank-Knopf „Sharecode“ (bisherige Regeln für Platz, Kurzform „Code“, sonst Symbol `</>`), Fenster
„Sharecode“ mit den Reitern Exportieren und Importieren, Konfigurator-Tab „Sharecode“, Befehle.

**Neu im Import:** Kopfzeile mit dem Format; bei PW1/PW2 der Hinweis auf PW3 und „Als PW3 kopieren“. Abschnitt
„Add-ons in diesem Code“ mit einer Zeile je Namensraum (Symbol, Name, nötige Version, installierte Version, Link-Knopf).
Abschnitt „Ersatzteile“ nur bei fehlenden Teilen. Banner bei falscher Prüfsumme. **Neu im Export:** Häkchen
„Add-on-Infos“ (Name und Link für Add-ons ohne Verzeichniseintrag, dort standardmäßig an, sonst aus) und der Hinweis, wenn
ein Add-on keine Tabelle hat.

Wichtige Schlüssel (Auszug; `propwash.share.*`, `propwash.command.code.*`):

| Schlüssel | en_us | de_de |
|---|---|---|
| `propwash.share.title` | Share code | Sharecode |
| `propwash.share.legacy` | Old code (%s) – still works. New codes are PW3. | Alter Code (%s) – funktioniert weiter. Neue Codes sind PW3. |
| `propwash.share.copy_pw3` | Copy as PW3 | Als PW3 kopieren |
| `propwash.share.addons.title` | Add-ons in this code | Add-ons in diesem Code |
| `propwash.share.addons.missing` | %s %s or newer – not installed | %s ab %s – nicht installiert |
| `propwash.share.addons.older` | %s %s or newer – you have %s | %s ab %s – installiert ist %s |
| `propwash.share.addons.inactive` | %s – not active on this server | %s – auf diesem Server nicht aktiv |
| `propwash.share.addons.mismatch` | This code doesn't match your version of %s – parts may be wrong | Der Code passt nicht zur installierten Version von %s – Teile können falsch sein |
| `propwash.share.addons.unknown_short` | Add-on no. %s from the official directory (newer than this Propwash) | Add-on Nr. %s aus dem offiziellen Verzeichnis (neuer als dieses Propwash) |
| `propwash.share.link.modrinth` | Open on Modrinth | Auf Modrinth öffnen |
| `propwash.share.link.curseforge` | Open on CurseForge | Auf CurseForge öffnen |
| `propwash.share.link.untrusted` | Link: %s – only download add-ons from Modrinth or CurseForge | Link: %s – lade Add-ons nur von Modrinth oder CurseForge |
| `propwash.share.substitutes.title` | Stand-in parts | Ersatzteile |
| `propwash.share.substitutes.use` | Use stand-in parts | Ersatzteile verwenden |
| `propwash.share.substitutes.create` | Create with stand-in parts | Mit Ersatzteilen erzeugen |
| `propwash.share.substitutes.none` | No fitting stand-in for %s | Kein passendes Ersatzteil für %s |
| `propwash.share.import_anyway` | Import parts anyway | Teile trotzdem übernehmen |
| `propwash.share.shopping.needs_addon` | %s× %s – needs %s | %s× %s – braucht %s |
| `propwash.share.export.addon_info` | Add-on info (name and link) | Add-on-Infos (Name und Link) |
| `propwash.share.export.no_table` | %s has no parts table – the code gets longer | %s hat keine Teile-Tabelle – der Code wird länger |
| `propwash.share.other.pwt` | This is a tune preset code – paste it in the Rates tab | Das ist ein Code für Tuning-Presets – im Tab „Rates“ einfügen |
| `propwash.share.other.pwd` | This is a Drone Editor code – paste it in the Drone Editor tab | Das ist ein Code des Drohnen-Editors – im Tab „Drohnen-Editor“ einfügen |
| `propwash.share.status.invalid_prefix` | Not a Propwash share code | Kein Propwash-Sharecode |
| `propwash.share.status.not_convertible` | Can't convert: %s | Nicht umrechenbar: %s |
| `propwash.command.code.converted` | PW3 code (click to copy): %s | PW3-Code (Klick kopiert): %s |

Die übrigen Texte aus `common/src/lang/share/` von JMP ziehen mit neuem Präfix um („Kein JMP-Sharecode“ wird „Kein
Propwash-Sharecode“). Website-Texte (Umrechner, Jar laden, Verzeichnis) kommen in `i18n.js`, unter anderem
„Deine Datei bleibt auf deinem Rechner. Es wird nichts hochgeladen.“ / „Your file stays on your computer. Nothing is
uploaded.“

---

## 6. Tests

Ziel: vollständig geprüft, ohne Handtests. Jede Zeile hat ein maschinelles Ergebnis (PASS/FAIL,
Golden-Datei oder Bildschirmfoto mit Prüfer).

### 6.1 JUnit (Propwash, ohne Minecraft)

| Test | Prüft |
|---|---|
| `Pw3CodecTest` | Testvektoren bytegleich (Golden `sharecode/pw3-vectors.json`), Rundlauf für 10000 zufällige Baupläne mit festem Seed (Teile aus allen Tabellen, Text-Ids, unbekannte Nummern, alle Ebenen), jeder Status, tolerantes Einlesen (`pw3-`, ohne Bindestrich, nackter Rumpf, Leerraum, Gruppen), alle Grenzen, nie eine Ausnahme bei 100000 zufälligen und verstümmelten Eingaben |
| `LegacyReadTest` | alle PW1-Vektoren A–G und alle PW2-Vektoren aus JMP (Golden-Kopie) ergeben denselben Inhalt wie in JMP 1.1.1 |
| `ShareConvertTest` | jede PW1/PW2-Golden-Datei und alle 27 Presets in 5 Ebenen-Varianten in beiden alten Formaten → PW3 bytegleich zur Golden-Datei `pw3-conversions.json` (die auch die Website prüft); `pw2.unknown`/`pw1.unknown` → `NOT_CONVERTIBLE` |
| `ShareTableTest` | Tabelle von Propwash deckt jedes eingebaute Teil ab, nur angehängt gegen die Vergleichsdatei, `since` nicht fallend |
| `ShareTableCheckTest` | jeder Befund aus 3.10 an je einer kaputten Tabelle; `add` und `freeze` erzeugen die erwartete Datei |
| `DirectorySnapshotTest` | Abzug gültig: Kurzkennungen eindeutig, 1/2 fest, Tabellen nur angehängt gegenüber dem Abzug des letzten Releases, `fallback` eingebaut |
| `TableChecksumTest` | Werte gegen gemeinsame Vektoren (dieselben prüft die Website) |
| `SubstitutesTest` | deterministisch; für jedes JMP-Teil in jedem JMP-Preset ein Ersatz ohne Probleme; Fall „kein Ersatz“ |
| `MissingAddonsTest` | Berichte für fehlend, älter, Prüfsumme falsch, inaktiv, unbekannte Kurzkennung; Links nur für Modrinth/CurseForge klickbar |
| `ShareTextTest` | jeder Schlüssel in beiden Sprachen, keine Ersatzschreibung statt Umlauten |

JMP-seitig: `ShareTableCheck` im Build, `JmpTableTest` (Tabelle = PW2-Tabelle gefiltert + neue Motoren), Prüfstände der
neuen Motoren. Gegenprobe ohne Minecraft: Tag `v1.1.1` von JMP auschecken und im JUnit-Lauf PW3-Vektoren lesen →
`UNSUPPORTED_VERSION`.

### 6.2 Selbsttest (Fabric und NeoForge), Gruppe `share:`

1. `share: Tabellen geladen` – Propwash, Beispiel-Add-on `propwash_example` (bekommt eine Tabelle mit `deprecated`,
   `replaced_by`, `fallback` und ohne Kurzkennung), keine Meldungen im strikten Modus.
2. `share: Werkbank → Code → Import` (aus JMP übernommen) bei der kleinsten GUI.
3. `share: alter Code` – PW1- und PW2-Vektor einfügen, Hinweis sichtbar, „Als PW3 kopieren“ ergibt die Golden-Umrechnung.
4. `share: fehlendes Add-on` – Code mit Namensraum `propwash_missing` (Info mit Modrinth-Slug) und mit Kurzkennung 2 ohne
   JMP: Zeilen, Link-Knopf, Einkaufsliste mit „braucht …“, „Mit Ersatzteilen erzeugen“ legt eine Drohne ins Inventar.
5. `share: Prüfsumme falsch` – Banner, Teile gesperrt, „Teile trotzdem übernehmen“ wirkt.
6. `share: freie Adresse` – Linkart 3 wird nicht klickbar angezeigt.
7. `share: Konfigurator-Tab` – Export, Import, Tuning und Lack auf die gehaltene Drohne, Weiterleitung von
   `justmoreparts:sharecode`.
8. `share: Befehle` – `export`, `info`, `give`, `convert`, `import … substitutes`.
9. `ui-scale` – Fenster und Tab in allen Fenstergrößen und beiden Sprachen.
10. `bildschirmfotos: Sharecode …` (nur Fabric) – Export, Import PW3, Import PW2 mit Hinweis, fehlende Add-ons,
    Prüfsumme falsch, Ersatzteile, Einkaufsliste im Überleben, Tab; englisch und deutsch, 640×480 und 854×480.

### 6.3 Mehrspieler auf dediziertem Server

Neuer Schalter `PROPWASH_JOINTEST_SHARE=1` (`dev.ShareJoinCheck`) im bestehenden Beitrittstest (Server und Client als
zwei Prozesse, beide Minecraft-Slots):

- Server führt 3 s nach dem Beitritt `/propwash code give <PW3-Vektor> <spieler>` aus → Drohne mit Name im Inventar.
- Client im Überlebensmodus: Import in der Werkbank, Einkaufsliste kommt vom Server (`missingParts`), Übernahme scheitert
  mit `MISSING_PARTS`, nach Geben der Teile `APPLIED`.
- Client hat `propwash_example`, der Server nicht: Import meldet „auf diesem Server nicht aktiv“, Server lehnt mit
  `UNKNOWN_PARTS` ab, mit Ersatz gelingt es.
- Erwartung: `PROPWASH_JOINTEST_EXPECT='joined|share give ok|share missing_parts|share applied|share inactive|share substitute applied'`.

### 6.4 Unverträglichkeit und alte Versionen

- Rauchtest-Start Propwash 0.6.0 + JMP 1.1.1 (beide Loader, Client und Server): PASS im Altmodus, Hinweis genau einmal,
  nur ein Knopf „Sharecode“ in der Werkbank, PW3-Export einer JMP-Drohne byte-gleich zum Export mit JMP 1.2.0.
- Rauchtest-Start Propwash 0.6.0 + JMP 1.2.0 (beide Loader, Client und Server): PASS, Tabellen geladen.

### 6.5 Website (ohne Minecraft)

- `tools/test-configurator.mjs`: PW3-Vektoren und Umrechnungen bytegleich zu Java (dieselben Golden-Dateien), alle
  Status, Prüfsummen-Vektoren.
- `tools/test-jar-loader.mjs`: der Jar von JMP 1.2.0 ergibt denselben Katalog wie der Web-Export aus dem Spiel (jedes Teil,
  jedes Feld); Jar von Propwash ebenso; Grenzen (Zip-Bombe, zu große Textur, 20001 Einträge, kaputtes ZIP, Pfad mit `..`)
  ergeben eine Meldung statt eines Absturzes.
- `tools/test-directory.mjs`: Kurzkennungen eindeutig, Tabellen je Version nur angehängt, Abzug von Propwash ist mit dem
  Verzeichnis verträglich.
- Kopfloser Chrome (`tools/headless-chrome.mjs`): Umrechner mit einem echten Beitrag aus mehreren Codes, Konfigurator mit
  `#PW2…`-Link (Weiterleitung), Jar laden per Datei-Eingabe, Verzeichnis-Seite; Bildschirmfotos englisch und deutsch,
  Breite 390 und 1280 px.

### 6.6 Echte Codes aus Discord

Alle Codes aus dem Drohnen-Channel und den Hilfe-Threads werden lesend gesammelt (nur die Codes, lokal, nicht ins
Repository). Jeder läuft durch Mod-Umrechnung (JUnit mit lokaler Datei) und Website-Umrechnung: Ergebnis bytegleich,
kein Code mit Status ungleich `OK` ohne Erklärung.

### 6.7 Unabhängige Prüfer

Nach der Integration prüfen mindestens zwei unabhängige Durchgänge: Spezifikation gegen Code (Java und JavaScript), die
Bildschirmfotos gegen Abschnitt 5 (Texte, Umlaute, GUI 320×240) und die Testfälle aus diesem Abschnitt 6. Fix-Runden, bis nichts Mittleres
mehr offen ist.

---

## 7. Doku und Website

**Propwash-Repository:** `docs/sharecode/pw3.md` (normativ, mit Testvektoren und gemessenen Längen), `pw2.md` und
`pw1.md` aus JMP übernommen und als eingefroren markiert, `docs/sharecode/tables.md` (Anleitung für Add-ons: Datei,
Regeln, Prüfwerkzeug, Gradle, Aufnahme ins Verzeichnis), `docs/api/addon-guide.md` §13 neu („Sharecodes gehören
Propwash, Add-ons liefern eine Tabelle“), `docs/api/changes.md` (API 1.7.0), `docs/ARCHITECTURE.md` (Zeile `share`),
`docs/FOUNDATION.md` (Verträge), `ADDONS.md` (Verweis auf die Tabelle, zusammen mit dem Namensraum-Strang),
`CHANGELOG.md`.

**JMP-Repository:** README und README.de (Codes kommen jetzt von Propwash), `CHANGELOG.md` 1.2.0, `docs/design/sharecode*.md`
mit Verweis auf Propwash, Modrinth-Beschreibung.

**Website:** Changelog 0.6.0 und JMP 1.2.0, Doku-Seiten aus `docs/sharecode/` (Format, Anleitung für Add-ons), Umrechner
`/convert/`, Verzeichnis `addons/`, Konfigurator-Hilfe (Jar laden, fehlende Add-ons), FAQ-Eintrag „Mein alter Code?“,
Datenschutzseite (Jar wird nur lokal gelesen), Navigation.

---

## 8. Risiken

| Risiko | Gegenmittel |
|---|---|
| Plattform-Review: Propwash 0.6.0 live, JMP 1.2.0 noch in Prüfung | Altmodus: Propwash 0.6.0 läuft mit JMP 1.1.x; Propwash zuerst hochladen, JMP 1.2.0 nach dessen Freigabe auf der jeweiligen Plattform; Announcement erst, wenn beide freigegeben sind |
| Spieler auf 0.5.x können PW3 nicht lesen | Announcement bittet, den alten Code im Beitrag stehen zu lassen und den PW3-Code darunter zu setzen (Anhang A) |
| Abzug im Propwash-Jar braucht die fertige JMP-Tabelle | Reihenfolge im Release: JMP-Tabelle einfrieren → Abzug synchronisieren → Propwash final bauen; `DirectorySnapshotTest` schlägt bei Abweichung an |
| Port von `PartJson` in JavaScript weicht ab | Golden-Vergleich für jedes Teil von Propwash und JMP; Abweichung ergibt im Browser eine Meldung statt falscher Werte |
| Codes werden länger (JMP-Teile etwa doppelt so lang wie PW2) | gemessen und dokumentiert; weiter weit unter 256 Zeichen |
| Phishing über Links im Code | nur Modrinth/CurseForge klickbar, Bestätigungsdialog, Rest als Text mit Warnung |
| Bösartige Jars im Browser | nur JSON und Bilder lesen, Grenzen aus 3.16, nie Code ausführen |
| Add-on-Autoren pflegen keine Tabelle | Text-Rückfall (länger, aber gültig), Log-Hinweis, Anleitung |
| Zwei Add-ons beanspruchen eine Kurzkennung | der Abzug entscheidet, sonst voller Namensraum |
| Fremde Assets im Verzeichnis | Aufnahme nur mit Lizenz-Prüfung durch lardtom |
| Alte Leser in JMP-Port verlieren Bitgleichheit | Golden-Dateien aus JMP 1.1.1 vor dem Entfernen erzeugen und einchecken |
| `/jmp code` in Befehlsblöcken auf Servern | Hinweis in 1.2.0, Changelog und Announcement |
| Export-Prüfung des öffentlichen Repositorys | dieses Dokument enthält nur Wortlaut der Roadmap-Notizen und keine privaten Nachrichten; trotzdem vor jedem Stufenabschluss und vor dem Release mit `tools/public_export.py --check-only` prüfen |
| Hohe KV auf 1S sprengt Grenzen des Motormodells | Prüfstand im JMP-Test (Drehzahl, Strom, Temperatur), sonst KV-Wert anpassen |

---

## 9. Entscheidungen

| Was | Warum | Verworfen |
|---|---|---|
| Präfix `PW3-` mit Bindestrich, Leser nimmt auch `PW3`, `pw3-` und den nackten Rumpf | Vorgabe vom 14.10.; der Doppelklick-Nachteil (nur der Teil nach dem Strich wird markiert) wird durch das Lesen des Rumpfs aufgefangen | `PW3` ohne Strich wie bei PW2 (weicht von der Vorgabe ab) |
| Base62, CRC-16-Rahmen, Lack/Tuning/OSD/Name/Erweiterungen bitgleich zu PW2 | bewährter, getesteter Code in Java und JavaScript; nur der Teile-Block ändert sich | neues Grundformat |
| Block Namensräume, Teile verweisen per Index darauf | jeder Namensraum steht einmal im Code (Vorgabe), je Teil 0–2 Bit statt Text | Namensraum je Teil; eine globale Tabelle (Vorgabe: pro Add-on) |
| Tabelle als JSON-Datei im Jar unter `data/<ns>/propwash_share/`, direkt aus dem Jar gelesen | Website und Werkzeug können sie lesen, Server und Client gleich, Datenpakete können sie nicht ändern | Java-Konstanten (im Browser nicht lesbar); Laden über Datenpakete |
| Kurzkennung als Zahl, 1 = propwash, 2 = justmoreparts, Abzug des Verzeichnisses im Jar | kurz, ohne Doppelte, offline auflösbar, Namen und Ersatzteile offizieller Add-ons auch ohne das Add-on | Hash als Kennung (Kollisionen); Kennung nur online (Spiel ohne Netz) |
| Prüfsumme 10 Bit über die benutzten Einträge | neuere, hinten erweiterte Tabellen ergeben keinen Fehlalarm; 1:1024 reicht neben dem Werkzeug | über die ganze Tabelle (Fehlalarm nach jedem Update); 16 Bit (ein Zeichen mehr je Add-on); 8 Bit (zu schwach gegen Forks) |
| Mindestversion = größte `since` der benutzten Einträge | „braucht X ab V“ nennt die wirklich nötige Version | installierte Version des Schreibers (zu streng) |
| Version immer, Name und Link optional; standardmäßig nur für Add-ons ohne Verzeichniseintrag | Version kostet wenige Bit; Verzeichnis-Add-ons sind ohnehin bekannt | Info immer (Codes deutlich länger); nie (Fremd-Add-ons blieben namenlos) |
| Link als Modrinth-/CurseForge-Slug, freie Adresse nur als Text | kurz und sicher gegen Malware-Links, passt zur Plattform-Vorgabe (Modrinth und CurseForge) | jede Adresse klickbar |
| `deprecated` + `replaced_by` in der Tabelle | ein Ort für alle Regeln, ersetzt `PartAliases` | eigene Alias-Datei |
| Teile ohne Tabelle oder Eintrag als Text | jedes Add-on funktioniert sofort, nur der Code wird länger | Kodieren verweigern |
| Prüfwerkzeug als Klasse im API-Paket (MIT) mit `main` und JUnit-Aufruf | läuft in jedem Add-on-Build ohne Zusatz-Werkzeug, Lizenz passt für Add-ons | Gradle-Plugin (Veröffentlichung); Python-Skript (Python nicht überall) |
| Prüfung auch ohne Vergleichsdatei über nicht fallende `since` | fängt eingeschobene Einträge auch beim ersten Release | nur mit Vergleichsdatei |
| JMP unter 1.2.0 läuft mit Propwash 0.6.0 im Altmodus (JMP-Knopf nicht angemeldet, PW3 über den Abzug) | ein Update nur von Propwash darf das Spiel nicht unstartbar machen; trotzdem nur eine Sharecode-Oberfläche | Unverträglichkeit in den Metadaten (Spiel startet nach einem Einzel-Update nicht) |
| Ersatz zuerst über `fallback`, dann über die Propwash-Presets | deterministisch, ergibt stimmige Teile-Kombinationen | Raten aus dem Id-Namen; kein Ersatz |
| Ersatz auch in der Werkbank im Überleben | gleicher Weg wie Kreativ, Spieler ohne Add-on können die Drohne nachbauen | nur Kreativ |
| Mod liest PW1/PW2 nur, keine alten Kodierer | weniger Code; Golden-Dateien sichern die Leser | Kodierer mitportieren |
| Umrechnung nur mit eingefrorenen Daten (Propwash-Tabelle, JMP aus dem Abzug) | Mod und Website rechnen bytegleich, Ergebnis hängt nicht vom installierten Stand ab | mit installierten Tabellen umrechnen |
| Fremde PW2-Text-Ids als voller Namensraum ohne Tabelle | Vorgabe; deterministisch ohne Verzeichnis | Nummern aus dem Verzeichnis einsetzen |
| `pw2.unknown`/`pw1.unknown` nicht umrechenbar | kommen nur in handgebauten Codes vor, Raten wäre falsch | Platzhalter-Namensraum |
| ~~Werte der Kreativ-Drohne nicht in 0.6.0~~ – Nachtrag 28.10.: in 0.6.0 als Erweiterung 0 (Revision 3.1), PWD1 gelesen und umrechenbar | Owner 28.10. (Befund PWD3-1): Bauplan, Lack und Tune sind PW3, der Editor soll es auch sein; API bleibt, Netz nur ein internes Paket | weiter `PWD1-` im Editor |
| kleine API `api.share.ShareCodes` | Server-Mod und Bot brauchen Codes ohne Interna | keine API |
| `/jmp code` in JMP 1.2.0 nur als Hinweis | Befehlsblöcke laufen nicht stumm ins Leere | still entfernen; Weiterleitung (Reihenfolge der Registrierung zwischen Mods unsicher) |
| keine Netzwerk-Pakete (Nachtrag 28.10.: eines für „Drohne erzeugen“ mit Kreativ-Werten) | vorhandene Import-Wege reichen, nur die Kreativ-Werte passen nicht in `ImportWire` | eigenes Share-Paket für alles |
| Web-Exporte des Verzeichnisses aus dem Jar mit derselben Logik wie im Browser | eine Rechnung, kein Spielstart je Add-on | Export-Befehl im Spiel für jedes Add-on |
| Web-Export als Befehl in der Basis (überholt die frühere Fassung „bleibt in JMP“, Übersicht `README.md` K4) | jedes Add-on kann ohne eigenen Code exportieren; ein Orakel statt zweier | Exporter im JMP-`devtest` behalten |
| Jar nur im Speicher des Tabs | „nichts hochladen“ und nichts dauerhaft speichern | IndexedDB-Cache |
| Kataloge der Website je Namensraum | Verzeichnis-Add-ons lassen sich einzeln nachladen | ein Gesamtkatalog |
| Neue Whoop-Motoren: 0702 mit etwa 30000 und 33000 KV, 0802 mit etwa 30000 KV, genaue Werte nach Datenbeleg | Wunsch aus Discord, ohne Markennamen | feste Werte ohne Beleg |
| Zitate = Wortlaut der Roadmap-Notizen und Pläne, Discord sinngemäß | Regel des öffentlichen Repositorys: keine wörtlichen privaten Nachrichten | Discord-Antworten wörtlich |
| Announcement auf Englisch als eigener kurzer Beitrag nach dem Release-Beitrag | Sprache des Servers; lässt sich im Drohnen-Channel anpinnen | im Release-Beitrag verstecken |
| Announcement: alten Code stehen lassen, PW3-Code darunter | Spieler auf 0.5.x können PW3 nicht lesen; so bleiben Beiträge für alle Versionen nutzbar, ohne PW2-Ausgabe auf der Website | Beiträge ersetzen; PW2-Ausgabe auf der Website |

---

## 10. Arbeitspakete

Reihenfolge: A1 (Spezifikation) → A2, B, C, D1, D2 parallel → Integration → E. Gemeinsam genutzte Dateien ändert nur
der Integrator; die Stränge liefern dafür je einen kleinen eigenen Commit.

| Strang | Repo / Branch | Besitzt | Abhängig von | MC-Test | Größe |
|---|---|---|---|---|---|
| **A1 pw3-spec** | propwash / `feat/0.6.0-pw3-codec` | `docs/sharecode/pw3.md`, `pw2.md`, `pw1.md`, `tables.md`; Golden-Dateien aus JMP 1.1.1 (`common/src/test/resources/sharecode/pw1-vectors.json`, `pw2-vectors.json`) | — | 0 min | S |
| **A2 pw3-codec** | propwash / `feat/0.6.0-pw3-codec` | `common/src/main/java/de/lardtom/propwash/share/**` ohne `client`, ohne `ShareCommands`/`ShareServer`; `api/share/**`; `data/propwash/propwash_share/**`; `common/src/test/java/de/lardtom/propwash/share/**`, `common/src/test/resources/sharecode/**`; `tools/sharecode/**` | A1 | 0 min | L |
| **B pw3-ui** | propwash / `feat/0.6.0-pw3-ui` | `share/client/**`, `share/ShareCommands`, `share/ShareServer`, `common/src/lang/share/**`, Sprites `assets/propwash/textures/gui/sprites/share/**`, `common/src/devtest/java/de/lardtom/propwash/share/**`, `dev/ShareJoinCheck`, Tabelle des Beispiel-Add-ons `common/src/devtest/resources/data/propwash_example/propwash_share/table.json`, Bildschirmfotos `docs/screenshots/share/**` | A2 (Schnittstellen nach A1 sofort, Stubs erlaubt) | etwa 45 min (Selbsttest beide Loader 2 × 8, Bildschirmfotos 12, dedizierter Server 12, Rauchtests 5) | L |
| **C jmp-1.2** | propwash-justmoreparts / `feat/1.2.0-jmp-pw3-table` | alles in JMP: Entfernen von `share/**`, `JmpCommands` (Hinweis), Tabelle, `sharecode-baseline.json`, neue Motoren (JSON, Modelle, Texturen, Sprache, Rezepte, Survival-Tabellen, Prüfstände), `export/**` (Abschnitt `sharecode`), `build.gradle`, `gradle.properties`, Doku, CHANGELOG | A2 (Werkzeug, API), für Selbsttest B | etwa 20 min (Selbsttest und Rauchtests beider Loader) | M |
| **D1 site-pw3** | propwash-site / `site/0.6.0-site-configurator` | `assets/js/configurator/sharecode3.js`, `convert.js`, `sharecode.js` (Weiche), Import-Teil in `index.js`/`ui.js`, Seite `/convert/` (geht im Strang `site-configurator` auf, Übersicht K6), `tools/test-configurator.mjs` (PW3-Teil), Fixtures der Vektoren, i18n-Schlüssel `share.*` | A2 (Golden-Dateien) | 0 min | M |
| **D2 site-addons** | propwash-site / `site/0.6.0-site-addons` | `jar.js`, `partjson.js`, `addon-catalog.js`, `catalog-build.js`, `directory.js`, `data.js`, Seite `addons/`, `assets/data/addons/**`, `tools/build-configurator-data.mjs`, `tools/build-configurator-models.mjs`, `tools/test-jar-loader.mjs`, `tools/test-directory.mjs`, Datenschutz-Abschnitt, i18n-Schlüssel `addons.*` | A2 (Tabellen-Format), C (Jar und Web-Export von JMP 1.2.0) | 0 min | L |
| **E pw3-texte** | propwash-site und Release-Unterlagen | Discord-Announcement (Anhang A), Changelog-Texte Website, FAQ-Eintrag | B, C, D1 | 0 min | S |

**Gemeinsame Dateien (nur über den Integrator):** `PropwashCommon.java` (`ShareModule.init()`), `PropwashClient`
(`ShareClient.init()`), `api/PropwashApi.java` (`shareCodes()`), Versionskonstanten der API (Erhöhung auf 1.7.0 gehört
dem API-Strang), Altmodus-Erkennung für JMP unter 1.2.0 (Teil von B, keine Einträge in `fabric.mod.json` oder
`neoforge.mods.toml`),
`docs/ARCHITECTURE.md`, `docs/FOUNDATION.md`, `docs/api/addon-guide.md`, `docs/api/changes.md`, `ADDONS.md`,
`CHANGELOG.md`, Navigation und `i18n.js` der Website.

**Release-Reihenfolge:** JMP-Tabelle einfrieren (`freeze`) → Abzug in Propwash synchronisieren → Propwash final →
JMP final gegen das finale Propwash → Web-Exporte und Verzeichnis → Upload Propwash 0.6.0 → Upload JMP 1.2.0, sobald
Propwash auf der jeweiligen Plattform freigegeben ist → nach Freigabe beider: Website live, Announcement.

---

## Anhang A: Discord-Announcement (Entwurf, Englisch)

```
@everyone
# Share codes are now PW3 – please convert yours
With Propwash 0.6.0, share codes work with every add-on, not just Just More Parts. That needs a new format: PW3.

## What changes
- Propwash itself now reads and writes share codes: *Share code* button in the workbench, the *Share code* tab, `/propwash code`. Just More Parts 1.2.0 no longer has its own.
- New codes start with `PW3-`. The code names every add-on it uses, with the version it needs.
- Missing an add-on? The import tells you which one, which version and where to get it (Modrinth or CurseForge), shows a shopping list in survival and can use stand-in parts.

## Your old codes
- `PW1-…` and `PW2…` codes keep working in the game and on the website – forever.
- From now on only PW3 is written. Please add a PW3 code to the builds you posted in <#1554530913365397614>:
  1. Open https://propwashfpv.com/convert/
  2. Paste your whole post – every old code in it is converted right in your browser, nothing is uploaded
  3. Edit your post and add the new `PW3-` code **below** your old one. Keep the old code: players who are still on
     Propwash 0.5 can only read that one.
- In the game, paste an old code and click *Copy as PW3*, or use `/propwash code convert <code>`.

## Add-on makers
Your parts get short numbers from a small table in your jar, and Propwash checks it when you build, so codes never break. On the website, anyone can open your add-on's jar locally in the configurator. Want a short id and a spot in the official add-on directory? Ask us here on Discord.

Just More Parts 1.2.0 also brings new 1S whoop motors with 30000+ KV.
```

Hinweis: Senden erst, wenn Propwash 0.6.0 und JMP 1.2.0 auf Modrinth und CurseForge freigegeben sind und
`propwashfpv.com/convert/` live ist; danach im Drohnen-Channel anpinnen.

## Anhang B: Testvektoren, die Strang A mindestens anlegt

| Vektor | Inhalt |
|---|---|
| P1 | nur Teile, Propwash-Preset Freestyle |
| P2 | nur Teile, JMP-Preset mit Zubehör vorne |
| P3 | Teile, Lack, BF-Tuning, Name, OSD (alle Ebenen) |
| P4 | Fremd-Add-on ohne Tabelle (Text-Namensraum und Text-Pfade) mit Info (Name, Modrinth-Slug) |
| P5 | Fremd-Add-on mit Tabelle ohne Kurzkennung, ein Teil als `v = 0` |
| P6 | unbekannte Nummer hinter dem Tabellenende (Neu-Schreiben übernimmt den Kopf) |
| P7 | gestrichenes Teil mit `replaced_by` |
| P8 | Linkart 3 (freie Adresse) |
| P9 | Versionszusatz (`1.3.0-beta.1`) |
| P10 | P1 mit einem vertauschten Zeichen (`CHECKSUM`) |
| P11 | P2 ohne Präfix (nackter Rumpf) |
| U1–U27 | alle 27 Presets aus PW2-Golden umgerechnet, je in 5 Ebenen-Varianten |
