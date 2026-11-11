# Rennwertung: rennrelevante Regeln, Rennstandard und freies Platzieren

Für Spieler, Server-Admins und Entwickler. Bis 0.5.2 machte jede Welt-Regel, die die Simulation verändert, eine Runde zur
Runde „mit Flughilfe“ – auch „Disarm on crash“, das keinen Vorteil verschafft. Ab 0.6.0 ist jede Welt-Regel fürs Rennen
eingeordnet, Operatoren legen einen **Rennstandard** fest, und jede Zeit merkt sich, mit welchen Regeln sie geflogen
wurde. Dazu kommt die Welt-Regel „Kurs-Elemente frei platzieren“.

Grundlagen: [`race-system.md`](race-system.md) (Wertung, Flughilfe), [`zones-open.md`](zones-open.md) (Zonen, rennfrei),
[`api/race-data.md`](api/race-data.md) (Renndaten für Server und Bots).

## 1. Rennrelevant oder neutral

Jede Welt-Regel trägt ihre Einordnung im Code (`RuleSpec#raceRelevant`, gesetzt in `WorldRuleSpecs`).

| Einordnung | Regeln | Wirkung |
|---|---|---|
| rennrelevant (verschafft einen Vorteil) | Schwerkraft, Propwash, Bodeneffekt, Hardware-Grenzen, Wind, Turbulenz, Akkulaufzeit, Spannungseinbruch, Crash-Schaden, Crash-Schaden-Faktor, Crash-Toleranz, Prop-Treffer, unbegrenzte Reichweite analog und digital, Drohnen treffen Wesen, Drohnen zerstören Blöcke, Aufprall beim Zerstören, wasserfeste Drohnen | Eine Runde zählt nur rennfrei, wenn die Regel den Wert des Rennstandards hatte. |
| neutral | Disarm on crash, max. Videokanäle, Signal-Repeater, 3D-Modus erlauben, Teile-Prüfung, autonomes Rescue, max. Größe freier Gates, Drohnen erzeugen Chunks, Runden mit Flughilfe speichern, Drohnen-Spuren, unverwundbare Piloten, Reset erlauben, Freecam erlauben, Spieler dürfen Zonen anlegen, Kurs-Elemente frei platzieren, Kreativ-FC renntauglich (§9) | Beeinflusst die Wertung nie, egal ob an oder aus. |

- Neutral heißt nicht „egal“: Was eine neutrale Regel erlaubt, prüft die Wertung weiter selbst. „Teile-Prüfung aus“ ist
  neutral, eine Drohne mit unpassenden Teilen zählt trotzdem als Flughilfe („Teile passen nicht“); „autonomes Rescue“ ist
  neutral, eine Runde mit ausgelöstem Rescue zählt als Flughilfe.
- Der Tooltip jeder Regel im Tab „Welt-Regeln“ nennt die Einordnung („Rennrelevant: Weicht ein Spieler persönlich vom
  Serverwert ab, zählt seine Runde mit Flughilfe“ bzw. „Neutral fürs Rennen …“, seit rc.3 Teil 2). `/propwash race
  standard` listet beide Gruppen.
- „Warum zählt meine Runde mit Flughilfe?“ (Flughilfe-Prüfung im Pilotenmenü), die Chat-Meldungen und die Gründe in der
  Bestenliste nennen nur rennrelevante Regeln, die vom Rennstandard abweichen, z. B. „Welt-Regeln weichen vom
  Rennstandard ab: Wind Mild statt Aus“ (Rennstandard Wind aus, Welt Wind mild).
- **Add-on-Regeln** zählen standardmäßig als rennrelevant. Ein Add-on markiert eine Regel als neutral mit
  `PropwashApi.worldRules().raceNeutral(rule)` (nur für eigene, über die API angemeldete Regeln, sonst
  `IllegalArgumentException`); `raceRelevant(rule)` fragt die Einordnung ab. Beide sind `default`-Methoden (API 1.7 bleibt
  additiv): eigene Implementierungen und Test-Doubles von `WorldRules` müssen sie nicht überschreiben. Eine rennrelevante
  Add-on-Regel geht mit ihrem Wert in den Regel-Satz jeder Zeit ein (Schlüssel `namespace:pfad`). Beispiel:
  `dev/example/ExampleRules` markiert die Werfer-Regeln des Beispiel-Add-ons (Abklingzeit, Nachladen) als neutral.

