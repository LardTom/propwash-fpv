# Oberfläche bei jeder GUI-Skala

Anforderung: Bei großer GUI-Skala (oder kleinem Fenster) darf kein Text mitten im Satz enden und kein Bedienelement
unerreichbar sein. Alle Propwash-Screens richten sich nach dem Platz, den sie haben.

## Wie klein die Oberfläche werden kann

Minecraft begrenzt die GUI-Skala so, dass mindestens 320 × 240 GUI-Punkte bleiben. Die Skala selbst sagt deshalb
wenig – es zählt die Größe in GUI-Punkten:

| Fenster (Bildpunkte) | Skala 2 | Skala 3 | Skala 4 | automatisch |
|---|---|---|---|---|
| 854 × 480 | 427 × 240 | 427 × 240 | 427 × 240 | 427 × 240 |
| 1920 × 1080 | 960 × 540 | 640 × 360 | 480 × 270 | 480 × 270 |
| 640 × 480 | 320 × 240 | 320 × 240 | 320 × 240 | 320 × 240 |

Auf einem Retina-Bildschirm zählt die doppelte Zahl an Bildpunkten: Ein Fenster in Standardgröße landet dort mit
automatischer Skala bei 427 × 240 Punkten.

## Regeln

1. **Zu hoher Inhalt scrollt.** Scrollleiste, Mausrad und Tastatur (Bild auf/ab, Pos1/Ende); nichts wird stumm
   weggelassen.
2. **Sätze brechen um.** Statuszeilen, Hinweise und Beschreibungen laufen über so viele Zeilen wie nötig; der Bereich
   darunter rückt nach oder scrollt.
3. **Zu schmal heißt eine Spalte.** Zwei Spalten gibt es erst ab `2 × Metrics.COLUMN_MIN + GAP` = 386 Punkten Breite;
   Formulare (`FormLayout`), die Übersicht, die Flugschul-Karten und das Flugtraining entscheiden zusätzlich nach ihrem
   Inhalt.
4. **Beschriftungen bekommen den Platz, den sie brauchen.** Die Beschriftungsspalte eines Reglers oder Umschalters wächst
   mit ihrem Text (`Metrics.labelWidth(zeile, textbreite)`), solange dem Bedienelement `CONTROL_MIN` bleibt. Kurze
   Beschriftungen stehen weiter in einer gemeinsamen Flucht.
5. **Einzeiler werden gestaucht, bevor sie gekürzt werden.** Passt ein einzeiliger Text nicht in sein Feld, zeichnet
   `ThemeText.fitted`/`ThemeText.line` ihn bis auf 70 % der Breite gestaucht (`ui.TextFit`). Erst darunter steht „…“ –
   das meldet der Selbsttest als Befund.
6. **Innere Listen geben das Mausrad weiter.** Eine Liste in einem scrollenden Bereich (Elementliste im OSD-Editor)
   nimmt das Rad nur, solange sie sich selbst noch bewegt; an ihrem Ende scrollt der Bereich darum herum weiter.
7. **Kopf- und Fußzeile gehören nicht zum Inhalt.** Inhalt endet an ihrer Kante; wächst der Text einer Fußzeile, wird
   die Fußzeile höher und der Inhalt kürzer.
8. **Keine feste Zeilenzahl für feste Texte.** Ein Feld für einen Text aus den Sprachdateien wird so hoch, wie der
   längste Text braucht, der dort stehen kann (Beschreibungsfeld im Modi-Tab, Bildunterschrift der Flugschul-Szenen) –
   gemessen in der gewählten Sprache und Breite, nicht am englischen Text im Standardfenster. Wer trotzdem Zeilen
   begrenzt (Platz in einem festen Container-Bild), fragt die Zahl über `ThemeText.shownLines(text, zeilen, platz)` ab:
   Fehlt eine Zeile, meldet der Selbsttest das als `CUT`.
9. **Tooltips bleiben im Bild.** Ein Tooltip bricht um, wird an den Bildschirmrand geschoben und zeigt, wenn er höher
   als der Bildschirm ist, einen Ausschnitt, der mit dem Mausrad scrollt (Abschnitt *Tooltips*).

## Konfigurator

- **Tabs scrollen von selbst.** Ein Tab, der sich nicht selbst um den Bildlauf kümmert (`scrollsItself()` = false),
  wird vom Konfigurator verschoben, sobald sein Inhalt höher ist als die Ansicht (`ui.client.widget.ContentScroll`).
  Der Tab rechnet weiter in den Koordinaten aus `build()`: Vor `extract`, `tick` und den Maus-Rückrufen stehen seine
  Widgets an ihrem Platz aus `build()`, gezeichnet wird verschoben (`pose`), die Maus kommt umgerechnet an. Danach
  stehen die Widgets wieder an ihrem Platz auf dem Bildschirm, damit Klicks, Fokus und Tooltips stimmen.
- **Höhe des Inhalts:** unterste Kante aller sichtbaren Widgets, dazu was der Tab mit `reportBottom(y)` meldet – in
  `build()` für feste Teile, in `extract()` für Text, dessen Höhe sich ändert (Statuszeilen). Ohne Meldung zählt nur
  die Widget-Kante.
- **Stehenbleiben beim Scrollen:** `host.contentShift()` liefert den Versatz; ein Tab zeichnet eine Vorschau damit an
  fester Stelle (Tab „Darstellung“).
