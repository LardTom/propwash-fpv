# Gameplay-Loop v2 — Funke, Sichtflug, Akkus, Ladegerät, Shift-Tooltips

> Stand 0.5.0: Brillen werden nicht mehr gebunden. Die Funke prüft die getragene Brille (passt sie zum Videosystem:
> FPV, keine Brille: Sichtflug, falsche Brille: abgelehnt). Was hier zum Binden der Brille und zu `BIND_GOGGLES` steht,
> ist durch `docs/design/receiver-binding.md` ersetzt; der Rest gilt weiter.

Implementierungs-Spezifikation (nur Design, kein Code). Stand: Integrations-Zweig
`integration` (Commit `83fb527`). Quelle der Anforderungen: Playtest-Wunsch des Owners und die mit
ihm getroffenen Entscheidungen (unten in §1 zusammengefasst — sie sind **verbindlich**, nichts davon neu verhandeln).

Umsetzung durch **einen Code-Agenten** (alles Java, JSON-Daten, Sprachdateien, Rezepte, Doku) und **einen
Asset-Agenten** (Modelle, Texturen, Blockstates, Item-Definitionen, GUI-Texturen, Generator/Validator). Beide arbeiten
parallel; die einzige Schnittstelle ist der **Asset-Vertrag in §11** (IDs, Dateien, Maße, Koordinaten). Der Code-Agent
erfindet keine Asset-Pfade außerhalb von §11, der Asset-Agent keine Java-Namen.

Verbindlich weiterhin: `docs/CONVENTIONS.md` (kein Kommentar im Code, Mixins nur unter
`de.lardtom.propwash.mixin.<modul>` als `<Ziel>Mixin`, englische Bezeichner, Texte nur über Sprachdateien mit
Umlauten), `docs/FOUNDATION.md` §1/§2/§3/§5/§9/§10/§11/§15, `docs/ASSETS.md`. Minecraft-APIs nur aus `.ref/` bzw.
`docs/api/*.md` — die in diesem Dokument genannten 26.3-APIs sind in `.ref/vanilla` nachgeschlagen (Fundstellen jeweils
angegeben), trotzdem vor Benutzung noch einmal gegen `.ref/vanilla` **und** `.ref/neoforge` prüfen. Prüfen mit
`tools/fastcheck.sh all` und `tools/fastcheck.sh test` (kein Gradle, kein Spielstart).

---

## 0. Inhalt

1. Entscheidungen (Kurzfassung) und Spielablauf
2. Datenmodell: Data-Components, Entity-Daten, Speicherformat, Migration
3. Akku-Kompatibilität (`BatteryFit`)
4. Interaktionstabelle
5. Pilotenmodi FPV und Sichtflug (LOS): Start, Stopp, Server-Prüfung, Subsystem-Matrix
6. Sichtflug im Detail: Blickführung, Eingabesperren, Funk-HUD
7. Akku-Lebenszyklus und Ladegerät
8. Menüs und Screens (Akkutausch, Ladegerät)
9. Netzwerk
10. Funke (Item, Special-Renderer, Gimbal-Animation)
11. **Asset-Vertrag** (für den Asset-Agenten)
12. Shift-Tooltips: System und vollständige Texte (DE/EN)
13. Weitere Sprachschlüssel (Meldungen, GUI, HUD)
14. Rezepte und Creative-Tab
15. Änderungen je Modul (Dateien/Klassen)
16. JUnit-Tests
17. Selbsttest-Schritte
18. Doku-Nachträge
19. Risiken und bewusst ausgelassene Punkte

---

## 1. Entscheidungen (Kurzfassung) und Spielablauf

| Thema | Entscheidung |
|---|---|
| Funke | Neues Item `propwash:remote` (EdgeTX-artiger Handsender: zwei Gimbals, Antenne, Display), in der **Haupthand** gehalten, echtes 3D-Item-Modell in erster und dritter Person (auch in der Hand des eingefrorenen Pilotenkörpers im FPV). |
| Binden | Rechtsklick auf eine platzierte Drohne mit der Funke → Funke gebunden (speichert Drohnen-UUID + Name/Build-Kurzinfo). Rechtsklick mit der Brille **in der Hand** → Brille gebunden. Reihenfolge egal, erneutes Binden ersetzt. Meldungen in der Actionbar. |
| Fliegen | Rechtsklick in die Luft mit gebundener Funke, Drohne geladen und in Reichweite → Steuerung. **FPV**, wenn der Spieler eine Brille **trägt**, die an **dieselbe** Drohne gebunden ist **und** deren `VideoSystem` den `VideoType` der Drohne empfängt (Kreativ-Brille: empfängt alles, an jede Drohne bindbar). Sonst **Sichtflug (LOS)**. |
| Stopp | Bestehende Taste „Steuerung beenden“ (`key.propwash.drone_exit`, Standard V), erneuter Rechtsklick mit der Funke, oder Schleichen + Rechtsklick mit der Funke. |
| Sichtflug | Eigene Ego-Kamera, Laufen gesperrt, Maus-Drehen gesperrt; der Blick dreht sich **jeden Frame automatisch und weich** zur Drohne (kein Umschalter, kein Freier Blick). Keine Video-Effekte. Kompaktes Funk-HUD im EdgeTX-Stil statt Brillen-OSD. Drohne wird normal gerendert. Reichweite = Funkstrecke, nicht Video. |
| Alter Startweg | „Drohne mit Brille auf dem Kopf anklicken“ **entfällt**. Starten nur noch mit der Funke. |
| Leere Hand | Rechtsklick mit leerer Hand auf eine nicht gesteuerte Drohne = **aufheben** (Build, eingesetzter Akku, Tuning, Drohnen-Id bleiben). |
| Schleichen + Rechtsklick | Auf eine Drohne = **Akkutausch**. |
| Akkus | Jeder `BatteryType` ist ein Item (gibt es schon) mit eigenem Ladestand `propwash:battery_charge` (0..1, neu = 1,0), Ladebalken wie Haltbarkeit, Prozent + Spannung im Tooltip. Eine Drohne enthält genau einen Akku oder keinen. `DroneBuild.battery` = eingesetzter Akku (Physik, Gewicht, Spannung). **Kein automatisches Nachladen** der Drohne mehr. |
| Akkutausch | Schleichen + Rechtsklick (nicht scharf, nicht gesteuert) → kleines Menü (serverautoritativ) mit Akku-Slot + Inventar: entnehmen (Drohne ohne Akku kann nicht verbunden werden, Anzeige „Kein Akku“) oder passenden einsetzen. Schnellweg: passender Akku in der Hand → direkter Tausch, alter Akku ins Inventar oder fallen lassen. |
| Kompatibilität | Tabelle je Rahmen (§3); unpassend → Meldung mit Grund (Zellen/Größe-Gewicht). Werkbank benutzt dieselbe Regel. |
| Ladegerät | Block `propwash:battery_charger` mit 4 Akku-Slots, Menü + Screen, lädt über Zeit (voller Pack ≈ 90 s Echtzeit, nach Kapazität skaliert, Server-Config; mit Redstone schneller), Anzeige je Slot + Balken, Ladezustand am Block sichtbar. Laden **nur** dort. |
| Shift-Tooltips | Für **alle** Propwash-Items: ohne Shift eine Kurzzeile (+ ggf. Statuszeile) + „Shift: Details“; mit Shift Zweck, Benutzung, Kompatibilität, Kennwerte. DE und EN. |

**Spielablauf (Soll):** Drohne in der Werkbank bauen (Akku muss zum Rahmen passen) → Drohne auf den Boden setzen →
Funke in die Hand, Drohne anklicken („Funke verbunden mit …“) → Brille in die Hand, Drohne anklicken („Brille verbunden
mit …“) → Brille aufsetzen (Rechtsklick in die Luft oder Kopf-Slot) → Funke in die Hand, Rechtsklick in die Luft → FPV.
Ohne (passende, gebundene) Brille → Sichtflug. Akku leer → landen, Verbindung trennen, Schleichen + Rechtsklick,
Akku tauschen, leeren Akku ins Ladegerät.

---

## 2. Datenmodell

### 2.1 Data-Components (neu, in `core.CoreComponents`)

`core` ist Foundation; die Erweiterung ist hier freigegeben (Integration, ein Code-Agent). Alle neuen Components
`persistent` **und** `networkSynchronized` (Tooltips brauchen sie auf dem Client).

| Id | Typ | Codec / StreamCodec | Auf welchen Items | Bedeutung |
|---|---|---|---|---|
| `propwash:battery_charge` (**besteht**) | `Float` 0..1 | unverändert | Akku-Items (**neu**: Standard 1,0 im Item-Prototyp), Drohnen-Item | Ladestand. Auf dem Drohnen-Item = Ladestand des eingesetzten Akkus (fehlt bei „kein Akku“). |
| `propwash:bound_drone` | `core.BoundDrone` | siehe 2.2 | `remote`, alle Brillen | Bindung an eine Drohne |
| `propwash:drone_id` | `UUID` | `UUIDUtil.CODEC` / `UUIDUtil.STREAM_CODEC` (`.ref/vanilla/net/minecraft/core/UUIDUtil.java:23/42`) | `drone` | Stabile Identität der Drohne = Entity-UUID der platzierten Drohne (§2.4) |
| `propwash:battery_installed` | `Boolean` | `Codec.BOOL` / `ByteBufCodecs.BOOL` | `drone` | nur vorhanden, wenn **false** (Drohne ohne Akku). Fehlt = Akku eingesetzt (rückwärtskompatibel). |

Registrierung wie die bestehenden (`PropwashRegistries.component(name, b -> b.persistent(...).networkSynchronized(...))`,
`BoundDrone` zusätzlich `.cacheEncoding()`).

### 2.2 `core.BoundDrone`

```
record BoundDrone(UUID drone, String name, FrameType frame, VideoSystem video)
```
- `drone`: Entity-UUID der Drohne (= `drone_id`).
- `name`: Anzeigename zum Zeitpunkt des Bindens: eigener Name (`getCustomName().getString()`, auf 48 Zeichen gekürzt) oder
  leer (dann zeigt der Tooltip den Rahmen-Kurznamen `propwash.content.frame_short.<frame>`).
- `frame`, `video`: Build-Kurzinfo für Tooltip und Brillenprüfung im Tooltip (die maßgebliche Prüfung macht der Server
  gegen die echte Entity).
- `CODEC`: `RecordCodecBuilder` mit Feldern `drone` (`UUIDUtil.CODEC`), `name` (`Codec.string(0, 48)`, optional,
  Standard `""`), `frame` (`DroneBuildCodecs`-Codec des Rahmen-Enums über `key()`), `video` (`VideoSystem` über
  `name().toLowerCase(Locale.ROOT)`, unbekannt → Fehler).
- `STREAM_CODEC`: `UUIDUtil.STREAM_CODEC`, `ByteBufCodecs.stringUtf8(48)`, Rahmen-StreamCodec aus `DroneBuildCodecs`,
  `VideoSystem` als `VAR_INT` des Ordinals **nur im Netzwerk** (Speicherformat immer über Namen/Keys).
- `Component displayName()`: `name` als Literal oder Rahmen-Kurzname.

### 2.3 `QuadcopterEntity` (neu synchronisiert/gespeichert)

| Feld | Serializer | Standard | Gespeichert als | Zweck |
|---|---|---|---|---|
| `DATA_PILOT_MODE` | `EntityDataSerializers.BYTE` (Ordinal von `drone.PilotMode`) | `NONE` | nicht gespeichert | Modus der laufenden Sitzung; Server setzt ihn **vor** `setPilot(player)` und bei Modus-Wechsel; `setPilot(null)` setzt `NONE`. |
| `DATA_HAS_BATTERY` | `EntityDataSerializers.BOOLEAN` | `true` | `HasBattery` (Boolean, fehlt → true) | Akku eingesetzt? |

- `DroneBuild.battery` ist immer der **zuletzt eingesetzte** Typ (auch wenn `hasBattery == false`), damit
  `AirframeSpec`, Hitbox und Renderer einen definierten Wert haben. Bei `hasBattery == false` blendet der Renderer den
  Akku aus (§5.3), das Gewicht im Tooltip wird ohne Akku gezeigt, Fliegen ist unmöglich (Start wird verweigert).
- `DATA_BATTERY` (Ladestand) bleibt; bei `hasBattery == false` ist er bedeutungslos (auf 0 setzen).
- **Entfällt:** `RECHARGE_INTERVAL_TICKS`, `RECHARGE_PER_SECOND`, `rechargeTimer` und der Nachlade-Zweig in `tickServer`.

Neue öffentliche Methoden (nur Server-Thread, außer Getter):
- `PilotMode pilotMode()`, `void setPilotMode(PilotMode)`.
- `boolean hasBattery()`, `boolean batteryLocked()` (= `isArmed() || pilot().isPresent()`).
- `ItemStack batteryStack()`: neuer Stack des eingesetzten Akkus mit `battery_charge` (über `content.Batteries.stackOf`),
  `ItemStack.EMPTY` ohne Akku. Verändert nichts.
- `ItemStack takeBattery()`: gibt `batteryStack()` zurück und setzt `hasBattery=false`, Ladestand 0.
- `void insertBattery(BatteryType type, float charge)`: `setBuild(PartCategory.BATTERY.with(build, type))` (bzw.
  `new DroneBuild(..., type)`), `setBatteryCharge(charge)`, `hasBattery=true`. Prüft **nicht** die Kompatibilität
  (Aufrufer prüft mit `BatteryFit`).

`loadFromStack`/`toStack` (Vertrag FOUNDATION §11 wird erweitert):
- `loadFromStack`: zusätzlich `battery_installed` (fehlt → true) → `hasBattery`; `battery_charge` nur bei
  `hasBattery`. `drone_id` behandelt **nicht** `loadFromStack`, sondern `QuadcopterItem#useOn` (vor `addFreshEntity`, §2.4).
- `toStack`: zusätzlich `drone_id = getUUID()`; ohne Akku `battery_installed=false` und **kein** `battery_charge`.

### 2.4 Drohnen-Identität (`drone_id`)

Damit eine Bindung Aufheben und Wiederaufstellen überlebt, ist die Entity-UUID die Drohnen-Id:
- `QuadcopterItem#useOn` (Server): `UUID id = stack.get(DRONE_ID)`; wenn vorhanden **und**
  `serverLevel.getEntityInAnyDimension(id) == null` (`.ref/vanilla/.../server/level/ServerLevel.java:1478`), dann
  `drone.setUUID(id)` (`Entity#setUUID`, `.ref/vanilla/.../world/entity/Entity.java:3360`) **vor** `addFreshEntity`.
  Sonst bleibt die zufällige UUID. `QuadcopterEntity#getPickResult` entfernt `DRONE_ID`, damit eine Pick-Block-Kopie im
  Kreativmodus nie die UUID eines Originals in einem ungeladenen Chunk übernimmt (die Kopie ist eine neue Drohne,
  Bindungen zeigen weiter auf das Original).