## 2. Rennstandard

Der Rennstandard sind die Werte der rennrelevanten Regeln, mit denen Runden als rennfrei zählen. Standard: die
Propwash-Standardwerte (wie bis 0.5.2).

**Ab 0.6.0-rc.3 (persönliche Regeln, [`personal-rules.md`](personal-rules.md)):** Für neue Zeiten ist der Rennstandard
die Serverregeln selbst (plus renntaugliche Zonen); Serverregeln machen keine Runde mehr assisted, nur persönliche
Abweichungen (`PERSONAL_RULES`) und nicht renntaugliche Zonen. Der hier beschriebene gespeicherte Rennstandard wertet
nur noch alte Zeiten (ohne gespeicherte Serverregeln); sie behalten so ihre Kennzeichnung.

- **Tab „Welt-Regeln“, Abschnitt „Rennstandard“** (nach den Zonen): Zeile mit dem Stand („Rennstandard:
  Propwash-Standardwerte“ bzw. die abweichenden Werte, Tooltip mit der ganzen Liste), darunter der Vergleich mit den
  aktuellen Welt-Regeln (grün „… entsprechen dem Rennstandard“, sonst gelb mit den abweichenden Regeln). Mit Recht auf
  die Welt-Regeln zwei Knöpfe, nebeneinander je in halber Breite; würde ein Text dort trotz Stauchung gekürzt (etwa bei
  GUI 320 × 240 auf Deutsch, wenn Tab-Titel von Add-ons die Seitenleiste verbreitern), stehen sie untereinander in voller
  Breite (`RaceStandardSection#buttons`):
  - **„Aktuelle Regeln übernehmen“** macht die aktuellen Werte aller rennrelevanten Regeln zum Rennstandard (aktiv, wenn
    sie abweichen). So stellt man einen Standard ein: Regeln wie gewünscht setzen, speichern, übernehmen.
  - **„Propwash-Standard“** setzt den Rennstandard auf die Propwash-Standardwerte zurück.
- **Befehl** (Operatoren): `/propwash race standard` zeigt den Standard und die Einordnung,
  `/propwash race standard adopt` übernimmt die aktuellen Werte, `/propwash race standard reset` setzt zurück,
  `/propwash race standard set <regel> <wert>` setzt einen Wert (`wind 0`, `gravity_scale 80`, `crash_damage false`,
  Add-on-Regeln in Anführungszeichen: `"mymod:gusts" 2`).
- Jede Änderung meldet der Server im Chat („… setzt den Rennstandard …“) und schickt den neuen Standard allen Spielern.
  Ohne Recht lehnt der Server Änderungen ab (Meldung höchstens zweimal in 5 s).
- Gespeichert in den Weltdaten (`data/propwash/race_standard.dat`: Werte, die vom Propwash-Standard abweichen, Zeitpunkt
  und Name der letzten Änderung). Alte Welten haben keine Datei und damit die Propwash-Standardwerte.

## 3. Regel-Satz je Zeit und Neuberechnung

Seit rc.3 gilt die Neuberechnung nur für alte Zeiten; neue Zeiten tragen die Serverregeln beim Fliegen
(`server_rules`) und behalten ihr Urteil ([`personal-rules.md`](personal-rules.md) §4).

- Zu **jeder** geflogenen Zeit (Runde und Rennen) speichert Propwash den Regel-Satz: die wirksamen Werte der
  rennrelevanten Regeln während der Runde, Welt und Zonen getrennt (Zone mit ihrer Id), rennfreie Zonen markiert.
  Ändert sich ein Wert während der Runde (Zone betreten, Regel umgestellt), stehen beide Werte im Satz. Werte gleich dem
  Propwash-Standard der Welt werden nicht geschrieben.
- Eine Zeit zählt **rennfrei**, wenn kein fester Grund vorliegt (Höhen-/Positionshalten, Rescue, Kreativ-Drohne, Teile,
  Skalierung, Brille, Add-on-Flughilfe) und jeder Wert ihres Regel-Satzes außerhalb rennfreier Zonen dem **aktuellen**
  Rennstandard entspricht; sonst „mit Flughilfe“ mit den abweichenden Regeln als Gründen.
