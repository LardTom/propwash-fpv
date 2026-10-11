# Eingabe: Fehlersuche (Funke, Gamepad)

Für Spieler, Support und Entwickler. Technische Notizen zur SDL3-Anbindung stehen in `docs/api/input.md`.

## Keine Funke und kein Gamepad gefunden (alle Systeme)

- Funke per USB anschließen und am Sender **USB-Joystick (HID)** wählen (EdgeTX/OpenTX fragen beim Einstecken). Nur
  laden oder „Massenspeicher“ reicht nicht. Manche Kabel übertragen nur Strom.
- Der Empfänger-Assistent (Konfigurator `K` → **Empfänger** → *Empfänger-Assistent …*) zeigt jedes Gerät, das SDL
  meldet, mit GUID, Achsen und Tasten.
- Im Log (`logs/latest.log`) stehen:
  - `Propwash: SDL-Joystick/Gamepad-Subsystem gestartet (SDL 3.x.y …)` – die Controller-Unterstützung läuft;
  - `Propwash: Controller verbunden: "…" | GUID … | … Achsen …` – SDL sieht das Gerät;
  - `Propwash: SDL findet keine Controller | System … | SDL … | SDL_JOYSTICK_MFI=…` – einmal je Start der
    Erkennung, wenn SDL kein einziges Gerät meldet. Diese Zeile gehört in jede Fehlermeldung.

## macOS: SDL meldet null Controller, andere Programme sehen die Funke

Fall aus dem Discord: Radiomaster Pocket per USB an einem M1-MacBook, SDL3 startete, listete aber keinen einzigen
Joystick; Liftoff und Browser sahen die Funke. Die Umgebungsvariable `SDL_JOYSTICK_MFI=0` in der Launcher-Instanz half.

**Ursache:** Apples GameController-Framework beansprucht viele HID-Geräte. Ist SDLs MFi-Treiber aktiv (SDL-Standard),
überspringt SDLs IOKit-Treiber jedes Gerät, das das Framework für sich meldet; der MFi-Treiber selbst kann mit einer
Funke ohne Gamepad-Profil nichts anfangen. Ergebnis: Das Gerät fällt zwischen beide Treiber.

**Propwash ab 0.4.0:** Auf macOS setzt Propwash vor dem Start von SDLs Joystick-/Gamepad-Subsystem den SDL-Hinweis
`SDL_JOYSTICK_MFI=0`. Minecraft 26.3 startet selbst nur das Video-Subsystem, das Joystick-Subsystem startet Propwash beim
ersten Bild – der Hinweis kommt also rechtzeitig. Das Log bestätigt es:
`Propwash: macOS-Controller-Erkennung: SDL_JOYSTICK_MFI=0 (Propwash-Einstellung), Apples GameController-Framework aus`.

**Was das für Gamepads heißt:**

| Controller | ohne Apples Framework (Standard) |
|---|---|
| Funken im USB-Joystick-Modus (EdgeTX, OpenTX, Radiomaster, Jumper, TBS …) | werden über IOKit erkannt |
| PlayStation (DualShock 4, DualSense), Switch Pro, Xbox per Bluetooth | unverändert über SDLs HIDAPI-Treiber |
| Xbox One/Series per Kabel (Apples eingebauter Treiber) | über SDLs HIDAPI-Xbox-Treiber; den nimmt SDL auf macOS nur, solange das Framework aus ist (laut SDL-Quelltext mit verkürztem Bericht, ohne Vibration – Propwash nutzt nur Achsen und Tasten) |
| Xbox 360 per Kabel | wie bisher nur mit dem Treiber „360Controller“, unabhängig von dieser Einstellung |
| reine MFi-Gamepads (ohne eigenen SDL-Treiber) | können fehlen oder als einfacher Joystick ohne Gamepad-Belegung erscheinen |

**Einstellung:** Konfigurator → **Empfänger** → *Erweitert (macOS)* → **Apples GameController** (Standard aus, nur auf
macOS sichtbar). An = SDL nutzt das Framework wie ohne Propwash (Hinweis zurückgesetzt). Nach dem Speichern sucht
Propwash die Controller sofort neu: das offene Gerät wird geschlossen, SDLs Joystick-/Gamepad-Subsystem beendet und
neu gestartet (während eines Flugs erst danach). Gespeichert in `config/propwash/input.json` als `appleGameController`.

**Umgebungsvariable:** Steht `SDL_JOYSTICK_MFI` in der Launcher-Instanz (etwa Prism Launcher: Instanz bearbeiten →
Einstellungen → Umgebungsvariablen), gilt sie. Propwash fasst den Hinweis dann nicht an, der Schalter ist ausgegraut
und nennt den Wert. `0` = Framework aus (besser für Funken), `1` oder leer = an.

**Anderer Mod war schneller:** Startet ein anderer Mod SDLs Joystick-Subsystem vor Propwash, warnt das Log
(`… lief schon vor Propwash (anderer Mod?) …`); der Hinweis wirkt dann nur auf Controller, die erst danach eingesteckt
werden. Abhilfe: die Umgebungsvariable `SDL_JOYSTICK_MFI=0` im Launcher.

**Empfänger-Assistent:** Findet er kein Gerät, nennt er auf macOS die Einstellung (aus: „Ein Gamepad, das nur über
Apples GameController-Framework läuft …“, an: „Fehlt deine Funke, schalte es … aus“, Umgebungsvariable: deren Wert).

## Gas falsch herum (Gas unten zeigt Vollgas)

