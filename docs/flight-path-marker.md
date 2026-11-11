# Flugbahn-Marker

Für Spieler. Der Flugbahn-Marker (englisch *flight path marker* oder *velocity vector*) ist ein OSD-Element wie im
Head-up-Display eines Flugzeugs: ein kleiner Ring mit zwei Flügeln und einem Leitwerk, der im Kamerabild genau dort
sitzt, wohin sich die Drohne gerade wirklich bewegt. Das Fadenkreuz zeigt, wohin die Kamera schaut, der Marker zeigt,
wohin du fliegst. Mit Uptilt liegt er im Geradeausflug deshalb unter der Bildmitte, beim Driften seitlich daneben.

Technik und Entscheidungen: [`design/0.6.0/gameplay.md`](design/0.6.0/gameplay.md) Teil A.

## 1. Einschalten

- Konfigurator öffnen (`K`), Tab **OSD**, in der Elementliste **Flugbahn-Marker** anhaken. Standardmäßig ist er aus;
  nur die Vorlage **Voll / Telemetrie** schaltet ihn mit ein. Bestehende OSD-Layouts ändern sich nicht.
- Das Element ist fest (Symbol ⌖ in der Liste) und lässt sich nicht verschieben: Es steht immer dort, wohin die Drohne
  fliegt. Farbe und Größe lassen sich wie bei jedem Element einzeln einstellen; die Deckkraft folgt dem Regler
  **Linien-Deckkraft** wie Fadenkreuz und künstlicher Horizont.
- Die Vorschau im OSD-Editor zeigt ihn mit Beispielwerten: Die Beispiel-Drohne fliegt mit 18 m/s in Richtung ihrer
  Nase und sinkt leicht. Weil die Vorschau-Kamera 25° Uptilt hat, steht der Marker ruhig unter der Bildmitte, auch
  während sich die Beispiel-Drohne dreht und neigt.
- Der Tooltip über dem Eintrag in der Elementliste zeigt Name und Beschreibung (was er zeigt, unter 1,5 m/s
  ausgeblendet) und den Hinweis, dass er fest ist.

## 2. Was er zeigt

- **Im Bild:** Ring mit Flügeln und Leitwerk an der Stelle, wohin die Drohne sich bewegt – mit geradlinigem Bild wie
  mit Fisheye-Objektiv an der richtigen Stelle.
- **Außerhalb des Bilds oder hinter der Kamera** (etwa rückwärts oder fast senkrecht nach unten): gestrichelt am Rand,
  in der Richtung, in der die Bewegung liegt. Der Home-Pfeil am Rand ist ein Pfeil, der Marker bleibt ein Ring.
- **Langsam:** unter 1,0 m/s ist er aus (die Richtung beim Schweben ist nur Rauschen), zwischen 1,0 und 2,0 m/s wird er
  eingeblendet, ab 2,0 m/s ist er ganz da.
- **Videoverlust:** ohne Bild ist er aus, wie Fadenkreuz und Horizont.
- **Passend zum Bild:** Das FPV-Bild zeigt die Drohne mit der Latenz der Videostrecke. Der Marker nimmt deshalb die
  Bewegung der gezeigten Bilder (Differenz über 50 ms, leicht geglättet) und nicht die aktuelle Geschwindigkeit der
  Simulation; bei hoher Latenz läuft er dem Bild nicht voraus.

## 3. Beim Zuschauen

Schaust du mit der Brille per `F7` bei einer anderen Drohne zu und ist **OSD des Piloten zeigen** an (Tab
**Videokanal**), zeigt dein OSD den Marker für die fremde Drohne, berechnet aus den Bildern, die du siehst. Ob er
erscheint, bestimmt das OSD-Layout des Piloten, das beim Zuschauen übertragen wird (ohne übertragenes Layout dein
eigenes, `docs/spectate-feed.md`).

## 4. Grenzen

- Kein Vorhalt: Der Marker zeigt die Bewegung jetzt, nicht, wo die Drohne unter Schwerkraft in einer Sekunde ist.
- Nicht im Sichtflug-HUD, keine Add-on-Schnittstelle in 0.6.0.
- Nach einem Sprung der Drohne (Teleport, Respawn, Neustart des Bildes) beginnt die Rechnung neu; der Marker fehlt dann
  für einen Augenblick.

## 5. Englische Kernsätze (Website)

- The flight path marker is an OSD element that shows where your drone is really moving in the camera image.
- Turn it on in the OSD tab of the configurator (`K`); it is off by default and only part of the Full preset.
- With camera uptilt it sits below the centre in straight flight; when you drift, it moves sideways.
- Below 1 m/s it is hidden and fades in up to 2 m/s; outside the image it waits dashed at the edge.
- It follows the delayed FPV image, not the live simulation, so it matches what you see even with high latency.
- It also works while you watch another pilot with `F7`.
