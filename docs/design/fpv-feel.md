# Fluggefühl ohne Physik: Latenz, Kamera, Vibration (0.5.0)

Viele Spieler beschreiben die Drohnen als „zu floaty“. Dieses Dokument behandelt alles, was das **Gefühl** formt, ohne
das Flugmodell anzufassen (Schub, Widerstand, Masse, Motoren, Schwerkraft bleiben bitgleich; Motion-Goldens und
Kalibrierung unverändert). Zuerst gemessen, dann nur geändert, was messbar unrealistisch war.

## 1. Latenzkette Knüppel → Bild

### 1.1 Ablauf je Bild (Minecraft 26.3)

| Schritt | Wo | Zeitpunkt |
|---|---|---|
| Knüppel lesen | `RenderSystem.pollEvents` → `TransmitterInput.onFrame` (SDL3, `SDL_UpdateJoysticks`, Achsen lesen) | Anfang der Hauptschleife |
| Client-Tick | `Minecraft.runTick` (nur in Bildern mit fälligem Tick, 20 Hz) | danach |
| Sim | `PilotSession.onFrame` an `FRAME_START` (vor `GameRenderer.update`): Frame-dt in festen 2000-Hz-Substeps, Knüppel linear über die Substeps des Bilds | `now` |
| Pose speichern | `VideoState.record(now)` – die Sim rechnet die sichtbare Pose über den Rest unter einem Substep (< 0,5 ms) linear hoch (`DroneSim.finishFrame`), die Pose gehört damit zur Zeit des Bilds (§3.1) | |
| Kamera | `Camera.update` → `FpvView.cameraPose`: Pose aus dem Puffer zur Zeit `now − angewandte Videolatenz`, linear/slerp zwischen zwei Bildern, keine Glättung | |
| Zeichnen, Ausgabe | `GameRenderer.render`, `GpuSurface.present` (gemessen über `mixin.video.client.MinecraftMixin`) | |
| Monitor | Bildaufbau (im Mittel eine halbe Bildwiederholzeit bis zur Bildmitte), danach Elektronik und Panel des Monitors | |

Die Knüppel-Rampe über die Substeps ist im Mittel unverzerrt: eine Knüppelbewegung irgendwo im letzten Bildintervall
hat ihren Erwartungswert in dessen Mitte, die Rampe auch. Die Eingabe ist bei der Sim nur so alt wie der Weg von
`pollEvents` bis `FRAME_START` (Spalte „Eingabe“ unten).

Die RC-Glättung (`FilterSettings.rcSmoothingHz`, PT2 40 Hz wie Betaflights Setpoint-Glättung, Gruppenlaufzeit ≈ 5 ms)
gehört zum Flugregler-Modell und bleibt.

### 1.2 Videolatenz genau einmal

`latency_ms` eines Videomoduls ist die **Glas-zu-Glas-Latenz** des echten Systems (Licht auf dem Kamerasensor bis Licht
im Brillen-Display; Quellen in `docs/design/realism/power-video.md` §4.1–4.2: analog 12 ms, digital Lite 26 ms,
digital HD 30 ms). Darin steckt schon der Bildaufbau des Brillen-Displays.

Bis 0.4.0 legte das Spiel diese Latenz **zusätzlich** auf seinen eigenen Darstellungsweg (Zeichnen, Ausgabe,
Bildaufbau des Monitors). Der Pilot sah damit die Systemlatenz zweimal: einmal künstlich, einmal durch den eigenen
Bildschirm. Seit 0.5.0 zieht `video.DisplayLatency` den **gemessenen** Teil des Darstellungswegs ab:

- `pipeline` = Zeit von der Kamerapose (`sampleDisplayed`, Zeitbasis = `now` des Bilds) bis nach `present`,
  geglättet (EMA, τ = 1 s, einzelne Werte auf 50 ms gekappt);
