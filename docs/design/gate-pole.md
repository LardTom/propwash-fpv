# Gate-Stange: eine einzelne Gate-Seite (race, 0.5.0)

Anforderung (Besitzer, 08.10., nach dem Test durch einen Renn-Piloten, mit einer MultiGP-Skizze: ein Gate 7 ft breit und
6 ft hoch, dessen rechter Pfosten über einen 3-Wege-Verbinder mit einer einzelnen 5-ft-Stange nach oben verlängert ist):
Es fehlt ein Kurs-Bauteil, das nur aus einer der vier Seiten eines Gates besteht und sich auch auf Gates setzen lässt.

Ergebnis: das Item **Gate-Stange** (`propwash:gate_pole`, englisch „Gate Pole“). Eine Gate-Stange ist eine Gate-Seite
mit demselben Profil (0,5 × 0,5 Blöcke), denselben Verbindern, LED-Leisten, Texturen und Längenschritten wie die Seiten
der Gates. Sie steht am Boden, ragt aus Wänden, hängt von Decken, sitzt auf der oberen Ecke stehender Gates (der
3-Wege-Verbinder aus der Skizze) und lässt sich zu Türmen verlängern. Sie ist ein **Hindernis und eine Markierung**,
kein Durchflug-Element: Sie gehört zu keinem Kurs und zählt in keiner Runde.

## 1. Modell (`race.CourseElement.GATE_POLE`, `race.GatePoles`)

- Die Stange ist eine `GateEntity` der neuen Art `GATE_POLE` (wie Flagge, Slalomstange und Würfel, siehe
  `course-elements.md` §1): Besitz, Abbau, Synchronisation, Speicherung, Einstellen und Kollision kommen vom Gate.
  `CourseElement` kennt dafür zwei neue Eigenschaften: `courseable()` (nur die Stange ist `false`) und `mountable()`
  (Gate und Stange dürfen eine Lage haben; Flagge, Slalomstange und Würfel stehen immer).
- Rahmen wie bei Gates (`gate-mounting.md` §2): Ursprung = Mitte des Fußendes, `up` = Achse der Stange, Breite
  `GatePoles.THICKNESS` = 0,5 (Rahmenstärke der Gates), Tiefe 0,5. Drei Lagen:

  | Fläche | Lage (`GateMount`) | Stange | Befestigungspunkt |
  |---|---|---|---|
  | Boden | `STANDING` | senkrecht nach oben, Kreuzfuß | Blockmitte auf dem Boden |
  | Wand | `WALL_DIVE` | waagerecht aus der Wand heraus, Flansch an der Wand | Mitte der geklickten Blockseite |
  | Decke | `HANGING` | senkrecht nach unten, Flansch an der Decke, Oberkante bündig (ohne den Abstand hängender Gates) | Blockmitte an der Decke |

- **Länge** 1–16 Blöcke in halben Schritten wie die Öffnung der Gates (`GatePoles.snapLength`), Standard 4 (eine Seite
  des mittleren Gates). Gespeichert wie die Höhe der Elemente (`OpeningHeight`, nur wenn nicht Standard).
- **Farbe** = Variante: 16 Farben in der Reihenfolge der Minecraft-Farbstoffe (Namen über `color.minecraft.*`),
  Standard Weiß; Schwarz lässt die LED-Leisten dunkel. Die Lichtfarben sind eigene, leuchtende Werte
  (`GatePoles.ledColor`). Die Platzierungs-Vorliebe merkt sich die Farbe (`GatePlacement.MAX_VARIANTS` 4 → 16, das Byte im
  Netz bleibt).
- **Drehen:** nur Gieren wie bei Kurs-Elementen (am Boden und an der Decke um die eigene Achse, an der Wand schwenkt die
  Stange seitlich); Nicken und Rollen gibt es nicht.
- **Sitzplätze** (`GatePoles.seats`): Ein aufrechtes, nicht geneigtes Gate, das nicht hängt (`STANDING`, `WALL_SIDE`,
  `WALL_WINDOW`, auch frei gegiert und mit eigener Größe), bietet seine zwei oberen Ecken an (Mitte des Pfostens, Oberkante
  des Rahmens); eine stehende Stange bietet ihre Spitze an. `restsOn(stange, träger)` prüft auf ±0,001 Blöcke.
  Liegende, hängende, geneigte und Wand-Tauchtore, Elemente und Freiform-Gates tragen keine Stangen.
- Reines Java (portierbar ab 0.5.1): `GatePoles`, die Stangen-Fälle in `GateGeometry`, `GatePlacement` und
  `GateStacking.base`. Minecraft-Anbindung in `GatePoleLinks` (Suche nach Träger und aufsitzenden Stangen, Abnehmen,
  Färben) und `GatePoleItem`.

