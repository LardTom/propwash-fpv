# Freestyle-Tricks — Erkennung, Repertoire, Punkte, Anzeige

Stand 0.5.0. Modul `trick` (Paket `de.lardtom.propwash.trick`, reines Java mit JOML; Client-Teil in `trick.client`).
Die Erkennung liest nur den Zustand des Flugmodells und optional Abstände zu Blöcken; sie greift nie in die Simulation ein.
Das Flugmodell ist dafür unverändert geblieben.

## Warum neu (0.5.0)

Rückmeldung aus dem Spieltest: echte Tricks wurden nicht erkannt, das Repertoire war zu klein. Die Ursachen in 0.3/0.4:

- Ein Manöver begann und endete nur in „Ruhe“ (`up.y ≥ 0,4`, also höchstens 66° Neigung, und Roll-/Nickrate < 120 °/s für 0,1 s).
  Schneller Vorwärtsflug liegt oft über 66° Neigung (Freestyle-Quad bei 75° ≈ 29 m/s) – dann gab es nie Ruhe, das Manöver lief
  bis zur 8-s-Grenze und nur ein Hänger konnte zählen.
- Mehrere Tricks ohne Pause (Flip direkt in eine Rolle, Split-S direkt in einen Power Loop) landeten in einer Episode und passten
  danach zu keiner Regel.
- Achsen-Mischung (Roll-Anteil im Flip, diagonale Flips) und die mittlere Drehrate über die ganze Episode samt An- und Auslauf
  verwarfen viele saubere und leicht unsaubere Ausführungen.

## Ablauf pro Frame

1. `trick.client.TrickTracker` hängt an `ClientBus.FRAME_START` (angemeldet in `TrickClient.init()`, vor `OsdClient`, also
   nach `PilotSession.onFrame` und vor dem OSD-Sampler). Aktiv nur mit laufender Pilot-Sitzung (`PilotSession.sim()`),
   Welt geladen und eingeschalteter Erkennung für den Rahmen der Drohne. Im Pausenmenü wird nicht gezählt.
2. Er füllt ein `TrickSample`: Sim-Zeit, CoM-Position, Geschwindigkeit, Lage-Quaternion (Körper → Welt, Spec 2.2), `armed`,
   `crashCount`, `impactCount`, `grounded`, `throttleOut`; `assisted` = GPS Rescue aktiv oder Rennen läuft.
3. `ClearanceProbe` schießt, solange die Drohne scharf ist, vier Strahlen (3 m, `ClipContext.Block.COLLIDER`, ohne Flüssigkeiten)
   quer zur Flugrichtung (unter 1 m/s quer zur Nase): seitlich (±e1, waagerecht) und „oben/unten“ (±e2) aus `ProbeAxes.compute`.
4. `TrickSession.update` → `TrickDetector` (findet Tricks) → `TrickScore` (bestätigt, punktet, Kombos, Ketten, Meldung).
5. `LiveOsdSource` kopiert den Zustand per `OsdData.applyTricks(session)` ins OSD, `FlightStats` übernimmt Punkte und beste
   Kombo des Flugs.

## Schalter

`tricks.json` (`TrickClient.CONFIG`, Schema `trick.TrickConfig`): `mode` = `AUTO` (Standard), `ON`, `OFF`.
`AUTO` ist an bei Rahmen, deren Rolle `tricksByDefault()` meldet (Freestyle 5″, Whoop, Cinewhoop), aus bei Race und Long Range
(`TrickMode.enabledFor`). Wer mit einem Race-Rahmen Tricks fliegt, stellt den Tab auf „An“.

## Quellen und Definitionen

Die Freestyle-Szene benennt Tricks nicht einheitlich. Propwash nimmt die Beschreibung, die in den Quellen am häufigsten
vorkommt, und erkennt Varianten mit, wo sie sich sauber trennen lassen:

- Rotor Riot, *FPV Freestyle Tricktionary* – https://rotorriot.com/blogs/tutorials-guides/fpv-freestyle-tricktionary
  (Rubik's Cube: halber Flip, Rolle, halber Flip „oder eine Kombination davon“; Juicy Flick: vorwärts kopfüber, zurückschauen,
  zurück; Split-S; Immelmann; Matty Flip „pitch forward, keep pitching, apply power, end up thrusting backwards“;
  Power Loop; Knife Edge; Trippy Spin/Cyclone „wie Orbit, andere Kameraperspektive“; Barrel Roll „mit Gas als Spirale“;
  Inverted Yaw Spin „turn over, ordinary yaw spin, turn over again“).
- Tyrantt's Pro Whooper (Trick-Datenbank mit Punkten) – https://prowhooper.com/juicy-flick/,
  https://prowhooper.com/rubiks-cube/, https://prowhooper.com/trippy-spin/ (Juicy Flick: „pitch forward with throttle until
  fully inverted, then a 180° roll“; Rubik's Cube: „180° flip, 360° roll, another 180° flip in the same direction“, dort
  325 Punkte; Trippy Spin: kopfüber mit Roll und Gier um ein Objekt kreisen).
- WREKD, *A master list of FPV tricks* – https://wrekd.com/pages/a-master-list-of-fpv-tricks-and-manuevers-and-how-to-do-them
  (Rewinds als eigene Familie: Split-S Rewind, Flip Rewind; „Inverted Orbit (Trippy Spin)“; Rubik's Cube als vier schnelle
  halbe Rollen und Flips; Knife Edge „auf der Rollachse gekippt, Richtung halten“).
- Drohnen-Forum.de, Thread *FPV Freestyle Trick-Tutorials* – https://www.drohnen-forum.de/index.php/Thread/35346-FPV-Freestyle-Trick-Tutorials/
  (laut Suchauszug: der McTwist ist „in Reinform“ mit dem Inverted Yaw Spin vergleichbar; die Seite selbst war nicht abrufbar).
- Skateboarding: McTwist = Salto mit Drehung um die Hochachse (Mike McGill, 1984); Kunstflug: gerissene Rolle (Snap Roll) =
  Höhen- und Seitenruder voll, Rotation um eine schräge Achse.

Wo die Quellen sich widersprechen, gilt:

| Trick | Quellen uneinig | Propwash |
|---|---|---|
| Matty Flip | rückwärts (Backflip mit Gasstoß) oder vorwärts (Rotor Riot) | seit dem Trick-Katalog getrennt: vorwärts = Matty Flip, rückwärts = Anti Matty (Pro Whooper); beide mit Gasstoß gegen die Flugrichtung und Richtungsumkehr |
| Trippy Spin | kopfüber kreisen (Pro Whooper, WREKD) oder kopfüber drehen im Fallen (Projektvorgabe) | beides: Yaw-Spin kopfüber im Fallen (≥ 4 m) oder kopfüber um einen Punkt |
| McTwist | Inverted Yaw Spin „in Reinform“ | halber Flip, Spin kopfüber, Flip in dieselbe Richtung zu Ende (sonst „Yaw-Spin kopfüber“) |
| Snap Roll | im FPV kaum belegt | wie im Kunstflug: Knüppel in die Ecke, schnelle Drehung um eine schräge Achse |
| Corkscrew | Barrel Roll mit Gas | langsame Rolle mit Gas, die Drohne fliegt dabei weiter |
| Rewind | Familie (Flip-Rewind, Split-S-Rewind …) | jede Drehung ab 150° über Kopf, die auf demselben Weg zurückgedreht wird |

## Neue Schule (0.5.0): Sbang, Dex-Style und 3D

Rückmeldung eines Creators, der Piloten coacht (öffentliches Video zur Mod, Oktober 2026): die Liste wirke wie die Tricks von
vor zehn Jahren; gewünscht waren Sbang und Sbang-Kombos, „Gigatines“, Shinobis, Side Trippies, Tricks im „deck style“ und
Tricks aus dem 3D-Modus. Mehrere Namen sind Szene-Slang ohne schriftliche Definition. Recherchiert wurde in Tutorials,
Trick-Listen und – wo es nur Videos gibt – anhand der Untertitel und Einzelbilder der Videos.

| Begriff | Was er in der Szene meint | Quelle |
|---|---|---|
| Sbang | ein Freestyle-Stil, kein einzelner Trick: aggressiv, ruckartig, viele 180°-Wender ohne „tote Strecke“; Grundbausteine sind Ejects (kippen, Gasstoß, zurückschnappen), Rewinds (Split-S, Pause, Juicy Flick zurück), Pausen kopfüber mit geradem Horizont und Yaw-Spins kopfüber als Verbindung | WREKD, *The types of FPV Freestyle* – https://wrekd.com/pages/the-types-of-fpv-freestyle; Roii FPV, *Introduction to Juicy Sbang Sbang* – https://www.youtube.com/watch?v=HH77v_NHqBM; Joshua Bardwell, *Sbang Freestyle – The Eject* – https://www.youtube.com/watch?v=fxx7ohlu1tg |
| Sbang-Kombo | eine Folge solcher Bausteine; Einstiegskombo „Boost (Eject) → Split-S → Juicy Flick + Yaw“ | T-Hobby, *The First Sbang Combo You Should Learn* – https://www.t-hobby.com/blog/the-first-sbang-combo-you-should-learn-boost-split-s-juicy-flick; headmazta, *Sbang Ultimate Combo* – https://www.youtube.com/watch?v=CK4AQszHHfI |
| „deck style“ | gemeint ist **Dex-Style** („dexstyle“, von *dexterity*): Tricks mit mehr Dimension – Höhe, Seitenlage, Flachdrehungen – statt geradeaus fliegen und Flips aneinanderreihen | Dronarchy, *How to FPV Guillotine* – https://www.youtube.com/watch?v=EapqqioV1-s (Untertitel „deck style, more dexterity … more dimensionality“), Hashtag #dexstylefpv |
| „Gigatine“ | die **Guillotine**: weit steigen, senkrecht stürzen und erst kurz vor dem Boden einen Matty (Flip) hinaus, oft durch eine Lücke; die automatische Untertitelung schreibt das Wort als „gigatines“ bzw. „guatines“ | Dronarchy, *How to FPV Guillotine* (s. o.); Kurzvideo *Insane Drone Guillotine* – https://www.youtube.com/watch?v=kj4ZMGEM6EA (Einzelbilder: Blick senkrecht nach unten, Flip über den Himmel, Ausflug knapp über dem Boden) |
| Shinobi (Flip) | Kombi aus „Funky Split · Rubik's · Matty out“ (Einblendung im Video) | Dronarchy, *FPV Drone Shinobi Flip (Dexstyle)* – https://www.youtube.com/watch?v=OM0HY41iulc |
| Funky Split | Split-S parallel statt frontal zum Hindernis, „im Grunde eine halbe Flachdrehung“ | Dronarchy, *Add this Insane DeX Trick to your FPV Lines!* – https://www.youtube.com/watch?v=pUGD3z88KhY |
| Side Trippy | wie ein Trippy Spin eng um einen Punkt kreisen, aber auf der Seite liegend – der Horizont steht senkrecht | Dronarchy, *Making FPV history: Self Side Trippy* – https://www.youtube.com/watch?v=KQakKP_S3WI und *Caged Side Trippy* – https://www.youtube.com/watch?v=HvWmSJFDpUM (Einzelbilder) |
| 3D-Tricks | Fliegen und Schweben kopfüber mit Umkehrschub; aus dem 3D-Kunstflug das Tic-Toc (Hin- und Herkippen um 180° auf der Stelle, bei 3D-Quads „tick tock flips“) | Dronarchy, *How to 3D FPV (Basics)* – https://www.youtube.com/watch?v=eQxXMkD9ISA, *Inverted Glass – 3D FPV Insanity* – https://www.youtube.com/watch?v=v7hkbwynxhM; *3d acro inverted indoors tick tock flips* – https://www.youtube.com/watch?v=6F6XVygr7fI |

