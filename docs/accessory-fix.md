# Zubehör: echte Beladung, abgestellt feuern, Tempo zwischen den Ticks, nur an bestimmten Rahmen (Add-on-API 1.7)

Für Add-on-Autoren. Drei Befunde aus einem Add-on mit Dispenser-Halterung sind behoben: die Beladung wurde stumm auf 2 kg
gekappt, eine abgestellte Drohne konnte nicht feuern, und das Drohnentempo am Server war im Pilotenflug manchmal 0.
Dazu kann ein Add-on ein Zubehör jetzt auf bestimmte Rahmen beschränken (Abschnitt 4), und die Items eines Add-ons mit
eigenem Kreativ-Tab stehen nicht mehr zusätzlich im Propwash-Tab (Abschnitt 5).
Stabil ist nur, was unter `de.lardtom.propwash.api` steht; die Anleitung steht in
[`api/addon-guide.md`](api/addon-guide.md) Abschnitte 3.4, 3.5, 20.2 und 21.6.

## 1. Beladung mit echter Masse

- `AccessoryBehaviors#payloadGrams` liefert die echte Beladung: eigenes Gewicht aus `AccessoryBehavior#payloadGrams`
  (nicht endlich oder Fehler → Item-Gewichte, negativ → 0) oder die Summe der Item-Gewichte, Behälter samt Inhalt.
  Die stille Kappung auf 2000 g je Halterung ist weg; `AccessoryInventory#payloadGrams()` und die Wartung zeigen die
  echte Zahl.
- Ins Flugmodell geht die Beladung über die beladene Variante des Zubehörs (`AccessoryType#laden`, 5-g-Schritte). Die
  einzige Grenze ist die jedes Bauteils: Zubehör samt Beladung höchstens 20 kg (`AccessoryPayload.MAX_LADEN_GRAMS`,
  gleich der Grenze von `PartBasics`). `AccessoryPayload.limitGrams(type)` = 20 kg − Masse des Zubehörs,
  `flightGrams(type, g)` kappt darauf, `massStep(type, g)` rundet auf 5 g ab, wenn die Grenze sonst überschritten würde.
  Knockback, Stöße, Treffer und `DronePhysics` rechnen mit derselben Masse wie das Flugmodell.
- Physik: Bis zur Grenze bleibt das Flugmodell stabil (JUnit: jede Vorlage an jeder Halterung mit 20 kg, 3 s Vollgas,
  endlich, sinkt nicht ein, kippt nicht, hebt nicht ab). Eine schwere Beladung macht die Drohne träge, eine zu schwere
  lässt sie nicht abheben: der 5-Zöller mit vier vollen Stein-Stapeln (12,8 kg) bleibt bei Vollgas am Boden, mit einem
  Stapel (3,2 kg, früher auf 2 kg gekappt) steigt er langsam; der 7-Zöller mit 3,2 kg steigt deutlich langsamer als mit
  2 kg.
- „Überladen“ heißt: echte Beladung über `limitGrams` (`AccessoryPayload.overloaded`). Dann zeigt
  - die Wartung (Fenster „Zubehör“) „Beladung … g · überladen“ in Rot, der Hinweis nennt Grenze und getragene Masse,
  - das Drohnen-Item (Werkbank, Inventar) die Statuszeile „Überladen: ein Zubehör wiegt samt Beladung über 20 kg“,
  - das OSD die Warnung `ÜBERLADEN` (`OsdWarnings.Warning.OVERLOADED`, blinkt, Stufe Warnung).

## 2. Abgestellt feuern

- `MountPose#front()` ist die Mündung: Standard die Mitte der Vorderfläche des Quaders aus `size_mm` (wie bisher,
  bitgleich), mit `muzzle_mm` ein eigener Punkt. `AccessoryDefinition#muzzleMm()` (neue letzte Record-Komponente,
  `Optional<Vec3Mm>`), Builder `muzzle(x, y, z)`, JSON `"muzzle_mm": [x, y, z]`: Versatz von der Vorderflächen-Mitte in
  mm, x rechts, y oben, −z vorn, in den Achsen des Zubehörs; höchstens 20 mm außerhalb der Box
  (`MAX_MUZZLE_OUTSIDE_MM`). Konstruktoren mit 12, 13 und 14 Argumenten bleiben (ohne Mündung); der Codec schreibt das
  Feld nur, wenn es gesetzt ist; der Teile-Hash im Handshake sieht es (Record-`toString()`).
