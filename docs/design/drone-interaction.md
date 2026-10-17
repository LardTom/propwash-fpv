# Drohnen treffen Lebewesen, Greifer sammelt Items (interact, Propwash 0.5.0)

Besitzer-Entscheidung vom 02.10.2026 aus einer Discord-Idee: (1) Drohnen treffen Mobs und Spieler – Schaden und
Rückstoß aus dem Impuls der Drohne (Masse × relative Geschwindigkeit) mit sinnvollen Grenzen, die Drohne selbst nimmt
den Aufprall wie an einem Block, Props, die ein Lebewesen treffen, zählen als Prop-Strike, PvP wird beachtet, eine neue
Spielregel schaltet es ab, keine Blöcke werden beschädigt. (2) Ein Zubehör zum Einsammeln liegender Items mit begrenzter
Tragkraft, dessen Last über die vorhandene Masse der Bauteile ins Flugmodell geht (keine Änderung der Flugphysik),
Abwurf auf Befehl, beim Landen und bei Abstürzen, Überlebensrezept, Shift-Tooltip und Flugschul-Szene. Nicht: Truhen
plündern, Sprengstoff.

Code im Modul `interact` (`de.lardtom.propwash.interact`, Client `interact.client`); Verträge in
`docs/FOUNDATION.md` §15 „Treffer und Greifer“.

## 1. Treffer

### 1.1 Wo der Treffer entsteht
Geflogen wird clientautoritativ (`docs/api/network-entity.md` 2.4). Damit die Drohne einen Treffer „wie an einem Block“
spürt, muss das Flugmodell des Piloten die Berührung sehen. Der Client des Piloten behandelt deshalb jedes treffbare
Lebewesen in der Nähe als achsenparallelen Kollisionskörper (`PilotInteraction#clip`, vor `QuadcopterEntity#move` in
`PilotSession#moveEntity`):

- treffbar (`StrikeTargets.solid`): lebende `LivingEntity`, nicht Zuschauer, kein Rüstungsständer, nicht der eigene
  Pilot (er steht beim Start und im Sichtflug ständig neben der Drohne, Treffer am eigenen Körper wären nur lästig);
- Körper, die die Drohne schon überlappen, zählen nicht (sonst bliebe eine Drohne in einem Mob stecken, der in sie
  hineinläuft);
- `Shapes.collide` je Achse wie bei den Gates (`ExtraCollider`), danach prüft Vanilla die Blöcke. Welche Entity eine
  Achse begrenzt hat, merkt sich `PilotInteraction` samt Achse, Richtung und Geschwindigkeit der Drohne **vor** dem
  Aufprall.

Das Flugmodell bekommt die gekürzte Bewegung über den unveränderten `MoveReport`: Abprall, Absturzerkennung,
Prop-Strikes und Verschleiß laufen genau wie an einer Wand. Ohne Lebewesen in Reichweite ist die Bewegung bitgleich zu
0.4.0.

### 1.2 Was der Server daraus macht
Nach dem Frame schickt der Client je bestätigtem Kontakt (`PilotInteraction#afterMove`: Abstand ≤ 5 cm nach der
Bewegung) ein `DroneStrikeC2S` (Drohne, Epoche, Ziel, Geschwindigkeit in m/s, Achse, Richtung), je Ziel höchstens alle
10 Ticks. Der Server (`DroneStrikes#onStrike`):

1. nur der Pilot mit passender Epoche, Ziel lebt und ist treffbar;
2. `StrikeRules.decide`: Spielregel an, Ziel ist nicht der Pilot, Spieler nur mit PvP (`ServerLevel#isPvpAllowed`),
   je Ziel 10 Ticks Pause, höchstens 8 Treffer je Sekunde und Drohne;
3. Geschwindigkeit begrenzt auf `min(Server-Grenze, Spitze der letzten 20 Ticks × 1,25 + 4 m/s)` – die Spitze stammt
   aus den angenommenen Zustandspaketen (`QuadcopterEntity.SERVER_MOVED`);
4. Abstand der Boxen ≤ 1,5 m + 0,15 s × Tempo (Latenz);
5. Annäherungstempo = Relativgeschwindigkeit (Drohne minus `getKnownMovement` des Ziels) entlang der Kontaktnormalen;
6. Masse = Flugmodell-Masse des Builds + Fracht.

