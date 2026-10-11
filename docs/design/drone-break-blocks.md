# Drohnen zerbrechen Blöcke (interact, Propwash 0.5.0)

Anforderung vom 08.10.2026: eine weitere Welt-Regel, mit der eine Drohne eine Glasscheibe zerbricht, wenn sie mit
hohem Tempo hineinfliegt; standardmäßig aus, per Gamerule einschaltbar. Umgesetzt im Modul `interact`
(`docs/design/drone-interaction.md` beschreibt die Treffer an Lebewesen, auf denen der Ablauf aufbaut).

## 1. Was der Spieler erlebt

- Welt-Regel `propwash:drones_break_blocks` („Drohnen zerbrechen Blöcke“, Schalter, Standard **aus**, Gruppe Spiel):
  im Tab „Welt-Regeln“ (Host und Operatoren ändern, alle anderen lesen), mit `/gamerule`, im Vanilla-Fenster
  „Spielregeln“ und im Export für die Website. Aus = Blöcke bleiben immer ganz, wie bisher.
- Mit Regel an zerbricht eine Drohne zerbrechliche Blöcke, wenn die Wucht reicht: ein Whoop prallt an jeder Scheibe ab,
  ein 5-Zoll durchschlägt eine Glasscheibe ab etwa 42 km/h und einen Glasblock ab etwa 77 km/h. Die Drohne fliegt mit
  dem verbleibenden Tempo weiter; was der Block schluckt, spürt sie wie einen Aufprall (Piepen, Verschleiß, beim knappen
  Durchbruch auch ein Absturz).
- Der Durchbruch ist zu spüren und zu sehen (seit 09.10., §3.2): die Drohne verliert Tempo an das Glas, wird aus der
  Lage gekippt (schief oder nur mit einer Seite getroffen stärker), vordere offene Props verlieren kurz Drehzahl, das
  FPV-Bild bekommt einen kurzen Schlag, Scherben fliegen in Flugrichtung mit und das Klirren reist mit der Drohne. Eine
  Scheibe ist ein Ruck, den man ausfliegt; ein Glasblock ist ein grober Schlag oder ein Absturz. Wie stark, stellt die
  Welt-Regel `propwash:break_impact_scale` („Wucht beim Durchbruch“, 0–300 %, Standard 100) ein.
- Bruch wie beim Abbauen ohne Werkzeug: Glas lässt nichts fallen, getöntes Glas sich selbst, ein verzierter Topf
  zerspringt wie unter einem Pfeil in seine Scherben und gibt den Inhalt frei; Geräusch und Splitter sehen alle Spieler
  in der Nähe.
- Darf der Pilot dort nicht abbauen (Spawn-Schutz, Abenteuer- oder Zuschauermodus, Weltgrenze, Schutz- und Claim-Mods,
  Rahmen eines Freiform-Gates), prallt die Drohne ab, und ein Hinweis nennt den Grund.

## 2. Welche Blöcke (Block-Tag `propwash:drone_breakable`)

Datengetrieben, Datenpakete und Add-ons können ergänzen oder ersetzen:

| Eintrag | Warum |
|---|---|
| Glas, getöntes Glas, alle 16 gefärbten Gläser | das Bild aus dem Wunsch; gleiche Härte 0,3 |
| Glasscheibe und alle 16 gefärbten Scheiben | dünn, deshalb leichter zu durchschlagen als ein Block |
| verzierter Topf | Vanilla lässt ihn schon unter Geschossen zerspringen |
| `#c:glass_blocks`, `#c:glass_panes` (optional) | Glas anderer Mods über die Konventions-Tags beider Loader |

Nicht dabei: Laub (ein Schwarm schneller Drohnen würde Wälder kahl fliegen, echte Drohnen verfangen sich eher),
Eis (ein Meter Eis ist kein dünnes Glas, Eisstraßen und Seen blieben sonst nicht ganz), Glowstone, Seelaternen und
Ähnliches (kein Glas im Sinn des Wunsches). Unabhängig vom Tag zerbricht nie ein Block mit Härte über 1,0 oder ohne
Härte (Grundgestein), kein Befehls- oder Strukturblock (`GameMasterBlock`).

## 3. Wann ein Block bricht (`interact.BreakMath`, reines Java)

| Größe | Wert |
|---|---|
| Energie | E = ½ · m · v² mit m = Flugmodell-Masse samt Fracht, v = Annäherungstempo entlang der Kontaktnormalen |
| Schwelle | S = max(5 J, 400 J × Härte × (0,2 + 0,8 × Dicke)); Dicke = Ausdehnung der Kollisionsform entlang der Normalen (1/16 … 1) |
| Bruch | E ≥ S und v ≥ 3 m/s (langsames Anstupsen bricht nie); der Server nimmt 90 % der Schwelle (Rundung, Masse aus dem Bauplan) |
| danach | v' = √(v² − 2·S/m) entlang der Normalen, Tempo längs der Fläche bleibt; Δv = v − v' |

Schwellen: Glasscheibe frontal 36 J, Glasblock 120 J, verzierter Topf 5 J. Bruchtempo der eingebauten Presets:

