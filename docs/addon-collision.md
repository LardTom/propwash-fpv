# Kollisionsregeln für Add-ons: Geistflug, Oberflächen, Kamera in Blöcken

Für Add-on-Autoren und Server-Betreiber. Add-ons können mit API 1.7 festlegen, wann eine Drohne durch Blöcke, Wasser,
Entities oder Ranken fliegt, und die Antwort einer berührten Fläche ändern. Propwash setzt das im Flugmodell, am Server
und an der Kamera selbst um; Mixins in Propwash-Interna sind dafür nicht mehr nötig.

Technik und Entscheidungen: [`design/0.6.0/addon-api-namespace.md`](design/0.6.0/addon-api-namespace.md) §3.3, E17–E20;
Oberflächen: [`design/0.6.0/gameplay.md`](design/0.6.0/gameplay.md) §3.F.

## 1. Anmelden

```java
PropwashApi.drones().registerCollisionRule(Identifier.fromNamespaceAndPath("meinaddon", "ghost"), query ->
        query.switchActive(GHOST) && query.pilot().map(MeinAddon::allowed).orElse(false) ? CollisionFlags.GHOST : CollisionFlags.SOLID);
```

- Die Id liegt im eigenen Namensraum (`propwash` ist gesperrt). Jede Id nur einmal.
- `CollisionFlags`: `blocks`, `fluids`, `entities`, `snags`. Bei mehreren Regeln gilt je Feld das UND aller Regeln: jede
  Regel darf eine Kollision abschalten, keine sie wieder einschalten.
- `CollisionQuery`: Drohne, `DroneView`, Pilot, Seite (`CLIENT`/`SERVER`), `autonomous()`, `switchActive(id)`,
  `axis(id)`.
- Ausgewertet wird einmal je Client-Tick beim Piloten und am Server bei jedem Bewegungspaket. Eine Regel, die eine
  Ausnahme wirft, zählt als „fest“; das Log meldet sie einmal.

## 2. Wirkung

| Feld aus | Wirkung |
|---|---|
| `blocks` | Bewegung ohne Block-Kollision, kein Bodenkontakt im Flugmodell (Bodenabstand unendlich), keine Oberflächen-Antwort, Gate-Rahmen ohne Kollision, kein Zerbrechen von Glas. Die FPV-Kamera darf in Blöcke (kein Kamera-Schutz) und zeigt sie wie im Zuschauermodus. Auch Entity-Treffer entfallen. |
| `fluids` | kein Wasser und keine Lava im Flugmodell (auch keine Wasseroberfläche). |
| `entities` | keine Entity-Treffer und kein `DRONE_HIT_ENTITY`; dabei entfällt auch das Zerbrechen von Glas. |
| `snags` | Ranken und Netze greifen nicht. |

- **Zurückschalten:** Wird eine Drohne wieder fest, während ihre Hülle in Blöcken steckt, bleibt sie durchlässig, bis die
  Hülle frei ist. Das OSD zeigt dann `IM BLOCK` (`INSIDE BLOCK`). Kein Teleport. Der Server erlaubt das nur zum
  Herausfliegen: höchstens 16 Blöcke vom Punkt des Zurückschaltens und 20 Sekunden lang, nur für denselben Piloten;
  danach setzt er wie sonst zurück.
- **Server entscheidet:** Der Server lässt die Prüfung des Bewegungspakets (Block-Kollision und Pfad) nur aus, wenn
  **seine eigene** Auswertung `blocks = false` ergibt. Sonst setzt er die Drohne wie bisher zurück, und der Pilot liest
  in der Aktionsleiste „Der Server erlaubt hier kein Fliegen durch Blöcke.“ (höchstens alle 2 Sekunden, mit einer
  Zeile im Server-Log). Rechte prüft die Regel deshalb auf der Seite `SERVER`.
- **Add-on nur beim Spieler:** Der Client wertet nur Regeln aus, die der Server kennt (Paket `propwash:addon_features`
  beim Betreten, `AddonServerFeatures#knowsCollisionRule`). Fehlt eine Regel am Server, bleibt die Drohne beim Client
  fest, statt in Blöcke zu fliegen und vom Server zurückgesetzt zu werden.
