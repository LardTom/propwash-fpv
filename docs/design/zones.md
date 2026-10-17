# Zonen – eigene Simulationswerte für einen Bereich der Welt (Propwash 0.5.2)

Auftrag des Besitzers (12.10.2026), sinngemäß: Zonen mit eigenen Simulationseinstellungen – eine örtliche Abweichung
von den Welt-Regeln, die nur für Drohnen in der Zone gilt (etwa unendlicher Akku auf einem Übungsplatz oder
Schwerelos-Passagen in einem Rennkurs). Jede Zone hat eigene Werte. Es gibt „rennfreie“ Zonen: Runden durch sie zählen
nicht als Flughilfe, damit Schwerelos-Passagen faire Streckenelemente werden. Nicht rennfreie Zonen mit veränderter
Physik zählen als Flughilfe, und die Gründe der Flughilfe nennen die Zone.

## 1. Was eine Zone ist

| Feld | Bedeutung | Grenzen |
|---|---|---|
| Name | frei wählbar, je Dimension eindeutig (ohne Groß-/Kleinschreibung) | 1–32 Zeichen, ohne Steuerzeichen und `§` |
| Form | Quader oder Zylinder (senkrecht, Kreis aus der kleineren waagerechten Seite) | je Richtung 1–1024 Blöcke, innerhalb der Welt (y −4096 … 4096) |
| Priorität | entscheidet bei Überlappung | −99 … 99, Standard 0 |
| Rennfrei | Runden durch die Zone zählen nicht als Flughilfe | Standard aus |
| Übergang | Zeit, in der Schwerkraft und Wind beim Ein- und Ausfliegen auf die neuen Werte gleiten | 0–5 s, Standard 0,5 s |
| Werte | eigene Werte für die Simulationsregeln; was die Zone nicht setzt, kommt aus der Welt-Regel | die elf Regeln aus `PhysicsRules.SPECS` mit ihren Grenzen |

Eine Zone ändert die **Simulationsregeln** (Schwerkraft, Propwash, Bodeneffekt, Hardware-Grenzen, Wind, Turbulenz,
Akkulaufzeit, Spannungseinbruch, Crash-Toleranz, Entschärfen bei Crash, Prop-Treffer) – genau die Regeln, die die Sim
rechnet und die als Flughilfe zählen. Höchstens 128 Zonen je Dimension (Bearbeiten, Befehle und Laden der Weltdaten
achten darauf; eine von Hand vergrößerte `zones.dat` behält beim Laden die 128 mit den kleinsten Nummern je Dimension,
sonst könnte die Liste das Sync-Paket sprengen). Zonen gehören zur Welt (Weltdaten
`data/propwash/zones.dat`), jede trägt ihre Dimension.

## 2. Welche Werte gelten

- **Je Einstellung die höchste Priorität:** Für jede Regel gilt die Zone mit der höchsten Priorität, die den Punkt
  enthält und die Regel setzt; bei gleicher Priorität die neuere (höhere Nummer). Setzt keine Zone die Regel, gilt
  die Welt-Regel. So kann ein Rennkurs als große Zone „Akku unendlich“ setzen und eine kleine Passage darin mit höherer
  Priorität nur die Schwerkraft auf 0 % – die Passage erbt den Akku vom Kurs.
- **Rand:** Ein Punkt auf dem Rand zählt als drinnen. Maßgeblich ist der Schwerpunkt der Drohne (Position der
  Entity), am Client wie am Server.
- **Übergang:** Schwerkraft und Wind gleiten über die Übergangszeit der Zone (lineares Gewicht je Zone, mehrere Zonen
  nach Priorität übereinander), alle Schalter und die übrigen Werte wechseln sofort am Rand. Startet eine Sitzung in
  einer Zone, gilt ihr Wert sofort. Eine gelöschte Zone blendet über ihre Übergangszeit aus statt zu springen.
