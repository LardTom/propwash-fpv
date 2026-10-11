# 3D-Modus – Propwash 0.5.0

Wunsch des Besitzers (03.10.2026): 3D als zusätzlicher Flugmodus. Er ist nicht von Anfang an da, sondern wird einmal in
den Einstellungen freigeschaltet und danach wie die anderen Modi auf einen Funkenschalter oder eine Taste gelegt, damit
Piloten ihn im Notfall abschalten können wie Angle. Aus der Community kam die Beschreibung: Gasweg −100 … 0 … +100
(1000 µs = −100, 1500 µs = 0, 2000 µs = +100), kopfüber wirken Nick und Gier vertauscht, umkehrbare ESCs haben 3D und
Turtle Mode ermöglicht, bidirektionales DShot macht den Nulldurchgang sicherer; Nutzen: Korrekturen in der Luft (ein zu
tiefer Split-S oder Power Loop, Stechen vor einer Stange) und Start kopfüber nach einem Absturz.

## 1. Vorbild Betaflight

| Betaflight | Bedeutung | Propwash |
|---|---|---|
| `feature 3D` | Motoren laufen umkehrbar (3D-fähige ESCs, DShot-3D-Befehle) | persönliche Einstellung „3D-Modus freischalten“ (Tab „Modi“) |
| Modus „3D DISABLE / SWITCH“ (`BOX3D`) | bei `3d_switched_mode = OFF`: Schalter an = 3D aus, Gas wieder 0–100 % vorwärts | Modusfunktion `MODE_3D`: Schalter oder Taste an = 3D an (Abschnitt 4) |
| `3d_deadband_throttle` (Standard 50 µs) | Gas-Totzone um `mid_rc` (1500 µs): dort Nullschub | `FlightControllerConfig.deadband3d` = 0,05 Knüppelweg = ±50 µs |
| `3d_deadband_low/high`, `3d_neutral` (1406/1514/1460) | Ausgangs-Totzone für PWM-ESCs; bei DShot fest (48–1047 rückwärts, 1049–2047 vorwärts) | entfällt; der Motor-Leerlauf (`motorIdlePercent`, wie `dshot_idle_value`) gilt in beide Richtungen |
| Totzone hält die letzte Richtung | in der Mitte drehen die Motoren im Leerlauf weiter in die zuletzt gefahrene Richtung; unscharf gilt vorwärts | `FlightController#magnitude3d` |
| `motorOutputMixSign = −1` rückwärts | Roll-, Nick- und Gier-Korrektur kehren sich um, weil Schub und Reaktionsmoment die Richtung wechseln | Mischer bekommt die negierten PID-Ausgänge |
| `pidResetIterm()` 250 ms nach einer Umkehr | I-Anteil nach dem Richtungswechsel aus | `FlightController.REVERSAL_ITERM_HOLD_S` |
| Scharfschalten in 3D nur mit Gas in der Totzone | „throttle low“ heißt in 3D „Gas in der Mitte“ | `Arming#conditions3d`, Blocker `THROTTLE_NOT_CENTERED`, OSD „GAS NICHT MITTE“ |
| `small_angle` 25°; 3D-Piloten stellen oft 180 ein | Kippwinkel beim Scharfschalten | 3D erlaubt aufrecht **und** kopfüber (Neigung ≤ 25° oder ≥ 155°), seitlich weiter gesperrt |
| Airmode | gilt in beide Richtungen, auch in der Totzone | unverändert, auf den Gas-Betrag |
| Failsafe, GPS-Rettung, ALTHOLD/POSHOLD | fliegen mit normalem Gas | 3D für diese Zeit aus (Flugmodell und Sitzung) |

## 2. Physik: umkehrbare Motoren (`sim`, nur mit 3D)

- **Gas:** Knüppel 0…1 (`PilotInput.throttle`, 0,5 = Mitte) → Richtung und Betrag mit Totzone ±0,05; auf den Betrag wirkt
  die Gaskurve (`thr_mid`, `thr_expo`, Gaslimit) wie vorwärts, auf den vorzeichenbehafteten Wert die Anti-Gravity.
