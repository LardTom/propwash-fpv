# Prop-Verschleiß nach Material (Propwash 0.5.0)

Stand: 2026-10-05, gehört zu Propwash 0.5.0. Anlass: Rückmeldung eines Creators, der selbst echte FPV-Drohnen fliegt –
sinngemäß: Props gehen viel zu schnell kaputt. Entscheidung des Besitzers vom 05.10.: Prop-Verschleiß und Prop-Strikes
überarbeiten. Leichte Rempler kosten wenig bis nichts, harte Einschläge bleiben realistisch, das Material zählt (weiches
PC verbiegt sich und fliegt schlechter weiter, hartes PC und Glasfaser-Nylon brechen, Carbon zerspringt selten, dann
aber ganz, Bi-Blend liegt dazwischen). Die Welt-Regeln `propwash:crash_damage` und `propwash:crash_damage_scale`
verhalten sich wie bisher; die Stärke wirkt weiter als Faktor auf jeden Treffer.

Modell und Konstanten stehen in `docs/design/flight-model-spec.md` §9.6; hier stehen Anlass, Recherche, Messwerte und
Entscheidungen.

## 1. Was vorher zu schnell ging

Messung mit dem Flugmodell (`BoxWorld`, Drohne fliegt mit Schwebegas in eine Wand, Absturz entschärft an, 20 Seeds,
schlechtester Prop auf der Wandseite) und mit dem Absturz-Generator aus `WearTest` (Abstürze mit 0,8 … 1,6 ×
Crash-Toleranz, vier Richtungen; Prop-Spitzen im Kontakt bekommen einen Strike mit 0,8 … 1,8 × Schwebedrehzahl):

| 5″ Freestyle, Wand mit | 2 m/s | 5 m/s | 8 m/s | 12 m/s | 16 m/s | 22 m/s |
|---|---|---|---|---|---|---|
| vorher (0.4.0) | 0,94 | 0,91 | 0,83 | 0,61 | 0,23 | 0,00 |
| jetzt (0.5.0) | 1,00 | 1,00 | 0,91 | 0,64 | 0,27 | 0,00 |

Der größte Teil kam von den Prop-Strikes: Jeder Kontakt einer drehenden Propspitze kostete pauschal
`0,06 × max(0,25; strikeSpin)` – auch beim Antippen einer Wand mit 1–2 m/s. Zwei Berührungen machten einen Prop
sichtbar „angeknackst“ (< 0,9). Die Crash-Dosis selbst (Schwere² aus Aufprall- und Seitengeschwindigkeit) lag im
Bereich der Referenzen und bleibt.

Lebensdauer des Props vorne links (Mittel aus 200 Sitzungen, Strikes eingerechnet):

| Material | kritisch (< 0,4) vorher → jetzt | kaputt vorher → jetzt |
|---|---|---|
| PC (alle eingebauten Props) | 8,0 → 16,9 Abstürze | 12,3 → 26,0 |
| weiches PC (`_soft`) | 10,3 → 27,5 | 16,3 → 44,0 |
| Bi-Blend (`_bi`) | 8,9 → 22,2 | 14,1 → 32,8 |
| hartes PC (`_hard`) | 6,7 → 12,3 | 10,1 → 18,8 |
| Glasfaser-Nylon | 6,7 → 12,7 | 10,1 → 18,5 |
| Carbonfaser-Nylon | 7,3 → 15,1 | 11,2 → 21,3 |
| Carbon | 10,5 → 72,4 | 16,8 → 72,4 (kritisch = kaputt) |
| Whoop und Cinewhoop (Ducts, PC) | 26,1 → nie in 400 Abstürzen | 42,4 → nie |

Props halten im Mittel gut doppelt so lange; leichte Rempler kosten nichts mehr. Ducts schützen die Props jetzt fast
ganz: ein 65-mm-Whoop erreicht seine Crash-Toleranz von 14 m/s im Flug praktisch nie (Topspeed 41 km/h), und
unterhalb davon bleibt die Last unter der elastischen Grenze.

## 2. Recherche

| Aussage | Quelle |
|---|---|
| FPV-Props sind meist aus Polycarbonat: leicht, flexibel, biegen oder verziehen sich im Crash, ohne leicht zu brechen. Beim Gemfan Hurricane (PC): verbogene Props zurückbiegen und weiterfliegen. | [OL-Props-Guide] |
| PC biegt statt zu brechen und bricht sauber; Glasfaser-Nylon ist steifer und spröder, splittert eher; Carbon zerspringt scharfkantig. | `motors-props.md` §4.6, [UAV-Prop] |
| Carbon-Props sind deutlich fester als Plastik, aber nicht unzerstörbar; weil sie schwer brechen, nimmt im Crash das Motorlager den Stoß. Plastik-Props gehen öfter kaputt und schützen dafür die Motoren. | [OL-CF] |
| Weiches PC übersteht viele Einschläge, wackelt aber und verliert unter Last Schub; hartes PC ist präzise und bricht früher; Bi-Blend ist steif und zäh zugleich. | Just More Parts, `docs/design/parts-catalog.md` §4.1 (Datenstand der Addon-Props) |

