# Sharecode PW2 — Spezifikation (eingefroren)

| | |
|---|---|
| Format | **PW2** (Präfix `PW2`, ohne Bindestrich) |
| Revision | 2.0 vom 2026-10-06 |
| Gilt ab | Just More Parts 1.0.0 (für Propwash 0.4.0) und 1.1.0 (für Propwash 0.5.0); Web-Konfigurator auf propwashfpv.com |
| Referenz | bis JMP 1.1.1: Java `de.lardtom.justmoreparts.share.Pw2Codec` mit `Pw2Tables`, `BitWriter`, `BitReader`, `Base62`; JavaScript (Website): `assets/js/configurator/sharecode2.js` mit `sharecode2-tables.js`; ab Propwash 0.6.0 nur Lesen: `de.lardtom.propwash.share.legacy.Pw2Reader` |
| Stand | eingefroren seit Propwash 0.6.0 |
| Vorgänger | PW1 Revision 1.2 (`pw1.md`), wird weiter gelesen |

> **Eingefroren.** Diese Spezifikation ist aus Just More Parts 1.1.1 (`docs/design/sharecode-pw2.md`, Revision 2.0)
> übernommen und wird nicht mehr geändert. Seit Propwash 0.6.0 schreiben Mod und Website nur noch PW3 (`pw3.md`), das die
> Ebenen Lack, Tuning, OSD, Name und Erweiterungen bitgleich aus diesem Dokument übernimmt. PW2 wird für immer gelesen:
> `de.lardtom.propwash.share.legacy.Pw2Reader` mit `Pw2Tables`. Einen PW2-Kodierer gibt es in Propwash nicht; die
> Testvektoren und alle weiteren Codes von JMP 1.1.1 liegen als Golden-Datei in
> `common/src/test/resources/sharecode/pw2-vectors.json` (geprüft von `LegacyReadTest`). Verweise auf Klassen in
> `de.lardtom.justmoreparts.share`, auf `Pw2CodecTest` und auf `PartAliases` beziehen sich auf JMP bis 1.1.1; die Rolle
> von `PartAliases` übernehmen in PW3 die Einträge `deprecated` und `replaced_by` der Teile-Tabellen (`tables.md`).

PW2 trägt genau denselben Inhalt wie PW1 (Ebenen, Teile, Lack, Tuning samt Kamerawinkel, OSD-Layout, Name; Abschnitt 1
in `pw1.md`), nur viel kürzer. Übernehmen, Befehle, Einkaufsliste und Sicherheit (`pw1.md` §8, §9, §12)
gelten unverändert: Ein PW2-Code dekodiert zu denselben Objekten (`ShareContent`, `ShareDecoded`) wie ein PW1-Code.

Ziel (06.10.2026): deutlich kürzere Codes ohne Datenverlust, alte Codes funktionieren weiter.

---

## 1. Gemessene Längen

Zeichen je Code, PW1 → **PW2**, für alle 27 Presets (5 von Propwash, 22 von JMP), gemessen mit den echten Kodierern
(`ShareCodec.encodePw1` und `ShareCodec.encode`) am 06.10.2026:

- **Teile**: nur die Teile-Ebene.
- **Preset**: wie der Preset-Code im Web-Export (Teile, Lack des Presets, leeres Tuning).
- **Actual + Name**: Preset mit Lack, Actual-Rates auf drei Achsen (11 Werte samt Kamerawinkel) und dem Namen „Night Ripper“.
- **BF + Name**: Preset mit Lack, Betaflight-Rates, eigene PIDs, Gaskurve, TPA, Filter und Kamerawinkel (19 Werte) und Name.
- **alles + OSD**: wie BF + Name, dazu ein eigenes OSD-Layout (Race-Preset von Propwash mit eingeschalteter Geschwindigkeit
  und Skalierung 1,15; 2116 Zeichen JSON).