- **Eigener Bildlauf:** `UiTab` mit `beginScroll()`, Freestyle, GPS Rescue, Empfänger (Maus-Ansicht) und Flugschule
  melden `scrollsItself()` und werden nicht zusätzlich verschoben. Bild auf/ab und Pos1/Ende erreichen sie als
  Mausrad-Schritte: Bild auf/ab als so viele Schritte, wie eine Seite hoch ist, Pos1/Ende als 4096. Listen, die
  zeilenweise scrollen (GPS Rescue, Flugschule), rechnen die Schritte mit `ScrollMath.rowSteps` in Zeilen um – ein
  Schritt des Mausrads ist eine Zeile, Bild auf/ab eine Seite ohne übersprungene Zeile, Pos1/Ende der Rand. Vorher
  bewegte jede dieser Tasten GPS Rescue nur um eine Zeile, und Bild ab übersprang in der Flugschule Karten. Die Leiste
  der Flugschul-Liste lässt sich ziehen (`SceneList.grab`/`drag`/`release`, im Tab und in der Übersicht); die
  Übersicht blättert auch mit Bild auf/ab und Pos1/Ende.
- **Fußzeile:** Der Status bricht um; die Höhe richtet sich nach dem längsten festen Status aller Tabs in der
  gewählten Sprache (`ui.ChromeLayout`). Eine längere Meldung wächst für ihre Anzeigedauer nach oben über den Inhalt.
  Bleiben dem Status neben den beiden Knöpfen weniger als 110 Punkte (Deutsch bei 320 Punkten Breite: 59), steht er
  über den Knöpfen in voller Breite (`ChromeLayout.footerStacked`) – vorher brach „Spieleinstellung“ mitten im Wort um
  und die Fußzeile nahm dem Inhalt 64 von 240 Punkten.
- **Kopfzeile:** Passen Name, Zustand, Funke und Suche nicht in eine Zeile, bekommt die Kopfzeile eine zweite. Der
  Name der Funke bekommt den Platz, der neben Name, Zustand und Suche übrig ist (`ChromeLayout.radioRoom`) – vorher
  höchstens ein Fünftel der Fensterbreite, bei 960 × 540 Punkten 142 Punkte, obwohl daneben Platz war. Für die
  Entscheidung „eine oder zwei Zeilen“ zählt er mit höchstens 40 % der Breite (`radioWanted`), damit ein sehr langer
  Name die Kopfzeile nicht bei jeder Größe zweizeilig macht. Reicht der Platz nicht, wird der Name gestaucht (Regel 5).
  Müsste er auch in der zweiten Zeile neben Drohnenname und Zustand gekürzt werden (unter 70 % seiner Breite), bekommt
  die Funke eine dritte Zeile für sich (`ChromeLayout.headerRows`): „Team-BlackSheep TBS Tango 2 Pro“ endete mit
  Drohne in der Hand auf Deutsch bei 320 Punkten Breite mit „Team-BlackSheep TBS…“. Die dritte Zeile kostet den Inhalt
  14 Punkte und kommt nur in diesem Fall.
- **Kopfzeile in der Pilotensitzung:** Solange man eine Drohne fliegt, steht neben „LIVE“ die Plakette „ARMEN
  GESPERRT“. Sie zählt bei der Aufteilung wie die Zustands-Plakette mit (`ChromeLayout.badgesWidth`, Abstand 6 Punkte)
  und wird immer gezeichnet. Vorher rechnete die Aufteilung ohne sie: In einem Band von Breiten nahm sie dem
  Drohnennamen den Platz („Dro…“ bei 450–460 Punkten mit Tastatur, das ist 1366 × 768 mit automatischer Skala; bei
  512 Punkten mit einer „Radiomaster Boxer“), knapp darunter fiel sie ohne Hinweis weg (427 × 240 Punkte, das
  Standardfenster). Ob die Funke neben Name und Plaketten passt, entscheidet der Platz, der dort wirklich bleibt
  (`ChromeLayout.radioSpace`), nicht die Mindestbreite von 40 Punkten – ein kurzer Name wie „Tastatur“ galt sonst
  als passend, obwohl nur 16 Punkte frei waren.
- **Tab „Übersicht“, Live-Telemetrie:** Die fünf Zeilen stehen links, die vier Motorbalken rechts, solange jede Zeile
  mit ihrem Wert in eine Zeile passt. Die Zeilen bekommen drei Fünftel der Breite oder so viel, wie die breiteste
  braucht (`OverviewTab.telemetryRowsWidth`); bleiben den Balken dann weniger als 64 Punkte, stehen sie unter den
  Zeilen (wie unter 260 Punkten Breite). Bricht dort trotzdem eine Zeile um, wächst das Panel mit, und die Balken
  rücken nach. Vorher brach der Wert in die nächste Zeile um, ohne dass sie nachrückte: Auf Englisch stand bei 367–390
  Punkten Breite (1920 × 1200 mit automatischer Skala) der Akku-Wert über „Consumption“.
- **Tab „Video“, Vorschau „So zerfällt das Bild“:** Die fünf Bilder stehen nebeneinander, solange jede Beschriftung
  ohne Trennung im Wort unter ihr Bild passt und „KEIN VIDEO“ im letzten Bild nicht gekürzt wird. Sonst stehen sie in
  Reihen zu drei, zwei oder einem Bild (`BreakupPreview.columns`); der Tab scrollt. Bei 320 Punkten Breite standen auf
  Deutsch „KEIN …“, „4 · Standbil / der“ und „5 · Verlore / n“.
