# Shaderpacks (Iris, Oculus)

Wie Propwash mit Iris-Shaderpacks rendert, was im Schattenpass erlaubt ist und was bei Shaderpacks bewusst anders
aussieht. Geprüft am 06.10.2026 mit Iris 1.11.7 und Sodium 0.9.2 für 26.3 (Fabric) und den Packs Complementary
Reimagined r5.9.3 und Complementary Unbound r5.9.3. Alle Zugriffe auf Iris laufen über
`compat.iris.client.IrisCompat` (nur Reflection auf `net.irisshaders.iris.api.v0.IrisApi`, Besitzer video, siehe
`docs/FOUNDATION.md` „Compat-Pakete“); ohne Iris oder Oculus ist jede Abfrage sofort `false`.

## 1. Der Schattenpass

Iris zeichnet für die Sonnen-Schatten die Welt ein zweites Mal aus Sicht der Sonne (orthografische Projektion um die
Kamera). Für Entities ruft der Schattenpass `shouldRender`, `extractRenderState` und `submit` des Entity-Renderers auf,
und zwar für **alle** Entities im Schatten-Frustum: Die Vanilla-Regel „die Kamera-Entity wird nicht gezeichnet“ und
`LevelExtractor#isEntityVisible` gelten dort nicht. `IrisApi#isRenderingShadowPass` ist während Extraktion und Submit
des Schattenpasses `true`; Propwash fragt das über `IrisCompat.renderingShadowPass()` ab.

**Regel:** Im Schattenpass entsteht nur Geometrie, die in der Welt wirklich an dieser Stelle steht und Licht abhält.
Nie im Schattenpass:

- **Kamerabezogene Geometrie** – alles, was über die `CameraRenderState` (Lage, Projektion) oder an der Linse platziert
  wird. Heute sind das die Props im FPV-Bild (`FpvProps`): `FpvProps.submit` bricht im Schattenpass sofort ab und zählt
  den Aufruf (`FpvProps.shadowPassCalls()`, der Selbsttest erwartet 0).
- **Reine Licht-Geometrie** – LED-Halos (`AfterWaterRenderTypes.halo()`, Arm-LEDs und LED-Streifen) und das
  Motor-Glühen. Licht wirft keinen Schatten; die kleinen LED-Körper selbst bleiben (das sind Bauteile).
  `DroneRenderState.shadowPass` (gesetzt in `QuadcopterRenderer#extractRenderState`) schaltet das in `DroneVisuals.submit`.

Weltgeometrie (Drohnen in der dritten Person samt Prop-Scheiben, Gates, Kurs-Elemente, Item-Rahmen mit Drohnen-Items)
wirft wie jede Entity ihren Schatten. Was über `ClientBus.SUBMIT_WORLD_GEOMETRY` gezeichnet wird (Ideallinie, Geister,
Gate-Vorschau, Trainings-Hologramme, Heimatmarke), erreicht den Schattenpass nicht: Iris ruft dort keine Level-Events auf.

### Ursache des flügelförmigen Schattens (gemeldet am 06.10.2026)

Mit Complementary lag im FPV-Flug tief über Wasser oder Boden ein dunkler, flacher, flügelförmiger Schatten im unteren
Bildteil. Der Schattenpass extrahierte die eigene FPV-Drohne (Kamera-Entity) und ließ sie über `FpvProps.submit`
zeichnen. Diese Geometrie ist nur aus der Kamera richtig:

- Sie hängt an der Linse (`camera.pos`, `camera.orientation`) und ist für die Nahebene verkleinert.
- Die Prop-Scheiben (seit 0.5.0 auch die Ducts, `FpvProps.emitGuards`) schiebt `FpvProps.emitAtLens` auf mindestens 52 mm
  Tiefe entlang des Sehstrahls (seit 0.5.0 `FpvPropGeometry.pushScale`: Faktor `nearScale`, nur der Rest vor 55 mm wird in
  52–55 mm gedrückt). Eckpunkte knapp
  vor der Linse (Tiefe ab 0,25 mm) wandern dabei bis zu 200-fach nach außen: im Kamerabild deckungsgleich mit der
  Scheibe, in der Welt aber eine dünne Fläche quer vor der Linse, viele Meter breit.
- Ohne Shaderpack zeichnet seit 0.5.0 ein eigener Durchgang am Platz der Hand (`FpvPropPass`) Props und Ducts ohne
  Tiefentest gegen die Welt. Mit aktivem Shaderpack (`IrisCompat.shaderPackActive`) bleibt der Welt-Durchgang mit der
  Schatten-Sperre unten, weil Iris die Hand in seiner eigenen Pipeline zeichnet.
