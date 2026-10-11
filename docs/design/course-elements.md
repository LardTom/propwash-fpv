# Kurs-Elemente: Wendeflagge, Slalomstange, Tauchwürfel (race, 0.5.0)

Anforderung (Besitzer, 05.10., aus dem Wunsch eines Creators nach Flaggen, Stangen, Slalom und Tauchwürfeln): neben
Gates gibt es drei Kurs-Elemente, die wie Gates zum Kurs gehören und gezählt werden:

1. **Wendeflagge** – wird umflogen, Richtung einstellbar (links oder rechts herum); zählt, wenn die Drohne sie richtig
   umrundet.
2. **Slalomstangen** – eine Reihe Stangen, abwechselnd links und rechts zu passieren; jede Stange zählt mit ihrer Seite.
3. **Tauchwürfel** (MultiGP-Würfel) – ein Rahmenwürfel, in den man von oben hineintaucht und durch eine Seite wieder
   hinausfliegt (Ausgangsseite einstellbar); zählt bei richtigem Ein- und Ausflug.

Alle drei gehören zu Kursen, Rundenzählung (Client `LapTracker`, Server `LapVerifier`), Ideallinie, Markierungen,
Bestenliste, Startampel, Kurs-Werkzeug, Speicherung und Weltdaten, Flugschule, Items mit Rezepten und Modellen im
Propwash-Stil. Grundlage ist das Gate-Modell aus `gate-mounting.md` und `gate-free-transform.md`: Elemente lassen sich
wie freie Gates gieren und in der Größe ändern, wo das Sinn ergibt.

## 1. Modell (`race.CourseElement`, `race.Passage`, `race.GateGeometry`)

- `CourseElement` (Enum, reines Java): `GATE`, `FLAG`, `POLE`, `CUBE` (dazu `FREEFORM`, `freeform-gate.md`, und die
  Gate-Stange `GATE_POLE`, die kein Kurs-Element ist, `gate-pole.md`). Jede Art kennt ihre Zahl der Varianten, die Zahl
  der **Stufen** (Durchflüge, die nacheinander gelingen müssen), die Rohrstärke und die Standardgröße.

  | Art | Varianten | Stufen | Stärke | Standard „Breite“ × Höhe | Bedeutung der Breite |
  |---|---|---|---|---|---|
  | `GATE` | – | 1 | – | wie bisher | Öffnung |
  | `POLE` | `LEFT` (0) links vorbei, `RIGHT` (1) rechts vorbei | 1 | 0,25 | 4 × 3 | wie weit neben der Stange ein Vorbeiflug noch zählt |
  | `FLAG` | `LEFT` (0) links herum, `RIGHT` (1) rechts herum | 3 | 0,25 | 5 × 4 | wie weit um die Flagge die Wende noch zählt |
  | `CUBE` | Ausgang `FRONT` (0), `RIGHT` (1), `BACK` (2), `LEFT` (3) | 2 | 0,25 | Öffnung 3 × 3 (außen 3,5) | lichte Öffnung, Würfel ist so tief wie breit |

  Größen laufen über dieselbe Öffnung 1–16 Blöcke in halben Schritten wie bei freien Gates (`GateSize#snapOpening`).
- Elemente sind **`GateEntity`** mit Art und Variante (ein synchronisiertes Byte `DATA_KIND`: Art in den unteren vier
  Bits, Variante darüber). So erben sie Kurszugehörigkeit, Nummer, Abbau, Besitz, Synchronisation, Weltdaten und das
  Einstellen ohne zweite Entity-Art. Ein Element steht immer aufrecht auf dem Boden (`GateMount.STANDING`,
  nie umgedreht, kein Nicken und Rollen, nur Gieren); Stapeln gibt es nicht.
- Die Seitenachse `side` der Geometrie zeigt zur **linken Hand des Piloten**, wenn er in Flugrichtung (`normal`) fliegt
  (JUnit `sideAxisIsThePilotsLeft`).
- **`Passage`** ist ein Rechteck im Raum (Ursprung, Achsen `s`/`u`, Normale `n`, Grenzen in `s` und `u`). Eine Stufe gilt,
  wenn ein Wegstück die Ebene in Richtung der Normale schneidet und der Schnittpunkt im Rechteck liegt – derselbe Test
  wie beim Gate, nur mit eigener Lage. `GateGeometry#stages()` liefert die Stufen, `fouls(stufe)` die Rechtecke, deren
  Durchflug in dieser Stufe ein Fehler ist.

