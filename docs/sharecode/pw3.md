# Sharecode PW3 — Spezifikation

| | |
|---|---|
| Format | **PW3** (Präfix `PW3-`) |
| Revision | 3.1 vom 2026-10-28 (3.0 vom 2026-10-18 plus Ebene Kreativ-Werte, §3.6) |
| Gilt ab | Propwash FPV 0.6.0 (API 1.7.0) mit und ohne Add-ons; Web-Konfigurator auf propwashfpv.com |
| Referenz | Java: `de.lardtom.propwash.share.Pw3Codec` mit `Pw3Params`, `LayerCodec`, `IdText`, `BitWriter`, `BitReader`, `Base62`, `TableChecksum`, `ShareTables`; JavaScript (Website): Port von `Pw3Codec`, bytegleich |
| Vorgänger | PW2 Revision 2.0 (`pw2.md`) und PW1 Revision 1.2 (`pw1.md`), beide eingefroren und weiter gelesen |
| Teile-Tabellen | `tables.md` (Anleitung für Add-ons) |

Ein Sharecode beschreibt eine Drohne vollständig, egal aus welchen Add-ons ihre Teile kommen: Teile, optional Lack,
Flug-Tuning samt Kamerawinkel, OSD-Layout, Name und die Werte einer Kreativ-Drohne aus dem Drohnen-Editor. Der Codec gehört Propwash; jedes Add-on liefert nur eine Teile-Tabelle
in seinem Jar. Ein Code nennt jeden Namensraum (= Mod-Id) genau einmal und verweist danach je Teil auf eine Nummer aus der
Tabelle dieses Namensraums. Je Namensraum trägt er eine kleine Prüfsumme der benutzten Tabellen-Einträge und die nötige
Mindestversion, optional Name und Link des Add-ons. Wer nur diese Datei und `pw2.md` liest, kann Codes bitgenau lesen und
schreiben.

Ebenen, Übernehmen, Befehle, Grenzen und Sicherheit sind wie in PW1 (`pw1.md` §1, §8, §9, §10, §12): Ein PW3-Code
dekodiert zu denselben Objekten (`ShareContent`, `ShareDecoded`) wie ein PW1- oder PW2-Code. Die Ebenen Lack, Tuning, OSD,
Name und Erweiterungen sind **bitgleich** zu PW2 kodiert (`pw2.md` §4.4–§4.6, §4.2); neu ist nur der Block der Teile.

---

## 1. Textform

```
PW3-<base62>
```

- Präfix `PW3-`. Danach die Base62-Zeichen wie in PW2 (`pw2.md` §2): Alphabet
  `0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz`, Groß- und Kleinschreibung zählt, die Bytes des Rahmens
  als eine große vorzeichenlose Zahl (big-endian) mit fester Länge `z(n)`.
- URL der Website: `configurator/#PW3-…` (der Bindestrich ist URL-sicher).

**Einlesen (tolerant):**

1. Leerraum entfernen (`Character.isWhitespace` oder `isSpaceChar`, auch geschützte Leerzeichen). Eingabe über 65536
   Zeichen vor dem Entfernen oder über 4000 Zeichen danach → `TOO_LONG`; nichts übrig → `EMPTY`.
2. Beginnt der Text ohne Rücksicht auf Groß- und Kleinschreibung mit `PW3`, gilt der Rest als Rumpf: alle `-` werden
   entfernt, die Schreibweise bleibt. `PW3-…`, `PW3…`, `pw3-…` und in Gruppen geteilte Codes sind also gleich.
3. `PW2…` liest der eingefrorene PW2-Leser (`pw2.md`), `PW1…` der PW1-Leser (`pw1.md`).
4. `PWT1…` (Tuning-Voreinstellung) ergibt `OTHER_CODE`; das Feld `format` des Ergebnisses nennt `PWT1`, damit die
   Oberfläche sagt, wohin der Code gehört. `PWD1…` (alter Code des Drohnen-Editors bis 0.5.x) liest seit Revision 3.1 der
   PWD1-Leser (§7.1): Format `PWD1`, Ebenen Teile und Kreativ-Werte.
5. `PW` mit einer Zahl ab 4 (`PW4…` bis `PW999…`) → `UNSUPPORTED_VERSION` („Code aus einer neueren Version“). Weil die
   Präfixe vorher geprüft werden, gehen `PW1…` und `PW2…` auch mit weiteren Ziffern an die alten Leser.
6. **Nackter Rumpf:** Besteht der ganze Text nur aus Base62-Zeichen, wird er als PW3-Rumpf gelesen. Ist das Ergebnis nicht
   `OK`, lautet der Status `INVALID_PREFIX`. Grund: Ein Doppelklick markiert in Discord und im Browser nur den Teil nach dem
   Bindestrich. Scheitert ein Text mit Präfix, der zugleich nur aus Base62-Zeichen besteht, wird er zusätzlich als nackter
   Rumpf versucht.
7. Sonst `INVALID_PREFIX`. Zeichen außerhalb des Alphabets → `BAD_CHARACTER`; gibt es zur Zeichenzahl keine Bytezahl →
   `TRUNCATED`; ist die Zahl größer als 256ⁿ − 1 → `CORRUPT`.

---

## 2. Rahmen

Wie PW2 (`pw2.md` §3): Nutzdaten (Bitstrom, mit 0-Bits auf ganze Bytes aufgefüllt), dahinter 2 Bytes mit den unteren 16
Bit der CRC-32 (IEEE) über die Nutzdaten, big-endian. Rahmen über 2500 Bytes → `TOO_LARGE`, unter 3 Bytes → `TRUNCATED`,
Prüfsumme falsch → `CHECKSUM`. Es gibt kein Versionsbyte: Spätere Revisionen bleiben über Erweiterungs-Ebenen lesbar, ein
inkompatibles Format bekäme `PW4-`.

---

## 3. Bitstrom

Bits werden höchstwertiges Bit zuerst geschrieben. Nach dem letzten Feld folgen höchstens 7 Füllbits, die 0 sein müssen;
mehr Bits oder ein gesetztes Füllbit → `CORRUPT`. Liest ein Feld über das Ende → `TRUNCATED`.

