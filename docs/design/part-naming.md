# Teilnamen — ein Schema für Propwash und Addons

| | |
|---|---|
| Gilt für | alle Drohnenteile von Propwash FPV, Propwash: Just More Parts und weiteren Addons (Sprachschlüssel `part.<ns>.<pfad>`, Rahmen-Kurznamen, Bestenlisten-Klassen, Addon-Presets, Halterungen, Zellchemien, Shift-Kurzbeschreibung) |
| Sprachen | `en_us` und `de_de` (Deutsch mit Umlauten und ß) |
| Referenz | die eingebauten Propwash-Namen, die der Besitzer als Vorbild gesetzt hat: „6S 1300 mAh Battery“, „1404 Motor, 4600 KV“, „31 mm Propeller (3-blade)“ |
| Stand | Propwash 0.4.0, Just More Parts 1.0.0 (Kurzbeschreibungen nach §6 vereinheitlicht, 2026-10-02) |
| Prüfung | Propwash: `content/PartNamingTest`; JMP: `tools/parts_sync.py --check [--propwash <Propwash-Ordner oder -Jar>]`, `share/ShareNamesTest`, `power/PowerDataTest` |

Anlass: Nach dem Test von Propwash 0.4.0 und Just More Parts 1.0.0 waren die Namen uneinheitlich – Propwash schrieb
„6S 1300 mAh Battery“, Just More Parts „6S battery 2200 mAh“; „1404 Motor, 4600 KV“ gegen „1404 motor, 3800 KV“;
„31 mm Propeller (3-blade)“ gegen „40 mm prop, 1.7″ pitch, 3 blades, PC“. Dieses Dokument legt **ein** Schema fest, das
beide Mods (und künftige Addons) einhalten. Der Web-Konfigurator übernimmt die Namen aus dem Web-Export der Mods und
braucht keine eigenen Regeln.

---

## 1. Aufbau

```
<Kern> [, <Zweitkennzahl>] [(<Zusatz>, <Zusatz>, …)]
```

- **Kern** = die Kenngröße, an der man das Teil im Hobby erkennt, plus Gerätewort: `6S 1300 mAh Battery`,
  `1404 Motor`, `31 mm Propeller`, `5″ Race Frame`, `30×30 FC/ESC Stack`, `Digital HD Video System`.
- **Zweitkennzahl** nach Komma gibt es nur beim Motor: die KV gehören zum Motor wie die Kapazität zum Akku
  (`1404 Motor, 4600 KV`).
- **Zusätze** in runden Klammern, durch Komma getrennt, wichtigster zuerst: Blattzahl, Steigung, Material beim Prop;
  Zellchemie beim Akku; Zellbereich und Strom beim Stack; Variante und Form beim Rahmen; Modellname bei Video und Zubehör.
  Es gibt höchstens **eine** Klammer pro Name; Hinweise wie „Beispiel“ kommen als letzter Zusatz hinein.
- Was ein Name **nicht** trägt: Marken, Gewicht, C-Rate, Stecker, Preise. Diese Werte stehen in der Shift-Hilfe.
- Ein Standardwert wird nicht genannt: LiPo beim Akku, True-X beim Rahmen (beim Race-Rahmen Stretched-X, siehe §3.5).

### 1.1 Schreibweise Englisch (`en_us`)
- Kern in **Title Case**: `Battery`, `Motor`, `Propeller`, `Frame`, `AIO Board`, `FC/ESC Stack`, `Video System`,
  `Camera + VTX`, `Action Cam`, `LED Strip`; Klassenwörter ebenso (`Whoop`, `Toothpick`, `Cinewhoop`, `Freestyle`,
  `Race`, `Long Range`, `X-Class`, `Nano`, `Micro`, `HD`).
- Klammerinhalt **klein** (`3-blade`, `1.7″ pitch`, `bi-blend`, `stretched X`, `for ducts`), außer Abkürzungen,
  Eigennamen, Modell- und Variantenbezeichnungen (`PC`, `LiHV`, `Li-Ion`, `H-frame`, `Lite`, `Slim`, `Bash`, `Light`,
  `Heavy`, `Clarity HD Pro`).
- Amerikanisches Englisch: `fiber`, nicht `fibre`.

