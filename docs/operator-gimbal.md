# Zweiter Operator und Gimbal in Aufnahme und Kamerapfad

Für Add-on-Autoren und Server-Betreiber. Mit API 1.7 steuert ein zweiter Spieler mit seiner Funke den Gimbal einer
Drohne, während ein anderer fliegt oder der Server sie im autonomen Flug hält (B8). Ein Gimbal-Add-on dreht den
sichtbaren Kamerakopf am Modell, bestimmt die Brennweite, nimmt mit der Gimbal-Lage auf, zeigt das Gimbal-Bild ohne
Props, gibt dem Flashback-Kamerapfad die Lage der Gimbal-Kamera und zeigt das Livebild der eigenen Drohne (B9).

Technik und Entscheidungen: [`design/0.6.0/addon-api-namespace.md`](design/0.6.0/addon-api-namespace.md) §3.11, §3.12,
§3.19, E22, E38.

## 1. Zweiter Operator binden

```java
OperatorAccess operators = PropwashApi.drones().operators();
boolean bound = operators.bind(drone, player);   // nur am Server
operators.operator(drone);                       // Optional<ServerPlayer>
operators.unbind(drone);
```

- Das Add-on entscheidet, wann es bindet (Einladung, Knopf, Befehl). Propwash prüft beim Binden: Der Spieler ist nicht
  der Pilot dieser Drohne, fliegt keine andere Drohne, ist in derselben Dimension und in Videoreichweite (Abstand der
  Augen zur Drohne höchstens `range_blocks` des Videomoduls; unbegrenzt im Kreativmodus, mit Kreativ-Brille oder mit
  der Welt-Regel für unbegrenzte Reichweite). Höchstens ein Operator je Drohne: Ist ein anderer gebunden, ergibt `bind`
  `false`; derselbe Spieler noch einmal ergibt `true`. Wer schon eine andere Drohne bedient, wird dort gelöst.
- Propwash löst die Bindung selbst, wenn der Operator selbst fliegt (diese oder eine andere Drohne), die Dimension
  wechselt, sich mehr als 10 % über die Videoreichweite entfernt, den Server verlässt, die Drohne verschwindet oder der
  Operator die Kamera mit Esc zurückgibt. Ein Ereignis dafür gibt es nicht; `operator(drone)` ist danach leer.
- Der Operator bekommt die Meldung „Du steuerst die Kamera von %s“ und sieht das Videobild wie ein Zuschauer, ohne
  Zuschauer-Anfrage am Server; die Brille braucht er wie jeder Zuschauer, sonst zeigt das Bild „keine Brille“. Der Pilot
  bekommt „%s steuert deine Kamera“, beim Lösen „%s steuert deine Kamera nicht mehr“.
- Löst Propwash die Bindung, endet die Ansicht des Operators sofort mit „Kamera-Steuerung von %s beendet“ (mit Esc:
  „Zuschauen beendet“). Verschwindet die Drohne bei ihm (etwa bei `/propwash clear`), endet die Ansicht ebenso sofort,
  egal ob das Entfernen vor der Meldung des Servers ankommt (dann gibt sein Client die Rolle selbst ab) oder im selben
  Tick; er wartet nicht auf die Zeitgrenze des Zuschauens („Kein Bild: Kanal nicht erreichbar“).

## 2. Eingaben des Operators

- Die Funke des Operators fliegt nicht. Ihre vier Knüppelachsen (0 Roll, 1 Nick, 2 Gier, 3 Gas; Roll, Nick und Gier
  −1…1, Gas 0…1 wie `ClientInput`) und die Zubehör-Aktionen 1–4 (Tasten bzw. Schalter der Zubehör-Aktionen) gehen mit
  20 Hz als `propwash:operator_input` an den Server (Entity-Id, 4 × `short`, Bitfeld). Tastatur-Flug zählt nicht, weil
  die Tasten dabei den Spieler bewegen; Funke oder Gamepad nötig. Tests ohne Gerät setzen die Eingaben über eine
  System-Eigenschaft (Abschnitt 9).
