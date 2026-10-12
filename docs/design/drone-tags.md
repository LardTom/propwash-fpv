# Drohnen-Tags für Server (drone, Propwash 0.5.0)

Stand 12.10.2026. Anforderung des Besitzers als Server-Admin, sinngemäß: Eine Drohne soll zeigen, wer sie platziert und
wer sie zuletzt geflogen hat, damit man auf Servern die Drohnen einzelner Spieler löschen kann, und ob sie gerade
geflogen wird.

Code in `de.lardtom.propwash.drone.tag` (reines Java: `DroneTagRules`; Minecraft: `DroneTags`), Aufrufe in
`QuadcopterItem#useOn` (Platzieren) und `QuadcopterEntity` (`setPilot`, `tickServer`, `readAdditionalSaveData`). Es sind
gewöhnliche Entity-Tags wie bei `/tag`: Sie leben nur auf dem Server, werden mit der Entity gespeichert (`Tags`) und nicht
an Clients geschickt – keine Änderung am Netzwerkprotokoll, an der API oder an den Sprachdateien.

## 1. Die Tags

Entity-ID für Selektoren: `propwash:drone`.

| Tag | gesetzt | entfernt |
|---|---|---|
| `pw_owner_<Name>` | Ein Spieler platziert die Drohne mit dem Drohnen-Item (auch im Kreativmodus und mit einem Item aus `/give`) | ersetzt beim nächsten Platzieren; Aufheben entfernt die Entity samt Tags |
| `pw_pilot_<Name>` | Der Server vergibt die Pilotenrolle (Start der Sitzung, FPV oder Sichtflug) | ersetzt, sobald ein anderer oder umbenannter Spieler fliegt; bleibt nach dem Aussteigen |
| `pw_flying` | solange eine Pilotensitzung läuft und der Pilot lebt – auch wenn die Drohne unscharf am Boden steht | Aussteigen, Funke weglegen, Reichweite, Abmelden, Tod des Piloten, Aufheben, `/kill`; nach jedem Laden |
| `pw_autopilot` | solange der Server die Drohne ohne Piloten fliegt (GPS Rescue ohne Pilot: Heimflug oder Notlandung, `docs/design/autonomous-rescue.md`) | Landung, Abbruch, Welt-Regel `propwash:autonomous_rescue` aus; nach jedem Laden (der nächste Tick setzt ihn wieder, wenn der Heimflug weiterläuft) |

`pw_flying` und `pw_autopilot` stehen nie zugleich an einer Drohne. „Niemand fliegt sie“ heißt als Selektor
`tag=!pw_flying,tag=!pw_autopilot`.

## 2. Namen

Der Name kommt aus dem Spielerprofil, in der Schreibweise des Profils: Selektoren vergleichen Tags genau, `pw_owner_steve`
findet keine Drohne von `Steve`. Echte Minecraft-Namen bestehen nur aus `A–Z`, `a–z`, `0–9` und `_` und passen so ohne
Anführungszeichen in `tag=…`. Andere Zeichen (nur auf Offline-Servern möglich) werden zu `_`
(`DroneTagRules.safeName`), damit jeder Tag selektorfest bleibt.

Ändert ein Spieler seinen Namen, bekommen Drohnen den neuen beim nächsten Platzieren bzw. Fliegen; ältere Drohnen
behalten den alten Namen, bis das passiert. Propwash entfernt dabei jeden älteren `pw_owner_…` bzw. `pw_pilot_…`, auch
von Hand gesetzte (`DroneTagRules.replace`).

## 3. Zustands-Tags sind ein Spiegel

`pw_flying` und `pw_autopilot` gleicht der Server mit dem Zustand der Drohne ab: in jedem Server-Tick der Drohne
(`QuadcopterEntity#tickServer`, zwei Abfragen im Tag-Set, nichts wird angelegt, solange sich nichts ändert), sofort beim
Wechsel des Piloten (`setPilot`) und am Ende von `readAdditionalSaveData`. Regeln in `DroneTagRules.flying` (Pilot
gesetzt und lebend) und `DroneTagRules.autopilot` (kein Pilot, der Server fliegt). Folgen:

- Nach einem Neustart oder Absturz des Servers bleibt nichts hängen. Der Pilot ist synchronisierter Zustand und wird nicht
  gespeichert, beim Laden fallen also beide Tags weg, auch wenn der letzte Autosave mitten im Flug lag. Ein gespeicherter
  Heimflug, der nach dem Laden weiterläuft, setzt `pw_autopilot` im ersten Tick wieder.
- Stirbt der Pilot, verschwindet `pw_flying` im nächsten Tick der Drohne, nicht erst nach der Gnadenfrist von einer
  Sekunde, in der das Failsafe greift (`docs/design/pilot-body.md`). Lebt er wieder, bevor die Sitzung endet, kommt der Tag
  zurück.
- `/tag … add pw_flying` oder `remove pw_flying` hält nur bis zum nächsten Tick. `/data merge entity` lädt die Entity neu;
  ein laufender Flug behält `pw_flying`.
- Wechselt eine Drohne die Dimension, lädt Minecraft sie als neue Entity aus den gespeicherten Daten; die Sitzung endet
  dort ohnehin (Pilot und Drohne in verschiedenen Welten).

