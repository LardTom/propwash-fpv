# Echte Blackbox-Logs als Referenz für das Flugmodell

Entwicklerwerkzeug, nicht im Mod-Jar. Es liest echte Betaflight-Blackbox-Logs (`.bbl`, `.bfl`), misst daraus, wie sich
echte Drohnen verhalten, fliegt dieselben Manöver mit derselben Motor-Duty im Flugmodell von Propwash nach und schreibt
einen Bericht mit Tabellen, Diagrammen und Vorschlägen für das Flugmodell. Das Werkzeug ändert das Flugmodell nicht.

## Aufruf

```
tools/blackbox_calibration.sh            # alles: fetch, roundtrip, compare
tools/blackbox_calibration.sh fetch      # öffentliche Logs aus tools/bbcal/sources.json laden und dekodieren
tools/blackbox_calibration.sh roundtrip  # Decoder-Prüfung mit Logs aus dem Blackbox-Schreiber von Propwash
tools/blackbox_calibration.sh compare    # Vergleich echt gegen Sim, Bericht nach build/bbcal/report.md
tools/blackbox_calibration.sh scan <ordner>  # neue Logs prüfen, bevor sie in die Quellentabelle kommen
```

Braucht git, make, cc und python3 (nur Standardbibliothek) sowie den Klassenpfad für `tools/fastcheck.sh`. Beim ersten
Lauf klont das Skript `betaflight/blackbox-tools` in den Cache und baut `blackbox_decode`. Teile aus *Just More Parts*
liest es aus `../propwash-justmoreparts` neben dem Repository (anderer Ort: `PROPWASH_BBCAL_ADDON`); fehlen sie,
überspringt der Vergleich die Logs mit Add-on-Teilen und sagt es. Laufzeit: `fetch` rund 3 min beim ersten Mal (danach
aus dem Cache), `compare` rund 20 s.

Ergebnis in `build/bbcal/`: `report.md` (Tabellen, Vorschläge, Diagramme), `results.json` (alle Werte maschinenlesbar,
auch die Mediane je Größe und die Vorschläge), `svg/` (Diagramme), `compare.txt` (Testausgabe).

## Wo die Logs liegen

Fremde Logs kommen nie ins Repository. `fetch` lädt sie in den Cache `.cache/bbcal/` im Hauptverzeichnis des
Repositorys (git-ignoriert, von allen Worktrees geteilt; anderer Ort: `PROPWASH_BBCAL_CACHE`):

- `raw/<Prüfsumme>/` – heruntergeladene Datei, bei ZIP die ausgepackten Logs in `x/`
- `decoded/<id>/log.csv.gz`, `log.headers.csv`, `decode.txt` – Ausgabe von `blackbox_decode` (Rohwerte, GPS
  eingemischt) und seine Statistik
- `src/blackbox-tools/` – der Decoder

`tools/bbcal/sources.json` hält nur die Adressen, die SHA-256-Prüfsumme, die Lognummer in der Datei und den Aufbau der
Drohne. Weicht die Prüfsumme ab, bricht `fetch` für diesen Log ab.

## Ablauf

1. **Dekodieren** mit `blackbox_decode` aus `betaflight/blackbox-tools` (`--unit-vbat raw --unit-amperage raw
   --unit-flags raw --merge-gps --save-headers`). Die Einheiten rechnet das Werkzeug selbst um: Spannung in
   Hundertstel Volt ab Betaflight 4.0, Gyro mit `gyro_scale` in °/s, Beschleunigung mit `acc_1G`, Motor als DShot-Wert
   48–2047 → Duty 0–1, eRPM/100 mit `motor_poles` → Drehzahl, `acc_lpf_hz` ab 500 als Hundertstel Hertz, Flugmodi als
   Box-Bits (ANGLE = 1, HORIZON = 2). Die Umrechnungen von `blackbox_decode` selbst sind für neuere Firmware falsch
   (Gyro in Betaflight 2025.12+, Spannung in 4.2) und werden deshalb nicht benutzt.