- **Rennen:** Runden, in denen eine Regel eine Kollision abschaltet, zählen als Runde mit Hilfe (Grund „Sonstiges“,
  mit der Id der Regel).
- **Ohne Regel** ändert sich nichts: Flugmodell, Kalibrierung und Golden-Dateien sind bitgleich.

## 3. Oberflächen-Antwort ändern

```java
public void onBlockContact(BlockContact contact) {
    if (contact.state().is(Blocks.STONE)) {
        contact.setRestitution(0.8);
        contact.setImpactScale(0.0);
    }
}
```

- Aufruf nach jeder Bewegung mit Kontakt oder dicht über dem Boden, dort wo das Flugmodell läuft: beim Piloten und am
  Server im Heimflug.
- Startwert ist die Antwort der Fläche aus den Block-Tags `#propwash:drone_bouncy|drone_cushion|drone_sticky`
  (`sim.SurfaceResponse`). Bei normalen Blöcken ist der Startwert des Rückpralls der eigene der Drohne; bleibt er
  unverändert, rechnet Propwash weiter wie ohne Regel.
- Die Regeln laufen in der Reihenfolge ihrer Anmeldung, jede sieht das Ergebnis der vorigen. Propwash kappt am Ende:
  Rückprall 0–0,95, Haftung 0,05–1,5, Aufprall 0–1.

## 4. Kamera in Blöcken (intern)

`drone.client.CameraClip` beantwortet „Kamera darf in Blöcke“: wahr bei durchlässiger Drohne im FPV und bei jeder
angemeldeten Quelle (`CameraClip.register(BooleanSupplier)`, als Nächstes die Freecam). `FpvEyeGuard` und der
Kamera-Mixin fragen nur diese Abfrage; weitere Quellen ändern die beiden Dateien nicht.

## 5. Beispiel und Tests

- Beispiel-Add-on: `dev/example/ExampleGhost` (Regel `propwash_example:ghost` über den Schalter `propwash_example:ghost`,
  Recht am Server über den Entity-Tag `propwash_example_ghost`; Regel `propwash_example:bouncy_stone` macht Stein
  federnd, nur im Selbsttest aktiv).
- JUnit: `sim.AddonCollisionTest`, `sim.AddonFlightHooksTest#unusedHooksKeepTheFlightBitIdentical`,
  `drone.collision.client.RigArmingTest` (Scharfschalter im Selbsttest und im Beitrittstest: an erst nach 3 Ticks in Ruhe,
  ohne Erfolg alle 20 Ticks kurz aus, bleibt an, sobald die Drohne scharf ist).
- Selbsttest: Gruppe `addon-api-17: A3`, darin „Regel, die der Server nicht kennt“ (der Server meldet die Geist-Regel als
  unbekannt: beim Client fest, keine Rücksetzung).
- Beitrittstest: `PROPWASH_JOINTEST_COLLISION=1` am Server und am Client; der Client schaltet den Geist über den
  Add-on-Schalter `propwash_example:ghost` (Taste G), der Server wertet nur seinen eigenen Stand dieses Schalters.
  Erwartung `joined|collision ghost switch|collision denied seen|collision passed` (Server-Log: „Kollision ohne Recht: …
  Rücksetzungen“, „Geist über den Add-on-Schalter (am Server an, …)“ und „Kollision mit Recht: Durchflug angenommen“).

## 6. Englische Kernsätze (Website)

- Add-ons can register collision rules: a drone may pass through blocks, fluids, entities or vines, and an add-on can
  change how a touched surface responds (bounce, grip, impact).
- With several rules, every rule may switch a collision off; none can switch it back on.
- The server decides with its own evaluation. Without permission it resets the drone and the pilot reads "The server
  doesn't allow flying through blocks here."
- A rule the server doesn't know (add-on installed only on the player's side) has no effect: the drone stays solid
  instead of being reset.
- A drone that becomes solid inside blocks stays passable until its hull is free; the OSD shows INSIDE BLOCK.
- Laps flown with a collision switched off count as assisted laps.