### 1.3 Schaden und Rückstoß (`StrikeMath`)
| Größe | Wert |
|---|---|
| Impuls p | Masse × Annäherungstempo (kg·m/s) |
| kein Treffer | Tempo < 2 m/s oder p < 0,4 kg·m/s |
| Schaden | 0,4 HP je kg·m/s, höchstens 10 HP (5 Herzen) |
| Rückstoß | 0,05 je kg·m/s, 0,1 … 1,2 (Vanilla-Faustschlag 0,4) |

Beispiele (eingebaute Presets): Whoop (28 g, höchstens 10 m/s) prallt nur ab. Cinewhoop (287 g): 20 m/s ≈ 2,3 HP.
Freestyle 5″ (525 g): 8 m/s ≈ 1,7 HP, 20 m/s ≈ 4,2 HP, Vollgas (140 km/h) ≈ 8,2 HP. Long Range 7″ (846 g) erreicht bei
Vollgas die Grenze, X-Class aus Addons ebenso. 400 g Fracht machen denselben Treffer härter.

Schadensart `propwash:drone_strike` (`data/propwash/damage_type`, Skalierung nie, Erschöpfung 0,1), direkte Entity die
Drohne, Verursacher der Pilot. Dadurch gelten Vanilla-Regeln von selbst: Spieler-gegen-Spieler-Prüfung und Teams
(`ServerPlayer#hurtServer`), Unverwundbarkeit, Rüstung, Schild, Beute und Erfahrung wie bei einem Spieler-Kill, Wölfe
und Golems werden wütend auf den Piloten. Tags: `minecraft:no_knockback` (der Rückstoß kommt aus `StrikeMath` statt der
festen 0,4 von Vanilla) und `minecraft:panic_causes` (Tiere laufen weg). Todesmeldung
`death.attack.propwash.drone_strike` („%1$s wurde von der Drohne von %2$s getroffen“). Treffer an Lebewesen verändern
keine Blöcke; zerbrechliche Blöcke wie Glas behandelt die Welt-Regel `propwash:drones_break_blocks` (Standard aus,
`docs/design/drone-break-blocks.md`), Spinnweben, Fäden, Ranken und Glocken `docs/design/drone-snags.md`.

### 1.4 Spielregel
`propwash:drone_hits_entities` (Boolean, Standard an) ist eine Welt-Regel im Katalog `server.rules.WorldRuleSpecs`
(Gruppe „Spiel“, Kategorie `propwash:propwash`), also auch in `/gamerule`, im Vanilla-Fenster „Spielregeln“, im Tab
„Welt-Regeln“ und im Export für die Website. Aus = Drohnen fliegen wie in 0.4.0 durch Lebewesen. Der Client erfährt den
Wert mit den übrigen Welt-Regeln (`WorldRulesS2C`); vor dem ersten Paket gilt „aus“. Bei der Zusammenführung für 0.5.0
ersetzte das das eigene Paket `propwash:interact_rules` und die Kategorie Mobs.

## 2. Greifer

### 2.1 Entscheidung: Greifer statt Magnet
Ein Servo-Greifer ist echtes FPV-Zubehör (Payload-Drop-Servos), greift jedes Item und lässt per Servo los. Ein Magnet
würde nur Metall halten und bräuchte zum Loslassen einen Elektromagneten; in Minecraft-Mods steht „Magnet“ außerdem für
Items, die zum Spieler fliegen. Name: *Greifer* / *Gripper*, Id `propwash:gripper`.

### 2.2 Bauteil
Eingebautes Zubehör (`data/propwash/propwash_parts/accessory/gripper.json`): untere Halterung (statt des LED-Streifens),
18 g, 24 × 20 × 22 mm, 0,3 W am Flugakku (Servo-Haltestrom), Stirnfläche 4 cm². Auf jedem Rahmen mit unterer Halterung,
also auch am 65-mm-Whoop. Er hängt gerade nach unten (`AccessoryType#hangsStraight`, `AccessoryAnchor#tiltFor` = 0°,
keine Streckung), am Anbaupunkt der unteren Halterung unter der Nase. Modell: `tools/assets/parts/accessories.py`
(`build_gripper`: TPU-Platte, blaues Servo mit Laschen und Horn, Querträger, zwei Backen mit Fingern und Gummiballen,
Servokabel), lackierbar (Servo = Zubehör, Platte = TPU). Rezept: Kupferspule über Platine, Eisennuggets links und rechts,
zwei Haken als Backen. Keine Truhen-Beute (die Beute-Balance bleibt).

