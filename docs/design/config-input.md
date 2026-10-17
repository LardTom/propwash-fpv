# Einstellungen: Zahlen tippen, Suche, eigene Presets

Drei Rückmeldungen aus der Community zu 0.5.0: In den Zahlenfeldern (Rates) ließ sich nichts tippen, nur die Pfeile
gingen; eine Suche über alle Einstellungen mit Vorschlägen beim Tippen; eigene Presets für Rates neben den eingebauten.
Alles ist rein clientseitig: kein neues Paket, Netzprotokoll und Parts-API bleiben, das Flugmodell ist unberührt.

## 1. Zahlen tippen

### Ursache
Minecraft 26.3 liest die Tastatur über SDL3. Zeichen (`SDL_EVENT_TEXT_INPUT`, im Spiel `charTyped`) liefert SDL3 nur,
solange für das Fenster `SDL_StartTextInput` läuft. Vanilla schaltet das in `EditBox.setFocused` über
`Minecraft#onTextInputFocusChange` ein. Die eigenen Zahlenfelder (`NumberSpinner`, `MicrosField`) erben von
`AbstractWidget` und taten das nie: Tasten (`keyPressed`, also Pfeile, Enter) kamen an, Ziffern aus Ziffernreihe und
Ziffernblock nicht. Unter GLFW (bis 1.21) gab es diese Sperre nicht.

### Lösung
- Jedes tippbare Zahlenfeld meldet Fokus und Fokusverlust an `onTextInputFocusChange` (Texteingabe nur, solange es den
  Fokus hat). Der Selbsttest prüft genau das mit `SDL_TextInputActive` und schlägt ohne den Fix fehl.
- Gemeinsamer Eingabe-Puffer `ui.NumberEntry` (reines Java): Ziffern, ein Dezimaltrenner, Minus (nur wenn der Bereich
  negative Werte erlaubt; schaltet das Vorzeichen um), Cursor, Auswahl, Rücktaste und Entf (mit Strg/Cmd bis zum Rand),
  Pos1/Ende, Einfügen mit Filter, höchstens 12 Zeichen. Punkt und Komma werden beide angenommen und als Trenner der
  Sprache angezeigt (aus `propwash.content.number.decimal`). Auswertung `EMPTY`, `INVALID`, `OK`, `CLAMPED`.
- Bedienung im Feld (`ui.client.widget.NumberEditor`, benutzt von `NumberSpinner`, `MicrosField` und allen Reglern über
  `ui.client.widget.SliderTyping`):
  - Zahlenfelder: Klick ohne Ziehen oder Doppelklick startet die Eingabe mit markiertem Wert; Ziehen ändert weiter in
    Schritten, die Pfeile links und rechts bleiben Schrittknöpfe, Rechtsklick setzt den Standard. Regler setzen per Klick
    und Ziehen wie bisher den Wert; getippt wird, sobald sie den Fokus haben, dann wird die Leiste zum Eingabefeld.
  - Mit Fokus startet jede Ziffer, Minus, Punkt oder Komma eine neue Eingabe; Enter, Strg/Cmd+A, Rücktaste und Entf
    beginnen mit dem aktuellen Wert, Strg/Cmd+V mit dem Inhalt der Zwischenablage.
  - Enter (auch auf dem Ziffernblock) übernimmt, Esc verwirft und stellt den alten Wert wieder her, ↑/↓ übernimmt und geht
    einen Schritt weiter, ←/→ bewegen den Cursor (mit Umschalt markieren), Mausrad übernimmt und ändert.
  - Fokusverlust (Klick daneben, Tab, Tabwechsel, „Speichern & schließen“) übernimmt eine gültige Eingabe, eine ungültige
    wird verworfen.
- Grenzen: Jedes Feld kennt seinen Bereich in Anzeige-Einheiten (Rates und PID aus `TuneParam`, Welt-Regeln aus der
  Regel, Kreativ-Editor aus `CreativeParam`, Kamerawinkel aus dem Halter, Funken-Bereiche 900–2100 µs). Während der
  Eingabe zeigt ein Tooltip unter dem Feld „Erlaubt: a – b“; liegt der Wert außerhalb, werden Text und Rahmen orange und
  der Tooltip nennt den Wert, auf den begrenzt wird. Nach Enter steht das kurz in der Fußzeile, der Rahmen bleibt 1,6 s
  orange. „-“ oder „.“ allein sind ungültig (rot); Enter übernimmt dann nicht, Esc stellt zurück. Auf das Raster des
  Werts (z. B. 5 °/s) rundet wie bisher `TuneParam.sanitize`.