- Die Sichtprüfung `FpvPropGeometry#visible` nimmt die Projektion der `CameraRenderState`; im Schattenpass ist das die
  orthografische Schattenprojektion (`1/m00` riesig), also galt jeder Prop als sichtbar – auch wenn im Bild gar keine
  Props zu sehen waren.

Aus Sicht der Sonne warf diese Fläche den flachen, an den Enden spitz zulaufenden Schatten vor die Drohne; bei tiefer
Sonne von hinten liegt er genau im unteren Bildteil (im gemeldeten Bild über Wasser). Mit Reimagined und
Unbound gleich nachgestellt (`bildschirmfotos: Iris – FPV tief über Wiese …`, vor der Korrektur ein breites dunkles
Band quer durchs Bild).

## 2. Schatten der eigenen Drohne im FPV

Entscheidung: Die eigene Drohne wirft im FPV-Flug mit Shaderpack einen echten Schatten – tief fliegend sieht man ihn
auch im echten FPV-Bild, und er sah in den Bildern mit beiden Packs sauber aus (kleiner weicher Fleck unter der
Drohne, kein Streifen, keine Selbstabschattung der Props im Bild). Abgelehnt:
die eigene Drohne im Schattenpass ganz weglassen (einfacher, aber unrealistisch, wenn man dicht über den Boden fliegt).

- `QuadcopterRenderer#shouldRender` lässt die eigene FPV- oder Zuschauer-Drohne im Schattenpass immer zu (auch wenn
  „Props im FPV-Bild“ aus ist). `extractRenderState` setzt sie dort über `DroneVisuals.placeAtDisplayedPose` auf die
  Pose des **angezeigten** Bildes (`FpvView.displayedBody`: Schwerpunkt und Lage aus dem Posen-Puffer, mit
  Video-Latenz) statt auf die Entity: Der Schatten gehört zum selben Zeitpunkt wie das Bild. Mit der Entity-Pose liefe
  er bei 30 m/s und 30 ms Latenz knapp 1 m voraus, mit Kreativ-Latenz bis 500 ms viele Meter.
- Gezeichnet wird die Drohne in Originalgröße (Drohnengröße 1,0, auch wenn die Darstellungsgröße für fremde Drohnen höher eingestellt ist),
  ohne Namensschild, Vanilla-Schattenfleck und Halos. `QuadcopterRenderer.fpvShadowBodies()` zählt für den Selbsttest.
- Ohne Shaderpack bleibt es wie bisher: kein Vanilla-Schattenfleck unter der eigenen FPV-Drohne.
- Schalter „Schatten“ (Tab „Darstellung“, persönlich): aus nimmt im Schattenpass **alle** Drohnen heraus, eigene und
  fremde (vorher wirkte er nur auf den Vanilla-Schattenfleck). Tooltip `propwash.drone_render.shadow.tip` sagt das.

## 3. Weitere Unterschiede mit Shaderpack

| Effekt | Verhalten mit Shaderpack | Wo |
|---|---|---|
| Video-Effekt (Rauschen, Bildstörung, 4:3-Ränder) | Ersatz-Overlay im HUD statt Post-Pass, OSD und Ränder unverändert | `VideoPass#overlayReason`, `docs/design/image-format.md` |
| Fisheye-Objektiv (seit 0.5.2, Stärke per Regler) | wirkt auch mit Shaderpack: entzerrt das fertige Bild des Packs am Hook nach `applyPostEffects` (geprüft mit Complementary Reimagined r5.9.3 bei 80 %, Marken im Median unter 1 px an der berechneten Stelle; andere Stärken laufen durch denselben Pass) | `VideoPass`, `docs/design/fisheye-lens.md` |
| FPV-Scheinwerfer (LED-Streifen) | Aufhell-Kegel in der Bildmitte ohne Tiefe; schwebend und mit wenig Uptilt hellt er eher den Himmel am Horizont auf | `docs/design/led-strip.md` „Grenze mit Shaderpacks“ |
| Nahebene im FPV | bleibt 50 mm (Packs rechnen mit der Vanilla-Nahebene) | `docs/design/fpv-eye-clearance.md` |
| Prop-Scheiben (eigene Pipelines) | Iris-Programm `ENTITIES_TRANSLUCENT`, Schatten `SHADOW_ENTITIES` | `AfterWaterRenderTypes`, `IrisCompat.assignPipeline` |
| Heimatmarke (Gizmo-Linien) | deckend gezeichnet, siehe unten | `rescue.client.HomeMarker` |
| Drohnen-Trails (eigene Pipeline `drone_trail`) | Iris-Programm `ENTITIES_TRANSLUCENT` statt `SPIDER_EYES` (das mischt additiv: bei Tag fehlte der dunkle Halo, alle Farben wurden blass); bei Tag farbig mit Halo, nachts sichtbar, aber ohne starkes Leuchten; kein Schatten | `drone.client.trail.TrailRenderer`, `docs/design/drone-trails.md` |
| Gates, Item-Rahmen, OSD, Funk-HUD | unverändert; Gate-LEDs leuchten (`eyes`) | – |

