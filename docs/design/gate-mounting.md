# Gates an Wand und Decke, Tauchtore und Drehen (race)

Anforderung (02.10.): Race-Gates lassen sich an Wänden und Decken platzieren und drehen, sodass man an der Wand von vorne
oder von oben hindurchfliegen kann (Dives). Aus der Community kam derselbe Wunsch (Discord, „Idea Item Frame“): Gates
waagerecht an Wänden, um durchzutauchen, oder ein eigenes „dive race gate“; der Rennteil soll ohnehin mehr Gates und
eine bessere Platzierung bekommen.

Ergebnis: Jedes Gate (alle drei Größen, auch Start/Ziel) lässt sich auf den **Boden**, an **Wände** und an **Decken**
setzen und in 90°-Schritten drehen. Es gibt **kein** eigenes Tauchtor-Item: Ein Tauchtor ist ein gewöhnliches Gate in
der Lage „Tauchtor“ an einer Wand. So bleiben Rezepte, Kreativ-Tab, Survival-Tabellen und Kurs-Werkzeug unverändert, und
jede Größe kann jede Lage.

## 1. Recherche: Tauch-, Hänge- und Wandtore im echten Drohnenrennen

- **MultiGP Dive Gate (7 × 6 ft):** ein Tor in rund 15 ft (≈ 4,5 m) Höhe, leicht schräg gestellt, damit man von oben
  einfliegt; die Piloten tauchen in einer abwärts gerichteten Bahn hindurch. Das Gegenstück ist das **Launch Gate**, das
  man von unten nach oben durchsteigt („Panels should be facing the ground since pilots will be entering the gate from
  below“). **Ladder** = drei Gates übereinander, Türme (Towers) = ein Gate erhöht auf einem Gestell.
  ([MultiGP Course Obstacles](https://www.multigp.com/multigp-drone-race-course-obstacles/))
- Typische Maße liegen bei 5 × 5 ft für Standard-Gates und bis 7 × 6 ft Öffnung; „Dive“-Elemente zwingen zu schnellem
  Höhenverlust und brauchen danach eine gerade Erholungsstrecke.
  ([UAVMODEL: Track Design](https://blog.uavmodel.com/5997-2/))
- **Split-S** und Sturzflug durch ein unteres Gate nach einem oberen sind Standard-Elemente in Rennkursen.
  ([GetFPV Flight Academy](https://www.getfpv.com/learn/fpv-flight-academy/drone-racing-the-eight-common-track-elements/))
- **Profi-Ligen** (DRL) bauen Formen-Gates auf Traversen-Türme oder hängen sie in Hallen; die Gates sind fest
  montiert statt auf Füßen. ([Atomic Design: DRL 2022](https://www.atomicdesign.tv/portfolio/drone-racing-league/))
- **Indoor-/Micro-Gates** haben Aufhängeschlaufen und Klettflächen, um sie an Decken oder Wänden zu befestigen; eine
  Aufhängung reicht, um sogar ein zweites Gate darunter zu tragen.
  ([weBLEEDfpv Micro Gates](https://webleedfpv.com/products/webleedfpv-micro-drone-racing-gates-wbmdrg),
  [Amazon: FPV Gates](https://www.amazon.com/fpv-gates/s?k=fpv+gates))

Folgerungen für Propwash: (1) Ein Tauchtor ist ein **waagerechtes** Tor, das man von oben nach unten durchfliegt –
die Flugrichtung muss umkehrbar sein (Launch Gate). (2) Hängende Tore hängen an Gurten/Stangen unter der Decke und
bleiben aufrecht. (3) Wandtore sind entweder ein Fenster in der Wand (Öffnung ausgeschnitten) oder ragen aus der Wand.
Ein schräg gestelltes Tor (MultiGP) bauen wir hier nicht nach: Kollisionsboxen bleiben achsparallel (90°-Schritte).
Seit 0.5.0 geht es über die freie Drehung (`docs/design/gate-free-transform.md`).

## 2. Orientierungsmodell (`race.GateMount`, `race.GateGeometry`)

Jedes Gate hat eine **Lage** (`GateMount`) auf einer von drei **Flächen** (`GateMount.Surface`), eine Richtung in
90°-Schritten (`yRot` des Entity, wie bisher) und bei umkehrbaren Lagen ein Bit **umgedreht** (`flipped`).

| Lage (`GateMount`, Schlüssel) | Fläche | Tor-Ebene | Durchflug | Stellungen |
|---|---|---|---|---|
| `STANDING` (`standing`) | Boden | senkrecht | waagerecht, in Blickrichtung beim Setzen | 4 Richtungen (wie bisher), stapelbar |
| `FLOOR_FLAT` (`floor_flat`) | Boden | waagerecht, liegt auf dem Boden | senkrecht, Standard abwärts (in eine Grube) | ab/auf |
| `WALL_DIVE` (`wall_dive`) | Wand | waagerecht, unterer Balken an der Wand, Öffnung ragt heraus | senkrecht, Standard **abwärts** (Tauchtor) | ab/auf |
| `WALL_SIDE` (`wall_side`) | Wand | senkrecht, quer zur Wand, ein Pfosten an der Wand | waagerecht an der Wand entlang | rechts/links |
| `WALL_WINDOW` (`wall_window`) | Wand | senkrecht auf der Wandfläche (Fenster) | durch die Wand, Standard in die Wand hinein | hinein/heraus |
| `HANGING` (`hanging`) | Decke | senkrecht, hängt an zwei Gurten 1 Block unter der Decke | waagerecht | 4 Richtungen |
| `CEILING_FLAT` (`ceiling_flat`) | Decke | waagerecht, bündig unter der Decke | senkrecht, Standard abwärts | ab/auf |

`FLOOR_FLAT` ist eine Ergänzung zur Vorgabe (Abweichung, §11): Mit dem allgemeinen Modell kostet sie nichts und gibt
Bauern den „Grubensprung“ (liegendes Tor über einem Loch), das Gegenstück zum Deckentor.

**Rahmen.** `GateGeometry` beschreibt jedes Gate mit einem orthonormalen, rechtshändigen Rahmen: Ursprung = Mitte der
Unterkante (bei stehenden Gates wie bisher der Entity-Ursprung), `side` (Breite), `up` (Höhe), `normal`
(Flugrichtung, Einflug auf der −normal-Seite), mit `side = up × normal`. Aus der Richtung `yaw` ergeben sich
`f = (−sin yaw, 0, cos yaw)` (bisherige Normale) und `r = (cos yaw, 0, sin yaw)` (bisherige Seite); `s = −1`, wenn
umgedreht, sonst 1:

| Lage | side | up | normal |
|---|---|---|---|
| `STANDING`, `HANGING` | r | +Y | f |
| `FLOOR_FLAT`, `CEILING_FLAT`, `WALL_DIVE` | s·r | f | −s·Y |
| `WALL_SIDE` | −s·f | +Y | s·r |
| `WALL_WINDOW` | −s·r | +Y | −s·f |

Bei Wandlagen ist `yaw` die Richtung aus der Wand heraus (die geklickte Blockseite, `Direction#toYRot`). Bei
`STANDING` sind alle Werte bitgleich zu vorher (gleiche Trigonometrie, Null-Anteile), alte Kurse, Geister, Bestenlisten
und Goldens bleiben gültig.

**Befestigungspunkt `P`** (Punkt auf der Fläche, um den sich das Gate beim Drehen an Ort und Stelle dreht) und
Ursprung (`h` Höhe, `w` Breite, `d` Tiefe 0,5, `F` = Richtung aus der Wand):

| Lage | Ursprung | P |
|---|---|---|
| `STANDING` | P | Bodenpunkt unter der Mitte |
| `FLOOR_FLAT` | P + Y·d/2 − f·h/2 | Bodenpunkt unter der Öffnungsmitte |
| `HANGING` | P − Y·(1 + h) | Deckenpunkt über der Mitte |
| `CEILING_FLAT` | P − Y·d/2 − f·h/2 | Deckenpunkt über der Öffnungsmitte |
| `WALL_DIVE` | P | Wandpunkt auf Höhe der Tor-Ebene, Mitte des unteren Balkens |
| `WALL_SIDE` | P + F·w/2 − Y·h/2 | Wandpunkt auf Höhe der Öffnungsmitte |
| `WALL_WINDOW` | P + F·d/2 − Y·h/2 | Wandpunkt hinter der Öffnungsmitte |

## 3. Platzieren (`race.GatePlacement`, `race.GateItem`)

- Die geklickte Blockseite wählt die Fläche: oben → Boden (wie bisher, `BlockPlaceContext#getClickedPos`, auch auf
  ersetzbaren Blöcken), unten → Decke, Seite → Wand.
- **Blöcke ohne Kollision** (Gras, Farn, Blumen, Fackeln, Ranken, Glühflechten, Höhlenranken, Schnee einer Lage) sind
  keine Fläche, sondern stehen auf einer (`GateItem#click`): Liegt der Klickpunkt höchstens 0,1 Block vor einer
  tragfähigen Seite dahinter (Ranke an der Wand, Flechte unter der Decke), zählt diese Wand oder Decke; sonst der
  Boden unter dem Block, wenn er oben tragfähig ist (Gras, Blumen: stehendes Gate in der Zelle der Pflanze, wie vor
  0.4.0 bei Gras); sonst eine tragfähige Seite dahinter; sonst der erste Block mit Kollision bis 3 Blöcke weiter auf
  dem Blickstrahl; sonst Boden in der Zelle selbst. Mit Schleichen + Taste wechselt man nur die Lage der gefundenen
  Fläche.
- **Tragfähigkeit:** Wand- und Deckenlagen sitzen auf der Blockgrenze, wenn die Seite tragfähig ist
  (`BlockState#isFaceSturdy`). Sonst (obere Stufen, Treppen, Zäune, Mauern) liegt die Befestigungsebene auf der
  getroffenen Fläche selbst (auf 1/16 Block gerundet): Unter einer oberen Stufe hängt das Gate an deren Unterseite,
  nicht einen halben Block tiefer. Bodenlagen bleiben unverändert.
- Jede Fläche merkt sich je Spieler eine Lage und eine Stellung (`GatePlacement.Preference`): Standard Boden
  `STANDING`, Wand `WALL_DIVE` (abwärts), Decke `HANGING`. Stehende und hängende Gates zeigen in Blickrichtung (+90°
  je Stellung), liegende Gates mit der Oberkante vom Spieler weg, Wandlagen nach der Wand; `WALL_SIDE` fliegt ohne
  Drehung nach rechts (vom Spieler aus gesehen, der zur Wand schaut).
- **Raster:** Stehende und hängende Gates sitzen wie bisher in Blockmitte. Bei allen anderen Lagen liegen die Kanten
  der Öffnung auf Blockgrenzen: Ist die Öffnung entlang einer Achse ungerade breit (Medium 3 × 3, Large 5 × 3), sitzt
  ihre Mitte in der Mitte des geklickten Blocks, bei gerader Breite (Small 2 × 2) auf der Blockkante, die dem
  Klickpunkt am nächsten ist. Für ein Fenstergate klickt man also die Wand dort an, wo die Mitte der Öffnung liegen
  soll, und gräbt danach die Blöcke hinter der Öffnung aus (die Vorschau zeigt sie). Tauchtore liegen auf halber Höhe
  des geklickten Blocks.
- Platz nötig wie bisher (`GateItem#free`: keine Blöcke im Gate, keine anderen Gates), Überlebensregeln unverändert
  (`mayBuild`, Item wird verbraucht, Abbauen gibt das Item zurück). Die Bauhöhe prüft `GateItem#insideWorld` mit der
  echten Hülle (`GateGeometry#bounds`): Liegende Lagen sind nur 0,5 Block hoch, ein Tauchtor oder Deckengate passt also
  bis an die Obergrenze der Welt.
- **Stapeln** nur für stehende Gates am Boden (Ziel und neues Gate `STANDING`); ein Klick mit einem Gate auf ein
  anderes Gate meldet sonst „Nur stehende Tore am Boden lassen sich stapeln“.

**Vorschau** (`race.client.GatePreview`): Mit einem Gate in der Hand und Blick auf einen Block zeichnet der Client das
Gate durchscheinend an der Stelle, an der es landen würde, mit demselben Renderer wie das echte Gate (Rahmen, Lichter,
Halterungen), dazu einen Pfeil durch die Öffnung in Flugrichtung. Weiß/grün = frei, rot = blockiert; der Hinweis
nennt den Grund („kein Platz“ oder „hier darfst du nicht bauen“, etwa im Abenteuermodus). Nicht im Flug
(Pilot-Sitzung), nicht mit F1.

## 4. Steuerung (`race.client.GateMountKeys`)

- Taste **„Gate drehen“** (Kategorie Propwash, Standard **R**, dieselbe Taste wie „Flugmodus wechseln“; Besitzer-
  Entscheidung vom 02.10.: man hat entweder das Gate bzw. das Kurs-Werkzeug oder die Funke in der Hand, die beiden
  Bedeutungen kommen sich nie in die Quere; „Hand“ heißt Haupt- oder Nebenhand, ein Gate in der Nebenhand zählt also
  auch neben der Funke). Was ein Druck auf die Taste tut, entscheidet der reine Router
  `race.client.GateKeyRoute` aus Kontext und Hand (JUnit `GateKeyRouteTest`):
  - offenes Fenster: nichts,
  - **Pilot-Sitzung** (Funke, Flug läuft): die Taste gehört dem Flugmodus. `PilotSession` wechselt ihn wie bisher
    über die eigene Belegung `key.propwash.drone_flight_mode`, Gate drehen schweigt – auch mit einem Gate in der
    Nebenhand,
  - mit einem Gate in der Hand: nächste **Stellung** der Lage der Fläche, auf die man schaut (stehend/hängend +90°,
    sonst Flugrichtung umkehren),
  - **Schleichen + Taste**: nächste **Lage** dieser Fläche (Boden: stehend ↔ liegend; Wand: Tauchtor → seitlich →
    Fenster; Decke: hängend ↔ liegend), Stellung zurück auf Standard,
  - Blick auf ein **platziertes Gate** mit einem Gate-Item oder dem Kurs-Werkzeug in der Hand: dasselbe für dieses
    Gate, an Ort und Stelle (um `P`). Mit leerer oder anderer Hand kommt der Hinweis „Zum Drehen ein Gate oder das
    Kurs-Werkzeug in die Hand nehmen“; mit der **Funke** in der Hand (und keinem Gate in der anderen Hand) bleibt die
    geteilte Taste still (sie gehört dort dem Flugmodus, der außerhalb eines Flugs nichts tut). So dreht niemand im Rennen versehentlich ein Kurs-Gate und
    archiviert damit die Bestenliste.
  - **Eigene Belegung:** Legt jemand eine der beiden Tasten um, gilt jede wieder für sich – „Gate drehen“ dreht ohne
    Flug wie oben (mit Funke und Blick aufs Gate dann wieder mit Hinweis), im Flug bleibt sie still, „Flugmodus
    wechseln“ wirkt nur im Flug. Geteilt heißt: beide sind auf dieselbe Taste mit demselben Modifikator gelegt
    (`GateMountKeys#sharedWithFlightMode`; Modifikatoren gibt es nur auf NeoForge, `IClientPlatformHelper#sameKeyModifier`).
- **Keine Doppelbelegungs-Warnung für dieses Paar:** `KeyDefaults.SHARED` nennt die Paare, die eine Taste teilen
  dürfen (nur `drone_flight_mode` + `gate_rotate`). Ein gemeinsamer Mixin `mixin.input.client.KeyMappingMixin` auf
  `KeyMapping#same` (beide Loader) meldet für dieses Paar „nicht gleich“; `same` benutzen nur die Steuerungs-Liste
  (`KeyBindsList.KeyEntry#refreshEntry`, Vanilla und NeoForge) und Tasten-Mods wie Controlling. Jede andere
  Doppelbelegung (etwa „Gate drehen“ auf derselben Taste wie „Scharf schalten“) bleibt markiert. Der Tab „Tasten“ im
  Konfigurator zeigt das Paar nicht als Konflikt, sondern im Tooltip „Teilt sich die Taste mit: …“.
  **Modifikatoren (NeoForge):** Die Ausnahme gilt nur bei gleichem Modifikator. Liegt etwa „Flugmodus wechseln“ auf
  Shift+R und „Gate drehen“ auf R, verteilt NeoForges `KeyMappingLookup` Shift+R nur an den Flugmodus – Schleichen + R
  käme bei „Gate drehen“ nie an. Dann greift NeoForges eigenes `same` (IN_GAME-Kontext, ein Modifikator NONE →
  Konflikt), die Steuerung und der Tab „Tasten“ markieren das Paar, und der Router behandelt die Tasten als getrennt.
  Selbsttest „race: Taste R ohne Doppelbelegungs-Warnung in der Steuerung“ prüft das auf NeoForge per Reflection.
  Verworfen: NeoForges `IKeyConflictContext` – `KeyMapping#same` fällt in NeoForge 26.3.0.10-beta bei nicht
  kollidierenden Kontexten auf den reinen Tastenvergleich zurück (`.ref/neoforge/net/minecraft/client/KeyMapping.java`,
  `same`), die Steuerung würde also trotzdem warnen; außerdem filtert `isActive()` die Klicks nur auf NeoForge, der
  Router müsste dann je Loader anders arbeiten. Verworfen: eine gemeinsame Belegung für beide Funktionen – dann ließe
  sich keine der beiden allein umlegen. Vanilla (Fabric) warnt bei zwei Standardbelegungen ohnehin nicht
  (`!isDefault()`-Bedingung), NeoForge schon – der Mixin deckt beide und auch geänderte Belegungen ab.
- Rückmeldung im Propwash-HUD („Wand · Tauchtor · abwärts“), Shift-Tooltip und Flugschule nennen die Taste über
  `Component.keybind`, zeigen also die tatsächliche Belegung. Der Shift-Tooltip der Gates sagt zusätzlich „Im Flug
  wechselt %s den Flugmodus“ (mit der echten Belegung von „Flugmodus wechseln“), der des Kurs-Werkzeugs „Blick aufs
  Gate + %s: drehen, Schleichen: Lage“.
- Wer die Nachtversion vor dem 02.10. gespielt hat, hat in `options.txt` noch `key_key.propwash.gate_rotate` auf der
  Taste links der 1 stehen (Minecraft speichert jede Belegung); „Zurücksetzen“ in der Steuerung legt sie auf R. Eine
  Migration gibt es bewusst nicht: 0.4.0 war noch nicht veröffentlicht.
- Die Wahl schickt der Client als `GatePreferenceC2S` an den Server (je Fläche Lage + Stellung); der Server benutzt sie
  in `GateItem#useOn`. Ohne Paket gelten die Standards. Beim Abmelden vergessen.
- Drehen platzierter Gates: `GateRotateC2S(entityId, lageWechseln)`, **serverseitig** geprüft: Reichweite
  (Interaktionsreichweite + 2), `mayBuild`, Kursbesitz wie beim Kurs-Werkzeug (`CourseRegistry.canEdit`), Platz für das
  gedrehte Gate (ohne das Gate selbst, mit Spielern und Drohnen). Gestapelte Gates drehen nur um 180° und wechseln die
  Lage nicht. Gedreht wird um `P`, danach zieht `GatePlacement#snapped` das Gate auf das Raster der neuen Lage (§3):
  Bei Small (gerade Öffnung) liegt es so genau dort, wo ein frisch gesetztes Gate läge, höchstens 0,5 Block neben `P`;
  bei Gleichstand gewinnt die kleinere Koordinate, damit Lagenzyklen (Wand: Tauchtor → seitlich → Fenster → Tauchtor,
  Boden und Decke: stehend/hängend ↔ liegend) an derselben Stelle ankommen. Medium und Large bleiben exakt um `P`.
  Ein Kurs-Gate bleibt im Kurs; `CourseRegistry#gateRotated` übernimmt die neue Lage und zählt das wie jede andere
  Kursänderung (`layoutChanged`): Rekord-Geister des Kurses fallen weg, gibt es Zeiten, wandert die Bestenliste ins
  Archiv, die Layout-Nummer steigt und die Spieler in der Dimension bekommen die Meldung „Bestenliste archiviert“.
- Rechtsklick, Linksklick, Kurs-Werkzeug und Stapeln bleiben unverändert. Seit 0.5.0 startet Schleichen + Rechtsklick
  mit Gate oder Kurs-Werkzeug „Gate einstellen“ als Einstell-Modus im Spiel; das Entfernen aus dem Kurs ist dort ein Knopf
  (`docs/design/gate-free-transform.md`, `docs/design/gate-edit-mode.md`).

## 5. Geometrie, Durchflug und Kollision

- `crossing(...)` prüft den Wechsel der Seite der Ebene durch den Ursprung mit der Normalen und das Öffnungsrechteck
  in (side, up) – für jede Lage dieselbe Rechnung, Client (`LapTracker`, Geister-Splits) und Server (`RaceServer`,
  `LapVerifier`) benutzen sie unverändert. Ein Tauchtor zählt einen Flug von oben nach unten, nicht von unten nach oben
  (Falschrichtung wie bisher).
- Kollisionsboxen (`boxes`): Pfosten, Balken (wie bisher je Stapelrolle) im Rahmen gedreht, achsparallel; hängende
  Gates bekommen dazu zwei Gurte (0,125 × 1 × 0,125) bis zur Decke. Entity-Box = Hülle aller Boxen, Auswahl/Picking
  über die Boxen wie bisher.
- Ideallinie (`RaceLine`): Mitte der Öffnung (`centerX/Y/Z`) und volle 3D-Normale; die Umweg-Regel für Stapel prüft
  den Versatz entlang `up`. Bei stehenden Gates bitgleich.
- Markierungen: Pfeile liegen in der Ebene unter der Öffnung vor der Einflugseite (beim Tauchtor auf der Wand über dem
  Tor, Richtung abwärts), Nummern-Tafel/Banner über dem oberen Balken in der Tor-Ebene, rote Rückseiten-Marken hinten.
  Hängende Gates haben Platz für Tafel und Banner zwischen Gate und Decke.

## 6. Darstellung (race, assets-world)

- Dieselben Modelle, gedreht mit dem Rahmen (`GateRenderer`, Quaternion aus side/up/normal).
- Neue Rahmen-Variante `_mount` (wie `_frame` ohne Standfüße; Lichter und Start-Rahmen ebenso) für Lagen ohne Boden.
- Halterungen statt Füßen (`tools/assets/world/generate.py`): `gate/mount_plate` (Montageplatte mit Warnstreifen,
  Maße wie ein Fuß) und `gate/hanger` (Gurt mit Deckenplatte und Klemme, 1 Block). Tauchtor: die Füße des Rahmens
  liegen als Platten an der Wand; seitliches Wandtor: zwei Platten am Wandpfosten; hängendes Gate: zwei Gurte an den
  Pfostenköpfen; Fenster und liegende Gates liegen auf der Fläche auf.
- Lichter (Tint wie bisher), Start-Rahmen und Banner folgen dem Rahmen.

## 7. Speicherung, Netz, Kompatibilität

- Entity-NBT: `Mount` (`GateMount.CODEC`, Standard `standing`) und `Flipped` (Standard `false`). Alte Welten laden als
  stehende Gates.
- Synchronisiert: ein Byte `DATA_MOUNT` (Ordinalzahl, Bit 7 = umgedreht).
- Kurs-Daten (`propwash_race.json`): `GateRecord.mount` (Standard `standing`) und `flipped`; alte Dateien und
  Welt-Kurse laden unverändert. `CourseData.Gate` überträgt beides; `layoutHash` bezieht die Lage nur ein, wenn sie
  nicht `STANDING` ist → alte Layouts, Geister und Bestenlisten bleiben gültig.
- **Netzprotokoll 7 → 8** (neues Entity-Datenfeld, `CourseData` im Paket, zwei neue C2S-Pakete). Die Änderung gehört
  zum noch nicht veröffentlichten Release 0.4.0 (Mod-Version bleibt `0.4.0`); die Nummer steigt trotzdem, damit schon
  gebaute 0.4.0-Jars mit Protokoll 7 nicht stillschweigend mit dem neuen Stand verbinden. Die Gate-Lagen berühren die
  Parts-API nicht (keine öffentliche Klasse); `API_VERSION` ist im Release 1.4.0 wegen der Beute-API und der
  Werkbank-Ergänzungen aus den offenen Punkten (`docs/design/parts-api.md`).

## 8. Flugschule und Texte

- Szene „Gates, Kurse und Rennen“ (`race`): neuer siebter Schritt `mount` „An Wand und Decke“. Die Gates des Kurses
  verschwinden, eine Steinwand mit Überhang wächst hoch; der Pilot setzt mit Rechtsklick ein seitliches Wandgate,
  wechselt mit Schleichen + „Gate drehen“ zum Tauchtor (Hinweise `side`, `dive`), dreht die Flugrichtung (`flip`),
  hängt ein kleines Gate unter die Decke (`hanging`) und eine Drohne taucht von oben durch das Tauchtor (`diving`).
  Die Tasten zeigen die echte Belegung (`KeyToken.mapping("key.propwash.gate_rotate")`). Sobald der Pilot die Funke
  nimmt, zeigt der Hinweis `flight_mode` („Mit Funke: Flugmodus“) die Belegung von „Flugmodus wechseln“ – im Standard
  dieselbe Taste R. JUnit
  `FlightSchoolStoriesTest#theDiveGoesDownThroughTheWallGateAndTheHangingGateReachesTheCeiling` prüft, dass die Drohne
  genau einmal abwärts und nie aufwärts durchs Tauchtor fliegt und die Gurte die Decke erreichen.
- Flugschul-Gates (`ActorSpec.Gate(size, lage, umgedreht)`) zeichnen über `GateRenderer#fill` dieselben Modelle,
  Drehungen und Halterungen wie in der Welt.
- Shift-Tooltip der Gates (8 Zeilen „Benutzung“, mehr zeigt `ItemHelp.MAX_LINES` nicht): Boden, Wand oder Decke,
  Vorschau, Taste (echte Belegung über `Component.keybind`), Drehen platzierter Gates, im Flug Flugmodus (echte
  Belegung), Aufsetzen nur auf stehende Gates, Kurs-Werkzeug verbinden und abbauen, Abbauen ohne Werkzeug.
  Kurs-Werkzeug: Zeile „Blick aufs Gate + Taste: drehen, Schleichen: Lage“.
- `de_de` und `en_us` (Modul-Quellen `common/src/lang/race`, `common/src/lang/guide`).

## 9. Tests

- JUnit `GateMountTest`: Rahmen orthonormal/rechtshändig in jeder Lage × Richtung × Stellung, Boxen achsparallel und
  in der Entity-Box, Öffnung frei, Durchflug durch die Mitte zählt nur in Flugrichtung (Tauchtor abwärts ja, aufwärts
  nein; Launch-Stellung umgekehrt), `STANDING` bitgleich zu den alten Formeln, Befestigungspunkt ↔ Ursprung.
- JUnit `GatePlacementTest`: Fläche je Blockseite, Standardlagen, Raster (ungerade/gerade Öffnung), Lage-/Stellungs-Zyklen,
  Drehen um `P` mit Raster (Small landet wie frisch gesetzt, Zyklen stabil), Befestigungsebene nicht tragfähiger Seiten,
  Höhe liegender Gates.
- JUnit `GateMountPersistenceTest`: `CourseData`-Paket mit Lagen, Kurs-JSON ohne neue Felder (alt) → stehend,
  `layoutHash` alter Kurse unverändert.
- Selbsttest `race.client.GateMountSelfTests` (beide Loader): Testbau mit 41 Blöcke langer Wand (zwei Fenster
  ausgeschnitten), Decke und Grube; 14 Gates über `GateItem#useOn` mit Server-Wahl – alle sieben Lagen, jede
  Umkehr-Stellung, eine Vierteldrehung, alle drei Größen; Lage wie vorhergesagt (`GatePlacement.resolve`), Boxen in der
  Entity-Box und frei von Blöcken, zweites Gate an derselben Stelle und Stapeln auf ein Tauchtor abgelehnt. Client:
  Lage, Geometrie und Entity-Box synchron (auch stehende Gates). Drehen an Ort und Stelle: zu weit weg, umdrehen um den
  Befestigungspunkt, Lagenzyklus seitlich → Fenster → Tauchtor → seitlich, Ablehnung durch einen Block, Kurs übernimmt
  Drehungen, stehend ↔ liegend, im Stapel nur 180°; ein großes stehendes Gate um 90° gedreht hat beim Client die
  gedrehte Entity-Box. Flächen: Klick auf die Seite eines Grasbüschels und auf eine Mohnblume setzt ein stehendes Gate
  in die Zelle der Pflanze, unter Glühflechten hängt das Gate an der Decke, unter einer oberen Stufe an deren
  Unterseite. Nach der bestätigten Runde: Drehen des hängenden Kurs-Gates archiviert die Bestenliste (Layout + 1,
  Prüfsumme neu), weitere Drehungen ohne neue Zeiten legen kein weiteres Layout an. Kurs aus Start-Gate, Tauchtor und hängendem Gate (Nummern beim Client),
  geskriptete Runde: Client-`LapTracker` zählt abwärts, nicht aufwärts; Server (`RaceServer#onDroneMoved` →
  `LapVerifier`) bestätigt die Runde abwärts und hält die Runde mit Tauchtor aufwärts zurück (`UNVERIFIED`). NBT mit und
  ohne `Mount`/`Flipped`. Bildschirmfoto-Modus (Fabric): `gate_mount_wall_dive`, `gate_mount_wall_side_window`,
  `gate_mount_ceiling`, `gate_mount_ceiling_below`, `gate_mount_floor`, `gate_mount_preview`,
  `gate_mount_preview_blocked`.

- JUnit `GateKeyRouteTest`: Entscheidungstabelle der Taste (Gate, Kurs-Werkzeug, Funke, leere Hand; mit und ohne Gate im
  Blick, Schleichen, Pilot-Sitzung, geteilte oder eigene Belegung, offenes Fenster). `KeyDefaultsTest`: „Gate drehen“
  liegt auf R wie „Flugmodus wechseln“, das Paar zählt nicht als Doppelbelegung, ein dritter Eintrag auf derselben
  Taste schon.
- Selbsttest `race.client.GateKeySelfTests` (beide Loader): Steuerung (`KeyBindsScreen`) zeigt beide Einträge als
  „R“ ohne Klammern, „Scharf schalten“ testweise auf R wird als Konflikt „[ R ]“ markiert, nach dem Umlegen von
  „Flugmodus wechseln“ ist nichts mehr geteilt. Danach echte Tastenklicks: Gate in der Hand dreht die Vorschau,
  Schleichen + R wechselt die Lage, mit Funke oder Kurs-Werkzeug (ohne Gate im Blick) ändert R nichts, im Flug mit
  Gate in der Nebenhand wechselt R den Flugmodus und nicht die Vorschau, mit umgelegter Flugmodus-Taste bleibt R im
  Flug still und die neue Taste wechselt. Die Selbsttests „keine doppelten Standardtasten“ und „Tastenbelegung ohne
  Doppelungen“ lassen nur die Paare aus `KeyDefaults.SHARED` zu. Bildschirmfoto-Modus (Fabric): `controls_shared_key_de`
  (GUI-Skala 3) und `controls_shared_key` (englisch, GUI-Skala 2, sonst liegen „Cycle flight mode“ und „Rotate gate“ zu
  weit auseinander; die Szene prüft, dass beide Zeilen sichtbar sind), `tooltip_gate_rotate_de`,
  `tooltip_course_tool_rotate_de` (Shift-Tooltips), `flight_school_race_remote` (Hinweis „Mit Funke: Flugmodus“).

## 10. Abweichungen von der Vorgabe

- Taste seit 02.10. `R` wie „Flugmodus wechseln“ (Besitzer-Entscheidung, Router nach Hand und Flug, §4). Vorher die
  Taste links der 1 (`KEY_GRAVE`).
- Zusätzliche Lage `FLOOR_FLAT` (liegendes Bodentor) – symmetrisch zum Deckentor, ohne Mehrkosten.
- Eine Taste statt zwei: Stellung mit der Taste, Lage mit Schleichen + Taste.
- Tauchtore haben eine feste Lage in der Ebene (unterer Balken an der Wand); eine zweite Drehung um 90° in der Ebene
  (Pfosten an der Wand) wäre nur bei Large anders und ist nicht vorgesehen.
- Hängende Gates haben einen festen Abstand von 1 Block zur Decke (Platz für Tafel und Banner).
- Pfeile vor schwebenden Gates (hängend, seitlich, Fenster) schweben auf Höhe der Unterkante statt auf dem Boden.
- Platzierte Gates dreht die Taste nur mit einem Gate oder dem Kurs-Werkzeug in der Hand (Vorgabe ließ es offen;
  Schutz vor versehentlichem Drehen von Kurs-Gates im Rennen).
- Kein eigenes Tauchtor-Item: jede Größe kann jede Lage, Rezepte, Kreativ-Tab und Beute bleiben unverändert.

## 11. Offene Punkte

- Schräg gestellte Tauchtore (MultiGP-Stil): erledigt in 0.5.0 mit der freien Drehung (`docs/design/gate-free-transform.md`).
- Frei wählbarer Abstand zur Decke.
- Gates in Rahmen/Item Frames (gewünscht war, dass Drohnen in Rahmen flach wie an der Wand hängen) gehören nicht zu
  diesem Auftrag.
