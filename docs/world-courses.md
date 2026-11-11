# Rennkurse in der Welt (0.6.0)

Für Spieler. Beim Erkunden findest du fertige Rennkurse mit Startfläche, Tafel und Truhe. Seit 0.6.0 nutzen die Kurse in
neu erzeugten Gebieten die neuen Elemente des Rennsystems: Schleifen und Rückwärts-Durchflüge, Slalomstangen, Flaggen und
Gate-Stangen, die bis zum Boden werten, einen Tauchwürfel, eine Gate-Reihe mit gemeinsamem Pfosten und einen Kurs von
Punkt zu Punkt. Technik und Balance: [`design/world-courses.md`](design/world-courses.md).

## Die vier Kurse

| Kurs | Wo | Was dich erwartet |
|---|---|---|
| **Feldslalom** (Einsteiger) | Ebene, Sonnenblumenebene, Wiese | Drei Slalomstangen im Wechsel, eine Haarnadel durch zwei große Gates mit gemeinsamem Pfosten (links hinaus, rechts zurück), drei Gates auf dem Rückweg. ![Feldslalom](screenshots/world_course_field_slalom.png) |
| **Canyon-Run** (Mittel) | Tafelberge, Wüste | **Von Punkt zu Punkt**: Start in der linken Schlucht, zwei Slalomstangen, unter dem Felsbogen durch, um die Wendeflagge an der Spitze des Grats und durch die rechte Schlucht zum Ziel-Gate. Die Zeit läuft vom Start bis zum Ziel. ![Canyon-Run](screenshots/world_course_canyon_run_badlands.png) ![Canyon-Run in der Wüste](screenshots/world_course_canyon_run_desert.png) |
| **Waldslalom** (Mittel) | Wälder, Birkenwälder, Taiga | Ein **Pfosten-Slalom** aus drei hohen Gate-Stangen (rot, weiß, rot), danach um die Baumstämme durch sechs Gates. ![Waldslalom](screenshots/world_course_forest_weave_oak.png) ![Birke](screenshots/world_course_forest_weave_birch.png) ![Fichte](screenshots/world_course_forest_weave_spruce.png) |
| **Drohnen-Arena** (die schwerste, selten) | Ebene, Savanne, verschneite Ebene | Durch ein Gate hinaus, um den Zeitmessturm mit der Flagge oben drauf und **rückwärts** durch dasselbe Gate zurück; Sockel-Gate; **Power-Loop** zweimal durch das große Gate; die Leiter aus drei Gates im Zickzack nach oben; im Sturzflug von oben in den **Tauchwürfel**. ![Drohnen-Arena](screenshots/world_course_drone_arena.png) |

- Mit dem Kurs-Werkzeug in der Hand zeigen die Namensschilder die Nummern, Rückwärts-Durchflüge mit ←, z. B. „2 · 4←“
  am Gate der Stichstrecke in der Arena; das nächste Ziel leuchtet grün wie bei jedem Kurs.
- Die Flagge auf dem Zeitmessturm zählt **bis zum Boden**: du fliegst um den Turm herum, nicht über die Flagge.
- Gates und Stangen im Feldslalom und im Waldslalom stehen bündig auf dem Trampelpfad (1/16 Block tiefer als
  ein voller Block).

## Kurs-ID und Bestenliste

Jeder gefundene Kurs ist ein eigener Kurs mit eigener Nummer, eigener Kurs-ID und eigener Bestenliste – auch wenn du
zwei Kurse derselben Art findest. Die Tafel an der Startfläche zeigt Name, Nummer und Kursrekord.

## Alte Welten

Kurse, die deine Welt schon erzeugt hat, bleiben, wie sie sind – mit Gates, Bestenliste und Rekord. Auch ein Kurs, der vor
0.6.0 nur zum Teil erzeugt wurde (am Rand des Erkundeten), wird im alten Aufbau fertig gebaut. Die neuen Kurse erscheinen
in Gebieten, die du ab 0.6.0 zum ersten Mal betrittst.
