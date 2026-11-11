# Persönliche Regeln: Freigabestufen, eigene Physik je Spieler, Rennstandard = Serverregeln

Für Spieler, Server-Admins und Entwickler. Stand 0.6.0-rc.3, Teil 1 (Kern) und Teil 2 (Oberfläche, §8). Bis rc.2
machte jede Welt-Regel, die vom Rennstandard abwich, eine Runde zur Runde „mit Flughilfe“, und Spieler konnten ihre
Physik nicht selbst wählen. Ab rc.3 gibt der Admin je Physikregel frei, wer sie ändern darf, jeder Spieler kann
freigegebene Regeln für sich abweichend setzen, und der Rennstandard sind die Serverregeln selbst. §1–7 beschreiben
Daten, Pakete und Wertung, §8 die Bildschirme (Schalter „Freigabe“, Tab „Meine Regeln“, Presets, Vorabwarnung,
Abzeichen „Serverregeln“).

Grundlagen: [`race-rules.md`](race-rules.md) (rennrelevant/neutral, Regel-Satz je Zeit), [`zones-open.md`](zones-open.md)
(Häkchen „Zonen“ je Regel), [`api/race-data.md`](api/race-data.md) (Renndaten).

## 1. Freigabestufe je Physikregel

Jede der 11 Physikregeln (`PhysicsRules.SPECS`: Schwerkraft, Propwash, Bodeneffekt, Hardware-Grenzen, Wind, Turbulenz,
Akkulaufzeit, Spannungseinbruch, Crash-Toleranz, Disarm on crash, Prop-Treffer) hat eine Stufe:

| Stufe | Schlüssel | Wirkung |
|---|---|---|
| Fest | `fixed` | Nur der Serverwert gilt. Zonen-Überschreibungen dieser Regel sind wirkungslos (bleiben in der Zone gespeichert), persönliche Abweichungen ebenso. |
| Zonen | `zones` | Zonen dürfen die Regel ändern (Verhalten bis rc.2). Standard. |
| Spieler | `players` | Zonen und jeder Spieler für sich dürfen sie ändern („Spieler schließt Zonen ein“). |

- Alle anderen Welt-Regeln sind immer „Fest“ (`RuleTiers#tier` liefert für sie `FIXED`).
- **Eine Quelle für „Fest“:** Die Stufe Fest ist das vorhandene Häkchen „Zonen“ aus zones-open (`ZoneRelease`, in
  `data/propwash/zones.dat`). Zusätzlich speichert Propwash nur, welche Regeln auf „Spieler“ stehen
  (`data/propwash/personal_rules.dat`, Feld `player_rules`). Fest gewinnt: Wird eine Regel auf Stufe Spieler über das
  Häkchen für Zonen gesperrt, steht sie auf Fest; gibt der Admin sie wieder frei, steht sie wieder auf Spieler.
- **Migration:** Alte Welten haben keine `personal_rules.dat`; jede Regel steht damit auf Zonen, eine über das Häkchen
  gesperrte Regel auf Fest – das bisherige Verhalten. Nichts steht auf Spieler.
- **Ändern** darf die Stufe, wer `/gamerule` darf (`WorldRules#mayEdit`, `RuleAccess`), im Einzelspieler der Host:
  Paket `propwash:rule_tier` (`RuleTierC2S`, Regel und Stufe); ohne Recht lehnt der Server ab (Meldung „Du darfst nicht
  festlegen, wer die Welt-Regeln ändern darf.“, höchstens zweimal in 5 s). Für Konsolen:
  `/propwash rules tier` listet die Stufen, `/propwash rules tier <regel> <fixed|zones|players>` setzt eine (Rechte-Stufe
  2). Jede Änderung meldet der Server im Chat und schickt sie allen Spielern.

## 2. Persönliche Abweichungen

- Der Server speichert je Spieler und Welt nur **Abweichungen** (`PersonalRules`: Regelname → Wert, in
  `personal_rules.dat` unter `players`, höchstens 4096 Spieler). Kein Eintrag heißt: der Spieler folgt dynamisch dem
  Serverwert, auch wenn der Admin ihn später ändert.