- **Akku läuft weiter:** Verlässt die Drohne eine Zone mit unendlichem Akku (oder wird die Regel wieder endlich),
  fährt die Ladung bei genau dem Stand fort, mit dem sie hineinflog (`DroneSim#keepBatteryCharge`). Vorher hätte der
  Verbrauch während der unendlichen Zeit beim Verlassen auf einmal gezählt; das galt auch für die Welt-Regel.
  Andere Akkulaufzeiten (z. B. 300 %) ändern nur, wie schnell es ab jetzt weitergeht.

## 3. Flughilfe

| Stelle in der Zone | Zählt als Flughilfe? | Grund |
|---|---|---|
| Rennfreie Zone, Wert vom Standard verschieden | nein | – |
| Nicht rennfreie Zone, Wert vom Standard verschieden | ja | neuer Grund `ZONE_PHYSICS` „Zone „Übungsplatz“ verändert die Physik: Akkulaufzeit Unendlich statt 100 %“ |
| Zone setzt einen Wert auf den Standard | nein | – |
| Welt-Regel verändert, Zone setzt diese Regel nicht | ja | wie bisher `WORLD_PHYSICS` mit Regel und Wert |
| Welt-Regel verändert, (rennfreie) Zone setzt sie | nur nach Zone | eine rennfreie Zone macht die Welt-Regel dort fair |
| Im Übergang (Schwerkraft, Wind): nicht rennfreie Zone blendet aus oder ein | ja, solange ihr Gewicht über 0 liegt | `ZONE_PHYSICS` mit dem Wert der Zone, auch nach dem Verlassen oder Löschen und beim Einflug in eine rennfreie Zone |
| Im Übergang: rennfreie Zone blendet über veränderte Welt-Regel ein | ja, bis die Zone voll wirkt | `WORLD_PHYSICS` mit dem Wert der Welt |

Der Server ist maßgeblich (`LapAssist.SERVER_REASONS` enthält `ZONE_PHYSICS`): Bei jeder Bewegung rechnet er die Zonen
an der gemeldeten Position mit einem eigenen `ZoneField` je Pilot (gleicher Übergang wie im Client, Zeit aus den
Bewegungen; nach mehr als 5 s ohne Bewegung oder mit einer anderen Drohne beginnt er ohne Übergang) und merkt die
Gründe mit Zeitstempel (`AssistTimeline`). Wer zählt, rechnet `ZoneBlend#details`: je Regel mit Übergang jede Zone
mit Gewicht über 0, die nicht von einer voll wirkenden Zone darüber verdeckt ist, und die Welt, solange keine Zone sie
voll verdeckt – also genau die Anteile, aus denen die Sim ihren Wert mischt. Die Meldung „Physik verändert“ im Zustandspaket berücksichtigt rennfreie Zonen, sonst zählte der
Server die Meldung als Grund „Physik deines Clients wich ab“. Details tragen die Regel, den Wert der Zone und als Bezug
die Nummer der Zone; der Text sucht den Namen (Server: Weltdaten, Client: synchronisierte Liste) und zeigt sonst
„Zone #n“ (gelöschte Zone im Tooltip einer alten Zeit).

## 4. Bedienung

- **Tab „Welt-Regeln“**, Abschnitt „Zonen“ oben direkt unter den Voreinstellungen und vor den Regel-Gruppen (ohne
  Scrollen sichtbar, auch bei größter GUI-Skala und in der kleinsten GUI-Fläche 320 × 240, etwa Fenster 640 × 480 mit
  GUI-Skala 2; vorher stand er unter rund 30 Regeln ganz unten und wurde im Spieltest nicht gefunden): „Neue Zone hier“
  (mit Recht) rechts in der Zeile der Überschrift, deren Trennlinie davor endet – so braucht der Knopf keine eigene
  Zeile, und Überschrift und Knopf passen auch auf Deutsch in 320 × 240 (die Ansicht endet immer an einem Scroll-Stopp;
  der Abschnitt setzt Stopps an der Überschrift, vor und nach jeder Zone und nach dem Hinweis ohne Zonen). Darunter alle
  Zonen der Dimension mit Farbe, Name, Art (rennfrei / zählt als Flughilfe / Physik wie Welt), Form, Größe, Lage,
  Priorität und Werten, Tooltip mit allen Angaben, „Bearbeiten“ je Zone (mit Recht), darunter die Erklärung; ohne Recht nur lesbar. Die Einstellungssuche
  findet „Neue Zone hier“ mit „Zone“ und „Zonen“ und jede Zone über ihren Namen („Zone „…“ bearbeiten“).
