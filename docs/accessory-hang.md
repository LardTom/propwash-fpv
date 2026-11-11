# Gerade hängendes Zubehör (Add-on-API 1.7)

Für Add-on-Autoren. Zubehör eines Add-ons kann angeben, dass es gerade hängt: ohne die Neigung der Halterung und ohne
die Streckung auf die LED-Streifenlänge des Rahmens, so wie Propwashs Greifer. Stabil ist nur, was unter
`de.lardtom.propwash.api` steht; die Anleitung steht in [`api/addon-guide.md`](api/addon-guide.md) Abschnitt 3.4.

## 1. Warum

Jede Halterung eines Rahmens hat eine Neigung (`accessory_anchors` → `tilt_deg`): am `freestyle_5` oben 22° und unten
30°, am Whoop unten 90°. Unten wird Zubehör außerdem in x auf `length_mm / width` gestreckt. Beides ist für Action-Cams
(Uptilt) und LED-Streifen gedacht. Bis jetzt galt das für jedes Zubehör außer dem Greifer: Eine Speer-Halterung erschien
am 5-Zöller 30° geneigt und breiter, ein Booster oben 22° nach oben geneigt, und `MountPose` (`forward()`, `front()`)
zeigte schräg. Ein Add-on kann das nicht ausgleichen, weil die Werte je Rahmen anders sind.

## 2. Angabe

```json
"slot": "bottom", "size_mm": [12.0, 10.0, 60.0], "hangs_straight": true
```

```java
AccessoryDefinition.builder(id).slot(AccessorySlot.BOTTOM).size(12.0, 10.0, 60.0).hangsStraight(true)
```

- `AccessoryDefinition#hangsStraight()` (neue letzte Record-Komponente), Builder `hangsStraight(boolean)`, JSON
  `hangs_straight` (`true` oder `false`, anderes ist ein Fehler mit Feldname). Standard `false` = Verhalten wie in 1.6.
- Die Konstruktoren mit 12 Argumenten (API 1.6) und 13 Argumenten (mit `ledPathMm`) bleiben und setzen `false`.
- Der Codec (`PropwashApi.parts().toJson`) schreibt das Feld nur bei `true`; Teile ohne Angabe ergeben dasselbe JSON
  wie bisher. Der Teile-Hash im Handshake sieht die Angabe (Record-`toString()`).
- Ältere Propwash-Versionen ignorieren das Feld mit der Warnung „unbekanntes Feld wird ignoriert“ und neigen das Teil.

## 3. Wirkung

Mit der Angabe gilt an jedem Rahmen und jeder Halterung Neigung 0 und Streckung 1, für alles, was die Lage des Zubehörs
nutzt:

- Modell bzw. Ersatzform, Hülle (`VisualHull`), Flugmodell (Schwerpunkt, Trägheit, Quader des Zubehörs).
- LEDs und Linse (`AccessoryAnchor#ledMm`, `#lensMm`): ohne Neigung, LEDs über die eigene Breite.
- `ItemDisplay` (B2): der Versatz liegt in den Achsen des Zubehörs ohne Neigung.
- `MountPose`: `forward()` waagerecht entlang der Nase, `up()` nach oben, `front()` mittig an der Vorderseite des
  Quaders aus `size_mm` (unten: 5 mm unter und `depth / 2` vor dem Anbaupunkt bei 10 mm Höhe).
- Web-Export: `parts.json` trägt `data.hangs_straight: true` für jedes gerade hängende Zubehör, auch für den Greifer;
  der Web-Konfigurator rechnet damit Neigung, Streckung und Einrücken wie das Spiel ([`web-export.md`](web-export.md)
  Abschnitt 5.3, Test `HangsStraightExportTest`).

Greifer (immer gerade), LED-Streifen (geneigt, gestreckt) und Action-Cams (geneigt) bleiben unverändert. Zubehör mit
`led_path` wird wie bisher nicht gestreckt.

## 4. Beispiel

`dev/example/ExampleDispenser` (nur im Entwicklungslauf): Die Speer-Halterung `propwash_example:spear_holder` hängt
gerade; der Speer (`SPEAR_DISPLAY`) liegt waagerecht unter der Halterung, Spitze nach vorn, an jedem Rahmen gleich. Der
Booster oben bleibt geneigt und gleicht die 22° am 5-Zöller in `ROCKET_DISPLAY` aus.

## 5. Tests

- JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.accessory.hang.HangsStraightTest` (Neigung,
  Streckung, Ecken und `MountPose` mit und ohne Angabe an allen Rahmen, Greifer und LED-Streifen unverändert, Codec
  additiv, alte Konstruktoren, beladene Varianten).
- Selbsttest beider Loader: `'-PpropwashSelftestOnly=addon-api-17: gerade hängend'` (Klasse
  `interact.client.AccessoryHangSelfTests`): Render-Zustand am `freestyle_5` und am Whoop ohne Neigung und Streckung,
  Speer sichtbar, `MountPose` am Server waagerecht, Booster weiter 22° geneigt.
- Bildschirmfotos (nur Fabric, `-PpropwashScreenshots=true`): `accessory_hang_freestyle` und `accessory_hang_whoop`
  (von der Seite, Speer unter der Halterung), `accessory_hang_freestyle_empty` (leere Halterung schräg von unten, oben der
  geneigte Booster).

## 6. Zubehör trägt die Drohne (Befund PTP-2)

Aus dem Spieltest von 0.6.0-rc.1: Ein Freestyle 5″ mit Dispenser vorn und Speer-Halterung unten (Mob Defense) und ein
Cine 15″ mit `gimbal_cine` vorn (Kamera-Add-on) landeten auf einer Stufe, und das FPV-Bild zeigte die Blocktextur.
Ursachen:

- Das Flugmodell kannte nur Zubehör oben (Punkt 8 der Hülle). Vorn und unten hängendes Zubehör steckte im Boden; das
  Modell wurde um den Überhang angehoben gezeichnet (`DroneVisuals#placeInWorld`), Auge und Schwerpunkt blieben unten.
- `FpvView#coverInsideBlock` prüfte 10 mm um Auge und Schwerpunkt. Stufen und Treppen zählen in Minecraft 26.3 als
  sichtversperrend, sobald dieser Bereich sie berührt: Lag Auge oder Schwerpunkt weniger als 10 mm über einer Stufe, deckte
  die Blocktextur das ganze Bild ab. Schnee unter 8 Schichten und Teppich deckte die Prüfung dagegen nie ab.

Seitdem gilt:

- **Bodenpunkte:** Ragt ein Zubehör vorn oder unten (auch ein Gimbal) mehr als 5 mm (`ACCESSORY_STAND_MIN`) unter die
  Rahmenhülle, sind die vier tiefsten Ecken seiner Box (mit Neigung und Streckung der Halterung, siehe Abschnitt 3)
  Bodenpunkte des Flugmodells (`AirframeSpec#accessoryPointCount`, `#accessoryX/Y/Z`, Plätze ab `ACCESSORY_GROUND`, bis
  zu 8 Punkte). Dazu kommen vier Standbeine (`AirframeSpec#STAND_LEGS`) an den Rahmenecken (unter den Motoren, bei Ducts
  an den Ringen) in der Ebene der tiefsten Zubehör-Ecke; `#standPointCount` zählt Ecken und Standbeine. Die Drohne steht
  und landet so eben wie ohne Zubehör auf dem Zubehör, nur um dessen Tiefe höher, und lässt sich am Boden scharf schalten
  (Befund AUTO47-1: vorher stand sie schräg auf Zubehör und Rahmen, ein Whoop 65 mit Speer-Halterung lag nach dem Fall
  aus 0,3 m auf dem Rücken, ein Cinewhoop mit Dispenser und Speer stand 27,5° schräg, über dem Scharfschalt-Winkel).
  Zubehör-Ecken und Standbeine sind harte Kontakte ohne Rückprall (`GroundSolver#absorbs`), graben sich nicht ein und
  beschädigen keine Props; so kippen Bauten auf hohem Zubehör beim Fallen nicht öfter aufs Dach als ohne Zubehör.
- **Hitbox unverändert:** Höhe, Breite und Fuß der Kollisionsbox und der Abstand Fuß–Schwerpunkt bleiben aus dem Rahmen
  berechnet. Flug, Kollisionen mit Blöcken und Gates und die Rennwertung (sie nimmt die Position der Drohne) ändern sich
  nicht; flache Teile wie LED-Streifen (bis 4,6 mm unter der Hülle) zählen nicht.
