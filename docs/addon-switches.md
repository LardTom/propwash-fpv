# Add-on-Schalter, Achsen und Namen der Zubehör-Aktionen

Für Spieler und Add-on-Autoren. Ab 0.6.0 (API 1.7) bringen Add-ons eigene Schalter und analoge Kanäle mit, die du wie
die eingebauten Funktionen auf Funke, Gamepad oder Tastatur legst. Zubehör von Add-ons nennt seine Aktionen beim Namen,
und beim ersten Flug mit einem solchen Zubehör sagt Propwash dir einmal, was jede Aktion tut und wo sie liegt.

Technik und Entscheidungen: [`design/0.6.0/addon-api-namespace.md`](design/0.6.0/addon-api-namespace.md) §3.1, §5, E10,
E12, E14 und E35; Vertrag der API in [`addon-api-1.7-contract.md`](addon-api-1.7-contract.md).

## 1. Seite „Add-ons“ im Tab „Modi“

Sobald ein Add-on einen Schalter oder eine Achse anmeldet, hat der Tab „Modi“ eine dritte Seite „Add-ons“ neben
„Funktionen“ und „Befehle“. Ohne solche Add-ons fehlt die Seite. Die drei Reiter stehen auch bei GUI 320 × 240 in einer
Zeile; reicht der Platz nicht, werden sie schmaler und der erste Reiter zeigt nur „Flug“. Knopf, Ansage und Suche nennen
ihn weiter „Funktionen“.

| Zeile | Belegung |
|---|---|
| Schalter und Taster | wie eine eingebaute Funktion: Quelle (Taste der Funke oder Kanal), bei einem Kanal der Bereich in µs (Min, Max), „Schalter umlegen“ zum Erkennen, Invertiert, bei Schaltern das Verhalten Halten oder Umschalten |
| Analoger Kanal | eine freie Achse der Funke (Drehregler, Schieber) mit Invertiert und Totzone in %, oder „Tasten“ |

- **Moduswahl am 3-Stufen-Schalter:** Drei Schalter eines Add-ons auf den drei Bereichen desselben Kanals ergeben eine
  Moduswahl (etwa 1000–1300, 1350–1650 und 1700–2000 µs). Eine eigene Einstellung dafür gibt es nicht.
- **Gamepad:** Legst du einen Schalter auf eine Gamepad-Taste, rastet sie ein (Umschalten), wenn das Add-on das so
  wünscht; sonst ist sie ein Taster.
- **Tastatur:** Die Tasten bringt das Add-on selbst mit (Minecraft-Steuerung, eigene Kategorie des Add-ons). Bei einem
  Schalter schaltet jeder Druck um, bei einem Taster wirkt die Taste, solange sie gedrückt ist. Analoge Kanäle bewegen
  sich mit den Tasten des Add-ons in dessen Tastenrate und bleiben beim Loslassen stehen.
- **Inaktiv auf diesem Server:** Schalter und Kanäle, die der Server kennen muss, kennt ein Server ohne das Add-on nicht.
  Dann zeigt die Seite „Auf diesem Server inaktiv“, die Funktion wirkt dort nicht, Propwash schickt nichts an den Server,
  und die Belegung bleibt gespeichert.
- **Belegung bleibt:** Fehlt ein Add-on (deinstalliert, anderes Modpack), bleibt seine Belegung in
  `config/propwash/input.json` stehen (Schlüssel `<namensraum>:<pfad>` unter `switches`, Kanäle unter `addonAxes`).
  Kommt das Add-on zurück, wirkt sie wieder. Gespeichert werden höchstens 64 Add-on-Schalter und 64 Add-on-Kanäle je
  Gerät; angemeldete haben Vorrang.
- „Zurücksetzen“ auf der Seite „Add-ons“ löscht die Belegungen der angemeldeten Add-on-Funktionen; das Zurücksetzen
  der Seite „Funktionen“ lässt sie stehen. Der Empfänger-Assistent fragt Add-on-Funktionen nicht ab.

## 2. Zubehör-Aktionen

- Zubehör löst über die Zubehör-Aktionen 1 bis 4 aus: an der Funke über die Belegung im Tab „Modi“, mit der Tastatur
  über die Tasten „Zubehör-Aktion 1“ bis „4“ (Standard Ziffernblock 9, 0, Enter und +). Der Rechtsklick beendet im Flug
  weiter das FPV, egal welches Zubehör montiert ist.