| Drohne | Masse | Scheibe | Glasblock | Topf |
|---|---|---|---|---|
| Whoop 65 | 28 g | 182 km/h (nie) | 333 km/h (nie) | 68 km/h (nie) |
| Cinewhoop 3″ | 287 g | 57 km/h | 104 km/h | 21 km/h |
| Freestyle 5″ | 525 g | 42 km/h | 77 km/h | 16 km/h |
| Racer 5″ | 430 g | 47 km/h | 85 km/h | 17 km/h |
| Long Range 7″ | 846 g | 33 km/h | 61 km/h | 12 km/h |

Liegt die Drohne beim Aufprall an mehreren zerbrechlichen Blöcken zugleich an (Fuge), zählt der stärkste, und alle
brechen; liegt sie zugleich an einem festen Block an, bricht nichts (die Wucht geht in den Aufprall).

### 3.1 Verhältnis zum Crash- und Schadensmodell
Der Durchbruch läuft über `DroneSim#breakThrough(n, S)`: die Normalkomponente der Geschwindigkeit sinkt auf v', das
verlorene Tempo Δv geht als Aufprall in dieselbe Einordnung wie an einer Wand (Piepen „Bump“/„harter Aufprall“,
Absturz ab der Crash-Toleranz des Rahmens mit Entschärfen, wenn `crash_disarm` an ist) und als `ImpactSample` mit
Normale in die Verschleißrechnung (`CrashDamage`, Welt-Regeln `crash_damage`, `crash_damage_scale`). Je schneller die
Drohne, desto weniger verliert sie: 5-Zoll mit 140 km/h durch eine Scheibe −1,8 m/s, durch einen Glasblock −6,4 m/s
(harter Aufprall, kein Absturz); mit 30 m/s durch einen Glasblock −9,5 m/s (Absturz). Wer knapp über dem Bruchtempo
einschlägt, verliert fast alles und stürzt ab – wie in echt.

Entscheidung: weiterfliegen mit Energieverlust statt Absturz an jedem zerbrochenen Block. Warum: physikalisch richtig
(die Arbeit zum Zerbrechen ist begrenzt), entspricht dem Wunsch „durchschlagen“, und das vorhandene Crash-Modell
entscheidet über Absturz und Verschleiß ohne neue Regeln. Verworfen: der Block bricht, die Drohne prallt trotzdem ab
(unglaubwürdig bei 140 km/h); fester Tempoverlust je Block (unabhängig von Masse und Tempo).

### 3.2 Durchbruch spüren (`sim.BreakImpulse`, `sim.BreakSolver`, seit 09.10.)

Rückmeldung nach dem Testen: Man merkte nicht, dass die Drohne durch Glas geflogen ist. Es soll eine Kollision geben,
aber keine wie an einer Wand – das Glas gibt nach. Bis 08.10. sank nur die Normalkomponente der Geschwindigkeit um den
Betrag aus der Bruchenergie (5-Zoll durch eine Scheibe: −3 m/s bei 90 km/h, −1,8 m/s bei 140 km/h), ohne Drehung, ohne
Bild-Reaktion. Seit 09.10. rechnet `DroneSim#breakThrough(n, S, BreakContact)` vier Dinge, alle in der Sim des Piloten
(reines Java, kein Minecraft-Typ):

| Schritt | Rechnung | Begründung |
|---|---|---|
| 1 Bruch | v₁ = √(v² − 2·S/m) wie bisher; Δv_B = v − v₁ | Arbeit zum Zerbrechen (§3) |
| 2 Scherben | m_S = 0,15 · k · σ · A; v₂ = v₁ · m / (m + m_S); Δv_S = v₁ − v₂ | die Drohne reißt das Glas vor ihrer Silhouette mit und teilt ihren Impuls damit (Durchschlag-Formel nach Recht und Ipson: Restgeschwindigkeit = m/(m + m_Pfropfen) · √(v² − v_Grenz²)) |
| 3 Drehstoß | ΔL = min(1; 0,14 · k) · [ r_B × (m·Δv_B·n) + r_S × (m·Δv_S·n) ], Änderung der Drehrate auf 12 rad/s begrenzt | der Stoß greift nicht im Schwerpunkt an |
| 4 Props | offene Props, die vorn im Glas liegen und schneller als 30 % der Schwebedrehzahl drehen, verlieren 35 % · min(1; k) ihrer Drehzahl; ihr Drehimpuls geht als Gierstoß auf den Rahmen; Ereignis `PROP_STRIKE` | Scherben im Propkreis; Ducts schützen (wie bei Wandkontakt, nicht bei einem Treffer mit dem Bauch) |

- **k** = Welt-Regel `break_impact_scale` / 100 (0–3). k = 0 lässt nur Schritt 1 übrig – bitgleich mit dem Stand bis
  08.10.