- Regler: Alles, was wie ein Regler aussieht, ist tippbar und verhält sich gleich. Beide Regler-Klassen nutzen dieselbe
  Eingabe `ui.client.widget.SliderTyping` (Tasten, Fokus und SDL-Texteingabe, Übernehmen, Zeichnen des Eingabefelds,
  oranger Rahmen nach dem Begrenzen):
  - `ValueSlider` (Propwash-Regler) wird tippbar, wenn sein Tab `typed(…)` aufruft: PID-Tab (alle Regler der Gruppen,
    `TuneParam` mit Anzeige-Skala, bei Filtern ist 0 = aus), Rates-Totzonen, Scharf-Winkel, Welt-Regeln und
    LED-Helligkeit – das sind alle `ValueSlider` in Fenstern.
  - `StepSlider` (Vanilla-Regler im Propwash-Look, für Addons freigegeben) ist seit 0.5.0 von Haus aus tippbar: GPS-Rettung
    (Rückflughöhe, Geschwindigkeit, Sinkrate, Mindestentfernung, Sicherheitsabstand, Übernahme-Schwelle), Rennen
    (Runden, Startampel, Ideallinie), Video (eigenes Sichtfeld der Kreativ-Brille, Effektstärke, Latenz, Reichweite,
    Bildrauschen), Empfänger (Totzone, Tastatur-Ausschlag und -Gas-Tempo, im Tab und im Assistenten), Audio
    (Lautstärken, Drohnen mit Live-Sound), Darstellung (Drohnengröße, Unschärfe, Sichtweite, Staubmenge), OSD (Deckkraft,
    Größe, Linien, Leiterabstand, Elementgröße) und der Kamerawinkel im Wartungsmenü.
- Einheit: Getippt wird, was die Beschriftung zeigt. Der Regler rechnet mit `ui.SliderScale` (reines Java) zwischen
  Rohwert und Anzeige um: Anzeige = Rohwert × Faktor + Versatz. Ohne Angabe ist das 1:1 (Meter, Sekunden, Grad, Runden,
  Rettungs-Schwelle schon in %). `percent()` für Regler mit Rohwert 0–1 und Anzeige in Prozent (Lautstärken,
  Unschärfe, Staub, Totzone, Tastatur-Ausschlag), `display(100, 0)` für Gas-Tempo in %/s, `display(5, 0)` für Deckkraft
  und Linien des OSD (Stufen zu 5 %), `display(10, 50)` für die OSD-Größe (50–200 %), `display(5, 5)` für den
  Leiterabstand (5–30°). Die Elementgröße hat vor 50 % die Stufe „erbt“; `typedRange(1, …)` lässt sie beim Tippen aus,
  „erbt“ bleibt am linken Ende des Reglers. Das Zeichen `%` tippt man nicht mit; wer es mit einfügt, dem filtert die
  Eingabe es heraus.
- Dezimalstellen und Grenzen kommen aus dem Raster: Ein Regler mit Schritt 0,5 % nimmt eine Nachkommastelle, einer mit
  ganzen Schritten keinen Trenner. Die Grenzen werden auf diese Stellen gerundet, damit Regler mit `float`-Grenzen
  (Gas-Tempo 0,05F = 5 %) ihre angezeigten Enden auch getippt annehmen. Getippte Werte rasten wie beim Ziehen auf den
  Schritt ein (Sichtweite 100 → 96 m bei 16er-Schritten). Bei Prozent-Reglern nennt der Hinweis die Grenzen mit %
  (`propwash.ui.number.percent`).
- Die Beschriftungen der Regler zeigen Kommazahlen im Trenner der Sprache (`Numbers.fixed`), wie das Eingabefeld:
  GPS-Rettung, Totzone, Drohnengröße (vorher immer mit Punkt).
- Fokus: Klick (setzt wie bisher auch den Wert an der Mausposition), Tab oder der Sprung aus der Suche geben dem Regler
  den Fokus; danach startet jede Ziffer die Eingabe. Die GPS-Rettung scrollt beim Sprung jetzt zum Regler
  (`RescueConfigTab#reveal`), vorher bekam ein verdeckter Regler keinen Fokus. Die übrigen Tabs mit Bildlauf scrollen
  schon über `UiTab#reveal`.