### 1.2 Schreibweise Deutsch (`de_de`)
- Kenngröße und Gerät als **Bindestrich-Kompositum** wie die eingebauten Namen: `6S-Akku 1300 mAh`, `1404-Motor`,
  `31-mm-Propeller`, `5,1-Zoll-Propeller`, `5-Zoll-Race-Rahmen`, `Whoop-AIO-Board`, `30×30-FC/ESC-Stack`,
  `Digitales HD-Videosystem`, `Analoge Race-Kamera mit Sender`.
- Zoll im Langnamen ausgeschrieben im Kompositum (`5-Zoll-…`, `2,5-Zoll-…`), in Klammern, Kurznamen und
  Kurzbeschreibungen als Zeichen `″` (`4,6″ Steigung`, `Race 5″`, `5″-Race-Rahmen`).
- Dezimalkomma (`2306,5`, `5,1`, `1,7″`); Nomen groß, Adjektive klein (`hartes PC`, `weiches PC`, `für Ducts`);
  FPV-Fachwörter bleiben englisch wie in Propwash (`Race`, `Long Range`, `Stretched-X`, `Squashed-X`, `Deadcat`,
  `H-Frame`, `Bi-Blend`, `Ducts`).

### 1.3 Zahlen, Einheiten, Zeichen
| Was | Schreibweise | Beispiele |
|---|---|---|
| Einheit | Leerzeichen zwischen Zahl und Einheit | `1300 mAh`, `4600 KV`, `31 mm`, `55 A`, `25 mW` |
| Zoll | Zeichen `″` (U+2033) direkt an der Zahl, **nie** `"` | `5.1″`, `13″`, de `5,1″` |
| Zellen | `<n>S` ohne Leerzeichen; Bereich mit Halbgeviertstrich und einem S | `6S`, `1–2S`, `3–8S` |
| Lochbild | `×` (U+00D7) ohne Leerzeichen | `20×20`, `30×30` |
| Stator | wie im Handel, halbe Millimeter mit Nachkommastelle | `2207`, `2306.5`, de `2306,5` |
| Prop-Durchmesser | unter 50 mm in Millimetern, sonst in Zoll auf eine Nachkommastelle, `.0` entfällt | `31 mm`, `45 mm`, `2″`, `2.6″`, `5.1″`, `13″` |
| Steigung | immer eine Nachkommastelle | `1.4″ pitch`, `3.0″ pitch`, de `3,0″ Steigung` |
| Zellchemie | Namen der Chemie-Tabelle | `LiHV`, `Li-Ion` (nicht `Li-ion`, nicht `HV`), `semi-solid LiHV` |

---

## 2. Übersicht je Kategorie

