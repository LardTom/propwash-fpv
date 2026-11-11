# Zonen für alle und Freigabe je Welt-Regel

Für Spieler, Server-Admins und Entwickler. Bis 0.5.2 konnten nur Spieler mit dem Recht auf die Welt-Regeln Zonen
anlegen (Tab „Welt-Regeln“ oder `/propwash zone`). Ab 0.6.0 legt jeder Spieler Zonen mit einem Item an, wie Race Gates
mit dem Kurs-Werkzeug, und verwaltet sie im eigenen Tab „Zonen“ des K-Menüs. Damit Server die Kontrolle behalten, legen
Operatoren im Tab „Welt-Regeln“ je Regel fest, ob Zonen sie ändern dürfen.

Grundlage der Zonen: [`design/zones.md`](design/zones.md).

## 1. Zonen-Markierer

| | |
|---|---|
| Item | `propwash:zone_marker` „Zonen-Markierer“ (EN „Zone Marker“), stapelt nicht, im Propwash-Kreativ-Tab |
| Rezept | Glowstone-Staub über Papier über Stock (so günstig wie das Kurs-Werkzeug: Redstone, Papier, Stock), Reiter „Ausrüstung“, frei mit jeder Zutat und jedem Propwash-Item |
| Rechtsklick auf einen Block | setzt die erste Ecke: den Block vor der angeklickten Seite (auf dem Boden also den Luftblock darüber), bis 64 Blöcke weit |
| danach | ein grüner Vorschau-Rahmen reicht von der ersten Ecke bis zum Block, auf den man schaut |
| zweiter Rechtsklick | gegenüberliegende Ecke; der Zonen-Editor öffnet sich mit Mauszeiger für die neue Zone („Zone n“, Quader über beide Ecken) |
| beide Ecken auf gleicher Höhe | die Zone wird 8 Blöcke hoch (wie „Neue Zone hier“), sonst genau so hoch wie markiert |
| Schleichen + Rechtsklick | verwirft gesetzte Ecken; ohne Ecken öffnet es das Zonen-Menü (Tab „Zonen“, §3) und hebt dort die Zone an der Stelle hervor, auf die man schaut (sonst am Spieler), sofern man sie sehen darf |

Der Editor ist der bestehende Zonen-Editor (`design/zones.md` §4): Name, Form, Lage, Größe, Priorität, Rennfrei,
Übergang und die freigegebenen Regeln; „Übernehmen“ speichert, „Abbrechen“ verwirft. Klicks mit dem Markierer gehen nicht
an Blöcke (keine Truhe öffnet sich), solange er in der Hand liegt und kein Editor offen ist.

## 2. Besitz und Rechte

- Eine Zone, die ein Spieler mit dem Markierer anlegt, gehört ihm (`owner`: UUID und Name, der Name wird beim eigenen
  Bearbeiten aktualisiert). Das gilt auch für Operatoren, die den Markierer benutzen.
- Zonen ohne Besitzer sind Admin-Zonen: alle Zonen aus älteren Welten, „Neue Zone hier“ eines Operators im Tab und `/propwash zone
  create`. Daran ändert sich nichts.
- **Anlegen:** jeder Spieler, solange die neue Welt-Regel „Spieler dürfen Zonen anlegen“ (`propwash:players_create_zones`,
  Gruppe Spiel, Standard an) an ist; Operatoren immer.
- **Ändern und löschen:** der Besitzer (solange die Regel an ist) und Operatoren. Fremde Zonen und Admin-Zonen kann ein
  Spieler ohne Recht weder ändern noch löschen; der Server lehnt Pakete ab und meldet es im Chat (höchstens zweimal in
  5 s).
- „Operator“ heißt hier wie bisher: Recht auf die Welt-Regeln (`WorldRules#mayEdit` – Gastgeber, Operatoren oder wer
  `/gamerule` für eine Propwash-Regel ausführen darf).
- Spieler dürfen ihre Zonen auch „rennfrei“ machen. Es gibt keine Höchstzahl und keine Höchstgröße je Spieler; es gelten
  die Grenzen jeder Zone (je Richtung 1–1024 Blöcke, Priorität −99 … 99) und höchstens 128 Zonen je Dimension für alle
  zusammen (Größe des Sync-Pakets).
