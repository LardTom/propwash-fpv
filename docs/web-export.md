# Web-Export (Format 2)

Für Add-on-Autoren und die Website. `/propwash webexport` schreibt das Datenpaket, aus dem der Web-Konfigurator und das
Add-on-Verzeichnis auf der Website Teile, Regeln, Presets, Kennzahlen, Texte und Modelle lesen. Seit 0.6.0 gehört der
Export zur Basis: Jedes Add-on bekommt seinen Export ohne eigenen Code, ein Ordner je Namensraum. Bis 0.5.2 erzeugte
Just More Parts den Export (Format 1) in seinem Entwicklungs-Quell-Set; Format 2 löst ihn ab.

Der Export nutzt nur die öffentliche API (`PropwashApi.parts()`, `builds()`), den Sharecode-Codec der Basis (PW3,
`docs/sharecode/pw3.md`) und die Ressourcen der geladenen Mods (Sprachdateien, Modelle, Render-Definitionen, Texturen,
`drone_layout.json`, `drone_paint.json`, Teile-Tabelle).

## 1. Befehl

| Befehl | Schreibt |
|---|---|
| `/propwash webexport` oder `/propwash webexport all` | `base/` und jeden Namensraum mit Teilen, Presets oder Zellchemien |
| `/propwash webexport propwash` | `base/` und `propwash/` |
| `/propwash webexport <namensraum>` | nur `<namensraum>/` (für das eigene Add-on) |

- Rechtestufe 4: Server-Konsole oder Operator mit Stufe 4, im Einzelspieler der Host mit erlaubten Cheats. Die
  Tab-Vervollständigung zeigt `all` und alle Namensräume.
- Ziel ist `<spielordner>/propwash/web-export/`. Der Ordner wird vor jedem Lauf **vollständig geleert**; danach liegt
  darin genau das Ergebnis dieses Laufs.
- Der Export rechnet auf dem Server-Thread. Mit rund 180 Teilen und 27 Presets steht der Server dafür etwa
  10 Sekunden still; auf einem Server mit Spielern also nicht mitten im Rennen starten.
- Rückmeldung im Chat (Teile, Namensräume, Warnungen, Ordner). Im Log steht eine Zeile
  `Propwash-Web-Export: PASS …` bzw. `… FAIL …`; Einzelheiten in `problems.json`.
- `FAIL` heißt: mindestens ein Fehler (Regel weicht von `PropwashApi.builds().check` ab, Standard-Tuning eines Presets
  nicht berechenbar). Fehlende Texte, Hilfen oder Modelle sind Warnungen, ebenso ein Teil ohne Eintrag in der
  Teile-Tabelle (geht im Sharecode als Pfad), ein Namensraum mit Teilen ohne Tabelle (Teile gehen als Text) und ein
  Preset, das sich nicht als Sharecode schreiben lässt.

## 2. Gradle: `runWebExport`

```sh
./gradlew :fabric:runWebExport
./gradlew :fabric:runWebExport -PpropwashWebExportMods=<add-on.jar>
./gradlew :fabric:runWebExport -PpropwashWebExportSelection=justmoreparts -PpropwashWebExportMods=<ordner mit jars>
```

- Startet einen dedizierten Fabric-Server ohne das Quell-Set `devtest` (also ohne das Beispiel-Add-on), schickt über die
  Konsole `propwash webexport <auswahl>` und `stop` und beendet sich. Lauf-Ordner `fabric/runs/web-export/`
  (mit `-PpropwashRunTag=<kennung>` `…/web-export-<kennung>`), Welt flach und jedes Mal neu.
- Ergebnis in `fabric/runs/web-export/propwash/web-export/`, Kopie in `build/web-export/` der Wurzel.
- `-PpropwashWebExportMods=<jar oder ordner>,…` legt weitere Mods, etwa ein Add-on-Jar, in `mods/` des Lauf-Ordners.
  Ohne die Property ist `mods/` leer (der Ordner wird bei jedem Start neu angelegt).
- `-PpropwashWebExportSelection=<namensraum>` statt `all`.
- Port wie beim Server-Rauchtest (`-PpropwashServerPort`, sonst aus `PROPWASH_MC_SLOT`, `docs/testing.md`).
- Der Task schlägt fehl ohne Ergebniszeile im Log, mit `FAIL` oder mit `ERROR`/`FATAL`-Zeilen im Log.

