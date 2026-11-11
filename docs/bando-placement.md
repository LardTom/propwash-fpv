# Bando-Lage: kein Hof mit Löchern, kein Laub ohne Stamm

Für Spieler. Ab 0.6.0 haben neu erzeugte Bandos keinen Hof mehr mit Löchern, wenn eine Höhle oder Schlucht unter dem
Bauplatz durchläuft, und es bleibt kein Laub mehr ohne Stamm am Bando hängen: Blätter, die der Bando von ihrem Baum
abgeschnitten hat, verschwinden schon beim Erzeugen, statt sich an einem Balken des Bandos für immer zu halten.
Bestehende Chunks bleiben, wie sie sind; das gilt nur für Chunks, die neu erzeugt werden.

Technik und Entscheidungen: [`design/0.6.0/fixes-polish.md`](design/0.6.0/fixes-polish.md) Punkt G (§3.G, §6.8, E21) und
[`design/bandos.md`](design/bandos.md) („Lage im Gelände“, „Pflanzen“).

## 1. Befund und Ursache

Die Lage-Prüfung von 0.5.2 scheiterte auf zwei von acht Seeds an je einer Scheune; beide Fälle sind mit 0.5.2 auf
Fabric nachgestellt (Selbsttest `bando: Lage – …` mit `-PpropwashBandoSeed`).

| Seed | Platz | Meldung 0.5.2 | Ursache |
|---|---|---|---|
| 1618033 | `barn/n17`, Wald bei −4728, 63, −6664 | „Gelände am Rand im Median −2 unter dem Boden“ | Eine Schlucht der Höhlen-Erzeugung läuft mit Wasser auf Meereshöhe um den Bauplatz. Die Platzwahl sieht sie nicht (die Höhen der Platzwahl kennen keine Höhlen): Dort meldete sie Gelände 4 bis 5 Blöcke über dem Boden, tatsächlich steht Wasser 1 Block unter dem Boden auf Grund 2 bis 7 Blöcke tiefer. Im Hof um das Silo fehlten 24 Hofblöcke: Für Hofspalten mit eigenem Fundament im Plan (der 7 × 7-Sockel um Silo und Wasserturm, die Rampe vor der Lagerhalle) baute das Setzen weder den Hof noch das Fundament direkt darunter, das Fundament begann erst zwei Blöcke unter dem Boden. |
| 900001 | `barn/n12`, Kirschhain bei −4664, 126, −1144 | „cherry_leaves … auf BEAM ohne Stamm (Abstand 1)“ | Der Bando hat die Krone eines Kirschbaums durchschnitten. Zwei einzelne Blätter blieben in Luftzellen unter dem Dach zurück; eines liegt auf einem Fichtenstamm-Balken des Bandos. Vanilla zählt jeden Stamm als Halt (Abstand 1), auch den Balken, also zerfiel es nie. |

Die Hang-Grenze der Platzwahl war nicht die Ursache: Der Grundriss lag mit Hang 5 innerhalb der Grenze 7 der Familie,
und ohne die Schlucht hätte das Gelände am Rand bei 0 bis +1 gelegen.

## 2. Was sich ändert

- **Hof über eigenem Fundament** (`BandoPiece#underpin`, Regel in `BandoSite.underpinned`): Fehlt unter einer Hofspalte
  das Gelände (Höhle, Schlucht, Wasser), füllt das Setzen sie jetzt auch dann bis zum festen Grund mit dem Fundament der
  Palette auf und legt den Hof darauf, wenn der Plan dort ein eigenes Fundament hat. Spalten, in denen ein Bauteil auf
  oder direkt über dem Boden steht (Wand, Stütze, Zaun), behalten ihr eigenes Fundament wie bisher. Auf intaktem
  Gelände ändert sich nichts.
- **Laub ohne Stamm** (`BandoPiece#tidy`, `LeafDistance.orphans`): Nach allen Merkmalen eines Chunks (also nach den
  Bäumen) rechnet das Setzen für jeden Bando, dessen Bereich samt 4 Blöcken Rand den Chunk berührt, den Abstand jedes
  Blatts zum nächsten Stamm neu – wie Vanilla, aber Stämme und Balken des Bandos zählen nicht als Stamm, und Blätter in
  Planzellen, die ein Bauteil ersetzt (auch in Nachbar-Chunks, die ihren Teil noch nicht gesetzt haben), leiten keinen
  Abstand weiter. So fallen schon jetzt die Blätter weg, die der übrige Bando später abschneiden wird. Blätter mit
  Abstand 7 (kein Stamm in 6 Schritten) entfernt es; eine Schneeschicht darauf fällt mit weg. Das trifft vom Bando
  abgeschnittene Kronenreste und auch Blätter, die Vanilla selbst ohne Stamm setzt (Mangroven, Büsche im Dschungel).
  Geschrieben wird wie bisher nur im Chunk, der gerade erzeugt wird.
- **Abgeschnittene Kronen jenseits des Chunk-Rands** (`BandoPiece#settleLeaves`): Schneidet das Setzen eine Krone
  durch, rechnet es den Blattabstand ebenfalls ohne die Stämme des Bandos. Blätter im Nachbar-Chunk, die dadurch keinen
  Stamm mehr haben, aber noch einen kleineren Abstand tragen, bekommen einen Block-Tick (kein Schreibzugriff auf den
  Block): Sobald der Nachbar-Chunk tickt, rechnet Vanilla ihren Abstand neu, und sie zerfallen wie Blätter eines
  gefällten Baums. Vorher blieben sie mit altem Abstand für immer stehen (Test-Seed 20261003, `concrete_frame/n6`).