- Regel aus: Zonen von Spielern bleiben bestehen und wirken weiter; ändern oder löschen können sie dann nur Operatoren.
- Der Server ist maßgeblich: Er setzt den Besitzer einer neuen Zone selbst (ein Spieler kann keine fremde oder
  Admin-Zone vortäuschen), behält den Besitzer beim Ändern, setzt die Dimension und übernimmt gesperrte Werte (§4).
- Zonen stehen im Tab „Zonen“ (§3), nicht mehr im Tab „Welt-Regeln“. Der Tooltip jeder Zone nennt den Besitzer bzw.
  „Admin-Zone“, ebenso `/propwash zone list` und `info`.

## 3. Tab „Zonen“

Owner-Playtest 23.10. (Befund OWNP-2): Zonen gehören in ein eigenes Menü, nicht in den Tab „Welt-Regeln“. Der Tab
„Zonen“ (`zone.client.ZonesTab`, Id `zones`) steht im K-Menü direkt vor „Welt-Regeln“; Schleichen + Rechtsklick mit dem
Zonen-Markierer öffnet ihn ebenfalls (§1).

| | Spieler | Operator |
|---|---|---|
| Liste | nur die eigenen Zonen der Dimension („Deine Zonen“) | alle Zonen der Dimension („Alle Zonen“), je Zone eine Zeile mit ihrem Besitzer (`propwash.zones_open.owner.player`) bzw. „Admin-Zone“ |
| „Neue Zone hier“ (rechts in der Zeile der Überschrift) | solange „Spieler dürfen Zonen anlegen“ an ist; die Zone gehört dem Spieler | immer; die Zone ist eine Admin-Zone |
| „Zeigen“ | jede gelistete Zone | jede Zone |
| „Hinspringen“ | nur wer `/tp` nutzen darf (z. B. mit Rechten über ein Rechte-Mod) | wer `/tp` nutzen darf (im Einzelspieler nur mit Cheats) |
| „Bearbeiten“, „Löschen“ | eigene Zonen, solange die Regel an ist | jede Zone |

- Je Zone: Farbpunkt, Name, Art (rennfrei / zählt als Flughilfe / Physik wie Welt), Detailzeile (Form, Größe, Lage,
  Priorität, Werte), beim Operator der Besitzer, darunter die Knöpfe. Passen die Knöpfe nicht in eine Zeile (GUI 320 ×
  240, deutsch), brechen sie in eine zweite Zeile um. Tooltip über der Zone wie bisher mit allen Angaben.
- „Zeigen“ schließt das Menü und hebt die Zone 12 s lang in der Welt hervor: Der Rand pulsiert und wird auch über
  160 Blöcke hinaus gezeichnet; über der Schnellleiste steht „Zone „…“: 120 Blöcke ↗“ (Pfeil relativ zur Blickrichtung,
  in der Zone „Du bist in der Zone „…““).
- „Hinspringen“ schickt das neue Paket `propwash:zone_jump` (Nummer der Zone). Der Server prüft das Recht wie `/tp`
  (Knoten `teleport location` im Befehlsbaum, `ZoneServer#mayJump`), dass der Spieler die Zone sehen darf und dass sie in
  seiner Dimension liegt, und setzt ihn auf den ersten freien Boden in der Mitte der Zone (von unten; sonst darunter bis
  zum Weltboden). Gibt es dort keinen, lehnt er ab („In und unter der Zone „…“ gibt es keinen freien Boden zum Landen“)
  und lässt den Spieler stehen, statt ihn in die Luft zu setzen (Befund AUTO18-1). Ohne Recht: Ablehnung im Chat
  (höchstens zweimal in 5 s).
- „Löschen“ fragt nach: Der erste Klick färbt den Knopf und meldet in der Fußzeile „Zum Löschen noch einmal auf
  „Löschen“ klicken“, ein zweiter Klick innerhalb von 4 s löscht. „Bearbeiten“ und „Neue Zone hier“ öffnen den
  bestehenden Zonen-Editor.
- Vor „Zeigen“, „Hinspringen“, „Bearbeiten“ und „Neue Zone hier“ schließt das K-Menü wie beim Start der Freecam:
  ungespeicherte Änderungen anderer Tabs werden gespeichert.