- Der Server nimmt nur Pakete des gebundenen Operators an (Rollenprüfung) und höchstens 25 je Sekunde (gleitendes
  Fenster), alles darüber verwirft er. Er gibt jede angenommene Eingabe als `propwash:operator_state` an Pilot, Operator
  und alle Zuschauer der Drohne weiter. Beim Lösen schickt er einmal neutrale Achsen.
- Lesen: `GimbalContext#operatorAxis(i)` / `#operatorAction(n)` (bei Pilot, Operator und Zuschauern dieselbe vom
  Server weitergegebene Lage, keine Vorhersage), `FlightState#operatorAxis(i)` in der Flughilfe am Client des Piloten
  und im autonomen Flug am Server, `ClientInput#operating()` (wahr, solange dieser Client Operator ist und das Bild
  sieht). Auf Clients, die die Drohne nur sehen, sind die Achsen 0.

## 3. Gimbal in Aufnahme und Kamerapfad

- `AccessoryDefinition` erlaubt `action_cam` jetzt auch auf der Halterung `front`. So trägt ein Gimbal vorn seine
  eigene Kamera.
- `GimbalProvider#stabilizesRecording()` (Standard `false`): Bei `true` nimmt die Action-Cam die Gimbal-Lage auf, wie
  sie das FPV-Bild zeigt, ohne zweite Software-Stabilisierung (die Einstellung „Stabilisierung“ der Action-Cam gilt
  dann als aus, kein zusätzlicher Zuschnitt), und der Flashback-Kamerapfad übernimmt die Gimbal-Lage statt Rahmenlage
  plus Kamerawinkel. Propwash kennt die Halterung eines Gimbals nicht; das Add-on meldet `true` nur, wenn sein Gimbal
  die Action-Cam der Drohne trägt.
- Bei `false` bleibt alles wie in 0.5.2.

**Getrennte FPV- und Gimbal-Kamera.** Ein Add-on kann dem Piloten das feste FPV-Bild zeigen und den Gimbal trotzdem
als eigene Kamera führen. `orient` setzt `rotation` dann nur, wenn das Bild die Gimbal-Kamera zeigt; drei weitere
Methoden trennen die beiden Kameras:

- `boolean showsProps(GimbalContext context)` (Standard `true`): Propwash zeichnet im FPV-Bild die Props und Ducts der
  eigenen Drohne fest vor die Kamera. Gibt das Add-on `false` zurück, weil das Bild gerade die Gimbal-Kamera zeigt,
  zeichnet Propwash dort weder Props noch Ducts. Das gilt für das Livebild des Piloten, für Zuschauer und den Operator
  und damit auch für Fotos (`recording().photo`) und Action-Cam-Aufnahmen dieses Bilds. Propwash fragt jeden Frame
  für die Drohne, deren Bild gerade zu sehen ist; `spectating()` ist bei Zuschauern und beim Operator `true`, wie bei
  `fovDegrees`. Wirft das Add-on eine Ausnahme, bleiben die Props wie bisher.
- `boolean gimbalRotation(GimbalContext context, Quaternionf rotation)` (Standard `false`): Lage der Gimbal-Kamera in
  Weltachsen wie bei `orient`, unabhängig davon, welches Bild der Schirm gerade zeigt. Bei `true` nimmt der
  Flashback-Kamerapfad diese Lage, auch wenn der Pilot das FPV-Bild sieht oder ein Operator den Gimbal steuert.
  Propwash fragt jeden Frame, in dem es den Pfad schreibt (am Client des Piloten); `context.frame()` ist die
  Rahmenlage des Pfads, `spectating()` ist `false`, `deltaSeconds()` ist 0. Bei `false`, nicht endlicher Lage oder
  einer Ausnahme bleibt der Pfad wie bisher: Kamerablick, wenn `stabilizesRecording()` gilt und der Schirm die Drohne
  zeigt, sonst Rahmenlage plus Kamerawinkel.