- **σ** = Scherbenmasse je Fläche = S · 10/36 kg/(m²·J) (`BreakMath#shardMass`): Scheibe 10 kg/m² (4 mm Fensterglas,
  2500 kg/m³), Glasblock 33 kg/m², verzierter Topf 1,4 kg/m². Sie wächst mit Härte und Dicke wie die Schwelle.
- **A** = getroffene Fläche = (|n_x|·Seitenfläche + |n_y|·Draufsicht + |n_z|·Stirnfläche des Flugmodells in
  Körperachsen) × Anteil der 12 Hüllpunkte, die quer zur Flugrichtung in der Glasfläche liegen (3 cm Rand). Ein
  5-Zoll zeigt einer Scheibe frontal 120 cm², mit 35° Neigung 300 cm² – geneigt reißt er mehr Glas mit.
- **0,15** (`SHARD_COUPLING`): nur etwa ein Siebtel des Glases vor der Silhouette wird auf Flugtempo mitgerissen, der
  Rest zerfällt und fällt. So gewählt, dass ein 5-Zoll an einer Scheibe 3–9 % seines Tempos an die Scherben abgibt.
- **r_B** (Bruchstoß): Schwerpunkt der Hüllpunkte in der Glasfläche, gewichtet mit e^(−Tiefe/λ) (Tiefe = Abstand hinter
  dem vordersten Punkt in Flugrichtung, λ = halber Hüllradius), dazu ein Versatz quer zur Flugrichtung von bis zu 12 %
  des Hüllradius aus der Blockposition (Glas bricht nie gleichmäßig; gleiche Scheibe, gleicher Versatz). **r_S**
  (Scherben): ungewichteter Schwerpunkt derselben Hüllpunkte. Folgen: im Vorwärtsflug liegen die vorderen Props tief
  und vorn – der Stoß kippt die Nase nach unten; waagerecht und mittig getroffen bleibt fast nur der zufällige Versatz;
  liegt das Glas nur auf einer Seite (Rand eines Fensters, Loch vom letzten Durchflug), dreht der Stoß die Drohne um
  die Hochachse zur Glasseite.
- **0,14** (`TUMBLE_SHARE`): Anteil des Stoßes, der außermittig wirkt. Props und Arme geben nach, das Glas reißt in
  Millisekunden über die ganze Fläche; mit dem vollen Stoß am vordersten Punkt überschlüge sich jeder 5-Zoll (über
  30 rad/s). So gewählt, dass eine Scheibe bei 90 km/h die Drohne um 4–10 rad/s (230–570 °/s) anstößt und der
  Flugregler sie nach 3–10° Lagefehler hält.
- **Absturz und Verschleiß** entscheidet weiter allein Δv_B (§3.1): die Bruchgrenzen und Absturzbereiche der Tabellen
  oben gelten unverändert. Der Scherbenanteil ist über den Durchflug verteilt (Rumpflänge / Tempo ≈ 10 ms) und belastet
  den Rahmen eine Größenordnung weniger als der harte Anteil. Das `ImpactSample` trägt Δv_B, die Normale, die vorn
  anliegenden Hüllpunkte und die getroffenen Props; `CrashDamage` rechnet daraus den Prop-Verschleiß wie bei jedem
  Prop-Strike (im Reiseflug federn die Props die Scherben ab, bei Vollgas oder knappem Durchbruch gibt es Kerben).

Gemessen (Freestyle 5″, 525 g, Selbsttest `glasbruch:` im FPV-Flug auf Fabric, Regel 100 %, wenn nicht anders genannt):

| Treffer | Anflug | Bruch | Scherben | danach | Drehstoß | Lage nach 0,1 s | Kameraschlag |
|---|---|---|---|---|---|---|---|
| Scheibe, 30° geneigt | 15,4 m/s | −5,4 | −0,7 | 9,7 m/s | 12 rad/s (Deckel) | 15° | 2,8° |
| Scheibe, 35° geneigt | 24,0 m/s | −3,1 | −1,6 | 19,3 m/s | 9 rad/s | 10° | 2,1° |
| Scheibe, 45° geneigt | 35,0 m/s | −2,0 | −2,9 | 29,6 m/s | 7 rad/s | 7° | 2,2° |
| Glasblock, 35° geneigt | 24,0 m/s | −13,1 | −2,4 | Absturz | 12 rad/s | – | 3,0° |
| Glasblock, 45° geneigt | 35,4 m/s | −7,2 | −6,8 | 21,1 m/s | 12 rad/s | 15° | 3,0° |
| Scheibe, Regel 0 % | 23,9 m/s | −3,1 | 0 | 20,8 m/s | 0 | 0° | 1,4° |
| Scheibe, Regel 200 % | 23,9 m/s | −3,1 | −3,0 | 17,9 m/s | 12 rad/s | 16° | 2,7° |
| Scheibe nur rechts, waagerecht | 23,3 m/s | −3,2 | −0,2 | 19,7 m/s | 12 rad/s, Gier −11,4 | 18° | 1,5° |
| Scheibe nur links, waagerecht | 23,3 m/s | −3,2 | −0,2 | 19,7 m/s | 12 rad/s, Gier +11,4 | 18° | 1,5° |