### 3.1 Grundtypen

`bit`, `u(n)`, `eg(k)`, `zz`, `idx(n)` und `text` wie PW2 (`pw2.md` §4.1, auch die Text-Modi Tabelle, ASCII und UTF-8).
Neu:

| Typ | Kodierung |
|---|---|
| `idtext` | `eg(3)` Länge − 1, dann je Zeichen `u(6)` als Index in `abcdefghijklmnopqrstuvwxyz0123456789_.-/` (40 Zeichen; Index ≥ 40 → `CORRUPT`). Passt die Länge nicht in die restlichen Bits → `TRUNCATED`, ist sie größer als die Grenze des Feldes → `CORRUPT` |
| `version` | `eg(0)` major, `eg(0)` minor, `eg(0)` patch (je höchstens 2³¹ − 1), `bit` hatZusatz, `[text zusatz]` (1–32 Zeichen aus `0-9 A-Z a-z . -`) |

### 3.2 Aufbau

```
bit hatLack  bit hatTuning  bit hatOsd  bit hatName  bit hatErweiterungen
NAMENSRÄUME                         (immer)
TEILE                               (immer)
LACK      wenn hatLack              (bitgleich pw2.md §4.4)
TUNING    wenn hatTuning            (bitgleich pw2.md §4.5, Schlüssel-Tabelle §6.3)
OSD       wenn hatOsd               (bitgleich pw2.md §4.6, gleiches Wörterbuch)
text NAME wenn hatName              (nach strip() 1–48 Zeichen, sonst CORRUPT)
ERWEITERUNGEN wenn hatErweiterungen (bitgleich pw2.md §4.2)
```

Die Ebenen-Liste des Ergebnisses ist PARTS, dann die gesetzten Ebenen in dieser Reihenfolge, zuletzt `CREATIVE`, wenn die
Erweiterung `e = 0` vorkommt. Revision 3.0 schrieb keine Erweiterung; seit Revision 3.1 trägt die Erweiterungs-Ebene
`e = 0` (Ebenen-Bit 5, Ebene `CREATIVE`) die Werte der Kreativ-Drohne (§3.6). Der Kodierer setzt `hatErweiterungen` nur
mit Kreativ-Werten und schreibt dann genau einen Eintrag; ohne Kreativ-Werte ist jeder Code bytegleich zu 3.0.

### 3.3 NAMENSRÄUME

```
eg(0) n − 1                         (1 ≤ n ≤ 16, sonst CORRUPT)
je Namensraum i:
    eg(1) s                          s = 0: idtext namensraum folgt; s ≥ 1: Kurzkennung (§4)
    [idtext namensraum]              nur bei s = 0; 1–64 Zeichen aus [a-z0-9_.-]
    bit hatTabelle                   1: Teile dieses Namensraums als Tabellen-Nummer
    [u(10) prüfsumme]                nur bei hatTabelle (§5.2)
    bit hatVersion
    [version]                        Mindestversion (§5.3)
    bit hatInfo
    [text name]                      1–48 Zeichen, nicht leer
    [u(2) linkart, link]             0 kein Link · 1 Modrinth: idtext slug · 2 CurseForge: idtext slug
                                     · 3 https-Adresse: text ohne „https://“ (1–200 Zeichen, druckbares ASCII 0x21–0x7E)
```

- Der Kodierer schreibt die Namensräume in der Reihenfolge ihres ersten Vorkommens in TEILE (Rahmen, Stack, Motor, Prop,
  Video, Akku, dann Zubehör nach Platz). Der Leser verlangt die Reihenfolge nicht.
- Text-Namensraum (`s = 0`) `propwash` → `CORRUPT` (Propwash hat immer die Kurzkennung 1). Reserviert und als Text
  verboten (`CORRUPT`): alles, was mit `pw3.` beginnt (darunter `pw3.unknown`), `pw2.unknown` und `pw1.unknown.*`.
- Zwei Köpfe, die zum selben Namensraum führen (auch Kurzkennung und Text), → `CORRUPT`.
- Ein Namensraum, auf den kein Teil verweist, wird gelesen und in `skipped` als `namespace_unused:<i>` vermerkt.
- Slugs (`linkart` 1 und 2) bestehen aus 1–64 Zeichen `[a-z0-9_.-]`, sonst `CORRUPT`. Eine unbekannte Linkart gibt es
  nicht (2 Bit, alle vier Werte belegt).

### 3.4 TEILE

```
ref(FRAME) ref(STACK) ref(MOTOR) ref(PROP) ref(VIDEO) ref(BATTERY)
eg(0) zubehörAnzahl (≤ 255), je Eintrag: eg(0) platz, ref(ACCESSORY)
```

Plätze wie PW2: 0 `top`, 1 `bottom`, 2 `front`, nur anhängen. Unbekannter Platz → gelesen und verworfen
(`accessory_slot:<n>`), doppelt → der spätere gewinnt (`accessory_slot_duplicate:<n>`).

`ref(art)`:

```
idx(n) namensraum                    Index in die Liste aus NAMENSRÄUME
hatTabelle = 1:  eg(3) v             v = 0: idtext pfad folgt (Teil steht nicht in der Tabelle)
                                     v ≥ 1: Eintrag v − 1 der Tabelle dieser Art im Namensraum
hatTabelle = 0:  idtext pfad
```

Pfade: 1–128 Zeichen aus `[a-z0-9_.-/]`, sonst `CORRUPT`. Die Art ist immer die **Feld-Art** (das Rahmen-Feld liest die
Rahmen-Tabelle). Der Kodierer schreibt eine Nummer, wenn die Id in der Tabelle ihrer Feld-Art und ihres Namensraums steht,
sonst `v = 0` mit Pfad. Ein Teil im falschen Feld meldet der Leser als `wrongKind` wie bisher.

### 3.5 Unbekannte Einträge

Liegt `v` hinter dem Ende der Tabelle des Lesers, oder hat der Leser für diesen Namensraum gar keine Tabelle, heißt das Teil
intern