- Warum die abgestellte Drohne nie feuern konnte: Die Box einer Halterung vorn hängt um ihre Höhe unter dem Anbaupunkt,
  der Anbaupunkt liegt am Rahmenboden (cinewhoop_3 auf Bodenhöhe, freestyle_5 3 mm darüber). Die Hülle der Drohne zählt
  vorn und unten angebautes Zubehör nicht mit, eine abgestellte Drohne steht also auf dem Rahmen, und die Box steckt im
  Boden. Bei einer 58 mm hohen Halterung lag die Vorderflächen-Mitte 29 mm im Boden, `dispense` meldete
  immer `BLOCKED`.
- Jetzt (`accessory.dispense.DispenseOrigin`): Steht die Drohne auf dem Boden (`onGround` oder fester Block bis 5 cm
  unter dem Hitbox-Boden), trägt sie ihr Zubehör wie in echt. Der Abgabepunkt rückt um die Tiefe nach oben, um die die
  unterste Ecke der Box unter die Standfläche ragt (Ecken über `AccessoryAnchor#corners`, also mit Neigung, Streckung
  und Lage der Drohne). Bei einer Halterung vorn liegt die Mündung dann eine halbe Box-Höhe über dem Boden. TNT und
  ausgeworfene Items starten nicht unter der Standfläche, der Rückstoß greift weiter am Rahmenpunkt der Mündung. Im Flug
  ändert sich nichts.
- `BLOCKED` nur, wenn dieser Punkt in der Kollisionsform eines Blocks liegt, der Chunk nicht geladen ist oder ein
  Vanilla-Verhalten nichts bewirkt.
- Offen: Modell und Hülle stehen weiter auf dem Rahmen, die Box steckt sichtbar im Boden. Dass die abgestellte Drohne
  auch sichtbar auf ihrem Zubehör steht, wäre eine Änderung an Hülle und Bodenkontakt.

## 3. Tempo zwischen den Ticks

- Im Pilotenflug setzt der Server die Position der Drohne aus den Paketen des Piloten (`DroneStateC2S`, Takt
  `stateSendHz`, Standard 20 Hz, an Bildern ausgerichtet). Kam in einem Server-Tick kein Paket, war die Bewegung zwischen
  zwei Ticks 0, in einem Tick mit zwei Paketen doppelt; Aktionen zwischen zwei Ticks bekamen den Wert des letzten Ticks.
  Gemessen bei 5,6 m/s: 4–5 von 40 Ticks 0.
- Jetzt (`accessory.MotionTrack`, gefüttert über `QuadcopterEntity.SERVER_MOVED` mit der Empfangszeit `Util.getNanos()`):
  je Achse der Median der Steigungen aller Positionspaare im Fenster der letzten 300 ms, die mindestens 40 ms
  auseinanderliegen (Theil-Sen; reicht das Fenster nicht, ältere Positionen bis 500 ms). Der Median hält einzelne zu spät
  verarbeitete Pakete aus, etwa nach einem langen Server-Tick; Pakete, die weniger als 2 ms nacheinander ankommen
  (gebündelt), zählen nur mit der neuesten Position. Ein Sprung der Position, die nicht vom Piloten kommt
  (Zurücksetzen, Teleport), beginnt die Spur neu. Höchstens so schnell, wie die Tempo-Regel des Piloten erlaubt
  (`StateValidation.refillRate`, Regel × 1,1, sonst 165 m/s), damit gebündelte Pakete eines veränderten Clients
  Geschossen kein Übertempo geben. Kommen 500 ms lang keine Positionen oder hat die Drohne keinen Piloten, gilt die
  Bewegung zwischen zwei Ticks (abgestellt, Heimflug, autonom); eine ruhende Drohne hat 0.
- Gilt für `AccessoryContext#velocity()`, `dispense` (Tempo-Vererbung), `DroneQueries`/`DronePhysics` und alles, was
  `DroneMotion.velocity` liest. Der Client rechnet wie bisher aus der eigenen Bewegung.

## 4. Zubehör nur an bestimmten Rahmen

Ein Add-on konnte ein Zubehör nicht auf bestimmte Rahmen beschränken; ein Gimbal für Kamera-Drohnen passte an jede
Drohne mit Halterung vorn. Jetzt (Befund APIFRAMES-1, API 1.7 additiv):

- `AccessoryDefinition#frames()` (`Set<Identifier>`, neue letzte Record-Komponente, sortiert und unveränderlich), Builder
  `frames(Identifier...)`, JSON `"frames": ["propwash:cinewhoop_3", …]` in `propwash_parts/accessory/*.json` (auch eine
  einzelne Id). Leer oder ohne Angabe: jeder Rahmen mit passender Halterung, wie bisher. Ein leerer Eintrag ist ein
  Fehler mit Feldname (`frames[1]`). Konstruktoren mit 12 bis 15 Argumenten bleiben; der Codec schreibt das Feld nur,
  wenn es gesetzt ist; der Teile-Hash im Handshake sieht es (Record-`toString()`, durch die Sortierung unabhängig von
  der Reihenfolge in der Datei).
