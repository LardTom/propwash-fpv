# Wasserdichte Drohnen, Turtle-Modus und Abschalten nach Absturz (0.5.2)

Drei Wünsche aus der Community, die zusammengehören: Eine Drohne landet im Teich oder kopfüber im Gras – was nun?

- **Wasserdicht:** Schutzbeschichtung als Spray (`propwash:waterproof_spray`) oder Welt-Regel; beschichtete Drohnen
  fliegen im Wasser weiter, alle anderen bekommen wie bisher einen Kurzschluss.
- **Turtle-Modus:** wie „Flip over after crash“ in Betaflight dreht die Drohne sich mit den eigenen Props zurück auf
  die Füße. Das bisherige „Drohne aufrichten“ (sofort, ohne Physik) bleibt als schnelle Hilfe daneben bestehen.
- **Abschalten nach Absturz:** die Welt-Regel `propwash:crash_disarm` heißt jetzt „Nach Absturz abschalten“; sie steht
  nur im Tab „Welt-Regeln“, die Suche und die Flugschule verweisen dorthin.

## 1. Wasserschutz-Spray

| | |
|---|---|
| Item | `propwash:waterproof_spray`, Haltbarkeit 100, jede Beschichtung kostet 10 (`drone.coating.CoatingRules`), also zehn Drohnen je Dose; im Kreativmodus kostet der Rechtsklick nichts (Vanilla `hasInfiniteMaterials`) |
| Rezept | geformt `·N·` / `NHN` / `NRN` mit Eisennugget, Honigwabe, Harzklumpen (`tools/data/survival.py`, freigeschaltet wie alle Propwash-Rezepte) |
| Anwenden | Rechtsklick auf eine platzierte Drohne (`DroneInteraction.Action.COAT`, nicht während ein anderer sie fliegt; Schleichen öffnet weiter das Wartungsmenü) oder Drohne + Dose im Handwerksraster (Sonderrezept `propwash:waterproof_coating`, die Dose bleibt mit 10 Haltbarkeit weniger liegen) |
| Rückmeldung | Wachs-Geräusch und Wachs-Partikel von Vanilla, Meldung mit den restlichen Drohnen; Tooltip der Drohne „Wasserdicht beschichtet“, Tooltip der Dose mit Shift-Hilfe |
| Speicher | Data Component `propwash:waterproof_coating` (Ganzzahl) an Drohnen-Item und Stack-Teil, Entity-Feld `Waterproof` (synchronisiert) |

Die Beschichtung haftet an der **Elektronik**: Der gespeicherte Wert ist die Signatur aus den Teile-IDs von Rahmen,
Stack, Motor, Videomodul und Zubehör (sortiert). Beim Zerlegen in der Werkbank wandert sie mit dem Stack-Teil (wie
Tuning und Bindung); beim Zusammenbauen gilt sie nur, wenn die neue Drohne dieselbe Signatur hat. Props und Akku zählen
nicht – die tauscht man auch im Feld. Wer Stack, Motoren, Video, Rahmen oder Zubehör wechselt, muss neu sprühen.
Teile aus Addons gehen mit ihrer ID automatisch ein. Kaputte Teile im Zerlegen-Feld verlieren die Beschichtung mit dem
Teil.

Welt-Regel `propwash:waterproof_drones` (Gruppe Spiel, Standard aus, in der Voreinstellung *Arcade* an): jede Drohne
gilt als beschichtet. Für die Physik zählt `Entity.waterproof || Regel`, am Client (`PilotSession`) wie am Server
(GPS Rescue ohne Pilot, `ServerReturnFlight`).

## 2. Wasser im Flugmodell

Nur für wasserdichte Drohnen; ohne Beschichtung ist alles bit-gleich wie zuvor (Kurzschluss nach 0,3 s im Wasser,
Scharfschalten im Wasser gesperrt, Körperwiderstand × 400). Lava schaltet jede Drohne ab.

- Der Client misst je Frame die **Höhe der Wasseroberfläche** über dem Schwerpunkt (`drone.WaterSurface`, bis 16 Blöcke
  nach oben, Füllhöhe der obersten Wasserzelle; liegt der Schwerpunkt über Wasser, zählt die Zelle darunter) und gibt
  sie als `SimEnvironment.waterSurface` an die Simulation.