```
pw3.unknown:<namensraum>/<art>/<v>
```

(Anzeige `?<art>.<v>` mit dem Namen des Add-ons), gilt als unbekannt (`unknownParts`) und `skipped` bekommt einmal
`part_index:<namensraum>/<art>.<v>`. Kennt der Leser die Kurzkennung `s` nicht, steht `pw3.short.<s>` an Stelle des
Namensraums, und auch Teile mit Pfad heißen dann `pw3.unknown:pw3.short.<s>/<art>/0/<pfad>`.

**Neu schreiben mit unbekannten Einträgen:** Enthält der Inhalt `pw3.unknown`-Teile eines Namensraums, übernimmt der
Kodierer dessen Kopf (Kurzkennung oder Text, Prüfsumme, Version, Info) unverändert aus dem gelesenen Code
(`ShareDecoded#carry`) und schreibt dieselben Nummern, solange die Teile dieses Namensraums an denselben Plätzen mit
denselben Werten stehen. Sonst lehnt er mit `UNKNOWN_ENTRIES` ab („enthält Teile aus einer neueren Version von X“). Teile
`pw2.unknown:…` und Namensräume `pw1.unknown.N` lassen sich nicht als PW3 schreiben.

### 3.6 Kreativ-Werte (Erweiterung `e = 0`, seit 3.1)

Im Block ERWEITERUNGEN (`pw2.md` §4.2: `eg(0) anzahl`, je Eintrag `eg(0) e`, `eg(6) bitlänge`, dann die Bits) steht bei
`e = 0`:

```
u(5) basis                 Bit 4 … 0 (höchstwertiges zuerst) = Basis-Teil für FRAME, STACK, MOTOR, PROP, BATTERY
je gesetztem Bit:          eg(0) h
                           h = 0: text schlüssel (1–128 Zeichen, wie im Editor: Pfad für propwash, sonst namensraum:pfad)
                           h ≥ 1: idtext pfad (1–128 Zeichen) im Namensraum des Kopfs h − 1 aus NAMENSRÄUME
eg(2) n                    Werte mit Tabellen-Schlüssel (n ≤ 96)
je Wert, Index aufsteigend: eg(1) abstand (Index − vorheriger Index − 1, Index ab 1), wert
eg(0) m                    Werte mit Text-Schlüssel (n + m ≤ 96)
je Wert, Schlüssel sortiert: text schlüssel (1–128 Zeichen), wert
wert:                      bit roh
                           roh = 0: eg(0) d (0–15), eg(4) zz → Zahl = zz⁻¹ · 10^−d, als IEEE-754-double korrekt gerundet
                                    (wie Double.parseDouble bzw. parseFloat von „<m>e-<d>“); |zz| < 2^40
                           roh = 1: u(64) IEEE-754-double, endlich
```

- `zz` ist Zickzack wie beim Tuning (`m ≥ 0 → 2m`, sonst `−2m − 1`). Der Kodierer schreibt die kürzeste Dezimalform
  (`Double.toString`, ohne Nullen am Ende), wenn sie in 15 Nachkommastellen und 40 Bit passt und genau dieselbe Zahl ergibt,
  sonst `roh = 1`. Damit kommt jede Zahl bitgleich zurück; −0,0 wird 0,0.
- **Schlüssel-Tabelle** (Index ab 1, nur anhängen): die Schlüssel von `creative.CreativeParam` in dieser Reihenfolge,
  `Pw3Params.CREATIVE_KEYS` und `spec.json` → `creative.keys`: `frame.mass_g`, `motor.mass_g`, `prop.mass_g`,
  `battery.mass_g`, `stack.mass_g`, `video.mass_g`, `inertia.roll`, `inertia.pitch`, `inertia.yaw`, `frame.wheelbase_mm`,
  zwölf Motorpositionen `arm.<front_left|front_right|rear_left|rear_right>.<forward|right|up>`, `prop.diameter_mm`,
  `prop.pitch_in`, `prop.blades`, `prop.ct`, `prop.cp`, `motor.kv`, `motor.resistance_mohm`, `motor.max_current_a`,
  `battery.cells`, `battery.capacity_mah`, `battery.resistance_mohm`, `battery.c_rating`, `stack.esc_current_a`,
  `frame.frontal_area_cm2`, `frame.drag_cd`, `frame.side_area_cm2`, `frame.top_area_cm2`, `frame.ducts`,
  `frame.impact_tolerance`, `video.fov_deg`, `camera.forward`, `camera.right`, `camera.up` (45 Einträge). Andere Schlüssel
  gehen als Text, damit unbekannte Werte den Code überleben wie bei PWD1.
- Werte stehen in Anzeige-Einheiten wie im Editor (g, mm, mΩ, cm², g·cm²). Der Code begrenzt nichts; das tut der Empfänger
  beim Übernehmen (`CreativeLimits.sanitize`, Server und Editor).
- Basis-Teile im Namensraum eines Kopfs kosten nur die Kopfnummer und den Pfad; Basis-Teile anderer Add-ons und alles, was
  kein Pfad im `idtext`-Alphabet ist, gehen als Text. Basis-Teile zählen nicht zu TEILE: Sie ändern weder Prüfsummen noch
  unbekannte Teile oder die Mindestversion.
- Lesen: Ein Index hinter der Tabelle wird verworfen (`skipped` einmal `creative_index:<n>`), ein Text-Schlüssel doppelt zu
  einem früheren gewinnt (`creative_duplicate:<schlüssel>`). Bits hinter den bekannten Feldern der Ebene bis zur
  `bitlänge` werden überlesen (spätere Revisionen dürfen dort anhängen). Braucht die Ebene mehr Bits als `bitlänge`, steht
  `e = 0` zweimal im Code oder passt ein Feld nicht → `CORRUPT`. Andere `e` werden wie bisher übersprungen (`layer:<bit>`).
- Ältere Leser (Revision 3.0, Propwash 0.6.0 vor dieser Ergänzung) überspringen die Ebene mit `layer:5` und lesen den Rest
  unverändert.