## 3. Aufbau

```
web-export/
  manifest.json                    Format 2, Versionen, Auswahl, Namensräume, Dateiliste
  base/                            nur mit dem Namensraum propwash
    rules.json  paint.json  tune.json  analysis_labels.json
    sharecode/spec.json            PW3-Spezifikation als Daten, eingefrorene PW1- und PW2-Tabellen
    sharecode/vectors.json         PW3-Testvektoren aller geladenen Presets
  <namensraum>/
    manifest.json                  Mod, Version, Kurzkennung, Teile-Tabelle mit Prüfsumme, benötigte Propwash-Version,
                                   Zähler, Dateiliste
    parts.json  mounts.json  chemistries.json  presets.json
    analysis/<preset-pfad>.json
    share_table.json               nur, wenn die Mod eine Teile-Tabelle mitbringt
    lang/de_de.json  lang/en_us.json
    assets/<namensraum>/…          Modelle, Render-Definitionen, Texturen, drone_layout.json, drone_paint.json
  problems.json
```

## 4. Grundregeln

- UTF-8, JSON mit 2 Leerzeichen Einzug, Zeilenende `\n`, Schlüssel in `snake_case`.
- **Deterministisch:** gleiche Mods und gleiche Auswahl ergeben byte-gleiche Dateien; einzige Ausnahme ist
  `generated_at` im obersten `manifest.json`.
- Teile sortiert nach Kategorie (`frame, stack, motor, prop, video, battery, accessory`), dann `sort`, dann Id.
- Zahlen mit der Genauigkeit der Definition, `NaN` und unendlich als `null`; Tuning-Werte in kürzester Dezimalform.
- Ids immer voll (`propwash:m2207_1750`). Texte immer als `{"de_de": …, "en_us": …}`; fehlt ein Schlüssel, steht dort
  `null` und `problems.json` nennt ihn.
- Pfade in `parts.json` (`model.item`, `model.drone`, `model.variants`, `model.textures`) beginnen mit `assets/` und
  gelten relativ zum Ordner des Namensraums.
- Nicht exportiert wird der Kreativ-Flugcontroller `propwash:creative_fc` (passt in jeden Rahmen, keine Formel aus
  `rules.json` bildet das ab).
- Neue Felder dürfen in Format 2 dazukommen; Umbenennen oder Entfernen heißt Format 3.

## 5. Dateien

### 5.1 `manifest.json`

`format` (2), `generated_at`, `generator` (`propwash <version>`), `loader`, `minecraft`, `propwash`, `propwash_api`,
`selection` (`all` oder der Namensraum), `base` (ob `base/` geschrieben wurde), `namespaces` (exportierte
Namensräume), `sharecode` (`format` `PW3`, `prefix` `PW3-`, `revision`, `reads` mit den lesbaren Formaten `PW1`, `PW2`,
`PW3`, `pw1_revision`, `pw2_revision`), `mods` (Version je Namensraum mit Inhalten, die beim Export geladen waren,
`null` ohne gleichnamige Mod), `languages`, `counts` (`namespaces`, `parts`, `presets`, `asset_files`, `vectors`: Zahl
der Einträge in `base/sharecode/vectors.json`, 0 ohne `base/`), `fingerprint` (`PropwashApi.parts().fingerprint()`) und
`files` (alle JSON-Dateien außer den Assets, sortiert).

### 5.2 `base/`

- `rules.json`: die Regeln aus `BuildCheck` als Daten und Formel (`rules`, `gates`, `constants`, `analysis_warnings`,
  `verified`). Bei **jedem** Lauf, auch mit nur einem Namensraum, prüft der Export 5000 zufällige Baupläne
  (Startwert 1) aus allen geladenen Teilen gegen `PropwashApi.builds().check`; jede Abweichung ist ein Fehler.
- `paint.json`: `slots` mit `key`, `bit` (Bit des Lack-Platzes in der Lack-Maske, in PW1, PW2 und PW3 gleich),
  `name`, `hint`, `categories`. Die Originalfarben je Teil stehen jetzt im Teil (`paint.default_colors`).
