# Versionen und Releases

Propwash FPV hat drei Versionsnummern, die unabhängig voneinander zählen: die **Mod-Version**, die **API-Version** der
Parts-API und die **Netzprotokoll-Version**. Dieses Dokument legt fest, wann welche Nummer steigt und wie ein Release
entsteht.

## 1. Mod-Version

Öffentlich zählen nur Releases. Ein Release hat eine saubere Nummer nach SemVer (`MAJOR.MINOR.PATCH`), erscheint auf
CurseForge bzw. Modrinth und hat einen eigenen Abschnitt in `CHANGELOG.md`.

- Solange die Mod vor 1.0 steht, steigt **MINOR** bei neuen Funktionen, geänderten Speicher- oder Netzformaten und
  neuen API-Nebenversionen, **PATCH** bei reinen Fehlerbehebungen ohne neue Funktionen.
- Die Nummer steht nur in `gradle.properties` (`version=`). Jar-Namen, `fabric.mod.json`, `neoforge.mods.toml` und die
  Maven-Koordinaten (`de.lardtom:propwash-common-26.3:<version>`, `-fabric-26.3`, `-neoforge-26.3`) werden daraus
  abgeleitet. Texte, die die aktuelle Version nennen (README, `docs/modrinth/`, Addon-Handbuch), werden beim Release
  angepasst.
- `CHANGELOG.md` ist kurz, englisch und für Spieler geschrieben und enthält nur öffentliche Releases (Entscheidung des
  Besitzers vom 02.10.2026). Aufbau je Release: Überschrift `## [x.y.z] — <Titel in einer Zeile>` (bestehende
  Überschriften bleiben unverändert, damit die Anker stabil sind), `### Highlights` mit 2–3 Punkten, dann `### New`,
  `### Improved` und `### Fixed` mit je einer Zeile pro Punkt (leere Abschnitte entfallen) und `### Compatibility` mit
  2–4 Zeilen: Minecraft, Loader und Java, ob Client und Server dieselbe Version brauchen, ob Welten und Drohnen
  unverändert laden, welche Addons passen. Keine Implementierungsdetails, keine Testzahlen, keine internen Versionsnamen,
  keine Protokoll- oder API-Nummern und keine Konfigurationsdateien (die stehen in `RELEASE-NOTES.md` und
  `api/changes.md`); verwandte Punkte zusammenfassen, damit ein Release-Abschnitt kurz bleibt (0.4.0: 41 Punkte, 0.5.0:
  44). Der Abschnitt wird unverändert als Changelog auf Modrinth und CurseForge eingefügt. Ein PATCH-Release mit nur
  wenigen Punkten (0.5.1: drei) lässt `### Highlights` weg, sonst stünde jeder Punkt doppelt da. Der Abschnitt eines
  veröffentlichten Releases bleibt danach unverändert; Fehlerbehebungen dazu kommen in den Abschnitt des nächsten
  Releases.
- Technisches gehört in die Doku: was Addon-Autoren betrifft (API, Datenformate, Netzprotokoll, Umstellung, Werkzeuge)
  nach `docs/api/changes.md`, die Einzelheiten der Spielerseite (Zahlen, Bereiche, Ursachen) nach
  `docs/RELEASE-NOTES.md`. Die Arbeitsschritte dazwischen stehen deutsch in den Tages-Changelogs unter `changelogs/`.

### 1.1 Interne Stände

Alles zwischen zwei Releases trägt die Nummer des **nächsten** geplanten Releases mit einem Vorab-Suffix:

| Stand | Beispiel | Bedeutung |
|---|---|---|
| Entwicklungsstand | `0.4.1-dev.1`, `0.4.1-dev.2`, … | Zwischenstände zum Testen (Spieltest, Addon-Entwicklung gegen mavenLocal) |
| Release-Kandidat | `0.4.1-rc.1`, `0.4.1-rc.2`, … | vollständig gebaut und geprüft, wird unverändert zum Release, wenn nichts mehr auffällt |
| Release | `0.4.1` | öffentlich, gleicher Commit wie der letzte Release-Kandidat, nur die Nummer ohne Suffix |
| Vorschau-Build | `0.6.0-rc.1` mit `-Ppropwash_preview=true` | für Spieltester: gleicher Commit wie der Release-Kandidat, nur mit Schalter gebaut; zeigt immer das Wasserzeichen „Propwash Preview 0.6.0-rc.1 · not final“ (§1.3) |

`N` zählt je Stufe ab 1. Ein interner Stand wird nie hochgeladen. Seine Jars liegen unter `release/<version>/`
(nicht im Repository).