- **Motor:** Tastverhältnis mit Vorzeichen; das Gleichstrommodell (Gegen-EMK `kt·ω`, Phasenstrom, Strombegrenzung des ESC,
  Eingangsstromgrenze) wird für die Fahrtrichtung gespiegelt, so dass Bremsen, Nulldurchgang und Rückwärtslauf aus
  denselben Gleichungen folgen. Luftmoment `q = kQ·fq·ω·|ω|` wirkt immer gegen die Drehung; Reaktionsmoment und
  Drehimpuls am Rahmen wechseln mit `ω` das Vorzeichen.
- **Nulldurchgang:** sensorlose ESCs sehen bei sehr kleiner Drehzahl keine Gegen-EMK. Unter 4 % der Höchstdrehzahl wirkt
  das Antriebsmoment deshalb nur 40 … 100 % (linear) – das ergibt die kurze Umkehrpause. 5″ Freestyle: Nulldurchgang nach
  etwa 35 ms, 30 % Drehzahl rückwärts nach etwa 50 ms; Whoop 60/80 ms. Desyncs werden nicht simuliert.
- **Prop-Daten:** normale Props sind gewölbt und verwunden; rückwärts angeströmt liefern sie viel weniger Schub bei
  kaum weniger Leistung. Das Modell nimmt den Umkehrschub `r` aus den Prop-Daten (`PropDefinition.reverseThrust()`, JSON
  `reverse_thrust`, 0,2–1,0); ohne Angabe gilt `r = 0,55`. Daraus: Schubbeiwert rückwärts `r·kT`, Drehmomentbeiwert
  `(0,6 + 0,4·r)·kQ` (normale Props 0,82 – der Wirkungsgrad sinkt), Nullschub-Geschwindigkeit `√r·v₀`. Ein symmetrischer
  3D-Prop (`r` ≈ 1) schiebt in beide Richtungen gleich. Gemessen am Prüfstand (`Mode3dTest`): normale Props 0,50 (Whoop)
  bis 0,59 (7″) des Vorwärtsschubs bei Vollgas.
- **Duct:** der Einlauf eines Ducts formt nur die Vorwärtsströmung; rückwärts gibt es keinen Duct-Gewinn. Whoops mit
  normalen Props schweben kopfüber deshalb nur knapp (etwa 86 % Umkehrgas).
- **Strömung:** Einströmverhältnis, Wirbelring, Nachlauf und Bodeneffekt gelten in Schubrichtung des jeweiligen Rotors
  (rückwärts von der Unterseite her; Bodeneffekt kopfüber mit Umkehrschub über dem Boden). H-Kraft und Biegeverlust
  rechnen mit dem Betrag.
- **Bitgleichheit:** Solange 3D nie aktiv war, läuft jede Rechnung über den bisherigen Weg (`DroneSim#reversibleActive`
  ist nur an, wenn 3D gewählt ist oder noch ein Rotor rückwärts dreht oder ein Tastverhältnis negativ ist).
  `tools/calibration.sh`: `calibration.txt`, `motion.txt`, `fall.txt` byte-gleich; alle Golden-Tests unverändert. Wo der
  bisherige Code schon mit Beträgen rechnen kann (Wärme, Antriebsverluste, Prop-Strikes, Vibration, Bodenaufrichten),
  ist `Math.abs` für nichtnegative Werte nachweislich gleich.

## 3. Was der Pilot merkt

- **Kopfüber schweben:** Gas etwas unter der Mitte (5″ Freestyle ≈ 30 % rückwärts, Knüppel ≈ 1320 µs).
- **Vertauschte Achsen:** Nick und Gier wirken kopfüber aus Sicht des Piloten umgekehrt – das ist Physik, nicht Absicht:
  die Kreisel-Regelung arbeitet im Körpersystem, die Richtungen am Knüppel bleiben gleich (`Mode3dTest`).
- **Umkehrschub nach unten:** voll rückwärts beschleunigt die Drohne schneller nach unten als im freien Fall (rettet zu
  tiefe Split-S und Power Loops, Stechen vor einer Stange).
- **Start kopfüber:** liegt die Drohne kopfüber, lässt sie sich mit Gas in der Mitte scharf schalten; Gas runter hebt sie
  kopfüber ab, danach eine Rolle. Das Aufrichten per Taste U bleibt für alle anderen Fälle.