| Preset | Teile | Preset | Actual + Name | BF + Name | alles + OSD |
|---|---|---|---|---|---|
| `propwash:whoop` | 146 → **13** | 150 → **13** | 249 → **51** | 303 → **68** | 1557 → **111** |
| `propwash:cinewhoop` | 143 → **13** | 146 → **13** | 248 → **51** | 296 → **68** | 1554 → **111** |
| `propwash:freestyle` | 146 → **13** | 150 → **13** | 252 → **51** | 300 → **68** | 1554 → **111** |
| `propwash:racer` | 138 → **13** | 143 → **13** | 240 → **51** | 291 → **68** | 1545 → **111** |
| `propwash:long_range` | 156 → **13** | 159 → **13** | 261 → **51** | 309 → **68** | 1563 → **111** |
| `justmoreparts:whoop75_1s` | 179 → **13** | 200 → **18** | 300 → **55** | 355 → **72** | 1611 → **115** |
| `justmoreparts:whoop85_2s` | 176 → **13** | 204 → **18** | 303 → **55** | 359 → **72** | 1609 → **115** |
| `justmoreparts:toothpick2_2s` | 167 → **13** | 188 → **18** | 288 → **55** | 344 → **72** | 1602 → **115** |
| `justmoreparts:toothpick3_4s` | 163 → **14** | 186 → **18** | 284 → **55** | 336 → **72** | 1593 → **116** |
| `justmoreparts:toothpick35_6s` | 175 → **14** | 200 → **18** | 297 → **55** | 349 → **72** | 1603 → **116** |
| `justmoreparts:cinewhoop25_4s` | 167 → **14** | 195 → **20** | 297 → **56** | 345 → **73** | 1609 → **118** |
| `justmoreparts:cinewhoop35_6s` | 173 → **14** | 201 → **20** | 300 → **56** | 357 → **73** | 1607 → **118** |
| `justmoreparts:bash5_6s` | 173 → **13** | 201 → **18** | 301 → **55** | 357 → **72** | 1602 → **116** |
| `justmoreparts:squashed5_6s` | 179 → **13** | 205 → **18** | 306 → **55** | 361 → **72** | 1607 → **116** |
| `justmoreparts:deadcat5_6s` | 173 → **13** | 204 → **20** | 301 → **56** | 355 → **73** | 1603 → **118** |
| `justmoreparts:race5_4s` | 194 → **14** | 230 → **21** | 330 → **57** | 383 → **75** | 1632 → **118** |
| `justmoreparts:race5_6s` | 182 → **13** | 211 → **18** | 311 → **55** | 367 → **72** | 1621 → **116** |
| `justmoreparts:hframe5_6s` | 173 → **13** | 195 → **18** | 296 → **55** | 351 → **72** | 1599 → **116** |
| `justmoreparts:lr4_6s_lion` | 169 → **13** | 198 → **18** | 297 → **55** | 348 → **72** | 1603 → **116** |
| `justmoreparts:lr6_6s` | 163 → **13** | 188 → **18** | 288 → **55** | 339 → **72** | 1597 → **116** |
| `justmoreparts:lr7_deadcat_lion` | 176 → **14** | 215 → **20** | 315 → **56** | 365 → **75** | 1615 → **118** |
| `justmoreparts:lr7_8s` | 173 → **14** | 204 → **21** | 306 → **57** | 354 → **75** | 1605 → **118** |
| `justmoreparts:lr8_6s_lion` | 171 → **14** | 201 → **20** | 301 → **56** | 351 → **73** | 1605 → **116** |
| `justmoreparts:xclass10_6s` | 176 → **14** | 205 → **18** | 306 → **55** | 361 → **73** | 1609 → **116** |
| `justmoreparts:xclass12_8s` | 169 → **14** | 194 → **20** | 294 → **56** | 348 → **73** | 1603 → **116** |
| `justmoreparts:xclass13_8s` | 173 → **14** | 198 → **20** | 297 → **56** | 354 → **73** | 1605 → **116** |
| `justmoreparts:xclass13h_8s` | 171 → **14** | 195 → **20** | 296 → **56** | 351 → **73** | 1602 → **116** |
| **Mittel** | 168 → **13** (−92 %) | 191 → **18** (−91 %) | 291 → **55** (−81 %) | 344 → **72** (−79 %) | 1597 → **115** (−93 %) |