## 2. Bedienung

- **Setzen:** Rechtsklick auf Boden, Wand oder Decke mit Vorschau (weiß frei, rot blockiert). `R` dreht in der
  eingestellten Schrittweite, **Schleichen + `R`** wechselt die Farbe – vor dem Setzen in der Vorschau, danach an der
  Stange (Stange oder Kurs-Werkzeug in der Hand, Blick auf die Stange).
- **Aufsetzen:** Rechtsklick mit einer Gate-Stange auf ein stehendes Gate setzt eine Stange auf die Ecke, die dem
  Klickpunkt am nächsten liegt; die Vorschau zeigt sie dort schon beim Zielen auf das Gate. Steht auf der Ecke schon eine
  Stange (oder ist das Gate Teil eines Gate-Stapels), wandert die neue nach oben: auf die Spitze des Turms dieser Ecke bzw.
  auf das oberste Gate. Rechtsklick auf eine stehende Stange verlängert ihren Turm. Höchstens **vier** Stangen
  übereinander (`GatePoles.MAX_CHAIN`, wie `GateStacking.MAX_STACK`).
- **Einstellen:** Schleichen + Rechtsklick (Stange oder Kurs-Werkzeug) startet „Gate-Stange einstellen“
  (`gate-edit-mode.md`) mit den Zeilen Richtung, Farbe und Länge. Hängende Stangen bleiben beim Ändern der Länge an der
  Decke, stehende auf ihrem Fuß.
- **Farbstoff:** Rechtsklick mit einem Farbstoff färbt die Stange (im Überlebensmodus kostet es einen Farbstoff).
- **Abbauen** wie bei Gates (Schleichen + Linksklick oder Kurs-Werkzeug).
- **Kurs-Werkzeug:** nimmt Stangen nicht auf („Gate-Stangen sind Hindernisse und Markierungen – sie gehören zu keinem
  Kurs“). Mit dem Kurs-Werkzeug in der Hand zeigt das Namensschild „Gate-Stange · Farbe · kein Kurs-Element“.

## 3. Stangen auf Trägern

- **Gesperrt:** Ein Gate mit Stangen darauf lässt sich nicht verstellen und nicht in eine andere Lage bringen, nur
  umdrehen (180°, die Stangen sitzen danach auf den vertauschten Ecken) – wie ein Gate in einem Gate-Stapel
  (`GateEntity#inStack`, Meldung „Erst die Gate-Stangen darauf abnehmen“). Eine Stange mit Stangen darauf behält ihre
  Länge; Richtung und Farbe lassen sich ändern (`GatePoleLinks.keepsRiders`).
- **Rechte:** Aufsetzen verlangt neben dem Baurecht, dass man den Kurs des tragenden Gates bearbeiten darf (Besitzer,
  Operator oder Kurs ohne Besitzer, wie beim Einstellen und Abbauen, `GatePoleItem#foreignCourse`); bei einem Turm zählt
  das Gate unter der untersten Stange, bei einem Gate-Stapel jedes Gate des Stapels. Sonst kommt die Meldung „Kurs …
  gehört …“ und nichts wird gesetzt.
- **Nachrutschen:** Rutscht ein Gate in einem Gate-Stapel nach, weil ein Gate darunter abgebaut wurde, wandern die
  Stangen darauf mit (`GateEntity#dropTo`).
- **Abbau:** Wird ein Träger abgebaut, kommen alle Stangen darüber mit ab; im Überlebensmodus fallen sie als Items heraus
  (wie der Träger selbst), im Kreativmodus und beim Entfernen ohne Spieler (`/kill`, `discard`) nicht.
- Darstellung: Eine getragene Stange beginnt ohne eigenen unteren Verbinder direkt auf dem Verbinder des Trägers und
  umschließt ihn mit einem etwas größeren 3-Wege-Verbinder (`gate_piece_pole_tee`); eine freie Stange hat unten einen
  Verbinder und den Kreuzfuß, an Wand und Decke einen Flansch. Ein Start-Gate mit Stangen zeigt statt des Banners über
  dem Rahmen die kleine Tafel „START“ an der Seite – wie ein Start-Gate mit einem Gate darauf.

## 4. Kollision und Platz

- Ein Kollisionsteil 0,5 × Länge × 0,5 wie ein Pfosten der Gates (gegiert als OBB, `gate-free-transform.md`); Drohnen
  prallen ab wie an Gate-Seiten. Platzprüfung wie bei Elementen (`GateItem#freeElement`): keine Blöcke, keine anderen
  Gates oder Stangen, beim Setzen keine Körper. Die Stange berührt ihren Träger nur an der Oberkante.

## 5. Darstellung und Assets

