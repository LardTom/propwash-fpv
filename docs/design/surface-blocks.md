# Slime, Wolle, Heuballen und Honig (Propwash 0.6.0)

Bis 0.5.2 kannte die geflogene Drohne genau zwei besondere Blöcke, und nur den Block unter ihrer Mitte: Slime gab einen
Rückprall von 0,8, Honig klebte. Der erste Aufprall lief aber über die Bodenebene im Flugmodell, die sich die Fläche erst
nach der Berührung merkte; auf Slime prallte die Drohne deshalb beim ersten Mal wie auf Stein ab und zählte den Aufprall
als Absturz. Wolle und Heuballen waren Stein. Seit 0.6.0 hat jede berührte Seite eine Antwort aus Block-Tags: Slime
federt an allen Seiten zurück, Wolle und Heuballen fangen die Drohne ohne Absturz ab, und von Wolle startet sie normal.
Honig wirkt seit der Owner-Entscheidung vom 21.10.2026 wie bei Vanilla-Entitys an allen Seiten: Kontakt bremst stark und
ohne Abprall, an einer Honigwand rutscht die Drohne langsam herunter, vom Honig startet sie erst nach etwa 1 s oder mit
deutlich mehr Schub (Abschnitt 2a).

## 1. Was die Drohne spürt

| Fläche | Blöcke (Tag) | Rückprall | Haftung | Aufprall zählt | Wirkung |
|---|---|---|---|---|---|
| normal | alle übrigen | Drohne (wie bisher) | aus der Block-Reibung (wie bisher) | 1,0 | unverändert |
| federnd | `propwash:drone_bouncy`: Slime | 0,8 | 1,0 | 0 | federt an allen Seiten, kein Absturz, kein Prop-Schlag, kein Verschleiß |
| Kissen | `propwash:drone_cushion`: `#minecraft:wool`, Heuballen | 0,05 | 1,5 | 0 | fängt ab, bleibt liegen, Start wie vom Boden |
| klebrig | `propwash:drone_sticky`: Honig | 0 | 1,5 | 0 | an allen Seiten: kein Abprall, kein Absturz, kein Verschleiß; dazu Bremsen, Rutschen und Haften (Abschnitt 2a) |

Die Tags sind Daten: Datenpakete und Add-ons können eigene Blöcke hineinlegen, Vanilla gleicht sie mit den Clients ab.
Steht ein Block in mehreren Tags, gilt Kissen vor federnd vor klebrig. Bremsen, Rutschen und Haften hängen nur am Tag
`drone_sticky`, nicht an der Antwort der Fläche: Ein Server kann so weitere Blöcke klebrig machen, ein Kontakt-Haken von
Add-ons (`addon-collision`) ändert nur Rückprall, Haftung und Aufprall.

**Nicht dabei:** Teppiche, Betten und Schneeschichten (ihre Kollision bleibt, wie sie ist), Woll-Stufen und Woll-Platten
(seit 26.3 in `#minecraft:wool_stairs` und `#minecraft:wool_slabs`; das Design nennt nur `#minecraft:wool`, Abschnitt 7)
und unbemannte, fallende Drohnen (sie nutzen schon die Vanilla-Physik mit Slime-Abprall).

## 2. Flugmodell

Die Antwort einer Fläche ist ein reines Java-Record `sim.SurfaceResponse(restitution, grip, impactScale)` mit den Werten
der Tabelle; `SurfaceResponse.normal(friction)` rechnet die Haftung wie bisher aus der Block-Reibung
(`(1 − Reibung) / 0,4`, begrenzt auf 0,05 bis 1,5). Die Welt meldet sie im `sim.MoveReport`: zu `surfaceRestitution`
und `surfaceGrip` kommt `surfaceImpactScale` (Standard 1,0; `NaN` zählt als 1,0, sonst auf 0 bis 1 begrenzt).

- **Wertung des Aufpralls** (`DroneSim#classify`): Geschwindigkeit und Quergeschwindigkeit zählen mit
  `v · surfaceImpactScale` für Absturz, harten Aufprall, Stoß und das Aufprall-Protokoll, aus dem der Verschleiß
  entsteht. Bei 0 zählt auch kein Prop-Schlag. Das gilt für alle Kontaktstellen eines Schritts: Achsen-Treffer,
  schräge Kontakte und die Bodenebene.
