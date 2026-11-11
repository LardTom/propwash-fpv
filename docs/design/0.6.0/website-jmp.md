# 0.6.0 – Website und Just More Parts 1.2.0

| | |
|---|---|
| Stand | 17.10.2026, Entwurf der Design-Runde für 0.6.0 |
| Repositories | Website `propwash-site` (öffentlich als `LardTom/propwash`), Add-on `propwash-justmoreparts`, dazu ein kleiner Teil in Propwash (Web-Export) |
| Basis | Propwash `v0.5.2`, Website `main` = Live-Stand 0.5.2 (`cfe6713`), Just More Parts `v1.1.1` (`747c2aa`) |
| Ziel-Versionen | Propwash 0.6.0, Just More Parts **1.2.0**, Website-Release `release/0.6.0` |
| Nachbar-Designs | Share-Codes PW3 (Codec in der Basis), XT60-Optik im Kern-Renderer, API 0.6.0, Feature-Stränge 0.6.0 – ihre Dokumente in diesem Ordner sind für die Schnittstellen maßgeblich (Anhang A). Die Server-Mod „Propwash: Server“ ist zurückgestellt und kommt auf der Website von 0.6.0 nicht vor. |

---

## 1. Ziel und Vorgaben

**Ziel.** Die Website zieht mit 0.6.0 komplett mit: Doku zu allen neuen Funktionen, FAQ zu den häufigsten
Discord-Fragen, ein Web-Konfigurator, den Einsteiger verstehen, ein Umrechner für alte Share-Codes, ein offizielles
Add-on-Verzeichnis und die Website in weiteren Sprachen. Downloads laufen nur über
CurseForge und Modrinth. Just More Parts erscheint passend als 1.2.0: ohne eigenen Share-Codec (nur noch die
Teile-Tabelle), mit mehr Whoop-Motoren sehr hoher KV und mit gesteckten Akku-Steckern.

Die Vorgaben stehen hier **sinngemäß und mit Datum**. Wörtliche Zitate gehören nach `docs/CONVENTIONS.md` nicht ins
Repository (die Export-Prüfung bricht sonst ab). Quelle sind die Roadmap-Notizen, der Plan nach 0.5.2, die
Add-on-Richtlinien und die Discord-Auswahl vom 17.10. (Feld „Ziel“).

### 1.1 Website

| Datum | Vorgabe |
|---|---|
| 17.10. | FAQ für 0.6.0: Lizenz, echte FPV-Brille, VR. Motion Blur ist **kein** Propwash-Feature; das läuft über Iris und Shaderpacks, die FAQ verweist höchstens dorthin. |
| 17.10. | Web-Konfigurator: Kennzahlen erklären und Builds für Einsteiger empfehlen (Vorschlag angenommen). |
| 17.10. | Die Seite in verschiedene weitere Sprachen übersetzen. Jede weitere Sprache bekommt ein Banner: mit KI übersetzt, offiziell sind nur Englisch und Deutsch, mit Link zur englischen bzw. deutschen Fassung. Immer allgemein „KI“, nie ein Modell- oder Firmenname. |
| 17.10. (korrigiert) | Geladen wird über **Modrinth und CurseForge**, auch wenn deren Prüfung länger dauert. GitHub wird auf der Website nicht als Plattform oder Download genannt, höchstens ein kleines GitHub-Icon zum Repository. Die erste Antwort in der Discord-Auswahl (GitHub als dritte Plattform) ist durch diese Korrektur vom selben Tag überholt. |
| 14.10. | PW3: Die Website erkennt PW1 und PW2 und rechnet sie automatisch in PW3 um. Der Web-Konfigurator lädt Add-on-Jars lokal im Browser (nichts wird hochgeladen) und nutzt ein offizielles Verzeichnis mit Web-Exporten; das Verzeichnis vergibt optional kurze Kennungen ohne Doppelte. Mob Defense und das Kamera-Add-on nutzen das später. |
| 14.10. | Nach der Umstellung Pflicht: Website und JMP anpassen, neuer JMP-Release ohne eigenen Codec, Discord-Ankündigung mit der Bitte, alte Codes auf der Website umzurechnen und neu zu posten. |
| 17.10. | Add-on-Richtlinien: alles in 0.6.0, darunter ein Website-Doku-Abschnitt „Regeln für Add-ons“ (EN/DE) unter Doku → Add-on-API. |
| 17.10. | Plan nach 0.5.2, Website-Teil: Changelog, Doku, PW3-Umrechner, Add-on-Verzeichnis, Roadmap (die dort genannte Server-Mod-Seite entfällt mit der Zurückstellung am 17.10. abends). Die Website wird bei jedem Produkt mitgezogen. 0.6.0 wird vor dem Release gründlich geprüft. |
| 17.10. / 08.10. | Roadmap an den neuen Plan anpassen; auf der öffentlichen Roadmap keine Spoiler. |
| 17.10. | Nicht Thema: eine Seite zum Community-Server (Join-Anleitung), eine Community-Maps-Seite. VR hat derzeit keine Relevanz. |
| 17.10. abends | Server-Mod „Propwash: Server“ zurückgestellt: nicht in 0.6.0, keine Seite, kein Verzeichnis-Eintrag, keine Doku, keine FAQ-Erwähnung. |
| 02.10. | Doku unter propwashfpv.com/docs, aus dem Code erzeugt. |

### 1.2 Just More Parts

| Datum | Vorgabe |
|---|---|
| 17.10. | In die nächste JMP-Version (sie kommt mit 0.6.0) mehr Whoop-Motoren mit sehr hoher KV (30000+ für 1S). Vorschlag dazu: ohne Markennamen, mit Datenbeleg. |
| 14.10. | Der Share-Codec wandert aus JMP in die Basis. JMP liefert nur noch seine Teile-Tabelle: im Jar, nur hinten erweitert, nie umnummeriert; ein Prüfwerkzeug schlägt beim Bauen Alarm; gestrichene Teile bleiben „veraltet“ mit Ersatz-Verweis. |
| 17.10. | XT60-Optik: Der Akku wirkt nicht angesteckt, weil Akku-Stecker und Pigtail-Stecker zwei getrennte gelbe Stecker sind. Stecker gesteckt darstellen, Kabel sinnvoll führen, für alle Akkuplätze (oben, unter dem Rahmen) und alle Rahmen, auch im Web-Konfigurator. |
| 15.10. | Backports bekommen später ausgewählte JMP-Teile fest eingebaut (betrifft dieses Design nur als Ausblick: die neuen Motoren sind Kandidaten). |

---

## 2. Umfang

### 2.1 Drin – Website

1. **FAQ** mit den vier neuen Einträgen (Lizenz, FPV-Brille, VR, Motion Blur) und zwei Einträgen, die 0.6.0 auslöst
   (alte Share-Codes, Sprachen); bestehende Einträge an 0.6.0 angepasst (Download, Add-ons).
2. **Downloads nur CurseForge und Modrinth**, gesteuert über eine Datei `assets/data/platforms.json`; GitHub nur als
   kleines Icon zum Quellcode. Ein Test verhindert Rückfälle.
3. **Web-Konfigurator:** Erklärung jeder Kennzahl (Hover, Antippen, Tastatur) mit dem für die Klasse typischen
   Bereich; drei **Einsteiger-Builds** mit Etikett und Code zum Kopieren.
4. **PW3 im Konfigurator:** lesen und schreiben (nur noch PW3), PW1/PW2 lesen und umstellen, fehlende Add-ons nennen.
5. **PW3-Umrechner** als eigene Seite `/convert/` (auch viele Codes auf einmal, etwa ein ganzer Discord-Post).
6. **Add-on-Jars lokal im Browser** laden (nur lesen, nichts hochladen, nichts speichern).
7. **Offizielles Add-on-Verzeichnis** `/addons/` mit Vergabeliste der kurzen Kennungen und gehosteten Web-Exporten;
   Seite des offiziellen Add-ons `/addons/just-more-parts/`.
8. **Doku zu allen 0.6.0-Funktionen**, Abschnitt „Regeln für Add-ons“.
9. **Changelog** wie bisher mit Propwash und Just More Parts.
10. **Roadmap-Pflege:** Start-Stand jetzt (auf Go), Release-Stand mit 0.6.0.
11. **Weitere Sprachen:** Spanisch, Französisch, Portugiesisch (Brasilien), Polnisch, Russisch, jeweils mit KI-Banner.
12. **XT60 im 3D-Viewer** des Konfigurators wie im Spiel.
13. Datenschutz-Text ergänzt (Jar wird nur lokal gelesen).

### 2.2 Drin – Just More Parts 1.2.0

1. Paket `share` (Codec, Tabellen, Share-Bildschirm, Einkaufsliste, Szene, Tab, Werkbank-Erweiterung) und der
   Web-Export (`export` im Quell-Set `devtest`) entfallen; dafür eine **PW3-Teile-Tabelle** als Ressource.
2. `/jmp code …` bleibt in 1.2.x als Weiterleitung auf den Propwash-Befehl (mit einmaligem Hinweis).
3. **Drei neue 1S-Whoop-Motoren mit 30000+ KV** plus ein Preset „65-mm-Renn-Whoop (1S)“, mit Datenbeleg ohne Marken.
4. **XT60 gesteckt:** Stecker aus 78 Akku- und 20 Stack-Modellen entfernt, Anker für den Kern-Renderer gesetzt.
5. Release 1.2.0 für Propwash 0.6.0 (README, Modrinth-Text, CHANGELOG, Release-Notizen).

### 2.3 Drin – Propwash (nur was Website und JMP brauchen)

1. **`/propwash webexport [<namensraum>]`**: der Web-Export wandert aus JMP in die Basis (Format 2, je Namensraum),
   damit jedes Add-on ohne eigenen Code einen Export für das Verzeichnis erzeugen kann.
2. **Anforderung an den PW3-Strang:** Propwash 0.6.0 trägt keine Unverträglichkeit für Just More Parts ein; mit JMP
   1.1.x läuft es im Altmodus (ein Sharecode-Knopf, PW3 über den Verzeichnis-Abzug, Hinweis „Just More Parts 1.2.0
   empfohlen“; Übersicht `README.md` §3.5).

### 2.4 Bewusst nicht drin

- Seite zum Community-Server mit Join-Anleitung, Community-Maps-Seite (beides abgelehnt).
- Alles zur zurückgestellten Server-Mod: Seite `/addons/server/`, Doku-Gruppe „Server betreiben“ (`/docs/server/`),
  Verzeichnis-Eintrag, drittes Produkt im Changelog, Erwähnung in FAQ und „Was ist neu“.
- VR-Unterstützung oder eine Ankündigung dazu; ein eigener Motion-Blur-Effekt.
- Chinesisch, Japanisch, Koreanisch (Begründung 3.2.1); Übersetzung der Mod selbst (Crowdin bleibt später).
- KI-Übersetzung von Impressum und Datenschutz (Rechtstexte bleiben DE/EN) und der Add-on-Entwickler-Doku
  (API, API-Referenz, Teile-Format).
- PW2-Export auf der Website, Upload oder Speicherung von Jars, eine offene Selbsteintragung ins Add-on-Verzeichnis.
- Kennzahl-Hilfen in der Werkbank im Spiel (die Texte sind so geschrieben, dass sie später dorthin wandern können).
- Neue Roadmap-Ideen ohne Freigabe (Portale u. Ä.).
- Neue Rahmen oder andere Teile in JMP außer den Whoop-Motoren und dem Preset.

---

## 3. Architektur und Datenformate

### 3.1 Seitenbaum der Website nach 0.6.0