**Hinweis Versionsvergleich:** Fabric vergleicht nach SemVer (`0.4.1-dev.1` < `0.4.1-rc.1` < `0.4.1`). NeoForge
vergleicht wie Maven; dort stehen unbekannte Suffixe wie `-dev.N` **hinter** der Release-Version (`0.4.1-dev.1` >
`0.4.1`), `-rc.N` korrekt davor. Deshalb:

- Abhängige (Addons, Beispiel-Addon), denen ein **öffentliches** Release reicht, nennen es als Untergrenze, z. B.
  `"propwash": ">=0.5.2"` bzw. `versionRange = "[0.5.2,)"` – das erfüllen alle späteren Stände auf beiden Loadern.
- Abhängige, die das **kommende** Release X.Y.Z brauchen (neue API-Nebenversion), schreiben die Untergrenze in
  Vorab-Schreibweise: Fabric `"propwash": ">=X.Y.Z-"`, NeoForge `versionRange = "[X.Y.Z-alpha,)"`, für 0.6.0 also
  `">=0.6.0-"` und `"[0.6.0-alpha,)"`. Beide nehmen `X.Y.Z-dev.N`, `X.Y.Z-rc.N` und `X.Y.Z` an und lehnen ältere
  Releases ab (geprüft mit Fabric Loader 0.19.5 und maven-artifact 3.9.16, das NeoForge für Bereiche nutzt). Der
  Bereich wird beim Start des Abhängigen gesetzt und bleibt im Release-Commit unverändert, damit der geprüfte
  Release-Kandidat dieselben Metadaten hat wie das Release; zur Laufzeit prüft `PropwashApi.requireVersion(modId, …)`
  die API-Version. Nicht verwenden: `>=X.Y.Z` (Fabric lehnt alle dev- und rc-Stände ab), `[X.Y.Z,)` (NeoForge lehnt alle
  rc-Stände ab), `[X.Y.Z-dev.2,)` (NeoForge lehnt sogar `X.Y.Z` selbst ab).
  Ausnahme für die offiziellen Add-ons: Ihr eigenes Release gegen das saubere Propwash-Release trägt die saubere
  Untergrenze (`>=X.Y.Z`, `[X.Y.Z,)`), weil der Release-Publisher Vorab-Kennungen wie `-alpha` in Abhängigkeiten auf
  Propwash sperrt (so in 0.6.0: Just More Parts 1.2.0, Camera Drones 1.0.0 und Mob Defense 1.0.0 mit `>=0.6.0` und
  `[0.6.0,)`). Ihre dev- und rc-Stände behalten die Vorab-Schreibweise.
- Einen `-dev`-Stand nie neben dem gleichnamigen Release installieren oder mit ihm vergleichen. Soll die Reihenfolge
  auch unter NeoForge stimmen, ist `-alpha.N` statt `-dev.N` die Maven-sichere Schreibweise (Entscheidung des Besitzers).

### 1.2 Geschichte

Das letzte öffentliche Release vor dieser Regel war **0.3.2** (CurseForge). Die danach intern gebauten Stände
0.4.0–0.4.4 trugen noch saubere Nummern; sie sind zusammen das öffentliche Release **0.4.0**. Ihre Jars liegen unter
`release/internal/0.4.0-internal` … `release/internal/0.4.4-internal`, die Einzelheiten in den Tages-Changelogs vom
29.09. bis 01.10.2026. Design-Dokumente (z. B. `docs/design/realism/calibration.md`, Flugmodell-Spezifikation) nennen
diese internen Nummern weiter als Durchgänge; öffentlich gibt es nur 0.4.0. Danach folgten 0.5.0, die Fehlerbehebung
0.5.1 und das Funktions-Update 0.5.2; das nächste Release ist 0.6.0, interne Stände bis dahin heißen `0.6.0-dev.N` /
`0.6.0-rc.N`.

### 1.3 Vorschau-Builds für Spieltester