- **Rückprall:** Der Rückprall der Fläche ersetzt den der Drohne. Die Verluste, die bei harten Treffern mit der
  Geschwindigkeit wachsen, stehen für den nachgebenden Rahmen; zählt der Aufprall nicht, gibt auch der Rahmen nicht nach.
  Deshalb rechnet die Bodenebene ihre Verluste mit der Absturzgeschwindigkeit geteilt durch `impactScale`
  (`DroneSim.lossSpeed`, bei 0 ohne Verlust), der Achsen- und Schrägkontakt mit einer auf
  `max(0,4 m/s, impactScale · Absturzgeschwindigkeit)` begrenzten Annäherung (`DroneSim.lossApproach`). Bei 1,0 laufen
  beide Wege unverändert. Damit federt Slime aus 8 m/s mit gut 6 m/s zurück; mit den Rahmenverlusten wären es keine
  5 m/s gewesen.
- **Gedächtnis der Bodenebene:** Die Bodenebene fängt die Drohne innerhalb von 1 m über dem Boden schon im Teilschritt
  ab, bevor das Entity den Block berührt. Sie nutzt die gemerkte Fläche (`groundGrip`, `groundBounce` und neu
  `groundImpact`). Die Drohne merkt sich die Fläche jetzt nicht nur bei Bodenkontakt, sondern auch bei jedem Zug ohne
  Berührung aus der Fläche darunter; der erste Aufprall auf Slime oder Wolle nutzt so schon deren Antwort.
- **Auflage knapp im Block:** Entsteht ein Block um die liegende Drohne herum (etwa wenn die Blöcke eines frisch
  gebauten Testfelds nach dem Versetzen der Drohne beim Client ankommen), ragt die Hülle einige Zehntelmillimeter in ihn
  hinein. Die Hüllen-Abfrage von Minecraft (`QuadcopterEntity#supportGap`) übersieht einen Block, in dem die Hülle schon
  steckt, und meldet freien Raum darunter. Bis zum Befund WA-W1-14 galt die Drohne dann als nicht aufliegend: „am Boden“
  sprang jedes zweite Bild aus, die Drohne kam nie zur Ruhe. Jetzt zählt sie als aufliegend, wenn der Strahl nach unten
  (`SimEnvironment.solidDistance`) eine Oberfläche höchstens 2 mm von der Unterkante der Hülle entfernt trifft
  (`DroneSim#afterMove`). Steckt sie tiefer, wird sie wie bisher geweckt und von der Bodenebene bis auf 0,5 mm
  herausgeschoben; danach greift dieselbe Regel.

Ohne besondere Fläche (alle Werte im Standard) bleibt das Flugmodell bitgleich: `AirframeGoldenTest`,
`MotionFingerprintTest` und `SaveFormatGoldenTest` grün, `tools/calibration.sh` byte-gleich.

## 2a. Honig

Liegt die Hülle an einer Seite höchstens 3 cm an einem Block aus `drone_sticky` an (gemessen an Kollisions- oder
Umrissform, die nähere zählt; beim Honig ist die Umrissform der volle Block, auf dem die Drohne über `drone.SoftGround`
auch aufliegt), meldet die Welt diese Seite im Bitfeld `MoveReport#stickySides` (Bit `achse · 2 + (positiv ? 1 : 0)`,
`MoveReport.STICKY_FLOOR` = unten). `SurfaceMaterials.sticky(level, hitbox, report)` tastet dafür nach jedem Zug alle
sechs Seiten ab (nur geladene Chunks); `drone.client.PilotSession`, `rescue.ServerFlight` und `rescue.VoxelFlight` (mit
Zell-Abfrage) rufen sie auf. Das Flugmodell merkt sich die Seiten bis zum nächsten Zug (`DroneSim#stickySides`) und
rechnet in jedem Teilschritt:

- **Bremsen:** Geschwindigkeit und Drehimpuls klingen mit 18/s ab (`DroneSim.STICKY_DRAG`, je Teilschritt exakt
  `1 − e^(−18 · h)`), so stark wie Vanillas Faktor 0,4 je Tick für Entitys auf Honig. Eine Drohne, die mit 10 m/s schräg
  gegen eine Honigwand fliegt, ist nach 0,25 s langsamer als 1 m/s und bleibt an der Wand.
- **Rutschen:** An einer Honigwand fällt die Drohne ohne Schub mit gut 0,5 m/s (Schwerkraft gegen die Bremse, Vanilla:
  höchstens 1 m/s), statt frei zu fallen. Die Kraft zählt als Kontaktkraft im Beschleunigungsmesser.
- **Haften:** Auf dem Honigboden (Seite unten und am Boden) zieht eine Haftkraft von anfangs dem Vierfachen des Gewichts
  nach unten (`DroneSim.STICKY_HOLD`); sie klingt linear über 1 s ab, solange der Schub die Drohne heben würde
  (`DroneSim.STICKY_SECONDS`), und baut sich ohne Zug ebenso schnell wieder auf. Gemessen (`HoneySurfaceTest`,
  Freestyle): mit dem 1,2-fachen Gewicht 1,2 s bis 5 cm, mit dem 1,5-fachen 1,0 s, mit dem 2,6-fachen 0,6 s, mit dem
  4,7-fachen 0,13 s, mit Vollgas sofort; von Stein dagegen 0,1 bis 0,2 s. Im Turtle-Modus haftet nichts.