---

## 4. Kurzkennungen

- Kurzkennung = ganze Zahl ≥ 1, vergeben vom offiziellen Add-on-Verzeichnis auf propwashfpv.com, nie wiederverwendet.
  Fest: **1 = `propwash`, 2 = `justmoreparts`**. `eg(1)` kostet für 1 zwei Bit, für 2–5 vier Bit.
- **Verzeichnis-Abzug** `data/propwash/propwash_share/directory.json` im Propwash-Jar: je offiziellem Add-on Namensraum,
  Kurzkennung, Name (`en_us`, `de_de`), Modrinth- und CurseForge-Slug, neueste bekannte Version und seine Tabelle samt
  Teilenamen (`en_us`, `de_de`) und `fallback`. Erzeugt mit `tools/sharecode/sync_directory.py` (`tables.md` §6).
- **Auflösen beim Lesen:** (1) Abzug; (2) installierte Tabellen mit `short_id`, die der Abzug nicht kennt und die genau ein
  Add-on beansprucht. Widerspricht eine installierte Tabelle dem Abzug, gilt der Abzug (Log-Warnung). Unbekannte
  Kurzkennung → Bericht „Add-on Nr. s aus dem offiziellen Verzeichnis (neuer als diese Propwash-Version)“ mit Verweis auf
  `https://propwashfpv.com/addons/`.
- **Schreiben:** Kurzkennung nur, wenn sie nach denselben Regeln eindeutig ist, sonst der volle Namensraum (`s = 0`).

---

## 5. Tabellen, Prüfsumme, Mindestversion, Info

### 5.1 Tabelle zum Lesen und Schreiben

Je Namensraum gilt die installierte Tabelle `data/<namensraum>/propwash_share/table.json` aus dem Jar der Mod, deren Mod-Id
der Namensraum ist (Format und Regeln in `tables.md`). Gibt es keine, gilt die Tabelle aus dem Verzeichnis-Abzug. So
schreibt Propwash 0.6.0 auch ganz ohne JMP denselben Text wie mit JMP 1.2.0. Ohne beide Tabellen gehen die Teile als Text (`hatTabelle = 0`).

### 5.2 Prüfsumme je Namensraum

`u(10)` = die unteren 10 Bit der CRC-32 (IEEE) über die UTF-8-Zeilen

```
<art>:<v>:<pfad>\n
```

aller im Code benutzten Tabellen-Einträge dieses Namensraums (`v ≥ 1`, `pfad` ist die Id des Eintrags ohne Namensraum),
sortiert nach Art (`frame`, `stack`, `motor`, `prop`, `video`, `battery`, `accessory`) und dann nach `v`, ohne Doppelte.
Ohne benutzte Einträge ist der Wert 0 (CRC-32 des leeren Textes). Ein Leser mit einer neueren, hinten erweiterten Tabelle
kommt auf denselben Wert; ein umnummeriertes Teil oder ein fremdes Add-on unter gleichem Namensraum fällt auf.
Vektoren: `common/src/test/resources/sharecode/tablechecksum-vectors.json`.

**Ergebnis je Namensraum** (`NamespaceReport.Result`):

| Ergebnis | Bedeutung | Wirkung |
|---|---|---|
| `MATCH` | Prüfsumme stimmt | – |
| `MISMATCH` | „Code passt nicht zur installierten Version von X“ | Teile werden gezeigt, die Ebene Teile ist gesperrt, bis der Spieler „Trotzdem übernehmen“ bestätigt; Lack, Tuning, OSD und Name gehen immer |
| `NEWER` | eine Nummer liegt hinter dem Tabellenende oder der Leser hat keine Tabelle: „braucht X ab V“ | Teile unbekannt, Ersatz möglich |
| `MISSING` | Add-on fehlt (oder Kurzkennung unbekannt) | Teile unbekannt, Hinweis mit Name, Version und Link, Ersatz möglich |
| `NO_TABLE` | Teile als Text, nichts zu prüfen | – |

Installiert heißt: Propwash selbst, eine Mod mit Tabelle oder eine Mod mit Version. Fehlt das Add-on, lautet das Ergebnis
`MISSING`, auch wenn der Verzeichnis-Abzug seine Teile benennen kann.

### 5.3 Mindestversion

Der Kodierer schreibt die größte `since` der benutzten Einträge, ohne benutzte Einträge die installierte Version des
Add-ons (ohne Build-Zusatz hinter `+`), ohne beides keine Version. So steht dort „braucht JMP ab 1.0.0“, wenn nur alte Teile
vorkommen. Die Umrechnung (§7) schreibt nur `since`-Versionen.

### 5.4 Info (Name und Link)

Standard: Name und Link nur für Add-ons, die der Verzeichnis-Abzug nicht kennt und deren Metadaten (`ModMeta`) einen Namen
haben; nie für `propwash`. `ShareOptions` kann Infos abschalten. Der Name wird auf 48 Zeichen gekürzt. Linkart aus der
Startseite der Mod: `https://modrinth.com/mod/<slug>` → 1, `https://www.curseforge.com/minecraft/mc-mods/<slug>` → 2,
jede andere `https://`-Adresse aus druckbarem ASCII bis 200 Zeichen → 3, sonst kein Link.

**Sicherheit:** Klickbar sind nur Modrinth- und CurseForge-Projekte (Linkart 1 und 2) und die Links des Verzeichnis-Abzugs,
immer über den Bestätigungsdialog von Minecraft. Eine freie Adresse (Linkart 3) erscheint nur als Text mit dem Hinweis
„Lade Add-ons nur von Modrinth oder CurseForge“. Name und Adresse werden nur als Literal angezeigt.

---

## 6. Lesen

### 6.1 Ergebnis

