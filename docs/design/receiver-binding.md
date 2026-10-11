# Binden ohne Phrase: Empfänger merkt sich den Piloten, die Funke prüft die Brille (drone, Propwash 0.5.0)

Stand 09.10.2026. Ersetzt den Entwurf vom 08.10. mit Binding-Phrase. Anforderungen des Besitzers, sinngemäß:

- Die Bindung bleibt in der Drohne bzw. im Item: nach einem Umbau in der Werkbank und erneutem Absetzen muss man nicht
  neu binden.
- Keine Binding-Phrase. Binden heißt: Drohne mit der Funke anklicken, mehr nicht.
- Die Funke prüft, ob man eine Brille trägt. Trägt man eine, muss ihr Videosystem zu dem der Drohne passen, sonst lehnt
  die Funke die Verbindung ab. Sichtflug gibt es nur ohne Brille.

Code in `de.lardtom.propwash.drone.bind` (reines Java: `ReceiverBind`, `BindAccess`, `BindSearch`; Minecraft:
`BindCodecs`, `BindComponents`, `ReceiverBinds`, `BindTooltips`), `drone.bind.client.BindClient`, `drone.PilotRules`
(reines Java), `drone.DronePiloting`, `drone.DroneInteractions` und `video.client.GogglesBlackout`. Verträge in
`docs/FOUNDATION.md`, Abschnitt „Bindung bleibt in der Drohne“.

## 1. Datenmodell

`ReceiverBind(pilot)`: UUID des Piloten, der die Drohne zuletzt gebunden hat. Drei Zustände:

| Zustand | Daten | Bedeutung |
|---|---|---|
| ohne Bindung | keine Komponente, kein NBT-Feld | alte Drohnen und neue Drohnen vor dem ersten Binden; jede Funke mit ihrer Id fliegt sie |
| gebunden | Pilot | die Funke dieses Piloten verbindet sofort |
| zurückgesetzt | `ReceiverBind.CLEARED` (kein Pilot), im NBT ein leeres Objekt | keine Funke verbindet, bis jemand neu bindet |

Speicherorte (die Bindung wandert genau wie `propwash:drone_id`):

- Entity: NBT-Feld `ReceiverBind` (`QuadcopterEntity.RECEIVER_BIND_KEY`), nur auf dem Server, nicht in den
  synchronisierten Entity-Daten.
- Drohnen-Item: Komponente `propwash:receiver_bind` (`BindComponents.RECEIVER_BIND`, persistent und synchronisiert),
  geschrieben in `QuadcopterEntity#toStack`, gelesen in `#loadFromStack`.
- Werkbank: Der Empfänger sitzt auf dem Stack. `DroneAssembly.disassemble` legt die Bindung an den Flugcontroller,
  `createDrone` nimmt sie von dort. Wer den Flugcontroller tauscht, tauscht den Empfänger – die Bindung ist weg (oder es
  kommt die des eingesetzten Flugcontrollers mit).
- Pick-Block im Kreativmodus: Die Kopie behält die Bindung, aber nicht die Id. Die Funke findet sie über die Suche
  (Abschnitt 3).

Teilen-Codes (Bauplan, Lack, Tuning, Kreativ-Werte), `DroneData` der Parts-API und der Codec von Just More Parts tragen
keine Bindung. `SaveFormatGoldenTest` bleibt byte-gleich, weil das Feld optional ist.

## 2. Zugriff (`BindAccess`, reines Java)

| Zugriff | Wann | Funke mit der Id dieser Drohne | Suche findet sie | „RX“ zurücksetzen |
|---|---|---|---|---|
| `UNBOUND` | keine Bindung | verbindet | nein | – |
| `PILOT` | du hast zuletzt gebunden | verbindet | ja | ja |
| `OTHER` | ein anderer Pilot hat zuletzt gebunden | abgelehnt | nein | ja |
| `CLEARED` | zurückgesetzt | abgelehnt | nein | – |

Eine Drohne mit einem Freund teilen heißt jetzt: Er klickt sie mit seiner Funke an. Danach gehört die Bindung ihm, bis
der Besitzer sie wieder anklickt.

