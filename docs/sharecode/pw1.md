# Sharecode PW1 — Spezifikation (eingefroren)

> **Eingefroren.** Diese Spezifikation ist aus Just More Parts 1.1.1 (`docs/design/sharecode.md`, Revision 1.2) übernommen
> und wird nicht mehr geändert. Seit Propwash 0.6.0 schreiben Mod und Website nur noch PW3 (`pw3.md`); PW1 wird für immer
> gelesen: `de.lardtom.propwash.share.legacy.Pw1Reader` mit `Pw1Tables`. Einen PW1-Kodierer gibt es in Propwash nicht; die
> Testvektoren dieses Dokuments und alle weiteren Codes von JMP 1.1.1 liegen als Golden-Datei in
> `common/src/test/resources/sharecode/pw1-vectors.json` (geprüft von `LegacyReadTest`). Verweise auf Klassen in
> `de.lardtom.justmoreparts.share`, auf `tools/sharecode_ref.py` und auf die JMP-Oberfläche beziehen sich auf JMP bis 1.1.1.

> Seit Just More Parts 1.0.0 (Stand 06.10.2026) schrieben JMP und die Website das kürzere Format PW2 (`pw2.md`). Ebenen
> (§1), Übernehmen (§8), Befehle (§9), Grenzen (§10) und Sicherheit (§12) galten für beide Formate.

| | |
|---|---|
| Format | **PW1** (Präfix `PW1-`) |
| Revision | 1.2 vom 2026-10-02 |
| Stand | eingefroren seit Propwash 0.6.0 (Lesen in `share.legacy.Pw1Reader`) |
| Referenz | bis JMP 1.1.1: Python `tools/sharecode_ref.py` und Java `de.lardtom.justmoreparts.share.ShareCodec`; ab Propwash 0.6.0 nur Lesen: `de.lardtom.propwash.share.legacy.Pw1Reader` |
| Gilt für | das Addon „Propwash: Just More Parts“ (JMP), den Web-Konfigurator und jedes Werkzeug, das JMP-Codes liest oder schreibt |

Ein Sharecode ist ein kurzer, kopierbarer Text, der eine Drohne aus Propwash FPV beschreibt: ihre Teile, optional die
Lackierung, das Flug-Tuning, das OSD-Layout und den Namen. Dieses Dokument ist in sich geschlossen: Wer nur diese Datei
liest, kann Codes bitgenau lesen und gültige Codes schreiben.

---

## 1. Ebenen

| Bit | Ebene | Inhalt | Beim Export standardmäßig | Quelle in Propwash (API 1.0) |
|---|---|---|---|---|
| 0 | `PARTS` | Rahmen, Stack, Motor, Prop, Video, Akku, Zubehör je Halterung | **immer** (Pflicht) | `DroneData#build()` (`BuildSpec`) |
| 1 | `PAINT` | Lackfarbe je Lack-Platz | an | `DroneData#paint()` (`PaintSpec`) |
| 2 | `TUNE` | Flug-Tuning: Rates, PID, Feedforward, TPA, Filter, Gaskurve; dazu der Kamerawinkel der Drohne (`camera.uptiltDeg`, ab Revision 1.2) | an | `DroneData#tune().only(TuneGroup.FLIGHT + TuneGroup.CAMERA)` (`ShareTables.TUNE_GROUPS`; `CAMERA` ab Propwash-API 1.4) |
| 3 | `OSD` | OSD-Layout des Spielers (Schema-Version + JSON) | **aus** | `PropwashClientApi.osd().current()` (nur Client) |
| 4 | `NAME` | Name der Drohne (1–48 Zeichen) | an, wenn die Drohne einen Namen hat | `DroneData#name()` |
| 5–31 | — | reserviert für spätere Ebenen | — | — |

**Nie enthalten** (auch nicht als Literal, auch nicht in späteren Revisionen von PW1): Schalter- und Tastenbelegung,
Funkprofile, Kanal-Zuordnung, Bindungen an Funke oder Brille, Akkustand, Verschleiß und Zustand von Teilen,
Drohnen-UUID, Videokanal (`vtx.*`), Höhen-/Positionshalter (`hold.*`), LED-Einstellungen (`led.*`) und Spielervorlieben
des OSD (Vanilla-HUD ausblenden, analoges Bildzerreißen — das OSD-Schema von Propwash enthält sie ohnehin nicht).
Maßgeblich ist `TuneGroup.of(schlüssel) ∈ TuneGroup.FLIGHT ∪ {CAMERA}`. Der Kamerawinkel (`camera.uptiltDeg`) gehört seit
Revision 1.2 dazu: In Propwash 0.4.0 ist er eine Eigenschaft der Drohne (im Wartungsmenü eingestellt, auf den
Kamerahalter des Rahmens begrenzt) statt einer Spielereinstellung, also Teil des Builds.

---

## 2. Textform

```
PW1-<Gruppe>-<Gruppe>-…-<Gruppe>
```

- Präfix `PW1-` (Großbuchstaben). `PW1` ist die Formatversion; ein inkompatibles Format bekommt `PW2-`.
- Danach die Bytes des **Rahmens** (Abschnitt 3) in **Crockford-Base32**, in Gruppen zu **5 Zeichen**, getrennt durch `-`.
  Die letzte Gruppe darf kürzer sein.
- Alphabet (Wert 0–31): `0123456789ABCDEFGHJKMNPQRSTVWXYZ` (ohne `I`, `L`, `O`, `U`).
- Bitreihenfolge: Die Bytes werden als Bitstrom geschrieben, höchstwertiges Bit zuerst; je 5 Bit ergeben ein Zeichen; die
  letzten Bits werden rechts mit Nullen auf 5 aufgefüllt.