`ShareDecoded`: `status`, `format` (`PW1`, `PW2`, `PW3`, `PWD1`, bei `OTHER_CODE` `PWT1`), `content` (Bauplan, Lack,
Tuning, OSD, Name, Kreativ-Werte), `layers`, `namespaces` (je Namensraum: Index, Namensraum oder leer bei unbekannter Kurzkennung,
Kurzkennung, Tabelle ja/nein, Prüfsumme, Mindestversion, Name und Link aus dem Code, installierte Version, Ergebnis, benutzt
ja/nein), `unknownParts`, `wrongKind`, `renamed`, `skipped`, `inactiveParts` (Teile von Add-ons, die in dieser Sitzung
inaktiv sind) und `carry` (Köpfe für das Neu-Schreiben, §3.5). Ein Leser wirft nie eine Ausnahme nach außen.

### 6.2 Gestrichene Teile

Eine Id, deren Tabellen-Eintrag `deprecated` mit `replaced_by` trägt, wird beim Lesen durch den Ersatz ersetzt, wenn die
alte Id nicht mehr angemeldet ist (Kette bis 8 Schritte, gemeldet in `renamed`). Das gilt für PW1, PW2 und PW3 gleich und
ersetzt die frühere Alias-Tabelle von JMP.

### 6.3 Tuning-Schlüssel

Index 1–57 wie PW2 (`pw2.md` §5.3). Die Tabelle wird nur hinten erweitert; Revision 3.0 erweitert sie nicht. Unbekannte
Flug-Schlüssel und Schlüssel fremder Namensräume gehen als Text. Farbtabelle (51 Farben) und OSD-Wörterbuch (8429 Bytes,
CRC-32 `0xb87eac81`) sind die von PW2 und bleiben eingefroren.

---

## 7. PW1 und PW2 umrechnen

`share.ShareConvert#toPw3(code)` liest den alten Code ohne Umbenennungen und schreibt PW3 **nur mit eingefrorenen Daten**:
Tabelle von Propwash aus dem Jar und JMP-Tabelle aus dem Verzeichnis-Abzug, beide gelten als installiert; Mindestversion
aus deren `since`; keine Info-Felder; keine installierten Versionen. Fremde Text-Ids aus PW2 gehen als voller Namensraum
(`s = 0`, `hatTabelle = 0`, Pfad als Text), Text-Ids von `propwash` und `justmoreparts`, die nicht in der Tabelle stehen,
als `v = 0` mit Pfad. Das Ergebnis hängt also nur vom alten Code ab und ist in Mod und Website bytegleich.

Nicht umrechenbar (`NOT_CONVERTIBLE` mit Grund): Teile `pw2.unknown:…` (Grund `pw2_unknown:<art>.<v>`), Namensräume
`pw1.unknown.N` (Grund `pw1_unknown:<N>`) und Text-Namensräume mit dem Präfix `pw3.` (Grund `reserved_namespace:<ns>`).
Beides kann nur ein von Hand gebauter Code enthalten. Fehlerhafte alte Codes behalten ihren Status (`CHECKSUM` usw.). Ein
PW3-Code wird unverändert als `PW3-<rumpf>` zurückgegeben.

Wer einen alten Code im Spiel übernimmt und die Drohne neu exportiert, bekommt PW3 mit den installierten Tabellen (ggf.
kürzer, weil ein installiertes Fremd-Add-on dann Nummern nutzt).

### 7.1 PWD1 (Drohnen-Editor bis 0.5.x) lesen und umrechnen

`PWD1-` + Base64url (ohne Padding) eines rohen Deflate-Stroms aus Zeilen `base.<art>=<teil>` und `<schlüssel>=<zahl>`
(`docs/design/creative-drone.md`, höchstens 4096 Zeichen Code und 16 KiB Text). Der Leser verlangt einen vollständigen
Deflate-Strom; sonst `CORRUPT` (Format `PWD1`). Ergebnis: Format `PWD1`, `legacy()`, Ebenen `PARTS` und `CREATIVE`.
PWD1 trägt nur die Editor-Werte; als Bauplan gilt fest die Freestyle-Drohne der Flugschule mit Kreativ-Flugcontroller:

```
propwash:freestyle_5, propwash:creative_fc, propwash:m2207_1750, propwash:p51_3b, propwash:digital_hd, propwash:b6s_1300
```

(`CreativeShare.PWD1_BUILD`, `spec.json` → `creative.pwd1_build`). Die Basis-Teile und Werte des Codes kommen unverändert
in die Ebene `CREATIVE`; so fliegt die Drohne wie früher nach dem Einfügen in einen Editor auf dieser Drohne.
`ShareConvert#toPw3` rechnet PWD1 wie PW1/PW2 mit den eingefrorenen Daten nach PW3 um (Ebenen Teile und Kreativ-Werte).
Die API (`ShareCodes#decode`) meldet PWD1 weiter als `OTHER_CODE`; `ShareCodes#convert` rechnet ihn um.

---

## 8. Status und Grenzen

| Status | Bedeutung |
|---|---|
| `OK` | gelesen |
| `EMPTY` | keine Eingabe |
| `INVALID_PREFIX` | kein Sharecode (auch: nackter Rumpf, der nicht lesbar ist) |
| `BAD_CHARACTER` | Zeichen außerhalb des Alphabets |
| `TOO_LONG` | über 4000 Zeichen nach dem Entfernen von Leerraum |
| `TRUNCATED` | Rahmen oder Feld endet vorzeitig |
| `CHECKSUM` | 16-Bit-Prüfsumme des Rahmens falsch |
| `UNSUPPORTED_VERSION` | `PW4…` und höher |
| `TOO_LARGE` | Rahmen über 2500 Bytes, entpacktes OSD über 16384 Bytes |
| `CORRUPT` | sonstige Formatfehler |
| `OTHER_CODE` | `PWT1…` (in der API auch `PWD1…`) |
| `NOT_CONVERTIBLE` | nur beim Umrechnen (§7) |
| `UNKNOWN_ENTRIES` | nur beim Schreiben (§3.5) |

Grenzen: 4000 Zeichen Eingabe, 2500 Bytes Rahmen, 16 Namensräume, Namensraum 64 Zeichen, Pfad 128, Slug 64, Link 200,
Versionszusatz 32, Add-on-Name 48, Drohnenname 48, 256 Tuning-Einträge (Text-Schlüssel 64 Zeichen), 8192 Zeichen OSD-JSON,
255 Zubehör-Einträge, 32 Erweiterungen. Jede Länge wird vor dem Lesen gegen die restlichen Bits geprüft; der Leser legt
keinen Speicher nach Angaben aus dem Code an.