| Kategorie | Muster Englisch | Muster Deutsch | Beispiele en / de |
|---|---|---|---|
| Motor | `<Stator> Motor, <KV> KV` | `<Stator>-Motor, <KV> KV` | `1404 Motor, 4600 KV` / `1404-Motor, 4600 KV`; `2306.5 Motor, 1860 KV` / `2306,5-Motor, 1860 KV`; `1404 Motor, 3800 KV` |
| Propeller | `<Ø> Propeller (<n>-blade, <Steigung>″ pitch, <Material>[, for ducts])` | `<Ø>-Propeller (<n> Blätter, <Steigung>″ Steigung, <Material>[, für Ducts])` | `31 mm Propeller (3-blade, 1.4″ pitch, PC)` / `31-mm-Propeller (3 Blätter, 1,4″ Steigung, PC)`; `40 mm Propeller (3-blade, 1.7″ pitch, PC)`; `5.1″ Propeller (3-blade, 3.6″ pitch, bi-blend)` / `5,1-Zoll-Propeller (3 Blätter, 3,6″ Steigung, Bi-Blend)`; `3″ Propeller (5-blade, 2.4″ pitch, PC, for ducts)` |
| Akku | `<n>S <mAh> mAh Battery[ (<Chemie>)]` | `<n>S-Akku <mAh> mAh[ (<Chemie>)]` | `6S 1300 mAh Battery` / `6S-Akku 1300 mAh`; `6S 2200 mAh Battery`; `1S 300 mAh Battery (LiHV)` / `1S-Akku 300 mAh (LiHV)`; `6S 4000 mAh Battery (Li-Ion)` / `6S-Akku 4000 mAh (Li-Ion)`; `8S 5000 mAh Battery (semi-solid LiHV)` / `8S-Akku 5000 mAh (Semi-Solid-LiHV)` |
| Stack / AIO | `<Form> AIO Board (<Zellen>, <A> A)` · `<Lochbild> FC/ESC Stack (<Zellen>, <A> A)` | `<Form>-AIO-Board (…)` · `<Lochbild>-FC/ESC-Stack (…)` | `Whoop AIO Board (1–2S, 5 A)` / `Whoop-AIO-Board (1–2S, 5 A)`; `Toothpick AIO Board (2–4S, 20 A)`; `20×20 AIO Board (2–6S, 25 A)`; `30×30 FC/ESC Stack (3–6S, 55 A)` / `30×30-FC/ESC-Stack (3–6S, 55 A)` |
| Rahmen | `<Größe> <Klasse> Frame[ (<Variante>, <Form>)]` | `<Größe>-<Klasse>-Rahmen[ (…)]` | `65 mm Whoop Frame` / `65-mm-Whoop-Rahmen`; `5″ Freestyle Frame` / `5-Zoll-Freestyle-Rahmen`; `5″ Freestyle Frame (squashed X)` / `5-Zoll-Freestyle-Rahmen (Squashed-X)`; `5″ Race Frame (Slim)`; `13″ X-Class Frame (H-frame)` / `13-Zoll-X-Class-Rahmen (H-Frame)` |
| Video | `<Analog\|Digital> [<Klasse>] Camera + VTX\|Video System[ (<Modell>)]` | `Analoge <Klasse>-Kamera mit Sender` · `Digitales [<Klasse>-]Videosystem` `[ (<Modell>)]` | `Analog Nano Camera + VTX` / `Analoge Nano-Kamera mit Sender`; `Digital Video System (Lite)` / `Digitales Videosystem (Lite)`; `Digital HD Video System (Clarity HD Pro)` / `Digitales HD-Videosystem (Clarity HD Pro)`; `Analog Whoop Camera + VTX (Firefly Nano)` |
| Zubehör | `<Gerät>[ (<Modell>)]` | `<Gerät>[ (<Modell>)]` | `Action Cam (Light)` / `Action-Cam (Light)`; `Action Cam (Heavy)`; `LED Strip` / `LED-Streifen` |

---

## 3. Regeln je Kategorie

### 3.1 Motor
- Stator wie im Datenfeld `stator` (`0702`, `1202.5`, `2207.5`, `2806.5`, `4214`), KV ohne Tausenderpunkt.
- Keine Zusätze: Halterung, Strom und Hitzegrenzen stehen im Tooltip.

### 3.2 Propeller
- Gerätewort immer `Propeller` (nicht `prop`), auch für Whoop-Props.
- Durchmesser nach §1.3 aus `diameter_mm`; die Zoll-Angabe ist die Handelsgröße (`130 mm` → `5.1″`, `76 mm` → `3″`).
- Zusätze in fester Reihenfolge: Blattzahl, Steigung, Material, `for ducts`/`für Ducts` (nur Duct-Props).
- Material: `PC`, `soft PC`/`weiches PC`, `hard PC`/`hartes PC`, `bi-blend`/`Bi-Blend`, `glass-fiber nylon`/
  `Glasfaser-Nylon`, `carbon-fiber nylon`/`Carbonfaser-Nylon`, `carbon`/`Carbon` (wie die Material-Zeile im Tooltip,
  Polycarbonat als `PC`). Auch die eingebauten Props nennen Steigung und Material, damit ein 31-mm-Dreiblatt aus PC und
  einer aus Bi-Blend im selben Werkbank-Raster unterscheidbar sind.

### 3.3 Akku
- Kern `<Zellen>S <Kapazität> mAh Battery` bzw. `<Zellen>S-Akku <Kapazität> mAh`.
- Zellchemie in Klammern, außer LiPo: `(LiHV)`, `(Li-Ion)`, `(semi-solid LiHV)`/`(Semi-Solid-LiHV)`; nicht `HV` und
  nicht vor dem Gerätewort (`6S Li-Ion Battery` und `6S-Li-Ion-Akku` sind falsch).
- Parallelschaltung (`6S2P`) bleibt in Kurzbeschreibung und Shift-Hilfe; der Name nennt Zellen in Reihe und Kapazität.

### 3.4 Stack / AIO
- `AIO Board`/`AIO-Board`, wenn FC und ESC auf einer Platine sitzen (`all_in_one`), sonst `FC/ESC Stack`/`FC/ESC-Stack`.
- Vorne die Bauform: `Whoop` (25,5-mm-Whoop-Aufnahme), `Toothpick` (Toothpick-AIO auf derselben Aufnahme), `20×20`,
  `30×30`.