- `scanout` = halbe Bildwiederholzeit des Monitors (`Window.findBestMonitor().currentMode().getRefreshRate()`,
  einmal je Sekunde, Standard 60 Hz);
- angewandt = `max(0, Videolatenz − pipeline − scanout)`.

Nicht abgezogen wird, was das Spiel nicht messen kann (GPU-Arbeit nach `present`, Compositor des Betriebssystems,
Eingangsverzögerung des Monitors). Die Gesamtlatenz im Spiel ist damit **etwa gleich** der des echten Systems, im
Mittel ±1 ms: Der Ausgleich folgt dem gleitenden Mittel der letzten Sekunde, einzelne Bilder und auch der Mittelwert
eines Laufs können knapp darunter liegen (Selbsttest Fabric: System 12 ms → 11,2 ms, 26 ms → 25,2 ms, 30 ms → 29,2 ms;
NeoForge 12,2 bzw. 30,1 ms). Fürs Fluggefühl ist das ohne Bedeutung; Ziel war, die Latenz nicht doppelt zu zählen. Digitale Aussetzer und Standbilder laufen unverändert über die Bild-Uhr des
`FeedModel` und werden nur um den Ausgleich verschoben. Video-Tab, Statuszeile und Rennwertung zeigen und vergleichen
weiter die Latenz des Systems (`VideoState.latencyNanos`), nicht den angewandten Rest.

### 1.3 Gemessen

Selbsttest `kamera: Latenzkette …` (Fabric, Freestyle mit HD-Modul im Schwebeflug, 478 Bilder in 4 s, Mac mit
120-Hz-Display, ≈ 120 Bilder/s; fünf weitere Läufe auf Fabric und NeoForge lagen bei Gesamt vorher 41,4–42,2 ms und
nachher 29,1–30,0 ms). Mittel / Maximum je Bild in ms:

| Glied | Mittel | Max | Anmerkung |
|---|---:|---:|---|
| Eingabe (`pollEvents` → Sim) | 0,27 | 1,57 | vernachlässigbar, kein Umbau nötig |
| Sim-Rest (noch nicht gerechnete Zeit) | 0,27 | 0,50 | so alt ist die Knüppelstellung in der Pose; die Bewegung selbst rechnet die Sim bis zur Bildzeit hoch (§3.1) |
| Videosystem (HD) | 30,00 | 30,00 | Glas-zu-Glas des Moduls |
| Darstellungsweg bis `present` | 6,83 | 14,46 | gemessen |
| Bildaufbau (halbe Bildwiederholzeit) | 4,17 | 4,17 | 120 Hz |
| angewandte Videoverzögerung | 18,71 | 19,14 | 30 − 11,3 |
| **Gesamt vorher** | **41,5** | 49,0 | Videolatenz zusätzlich zum Darstellungsweg |
| **Gesamt nachher** | **30,0** | 37,8 | = Glas-zu-Glas des Systems |

Je Videomodul (gleicher Lauf, Ausgleich 11,3 ms; bei 60 Hz und 60 Bildern/s wird er größer – geschätzt 8,3 ms Bildaufbau plus bis zu einer Bildzeit Pipeline, also
etwa 15–25 ms; der Ausgleich misst das je Rechner selbst):

| Videomodul | Glas-zu-Glas | vorher im Spiel | nachher im Spiel | künstliche Verzögerung nachher |
|---|---:|---:|---:|---:|
| analog (Nano, Race) | 12 ms | 23,5 ms | 12,0 ms | 0,7 ms |
| digital Lite | 26 ms | 37,5 ms | 26,0 ms | 14,7 ms |
| digital HD | 30 ms | 41,5 ms | 30,0 ms | 18,7 ms |

Nicht enthalten (in beiden Spalten gleich): RC-Glättung und Motor-Hochlauf (Flugmodell), USB-Abfrage des
Controllers, GPU nach `present`, Compositor und Monitor-Elektronik.

## 2. Kamera

