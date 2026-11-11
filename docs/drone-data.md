# Daten an der Drohne und Knöpfe in der Wartung (Add-on-API 1.7)

Für Add-on-Autoren. Zwei Bausteine der API 1.7: Add-ons speichern eigene Daten an der Drohne (B6), und sie hängen
eigene Knöpfe in die Wartung (B10). Technik und Entscheidungen: [`design/0.6.0/addon-api-namespace.md`](design/0.6.0/addon-api-namespace.md)
§3.9, §3.13 und Entscheidung E23. Stabil ist nur, was unter `de.lardtom.propwash.api` steht.

## 1. Daten an der Drohne

```java
DroneDataKey<Mission> MISSION = PropwashApi.droneData().register(Identifier.fromNamespaceAndPath("camdrones", "mission"),
        Mission.CODEC, Mission.EMPTY, DroneDataKey.Sync.TRACKING);
PropwashApi.droneData().set(drone, MISSION, mission);
Mission now = PropwashApi.droneData().get(drone, MISSION);
```

- **Anmelden** einmal beim Start, im eigenen Namensraum (`propwash:` wird abgelehnt). Ein Schlüssel hat einen Codec, einen
  Standardwert und eine Sync-Stufe. Doppelte Ids werfen.
- **Schreiben** (`set`, `remove`) nur am Server. Am Client wirft beides `IllegalStateException`. Eine Entity, die keine
  Propwash-Drohne ist, wirft `IllegalArgumentException`; `get` liefert dort den Standard.
- **Lesen** (`get`) am Server immer. Am Client nur Schlüssel mit `Sync.TRACKING`; `Sync.NONE` liefert am Client den
  Standard. Fehlt ein Wert, kommt der Standard. Lässt sich ein gespeicherter Wert mit dem Codec nicht lesen, kommt der
  Standard und einmal je Schlüssel eine Warnung im Log; der gespeicherte Wert bleibt unverändert.
- **Grenzen:** höchstens 2048 Bytes je Schlüssel (`IllegalArgumentException`) und 8192 Bytes je Drohne
  (`IllegalStateException`). Gezählt wird die Größe als Binär-NBT (Typ-Byte plus Inhalt), für die Drohne über alle
  Add-ons. Ein abgelehnter Wert ändert nichts.
- **Werte** gelten als unveränderlich: `get` gibt dasselbe Objekt zurück, bis sich die Daten ändern. Listen und Records
  aus Codecs sind das ohnehin.
- **Abgleich:** Ändert der Server einen `TRACKING`-Wert, bekommen alle Clients, die die Drohne sehen, den neuen Stand
  (Entity-Daten-Serializer `propwash:addon_data`, nur `TRACKING`-Schlüssel, die am Server angemeldet sind). `NONE`-Werte
  verlassen den Server über die Drohne nie.
- **Speichern:** Entity-NBT `"AddonData": {"<ns>": {"<pfad>": <Codec-NBT>}}`. Beim Aufheben trägt das Drohnen-Item
  dieselben Daten in der Komponente `propwash:addon_data`, beim Setzen kommen sie zurück an die Drohne; auch der
  Pick-Block im Kreativmodus kopiert sie. `get(ItemStack)` liest am Item.
- **Item und `Sync.NONE`:** Am Item reisen **alle** Daten, auch `NONE`. Minecraft gleicht jede Item-Komponente mit dem
  Client ab, und das Kreativ-Inventar schickt Items vom Client zurück; eine gefilterte Komponente würde dort Daten
  löschen. `NONE` heißt also „nicht über die Drohne in der Welt“, nicht „geheim“. Geheimnisse gehören nicht in
  Drohnen-Daten.
- **Fehlt ein Add-on,** bleiben seine Daten unverändert erhalten: an der Drohne, am Item, beim Speichern und Laden der
  Welt. Sie werden nicht abgeglichen (die Sync-Stufe kennt nur das Add-on). Ein Eintrag, dessen Namensraum kein Compound
  ist, wird nie überschrieben; `set` wirft dann `IllegalStateException`.
- **Codecs mit Registern:** An der Drohne liest und schreibt Propwash mit dem Register-Kontext der Welt. Am Item gibt es
  keinen Kontext; Codecs, die datengetriebene Register brauchen (z. B. `ItemStack` mit Verzauberungen), liest man an
  der Drohne.
- **Nicht dabei:** Sharecodes und Baupläne (Daten gehören zur einzelnen Drohne), das Zerlegen in der Werkbank (die Teile
  tragen keine Add-on-Daten, eine neu gebaute Drohne startet leer), Zonen.
- **Alte Welten** laden unverändert (Drohnen ohne `AddonData`). 0.5.x ignoriert `AddonData` an Drohnen in der Welt; ein
  Drohnen-Item mit Add-on-Daten kann 0.5.x nicht lesen (unbekannte Komponente `propwash:addon_data`).

## 2. Knöpfe in der Wartung