- Paket `propwash:personal_rules_edit` (`PersonalRulesC2S`, höchstens 64 Einträge) mit drei Arten
  (`PersonalRuleEdit.Op`):
  - `SET` – setzt die genannten Regeln. Nur für Regeln auf Stufe Spieler und gültige Werte; ein Wert gleich dem
    Serverwert löscht die Abweichung (nur Deltas).
  - `RESET` – löscht die genannten Abweichungen (für jede Physikregel erlaubt, auch gesperrte).
  - `APPLY` – setzt alle freigegebenen Regeln auf die mitgeschickten Werte und löscht freigegebene Abweichungen ohne
    Wert; gesperrte Abweichungen bleiben gespeichert. „Race Legal“ ist `APPLY` mit leerer Liste.
  Abgelehnte Einträge (nicht freigegeben, ungültig, keine Physikregel) nennt der Server im Chat („Diese persönlichen
  Regeln hat der Server nicht übernommen …“) und im Log; der Rest wird übernommen. Dem Client wird nie vertraut.
- **Freigabe entzogen:** Die Abweichung bleibt gespeichert, wirkt aber nicht (`PersonalRules#locked`); bei erneuter
  Freigabe wirkt sie wieder.
- Sync: `propwash:personal_rules` (`PersonalRulesS2C`) an jeden Spieler mit Recht-Flag, allen Stufen und den eigenen
  Abweichungen; beim Beitritt, nach jeder Änderung und spätestens jede Sekunde, wenn sich etwas geändert hat (etwa das
  Häkchen „Zonen“). Beim Client `server.client.ClientPersonalRules` (Stufen, eigene Werte, wirksame Werte,
  `deviations`/`assisting`/`locked` und `set`/`reset`/`apply`/`setTier` für den Bildschirm aus Teil 2).
- Zonen sind **nicht** pro Spieler abschaltbar; es gibt keinen „Zonen ignorieren“-Schalter.

## 3. Auflösung Server → Spieler → Zone

`RuleBase` (Serverregeln, persönliche Regeln, Stufen) ersetzt die bloßen Serverregeln überall, wo Physik je Drohne
aufgelöst wird (`ZoneResolver#resolve`, `ZoneField#update`, `ZoneBlend`):

- Ohne Zone: Serverwert, überschrieben durch die persönliche Abweichung (nur Stufe Spieler).
- In einer Zone gewinnt die Zone für jede Regel, die sie setzt. Modus „Welt“ = Serverwert (die persönliche Abweichung
  gilt dort nicht), Modus „Darunter“ = Zone darunter, sonst der persönliche Wert.
- Stufe Fest: Zonen und Spieler werden für die Regel übergangen.
- Übergänge von Schwerkraft und Wind blenden vom persönlichen Wert aus; eine Zone im Modus Welt blendet zum Serverwert.
- Pfade: eigener Flug (`PilotSession` über `ZonePhysics`), Freecam, Rescue-Rückflug und autonome Flüge des Besitzers am
  Server (`ServerFlight`, der Besitzer bzw. Pilot; der Heimflug ohne Pilot nimmt den Besitzer des Heimflugplans,
  `ServerReturnFlight#start` mit `rulesOf(plan.owner())`, Add-on-Flüge den Besitzer des Flugs), Rennwertung am Server
  (`RaceServer`, je Pilot), Flughilfe-Prüfung, Flugschule und Tricks.

## 4. Assisted – neue Bedeutung

- **Rennstandard = Serverregeln** plus renntaugliche Zonen an der jeweiligen Stelle. Die Serverregeln selbst machen
  eine Runde nie mehr assisted, auch wenn sie vom Propwash-Standard abweichen.
- Assisted ist jede Runde, in der der wirksame Wert einer rennrelevanten Regel vom Rennstandard an dieser Stelle
  abweicht. Verglichen werden **Werte**: Hat ein Spieler persönlich denselben Wert wie der Server, zählt das als
  Serverwert. Neutrale Regeln (etwa „Disarm on crash“) zählen auch persönlich nie.
- Neuer Grund `LapAssist.PERSONAL_RULES` (Schlüssel `personal_rules`, Bit 9, am Ende des Enums – gespeicherte Masken
  verschieben sich nicht; Server-Grund, nie vom Client übernommen). `AssistDetail.personal(regel, persönlicher Wert,
  Serverwert)`; Text „persönliche Regeln weichen von den Serverregeln ab: Wind Aus statt Mild“. Gruppe Physik in der
  Flughilfe-Prüfung.
