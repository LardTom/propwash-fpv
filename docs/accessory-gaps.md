# Werfen, Inhalt sichtbar, Stöße, alle Drohnen und Kamerablick (Add-on-API 1.7)

Für Add-on-Autoren. Vier Bausteine der API 1.7: Zubehör wirft wie ein Dispenser (B1), eingesetzte Items sind an der
Drohne sichtbar (B2), Explosionen und Windkugeln können geflogene Drohnen stoßen (B3), und der Server kennt alle
geladenen Drohnen eines Levels samt Kamerablick (B4). Technik und Entscheidungen:
[`design/0.6.0/addon-api-namespace.md`](design/0.6.0/addon-api-namespace.md) §3.4–§3.7, Tabelle §3.19 und die
Entscheidungen E15, E16 und E36. Stabil ist nur, was unter `de.lardtom.propwash.api` steht.

## 1. Werfen wie ein Dispenser

```java
public void onAction(AccessoryAction action) {
    if (action.number() == 1 && action.pressed()) {
        int slot = action.inventory().firstFilled();
        DispenseResult result = action.dispense(slot, DispenseOptions.defaults().consume(true));
    }
}
```

- **Wo:** am Server, aus `AccessoryBehavior#onAction` oder `#tick`. Andere Threads bekommen `REFUSED`.
- **Mündung:** `MountPose#front()` der Halterung (Mitte der Vorderfläche oder `muzzle_mm`), Richtung genau `forward()`
  (auch schräg, im Looping, kopfüber). Eine abgestellte Drohne steht auf ihrem Zubehör, der Abgabepunkt rückt um die
  Tiefe nach oben, um die die Box unter die Standfläche ragt (`docs/accessory-fix.md` §2).
- **Ablauf** je nach Item im Slot:
  1. Eimer mit Flüssigkeit (Wasser, Lava, Pulverschnee, Fisch-Eimer) → `REFUSED`, solange `allowFluids` aus ist
     (Standard). Mit `allowFluids(true)` gilt das Vanilla-Verhalten (mit Bau-Rechten, siehe unten).
  2. Geschosse (jedes `ProjectileItem`: Pfeile aller Art, Schneeball, Eier, Wurf- und Verweiltränke,
     Erfahrungsfläschchen, Windkugel, Feuerkugel, Feuerwerk, Dreizack) → `FIRED`. Mündungstempo und Streuung wie beim
     Vanilla-Dispenser (`DispenseConfig` des Items) mal `speedScale` bzw. `inaccuracy`, dazu das Tempo der Drohne.
     Besitzer ist der Pilot (sonst der auslösende Spieler), damit gelten PvP-Regeln und Kill-Zuordnung.
  3. TNT → gezündetes TNT an der Mündung, `PLACED`; nur wenn die Spielregel `tnt_explodes` an ist, sonst `REFUSED`.
  4. Items ohne eigenes Dispenser-Verhalten → ein Item wird in `forward()` ausgeworfen wie beim Vanilla-Dispenser
     (0,2–0,3 Blöcke je Tick plus 0,2 nach oben, gleiche Streuung), `FIRED` mit dem `ItemEntity`.
  5. Alles andere (Knochenmehl, Schere, Feuerzeug, leerer Eimer, Schalker-Kiste, geschnitzter Kürbis, Rüstung, Boot,
     Spawn-Ei …) → das Vanilla-Verhalten an einer `BlockSource` am Mündungsblock mit der Richtung, die `forward()` am
     nächsten liegt; es wirkt also auf den Block vor dem Mündungsblock. Gewirkt → `USED`, ohne Wirkung → `BLOCKED`.
     Rückgaben des Verhaltens (z. B. eine Flasche oder ein leerer Eimer) kommen in freie oder passende Slots des
     Zubehör-Inventars (`accepts` und `maxStackSize` des Zubehörs gelten), sonst fallen sie an der Mündung heraus.
- **Status:** `FIRED`, `PLACED`, `USED` sind Erfolg (`success()`); `EMPTY` (Slot leer), `REFUSED` (Slot außerhalb des
  Inventars, Eimer ohne `allowFluids`, Spielregel, falscher Thread), `BLOCKED` (Mündung steckt in einem festen Block,
  Chunk nicht geladen oder Vanilla-Verhalten ohne Wirkung), `DENIED` (keine Bau-Rechte). `entity()` ist das Geschoss,
  das TNT bzw. das ausgeworfene Item.