- **Modell:** `DronePhysique` nimmt dieselben Punkte, das Modell sitzt an der Physik statt um den Überhang angehoben.
- **Lose abgestellt:** Ohne Pilot hält `QuadcopterEntity` die Drohne auf dem Zubehör. Gemessen wird der Boden unter jeder
  Zubehör-Ecke einzeln (dünne Säule an ihrer Lage), nicht unter der ganzen Kollisionsbox: Liegt die Hitbox mit dem Rand
  auf einer Stufe und hängt das Zubehör über tieferem Boden, liegt die Drohne auf der Stufe (Befund AUTO47-2: vorher
  schwebte sie um die Zubehör-Tiefe darüber, am 7″ mit Beispiel-Gimbal 41 mm). Die Standbeine zählen dabei nicht. Eine
  Drohne aus einem älteren Stand, deren Zubehör noch im Boden steckt, hebt sich beim nächsten Tick darauf.
- **Gesteuert an Stufenkanten:** Auch im Flugmodell (FPV und Sichtflug, scharf und unscharf, Rückholflug auf dem Server)
  gilt für Zubehör-Ecken und Standbeine der Boden unter dem jeweiligen Punkt, auch wenn die Mitte gestützt ist
  (Befund STAND3-1: vorher prüfte `DroneSim` dann alle Bodenpunkte gegen eine Ebene; Zubehör-Ecken über der Kante standen
  auf Stufenhöhe in der Luft, die Standbeine hoben den Rahmen eben an, am 7″ mit Beispiel-Gimbal schwebte der Fuß 40,5 mm
  über der Stufe, beim FPV-Start sprang die Drohne hoch). Dafür misst `DroneGround#fillPoints` je Frame mit derselben
  Säule wie das Untergrund-Raster den Boden genau unter jeder Zubehör-Ecke und jedem Standbein
  (`SimEnvironment#pointSupport`, `#pointX`, `#pointZ`, `#pointCount`, bis zu 12 Punkte); `GroundSupport#groundPlanes`
  nimmt diese Werte, solange der Punkt nicht mehr als einen halben Rasterabstand von der gemessenen Stelle entfernt ist,
  sonst das Raster. Ein Standbein trägt höchstens bis zu der Höhe, auf der das Zubehör selbst Boden hat
  (`GroundSupport#capStandLegs`): Hängt das Zubehör über tieferem Boden, liegt die Drohne mit dem Rahmen auf der Stufe;
  reicht ein tiefes Zubehör bis auf den tieferen Boden, steht sie darauf eben, der Rahmen über der Stufe. Rahmenpunkte
  nehmen wie bisher die eine Ebene, solange die Mitte gestützt ist; auf ebenem Boden ist alles wie vorher (Kalibrierung
  byte-gleich). Liegt eine Zubehör-Ecke tiefer als 0,48 m unter dem Fuß der Hitbox, reicht die Messung (0,5 m) nicht bis
  zu ihr; dann gilt für sie weiter die Ebene.
- **FPV-Auge:** Im Pilot- und im Zuschauer-FPV sitzt das Auge an der gezeichneten Kamera: `FpvView` verschiebt Auge und
  Anker des Kameraschutzes um den Versatz, mit dem das Modell gezeichnet wird (`DroneVisuals#displayLift`, wie
  `placeAtDisplayedPose` mit Bodenkontakt).
- **Teil-Blöcke:** Die Überlagerung prüft nur den Punkt selbst. Sie deckt das Auge ab, wenn es in einem sichtversperrenden
  Block liegt oder in der sichtbaren Form eines verdeckenden Blocks (Stufe, Treppe, Schnee, Teppich, Stein …). Knapp
  darüber bleibt das Bild frei, Glas und Laub decken nie ab. Den Schwerpunkt prüft sie nur noch, wenn er in einem
  sichtversperrenden Block steckt. Verhindert wird das Auge im Block wie bisher vom Kameraschutz (`FpvEyeGuard`).