**Definitionen in Propwash (prüfbar):**

| Trick | Bedingung |
|---|---|
| Eject | aus ruhiger Lage (Neigung ≤ 20°, Kipprate < 130 °/s) in ≤ 0,35 s auf ≥ 40° kippen, in beliebige Richtung; während ≥ 30° Neigung ≥ 0,1 s Gas ≥ 0,75; dann in ≤ 0,3 s von 30° auf ≤ 15° zurück; alles in ≤ 1,2 s, nie mehr als 110° geneigt (`up.y ≥ −0,35`), waagerecht ≥ 3 m/s schneller als vorher, Höhe ±6 m, ohne Berührung |
| Sbang-Kombo | Linie aus Tricks mit jeweils ≤ 0,8 s Pause, ≤ 8 s ab Beginn, darin ≥ 1 Eject und ≥ 2 Wender (Split-S, Juicy Flick, Immelmann, Rewind, Funky Split, Yaw-Spin kopfüber, Trippy Spin, Matty Flip, Rubik's Cube, McTwist, Snap Roll, Tic-Toc, Anti Matty, Half Matty, Power Matty, 540-Split-S, Splitback, Flip-Rewind, Barani); einmal je Linie |
| Funky Split | halbe Drehung (90–270°, Rolle oder Nick) auf den Rücken, Gier-Strich 120–250° zu ≥ 50 % über Kopf mit Roll/Nick ≤ 50 % des Gierens, halbe Drehung zurück, endet aufrecht; Nase zeigt danach ≥ 120° anders als vorher |
| Shinobi Flip | Split-S oder Funky Split, dann Rubik's Cube, dann Matty Flip, Anti Matty, Power Matty oder Flip, jeweils ≤ 0,8 s Pause |
| Guillotine | ein Flip (auch Doppel-, Dreifach-, Matty Flip, Anti Matty, Power Matty oder ein als Power Loop erkannter Bogen) endet höchstens 1,5 s nach einem ruhigen Sturz: ≥ 0,3 s Nase ≥ 40° unten, ≥ 7 m/s Sinken, dabei ≤ 40° Drehung; nach dem ruhigen Sturz noch ≥ 200° Nick; das schnelle Sinken (≥ 7 m/s) liegt ≤ 0,8 s vor dem Flip-Ende; höchstens 0,8 s nach dem Flip abgefangen (Sinken ≤ 2,5 m/s, `up.y ≥ 0,3`); ≥ 8 m Fallhöhe in den 6 s davor. Ersetzt Flip und Sturzflug |
| Side Trippy | ein Nick-Strich (kein Roll- oder Diagonal-Strich), ≥ 50 % der Zeit auf der Seite (`\|up.y\| ≤ 0,5`), nie über 135° gekippt (`up.y ≥ −0,7`), Nase dreht ≥ 330° um die Hochachse mit ≥ 200 °/s, die Flugbahn dreht gleichsinnig ≥ 60 % davon, mittlerer Abstand zum Kreismittelpunkt ≤ 6 m, ≥ 2,5 m/s; je weitere 360° eine Runde mehr |
| Rückenflug (3D) | nur im 3D-Modus: ≥ 1 s am Stück über Kopf (`up.y < −0,5`) ohne Drehung, davon ≥ 60 % mit Umkehrschub (Gas ≤ −0,05), höchstens 1,5 m gefallen; ersetzt das Kopfüber-Hängen |
| Tic-Toc (3D) | nur im 3D-Modus (≥ 90 % der Zeit): ≥ 4 halbe Flips oder halbe Rollen (je 120–240°, gleiche Achse) abwechselnd vor und zurück, mindestens einmal über Kopf, ≤ 6 s |

**Vom Besitzer mit dem Creator abzustimmen** (bis dahin gilt die Propwash-Definition oben):

1. „Gigatine“ ist die Guillotine – oder ein eigener, größerer Trick? Muss der Flip durch eine Lücke gehen? (Propwash: keine Lücke nötig, Nähe zu Blöcken bringt wie bei allen Tricks den Stil-Bonus.)
2. Shinobi: genügt ein normaler Flip als „Matty out“, und darf statt des Funky Splits ein Split-S stehen? (Propwash: ja.)
3. Funky Split: ist die halbe Flachdrehung kopfüber richtig verstanden, oder liegt die Drohne dabei auf der Seite?
4. Side Trippy: zählt auch die Variante mit der Nase nach oben (im Käfig), und sind 200 °/s und 6 m Radius passende Grenzen?
5. Sbang-Kombo: ist „ein Eject und zwei Wender ohne Pause“ als Mindestmaß richtig? Gehören Pausen kopfüber mit geradem Horizont („Inverted Pause“) als eigener Baustein dazu?
6. Welche weiteren Dex-Tricks zuerst (Dex Trippy, Hell Cube, Oni Flip, Skydoc Flip, Chorizo 900, Betty Flip, Shimmy)? Vorgemerkt für 0.5.x.
7. 3D: welche 3D-Tricks außer Rückenflug und Tic-Toc (z. B. Shinobi in 3D, Matty kopfüber)?

## Trick-Katalog (0.5.0): Familien, Varianten, Quellen

Wunsch des Besitzers nach dem Spieltest: viele bekannte Tricks, auch Varianten, nicht nur die Grundformen. Der Katalog wurde
aus Trick-Listen und Tutorials zusammengestellt; Namen kommen nur aus den Quellen (oder sind als Kunstflug-Begriff bzw.
Vorschlag des Besitzers gekennzeichnet), Slang mit unklarer Definition steht unten zur Abstimmung.

| Quelle | Was daraus übernommen wurde |
|---|---|
| Tyrantt's Pro Whooper, *All Tricks* (Tricktionary mit Kategorien, Schwierigkeit und Punkten) – https://prowhooper.com/all-tricks/ | Familien (Open Air, Powerloops, Maverick Loops, Matty Flips, Split-S, Rewinds, Wall Tricks, Gaps) und die Definitionen von Matty Flip (https://prowhooper.com/matty-flip/), Anti Matty (/anti-matty/), Half Matty (/half-matty/), Power Matty (/power-matty/), 540 Split S (/540-split-s/), Splitback (/splitback/), Inverted Rewind (/inverted-rewind/), Segmented Flips/Rolls (/segmented-flips-rolls/), Vanny Roll (/vanny-roll/), Power Flip (/power-flip/), Reversed Power Flip (/reversed-power-flip/), Power Roll (/power-roll/), Inverted 360 Powerloop (/inverted-360-powerloop/), Side Loop (/side-loop/), Barani (/barani/), Immelmatt (/immelmatt/), Maverick Loop (/maverick-loop/), Snapback (/snapback/), Wall Ride (/wall-ride/), Reverse Knife Edge (/reverse-knife-edge/), Building Dive (/building-dive/) |
| WREKD, *A master list of FPV tricks* – https://wrekd.com/pages/a-master-list-of-fpv-tricks-and-manuevers-and-how-to-do-them | „Half Matty Flip / Reverse Split-S“, „Sideways Powerloop“, „Coin Drop – drop vertically while rotating“, „Sbang Tornado – a rapid roll into a controlled dive“, „Barani – a forward flip with a half-twist“, „Inverted Orbit (Trippy Spin)“ |
| Rotor Riot, *FPV Freestyle Tricktionary* – https://rotorriot.com/blogs/tutorials-guides/fpv-freestyle-tricktionary | „Roll/Barrel Roll … using power to spiral“, „Vanny Roll“, „Backwards Dive – descend using nose-up stall“, „Wall Ride – glide along walls without contact“, Wall Bonk und Slides als Kontakt-Tricks |
| Kunstflug: Wikipedia *Stall turn* – https://en.wikipedia.org/wiki/Stall_turn, *Aerobatic maneuver* – https://en.wikipedia.org/wiki/Aerobatic_maneuver | Hammerhead (Turn): senkrecht steigen, beim Strömungsabriss um die Hochachse auf die Nase drehen, senkrecht zurück; Außenlooping (Outside Loop) |
| 3D-Kunstflug mit Helikoptern, *3D Format – Set Manoeuvres* – https://rchelicopterhub.com/media/8262/set-manoeuvres.pdf; UAVMODEL, *FPV Freestyle Trick Progression* – https://blog.uavmodel.com/fpv-freestyle-trick-progression-2026-from-power-loops-to-matty-flips/ | Tic-Toc (schon da), Inverted Hover als Begriff; die Namen „3D-Flip“, „3D-Rolle“ und „3D-Power-Loop“ stammen aus der Vorgabe des Besitzers |

**Familien** (`TrickType.family()`, `trick.TrickFamily`, Reihenfolge = Reihenfolge im Tab und im Repertoire):

| Familie | Tricks |
|---|---|
| Flips & Rollen | Flip, Doppel-/Dreifachflip, Rolle, Doppel-/Dreifachrolle, Snap Roll, Korkenzieher, Segmentierter Flip, Vanny Roll |
| In der Luft | Juicy Flick, Rubik's Cube, Rewind, Flip-Rewind, Kopfüber hängen |
| Loops | Power Loop, Power Flip, Power Roll, Inverted 360 Power Loop, Maverick Loop, Side Loop, Barani, Immelmann, Immelmatt, Hammerhead |
| Matty & Split-S | Matty Flip, Anti Matty, Half Matty, Power Matty, Split-S, 540-Split-S, Splitback |
| Spins & Orbits | Yaw-Spin, Doppel-Yaw-Spin, Yaw-Spin kopfüber, Trippy Spin, McTwist, Coin Drop, Orbit, Side Trippy |
| Stürze | Sturzflug, Guillotine, Tornado, Rückwärts-Sturzflug |
| Nah am Hindernis | Knife Edge, Reverse Knife Edge, Wall Ride, Gap, Proximity |
| Sbang & Dex | Eject, Sbang-Kombo, Funky Split, Shinobi Flip |
| 3D-Modus | Rückenflug, Schweben kopfüber, Tic-Toc, Power Loop (3D), 3D-Flip, 3D-Rolle |

Damit erkennt Propwash **59 Tricks** (34 bisher, 25 neu). Varianten zeigen über `TrickType.variantOf()` auf ihren Grundtrick
(Doppelflip und 3D-Flip auf Flip, Power Flip und Side Loop auf Power Loop, Anti/Half/Power Matty auf Matty Flip, 540-Split-S und
Splitback auf Split-S, Flip-Rewind auf Rewind, Wall Ride und Reverse Knife Edge auf Knife Edge, Rückwärts-Sturzflug auf
Sturzflug, Coin Drop auf Yaw-Spin, Schweben kopfüber auf Rückenflug). Das Flugtraining zählt eine Variante für den Grundtrick
(`TrickType.withVariants`), das Trainings-HUD nennt dann den Grundtrick.

**Aliase:** Namen, die in der Szene für einen schon erkannten Trick stehen, sind keine eigenen Tricks, sondern stehen als
`propwash.trick.type.<key>.aka` im Tooltip und werden mitgesucht: Barrel Roll = Korkenzieher, Building Dive = Sturzflug,
Snapback = Immelmann, Inverted Rewind = Rewind, Inverted Orbit, Cyclone und Trippy Fall = Trippy Spin, Reverse Power Loop =
Matty Flip, Reverse Split-S = Half Matty, Sideways Power Loop = Side Loop, Stall Turn = Hammerhead, Outside Loop = Power Loop
(3D), Rolling Dive und Sbang Tornado = Tornado, Gigatine = Guillotine, Boost = Eject, Reversed Power Flip = Power Flip.

**Definitionen der neuen Tricks (prüfbar):**

| Trick | Bedingung |
|---|---|
| Matty Flip | Nick-Strich **vorwärts** 230–450°, ≥ 0,15 s Gasstoß (Gas ≥ 0,45) kopfüber oder mit der Oberseite gegen die alte Flugrichtung, Richtungsumkehr, ≤ 4 s |
| Anti Matty | wie Matty Flip, aber **rückwärts** genickt (Backflip über das Hindernis) |
| Power Matty | Nick vorwärts 450–810° (anderthalb Flips und das Ausleiten) mit Gasstoß und Richtungsumkehr, ≤ 4 s |
| Half Matty | halbe Rolle (110–270°), dann Nick vorwärts 110–300° mit ≥ 0,15 s Gasstoß; keine Richtungsumkehr, Nase danach ≥ 120° gedreht, am Ende ≥ 2 m/s mit dem Heck voran (Nase und Flugrichtung ≥ 120° auseinander), endet aufrecht, ≤ 4 s |
| 540-Split-S | Rolle 450–650°, dann Nick zurück 90–300°; Richtungsumkehr, ≥ 2 m Höhenverlust, ≤ 5 s |
| Splitback | Rolle 90–300°, dann Nick zurück 450–650° (Backflip und Durchziehen); Richtungsumkehr, ≥ 2 m Höhenverlust, ≤ 5 s |
| Flip-Rewind | wie Rewind, beide Striche ≥ 300° (ganzer Flip oder ganze Rolle hin und zurück) |
| Segmentierter Flip | zwei halbe Drehungen (110–270°) um dieselbe Achse in dieselbe Richtung; dazwischen 0,4–0,9 s Pause (vom Ende des ersten bis zum ersten schnellen Frame des zweiten Strichs), davon ≥ 70 % kopfüber und ohne Gasstoß; endet aufrecht. Ab 0,9 s Pause zählt Kopfüber hängen, mit Gasstoß ein Matty |
| Vanny Roll | Gier-Strich 120–240° · Roll-Strich 250–450° · Gier-Strich 120–240° in dieselbe Richtung (Gieren zusammen 300–420°); vorher ≥ 3 m/s waagerecht, die Bahn dreht ≤ 60°, einmal über Kopf, endet aufrecht |
| Coin Drop | aufrechter Yaw-Spin (≥ 330°, ≥ 60 % aufrecht, Bahn dreht ≤ 50 %), dabei Gas im Mittel ≤ 0,2 und ≥ 2 m mit ≥ 2 m/s gefallen; je Drehung mehr |
| Tornado | Roll-Strich ≥ 250° mit ≥ 150 °/s, ≥ 70 % der Zeit Nase ≥ 40° unten bei ≥ 4 m/s Sinken, ≥ 3 m gefallen; vor der Bedingung „über Kopf“ geprüft (eine Rolle im senkrechten Sturz kommt nie über Kopf); je Rolle mehr; zählt neben dem Sturzflug |
| Rückwärts-Sturzflug | wie Sturzflug (Sinken ≥ 7 m/s, ≥ 9 m/s, Bahn ≥ 44° abwärts, ≥ 0,7 s, ≥ 12 m), aber Nase ≥ 15° oben (`f.y ≥ 0,26`) und höchstens 107° gekippt (`up.y ≥ −0,3`); überwiegt im Sturz die Zeit mit Nase oben, wird er statt des Sturzflugs gewertet |
| Hammerhead | eigener Zustandsautomat: Steigen (≥ 2 m/s) mit Nase ≥ 37° oben, die Nase bleibt ≥ 0,3 s oben und die Drohne steigt ≥ 3 m; dann dreht die Nase innerhalb 1 s auf ≥ 30° unten, überwiegend durch Gieren (≥ 60° und ≥ 1,5 × Kippen), die Oberseite weicht ≤ 50° ab, und beim Kreuzen der Waagerechten steigt oder sinkt die Drohne höchstens 4 m/s (Strömungsabriss); danach ≥ 2 m gefallen und abgefangen (Sinken ≤ 1,5 m/s, `up.y ≥ 0,3`), Nase ≥ 120° gegen die alte Flugrichtung, alles in 6 s; ersetzt den Sturzflug |
| Maverick Loop | eigener Zustandsautomat: die Flugbahn dreht in der senkrechten Ebene der Anfangsrichtung ≥ 330° (zuerst nach oben), Tempo nie unter 1,5 m/s, nie über 101° gekippt (`up.y ≥ −0,2`), Seitwärtsanteil ≤ 50 %, die Lage nickt zusammen ≤ 270°, ≤ 6 s ab dem letzten waagerechten Stück, ≥ 2,5 m Höhenunterschied |
| Power Flip | Power Loop (Nick-Strich zurück ≥ 230°, Roll < 120°, Scheitel ≥ 2,5 m, 0,6–6 s, Bahn ≥ 200°) mit ≥ 270° mehr Nick als die Bahn dreht (Backflip am Scheitel) – oder Nick zurück · Flip vorwärts 250–450° · Nick zurück mit derselben Loop-Bahn (Reversed Power Flip) |
| Power Roll | Nick zurück (≥ 60°) · Rolle 250–450° · Nick zurück (≥ 60°) mit Loop-Bahn wie Power Loop, über Kopf, endet aufrecht |
| Inverted 360 Power Loop | halber Nick zurück · Gier-Strich ≥ 330° kopfüber · halber Nick zurück mit Loop-Bahn wie Power Loop (ohne Loop-Bahn bleibt es ein McTwist) |
| Side Loop | Roll-Strich ≥ 300° (Nick < 120°) mit Loop-Bahn wie Power Loop in der senkrechten Ebene der Flugrichtung, keine Richtungsumkehr |
| Barani | Nick zurück 90–270° · Gier-Strich 120–250° zu ≥ 50 % kopfüber · Nick vorwärts 90–270°; ≥ 1,5 m gestiegen, Bahn dreht ≥ 200°, Nase danach ≥ 120° gedreht, endet aufrecht; vor dem Funky Split geprüft |
| Immelmatt | Kombi-Trick: Immelmann, dann Matty Flip, Anti Matty oder Power Matty mit ≤ 0,8 s Pause; Bonus wie Shinobi Flip |
| Wall Ride | Knife Edge (vorwärts) mit ≥ 50 % der Zeit ≤ 1 m Abstand seitlich oder oben (Strahlen nach unten zählen nicht); ersetzt die Knife Edge, Proximity zählt während der Seitenlage erst ab 1,5 s |
| Reverse Knife Edge | Knife Edge mit dem Heck voran (Nase und Flugrichtung waagerecht ≥ 134° auseinander); wechselt die Richtung, endet die Knife Edge |
| Schweben kopfüber (3D) | wie Rückenflug (3D), dabei im Mittel ≤ 1,5 m/s waagerecht |
| 3D-Flip / 3D-Rolle | Flip bzw. Rolle wie bisher, ≥ 90 % der Zeit im 3D-Modus und ≥ 50 % der Zeit kopfüber mit Umkehrschub (Gas ≤ −0,05); je Umdrehung mehr |
| Power Loop (3D) | Nick-Strich ≥ 300°, ≥ 90 % im 3D-Modus, ≥ 40 % der Zeit mit Umkehrschub, Bahn dreht ≥ 270°, 0,6–6 s – auch nach unten als Außenlooping mit der Oberseite nach außen |

**Geprüft, aber nicht aufgenommen:**

| Kandidat | Warum nicht |
|---|---|
| Inverted Dive | keine verlässliche Definition (die Fundstellen widersprechen sich); ein Sturz kopfüber mit Nase unten zählt als Sturzflug |
| Trippy Fall, Inverted Orbit, Rolling Dive, Barrel Roll, Reverse Power Loop | gleiche Bewegung wie Trippy Spin, Tornado, Korkenzieher bzw. Matty Flip – Alias statt Doppel-Trick |
| Half-Pipe- und Bowl-Moves | in den Quellen kein beschriebener Trick, bräuchte eine Erkennung der Geometrie (Rampe, Schüssel) |
| Wall Tap, Ceiling Tap, Roll Tap, Loop Tap, Wall Bonk, Slides | verlangen eine Berührung; in Propwash macht jede Berührung einen Trick ungültig (sonst zählten Abstürze); als eigene Kontakt-Familie für später vorgemerkt |
| Cradle, Whiplash, Side-Lock Rewind, Jump Rope, Cinnamon Roll, Pole Dance | Kern ist die Blickrichtung zu einem Objekt; ohne Objekt sind sie kinematisch normales Bremsen oder Kurven (Gegenprobe „Bremsen mit 100 Grad Nick“) |
| Power Split, Power Swap, Power Switch, Loop to Matty, Immelloop, Split Yaw, Matty Twister, Matty Roll, Mavvy Roll, Mavvelmann, Donkey Loop, Flipani, Rollani, Forani, True Barani, Trippy Switch, Double Rolling Trippy Spin, Inverted Uh-uh, Stall-Rewinds | Kombinationen erkannter Bausteine oder sehr selten; Kandidaten für 0.5.x nach Abstimmung |
| Stall, Throwback | ein Stall ist keine Drehung und vom Schweben nicht zu unterscheiden |

**Vom Besitzer mit dem Creator (Dronarchy) abzustimmen** (bis dahin gilt die Propwash-Definition oben):

8. Matty-Richtung: Matty Flip = vorwärts, Anti Matty = rückwärts (Pro Whooper, Rotor Riot). Sagt die Dex-Szene dazu auch so – und ist „Matty out“ beim Shinobi beides?
9. Tornado: Rolle im Sturz (WREKD „Sbang Tornado“) oder Yaw-Spin im Sturz? Propwash: Rolle mit der Nase nach unten, sieht wie Drehen um die Senkrechte aus.
10. Coin Drop: aufrechter Yaw-Spin im freien Fall (Gas weg) – nur ein Einzeiler bei WREKD.
11. Rückwärts-Sturzflug: Fallen mit Nase ≥ 15° oben (Rotor Riot „nose-up stall“) – oder ist das Rückwärtsfliegen an einer Wand nach unten gemeint?
12. Hammerhead: Kunstflug-Begriff; in der FPV-Szene so genannt, und ist der Turn mit Gieren richtig?
13. Barani: Pro Whooper (halber Power Loop, 180° Gieren kopfüber, Matty) gegen Trampolin/WREKD (Vorwärtssalto mit halber Schraube). Propwash: Pro Whooper.
14. 3D-Flip, 3D-Rolle, Power Loop (3D), Schweben kopfüber: so in der 3D-Szene benannt, und zählt beim 3D-Flip der Wechsel auf Umkehrschub?
15. Segmentierter Flip: Pause 0,4–0,9 s; darüber zählt Kopfüber hängen.
16. Maverick Loop und Wall Ride: Pro-Whooper-Begriffe; reicht beim Wall Ride ≤ 1 m in Seitenlage, oder muss die Unterseite zur Wand zeigen?

## Repertoire

Aus dem Code erzeugt (`TrickType.REPERTOIRE`, Grundpunkte, Namen und Erklärungen aus `common/src/lang/trick`); der JUnit-Test
`trick.TrickDocsTest` vergleicht den Block und legt den erwarteten Stand unter `common/build/docs/trick-repertoire.md` ab.

<!-- trick-repertoire:begin -->
| Familie | Trick | Englisch | Schlüssel | Punkte | Erklärung im Tab |
|---|---|---|---|---|---|
| Flips & Rollen | Flip | Flip | `flip` | 100 | Eine ganze Drehung um die Nickachse, vorwärts oder rückwärts. Zählt ab etwa 250°. |
| Flips & Rollen | Doppelflip | Double flip | `double_flip` | 250 | Zwei Flips am Stück, ohne abzusetzen. |
| Flips & Rollen | Dreifachflip | Triple flip | `triple_flip` | 450 | Drei Flips am Stück, ohne abzusetzen. |
| Flips & Rollen | Rolle | Roll | `roll` | 100 | Eine ganze Drehung um die Rollachse, links oder rechts herum. Zählt ab etwa 250°. |
| Flips & Rollen | Doppelrolle | Double roll | `double_roll` | 250 | Zwei Rollen am Stück, ohne abzusetzen. |
| Flips & Rollen | Dreifachrolle | Triple roll | `triple_roll` | 450 | Drei Rollen am Stück, ohne abzusetzen. |
| Flips & Rollen | Snap Roll | Snap roll | `snap_roll` | 150 | Knüppel in die Ecke: Rollen und Nicken gleichzeitig mit voller Rate, die Drohne überschlägt sich um eine schräge Achse – wie die gerissene Rolle im Kunstflug. |
| Flips & Rollen | Korkenzieher | Corkscrew | `corkscrew` | ab 200 | Eine langsame Rolle mit Gas, während du weiterfliegst: die Drohne schraubt sich um ihre Linie (Fassrolle). Jede weitere Windung zählt mit. |
| Flips & Rollen | Segmentierter Flip | Segmented flip | `segmented` | 150 | Halber Flip oder halbe Rolle, kurz kopfüber anhalten (0,4–0,9 s), dann in dieselbe Richtung zu Ende drehen. |
| Flips & Rollen | Vanny Roll | Vanny Roll | `vanny_roll` | 200 | Im Vorwärtsflug schnell 180° gieren, rückwärts eine ganze Rolle drehen und mit 180° Gieren in dieselbe Richtung wieder nach vorn schauen. |
| In der Luft | Juicy Flick | Juicy Flick | `juicy_flick` | 200 | Mit Schwung vorwärts in einen halben Flip bis kopfüber, dann eine halbe Rolle zurück aufrecht: danach schaust du zurück, woher du kamst. |
| In der Luft | Rubik's Cube | Rubik's Cube | `rubiks_cube` | 450 | Halber Flip, ganze Rolle, halber Flip in dieselbe Richtung – oder eine andere schnelle Folge aus halben Flips und Rollen mit wechselnden Achsen. |
| In der Luft | Rewind | Rewind | `rewind` | ab 150 | Über Kopf drehen und auf demselben Weg zurück, als liefe das Video rückwärts – eine halbe Rolle hin und zurück oder ein ganzer Flip-Rewind. |
| In der Luft | Flip-Rewind | Flip rewind | `flip_rewind` | 300 | Ein ganzer Flip oder eine ganze Rolle und sofort auf demselben Weg zurück – als liefe das Video rückwärts. |
| In der Luft | Kopfüber hängen | Inverted hang | `inverted_hang` | ab 120 | Mindestens 0,9 s kopfüber hängen, ohne zu drehen. Länger bringt mehr. |
| Loops | Power Loop | Power loop | `power_loop` | 250 | Mit Gas rückwärts durch einen ganzen senkrechten Looping, meist über ein Hindernis, dann in dieselbe Richtung weiter. |
| Loops | Power Flip | Power flip | `power_flip` | 500 | Power Loop mit einem Flip oben am Scheitel – rückwärts oder vorwärts –, danach den Loop zu Ende fliegen. |
| Loops | Power Roll | Power roll | `power_roll` | 550 | Power Loop mit einer ganzen Rolle oben am Scheitel, danach den Loop zu Ende fliegen. |
| Loops | Inverted 360 Power Loop | Inverted 360 power loop | `inverted_360_power_loop` | 650 | Power Loop mit einem ganzen Yaw-Spin kopfüber oben am Scheitel, danach den Loop zu Ende fliegen. |
| Loops | Maverick Loop | Maverick loop | `maverick_loop` | 200 | Ein Looping über ein Hindernis, bei dem die Kamera die ganze Zeit nach vorn schaut: die Drohne kippt nur leicht und kommt nie auf den Rücken. |
| Loops | Side Loop | Side loop | `side_loop` | 300 | Seitlich anfliegen und mit der Rolle statt mit dem Nick einen Looping über das Hindernis fliegen. |
| Loops | Barani | Barani | `barani` | 700 | Halber Power Loop, oben kopfüber 180° gieren und mit einem Matty Flip unter dem Hindernis hindurch – am Ende schaust du zurück. |
| Loops | Immelmann | Immelmann | `immelmann` | 200 | Das Gegenstück zum Split-S: halber Looping nach oben, dann eine halbe Rolle aufrecht – kehrt die Richtung um und gewinnt Höhe. |
| Loops | Immelmatt | Immelmatt | `immelmatt` | 200 | Kombi: ein Immelmann direkt in einen Matty Flip, höchstens 0,8 s dazwischen. Bonus auf beide Tricks. |
| Loops | Hammerhead | Hammerhead | `hammerhead` | 250 | Steil hochziehen, oben mit Gieren die Nase nach unten drehen, zurück stürzen und abfangen – der Turn aus dem Kunstflug. |
| Matty & Split-S | Matty Flip | Matty flip | `matty_flip` | 350 | Ein ganzer Flip mit Gasstoß, während die Drohne nach hinten oder kopfüber zeigt – das kehrt die Flugrichtung um: danach fliegst du zurück, woher du kamst. |
| Matty & Split-S | Anti Matty | Anti Matty | `anti_matty` | 375 | Der Matty Flip rückwärts: über ein Hindernis, Gas weg, ein ganzer Backflip und mit Gas in die Waagerechte – danach fliegst du mit dem Heck voran zurück, woher du kamst. |
| Matty & Split-S | Half Matty | Half Matty | `half_matty` | 400 | Halbe Rolle auf den Rücken, dann die zweite Hälfte eines Matty Flips nach vorn mit Gas: du fliegst mit dem Heck voran weiter und schaust zurück. |
| Matty & Split-S | Power Matty | Power Matty | `power_matty` | 500 | Ein Matty Flip mit einem Flip mehr: Gas weg, anderthalb Flips vorwärts, dann mit Gas weiterdrehen, bis du mit dem Heck voran zurückfliegst. |
| Matty & Split-S | Split-S | Split-S | `split_s` | 200 | Halbe Rolle auf den Rücken, dann durch einen halben Looping nach unten ziehen: kehrt die Richtung um und kostet Höhe. |
| Matty & Split-S | 540-Split-S | 540 Split-S | `split_s_540` | 300 | Anderthalb Rollen auf den Rücken, dann wie beim Split-S nach unten durchziehen: kehrt die Richtung um und kostet Höhe. |
| Matty & Split-S | Splitback | Splitback | `splitback` | 350 | Split-S mit Extra-Backflip: halbe Rolle auf den Rücken, ein ganzer Backflip und dann nach unten durchziehen. |
| Spins & Orbits | Yaw-Spin | Yaw spin | `yaw_spin` | 80 | Eine ganze Drehung um die Hochachse, ohne eine Kurve zu fliegen. |
| Spins & Orbits | Doppel-Yaw-Spin | Double yaw spin | `double_yaw_spin` | 180 | Zwei Yaw-Spins am Stück. |
| Spins & Orbits | Yaw-Spin kopfüber | Inverted yaw spin | `inverted_yaw_spin` | ab 250 | Kopfüber eine ganze Drehung um die Hochachse, dann wieder aufrecht. Jede weitere Drehung zählt mit. |
| Spins & Orbits | Trippy Spin | Trippy Spin | `trippy_spin` | ab 350 | Kopfüber um die Hochachse drehen, während du fällst oder um einen Punkt kreist. Jede weitere Drehung zählt mit. |
| Spins & Orbits | McTwist | McTwist | `mctwist` | 500 | Halber Flip auf den Rücken, ein ganzer Yaw-Spin, dann den Flip in dieselbe Richtung zu Ende drehen – benannt nach dem Skateboard-Trick. |
| Spins & Orbits | Coin Drop | Coin drop | `coin_drop` | ab 200 | Gas weg und fallen lassen, dabei aufrecht um die Hochachse drehen – wie eine fallende Münze. Jede weitere Drehung zählt mit. |
| Spins & Orbits | Orbit | Orbit | `orbit` | 250 | Mit der Nase nach innen um einen Punkt kreisen, einmal je voller Runde. |
| Spins & Orbits | Side Trippy | Side trippy | `side_trippy` | ab 400 | Auf der Seite liegend eng um einen Punkt kreisen: der Horizont steht senkrecht und dreht sich. Eine ganze Runde, jede weitere zählt mit. |
| Stürze | Sturzflug | Dive | `dive` | ab 150 | Nase runter, mindestens 12 m schnell und steil fallen, dann abfangen. Jeder Meter über 10 m zählt. |
| Stürze | Guillotine | Guillotine | `guillotine` | ab 450 | Hoch steigen, senkrecht stürzen und erst kurz über dem Boden einen Flip drehen, der den Sturz abfängt. Ab 8 m Fallhöhe, höher bringt mehr. |
| Stürze | Tornado | Tornado | `tornado` | ab 300 | Im steilen Sturzflug mit der Nase nach unten um die eigene Längsachse rollen. Jede weitere Drehung zählt mit. |
| Stürze | Rückwärts-Sturzflug | Backwards dive | `backwards_dive` | ab 200 | Mit der Nase nach oben fallen: Gas weg, nach hinten gekippt mindestens 12 m schnell sinken, dann abfangen. |
| Nah am Hindernis | Knife Edge | Knife edge | `knife_edge` | ab 150 | Auf die Seite rollen und mindestens eine halbe Sekunde geradeaus weiterfliegen. Länger bringt mehr. |
| Nah am Hindernis | Reverse Knife Edge | Reverse knife edge | `reverse_knife_edge` | ab 300 | Knife Edge rückwärts: auf der Seite liegend mit dem Heck voran geradeaus fliegen, mindestens eine halbe Sekunde. |
| Nah am Hindernis | Wall Ride | Wall ride | `wall_ride` | ab 200 | Auf der Seite liegend dicht an einer Wand oder Decke entlanggleiten – höchstens 1 m Abstand, ohne sie zu berühren. |
| Nah am Hindernis | Gap (Lücke) | Gap | `gap` | ab 200 | Schnell durch eine Lücke von höchstens zwei Blöcken Breite oder Höhe, ohne sie zu berühren. Enger bringt mehr. |
| Nah am Hindernis | Proximity (knapp vorbei) | Proximity | `proximity` | ab 100 | Schnell weniger als einen Meter neben oder unter Blöcken fliegen. Näher und länger bringt mehr. |
| Sbang & Dex | Eject | Eject | `eject` | 60 | Die Sbang-Grundbewegung: kurz kräftig kippen, voll Gas geben und sofort zurück in die Waagerechte schnappen – die Drohne schießt flach davon. |
| Sbang & Dex | Sbang-Kombo | Sbang combo | `sbang` | 300 | Sbang ohne tote Strecke: ein Eject und mindestens zwei schnelle Wender (Split-S, Juicy Flick, Rewind …), höchstens 0,8 s dazwischen. Bonus auf die Tricks. |
| Sbang & Dex | Funky Split | Funky split | `funky_split` | 250 | Split-S quer zum Hindernis: halbe Rolle auf den Rücken, kopfüber eine halbe Flachdrehung (Gieren), dann aufrecht – danach schaust du zurück. |
| Sbang & Dex | Shinobi Flip | Shinobi flip | `shinobi` | 600 | Dex-Kombi: Split-S oder Funky Split, direkt ein Rubik's Cube und mit einem Matty Flip hinaus. Bonus auf die drei Tricks. |
| 3D-Modus | Rückenflug (3D) | Inverted flight (3D) | `inverted_flight` | ab 150 | Nur im 3D-Modus: mit Umkehrschub kopfüber fliegen oder schweben, ohne zu fallen – mindestens 1 s. Länger bringt mehr. |
| 3D-Modus | Schweben kopfüber (3D) | Inverted hover (3D) | `inverted_hover` | ab 200 | Nur im 3D-Modus: kopfüber mit Umkehrschub auf der Stelle schweben, mindestens 1 s, kaum Fahrt. Länger bringt mehr. |
| 3D-Modus | Tic-Toc (3D) | Tic-toc (3D) | `tic_toc` | ab 400 | Nur im 3D-Modus: mindestens vier halbe Flips im Wechsel vor und zurück, kopfüber mit Umkehrschub gehalten. Jeder weitere halbe Flip zählt mit. |
| 3D-Modus | Power Loop (3D) | Power loop (3D) | `power_loop_3d` | 450 | Nur im 3D-Modus: ein ganzer Looping mit Umkehrschub – auch als Außenlooping nach unten, mit der Oberseite nach außen. |
| 3D-Modus | 3D-Flip | 3D flip | `flip_3d` | ab 200 | Nur im 3D-Modus: ein Flip, bei dem du kopfüber auf Umkehrschub wechselst, statt durchzufallen. Jeder weitere Flip zählt mit. |
| 3D-Modus | 3D-Rolle | 3D roll | `roll_3d` | ab 200 | Nur im 3D-Modus: eine Rolle, bei der du kopfüber auf Umkehrschub wechselst, statt durchzufallen. Jede weitere Rolle zählt mit. |
<!-- trick-repertoire:end -->

## Erkennung (`TrickDetector`)

**Drehungen messen.** Aus der Lageänderung zwischen zwei Frames im Körpersystem (`dq = q_alt⁻¹·q_neu`, Achsen wie Spec 2.3:
Nick = +x (positiv = Nase runter, vorwärts), Roll = +z (positiv = rechts), Gier = −y (positiv = rechts)). Das ist unabhängig
von der Bildrate und robust gegen Achs-Kopplung: Gieren während eines Flips ändert die Nick-Summe im Körpersystem nicht.
Rücksetzen ohne Wertung: Zeitsprung > 0,3 s, Sprung > max(20 m, 3·v·dt) (Teleport, Reset), Disarm, `assisted`. Nach einem
Absturz wartet die Erkennung, bis die Drohne 0,5 s ruhig ist. Jede Berührung (`impactCount` steigt oder `grounded`) während
eines Tricks macht ihn ungültig.

**Manöver-Fenster (`ManeuverLog`).** Ein Fenster öffnet, sobald sich die Drohne kippend dreht (Roll/Nick ≥ 130 °/s), giert
(≥ 150 °/s) oder über Kopf ist (`up.y < −0,2`). Es schreibt je Frame Drehung, Lage, Position, Geschwindigkeit, Gas, Abstand und
Berührung mit (höchstens 250 Einträge pro Sekunde, schnellere Frames werden zusammengefasst). Es schließt, wenn die Drohne ruhig
und nicht über Kopf ist: aufrecht (`up.y ≥ 0,5`) nach 0,05 s, sonst nach 0,3 s; über Kopf schließt es nie, nach 8 s wird
ausgewertet und neu begonnen. Eine bestimmte Neigung muss nicht erreicht werden – schneller Flug mit 80° Neigung ist ruhig.

**Drehstriche (`Stroke`).** Beim Schließen zerlegt `ManeuverClassifier` das Fenster in Striche: aufeinanderfolgende Frames mit
Kipprate ≥ 130 °/s, deren Richtung in der Nick-Roll-Ebene höchstens 55° von der Summe des Strichs abweicht (ein Flip mit 30 %
Roll-Anteil bleibt ein Strich), oder Gier-Striche (≥ 150 °/s, gleiche Richtung). Langsame Frames davor und dazwischen zählen
zum Strich, wenn sie in seine Richtung drehen (An- und Auslauf gehen nicht verloren); 0,3 s Pause beendet ihn. Striche unter 40°
fallen weg, gleichgerichtete Striche mit höchstens 0,35 s Abstand werden zusammengelegt (ein Flip in vier Vierteln ist ein Flip).
Art eines Kipp-Strichs: Nick oder Roll nach der größeren Summe, „diagonal“, wenn die kleinere ≥ 60 % der größeren ist.

**Gruppieren.** Eine dynamische Programmierung teilt die Strich-Folge in aufeinanderfolgende Gruppen (höchstens 6 Striche,
Lücken ≤ 0,6 s, ≤ 6 s) und wählt die Aufteilung mit den meisten Grundpunkten, bei Gleichstand die mit weniger, dafür größeren
Tricks. Jeder Strich gehört zu höchstens einem Trick – nichts wird doppelt gezählt. Eine Gruppe ist ein Trick, wenn sie eine der
Regeln erfüllt (Reihenfolge = Vorrang innerhalb einer Gruppe):

| Trick | Gruppe | Bedingung |
|---|---|---|
| McTwist | ein Nick-Strich mit Gieren, oder halber Flip · Gier-Strich · halber Flip gleiche Richtung | Nick ≥ 300° und Gieren ≥ 300°, über Kopf (`up.y < −0,5`), endet aufrecht (`up.y ≥ 0,15`) |
| Power Loop (3D) | Nick-Strich ≥ 300° | vor Matty und Power Loop geprüft, Bedingungen siehe „Trick-Katalog“ |
| Matty Flip / Anti Matty | Nick-Strich 230–450° | ≥ 0,15 s Gas ≥ 0,45 in Rückenlage (`up.y < −0,1`) oder mit der Oberseite gegen die alte Flugrichtung; Richtungsumkehr (waagerecht vorher ≥ 3 m/s, nachher ≥ 2 m/s, Skalarprodukt < −0,3·|v₀|·|v₁|); ≤ 4 s; vorwärts genickt Matty Flip, rückwärts Anti Matty |
| Power Matty | Nick-Strich vorwärts 450–810° | wie Matty Flip |
| Power Loop / Power Flip | Nick-Strich ≥ 230°, Roll < 120° | Scheitel ≥ 2,5 m über Start und Ende, 0,6–6 s, Flugbahn dreht in der senkrechten Ebene ≥ 200°, keine Umkehr (bei mehr als 450° Nick, also mehreren Loops hintereinander, zählt die Umkehr nicht; das ist dann kein Matty Flip); nickt die Drohne ≥ 270° mehr, als die Bahn dreht, ist es ein Power Flip |
| Side Loop | Roll-Strich ≥ 300° | wie Power Loop mit Rolle statt Nick |
| Half Matty | Rolle 110–270° dann Nick vorwärts 110–300° | Bedingungen siehe „Trick-Katalog“ |
| 540-Split-S / Splitback | Rolle 450–650° dann Nick zurück 90–300° bzw. Rolle 90–300° dann Nick zurück 450–650° | Richtungsumkehr, ≥ 2 m Höhenverlust, ≤ 5 s |
| Split-S | Rolle 90–300° dann Nick zurück 90–300° (oder beides gleichzeitig) | Richtungsumkehr, ≥ 2 m Höhenverlust, ≤ 4 s |
| Immelmann | Nick zurück 110–270° dann Rolle 110–270° | Richtungsumkehr, ≥ 1,5 m höher als vorher, endet aufrecht |
| Juicy Flick | Nick vorwärts 110–270° dann Rolle 110–270° | zusammen ≥ 300° (ein Nick von 112° aus der Schräglage mit einer Rolle zum Aufrichten ist kein Juicy Flick), endet aufrecht, ≤ 2,5 s |
| Rewind / Flip-Rewind | zwei entgegengesetzte Striche gleicher Art | beide ≥ 150°, Unterschied ≤ max(60°, 35 %), über Kopf, endet aufrecht; beide ≥ 300° ist es ein Flip-Rewind |
| Segmentierter Flip | zwei gleichgerichtete halbe Striche | eine Pause von 0,4–0,9 s kopfüber ohne Gasstoß trennt sie (dann werden sie auch nicht zu einem Strich zusammengelegt und dürfen weiter als 0,6 s auseinanderliegen); nur wenn die Gruppe sonst ein einfacher Flip oder eine Rolle wäre |
| Power Roll / Reversed Power Flip | Nick zurück · Rolle bzw. Flip vorwärts · Nick zurück | vor Tic-Toc und Rubik's Cube geprüft, Bedingungen siehe „Trick-Katalog“ |
| Vanny Roll | Gier-Strich · Roll-Strich · Gier-Strich | Bedingungen siehe „Trick-Katalog“ |
| Rubik's Cube | ≥ 3 Kipp-Striche | jeder ≥ 70°, zusammen ≥ 540°, ≥ 2 Wechsel zwischen Nick und Roll, ≥ 2 halbe (nicht ganzzahlige) Drehungen, endet aufrecht, ≤ 4 s; ein kurzer letzter Strich unter 70° nach mindestens drei Strichen gilt als Aufrichten und gehört dazu; keine Rolle im Sturzflug (Tornado) |
| Snap Roll | diagonaler Strich | ≥ 300°, mittlere Rate ≥ 500 °/s |
| Korkenzieher | Roll-Strich ≥ 330° | mittlere Rate ≤ 450 °/s, mittleres Gas ≥ 0,3, nie unter 4 m/s, Weg weicht ≤ 75° von der alten Flugrichtung ab; je volle Windung mehr Punkte |
| Tornado | Roll-Strich ≥ 250° im steilen Sturz | vor der Bedingung „über Kopf“ geprüft, Bedingungen siehe „Trick-Katalog“ |
| 3D-Flip / 3D-Rolle | wie Flip/Rolle | im 3D-Modus mit Umkehrschub kopfüber, vor dem Korkenzieher geprüft |
| Flip ×1/×2/×3 | Nick-Strich (auch diagonal und langsam) | ≥ 250°, mittlere Rate ≥ 150 °/s, über Kopf; Umdrehungen = 1 + ⌊(Winkel − 250°)/360°⌋, höchstens 3 |
| Rolle ×1/×2/×3 | wie Flip mit Roll | |
| Yaw-Spin kopfüber | Gier-Strich, wahlweise mit halber Rolle/halbem Flip davor und danach (90–270°) | ≥ 330° Gieren (Summe der Gruppe), ≥ 60 % der Spin-Zeit über Kopf, Roll/Nick ≤ 50 % des Gierens, Bahn dreht ≤ 50 % |
| Trippy Spin | wie Yaw-Spin kopfüber | dabei ≥ 4 m gefallen oder kopfüber um einen Punkt gekreist (Bahn dreht ≥ 270° und 50–200 % des Gierens) |
| Yaw-Spin / Doppel-Yaw-Spin / Coin Drop | Gier-Strich | ≥ 330° (ab 690° doppelt), ≥ 60 % aufrecht (`up.y ≥ 0,3`), Roll/Nick ≤ 50 %, Bahn dreht ≤ 50 % (sonst ist es eine Kurve); mit Gas ≤ 0,2 und ≥ 2 m Fall ein Coin Drop |
| McTwist / Inverted 360 Power Loop | halber Flip · Gier-Strich · halber Flip gleiche Richtung | mit Loop-Bahn wie Power Loop und Nick zurück ein Inverted 360 Power Loop |
| Barani | Nick zurück · Gier-Strich · Nick vorwärts | vor dem Funky Split geprüft, Bedingungen siehe „Trick-Katalog“ |
| Side Trippy | Nick-Strich | vor allen anderen Regeln geprüft (auch ohne Moment über Kopf), Bedingungen siehe „Neue Schule“ |
| Funky Split | halbe Drehung · Gier-Strich · halbe Drehung | vor Yaw-Spin kopfüber und Trippy Spin, Bedingungen siehe „Neue Schule“ |
| Tic-Toc (3D) | ≥ 4 Kipp-Striche | vor dem Rubik's Cube, Bedingungen siehe „Neue Schule“ |

Ein Rubik's Cube zählt keinen Nick-Strich, der eigentlich eine Kurve auf der Seite ist (≥ 50 % auf der Seite und die Nase dreht
um ≥ 60 % des Strichs um die Hochachse) – sonst wurde ein Side Trippy oder eine enge Kurve mit Ein- und Ausrollen zum Rubik's Cube.