- **Bau-Rechte:** Vor TNT, Flüssigkeiten und allen Block-Verhalten prüft Propwash am Mündungsblock (und beim
  Block-Verhalten auch am Zielblock) die Rechte des Piloten bzw., ohne Pilot, des auslösenden Spielers:
  `ServerPlayer#mayInteract` (Spawnschutz am dedizierten Server, Weltgrenze), Abenteuer- und Zuschauermodus
  (`blockActionRestricted`) und `mayBuild`. Fehlen sie oder gibt es keinen Spieler (etwa im `tick` einer Drohne ohne
  Pilot), passiert nichts und der Status ist `DENIED`. Geschosse und ausgeworfene Items prüfen keine Bau-Rechte.
- **Eigene Treffer:** Geschosse aus `dispense` treffen die Drohne, die sie abgefeuert hat, nie: Sie fliegen ihre ganze
  Lebensdauer durch die eigene Drohne hindurch, auch nachdem sie deren Hülle verlassen haben und die Drohne sie später
  einholt. Schaden, dessen direkte Quelle oder Verursacher ein eigenes Geschoss ist, kommt an der eigenen Drohne nicht
  an (kein `DRONE_DAMAGED`, kein Zerlegen). Andere Drohnen, Mobs und Spieler trifft das Geschoss normal; platzt eine
  eigene Windkugel an einem Block neben der Drohne, stößt ihr Windstoß die Drohne weiter an (mit dem halben Vanilla-Stoß,
  §3). Grund: Ein Geschoss aus
  dem Werfer startet mit dem Tempo der Drohne plus Mündungstempo und fliegt zuerst von ihr weg. Einholen kann die
  Drohne es nur, wenn sie danach schneller wird als das Geschoss – etwa im schnellen Vorwärtsflug oder bei Gegenwind,
  denn Windkugeln fliegen in Minecraft mit festem Tempo, und Pfeile bremst Minecraft um 1 % je Tick und zieht sie mit
  20 m/s² nach unten, doppelt so stark wie die Erdschwere. Dann treffen sich beide mit dem kleinen Tempo-Unterschied;
  ein echtes Geschoss streift dabei den Rahmen, ohne ihn zu beschädigen. Vanilla rechnet den Schaden aber aus dem Tempo
  des Geschosses in der Welt (Pfeil) bzw. fest (Windkugel 1,0) und schreibt ihn dem Piloten zu; die Drohne würde wie
  von einem Schlag des Piloten zerlegt. Geschosse aus der Hand eines Spielers zählen nicht als eigene Geschosse.
- **Tempo:** Geschosse, gezündetes TNT und ausgeworfene Items bekommen das Tempo der Drohne dazu (im Pilotenflug aus
  den letzten Pilotenpositionen geschätzt, auch zwischen zwei Ticks, `docs/accessory-fix.md` §3). TNT behält den
  Vanilla-Hüpfer (0,2 Blöcke je Tick nach oben, kleine Zufallsrichtung); wer im Vorwärtsflug abwirft, sieht es mit der
  Drohne mitfliegen und fallen.
- **Verbrauch:** `consume(true)` (Standard) nimmt ein Item aus dem Slot. Mit `consume(false)` bleibt der Slot
  unverändert, und Rückgaben wie Flaschen entfallen.
- **Rückstoß:** `recoilNs()` = Masse × Mündungstempo des Geschosses (m/s) aus einer Tabelle, Windkugel zusätzlich
  0,8 N·s für die Druckwelle (vorher 2,5 N·s, Befund RC3P-2); TNT, ausgeworfene Items und
  Block-Verhalten 0. Mit `applyRecoil(true)` (Standard) gibt
  Propwash den Stoß selbst über `DronePhysics#impulse` an der Mündung entgegen `forward()` ins Flugmodell.

| Geschoss | Masse | Mündungstempo (Vanilla) | Rückstoß |
|---|---|---|---|
| Pfeil (auch Spektral-, Trankpfeil) | 0,02 kg | 22 m/s | 0,44 N·s |
| Wurf-, Verweiltrank | 0,35 kg | 27,5 m/s | 9,6 N·s |
| Erfahrungsfläschchen | 0,35 kg | 27,5 m/s | 9,6 N·s |
| Windkugel | 0,05 kg | 20 m/s | 1,0 + 0,8 = 1,8 N·s |
| Schneeball | 0,1 kg | 22 m/s | 2,2 N·s |
| Ei | 0,06 kg | 22 m/s | 1,3 N·s |
| Feuerkugel | 0,1 kg | 20 m/s | 2,0 N·s |
| Feuerwerk | 0,1 kg | 10 m/s | 1,0 N·s |
| Dreizack | 1,5 kg | 22 m/s | 33,0 N·s |
| andere Geschosse | 0,05 kg | laut Item | – |

