# Drohnen-Trails – Propwash 0.5.0

Auftrag des Besitzers (08.10.2026), sinngemäß: eine Welt-Regel, die hinter den Drohnen einen kleinen Trail zeichnet,
damit man sie besser sieht. Die Regel schaltet die Funktion grundsätzlich frei, danach entscheidet jeder Spieler für
sich. Nachtrag: Wichtigster Zweck sind die Trails **anderer** Drohnen im FPV-Bild – man soll die anderen Piloten
beim Fliegen sehen.

Für Spieler: [`docs/drone-trails.md`](../drone-trails.md).

## 1. Verhalten

- Welt-Regel `propwash:drone_trails` (Schalter, Standard **aus**, Gruppe „Spiel“, keine Physik). Training und Arcade
  schalten sie an, Realistisch bleibt der Standard. Sync wie alle Welt-Regeln über `propwash:world_rules`.
- Persönlicher Schalter **Drohnen-Trails zeigen** (Standard an) im Tab „Darstellung“, Abschnitt „Drohnen-Trails“,
  Datei `config/propwash/trails.json` (`enabled`). Ist die Welt-Regel aus, ist der Schalter gesperrt, darunter steht
  „In dieser Welt aus – Welt-Regel „Drohnen-Trails““, der Tooltip erklärt, wer sie einschaltet. Der Tab baut sich neu
  auf, sobald die Regel sich ändert. Über die Suche zu finden („Trail“, „Spur“, „Schweif“, „tracer“ …).
- Taste **Drohnen-Trails ein/aus** (ohne Standardbelegung, Tab „Tasten“): schaltet den persönlichen Schalter um und
  meldet das als Hinweis im Propwash-Stil; bei Regel aus nur der Hinweis „in dieser Welt aus“.
- Jede **scharfe, fliegende** Drohne zieht einen dünnen, verblassenden Trail. Gelandet (Boden höchstens 0,35 Blöcke
  unter der Drohne und langsamer als 1 m/s) oder entschärft entsteht keine neue Spur, der vorhandene Trail verblasst.
- Länge: Jede Probe lebt 1 s (bis 4 m/s) bis 2 s (ab 24 m/s), linear dazwischen nach dem geglätteten Tempo
  (Zeitkonstante 0,25 s). Der Trail wird zum Ende hin dünner (30 % Breite, aber nie schmaler als die Mindestbreite im
  Bild, siehe Breite) und durchsichtiger (1 − (1 − Frische)²,
  so bleibt die zweite Hälfte noch gut sichtbar). Am Kopf blendet der Trail zur Drohne hin ein, damit er nicht über den
  Rumpf gezeichnet wird.
- Breite: Kern 6 cm, Halo 16 cm, Deckkraft 90 % bzw. 45 %; mit der Entfernung wächst die Breite so, dass der Kern
  im FPV-Bild (1080p) mindestens ≈ 2–3 Pixel und der Halo ≈ 6 Pixel breit bleibt (Winkel 0,0014 bzw. 0,0036 rad je
  Seite); diese Mindestbreite gilt auch am verblassenden Ende, sonst zerfällt ein ferner Trail dort in Striche, weil der
  Kern schmaler als ein Pixel würde. Nahe der Kamera (unter 0,5 bis 2 Blöcke) und
  am Ende der Sichtweite der Drohnen (Tab „Darstellung“, letzte 24 Blöcke) blendet der Trail aus.
- Farbe: leuchtender LED-Streifen (nicht aus, Akku drin, nicht Regenbogen, nicht Weiß/Warmweiß) → LED-Farbe; sonst die
  erste farbige Lackierung in der Reihenfolge Rahmen, TPU, Props, Motoren, Kamera, Antenne, Zubehör, Akku, Stack
  (Sättigung ≥ 30 %, Helligkeit ≥ 25 %); sonst eine von acht kräftigen Farben, fest aus der UUID der Drohne (alle
  Spieler sehen dieselbe). Der Kern ist die Farbe mit voller Helligkeit und mindestens 70 % Sättigung, der Halo
  derselbe Farbton dunkel (28 %). Nachts trägt der leuchtende Kern (ohne Lichtkarte), bei Tag hebt der dunkle Halo den
  Trail vom hellen Himmel ab. Mit Shaderpack (Iris) zeichnet das Pack den Trail wie durchscheinende Entities: bei Tag
  farbig mit dunklem Halo, nachts gut sichtbar, aber ohne das starke Leuchten von Vanilla (Begründung in der Entscheidungstabelle).
- Uhr: Alter und Lebensdauer der Proben laufen auf einer eigenen Uhr des Clients – ein Zähler je Client-Tick, der nur
  zählt, wenn die Welt tickt (nicht in der Pause im Einzelspieler, nicht bei `/tick freeze`), plus Teil-Tick. Die
  Spielzeit der Welt taugt dafür nicht: Der Server setzt sie alle 20 Ticks hart auf seinen Stand, ein Server unter 20 TPS
  springt damit jede Sekunde zurück, und nach einer Pause im Einzelspieler ebenso. Kleine Rücksprünge der eigenen Uhr (bis
  1 s, etwa beim Ende von `/tick freeze`) überspringt der Trail, erst ein größerer fängt ihn neu an.