Die beiden einseitigen Flüge führen die Drohne im Schweben vor dem Anflug seitlich auf ihren Versatz (11,25 cm neben
der Blockmitte, ein Drittel der Hülle im Glas) und starten erst, wenn sie höchstens 8 mm daneben steht: Schon wenige
Zentimeter Drift aus dem Schweben entschieden sonst, ob sie das Glas halb, ganz oder gar nicht trifft. (Die Position
im Sim-Zustand zu setzen, wie es der Test zuerst tat, verschiebt die Drohne nicht – die Sitzung bewegt das Entity nur
um den Weg eines Sim-Schritts; mit 13–15 cm Drift flog sie am Glas vorbei. Der Test regelt deshalb die
Seitengeschwindigkeit und wiederholt einen Flug, der das Glas trotzdem nicht berührt hat.) Beide Seiten drehen
gleich stark in entgegengesetzte Richtung. Die Tabelle zeigt einen Lauf; die Werte früherer Läufe streuten mit der
Drift (Gier 10,7–11,8 rad/s je Seite, Drehstoß der Scheibe bei 25 m/s 7–9 rad/s), geprüft werden Vorzeichen und
Grenzen.

In allen Flügen ohne Absturz steht die Drohne danach wieder ruhig und bleibt scharf. Die Scheibe im Selbsttest liegt mit
ihrem Versatz am oberen Rand der Streuung; über 200 Blockpositionen (JUnit, 35° und 25 m/s) gibt eine Scheibe dem
5-Zoll 3,8–9,9 rad/s, im Mittel 6,7, und kostet 4,6 m/s; mit 140 km/h und 45° sind es 3,4–7,3 rad/s und 5,0 m/s (davon
1,8 m/s Bruch – der Stand bis 08.10.). Ein Glasblock mit 140 km/h kostet 14 m/s (6,4 Bruch) und stößt mit dem Deckel,
ohne Absturz. Racer 5″ (430 g) an der Scheibe bei 90 km/h: −5,2 m/s; Long Range 7″ (846 g): −3,3 m/s und 2,5–5,8 rad/s;
Cinewhoop (287 g, Ducts, keine Prop-Treffer): −7,6 m/s.

**Client und Server.** Geflogen wird clientautoritativ; der Server prüft nur, ob der Block brechen darf (§4), und
übernimmt Lage und Tempo aus den Zustandspaketen des Piloten. Der Stoß entsteht deshalb nur in der Sim des Piloten und
kann nicht gegen eine zweite Rechnung laufen: im Selbsttest springt die Drohne nie zurück, die Server-Entity liegt nach
dem Durchflug höchstens 5 cm neben der des Clients. Die Regelstärke kommt wie alle Welt-Regeln vom Server.

**Sehen und Hören** (nur beim Piloten zusätzlich; alle anderen sehen und hören den Bruch wie bisher über den Server):

- Kameraschlag (`video.ImpactJolt`, `drone.client.FpvShake`): gedämpfte Schwingung der Kamera, Ausschlag 0,45° je
  verlorenem m/s (bis 3°), Nicken 13 Hz, Rollen × 0,6 mit 15,5 Hz, Gieren × 0,4 mit 11 Hz, klingt mit τ = 0,11 s ab.
  Gleiche Zeitbasis wie die Kamera-Vibration (`docs/design/fpv-feel.md` §3): der Schlag erscheint in dem Bild, das den
  Durchbruch zeigt (Videolatenz), folgt der Einstellung „Kamera-Vibration“ (0 % = aus, bis 200 %) und ändert nur das
  Bild, nie Sim, OSD, Rennwertung oder Posen-Puffer.
- Scherben (`interact.client.BreakEffects`, `ShardParticle`): 48 je Block (Partikel-Einstellung „Reduziert“ 24,
  „Minimal“ 7; bei kleinem Trefferanteil weniger), 2,5–10 cm groß, aus dem Rand der Block-Textur (bei klarem Glas der
  helle Rahmen statt der durchsichtigen Mitte, bei getöntem die Farbe), mit 55–145 % des Anflugtempos in Flugrichtung
  und 8–30 % nach außen, taumelnd. Sie starten dort, wo sie beim nächsten Tick wären, sonst lägen sie hinter der Kamera,
  bis die Partikel-Engine sie übernimmt. Die Vanilla-Splitter am Block bleiben.
- Geräusch: das Bruchgeräusch des Blocks ein zweites Mal an der Drohne (`ClientLevel#playLocalSound(Entity, …)`, tiefer,
  Lautstärke 0,45 + 0,14 je verlorenem m/s, höchstens 1,6) – im FPV-Flug hört man das Klirren, obwohl die Scheibe nach
  einem Zehntel schon Meter zurückliegt.

## 4. Ablauf

Geflogen wird clientautoritativ (`docs/api/network-entity.md` 2.4); der Server entscheidet über den Block.