- Zusätze: Zellbereich aus `cells` (`1S`, `2–4S`) und Dauerstrom je Motor aus `esc_continuous_a` (`55 A`).
- Ausnahme: der `Creative Flight Controller` / `Kreativ-Flugcontroller` (`creative_fc`, nur im Kreativmodus, öffnet den
  Drohnen-Editor, `docs/design/creative-drone.md`) heißt nach seiner Rolle, nicht nach Bauform und Zellbereich; seine
  Kurzzeile beginnt mit `Creative FC` / `Kreativ-FC`. `PartNamingTest` nimmt ihn deshalb aus der Namensrechnung.

### 3.5 Rahmen
- Größe: Whoops in Millimetern (`65 mm`, `65-mm-…`), alle anderen nach Propgröße in Zoll (`2.5″`, `2,5-Zoll-…`).
- Klasse aus `role`: `Whoop`, `Toothpick`, `Cinewhoop`, `Freestyle`, `Race`, `Long Range`, `X-Class`.
- Zusätze: zuerst eine Variante (`Lite`, `Slim`, `Bash`), dann die Form, wenn sie nicht die übliche der Klasse ist.
  Üblich ist True-X; bei `Race` Stretched-X (der eingebaute `5″ Race Frame` ist gestreckt). Formnamen wie im Tooltip:
  en `stretched X`, `squashed X`, `deadcat`, `H-frame`; de `Stretched-X`, `Squashed-X`, `Deadcat`, `H-Frame`.
- Ducts nennt der Name nicht: Whoops und Cinewhoops haben sie immer.

### 3.6 Video
- Englisch `Analog <Klasse> Camera + VTX` (Kamera und Sender getrennt) bzw. `Digital <Klasse> Video System`; Deutsch
  `Analoge <Klasse>-Kamera mit Sender` bzw. `Digitales <Klasse>-Videosystem`. Klassen: `Nano`, `Whoop`, `Micro`, `Race`,
  `HD`, `Long Range`; ohne Klasse für das Grundsystem.
- Produktartige Modellnamen (ohne echte Marken) in Klammern: `(Lite)`, `(Firefly Nano)`, `(Clarity HD Pro)`.

### 3.7 Zubehör
- Gerät wie in Propwash (`Action Cam`, `LED Strip`), Modell in Klammern wie bei Video und wie `Race Gate (large)`:
  `Action Cam (Light)`, `Action Cam (Heavy)`, `Action Cam (Flashback)` (Flashback ist der Name der Replay-Mod, mit der sie
  zusammenarbeitet, keine erfundene Marke).
- `Gripper` / `Greifer` (seit 0.5.0) ohne Modell, weil es nur einen gibt.

---

## 4. Kurznamen

Kurznamen stehen dort, wo der Platz knapp ist: Bestenliste (Spalte „Klasse“, 74 px), Funk-HUD, Bindung, Zuschauen,
Tooltip-Zeile „Rahmen“, Web-Konfigurator (`short_name`).

### 4.1 Rahmen-Kurzname (`propwash.content.frame_short.<pfad>` bzw. `<ns>.frame_short.<pfad>`)
```
<Kennwort> <Größe>[ <Zusatz>]
```
- Kennwort = Klasse (`Whoop`, `Toothpick`, `Cinewhoop`, `Freestyle`, `Race`, `Long Range`, `X-Class`); bei 5–13″-Rahmen
  mit eigener Form oder Bauart das Wort, unter dem Piloten sie kennen (`Bash`, `Squashed`, `Deadcat`, `H-Frame`).
- Größe: Whoops als Zahl ohne Einheit (`Whoop 65`), sonst mit `″` (`Race 5″`, de `Toothpick 3,5″`).
- Zusatz (ein Wort) nur, wenn sonst zwei Rahmen gleich hießen: `Lite`, `Slim`.
- Höchstens 14 Zeichen; Englisch und Deutsch gleich bis auf das Dezimalkomma.