- **Editor wie „Gate anpassen“:** Panel links, die Zone als grüner Entwurf in der Welt, die alte Form ausgeblendet.
  Mausrad ändert den Wert der gewählten Zeile, Umschalt + Mausrad wählt die Zeile, Strg fein, Pfeiltasten gehen auch,
  Entf/Rücktaste setzt eine Zeile zurück (Werte zurück auf „Welt“), Enter oder Rechtsklick übernimmt, Esc bricht ab.
  Gehalten (Taste des Gate-Editors, Standard linke Alt-Taste) oder aus dem Tab geöffnet erscheint der Mauszeiger mit
  Namensfeld, ‹ › je Zeile, Rechtsklick = zurück auf Welt-Wert, Knöpfen „Übernehmen“, „Abbrechen“, „Mitte hierher“ und
  „Löschen“ (zweimal klicken). Laufen geht weiter. Schritte: Lage 1 Block (Strg ½), Größe 2 Blöcke – ein Block je
  Seite, die Mitte bleibt – (Strg 1), Prozentwerte 10 (Strg die Schrittweite der Regel), Schalter Welt → an → aus.
  Neue Zonen: 16 × 8 × 16 um den Spieler, Name „Zone n“. Der Client prüft vor dem Senden (Name frei, Größe), der
  Server noch einmal.
- **Befehle** `/propwash zone`: `list` und `info <name>` für alle; mit Recht `create <name> box <von> <bis>`,
  `create <name> cylinder <mitte> <radius> <höhe>`, `set <name> <regel> <wert>`, `reset <name> <regel>`,
  `priority`, `racelegal`, `transition <sekunden>`, `rename`, `move <name> <von> <bis>`, `delete`. Namen mit
  Leerzeichen in Anführungszeichen; Vorschläge für Namen, Regeln und Werte.
- **Recht:** dasselbe wie für die Welt-Regeln (`WorldRules#mayEdit`: Gastgeber, Operatoren oder wer `/gamerule` für
  eine Propwash-Regel ausführen darf). Jede Änderung geht als Chatzeile an alle Spieler und ins Server-Log.

## 5. Darstellung

- **Rand in der Welt** (`ZoneRenderer`, Leucht-Geometrie wie die Trainings-Hologramme): dünne Kanten (Quader: zwölf
  Kanten, Zylinder: zwei Kreise und acht Stäbe) bis 160 Blöcke, eine kaum sichtbare Fläche innerhalb von 24 Blöcken
  und ein Blockraster auf der nahen Wand in einem Kreis von 7 Blöcken um den Betrachter, das mit dem Abstand zur Wand
  (bis 14 Blöcke) ausblendet. Farben: rennfrei türkis, Flughilfe bernstein, ohne Physik-Änderung grau, Entwurf grün.
  Mit ausgeblendetem HUD (F1) unsichtbar.
- **Hinweis beim Ein- und Ausfliegen** (`ZoneHud`): im FPV als OSD-Text ab Zeile 5 („ZONE ÜBUNGSPLATZ“, darunter Werte
  und „ZÄHLT ALS FLUGHILFE“/„RENNFREI“), im Sichtflug als Hinweis-Kasten im Propwash-Stil; 3,5 s, blendet aus. Lange
  Zeilen brechen um (im FPV innerhalb des OSD-Rasters ohne die äußeren Spalten, im Sichtflug innerhalb des Fensters),
  damit bei vielen Werten nichts über den Rand läuft und die Art der Zone immer zu lesen ist.
  Beim Verlassen „Zone … verlassen – Welt-Regeln gelten wieder“, beim Wechsel in eine andere Zone deren Name.

## 6. Technik