- Die Bestenliste wird gegen den aktuellen Rennstandard berechnet. Ändert er sich, werden alle Zeiten (auch archivierte
  Layouts) sofort neu eingeordnet; es entsteht **kein** Archiv. Beispiel: Rennstandard Wind 0 – eine Runde bei Wind 0
  zählt rennfrei, eine bei Wind 1 mit Flughilfe; zurück auf den Propwash-Standard tauschen beide die Liste.
- Damit Zeiten bei einem Wechsel nicht fehlen, speichert Propwash Zeiten mit abweichendem Regel-Satz **immer**, auch wenn
  „Runden mit Flughilfe speichern“ aus ist: je Spieler, Klasse und Regel-Satz die beste, höchstens 8 Regel-Sätze je
  Spieler und Klasse und 200 Zeiten je Klasse (Rennen: je Rundenzahl). Zeiten mit festen Gründen speichert Propwash wie
  bisher nur mit der Regel.
- Angezeigt werden Zeiten mit Flughilfe weiter nur nach dieser Regel: Eine Zeit, die geflogen wurde, während die Regel
  aus war, trägt die Marke `hidden`. Sie zählt rennfrei, sobald ihr Regel-Satz zum Rennstandard passt, erscheint aber
  nie in der Liste „Mit Flughilfe“ (Spiel, API, JSON) – auch nicht, wenn die Regel später angeht. Die Liste „Mit
  Flughilfe“ selbst zeigt das Spiel wie bisher nur, solange die Regel an ist.
- Persönliche Bestzeit, Klassen- und Kursrekord, Rang und Geist gelten für die rennfreie Wertung beim Fliegen. Ein Geist
  bleibt nach einem Wechsel des Standards gespeichert.
- **Alte Zeiten** (Welten vor 0.6.0) haben keinen Regel-Satz und gelten als mit den damaligen Standardwerten geflogen.
  Bei gleichem Rennstandard bleiben die Bestenlisten unverändert; alte Zeiten mit Flughilfe behalten ihre Gründe,
  Gründe aus neutralen Regeln fallen weg (eine alte Runde, die nur wegen „Disarm on crash“ als Flughilfe galt, zählt
  jetzt rennfrei).
- Sicherheit: Mehr als 95 Einträge in einem Regel-Satz setzen die Marke `propwash:too_many_rules`, die nie rennfrei
  zählt; die Zeitleiste der Regeln je Pilot fasst bei mehr als 64 Abschnitten in einer Runde die ältesten zusammen,
  statt sie zu vergessen.

## 4. Kurs-Elemente frei platzieren

Neue Welt-Regel `propwash:free_course_placement` „Kurs-Elemente frei platzieren“ (Gruppe Spiel, Standard aus, neutral).

- **Aus:** wie bisher – ein Gate, eine Fahne, Stange oder ein Würfel lässt sich nicht dort setzen, wo es ein anderes
  Element oder einen Block überlappen würde. Die Meldung lautet jetzt „Hier ist kein Platz – für überlappende
  Kurs-Elemente braucht es die Welt-Regel „Kurs-Elemente frei platzieren““, wenn nur ein anderes Element im Weg ist; bei
  Blöcken bleibt die alte Meldung.
- **An:** Elemente dürfen ineinander, überlappend und in Blöcken stehen (Gate in Gate, Fahne im Gate, eng gestellte
  Stangen, ein Gate-Pfosten in der Wand, eine Fahne im Boden). Die Platzierung prüft dann weder andere Elemente noch
  Blöcke (Owner-Entscheidung vom 27.10.2026). Das gilt für Setzen, Vorschau, Verschieben im Gate-Editor, Stapeln, Gates
  auf Gate-Stangen und Deckel. Weiter geprüft werden die Welthöhe und, wo bisher, Spieler, Drohnen und andere Wesen im
  Weg; die Regeln des Stapelns selbst bleiben unverändert.