- **Pose:** keine Glättung, kein Nachziehen; Roll kommt direkt aus der Orientierung (fest montierte Kamera, kein
  Horizont-Ausgleich – wie echt). Der OSD-Horizont rechnet aus der Lage des Flugreglers und zittert nicht mit.
- **Neu:** Treffer-Neigung (`bobHurt`) und Laufwackeln (`bobView`) des Spielers wirken nicht mehr auf das FPV-Bild
  (`mixin.drone.client.CameraMixin#propwash$steadyFpvImage` setzt im `CameraRenderState` des Bilds `hurtTime = −1`
  und `bob = 0`). Vorher kippte das Drohnenbild um bis zu 14°, wenn der Pilot getroffen wurde (Selbsttest
  `kamera: Treffer und Laufwackeln des Piloten kippen das FPV-Bild nicht`).
- **Kamerawinkel je Rahmen** (`camera.tilt_deg`): Whoop 12°, Cinewhoop 10°, Freestyle 5″ 28°, Race 5″ 38°,
  Long Range 7″ 20° – im üblichen Bereich echter Builds (Whoop 0–20°, Cinewhoop 0–15°, Freestyle 20–35°, Race 30–50°,
  Long Range 10–25°). Unverändert.
- **Sichtfeld je Videomodul** (horizontal, aus Herstellerangaben, `realism/power-video.md` §4.4): analog 125°,
  digital Lite 104°, digital HD 130°. Unverändert. Propwash zeichnet geradlinig (Minecraft-Projektion); echte
  FPV-Optiken sind Fischaugen mit Tonnenverzeichnung. Bei gleichem horizontalem Sichtfeld erscheint die Bildmitte
  geradlinig um den Faktor `tan(H/2)/(H/2)` kleiner als bei einer f-θ-Optik: 104° → 1,41, 125° → 1,76, 130° → 1,89
  (stereografisch 1,3–1,6). Dinge vor der Drohne wirken dadurch weiter weg und langsamer – ein offener Hebel für das
  Geschwindigkeitsgefühl (§6).

## 3. Kamera-Vibration (neu, nur im Bild)

Echtes FPV-Video steht nie ganz still. `video.CameraShake` (reines Java) erzeugt drei physikalisch begründete Anteile
als kleine Drehungen der Kamera (Nicken, Gieren × 0,4, Rollen), `drone.client.FpvShake` speist sie aus der Telemetrie
der Sim und hängt sie in `FpvView.cameraPose` hinter Pose und Uptilt:

| Anteil | Treiber | Größe bei 100 % | Frequenz im Bild |
|---|---|---|---|
| Motorzittern | mittlere Drehzahl / Schwebedrehzahl `r`: gesunde Props `0,035° · min(1, r²)` – über der Schwebedrehzahl konstant; dazu beschädigte Teile (`WearShake`: Prop, Motor, Arm unter 90 % Zustand, Pegel `w` ab der Schadensgrenze von null an, `v = min(4, w · r²)`) `min(0,2°, 0,035° · r²) · 0,75 · v` | Schweben und Vollgas 0,035°, auch mit leicht abgenutzten Teilen (ab 90 %); mit verbogenem Prop bei Vollgas bis ≈ 0,6° | 38 Hz + 70 Hz (Rauschen) |
| Buffet | Prop-Wash (`max(propWash, propWashVrs)` der Sim) · 0,35° und Rezirkulation · 0,18°, beides × Strömung `min(r, 1,5)`; Rezirkulation = Bodeneffekt (`groundEffectFactor` 1,0–1,3 → 0–1) oder Enge (feste Blöcke in 2,5 bzw. 5 Radständen links, rechts, vorn, hinten, oben; alle 4 Bilder), mal Abklingen mit der Fahrt (`CameraShake.recirculationFade`) | bis ≈ 0,5°; Rezirkulation bei Fahrt gegen 0 | 9 Hz |
| Gasstoß | Anstieg von `r` über 4 je Sekunde, aufsummiert mit τ = 0,1 s; voller Stoß ab 0,8 aufsummiertem Anstieg, Hüllkurve klingt mit τ = 0,18 s ab | bis 0,25° (5″ aus dem Schweben auf Vollgas), Whoop ≈ 0,05° | 17 Hz |