- `DroneBuild#problems()` meldet ein Zubehör an einem anderen Rahmen als `ACCESSORY_FRAME` (API-Text `accessory_frame`,
  `propwash.build.issue.accessory_frame`), nach `accessory_mount`. Wie die übrigen Halterungsprobleme ist es ein
  Kompatibilitätsproblem: Mit der Welt-Regel „Teile müssen zusammenpassen“ aus wird es zur Warnung. Damit prüfen
  Werkbank, Importe, Presets (`createDrone`), PW3-Codes, `PropwashApi.builds().check` und der Web-Export
  (`RuleCheck`, Regel `accessory_frame`, `parts.json` mit `data.frames`) dasselbe.
- Welt-Regel aus (Befund DEC2810-2, wie bei allen anderen Passungsregeln): Das Zubehör ist an jedem Rahmen mit
  passender Halterung erlaubt. Die Werkbank nimmt es an und baut, `builds().check` und PW3-Codes nennen
  `accessory_frame` nur unter den Warnungen, `createDrone` und der Import einer neuen Drohne bauen sie, und sie darf
  fliegen. Die Regel liest jede Stelle über `WorldRules`, keine prüft `DroneBuild#problems()` ohne sie; ungefiltert
  bleibt nur der Web-Export, der keine Welt-Regel kennt und das Problem wie jede Passungsregel meldet.
- Werkbank (`DroneWorkbenchMenu#fitsFrame`, `BuildSlots.fitsFrame`): Die Halterung nimmt das Zubehör nicht an, solange
  ein anderer Rahmen im Rahmen-Slot liegt und die Regel an ist, auch nicht per Shift-Klick. Hält der Spieler es über die
  leere Halterung, zeigt sie „Passt nicht an diesen Rahmen, nur an: …“ (`DroneWorkbenchScreen#otherFramesHint`).
  Liegt das Zubehör schon vor dem Rahmen in der Halterung, baut die Werkbank nicht und zeigt das Problem. Der Tooltip
  „Passt zu“ (`PartTooltips.fits`) nennt nur die erlaubten Rahmen.
- Bestehende Drohnen: Speicherstand, Netz und Sharecodes lesen das Zubehör weiter ein (geprüft wird beim Laden nur die
  Halterung), nichts wird gelöscht. Die Drohne zeigt das Problem wie eine Drohne mit fehlenden Teilen, lässt sich bei
  geltender Regel nicht steuern (`propwash.server.deny.parts_incompatible`) und nicht scharfschalten. In der Werkbank
  zerlegt liegt das Zubehör wieder in seiner Halterung und geht von dort ins Inventar; ohne es baut die Werkbank die
  Drohne neu.
- Beispiel: `propwash_example:cine_gimbal` (Kino-Gimbal vorn, nur `cinewhoop_3` und `long_range_7`).

## 5. Kreativ-Tab ohne doppelte Items

Ein Add-on mit eigenem Kreativ-Tab, das seine Teil-Items zusätzlich über `PropwashApi.creativeTab().add(…)` anmeldete,
zeigte sie doppelt: im eigenen Tab und im Propwash-Tab. Der Propwash-Tab blendete bisher nur Teil-Items und Presets
von Namensräumen mit eigenem Tab aus, die `creativeTab()`-Einträge nahm er immer auf. Jetzt (Befund OWNP6-3):

- Ein Eintrag aus `creativeTab()` steht nur im Propwash-Tab, solange der Namensraum seines Items (Registry-Id) keinen
  eigenen Kreativ-Tab hat. Dieselbe Prüfung gilt für den Rückfall der Teil-Items und Presets.
- Eigener Tab heißt, egal wie angemeldet: ein registrierter `propwash:main-<ns>` (über `extensionTab(ns)` oder
  automatisch über Teil-Items und Presets) oder irgendein Tab `<ns>:…` im Register der Kreativ-Tabs
  (`content.CreativeTabNamespaces`). `propwash` und `minecraft` zählen nie, Einträge mit ihren Items bleiben im
  Propwash-Tab.
- Für eigene Einträge ist `extensionTab(ns).add(…)` gedacht; siehe [`api/addon-guide.md`](api/addon-guide.md)
  Abschnitt 9.

## 6. Tests

