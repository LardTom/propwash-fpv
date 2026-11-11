# Rennsystem 0.6.0, Teil A: Rennstart, Wertung, Flächen und Verschieben

Für Spieler. Mit 0.6.0 startet die Rückkehr zum Homepoint kein Rennen mehr, Flaggen und Slalomstangen werten auf
Wunsch weiter nach unten und oben, Gate-Stangen lassen sich in Kurse nehmen, Freiform-Gates dürfen Lücken im Rand
haben, die Wertungsflächen sind zu sehen, und im Menü am Gate lässt sich alles in kleinen Schritten verschieben.

Technik und Entscheidungen: [`design/0.6.0/race-system.md`](design/0.6.0/race-system.md) (Teil A und B).

## 1. Rennstart: nur, wenn du am Start-Gate scharf schaltest

- **Reset** (Rücktaste bzw. Schalter „Reset“) und **GPS-Rescue** bringen die Drohne zum Homepoint, starten aber nie einen
  Countdown – auch nicht, wenn der Homepoint direkt vor dem Start-Gate liegt und die Drohne beim Reset noch scharf war.
- Ein **stehender Start** (3 s Countdown mit Startampel) beginnt wie in 0.5.2 nur, wenn du nahe am Start-Gate (bis 24
  Blöcke) **selbst scharf schaltest** und im Tab „Rennen“ „Start beim Scharfschalten“ an ist – oder wenn dort gerade ein
  anderer Pilot eine Startaufstellung offen hat.
- Ohne „Start beim Scharfschalten“ fliegst du wie bisher einfach durch das Start-Gate (fliegender Start).
- Reset während eines Laufs bricht ihn ab („zurück zum Homepoint“), wie bisher.

## 2. Wertung nach unten und oben (Flagge, Slalomstange)

- **Schleichen + Rechtsklick** mit Kurs-Werkzeug oder dem Element in der Hand öffnet das Menü, der Knopf „Wertung »“ (oder
  Schleichen + Mausrad) führt auf die Seite **Wertung**.
- **Nach unten:** so viele Blöcke unter dem Fuß zählt das Vorbeifliegen bzw. Umrunden noch (0 bis 32, Standard 1 wie
  bisher). Damit zählt eine hohe Flagge auf einer Säule, auf einem Gate oder auf Blöcken bis hinunter zum Boden.
- **Nach oben:** so viele Blöcke über der Spitze (0 bis 32, Standard 0 – über die Flagge hinweg zählt dann nicht).
- Die Breite der Wertung („Wertung“ auf der ersten Seite) bleibt wie bisher.

## 3. Gate-Stangen im Kurs

- Klickst du eine **stehende, hängende oder aufgesetzte** Gate-Stange mit dem Kurs-Werkzeug an, kommt sie in den Kurs und
  wertet wie eine **Slalomstange** („vorbeifliegen“). Folgt sie auf eine Slalomstange oder eine andere Vorbeiflug-Stange,
  nimmt sie automatisch die andere Seite (Slalom).
- Auf der Menüseite **Wertung** stellst du ein: **Wertung** (keine / vorbeifliegen / umrunden wie eine Wendeflagge),
  **Seite**, **Reichweite** (wie weit neben der Stange es zählt), **nach unten**, **nach oben**. Im Kurs geht „keine“ erst
  nach „Aus dem Kurs“.
- Gate-Stangen an der Wand werten nicht und kommen nicht in Kurse. Eine Gate-Stange kann nicht der Start sein.
- Im Kurs zeigt die Stange Nummer, Bodenpfeile und Kreuz wie eine Slalomstange bzw. Flagge; ihre LED-Farbe bleibt.

## 4. Freiform-Gate: Lücken ignorieren

- Ist eine Öffnung nicht ringsum geschlossen (z. B. zwei Pfosten auf dem Boden ohne oberen Balken), sagt die Vorschau
  „nicht ringsum geschlossen“ und darunter **„Schleichen + Rechtsklick: Lücken ignorieren“**. Hältst du Schleichen, zeigt
  die Vorschau bernsteinfarben die Fläche, die dann entsteht.
