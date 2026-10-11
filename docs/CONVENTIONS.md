# Code-Konventionen

Orientierung: Aufbau etablierter Multi-Loader-Mods (Sodium, Lithium, Iris, EntityCulling, ImmediatelyFast).

## Kommentare
- **Kein Kommentar im Mod-Code**: keine Javadoc-, Block- oder Zeilenkommentare in `common/src`, `fabric/src`, `neoforge/src` (auch Tests).
  Erklärungen gehören in `docs/`, Commit-Messages oder sprechende Namen.
- `python3 tools/strip_comments.py` entfernt übrig gebliebene Kommentare (Strings und Text-Blöcke bleiben unberührt).

## Pakete
- `de.lardtom.propwash.<feature>` — Logik je Feature (`build`, `sim`, `drone`, `race`, `content`, `server`, `network`, `registry`, `config`, `util`).
- `de.lardtom.propwash.<feature>.client` bzw. `de.lardtom.propwash.client.<feature>` — nur Client (Rendering, GUI, Eingabe, Video, Audio, OSD).
- `de.lardtom.propwash.api` — kleine, stabile öffentliche API für andere Mods (nur was wirklich extern gebraucht wird).
- `de.lardtom.propwash.mixin.<feature>` — **alle** Mixins zentral, nach Feature gegliedert; je Feature eine Mixin-Config `propwash.<feature>.mixins.json`.
- `de.lardtom.propwash.platform` — Service-Interfaces (ServiceLoader) für Loader-Unterschiede.
- `de.lardtom.propwash.fabric` / `de.lardtom.propwash.neoforge` — Loader-Glue in den Loader-Modulen.
- `de.lardtom.propwash.compat.<mod>` — optionale Integrationen (Iris, Mod Menu, Controlify), nur per Reflection/`compileOnly`.

## Namen
- Mixins `<Ziel>Mixin`, Accessor/Invoker `<Ziel>Accessor`, eigene Mixin-Member mit Präfix `propwash$`.
- Klassen, Methoden, Felder auf Englisch; Texte für Spieler nur über Sprachdateien (`de_de`, `en_us`, deutsche Texte mit Umlauten).
- Records für unveränderliche Daten, `final` wo möglich, keine Wildcard-Imports.

## Lizenz und öffentliches Repository
- Ab 0.5.0 wird der Quellcode öffentlich (Export ohne Historie mit `tools/public_export.py`). Alles, was ins Repository
  kommt, muss öffentlich sein dürfen: keine echten Namen, keine E-Mail-Adressen, keine lokalen Pfade (`/Users/…`),
  keine wörtlichen Zitate des Besitzers oder aus privaten Nachrichten – Anforderungen sinngemäß beschreiben. Der
  Export prüft das und bricht sonst ab.
- Lizenz: Kern unter PolyForm Shield 1.0.0 (`LICENSE`), MIT nur für die Pfade in `LICENSE-MIT` (API
  `de.lardtom.propwash.api`, Beispiel-Addon, Doku). Keine Lizenzköpfe in Quelldateien (Code ohne Kommentare). Was ins
  API-Paket wandert, wird MIT; Kern-Code nur dorthin verschieben, wenn er MIT werden darf.
- Fremder Code oder fremde Assets nur mit verträglicher Lizenz und Eintrag in `NOTICE`. Beiträge von außen nur mit
  Zustimmung zu `CLA.md` (Häkchen in der Pull-Request-Vorlage).