- Nennt ein Zubehör seine Aktionen, zeigt der Tab „Modi“ zum Beispiel „Zubehör-Aktion 1 – Blinken“: im Flug die Namen
  des Zubehörs an deiner Drohne, sonst die aller angemeldeten Zubehöre (mehrere mit „/“ getrennt). In der Liste steht
  der Name, wenn er in die Spalte passt; im Bereich unten immer.
- **Hinweis beim ersten Flug:** Fliegst du zum ersten Mal eine Drohne mit einem solchen Zubehör, schreibt Propwash in den
  Chat, wie jede Aktion heißt und wo sie gerade liegt (Schalter der Funke, Taste oder „nicht belegt“ mit Verweis auf den
  Tab „Modi“). Einmal je Zubehörtyp und Spieler; gemerkt in `config/propwash/accessory_hints.json`.
- **Flugmodi einer Flughilfe:** Deutet die Flughilfe eines Add-ons Acro, Angle und Horizon um (etwa eine Kamera-Drohne
  mit S, N und C), nennt die Meldung oben rechts beim Wechsel per Taste deren Modi, zum Beispiel „Mode: C (key – radio
  switch: S)“, ebenso der Hinweis „… per Taste gewählt“ im Tab „Modi“. Ohne solche Flughilfe bleibt es bei Acro, Angle und
  Horizon.

## 3. Für Add-on-Autoren

```java
AddonSwitch ghost = PropwashApi.switches().register(SwitchSpec.builder(Identifier.fromNamespaceAndPath("mymod", "ghost"))
        .toggle(true).latchesOnGamepad(true).serverSync(true).build());
AddonAxis tilt = PropwashApi.switches().registerAxis(AxisSpec.builder(Identifier.fromNamespaceAndPath("mymod", "gimbal_tilt"))
        .center(0.0).keyRateDps(60).serverSync(true).build());
PropwashClientApi.bindSwitchKey(ghost.id(), ghostKey);
PropwashClientApi.bindAxisKeys(tilt.id(), tiltUpKey, tiltDownKey);
```

| Abfrage | Am Server | Beim Piloten | Sonst |
|---|---|---|---|
| `AddonSwitch#active(drone)` | zuletzt gemeldeter Zustand (nur `serverSync`) | eigener Zustand der Drohne, die er fliegt | `false` |
| `AddonAxis#value(drone)` | zuletzt gemeldeter Wert (nur `serverSync`) | eigener Wert | `center` |
| `ClientInput#switchActive(id)`, `#axis(id)` | – | im Flug der eigene Zustand bzw. Wert | `false` bzw. `center` (0 für unbekannte Ids) |
| `FlightState#addonSwitch(id)`, `#addonAxis(id)` | im Server-Flug der zuletzt gemeldete Zustand bzw. Wert | in Flughilfe und Kräften der eigene Zustand bzw. Wert | `false` bzw. `center` (0 für unbekannte Ids) |
| `GimbalContext#axis(id)` | – | eigener Wert | `center` (0 für unbekannte Ids) |

- Ein `serverSync`-Schalter, den der Server nicht kennt, wirkt beim Piloten nicht (`false`), damit Client und Server
  dasselbe sehen. Ohne `serverSync` wirkt ein Schalter nur lokal und geht nie an den Server.
- `PropwashEvents.ADDON_SWITCH_CHANGED` kommt am Server bei jedem Wechsel eines `serverSync`-Schalters, auch beim
  vollständigen Abgleich am Anfang einer Pilotensitzung, wenn sich dabei etwas ändert. Beim Pilotenwechsel vergisst der
  Server alle Zustände der Drohne.
- Tastenrate: `keyRateDps` in Grad je Sekunde, ein Vollausschlag (0 bis ±1) entspricht 90°. Mit 60 °/s dauert der Weg
  von der Mitte zum Anschlag 1,5 s.
- Totzone: symmetrisch um 0, der Rest wird auf den vollen Bereich gestreckt (Standard 5 %, höchstens 50 %).
- Grenzen: 64 Schalter und 16 Achsen über alle Add-ons; Ids `[a-z0-9_.-]+:[a-z0-9_./-]+`, höchstens 64 Zeichen, nicht
  im Namensraum `propwash`.