- Server-Suche einer gebundenen Drohne: `player.level().getEntity(uuid)` (`Level#getEntity(UUID)`,
  `.ref/vanilla/.../world/level/Level.java:772`), nur im Level des Spielers.
- Presets/Werkbank-Drohnen haben keine Id, bis sie einmal platziert und aufgehoben wurden.

### 2.5 Migration

- Bestehende Drohnen-Items/Entities ohne neue Felder: Akku eingesetzt (Typ aus `drone_build`), Ladestand wie bisher.
- Bestehende Akku-Items ohne `battery_charge`: der Prototyp liefert jetzt 1,0.
- Stapelgröße der Akkus bleibt 16. Gleich volle Akkus stapeln sich (bei `set(..., 1.0F)` entfernt
  `PatchedDataComponentMap#set` den Patch, `.ref/vanilla/.../core/component/PatchedDataComponentMap.java:66-74`),
  unterschiedlich geladene nicht. Ladestand beim Schreiben auf 1/1000 runden (`Batteries.quantize`), ab 0,999 exakt 1,0.

---

## 3. Akku-Kompatibilität (`build.BatteryFit`, reines Java)

```
enum BatteryFit.Result { OK, WEAK, CELLS_LOW, CELLS_HIGH, TOO_HEAVY }
static Result check(FrameType frame, BatteryType battery)
static boolean allowed(Result r)          // OK oder WEAK
static Rule rule(FrameType frame)         // record Rule(int minCells, int maxCells, double maxMassGrams, int preferredMinCells, int preferredMinCapacityMah)
static List<BatteryType> fitting(FrameType frame)   // OK + WEAK, Enum-Reihenfolge
static List<FrameType> framesFor(BatteryType battery)
```

Regel je Rahmen (Prüfreihenfolge: Zellen zu wenig → Zellen zu viel → zu schwer → schwach → OK):

| Rahmen | Zellen | max. Akkumasse | „schwach“, wenn | Ergebnis je Akku |
|---|---|---|---|---|
| `WHOOP_65` | 1S–1S | 10 g | — | `b1s_300_hv` OK · `b4s_650` CELLS_HIGH · `b6s_1100` CELLS_HIGH · `b6s_1300` CELLS_HIGH · `b6s_4000_lion` CELLS_HIGH |
| `CINEWHOOP_3` | 3S–4S | 120 g | — | `b1s_300_hv` CELLS_LOW · `b4s_650` **OK** · `b6s_*` CELLS_HIGH |
| `FREESTYLE_5` | 4S–6S | 230 g | Zellen < 6 | `b1s_300_hv` CELLS_LOW · `b4s_650` **WEAK** · `b6s_1100` OK · `b6s_1300` OK · `b6s_4000_lion` TOO_HEAVY (420 g) |
| `RACE_5` | 4S–6S | 230 g | Zellen < 6 | wie `FREESTYLE_5` |
| `LONG_RANGE_7` | 6S–6S | 500 g | Kapazität < 1300 mAh | `b1s_300_hv` CELLS_LOW · `b4s_650` CELLS_LOW · `b6s_1100` **WEAK** · `b6s_1300` OK · `b6s_4000_lion` OK |

- Alle Presets (`DroneBuild.PRESETS`) sind OK (JUnit prüft das).
- **Entschieden (Integration Welle 2b):** `LONG_RANGE_7` nimmt alle 6S-Packs; `b6s_1100` bleibt „schwach“ (erlaubt mit
  Werkbank-Warnung). Laut `BuildAnalysis` schwebt die Long-Range damit 13,7 min statt 15,3 min (`b6s_1300`) bzw. 32,4 min
  (`b6s_4000_lion`) — fliegbar, aber am Zweck des Rahmens vorbei.
- Die Tabelle ist die Quelle für: Werkbank (`DroneBuild.problems/warnings`), Akkutausch (Menü-Slot + Schnellweg),
  Tooltips (Akku: „Passt zu“, Rahmen: „Akkus“), Akku-Menü („Passende Akkus“).
- `DroneBuild`: neue `Issue`-Werte **`BATTERY_FRAME`** (in `problems()`, wenn `!allowed(check(frame, battery))`) und
  **`BATTERY_UNDERSIZED`** (in `warnings()`, wenn `WEAK`). `BuildSlots.affectedBy`: beide →
  `{FRAME, BATTERY}`. Die bestehenden Spannungswarnungen (Motor/Stack) bleiben zusätzlich (4S an 5" liefert also auch
  `MOTOR_UNDERVOLTAGE` — gewollt, das ist das „schwach“).
- Gründe als Text (Sprachschlüssel §13): `CELLS_LOW` „zu wenig Zellen (%1$sS, braucht %2$sS–%3$sS)“, `CELLS_HIGH` „zu
  viele Zellen (%1$sS, höchstens %2$sS)“, `TOO_HEAVY` „zu groß und schwer (%1$s g, höchstens %2$s g)“, `WEAK` „passt,
  aber schwach (%s)“ mit Unterfall Zellen („weniger Spannung“) bzw. Kapazität („kurze Flugzeit“).
- Diese Tabelle kommt zusätzlich als Abschnitt „Akkus je Rahmen“ in `docs/ASSETS.md` (Code-Agent, §18).

---

## 4. Interaktionstabelle

Nur `InteractionHand.MAIN_HAND` (Nebenhand → `PASS`). Entscheidung als reine Funktion
`drone.DroneInteraction.resolve(HeldKind held, boolean sneaking, boolean pilotedByOther, boolean pilotedBySelf,
boolean locked, boolean heldBatteryFits)` → `Action` (JUnit-testbar); Ausführung serverseitig in
`drone.DroneInteractions.apply(ServerPlayer, QuadcopterEntity, Action, ItemStack held)`. Client: `QuadcopterEntity#interact`
liefert auf dem Client `SUCCESS` nur für Aktionen ≠ `PASS` (die Entscheidung braucht dort nur Item-Art und Schleichen;
`pilotedBy…`/`locked` aus den synchronisierten Daten).

`HeldKind`: `EMPTY`, `REMOTE`, `GOGGLES`, `BATTERY`, `OTHER`. `locked` = `drone.batteryLocked()`.

### 4.1 Rechtsklick auf eine Drohne

| Hand | Schleichen | Zustand der Drohne | Aktion | Ergebnis / Meldung (Actionbar) |
|---|---|---|---|---|
| Funke | nein | beliebig | `BIND_REMOTE` | `bound_drone` auf die Funke schreiben (ersetzt alte Bindung). „Funke verbunden mit %s“. Trägt der Spieler eine Brille, die nicht an diese Drohne gebunden ist: zweite Meldung als Chat-freie Actionbar-Folge 40 Ticks später „Tipp: Brille in die Hand nehmen …“ (nur einmal pro Bindung). |
| Brille | nein | beliebig | `BIND_GOGGLES` | Nur wenn `system.receives(drone.build().video().system)`: `bound_drone` auf die Brille. „Brille verbunden mit %s“. Sonst **keine** Bindung: „Diese Brille empfängt kein %s-Video – nicht verbunden“ (rot). |
| Akku | ja | nicht `locked`, Akku passt | `QUICK_SWAP` | 1 Akku aus der Hand nehmen, alten (falls vorhanden) mit seinem Ladestand ins Inventar (`Inventory#add`, sonst `spawnAtLocation`), neuen einsetzen. „Akku eingesetzt: %s (%s %%)“ bzw. „Akku getauscht: …“. Ton `SoundEvents.ARMOR_EQUIP_GENERIC` (Vanilla, audio-Regel betrifft nur eigene SoundEvents). |
| Akku | ja | nicht `locked`, Akku passt nicht | `REJECT_BATTERY` | „%1$s passt nicht in %2$s: %3$s“ (rot, Grund aus §3). Kein Menü. |
| Akku | nein | beliebig | `HINT_BATTERY` | „Schleichen + Rechtsklick, um den Akku einzusetzen“ (grau). |
| leer / sonstiges / Funke / Brille | ja | nicht `locked` | `OPEN_BATTERY_MENU` | Akku-Menü öffnen (§8.1). |
| beliebig | ja | `locked` | `LOCKED` | „Erst landen und die Verbindung trennen“ (rot). |
| leer | nein | nicht gesteuert | `PICK_UP` | wie heute `DronePiloting.pickUp` (Reichweite `PICKUP_REACH`), Item per `toStack()` ins Inventar oder fallen lassen. |
| leer | nein | von anderem gesteuert | `OCCUPIED` | „Diese Drohne wird bereits von jemand anderem gesteuert“. |
| leer | nein | vom Spieler selbst gesteuert | `PASS` | (kommt praktisch nicht vor, `keyUse` ist während der Sitzung gesperrt) |
| sonstiges | nein | beliebig | `PASS` | Vanilla macht weiter (z. B. `Item#use`). |

Vorrang: `locked` schlägt alle Schleich-Aktionen; Schleichen + Akku in der Hand schlägt das Menü.
Linksklick (Angriff) bleibt unverändert (`hurtServer` → `breakApart`, Drop per `toStack()` inkl. Akku und Id).

### 4.2 Rechtsklick mit der Funke in die Luft (`RemoteItem#use`, nur Haupthand)

| Schleichen | Zustand | Ergebnis |
|---|---|---|
| egal | Spieler steuert gerade eine Drohne (Server: `DronePiloting.pilotedBy`) | Stopp (`release`), „Verbindung zur Drohne getrennt“. (Im Normalfall kommt der Stopp schon vom Client, §5.2 — dieser Zweig ist die Server-Absicherung.) |
| ja | keine Sitzung | Status: „Verbunden mit %s · %s Blöcke entfernt“ / „Verbunden mit %s · gerade nicht erreichbar“ / „Die Funke ist mit keiner Drohne verbunden – klicke eine Drohne mit ihr an“. |
| nein | keine Bindung | „Die Funke ist mit keiner Drohne verbunden – klicke eine Drohne mit ihr an“. |
| nein | Drohne nicht im Level geladen / entfernt | „Drohne %s nicht gefunden – nicht geladen oder zu weit weg“. |
| nein | gebunden, gefunden | Start nach §5.1. |

- Rückgabe: Server `InteractionResult.SUCCESS`; Client `InteractionResult.CONSUME` (kein Armschwung,
  `.ref/vanilla/.../world/InteractionResult.java:13`).
- Entprellen (Server): pro Spieler höchstens eine Start/Stopp-Aktion je 10 Ticks (`DronePiloting.REMOTE_DEBOUNCE_TICKS`,
  Map `UUID → gameTime`, bei `PLAYER_LEAVE`/`STOPPED` leeren).

### 4.3 Brille

Rechtsklick in die Luft mit der Brille: unverändert Vanilla (`Equippable` → aufsetzen). Die Bindung bleibt am Stack
(auch im Kopf-Slot). Kein Startweg mehr über die Brille.

---

## 5. Pilotenmodi FPV und Sichtflug

### 5.1 Start (Server, `DronePiloting.useRemote(ServerPlayer, ItemStack remote)`)

1. Bindung lesen, Drohne suchen (§4.2).
2. `hasBattery == false` → `NO_BATTERY`: „Kein Akku in der Drohne – Schleichen + Rechtsklick zum Einsetzen“.
3. Entfernung Spieler ↔ Drohne > `min(ServerRules.maxPilotDistance(player), RadioLink.START_RANGE)` → `TOO_FAR`.
4. Modus bestimmen (reine Funktion `drone.PilotRules.decide(boolean gogglesWorn, @Nullable UUID gogglesBinding,
   @Nullable VideoSystem gogglesSystem, UUID drone, VideoSystem droneVideo)` → `record Decision(PilotMode mode,
   LosReason reason)`):
   - Brille getragen (`ContentIds.wornGoggles(player)`), Bindung == Drohnen-UUID, `system.receives(droneVideo)` → `FPV`,
     `reason = NONE`.
   - sonst `LINE_OF_SIGHT` mit `reason`: `NO_GOGGLES` (keine Brille getragen, keine Zusatzmeldung),
     `GOGGLES_NOT_BOUND`, `GOGGLES_BOUND_OTHER`, `GOGGLES_WRONG_SYSTEM`.
   - Kreativ-Brille: `receives` ist immer true; gebunden sein muss sie trotzdem.
5. `DronePiloting.tryStart(player, drone, mode)` (neue Signatur, ersetzt `tryStart(player, drone, requireGoggles)`):
   Prüfungen wie heute (entfernt, Spieler tot/Zuschauer, anderes Level, besetzt, `ServerRules.mayPilot`), dann
   `drone.setPilotMode(mode)` **vor** `drone.setPilot(player)` (beide Entity-Daten gehen im selben Tick raus).
   `StartResult`: `STARTED, ALREADY_PILOT, UNAVAILABLE, NO_BATTERY, OCCUPIED, TOO_FAR, DENIED` (die Werte
   `NO_GOGGLES`/`WRONG_GOGGLES` entfallen).
6. Meldung: FPV „FPV: verbunden mit %s“ (aqua); LOS „Sichtflug: verbunden mit %s“ (aqua), bei `reason ≠ NO_GOGGLES`
   stattdessen die Grund-Meldung (gold), z. B. „Deine Brille ist nicht mit dieser Drohne verbunden – Sichtflug“.
   Die Meldung schickt der Server (Actionbar); `PilotSession.start` zeigt nur noch „Keine Funke verbunden …“ als
   Warnung, wenn kein Eingabegerät da ist (bestehender Schlüssel `propwash.drone.session.no_radio`).

`PilotRequestC2S(entityId, start)`: `start == true` wird serverseitig **ignoriert** (Payload bleibt wegen
Kanalgleichheit bestehen; nur `start == false` = Stopp). `DronePiloting.interactStart` entfällt.

### 5.2 Stopp

- Client: `DroneKeys.EXIT` (wie heute) **oder** ein Klick auf `keyUse` während der Sitzung (beide Modi, egal ob
  geschlichen) → `PilotSession.requestStop()` → `PilotRequestC2S(id, false)`. Der `keyUse`-Klick wird im bestehenden
  Mixin `mixin.drone.client.MinecraftMixin#propwash$blockWorldInteraction` gezählt, bevor er geleert wird
  (`PilotView.onUseKeyWhilePiloting()`); danach bleibt `keyUse` gesperrt, bis die Taste losgelassen ist
  (`PilotView.useLatch`, verhindert sofortiges Neustarten durch gehaltene Taste; zusätzlich Server-Entprellung §4.2).
- Server: `release` wie heute; außerdem Stopp, wenn (in `DronePiloting.onServerTick`, zusätzlich zu den bestehenden
  Prüfungen):
  - die Haupthand des Piloten 10 Ticks am Stück keine an diese Drohne gebundene Funke hält → „Funke weggelegt –
    Verbindung getrennt“;
  - Entfernung > `RadioLink.RELEASE_RANGE` → bestehender Schlüssel `propwash.drone.pilot.lost_range` (Text wird zu
    „Funkverbindung verloren – zu weit entfernt“);
  - `hasBattery == false` (Absicherung).
