# Ranken, Fäden, Spinnennetze und Glocken (Propwash 0.5.2)

Viele Maps und Höhlen sind voller Spinnweben, Stolperdrähte, Ranken und Glocken. Bis 0.5.1 flog eine Drohne durch
alles ohne Kollisionsform hindurch, als wäre es Luft, und an einer Glocke prallte sie ab, ohne dass etwas geschah. Seit
0.5.2 spürt die Drohne diese Blöcke wie echte Hindernisse aus Fasern und Blättern, und Glocken läuten.

## 1. Was die Drohne spürt

| Werkstoff | Blöcke (Tag) | Wirkung |
|---|---|---|
| Netz (`WEB`) | `propwash:drone_snag_web`: Spinnweben | fängt die Drohne: starke Bremse, hält sie fest, Props laufen schwer |
| Faden (`STRING`) | `propwash:drone_snag_string`: Stolperdraht (Faden) | kurzer, kräftiger Ruck; der Faden wickelt sich um die Motoren |
| Laub (`FOLIAGE`) | `propwash:drone_snag_foliage`: Ranken, Leuchtflechte, Trauer- und Wirrranken (Spitze und Stamm), Höhlenranken (beide), hängende Wurzeln, hängendes Blassmoos | leichte Bremse, die Props reißen Blätter ab |

Die Tags sind Daten: Datenpakete und Add-ons können eigene Blöcke in einen der drei Tags legen.

**Nicht dabei** (Entscheidungen):
- *Gerüst*: Bambusstangen sind ein festes Gestell, kein Fasergewirr. Als Bremse wäre es falsch, als Kollision würde es
  das Vanilla-Klettern im Gerüst ändern. Es bleibt, wie es ist.
- *Laub*: hat in Minecraft eine Kollisionsform, die Drohne prallt schon heute daran ab.
- *Süßbeerenbusch*, *Sculk-Ader*, *Sporenblüte*: kein hängendes Fasergewirr, das Props fassen könnten.

Nichts davon wird verändert oder zerstört – auch nicht mit der Welt-Regel „Drohnen zerbrechen Blöcke“. Die Regel bleibt
beim Glas mit ihrer Energieschwelle und der Freigabe vorab; Spinnweben, Fäden und Ranken sind in Maps Dekoration oder
Mechanik (Fallen, Rätsel), die eine Drohne nicht abräumen soll. Stolperdrähte lösen nicht aus (wie Druckplatten:
Drohnen sind keine Auslöser).

## 2. Flugmodell

Die Wirkung steht im Flugmodell als Umgebungsgröße `SimEnvironment.snag` (`sim.SnagField`, reines Java). Sie wirkt an
fünf Punkten: den vier Props und der Rahmenmitte. Je Punkt gibt es einen linearen Anteil `linear` (1/s) und einen
quadratischen `quadratic` (1/m); dazu für die ganze Drohne eine Haltekraft `hold` (m/s²) und je Prop eine Zusatzlast
`propLoad`.

- **Widerstand an den Punkten** (`sim.SnagDrag`): Jeder Punkt bremst mit `a = −(linear + quadratic·|v_p|)·v_p`, wobei
  `v_p` die Geschwindigkeit des Punkts ist (Schwerpunkt plus Drehung × Hebel). Die Kraft greift am Punkt an, das
  Drehmoment `r × F` entsteht von selbst: Fasst nur ein Prop eine Ranke, giert und kippt die Drohne zu dieser Seite;
  steckt sie ganz im Netz, dämpft das Netz auch das Drehen.
- **Halten** (Coulomb-Reibung am Schwerpunkt): Bewegt sich die Drohne, bremst `hold` zusätzlich entgegen der Bewegung,
  nie bis zur Umkehr. Steht sie (unter 0,05 m/s) und ist die freie Beschleunigung aus Schub und Schwerkraft kleiner als
  `hold`, bleibt sie stehen; sonst zieht sie mit dem Überschuss los. So hängt eine Drohne im Netz und sinkt mit
  Leerlaufgas nur langsam, und mit Vollgas kämpft sie sich frei.