- Baupläne, Platzwahl und alle übrigen Blöcke bleiben unverändert (Fingerabdrücke der Pläne in `BandoPlacementTest`).

## 3. Grenzen

- Die Platzwahl kennt weiter keine Höhlen und Schluchten. Läuft eine Schlucht durch den Hof, steht der Hof dort auf
  einem Fundament bis zum Grund, wie es seit 0.5.0 für Höhleneingänge und Lavaseen gilt.
- Setzt ein Baum aus einem später erzeugten Nachbar-Chunk Blätter ohne Verbindung zu seinem Stamm direkt an einen Balken
  aus Stamm-Holz in einem fertigen Chunk, hält Vanilla sie am Balken fest (Vanilla zählt jeden Stamm). Der Bando kann sie
  nicht mehr entfernen, ohne in den fertigen Chunk zu schreiben; die Lage-Prüfung meldet solche Blätter. Das hängt von
  der Reihenfolge der Chunk-Erzeugung ab: Auf Seed 900001 traf es im Endstand einen von fünf Läufen (zwei Kirschblätter
  im Dachboden der Scheune `barn/n12`, gesetzt von einem Kirschbaum aus dem südlichen Nachbar-Chunk). Abhilfe wäre, dass
  `BandoGuard` solche Blätter beim Schreiben in einen fertigen Chunk ablehnt (offen).
- Ein Blatt, das erst ein später erzeugter Nachbar-Chunk in einen fertigen Chunk am Bando setzt, prüft der Bando nicht
  mehr nach (er schreibt nie in fertige Nachbar-Chunks). Solche Blätter hängen an ihrem eigenen Baum, oder Vanilla hat
  sie selbst ohne Stamm gesetzt (Abstand 7) und sie zerfallen nach kurzer Zeit von selbst. Gemessen auf dem Test-Seed
  20261003: 10 solche Eichenblätter von Dschungelbüschen an 2 von 164 Plätzen (beide Betonskelette im Bambusdschungel).

## 4. Prüfung

- JUnit `bando.BandoPlacementTest`: acht feste Seeds (77031, 31337, 123456789, 2718281, 4242, 555, 900001, 1618033) für
  jede Variante aller Familien; jedes Bauteil der Bodenzeile ruht auf Gelände oder einem Fundament bis zum Gelände, auf
  flachem Gelände, über einer Rinne von 1, 2, 7 und 30 Blöcken Tiefe und über einer Grube; Hofspalten mit eigenem
  Fundament werden unterfüttert, stehende Teile behalten ihr Fundament; nach dem Entfernen bleibt im Bereich samt
  4 Blöcken Rand kein Blatt mit Abstand 7; die Baupläne der acht Seeds sind byte-gleich zum Stand vor der Änderung.
- Selbsttest `bando: Lage – Boden auf Geländehöhe, nichts schwebt, keine Pflanzen auf dem Bau` prüft an den echten
  Blöcken zusätzlich jedes Blatt im Bereich samt 4 Blöcken Rand (Abstand zum nächsten Stamm, der nicht zum Bando
  gehört). Blätter ohne Stamm bilden Gruppen; eine Gruppe zerfällt von selbst, wenn kein Stamm des Bandos sie hält und
  alle Blätter schon Abstand 7 haben oder für eines ein Block-Tick aussteht. Solche Blätter stehen nur als Zahl im
  Protokoll („zerfallende ohne Stamm“); jedes andere Blatt ohne Stamm lässt die Prüfung scheitern.
  Randspalten, deren Gelände nach der Platzwahl abgetragen wurde (Gelände tiefer als der Boden und tiefer als die
  Höhe, die die Platzwahl sah), zählen im Median nicht mit, wie schon Randspalten in fremden Bauwerken.
- Selbsttest `bando: Lage – Luftbilder der Stellen mit Befund` (nur mit `-PpropwashScreenshots=true`): Luftbild und
  Nahaufnahme der bekannten Befund-Stellen des Seeds und jedes Platzes, der in der Prüfung auffiel.
- Selbsttest `bando: alle Varianten in die Testwelt gesetzt und geprüft` setzt jede Variante zweimal auf die flache
  Testwelt, und zwar bei nicht geladener Spawn-Spalte: Der Spieler geht dafür 640 Blöcke vom Spawn weg und kommt danach
  an seine Stelle zurück. Die Bodenhöhe kommt aus dem Chunk am Spawn, den der Schritt dafür lädt. Vorher las er sie über
  `Level#getHeight`, das bei nicht geladener Spalte die Weltunterkante liefert. Im vollen Lauf auf Fabric mit
  Bildschirmfotos steht der Spieler nach den Bildschirmfotos weit vom Spawn, dort landeten so alle Bandos mit Boden −65
  im Grundgestein (Wellenabschluss W1, Befund 3).
- Befehle: `'-PpropwashSelftestOnly=bando:' -PpropwashBandoSeed=1618033` und `…=900001` auf Fabric und NeoForge
  ([`testing.md`](testing.md) §1).

## 5. Englische Kernsätze (Website)

- Newly generated bandos no longer have holes in their yard where a cave or ravine runs under the site.
- Leaves that a bando cut off from their tree disappear while the world is generated instead of clinging to a beam forever.
- Existing chunks stay as they are; only newly generated chunks are affected.