- `tune.json`: `groups.flight` (Gruppen des Tuning-Editors), `groups.shared` (Gruppen, die ein Sharecode trägt:
  `rates`, `pid`, `feedforward`, `tpa`, `filters`, `throttle`, `camera`), `groups.never` (alle übrigen Gruppen, nie im
  Sharecode), `group_names` und `pw1_keys` (Tuning-Schlüssel von PW1: `index` ab 1, `key`, `group`). Die
  Standard-Tunings je Preset stehen jetzt im Preset (`default_tune`).
- `analysis_labels.json`: Beschriftung je Kennzahl wie in der Werkbank (`key` und `text`).
- `sharecode/spec.json`: genau das Objekt `ShareSpecJson.spec()` des Codecs (Spezifikation `docs/sharecode/pw3.md`),
  in `pw1` und `pw2` um die übrigen eingefrorenen Werte ergänzt (Abschnitt 5.5).
- `sharecode/vectors.json`: je beim Export geladenem Preset zwei PW3-Vektoren (Abschnitt 5.5).

### 5.3 `<namensraum>/`

- `manifest.json`: `format`, `namespace`, `mod` (`id`, `name`, `version` oder `null`), `short_id` (aus
  `share_table.json` oder `null`), `table_source`, `table_entries` und `table_crc` (Teile-Tabelle, mit der PW3 diesen
  Namensraum schreibt, Abschnitt 5.5), `requires` (`propwash`: Versionsbereich aus `fabric.mod.json` bzw.
  `neoforge.mods.toml` der Mod, in der Schreibweise des Loaders aus `loader` im obersten Manifest), `languages`,
  `counts` (je Kategorie, `chemistry`, `preset`, `asset_files`) und `files` (relativ zum Ordner, ohne Assets).
- `parts.json`: die Teile des Namensraums mit `id`, `category`, `builtin`, `name`, `short_name` (Rahmen), `help`,
  `basics`, `data` (alle Felder der Kategorie mit den Namen des JSON-Formats, `docs/design/parts-api-data.md`),
  `derived`, `paint` und `model`. `paint.default_colors`: Originalfarbe je Lack-Platz aus `drone_paint.json` (erste
  Ebene je Kanal) bzw. die Akzentfarbe prozeduraler Teile, sonst `{}`. Zubehör trägt `data.led_path`: die Punkte des
  LED-Pfads als Liste von `[x, y, z]` in mm, leer für den geraden Streifen. Zubehör, das nur an bestimmte Rahmen passt,
  trägt dazu `data.frames` (sortierte Rahmen-Ids); ohne Einschränkung fehlt das Feld, und die Regel `accessory_frame`
  in `rules.json` greift nicht. Eingerücktes Zubehör trägt `data.recess_mm` (Quader so weit hinter den Anker gerückt)
  und `data.drop_mm` (so weit abgesenkt), jedes Feld nur, wenn es nicht 0 ist. Zubehör, das im Spiel gerade hängt
  (`AccessoryType#hangsStraight`: der Greifer `propwash:gripper` oder `hangs_straight` im Teile-JSON), trägt
  `data.hangs_straight: true`, sonst fehlt das Feld. Damit rechnet die Website das Spiel nach, mit dem Anker aus
  `accessory_anchors` des Rahmens (`pos`, `tilt_deg`, `length_mm`; fehlt der Platz dort, der Ersatz aus
  `AccessoryAnchor#fallback` nach `wheelbase_mm`):
  - Neigung (`AccessoryAnchor#tiltFor`): 0 bei `hangs_straight`, sonst `tilt_deg`.
  - Streckung in x (`AccessoryAnchor#stretch`): `length_mm / size_mm[0]` nur unten (`slot` `bottom`), ohne
    `hangs_straight`, mit leerem `led_path`, `length_mm` und Breite größer 0; sonst 1.
  - Zeichenursprung (`AccessoryAnchor#modelOrigin`): ohne `recess_mm` und `drop_mm` der Anker, sonst der Anker plus
    `[0, −drop_mm, recess_mm]`, mit dieser Neigung um die x-Achse gedreht (bei `hangs_straight` also ungedreht).
  - Quader für Schwerpunkt und Trägheit (`AccessoryAnchor#corners`): der Quader des Platzes um `[0, −drop_mm, recess_mm]`
    verschoben, x mal Streckung, mit derselben Neigung gedreht und an den Anker gesetzt.
  - LEDs ohne `led_path` (`AccessoryAnchor#ledMm`): über `length_mm` verteilt, wenn das Zubehör nicht gerade hängt und
    `length_mm` größer 0 ist, sonst über die eigene Breite; gedreht wie der Quader.
