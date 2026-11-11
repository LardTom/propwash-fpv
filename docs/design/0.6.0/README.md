# 0.6.0 – Übersicht: Umfang, Abhängigkeiten, Versionen, Prüfung und Release

| | |
|---|---|
| Stand | 17.10.2026, Übersichtsdokument zum Design von 0.6.0 |
| Gilt für | Propwash FPV 0.6.0 (API 1.7.0, Netzprotokoll 20), Propwash: Just More Parts 1.2.0, Website-Release 0.6.0 |
| Basis | Integrationszweig `integration` ab `53ce0049` (Version `0.6.0-dev.1`, mit paralleler Test-Infrastruktur), darunter Tag `v0.5.2` (API 1.6.0, Protokoll 16); JMP `v1.1.1`; Website `main` = Live-Stand 0.5.2 |
| Dokumente dieses Ordners | [`sharecodes-pw3.md`](sharecodes-pw3.md), [`addon-api-namespace.md`](addon-api-namespace.md), [`gameplay.md`](gameplay.md), [`fixes-polish.md`](fixes-polish.md), [`website-jmp.md`](website-jmp.md) |
| Rang | Bei Widerspruch zwischen den Fach-Dokumenten gilt dieses Dokument (Abschnitt 2.2). Für alles, was hier nicht steht, gilt das Fach-Dokument. |

Dieses Dokument fasst die Fach-Dokumente zusammen: Umfang, Abhängigkeiten zwischen den Themen, aufgelöste Widersprüche,
Versionen und Kompatibilität, Regeln für Stränge und Integration, den Prüfplan und die Release-Schritte. Vorgaben stehen
sinngemäß mit Datum und Quelle, nicht als wörtliches Zitat (E1).

---

## 0. Kurzfassung

- **Umfang:** PW3-Share-Codes für alle Add-ons, Add-on-API 1.7 mit reserviertem Namensraum `propwash`, sieben
  Spiel-Features (Flugbahn-Marker, Freecam-Drohne, RX-Block, Kamerawinkel im K-Menü, Gate-Hinweis, Slime und Wolle,
  `/propwash clear`), die Prüfung der Motor-Hitze, sechs Fixes (gestapelte Gates, gestecktes XT60, Druckplatten und
  Stolperdraht, Signal-Gedächtnis, Zonenzeile, Bando-Lage), Just More Parts 1.2.0, Website mit Umrechner,
  Add-on-Verzeichnis, FAQ und fünf weiteren Sprachen.
- **Nicht drin:** die Server-Mod „Propwash: Server“ samt ihrer Basis-Haken (zurückgestellt am 17.10. abends), auch nicht
  auf der Website und nicht in `ADDONS.md`.
- **Versionen:** API **1.7.0** (eine Nebenversion für alles), Netzprotokoll **20** (17 im Basis-Commit, 18 ab
  `0.6.0-rc.3` für persönliche Regeln und die Zeilen der Bestenliste mit Serverregeln, 19 ab `0.6.0-rc.4` für
  Rundenurteil, Ideallinie je Kurs und Fortschritte, 20 nach rc.4 für Replays aus der Spur des Piloten, 3.3).
  Abhängige Mods nennen Propwash mit Bereichen, die Vorabstände einschließen: Fabric `>=0.6.0-`, NeoForge
  `[0.6.0-alpha,)` (3.2).
- **Just More Parts:** Propwash 0.6.0 startet auch mit JMP 1.1.x (Altmodus mit Hinweis, Share-Codes über die Basis);
  JMP 1.2.0 ist empfohlen und verlangt Propwash 0.6.0 (3.5).
- **Bau:** Stränge in getrennten Worktrees, rollende Integration unter **einer** Sperre für alle Repos und jede
  Veröffentlichung nach `mavenLocal`; jede Veröffentlichung bekommt eine neue Nummer `0.6.0-dev.N` (4.3).
- **Prüfung:** zwölf Prüfbereiche mit unabhängigen Prüfern, Mehrspieler-Matrix auf dediziertem Server mit zwei echten
  Clients, Testwelten aus 0.5.2, 0.5.0 und 0.4.0, alle Share-Code-Generationen samt echter Codes aus Discord,
  Bildschirmfoto-Abgleich, Klon-Probe des öffentlichen Exports; Fix-Runden, bis nichts Mittleres offen ist (6).
- **Release:** Release-Commit, Tags und Jars entstehen aus dem Integrationszweig; `main` bewegt die Integration nicht.
  Propwash 0.6.0 wird zuerst veröffentlicht, JMP 1.2.0 danach (7).

---

## 1. Umfang und Vorgaben

### 1.1 Was 0.6.0 enthält

| Thema | Dokument | Stränge | Für Spieler sichtbar |
|---|---|---|---|
| Share-Codes PW3 für alle Add-ons, Umrechnung PW1/PW2, fehlende Add-ons | `sharecodes-pw3.md` | `pw3-codec`, `pw3-ui`, `jmp-pw3-table`, `site-configurator`, `site-addons` | ja |
| Add-on-API 1.7: Schalter und Achsen, OSD-Elemente, Kollision, Werfen, Anzeige, Stoß-Ereignis, Welt-Regeln, Drohnen-Daten, autonomer Flug, Operator, Gimbal, Pilotenwechsel | `addon-api-namespace.md` | `api17-contract`, `addon-switches`, `addon-osd`, `addon-collision`, `addon-autonomy`, `operator-gimbal`, `drone-data-service`, `accessory-gaps`, `addon-world-rules`, `api17-docs` | nur über Add-ons (Explosions- und Windstöße ohne Kernverhalten, E27) |
| Namensraum `propwash` reserviert, Prüfung beim Start, `ADDONS.md`, NOTICE, README, Issue-Vorlage | `addon-api-namespace.md` | `ns-guard`, `addon-rules-docs` | Hinweis nur bei Funden |
| Drei Tests, die nach einem Klon des öffentlichen Repos scheitern | `addon-api-namespace.md` | `test-portability` | nein |
| Flugbahn-Marker, Freecam-Drohne, RX-Block, Kamerawinkel im K-Menü, Gate-Hinweis und Kapitel „Gate-Menü“, Slime und Wolle (Honig unverändert), `/propwash clear`, Prüfung der Motor-Hitze | `gameplay.md` | `fpm`, `freecam`, `rx`, `camangle`, `gatehint`, `surface`, `clear`, `heat` (dazu `pilot-prep`) | ja |
| Gestapelte Gates, gestecktes XT60, Druckplatten und Stolperdraht, Signal-Gedächtnis, Zonenzeile und Doku, Bando-Lage | `fixes-polish.md` | `gate-legs`, `battery-plug`, `block-triggers`, `signal-memory`, `ui-doc-polish`, `jmp-xt60`, `bando-placement` | ja (Signal-Gedächtnis nur indirekt) |
| Web-Export in der Basis (`/propwash webexport`, Format 2) | `website-jmp.md` | `webexport` | nein |
| Just More Parts 1.2.0: Teile-Tabelle statt Codec, Whoop-Motoren über 30000 KV, gesteckte Stecker | `website-jmp.md`, `sharecodes-pw3.md` | `jmp-pw3-table`, `jmp-whoop-motors`, `jmp-xt60`, `jmp-release-120` | ja |
| Website: FAQ, Kennzahl-Hilfen, Einsteiger-Builds, `/convert/`, `/addons/`, Sprachen es/fr/pt-BR/pl/ru mit KI-Banner, Doku, Changelog, Roadmap | `website-jmp.md` | `site-i18n`, `site-content`, `site-configurator`, `site-addons`, `site-docs`, `site-i18n-<lang>`, `site-release-060` | ja |
| Prüf-Infrastruktur: Testwelten alter Versionen, dedizierter Server mit zwei Clients, Migrations-Selbsttest, stabiler Landungs-Selbsttest | dieses Dokument | `verify-infra`, `world-fixtures`, `landing-stability` | nein |

### 1.2 Was nicht drin ist

- **Server-Mod „Propwash: Server“** (Kern, Spielmodi, HTTP-API) und **alle Basis-Teile dafür** (`core-server-hooks`,
  `api.race`, `api.game`, Spiel-HUD, Zuschauer-Pass). Die Server-Mod wird gesondert besprochen und ist nicht
  Teil dieses Ordners. Auf der Website gibt es dafür in 0.6.0 weder `/addons/server/` noch
  `/docs/server/` noch einen Eintrag im Verzeichnis oder in der FAQ (E7); `ADDONS.md` nennt sie nicht (K29).
- **Aus der Discord-Auswahl abgelehnt:** Community-Server-Themen und Discord-Pflege, Kollision an Zäunen und Schnee
  (gewollt), Tastenkonflikt mit Iris (Belegung bleibt), Drohne steckt fest (Bedienung), Distant Horizons, Erkennung von
  Einrichtungsfehlern im Assistenten, VR, Community-Maps-Seite, Abgleich der Teile mit Echtwerten, Nachtsicht, Gates
  anderer Kurse ausblenden, Verzauberungen, Inhalte aus einem privaten Fork.
- **Später oder als eigenes Add-on:** Portale und Dimensionswechsel (nach 0.6.0), Bullet-Drohnen, Fixed Wings.
- **Nicht beauftragt, deshalb nicht drin:** ein Kernverhalten für Explosions- und Windstöße an geflogenen Drohnen (E27),
  eine neue Wirkung von Honig (E29), eine PW2-Ausgabe auf der Website (E33).
- **Getrennte Vorhaben:** das Kamera-Drohnen-Add-on, Mob Defense und die Backport-Vorarbeit für 1.21.1. Dieses Dokument
  legt nur ihre Übergabepunkte fest (Abschnitt 5).

### 1.3 Vorgaben (sinngemäß, mit Datum und Quelle)

