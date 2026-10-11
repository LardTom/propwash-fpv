# Flugtraining — interaktive Flugschule

Stand 0.5.0. Ergänzt die Flugschule (`guide`, dreizehn erklärende 3D-Szenen) um Lektionen, in denen der Spieler **selbst
fliegt**: fünf Kapitel mit zwanzig Lektionen, steigender Schwierigkeit, Zielvorgaben, Live-Anzeige, Sternen, Bestzeiten und
Freischaltung. Vorbild ist das Tutorial von Liftoff: kurze Aufgaben, sofortiges Feedback, nochmal fliegen.

## Entscheidungen

| Frage | Entscheidung | Warum | Verworfen |
|---|---|---|---|
| Wo finden die Lektionen statt? | Als **Hologramm-Kurs dort, wo die eigene Drohne gelandet und abgeschaltet steht**, nur beim eigenen Client gezeichnet | Keine Änderung an der Welt, kein Server-Code, kein Netzwerkprotokoll, funktioniert im Überlebensmodus und im Mehrspieler ohne Rechte, jeder Spieler hat automatisch seine eigene Instanz | Eigene Trainings-Dimension (Teleport hin und zurück, Inventar, Tod und Abmelden in der Dimension, Chunk-Erzeugung, Dimensions-Formate je Minecraft-Version — schlecht für die Ports nach 1.21.1/26.2), Leih-Drohne vom Server (Dupe-Gefahr im Überleben, Eingriffe in drone), Simulator-Fenster mit eigener Welt (nicht die echte Drohne, kein echtes FPV-Bild) |
| Welche Drohne? | Die **echte Drohne des Spielers** mit echtem Flugmodell, echter Funke, FPV oder Sichtflug | „Echte Drohne, echtes Flugmodell“; keine Sonderregeln für Akku, Verschleiß oder Rechte | Leih-Drohne (siehe oben) |
| Kurs zu eng? | **Platzprüfung** im Gelände: Luftraum aller Elemente und Flugwege muss frei sein (Blöcke mit Kollisionsform und Flüssigkeiten zählen), Pads brauchen Boden ±1,5 Blöcke; sonst wird der Kurs um 90°/−90°/180° gedreht; sonst meldet das HUD „Kein Platz“ samt Anzahl der Blöcke im Weg | Der Kurs liegt nie in Bäumen oder Häusern, Spieler bekommen eine klare Anweisung | Kurs verschieben/suchen (unvorhersehbar), Kollision ignorieren |
| Fortschritt | **Je Spieler (UUID) in der Client-Datei** `config/propwash/flight_training.json`, gilt in allen Welten und auf allen Servern | Können hängt am Piloten, nicht an der Welt; Belohnungen gibt es keine, also auch keinen Anreiz zum Schummeln | Server-Daten je Welt (Protokoll, Fortschritt pro Server neu) |
| Belohnungen | Nur Sterne, Bestzeiten und Freischaltung — keine Items | Kein Schummeln über Client-Daten möglich | Items oder Fortschritte (bräuchten Server-Prüfung) |
| Freischaltung | Lektionen eines Kapitels nacheinander; Kapitel 2 nach Kapitel 1; **Rennen und Freestyle parallel** nach Kapitel 2; Fortgeschritten nach Rennen **und** Freestyle | Wer nur Rennen oder nur Freestyle will, muss das andere nicht zuerst machen | Strikt linear |
| Flughilfen | Höhe/Position halten, Rescue oder veränderte Physik-Welt-Regeln: Lektion zählt, aber **höchstens ein Stern**, keine Bestzeit | Lernen mit Hilfen ist erlaubt, Bestwerte sollen vergleichbar sein | Versuch verweigern |
| Drohnengröße | Kurs **skaliert nach Radstand**: unter 100 mm (Whoops) 0,6× Größe und 1,25× Zeit, unter 180 mm (Cinewhoops) 0,8× und 1,1×, sonst 1× | Ein 65-mm-Whoop braucht kleinere Ringe und kürzere Wege; Addon-Rahmen werden automatisch eingeordnet | Eigene Kurse je Klasse |
| Wiederholen | **Landen und abschalten** startet automatisch einen neuen Versuch; nahe am Start (≤ 3 m) am selben Kurs, sonst wird der Kurs neu an der Drohne gesetzt; scharf schalten startet die Uhr | Keine zusätzliche Taste, funktioniert mit Funke und Tastatur | Taste „Lektion neu starten“ (Konflikt mit „Rennen neu starten“) |
| Öffnen | Knopf „Flugtraining“ in der Flugschul-Übersicht und im Konfigurator-Tab „Flugschule“; **Taste M im Flug** (oder mit aktiver Lektion) öffnet direkt das Training | Ohne neue Taste erreichbar, M bleibt „Flugschule“ | Eigene Taste |