- **OSD-Tab, eigene Farben:** Die beiden Hex-Felder (`#RRGGBB` für Text- und Warnfarbe) gibt es bei jeder Größe.
  Passen sie nicht neben ihre Farb-Knöpfe in drei Spalten (unter 122 Punkten Spaltenbreite), steht die Farbzeile in
  zwei Spalten, darunter jede Farbe in einer eigenen Zeile und zuletzt das Feld unter seinem Knopf – die Zeilen darunter
  rücken nach (`OsdConfigTab.rowLines`). Vorher fehlten die Felder bis 640 × 360 Punkte (1080p mit Skala 3 und 4) ohne
  Hinweis; nur die Palette ließ sich durchschalten.
- **Tab „Empfänger“:** Die Statuszeile („Verbunden: … – noch nicht eingerichtet“, „Maus + Tastatur aktiv – …“) läuft
  über die ganze Tab-Breite und bricht um; alles darunter rückt nach. Vorher stand sie einzeilig in der linken Spalte
  und verlor mit einer gewöhnlichen Funke bei jeder Größe ihr Ende – genau den Teil, der sagt, was zu tun ist. Ändert
  sich die Zeilenzahl (Hinweis läuft ab, Funke wird verbunden), baut der Tab neu. Die vier Richtungs-Umschalter sind
  so breit wie „Norm.“/„Inv.“ samt Pfeilen (`ReceiverTab.cycleWidth`, vorher höchstens 40 Punkte: „N…“); passen
  Achsen- und Richtungs-Umschalter zweier Kanäle nicht nebeneinander (`channelsPerRow`, 320 Punkte Breite), steht je
  Zeile ein Kanal.
- **Seitenleiste:** wird so breit wie der längste Tab-Name (höchstens 30 % der Fensterbreite); passen die Tabs auch
  mit 9 Punkten Zeilenhöhe nicht, scrollt sie zeilenweise (Zeilen 11 Punkte) und hält den gewählten Tab sichtbar:
  Mausrad, Scrollleiste zum Anfassen (ein Klick in ihre Bahn springt dorthin) und Bild auf/ab, Pos1/Ende, solange die
  Maus über der Leiste steht oder einer ihrer Tabs den Fokus hat (`ui.client.widget.RowScroll`, dieselbe Klasse wie
  in der Gate-Liste von „Kurs bearbeiten“). Sonst gehören diese Tasten dem Inhalt des Tabs.
- Klicks und Mausrad erreichen Tab-Widgets nur innerhalb der Ansicht – ein unter die Fußzeile gescrolltes Widget ist
  nicht bedienbar.
- **Modi-Tab, Feld unter der Liste:** Die Beschreibung der gewählten Funktion steht links neben den Knöpfen und bekam
  früher höchstens vier bis fünf Zeilen – im Standardfenster (427 Punkte breit) endeten sieben von acht geprüften
  Beschreibungen mitten im Satz. Der Text läuft jetzt um die Knöpfe herum (`ui.FlowWrap`, reines Java, JUnit
  `FlowWrapTest`): Zeilen neben den Knöpfen sind schmal, alle weiteren nutzen die volle Breite – bei 320 Punkten
  Breite braucht der 3D-Modus so 10 statt 17 Zeilen. Das Feld ist so hoch, wie die längste Beschreibung der gezeigten
  Seite („Funktionen“ oder „Befehle“) braucht; die Höhe hängt nicht von der gewählten Zeile ab, damit die Liste beim
  Anklicken nicht springt. Ist der Tab dadurch höher als die Ansicht, scrollt er. Die Zeile darunter (Befehl oder der Hinweis
  ohne Funke) nutzt die volle Breite, der Hinweis bricht um. Mit Funke steht der Befehl zwischen den beiden Knöpfen,
  solange er dort bei allen acht Befehlen passt, sonst in einer eigenen Zeile darunter.
- **Modi-Tab mit Funke:** Das Feld unter der Liste wird je Funktion aus Zeilen aufgebaut (`ModesTab.Panel`): Passt
  der Quellen-Umschalter mit seinem längsten Eintrag nicht neben „Schalter umlegen“, steht er in voller Breite darüber;
  passt „Verhalten“ nicht neben Min/Max, steht es in der Zeile darunter; die Hinweise („Erst freischalten …“, „Nicht
  belegt …“) und die Tastenzeile brechen um; die beiden Knöpfe der letzten Zeile teilen sich die Breite oder stehen
  untereinander; passt der längste mögliche Zustand nicht neben den Titel, steht er in einer eigenen Zeile. Das Feld
  ist so hoch wie die höchste Funktion der Seite, damit die Liste beim Anklicken nicht springt. Ein leerer Befehl
  heißt in der Liste „kein Befehl“, wenn der ganze Satz dort nicht passt; unter der Liste steht er ganz.
- **Modi-Tab, Hinweis und Legende:** stehen immer da und brechen um – vorher fielen sie weg, sobald der Tab
  niedriger war als sein Inhalt (427 × 240 und 320 × 240 Punkte). Ist für Liste, Hinweise und das Feld darunter Platz,
  stehen sie zwischen Liste und Feld; sonst unter dem Feld (`ModesTab.hintsBelow`), damit die Bedienelemente des
  Felds zuerst in der Ansicht sind und nur die Hinweise einen Bildlauf brauchen (1080p mit GUI-Skala 3 und Funke:
  Liste und Feld passen ohne Bildlauf, die Hinweise stehen darunter).
- **Tab „Freestyle“:** Der Hilfetext über den Knöpfen hat keine Obergrenze von vier Zeilen mehr; der Tab scrollt. Der
  Zustand (Punkte des Flugs oder „Zählt ab dem nächsten passenden Flug“) steht unter dem Umschalter, wenn daneben
  kein Platz ist (eine Spalte) – vorher fehlte er dort.

