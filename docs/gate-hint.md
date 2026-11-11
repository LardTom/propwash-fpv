# Gate-Hinweis und Kapitel „Gate-Menü“

Für Spieler. Wer zum ersten Mal ein Gate setzt, weiß oft nicht, dass sich Größe, Drehung und Redstone-Signal im Spiel
einstellen lassen. Deshalb erscheint nach dem ersten Gate in einer Welt ein kleines Fenster, und die Flugschule hat ein
eigenes Kapitel „Gate-Menü“.

Technik und Entscheidungen: [`design/0.6.0/gameplay.md`](design/0.6.0/gameplay.md) Teil E, Verträge in
[`FOUNDATION.md`](FOUNDATION.md) (Abschnitt „Gate-Hinweis und Kapitel „Gate-Menü““).

## 1. Wann der Hinweis kommt

- Nach dem **ersten Gate oder Freiform-Gate**, das du in einer Welt setzt – auch wenn du es auf ein anderes Gate stapelst.
  Wendeflagge, Slalomstange, Tauchwürfel und Gate-Stangen lösen ihn nicht aus.
- **Einmal je Welt und Spieler.** Welt heißt im Einzelspieler der Ordner des Spielstands, im Mehrspieler die
  Server-Adresse. Gespeichert wird auf deinem Rechner (`config/propwash/gate_hint.json`), nicht auf dem Server.
- Auf einem Server mit Verzögerung kommt das Fenster, sobald das Gate bei dir erscheint (bis 1,5 s nach dem Klick).
- Steht gerade ein anderes Fenster offen (Inventar, Chat), wartet der Hinweis bis zu 10 s, bis es zu ist.

## 2. Das Fenster

- Oben die Tasten deiner echten Belegung: **Schleichen + Rechtsklick** (oder was du dafür eingestellt hast), darunter der
  Satz, was das Gate-Menü kann.
- **Kapitel in der Flugschule öffnen:** zeigt sofort die Lektion „Gate-Menü“. Danach kommt der Hinweis in keiner Welt
  wieder.
- **Nicht wieder anzeigen:** Haken setzen, dann „Verstanden“ – der Hinweis kommt in keiner Welt wieder.
- **Verstanden** (oder Esc) ohne Haken: in dieser Welt ist Ruhe, in der nächsten Welt kommt er einmal.
- Im Einzelspieler hält das Fenster das Spiel an, im Mehrspieler nicht.

## 3. Zurückholen

Konfigurator öffnen (`K`), Tab **Flugschule**, Knopf **Hinweise zurücksetzen**. Danach erscheinen alle Hinweise für das
erste Mal wieder: der Gate-Hinweis in jeder Welt einmal, und der Tipp zur freien Maus im Gate-Menü pulsiert wieder die
ersten drei Male.

## 4. Kapitel „Gate-Menü“ in der Flugschule

Fünf Schritte, auch über die Taste der Flugschule auf einem Gate, Freiform-Gate oder dem Kurs-Werkzeug im Inventar:

1. **Gate-Menü öffnen:** Schleichen + Rechtsklick auf ein gesetztes Gate, mit einem Gate oder dem Kurs-Werkzeug in der
   Hand.
2. **Größe und Drehung:** Mausrad ändert die gewählte Zeile (Gieren, Nicken, Rollen, Breite, Höhe), Schleichen + Mausrad
   wählt die Zeile, Alt (Mac: Wahltaste) gedrückt halten gibt die Maus frei, Enter übernimmt, Esc bricht ab.
3. **Tab Redstone:** Signal an oder aus, Pulslänge 2 bis 100 Ticks und „Nur im Rennen“. Ein Durchflug in Flugrichtung
   versorgt den Block unter dem Gate – in der Lektion leuchtet dort eine Redstone-Lampe.
4. **Tab Position:** das Gate rastet in Schritten von 1/4 Block nach Osten und zum Piloten, mit Strg um 1/16 Block;
   Hinweise zu X, Y und Z (bis 8 Blöcke), zum Mausrad (ein Schritt je Raste) und dazu, dass die Pfeiltasten dem Blick
   folgen und Bild↑/↓ hoch und runter verschiebt.
5. **Freiform-Gates:** dasselbe Menü mit Flugrichtung und Redstone-Signal; die Blöcke um die Öffnung sind der Rahmen.

Das ältere Kapitel „Gates frei drehen“ bleibt unverändert.

## 5. Kernsätze für die Website (Englisch)

- After your first gate in a world, a small window explains the gate menu: sneak + right-click the gate with a gate item
  or the course tool to change size, rotation and the redstone signal.
- It appears once per world and player; “Don't show again” or “Open flight school chapter” turns it off for all worlds.
- “Reset hints” in the Flight school tab of the K menu brings all first-time hints back.
- New flight school chapter “Gate menu: size, rotation, redstone”: opening the menu, the mouse wheel controls, the
  Redstone tab with a lamp that lights on a pass, the Position tab for small steps, and freeform gates.