Kipp-Tricks brauchen immer einen Moment über Kopf (`up.y < −0,5`), außer Side Trippy und Tornado (eine Rolle im senkrechten
Sturz bleibt auf der Seite): Kurven mit 80° Schräglage drehen im Körpersystem zwar
um die Nickachse, kommen aber nie über Kopf und zählen nie. Halbe Drehungen allein (halber Flip, halbe Rolle) sind kein
Trick, sie werden erst als Teil von Juicy Flick, Split-S, Immelmann, Rewind, Rubik's Cube oder Spin gewertet.

**Kopfüber hängen.** Im Fenster die längste Zeit über Kopf (`up.y < −0,5`) ohne Drehung (Kipp < 130 °/s, Gier < 150 °/s) und
ohne Berührung; ab 0,9 s. Spins kopfüber zählen dadurch nicht zusätzlich als Hänger. Im 3D-Modus wird daraus ein Rückenflug,
wenn die Zeit mindestens 1 s dauert, zu ≥ 60 % mit Umkehrschub geflogen wurde und die Drohne höchstens 1,5 m gefallen ist.

Im 3D-Modus wird aus dem Rückenflug ein **Schweben kopfüber**, wenn die Drohne dabei im Mittel höchstens 1,5 m/s waagerecht
fliegt. Ein Hänger innerhalb eines segmentierten Flips zählt nicht zusätzlich.