```
/                         Startseite EN/DE (wie bisher, ein HTML mit beiden Sprachen)
/configurator/            Web-Konfigurator (PW3, Kennzahl-Hilfen, Einsteiger-Builds, Add-ons, Jar laden)
/convert/                 NEU: PW1/PW2 → PW3 umrechnen
/addons/                  NEU: Add-on-Verzeichnis
/addons/just-more-parts/  NEU: Seite des offiziellen Add-ons
/docs/ …                  Doku mit den 0.6.0-Abschnitten
/changelog/  /roadmap/    wie bisher
/impressum/ /datenschutz/ wie bisher (nur DE/EN)
/es/ /fr/ /pt-br/ /pl/ /ru/   NEU: erzeugte Sprachfassungen der Spieler-Seiten (3.2)
```

Alle Pfade bleiben relativ (GitHub Pages, eigene Domain). Alte Anker (`#release`, `#faq`, `configurator/#PW1-…`,
`configurator/#PW2…`) bleiben gültig.

### 3.2 Weitere Sprachen

#### 3.2.1 Auswahl

| Sprache | Pfad | `hreflang` | Begründung | Schrift |
|---|---|---|---|---|
| Spanisch | `/es/` | `es` | eine der größten Minecraft-Java-Gemeinschaften (Spanien und Lateinamerika), Start-Sprache des Übersetzungsplans vom 06.10. | Latin-1, vorhandene Schriften reichen |
| Französisch | `/fr/` | `fr` | französische Spieler fanden das K-Menü schwer verständlich (Anlass des Discord-Wunschs), Start-Sprache | Latin-1 |
| Portugiesisch (Brasilien) | `/pt-br/` | `pt-BR` | große brasilianische Minecraft-Szene, Start-Sprache | Latin-1 |
| Polnisch | `/pl/` | `pl` | Angebot einer polnischen Übersetzung aus der Community, Start-Sprache | Latin Extended-A nötig (ł, ż, ą …) |
| Russisch | `/ru/` | `ru` | große russischsprachige Minecraft-Szene, russischsprachige Spieler im Discord, Start-Sprache | Kyrillisch nötig |

Nicht jetzt: Chinesisch und Japanisch (ebenfalls Start-Sprachen des Übersetzungsplans). Gründe: Für CJK gibt es keine
Webschrift in vertretbarer Größe (Systemschriften brechen den Look), Zeilenumbruch ohne Wortabstände bricht Kacheln,
Navigation und Konfigurator-Spalten anders als alle übrigen Sprachen, und die Qualität lässt sich ohne Muttersprachler
deutlich schlechter automatisch prüfen. Das Gerüst unten trägt sie später ohne Umbau (eine Zeile in
`languages.json`, ein Wörterbuch, eine Layout-Runde). Italienisch, Türkisch, Ukrainisch und Niederländisch haben im
Discord bisher kein Signal.

**Schriften (geprüft am 17.10.):** `saira-var.woff2` und `jetbrains-mono-var.woff2` sind Latin-1-Teilmengen mit je 230
Zeichen; ł/ż (Polnisch) und Kyrillisch fehlen in beiden. Daraus folgt:

- beide Schriften neu als Teilmenge **Latin + Latin Extended-A** aus den OFL-Quellen schneiden (Polnisch);
- JetBrains Mono zusätzlich mit **Kyrillisch** (ist in der Quelle enthalten);
- Saira hat laut Quelle kein Kyrillisch: für `/ru/` eine zweite `@font-face` mit `unicode-range` U+0400–04FF einer
  passenden OFL-Schrift (Kandidat **Exo 2**, technischer, breiter Schnitt wie Saira), nur auf `/ru/`-Seiten geladen;
  Abnahme per Bildschirmfoto-Vergleich der Überschriften (3.2.8);
- OFL-Texte und `NOTICE`-Zeilen der Schriften aktualisieren.

#### 3.2.2 URL-Schema und Erzeugung

EN und DE bleiben, wie sie sind: ein HTML je Seite mit `<span lang="en">`/`<span lang="de">` und Umschalter. Jede
weitere Sprache ist ein **erzeugter, statischer Baum** `/<lang>/…` mit genau einer Sprache je Seite:

```
node tools/i18n/build-i18n.mjs              # erzeugt /es/ /fr/ /pt-br/ /pl/ /ru/ aus den EN/DE-Seiten + Wörterbüchern
node tools/i18n/build-i18n.mjs --check      # meldet veraltete erzeugte Seiten, schreibt nichts
node tools/i18n/build-i18n.mjs --missing fr # listet fehlende/veraltete Texte als JSON (Arbeitsliste für die Übersetzung)
node tools/i18n/test-i18n.mjs               # Prüfungen aus 6.2
```

Ablauf je Quellseite (Liste in `tools/i18n/pages.mjs`):

1. HTML lesen (nach `build-nav`, `build-docs`, `stamp-assets`).
2. Jedes Paar `lang="en"`/`lang="de"` wird zu **einem** Element `lang="<ziel>"` mit dem übersetzten Inhalt des
   englischen Elements; Attribute mit `data-de-alt|aria-label|title` ebenso; `<html lang>`, `<title>`,
   `meta description`, `og:*`, `data-title-*` werden ersetzt.
3. Relative Links umschreiben: Ziele, die es übersetzt gibt, bleiben im Sprachbaum; alle anderen (API-Doku,
   Rechtstexte, `assets/`) zeigen eine Ebene höher auf die Root-Fassung.
4. Banner (3.2.6), `hreflang`-Satz und `canonical` einsetzen, `<meta name="pw-root" content="../">` für Skripte.
5. Ergebnis schreiben; `stamp-assets.mjs` läuft über die erzeugten Seiten mit.

Die erzeugten Seiten liegen im Repository (GitHub Pages hat keinen Build-Schritt), werden aber **nie von Hand**
bearbeitet; `--check` ist Teil der Testfolge.

Skripte, die Daten per `fetch` laden (Roadmap, Changelog, Konfigurator, Verzeichnis), lösen Pfade künftig über
`root()` aus `assets/js/i18n.js` auf (liest `pw-root`), nicht mehr relativ zur Seite.

#### 3.2.3 Wörterbuch

Je Sprache **eine** Datei `tools/i18n/dict/<lang>.json`; Schlüssel ist der **englische Quelltext** (Leerraum
normalisiert, Markup erhalten), wie schon bei `tools/changelog/de.json`:

```json
{
  "schema": 1,
  "lang": "fr",
  "source": "en",
  "entries": {
    "Do I need a real RC transmitter?": "Ai-je besoin d’une vraie radiocommande ?",
    "Open the tab <strong>World rules</strong>.": "Ouvre l’onglet <strong>World rules</strong> (règles du monde)."
  }
}
```

- Ein Eintrag gilt als **veraltet**, wenn sein Schlüssel in keiner Quelle mehr vorkommt; `--missing` listet neue und
  veraltete Einträge. Geänderte englische Sätze erzeugen damit automatisch Arbeit.
- Dieselbe Datei bedient die HTML-Seiten (beim Bauen) und die per JavaScript gezeichneten Inhalte (Roadmap,
  Changelog, Konfigurator, Verzeichnis). `build-i18n` schreibt dafür je Sprache die zur Laufzeit gebrauchte
  Teilmenge nach `assets/i18n/<lang>.json`; nur Seiten dieser Sprache laden sie.
- Quellen der Texte: alle Paare der Handseiten, die von `build-docs` erzeugten Spieler-Seiten, `roadmap.json`,
  `changelog.json` (englische Zeilen), `platforms.json`, `addons/registry.json`, die Texte in
  `assets/js/configurator/i18n.js` und die Teile-Texte des Konfigurator-Katalogs (Beschreibungen, Pro/Contra,
  Warnungen; **nicht** die Teilenamen, 3.2.5).

**Glossar** `tools/i18n/glossary.json`: feste Begriffe je Sprache und Begriffe, die unverändert bleiben (Propwash,
Just More Parts, Whoop, Toothpick, Cinewhoop, Gate, Bando, OSD, VTX, KV, PID, Rates, Acro/Angle/Horizon, Sharecode,
PW3 …). Die Liste der Spiel-UI-Begriffe (3.2.5) erzeugt `build-i18n` aus der englischen Sprachdatei der Mod
(`--propwash <repo>`), damit sie mit jeder Version stimmt.

#### 3.2.4 Übersetzungsumfang

| Übersetzt (alle fünf Sprachen) | Nicht übersetzt (Link auf EN/DE) |
|---|---|
| Startseite inkl. FAQ und Download | Doku-Gruppe „Add-ons bauen“: Add-on-API, API-Referenz, Teile-Format |
| Konfigurator (Oberfläche, Kennzahl-Hilfen, Einsteiger-Builds, Teile-Texte) | Impressum, Datenschutz |
| `/convert/`, `/addons/`, `/addons/just-more-parts/` | |
| Roadmap, Changelog (alle Releases beider Produkte) | |
| Doku-Gruppe „Spielen“: Übersicht, Spielerhandbuch, Welt-Regeln, Blackbox-Tuning | |

Umfang grob (Stand 0.5.2, gezählt in den EN-Spans): Startseite ~6.400 Wörter, Spielerhandbuch ~19.600, Welt-Regeln
~5.500, Blackbox ~2.100, Doku-Übersicht ~1.200, dazu Changelog, Roadmap, Konfigurator und neue Seiten – zusammen
rund **50.000 Wörter je Sprache**, bei jedem späteren Release nur das Delta.

#### 3.2.5 Begriffe aus dem Spiel und Teilenamen

Die Mod gibt es nur auf Englisch und Deutsch. Wer die französische Seite liest, sieht im Spiel englische Knöpfe. Darum:

- **UI-Begriffe des Spiels bleiben englisch** (Tab-, Knopf-, Menü-, Tasten-, Welt-Regel-Namen), beim ersten Vorkommen
  je Abschnitt mit Übersetzung in Klammern: „ouvre l’onglet **World rules** (règles du monde)“.
- **Teilenamen bleiben englisch** (sie sind die Item-Namen im Spiel); Beschreibungen, Pro/Contra und Warnungen werden
  übersetzt.
- Zahlen und Einheiten folgen der Sprache (`Intl.NumberFormat`, Dezimalkomma in es/fr/pt-BR/pl/ru, geschütztes
  Leerzeichen vor Einheiten wie bisher).

#### 3.2.6 Banner

Auf **jeder** Seite jeder weiteren Sprache, direkt unter dem Kopf, nicht wegklickbar, eine Zeile (auf schmalen
Schirmen umbrechend). Links führen auf **dieselbe Seite** in EN bzw. DE (`?lang=en`/`?lang=de`, 3.2.7):

```html
<aside class="i18n-banner" role="note" lang="fr">
  <span class="i18n-banner__tag mono">IA</span>
  <p>Cette page a été traduite par IA. Seules les versions anglaise et allemande sont officielles.
     <a href="../roadmap/?lang=en" hreflang="en">Version anglaise</a> ·
     <a href="../roadmap/?lang=de" hreflang="de">Version allemande</a></p>
</aside>
```

Entwürfe (endgültig im jeweiligen Sprachstrang, immer nur das allgemeine Wort für KI):

| Sprache | Satz | Links |
|---|---|---|
| es | Esta página se ha traducido con IA. Solo el inglés y el alemán son oficiales. | Versión en inglés · Versión en alemán |
| fr | Cette page a été traduite par IA. Seules les versions anglaise et allemande sont officielles. | Version anglaise · Version allemande |
| pt-BR | Esta página foi traduzida com IA. Somente as versões em inglês e alemão são oficiais. | Versão em inglês · Versão em alemão |
| pl | Ta strona została przetłumaczona przy użyciu AI. Oficjalne są tylko wersje angielska i niemiecka. | Wersja angielska · Wersja niemiecka |
| ru | Эта страница переведена с помощью ИИ. Официальные версии — только английская и немецкая. | Английская версия · Немецкая версия |

Gestaltung im Propwash-Theme: `--panel` als Fläche, linker Rand 3 px `--accent`, Kürzel in `--mono` wie die
Statuszeile, Text `--text-2`; Kontrast ≥ 4,5:1. Eigene Datei `assets/css/i18n.css`.