0. **Freigabe vorab** (seit 08.10., `PilotBreaks#clearAhead` am Ende jedes Frames, höchstens alle 2 Ticks): Der Client
   sucht zerbrechliche Blöcke vor der Drohne – Kasten um die Drohne, 6 Blöcke Rand, nach vorn bis zu 1 s Flug (höchstens
   24 Blöcke); Abschnitte ohne Tag-Block überspringt er über die Palette (`LevelChunkSection#maybeHas`). Für die
   dringendsten 32 Blöcke ohne gültige Freigabe fragt er `DroneBreakClearC2S` (Drohne, Epoche, Blöcke). Dringend heißt
   (`interact.ClearOrder`, reines Java): zuerst Blöcke im Flugweg (seitlich höchstens 1,5 Blöcke plus halbe Drohne
   neben der Flugbahn) nach der Zeit bis zum Aufprall, danach alles andere nach Zeit und seitlichem Abstand; Glas, das
   bei mehr als 6 m/s weiter als 4 Blöcke hinter der Drohne liegt, wird ausgelassen. Unter 2 m/s zählt nur der Abstand.
   Bleiben mehr Blöcke übrig, als in eine Anfrage passen, fragt der Client im nächsten Tick weiter (28 Blöcke je Tick)
   statt erst nach zwei. Der Server
   (`DroneBreaks#clearance`) prüft dieselben Rechte wie beim Bruch, ohne Tempo und Energie: Regel, Tag, Abstand zur
   Drohne ≤ 32 Blöcke plus 0,5 s Flugweg beim Tempo der Drohne am Server (höchstens 64, `DroneBreaks#clearRange`),
   Gate-Rahmen, Spielmodus, `mayInteract` (Spawn-Schutz, Weltgrenze) und als Probe das Abbau-Ereignis der
   Loader (Fabric nur `BEFORE`, ohne `CANCELED`; NeoForge `BreakBlockEvent`), auf das Schutz- und Claim-Mods hören. Nach
   der ersten Schutz-Absage einer Anfrage gelten Blöcke im Umkreis von 24 ohne weitere Probe als geschützt, damit
   Claim-Mods nicht für jede Scheibe eine Meldung schicken. Antwort `DroneBreakClearS2C` je Block mit Grund; freigegeben
   gilt 10 s und wird 3 s vor Ablauf neu erfragt, „geschützt“ landet in der Merkliste (24 Blöcke, 2 Minuten). „Zu
   weit“ erfragt der Client beim nächsten Scan erneut (sonst erst nach 1 s). Höchstens 640 geprüfte Blöcke je Sekunde
   und Drohne (vorher 20 Anfragen zu je 32, also dieselbe Obergrenze).

   *Schneller Anflug (Nachtrag 09.10.):* Die erste Anfrage geht ab, wenn die Scheibe noch bis zu 30,7 Blöcke entfernt
   ist; die feste Server-Grenze von 32 Blöcken, gemessen an der um einen Zustands-Takt älteren Server-Position, lehnte
   sie bei 36 m/s gelegentlich als „zu weit“ ab (32,08 gegen 32,0), der Client fragte 1 s lang nicht nach und die
   intakte Scheibe wirkte als Wand: 4 von 20 Flügen bei 30 Bildern/s, 2 von 26 bei 60. In einer Glasröhre (16
   Glasblöcke je Meter) kam die Scheibe im Flugweg zudem erst nach dem Glas ringsum an die Reihe. Beides prüft der
   Selbsttest jetzt aus 50 Blöcken Anlauf (`glasbruch: Anflug …`, `glasbruch: Glasröhre …`, 40 Blöcke über dem Boden):
   Freigabe mindestens 15 Blöcke vor der Scheibe, kein ungeklärter Treffer, kein Absturz.
1. **Client (Pilot)**, vor jeder Bewegung der Drohne (`PilotInteraction#clip` → `interact.client.PilotBreaks#beforeMove`):
   nur mit Regel an. Er sucht im überstrichenen Bereich (höchstens 128 Blöcke, alle geladen) die Kollisionsformen und
   spielt die Vanilla-Achsenreihenfolge nach. Begrenzt ein zerbrechlicher Block die Bewegung und reicht die Energie
   (`BreakMath`), prüft er, was er wissen kann: Spielmodus (`Player#blockActionRestricted`), Weltgrenze, Rahmen eines
   Freiform-Gates, abgelehnte Stellen der letzten Zeit – und ob der Server den Block vorab freigegeben hat. Ohne
   Freigabe (noch keine Antwort oder abgelehnt) ist der Block eine Wand: die Drohne prallt ab, nichts wird entfernt.
   Dann sagt er den Bruch voraus wie Vanilla beim Abbauen: Block
   lokal entfernen (Flüssigkeit bleibt), Splitter und Geräusch (`LevelEvent` 2001), `DroneSim#breakThrough`, Anfrage
   `DroneBreakC2S` (Drohne, Epoche, Block, Geschwindigkeit vor dem Bruch, Achse, Richtung) am Ende des Frames. Die
   Bewegung geht danach unverändert durch Vanilla.
