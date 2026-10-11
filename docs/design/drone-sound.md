# Drohnen-Ton für Zuschauer und Hörweite (0.5.0)

Anlass: Rückmeldung aus dem Spieltest vom 08.10. – fremde Drohnen rauschten wie Fahrtwind (vor allem große), und
selbst ein 75-mm-Whoop war noch in rund 60 Blöcken zu hören. Dieses Dokument hält die Bestandsaufnahme, das neue
Modell, die Zahlen und die Entscheidungen fest. Code: `audio.SoundReach` (reines Java), `audio.MotorSynth`,
`audio.MotorControl`, `audio.client.DroneVoice`, `audio.client.MotorVoices`.

## 1. Bestandsaufnahme (Stand vor der Änderung)

| Schicht im Synthesizer | Was sie darstellt | Pilot im FPV | alle anderen (Zuschauer, Pilot auf Sicht) |
|---|---|---|---|
| Blattfrequenz + Obertöne, Rotor-Grundton | Propeller (tonal) | ja | ja |
| Motorpfeifen (Polpaare × Drehzahl) | Motoren | ja | ja |
| Luftrauschen (Bandpass um 2,2 × Blattfrequenz) und Zischen | Breitbandrauschen der Props | ja | ja |
| Duct-Resonanz | Ducts von Cinewhoops | ja | ja |
| **Rumpeln** (weißes Rauschen, zweifach tiefpassgefiltert bei 75 Hz) | Rahmen-Vibration und Wind am Kamera-Mikrofon, Prop-Wash | ja | **ja** – Grundpegel 0,12 × Last, im Sinkflug bis 1,1 × Rumpelstärke, die mit der Propgröße wächst |
| **Fahrtwind** (Bandpass 450–1550 Hz) | Wind an der Kamera | ja (Fluggeschwindigkeit) | nein (Fahrtwind 0) |
| Aufschlag-Puls | Crash/Prop-Strike aus der Telemetrie | ja | nein |

- Das „Windrauschen“ bei fremden großen Drohnen war das **Rumpeln**: Es hing an der Rumpelstärke der Größe
  (`0,08 + 0,55 × Größe`, X-Class mehr) und an der Prop-Wash-Schätzung der Zuschauerseite, die jeden Sinkflug über
  2 m/s als Prop-Wash zählte – auch einen schnellen Sturzflug nach vorn, bei dem der Rotor sein eigenes Nachlaufgebiet
  längst verlassen hat.
- Abschwächung: Minecrafts `LINEAR` (OpenAL `AL_LINEAR_DISTANCE`, voller Pegel bei 0 Blöcken, linear auf 0 bei der
  Abschwächungsdistanz). Live-Sound: `48 × rangeFactor` Blöcke (65-mm-Whoop 48, 75-mm-Whoop 53, 5″ 103, X-Class
  bis 211), dazu die Stimme bis Reichweite + 8 Blöcke. Aufgenommener Ersatz-Sound: immer 96 Blöcke. Linear heißt: in
  halber Distanz noch −6 dB, ein Whoop in 30 Blöcken also noch fast halb so laut wie direkt daneben. Das ist der
  Grund für die 60 Blöcke beim Whoop.
- Crash: Der Pilot hört den Crash-Ton (UI, nicht positionsgebunden) und den Aufschlag-Puls im Motorton. Zuschauer hören
  das Zerbrechen von Teilen (Vanilla-Ton „Gegenstand zerbricht“ vom Server, 16 Blöcke) – unverändert.
- Verdeckung durch Blöcke gibt es im Audio nicht (nur beim Videosignal) – unverändert.

## 2. Wer was hört (neu)

| | Pilot im FPV | alle anderen |
|---|---|---|
| Propeller, Motoren, Breitbandrauschen, Duct | unverändert | unverändert |
| Fahrtwind an der Kamera | unverändert | **nie** (`MotorControl.setOnboard(false)`) |
| Rumpeln (Rahmen, Mikrofon) | unverändert | **keins** |
| Prop-Wash-Rauschen | unverändert (aus der Simulation) | ein Viertel so laut (−12 dB) und nur bei Sinkflug **ohne** Vorwärtsfahrt (voll unter 0 m/s, weg ab 8 m/s waagerecht) |
| Abstand | keiner (Kamera an Bord) | Pegel nach §3, Luftdämpfung nach §4 |