### Stufen und Fehler je Art

| Art | Stufen (in dieser Reihenfolge) | Fehler (nur beim aktiven Element im Rennen) |
|---|---|---|
| Stange | Halbebene von der Stange nach der verlangten Seite, vorwärts durchflogen | Halbebene auf der anderen Seite vorwärts → „falsche Seite“ |
| Flagge | 1. vorwärts an der Flagge vorbei, die Flagge auf der Wendeseite (links herum: links); 2. hinter der Flagge quer in Wenderichtung; 3. auf der anderen Seite zurück | an der falschen Seite vorwärts vorbei → „falsche Richtung“ |
| Würfel | 1. Deckfläche (lichte Öffnung) abwärts; 2. Ausgangsseite nach außen | vor dem Tauchen jede Seite nach außen („von oben eintauchen“), danach jede andere Seite als der Ausgang |

- Halbebenen von Stange und Flagge reichen von der Achse `Breite` Blöcke nach außen und von 1 Block unter dem Fuß
  (`CourseElement.BELOW`) bis zur Spitze: Überfliegen zählt nicht, zu weit außen auch nicht.
- **Rücknahme:** Fliegt die Drohne die zuletzt geschaffte Stufe rückwärts wieder durch (z. B. auf halber Wende umkehren
  oder aus dem Würfel nach oben wieder heraus), fällt der Fortschritt eine Stufe zurück.
- **Fehler** setzen den Fortschritt des Elements auf 0 und melden `LapTracker.EventType.WRONG_SIDE`; das Element bleibt
  das nächste Ziel (wie bei einem verpassten Gate: zurück und richtig fliegen).
- Gates und Würfel zählen im Rennen auch, wenn sie nicht das nächste Ziel sind (dann „ausgelassen“, wie bisher bei
  Gates, `CourseElement#strictMiss`); Stangen und Flaggen nur als nächstes Ziel, weil ihre Halbebenen sonst beim
  normalen Flug durch den Kurs ständig getroffen würden.

## 2. Rundenzählung

- **Client (`LapTracker`):** je Element ein Stufenzähler; Durchflüge, Rücknahmen und Fehler eines Bildes werden wie die
  Gate-Durchflüge nach ihrem Zeitpunkt sortiert. Ist die letzte Stufe geschafft, gilt das Element als durchflogen
  (`GATE`-Ereignis mit Split, Piepton, HUD „Stange 3/7 · Split …“). Reine Gate-Kurse laufen bitgleich wie bisher.
- **Server (`RaceServer`, `LapVerifier`):** jede Stufe ist ein eigener **Prüfpunkt**. `CourseCheckpoints` zählt sie
  (`count`) und bildet (Element, Stufe) auf eine Nummer ab, das Start-Element liegt mit seiner letzten Stufe auf
  Prüfpunkt 0. Der Server sucht die Elemente eines Wegstücks im um `CourseElement.REACH` vergrößerten Bereich und
  meldet alle Stufen-Durchflüge; `LapVerifier` verlangt wie bisher alle Prüfpunkte in Reihenfolge. Kurse nur aus Gates
  behalten ihre Nummern (Prüfpunkt = Gate-Nummer). Fehler und Rücknahmen prüft der Server nicht – wer falsch fliegt,
  erzeugt die fehlenden Prüfpunkte schlicht nicht.
- **Geist und Splits (`GhostTrack#splits`):** gemessen beim Abschluss eines Elements, also nach der letzten Stufe.
- **Mindestrundenzeit:** weiter aus der Weglänge über die Element-Mitten (für Flaggen eher zu kurz, also nachsichtig).

## 3. Bedienung

- Items `propwash:race_flag` (16 je Stapel), `propwash:slalom_pole` (64) und `propwash:dive_cube` (16). Rechtsklick auf
  den **Boden** setzt das Element mit Vorschau (weiß frei, rot blockiert, „nur auf dem Boden“ an Wand und Decke).
- Taste „Gate drehen“ (`R`): dreht in der eingestellten Schrittweite (90/45/15/5°); **Schleichen + R** wechselt die
  Variante (Seite, Richtung, Ausgang) – vor dem Setzen in der Vorschau, danach am Element (Kurs-Werkzeug oder Element in
  der Hand). Die Variante steht in der Platzierungs-Vorliebe (`GatePlacement.Preference#variant`, mit dem Server
  geteilt).