- Fenster außerhalb des Konfigurators (Wartungsmenü der Drohne, Empfänger-Assistent): Esc verwirft eine laufende
  Eingabe, statt das Fenster zu schließen (`NumberInput.cancelEditing`), ein Klick neben den Regler nimmt ihm den Fokus
  und übernimmt die Eingabe (`NumberInput.releaseOnClickAway`). Im Wartungsmenü ist das nötig, weil ein fokussierter
  Regler die Zifferntasten bekommt – sonst würden die Schnellleisten-Tasten über einem Fach nach einem Klick auf den
  Regler nicht mehr tauschen. Ohne Konfigurator gibt es keine Fußzeile; der orange Rahmen und der Tooltip zeigen das
  Begrenzen.
- Ohne Texteingabe bleiben nur die Regler im Einstell-Modus der Gates: Sie liegen im HUD, Enter gehört dort dem Gate,
  und eine Texteingabe im Spiel würde bei IME-Nutzern das Eingabefenster öffnen. `StepSlider#untyped()` gibt es für
  Addons, Propwash nutzt es nicht.
- Pfeiltasten ohne laufende Eingabe bleiben je Regler wie bisher: `ValueSlider` ändert mit allen vier Pfeilen,
  `StepSlider` mit ←/→, sobald er per Maus oder Tab fokussiert ist (Vanilla-Verhalten, ↑/↓ wechseln dort den Fokus).
  Während der Eingabe gelten bei beiden dieselben Tasten wie oben.

## 2. Suche im Konfigurator

### Bedienung
- Suchfeld rechts im Kopf des Konfigurators, Lupe davor, Kurzbefehl Strg+F (macOS Cmd+F, Strg+F geht dort auch).
  Propwash belegt sonst nur Strg+S im Konfigurator; Vanilla nutzt Strg+F in Fenstern nicht.
- Beim Tippen erscheint eine Liste mit bis zu 8 Vorschlägen: Name der Einstellung mit hervorgehobenem Treffer, rechts der
  Tab (bei Tabs „Tab“). ↑/↓, Bild↑/Bild↓ oder das Mausrad wählen, Enter oder Klick springt. Esc leert zuerst das Feld,
  ein zweites Esc verlässt es, erst danach schließt Esc wie gewohnt den Konfigurator.
- Sprung: Tab öffnen, bis zur Einstellung scrollen (`ConfigTab.reveal`, für `UiTab` über `ScrollPanel.reveal`, die
  Einstellung landet im oberen Drittel, bei Tabs mit Zeilenrastern auf einer Rasterzeile), 2,5 s pulsierender Rahmen in
  Akzentfarbe und Fokus auf die Einstellung – ein Zahlenfeld lässt sich danach sofort tippen. Bei Tab-Treffern leuchtet
  der Tab in der Seitenleiste.

### Index (aus dem Code, nicht von Hand gepflegt)
`ui.client.search.SettingsIndex` baut den Index bei jedem Fokus auf das Suchfeld neu (etwa 250 Einträge, gemessen
26 ms): Er erzeugt alle Tabs mit `ConfigTabs.create()` frisch, baut sie mit einem Mess-Host in derselben Größe wie den
echten Inhaltsbereich auf und liest jedes Widget aus:
- Name: Beschriftung des Widgets (`getMessage`, bei Vanilla-Reglern und Umschaltern der Teil vor „: “, bei
  `LabeledValue` das Label). Tabs können ein Widget mit `SearchTags.label(…)` benennen (der Tasten-Tab nennt die
  Belegungsknöpfe nach ihrer Taste) oder mit `SearchTags.hide(…)` ausblenden (die Zurücksetzen-Knöpfe je Taste).
- Beschreibung: der Tooltip über die Bildschirmleser-Ausgabe des Widgets (`NarratedElementType.HINT`).
- Zweite Sprache: Für jeden Übersetzungsschlüssel in Name und Tooltip kommen die Texte aus `en_us` und `de_de` der Mod
  dazu, so findet „Totzone“ die Totzone auch im englischen Spiel.
- Art: Einstellung, Welt-Regel (Tab `world_rules`), Tastenbelegung (Tab `keys`) oder Tab; Tabs selbst sind eigene
  Einträge. Die Art und der Tabname zählen als schwache Stichwörter.
- Tabs von Addons kommen automatisch mit. Den Sprung können sie mit `reveal(AbstractWidget)` verfeinern.
- Gefunden wird, was der Tab gerade zeigt: Bei Rates-Typ „Actual“ gibt es keine Felder „RC Rate“, der Tab „Rates“ und
  der Rates-Typ werden trotzdem gefunden.