| Rahmen | Kurzname |
|---|---|
| Propwash `whoop_65`, `cinewhoop_3`, `freestyle_5`, `race_5`, `long_range_7` | `Whoop 65`, `Cinewhoop 3″`, `Freestyle 5″`, `Race 5″`, `Long Range 7″` |
| JMP Whoops, Toothpicks, Cinewhoops | `Whoop 65 Lite`, `Whoop 75`, `Whoop 85`, `Toothpick 2″` … `Toothpick 3.5″`, `Cinewhoop 2.5″`, `Cinewhoop 3.5″` |
| JMP 5″ | `Bash 5″`, `Squashed 5″`, `Race 5″ Slim`, `Deadcat 5″`, `H-Frame 5″` |
| JMP Long Range, X-Class | `Long Range 4″`, `Long Range 6″`, `Deadcat 7″`, `Long Range 8″`, `X-Class 10″`, `X-Class 12″`, `X-Class 13″`, `H-Frame 13″` |

### 4.2 Bestenlisten-Klasse
`propwash.race.class.<pfad>` der eingebauten Rahmen = Rahmen-Kurzname (`Whoop 65`, nicht `Whoop`; `Long Range 7″`, nicht
`Long range 7"`). Fremde Rahmen benutzen ihren Kurznamen direkt.

### 4.3 Presets
- Propwash-Presets heißen nach der Klasse: `Whoop Drone`/`Whoop-Drohne`, `Racing Drone`/`Race-Drohne` (unverändert).
- Addon-Presets: `<Rahmen-Kurzname> · <Zellen>S[ <Chemie>][ <Video>]` mit Chemie nur bei Li-Ion und Video nur, wenn es
  dieselbe Klasse mit beiden Videoarten gibt: `Whoop 75 · 1S`, `Whoop 85 · 2S digital`, `Race 5″ Slim · 6S analog`,
  `Long Range 8″ · 6S2P Li-Ion`, `Deadcat 7″ · 8S`, `H-Frame 13″ · 8S`.

---

## 5. Halterungen und Zellchemien
- Motorhalterung nach Baugröße: `08xx`, `14xx`, `22xx/23xx`, `28xx`, `31xx`, `42xx` (Lochbild und Schrauben in die Hilfe).
- Stack-Halterung nach Lochbild: `20×20 mm`, `30×30 mm`, `Whoop AIO`.
- Zellchemie kurz wie im Hobby: `LiPo`, `LiHV`, `Li-Ion`, `Semi-solid LiHV`/`Semi-Solid-LiHV`; Spannungen stehen in der
  Shift-Hilfe, nicht im Namen.

---

## 6. Shift-Kurzbeschreibung (`…help.<pfad>.summary`)

Die graue Zeile direkt unter dem Namen. Aufbau `<Kurzkennung> – <Einsatz>` mit genau einem Halbgeviertstrich und ohne
Schlusspunkt, im Satzbau (Englisch: der Einsatz beginnt klein, Deutsch nach Rechtschreibung), Zoll als `″`. Die
Kurzkennung ergibt sich aus Name und Daten des Teils und wiederholt den Namen **nicht** in anderer Reihenfolge; Hinweise
wie „Beispiel“ übernimmt sie nicht. Was der Name in Klammern über die Form sagt (Deadcat, H-Frame, Squashed-X) oder was
jeder Rahmen der Klasse hat (Ducts), steht, wenn es wichtig ist, vorn im Einsatz: `– deadcat, …`, `– mit Ducts, …`.

| Kategorie | Kurzkennung Englisch | Kurzkennung Deutsch | Beispiele |
|---|---|---|---|
| Motor | `<Stator> motor, <KV> KV` | `<Stator>-Motor, <KV> KV` | `1404 motor, 3800 KV – 3″ toothpick on 4S`; `2306,5-Motor, 1860 KV – Race auf 6S` |
| Propeller | `<Ø> <n>-blade, <Material>`; Duct-Props `<Ø> <n>-blade for ducts` | `<Ø>-<n>-Blatt, <Material>`; Duct-Props `<Ø>-<n>-Blatt für Ducts` | `31 mm quad-blade, hard PC – whoop racing`; `5,1″-Dreiblatt, Bi-Blend – flüssiges Freestyle`; `3″ five-blade for ducts – cinewhoop` |
| Akku | `<n>S[<p>P] <Chemie>, <mAh> mAh` (auch LiPo) | `<n>S[<p>P]-<Chemie>, <mAh> mAh` | `6S LiPo, 2200 mAh – long flights on 6–7″`; `6S2P-Li-Ion, 8400 mAh – extreme Reichweite` |
| Stack / AIO | `<Form> AIO (<Zellen>, <A> A)` · `<Form> stack (<Zellen>, <A> A)` | `<Form>-AIO (…)` · `<Form>-Stack (…)` | `Whoop AIO (1–2S, 5 A) – FC and ESC on one board`; `30×30-Stack (3–6S, 60 A) – der 5″-Standard` |
| Rahmen | `<Größe> <klasse> frame[ (<Variante>)]` | `<Größe>-<Klasse>-Rahmen[ (<Variante>)]` | `5″ race frame (Slim) – stretched X for racing`; `65-mm-Whoop-Rahmen – mit Ducts, für drinnen`; `7″ long-range frame – deadcat, cruising almost without props in view` |
| Video | Modellname oder `Analog <klasse> camera[ (<Modell>)]` · `Digital [<klasse> ]video[ (<Modell>)]` | Modellname oder `Analoge <Klasse>-Kamera[ (<Modell>)]` · `Digitales [<Klasse>-]Video[ (<Modell>)]` | `Analog nano camera – light, low latency`; `Digitales Video (Lite) – klares Bild, leicht`; `Firefly Nano – camera and transmitter for 1S whoops` |
| Zubehör | `<Gerät>[ (<Modell>)]` im Satzbau | `<Gerät>[ (<Modell>)]` wie im Namen | `Action cam (Light) – naked, 42 g, runs on flight battery`; `LED-Streifen – unter der Nase, Licht im Dunkeln` |