- **Dreizack 1,5 kg** (vorher 0,6 kg, Befund RC3P-1): dieselbe Masse gilt für den Rückstoß und für die Beladung. Im
  Zubehör-Inventar wiegt jeder Dreizack 1500 g (`ItemWeights.TRIDENT_GRAMS`, auch in Behältern und für
  `AccessoryRegistry#itemGrams`); das geht wie jede Beladung in Flugmasse, Schwerpunkt und Schub/Gewicht ein. Ein
  Freestyle-5″ mit Werfer (567 g) wiegt mit zwei Dreizacken 3567 g, Schub/Gewicht fällt von 9,85 auf 1,57; der Wurf
  eines Dreizacks stößt die abgestellte Drohne mit dem zweiten noch im Werfer um 16 m/s zurück. Für den Greifer ist ein
  Dreizack zu schwer (höchstens 400 g).

### Aktion am Server auslösen

```java
boolean handled = PropwashApi.accessories().triggerAction(drone, 1, true, operator);
```

- Wirkt wie das Paket des Piloten: dieselbe Ratenbegrenzung (je auslösendem Spieler 8 auf einmal, 20 je Sekunde), jedes
  Zubehör mit Verhalten bekommt `onAction`, danach `ACCESSORY_ACTION`. `pressed = false` gibt eine gehaltene Aktion frei.
- `false`, wenn die Drohne kein Zubehör mit Verhalten hat, die Nummer außerhalb 1–4 liegt, die Ratenbegrenzung greift,
  eine nicht gehaltene Aktion losgelassen wird oder der Aufruf nicht im Server-Thread kommt.
- **Die Rechte prüft der Aufrufer** (etwa ein Befehl mit Operator-Recht). Für `dispense` zählt danach der Pilot, ohne
  Pilot der übergebene Spieler. Ohne Pilot gilt eine gehaltene Aktion nur bis zum nächsten Tick der Drohne.
- Gedacht für Befehle und Tests auf dem dedizierten Server; ein Client ist nicht nötig.

### Treffer durch Mobs

Keine neue Signatur: Nahkampf (`Mob#doHurtTarget`), Pfeile, Feuerbälle, Dreizack und Shulker-Geschosse treffen die
Hülle, und `DRONE_DAMAGED` liefert die Schadensquelle mit dem Mob als Verursacher (`damage.source().getEntity()`).
Der Selbsttest bestätigt das mit dem Schlag eines Zombies und dem Pfeil eines Skeletts.

## 2. Inhalt sichtbar

```java
@Override
public Optional<ItemDisplay> display(int slot) {
    return Optional.of(ItemDisplay.at(0.0, -22.0, -40.0).rotated(-90.0F, 0.0F, 45.0F).scaled(1.4F));
}
```

- Für jeden belegten Slot eines Zubehörs mit Verhalten fragt Propwash `display(slot)` und zeichnet den Stapel als
  Item-Modell (Darstellung wie im Rahmen, `FIXED`) an der Drohne. Leerer Slot oder leeres `Optional` = nichts. Alle
  Zuschauer sehen es, der Inhalt ist ohnehin an alle Clients abgeglichen, auch der Pilot im eigenen FPV-Bild und wer
  als Zuschauer das FPV-Bild der Drohne sieht (Befund SPEAR3-1). Dort ist die Drohne selbst verborgen; die Items hängen an der Linse,
  drehen mit der Kamera samt Vibration und Ruck (zurückgerechnet um den Uptilt) und stehen damit fest im Bild wie die
  Props, auch wenn Props und Ducts im FPV-Bild ausgeschaltet sind (Befund G-SPEAR3-1-2). Mit der Gimbal-Kamera eines
  Add-ons bleiben sie an der Lage der Drohne.