- Ohne Recht zum Anlegen (Regel aus) ist der Tab nur lesbar; der Hinweis oben nennt den Grund.
- Die Einstellungssuche findet „Neue Zone hier“ mit „zone“, „Zone“ und „zones“ und jede gelistete Zone über ihren Namen.
- Der Server bleibt maßgeblich: Liste und Knöpfe folgen dem Stand aus dem Sync, jede Aktion prüft der Server (§2).
- Rein rechnerisch (`zone.ZoneMenu`): wer welche Zonen sieht (`listed`), welche Knöpfe eine Zone hat (`actions`), das
  Ziel beim Hinspringen (`jumpTarget`) und der Richtungspfeil (`arrow`).
- Unter der Liste steht der Abschnitt „Anzeige“ mit der Client-Einstellung „Zonen anzeigen: Nur mit Werkzeug / Mit Werkzeug oder
  im Flug / Immer“ (§9.3); sie gilt nach „Speichern“ bzw. beim Schließen des K-Menüs.

## 4. Freigabe je Welt-Regel

**Seit 0.6.0-rc.3** ersetzt der Schalter „Freigabe“ mit den Stufen Fest, Zonen und Spieler das Häkchen (nur an den elf
Simulationsregeln, alle anderen Regeln sind immer „Fest“ und zeigen keinen Schalter); Häkchen aus entspricht „Fest“,
Häkchen an „Zonen“. Details: [`personal-rules.md`](personal-rules.md) §8. Der Rest dieses Abschnitts beschreibt den Stand
bis rc.2; Speicherung, Wirkung und Meldungen gelten unverändert.

Im Tab „Welt-Regeln“ steht rechts an jeder Regel ein Häkchen, darüber je Gruppe der Spaltenkopf „Zonen“. Tooltip:
„Darf von Zonen geändert werden“.

| Regel | Häkchen | Standard |
|---|---|---|
| die elf Simulationsregeln, die Zonen schon in 0.5.2 ändern konnten (Schwerkraft, Propwash, Bodeneffekt, Hardware-Grenzen, Wind, Turbulenz, Akkulaufzeit, Spannungseinbruch, Crash-Toleranz, Entschärfen bei Crash, Prop-Treffer) | bedienbar (mit Recht) | an |
| alle anderen Propwash-Regeln, Add-on-Regeln und „Weitere Regeln“ | sichtbar, ausgegraut | aus |

- Aus: Der Zonen-Editor bietet die Regel nicht mehr an. Zonen, die die Regel schon setzen, **behalten den Wert
  gespeichert**, wenden ihn aber nicht an (Physik am Client und am Server, Flughilfe, Farbe und Art der Zone im Tab und am
  Rand). Im Editor steht der Wert ausgegraut im Abschnitt „Für Zonen gesperrt (wirkt nicht)“ und lässt sich nicht
  ändern; im Tab trägt er „(gesperrt)“. Wieder an: Der gespeicherte Wert wirkt sofort wieder.
- Die Freigabe ist Teil der Weltdaten der Zonen (`data/propwash/zones.dat`, Feld `release`, nur Abweichungen vom
  Standard). Bearbeiten wie die Regeln: Entwurf bis „Speichern“, „Standard“ setzt auch die Freigaben zurück, der Server
  meldet jede Änderung im Chat („… erlaubt Zonen, Schwerkraft zu ändern“ bzw. „… sperrt Schwerkraft für Zonen“).
- `/propwash zone set` bleibt unverändert: Operatoren können damit auch eine gesperrte Regel setzen; sie wird gespeichert
  und wirkt erst, wenn die Regel freigegeben ist. Der Zonen-Editor dagegen übernimmt gesperrte Werte immer vom Server
  (auch für Operatoren).

**Abweichung vom Auftrag (bewusst):** Der Auftrag sieht das Häkchen an jeder Regel vor, auch für Add-on-Regeln, mit
„aus“ als Standard für die Regeln, die Zonen heute nicht ändern können. Eine Zone kann aber nur Regeln ändern, die am Ort
der Drohne gelesen werden; das sind heute genau die elf Simulationsregeln. Die übrigen Propwash-Regeln lesen ihre Module
ohne Ort (Crash-Schaden, Reichweite, Videokanäle, Blöcke zerstören, Freecam …), und Add-on-Regeln haben in API 1.7 nur
`get(Level)`. Ein Häkchen, das man einschalten kann, das aber nichts bewirkt, wäre irreführend. Deshalb zeigen diese
Regeln das Häkchen ausgegraut und aus, mit dem Tooltip „Zonen können … nicht ändern – die Regel gilt immer für die
ganze Welt“. Die Technik ist dafür vorbereitet: Freigaben werden je Regel-Id gespeichert (auch `namespace:pfad` von
Add-ons), `ZoneRelease.capable` entscheidet, welche Regeln Zonen ändern können. Für weitere Regeln braucht es je Modul
das Lesen am Ort der Drohne (und für Add-ons eine API-Erweiterung wie `WorldRule#get(Level, Vec3)`).

