# Eigene Befehle auf Funken-Schaltern (0.5.0)

Spieler legen eigene Minecraft-Befehle auf Funkenschalter, Taster und Tasten – etwa `/time set night` beim Einschalten
und `/time set day` beim Zurücklegen. Der Befehl läuft genau so, als hätte der Spieler ihn im Chat getippt. Rein
clientseitig: kein neues Paket, kein neues Netzprotokoll, keine API-Änderung, keine Änderung am Flugmodell.

## Bedienung

- Konfigurator, Tab „Modi“, Seite **„Befehle“** (Kopfzeile des Tabs: „Funktionen“ | „Befehle“). Acht Zeilen „Eigener
  Befehl 1–8“ mit Quelle, Taste und den eingetragenen Befehlen. Belegen wie jede andere Funktion: Quelle wählen oder
  „Schalter umlegen“, Bereich in µs, Halten/Umschalten, Inv.
- **„Befehl einrichten …“** (oder Doppelklick auf die Zeile) öffnet das Fenster mit zwei Feldern: „Beim Einschalten“
  und „Beim Ausschalten (optional)“. Unter jedem Feld steht live, ob der Befehl passt: Zeichenzahl, „zu lang“ (rot,
  Übernehmen gesperrt), „hier unbekannt oder nicht erlaubt“ (gelb, wird trotzdem gesendet). „Leeren“ leert beide Felder.
- „Standard“ wirkt auf die angezeigte Seite: auf „Funktionen“ bleiben die Befehle unberührt, auf „Befehle“ werden alle
  acht Befehle samt Belegung geleert. „Verwerfen“ nimmt beides zurück, bis gespeichert ist.
- Tasten „Eigener Befehl 1–8“ (Gruppe „Eigene Befehle“ im Tab „Tasten“ und unter *Optionen → Steuerung*), Standard
  unbelegt.

## Auslösen

- Ausgelöst wird an der Flanke des logischen Schalterzustands (`SwitchEvaluator`, also nach Bereich, Invertieren und
  Umschalten): aus → an läuft der Einschalt-Befehl, an → aus der Ausschalt-Befehl, falls eingetragen. Ein Taster im
  Modus „Halten“: Drücken = an, Loslassen = aus; im Modus „Umschalten“ wechselt jeder Druck.
- **Tasten:** jeder Druck führt den Einschalt-Befehl aus; ist ein Ausschalt-Befehl eingetragen, wechselt jeder Druck
  zwischen beiden (wie die Tasten für LEDs und Piepser). Ein leerer Befehl meldet „Eigener Befehl n ist leer“.
- Nur während der Spieler eine Drohne steuert (FPV oder Sichtflug, `PilotSession#isActive`). Außerhalb des Flugs ist die
  Auswertung aus; beim nächsten Flugstart gilt die aktuelle Schalterstellung als Ausgangslage, löst also nichts aus.
  Grund: Gamepads dienen oft auch zum Laufen (z. B. mit Controlify), ein belegter Knopf soll dann keine Befehle senden.
- Nie bei offenem Fenster (Chat, Inventar, Pause, Konfigurator …): die Flanke wird verworfen und gemeldet („nicht
  ausgeführt – erst das Fenster schließen“); nach dem Schließen gilt die aktuelle Stellung als Ausgangslage, es wird
  nichts nachgeholt.
- Gerät neu oder getrennt, Profil gewechselt: Ausgangslage neu, kein Befehl. Ein Schalter, dessen Belegung am Gerät
  nicht lesbar ist, löst nichts aus; wird er wieder lesbar, gilt seine Stellung als Ausgangslage (auch ohne Meldung bei
  offenem Fenster). Nach dem Empfänger-Assistenten und nach geänderten Belegungen bleiben Befehls-Schalter wie die
  Taster-Funktionen maskiert, bis sie einmal aus waren (`TransmitterInput.EDGE_FUNCTIONS`).

## Schutz gegen Prellen und Spam

- Je Schalter höchstens ein Befehl je 0,5 s. Ändert sich der Schalter in dieser Zeit, zählt die Endstellung: Prellen
  an–aus–an sendet einmal, schnelles an–aus sendet den Ausschalt-Befehl nach Ablauf der Sperrzeit nach. Eine Flanke
  ohne Befehl kostet keine Sperrzeit.
- Für alle Befehle zusammen ein Kontingent von 5 Befehlen auf einmal, danach einer je Sekunde. Der Vanilla-Server wirft
  ab dem zehnten Befehl kurz hintereinander wegen Spam hinaus (`command-spam-threshold-seconds`, Standard 10: +20 je
  Befehl, −1 je Tick, Grenze 200); das Kontingent bleibt bei der Hälfte. Was darüber hinausgeht, wird nicht gesendet und
  gemeldet („zu viele Befehle kurz hintereinander“).