- `double gimbalFovDegrees(GimbalContext context)` (Standard `Double.NaN`): horizontales Sichtfeld der Gimbal-Kamera
  (Brennweite, Zoom) in Grad für den Flashback-Kamerapfad, unabhängig davon, welches Bild der Schirm gerade zeigt.
  `fovDegrees` gilt nur für das Bild auf dem Schirm; zeigt es das FPV-Bild (dort meist `NaN`), bekäme der Pfad sonst
  das Sichtfeld der FPV-Kamera. Propwash fragt mit demselben Kontext wie bei `gimbalRotation` jeden Abtastwert des
  Pfads und kappt auf 20–150°. Bei `NaN`, einem nicht endlichen Wert oder einer Ausnahme nimmt der Pfad wie bisher
  das Sichtfeld des Bilds auf dem Schirm (`fovDegrees`, sonst Bau und Brille).
- Ohne diese Methoden bleibt alles wie vorher. Action-Cam-Aufnahmen und Fotos nehmen weiter das Bild auf dem Schirm
  auf; was sie zeigen, bestimmt `orient`.

```java
public void orient(GimbalContext ctx, Quaternionf rotation) {
    aim(ctx, gimbal);                                  // Gimbal immer nachführen
    if (showsGimbal(ctx)) rotation.set(gimbal);        // sonst bleibt das FPV-Bild
}
public boolean showsProps(GimbalContext ctx) { return !showsGimbal(ctx); }
public boolean gimbalRotation(GimbalContext ctx, Quaternionf rotation) {
    rotation.set(gimbal);                              // Lage der Gimbal-Kamera, nie das FPV-Bild
    return true;
}
public double fovDegrees(GimbalContext ctx) { return showsGimbal(ctx) ? zoom : Double.NaN; }
public double gimbalFovDegrees(GimbalContext ctx) { return zoom; }   // Brennweite der Gimbal-Kamera
```

## 4. Gimbal-Kopf am Modell

`boolean modelPose(GimbalContext context, Quaternionf rotation)` (Standard `false`, Modell starr):

- Propwash fragt jeden Frame für jede sichtbare Drohne, auf die der Gimbal passt (`appliesTo`). `rotation` ist die
  Drehung des Kopfs relativ zum Rahmen (Rahmenachsen: vorn −Z, oben +Y); `context.frame()` ist die gezeichnete Lage der
  Drohne, `spectating()` ist `false`.
- Gedreht wird das Element `gimbal_head` im Modell des Zubehörs an einer Halterung (Datei hinter
  `items/drone_render/accessory/<id>.json`): alle Flächen, deren Mitte im Quader des Elements liegt, um
  `rotation.origin` des Elements (ohne `origin` um seine Mitte). Das Element selbst bleibt im Modell ungedreht.
  Gegenstände, die an dieser Halterung angezeigt werden (`ItemDisplay`), drehen mit (`AccessoryDisplayState#gimbal`).
- Was andere Clients für die Lage brauchen (etwa die Neigung des Piloten), gleicht das Add-on selbst über
  Drohnen-Daten (`Sync.TRACKING`) ab; Propwash schickt dafür kein eigenes Paket.

## 5. Brennweite

`double fovDegrees(GimbalContext context)` (Standard `Double.NaN` = Sichtfeld aus Bau und Brille): horizontales
Sichtfeld in Grad, gekappt auf 20–150°. Wirkt auf FPV-Bild, Zuschauer, Operator, die Action-Cam-Aufnahme (sie schneidet
aus dem FPV-Bild) und den Flashback-Kamerapfad. Liefert `gimbalFovDegrees` (Abschnitt 3) einen Wert, nimmt der
Flashback-Kamerapfad diesen statt des Sichtfelds auf dem Schirm.

## 6. Aufnahme, Foto und Livebild per API (Client)

```java
RecordingControl rec = PropwashClientApi.recording();
rec.start(drone); rec.stop(); rec.recording();
rec.photo(drone);                       // PNG des aktuellen Bilds
PropwashClientApi.spectate(drone);      // Livebild der eigenen Drohne
```