Ohne Honig an der Hülle (Bitfeld 0) rechnet das Flugmodell bitgleich wie vorher.

## 3. Abtastung der Welt

`drone.SurfaceMaterials.at(level, hitbox, hitX, hitY, hitZ)` bekommt die Hülle (Hitbox) und je Achse die berührte Seite
(Vorzeichen, 0 = keine). Je Seite geht es die Felder ab, die die Seitenfläche der Hülle überdeckt, und nimmt je Feld den
nächsten Block mit Kollisionsform nach außen (berührte Seiten bis 0,25 m, unten bis 1 m, passend zur Reichweite der
Bodenebene). Die Fläche dieses Felds unter der Hülle zählt für die Antwort des Blocks. Es gewinnt die Antwort mit der
größten Fläche; bei Gleichstand die weichere (kleinerer `impactScale`, dann kleinerer Rückprall, der Rückprall der Drohne
zählt als härtester). Felder in nicht geladenen Chunks liest sie nicht (am Server kein Laden im Tick).

`SurfaceMaterials.fill(level, hitbox, below, report, grounded)` leitet die Seiten aus dem Bericht ab: getroffene Achsen
nach der Richtung des Zugs, bei schrägem Kontakt die Achsen mit mehr als 0,35 Anteil an der Normalen; unten zählt dazu,
wenn die Drohne am Boden ist oder nichts berührt.

**Honig an allen Seiten (Owner-Entscheidung 21.10.2026):** Eine berührte Seite aus `drone_sticky` zählt wie Slime und
Wolle mit ihrer eigenen Antwort. Die Regel aus Befund P1-V4-1 (Honig nur als Block unter der Drohne, dann für jeden
Kontakt des Zugs, `SurfaceResponse#withStickyBelow`) ist entfallen: Eine Steinwand knapp über Honigboden ist wieder eine
Steinwand mit Abprall und Absturz.

Aufrufe:

- `drone.client.PilotSession#fillSurface` bei Treffer, schrägem Kontakt, Bodenkontakt und neu auch, solange die
  Bodenebene gilt (fester Boden weniger als 1 m unter dem Schwerpunkt);
- `rescue.ServerReturnFlight#move` mit derselben Bedingung (autonomer Heimflug am Server);
- `rescue.VoxelFlight` über eine optionale Zell-Abfrage `VoxelFlight.SurfaceCells` (für Tests der Rettung; ohne Abfrage
  unverändert).

## 4. Server und Mehrspieler

Keine neuen Pakete, keine Änderung am Netzwerkprotokoll. Der Client fliegt die eigene Drohne und liest die Tags aus der
synchronisierten Welt; ein Absturz, der nicht zählt, schickt auch keinen Aufprall an den Server, also keinen Verschleiß.
Der autonome Heimflug am Server liest dieselben Tags.

## 5. Kompatibilität

Alte Welten und Speicherformat unverändert. Spürbar anders als in 0.5.2: Slime hat Haftung 1,0 statt 0,5 (Tabelle des
Designs); bei gemischtem Untergrund zählt die größte Fläche unter der Hülle statt des Blocks unter der Mitte; der erste
Aufprall nach dem Abheben nutzt die Fläche darunter statt der zuletzt berührten; Honig bremst, lässt rutschen und haftet
an allen Seiten, ein Aufprall auf Honig ist kein Absturz mehr.

## 6. Tests