---

## 9. Gemessene Längen

Zeichen je Code PW1 / PW2 / **PW3** für alle 27 Presets (5 von Propwash, 22 von JMP 1.1.1), gemessen am 18.10.2026 mit
den Golden-Dateien: PW1 und PW2 von den Kodierern aus JMP 1.1.1, PW3 als Umrechnung (§7, gleich dem Export im Spiel mit
Propwash und JMP 1.2.0).

- **Teile**: nur die Teile-Ebene.
- **Preset**: Teile, Lack des Presets, leeres Tuning (wie der Preset-Code im Web-Export).
- **Actual + Name**: Preset mit Lack, Actual-Rates auf drei Achsen und Kamerawinkel (11 Werte), Name „Night Ripper“.
- **BF + Name**: Preset mit Lack, Betaflight-Rates, PIDs, Gaskurve, TPA, Filter und Kamerawinkel (19 Werte), Name.
- **alles + OSD**: wie BF + Name, dazu das Race-OSD von Propwash mit Geschwindigkeit und Skalierung 1,15.

| Preset | Teile | Preset | Actual + Name | BF + Name | alles + OSD |
|---|---|---|---|---|---|
| `propwash:whoop` | 146 / 13 / **17** | 150 / 13 / **18** | 252 / 51 / **56** | 301 / 68 / **72** | 1551 / 111 / **116** |
| `propwash:cinewhoop` | 143 / 13 / **17** | 146 / 13 / **18** | 249 / 51 / **56** | 294 / 68 / **72** | 1548 / 111 / **116** |
| `propwash:freestyle` | 146 / 13 / **17** | 150 / 13 / **18** | 253 / 51 / **56** | 297 / 68 / **72** | 1548 / 111 / **116** |
| `propwash:racer` | 144 / 13 / **17** | 147 / 14 / **18** | 249 / 52 / **56** | 296 / 68 / **72** | 1542 / 112 / **116** |
| `propwash:long_range` | 156 / 13 / **17** | 159 / 13 / **18** | 263 / 51 / **56** | 307 / 68 / **72** | 1557 / 111 / **116** |
| `justmoreparts:whoop75_1s` | 179 / 13 / **18** | 200 / 18 / **22** | 301 / 55 / **60** | 354 / 72 / **77** | 1599 / 115 / **120** |
| `justmoreparts:whoop85_2s` | 176 / 13 / **18** | 204 / 18 / **22** | 306 / 55 / **60** | 357 / 72 / **77** | 1603 / 115 / **120** |
| `justmoreparts:toothpick2_2s` | 167 / 13 / **18** | 188 / 18 / **22** | 290 / 55 / **60** | 342 / 72 / **77** | 1596 / 115 / **120** |
| `justmoreparts:toothpick3_4s` | 163 / 14 / **23** | 186 / 18 / **27** | 288 / 56 / **65** | 335 / 72 / **82** | 1590 / 116 / **125** |
| `justmoreparts:toothpick35_6s` | 175 / 14 / **19** | 200 / 18 / **23** | 301 / 56 / **61** | 348 / 73 / **77** | 1597 / 116 / **121** |
| `justmoreparts:cinewhoop25_4s` | 167 / 14 / **18** | 195 / 20 / **23** | 300 / 57 / **61** | 344 / 73 / **78** | 1599 / 118 / **121** |
| `justmoreparts:cinewhoop35_6s` | 173 / 14 / **18** | 201 / 20 / **25** | 303 / 57 / **62** | 355 / 75 / **78** | 1602 / 118 / **123** |
| `justmoreparts:bash5_6s` | 173 / 13 / **23** | 201 / 18 / **27** | 303 / 56 / **65** | 355 / 72 / **82** | 1596 / 116 / **125** |
| `justmoreparts:squashed5_6s` | 179 / 13 / **23** | 205 / 18 / **27** | 307 / 56 / **65** | 359 / 73 / **82** | 1599 / 116 / **125** |
| `justmoreparts:deadcat5_6s` | 173 / 13 / **23** | 204 / 20 / **29** | 306 / 57 / **66** | 354 / 73 / **84** | 1599 / 118 / **127** |
| `justmoreparts:race5_4s` | 194 / 14 / **18** | 230 / 21 / **25** | 332 / 57 / **62** | 380 / 75 / **80** | 1622 / 118 / **123** |
| `justmoreparts:race5_6s` | 182 / 13 / **23** | 211 / 18 / **29** | 313 / 56 / **66** | 365 / 73 / **82** | 1613 / 116 / **127** |
| `justmoreparts:hframe5_6s` | 173 / 13 / **23** | 195 / 18 / **27** | 297 / 56 / **65** | 349 / 72 / **82** | 1593 / 116 / **125** |
| `justmoreparts:lr4_6s_lion` | 169 / 13 / **18** | 198 / 18 / **22** | 301 / 56 / **60** | 345 / 72 / **77** | 1596 / 116 / **120** |
| `justmoreparts:lr6_6s` | 163 / 13 / **18** | 188 / 18 / **23** | 290 / 56 / **61** | 338 / 73 / **78** | 1593 / 116 / **121** |
| `justmoreparts:lr7_deadcat_lion` | 176 / 14 / **19** | 215 / 20 / **25** | 319 / 57 / **62** | 363 / 75 / **80** | 1611 / 118 / **123** |
| `justmoreparts:lr7_8s` | 173 / 14 / **19** | 204 / 21 / **26** | 307 / 57 / **62** | 351 / 75 / **80** | 1599 / 118 / **123** |
| `justmoreparts:lr8_6s_lion` | 171 / 14 / **19** | 201 / 20 / **25** | 303 / 56 / **61** | 349 / 73 / **78** | 1599 / 116 / **121** |
| `justmoreparts:xclass10_6s` | 176 / 14 / **19** | 205 / 18 / **23** | 307 / 56 / **61** | 359 / 73 / **78** | 1599 / 116 / **121** |
| `justmoreparts:xclass12_8s` | 169 / 14 / **19** | 194 / 20 / **25** | 296 / 56 / **61** | 345 / 73 / **78** | 1596 / 116 / **121** |
| `justmoreparts:xclass13_8s` | 173 / 14 / **19** | 198 / 20 / **25** | 300 / 56 / **61** | 351 / 73 / **78** | 1597 / 116 / **121** |
| `justmoreparts:xclass13h_8s` | 171 / 14 / **19** | 195 / 20 / **25** | 297 / 56 / **61** | 349 / 73 / **78** | 1596 / 116 / **121** |
| **Mittel** | 169 / 13 / **19** | 191 / 18 / **24** | 294 / 55 / **61** | 342 / 72 / **78** | 1590 / 115 / **121** |

