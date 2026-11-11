# Kamerawinkel

Für Spieler. Der Kamerawinkel (Uptilt) sagt, wie weit die FPV-Kamera nach oben zeigt. Er gehört zur Drohne, nicht zur
Brille: Er gilt mit jeder Brille, beim Zuschauen anderer Spieler und in Aufnahmen, und er bleibt beim Zerlegen am
Rahmen. Mehr Winkel passt zu schnellerem Flug, weil sich die Drohne dabei weiter nach vorn neigt.

Technik und Entscheidungen: [`design/0.6.0/gameplay.md`](design/0.6.0/gameplay.md) Teil D,
[`design/realism/props-in-view.md`](design/realism/props-in-view.md) §9 (Bereiche der Kamerahalter).

## 1. Zwei Wege

- **K-Menü:** Konfigurator öffnen (`K`), Tab **Video**, Abschnitt **Kamera dieser Drohne**. Das geht mit der Drohne in
  der Hand und während du sie fliegst. Ohne Brille steht der Abschnitt unter dem Hinweis zur Brille, mit Brille unter
  den Zeilen zu Sichtfeld und Bildformat.
- **Wartungsmenü:** Schleichen + Rechtsklick auf die abgestellte Drohne, Leiste **Kamerawinkel**. Dort zeigt das Menü
  zusätzlich, wie viel Prozent des Bilds die Props verdecken.

Beide Wege ändern denselben Wert; was du im einen einstellst, steht im anderen.

## 2. Bedienung im K-Menü

- Regler **Kamerawinkel (Uptilt)** im Bereich des Kamerahalters: Freestyle 0–50°, Race 15–70°, Whoop 10–35°, Cinewhoop
  0–35°, Long Range 0–40°, Toothpick 10–40°, Cinelifter 0–30°, X-Class 0–50°; Kreativ-Drohnen 0–80°. Der Bereich
  schließt immer den Standard des Rahmens ein. Zahl tippen geht wie bei jedem Regler; ein Wert außerhalb wird mit
  Hinweis auf die Grenze gesetzt.
- `−` / `+`: 1° flacher oder steiler, mit gedrückter Schleichen-Taste (Shift) 5°.
- `↺`: zurück auf den Standard des Rahmens. Den Standard speichert die Drohne nicht, sie folgt dann dem Rahmen.
- Die Änderung wirkt wie die übrigen Werte des K-Menüs sofort: Auf einem Server kippt das FPV-Bild schon bei offenem
  Menü mit, bei der gehaltenen Drohne steht der Winkel gleich im Item. Im Einzelspieler pausiert das offene K-Menü das
  Spiel; der Winkel gilt dann, sobald du das Menü schließt. **Verwerfen** stellt den Winkel beim Öffnen des Menüs wieder
  her, **Standardwerte** im Tab Video setzt auch den Kamerawinkel zurück.
- Fliegst oder hältst du keine Drohne, ist die Zeile ausgegraut: „Fliege oder halte eine Drohne, um ihren Kamerawinkel
  zu ändern.“
- Die Fußzeile sagt, wo was gespeichert wird: Kamerawinkel in der Drohne, alles andere im Tab für alle Drohnen.
- Die Suche oben im K-Menü findet die Zeile mit „Kamerawinkel“, „uptilt“, „camera angle“, „Neigung“ oder „tilt“.

## 3. Auf Servern

Der Winkel geht über den bestehenden Tuning-Weg des K-Menüs an den Server; es gibt kein neues Netzpaket. Der Server
begrenzt jeden Wert auf den Halter des Rahmens, egal woher er kommt, und nimmt ihn wie die übrigen Tuning-Werte nur vom
Piloten der Drohne oder für die Drohne in der eigenen Hand an.

## 4. Grenzen

- Keine eigene Taste für den Kamerawinkel im Flug.
- Bei Kreativ-Drohnen hat auch der Tab **Drohnen-Editor** eine Zeile für den Uptilt. Änderst du in einem Besuch des
  K-Menüs beide, gilt beim Speichern der Wert aus dem Drohnen-Editor.

## 5. Englische Kernsätze (Website)

- How do I change the camera angle? Two ways: in the configurator (`K`), tab Video, section "Camera of this drone", or
  in the drone's maintenance menu (sneak + right-click the drone).
- The K menu works while you hold the drone or fly it; on a server the camera tilts live, in singleplayer when you close the menu.
- The slider stays inside the camera mount of the frame (for example 0–50° on a freestyle 5″, 15–70° on a race frame);
  `−`/`+` step 1°, with Shift 5°, `↺` returns to the frame default.
- The angle belongs to the drone: it applies with any goggles, for spectators and in recordings, and stays with the
  frame when you take the drone apart.
- Search the K menu for "camera angle", "uptilt" or "tilt" to jump straight to it.