- Wertung und Kollision bleiben je Element: Jedes Element wertet seinen eigenen Durchflug (durch die Öffnung des inneren
  Gates zählen beide, zwischen innerem und äußerem Rahmen nur das äußere) und kollidiert mit seinem eigenen Rahmen. Zwei
  genau gleiche Gates an derselben Stelle wertet der Kurs wie ein Gate, das zweimal im Kurs steht (je Durchflug eins).
  Wertung und Wertungsflächen rechnen nur mit der Form der Elemente, nicht mit Blöcken: Steckt ein Teil im Block, zählt
  der Durchflug durch den freien Teil der Öffnung wie sonst.

## 5. Renndaten (API und JSON)

- `PropwashApi.race().standard(server)` → `RaceStandardInfo`: alle rennrelevanten Regeln mit Wert und Standardwert
  (`rules`), die neutralen Regel-Ids (`neutral`), Zeitpunkt und Name der letzten Änderung; nur am Server-Thread.
- `BoardEntry#rules()` → `List<RuleValue>` (`rule`, `value`, `zone` – 0 = Welt, `raceLegalZone`): der gespeicherte
  Regel-Satz der Zeit; leer = alle rennrelevanten Regeln auf dem Propwash-Standard (oder eine alte Zeit).
- JSON-Spiegel: `raceStandard` in `courses.json` und jeder `leaderboards/<kurs-id>.json`, `rules` an jedem Eintrag
  (Schema bleibt 1, neue Felder). Nach einem Wechsel des Standards schreibt der Spiegel alle Dateien neu.
- `ZoneHint.Zone#changesPhysics` heißt jetzt: Die Zone setzt mindestens eine rennrelevante Regel auf einen anderen Wert
  als den Rennstandard (vorher: irgendeine Regel). Einzelheiten in [`api/race-data.md`](api/race-data.md).

## 6. Technik

| Datei | Inhalt |
|---|---|
| `server/rules/RuleSpec`, `WorldRuleSpecs` | Feld `raceRelevant`, `neutral()`; Einordnung jeder Regel; `FREE_COURSE_PLACEMENT` |
| `api/worldrule/WorldRules`, `apiimpl/Api17Registries` | `raceNeutral`, `raceRelevant` für Add-on-Regeln |
| `race/RaceRules` | alle Regeln (Propwash und Add-ons) als `Rule`, Werte der Welt, Beobachtung mit Zonen (`observe`) |
| `race/RaceRuleSet` | Regel-Satz (`Seen`: Regel, Wert, Zone, rennfrei), Codec, `matches`/`mismatches` gegen einen Standard, `split` alter Gründe |
| `race/RaceStandard`, `RaceStandards` | Standard (nur Abweichungen), SavedData, Sync `propwash:race_standard`, Änderung `propwash:race_standard_edit` mit Rechte-Prüfung |
| `race/RuleTimeline` | Regel-Sätze je Pilot über die Zeit, wie `AssistTimeline` |
| `race/Leaderboard` | gespeicherte Zeiten (`lap_times`, `race_times`) mit Regel-Satz und Marke `hidden`, Wertung gegen `Scoring` (Rennstandard), die alten Listen (`entries`, `assisted`, `races`, `assisted_races`) schreibt Propwash weiter für ältere Versionen |
| `race/AssistCheck`, `AssistDetail`, `AssistWords` | Gründe nur rennrelevant, Lap-Urteil mit Regel-Satz |
| `race/RaceServer`, `CourseRegistry` | Regel-Satz je Runde und Rennen, Eintragen mit Regel-Satz |
| `race/RaceStandardCommands` | `/propwash race standard …` |
| `race/client/ClientRaceStandard`, `RaceStandardSection` | Standard beim Client, Abschnitt im Tab |
| `race/GateItem` | Platzierungsprüfung mit der Regel |
| `race/export/*`, `api/race/RaceStandardInfo`, `RuleValue` | Renndaten |

## 7. Tests