- Nicht renntaugliche Zonen bleiben `ZONE_PHYSICS`, verglichen mit dem Serverwert.
- Verglichen wird im Moment: Der Server hält bei jeder Bewegung neben dem Regel-Satz fest, welche Werte in diesem
  Augenblick von den Serverregeln abweichen (`RuleTimeline#observe` mit `RaceStandards.server`), und die Runde sammelt
  diese Urteile (`RuleTimeline#personalBetween`, `AssistCheck#serverLapVerdict`). Stellt der Server während einer Runde
  eine Regel um, bleibt eine Runde ohne persönliche Abweichung rennfrei; wer persönlich abwich, solange der Server einen
  anderen Wert hatte, fliegt mit Flughilfe, auch wenn der Server später seinen Wert übernimmt.
- `WORLD_PHYSICS` setzt Propwash für neue Runden nicht mehr aus Serverregeln (nur noch die Client-Meldung „Physik am
  Client verändert“ und der Überlauf des Regel-Satzes).
- **Neue Runden** tragen die Serverregeln beim Fliegen (`Leaderboard.Entry#server`, gespeichert als `server_rules`,
  ebenso für Rennen). Ihr Urteil steht beim Fliegen fest (Gründe im gespeicherten Eintrag) und ändert sich nicht, wenn
  der Server später andere Regeln setzt. **Alte Runden** (ohne `server_rules`) werten unverändert gegen den
  gespeicherten Rennstandard aus [`race-rules.md`](race-rules.md) §2; ihre Kennzeichnung bleibt.
- Flugschule und Sterne (`AssistText#evaluate`) und die Vorabwarnung (`AssistText#preflight`) nutzen dieselbe Logik,
  ebenso Tricks (`TrickTracker`: persönliche Abweichungen in rennrelevanten Regeln zählen wie Rescue als Flughilfe).

Beispiel (Owner): Server 100 % Akku, Akku auf Stufe Spieler. Ein Spieler fliegt mit unendlich Akku
(`battery_scale` 0) → Runde mit Flughilfe „persönliche Regeln: Akkulaufzeit unendlich statt 100 %“; mit 100 % zählt
sie normal; stellt der Server selbst 0 ein, zählt sein persönlicher Wert 0 als Serverwert.

## 5. Regelsatz für das Abzeichen „Serverregeln“

Die Serverregeln beim Fliegen (Abweichungen vom Propwash-Standard, nur rennrelevante Regeln) stehen an:

- jeder neuen Runde und jedem Rennen (`server_rules` in `lap_times`/`race_times`),
- jedem Geist (`GhostTrack#rules`, Geister-Format 3; ohne Regelsatz schreibt Propwash weiter Format 2 – auch geteilte
  und importierte Geister und Replays tragen ihn so mit),
- jeder Zeile der Bestenliste beim Client (`RacePayloads.BoardRow#server`),
- der API: `BoardEntry#serverRules()` (`Optional`, leer bei alten Zeiten; `serverRulesDiffer()`),
  `PropwashApi.race().serverRules(server)` → `RaceStandardInfo` der aktuellen Serverregeln (`defaults()` = kein
  Abzeichen),
- dem JSON-Spiegel: `serverRules` in `courses.json` und jeder Bestenlisten-Datei (wie `raceStandard`), `serverRules`
  je Eintrag (`{"propwash:wind": 0}`, `null` bei alten Zeiten).

Abzeichen „Serverregeln“: Bestenliste, wenn `serverRules(server)` nicht `defaults()` ist; Eintrag bzw. Geist, wenn sein
Regelsatz von den aktuellen Serverregeln abweicht (Anzeige §8.5).

## 6. Technik

| Datei | Inhalt |
|---|---|
| `server/rules/RuleTier`, `RuleTiers` | Stufe je Regel, abgeleitet aus Zonen-Freigabe und Spieler-Regeln |
| `server/rules/PersonalRules`, `PersonalRuleEdit` | Abweichungen je Spieler, Prüfung von SET/RESET/APPLY |
| `server/rules/RuleBase` | Grundlage der Auflösung (Server, persönlich, Stufen) |
| `server/PersonalRulesServer` | SavedData `personal_rules`, Pakete, Sync, Befehl `/propwash rules tier` |
| `server/PersonalRulesS2C`, `PersonalRulesC2S`, `RuleTierC2S` | Pakete |
| `server/client/ClientPersonalRules` | Stand beim Client, Pakete zum Ändern |
| `zone/ZoneResolver`, `ZoneResolution`, `ZoneBlend`, `ZoneField` | Auflösung mit `RuleBase`, `personalDetails` |
| `race/LapAssist`, `AssistDetail`, `AssistCheck`, `RaceRuleSet`, `AssistWords` | `PERSONAL_RULES`, `judge`, `serverLapVerdict`, `personalMismatches` |
| `race/Leaderboard`, `CourseRegistry`, `RaceServer` | Serverregeln je Zeit, Wertung neuer und alter Zeiten |
| `race/GhostTrack`, `GhostCodec`, `RacePayloads`, `race/export/*`, `api/race/*` | Regelsatz in Geistern, Zeilen, API, JSON |
| `server/rules/PersonalPresets`, `PersonalRulesDraft` | Presets (Race Legal fest, eigene), Entwurf des Tabs „Meine Regeln“ mit Status (Teil 2) |
| `race/ServerRulesBadge`, `race/client/ServerRulesText` | Abweichungen für das Abzeichen „Serverregeln“, Texte und Zeichnen (Teil 2) |
| `server/client/RuleTierSwitch`, `WorldRulesTab` | Schalter „Freigabe“ je Physikregel (Teil 2) |
| `server/client/MyRulesTab`, `PersonalPresetStore` | Tab „Meine Regeln“, Presets in `config/propwash/personal_presets.json` (Teil 2) |
| `race/client/AssistPreflight`, `RaceConfigTab`, `LeaderboardScreen`, `ReplayScreen` | „Race Legal anwenden“, Abzeichen (Teil 2) |