#### 3.2.7 Sprachwahl und Erkennung

- Kopf: Der EN/DE-Umschalter bleibt; daneben ein Knopf mit Globus-Symbol „Sprachen“ öffnet eine Liste: English,
  Deutsch, dann Español, Français, Português (Brasil), Polski, Русский, jeweils mit dem kleinen Kürzel „KI“ in der
  Zielsprache. Bedienung wie das Features-Menü (Klick, Tippen, Tastatur, Escape).
- Wahl einer weiteren Sprache navigiert zur gleichen Seite im Sprachbaum und speichert `propwash-lang=<lang>`.
- EN/DE auf einer übersetzten Seite navigiert zur Root-Seite mit `?lang=en|de`; das Kopf-Skript der Root-Seiten liest
  den Parameter, speichert ihn und entfernt ihn per `history.replaceState` aus der Adresse.
- **Keine automatische Weiterleitung.** Auf Root-Seiten erscheint bei passender Browsersprache und ohne gespeicherte
  Wahl einmalig eine dezente Zeile „Diese Seite gibt es auch auf Français (KI-Übersetzung)“ in der Zielsprache;
  Schließen merkt sich die Ablehnung.
- `hreflang`: jede Seite nennt alle Fassungen (`en` und `x-default` = Root, `de` = Root mit `?lang=de`, die fünf
  Sprachbäume); `sitemap.xml` führt die Sprachseiten mit `xhtml:link`-Alternativen.

#### 3.2.8 Qualität ohne Muttersprachler

Siehe 6.2 (automatisch) und 6.4 (Prüfer). Kern: Markup-, Zahl- und Link-Gleichheit je Eintrag, Glossar- und
UI-Begriff-Prüfung, Rückübersetzung einer Stichprobe durch einen unabhängigen Prüfer, Bildschirmfotos aller Seiten in
drei Breiten mit Überlauf-Prüfung.

### 3.3 Plattformen und Downloads

`assets/data/platforms.json` ist die einzige Stelle für Download-Ziele und Prüfstatus:

```json
{
  "schema": 1,
  "products": {
    "propwash":      { "version": "0.6.0",
                       "curseforge": { "url": "https://www.curseforge.com/minecraft/mc-mods/propwash-fpv", "status": "review" },
                       "modrinth":   { "url": "https://modrinth.com/mod/propwash-fpv", "status": "review" } },
    "justmoreparts": { "version": "1.2.0",
                       "curseforge": { "url": "https://www.curseforge.com/minecraft/mc-mods/propwash-just-more-parts", "status": "review" },
                       "modrinth":   { "url": "https://modrinth.com/mod/propwash-just-more-parts", "status": "review" } }
  },
  "source": { "propwash": "https://github.com/LardTom/propwash-fpv", "justmoreparts": "https://github.com/LardTom/propwash-justmoreparts" }
}
```

- `status`: `live` (Knopf „Download auf …“), `review` (Knopf mit Punkt „in Prüfung“), `pending` (kein Link, Text
  „erscheint auf CurseForge und Modrinth“) für künftige Produkte.
- `tools/build-platforms.mjs` schreibt die Download-Knöpfe, die Statuszeile im Hero und die Add-on-Zeile der
  Startseite in markierte Bereiche (`<!-- platforms:… -->`), damit sie ohne JavaScript stimmen; Roadmap und
  Verzeichnis lesen die Datei zur Laufzeit.
- **GitHub:** nur als kleines Icon mit `aria-label` „Quellcode“ (Hero/Download und Abschnitt „Quellcode & Lizenz“).
  Die beiden Text-Knöpfe „… auf GitHub“ im Lizenzabschnitt werden zu Icons; die FAQ zur Lizenz verlinkt das Repository
  über das Icon. Nirgends `…/releases`, nirgends „Download“ neben GitHub.

### 3.4 FAQ

Neue Reihenfolge; heute hat kein Eintrag eine Id, künftig bekommt jeder eine (Deep-Links aus dem Discord):

| # | Frage (EN) | Id | Status |
|---|---|---|---|
| 1 | Do I need a real RC transmitter? | `faq-radio` | bleibt |
| 2 | My radio or controller isn’t detected. What now? | `faq-radio-not-detected` | bleibt |
| 3 | Can I use my real FPV goggles? | `faq-goggles` | neu |
| 4 | How realistic is it? | `faq-realism` | bleibt |
| 5 | I’ve never flown FPV. Can I learn it here? | `faq-learn` | bleibt |
| 6 | Does it work in multiplayer? | `faq-multiplayer` | bleibt (keine Erwähnung der zurückgestellten Server-Mod) |
| 7 | Survival or creative? | `faq-survival` | bleibt |
| 8 | Is there VR? | `faq-vr` | neu |
| 9 | Is there motion blur? | `faq-motion-blur` | neu |
| 10 | My old share codes (PW1/PW2) – do they still work? | `faq-old-codes` | neu |
| 11 | Can I try anything without installing the mod? | `faq-try` | bleibt |
| 12 | Where can I download it? | `faq-download` | nur CurseForge/Modrinth aus `platforms.json` |
| 13 | Is Propwash open source? | `faq-license` | ersetzt „Is the source code available?“ |
| 14 | Can I make my own parts or add-ons? | `faq-addons` | ergänzt: eigener Namensraum, Regeln, Verzeichnis |
| 15 | Which languages are there? | `faq-languages` | neu |

Textentwürfe der neuen Einträge:

- **faq-license** – EN: *No. Propwash is source-available: the code is public under the PolyForm Shield License
  1.0.0. You may read it, build it yourself, put Propwash in modpacks and send pull requests. You may not re-upload it
  or build a competing drone mod from its code. The add-on API and the docs are MIT licensed, and add-ons follow the
  rules for add-ons.* – DE: *Nein. Propwash ist source-available: Der Code ist öffentlich, unter der PolyForm Shield
  License 1.0.0. Du darfst ihn lesen, selbst bauen, Propwash in Modpacks aufnehmen und Pull Requests schicken. Nicht
  erlaubt sind erneutes Hochladen und eine konkurrierende Drohnen-Mod aus dem Code. Add-on-API und Doku stehen unter
  MIT, für Add-ons gelten die Regeln für Add-ons.* (Link auf `docs/api/#rules`; im Lizenzabschnitt der Startseite
  ausdrücklich „nicht Open Source“.)
- **faq-goggles** – EN: *You don’t need goggles: Propwash shows the FPV picture on your screen. A mod can’t send video
  to your goggles. If your goggles have an HDMI input, you can use them as a screen and mirror your desktop to them.
  Your radio, on the other hand, works directly over USB.* – DE: *Du brauchst keine Brille: Propwash zeigt das
  FPV-Bild auf deinem Bildschirm. Eine Mod kann kein Video an deine Brille senden. Hat deine Brille einen
  HDMI-Eingang, kannst du sie als Bildschirm nutzen und den Desktop darauf spiegeln. Deine Funke dagegen geht direkt
  per USB.*
- **faq-vr** – EN: *No. Propwash doesn’t support VR, and it isn’t planned right now.* – DE: *Nein. Propwash
  unterstützt kein VR, und das ist derzeit nicht geplant.*
- **faq-motion-blur** – EN: *Motion blur isn’t a Propwash feature. Shader packs can add it: install Iris and a shader
  pack with motion blur. With an active shader pack, Propwash switches its video effect to a simpler overlay. Iris
  toggles shaders with K by default, which Propwash uses for its menu – change one of the two key bindings.* – DE:
  *Motion Blur ist kein Propwash-Feature. Shaderpacks können es nachrüsten: Iris und ein Shaderpack mit Motion Blur
  installieren. Mit aktivem Shaderpack nutzt Propwash für den Videoeffekt ein einfacheres Overlay. Iris schaltet
  Shader standardmäßig mit K um, Propwash öffnet damit sein Menü – belege eine der beiden Tasten um.*
- **faq-old-codes** – EN: *Yes. Propwash 0.6.0 and the configurator still read them. New codes start with PW3 and can
  carry parts from any add-on; they need Propwash 0.6.0 or newer. To share a build in the new format, convert the old
  code at propwashfpv.com/convert/ and post the PW3 code next to the old one, so players on 0.5 can still use it.* – DE
  entsprechend.
- **faq-languages** – EN: *The game is in English and German. This website is also available in Spanish, French,
  Brazilian Portuguese, Polish and Russian, translated with AI; only English and German are official.* – DE
  entsprechend. Keine Zusage zu weiteren Spielsprachen.

### 3.5 Web-Konfigurator

#### 3.5.1 Kennzahlen erklären

- Jede Kennzahl der Statistik (heute 23 Ids in `TEXT.stats` von `i18n.js`, von `mass_grams` bis
  `props_in_view_percent`) bekommt ein Info-Symbol. Hover, Antippen und Fokus öffnen eine kleine Erklärung:
  **was** die Zahl bedeutet, **woran** man gute Werte erkennt, und den **typischen Bereich der Klasse** des gewählten
  Rahmens.
- Daten: `assets/data/configurator/stathelp.json` (EN/DE, Schlüssel = Kennzahl-Id; Übersetzungen über 3.2.3) und
  Klassenbereiche aus den Zielkorridoren von Just More Parts (Katalog §1.7) bzw. der Propwash-Klassen:

```json
{
  "schema": 1,
  "stats": {
    "thrust_to_weight": {
      "text": { "en": "How much stronger full thrust is than the weight. Around 2:1 barely lifts off and feels sluggish; whoops fly at 4–7:1, 5″ freestyle and race quads at 10:1 and more.",
                "de": "Wie viel stärker der volle Schub als das Gewicht ist. Um 2:1 hebt die Drohne gerade ab und wirkt träge; Whoops fliegen mit 4–7:1, 5″-Freestyle und Racer mit 10:1 und mehr." },
      "range": { "whoop": [3.5, 7], "toothpick": [5, 8], "cinewhoop": [3.5, 5], "freestyle": [10, 15], "race": [10, 15], "long_range": [3.5, 11], "x_class": [5, 9] }
    }
  }
}
```

- Weitere Entwürfe (DE): **Schwebegas** – Gasstellung, bei der die Drohne in der Luft steht; unter 25 % viel Reserve,
  aber feinfühliges Gas, über 50 % wenig Reserve für Manöver. **Flugzeit gemischt** – Minuten bis zur Landespannung
  bei typischem Fliegen dieser Klasse. **Landespannung je Zelle** – Spannung unter Last am Ende; unter etwa 3,3 V je
  Zelle altert ein LiPo schnell. **Props im Kamerabild** – Anteil des Bildes, den die Propeller verdecken; Racer
  wollen 0–5 %, Cinewhoops mit Schutzringen zeigen mehr.
- Werte außerhalb des Klassenbereichs bekommen einen dezenten Hinweis (kein Fehler; Fehler bleiben Sache der
  Kompatibilitätsprüfung).
- Ein Test verlangt für jede Kennzahl-Id der Analyse einen Text in EN, DE und jeder aktiven Sprache.

#### 3.5.2 Einsteiger-Builds

- Drei **Propwash-Presets** (laufen ohne Add-ons): `propwash:whoop` („Dein erster Whoop: klein, leicht, mit
  Schutzringen – verzeiht Crashs drinnen“), `propwash:cinewhoop` („Ruhig und gutmütig, ideal für Kamerafahrten“),
  `propwash:freestyle` („Der 5″-Standard der echten FPV-Welt – nach den ersten Lektionen der Flugschule“).
- Im Teile-Bereich oben eine Leiste „Empfohlen für Einsteiger“ mit drei Karten (Bild aus dem Viewer-Screenshot,
  Kernzahlen, „Laden“, „Code kopieren“) und dem Satz „Fang im Angle-Modus an“ mit Link auf die Flugschule.