2. **Server** (`interact.DroneBreaks#attempt`, Reihenfolge in `BreakRules#decide`): nur geladene Blöcke; Pilot und
   Epoche; Regel an; Block im Tag, zerbrechlich, kein `GameMasterBlock`; höchstens 20 Brüche je Sekunde und Drohne;
   Tempo begrenzt wie bei Treffern auf `min(Server-Grenze, Spitze der letzten 20 Ticks × 1,25 + 4 m/s)`; Abstand der
   Drohne zum Block ≤ 1,5 m + 0,15 s × Tempo; Energie mit Masse aus Bauplan und Fracht; Rahmen eines Freiform-Gates;
   `blockActionRestricted` mit dem Spielmodus des Piloten; `ServerLevel#mayInteract` (Spawn-Schutz, Weltgrenze); zuletzt
   die Abbau-Ereignisse der Loader (Fabric `PlayerBlockBreakEvents.BEFORE`, bei Absage `CANCELED`; NeoForge
   `BreakBlockEvent`), die Schutz- und Claim-Mods auswerten.
3. **Bruch**: hat der Block die Eigenschaft `cracked` (verzierter Topf), wird sie gesetzt; `LevelEvent` 2001 an alle
   außer dem Piloten (der hat es schon); Beute über `Block#dropResources` mit leerem Werkzeug und dem Piloten als Entity
   (keine Beute für Spieler im Kreativmodus, `Player#preventsBlockDrops`); Block durch seine Flüssigkeit ersetzen
   (Inhalt von Behältern fällt heraus); Spielereignis `BLOCK_DESTROY` mit der Drohne als Quelle; Fabric
   `PlayerBlockBreakEvents.AFTER` (Protokoll-Mods). Ereignis `DroneBreaks.BROKEN` (intern, Selbsttest).
4. **Ablehnung**: Der Server schickt dem Piloten den echten Block zurück (`ClientboundBlockUpdatePacket`, bei
   Block-Entities auch deren Daten) und `DroneBreakDeniedS2C` mit dem Grund. Der Client merkt sich bei „geschützt“ die
   Umgebung (24 Blöcke, 2 Minuten, deckt den Spawn-Schutz mit Radius 16 ab), sonst nur den Block (10 s), und sagt dort
   keinen Bruch mehr voraus; bei Spielmodus, Schutz und Gate-Rahmen erscheint ein Hinweis (höchstens alle 3 s).

Mehrspieler: Andere Spieler sehen den Bruch über den Server (Block-Update, Splitter, Geräusch), die Drohne über die
bekannten Zustandspakete. Auf einem dedizierten Server gilt dasselbe; der Spawn-Schutz greift nur dort (wie bei
Vanilla, ohne Operatoren auf der Liste nicht).

Geschützte Bereiche: Spawn-Schutz und Schutz-Mods kennt der Client nicht selbst, er fragt sie über die Freigabe vorab
ab. Deshalb prallt die Drohne dort schon beim ersten Treffer ab wie an einer Wand; kein Block verschwindet kurz, und der
Hinweis nennt den Grund. Bis 08.10. flog sie beim ersten Treffer durch und der Block erschien wieder. Grenze: Kommt die
Freigabe nicht rechtzeitig (Drohne startet direkt vor der Scheibe, sehr hohe Latenz), prallt die Drohne auch an einem
erlaubten Block einmal ab – lieber einmal zu viel abprallen als durch einen geschützten Block fliegen. Ändert sich der
Schutz innerhalb der 10 s einer Freigabe, entscheidet weiter der Server beim Bruch (Block kommt zurück).