- Erlaubt für den Piloten im FPV, den Operator (B8) und den zuschauenden Besitzer. `start` nutzt die eingebaute
  Action-Cam mit ihren Einstellungen, ihrem Ordner (`propwash/recordings`) und ihren Meldungen; `false`, wenn nicht
  erlaubt, keine Action-Cam an Bord oder schon eine Aufnahme läuft. Die Ladung einer Kamera mit eigenem Akku meldet nur
  der Pilot an den Server.
- `photo` speichert das Bild vor Videoeffekten und OSD als `propwash_photo_<Datum>.png` im Aufnahme-Ordner (Meldung im
  Chat mit Link); `false`, wenn nicht erlaubt oder ein Foto noch läuft.
- `spectate(drone)` startet die bestehende Zuschauer-Sitzung für eine eigene Drohne mit denselben Prüfungen wie über die
  Brille (Welt-Regel Zuschauen, Brille passt zum Videosystem, nicht selbst am Fliegen, Drohne sendet auf einem Kanal);
  beendet wird wie gewohnt (Esc, Kanal-Taste). „Eigen“ heißt: Die Drohne fliegt im Add-on-Flug (B7) dieses Spielers.
  Drohnen im Add-on-Flug ohne Piloten senden dafür ihr Bild wie geflogene Drohnen und erscheinen in der Kanalliste
  (Name des Piloten = Label des Flugs).

## 7. Beispiel

`dev/example/ExampleOperator`: Zubehör `propwash_example:gimbal_front` (vorn, mit Action-Cam, Modell mit
`gimbal_head`). Zubehör-Aktion 3 des Piloten gibt die Kamera an den nächsten Spieler in 16 Blöcken bzw. nimmt sie
zurück. `ExampleOperatorClient` hält den Horizont, schwenkt mit Operator-Achse 0 (90°/s) und neigt mit Achse 1 (60°/s).
Sein Bild ist das Gimbal-Bild ohne Props (`showsProps`), dem Kamerapfad gibt er die Gimbal-Lage (`gimbalRotation`)
und die Brennweite (`gimbalFovDegrees`); für Tests zeigt er mit `fpvImage(true)` das FPV-Bild, dann ohne eigenes
Sichtfeld auf dem Schirm (`fovDegrees` `NaN`) wie eine getrennte FPV-Kamera.

## 8. Tests

- JUnit `drone.operator.OperatorChannelTest` (Quantisierung, Bitfeld, Pakete, Rate, Rollenprüfung) und
  `drone.operator.client.GimbalRecordingTest` (Kamerapfad, Kamerapfad aus `gimbalRotation` und ohne sie wie bisher,
  Brennweite des Pfads aus `gimbalFovDegrees` und ohne sie wie bisher, Props nach `showsProps`,
  Software-Stabilisierung aus, Kappung der Brennweite, Element `gimbal_head`).
- JUnit `drone.operator.client.OperatorTestInputTest` (Test-Eigenschaft aus Abschnitt 9: Format, Kappung, Aktionen,
  ungültige Werte, ohne Eigenschaft keine Wirkung).
- Selbsttest `addon-api-17: B9` (Fabric und NeoForge), Bildschirmfoto `gimbal_head` (Fabric). Pilot und Zuschauer
  sehen im FPV-Bild Props, im Gimbal-Bild derselben Drohne keine (Zähler der gezeichneten Props); bei 30° Rollen und
  FPV-Bild auf dem Schirm (rollt mit) folgt der Kamerapfad der Gimbal-Kamera (unter 2°); bei FPV-Bild mit dem
  Sichtfeld des Baus nimmt er die Brennweite der Gimbal-Kamera (40°), ohne sie das Sichtfeld des Schirms. Der
  Operator-Schritt steuert ohne Funke über `propwash.test.operatorInput=1,0,0,0.5,1,4` (Abschnitt 9): Der
  Beispiel-Gimbal schwenkt um mehr als 20°, beim Operator und am Server kommen Achse 0 = 1, Gas 0,5 und die Aktionen 1
  und 4 an; danach wirkt der ungültige Wert `1,0,0` nicht (Achsen 0, keine Aktion), die Eigenschaft ist am Ende wie
  vorher. Zwei Schritte entfernen die Drohne nur am Client wie das Entfernen-Paket, einmal vor `OperatorRoleS2C` mit
  `NONE` und einmal im selben Tick wie diese Meldung, und verlangen jeweils das Ende der Operator-Ansicht nach
  höchstens 40 Ticks und danach das Lösen der Bindung am Server.