- JUnit: `race.RaceRulesTest` (Einordnung aller Regeln, Add-on-Regeln), `race.RaceRuleSetTest` (Regel-Satz mit Zonen,
  Codec, Überlauf, Lap-Urteil, Zeitleiste, Standard), `race.RaceStandardScoringTest` (Neuberechnung beim Wechsel, Zeiten
  ohne „Runden mit Flughilfe speichern“, Grenzen je Regel-Satz, Rennen, alte Zeiten), `race.CourseOverlapTest` (Wertung
  und Kollision überlappender Gates), `race.data.RaceRulesDataTest` (API und JSON), `race.client.RaceStandardButtonsTest`
  (beide Knöpfe in jeder Zeilenbreite ab 204 Punkten auf Englisch und Deutsch ungekürzt, gemessen mit der Vanilla-Schrift;
  wo beide Texte passen, nebeneinander wie bisher).
- ui-scale: Der Fall „Konfigurator mit langem Add-on-Tab“ prüft im Tab Welt-Regeln den gescrollten Abschnitt Rennstandard
  (beide Knöpfe ganz zu sehen und ungekürzt, `docs/testing.md`, „Preset-Zeile neben einem langen Add-on-Tab“).
- Selbsttest `'-PpropwashSelftestOnly=race-rules:'` (beide Loader): Runde mit „Disarm on crash“ aus und an rennfrei;
  Rennstandard Wind 0 – Runde bei Wind 1 mit Flughilfe „Wind“, bei Wind 0 rennfrei, zurück auf den Propwash-Standard
  tauschen beide; Tab mit Abschnitt, „Aktuelle Regeln übernehmen“ und „Propwash-Standard“ über die Knöpfe; Gate in Gate
  und Fahne im Gate nur mit der Regel; Fahne und Gate in Blöcken nur mit der Regel (ohne Regel abgelehnt mit der
  bisherigen Meldung, Vorschau beim Client mit Regel frei), Runde durch ein Gate, dessen Pfosten im Stein steckt, zählt
  rennfrei. Mit `-PpropwashScreenshots=true` (Fabric):
  `race_rules_standard`, `race_rules_standard_de`.
- Beitrittstest (`PROPWASH_JOINTEST_RACE_RULES=1` an beiden Prozessen, `dev.RaceRulesJoinCheck`,
  `race.client.RaceRulesJoinProbe`): Der Server fliegt mit dem echten Spieler vier Runden (Disarm aus/an, Wind 1 und 0
  unter Rennstandard Wind 0), der Client sieht den Standard, darf ihn ohne Recht nicht ändern, seine Flughilfe-Prüfung
  nennt Wind, nach dem Zurücksetzen nicht mehr. Erwartung:
  `joined|race_rules_server=ok|race_rules_synced=wind0|race_rules_editable=false|race_rules_denied=yes|race_rules_preflight=wind|race_rules_reset=defaults|race_rules_after_reset=clean`.

## 8. Englische Kernsätze (Website)

- Every world rule is now either race-relevant or neutral. Neutral rules such as "Disarm on crash" never make a lap
  assisted, whether on or off.
- Operators set a race standard in the World Rules tab: the values of the race-relevant rules at which laps count as
  unassisted. Every time remembers the rules it was flown with, so changing the standard re-rates old times too.
- New world rule "Free course placement": gates, flags and poles may overlap, for gate-in-gate builds. Each element
  still scores and collides on its own.
- Old worlds keep their leaderboards: the race standard starts at the Propwash defaults.
- New world rule "Creative FC race-legal" (off by default): laps with a creative flight controller no longer count as
  assisted. Admins can override it per course in the course settings (world rule / counts as assist / race-legal); the
  leaderboard then shows "Creative FC allowed".

## 9. Kreativ-Flugcontroller im Rennen (Befund CFC1-1, rc.3)

Bis rc.3 zählte jede Runde mit verbautem Kreativ-Flugcontroller (`StackType.CREATIVE_FC`) als Flughilfe
(`LapAssist.CREATIVE_DRONE`). Jetzt entscheiden Welt-Regel und Kurs:

- Welt-Regel `propwash:creative_fc_race_legal` „Kreativ-FC renntauglich“ (Gruppe Spiel, Standard aus, neutral, nicht in
  Zonen änderbar; stellen darf sie, wer `/gamerule` darf). Aus = der Kreativ-FC zählt als Flughilfe wie bisher, an = er
  ist renntauglich.