- Anstieg τ = 0,03 s, Abklingen τ = 0,2 s; nur scharf, Motoren drehend und nicht am Boden; Summe je Achse auf
  1,2° × Stärke begrenzt.
- **Zeitbezug:** die Stärken werden mit dem Zeitstempel des Sim-Zustands gespeichert und zur **angezeigten** Zeit
  gelesen (gleiche Verzögerung wie das Bild); das Rauschen ist eine stetige Funktion der Zeit (Wertrauschen mit
  Hermite-Glättung) und damit von der Bildrate unabhängig.
- **Nie in der Physik:** nur die Kamera-Drehung im Bild ändert sich. Posen-Puffer, Körper im FPV, Sim, OSD,
  Rennwertung, Trick-Erkennung und Flashback-Kamerapfad sehen keine Vibration.
- **Action-Cam:** sie nimmt das Kamerabild auf. Stabilisierung „Glätten“ und „Glätten + Horizont halten“ (Heavy-Cam) glätten die Drehung und
  nehmen das Zittern weitgehend heraus (wie echte Stabilisierung); die Light-Cam (roh) behält es.
- **Zuschauer** sehen das Bild ohne Vibration (ihr Client rechnet keine Sim der fremden Drohne).
- **Kameraschlag beim Durchbruch durch Glas** (`video.ImpactJolt`, seit 09.10.): ein vierter Anteil neben Zittern,
  Buffet und Gasstoß, ausgelöst vom Durchbruch der Sim (`DroneSim#breakCount`): gedämpfte Schwingung mit 0,45° je
  verlorenem m/s (bis 3°), Nicken 13 Hz, Rollen × 0,6, Gieren × 0,4, τ = 0,11 s. Gleicher Zeitbezug (erscheint im
  Bild des Durchbruchs), gleiche Einstellung, ebenfalls nie in der Physik. Einzelheiten:
  `docs/design/drone-break-blocks.md` §3.2.
- **Einstellung:** Tab „Video“, „Kamera-Vibration“ 0–200 % in 10er-Schritten, Standard 100 %, 0 % = aus
  (`VideoConfig.cameraShake`, persönlich).
- **Selbsttests:** der Basis-Schritt „dev: Kamera-Vibration aus“ hält alle übrigen Schritte frei von Zittern
  (Bildschirmfotos, Winkelprüfungen) über einen Test-Schalter in `FpvShake`, die Einstellung des Spielers bleibt dabei
  unverändert (der Video-Tab zeigt im Bildschirmfoto den Standard 100 %); die Schritte `kamera:` schalten sie gezielt
  ein: 0 % weicht die Kamera um 0,00000° vom Rahmen ab, bei 100 % (Fabric/NeoForge) RMS im Schweben 0,018–0,019°, beim
  Gasstoß 0,08–0,17°, im Sinkflug von 12 m mit Abfangen (bis 7,8 m/s, Prop-Wash der Sim bis 1,00) 0,12–0,13° mit
  Buffet bis 0,33°; neben einer Säule meldet die Enge-Prüfung 0,50, im Freien 0. Der Schritt
  `kamera: bei hohem Tempo bleibt das Bild ruhig` misst je Bild im Schweben und im Vollgas-Steigflug (§3.1).