**Ab 0.4.0** erkennt der Empfänger-Assistent die Gas-Richtung nur aus der Bewegung: Gas zuerst ganz nach **oben**, dann
ganz nach **unten** schieben und unten 1,5 s halten. Wo das Gas beim Start des Schritts steht, spielt keine Rolle, kurzes
Antippen vorher auch nicht; ein Balken zeigt den erreichten Weg. Die Reihenfolge zählt: Wer erst nach unten und dann nach
oben schiebt und oben stehen bleibt, sieht für den Assistenten genauso aus wie eine invertierte Funke. Deshalb fragt er
beim Halten nach („UNTEN?“ und „OBEN?“ an den Enden, „Steht das Gas ganz UNTEN?“) – steht das Gas in dem Moment oben,
einfach nach unten ziehen und dort halten. Nach den vier Kanälen zeigt die Prüfansicht jeden Kanal live: Gas unten =
1000, oben = 2000. Läuft ein Kanal falsch herum, dort auf „Inv.“ stellen (oder später im Tab **Empfänger**). Bei
Gamepads mit federndem Gas-Knüppel den Knüppel unten festhalten, bis die Erkennung fertig ist.

**Ab 0.5.0** macht die Funke im Assistenten die Bewegung vor (erst ganz nach oben, dann nach unten halten). Und der
Assistent rät nicht mehr: Stand das Gas beim Start des Schritts an einem Ende und wird am anderen gehalten – oder weicht die
gehaltene Richtung von der bisherigen Einstellung ab (bei neuen Funken die Vorbelegung, Gas nicht invertiert) –, fragt er
„Wo steht dein Gas gerade?“ mit den Knöpfen **Ganz UNTEN (aus)** und **Ganz OBEN (voll)**. Statt zu klicken kannst du das
Gas auch einfach nach unten ziehen und dort halten. Eine invertierte Funke fragt deshalb beim ersten Einrichten einmal
nach. Nur wenn das Gas auf einer anderen Achse liegt als in der Einstellung und nicht unten startet, entscheidet allein die
Bewegung; die Prüfansicht zeigt dann, ob es stimmt.

## Scharf schalten geht nur mit der Taste

Dann ist meist kein oder der falsche Funkenschalter auf **Scharf schalten** gelegt. Im Empfänger-Assistenten, Seite
**Schalter**: Leg deinen Arm-Schalter um – die Lampe in der hervorgehobenen Zeile „Scharf schalten“ muss angehen. Geht eine
andere oder keine an, bei „Scharf schalten“ auf **Zuweisen** klicken und den Schalter umlegen; die Funke daneben zeigt, wie.
„Fertig“ ohne Arm-Schalter fragt einmal nach. Später geht das auch im Tab **Modi**.

## Scharf schalten in der Luft oder kopfüber

Standard ist wie in Betaflight höchstens 25° Schräglage („NICHT WAAGERECHT“ im OSD, Meldung „mehr als 25° gekippt“). Im
Konfigurator, Tab **Konfiguration**, Abschnitt **Scharf schalten**, stellt **Max. Scharf-Winkel** das je Drohne ein
(0–180°, wie `small_angle`); mit 180° schaltest du in jeder Lage scharf, auch in der Luft und kopfüber. Das Gas muss
trotzdem ganz unten stehen (im 3D-Modus in der Mitte). Der Wert gehört zum Tuning der Drohne und steckt auch in Sharecodes.

## Eigener Befehl läuft nicht

Eigene Befehle (Tab **Modi**, Seite **Befehle**) laufen nur, während du eine Drohne steuerst (FPV oder Sichtflug), und
nie, solange ein Fenster offen ist (Chat, Inventar, Pause, Konfigurator). Die Meldung oben rechts sagt, was passiert ist:

- **„nicht ausgeführt“ – „Erst das Fenster schließen …“**: der Schalter wurde bei offenem Fenster umgelegt. Nach dem
  Schließen wird nichts nachgeholt; leg den Schalter einmal zurück und wieder um.
- **„nicht ausgeführt“ – „Zu viele Befehle kurz hintereinander …“**: höchstens 5 Befehle auf einmal, danach einer je
  Sekunde – sonst würde der Server dich wegen Spam trennen. Je Schalter läuft außerdem höchstens ein Befehl je halbe
  Sekunde; ändert sich der Schalter in dieser Zeit, zählt die Endstellung.
- **„… scheint unbekannt oder nicht erlaubt – Antwort im Chat“** (gelb): der Befehl wurde gesendet, aber dein Client
  kennt ihn nicht oder du darfst ihn nicht benutzen (etwa `/time` ohne Operator-Rechte). Die Antwort des Servers steht
  im Chat. Eigene Befehle laufen immer mit deinen eigenen Rechten, wie im Chat getippt.
- **„ist leer“**: die Taste ist belegt, aber im Fenster **Befehl einrichten** steht kein Befehl.
- Keine Meldung: der Schalter stand beim Flugstart schon an (das zählt als Ausgangslage), die Belegung ist am Gerät
  nicht lesbar, oder es ist nur ein Befehl beim Einschalten eingetragen und du hast ausgeschaltet. Zu lange oder
  ungültige Befehle aus einer von Hand geänderten `config/propwash/input.json` fallen beim Laden weg. Die Lampe in der
  Zeile zeigt, ob Propwash den Schalter als „an“ sieht. Befehle gehören zur Funke: mit der Tastatur gilt der eigene Satz
  der Tastatur.
