# Auto-Arm nach Reset

Für Piloten und Entwickler. Setzt der Pilot die Drohne per **Reset** auf den Homepoint zurück (Taste „Reset“, Standard
Rücktaste, oder die Schalterfunktion Reset der Funke bzw. des Gamepads) und steht Arm danach noch auf an, wartet Propwash
nur noch auf **Gas unten** und schaltet die Drohne dann von selbst scharf. Der Arm-Schalter muss nicht erst aus- und
wieder eingeschaltet werden.

## 1. Einstellung

- K-Menü, Tab **Modi**, Zeile **Scharf schalten**: Schalter **„Auto-Arm“** neben „Tasten…“ (wie die Freigabe beim
  3D-Modus), Tooltip und Suchbegriff „Nach Reset automatisch armen“. **Standard an.**
- Gespeichert in `config/propwash/input.json` als `autoArmAfterReset` (fehlt der Eintrag, gilt an); wandert mit den
  Schalter-Einstellungen (`InputConfig#mergeSwitchesFrom`).
- **Aus** = Verhalten bis 0.6.0: nach dem Reset bleibt die Drohne entschärft, bis Arm einmal aus- und wieder eingeschaltet
  wurde (OSD „ARM-SCHALTER AN“).

## 2. Ablauf

1. Reset: Die Drohne steht gerade und entschärft auf dem Homepoint (`rescue.HomeResets`), auf Schnee, Seelensand, Schlamm
   und Honigblock obenauf, wo sie auch im Flug liegt. Ist der Homepoint belegt (etwa
   ein Block darauf gesetzt), steht sie an der nächsten freien Stelle bis 3 Blöcke daneben, nie unter dem Boden und nie
   über einer Decke, mit dem Hinweis „Reset: Homepoint blockiert – …“; Auto-Arm gilt dort genauso
   (`docs/design/home-point.md` §7).
2. Steht Arm noch auf an, wartet die Drohne. Das OSD zeigt dabei **„AUTO-ARM: GAS RUNTER“** (englisch „AUTO-ARM:
   THROTTLE DOWN“) statt „GAS NICHT UNTEN“; im 3D-Modus „AUTO-ARM: GAS MITTE“, dort heißt „Gas unten“ Gas in der Mitte.
3. Sobald das Gas unten ist, wird die Drohne scharf (Arm-Piepton wie immer). Eine laufende Absturz-Pause (1 s) oder ein
   Neustart des Flugcontrollers (0,5 s) wird dabei abgewartet.

Was als „Arm an“ zählt, ist für alle Eingaben gleich:

| Eingabe | Arm an |
|---|---|
| Funke | Arm-Schalter steht auf ARM |
| Gamepad, Arm-Taste als Umschalter (Standard) | Umschalter steht auf an |
| Gamepad, Arm-Taste gehalten | Taste ist beim Reset und danach gedrückt |
| Tastatur (Arm-Taste) | die Drohne war per Arm-Taste scharf und wurde nicht per Arm-Taste entschärft – auch wenn sie danach abgestürzt ist |

Das Warten endet ohne Scharfschalten, wenn Arm ausgeschaltet wird (danach gilt wie immer: an = Scharfschalten mit Gas
unten), wenn sich die Quelle des Arm-Schalters ändert (anderes Gerät, Belegung geändert), wenn GPS Rescue aktiv ist oder
die Drohne aufgerichtet wird.

## 3. Was gleich bleibt

- **Nur nach einem Reset.** Nach einem Absturz, beim Spawn bzw. Start der Sitzung und nach dem Landen der GPS-Rettung
  bleibt es dabei: Arm einmal aus und wieder an. Ein Reset nach einem Absturz armt dagegen wieder von selbst.
- **Alle übrigen Arm-Sperren gelten unverändert:** Neigung über dem Scharf-Winkel, Akku leer, Drohne nicht flugfähig
  (auch nach Welt-Regeln wie „Teile müssen zusammenpassen“), keine Funkverbindung, im Wasser oder in Lava, defekte
  Hardware, Turtle in der Luft. Ist eine davon gesetzt, wenn das Gas unten ist, wird das Scharfschalten wie von Hand
  abgelehnt (Meldung „Scharf schalten nicht möglich: …“) und Arm muss wieder einmal aus und an.
- **Rennstart unverändert:** Ein Reset startet keinen 3-s-Countdown und keine Startaufstellung, auch nicht über das
  Auto-Arm am Start-Gate mit „Start beim Scharfschalten“. Wer am Start-Gate mit Countdown starten will, schaltet Arm von
  Hand aus und wieder an.
- Der Server ist unverändert: Reset und Entschärfen macht der Server wie bisher, das Scharfschalten danach der Client des
  Piloten (wie jedes Scharfschalten); andere Spieler sehen die Drohne wie gewohnt scharf.

## 4. Technik

- `sim.DroneSim#homeReset(autoArm)` entschärft (`FORCED`) und verlangt wie bisher den Schalter-Wechsel; mit `autoArm`
  merkt sich `Arming#autoArm` das Warten statt der Sperre `ARM_SWITCH_NOT_RESET`. `frameArming` versucht das
  Scharfschalten im ersten Frame, in dem keine der Warte-Sperren `Arming.AUTO_ARM_WAIT` (Gas nicht unten, Gas nicht
  Mitte, Absturz-Pause, Neustart, Turtle in der Luft) mehr gesetzt ist, mit genau derselben Prüfung wie bei der Flanke
  des Schalters. `autoArmPending()`, `autoArmed()` (so geschärft, bis zum nächsten Entschärfen), `cancelAutoArm()`;
  `Telemetry#autoArmPending`, `OsdData#autoArmPending`, OSD-Warnungen `AUTO_ARM` und `AUTO_ARM_3D`.
- `drone.client.PilotSession#onReset` ruft `homeReset(InputConfig#autoArmAfterReset)`; für die Tastatur merkt
  `keyArmed`, dass der Arm-Zustand per Taste an ist, und setzt ihn beim Reset wieder. Aktive GPS-Rettung ruft
  `cancelAutoArm()`.
- `race.client.ClientRaceTracker`: Eine Scharf-Flanke mit `PilotSession#autoArmed()` zählt nicht für den stehenden
  Start.

## 5. Tests

- JUnit `sim.AutoArmTest` (14 Fälle: Warten und Scharfschalten, Einstellung aus, kein Auto-Arm nach Absturz, Spawn und
  GPS-Rettung, Absturz-Pause, Akku leer, Neigung, Funkverlust, Wasser, 3D, neue Arm-Quelle, Einstellung und Speichern).
- Selbsttest `'-PpropwashSelftestOnly=input: Auto-Arm'` (beide Loader, etwa 1 min, `input.client.AutoArmSelfTests`):
  Modi-Tab, Funke, Gamepad (Umschalter und gehalten, Reset über die Back-Taste), Tastatur mit Absturz, Einstellung aus,
  Start-Gate ohne Countdown. Bildschirmfotos (nur Fabric, `-PpropwashScreenshots=true`): `input_auto_arm_osd`,
  `input_auto_arm_osd_de`, `input_auto_arm_modes_tab`.
- Beitrittstest `PROPWASH_JOINTEST_AUTOARM=1` (`dev.AutoArmJoinCheck`, `input.client.AutoArmJoinProbe`), siehe
  `docs/testing.md`.