**Hammerhead, Maverick Loop, Rückwärts-Sturzflug.** Hammerhead und Maverick Loop sind eigene Zustandsautomaten neben den
Fenstern (ein Maverick Loop kommt nie über Kopf, beim Hammerhead dreht die Nase durch Gieren); der Rückwärts-Sturzflug teilt
sich den Automaten mit dem Sturzflug. Bedingungen siehe „Trick-Katalog“. Ein Hammerhead beendet den Sturzflug ohne Wertung.

**Wall Ride und Reverse Knife Edge** laufen im Automaten der Knife Edge mit (Richtung und Wandabstand je Frame).

**Eject.** Eigener Zustandsautomat neben den Manöver-Fenstern (die Kippbewegung eines Ejects geht nie über Kopf, im Fenster wird
daraus kein Trick); Bedingungen siehe „Neue Schule“.

**Guillotine.** Der Detektor führt einen Verlauf der letzten 8,5 s (Zeit, Höhe, Sinken, Nase, Kipprate, Nick-Drehung je
1/60 s). Endet ein Fenster mit einem Flip, prüft er rückwärts vom Flip-Ende, ob davor ein ruhiger Sturz lag (Bedingungen siehe
„Neue Schule“). Dann hält er den Flip bis zu 0,8 s zurück: wird der Sturz abgefangen, meldet er die Guillotine (Fallhöhe = höchster
minus tiefster Punkt) und beendet den Sturzflug ohne Wertung; sonst kommen Flip und Sturzflug wie bisher. Ein Sturzflug, der
abgefangen wird, während noch ein Fenster offen ist, wird bis zum Schließen des Fensters gehalten, damit er nicht neben der
Guillotine zählt. Ein Power Loop dreht im Sturz weiter und hat danach höchstens etwa 90° bis zur Waagerechten – er wird nie zur
Guillotine.