## 3. Abläufe

- **Binden** (Funke in der Hand, Rechtsklick auf die Drohne, `DroneInteractions.bindRemote`):
  1. Brille prüfen (Abschnitt 4). Mit falscher Brille auf dem Kopf bindet die Funke nicht und meldet beide Systeme.
  2. Die Funke merkt sich die Drohne (`BoundDrone`), der Server schreibt `ReceiverBind(Pilot)` in die Drohne. War sie
     an einen anderen Piloten gebunden, sagt die Meldung „… die vorherige Bindung ist ersetzt“.
  3. Die Meldung endet mit „· Brille passt: FPV“ oder „· ohne Brille: Sichtflug“.
  Fliegt gerade ein anderer Spieler die Drohne, lehnt das Binden ab („besetzt“, `DroneInteraction`).
- **Funke in die Luft** (`DronePiloting.useRemote`):
  1. Die Drohne der Funke ist da: Zugriff prüfen. `OTHER` und `CLEARED` melden „an einen anderen Piloten gebunden – zum
     Übernehmen mit der Funke anklicken“ bzw. „zurückgesetzt“, kein Start.
  2. Sie fehlt (andere Id, nicht geladen, aufgehoben) oder die Funke ist ungebunden: Suche im Umkreis von 128 Blöcken
     (`ReceiverBinds.SEARCH_RANGE`) nach der nächsten Drohne, die an dich gebunden ist und die niemand anderes fliegt
     (`BindSearch`). Ohne Treffer kommen die Meldungen „nicht gebunden“ bzw. „nicht gefunden“.
  3. Brille prüfen (Abschnitt 4). Mit falscher Brille kein Start; eine gefundene Drohne wird dann auch nicht in die Funke
     eingetragen.
  4. Start in FPV (passende Brille) oder im Sichtflug (keine Brille). Nach einer Suche endet die Meldung mit „· der
     Empfänger hat dich erkannt“ und die Funke zeigt auf die gefundene Drohne.
  5. Schleichen + Funke zeigt den Status wie bisher und sucht nicht.
- **Wartungsmenü** (Schleichen + Rechtsklick): Feld „RX“ links neben dem Inventar mit dem Symbol des Flugcontrollers.
  Rahmenfarbe grün (du), gelb (fremd) oder grau (ohne Bindung, zurückgesetzt), darunter ein Kurzwort, Tooltip mit
  Erklärung. Erster Klick fragt nach (rot, „Reset?“, 3 s), der zweite setzt zurück
  (`DroneServiceMenu.RESET_BIND_BUTTON` = 2000). Den Zustand für den Betrachter liefert der Server per DataSlot.
- **Tooltips**: Drohnen-Item und Flugcontroller zeigen „Empfänger an dich gebunden“, „… an einen anderen Piloten
  gebunden“ oder „Empfänger zurückgesetzt“; ohne Bindung bleibt beim Flugcontroller die Zeile „Empfänger-Bindung
  gespeichert“, solange er eine Id trägt.

## 4. Die Brille: tragen statt binden

Brillen werden nicht mehr gebunden. Was zählt, ist die Brille auf dem Kopf (`ContentIds.wornGogglesSystem`) und das
Videosystem der Drohne. `PilotRules.decide(Brille, Videosystem)` kennt drei Ergebnisse:

| Getragene Brille | Ergebnis | Binden und Start | Im laufenden Flug |
|---|---|---|---|
| keine | `LINE_OF_SIGHT` | erlaubt, Sichtflug | Sichtflug |
| empfängt das Videosystem (analog ↔ analog, digital ↔ digital, Kreativ-Brille ↔ alles) | `FPV` | erlaubt, FPV | FPV |
| empfängt es nicht | `NO_VIDEO` | abgelehnt, Meldung nennt beide Systeme | kein Bild |

`VideoSystem.receives` entscheidet, was passt. Propwash kennt die Systeme Analog und Digital samt je einer Brille und
die Kreativ-Brille; kommt eine weitere Systemfamilie dazu, bekommt sie ihren eigenen Eintrag und ihre eigene Brille,
ohne dass sich an dieser Regel etwas ändert.