## 5. Alte Welten

Zonen ohne Feld `owner` sind Admin-Zonen; ohne Feld `release` gilt die Standard-Freigabe; ohne Feld `force_world`
erzwingt die Zone keinen Weltwert, jede nicht gesetzte Regel bleibt „wie darunter“ (§9.1). Beim Speichern schreibt
Propwash beide Felder nur, wenn sie vom Standard abweichen – eine Welt ohne Spieler-Zonen und mit Standard-Freigabe
behält dieselben Weltdaten. Netzprotokoll 17 (Zone mit Besitzer, Sync mit Anlegen-Recht und Freigaben, neue Pakete
`propwash:zone_release` und `propwash:zone_jump`).

## 6. Technik

| Datei | Inhalt |
|---|---|
| `zone/ZoneOwner` | Besitzer (UUID, Name), Codec |
| `zone/Zone` | neues Feld `owner` (null = Admin-Zone), `withOwner`, `ownedBy`, `withOverrides` |
| `zone/ZoneRights` | reine Rechte-Logik: `mayCreate`, `mayChange`, `mayDelete`, `maySend`, Besitzer neuer und geänderter Zonen |
| `zone/ZoneRelease` | Freigabe je Regel-Id: `capable`, `released`, `with`, `effective` (Zone ohne gesperrte Werte), `enforce` (gesperrte Werte vom Server), `blocked` |
| `zone/ZoneData` | Feld `release`, `effectiveIn(dimension)` (zwischengespeichert) |
| `zone/ZoneServer` | `zones(level)` und `resolve` rechnen mit den wirksamen Zonen, `stored(level)` liefert die gespeicherten; `edit` prüft Rechte je Zone, `prepared` setzt Besitzer, Dimension und gesperrte Werte; `release` für das Häkchen |
| `zone/ZonesS2C`, `ZoneReleaseC2S`, `ZoneWire` | Sync mit `creatable` und Freigaben, Paket für die Freigabe |
| `zone/ZoneDraft` | Zeilenart `LOCKED` für gesperrte gespeicherte Werte |
| `zone/ZoneMarker`, `ZoneMarkerItem`, `client/ZoneMarkerClient` | Item, Ecken, Vorschau, Klick über den Haken vor den Tastenbelegungen (`mixin.zone.client.MinecraftMixin`) |
| `server/client/RuleTierSwitch`, `server/client/WorldRulesTab` | Schalter „Freigabe“ im Tab „Welt-Regeln“ (bis rc.2 Häkchen `zone/client/ZoneReleaseBox`) |
| `zone/ZoneMenu`, `zone/ZoneJumpC2S` | Liste und Knöpfe je Recht, Ziel beim Hinspringen, Richtungspfeil; Paket `propwash:zone_jump` |
| `zone/client/ZonesTab`, `client/ZoneSection`, `client/ZoneFocus` | Tab „Zonen“, Liste mit Knöpfen, Hervorhebung nach „Zeigen“ (`ZoneRenderer`) |
| `zone/ZoneRuleMode`, `zone/Zone` | Zustand je Regel `BELOW` / `WORLD` / `OWN` (§9.1): `mode`, `sets`, `forcesWorld`, `withWorld`, `withMode`, Feld `world` (Codec `force_world`, Netz nach dem Besitzer) |
| `zone/ZoneResolver`, `ZoneResolution`, `ZoneBlend` | je Regel die höchste Zone, die sie setzt; `forcedBy`/`decider` nennen die Zone mit erzwungenem Weltwert, `source` bleibt die Zone mit eigenem Wert (sonst Welt); `below` rechnet die Zonen unter einer Zone |
| `zone/ZoneText` | `notice` (OSD: Werte je Zone, „aus „…“: …“, „(Welt)“), `decided`, `kind(ZoneResolution)`, Listen mit „Regel: Weltwert“ |
| `zone/ZoneView`, `zone/client/ZoneViewConfig`, `client/ZoneRenderer` | Umrisse je Modus (`outlines`: Werkzeug, Bearbeiten, Pilotensitzung), Stil je Zone bei Überlappung (`styles`, `highlight`), Einstellung `config/propwash/zones.json` mit einmaligem Anheben (`ZoneViewConfig#migrate`) |
| `zone/client/ZoneEditPanel`, `ZoneEditScreen` | Mausrad scrollt das Menü, Scrollbalken (`scroll`, `startDrag`, `drag`), Werte „Darunter: …“ und „Welt: …“ |