Ein PW2-Code mit Teilen, Lack, vollem Tuning und Namen passt damit bequem in eine Chatzeile (Minecraft: 256 Zeichen),
auch mit OSD-Layout. Beispiel (Vektor A, nur Teile des Freestyle-Presets): `PW205zwQBrAS4` statt
`PW1-04002-GR11D-K74SB-…-H23NQ` (147 Zeichen).

Woher die Ersparnis kommt (Beispiel `deadcat5_6s`, BF + Name): Teile 36 statt 114 Byte-Bits als Text (PW1 schreibt jede
Id als Text), Lack 29 Bit statt 88, Tuning rund 33 Byte statt 70, Name 10 Byte statt 13, Rahmen und Prüfsumme 2 statt
6 Byte; dazu Base62 (5,95 Bit je Zeichen) statt Crockford-Base32 mit Bindestrich alle fünf Zeichen (4,17 Bit je Zeichen).

---

## 2. Textform

```
PW2<base62>
```

- Präfix `PW2` (Großbuchstaben), direkt danach die Base62-Zeichen, **ohne Bindestriche und ohne Gruppen**: Der ganze Code ist
  ein Wort, ein Doppelklick markiert ihn vollständig (in Discord, im Browser, im Minecraft-Chat), und er ist URL-sicher
  (`configurator/#PW2…`).
- Alphabet (Wert 0–61): `0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz`. **Groß- und Kleinschreibung zählt.**
- Die Bytes des Rahmens (Abschnitt 3) gelten als eine große vorzeichenlose Zahl (big-endian) und werden in Base62
  geschrieben, höchstwertige Stelle zuerst, **mit fester Länge**: n Bytes ergeben genau `z(n)` Zeichen, die kleinste Zahl
  mit 62^z ≥ 256^n (führende `0` werden geschrieben). `z(n)` steigt streng (je Byte um 1 oder 2), also ergibt jede
  Zeichenzahl höchstens eine Bytezahl.

**Einlesen (tolerant):**
1. Leerraum (wie PW1: `Character.isWhitespace` oder `isSpaceChar`) entfernen. Länge über 4000 → `TOO_LONG`.
2. Beginnt der Text (in Großbuchstaben) mit `PW2`, wird der Rest **ohne Umwandlung der Schreibweise** gelesen; `-`
   werden entfernt (so geht auch `PW2-…` oder ein Code, den jemand in Gruppen aufgeteilt hat).
3. Beginnt er mit `PW1`, gilt PW1 (`pw1.md`). Beginnt er mit `PW` und einer Zahl ab 3 (`PW3…`), → `UNSUPPORTED_VERSION`.
   Sonst → `INVALID_PREFIX`.
4. Zeichen außerhalb des Alphabets → `BAD_CHARACTER`. Gibt es zur Zeichenzahl keine Bytezahl → `TRUNCATED`. Ist die Zahl
   größer als 256^n − 1 → `CORRUPT`.

---

## 3. Rahmen

| Teil | Länge | Inhalt |
|---|---|---|
| Nutzdaten | n − 2 Bytes | Bitstrom (Abschnitt 4), mit 0-Bits auf ganze Bytes aufgefüllt |
| Prüfsumme | 2 Bytes | die **unteren 16 Bit** der CRC-32 (IEEE, wie `java.util.zip.CRC32`) über die Nutzdaten, big-endian |

- Rahmen über 2500 Bytes → `TOO_LARGE`; unter 3 Bytes → `TRUNCATED`; Prüfsumme falsch → `CHECKSUM`.
- Es gibt kein Versionsbyte: Die Version steckt im Präfix. Spätere Revisionen von PW2 bleiben für alte Leser lesbar
  (Abschnitt 8); ein inkompatibles Format bekommt `PW3`.