- Namen der Zubehör-Aktionen: `AccessoryBehavior#actionNameKey(int number)` liefert für die Nummern 1 bis 4 einen
  Übersetzungsschlüssel des Add-ons oder leer.
- Namen der Flugmodi: `FlightAssist#modeNameKey(StabilizationMode mode)` liefert für `ACRO`, `ANGLE` und `HORIZON` einen
  Übersetzungsschlüssel des Add-ons oder leer (Standard). Propwash fragt beim Piloten im Client-Thread, wenn die Meldung
  beim Moduswechsel entsteht oder der Tab „Modi“ den Hinweis zeichnet, nie im Flugmodell; der Schlüssel darf vom Zustand
  abhängen (ATTI ohne GPS). Gefragt wird die Flughilfe der geflogenen Drohne, auch während GPS Rescue. Leer, `null`,
  blank oder eine Ausnahme (Warnung im Log, einmal je Klasse der Flughilfe) ergeben für diesen Modus den Namen von
  Propwash. Umgesetzt in `input.addon.AddonModeNames` (`name`, `changed`), aufgerufen von `PilotSession#selectMode` und
  `PilotSession#flightModeName`.
- Angehängte Werte: Hängt ein Add-on per Mixin einen eigenen Wert an `SwitchFunction` an (wie Noclip 0.1.x), ist das
  keine API, Propwash stürzt dafür aber nicht ab. Jede Fallunterscheidung über `SwitchFunction`, `OsdElement` und
  `ModesTab$Row` hat einen Standardfall. Ein angehängter Wert ist eine Flugfunktion (kein Befehl, `builtIn()` falsch),
  nicht umschaltbar, solange das Add-on `supportsToggle()` nicht selbst beantwortet, und hat keine eigene Taste
  (`key.propwash.<name in Kleinbuchstaben>` ohne Belegung). Die Funke im Empfänger-Assistenten zeigt ihn auf einem
  gewöhnlichen Schalter (Schalter 3), am Gamepad als Taster. Der Assistent listet angehängte Werte nach den eingebauten
  Funktionen und nur, solange alle Zeilen über den Arm-Hinweis passen; sonst fallen dort zuerst Turtle und dann die
  angehängten Werte von hinten weg (bei GUI 427 × 240 etwa bleibt nur die eingebaute Liste). Belegbar bleiben sie im
  Tab „Modi“, wenn das Add-on dort eine eigene Zeile anhängt. Neue Add-ons nehmen `PropwashApi.switches()`.

### Netz

| Paket | Richtung | Inhalt | Wann |
|---|---|---|---|
| `propwash:addon_features` | Server → Client | Ids der `serverSync`-Schalter und -Achsen und der Kollisionsregeln, die der Server kennt | beim Betreten |
| `propwash:addon_switch` | Client → Server | Entity-Id, Reset-Epoche, bis 16 × (Id, an/aus) | bei Änderung, vollständig beim Start der Sitzung, nach einem Reset und nach neuen `addon_features` |
| `propwash:addon_axis` | Client → Server | Entity-Id, Reset-Epoche, bis 8 × (Id, Wert als `short`) | bei Änderung, höchstens 10 je Sekunde |

Der Server nimmt nur Pakete des Piloten mit der aktuellen Reset-Epoche, höchstens 20 je Sekunde (kurze Spitzen bis 8),
und nur bekannte Ids mit `serverSync`. Beide C2S-Pakete gehen über dieselbe Verbindung wie der Flugzustand und kommen
daher vor dem nächsten `flight_state` an.

### Flugmodell und Gimbal

`FlightState#addonSwitch`/`#addonAxis` und `GimbalContext#axis` lesen den Zustand über
`input.addon.AddonInputState#active(Entity, String)` und `#axis(Entity, String)`, dieselbe Abfrage wie
`AddonSwitch#active`, `AddonAxis#value` und `CollisionQuery#switchActive`. Das Flugmodell bleibt reines Java:
`AddonInputState#bind(Entity, DroneSim)` hängt die Abfrage der Drohne als `DroneSim#setAddonInput` an, beim Piloten
einmal je Sitzung (`AddonFlightClient`), am Server beim Start jedes Server-Flugs (`ServerFlight#begin`). Ohne Anbindung
(etwa in der Freecam) gelten `false` bzw. 0. Achsen sind auf −1…1 gekappt, NaN wird 0. Da der Server beim
Pilotenwechsel alle Zustände der Drohne vergisst, sieht eine Flughilfe im Heimflug oder autonomen Flug `false` bzw.
`center`. Ob der Server eine Kollisionsregel kennt, sagt `AddonServerFeatures#knowsCollisionRule`.

