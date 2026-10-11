# VTX-Abschattung: Hindernisse in der Videostrecke (ab 0.5.0)

Wie stark Bäume, Häuser, Wälder, Wasser und Gelände das Videosignal zwischen Kopf des Spielers und Drohne dämpfen.
Rechnung als reines Java in `video.link` (keine Minecraft-Typen), Block-Einordnung im Client
(`video.client.ClientBlockLoss`), Anbindung in `video.client.VideoState`. Quellen und Materialwerte stehen in
[`realism/power-video.md`](realism/power-video.md) Abschnitt 4.5.

## Ausgangslage bis 0.4.0

Ein gerader Strahl vom Kopf zur Antenne plus vier abgeknickte Strahlen über einen festen Versatz (0,5–3 m) in der
Mitte, je Block 7 dB (fest, auch Holz), 1,2 dB (Laub), 3,5 dB (Flüssigkeit), 1,5 dB (dünn), gemittelt nach Leistung
mit Gewichten 2-1-1-1-1. Ein Baumstamm kostete so immer 7 dB, ein Mittelstrahl allein hinter einem dünnen Hindernis
kaum etwas (die anderen vier Strahlen gingen vorbei). Eine 7 × 6 m große Steinwand kostete ebenfalls nur 7 dB, wenn
die Strahlen nur einen Block Stein querten. Rückmeldung aus der Community: hinter einem Baum passiert bei typischen
Freestyle-Abständen nichts.

## Modell

1. **Material je Block** (`RfMaterial`, Einordnung `ClientBlockLoss.classify`, je Blockzustand zwischengespeichert):
   Laub, dünne Pflanzen, Stamm, Holzwand, Mauerwerk, Beton, Erde/Fels, Sand, Glas, Metall, Metallgitter, Wasser, Eis,
   Schnee, Stoff. Dämpfung je Meter aus den Materialdaten (ITU-R P.2040, P.527) und je Durchgang (Reflexion an den
   Grenzflächen, dünne Scheiben). Blöcke, die im Spiel für eine Wand stehen (Bretter, Ziegel, Beton), zählen mit einer
   typischen Wandstärke statt mit einem Meter Vollmaterial; Stämme, Gelände und Wasser zählen voll. Teilblöcke
   (Stufen, Treppen, Zäune, Scheiben, Schneeschichten) zählen nur dort, wo ihre Form liegt.
2. **Laub** sammelt je Strahl die Meter im Laub und rechnet sie über das Dual-Slope-Modell aus ITU-R P.833 (erst
   ≈ 1,16 dB/m, dann flacher, höchstens ≈ 50 dB wie im Wald), nicht linear je Block.
3. **Strahlen durch die Fresnel-Zonen** (`ObstructionScan`): jeder Strahl folgt einer Spindel vom Kopf zur Antenne,
   die überall um ν · r₁(t)/√2 seitlich versetzt ist (r₁ = Radius der ersten Fresnel-Zone bei 5,8 GHz, λ ≈ 5,17 cm).
   So liegt ein Strahl an jeder Stelle des Weges im selben Beugungsparameter ν, egal ob das Hindernis nah am Piloten,
   in der Mitte oder nah an der Drohne steht. Die Spindel ist ein Polygonzug mit 12 Stücken.
4. **Zwei Schnitte**: ein Strahl in der Sichtlinie, dann je Seite (rechts, links, oben, unten) Strahlen bei
   ν = 0,2 · 0,4 · 0,625 · 0,9 · 1,25 · 1,73 · 2,35 · 3,15 · 4,2 · 5,65 · 7,75 · 10,75 · 15 · 21,25 · 31,25. Eine Seite
   hört auf, sobald ab ν ≥ 0,75 zwei Strahlen hintereinander frei sind (freies Feld: 17 Strahlen, höchstens 61).
5. **Beugung**: jeder Schnitt ist ein eindimensionales Fresnel-Kirchhoff-Integral über Streifen (ITU-R P.526 §5.2,
   zusammengesetzte Blenden aus Rechtecken; Fresnel-Integrale nach Boersma, P.526 §2.7). Jeder Streifen trägt die
   Amplitude seines Strahls (10^(−Dämpfung/20)), was hinter dem letzten Strahl liegt, gilt als frei. Damit ergeben sich
   Messerkante (P.526 §4.1), schmale und breite Hindernisse und Öffnungen aus derselben Rechnung. Die Leistung jedes
   Schnitts wird über drei Lagen (Versatz −0,3 · 0 · +0,3 in ν, Gewichte 1-2-1) gemittelt: das ist das Ortsmittel aus
   P.526 §5.1 statt der Interferenzstreifen, die sich bei 5 cm Wellenlänge schon innerhalb einer Drohnenlänge abwechseln.