- **Props im Wasser** (`DroneSim.updateSubmersion`): je Rotor ein Benetzungsgrad 0…1 aus der Tiefe der Motorposition,
  weich über einen Propradius. Die Last auf dem Motor steigt mit der Dichte (Wasser/Luft ≈ 816, `PowerSystem.fluidLoad`),
  der Schub nur mit 12 % davon (`WATER_PROP_EFFICIENCY`: Kavitation, Ansaugen von Luft an der Oberfläche, verwirbeltes
  Wasser). Die Motoren laufen dadurch mit etwa einem Sechzehntel der Drehzahl, ziehen viel Strom (Strombegrenzung des ESC,
  Wärme, schneller leerer Akku) und schieben eine 5"-Drohne mit Vollgas etwa 1,4 m/s durchs Wasser.
- **Körper:** Eintauchgrad aus Oberfläche und Höhe der Hülle; Widerstand und Drehdämpfung steigen damit bis auf das
  400-Fache, **Auftrieb** = Eintauchgrad × Gewicht / 1,8 (mittlere Dichte einer FPV-Drohne 1,8 g/cm³: Akku, Motoren,
  Carbon) – unbeschichtet wie beschichtet sinkt eine Drohne also langsam. Der Bodeneffekt ist unter Wasser aus.
- Scharfschalten, Fliegen und Entschärfen gehen im Wasser; der Blocker „IM WASSER“ gilt nur noch ohne Beschichtung.

## 3. Turtle-Modus

**Bedienung:** Schalterfunktion `TURTLE` (Tab „Modi“, Zeile nach „Aufrichten“) oder Taste „Turtle-Modus an/aus“
(Ziffernblock 8, `KeyDefaults.TURTLE`). Ablauf wie in Betaflight: Turtle-Modus an, scharf schalten, Roll, Nick oder Gier
zu der Seite drücken, zu der die Drohne kippen soll. Ausschalten des Turtle-Modus entschärft (`DisarmReason.TURTLE_OFF`),
danach muss der Scharfschalter einmal umgelegt werden. Mit der Taste geht der Turtle-Modus nach dem Entschärfen von
selbst aus (`TurtleSelector.releaseKey`), damit Tastatur-Piloten danach normal scharf schalten. OSD und Funk-HUD zeigen
„TURTLE“, die Blackbox setzt das Betaflight-Modusbit `FLIP_OVER_AFTER_CRASH`.

**Scharfschalten** (`Arming.conditionsTurtle`): ohne Scharf-Winkel (kopfüber ist der Zweck), aber nur, wenn die Drohne
liegt (Bodenkontakt oder Ruhe, unter 1 m/s); in der Luft meldet sie „Turtle-Modus geht nur am Boden“
(`ArmBlocker.TURTLE_AIRBORNE`). Im Flug normal scharf geschaltet ändert der Schalter nichts – wie in Betaflight zählt er
nur beim Scharfschalten. Gas muss unten sein (im 3D-Modus in der Mitte).

**Motoren** (`sim.TurtleMix`): Der Flugcontroller regelt nicht, die Motoren laufen gesteuert rückwärts. Die stärkste
Achse gewinnt (Gier vor Roll/Nick); liegen Roll und Nick weniger als 30° von der Diagonalen, läuft nur der eine Motor
dieser Ecke. Es drehen die Motoren, die bei diesem Ausschlag im normalen Flug langsamer würden – Roll rechts dreht die
Drohne also im Kamerabild wie ein Rollen nach rechts. Leistung = Ausschlag mit Betaflight-Expo 35 % oder, wenn höher, das
Gas (Gas treibt die gewählte Seite; ohne Ausschlag über 10 % läuft nichts). Leerlauf wie im Tuning.

**Schutz gegen Überschlagen** (`TurtleMix.governor`): Die Leistung sinkt linear zwischen 135° und 75° Neigung
(jenseits des Kipppunkts zieht die Schwerkraft), mit der Drehrate ab 4 rad/s (aus bei 8 rad/s) und mit der
Steiggeschwindigkeit ab 0,25 m/s (aus bei 0,75 m/s). Unter 60° Neigung stoppen die Motoren und bleiben bis zum
Entschärfen aus. So dreht auch die Tastatur mit vollem Ausschlag eine Drohne in 0,4–0,6 s um, ohne sie zu werfen.