- Die Fläche ist das Rechteck, das die vorhandenen Blöcke aufspannen: zwischen den Pfosten bis zur Höhe der Pfosten, unter
  einem Bogen bis zu den Pfostenenden, bei einer L-Form bis zum Ende von Boden und Pfosten. Blöcke darin bleiben Rand.
  Ein einzelner Pfosten ergibt kein Rechteck.
- Im Menü des Freiform-Gates schaltet die Zeile **Rand** zwischen „Lücken melden“ und „Lücken ignorieren“. Mit
  „ignorieren“ leuchten offene Stellen nicht mehr rot und es gibt kein Warndreieck dafür; zugebaute Felder meldet es
  weiter. „Neu abtasten“ folgt der Einstellung.

## 5. Sichtbare Wertungs- und Durchflugflächen

- Das **aktive Gate** – im Rennen das nächste Gate, vor dem Start das Start-Gate – zeigt seine Öffnung als hauchdünne,
  leuchtende Fläche wie ein Freiform-Gate. Beim Gate auf Beinen reicht sie bis zum Boden.
- **Flaggen, Slalomstangen und wertende Gate-Stangen** zeigen ihre Wertungsflächen mit feinen Kanten: das nächste Element
  im Rennen deutlich, Elemente in deiner Nähe dezent, beim Bauen (Kurs-Werkzeug, Gate, Element oder Gate-Stange in der
  Hand) alle bis 32 Blöcke. Im Menü siehst du die geänderte Fläche sofort.
- Die Flächen sind im FPV und in der dritten Person zu sehen, leuchten auch nachts und mit Shaderpacks und werfen keinen
  Schatten. Mit F1 sind sie aus. Beim Zuschauen (F7) zeigt nur das aktive Gate des beobachteten Piloten seine Fläche
  (`docs/spectate-feed.md`), die übrigen bleiben aus.
- Abschalten: Tab „Rennen“ → **Wertungsflächen**.

## 6. Verschieben in kleinen Schritten

- Das Gate-Menü zeigt seine Seiten als **Tabs** in der ersten Zeile: **Gate** (bei Kurs-Elementen ihr Name),
  **Redstone** bzw. **Wertung** und **Position**. Mit freier Maus (Alt links halten) klickst du einen Tab an, ohne freie
  Maus wechselt **Tab** zur nächsten und **Umschalt + Tab** zur vorigen Seite; Schleichen + Mausrad läuft weiter über
  alle Zeilen aller Seiten.
- Tab **Position** (Gates, Flaggen, Slalomstangen, Tauchwürfel, Gate-Stangen; nicht Freiform-Gates):
  **Schritt** 1/16, 1/4 oder 1 Block (wird gemerkt), dazu **X (Ost)**, **Y (oben)**, **Z (Süd)** als Versatz gegenüber
  der Stellung in der Welt, höchstens 8 Blöcke je Achse auf einmal. Die Vorschau zeigt das Gate an der neuen Stelle.
  Unter den Zeilen steht kurz, wie es geht: die drei Schrittweiten, dass die Pfeiltasten deinem Blick folgen und dass
  du im ersten Tab oder mit **R** drehst.
- **Tastatur** (ohne freie Maus): Pfeiltasten verschieben waagerecht relativ zu deinem Blick (↑ von dir weg, ← nach
  links), **Bild↑/Bild↓** nach oben und unten – jeweils um den Schritt, mit **Strg** um 1/16 Block (ein Pixel), mit
  **Strg + Umschalt** um 1/32 Block (ein halber Pixel). Mausrad auf einer Zeile verschiebt genauso, mit freier Maus
  (Alt links halten) gibt es −/+ und die Regler. Die Grenze bleibt 8 Blöcke je Achse.
- **Tooltips ohne freie Maus:** Neben dem Menü erscheint für die gewählte Zeile ihr Tooltip, ein paar Sekunden nach
  jedem Zeilenwechsel (Schleichen + Mausrad, Tab). Er nennt auch den Strg-Schritt der Zeile: 1° bei Winkeln, 1 Tick beim
  Puls, 1/16 bzw. 1/32 Block beim Verschieben. Die Fußzeile nennt ihn ebenfalls, auf der Seite Position in einer eigenen
  Zeile unter „Mausrad, Pfeile, Bild↑/↓: schieben“. Steht unten links noch der Start-Hinweis zur freien Maus, rückt der
  Tooltip über ihn; ist dafür kein Platz, wartet der Hinweis, bis der Tooltip verschwindet.