| Datei | Inhalt |
|---|---|
| `zone.Zone`, `ZoneBox`, `ZoneShape`, `ZoneLimits`, `ZoneCheck` | Daten, Codec, Prüfung (reines Java bis auf den DFU-Codec) |
| `zone.ZoneResolver`, `ZoneResolution` | Werte an einem Punkt, Quelle je Regel, Flughilfe-Details |
| `zone.ZoneBlend`, `ZoneField` | Übergang von Schwerkraft und Wind, Zustand je Flug, `SimTuning` daraus |
| `zone.ZoneDraft` | Zeilen und Schritte des Editors (reines Java, JUnit) |
| `zone.ZoneData` | Weltdaten (`SavedData`, server-weit, Zonen je Dimension) |
| `zone.ZoneServer`, `ZoneCommands`, `ZoneModule` | Sync, Änderungen mit Rechteprüfung, Befehle |
| `zone.ZonesS2C`, `ZoneEditC2S`, `ZoneWire` | Netz: `propwash:zones` (Liste der Dimension und Recht), `propwash:zone_edit` (Speichern/Löschen) |
| `zone.client.*` | Liste am Client, Rand, Hinweis, Editor (HUD, Fenster), Abschnitt im Tab |
| `mixin.zone.client` | Mausrad, Tasten und Benutzen/Angreifen während des Editors (wie beim Gate-Editor) |
| `drone.client.PilotSession` | rechnet je Bild `ZoneField` an der Drohne, setzt die Sim-Regeln bei Änderung |
| `rescue.ServerReturnFlight` | autonomer Heimflug rechnet mit denselben Zonen je Tick |
| `race.LapAssist`, `AssistDetail`, `AssistCheck`, `AssistWords` | Grund `ZONE_PHYSICS` |

Sync: Der Server schickt jedem Spieler die Zonen seiner Dimension und ob er sie ändern darf – beim Beitritt, beim
Dimensionswechsel, nach jeder Änderung und wenn sich das Recht ändert (geprüft jede Sekunde). Ohne Zonen rechnet die
Sim Bit für Bit wie bisher (JUnit `ZoneFieldTest`, Bewegungs-Goldens und Kalibrierung unverändert).

## 7. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Zonen ändern nur die elf Simulationsregeln | genau die Regeln, die die Sim rechnet und die als Flughilfe zählen; der Auftrag spricht von Simulationseinstellungen | auch Crash-Schaden, 3D-Modus, Reichweite (bräuchten je Modul eine Positionsprüfung am Server) |
| Je Einstellung die höchste Priorität, Rest erbt nach unten | Passagen in Kurs-Zonen müssen nur ändern, was anders ist | ganze Zone gewinnt (Passage müsste alle Werte des Kurses wiederholen) |
| Gleiche Priorität: neuere Zone gewinnt | eindeutig, ohne Namen zu vergleichen | Fehler bei gleicher Priorität |
| Übergang zeitlich je Zone (0–5 s), nur Schwerkraft und Wind | gleicht weich aus, egal wie schmal die Zone ist; Schalter haben keinen Zwischenwert | räumlicher Übergang am Rand (schmale Passagen erreichen den Wert nie, die Zone wirkt kleiner als ihr Rand) |
| Akku: Ladung beim Verlassen festhalten | „der Akku läuft weiter“; behebt denselben Sprung bei der Welt-Regel | Verbrauch in der unendlichen Zone nachträglich abziehen |
| Rennfrei schaltet nur die Zone frei, nicht die Welt-Regeln | die Welt bleibt fair oder nicht; die Zone macht nur ihre eigenen Werte fair | rennfreie Zone erklärt alles in ihr für sauber |
| Neuer Grund `ZONE_PHYSICS` mit Zonennummer als Bezug | Gründe nennen die Zone, alte Texte bleiben; Name wird beim Anzeigen gesucht (Umbenennen wirkt auch rückwirkend) | Name im Detail speichern (Detail-Text ist auf Kleinbuchstaben gekürzt) |
| Recht wie Welt-Regeln | Auftrag; derselbe Personenkreis ändert die Physik | eigenes Recht oder Operator-Stufe |
| Editor wie „Gate anpassen“ mit derselben Mauszeiger-Taste | gleiche Handgriffe; keine weitere Taste | eigenes Werkzeug-Item (neues Item, Modell, Rezept – mehr als nötig) |
| Zonen im Tab nur der aktuellen Dimension | was man sieht und bearbeiten kann | alle Dimensionen (Lage ohne Bezug) |
| Netzprotokoll bleibt 16 | 0.5.2 ist unveröffentlicht und erhöht schon auf 16 | 17 |
| Parts-API unverändert | Addons brauchen Zonen nicht | Lesezugriff in der API |
| Heimflug ohne Pilot rechnet mit Zonen je Tick | dieselbe Physik wie mit Pilot | nur Welt-Regeln beim Start |