| Datum | Vorgabe | Quelle | Umsetzung |
|---|---|---|---|
| 02.10. | Changelog für Spieler: 2–3 Highlights, dann Neu, Verbessert, Behoben, am Ende Kompatibilität mit Welten, Servern und Add-ons | Roadmap-Notiz | Integration, Release-Schritt 2 |
| 14.10. | Share-Codes für alle Add-ons: Codec in der Basis, Nummern je Add-on aus einer Tabelle im Jar, nur hinten erweitert, Prüfwerkzeug beim Bauen, gestrichene Teile bleiben als veraltet mit Ersatz | Roadmap-Notiz | `pw3-codec`, `jmp-pw3-table` |
| 14.10. | Kurzkennungen über das offizielle Verzeichnis, Prüfsumme je Add-on, fehlende Add-ons mit Name, Version und Link, Einkaufsliste im Überleben, Ersatz im Kreativmodus, Jar lokal im Browser | Roadmap-Notiz | `pw3-codec`, `pw3-ui`, `site-configurator`, `site-addons` |
| 14.10. | Präfix `PW3-`; Website und Mod lesen PW1/PW2 weiter, die Mod schreibt nur noch PW3; danach JMP-Release ohne Codec und ein Discord-Beitrag mit der Bitte, Codes umzurechnen | Roadmap-Notiz | ganze PW3-Kette, `sharecodes-pw3.md` Anhang A |
| 14.10. | Flugbahn-Marker als OSD-Element | Roadmap-Notiz (aus Discord) | `fpm` |
| 15.10. | Freecam-Drohne rein clientseitig, ohne Kollision, Modell, Akku und Crash-Schaden, mit eigener Welt-Regel und reduziertem K-Menü (Whoop, 3,5″, 5″, 7″) | Roadmap-Notiz | `freecam`, `pilot-prep` |
| 15.10. | Die drei Tests, die nach dem Klon scheitern, reparieren; Bando-Lage auf 2 von 8 Seeds und Hover im Lack-Modus für die nächste Version | Roadmap-Notiz | `test-portability`, `bando-placement`, `site-configurator` |
| 17.10. | Den Druckplatten-PR per Portierung mit Nennung des Autors übernehmen | Plan nach 0.5.2 | `block-triggers` |
| 17.10. | Namensraum `propwash` generell reserviert (NOTICE, `ADDONS.md`), Namensschema für Community-Add-ons, API lehnt fremde `propwash:`-Anmeldungen hart ab, Prüfung beim Start und Neuladen mit Log-Fehler und Hinweis ohne Absturz, Überschreiben per Paket bleibt erlaubt; alles in 0.6.0 | Roadmap-Notiz, Add-on-Richtlinien-Entwurf | `api17-contract`, `ns-guard`, `addon-rules-docs` |
| 17.10. | RX-Block: an einen Spieler gebunden, genau ein Kanal, gleichzeitig mit der Drohne steuerbar, Ausgabe 0–15 mit angelernten Endpunkten, Reichweite höchstens 128, außer Reichweite bleibt der letzte Zustand | Roadmap-Notiz | `rx` |
| 17.10. | Gestapelte Gates erst in 0.6.0 reparieren; Akku gesteckt darstellen, Kabel geführt, alle Akkuplätze und Rahmen, auch im Web | Roadmap-Notiz | `gate-legs`, `battery-plug`, `jmp-xt60`, `site-configurator` |
| 17.10. | Discord-Auswahl: `/propwash clear`; Kamerawinkel zusätzlich im K-Menü (Schleichen + Rechtsklick bleibt); Gate-Hinweis beim ersten Setzen bzw. Flugschul-Kapitel; Slime federt, Wolle (und Heuballen, wie vorgeschlagen) fängt ab, Start von Wolle; Motor-Hitze genau prüfen (größere Kopter kämpfen mit Hitze); mehr Whoop-Motoren mit 30000+ KV in JMP | Discord-Auswahl 17.10. | `clear`, `camangle`, `gatehint`, `surface`, `heat`, `jmp-whoop-motors` |
| 17.10. | Website: FAQ (Lizenz, echte Brille, VR; Motion Blur nur Verweis auf Iris/Shaderpacks), Kennzahlen erklären, Einsteiger-Builds, weitere Sprachen mit Banner „mit KI übersetzt, offiziell nur Englisch und Deutsch“, nie ein Modell- oder Firmenname | Discord-Auswahl 17.10. | `site-content`, `site-i18n`, `site-configurator` |
| 17.10. (korrigiert) | Downloads nur über Modrinth und CurseForge; GitHub wird nie als Plattform oder Download genannt, höchstens ein kleines Symbol zum Repository | Roadmap-Notiz (Korrektur der Discord-Antwort vom selben Tag) | `site-content`, `website-jmp.md` E10 |
| 17.10. | 0.6.0 gründlich vor dem Release prüfen: viele unabhängige Prüfer, dedizierter Server mit Client, Bildschirmfotos, Altwelt, alle Share-Code-Generationen, Fix-Runden bis nichts Mittleres offen; Website mitziehen | Plan nach 0.5.2 | Abschnitt 6 |
| 17.10. | Integration nur mit Sperre, nie zwei Integrationen oder Release-Schritte gleichzeitig; Push nur nach Freigabe | Plan nach 0.5.2 | 4.3, 7 |
| 17.10. abends | Server-Add-on zurückgestellt, nicht in 0.6.0 | Roadmap-Notiz | 1.2, E2 |
| 17.10. abends | Add-ons starten, sobald die API von 0.6.0 steht, Schlussprüfung gegen das fertige 0.6.0; Backport-Vorarbeit parallel | Roadmap-Notiz | Abschnitt 5 |

---

## 2. Querschnitt: Abhängigkeiten und Konflikte

### 2.1 Abhängigkeiten zwischen den Themen

```
Basis-Commit (Protokoll 17, Doku-Korrekturen)
 ├─ api17-contract ──┬─ ns-guard
 │                   ├─ addon-switches ◄── pilot-prep
 │                   ├─ addon-osd ◄── fpm
 │                   ├─ addon-collision ◄── surface, heat ──► addon-autonomy ──► (Test a) operator-gimbal
 │                   │        └─ CameraClip ──► freecam
 │                   ├─ drone-data-service ◄── block-triggers ──► accessory-gaps
 │                   ├─ addon-world-rules
 │                   ├─ pw3-ui ◄── pw3-codec
 │                   ├─ api17-docs (nach allen API-Strängen)
 │                   └─ API-Stand A ──► Kamera-Drohnen-Add-on, Mob Defense (eigene Pläne)
 ├─ pilot-prep ──► surface ──► heat Phase A ──(nur bei Befund)──► heat Phase B ──► jmp-whoop-motors (Prüfstand neu)
 ├─ fpm, camangle, pilot-prep ──► freecam
 ├─ pw3-codec ──► webexport (Vektoren) ──► jmp-pw3-table ──► site-configurator ──► site-addons
 ├─ battery-plug ──► jmp-xt60 ──► site-configurator (Viewer)
 ├─ site-i18n ──► site-content, site-configurator, site-addons
 ├─ landing-stability ──► alle vollen Läufe mit „genau PASS“
 └─ verify-infra, world-fixtures ──► Prüfbereiche V6, V7
Funktionsstopp ──► site-docs, site-i18n-<lang>, api17-docs ──► RC ──► jmp-release-120, site-release-060
```

### 2.2 Widersprüche zwischen den Dokumenten und ihre Auflösung

