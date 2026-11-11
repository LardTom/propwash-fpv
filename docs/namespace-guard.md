# Namensraum-Prüfung, Hinweis und Absturzbericht

Für Spieler, Server-Betreiber und Add-on-Autoren. Der Namensraum `propwash` gehört allein Propwash. Propwash 0.6.0
prüft beim Start und beim Neuladen, ob ein anderer Mod ihn trotzdem benutzt, nennt den Mod im Log und einmal je
Spielstart im Chat und stürzt dabei nie ab. Ressourcen- und Datenpakete der Spieler dürfen Propwash-Dateien weiter
überschreiben. Regeln für Add-ons: [`../ADDONS.md`](../ADDONS.md).

Technik und Entscheidungen: [`design/0.6.0/addon-api-namespace.md`](design/0.6.0/addon-api-namespace.md) §3.14–§3.16.

## 1. Was geprüft wird

| Kategorie | Fund | erlaubt |
|---|---|---|
| Registry-Ids | Eintrag `propwash:…` in einem eingebauten Register, angemeldet von einem anderen Mod | – |
| Übersetzungsschlüssel | Schlüssel mit dem Segment `propwash` (`propwash.…`, `key.propwash.…`, `item.propwash.…`, `death.attack.propwash.…`) in den Sprachdateien eines anderen Mods (Englisch und aktive Sprache), den Propwash selbst nicht kennt | bekannter Schlüssel (Übersetzung überschreiben); Dateien unter `assets/propwash/lang/` (Übersetzungs-Mods) |
| Tastenbelegungen | `KeyMapping` mit Namen `key.propwash.…`, die nicht von Propwash stammt | – |
| Netzwerk-Pakete | Kanal `propwash:…`, den nicht Propwash anmeldet | – |
| Enum-Werte | zusätzliche Konstante, die ein Mixin an ein Enum von Propwash hängt | – |
| Datendateien | neue Datei unter `data/propwash/…` in einem anderen Mod | Pfad, den Propwash selbst mitbringt (Überschreiben, Tag-Ergänzung wie bei Just More Parts) |
| Assets | neue Datei unter `assets/propwash/…` in einem anderen Mod | Pfad, den Propwash selbst mitbringt (etwa eine Textur ersetzen) |
| Befehle | Unterbefehl von `/propwash`, den nicht Propwash anlegt | reservierte Unterbefehle offizieller Add-ons (Liste `guard.OfficialAddons`, in 0.6.0 leer) |
| Abgelehnte Anmeldungen | Anmeldung über die API mit einer `propwash:`-Id (neue Punkte werfen eine Ausnahme, alte Punkte verweigern still) | – |
| Mixins in Interna | nur Info, kein Fund: Mods mit Mixins in Klassen von Propwash und die Zahl der Ziel-Klassen | erlaubt, auf eigenes Risiko |

- **Wann:** beim Start (Register, Enum-Werte, Datendateien), beim Laden und Neuladen der Client-Ressourcen (`F3+T`,
  Sprachwechsel: Übersetzungen, Tastenbelegungen, Assets, Netzwerk-Pakete des Clients), beim Serverstart und nach
  `/reload` (Befehle, Netzwerk-Pakete des Servers, Datenpakete).
- **Spielerpakete:** Ressourcen- und Datenpakete aus `resourcepacks/`, `datapacks/` oder vom Server erzeugen keine
  Funde; neue Dateien daraus stehen nur als Info im Log, eine Zeile je Paket und Start, etwa
  `Namespace check: pack 'file/v3info.zip' adds 1 new files in the namespace propwash (resource and data packs are allowed): assets/propwash/textures/item/v3info_new.png`.
  Die Prüfung liest Zip- und Ordner-Pakete Ordner für Ordner: die Standard-Ordner von Minecraft 26.3 (`textures`,
  `models`, `recipe`, `tags`, `function`, `worldgen` …) und die Ordner, die Propwash selbst mitbringt (etwa
  `propwash_parts`). Dateien direkt in `assets/propwash/` oder `data/propwash/` zählen nicht.