## 8. Tests

- JUnit: `ZoneTest` (Form, Rand, Zylinder, Namen, Werte, Codec und Netzformat, Prüfungen), `ZoneResolverTest`
  (Priorität je Einstellung, gleiche Priorität, Dimensionen, rennfrei, Gründe mit Zonennamen, Client gleich Server),
  `ZoneFieldTest` (ohne Zonen bitgleich zur Welt, Start in der Zone, Übergang hin und zurück, Übergang 0, gelöschte
  Zone, Überlappung), `ZoneDraftTest` (Editor-Schritte), `ZoneBatteryTest` (Ladung läuft nach unendlichem Akku weiter).
- Selbsttest (`zonen:`, beide Loader): Befehle legen an, ändern, benennen um und löschen, Weltdaten verlustfrei, Sync
  zum Client; Flug im FPV: Übergang auf 0 % Schwerkraft mit Zwischenwert, rennfrei ohne Flughilfe, OSD-Hinweis, ohne
  Rennfreigabe Grund mit Zonennamen am Client und am Server, Schwerkraft gleitet zurück, unendlicher Akku hält die
  Ladung, nach dem Löschen kein Sprung und weiterer Verbrauch; Editor legt an (Breite, Form, Wert, Name), lehnt
  doppelte Namen ab, löscht nach Rückfrage.
- Server-Rauchtest (dedizierter Server): Gast ohne Recht abgelehnt (Paket, 20 Wiederholungen, Befehl), Operator legt an,
  doppelter Name und zu große Zone abgelehnt, Sync je Dimension, Weltdaten verlustfrei, Server-Gründe an drei
  Drohnen (rennfreier Tunnel sauber, Übungsplatz mit Zonennamen, außerhalb sauber), Tunnel ohne Rennfreigabe zählt,
  nach Entzug der Rechte kein Löschen.
- Beitrittstest mit echtem Client auf dem dedizierten Server (`PROPWASH_JOINTEST_ZONES=1` an beiden Prozessen,
  dazu `PROPWASH_JOINTEST_PILOT=1` am Server): ein zweiter Spieler mit Recht (simuliert am Server) legt um den
  Piloten eine rennfreie Schwerelos-Zone und einen Übungsplatz an, schaltet die Zone nach 20 s auf Flughilfe und
  löscht beide nach 30 s; der Client prüft Empfang, nur lesbar, abgelehnte eigene Änderung, Schwerkraft 0 in seiner
  Sim, sauber/Flughilfe mit Zonennamen und die Rückkehr zur Welt-Physik. Erwartung:
  `joined|zones_synced=2|zones_editable=false|zones_guest_edit=denied|zone_gravity=0|zone_race_legal_assist=clean|zone_assist=named|zone_left=world|zone_after_assist=clean`.
- Bildschirmfotos (Fabric, englisch und deutsch): `zones_border`, `zones_editor_hud`, `zones_editor_cursor`,
  `configurator_world_rules_zones`, `zones_fpv_notice`.

Stand 14.10.2026: JUnit vollständig grün (2849 Tests, davon 31 für Zonen und Akku), Selbsttest `zonen:` auf Fabric und
NeoForge grün, Bildschirmfotos englisch und deutsch, Server-Rauchtest Fabric und NeoForge grün, Beitrittstest mit
echtem Client auf dem dedizierten Server (Fabric) mit allen Erwartungen erfüllt.