- **Bildfolge** (`bildschirmfotos: Kamera-Vibration aus und an`, nur zur Durchsicht unter
  `docs/screenshots/camera_shake/`, von git ignoriert): je drei Bilder aus und an im Schweben vor einer Schachbrettwand,
  zwei beim Gasstoß. Abweichung der Kamera vom Rahmen je Bild: aus 0,0000°, an 0,011–0,039° (≈ 0,1–0,3 px bei 1080p),
  Gasstoß 0,11–0,22° (≈ 1–2 px). Im Schweben und bei Tempo ist das Zittern bewusst kaum sichtbar, erst ein harter
  Gasstoß, Prop-Wash und Enge im langsamen Flug machen es deutlich – wie bei einem sauber gebauten echten Copter.

### 3.1 Hohes Tempo: das Bild bleibt ruhig (09.10.)

Im Test der 0.5.0 wirkte das FPV-Bild bei hohem Tempo unruhig. Ein sauber gebauter echter Copter fliegt bei Tempo
ruhig; gemessen wurde deshalb an drei Stellen, mit allen fünf Klassen.

**1. Flugmodell (Vibration aus).** JUnit `sim.HighSpeedSmoothnessTest`: Vollgas mit 70° Neigung, 60 % Gas mit 60°,
Sturzflug mit 130° und halbem Gas, dazu eine Vorab-Reihe aus 225 Flügen (Acro und Angle, 30–170° Neigung, 0–100 % Gas,
120 Bilder/s mit schwankender Bildzeit, Standard-Tuning mit Gyro-Rauschen, Prop-Wash und Böen bei ruhigem Wetter).
Die Lage schwingt nicht: Anteil über 2 Hz im Mittel 0,0015° RMS, schlechtester Flug 0,026° (Whoop im Sturzflug ohne
Gas, im eigenen Prop-Wash), Drehrate über 2 Hz in den Testflügen höchstens 0,31 °/s RMS. Bei Vollgas ist die Drohne ruhiger als im
Schweben (5″ Freestyle 0,0014° gegen 0,0046° RMS). PID, D-Anteil, Filter, TPA und die Aerodynamik bleiben unverändert;
Motion-Goldens und Kalibrierung bitgleich.

**2. Kamera-Vibration.** Drei Anteile wuchsen mit dem Tempo, obwohl es dafür keinen Grund in der Wirklichkeit gibt:

- *Motorzittern* stieg mit der Drehzahl im Quadrat bis 0,2° und war damit bei Vollgas 5,5-mal so stark wie im
  Schweben. Eine Unwucht drückt zwar mit ω², der Rahmen weicht aber nur um Kraft / (Trägheit · ω²) aus – die
  Auslenkung hängt nicht von der Drehzahl ab, nur Drehrate und Ton steigen. Das Rauschen liegt zudem unter 19 Hz und
  liest sich bei 0,2° als Wackeln, nicht als Zittern. Jetzt bleibt der Anteil gesunder Props über der Schwebedrehzahl
  bei 0,035°; der Zuschlag für beschädigte Teile wächst wie bisher mit der Drehzahl (ein verbogener Prop soll man sehen).
  Nachtrag aus der Gegenprüfung: Der Zuschlag rechnete mit `Telemetry.vibration`, in der schon ein `r²` steckt, wuchs
  also mit `r⁴` – und zwar ab dem ersten Prozent Abnutzung. Mit vier Motoren bei 97 % zitterte das Bild bei Vollgas mit
  0,092° RMS (Schweben 0,019°), so stark wie die fabrikneue Drohne vor der Korrektur; nach fünf leichten Abstürzen
  (alle Teile noch über 90 %) im Mittel 0,13°. Die Kamera rechnet deshalb mit einem eigenen Pegel `video.WearShake`:
  Teile, die die Mod als unbeschädigt zählt (Zustand ab 90 %, `PartWear.DAMAGED_BELOW`, dieselbe Grenze wie das
  sichtbare Schadensbild am Modell), tragen nichts bei; darunter beginnt der Pegel je Teil bei null (kein Sprung an
  der Grenze) und wächst wie die Physik-Vibration. Physik, Gyro-Rauschen, `Telemetry.vibration`, Goldens und
  Kalibrierung bleiben unverändert. Geprüft in `video.WearShakeTest`, `sim.HighSpeedSmoothnessTest`
  (`lightlyWornPartsKeepTheImageCalmAtSpeed`, `aBentPropStillShowsAtFullThrottle`) und im Spiel mit den Schritten
  `kamera: leicht abgenutzte Teile (ab 90 %) lassen das Bild bei Vollgas ruhig` (Props 95 %, Motoren 97 %, dieselben
  Grenzen wie fabrikneu) und `kamera: ein verbogener Prop ist bei Vollgas im Bild zu sehen, im Schweben kaum`.