---

## 4. Bitstrom

Bits werden höchstwertiges Bit zuerst in die Bytes geschrieben. Nach dem letzten Feld folgen höchstens 7 Füllbits, die
0 sein müssen; mehr Bits oder ein gesetztes Füllbit → `CORRUPT`. Liest ein Feld über das Ende → `TRUNCATED`.

### 4.1 Grundtypen

| Typ | Kodierung |
|---|---|
| `bit` | ein Bit |
| `u(n)` | n Bit, vorzeichenlos |
| `eg(k)` | Exp-Golomb-Zahl ≥ 0 mit Parameter k: w = Wert + 2^k hat b Bits; geschrieben werden b − k − 1 Nullen, dann w mit b Bits. Mehr als 40 führende Nullen → `CORRUPT`. Länge in Bit: 2b − k − 1 |
| `zz` | vorzeichenbehaftete Zahl q als `eg(7)` von 2q (q ≥ 0) bzw. −2q − 1 (q < 0); Werte über 2³² − 1 → `CORRUPT` |
| `idx(n)` | Index in eine Liste mit n Einträgen: `u(⌈log₂ n⌉)` (0 Bit bei n = 1); Index ≥ n → `CORRUPT` |
| `text` | Modus, dann `eg(3)` Anzahl, dann die Zeichen (unten) |

**`text`**: Der Kodierer nimmt den ersten passenden Modus.

| Modus | Bits | Zeichen | je Zeichen |
|---|---|---|---|
| Tabelle | `0` | nur aus `0-9 A-Z a-z`, Leerzeichen, `-` (64 Zeichen, Reihenfolge wie geschrieben: Ziffern, Großbuchstaben, Kleinbuchstaben, Leerzeichen, Bindestrich) | 6 Bit (Index) |
| ASCII | `10` | nur druckbares ASCII `0x20`–`0x7E` | 7 Bit (Zeichen − 0x20; Werte über 94 → `CORRUPT`) |
| UTF-8 | `11` | alles andere; Anzahl = Bytes | 8 Bit (UTF-8 wie `String#getBytes(UTF_8)`; ungültiges UTF-8 → `CORRUPT`) |

Passt die Anzahl nicht in die restlichen Bits → `TRUNCATED`; ist sie größer als die Grenze des Feldes (bei UTF-8 das
Vierfache) → `CORRUPT`.

### 4.2 Aufbau

```
bit hatLack  bit hatTuning  bit hatOsd  bit hatName  bit hatErweiterungen
TEILE                              (immer)
LACK      wenn hatLack
TUNING    wenn hatTuning
OSD       wenn hatOsd
text NAME wenn hatName             (nach strip() 1–48 Zeichen, sonst CORRUPT)
ERWEITERUNGEN wenn hatErweiterungen
```

Die Ebenen-Liste des Ergebnisses ist PARTS, dann die gesetzten Ebenen in dieser Reihenfolge (wie PW1).

**ERWEITERUNGEN** (für spätere Revisionen; PW2 2.0 schreibt sie nie):
`eg(0) anzahl` (≤ 32), je Eintrag `eg(0) e` (Ebenen-Bit = 5 + e), `eg(6) bitlänge`, dann so viele Bits. Ein Leser
überspringt sie und vermerkt `layer:<bit>` in `skipped` (wie PW1 bei unbekannten Ebenen).

### 4.3 TEILE

```
ref(FRAME) ref(STACK) ref(MOTOR) ref(PROP) ref(VIDEO) ref(BATTERY)
eg(0) zubehörAnzahl (≤ 255), je Eintrag: eg(0) platz, ref(ACCESSORY)
```

Plätze wie PW1 (0 `top`, 1 `bottom`, ab 1.1.1 mit Propwash 0.5.2 auch 2 `front`); unbekannter Platz → gelesen und verworfen (`accessory_slot:<n>`), doppelt → der
spätere gewinnt (`accessory_slot_duplicate:<n>`).

**`ref(art)`** = `eg(4) v`:

| v | Bedeutung |
|---|---|
| 0 | Text-Id: `text namensraum` (1–64 Zeichen `[a-z0-9_.-]`, nicht `..`), `text pfad` (1–128 Zeichen `[a-z0-9_.-]`); sonst `CORRUPT`. Der Namensraum `pw2.unknown` ist als Text verboten (`CORRUPT`). Für Teile fremder Addons und für Teile, die nach dieser Tabelle dazukamen, wenn der Kodierer sie noch nicht kennt |
| 1 … Länge | Eintrag v − 1 der **Teile-Tabelle der Art** (Abschnitt 5.1) |
| größer | Teil aus einer neueren Tabelle: Die Id heißt intern `pw2.unknown:<art>.<v>` (Anzeige und Website `?<art>.<v>`, z. B. `?frame.200`), gilt als unbekanntes Teil (`unknownParts`), `skipped` bekommt einmal `part_index:<art>.<v>`; beim erneuten Kodieren wird dieselbe Zahl geschrieben |

Kodierer: Steht die Id in der Tabelle ihrer **Feld-Art**, wird die Nummer geschrieben, sonst die Text-Id (also auch ein
Motor im Rahmen-Feld; er wird beim Lesen als `wrongKind` gemeldet wie bei PW1). Eine Id `pw2.unknown:<art>.<v>` darf nur
im Feld ihrer Art und nur mit v > Tabellenlänge stehen, sonst lehnt der Kodierer sie ab. PW1-Ids aus unbekannten
Namensräumen (`?7:pfad`, intern `pw1.unknown.7:pfad`) werden als Text-Id mit dem Namensraum `pw1.unknown.7` getragen.

### 4.4 LACK

```
eg(2) L                       (Zahl der Platz-Bits, ≤ 64; der Kodierer schreibt höchster belegter Platz + 1)
L × bit belegt[i]             (Platz i wie PW1-Lack-Bit i)
je belegtem Platz i, aufsteigend:
    wenn schon Farben gelesen:  bit neu   (0 = Wiederholung)
    Wiederholung: idx(Anzahl bisheriger Farben)  → diese Farbe
    neu:          bit roh;  roh = 0: eg(5) Index in die Farbtabelle (Abschnitt 5.2, Index außerhalb → CORRUPT)
                            roh = 1: u(24) RGB
                  die Farbe kommt ans Ende der Liste bisheriger Farben
```

Plätze ab 12 werden gelesen und verworfen (`paint_slot:<i>`). Der Kodierer schreibt eine Farbe, die schon vorkam, immer
als Wiederholung, eine neue Farbe aus der Tabelle als Index, sonst roh.

### 4.5 TUNING

```
eg(2) n                       (Einträge mit Tabellen-Index, ≤ 256)
je Eintrag, Index aufsteigend:  eg(0) abstand (Index = vorheriger Index + 1 + abstand, Start 0), wert
eg(0) m                       (Einträge mit Text-Schlüssel, n + m ≤ 256)
je Eintrag, Schlüssel nach Unicode-Ordnung:  text schlüssel (1–64 Zeichen), wert
```

**`wert`** (präfixfrei; jeder Wert, der keine Wiederholung ist, kommt ans Ende der Liste bisheriger Werte):

| Präfix | Bedeutung |
|---|---|
| `0` | ganzzahlig: `zz` q, Wert = (float) q |
| `10` | zwei Nachkommastellen: `zz` q, Wert = (float) (q / 100.0) |
| `110` | Wiederholung: `idx(Anzahl bisheriger Werte)`, Wert bitgleich wie dort |
| `1110` | eine Nachkommastelle: `zz` q, Wert = (float) (q / 10.0) |
| `11110` | drei Nachkommastellen: `zz` q, Wert = (float) (q / 1000.0) |
| `11111` | roh: `u(32)` IEEE-754 binary32; nicht endlich → `CORRUPT` |