- **Props** (`PowerSystem.snagLoad`): Fäden um die Welle und Blätter in der Kreisfläche erhöhen das Luft-Drehmoment des
  Props um den Faktor `1 + propLoad`. Die Drehzahl sinkt, der Strom steigt, der Schub fällt – und der Motorklang wird
  tiefer, ohne dass der Klang davon wissen muss.
- **Stabilität**: Bei 2 kHz rechnet der Widerstand explizit. Damit er nie überschwingt, kappt `SnagDrag` die Bremse so,
  dass ein Rechenschritt höchstens die Hälfte der Geschwindigkeit (`MAX_STEP` = 0,5) und des Drehimpulses nimmt.
- **Bitgleich ohne Netz**: Ist das Feld leer, rechnet das Flugmodell keinen einzigen Schritt anders
  (`SnagFlightTest#noSnagStaysBitIdentical`, Bewegungs-Goldens und Kalibrierung unverändert).

Werkstoffe (`interact.SnagMaterial`, Werte bei voller Berührung aller fünf Punkte):

| | linear (1/s) | quadratisch (1/m) | Halten (m/s²) | Prop-Last | Verschleiß (je s, Vollgas) |
|---|---|---|---|---|---|
| Netz | 12 | 4 | 4 | +100 % | 2 % |
| Faden | 3 | 1,2 | 0 | +200 % | 3 % |
| Laub | 1 | 0,25 | 0 | +30 % | 0,5 % |

Was das im Flug heißt (JUnit `SnagFlightTest`, 5-Zoll und 65-mm-Whoop):
- Ein 5-Zoll mit 20 m/s steckt nach 0,44 m im Netz fest, mit 40 m/s nach 0,84 m – kein Absturz, kein Aufprall.
- Mit Vollgas steigt er in 0,28 s aus einem 1 m hohen Netz, ein Whoop in 0,75 s.
- Mit Leerlaufgas sinkt er höchstens 0,4 m/s (frei fallend wären es nach einer Sekunde fast 10 m/s).
- Eine dünne Rankenwand (1/16 Block) kostet bei 15 m/s 7–10 % Tempo, je nach Bildrate (30, 60, 144 fps); im Spiel
  kostet eine 0,5 m dicke Wirrranke bei 11 m/s rund 23 %.
- Ein gespannter Faden auf Prop-Höhe kostet bei 10 m/s etwa 30 %, eine gleich dünne Ranke 8 %.
- Im Netz drehen die Props bei gleichem Gas rund 12 % langsamer.

## 3. Abtastung der Welt

`interact.SnagSampler` (reines Java) rechnet vor jedem Rechenabschnitt der Pilotensitzung (höchstens 0,5 m Flugweg je
Abschnitt) die fünf Punkte aus Lage und Position des Flugmodells. Jeder Prop ist eine Scheibe mit dem Prop-Radius:
ihre Box hat je Achse die halbe Ausdehnung `r·√(1 − n²)` (n = Hochachse der Drohne in Weltkoordinaten) plus 1 cm; die
Rahmenmitte ist ein Würfel mit 35 % des Motorabstands (mindestens 3 cm).

Die Welt liefert Boxen (`SnagSampler.World`); in Minecraft macht das `interact.SnagBlocks` aus den Umrissformen der
Blöcke in den drei Tags, nur in geladenen Chunks. Der Stolperdraht ist ein Sonderfall: Seine Umrissform ist die ganze
Zellenfläche, der Faden selbst aber eine Linie; `SnagBlocks` gibt deshalb eine 1/16 Block breite Linie entlang der
verbundenen Richtung(en) in den unteren 1,5 Pixeln der Form zurück (dort liegt der Faden, gespannt 1 Pixel über dem
Boden der Zelle, lose direkt am Boden).