- **Im Flug** prüft der Server jede Runde neu (`DronePiloting.recheckView`): Brille ab → Sichtflug, passende Brille
  auf → FPV, falsche Brille auf → kein Bild. Jeder Wechsel bringt eine Meldung („Brille abgesetzt – weiter im
  Sichtflug“, „Brille aufgesetzt – FPV“, „Kein Bild: Deine Brille empfängt …, … sendet …“).
- **Kein Bild**: Die Funkstrecke hängt nicht am Video, die Drohne bleibt also steuerbar – man sieht nur nichts. Der
  Server führt die Sitzung als Sichtflug (Kamera beim Spieler), der Client legt über alles ein deckendes Bild
  (`GogglesBlackout`, gezeichnet in `VideoOverlay`): analoge Brille Rauschen, digitale schwarz, dazu „KEIN SIGNAL“,
  beide Systeme und der Hinweis, die Brille abzusetzen. Das Funk-HUD ist solange ausgeblendet. Inventar und Menüs
  liegen darüber, die Brille lässt sich also absetzen.
- **Klick mit der Brille in der Hand** auf eine Drohne bindet nichts mehr, er prüft nur: „Diese Brille passt zu …“ oder
  „Diese Brille empfängt …, … sendet … – damit gibt es kein Bild“. Der Klick wird verbraucht, die Brille also nicht
  versehentlich aufgesetzt.
- **Zuschauen** (Zuschauen-Taste mit Brille) bleibt unverändert.

## 5. Was die Bindung behält und was sie löscht

| Behält | Löscht |
|---|---|
| Aufheben und Absetzen, Abbauen, Zerbrechen mit Drop | neuer Flugcontroller in der Werkbank |
| Werkbank: Props, Motoren, Rahmen, Akku, Video, Zubehör, Lack, Tuning, Name | „RX“ im Wartungsmenü (zurückgesetzt) |
| Drohnen-Editor (Kreativ-FC), Kamerawinkel, Akku und Props im Wartungsmenü | Import eines Teilen-Codes mit anderem Flugcontroller (gleicher Flugcontroller bleibt mit Bindung in der Werkbank liegen) |
| Item-Rahmen, Truhen, Inventar, Weltspeicher, Server-Neustart | Anklicken durch einen anderen Piloten (bindet an ihn) |
| Pick-Block-Kopie (die Funke findet sie über die Suche) | |

Neue Drohnen (Kreativ-Tab, Werkbank mit neuem Flugcontroller, Teilen-Code) haben keine Bindung, bis man sie einmal
bindet.

## 6. Alte Daten

- Bindungen aus Testständen mit Phrase tragen im NBT bzw. in der Komponente zusätzlich ein Feld `lock`. Der Codec
  überliest es und schreibt es nicht mehr; der Pilot bleibt erhalten.
- Das Welt-Geheimnis der Phrase (`data/propwash/receiver_bind.dat`) löscht der Server beim Start
  (`ReceiverBinds.dropLegacyFile`), die persönliche Phrase (`config/propwash/binding.json`) der Client
  (`BindClient`).
- Brillen aus 0.4.0 tragen noch die Komponente `propwash:bound_drone`. Sie wird nicht mehr gelesen und verschwindet,
  sobald die Brille im Inventar liegt (`FpvGogglesItem#inventoryTick`), an eine Drohne gehalten wird oder ihr Träger
  bindet oder startet (`DronePiloting.dropLegacyBind`).

## 7. Server-Autorität und Missbrauch

- Zugriff, Suche, Binden, Zurücksetzen, FPV oder Sichtflug und die Ablehnung bei falscher Brille entscheidet der
  Server aus dem, was der Spieler wirklich trägt. Der Client schickt dazu nichts.
- Aus der Ferne lässt sich eine gebundene Drohne nicht übernehmen: Eine alte Funke mit der Id wird bei `OTHER`
  abgelehnt, die Suche findet nur eigene Drohnen.
- Physisch anklicken bindet weiter um, wie der Bind-Modus an einem echten Empfänger – aber nicht, solange jemand
  anderes fliegt.