- `setPilot(null)` setzt `pilotMode = NONE`.

### 5.3 Moduswechsel während der Sitzung

- Server, jeden Tick: Modus `FPV` und `PilotRules.decide(...)` liefert nicht mehr `FPV` (Brille abgesetzt,
  getauscht) → `setPilotMode(LINE_OF_SIGHT)`, Meldung „Brille abgesetzt – weiter im Sichtflug“. Umgekehrt (LOS → FPV)
  **kein** automatischer Wechsel (Inventar ist während der Sitzung gesperrt, §6.2; Neustart der Sitzung genügt).
- Client: `PilotSession.onClientTick` vergleicht `drone.pilotMode()` mit dem aktiven Modus und schaltet um
  (`applyMode`: Kamera-Entity, Perspektive, eingefrorener Körper, Video-Quelle — alles aus §5.4).

### 5.4 `RadioLink` (drone, reines Java)

Konstanten wandern aus `PilotSession` hierher: `FULL_RANGE = 800`, `FADE_RANGE = 400` (Qualität 0 bei 1200),
`START_RANGE = 800`, `RELEASE_RANGE = 1250`. `static float quality(double distance)` (1 bis 800, linear auf 0 bei
1200). Der Client benutzt `quality` für `linkStrength()`, der Server `START_RANGE`/`RELEASE_RANGE`. Gilt in **beiden**
Modi (die Funkstrecke braucht man immer; im FPV kommt die Videostrecke von video zusätzlich dazu).

### 5.5 Subsystem-Matrix

`PilotSession` bekommt `PilotMode mode()`, `boolean isFpv()`, `boolean isLineOfSight()`. **`isActive()` bedeutet ab
jetzt „steuert eine Drohne, egal in welchem Modus“** (FOUNDATION §11 wird angepasst). Jeder bisherige Aufrufer wurde
geprüft:

| Subsystem / Aufrufer | FPV | Sichtflug (LOS) | Änderung |
|---|---|---|---|
| Kamera-Entity (`PilotSession.start/applyMode`) | Drohne (`mc.setCameraEntity(d)`) | Spieler; Perspektive beim Start merken und auf `CameraType.FIRST_PERSON` setzen, beim Ende zurücksetzen | neu |
| Kamera-Pose `mixin.drone.client.CameraMixin` über `FpvView.cameraPose` | aktiv | aus | `FpvView.active()` → `isFpv()` |
| FOV `mixin.video.client.CameraMixin` über `VideoState.fpvFov()` | Video-FOV | Vanilla | `VideoState.fpvFov`: `isActive()` → `isFpv()` |
| Video-Verbindung, Post-Effekte, Störbild (`VideoState.resolveSource`) | aktiv | **aus** (keine Quelle) | `resolveSource`: `isActive()` → `isFpv()` |
| Posen-Puffer `VideoState.record` (aus `PilotSession.onFrame`) | ja | ja (harmlos, wird bei Sitzungsende geleert) | keine |
| Hände ausblenden `GameRendererMixin` | ja | **nein** (Funke in der Hand sichtbar) | über `FpvView.active()` |
| Frustum-Refresh `LevelExtractorMixin` | ja | nein | über `FpvView.active()` |
| Pilotenkörper verstecken `LevelExtractorMixin#propwash$hidePilotBody` | nur solange die Kamera < 2,0 Blöcke vom Körper entfernt ist (Start) — weiter weg sieht man den eingefrorenen Körper **mit der Funke in der Hand** | nie (Ego-Sicht, Vanilla rendert den eigenen Körper ohnehin nicht) | neu: `FpvView.hidesBody(entity)` |
| Körper einfrieren `LivingEntityMixin#travel` | ja | ja (nicht laufen, nicht springen, keine Schwerkraft) | `freezeBody` in beiden Modi |
| Maus-Drehen `MouseHandlerMixin#turnPlayer` | gesperrt | gesperrt (Blick führt `LosView`) | `PilotView.active()` |
| Tasten `MinecraftMixin#handleKeybinds` | Angriff, Benutzen (→ Stopp), Pick, Perspektive, Hand tauschen, Wegwerfen, **Hotbar 1–9, Inventar** geleert | dito | erweitert, `PilotView.active()` |
| Hotbar-Scrollen | gesperrt: `PilotSession.onClientTick` setzt den beim Start gemerkten Slot zurück (`Inventory#setSelectedSlot`) | dito | neu, kein Mixin |
| Fadenkreuz (`ClientRegistrar.hideVanillaHud(CROSSHAIR, …)` in `DroneClient`) | aus | aus | `PilotView::active` |
| Vanilla-HUD ausblenden (`OsdHud.hideVanilla`) | laut OSD-Config | **nein** (Hotbar, Herzen sichtbar — der Spieler steht in der Welt) | `isActive()` → `isFpv()` |
| Brillen-OSD (`LiveOsdSource.active`) | ja | nein | `isActive()` → `isFpv()` |
| Funk-HUD (`osd.client.RadioHud`, neu) | nein | ja | neu (§6.3) |
| Drohne rendern (`QuadcopterRenderer.isOwnFpvDrone`) | eigene Drohne unsichtbar | **sichtbar** | `isActive()` → `isFpv()` |
| Akku am Modell (`DroneVisuals.fillFromEntity`) | — | — | `hasBattery == false` → Akku-Slot nicht zeichnen (beide Modi, auch für Zuschauer) |
| Motorsound (`MotorVoices`, Zeilen mit `session.isActive()`) | Pilotenansicht (ohne Abschwächung) | **positional** wie für Zuschauer (man hört die Drohne dort, wo sie ist) | `isActive()` → `isFpv()` (beide Stellen) |
| Pieptöne (`AudioHooks.beep`: Arm, Akku, Crash) | ja | ja (die Funke piept) | keine |
| Suchpiepser `LocatorBeeper` | wie bisher | wie bisher | keine |
| Eingabe (`TransmitterInput`, Tastatur-Flug) | aktiv | aktiv (WASD/Pfeile steuern die Drohne, der Körper ist eingefroren) | keine |
| Rescue (`RescueController`) | aktiv | aktiv | keine |
| Rennen (`ClientRaceTracker`, `RaceHud`) | aktiv | aktiv | keine |
| Konfigurator (`PilotGuard`, `DroneContext`, `TemplateApplier`, `TuneAccess`) | wie bisher | wie bisher | keine |
| Chunk-Streaming (server, `PILOT_CHANGED`) | Anker Spieler → Drohne | **ebenso** (der Client braucht die Blöcke um die Drohne für Kollisionen) | keine |
| Server-Plausibilität `DroneStateC2S` | wie bisher | wie bisher | keine |
| Reichweite Server | `maxPilotDistance`, `RadioLink.RELEASE_RANGE` | dito | neu: `RELEASE_RANGE` |
| Reichweite Client | Funk (`RadioLink.quality`) + Video (video) | nur Funk | `updateLinkQuality` benutzt `RadioLink` |

`drone.client.PilotView` (neu): `static boolean active()` (= `PilotSession.INSTANCE.isActive()`),
`static boolean useLatched()`, `static void onUseKeyWhilePiloting()`. `FpvView.active()` bleibt, bedeutet aber `isFpv()`.

---

## 6. Sichtflug im Detail

### 6.1 Blickführung (`drone.LosAim` rein, `drone.client.LosView` Client)

- `LosView.onFrame(Minecraft, DeltaTracker)` wird in `DroneClient.init()` **nach** `PilotSession.INSTANCE::onFrame` an
  `ClientBus.FRAME_START` gehängt (Listener laufen in Registrierungsreihenfolge, FOUNDATION §6), damit die
  Drohnenposition dieses Frames schon steht. Nur wenn `isLineOfSight()` und nicht pausiert.
- Ziel: `PilotSession.comPosition` (Schwerpunkt der Drohne); Auge: `player.getEyePosition()` (Körper steht still).
- `LosAim` (keine Minecraft-Typen): `yawTo(dx, dz) = toDegrees(atan2(-dx, dz))`, `pitchTo(dx, dy, dz) =
  -toDegrees(atan2(dy, sqrt(dx²+dz²)))` (Minecraft-Konvention, +Pitch = nach unten), `approach(current, target,
  dtSeconds, tau)` = `current + wrapDegrees(target - current) * (1 - exp(-dt/tau))`, `tau = 0,025 s` (Integration: von 0,06 s verkürzt, damit der Blick auch nahe, schnelle Drohnen eng hält); Pitch auf
  ±89° klemmen; Abstand < 0,75 Blöcke → Blick halten.
- Setzen: `player.setYRot(yaw)`, `player.setXRot(pitch)`, `player.yRotO = yaw`, `player.xRotO = pitch`,
  `player.setYHeadRot(yaw)` (andere Spieler sehen den Piloten der Drohne nachschauen). Weil `yRotO == yRot`, liefert
  die Kamera in diesem Frame exakt diesen Blick (keine Tick-Interpolation). Feldnamen/Setter vor Benutzung in
  `.ref/vanilla/.../world/entity/Entity.java` prüfen.
- Maus-Drehen ist gesperrt (§5.5); es gibt **keinen** Umschalter und keinen freien Blick.
- **Nur im Sichtflug.** Im FPV dreht nichts den Spieler zur Drohne: Blick, Kopf und Körper bleiben so, wie sie beim
  Wechsel ins FPV standen (auch für andere Spieler, denn ohne Kamera am Spieler sendet Vanilla keine Rotation). Der
  gezeichnete Kopf senkt sich dabei weich um 20° Richtung Funke (`drone.client.PilotHeads`, FOUNDATION §11
  `PilotSession`). Die Entscheidung steckt in `drone.PilotLook` (JUnit `PilotLookTest`: Drohne kreist um den Piloten,
  im FPV bleiben Gieren/Nicken exakt stehen, im Sichtflug folgt der Blick; Wechsel wirkt ab dem nächsten Frame).

### 6.2 Eingabesperren (beide Modi)

Siehe Matrix §5.5. Zusätzlich im Sichtflug: Perspektive auf Ego erzwingen (Start: `mc.options.getCameraType()` merken,
`setCameraType(FIRST_PERSON)`; Ende: zurück). Inventar-Taste gesperrt, damit Funke und Brille während der Sitzung nicht
getauscht werden (Server prüft trotzdem, §5.2/§5.3).

### 6.3 Funk-HUD (`osd.client.RadioHud`, `osd.client.LosOsdSource`)

- `LosOsdSource extends LiveOsdSource`, `active()` = `PilotSession.INSTANCE.isLineOfSight()`; eigener `OsdSampler`
  (Daten wie das OSD: `cells`, Spannung, Ladestand, `armed`, Modus, `linkStrength`, `armedSeconds`, Position/Home,
  `rescueActive`/`rescuePhase`, `radioConnected`).
- HUD-Ebene `ClientRegistrar.hudLayer(Constants.id("radio"), RadioHud::extract)` in `OsdClient.init()`.
- Aussehen: Panel **128 × 64 GUI-Pixel**, **links oben**, Abstand 6 px zum Rand (rechts unten lag es über der Funke in
  der Hand), im dunklen Propwash-Look (Palette `PANEL`/`PANEL_HEADER`, Amber-Akzent, Statusfarben; Welle 2b ersetzt das
  graugrüne LCD). Kein Textur-Asset (alles `fill`/`text`).
- Rennmeldungen (`race.client.RaceHud`) erscheinen als Toasts **oben mittig** (Panel mit Rand und farbigem Akzentstreifen
  links, deckend), nicht mehr als halbtransparente Tafel mitten im Bild; der große Countdown bleibt mittig.

| Zeile (y ab Panel-Oberkante) | Inhalt |
|---|---|
| 0–9 (invertiert) | links Modellname (Drohnen-Name oder Rahmen-Kurzname, gekürzt), rechts `LQ 98` + 4 Balken (Link-Qualität) |
| 12–27 | links groß (Schrift ×2) Spannung `23.4V`; rechts `74%` + Balken 40 × 5 (Farbe bleibt LCD; unter 20 % blinkt der Balken 2 Hz) |
| 30–39 | `SCHARF` invertiert, wenn scharf, sonst `SICHER`; rechts Flugmodus (`ACRO`/`ANGLE`/`HORIZON`, bestehende Schlüssel `propwash.drone.mode.*`) |
| 42–51 | `T1 01:23` (Summe `armedSeconds`), rechts Entfernung `D 42m` |
| 54–63 | Zustandszeile, Vorrang: `KEIN SIGNAL` (blinkend, `linkStrength == 0` **bei verbundener Funke** — beim Tastaturflug ist `linkStrength` immer 0) > `RESCUE <Phase>` > `Keine Funke – Tastatur` (Tastatur-Fallback) > Höhe über Home `H 12m` |

---

## 7. Akku-Lebenszyklus und Ladegerät

### 7.1 Lebenszyklus

```
Akku-Item (charge c) ──einsetzen──▶ Drohnen-Entity (DATA_BATTERY = c, build.battery = Typ)
   ▲                                   │ Pilot-Client: DroneSim.setBatteryCharge(c) beim Start,
   │                                   │ Sim entlädt → DroneStateC2S.battery → Server acceptBattery (nur fallend)
   │                                   ▼ → DATA_BATTERY
   └──entnehmen/Tausch◀── Drohnen-Entity ──aufheben/Drop──▶ Drohnen-Item (battery_charge = c, drone_build.battery)
Ladegerät: Akku-Item c → c + Δ pro Intervall bis 1,0
Werkbank: Bauen → Drohnen-Item übernimmt Typ **und Ladestand** des Akkus aus dem Slot;
          Zerlegen → Akku-Item bekommt den Ladestand der Drohne (ohne Akku bleibt der Akku-Slot leer)
```

- Nirgends sonst ändert sich ein Ladestand (kein Nachladen in der Welt, keine Magie).
- `StateValidation.acceptBattery` bleibt (Server nimmt nur fallende Werte an, Toleranz wie heute).
- Die Client-Option „Unendlicher Akku“ (Simulation) bleibt unverändert (entlädt dann einfach nicht).
- Arm-Sperre bei leerem Akku macht wie heute das Flugmodell (`ArmBlocker.PACK_DEPLETED`).

### 7.2 Hilfsklassen

- `content.Batteries` (Common): `Optional<BatteryType> typeOf(ItemStack)`, `boolean isBattery(ItemStack)`,
  `float charge(ItemStack)` (geklemmt, Standard 1), `ItemStack stackOf(BatteryType, float charge)`,
  `void storeCharge(ItemStack, float)` (quantisiert, ≥ 0,999 → 1,0), `float quantize(float)`,
  `double openCircuitVoltage(BatteryType, float charge)` = `cells × CellCurve.ocv(chemistry, charge)`
  (Integration: `sim` bleibt unverändert, `Batteries` führt dieselben Stützstellen als Kopie, bis `CellCurve.ocv`
  öffentlich ist — Sim-Wunsch).