| Nr. | Widerspruch | Dokumente | Auflösung |
|---|---|---|---|
| K1 | Basis der Stränge `v0.5.2` | alle | Basis ist `integration` ab `53ce0049` (0.6.0-dev.1 mit paralleler Test-Infrastruktur). Ohne sie überschreibt ein Server-Rauchtest Port und Welt eines parallelen Laufs (E3). |
| K2 | Wer setzt Protokoll 17: „erster Strang mit Payload“ bzw. „Integration am Ende“ | `gameplay.md` E34, `addon-api-namespace.md` §4 | Der Basis-Commit setzt 17, bevor ein Strang abzweigt; kein Strang ändert `Constants.PROTOCOL_VERSION` (E4). |
| K3 | `core-server-hooks` ändert dieselben Fassaden wie `api17-contract` | Server-Mod-Entwurf, `addon-api-namespace.md` | entfällt mit der Zurückstellung (E2). Die Liste der für die Prüfung erlaubten `/propwash`-Unterbefehle offizieller Add-ons (`addon-api-namespace.md` E33) bleibt in 0.6.0 leer. |
| K4 | Web-Export: Format 1 im JMP-Devtest als Orakel gegen Format 2 als Befehl `/propwash webexport` in der Basis | `sharecodes-pw3.md` §3.15/§4, `website-jmp.md` E18 | Format 2 in der Basis (`webexport`). Der JMP-Export wird in `jmp-pw3-table` entfernt; Orakel ist der Propwash-Export mit geladenem JMP (E8). |
| K5 | Umrechner-Seite `sharecode/` gegen `/convert/` | `sharecodes-pw3.md` §3.16 und Anhang A, `website-jmp.md` E16 | `/convert/`. Anhang A von `sharecodes-pw3.md` ist darauf umgestellt (E9). |
| K6 | Website-Stränge D1/D2 gegen S3/S4 mit überlappenden Dateien | `sharecodes-pw3.md` §10, `website-jmp.md` §10 | D1 geht in `site-configurator` auf, D2 in `site-addons`; der Jar-Leser (`jar.js`, `partjson.js`) gehört zum Konfigurator, das Verzeichnis nutzt ihn (E9). |
| K7 | JMP-Strang C („alles in JMP“) gegen J1–J4 | `sharecodes-pw3.md` §10, `website-jmp.md` §10 | Aufteilung J1–J4; der Inhalt von C steckt in J1 (Codec raus, Tabelle, Hinweis bei `/jmp code`, Baseline), J2 (Motoren) und J4 (Version, Changelog) (E10). |
| K8 | Server-Mod-Seite, Server-Doku, FAQ-Zusatz und Produkt „Propwash: Server“ im Changelog und Verzeichnis | `website-jmp.md` §2.1, §3.3, §3.4, §3.7–3.10, E20, E21, A.3 | entfallen für 0.6.0; `website-jmp.md` ist nachgezogen. Die Roadmap führt die Server-Mod ohne Datum in einer späteren Gruppe (E7). |
| K9 | Sprachdateien: „alle schreiben in `assets/propwash/lang/*.json`“ gegen „nur Modul-Quellen“ | `gameplay.md` §10.2, `addon-api-namespace.md` §10 | Neue Schlüssel nur in einem eigenen Ordner `common/src/lang/<strang>/`; die erzeugten Dateien schreibt nur die Integration mit `mergeLang` (4.5, E13). |
| K10 | `PilotSession` wird von `addon-switches`, `addon-collision` und `freecam` geändert | `addon-api-namespace.md`, `gameplay.md` | Vorab-Strang `pilot-prep` übernimmt die `long`-Schaltermaske und den Umzug der Zonen-Auflösung; danach besitzt nur `addon-collision` die Datei (E11). |
| K11 | `DronePiloting` bei `addon-collision` (`onState`) und `drone-data-service` (`onPilotChanged`) | `addon-api-namespace.md` | Das Auslösen von `PILOT_CHANGED` wandert in `api17-contract` (E12). |
| K12 | `QuadcopterEntity` bei `block-triggers`, `drone-data-service` (NBT, Abgleich) und `accessory-gaps` (Stöße) | `fixes-polish.md`, `addon-api-namespace.md` | `block-triggers` zuerst; danach `drone-data-service` → `accessory-gaps` nacheinander (E11). |
| K13 | `ServerReturnFlight`/`VoxelFlight` bei `surface`, `addon-collision` und `addon-autonomy` | `gameplay.md`, `addon-api-namespace.md` | `surface` zuerst, dann `addon-collision` → `addon-autonomy`. `operator-gimbal` wartet nur mit Test (a) auf `addon-autonomy`, nicht mit dem Bau. |
| K14 | Schnittstellen `api/share/**` gehören sowohl `api17-contract` als auch `pw3-codec` | `addon-api-namespace.md` §10, `sharecodes-pw3.md` §10 | `api17-contract` legt `ShareCodes`, `ShareResult`, `ShareOptions` mit Platzhalter-Implementierung an (Signaturen aus `sharecodes-pw3.md` §3.12); `pw3-codec` implementiert sie und besitzt `ShareTableCheck`. |
| K15 | Warten auf andere Arbeit an `AccessoryDefinition` und `DroneRenderState`, bevor `battery-plug`, `accessory-gaps` und `operator-gimbal` dort ändern | `addon-api-namespace.md` §10 (frühere Fassung) | Nicht warten; die drei Stränge ändern dort nur je eine kleine, abgegrenzte Stelle, damit sich andere Änderungen sauber zusammenführen lassen. |
| K16 | Branch-Namen in drei Schreibweisen (`feat/060-…`, `feat/0.6.0-…`, `feat/web-export`) | alle | Einheitlich nach 4.1 (`feat/0.6.0-<strang>` usw.) (E6). |
| K17 | Discord-Auswahl Punkt 1 nennt GitHub als dritte Plattform | Discord-Auswahl, Roadmap-Notiz | Es gilt die Korrektur vom 17.10.: Downloads nur Modrinth und CurseForge, GitHub nie als Download, höchstens ein kleines Symbol zum Repository (`website-jmp.md` E10). |
| K18 | Rangfolge bei der Server-Mod: der Spielmodi-Plan sieht sie für 0.6.0 vor | Spielmodi-Plan 14.10., Roadmap-Notiz 17.10. abends | Die spätere Vorgabe gilt: zurückgestellt. |
| K19 | JMP 1.1.1: „0 Funde, Selbsttest mit diesem Jar“ gegen „Propwash 0.6.0 erklärt JMP < 1.2.0 für unverträglich“ | `addon-api-namespace.md` §4, §6.2; `sharecodes-pw3.md` §4, §6.4; `website-jmp.md` §2.3, §4 | Keine Unverträglichkeit in den Metadaten. Propwash 0.6.0 startet mit JMP 1.1.x im **Altmodus** (3.5); der `guard:`-Lauf mit JMP 1.1.1 bleibt gültig. Sonst startet nach einem Update nur von Propwash (etwa in der CurseForge-App) das Spiel nicht mehr (E28). |
| K20 | Freecam fliegt durch Wände und braucht `FpvEyeGuard` und den Kamera-Mixin, die `addon-collision` besitzt | `gameplay.md` §3.B, `addon-api-namespace.md` §2.3.1, §10 | `addon-collision` besitzt beide Dateien und legt die interne Abfrage `drone/client/CameraClip` an (Kamera darf in Blöcke, mit Anmeldung weiterer Quellen). `freecam` meldet sich dort an, ändert die beiden Dateien nicht und mergt nach `addon-collision` (E30). |
| K21 | Abhängigkeit „`propwash >= 0.6.0`“ und `breaks "<1.2.0"`/`[,1.2.0)` lehnen Vorabstände ab | `addon-api-namespace.md` §4, `sharecodes-pw3.md` §4, `docs/RELEASING.md` §1.1 | Bereiche mit Vorabstand-Schreibweise (3.2), gesetzt beim Start des Abhängigen und im Release unverändert; die Unverträglichkeits-Einträge entfallen (K19) (E31). |
| K22 | Explosionen und Windkugeln stoßen geflogene Drohnen als neues Kernverhalten ohne Welt-Regel | `addon-api-namespace.md` §3.6, E16 | Kein Kernverhalten in 0.6.0. Das Ereignis `DRONE_KNOCKBACK` kommt, der Stoß ins Flugmodell geschieht nur, wenn ein Add-on ihn anfordert (E27). |
| K23 | `allow_freecam` Standard an, begründet mit der Server-Mod | `gameplay.md` E7 | Die Server-Mod ist zurückgestellt; Standard **aus** (E26). |
| K24 | `docs/RELEASING.md` §4 Schritt 7 spult `main` im Haupt-Arbeitsbaum vor | `docs/RELEASING.md` | Release-Commit, Tags und Jars entstehen aus dem Integrations-Worktree; `main` spult die Integration nicht vor. Der Basis-Commit passt `RELEASING.md` an (E32). |
| K25 | Gate-Hinweis „ohne Haken noch höchstens zweimal“ | `gameplay.md` §3.E | Einmal je Welt und Spieler beim ersten Setzen; „Nicht wieder anzeigen“ beendet ihn für alle Welten (E34). |
| K26 | `ui-doc-polish` ändert `docs/RELEASE-NOTES.md`, die Integration allein darf das | `fixes-polish.md` §3.F, 4.4 | Der Strang liefert den korrigierten Satz in seinem Bericht, die Integration trägt ihn ein. |
| K27 | Discord-Text bittet, Beiträge mit dem PW3-Code zu ersetzen; 0.5.x liest PW3 nicht | `sharecodes-pw3.md` Anhang A | Der alte Code bleibt im Beitrag stehen, der PW3-Code kommt darunter (E33). |
| K28 | `ADDONS.md` übernimmt den Entwurf wörtlich und nennt „Propwash: Server“ als Beispiel | `addon-api-namespace.md` §3.17 | Wörtlich bis auf das Beispiel: die Liste der offiziellen Add-ons nennt nur *Propwash: Just More Parts*. |
| K29 | „0.6.0 nur mit genau `PASS`“ gegen den offenen Ausreißer im Landungs-Selbsttest (1 von 9 Läufen) | dieses Dokument, Bekannte Kleinigkeiten 0.5.2 | Eigener Strang `landing-stability` vor dem ersten vollen Lauf (6.1, E35). |
| K30 | Motor-Hitze Phase B unabhängig vom Ergebnis der Prüfung | `gameplay.md` §3.H | Phase B nur bei einem Befund nach der Regel in `gameplay.md` §3.H (E36). |

---

## 3. Versionen, API, Protokoll und Kompatibilität

### 3.1 Stände

| Stand | Wann | Inhalt | Wofür |
|---|---|---|---|
| `0.6.0-dev.1` | vorhanden (`53ce0049`) | 0.5.2 + parallele Test-Infrastruktur | Ausgang |
| Basis-Commit (bleibt `dev.1`) | zuerst | Protokoll 17, Hinweis in `docs/api/changes.md`, Korrekturen in `docs/testing.md` und `docs/RELEASING.md` | Abzweigpunkt aller Stränge |
| `0.6.0-dev.N` | **jede** Veröffentlichung nach `mavenLocal` | N = letzte Nummer + 1; eine Nummer wird nie zweimal veröffentlicht | reproduzierbare Builds der Abhängigen |
| **API-Stand A** | erste `dev.N` nach dem Merge von `api17-contract` | alle API-1.7-Typen, Ablage ohne Verhalten | Start der Add-ons |
| **API-Stand B** | `dev.N` am Funktionsstopp | alle Funktionen, API mit Verhalten | Add-ons testen echtes Verhalten |
| `0.6.0-rc.N` | Release-Phase | gebaut, vollständig geprüft | Prüfer, Fix-Runden |
| `0.6.0` | nach Freigabe | gleicher Stand wie der letzte RC, nur die Nummer | Release |

JMP folgt mit `1.2.0-dev.N`/`1.2.0-rc.N` nach derselben Regel, die Website mit dem Zweig `release/0.6.0`. Interne Jars
liegen unter `release/internal/<version>/` im Integrations-Worktree und werden nie hochgeladen (`docs/RELEASING.md`
§1.1). Abhängige tragen die **genaue** `dev.N` in ihre `gradle.properties` und wechseln nur, wenn eine neue Nummer
gemeldet ist.

### 3.2 API 1.7.0 und Versionsbereiche

Eine einzige Nebenversion für alle Ergänzungen von 0.6.0. `API_VERSION` setzt nur `api17-contract`
(`ApiContractTest`). Nach API-Stand A sind nur Ergänzungen erlaubt; jede Änderung an einer Signatur aus Stand A steht in
`docs/api/changes.md` und wird den Add-ons gemeldet. Ab RC 1 ist die API eingefroren.

| Bereich | Typen (Auszug) | Vertrag | Verhalten |
|---|---|---|---|
| Schalter und Achsen, Namen der Zubehör-Aktionen | `api/input/**`, `ClientInput`, `AccessoryBehavior#actionNameKey` | `api17-contract` | `addon-switches` |
| OSD-Elemente und Warnungen | `api/client/osd/**` | `api17-contract` | `addon-osd` |
| Kollision und Physik | Kollisionsregeln, `FlightState` | `api17-contract` | `addon-collision` |
| Werfen (mit Rechten, ohne Verbrauch), Anzeige, Stoß-Ereignis, alle Drohnen, Kamerablick | `AccessoryContext`, `AccessoryBehavior`, `AccessoryRegistry#triggerAction`, `DRONE_KNOCKBACK`, `DroneAccess`, `CameraPose` | `api17-contract` | `accessory-gaps` |
| Welt-Regeln der Add-ons, auch in Voreinstellungen | `api/worldrule/**` | `api17-contract` | `addon-world-rules` |
| Daten an der Drohne, Wartung, Pilotenwechsel | `DroneDataKey`, `ServiceExtension`, `PILOT_CHANGED` | `api17-contract` (Auslösen von `PILOT_CHANGED` inklusive) | `drone-data-service` |
| Autonomer Flug | Halten, Folgen, Punkt; Dauer bis zum Akku, Übergabe beim FPV-Ende, Start und Landung, `AUTONOMY_ENDED` | `api17-contract` | `addon-autonomy` |
| Operator und Gimbal | `GimbalContext`, `GimbalProvider` (auch Gimbal-Kopf und Brennweite), Operator-Rolle, `RecordingControl`, `spectate` | `api17-contract` | `operator-gimbal` |
| Namensraum | Ablehnung von `propwash:`-Anmeldungen | `api17-contract` (`NamespaceRules`) | `ns-guard` (Prüfung beim Start) |
| Share-Codes | `api/share`: `ShareCodes`, `ShareResult`, `ShareOptions`, `ShareTableCheck` | `api17-contract` (die ersten drei) | `pw3-codec` |
| Bedeutung eines Felds | `MotorThermal#derateStartC` (nur Doku, nur falls Phase B kommt) | – | `heat` |
| LED-Pfad für Zubehör | `api/part/AccessoryDefinition#ledPathMm` (2–256 Punkte in mm, an denen die LEDs verteilt sind) und das passende Feld im Teile-JSON; der bisherige Konstruktor bleibt für Add-ons aus 1.6 | Strang `led-path` (vor allen anderen Strängen in der Integration) | `led-path` |

**Versionsbereiche** (geprüft mit Fabric Loader 0.19.5 und maven-artifact 3.9.16, das NeoForge für Bereiche nutzt):

| Wer | Fabric (`fabric.mod.json`) | NeoForge (`neoforge.mods.toml`) | Warum |
|---|---|---|---|
| Add-ons, die API 1.7 brauchen (JMP 1.2.0, Kamera-Add-on, Mob Defense, Beispiel-Add-on) | `"propwash": ">=0.6.0-"` | `versionRange = "[0.6.0-alpha,)"` | beide nehmen `0.6.0-dev.N`, `0.6.0-rc.N` und `0.6.0` an und lehnen 0.5.x ab |
| zusätzlich zur Laufzeit | `PropwashApi.requireVersion(modId, "1.7")` | gleich | prüft die API, nicht die Mod-Version |

