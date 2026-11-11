# Kompass im Flug-Zustand (Add-on-API 1.7)

Für Add-on-Autoren. `FlightState` nennt ab API 1.7 den Kurs, den der Flight Controller annimmt, die Abweichung vom
echten Kurs und ob der Kompass gestört ist. Damit kann eine Flughilfe eine Kompassstörung erkennen und wie eine echte
GPS-Drohne reagieren. Stabil ist nur, was unter `de.lardtom.propwash.api` steht; die Anleitung steht in
[`api/addon-guide.md`](api/addon-guide.md) Abschnitt 20.7.

## 1. Warum

Propwashs Positionshalten (POSHOLD des Piloten, `AssistOutput#holdPosition` und `#velocity`) rechnet mit einem
Kompassfehler, der je Flug neu gewürfelt wird: meist 1,5–7,5°, in etwa jedem zehnten Flug ein Störfeld mit zusätzlich
20–45° (zusammen 21,5–52,5°), dazu ein langsames Wandern um ±1,5°. Der Regler dreht seine Neigung um diesen Fehler.
Bis etwa 15° hält die Drohne kaum schlechter als ohne Fehler, ab etwa 30° kreist sie beim Halten („Toilet Bowl“),
bei 42° mit rund 4 m/s auch ohne Wind. Das bleibt so, es ist realistisch. Bis API 1.6 konnte ein Add-on die Störung
aber nicht erkennen.

## 2. Lesen

| Methode | Bedeutung |
|---|---|
| `compassHeadingDeg()` | Kurs, den der Flight Controller annimmt, in Kompassgrad wie `headingDeg()` (0 Norden, 90 Osten, 0 bis unter 360) |
| `compassErrorDeg()` | `compassHeadingDeg() − headingDeg()`, auf −180 bis 180° gefaltet. Positiv heißt: Der Flight Controller hält die Nase für weiter im Uhrzeigersinn gedreht, als sie ist. Ein Befehl aus `holdPosition`/`velocity` neigt die Drohne dann zuerst um diesen Winkel gegen den Uhrzeigersinn versetzt. |
| `compassHealthy()` | `false`, sobald der Betrag des Fehlers `FlightState.COMPASS_DISTURBED_DEG` (15°) erreicht. Ungestörte Flüge bleiben unter rund 13,5°, gestörte liegen über rund 15,5°. |

```java
public void update(FlightState state, AssistOutput out) {
    if (!state.compassHealthy()) {
        out.label("ATTI");
        out.climb(0.0);
        return;
    }
    out.holdPosition(this.x, this.z);
}
```

Mit `climb(0.0)` und ohne waagerechte Vorgabe hält Propwash nur die Höhe; die Knüppel des Piloten steuern die Lage, die
Drohne treibt mit dem Wind. Das ist ATTI.

- **Am Client** (Flughilfe und Kräfte im Pilotenflug): die Werte des Flugmodells des Piloten, also genau der Fehler, mit
  dem POSHOLD und die Vorgaben der Flughilfe rechnen. Er gilt vom ersten Aufruf an und ändert sich im Flug nur durch
  das Wandern.
- **Am Server** (Add-on-Flug über `PropwashApi.drones().autonomy()`): Propwash fliegt dort wie im Heimflug mit der
  echten Lage ohne GPS- und Kompassfehler (wie `positionValid()` immer wahr ist). `compassHeadingDeg()` ist
  `headingDeg()`, der Fehler 0, `compassHealthy()` wahr. Der Wert des Clients wird nicht übertragen.
- Kräfte (`ForceProvider`) laufen auch im Flugmodell des Servers. Dort sehen sie einen eigenen, unabhängig gewürfelten
  Kompassfehler, den kein Regler nutzt; nach dem Kompass entscheidet man in der Flughilfe am Client.
- **Eigene Implementierungen** von `FlightState` (Test-Doubles aus 1.6) müssen nichts tun: Die drei Methoden sind
  `default`-Methoden mit idealem Kompass (`compassHeadingDeg()` = `headingDeg()`, Fehler 0, gesund). Wer nur
  `compassHeadingDeg()` überschreibt, bekommt Fehler und Güte daraus.

## 3. Wie echte GPS-Drohnen reagieren