6. **Kombination** der beiden Schnitte X (waagerecht) und Y (senkrecht) mit der Leistung T des Mittelstrahls:
   `P = X · Y + (1 − T) · (X + Y − T)`. Bei freier Sichtlinie (T = 1) multiplizieren sich die Schnitte (exakt für
   Öffnungen und Kanten, die in einer Richtung durchgehen); im Schatten (T = 0) addieren sich die Wege um das Hindernis
   herum (Leistungssumme wie P.526 §5.1 und P.833 Gl. 16); eine gleichmäßige Wand mit Durchlass T ergibt genau T.
   Dämpfung = −10 · log₁₀ P, höchstens 60 dB. Sind alle Strahlen frei, ist sie exakt 0 dB.
7. **Signal** wie bisher in `LinkBudget`: Reserve 20 · log₁₀(Reichweite/Abstand) minus Dämpfung; Analog sauber ab
   +12 dB, verloren ab −6 dB, Digital sauber ab +4 dB, verloren ab −2 dB. Reichweiten und Schwellen sind unverändert,
   Analog zerfällt also weich über 18 dB (Rauschen, Farbverlust, Schnee), Digital hält länger und bricht dann über
   6 dB ab (Blöcke, Standbild, Verlust).

## Zahlen vorher und nachher

JUnit `ShadowScenariosTest` rechnet beide Modelle in nachgebauten Szenen (Pilotenauge 1,62 m, Eiche nach Vanilla-Form
mit 5 Blöcken Stamm und Krone ab 2 m, Haus 7 × 7 × 4 mit Fenstern, Wald aus Eichen im 5-m-Raster). Signal = Qualität
0 … 1 für `analog_race` (Reichweite 380) und `digital_hd` (520); Stufen: ≥ 0,8 sauber, 0,6–0,8 Glitches, 0,4–0,6
Farbverlust, 0,1–0,4 Schnee, < 0,1 Rauschen.

| Szene | Abstand | vorher | nachher | Analog | Digital |
|---|---|---|---|---|---|
| 1 Baum, Drohne 1,5 m hoch 5 m dahinter (Stamm) | 20 m | 7,0 dB | 13,7 dB | 1,00 → 1,00 | 1,00 → 1,00 |
| 1 Baum, Stamm | 50 m | 7,0 dB | 13,8 dB | 0,98 → 0,57 | 1,00 → 1,00 |
| 1 Baum, Stamm | 100 m | 7,0 dB | 13,8 dB | 0,63 → 0,12 | 1,00 → 0,39 |
| 1 Baum, Drohne 3,5 m hoch (Stamm und Krone) | 20 m | 11,9 dB | 18,6 dB | 1,00 → 0,81 | 1,00 → 1,00 |
| 1 Baum, Stamm und Krone | 50 m | 11,8 dB | 18,3 dB | 0,73 → 0,21 | 1,00 → 0,75 |
| 1 Baum, Stamm und Krone | 100 m | 11,8 dB | 18,2 dB | 0,24 → 0,00 | 0,85 → 0,00 |
| 1 Baum, nur Krone (1,5 m neben dem Stamm) | 50 m | 5,5 dB | 4,8 dB | 1,00 → 1,00 | 1,00 → 1,00 |
| 1 Baum, nur Krone | 100 m | 5,5 dB | 4,7 dB | 0,75 → 0,80 | 1,00 → 1,00 |
| Steinwand 7 × 6 m, 5 m vor der Drohne | 50 m | 7,0 dB | 28,8 dB | 0,98 → 0,00 | 1,00 → 0,00 |
| Holzhaus mit Glasfenstern, Drohne 3,5 m dahinter | 30 m | 14,0 dB | 11,0 dB | 0,88 → 0,99 | 1,00 → 1,00 |
| Holzhaus | 60 m | 14,0 dB | 11,6 dB | 0,42 → 0,61 | 1,00 → 1,00 |
| Holzhaus | 100 m | 14,0 dB | 11,7 dB | 0,10 → 0,25 | 0,33 → 0,86 |
| Haus aus Bruchstein mit Glasfenstern | 30 m | 14,0 dB | 18,9 dB | 0,88 → 0,51 | 1,00 → 1,00 |
| Haus aus Bruchstein | 60 m | 14,0 dB | 21,1 dB | 0,42 → 0,01 | 1,00 → 0,00 |
| Haus aus Bruchstein | 100 m | 14,0 dB | 20,1 dB | 0,10 → 0,00 | 0,33 → 0,00 |
| Wald, Drohne 15 m tief, 2,5 m hoch | 50 m | 10,7 dB | 29,6 dB | 0,81 → 0,00 | 1,00 → 0,00 |
| Wald | 100 m | 7,0 dB | 18,9 dB | 0,63 → 0,00 | 1,00 → 0,00 |
| freies Feld, Drohne 10 m hoch | 20–760 m | 0 dB | 0 dB | unverändert | unverändert |
| freies Feld, Drohne 2 m hoch | 300 m | 0,8 dB | 0 dB | 0,36 → 0,42 | 1,00 → 1,00 |