Kodierer: Wiederholung, wenn derselbe float (bitgleich) schon geschrieben wurde; sonst die kleinste Stellenzahl
e ∈ {0, 1, 2, 3}, für die `q = Math.round((double) wert · 10^e)` mit |q| < 2³¹ den Wert bitgleich ergibt (−0,0 nie,
also roh), sonst roh. Alles andere wie PW1: nur Flug-Gruppen und Kamerawinkel, unbekannter Index → gelesen und verworfen
(`tune_index:<k>`), Nicht-Flug-Schlüssel → `tune_key:<schlüssel>`, doppelt → der spätere gewinnt
(`tune_duplicate:<schlüssel>`), Übernahme ersetzt alle Flug-Gruppen samt Kamerawinkel.

### 4.6 OSD

```
eg(0) version − 1             (Schema-Version ≥ 1, ≤ 2³¹ − 1)
bit gepackt
gepackt = 1: eg(8) bytes, dann so viele Bytes raw DEFLATE (RFC 1951) mit dem OSD-Wörterbuch (Abschnitt 5.4) als
             Preset-Dictionary; ausgepackt höchstens 16384 Bytes (sonst TOO_LARGE), gültiges UTF-8, Fehler → CORRUPT
gepackt = 0: text json
JSON über 8192 Zeichen → CORRUPT
```

Kodierer: DEFLATE Stufe 9, Fenster 15, memLevel 8, Standard-Strategie, `setDictionary(Wörterbuch)` (`java.util.zip.Deflater`
bzw. die gebündelte zlib-Portierung pako auf der Website, beide bytegleich); gepackt wird nur, wenn
`eg(8)-Länge + 8 · Bytes` **weniger** Bits braucht als `text json`.

---

## 5. Tabellen (nur anhängen)

Alle Tabellen stehen in `Pw2Tables` (Java) und `sharecode2-tables.js` (Website) und im Web-Export unter
`sharecode/spec.json` → `pw2`; `tools/test-configurator.mjs` vergleicht sie, `Pw2CodecTest` prüft Größen und
Reihenfolge.

### 5.1 Teile

Eine Tabelle je Teile-Art (Rahmen, Stack, Motor, Prop, Video, Akku, Zubehör), Ids mit Namensraum. Stand 2.0: die Teile
von Propwash 0.4.0 (je Art alphabetisch), dann die neuen Teile von Propwash 0.5.0 (`propwash:creative_fc`,
`propwash:action_cam_flashback`, `propwash:gripper`), dann die 143 Teile von JMP (je Art alphabetisch), zuletzt der
Race-Prop `propwash:p51_29_3b` (5,1″ mit 2,9″ Steigung), den Propwash 0.5.0 nach dem Playtest bekam: Nummer 43 der
Prop-Tabelle, hinter allen bisherigen, damit kein bestehender Code eine andere Nummer liest. Größen: 27 Rahmen,
16 Stacks, 40 Motoren, 44 Props, 11 Video, 35 Akkus, 5 Zubehör. Damit liest jede Version von JMP und die Website alle
Teile, die es heute gibt, als Nummer.

**Regeln:** Neue Teile (von Propwash oder JMP) kommen **ans Ende** der Tabelle ihrer Art; nie umsortieren, nie löschen,
nie eine Nummer wiederverwenden. Ein umbenanntes Teil bekommt einen neuen Eintrag am Ende (der alte bleibt, die
Alias-Tabelle `PartAliases` macht ihn zum neuen). `Pw2CodecTest.tablesAreAppendOnlyAndCoverEveryPart` schlägt fehl, wenn
ein angemeldetes Teil von Propwash oder JMP in der Tabelle fehlt. Ältere Leser melden Teile, die nach ihrer Tabelle
kamen, als `?<art>.<nummer>`; das Teil fehlt dort ohnehin.

### 5.2 Farben (eingefroren)