Die Farben im Morgen- und Abendlicht weichen bei Complementary stark von Vanilla ab (Ostseiten von Gates und Blöcken
orange, grüne Props gelblich). Das ist das Sonnenlicht des Packs, kein Fehler.

### Heimatmarke unsichtbar im FPV

Die Heimatmarke (Kreise und Stab am Homepoint) fehlte mit Shaderpack im FPV-Bild, im Sichtflug war sie zu sehen.
`HomeMarker.submit` lief im FPV jedes Bild (Logzeilen im Versuch), selbst als „immer obenauf“ gezeichnete Kreise
blieben unsichtbar. Gizmo-Linien mit Alpha unter 255 zeichnet Vanilla mit `RenderTypes.linesTranslucentNoDepthWrite()`
(Pipeline `minecraft:pipeline/lines_translucent_no_depth_write`); Iris 1.11.7 hat dafür kein Programm und meldet einmal
„Missing program … in override list. This is likely an Iris bug“, die Linien laufen dann am Pack vorbei. Warum davon im
Sichtflug noch etwas im Bild ankommt und im FPV nicht, ließ sich ohne Iris-Quellen nicht klären. Mit aktivem Shaderpack
zeichnet `HomeMarker` deshalb deckend (Alpha 255): Vanilla nimmt dann `RenderTypes.lines()`, das Iris dem Programm
`LINES` des Packs zuordnet; die Logzeile verschwindet, die Marke ist im FPV und im Sichtflug zu sehen. Ohne Shaderpack
bleibt die Marke durchscheinend wie bisher.

## 4. Offene Punkte

- **NeoForge mit Iris im Entwicklungslauf:** Iris 1.11.7 für NeoForge stürzt im NeoForge-Dev-Lauf (26.3.0.10-beta und
  26.3.0.51-beta) beim ersten Bild mit Shaderpack ab (`IllegalStateException: Missing uniform InSampler` in
  `net.irisshaders.iris.pathways.CenterDepthSampler`, ohne Propwash im Stack). Die Korrektur ist Common-Code und auf
  NeoForge identisch, dort aber nicht mit Shaderpack geprüft. Der Selbsttest übergeht unter NeoForge inzwischen den
  Warnbildschirm, den Iris beim Laden auslöst (veraltetes `logoFile`).
- **Scheinwerfer mit Shaderpack** bleibt die bekannte Overlay-Näherung (siehe `led-strip.md`); echtes Licht gibt es mit
  LambDynamicLights.
- Ein Vanilla-Schattenfleck für die eigene FPV-Drohne ohne Shaderpack ist nicht geplant.

## 5. Prüfen

Selbsttest-Schritt `iris: eigene FPV-Drohne im Schattenpass – keine FPV-Props, echter Schatten` (angemeldet in
`compat.iris.client.IrisSelfTests`, läuft immer, ohne Iris oder ohne aktives Shaderpack mit Logzeile übersprungen):
schwebt im FPV tief über einer Wiese bei Morgensonne im Rücken und prüft über 40 Ticks, dass der Schattenpass
`FpvProps` nie aufruft, die eigene Drohne als Schattenkörper zeichnet und die Prop-Scheiben im Bild weiter gezeichnet
werden; danach nimmt „Schatten aus“ die Drohne aus dem Schattenpass. Bildschirmfotos `bildschirmfotos: Iris – …`
(`IrisShowcase`, nur mit Iris und `-PpropwashScreenshots=true`): FPV über Wasser und über Wiese, Sichtflug von der
Seite, FPV 4:3 mit analogem Rauschen, Item-Rahmen und Gates, FPV und Sichtflug bei Nacht mit LED-Streifen, danach
dieselben Szenen ohne Shaderpack (`…_vanilla`). Aufbau des lokalen Laufs in `docs/testing.md` §5.