- **Enter** oder Rechtsklick übernimmt, Esc verwirft. Gates in einem Stapel und Gates mit Gate-Stangen darauf bleiben
  gesperrt (erst die oberen abnehmen); Gates im Stapel lassen sich aber drehen ([`race-topology.md`](race-topology.md) §8).
- Verschieben eines Gates im Kurs ist eine Änderung des Kurses: die Bestenliste wird wie bei jeder Änderung archiviert.
- Die Flugschule zeigt es im Kapitel „Gate-Menü“, Schritt **Tab Position**.

## 6a. Gate-Stange und liegendes Freiform-Gate

- Der Fuß der **Gate-Stange** besteht aus vier gleichen Armen; er sieht bei 0°, 90°, 180° und 270° gleich aus.
- **Liegendes Freiform-Gate** (Schacht, Grube): Die Zeile **Ausrichtung** im Menü dreht es in Schritten von 90°, der
  Redstone-Ausgang dreht mit – 0° Nordrand, 90° Ostrand, 180° Südrand, 270° Westrand, jeweils mittig. Beim Setzen liegt
  der Ausgang auf deiner Seite (wie der Balken eines liegenden Gates). Ältere liegende Freiform-Gates stehen auf 0° und
  geben ihr Signal wie bisher an der Nordkante. Senkrechte Freiform-Gates bleiben beim Randblock unter der Öffnung.
- Im Kurs ändert die **Ausrichtung** nur den Ausgang, nicht den Weg durchs Gate: Bestenliste, Rekord-Geister und Layout
  bleiben wie bei den Einstellungen auf der Seite Redstone. **Flugrichtung** umkehren und **Neu einlesen** bleiben
  Kursänderungen (die Bestenliste wird archiviert).
- Die **Fußzeile** des Freiform-Menüs sagt, was Mausrad und **R** auf der gewählten Zeile tun: Flugrichtung „Mausrad oder
  R: Flugrichtung umkehren“, Ausrichtung „Mausrad: um 90° drehen“, Rand „Mausrad: umschalten“ (dahinter jeweils „R:
  Flugrichtung umkehren“), auf der Seite Redstone „Mausrad: Wert ändern · Strg: 1 Tick“. Schleichen + Mausrad wählt die
  Zeile.

## 7. Alte Welten, Kurse und Bestenlisten

- Kurse aus 0.4.0, 0.5.0 und 0.5.2 laden unverändert; ihre Bestenlisten, Rekord-Geister und persönlichen Geister bleiben
  gültig. Neue Einstellungen werden nur gespeichert, wenn sie vom Standard abweichen.
- Die Kursdatei `data/propwash_race.json` trägt ab 0.6.0 die Formatversion 2.
- Server und Client brauchen beide 0.6.0.

## 8. Englische Kernsätze (Website)

- Returning to your home point (reset or GPS rescue) never starts a race; a standing start only begins when you arm at the
  start gate with “Start when arming” on, just like in 0.5.2.
- Flags and slalom poles can score further down and up – a tall flag on a pillar or on a gate counts all the way down.
- Gate poles can join courses as slalom poles or turn markers.
- Freeform gates can ignore gaps in their edge: sneak + right-click places them with the rectangle spanned by the blocks.
- The active gate shows its fly-through area, flags and poles show their scoring areas – subtle, also with shader packs.
- Ctrl nudges a gate by 1/16 block, Ctrl + Shift by 1/32 block; without a free mouse the menu shows the tooltip of the
  selected row.
- A lying freeform gate can be turned in 90° steps, its redstone output turns with it; in a course this keeps the
  leaderboard, since only the output moves.
- The gate menu shows its pages as tabs; the new Position tab moves gates in steps of 1/16, 1/4 or 1 block along all
  three axes, also with the arrow keys and Page Up/Down, and Tab switches between the tabs.