Gemessen im JUnit-Test (`MotorSynthTest`): Rumpelband draußen 17–19 dB unter dem des Piloten (5″, 7″), im
Prop-Wash 13 dB darunter; Fahrtwind draußen wirkungslos (Sample für Sample gleich). Der Klang des Piloten bleibt
bitgleich (Prüfsumme über alle Presets mit Drehzahl-, Wash-, Fahrtwind- und Aufschlag-Verlauf, berechnet auf dem Stand
vor der Änderung).

## 3. Schallpegel und Hörweite

**Quellpegel** (A-bewertet, 1 m, beim Schweben), in zwei Schritten:

```
L_Rotor = 80 dB(A) + 10 · log10(P / 60 W) + 30 · log10(M / 0,21)
L_Größe = 80 dB(A) + 18 · log10(D / 130 mm)
L       = L_Größe + begrenzt(0,5 · (L_Rotor − L_Größe), −1 dB … +1 dB)
```

- `P` = Wellenleistung aller vier Motoren beim Schweben (`4 · kTorque · ω³` aus `AirframeSpec`), `M` = Mach-Zahl der
  Blattspitzen (`ω · r / 343 m/s`). Die Schallleistung kompakter Dipolquellen (Curle) wächst mit `ρ U⁶ D² / c³`, die
  Wellenleistung mit `ρ U³ D²`; der akustische Wirkungsgrad ist also `∝ M³`. Propgröße, Drehzahl und Blattzahl gehen
  über `P` und `M` ein (mehr Blätter → weniger Drehzahl für denselben Schub → leiser).
- `D` = Propdurchmesser. Die Größenkurve geht durch die Owner-Vorgaben (65-mm-Whoop rund 19, 5″ rund 70, 13″-X-Class
  160 Blöcke; 18 dB je Dekade Propgröße). Der Rotor-Pegel aus Leistung und Mach-Zahl verschiebt die Drohne davon nur um
  die Hälfte und höchstens ±1 dB (Hörweite ±12 %): schwere oder hochdrehende Bauweisen klingen etwas weiter, leichte
  etwas kürzer, die Reihenfolge nach Größe bleibt. Ohne die Begrenzung trug der eingebaute 3″-Cinewhoop (287 g,
  Blattspitzen-Mach 0,28, 72 W) schwebend 121 Blöcke weit – weiter als ein 7″ und fast so weit wie eine X-Class 10″ –,
  und der 75-mm-Whoop von Just More Parts (40-mm-Props, Mach 0,14) nur 10.
- Bezug: 5″-Freestyle schwebend = 80 dB(A) in 1 m. Messwerte zum Vergleich: zwei Quadcopter mit 33 und 45 cm
  Achsabstand (DJI F330/F450) 74,9/76,9 dB(A) in 1 m bei wenig Gas und 88,3/91,8 dB(A) bei viel Gas
  (DroneAudioset, arXiv 2510.15383, Tabelle 5); DJI Phantom 2 (1,6 kg) 69 dB(A) schwebend in rund 6 m Höhe
  (NASA, Cabell et al. 2016) – nach dem Abstandsgesetz rund 84 dB(A) in 1 m; EU-Klasse C1 erlaubt höchstens
  81–85 dB(A) Schallleistung, das sind 70–74 dB(A) in 1 m (Mavic 3 und Air 3 haben 83 und 81 dB). Ein 5″ ist
  lauter als diese ruhigen Kameradrohnen, aber leiser als sie bei Vollgas.
- **Abstandsgesetz**: Pegel in `r` Metern = `L − 20 · log10(r)` (−6 dB je Verdopplung, Kugelwelle).
- **Hörschwelle** 43 dB(A): ruhiger Hintergrund draußen (35–45 dB(A)). Hörweite beim Schweben
  `R = 10^((L − 43) / 20)` Blöcke, begrenzt auf 4–160 Blöcke (160 = 10 Chunks, die Standard-Sichtweite; weiter
  schickt der Server die Drohne meist gar nicht).
