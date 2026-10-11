# Maus + Tastatur als Eingabequelle

Stand 0.5.0. Spielerseite: `docs/mouse-control.md`. Ziel: Spieler, die nur Maus und Tastatur haben, sollen in Angle
sicher und in Acro sauber fliegen können, ohne Flughilfe und mit derselben Latenz wie eine Funke.

## 1. Quelle und Konfiguration

- Auswahl über `InputConfig.selectedGuid = "mouse"` (`InputConfig.MOUSE_GUID`), angeboten als „Maus + Tastatur“ im
  Empfänger-Tab und im Assistenten. `InputConfig.withoutDevice(guid)` gilt für `keyboard` und `mouse`:
  `DeviceChooser.pick` öffnet dann kein Gerät, `neverAskFor` und `sanitize` behandeln beide nie als neues Gerät.
  `keyboardSelected()` ist auch bei der Maus wahr (die Tastatur gehört zur Quelle), `mouseSelected()` nur bei der Maus.
- Einstellungen in `InputConfig.mouse` (`input.MouseSettings`, persönlich, Datei `input`): Modus (`MouseMode`
  AUTO/RATE/HOLD), `xAxis` (`MouseAxis` ROLL/YAW), Empfindlichkeit, DPI, Glättung (ms), Totzone, Expo, Rückstellung
  (1/s), `invertY`, Mausrad-Schritt (0 = aus). `sanitize()` repariert fehlende Enums, NaN und Bereiche.
- Veröffentlicht wird wie beim Tastaturflug `RadioFrame(RadioSource.KEYBOARD, „Maus + Tastatur“, "mouse", …)`. Alles,
  was für die Tastatur gilt, gilt damit auch für die Maus: Start in Angle (`keyboard.startInAngle`), Modus-Tasten,
  Arm-Taste, Eigene Befehle der Tastatur, kein Funkverlust im OSD. Eine eigene `RadioSource.MOUSE` gibt es bewusst
  nicht (jede Abfrage auf `KEYBOARD` hätte sie kennen müssen); wer die Maus unterscheiden muss, prüft `deviceGuid`.

## 2. Mausbewegung → Knüppel (`input.MouseSticks`, reines Java)

- Eingang: Mausschritte („counts“) mit Zeitstempel in ns (`motion(t, dx, dy)`) und je Bild `update(now, dt, …)`.
  Für Vorschau, Vorführung und Tests verteilt `update(dx, dy, dt, …)` die Schritte eines Bildes gleichmäßig auf 8
  Zeitpunkte innerhalb des Bildes.
- DPI-unabhängig: Schritte ÷ DPI × 2,54 = cm auf dem Tisch. Bei 100 % Empfindlichkeit:
  - **Tempo:** Vollausschlag bei 30 cm/s (`RATE_FULL_CM_PER_SECOND`), Ausschlag = Maustempo × Empfindlichkeit / 30.
  - **Halten:** Vollausschlag nach 6 cm (`HOLD_FULL_CM`), der Knüppel integriert die Bewegung und bleibt stehen.
    Ohne Bewegung im Glättungsfenster wandert er mit der Rückstellung (Anteil Vollausschlag je Sekunde) linear zur
    Mitte. Wechsel zwischen Tempo und Halten setzt die Halteposition auf 0.
  - **Automatisch:** Halten, wenn der Flugmodus selbst nivelliert (Angle, Horizon; dazu Höhe/Position halten, die auf
    Angle aufsetzen), sonst Tempo (`MouseMode.holds(selfLevel)`, `selfLevel = flightMode != ACRO`).
  - Maus X auf Gieren nutzt immer Tempo (Gieren ist in jedem Modus eine Drehrate).
- Glättung: Fenster W = `smoothingMs`. Ist W ≤ dt des Bildes, gilt der Mittelwert des Bildes (Schritte ÷ dt), sonst ein
  Dreiecksfenster über die Ereignis-Zeitstempel der letzten W (Gewicht 4/W² · min(a, W − a) für das Alter a). Das
  Dreieck setzt periodische Mausberichte lückenlos zusammen (Teilung der Eins, solange W/2 ein Vielfaches des
  Berichtsabstands ist: 20 ms passt zu 125/250/500/1000 Hz) und hat dieselbe mittlere Verzögerung wie ein Rechteck
  (W/2). Gemessen im JUnit mit ±0,3 ms Zeitstempel-Zittern bei 60–360 Bildern/s: 125-Hz-Maus innerhalb ±20 %,
  500/1000 Hz innerhalb ±5 % des Sollausschlags. Ein Rechteck gleicher Länge schwankte bei 125 Hz um +42 %, die frühere
  Verteilung der Schritte auf ganze Bilder (ohne Zeitstempel) ebenso. Weitere Glättung gibt es nicht.