- Daten `assets/data/configurator/beginner.json` (Preset-Id, Texte, Reihenfolge); Codes werden zur Laufzeit mit PW3
  erzeugt, nicht gespeichert (bleiben so bei Tabellenänderungen richtig).
- Test: Presets existieren, Analyse ohne Warnungen, Werte im Klassenbereich.

#### 3.5.3 PW3, alte Codes und Umrechner

- Neues Modul `assets/js/configurator/sharecode3.js` nach der PW3-Spezifikation des PW3-Strangs; Prüfgrundlage sind
  die PW3-Testvektoren aus dem Web-Export (3.7), Kodieren **bytegleich** zur Mod.
- `SHARE_FORMAT` = `PW3`: Die Website **schreibt nur PW3** und liest PW1, PW2, PW3.
- Öffnet jemand `configurator/#PW1-…` oder `#PW2…`, lädt der Konfigurator den Build, ersetzt die Adresse per
  `history.replaceState` durch den PW3-Code und zeigt „Alter Code umgerechnet“ mit Kopier-Knopf.
- Fehlende Add-ons: Teile aus Namensräumen ohne geladenen Export erscheinen als „braucht *Name Version*“ mit Link aus
  dem Code bzw. dem Verzeichnis und Angebot, das Jar lokal zu laden (3.5.4); Prüfsummen-Abweichung → Hinweis „Code
  passt nicht zur geladenen Version von *X*“.
- **`/convert/`**: ein Textfeld, das beliebigen Text annimmt (auch einen ganzen Discord-Post). Alle PW1-/PW2-Codes
  darin werden gefunden (höchstens 100 je Durchgang), umgerechnet und als Liste gezeigt: Name, Klasse, Status (OK /
  unbekannte Teile / beschädigt), neuer Code mit „Kopieren“ und „Im Konfigurator öffnen“, oben „Alle kopieren“.
  PW3-Codes werden nur geprüft. Die Seite nutzt dieselben Module wie der Konfigurator, ohne 3D-Viewer.
- Umrechnung ist eindeutig, weil Codes vor 0.6.0 nur Teile aus Propwash und JMP enthalten; Fremdteile aus PW2-Text-Ids
  wandern mit vollem Namensraum mit (PW3-Vorgabe 14.10.).

#### 3.5.4 Add-on-Jars lokal laden

- Knopf „Add-on-Jar öffnen“ (Datei-Dialog und Ziehen-und-Ablegen). Das Jar wird **nur im Speicher** gelesen, nicht
  hochgeladen und nicht gespeichert; ein Neuladen der Seite vergisst es.
- Gelesen werden nur: `fabric.mod.json` bzw. `META-INF/neoforge.mods.toml` (Id, Name, Version), Teile-Dateien
  `data/<ns>/propwash_parts/<art>/*.json`, `assets/<ns>/lang/{en_us,de_de}.json`, Modelle und Texturen unter
  `assets/<ns>/models/part/**`, `assets/<ns>/textures/**`, `assets/<ns>/items/drone_render/**`, die PW3-Teile-Tabelle.
- Ein JavaScript-Teileleser `partjson.js` bildet den Teile-Leser der Mod (`parts/PartJson.java`) nach. **Abnahme:** Für
  das JMP-1.2.0-Jar müssen die im Browser gelesenen Teile Feld für Feld den `parts.json` des offiziellen Exports
  gleichen. Teile, die ein Add-on nur in Java anmeldet, kann der Browser nicht sehen; sie erscheinen als unbekannt mit
  Hinweis „Dieses Add-on braucht einen Web-Export“.
- **Sicherheitsgrenzen:** Jar ≤ 64 MB, ≤ 20.000 Einträge, entpackt ≤ 256 MB, Verhältnis ≤ 100:1 je Eintrag, JSON ≤ 2 MB,
  Texturen ≤ 1024 × 1024 PNG; Pfade mit `..` oder absolut werden ignoriert; alle Texte aus dem Jar nur über
  `textContent`, nie als HTML; kein `eval`, keine Netzwerkanfrage.

#### 3.5.5 Add-ons im Konfigurator

- Geladen werden der Basis-Export (Propwash) und die Exporte aller **offiziellen** Add-ons aus dem Verzeichnis; Add-ons
  der Community sind abschaltbar und standardmäßig aus, außer ein geöffneter Code braucht sie.
- Leiste „Add-ons“ im Teile-Bereich: Name, Version, Quelle (Verzeichnis / lokal), Schalter.

#### 3.5.6 XT60 im 3D-Viewer

> **Variante 2 gilt** (`fixes-polish.md` §3.B, Befund PLUG-1): `assembly.js` zeichnet kein Steckerpaar und keine
> Kabelstücke, sondern blendet mit Akku dieselben Elemente aus wie das Spiel (Buchse, Balancer-Stecker und Kabel des
> Akkus, die beiden `pigtail` des Stacks). Weil die kompakten Modelle keine Element-Namen tragen, findet das Web sie
> über Buchse und Pigtail-Enden in den Daten. Der Render-Test prüft für jede Kombination, dass keines davon gezeichnet
> wird; der Absatz darunter ist überholt.

`assembly.js` zeichnet das gesteckte Steckerpaar und die Kabelstücke **nach demselben Verfahren wie der
Kern-Renderer** (Anker und Regeln aus dem Export, Anhang A.2). Der Render-Test prüft es für jede Kombination
(6.2).

### 3.6 Add-on-Verzeichnis

`assets/data/addons/registry.json` ist die **Vergabeliste**: nur anhängen, Kennungen nie neu vergeben, gestrichene
Add-ons bleiben mit `status: "withdrawn"` stehen.

```json
{
  "schema": 1,
  "addons": [
    { "namespace": "justmoreparts", "short_id": "<laut PW3-Format>", "name": "Propwash: Just More Parts",
      "author": "lardtom", "official": true, "status": "listed", "since": "2026-10",
      "page": "addons/just-more-parts/", "license": "PolyForm-Shield-1.0.0",
      "requires": { "propwash": ">=0.6.0" },
      "platforms": "justmoreparts",
      "web_export": { "version": "1.2.0", "path": "assets/data/addons/justmoreparts/1.2.0/" },
      "text": { "en": "143 more realistic parts …", "de": "143 weitere realistische Teile …" } }
  ]
}
```

- **Seite `/addons/`:** offizielle Add-ons oben, darunter geprüfte Community-Add-ons (Kennzeichen „geprüft nach den
  Regeln für Add-ons“), Filter nach Art (Teile, Spielmodi, Zubehör, Werkzeuge), jede Karte mit Kurztext, Versionen,
  Download-Knöpfen (nur CurseForge/Modrinth), „Im Konfigurator zeigen“ (bei Web-Export) und Link auf die Regeln.
- **Start-Bestand 0.6.0:** nur das offizielle Add-on Just More Parts 1.2.0. Community-Add-ons
  kommen erst nach Prüfung durch lardtom gegen `ADDONS.md` (Namensraum, Name „X for Propwash“, Lizenz).
- **Antrag:** Abschnitt „Add-on eintragen lassen“ auf `/addons/`: per Discord-Ticket an lardtom oder als Issue im
  Website-Repository (Vorlage „Add-on listing“ mit Namensraum, Name, Lizenz, Links, gewünschte Kurzkennung,
  optional Web-Export).
- **Kurze Kennungen:** Format und Bedeutung legt das PW3-Design fest; das Verzeichnis führt nur die Vergabe ohne
  Doppelte. Wie die Mod sie kennt (eingebaute Kopie in Propwash und/oder Eintrag in der Tabelle des Add-ons), regelt
  der PW3-Strang (Anhang A.1).
- **Web-Exporte** liegen versioniert unter `assets/data/addons/<namensraum>/<version>/` (Format 3.7); der Konfigurator
  lädt die in `registry.json` genannte Version.
- Test `tools/test-addons.mjs`: Schema, Namensräume und Kurzkennungen eindeutig, **append-only** gegen den Stand des
  letzten Release-Tags, `propwash` nicht vergeben, Download-Ziele nur über `platforms.json`, jeder genannte Export
  vorhanden und vom Konfigurator ladbar.

### 3.7 Web-Export in Propwash (`/propwash webexport`)

Heute erzeugt JMP den Export für die Website (Dev-Server, Quell-Set `devtest`, Paket
`de.lardtom.justmoreparts.export`). Mit PW3 gehört der Codec der Basis, und das Verzeichnis braucht Exporte jedes
Add-ons. Darum:

- Neues Paket `de.lardtom.propwash.webexport` (Hauptcode, nicht `devtest`): `WebExportModule` meldet über
  `ServerBus.REGISTER_COMMANDS` den Befehl `/propwash webexport [<namensraum>|all]` an (Rechtestufe 4; im
  Einzelspieler mit Cheats). Der Code wird aus JMP übernommen (`WebExport`, `PartData`, `RuleCheck`, `AssetIndex`,
  `LangTable`, `ExportJson`, `Problems`) und auf Namensräume verallgemeinert; er nutzt nur API und Ressourcen der Mods.
- Ziel: `<spielordner>/propwash/web-export/`, deterministisch wie bisher (feste Sortierung, Zeitstempel nur im Manifest).
- **Format 2** (Umbau gegenüber Format 1, deshalb neue Hauptnummer):

```
web-export/
  manifest.json               format 2, Propwash- und API-Version, Liste der Namensräume
  base/                       nur von Propwash: rules.json, paint.json, tune.json,
                              sharecode/spec.json (PW3 + eingefrorene PW1/PW2-Tabellen), sharecode/vectors.json
  <namensraum>/
    manifest.json             Id, Name, Version, Kurzkennung (falls vergeben), Tabellen-Prüfsumme, benötigte Propwash-Version
    parts.json  mounts.json  chemistries.json  presets.json  analysis/…  share_table.json
    lang/en_us.json  lang/de_de.json
    assets/<namensraum>/…     Modelle, Render-Definitionen, Texturen, nur was Teile benutzen
  problems.json
```

- Gradle: `runWebExport` (Fabric, Dev-Server) ruft den Befehl mit `all` auf und beendet sich; JMP behält einen
  gleichnamigen Task, der Propwash aus `mavenLocal` startet.
- Regression: Für Propwash- und JMP-Teile müssen `parts.json`, `mounts.json`, `rules.json`, `paint.json`,
  `tune.json` und `analysis/` inhaltlich dem Format-1-Export von JMP 1.1.1 entsprechen (Werkzeug vergleicht,
  zulässige Abweichungen nur Struktur und die neuen Teile).

### 3.8 Server-Mod-Seite und Doku „Server betreiben“ (entfällt)

Entfällt für 0.6.0: Die Server-Mod ist zurückgestellt (E20). Es gibt weder `/addons/server/` noch `/docs/server/`, keinen
Eintrag in `platforms.json`, im Verzeichnis oder in der FAQ, kein drittes Produkt im Changelog. `test-links` prüft, dass
kein Link auf diese Pfade zeigt. Weiterhin **keine** Join-Anleitung für den Community-Server.

### 3.9 Doku zu 0.6.0

Jeder Feature-Strang liefert einen **Doku-Baustein** als Markdown in `docs/<thema>.md` des Propwash-Repositorys
(wie heute `docs/vtx-power.md`) plus Bildschirmfoto-Schritte im Selbsttest. Der Doku-Strang der Website setzt sie in
`tools/docs/pages/*.mjs` um (EN/DE nebeneinander).