- **Abschalten:** Schalter oder Taste aus → sofort normales Gas (Mitte = halbes Gas, die Drohne steigt) – wie Angle zum
  Retten. Motoren, die rückwärts drehen, bremsen und laufen wieder vorwärts an.
- **Acro, Horizon, Angle:** 3D ist mit allen drei Modi kombinierbar wie bei Betaflight. Acro und Horizon fliegen kopfüber;
  Angle richtet die Drohne immer aufrecht aus (kopfüber schweben geht dort nicht, Umkehrschub drückt aufrecht nach unten).

## 4. Eingabe und Bedienung

- **Freischalten** (`InputConfig.mode3dUnlocked`, persönlich, Standard aus): Tab „Modi“, Zeile „3D-Modus“, Schalter
  „Freigabe“ (englisch „Unlock“). Vorher sind Quelle, Zuweisen und Löschen der Zeile gesperrt und der Empfänger-Assistent zeigt die
  Schalter-Zeile nicht. Taste oder Schalter bei gesperrtem 3D zeigen „3D-Modus gesperrt – freischalten im Konfigurator,
  Tab „Modi““.
- **Belegen:** Modusfunktion `SwitchFunction.MODE_3D` (Funkenschalter-Bereich oder Knopf, Halten oder Umschalten; Funke und
  Gamepad standardmäßig unbelegt) und Taste „3D-Modus an/aus“ (Ziffernblock 6, `KeyDefaults.MODE_3D`).
- **Es zählt die letzte Eingabe** (`input.Mode3dSelector`, reines Java, gleiche Regeln wie `FlightModeSelector`): ein
  bewegter Schalter setzt seinen Zustand, die Taste schaltet sofort um, auch gegen den Schalter, bis dieser wieder bewegt
  wird; fällt die Belegung weg, geht ein vom Schalter gesetztes 3D aus. Die Wahl lebt über Sitzungen.
  `active = gewählt ∧ freigeschaltet ∧ Welt-Regel ∧ keine GPS-Rettung`.
- **Gas in 3D:** Funke mit vollem Gasweg nutzt die im Assistenten kalibrierte Gasmitte (`ChannelMapping.center`,
  stückweise linear; normales Gas bleibt linear zwischen den Anschlägen). Der Assistent übernimmt die gefangene Gasmitte,
  wenn sie höchstens 15 % des Wegs vom Mittelpunkt der Anschläge entfernt liegt, und erklärt sie bei freigeschaltetem 3D.
  Gamepad mit selbstzentrierendem Knüppel („obere Hälfte“) nutzt in 3D den ganzen Weg: Knüppel in Ruhe = Nullschub.
  Tastatur und Gamepad „schrittweise“ springen beim Einschalten unscharf in die Mitte und rasten dort ein, bis die Taste
  losgelassen wird (`input.ThrottleCentering`); beim Abschalten unscharf zurück nach unten. Der Wert reist als
  `RadioFrame.throttle3d` mit; bei Funkverlust steht das 3D-Gas in der Mitte.
- **Meldungen:** „3D-Modus an – Gasmitte ist Nullschub“, „3D-Modus aus – normales Gas“, bei Taste gegen Schalter mit
  Schalterstellung; Modi-Tab mit Lampe und Hinweis, wenn die Taste den Schalter überstimmt.
- **Anzeige:** OSD-Flugmodus „ACRO 3D“, „ANGL 3D“, „HOR 3D“; Element „Gas“ −100 … +100 (`THR -30`); Funk-HUD im Sichtflug
  zeigt Modus und Gas ebenso; Knüppel-Overlay zeigt das 3D-Gas.

## 5. Welt-Regel

`propwash:allow_3d_mode` (Schalter, Standard an, Gruppe „Spiel“, keine Physik-Regel): aus, und niemand fliegt 3D, auch
wenn er es freigeschaltet hat (Meldung „3D-Modus ist in dieser Welt nicht erlaubt (Welt-Regel)“). Gedacht für
Rennserver, auf denen Umkehrschub als Bremse unfair wäre. Tab, Befehl, Synchronisation und Export für die Website folgen
aus dem Katalog. Die Regel wirkt nur im Client (`PilotSession#mode3dAllowed` liest `ClientWorldRules`): Der Zustand der
Drohne (`DroneStateC2S`, Motorwerte als Beträge) enthält den 3D-Modus nicht, der Server kann ihn weder sehen noch für
Bestenlisten werten. Ein veränderter Client umgeht sie wie jede Physik-Regel; der Regeltext sagt deshalb „Propwash lässt
ihn niemanden einschalten … wie das Flugmodell setzt das Spiel jedes Piloten die Regel um“ statt „bleibt für alle aus“.
Eine Prüfung am Server bräuchte ein Feld im Zustandspaket (neues Netzprotokoll) und ist für später notiert.