**Einlesen (tolerant):**
1. Alle Leerzeichen, Tabulatoren und Zeilenumbrüche entfernen; in Großbuchstaben umwandeln.
2. Präfix `PW1` prüfen (der Bindestrich danach ist optional). Fehlt es → `INVALID_PREFIX`.
3. Alle `-` entfernen. `O` → `0`, `I` und `L` → `1`. Jedes andere Zeichen außerhalb des Alphabets → `BAD_CHARACTER`.
4. Zeichen zu Bits, Bits zu Bytes: `floor(5·n/8)` Bytes; übrige Bits müssen 0 sein, sonst `CORRUPT`.

Längen zur Orientierung (siehe Testvektoren): nur eingebaute Teile ≈ 140 Zeichen, JMP-Teile mit Lack, Tuning und Name
≈ 300 Zeichen, alles mit OSD ≈ 400 Zeichen. Der Minecraft-Chat nimmt höchstens 256 Zeichen an; deshalb ist die
Zwischenablage im Sharecode-Fenster der Hauptweg, der Befehl `/jmp code import` ist für kurze Codes gedacht.

---

## 3. Rahmen (Bytes)

| Versatz | Länge | Feld | Wert |
|---|---|---|---|
| 0 | 1 | Formatversion | `0x01` |
| 1 | 1 | Flags | Bit 0 = Nutzdaten mit **raw DEFLATE** (RFC 1951, ohne zlib-Kopf) komprimiert; Bits 1–7 müssen 0 sein |
| 2 | n | Nutzdaten | Abschnitt 4 (komprimiert, wenn Flag-Bit 0 gesetzt) |
| 2+n | 4 | Prüfsumme | **CRC-32** (IEEE 802.3, Polynom `0xEDB88320` gespiegelt, wie `java.util.zip.CRC32`, `zlib.crc32`, `crc32` in JavaScript-Bibliotheken) über die Bytes 0 bis 1+n, **big-endian** |

- Stimmt die Prüfsumme nicht → `CHECKSUM` (keine weitere Auswertung).
- Formatversion ≠ 1 oder gesetzte reservierte Flag-Bits → `UNSUPPORTED_VERSION`.
- Mindestlänge des Rahmens: 7 Bytes, sonst `TRUNCATED`.

---

## 4. Nutzdaten

```
varint   ebenenMaske
für jedes gesetzte Bit b der Maske, aufsteigend:
    varint  länge
    bytes   block[länge]
(danach dürfen keine Bytes mehr folgen)
```

- Bit 0 (`PARTS`) **muss** gesetzt sein, sonst `CORRUPT`.
- Jeder Block trägt seine Länge. Ein Decoder, der ein Bit nicht kennt (≥ 5 in PW1 Revision 1.2), überspringt den Block
  und vermerkt `layer:<bit>` in der Liste `skipped`.
- Ein bekannter Block muss genau seine Länge verbrauchen: bleiben Bytes übrig → `CORRUPT`; liest der Decoder über das
  Blockende hinaus → `TRUNCATED` (wie bei einem Rahmen, der vorzeitig endet).
- Nach dem letzten Block folgende Bytes → `CORRUPT`.

### 4.1 Grundtypen

| Typ | Kodierung |
|---|---|
| `u8` | ein Byte |
| `u16` | zwei Bytes, big-endian |
| `varint` | vorzeichenlos, LEB128: je Byte 7 Bit, niederwertige Gruppe zuerst, Bit 7 = „es folgt noch ein Byte“; höchstens 5 Bytes und Wert < 2³², sonst `CORRUPT` |
| `zigzag` | vorzeichenbehaftete Zahl z als varint von `(z << 1) ^ (z >> 63)` |
| `str` | `varint` Bytelänge + UTF-8-Bytes (ungültiges UTF-8 → `CORRUPT`) |
| `ref` | Teil-Id: `varint ns`; ist `ns = 0`, folgt `str namensraum` (1–64 Zeichen `[a-z0-9_.-]`, nicht `..`); danach immer `str pfad` (1–128 Zeichen `[a-z0-9_.-]`); andere Zeichen oder Längen → `CORRUPT` |
| `f32` | IEEE-754 binary32, big-endian |

**Namensraum-Tabelle PW1** (nur anhängen, nie umnummerieren):

| `ns` | Namensraum |
|---|---|
| 0 | Literal (Namensraum folgt als `str`) |
| 1 | `propwash` |
| 2 | `justmoreparts` |

Ein `ns` > 2, den ein Decoder nicht kennt, macht die Id unlesbar: Der Pfad wird trotzdem gelesen (Byte-Grenzen bleiben
gültig), das Teil gilt als unbekannt (`skipped` bekommt `namespace:<ns>` einmal je Nummer, die Id bleibt `?<ns>:<pfad>` und
steht in `unknownParts`; das gilt auch für Zubehör, das dann nicht als `accessory_slot:` übersprungen wird). Die Java-Seite
hält solche Ids intern als `pw1.unknown.<ns>:<pfad>` und schreibt beim erneuten Kodieren wieder dieselbe Nummer `ns`.

---

## 5. Blöcke

### 5.1 `PARTS` (Bit 0)

```
ref frame
ref stack
ref motor        (gilt für alle vier Motoren)
ref prop         (gilt für alle vier Props)
ref video
ref battery
u8  zubehörAnzahl   (0–2)
wiederholt: u8 platz, ref zubehör
```