- `mounts.json`: je Art (`motor`, `stack`, `prop`) die Halterungen, die Teile dieses Namensraums benutzen, mit Namen
  und den Listen `frames` bzw. `motors` und `parts` – nur mit Teilen dieses Namensraums. Für den Gesamtkatalog die
  Listen aller Namensräume je Halterungs-Id zusammenführen.
- `chemistries.json`: Zellchemien, die der Namensraum anmeldet oder die seine Akkus benutzen, mit allen Werten;
  `used_by` nennt nur Akkus dieses Namensraums.
- `presets.json`: die Presets des Namensraums mit `id`, `name`, `creative`, `build`, `paint`, `tune`, `sharecode`
  (PW3-Code des Presets: Teile, Lack, Tuning und Name des Presets, gleich dem Vektor `preset:<id>`; `null`, wenn er
  sich nicht schreiben lässt), `default_tune` (`PropwashApi.builds().defaultTune`), `check`, `analysis` und
  `camera_view`.
- `analysis/<preset-pfad>.json`: je Preset dieses Namensraums `preset`, `camera_view` und `variants`: jedes baubare Teil
  aller beim Export geladenen Namensräume einzeln ins Preset getauscht (Zubehör in seinem Platz), mit `check`,
  `analysis` und `camera_view`.
- `share_table.json`: Kopie von `data/<namensraum>/propwash_share/table.json` der Mod, falls vorhanden.
- `lang/<sprache>.json`: alle Schlüssel, die die Dateien dieses Namensraums benutzen (bei `propwash` auch die von
  `base/` und die Texte von `ImportStatus` und `ImportLayer`), flach und sortiert.
- `assets/…`: nur Dateien, die ein Teil des Namensraums wirklich benutzt (samt Eltern-Modellen und Texturen, auch aus
  anderen Namensräumen), dazu `drone_layout.json` und `drone_paint.json` des Namensraums. Pfade wie im Mod.

### 5.4 `problems.json`

Liste von `{"level": "error" | "warning", "where", "message"}`, sortiert. `where` nennt Datei und Eintrag
(`justmoreparts/presets.json#…`) bzw. die Teile-Id.

### 5.5 Sharecode (PW3)

Alle Codes schreibt der Codec der Basis mit den Tabellen des laufenden Spiels (`ShareTables.current()`), also genau so
wie die Werkbank. Die Website prüft daran ihren Port (`sharecode3.js`): gleiche Inhalte müssen byte-gleiche Codes
ergeben.

**`base/sharecode/spec.json`** – Objekt. Oberste Ebene wie `ShareSpecJson.spec()` (PW3):