- **Gas**: Die Hörweite wächst mit der Wurzel aus der Lautheit des Synthesizers (`Σ (Drehzahl/max)^0,6 ·
  (0,6 + 0,4 · Last)`, dieselbe Kurve wie der Motorton selbst): `R(t) = R · (A(t)/A_schweben)^0,5`. Echte Rotoren
  werden von Schweben bis Vollgas rund 25–30 dB lauter (`60 · log10(Drehzahlverhältnis)`), der Motorton im Spiel
  bewusst nur rund 6–10 dB; die halbe Wurzel hält einen voll beschleunigenden 5″ unter 120 Blöcken.
- **Lautstärkekurve** für alle außer dem FPV-Piloten: voller Pegel bis 3 Blöcke (`NEAR_BLOCKS`), dann `3/r`
  (−6 dB je Verdopplung), ab der halben Hörweite zusätzlich weich ausgeblendet (Smoothstep, ohne Knick) bis 0 bei
  `R(t)`. Der Pegel direkt neben der Drohne ist derselbe wie für den Piloten (Größen-Lautheit des Motortons), damit
  eine Drohne aus der Nähe nicht anders klingt als durch die Brille.
- Minecraft schwächt die Motor-Sounds nicht mehr selbst ab (`Attenuation.NONE`, Richtung bleibt räumlich); die Kurve
  rechnet `DroneVoice` je Tick und je Bild (aus der interpolierten Position), der Synthesizer glättet je Sample. Der
  Live-Sound meldet außerhalb der Hörweite Lautstärke 0 (kein Untertitel mehr), die Stimme endet ab
  `1,15 · R + 4` Blöcken. Rangfolge für den Live-Sound: Entfernung relativ zur Hörweite (die am besten hörbaren zuerst).

| Bauweise (Daten der eingebauten Presets und von Just More Parts) | P [W] | M | L_Rotor → L [dB(A), 1 m] | Leerlauf | schwebend | Flug (+15 % Gas) | Vollgas |
|---|---|---|---|---|---|---|---|
| 65-mm-Whoop 1S (eingebaut) | 5 | 0,21 | 68,6 → 68,7 | 10 | **19** | 21 | 25 |
| 75-mm-Whoop 1S (JMP) | 4 | 0,14 | 63,3 → 69,8 | 14 | **22** | 24 | 32 |
| 85-mm-Whoop 2S (JMP) | 9 | 0,20 | 71,3 → 71,5 | 18 | 27 | 30 | 42 |
| Cinewhoop 2,5″ 4S (JMP) | 36 | 0,19 | 76,3 → 75,5 | 26 | 42 | 48 | 64 |
| Toothpick 3″ 4S (JMP) | 28 | 0,22 | 77,6 → 76,7 | 30 | 48 | 55 | 75 |
| Cinewhoop 3″ (eingebaut) | 72 | 0,28 | 84,7 → 76,8 | 30 | **49** | 55 | 72 |
| Cinewhoop 3,5″ 6S (JMP) | 58 | 0,16 | 76,0 → 76,5 | 31 | 47 | 54 | 75 |
| 5″ Race (eingebaut) | 43 | 0,19 | 77,2 → 79,0 | 46 | 63 | 74 | 113 |
| 5″ Freestyle (eingebaut) | 59 | 0,21 | 79,8 → 79,9 | 48 | **70** | 82 | 120 |
| 7″ Long Range (eingebaut) | 81 | 0,22 | 82,1 → 82,3 | 61 | 92 | 104 | 142 |
| X-Class 10″ (JMP) | 162 | 0,22 | 85,0 → 85,1 | 86 | 128 | 147 | 160 |
| X-Class 13″ (JMP) | 296 | 0,23 | 88,0 → 87,6 | 102 | **160** | 160 | 160 |

Hörweiten in Blöcken. Bis zum Release-Kandidaten vom 08.10. galt `L = L_Rotor`: eingebauter 3″-Cinewhoop schwebend 121
(Flug 136, Vollgas 160), 75-mm-Whoop 10, 5″ Race 51, JMP-Cinewhoops 2,5″/3,5″ 47/44. Jetzt liegen alle Cinewhoops bei
42–49 Blöcken, zwischen Whoop und 5″; der 75-mm-Whoop bei 22 (Vorgabe „etwa 15–25“).