## Kleine Fenster und Anzeigen im Spiel

- **Gate-Editor:** Wäre das Fenster höher als der Bildschirm (Deutsch bei 240 Punkten Höhe), ordnet es sich kompakt an
  (`GateEditPanel`): Lagezeile, Tastenhinweise und der Hinweis zur freien Maus stehen einzeilig (gestaucht nach Regel 5),
  und für den Status ist nur so viel Platz reserviert, wie der aktuelle Text braucht. Kompakt ist der Zeilenbereich nur
  so hoch wie die gezeigte Seite: Die Seite „Redstone“ hat drei Zeilen statt fünf, ihr dreizeiliger deutscher Status
  ragte vorher mit dem letzten Hinweis 2 Punkte aus dem Bild (248 statt 236 Punkte). Reicht auch das nicht, schrumpfen
  die Abstände über und unter dem Zeilenbereich um bis zu 6 Punkte. Der Hinweis zum Signal („Signal aus …“ oder der
  Block mit Strom) zählt mit seiner Zeilenzahl zur Höhe der Seite – beim Freiform-Gate war dafür kein Platz reserviert
  und er fehlte ganz.
- **Meldungen über der Schnellleiste:** Minecraft zeichnet sie einzeilig und mittig; was breiter als der Bildschirm
  ist, wird an beiden Enden abgeschnitten (die Absage bei falscher Brille ist 615–716 Punkte breit, die GUI im
  Standardfenster 427). `ui.client.OverlayMessageWrap` (Mixin `HudMixin`) bricht jede solche Meldung, die breiter als
  der Bildschirm minus 12 Punkte ist, mittig um – die letzte Zeile bleibt an ihrem Platz, weitere wachsen nach oben –
  und lässt sie je Zusatzzeile 2 s länger stehen (höchstens 4 s). Kürzere Meldungen zeichnet weiter Minecraft.
- **GPS Rescue:** zwei Spalten nur, wenn der längste Wert jedes Umschalters neben seiner Beschriftung Platz hat (Deutsch
  „Erstes Scharfschalten“: erst ab 199 Punkten Spaltenbreite), sonst eine Spalte.
- **Welt-Regeln ohne Schreibrecht:** Die Zeilen zeigen Beschriftung und Wert als Text. Die Wertspalte beginnt hinter
  der längsten Beschriftung, höchstens so weit rechts, dass der längste Wert noch Platz hat
  (`WorldRulesTab.valueColumn`), und die Beschriftung endet 4 Punkte davor. Vorher endete eine gestauchte Beschriftung
  genau am Wert („Teile müssen zusammenpassenAn“, Deutsch bei 320 × 240 Punkten).
- **Empfänger-Assistent, Schalter:** Passen Funktionsname und Belegung nicht nebeneinander, stehen sie in der Zeile
  untereinander (ab 20 Punkten Zeilenhöhe). Ist unter den Zeilen nur für eine Zeile Platz und die kurze Anweisung
  bräuchte zwei (Deutsch bei 342 × 256 Punkten), steht sie einzeilig gestaucht (Regel 5) statt mit „…“.
- **Empfänger-Assistent, Gerät:** Der Bereich unter den Einstellungen (Gerätedaten, Achsen, Kanäle, Tasten) scrollt
  zwischen den Einstellungen und der Fußzeile (`ScrollArea`: Rad, Leiste, Bild auf/ab, Pos1/Ende). Vorher lag bei 240
  Punkten Höhe die Kanalzeile „Gas“ halb unter der Fußzeile, Achsen 7 und 8 und die Tasten fehlten. Die Achsen stehen
  in so vielen Spalten, wie ihre Beschriftung neben einem Balken von mindestens 16 Punkten Platz hat
  (`InputGui.axisColumns`); keine Achse wird weggelassen.
- **Kurs bearbeiten:** Passt der Hinweis neben der Rundenzahl nicht („gilt für alle Piloten“), steht er in der Zeile
  darunter – vorher fehlte er bei 320 Punkten Breite. Für die Hilfe unter der Liste ist so viel Platz reserviert, wie
  sie Zeilen hat (mindestens drei); vorher lag ihre vierte Zeile auf Englisch bei 320 Punkten Breite 2 Punkte unter
  der Fußzeile. Die Liste zeigt dafür eine Zeile weniger und scrollt: Mausrad, Scrollleiste zum Anfassen, Bild auf/ab
  und Pos1/Ende (`RowScroll`) – ein Kurs mit 14 Gates zeigt bei 320 × 240 Punkten drei Zeilen.
- **Bestenliste:** Der Text der Fußzeile endet vor dem ersten Knopf; bleibt dort weniger als 150 Punkte, steht er über
  den Knöpfen. Die Tabelle scrollt zwischen Spaltenköpfen und Fußzeile (`ScrollArea`: Rad, Leiste, Bild auf/ab,
  Pos1/Ende). Vorher fielen Zeilen stumm weg, sobald die GUI niedriger als etwa 384 Punkte war: bei 240 Punkten Höhe
  standen nur die Plätze 1–8 da, bei 270 (1080p mit Skala 4) fehlten die eigene Zeile und der Abschnitt „Mit
  Flughilfe“. Die Spalten richten sich nach dem längsten Namen: Würde er gekürzt, fallen der Reihe nach Datum, Klasse
  und Abstand weg (bei 320 Punkten Breite war die Namensspalte 8 Punkte breit und jeder Name leer, die Marke
  „Flughilfe“ lag über der Rundenzeit); was fehlt, zeigt der Tooltip der Zeile. Klassenfilter, die nicht in eine
  Zeile passen, laufen in eine zweite; reicht auch die nicht, blättert ein Knopf „+N“ durch die übrigen
  (`ui.ChipFlow`) – vorher fielen sie weg, „Long Range 7″“ war mit allen fünf Klassen bei keiner Fenstergröße wählbar.