- `content.BatteryItem extends PartItem`: Registrierung der Akku-Teile in `ContentItems` mit `BatteryItem::new` und
  `.component(CoreComponents.BATTERY_CHARGE.get(), 1.0F)` im Prototyp. `isBarVisible` → immer true;
  `getBarWidth` → `round(13 × charge)`; `getBarColor` → `0x55FF55` (> 50 %), `0xFFFF55` (> 20 %), `0xFF5555` sonst
  (`Item#isBarVisible/getBarWidth/getBarColor`, `.ref/vanilla/.../world/item/Item.java:234-242`).

### 7.3 Ladegerät `propwash:battery_charger` (content)

- **Block** `content.BatteryChargerBlock extends BaseEntityBlock`: Eigenschaften `FACING` (horizontal,
  `HorizontalDirectionalBlock.FACING`-Muster, beim Setzen entgegen der Blickrichtung) und `STATE`
  (`EnumProperty<ChargerState>`, `ChargerState { IDLE, CHARGING, DONE }`, `getSerializedName` = `idle|charging|done`).
  Form: `Block.box(1, 0, 2, 15, 8, 14)` für Nord/Süd, `Block.box(2, 0, 1, 14, 8, 15)` für Ost/West (passt zum Modell §11).
  Licht: `CHARGING` → 6, `DONE` → 4, `IDLE` → 0. Stärke 2,5, Klang Metall, `requiresCorrectToolForDrops` nein.
  `useWithoutItem` → Menü öffnen. `useItemOn` mit Akku in der Hand und **ohne** Schleichen → Akku in den ersten freien
  Schacht legen (1 Stück), Ton `SoundEvents.ARMOR_EQUIP_GENERIC`; sonst `InteractionResult.TRY_WITH_EMPTY_HAND` (26.3:
  so geht Vanilla weiter zu `useWithoutItem`).
  `getTicker` nur serverseitig (`createTickerHelper`, `.ref/vanilla/.../world/level/block/BrewingStandBlock.java:49`).
  Komparator: `hasAnalogOutputSignal` true, Signal = `ceil(15 × volle Akkus / 4)` (Methodennamen in `.ref` prüfen).
- **Block-Entity** `content.BatteryChargerBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer`:
  4 Slots (`NonNullList`), `getMaxStackSize()` = 1, `canPlaceItem` nur Akkus. Hopper: oben/Seiten einlegen,
  unten nur **volle** Akkus entnehmen. Beim Abbauen fallen die Akkus heraus (Vanilla `BlockEntity#preRemoveSideEffects`
  → `Containers.dropContents` für `Container`, `.ref/vanilla/.../world/level/block/entity/BlockEntity.java:233`).
  Speichern: Items wie Vanilla-Container.
- **Laden** (Server-Tick, alle `INTERVAL = 10` Ticks): für jeden Slot mit Akku und `charge < 1`:
  `charge += ChargeMath.step(type, INTERVAL, powered, config)`; `powered = level.hasNeighborSignal(pos)`. Weil der
  Ladestand auf 1/1000 gerundet gespeichert wird, merkt sich die Block-Entity je Schacht den Rest unter 1/1000 (nicht
  gespeichert); sonst bliebe ein großer Pack bei langer Ladezeit (Schritt < 0,0005) für immer stehen.
  Danach `STATE` neu bestimmen (keine Akkus → `IDLE`; mindestens einer < 1 → `CHARGING`; sonst `DONE`), nur bei
  Änderung `setBlock(..., 3)`; `setChanged()`.
- `content.ChargeMath` (rein): `seconds(BatteryType t, Cfg c) = max(c.minSeconds, c.referenceSeconds ×
  (t.capacityMah / 1300)^c.capacityExponent)`; `step = INTERVAL / (20 × seconds) × (powered ? c.redstoneMultiplier : 1)`;
  `remainingSeconds(charge, t, powered, c)`. Mit Standardwerten (90 s, Exponent 0,5, min 20 s): 1S 300 mAh ≈ 43 s,
  4S 650 ≈ 64 s, 6S 1100 ≈ 83 s, 6S 1300 = 90 s, 6S 4000 ≈ 158 s (leer → voll; mit Redstone ×2 halb so lang).
- **Server-Config** (server-Modul, `ServerRulesConfig`, Datei `server.json`, Validator ergänzen, `copy/assign`
  ergänzen): `batteryChargeSeconds = 90.0` (10…3600), `batteryChargeCapacityExponent = 0.5` (0…1),
  `batteryChargeMinSeconds = 20.0` (1…3600), `redstoneChargeMultiplier = 2.0` (1…10). Die Clients brauchen die Werte
  nicht (Restzeit kommt über `ContainerData`, §8.2).

---

## 8. Menüs und Screens

Beide Screens im dunklen Konfigurator-Stil der Werkbank (Anthrazit, Amber-Akzent; Textfarben aus `docs/ASSETS.md`
„Werkbank-GUI“: Beschriftung `0xFFE6E8EC`, gedämpft `0xFF8C949E`, Akzent `0xFFFFBB00`, Fehler `0xFFFF5555`,
gut `0xFF55FF55`). Hintergrund per `graphics.blit(RenderPipelines.GUI_TEXTURED, TEX, leftPos, topPos, 0, 0, W, H,
256, 256)`. Slot-Koordinaten = `Slot#x/y` (linke obere Ecke der 16 × 16-Item-Fläche; gezeichneter Rahmen bei x − 1,
y − 1, 18 × 18 — liefert die Textur).

### 8.1 Akku-Menü der Drohne (`drone.DroneBatteryMenu`, `drone.client.DroneBatteryScreen`)

- Menütyp `DroneModule.DRONE_BATTERY_MENU = PropwashRegistries.extendedMenu("drone_battery",
  DroneBatteryMenu::new, ByteBufCodecs.VAR_INT)` (Öffnungsdaten = Entity-Id). Öffnen nur mit
  `Services.PLATFORM.openMenu(player, provider, ByteBufCodecs.VAR_INT, drone.getId())`. Titel
  `container.propwash.drone_battery`.
- Container: Server `drone.DroneBatteryContainer implements Container` (Größe 1) liest/schreibt direkt die Entity
  (`getItem` → `batteryStack()`, `removeItem`/`removeItemNoUpdate` → `takeBattery()`, `setItem` → leer:
  `takeBattery()`, sonst `insertBattery(type, Batteries.charge(stack))`), `getMaxStackSize` 1. Client:
  `SimpleContainer(1)`.
- Slot 0 `mayPlace`: `Batteries.isBattery(stack) && BatteryFit.allowed(BatteryFit.check(frame, type))`; `frame` auf dem
  Client aus der Entity (`level.getEntity(id)`), ohne Entity → nichts erlaubt.
- `quickMoveStack`: Inventar → Slot 0 (wenn leer und passend), Slot 0 → Inventar.
- `stillValid`: Drohne vorhanden, nicht entfernt, `!batteryLocked()`, Abstand ≤ 8 Blöcke. Wird die Drohne gesteuert
  oder scharf, schließt das Menü.
- Layout (Textur `propwash:textures/gui/drone_battery.png`, Panel **176 × 166**):

| Element | Koordinaten (Panel) |
|---|---|
| Titelleiste | 2, 2 – 174, 15; Titel bei 8, 6 |
| Drohnen-Vorschau (Code: `DronePreview.render`, langsam drehend) | 7, 17 – 89, 71 |
| Akku-Slot | **x 116, y 22** |
| Info-Feld (Code-Text) | 94, 42 – 169, 71: Zeile 1 Akku-Name oder „Kein Akku“ (rot), Zeile 2 „74 % · 23,4 V“, Zeile 3 Balken 70 × 3 |
| Inventar-Beschriftung | 8, 73 |
| Inventar | 8 + 18·Spalte, 84 + 18·Zeile |
| Schnellleiste | 8 + 18·Spalte, 142 |

  Tooltip über dem Info-Feld: „Passende Akkus:“ + Liste aus `BatteryFit.fitting(frame)` (schwache mit „(schwach)“).
  Hält der Spieler einen unpassenden Akku am Mauszeiger: rote Zeile mit dem Grund aus §3 unter dem Info-Feld.

### 8.2 Ladegerät (`content.BatteryChargerMenu`, `content.client.BatteryChargerScreen`)

- Menütyp `ContentBlocks.BATTERY_CHARGER_MENU = PropwashRegistries.menu("battery_charger", BatteryChargerMenu::new)`,
  Titel `container.propwash.battery_charger`. `ContainerData` mit 5 Werten: 0–3 Restsekunden je Slot (−1 = kein Akku
  oder voll), 4 = Redstone (0/1).
- Slots nur Akkus, max. 1; `quickMoveStack` Inventar ↔ erster freier Schacht.
- Layout (Textur `propwash:textures/gui/battery_charger.png`, Panel **176 × 186**):

| Element | Koordinaten (Panel) |
|---|---|
| Titelleiste | 2, 2 – 174, 15; Titel bei 8, 6; Redstone-Anzeige (Code) bei 160, 5 (8 × 6, rot = an, dunkel = aus) |
| Ladeschacht-Rahmen (Textur) | je Schacht `(x − 5, 19) – (x + 21, 85)` |
| Akku-Slots | **x = 26, 62, 98, 134; y = 30** |
| Ladebalken (Code, senkrecht) | je Schacht `(x + 2, 52) – (x + 14, 76)`, von unten gefüllt; Farbe wie Ladebalken §7.2 |
| Prozent/Restzeit (Code) | zentriert unter dem Schacht bei y 79 (klein, `74%`), Tooltip über dem Schacht: „Lädt – noch %s s“ / „Voll“ / „leer“ |
| Inventar-Beschriftung | 8, 93 |
| Inventar | 8 + 18·Spalte, 104 + 18·Zeile |
| Schnellleiste | 8 + 18·Spalte, 162 |

---

## 9. Netzwerk

- **Keine neuen Payloads.** Start über das Vanilla-Paket „Item benutzen“ (`RemoteItem#use`), Modus über
  Entity-Daten (`DATA_PILOT_MODE`), Menüs über Vanilla-Menü-Sync, Bindungen über Item-Components.
- `PilotRequestC2S`: nur noch Stopp (`start == false`); `start == true` wird ignoriert.
- Server-Validierung neu: Interaktionen (§4) nur Hauptthread, Haupthand, Abstand (Vanilla-Interaktionsreichweite +
  `PICKUP_REACH` für Aufheben), `batteryLocked`; Menü-Aktionen über `stillValid`; `useRemote` (§5.1) mit Entprellung;
  Tick-Prüfungen §5.2/§5.3.

---

## 10. Funke (`propwash:remote`)

### 10.1 Item

- `drone.RemoteItem extends Item`, registriert in `DroneModule`:
  `REMOTE = PropwashRegistries.item("remote", RemoteItem::new, p -> p.stacksTo(1))`. Id in `ContentIds.REMOTE`
  (`Constants.id("remote")`); `ContentIds.isRemote(ItemStack)`, `ContentIds.boundDrone(ItemStack) →
  Optional<BoundDrone>`.
- `use` nach §4.2; `appendHoverText` nach §12.
- Keine Haltbarkeit, keine Components außer `bound_drone`.

### 10.2 Animierte Gimbals (Special-Item-Model-Renderer, 26.3)

Machbar mit Vanilla-Mitteln (geprüft): Item-Definitionen kennen `minecraft:special` mit `base` + `model`
(`.ref/vanilla/.../client/renderer/item/SpecialModelWrapper.java:77-81`) und `minecraft:composite` mit `models`
(`CompositeModel.java:45-47`); eigene Typen landen in `SpecialModelRenderers.ID_MAPPER`
(`.ref/vanilla/.../client/renderer/special/SpecialModelRenderers.java:12`), NeoForge über
`RegisterSpecialModelRendererEvent#register(Identifier, MapCodec)`, Fabric über den von fabric-api transitiv
zugänglich gemachten `ID_MAPPER` (`fabric-transitive-access-wideners-v1.classtweaker:53`). Vorbild:
`TridentSpecialRenderer` (Modell per `context.entityModelSet().bakeLayer(...)`, Zeichnen mit
`submitNodeCollector.submitModel(model, state, poseStack, texture, light, overlay, outline)`).

- **Foundation-Erweiterung** (vom Code-Agenten mit umzusetzen): `ClientRegistrar.specialModelRenderer(Identifier id,
  MapCodec<? extends SpecialModelRenderer.Unbaked<?>> codec)` (nur Client-`init()`), Glue: NeoForge im
  `RegisterSpecialModelRendererEvent`, Fabric im Client-Entrypoint `SpecialModelRenderers.ID_MAPPER.put(id, codec)`.
  FOUNDATION §8 ergänzen.
- `drone.client.render.RemoteGimbalRenderer implements SpecialModelRenderer<RemoteGimbalRenderer.Pose>`,
  Typ-Id **`propwash:remote_gimbals`**, `Unbaked` als `MapCodec.unit(...)`.
  - `record Pose(float leftX, float leftY, float rightX, float rightY)` (−1..1, auf 1/32 quantisiert, damit die
    Modell-Identität nicht jeden Frame wechselt). Seit 0.5.0 zusätzlich `Controls` (vier Schalter, drei Tasten) für den
    Empfänger-Assistenten; der Vier-Werte-Konstruktor setzt `Controls.NEUTRAL` (Schalter senkrecht, Tasten oben), die
    Schalter liegen nicht mehr in `remote_body.json` (`docs/ASSETS.md` „Funke“).
  - `extractArgument(ItemStack stack)`: nur wenn `mc.player != null` und `stack == mc.player.getMainHandItem()`
    (Identität, also die Funke in **der eigenen** Hand — erste Person, dritte Person und der eingefrorene Körper im
    FPV) **und** eine Funke/ein Gamepad verbunden ist (`RadioState.INSTANCE.connected()`): Mode 2 —
    links X = Yaw, links Y = Gas (0..1 → −1..1), rechts X = Roll, rechts Y = Pitch (`StickPositions.pitch` +1 = Knüppel nach
    vorn = nach oben im Modell; korrigiert, vorher stand hier „−Pitch“ im Widerspruch zur Klammer); bei aktivem Rescue
    `RescueState.commandedSticks()`. Sonst `null` → Knüppel mittig, Gas unten.
  - Geometrie: eigenes `LayerDefinition` `drone.client.render.RemoteGimbalModel.LAYER`
    (`ClientRegistrar.modelLayer`), Textur **`propwash:textures/entity/remote/gimbal.png`** (§11). Je Gimbal ein
    Knüppel: Schaft (Würfel 0,5 × 0,5 × 2,25 px, `texOffs(0, 0)`, UV nur in Zeilen 0–7) und Kappe
    (1,0 × 1,0 × 0,75 px am Ende, `texOffs(0, 8)`, UV nur in Zeilen 8–15). Drehpunkte = §11.2
    (Modellpixel / 16). Die Knüppel ragen entlang +Z aus der Frontplatte; Kippung max. ±20°: Knüppel-Y (auf/ab im
    Modell) = Drehung um die X-Achse, Knüppel-X (links/rechts) = Drehung um die Y-Achse, jeweils um den Drehpunkt.
  - `getExtents`: Extents beider Knüppel in Neutralstellung.