- *Schütteln an Boden und Wänden* (Rezirkulation) hing nicht von der Fahrt ab: ein Gate oder eine Wand bei 40 m/s
  schüttelte das Bild wie im Schweben daneben (bis 0,27°). Der Abwind kommt aber nur zurück, solange die Drohne nicht
  aus ihm herausfliegt. `CameraShake.recirculationFade` rechnet nach der Impulstheorie: statische induzierte
  Geschwindigkeit `v₀ = v_h · r`, Fahrt `μ = V / v₀`, induzierte Geschwindigkeit im Vorwärtsflug
  `(v_i / v₀)² = (√(μ⁴ + 4) − μ²) / 2`, Anteil `1 / (1 + (V / v_i)²)` (Bodeneffekt im Vorwärtsflug nach
  Cheeseman und Bennett). Bei `V = v₀` bleiben 38 %, bei `2 v₀` 6 %, bei `3 v₀` 1 %.
- *Gasstoß* reagierte auf jedes Gasgeben im Verhältnis zum Tempo des Anstiegs: normale Gasarbeit bei Tempo
  (60–100 % im 0,7-s-Takt) hielt das Bild mit 0,06° RMS in Bewegung, ein kurzer Stoß um 20 % gab beim 5″ den vollen Ruck.
  Jetzt zählt nur der Anstieg über 4 Schwebedrehzahlen je Sekunde, aufsummiert über 0,1 s – der Ruck richtet sich nach
  der Größe des Gasstoßes und hängt nicht mehr von der Bildrate ab.

**3. Kamerapose.** Die Sim rechnet die sichtbare Pose über den Rest unter einem Substep linear hoch; gespeichert wurde
sie seit dem Umbau der Latenzkette aber mit der Zeit des letzten Sim-Schritts. Das Bild lag damit 0–0,5 ms neben seiner
Zeit, von Bild zu Bild wechselnd – ein Fehler, der mit dem Tempo wächst. Jetzt trägt die Pose die Bildzeit. Die
Latenzkette rechnet unverändert `max(Sim-Rest, Videoverzögerung)`: so alt ist die Knüppelstellung, die im Bild steckt.

| Messung | vorher | nachher |
|---|---:|---:|
| Kamera zu Rahmen im Schweben, RMS / größte (Selbsttest Fabric, 5″ Freestyle) | 0,019° / 0,038° | 0,020° / 0,041° |
| Kamera zu Rahmen bei Vollgas mit 38,5 m/s, RMS / größte | 0,105° / 0,189° | 0,020° / 0,041° |
| Posen-Zeitfehler bei Vollgas, Mittel / größter | 0,274 ms / 0,478 ms (18,4 mm) | 0,000 ms / 0,001 ms (0,0 mm) |
| Drehrate der gezeigten Pose bei Vollgas, RMS (Flugmodell) | 0,070 °/s | 0,072 °/s |
| Kamera-Vibration bei Vollgas, RMS je Klasse Whoop / Cinewhoop / 5″ / Race / 7″ (JUnit) | 0,059° / 0,088° / 0,107° / 0,107° / 0,107° | 0,018° / 0,018° / 0,018° / 0,018° / 0,019° |
| Gasarbeit 60–100 % im 0,7-s-Takt bei 35 m/s, 5″, RMS | 0,058° | 0,018° |
| Kurzer Gasstoß 70 → 90 % / 80 → 90 % bei Tempo, 5″, Ruck | 0,25° / 0,17° | 0,05° / 0,01° |
| Schütteln durch ein Gate oder dicht an einer Wand bei Vollgas (Enge 1), 5″ | 0,27° | 0,012° |
| Posen-Zeitfehler Race bei 48,6 m/s, Mittel / größter (JUnit `sim.VisiblePoseTimingTest`) | 0,246 ms / 0,488 ms (23,6 mm) | 0,0002 ms / 0,0003 ms (0,02 mm) |
| Drehfehler in einer Rolle mit 670 °/s, RMS / größter | 0,19° / 0,33° | unter 0,001° (aus dem Zeitfehler) |