### 2.3 Aufnehmen (`DroneCargo`, Server)
- nur scharf, gesteuert und mit Greifer, alle 4 Ticks;
- Reichweite: Kasten ±0,6 Blöcke waagerecht, 0,9 Blöcke unter und 0,25 über dem Fuß der Drohne – man muss tief
  schweben; Items hinter Blöcken (kein freier Strahl) bleiben liegen, ebenso Items mit Aufheb-Verzögerung und Items,
  die Vanilla nur einem bestimmten Spieler gibt (Feld „Owner“ des Items, z. B. Überlauf von Fortschritts-Belohnungen):
  die greift die Drohne nur, wenn dieser Spieler sie steuert (`GripperSpec.mayTake`, wie `ItemEntity#playerTouch`);
- nächstes Item zuerst; `CargoPlan.takeable` nimmt so viele, wie Gewicht und Stapel erlauben (Teilstapel möglich);
- **Tragkraft** `GripperSpec.capacityGrams` = min(400 g, ½ × (Maximalschub − Abfluggewicht),
  Maximalschub ÷ (1,3 × Schwerkraft) − Abfluggewicht) aus `BuildAnalysis`, mit der Welt-Regel `propwash:gravity_scale`
  (0–1000 %, für die Rechnung mindestens 1 %) zum Zeitpunkt des Greifens. Bei normaler Schwerkraft tragen Cinewhoop,
  Freestyle, Race und Long Range 400 g, der Whoop (mit Greifer 46 g, Schub/Gewicht 1,9) 21 g; bei 150 % trägt der Whoop
  nichts mehr, die anderen weiter 400 g; bei 0 % begrenzt nur noch die Schubreserve (½ × Überschuss).
  Voll beladen bleibt Schub/Gewicht bei der eingestellten Schwerkraft immer mindestens 1,3 (Reserve für den
  Spannungseinbruch des Akkus). Höchstens 4 Stapel;
- **Gewicht je Item** (`ItemWeights`): Propwash-Teile, Akkus und Drohnen ihre echte Masse; sonst nicht stapelbar
  150 g, bis 16 stapelbar 40 g, Blöcke 50 g, alles andere 10 g; Behälter (Shulker-Kisten und alles mit Inhalt
  `minecraft:container`, Bündel) wiegen zusätzlich ihren Inhalt, verschachtelt bis 4 Ebenen. Eine volle Shulker-Kiste
  ist damit viel zu schwer für jede Drohne;
- Meldung an den Piloten (Aktionsleiste): „Greifer: +n × Item · 380 / 400 g“, bei voller Last alle 3 s „Greifer voll“.

### 2.4 Last im Flugmodell (keine neue Physik)
Die Last geht als Masse des Zubehörs ins vorhandene Datenmodell: `CargoMass.withPayload(build, g)` ersetzt den Greifer
durch eine schwerere Variante desselben Typs (`AccessoryType#laden`, auf 5 g gerastert und je Gewicht wiederverwendet,
gleicher Schlüssel, gespeichert wird nur der leere Greifer). `ParamDerivation` rechnet daraus wie für jedes Zubehör
Masse, Schwerpunkt und Trägheit (Quader am Anbaupunkt). Damit das im Flug wirkt, tauscht die neue Methode
`DroneSim#setAirframe` nur die Kennwerte (wie der Konstruktor, ohne Zustand, Filter oder Regler zurückzusetzen) und
aktualisiert die Schwebegas-Schätzung. Formeln und Schrittweite bleiben unverändert; ohne Greifer oder ohne Last ist das
Flugmodell exakt das von 0.4.0 (JUnit `GripperTest`, `AirframeSwapTest`; Bewegungs-Goldens und Kalibrierung bitgleich).