- **Größe und Ø** wie im Namen, Zoll aber immer als Zeichen: `31 mm`, `5.1″`, `65 mm` / `31-mm-…`, `5,1″-…`, `65-mm-…`.
- **Blattzahl** als Wort: `two-blade`, `tri-blade`, `quad-blade`, `five-blade` / `Zweiblatt`, `Dreiblatt`, `Vierblatt`,
  `Fünfblatt`.
- **Material** wie im Namen (§3.2): `PC`, `soft PC`/`weiches PC`, `hard PC`/`hartes PC`, `bi-blend`/`Bi-Blend`,
  `glass-fiber nylon`/`Glasfaser-Nylon`, `carbon-fiber nylon`/`Carbonfaser-Nylon`, `carbon`/`Carbon` – nicht `soft`,
  `hart`, `Glasfaser` oder `full carbon`/`Vollcarbon`. Propwash und Just More Parts nennen es bei allen Props außer den
  Duct-Props; Duct-Props enden auf `for ducts`/`für Ducts`.
- **Chemie** `LiPo`, `LiHV`, `Li-Ion`, `semi-solid LiHV`/`Semi-Solid-LiHV`, Parallelschaltung wie im Hobby (`6S2P`).
- **Stack-Form** wie im Namen (`Whoop`, `Toothpick`, `20×20`, `30×30`), `AIO` für `AIO Board`, `stack`/`Stack` für
  `FC/ESC Stack`.
- **Rahmenklasse** aus `role`, Englisch klein (`whoop`, `toothpick`, `cinewhoop`, `freestyle`, `race`, `long-range`;
  `X-Class` bleibt), Deutsch `Whoop`, `Toothpick`, `Cinewhoop`, `Freestyle`, `Race`, `Long-Range`, `X-Class`. In Klammern
  nur die Variante (`Lite`, `Slim`, `Bash`), nicht die Form.
- **Video:** Klasse wie im Namen, Englisch klein außer Abkürzungen (`nano`, `whoop`, `race`, `HD`, `long-range`);
  Modellname in Klammern oder allein.
- Ducts heißen `ducts`/`Ducts`, nicht „Schutzringe“.

## 7. Fließtext
Shift-Hilfen, Flugschule, Doku und Changelogs nennen Teile mit dem Namen oder einer daraus gekürzten Form ohne
Gerätewort (`6S 1300 mAh`, `6S 4000 mAh (Li-Ion)`, `1404 3800 KV`), Chemien und Zoll wie in §1.3.

## 8. Prüfung und Pflege
- Propwash: `content/PartNamingTest` rechnet die Namen der eingebauten Motoren, Props, Akkus und Stacks aus ihren
  Daten nach, prüft Rahmen-Kurznamen gegen Bestenlisten-Klassen, die Kurzbeschreibungen aller eingebauten Teile nach §6
  und verbietet `"` als Zoll, `Li-ion` und `fibre`.