- Das deckende Bild bei falscher Brille zeichnet der Client. Wer es mit einem veränderten Client entfernt, fliegt im
  Sichtflug – das darf ohnehin jeder, der die Brille absetzt. Ein Kamerabild bekommt er nicht, denn FPV vergibt nur der
  Server.
- Aus einer normalen Brille wird nie eine Kreativ-Brille: Der Brillentyp steckt im Item, nicht in einer Einstellung.
  Der Video-Tab zeigt nur die Einstellungen der getragenen Brille; die Kreativ-Werte (eigenes Sichtfeld, Latenz,
  Reichweite, Rauschen, Abschattung) wirken ausschließlich mit getragener Kreativ-Brille (`VideoConfig.horizontalFov`,
  `latencyMs`, `rangeBlocks`, `extraNoise`, `obstructs`, geprüft in `VideoTabRulesTest`), auch wenn jemand sie von Hand in
  `video.json` einträgt. Die freie Funkreichweite prüft der Server zusätzlich selbst (`RadioLink.freeRange`).

## 8. Video-Tab

- Der Umschalter „Brille“ ist entfernt. Er hat nie die wirksame Brille geändert, sondern nur ausgewählt, welche
  Einstellungen der Tab zeigt – das war missverständlich.
- Mit Brille auf dem Kopf zeigt der Tab die Überschrift „Deine Brille: …“ und genau deren Einstellungen: Sichtfeld der
  Kamera, Bildformat, Effektstärke, Kamera-Vibration, Flackern, Videoeffekte, LED-Ausleuchtung; mit der Kreativ-Brille
  zusätzlich die Kreativ-Werte.
- Ohne Brille steht dort nur ein Hinweis. „Standardwerte“ und Speichern ändern dann nichts.
- „Standardwerte“ mit einer normalen Brille setzt nur das Sichtbare zurück, die verborgenen Kreativ-Werte bleiben stehen
  (`VideoTabRules`).
- Wechselt die Brille bei offenem Tab (Mehrspieler), baut er sich neu auf.

## 9. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Phrase, Schloss und Welt-Geheimnis ganz entfernt | Wunsch des Besitzers; ein Mechanismus weniger, den man erklären muss | Phrase als versteckte Option behalten |
| Empfänger merkt sich weiter den letzten Piloten | Werkbank, Aufheben und Absetzen ohne neues Binden war ausdrücklich gewünscht | Bindung nur an der Funke wie in 0.4.0 |
| Teilen einer Drohne über Anklicken statt über eine gemeinsame Phrase | derselbe Handgriff wie das Binden, keine Einstellung | Freundesliste, Team-Bindung |
| Brille wird nicht mehr gebunden | „Funke prüft, ob man eine Brille aufhat“ – ein Schritt weniger, kein Zustand an der Brille | Brille weiter binden und zusätzlich prüfen |
| Falsche Brille lehnt Binden und Start ab, statt in den Sichtflug zu fallen | Sichtflug soll es nur ohne Brille geben; die Meldung nennt beide Systeme | wie bisher Sichtflug mit Hinweis |
| Falsche Brille mitten im Flug: kein Bild, Drohne bleibt steuerbar | die Funkstrecke hängt nicht am Video; ein Abbruch würde die Drohne abstürzen lassen | Verbindung trennen; im Sichtflug weiterfliegen lassen |
| Passende Brille mitten im Flug schaltet auf FPV | „Brille auf“ soll überall dasselbe bedeuten | erst beim nächsten Start |
| „Kein Bild“ als Sichtflug auf dem Server plus deckendes Bild beim Client | Chunk-Anker, Kamera und Zuschauer verhalten sich wie im Sichtflug; kein neuer Sitzungsmodus | dritter Modus in den Entity-Daten; FPV-Kamera mit Störbild (halbdurchsichtiges Rauschen ließe das Bild durch) |
| Klick mit der Brille prüft und meldet | wer es aus 0.4.0 gewohnt ist, bekommt eine Antwort statt einer aufgesetzten Brille | Klick durchreichen |
| Suche 128 Blöcke, nächste eigene Drohne | nah vor fern; begrenzt die Entity-Suche | ganze Funkreichweite (800 Blöcke) |
| Alte Drohnen bleiben ohne Bindung, bis man sie anklickt | keine stille Übernahme beim ersten Flug | beim ersten Flug automatisch binden |
| Binden abgelehnt, solange ein anderer fliegt | sonst ließe sich einem fliegenden Piloten die Bindung wegnehmen | immer binden |
| Zurücksetzen nur im Wartungsmenü, mit Rückfrage | dort wird gewartet, die Drohne steht; zwei Klicks verhindern Versehen | eigener Bestätigungs-Screen |
| Video-Tab ohne Umschalter, ohne Brille nur ein Hinweis | man stellt nur ein, was man trägt | Umschalter behalten und besser beschriften; allgemeine Einstellungen auch ohne Brille zeigen |