## 4. Beispiel und Tests

- Beispiel-Add-on: `dev/example/ExampleSwitches` (Schalter „Geist“ `propwash_example:ghost`, Achse „Gimbal-Neigung“
  `propwash_example:gimbal_tilt`, beide `serverSync`; Zubehör „Signallicht“ mit den Aktionen „Blinken“ und
  „Farbe wechseln“), Tasten in `dev/example/client/ExampleSwitchesClient` (ohne Standardbelegung). Das Item des
  Signallichts braucht wie jedes per `partItems().registerItem` angemeldete Teil ein Item-Modell
  (`assets/propwash_example/items/signal_light.json` mit dem Teil-Renderer `propwash:part`), sonst liegt es als Würfel
  ohne Modell am Boden und in der Hand.
- JUnit `input.AddonSwitchesTest`, Selbsttest `addon-api-17: A1`, Beitrittstest `PROPWASH_JOINTEST_ADDON17`
  ([`testing.md`](testing.md) §1 „Add-on-Schalter“).
- Gamepad-Rasten: `SimulatedRadio#asGamepad()` macht die Testfunke zum Gamepad (`DeviceInfo.gamepad`, Gamepad-Achsen
  und -Knöpfe mit denselben Nummern wie die Joystick-Werte, mindestens 6 Achsen und 15 Knöpfe), sodass
  `RawInput#usesGamepadLayout` mit einem Gamepad-Profil gilt. JUnit
  `AddonSwitchesTest#simulatedGamepadUsesTheGamepadLayoutAndLatchesAddonButtons`; Selbsttest „addon-api-17: A1 –
  Gamepad“: auf der Seite „Add-ons“ ergeben „Quelle“ (rückwärts auf den letzten Knopf) und „Schalter umlegen“ (Knopf
  A) für „Geist“ das Verhalten „Umschalten“, die Anzeige rastet bei jedem Druck um, gespeichert wird der Knopf mit
  `toggle`; dieselbe Funke ohne `asGamepad()` mit demselben Gamepad-Profil ergibt „Halten“ und wirkt nur gehalten.
- Testfunke über die API (für Selbsttests von Add-ons, die nur `api.**` nutzen dürfen): `PropwashClientApi.simulateRadio(boolean
  gamepad)`, `setSimulatedAxis`, `setSimulatedButton`, `bindSimulatedSwitch` (Knopf bzw. Kanal und Bereich) und
  `stopSimulatedRadio` ([`api/addon-guide.md`](api/addon-guide.md) §21.2). Intern `input.addon.client.AddonTestRadio`: Gamepad
  `propwash-api-test-gamepad` (6 Achsen, 15 Knöpfe, `SimulatedRadio#asGamepad()`, Profil `ProfileDefaults.gamepad`) bzw.
  Funke `propwash-api-test-radio` (12 Kanäle, 16 Knöpfe, Profil `ProfileDefaults.radio`), beide mit `setupDone`; das Profil
  steht während des Tests unter seiner GUID in `InputConfig`, damit `AddonRadioStage` es findet, und `stopSimulatedRadio`
  entfernt es wieder. Belegt wird wie auf der Seite „Add-ons“: Knopf über `AddonBindings.apply` mit
  `RawInput#usesGamepadLayout` (rastet am Gamepad nur mit `latchesOnGamepad`), Kanal nur auf Schalterkanälen
  (`DeviceProfile#auxAxes`, nicht auf den Sticks) mit einem Bereich von 900 bis 2100 µs. `simulateRadio` wirkt nur in der
  Entwicklungsumgebung (`IPlatformHelper#isDevelopmentEnvironment`), damit ein Add-on im Spiel nie die Steuerung des
  Spielers ersetzt. JUnit `AddonSwitchesTest#apiTestRadioBindsLikeTheAddonsPageAndLatchesOnlyOnTheGamepad`; Selbsttest
  „addon-api-17: A1 – Testfunke über die API“ (`AddonSwitchApiRadioSelfTests`, nur über `PropwashClientApi`): Gamepad,
  „Geist“ auf Knopf 0, drei Drücke mit Prellen ergeben an/aus/an mit je einem `ADDON_SWITCH_CHANGED` am Server; danach
  Funke mit „Geist“ auf Kanal 8 (1700–2000 µs: an bei 2000, aus bei 1000) und auf Knopf 0 (nur gehalten); ungültige
  Knöpfe, Achsen, Kanäle, Bereiche und Ids lehnt die API ab, nach `stopSimulatedRadio` sind Testfunke und Profil weg.
