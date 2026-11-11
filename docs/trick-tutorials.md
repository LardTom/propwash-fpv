# Trick-Tutorials im Tab Freestyle

Jeder der 59 Tricks im Tab „Freestyle“ (`trick.client.TrickConfigTab`) hat ein Tutorial als Video. Minecraft spielt kein
Videoformat ab; das Video ist eine Szene der Flugschul-Engine (`guide.scene`), erzeugt aus einer Choreografie je Trick.

## Für Spieler

- **Hovern** über einen Trick zeigt nach kurzer Zeit (0,35 s) die Szene klein neben dem Tooltip, in Schleife und ohne
  Text. Die Vorschau überdeckt weder den Tooltip noch die Zeile des Tricks.
- **Klick** öffnet den großen Player (`guide.client.screen.SceneScreen`) mit den Schritten des Tricks (etwa Anflug,
  Einleitung, Drehung, Ausleitung), Erklärtext je Schritt, Pause (Leertaste), Schritt vor/zurück (Pfeiltasten),
  Neustart (Pos1), Zeitlupe 0,5× (Knopf „1×/0,5×“ oder Taste S) und Ansicht drehen und zoomen wie in der Flugschule.
  Zurück (Esc oder ×) führt in den Tab Freestyle, an dieselbe Stelle der Liste; Trick-Erkennung, Suche und Familie
  scrollen mit der Liste und liegen nie über den Trick-Zeilen.
- **Stick-Cam**: Nahaufnahme der Funke als Bild-im-Bild unten rechts in der Bühne. Die Knüppel laufen synchron mit dem
  Trick, im Stick-Modus des Spielers (Mode 1 bis 4, aus der Eingabe-Einstellung), mit einer kurzen Spur der
  Knüppelspitzen. Knopf mit dem Knüppel-Symbol oder Taste C blendet sie ein und aus.
- Kurze Phasen (unter 1 s) laufen in der Szene in Zeitlupe (höchstens 2,5-fach), Anflug und Ausleitung in Echtzeit.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `trick.tutorial.TrickMove` | eine Phase: `Level`, `Fly` (Neigung, Schräglage, Kurve, Gas), `Rotate` (Drehraten Roll/Nick/Gier, Gas, Winkel), `Hold` (Drehraten, Gas, Dauer), `InvertedHold`, `InvertedHover` (3D), `Maverick`, `Orbit`, `Obstacle` (Block-Kasten relativ zur Drohne); jede Phase trägt den Namen ihres Schritts; `mirrored()` seitenverkehrt, `backwards()` Nick umgekehrt |
| `trick.tutorial.TrickChoreography` | Trick, Phasen, 3D-Modus, Teile einer Linie (Sbang, Shinobi, Immelmatt), gemeinsame Texte je Schritt, `sharedWith` (Grundtrick, dessen Choreografie eine Variante teilt); Schlüssel `propwash.trick.tutorial.stage.<schritt>` (Titel), `propwash.trick.tutorial.<trick>.<schritt>` bzw. `propwash.trick.tutorial.text.<id>` (Text) |
| `trick.tutorial.TrickChoreographies` | `of(TrickType)` für alle Tricks des Repertoires. Varianten teilen die Choreografie ihres Grundtricks (Doppel- und Dreifachflip und -rolle, Doppel-Yaw-Spin, Anti Matty = Matty Flip rückwärts, Rückwärts-Sturzflug = Sturzflug rückwärts, Wall Ride = Knife Edge mit Wand, Reverse Knife Edge = Knife Edge mit Wende davor, Power Loop 3D = Power Loop mit Umkehrschub); eigene Bahnen nur für Sonderfälle wie Power Loop, Split-S, Maverick Loop, Orbit, Gap und Proximity |
| `trick.tutorial.TrickFlight` | fliegt eine Choreografie in der echten Flugsimulation (`sim.DroneSim`, 5″ Freestyle, 120 Bilder/s, ohne Rauschen) und zeichnet je Bild Zeit, Schritt, Position, Lage und Knüppel (Roll, Nick, Gier, Gas) samt Motor-Gas auf; ein optionaler Empfänger bekommt jedes `TrickSample` (für die Erkennung im Test); Hindernisse liefern die vier Abstände wie im Spiel |
| `trick.tutorial.TrickTutorial` | baut aus dem Flug die Szene `trick_<trick>`: ab 1,2 s vor dem ersten Trick-Schritt, Maßstab so, dass der Weg etwa 4,5 Blöcke misst (0,06 bis 0,25), Drohne 3,5-fach vergrößert, Flugbahn als gepunktete Spur (`path_<n>`, ein Punkt je 0,1 s), Grasboden unter dem Weg, Hindernisse aus Steinziegeln mit mindestens 1 Block Abstand zur Drohne, Kamera seitlich von der freien Seite, bei Lücken mit Blöcken auf beiden Seiten von hinten; je Tick Bewegung, Lage (`sceneRotation`: die Lage der Simulation – Nase +Z – um 180° um die Hochachse der Drohne gedreht wie beim Zeichnen im Flug in `PilotSession`, weil das Modell mit der Kamera nach −Z zeigt; daraus stetige Euler-Winkel), Knüppel (Mode-2-Belegung) und Gas der Drohne; so fliegt die Drohne mit der Kamera voraus, die Antennen zeigen nach hinten |
| `trick.client.TrickTutorials` | erzeugt Tutorial und `SceneFraming` im Hintergrund (`Util.backgroundExecutor`) und hält sie; `open(trick, parent)` öffnet den Player mit `SceneScreen.Options` (Titel = Trickname, Kopfzeile „Trick-Tutorial“, Schritt-Texte, Stick-Cam an der Drohne) |
| `trick.client.TrickPreview` | Hover-Vorschau: Verzögerung, Platz neben dem Tooltip (Position wie der Tooltip über `DefaultTooltipPositioner`), Schleife mit kurzer Pause, Zeichnen über `SceneView` und `SceneRenderState`; `TrickConfigTab#reveal(trick)` scrollt eine Zeile ins Bild, `#pointAt(zeile)` zeigt Hover und Vorschau dort ohne echte Maus (Selbsttest, Bildschirmfotos) |
| `guide.client.screen.SceneScreen.Options`, `StickCamView` | Player-Optionen für Titel, Kopfzeile, Schritt-Texte und Stick-Cam; Zeitlupe 0,5× gilt für alle Szenen; Stick-Cam zeichnet die Funke mit `RemoteDemoRenderState` und dem Ausschnitt `RemoteProjection.Region.STICKS` (fast von vorn, damit die Knüppelspitze dorthin zeigt, wohin der Knüppel gedrückt ist) |