51 Farben: die 21 Lack-Farbtöne von Propwash 0.4.0 (`PaintSwatch`: 16 Farbstoffe, Carbon, Gunmetal, Aluminium, Gold,
Kupfer) in ihrer Reihenfolge, dann die Lackfarben der JMP-Presets in Preset-Reihenfolge. Diese Tabelle wird **nie**
erweitert (ein alter Leser könnte einen neuen Index nicht in eine Farbe übersetzen); neue Farben gehen roh (24 Bit).

### 5.3 Tuning-Schlüssel

Index 1–56 wie PW1 Revision 1.2 (`pw1.md` §5.3), dazu **57 = `pid.armMaxAngleDeg`** (Propwash 0.5.0). Nur anhängen.
Unbekannte Flug-Schlüssel gehen als Text.

### 5.4 OSD-Wörterbuch (eingefroren)

Das Preset-Dictionary für DEFLATE ist die UTF-8-Folge der OSD-Layouts der vier OSD-Presets von Propwash 0.4.0, wie
`OsdLayoutCodec.encode` sie schreibt, in der Reihenfolge `full`, `standard`, `race`, `minimal` (das letzte ist der
Standard und liegt so am nächsten). 8429 Bytes, CRC-32 `0xb87eac81`. Propwash 0.5.0 schreibt dieselben Layouts. Ein
übliches Layout schrumpft damit von rund 600 Bytes (DEFLATE ohne Wörterbuch) auf 30–60 Bytes. Das Wörterbuch ist Teil
des Formats und ändert sich nie; neue OSD-Elemente kosten nur ihre eigenen Bytes.

---

## 6. Status und Grenzen

Status wie PW1 (`pw1.md` §7.1), zusätzlich: `PW3…` und höher → `UNSUPPORTED_VERSION` („Code aus einer neueren
Version“), auch in JMP für PW1-Leser neu. Grenzen wie PW1 §10 (4000 Zeichen Eingabe, 2500 Bytes Rahmen, 256
Tuning-Einträge, 8192 Zeichen OSD-JSON, 48 Zeichen Name). Ein Leser wirft nie eine Ausnahme nach außen; jede Länge wird
vor dem Lesen gegen die restlichen Bits geprüft.

---

## 7. Kompatibilität

| Programm | liest | schreibt |
|---|---|---|
| JMP-Testbauten vor dem 06.10.2026 (an Creator verteilt) | PW1 | PW1 |
| **JMP 1.0.0** (für Propwash 0.4.0, neu erstellt) | PW1, PW2 | **PW2** |
| **JMP 1.1.0** (für Propwash 0.5.0) | PW1, PW2 | **PW2** |
| Website, Stand 06.10.2026 (`main`) | PW1, PW2 | PW1 (`SHARE_FORMAT = 'PW1'`) |
| Website nach der Umstellung (`site/pw2-default`) | PW1, PW2 | **PW2** |

Ein PW1-Code bleibt für immer lesbar (`pw1.md` §11). Ein PW2-Code in einem reinen PW1-Leser ergibt
`INVALID_PREFIX`; deshalb liest die Website PW2, bevor sie PW2 schreibt. Inhalt und Übernahme sind in beiden Formaten
gleich, ein Code lässt sich verlustfrei umschreiben (`ShareCodec.encodePw1` ↔ `ShareCodec.encode`).

---

## 8. Stabilitätsregeln

1. Ein veröffentlichter PW2-Code bleibt lesbar, solange JMP existiert. Inkompatible Änderungen bekommen `PW3`.
2. Nur anhängen: Teile-Tabellen, Tuning-Schlüssel, Lack-Plätze, Zubehör-Plätze, Erweiterungs-Ebenen. Eingefroren:
   Farbtabelle, OSD-Wörterbuch, Text-Alphabet, alle Exp-Golomb-Parameter (k: Teil 4, Farbe 5, Lack-Plätze 2,
   Tuning-Anzahl 2, Tuning-Abstand 0, Wert 7, Text 3, OSD-Bytes 8, Erweiterung 6).