```java
PropwashClientApi.registerServiceExtension(new ServiceExtension() {
    public Identifier id() { return Identifier.fromNamespaceAndPath("camdrones", "mission"); }
    public Component label() { return Component.translatable("camdrones.service.mission"); }
    public boolean visible(Entity drone) { return true; }
    public void open(Entity drone, Screen parent) { Minecraft.getInstance().gui.setScreen(new MissionScreen(parent, drone)); }
});
```

- Die Wartung öffnet man mit Schleichen + Rechtsklick auf die Drohne. Die Knöpfe stehen im Kopf rechtsbündig vor
  „Zubehör“ (falls die Drohne Zubehör hat), in der Reihenfolge der Anmeldung. `visible(drone)` entscheidet beim Öffnen.
- **Höchstens drei** Knöpfe sind sichtbar. Reicht der Platz nicht, werden sie schmaler (mindestens 32 GUI-Pixel, Text
  gekürzt, voller Text im Tooltip) oder es sind weniger. Der Rest liegt unter **„Mehr“**: ein kleines Fenster im
  Propwash-Theme mit je einem Knopf. Neben „Zubehör“ ist der Kopf knapp (englisch bleiben rund 18 GUI-Pixel); dann
  steht dort nur ein schmaler Knopf „…“ mit der Zahl im Tooltip, der dasselbe Fenster öffnet.
- `open(drone, parent)` bekommt immer die Wartung als `parent`, auch aus dem Fenster „Mehr“. Das Add-on öffnet sein
  Kind-Fenster im Propwash-Theme (Handbuch §14.4) und kehrt mit `setScreen(parent)` zurück; die Wartung ist am Server
  die ganze Zeit offen.
- Wirft ein Add-on in `visible`, `label` oder `open`, bleibt die Wartung bedienbar: der Knopf fehlt bzw. zeigt die Id,
  der Fehler steht einmal im Log.
- **Pilotenwechsel:** `PropwashEvents.PILOT_CHANGED` kommt am Server in beiden Richtungen (`previous` leer → Pilot,
  Pilot → `current` leer), auch beim Wechsel zwischen zwei Piloten.

## 3. Beispiel

`dev/example/ExampleMission` (nur im Entwicklungslauf): Schlüssel `propwash_example:mission` (`TRACKING`, Name,
Wegpunkte, Flüge) und `propwash_example:service_log` (`NONE`, Zahl der Pilotenwechsel, letzter Pilot). Bei jedem
`PILOT_CHANGED` schreibt das Beispiel das Protokoll; übernimmt jemand die Drohne, legt es beim ersten Mal eine Runde um
die Drohne an und zählt den Flug. Der Knopf „Mission“ in der Wartung öffnet ein Fenster mit Name, Flügen und den ersten
Wegpunkten; das Protokoll sieht man dort nicht, es bleibt am Server. Im Selbsttest ist das Beispiel nur aktiv, wenn ein
Schritt es anfordert.

## 4. Tests

- JUnit `drone.data.DroneDataTest` (Codec, Grenzen, Item-Komponente, fremde Daten, Abgleich): `tools/fastcheck.sh test
  --select-class de.lardtom.propwash.drone.data.DroneDataTest`.
- Selbsttest beider Loader: `'-PpropwashSelftestOnly=addon-api-17: B6,addon-api-17: B10'`, Beschreibung in
  [`testing.md`](testing.md) §1 „Daten an der Drohne und Knöpfe in der Wartung“.
- Beitrittstest auf dem dedizierten Server mit `PROPWASH_JOINTEST_ADDONDATA=1`, ebenfalls dort.
- Bildschirmfotos (nur Fabric, `-PpropwashScreenshots=true`): `'-PpropwashSelftestOnly=bildschirmfotos: Wartung mit
  Knöpfen,bildschirmfotos: Kind-Fenster Mission'` schreibt `drone_service_addons` (Wartung mit „Mission“ und „Mehr“) und
  `drone_service_addon_window` (Kind-Fenster des Beispiels).
- Der Server-Rauchtest deckt das Beispiel mit ab: dort ist es aktiv und schreibt bei jedem `PILOT_CHANGED` Mission und
  Protokoll an die Testdrohnen.

## 5. Englische Kernsätze (Website)

- Add-ons can store their own data on a drone: `PropwashApi.droneData().register(id, codec, default, sync)`, then `set`
  on the server and `get` on either side.
- `Sync.TRACKING` values reach every client that sees the drone; `Sync.NONE` values stay on the server while the drone
  is in the world.
- Limits: 2 KiB per key and 8 KiB per drone (binary NBT). Larger values are rejected with an exception and change
  nothing.
- The data travels with the drone item when it is picked up and comes back when it is placed. On the item every value
  travels, so never store secrets in drone data.
- Data of an add-on that is not installed is kept unchanged on the drone, on the item and in the saved world.
- Add-ons can add up to three buttons to the drone's service screen (sneak + right-click); more buttons go under "More".
  Each button opens the add-on's own window, which returns to the service screen.