- Ohne verbundene Funke oder in GUI/Boden/Rahmen zeigt das statische Modell mittige Knüppel (§11.2, Item-Definition).
- **Dritte Person:** Vanilla hält Items in einer Hand. Beidhändiges Halten (eigene `ArmPose`) ist loaderspezifisch
  und **nicht** Teil dieser Stufe (§19).

---

## 11. Asset-Vertrag (für den Asset-Agenten)

Alles unter `common/src/main/resources/assets/propwash/`. Erzeugt vom bestehenden Welt-Asset-Generator
(`tools/assets/world/generate.py`, Quellen in `assets-src/world/`), geprüft von `tools/assets/world/validate.py`,
`common/src/test/java/de/lardtom/propwash/assets/world/WorldAssetsTest.java` und den Selbsttest-Schritten in
`assets.world.client.WorldAssetsSelfTests` (bestehende Mechanik, `docs/ASSETS.md` „Welt-Assets“). Dateien nie von Hand
bearbeiten. Palette wie die übrigen Welt-Assets: Anthrazit `#2B2F36`/`#1B1E23`, Metall `#8C949E`, Amber `#FFBB00`,
LCD `#0E2A24` mit Pixeln `#7CFFB2`, LED gelb `#FFB000`, LED grün `#3BFF6A`, LED rot `#FF3B3B`.
Der Asset-Agent ändert **keinen** Java-Code außer `assets/world`-Paket (Selbsttests) und dem JUnit
`WorldAssetsTest`.

### 11.1 Dateiliste

| Asset | Dateien |
|---|---|
| Funke – Item-Definition | `items/remote.json` (Aufbau §11.3) |
| Funke – Modelle | `models/item/remote.json` (vollständig, mit mittigen Knüppeln), `models/item/remote_body.json` (identisch, **ohne** die zwei Knüppel-Elementgruppen), beide mit identischem `display`-Block |
| Funke – Texturen | `textures/item/remote.png` (64 × 64, `texture_size` im Modell), `textures/entity/remote/gimbal.png` (16 × 16, §11.2) |
| Ladegerät – Blockstate | `blockstates/battery_charger.json`: Varianten `facing=north|east|south|west` × `state=idle|charging|done` → Modell `propwash:block/battery_charger_<state>`, `y` = 0/90/180/270 für north/east/south/west |
| Ladegerät – Modelle | `models/block/battery_charger_idle.json`, `…_charging.json`, `…_done.json` (gleiche Geometrie, andere LED-Texturen; LED-Flächen in `charging`/`done` mit `light_emission` 12) |
| Ladegerät – Texturen | `textures/block/battery_charger_top.png`, `…_side.png`, `…_bottom.png`, `…_front_idle.png`, `…_front_charging.png`, `…_front_done.png` (je 32 × 32) |
| Ladegerät – Item | `items/battery_charger.json` → `minecraft:model` `propwash:block/battery_charger_idle` |
| GUI Ladegerät | `textures/gui/battery_charger.png` 256 × 256, Panel (0, 0)–(176, 186), Layout §8.2 |
| GUI Akku-Menü | `textures/gui/drone_battery.png` 256 × 256, Panel (0, 0)–(176, 166), Layout §8.1 |
| Akkus | **keine Änderung** an `part/battery/*` und `items/b*.json` (Ladebalken zeichnet Vanilla aus `Item#getBar*`) |

### 11.2 Funke — Geometrie (Modellpixel, 16 = 1 Block)

Front (Display, Gimbals) zeigt nach **+Z** (Süden), Antenne nach +Y. Alle Maße `from`/`to`:

| Teil | from | to | Hinweis |
|---|---|---|---|
| Gehäuse | (1.5, 3, 7) | (14.5, 10, 10) | Anthrazit, leicht abgesetzte Kanten per Textur |
| Griff links | (1.5, 0.5, 7) | (5, 3, 10) | gummiert (dunkler) |
| Griff rechts | (11, 0.5, 7) | (14.5, 3, 10) | dito |
| Display | (6, 6.5, 10) | (10, 9.5, 10.1) | LCD, Fläche `south` mit `light_emission` 6 |
| Gimbal-Teller links | (2.25, 4.75, 10) | (5.75, 8.25, 10.25) | Amber-Ring um Metallfläche |
| Gimbal-Teller rechts | (10.25, 4.75, 10) | (13.75, 8.25, 10.25) | dito |
| Knüppel links (nur `remote.json`) | Schaft (3.75, 6.25, 10.25)–(4.25, 6.75, 12.5), Kappe (3.5, 6, 12.5)–(4.5, 7, 13.25) | | Gruppe `gimbal_left` |
| Knüppel rechts (nur `remote.json`) | Schaft (11.75, 6.25, 10.25)–(12.25, 6.75, 12.5), Kappe (11.5, 6, 12.5)–(12.5, 7, 13.25) | | Gruppe `gimbal_right` |
| Tragegriff | (5, 10, 8) | (11, 11.5, 9) | Metall |
| Schalter (4 ×) | je 0,5 × 1 × 0,5 bei x = 2.5, 4, 12, 13.5; y 10–11; z 8.25–8.75 | | Amber-Spitzen |
| Antenne | (12.75, 10, 7.75) | (13.75, 15, 8.75) | dunkel, Gelenk bei y 10–11 |

**Gimbal-Drehpunkte (für den Code-Renderer):** links **(4, 6.5, 10.25)**, rechts **(12, 6.5, 10.25)** (Mitte der
Tellerfläche). Die Knüppel im statischen Modell sitzen genau dort (Neutralstellung).

`textures/entity/remote/gimbal.png` (16 × 16): Zeilen 0–7 Schaft (Metall `#8C949E`, 1 px dunklere Kante links/rechts),
Zeilen 8–15 Kappe (Gummi `#1B1E23`, Zeile 8 Amber `#FFBB00` als Ring). Der Code benutzt nur diese beiden Bänder.

`display` (in beiden Modellen gleich; Werte tunt der Asset-Agent im Vorschau-Renderer, Anforderungen verbindlich):
- `firstperson_righthand`: Funke unten mittig-rechts, Front ca. 35° zur Kamera gekippt, Breite etwa ein Viertel der
  Bildschirmbreite, Knüppel sichtbar; `firstperson_lefthand` gespiegelt.
- `thirdperson_righthand`/`lefthand`: vor der Brust gehalten, Front nach oben/zum Spieler, Breite ≈ 0,45 Blöcke.
  Nachtrag 2026-09-24: Hält ein Spieler die Funke bei freier anderer Hand, zeichnet drone sie selbst mittig zwischen
  beiden Händen (`drone.client.RemoteHoldPose`, Werte in `docs/ASSETS.md`); das Display gilt dann nur noch für das Halten
  mit einer Hand (andere Hand belegt, Rüstungsständer, Mobs).
- `gui`: Frontansicht leicht von oben (Display + beide Gimbals klar erkennbar), füllt ≈ 90 % des Slots.
- `ground`, `fixed`, `head` sinnvoll (`head` wie Vanilla-Blöcke verkleinert).

### 11.3 Funke — Item-Definition `items/remote.json`

```json
{
  "model": {
    "type": "minecraft:select",
    "property": "minecraft:display_context",
    "cases": [
      { "when": ["gui", "ground", "fixed", "on_shelf"],
        "model": { "type": "minecraft:model", "model": "propwash:item/remote" } }
    ],
    "fallback": {
      "type": "minecraft:composite",
      "models": [
        { "type": "minecraft:model", "model": "propwash:item/remote_body" },
        { "type": "minecraft:special", "base": "propwash:item/remote_body",
          "model": { "type": "propwash:remote_gimbals" } }
      ]
    }
  }
}
```

Der Typ `propwash:remote_gimbals` wird vom Code-Agenten registriert (§10.2). Validator: Modelle vorhanden, Texturen im
Item-Atlas (`textures/item/…`), Gimbal-Textur 16 × 16, Drehpunkte liegen auf den Tellerflächen, `remote_body` enthält
keine Elemente in den Knüppel-Boxen.

### 11.4 Ladegerät — Geometrie (Modellpixel, Ausrichtung Nord = Front zeigt nach −Z)

| Teil | from | to |
|---|---|---|
| Gehäuse | (1, 0, 2) | (15, 7, 14) |
| Deckel mit 4 Schächten (Aussparungen 2,5 × 3 × 5 px, Mitten bei x = 3.5, 6.5, 9.5, 12.5; z 5–11) | (1, 7, 2) | (15, 8, 14) |
| Frontblende | (1.5, 1, 1.5) | (14.5, 6, 2) |
| 4 LED-Fenster in der Blende (je 1,5 × 1 px, Mitten x = 3.5, 6.5, 9.5, 12.5; y 4–5) | auf der Blende, z 1.4–1.5 | |
| Mini-Display rechts unten (von vorn gesehen; entschieden in Welle 2b) | (2, 1.5, 1.4) | (7, 3, 1.5) |

- `idle`: LEDs dunkelgrau, Display aus. `charging`: LEDs gelb `#FFB000`, Display amber, `light_emission` 12.
  `done`: LEDs grün `#3BFF6A`, Display grün, `light_emission` 12.
- Die Code-Form `Block.box(1, 0, 2, 15, 8, 14)` muss die Bounding-Box des Modells (ohne Blende, die 0,5 px vorsteht)
  abdecken; Validator prüft Bounding-Box ⊆ (1, 0, 1.4)–(15, 8, 14).
- `display` wie Vanilla-Block-Items (erbt z. B. `minecraft:block/block`).

### 11.5 GUI-Texturen

- `battery_charger.png`: Panel 176 × 186 im Stil der Werkbank; Titelleiste; vier Ladeschächte (Rahmen
  `(x − 5, 19) – (x + 21, 85)` für x = 26, 62, 98, 134) mit Slot-Rahmen 18 × 18 bei `(x − 1, 29)`, darin das
  Slot-Symbol-Motiv (Akku, `#555555`), darunter eine **leere** Balkenrinne `(x + 1, 51) – (x + 15, 77)` (1 px Rahmen,
  der Code füllt innen); Inventar- und Schnellleisten-Slots an den Positionen §8.2; Beschriftungsflächen frei lassen.
- `drone_battery.png`: Panel 176 × 166; Titelleiste; Vorschaufeld 7, 17 – 89, 71 (Verlauf mit Bodenraster wie die
  Werkbank-Vorschau); Akku-Slot-Rahmen bei 115, 21 mit Amber-Ring; Info-Feld 94, 42 – 169, 71 (dunkel, 1 px Amber-Linie
  oben); Inventar/Schnellleiste §8.1.
- Beide: Rest der 256 × 256 transparent.

### 11.6 Übergabe

Der Asset-Agent ergänzt `docs/ASSETS.md` um die Abschnitte „Funke“, „Ladegerät“, „GUI Ladegerät/Akku-Menü“ (Werte aus
diesem Kapitel) und `tools/assets/world/preview.py` um Funke und Ladegerät (`docs/asset-sheets/world_preview.png`).
Selbsttest-Schritt(e) in `WorldAssetsSelfTests`: Item-Modelle `remote`/`remote_body` gebacken, Blockmodelle aller
6 Blockzustände × 4 Richtungen vorhanden, GUI-Texturen geladen, Gimbal-Textur vorhanden.

---

## 12. Shift-Tooltips

### 12.1 System

- `content.TooltipShift` (Common): `static volatile BooleanSupplier SHIFT = () -> false;`, `static boolean down()`,
  `static void override(@Nullable Boolean)` (für Selbsttests). `content.client.ContentClient.init()` setzt
  `SHIFT = () -> Minecraft.getInstance().hasShiftDown()` (`.ref/vanilla/.../client/Minecraft.java:766`).
- `content.ItemHelp` (Common, nur Aufbau von Components):
  - `static void append(String id, Consumer<Component> out, List<Component> status, Consumer<Consumer<Component>> fits,
    Consumer<Consumer<Component>> specs)` mit fester Reihenfolge:
    - **ohne Shift:** `propwash.help.<id>.summary` (grau) · Statuszeilen (farbig, falls vorhanden) · `propwash.help.shift`
      (dunkelgrau).
    - **mit Shift:** Kurzzeile · Statuszeilen · Leerzeile · `…what.1..n` (grau) · Überschrift
      `propwash.help.section.use` (gold) + `…use.1..n` (grau) · Überschrift `propwash.help.section.fits` + Kompatibilität
      (nur wenn nicht leer) · Überschrift `propwash.help.section.specs` + Kennwerte (nur wenn nicht leer).
  - Nummerierte Zeilen: `…what.1`, `…what.2`, … bis der Schlüssel fehlt (`Language.getInstance().has(key)`,
    `.ref/vanilla/.../locale/Language.java:86/100`), höchstens 8.
  - Teile benutzen `propwash.help.category.<kategorie>.what/use` statt eigener Absätze; die Kurzzeile ist je Teil.
  - Zeilen ≤ 48 Zeichen (Tooltips brechen nicht um).
- Alle Items verwenden das System: `PartItem`/`BatteryItem`, `QuadcopterItem`, `RemoteItem`, `FpvGogglesItem`,
  Werkbank- und Ladegerät-BlockItem (neue Klasse `content.HelpBlockItem extends BlockItem`; dafür Foundation-Erweiterung
  `PropwashRegistries.blockItem(block, BiFunction<Block, Item.Properties, BlockItem>, props)` bzw. passende
  `blockWithItem`-Überladung), race `GateItem`/`CourseToolItem` (rufen `ItemHelp`).
- Die bisherigen Einzelzeilen (`PartTooltips.append`, `QuadcopterItem`-Zeilen, `propwash.race.gate.tooltip.size` usw.)
  wandern in den Abschnitt „Daten“ bzw. werden Status/Kurzzeile. Entfallen: `propwash.drone.tooltip.hint`,
  `propwash.content.goggles.wear_hint`, `propwash.race.gate.tooltip.place` (ersetzt durch „Benutzung“).

### 12.2 Allgemeine Schlüssel

| Schlüssel | DE | EN |
|---|---|---|
| `propwash.help.shift` | Shift gedrückt halten: Details | Hold Shift for details |
| `propwash.help.section.use` | Benutzung | How to use |
| `propwash.help.section.fits` | Passt zu | Compatible with |
| `propwash.help.section.specs` | Daten | Specs |
| `propwash.help.status.bound` | Verbunden mit: %s | Bound to: %s |
| `propwash.help.status.unbound` | Nicht verbunden | Not bound |
| `propwash.help.status.charge` | Ladung: %s %% · %s V | Charge: %s%% · %s V |
| `propwash.help.status.no_battery` | Kein Akku eingesetzt | No battery installed |
| `propwash.help.fits.batteries` | Akkus: %s | Batteries: %s |
| `propwash.help.fits.weak` | Schwach: %s | Weak: %s |
| `propwash.help.fits.frames` | Rahmen: %s | Frames: %s |
| `propwash.help.fits.goggles` | Brillen: %s | Goggles: %s |