| `platz` | Zubehör-Halterung (`AccessorySlot#key`) |
|---|---|
| 0 | `top` |
| 1 | `bottom` |

Unbekannter Platz → Eintrag wird gelesen und verworfen, `skipped` bekommt `accessory_slot:<n>`. Derselbe Platz zweimal →
der spätere gewinnt, `skipped` bekommt `accessory_slot_duplicate:<n>`. Eine `zubehörAnzahl` über 2 ist lesbar (jeder Eintrag
wird nach diesen Regeln behandelt); ein Encoder schreibt nie mehr als 2.

### 5.2 `PAINT` (Bit 1)

```
u16  platzMaske
für jedes gesetzte Bit i aufsteigend: u8 R, u8 G, u8 B
```

| Bit | Lack-Platz (`PaintSpec.SLOTS`) | Bit | Lack-Platz |
|---|---|---|---|
| 0 | `frame` | 6 | `prop_rr` |
| 1 | `tpu` | 7 | `stack` |
| 2 | `motors` | 8 | `battery` |
| 3 | `prop_fl` | 9 | `camera` |
| 4 | `prop_fr` | 10 | `antenna` |
| 5 | `prop_rl` | 11 | `accessories` |

Bits 12–15 sind reserviert: Ein Decoder liest die drei Farbbytes, verwirft sie und vermerkt `paint_slot:<i>`.
Ein nicht gesetztes Bit heißt „Originalfarbe“ (wie ein fehlender Eintrag in `PaintSpec`). Eine leere Lackierung wird
nicht als Block geschrieben (Bit 1 bleibt aus).

### 5.3 `TUNE` (Bit 2)

```
varint anzahl                      (0–256)
wiederholt:
    varint kopf                    k = kopf >> 1, roh = kopf & 1
    falls k = 0: str schlüssel     (Literal)
    sonst:       Schlüssel = Tabelle[k] (1-basiert)
    falls roh = 0: zigzag q        Wert = (float) (q / 1000.0)
    falls roh = 1: f32 wert
```

**Schlüssel-Tabelle PW1** (Index → Tuning-Schlüssel wie im Flight-Controller von Propwash; nur anhängen):

| k | Schlüssel | k | Schlüssel | k | Schlüssel |
|---|---|---|---|---|---|
| 1 | `rates.type` | 20 | `rates.rcDeadband` | 39 | `pid.antiGravityGain` |
| 2 | `rates.roll.rcRate` | 21 | `rates.yawDeadband` | 40 | `pid.airmode` |
| 3 | `rates.roll.superRate` | 22 | `pid.source` | 41 | `pid.tpaMode` |
| 4 | `rates.roll.rcExpo` | 23 | `pid.roll.p` | 42 | `pid.tpaRatePercent` |
| 5 | `rates.roll.centerRateDps` | 24 | `pid.roll.i` | 43 | `pid.tpaBreakpoint` |
| 6 | `rates.roll.maxRateDps` | 25 | `pid.roll.d` | 44 | `pid.motorIdlePercent` |
| 7 | `rates.roll.actualExpo` | 26 | `pid.roll.ff` | 45 | `pid.throttleMid` |
| 8 | `rates.pitch.rcRate` | 27 | `pid.pitch.p` | 46 | `pid.throttleExpo` |
| 9 | `rates.pitch.superRate` | 28 | `pid.pitch.i` | 47 | `pid.throttleLimitType` |
| 10 | `rates.pitch.rcExpo` | 29 | `pid.pitch.d` | 48 | `pid.throttleLimitPercent` |
| 11 | `rates.pitch.centerRateDps` | 30 | `pid.pitch.ff` | 49 | `pid.thrustLinearPercent` |
| 12 | `rates.pitch.maxRateDps` | 31 | `pid.yaw.p` | 50 | `pid.vbatSagCompensationPercent` |
| 13 | `rates.pitch.actualExpo` | 32 | `pid.yaw.i` | 51 | `filters.pidLoop` |
| 14 | `rates.yaw.rcRate` | 33 | `pid.yaw.d` | 52 | `filters.gyroLpfHz` |
| 15 | `rates.yaw.superRate` | 34 | `pid.yaw.ff` | 53 | `filters.dtermLpf1Hz` |
| 16 | `rates.yaw.rcExpo` | 35 | `pid.angleStrength` | 54 | `filters.dtermLpf2Hz` |
| 17 | `rates.yaw.centerRateDps` | 36 | `pid.angleLimitDeg` | 55 | `filters.rcSmoothingHz` |
| 18 | `rates.yaw.maxRateDps` | 37 | `pid.horizonStrength` | 56 | `camera.uptiltDeg` (ab 1.2) |
| 19 | `rates.yaw.actualExpo` | 38 | `pid.itermRelax` | | |

Regeln:
- **Nur Flug-Gruppen und Kamerawinkel**: Ein Schlüssel wird nur geschrieben und nur übernommen, wenn
  `TuneGroup.of(schlüssel)` in `TuneGroup.FLIGHT` liegt (Rates, PID, Feedforward, TPA, Filter, Gaskurve) oder `CAMERA`
  ist (`camera.uptiltDeg`, Revision 1.2). Ein Decoder verwirft andere Schlüssel (`skipped`: `tune_key:<schlüssel>`), auch
  wenn sie in einem von Hand gebauten Code stehen.
- Unbekannter Tabellen-Index (k > 56 in Revision 1.2) → Wert wird gelesen und verworfen, `skipped`: `tune_index:<k>`. Ein
  Decoder der Revision 1.1 liest einen Code mit Kamerawinkel deshalb weiter und meldet nur `tune_index:56` (Vektor G).