**Boden** (`sim.HullContact`): Solange die Drohne im Turtle-Modus scharf ist, stützt sie sich mit den zwölf Hüllpunkten
(Prop-Spitzen, Motoren, Akkufläche, Unterseite) federnd auf den Boden, mit Reibung und etwas Rolldämpfung; die
gewöhnliche Bodenbehandlung (feste Box, Aufrichtmoment) und Boden-Stöße der Kollision ruhen so lange. Damit kippt die
Drohne über die Kante der anderen Seite, das Gewicht wirkt am Schwerpunkt, und schwache Drohnen schaffen es langsamer.
Beim Scharfschalten hebt die Simulation sie auf die tiefste Hüllspitze, falls die Hülle tiefer reicht als die
Kollisionsbox. Prop-Treffer am Boden zählen im Turtle-Modus nicht.

## 4. Abschalten nach Absturz finden

- Welt-Regel `propwash:crash_disarm` heißt „Nach Absturz abschalten“ / „Disarm on crash“, die Beschreibung nennt den
  Turtle-Modus.
- Die Regel steht nur im Tab „Welt-Regeln“ (ändern darf dort, wer `/gamerule` darf). Eine zusätzliche Zeile im Tab
  „Konfiguration“ gab es in Vorabständen von 0.5.2; sie ist entfallen, weil eine Welt-Regel an genau einen Ort gehört.
- Suche: Synonymgruppen für Turtle-Modus, Absturz-Abschaltung und Wasserdicht (`search/synonyms.txt`; „turtle“ gehört
  nicht mehr zum 3D-Modus). „crash disarm“, „disarm on crash“ oder „absturz“ finden die Regel im Tab „Welt-Regeln“ und
  springen dorthin (Selbsttest `ui: Nach Absturz abschalten nur im Tab Welt-Regeln, die Suche führt dorthin`).
- Flugschule, Szene „Absturz, Reichweite, Signal“: Schritt „Absturz“ nennt die Regel, neuer Schritt „Turtle-Modus“
  zwischen „Aufrichten und weiter“ und „Scharf schalten in der Luft“.
- Tab „Modi“: die Erklärung des Turtle-Modus verweist auf die Regel.

## 5. Scharf-Winkel

Der Wunsch nach einem „maximalen Scharf-Winkel“ ist mit 0.5.0 erfüllt: Tab „Konfiguration“, 0–180° (Standard 25°,
180° = in jeder Lage, auch in der Luft), Tuning-Schlüssel `pid.armMaxAngleDeg`; die Meldung „mehr als N° gekippt“ nennt
den Tab, die Suche findet ihn unter „arm angle“, „small angle“ und „Scharf-Winkel“. Ein zweiter Regler im Tab „PID“ würde
denselben Wert an zwei Stellen zeigen; Betaflight führt `small_angle` ebenfalls unter „Configuration“. Der Turtle-Modus
umgeht den Winkel bewusst.

## 6. Tests

- JUnit: `sim.TurtleModeTest` (Mischer, Scharfschalten nur mit Turtle und nur am Boden, Ausschalten entschärft,
  Richtung wie im Kamerabild, alle fünf Presets drehen sich in vier Richtungen um und liegen danach flach),
  `sim.WaterproofTest` (Kurzschluss ohne Beschichtung, Fliegen und Entschärfen unter Wasser, Props bremsen, Auftrieb,
  aus dem Wasser heraus, Lava), `drone.coating.CoatingRulesTest`, `input.TurtleSelectorTest`, `DroneInteractionTest`;
  Bewegungs-Goldens und Kalibrierung unverändert.
- Selbsttest (`drone.client.WaterproofTurtleSelfTests`, Präfixe `wasserdicht:` und `turtle:`): Spray auf die
  platzierte Drohne (Haltbarkeit, zweites Sprühen kostenlos), Item ↔ Entity, Sonderrezept über den echten
  Rezept-Manager samt Rest im Raster, Werkbank-Kreislauf (gleiche Teile, andere Props, andere Motoren), Flug im Becken
  aus Glas (ohne Beschichtung gesperrt, beschichtet scharf, Props bremsen, mit Vollgas heraus, entschärfen) und
  Turtle-Flip im Sichtflug (ohne Turtle gesperrt, mit Turtle scharf, OSD, Motoren rückwärts, aufrecht, Motoren aus,
  Ausschalten entschärft). Mit `-PpropwashScreenshots=true` dazu die Bilder `waterproof_underwater_fpv` und
  `turtle_flip_los` sowie `bildschirmfotos: Turtle-Modus im Tab Modi` und `… Nach Absturz abschalten im Tab
  Welt-Regeln` (englisch und deutsch, `WaterproofTurtleShowcase`) und `flight_school_turtle` in
  `guide: Bildschirmfotos der Flugschule`.