| Feld | Inhalt |
|---|---|
| `format` | 1 (Format dieser Datei) |
| `code_format`, `prefix`, `revision` | `PW3`, `PW3-`, `3.0` |
| `alphabet`, `id_alphabet`, `text_alphabet` | Base62 des Rahmens, Zeichen für Text-Ids, Zeichen für Texte |
| `checksum`, `table_checksum` | Prüfsumme des Rahmens, Regel der 10-Bit-Prüfsumme je Namensraum (`pw3.md` §5.2) |
| `unknown_part`, `unknown_short_id_key`, `reserved_prefix` | Darstellung unbekannter Teile und Kurzkennungen, reservierter Präfix `pw3.` |
| `params`, `limits`, `link_types` | Exp-Golomb-Parameter, Grenzen, Linkarten (`none`, `modrinth`, `curseforge`, `https`) |
| `layers` | je Ebene `bit`, `key`, `default` (im Standard-Export), `translation_key` |
| `kinds`, `accessory_slots`, `paint_slots`, `tune_keys`, `tune_groups` | Reihenfolgen der Arten, Zubehör-Plätze, Lack-Plätze (Index = Bit), Tuning-Schlüssel (Index + 1 = Nummer), geteilte Tuning-Gruppen |
| `colors`, `osd_dictionary` | Farbtabelle und OSD-Wörterbuch (eingefroren, bitgleich zu PW2) |
| `statuses`, `namespace_results` | Status mit `key` und `translation_key`; Ergebnisse je Namensraum (`MATCH`, `MISMATCH`, `NEWER`, `MISSING`, `NO_TABLE`) |
| `propwash_table` | `data/propwash/propwash_share/table.json` aus dem Propwash-Jar |
| `directory` | Verzeichnis-Abzug `data/propwash/propwash_share/directory.json` (Kurzkennungen, Namen, Tabellen offizieller Add-ons) |
| `pw2` | eingefrorene PW2-Tabellen: `format`, `prefix`, `revision`, `unknown_part`, `part_param`, `parts` (je Art die vollen Ids in Nummernfolge), `tune_keys`; ergänzt um `alphabet`, `text_alphabet`, `checksum`, `params` (`part`, `color`, `paint_slots`, `tune_count`, `tune_gap`, `value`, `text`, `osd_bytes`, `extension_bits`), `colors`, `osd_dictionary` |
| `pw1` | eingefrorene PW1-Tabellen: `format`, `prefix`, `revision`, `alphabet`, `unknown_namespace`, `namespaces` (Index = Nummer, 0 = Text), `tune_keys`; ergänzt um `group_length`, `frame_version`, `flag_deflate`, `checksum`, `tolerant` (`O` → `0`, `I`/`L` → `1`), `accessory_slots` und `paint_bits` (je `index`/`key` bzw. `bit`/`slot`), `limits` |

Die Texte zu `layers`, `statuses` und `namespace_results` stehen in `propwash/lang/*.json` (Schlüssel
`propwash.share.layer.*`, `propwash.share.status.*`, `propwash.share.namespace.*`).

**`base/sharecode/vectors.json`** – Liste, je Preset in der Reihenfolge von `PropwashApi.builds().presets()` zwei
Einträge:

| Name | Inhalt des Codes |
|---|---|
| `parts:<preset-id>` | nur die Teile-Ebene |
| `preset:<preset-id>` | Teile, Lack, Tuning und Name des Presets (wie `presets.json` → `sharecode`) |

Felder je Eintrag: `name`, `format` (`PW3`), `code`, `status` (`OK`), `layers`, `decoded` (Inhalt nach dem Lesen:
`build` mit `frame` … `battery` und `accessories`, `paint`, `tune`, `osd`, `name`), `namespaces` (je Namensraum im Code
`key`, `short_id`, `table`, `min_version`, `result`), `skipped`, `unknown_parts`, `check` (`problems`, `warnings`) und
`analysis` (Kennzahlen wie in `presets.json`; beide `null`, wenn der Code unbekannte Teile enthält). Lässt sich ein
Code nicht schreiben, steht dort nur `name`, `status` und `error`, und `problems.json` nennt ihn.

**Teile-Tabelle im Namensraum-Manifest:**

| Feld | Inhalt |
|---|---|
| `table_source` | `mod` (eigene `data/<namensraum>/propwash_share/table.json` im Jar, bei `propwash` die der Basis), `directory` (Tabelle aus dem Verzeichnis-Abzug, etwa Just More Parts 1.1.x ohne eigene Tabelle) oder `null` (keine Tabelle, Teile gehen als Text) |
| `table_entries` | Zahl der Einträge aller Arten, gestrichene mitgezählt; 0 ohne Tabelle |
| `table_crc` | Tabellen-Prüfsumme: CRC-32 (IEEE) als 8 Hex-Ziffern über die UTF-8-Zeilen `<art>:<v>:<pfad>\n` **aller** Einträge der Tabelle, sortiert nach Art (`frame`, `stack`, `motor`, `prop`, `video`, `battery`, `accessory`) und Nummer – die Regel aus `pw3.md` §5.2 über die ganze Tabelle; `null` ohne Tabelle. Wert für `table_crc` im Verzeichnis der Website. Wird die Tabelle hinten erweitert, ändert sich der Wert. |

## 6. Von Format 1 zu Format 2