- **Lackierung:** Der Hinweis unter der Vorschau („Klick wählt ein Teil, Ziehen dreht“) bricht um
  (`ThemeText.smallWrapped`), wenn er nicht in eine Zeile passt – bei GUI-Skala 2 ist die kleine Schrift so groß wie
  die normale, bei 400 × 300 Punkten (Fenster 800 × 600) endete er mit „…“.
- **Wartungsmenü:** Die kleine Zeile „Props im Bild“ steht auf der Grundlinie der Überschrift „Kamera“
  (`ThemeText.smallBaseline`); bei GUI-Skala 2 ragte sie 1–2 Punkte in die Knöpfe darunter.
- **Aufnahmen, Befehl bearbeiten, Flugschul-Szenen:** Knöpfe teilen sich eine zu schmale Fußzeile, Abstände schrumpfen,
  bevor die Hilfe hinter der Fußzeile verschwindet, und der Bedienhinweis der Szene bricht um.
- **Flugschul-Szenen, Text zum Schritt:** Der Bereich unter dem Bild ist so hoch wie der längste Schritt-Text der Szene
  in der gewählten Sprache und Breite (mindestens zwei Zeilen), das Bild darüber entsprechend niedriger – einmal je
  Szene berechnet, damit das Bild beim Schrittwechsel nicht springt. Vorher standen höchstens zwei Zeilen da: auf
  Deutsch fehlte bei 320 Punkten Breite in 97 von 108 Schritten das Ende. Dem Bild bleiben mindestens 60 Punkte Höhe.
- **Aufnahmen und Elementliste im OSD-Editor:** Die Scrollleiste lässt sich anfassen und ziehen; ein Klick in ihre Bahn
  springt dorthin.
- **Flugtraining, Lektionsliste:** Passt ein Wort eines Lektionstitels nicht neben Sterne oder „Gesperrt“ (Deutsch bei
  320 Punkten Breite: „Scharf / schalt / en“), bekommt der Titel die ganze Zeilenbreite und die Sterne stehen darunter.

- **Werkbank „Mehr“:** so breit wie Titel und längster Eintrag (höchstens Bildschirm minus 8 Punkte); passen die
  Einträge nicht in die Höhe, scrollen sie zwischen Kopfzeile und dem festen „Zurück“-Knopf (Rad, Leiste, Bild auf/ab,
  Pos1/Ende; mit Tab angesprungene Einträge rücken in die Ansicht).
- **Flugtraining-Anzeige** (Lektion, Hinweise, Banner oben im Bild): Zeilen, die breiter als der Bildschirm wären,
  brechen um – in der normalen Ansicht wie im OSD.
- **Meldungen (Toasts):** Textbreite folgt der Bildschirmbreite (`ThemedToast.textWidth`).

## Tooltips

Minecraft schiebt einen Tooltip nur nach links oder nach oben und begrenzt weder seine Breite noch seine Höhe. Bei
240 Punkten Höhe begann deshalb der Tooltip eines Reglers über dem Bildschirm (12–17 Zeilen, die ersten fehlten), und
ein Satz als einzelne Zeile lief rechts hinaus (bis 667 Punkte breit). In allen Propwash-Screens (`ThemedScreen`) gilt:

- **Breite:** `ThemeText.tooltip`, `tooltipLines`, `tooltipWrapped` und `tooltipBelow` brechen jede Zeile um – bei
  `TooltipPlacement.WRAP_WIDTH` = 170 Punkten wie die Tooltips der Regler (`Tooltip.create`), eigene Breiten bleiben,
  und nie breiter als der Bildschirm abzüglich Rand (`TooltipPlacement.wrapWidth`).
- **Lage:** `ScreenTooltipPositioner` lässt Minecraft den Platz wählen und schiebt das Ergebnis in den Bildschirm
  (`TooltipPlacement.clamp`, Rahmen 4 Punkte plus 1 Punkt Luft). Er gilt für jeden Tooltip eines Propwash-Screens –
  auch für die der Regler, für Item-Tooltips in Werkbank, Ladegerät und Wartungsmenü und für Tabs aus Add-ons
  (`GuiGraphicsExtractorMixin`, nur solange ein `ThemedScreen` offen ist; Vanilla-Screens bleiben unverändert).
- **Höhe:** Passt ein Tooltip auch dann nicht (Details der Werkbank mit 34 Zeilen, deutsche Tooltips der Welt-Regeln
  mit 24–25 Zeilen bei 240 Punkten Höhe), zeigt `TooltipScroll` so viele Zeilen, wie passen, und darunter „Mausrad:
  Zeilen 1–22 von 34“. Das Mausrad scrollt dann den Tooltip, solange er sich noch bewegt; an seinem Ende geht das Rad
  wie bei inneren Listen (Regel 6) an den Bereich darunter. Wechselt der Tooltip oder verschwindet er, beginnt er
  wieder oben.

`TooltipPlacement` ist reines Java (JUnit `TooltipPlacementTest`). Add-ons brauchen nichts zu tun; wer selbst einen
Tooltip setzt, nimmt `ThemeText.tooltip…` statt `setTooltipForNextFrame`, damit lange Zeilen umbrechen.

## Frei gezeichnete Bereiche