2. **Aufbereiten** (`calibration.Flight`): Mittelwerte auf ein festes Raster von höchstens 1 kHz, Lücken bis 3 Werte
   überbrückt. Lage aus dem geloggten Quaternion (ab 2025.12) oder aus Gyro und Acc wie im IMU von blackbox-tools;
   Steigrate aus der Baro-Höhe, Tempo aus GPS (ab 6 Satelliten).
3. **Abschnitte finden** (`calibration.Maneuvers`), jeder verworfene Kandidat mit Grund im Bericht:
   - *Schweben*: 0,8-s-Fenster mit Gyro höchstens 90 °/s, ruhiger Duty, Acc Z 1 ± 0,06 g, weniger als 15° Lage, Baro
     höchstens 0,5 m/s, GPS höchstens 3 m/s, mindestens 1 m über dem tiefsten Baro-Wert und Flugbewegung in ±2 s;
     Ausreißer über 15 % vom Median fallen weg.
   - *Punch*: Duty steigt in 0,12 s um mindestens 0,22 auf mindestens 0,5 und bleibt 0,35 s oben.
   - *Gas weg*: Duty fällt in 0,12 s um mindestens 0,18 auf höchstens 70 % und bleibt mindestens 0,22 s unten.
   - Verworfen werden Stufen am Boden (Acc reagiert nicht auf das Gas), mit Drehung über 400 °/s (Punch) bzw. 250 °/s
     (Gas weg), über 35° Lage, über 4 m/s GPS-Tempo, mit Aufprall oder Lücke.
4. **Drohne aus Teilen bauen** (`sim.RealLogBuilds`): Rahmen, Stack, Motor, Props, Video und Akku aus den eingebauten
   oder Just-More-Parts-Teilen, mit Abweichungen (`set`, etwa KV oder Durchmesser). Die Rahmenmasse wird so gesetzt,
   dass das Abfluggewicht stimmt. Gebaut wird dreimal: Gewicht, Gewicht − Unsicherheit, Gewicht + Unsicherheit.
5. **Nachfliegen** (`sim.RealLogSim`): Die Sim hält die Drohne waagerecht (ideale Lage-Regelung), schwebt 1 s mit der
   Steigrate vom Baro, dann bekommt sie ab 0,4 s vor der Stufe die echte Duty. Vor der Stufe wird die Duty auf das
   Schweben der Sim umgerechnet (gleicher Zustand vor dem Manöver), über die Stufe hinweg geht sie auf die echte Duty
   über; danach ist sie absolut gleich. Akku: Ladezustand so, dass die Spannung unter Last passt (mindestens 35 %),
   Unterschiede gleicht die Duty aus. Acc Z der Sim läuft durch denselben PT2-Tiefpass wie im Log.
6. **Messen** (`calibration.Measure`), für echt und Sim mit demselben Code:
   - Schwebe-Duty (auf 1 g umgerechnet), Schwebe-Drehzahl; Sim: Gleichgewicht bei derselben Spannung.
   - *Schub echt/Sim*: Acc Z in ruhigen Abschnitten (0,7–1,4 g, Baro ±1 m/s) geteilt durch den statischen Schub der Sim
     bei gleicher Duty und Spannung; *Drehzahl echt/Sim* ebenso.
   - *Steig- und Sinkflug*: dasselbe bei Baro-Steigraten ab 1,5 m/s mit Anströmung in der Sim, geteilt durch den Wert
     beim Schweben – unabhängig von Gewicht und Prop-Beiwerten, prüft nur die Anströmung.
   - *Gas weg*: Acc Z 0,12–0,30 s nach der Stufe → Fallbeschleunigung g·(1 − Acc Z); Endgeschwindigkeit aus dem
     Anstieg von Acc Z mit der aufintegrierten Fallgeschwindigkeit (nur ruhige Stürze ab 0,8 s), dazu die exakte
     Endgeschwindigkeit der Sim bei Leerlauf-Duty.
   - *Punch*: 90-%-Wert von Acc Z 0,05–0,5 s nach der Stufe, Zeit bis 90 %, Spannungseinbruch.
   - *Motorverzögerung*: t63 der Drehzahl ab der Mitte der Duty-Stufe, nur bei Stufen unter 60 ms.
   - *Rates*: Sollwert aus dem Log gegen `RateCurves` von Propwash mit den Rates aus dem Kopf (nur Acro-Abschnitte).