## 10. Tests

- JUnit: `PilotRulesTest` (keine Brille, passende, Kreativ, falsche, Wechsel), `BindAccessTest`, `BindSearchTest`,
  `BindCodecsTest` (JSON, NBT, Netz, altes Feld `lock`, Löschen alter Dateien), `VideoTabRulesTest` (Tab-Regeln,
  Kreativ-Werte nur mit Kreativ-Brille, Speichern und Standardwerte), `DroneInteractionTest`,
  `FlightSchoolScenesTest`/`FlightSchoolStoriesTest` mit der Szene „Funke binden, Brille aufsetzen“.
- Selbsttest `bindung:` (`drone.bind.client.BindSelfTests`, dreizehn Schritte): Binden speichert den Piloten;
  Aufheben + Werkbank-Umbau + Absetzen behalten Bindung und Id; Speicherstand samt altem Feld `lock`; fremder Pilot
  lehnt ab und Anklicken übernimmt; neuer Flugcontroller; Suche findet die eigene Drohne und fliegt mit ungebundener
  Brille FPV; Zurücksetzen über zwei echte Klicks; falsche Brille lehnt Binden, Start und Suche ab; Klick mit der Brille
  prüft nur und alte Brillen-Bindungen verschwinden; Brille auf und ab im Flug; falsche Brille im Flug zeigt kein Bild
  (Server und Client); keine Reste der Phrase; Aufräumen.
- Selbsttest `video: Video-Tab` (`video.client.VideoTabSelfTests`, vier Schritte): ohne Brille nur der Hinweis, je
  Brille nur deren Einstellungen und kein Umschalter, verborgene Kreativ-Werte bleiben stehen, Tab folgt der Brille.
- `drone: Spielablauf` klickt die Drohne mit Brille und Funke wirklich an, setzt die Brille auf und fliegt FPV.
- Selbsttest `ui: lange Meldungen über der Schnellleiste brechen um` (`ui.client.OverlayMessageSelfTests`): Die
  Absagen bei falscher Brille (`bind.refused_video`, `session.refused_video`), „kein Bild“ (`session.no_video`) und
  „fremder Pilot“ (`bind.denied_other`) sind auf Deutsch und Englisch 451–716 Punkte breit; bei 427, 480 und 320
  Punkten GUI-Breite passt jede in höchstens drei Zeilen, und die Meldung wird im Spiel wirklich umbrochen gezeichnet
  (`docs/design/ui-scaling.md`). Vorher war sie eine einzige Zeile und an beiden Enden abgeschnitten.
- Bildschirmfotos (deutsch und englisch): `configurator_video_no_goggles`, `configurator_video_analog`,
  `goggles_mismatch_analog`, `goggles_mismatch_digital` (nur englisch), `flight_school_bind_wear`,
  `flight_school_bind_mismatch`, `flight_school_bind_keep`, `drone_service_receiver`, `drone_service_receiver_reset`,
  `tooltip_drone_bound`, `tooltip_goggles_no_bind`.

Netzprotokoll 15: Die Pakete `propwash:bind_phrase` und `propwash:bind_lock` entfallen, die Komponente
`propwash:receiver_bind` überträgt nur noch den Piloten. Die Parts-API ändert sich nicht.