- JUnit (`common/src/test/java/de/lardtom/propwash/accessory/fix/`):
  `tools/fastcheck.sh test --select-class de.lardtom.propwash.accessory.fix.PayloadLimitTest --select-class de.lardtom.propwash.accessory.fix.MuzzleTest --select-class de.lardtom.propwash.accessory.fix.MotionTrackTest --select-class de.lardtom.propwash.accessory.fix.OverloadDisplayTest --select-class de.lardtom.propwash.accessory.fix.AccessoryFramesTest --select-class de.lardtom.propwash.accessory.fix.CreativeTabNamespacesTest`
  - `PayloadLimitTest`: 3870 g und 9,6 kg kommen voll an, Grenze 20 kg je Zubehör samt Beladung, kaputte Gewichte,
    Stabilität an der Grenze für alle Vorlagen und Halterungen, 3,2 kg gegen 2 kg am 7-Zöller.
  - `MuzzleTest`: Vorderflächen-Mitte bitgleich zur bisherigen `front()`, eigene Mündung mit Neigung, Grenzen, alte
    Konstruktoren, Codec, abgestellte Drohne (26 und 58 mm hohe Box, freestyle_5 und cinewhoop_3) feuert eine halbe
    Box-Höhe über dem Boden, im Flug unverändert.
  - `MotionTrackTest`: Pilotenpakete mit Bildtakt, Netz-Jitter und Phasenversatz gegen Server-Ticks (12 × 40 Ticks,
    alte Schätzung mit vielen Nullen, neue nie 0 und innerhalb 10 %), lange Server-Ticks (Streuung unter 0,2 m/s),
    5–40 Hz, Schübe nach Stockung, gebündelte Pakete, Ruhe, Verfall, Tempo-Grenze.
  - `OverloadDisplayTest`: OSD-Warnung und Texte in beiden Sprachen.
  - `AccessoryFramesTest`: JSON `frames` an freestyle_5 und race_5 → `ACCESSORY_FRAME`, an cinewhoop_3 und long_range_7
    nichts; API additiv (alte Konstruktoren, Sortierung, Teile-Hash, leerer Eintrag, beladene Variante), JSON-Fehler
    mit Feldname, Welt-Regel, Werkbank-Slot und Tooltip; Neustart in eigenen Klassenladern (`FramesWorld`): eine vor der
    Einschränkung gespeicherte Drohne behält das Zubehör in Speicherstand, Netz-Bytes und PW3-Code und meldet
    `accessory_frame`, ebenso `builds().check` und `RuleCheck`; dazu beide Werte der Welt-Regel über `WorldRules`
    (Werkbank-Slot an jedem Rahmen, Werkbank-Bericht, `builds().check` eines PW3-Codes, `createDrone`, Flugverbot).
  - `CreativeTabNamespacesTest`: eigener Tab über `extensionTab`, über `propwash:main-<ns>` im Register oder direkt als
    `<ns>:…`; ohne Tab Rückfall in den Propwash-Tab; `propwash` und `minecraft` zählen nie.
- Selbsttest beider Loader: `'-PpropwashSelftestOnly=addon-api-17: Zubehör-Befunde'` (Klasse
  `interact.client.AccessoryFixSelfTests`, Beispiel `ExampleDispenser`): abgestellt feuern an freestyle_5 und
  cinewhoop_3 (Pfeil `FIRED`, TNT `PLACED` auf der Standfläche, Stein an der Mündung `BLOCKED`), Wartung und Drohnen-Item
  mit „überladen“, schwer beladen fliegen (12,8 kg hebt nicht ab, Shulkerkiste voll Stein → überladen mit OSD-Warnung
  und 20 kg im Flugmodell, 3,2 kg zählt voll und steigt), Tempo im Sichtflug mit 5,6 m/s über 40 Server-Ticks und bei
  Zubehör-Aktionen zwischen den Ticks. Bildschirmfoto (nur Fabric, `-PpropwashScreenshots=true`): `accessory_overloaded`.
  Dazu „nur an bestimmten Rahmen“ (Klasse `interact.client.AccessoryFramesSelfTests`, Test-Zubehör
  `propwash_example:cine_gimbal`): Bauprüfung an jeder Vorlage, Werkbank per Klick und Shift-Klick, Hinweis, Tooltip,
  Regel aus, gespeicherte Drohne laden, Steuern verweigert, Zerlegen, PW3-Code, `createDrone`, Drohnen-Item; mit Regel
  aus darf die Freestyle mit Gimbal fliegen, PW3-Code, `createDrone` und Import einer neuen Drohne bauen sie, mit Regel
  an lehnt der Import ab.
  Dazu „Kreativ-Tab“ (Klasse `interact.client.AccessoryTabSelfTests`): Das Beispiel-Add-on meldet seine 18 Teil-Items
  zusätzlich über `creativeTab()` an; keines steht im Propwash-Tab, je ein Eintrag aus `propwash` und `minecraft` bleibt.
- Beitrittstest mit Pilot auf dem dedizierten Server: `PROPWASH_JOINTEST_ACCFIX=1` an beiden Prozessen
  (`dev.AccFixJoinCheck`, `accessory.client.AccFixJoinProbe`, Erwartung `joined|accfix speed=ok`), siehe
  [`testing.md`](testing.md).