Vorbeiflug hinter dem Stamm (Drohne 5 m hinter der Eiche, 1,5 m hoch), seitlicher Versatz der Drohne, vorher → nachher
(JUnit `flyingPastATrunkFadesInAndOutAndNearMissesAlreadyCost`):

| Abstand | 0 m | 0,25 m | 0,5 m | 0,75 m | 1,0 m | ab 1,5 m |
|---|---|---|---|---|---|---|
| 20 m | 7,0 → 13,7 dB | 7,0 → 13,0 dB | 4,8 → 9,4 dB | 0,6 → 4,5 dB | 0,5 → 0,1 dB | 0 → 0 dB |
| 50 m | 7,0 → 13,8 dB | 7,0 → 10,9 dB | 4,8 → 6,2 dB | 0,2 → 1,6 dB | 0 → 0 dB | 0 → 0 dB |
| 100 m | 7,0 → 13,8 dB | 7,0 → 10,9 dB | 4,8 → 6,2 dB | 0 → 1,5 dB | 0 → 0 dB | 0 → 0 dB |

Bei 0,75 m Versatz geht der Mittelstrahl schon am Stamm vorbei, die erste Fresnel-Zone streift ihn aber noch: knapp
vorbei kostet 1,5–4,5 dB, erst ab gut 1 m ist die Strecke wieder frei. Die Dämpfung steigt und fällt gleichmäßig, ohne
Sprünge über 4 dB je 5 cm Versatz.

Runde ums Haus aus Bruchstein (7 m um die Mitte, Drohne 2 m hoch, alle 45°, 0° genau hinter dem Haus; JUnit
`circlingAHouseShadowsOnlyTheFarSide`): bei 30 m vorher 14,0 · 2,0 · 0 · 0 · 0 · 0 · 0 · 2,0 dB, nachher 18,9 · 3,8 · 0 ·
0 · 0 · 0 · 0 · 3,8 dB; bei 60 m vorher 14,0 dB und sonst 0, nachher 21,1 dB und sonst 0. Auf der Seite des Piloten und
seitlich bleibt die Strecke frei.

Wald (Eichen im 5-m-Raster, Drohne 15 m tief und 2,5 m hoch, 21 Stellen quer im Abstand von 1 m; JUnit
`forestShadowDependsOnWhereTheDroneHides`), kleinster / mittlerer / größter Wert:

| Abstand | vorher | nachher |
|---|---|---|
| 50 m | 5,8 / 11,3 / 22,6 dB | 3,4 / 22,1 / 32,1 dB |
| 100 m | 3,6 / 10,8 / 30,3 dB | 1,7 / 16,6 / 37,6 dB |

Ein Wald ist kein gleichmäßiger Block: zwischen den Stämmen unter den Kronen bleibt das Bild lange brauchbar, direkt
hinter einem Stamm ist es weg. Die Zeile „Wald“ in der Tabelle oben ist so eine Stelle (Stamm 1 m vor der Drohne).

Abgleich mit Messungen bei 5,85 GHz (Durgin, Rappaport, Xu 1998): Laubbäume 10–13 dB, Nadelbaum-Reihe 11–16 dB,
kleiner Laubbaum 3,5 dB, Ziegel-Außenwand 10,2 dB, Innenwand 3,6–5,6 dB, Abschattung durch ein ganzes Haus im
Beispiel 10,8 dB (mit Streuung aus der Nachbarschaft). Die Minecraft-Eiche mit 1 m dickem Stamm liegt am oberen Rand,
ein Haus ohne Nachbarhäuser etwas darüber, weil es im Spiel keine Streuwege aus der Umgebung gibt.