- Just More Parts: `tools/parts_sync.py --check` rechnet Motor-, Prop-, Akku- und Stack-Namen aus den Teile-Dateien nach
  (jede Abweichung ist ein Problem), prüft die Kurzbeschreibung jedes Teils nach §6 (Kurzkennung je Kategorie aus Name
  und Daten, genau ein Einsatz ohne Schlusspunkt, Englisch klein) und verbietet in allen Texten (Namen, Kurznamen,
  Shift-Hilfen) `"` als Zoll, `Li-ion`, `fibre` und „Schutzring“. Mit `--propwash <Propwash-Ordner oder -Jar>` prüft es
  nach denselben Regeln auch die eingebauten Propwash-Teile (im Ordner zusätzlich die JSON-Teile des Beispiel-Addons).
  `share/ShareNamesTest` prüft das Prop-Muster, `prop/PropDataTest` das Material in der Prop-Kurzbeschreibung,
  `power/PowerDataTest` die Kurzkennungen der Akkus und Stacks, der Teile-Katalog (`docs/design/parts-catalog.md`) führt
  alle Namen.
- Neue Teile bekommen ihren Namen nach diesem Dokument; Rahmen, Video und Zubehör werden von Hand benannt, alles andere
  ergibt sich aus den Daten.
- Addons außerhalb von Just More Parts sollen dieselben Muster benutzen (`docs/api/addon-guide.md` verweist hierher);
  Propwash prüft fremde Namen nicht.

---

## 9. Eingebaute Teile (Propwash 0.4.0)

| Pfad | Englisch | Deutsch |
|---|---|---|
| `whoop_65` | 65 mm Whoop Frame | 65-mm-Whoop-Rahmen |
| `cinewhoop_3` | 3″ Cinewhoop Frame | 3-Zoll-Cinewhoop-Rahmen |
| `freestyle_5` | 5″ Freestyle Frame | 5-Zoll-Freestyle-Rahmen |
| `race_5` | 5″ Race Frame | 5-Zoll-Race-Rahmen |
| `long_range_7` | 7″ Long Range Frame | 7-Zoll-Long-Range-Rahmen |
| `aio_whoop` | Whoop AIO Board (1–2S, 5 A) | Whoop-AIO-Board (1–2S, 5 A) |
| `aio_20` | 20×20 AIO Board (2–6S, 25 A) | 20×20-AIO-Board (2–6S, 25 A) |
| `stack_30` | 30×30 FC/ESC Stack (3–6S, 55 A) | 30×30-FC/ESC-Stack (3–6S, 55 A) |
| `m0802_22000` | 0802 Motor, 22000 KV | 0802-Motor, 22000 KV |
| `m1404_4600` | 1404 Motor, 4600 KV | 1404-Motor, 4600 KV |
| `m2004_3000` | 2004 Motor, 3000 KV | 2004-Motor, 3000 KV |
| `m2207_1750` | 2207 Motor, 1750 KV | 2207-Motor, 1750 KV |
| `m2306_1860` | 2306.5 Motor, 1860 KV | 2306,5-Motor, 1860 KV |
| `m2807_1300` | 2807 Motor, 1300 KV | 2807-Motor, 1300 KV |
| `p31_3b` | 31 mm Propeller (3-blade, 1.4″ pitch, PC) | 31-mm-Propeller (3 Blätter, 1,4″ Steigung, PC) |
| `p31_4b` | 31 mm Propeller (4-blade, 1.2″ pitch, PC) | 31-mm-Propeller (4 Blätter, 1,2″ Steigung, PC) |
| `p3_3b` | 3″ Propeller (3-blade, 3.0″ pitch, PC) | 3-Zoll-Propeller (3 Blätter, 3,0″ Steigung, PC) |
| `p51_3b` | 5.1″ Propeller (3-blade, 4.6″ pitch, PC) | 5,1-Zoll-Propeller (3 Blätter, 4,6″ Steigung, PC) |
| `p7_2b` | 7″ Propeller (2-blade, 3.5″ pitch, PC) | 7-Zoll-Propeller (2 Blätter, 3,5″ Steigung, PC) |
| `b1s_300_hv` | 1S 300 mAh Battery (LiHV) | 1S-Akku 300 mAh (LiHV) |
| `b4s_650` | 4S 650 mAh Battery | 4S-Akku 650 mAh |
| `b6s_1100` | 6S 1100 mAh Battery | 6S-Akku 1100 mAh |
| `b6s_1300` | 6S 1300 mAh Battery | 6S-Akku 1300 mAh |
| `b6s_4000_lion` | 6S 4000 mAh Battery (Li-Ion) | 6S-Akku 4000 mAh (Li-Ion) |
| `analog_nano` | Analog Nano Camera + VTX | Analoge Nano-Kamera mit Sender |
| `analog_race` | Analog Race Camera + VTX | Analoge Race-Kamera mit Sender |
| `digital_lite` | Digital Video System (Lite) | Digitales Videosystem (Lite) |
| `digital_hd` | Digital HD Video System | Digitales HD-Videosystem |
| `action_cam_light` | Action Cam (Light) | Action-Cam (Light) |
| `action_cam_heavy` | Action Cam (Heavy) | Action-Cam (Heavy) |
| `action_cam_flashback` | Action Cam (Flashback) | Action-Cam (Flashback) |
| `led_strip` | LED Strip | LED-Streifen |
| `gripper` | Gripper | Greifer |