Spieltester bekommen einen Release-Kandidaten als **Vorschau-Build** (Owner-Entscheidung vom 28.10.2026,
`docs/preview-build.md`): derselbe Commit und dieselbe Nummer `X.Y.Z-rc.N` wie der Release-Kandidat, nur mit dem
Build-Schalter `-Ppropwash_preview=true` gebaut. Er zeigt immer „Propwash Preview X.Y.Z-rc.N · not final“ unten rechts –
im Spiel über allem (auch bei F1), in Menüs und im Titelbildschirm, in Bildschirmfotos, Action-Cam-Aufnahmen und Fotos;
das Spielfenster heißt „Propwash Preview X.Y.Z-rc.N“. Der Schalter landet beim Bauen als Konstante im Code
(`preview/PreviewSwitch`), das Wasserzeichen zeichnet ein eigener Shader aus dem Code; Spieltester können es weder
abschalten noch mit einem Ressourcenpaket ausblenden (`docs/preview-build.md` §2). Die Jars unterscheiden sich in dieser Klasse und den Klassen, in die der
Compiler die Konstante einsetzt, im Kennzeichen `META-INF/propwash-build.properties` (`preview=true`) und im
Manifest-Attribut `Propwash-Preview` (beides nur zur Kennzeichnung) und im Namen (`…-X.Y.Z-rc.N-preview.jar`). Der
Schalter ist nur mit einer Vorab-Nummer erlaubt; ein Release (`X.Y.Z`) und alles, was hochgeladen oder nach Maven
veröffentlicht wird, entsteht immer ohne ihn.

## 2. API-Version (Parts-API)

`PropwashApi.API_VERSION` (heute **1.8.0**, ab Propwash 0.6.0, intern seit `0.6.0-dev.14`, davor 1.7.0 intern seit
`0.6.0-dev.2`; vorher 1.6.0 ab 0.5.2)
folgt SemVer und ist unabhängig von der Mod-Version (`docs/design/parts-api.md` §3):

- **MAJOR** bei jeder inkompatiblen Änderung stabiler Typen, **MINOR** bei Ergänzungen, **PATCH** bei Fehlerbehebungen
  ohne API-Änderung.
- Addons prüfen beim Start `PropwashApi.requireVersion(modId, "1.4")` (die kleinste Nebenversion, deren Funktionen sie
  benutzen) und deklarieren zusätzlich die Mod-Version als Abhängigkeit.
- Eine neue API-Nebenversion erscheint immer mit einem Mod-Release; interne Stände dürfen sie vorab tragen. Die
  API-Nummer bekommt kein Vorab-Suffix. Propwash 0.6.0 bringt 1.7 und 1.8 zusammen.

## 3. Netzprotokoll

`Constants.PROTOCOL_VERSION` (heute **20** für 0.6.0 nach 0.6.0-rc.4, 19 in 0.6.0-rc.4, 18 in 0.6.0-rc.3, 17 bis 0.6.0-rc.2, vorher 16 ab Propwash 0.5.2) steigt,
sobald Client und Server mit der vorherigen Nummer nicht mehr zusammenspielen (neue oder geänderte Pakete, geänderte
Teile-Abgleiche); das gilt auch zwischen zwei veröffentlichten Vorabversionen. Beim Verbinden
vergleichen beide Seiten die Nummer und melden eine Abweichung mit beiden Versionen. Die Protokollversion ist unabhängig
von Mod- und API-Version; steigt sie, nennt der CHANGELOG-Abschnitt des Releases unter *Compatibility*, dass Client und
Server dieselbe Version brauchen; `docs/api/changes.md` nennt im Unterabschnitt „Netzprotokoll N“ vor dessen Liste den
Grund („Grund:“ bzw. „Gründe:“) und jede seit dem vorigen Release übersprungene Nummer, sofern diese keinen eigenen
Unterabschnitt hat. Bleibt sie gleich, spielen die Versionen zusammen; der CHANGELOG verlangt dann nicht, dass Client und
Server dieselbe Version haben, und braucht keine Nummer. Die JUnit `race.ProtocolVersionTextsTest` vergleicht dazu
`Constants.PROTOCOL_VERSION` mit dem vorigen Release in `docs/api/changes.md`. Wo CHANGELOG, Release-Notizen,
`docs/api/changes.md`, dieser Abschnitt, die Texte der Add-on-API, das Design-Dokument des Releases und
`docs/modrinth/listing.md` eine Nummer für die Version aus `gradle.properties` nennen, muss es die heutige sein. Die
Abschnitte des Releases in CHANGELOG, Release-Notizen und `docs/api/changes.md` verlangt der Test erst mit der sauberen
Nummer (§4 Schritt 1); in `-dev`- und `-rc`-Ständen prüft er sie nur, wenn es sie schon gibt.

## 4. Ablauf eines Releases

1. Version in `gradle.properties` auf die saubere Nummer setzen, `CHANGELOG.md` mit einem Abschnitt für das Release
   (alle internen Stände seit dem letzten Release zusammengefasst, Aufbau nach §1), dazu `docs/api/changes.md` und
   `docs/RELEASE-NOTES.md`; README, `docs/modrinth/` und Addon-Handbuch prüfen.