Die Reichweiten im Spiel sind kürzer als echte (Analog 380, Digital 520 Blöcke), die Reserve bei gleichem Abstand
also kleiner; dieselbe Dämpfung wirkt deshalb im Spiel früher als draußen. Das ist gewollt: Abstände bleiben
spielbar, Hindernisse haben echte Dezibel.

## Leistung

- Freies Feld: 17 Strahlen je Runde, sonst bis 61; jeder Strahl läuft per DDA durch die Blockzellen, leere
  Chunk-Abschnitte (`LevelChunkSection.hasOnlyAir`) kosten nur den Schritt. Mit festem Durchgang (≥ 60 dB) endet ein
  Strahl früh.
- Budget 24 000 Zellen je Client-Tick; reicht es nicht, läuft die Runde im nächsten Tick mit derselben Geometrie
  weiter. In den Szenen oben braucht eine Runde 550–6 500 Zellen (100 m), im freien Feld bei 600 m rund 10 500.
- Messung im Selbsttest „video: Abschattung hinter Baum, Haus und Wald, Vorbeiflug und Runde ums Haus“ (Fabric und
  NeoForge, echte Chunks, eine Runde je Tick, sechs Läufe vom 03.10. und 05.10.2026): im Durchschnitt 0,03–0,06 ms je
  Tick, höchstens 0,12–0,74 ms; die größte Runde hatte 4323 Zellen (Baum bei 100 m, 45 Strahlen), mit der weitesten
  Stellung bei 90 m 3949 Zellen (Steinhaus bei 60 m, 58 Strahlen); im freien Feld 17 Strahlen. Das Budget von 24 000
  Zellen wird in diesen Szenen nie erreicht.

## Selbsttest im Spiel

Der Schritt „video: Abschattung hinter Baum, Haus und Wald, Vorbeiflug und Runde ums Haus“ baut 400 Blöcke neben dem
Spieler dieselben Szenen wie `ShadowScenariosTest` (Eiche, Haus aus Bruchstein mit Fenstern, Wald mit demselben Raster
und Versatz wie bei 50 m im JUnit) und stellt Pilot und Drohne nacheinander auf 26 Stellungen: Baum bei 20/50/90 m,
Haus bei 30/60 m, Wald bei 50 m, freies Feld bei 90 m, Vorbeiflug am Baum bei 50 m in 11 seitlichen Stellungen und eine
Runde ums Haus bei 60 m in 8 Stellungen. Jede Stellung muss im Spiel innerhalb von ±1,5 dB den Wert des reinen Modells
treffen (Einordnung der Blöcke, Formen, Chunk-Zugriff und Strahlen zusammen), freie Stellungen exakt 0 dB, „knapp
vorbei“ bei freiem Mittelstrahl mehr als 0,5 dB; das Signal muss zur Dämpfung passen. In den Läufen vom 03.10. und 05.10.2026 trafen alle 26 Stellungen den
Modellwert auf 0,1 dB genau. Die weiteste Stellung liegt bei 90 m, weil der Selbsttest mit 6 Chunks Sichtweite läuft:
der Server zeigt dem Client eine Drohne ohne Zuschauen nur bis 6 · 16 = 96 Blöcke (`ChunkMap.TrackedEntity`); die
Bildschirmfotos bei 100 m laufen über Zuschauen, das die Sicht an der Drohne verankert.

Bildschirmfotos (`-PpropwashScreenshots=true`, Zuschauen auf die Testdrohne mit passender Brille): `vtx_shadow_tree_overview`
(Aufbau schräg von hinten: Drohne vorn, dahinter die Eiche, der Pilot weit hinter dem Baum), `vtx_shadow_tree_20/50/100_analog`, `vtx_shadow_tree_50/100_digital` (Analog bei 50 m
Farbverlust, bei 100 m Schnee; Digital bei 50 m sauber, bei 100 m Blöcke), `vtx_shadow_house_40_analog/digital` (19,3 dB:
Analog Schnee, Digital fast sauber) und `vtx_shadow_forest_30_analog/digital` (22,6 dB: Analog Schnee, Digital Glitches).

## Mehrspieler und Zuschauen

Jeder Client rechnet vom eigenen Kopf zur Antenne der Drohne, deren Bild er empfängt (eigene FPV-Drohne oder beim
Zuschauen die beobachtete), mit den Blockzuständen seiner Welt; Block-Tags kommen vom Server. Es gibt keine neuen
Daten im Netz, das Netzprotokoll und die API bleiben gleich. Zwei Spieler am selben Ort sehen dieselbe Dämpfung.