Belastbare Zahlen, wie viele Crashs ein Prop übersteht, gibt es nicht; die Quellen beschreiben das Verhalten. Die
Größenordnung (PC übersteht „dutzende“ leichte Crashs, harte Betonwände kosten auch PC-Props) ist Erfahrungswissen
und deckt sich mit den Werten oben.

## 3. Modell in Kürze

- **Last je Prop** = Crash-Dosis × Kontaktgewicht / 7,5 (wie bisher) + Strike-Last, × 0,35 mit Ducts, ÷ Haltbarkeit.
- **Strike-Last** = `0,045 × min(strikeSpin; 2)² × (0,2 + v_eff / Crash-Toleranz) × Stärke`: Drehenergie (∝ ω²) und
  wie tief der Prop in das Hindernis schlägt (Anfluggeschwindigkeit). Ein Streifschlag im Schweben bleibt elastisch.
- **Elastische Grenze 0,08:** Lasten darunter federt jeder Prop weg.
- **Bruchverhalten** (`sim.PropFailure`) aus Steifigkeit und Haltbarkeit der Prop-Daten, also aus dem Material:
  Sprödigkeit `β = max(σ; smoothstep(0,95; 1,2; Steifigkeit) / √Haltbarkeit)`, Splittern `σ = smoothstep(1,3; 2,2;
  Steifigkeit)`.

| Material (Steifigkeit / Haltbarkeit) | β | σ | Anzeige im Tooltip |
|---|---|---|---|
| weiches PC (0,85 / 1,35) | 0,00 | 0 | verbiegt sich, fliegt schlechter weiter |
| PC (1,0 / 1,0) | 0,10 | 0 | verbiegt sich, fliegt schlechter weiter |
| Bi-Blend (1,1 / 1,15) | 0,60 | 0 | verbiegt sich oder bricht |
| hartes PC (1,12 / 0,8) | 0,85 | 0 | bricht in Stücken |
| Glasfaser-Nylon (1,2 / 0,8) | 1,00 | 0 | bricht in Stücken |
| Carbonfaser-Nylon (1,4 / 0,9) | 1,00 | 0,03 | bricht in Stücken |
| Carbon (2,2 / 1,4) | 1,00 | 1,00 | hält viel aus, zerspringt dann ganz |

- **Verlust** oberhalb der Grenze: verbiegen `(1 − β)·Überlast`, absplittern `0,25·β(1 − σ)·Überlast`, Carbon
  `0,05·βσ·Überlast`; dazu mit Wahrscheinlichkeit `β(1 − σ)(1 − e^(−Überlast/0,4))` ein Riss, der 0,3 … 0,6 kostet, und
  bei Carbon mit `βσ·smoothstep(0,08; 0,8; Last)` ein Bruch (Zustand 0).
- **Steife Props schützen den Motor nicht:** bei Spitzenkontakt oder Strike × `1 + 0,5·βσ` Motorschaden (Carbon 1,5).
- **Wirkung im Flug** (`WearEffects`): verbogen kostet mehr Schub (bis 20 % bei Zustand 0+, PC), angebrochen mehr
  Vibration (`1,2·(1 − c)` gegen `0,7·(1 − c)^1,5`), gemischt nach β. Kaputt bleibt kaputt (0,5 % Schub, nicht fliegbar).

Je Wand-Crash eines 5″ (Schwebegas, Mittel aus 200 Seeds; Mittel / Anteil < 0,9 / < 0,4 / kaputt in %):

| Material | 8 m/s | 12 m/s | 16 m/s | 22 m/s |
|---|---|---|---|---|
| weiches PC | 0,95 / 0 / 0 / 0 | 0,78 / 100 / 0 / 0 | 0,52 / 100 / 0 / 0 | 0,05 / 100 / 100 / 51 |
| PC | 0,89 / 30 / 0 / 0 | 0,64 / 100 / 11 / 0 | 0,30 / 100 / 76 / 14 | 0,00 / 100 / 100 / 98 |
| Bi-Blend | 0,87 / 20 / 4 / 0 | 0,62 / 100 / 23 / 0 | 0,36 / 100 / 66 / 2 | 0,08 / 100 / 94 / 65 |
| hartes PC | 0,74 / 45 / 10 / 0 | 0,47 / 100 / 45 / 0 | 0,22 / 100 / 89 / 11 | 0,02 / 100 / 99 / 83 |
| Glasfaser-Nylon | 0,74 / 50 / 9 / 0 | 0,49 / 95 / 39 / 0 | 0,26 / 100 / 88 / 13 | 0,07 / 100 / 100 / 36 |
| Carbon | 0,96 / 4 / 4 / 4 | 0,63 / 36 / 36 / 36 | 0,13 / 87 / 87 / 87 | 0,00 / 100 / 100 / 100 |