- Kamerawinkel: geschrieben wird er nur, wenn die Drohne einen eigenen hat (Propwash speichert den Standard des Rahmens
  nicht). Fehlt er im Code, gilt beim Übernehmen der Standard des Rahmens – alte Codes bleiben so gültig. Propwash
  begrenzt jeden übernommenen Winkel auf den Kamerahalter des Zielrahmens.
- `anzahl` über 256, ein Literal-Schlüssel mit 0 oder mehr als 64 Zeichen oder ein Wert, der nicht endlich ist (NaN,
  unendlich), → `CORRUPT`.
- Neue Flug-Schlüssel einer späteren Propwash-Version schreibt ein PW1-Encoder als Literal (k = 0); bekommt die Tabelle
  später einen Index für sie, lesen alte Decoder den neuen Index als unbekannt, neue lesen beide Formen.
- Wert „skaliert“ (`roh = 0`) genau dann, wenn `q = round(v · 1000)` mit |q| < 2³¹ und `(float) (q / 1000.0) == v`
  (bitgleich als float32, also samt Vorzeichen: −0,0 wird immer roh geschrieben); sonst `roh = 1`. Alle Schrittweiten des
  Konfigurators (≥ 0,005) sind so verlustfrei. Die Rundung bei genau ,5 ist gleichgültig: Ein solcher Wert ist nie
  bitgleich darstellbar und wird ohnehin roh geschrieben.
- Reihenfolge beim Schreiben: Tabellen-Einträge nach Index aufsteigend, danach Literale nach Schlüssel (Unicode-Ordnung).
  Doppelte Schlüssel beim Lesen: der spätere gewinnt, `skipped`: `tune_duplicate:<schlüssel>`.
- Inhalt: das **gespeicherte** Tuning (nur Werte, die vom Standard abweichen, abgeleitete PIDs fehlen). Ein fehlender
  Schlüssel heißt „Standardwert des Zielbauplans“. Beim Übernehmen ersetzt der Code deshalb **alle** Flug-Gruppen
  (`TuneSpec#overlay(importiert, TuneGroup.FLIGHT)`), damit zurückgesetzte Werte auch auf der Zieldrohne zurückgesetzt
  werden; seit Revision 1.2 gehört `CAMERA` mit zu den ersetzten Gruppen (`ShareTables.TUNE_GROUPS`). Ein TUNE-Block mit
  `anzahl = 0` bedeutet „Flug-Tuning und Kamerawinkel auf Standard“ und ist gültig.

### 5.4 `OSD` (Bit 3)

```
varint schemaVersion     (OsdLayoutSpec#version; Propwash 0.4.0: 1)
str    json              (höchstens 8192 Zeichen, kompakt ohne Leerraum geschrieben)
```

`schemaVersion` muss mindestens 1 sein (höchstens 2³¹ − 1), das JSON höchstens 8192 Zeichen lang, sonst `CORRUPT`.
Das JSON wird nicht umgebaut, nur weitergereicht: Übernahme mit
`PropwashClientApi.osd().apply(new OsdLayoutSpec(version, json))`; Propwash begrenzt und prüft die Werte selbst (unbekannte
Elemente und Felder werden übersprungen). Liefert `apply` `false`, meldet das Fenster „OSD-Layout ungültig“.

### 5.5 `NAME` (Bit 4)

`str name` — 1 bis 48 Zeichen nach `strip()` (`DroneData.MAX_NAME_LENGTH`). Längere Namen → `CORRUPT`. Beim Erzeugen
des Drohnen-Items kürzt Propwash selbst auf 24 Zeichen.

---

## 6. Kodieren

1. `PARTS` immer; `PAINT` nur bei nicht leerer Lackierung und gewählter Ebene; `TUNE` bei gewählter Ebene (auch wenn
   leer); `OSD` nur, wenn gewählt und auf dem Client verfügbar; `NAME`, wenn gewählt und vorhanden.
2. Zubehör in der Reihenfolge der Plätze (`top`, dann `bottom`).
3. Namensräume `propwash` und `justmoreparts` als Tabellen-Index, alle anderen als Literal.
4. Kompression „automatisch“: raw DEFLATE, Stufe 9, Standard-Strategie; genommen wird die komprimierte Form nur, wenn sie
   **echt kürzer** ist. Für Tests gibt es die Modi „nie“ und „immer“.
5. Bei gleicher Eingabe und Modus „nie“ ist der Code bytegleich über alle Implementierungen (Testvektoren A, B, D).
   Komprimierte Codes dürfen sich zwischen DEFLATE-Implementierungen unterscheiden; verbindlich ist nur, dass jeder
   Decoder sie liest (Testvektor C).

---

## 7. Dekodieren

### 7.1 Status

| Status | Bedeutung | Anzeige |
|---|---|---|
| `OK` | gelesen (auch mit Einträgen in `skipped`/`unknownParts`) | Vorschau + Teile-Check |
| `EMPTY` | leere Eingabe | nichts |
| `INVALID_PREFIX` | kein `PW1` am Anfang | „Kein JMP-Sharecode“ |
| `BAD_CHARACTER` | Zeichen außerhalb des Alphabets | „Ungültiges Zeichen im Code“ |
| `TOO_LONG` | mehr als 4000 Zeichen nach dem Entfernen von Leerraum | „Code zu lang“ |
| `TRUNCATED` | Rahmen oder Block endet vorzeitig | „Code unvollständig“ |
| `CHECKSUM` | CRC-32 stimmt nicht | „Code beschädigt (Tippfehler?)“ |
| `UNSUPPORTED_VERSION` | Formatversion/Flags unbekannt | „Code aus einer neueren Version“ |
| `TOO_LARGE` | entpackte Nutzdaten über 16384 Bytes | „Code zu groß“ |
| `CORRUPT` | sonstige Formatfehler (Längen, varint, UTF-8, Ids, Name, Tuning-Werte, OSD, DEFLATE-Strom fehlerhaft, unvollständig oder mit Resten dahinter) | „Code beschädigt“ |