## 5. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Id `propwash:drones_break_blocks`, Gruppe Spiel, neben `drone_hits_entities` | Katalog-Schema `propwash:<name>`; gehört zu den Folgen eines Treffers | `propwashDronesBreakBlocks` (camelCase passt nicht zu 26.3) |
| Standard aus, in allen Voreinstellungen aus | verändert die Welt dauerhaft; Wunsch; keine Physik-Regel | Realistisch an |
| Energie entlang der Normalen gegen Schwelle aus Härte und Dicke | schräges Streifen bricht nicht; Scheiben sind dünner als Blöcke; Härte ist der Vanilla-Maßstab | Impuls (Masse × Tempo) wie bei Treffern – würde schwere langsame Drohnen bevorzugen |
| Client sagt voraus, Server entscheidet | nur so fliegt die Drohne ohne Ruckeln durch; wie Vanilla-Abbauen | erst nach Antwort des Servers brechen (Drohne prallte immer erst ab) |
| Freigabe vorab für Blöcke vor der Drohne (08.10.): Vorhersage nur mit Freigabe, sonst Wand | in Spawn-Schutz und Claims prallt die Drohne schon beim ersten Treffer ab, kein Block blitzt weg | Spawn-Schutz an die Clients schicken (Claims blieben unbekannt); weiter einmal durchfliegen |
| Probe über das Abbau-Ereignis der Loader (Fabric nur `BEFORE`), danach 24 Blöcke ohne weitere Probe | nur so kennt die Freigabe Claims; eine Absage je Bereich statt einer Meldung je Scheibe | eigenes API-Ereignis (Schutz-Mods kennen es nicht); jede Scheibe einzeln proben |
| Ohne Antwort ist der Block eine Wand | lieber einmal zu viel abprallen als durch einen geschützten Block fliegen | ohne Antwort vorhersagen wie bisher |
| Weiterfliegen mit Energieverlust, Δv als Aufprall | §3.1 | §3.1 |
| Rahmen von Freiform-Gates bleiben ganz | ein Durchflug-Fenster aus Glas soll im Rennen nicht kaputtgehen | auch Gate-Rahmen zerbrechen |
| Abbau-Ereignisse der Loader statt eigener API | Schutz- und Claim-Mods hören schon darauf; der Pilot gilt als Verursacher | eigenes Ereignis in der öffentlichen API (API_VERSION bleibt) |
| Nur der Pilot zerbricht, nicht der Server-Heimflug (GPS Rescue ohne Pilot) | ohne Pilot am Steuer gäbe es keinen Verursacher mit Rechten; der Heimflug weicht Hindernissen ohnehin aus | Server-Flug zerbricht wie ein Pilot |
| Abgelehnte Blöcke nur in der Nähe von Pilot oder Drohne (16 Blöcke) zurückschicken | eine gefälschte Anfrage soll keine fernen Blockzustände abfragen können | jede Anfrage beantworten |
| Kein eigener Sprung des Netzprotokolls (0.5.0 hat 13, seit der Beitrittsprüfung) | 0.5.0 ist unveröffentlicht und zählt schon hoch; zwei neue Pakete in derselben Version | eigener Sprung nur für den Blockbruch |
| Durchbruch spüren (09.10.): Scherben teilen den Impuls, Stoß außermittig, Prop-Strikes | physikalisch begründet, hängt an Masse, Tempo, Lage, Glasdicke und Trefferseite | fester Ruck je Block; zufälliges Taumeln ohne Bezug zur Geometrie |
| Absturz weiter nur aus der Bruchenergie | die Drohne soll durch Glas fliegen, nicht wie an einer Wand enden; die dokumentierten Bruch- und Absturztempi bleiben; der Scherbenanteil ist zeitlich verteilt | ganzes verlorenes Tempo zählt (ein 5-Zoll stürzte dann an jedem Glasblock und ab 140 km/h an jeder Scheibe ab) |
| Scherbenmasse aus der Schwelle (10 kg/m² je 36 J) | Scheibe und Block unterscheiden sich ohne neue Daten; Datenpaket-Blöcke bekommen sie aus Härte und Dicke | eigene Masse je Block im Tag oder in Datendateien |
| Angriffspunkt tiefengewichtet, nur 14 % außermittig, Deckel 12 rad/s | voller Stoß am vordersten Prop überschlägt jede Drohne; so bleibt eine Scheibe ein Ruck | Stoß durch den Schwerpunkt (kein Taumeln); voller Hebel |
| Versatz aus der Blockposition statt Zufall je Bruch | jede Scheibe bricht anders, Tests und Wiederholungen bleiben reproduzierbar | Zufallszahl je Bruch |
| Eine Welt-Regel `break_impact_scale` (0–300 %), 0 = Stand bis 08.10. | Physik gilt für alle (Welt-Regeln statt persönlicher Einstellung); ein Regler statt fünf | persönliche Einstellung; einzelne Regeln für Scherben, Drehstoß, Props |
| Regel skaliert Scherben, Drehstoß, Props – nicht die Schwelle | ob ein Block bricht, prüft der Server; die Entscheidung bleibt von der Stärke unabhängig | Regel verschiebt auch das Bruchtempo |
| Kameraschlag folgt der persönlichen „Kamera-Vibration“ | reines Bild wie die Vibration; wer sie abschaltet, will ein ruhiges Bild | eigene Einstellung; an die Welt-Regel hängen |
| Kameraschlag nur beim Durchbruch | Auftrag war das Glas; ein Schlag bei jedem Aufprall gehört zum Fluggefühl insgesamt | Schlag bei jedem `ImpactSample` |
| Scherben und zweites Klirren nur beim Piloten | ohne neues Paket; andere sehen den Vanilla-Bruch und die taumelnde Drohne | Paket an alle in der Nähe |
| Vanilla-Bruchgeräusch statt eigener Aufnahme | klingt je Block richtig (Topf, Glas anderer Mods), folgt Ressourcenpaketen | eigene Klangdatei |
| Kein Sprung des Netzprotokolls, API_VERSION bleibt | kein neues Paket, keine neue öffentliche API; die Regel reist im vorhandenen Welt-Regeln-Paket (Liste nach Namen) | Protokoll 15 |

## 6. Tests

- JUnit: `BreakMathTest` (Schwellen, starke Blöcke nie, Whoop nie, 5-Zoll bei Vollgas durch, Energieverlust, Fracht,
  Mindesttempo, Toleranz des Servers), `BreakRulesTest` (Reihenfolge der Prüfungen, Gründe, Merkdauer, Rahmen eines
  Freiform-Gates), `BreakThroughTest` (Normalkomponente sinkt, Tempo längs bleibt, Aufprall im Log, knapper Bruch = Absturz,
  nie rückwärts), `InteractPayloadsTest` (alle vier Pakete, Freigabe mit höchstens 32 Blöcken),
  `ClearOrderTest` (Scheibe im Flugweg vor dem Glas ringsum, Glas hinter der Drohne, Server-Reichweite je Tempo).