Nicht verwenden: `>=0.6.0` (Fabric lehnt alle dev- und rc-Stände ab), `[0.6.0,)` (NeoForge lehnt alle rc-Stände ab, weil
Maven `-rc` vor und unbekannte Zusätze wie `-dev` hinter das Release sortiert), `[0.6.0-dev.2,)` (lehnt unter NeoForge
sogar `0.6.0` ab). Die Bereiche werden beim Start des Abhängigen gesetzt und bleiben im Release-Commit unverändert, damit
der geprüfte RC dieselben Metadaten hat wie das Release. Propwash selbst trägt keine Abhängigkeit und keine
Unverträglichkeit für Just More Parts ein (3.5).

Keine öffentliche API bekommen RX-Block, Freecam und Flugbahn-Marker (`gameplay.md` E35).

### 3.3 Netzprotokoll 20

Erhöhung auf 17 für 0.6.0 im Basis-Commit. Zwischen Entwicklungsständen ändert sich die Nummer nicht mehr;
Entwicklungsstände werden nie mit Spielern gemischt. Mit 17 sind auch die Release-Kandidaten `0.6.0-rc.1` und
`0.6.0-rc.2` als Vorschau für Spieltester erschienen. Weil sich danach das Protokoll noch einmal geändert hat
(persönliche Regeln, Befund PERS1B-5), steht die Nummer ab `0.6.0-rc.3` auf 18 (`docs/RELEASING.md` §3: auch zwischen
zwei veröffentlichten Vorabversionen); der Grund steht in `docs/api/changes.md`, Abschnitt „Netzprotokoll 18“. Nach
dem veröffentlichten rc.3 hat der letzte Fix-Block das Protokoll noch einmal geändert (Rundenurteil, Ideallinie je Kurs,
Fortschritte, eingerücktes Zubehör im Teile-Abgleich); ab `0.6.0-rc.4` gilt 19, Gründe in `docs/api/changes.md`,
Abschnitt „Netzprotokoll 19“. Nach rc.4 schickt der Pilot dem Server seine Aufnahme jeder Runde, die in die Bestenliste
kommt (Befund OT4-2: Replays ohne Standbilder bei schlechter Verbindung); dafür gilt 20, Grund in `docs/api/changes.md`,
Abschnitt „Netzprotokoll 20“.

| Neu oder geändert | Richtung | Strang |
|---|---|---|
| `rx_*` (Bedarf, Kanalwerte, Einstellungen) | beide | `rx` |
| `addon_switch`, `addon_axis` | Client → Server | `addon-switches` |
| `addon_features` | Server → Client | `addon-switches` |
| `operator_input`, `operator_state`, `operator_role` | beide | `operator-gimbal` |
| `autonomy_status` | Server → Client | `addon-autonomy` |
| Stoß ins Flugmodell (`drone_push`, nur wenn ein Add-on einen Stoß anfordert) | Server → Client | `accessory-gaps` |
| Entity-Daten-Serializer und Komponente `propwash:addon_data` | Abgleich | `drone-data-service` |
| Welt-Regel-Abgleich mit `allow_freecam` und Add-on-Regeln | Server → Client | `freecam`, `addon-world-rules` |
| `personal_rules` (ab rc.3, Protokoll 18) | Server → Client | `race-rules` |
| `personal_rules_edit`, `rule_tier` (ab rc.3, Protokoll 18) | Client → Server | `race-rules` |
| Zeilen von `race_leaderboard` mit Serverregeln (`BoardRow#server`, ab rc.3, Protokoll 18) | Server → Client | `race-rules` |
| `race_lap_verdict` (Urteil zur Runde, ab rc.4, Protokoll 19) | Server → Client | `race-b` |
| `race_course_edit` mit Aktion `RACE_LINE`, `race_courses` mit Merker Ideallinie (ab rc.4, Protokoll 19) | beide | `race-b` |
| `stats_trick`, `stats_training`, `stats_ghost_beaten` (Fortschritte, ab rc.4, Protokoll 19) | Client → Server | `pw-stats` |
| `race_ghost_upload_request` (Bitte um die Spur des Piloten, nach rc.4, Protokoll 20) | Server → Client | `race-ghosts-a` |
| `race_ghost_upload` (Spur des Piloten in Stücken, nach rc.4, Protokoll 20) | Client → Server | `race-ghosts-a` |

Ohne Protokolländerung: PW3 (Wege von 0.5.2), Web-Export, Flugbahn-Marker, Kamerawinkel (bestehender Tuning-Weg),
`/propwash clear`, Slime und Wolle (Block-Tags), Motor-Hitze, alle Fixes. Client und Server mit 0.5.x und 0.6.0 trennt die
bestehende Beitrittsprüfung mit beiden Versionen im Text (V6 prüft das in beide Richtungen).

### 3.4 Daten, Formate und Kompatibilität

| Bereich | Änderung | Alte Daten | Rückweg auf 0.5.2 |
|---|---|---|---|
| Welten | neuer Block RX-Block; optional `AddonData` an Drohnen; neue Welt-Regel `allow_freecam` (Standard **aus**) | laden unverändert, `SaveFormatGoldenTest` bleibt byte-gleich | RX-Blöcke werden Luft, `AddonData` wird ignoriert |
| Weltgenerierung | Bando-Lage nur in neu erzeugten Chunks | bestehende Bandos bleiben, wie sie sind | – |
| Geräteprofile | Schaltermaske `long`; der alte Schlüssel bleibt und wird für die eingebauten Bits weiter geschrieben | werden gelesen und ergänzt | 0.5.2 liest den alten Schlüssel weiter |
| Client-Configs | `freecam.json` neu, OSD-Element Marker (aus, außer Preset FULL), neue Hinweis-Schlüssel | unverändert gültig | neue Schlüssel werden verworfen |
| Share-Codes | schreibt nur PW3, liest PW1/PW2/PW3 | alle alten Codes bleiben lesbar | 0.5.2 liest PW3 nicht (Hinweis im Changelog; Discord-Beiträge behalten den alten Code, E33) |
| Web-Export | Format 2 je Namensraum | – | – |
| Just More Parts | 1.2.0 empfohlen; 1.1.x läuft im Altmodus (3.5) | JMP-1.1.1-Drohnen laden unter 1.1.1 und 1.2.0 | – |
| Kurse, Bestenlisten, Ghosts | unverändert (`layoutHash` gleich), Öffnung gestapelter Gates nur größer | gültig | gültig |

### 3.5 Just More Parts 1.1.x mit Propwash 0.6.0 (Altmodus)

Ziel: Wer nur Propwash aktualisiert (etwa über die CurseForge-App, während JMP 1.2.0 noch in Prüfung ist), kann weiter
spielen.

- **Erkennung:** Propwash vergleicht nur die Zahlen der installierten JMP-Version (`major.minor.patch`, Zusätze wie
  `-dev.N` zählen nicht). Kleiner als 1.2.0 → Altmodus. JMP 1.1.1 verlangt selbst nur `propwash >=0.4.0` bzw.
  `[0.4.0,)` und lädt deshalb mit 0.6.0.
- **Verhalten:** JMP-Teile, Presets, Tab und Rezepte laden wie bisher (API 1.7 enthält 1.4 vollständig). Die
  Werkbank-Erweiterung von JMP 1.1.x für Share-Codes (`de.lardtom.justmoreparts.share.client.ShareWorkbenchExtension`)
  meldet Propwash nicht an, damit es nur **einen** Knopf „Sharecode“ gibt. Share-Codes schreibt die Basis: JMP-Teile
  über die JMP-Tabelle aus dem Verzeichnis-Abzug im Propwash-Jar (`sharecodes-pw3.md` §3.6), also derselbe PW3-Text wie
  mit JMP 1.2.0. `/jmp code` bleibt der Befehl von JMP 1.1.x und schreibt weiter PW2, das überall lesbar bleibt.
- **Hinweis:** eine Log-Warnung und einmal je Spielstart ein Chat-Hinweis „Just More Parts 1.1 erkannt: bitte auf 1.2.0
  aktualisieren; Share-Codes laufen jetzt über Propwash (`/propwash code`).“ XT60 zeigt an JMP-Stacks ohne
  `_mounted`-Variante das alte Bild (`fixes-polish.md` §4).
- **Umgekehrt:** JMP 1.2.0 mit Propwash 0.5.x lehnt der Loader ab (Bereiche aus 3.2). Deshalb erscheint Propwash 0.6.0
  vor JMP 1.2.0 (Release-Schritt 12).
- **Server und Client:** Die Beitrittsprüfung verlangt dieselben Add-on-Versionen wie bisher; gemischte JMP-Versionen
  zwischen Server und Client bekommen die bestehende Meldung.

---

## 4. Regeln für Stränge und Integration

### 4.1 Repos, Zweige, Worktrees

| Repo | Integrationszweig | Strang-Zweige | Worktrees |
|---|---|---|---|
| Propwash | `integration` im Integrations-Worktree; `main` bewegt die Integration nicht | `feat/0.6.0-<strang>`, `fix/0.6.0-<strang>`, `docs/0.6.0-<strang>`, `test/0.6.0-<strang>` | einer je Strang, ab dem aktuellen `integration` |
| Just More Parts | `integration-1.2.0` (neu, ab `v1.1.1`) | `feat/1.2.0-<strang>`, Release `release/1.2.0` | einer je Strang |
| Website | `release/0.6.0` (neu, ab `main`) | `site/0.6.0-<strang>`, früh `site/0.6.0-roadmap-start` | einer je Strang |

- `<strang>` im Zweig-Namen ist immer der volle Strang-Name, etwa `feat/0.6.0-rx`, `fix/0.6.0-gate-legs`,
  `feat/1.2.0-jmp-xt60`, `site/0.6.0-site-i18n`. Ältere Schreibweisen gelten nicht mehr (K16).
- Ein Strang zweigt vom **aktuellen** Integrationsstand ab, nicht von einem Tag, und rebased vor dem Merge darauf.
- Testwelten alter Versionen entstehen auf Wegwerf-Zweigen `test/fixtures-0.5.2`, `test/fixtures-0.5.0` und
  `test/fixtures-0.4.0` ab den Tags; diese Zweige werden nie gemergt.
- Commits: deutsche Nachrichten, klein.

### 4.2 Fertig-Kriterien eines Strangs

Ein Strang meldet „fertig“ erst, wenn alles davon belegt ist (Log-Auszüge im Ergebnisbericht außerhalb des Repos):

1. JUnit grün (`tools/fastcheck.sh test`), `tools/fastcheck.sh all` ohne Befund, `tools/strip_comments.py` ändert
   nichts (keine Kommentare im Code).