Ein Decoder wirft nie eine Ausnahme nach außen und blockiert nie; alle Längen werden vor dem Lesen gegen die Restlänge
geprüft (kein Speicher-Anlegen nach Angaben aus dem Code).

### 7.2 Ergebnis bei `OK`

| Feld | Inhalt |
|---|---|
| `layers` | die gelesenen, bekannten Ebenen |
| `build` | `BuildSpec` (Ids so, wie sie im Code stehen, nach Anwendung der Alias-Tabelle 7.3) |
| `paint`, `tune`, `osd`, `name` | falls vorhanden |
| `unknownParts` | Ids, die dieses Spiel nicht kennt (`PropwashApi.builds().check(build).unknownParts()`): Addon fehlt oder ist zu alt |
| `wrongKind` | Ids, die bekannt sind, aber zu einer anderen Kategorie gehören als ihr Feld (z. B. ein Motor im Rahmen-Feld) |
| `renamed` | Paare alt → neu aus der Alias-Tabelle |
| `skipped` | übersprungene Einträge (`layer:`, `namespace:`, `accessory_slot:`, `paint_slot:`, `tune_index:`, `tune_key:`, `…_duplicate:`) |

Danach zeigt das Fenster: `problems`/`warnings` aus `builds().check(build)`, die Analyse aus `builds().analyze(build)`
(nur wenn alle Teile bekannt sind) und die Vorschau (`PropwashClientApi.renderPreview`). Übernehmbar ist die Ebene
`PARTS` nur, wenn `unknownParts` und `wrongKind` leer sind; `PAINT`, `TUNE`, `OSD` gehen immer (auf die gehaltene Drohne
bzw. clientseitig).

Platz im Fenster: Beim Export lässt der Code-Kasten drei Zeilen für die Werte der Drohne frei, sofern ihm selbst
mindestens zwei Zeilen bleiben. Passt der Code nicht hinein, scrollt der Kasten zeilenweise mit dem Mausrad (Scrollleiste
rechts, der Code bricht dafür etwas schmaler um); die Werte darunter scrollen ebenso, beim Import die Prüfergebnisse.
Nichts wird weggelassen – geprüft im Selbsttest `share: Werkbank → Code → Import` bei der kleinsten GUI des Fensters.

### 7.3 Alias-Tabelle

`share.PartAliases` (Java) und `aliases` im Web-Export: alte Id → neue Id für umbenannte JMP-Teile. Beim Dekodieren
ersetzt, in `renamed` gemeldet, beim Kodieren nie benutzt. Revision 1.2: leer.

---

## 8. Übernehmen (Abbildung auf die Propwash-API 1.0)

| Auswahl im Fenster | Aufruf | Bedingung |
|---|---|---|
| Ganzer Bauplan (Werkbank offen) | `WorkbenchContext#importIntoWorkbench(data, Ebenen, ShareTables.TUNE_GROUPS)` mit Ebenen aus `PARTS`/`PAINT`/`TUNE` der Auswahl, dazu `NAME`, wenn Teile übernommen werden und der Code einen Namen hat (Parts-API 1.4) | Werkbank-Kind-Fenster |
| Nur Tuning auf die gehaltene Drohne | `PropwashClientApi.sendImport(new ImportRequest(data, {TUNE}, ShareTables.TUNE_GROUPS, HELD_DRONE))` | Drohne in der Haupthand |
| Nur Lackierung auf die gehaltene Drohne | `PropwashClientApi.sendImport(ImportRequest.paintOnHeldDrone(data))` | Drohne in der Haupthand |
| OSD-Layout übernehmen | `PropwashClientApi.osd().apply(…)` | nur Client, immer |
| Befehl (Server) | `PropwashApi.builds().applyImport(spieler, request)` bzw. im Kreativmodus `createDrone(data)` ins Inventar | Abschnitt 9 |

`data` ist `new DroneData(build, paint oder PaintSpec.DEFAULT, tune oder TuneSpec.EMPTY, name)`.

**Oberflächen in JMP (Umsetzung):**