## Ablauf für Spieler

1. Drohne an einem freien Ort absetzen, mit der Funke starten (FPV oder Sichtflug).
2. M drücken (oder Flugschule → „Flugtraining“), Lektion wählen, „Fliegen“.
3. Der Kurs erscheint als Hologramm an der Drohne: Start-Pad, Ringe/Gates, Kästen, Säulen, Lande-Pad, Trick-Bereich.
   Das HUD zeigt Lektion, Aufgabe, Zeit und die nächste Sterne-Grenze; bei Ringen führt die Ideallinie der Rennen zum nächsten Ring.
4. Scharf schalten startet den Versuch. Geschafft: Banner mit Sternen, Zeit, neuer Bestzeit; nicht geschafft: Grund.
5. Landen und abschalten → neuer Versuch. M → nächste Lektion.

## Kapitel und Lektionen

Maße in Blöcken bei Größe 1 (5″), lokal: x rechts, y hoch, z vorwärts ab der Drohne. Zeiten in Sekunden, gezählt ab dem Scharfschalten.

| Code | Id | Aufgabe | ★★★ / ★★ | Limit |
|---|---|---|---|---|
| 1.1 | `arm` | 2 s scharf, dann am Boden abschalten | 5 / 10 | 60 |
| 1.2 | `hover` | 6 s im Kasten 3×3×3 über dem Start-Pad | 9 / 15 | 90 |
| 1.3 | `land` | Auf dem Pad 10 m voraus landen und abschalten | 0,4 m / 0,8 m Abstand zur Mitte | 60 |
| 1.4 | `yaw` | Im Kasten nacheinander vier Säulen (rechts, hinten, links, vorn) je 1 s anpeilen (±12°) | 12 / 20 | 120 |
| 2.1 | `straight` | Fünf Ringe geradeaus | 6,5 / 10 | 60 |
| 2.2 | `altitude` | Fünf Ringe auf 2,5–6 m | 8,5 / 13 | 75 |
| 2.3 | `turns` | Rechtskurve 180° durch sechs Ringe | 8,5 / 12,5 | 75 |
| 2.4 | `figure_eight` | Acht um zwei Säulen, sieben Ringe | 18 / 28 | 90 |
| 3.1 | `gate_line` | Sechs Gates (4×4) geradeaus, Zwischenzeiten gegen die Bestzeit | 8 / 11,5 | 60 |
| 3.2 | `slalom` | Sechs versetzte Gates | 11 / 17 | 60 |
| 3.3 | `short_course` | Rundkurs mit sechs Gates, zwei Runden (Rennlogik `LapTracker`) | 28 / 42 | 150 |
| 4.1 | `flip` | Flip (auch doppelt/dreifach) | 6,5 / 12 | 60 |
| 4.2 | `roll` | Rolle | 6,5 / 12 | 60 |
| 4.3 | `power_loop` | Power Loop | 11 / 18 | 90 |
| 4.4 | `split_s` | Split-S | 10 / 16 | 90 |
| 4.5 | `combo` | Drei Tricks in einer Kombo | 11 / 18 | 90 |
| 5.1 | `dive_gate` | Ring, liegendes Tauchtor von oben, tiefer Ring | 8,5 / 12 | 75 |
| 5.2 | `precision` | Sechs kleine Ringe (Öffnung 1,7) im S-Bogen, jede Berührung beendet den Versuch | 8,5 / 12 | 60 |
| 5.3 | `low_flight` | Fünf flache Ringe (Öffnung 1,1 hoch) knapp über dem Boden | 6,5 / 10 | 60 |
| 5.4 | `final_exam` | Zwei Ringe, Flip oder Rolle im Trick-Bereich, Tauchtor, Landung | 21 / 28 | 120 |