## 6. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| 3D ist ein eigener Modus, der **an** heißt, wenn Schalter/Taste an sind | So hat es der Besitzer beschrieben („freischalten, dann wie die anderen Modi belegen“), und das Abschalten zum Retten ist dieselbe Bewegung wie bei Angle | Betaflights Umkehrung (Feature an = 3D, Schalter an = 3D aus) – verwirrt neben Angle/Horizon |
| Freigeschaltet ohne Belegung bleibt 3D aus, bis Taste oder Schalter es einschalten | Freischalten allein darf nie den Gasweg ändern (Mitte wäre plötzlich Nullschub) | 3D sofort an nach dem Freischalten |
| Totzone fest ±50 µs, ohne Regler | Betaflight-Standard, passt für Funken; Gamepads haben ihre eigene Totzone | eigener Regler im Modi-Tab |
| Gaskurve auf den Betrag je Richtung | gleiches Gefühl vorwärts und rückwärts | Kurve auf den ganzen Knüppelweg |
| Scharf schalten kopfüber in 3D erlaubt, seitlich nicht | „Start kopfüber“ ist der Sinn; seitlich würde die Drohne beim Hochlaufen umkippen | `small_angle 180` (jede Lage) |
| Umkehrschub als optionale Prop-Eigenschaft, Standard 0,55 | normale Props liefern rückwärts nur etwa die Hälfte; Addons können echte 3D-Props mit `reverse_thrust` ≈ 1 anbieten | fester Wert für alle Props; eigenes 3D-Prop-Teil im Kern |
| **Kein eingebauter 3D-Prop in 0.5.0** | ein neues Teil verändert die Golden-Dateien und die Kalibrierung (neue Zeilen), braucht Modelle, Rezept, Beute und Fortschritte und kollidiert mit anderen 0.5.0-Zweigen; das Feld steht bereit, *Just More Parts* oder ein späteres Release kann einen symmetrischen Prop liefern (Vorschlag: 5″ 3-Blatt, Steigung 3,0, `ct` ≈ 0,10, `cp` ≈ 0,055, `reverse_thrust` 1,0) | Teil jetzt mitliefern |
| Duct rückwärts ohne Gewinn | der Einlauf wirkt nur in Strömungsrichtung | Duct-Faktor in beide Richtungen |
| Kurze Anlaufpause beim Nulldurchgang statt Desync-Modell | billig, macht die Umkehr spürbar, Desyncs sind mit bidirektionalem DShot selten | Desync-Simulation |
| Rettung, Failsafe, ALTHOLD/POSHOLD ohne 3D | deren Regler rechnen mit normalem Gas; die Rettung soll die Drohne aufrecht heimbringen | 3D mitführen |
| Welt-Regel `allow_3d_mode` (Standard an) statt Server-Konfiguration | Welt-Regeln sind der Ort für Regeln, die alle betreffen, und erscheinen ohne Zusatzarbeit im Tab und auf der Website | Eintrag in `server.json` |
| 3D zählt nicht als veränderte Physik | es ist eine Fähigkeit der Drohne wie Rates oder Airmode; wer Rennen fair halten will, verbietet es per Regel | Bestenlisten-Sperre bei 3D |
| Netzprotokoll unverändert | Drehrichtung wird nicht übertragen; andere Spieler sehen die Props wie vorwärts drehen | eigenes Feld für die Drehrichtung |
| Parts-API bleibt 1.5.0 | 0.5.0 ist noch nicht veröffentlicht; die Ergänzungen (`reverseThrust`, `FlightSchoolScenes.MODE_3D`) sind additiv und gehören zur selben Nebenversion wie `TRICKS` und `GRIPPER` | 1.6.0 |

## 7. Technik