## 7. Tests

- JUnit `zone.ZonesOpenTest`: Rechte je Fall (Besitzer, fremder Spieler, Operator, Regel aus), Besitzer neuer und
  geänderter Zonen, Server-Vorbereitung (vorgetäuschter Besitzer, Dimension, gesperrte Werte), Standard-Freigabe für alle
  Regeln, gespeicherter und wirksamer Wert, Editor-Zeilen, keine Grenzen je Spieler (127 Zonen eines Spielers, 1024
  Blöcke), alte Weltdaten, Codec und Netz, Ecken des Markierers.
- JUnit `zone.ZoneMenuTest`: Liste je Recht (Spieler nur eigene Zonen, Operator alle), Knöpfe je Zone und Recht, Ziel
  beim Hinspringen (Boden in der Zone, darunter bis zum Weltboden, auch 86 Blöcke tief; ohne Boden kein Ziel),
  Richtungspfeil, Paket `zone_jump`, Texte des Menüs in beiden Sprachen.
- JUnit `zone.ZoneLayersTest` (Befund PATTY-1): innere Zone mit „Weltwert erzwingen“ hebt die äußere Schwerelosigkeit
  auf und folgt der Welt-Regel, „wie darunter“ erbt, eigener Wert gewinnt, erzwungener Weltwert zählt bei der Flughilfe
  wie die Welt, Übergang zurück auf den Weltwert, alte Zonen ohne `force_world`, Codec und Netz, Zustände ersetzen sich,
  Editor (Entf, Schalter-Zyklus), Sperre behält den Zustand. Ohne Fix scheiterten 5 der 9 Tests.
- JUnit `zone.ZoneSourcesTest`: Quelle je Regel (`forcedBy`, `decider`), OSD-Text in beiden Sprachen, Listen mit
  Weltwert, `below`, Startwert im Editor, `ZoneView` (Umrisse je Modus, Stile bei Überlappung).
- JUnit `zone.client.ZoneViewConfigTest` (Befund FB1-8): neuer Standard, `TOOL` aus einer Datei ohne `version` wird einmal
  gehoben, danach gewähltes `TOOL` bleibt, `ALWAYS` bleibt, kaputte Werte fallen auf den Standard.
- JUnit `zone.ZonesOpenDocTest`: Diese Datei besteht die Zitat-Prüfung von `tools/public_export.py --check-only`
  (dieselben Muster, 120 Zeichen Fenster), mit Gegenprobe an der früheren Zeile zum Besitzer.