★ = geschafft im Zeitlimit (Landung: irgendwo auf dem Pad). Die Grenzen sind am Autopilot-Test kalibriert: ein Autopilot mit
4 m/s bekommt bei den Ring-Lektionen höchstens zwei Sterne, mit 10 m/s drei (JUnit `LessonAutopilotTest`).

## Erkennung (reines Java, `guide.training`)

- **Ringe:** `race.GateGeometry` je Ring (stehend oder `FLOOR_FLAT` für das Tauchtor), Durchflug mit `GateGeometry#crossing`
  zwischen zwei Frames in Flugrichtung, nur der nächste Ring zählt. Ein späterer Ring meldet „Ring n verpasst“, rückwärts
  „falsche Richtung“, der Rahmen wird mit dem halben Hitbox-Durchmesser als Berührung erkannt (in `precision` Fehlschlag).
- **Runden:** `race.LapTracker` mit den Gate-Geometrien des Kurses (Start durch Gate 1, Zwischenzeiten, Runde, Ziel);
  die zweite Runde vergleicht jede Zwischenzeit mit der ersten.
- **Tricks:** eigene `trick.TrickSession` je Versuch, gefüttert mit demselben Zustand wie `TrickTracker` (unabhängig vom
  Freestyle-Modus im Tab); gezählt werden bestätigte Tricks (`TrickScore#awarded`), aber nur, wenn die Drohne während des
  Tricks (Beginn bis Ende, ±0,25 s) im Trick-Bereich des Ziels war (`LessonRun#inZoneDuring`). Ein Trick außerhalb meldet
  „außerhalb des Trick-Bereichs, zählt nicht“, das HUD sagt dann „Flieg in den Trick-Bereich“. Kombos zählen die Tricks in
  Folge im Bereich innerhalb der laufenden Kombo von `TrickScore`; ein Trick außerhalb beendet die Folge.
- **Kasten, Säulen, Landung:** Position der Drohne im lokalen Kursrahmen; Säulen über den Kurs der Nase (Sim-Lage, Nase =
  Körper +Z), Landung = Abschalten am Boden auf dem Pad (±1 Block Höhe).
- **Fehlschläge:** Absturz (`crashCount`), Zeitlimit, mehr als 24 Blöcke außerhalb des Kursbereichs, Abschalten in der Luft
  oder mitten in der Aufgabe, zu früh abgeschaltet (1.1), Rahmen berührt (5.2), neben dem Pad gelandet.
- Alles läuft über `LessonRun#update(TrainingSample)`; Sim-Zeit statt Uhrzeit, Pausen zählen also nicht.

## Platzprüfung (`guide.training.Placement`)

Jede Lektion bringt ihren Luftraum mit (`Lesson#airspace`): Kästen um jeden Ring (+0,5), alle Zonen, und Würfel (±0,6)
entlang des Referenzwegs (Anflug, Durchflug, Abflug jedes Rings). Geprüft werden die Blöcke mit Kollisionsform oder Flüssigkeit
ab 0,2 über dem Boden der Drohne; Lande-Pads suchen ihren Boden 1 Block darüber bis 1,5 darunter und brauchen 1,8 freie Höhe.
Nicht geladene Chunks melden „Gelände noch nicht geladen“. Der Client prüft höchstens alle 2 s oder sobald die Drohne
woanders steht.

## Anzeige (`guide.client.training`)

- **Hologramme** (`TrainingRenderer`, `RenderTypes.eyes`, nur eigener Client): nächster Ring amber pulsierend mit Pfeilen,
  folgende hellblau und blasser, geschaffte verschwinden; nach dem Ziel alles grün. Kästen als leuchtende Kanten (amber, grün
  sobald man drin ist), Pads als Kreise (Ziel-Pad mit Ringen für die Präzision und Leuchtsäule), Säulen und Trick-Bereich.
  Die Ideallinie der Rennen (`RaceLineRenderer#submitPath`) führt durch die nächsten drei Ringe.