### Rangfolge (`ui.SettingsSearch`, reines Java)
Text wird gefaltet (klein, ohne Akzente, ß → ss, Satzzeichen → Leerzeichen; Umlaute zusätzlich als ae/oe/ue). Jedes
Suchwort muss irgendwo passen (UND). Je Wort zählt der beste Treffer: Wortanfang im Namen 100 (Namensanfang 110),
irgendwo im Namen 60, Name der anderen Sprache 75/45, Stichwort 60/35, Tabname 30/15, Synonym 55 (im Namen) bis 20,
Tooltip 25/12, Tippfehler (Buchstaben in Reihenfolge innerhalb eines Worts, mindestens 3 Zeichen und 60 % des Worts) 8.
Dazu für die ganze Eingabe: gleicher Name +1000, Namensanfang +300, Wortanfang +150, irgendwo +80, Tabs mit passendem
Anfang +120. Gleichstand: kürzerer Name zuerst. Doppelte (gleicher Name im selben Tab) erscheinen einmal.

Synonyme stehen in `assets/propwash/search/synonyms.txt` (eine Gruppe je Zeile, `|` trennt, Deutsch und Englisch
gemischt, per Ressourcenpaket erweiterbar): Rates/Empfindlichkeit, Totzone/Deadband, FOV/Sichtfeld, Gamerule/Welt-Regel,
Taste/Keybinding, Lautstärke/Volume und über 35 weitere aus der Szene (43 Gruppen). Ein Suchwort ab 3 Zeichen erweitert sich auf alle
Wörter einer Gruppe, zu deren Wort es passt.

## 3. Eigene Presets

### Entscheidung: ein Preset je Tab
Eigene Presets gibt es getrennt für den Tab „Rates“ und den Tab „PID-Tuning“ (PID, Regler, Gas, Filter, Halten). Rates
sind das Gefühl des Piloten und passen auf jede Drohne; PID und Filter gehören zum Aufbau. Ein gemeinsames Preset würde
beim Wechsel des Rates-Gefühls die PID einer anderen Drohne überschreiben. Ein Preset nur für Rates hätte die Bitte um
PID-/Filter-Presets nicht erfüllt, eins je Abschnitt (PID, Filter, Gas getrennt) wäre zu kleinteilig für einen Tab.

### Was ein Preset enthält
- Rates: Rates-Typ und die drei Werte des Typs je Achse (wie die eingebauten Presets). Die Totzonen bleiben, sie gehören
  zur Funke. Gespeichert werden die wirksamen Werte, deshalb fühlt sich das Preset auf jeder Drohne gleich an.
- PID-Tuning: nur die Abweichungen vom Standard der Drohne (Filter, Gas, TPA, Modi, Halten) und die PID-Gains nur, wenn
  sie von Hand gesetzt sind (dann mit Quelle „von Hand“). Laden setzt erst alle Werte des Tabs auf den Standard der
  Drohne und dann die des Presets – auf einer anderen Drohne bleiben deren abgeleitete Werte. Eingebaut ist dort
  „Standard (aus dem Aufbau)“. Der Scharf-Winkel und der Kamerawinkel gehören zur Drohne und bleiben.
- Ohne Drohne (Vorlage für neue Drohnen) speichert der Konfigurator wie bisher keine Gains von Hand.

### Bedienung
Abschnitt „Voreinstellungen“ am Ende beider Tabs: Auswahl mit den eingebauten Presets und danach den eigenen
(„Eigene: Name“), „Übernehmen“; darunter Namensfeld mit „Merken“ (gleicher Name überschreibt, ohne Name „Meine Rates“ bzw.
„Mein Tuning“, bei Bedarf mit Nummer), „Umbenennen“ und „Löschen“ (zweiter Klick innerhalb von 3 s bestätigt); darunter „Code kopieren“
und „Code einfügen“. Die Auswahl zeigt das Preset, das gerade gilt. Übernehmen ändert das Tuning wie jede andere
Änderung, gespeichert wird es erst mit „Speichern & schließen“; Merken, Umbenennen und Löschen schreiben sofort.

### Speicherung
`config/propwash/tune_presets.json` des Spielers (`ui.client.TunePresetStore`, Listen `rates` und `pid` mit Name und
Werten), höchstens 32 je Tab, Namen bis 24 Zeichen, Groß-/Kleinschreibung zählt beim Vergleich nicht. Beim Laden
prüft `TunePresetBook.validate` die Datei: unbekannte oder fremde Schlüssel fallen weg, Werte werden wie im Tuning
begrenzt, doppelte und leere Namen verworfen.