### 12.3 Kategorien (Teile)

| Schlüssel | DE | EN |
|---|---|---|
| `propwash.help.category.frame.what.1` | Das Grundgerüst der Drohne. Bestimmt Größe, | The backbone of the drone. Sets its size, |
| `…frame.what.2` | Motor- und Stack-Aufnahme und die | motor and stack mounts and the largest |
| `…frame.what.3` | größten Props – und welche Akkus passen. | props – and which batteries fit. |
| `…frame.use.1` | Drohnen-Werkbank: in den mittleren Slot. | Drone workbench: centre slot. |
| `…frame.use.2` | Ein Name am Rahmen wird zum Drohnennamen. | A named frame names the drone. |
| `propwash.help.category.stack.what.1` | Flight-Controller und Motorregler (ESC). | Flight controller and speed controllers. |
| `…stack.what.2` | Muss zum Rahmen und zur Akkuspannung passen. | Must fit the frame and battery voltage. |
| `…stack.use.1` | Werkbank: Slot über dem Rahmen. | Workbench: slot above the frame. |
| `…stack.use.2` | Beim Zerlegen behält er das Tuning. | Keeps the tune when you take a drone apart. |
| `propwash.help.category.motor.what.1` | Vier gleiche Motoren treiben die Props an. | Four identical motors spin the props. |
| `…motor.what.2` | KV × Spannung ergibt die Drehzahl. | KV × voltage gives the RPM. |
| `…motor.use.1` | Werkbank: alle vier Motor-Slots füllen. | Workbench: fill all four motor slots. |
| `propwash.help.category.prop.what.1` | Vier gleiche Propeller. Größer heißt mehr | Four identical propellers. Bigger means |
| `…prop.what.2` | Schub, aber der Rahmen begrenzt die Größe. | more thrust, but the frame limits the size. |
| `…prop.use.1` | Werkbank: alle vier Prop-Slots füllen. | Workbench: fill all four prop slots. |
| `propwash.help.category.battery.what.1` | Energie für die Drohne. Jeder Akku hat | Powers the drone. Every pack keeps its |
| `…battery.what.2` | seinen eigenen Ladestand und lädt sich nur | own charge and only recharges in a |
| `…battery.what.3` | im Akku-Ladegerät wieder auf. | battery charger. |
| `…battery.use.1` | Einsetzen: Schleichen + Rechtsklick auf | Install: sneak + right-click the drone; |
| `…battery.use.2` | die Drohne, mit Akku in der Hand sofort. | with a pack in hand it swaps at once. |
| `…battery.use.3` | Laden: ins Akku-Ladegerät legen. | Charge: put it into a battery charger. |
| `propwash.help.category.video.what.1` | Kamera und Videosender. Bestimmt Bild, | Camera and video transmitter. Sets image, |
| `…video.what.2` | Latenz, Reichweite und passende Brille. | latency, range and matching goggles. |
| `…video.use.1` | Werkbank: Slot an der Nase. | Workbench: slot at the nose. |
| `…video.use.2` | Analog → analoge Brille, digital → digitale. | Analog → analog goggles, digital → digital. |

Kompatibilität je Kategorie (Code): Rahmen → `propwash.help.fits.batteries` mit `BatteryFit.fitting(frame)` (schwache
extra mit `fits.weak`); Stack/Motor/Prop → bestehendes `PartTooltips.fits` (Rahmenliste); Akku →
`fits.frames` mit OK-Rahmen, `fits.weak` mit WEAK-Rahmen; Video → `fits.goggles` mit den Brillen, deren System
empfängt (Kreativ-Brille immer dabei). Kennwerte: bestehende Zeilen aus `PartTooltips.append` (ohne die Kategoriezeile,
die steckt jetzt in der Kurzzeile).

### 12.4 Kurzzeilen der Teile (`propwash.help.<key>.summary`)

| Key | DE | EN |
|---|---|---|
| `whoop_65` | 65-mm-Whoop mit Schutzringen – für drinnen | 65 mm whoop with ducts – made for indoors |
| `cinewhoop_3` | 3"-Cinewhoop mit Schutzringen – ruhig | 3" cinewhoop with ducts – smooth |
| `freestyle_5` | 5"-Freestyle-Rahmen – der Allrounder | 5" freestyle frame – the all-rounder |
| `race_5` | Leichter 5"-Race-Rahmen – schnell, direkt | Light 5" race frame – fast and direct |
| `long_range_7` | 7"-Long-Range-Rahmen – weit und sparsam | 7" long-range frame – far and efficient |
| `aio_whoop` | AIO-Board für Whoops (1S–2S) | AIO board for whoops (1S–2S) |
| `aio_20` | 20×20-AIO für 3"-Drohnen (2S–6S) | 20×20 AIO for 3" drones (2S–6S) |
| `stack_30` | 30×30-Stack: FC + 4-in-1-ESC (3S–6S) | 30×30 stack: FC + 4-in-1 ESC (3S–6S) |
| `m0802_22000` | 0802-Motor, 22000 KV – für Whoops | 0802 motor, 22000 KV – for whoops |
| `m1404_4600` | 1404-Motor, 4600 KV – für 3"-Drohnen | 1404 motor, 4600 KV – for 3" drones |
| `m2207_1750` | 2207-Motor, 1750 KV – Freestyle auf 6S | 2207 motor, 1750 KV – 6S freestyle |
| `m2306_1860` | 2306,5-Motor, 1860 KV – Race auf 6S | 2306.5 motor, 1860 KV – 6S racing |
| `m2807_1300` | 2807-Motor, 1300 KV – für 7" Long Range | 2807 motor, 1300 KV – 7" long range |
| `p31_4b` | 31-mm-Vierblatt für Whoops | 31 mm quad-blade for whoops |
| `p3_3b` | 3"-Dreiblatt für Cinewhoops | 3" tri-blade for cinewhoops |
| `p51_3b` | 5,1"-Dreiblatt – Standard für 5" | 5.1" tri-blade – the 5" standard |
| `p7_2b` | 7"-Zweiblatt – sparsam für Long Range | 7" two-blade – efficient long range |
| `b1s_300_hv` | 1S-LiHV-Akku, 300 mAh – nur für Whoops | 1S LiHV pack, 300 mAh – whoops only |
| `b4s_650` | 4S-LiPo, 650 mAh – für Cinewhoops | 4S LiPo, 650 mAh – for cinewhoops |
| `b6s_1100` | 6S-LiPo, 1100 mAh – leicht, für Race | 6S LiPo, 1100 mAh – light, for racing |
| `b6s_1300` | 6S-LiPo, 1300 mAh – Freestyle-Standard | 6S LiPo, 1300 mAh – freestyle standard |
| `b6s_4000_lion` | 6S-Li-Ion, 4000 mAh – Ausdauer für 7" | 6S Li-ion, 4000 mAh – endurance for 7" |
| `analog_nano` | Analoge Nano-Kamera – leicht, kaum Latenz | Analog nano camera – light, low latency |
| `analog_race` | Analoges Race-Video – schnell, robust | Analog race video – fast and robust |
| `digital_lite` | Digitales Lite-Video – klares Bild, leicht | Digital lite video – clear and light |
| `digital_hd` | Digitales HD-Video – bestes Bild | Digital HD video – best image |

Akku-Status (immer, auch ohne Shift): `propwash.help.status.charge` mit Prozent (0 Nachkommastellen) und
`Batteries.openCircuitVoltage` (1 Nachkommastelle, `Numbers.of`), Farbe grün/gelb/rot wie der Balken.

### 12.5 Übrige Items

`propwash.help.<id>.summary / .what.N / .use.N` (die Kompatibilität/Daten macht der Code wie angegeben).

**`remote`** — Status: `status.bound` mit `BoundDrone.displayName()` + „(Rahmen-Kurzname)“ bzw. `status.unbound`.

| Schlüssel | DE | EN |
|---|---|---|
| summary | Funke, immer mit einer Drohne verbunden | Radio transmitter, bound to one drone |
| what.1 | Knüppel und Schalter folgen deinen Eingaben. | Its sticks and switches mirror your inputs. |
| use.1 | Verbinden: Drohne mit der Funke anklicken. | Bind: click a drone with the radio. |
| use.2 | Fliegen: Rechtsklick in die Luft. | Fly: right-click into the air. |
| use.3 | Verbundene Brille auf: FPV, sonst Sichtflug. | Bound goggles on: FPV, else line of sight. |
| use.4 | Beenden: nochmal Rechtsklick oder %s. | Stop: right-click again or %s. |
| use.5 | Schleichen + Rechtsklick: Verbindungsstatus. | Sneak + right-click: link status. |

Nur eine `what`-Zeile und kein Abschnitt „Passt zu“ (die Kurzzeile sagt schon alles, die Funke passt zu jeder Drohne;
Integration Welle 2b: der Shift-Tooltip war mit 17 Zeilen zu lang). `use.4` bekommt
`Component.keybind("key.propwash.drone_exit")` als Argument. Daten: „Funkreichweite: ca. %s Blöcke“ / „Radio range: about %s blocks“
(`propwash.help.remote.range`, Wert `RadioLink.displayRange()` = `min(START_RANGE, maxPilotDistance des Servers)`; der
Standard-Server erlaubt nur 160 Blöcke, 800 wären im Tooltip irreführend).

**Brillen** (`goggles_analog`, `goggles_digital`, `goggles_creative`) — Status: Bindung wie bei der Funke.

| Schlüssel | DE | EN |
|---|---|---|
| `goggles_analog.summary` | Analoge FPV-Brille | Analog FPV goggles |
| `goggles_digital.summary` | Digitale FPV-Brille | Digital FPV goggles |
| `goggles_creative.summary` | Kreativ-FPV-Brille – empfängt alles | Creative FPV goggles – receive everything |
| `goggles.what.1` (gemeinsam, Schlüssel `propwash.help.goggles.what.1`) | Zeigt das Kamerabild der Drohne – so | Shows the drone's camera feed – you fly |
| `goggles.what.2` | fliegst du aus Sicht der Drohne (FPV). | from the drone's point of view (FPV). |
| `goggles.use.1` | Verbinden: Brille in die Hand nehmen und | Bind: hold the goggles and click the |
| `goggles.use.2` | die Drohne anklicken. | drone. |
| `goggles.use.3` | Aufsetzen (Kopf-Slot), dann mit der | Wear them (head slot), then fly with the |
| `goggles.use.4` | verbundenen Funke starten → FPV. | bound radio → FPV. |

Danach die bestehende Zeile `propwash.content.goggles.<system>.description` als drittes „what“ (dunkelgrau).
Kompatibilität: bestehende `propwash.content.goggles.compatible`-Zeilen je `VideoType`; Kreativ:
`propwash.help.goggles_creative.fits` = „Jede Drohne, jedes Videosystem“ / „Any drone, any video system“, plus die
bestehende Zeile `creative_only`.

**`drone`** — Status: Akku (`status.charge` mit Akkuname davor: `propwash.drone.tooltip.battery_type` = „Akku: %s ·
%s %%“ / „Battery: %s · %s%%“, oder `status.no_battery`), ggf. „Eigenes Tuning (%s Werte)“ (bestehend).

| Schlüssel | DE | EN |
|---|---|---|
| summary | %s – flugbereite Drohne (Arg.: Rahmen-Kurzname bzw. eigener Name) | %s – ready-to-fly drone |
| what.1 | Eine fertige Drohne. Aufstellen, Funke und | A finished drone. Place it, bind radio |
| what.2 | Brille verbinden, losfliegen. | and goggles, take off. |
| use.1 | Aufstellen: Rechtsklick auf einen Block. | Place: right-click a block. |
| use.2 | Aufheben: mit leerer Hand anklicken. | Pick up: click it with an empty hand. |
| use.3 | Akku tauschen: Schleichen + Rechtsklick. | Swap battery: sneak + right-click. |
| use.4 | Funke/Brille verbinden: damit anklicken. | Bind radio/goggles: click with them. |
| use.5 | Zerlegen: in der Drohnen-Werkbank. | Take apart: in the drone workbench. |

Kompatibilität: `fits.batteries` für den Rahmen. Daten: bestehende Zeilen `propwash.drone.tooltip.frame/power/
electronics/video/stats/not_buildable` (+ Problemliste); ohne Akku Masse ohne Akku und keine Flugzeit.

**`drone_workbench`**

| Schlüssel | DE | EN |
|---|---|---|
| summary | Baut Drohnen aus Bauteilen – und zerlegt sie | Builds drones from parts – and takes them apart |
| what.1 | Rahmen, Stack, Motoren, Props, Video und | Frame, stack, motors, props, video and |
| what.2 | Akku werden hier zu einer Drohne. | battery become a drone here. |
| use.1 | Rechtsklick öffnet die Werkbank. | Right-click to open the workbench. |
| use.2 | Alle Slots füllen, Drohne rechts entnehmen. | Fill every slot, take the drone on the right. |
| use.3 | Drohne in den Zerlege-Slot: zurück in Teile. | Drone into the salvage slot: back to parts. |
| fits (`propwash.help.drone_workbench.fits`) | Der Akku muss zum Rahmen passen. | The battery must fit the frame. |

**`battery_charger`**

| Schlüssel | DE | EN |
|---|---|---|
| summary | Lädt bis zu vier Akkus | Charges up to four batteries |
| what.1 | Akkus laden sich nur hier. Ein voller Pack | Batteries only charge here. A full pack |
| what.2 | braucht etwa 1–3 Minuten, je nach Größe. | takes about 1–3 minutes, by size. |
| use.1 | Rechtsklick: Ladegerät öffnen. | Right-click: open the charger. |
| use.2 | Mit Akku in der Hand: direkt einlegen. | Holding a battery: put it straight in. |
| use.3 | Redstone-Signal: schneller laden. | Redstone signal: faster charging. |
| use.4 | Gelb = lädt, grün = alle voll. | Yellow = charging, green = all full. |
| fits | Alle Propwash-Akkus | All Propwash batteries |

**Gates** (`gate_small`, `gate_medium`, `gate_large`; gemeinsame Absätze unter `propwash.help.gate.*`, Kurzzeile je Größe)