Echte Flight Controller sehen den Winkelfehler nicht direkt. Sie erkennen eine Störung an Anzeichen: Die Feldstärke am
Magnetometer weicht vom Erdfeld ab, der Kurs aus der GPS-Bewegung passt nicht zum Kompass, oder die Position läuft
beim Halten im Kreis. Ursachen sind Stahl und Beton, Autos, Stromleitungen, hohe Ströme nah am Magnetometer oder eine
fehlende Kalibrierung. Die Drohne warnt dann (im OSD oder in der App „Kompassfehler“ bzw. „MAG“) und wechselt in ATTI:
Die Höhe wird weiter gehalten, die Position nicht. Positionshalten und automatische Flüge bietet sie erst wieder an,
wenn der Kompass passt oder neu kalibriert ist.

`compassHealthy()` liefert diese Erkennung fertig; ein Add-on entscheidet selbst, ob und wie es warnt und wechselt.
Propwashs eigenes POSHOLD wechselt nicht von selbst, das Kreisen ist gewollt.

## 4. Testen von Add-ons: Kompassfehler festlegen

Weil der Fehler je Flug gewürfelt wird, ist nur etwa jeder zehnte Flug gestört; ein Test kann sich darauf nicht
verlassen. Für Selbst-, Rauch- und Beitrittstests legt die System-Eigenschaft `propwash.test.compassErrorDeg=<Grad>`
den Fehler fest:

- **Wert:** Kompassfehler in Grad mit demselben Vorzeichen wie `compassErrorDeg()`, Dezimalpunkt (`40`, `-25.5`).
  Das Wandern kommt weiter dazu (Standardabweichung 1,5°, selten mehr als 4,5°). Für eindeutige Ergebnisse Werte
  deutlich neben der Schwelle wählen: bis 5° gesund, ab 25° gestört; `0` ergibt einen fast idealen Kompass.
- **Wann:** Propwash liest die Eigenschaft beim Start jedes Flugmodells, also beim Wechsel ins FPV, beim Neustart
  eines Flugs, beim Start eines Server-Flugs und in der Freecam, und nur in dem Prozess, in dem sie gesetzt ist. Ein
  laufender Flug ändert sich nicht. Alle übrigen Zufallswerte des Flugs (Wandern, GPS, Barometer, Sensorfehler)
  bleiben, wie sie ohne die Eigenschaft wären.
- **Ohne die Eigenschaft** (normales Spiel) ändert sich nichts. Ungültige oder nicht endliche Werte (`abc`, `NaN`)
  ignoriert Propwash mit einer Warnung im Log; ein gültiger Wert steht beim ersten Flug einmal als Warnung im Log.
- **Am Server** meldet `FlightState` weiter Fehler 0 (Abschnitt 2); dort wirkt die Eigenschaft nur auf die Kräfte im
  Flugmodell des Servers. Bei Beitrittstests gehört sie in den Client-Prozess, dort fliegt der Pilot.

Setzen:

- **Im Selbsttest des Add-ons** (Client und eingebauter Server laufen im selben Prozess): vor dem Start des Flugs
  `System.setProperty("propwash.test.compassErrorDeg", "40")`, sobald das Flugmodell läuft, den vorherigen Wert
  wiederherstellen (bzw. `System.clearProperty(…)`), damit spätere Schritte den gewürfelten Fehler behalten. So macht
  es Propwashs eigener Schritt `addon-api-17: Kompass` (Abschnitt 5).
- **Für einen ganzen Lauf** in der Run-Konfiguration: Loom `property('propwash.test.compassErrorDeg', '40')`,
  ModDevGradle `systemProperty('propwash.test.compassErrorDeg', '40')`.

## 5. Tests

- JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.api.compass.CompassStateTest` (Standard für alte
  Implementierungen, Schwelle 15° über 0°/360° hinweg, Werte am Client bei einem per Test-Eigenschaft festgelegten
  Fehler von 4°, 25° und 42° mit beiden Vorzeichen, das Vorzeichen an der ersten Neigung nach einem
  Geschwindigkeitsbefehl, Kreisen bei 42° gegen ruhiges Halten bei 4°, etwa jeder zehnte Flug gestört, am Server
  Fehler 0; die Test-Eigenschaft legt den Fehler neuer Flüge fest, lässt Lage, Höhenschätzung, Barometer und GPS des
  Flugs unverändert und wirkt weder mit ungültigen Werten noch im laufenden Flug).
- Selbsttest: `'-PpropwashSelftestOnly=addon-api-17: Kompass'` (Klasse `accessory.client.CompassSelfTests`): zwei
  FPV-Flüge mit Test-Eigenschaft 3° und 40°. Fühler-Kräfte lesen Kurs, Kompasskurs, Fehler und Güte; sie gleichen dem
  Kompassfehler des Flugmodells, 3° gilt als gesund, 40° als gestört. Danach ist die Eigenschaft wie vorher.