NeoForge misst dasselbe (Vollgas 0,017–0,018° / 0,039–0,040°, Zeitfehler 0,000 ms / höchstens 0,002 ms, Drehrate
0,070–0,075 °/s).

Unverändert und gewollt: Prop-Wash im Sinkflug und in harten Manövern schüttelt (das rechnet die Sim), ein Punch-out
aus dem Schweben ruckt beim 5″ mit 0,25°, bei schwächeren Klassen im Verhältnis zum Schub weniger (7″ 0,16°,
Cinewhoop 0,13°, Whoop 0,05°).

## 4. Standardwerte Rates, Expo, Gas, TPA

| Wert | Propwash (alle Klassen) | Betaflight 4.4/4.5 Standard |
|---|---|---|
| Rates-Typ | Actual | Actual |
| Mitte / Max / Expo (Roll, Nick, Gier) | 70 °/s / 670 °/s / 0 | 70 / 670 / 0 |
| Gas-Mitte / Gas-Expo | 0,50 / 0 | 50 / 0 |
| TPA | 65 % ab 1350 µs (0,35) | 65 % ab 1350 |
| RC-Deadband | 0 | 0 |

Betaflight hat keine eigenen Standards je Klasse; Whoop-, 5″- und 7″-Presets der Hersteller weichen nur nach
Geschmack ab. Die Kurvenformel (`RateCurves`) entspricht Betaflights Actual Rates. Nichts ist klar daneben →
unverändert; gespeicherte Tunes der Spieler bleiben ohnehin unberührt.

## 5. Größe und Geschwindigkeit

- 1 Block = 1 m, die Sim rechnet mit echten Maßen (5″ Freestyle: 225 mm Radstand). Die eigene Drohne im FPV wird in
  Originalgröße gezeichnet.
- **Darstellungsgröße** fremder Drohnen und der eigenen im Sichtflug: Standard 1,0 = echte Größe
  (`DroneRenderConfig.visualScale`, Tab „Darstellung“, 1,0–3,0). Eine 1,75-fach große Drohne wirkt näher und – in
  Körperlängen je Sekunde gemessen – um 43 % langsamer (20 m/s: 51 statt 89 Körperlängen/s beim 5″). Bis 0.4.0 war
  1,75 der Standard; Entscheidung vom 08.10.: Standard 1,0. Wer noch genau 1,75 gespeichert hatte (also nie etwas
  geändert hat), wird beim ersten Start einmal auf 1,0 umgestellt (`DroneRenderConfig.VERSION` 2) und bekommt in der Welt
  einen Hinweis, wo er die Größe zurückstellt (`DroneSizeNotice`); jeder andere gespeicherte Wert bleibt.
- Kamerahöhe: Linse 17 mm über der Rahmenmitte (5″), am Boden wenige Zentimeter über Grund – realistisch.

## 6. Offen

- Fischaugen-Projektion (Tonnenverzeichnung) als Option: braucht ein breiteres Rendering und eine Entzerrung im
  Video-Pass; ohne höhere Renderauflösung wird die Bildmitte um den Faktor 1,4–2,8 hochgerechnet und unscharf.
- Zuschauer-Vibration aus den synchronisierten Motorwerten der fremden Drohne.