| Format 1 | Format 2 |
|---|---|
| `parts.json` | `<namensraum>/parts.json` |
| `mounts.json`, `chemistries.json` | `<namensraum>/…` (Listen nur mit eigenen Teilen) |
| `rules.json`, `analysis_labels.json` | `base/…` |
| `paint.json` `slots` | `base/paint.json` |
| `paint.json` `default_colors` | `<namensraum>/parts.json` → `paint.default_colors` |
| `tune.json` `groups.flight`, `group_names` | `base/tune.json` |
| `tune.json` `defaults` | `<namensraum>/presets.json` → `default_tune` |
| `presets.json` | `<namensraum>/presets.json` |
| `analysis/<ns>/<pfad>.json` | `<ns>/analysis/<pfad>.json` |
| `lang/…`, `assets/…` | `<namensraum>/lang/…`, `<namensraum>/assets/…` |
| `sharecode/spec.json` (PW1 oben, `pw2`) | `base/sharecode/spec.json` (PW3 oben, `pw1`, `pw2`) |
| `sharecode/vectors.json` (PW1 und PW2) | `base/sharecode/vectors.json` (PW3, `parts:` und `preset:` je Preset) |
| `tune.json` `pw1_keys`, `groups.shared`/`never` | `base/tune.json` (gleich) |
| `paint.json` `slots[].bit` | `base/paint.json` (gleich) |
| `presets.json` `sharecode` (PW2) | `<namensraum>/presets.json` `sharecode` (PW3, gleich dem umgerechneten PW2-Code) |
| `manifest.json` `sharecode` | `manifest.json` `sharecode` (`format` PW3, `pw1_revision`, `pw2_revision`) |

Die Texte des alten Sharecodes von Just More Parts (`justmoreparts.share.…`) entfallen; die Universal-Prop-Aufnahme
`propwash:universal` heißt jetzt über den Propwash-Schlüssel `propwash.content.mount.prop.universal`.

## 7. Prüfung

- JUnit `WebExportTest`: Export der eingebauten Teile zweimal hintereinander byte-gleich bis auf `generated_at`,
  Aufbau, `led_path`, Standard-Tuning und Analysen je Preset, keine Regel-Abweichung.
- JUnit `AccessoryRecessExportTest`: `recess_mm` und `drop_mm` stehen unter den Namen des Teile-JSON in `data`, jedes
  nur, wenn es gesetzt ist; ohne die Felder bleibt `data` jedes Zubehörs wie bisher.
- JUnit `HangsStraightExportTest`: `hangs_straight` steht beim Greifer und bei Zubehör mit der Angabe, bei allem anderen
  eingebauten Zubehör nicht. Die Rechnung aus Abschnitt 5.3 (`parts.json`) nur auf dem Export ergibt an jedem eingebauten
  Rahmen für jedes eingebaute und mehrere Prüf-Zubehöre (gerade hängend, geneigt, eingerückt, oben, unten, vorne, mit
  LEDs) dieselbe Neigung, Streckung, denselben Ursprung, dieselben Ecken, Mitte und LEDs wie `AccessoryAnchor`; am
  `freestyle_5` mit `propwash:gripper` unten Neigung 0 und Streckung 1,0 (Halterung 30°).
- JUnit `WebExportTest` prüft außerdem: `spec.json` gleich `ShareSpecJson.spec()` (nur ergänzt), jeder Vektor liest sich
  zu seinem `decoded`, die Codes der eingebauten Presets sind die Golden-Codes aus
  `common/src/test/resources/sharecode/pw3-vectors.json` (`U…/<id>/parts` und `/preset`), `presets.json` `sharecode`
  gleich dem Vektor `preset:`, `pw1_keys`, Gruppen, Lack-Bits und `table_crc`.