- JUnit zum Durchbruch (09.10.): `BreakImpulseTest` (Energie- und Impulsbilanz, getroffene Fläche, Grenzen der Stärke,
  reproduzierbarer Versatz), `BreakFeelTest` (0 % = Stand bis 08.10. bitgleich; Scheibe gegen Glasblock bei 23–40 m/s;
  schwere Drohne verliert weniger; geneigter Treffer kippt die Nase nach unten, waagerechter kaum; Glas rechts oder
  links giert gegensinnig; Deckel und Regelstärke; offene Props getroffen, Ducts und Bauchtreffer nicht; Absturz
  unabhängig von der Stärke; Prop-Verschleiß nur bei Vollgas oder knappem Durchbruch; der Flugregler fängt eine Scheibe
  bei 85 km/h mit 4–16° Lagefehler und bleibt scharf), `ImpactJoltTest` (Ausschlag, Latenz, Abklingen, Einstellung),
  `BreakEffectsTest` (Scherbenzahl, Lautstärke), `BreakMathTest#shardMassFollowsTheThreshold`.
- Selbsttest zum Durchbruch (beide Loader, Gruppe `glasbruch:`, im FPV-Flug): Regel registriert, Standard 100 %; neun
  Flüge (Tabelle §3.2, dazu Glas nur rechts und nur links) messen Tempoverlust, Drehstoß, Lagefehler, Kameraschlag im
  Bild (je Bild gemessen), Scherben, Geräusch und prüfen, dass die Drohne nie zurückspringt, der Server ihr folgt und
  genau ein Block bricht; der Vergleich am Ende verlangt: Tempoanteil der Scheibe fällt mit dem Tempo, Glasblock mehr
  als doppelt so hart, 0 % < 100 % < 200 %, Glas rechts und links gieren gegensinnig.
- Selbsttest (beide Loader, Gruppe `blockbruch:`): Regel registriert, Standard aus, Tag geladen (Glas, Scheiben, Topf
  ja; Stein, Obsidian, Laub, Eis, Grundgestein nein); Regel aus – Scheibe hält bei 25 m/s; Whoop im Schritttempo und
  5-Zoll mit 8 m/s prallen ab; 5-Zoll zerbricht die Scheibe mit 25 m/s (genau ein Block, −3 m/s, kein Absturz, nichts
  fällt heraus); 5-Zoll zerbricht einen Glasblock mit 30 m/s und stürzt ab; Abenteuermodus – keine Vorhersage, direkte
  Anfrage am Server mit `GAME_MODE` abgelehnt; geschützter Bereich (interner Schutz `DroneBreaks.GUARDS` wie ein
  Claim-Mod) – Freigabe vorab abgelehnt, die Drohne prallt beim ersten Treffer mit 25 m/s ab, keine Bruch-Anfrage, die
  Scheibe verschwindet beim Client nie; verzierter Topf im Überleben – Scherben und Inhalt fallen heraus.
- Server-Rauchtest (dedizierter Server, beide Loader): ein Gast ohne Recht am Spawn wird mit `PROTECTED` abgelehnt
  (Spawn-Schutz 16, ein Operator auf der Liste) – schon die Freigabe vorab (`DroneBreaks#clearance`) –, als Operator und außerhalb des Schutzes zerbricht er, im Abenteuermodus
  und mit Regel aus nicht; Freigabe vorab und Bruch nennen in jedem Fall denselben Grund (`interact.BlockBreakRights`).
- Bildschirmfotos (Fabric): `block_break_rule_off`, `block_break_pane`, `block_break_glass_block`, `block_break_pot`,
  `configurator_world_rules_drones_break_blocks` und `_de`; zum Durchbruch `glass_break_fpv_pane` und
  `glass_break_fpv_block` (FPV-Bild einen Tick nach dem Durchbruch, Scherben voraus), dazu nur zur Durchsicht unter
  `docs/screenshots/glass_feel/` (von git ignoriert) je Anflug und Durchbruch; die Regel im Tab „Welt-Regeln“:
  `configurator_world_rules_break_impact_scale` (200 %) und `_de` („Kein Stoß“).
- Stand 09.10.: JUnit 2606 Tests (2600 grün, 6 abgebrochene Annahmen), Kalibrierung 11/11 und bitgleich mit dem Stand
  davor (`motion.txt`, `calibration.txt`); Selbsttest gefiltert Fabric `glasbruch:` 16 Schritte PASS, Fabric
  `blockbruch:,kamera:,server:,interact:` 44 Schritte PASS, NeoForge `glasbruch:,blockbruch:,kamera:,server:` 45 Schritte
  PASS, Server-Rauchtest beider Loader PASS.