## Grenzen

- Der Client rechnet nur mit Chunks, die er hat. Im FPV und beim Zuschauen verankert der Server die Sicht an der Drohne
  (`ViewAnchors`); liegt der Pilot weiter als die Sichtweite von der Drohne entfernt, fehlen dem Client die Chunks beim
  Piloten, und Hindernisse direkt neben ihm zählen nicht (fehlende Chunks gelten als Luft, wie schon bis 0.4.0). Bei
  üblichen Freestyle-Abständen unter der Sichtweite (8 Chunks = 128 Blöcke) betrifft das nichts.
- Die Hindernisse aller Tiefen werden in die Ebene der Fresnel-Zonen projiziert (wie eine einzige Blende). Mehrere
  Kanten hintereinander (Deygout, Epstein-Peterson) sind nicht eigens gerechnet; Strahlen, die durch beide gehen,
  addieren ihre Dämpfung.
- Hindernisse, die nur schräg neben der Sichtlinie liegen und keinen der beiden Schnitte berühren, zählen nicht; nach
  P.526 kosten sie ohnehin unter etwa 1 dB.
- Keine Reflexionen und Streuwege; die Mehrwege-Störungen im Bild (`Multipath`) bleiben unverändert und nutzen
  weiter die alten Reflexionswerte der Blöcke.
- Das Laubmodell aus P.833-4 wurde mit Messungen von 9,6–57,6 GHz gebaut; bei 5,8 GHz passt die Anfangssteigung zu
  den Extinktionswerten aus P.833-10 (Tabelle 8, 5,5 GHz: 0,2–0,56 /m ≈ 0,9–2,4 dB/m).
- Wandstärken für Holz (0,15 m), Mauerwerk (0,35 m) und Beton (0,25 m) sind gesetzt, nicht gemessen; Mauerwerk trifft
  damit die gemessene Ziegelwand (10,2 dB), Holz liegt bei den Innenwänden.

## Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Zwei Schnitte durch die Fresnel-Zonen mit 1D-Fresnel-Kirchhoff je Schnitt | exakt für Kanten und Streifen, wenige Strahlen (17 im freien Feld) | volles 2D-Raster (100–150 Strahlen, Treppenstufen von 2–4 dB beim Vorbeifliegen, Poisson-Fleck hinter runden Kronen) |
| Ortsmittel über ±0,3 ν | Interferenz wechselt bei 5 cm Wellenlänge schneller, als die Drohne sich bewegt | Punktwert (springt zwischen Auslöschung und Verstärkung) |
| Kombination X · Y + (1 − T)(X + Y − T) | exakt für die Grenzfälle (frei, Kante, Streifen, Öffnung, gleichmäßige Wand), Schatten wie P.526 §5.1 | reine Leistungssumme (Öffnungen falsch), reines Produkt (Schatten falsch) |
| Laub nach P.833-4 §3.2 mit Deckel aus P.833-10 §2.1 | einzige ITU-Formel für einzelne Vegetation über 5 GHz | lineare dB je Block (Wald zu teuer) |
| Bauwände mit typischer Wandstärke, Gelände und Stämme voll | Ziegelwand trifft die Messung, ein Hügel bleibt undurchdringlich | ein Meter Vollmaterial je Block (jedes Haus wäre ein Bunker) |
| Fenster als Glasscheibe 6 mm je Durchgang (≈ 3,2 dB) | Glasblöcke stehen im Spiel für Fenster | 1 m Glas (25 dB) |
| Reichweiten, Schwellen, Mehrwege unverändert | Kalibrierung je VTX bleibt, nur Hindernisse ändern sich | Schwellen an das neue Modell anpassen |
| Budget je Tick, Runde über mehrere Ticks | kein Ruckeln bei großen Abständen | feste Strahlzahl je Tick wie bisher |
| Selbsttest-Szenen gleich gebaut wie die JUnit-Szenen, Prüfung gegen die Modellwerte | prüft Einordnung, Formen und Chunk-Zugriff im echten Spiel gegen dieselbe Rechnung | nur grobe Bereiche je Szene (fängt falsche Einordnung nicht) |
| Wald als Verteilung über 21 Stellen angeben | die Dämpfung hängt stark davon ab, ob ein Stamm vor der Drohne steht | eine einzelne Stelle als „der Wald“ |
| Blockform, die ohne Welt nicht abfragbar ist, zählt als ganzer Block | Blöcke anderer Mods dürfen die Rechnung nicht abbrechen | Block überspringen (wäre Luft) |