- Schleichen + Rechtsklick mit Element oder Kurs-Werkzeug startet „Flagge/Slalomstange/Tauchwürfel einstellen“ als
  Einstell-Modus im Spiel (`race.client.GateEditor`, `docs/design/gate-edit-mode.md`): Zeilen Richtung,
  Seite/Umrunden/Ausgang, Breite und Höhe (bei Stange und Flagge „Wertung“ und „Höhe“, beim Würfel die Öffnung); Mausrad
  ändert, Schleichen + Mausrad wählt die Zeile, Schleichen + „Gate drehen“ schaltet Seite oder Ausgang weiter, Live-Vorschau,
  Enter oder Rechtsklick übernimmt, Esc verwirft; mit gehaltener Alt-Taste Zurücksetzen und „Aus dem Kurs“. Nicken und
  Rollen fehlen. Die Kopfzeile „In der Welt: Tauchwürfel · Ausgang vorne · 90°“ beschreibt den gesetzten Stand, Zeilen und
  Vorschau den Entwurf.
- **Kurs-Werkzeug:** Elemente hängen sich an wie Gates. Ein Element kann nicht das erste des Kurses sein („klick zuerst
  ein Gate an“), weil Start, Startaufstellung und Startampel ein Gate brauchen. Folgt eine Stange direkt auf eine
  Stange, nimmt sie die **andere Seite** als die vorige (Slalom), die Meldung sagt „Slalom: links vorbei“. Wer eine
  andere Folge will, ändert die Seite danach im Fenster oder mit Schleichen + R.
- Abbau wie bei Gates (Schleichen + Linksklick oder mit dem Kurs-Werkzeug), das Item fällt passend heraus.

## 4. Kollision und Platz

- Stange: ein Rohr 0,25 × Höhe × 0,25; Flagge: Rohr plus Tuch (1 Block breit, 55 % der Höhe, höchstens 2 Blöcke,
  1/16 dick) auf der Seite, um die gewendet wird; Würfel: zwölf Rohre 0,25 (vier Pfosten, je vier Balken unten und oben).
  Achsparallele Teile kollidieren als Boxen, gedrehte über die OBB-Kollision aus `gate-free-transform.md`.
- Platzprüfung (`GateItem#freeElement`): keine Blöcke, keine anderen Gates oder Elemente, beim Setzen keine Körper in den
  Teilen; die Wertungs-Halbebenen brauchen keinen freien Platz.

## 5. Darstellung (`race.client.CourseElementRenderer`)

- Teilmodelle `models/course/*` aus `tools/assets/world/course.py` (Maßstab der Gates, 8 Modellpixel = 1 Block), vom
  Renderer gekachelt: Stangenrohre (rot-weiß), Flaggenrohr (gelb-schwarz), Fuß, Leuchtkappe, Tuch links/rechts (gelb mit
  Wendepfeil), Würfel-Balken, -Pfosten, -Ecken und LED-Leisten. Leuchtkappe und LED-Leisten tragen die Gate-Lichtfarbe:
  grün, wenn das Element das nächste Ziel des eigenen Rennens ist, sonst weiß.
- Bodenmarkierungen im Code: Stange – Leuchtpfeile auf der verlangten Seite, rotes Kreuz auf der falschen; Flagge –
  Pfeilbogen um die Flagge in Wenderichtung, Pfeil am Einflug und Kreuz auf der falschen Seite; Würfel – zwei
  Tauchpfeile über der Deckfläche, leuchtender Rahmen an der Ausgangsseite und Pfeile aus ihr heraus. Im Kurs trägt
  jedes Element seine Nummer auf einer Tafel (über der Stange, auf der Hinterkante des Würfels).
- Ideallinie (`RaceLine`): jedes Element liefert **Wegpunkte** mit Richtung (`GateGeometry#waypoints`): Stange einer auf
  der verlangten Seite, Flagge drei auf dem Wendebogen, Würfel einer über der Deckfläche (abwärts) und einer am Ausgang
  (nach außen). Zwischen den Wegpunkten liegen Hermite-Stücke (`INTERNAL_TANGENT`), die Hälfte eines Abschnitts gehört
  dem Element. JUnit prüft, dass ein Flug entlang der Linie jedes Element zählt und die Linie um die Flagge ohne Knick
  (< 25° je Probe) läuft.