| Ort | Inhalt |
|---|---|
| Werkbank, **ein** Knopf „Sharecode“ im Kopf | Beschriftung nach Platz (Parts-API 1.4): Propwash reserviert Marke und Titel „Drohnen-Werkbank“ und meldet den Rest (`WorkbenchContext#headerSpace`); passt „Code“, bekommt der Knopf „Sharecode“ („Share code“) mit Kurzform „Code“ (`addButton` mit `shortLabel`, Propwash wählt), sonst ist er ein 13 × 13-Symbol `</>` (`addIconButton`, Sprite `justmoreparts:workbench/share_code`). Auf Deutsch ist es meist das Symbol, der Tooltip nennt „Sharecode“ und erklärt den Knopf. Öffnet das Fenster „Sharecode“ auf „Exportieren“, wenn die Werkbank eine fertige Drohne hat, sonst auf „Importieren“; die Reiter oben rechts wechseln |
| Fenster „Sharecode“, Reiter „Exportieren“ | Ebenen-Häkchen Lack/Tuning/OSD/Name („Name“ ist ausgegraut, wenn die Drohne keinen Namen hat; der Tooltip sagt das), Code in Fünfergruppen ohne zusätzliche Leerzeichen, Umbruch nur an Bindestrichen, Kasten so hoch wie der Code, darunter „Im Code: …“ und die Flugwerte der Drohne, „In die Zwischenablage“ |
| Fenster „Sharecode“, Reiter „Importieren“ | Eingabefeld (Einfügen-Knopf, Dekodieren beim Tippen), Vorschau, Teile-Check (Art nur, wenn der Teilname sie nicht schon nennt: „4× 2207,5-Motor, 1850 KV“, aber „Video: Clarity HD Pro …“), Probleme/Warnungen, Flugwerte, weitere Ebenen, **Einkaufsliste** (Überleben: benötigte Stückzahl je Teil, Fehlendes nach den Regeln des Servers über `WorkbenchContext#missingParts`, also Werkbank-Slots und Inventar; Kreativ: Hinweis, dass Teile erzeugt werden), Ebenenwahl Teile/Lack/Tuning/OSD, „Übernehmen“. Die Liste zeigt nur ganze Zeilen und bricht vor der Bildlaufleiste um |
| Konfigurator-Tab `justmoreparts:sharecode` | Export der Drohne in der Haupthand (Ebenen wie im Fenster), Import-Feld mit Kurzfassung, „Tuning übernehmen“, „Lack übernehmen“ (gehaltene Drohne), „OSD übernehmen“, im Kreativmodus „Drohne erzeugen“ (`PropwashClientApi.sendCreateDrone`, der Server prüft Kreativ/Operator und legt die Drohne samt Name ins Inventar), im Überlebensmodus „Einkaufsliste“ (nur Inventar) und die **Lackkosten** der gehaltenen Drohne vorab (`BuildAccess#paintCost`) |

Den Namen übernimmt der Werkbank-Import seit Parts-API 1.4 zusammen mit dem Bauplan (`ImportLayer.NAME`, er landet auf
dem Rahmen-Item). Befehl und Kreativ-Knopf erzeugen die Drohne mit Namen (`createDrone` bzw. `sendCreateDrone`). Der Server entscheidet
(Überleben: Teile kosten Teile aus dem Inventar, Lack kostet Farbstoff). Das Ergebnis (`ImportResult`) wird mit
`Component.translatable(result.status().translationKey())` angezeigt; `MISSING_PARTS` listet die fehlenden Teile je
Stück (`missingParts`), `MISSING_DYES` die Farbstoffe. `BUSY` (mehr als 2 Anfragen je Sekunde) → Knopf nach einer
Sekunde wieder freigeben.

---

## 9. Befehl

`/jmp code export [<ebenen>]` (Spieler, Drohne in der Haupthand): schreibt den Code als anklickbare Chat-Nachricht
(Klick kopiert in die Zwischenablage). `<ebenen>` z. B. `parts,paint,tune,name` (Standard wie Tabelle 1, ohne OSD — der
Server kennt das OSD-Layout nicht).
`/jmp code import <code>` (Spieler): Kreativmodus → neue Drohne ins Inventar (`createDrone`), Überleben → Tuning und Lack
auf die gehaltene Drohne (`applyImport`); Teile nur über die Werkbank.
`/jmp code info <code>`: dekodiert und zeigt Ebenen, Teile, unbekannte Teile und Probleme, ohne etwas zu ändern.
`/jmp code give <code> [<spieler>]` (Operatoren, Stufe 2): erzeugt die Drohne des Codes (Teile, Lack, Tuning, Name) im
Inventar der Ziel-Spieler (Standard: man selbst), unabhängig vom Spielmodus.
Rechte: Stufe 0 für `export`/`info`, `import` wie die Werkbank (Propwash prüft Zuschauermodus und Rechte selbst), Stufe 2
für `give`. `/justmoreparts …` ist ein gleichwertiger Name für `/jmp …`.

---

## 10. Grenzen

| Grenze | Wert |
|---|---|
| Eingabetext (ohne Leerraum) | 4000 Zeichen |
| Rahmen | 2500 Bytes |
| entpackte Nutzdaten | 16384 Bytes |
| Tuning-Einträge | 256 (`TuneSpec.MAX_ENTRIES`) |
| Tuning-Schlüssel als Literal | 1–64 Zeichen |
| OSD-JSON | 8192 Zeichen |
| Name | 48 Zeichen |
| Id-Pfad | 128 Zeichen, nur `[a-z0-9_.-]`; Namensraum `[a-z0-9_.-]` |

---

## 11. Stabilitätsregeln

1. Ein veröffentlichter PW1-Code bleibt lesbar, solange JMP existiert. Inkompatible Änderungen bekommen ein neues
   Präfix (`PW2-`); JMP liest dann PW1 und PW2.
2. **Nur anhängen:** Namensraum-Tabelle, Zubehör-Plätze, Lack-Plätze (Bits 12–15 frei), Ebenen-Bits (5–31 frei),
   Tuning-Schlüssel-Tabelle (ab 57). Nie umnummerieren, nie wiederverwenden, nie löschen.
3. Jede Erweiterung ist so gebaut, dass alte Decoder sie überspringen können (Längen je Ebene, feste Größen je Lack-Bit,
   Wert-Kodierung unabhängig vom Schlüssel).
4. JMP-Teil-Ids sind ab der ersten Veröffentlichung eingefroren. Umbenennen nur mit Eintrag in der Alias-Tabelle.
   Entfernte Teile bleiben als Id gültig und werden als unbekannt gemeldet.
5. Die Revision dieses Dokuments steigt bei jeder Ergänzung (1.0 → 1.1); die Ergänzung steht in einer Tabelle
   „Änderungen“ am Ende. Neue Testvektoren kommen dazu, alte bleiben.