- `race.client.GatePoleRenderer` setzt die Stange aus Gate-Teilen zusammen (`GateRenderer.Piece`): Pfosten und
  LED-Leisten (`post_left`, `led_post_left`, je ganz und halb) und drei neue Teile `pole_end` (quadratischer Verbinder
  0,625 × 0,5 × 0,625), `pole_tee` (3-Wege-Verbinder, umschließt den Verbinder darunter) und `pole_foot` (Kreuzfuß aus
  zwei Platten, die quere etwas niedriger gegen Z-Fighting). Die LED-Leisten tragen die Farbe der Stange, leuchten voll.
- Licht: Die Gate-Entity misst die Helligkeit in der Mitte ihrer Geometrie (`GateEntity#getLightProbePosition`), wie
  der Einstell-Modus. Vanilla nimmt die Augenhöhe des Entity-Typs (0,85 × 4 = 3,4 Blöcke über der Position); bei Stangen
  an Nord- und Westwänden (Position genau auf der Wandfläche), bei hängenden Stangen ab 2,5 Blöcken Länge und in
  niedrigen Räumen lag dieser Punkt im Block, und die Stange wurde bis auf die LED-Leisten schwarz gezeichnet.
- Erzeugt von `tools/assets/world/generate.py` (`gen_gate_pole`), geprüft von `validate.py`; die Rahmen-Textur der Gates
  hat dafür ein neues 10 × 10-Feld `conn_cap` (Deckfläche des quadratischen Verbinders). Inventar-Symbol
  `item/gate_pole` im Stil des Gate-Symbols (dunkles Rohr mit grüner LED-Leiste, gelbe Verbinder, Kreuzfuß).

## 6. Speicherung, Netz, Kompatibilität

- NBT wie bei Elementen: `Kind` = `gate_pole`, `Variant` = Farbe, dazu `Mount` und (wenn nicht Standard) die Länge.
- Neue Ordinalzahl 5 der Art im synchronisierten Byte `DATA_KIND` (vier Bits reichen); keine neuen Pakete. 0.5.0 hat
  ohnehin **Netzprotokoll 14** (wegen des Redstone-Signals der Gates, `design/gate-redstone.md` §3); die 14 deckt die
  neue Art mit ab, ein eigener Sprung ist nicht nötig. Parts-API unverändert, die Stange ist kein Teil der API.
- Kurse, Kursdaten, Bestenlisten und Geister bleiben unberührt, weil Stangen nie in Kurse kommen.

## 7. Rezept

| Item | Anzahl | Muster | Zutaten |
|---|---|---|---|
| `gate_pole` | 2 | `G` / `B` / `B` | Glowstone-Staub, Eisengitter |

Wie die Gates (Glowstone für die LEDs, Eisengitter für die Rohre), Gruppe `propwash_gate`, Reiter „Baumaterial“,
Freischaltung über „jedes Propwash-Item“ (`tools/data/survival.py`).

## 8. Tests

- JUnit `race.GatePolesTest` (9): Art und Farben, Setzen an Boden/Wand/Decke mit Kollisionsteil, Sitzplätze auf
  Gate-Ecken (auch gedreht, mit eigener Größe und frei gegiert), nur aufrechte Gates tragen, Türme aus Stangen und
  Grenze, Ecke nach Klickseite, Einstellen (hängende bleibt an der Decke), Vorliebe mit 16 Farben, keine Durchflüge.
- Selbsttest `race.client.GatePoleSelfTests` (`race: Gate-Stange …`): Setzen über `GateItem#useOn` an Boden, Wand und
  Decke, Ablehnung am belegten Platz; Aufsetzen auf die rechte und linke Ecke, Verlängern über Gate und Stange bis vier,
  fünfte abgelehnt, Kurs-Werkzeug lehnt ab, Gate gesperrt und umdrehbar, Farbe trotz Stange darauf; beim Client getragen
  mit 3-Wege-Verbinder und Vorschau auf der Ecke eines zweiten Gates; Einstellen (Länge, Richtung, Farbe), hängende
  Stange kürzen, NBT, Farbstoff, Schleichen + R, Drohne prallt an stehender und an Wand-Stange ab; Nachrutschen im
  Gate-Stapel, Abbau im Überleben mit zwei Stangen-Items, Entfernen ohne Spieler ohne Items; Lichtmesspunkt in der
  Mitte und nicht im Block bei Stangen am Boden, an Ost- und Nordwand und hängend mit 4 und 3 Blöcken.
- Bildschirmfotos `gate_pole_ground`, `gate_pole_multigp`, `gate_pole_stacked`, `gate_pole_wall_ceiling`,
  `gate_pole_edit_mode`, `gate_pole_fpv`.

## 9. Entscheidungen