- Ohne Ressourcen (z. B. abgeschaltetes Paket) zeichnet der Renderer die Kollisionsboxen in Rahmenfarbe.

## 6. Speicherung, Netz, Kompatibilität

- Entity-NBT: `Kind` und `Variant` nur bei Elementen; Größe wie bei freien Gates (`OpeningWidth`/`OpeningHeight`, nur
  wenn vom Standard abweichend). Alte Gates ohne `Kind` laden als Gate.
- Weltdaten (`CourseRegistry.GateRecord`): `kind` und `variant` nur bei Elementen (sonst `null`), deshalb bleiben
  gespeicherte Kurse aus 0.4.0 Byte für Byte lesbar.
- `CourseData.Gate` trägt Art und Variante (Merkerbit 4, danach zwei Bytes), `layoutHash` nimmt sie nur bei Elementen
  auf: Prüfsummen und Bestenlisten reiner Gate-Kurse bleiben gültig. Ein Element in den Kurs nehmen, umstellen oder
  entfernen ändert das Layout (alte Bestenliste wird archiviert, wie bei Gates).
- **Netzprotokoll 11** (bleibt, 0.5.0 ist noch nicht veröffentlicht): neues synchronisiertes Byte der Gate-Entity,
  `race_gate_preference` und `race_gate_edit` mit einem Byte für die Variante, `race_courses` mit Art und Variante.
- Parts-API 1.5.0 (additiv): `FlightSchoolScenes.COURSE_ELEMENTS` (`propwash:course_elements`). Die Elemente selbst
  sind kein Teil der API.
- Portierbar: `CourseElement`, `Passage`, `CourseCheckpoints`, die Stufen- und Wegpunkt-Rechnung in `GateGeometry`,
  `LapTracker`, `RaceLine` und `GhostTrack` sind reines Java ohne Minecraft-Typen.

## 7. Flugschule und Texte

- Neue Szene „Flaggen, Slalom und Tauchwürfel“ (`guide.scenes.CourseElementsScene`, nach „Gates frei drehen“): Stangen
  setzen und als Slalom anhängen, Wendeflagge mit Richtung, Tauchwürfel mit Ausgang, Flug durch alle Elemente (die
  Szene prüft per JUnit, dass dieser Flug mit der echten Zählung jedes Element schafft), Einstellen im Fenster.
- Texte `race` und `guide` in `de_de` und `en_us`, Shift-Tooltips aller drei Items (`propwash.help.*`).

## 8. Rezepte

| Item | Anzahl | Muster | Zutaten |
|---|---|---|---|
| `race_flag` | 1 | `SW` / `SW` / `N·` | Stock, Wolle, Eisennugget |
| `slalom_pole` | 2 | `D` / `S` / `N` | roter Farbstoff, Stock, Eisennugget |
| `dive_cube` | 1 | `BGB` / `B·B` / `BBB` | Eisengitter, Glowstone-Staub |

Erzeugt mit `tools/data/survival.py` (Gruppe `propwash_course`, Freischaltung wie alle Propwash-Rezepte über „jedes
Propwash-Item“), Tabelle in `survival-tables.md`.

## 9. Tests

- JUnit `race.CourseElementTest` (19): Seitenachse, Stange nur auf der verlangten Seite, Slalom, Flagge links/rechts
  herum, Rücknahme auf halber Wende, Würfel nur mit Tauchen und richtigem Ausgang, falscher Ausgang und Durchflug
  melden, Kollisionsteile, Prüfpunkte, Verifier über alle Stufen, Ideallinie zählt jedes Element, Geister-Splits je
  Element. `race.CourseElementDataTest` (8): Kursdaten über das Netz, Layout-Prüfsumme, Weltdaten, Platzierung,
  Vorliebe mit Variante, kein Stapeln, Slalom-Seiten. `guide.CourseElementsSceneTest` (2).