`pw_owner_…` und `pw_pilot_…` sind Angaben, kein Spiegel: Admins dürfen sie mit `/tag` ändern, Propwash ersetzt sie erst
beim nächsten Platzieren bzw. Flugstart.

## 4. Entscheidungen

- **Besitzer ist, wer platziert.** Jede Platzierung setzt ihn neu. `/summon propwash:drone` hat keinen Spieler und damit
  keinen Besitzer; wer einen will, gibt ihn mit: `/summon propwash:drone ~ ~ ~ {Tags:["pw_owner_Steve"]}`. Platziert ein
  Block oder eine Maschine eines anderen Mods mit einem Ersatzspieler, steht dessen Name im Tag.
- **Aufheben und neu Platzieren** ergibt eine neue Entity mit derselben UUID: Besitzer ist, wer sie absetzt, ein
  `pw_pilot_…` fehlt bis zum nächsten Flug. Verworfen: den Piloten im Item mitführen – eine eigene Komponente hieße ein
  neuer Registereintrag im Beitrittsabgleich, `minecraft:custom_data` würde mit Daten von Mapmakern kollidieren. Die
  Empfänger-Bindung (`docs/design/receiver-binding.md`) wandert weiter mit dem Item.
- **Binden setzt keinen Pilot-Tag.** Pilot ist nur, wer wirklich fliegt; Binden ohne Flug ändert nichts.
- **`pw_flying` heißt Sitzung, nicht „scharf“.** Eine Drohne, deren Pilot gerade unscharf am Boden steht, ist in Benutzung;
  `/kill @e[…,tag=!pw_flying]` soll sie nicht treffen. Verworfen: zusätzlich `pw_armed` – wechselt in jedem Flug oft und
  hilft beim Aufräumen nicht.
- **Eigener Tag für den Autopiloten** statt ihn unter `pw_flying` zu zählen: Der Pilot hat die Drohne verlassen, löschen
  würde aber einen laufenden Heimflug abbrechen. Mit zwei Tags entscheidet der Admin.
- **Kein Eintrag in der öffentlichen API.** Die Tag-Namen sind ein Vertrag mit Server-Admins (dokumentiert, stabil), Add-ons
  lesen sie wie jeden Entity-Tag. Eine API-Konstante hätte `API_VERSION` angehoben, ohne etwas zu können, was
  `Entity#entityTags()` nicht kann.

## 5. Beispiele für Server-Admins

```
/kill @e[type=propwash:drone,tag=pw_owner_Steve]
/kill @e[type=propwash:drone,tag=!pw_flying,tag=!pw_autopilot]
/kill @e[type=propwash:drone,tag=pw_owner_Steve,tag=!pw_flying,tag=!pw_autopilot]
/tag @e[type=propwash:drone,limit=1,sort=nearest] list
/execute as @e[type=propwash:drone,tag=pw_flying] run say Ich werde geflogen
/tag @e[type=propwash:drone,tag=pw_owner_Steve] remove pw_owner_Steve
/tag @e[type=propwash:drone,limit=1,sort=nearest] add pw_owner_Alex
```

- `@e` findet nur Drohnen in geladenen Chunks.
- `/kill` löscht die Drohne, ohne sie als Item fallen zu lassen; Fracht im Greifer fällt heraus. Fliegt sie jemand, endet
  seine Sitzung.
- Aufheben mit Schleichen + Rechtsklick gibt die Drohne als Item zurück, wie bisher.

## 6. Tests

- JUnit `DroneTagRulesTest`: Tag-Namen, Schreibweise, Ersatzzeichen, Ersetzen alter Besitzer- und Pilot-Tags ohne fremde
  Tags anzufassen, Umbenennen, Regeln für `pw_flying`/`pw_autopilot`, Laden nach einem Absturz.
- Server-Rauchtest (`:fabric:runServerSmoke`, `:neoforge:runServerSmoke`, `dev.ServerSmoke` → `drone.DroneTagChecks`) auf
  dem dedizierten Server mit zwei Ersatzspielern: Platzieren → nur `pw_owner_…`; Fliegen → `pw_pilot_…` und `pw_flying`;
  Tod des Piloten → `pw_flying` im nächsten Tick weg, Sitzung noch in der Gnadenfrist, wieder lebend → zurück;
  Aussteigen → `pw_flying` weg, Pilot bleibt; anderer Pilot → Pilot-Tag ersetzt; Speichern im Flug (der Speicherstand
  enthält `pw_flying`) und Laden wie beim Chunk-Laden (`EntityType.loadEntityRecursive`) → ohne `pw_flying`; Speicherstand
  mit `pw_flying`, `pw_autopilot` und einem fremden Tag → nur der fremde und die Angaben bleiben; `/tag add pw_flying` auf
  geparkter Drohne → nach einem Tick weg; Umbenennen → neuer Pilot-Tag; Aufheben und Platzieren durch den umbenannten
  Spieler → gleiche UUID, nur sein Besitzer-Tag.
- Selbsttest `pilotenkörper:` (beide Loader) im echten Flug: beim Schweben `pw_flying` und Pilot-Tag, nach dem Tod kein
  `pw_flying`, beim Failsafe-Heimflug `pw_autopilot`, bei „Absinken“ keiner.