- Danach Totzone (Bereich um 0 wird 0, Rest neu skaliert) und Expo (`(1−e)·x + e·x³`), beides je Achse, Ergebnis auf
  ±1 begrenzt (`MouseSticks.shape`).
- Vorzeichen: Maus nach rechts = Roll bzw. Gieren rechts (+). Maus vom Körper weg (SDL `yrel < 0`) = Nick vorwärts (+,
  Nase runter, wie ein Knüppel); `invertY` dreht das (Maus vor = Nase hoch wie Hochschauen im Shooter).

## 3. Mausereignisse im Spiel (`input.client.MouseCapture`, Mixin `SDLEventHandlerMixin`)

- `handleMouseMotionEvent` (HEAD) liest `xrel`, `yrel`, `timestamp`, `windowID` und `which` direkt aus dem
  SDL-Ereignis, also schon beim Abholen der Ereignisse am Bildanfang. Minecraft selbst legt die Bewegung erst über
  `Minecraft#execute` in die Aufgabenliste; `TransmitterInput#onFrame` (TAIL von `pollEvents`) sieht sie so im selben
  Bild wie eine Funke, die dort per SDL abgefragt wird. Die Maus hat damit dieselbe Latenz wie Funke/Gamepad.
- Angenommen werden nur Ereignisse des eigenen Fensters (`SDL_GetWindowID`), keine Touch- (`SDL_TOUCH_MOUSEID`) und
  Stift-Mäuse (`SDL_PEN_MOUSEID`). Für den Flug nur, wenn im vorigen Bild geflogen wurde: Sitzung aktiv, kein Fenster
  offen, Zeiger gefangen (`MouseHandler#isMouseGrabbed`) und Fenster aktiv. Der Sprung, den `grabMouse` beim Schließen
  eines Fensters durch `SDL_WarpMouseInWindow` auslösen kann, fällt so weg. Relative Daten sind unabhängig von
  Monitor, Fenstergröße und GUI-Skala; Minecraft 26.3 hat keinen eigenen Schalter „Rohe Eingabe“ mehr.
- Zeitstempel (SDL3, ns seit `SDL_GetTicksNS`) werden in `MouseFlight` auf [jetzt − 1 s, jetzt] begrenzt; fehlende (0)
  gelten als „jetzt“.
- `handleMouseWheelEvent` (HEAD, abbrechbar): Beim Mausflug mit Mausrad-Gas zählt Propwash die Rasten
  (`direction == SDL_MOUSEWHEEL_FLIPPED` dreht zurück, damit „Rad vom Körper weg“ immer mehr Gas ist) und bricht das
  Vanilla-Ereignis ab – die Schnellleiste wechselt dann nicht.
- Vanilla-Mausblick: `MouseHandler#turnPlayer` ist während des Steuerns schon gesperrt (`mixin.drone.client.MouseHandlerMixin`).

## 4. Zusammensetzen (`TransmitterInput#publishMouse`)

- `KeyboardFlight` liefert Gas (W/S mit Gas-Halten und Gas-Tempo, dazu die Mausrad-Schritte über `nudgeThrottle`,
  3D-Gasmitte unverändert über `ThrottleCentering`), Gieren (A/D) und die Pfeiltasten. Mit `xAxis = YAW` steuern A/D
  Rollen (`yawKeysSteerRoll`).
- Knüppel = Tastatur + Maus je Achse, auf ±1 begrenzt. Ohne Flug (Fenster offen, Zeiger frei, keine Sitzung) setzt
  `MouseFlight` die Maus-Knüppel auf 0 und verwirft die Halteposition.
- Taste „Maus: Knüppel zentrieren“ (`kb_mouse_center`, Standard mittlere Maustaste) setzt Halteposition und
  Glättungsfenster zurück. Sie teilt sich die Taste mit Vanilla „Block auswählen“, das beim Steuern ohnehin geleert
  wird, und „Zuschauer-Leiste“ (nur im Zuschauermodus) über `KeyDefaults.SHARED`; `KeyDefaults.Entry#mouse` trennt
  Maus- von Tastaturnummern in der Doppelungsprüfung.
- Keine Flughilfe: Die Maus erzeugt nur Knüppelwerte, Rates, PIDs, Flugmodi und Physik bleiben unverändert;
  `AssistText`/`LapAssist` kennen die Eingabequelle nicht, Runden zählen normal. Nichts davon wird übertragen.

## 5. Oberfläche

- Empfänger-Tab (`ReceiverTab`, bei gewählter Maus ohne Gerät): scrollbar (`ScrollPanel`), links Gerät, Abschnitt
  „Maus“ (zehn Einstellungen, alle mit Suchbegriff „Maus“), Abschnitt „Tastatur“; rechts bzw. darunter
  „Virtuelle Knüppel“ (`MousePreview`: zwei Knüppelfelder, Modus-Zeile, Testfläche mit Klick-und-Ziehen und Umschalter
  Angle/Acro für „Automatisch“) und die Tastenübersicht. Die Testfläche liest dieselben rohen SDL-Schritte
  (`MouseCapture.Frame#previewX/Y`), auch ohne gefangenen Zeiger.