- Selbsttest `'-PpropwashSelftestOnly=zones-open:'` (beide Loader): Markierer setzt mit echtem Rechtsklick zwei Ecken auf
  einer Testplattform, prüft den Vorschau-Rahmen und den geöffneten Editor; Häkchen im Tab sperrt die Schwerkraft
  (gespeichert über „Speichern“); gesperrter Wert bleibt am Server und am Client gespeichert, wirkt nicht und ist im
  Editor ausgegraut, auch ein Paket mit geändertem Wert ändert ihn nicht; Rechte mit zwei Testspielern am Server und dem
  Gastgeber als Operator; Ansicht ohne Operator-Recht (Tab „Zonen“ nur mit der eigenen Zone samt „Bearbeiten“ und
  „Löschen“, Häkchen gesperrt, Markierer bei Regel aus abgelehnt). Schritt „eigener Tab „Zonen“ im K-Menü …“ (Befund
  OWNP-2): Tab „Welt-Regeln“ ohne Zonen-Knöpfe, aber mit Häkchen an jeder Regel; drei Testzonen (eigene auf der
  Testplattform, eine von Spieler A, eine Admin-Zone); `ZonesTab.open` hebt die Zone von A hervor; als Operator alle drei
  mit Besitzerzeile und allen Knöpfen; „Zeigen“ schließt das Menü und meldet Name, Entfernung und Pfeil; „Hinspringen“
  setzt den Spieler auf den Boden der eigenen Zone; Schleichen + Rechtsklick mit dem Markierer öffnet den Tab mit der Zone
  am Ort; „Löschen“ fragt beim ersten Klick nach und löscht beim zweiten; ohne Operator-Recht nur die eigene Zone ohne
  Besitzerzeile; Spieler ohne `/tp` springt nicht (Server); ohne Cheats verschwindet „Hinspringen“ und der Server lehnt
  ab. Schritt „„Hinspringen“ landet unter einer Zone hoch in der Luft auf dem Boden …“ (Befund AUTO18-1): von weit weg
  zu einer Zone 90 Blöcke über der Testplattform landet der Spieler auf der Plattform; zu einer Zone am Weltboden ohne
  freien Boden lehnt der Server ab und der Spieler bleibt stehen.
  Mit `-PpropwashScreenshots=true` (Fabric): `zones_open_marker`, `zones_open_editor_locked`, `zones_open_world_rules`
  (ohne Zonen-Liste), je mit `_de`; den Tab „Zonen“ zeigen `configurator_zones` (`bildschirmfotos: Zonen`) und
  `configurator_zones_narrow_{en,de}` (`ui-scale: Zonenname`); danach prüft „options.txt behält nach den deutschen Bildschirmfotos die Sprache des Laufs“, dass in
  `options.txt` dieselbe Sprache steht wie im Spiel. Die Szenen blenden die Anleitung nur im Speicher aus
  (`Tutorial.setStep` speichert `options.txt` und hinterließ so `lang:de_de` für den nächsten Lauf).
- Selbsttest-Schritte zu Befund PATTY-1 (`zone.client.ZoneLayersSelfTests`, Gruppe `zones-open:`): „Zonen-Editor:
  Mausrad über dem Menü scrollt …“ (GUI 427 × 240, 40 Rasten bis zur untersten Regel und zurück, kein Wert ändert
  sich, Klick ans Ende des Scrollbalkens); „Zonen-Umrisse nur mit Zonen-Markierer oder Kurs-Werkzeug …“ (zwei
  verschachtelte Zonen auf einem eigenen Testplatz: ohne Werkzeug 0 Umrisse, mit Markierer und Kurs-Werkzeug 2, innere
  Zone hervorgehoben, äußere nur Kanten, „Zonen anzeigen“ im Tab umschalten und speichern, „Immer“ ohne Werkzeug 2);
  „Zonen-Umrisse im Flug …“ (Befund FB1-8: echte `zones.json` aus rc.3 mit `TOOL` wird gehoben, danach gewähltes `TOOL`
  bleibt; zwei überlappende Testzonen vor der Drohne: FPV beide mit Umriss und nur Kanten, F1 keine, „Nur mit Werkzeug“ im
  Flug keine, Sichtflug beide, zu Fuß ohne Werkzeug nach dem Flug keine; Bild `zones_open_flight_fpv`);
  „verschachtelte Zonen: „Weltwert erzwingen“ …“ (Server und Client: Schwerkraft wie Welt, Akku aus der äußeren Zone;
  OSD-Text nennt beide Quellen; Editor zeigt „Welt: 100 %“ und „Darunter: Unendlich“; Entf und „Übernehmen“ speichern
  den Weltwert für den Akku; `/propwash zone set … wind world`). Ohne Fix scheiterten die ersten beiden Schritte.
  Bildschirmfotos `zones_open_editor_scroll` und `zones_open_nested` (je mit `_de`); `zones_border` entsteht mit
  „Zonen anzeigen: Immer“.