- **Nahebene im FPV-Bild (Befund AUTO29-1):** Im Flug liegt die Nahebene der Kamera bei 0,05 Blöcken (50 mm); ein Speer,
  der von unten zur Bildmitte läuft, läge großteils davor. Propwash rückt die Items deshalb wie die Props gleichmäßig um
  die Linse nach außen, bis der nächste sichtbare Punkt des Item-Modells (Eckpunkte im Bild, ein Texel Rand) 55 mm vor
  der Linse liegt, höchstens ×12. Das Bild bleibt dabei gleich. Gezeichnet wird im eigenen Durchgang der FPV-Props an
  der Stelle der Hand mit frisch geleerter Tiefe: Boden, Wand oder ein Mob vor der Drohne verdecken den Inhalt nicht,
  wie eine Hand in der Ego-Sicht. Mit Iris-Shaderpack bleibt der Welt-Durchgang mit demselben Ausgleich; dort kann ein
  Hindernis, das näher liegt als der nach außen gerückte Inhalt, ihn verdecken. Teile, die näher als etwa 4 mm an der
  Linse liegen, schneidet die Nahebene auch nach dem Ausgleich ab.
- **Lage:** Versatz in mm vom Anbaupunkt der Halterung in deren Achsen (x rechts, y oben, −z vorn), gedreht mit der
  Neigung der Halterung. Danach Gieren (um y), Nicken (um x) und Rollen (um z) in Grad, in dieser Reihenfolge,
  positiv nach der Rechte-Hand-Regel. `scale` 1 = das Item-Modell (16 Pixel) ist 160 mm groß; die Drohne samt Items
  folgt der Größe aus den Render-Einstellungen. Grenzen aus `ItemDisplay`: Versatz ±500 mm je Achse, Skalierung über
  0 bis 4.
- `display` läuft am Client beim Zeichnen jedes Bilds: schnell und ohne Seiteneffekte halten. Wirft es, fehlt das Item,
  und der Fehler steht einmal je Zubehör im Log.

## 3. Explosions- und Windstöße

```java
PropwashEvents.DRONE_KNOCKBACK.register(knockback -> {
    if (knockback.cause() != DroneKnockback.Cause.OTHER && isMine(knockback.drone())) {
        knockback.setScale(1.0);
    }
});
```

- **Kein Kernverhalten:** Ohne Listener (oder ohne `setScale`) bleibt alles wie in 0.5.2: geflogene Drohnen spüren
  Explosionen und Windkugeln nicht. Abgestellte Drohnen stößt Minecraft wie bisher selbst.
- Das Ereignis kommt am Server für **geflogene** Drohnen (Pilot, Heimflug oder autonomer Flug), wenn Minecraft sie stößt:
  Explosion (`EXPLOSION`), Windkugel oder Böen-Kugel (`WIND_CHARGE`, erkannt an der Quelle der Explosion) und sonstige
  `push`-Aufrufe anderer Mods (`OTHER`). `deltaVelocity()` ist der Stoß in m/s (Minecraft-Rückstoß × 20).
- Standard-Skala 0. `setScale(s)` (0 bis 2) fordert den Stoß an, `cancel()` bricht ab. Danach rechnet Propwash
  J = m · Δv · s mit der Masse samt Zubehör, Beladung und Fracht, kappt auf 50 N·s und gibt den Stoß über
  `propwash:drone_push` ins Flugmodell (Pilot) bzw. in den Server-Flug.
- Grund für die Standard-Skala 0: Auf Renn-Servern wäre ein Stoß durch fremde Windkugeln ein Griefing-Weg (E16). Der
  Rückstoß beim eigenen Werfen (B1) gilt immer.
- Ausnahme seit Befund OT4-5: Mit der Welt-Regel `propwash:drones_boss_interactions` (Standard an) setzt Propwash die
  Skala vorab auf 1, wenn die Explosion von einer Windkugel der Breeze, einem Wither-Schädel oder dem Wither kommt;
  Listener können sie weiter ändern oder `cancel()` rufen. Windkugeln von Spielern bleiben bei 0
  ([`drone-bosses.md`](drone-bosses.md)).
- **Windkugeln aus dem Werfer stoßen halb so stark** (Befund RC3P-2): Platzt eine Windkugel, die `dispense` abgefeuert
  hat, rechnet Minecraft ihren Windstoß mit dem halben Rückstoß-Faktor (0,61 statt 1,22) – für alle getroffenen Entities,
  Mobs, Spieler, abgestellte und geflogene Drohnen, auch die eigene Drohne, wenn die Kugel nah an ihr platzt
  (`deltaVelocity()` von `DRONE_KNOCKBACK` ist dann ebenfalls halb so groß). Windkugeln aus der Hand eines Spielers und
  aus dem Dispenser-Block bleiben Vanilla. Technik: `OwnShots` merkt sich jedes Geschoss aus `dispense`,
  `mixin.accessory.WindChargeMixin` tauscht beim Platzen den Schadensrechner der Explosion gegen `DroneWindBurst`
  (Faktor `DispenseMath.DRONE_WIND_KNOCKBACK` = 0,5). Add-ons wie Mob Defense bekommen das über `dispense` ohne Änderung.
  Gemessen im Selbsttest: Zombie 5,08 m/s durch die Windkugel eines Spielers, 2,54 m/s durch die aus dem Werfer; die
  eigene Drohne daneben 10,23 gegen 5,12 m/s.