### Code
`PWT1-` + Base64url (ohne Füllzeichen) eines roh deflateten Texts mit Zeilen `section=rates|pid`, `name=…` und
`schlüssel=wert` – dasselbe Muster wie der Teilen-Code des Drohnen-Editors (`PWD1-`), bewusst nicht das Sharecode-Format
`PW1-` von Just More Parts, das ganze Drohnen beschreibt. Ein Rates-Preset ergibt etwa 170 Zeichen. Einfügen legt das
Preset bei den eigenen Presets seines Tabs ab (bei gleichem Namen mit anderen Werten mit „(2)“ usw.), ungültige Codes,
fremde Schlüssel und Werte außerhalb der Grenzen werden abgelehnt bzw. begrenzt.

## 4. Tests
- JUnit: `SliderScaleTest` (Umrechnung, Dezimalstellen aus `float`-Schritten, Grenzen, OSD-Stufen),
  `StepSliderTypingTest` (Tippen in Einheit der Anzeige, Prozent, Raster, Begrenzen, ganze Zahlen ohne Trenner, Esc,
  OSD-Stufen mit `typedRange`, `untyped`),
  `NumberEntryTest` (Tippen, Trenner, Vorzeichen, Cursor, Auswahl, Einfügen, Grenzen, Zahl aus der Anzeige),
  `SettingsSearchTest` (Faltung, Rangfolge, zweite Sprache, Synonyme, Tooltips, Doppelte, Tippfehler, mitgelieferte
  Synonyme), `TunePresetsTest` (Erfassen, Laden auf anderer Drohne, gleiche Wirkung wie eingebaute Presets, PID-Regeln,
  Code), `TunePresetBookTest` (Merken, Grenze, Umbenennen, Löschen, Einfügen, Prüfung der Datei).
- Selbsttest `ui: Zahlen in Rates und PID tippen …`, `ui: Einstellungssuche …`, `ui: eigene Presets …`; Bilder
  `configurator_number_typing`, `configurator_search`, `configurator_search_jump`, `configurator_rate_presets`
  (Englisch und Deutsch).
- Selbsttest `ui: Regler tippen – jeder Zahlenwert in allen Tabs …`: geht jeden Tab des Konfigurators durch, dazu
  Video mit Kreativ-Brille und eigenem Sichtfeld, beide OSD-Seiten mit gewähltem Element sowie Empfänger und Modi mit
  simulierter Funke. Jedes Zahlenfeld und jeder Regler (auch verdeckte, die der Tab per `reveal` ins Bild holt, und
  gesperrte, die für die Prüfung kurz frei sind) bekommt den Fokus; geprüft werden die SDL-Texteingabe, das Tippen
  der ferneren Grenze (sonst der anderen oder der Mitte, wenn eine Grenze ein Wort wie „Aus“ zeigt oder an einen
  anderen Wert gekoppelt ist) mit der Zahl in der Beschriftung, das Begrenzen mit Hinweis und Esc. Regler ohne
  Texteingabe (auch Vanilla-Regler) und Tabs mit zu wenigen geprüften Reglern sind Fehler. Ausgenommen ist nur, was
  bewusst ohne Eingabe ist, weil die Beschriftung nicht den Reglerwert zeigt: „Vereinfachung“ im Tab Action-Cam (nur
  mit Flashback sichtbar, zeigt cm und Grad); der Lauf protokolliert ihn als „bewusst ohne Eingabe“. Im Lauf: 90 Werte
  (37 `ValueSlider`, 30 `StepSlider`, 21 `NumberSpinner`, 2 `MicrosField`); ohne Kreativ-Flugcontroller fehlt der
  Drohnen-Editor, dessen Felder `NumberSpinner` wie im Rates-Tab sind.
- `ui: Regler tippen – Sprung aus der Suche, Klick und Tab auf Regler` (Suche „Return altitude“, sofort tippen, Tab
  übernimmt; Klick auf die Motorlautstärke, 35 % tippen, Esc), `ui: Regler tippen – Empfänger-Assistent …` (Totzone
  und Gas-Tempo mit simulierter Funke) und `drone: Kamerawinkel …` (tippt im Wartungsmenü, Klick daneben übernimmt,
  zu großer Winkel wird mit Hinweis begrenzt). Bilder `configurator_slider_typing` (Rückflughöhe 500 über der Grenze)
  und `configurator_percent_typing` (Motorlautstärke 85 % mit Hinweis „Erlaubt: 0 % – 100 %“), Englisch und Deutsch.