Kein neues Netzpaket, keine neue Einstellung, keine Änderung an der Trick-Erkennung oder den exportierten
`guide.scene`-Typen.

## Prüfung

- JUnit `trick.tutorial.TrickChoreographyTest`: jede Choreografie läuft durch die Trick-Erkennung (`TrickSession`) und muss
  genau ihren Trick ergeben (Linien: ihre Teile und dann die Linie), ohne Absturz; dasselbe seitenverkehrt; Varianten teilen
  die Choreografie ihres Grundtricks; der Flug zeichnet Knüppel, Lage und Schritte auf.
- JUnit `trick.tutorial.TrickTutorialTest`: jedes Tutorial hat einen Schritt je Phase und alle Texte englisch und deutsch
  (Schritt-Text höchstens 190 Zeichen, kein ungenutzter Schlüssel), die Lage der Szene entspricht in allen 59 Tricks der
  Lage derselben Drohne im Flug (Lage der Simulation mit 180° um die Hochachse), in jedem Vorwärtsflug (Anflug, Durchflug)
  zeigt die Kamera in Flugrichtung (33 Tricks), Nick nach vorn senkt beim Flip die Kamera, Nick nach hinten hebt sie beim
  Power Loop, Roll nach rechts senkt bei der Rolle die rechte Seite, Gier nach rechts dreht beim Yaw-Spin nach rechts, die
  Flip-Szene dreht die Drohne und bewegt den Nick-Knüppel, Hindernisse berühren die Drohne nicht.
- Selbsttest `tricks:` (`trick.client.TrickTutorialSelfTests`): alle Tutorials mit Texten, Vorschau beim Hovern und Klick
  in den Player, Player mit Schritten, Pause, 0,5× und Stick-Cam, Stick-Cam in Mode 2 und Mode 1. Dazu
  `trick.client.TrickListLayoutTour`: Liste bis zum letzten Trick gescrollt, Klick, Player einmal mit Zurück und einmal mit
  Escape schließen, danach Rast für Rast bis oben scrollen und bei jedem Trick-Abschnitt den Tab neu aufbauen; jedes Mal
  müssen Trick-Erkennung, Suche und Familie um genau so viel verschoben sein wie die Trick-Zeilen und dürfen keine Zeile
  überdecken. `ui-scale:` prüft den Player der Tutorials Sbang und Flip in allen Fenstergrößen. Bildschirmfotos
  (`-PpropwashScreenshots=true`, `bildschirmfotos: Freestyle-Tutorial …`): Vorschau und Player je Familie
  (`trick_tutorial_preview_<familie>`, `trick_tutorial_player_<familie>`), Stick-Cam in Mode 2 und Mode 1, Vorschau und
  Player deutsch, Liste nach Zurück aus dem Player (`trick_tutorial_back_scrolled`) und danach nach oben gescrollt
  (`trick_tutorial_back_scrolled_up`); Flugrichtung (`bildschirmfotos: Freestyle-Tutorial Flugrichtung …`): Vorschau,
  Anflug und Kernschritt von Power Loop, Split-S und Vanny Roll (`trick_direction_preview_<trick>`,
  `trick_direction_player_<trick>_<schritt>`) und zum Vergleich dieselbe Drohne im Sichtflug von der Seite
  (`trick_direction_in_flight`, `drone.client.ForwardFlightShowcase`: Kamera voraus, Antenne hinten).