- Selbsttest `race.client.CourseElementSelfTests` (`race:`): Setzen über `GateItem#useOn` (Lage, Boden, Kollisionsteile),
  Ablehnungen (Wand, Stapeln in beide Richtungen, Element als Kursstart), Kurs-Werkzeug mit Slalom-Seiten, Kursdaten über
  den Netz-Codec; Client-Abgleich und Runde entlang der Ideallinie im `LapTracker`, falsche Seite einer Stange; Server
  prüft eine Runde ohne Wende um die Flagge als ungültig und eine echte als gültig (Bestenliste); Einstellen per Schleichen
  + Rechtsklick wechselt den Ausgang des Würfels mit Mausrad, Rechtsklick, Schleichen + `R` und Enter (Vorschau, Server,
  Kursdaten); NBT und eingestellte Flagge, altes NBT,
  Drohne prallt an der Stange und am oberen Würfelbalken ab und taucht frei in den Würfel. `survival:` kennt die drei
  Rezepte (Reiter „Baumaterial“), `race: Rezepte …` ihre Freischaltung, `assets-world:` Sprites und Item-Modelle.
- Bildschirmfotos `course_elements.png`, `course_elements_slalom.png`, `course_elements_flag.png`,
  `course_elements_cube.png`, `course_elements_race_line.png`, `course_element_edit_mode(_de).png`,
  `tooltip_race_flag_de.png`, Flugschule `flight_school_course_elements*.png`.

## 10. Entscheidungen

| Frage | Entscheidung | Warum | Verworfen |
|---|---|---|---|
| Eigene Entity oder Gate | Elemente sind `GateEntity` mit Art und Variante | Kurs, Nummer, Abbau, Besitz, Weltdaten, Fenster und Synchronisation gibt es schon; Kurse bleiben eine Liste | eigene Entity-Arten je Element (doppelter Code für Kurs und Netz) |
| Slalom | einzelne Stangen, das Kurs-Werkzeug wechselt die Seite beim Anhängen | Abstände und Kurven frei wählbar, eine Stange ist überall nutzbar, Seite danach änderbar | Slalom-Item mit fester Reihe (starr, braucht eigenen Platz-Test) |
| Zählung | Stufen als Rechtecke/Halbebenen (`Passage`) wie der Gate-Durchflug | gleicher robuster Schnitttest, auch bei schnellen Wegstücken; Server prüft dieselben Stufen | Winkelsumme um die Flagge (fehleranfällig bei Kreisen und Rückwärtsflug) |
| Flagge | drei Stufen = halbe Wende (vorbei, davor quer, auf der anderen Seite zurück) | so wird im Rennen um eine Wendeflagge geflogen; Rückweg führt zum nächsten Element | volle 360°-Runde (unüblich, langsam) |
| Fehler | Fortschritt auf 0, Meldung, Element bleibt Ziel | entspricht „zurück und richtig fliegen“ beim Gate; keine Zeitstrafe | Zeitstrafe oder Runde ungültig |
| Elemente als Start | nicht über das Kurs-Werkzeug | Startaufstellung, Startampel und Start-/Ziel-Markierung brauchen ein Gate | Element als Start mit Sonderfällen |
| Neigen | Elemente stehen immer senkrecht, nur Gieren | Flaggen und Würfel stehen auf dem Boden; geneigte Halbebenen wären für Spieler unverständlich | freie Neigung wie bei Gates |
| Würfelgröße | lichte Öffnung 1–16, Würfel so tief wie breit | ein Regler weniger, MultiGP-Würfel sind Würfel | eigene Tiefe |
| Breite bei Stange/Flagge | „Wertung“: wie weit daneben ein Vorbeiflug zählt | verhindert Abkürzen weit außen und erlaubt enge oder großzügige Kurse | feste Reichweite |
| Server-Prüfung | nur die Stufen als Prüfpunkte, keine Fehler | wer falsch fliegt, erzeugt die Prüfpunkte nicht; der Client meldet die Fehler | Server spiegelt die komplette Fehlerlogik |
| Protokoll | bleibt 11 | 0.5.0 ist unveröffentlicht, 11 ist das Protokoll von 0.5.0 | weiterer Sprung auf 12 |
| Flugschule | eigene Szene nach „Gates frei drehen“ | die Gates-Lektion ist schon lang; Elemente brauchen eigene Schritte | Schritte in „Gates und Rennen“ |

## 11. Offene Punkte

- Flugtraining (`flight-training.md`): eine Lektion mit Slalomstangen, Wendeflagge und Würfel als Hologramm (braucht
  Hologramm-Darstellung der Elemente und neue Autopilot-Kalibrierung der Sterne).
- Rennkurse in der Welt (`world-courses.md`) mit Elementen.
- Port nach 1.21.1/26.2 ab 0.5.1: die reine Rechnung (siehe §6) wandert unverändert mit.