Tests: JUnit `tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.AccessoryGroundTest --select-class
de.lardtom.propwash.sim.AccessoryLedgeTest --select-class de.lardtom.propwash.accessory.hang.AccessoryStandTest
--select-class de.lardtom.propwash.accessory.fix.PayloadLimitTest` (Bodenpunkte, Landung auf dem Zubehör, Hitbox,
LED-Streifen, Modell, Halt lose abgestellter Drohnen; eben und scharf schaltbar mit Dispenser und Speer-Halterung in den
Maßen von Mob Defense an allen passenden Rahmen und mit dem Cine-Gimbal, nicht öfter auf dem Rücken als ohne Zubehör,
Überladungsflug mit 58 mm hoher Box scharf schaltbar; gesteuert an der Stufenkante mit Cinewhoop 3 und Dispenser und
mit Cine 15″ und `gimbal_cine`: unscharf und scharf eben auf der Stufe ohne Hochspringen, über 10 cm tieferem Boden
das tiefe Gimbal unten aufgesetzt, mit Zubehör auf der Stufe wie bisher darauf). Selbsttest beider Loader
`'-PpropwashSelftestOnly=drone: steht auf Zubehör'` (Klasse `drone.client.AccessoryStandSelfTests`, im FPV eben und ohne
Scharfschalt-Sperre, lose an der Stufenkante ohne Schweben, gesteuert an der Stufenkante mit der Mitte 66 mm auf der
Stufe: lose, im FPV unscharf und scharf, nach der Sitzung und im Sichtflug ohne Schweben und Hochspringen),
Bildschirmfotos `accessory_stand_freestyle_side`, `accessory_stand_freestyle_fpv`, `accessory_stand_gimbal_side`,
`accessory_stand_gimbal_fpv` und `accessory_stand_edge_piloted` (Sichtflug, Seitenansicht der Kante).

## 7. Zubehör vorne sitzt am Rahmen (Befund RC3P-5)

Aus dem Spieltest vom 04.11. (0.6.0-rc.2 mit allen Add-ons): Der Dispenser hing mit sichtbarer Lücke vor einer 6S-Drohne
und war nicht mit dem Rahmen verbunden. Ursache sind die Anker vorn in den Rahmendaten, nicht eine Ableitung: Sie lagen
6–9 mm vor der Vorderkante von Bodenplatte und Kameraplatten, und vorn gibt es keinen Sockel, der die Lücke füllt. Just More
Parts hat diese Regel übernommen („8 mm vor dem vordersten Teil“), beim Kino-Rahmen des Kamera-Add-ons hängt das Zubehör
4,5 mm unter der Nase. Die Ersatzformel `(0; 0; −0,3 × Radstand)` für Rahmen ohne Anker vorn benutzt kein Rahmen der
Kombination; sie bleibt unverändert.

Soll: Zubehör vorne berührt an jedem Rahmen den Rahmen oder die Platte – Abstand zwischen gezeichnetem Zubehör und
gezeichnetem Rahmen höchstens 1 mm, Überschneidung mit dem Quader aus `size_mm` höchstens 2 mm.

Anker vorn der eingebauten Rahmen (Rahmen-JSON, `drone_layout.json`, `tools/assets/parts/layout.py`, Neigung 0°):

| Rahmen | vorher | jetzt | Lage |
|---|---|---|---|
| `cinewhoop_3` | (0; −3; −50) | (0; −3; −30) | unter der Vorderkante der Bodenplatte (auch schmales Zubehör berührt sie; vorher nur breites die Arme) |
| `freestyle_5` | (0; 0; −72) | (0; 0; −64) | Vorderkante der Bodenplatte |
| `race_5` | (0; 0; −66) | (0; 0; −56) | Vorderkante der Bodenplatte |
| `long_range_7` | (0; 0; −76) | (0; 0; −68) | Vorderkante der Bodenplatte |

Damit rücken Modell, Hülle, Flugmodell (Schwerpunkt, Trägheit, Bodenpunkte) und `MountPose` des Zubehörs vorn um
8–20 mm nach hinten. Kalibrierung und Goldens bleiben byte-gleich (dort fliegt kein Zubehör vorn); `MountPosesTest` prüft
den neuen Anker des `freestyle_5`. Die FPV-Kamera der eingebauten Rahmen ragt bei keinem Kamerawinkel in den Quader
(höchstens 0,6 mm am Cinewhoop bei 80°).

Prüfung (`AccessoryFit` in devtest): misst den Abstand zwischen den gezeichneten Flächen des Zubehörs (Modell oder
Ersatzform samt Sockel, mit Neigung und Streckung) und denen des Rahmens, die Überschneidung des Rahmens mit dem
Zubehör-Quader und schlägt bei Ausreißern einen Anker vor (vorn: nach hinten bis an den Rahmen oder nach oben bis unter
die Nase, je nachdem, was näher liegt; unten: nach oben).

- JUnit `tools/fastcheck.sh test --select-class de.lardtom.propwash.accessory.hang.FrontAnchorFitTest`: jeder eingebaute
  Rahmen mit Halterung vorne gegen die Modelldateien mit Quadern 62×58×64 (Dispenser aus Mob Defense), 34×26×40
  (Beispiel-Dispenser), 12×10×20 und 44×44×46; dazu die Messung an einfachen Quadern. Ohne den Fix rot (freestyle_5
  7–8 mm, race_5 9–10 mm, long_range_7 6–7,7 mm, cinewhoop_3 bei schmalem Zubehör 2,4–3,9 mm Lücke).