- Beitrittstest mit echtem Client auf dem dedizierten Server (`PROPWASH_JOINTEST_ZONES_OPEN=1` an beiden Prozessen):
  Spieler A (der Client) legt eine Zone an und macht sie rennfrei, Spieler B (am Server simuliert) darf sie weder ändern
  noch löschen, ein Operator (simuliert) ändert sie, der Besitzer bleibt A; danach sperrt der Server die Schwerkraft für
  Zonen, A sieht den Wert gespeichert und ausgegraut und löscht seine Zone. Dazu (Befund OWNP-2): Der Server legt eine
  Admin-Zone und eine Zone von B an; A listet nur seine eigene Zone, sieht kein „Hinspringen“ (kein `/tp`), und der
  Server lehnt sein Paket `zone_jump` ab. Erwartung:
  `joined|zones_open_creatable=true|zones_open_admin=false|zones_open_owned=self|zones_open_race_legal=self|zones_open_other=denied|zones_open_operator=changed|zones_open_listed=own|zones_open_jump_button=hidden|zones_open_jump=denied|zones_open_locked=kept|zones_open_greyed=true|zones_open_deleted=own`.
- Bestehende Prüfungen: Der Server-Rauchtest Zonen prüft die Ablehnung eines Gasts jetzt bei ausgeschalteter Regel
  „Spieler dürfen Zonen anlegen“; im Beitrittstest Zonen versucht der Gast, eine Admin-Zone zu ändern (Erwartung
  unverändert `zones_guest_edit=denied`).

## 8. Englische Kernsätze (Website)

- Every player can now create zones with the new Zone Marker: right-click two corners, watch the green preview frame,
  then the zone editor opens. Crafted from glowstone dust, paper and a stick.
- Zones belong to the player who made them. Owners and operators can change or delete them; other players can't.
  Servers can switch this off with the world rule "Players may create zones".
- New "Zones" tab in the K menu: players see and manage their own zones, operators see every zone with its owner. Show
  a zone in the world (highlighted frame, direction and distance), teleport there (if you may use /tp), edit or delete
  it, or create a new zone right where you stand. Sneak + right-click with the Zone Marker opens the tab.
- Each world rule now has a "Zones" box in the World Rules tab that decides whether zones may change it. Locked rules
  keep their saved zone values but don't apply them, and the zone editor shows them greyed out.
- Old worlds load unchanged: existing zones become admin zones.
- Nested zones: every rule in a zone is "as below" (takes the value of the zone underneath or the world), "world value"
  (always the world rule, even inside another zone) or its own value. The zone notice in the OSD names the zone each
  value comes from.
- The zone editor scrolls with the mouse wheel and shows a scroll bar.
- Zone outlines only show while you hold the Zone Marker or the Course Tool; the new setting "Show zones: Always" in
  the Zones tab brings them back for good. Overlapping zones only draw their edges, the zone you are in is highlighted.

## 9. Verschachtelte Zonen, Zonen-Editor und Anzeige (Playtest 29.10., Befund PATTY-1)

Im Playtest blieb in einer inneren Zone mit Schwerkraft „wie Welt“ die Schwerelosigkeit der äußeren Zone bestehen:
„wie Welt“ hieß bis dahin nur „nicht gesetzt“, und je Regel gilt die höchste Zone, die die Regel setzt. Dazu fand man
die unteren Zeilen des Zonen-Editors nur zufällig (das Mausrad änderte Werte statt zu scrollen), und die Umrisse
überlappender Zonen verdeckten einander.

### 9.1 Drei Zustände je Regel

| Zustand | im Editor | Wirkung | gespeichert |
|---|---|---|---|
| wie darunter | blass „Darunter: 0 %“ – Wert der Zonen unter dieser Zone an ihrer Mitte, sonst der Welt-Regel | Wert der nächsten Zone darunter, die die Regel setzt, sonst die Welt-Regel | nichts (wie bisher „nicht gesetzt“) |
| Weltwert erzwingen | „Welt: 100 %“ | immer die Welt-Regel, auch wenn eine Zone darunter einen eigenen Wert hat; folgt Änderungen der Welt-Regel | Feld `force_world` (Liste der Regel-Namen) |
| eigener Wert | der Wert | wie bisher | Feld `overrides` |

- Je Regel entscheidet die Zone mit der höchsten Priorität am Ort, die die Regel setzt – mit eigenem Wert oder mit
  „Weltwert erzwingen“; bei gleicher Priorität die neuere.
- Bedienung: ‹ ›, Pfeiltasten und das Mausrad im HUD setzen einen eigenen Wert, ausgehend vom bisher geltenden Wert
  (darunter bzw. Welt). Entf/Rücktaste und im Fenster mit Mauszeiger ein Rechtsklick: eigener Wert → „wie darunter“,
  „wie darunter“ ↔ „Weltwert erzwingen“. Schalter laufen darunter → an → aus → Welt → darunter.
