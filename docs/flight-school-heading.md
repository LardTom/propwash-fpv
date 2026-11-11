# Flugschule: Drohnen fliegen mit der Kamera voraus

In allen Szenen der Flugschule fliegt die Drohne mit der Kamera voraus, die Antennen zeigen nach hinten. Die Lage der
Szenen-Drohnen (Minecraft-Yaw der Kamera, `pitch` positiv = Nase runter, `roll` positiv = rechte Seite hoch) und
`SceneKit.heading`/`fly` beschreibt `docs/FOUNDATION.md` im Abschnitt Flugschule unter „Richtungen“.

## Für Spieler

- Rennen, Kurs-Elemente, FPV und Sichtflug, Action-Cam, Greifer, Rescue, Halten, Absturz, Tricks, Bandos und die
  Gate-Hinweise zeigen die Drohne im Flug mit der Kamera vorn, geneigt in Flugrichtung und in Kurven zur Innenseite.
- Auch Wege ohne Neigung fliegen vorwärts: die Runde über der Werkbank, Start und Flug in die Höhle beim Halten, der
  Whoop im Überleben und in den Tricks die Wege zum Startpunkt, zurück zum Schwebepunkt und aus der Guillotine. Vor dem
  nächsten Trick dreht die Drohne auf der Stelle zur Mauer.
- Ebenso Steig- und Sinkflüge: die Landung am Ende von FPV und Sichtflug, beim Absturz das Abheben nach dem Aufrichten
  und nach dem Turtle-Modus und der Weg zur Startlinie (erst dort dreht die Drohne in Flugrichtung der Reichweiten-Runde),
  in den Tricks der Steigflug vor den Spins und vor der Guillotine (die Drohne dreht zuerst und steigt dann).

## Lage von Hand setzen

- Für einen Flugabschnitt `SceneKit.fly(…)` nehmen; es setzt Kurs, Vorneigung und Schräglage je Abschnitt.
- Von Hand: Yaw = `SceneKit.heading(von, nach)`, auch beim Abheben und Landen mit kleinem waagerechtem Anteil. Die
  Drehung beginnt mit dem Weg und ist nach wenigen Ticks fertig (meist 8), solange die Drohne bei `Ease.IN_OUT` noch
  langsam ist; eine Drehung über die ganze Wegdauer lässt sie in der Mitte des Wegs seitlich oder rückwärts fliegen.
  Eine Pose, die im alten Sinn geschrieben war (Kompasskurs, Nase hoch positiv), wird zu `(yaw + 180, −pitch, −roll)`:
  gleiche Neigung, die Kamera zeigt in die andere Richtung.
- Soll die Drohne nach einem Weg in eine andere Richtung schauen, erst ankommen, dann drehen (Tricks: `RIGHT` zur Mauer).

## Prüfung

- JUnit `guide.FlightSchoolDirectionTest`: Kamera, Neigung und Schräglage in jedem geneigten Vorwärtsflug, Aufprall mit
  der Nase, Flip und Power Loop, Knüppel im Konfigurator.
- JUnit `guide.FlightSchoolHeadingTest#dronesFlyCameraFirstEvenWithoutTilt`: alle Szenen aus `GuideScenes.all()` sowie
  `gate_menu` und `bandos`, jeden Tick, auch ohne Neigung und auch beim Steigen und Sinken. Geprüft wird jede
  eingeschaltete, aufrechte Drohne, die waagerecht schneller als 0,03 Blöcke je Tick fliegt und nicht steiler als 75°
  steigt oder sinkt. Rückwärts heißt: die Kamera zeigt mehr als 105° neben die Flugrichtung (Skalarprodukt unter −0,25),
  drei Ticks am Stück oder länger; einzelne Ticks mitten in einer Drehung oder an einer Kehre zählen nicht (Absetzen
  beim Greifer, oberes Ende des Schachts bei den Bandos). Fast senkrechte Wege (Sturzflug im Rennen) zählen nicht, dort
  folgt die Gier dem nächsten Flugstück. Ausnahmen mit Zeitfenster und Grund: Juicy Flick (die Nase zeigt nach dem Trick
  zurück, der Schwung trägt weiter), GPS-Halt (Rückkehr zum Platz ohne Gieren), Pendeln nach dem Abheben beim Binden.
  Eine Ausnahme ohne Rückwärtsprobe lässt den Test scheitern.
- Selbsttest `guide:` auf Fabric und NeoForge; Bildschirmfoto `flight_school_fly` (Runde über der Werkbank) neu erzeugt.