## 7. Tests

- JUnit `race.PersonalRulesTest` (15 Fälle): Auflösung Server → Spieler → Zone mit Modus Welt und Darunter, Stufe Fest
  sperrt Zonen und Spieler, Freigabe entziehen und wiedergeben, Übergänge, Serverregeln nie assisted, Vergleich nach
  Werten (Server stellt später denselben Wert ein → nicht assisted), neutrale Regeln und renntaugliche Zonen, Enum-Bits
  stabil, Paketprüfung (nicht freigegeben, außerhalb des Bereichs, keine Physikregel, Race Legal, Preset), Migration
  (alte Welten → Zonen, gesperrte Regel → Fest), Regelsatz an neuen und alten Runden (Codec), Rundenurteil am Server,
  Urteil im Moment (Server stellt Wind in der Runde um: ohne Abweichung rennfrei, persönlicher Wind 0 bei Server-Wind 1
  mit Flughilfe, auch wenn der Server danach Wind 0 stellt), zusammengelegte Abschnitte behalten ihr Urteil, Gründe aus
  der Server-Zeitleiste prüfen nur ihre eigene Regel. `race.BoardRowWireTest`: eine Netzprotokollnummer, ein Format der
  Zeilen der Bestenliste (17 bis rc.2, 18 ab rc.3 mit Serverregeln).
- Selbsttest `'-PpropwashSelftestOnly=race-rules:'`: Schritt „Rennstandard = Serverregeln …“ (Server-Wind 0 rennfrei mit
  Regelsatz und API, persönlicher Wind 0 bei Server-Wind 1 mit Flughilfe „persönliche Regeln“, persönlicher Wind 0 bei
  Server-Wind 0 rennfrei, Vorabwarnung beim Client) und „Freigabestufe Fest …“ (Fest sperrt Zonen-Häkchen und
  Abweichung, beides beim Client; wieder Spieler wirkt; Befehl). Die Gruppen `flughilfe` nutzen persönliche Regeln statt
  Serverregeln für veränderte Physik, ebenso das Bild „Rennen im FPV mit Flughilfe“ (wartet beim Client auf die
  persönliche Schwerkraft 130 %). „drone: Welt-Regel Schwerkraft wirkt im Flug“ prüft am Server, dass die Drohne des
  Piloten mit der Welt-Regel 130 % rechnet (12,753 m/s²) und dass Serverregeln weder als Abweichung noch als Flughilfe
  zählen. Dazu (`race.client.PersonalRulesFlightSelfTests`): „Server stellt Wind während der
  Runde um …“ (echte Runde am Server mit Wechsel zur Rundenmitte) und „Heimflug ohne Pilot fliegt mit den persönlichen
  Regeln …“ (der Besitzer der Drohne hat Schwerkraft 50 %, der Server 100 %).
- Beitrittstest `PROPWASH_JOINTEST_RACE_RULES=1` ([`testing.md`](testing.md)): zwei Spieler mit je eigenen Werten –
  der echte Client mit unendlich Akku, ein zweiter Spieler am Server (`PwPersonalB`, eingebettete Verbindung wie beim
  Zuschauerzahl-Test) mit Wind 0 –, beide Runden mit genau ihrem Grund, danach gleiche Werte wie der Server rennfrei.