2. Eigene Selbsttest-Gruppen auf **Fabric und NeoForge** `PASS gefiltert` über das Slot-Skript, mit `ui-scale:` bei
   neuer Oberfläche; eigene Bildschirmfoto-Schritte auf Fabric.
3. Eigener Beitrittstest mit dediziertem Server, wenn der Strang Netzwerk oder Server-Verhalten ändert.
4. Doku-Baustein `docs/<thema>.md` (Bedienung, Grenzen, englische Kernsätze für die Website), Text für den
   FOUNDATION-Abschnitt und Changelog-Zeilen (Englisch und Deutsch, für Spieler) im Ergebnisbericht.
5. Sprachschlüssel nur im eigenen Ordner (4.5), keine erzeugten Dateien im Commit.
6. Rebase auf den aktuellen Integrationsstand, Build grün danach.

### 4.3 Integration und Veröffentlichung

- **Eine Sperre für alles:** Jeder Merge in einen Integrationszweig (Propwash, JMP, Website, Add-ons), jede
  Veröffentlichung nach `mavenLocal` (aus jedem Repo) und jeder RC- und Release-Schritt läuft unter derselben
  Integrations-Sperre. Es integriert immer höchstens einer.
- **Merge-Prüfung je Strang:** `git merge --no-ff`, Sammeldateien auflösen, `mergeLang`, Build, `fastcheck all` und
  `test`, gefilterter Selbsttest der Gruppen des Strangs auf Fabric (NeoForge zusätzlich, wenn der Strang Loader-Code
  ändert), Server-Rauchtest bei Netzwerk- oder Server-Änderungen, Tages-Changelog-Eintrag. Scheitert die Prüfung und
  dauert der Fix länger als 30 Minuten, wird der Merge zurückgenommen und der Strang zurückgegeben. Die Merge-Prüfung
  vergleicht die geänderten Dateien mit der Besitzliste des Strangs; Abweichung = Rückgabe.
- **Veröffentlichung:** Commit „Version 0.6.0-dev.N“ mit neuer Nummer, `publishToMavenLocal`, Nachricht an die
  Abhängigen mit Nummer und Inhalt (3.1).
- **Stufenabschluss** (Ende der Grundlagen und Funktionsstopp): voller Selbsttest beider Loader in Teilen
  (`tools/selftest-sharded.sh all --screenshots`, etwa 69 Minuten), Rauchtests Client und Server beider Loader, alle
  Beitrittstests der Stufe auf beiden Loadern, Kalibrierung und Golden byte-gleich, neue `dev.N`, Export-Vorprobe ohne
  Bauen.
- **Stand-Prüfung** nach jedem Merge, jeder Veröffentlichung und jedem Stufenabschluss durch eine eigene Prüfung:
  Zweige und Merges wie geplant, keine neuen öffentlichen Tags, Jars nur unter `release/internal/`, `mavenLocal`-Nummer
  neu, Exporte unberührt. Erst danach startet der nächste abhängige Schritt.

### 4.4 Sammeldateien

Diese Dateien dürfen mehrere Stränge gleichzeitig ändern, aber nur **anhängend** (eine Zeile oder ein abgegrenzter
Block) und in einem **eigenen kleinen Commit** des Strangs. Den Konflikt löst die Integration mechanisch.

| Repo | Sammeldateien |
|---|---|
| Propwash | `PropwashCommon`, `PropwashClient` (Modul-Anmeldung); `server/ServerCommands` (Unterbefehle); `guide/GuideScenes`; `dev/example/ExampleAddon`; `input/client/screen/ModesTab` und `server/client/WorldRulesTab` (nur die eine Zeile bzw. Methode von `freecam`, `gameplay.md` §10.2); Selbsttest- und Beitrittstest-Anmeldung im Quell-Set `devtest`; `common/build.gradle`, `fabric/build.gradle`, `neoforge/build.gradle`, `settings.gradle`; `fabric.mod.json`, `neoforge.mods.toml`; `docs/FOUNDATION.md`, `docs/ARCHITECTURE.md`, `docs/testing.md` (eigener Abschnitt) |
| Propwash, nur Integration | `gradle.properties` (Version), `Constants.PROTOCOL_VERSION` (nur Basis-Commit), erzeugte `assets/propwash/lang/*.json`, `CHANGELOG.md`, `docs/RELEASE-NOTES.md`, `docs/api/changes.md`, `docs/modrinth/**`, Tages-Changelog |
| Just More Parts | `build.gradle`, `gradle.properties`, `CHANGELOG.md`, `tools/public_export.json` |
| Website | `README.md` (eigener Abschnitt), `tools/site-nav.mjs` (`LINKS` gehört `site-addons`, der Rest `site-i18n`), `sitemap.xml` und erzeugte `/<lang>/**` (nur `site-release-060`) |

### 4.5 Sprachdateien

- Neue Schlüssel stehen in einem **eigenen neuen Ordner** `common/src/lang/<strang>/` (`de_de.json`, `en_us.json`),
  zum Beispiel `freecam`, `rx`, `share`, `guard`, `fpm`, `heat`, `camangle`. `mergeLang` liest jeden Unterordner und
  bricht bei doppelten Schlüsseln ab; so fallen Kollisionen beim Bauen auf.
- Bestehende Modul-Ordner ändert ein Strang nur, wenn kein paralleler Strang denselben Ordner ändert (zum Beispiel
  Textkorrekturen in `zone` durch `ui-doc-polish`).
- Die erzeugten Dateien `assets/propwash/lang/*.json` committet nur die Integration.
- Nach dem Funktionsstopp darf die Integration Schlüssel ohne Textänderung in Fach-Ordner umziehen (eigener Commit).
- Deutsche Texte mit Umlauten und ß; JMP und Website halten sich an ihre eigenen Sprachregeln aus `website-jmp.md`.

### 4.6 Community-Add-ons und öffentliche Stände

- **Noclip-Add-on der Community:** Vor dem Bau werden die Mixin-Ziele aus dem echten Jar gelesen (in einem eigenen
  leeren Ordner, nie im Repo). Die Liste gilt als „geschützte Signaturen“: Stränge, die diese Klassen ändern
  (`pilot-prep`, `addon-switches`, `addon-collision`), behalten Namen und Deskriptoren dieser Ziele, wo es irgend geht.
  Was trotzdem nicht zu halten ist, ersetzt die API 1.7 (`addon-api-namespace.md` §2.3.1); Changelog
  (Kompatibilität) und Add-on-Handbuch (Umstiegsbeispiel „Noclip ohne Mixins“) sagen das offen. V3 startet Propwash mit
  Noclip auf beiden Loadern. Die Nachricht an den Autor schreibt der Projektinhaber vor dem Release.
- **Öffentliche Stände:** veröffentlichte Tags, Releases und Exporte werden nie verändert. Push nur nach Freigabe.
- **Vorgaben im Repo** stehen sinngemäß, nie wörtlich mit Zuschreibung, weil `docs/design` öffentlich exportiert wird.

---

## 5. Reihenfolge und Übergaben

Die Reihenfolge ergibt sich aus 2.1 und den Datei-Abhängigkeiten aus 2.2: Basis-Commit → Grundlagen (API-Vertrag,
Schaltermaske, Oberflächen, Codec, Web-Export, Prüf-Infrastruktur, Landungs-Selbsttest, die meisten Spiel-Features und
Fixes) → Verhalten der API, Freecam, Share-Oberfläche, JMP-Tabelle und Konfigurator → **Funktionsstopp** → Doku, Texte
und Übersetzungen → RC.

- **API-Stand A:** Das Kamera-Drohnen-Add-on und Mob Defense bauen gegen die Verträge, mit genauer `dev.N`. Verhalten
  liefern die späteren Stränge; jedes Add-on bekommt eine Nachricht, sobald „sein“ Strang gemergt ist (Mob Defense:
  `accessory-gaps`, `addon-world-rules`, `addon-switches`, `addon-osd`, `drone-data-service`; Kamera:
  `drone-data-service`, `addon-autonomy`, `operator-gimbal`, `addon-switches`, `addon-osd`, `addon-world-rules`). Die
  Lücken aus beiden Add-on-Entwürfen stehen schon im Vertrag (`addon-api-namespace.md` §3.19). Stöße durch Explosionen fordert Mob Defense über `DRONE_KNOCKBACK` selbst an (E27).
- **API-Stand B:** Add-ons testen echtes Verhalten. Ihre Schlussprüfung läuft gegen den letzten RC.
- **Backport 1.21.1:** teilt nur Test-Infrastruktur und Integrations-Sperre. Die internen Klassen für Werfen, Anzeige
  und autonomen Flug sind so geschnitten, dass der fest eingebaute Dispenser und die programmierbare Drohne sie
  übernehmen können (`addon-api-namespace.md` §4).

---

## 6. Prüfplan

### 6.1 Stufen

1. **Strang:** Fertig-Kriterien (4.2).
2. **Merge:** Merge-Prüfung (4.3).
3. **Stufenabschluss:** voller Lauf, Beitrittstests, dann Prüfrunde mit den betroffenen Prüfbereichen. Vor dem ersten
   vollen Lauf ist `landing-stability` gemergt: Die Gruppe „drone: Landung“ besteht danach 30 gefilterte Fabric- und 15
   NeoForge-Läufe hintereinander ohne Ausreißer (heute 1 von 9), Goldens und Kalibrierung byte-gleich.
4. **Release-Kandidat:** alle zwölf Prüfbereiche, Fix-Runden, letzter RC mit ungeteilten Läufen.

### 6.2 Unabhängige Prüfer

Jeder Prüfbereich hat einen eigenen Prüfer, der den geprüften Strang nicht gebaut hat. Er bekommt nur Fach-Dokument,
Commit, Testausgaben, Bilder und dieses Dokument, nicht den Ergebnisbericht des Erbauers. Er belegt jeden Befund mit
Log-Auszug, Bild oder Testfall und stuft ihn ein: **hoch** (Absturz, Datenverlust, falsches Ergebnis, Vorgabe verfehlt),
**mittel** (sichtbarer Fehler, falscher Text, fehlender Test), **niedrig** (Kosmetik).