- Befehl: `/propwash zone set <zone> <regel> world` erzwingt den Weltwert, `reset` setzt auf „wie darunter“.
- Flughilfe: Ein erzwungener Weltwert zählt wie die Welt (`WORLD_PHYSICS`, wenn die Welt-Regel vom Standard abweicht),
  auch in einer rennfreien Zone; eine rennfreie Zone macht weiterhin nur ihre eigenen Werte fair. Schwerkraft und Wind
  gleiten über die Übergangszeit der Zone auf den Weltwert.
- Freigabe (§4): Bei einer gesperrten Regel bleibt auch „Weltwert erzwingen“ gespeichert, wirkt nicht und steht im
  Editor ausgegraut.
- Anzeige: Tab, Tooltip, `list` und `info` nennen „Schwerkraft: Weltwert“. Der Hinweis beim Einfliegen (OSD bzw.
  Kasten) nennt die Werte der obersten Zone, erzwungene Weltwerte mit Wert und „(Welt)“, und je weitere Zone, die etwas
  liefert, „aus „Kurs“: Akkulaufzeit Unendlich“; die Art („zählt als Flughilfe“, „rennfrei“, „Physik wie Welt“) folgt
  aus allen Quellen.
- Alte Welten: Zonen ohne `force_world` behalten „wie darunter“. Ohne erzwungene Weltwerte bleiben die Weltdaten
  unverändert. Das Zonen-Paket (`propwash:zones`, `propwash:zone_edit`) hängt die Liste hinter dem Besitzer an;
  Protokoll bleibt 17.

### 9.2 Zonen-Editor scrollt

Mit Mauszeiger (Taste des Gate-Editors gehalten oder aus dem Tab „Zonen“ geöffnet) scrollt das Mausrad über dem Menü
die Liste; rechts steht ein Scrollbalken, den man ziehen oder anklicken kann. Werte ändern ‹ ›, die Pfeiltasten und das
Mausrad außerhalb des Menüs (gewählte Zeile). Die Liste folgt der Auswahl nur, wenn die Auswahl wechselt. Ohne
Mauszeiger bleibt es beim Gate-Editor-Muster (Mausrad: Wert, Umschalt + Mausrad: Zeile); der Scrollbalken zeigt dort,
dass weitere Zeilen folgen.

### 9.3 Zonen anzeigen

- „Nur mit Werkzeug“: Zonen-Umrisse erscheinen, solange man den Zonen-Markierer oder das Kurs-Werkzeug in einer Hand
  hält, während man eine Zone bearbeitet (Entwurf und Vorschau-Rahmen des Markierers) und für die Zone nach „Zeigen“.
- Standard „Mit Werkzeug oder im Flug“ (Befund FB1-8): wie „Nur mit Werkzeug“, dazu solange eine Pilotensitzung läuft,
  in FPV und im Sichtflug (`PilotSession#isActive`). Beim Fliegen sieht man so, wo eine Zone mit anderen Regeln anfängt;
  zu Fuß ohne Werkzeug bleiben die Umrisse aus. Zuschauen zählt nicht als Pilotensitzung.
- „Immer“ zeigt sie die ganze Zeit.
- Gespeichert in `config/propwash/zones.json` (Feld `show`: `TOOL`, `TOOL_OR_FLIGHT` oder `ALWAYS`; Feld `version`: 2).
  Eine Datei ohne `version` stammt aus rc.3 oder früher, dort war `TOOL` der Standard: Beim ersten Laden wird es einmalig
  auf `TOOL_OR_FLIGHT` gehoben (`ZoneViewConfig#migrate`), `ALWAYS` bleibt. Danach gewähltes „Nur mit Werkzeug“ bleibt.
- Überlappende Zonen (Quader schneiden sich, Zylinder über ihren Quader) zeichnen nur ihre Kanten, dezenter und ohne
  Fläche und Raster. Hervorgehoben (kräftigere Kanten, Fläche, Raster) ist die Zone, in der die Kamera ist (höchste
  Priorität), bzw. die gezeigte Zone; neben einem Entwurf zeichnen andere Zonen nur Kanten. Zonen ohne Überlappung sehen
  aus wie bisher.