6. Testvektoren A–G sind Pflichttests in JUnit (`share/ShareCodecTest`) und im Web-Konfigurator.

---

## 12. Sicherheit

- Codes sind Daten, nie Befehle: kein Nachladen, keine Pfade, keine Ausführung.
- Der Server prüft jede Übernahme selbst (Propwash `applyImport`: Rate, Ziel, Rechte, Kosten). Das Addon schickt nur
  `ImportRequest`s über die Propwash-API, kein eigenes Netzwerkpaket.
- Anzeige unbekannter Ids und Namen nur als Literal-Text (`Component.literal`), nie als Übersetzungsschlüssel oder mit
  Formatierung.

---

## 13. Testvektoren

Erzeugt mit `python3 tools/sharecode_ref.py`. Die Java-Implementierung muss A, B und D mit Kompression „nie“ bytegleich
erzeugen und alle Vektoren genau so dekodieren. Die Web-Export-Datei `sharecode/vectors.json` ergänzt Vektoren mit den
erwarteten Analysewerten (`docs/design/web-export.md`).

#### A — nur Teile, unkomprimiert (Kompression „nie“)

```
PW1-04002-GR11D-K74SB-5EDT7-JV35B-WTG22-3KEHG-P6TTZ-6CR02-2KD68-S30DT-Z64VK-AC010-SR3AC-AZ6DH-022V1-DSGPR-VV7BX-S62RV-50446-4DKKB-WRK6C-1G000-H23NQ
```

Dekodiert (Referenz `tools/sharecode_ref.py`, Werte als float32):

```json
{"build": {"accessories": {}, "battery": "propwash:b6s_1300", "frame": "propwash:freestyle_5", "motor": "propwash:m2207_1750", "prop": "propwash:p51_3b", "stack": "propwash:stack_30", "video": "propwash:analog_race"}, "layers": ["parts"], "skipped": []}
```

#### B — Teile, Lack, Tuning (ein Wert roh als float32), Name, unkomprimiert

```
PW1-0401E-SG215-J6ARB-4CDGQ-8QSN0-87Q6X-31CDN-NYCSG-BWV76-QSP61-GG42V-D68S3-0DSNB-WRKGD-9G086-Q0C9K-61FK6-DJZ6D-H5YRK-90876-8TB7D-5T62V-2ZD1J-5YW3J-DW0GG-RHPED-FK2CS-G600G-008GC-5HQ8T-BFDSF-P6RBD-BXP6J-SV8EG-A00Z8-V3MHE-HDH2Z-XX1NZ-VT3BZ-QM6QZ-F8D1M-1R4W0-911R0-J3JT1-0BM0X-R2NHY-2NBCZ-EKR23-D2GC4-7GD1H-26ARB-4CDGQ-882MC-NSQ8M-TPZ1K-0
```

Dekodiert (Referenz `tools/sharecode_ref.py`, Werte als float32):

```json
{"build": {"accessories": {"top": "propwash:action_cam_light"}, "battery": "propwash:b6s_1300", "frame": "justmoreparts:deadcat_5", "motor": "justmoreparts:m22075_1850", "prop": "justmoreparts:p130_36_3b_bi", "stack": "justmoreparts:stack_30_6s_60a", "video": "justmoreparts:digital_hd_pro"}, "layers": ["parts", "paint", "tune", "name"], "name": "Deadcat Test", "paint": {"frame": 1776930, "motors": 15250978, "prop_fl": 16742938, "prop_fr": 16742938, "prop_rl": 16742938, "prop_rr": 16742938}, "skipped": [], "tune": {"filters.gyroLpfHz": 250.0, "pid.motorIdlePercent": 5.5, "pid.roll.p": 48.0, "pid.throttleMid": 0.4567891061306, "rates.pitch.rcRate": 1.2000000476837158, "rates.roll.rcRate": 1.2000000476837158, "rates.yaw.rcRate": 1.0499999523162842}}
```

#### C — alle Ebenen inklusive OSD, DEFLATE (Kompression „immer“)

```
PW1-040S6-3V2WA-P4HKJ-95GQ8-WDSMC-R92GB-J99K7-8WDSP-H2VG1-WGD1G-99JE3-K9P60-RKF29-M60S6-041E6-824Q6-0VRMC-JFHS6-4MAJF-SF565-RACSX-6CJB2-9SY4W-MAK0W-9503V-9G60R-30R74-CHG50-M2QTZ-7BQH6-DS38Q-F7W8W-B1J1S-MQGJ7-2SQMD-KBAMM-SJEAA-13TK8-18PMW-BAH69-9AMJG-C9SAN-4MEA9-N4WTK-EDDFM-HCCKA-JTN6W-20D9N-0Q2CH-KTKP8-N2JAJ-DTN8M-NKPF0-5NJQJ-HF0Z9-4DDDN-PQE5R-4DTSR-G5EAR-R381G-01PT8-BDG
```

Dekodiert (Referenz `tools/sharecode_ref.py`, Werte als float32):

```json
{"build": {"accessories": {}, "battery": "justmoreparts:b8s_5000", "frame": "justmoreparts:xclass_13", "motor": "justmoreparts:m4214_400", "prop": "justmoreparts:p330_70_2b_carbon", "stack": "justmoreparts:stack_30_8s_100a", "video": "justmoreparts:digital_lr"}, "layers": ["parts", "paint", "tune", "osd", "name"], "name": "X-Class 13", "osd": {"json": "{\"v\":1,\"preset\":\"race\",\"elements\":{\"speed\":{\"on\":true,\"col\":1,\"row\":12}}}", "version": 1}, "paint": {"battery": 3108822, "frame": 2105376}, "skipped": [], "tune": {"pid.tpaRatePercent": 40.0, "rates.roll.centerRateDps": 120.0, "rates.roll.maxRateDps": 420.0}}
```