- Sichtbarkeit: Im FPV-Bild sind die Trails aller **anderen** Drohnen zu sehen; die eigene FPV-Drohne (und beim
  Zuschauen die beobachtete) zeigt keinen Trail. Im Sichtflug und in der dritten Person zeigt die eigene Drohne ihren
  Trail. In einer Flashback-Wiederholung fehlt der Trail der Drohne, an der die Kamera hängt (`ReplayView`).
- Action-Cam-Aufnahmen nehmen das fertige Weltbild der Kamera auf; Trails erscheinen darin genau so wie im FPV-Bild des
  Aufnehmenden (Welt-Regel und **sein** Schalter), ohne eigenen Trail.

## 2. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Id `propwash:drone_trails` statt `propwashDroneTrails` | Namensschema aller Welt-Regeln (`docs/design/world-rules.md` §2) | camelCase im Namensraum `minecraft` |
| Gruppe „Spiel“, keine Physik | rein optisch, ändert keinen Flug und keine Wertung | neue Gruppe „Darstellung“ – eine Gruppe für eine Regel |
| Training und Arcade schalten die Trails an | beides sind Übungs- und Spaß-Voreinstellungen; man sieht die eigene Linie im Sichtflug und die anderen im FPV | in allen Voreinstellungen Standard (aus) |
| Persönlicher Schalter im Tab „Darstellung“ in eigener Datei `trails.json` | neben Staub und FPV-Darstellung, wo Spieler Optik suchen; eigene Datei hält die Änderung an `DroneRenderConfig` klein | Feld in `drone_render.json` |
| Gesperrter Schalter mit Hinweiszeile statt ausgeblendet | Spieler sehen, dass es die Funktion gibt und warum sie fehlt | Schalter verstecken |
| Taste ohne Standardbelegung | alle naheliegenden Tasten sind belegt; die Taste ist für Spieler, die mitten im Flug umschalten wollen | feste Taste (Kollisionen mit Vanilla und Propwash) |
| Eigener Trail im FPV aus, im Sichtflug an | im FPV läge er hinter der Kamera und liefe bei Flips durchs Bild; im Sichtflug hilft er, die eigene Linie zu sehen | eigener Trail auch im FPV; eigener Trail nie |
| Aufnahmen folgen dem Bild des Aufnehmenden | die Action-Cam greift das fertige Weltbild ab, eine zweite Darstellung kostet Zeit; was der Pilot sieht, steht im Video | Trails in Aufnahmen immer aus oder immer an |
| Proben nach Zeit (40 Hz) und mindestens 3 cm Abstand, gespeichert je Bild aus der interpolierten Position | gleiche Form bei 30 und 240 fps, folgt genau der gezeichneten Drohne; fremde Drohnen kommen über die Interpolation des Clients | eine Probe je Tick (eckig bei schnellen Drohnen) |
| Lebensdauer je Probe statt ein Wert je Trail | bremst eine Drohne ab, verschwindet der alte, lange Teil nicht schlagartig | gemeinsame Länge aus dem aktuellen Tempo |
| Kamerazugewandtes Band in zwei Lagen (Halo, Kern), beide Wicklungen | wirkt aus jedem Winkel gleich dünn; Muster der Ideallinie, die mit Iris schon läuft | echte Röhre (viel mehr Geometrie), Partikel (zu viele, nicht glatt) |
| Eigene Pipeline `propwash:pipeline/drone_trail`, in Vanilla gleich `RenderTypes.eyes` (weiße Textur), Kern leicht zur Kamera gezogen; für Iris dem Programm `ENTITIES_TRANSLUCENT` zugeordnet | durchscheinend, ohne Lichtkarte (nachts sichtbar), Tiefentest gegen Blöcke; der Zug sorgt beim Sortieren für Kern über Halo. Iris ordnet `eyes` dem Programm `SPIDER_EYES` zu, das additiv mischt: Der dunkle Halo verschwand, bei Tag wurden alle Farben blass (Lila, Rot und Magenta rosa, Gelb fast weiß) | `RenderTypes.eyes` (unter Iris additiv), Halo und Kern in zwei Programmen (Kern bliebe additiv und bei Tag blass), `BEACON_BEAM` (anderes Vertex-Format) |
| Eigene Uhr aus Client-Ticks statt Spielzeit der Welt | monoton, steht in der Pause; der Abgleich der Spielzeit mit dem Server (alle 20 Ticks) löschte vorher bei jedem Rücksprung alle Trails | Spielzeit mit `max(jetzt, letzte Zeit)` (friert die Trails bis zum Aufholen ein, ein Vorsprung des Servers ließe alle Proben auf einmal altern) |
| Über `ClientBus.SUBMIT_WORLD_GEOMETRY` (Fabric `BEFORE_GIZMOS`, NeoForge `SubmitCustomGeometryEvent`) | gehört zum Extract/Submit-Weg von 26.3; Iris ruft dort im Schattenpass nichts auf – Trails werfen keinen Schatten; zusätzlich bricht `submit` im Schattenpass sofort ab | Zeichnen im Entity-Renderer (erreicht den Schattenpass) |
| Ringpuffer mit 100 Proben je Drohne, höchstens 64 Trails, alle Arrays einmal angelegt, ein fester Geometrie-Renderer | keine Allokation je Bild; Obergrenze ≈ 26 000 Vierecke | Listen je Bild |
| Ab 64 gleichzeitig fliegenden Drohnen bekommt die 65. keinen Trail, bis einer frei wird | einfach und vorhersehbar; so viele fliegende Drohnen in Sichtweite sind selten | nahe Drohnen bevorzugen (Sortieren je Bild) |
| Logik ohne Minecraft-Typen in `drone.trail` (`TrailBuffer`, `TrailEmitter`, `TrailModel`, `TrailColor`) | per JUnit prüfbar und für die Portierungen auf 1.21.1/26.2 wiederverwendbar | alles im Renderer |
| Kein neues Netzprotokoll, keine API-Änderung | die Regel reist im bestehenden Paket, das 0.5.0 ohnehin einführt; Addons brauchen die Trails nicht | Protokoll 13 |