Synchronisiert werden nur Gesamtgewicht und das schwerste Item (`QuadcopterEntity` `DATA_CARGO_GRAMS`,
`DATA_CARGO_ITEM`); der Client des Piloten baut daraus den beladenen Build, alle sehen das Item im Greifer hängen
(`DroneVisuals#submitCargo`, 15 cm Item-Block, 3 cm unter den Backen).

### 2.5 Abwerfen
| Auslöser | Ablauf |
|---|---|
| Taste „Greifer öffnen“ (Standard Enter, frei in Vanilla und Propwash) oder Funkenschalter „Fracht abwerfen“ (`SwitchFunction.CARGO_DROP`, Taster, Tab „Modi“) | `CargoReleaseC2S(COMMAND)` |
| harter Absturz (`CrashDamage.crash`, Aufpralltempo ≥ Bruchgrenze des Rahmens) | `CargoReleaseC2S(CRASH)` |
| Landen bzw. Entschärfen: 10 Ticks nicht scharf (auch Ende der Steuerung) | Server |
| Drohne verschwindet (aufheben, zerstören, entfernen) oder der Greifer fehlt im Build | Server, ohne Schwung |

Die Items fallen mit dem Schwung der Drohne (höchstens 1,5 Blöcke/Tick) knapp unter ihr heraus, mit normaler
Aufheb-Verzögerung. Danach greift er erst wieder zu, wenn die Drohne mindestens 1,5 Blöcke vom Abwurfpunkt entfernt ist
(waagerecht oder senkrecht, `GripperSpec.awayFromDrop`) – abgeworfene Stapel verschmelzen sonst mit liegenden Items gleicher
Art und würden sofort wieder gegriffen; zusätzlich ignoriert er die abgeworfenen Items selbst, bis sie seinen Kasten
verlassen haben (höchstens 60 s).
Fracht wird mit der Drohne gespeichert (`Cargo`, Liste von Item-Stapeln); eine geladene, nicht scharfe Drohne setzt sie
nach 10 Ticks ab. Verloren geht nie ein Item.

## 3. Netz, API, Daten
- Netzprotokoll 10 (Propwash 0.5.0): `propwash:drone_strike`, `propwash:cargo_release` (C2S), zwei neue
  synchronisierte Entity-Daten der Drohne.
- Parts-API 1.5.0: `FlightSchoolScenes.GRIPPER` (`propwash:gripper`, neue eingebaute Szene). Greifer-Eigenschaften
  sind (noch) nicht für Addon-Zubehör freigegeben.
- Neue Ressourcen: Schadensart `propwash:drone_strike`, Tags `minecraft:no_knockback` und `minecraft:panic_causes`
  (ergänzt), Bauteil `gripper`, Rezept und Fortschritt, Item-Modelle, Flugschul-Szene `gripper`.

## 4. Prüfungen
- JUnit: `StrikeMathTest`, `StrikeRulesTest`, `CargoPlanTest`, `GripperTest` (Halterung, Hängen, Tragkraft je Rahmen
  und je Schwerkraft 80–150 % mit Schub/Gewicht beladen ≥ 1,3, bei 0 % die Schubreserve, Items fremder Spieler, Masse über das Zubehör,
  Speicherformat), `sim/AirframeSwapTest` (Tausch im Flug ohne Zustandssprung, die Drohne sinkt
  beim alten Schwebegas).
- Selbsttest (beide Loader) `interact: …`: Spielregel registriert und beim Client, Shift-Tooltip, Behälter wiegen mit
  Inhalt (leere und volle Shulker-Kiste, Bündel, Bündel in der Kiste) und Tragkraft je Schwerkraft, Drohne prallt an einer
  Kuh ab (Schaden, Rückstoß in Flugrichtung, Absturz gezählt, nicht hindurch), Spielregel aus (hindurch, kein Schaden),
  Greifer nimmt 8 Eisenbarren und 6 von 64 Bruchsteinen (380 von 400 g, Flugmodell 380 g schwerer) und lässt Smaragde
  liegen, die einem anderen Spieler gehören, Taste wirft ab und
  greift nicht sofort wieder zu, Entschärfen setzt Gold ab, Absturz an einer Mauer verliert die Äpfel. Bildschirmfotos
  `drone_gripper_carry.png`, `tooltip_gripper.png`, `flight_school_gripper.png`, `flight_school_gripper_drop.png`.