## 4. Luftdämpfung

Hohe Töne verlieren in Luft mehr (ISO 9613-1, 20 °C, 70 % rel. Feuchte: 8 kHz 0,117 dB/m, 4 kHz 0,033 dB/m,
Steigung rund `f^1,8`). Die Grenzfrequenz des Tiefpasses ist die Frequenz, die auf der Strecke 3 dB verliert:
`f = 8 kHz · (3 / (0,117 · r))^(1/1,8)`, zwischen 1,8 und 14 kHz – 8 kHz in 26 Blöcken, 3,8 kHz in 100 Blöcken. Vorher
hing die Helligkeit am Anteil der alten Reichweite; ein Whoop hätte mit der neuen, kurzen Hörweite schon in 15 Blöcken
dumpf geklungen.

## 5. Tests

- `SoundReachTest` (JUnit): Pegelformel (+3 dB je doppelte Leistung, +9 dB je doppelte Mach-Zahl), Abstandsgesetz
  (−6,02 dB je Verdopplung, Hörweite = Abstand, an dem die Schwelle erreicht ist), Kurve ohne Sprung und Knick,
  Hörweiten der Presets (65-mm-Whoop 15–25 Blöcke schwebend und höchstens 25 im Flug, 5″ Freestyle 60–80), Vollgas
  höchstens doppelte Hörweite, X-Class-Rotor über doppelt so weit wie der 5″ und bei 160 begrenzt, Luftdämpfung;
  Größenkurve (18 dB je Dekade, Band ±1 dB, halbe Rotor-Abweichung), jedes Preset höchstens 1 dB neben der Kurve,
  3″-Cinewhoop 35–65 Blöcke und zwischen Whoop und 5″ (schwebend und im Flug).
- `SoundReachClassesTest` (JUnit, mit Just-More-Parts-Daten Tag `addon-daten`): Whoops bis 40-mm-Props 15–25 Blöcke
  schwebend und höchstens 25 im Flug, alle Cinewhoops 30–65, alle 5″ 40–85, jede X-Class weiter als jeder 5″ und
  mindestens 100; jede Bauweise höchstens 1 dB neben der Größenkurve, und jede Drohne mit mindestens 1,3-fachem
  Propdurchmesser trägt weiter als die kleinere (oder ist bei 160 begrenzt); ohne Add-on-Daten die Whoop-Varianten des
  Besitzers (16–21 Blöcke).
- `MotorSynthTest`: Pilotenklang bitgleich, draußen kein Fahrtwind, wenig Rumpeln, Motor gleich laut.
- Selbsttest `audio: Hörweite je Drohnengröße` (Fabric und NeoForge, rund 900 Ticks): Drohne (65-mm-Whoop, 3″-Cinewhoop
  35–65 Blöcke, 5″, 7″) schwebt
  senkrecht über dem Spieler (die Server-Verfolgung der Entities ist waagerecht, so bleibt sie bei jeder Höhe beim
  Client), der Spieler ist der zweite Zuhörer. Der Client hält die Drohne scharf, die vier Motoren bei 88/96/104/112 %
  des Schwebegases – gleiche Drehzahlen würden langsam schweben (Schwebung unter 1 Hz beim 7″) und die Pegelmessung um
  mehrere dB verfälschen. Gemessen wird der echte Motor-PCM über `MotorAudioTap`: Pegel von 4 auf 8 Blöcke über je
  24 Ticks (−6 ± 1,5 dB), dann die Hörweite per Halbierung zwischen 8 und 200 Blöcken (hörbar = Live-Sound mit
  mindestens 2 LSB Spitze; die letzten 2–3 % vor der Hörweite liegen darunter), danach bei 1,5-facher Hörweite keine
  Stimme mehr. Läufe vom 08.10. (Fabric und NeoForge gleich bis auf 0,7 Blöcke): Whoop 18,5 Blöcke, 5″ 67,3–68,0,
  7″ 88,3 (Stimme 19,2 / 69,3 / 90,0), 4 → 8 Blöcke −6,1 / −6,0 / −5,9 dB.

## 6. Entscheidungen