**Kombi-Tricks** (`LineRecognizer`). Sbang-Kombo und Shinobi Flip sind Namen für Folgen aus erkannten Tricks. Der Erkenner sieht
jeden neuen Trick (nach Beginn sortiert), führt die laufende Linie (Pausen ≤ 0,8 s) und meldet den Kombi-Trick zusammen mit dem
letzten Baustein. Er ist ein Bonus: Kombo- und Kettenzähler wachsen nicht, die Punkte zählen in die Kombo, die Meldung hängt den
Namen an. Berührung, Absturz oder Disarm verwerfen ihn wie jeden noch nicht bestätigten Trick.

**Sturzflug.** Steil = Nase ≥ 40° unten (`f.y ≤ −0,64`), Sinken ≥ 7 m/s, Tempo ≥ 9 m/s, Bahn mindestens 44° abwärts
(`v.y ≤ −0,7·|v|`). Oberkante = letzte Höhe mit Sinken < 1,5 m/s. Gewertet beim Abfangen (Sinken < 1,5 m/s) mit
≥ 0,7 s steil und ≥ 12 m Fallhöhe; ohne Berührung.

**Knife Edge.** Auf der Seite (`|up.y| ≤ 0,3`, also ≥ 72° Schräglage), Nase höchstens 30° geneigt, ≥ 5 m/s, Flugrichtung
höchstens 45° neben der Nase, kaum Drehung (wie oben), mindestens 0,5 s am Stück (0,1 s Lücke erlaubt). Die Bahn darf sich
insgesamt höchstens 25° und höchstens 25 °/s drehen – eine Kurve mit viel Schräglage dreht viel schneller und zählt nicht.