Je Punkt und Werkstoff zählt, wie viel des Wegs seit dem letzten Abschnitt in der (um die Punktbox vergrößerten) Form
lag – als Vereinigung der Abschnitte, damit sich berührende Boxen nicht doppelt zählen – oder 1, wenn der Punkt gerade
darin steckt. So spürt die Drohne auch eine 1/16 Block dünne Ranke, die sie zwischen zwei Bildern durchflogen hat, und
die Wirkung hängt kaum von der Bildrate ab. Sprünge über 4 m (Teleport, Reset) zählen nicht als Durchflug.

## 4. Verschleiß, Hinweis, Effekte

- **Props nutzen sich ab**, solange sie sich in Fasern drehen: je Prop `Berührung × Werkstoff-Rate × Drehzahl-Anteil ×
  Zeit` (`interact.SnagWear`). Der Client schickt das höchstens einmal je Sekunde als `propwash:drone_snag_wear`; der
  Server nimmt es nur, wenn die Drohne vom Spieler geflogen wird, die Epoche passt, ein Block aus den Tags höchstens
  2 Blöcke von der Drohne entfernt ist und die Welt-Regel „Crash-Schaden“ an ist. Je Prop gilt höchstens 3 % je Sekunde
  seit dem letzten Paket × 1,5 (höchstens 10 % je Paket), mal der Stärke aus „Crash-Schaden-Stärke“.
- **Hinweis**: Hängt die Drohne 0,4 s fest im Netz (Haltekraft ab 1 m/s², unter 1 m/s), erscheint einmal der Toast
  „Im Spinnennetz verfangen – Gib Gas, um dich freizukämpfen …“; erst nach 2 s Freiheit wieder.
- **Effekte für alle** (`interact.client.SnagEffects`, an jeder scharfen Drohne mit drehenden Props bis 48 Blöcke um die
  Kamera): Berührt die Box der Drohne (+8 cm) einen Block aus den Tags, fliegen jeden Tick 1–4 Bröckchen dieses Blocks
  (Blätter, Fäden) davon, alle 6 Ticks klingt das Treffergeräusch des Blocks. Das braucht kein Netzpaket: Jeder
  Client sieht dieselben Blöcke und die synchronisierte Motorleistung.

## 5. Server und Mehrspieler

- Der Pilot rechnet sein Flugmodell selbst; die Blöcke sind bei allen Clients dieselben, der Server prüft wie bisher nur
  die Plausibilität (langsamer ist immer plausibel).
- **GPS Rescue ohne Pilot** (`rescue.ServerReturnFlight`) tastet mit `DroneSnags.Flight` genauso ab, und die Props
  nutzen sich dort direkt am Server ab (einmal je Sekunde).
- Lose, nicht geflogene Drohnen verhalten sich unverändert.

## 6. Glocken

Fliegt eine Drohne gegen eine Glocke, läutet sie wie bei einem Pfeil:
- Der Client erkennt nach jeder Bewegung, ob die Drohne entlang einer Achse an der Kollisionsform einer Glocke
  (`BellBlock`) gestoppt wurde (`interact.client.PilotBells`), mit mindestens 1 m/s auf die Glocke zu. Er schickt
  `propwash:drone_bell` mit Position, getroffener Seite, Trefferpunkt und Tempo, je Glocke höchstens alle 5 Ticks.
- Der Server (`interact.DroneBells`) prüft Pilot, Epoche, dass dort eine Glocke steht, Spawn-Schutz
  (`Level#mayInteract`), Abklingzeit (5 Ticks je Glocke), höchstens 8 je Sekunde und Drohne und die Reichweite wie bei
  Treffern (1,5 Blöcke + 0,15 je m/s des Tempos, das der Server aus den letzten 20 Ticks glaubt). Dann ruft er
  `BellBlock#onHit(…, Pilot, true)` – genau den Weg eines Geschosses: nur seitliche Treffer am Glockenkörper läuten (wie
  bei Pfeilen), Schwingen, Klang, Spiel-Ereignis, die Statistik „Glocke geläutet“ des Piloten (wie beim Besitzer eines
  Pfeils) und nach dem Nachklang das Leuchten von Räubern im Umkreis macht Vanilla.