Mehrkosten gegenüber PW2: 4 bis 10 Zeichen. Präfix `PW3-` statt `PW2` (1 Zeichen), je Namensraum Kurzkennung, Tabellen-Bit,
10 Bit Prüfsumme und die Mindestversion. Presets mit Teilen aus Propwash **und** JMP tragen zwei Köpfe (23 statt 18
Zeichen). Alles passt weiter in eine Chatzeile (256 Zeichen). Die 12 gesammelten echten Codes aus dem Drohnen-Channel
(PW2, 25–71 Zeichen) werden 35–74 Zeichen lang.

**Mit Kreativ-Werten (Revision 3.1, `pw3-creative-vectors.json`):** leere Werte auf der Freestyle-Drohne mit Kreativ-FC
21 Zeichen (C3), JMP-Drohne mit vier Werten 42 (C2), Freestyle mit Lack, Tuning, Name, drei Werten und zwei Basis-Teilen 93
(C1). Umgerechnete PWD1-Codes werden kürzer: 123 → 70 (D1), 55 → 41 (D3). Der Editor-Export im Selbsttest (Tuning mit
Kamerawinkel, sieben Werte, zwei Basis-Teile) hat 64 Zeichen; alles passt in eine Chatzeile.

---

## 10. Testvektoren und Golden-Dateien

Alle Dateien liegen in `common/src/test/resources/sharecode/`. Java (JUnit, Paket `de.lardtom.propwash.share`) und die
Website prüfen dieselben Dateien.

| Datei | Inhalt | Prüft |
|---|---|---|
| `pw1-vectors.json` | 306 PW1-Codes aus JMP 1.1.1: Vektoren A–G (`pw1.md` §13), die 61 veröffentlichten Vektoren von JMP 1.0.0, 27 Presets × 5 Varianten, 100 Zufallsinhalte, 3 Sonderfälle (unbekannter Namensraum 7, fremde Text-Ids, Zubehör vorne); je Code Status, Ebenen, Inhalt, `skipped` | `LegacyReadTest` |
| `pw2-vectors.json` | 447 PW2-Codes aus JMP 1.1.1: Vektoren A–I (`pw2.md`), 27 Presets × 5 Varianten, 300 Zufallsinhalte, 3 Sonderfälle | `LegacyReadTest`, `SubstitutesTest` |
| `pw3-conversions.json` | jede Eingabe aus beiden Dateien oben → `ShareConvert#toPw3`: Status, PW3-Code oder Grund (747 `OK`, 3 `CHECKSUM`, 3 `NOT_CONVERTIBLE`) | `ShareConvertTest` |
| `pw3-creative-vectors.json` | Revision 3.1: C1–C4 mit Ebene Kreativ-Werte (Editor-Export, JMP-Drohne nur mit Werten, leere Werte, Text-Schlüssel und rohe Zahlen) und D1–D3 PWD1 → PW3; je Vektor Code, Ebenen, Inhalt, Kreativ-Werte | `CreativeShareTest` |
| `pw3-vectors.json` | P1–P11 und U01–U27 (je 5 Varianten), dazu die Kontexte `frozen`, `extra` und `extra_newer`; je Vektor Inhalt zum Schreiben, Code, Status, Inhalt nach dem Lesen, Berichte je Namensraum, `skipped`, unbekannte Teile, Umbenennungen | `Pw3CodecTest` |
| `tablechecksum-vectors.json` | Prüfsummen-Vektoren (§5.2) | `TableChecksumTest` |
| `propwash-baseline.json` | eingefrorene Reihenfolge der Propwash-Tabelle | `ShareTableTest` |
| `directory-baseline.json` | eingefrorene Reihenfolge der JMP-Tabelle im Abzug | `DirectorySnapshotTest` |

Kontexte von `pw3-vectors.json`: `frozen` ist der Kontext der Umrechnung (§7). `extra` und `extra_newer` enthalten die
Propwash-Tabelle, den Verzeichnis-Abzug, alle eingebauten Teile als angemeldet und zusätzlich das Test-Add-on
`extraparts` (Tabelle in der Datei, Version 1.1.0 bzw. 1.2.0 mit einem dritten Rahmen) sowie die Mods `otheraddon`,
`freeaddon` und `betaaddon` ohne Tabelle; JMP fehlt dort.