- **Kein Absturz:** Jede Kategorie läuft für sich. Scheitert die Prüfung selbst, steht eine Warnung im Log
  (`Namespace check: … skipped`) und nur diese Kategorie fällt weg.
- **Kosten:** Jeder Lauf schreibt seine Zeit ins Log, etwa `Namespace check (resources): 0 findings in 17.8 ms`. Mit
  Just More Parts 1.1.1 dauern Start, Ressourcen und Serverstart zusammen etwa 30 ms.

## 2. Was Spieler sehen

- **Log:** je Mod und Kategorie eine Fehlerzeile mit höchstens 20 Ids, der Rest gezählt:
  `Mod 'nsprobe' uses the reserved namespace propwash (registry ids, 1): propwash:nsprobe_item`. Jede Zeile kommt
  einmal je Start; neue Funde nach einem Neuladen gehen nur ins Log.
- **Chat:** beim ersten Betreten einer Welt nach dem Spielstart genau eine Zeile, etwa „Namespace Probe nutzt den
  reservierten Namensraum propwash – das kann mit dem nächsten Update kaputtgehen.“ mit dem Knopf **[Details]**.
  Bei mehreren Mods steht die Zahl der Add-ons da. Ein zweiter Weltbeitritt und `F3+T` zeigen keinen zweiten Hinweis.
- **Details:** Fenster „Reservierter Namensraum“ im Propwash-Theme: je Mod die Kategorien mit allen Ids, darunter die
  Mixins in Interna. **Kopieren** legt die Liste als englischen Text in die Zwischenablage (für den Autor des Add-ons
  oder einen Fehlerbericht), **Regeln für Add-ons** öffnet die Website, **Schließen** führt zurück. Das Fenster scrollt;
  die drei Knöpfe bleiben auch bei GUI 320×240 sichtbar.
- **Dedizierter Server:** Jeder Operator bekommt einmal je Serverstart beim Beitritt eine Zeile „Server: … nutzen den
  reservierten Namensraum propwash (n Funde, siehe Server-Log).“

## 3. Absturzbericht

Jeder Crash-Report enthält den Abschnitt `Propwash add-ons` (Fabric: im Systembericht, NeoForge: als
Crash-Callable):

```text
Propwash add-ons:
		Add-ons:
			Propwash: Just More Parts (justmoreparts) 1.1.1
		Mixins into Propwash internals: none
		Namespace check: no findings
```

Damit beantwortet schon der Bericht die Frage der Issue-Vorlage, welche Add-ons installiert sind und wer in Interna
eingreift.

## 4. Just More Parts 1.1.x (gesperrt)

Propwash 0.6.0 verlangt Just More Parts 1.2.0 (Owner-Entscheidung 21.10.2026, der frühere Altmodus mit Hinweis je
Spielstart ist entfallen). Die Sperre steht in den Metadaten: Fabric `"breaks": {"justmoreparts": "<1.2.0-"}`,
NeoForge eine Abhängigkeit `incompatible` mit `versionRange = "(,1.2.0-alpha)"` und dem Grund „Just More Parts 1.1.x
does not work with Propwash 0.6.0 – please update to Just More Parts 1.2.0. (Just More Parts 1.1.x passt nicht zu
Propwash 0.6.0 – bitte auf 1.2.0 aktualisieren.)“. Vorabstände von 1.2.0 (`1.2.0-dev.N`, `1.2.0-rc.N`) laden. Mit
Just More Parts 1.1.x bricht der Start ab, bevor die Namensraum-Prüfung läuft; Just More Parts 1.2.0 erzeugt 0 Funde.

## 5. Probe-Mod für Tests

Das Quell-Set `common/src/nsprobe` ist ein eigener Mod (`nsprobe`, Namespace Probe) und verletzt den Namensraum mit
Absicht in jeder Kategorie. Es liegt nur in Entwicklungsläufen mit `-PpropwashNsProbe=true` im Spiel, nie im
Release-Jar:

```sh
./gradlew :fabric:runClientSelftest '-PpropwashSelftestOnly=guard:' -PpropwashNsProbe=true
```

Die Gruppe `guard:` erwartet ohne Probe 0 Funde und mit Probe genau diese Ids (alle vom Mod `nsprobe`):
`propwash:nsprobe_item`, `key.propwash.nsprobe`, `propwash.nsprobe.text`, die Tastenbelegung `key.propwash.nsprobe`,
`propwash:nsprobe_ping`, `input.SwitchFunction.NSPROBE`, `osd.OsdElement.NSPROBE`, `data/propwash/recipe/nsprobe.json`,
`assets/propwash/textures/item/nsprobe.png`, `/propwash nsprobe`, `propwash:nsprobe (config tab)` und
`propwash:probe (hud element)`. Die Ergänzung von `data/propwash/tags/item/parts/frame.json` und die ersetzte Textur
`assets/propwash/textures/item/carbon_plate.png` dürfen keine Funde sein. Bildschirmfotos (nur Fabric, mit
`-PpropwashScreenshots=true`): `guard_hint_{en,de}`, `guard_report_{en,de}_{854x480,320x240}`.

## 6. Grenzen

- Technisch reserviert ist nur der exakte Namensraum `propwash`; `propwash_example` oder `propwash_addon` sind eigene
  Namensräume.
- NBT-Schlüssel und Konfigurationsdateien fremder Mods prüft Propwash nicht; das regelt nur `ADDONS.md`.
- Die Zuordnung zum Mod kommt aus dem Plattform-Dienst `ModOrigins` (Klassen, Pakete, Mixin-Configs). Lässt sich ein
  Fund keinem Mod zuordnen, heißt der Mod „unknown mod“.
- Abgelehnte Anmeldungen aus Propwashs eigenem Code (etwa die absichtlichen Fehlversuche der API-Selbsttests mit dem
  Kreativ-Tab `propwash` oder dem Preset `propwash:fremd`) zählen als Anmeldungen von Propwash und sind keine Funde.
  Maßgeblich ist der direkte Aufrufer der API (`de.lardtom.propwash.api.**` und die Methoden, mit denen Propwash deren
  Schnittstellen umsetzt): Er muss Propwash-Code sein, und weiter unten im Stack darf kein Rahmen eines anderen Mods
  stehen; sonst bleibt es ein Fund (ohne eindeutigen Aufrufer als „unknown mod“). Die Rahmen der API-Umsetzung selbst
  machen eine Anmeldung nie zu einer von Propwash: Meldet Code ohne bekannte Herkunft, Mixin-Code eines anderen Mods in
  einer Minecraft-Klasse oder Code an der API vorbei (etwa direkt über `NamespaceRules` oder `ExtensionTabs`) etwas im
  Namensraum `propwash` an, ist das ein Fund von „unknown mod“. Gegenprobe im Selbsttest:
  `'-PpropwashSelftestOnly=api: Presets,api: eigener Kreativ-Tab,guard:'` muss wie der volle Lauf 0 Funde ergeben.
- Funde ändern nichts am Spiel: Anmeldungen bleiben, wie die API sie behandelt hat; Mods werden nicht abgeschaltet.

## 7. Englische Kernsätze (Website)

- The namespace `propwash` is reserved for Propwash. Add-ons use their own mod id.
- On start and on every resource reload, Propwash checks registry ids, translation keys, key mappings, network
  payloads, enum values, data files, assets and `/propwash` subcommands of all other mods.
- Findings never crash the game: they are logged once per start, and the first time you join a world you get one chat
  line with a Details button.
- Resource packs and data packs may still override Propwash files; they never count as findings.
- On a dedicated server, every operator gets one line per server start that points to the server log.
- Every crash report has a "Propwash add-ons" section with installed add-ons, mods with mixins into Propwash internals
  and the findings of the check.
- Propwash 0.6.0 needs Just More Parts 1.2.0; with Just More Parts 1.1.x the game does not start and the loader names the reason.