**Orbit.** Waagerecht ≥ 2,5 m/s, `up.y ≥ 0,3`, Nase quer zur Flugrichtung (Winkel 50–130°), Nase dreht ≥ 20 °/s und die
Flugbahn gleichsinnig mit dem 0,5- bis 2-Fachen der Nasendrehung. Bis zu 0,4 s Abweichung werden überbrückt; je volle
360° ein Orbit.

**Gap.** Ab 4 m/s ein Paar gegenüberliegender Abstände, beide ≤ 1,6 m und zusammen ≤ 2,2 m (Lücke höchstens zwei Blöcke
breit oder hoch). Gewertet beim Verlassen (0,1 s frei), wenn der Durchflug ≤ 3 s dauerte und nichts berührt wurde.

**Proximity.** Ab 7 m/s ein Abstand ≤ 0,9 m zu Blöcken seitlich oder oben (Richtungen mit `y < −0,7` = Boden zählen nicht),
mindestens 0,35 s am Stück (0,12 s Lücke erlaubt), ohne Berührung. Enthielt die Passage ein Gap, zählt sie erst ab 1,5 s.

Jeder Trick trägt Anfang und Ende (`Trick#start`, `Trick#time`), daraus entstehen die Ketten.

## Punkte, Kombos und Ketten (`TrickScore`)