- Assistent: Im Schritt „Gerät“ erklärt ein Text die Belegung, „Weiter“ öffnet `MouseSetupScreen` („1. Gerät ›
  2. Maus + Tastatur“): links die Vorführung (`MouseDemoView`, Ablauf `input.MouseDemo`: Maus rechts, Pause, links,
  Pause, vor, Pause, zurück; die Funke wird über `RemoteGimbalRenderer`/`RemoteDemoRenderState` gezeichnet, die
  Knüppel kommen aus `MouseSticks` mit den gewählten Einstellungen bei fester Empfindlichkeit), rechts Modus,
  Maus links/rechts, Empfindlichkeit, Y umkehren und die Testfläche. Wer in der Testfläche zieht, sieht 1,5 s lang
  seine eigene Maus auf der Funke. „Fertig“ speichert wie der Assistent.
- Flugtraining: Mit gewählter Maus zeigen acht Lektionen einen eigenen Tipp (`…lesson.<id>.tip_mouse`).
- Testfläche (`MousePreview#drawPad`): Der Anleitungstext läuft nie unter den Umschalter Angle/Acro. Er wird neben dem
  Umschalter umbrochen (bis 2 Zeilen), passt er dort nicht, beginnt er unter dem Umschalter; passt auch das nicht (z. B.
  bei 427 × 240), steht die Kurzfassung da („Klicken und ziehen zum Testen“ bzw. „Loslassen beendet den Test“). Vorher
  wurde die erste Zeile bis zum rechten Rand umbrochen und vom Umschalter überdeckt.
- Geräteliste: kurze Bezeichnung `propwash.input.mouse_keyboard.short` („Mouse + keys“, deutsch „Maus + Tastatur“), damit
  sie bei 1920 × 1080 nicht gekürzt wird. Die Tastenübersicht gibt der Beschriftung mehr als die halbe Breite, wenn die
  Tastenspalte schmal ist (vorher „Scharf schalten / en…“).
- Einstellungssuche: Die zehn Maus-Einstellungen findet die Suche nur, wenn „Maus + Tastatur“ gewählt ist – der Index
  liest jeden Tab in seinem aktuellen Zustand, und ein Treffer springt zu einer Zeile, die es im offenen Tab geben muss.
  Sonst führt die Suche über die Suchbegriffe des Geräte-Umschalters („Maus … DPI Mausrad“) dorthin, wo man die Maus
  wählt. Verworfen: den Maus-Abschnitt immer mit indexieren (Treffer zeigten auf Zeilen, die es nicht gibt) oder beim
  Treffer die Eingabequelle umschalten (änderte das Gerät ungefragt).

## 6. Tests

- JUnit: `MouseSticksTest` (Tempo, Zurückfedern mit und ohne Glättung, Halten, Rückstellung, Zentrieren,
  Empfindlichkeit, gleiche Ausgabe bei 400 und 1600 DPI für gleichen Tischweg, Y umkehren, Gieren, Automatisch,
  Totzone/Expo, Bildrate 30–500, Berichtsrate 125/500/1000 Hz bei 60–360 Bildern/s, ungültige Eingaben),
  `MouseConfigTest` (Standardwerte, `sanitize`, Quelle ohne Gerät, Kopie/Zusammenführen), `MouseDemoTest`,
  `KeyDefaultsTest` (Zentrieren-Taste, Maus- und Tastaturnummern).
- Selbsttest `input: Maus + Tastatur – …` (beide Loader): Quelle ohne Gerät; im FPV-Flug 3 cm vor = Nick 50 %
  (Angle, Halten) und bleibt, offenes Fenster sperrt Maus und Mausrad, Zentrieren-Taste, Acro mit 15 cm/s = Roll 50 %
  und Zurückfedern, Scharfschalten, Mausrad-Gas 70 % lässt die Drohne steigen, Maus vor fliegt sie vorwärts;
  Empfänger-Tab mit Testfläche, Suche „Maus“ und Assistenten-Schritt. `ui: Konfigurator-Hover – alle Tabs mit
  Maus + Tastatur bei 427x240`. Bildschirmfotos `receiver_mouse`, `receiver_wizard_mouse_device`, `receiver_wizard_mouse`
  (jeweils auch `_de`).
- Testhaken: `TransmitterInput#forceMouseCapture` (Zeiger gilt als gefangen, im Selbsttest hat das Fenster oft keinen
  Fokus), `MouseCapture#inject` (Schritte und Rasten mit Zeitstempel „jetzt“), `MouseCapture#simulateVelocity`
  (gleichmäßiges Tempo, je Bild auf 8 Zeitpunkte verteilt).