- Je Kurs in den Kurs-Einstellungen (Gruppe „Bestenliste und Geister“, Zeile „Kreativ-FC“) drei Stufen: „Welt-Regel“
  (Standard, folgt der Welt-Regel), „Zählt als Flughilfe“, „Renntauglich“. Ändern dürfen nur Admins
  (wer die Welt-Regel per `/gamerule` stellen darf, `WorldRules.maySet`: Operatoren, im Einzelspieler der Host), nicht der Ersteller des
  Kurses; für alle anderen sind die Stufen gesperrt, die Zeile darunter nennt, was gilt („Gilt hier: …“) und dass nur
  Admins ändern. Der Server prüft jedes Paket `propwash:race_course_creative_fc` selbst (`CourseEdits.creativeFc`,
  Meldung `propwash.race.course_edit.denied.admin_only`) und schreibt jede Änderung mit Name ins Server-Log. Das Menü
  sperrt die Stufen nach dem Recht für Welt-Regeln, das der Client kennt (`ClientWorldRules#editable`); entscheidend ist
  die Prüfung am Server.
- Auswertung am Server (`AssistCheck.creativeFc`, in `RaceServer` je Runde): Welt-Regel, überschrieben durch die
  Kurs-Einstellung (`CreativeFcRule#legal(world)`). Renntauglich heißt: `CREATIVE_DRONE` fällt für diese Runde weg;
  Teile-Fehler, Größe, Kreativ-Brille mit eigener Latenz, Halte-Modus, Rescue, persönliche Regeln, Zonen und Add-on-Hilfen
  zählen weiter. Der Client nimmt den Grund für seine Anzeige auf demselben Kurs ebenso heraus.
- Runden tragen den Stand: Eine Zeit, die mit Kreativ-FC renntauglich gewertet wurde, hat `creative_fc: true`
  (Rundenzeit und Rennzeit, nur dann gespeichert). Bestehende Zeiten behalten ihre Wertung: Ein Wechsel von Welt-Regel
  oder Kurs-Einstellung ordnet nichts neu ein (anders als beim Rennstandard), alte Zeiten tragen den Vermerk nicht.
- Transparenz: Ist der Kreativ-FC auf dem Kurs renntauglich, zeigt die Bestenliste in der Kopfzeile das Abzeichen
  „Kreativ-FC erlaubt“ (wie „Serverregeln“, Tooltip mit Grund Welt-Regel oder Kurs). Passen beide Abzeichen nicht ganz
  hinter den Titel (GUI 320 × 240), steht „Serverregeln“ kurz als „≠“ mit demselben Tooltip, „Kreativ-FC erlaubt“ bleibt
  ganz (`race/client/HeaderBadges`); die Angabe Gates und Layout rechts daneben entfällt, wenn sie nicht mehr ganz
  passt. Die Vorabwarnung im Tab Rennen
  nennt den Kreativ-FC nur, wenn er auf dem Kurs, an dem der Pilot steht, als Flughilfe zählt; sonst steht in der Zeile
  Drohne „Kreativ-Flugcontroller – zählt auf diesem Kurs nicht als Flughilfe“.
- Renndaten-Export (JSON-Spiegel, Schema bleibt 1): `creativeFc` je Kurs in `courses.json` und in jeder
  `leaderboards/<kurs-id>.json` (`{"course": "world|assist|legal", "worldRule": …, "raceLegal": …}`) und `creativeFc`
  (`true`/`false`) an jedem Eintrag. Ein Wechsel der Welt-Regel schreibt alle Dateien bei der nächsten Prüfung (alle
  60 s) neu. Die Add-on-API (`BoardEntry`, `CourseInfo`) bleibt unverändert.
- Abgleich ohne neues Protokoll: Die Kurs-Einstellung steht in zwei freien Bits des Kopf-Bytes von `CourseData`
  (gleich lang wie bisher, ältere Clients überlesen sie); das neue Paket `race_course_creative_fc` sendet der Client nur,
  wenn der Server es kennt (`ClientNet.canSend`), sonst sind die Stufen gesperrt. Die Kursdatei speichert `creativeFc`
  nur für „assist“ und „legal“; alte Welten folgen der Welt-Regel (aus), Bestenlisten bleiben unverändert.