- Selbsttest beider Loader `'-PpropwashSelftestOnly=addon-api-17: Zubehör am Rahmen'` (Klasse
  `interact.client.AccessoryFitSelfTests`): jeder angemeldete Rahmen (auch aus Add-ons) mit jedem passenden Zubehör vorne
  (dazu an eingebauten Rahmen zwei Prüfquader) gegen den Rahmen; Zubehör unten und oben gegen Rahmen, Akku, Stack,
  Kamera und Motoren des Grundaufbaus (Abstand höchstens 1 mm, Überschneidung nur gemeldet). Eingebaute Rahmen müssen
  bestehen, Add-on-Rahmen erscheinen als Zeilen „AUSREISSER“ und am Ende als „Add-on-Befund: <Rahmen> + <Zubehör> …
  Anker … → Soll …“. Bildschirmfotos (nur Fabric) `accessory_fit_race`, `_freestyle`, `_cinewhoop`, `_long_range`
  (Beispiel-Dispenser, im Repo); mit Mob Defense nimmt der Schritt dessen Dispenser, mit dem Kamera-Add-on bzw. Just More
  Parts kommen `accessory_fit_camdrones_cine` und `accessory_fit_jmp_deadcat` dazu (nicht ins Repo).
- Für Add-on-Autoren: Anker vorn an die Vorderkante der Bodenplatte bzw. unter die Nase legen, nicht davor; der Quader
  hängt unter dem Anker und ragt nach vorn. Mit dem eigenen Jar in `runs/selftest-client/mods/` zeigt der Selbsttest
  jeden Ausreißer mit Soll-Anker.

## 8. Zubehör einrücken (Befund FB1-9)

Aus dem Spieltest von 0.6.0-rc.3: Der Dispenser aus Mob Defense hing auch nach Abschnitt 7 als ganzer Block vor der
Drohne. Abschnitt 7 hat nur die Lücke geschlossen: Der Quader liegt am Platz vorn weiter ganz vor und unter dem Anker
(z −Tiefe bis 0, y −Höhe bis 0), und der Anker sitzt an der Vorderkante der wenige Millimeter dicken Bodenplatte. Mit dem
Modell des Add-ons allein lässt sich das nicht ändern: Das Flugmodell, `MountPose` und die Mündung rechnen mit dem Quader
aus `size_mm`, und die Halterung liegt je Rahmen woanders.

```json
"slot": "front", "size_mm": [62.0, 58.0, 64.0], "recess_mm": 58.0, "drop_mm": 1.5
```

```java
AccessoryDefinition.builder(id).slot(AccessorySlot.FRONT).size(62.0, 58.0, 64.0).recess(58.0).drop(1.5)
```

- `AccessoryDefinition#recessMm()` und `#dropMm()` (neue letzte Record-Komponenten), Builder `recess(double)` und
  `drop(double)`, JSON `recess_mm` und `drop_mm`. Standard 0 = Verhalten wie bisher, bitgleich.
- `recess_mm` schiebt das Zubehör nach hinten (+z), `drop_mm` nach unten (−y), beides in den Achsen des Zubehörs: mit der
  Neigung der Halterung, ohne sie bei `hangs_straight`. Erlaubt sind 0 bis `depth` bzw. 0 bis `height`, sonst wirft die
  Definition mit dem Feldnamen. Negative Werte gibt es nicht.
- Die Konstruktoren mit 12 bis 16 Argumenten bleiben und setzen 0. Der Codec schreibt die Felder nur, wenn sie nicht 0
  sind. Der Teile-Hash im Handshake sieht sie (Record-`toString()`). Ältere Propwash-Versionen ignorieren die Felder mit
  der Warnung „unbekanntes Feld wird ignoriert“ und lassen das Teil vor dem Rahmen hängen.

Wirkung, an jedem Platz und jedem Rahmen:

- **Quader:** `AccessoryType#boxMinMm`, `#boxMaxMm`, `#centerMm`, `#muzzleMm`, `#lensMm` und `#ledMm` sind verschoben;
  `AccessoryAnchor#corners`, `#centerOf`, `#lensMm` und `#ledMm` folgen damit. Das Flugmodell nimmt Ecken und Mitte: Masse,
  Trägheit, bei Rahmen mit eigener Geometrie der Schwerpunkt längs, die Bodenpunkte (Abschnitt 6). Die Hitbox bleibt.