| Funktion 0.6.0 | Seite / Abschnitt | Quelle |
|---|---|---|
| Share-Codes PW3, fehlende Add-ons, Einkaufsliste | Spielerhandbuch, neuer Abschnitt „Builds teilen“ (`#share`) | PW3-Strang |
| Freecam-Drohne mit eigenem K-Menü | Spielerhandbuch `#freecam`; Welt-Regel automatisch | Freecam-Strang |
| Flugbahn-Marker im OSD | Spielerhandbuch, OSD | OSD-Strang |
| RX-Block | Spielerhandbuch `#rx-block` (Anlernen, Kanal, Reichweite, Redstone) | RX-Strang |
| `/propwash clear` | Spielerhandbuch, Befehle | Befehl-Strang |
| Kamerawinkel im K-Menü | Spielerhandbuch, Steuerung | K-Menü-Strang |
| Gate-Tutorial / Flugschul-Kapitel | Spielerhandbuch, Rennen | Gate-Strang |
| Slime federt, Wolle fängt ab | Spielerhandbuch, Welt | Physik-Strang |
| Motor-Hitze | Spielerhandbuch, Bauen | Physik-Strang |
| Druckplatten reagieren auf Drohnen (PR #1) | Spielerhandbuch, Welt; Changelog nennt den Autor | Pflege-Strang |
| Zubehör mit Aktion, Schalterfunktionen, OSD-Elemente, Kollisions-Haken | Add-on-API + Referenz (aus dem Code) | API-Strang |
| Namensraum `propwash` reserviert, Regeln | Add-on-API, Abschnitt `#rules` „Regeln für Add-ons“ | `ADDONS.md` |
| Just More Parts 1.2.0 | `/addons/just-more-parts/`, Konfigurator, Changelog | JMP-Stränge |

**Regeln für Add-ons:** Die englische Fassung steht in `ADDONS.md` (Propwash-Repository, maßgeblich); `extract.mjs`
liest sie, die deutsche Fassung (aus dem Richtlinien-Entwurf vom 17.10.) steht in `tools/docs/pages/api.mjs`.
`test-docs` bricht ab, wenn sich `ADDONS.md` ändert (Prüfsumme) und die deutsche Fassung nicht nachgezogen ist.

**Startseite „Was ist neu“:** drei Highlights von 0.6.0 – Vorschlag: Freecam-Drohne, RX-Block, Sharecodes für alle
Add-ons. Bilder aus den Bildschirmfotos des Release-Commits.

### 3.10 Changelog

`tools/build-changelog.mjs` bleibt bei zwei Produkten (Propwash, Just More Parts); 0.6.0 und 1.2.0 kommen in der
Spieler-Form dazu, deutsche Texte wie bisher in `tools/changelog/de.json`.

### 3.11 Roadmap-Pflege

- **Jetzt (eigener Branch `site/0.6.0-roadmap-start`, nur auf Go live):** 0.6.0 unter „Im nächsten Update“ mit dem, was
  schon öffentlich ist: Sharecodes für alle Add-ons, mehr Add-on-API, nächste Version von Just More Parts. Die schon
  angekündigte Server-Mod steht ohne Datum in einer späteren Gruppe. Danach Mob Defense, programmierbare Kameradrohnen, Backport 1.21.1 (ohne Add-on-API, unregelmäßig, mit
  ausgewählten Add-on-Inhalten), unter „Vielleicht später“ wie live.
- **Mit dem Release:** 0.6.0 nach „Schon da“ mit Highlights und Changelog-Link; nächste Gruppe Add-ons und Backport.
- Keine Spoiler: Freecam, RX-Block, Flugbahn-Marker u. Ä. erscheinen erst mit dem Release in Changelog und „Was ist
  neu“. Keine neuen Ideen ohne Freigabe.

### 3.12 Just More Parts 1.2.0

#### 3.12.1 Ohne eigenen Codec

| Heute (1.1.1) | 1.2.0 |
|---|---|
| `share/**` (30 Klassen: `ShareCodec`, `Pw2Codec`, `Pw2Tables`, `ShareTables`, `PartAliases`, Bit-/Base-Kodierer, `share/client/**` mit Share-Bildschirm, Import, Einkaufsliste, Szene, Konfigurator-Tab, Werkbank-Erweiterung) | entfällt; die Funktionen liefert Propwash 0.6.0 |
| `command/JmpCommands` mit `/jmp code export|import|info|give` | `/jmp code …` leitet an den Propwash-Befehl des PW3-Strangs weiter und schreibt einmal je Sitzung „Moved to /propwash …“; entfällt mit JMP 1.3.0 |
| Web-Export im Quell-Set `devtest` (`export/**`, `dev.WebExportRun`) | entfällt; `runWebExport` startet `/propwash webexport all` (3.7) |
| Share-Tests (`Pw2CodecTest`, `ShareNamesTest` u. a.) | entfallen bzw. wandern sinngemäß in den PW3-Strang; neu `share/ShareTableTest` und `share/LegacyCodesTest` (6.3) |
| `docs/design/sharecode*.md`, `web-export.md` | Kopfzeile „abgelöst ab 1.2.0“, Verweis auf die Propwash-Dokumente; die Inhalte bleiben als Referenz für die alten Formate |

**Teile-Tabelle:** Pfad und Schema legt das PW3-Design fest. Vorschlag zur Abstimmung (je Teile-Art wie in PW2,
damit die Nummern kurz bleiben):

```json
{
  "format": 1,
  "namespace": "justmoreparts",
  "kinds": {
    "frame":   ["justmoreparts:whoop_75", "…"],
    "motor":   ["justmoreparts:m0702_23000", "justmoreparts:m0702_27000", "…", "justmoreparts:m0702_30000"],
    "battery": ["…"]
  },
  "deprecated": { "justmoreparts:<alte_id>": "justmoreparts:<ersatz>" }
}
```

- **Reihenfolge:** je Art genau die JMP-Einträge der eingefrorenen PW2-Tabellen in ihrer Reihenfolge, danach die neuen
  Teile von 1.2.0. Das hält die Umrechnung PW2 → PW3 trivial nachprüfbar.
- `deprecated` übernimmt die heutige `PartAliases`-Tabelle.
- Das Prüfwerkzeug des PW3-Strangs läuft im JMP-Build (`check`): Jedes angemeldete JMP-Teil steht in der Tabelle, die
  Tabelle ist gegenüber `common/src/test/resources/share/table-1.2.0.json` (Schnappschuss je Release) nur hinten
  erweitert, jede veraltete Id hat einen Ersatz.
- Für die eingefrorenen Alt-Tabellen der Basis liefert der JMP-Strang einmalig die JMP-Anteile aus `Pw2Tables`,
  `ShareTables` (PW1-Namensraum-Tabelle) und `PartAliases` als JSON an den PW3-Strang.

#### 3.12.2 Whoop-Motoren mit sehr hoher KV

| Id (vorläufig) | Klasse | Einsatz | Ziel |
|---|---|---|---|
| `m0702_30000` | 0702, ≈ 30000 KV, 1S | 65-mm-Whoop, Rennen | Brücke zwischen 27000 und dem schnellsten |
| `m0702_33000` | 0702, ≈ 33000 KV, 1S | 65-mm-Renn-Whoop, maximaler Punch | Wunsch aus dem Discord (31800/33800 KV genannt) |
| `m0802_30000` | 0802, ≈ 30000 KV, 1S | 75-mm-Renn-Whoop | 75 mm bisher nur bis 22000 KV (Propwash) |

- **Endgültige KV und Ids** kommen aus dem Datenbeleg: Median der Datenblätter der Klasse, KV auf 100 gerundet (nennt
  die Klasse etwa 31800 KV, heißt der Motor `m0702_31800`). Id- und Namensschema wie bisher (`m<stator>_<kv>`,
  „0702-Motor, 30000 KV“), **keine Markennamen**.
- **Datenbeleg:** je Motor mindestens drei Datenblätter verschiedener Hersteller derselben Stator-/KV-Klasse mit Masse,
  KV, Spitzenstrom und, wo angegeben, Widerstand. Öffentlich (in `docs/design/parts-catalog.md` §3) stehen nur die
  Klassenwerte („n = 4, Masse 1,8–2,1 g, Spitzenstrom 3,8–4,5 A“); die Quellenliste mit Herstellern und Links liegt in
  `docs/design/data-sources/` und wird vom öffentlichen Export ausgeschlossen (`tools/public_export.json`).
- **Werte nach Katalog §1.3:** Masse und Spitzenstrom aus der Datenblatt-Klasse; Widerstand über `KV·√R` des Stators
  (0702/0802-Reihe), Thermik wie beim 0702/27000 (niedrigere Grenzen, winzige Wärmekapazität).
- **Regel-Änderung §1.3:** Das Drehzahl-Fenster der Whoops (80–100 k/min Leerlauf) gilt für Allround-Whoops; für
  **1S-Renn-Whoops** gilt neu bis 125 k/min (33000 KV × 3,7 V ≈ 122 k/min ist real üblich). `MotorDataTest` bekommt
  die Unterscheidung.
- **Prüfstand** (`MotorBenchTest`): Referenzaufbau 65 mm bzw. 75 mm auf 1S-HV mit passender C-Rate; neue Korridorzeile
  „Renn-Whoop 1S“ (Vorschlag: T/W 5–8, Schwebegas 20–35 %, Flugzeit gemischt 2,5–4,5 min, Höchstgeschwindigkeit
  55–75 km/h; `heavy_sag` erlaubt, `battery_overload` nur mit Akkus unter 60C). Pro/Contra nur mit ≥ 1 % Abstand,
  z. B. „mehr Topspeed als 27000 KV“ / „kürzere Flugzeit, wird heiß, Akku bricht ein“.
- **Preset** `justmoreparts:whoop65_race_1s` „65-mm-Renn-Whoop (1S)“ mit dem schnellsten neuen Motor; Werte im
  Korridor, ohne Warnungen außer den erlaubten.
- Rezepte eindeutig und spiegelgleich (`parts_sync.py --check`), KV-Kennzeichen Glowstone-Staub wie die übrigen
  hohen KV, Unterscheidung über eine zusätzliche Zutat; Truhen-Beute in `village_toolsmith` und `trial_chambers`;
  Modelle wie die vorhandenen 0702/0802-Motoren mit eigener Akzentfarbe; Sprachtexte EN/DE mit Shift-Hilfe.
- **Abhängigkeit Motor-Hitze:** 0.6.0 prüft die Hitze-Drosselung der Motoren. Die Prüfstandswerte der neuen (und
  aller) JMP-Motoren werden nach dem Merge dieses Strangs in die Integration erneut gemessen; Texte, die Grenzen
  nennen, ziehen mit.

#### 3.12.3 XT60 gesteckt

> **Variante 2 gilt** (`fixes-polish.md` §3.B, Befund PLUG-1): Propwash zeichnet mit Akku keinen gesteckten Stecker und
> kein Kabel, sondern blendet Stecker und Kabel von Akku und Stack aus. Für JMP heißt das: nur die Varianten `_mounted`
> (+ `_paint`) der Stacks mit Stromkabeln namens `pigtail`, Akku-Modelle unverändert; Park-Positionen und
> `power_lead`-Einträge entfallen (ein vorhandener Eintrag wird ignoriert). Das Vorgehen darunter ist überholt.

Bestand: 30 JMP-Akkus mit 88 Modellen (davon 78 mit Elementen `connector`/`connector_grip`), 12 Stacks mit 24
Modellen (20 mit Stecker), Stecker-Arten PH2.0, BT2.0, BT3.0, A30, XT30, XT60, XT90, AS150. Heute bringen Akku- und
Stack-Modell je einen eigenen Stecker mit, die getrennt im Raum stehen (Playtest-Bild vom 15.10.).

Vorgehen nach dem Vertrag des Kern-Strangs (Anhang A.2; weicht er ab, gilt er, das Vorgehen bleibt):

- Werkzeug `tools/connector_models.py`: entfernt die Stecker-Elemente aus allen Akku- und Stack-Modellen (auch
  `_paint`, `_under`), lässt die Kabel bis zum Kabelende stehen und schreibt das Kabelende als **Anker** in die
  Render-Definition (`items/drone_render/battery|stack/…`), getrennt für Akku oben und unten.
- Den gesteckten Stecker samt Kabelführung zeichnet der Kern-Renderer aus Steckerart (`connector` des Akkus),
  Ankern und Rahmen-Lage; JMP-Rahmen bekommen nur dann eigene Park-Positionen, wenn die Standardregel des Kerns bei
  einem Rahmen sichtbar falsch liegt (Ergebnis der Bildschirmfotos).
- Kein Rahmen-Modell ändert sich sonst; die Prüfung erfolgt im Spiel (Bildschirmfotos) und im Web-Viewer (Render-Test).

#### 3.12.4 Versionen und Abhängigkeiten

- `version=1.2.0`, `propwash_version_range=[0.6.0,)`, `propwash_fabric_range=>=0.6.0`, `propwash_api_version` = die
  API-Version von 0.6.0 (Annahme 1.7).
- Beschreibung in `gradle.properties` und Modrinth-Text ohne „share codes“ als JMP-Funktion; stattdessen „works with
  Propwash share codes (PW3)“.
- JMP hat keine eigenen Netzwerk-Pakete; am Protokoll ändert sich durch JMP nichts.

---

## 4. Kompatibilität

| Bereich | Verhalten |
|---|---|
| Alte Welten | JMP-Items und -Teile behalten ihre Ids; Drohnen aus 1.1.x laden unverändert. Neue Motoren sind reine Ergänzung. XT60 ist reine Darstellung, keine Daten in Welt oder NBT. |
| Alte Codes | PW1 und PW2 (auch mit JMP-Teilen und Fremd-Text-Ids) liest Propwash 0.6.0 und die Website; geschrieben wird nur PW3. `LegacyCodesTest` belegt das mit allen Testvektoren aus JMP 1.1.1. |
| Alte Links | `configurator/#PW1-…` und `#PW2…` öffnen weiter und werden auf PW3 umgestellt; alle Anker der Startseite und Doku bleiben. |
| PW3 auf altem Stand | Spieler mit 0.5.x können PW3 nicht lesen; Website und FAQ sagen „PW3 braucht Propwash 0.6.0“. |
| JMP 1.1.1 + Propwash 0.6.0 | startet im Altmodus (Übersicht `README.md` §3.5): JMP-Teile da, nur der Sharecode-Knopf von Propwash, PW3 über den Verzeichnis-Abzug, Hinweis „Just More Parts 1.2.0 empfohlen“. Keine Unverträglichkeit in den Metadaten, damit ein Update nur von Propwash das Spiel nicht unstartbar macht. |
| JMP 1.2.0 + Propwash 0.5.x | vom Loader abgelehnt (Bereiche Fabric `>=0.6.0-`, NeoForge `[0.6.0-alpha,)`; beide nehmen auch `0.6.0-dev.N` und `0.6.0-rc.N` an). Deshalb erscheint JMP 1.2.0 erst nach Propwash 0.6.0. |
| Server/Client | JMP 1.2.0 nur beim Spieler: bleibt auf fremden Servern aus wie bisher (client-only-addons). Server mit JMP verlangt JMP 1.2.0 beim Spieler (Beitrittsprüfung). |
| API (SemVer) | JMP nutzt nur die öffentliche API; Mindestversion = API von 0.6.0 (Minor-Erhöhung des API-Strangs). Die Website-Konstante der API-Version kommt aus dem Doku-Export. |
| Netzwerkprotokoll | durch diese Stränge unverändert; die Protokollnummer von 0.6.0 setzen PW3- und Feature-Stränge. |
| Web-Export | Format 1 → 2 (neuer Aufbau); der Konfigurator liest nur noch Format 2; Daten für 0.5.2 werden nicht mehr gebaut. |
| Website, gespeicherte Sprache | `propwash-lang` mit `en`/`de` gilt weiter; neue Werte `es|fr|pt-br|pl|ru`. |
| Browser | wie bisher (moderne Browser mit ES-Modulen); Jar-Lesen über das vorhandene `inflate.js`, kein neues Fremdpaket. |

---

## 5. UI und Texte

- **Sprachen:** Alle neuen Website-Texte entstehen EN und DE nebeneinander (offiziell), danach per KI in die fünf
  weiteren Sprachen (3.2). Deutsche Texte mit echten Umlauten und ß; `test-docs` und `test-i18n` prüfen das.
- **Propwash-Theme:** dunkle Flächen (`--bg`, `--panel`), Akzent `--accent` (#ffbb00), Überschriften Saira, Zahlen
  und Kürzel `--mono`; neue Bausteine (Banner, Sprachmenü, Info-Symbol der Kennzahlen, Einsteiger-Karten,
  Add-on-Karten, Umrechner-Liste) verwenden nur vorhandene Variablen. Eigene CSS-Dateien je Strang
  (`i18n.css`, `addons.css`, Ergänzungen in `configurator.css`), `style.css` bleibt unberührt.
- **Breiten statt GUI-Skalierung:** Die Website wird bei 360, 768, 1100 (Umbruch der Navigation) und 1280 px geprüft;
  Polnisch und Russisch laufen 20–30 % länger als Englisch, deshalb Navigation, Knöpfe und Kennzahl-Zeilen mit
  Umbruch statt fester Breiten. Das Sprachmenü passt in die vorhandene Kopfzeile, unter 1100 px ins Menü.
- **Barrierefreiheit:** Info-Symbole mit `aria-describedby`, Tooltips auch per Tastatur, Banner als `role="note"`,
  `lang` auf jedem Element mit fremdsprachigem Text (z. B. englische UI-Begriffe in `<span lang="en">`).
- **Im Spiel (JMP):** keine neue GUI. Neue Motoren: Name, Kurzname, Shift-Hilfe (Einsatz, Pro, Contra) in EN/DE wie die
  vorhandenen; Weiterleitungs-Hinweis von `/jmp code` in EN/DE. Die Share-Oberfläche liegt künftig in Propwash
  (PW3-Strang) und folgt dort der GUI-Skalierungs-Prüfung (`ui-scale`).

---

## 6. Tests – vollständige Prüfung

### 6.1 Bestehende Website-Tests (müssen grün bleiben)

`test-nav`, `test-docs`, `test-roadmap`, `test-changelog`, `test-links` (inkl. Sitemap), `test-stamp-assets`,
`test-configurator`, `test-configurator-render`, `test-configurator-pick`.

### 6.2 Neue automatische Website-Tests

| Test | Prüft |
|---|---|
| `tools/i18n/test-i18n.mjs` | je aktiver Sprache 100 % Abdeckung, keine veralteten Einträge; je Eintrag gleiche Tags, Attribute, Links, Zahlen, Einheiten und Code-Spans wie die Quelle; Glossar-Begriffe und Spiel-UI-Begriffe unverändert; Längenverhältnis 0,6–1,8 (sonst Hinweis für den Prüfer); verbliebene englische Sätze (Anteil englischer Wörter) als Fehler; keine EN/DE-Spans in erzeugten Seiten; Banner auf jeder Seite mit gültigen EN/DE-Links; `hreflang` vollständig und gegenseitig; `lang`-Attribute; lokale Sperrliste (dieselbe wie beim öffentlichen Export, um Modell- und Anbieternamen erweitert, liegt nicht im Repository); **Schriftabdeckung**: jedes Zeichen jeder Sprache ist in den geladenen `woff2`-Teilmengen enthalten (liest `cmap` per Node-Brotli); erzeugte Seiten aktuell (`--check`) |
| `tools/test-platforms.mjs` | Download-Knöpfe nur CurseForge/Modrinth und gleich `platforms.json`; kein Link auf `github.com/*/releases`; „GitHub“ nur in `aria-label`/`title` eines Icon-Links; Statuszeile passt zur Version |
| `tools/test-addons.mjs` | siehe 3.6 |
| `test-configurator.mjs` (erweitert) | alle PW3-Vektoren aus dem Export: dekodieren gleich, kodieren **bytegleich**; alle PW1/PW2-Vektoren (Export-Vektoren, `sharecode-vectors.json`, `report-vectors.json`, Vektoren aus JMP 1.1.1) → PW3 → gleicher Build; Umrechnung idempotent; `/convert/` findet Codes in Fließtext; Kennzahl-Hilfe für jede Kennzahl in jeder Sprache; Einsteiger-Presets ohne Warnungen und im Klassenbereich |
| `test-configurator-jar.mjs` (neu) | JMP-1.2.0-Jar: Teile aus dem Jar = `parts.json` des Exports; Beispiel-Add-on-Jar von Propwash; künstliche Jars: Pfad-Ausbruch, Zip-Bombe, übergroßes JSON, HTML in Namen (erscheint als Text), Java-only-Teile (erscheinen als unbekannt) |
| `test-configurator-render.mjs` (erweitert) | XT60: je gültiger Kombination aus Rahmen, Akkuplatz, Akku und Stack genau ein gestecktes Paar, Kabelenden auf den Ankern (Abstand < 0,5 Modell-Pixel), Stecker innerhalb der Rahmenhülle plus Rand |
| `tools/test-layout.mjs` (neu, Headless Chrome) | jede Seite × jede Sprache × 360/768/1280 px: kein waagerechter Überlauf, keine abgeschnittenen Knöpfe, Navigation überlappt nichts; Banner sichtbar; Bildschirmfotos in einen temporären Ordner für die Prüfer |

### 6.3 Just More Parts

- **JUnit** (`tools/fastcheck.sh`): `MotorDataTest` (KV·√R je Stator, neue Fenster-Regel, Massen und Ströme im
  Datenbeleg-Bereich), `MotorBenchTest` (Korridor „Renn-Whoop 1S“, jede Pro/Contra-Aussage mit ≥ 1 % Abstand),
  Preset-Test (Korridor, erlaubte Warnungen), `parts_sync.py --check` (Ids, Namen, Rezepte eindeutig),
  Sprachdateien EN/DE vollständig, Truhen-Beute, neu `share/ShareTableTest` (Tabelle deckt jedes Teil, nur hinten
  erweitert gegen den Schnappschuss, veraltete Ids mit Ersatz, Prüfsumme des Bestands unverändert) und
  `share/LegacyCodesTest` (alle PW1/PW2-Vektoren aus `web-export/sharecode/vectors.json` von 1.1.1 dekodieren über die
  Propwash-0.6.0-API zum selben Build wie damals).
- **Selbsttest** Fabric und NeoForge mit Propwash 0.6.0: Presets fliegen, neue Motoren in Werkbank, Kreativ-Tab und
  Shift-Hilfe; Bildschirmfotos (Fabric) der neuen Motoren und des neuen Presets.
- **XT60-Bildschirmfotos** (Fabric): Nahaufnahme für Akku oben und unten an je einem Rahmen jeder Klasse (Whoop,
  Toothpick, Cinewhoop, 5″ Freestyle, Deadcat, H-Frame, Long Range, X-Class), dazu die eingebauten Propwash-Rahmen
  als Gegenprobe.
- **Mehrspieler auf dediziertem Server** (Beitrittstest aus `docs/testing.md`): (a) Server und Client mit JMP 1.2.0 →
  `joined`, Pilot fliegt eine Drohne mit neuen Motoren, `/propwash code give` mit einem PW3-Code voller JMP-Teile
  gibt die richtigen Items; (b) JMP nur beim Spieler → `session_inactive=justmoreparts`; (c) JMP nur am Server →
  Hinweis mit „Just More Parts 1.2.0“, `!joined`; (d) JMP 1.1.1 mit Propwash 0.6.0 an Server und Client → `joined`
  im Altmodus, Hinweis einmal, PW3-Code einer JMP-Drohne wie mit 1.2.0 (beide Loader). Danach `latest.log` beider
  Seiten auf Fehler.
- **Web-Export-Regression** (3.7) und Rauchtests beider Loader.

### 6.4 Unabhängige Prüfer (vor dem Release, Fix-Runden bis nichts Mittleres offen ist)

| Prüfer | Gegenstand | Grundlage |
|---|---|---|
| P-Web | alle Website-Tests, Linkprüfung, Konfigurator von Hand im Headless-Browser (PW1/PW2-Links aus echten Discord-Posts, Umrechner mit einem ganzen Post, Jar laden) | Testfolge, Beispiel-Codes |
| P-Bild | Bildschirmfotos aller Seiten in allen Sprachen bei 360 und 1280 px (Startseite, Konfigurator, Umrechner, Verzeichnis vollständig; übrige als Stichprobe), Banner, kyrillische Überschriften | `test-layout`-Ausgabe |
| P-Sprache | Rückübersetzung nach Englisch: alle Banner-, FAQ-, Download- und Lizenz-Texte vollständig, sonst 10 % Stichprobe je Sprache und Seite; Bedeutung, Ton, UI-Begriffe | Wörterbücher |
| P-Daten | Datenbeleg und Werte der neuen Motoren, Prüfstand, Preset | Katalog, Datenblätter |
| P-XT60 | Bildschirmfotos im Spiel und Web-Viewer nebeneinander, alle Rahmen und Akkuplätze | Selbsttest, Render-Test |
| P-Kompat | Altwelt mit JMP-1.1.1-Drohnen unter 0.6.0 + JMP 1.2.0, alle Code-Generationen im Spiel und auf der Website, Beitrittsfälle (a)–(d) | 6.3 |

---

## 7. Doku und Website

- **Website:** alles aus Abschnitt 3; `README.md` des Site-Repositorys bekommt Abschnitte „Languages“,
  „Add-on directory“, „Platforms“ und aktualisierte Abschnitte zu Konfigurator, Doku und Changelog (jeder Strang nur
  seinen Abschnitt).
- **Just More Parts:** `CHANGELOG.md` 1.2.0 (Highlights: Renn-Whoop-Motoren, gesteckte Akkus; Neu/Verbessert/Behoben;
  Kompatibilität: braucht Propwash 0.6.0; Propwash 0.6.0 läuft auch noch mit JMP 1.1.x; Share-Codes jetzt in
  Propwash, alte Codes gehen weiter), `README.md` und
  `README.de.md`, `docs/modrinth/`, `docs/RELEASE-NOTES.md`, `docs/ARCHITECTURE.md` (Pakete ohne `share`/`export`),
  `docs/design/parts-catalog.md` (§1.3 Regel, §3 Motoren, Datenbeleg-Klassenwerte).
- **Propwash:** `docs/web-export.md` (Format 2, Befehl) für Add-on-Autoren; der Doku-Abschnitt der Website erklärt den
  Befehl im Add-on-API-Teil („Dein Add-on im Konfigurator“).
- **Discord-Ankündigung** (Entwurf durch den Release-Strang, Post durch lardtom): Umstellung auf PW3, alte Codes gehen
  weiter, Bitte, Codes im Drohnen-Channel über propwashfpv.com/convert/ umzurechnen und den PW3-Code **neben** den alten
  zu setzen (Spieler auf 0.5 lesen nur den alten; `sharecodes-pw3.md` Anhang A).

---

## 8. Risiken

| Risiko | Wirkung | Gegenmaßnahme |
|---|---|---|
| Übersetzungsmenge (~50.000 Wörter × 5) und späte Textänderungen | Release wartet auf Übersetzungen | zwei Durchgänge (Bestand sofort, Delta nach Textstopp); Sprache ohne 100 % fällt aus dem Release, statt halb englisch zu erscheinen |
| Fehlübersetzungen, besonders Lizenz und Kompatibilität | falsche Zusagen | Banner verweist auf EN/DE; vollständige Rückübersetzung dieser Texte; Rechtstexte nicht übersetzt |
| Schriftabdeckung pl/ru, Look der Ersatzschrift | Kästchen oder Stilbruch | Glyphen-Test im CI; Bildschirmfoto-Prüfung der Überschriften |
| Längere Texte brechen Navigation/Kacheln | kaputtes Layout | `test-layout` mit Überlauf-Prüfung in allen Sprachen und Breiten |
| PW3-Spezifikation ändert sich spät | Website kodiert anders als die Mod | Website prüft nur gegen Vektoren aus dem Export der Mod; Release-Build erzeugt die Vektoren neu |
| JS-Teileleser weicht von `PartJson` ab | falsche Werte bei lokal geladenen Jars | Abnahme gegen den offiziellen Export (Feld für Feld) |
| Bösartige Jars | Absturz oder Skript-Einschleusung im Browser | Größen- und Pfadgrenzen, nur `textContent`, keine Speicherung, Tests mit künstlichen Jars |
| JMP entfernt Codec, bevor Propwash ihn hat | Funktionslücke | J1 wird erst nach dem Merge des PW3-Strangs in die Integration gemergt; JMP-Build hängt an Propwash aus `mavenLocal` |
| Motor-Hitze-Änderung verschiebt Prüfstandswerte | falsche Texte und Korridore | Prüfstand nach Merge der Hitze-Änderung neu, Texte ziehen mit |
| XT60-Vertrag des Kern-Strangs anders als angenommen | Nacharbeit an 98 Modellen | Werkzeug-Skript statt Handarbeit; J3 startet erst nach dem Kern-Format |
| Vergessener GitHub-Download (z. B. aus altem Text) | Verstoß gegen die Plattform-Vorgabe | `test-platforms` |
| Seite geht live, bevor CurseForge/Modrinth 0.6.0 haben | falsche Download-Angabe | `status: review` bis zur Freigabe; Live nur auf Go |
| Kurzkennungen werden doppelt oder neu vergeben | kaputte Codes | append-only-Test gegen den letzten Release-Tag |
| Sperrliste: Modell-/Anbieternamen in übersetzten Texten | Spuren, Vorgabe „allgemein KI“ verletzt | `test-i18n` gegen die lokale Sperrliste |

---

## 9. Entscheidungen

| # | Was | Warum | Verworfen |
|---|---|---|---|
| E1 | Weitere Sprachen: es, fr, pt-BR, pl, ru | große Minecraft-Java-Gemeinschaften, Signale im Discord, decken sich mit den Start-Sprachen des Übersetzungsplans | zh-CN/ja jetzt (Schrift, Umbruch, Prüfbarkeit); it/tr/uk/nl (kein Signal); nur ein bis zwei Sprachen (gewünscht sind mehrere) |
| E2 | Erzeugte statische Bäume `/<lang>/`, EN/DE bleiben wie sie sind | SEO, funktioniert ohne JavaScript, Handseiten bleiben pflegbar | weitere Spans in denselben Seiten (Seiten ×3,5, Handpflege unmöglich); Übersetzung zur Laufzeit per JS (Flackern, ohne JS leer, schlecht für Suchmaschinen) |
| E3 | Ein Wörterbuch je Sprache, Schlüssel = englischer Quelltext | lesbar für Prüfer, Änderungen werden automatisch zu Arbeit, gleiches Muster wie `de.json` | Hash-Schlüssel (unlesbar); Crowdin jetzt (bleibt für später geplant) |
| E4 | Übersetzt werden alle Spieler-Seiten; Entwickler-Doku und Rechtstexte nicht | Spieler sind die Zielgruppe; API-Referenz entsteht aus Code, Entwickler lesen Englisch; Rechtstexte nicht per KI | alles übersetzen; nur Startseite (halbe Übersetzung) |
| E5 | UI-Begriffe des Spiels und Teilenamen bleiben englisch (UI-Begriffe mit Übersetzung in Klammern) | die Mod gibt es nur EN/DE; Spieler müssen Knöpfe und Items wiederfinden | alles übersetzen |
| E6 | Banner fest, nicht wegklickbar, unter dem Kopf, Links auf dieselbe Seite in EN/DE | Vorgabe: jede Sprache bekommt ein Banner | wegklickbar; Fußnote |
| E7 | Keine automatische Weiterleitung, nur ein einmaliger Hinweis | Nutzer behalten die offizielle Fassung, Suchmaschinen sehen stabile Seiten | Weiterleitung nach Browsersprache |
| E8 | Sprache nur mit 100 % Abdeckung im Release | keine gemischt englischen Seiten | Fallback auf Englisch |
| E9 | Schriften neu schneiden (Latin Ext-A, Kyrillisch in Mono), Ersatz-Überschriftenschrift nur für `/ru/` | heutige Teilmengen decken pl/ru nicht | Systemschrift für ru; ru weglassen |
| E10 | `platforms.json` als einzige Download-Quelle, GitHub nur Icon, Test erzwingt das | Vorgabe vom 17.10. (korrigiert), keine Rückfälle | GitHub als dritte Plattform (am selben Tag zurückgenommen) |
| E11 | FAQ VR: nicht unterstützt, nicht geplant, kein Hinweis auf ein Community-Add-on | VR hat derzeit keine Relevanz; keine unbestätigten Zusagen | Ankündigung eines Community-Add-ons |
| E12 | FAQ Motion Blur: Verweis auf Iris/Shaderpacks plus Tastenhinweis K | Vorgabe; der K-Konflikt ist die häufige Folgefrage, die Belegung bleibt | eigener Blur-Effekt |
| E13 | Kennzahl-Erklärungen zunächst nur auf der Website, mit Klassenbereich | Werkbank-Hilfe ist nicht verlangt; Sprachdateien der Mod sind ein Konfliktpunkt vieler Stränge | sofort in die Mod-Sprachdateien |
| E14 | Einsteiger-Builds = drei Propwash-Presets ohne Tuning-Änderung | laufen ohne Add-ons, sind geprüft | JMP-Presets; eigene Einsteiger-Rates (ungeprüft) |
| E15 | Website schreibt nur PW3; alte Links werden beim Öffnen auf PW3 umgestellt | Vorgabe „nur noch PW3“ gilt sinngemäß auch hier; geteilte Links werden von selbst neu | zusätzlicher PW2-Export für 0.5.x |
| E16 | Eigene Seite `/convert/` mit Stapelverarbeitung | kurze URL für die Ankündigung, viele Codes in einem Post | nur im Konfigurator |
| E17 | Jar im Browser: nur Daten (JSON, Modelle, Texturen, Sprache, Tabelle), JS-Teileleser gegen Export geprüft | Java-Code lässt sich weder ausführen noch sicher lesen; nichts hochladen | Upload; Java-Analyse |
| E18 | Web-Export als Befehl `/propwash webexport` im Hauptcode, Format 2 je Namensraum | jedes Add-on kann ohne Code exportieren; Codec gehört der Basis | Export bleibt in JMP; Export nur im Propwash-`devtest` (nicht im Release-Jar) |
| E19 | Verzeichnis: `registry.json` append-only; Start nur mit offiziellen Add-ons; Aufnahme nach Prüfung durch lardtom | Codes hängen an Namensräumen und Kennungen; Qualität und Regeln | offene Selbsteintragung |
| E20 | Keine Server-Mod-Seite, keine Server-Doku, kein Verzeichnis- oder FAQ-Eintrag in 0.6.0 | die Server-Mod ist am 17.10. abends zurückgestellt; die Website verspricht nichts, was nicht kommt | Seiten vorab bauen (früherer Entwurf: `/addons/server/`, `/docs/server/`) |
| E21 | Changelog bleibt bei zwei Produkten | die Server-Mod ist zurückgestellt | drittes Produkt „Propwash: Server“ |
| E22 | Roadmap ohne Spoiler; Start-Stand als eigener Branch auf Go | Vorgabe 08.10./17.10.; Push nur auf Go | 0.6.0-Inhalte vollständig vorab |
| E23 | JMP-Version 1.2.0; Propwash 0.6.0 läuft mit JMP 1.1.x im Altmodus statt es abzulehnen | Muster 1.x je Propwash-Minor; ein Update nur von Propwash (Plattform-Prüfzeiten verschieden) darf das Spiel nicht unstartbar machen; der Altmodus zeigt trotzdem nur eine Share-Oberfläche | 2.0.0; `breaks`/`incompatible` für JMP < 1.2.0 |
| E24 | JMP-Tabelle in PW2-Reihenfolge, Neues hinten | Umrechnung trivial nachprüfbar | neue Ordnung |
| E25 | `/jmp code …` als Weiterleitung bis 1.3.0 | Befehlsblöcke und Datenpakete in Karten laufen weiter | sofort streichen |
| E26 | Drei neue Motoren (0702 ≈30000, 0702 ≈33000, 0802 ≈30000) plus ein Renn-Whoop-Preset; endgültige KV aus dem Datenbeleg | deckt 65 und 75 mm ab, Preset macht sie auffindbar | nur ein Motor; mehr als fünf (Katalog-Rauschen); KV ohne Beleg festlegen |
| E27 | Drehzahl-Fenster für 1S-Renn-Whoops bis 125 k/min | reale 30000+-KV-Motoren liegen darüber; ohne Änderung wären sie regelwidrig | Fenster beibehalten (dann keine 30000+ KV möglich) |
| E28 | Datenbeleg: Klassenwerte öffentlich, Quellen mit Herstellern nur intern (vom Export ausgeschlossen) | keine Markennamen nach außen, Beleg trotzdem prüfbar | Quellen öffentlich; kein Beleg |
| E29 | XT60 in JMP per Werkzeug nach dem Kern-Vertrag | 98 Modelle, gleiche Regel wie im Kern und im Web | Handarbeit; Stecker im Modell nur verschieben (passt nicht für alle Rahmen und Akkuplätze) |
| E30 | Vorgaben sinngemäß statt wörtlich, keine Namen aus der Community | `docs/CONVENTIONS.md` und Export-Prüfung | wörtliche Zitate |

---

## 10. Arbeitspakete

Repos: **P** = Propwash, **J** = Just More Parts, **S** = Website. Branches gehen vom aktuellen Integrationsstand ab:
`integration` (P), `integration-1.2.0` ab `v1.1.1` (J), `release/0.6.0` ab Website-`main` `cfe6713` (S); Zweig-Namen nach
Übersicht `README.md` §4.1. Merges laufen unter der gemeinsamen Integrations-Sperre. Ein Strang fasst nur die
genannten Dateien an; `README.md` der Website nur im eigenen Abschnitt.

| Strang | Branch | Repo | Besitzt | Abhängigkeiten | MC-Test (min) | Größe |
|---|---|---|---|---|---|---|
| P1 `webexport` | `feat/0.6.0-webexport` | P | `common/src/main/java/de/lardtom/propwash/webexport/**`, `common/src/test/java/de/lardtom/propwash/webexport/**`, eine Zeile Modul-Anmeldung, Task `runWebExport` in `fabric/build.gradle`, `docs/web-export.md` | Vektoren erst nach PW3-Codec (vorher ohne `sharecode/`) | 15 | M |
| J1 `jmp-pw3-table` | `feat/1.2.0-jmp-pw3-table` | J | Löschung `share/**`, `export/**` (devtest), `command/JmpCommands.java`, neue Tabellen-Ressource, `share/ShareTableTest`, `share/LegacyCodesTest`, Snapshot unter `common/src/test/resources/share/`, Build-Anbindung des Prüfwerkzeugs, Kopfzeilen der alten Share-Dokumente | PW3-Strang in der Propwash-Integration gemergt (+ `publishToMavenLocal`), P1 | 30 | M |
| J2 `jmp-whoop-motors` | `feat/1.2.0-jmp-whoop-motors` | J | neue `propwash_parts/motor/m0702_*`, `m0802_*`, deren Modelle/Texturen, Rezepte, `motor/MotorLoot.java`, Motor-Sprachmodul, `MotorDataTest`/`MotorBenchTest`-Ergänzungen, Preset in `preset/JmpPresets.java`, `docs/design/parts-catalog.md` §1.3/§1.7/§3, `docs/design/data-sources/**`, Ausschluss-Zeile in `tools/public_export.json` | Tabellen-Zeilen erst nach J1 (rebase); Prüfstand erneut nach Motor-Hitze-Strang | 10 | M |
| J3 `jmp-xt60` | `feat/1.2.0-jmp-xt60` | J | `assets/justmoreparts/models/part/battery/**`, `…/stack/**`, `items/drone_render/battery|stack/**`, `tools/connector_models.py`, ggf. Park-Positionen in Rahmen-Render-Definitionen | Kern-XT60-Strang (Format + Renderer) | 20 | M |
| J4 `jmp-release-120` | `release/1.2.0` | J | `gradle.properties`, `CHANGELOG.md`, `README*.md`, `docs/modrinth/**`, `docs/RELEASE-NOTES.md`, `docs/ARCHITECTURE.md`, `NOTICE` | J1–J3, Propwash-0.6.0-Kandidat | 45 | S |
| S1 `site-i18n` | `site/0.6.0-site-i18n` | S | `tools/i18n/**` außer `dict/`, `assets/js/i18n.js`, `assets/css/i18n.css`, `assets/i18n/**` (erzeugt), Sprachteil in `assets/js/main.js`, `tools/site-nav.mjs` außer `LINKS`, `tools/build-nav.mjs` (Kopf-Skript per Marker), `assets/fonts/**`, `test-layout.mjs` | – | 0 | L |
| S2 `site-content` | `site/0.6.0-site-content` | S | Inhalt von `index.html` (FAQ, Download, Lizenz, Was ist neu), `impressum/**`, `datenschutz/**`, `assets/data/platforms.json`, `tools/build-platforms.mjs`, `tools/test-platforms.mjs`, `assets/data/roadmap.json`, `roadmap/**`, `assets/js/roadmap/**`, `assets/img/*` (ohne `docs/`), `IMAGES` in `tools/build-landing-images.mjs`; zusätzlich früh `site/0.6.0-roadmap-start` | S1 (`root()`, Lookup) | 0 | M |
| S3 `site-configurator` | `site/0.6.0-site-configurator` | S | `configurator/**`, `convert/**`, `assets/js/configurator/**`, `assets/css/configurator.css`, `assets/data/configurator/**`, `tools/build-configurator-*.mjs`, `tools/test-configurator*.mjs`, `tools/fixtures/**` | P1-Export, PW3-Spezifikation und Vektoren, J1 (Tabelle), Kern-XT60-Format, S1 | 0 | L |
| S4 `site-addons` | `site/0.6.0-site-addons` | S | `addons/**`, `assets/data/addons/**`, `assets/js/addons/**`, `assets/css/addons.css`, `tools/build-addons.mjs`, `tools/test-addons.mjs`, `LINKS` in `tools/site-nav.mjs` | P1 (Format), PW3 (Kurzkennungen), S3 (`jar.js`), S1 | 0 | M |
| S5 `site-docs` | `site/0.6.0-site-docs` | S | `tools/docs/**`, `docs/**` (erzeugt), `tools/build-docs.mjs`, `tools/test-docs.mjs`, `assets/img/docs/**`, `assets/css/docs.css`, `assets/js/docs.js`, `changelog/**`, `assets/js/changelog/**`, `assets/data/changelog.json`, `tools/build-changelog.mjs`, `tools/test-changelog.mjs`, `tools/changelog/de.json` | Doku-Bausteine aller Feature-Stränge, API-Strang, `ADDONS.md` | 0 (Bilder aus dem Release-Selbsttest) | L |
| S6–S10 `site-i18n-es`, `-fr`, `-pt-br`, `-pl`, `-ru` | `site/0.6.0-site-i18n-<lang>` | S | nur `tools/i18n/dict/<lang>.json` | S1 (Extraktion); **ein** Durchgang nach dem Textstopp von S2–S5 | 0 | je M |
| S11 `site-release-060` | `release/0.6.0` | S | Integration, erzeugte `/<lang>/**`, `sitemap.xml`, Stempel, Konfigurator-Bild, Entwurf der Discord-Ankündigung | alle S-Stränge, J4, Propwash 0.6.0 | 0 (Online-Linkprüfung nach Upload) | M |

**Reihenfolge (aus den Abhängigkeiten):**

1. **Ohne Vorbedingung:** S1, S2 (inkl. Roadmap-Start-Branch), J2 (Datenbeleg, Werte, Prüfstand), P1 (ohne Vektoren).
2. **Nach dem PW3- bzw. XT60-Kern-Merge:** J1, J3, S3, danach S4.
3. **Nach dem Funktionsstopp 0.6.0:** S5, dann S6–S10 in einem Durchgang nach dem Textstopp, Prüfstand-Neumessung J2.
4. **Release:** J4, S11, Prüfer aus 6.4, Fix-Runden; Veröffentlichung nur nach Freigabe (Website-Push erst, wenn
   CurseForge und Modrinth die Dateien haben oder der Status ehrlich „in Prüfung“ zeigt). JMP 1.2.0 wird erst
   hochgeladen, wenn Propwash 0.6.0 auf der jeweiligen Plattform freigegeben ist.

MC-Bedarf gesamt etwa 2 h, verteilt auf P1, J1–J4; die Website braucht keine Minecraft-Instanz (Bilder kommen aus
den Selbsttest-Läufen der Feature- und Release-Stränge).

---

## Anhang A – Schnittstellen-Annahmen an andere Stränge

Was hier steht, sind Annahmen dieses Designs. Maßgeblich ist das jeweilige Nachbar-Design; weicht es ab, passen die
Stränge dieses Dokuments sich an, ohne ihren Besitz zu ändern.

**A.1 PW3-Strang**

- liefert die PW3-Spezifikation (Textform, Bitstrom, Prüfsumme, Kurzkennungen, Fehlende-Add-on-Angaben) und
  Testvektoren für den Web-Export (`base/sharecode/vectors.json`), dazu PW1/PW2-Altvektoren;
- legt Pfad und Schema der Teile-Tabelle eines Add-ons fest (Vorschlag 3.12.1) und liefert das Prüfwerkzeug, das
  Add-ons im Build nutzen;
- legt fest, wie die Mod Kurzkennungen kennt (Kopie der Vergabeliste im Jar und/oder Eintrag in der Add-on-Tabelle);
- übernimmt Share-Oberfläche, Einkaufsliste, Kreativ-Ersatz, Befehle (Ziel der `/jmp code`-Weiterleitung) und die
  eingefrorenen PW1/PW2-Tabellen samt JMP-Aliasen (Datenlieferung aus J1);
- baut den Altmodus für Just More Parts < 1.2.0 (2.3, Übersicht `README.md` §3.5); keine Einträge `breaks`/`incompatible`.

**A.2 XT60-Kern-Strang**

- **Variante 2 gilt** (`fixes-polish.md` §3.B, Befund PLUG-1): Kern und Web zeichnen kein gestecktes Paar und kein
  Kabel, sondern blenden mit Akku Stecker und Kabel von Akku und Stack aus; die Punkte darunter sind überholt.
- entfernt die Stecker aus den Propwash-Modellen und zeichnet das gesteckte Paar plus Kabel im Drohnen-Renderer;
- definiert die Anker (Kabelende Akku je Akkuplatz, Pigtail-Ende Stack), optionale Park-Position je Rahmen und
  Akkuplatz, Größe und Farbe je Steckerart;
- exportiert diese Angaben über den Web-Export, damit `assembly.js` dasselbe zeichnet.

**A.3 Server-Mod-Strang**

- entfällt für 0.6.0 (Server-Mod zurückgestellt, E20).

**A.4 Feature-Stränge 0.6.0**

- je Funktion ein Doku-Baustein `docs/<thema>.md` (EN-Kernaussagen, Bedienung, Grenzen) und Bildschirmfoto-Schritte im
  Selbsttest (Fabric, `-PpropwashScreenshots=true`);
- der API-Strang hält die öffentlichen Typen so, dass `tools/docs/java-api.mjs` sie liest, und erhöht die
  API-Version nach SemVer.