## Prüfung

- `CalibrationLogicTest` (JUnit): Kopf und Einheiten, CSV mit Namen wie `eRPM(/100)[0]`, Umrechnung, Lage, Erkennung
  und Messung an synthetischen Flügen, Boden- und Drehungs-Ausschluss, Endgeschwindigkeits-Fit.
- `RealLogSimTest`: Gewicht aus Teilen; die Sim fliegt einen eigenen Flug (Whoop und 5″) als „echten“ Log nach und
  misst dasselbe (Punch ±3 %, Motor-t63 ±3 ms, Gas weg ±0,03 g, Schwebe-Duty ±3 %); Quellentabelle vollständig.
- `CalibrationRoundTripTest`: Propwash schreibt mit seinem Blackbox-Schreiber einen Flug mit Punch und Gas weg, der
  Java-Decoder und `blackbox_decode` lesen ihn: Duty höchstens 0,4 DShot-Stufen, Drehzahl höchstens 7 1/min, Spannung
  0,005 V vom Flugmodell, beide Decoder gleich, Motor-t63 gleich. Mit `PROPWASH_BBCAL_GAME_LOGS` auch Logs aus dem Spiel.
- Stand 08.10.2026: alle Tests grün; Rundreise Whoop und 5″ wie oben; die Logs aus den gefilterten Selbsttests
  (`blackbox:`, Fabric und NeoForge je 7 Schritte) dekodieren sauber.

## Quellen (Stand 08.10.2026)

Gesucht wurde in den Issues und Pull Requests von `betaflight/betaflight`, `betaflight/blackbox-log-viewer`,
`bird-sanctuary/bluejay`, PIDtoolbox und weiteren Repositories (rund 880 Anhänge, 994 Logs). 740 Logs hatten Acc, Motoren
und Spannung, 136 genug Schweben, 72 einen Punch, 56 ein Gas weg. Übernommen wurden Logs, deren Drohne sich aus dem
Text, dem Craft-Namen oder Herstellerangaben bestimmen lässt.