- Reines Java in `input.CommandTrigger` (Flanken, Sperrzeit, Kontingent, Tasten-Wechsel), JUnit `CommandTriggerTest`.

## Senden

- `ClientPacketListener#sendCommand(String)` – derselbe Weg wie der Chat (`ChatScreen#handleChatInput`): Signieren von
  Argumenten wie bei `/msg`, Client-Befehle von Fabric API und NeoForge greifen. Keine erhöhten Rechte, der Server
  entscheidet; Fehler meldet er wie gewohnt im Chat.
- Vorab prüft der Client mit seinem Befehlsbaum (`getCommands().parse(…)`, wie `sendUnattendedCommand`), ob der Befehl
  bekannt und erlaubt ist. Unbekannt: der Befehl wird trotzdem gesendet, die Meldung ist gelb („scheint unbekannt oder
  nicht erlaubt – Antwort im Chat“). Sonst eine kurze Meldung mit dem Befehl.
- Text wie im Chat: Leerraum zusammengefasst und getrimmt, ein führender `/` ist freiwillig (ohne wird er ergänzt, `//wand`
  bleibt `//wand`), höchstens 256 Zeichen samt `/` (`SharedConstants.MAX_CHAT_LENGTH`), keine Zeichen, für die der Server
  die Verbindung trennt (`§`, Steuerzeichen, DEL; wie `StringUtil#isAllowedChatCharacter`). Zu lange oder ungültige
  Befehle lehnt das Fenster ab; beim Laden der Config fallen sie weg. Reines Java in `input.SwitchCommands`.

## Speichern

- Belegung: `DeviceProfile.switches` unter `command_1` … `command_8` wie alle Funktionen (`SwitchFunction.COMMAND_1…8`,
  hinten an das Enum angehängt, Bits 14–21 von `activeSwitches()`).
- Texte: `DeviceProfile.commands` (`{"command_1": {"on": "/time set night", "off": "/time set day"}}`) je Funke in
  `config/propwash/input.json`; die Tastatur hat einen eigenen Satz in `keyboard.commands`. Es gelten immer die Befehle des
  Eingabegeräts, mit dem gerade geflogen wird – auch für die Tasten.
- Alte Configs ohne die Felder laden mit leeren Befehlen. „Profil zurücksetzen“ im Empfänger-Assistenten behält die
  Befehle (Belegungen auf jetzt als Knüppel genutzten Achsen fallen weg).

## Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Acht neue `SwitchFunction`-Werte statt eigener Struktur | Belegen, Bereiche, Invertieren, Umschalten, „Schalter umlegen“, Knüppel-Konflikte und Speichern funktionieren wie bei jeder Funktion | eigenes Belegungssystem nur für Befehle (doppelter Code) |
| Eigene Seite „Befehle“ im Modi-Tab, umschaltbar in der Kopfzeile | 15 + 8 Zeilen passen nicht in die Höhe des Tabs, die Kopfzeile kostet keinen Platz | eigener Konfigurator-Tab (Seitenleiste ist bei kleiner GUI schon voll), Bildlauf im Modi-Tab |
| Nicht im Empfänger-Assistenten | Der Assistent richtet das Fliegen ein, seine Schalter-Seite ist auf die Flugfunktionen ausgelegt | alle 22 Funktionen im Assistenten |
| Nur im Flug | Gamepads laufen oft auch als Spielsteuerung, ein Knopf soll beim Laufen nichts senden | immer in der Welt aktiv |
| Text immer als Befehl, Schrägstrich freiwillig | Die Funktion heißt „Befehl“; Chatnachrichten per Schalter wären Spam | Text ohne `/` als Chatnachricht senden |
| Flanke bei offenem Fenster verwerfen statt nachholen | Ein Befehl Sekunden später nach dem Schließen überrascht | nach dem Schließen nachholen |
| Tasten wechseln mit Ausschalt-Befehl | Für eine Taste ist Drücken/Drücken nützlicher als Drücken/Loslassen | Taste wie ein Taster im Modus „Halten“ |
| Befehle je Funke, Tastatur mit eigenem Satz | Vorgabe „je Funke im Geräteprofil“; ohne Funke braucht die Tastatur ihre eigenen | ein globaler Satz für alle Geräte |
| Senden trotz unbekanntem Befehl | „Der Server entscheidet“; der Client-Baum kennt nicht jeden Sonderfall | unbekannte Befehle gar nicht senden |
| Wieder lesbarer Schalter startet von seiner Stellung | Eine Belegung, die gerade erst lesbar wird (neu zugewiesen, Achse wieder da), ist keine Bewegung des Piloten | die Stellung von vorher vergleichen und auslösen |
| Prüfung „bekannt“ im Fenster zwischengespeichert | Der Befehlsbaum wird nur bei geändertem Text neu befragt, nicht in jedem Bild | in jedem Bild parsen |