## 4. Alle Drohnen eines Levels und Kamerablick

```java
for (Entity drone : PropwashApi.drones().loaded(level)) {
    PropwashApi.drones().cameraPose(drone).ifPresent(pose -> lookAt(pose.eye(), pose.look()));
}
```

- `loaded(ServerLevel)` → unveränderliche Liste aller geladenen Drohnen des Levels, ohne Entity-Suche: Propwash führt
  sie beim Laden und Entladen der Entities mit (auch in geladenen, aber nicht tickenden Chunks). Gleiche Liste, bis sich
  etwas ändert; günstig genug für jeden Server-Tick.
- `cameraPose(drone)` → `CameraPose(eye, look, up, verticalFovDegrees)` am Server: Linse und Sichtfeld des Baus,
  Uptilt aus dem Tune der Drohne (wie die FPV-Kamera), gedreht mit der Lage der Drohne, ohne Gimbal-Nachlauf und
  Wackeln. `look` und `up` sind Einheitsvektoren. Leer für Entities, die keine Propwash-Drohne sind, und für Drohnen
  ohne FPV-Kamera (fehlendes Video-Teil).
- Für Mobs, die auf Blicke reagieren: Der Selbsttest prüft mit der Vanilla-Formel des Endermans (Kegel 0,025 / Abstand,
  freie Sicht), dass eine schwebende Drohne vor einem Enderman auf dessen Kopf schaut und um 30° gedreht nicht mehr.

## 5. Beispiel

`dev/example/ExampleDispenser` (nur im Entwicklungslauf): Werfer vorne mit 4 Slots (Aktion 1 „Werfer feuern“ wirft den
ersten belegten Slot über `dispense`), Speer-Halterung unten (ein Speer, sichtbar) und Feuerwerks-Booster oben (eine
Rakete, sichtbar; Aktion 3 „Boost“ schiebt 12 N für 0,5 s entlang der Nase und leert den Slot). Ein Listener auf
`DRONE_KNOCKBACK` setzt für Drohnen mit dem Werfer die Skala 1 bei Explosionen und Windkugeln; einer auf `DRONE_DAMAGED`
merkt sich Mobs als Verursacher. Die drei Teile haben Items (nicht im Kreativ-Tab) und Einträge in der Teile-Tabelle des
Beispiels. Die Speer-Halterung hängt gerade (`hangsStraight`, [`accessory-hang.md`](accessory-hang.md)), der Speer liegt
an jedem Rahmen waagerecht unter ihr; die Lage der Rakete gleicht die Neigung der oberen Halterung am 5-Zöller aus (22°),
sie liegt waagerecht auf dem Akku. Beide zeigen mit der Spitze nach vorn.

## 6. Tests

- JUnit: `accessory.dispense.DispenseMathTest`, `drone.CameraPoseTest`, `accessory.ItemDisplayTest`, `drone.KnockbackTest`,
  `drone.client.render.AccessoryLensNearTest` (Nahebenen-Ausgleich an der Linse), `accessory.dispense.TridentLoadTest`
  (Dreizack 1,5 kg in Beladung, Flugmasse, Schub/Gewicht und Rückstoß).
- Selbsttest-Schritte `addon-api-17: B1 – Dreizack wiegt 1,5 kg …` und `addon-api-17: B3 – Windstoß einer Windkugel aus
  dem Werfer …` (Klasse `interact.client.DispenseRecoilSelfTests`): Beladung, Flugmasse und Rückstoß von Dreizack und
  Windkugel an der abgestellten Drohne; Windstoß einer Werfer-Windkugel und einer Windkugel des Spielers, die je an einem
  Steinblock neben einem Zombie und der Drohne platzen, gemessen als Tempo direkt nach dem Platzen (50 % ± 5 %).