- Beitrittstest `PROPWASH_JOINTEST_OPERATOR=1` (Fall `operator`, `docs/testing.md`).

## 9. Testen von Add-ons: Operator-Eingaben ohne Funke

Operator-Eingaben kommen sonst nur von einer Funke oder einem Gamepad (Abschnitt 2), die ein Selbst- oder Beitrittstest
nicht bedienen kann. Für Tests legt die System-Eigenschaft `propwash.test.operatorInput` die Eingaben fest, die der
Client des Operators an den Server schickt:

- **Wert:** `<Roll>,<Nick>,<Gier>,<Gas>[,<Aktion>…]`, durch Kommas getrennt, Dezimalpunkt. Die vier Achsen wie
  `GimbalContext#operatorAxis(0…3)`: Roll, Nick und Gier −1…1, Gas 0…1; größere Werte kappt Propwash wie bei der Funke.
  Jede weitere Zahl ist eine gehaltene Zubehör-Aktion 1–4. Beispiel: `1,0,0,0.5,4` schwenkt mit voller Rollachse, Gas
  halb, Aktion 4 gehalten; `0,0,0,0` ist neutral ohne Aktion.
- **Wann:** Propwash liest die Eigenschaft bei jedem Senden (20 Hz), solange dieser Client Operator ist und das Bild
  sieht, und nur in dem Prozess, in dem sie gesetzt ist. Sie ersetzt dann Knüppel, Tasten und Schalter der
  Zubehör-Aktionen ganz, auch bei offenem Fenster. Alles danach bleibt wie mit Gerät: Der Server prüft Rolle und Rate,
  gibt die Werte an Pilot, Operator und Zuschauer weiter, `GimbalContext#operatorAxis`/`#operatorAction` und
  `FlightState#operatorAxis` zeigen sie. Eine geänderte oder gelöschte Eigenschaft wirkt beim nächsten Senden.
- **Ohne die Eigenschaft** (normales Spiel) ändert sich nichts. Ungültige Werte (weniger als vier Zahlen, nicht
  endliche Zahlen, Aktionen außerhalb 1–4, andere Trennzeichen) ignoriert Propwash mit einer Warnung im Log, dann gelten
  wieder Funke oder Gamepad; ein gültiger Wert steht einmal als Warnung im Log.
- **Nur der Client des Operators:** Einen am Server simulierten Operator ohne Client erreicht die Eigenschaft nicht.
  Bei Beitrittstests gehört sie in den Client-Prozess, der Operator ist.

Setzen:

- **Im Selbsttest des Add-ons** (Client und eingebauter Server im selben Prozess): sobald der Spieler Operator ist
  (`PropwashClientApi.input().operating()`), etwa `System.setProperty("propwash.test.operatorInput", "1,0,0,0.5")`;
  zum Abgeben über eine Aktion diese einige Ticks halten (`"0,0,0,0,4"`). Danach den vorherigen Wert wiederherstellen
  bzw. `System.clearProperty(…)`, damit spätere Schritte wieder Funke oder Gamepad lesen. So macht es Propwashs eigener
  Operator-Schritt in `addon-api-17: B9` (Abschnitt 8).
- **Für einen ganzen Lauf** in der Run-Konfiguration: Loom `property('propwash.test.operatorInput', '1,0,0,0.5')`,
  ModDevGradle `systemProperty('propwash.test.operatorInput', '1,0,0,0.5')`.
