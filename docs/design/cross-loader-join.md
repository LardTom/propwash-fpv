# NeoForge-Spieler auf Fabric-Servern

Beitrag von ETHG (GitHub-PR #2), in 0.6.0 übernommen. Propwash FPV hat einen Fabric- und einen NeoForge-Build, die
denselben Inhalt in derselben Reihenfolge anmelden (FOUNDATION §3, „Flush-Reihenfolge“). Bis 0.5.2 musste ein Spieler
trotzdem den Loader des Servers benutzen. Seit 0.6.0 tritt ein **NeoForge-Client einem Fabric-Server** bei, der dieselbe
Propwash-Version und dieselben Add-ons hat, direkt oder hinter Velocity. Für Fabric-Clients, für NeoForge auf NeoForge
und im Einzelspieler ändert sich nichts, Server-Betreiber müssen nichts tun.

Die Gegenrichtung (Fabric-Client auf NeoForge-Server) gibt es nicht. Dafür bräuchte es dieselbe Art Brücke für den
Registerabgleich von NeoForge auf der Fabric-Seite. Der NeoForge-Server lehnt solche Spieler sauber ab (Abschnitt
„Spieler ohne NeoForge auf NeoForge-Servern“).

## Warum es nicht ging

Ein NeoForge-Client scheiterte am Fabric-Server an vier Stellen, jede verdeckte die nächste:

1. **Pflicht-Kanäle.** Propwash meldete seine Pakete als Pflicht an. Gegenüber einem Server ohne NeoForge meldet NeoForge
   nur *optionale* Pakete und lehnt Pflicht-Pakete ab. Der Fabric-Server sah die Propwash-Kanäle also nie und antwortete
   mit „This server requires Propwash FPV“.
2. **Registerabgleich von Fabric.** Die Fabric API trennt jeden Client, der `fabric:registry/sync` nicht empfangen kann,
   sobald der Server Mod-Einträge in abgeglichenen Registern hat („requires Fabric Loader and Fabric API“). Das hat einen
   guten Grund: Items, Blöcke, Entity-Typen und anderes gehen als Zahlen übers Netz, und die Zahlen des Fabric-Servers
   stammen aus seinem Weltstand. Sie können von der Reihenfolge beim Client abweichen.
3. **Frühe Pakete.** Ein Fabric-Server arbeitet Konfigurationsaufgaben aus `BEFORE_CONFIGURE` (Registerabgleich von
   Fabric, Beitritts-Prüfung von Propwash) ab, bevor er seine Marke schickt. Ein NeoForge-Client behandelt einen Server
   aber erst dann als „kein NeoForge“, wenn er die Marke sieht. Bis dahin trennt er: „Received a modded payload before
   channel negotiation“.
4. **Serverwechsel hinter einem Proxy.** Am Ende der Konfiguration meldet NeoForge seine Konfigurations-Kanäle ab.
   Velocity merkt sich das und spielt dem nächsten Server die restliche Kanalliste vor. Nach einem Serverwechsel glaubte
   der Fabric-Server also wieder, der Client könne den Registerabgleich nicht empfangen.

## Was der NeoForge-Build jetzt tut

Alles steckt im NeoForge-Teil und in den gemeinsamen Paket-Klassen, der Fabric-Build bleibt unverändert.

- **Optionale Pakete.** `PropwashNeoForge` meldet seine Pakete mit `.optional()` an. Dass beide Seiten Propwash haben,
  prüft die Beitritts-Prüfung von Propwash selbst, das Netz muss es nicht mehr. Ein anderes Netzprotokoll lehnt NeoForge
  weiter ab: Die Aushandlung vergleicht die Version auch bei optionalen Paketen. Das Netzprotokoll bleibt, der Inhalt
  der Pakete ändert sich nicht.
- **Brücke für den Registerabgleich** (`neoforge.fabricsync.FabricRegistryBridge`, Pakete in `network.fabricsync`):
  - empfängt `fabric:registry/sync` (`FabricRegistrySyncPayload` liest das Format der Fabric API),
  - macht aus jedem Register einen NeoForge-`RegistrySnapshot` und wendet ihn mit `RegistryManager.applySnapshot` an.
    Das ist derselbe Aufruf wie beim Abgleich von NeoForge selbst, Blockzustände, Item-Zuordnungen und andere abgeleitete
    Daten baut NeoForge also mit seinen eigenen Callbacks neu auf,
  - ordnet die Entity-Data-Serializer aus dem Fabric-Register `fabric-object-builder-api-v1:tracked_data_handler` neu
    zu. Fabric nummeriert Mod-Serializer direkt hinter den Vanilla-Serializern, NeoForge ab 256. Für diese Verbindung
    trägt die Brücke die Mod-Serializer deshalb unter den Nummern von Fabric in die Vanilla-Tabelle ein,
  - antwortet mit `fabric:registry/sync/complete`,
  - lehnt den Beitritt mit einer klaren Meldung ab, wenn der Server Einträge oder Register hat, die dem Client fehlen. Die
    Meldung nennt die Namensräume, Spieler sehen also, welche Mod fehlt,
  - behandelt unbekannte Register wie Fabric selbst: Trägt das Register die Markierung „optional“ (Bit `0x1` im
    Attribut-Byte, `RegistryAttribute.OPTIONAL`), überspringt die Brücke es, sonst lehnt sie ab,
  - setzt Register (`RegistryManager.revertToFrozen`) und Serializer-Nummern im ersten Client-Tick nach dem Ende der
    Verbindung zurück. Eine Aufgabe über `Minecraft#execute` reicht dafür nicht: `level.disconnect` schließt den Kanal,
    und `Minecraft#disconnect` verwirft direkt danach alle wartenden Aufgaben (`dropAllTasks`). Die Mod-Serializer
    blieben sonst beim Verlassen über das Menü auf den Nummern von Fabric,
  Die Brücke schaltet sich ab, wenn `fabric_registry_sync_v0` geladen ist (etwa Forgified Fabric API), das erledigt dann
  dasselbe.
- **Menüs mit Zusatzdaten** (Befund V-RC4-1, `neoforge.fabricsync.FabricMenuBridge`, `neoforge.client.FabricMenuScreens`,
  Paket `network.fabricsync.FabricOpenScreenPayload`): Propwash öffnet Wartung (`propwash:drone_service`, Zusatzdaten
  die Entity-Id als VarInt) und Zubehör (`propwash:drone_accessories`, `AccessoryMenu.OPENING`) mit Zusatzdaten. Auf
  Fabric läuft das über `ExtendedMenuProvider`, und der Fabric-Server schickt dafür statt des Vanilla-Pakets
  `fabric-menu-api-v1:open_screen`. NeoForge verwarf es („No registration for payload“), das Fenster blieb zu. Jetzt:
  - `FabricOpenScreenPayload` liest das Format der Fabric API (`Networking.OpenScreenPayload`, fabric-menu-api-v1 2.0.27
    in Fabric API 0.161.0): Menütyp als Identifier, Fenster-Id als **ein Byte** (der Kommentar dort sagt VarInt, der
    Code schreibt `writeByte`), Titel über `ComponentSerialization.STREAM_CODEC`, danach die Zusatzdaten bis zum Ende.
  - `FabricMenuScreens` öffnet das Menü wie NeoForges eigenes Öffnen mit Zusatzdaten (`AdvancedOpenScreenPayload`):
    `MenuType#create` mit den Zusatzdaten im `RegistryFriendlyByteBuf`, Fenster aus `MenuScreens.getScreenFactory`.
    Beide Loader lesen die Zusatzdaten mit demselben `StreamCodec` (`IPlatformHelper.extendedMenuType`), die Bytes passen
    also ohne Umbau. Unbekannte Menütypen und Fehler beim Lesen landen im Log, der Spieler wird nicht getrennt.
  - Angemeldet als optionales Paket (wie der Registerabgleich) nur, wenn `fabric_menu_api_v1` nicht geladen ist.
  - Werkbank und Ladegerät sind Vanilla-Menüs ohne Zusatzdaten, der Kurs-Editor und die anderen Propwash-Fenster öffnen
    sich über eigene Propwash-Pakete. Die Add-ons (Kamera-Drohnen, Mob Defense, Just More Parts) haben keine eigenen
    Menüs, ihr Inhalt erscheint in Wartung und Zubehör.
- **Zwei kleine Client-Mixins** (`mixin.fabricsync.client`, nur NeoForge):
  - `ClientNetworkRegistryMixin`: Ein Mod-Paket, das vor jeder NeoForge-Aushandlung ankommt, kann nur von einem Server ohne
    NeoForge stammen, denn ein NeoForge-Server schickt immer zuerst seine Netzwerk-Anfrage. In dem Fall richtet das Mixin
    die Verbindung als „kein NeoForge“ ein, wo NeoForge sonst trennen würde.
  - `ClientConfigurationPacketListenerImplMixin`: Schickt ein Server ohne NeoForge `minecraft:register` auf einer schon
    eingerichteten Verbindung (Serverwechsel hinter einem Proxy), meldet der Client seine Konfigurations-Kanäle neu an.

## Ort der Mixins

Es sind die ersten Mixins, die nur ein Loader braucht. Sie liegen wie bei Sodium im Modul des Loaders, Paket und Config
folgen derselben Regel wie die gemeinsamen Mixins (`docs/ARCHITECTURE.md`, `docs/CONVENTIONS.md`): Paket
`de.lardtom.propwash.mixin.<feature>`, Client-Mixins in `.client`, eigene Config `propwash.<feature>.mixins.json`.

| | Klassen | Config | Anmeldung |
|---|---|---|---|
| gemeinsam | `common/src/main/java/…/mixin/<feature>` | `common/src/main/resources/propwash.<feature>.mixins.json` | `fabric.mod.json` und `neoforge.mods.toml` |
| nur NeoForge | `neoforge/src/main/java/…/mixin/<feature>` | `neoforge/src/main/resources/propwash.<feature>.mixins.json` | nur `neoforge.mods.toml` |

## Spieler ohne NeoForge auf NeoForge-Servern

Mit `.optional()` lehnt die Aushandlung von NeoForge Clients ohne NeoForge nicht mehr ab. Ein Fabric-Client mit Propwash
käme ohne Registerabgleich auf den Server (falsche Ids, kaputte Welt), denn NeoForge gleicht Register nur mit
NeoForge-Clients ab. Deshalb lehnt der NeoForge-Server **jede** Verbindung ab, die nicht von NeoForge kommt, mit oder
ohne Propwash:

- Ort: `PropwashNeoForge.onConfigurationTasks`, vor allen anderen Aufgaben.
- Erkennung: `listener.getConnectionType().isOther()`, nicht die Client-Marke und nicht die Kanäle. Wann im Handshake die
  Kanalliste ankommt, spielt so keine Rolle.
- Meldung: `JoinText.notNeoForge` mit englischem Rückfalltext für Clients ohne Propwash, dazu die Add-on-Liste wie bei
  `JoinText.missingPropwash`.
- Ein **NeoForge-Client ohne Propwash** hat keine Propwash-Kanäle und bekommt weiter `JoinCheck.missingPropwash()`. Ohne
  diese Zeile trennte ihn erst der Registerabgleich von NeoForge mit einer Liste einzelner Einträge.

Ein anderes Netzprotokoll zwischen zwei NeoForge-Seiten lehnt die Aushandlung weiter ab, auch bei optionalen Paketen.

## Gemischte Server

Wer NeoForge- und Fabric-Spieler auf einem Server haben will, nimmt Fabric als Server-Loader, auch hinter Velocity.
Beide Spielergruppen brauchen dieselbe Propwash-Version und dieselben Add-ons mit Inhalt. Auf einem NeoForge-Server
können nur NeoForge-Spieler spielen. Die Gegenrichtung ist zurückgestellt, bis Betreiber von NeoForge-Servern danach
fragen.

## Grenzen

- Client und Server brauchen wie bisher dieselbe Propwash-Version und dieselben Add-ons mit Inhalt. Alles andere meldet
  die Beitritts-Übersicht mit Namen.
- Inhalts-Mods, die es nur für Fabric gibt, brauchen beim Client ihren eigenen NeoForge-Build mit denselben Ids.
- Add-ons mit eigenen NeoForge-Paketen melden sie mit `.optional()` an. Gegenüber einem Server ohne NeoForge verweigert
  NeoForge jede Mod mit Pflicht-Paketen (`ClientNetworkRegistry.configureOtherConnection`).
- `FabricRegistrySyncPayload` folgt dem internen Format der Fabric API (`fabric-registry-sync-v0` 8.0.x, MC 26.3). Ändert
  die Fabric API das Format, wird `FabricWireFormatTest` beim Bauen rot (siehe unten), statt dass echte Server mit der
  Meldung „ließ sich nicht abgleichen“ scheitern.
- Dasselbe gilt für `FabricOpenScreenPayload` und das Format von `fabric-menu-api-v1:open_screen`.

## Tests

- JUnit `network.fabricsync.FabricRegistrySyncPayloadTest` (`:common:test`, auch `tools/fastcheck.sh test`): liest ein von
  Hand gebautes Paket im Format von Fabric, hält Nummern über Lücken und gemischte Namensräume, Markierung „optional“.
- JUnit `fabric.fabricsync.FabricWireFormatTest` (`:fabric:test`, läuft in `./gradlew build`): gegen die echte Klasse
  `RegistrySyncPayload` der Fabric API in beide Richtungen. Sie liegt nur im Testklassenpfad von Fabric, `fastcheck` kennt
  sie nicht.
- `FabricWireFormatTest` prüft auch `fabric-menu-api-v1:open_screen` gegen `Networking.OpenScreenPayload`: gleiche
  Kennung, Wartung und Zubehör mit Fenster-Id 100 und 1, Titel mit Umlauten, Zusatzdaten byte-genau, und die Fabric API
  liest, was `FabricOpenScreenPayload` schreibt.
- Beitrittstest mit gemischten Loadern: `tools/mp_dedicated_test.sh`, Abschnitt in `docs/testing.md`.
- Beitrittstest `menus` (`PROPWASH_JOINTEST_MENUS=1`, `docs/testing.md`, Abschnitt „Gemischte Loader“): Wartung und
  Zubehör per Schleichen + Rechtsklick und Knopf „Zubehör“ öffnen, mit Add-ons auch an deren Rahmen und Zubehör. Ohne den
  Empfänger für `open_screen` scheitert er mit NeoForge-Client am Fabric-Server (`service=fail`).
- Von Hand durch ETHG, NeoForge 26.3.0.10-beta und 26.3.0.101 mit Propwash 0.5.2 und Just More Parts 1.1.1: direkt auf einem
  Fabric-Server (Fabric API 0.162.0, gleiche Mods), dasselbe hinter Velocity mit FabricProxy-Lite (Modern Forwarding) mit
  Wechsel zwischen zwei Fabric-Servern in beide Richtungen, und auf einem echten Netz mit mehreren Servern: Beitritt,
  Serverwechsel, Drohnen mit Teilen aus Just More Parts bauen und fliegen, danach Einzelspieler (Register wieder normal).