| Schlüssel | DE | EN |
|---|---|---|
| `gate_small.summary` | Kleines Renn-Gate (3 × 3 Blöcke) | Small race gate (3 × 3 blocks) |
| `gate_medium.summary` | Mittleres Renn-Gate (4 × 4 Blöcke) | Medium race gate (4 × 4 blocks) |
| `gate_large.summary` | Großes Renn-Gate (6 × 4 Blöcke) | Large race gate (6 × 4 blocks) |
| `gate.what.1` | Ein Tor für Rennstrecken. Das nächste Gate | A gate for race tracks. Your next gate |
| `gate.what.2` | deines Rennens leuchtet grün. | lights up green. |
| `gate.use.1` | Rechtsklick auf einen Block: aufstellen, | Right-click a block: place it, facing |
| `gate.use.2` | Öffnung in Blickrichtung. | where you look. |
| `gate.use.3` | Mit dem Kurs-Werkzeug zum Kurs verbinden. | Link gates into a course with the course tool. |
| `gate.use.4` | Abbauen: Linksklick. | Remove: left-click. |

Daten: bestehende `propwash.race.gate.tooltip.size`.

**`course_tool`** — Status: bestehende Zeile `propwash.race.tool.tooltip.course`/`none`.

| Schlüssel | DE | EN |
|---|---|---|
| summary | Verbindet Gates zu einer Rennstrecke | Links gates into a race course |
| what.1 | Legt fest, in welcher Reihenfolge die | Sets the order in which the gates |
| what.2 | Gates durchflogen werden. | must be flown. |
| use.1–3 | bestehende Zeilen `propwash.race.tool.tooltip.click`, `sneak_gate`, `sneak_air` (werden als `use`-Zeilen angezeigt) | |
| use.4 (`propwash.help.course_tool.use.4`) | Rennen: nahe Gate 1 scharf schalten. | Race: arm near gate 1. |

---

## 13. Weitere Sprachschlüssel

Quelle: `common/src/lang/<modul>/{de_de,en_us}.json` (danach `mergeLang` im Integrationslauf; lokal die generierten
Dateien nicht anfassen). drone:

| Schlüssel | DE | EN |
|---|---|---|
| `item.propwash.remote` | FPV-Funke | FPV Remote |
| `container.propwash.drone_battery` | Akku wechseln | Swap Battery |
| `propwash.drone.bind.remote` | Funke verbunden mit %s | Radio bound to %s |
| `propwash.drone.bind.goggles` | Brille verbunden mit %s | Goggles bound to %s |
| `propwash.drone.bind.goggles_wrong_system` | Diese Brille empfängt kein %s-Video – nicht verbunden | These goggles can't receive %s video – not bound |
| `propwash.drone.bind.hint_goggles` | Tipp: Brille in die Hand nehmen und die Drohne anklicken → FPV | Tip: hold your goggles and click the drone → FPV |
| `propwash.drone.remote.unbound` | Die Funke ist mit keiner Drohne verbunden – klicke eine Drohne mit ihr an | This radio isn't bound – click a drone with it |
| `propwash.drone.remote.not_found` | Drohne %s nicht gefunden – nicht geladen oder zu weit weg | Drone %s not found – not loaded or too far away |
| `propwash.drone.remote.status` | Verbunden mit %s · %s Blöcke entfernt | Bound to %s · %s blocks away |
| `propwash.drone.remote.status_missing` | Verbunden mit %s · gerade nicht erreichbar | Bound to %s · out of reach right now |
| `propwash.drone.remote.stopped` | Verbindung zur Drohne getrennt | Disconnected from the drone |
| `propwash.drone.remote.put_away` | Funke weggelegt – Verbindung getrennt | Radio put away – disconnected |
| `propwash.drone.pilot.no_battery` | Kein Akku in der Drohne – Schleichen + Rechtsklick zum Einsetzen | No battery in the drone – sneak + right-click to install one |
| `propwash.drone.pilot.lost_range` (Text neu) | Funkverbindung verloren – zu weit entfernt | Radio link lost – too far away |
| `propwash.drone.session.fpv` | FPV: verbunden mit %s | FPV: linked to %s |
| `propwash.drone.session.los` | Sichtflug: verbunden mit %s | Line of sight: linked to %s |
| `propwash.drone.session.los_reason.goggles_not_bound` | Deine Brille ist nicht mit dieser Drohne verbunden – Sichtflug | Your goggles aren't bound to this drone – line of sight |
| `propwash.drone.session.los_reason.goggles_bound_other` | Deine Brille ist mit einer anderen Drohne verbunden – Sichtflug | Your goggles are bound to another drone – line of sight |
| `propwash.drone.session.los_reason.goggles_wrong_system` | Deine Brille empfängt kein %s-Video – Sichtflug | Your goggles can't receive %s video – line of sight |
| `propwash.drone.session.switched_los` | Brille abgesetzt – weiter im Sichtflug | Goggles off – continuing line of sight |
| `propwash.drone.battery.inserted` | Akku eingesetzt: %s (%s %%) | Battery installed: %s (%s%%) |
| `propwash.drone.battery.swapped` | Akku getauscht: %s eingesetzt (%s %%) | Battery swapped: %s installed (%s%%) |
| `propwash.drone.battery.locked` | Erst landen und die Verbindung trennen | Land and disconnect first |
| `propwash.drone.battery.hint` | Schleichen + Rechtsklick, um den Akku einzusetzen | Sneak + right-click to install the battery |
| `propwash.drone.battery.incompatible` | %s passt nicht in %s: %s | %s doesn't fit %s: %s |
| `propwash.drone.battery_menu.none` | Kein Akku | No battery |
| `propwash.drone.battery_menu.none_hint` | Ohne Akku lässt sich die Drohne nicht verbinden | Without a battery the drone can't link |
| `propwash.drone.battery_menu.info` | %s %% · %s V | %s%% · %s V |
| `propwash.drone.battery_menu.fits` | Passende Akkus: | Fitting batteries: |
| `propwash.drone.battery_menu.weak` | (schwach) | (weak) |
| `propwash.drone.tooltip.battery_type` | Akku: %s · %s %% | Battery: %s · %s%% |

Entfallen (drone): `propwash.drone.pilot.no_goggles`, `propwash.drone.pilot.wrong_goggles`,
`propwash.drone.session.started`, `propwash.drone.tooltip.hint`, `propwash.drone.tooltip.battery`.

build/content (`common/src/lang/content/`, die `propwash.build.*`-Schlüssel gehören heute content):

| Schlüssel | DE | EN |
|---|---|---|
| `block.propwash.battery_charger` | Akku-Ladegerät | Battery Charger |
| `container.propwash.battery_charger` | Akku-Ladegerät | Battery Charger |
| `propwash.build.issue.battery_frame` | Akku passt nicht zum Rahmen | Battery doesn't fit the frame |
| `propwash.build.issue.battery_undersized` | Akku ist für diesen Rahmen schwach | Battery is weak for this frame |
| `propwash.build.battery_fit.cells_low` | zu wenig Zellen (%sS, braucht %sS–%sS) | too few cells (%sS, needs %sS–%sS) |
| `propwash.build.battery_fit.cells_high` | zu viele Zellen (%sS, höchstens %sS) | too many cells (%sS, at most %sS) |
| `propwash.build.battery_fit.too_heavy` | zu groß und schwer (%s g, höchstens %s g) | too big and heavy (%s g, at most %s g) |
| `propwash.build.battery_fit.weak_cells` | passt, aber weniger Spannung | fits, but less voltage |
| `propwash.build.battery_fit.weak_capacity` | passt, aber kurze Flugzeit | fits, but short flight time |
| `propwash.content.charger.empty` | leer | empty |
| `propwash.content.charger.charging` | Lädt – noch %s s | Charging – %s s left |
| `propwash.content.charger.full` | Voll | Full |
| `propwash.content.charger.redstone_on` | Redstone: Schnellladen | Redstone: fast charging |
| `propwash.content.charger.redstone_off` | Redstone: aus | Redstone: off |
| `propwash.content.workbench.hint.result` (Text neu) | Entnehmen gibt die Drohne mit dem eingesetzten Akku und seinem Ladestand | Taking it gives the drone with the installed battery and its charge |

osd (`common/src/lang/osd/`):

| Schlüssel | DE | EN |
|---|---|---|
| `propwash.osd.radio.armed` | SCHARF | ARMED |
| `propwash.osd.radio.disarmed` | SICHER | SAFE |
| `propwash.osd.radio.no_link` | KEIN SIGNAL | NO SIGNAL |
| `propwash.osd.radio.keyboard` | Keine Funke – Tastatur | No radio – keyboard |
| `propwash.osd.radio.timer` | T1 %s | T1 %s |
| `propwash.osd.radio.distance` | D %sm | D %sm |
| `propwash.osd.radio.height` | H %sm | H %sm |
| `propwash.osd.radio.rescue` | RESCUE %s | RESCUE %s |

server (`common/src/lang/server/`, nur falls `ServerRulesTab` die neuen Werte anzeigt):
`propwash.server.rules.battery_charge_seconds` „Ladezeit Referenzakku: %s s“ / „Charge time reference pack: %s s“,
`propwash.server.rules.redstone_charge` „Redstone-Ladefaktor: ×%s“ / „Redstone charge factor: ×%s“.

---

## 14. Rezepte und Creative-Tab

- `data/propwash/recipe/remote.json` (`crafting_shaped`, Kategorie `equipment`, Gruppe `propwash_radio`):
  Muster `[" T ", "LGL", "IRI"]`, `T` = `minecraft:redstone_torch`, `L` = `minecraft:lever`, `G` =
  `minecraft:glass_pane`, `I` = `minecraft:iron_ingot`, `R` = `minecraft:redstone`.
- `data/propwash/recipe/battery_charger.json` (`crafting_shaped`, Kategorie `misc`): Muster `["IGI", "CRC", "SSS"]`,
  `I` = `minecraft:iron_ingot`, `G` = `minecraft:glass_pane`, `C` = `minecraft:copper_ingot`, `R` =
  `minecraft:redstone_block`, `S` = `minecraft:smooth_stone_slab`.
- Akku-Rezepte unverändert (Ergebnis hat über den Prototyp Ladestand 1,0).
- `ContentTab`: `remote` nach den Brillen, `battery_charger` nach der Werkbank (Nachschlagen per `ContentIds.item`).

---

## 15. Änderungen je Modul

Neue Dateien **fett**. Paketpfade relativ zu `common/src/main/java/de/lardtom/propwash/`.

### build
- **`build/BatteryFit.java`** (Tabelle §3).
- `build/DroneBuild.java`: `Issue.BATTERY_FRAME` (problems), `Issue.BATTERY_UNDERSIZED` (warnings).

### core (Foundation, freigegeben)
- **`core/BoundDrone.java`**; `core/CoreComponents.java`: `BOUND_DRONE`, `DRONE_ID`, `BATTERY_INSTALLED`.

### registry (Foundation, freigegeben)
- `registry/client/ClientRegistrar.java`: `specialModelRenderer(...)` + Flush-Liste.
- `registry/PropwashRegistries.java`: `blockItem`/`blockWithItem`-Überladung mit BlockItem-Konstruktor.
- `fabric/.../PropwashFabricClient.java`, `neoforge/.../PropwashNeoForgeClient.java`: Special-Renderer-Flush.

### sim
- `sim/CellCurve.java`: unverändert (Integration: `sim` gehört einem anderen Arbeitsstrang; Sim-Wunsch „`ocv` öffentlich“).

### drone
- **`drone/PilotMode.java`** (`NONE, FPV, LINE_OF_SIGHT`), **`drone/PilotRules.java`**, **`drone/RadioLink.java`**,
  **`drone/LosAim.java`**, **`drone/DroneInteraction.java`** (reine Tabelle), **`drone/DroneInteractions.java`**
  (Ausführung), **`drone/RemoteItem.java`**, **`drone/DroneBatteryMenu.java`**, **`drone/DroneBatteryContainer.java`**.
- `drone/QuadcopterEntity.java`: §2.3/§2.4, `interact` → `DroneInteractions`, Nachladen entfernt.
- `drone/DronePiloting.java`: `useRemote`, neue `tryStart`-Signatur/`StartResult`, `interactStart` weg,
  `onPilotRequest` nur Stopp, Tick-Prüfungen §5.2/§5.3, Entprellung.
- `drone/QuadcopterItem.java`: `drone_id` beim Platzieren, Tooltip über `ItemHelp`.
- `drone/DroneModule.java`: `REMOTE`, `DRONE_BATTERY_MENU`.
- `drone/client/PilotSession.java`: Modus, `isFpv/isLineOfSight/mode`, `applyMode`, Kamera/Perspektive, Hotbar-Slot,
  `RadioLink`, Start-Meldung reduziert.
- **`drone/client/PilotView.java`**, **`drone/client/LosView.java`**, **`drone/client/DroneBatteryScreen.java`**,
  **`drone/client/render/RemoteGimbalRenderer.java`**, **`drone/client/render/RemoteGimbalModel.java`**.
- `drone/client/FpvView.java`: `active()` = FPV, `hidesBody(entity)`.
- `drone/client/DroneClient.java`: LosView, Menü-Screen, Special-Renderer + Model-Layer, Fadenkreuz über `PilotView`.
- `drone/client/render/QuadcopterRenderer.java` (`isOwnFpvDrone` → FPV), `DroneVisuals.java` (Akku ausblenden).
- `drone/client/DroneSelfTests.java`: an neue Startlogik anpassen + neue Schritte (§17).
- `mixin/drone/client/MinecraftMixin.java` (Hotbar/Inventar leeren, `keyUse` → Stopp + Latch, `PilotView.active()`),
  `MouseHandlerMixin.java` (`PilotView.active()`), `LevelExtractorMixin.java` (`hidesBody`). `CameraMixin`,
  `GameRendererMixin`, `LivingEntityMixin` unverändert (Verhalten über `FpvView`/`freezeBody`).

### content
- **`content/Batteries.java`**, **`content/BatteryItem.java`**, **`content/ChargeMath.java`**, **`content/ChargerState.java`**,
  **`content/BatteryChargerBlock.java`**, **`content/BatteryChargerBlockEntity.java`**, **`content/BatteryChargerMenu.java`**,
  **`content/ItemHelp.java`**, **`content/TooltipShift.java`**, **`content/HelpBlockItem.java`**,
  **`content/client/BatteryChargerScreen.java`**.
- `content/ContentIds.java`: `REMOTE`, `BATTERY_CHARGER`, `isRemote`, `boundDrone`.
- `content/ContentItems.java`: Akkus als `BatteryItem` mit Standard-Ladestand.
- `content/ContentBlocks.java`: Ladegerät (Block, `HelpBlockItem`, Block-Entity-Typ, Menü), Werkbank-Item als `HelpBlockItem`.
- `content/ContentTab.java`, `content/PartItem.java`, `content/PartTooltips.java` (Kurzzeile/Daten-Trennung),
  `content/FpvGogglesItem.java` (ItemHelp + Bindungsstatus), `content/DroneAssembly.java` (Ladestand übernehmen,
  Zerlegen ohne Akku), `content/BuildSlots.java` (`affectedBy`), `content/client/WorkbenchReport.java` (neue Issues),
  `content/client/ContentClient.java` (TooltipShift, Screen), `content/client/ContentSelfTests.java`.