Geändert gegenüber dem ersten Stand von 0.4.0: Props (Steigung und Material), Stacks (Zellbereich und Strom), die
beiden Akkus mit anderer Chemie (Chemie in Klammern statt `HV`/`Li-Ion` im Kern), `Digital Video System (Lite)`, die
Action-Cams, Zoll als `″`, Bestenlisten-Klassen gleich den Rahmen-Kurznamen. Motoren, LiPo-Akkus und Rahmen behalten
ihre Namen.

## 10. Just More Parts 1.0.0
Alle 143 Teile stehen mit beiden Namen im Teile-Katalog von Just More Parts (`docs/design/parts-catalog.md`, Abschnitte
3–8). Beispiele je Kategorie:

| Kategorie | Englisch | Deutsch |
|---|---|---|
| Motor | 0702 Motor, 23000 KV · 2207.5 Motor, 1850 KV · 4214 Motor, 400 KV | 0702-Motor, 23000 KV · 2207,5-Motor, 1850 KV · 4214-Motor, 400 KV |
| Propeller | 40 mm Propeller (3-blade, 1.7″ pitch, PC) · 3.5″ Propeller (5-blade, 2.5″ pitch, PC, for ducts) · 13″ Propeller (2-blade, 7.0″ pitch, carbon) | 40-mm-Propeller (3 Blätter, 1,7″ Steigung, PC) · 3,5-Zoll-Propeller (5 Blätter, 2,5″ Steigung, PC, für Ducts) · 13-Zoll-Propeller (2 Blätter, 7,0″ Steigung, Carbon) |
| Akku | 1S 450 mAh Battery (LiHV) · 6S 2200 mAh Battery · 6S 8400 mAh Battery (Li-Ion) · 8S 5000 mAh Battery (semi-solid LiHV) | 1S-Akku 450 mAh (LiHV) · 6S-Akku 2200 mAh · 6S-Akku 8400 mAh (Li-Ion) · 8S-Akku 5000 mAh (Semi-Solid-LiHV) |
| Stack / AIO | Whoop AIO Board (1S, 6 A) · Toothpick AIO Board (2–6S, 20 A) · 20×20 FC/ESC Stack (3–6S, 40 A) · 30×30 FC/ESC Stack (4–8S, 100 A) | Whoop-AIO-Board (1S, 6 A) · Toothpick-AIO-Board (2–6S, 20 A) · 20×20-FC/ESC-Stack (3–6S, 40 A) · 30×30-FC/ESC-Stack (4–8S, 100 A) |
| Rahmen | 65 mm Whoop Frame (Lite) · 3.5″ Toothpick Frame (stretched X) · 5″ Freestyle Frame (deadcat) · 5″ Race Frame (Slim) · 7″ Long Range Frame (deadcat) · 13″ X-Class Frame (H-frame) | 65-mm-Whoop-Rahmen (Lite) · 3,5-Zoll-Toothpick-Rahmen (Stretched-X) · 5-Zoll-Freestyle-Rahmen (Deadcat) · 5-Zoll-Race-Rahmen (Slim) · 7-Zoll-Long-Range-Rahmen (Deadcat) · 13-Zoll-X-Class-Rahmen (H-Frame) |
| Video | Analog Whoop Camera + VTX (Firefly Nano) · Digital Race Video System (Swift Link) · Digital Long Range Video System (Horizon LR) | Analoge Whoop-Kamera mit Sender (Firefly Nano) · Digitales Race-Videosystem (Swift Link) · Digitales Long-Range-Videosystem (Horizon LR) |
| Chemie, Halterung | Semi-solid LiHV · 31xx · 42xx | Semi-Solid-LiHV · 31xx · 42xx |