| Frage | Entscheidung | Warum | Verworfen |
|---|---|---|---|
| Eigene Entity oder Art | Art `GATE_POLE` der Gate-Entity | Abbau, Besitz, Netz, Speicherung, Einstellen, Kollision und Vorschau gibt es schon | eigene Entity (doppelter Code) |
| Name | „Gate-Stange“ / „Gate Pole“ | grenzt sich von der Slalomstange ab und sagt, woher sie kommt | „Gate-Pole“ (im Deutschen mehrdeutig), „Gate-Pfosten“ (passt nicht zur liegenden Stange an der Wand) |
| Slalomstange? | Nein, sie zählt nicht und gehört zu keinem Kurs | Slalom hat schon die Slalomstange mit Seite, Wertung und Pfeilen; zwei Items für dieselbe Aufgabe verwirren | Stange als Slalom-Prüfpunkt mit Seite |
| Länge | 1–16 in halben Schritten, Standard 4 | dieselben Schritte wie Gate-Seiten; 4 = Seite des mittleren Gates | feste Längen in drei Größen |
| Farbe | 16 Farbstoff-Farben der LED-Leisten, Standard Weiß | Gates haben keine Lackierung, ihre Farbe sind die LEDs; Farbstoffe kennt jeder, Rechtsklick mit Farbstoff passt dazu | eigene Lackier-Optionen am Rahmen |
| Wand und Decke | aus der Wand heraus, von der Decke herab | die zwei sinnvollen Lagen einer einzelnen Stange; eine senkrechte Stange an der Wand steht genauso gut am Boden | alle sieben Gate-Lagen |
| Sitz auf dem Gate | Stange beginnt auf dem Eckverbinder, 3-Wege-Verbinder darum | entspricht der Skizze (Verbinder bleibt, Stange steckt oben darin); keine Überlappung der Kollision | Stange überlappt die Ecke wie gestapelte Gates |
| Welche Ecke | die dem Klickpunkt nächste, bei belegter Ecke die Turmspitze | man zielt einfach auf die Seite, die man verlängern will | Ecke nach Blickrichtung des Spielers |
| Stapelgrenze | 4 Stangen | wie Gate-Stapel, hält Türme bezahlbar | unbegrenzt |
| Träger verstellen | gesperrt, nur umdrehen | wie Gate-Stapel; Mitbewegen bei Größe oder Neigung wäre schwer vorhersehbar | Stangen beim Verstellen mitnehmen |
| Träger abbauen | Stangen kommen mit ab (Überleben: als Items) | wie beim echten Aufbau, nichts schwebt, keine schiefen Sitze nach Breitenwechsel | Stangen fallen nach unten |
| Nicken/Rollen | nicht | Stangen sollen stehen, hängen oder aus der Wand ragen; schräge Stangen sind selten und erschweren Türme | freie Neigung wie bei Gates |
| Netzprotokoll | 14 wie das ganze Release 0.5.0, kein eigener Sprung | nur eine neue Ordinalzahl im vorhandenen Byte; 0.5.0 erhöht das Protokoll wegen des Gate-Redstone ohnehin von 13 auf 14 | eigener Sprung auf 15 |
| Licht | Helligkeit aus der Mitte der Geometrie (alle Arten der Gate-Entity) | Vanilla misst an der Augenhöhe des Entity-Typs, bei der Gate-Entity 3,4 Blöcke über der Position – bei Stangen an Nord- und Westwänden, hängenden Stangen ab 2,5 Blöcken und in niedrigen Räumen liegt dieser Punkt im Block, die Stange wurde schwarz; der Einstell-Modus maß schon in der Mitte | eigener Entity-Typ der Stange mit kleinerer Augenhöhe (neuer Typ, Netz und Speicherung ändern sich) |
| Fremde Kurse | Stange auf ein Gate nur, wenn man dessen Kurs bearbeiten darf (Besitzer, Operator oder Kurs ohne Besitzer) | ein Gate mit Stange ist gesperrt, bis die Stange ab ist – ohne die Prüfung könnte jeder fremde Kurse so blockieren | nur das Baurecht prüfen |
| Flugschule | Stange öffnet mit `M` die Lektion „Flaggen, Slalom und Tauchwürfel“, keine eigene Animation | die Lektion zeigt Bauen und Einstellen von Kurs-Bauteilen; eine weitere Animation hätte die Szene verlängert | eigene Flugschul-Szene |

## 10. Offene Punkte

- Eigene Animation der Gate-Stange in der Flugschule (Aufsetzen auf die Ecke, Turm).
- Stangen auf den Ecken des Tauchwürfels oder in Rennkursen der Welt (`world-courses.md`).
- Port nach 1.21.1/26.2 ab 0.5.1: `GatePoles` wandert unverändert mit.