| id | Größe | Drohne, Aufbau | Gewicht | Bau bekannt | Quelle | verwertbar |
|---|---|---|---|---|---|---|
| meteor75_erpm_fraction, meteor75_debug1 | Whoop 75 mm 1S | BetaFPV Meteor75 (2022), 0802SE 19500 KV, 40 mm 2-Blatt, 1S 450 mAh | 37 ± 4 g | mittel (Version) | [log-viewer #625](https://github.com/betaflight/blackbox-log-viewer/pull/625) | Schweben, Punch, Gas weg; kein eRPM |
| meteor75_bumps | Whoop 75 mm 1S | wie oben | 37 ± 4 g | mittel | [betaflight #12937](https://github.com/betaflight/betaflight/pull/12937) | 8 s Schweben, eRPM, Gas weg |
| meteor75pro_bf442, meteor75pro_bf432 | Whoop 75 mm 1S | BetaFPV Meteor75 Pro, 1102 22000 KV, 45 mm 3-Blatt, 1S 550 mAh | 45 ± 3 g | hoch | [betaflight #12961](https://github.com/betaflight/betaflight/issues/12961) | Punch, Gas weg, Rates |
| meteor65 | Whoop 65 mm 1S | BetaFPV Meteor65 (2022), 0702SE II 27000 KV | 26 ± 3 g | mittel | [log-viewer #625](https://github.com/betaflight/blackbox-log-viewer/pull/625) | **verworfen**: kein Acc, kein eRPM |
| pavo_pico_timing, pavo_pico_all | 2″-Cinewhoop 2S | BetaFPV Pavo Pico (Walksnail), 1102 14000 KV, 45 mm 3-Blatt, 2S 450 mAh | 77 ± 8 g | mittel | [betaflight #13388](https://github.com/betaflight/betaflight/pull/13388) | Schweben, eRPM, Steig-/Sinkflug, Gas weg |
| pavo20pro2_b0, pavo20pro2_fullthrottle | 2″-Cinewhoop 3S | BetaFPV Pavo20 Pro II, LAVA 1104 7200 KV, Gemfan 2218, 3S 550 mAh | 140 ± 15 g | mittel (Video) | [betaflight #15400](https://github.com/betaflight/betaflight/pull/15400) | Schweben, eRPM, Rates |
| firefly25_evening, firefly25_long | 2,5″ 3S | Flywoo Firefly 25 Mini mit GPS, ROBO 1303 6000 KV, Gemfan 2520-3 | 183 ± 20 g | mittel (Akku) | [betaflight #15809](https://github.com/betaflight/betaflight/issues/15809) | Schweben, eRPM, Gas weg, Motor-t63, Rates |
| geprc_cinewhoop3 | 3″-Cinewhoop 4S | GEPRC, 1404 3850 KV | 330 ± 50 g | niedrig | [betaflight #14240](https://github.com/betaflight/betaflight/issues/14240) | Schweben, eRPM |
| nazgul_evoque_f5_a, _b | 5″ 6S | iFlight Nazgul Evoque F5 V2 (BNF) mit GPS, XING-E Pro 2207 1800 KV | 630 ± 40 g | hoch | [betaflight #12756](https://github.com/betaflight/betaflight/issues/12756) | Steig-/Sinkflug, Gas weg, Endgeschwindigkeit; kein eRPM |
| fifty5_6s | 5″ 6S | „FIFTY5-2“, Motoren nicht genannt | 640 ± 80 g | niedrig | [betaflight #15816](https://github.com/betaflight/betaflight/issues/15816) | Schub, Drehzahl, Rates |
| lr5_lion_a, _b | 5″ LR 4S Li-Ion | Emax RS2205, 4S 21700 4000 mAh, O4 Pro | 620 ± 80 g | niedrig | [betaflight #15651](https://github.com/betaflight/betaflight/issues/15651) | Punch, Gas weg; viel GPS-Rettung, Werte unsicher |
| rekon7 | 7″ 6S | REKON-7-Rahmen, SpeedyBee F405 V4, O4 | 820 ± 130 g | niedrig | [betaflight #14590](https://github.com/betaflight/betaflight/issues/14590) | Schweben, eRPM, Motor-t63, Rates |
| micoair7 | 7″ 6S | „MicoAir_7inch“, 14 min Flug | 820 ± 130 g | niedrig | [log-viewer #922](https://github.com/betaflight/blackbox-log-viewer/issues/922) | Schweben, eRPM |

Gewichte mit „hoch“ stammen aus Herstellerangaben, „mittel“ hat eine offene Größe (Version, Akku, Videosystem),
„niedrig“ ist aus typischen Bauten geschätzt. Die Mediane je Größe nehmen „niedrig“ nur, wenn es nichts anderes gibt
(dann als „nur geschätzte Bauten“ markiert). Die Unsicherheit geht als Sim-Spanne (Gewicht ±) in jede Tabelle.

## Ergebnis (Stand 08.10.2026, Flugmodell 0.5.0 mit Steigflug-Schub nach Impulstheorie)

Mediane je Größe, echt / Sim. Werte in Klammern stammen nur aus geschätzten Bauten. Steigflug und Sinkflug sind
echt/Sim relativ zum Schweben (1 = gleich, über 1 = die Sim hat weniger Schub). Gerechnet mit den Teiledaten von
Just More Parts 1.1.0 (`fix/physics-floaty`).

| Größe | Schwebe-Duty | Schwebe-Drehzahl | Schub echt/Sim | Drehzahl echt/Sim | Steigflug 1,5–3,5 / 3,5–8 m/s | Sinkflug 1,5–3,5 m/s | Fall nach Gas weg [m/s²] | Punch [g] | Motor runter t63 [ms] |
|---|---|---|---|---|---|---|---|---|---|
| Whoop 75 mm 1S (5) | 44,5 / 42,6 % | 24 542 / 25 742 | 0,94 | 0,90 | – | – | 6,7 / 7,2 | 2,24 / 2,66 | – |
| 2″-Cinewhoop (4) | 39,4 / 40,8 % | 23 354 / 36 282 | 1,42 | 0,66 | 1,12 / 1,58 | 1,00 | 7,0 / 6,7 | – | – |
| 2,5″ 3S (2) | 30,5 / 45,7 % | 19 406 / 25 459 | 2,10 | 1,10 | 0,99 / 1,32 | 0,99 | 10,1 / 10,7 | – | 21 / 34 |
| (3″-Cinewhoop) (1) | 30,8 / 47,8 % | 14 151 / 26 245 | 2,34 | 0,82 | 1,09 / – | 0,99 | – | – | – |
| 5″ 6S (2) | 26,3 / 28,1 % | – / 11 846 | 1,16 | – | 0,97 / 1,00 | 0,98 | 9,3 / 9,9 | – | – |
| (5″ LR Li-Ion) (2) | 23,6 / 35,5 % | – | 1,88 | – | – | – | −1,2 / 4,6 | 1,52 / 2,52 | – |
| (7″ LR) (2) | 29,9 / 28,3 % | 7 560 / 6 932 | 1,02 | 1,12 | 1,12 / 1,27 | 0,98 | – | – | – |

Vor der Änderung am Steigflug-Schub (gleiches Werkzeug, gleiche Teiledaten) lag der Steigflug bei 1,04–1,36 (1,5–3,5 m/s)
bzw. 1,08–1,75 (3,5–8 m/s); alles andere war gleich. Zum Vergleich die Motorverzögerung im Flugmodell aus der
Rundreise: Whoop hoch 64 ms / runter 45 ms, 5″ 33 / 16 ms. Rates: Wo die Logs Acro-Abschnitte haben, folgt der echte
Sollwert der ACTUAL-Kurve von Propwash auf 0,3–0,7 °/s (Median, sechs Logs); zwei Logs mit 43–57 °/s fliegen mit
Höhen-Halten bzw. wechseln das Rate-Profil.

### Vorschläge und was daraus wurde

Ausführlich mit Herleitung in `docs/design/realism/flight-feel-gravity.md` §11.

1. **Steigflug – umgesetzt.** Die Sim verlor beim Steigen zu viel Schub (`1 − λ`). Jetzt rechnet sie mit Impuls- und
   Blattelementtheorie: Die induzierte Geschwindigkeit sinkt beim Steigen mit, Anfangssteigung `−r/(2r − 1)` statt
   `−1`. Im Vorwärtsflug blendet der Effekt nach Glauert aus. Der 5″ (Bau bekannt) liegt jetzt bei 0,97–1,00.
2. **Props 45–76 mm zu schwach – Teiledaten, nicht in 0.5.0.** Pavo Pico 45 mm, Pavo20 Pro II 56 mm, Firefly 65 mm
   und GEPRC 76 mm:
   - Die Sim dreht beim Schweben 30–85 % höher als echt (Pico 36 300 statt 23 400 1/min).
   - Ab 56 mm braucht sie 27–55 % mehr Duty.
   - `c_t` dieser Props müsste etwa × 1,7–2,5. Für den 45-mm-Dreiblatt ergeben die Pavo-Pico-Logs ≈ 0,216; der auf
     den Dreiblatt skalierte Prüfstand des 1811 (`WhoopThrustTableTest`) ≈ 0,22; die Daten sagen 0,0894.
   - `c_p` lässt sich aus den Logs nicht sicher bestimmen (hängt an KV, Widerstand und ESC).
   - Die Korrektur verschiebt Strom, Motorwärme und Flugzeit-Kalibrierung. Vorschlag für 0.5.1 bzw. Just More
     Parts 1.2 mit Prüfstandsdaten. Kein größenabhängiger Faktor im Modell.
3. **5″ – Teiledaten.** Schub bei gleicher Duty echt 1,16-mal so hoch (Schwebe-Duty 26,3 statt 28,1 %). Das liegt im
   Band der Prop-Daten; `c_t` der 5″-Props wäre rund × 1,15, nicht geändert.
4. **Whoop 75 mm – Teiledaten.** Schwebe-Drehzahl 5 % zu hoch, Punch 19 % zu stark (2,66 statt 2,24 g). Ursache: `c_p`
   des 40-mm-Props laut Werkzeug × 1,24 und der Spannungseinbruch des 1S-Antriebs. Nicht geändert.
5. **Sinkflug – kein Handlungsbedarf mehr.** Der Vorschlag `DESCENT_GAIN` 0,5 → 0,1 kam aus der ersten Fassung des
   Werkzeugs, die ohne Prop-Wash nachflog. Mit der Physik des Spiels liegt der Sinkflug bei 0,98–1,00.
6. **Motor abbremsen – Teiledaten.** 2,5″ echt 21 ms, Sim 34 ms bis 63 %. Die Sim bremst schon aktiv (negativer
   Phasenstrom wie „damped light“); der Unterschied kommt aus Prop-Last und Rotorträgheit.

Nach jeder Änderung am Flugmodell einfach `tools/blackbox_calibration.sh compare` erneut laufen lassen.

### Grenzen

- Gewichte sind bei den meisten Logs geschätzt; Schwebe-Drehzahl und Schub echt/Sim hängen direkt daran
  (Spanne im Bericht). Steig-/Sinkflug relativ, Rates und Fallbeschleunigung sind gewichtsunabhängig.
- `motor_poles` steht so im Log, wie der Pilot es eingestellt hat; falsche Polzahl verfälscht die Drehzahl.
- Die Duty ist der DShot-Befehl; ESC-Eigenheiten (Bluejay/AM32-Kurven, Startleistung) sind nicht im Modell.
- Whoop-Logs sind kurz und wild, Logs ohne Baro (Whoops) kennen die Steigrate vor dem Manöver nicht (angenommen 0).
- Die Sim hält die Lage ideal; Manöver mit Flips werden verworfen.
- Logs ohne Acc (Meteor65) oder ohne eRPM liefern nur einen Teil der Werte.

## Neue Logs hinzufügen

1. Logs in einen Ordner legen und `tools/blackbox_calibration.sh scan <ordner>` aufrufen. `build/bbcal/scan/scan.tsv`
   zeigt je Log Firmware, Craft-Name, Acc, eRPM, Schwebe-Sekunden, Punches, Gas weg und die Gründe fürs Verwerfen.
2. Brauchbare Logs öffentlich ablegen (z. B. als Anhang eines Issues) und in `tools/bbcal/sources.json` eintragen:
   `id`, `class` (`whoop65`, `whoop75`, `cinewhoop2`, `toothpick25`, `cinewhoop3`, `freestyle5`, `longrange5`,
   `longrange7`), `label`, `page`, `url`, `member` (Datei im ZIP), `index` (Log in der Datei), `sha256`, `build` mit
   `text`, `auw_g`, `auw_tolerance_g`, `auw_source`, `confidence` (`hoch`, `mittel`, `niedrig`) und `parts`.
3. `parts`: je Teil eine Kennung (`propwash:p51_3b`, `justmoreparts:m1404_3800`) oder `{"base": "<Kennung>", "set":
   {…}}`, um Werte zu überschreiben (`kv`, `stator`, `mass_g`, `diameter_mm`, `pitch_in`, `cells`, `capacity_mah`, …).
   Die Rahmenmasse setzt das Werkzeug selbst so, dass `auw_g` stimmt.
4. `tools/blackbox_calibration.sh fetch` und `compare`; `RealLogSimTest` prüft die Tabelle.

## Für Piloten: einen passenden Log aufnehmen

Wer eine echte Drohne hat, kann mit einem kurzen Flug die beste Referenz liefern:

- Betaflight 4.4 oder neuer, Blackbox an, **Lograte 1 kHz oder höher**, Felder **Acc**, **Motoren**, **Spannung** und –
  wenn bidirektionales DShot läuft – **RPM/eRPM** an; `motor_poles` passend zu den Motoren einstellen.
- Draußen bei wenig Wind, im **Acro-Modus**, Akku voll:
  1. 10 s ruhig auf 2–3 m Höhe schweben,
  2. aus dem Schweben dreimal kurz **Vollgas** (0,5 s), dazwischen wieder schweben,
  3. aus dem Schweben auf sicherer Höhe (ab 15 m) dreimal **Gas ganz weg** für 1 s ohne Drehung, dann abfangen,
  4. langsam senkrecht steigen und sinken (je 3–5 s).
- Die Drohne mit Akku **wiegen** und Motoren, KV, Props, Akku und Rahmen notieren – ohne Gewicht ist der Log nur halb so
  viel wert.
- Log und Angaben als Issue anhängen; danach wie oben eintragen.

## Eingebauter Import „echten Log laden und die Sim-Drohne abstimmen“?

Entscheidung: **später sinnvoll, aber nur als Vergleich, nicht als automatische Abstimmung.** Ein Spieler-Log liefert
Schwebe-Duty, Drehzahl, Punch und Fall seiner Drohne; daraus ließen sich im Drohnen-Editor Gewicht, KV und
Prop-Beiwerte vorschlagen. Dafür braucht das Spiel aber einen eigenen Blackbox-Decoder im Mod (heute nur in den Tests),
Schutz gegen beliebige Dateien und eine Erklärung, warum Gewicht und Polzahl stimmen müssen – sonst „korrigiert“ der
Import das Modell mit falschen Angaben. Ein erster Schritt ohne Risiko wäre eine Ansicht „deine Drohne gegen echt“ im
Blackbox-Tab für Logs, die der Spieler selbst gewogen hat. Verworfen: automatische Änderung der Teile oder des
Flugmodells aus einem einzelnen Log (zu viele unbekannte Größen pro Log, siehe Grenzen).

## Entscheidungen

- **blackbox_decode als Decoder** statt eines eigenen Parsers: Referenz von Betaflight, robust bei kaputten Frames und
  allen Kodierungen; geprüft mit der Rundreise und gleich dem Java-Test-Decoder. Eigene Umrechnung der Einheiten, weil
  die von `blackbox_decode` bei neuer Firmware falsch sind.
- **Auswertung in Java im Test-Quellset** statt in Python: dasselbe Flugmodell im selben Prozess, JUnit für die Logik,
  schnell genug für 100-MB-Logs; Python nur zum Laden (Standardbibliothek, `-I`).
- **Duty statt Gas-Knüppel**: Der Motorbefehl im Log enthält schon Airmode, Spannungsausgleich und Leerlauf; derselbe
  Befehl in der Sim vergleicht Antrieb und Aerodynamik direkt.
- **Ideale Lage im Nachflug** statt des PID-Reglers der Sim: misst nur die senkrechte Physik, ohne Tuning-Unterschiede.
- **Vor der Stufe auf das Sim-Schweben umgerechnet**: sonst würde eine schwächere Sim schon vor dem Manöver sinken und
  Fall und Punch verfälschen.
- **Gewichtsunsicherheit als dritte und vierte Sim** statt nur einer Zahl; Mediane ohne geschätzte Bauten, wo möglich.
- **Cache im Hauptverzeichnis** (`.cache/bbcal`), geteilt von allen Worktrees, statt `build/` (würde `gradle clean`
  löschen).