- **Hörweite aus Wellenleistung und Mach-Zahl** statt aus Propgröße allein (alt) oder aus der Masse (Regression über
  Sound-Leistung und Gewicht): nutzt die Daten, die das Flugmodell ohnehin hat, gilt für jedes Add-on-Teil und bildet
  Drehzahl und Blattzahl ab.
- **Größenkurve mit schmalem Band** (Verifikation 08.10.): Die reine Physik ließ den eingebauten 3″-Cinewhoop weiter
  tragen als 5″ und 7″ und sprang innerhalb der Cinewhoops (47/121/44 Blöcke); die Owner-Vorgabe verlangt eine glatte
  Kurve nach Größe. Jetzt bestimmt der Propdurchmesser die Hörweite, der Rotor-Pegel verschiebt sie nur um die Hälfte
  und höchstens ±1 dB. Verworfen: ±3 dB um die Kurve (Cinewhoops 47/60/44, weiter ein Sprung) und den Mach-Term deckeln
  (ein schwerer 3″ mit viel Leistung bliebe über dem 5″). Flugmodell und Pilotenklang bleiben unverändert; der
  75-mm-Whoop liegt damit bei 22 statt 10 Blöcken, die frühere Ausnahme „10–15 statt 15–25“ ist hinfällig.
- **Bezug 80 dB(A) / Schwelle 43 dB(A)** so gewählt, dass der 5″ schwebend bei rund 70 Blöcken verklingt; die
  Abstände zwischen den Klassen kommen allein aus der Physik. Verworfen: niedrigere Schwelle für längere Whoop-Weiten
  (der 5″ käme auf 100 Blöcke) und eine Frequenzgewichtung nach der Blattfrequenz (schöbe den 65-mm-Whoop über
  30 Blöcke und machte X-Class leiser als 5″).
- **Gas verlängert die Hörweite nur mit der Wurzel** der Synthesizer-Lautheit. Verworfen: feste Hörweite je Drohne
  (harte Kante beim Beschleunigen) und volle Kopplung (5″ bei Vollgas über 200 Blöcke).
- **Nahbereich 3 Blöcke, danach 1/r**. Verworfen: 1 Block (Drohnen in 10–30 Blöcken leiser als Vanilla-Tiere) und
  Minecrafts lineare Abschwächung (der eigentliche Fehler).
- **Eigene Abschwächung mit `Attenuation.NONE`** statt OpenAL-`LINEAR` mit nachgeführter Distanz: Minecraft setzt die
  Distanz nur beim Start, und eine Kurve nach dem Abstandsgesetz geht mit `LINEAR` nicht.
- **Rumpeln und Fahrtwind nur an Bord, Prop-Wash draußen −12 dB und nur ohne Vorwärtsfahrt**: Beides entsteht am
  Kamera-Mikrofon und im Rahmen; von außen hört man beim Durchsinken der eigenen Abwinde ein leises Flattern, beim
  schnellen Sturzflug nach vorn keins. Verworfen: Rumpeln draußen ganz streichen (Prop-Wash ist von außen hörbar).
- **Luftdämpfung nach absoluter Entfernung** statt nach Anteil der Reichweite.
- **`rangeFactor` aus `MotorTimbre` entfernt** (die Prüfsumme der Klangfarben ist neu; ohne dieses Feld ist sie
  identisch mit der alten, also sind alle übrigen Werte gleich).
- **Kein neuer Crash-Ton für Zuschauer**: Ein Aufschlag lässt sich auf der Zuschauerseite nur aus Positionssprüngen
  raten (Fehlalarme bei Teleport, Reset und Korrekturen); offen für später.
- **Keine Verdeckung durch Blöcke**: Das Audio hat keine; ein eigener Strahl je Drohne und Tick wäre ein eigenes
  Feature.

## 7. Offen

- Crash-Ton für Zuschauer (Aufschlag aus Positionssprüngen ohne Fehlalarme bei Reset, Teleport und Korrekturen).
- Verdeckung durch Blöcke (gedämpfter, dumpferer Motorton hinter Wänden), wie beim Videosignal.
- Hörschwelle nach Umgebung (Regen, Gewitter, Höhlen) statt fester 43 dB(A).
- Die Spieler-Doku auf propwashfpv.com hat keinen Audio-Abschnitt; die Hörweiten stehen im Tooltip der
  Motorlautstärke.