- **`MountPose`:** `center()` und `front()` folgen, `anchor()`, `forward()`, `up()` und `right()` bleiben. Geschosse,
  TNT und Items starten an der eingerückten Mündung; `muzzle_mm` zählt weiter ab der Mitte der Vorderfläche.
- **Modell:** Modell und Ersatzform entwirft man wie bisher am Anker (z −Tiefe bis 0, y −Höhe bis 0). Gezeichnet wird
  es an `AccessoryAnchor#modelOrigin` (Anker plus Verschiebung), dorthin wandern auch die `ItemDisplay`. Der Sockel oben
  (`mount_top`) bleibt am Anker. Die Felder `modelBoxMinMm`, `modelBoxMaxMm`, `modelLensMm` und `modelLedMm` von
  `AccessoryType` sind die unverschobenen Werte für die Ersatzform.

`AccessoryFit` (Abschnitt 7) trennt: `overlapMm` zählt nur noch, was im Quader vor dem Anker steckt, und darf wie bisher
höchstens 2 mm sein. Was im eingerückten Teil steckt, ist gewollt (`wantedOverlapMm`, im Log „gewollt eingerückt“). Der
Abstand von höchstens 1 mm gilt weiter, und der Soll-Anker rechnet ohne Einrücken.

Wie weit das geht, gemessen mit dem Quader 62×58×64 und 58 mm Einrücken (`AccessoryRecessTest`):

| Rahmen | Abstand zum Rahmen bei `drop_mm` 0 / 1,5 / 3 | gewollte Überlappung mit dem Rahmen bei 1,5 |
|---|---|---|
| `cinewhoop_3` | 0 / 0,9 / **2,4 mm** (über dem Soll) | 0 mm |
| `freestyle_5` | 0 / 0 / 0 mm | 5,1 mm |
| `race_5` | 0 / 0 / 0,1 mm | 4,1 mm |
| `long_range_7` | 0 / 0 / 0 mm | 4,1 mm |

`drop_mm` gilt je Zubehör, die Anker liegen aber verschieden: am `cinewhoop_3` schon unter der Platte (y −3), sonst an
der Oberkante (y 0). Bei 1,5 mm bleibt jeder eingebaute Rahmen im Soll, bei 3 mm bleibt am Cinewhoop eine Lücke.

Einrücken schiebt den Quader unter den Rahmen und damit in andere Teile. Propwash lässt das zu und zeichnet beides; die
Überschneidung ist nur sichtbar und ändert keine Flugwerte:

| Teil | ohne Einrücken | mit 58 mm |
|---|---|---|
| Akku unter `race_5` (`b6s_1100`) | 23 mm Abstand | 29 mm tief im Quader, bis 23 mm Einrücken frei |
| Halterung 24×18×90 unten, geneigt (30°) | 11,0–12,0 mm tief | 27,6–28,9 mm tief |
| dieselbe mit `hangs_straight` | 18,0–21,0 mm tief | 16,5–19,5 mm tief |
| `gripper` | 3,0–5,0 mm tief | 18,5–21,5 mm tief |
| `led_strip` | 1,0–3,0 mm Abstand | 3,1–6,1 mm tief |

Tests: JUnit `tools/fastcheck.sh test --select-class de.lardtom.propwash.accessory.hang.AccessoryRecessTest --select-class
de.lardtom.propwash.sim.AccessoryRecessFlightTest` (ohne Felder bitgleich, Quader, Mündung, Linse, LEDs, Zeichenposition,
Ersatzform, `MountPose`, Flugwerte, Codec und Grenzen, Messung, Kollisionen oben). Selbsttest beider Loader
`'-PpropwashSelftestOnly=addon-api-17: Zubehör eingerückt'` (Klasse `interact.client.AccessoryRecessSelfTests`, Beispiel
`propwash_example:dispenser_recessed`): Modell und Ersatzform an den vier eingebauten Rahmen, Hülle, Schuss aus der
Mündung. `addon-api-17: Zubehör am Rahmen` misst dazu an jedem eingebauten Rahmen einen eingerückten Prüfquader und jedes
angemeldete Zubehör, auch aus Add-ons. Bildschirmfotos (nur Fabric) `accessory_recess_race`, `_freestyle`, `_cinewhoop`,
`_long_range` (im Repo); mit dem Kamera-Add-on bzw. Just More Parts kommen `accessory_recess_camdrones_cine` und
`accessory_recess_jmp_deadcat` dazu (nicht ins Repo).