#### D — fremder Namensraum als Literal, Tuning-Schlüssel als Literal

```
PW1-0400A-JG019-QQ8T3-5E9GP-8S3FD-R3PCW-K1DNJ-NYY01-11SQ8-RB3DD-FK6C0-119PK-4CHG6-XFK2D-SN600-GCW1N-65FK6-RG11D-GPWRB-CDXKN-YWK1C-DJG22-326SS-NYC9K-60R00-6023K-80Y00-GE1MP-8BKEC-NVMCV-39CXM-78JV5-F7R2W-8RFB6-G0
```

Dekodiert (Referenz `tools/sharecode_ref.py`, Werte als float32):

```json
{"build": {"accessories": {}, "battery": "propwash:b6s_1300", "frame": "otheraddon:frame_x", "motor": "propwash:m2207_1750", "prop": "propwash:p51_3b", "stack": "propwash:stack_30", "video": "propwash:analog_race"}, "layers": ["parts", "tune"], "skipped": [], "tune": {"pid.newFlightKey": 3.0, "rates.yaw.rcRate": 1.0}}
```

#### E — von Hand gebaut: unbekannter Zubehör-Platz, Lack-Platz, Tuning-Index, Nicht-Flug-Schlüssel und unbekannte Ebene

```
PW1-0404E-KR11D-K74SB-5EDT7-JV35B-WTG22-3KEHG-P6TTZ-6CR02-2KD68-S30DT-Z64VK-AC010-SR3AC-AZ6DH-022V1-DSGPR-VV7BX-S62RV-50446-4DKKB-WRK6C-1G042-G22BC-CNJ5Y-WVME9-MQ021-0048J-4CT4A-NK1E0-WG0ER-6T00B-EST7G-BK3D1-GPWVK-5DJ84-WM6G1-W1G20-G3RA7-XN5R
```

Dekodiert (Referenz `tools/sharecode_ref.py`, Werte als float32):

```json
{"build": {"accessories": {}, "battery": "propwash:b6s_1300", "frame": "propwash:freestyle_5", "motor": "propwash:m2207_1750", "prop": "propwash:p51_3b", "stack": "propwash:stack_30", "video": "propwash:analog_race"}, "layers": ["parts", "paint", "tune"], "paint": {"frame": 1122867}, "skipped": ["accessory_slot:5", "paint_slot:13", "tune_index:200", "tune_key:vtx.channel", "layer:6"], "tune": {"pid.airmode": 1.0}}
```

#### F — Vektor A mit einem vertauschten Zeichen

```
PW1-04002-1R11D-K74SB-5EDT7-JV35B-WTG22-3KEHG-P6TTZ-6CR02-2KD68-S30DT-Z64VK-AC010-SR3AC-AZ6DH-022V1-DSGPR-VV7BX-S62RV-50446-4DKKB-WRK6C-1G000-H23NQ
```

Erwartet: Status `CHECKSUM`.

#### G — Teile und Tuning mit eigenem Kamerawinkel (Revision 1.2), unkomprimiert

```
PW1-0400A-FG10S-S62RV-5BWTG-223KE-HGP6T-TZ6CR-022KD-68SK0-DJZ64-W3CC0-10SR3-ACAZ6-DH022-V1DSG-PRVV7-BXS62-RV504-464DK-KBWRK-2C1G0-04041-4R25R-C1B06-GN0X9-4R
```

Dekodiert (Referenz `tools/sharecode_ref.py`, Werte als float32):

```json
{"build": {"accessories": {}, "battery": "propwash:b6s_1100", "frame": "propwash:race_5", "motor": "propwash:m2306_1860", "prop": "propwash:p51_3b", "stack": "propwash:stack_30", "video": "propwash:analog_race"}, "layers": ["parts", "tune"], "skipped": [], "tune": {"camera.uptiltDeg": 52.0, "rates.roll.rcRate": 1.100000023841858}}
```

Ein Decoder der Revision 1.1 liest denselben Code mit `skipped: ["tune_index:56"]` und ohne Kamerawinkel (Standard des
Rahmens).

---

## Änderungen

| Revision | Datum | Änderung |
|---|---|---|
| 1.0 | 2026-09-29 | erste Fassung |
| 1.2 | 2026-10-02 | Kamerawinkel der Drohne im TUNE-Block (Schlüssel `camera.uptiltDeg`, Tabellen-Index 56, Gruppe `CAMERA`), Übernahme ersetzt Flug-Gruppen und Kamerawinkel (fehlt er: Standard des Rahmens); Vektor G. Vektoren A–F bleiben bytegleich gültig, ältere Decoder lesen G und überspringen nur den Winkel |
| 1.1 | 2026-09-30 | Präzisierungen ohne Formatänderung (alle Vektoren bleiben gültig): Lesen über ein Blockende → `TRUNCATED`; varint höchstens 5 Bytes und < 2³²; Zeichen und Längen von Namensraum und Pfad; unbekannter Namensraum auch im Zubehör, `namespace:<ns>` einmal je Nummer; Tuning-Anzahl, Literal-Länge und nicht endliche Werte → `CORRUPT`; −0,0 roh; OSD-Version ≥ 1; DEFLATE-Fehler → `CORRUPT`; Befehl `give` und Name `/justmoreparts` |