| Bereich | Gegenstand | Grundlage | Methode | Prüfrunde |
|---|---|---|---|---|
| V1 Share-Codes | alle Generationen in Mod und Website (6.5) | `sharecodes-pw3.md` §6, `website-jmp.md` §6.2 | JUnit mit Korpus, Selbsttest `share:` beider Loader, `ShareJoinCheck`, Node-Tests, kopfloser Browser | 1 (Codec), 2, R |
| V2 API-Vertrag | API 1.7.0 gegen 1.6.0 (nur Ergänzungen), Beispiel-Add-on, Handbuch, Versionsbereiche (3.2) | `addon-api-namespace.md` §3, §6.1 | Signaturvergleich der Klassen beider Jars, Beispiel-Add-on-Läufe gegen `dev.N` und `rc.N`, Handbuch gegen Code | 1, 2, R |
| V3 Namensraum und Verträglichkeit | Probe, JMP 1.1.1 (Altmodus: Start, Hinweis einmal, 0 Funde, ein Sharecode-Knopf, PW3 gleich wie mit 1.2.0), JMP 1.2.0 (0 Funde), Noclip, Ressourcenpakete nur Info | `addon-api-namespace.md` §6.2–6.3, `sharecodes-pw3.md` §6.4, 3.5 | Rauchtest-Starts Client und Server auf beiden Loadern | 1, R |
| V4 Spiel-Features | Marker, Freecam (Regel standardmäßig aus), RX (lädt nie Chunks), Kamerawinkel, Gate-Hinweis, Oberflächen, `clear` | `gameplay.md` §1, §6 | Vorgaben Punkt für Punkt gegen Ausgaben und Bilder | 1, 2, R |
| V5 Fixes | gestapelte Gates, XT60, Druckplatten und Stolperdraht, Signal, Zonenzeile, Bando-Lage | `fixes-polish.md` §6 | wie V4, XT60 Bilder aller Rahmen und Akkuplätze, Bandos auf acht Seeds | 1, R |
| V6 Mehrspieler | Beitritts-Matrix (6.3), zwei echte Clients, Versions-Trennung | dieses Dokument | `mp_dedicated_test.sh`, Beitrittstests beider Loader, beide `latest.log` ohne unerwartete Fehler | 2, R |
| V7 Altwelt-Umstieg | Testwelten 0.5.2, 0.5.0 und 0.4.0, JMP 1.1.1 → 1.2.0, Rückweg | dieses Dokument | Gruppe `migration:` auf Client und dediziertem Server, beide Loader (6.4) | 1, 2, R |
| V8 Bildschirmfotos und Oberfläche | alle neuen und geänderten Bilder, EN/DE, 854×480 und 320×240, `ui-scale:` | Abschnitte 5 der Fach-Dokumente | Abgleich gegen die Bilder von `v0.5.2` mit Rausch-Filter, jedes geänderte Bild ansehen | 1, 2, R |
| V9 Website | alle Tests, Links, Sprachen, Banner, Downloads nur Modrinth/CurseForge, keine Server-Seiten, Datenschutz | `website-jmp.md` §6.4 (P-Web, P-Bild, P-Sprache) | Node-Tests, Linkprüfung, kopfloser Browser 390/1280 px, Rückübersetzung | 2, R |
| V10 Just More Parts 1.2.0 | Tabelle (eingefroren, nur angehängt), Codec entfernt, `/jmp code`-Hinweis, XT60, Selbsttest und Rauchtests beider Loader, Versionsbereiche | `website-jmp.md` §6.3, P-XT60 | JMP-Tests, Start mit 0.6.0-rc.N | 2, R |
| V11 Release-Artefakte | Klon-Probe, Export-Prüfung Propwash und JMP, Jar-Inhalte, `SHA256SUMS`, Metadaten (Abhängigkeiten und Bereiche nach 3.2, keine Unverträglichkeit für JMP), Changelog-Form, keine Werkzeug-Spuren in Dateien und Commits seit `v0.5.2` | `docs/RELEASING.md`, 6.7 | Skripte und Sichtprüfung | R |
| V12 Realismus | Motor-Hitze (Phase-A-Daten gegen Erfahrungswerte: Whoop, 5″, 7″, X-Class; Entscheidung über Phase B nachvollziehbar) und neue Whoop-Motoren (Datenbeleg, Prüfstand, Preset) | `gameplay.md` §3.H, `website-jmp.md` P-Daten | Kennlinien und Prüfstand-Ausgaben gegen Quellen | 1, R |

**Fix-Runden:** Hohe und mittlere Befunde gehen zurück an den Erbauer des Strangs. Nach dem Fix prüft ein frischer
Prüfer nur den betroffenen Bereich erneut. Fertig ist eine Runde, wenn kein hoher oder mittlerer Befund offen ist.
Niedrige Befunde kommen in „Bekannte Kleinigkeiten“ (Abschnitt 8) und in den Release-Bericht.

### 6.3 Mehrspieler auf dediziertem Server

Jeder Fall läuft auf **Fabric und NeoForge** (dedizierter Server und Client desselben Loaders), danach werden beide
`logs/latest.log` auf unerwartete Fehler geprüft.

| Fall | Schalter / Werkzeug | Erwartung (Kurzform) | Quelle |
|---|---|---|---|
| Pilot fliegt auf dem Server | `PROPWASH_JOINTEST_PILOT=1`, erweitert um Kamerawinkel 40° im K-Menü | Server sieht 40° über `flight_tune` | `gameplay.md` §6.3 |
| RX-Block | `PROPWASH_JOINTEST_RX=1` | Kanal 0 → 100 % wird Redstone 0 → 15, außer Reichweite bleibt der Zustand, entladener Chunk wird nicht geladen | `gameplay.md` |
| Freecam | `PROPWASH_JOINTEST_FREECAM=1` (Server schaltet die Regel ein) | keine Server-Pakete während der Freecam, Regel aus beendet sie; ohne Regel kein Start | `gameplay.md` |
| `clear`, RX-Missbrauch | Server-Rauchtest (`ClearServerChecks`, `RxServerChecks`) | Gast abgelehnt, Operator entfernt alles, falsche Payloads verworfen | `gameplay.md` |
| Gate-Hinweis | Beitrittstest-Client setzt ein Gate | Hinweis trotz Server-Verzögerung | `gameplay.md` |
| Gestapelte Gates, Druckplatten, Signal | `StackGateJoinCheck`, `PlateJoinCheck`, `SignalJoinCheck` | Durchflug unten zählt, Tür öffnet, Signal ohne Luft-Fehler | `fixes-polish.md` |
| Share-Codes | `PROPWASH_JOINTEST_SHARE=1` | `give`, Einkaufsliste vom Server, fehlendes Add-on am Server, Ersatz | `sharecodes-pw3.md` §6.3 |
| API 1.7 | `PROPWASH_JOINTEST_ADDON17=1` | Server-Schalter, Kollision mit und ohne Recht, Operator (a) und (b), Add-on-Daten `TRACKING`/`NONE`, Probe-Hinweis einmal je Operator | `addon-api-namespace.md` §6.3 |
| **Zwei echte Clients** | `tools/mp_dedicated_test.sh` (neu, ein Server und zwei Clients aus zwei Worktrees desselben Commits, drei Minecraft-Prozesse, nur mit Zustimmung des Speicher-Wächters des Slot-Skripts) | Pilot A fliegt ein Rennen mit Ghost, Client B schaut per F7 zu und sieht Flugbahn-Marker und Renn-HUD; B wird Operator am Gimbal einer Drohne im autonomen Halten; danach `/propwash clear` durch den Operator; beide sehen dieselbe Bestenliste | dieses Dokument |
| Versions-Trennung | Client 0.5.2 → Server 0.6.0 und umgekehrt | klare Meldung mit beiden Versionen, kein Absturz | `docs/design/join-check.md` |
| Add-on nur beim Spieler | Client mit Beispiel-Add-on, Server ohne | Seite „Add-ons“ zeigt „inaktiv“, nichts wirkt am Server | `addon-api-namespace.md` |
| JMP | Server 0.6.0 + JMP 1.2.0, Client mit und ohne JMP; Server und Client mit JMP 1.1.1 (Altmodus) | Teile-Abgleich wie in 0.5.2, Hinweis bei fehlendem JMP; im Altmodus fliegen beide, ein Sharecode-Knopf | `website-jmp.md` P-Kompat, 3.5 |

**Alte Stände starten:** Version 0.5.2 (für die Versions-Trennung und den Rückweg) läuft immer aus einem
Wegwerf-Worktree des Tags `v0.5.2` mit dessen eigenem Gradle (`runClient` bzw. `runServer`), gestartet über das
Slot-Skript; der Port des alten Servers steht vorher in `server.properties` dieses Worktrees, `eula=true` ist gesetzt.
Lose Jars aus Release-Ordnern werden nicht gestartet; sie dienen nur dem Abgleich der Prüfsummen mit dem gebauten Stand.

### 6.4 Altwelt-Umstieg

- **Testwelten** (`world-fixtures`, Größe M–L): je alter Stand ein eigener Schreib-Schritt im Selbsttest, gebaut gegen
  den Code dieses Stands auf dem Wegwerf-Zweig ab dem Tag, nacheinander in je einem Wegwerf-Worktree (etwa 60
  Minecraft-Minuten zusammen). Ab `v0.5.2` voll: Drohnen aus allen Presets und mit JMP-1.1.1-Teilen, benannte und
  lackierte Drohnen, Homepoint im NBT, wasserdicht beschichtete Drohne, manuelle VTX-Leistung; gestapelte und frei
  gedrehte Gates; ein Kurs mit Bestenliste und Ghost; zwei Zonen (eine rennkonform); geänderte Welt-Regeln; dazu
  Client-Configs mit eigenem OSD-Layout, Geräteprofil mit belegten Schaltern auf hohen Bits, gesetzte Hinweise. Daneben
  eine Erwartungsdatei (JSON) mit allen Werten. Dasselbe kleiner ab `v0.5.0` und, nur mit Drohnen, Gates und einem Kurs,
  ab `v0.4.0` (entfällt mit Vermerk, falls der Tag mit den heutigen Werkzeugen nicht baut). Ergebnis außerhalb der Repos.
- **Prüfung in 0.6.0** (Gruppe `migration:` aus `verify-infra`): Welt-Kopie laden, alle Werte gegen die Erwartungsdatei,
  neue Standards prüfen (`allow_freecam` aus, Marker aus, Kamerawinkel unverändert, Schaltermaske umgestiegen und alle
  Modi schalten), Runde durch das unterste gestapelte Gate zählt, Log ohne Fehler; speichern, neu laden, erneut
  vergleichen. Dasselbe am **dedizierten Server** mit einem beitretenden Piloten.
- **JMP:** dieselbe Welt mit JMP 1.1.1 (Altmodus) und mit JMP 1.2.0 → alle JMP-Teile bleiben, Codes der Drohnen lassen
  sich als PW3 exportieren, in beiden Fällen derselbe Text.
- **Rückweg** (niedrige Priorität): Die umgestiegene Welt in 0.5.2 öffnen (Start nach 6.3) → kein Absturz, RX-Blöcke
  sind Luft, Geräteprofil liest weiter.

### 6.5 Alle Share-Code-Generationen