`ui.client.widget.ScrollArea` macht einen Bereich ohne Widgets scrollbar: `view(…)`, dann `begin(g)` – zeichnen in
Inhalts-Koordinaten – `end(g, untereKante)`. Maus über `hoverY`/`contentY`, Rad über `wheel`, Tasten über `key`,
Scrollleiste über `mouseClicked`/`mouseDragged`/`mouseReleased`. So arbeiten die drei Spalten des Flugtrainings.

Bild-im-Bild (`graphics.entity(…)`) kennt die `pose` nicht; `DronePreview` rechnet sein Rechteck deshalb selbst um.
Wer eigenes Bild-im-Bild in einem verschobenen Bereich zeichnet, macht es genauso.

## Selbsttest `ui-scale:`

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest -PpropwashScreenshots=true \
    '-PpropwashSelftestOnly=ui-scale:'
```

Andere Größen einmalig prüfen: `-PpropwashUiScaleWindows=770x480,684x512,640x1000@2` ersetzt die festen Fenster
(Bildpunkte, Skala ohne Angabe 2) – so lassen sich Zwischengrößen wie 385 × 240, 342 × 256 oder 320 × 500 Punkte
nachmessen, ohne den Selbsttest zu ändern. `'-PpropwashUiScaleCases=Konfigurator im,Bestenliste'` prüft dabei nur
die Ansichten, deren Name so beginnt (zwanzig Fenster in beiden Sprachen mit den vier Fällen „Konfigurator im …“:
etwa 2 Minuten); passt kein Name, scheitert der Schritt. Ein Release läuft ohne diese Auswahl.

`UiScaleSelfTests` stellt das Fenster nacheinander auf 854 × 480, 1920 × 1080, 640 × 480 und 800 × 600 Bildpunkte
(letzteres ergibt bei jeder Skala 400 × 300 Punkte) und die GUI-Skala auf 2, 3, 4 und automatisch, danach auf
768 × 480 und 912 × 512 Bildpunkte mit Skala 2 (384 × 240 und 456 × 256 Punkte – so groß ist die Oberfläche bei
1920 × 1200 und 1366 × 768 mit automatischer Skala; zwischen den übrigen Größen lagen die Befunde der sechsten
Gegenprüfung). In diesen beiden Zwischengrößen laufen nur die Fälle, deren Kopfzeile und Tabs am stärksten von der
Breite abhängen (`Case.between`: der Konfigurator mit Funke in allen Tabs, mit Drohne und langer Funke und die vier
Fälle der Pilotensitzung, zusammen 38 Ansichten) – alle 202 Ansichten kosteten dort gut fünf Minuten mehr, und der
volle Lauf hat eine Zeitgrenze (`docs/testing.md`); mit `-PpropwashUiScaleWindows` laufen immer alle. Alles läuft
erst auf Englisch, dann in denselben Größen auf Deutsch (Schritte
`ui-scale: Deutsch: Fenster …`; die Sprache wird nur umgestellt, wenn einer dieser Schritte ausgewählt ist, und
danach zurückgestellt). Deutsche Texte sind im Mittel länger; vor der 0.5.0 lief die Prüfung nur auf Englisch und
übersah 46 Befunde bei 320 × 240 und 16 bei 427 × 240. Ergibt eine Kombination dieselbe Größe in GUI-Punkten wie eine frühere, wird sie
nicht noch einmal geprüft (im Protokoll steht, wo). Geprüft werden alle Konfigurator-Tabs (ohne und mit Drohne in der
Hand, mit einer simulierten Funke „OpenTX Radiomaster Boxer Joystick“ und mit Maus-Steuerung) und die übrigen Screens
(`UiScaleCases`): Flugschule mit allen Szenen, Flugtraining, Aufnahmen (leer und mit
Liste), Beitritts-Prüfung, Bestenliste (leer und gefüllt: 14 Zeiten, eigener Platz 12, drei Runden mit Flughilfe,
acht Klassen, ein Name aus 16 „W“), Kurs bearbeiten (Kurs mit vier und mit 14 Gates), Geräte-Einrichtung, Befehl bearbeiten, Empfänger-Assistent (alle
Seiten), Maus-Einrichtung, Werkbank, Werkbank „Mehr“ mit 14 Einträgen, Lackierung, Ladegerät, Wartungsmenü und der
Gate-Editor mit freier Maus und als Anzeige ohne freie Maus, jeweils beide Seiten. Dazu kommen Ansichten, die nicht
der Zustand nach dem Öffnen sind: im Modi-Tab die Zeilen „3D-Modus“ (längste Beschreibung) und „Befehl 1“ (für das
Bild ans Ende gescrollt, `Case.frame`), mit Funke die Zeilen „Scharf schalten“, „Angle“, „Beeper“, „3D-Modus“,
„Blackbox“, „Befehl 1“ und „Beeper“ sowie der Zustand „Schalter jetzt umlegen…“, je
Flugschul-Szene der Schritt mit dem längsten Text, und in der Aufnahmen-Liste zieht die Prüfung die Scrollleiste ans
Ende und zurück. Ebenso zieht sie die Leisten der gefüllten Bestenliste, der Gate-Liste und der Tab-Leiste des
Konfigurators und drückt dort Bild ab, Ende und Pos1; in der Bestenliste blättert sie durch alle Klassenfilter. Dazu
kommen vier kurze Konfigurator-Fälle: Drohne in der Hand mit der Funke „Team-BlackSheep TBS Tango 2 Pro Joystick“
(Tabs „Empfänger“ und „Übersicht“), Drohne mit Maus-Steuerung (Tab „Empfänger“), die Seite „Optionen“ des OSD-Tabs
und der Tab „Welt-Regeln“ ohne Schreibrecht. Drei weitere öffnen den Konfigurator mit Tastatur und stecken die Funke
erst danach an (ohne Drohne und mit Drohne die lange Funke, mit Drohne die „Radiomaster Boxer“; Tabs „Empfänger“,
„Übersicht“ und „OSD“, geprüft 20 Ticks nach dem Anstecken) – die Kopfzeile muss sich dann neu aufteilen. Der Fall
„Empfänger-Assistent Rückfragen“ löscht den Arm-Schalter und drückt „Fertig“ (Rückfrage statt der Schalterzeilen) und
übernimmt eine Kalibrierung, bei der kein Knüppel bewegt wurde (längste Meldung über der Fußzeile). „Bestenliste leer“
zeigt die vier leeren Zustände mit eingespielten Daten: Kurs gibt es nicht mehr, noch keine Zeit, neues Layout mit
archivierten Zeiten (der Befehl muss in einer Zeile stehen) und nur Runden mit Flughilfe. „Konfigurator Action-Cam
ffmpeg“ spielt dem Tab acht Zustände der ffmpeg-Prüfung ein (`FfmpegStates`: fehlt, unbrauchbar, keine Hardware,
bereit mit vier Encodern, defekt, eigener Pfad fehlt oder nicht ausführbar, prüft – mit langen Pfaden und
Fehlertexten). Vier Fälle öffnen den Konfigurator in einer Pilotensitzung (`UiScaleLiveCases`: Drohne neben dem
Spieler, Brille und Funke gebunden, Start wie im Spiel): im FPV-Flug mit digitaler Brille und Tastatur (Tabs
„Übersicht“, „Empfänger“, „Video“), im FPV-Flug mit der „Radiomaster Boxer“ und im Sichtflug mit der langen Funke
(„Empfänger“, „Übersicht“) sowie im FPV-Flug mit analoger Brille (Tab „Video“). Dort stehen die Plaketten „LIVE“ und
„ARMEN GESPERRT“, die Live-Telemetrie mit Werten und die Vorschau „So zerfällt das Bild“ – vorher öffnete die
Prüfung den Konfigurator nie in einer Sitzung und nie mit Brille. Weil ein weggelassener Text kein Rechteck hat, das
die Prüfung messen könnte, verlangen diese Fälle ihn ausdrücklich: beide Plaketten in jedem Tab, die fünf
Telemetrie-Zeilen in der Übersicht, im Video-Tab die letzte Beschriftung am Stück und „KEIN VIDEO“ ungekürzt; fehlt
einer, ist das ein Befund `HIDDEN`. Ohne eigenen Screen geprüft werden die Meldungen (drei lange Toasts) und die
Flugtraining-Anzeige (die drei Lektionen mit den längsten Titeln): Der Fall zeichnet nur sie und prüft die Texte gegen
den Bildschirmrand.

Je Ansicht zeichnet `LayoutProbe` den Screen in einen eigenen Zustand und liest daraus jeden Text mit Rechteck und
Ausschnitt sowie alle sichtbaren Widgets. `ui.LayoutAudit` (reines Java, JUnit `LayoutAuditTest`) meldet:

| Befund | Bedeutung |
|---|---|
| `OFFSCREEN` | Text oder Widget ragt aus dem Bildschirm |
| `CLIPPED` / `HIDDEN` | Text ist vom Ausschnitt angeschnitten oder ganz verdeckt und lässt sich nicht in die Ansicht scrollen |
| `MARQUEE` | einzeiliger Text läuft als Laufschrift, weil er nicht in sein Feld passt |
| `TEXT_OVERLAP`, `TEXT_ON_WIDGET`, `WIDGET_OVERLAP` | Texte oder Widgets liegen übereinander |
| `CUT` | ein Text wurde mit „…“ gekürzt oder es fehlen Zeilen („2 von 3 Zeilen“, `ThemeText.shownLines`) (`ThemeStats.trackCuts`); auch die Stellen, die selbst kürzen, melden sich dort (Knöpfe der Werkbank, Mängelliste, Empfänger-Zustand und mitten im Wort gekürzte Namen im Wartungsmenü, Hilfetext im Tab „Rennen“, Grund in der Beitritts-Prüfung); Namen, die das Wartungsmenü an einer Wortgrenze kürzt („6S 1300 mAh“ statt „6S 1300 mAh Battery“), zählen nicht |
| `COVERED` | ein Text liegt unter einer später gezeichneten deckenden Fläche (Fußzeile über dem Inhalt) |
| `TOOLTIP` | ein Tooltip ragt mit Text oder Rahmen aus dem Bildschirm, ist breiter oder höher als der Bildschirm oder lässt sich als hoher Tooltip nicht bis ans Ende scrollen |
| `SCROLLBAR` | eine gezeichnete Scrollleiste lässt sich nicht ziehen (oder der Klick auf ihren Griff öffnet etwas anderes) |

`COVERED`: `LayoutProbe` merkt sich zu jeder deckenden Fläche (`fill` mit Alpha ab 240), wie viele Texte vor ihr
gezeichnet wurden. Liegt sie mindestens 4 × 2 Punkte über einem dieser Texte und wird er danach nicht noch einmal
gezeichnet, ist er verdeckt. Flächen unter 8 Punkten Breite oder Höhe zählen nicht (Scrollleiste, Cursor, der 4 Punkte
hohe Rand am Anfang und Ende eines scrollenden Bereichs). Bis zur 0.5.0 sah die Prüfung solche Texte als vorhanden an – so blieb die halb verdeckte
Kanalzeile im Empfänger-Assistenten unbemerkt.

Angeschnittener Text ist nur erlaubt, solange es in diese Richtung weitergeht: Die Prüfung scrollt jeden Bereich mit
dem Mausrad (hilft das nicht, mit Bild ab) bis ans Ende. Oben darf am Anfang nichts fehlen, unten am Ende nichts.
Stehen im Bild weitere Ausschnitte mit Text, in denen gerade nichts angeschnitten ist (`LayoutAudit.clipRegions`) –
die Kante fällt dort genau zwischen zwei Zeilen –, blättert die Prüfung zusätzlich mit Bild ab durch die Ansicht,
solange sich in einem dieser Ausschnitte etwas bewegt, prüft jede Stellung wie die erste Ansicht und blättert zurück.
Bis zur 0.5.0 scrollte sie nur Bereiche mit angeschnittenem Text; bei 240 Punkten Höhe blieben so der untere Teil
von GPS Rescue, die Flugschul-Liste und der Audio-Tab ungeprüft. Für diesen Durchgang nimmt sie die Tasten und nicht
das Mausrad: Das Rad träfe Regler und Auswahlknöpfe, die beim Blättern unter den Mauszeiger rücken, und verstellte
deren Werte.
Glyphen-Rechtecke stehen bis zu 2 Punkte über die Laufweite hinaus; so viel Überstand zählt nicht. Buchstaben mit
Akzent (Ä, Ö, Ü, É) haben 12 Punkte hohe Glyphen, die 3 Punkte über die Zeile ragen; `LayoutProbe` rechnet jedes
Textrechteck deshalb auf die Zeilenzelle (9 Punkte ab der Zeichenposition) zurück, sonst meldete jede deutsche Ansicht
Dutzende Überlappungen, die es nicht gibt.

`TOOLTIP`: Bis zur siebten Gegenprüfung parkte die Prüfung die Maus vor jeder Ansicht und sah nie einen Tooltip. Jetzt
fährt `LayoutProbe.tooltips` jede Ansicht ab, deren Oberfläche höchstens 480 Punkte breit oder 270 Punkte hoch ist
(320 × 240, 384 × 240, 400 × 300, 427 × 240, 456 × 256 und 480 × 270 Punkte – größere Oberflächen zeigen dieselben
Tooltips mit mehr Platz): die Mitte jedes Widgets mit Tooltip, die Mitte jedes gezeichneten Textes und jeden Slot
eines Container-Screens, danach mit Bild ab jede weitere Seite der Ansicht. Den Tooltip misst sie an der Stelle, an
der er gezeichnet wird (`ThemeStats.tooltip` in `TooltipRenderUtilMixin`), mit 4 Punkten Rahmen. Einen hohen Tooltip
scrollt sie mit dem Mausrad bis ans Ende und zurück und prüft jede Stellung. Das kostet je Größe und Sprache etwa
6–7 Sekunden für rund 2000 verschiedene Tooltips in 202 Ansichten.

`SCROLLBAR`: In jeder Ansicht sucht `LayoutProbe.scrollbars` die gezeichneten Leisten (3 Punkte breite Fläche in
`Palette.FIELD` mit einem Griff in `Palette.BORDER_STRONG` darin), fasst den Griff, zieht ihn ans untere und ans obere
Ende und zurück und verlangt, dass er sich dabei bewegt. So fiel die Liste der Flugschule auf, deren Leiste nur
gezeichnet war.

Befunde stehen in `propwash-ui-scale.txt` im Run-Verzeichnis; mit `-PpropwashScreenshots=true` entsteht zu jeder
Ansicht mit Befund ein Bild unter `docs/screenshots/ui-scale/`, dazu die schwierigsten Ansichten (`UiScaleCases.SHOWCASE`)
bei 320 × 240 (Fenster 640 × 480) und 480 × 270 Punkten (1080p mit Skala 4) auch ohne Befund, die Ansichten der
Pilotensitzung zusätzlich bei 384 × 240, 427 × 240 und 456 × 256 Punkten. Vor jedem Bild parkt die
Prüfung die Maus in der Ecke, damit kein Tooltip im Bild steht. Acht Ansichten bekommen bei 320 × 240 und 427 × 240
Punkten in beiden Sprachen zusätzlich ein Bild `…_tooltip.png` mit der Maus auf ihrem größten Tooltip
(`UiScaleCases.TOOLTIP_SHOWCASE`: Modi mit Funke, Konfiguration, Action-Cam, OSD, Welt-Regeln, Werkbank, Lackierung,
Ladegerät). Zum Vergleich liegt unter `vorher/` der Zwischenstand
von Modi- und PID-Tab bei 320 × 240 vor dem schmalen Layout.

## Für eigene Tabs (Add-ons)

Ein Tab aus einem Add-on scrollt ohne Zutun, sobald seine Widgets nicht in die Ansicht passen. Zeichnet er Text unter
das letzte Widget, meldet er dessen untere Kante mit `reportBottom(y)`; schneidet er selbst zu (`enableScissor`),
reicht der Ausschnitt bis zu dieser Kante statt bis `y + height`.

Tooltips der Widgets eines Add-on-Tabs (`setTooltip(Tooltip.create(…))`) schiebt Propwash in den Bildschirm und lässt
sie scrollen, wenn sie zu hoch sind (Abschnitt *Tooltips*). Eine Zeile, die länger sein kann als der Tab breit ist,
bricht der Tab selbst um (`font.split`), statt sie mit `ThemeText.line(…, scroll = true)` als Laufschrift zu zeigen –
die Prüfung zählt Laufschrift als Befund.