| Trick | Punkte |
|---|---|
| Flip, Rolle | 100 |
| Doppelflip, Doppelrolle | 250 |
| Dreifachflip, Dreifachrolle | 450 |
| Snap Roll | 150 |
| Korkenzieher | 200, +100 je weitere Windung (höchstens +200) |
| Rewind | 150 + 0,5 je Grad über 180° (höchstens +200; Flip-Rewind 240) |
| Juicy Flick | 200 |
| Rubik's Cube | 450 |
| Power Loop | 250 |
| Matty Flip | 350 |
| Split-S, Immelmann | 200 |
| Yaw-Spin / Doppel-Yaw-Spin | 80 / 180 |
| Yaw-Spin kopfüber | 250, +150 je weitere Drehung (höchstens +300) |
| Trippy Spin | 350, +150 je weitere Drehung (höchstens +300) |
| McTwist | 500 |
| Kopfüber hängen | 120 + 80 je Sekunde über 0,9 s (bis 3 s) |
| Knife Edge | 150 + 100 je Sekunde über 0,5 s (höchstens +250) |
| Sturzflug | 150 + 5 je Meter über 10 m (höchstens +250) |
| Orbit | 250 |
| Gap | 200 × 2,2 m / Breite (1–1,6) |
| Proximity | (100 + 150 je Sekunde über 0,35 s, höchstens +300) × 0,9 m / kleinster Abstand (1–1,5) |
| Eject | 60 |
| Sbang-Kombo | 300 (Bonus, zählt nicht als Kombo-Trick) |
| Funky Split | 250 |
| Shinobi Flip | 600 (Bonus auf Split-S/Funky Split, Rubik's Cube und Flip) |
| Guillotine | 450 + 10 je Meter Fallhöhe über 8 m (höchstens +300) |
| Side Trippy | 400, +150 je weitere Runde (höchstens +300) |
| Rückenflug (3D) | 150 + 100 je Sekunde über 1 s (höchstens +300) |
| Tic-Toc (3D) | 400 + 100 je halbem Flip über 4 (höchstens +200; eine Gruppe hat höchstens 6 Striche) |
| Anti Matty / Half Matty / Power Matty | 375 / 400 / 500 |
| 540-Split-S / Splitback | 300 / 350 |
| Flip-Rewind / Segmentierter Flip | 300 / 150 |
| Vanny Roll | 200 |
| Coin Drop, Tornado | 200 bzw. 300, +100 je weitere Drehung (höchstens +200) |
| Rückwärts-Sturzflug | 200 + 5 je Meter über 10 m (höchstens +250) |
| Hammerhead / Maverick Loop / Side Loop | 250 / 200 / 300 |
| Power Flip / Power Roll / Inverted 360 Power Loop / Barani | 500 / 550 / 650 / 700 (Power Flip und Power Roll über Flip bzw. Rolle + Anti Matty, damit die Gruppierung den ganzen Loop nimmt) |
| Immelmatt | 200 (Bonus wie Shinobi) |
| Wall Ride / Reverse Knife Edge | 200 bzw. 300 + 100 je Sekunde über 0,5 s (höchstens +250) |
| Schweben kopfüber (3D) | 200 + 100 je Sekunde über 1 s (höchstens +300) |
| Power Loop (3D) / 3D-Flip / 3D-Rolle | 450 / 200 (+100 je weitere Umdrehung) |

- **Bestätigung:** Ein erkannter Trick zählt erst 0,45 s nach seinem Ende. Berührung in der Zeit verwirft ihn, Absturz oder
  Disarm ebenfalls.
- **Stil:** kleinster Abstand zu Blöcken während des Tricks ≤ 1 m → +50 %, ≤ 2 m → +25 % (nicht für Gap/Proximity);
  sauber abgefangen (bei der Bestätigung Roll-/Nickrate < 300 °/s und `up.y ≥ 0,3`) → +20 % (Dreh-Tricks und Sturzflug).
- **Kette:** Beginnt ein Trick höchstens 0,5 s nach dem Ende des vorigen (oder überlappt er ihn), ist er verkettet: +25 %.
  `chain()` zählt die Länge, `bestChain()` die längste des Flugs.
- **Wiederholung:** derselbe Trick in derselben Kombo ×0,6 je Wiederholung.
- **Kombo:** folgt der nächste Trick innerhalb von 3 s, wächst die Kombo. Endet sie (3 s ohne Trick), kommt der Bonus
  Summe × (Multiplikator − 1) dazu, Multiplikator = 1 + 0,5·(n − 1), höchstens ×4. Absturz oder Disarm beenden Kombo und
  Kette ohne Bonus (Meldung „KOMBO VERLOREN“ ab zwei Tricks), die Punkte der einzelnen Tricks bleiben.
- **Meldung:** Ein verketteter Trick (oder mehrere im selben Frame bestätigte) hängt sich an die noch sichtbare Meldung an
  (höchstens 2 s alt): bis zu drei Namen, Punkte als Summe, die Meldung beginnt neu zu laufen (`popupTypeCount()`,
  `popupType(i)`, `popupTricks()`, `popupChain()`).
- **Flug:** Punkte und beste Kombo (Anzahl, bei Gleichstand mehr Punkte) gelten je Flug (Scharfschalten bis Disarm) und
  stehen in der Flugstatistik. Alle Werte werden auf 5 gerundet.

## OSD

Element `OsdElement.TRICKS` (Spalte 15, Zeile 13, zentriert, verschiebbar; an in Minimal, Standard und Voll, aus in Race):
„JUICY FLICK + ROLLE +480  X2“ in der Elementfarbe (bis zu drei Namen einer Kette aus `OsdData#trickTypes`; die Namen
haben zusammen höchstens 28 Zeichen, ältere fallen dann mit „.. “ davor weg, damit die Zeile ins 30-Spalten-Raster passt), Kombo-Ende
„KOMBO X3 +540“ in Grün, „KOMBO VERLOREN“ in Bernstein (beides nur mit farbigen Warnungen). Sichtbar 2,5 s (verloren 1,8 s),
die letzten 0,5 s blendet es aus. Die Flugstatistik bekommt die Zeilen „TRICK-PUNKTE“ und „BESTE KOMBO“ (`X5 · 1320`), wenn die
Erkennung im Flug an war.

## Tab „Freestyle“ und Flugschule

Tab „Freestyle“ (`TrickConfigTab`, Reihenfolge 610, Spieleinstellung): Modus, Live-Stand im Flug, darunter „Punkte je Trick“
mit der Zahl der sichtbaren Tricks („59 von 59 Tricks“), einem Suchfeld und dem Familienfilter (Zyklus-Knopf „Alle Familien“,
dann die neun Familien; nebeneinander ab zwei Spalten, sonst untereinander). Die Tricks stehen nach Familien gruppiert (Familienname
in Akzentfarbe, darunter die Tricks der Familie in bis zu drei Spalten, scrollbar). Die Suche (`trick.TrickCatalog`, reines Java)
vergleicht ohne Groß-/Kleinschreibung, Akzente und Satzzeichen den Namen in der eingestellten Sprache, den Schlüssel (englisch,
z. B. „inverted yaw spin“) und die Aliase (`.aka`); jedes Suchwort muss vorkommen, Familiennamen werden nicht durchsucht (sonst
fände „matty“ auch alle Split-S). Ohne Treffer steht „Kein Trick passt zu „…““. Der Tooltip zeigt Name, Familie, Erklärung und
die Aliase („Auch: Barrel Roll, Fassrolle“). Darunter die Regeln für Kombo und Kette. Suche und Filter sind Anzeige-Zustand,
keine Einstellung („Standardwerte“ setzt sie zurück).
Die Flugschul-Lektion „Freestyle-Tricks“ (`guide.scenes.TricksScene`) zeigt Flip und Rolle, Juicy Flick, Rewind und Rubik's
Cube, Power Loop über eine Mauer und Split-S, Spins kopfüber (Yaw-Spin kopfüber, Trippy Spin), die neue Schule (Guillotine
im Diorama, Hinweis auf Sbang-Kombo, Side Trippy, Rückenflug und Tic-Toc) und Kombos mit Kette. Die Lektion hat genau 60 s;
für den Katalog nennen die Texte Familien und Varianten (Beschreibung „Über 50 Tricks …“, Loop-Schritt mit Power Flip & Co.,
Hinweis auf den Tab mit Familien und Suche) statt jeden Trick vorzuführen.

## Tests

- `trick.TrickRepertoireTest`: Referenz-Flugbahnen aus geskripteten Sim-Eingaben (echte `DroneSim`, Pilot `SimPilot` mit
  Raten-Befehlen, geschlossener Drehwinkel-Regelung `rotate`, Lageregler) für jeden Trick und seine Varianten: sauber,
  unsauber (Knüppelrauschen ±140 °/s), langsam (320 °/s), schnell (hohe Rates, 1050 °/s), mit Gasstoß und Gaswechseln,
  Achsen-Mischung (Roll im Flip, Gier-Kopplung), weiche Knüppel (Verzögerung 60–70 ms), schwankende Bildrate (25–200 Hz),
  60/240 Hz, aus schnellem Flug mit 65° Neigung, zu kurz gedreht, in den Sturz gedreht; Whoop, Racer, Cinewhoop. Ketten
  (Flip direkt in Rolle, Juicy Flick in Flip, Split-S und Power Loop, Rolle-Flip-Rolle) zählen jeden Trick genau einmal.
  Nicht-Treffer: Schnellflug mit 80° Neigung und Korrekturen, Kurven bis 80° Schräglage, schnelle Kurvenwechsel, Rückblick
  ohne Rückenlage, halbe Rolle 120° hin und zurück, Wackeln mit 90°, Bremsen mit 100° Nick, Steigen-Fallen-Abfangen,
  Gierkurven, Cruisen mit Knüppelrauschen und sechs zufällige aggressive Flüge ohne Tricks.
- `trick.TrickRobustnessTest`: dieselben Flugbahnen mit 40 weiteren Zufalls-Seeds je Familie (unsaubere Flips, Rollen,
  Juicy Flicks, Rubik's Cubes, Split-S, Power Loops, Flip und Rolle bei 30–200 Hz, Whoop-Flip, Gierdrehung kopfüber,
  Power Loop bei 30–90 Hz, Juicy Flick direkt in Flip) und je 40 aggressive Flüge und Cruisen ohne Tricks, alles ohne eine
  Abweichung; dazu die drei früheren Fehlerkennungen (Rubik's Cube mit kurzem Aufrichten als Rolle, aggressiver Flug als
  Juicy Flick, mehrfacher Power Loop als Matty Flip) als feste Fälle. Gegenprobe über 1963 weitere Fälle (Seeds 1000–3099):
  vorher 6, jetzt 4 Abweichungen, alle vier in der Familie „Rennlinie Racer“, in der sich die Drohne bei 85° Schräglage
  tatsächlich mehrfach überschlägt (bis 1015° Roll); dort ist die Erkennung als Rolle oder Rubik's Cube die echte Bewegung.
- `trick.TrickSimTest`: die bisherigen Sim-Fälle (Flips, Rollen, Power Loop, Split-S, Matty Flip, Sturzflug, Yaw-Spins,
  Hänger, Gap, Proximity, Rennlinien, zufälliges Cruisen); eine halbe Rolle hin und zurück ist seit 0.5.0 ein Rewind.
- `trick.TrickKinematicTest`: vorgegebene Bahnen für Orbit, Absturz-Taumeln, Disarm, Rescue, Teleport, Berührung, Tunnel,
  Bildraten 20–240 Hz.
- `trick.TrickProTricksTest`: Referenz-Flugbahnen der neuen Schule – Eject (schnell, langsam, seitlich, unsauber, weiche
  Knüppel, 30–144 Hz, aus dem Schweben, Whoop) und Gegenproben (Kippen ohne Gasstoß, Gasstoß ohne Zurückschnappen,
  senkrechter Punch-out, Beschleunigen wie im Rennen, langsam ankippen), Sbang-Kombos (Eject · Split-S · Juicy Flick,
  Eject · Rewind · Yaw-Spin kopfüber, unsauber) und ohne Eject oder mit Pause keine, Funky Split (links, rechts, unsauber,
  weiche Knüppel; nur 90° gieren zählt nicht), Shinobi Flip (mit Split-S, mit Funky Split, unsauber; ohne Einstieg oder mit
  Pause keiner), Guillotine (Grundform, aus großer Höhe, unsauber, weiche Knüppel, 30–144 Hz, über die Senkrechte gedrückt)
  gegen Sturzflug mit normalem Abfangen, Sturzflug dann Flip, großen Power Loop und Flip im flachen Fallen, Side Trippy
  (90°, 75°, links, unsauber, zwei Runden) gegen langsames Kreisen auf der Seite und halbe Runden, Rückenflug und Tic-Toc nur
  im 3D-Modus (auch lang, mit Fahrt, gerollt, unsauber) gegen Kopfüber ohne Umkehrschub und halbe Flips ohne 3D, Cruisen und
  schnelles Kurven im 3D-Modus ohne Tricks.
- `trick.TrickRobustnessTest`: zusätzlich je 40 Zufalls-Seeds für Eject, Sbang-Kombo, Funky Split, Shinobi Flip, Guillotine,
  Side Trippy, Rückenflug und Tic-Toc sowie 40 aggressive Flüge im 3D-Modus – ohne Abweichung. Gegenprobe vor dem Einbau
  (nicht im Test-Satz): 9650 Fälle, je 150 Seeds der elf alten Familien und je 1600 Seeds für aggressive Flüge, Cruisen und
  Rennlinien des Racers, normal und im 3D-Modus – 0 Abweichungen, der alte Stand ebenfalls 0 auf denselben 6450 alten Fällen.
- `trick.TrickVariantsTest` (Trick-Katalog): jede neue Variante sauber, unsauber (±100 °/s), mit weichen Knüppeln (60 ms),
  bei 30–144 Hz und teils als Whoop oder links herum (Skripte in `trick.TrickVariantScripts`; der Test-Pilot fliegt den
  Maverick Loop entlang einer Kreisbahn, schwebt im 3D-Modus kopfüber auf der Stelle und hält Zeiten mit `hold` unabhängig von
  der Bildrate); Gegenproben: Doppelflip ohne Gasstoß, halbe Rolle mit halbem Flip ohne Gas, Split-S, anderthalb und eine halbe
  Rolle, halber Flip mit kurzer Pause, lange Pause kopfüber (Rolle und Hänger), Power Loop, McTwist, Immelmann mit Pause, kleiner
  Hüpfer, Yaw-Spins mit Gas, flach fallen lassen, Sturzflug, Hochziehen und vorwärts abkippen, Hochziehen und 90° gieren, Knife
  Edge 2 m neben der Wand, Proximity ohne Seitenlage, Flip und Rolle im 3D-Modus ohne Umkehrschub.
- `trick.TrickRobustnessTest.catalogueTricksWithRandomSeeds`: je 40 Zufalls-Seeds für 25 Varianten (Wall Ride darf zur Knife
  Edge werden, wenn die Drohne sich von der Wand entfernt) – ohne Abweichung. Reverse Knife Edge ist nicht im Seed-Test: bei
  ±30 °/s Rauschen 39 von 40, beim Ausreißer überschreitet die Schräglage die bestehende Grenze der Knife Edge (±72°).
- `trick.TrickCatalogTest`: mehr als 50 Tricks in neun Familien, Repertoire nach Familien geordnet, Suche (Barrel, Fassrolle,
  Gigatine, Stall Turn, Reverse Split, „rückwärts“ und „ruckwarts“, Schlüssel, „splits“, „matty“ genau die vier Matty-Tricks),
  Filter und Suche kombiniert, Normalisierung, OSD-Namen ≤ 18 Zeichen in Großbuchstaben, Familien und Aliase in beiden Sprachen,
  Varianten zeigen auf ihren Grundtrick.
- Gegenprobe Trick-Katalog (nicht im Test-Satz): 11 250 Fälle – je 1600 Seeds aggressive Flüge, Cruisen und Rennlinien des
  Racers, normal und im 3D-Modus, und je 150 Seeds der elf alten Familien. 4 Abweichungen (zwei aggressive Flüge, je normal und
  im 3D-Modus, als Split-S bzw. Rolle) – genau dieselben erkennt der Stand vor dem Katalog (integration), also keine neue
  Fehlerkennung. Ein früherer Entwurf des Hammerheads hatte in aggressiven Flügen angeschlagen; seitdem verlangt er den
  Strömungsabriss und eine schnelle, überwiegend gierende Drehung.
- `trick.TrickScoreTest` (Ketten-Bonus, gemeinsame Meldung, Absturz beendet Kette, Bonus der Kombi-Tricks ohne Kombo- und
  Kettenzähler, Verlust bei Berührung), `trick.TrickTypeTest`, `osd.OsdTricksTest`, `guide.FlightSchoolScenesTest` (Lektion
  „tricks“ mit sieben Schritten).
- Selbsttest „tricks: …“: Anmeldung, Tab, Flip in der Simulation mit OSD-Meldung und Flugstatistik, Kette aus Flip und Rolle
  mit gemeinsamer OSD-Meldung, Repertoire-Tab (drei Spalten, neun Familien, Suche „matty“, Suche ohne Treffer, Filter 3D,
  Erklärungen, Scrollen im schmalen Tab), Zustand nach dem
  Verlassen; „tricks: Neue Schule in der Simulation“: dieselbe Sim fliegt Guillotine, Eject, Split-S und Juicy Flick und
  erwartet genau `[GUILLOTINE, EJECT, SPLIT_S, JUICY_FLICK, SBANG]` samt OSD-Meldung mit der Sbang-Kombo. Bildschirmfotos
  `trick_popup_fpv.png`, `trick_chain_fpv.png` (Juicy Flick und Rolle im FPV-Flug), `trick_guillotine_fpv.png` und
  `trick_sbang_fpv.png` (echter FPV-Flug mit demselben Skript, `trick.client.ProTrickShowcase`), `configurator_tricks.png`,
  `configurator_tricks_search.png` (Suche „loop“, Schritt „bildschirmfotos: Tab Freestyle mit Familien und Suche“),
  `flight_school_tricks.png`, `flight_school_tricks_new_school.png`.

## Entscheidungen zur neuen Schule

| Entscheidung | Warum | Verworfen |
|---|---|---|
| „deck style“ als Dex-Style gedeutet; dazu Guillotine, Funky Split, Shinobi Flip und Side Trippy | so heißt der Stil in den Videos und Hashtags; das Wort klingt gesprochen wie „deck“ | eine eigene Kategorie „Deck“ erfinden |
| „Gigatine“ heißt in Propwash **Guillotine** (beide Sprachen) | das Tutorial des Tricks heißt „Guillotine“, „gigatines“ stammt aus der automatischen Untertitelung | den Namen „Gigatine“ übernehmen |
| Sbang = Grundbewegung Eject als Trick + Sbang-Kombo als Bonus für eine Linie | Sbang ist ein Stil aus Bausteinen; ein Eject ist der typische Baustein, eine Linie ohne Pause das Erkennungszeichen | ein einzelner „Sbang“-Trick für jede schnelle Folge; eine Stil-Wertung über den ganzen Flug |
| Kombi-Tricks (Sbang-Kombo, Shinobi Flip) sind Bonus-Namen, Kombo- und Kettenzähler wachsen nicht | die Bausteine bleiben sichtbar und gewertet, der Multiplikator wird nicht künstlich größer | Bausteine ersetzen (Namen und Punkte gingen verloren); als zusätzlichen Kombo-Trick zählen |
| Shinobi akzeptiert Split-S statt Funky Split und einen Flip statt des Matty | in den Referenzflügen verschmilzt der Matty nach dem Rubik's Cube oft mit dessen letztem halben Flip oder kehrt die Richtung nicht ganz um | nur Funky Split und Matty Flip |
| Guillotine über einen Verlauf der letzten Sekunden und einen ruhigen Sturz | funktioniert auch, wenn der Pilot über die Senkrechte drückt und das Fenster offen bleibt; ein Power Loop dreht im Sturz weiter und zählt nie | den Sturzzustand beim Öffnen des Fensters prüfen; jeden Flip nach jedem Sturz zählen |
| Side Trippy nur als schneller Nick-Strich (≥ 200 °/s) mit ≤ 6 m Radius | eine Kurve mit 70° Schräglage in der Rennlinie hatte sonst als Side Trippy gegolten | jede ganze Runde auf der Seite |
| Rubik's Cube zählt keine Kurve auf der Seite mehr | eine enge Kurve mit Ein- und Ausrollen war bisher ein Rubik's Cube | so lassen |
| 3D-Tricks nur mit `Telemetry.mode3d` (aktiver 3D-Modus) | entspricht „nur im 3D-Modus“, auch bei verbotener Welt-Regel oder Rettung ist 3D aus | 3D am negativen Gas erkennen |
| Rückenflug ersetzt im 3D-Modus mit Umkehrschub das Kopfüber-Hängen | dieselbe Zeit über Kopf soll nicht doppelt zählen | beide werten |
| Tic-Toc nur ohne Absetzen in einem Fenster (≥ 4 halbe Flips) | wer aufrecht kurz pausiert, fliegt zwei Rewinds – so zählt es auch sonst | Rewinds über Fenster hinweg zusammensetzen |
| Punkte: Eject 60, Funky Split 250, Side Trippy 400, Guillotine ab 450, Tic-Toc ab 400, Rückenflug ab 150, Shinobi-Bonus 600, Sbang-Bonus 300 | ein Eject ist Fortbewegung, eine Guillotine riskant wie ein Rubik's Cube; die Boni lohnen die Linie, ohne einzelne Tricks zu entwerten | gleiche Punkte für alle neuen Tricks |
| Eject bis 110° Neigung, Gas ≥ 0,75 für ≥ 0,1 s, Höhe ±6 m | weiche Knüppel und langsame Rates überschwingen bzw. steigen mehr; eine Rolle oder ein halber Flip geht trotzdem über 110° | ≤ 72° Neigung, Gas ≥ 0,8 für ≥ 0,12 s, ±3 m |
| Flugschule: ein kurzer Schritt „Neue Schule“ (5,5 s) in der Lektion „Freestyle-Tricks“ | die Lektion darf höchstens 60 s dauern und hatte noch 5,5 s frei | eigene Lektion (neue Szene in der festen Reihenfolge, API-Konstante, Bildschirmfotos anderer Zweige) |
| Keine Aufgaben im Flugtraining für die neuen Tricks in 0.5.0 | Lehrplan, Sterne und Fortschritt gehören einem anderen Zweig | Kapitel-Aufgaben ergänzen |
| Kein Netzwerk- und API-Wechsel | die Erkennung läuft nur im Client und liest die vorhandene Telemetrie | `API_VERSION` oder Protokoll erhöhen |
| Kein Bildschirmfoto im 3D-Modus | 3D braucht Freischaltung und eine Funke mit Schalter (im Rundgang des 3D-Modus); die 3D-Tricks prüfen die JUnit-Referenzflüge | eigene 3D-Szene im Rundgang |

## Entscheidungen zum Trick-Katalog

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Neun Familien im Code (`TrickType.family()`, `TrickFamily`), Repertoire nach Familien geordnet | Tab, Doku-Tabelle und Website lesen dieselbe Ordnung; angelehnt an die Kategorien von Pro Whooper | Familien nur in der Doku |
| Varianten mit eigenem Szene-Namen sind eigene Tricks (Anti Matty, Half Matty, Power Flip …) | die Quellen geben ihnen eigene Namen und Punkte, das OSD zeigt den Namen | nur ein Satz in der Erklärung des Grundtricks |
| `TrickType.variantOf()` und `withVariants` fürs Flugtraining | die Lektionen Flip, Rolle, Power Loop, Split-S und die Abschlussprüfung zählen weiter, wenn eine Variante erkannt wird (z. B. 3D-Flip im 3D-Modus); das HUD nennt den Grundtrick | jede Lektion einzeln pflegen |
| Matty Flip = vorwärts, Anti Matty = rückwärts | Rotor Riot und Pro Whooper beschreiben den Matty vorwärts, Pro Whooper nennt den Backflip-Matty „Anti Matty“ | beide Richtungen als Matty Flip (bisher) |
| Gleiche Bewegung, anderer Name = Alias (Barrel Roll, Building Dive, Snapback, Inverted Rewind, Inverted Orbit, Trippy Fall, Rolling Dive, Reverse Power Loop, Reverse Split-S …) | kein Trick soll doppelt zählen; Spieler finden ihn trotzdem über die Suche | eigene Tricks mit derselben Erkennung |
| Suche über Name, Schlüssel und Aliase, ohne Familiennamen | Spieler suchen nach dem Namen aus Videos; „matty“ soll nicht alle Split-S zeigen | Familiennamen und Erklärungen mitdurchsuchen |
| Hammerhead als eigener Automat mit Strömungsabriss (≤ 4 m/s beim Kreuzen der Waagerechten), Gieren innerhalb 1 s und gleich bleibender Oberseite | ohne Abriss schlug er in zufälligen aggressiven Flügen an; der Abriss ist das Kennzeichen des Turns | 180° Gieren oben genügt |
| Maverick Loop über die Bahn statt über Drehstriche | die Drohne dreht dabei kaum, das Manöver-Fenster öffnet nicht | Maverick Loop weglassen |
| Segmentierter Flip nur 0,4–0,9 s Pause ohne Gasstoß und nur statt eines einfachen Flips oder einer Rolle | ab 0,9 s ist es ein Hänger, mit Gasstoß ein Matty (sonst stahl er den Anti Matty im Shinobi) | jede Pause kopfüber |
| Wall Ride ersetzt die Knife Edge; Proximity während der Seitenlage erst ab 1,5 s | dasselbe Element soll nicht doppelt zählen (wie bei Gap) | Knife Edge, Proximity und Wall Ride zusammen |
| Tornado zählt neben dem Sturzflug, der Rubik's Cube zählt keine Rolle im Sturz | Rolle und Sturz sind zwei Elemente (wie ein Flip im Sturz); sonst wurde aus Einleiten · Tornado · Abfangen ein Rubik's Cube | Tornado ersetzt den Sturzflug |
| Hammerhead ersetzt den Sturzflug | der Sturz gehört zum Turn | beide zählen |
| Punkte grob nach Pro Whooper, Power Flip 500 und Power Roll 550 | die Gruppierung nimmt die Aufteilung mit den meisten Punkten; Flip + Anti Matty (475) darf den Power Flip nicht schlagen | Pro-Whooper-Punkte 1:1 |
| 3D-Varianten nur im 3D-Modus mit Umkehrschub (kopfüber ≥ 50 %, Loop ≥ 40 %), Schweben kopfüber ≤ 1,5 m/s | entspricht den bisherigen 3D-Tricks; Schweben heißt auf der Stelle | 3D-Flip für jeden Flip im 3D-Modus |
| Neue Tricks am Ende des Enums | Ordinalzahlen bleiben gleich (OSD-Zwischenspeicher, Ereignisse des Flugtrainings) | alphabetisch einsortieren |
| Keine neuen Schritte in der Flugschul-Lektion, nur Texte | die Lektion ist mit 60 s voll | eigene Lektion „Trick-Katalog“ (neue Szene, API-Konstante, Bildschirmfotos) |
| Kein Netzwerk- und API-Wechsel | die Erkennung läuft nur im Client, neue Namen kommen aus den Sprachdateien | `API_VERSION` oder Protokoll erhöhen |
| Kontakt-Tricks (Wall Tap, Slides) und objektbezogene Tricks (Cradle, Jump Rope, Pole Dance) nicht in 0.5.0 | Berührung macht Tricks ungültig; ohne das Objekt sind sie normales Bremsen oder Kurven | Ausnahmen für Berührungen |
