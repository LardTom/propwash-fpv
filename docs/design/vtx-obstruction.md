# VTX-Abschattung: Hindernisse in der Videostrecke (ab 0.5.0)

Wie stark Bäume, Häuser, Wälder, Wasser und Gelände das Videosignal zwischen Kopf des Spielers und Drohne dämpfen.
Rechnung als reines Java in `video.link` (keine Minecraft-Typen), Block-Einordnung im Client
(`video.client.ClientBlockLoss`), Anbindung in `video.client.VideoState`. Quellen und Materialwerte stehen in
[`realism/power-video.md`](realism/power-video.md) Abschnitt 4.5. Seit 0.5.2 hebt eine höhere Sendeleistung die Reserve
auf jedem Weg um 10·log10(P / P_Nenn) dB ([`vtx-power.md`](vtx-power.md)).

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
   Grenzflächen, dünne Scheiben). Seit der Nachbesserung in 0.5.2 zählt ein Block Baumaterial wie eine Hauswand (Werte
   im Abschnitt [Nachbesserung in 0.5.2](#nachbesserung-in-052-spieltest-hauswand-statt-bunker)); Wasser, Metall, Eis
   und Schnee rechnen weiter mit ihren Materialdaten. Teilblöcke (Stufen, Treppen, Zäune, Scheiben, Schneeschichten)
   zählen nur dort, wo ihre Form liegt.
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
7. **Signal** in `LinkBudget`: Reserve 20 · log₁₀(Reichweite/Abstand) minus Dämpfung; Analog sauber ab +12 dB,
   verloren ab −6 dB (zerfällt weich über 18 dB: Störstreifen, Farbverlust, Schnee, Rauschen). Digital bis 0.5.2 vor
   dem Spieltest sauber ab +4 dB, verloren ab −2 dB (6 dB), seit der Nachbesserung sauber ab +7 dB, verloren ab −5 dB
   (12 dB, Stufen im Abschnitt [Nachbesserung in 0.5.2](#nachbesserung-in-052-spieltest-hauswand-statt-bunker)).

## Zahlen vorher und nachher (Stand 0.5.0)

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

## Nachbesserung in 0.5.2 (Spieltest): Hauswand statt Bunker

Rückmeldung aus dem Spieltest vor dem Release: digital wird das Bild hinter einem Block oder einer dicken Wand ohne
Ruckeln sofort schwarz, analog bricht es genauso schnell weg. Ein Meter Wand lässt draußen kaum Signal durch, im Spiel
soll ein Block aber wie eine normale Hauswand zählen. Ursachen im Modell: Stein, Erde und Tiefenschiefer zählten als
Gelände mit einem Meter Vollmaterial (≈ 286 dB je Block), Beton mit 25 cm Vollbeton (34 dB), Stämme mit einem Meter Holz
(36 dB); Digital fiel über nur 6 dB Reserve von sauber auf schwarz; die Steuerstrecke kannte keine Hindernisse.

### Material: ein Block Baumaterial ist eine Wand

`RfMaterial.Wall`: Dämpfung eines geraden Durchgangs durch einen ganzen Block, aufgeteilt in die Grenzflächen aus den
Materialdaten (P.2040) und den Rest je Meter. Ein Block steht im Spiel für eine Wand, nicht für einen Meter Vollmaterial.

| Material (Blöcke) | bis 0.5.2 vor dem Spieltest | jetzt | Bezug |
|---|---|---|---|
| Bretter, Holztüren, Holzstufen (`TIMBER`) | 5,6 dB | 4,5 dB | Holz- und Leichtbauwände 3–6 dB |
| Mauerwerk: Bruchstein, Ziegel, Steinziegel, glasierte Terrakotta, geschnittener Sandstein, Endsteinziegel und alles Unbekannte (`MASONRY`) | 10,1 dB | 7,0 dB | Ziegelwände 5–10 dB, ganzes Haus gemessen 10,8 dB (Durgin 1998) |
| Erde, Stein, Tiefenschiefer, Erze, Kies, Terrakotta (alle Farben), Sandstein, roter Sandstein, Endstein (`EARTH`) | 286 dB | 8,0 dB | wie Mauerwerk, Gelände summiert sich über die Blöcke |
| Sand (`SAND`) | 12,5 dB | 8,0 dB | wie Erde |
| Beton (`CONCRETE`) | 34,1 dB | 9,0 dB | obere Hälfte der Hauswände (5–12 dB) |
| Stamm und Holz (`TRUNK`) | 36,1 dB | 12,0 dB | so gewählt, dass die einzelne Eiche bei ihren 13,9 dB bleibt (0.5.0: 13,8 dB, gemessen 10–13 dB) |
| Wolle, Teppich, Betten (`CLOTH`) | 9,9 dB | 2,0 dB | Stoff, viel weniger als eine Wand |
| Glas (Block oder Scheibe) | 3,2 dB | 3,2 dB | unverändert (6 mm Scheibe) |
| Laub, Pflanzen, Eis, Schnee | unverändert | unverändert | P.833, P.527 |
| Metall, Wasser | undurchdringlich | undurchdringlich | unverändert |

**Lauf aus Baumaterial höchstens zwei Wände** (`RfMaterial.maxRunMeters`, `RayLoss#add`): Ein Strahl, der ohne Lücke
durch dasselbe Baumaterial läuft (Holz, Mauerwerk, Beton, Stamm, Stoff), zählt höchstens 2 m davon. Eine echte Wand ist
gut 30 cm dick; ein Block-Wand-Strahl, der flach an einer Hausecke entlang durch die Seitenwand läuft, kam vorher auf
sieben Wände (43 dB) und machte gerade die Ecke schlechter als die Mitte hinter dem Haus. Gelände (Erde, Stein, Sand),
Wasser und Metall summieren sich weiter: 8 Blöcke Erde ≈ 41 dB, 12 Blöcke ≈ 60 dB (Deckel), ein Hügel oder eine Höhle
schneidet die Strecke also weiter ab. Getrennte Wände (Bando mit drei Räumen) zählen jede für sich.

**Gelände aus „Baustein“-Blöcken zählt als Gelände** (`ClientBlockLoss#material`): Terrakotta (Tag `terracotta`, also
auch die Tafelberge der Badlands), Sandstein und roter Sandstein (Wüsten, Strände, Badlands) und Endstein (End-Inseln)
klingen wie Stein und fielen früher in den Standardfall Mauerwerk. Mit der Laufgrenze zählte ein beliebig dicker
Tafelberg dann wie zwei Hauswände (13 dB statt 60 dB). Sie zählen jetzt als `EARTH`; die bearbeiteten Formen
(glasierte Terrakotta, geschnittener und gemeißelter Sandstein, Endsteinziegel) bleiben Baumaterial. JUnit
`LinkScenariosTest#aMesaOfTerrainBlocksCutsTheLinkAtEveryDistance` (Tafelberg 40 m tief, 20 m hoch: 60 dB, LQ 0 % bei
70, 150 und 300 m), Selbsttest `video: Funk-Material der Blöcke`.

### Digital: Stufen wie DJI, Walksnail und HDZero

- **Fenster** (`LinkBudget`): sauber ab +7 dB, verloren ab −5 dB statt +4/−2 dB. In Reserve ausgedrückt (Stufen aus
  `DigitalBreakup` unverändert): ab +4,0 dB sauber, +2,6…+4,0 dB weicher (Bitrate sinkt), −0,7…+2,6 dB Blöcke und
  Auflösungsstufen, −3,3…−0,7 dB Standbilder und Ruckeln, darunter Verlust. Vorher lagen zwischen sauber (+2,5 dB) und
  Verlust (−1,1 dB) nur 3,6 dB, jetzt 7,3 dB. Im freien Feld bleibt das Bild bis 0,63 · Reichweite sauber (wie vorher
  bis 0,75), bei der Reichweite blockig, verloren erst ab 1,46 · Reichweite statt 1,13. Die Hindernis-Abtastung reicht
  entsprechend weiter (`LinkBudget.maxReachBlocks`).
- **Profil je System** (`video.DigitalProfile`, aus der Latenz des Videomoduls, keine neue API): bis 18 ms Latenz
  `FIXED_RATE` (wie HDZero, z. B. Swift Link aus Just More Parts mit 14 ms), sonst `ADAPTIVE` (wie DJI und Walksnail,
  eingebaut Digital Lite und Digital HD).
  - `ADAPTIVE`: Bitrate sinkt (weicher, Auflösungsstufen, Blöcke wie bisher), die Latenz steigt mit schwächer werdender
    Verbindung um bis zu 40 ms (vorher fest bis 12 ms), Ruckeln und Standbilder über Bildaussetzer.
  - `FIXED_RATE`: Bild bleibt scharf (keine Unschärfe, keine Auflösungsstufen), die Latenz bleibt fest; schon ab der
    ersten Stufe kleine Blockfehler (8 px), mehr Bilder kommen an (mindestens 60 % statt 30 %).
- **Kein Schwarz auf einen Schlag** (`FeedModel#digitalLink`): fällt das Signal unter die Verlustschwelle, hält die
  Brille das letzte Bild – `ADAPTIVE` 0,7 s, `FIXED_RATE` 0,25 s –, erst danach „NO SIGNAL“. Ein kurzer Einbruch
  (einzelner Block, Pfosten) bleibt ein Standbild. Ohne Verbindung (falsche Brille, Drohne weg) weiter sofort schwarz.
  Rückkehr mit Hysterese: Verlust unter q = 0,06 (−3,3 dB), Rückkehr erst über q = 0,2 (−1,6 dB, vorher 0,12) und
  frühestens 0,3 s nach dem Schwarzbild (0,8 s mit „Flackern verringern“).

### Analog

Die Stufen (Störstreifen, Farbverlust, Schnee mit Sync-Sprüngen und Bildrollen, Rauschen) und das Fenster (+12/−6 dB)
bleiben; sie zerfallen schon über 12 dB von sauber bis Rauschen. Der Spieltest-Eindruck „bricht sofort weg“ kam von den
Hindernissen (ein Steinblock 286 dB, ein Bando-Raum 34 dB), nicht vom Fenster.

### Steuerstrecke (ELRS) mit derselben Abschattung

- `RadioLink`: die Steuerstrecke (2,4 GHz) bekommt 0,6 × die Abschattung der Videostrecke (Wände dämpfen bei 2,4 GHz
  etwa 0,6-mal so stark, die Fresnel-Zone ist breiter, Beugung kostet weniger). Die Dämpfung wirkt wie zusätzlicher
  Abstand (Abstand · 10^(dB/20)) auf dieselbe LQ-Kurve (voll bis 800 Blöcke, 0 bei 1200); die Grenze des Servers zählt
  weiter nur den echten Abstand. Kreativ und „unbegrenzte Reichweite“ bleiben frei.
- RSSI im OSD (`OsdData.rssiDbm`): −105 dBm + 20 · log₁₀(1200 / wirksamer Abstand), höchstens −30 dBm – fällt also mit
  Abstand und Abschattung, schon bevor die LQ sinkt (vorher aus der LQ abgeleitet, bis 800 Blöcke fest −50 dBm).
- Abtastung: im FPV und beim Zuschauen nutzt die Steuerstrecke die Abtastung der Videostrecke (`VideoState#pathLossDb`,
  keine Mehrkosten), im Sichtflug läuft eine eigene (`video.client.ControlLinkScan`, Budget 12 000 Zellen je Tick, bis
  1200 Blöcke). Dieselbe Abtastung nutzt im Sichtflug *Auto* der VTX-Leistung (`VtxRuntime`), das bisher nur die
  Entfernung kannte.
- Folge: bricht die LQ auf 0 (tief unter Tage, weit hinter einem Hügel), greift wie bei echtem ELRS der Failsafe bzw.
  GPS Rescue (`linkOk`, `RescueSupervisor`). Signal-Repeater helfen auch der Steuerstrecke.

### VTX-Leistung und Auto

Mehr Leistung hebt die Reserve wie bisher um 10 · log₁₀(P/P_Nenn) dB gegen jede Wand. *Auto* digital misst die Reserve
inklusive Abschattung und regelt beim Wechsel hinter ein Hindernis nach 0,25 s hoch (Ziel 10 dB = 3 dB über „sauber“,
unverändert, `VtxAuto`); bei Digital HD bringt die höchste Stufe 2,3 dB, bei Digital Lite 2,4 dB. Das reicht für eine
Stufe mehr (z. B. Blöcke → weicher), ersetzt aber keine freie Sicht. *Auto* analog (Standard seit derselben
Nachbesserung) regelt der Flugcontroller nach der Funkverbindung, die dieselbe Abschattung sieht, mit 15 dB Ziel
([`vtx-power.md`](vtx-power.md)); eine fest gewählte Stufe bleibt fest. Die Szenarien unten rechnen analog mit der festen
Nennleistung.

### Szenarien vorher und nachher (JUnit `LinkScenariosTest`)

Nachgebaute Szenen (Pilotenauge 1,62 m, flacher Boden), Standardleistung je Modul: Analog Race 400 mW (Reichweite 380),
Digital Lite 400 mW (300), Digital HD 700 mW (520); in Klammern Digital mit *Auto* (pendelt sich auf die höchste nötige
Stufe ein); „feste Datenrate“ = Profil `FIXED_RATE` mit 380 Blöcken (wie Swift Link aus Just More Parts, Bildstufe gleich,
nur das Aussehen unterscheidet sich). Qualität 0…1 mit Stufe; LQ/RSSI der Steuerstrecke (vorher immer 100 % und
−50 dBm). Haus 7 × 7 × 4 ohne Fenster, Drohne 2 m hoch 3,5 m dahinter; Bando aus Beton (13 × 7 × 17, zwei Innenwände im
Abstand von 4 m), Pilot 30 bzw. 80 m vor der Außenwand; Hügel aus Erde 10 m hoch, Kamm 20 m vor der Drohne; Höhle als
Luftblase 10 m unter der Oberfläche.

| Szene | Abstand | Dämpfung | Analog 400 mW | Digital Lite 400 mW (Auto) | Digital HD 700 mW (Auto) | feste Datenrate | LQ / RSSI |
|---|---|---|---|---|---|---|---|
| freies Feld, Drohne 2 m hoch | 101 m | 0,0 dB → 0,0 dB | 1,00 sauber → 1,00 sauber | 1,00 sauber → 1,00 sauber (1,00 sauber) | 1,00 sauber → 1,00 sauber (1,00 sauber) | 1,00 sauber | 100 % → 100 % / −83 dBm |
| Haus aus Bruchstein | 30 m | 19,0 dB → 13,6 dB | 0,51 Farbverlust → 0,90 sauber | 0,50 Blöcke → 0,99 sauber (1,00 sauber) | 1,00 sauber → 1,00 sauber (1,00 sauber) | 1,00 sauber | 100 % → 100 % / −81 dBm |
| Haus aus Bruchstein | 100 m | 19,7 dB → 14,4 dB | 0,00 Rauschen → 0,08 Rauschen | 0,00 verloren → 0,00 verloren (0,12 Standbilder) | 0,00 verloren → 0,37 Blöcke (0,66 Blöcke) | 0,09 Standbilder | 100 % → 100 % / −92 dBm |
| Haus aus Bruchstein | 250 m | 18,8 dB → 13,2 dB | 0,00 Rauschen → 0,00 Rauschen | 0,00 verloren → 0,00 verloren (0,00 verloren) | 0,00 verloren → 0,00 verloren (0,00 verloren) | 0,00 verloren | 100 % → 100 % / −99 dBm |
| Haus aus Brettern | 30 m | 10,9 dB → 8,9 dB | 0,99 sauber → 1,00 sauber | 1,00 sauber → 1,00 sauber (1,00 sauber) | 1,00 sauber → 1,00 sauber (1,00 sauber) | 1,00 sauber | 100 % → 100 % / −78 dBm |
| Haus aus Brettern | 100 m | 11,5 dB → 9,4 dB | 0,27 Schnee → 0,43 Farbverlust | 0,00 verloren → 0,39 Blöcke (0,69 Blöcke) | 0,90 sauber → 0,92 sauber (1,00 sauber) | 0,64 Blöcke | 100 % → 100 % / −89 dBm |
| Haus aus Brettern | 250 m | 10,5 dB → 8,4 dB | 0,00 Rauschen → 0,01 Rauschen | 0,00 verloren → 0,00 verloren (0,01 verloren) | 0,00 verloren → 0,15 Standbilder (0,41 Blöcke) | 0,00 verloren | 100 % → 100 % / −96 dBm |
| im Bando aus Beton hinter 1 Wand | 31 m | 34,1 dB → 9,0 dB | 0,00 Rauschen → 1,00 sauber | 0,00 verloren → 1,00 sauber (1,00 sauber) | 0,00 verloren → 1,00 sauber (1,00 sauber) | 1,00 sauber | 100 % → 100 % / −79 dBm |
| im Bando aus Beton hinter 2 Wänden | 35 m | 60,0 dB → 18,1 dB | 0,00 Rauschen → 0,48 Farbverlust | 0,00 verloren → 0,46 Blöcke (0,75 weicher) | 0,00 verloren → 0,96 sauber (1,00 sauber) | 0,71 weicher | 100 % → 100 % / −85 dBm |
| im Bando aus Beton hinter 3 Wänden | 39 m | 60,0 dB → 27,2 dB | 0,00 Rauschen → 0,00 Rauschen | 0,00 verloren → 0,00 verloren (0,00 verloren) | 0,00 verloren → 0,00 verloren (0,13 Standbilder) | 0,00 verloren | 100 % → 100 % / −91 dBm |
| im Bando aus Beton hinter 1 Wand | 81 m | 34,1 dB → 9,0 dB | 0,00 Rauschen → 0,62 Störstreifen | 0,00 verloren → 0,67 Blöcke (0,92 sauber) | 0,00 verloren → 1,00 sauber (1,00 sauber) | 0,89 sauber | 100 % → 100 % / −87 dBm |
| im Bando aus Beton hinter 2 Wänden | 85 m | 60,0 dB → 18,1 dB | 0,00 Rauschen → 0,01 Rauschen | 0,00 verloren → 0,00 verloren (0,00 verloren) | 0,00 verloren → 0,13 Standbilder (0,38 Blöcke) | 0,00 verloren | 100 % → 100 % / −93 dBm |
| im Bando aus Beton hinter 3 Wänden | 89 m | 60,0 dB → 27,3 dB | 0,00 Rauschen → 0,00 Rauschen | 0,00 verloren → 0,00 verloren (0,00 verloren) | 0,00 verloren → 0,00 verloren (0,00 verloren) | 0,00 verloren | 100 % → 100 % / −99 dBm |
| hinter einem Hügel (10 m hoch, 20 m vor der Drohne), Drohne 2 m hoch | 70 m | 34,8 dB → 34,8 dB | 0,00 Rauschen → 0,00 Rauschen | 0,00 verloren → 0,00 verloren (0,00 verloren) | 0,00 verloren → 0,00 verloren (0,00 verloren) | 0,00 verloren | 100 % → 100 % / −101 dBm |
| hinter einem Hügel (10 m hoch, 20 m vor der Drohne), Drohne 2 m hoch | 150 m | 34,8 dB → 34,8 dB | 0,00 Rauschen → 0,00 Rauschen | 0,00 verloren → 0,00 verloren (0,00 verloren) | 0,00 verloren → 0,00 verloren (0,00 verloren) | 0,00 verloren | 100 % → 0 % / −108 dBm |
| unter Tage in einer Höhle, 10 m unter der Oberfläche | 30 m | 60,0 dB → 60,0 dB | 0,00 Rauschen → 0,00 Rauschen | 0,00 verloren → 0,00 verloren (0,00 verloren) | 0,00 verloren → 0,00 verloren (0,00 verloren) | 0,00 verloren | 100 % → 0 % / −109 dBm |
| Wald, Drohne 15 m tief, 2,5 m hoch (Median aus 21 Stellen) | 50 m | 22,1 dB → 11,4 dB | 0,02 Rauschen → 0,75 Störstreifen | 0,00 verloren → 0,85 sauber (1,00 sauber) | 0,00 verloren → 1,00 sauber (1,00 sauber) | 0,99 sauber | 100 % → 100 % / −84 dBm |
| hinter einem Glashaus (zwei Glaswände) | 100 m | 6,8 dB → 6,8 dB | 0,65 Störstreifen → 0,65 Störstreifen | 0,88 sauber → 0,71 weicher (0,94 sauber) | 1,00 sauber → 1,00 sauber (1,00 sauber) | 0,91 sauber | 100 % → 100 % / −87 dBm |
| hinter einer Eiche (Stamm), Drohne 5 m dahinter | 50 m | 13,8 dB → 13,9 dB | 0,57 Farbverlust → 0,56 Farbverlust | 0,70 Blöcke → 0,58 Blöcke (0,85 sauber) | 1,00 sauber → 0,99 sauber (1,00 sauber) | 0,82 weicher | 100 % → 100 % / −86 dBm |

Warum das plausibel ist:

- **Haus**: 30 m ist für alle sauber (Analog knapp, 0,90); bei 100 m Analog fast nur noch Rauschen, Digital HD
  blockig mit Ruckeln statt schwarz, Lite nur mit Auto noch Standbilder; bei 250 m hinter Stein ist es weg – dort liegt
  die Reserve im Freien nur noch bei 3,6 dB (Analog) bzw. 6,4 dB (HD). Ein Holzhaus kostet 4–5 dB weniger.
- **Bando**: eine Betonwand kostet so viel wie eine Hauswand (9 dB, bei 30 m überall sauber, bei 80 m Analog mit
  Störstreifen), zwei Wände machen bei 30 m Analog farblos und Digital Lite blockig, drei Wände schneiden die Strecke
  ab – mit Auto hält Digital HD bei 30 m noch Standbilder.
- **Hügel und Höhle**: Gelände summiert sich, über den Kamm bleibt nur Beugung (34,8 dB); Video ist weg. Die
  Steuerstrecke hat hinter dem Hügel bei 70 m noch volle LQ, aber nur −101 dBm; bei 150 m und in der Höhle fällt sie
  aus (Failsafe) – wie bei echtem ELRS, das länger hält als das Video, aber nicht durch Fels.
- **Wald**: Median 11,4 dB statt 22,1 dB – hinter einem Stamm bleibt es teuer (größter Wert 30 dB), zwischen den Stämmen
  geht es weich; der einzelne Baum bleibt wie in 0.5.0 kalibriert (13,9 dB).
- **Glas**: zwei Scheiben 6,8 dB wie vorher – Glas kostet viel weniger als Mauerwerk.

Hausecke (Steinhaus bei 100 m, Sichtlinie an der Pilotenseite der Ecke, positiv = in den Schatten):

| Sichtlinie | 1 m frei | 0,5 m frei | an der Kante | 0,25 m | 0,5 m | 1 m | 2 m | 3 m im Schatten |
|---|---|---|---|---|---|---|---|---|
| Dämpfung | 0 dB | 0 dB | 3,6 dB | 7,3 dB | 13,7 dB | 14,4 dB | 14,7 dB | 14,3 dB |
| Digital HD | sauber | sauber | sauber | sauber | Blöcke (0,46) | Blöcke (0,36) | Blöcke (0,33) | Blöcke (0,37) |
| Analog | sauber | sauber | sauber (0,87) | Störstreifen | Schnee | Rauschen (0,08) | Rauschen | Rauschen |

Das Bild blendet um die Ecke über einen halben Meter aus statt zu springen; vorher lag die Ecke durch die
längs durchlaufene Seitenwand bei 21–24 dB und damit über dem Wert hinter der Hausmitte.

### Entscheidungen der Nachbesserung

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Ein Block Baumaterial = eine Hauswand (4,5–9 dB, Stamm 12 dB) | Vorgabe aus dem Spieltest; ein ganzes Haus kostet so 9–15 dB wie gemessene Hausschatten | physikalische Wandstärken beibehalten (Beton 34 dB, Stein 286 dB: jede dicke Wand ein Bunker) |
| Stein und Erde wie Mauerwerk je Block, aber ohne Laufgrenze | ein Steinblock als Baustoff wie eine Wand, ein Hügel oder eine Höhle summiert sich trotzdem bis zum Deckel | Gelände getrennt von Baustein (dieselben Blöcke, nicht unterscheidbar) |
| Lauf aus Baumaterial höchstens 2 m | Strahlen längs durch eine Block-Wand (Hausecke) zählten sieben Wände; echte Wände sind dünn | keine Grenze (Ecke schlechter als Hausmitte); Grenze auch für Gelände (Hügel durchlässig) |
| Stamm 12 dB statt 10 dB | die einzelne Eiche bleibt bei ihrem Wert aus 0.5.0 (13,9 dB), Blockhauswände zählen wie schwere Wände | 10 dB (Baum 11,7 dB, Wald 10,7 dB), 16 dB (Baum 16,3 dB, über den Messungen) |
| Digitales Fenster +7/−5 dB | doppelt so viel Weg zwischen sauber und schwarz; freies Feld bleibt nah an der Kalibrierung (sauber bis 0,63 · Reichweite) | nur −5 dB als Verlust (sauber unverändert, Übergang zu hart), +10/−2 dB (sauber nur bis 0,32 · Reichweite) |
| Profil aus der Latenz (≤ 18 ms = feste Datenrate) | Systeme mit fester Datenrate haben die geringste Latenz; kein neues API-Feld, Just More Parts bleibt unverändert | API-Feld `digital_profile` (API- und Addon-Änderung kurz vor dem Release) |
| Standbild 0,7 s (adaptiv) bzw. 0,25 s (feste Datenrate) vor Schwarz | DJI und Walksnail halten das letzte Bild, HDZero zeigt den Verlust schneller | sofort schwarz (Spieltest), längeres Standbild (verdeckt echten Verlust) |
| Latenz adaptiv bis +40 ms | schwache Verbindung bei DJI/Walksnail ist spürbar träger; der Spieltest vermisste das Nachziehen | feste +12 ms (kaum spürbar) |
| Steuerstrecke 0,6 × Abschattung der Videostrecke | dieselbe Abtastung, keine zweite Rechnung; 2,4 GHz dämpft in Wänden und an Kanten weniger | eigene Abtastung bei 2,4 GHz (doppelte Kosten im FPV), Steuerstrecke ohne Hindernisse (wie bisher) |
| RSSI aus Abstand und Abschattung | zeigt die Reserve, bevor die LQ fällt, wie bei echtem ELRS | RSSI aus der LQ (bis 800 Blöcke fest −50 dBm) |
| Kein Streupegel (Mehrwege) als Untergrenze | würde jede Abschattung im Freien auf ≈ 15 dB deckeln, Hügel schnitten nicht mehr ab | Streupegel −15…−20 dB, nur unter freiem Himmel |
| Analog-Fenster unverändert | zerfällt schon über 12 dB, Reichweiten im freien Feld bleiben | Fenster verbreitern (Analog reicht im Freien weiter) |
| VtxAuto-Ziel bleibt 10 dB | 3 dB über „sauber“, 6 dB über der Stufe „sauber“; Auto regelt hinter Hindernissen hoch | Ziel an das neue Fenster koppeln (mehr Strom und Wärme im Normalflug) |

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
- Nachbesserung 0.5.2 (Selbsttest-Lauf vom 15.10.2026, Fabric, 32 Stellungen samt Bando): im Durchschnitt 0,066 ms je
  Tick, höchstens 0,62 ms, größte Runde 5111 Zellen (Bando bei 60 m mit 61 Strahlen). Die Steuerstrecke kostet im FPV
  nichts extra (gemeinsame Abtastung); im Sichtflug läuft eine Abtastung mit höchstens 12 000 Zellen je Tick, wo die
  Videostrecke ohne Brille nichts rechnet.

## Selbsttest im Spiel

Der Schritt „video: Abschattung hinter Baum, Haus, Bando und Wald, Vorbeiflug und Runde ums Haus“ baut 400 Blöcke neben
dem Spieler dieselben Szenen wie `ShadowScenariosTest` und `LinkScenariosTest` (Eiche, Haus aus Bruchstein mit Fenstern,
Wald mit demselben Raster und Versatz wie bei 50 m im JUnit, Bando aus Beton mit drei Räumen, Licht und farbigem Beton –
gleiches Material) und stellt Pilot und Drohne nacheinander auf 32 Stellungen: Baum bei 20/50/90 m, Haus bei 30/60 m,
Wald bei 50 m, freies Feld bei 90 m, Bando hinter 1/2/3 Wänden mit dem Piloten 30 und 60 m vor der Außenwand,
Vorbeiflug am Baum bei 50 m in 11 seitlichen Stellungen und eine Runde ums Haus bei 60 m in 8 Stellungen. Dazu muss die
Steuerstrecke genau die Abschattung der Videostrecke sehen (`ControlLinkScan#videoBandLossDb`). Jede Stellung muss im Spiel innerhalb von ±1,5 dB den Wert des reinen Modells
treffen (Einordnung der Blöcke, Formen, Chunk-Zugriff und Strahlen zusammen), freie Stellungen exakt 0 dB, „knapp
vorbei“ bei freiem Mittelstrahl mehr als 0,5 dB; das Signal muss zur Dämpfung passen. In den Läufen vom 03.10. und 05.10.2026 trafen alle 26 Stellungen den
Modellwert auf 0,1 dB genau, im Lauf vom 15.10.2026 (Nachbesserung) alle 32 (Bando 9,0 / 18,1 / 27,2 dB bei 30 m und
9,0 / 18,1 / 27,1 dB bei 60 m wie im Modell). Die weiteste Stellung liegt bei 90 m, weil der Selbsttest mit 6 Chunks Sichtweite läuft:
der Server zeigt dem Client eine Drohne ohne Zuschauen nur bis 6 · 16 = 96 Blöcke (`ChunkMap.TrackedEntity`); die
Bildschirmfotos bei 100 m laufen über Zuschauen, das die Sicht an der Drohne verankert.

Bildschirmfotos (`-PpropwashScreenshots=true`, Zuschauen auf die Testdrohne mit passender Brille): `vtx_shadow_tree_overview`
(Aufbau schräg von hinten: Drohne vorn, dahinter die Eiche, der Pilot weit hinter dem Baum), `vtx_shadow_tree_20/50/100_analog`, `vtx_shadow_tree_50/100_digital` (Analog bei 50 m
Farbverlust, bei 100 m Schnee; Digital bei 50 m sauber, bei 100 m Blöcke), `vtx_shadow_house_40_analog/digital` (0.5.0:
19,3 dB; seit der Nachbesserung 13,7 dB: Analog Störstreifen 0,73, Digital sauber) und
`vtx_shadow_forest_30_analog/digital` (0.5.0: 22,6 dB; jetzt 20,6 dB: Analog Schnee 0,37, Digital weicher 0,86).

Störstufen im Bando (Nachbesserung 0.5.2, `vtx_signal_bando_<Wände>_<Abstand des Piloten>_<System>`): jedes Bild
prüft vorher, dass das Spiel genau die angegebene Stufe zeigt, digital auch, dass nur „verloren“ schwarz ist. Analog
`1_70` Störstreifen (9,0 dB, 0,72), `2_30` Farbverlust (18,1 dB, 0,49), `2_45` Schnee (0,24), `3_30` Rauschen (27,2 dB);
Digital HD `1_60` sauber, `2_40` weicher (0,78), `2_60` Blöcke (0,39, Latenz mit Standbildern 90 ms), `2_71`
Standbilder (0,23, 115 ms), `3_60` verloren (27,1 dB, „NO VIDEO“).

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
| Bauwände mit typischer Wandstärke, Gelände und Stämme voll (seit der Nachbesserung in 0.5.2 ersetzt: ein Block = eine Hauswand, Gelände summiert sich je Block) | Ziegelwand trifft die Messung, ein Hügel bleibt undurchdringlich | ein Meter Vollmaterial je Block (jedes Haus wäre ein Bunker) |
| Fenster als Glasscheibe 6 mm je Durchgang (≈ 3,2 dB) | Glasblöcke stehen im Spiel für Fenster | 1 m Glas (25 dB) |
| Reichweiten, Schwellen, Mehrwege unverändert (seit der Nachbesserung in 0.5.2: digitales Fenster +7/−5 dB) | Kalibrierung je VTX bleibt, nur Hindernisse ändern sich | Schwellen an das neue Modell anpassen |
| Budget je Tick, Runde über mehrere Ticks | kein Ruckeln bei großen Abständen | feste Strahlzahl je Tick wie bisher |
| Selbsttest-Szenen gleich gebaut wie die JUnit-Szenen, Prüfung gegen die Modellwerte | prüft Einordnung, Formen und Chunk-Zugriff im echten Spiel gegen dieselbe Rechnung | nur grobe Bereiche je Szene (fängt falsche Einordnung nicht) |
| Wald als Verteilung über 21 Stellen angeben | die Dämpfung hängt stark davon ab, ob ein Stamm vor der Drohne steht | eine einzelne Stelle als „der Wald“ |
| Blockform, die ohne Welt nicht abfragbar ist, zählt als ganzer Block | Blöcke anderer Mods dürfen die Rechnung nicht abbrechen | Block überspringen (wäre Luft) |