- Beitrittstest mit zwei echten Clients `PROPWASH_JOINTEST_RACE_RULES=2` (`PwPersonalA`, `PwPersonalB`): jeder Client
  setzt seinen Wert selbst (A unendlich Akku, B Wind 0), bekommt nur seinen eigenen zurück, fliegt am Client mit ihm
  und fliegt eine echte Runde; Server, Client und die Bestenliste beider Clients nennen je Runde genau den Grund ihres
  Spielers.
- JUnit `race.PersonalRulesUiTest` (8 Fälle, Teil 2): Race Legal fest, leer, nicht umbenennbar und nicht löschbar (auch
  nicht als gespeicherter Name), eigene Presets merken, aktualisieren, umbenennen (belegt, leer), löschen, höchstens 32;
  gespeicherte Presets behalten nur Physikregeln mit gültigem Wert; Anwenden überspringt nicht freigegebene Regeln;
  Entwurf zeigt Serverwerte, ändert nur freigegebene Regeln, Zurücksetzen folgt dem Server, Status Race Legal, neutral
  oder Flughilfe, gesperrte Abweichung nur lesbar und nach erneuter Freigabe wieder wirksam; Abzeichen für Bestenliste
  (Abweichung vom Propwash-Standard) und Zeit bzw. Geist (Abweichung von den Serverregeln heute, alte Zeiten ohne
  Regelsatz ohne Abzeichen).
- Selbsttest `race-rules:` (Teil 2, `race.client.PersonalRulesUiSelfTests`): Schalter „Freigabe“ (11 Schalter, nur an
  Physikregeln, Entwurf bis Speichern, Server und Client), Tab „Meine Regeln“ (Referenz Serverwert, gesperrte Regel nur
  lesbar, Speichern schickt nur Abweichungen, Zurücksetzen folgt dem Server, Status), Presets (Race Legal, merken,
  umbenennen, löschen, Wind beim Anwenden übersprungen mit Hinweis), Vorabwarnung mit Text „Schwerkraft 130 % statt
  100 %“ und Knopf „Race Legal anwenden“, Abzeichen an Kopfzeile und Zeilen der Bestenliste; Bildschirmfotos
  `personal_rules_*` englisch und deutsch. ui-scale-Fall „Konfigurator mit persönlichen Regeln“
  (`race.client.PersonalRulesUiScaleCase`): Welt-Regeln, Meine Regeln und Flughilfe-Prüfung mit freigegebenen Regeln
  in allen Fenstergrößen. Der Fall merkt sich beim Öffnen Zonen-Freigabe, Liste „Spieler“ und Werte aller Spieler
  (`dev.PersonalRulesDev#snapshot`) und stellt sie beim Schließen wieder her (`#restore`), damit nachfolgende Schritte
  wie B7 und `zones-open:` nicht mit Wind auf Fest laufen; das prüft der Schritt `race-rules:` „ui-scale-Fall
  „Konfigurator mit persönlichen Regeln“ stellt beim Schließen … wieder her“.

## 8. Oberfläche (Teil 2)

### 8.1 Schalter „Freigabe“ im Tab „Welt-Regeln“

- Rechts an jeder der 11 Physikregeln ein Schalter mit der Stufe **Fest** (grau), **Zonen** (blau) oder **Spieler**
  (grün); Spaltenkopf „Freigabe“ je Abschnitt mit Physikregeln. Er ersetzt das Häkchen „Zonen“ aus zones-open (aus =
  Fest, an = Zonen). Alle anderen Regeln sind immer Fest und haben keinen Schalter.
- Klick: nächste Stufe (Fest → Zonen → Spieler → Fest), Rechtsklick: Zonen (Standard), Pfeiltasten. Wie die Regeln ein
  Entwurf bis „Speichern“, dann je Regel `propwash:rule_tier`; „Standardwerte“ setzt alle auf Zonen.
- Tooltip: alle drei Stufen mit Erklärung, die aktuelle hervorgehoben, „Spieler schließt Zonen ein“, dass gesperrte
  Werte gespeichert bleiben, und Bedienung bzw. „Nur lesbar“. Ohne Recht (`/gamerule`) ist der Schalter nur lesbar.
- Kleine Fenster: Würde ein Abschnitt sonst Beschriftungen kürzen, zeigt der Schalter dort nur den Anfangsbuchstaben
  (F, Z, S bzw. F, Z, P) in der alten Spaltenbreite; Abschnitte ohne Physikregel behalten die alte Breite.