- Langsames Anlehnen und Landen neben der Glocke läuten nicht.
- **Entscheidung Spawn-Schutz**: Pfeile läuten Glocken auch im Spawn-Schutz. Bei Drohnen prüft der Server trotzdem
  `mayInteract`, weil eine Drohne ohne Munition beliebig oft läuten könnte; Abenteuermodus bleibt erlaubt (Vanilla läutet
  dort auch per Hand).
- GPS Rescue ohne Pilot läutet nicht (der Heimflug weicht Hindernissen aus).

## 7. Netz

`Constants.PROTOCOL_VERSION` 16: `propwash:drone_snag_wear` (C2S: Drohne, Epoche, vier Prop-Werte 0–1) und
`propwash:drone_bell` (C2S: Drohne, Epoche, Block, Seite, Trefferpunkt, Tempo). Die Parts-API ändert sich nicht.

## 8. Tests

- JUnit: `sim.SnagFlightTest` (bitgleich ohne Netz, Fangen bei 20 und 40 m/s, Freikommen mit Vollgas für 5-Zoll und
  Whoop, langsames Sinken, Rankenvorhang bei drei Bildraten, Faden gegen Ranke, Drehzahl im Netz) und
  `interact.SnagRulesTest` (Abtastung, Vereinigung der Abschnitte, dünne Ebene zwischen zwei Bildern, Sprünge,
  Verschleiß auf dem richtigen Prop, Server-Grenze, Glocken-Regeln, Pakete).
- Selbsttest `interact.client.SnagSelfTests` (beide Loader, `-PpropwashSelftestOnly='verfangen:,glocke:'`): Tags bei
  Server und Client, 5-Zoll fliegt mit 18 m/s in eine Spinnwebenwand und kommt mit Vollgas frei (Hinweis, Verschleiß am
  Server, alle Spinnweben noch da), Wirrranken mit 12 m/s und Blätter an den Props, Glocke läutet (Statistik,
  Plünderer leuchtet), langsames Anstupsen läutet nicht. Die Wirrranken stehen dort, weil sie wie in der Welt am Boden
  wachsen; frei hängende Ranken ohne Halt würde der Zufallstick beim Ausbreiten wegräumen. Mit `-PpropwashScreenshots=true` die Bilder
  `snag_cobweb.png`, `snag_vines.png`, `drone_bell.png` und `drone_bell_raider.png`.

Ergebnisse im Spiel (Fabric und NeoForge, 14.10.2026): Der 5-Zoll steckt nach dem Einflug mit 18 m/s mit der Mitte
0,4 m tief im Netz (0,00 m/s, kein Absturz), kämpft sich mit Vollgas in 1,5 s durch 3,5 m Netz nach oben frei, die
Props sind danach zusammen rund 6 % abgenutzt, alle 20 Spinnweben stehen noch. Durch eine 0,5 m dicke Wirrranke
verliert er bei 11,4 m/s rund 23 % Tempo (Props 0,2 % abgenutzt). Die Glocke läutet nach dem Treffer mit 3 m/s, der
Plünderer 7 Blöcke weiter leuchtet nach dem Nachklang, langsames Anstupsen mit 0,6 m/s läutet nicht.

## 9. Offen

- Lose, nicht geflogene Drohnen fallen weiter ungebremst durch Spinnweben (Vanilla-artiges Steckenbleiben wäre ein
  kleiner Zusatz in `QuadcopterEntity#tickLoosePhysics`).
- Stolperdrähte lösen durch Drohnen nicht aus; dafür bräuchte der Server eine Wegprüfung zwischen den
  Positionspaketen und private Vanilla-Logik des Drahts.
- Flugschule: keine eigene Szene; „Absturz und Reichweite“ könnte einen Schritt mit Spinnweben bekommen.