| Quelle | Inhalt | Prüfung |
|---|---|---|
| Golden-Vektoren aus JMP 1.1.1 | PW1, PW2 | Lesen in Mod und Website gleich, Umrechnung in PW3 byte-gleich in Java und JavaScript |
| PW3-Vektoren (`pw3-codec`, Anhang B von `sharecodes-pw3.md`) | alle Ebenen, Kurzkennung und voller Namensraum, Prüfsumme, Mindestversion, Name/Link, unbekannte Nummern | Kodieren und Dekodieren byte-gleich in beiden Sprachen |
| Zufälliger Rundlauf | 10000 Baupläne mit festem Startwert | Mod: Bauplan → PW3 → Bauplan identisch |
| Echte Codes aus Discord | alles, was im Drohnen-Channel und in Hilfe-Threads steht (nur lokal, ohne Namen) | jeder Code: Mod und Website ergeben denselben PW3-Text; jeder Status ungleich OK ist erklärt |
| Generationswechsel im Spiel | Code aus 0.5.2 mit JMP 1.1.1 erzeugt (Testwelt) | Import in 0.6.0 → PW3-Export → Website dekodiert dieselbe Drohne (Teile, Lack, Tuning, OSD, Name) |
| Altmodus | PW3-Export einer JMP-Drohne mit JMP 1.1.1 und mit JMP 1.2.0 | byte-gleich |
| Fehlerfälle | kaputte, gekürzte, fremde Codes, falsche Prüfsumme | klare Meldung, kein Absturz, nichts übernommen |
| Befehle und Oberfläche | `/propwash code …`, `/jmp code …` (Hinweis in 1.2.0, alter Befehl in 1.1.x), Werkbank-Import, Einkaufsliste, Ersatz im Kreativmodus | Selbsttest `share:` beider Loader und Beitrittstest |

### 6.6 Bildschirmfotos

- Quelle: die Bildschirmfoto-Schritte im vollen Fabric-Lauf (`-PpropwashScreenshots=true`) und die Website-Bilder aus
  dem kopflosen Browser.
- V8 vergleicht jedes Bild mit dem Stand von `v0.5.2` (Rausch-Filter wie im Release-Lauf von 0.5.2), sieht jedes neue und
  jedes geänderte Bild an und prüft Texte (Englisch und Deutsch, Umlaute, nichts abgeschnitten), Theme, 854×480 und
  320×240. Unbeabsichtigte Änderungen an alten Bildern sind mindestens „mittel“.
- Die geprüften Bilder gehen in die Website-Doku (`site-docs`).

### 6.7 Klon-Probe und öffentlicher Export

- Export ohne Bauen in ein Wegwerf-Verzeichnis, dort mit leerem `HOME`, eigenem `GRADLE_USER_HOME` und minimalem
  `PATH`: `./gradlew :common:test` grün; JUnit aus fremdem Arbeitsverzeichnis grün; dieselbe Suite mit einem
  x86_64-JDK unter Rosetta (`addon-api-namespace.md` §6.4).
- Export-Prüfung für Propwash und JMP besteht (Pflichtdateien, Sperrliste, lokale Pfade, Zitate, Größen); im
  Basis-Commit und vor jedem Stufenabschluss als Vorprobe, vor dem Release endgültig.
- Suche nach Werkzeug-Spuren in allen Commits seit `v0.5.2` (JMP seit `v1.1.1`, Website seit `cfe6713`): keine
  Werkzeug- oder Firmennamen.

### 6.8 Freigabe-Kriterien für den Release

0.6.0 ist bereit für die Freigabe, wenn alles zutrifft:

1. Letzter RC: JUnit grün, `fastcheck` ohne Befund, keine Kommentare, Kalibrierung und Golden byte-gleich.
2. Ungeteilte Selbsttests Fabric (mit Bildschirmfotos) und NeoForge genau `PASS`, Rauchtests Client und Server beider
   Loader `PASS`.
3. Alle Fälle aus 6.3 auf beiden Loadern bestanden, der Test mit zwei Clients bestanden.
4. Testwelten 0.5.2 und 0.5.0 bestanden, auf Client und dediziertem Server; 0.4.0 bestanden oder mit Vermerk entfallen.
5. Share-Codes nach 6.5 vollständig, null unerklärte Abweichungen zwischen Mod und Website.
6. JMP 1.2.0 RC gegen den letzten Propwash-RC grün; JMP 1.1.1 läuft im Altmodus (Start, Hinweis, ein Knopf).
7. Website-Tests, Linkprüfung und Bildprüfung grün, alle Sprachen mit voller Abdeckung.
8. Klon-Probe und Export-Prüfung grün, keine Spuren.
9. Kein hoher oder mittlerer Befund offen in V1–V12.
10. Stand-Prüfung nach dem letzten RC ohne Befund.

---

## 7. Release-Schritte

Grundlage ist `docs/RELEASING.md` §4, erweitert um JMP und Website. Alle Schritte unter der Integrations-Sperre und im
Integrations-Worktree; `main` bewegt kein Release-Schritt.

1. **JMP-Tabelle einfrieren** (`freeze` des Prüfwerkzeugs) und den Abzug der Kurzkennungen in Propwash abgleichen.
2. **Texte:** `CHANGELOG.md` in der Spieler-Form (2–3 Highlights; Neu, Verbessert, Behoben; Kompatibilität mit
   Welten, Servern, Add-ons: Protokoll 18, JMP 1.2.0 empfohlen und 1.1.x im Altmodus, PW3, Noclip-Ergebnis, Freecam-Regel
   standardmäßig aus), `docs/api/changes.md`, `docs/RELEASE-NOTES.md`, README, `docs/modrinth/`, Add-on-Handbuch.
3. **Letzter RC** nach den Fix-Runden, Freigabe-Kriterien 6.8 erfüllt.
4. **Version** `0.6.0` (gleicher Stand wie der letzte RC, nur die Nummer; Versionsbereiche unverändert), JMP `1.2.0`;
   `strip_comments`, `fastcheck`, Kalibrierung mit JMP-Daten, `clean build`; Rauchtests beider Loader mit der
   Release-Nummer.
5. `publishToMavenLocal`, `tools/export_rules.sh`, Web-Exporte (`/propwash webexport` für `propwash` und
   `justmoreparts`).
6. **Jars** nach `release/0.6.0/` bzw. `release/1.2.0/` im jeweiligen Integrations-Worktree, mit `SHA256SUMS`; interne
   Stände nach `release/internal/`.
7. **Tags** `v0.6.0` und JMP `v1.2.0` lokal auf die Release-Commits der Integrationszweige. `main` spult die Integration
   nicht vor; das übernimmt der Projektinhaber.
8. **Website** `release/0.6.0` final: Doku aus dem Release-Stand neu erzeugen und testen, Plattform-Status ehrlich „in
   Prüfung“, Roadmap mit 0.6.0 unter „Schon da“.
9. **Öffentliche Exporte** für Propwash und JMP aus den Release-Commits erzeugen und prüfen (noch kein Push).
10. **Release-Paket** mit Stand, Prüfsummen, Bericht und offenen Freigaben, dazu die Upload-Unterlagen: Jars mit
    `SHA256SUMS` und je eine Changelog-Datei `changelog-0.6.0.md` und `changelog-1.2.0.md` im Format der
    `CHANGELOG.md`-Abschnitte. Das Upload-Werkzeug liest Jars aus `release/<version>/` des lokalen Repositorys; dorthin
    kopiert sie der Projektinhaber selbst (oder mit seiner Zustimmung), weil `main` bis dahin auf dem alten Stand steht.
    `release/` ist in beiden Repositorys per `.gitignore` ausgeschlossen.
11. **Nach Freigabe:** Exporte und Tags pushen, GitHub-Releases mit Jars und Prüfsummen (per Download geprüft).
12. **Upload** auf Modrinth und CurseForge: zuerst Propwash 0.6.0; JMP 1.2.0, sobald Propwash 0.6.0 auf der jeweiligen
    Plattform freigegeben ist (JMP 1.2.0 verlangt Propwash 0.6.0, Propwash 0.6.0 läuft mit JMP 1.1.x weiter).
13. **Website live**, sobald beide Plattformen die Dateien zeigen oder der Status ehrlich „in Prüfung“ steht.
14. **Discord-Beiträge** erst, wenn beide Mods auf beiden Plattformen freigegeben sind: 0.6.0-Ankündigung,
    PW3-Umstellung (alten Code stehen lassen, PW3 ergänzen, Link `/convert/`), JMP 1.2.0.
15. **Nachlauf:** Worktrees und Builds aufräumen, niedrige Befunde in den Plan der nächsten Version.

---

## 8. Bekannte Kleinigkeiten

| Punkt | Stand | Umgang in 0.6.0 |
|---|---|---|
| Bando-Lage auf 2 von 8 Seeds (Scheune am Hang schwebt, Kirschblatt ohne Stamm), seit 0.5.0 | offen | Strang `bando-placement` (`fixes-polish.md` §3.G), nur neue Chunks |
| Ausreißer im Selbsttest „drone: Landung“ (1 von 9 Läufen) | offen | Strang `landing-stability` vor dem ersten vollen Lauf (6.1) |
| „Rennen neu starten“ gibt es seit 0.5.2 nicht mehr; „Reset“ bringt die Drohne zum Homepoint und bricht die Runde ab (Welt-Regel „Reset erlauben“) | gewollt | `ui-doc-polish` entfernt verbliebene Erwähnungen in Doku, Flugschul-Texten und Website und nennt „Reset“ als Ersatz |
| Hover im Lack-Modus auf unlackierbaren Flächen (Web) | offen | `site-configurator` (`fixes-polish.md` §3.E) |
| Satz „Großes Update auf 0.5.1.“ in `docs/RELEASE-NOTES.md` | offen | `ui-doc-polish` liefert den Satz, die Integration trägt ihn ein (K26) |
| Runden mit Flughilfe nur mit Welt-Regel gespeichert | gewollt | keine Änderung (`gameplay.md` §9.2) |

---

## 9. Risiken

| Risiko | Folge | Gegenmaßnahme |
|---|---|---|
| Minecraft-Läufe sind der Engpass, Add-ons und Backport laufen parallel | Stufen ziehen sich | Vorrang für Integration und Release, Läufe in Teilen, lange volle Läufe nachts |
| Viele parallele Gradle-Läufe bei 24 GB RAM | Abbruch von Minecraft-Läufen | Build-Pool im Slot-Skript, Speicher-Wächter |
| Noclip bricht beim Start | Community-Spieler stürzen ab | geschützte Signaturen, API-Ersatz mit Umstiegsbeispiel, Test mit dem echten Jar, offener Changelog, Nachricht an den Autor vor dem Release |
| Konflikte in Sammeldateien | Merge-Verzögerung | nur anhängen, eigener Commit, Integration löst mechanisch |
| Ein Strang wächst über seinen Besitz hinaus | Konflikte mit parallelen Strängen | Merge-Prüfung vergleicht die geänderten Dateien mit der Besitzliste; Abweichung = Rückgabe |
| Review-Zeiten von Modrinth und CurseForge verschieden | Spieler mit nur einem aktualisierten Mod | Altmodus für JMP 1.1.x (3.5), Propwash zuerst, JMP danach, Discord erst nach allen Freigaben |
| Versionsbereich lehnt Vorabstände ab | Add-on-Prüfung gegen dev/rc unmöglich | Bereiche nach 3.2, V2 und V10 starten gegen `dev.N` und `rc.N` |
| Freecam als Späh-Kamera auf Survival-Servern | Wall-Hack durch ein Update | Welt-Regel standardmäßig aus (E26) |
| RX-Block lädt entfernte Chunks | Tick-Lag auf Servern | nur geladene Chunks lesen und setzen (`gameplay.md` §3.C), Test zählt geladene Chunks |
| Export-Prüfung scheitert kurz vor dem Release | Verzögerung | Vorprobe im Basis-Commit und an jedem Stufenabschluss |
| Prüfer übersehen etwas | Fehler im Release | Belegpflicht je Befund, Gegenprüfung mit frischem Prüfer, Stand-Prüfung |
| Abweichung zwischen arm64 und x86_64 | Bit-Tests im öffentlichen Repo rot | Rosetta-Lauf in der Klon-Probe, zweite Golden-Spalte (`addon-api-namespace.md` E30) |

