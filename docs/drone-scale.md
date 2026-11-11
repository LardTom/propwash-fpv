# Drohnen skalieren (`minecraft:scale`)

Drohnen nehmen das Vanilla-Attribut `minecraft:scale` an, wie Spieler und Mobs. Damit lässt sich eine Drohne zum
Spaß sichtbar und spürbar vergrößern oder verkleinern:

```mcfunction
/attribute @e[type=propwash:drone,limit=1,sort=nearest] minecraft:scale base set 3
/attribute @e[type=propwash:drone,limit=1,sort=nearest] minecraft:scale base reset
/summon propwash:drone ~ ~ ~ {attributes:[{id:"minecraft:scale",base:0.25d}]}
```

## 1. Befehle und Grenzen

- `/attribute … minecraft:scale` mit `get`, `base get|set|reset` und `modifier add|remove|value get` wirkt auf Drohnen
  wie auf Mobs (`mixin.drone.AttributeCommandMixin`; die Drohne ist kein `LivingEntity` und hat dafür eine eigene
  Attributliste `drone.DroneAttributes` mit genau diesem einen Attribut). Andere Attribute melden „Objekt … hat kein
  Attribut …“.
- Grenzen wie in Vanilla: Der Wert wird auf 0,0625 bis 16 begrenzt (`base set 40` ergibt 16, `0.01` ergibt 0,0625).
- Der Wert gehört zur Drohne, nicht zum Item: Wer die Drohne aufhebt und wieder absetzt, hat wieder Größe 1.
- Speichern: Nur eine geänderte Attributliste landet als `attributes` im Speicherstand der Drohne (Vanilla-Format wie
  bei Mobs, auch für `/data` und `/summon`). Normale Drohnen speichern genau wie bisher.
- Sync: Der Server übernimmt den Wert bei jedem Tick in ein synchronisiertes Feld der Drohne; jeder Client, der die
  Drohne sieht oder später dazukommt, bekommt ihn mit den Entity-Daten.

## 2. Was mitskaliert

Alles wird um den Fußpunkt der Drohne (Mitte der Hitbox-Unterseite) skaliert, die Drohne steht also weiter auf dem
Boden.

- Hitbox und damit Kollision mit Blöcken, Gates und Stangen, Aufheben, Treffer, Namensschild.
- Modell mit Propellern, LEDs und Leuchtschein, Zubehör und Fracht, Schatten und Sichtbarkeitsbox.
- FPV-Kamera: Linse und Schwerpunkt wandern mit (Pilot, Zuschauer, Flashback-Kamerapfad und die Kamera-Pose der
  Add-on-API `DroneQueries#cameraPose`). Die eigenen Propeller im FPV-Bild sehen deshalb gleich aus; die Welt wirkt bei
  3× kleiner, bei 0,25× riesig.
- Staubwirbel unter den Propellern: Höhe, Reichweite und Ring wachsen mit, Partikel etwas größer (Wurzel der Skalierung).

Größen der Hitbox (Breite in Blöcken): Freestyle 5″ 0,289, 7″-Long-Range 0,387, Whoop 0,083. Das kleinste Gate
(Öffnung 1 × 1) lässt eine 3×-Freestyle (0,867) noch knapp durch, ab 3,5× bleibt sie hängen; eine 3×-7-Zoll (1,160)
passt nicht mehr durch. Durch das kleine Standard-Gate (Öffnung 2 × 2) kommt eine Freestyle bis knapp 7×. Eine
0,25×-Drohne passt durch jedes Gate.

## 3. Was nicht mitskaliert

- Das Flugmodell: Masse, Schub, Trägheit, Akku, Rotoren und Bodenkontakt der Simulation bleiben gleich, damit sich die
  Drohne gleich fliegt. Nur ihre Größe ändert sich.
- Die Wegplanung von GPS Rescue rechnet mit der normalen Größe (die Bewegung selbst stößt mit der skalierten Hitbox an).
- Zubehör-Ankerpunkte am Server (`MountPose` der Add-on-API: Greifer, Ausgabe, Mitte der Drohne) bleiben an der
  normalen Stelle.

## 4. Bestenlisten

Runden mit einer Drohne, deren `scale` nicht 1 ist, zählen als „mit Flughilfe“: neuer Grund `LapAssist.SCALE`
(„Skalierung“, Schlüssel `scale`) in `race.AssistCheck`. Der Server kennt ihn selbst (`LapAssist.SERVER_REASONS`), der
Client kann ihn weder melden noch verbergen. In der Checkliste „Warum zählt meine Runde mit Flughilfe?“ steht er unter
„Drohne“, der Text nennt die Größe in Prozent („die Drohne war auf 50 % skaliert (Attribut scale)“). Alte Bitmasken
behalten ihre Bits, `SCALE` ist Bit 8.

## 5. Tests

- JUnit: `tools/fastcheck.sh test --select-class de.lardtom.propwash.drone.DroneScaleTest --select-class
  de.lardtom.propwash.race.AssistScaleTest` (Grenzen, Hitbox, Kamera um den Fußpunkt, Gate-Größen, Grund Skalierung,
  Server-Wertung, Texte).
- Selbsttest `'-PpropwashSelftestOnly=drone-scale:'` (beide Loader, 6 eigene Schritte, mit Bildschirmfotos 10, in
  `drone.client.DroneScaleSelfTests`, Platz bei Spieler + (30, 30)): `/attribute` samt Grenzen, Modifikator, fremdem
  Attribut, Speichern/Laden und Absetzen aus dem Item; der Client sieht 0,25×, 1× und 3× mit passender Hitbox und
  Renderzustand; FPV-Kamera bei 3× dreimal und bei 0,25× ein Viertel so hoch über dem Fuß; Gate mit Öffnung 1 × 1
  (7″ 1× und 5″ 0,25× durch, 7″ 3× und 5″ 4× hängen, 5″ 3× knapp durch); Runde mit 0,5× als Flughilfe mit Grund und
  Prozent, danach mit 1× ohne. Mit `-PpropwashScreenshots=true` (Fabric) die Fotos `drone_scale_025`, `drone_scale_100`,
  `drone_scale_300` (gleiche Kamera, Quarzblock als Maßstab) und `drone_scale_lineup`.
- Beitrittstest (`docs/testing.md` §3): Server mit `PROPWASH_JOINTEST_HOLD=1800 PROPWASH_JOINTEST_DRONESCALE=1`, Client
  mit `PROPWASH_JOINTEST_LABEL=dronescale PROPWASH_JOINTEST_DRONESCALE=1 PROPWASH_JOINTEST_EXPECT='joined|drone_scale=ok'`.
  Der Server beschwört 3 Blöcke vor dem Spieler eine Drohne mit `{attributes:[{id:"minecraft:scale",base:3.0d}]}` und
  setzt sie 5 s später mit `/attribute @e[type=propwash:drone,limit=1,sort=nearest] minecraft:scale base set 0.25`; der
  Client (`drone.client.DroneScaleJoinProbe`) sieht erst 3× (Daten beim Erscheinen), dann 0,25× (Aktualisierung), jeweils
  mit passender Hitbox und Renderzustand. Server-Log `[Beitrittstest] Skalierung: …`.