3. Revision steigt bei jeder Ergänzung (2.0 → 2.1); Testvektoren kommen dazu, alte bleiben bytegleich.
4. Java und JavaScript schreiben jeden Code bytegleich; die Testvektoren beweisen das in JUnit (`Pw2CodecTest`) und auf
   der Website (`tools/test-configurator.mjs`).

---

## 9. Entscheidungen (06.10.2026)

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Base62 als ganze Zahl mit fester Länge | 5,95 Bit je Zeichen, nur Buchstaben und Ziffern: ein Wort für Doppelklick, URL- und Discord-sicher | Base64url (`-` und `_`: `_…_` wird in Discord kursiv, `-` trennt beim Doppelklick); Crockford-Base32 wie PW1 (30 % länger, nur dafür tippfehlertolerant – Codes werden kopiert, nicht abgetippt); Base58 (2 % länger) |
| Präfix `PW2` ohne Bindestrich | Doppelklick erfasst den ganzen Code samt Präfix | `PW2-` (ein Doppelklick nähme nur den Teil nach dem Strich) |
| Teile als Nummer je Art, Text als Ausweg | ein Teil kostet 5–9 Bit statt 10–20 Byte; fremde Addons bleiben möglich | Bezug auf ein Preset plus Abweichungen (Presets ändern sich zwischen Versionen, ein Code hinge an der Version); eine gemeinsame Tabelle über alle Arten (2–3 Bit mehr je Teil); Hash der Id (Kollisionen) |
| Tabellen in Java-Konstanten, Export in den Web-Export | kein Laden zur Laufzeit, Server und Client gleich, Website prüft gegen den Export | Datei im Datapack (Server ohne Ressourcen, mehr Fehlerquellen) |
| Tabellen enthalten schon die Teile von Propwash 0.5.0 | JMP 1.0.0 (für 0.4.0) liest Codes aus 0.5.0-Welten mit Nummern | nur 0.4.0-Teile (0.5.0-Teile kämen dort als `?stack.4`) |
| Werte absolut, Tuning bleibt „nur Abweichungen“ | verlustfrei unabhängig von Propwash-Version und abgeleiteten PIDs | Abstand zu Standardwerten (Standards hängen vom Bauplan und der Propwash-Version ab) |
| Dezimal-Skalierung mit kürzester exakter Stellenzahl, Wiederholungen | Rates und PIDs sind kurze Dezimalzahlen, Roll/Pitch sind oft gleich | feste Skalierung ×1000 wie PW1 (3–4 Bit mehr je Wert); Schrittweiten je Schlüssel (an Propwash-Editor gebunden) |
| Farbtabelle aus Propwash-Farbtönen und Preset-Farben, eingefroren | Farbtöne aus dem Spiel kosten 7–11 statt 25 Bit; eingefroren, damit alte Leser jede Farbe kennen | erweiterbare Tabelle (alte Leser verlören neue Farben) |
| DEFLATE nur für das OSD-JSON, mit Wörterbuch | der Rest ist schon dicht gepackt (DEFLATE würde ihn verlängern); das Wörterbuch macht aus ~600 Bytes ~40 | DEFLATE über alles wie PW1; eigenes OSD-Binärformat (an das OSD-Schema von Propwash gebunden) |
| Website kodiert mit pako, dekodiert mit eigenem Inflater | bytegleich zu `java.util.zip`, synchron, läuft in jedem Browser mit BigInt | `CompressionStream` (kein Preset-Dictionary, asynchron, ältere Safari ohne `deflate-raw`) |
| 16-Bit-Prüfsumme (untere Hälfte der CRC-32) | 2 Zeichen kürzer als 32 Bit; ein zufällig beschädigter Code rutscht mit 1 : 65 536 durch und scheitert dann meist an der Struktur | 32 Bit wie PW1; keine Prüfsumme |
| Erweiterungs-Ebenen mit Bitlänge am Ende, sonst keine Längenangaben | 1 Bit Vorrat statt Längen je Ebene, spätere Ebenen bleiben für alte Leser überspringbar | Länge je Ebene wie PW1 (1–2 Byte je Ebene) |