- `Format1CompareTest`: vergleicht einen Format-2-Export inhaltlich mit einem Format-1-Export. Zulässig sind nur der
  neue Aufbau, neue Teile (auch ihre Analyse-Varianten, Vektoren und Assets), `led_path`, beim Zubehör `recess_mm`,
  `drop_mm` und `hangs_straight`, wenn der Altexport sie nicht hat, die entfallenen
  Sharecode-Texte von Just More Parts, Texte, die jetzt als Sprachschlüssel kommen, und eine Asset-JSON, die nur neue
  Einträge bekommt (etwa die `_mounted`-Varianten in `drone_paint.json`). Ist der Altexport mit Propwash 0.5 entstanden
  (`mods.propwash` im Manifest), sind dazu die Motor-Hitze-Werte von 0.6.0 zulässig: `sustained_motor_temp_c`,
  `motor_temp_minute_c`, `mixed_motor_temp_c`, die Warnung `motor_thermal` und ihr Text; jeder Fall steht im Bericht.
  Streng geprüft werden die Sharecode-Felder:
  - `spec.json`: jeder PW1-Wert des Altexports gleich `pw1.*`, jeder PW2-Wert gleich `pw2.*` (außer der Darstellung
    `unknown_part`), Ebenen nach `bit`, `key`, `default`, alle alten Status vorhanden, keine Alias-Tabelle;
  - jeder alte Preset-Vektor (PW1 und PW2, `preset:`/`preset_parts:`) ergibt umgerechnet (`ShareConvert.toPw3`) genau
    den Code des neuen Vektors, mit gleichem Inhalt, `check` und `analysis`; jeder alte Sondervektor (A–I) liest sich mit
    Propwash 0.6.0 zum selben Inhalt und Status und ergibt nach PW3 umgerechnet denselben Build;
  - `presets.json` `sharecode`: der alte PW2-Code ergibt umgerechnet den neuen Code;
  - `pw1_keys`, `groups.shared`/`never`, `bit`, `manifest.json` `sharecode`;
  - je Namensraum: die Teile-Tabelle beginnt je Art mit der PW2-Reihenfolge des Altexports, `table_entries` und
    `table_crc` stimmen mit der Tabelle aus `share_table.json` bzw. dem Verzeichnis-Abzug.

  Teile anderer Namensräume, die im neuen Export fehlen, bleiben außen vor. Über Umgebungsvariablen:

  ```sh
  PROPWASH_WEBEXPORT_FORMAT1=<format-1-ordner> PROPWASH_WEBEXPORT_FORMAT2=<format-2-ordner> \
  PROPWASH_WEBEXPORT_SECOND=<zweiter lauf> PROPWASH_WEBEXPORT_REPORT=<bericht.txt> \
    tools/fastcheck.sh test --select-class de.lardtom.propwash.webexport.Format1CompareTest
  ```

  Nur mit `PROPWASH_WEBEXPORT_FORMAT1` vergleicht der Test einen frischen Export der eingebauten Teile; mit
  `PROPWASH_WEBEXPORT_SECOND` prüft er zwei Läufe aus dem Spiel auf Byte-Gleichheit bis auf `generated_at`.
- Orakel für Add-ons: `runWebExport` mit dem Jar des Add-ons (`-PpropwashWebExportMods`) gegen dessen letzten Export.

## 8. Grenzen

- Texte, Modelle und die Teile-Tabelle findet der Export nur, wenn die Mod-Id gleich dem Namensraum ist; sonst steht
  `mod: null` im Manifest und die Warnungen nennen die fehlenden Texte.
- Die Analyse-Varianten hängen davon ab, welche Add-ons beim Export geladen waren (Teile aller geladenen Namensräume),
  ebenso `base/sharecode/vectors.json` (Presets aller geladenen Namensräume).
- Der Export liest keine Daten aus Code eines Add-ons, nur was über die API angemeldet ist.
- `runWebExport` gibt es nur für Fabric; der Befehl selbst läuft auf beiden Loadern.

## 9. Kernsätze (Englisch, für die Website)

- `/propwash webexport` writes the data package for the web configurator and the add-on directory: one folder per
  namespace plus `base/` for Propwash's own rules, paint slots and tune groups.
- Every add-on gets its export without code of its own: `/propwash webexport <namespace>` (permission level 4).
- The output is deterministic: the same mods give byte-identical files, only the timestamp in `manifest.json` changes.
- Accessories carry their LED path (`data.led_path`), so the website can draw rings and arcs.
- Recessed accessories carry `data.recess_mm` and `data.drop_mm`, so the website draws them where the game does: moved
  back behind the anchor and down, not in front of the nose.
- Accessories that hang straight in the game (the gripper, or `hangs_straight` in the part file) carry
  `data.hangs_straight: true`, so the website neither tilts them with the mount nor stretches them to the strip length.
- Share codes are PW3: `base/sharecode/spec.json` holds the specification as data plus the frozen PW1 and PW2 tables,
  `base/sharecode/vectors.json` holds two test codes per preset, and every preset carries its PW3 code.