Bis 5 m/s bleibt jedes Material unbeschädigt. Die Bildstufen (`PropDamage`: angeknackst < 0,9, beschädigt < 0,4,
gebrochen = 0) bleiben; durch das neue Modell erscheinen sie bei PC allmählich, bei Glasfaser in Sprüngen und bei
Carbon direkt als „gebrochen“.

## 4. Prüfung

- JUnit `sim.PropWearTest`: Bruchverhalten aller sieben Materialien und der eingebauten Props; leichte Rempler bis
  0,4 × Crash-Toleranz für alle eingebauten Presets im Flugmodell ohne Schaden, bis 0,7 × höchstens 1 %; 22 m/s in die
  Wand bricht beim 5″ in mindestens 80 % der Seeds zwei Props; weiches PC verbiegt bis 17 m/s, ohne zu brechen;
  Glasfaser bricht in Stufen, PC nicht; Carbon bei 8 m/s fast immer heil, bei 24 m/s immer kaputt, Motor leidet mehr;
  Lebensdauern in der Reihenfolge weich > Bi-Blend > PC > hart/Glasfaser, Carbon > 2 × PC; Strike-Last nach Spin² und
  Anflug, Stärke 0 = kein Schaden.
- `sim.WearTest`, `sim.PartDurabilityTest`: Haltbarkeit teilt die Last, Wirkung je Material.
- Goldens: nur `wear:*` und `worn:*` der fünf Presets in `builtin_regression.txt` neu; Flugbahnen mit neuen Teilen
  bitgleich.
- Selbsttest „drone: Absturz beschädigt alle Bauteile“ (jeder Absturz ab Crash-Toleranz kostet weiter jedes Teil
  mindestens 0,0015), „drone: Tooltips zeigen Bruchverhalten der Props und beide Gewichte“, Bildschirmfotos
  `tooltip_prop_crash(_de).png`, `flight_school_repair_material.png`.

## 5. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Bruchverhalten aus Steifigkeit und Haltbarkeit statt neuer Materialien in der API | Die Addon-Daten kennen weiches/hartes PC und Bi-Blend nur über diese beiden Werte; jedes Addon bekommt das Verhalten ohne Änderung, API 1.5.0 und Netzprotokoll bleiben | `PropMaterial` um `SOFT_PC`, `HARD_PC`, `BI_BLEND` erweitern (API-Änderung, alte Daten müssten umziehen) |
| Elastische Grenze statt nur kleinerer Faktoren | leichte Rempler kosten wirklich nichts; harte Crashs bleiben fast wie bisher | Strike-Faktor nur senken (Antippen bliebe sichtbar) |
| Strike nach Drehzahl² × Anflug | Drehenergie wächst mit ω²; ein Prop, der tief einschlägt, leidet mehr als einer, der streift | fester Betrag je Strike (bisher) |
| Carbon gibt den Stoß an den Motor weiter (× 1,5) | belegt [OL-CF]; macht Carbon nicht zum Freifahrtschein | ohne Motoreffekt |
| Ducts × 0,35 wie bisher, jetzt meist unter der Grenze | Ducts schützen echte Whoop-Props sehr gut | eigene Whoop-Regel |
| Prop-Strike-Physik (`CollisionSolver.STRIKE_*`: Drehzahl auf 20 %, Gier-Drall) unverändert | Flugverhalten beim Strike passt und ist Teil der Flug-Engine; geändert ist nur der Schaden | Strike-Schwelle 0,3 × Schwebedrehzahl anheben |
| Bildstufen der Props unverändert | Die Stufen folgen dem Zustand; das neue Modell verteilt den Zustand nach Material | eigene Modelle je Material (Assets für alle Props und Addons) |
| Oberfläche des Hindernisses (Laub, Gras, Stein) nicht berücksichtigt | bräuchte ein neues Feld im Aufprall-Paket (Netzprotokoll) und eine Block-Einordnung | weiche Blöcke dämpfen (Vorschlag für später) |

## Quellen

- [OL-Props-Guide] Oscar Liang, „How to Choose the Best Propellers for FPV Drone“ – https://oscarliang.com/propellers/
  (abgerufen 2026-10-05)
- [OL-CF] Oscar Liang, „Carbon Fibre Props vs Plastic Propeller“ – https://oscarliang.com/carbon-fibre-props-plastic-propeller/
  (abgerufen 2026-10-05)
- [UAV-Prop] UAVMODEL, FPV-Prop-Auswahl nach Steigung, Material und Blattzahl –
  https://blog.uavmodel.com/fpv-drone-propeller-selection-guide-pitch-material-and-blade-count-for-every-flying-style-2026/
- `docs/design/realism/motors-props.md` §4.6 (Material), Just More Parts `docs/design/parts-catalog.md` §4.1