---

## 10. Entscheidungen (was – warum – verworfen)

| Nr. | Entscheidung | Warum | Verworfen |
|---|---|---|---|
| E1 | Vorgaben stehen sinngemäß mit Datum und Quelle | `docs/design` wird öffentlich exportiert; die Export-Prüfung bricht bei wörtlichen Zitaten mit Zuschreibung ab | wörtliche Zitate |
| E2 | Server-Mod und alle Basis-Teile dafür sind nicht in 0.6.0; die Server-Mod wird gesondert besprochen | Vorgabe vom 17.10. abends; ohne Server-Mod gäbe es keine Nutzer für die Haken | Basis-Haken vorab einbauen |
| E3 | Alle Stränge zweigen vom aktuellen `integration` ab (ab `53ce0049`) | die parallele Test-Infrastruktur ist dort; ab `v0.5.2` würde ein Server-Rauchtest die Welt eines parallelen Laufs löschen | Abzweig von `v0.5.2` wie in den Fach-Dokumenten |
| E4 | Protokoll 17 im Basis-Commit, danach fest | kein Wettlauf zwischen Strängen; Entwicklungsstände verbinden sich nie mit 0.5.2 | „erster Strang mit Payload“; Erhöhung erst am Ende |
| E5 | Rollende Integration mit Merge-Prüfung je Strang, voller Lauf nur am Stufenabschluss | Konflikte fallen früh auf, Abhängige starten früher; ein voller Lauf je Merge kostete etwa 25 Stunden Minecraft-Zeit mehr | Sammel-Merge am Ende; voller Lauf nach jedem Merge |
| E6 | Einheitliche Zweig-Namen nach 4.1 | Skripte und Stand-Prüfung brauchen ein Schema | die drei Schreibweisen der Fach-Dokumente |
| E7 | Keine Server-Seiten, kein Verzeichnis-Eintrag und kein FAQ-Zusatz zur Server-Mod; Roadmap führt sie ohne Datum in einer späteren Gruppe | Zurückstellung; schon öffentlich angekündigt, also nicht streichen, aber nicht als 0.6.0 versprechen | Seiten vorab bauen; Server-Mod aus der Roadmap löschen |
| E8 | Web-Export als Befehl in der Basis, Format 2; JMP-Export entfällt | jedes Add-on soll ohne eigenen Code ins Verzeichnis kommen; ein Orakel statt zweier | Format 1 im JMP-Devtest behalten |
| E9 | Umrechner unter `/convert/`; D1 in `site-configurator`, D2 in `site-addons`, Jar-Leser im Konfigurator | ein Besitzer je Datei; der Jar-Leser ist Teil des Konfigurators | `sharecode/`; getrennte D-Stränge neben S3/S4 |
| E10 | JMP in J1–J4 aufgeteilt | Motoren können sofort starten, Release-Dateien bleiben bei einem Strang | ein JMP-Strang für alles |
| E11 | Vorab-Strang `pilot-prep` und feste Reihenfolge für Dateien mehrerer Stränge | dieselben Dateien würden sonst gleichzeitig geändert | parallele Arbeit in denselben Methoden |
| E12 | `PILOT_CHANGED` wird in `api17-contract` ausgelöst | eine Zeile, die sonst `DronePiloting` doppelt belegt | `drone-data-service` vor `addon-collision` |
| E13 | Neue Sprachschlüssel in eigenen Ordnern je Strang, erzeugte Dateien nur durch die Integration | `mergeLang` liest alle Ordner und meldet Doppelte; keine Konflikte in großen JSON-Dateien | alle schreiben in `assets/propwash/lang` |
| E14 | Sammeldateien nur anhängend in eigenem Commit | Anmeldezeilen lassen sich nicht sinnvoll einem Strang allein geben | eigener Strang nur für Anmeldezeilen |
| E15 | `operator-gimbal` baut parallel zu `addon-collision`/`addon-autonomy`, nur Test (a) wartet | keine gemeinsame Datei | strenge Reihenfolge bis `operator-gimbal` |
| E16 | Geschützte Signaturen aus dem Noclip-Jar, Ersatz durch die API, wo sie sich nicht halten lassen | ein Absturz beim Start trifft Spieler, nicht nur den Autor | Absturz hinnehmen und nur informieren |
| E17 | `dev.N` je Veröffentlichung, `rc.N` in der Release-Phase | Add-ons brauchen benannte, unveränderliche Stände | gleiche Nummer mehrfach veröffentlichen |
| E18 | Eine Integrations-Sperre für alle Repos und jede Veröffentlichung nach `mavenLocal` | Vorgabe „nie zwei Integratoren“; parallele Merges in JMP oder Website bauen sonst gegen halb veröffentlichte Stände | eine Sperre je Repo |
| E19 | Letzter RC mit ungeteilten Läufen, Fix-Runden mit Teile-Läufen | `docs/RELEASING.md` verlangt genau `PASS` ohne Filter; Teile sparen in den Runden Zeit | nur Teile-Läufe |
| E20 | Zwölf Prüfbereiche mit Belegpflicht und Schwere-Stufen; Prüfer nie der Erbauer | Vorgabe: viele unabhängige Prüfer, Fix-Runden bis nichts Mittleres offen | ein großer Prüfer |
| E21 | Testwelten aus 0.5.2 und 0.5.0 voll, aus 0.4.0 verkleinert, mit Erwartungsdatei, geschrieben auf Wegwerf-Zweigen ab den Tags | echte alte Welten ohne Handarbeit; die Erwartungsdatei macht den Vergleich automatisch; wer von 0.4.0 direkt springt, durchläuft alle Umstiege auf einmal | eine Welt von Hand; nur 0.5.2 |
| E22 | Zwei echte Clients am dedizierten Server über zwei Worktrees desselben Commits | zwei Gradle-Aufrufe in einem Worktree sind verboten; so bleiben die Regeln aus `docs/testing.md` | nur ein simulierter zweiter Spieler |
| E23 | Alte Stände starten aus einem Wegwerf-Worktree des Tags mit dessen Gradle | gleiche Startwege wie heute, Port vorher gesetzt, kein eigener Launcher nötig | lose Jars mit eigenem Startskript |
| E24 | Echte Codes aus Discord nur lokal, ohne Namen | Datenschutz und öffentliches Repo | Codes als Testdaten einchecken |
| E25 | Offene Frage „Runden mit Flughilfe“ bleibt offen, 0.6.0 ändert nichts | keine Vorgabe dazu | eigene Festlegung |
| E26 | `allow_freecam` standardmäßig **aus**; im Einzelspieler und für Operatoren führt der ausgegraute Knopf direkt zur Regel | ein Update darf Survival-Servern keine Kamera durch Wände bescheren, ohne dass der Admin es weiß; im Einzelspieler ist es ein Klick | Standard an (Begründung mit der Server-Mod entfällt) |
| E27 | Explosions- und Windstöße kein Kernverhalten: `DRONE_KNOCKBACK` feuert immer, Stoß ins Flugmodell nur auf Anforderung eines Add-ons (Standard-Skala 0) | nicht beauftragt, Griefing-Weg auf Renn-Servern; Mob Defense bekommt trotzdem, was es braucht | Kernverhalten ohne Regel; neue Welt-Regel (Oberfläche, Texte, Tests ohne Auftrag) |
| E28 | Kein `breaks`/`incompatible` für JMP; Altmodus für JMP 1.1.x | ein Update nur von Propwash darf das Spiel nicht unstartbar machen; der Verzeichnis-Abzug macht PW3 auch ohne JMP-Tabelle exakt | Unverträglichkeit in den Metadaten |
| E29 | Honig nur auf den Tag-Weg umgezogen, Verhalten unverändert; Heuballen wie Wolle; Stolperdraht wie im PR | Honig war nicht beauftragt; Heuballen standen im angenommenen Vorschlag; Stolperdraht löst der PR über die Vanilla-Blockwirkung ebenfalls aus | neue Honig-Wirkung; Heuballen und Stolperdraht streichen |
| E30 | `addon-collision` besitzt `FpvEyeGuard` und Kamera-Mixin, Freecam nutzt `CameraClip` | eine Datei, ein Besitzer; Geist-Flug und Freecam brauchen dieselbe Kamera in Blöcken | zwei Haken in derselben Methode |
| E31 | Versionsbereiche `>=0.6.0-` und `[0.6.0-alpha,)`, im Release unverändert | nachgemessen mit beiden Loader-Bibliotheken; RC und Release haben dieselben Metadaten | `>= 0.6.0` (lehnt Vorabstände ab); Bereiche erst im Release-Commit setzen |
| E32 | Release aus dem Integrations-Worktree, `main` bewegt die Integration nicht | `main` ist der Arbeitsstand des Projektinhabers; ein Vorspulen durch die Integration könnte dort laufende Arbeit stören | `git merge --ff-only` im Hauptbaum |
| E33 | Discord-Bitte: alten Code stehen lassen, PW3-Code darunter; keine PW2-Ausgabe auf der Website | Spieler auf 0.5.x behalten lesbare Codes ohne neue Schreib-Logik | Beiträge ersetzen; PW2-Ausgabe auf der Website |
| E34 | Gate-Hinweis einmal je Welt und Spieler | Vorgabe „beim ersten Setzen“ mit „nicht wieder anzeigen“ | bis zu dreimal |
| E35 | Eigener Strang für den Landungs-Ausreißer vor dem ersten vollen Lauf | „genau PASS“ ist sonst Glückssache | Ausreißer mit Wiederholung hinnehmen |
| E36 | Motor-Hitze Phase B nur bei Befund aus Phase A | Vorgabe „genau prüfen“; größere Kopter kämpfen tatsächlich mit Hitze, ein Umbau ohne Befund wäre Selbstzweck | Umbau unabhängig vom Ergebnis |
| E37 | Bando-Lage in 0.6.0 beheben (nur neue Chunks) | für die nächste Version vorgemerkt; bestehende Welten bleiben unverändert | nur als Kleinigkeit führen |

---

## 11. Offen

- **Runden mit Flughilfe** (`gameplay.md` §9.2): keine Änderung in 0.6.0.
- **Genaue KV-Werte** der neuen Whoop-Motoren: Datenbeleg im Strang `jmp-whoop-motors`.
- **Server-Mod:** wird gesondert besprochen. Startet sie später, gelten dann die nächsten
  Nummern (API 1.8, Protokoll 19).