- Zubehör-Aktionen an der Testfunke: `PropwashClientApi.bindSimulatedAction(nummer, knopf)` bzw. `(nummer, kanal,
  vonMikros, bisMikros)` belegt Zubehör-Aktion 1–4 im Profil der Testfunke wie die Auswahl „Quelle“ im Tab „Modi“
  (`SwitchSources.apply` mit `SwitchFunction.accessory(nummer)`): ein Knopf ist ein Taster und rastet auch am Gamepad nie
  ein, ein Kanal braucht wie bei `bindSimulatedSwitch` einen Schalterkanal und einen Bereich von 900 bis 2100 µs. Danach
  läuft eine Aktion den Weg der echten Funke: `RadioFrame` → `AccessoryActionInput` → `AccessoryActionC2S` → `onAction`
  und `actionHeld` am Server; `triggerAction` umgeht dagegen den Client ganz. Die Testfunke lässt sich auch mitten in einer
  FPV-Sitzung starten und beenden, die Sitzung läuft weiter (Knüppel, Scharf-Schalter und Flugmodus kommen solange von
  der Testfunke). JUnit `AddonSwitchesTest#apiTestRadioBindsAccessoryActionsLikeTheModesTab`; Selbsttest „addon-api-17:
  A1 – Testfunke über die API: Zubehör-Aktion 1 mitten im Flug“ (`AddonSwitchApiRadioSelfTests`, nur über
  `PropwashClientApi`): Flug mit Signallicht, dann Funke, Aktion 1 auf Knopf 1 ergibt je Druck von 4 Ticks am Server
  genau ein Drücken und Loslassen, auf Kanal 9 (1700–2000 µs) 40 Ticks an genau ein Drücken und 40 Ticks später das
  Loslassen; der alte Knopf wirkt nach dem Umbelegen nicht mehr, ungültige Nummern, Knöpfe, Kanäle und Bereiche lehnt die
  API ab.
- Namen der Flugmodi: JUnit `input.addon.AddonModeNamesTest` (Flughilfe wie eine Kamera-Drohne: „Mode: C (key – radio
  switch: S)“, „Modus: N (Taste – Funkenschalter: S)“, ATTI ohne GPS, Hinweis im Tab „Modi“; ohne Namen, leer, `null`,
  blank, Ausnahme und nur teilweise benannt je Modus die Namen von Propwash); Selbsttest „addon-api-17: A1 – Meldung beim
  Moduswechsel nennt die Flugmodi der Flughilfe …“ (`drone.client.AddonModeNameSelfTests`): Probe-Flughilfe
  `propwash_example:mode_name_probe` (nur während des Schritts, Namen N, C und S), Testfunke mit Modusschalter in der
  Mitte (Horizon); Acro-Taste ergibt „Mode: S (key – radio switch: C)“ und im Tab „Modi“ „S“, Horizon-Taste „Mode: C“.
- Flugmodell und Gimbal: JUnit `sim.AddonFlightHooksTest#addonSwitchesAndAxesReachAssistAndForces` (Flughilfe und
  Kräfte, Kappung, ohne Anbindung neutral) und `rescue.AutonomousAssistTest#serverAssistSeesAddonSwitchesAndAxes`
  (Flughilfe am Server); Selbsttest „addon-api-17: A1 – Flugmodell und Gimbal sehen Schalter und Achse“: eine
  Kraft-Probe (`propwash_example:addon_input_probe`) und `GimbalContext#axis` der Pilotendrohne folgen der Funke
  (Geist an und Achse 0,5, danach aus und 0), fremde und reservierte Ids bleiben ohne Wert.