- Einleitung des Tabs: Serverregeln sind der Rennstandard, „Freigabe“ legt fest, wer eine Physikregel ändern darf. Der
  Abschnitt „Rennstandard“ sagt jetzt, dass er nur noch alte Zeiten (vor rc.3, ohne gespeicherte Serverregeln) einordnet.

### 8.2 Zonen-Editor

Werte einer Zone für Regeln auf Stufe Fest stehen wie bisher ausgegraut im Abschnitt „Vom Server gesperrt (wirkt
nicht)“; der Tooltip nennt die Freigabe „Fest“ im Tab Welt-Regeln und dass der Wert bei erneuter Freigabe wieder wirkt.

### 8.3 Tab „Meine Regeln“ (K-Menü, nach „Welt-Regeln“)

- Einleitung: nur für dich, nur in dieser Welt; ohne freigegebene Regel der Hinweis, wo ein Admin freigibt.
- **Presets** oben: Auswahl („Race Legal“ und „Eigene: …“) mit „Übernehmen“ (gilt sofort, `APPLY`), darunter Namensfeld,
  „Merken“ (speichert die aktuellen Abweichungen unter dem Namen, gleicher Name aktualisiert), „Umbenennen“ und
  „Löschen“ (zweimal klicken). „Race Legal“ ist fest eingebaut: keine Abweichungen, also immer genau die aktuellen
  Serverregeln; es lässt sich weder umbenennen noch löschen, und kein eigenes Preset darf so heißen. Eigene Presets
  liegen in `config/propwash/personal_presets.json` (höchstens 32, je Regel nur Abweichungen) und gelten in allen
  Welten. Beim Anwenden überspringt Propwash Regeln, die der Server nicht für Spieler freigibt, und nennt sie
  („… übernommen – übersprungen, weil nicht freigegeben: Wind“).
- **Statuszeile**: „Race Legal ✓ – deine Runden zählen normal“, „Race Legal ✓ – Abweichungen nur bei neutralen
  Regeln“ oder gelb „Weicht ab – Runden zählen als assisted: Schwerkraft 130 % statt 100 %“; ungespeichert mit
  „(noch nicht gespeichert)“.
- **Physikregeln**: alle 11. Freigegebene (Stufe Spieler) mit Regler bzw. Schalter, daneben „Server: X“ und „↺“
  (zurücksetzen: folgt wieder dem Serverwert, auch wenn der Admin ihn später ändert; Rechtsklick auf den Regler ebenso).
  Gesperrte grau mit „Vom Server gesperrt“ und dem Serverwert; Tooltip mit der Stufe und einer gespeicherten, gerade
  wirkungslosen Abweichung. Entwurf bis „Speichern“ (ein `APPLY` mit allen Abweichungen), „Standardwerte“ = Race Legal.
  In schmalen Fenstern steht „Server: X“ unter der Regel.

### 8.4 Vorabwarnung im Tab „Rennen“

Die Flughilfe-Prüfung nennt persönliche Abweichungen mit Regel, eigenem Wert und Serverwert („Persönliche Regeln
weichen von den Serverregeln ab: Schwerkraft 130 % statt 100 %“, auch Chat und Gründe der Bestenliste). Gibt es
persönliche Abweichungen, steht unter der Überschrift der Knopf **„Race Legal anwenden“**: Er löscht alle Abweichungen
sofort (`APPLY` ohne Werte).

### 8.5 Abzeichen „Serverregeln“

- **Bestenliste**: blaues Abzeichen neben dem Titel, wenn die Serverregeln der Welt vom Propwash-Standard abweichen;
  Tooltip mit den Abweichungen („Schwerkraft 80 % statt 100 %“) und dem Hinweis, dass die Zeiten nur bedingt mit anderen
  Welten vergleichbar sind. Ist die Kopfzeile zu schmal für „Serverregeln“ (oder neben „Kreativ-FC erlaubt“, etwa bei
  GUI 320 × 240), steht kurz „≠“ mit demselben Tooltip.
- **Zeiten und ihre Geister**: Abzeichen in der Zeile, wenn der Regelsatz der Zeit (und damit ihres Geistes und Replays)
  von den Serverregeln heute abweicht; ist kein Platz für „Serverregeln“, kurz „≠“. Der Zeilen-Tooltip nennt „Mit anderen
  Serverregeln geflogen (damals statt heute)“ mit den Werten. Alte Zeiten ohne Regelsatz bekommen keins.
- **Wiedergabe**: dasselbe Abzeichen im Infokasten, wenn die geladene Aufnahme mit anderen Serverregeln geflogen wurde.