- JUnit `sim.SurfaceResponseTest` (`tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.SurfaceResponseTest`):
  Fall auf Slime mit 8 m/s → Rückprall ≥ 5,5 m/s ohne `CRASH`, `HARD_IMPACT`, `BUMP`, `PROP_STRIKE` und ohne Eintrag im
  Aufprall-Protokoll; Wolle mit 20 m/s → keine Ereignisse, Rückprall unter 6 %, liegt danach ruhig; Stein mit 20 m/s →
  `CRASH` wie bisher; Honig mit 20 m/s → keine Ereignisse, kein Rückprall; Slime-Wand mit 10 m/s im 45°-Winkel → zurück mit mindestens
  5 m/s, ohne Ereignisse; Wollwand mit 15 m/s im 45°-Winkel → keine Ereignisse, kein Prop-Schlag, Stein dagegen
  `CRASH` und `PROP_STRIKE`; Start von Wolle; Drohne 0,3 mm, 5,6 mm und ruhend um 3 mm in die Wolle versetzt (Kollision
  wie in Minecraft über `BoxWorld`) bleibt in jedem Bild am Boden, kommt zur Ruhe und sinkt höchstens 1 mm ein; Standard
  und `NaN` bitgleich; Tabelle, Haftung, Größte-Fläche-Regel; `VoxelFlight` mit Zell-Abfrage; Honigwand mit 6 m/s
  ohne Abprall, Steinwand knapp über Honigboden mit Abprall; Slime-Decke 1,2 m über der schwebenden Drohne, von unten mit
  8 m/s → bis zum Boden keine Ereignisse und kein Eintrag im Aufprall-Protokoll, zurück mit mindestens 4 m/s, scharf;
  erst der Steinboden 4 m tiefer bringt nach weniger als 1 s einen schädlichen Treffer, eine Steindecke zählt den Treffer
  schon an der Decke.
- JUnit `sim.HoneySurfaceTest`: Honigwand mit 10 m/s (und 6 m/s quer) ohne Abprall (kleinstes vx −0,05 m/s), ohne
  Ereignisse, nach 0,25 s unter 1 m/s und noch an der Wand, Steinwand mit Abprall und Absturz; Rutschen an der Honigwand
  0,52 m in 1 s (vy −0,55 m/s), an Stein freier Fall; Start vom Honigboden mit dem 1,5-fachen Gewicht nach 0,8 bis
  1,3 s (gemessen 1,0 s), mit Vollgas wie von Stein nach weniger als 0,35 s, ohne Ereignisse.
- Selbsttest beider Loader: `'-PpropwashSelftestOnly=drone: Slime,drone: Wolle,drone: Honig'` (`drone.client.SurfaceSelfTests`). Jeder
  Schritt wartet, bis der Client das Testfeld sieht, und versetzt die Drohne erst danach. Der Schritt „3 mm in der Wolle
  bleibt die Drohne am Boden und kommt zur Ruhe“ setzt die ruhende Drohne 3 mm tief in die Wolle und verlangt danach
  mindestens 20 Ticks Ruhe und Bodenkontakt. „drone: Honig“: gegen eine Honigwand mit 6 m/s ohne Abprall (kleinstes vx
  nicht unter −0,3 m/s), ohne Absturz und Verschleiß; danach entschärft rutscht die Drohne an der Wand herunter (vy in
  1,5 s zwischen −1,0 und −0,15 m/s, jeden Tick an der Wand, Bildschirmfoto `surface_honey_wall`); Fall aus 10 m auf
  Honig ohne Rückprall (Gipfel unter 0,1 m), Absturz und Verschleiß; Start von Honig mit Gas Schwebe + 0,05: klebt
  mindestens 12 und höchstens 40 Ticks, nach 3,5 s höher als 0,5 m (Bildschirmfoto `surface_honey_takeoff`). Die
  Bildschirmfotos entstehen bei jedem Lauf: mit `-PpropwashScreenshots=true` (Fabric) in `docs/screenshots/`, sonst unter
  `screenshots/` im Run-Ordner des Selbsttests. „von unten gegen eine Slime-Decke mit
  8 m/s“: Steinboden, Slime-Decke 4 m darüber, Drohne schwebt 2,8 m über dem Boden und wird mit 8 m/s nach oben
  angeschoben; der Schritt zählt Treffer (`Telemetry#impactCount`) getrennt nach Decke und Boden und prüft Absturz und
  Verschleiß bis zum letzten Tick vor dem Boden. Gemessen: Decke mit 7,2 m/s, nach 5 Ticks −5,9 m/s, 0 Treffer,
  Verschleiß gleich; der Steinboden folgt nach 13 Ticks mit 6,2 m/s und verschleißt die Drohne (ohne Absturz). Wer den
  Verschleiß erst 20 Ticks nach der Decke abliest, misst also den Boden mit.
- Server-Rauchtest (`rescue.SurfaceServerChecks`, Zeile „Slime und Wolle: …“ im Log): autonomer Heimflug mit 15 m/s gegen
  eine Wand aus Wolle, Slime und zur Kontrolle Stein.

## 7. Offen

- Woll-Stufen und Woll-Platten (26.3) in `drone_cushion` aufnehmen? Das Design nennt nur `#minecraft:wool`.
- Der Kollisions-Haken für Add-ons (`addon-collision`) soll auf `SurfaceResponse` aufsetzen.
- Zwischenwerte von `impactScale` (zwischen 0 und 1) nähern die Rahmenverluste beim Achsen- und Schrägkontakt nur an;
  die eingebauten Flächen nutzen 0 und 1.