| Datei | Inhalt |
|---|---|
| `sim/FlightController` | `magnitude3d`, Richtung, I-Pause, Anti-Gravity auf das Vorzeichen, Mischer und Tastverhältnis mit Vorzeichen, `signedThrottleOut` |
| `sim/PowerSystem` | `reversible`, `classifyReversible`, `stepRotorsReversible`, `anyReversed` |
| `sim/DroneSim` | 3D aus bei Failsafe/Hold, `reversibleRotor` (Schub, H-Kraft, Wirbelring), Bodeneffekt je Rotor, Nachlauf mit Schubrichtung, Hitze-Drossel symmetrisch, `Arming#conditions3d`, Telemetrie `mode3d` |
| `sim/AirframeSpec`, `ParamDerivation`, `ModelConstants` | `reverseThrust`, `reverseTorque`, `reversePitch`; `REVERSE_THRUST_STANDARD`, `STARTUP_*` |
| `api/part/PropDefinition`, `parts/PartJson` | `reverseThrust()` (OptionalDouble), Builder `reverseThrust(…)`, JSON `reverse_thrust`; Tooltip-Zeile „Umkehrschub (3D-Modus)“, nur wenn gesetzt |
| `input/Mode3dSelector`, `ThrottleCentering`, `StickMapper#throttle3d`, `RadioFrame.throttle3d`, `CalibrationRecorder` | Auswahl, Gas-Mitte, Rastpunkt, Gasmitte im Assistenten |
| `input/SwitchFunction.MODE_3D`, `KeyDefaults.MODE_3D`, `InputConfig.mode3dUnlocked` | Belegung, Taste, Freischaltung |
| `drone/client/PilotSession` | `mode3dActive`, `mode3dBlock`, Meldungen, 3D-Gas, Test-Haken `setTestMode3d` |
| `input/client/screen/ModesTab`, `ReceiverWizardScreen` | Zeile, Freischalten, Lampe, Hinweise |
| `osd` | `OsdData.mode3d`, Gas mit Vorzeichen, `propwash.osd.mode_3d.*`, Warnung `ARM_THROTTLE_3D`, Funk-HUD |
| `guide/scenes/Mode3dScene` | Flugschul-Lektion „3D-Modus“ (6 Schritte), Props drehen im Diorama rückwärts |
| `server/rules/WorldRuleSpecs.ALLOW_3D_MODE` | Welt-Regel |

## 8. Tests

- JUnit `sim.Mode3dTest`: Gas-Abbildung mit Totzone und Richtung, Scharfschalten (Mitte, kopfüber), Umkehrschub normaler
  und symmetrischer Props, Vorzeichen von Luft- und Reaktionsmoment, Nulldurchgang ohne Sprung (10–300 ms), Schweben
  kopfüber (Höhe ±0,5 m über 6 s), gleiche Knüppelrichtungen kopfüber, Rolle und kopfüber weiterfliegen, Umkehrschub
  schneller als freier Fall, Start kopfüber vom Boden, Abschalten kopfüber in Angle fängt sich, Hold ohne 3D,
  Totzone ohne Wirkung ohne 3D.
- JUnit `input.Mode3dInputTest`: Modusfunktion, letzte Eingabe zählt, Sperren, Gas mit kalibrierter Mitte, Gamepad,
  schrittweises Gas mit Rastpunkt, `RadioFrame`, Assistent, Freischaltung in Kopie und Speichern; `parts.PartJsonTest`
  mit `reverse_thrust`; `guide.FlightSchoolScenesTest` mit der neuen Szene.
- Selbsttest (beide Loader, `drone.client.Mode3dSelfTests`, echter Eingabeweg mit simulierter Funke bzw. Tastatur):
  gesperrt; Funke mit 3D-Schalter (scharf nur in der Mitte mit OSD-Warnung, Steigen, Nullschub, Motoren rückwärts, Taste
  gegen Schalter, Schalter übernimmt wieder); kopfüber gelandet abheben, kopfüber schweben, Rolle zurück; nur Tastatur;
  Welt-Regel aus.
- Bildschirmfotos (Fabric): `configurator_modes_3d(_de)`, `mode3d_inverted_los(_de)`, `mode3d_inverted_fpv`,
  `flight_school_3d`, `flight_school_3d_takeoff`.