2. `python3 tools/strip_comments.py` (keine Kommentare im Code), `tools/fastcheck.sh all` und `tools/fastcheck.sh test`,
   mit JMP-Daten zusätzlich `tools/calibration.sh`.
3. `JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew clean build`.
4. Stumme Läufe einzeln nacheinander: Selbsttest Fabric (mit Bildschirmfotos) und NeoForge, Rauchtests Client und
   Server beider Loader. Immer ohne `-PpropwashSelftestOnly`: das Ergebnis muss genau `PASS` lauten, nicht
   `PASS gefiltert: …` (`docs/testing.md`).
5. `./gradlew publishToMavenLocal` (Maven-Koordinaten mit der Release-Nummer). `tools/export_rules.sh` schreibt die
   Welt-Regeln für die Docs-Seite (`build/web-export/gamerules.json`, `docs/design/world-rules.md` §5).
6. Jars (ohne `-sources`/`-javadoc`) nach `release/<version>/` mit `SHA256SUMS`; interne Stände nach
   `release/internal/<version>` verschieben, nie löschen.
7. Release-Commit, lokale Tags (`vX.Y.Z`) und Jars (Schritt 6, `release/<version>/`) entstehen im Integrations-Worktree
   auf `integration`, nicht im Hauptbaum. `main` spult dabei kein Release-Schritt und kein automatisierter Lauf vor; das
   Vorspulen (`git merge --ff-only integration` im Hauptbaum, nicht committete Änderungen dort vorher als Patch sichern)
   und das Kopieren der Jars in den Hauptbaum macht der Projektinhaber selbst. Danach die Website-Doku
   (propwashfpv.com/docs) im Website-Repository auf diesen Stand bringen: hier `tools/export_rules.sh`, dort
   `node tools/build-docs.mjs --propwash <dieses Repository> --images` und `node tools/test-docs.mjs`; der Test nennt,
   was für neue API-Typen, Teile-Felder, `server.json`-Einstellungen, Events oder eine neue API-Nebenversion von Hand zu
   erklären ist (`tools/docs/pages/*.mjs`). Das Spielerhandbuch (`pages/guide.mjs`) braucht die Sprungziele, auf die das
   Spiel verlinkt: `video-ffmpeg` (Knopf „Anleitung“ im Tab *Action-Cam*, `ActionCamTab.GUIDE_URL`; Text deutsch und
   englisch in `docs/recording-ffmpeg.md`). Die Website geht mit dem Release online.
8. Hochladen (CurseForge, Modrinth) macht der Besitzer; Dateien und Texte: `docs/modrinth/listing.md`. Nennen Changelog
   oder Website den öffentlichen Quellcode (ab 0.5.0), kommt Schritt 9 zuerst: erst die Repositories auf GitHub
   veröffentlichen, dann hochladen und die Website live schalten – sonst stimmen diese Aussagen bis dahin nicht.
9. Öffentlicher Quellcode (ab 0.5.0): `python3 tools/public_export.py` erzeugt aus dem Release-Commit den Stand ohne
   Historie in `<Hauptbaum>-public` (beim JMP genauso), prüft ihn (Lizenzdateien, Sperrliste, lokale Pfade, Zitate,
   interne Ordner), baut ihn und legt dort genau einen Commit an. Ein vorhandener Export wird dabei samt seiner
   Historie neu angelegt, damit nichts aus älteren Exporten mit veröffentlicht wird; hat er schon ein Remote, bricht das
   Werkzeug ab (`--append` hängt dann einen Commit an, `--fresh` legt trotzdem neu an). Veröffentlicht (Remote, Push)
   wird nur nach ausdrücklicher Freigabe des Besitzers. Regeln für alles, was öffentlich wird: `docs/CONVENTIONS.md`, Abschnitt Lizenz und
   öffentliches Repository.
10. Vorschau-Build eines Release-Kandidaten für Spieltester (§1.3): auf demselben Commit nach den Schritten 2–4
    `JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew clean build -Ppropwash_preview=true`, dazu der Selbsttest
    `'-PpropwashSelftestOnly=preview:' -Ppropwash_preview=true` auf Fabric (mit Bildschirmfotos) und NeoForge
    (`docs/testing.md`, „Vorschau-Build“). Die `-preview`-Jars nach `release/internal/<version>-preview/` mit
    `SHA256SUMS`; danach wieder ohne Schalter bauen, bevor Jars für Schritt 6 oder Maven entstehen.