- **HUD** (`TrainingHud`): oben Mitte Lektion, Aufgabe, Uhr mit der nächsten Sterne-Grenze, darunter kurze Meldungen
  (Ring mit Zeit und Abstand zur Bestzeit, verpasst, Runde, Trick). Ergebnis-Banner in der Mitte. Im FPV im OSD-Stil über den
  Videoeffekten, im Sichtflug als Propwash-Meldungsfläche.
- **Fenster** (`TrainingScreen`, `ThemedScreen`): Kapitel links (Sterne, gesperrt, fertig), Lektionen in der Mitte (★★☆),
  rechts Beschreibung, Tipp, Sterne-Grenzen für die aktuelle Drohne, Bestzeit, Versuche, Sperrgrund und „Fliegen“.

## Mehrspieler und Überleben

- Nichts davon geht über den Server: andere Spieler sehen nur die Drohne fliegen, nicht die Hologramme.
- Die Drohne nutzt sich im Überlebensmodus wie bei jedem Flug ab (Akku, Verschleiß). Das ist gewollt (echte Drohne); der
  Lehrplan beginnt deshalb ohne Abstürze provozierende Aufgaben, und Whoops trainieren auf kleineren Kursen.
- Server-Regeln (Reichweite, Pilotenrechte, Spawnschutz) gelten unverändert.

## Portabilität

`guide.training` enthält keine Minecraft-Typen außer den bereits reinen Race- und Trick-Klassen (`GateGeometry`,
`LapTracker`, `TrickSession`). Minecraft-Code steckt nur in `guide.client.training` (Gelände-Abfrage, Hologramme, HUD,
Fenster, Datei) und lässt sich für 1.21.1/26.2 einzeln anpassen.

## Tests

- JUnit `guide.training`: `CurriculumTest` (Aufbau, Ziele, Texte beider Sprachen mit Längen, Freischaltung, Ringe ohne
  Überlappung), `LessonRunTest` (alle Zielarten kinematisch, Fehlschläge, Zwischenzeiten, Skalierung), `PlacementTest`
  (freies Feld, Mauer → Drehung, eingeschlossen, Stufe/Loch unter dem Pad, nicht geladen), `TrainingProgressTest`,
  `LessonAutopilotTest` (alle zwanzig Lektionen mit dem echten Flugmodell geflogen, Whoop und Cinewhoop auf skalierten Kursen,
  Sterne-Kalibrierung, Nase und Vorwärtsflug passen zum Kurs).
- Selbsttest `guide.client.TrainingSelfTests`: Texte und Freischaltung, Fenster aus der Übersicht, echte FPV-Drohne an einem
  freien Ort, Kurs an der Drohne, Schweben im Kasten, Ringe mit gespeicherten Zwischenzeiten und neuem Versuch nach dem
  Landen, Abschalten in der Luft, Mauer → „Kein Platz“ und nach dem Abbau Kurs gesetzt, Taste M im Flug, Aufräumen, Zustand
  nach dem Verlassen. Dazu `guide.client.TrainingCourseSelfTests` mit Kursen aus Kapitel 3–5, im echten Gelände gesetzt
  (`Placement` mit dem Gelände des Clients) und mit der echten FPV-Drohne geflogen: zwei Runden durch die Gates von 3.3
  (`LapTracker`, zwei Rundenzeiten), ein echter Flip im Acro-Modus im Trick-Bereich von 4.1 (Hologramm gezeichnet), das
  liegende Tauchtor 5.1, die kleinen Präzisionsringe 5.2 ohne Berührung und die Abschlussprüfung 5.4 mit Ringen, Flip,
  Tauchtor und Landung auf dem Pad in 56 Blöcken (Pad auf dem Boden gefunden und gezeichnet).
- Bildschirmfotos (`guide.client.TrainingShowcase`, nur mit `-PpropwashScreenshots=true`): `training_screen.png`,
  `training_screen_de.png`, `training_hover_fpv.png`, `training_hover_fpv_de.png`, `training_yaw_fpv.png`,
  `training_rings_fpv.png`, `training_result_fpv.png`, `training_course_los.png`, `training_landing_los.png`,
  `training_gates_fpv.png`, `training_trick_zone_fpv.png`, `training_dive_gate_los.png`, `training_precision_fpv.png`,
  `training_final_exam_los.png`.