- Rezepte §14.

### osd
- **`osd/client/RadioHud.java`**, **`osd/client/LosOsdSource.java`**; `osd/client/LiveOsdSource.java`,
  `osd/client/OsdHud.java` (`isFpv`), `osd/client/OsdClient.java` (HUD-Ebene).

### video
- `video/client/VideoState.java`: `fpvFov` und `resolveSource` auf `isFpv()`.

### audio
- `audio/client/MotorVoices.java`: beide `session.isActive()` → `session.isFpv()`.

### race
- `race/GateItem.java`, `race/CourseToolItem.java`: Tooltips über `ItemHelp`.

### server
- `server/ServerRulesConfig.java` (+ Validator, `copy/assign`), optional `server/client/ServerRulesTab.java`.

### Ressourcen (Code-Agent)
- Sprachquellen je Modul (§12/§13), Rezepte (§14). **Nicht**: Modelle, Texturen, Blockstates, Item-Definitionen
  (Asset-Agent).

### Asset-Agent
- `tools/assets/world/*` (Generator, Validator, Vorschau), `assets-src/world/*`, alle Dateien aus §11.1,
  `assets/world/client/WorldAssetsSelfTests.java`, `WorldAssetsTest.java`, `docs/ASSETS.md`-Abschnitte (§11.6).

---

## 16. JUnit-Tests (`common/src/test`, ohne Minecraft-Bootstrap)

| Test | Prüft |
|---|---|
| `build/BatteryFitTest` | vollständige 5 × 5-Matrix aus §3 (Ergebnis je Paar), jeder Rahmen hat ≥ 1 OK-Akku, alle Presets OK, `fitting`/`framesFor` konsistent, Gründe-Reihenfolge |
| `build/DroneBuildBatteryIssuesTest` | `BATTERY_FRAME` in `problems()` (z. B. Whoop + 6S) → nicht baubar; `BATTERY_UNDERSIZED` in `warnings()` (5" + 4S, 7" + 1100) |
| `content/BuildSlotsTest` (bestehend, erweitern) | `affectedBy` deckt die neuen Issues ab |
| `content/ChargeMathTest` | 1300 mAh = 90 s, Exponent, Mindestzeit, Redstone-Faktor, Schrittweite je Intervall, Summe erreicht genau 1,0, Restzeit monoton, ungültige Config-Werte geklemmt |
| `content/BatteriesMathTest` | `quantize` (1/1000, ≥ 0,999 → 1,0), Klemmen, Ruhespannung voll/leer je Chemie, Mitte monoton und nach der LiPo-Kurve |
| `drone/PilotRulesTest` | Entscheidungsmatrix: keine Brille → LOS/NO_GOGGLES; nicht gebunden; andere Drohne; falsches System; analog+analog → FPV; Kreativ gebunden → FPV bei jedem System; Kreativ ungebunden → LOS |
| `drone/DroneInteractionTest` | jede Zeile der Tabelle §4.1 inkl. Vorrang (`locked` vor Schleich-Aktionen, Akku vor Menü) |
| `drone/LosAimTest` | Yaw/Pitch-Konvention (Ziel in +Z → Yaw 0, in −X → Yaw 90, oben → negativer Pitch), Wrap-Around 179° → −179° über den kurzen Weg, Glättung konvergiert, Pitch-Klemme, Halten unter 0,75 Blöcke |
| `drone/RadioLinkTest` | Qualität 1 bis 800, 0 ab 1200, monoton; `START_RANGE ≤ FULL_RANGE`, `RELEASE_RANGE ≥ FULL+FADE` |
| `core/BoundDroneCodecTest` | `CODEC`-Rundlauf über `JsonOps` (UUID, leerer/gesetzter Name, alle Rahmen/Systeme), unbekanntes System → Fehler |
| `content/HelpKeysTest` | für jede Propwash-Item-Id (alle Teile, `drone`, `remote`, drei Brillen, `drone_workbench`, `battery_charger`, drei Gates, `course_tool`) existiert `propwash.help.<id>.summary` in **beiden** Sprachquellen; jede Kategorie hat `what.1` und `use.1`; alle `propwash.help.*`-Werte ≤ 48 Zeichen; DE enthält keine Ersatzschreibungen „ae/oe/ue“ in den Wörtern der Liste {„fuer“, „ueber“, „moeglich“, „Groesse“, „laedt“, „waehrend“} und kein „ss“ statt „ß“ in {„gross“, „Strasse“} (grobe Umlaut-Prüfung) |
| `content/TranslationKeysTest` (bestehend) | läuft weiter grün (neue Schlüssel in beiden Sprachen) |

---

## 17. Selbsttest-Schritte (`SelfTest.clientStep`, laufen im Integrations-Worktree)

drone (`DroneSelfTests`, bestehende Schritte anpassen: „Pilotenrolle mit Brille“ wird zu den Schritten unten; `tryStart`
mit `PilotMode`):
1. **„drone: Funke und Brille binden“** — Server: Drohne (Freestyle, digital) spawnen, Spieler Funke in die Haupthand,
   `drone.interact(player, MAIN_HAND, pos)` → Funke hat `bound_drone.drone == drone.getUUID()`; analoge Brille in der
   Hand → nicht gebunden; Kreativ-Brille → gebunden.
2. **„drone: Sichtflug starten“** — ohne Brille `RemoteItem#use` auf dem Server → `pilot` = Spieler,
   `pilotMode == LINE_OF_SIGHT`; Client: `isLineOfSight()`, Kamera-Entity = Spieler, `VideoState.active() == false`,
   `QuadcopterRenderer.isOwnFpvDrone(drone) == false`; Drohne 3 Blöcke seitlich versetzen (`resetTo`), nach 20 Frames
   zeigt `player.getLookAngle()` auf die Drohne (Winkel < 5°).
3. **„drone: Stopp per Funke“** — `PilotSession.INSTANCE.requestStop()` bzw. simulierter `keyUse`-Klick über
   `PilotView.onUseKeyWhilePiloting()` → Sitzung weg, Server-Pilot leer, `pilotMode == NONE`; erneutes `use` innerhalb
   von 10 Ticks wird ignoriert.
4. **„drone: FPV mit gebundener Brille“** — gebundene Kreativ-Brille in den Kopf-Slot, `use` → `FPV`, Kamera-Entity =
   Drohne; dann Kopf-Slot leeren (Server) → innerhalb von 40 Ticks `LINE_OF_SIGHT`, Kamera zurück beim Spieler.
5. **„drone: Scharf schalten und steigen“ / „Absturz wird erkannt“** — bestehend, Start jetzt über Schritt 4 (FPV).
6. **„drone: Akku tauschen“** — Schnellweg mit `b6s_1100` (Ladung 0,5) → `build.battery == B6S_1100`,
   `batteryCharge ≈ 0,5`, alter `b6s_1300` im Inventar mit altem Ladestand; `b1s_300_hv` → abgelehnt, nichts
   verändert; Schleichen + leere Hand → `player.containerMenu instanceof DroneBatteryMenu`; Akku über das Menü
   entnehmen → `hasBattery == false`, Renderer ohne Akku; `use` → `NO_BATTERY`.
7. **„drone: Aufheben behält Akku, Id und Tuning“** — leere Hand → Item mit `drone_id`, `battery_installed=false`
   (aus Schritt 6), Tuning; wieder platzieren → `getUUID()` gleich; Akku einsetzen; Funke startet ohne neues Binden.
8. **„drone: kein Nachladen“** — Drohne mit Ladung 0,4 steht 100 Ticks → unverändert 0,4.

content (`ContentSelfTests`):
9. **„content: Ladegerät“** — Ladegerät setzen, `ServerRulesConfig` im Test auf `batteryChargeSeconds = 10` (kleinster
   gültiger Wert, der Validator klemmt 10…3600), `batteryChargeMinSeconds = 1`, `redstoneChargeMultiplier = 2` und einen
   Redstone-Block daneben (danach alles zurück), Akku mit 0,5 einlegen → Blockzustand `CHARGING`, nach ≤ 100 Ticks Ladung 1,0 und `DONE`; Menü-Daten
   liefern Restsekunden; Abbauen → Akku als Item-Entity.
10. **„content: Werkbank übernimmt Ladestand“** — Bauen mit Akku 0,3 → Drohnen-Item `battery_charge == 0,3`; Whoop +
    6S → nicht baubar mit `BATTERY_FRAME`; Zerlegen gibt den Akku mit Ladestand zurück.
11. **„content: Shift-Tooltips“** — für jedes Propwash-Item `getTooltipLines` mit `TooltipShift.override(false/true)`:
    ohne Shift ≤ 3 Zeilen und letzte = `propwash.help.shift`; mit Shift mehr Zeilen; keine Zeile gleich ihrem
    Übersetzungsschlüssel (fehlende Übersetzung).

osd (`OsdSelfTests`):
12. **„osd: Funk-HUD im Sichtflug“** — während Schritt 2: `RadioHud` aktiv, Brillen-OSD inaktiv, `OsdHud.hideVanilla()
    == false`.

assets-world (Asset-Agent, §11.6):
13. **„assets: Funke und Ladegerät“** — Modelle/Texturen/Blockzustände/GUI geladen, Special-Typ
    `propwash:remote_gimbals` aufgelöst (kein Missing-Model für `propwash:remote`).

---

## 18. Doku-Nachträge (Code-Agent)

- `docs/FOUNDATION.md` §4 (neue Components), §8 (`specialModelRenderer`), §3 (BlockItem-Überladung), §11:
  `QuadcopterEntity` (Pilotenmodus, Akku ein/aus, `drone_id`, kein Nachladen, `loadFromStack/toStack` erweitert),
  `PilotSession` (`isActive` = beliebiger Modus, `isFpv`, `isLineOfSight`, `mode`; Aufrufer-Tabelle §5.5),
  `ContentIds` (`REMOTE`, `BATTERY_CHARGER`, `isRemote`, `boundDrone`), neue Verträge `build.BatteryFit`,
  `drone.RadioLink`, `content.ItemHelp`/`TooltipShift`.
- `docs/ARCHITECTURE.md` „Datenfluss beim Fliegen“ Schritt 1: Start über die Funke, Modi FPV/LOS; „Spielinhalte“:
  Funke, Ladegerät, Akkus mit Ladestand.
- `docs/ASSETS.md`: Abschnitt „Akkus je Rahmen“ (Tabelle §3) — Code-Agent; Asset-Abschnitte — Asset-Agent (§11.6).
- Tages-Changelog `changelogs/changelog-YYYY-MM-DD.md` (Neue Features: Funke, Sichtflug, Akkutausch, Ladegerät,
  Shift-Tooltips; UI: Funk-HUD, Menüs; Sonstiges: Konfig-Werte).

---

## 19. Risiken und bewusst ausgelassene Punkte

### 19.1 Umsetzungsnotizen (Code-Agent, Branch `feedback/gameplay`)

- **Pilot ohne Modus:** Andere Module und ältere Selbsttests setzen `QuadcopterEntity#setPilot` direkt (race, server, core).
  Dann bleibt `pilotMode() == NONE`; der Client behandelt das wie FPV (bisheriges Verhalten) und der Server verlangt dafür
  keine Funke in der Hand. Alle Starts über die Funke setzen den Modus vor dem Piloten.
- **Akku-Items tragen den Ladestand im Prototyp:** `stack.has(BATTERY_CHARGE)` ist für Akkus deshalb immer wahr; „volle
  Akkus stapeln“ prüft man über einen leeren Component-Patch. Der Ladebalken lag vorher in `PartItem` und liegt jetzt nur
  in `BatteryItem`.
- **`BuildAnalysis`/`DerivationTest`:** Mit `BATTERY_FRAME` sind statt 380 nur noch 204 der 6000 Kombinationen baubar
  (Test angepasst).
- **`RadioLink.quality(distance, serverLimit)`:** Die bisherige Ausblendung vor dem Server-Limit bleibt (sonst würde die
  Verbindung bei 160 Blöcken hart abreißen); `quality(distance)` ist der Fall ohne Limit. Start: `min(maxPilotDistance,
  START_RANGE)`, Lösen: `min(maxPilotDistance, RELEASE_RANGE)`.
- **Tipp „Brille binden“** kommt nur, wenn der Spieler beim Binden der Funke eine Brille trägt, die nicht an diese
  Drohne gebunden ist (wie in §4.1), und nicht, wenn er schon wieder fliegt.
- **Ohne Assets:** Fehlen `textures/gui/battery_charger.png`/`drone_battery.png`, zeichnen die Screens das Panel aus
  Flächen; fehlt `textures/entity/remote/gimbal.png`, nehmen die Knüppel eine Vanilla-Textur. Item-/Blockmodelle kommen
  vom Asset-Branch `feedback/gameplay-assets`.
- **Beide Screens** implementieren `ui.client.theme.ThemedScreen` (Theme aus der Integration).

- **Special-Renderer auf Fabric:** Zugriff auf `SpecialModelRenderers.ID_MAPPER` nur über den transitiven
  Access-Widener von fabric-api im Fabric-Modul (nicht aus `common`). Scheitert das, liefert die Glue auf Fabric vorerst
  nichts und `items/remote.json` würde dort ein fehlendes Modell zeigen — dann Fallback: Item-Definition ohne
  `composite`/`special` (nur `propwash:item/remote`, statische Knüppel) für beide Loader, Animation nachreichen.
  Vor dem Merge klären, nicht raten.
- **Identitätsvergleich `stack == getMainHandItem()`** im Renderer: bei Hotbar-Icons derselbe Stack → deshalb zeigt die
  GUI das statische Modell (Item-Definition §11.3). Andere Spieler sehen neutrale Knüppel (keine Stick-Daten im Netz).
- **Beidhändiges Halten in der dritten Person** (eigene Armhaltung) ist loaderspezifisch (Mixin/Client-Extensions) —
  nicht in dieser Stufe. Nachtrag: umgesetzt mit Mixins in `common` für beide Loader (`HumanoidModel#setupAnim`,
  `ItemInHandLayer#submitArmWithItem`, `AvatarRenderState`), siehe `docs/ASSETS.md`.
- **Blick-Setzen pro Frame** schickt Rotationspakete an den Server (Vanilla, pro Tick) — gewollt, andere sehen den
  Kopf im Sichtflug folgen (im FPV nicht, siehe §6.1).
- **Hotbar-Rücksetzen** statt Scroll-Mixin: kurzes Flackern des Auswahlrahmens möglich, bewusst in Kauf genommen
  (kein Mixin auf das von NeoForge gepatchte `MouseHandler#onScroll`).
- **Drohnen-Id bei Kopien** (Pick-Block): die Kopie trägt keine `DRONE_ID` und bekommt beim Platzieren eine neue
  UUID, Bindungen zeigen aufs Original — gewollt.
- **Akkus in Werkbank-Presets/Creative-Tab**: voll geladen (Prototyp), wie bisher.