- Selbsttest beider Loader: `'-PpropwashSelftestOnly=addon-api-17: B1,addon-api-17: B2,addon-api-17: B3,addon-api-17: B4'`
  (Klasse `interact.client.AccessoryGapsSelfTests`; der Präfix `addon-api-17: B1` erfasst auch B10). Der Schritt
  `addon-api-17: B2 – Speer im eigenen FPV-Bild` (Klasse `interact.client.AccessoryFpvSelfTests`) fliegt einen 7″ mit
  Beispiel-Speer im FPV, er steht auf Barrieren in einem Raum aus schwarzem Beton. Verglichen wird das Bild vor dem
  Videoeffekt mit und ohne Speer, einmal mit und einmal ohne Props und Ducts im FPV-Bild; das Rauschen kommt aus zwei
  Bildern ohne Speer. Zur Kontrolle muss eine Vanilla-Item-Anzeige (`item_display`) mit derselben Lage das Bild genauso
  ändern. Grenze: 0,1 % des Bildes oder dreimal das Rauschen.
- `addon-api-17: B2 – Speer im eigenen FPV-Bild frei in der Luft` (Befund AUTO29-1): derselbe Raum, Kamera 0°. Erst über
  Barrieren (die Nahebene sinkt dort), dann über getöntem Glas (ohne Sichtform, die Nahebene bleibt 0,05 wie im Flug)
  je ein Bild mit und ohne Speer, über Glas auch mit Props und Ducts im FPV-Bild aus. Über Glas muss der Speer
  mindestens 90 % der Fläche über Barrieren haben; die Nahebene jeder Aufnahme wird geprüft.
- `addon-api-17: B2 – Speer im eigenen FPV-Bild bleibt beim Ruck der Kamera fest im Bild wie die Props` (Befund
  G-SPEAR3-1-2): Kamera-Vibration 1,0, Ruck wie bei einem Aufprall mit 10 m/s; das Bild mit Ruck (Kamera um mindestens
  0,5° gedreht) darf sich vom Bild ohne Ruck nicht mehr unterscheiden als die Grenze oben.
- Bildschirmfotos (Fabric): `accessory_display_side`, `accessory_display_front`, `accessory_display_boosted`.
- Server-Rauchtest beider Loader: `[Server-Rauchtest] Werfer: …` (Spawnschutz, Auslösen ohne Client).

## 7. Deko-Drohnen mit drehenden Props (Befund RC2-3)

Ein Drohnen-Item zeigt seine Props wie im Flug als durchscheinende Scheiben, wenn in seinem `minecraft:custom_model_data`
der String `blur` steht (Wunsch aus der Community: Drohnen als Deko in der Welt). Das gilt überall, wo das Item
erscheint: Item-Rahmen und Leuchtrahmen, `item_display`, am Boden, im Slot, in der Hand, auf dem Kopf und im Regal.

```mcfunction
/give @p propwash:drone[custom_model_data={strings:["blur"]}]
/item modify entity @s weapon.mainhand {type:"minecraft:set_custom_model_data",strings:{values:["blur"],mode:"replace_all"}}
/summon minecraft:item_display ~ ~1 ~ {item:{id:"propwash:drone",components:{"minecraft:custom_model_data":{strings:["blur"]}}}}
```

- `/give` ohne Bauplan gibt die Freestyle-Drohne; `/item modify` setzt `blur` auf eine gebaute Drohne in der Hand
  (Bauplan, Lack und Schäden bleiben). Der Schlüssel der Item-Funktion heißt in 26.x `type`, nicht mehr `function`.
- Je Prop gilt: drehend nur mit `_blur`-Modell (`drone_render/prop/<key>_blur`, bei Add-on-Props im eigenen
  Namensraum), sonst statisch; gebrochene Props stehen immer still. Nur der genaue String `blur` zählt.
- Lackieren und Lack entfernen behalten `blur` (`PaintItems` ändert nur die Farben von `custom_model_data`). Wird die
  Drohne platziert und wieder aufgehoben, entsteht ein neues Item ohne `blur`.
- Technik: `DroneItemRenderer.Look#blur` liest den String, `DroneVisuals#fillPreview(…, spinning)` setzt die
  `_blur`-Teile wie im Flug; Größe und Lage im Item-Rahmen bleiben wie beim statischen Item.
- Tests: JUnit `drone.client.render.DroneItemBlurTest`, Selbsttest `drone-render: Deko-Blur` beider Loader (neun Ansichten
  und die Welt mit Item-Rahmen, `item_display`, Boden-Item und Hand), Bildschirmfotos `drone_item_blur` und
  `drone_item_blur_display` (Fabric); Einzelheiten in [`testing.md`](testing.md) „Deko-Drohnen mit drehenden Props“.