## 3. Technik

| Datei | Inhalt |
|---|---|
| `drone/trail/TrailBuffer` | Ringpuffer (Zeit, Position, Lebensdauer je Probe), `expire(jetzt)` |
| `drone/trail/TrailEmitter` | Proben nach Zeit und Abstand, geglättetes Tempo, Sprung > 4 Blöcke und > 100 m/s = neuer Trail, Zeit bis 1 s rückwärts = Bild übersprungen, weiter rückwärts = neu |
| `drone/trail/TrailModel` | Konstanten und reine Rechnung: Lebensdauer, Ausblenden, Breite, Mindestbreite, gelandet, Obergrenzen |
| `drone/trail/TrailColor` | Farbwahl (LED, Lackierung, Palette), Kern- und Halo-Farbe |
| `drone/client/trail/TrailClient` | Anmeldung (`DroneClient.init`), Taste, Hinweise, `ruleOn()` |
| `drone/client/trail/TrailConfig` | `config/propwash/trails.json` |
| `drone/client/trail/TrailRenderer` | Plätze je Drohne (`QuadcopterEntity.CLIENT_TICK`: Platz, Bodenprobe, Farbe einmal je Sekunde), eigene Uhr (Client-Ticks, `TICK_END`), Pipeline `drone_trail` mit Iris-Zuordnung, Proben je Bild und Geometrie in `submit`, Zähler für den Selbsttest |
| `drone/client/render/DroneRenderConfigTab` | Abschnitt „Drohnen-Trails“ |
| `server/rules/WorldRuleSpecs.DRONE_TRAILS`, `RulePreset` | Regel und Voreinstellungen |

## 4. Tests

- JUnit `TrailBufferTest`, `TrailEmitterTest`, `TrailModelTest`, `TrailColorTest`; `RulePresetTest` (Training und Arcade
  an, Realistisch aus, keine Physik).
- Selbsttest (beide Loader, Gruppe `trails:`): drei vom Server bewegte Drohnen (lackiert, unlackiert, LED grün) ziehen
  Trails in ihrer Farbe, die schnelle (24 m/s) mindestens 1,4-mal so lang wie die langsame; Regel aus → nichts verfolgt
  und nichts gezeichnet, Schalter im Tab gesperrt mit Hinweis, Taste ändert nichts; persönlich aus → nichts übergeben,
  Taste schaltet wieder ein; die Einstellungssuche findet den Schalter unter „trail“, „spur“, „schweif“ und „tracer“;
  die Spielzeit des Clients springt 40 Ticks zurück und 400 Ticks vor (wie beim Abgleich mit dem Server) → die Trails
  behalten ihre Proben; entschärft → keine Probe mehr, der Trail verblasst allmählich (die Frische der jüngsten Probe
  sinkt, frühestens nach 15 Ticks leer, vorher unter 0,5 gesehen); gelandet und scharf → keine Spur; eigene
  Drohne im FPV mit Proben, aber ohne gezeichneten Trail, fremde Trails gezeichnet; im Sichtflug eigener Trail
  gezeichnet.
- Bildschirmfotos (Fabric, `-PpropwashScreenshots=true`, Gruppe „bildschirmfotos: Drohnen-Trails“): `trails_several_drones`,
  `trails_rule_off`, `trails_personal_off`, `trails_fpv_day`, `trails_fpv_night`, `configurator_display_trails`,
  `configurator_display_trails_rule_off`, `configurator_world_rules_drone_trails`.