| Vektor | Inhalt | Code |
|---|---|---|
| P1 | nur Teile, Propwash-Preset Freestyle (`frozen`) | `PW3-02lVAldkcxCBH` |
| P2 | nur Teile, JMP-Preset `bash5_6s` mit Action-Cam vorne (`frozen`) | `PW3-012k1VwVtRUzVyI1ZQauW` |
| P3 | alle Ebenen: Teile, Lack, BF-Tuning, Name, OSD (`deadcat5_6s`, `frozen`) | 127 Zeichen, siehe Datei |
| P4 | Fremd-Add-on `otheraddon` ohne Tabelle (Text-Namensraum, Text-Pfad) mit Info „Other Addon“, Modrinth-Slug `other-addon` | 64 Zeichen |
| P5 | Fremd-Add-on `extraparts` mit Tabelle ohne Kurzkennung, Zubehör `x_cam` als `v = 0` mit Pfad | 65 Zeichen |
| P6 | Rahmen 3 von `extraparts` 1.2.0, gelesen mit 1.1.0: `pw3.unknown:extraparts/frame/3`, Ergebnis `NEWER`, Mindestversion 1.2.0; Neu-Schreiben mit `carry` ergibt denselben Code | 58 Zeichen |
| P7 | gestrichenes Teil `extraparts:old_motor` → `extraparts:new_motor` (`renamed`) | 57 Zeichen |
| P8 | Linkart 3 (freie Adresse `example.org/free-addon`, nur als Text) | 77 Zeichen |
| P9 | Versionszusatz `1.3.0-beta.1`, CurseForge-Slug | 72 Zeichen |
| P10 | P1 mit zwei vertauschten Zeichen → `CHECKSUM` | `PW3-02lVAldkcxCHB` |
| P11 | P2 ohne Präfix (nackter Rumpf) → `OK`, gleicher Inhalt | `012k1VwVtRUzVyI1ZQauW` |
| U01–U27 | alle 27 Presets aus `pw2-vectors.json` umgerechnet, je in 5 Varianten | siehe Datei |

Dazu prüft `Pw3CodecTest`: 10000 zufällige Baupläne mit festem Seed (Teile aus allen Tabellen, Text-Ids, unbekannte
Nummern, alle Ebenen) im Rundlauf, jeden Status, das tolerante Einlesen, alle Grenzen, dass die gemeinsamen Ebenen über
mehr als 400 Golden-Inhalte bitgleich zu PW2 sind, und dass 100000 zufällige und verstümmelte Eingaben nie eine Ausnahme
auslösen.

---

## 11. Stabilitätsregeln

1. Ein veröffentlichter PW3-Code bleibt für immer lesbar. Inkompatible Änderungen bekommen `PW4-`.
2. Nur anhängen: Teile-Tabellen je Namensraum, Tuning-Schlüssel, Lack-Plätze, Zubehör-Plätze, Erweiterungs-Ebenen,
   Kurzkennungen, Kreativ-Schlüssel (§3.6). Eingefroren seit 3.1 auch die Parameter der Kreativ-Ebene (Kopf 0, Anzahl 2,
   Abstand 1, Text-Anzahl 0, Nachkommastellen 0, Zahl 4) und der PWD1-Bauplan (§7.1). Eingefroren: Farbtabelle, OSD-Wörterbuch, beide Text-Alphabete, alle Exp-Golomb-Parameter (Namensräume 0,
   Kurzkennung 1, Teil 3, `idtext` 3, Version 0, Zubehör 0, dazu die von PW2), die Prüfsummen-Regel und die Linkarten.
3. Die Revision steigt bei jeder Ergänzung (3.0 → 3.1); Testvektoren kommen dazu, alte bleiben bytegleich.
4. Java und JavaScript schreiben jeden Code bytegleich; die Golden-Dateien beweisen das in JUnit und auf der Website.

---

## 12. Kompatibilität

| Programm | liest | schreibt |
|---|---|---|
| Propwash 0.6.0 (mit oder ohne JMP) | PW1, PW2, PW3, PWD1 | **PW3** (auch der Drohnen-Editor) |
| Propwash 0.5.x mit JMP 1.1.1 | PW1, PW2; PW3 → `UNSUPPORTED_VERSION` | PW2 |
| Website ab 0.6.0 | PW1, PW2, PW3 (Kreativ-Ebene und PWD1 mit dem Port von Revision 3.1) | **PW3** |

Codes stehen nirgends im Spielstand; es gibt nichts zu migrieren. Das Netzwerkprotokoll ändert sich durch PW3 nicht.

---

## 13. API für andere Mods

`PropwashApi.shareCodes()` (API 1.7.0, Paket `de.lardtom.propwash.api.share`) liefert `ShareCodes` mit `decode(text)`,
`encode(DroneData, ShareOptions)` und `convert(alterCode)`. `ShareResult` trägt Status, Format, `DroneData`, OSD-JSON, je
Namensraum Kurzkennung, installierte Version, Mindestversion, Prüfergebnis, Name und Link (Name und Link aus dem
Verzeichnis-Abzug vor denen aus dem Code; `trustedLink` nur für Links des Abzugs, Links aus dem Code nie), die unbekannten Teile und bei `convert` den PW3-Code. `ShareOptions` wählt
die Ebenen, ob Infos zu Add-ons ohne Verzeichniseintrag mitgeschrieben werden, und das OSD-JSON. `encode` wirft eine
`IllegalArgumentException`, wenn sich der Inhalt nicht schreiben lässt (etwa `pw2.unknown`-Teile oder mehr als 2500 Bytes).
Server-Mods und Bots lesen und verteilen Codes so, ohne Interna von Propwash anzufassen.

---

## 14. Festlegungen der Umsetzung

| Was | Warum |
|---|---|
| Text-Namensräume mit `pw3.` sind reserviert (nicht nur `pw3.unknown`) | Propwash benutzt `pw3.short.<s>` intern für unbekannte Kurzkennungen; so kann kein echter Namensraum damit verwechselt werden |
| Pfade dürfen `/` enthalten, Namensräume und Slugs nicht | das `idtext`-Alphabet sieht `/` vor; Propwash-Teile selbst haben keine Unterordner |
| Unbekannte Einträge als `pw3.unknown:<ns>/<art>/<v>` bzw. `…/0/<pfad>` | eindeutig, im Bauplan als gewöhnliche Id tragbar, beim Neu-Schreiben wieder in Nummer und Kopf zerlegbar |
| Neu-Schreiben vergleicht die Werte je Platz, nicht nur die Menge der Teile | ein vertauschtes Teil im selben Namensraum ändert die Prüfsumme; der alte Kopf wäre dann falsch |
| Leser verlangt keine Reihenfolge der Namensräume | tolerant gegen Werkzeuge; der Kodierer schreibt trotzdem kanonisch |
| Die Umrechnung zählt Propwash und JMP als installiert | so lautet das Ergebnis `MATCH` und hängt nicht vom Rechner ab |