| Datei | Inhalt |
|---|---|
| `race/CreativeFcRule` | die drei Stufen, `legal(world)`, Schlüssel für Datei, Netz und Texte |
| `server/rules/WorldRuleSpecs` | `CREATIVE_FC_RACE_LEGAL` |
| `race/AssistCheck` | `creativeFc(reasons, legal)`, `creativeFc(LapVerdict, legal)`, `LapVerdict#creativeFc` |
| `race/CourseRegistry`, `CourseData`, `CourseEdits`, `CourseAccess`, `CourseMenu`, `RacePayloads`, `RaceModule` | Feld je Kurs, Abgleich, Paket mit Rechteprüfung, Zeile `CREATIVE_FC` mit Recht `ADMIN` |
| `race/RaceServer`, `Leaderboard` | Urteil je Runde, `creative_fc` an `Entry`, `RaceTime`, `RaceRow` |
| `race/client/ClientCreativeFc`, `CourseMenuScreen`, `LeaderboardScreen`, `AssistPreflight`, `ClientRaceTracker` | Zustand beim Client, Schalter, Abzeichen, Vorabwarnung, Anzeige beim Fliegen |
| `race/client/HeaderBadges` | Platz der Abzeichen „Serverregeln“ (ganz oder „≠“) und „Kreativ-FC erlaubt“ in der Kopfzeile |
| `race/export/RaceSnapshots`, `RaceDataJson`, `RaceDataMirror` | Export |

Tests:

- JUnit `race.CreativeFcRaceTest` (Welt-Regel aus/an × Kurs Welt-Regel/Flughilfe/renntauglich, nur der Kreativ-FC fällt
  weg, Rechte: Besitzer ohne `/gamerule` nein, Admin auch am fremden Kurs ja, `CourseData` gleich lang),
  `race.data.CreativeFcDataTest` (Kursdatei, Rechteprüfung am Server, Vermerk an Runde und Rennen, alte Zeiten
  unverändert, Codec, Export); `race.client.HeaderBadgesTest` (Platz der Abzeichen in der Kopfzeile mit den Breiten bei
  GUI 320 × 240 und 427 × 240); `race.RaceRulesTest` ordnet die Regel als neutral ein; Vergleichsdateien unter
  `common/src/test/resources/race-data/` mit den neuen Feldern.
- Selbsttest `'-PpropwashSelftestOnly=race-rules: Kreativ-FC'` (beide Loader): sieben Runden mit Kreativ-FC am Server
  (Welt-Regel aus/an × drei Stufen, renntauglich mit persönlichem Wind 0 bleibt „persönliche Regeln“, alte Runde nach
  dem Wechsel unverändert); Kurs-Einstellungen mit drei Stufen, Admin stellt „Renntauglich“, Server übernimmt es, Layout
  ohne Befund; Bestenliste mit Abzeichen nur bei renntauglich, Vorabwarnung mit Kreativ-Drohne in der Hand nennt den
  Kreativ-FC nur als Flughilfe; Bestenliste mit Abzeichen „Serverregeln“ bei GUI 320 × 240 (Fenster 640 × 480, Skala 2
  und automatisch) und 427 × 240: „Kreativ-FC erlaubt“ steht mit, beide innerhalb der Kopfzeile ohne Überlappung, Layout
  ohne Befund, beide Tooltips erscheinen. Mit `-PpropwashScreenshots=true` (Fabric): `race_rules_creative_fc_menu`,
  `race_rules_creative_fc_badge`, `race_rules_creative_fc_badge_tooltip`, `race_rules_creative_fc_badge_narrow`
  (640 × 480), je auch `_de`.
- Beitrittstest `PROPWASH_JOINTEST_CREATIVE_FC=1` an beiden Prozessen (`dev.CreativeFcJoinCheck`,
  `race.client.CreativeFcJoinProbe`): Der Gast baut den Kurs (Besitzer), sieht die Stufen gesperrt, sein Paket lehnt
  der Server ab; als Operator stellt er im Menü „Renntauglich“; der Server fliegt mit ihm vier Runden mit Kreativ-FC
  (renntauglich, Flughilfe, Welt-Regel an, Welt-Regel aus) und prüft, dass die renntaugliche Runde danach so bleibt.
  Erwartung: `joined|creative_fc=ok`.
