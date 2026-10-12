# Propwash FPV — Foundation

Dieses Dokument beschreibt das Gerüst, auf dem die Modul-Agenten aufbauen: wie Inhalte, Payloads, Events,
Configs, Client-Registrierungen, Selbsttest-Schritte, Sprachdateien und Mixins angemeldet werden, welche
modulübergreifenden Verträge es gibt (mit Besitzer) und welche Stolperfallen beim Aufbau aufgefallen sind.
Ergänzt `docs/ARCHITECTURE.md` (Paketplan), `docs/CONVENTIONS.md` (verbindliche Code-Regeln) und `docs/api/*.md`
(verifizierte 26.3-APIs).

> **Kein Kommentar im Mod-Code** (`docs/CONVENTIONS.md`): In `common/src`, `fabric/src` und `neoforge/src` stehen keine
> Javadoc-, Block- oder Zeilenkommentare; `python3 tools/strip_comments.py` entfernt übrig gebliebene. Alle modulübergreifenden
> Verträge stehen deshalb **vollständig in Abschnitt 11** dieses Dokuments, nicht im Code. Wer einen
> Vertrag erweitert, ergänzt ihn hier.

> **Abweichungen von `docs/api/*.md` — hier gilt FOUNDATION.md.** Die API-Notizen wurden vor der Foundation
> geschrieben. Ihre **APIs** (Klassen, Methoden, Injection-Ziele von Minecraft/Fabric/NeoForge) stimmen, das
> **Projekt-Layout** darin ist aber veraltet:
>
> | In `docs/api` steht | Stattdessen |
> |---|---|
> | eine Config `propwash.mixins.json`, Paket `de.lardtom.propwash.mixin` (z. B. `mixin/client/CameraMixin`) | eigenes Config `propwash.<modul>.mixins.json`, Paket `de.lardtom.propwash.mixin.<modul>` (z. B. `mixin/drone/client/CameraMixin`, §10) |
| ein `CameraMixin` für Pose **und** FOV, dazu Hand-/Maus-Mixins (`camera-gui.md` 1.3–1.7, 4.) | zwei Besitzer (§11 „Kamera und Ansicht“): drone `mixin.drone.client.CameraMixin` (`alignWithEntity` TAIL) + Hand/Körper/Maus/Frustum; video `mixin.video.client.CameraMixin` (`calculateFov` RETURN) |
| Chunk-Tickets in `QuadcopterEntity#tick`, `PropwashTickets`, `ViewAnchors` im Basispaket (`network-entity.md` 4.2/4.3) | gehören **server** (§11 `server/ServerRules`): Ticket-Typen per `PropwashRegistries.ticketType` in `ServerModule`, Anker/Tickets über `QuadcopterEntity.PILOT_CHANGED` + `ServerBus.TICK_END`, Mixins in `mixin.server` |
> | Paket `net`, `PropwashNet`/`ClientNet` in `net` | Paket `network` (`network.PropwashNet`, `network.client.ClientNet`, §5) |
> | Javadoc/Kommentare in den Code-Beispielen | nur Erklärung; im Mod-Code verboten (`docs/CONVENTIONS.md`) |
> | `MixinMinecraft`, `MixinTitleScreen`, `CommonClass`, `CommonClass.init()` | entfernt; Einstieg `PropwashCommon.init()` / `client.PropwashClient.init()` (§2) |
> | `PropwashItems`, `PropwashBlocks`, `PropwashEntities`, `PropwashComponents`, `PropwashSounds`, `PropwashMenus`, `PropwashTabs`, `RegList.Ref`, eigene `flush(...)`-Aufrufe | Helfer in `registry.PropwashRegistries` + `RegHolder` in einer Klasse des Moduls (§3); vorhandene Holder: `DroneModule.DRONE`, `RaceModule.GATE`, `CoreComponents.*` |
> | Registrierung in `PropwashFabric`/`PropwashNeoForge`/`PropwashFabricClient` selbst schreiben (Keys, Renderer, HUD, Menü-Screens, Commands, Payloads) | nur über `ClientRegistrar` (§8), `ServerBus`/`ClientBus` (§6), `PropwashNet`/`ClientNet` (§5); die Loader-Glue gehört der Foundation |
> | `PropwashKeys`, `PropwashHooks`, `PropwashCommands`, `PropwashTickets` o. Ä. als Klassen im Basispaket | im eigenen Modulpaket anlegen (z. B. `input.client.InputKeys`, `server.ServerCommands`) |
> | `DeferredRegister`/`Registry.register` für Ticket-, Particle- u. a. Typen | `PropwashRegistries.ticketType(...)` bzw. `PropwashRegistries.list(Registries.X).add(...)` (§3) |
> | `DroneRenderState.entityType = PropwashEntities.DRONE` | `DroneModule.DRONE.get()` |
> | `RaceGateEntity`, eigene Gate-Registrierung | `race.GateEntity` + `race.GateSize`, registriert als `RaceModule.GATE` (§11) |
> | `DroneBuild` als gepackter `LONG`/`STRING` synchronisieren (`network-entity.md` 2.3) | eigener Serializer `CoreComponents.DRONE_BUILD_SERIALIZER` (§4) |

## 1. Grundregeln für Modul-Agenten

- Jedes Modul arbeitet nur in **seinem Paket** `de.lardtom.propwash.<modul>` (inkl. `<modul>.client`), in
  **seinem Mixin-Paket** `de.lardtom.propwash.mixin.<modul>` (inkl. `mixin.<modul>.client`) und in **seinen
  Ressourcen**: `common/src/lang/<modul>/`, eigene Assets/Daten unter `assets/propwash/…`/`data/propwash/…` mit
  Dateinamen des Moduls, eigenes Mixin-Config `propwash.<modul>.mixins.json`.
- **Gemeinsame Einzeldateien haben genau einen Besitzer:** `assets/propwash/sounds.json` und alle `SoundEvent`s gehören
  **audio** (registriert in der Common-`init()` `audio.AudioModule`; andere Module lösen Töne über `AudioHooks` aus
  oder melden einen Wunsch an audio);
  alle Sprachschlüssel `part.propwash.*` (`PartInfo#translationKey()`) gehören **content**; Gate-Items und
  Kurs-Werkzeug gehören **race** (§11). Generierte Sprachdateien und der Tages-Changelog werden nach §10 gemergt.
- Foundation-Dateien (`registry`, `network`, `core`, `config`, `platform`, `dev` im Quell-Set `devtest`, `mixin.core`, Loader-Glue in
  `fabric`/`neoforge`, Build-Skripte) **nicht ändern**. Fehlt etwas, im Modul-Bericht als „Foundation-Wunsch“ melden.
- Verträge anderer Module (Abschnitt 11) nur **benutzen**, nicht ändern. Der Besitzer erweitert sie, ohne die
  Signaturen zu brechen (nur ergänzen).
- **Ausnahme von der Paketregel:** `client/render/DronePreview.java` gehört dem drone-Modul (Rendering) und darf von
  ihm geändert werden, obwohl es außerhalb von `de.lardtom.propwash.drone` liegt. Weitere Hilfsklassen legt drone in
  seinem eigenen Paket an.
- **Selbsttest-Modus ändert nichts an der Registrierung:** Welche Payloads, Client-Handler und Registereinträge es gibt,
  darf nie von `SelfTest.enabled()` oder einer Config abhängen (sonst stürzt ein normaler NeoForge-Client ab, während
  der Selbsttest grün ist). Nur Selbsttest-Schritte, -Listener, -Tasten und -HUD-Ebenen dürfen selbsttestabhängig sein.
- Client-Code (alles, was `net.minecraft.client.*` berührt) liegt in `…client`-Paketen und wird nur aus Client-Code
  referenziert. Common-Klassen dürfen keine Client-Typen in Feldern, Signaturen, Casts, `instanceof` oder Lambdas haben.
  Brücken von Common zu Client laufen über Events (Beispiel `QuadcopterEntity.CLIENT_TICK`).
- APIs nur aus `docs/api/*.md` bzw. `.ref/` — 26.3 unterscheidet sich stark von 1.21.
- Code-Regeln aus `docs/CONVENTIONS.md`: keine Kommentare (vor dem Commit `python3 tools/strip_comments.py`), Mixins
  `<Ziel>Mixin` mit `propwash$`-Präfix, englische Namen, Texte nur über Sprachdateien, keine Wildcard-Imports.

## 2. Einstiege und feste Reihenfolge

| Klasse | Aufgerufen von | Module (in dieser Reihenfolge) |
|---|---|---|
| `PropwashCommon.init()` | Fabric `fabric.PropwashFabric#onInitialize`, NeoForge `neoforge.PropwashNeoForge` (Konstruktor) | `core.CoreModule` → `content.ContentModule` → `drone.DroneModule` → `race.RaceModule` → `bando.BandoModule` → `server.ServerModule` → `rescue.RescueModule` → `input.InputModule` → `video.VideoModule` → `audio.AudioModule` → `ui.UiModule` → `osd.OsdModule` → `guide.GuideModule` |
| `client.PropwashClient.init()` | Fabric `fabric.client.PropwashFabricClient#onInitializeClient`, NeoForge `neoforge.client.PropwashNeoForgeClient` (`@Mod(dist = CLIENT)`) | `core.client.CoreClient` → `content.client.ContentClient` → `drone.client.DroneClient` → `race.client.RaceClient` → `bando.client.BandoClient` → `server.client.ServerClient` → `rescue.client.RescueClient` → `input.client.InputClient` → `video.client.VideoClient` → `audio.client.AudioClient` → `ui.client.UiClient` → `osd.client.OsdClient` → `guide.client.GuideClient` → `core.DevExtensions.client()` (lädt `dev.client.DevClientBootstrap` aus dem Quell-Set `devtest`, falls vorhanden) |

**Jedes Modul hat beide Einstiege**, auch wenn seine Logik nur auf einer Seite läuft (audio/input/video/rescue/ui
brauchen die Common-`init()` für SoundEvents und Payloads, server die Client-`init()` für seine S2C-Handler). Was wohin
gehört:

| Common-`init()` (`<modul>.<Name>Module`, Client **und** Dedicated Server) | Client-`init()` (`<modul>.client.<Name>Client`, nur Client) |
|---|---|
| alle Registereinträge (`PropwashRegistries.*`, auch `sound(...)`, `ticketType(...)`, `list(...)`), Entity-Data-Serializer, `PropwashNet.registerC2S`/`registerS2C`, `ServerBus`-Listener, Configs, die beide Seiten brauchen | `ClientNet.handle` (jeder S2C des Moduls), `ClientRegistrar.*`, `ClientBus`-/`QuadcopterEntity.CLIENT_TICK`-Listener, Client-Configs, Selbsttest-Schritte |

**Sperre nach der Common-`init()`:** Am Ende von `PropwashCommon.init()` schließt die Foundation Register, Serializer und
Payload-Anmeldung auf **beiden** Loadern. Jede spätere Anmeldung — also jede aus einer Client-`init()` — wirft
`IllegalStateException` („… nach PropwashCommon.init() angemeldet: … gehören in die Common-init() des Moduls …“). Ohne
die Sperre würde Fabric erst beim Flush werfen, NeoForge die Anmeldung aber stillschweigend nur auf dem Client
registrieren (Register und Kanäle passen dann nicht zum Dedicated Server). Der Selbsttest prüft die Sperre während der
Client-`init()` (Schritt „core: Registrierung“).

Beide `init()` sind idempotent; `PropwashClient.init()` holt `PropwashCommon.init()` notfalls nach (NeoForge
garantiert keine Reihenfolge zwischen zwei `@Mod`-Klassen). In `init()` wird **nur angemeldet**
(Registereinträge, Payloads, Listener, Configs laden, Client-Registrierungen, Selbsttest-Schritte).
Registriert (geflusht) wird danach von der Loader-Glue.

**Thread:** Unter NeoForge laufen die Mod-Konstruktoren (und damit beide `init()`) auf einem
`modloading-worker`-Thread, unter Fabric auf dem Hauptthread. In `init()` daher nichts anfassen, was einen laufenden
Client/Server braucht (`Minecraft.getInstance()`-Zustand, Welt, Fenster, SDL, Sound) — dafür `ClientBus.TICK_END`
bzw. `ServerBus.STARTED` verwenden.

## 3. Registereinträge (`registry.PropwashRegistries`)

```java
public static final RegHolder<Item> MOTOR_2207 = PropwashRegistries.item("motor_2207", p -> p.stacksTo(16));
public static final RegHolder<FpvGogglesItem> GOGGLES = PropwashRegistries.item("goggles_analog", FpvGogglesItem::new, p -> p.stacksTo(1));
public static final PropwashRegistries.BlockWithItem<DroneWorkbenchBlock> WORKBENCH = PropwashRegistries.blockWithItem(
        "drone_workbench", DroneWorkbenchBlock::new, () -> BlockBehaviour.Properties.of().strength(2.5F), p -> p);
public static final RegHolder<BlockEntityType<BenchBE>> BENCH_BE = PropwashRegistries.blockEntity("drone_workbench", BenchBE::new, WORKBENCH.block());
public static final RegHolder<EntityType<HomeMarkerEntity>> HOME_MARKER = PropwashRegistries.entity("home_marker",
        () -> EntityType.Builder.<HomeMarkerEntity>of(HomeMarkerEntity::new, MobCategory.MISC).sized(0.5F, 2F).clientTrackingRange(8).noUpdateInterval());
public static final RegHolder<DataComponentType<Integer>> COURSE_ID = PropwashRegistries.component("course_id",
        b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
public static final RegHolder<SoundEvent> BEEP = PropwashRegistries.sound("beep");                // nur audio, in audio.AudioModule (§10)
public static final RegHolder<MenuType<WorkbenchMenu>> MENU = PropwashRegistries.menu("drone_workbench", WorkbenchMenu::new);
public static final RegHolder<MenuType<TuneMenu>> TUNE = PropwashRegistries.extendedMenu("tune", TuneMenu::new, ByteBufCodecs.VAR_INT);
public static final RegHolder<CreativeModeTab> TAB = PropwashRegistries.creativeTab("main", b -> b
        .title(Component.translatable("itemGroup.propwash.main")).icon(() -> new ItemStack(GOGGLES.get()))
        .displayItems((params, out) -> out.accept(GOGGLES.get())));
public static final RegHolder<RecipeSerializer<MyRecipe>> SERIALIZER = PropwashRegistries.recipeSerializer("my_recipe", MyRecipe.CODEC, MyRecipe.STREAM_CODEC);
public static final EntityDataSerializer<Foo> FOO = PropwashRegistries.entityDataSerializer("foo", EntityDataSerializer.forValueType(Foo.STREAM_CODEC));
public static final RegHolder<TicketType> DRONE_TICKET = PropwashRegistries.ticketType("drone", 40L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION);
// Jedes weitere statische Register (Rezepttypen, Recipe-Book-Kategorien, Particle-Typen, …):
public static final RegHolder<RecipeBookCategory> BENCH_BOOK = PropwashRegistries.list(Registries.RECIPE_BOOK_CATEGORY).add("drone_parts", key -> new RecipeBookCategory());
public static final RegHolder<RecipeType<MyRecipe>> MY_TYPE = PropwashRegistries.list(Registries.RECIPE_TYPE).add("my_recipe", key -> new RecipeType<MyRecipe>() {
    @Override public String toString() { return key.identifier().toString(); } });
// Particle-Typen: SimpleParticleType hat einen protected Konstruktor → anonyme Unterklasse: key -> new SimpleParticleType(false) {}
```

- Diese Felder stehen in einer Klasse des Moduls, die in dessen **Common**-`init()` geladen wird (z. B.
  `ContentItems.bootstrap()` aufrufen oder die Felder direkt in `ContentModule` halten). Eine Klasse, die erst in der
  Client-`init()` geladen wird, darf keine Registereinträge enthalten (Sperre, §2).
- `RegHolder#get()` erst zur Laufzeit bzw. in Fabriken, die nach dem Register laufen. Vorher: `IllegalStateException`.
- `Item.Properties#setId`/`BlockBehaviour.Properties#setId` setzt der Helfer. BlockItems bekommen
  `useBlockDescriptionPrefix()` (Name `block.propwash.<id>`).
- Eigene BlockItem-Klasse (z. B. für Tooltips): `PropwashRegistries.blockItem(block, BiFunction<Block, Item.Properties, BlockItem>,
  props)` bzw. `blockWithItem(name, blockConstructor, blockProps, HelpBlockItem::new, itemProps)` (Gameplay-Loop v2;
  benutzt von content für Werkbank und Ladegerät).
- Keine `ItemStack`s während der Registrierung bauen (`icon`/`displayItems` sind lazy, `static final ItemStack` nicht).
- Menü mit Öffnungsdaten nur mit `Services.PLATFORM.openMenu(player, provider, codec, data)` öffnen.
- **Flush-Reihenfolge der Register ist auf beiden Loadern gleich**, nämlich die von NeoForge
  (`GameData.getRegistrationOrder`): zuerst `attribute`, `data_component_type`, `particle_type`, dann alle
  Vanilla-Register in Vanilla-Reihenfolge (u. a. Sounds → Mob-Effekte → Blöcke → Entity-Typen → Items → Block-Entity-Typen
  → Menüs → Rezept-Serializer → Creative-Tabs → Ticket-Typen), dann Mod-Register alphabetisch. NeoForge feuert dafür je
  Register ein `RegisterEvent`, Fabric flusht in `onInitialize` nach `PropwashRegistries.inFlushOrder()` (berechnet aus
  `PropwashRegistries.registrationOrder()`). Innerhalb eines Registers gilt die Anmeldereihenfolge. Eine Fabrik darf also
  `get()` auf Einträge früherer Register aufrufen (z. B. ein Block auf einen eigenen Particle-Typ, ein Item auf ein
  eigenes Attribut), nie auf spätere.
- Entity-Data-Serializer sind unabhängig davon (Fabric: vor allen Registern über `FabricEntityDataRegistry`, NeoForge:
  Register `neoforge:entity_data_serializers`, also zuletzt). Keine Fabrik darf beim Registrieren von ihnen abhängen;
  `SynchedEntityData.defineId` in der Entity-Klasse ist unkritisch.
- **Weitere Register ohne Foundation-Änderung:** `PropwashRegistries.list(Registries.X)` liefert (bzw. legt an) die
  Liste für jedes statische Register aus `BuiltInRegistries.REGISTRY`; die Loader-Glue flusht alle Listen. Nur in
  `init()` aufrufen. Datapack-Register (Biome, Schadensarten, Dimensionen, …) werfen dort — die sind JSON unter
  `data/propwash/…`. `RegList` hat keinen öffentlichen Konstruktor (eine selbst gebaute Liste würde nie geflusht).
- Ticket-Typen: `PropwashRegistries.ticketType(name, timeout, flags)` (common `new TicketType(timeout, flags)`, auf
  NeoForge binärkompatibel); Benutzung siehe `docs/api/network-entity.md` 4.3. Nie `(0L, 15)` (= `FORCED`).
- Game-Rules (seit 0.5.0): `PropwashRegistries.list(Registries.GAME_RULE).add(name, key -> new GameRule<>(kategorie, typ,
  argument, besucher, codec, ergebnis, standard, FeatureFlagSet.of()))` – Vanilla-Register `minecraft:game_rule`, Id
  `propwash:<name>`, Texte `gamerule.propwash.<name>` und `….description`; Werte nur serverseitig über
  `server.getGameRules().get(regel)`, Clients bekommen sie per eigenem S2C. Beispiel `interact.InteractRules`.

## 4. Core-Daten

| Was | Wo | Inhalt |
|---|---|---|
| `DroneBuildCodecs` | `core` | `CODEC` (RecordCodecBuilder, Felder `frame, stack, motor, prop, video, battery`), `STREAM_CODEC`, je Bauteil-Enum `Codec`/`StreamCodec`; **immer über den stabilen `key()`**, nie Ordinalzahl |
| `PartCategory` | `core` | `FRAME, STACK, MOTOR, PROP, VIDEO, BATTERY`; `key()`, `parts()`, `requiredCount()` (4 für Motor/Prop), `resolve(key)`, `get(build)`, `with(build, part)`, `of(part)`, `CODEC`, `STREAM_CODEC` |
| `PartRef` | `core` | `record (PartCategory category, String key)`, `CODEC` (`{"category":"motor","key":"m2207_1750"}`), `STREAM_CODEC`, `of(part)`, `resolve()`, `resolve(Class)` |
| `CoreComponents.DRONE_BUILD` | `propwash:drone_build` | `DroneBuild`, gespeichert + synchronisiert |
| `CoreComponents.BATTERY_CHARGE` | `propwash:battery_charge` | `Float` 0..1 (Codec prüft den Bereich; beim Lesen trotzdem klemmen) |
| `CoreComponents.PART` | `propwash:part` | `PartRef` |
| `CoreComponents.FLIGHT_TUNE` | `propwash:flight_tune` | `FlightTune` (Tuning des Flight-Controllers, §11), gespeichert + synchronisiert |
| `FlightTune` | `core` | `record (Map<String, Float> values)`, unveränderlich und sortiert; max. 256 Einträge, Schlüssel 1–64 Zeichen, nur endliche Werte (sonst `IllegalArgumentException`, im `CODEC` ein `DataResult`-Fehler); `EMPTY`, `get(key, fallback)`, `has`, `with`, `without`, `CODEC`, `STREAM_CODEC` |
| `CoreComponents.DRONE_BUILD_SERIALIZER` | Entity-Data-Serializer `propwash:drone_build` | für `SynchedEntityData.defineId` (benutzt von `QuadcopterEntity`) |
| `CoreComponents.BOUND_DRONE` | `propwash:bound_drone` | `core.BoundDrone` (`record (UUID drone, String name, FrameType frame, VideoSystem video)`, Name höchstens 48 Zeichen, `displayName()` = Name oder Rahmen-Kurzname); auf der Funke: an welche Drohne sie gebunden ist (Brillen tragen sie seit 0.5.0 nicht mehr, alte Einträge werden entfernt). Speicherformat über Rahmen-Key und Systemnamen (`analog`/`digital`/`creative`), im Netz das Ordinal des Systems |
| `CoreComponents.DRONE_ID` | `propwash:drone_id` | `UUID` der Drohne (= Entity-UUID der platzierten Drohne) auf dem Drohnen-Item; `QuadcopterItem#useOn` übernimmt sie, solange keine Entity mit dieser UUID existiert |
| `CoreComponents.BATTERY_INSTALLED` | `propwash:battery_installed` | `Boolean`, nur vorhanden (`false`), wenn das Drohnen-Item keinen Akku enthält; fehlt = Akku eingesetzt |
| `propwash:battery_charge` auf Akku-Items | | Akku-Items haben ihn im Prototyp (1,0); Schreiben nur über `content.Batteries.storeCharge` (auf 1/1000 gerundet, ab 0,999 exakt 1,0, damit volle Akkus stapeln) |

## 5. Netzwerk (`network.PropwashNet`, `network.client.ClientNet`)

```java
// Common-init() <modul>.<Name>Module (Client UND Server; auch für reine Client-Module wie input/video/ui):
PropwashNet.registerC2S(ArmC2S.TYPE, ArmC2S.CODEC, (payload, ctx) -> { /* Server-Thread; ctx.player(), ctx.level(), ctx.server() */ });
PropwashNet.registerS2C(RaceEventS2C.TYPE, RaceEventS2C.CODEC);
// Client-init() <modul>.client.<Name>Client:
ClientNet.handle(RaceEventS2C.TYPE, (payload, ctx) -> { /* Render-Thread; ctx.minecraft(), ctx.player() */ });
// Senden:
ClientNet.sendToServer(new ArmC2S(id, true));            // false, wenn nicht verbunden/Kanal unbekannt
PropwashNet.sendToPlayer(serverPlayer, payload);          // prüft canSend
PropwashNet.sendToTrackingAndSelf(entity, payload);       // alle Tracker + Entity selbst (falls Spieler), prüft canSend
PropwashNet.canSend(serverPlayer, TYPE); ClientNet.canSend(TYPE);
```

- Payload = `record … implements CustomPacketPayload` mit `Type` im Namespace `propwash` und `StreamCodec`
  (Beispiel: `drone.net.TuneC2S`). Nur in der Common-`init()` anmelden (C2S **und** S2C), sonst
  `IllegalStateException` (§2). Ids nur im Namespace
  `propwash`, jede Id nur einmal. Alle Kanäle sind Pflichtkanäle (nicht optional).
- **Jeder S2C-Payload braucht einen Client-Handler, bedingungslos** (`ClientNet.handle` in der Client-`init()`, nie nur
  im Selbsttest oder abhängig von einer Config). NeoForge bricht sonst den Client-Start mit „Some clientbound payloads
  are missing client-side handlers“ ab; `ClientNet.freeze()` prüft das auf **beiden** Loadern und bricht mit
  „Server→Client-Payloads ohne Client-Handler: […]“ ab. Soll ein Handler zeitweise nichts tun, prüft er das im Rumpf.
- Payloads, die nur Entwicklern dienen, trotzdem immer registrieren (Kanalmengen müssen auf beiden Seiten gleich
  sein) und im Handler abschalten. Reine Entwickler-Payloads (`dev.network.SelfTestNet`) liegen im Quell-Set `devtest`
  und werden von `dev.DevBootstrap` angemeldet, sobald `devtest` im Klassenpfad ist (Selbst- und Rauchtest-Runs, dort
  auf Client und integriertem bzw. Dev-Server gleich); das Release-Jar kennt sie nicht.
- Handler laufen auf beiden Loadern auf dem Hauptthread (Server- bzw. Render-Thread). Jeder S2C-Payload hat genau
  einen Client-Handler. `ClientNet.sendToServer` liefert `false`, wenn nicht verbunden oder der Server den Kanal nicht
  kennt; `PropwashNet.sendToPlayer` liefert `false`, wenn der Client den Kanal nicht kennt.
- Jeden C2S-Payload serverseitig validieren (Absender = Pilot, Entity im selben Level, Werte endlich, Schrittweite plausibel).
- Limits: C2S 32 767 Bytes, S2C 1 MiB.
- **Protokollversion `Constants.PROTOCOL_VERSION`:** um 1 erhöhen, sobald sich das Format eines Payloads ändert (Feld
  dazu/weg/anderer Typ, Payload neu oder entfernt, andere Bedeutung eines Werts). Client und Server mit verschiedenen
  Versionen werden dann schon beim Verbinden mit einer klaren Meldung getrennt statt später mit „Network protocol error“:
  NeoForge über `event.registrar(PROTOCOL_VERSION)` (eigene Meldung „neoforge.network.negotiation.failure.version.mismatch“),
  Fabric über eine Konfigurationsaufgabe der Foundation (`network.ProtocolVersionPayload`, Kanal
  `propwash:protocol_version`, nur Konfigurationsphase): der Server schickt Protokoll + Mod-Version, der Client antwortet
  mit seinen, bei Abweichung trennt der Server mit `propwash.network.version_mismatch` (Server-/Client-Version und
  Protokoll). Ein Client ohne diesen Kanal (Propwash fehlt oder zu alt) wird mit `propwash.network.version_missing`
  getrennt. Beide Texte haben einen englischen Fallback für Clients ohne den Sprachschlüssel. Die Protokollversion
  ist unabhängig von der Mod-Version: Versionen mit gleichem Payload-Format bleiben kompatibel.
  Ab 0.5.0 (Protokoll 13) läuft davor die Beitritts-Prüfung `propwash:join_info` (Abschnitt „Beitritts-Prüfung“ unten,
  `docs/design/join-check.md`): Clients mit diesem Kanal vergleichen Protokoll und Add-ons darüber, `protocol_version`
  bleibt nur für ältere Clients.

## 6. Events (`core.event.ServerBus`, `core.client.ClientBus`)

`Event<T>` ist eine einfache Listener-Liste: `ServerBus.TICK_END.register(server -> …)`. Listener werden in
Registrierungsreihenfolge aufgerufen; Ausnahmen werden nicht geschluckt (sie sollen laut auffallen). Registrieren ist
jederzeit erlaubt (copy-on-write), üblich in der `init()` des Moduls. `fire(...)` gehört der Foundation (Loader-Glue,
Core-Mixin); Module feuern keine Foundation-Events.

**Thread-Garantie:** Alle `ServerBus` laufen auf dem **Server-Thread**, alle `ClientBus` auf dem
**Render-Thread** — auf beiden Loadern, geprüft vom Selbsttest (Schritte „core: Threads und Sitzung“ und „core: Events
nach dem Verlassen“ schlagen fehl, wenn ein Hook auf einem anderen Thread feuert).

| Event | Thread | Zeitpunkt | Fabric | NeoForge |
|---|---|---|---|---|
| `ServerBus.TICK_END` | Server | Ende jedes Server-Ticks (nach allen Level-Ticks) | `ServerTickEvents.END_SERVER_TICK` | `ServerTickEvent.Post` |
| `ServerBus.PLAYER_JOIN` | Server | Ende von `PlayerList#placeNewPlayer`: Spieler im Level, Kanäle ausgehandelt, `canSend` gültig | `ServerPlayerEvents.JOIN` | `PlayerEvent.PlayerLoggedInEvent` |
| `ServerBus.PLAYER_LEAVE` | Server | Anfang von `PlayerList#remove`: Spieler noch im Level, noch nicht gespeichert; genau einmal je `PLAYER_JOIN` (Trennen, Kick, Serverstopp) | `ServerPlayerEvents.LEAVE` | `PlayerEvent.PlayerLoggedOutEvent` |
| `ServerBus.STARTED` / `STOPPED` | Server | Server gestartet / vollständig gestoppt (im Einzelspieler einmal pro Welt) | `ServerLifecycleEvents.SERVER_STARTED/STOPPED` | `ServerStartedEvent` / `ServerStoppedEvent` |
| `ServerBus.REGISTER_COMMANDS` | Server/Init | bei jedem Datapack-(Re)Load; im Dev zusätzlich beim Bootstrap (nur Baum bauen, keinen Server-Zustand anfassen) | `CommandRegistrationCallback` | `RegisterCommandsEvent` |
| `ServerBus.LEVEL_UNLOAD` | Server | Server-Level wird entladen (Serverstopp, NeoForge auch Dimensionsentladung) | `ServerLevelEvents.UNLOAD` | `LevelEvent.Unload` (nur `ServerLevel`) |
| `ClientBus.TICK_END` | Render | Ende jedes Client-Ticks (auch Titelbildschirm, nicht im Start-Ladebildschirm) | `ClientTickEvents.END_CLIENT_TICK` | `ClientTickEvent.Post` |
| `ClientBus.FRAME_START` | Render | jeder Frame in `Minecraft#renderFrame` vor `GameRenderer#update` (vor Kamera/Picking/Extraktion/Level-Render), mit und ohne Welt; `DeltaTracker` ist für diesen Frame fortgeschrieben | Core-Mixin `mixin.core.client.MinecraftMixin` | dito |
| `ClientBus.LOGIN` | Render | Welt/Server betreten (`mc.player`, `mc.level` gesetzt); danach `inSession() == true` | `ClientPlayConnectionEvents.JOIN` → `ClientBus.fireLogin` | `ClientPlayerNetworkEvent.LoggingIn` → dito |
| `ClientBus.DISCONNECT` | Render | **genau einmal nach jedem `LOGIN`**, nie ohne vorheriges `LOGIN`: Anfang von `Minecraft#disconnect(Screen, boolean, boolean)` (Welt verlassen, Kick, Verbindungsabbruch, Spiel beenden) bzw. `Minecraft#clearClientLevel` (Server schickt den Client zurück in die Konfiguration; danach folgt ein neues `LOGIN`). `mc.player`/`mc.level` sind noch gesetzt, die Verbindung ist noch offen | Core-Mixin `MinecraftMixin` → `ClientBus.fireDisconnect` | dito |
| `ClientBus.KEY_MAPPINGS_REGISTERED` | Render | einmal, nachdem alle Propwash-Tasten beim Loader sind | nach Flush im Client-Entrypoint | Ende `RegisterKeyMappingsEvent` |
| `ClientBus.REGISTER_HUD_LAYERS` | Render/Init | einmal, letzte Chance für `ClientRegistrar.hudLayer/hideVanillaHud` | im Client-Entrypoint | Beginn `RegisterGuiLayersEvent` |
| `ClientBus.SUBMIT_WORLD_GEOMETRY` | Render | jeder Level-Frame in `LevelRenderer#submitFeatures`, nach Entities/Block-Entities/Partikeln, **vor** `finalizeGizmoCollection()` (also dürfen Listener auch `Gizmos.*` benutzen). Argumente `(SubmitNodeCollector, PoseStack, LevelRenderState)`; Koordinaten kamerarelativ zu `levelState.cameraRenderState.pos` (siehe `docs/api/post-render.md` §7) | `LevelRenderEvents.BEFORE_GIZMOS` | `SubmitCustomGeometryEvent` |
| `QuadcopterEntity.CLIENT_TICK` | Render | jede Drohne im Client-Level, jeden Client-Tick | `QuadcopterEntity#tick` | dito |
| `QuadcopterEntity.PILOT_CHANGED` | Server | Pilot einer Drohne ändert sich (`setPilot`, Entfernen der Entity), §11 | `QuadcopterEntity#setPilot`/`onRemoval` | dito |

`ClientBus.inSession()` sagt, ob gerade eine Sitzung zwischen `LOGIN` und `DISCONNECT` läuft. Die Glue ruft
`fireLogin`/`fireDisconnect` (idempotent, ein zweites `LOGIN` ohne `DISCONNECT` wird verschluckt, ebenso ein
`DISCONNECT` ohne `LOGIN`); ein Aufruf von einem fremden Thread wird auf den Render-Thread umgeleitet.

## 7. Configs (`config.JsonConfig`)

```java
public final class VideoConfig { public int fov = 120; public boolean noise = true; }
public static final JsonConfig<VideoConfig> CONFIG = JsonConfig.create("video", VideoConfig.class, VideoConfig::new,
        c -> c.fov = Mth.clamp(c.fov, 60, 170));   // optionaler Validator
// init(): CONFIG.load();   Laufzeit: CONFIG.get().fov   nach Änderung: CONFIG.save();
```

Datei: `<config>/propwash/<name>.json` (Gson, formatiert). Schema = POJO mit öffentlichen Feldern, Standardwerten in
den Feld-Initialisierern und Konstruktor ohne Argumente. Fehlt die Datei → Standardwerte und schreiben; fehlen Felder →
Standardwert und Datei neu schreiben (unbekannte Felder fallen weg); kaputtes JSON → nach `<name>.json.broken`
verschieben, Standardwerte. Der Validator läuft nach jedem Laden. `get()` vor `load()` liefert die Standardwerte,
`set(value)` ersetzt und speichert, `save()` schreibt atomar und loggt Fehler statt zu werfen. Nicht thread-sicher:
Laden/Speichern immer auf einem Thread (Init, Client- oder Server-Thread). Jedes Modul definiert sein eigenes Schema in seinem Paket; Beispiel: `core.CoreConfig` (`core.json`).
Werte, die Clients vom Server brauchen, per S2C-Payload bei `PLAYER_JOIN` schicken. Beispiel Server-Config (server):
`ServerModule.init()` lädt die Config, registriert den S2C und sendet ihn in `ServerBus.PLAYER_JOIN`;
`server.client.ServerClient.init()` registriert den Handler (`ClientNet.handle`) und verwirft die Werte bei
`ClientBus.DISCONNECT`.

## 8. Client-Registrierungen (`registry.client.ClientRegistrar`)

```java
public static final KeyMapping ARM = ClientRegistrar.keyMapping("arm", InputConstants.KEY_B);   // key.propwash.arm, Kategorie key.category.propwash.main
ClientRegistrar.entityRenderer(RaceModule.GATE, GateRenderer::new);
ClientRegistrar.blockEntityRenderer(ContentBlocks.BENCH_BE, BenchRenderer::new);
ClientRegistrar.modelLayer(GateModel.LAYER, GateModel::createLayer);
ClientRegistrar.menuScreen(ContentMenus.MENU, WorkbenchScreen::new);
ClientRegistrar.hudLayer(Constants.id("osd"), OsdHud::extract);                 // unter dem Chat, bei F1 automatisch aus
ClientRegistrar.hudLayer(Constants.id("video_feed"), VideoOverlay::extract, HudVisibility.ALWAYS);   // auch bei F1, unter allen WITH_HUD-Ebenen
ClientRegistrar.hideVanillaHud(VanillaHudLayer.HOTBAR, () -> PilotSession.INSTANCE.isActive());
public static final RenderPipeline FPV_ANALOG = ClientRegistrar.renderPipeline(RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
        .withLocation(Constants.id("pipeline/fpv_analog"))/* … */.build());      // beim Loader als optionale Pipeline vorkompiliert
ClientRegistrar.reloadListener(Constants.id("osd_glyphs"), new OsdFonts.ReloadListener());   // bei jedem Ressourcen-Reload
ClientBus.SUBMIT_WORLD_GEOMETRY.register((collector, poseStack, levelState) -> HomeMarker.submit(collector, poseStack, levelState.cameraRenderState.pos));
```

- Nur in der Client-`init()`; danach wirft jede Registrierung. Tasten-Standardwerte sind **SDL-Scancodes**
  (`InputConstants.KEY_*`), Tasten im Client-Tick mit `consumeClick()`/`isDown()` auslesen.
- HUD: `HudLayer#extract(GuiGraphicsExtractor, DeltaTracker)`; Farben mit Alpha (`0xFFFFFFFF`), nie
  `blurBeforeThisStratum()`. Sichtbarkeit (`HudVisibility`, auf beiden Loadern gleich):
  - `WITH_HUD` (Standard, `hudLayer(id, layer)`): Anzeigen wie OSD oder Renn-HUD. Bei F1 aus (Foundation umhüllt die
    Ebene zusätzlich mit `hud.isHidden()`), gezeichnet direkt unter dem Chat (Fabric `attachElementBefore(CHAT)`,
    NeoForge `registerBelow(CHAT)`), untereinander in Anmeldereihenfolge.
  - `ALWAYS`: Ebenen, die das Bild selbst verändern (Videobild-Ersatz, Störbild, „Kein Signal“-/„Keine Brille“-Schwarzbild)
    und sich deshalb nicht per F1 abschalten lassen dürfen. Gezeichnet direkt nach dem Schlaf-Overlay, das Vanilla auch
    bei F1 zeichnet (Fabric `attachElementAfter(SLEEP)`, NeoForge `registerAbove(SLEEP_OVERLAY)`), also **unter** allen
    `WITH_HUD`-Ebenen, untereinander in Anmeldereihenfolge. Nur für echte Bildeffekte verwenden.
- `VanillaHudLayer` bildet die unterschiedlichen Loader-Ids ab (Fabric `VanillaHudElements`, NeoForge `VanillaGuiLayers`).
- `renderPipeline(pipeline)` meldet eine eigene `RenderPipeline` (Namespace `propwash`, jede Location einmal) zum
  Vorkompilieren an: NeoForge `RegisterRenderPipelinesEvent#registerOptionalPipeline`, Fabric
  `RenderPipelines.registerOptional` im Client-Entrypoint. Immer **optional** (eine fehlerhafte Pipeline bricht so nicht
  das Resource-Reload ab). Nicht angemeldete Pipelines funktionieren trotzdem, werden aber beim ersten Gebrauch synchron
  kompiliert (Ruckler, nach jedem F3+T erneut). Die Pipeline einmal als `static final` bauen.
- `configScreen(parent -> Screen)` meldet **den** Einstellungs-Screen der Mod an (genau einmal, gehört ui:
  `UiClient` meldet `ConfiguratorScreen.create(parent, null)` an). Er muss ohne Welt und ohne Spieler funktionieren
  (Aufruf aus der Mod-Liste auf dem Titelbildschirm) und kehrt beim Schließen zu `parent` zurück. NeoForge trägt ihn als
  `IConfigScreenFactory` ein (Knopf „Konfiguration“ in der Mod-Liste). Fabric hat keinen eigenen Einstieg dafür; eine
  Mod-Menu-Anbindung `compat.modmenu.ModMenuCompat` (nur im Fabric-Modul, Entrypoint `modmenu` in `fabric.mod.json`)
  gibt `ClientRegistrar.configScreenFactory()` an Mod Menu weiter; Mod Menu ist nur `compileOnly`
  (`modmenu_version` in `gradle.properties`, Maven TerraformersMC) und zur Laufzeit optional.
- `specialModelRenderer(Identifier id, MapCodec<? extends SpecialModelRenderer.Unbaked<?>> codec)` meldet einen eigenen
  Typ für `minecraft:special`-Item-Modelle an (Namespace `propwash`, jede Id einmal, nur Client-`init()`). Glue: NeoForge
  `RegisterSpecialModelRendererEvent#register`, Fabric `SpecialModelRenderers.ID_MAPPER.put` im Client-Entrypoint (Feld
  über den transitiven Access-Widener von fabric-api zugänglich). Benutzer: drone (`propwash:remote_gimbals`).
- `pictureInPictureRenderer(Class<T> stateClass, Supplier<? extends PictureInPictureRenderer<T>> factory)` meldet einen
  eigenen Bild-im-Bild-Renderer für GUI-3D an (ein Renderer je State-Klasse, nur Client-`init()`). Glue: Fabric
  `PictureInPictureRendererRegistry.register` im Client-Entrypoint, NeoForge `RegisterPictureInPictureRenderersEvent#register`
  (Mod-Bus). Den State stellt man per `GuiLayerAccess#propwash$renderState().addPicturesInPictureState(...)` in die GUI.
  Benutzer: guide (`guide.client.render.SceneRenderState`/`SceneRenderer`). API-Notiz: `docs/api/gui-scene.md`.
- `blockTints(Supplier<? extends Block> block, List<BlockTintSource> layers)` meldet die Farbebenen eines eigenen Blocks an
  (Ebene *i* färbt Flächen mit `"tintindex": i`; nur Client-`init()`). Glue: Fabric `BlockColorRegistry.register` im
  Client-Entrypoint, NeoForge `RegisterColorHandlersEvent.BlockTintSources#register` (Mod-Bus). `colorInWorld` läuft beim
  Chunk-Bau auf Worker-Threads und darf nur lesen. Benutzer: content (`content.client.ChargerPackTint`, Akkufarbe je Schacht).
- `reloadListener(Identifier id, PreparableReloadListener listener)` meldet einen Client-Reload-Listener an (Namespace
  `propwash`, jede Id einmal, nur Client-`init()`), der bei jedem Laden der Ressourcenpakete (Start, F3+T,
  Paketwechsel) läuft. Glue: Fabric `ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener` im
  Client-Entrypoint, NeoForge `AddClientReloadListenersEvent#addListener` (Mod-Bus). Schwere Arbeit gehört in
  `prepare` (`SimplePreparableReloadListener`, Hintergrund-Thread), `apply` läuft im Client-Thread. Benutzer: osd
  (`propwash:osd_glyphs`, Glyphentabelle des OSD).
- Nicht abgedeckt (bei Bedarf Foundation-Wunsch): Particle-Provider.

## 9. Selbsttest (`dev.SelfTest`, Quell-Set `devtest`)

Alles in diesem Abschnitt liegt in `common/src/devtest/java` (gleiche Pakete wie der Mod-Code) und ist nicht im
Release-Jar. Die Loader kompilieren `devtest` gegen `main` (`check` hängt davon ab) und laden es nur in den Runs
`runClientSelftest`, `runClientSmoke` und `runServerSmoke` (NeoForge führt es als zweites Quell-Set der Mod in allen
Dev-Runs; ohne Property bleibt es inaktiv). Einstieg ist `dev.DevBootstrap.init()` (Common) bzw.
`dev.client.DevClientBootstrap.init()` (Client), beide über `core.DevExtensions` per Reflection geladen. Die Reihenfolge
der Schritte legt `DevClientBootstrap` fest: core → content (inkl. Überlebensmodus) → drone-render → dust → drone (inkl.
Spielablauf) → race → server → rescue → input → video → audio → ui → osd → tricks → guide → assets-world → Bildschirmfoto-Rundgang.
Davor setzt der Schritt „dev: Propwash-Tasten auf Standard“ alle Propwash-Tasten auf ihre Standardwerte, damit alte
Belegungen aus der `options.txt` des Run-Verzeichnisses weder Tests noch Bildschirmfotos (Flugschul-Tastenhinweise) verfälschen.
Danach stellt „dev: FPV-Darstellung auf Standard“ die Schalter „Props im FPV-Bild“ und „Ducts im FPV-Bild“ zurück, falls ein
abgebrochener Lauf sie mitten im Bildschirmfoto-Rundgang aus gelassen hat (sonst scheitern die FPV-Schritte des nächsten Laufs).
Neue Schritte eines Moduls stehen in `<modul>.client.<Name>SelfTests` (öffentliches `register()`) und werden dort
eingetragen; der Mod-Code verweist nie auf eine Klasse aus `devtest`.

- Start: `JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew :fabric:runClientSelftest` bzw. `:neoforge:runClientSelftest`
  (eigenes, stummes Run-Verzeichnis `runs/selftest-client`). Ohne `-Dpropwash.selftest=true` ist alles inaktiv.
- Ablauf: Titelbildschirm → Flachwelt „Propwash Selftest“ (Seed 1, Kreativ, keine Mobs, Zeit/Wetter stehen) →
  40 Ticks warten → Schritte der Reihe nach → Welt verlassen (integrierter Server stoppt) → Schritte „nach dem
  Verlassen“ → Log `PROPWASH SELFTEST PASS` bzw. `PROPWASH SELFTEST FAIL <Gründe>` → `propwash-selftest-result.txt` →
  Spiel beenden. Der Gradle-Task schlägt fehl, wenn das Ergebnis nicht PASS ist. Jeder Schritt läuft einmal pro
  Client-Tick auf dem Render-Thread; nach `maxTicks` ohne Ergebnis oder bei einer Ausnahme zählt er als FAIL; alle
  Schritte laufen, auch wenn ein früherer fehlschlägt.
- Eigene Schritte in `register()` der `SelfTests`-Klasse des Moduls:

```java
SelfTest.clientStep("race: Gate spawnen", 100, ctx -> {
    if (ctx.tick() == 0) ctx.state().put("f", ctx.onServer(server -> { /* Server-Thread */ }));
    return bedingung ? SelfTest.StepResult.pass() : SelfTest.StepResult.pending();   // oder fail("Grund")
});
```

- Schritte, die erst nach dem Verlassen der Welt prüfbar sind (Ressourcen freigegeben, Zustand zurückgesetzt, Events
  beim Trennen), mit `SelfTest.afterLeaveStep(name, maxTicks, ctx -> …)` anmelden; dort ist `ctx.server()` null und
  der Client steht auf dem Titelbildschirm.
- Eingebaute Core-Schritte (`dev.client.CoreSelfTests`): Registrierung (Components, Entity-Typen `drone`/`gate`,
  Serializer `drone_build`/`flight_tune`, Flush-Reihenfolge, Sperre nach der Common-`init()`: SoundEvent, Payload und
  Serializer aus der Client-`init()` werden abgelehnt), Codecs (DroneBuild/PartRef/FlightTune/ItemStack-Roundtrip), Events (alle Hooks
  gefeuert), Netzwerk-Ping/Pong (inkl. `sendToTrackingAndSelf`), Drohne auf dem Server spawnen (vorher
  `loadFromStack`/`toStack` mit und ohne Komponenten, `gogglesSystem`) → auf dem Client mit allen synchronisierten
  Feldern inkl. `tune()` sehen → entfernen → `PILOT_CHANGED` genau zweimal (leer → Pilot, Pilot → leer beim Entfernen),
  auf dem Server-Thread, Tasten & HUD, Config-Datei, Threads und Sitzung (jedes
  Server-/Client-Event auf dem richtigen Thread, genau ein `LOGIN`); nach dem Verlassen: genau ein `DISCONNECT`, ein
  `PLAYER_LEAVE`, ein `STOPPED`, alles auf dem richtigen Thread.
- Globaler Timeout 30 min (Ticks), Watchdog 46 min (echte Zeit, beendet die JVM hart); der volle Lauf mit
  Bildschirmfotos hat nach Welle 3 658 Schritte und braucht in der Welt gut 21 min.
- Der Lauf endet immer von selbst, auch nach FAIL oder Absturz (`SelfTestRunner`, Thread `Propwash-Selftest-Exit`):
  Läuft das Spiel 120 s nach dem geschriebenen Ergebnis noch, beendet er den Prozess (Ergebnisdatei bleibt); steckt der
  Spiel-Thread nach einem Absturz 30 s in `Minecraft.emergencySave` oder 5 min im Anhalten des integrierten Servers
  (`MinecraftServer.halt`), schreibt er `FAIL Spiel hängt in …` und beendet die JVM mit Code 3. Hintergrund:
  `getChunk`/`getChunkFuture` mit Erzeugen warten im Server-Thread in `managedBlock`; hängt die Erzeugung (z. B. nach
  einem Absturz im Licht-Thread), wartet beim Notspeichern auch `IntegratedServer.halt` ewig. Die Lage-Prüfung der
  Bandos (168 Plätze, die größte Chunk-Last im Lauf) fordert deshalb mit `ServerChunkCache.addTicketAndLoadWithRadius`
  an, ohne den Server-Thread anzuhalten, prüft erst, wenn alle Chunks mit `getChunkNow` da sind (sonst bis zu drei neue
  Versuche), und bricht ab, sobald der Server anhält. Die Mess- und Lastschritte der Bandos (`BandoWorldTests`,
  `BandoStressTests`) laden weiter im Server-Thread, damit ihre Messwerte mit früheren Läufen vergleichbar bleiben.
- Beim Entwickeln nur die eigenen Schritte: `-PpropwashSelftestOnly=<Namensanfänge, durch Komma getrennt>` (z. B.
  `'race:,tricks:'`; die ältere Umgebungsvariable `PROPWASH_SELFTEST_ONLY` mit `|` gilt weiter), dazu laufen die
  Rahmen-Schritte aus `SelfTest.baseStep`/`setupStep`/`switchStep`. `-PpropwashSelftestList=true` schreibt nur die
  Schrittliste `propwash-selftest-steps.md` ins Run-Verzeichnis. Präfixe, Regeln, Ports paralleler Läufe und Messwerte:
  `docs/testing.md`. Der Lauf vor dem Zusammenführen und vor einem Release läuft immer ohne Filter.
- **Dev-Spieler** (`dev.client.DevPlayerCheck`, nur mit `-PpropwashDevName=<Name>` am Selbsttest, sonst nicht
  angemeldet): Schritt „dev: Skin des Dev-Spielers in der dritten Person“ wartet bis 20 s auf den heruntergeladenen Skin
  (`ClientAsset.DownloadedTexture`, sonst FAIL), fotografiert den Spieler von vorn nach
  `runs/selftest-client/screenshots/propwash-dev-player.png` und loggt Skin, Modell und Umhang. Kurzlauf:
  `./gradlew :fabric:runClientSelftest -PpropwashDevName=LardTom '-PpropwashSelftestOnly=dev: Skin'`. Der normale
  Selbst- und Rauchtest bleibt beim Standardnamen des Loaders und ohne Netz (Bildschirmfotos unverändert).
- **Start-Rauchtest** (`:fabric:runClientSmoke`, `:neoforge:runClientSmoke`, `-Dpropwash.smoketest=true`, stummes
  Run-Verzeichnis `runs/smoke-client`): ein **normaler** Client ohne Selbsttest-Modus — keine Schritte, Tasten,
  HUD-Ebenen oder Listener aus `CoreSelfTests`. Er legt nur dieselbe Flachwelt an, betritt sie, verlässt sie, loggt
  `PROPWASH SMOKE PASS` und beendet das Spiel. Findet Fehler, die der Selbsttest-Modus verdecken würde
  (z. B. fehlende Client-Handler). Stürzt der Client beim Laden ab, fehlt die Ergebnisdatei und der Task schlägt fehl;
  hängt er (NeoForge-Ladefehlerbildschirm), beendet ihn der Watchdog nach 5 min mit FAIL.
- **Spielablauf wie ein Spieler** (`drone.client.PlayLoopSelfTests`, nach „drone: Aufräumen“): prüft die Schleife aus
  Besitzersicht über echte Eingaben (`KeyMapping.click` auf die Benutzen-Taste, `mc.hitResult`, gehaltene Schleichtaste)
  statt über Server-Abkürzungen: Drohne setzen → Rechtsklick mit der Brille prüft nur (sie bleibt ungebunden in der Hand)
  → Funke per Rechtsklick binden → Brille per Rechtsklick in die Luft aufsetzen →
  Rechtsklick mit der Funke startet FPV → Rechtsklick beendet → ohne Brille Sichtflug, der Blick bleibt während des
  Flugs und nach einem Mausruck auf der Drohne (≤ 10°) → Schleichen + Rechtsklick tauscht den Akku, ein unpassender Akku
  wird abgelehnt → Rechtsklick legt den alten Akku ins Ladegerät, das lädt.
- **Bildschirmfotos für den Besitzer** (`dev.client.ShowcaseTour`, `dev.client.Screenshots`): nur Fabric, nur mit
  `./gradlew :fabric:runClientSelftest -PpropwashScreenshots=true` (setzt `-Dpropwash.screenshots=<repo>/docs/screenshots`).
  Ohne die Property werden die Schritte gar nicht angemeldet. Der Rundgang läuft als letzte Client-Schritte
  (angemeldet am Ende von `PropwashClient.init()`), stellt das Fenster auf 1920×1080 Bildpunkte mit GUI-Skala 3,
  setzt die Propwash-Tasten auf ihre Standardwerte, blendet die Selbsttest-Markierung aus und baut jede Szene 48 Blöcke
  neben dem Spawn auf: alle Konfigurator-Tabs (`configurator_<tab>.png`), Werkbank mit FREESTYLE, Shift-Tooltip der
  Funke, Funke + Akku + ladendes Ladegerät, Funke beidhändig beim Blick nach unten und geradeaus
  (`remote_first_person.png`, `remote_first_person_ahead.png`), Funke im Sichtflug mit Truhe in der Nebenhand bei Sichtfeld 70 und 110
  (`remote_first_person_los.png`, `remote_first_person_los_fov110.png`), Ladegerät-Menü (`charger_gui.png`), Wartungsmenü der Drohne
  mit beschädigten Props (`drone_service_menu.png`), Empfänger-Assistent (`receiver_wizard.png`), Gas-Schritt des Assistenten nach dem Weg nach oben mit simulierter Funke (`receiver_wizard_throttle.png`, deutsch `receiver_wizard_throttle_de.png`), beim Halten unten mit OBEN/UNTEN (`receiver_wizard_throttle_hold.png`) und Prüfansicht nach den vier Kanälen (`receiver_wizard_check.png`), animierte Funke in jedem Schritt samt Gas-Rückfrage, Schalter-Seite und Karte bei GUI-Skala 4 sowie bei 240 px GUI-Höhe (`receiver_wizard_remote_*.png`), Tab „Konfiguration“ mit Scharf-Winkel 180° (`configurator_arm_angle.png`, deutsch `_de`), Empfänger-Tab mit einer simulierten Funke mit sehr langem Namen, die Statuszeile bricht um (`configurator_receiver_status_wrapped.png`, deutsch `_de`), Gerätemeldung über dem Assistenten (`device_toast.png`), Inventar mit Brillen, Gates und Kurs-Werkzeug
  in Slot-Größe (`inventory_icons.png`), Werkbank-Menü im Überlebensmodus mit offenem Rezeptbuch, Suche `propwash:` und
  gelegtem Motor-Rezept (`recipe_book.png`), Inventar mit allen Drohnenklassen nebeneinander (`inventory_drones.png`), Funke im Rahmen/auf dem Kopf/am Boden, Pilot mit Brille und Funke in der dritten
  Person von vorn, von der Seite und von hinten (`pilot_third_person.png`, `pilot_third_person_side.png`,
  `pilot_third_person_back.png`; für die Seitenansicht stehen Körper und Kopf 90° zur Kamera), Drohne kurz nach dem Abheben knapp über Gras mit aufgewirbeltem Staub bei Standard- und voller Staubmenge (`dust_takeoff.png`, `dust_takeoff_max.png`), Drohne aus der Nähe, Lackier-Screen der Werkbank mit Vorlage „Neon“ (`paint_screen.png`), lackierte Drohnen aus der Nähe (`drone_closeup_paint_neon.png`, `drone_closeup_paint_race_team.png`), Gate-Kurs mit grünem nächstem Gate (Sichtflug), OSD im FPV-Flug (Standardvorlage) und
  Pilot aus Sicht der Drohne, dazu Rennen und Tricks: Geist der Bestrunde vor der FPV-Drohne (`race_ghost.png`, eine erfundene
  Geisterspur über `GhostLibrary#offerLap` ohne Speichern), stehender Start mit roter und grüner Startampel
  (`race_start_lights.png`, `race_start_go.png`), ein Flip im FPV-Flug mit Trick-Meldung im echten OSD
  (`trick_popup_fpv.png`) und die Bestenliste des Kurses mit erfundenen Zeiten (`leaderboard_screen.png`, danach wieder entfernt).
  Nach den Konfigurator-Tabs schaltet der Rundgang „Props im FPV-Bild“ und „Ducts im FPV-Bild“ für seine FPV-Bilder aus (sie bleiben wie in
  0.3.2; das Merkmal zeigt `fpv_props_in_view.png` aus `PartsRenderShowcase`, dazu `fpv_props_over_water.png`: drehende Props im FPV-Flug
  über einem See, das Wasser muss hinter den Scheiben sichtbar bleiben; je Rahmenart ein Schwebebild `fpv_props_whoop.png`,
  `fpv_props_cinewhoop.png`, `fpv_props_race.png`, `fpv_props_long_range.png` und aus dem Beispiel-Addon
  `fpv_props_stretched.png`, `fpv_props_deadcat.png`; der Selbsttest „drone-render: Props im FPV-Bild – Analyse gegen
  gerendertes Bild“ legt je Rahmen das gezählte Maskenbild unter `fpv_coverage/<namensraum>_<rahmen>.png` ab) und stellt den Schalter beim Aufräumen
  zurück. Die Videobild-Stufen (`video_analog_stage2`–`5`, `video_digital_breakup`, `video_digital_lost`) starten jede aus
  derselben Lage (Drohne am Parkplatz vor dem Kurs, FPV neu gestartet, scharf, 2,2 Blöcke hoch), unabhängig davon, wie
  der Flip davor ausging. Die Geister-Rennen leeren vor dem Start die Rennmeldungen (`RaceHud.clear()`, keine übrig
  gebliebene „Rennen abgebrochen“-Zeile). Szenen mit Menüs parken die Maus (`Screenshots.parkMouse`), Inventare setzen
  die Maus fest auf ein leeres Feld (`ShowcaseInventoryScreen`), damit kein Tooltip Slots verdeckt.
  Danach räumt er auf und stellt Fenster, Kamera und FOV zurück. Die PNGs werden
  eingecheckt; der Rundgang überschreibt sie bei jedem Lauf mit der Property.

**Prüfschritte vor jedem Commit eines Moduls** (alle mit `JAVA_HOME=/opt/homebrew/opt/openjdk@25`):

0. `python3 tools/strip_comments.py` (Konventionen: kein Kommentar im Code)
1. `./gradlew build`
2. `./gradlew :fabric:runClientSelftest` und `./gradlew :neoforge:runClientSelftest` → `PROPWASH SELFTEST PASS`
3. `./gradlew :fabric:runClientSmoke` und `./gradlew :neoforge:runClientSmoke` → `PROPWASH SMOKE PASS`

Die Gradle-Tasks nacheinander starten, nicht parallel.

## 10. Sprachdateien und Mixins

- Sprachquellen: `common/src/lang/<modul>/de_de.json` und `en_us.json` (flache JSON-Objekte, deutsch mit Umlauten).
  `:common:mergeLang` (läuft automatisch vor `processResources`) sortiert und schreibt
  `common/src/main/resources/assets/propwash/lang/{de_de,en_us}.json` — **generiert, aber eingecheckt**; nie direkt
  bearbeiten. Doppelte Schlüssel (in einer Datei oder modulübergreifend) brechen den Build ab; fehlende Übersetzungen
  in einer Sprache erzeugen eine Warnung.
- Schlüssel-Konventionen: `item.propwash.<id>`, `block.propwash.<id>`, `entity.propwash.<id>`, `key.propwash.<name>`,
  `propwash.<modul>.<…>`, `subtitles.propwash.<sound>` (gehört audio), `part.propwash.<bauteil-key>` (gehört content,
  für alle ~40 Bauteile aus `build`; ui/drone benutzen die Schlüssel nur über `PartInfo#translationKey()`).
- **Merge-Konflikte in `assets/propwash/lang/*.json` nie von Hand lösen:** beliebige Seite nehmen
  (`git checkout --theirs common/src/main/resources/assets/propwash/lang/*.json`), dann `./gradlew :common:mergeLang`
  (oder `build`) laufen lassen — das erzeugt die Dateien aus allen `common/src/lang/<modul>/` neu — und das Ergebnis
  committen. Die Quelldateien je Modul kollidieren nicht, weil jedes Modul nur seinen eigenen Ordner ändert. Die
  generierten Dateien sind in `.gitattributes` als `linguist-generated` markiert.
- **Changelog** (`changelogs/changelog-YYYY-MM-DD.md`): jedes Modul fügt nur eigene Stichpunkte in der passenden
  Kategorie hinzu, ändert oder sortiert keine fremden Zeilen und benennt keine Überschriften um. `.gitattributes` setzt
  `changelogs/*.md merge=union`: Git behält bei Konflikten beide Seiten automatisch (danach kurz auf doppelte Zeilen
  prüfen).
- **Sounds:** `assets/propwash/sounds.json` gibt es nur einmal je Namespace; sie und alle `PropwashRegistries.sound(...)`
  gehören audio und stehen in dessen Common-`init()` (`audio.AudioModule`, z. B. Felder in `audio.AudioSounds`).
  Andere Module legen keine `SoundEvent`s an. Aus `audio.client.AudioClient.init()` wirft `sound(...)` (Sperre, §2).
- Mixins (`docs/CONVENTIONS.md`: alle zentral unter `de.lardtom.propwash.mixin.<feature>`): je Modul
  `common/src/main/resources/propwash.<modul>.mixins.json` mit Paket `de.lardtom.propwash.mixin.<modul>`, in beiden
  Loader-Metadaten eingetragen. Klassen relativ zum Paket in `"mixins"` (beide Seiten), `"client"` oder `"server"`
  eintragen, z. B. `"client": ["client.CameraMixin"]` in `propwash.drone.mixins.json` → Klasse
  `de.lardtom.propwash.mixin.drone.client.CameraMixin`. Mehrere Module dürfen dieselbe Zielklasse mixen (eigene
  Klassen, verschiedene Methoden), z. B. drone und video beide `Camera` (§11 „Kamera und Ansicht“).
  Ein Mixin-Paket enthält nur Mixin-Klassen (Hilfsklassen ins Modulpaket). Name `<Ziel>Mixin`, Accessoren `<Ziel>Accessor`. `defaultRequire: 1` → ein nicht greifender Mixin
  crasht beim Start (gewollt). Handler/Accessoren mit `propwash$` präfixen. Vor INVOKE-/Ordinal-Zielen vanilla und
  NeoForge-Patch vergleichen (`.ref/vanilla` vs. `.ref/neoforge`). MixinExtras ist verfügbar.
- Access-Widening: `META-INF/accesstransformer.cfg` (NeoForge + Common-Compile) und `propwash.accesswidener` (Fabric)
  gehören der Foundation; neue Einträge als Foundation-Wunsch melden. Beide Dateien enthalten keine Kommentare
  (Konventionen), die Erklärungen stehen hier:
  - Der AT (Mojang-Namen) wirkt beim Kompilieren von common (NeoForm) und neoforge (ModDevGradle) und zur Laufzeit unter
    NeoForge; `neoforge.mods.toml` deklariert ihn ausdrücklich unter `[[accessTransformers]]`, statt sich auf den
    FML-Fallback für `META-INF/accesstransformer.cfg` zu verlassen. Der AW (Kopfzeile `accessWidener v2 official`, Pflicht)
    ist das Fabric-Gegenstück.
  - **Jeder neue Eintrag gehört in beide Dateien**, damit common auf beiden Loadern dieselbe Sicht hat.
  - Heutige Einträge: `MenuType(MenuSupplier, FeatureFlagSet)` + `MenuType$MenuSupplier` (eigene MenuTypes in common,
    `docs/api/registration.md` 7 und 12) sowie `CreativeModeTab$Output`/`$TabVisibility` (Creative-Tab-Inhalte in
    common). Zur Laufzeit sind sie ohnehin offen (NeoForge-eigener AT bzw. Fabric API
    `fabric-transitive-access-wideners-v1`/`fabric-creative-tab-api-v1`).
- Loader-Metadaten (`fabric.mod.json`, `neoforge.mods.toml`) sind kommentarfrei. Beide listen dieselben Mixin-Configs
  in derselben Reihenfolge; ein Modul ohne Mixins hat keine Config. Wer die erste Mixin-Klasse eines Moduls anlegt,
  legt `propwash.<modul>.mixins.json` an und meldet den Eintrag in beiden Metadaten als Foundation-Wunsch.

## 11. Modulübergreifende Verträge

Hier steht der **ganze** Vertrag jeder modulübergreifenden Klasse (im Code gibt es keine Kommentare). Der Besitzer
erweitert sie, ohne Signaturen zu brechen (nur ergänzen); alle anderen Module benutzen ihn nur. Alle State-Fassaden (`INSTANCE`) sind
**nur auf dem Render-Thread** zu benutzen. Übersicht:

| Datei | Besitzer | Leser / Aufrufer |
|---|---|---|
| `core/ExtraCollider` | core (definiert), race (implementiert in `GateEntity`) | drone (Kollision) |
| `drone/QuadcopterEntity`, `drone/DroneModule.DRONE` | drone | alle; content (Drohnen-Item: `loadFromStack`), server (`PILOT_CHANGED`) |
| `server/ServerRules` | server | drone (Pilotenrolle, C2S-Prüfung) |
| Kamera und Ansicht (nur Mixins) | drone (Pose, Hand/Körper, Maus), video (FOV, Post-Effekte) | — |
| `drone/client/PilotSession` | drone | video, ui, race, rescue, audio, input |
| `drone/client/TuneAccess`, `core/FlightTune`, `CoreComponents.FLIGHT_TUNE` | drone (Daten), core (Typ) | ui (Konfigurator) |
| `client/render/DronePreview` | drone (Rendering) — **Ausnahme**, liegt außerhalb des drone-Pakets | content (Werkbank), ui (Konfigurator) |
| `input/RadioState`, `input/StickPositions`, `input/SwitchFunction` | input | drone, ui, rescue, race |
| `video/client/VideoState` | video | drone (schreibt Posen, liest Kamera), ui, audio |
| `race/GateEntity`, `race/GateSize`, `race/RaceModule.GATE` | race | drone (Kollision), content (Creative-Tab) |
| `race/client/RaceState` | race | ui, audio |
| `rescue/client/RescueState` | rescue | ui, drone, audio |
| `audio/client/AudioHooks`, `audio/client/Beep` | audio | drone, race |
| `content/ContentIds` | content (Ids), race (registriert Gate-Items + Kurs-Werkzeug) | alle |

### `core/ExtraCollider` (definiert core, implementiert race, abgefragt von drone)
Entities, die Drohnen zusätzliche Kollisionskörper anbieten (Renn-Gates mit Pfosten und Balken).
`void collectColliders(AABB query, List<AABB> out)`:
- hängt an `out` alle eigenen Kollisionsboxen in **Weltkoordinaten** an, die `query` schneiden (andere dürfen, müssen
  aber nicht weggelassen werden); `out` nie leeren.
- Boxen sind achsenparallel; gedrehte Teile werden durch mehrere kleine Boxen angenähert.
- Aufruf auf der Seite, die die Drohne simuliert (Client des Piloten bzw. Server), im Tick-Thread dieser Seite; keine
  Allokationen außer den Boxen selbst, keine Weltzugriffe.
- Die Entity meldet `canBeCollidedWith(...) == false`, damit Vanilla nicht ihre ganze Bounding-Box als Hindernis nimmt
  (sonst wäre die Gate-Öffnung zu).
- Suchende (drone, vor der Vanilla-Bewegung in `QuadcopterEntity#move`) erweitern ihre Entity-Abfrage um
  `ExtraCollider.MAX_HALF_EXTENT` (= 8 Blöcke; Obergrenze der halben Ausdehnung eines Körpers um die Entity-Position),
  weil Entity-Abfragen nach Entity-Position einsortiert sind (`network-entity.md` 3.4).

### `drone/QuadcopterEntity` + `DroneModule.DRONE` (drone)
Entity `propwash:drone`, `EntityType.Builder.of(QuadcopterEntity::new, MISC).sized(0.5F, 0.15F).clientTrackingRange(16).updateInterval(1)`.
drone implementiert Flugphysik-Anbindung, Pilotenrolle, Bewegungs-Gating (`canSimulateMovement()`), Interaktion,
Schaden/Drop und Interpolation.
- **Synchronisiert** (Server → alle Tracker sofort bei Änderung, beim Spawn im Bundle): `build()`/`setBuild` (`DroneBuild`
  über `CoreComponents.DRONE_BUILD_SERIALIZER`), `orientation()`/`setAttitude` (`Quaternionfc`, Welt ← Körper;
  Körper +Y oben, −Z vorn wie die Kamera; Rückgabe nicht verändern, Kopie mit `new Quaternionf(orientation())`),
  `isArmed()`/`setArmed`, `pilot()` (`Optional<UUID>`, leer = niemand)/`setPilot(@Nullable LivingEntity)`,
  `batteryCharge()`/`setBatteryCharge` (0..1, Setter klemmt), `motorOutput()`/`setMotorOutput` (mittlere Motorleistung
  0..1 für den Motorsound entfernter Spieler, Setter klemmt), `tune()`/`setTune(FlightTune)` (Tuning des
  Flight-Controllers über `CoreComponents.FLIGHT_TUNE_SERIALIZER`, nie null; der Client des Piloten speist es ins
  Flugmodell), `ledsOff()`/`setLedsOff` (LED-Streifen per Funkenschalter „LEDs aus“ abgeschaltet; siehe §15
  „LED-Schalter an der Funke“), `paint()`/`setPaint` (Lackierung, §15 „Lackierung“).
- Setter immer mit neuen/unveränderten Werten aufrufen: `SynchedEntityData` erkennt Änderungen per `equals`; ein
  verändertes, bereits gesetztes Objekt würde nicht gesendet (`setAttitude` kopiert deshalb).
- **Gespeichert** (`ValueOutput`): `Build` (`DroneBuildCodecs.CODEC`), `Orientation` (`ExtraCodecs.QUATERNIONF`),
  `Battery`, `HasBattery` (fehlt → true), `Tune` (`FlightTune.CODEC`), `Paint` (`PaintCodecs.CODEC`, nur wenn nicht Standard). Nicht gespeichert: armed, Pilot, Pilotenmodus,
  Motorleistung, LEDs aus.
- **Pilotenmodus und Akku (Gameplay-Loop v2):** `pilotMode()`/`setPilotMode(PilotMode)` (`NONE`, `FPV`, `LINE_OF_SIGHT`,
  synchronisiert; drone setzt ihn **vor** `setPilot(player)`, `setPilot(null)` setzt `NONE`; ein Pilot mit Modus `NONE`
  — nur wenn ein anderes Modul `setPilot` direkt aufruft — gilt auf dem Client als FPV und ohne Funken-Pflicht).
  `hasBattery()`, `batteryLocked()` (= scharf oder gesteuert), `batteryStack()` (neuer Akku-Stack mit Ladestand, leer ohne
  Akku), `takeBattery()` (gibt den Stack zurück, danach kein Akku, Ladestand 0), `insertBattery(BatteryType, float)`
  (setzt `build().battery()` und den Ladestand; prüft die Kompatibilität **nicht**, das macht der Aufrufer mit
  `build.BatteryFit`). `build().battery()` ist immer der zuletzt eingesetzte Typ, auch ohne Akku. Es gibt **kein**
  Nachladen in der Welt mehr; Ladestände ändern sich nur durch Fliegen und im Ladegerät.
- **Item ↔ Entity** (Datenübergabe zwischen content und drone):
  - `loadFromStack(ItemStack)`: übernimmt aus dem Stack `propwash:drone_build` (fehlt → `DroneBuild.FREESTYLE`),
    `propwash:battery_installed` (fehlt → true), `propwash:battery_charge` (nur mit Akku; fehlt → 1, geklemmt), `propwash:flight_tune` (fehlt → `FlightTune.EMPTY`), `propwash:drone_paint` (fehlt → Standard) und den
    Anzeigenamen `minecraft:custom_name` (fehlt → keiner). Der Item-Typ wird nicht geprüft.
  - `toStack()`: neues Drohnen-Item (`ContentIds.item(ContentIds.DRONE)`, Anzahl 1) mit `drone_build`, `drone_id`
    (= `getUUID()`), `battery_charge` (ohne Akku stattdessen `battery_installed=false`), `flight_tune` (nur wenn nicht leer), `drone_paint` samt Icon-Tints in `custom_model_data` (nur wenn nicht Standard) und `custom_name` (nur wenn die Entity einen hat);
    `ItemStack.EMPTY`, solange content das Item nicht registriert. Verändert die Entity nicht.
  - **Platzieren (`QuadcopterItem#useOn`, nur Server):** `DroneModule.DRONE.get().create(level,
    EntitySpawnReason.SPAWN_ITEM_USE)` → `loadFromStack(stack)` → `setUUID(drone_id)`, falls vorhanden und keine Entity mit
    dieser UUID existiert (`ServerLevel#getEntityInAnyDimension`) → `snapTo(x, y, z, player.getYRot(), 0)` auf der
    angeklickten Blockseite → `level.addFreshEntity(drone)` → `stack.consume(1, player)`; `InteractionResult.SUCCESS`.
    content setzt weder Pilot noch armed.
  - **Aufheben/Drop (drone):** drone baut das Item immer mit `toStack()` (Aufheben per Interaktion, Zerstören,
    Drop) und entfernt danach die Entity; neue Item-Komponenten ergänzt drone in beiden Methoden gemeinsam.
- `static Event<PilotListener> PILOT_CHANGED` (`onPilotChanged(QuadcopterEntity drone, Optional<UUID> previous,
  Optional<UUID> current)`): feuert **nur auf dem Server-Thread**, sobald sich der Pilot tatsächlich ändert — in
  `setPilot` (Zuweisen, Wechsel, `setPilot(null)`) und beim Entfernen der Entity (`onRemoval`: discard, Tod,
  Entladen, Dimensionswechsel; der Pilot wird dann zurückgesetzt, `current` ist leer). Hörer: server
  (Sichtanker, Chunk-Tickets, siehe `ServerRules`). drone ruft vor jeder Zuweisung `ServerRules.mayPilot` und löst den
  Piloten mit `setPilot(null)`, wenn er die Verbindung trennt, stirbt, die Dimension wechselt oder
  `ServerRules.maxPilotDistance` überschreitet.
- `static Event<Consumer<QuadcopterEntity>> CLIENT_TICK`: feuert jeden Client-Tick für jede Drohne im Client-Level
  (Render-Thread). Liegt in Common, damit die Klasse keine Client-Typen kennt; Client-Module registrieren in ihrer
  Client-`init()` (audio: `AudioHooks::onDroneClientTick`, verdrahtet in `AudioClient`).
- `resetTo(Vec3 pos, Quaternionfc orientation)` (implementiert drone, aufgerufen von race/server/rescue; nur
  Server-Thread, auf dem Client `IllegalStateException`): Drohne in Ruhe an diese Pose setzen (Geschwindigkeit und
  Drehraten 0). Da clientautoritativ geflogen wird (`network-entity.md` 2.4), erweitert drone die Methode so, dass der
  Server Position/Lage/Geschwindigkeit sofort übernimmt und an alle Tracker schickt (Teleport, keine Interpolation)
  **und** der Client des Piloten (falls `pilot()` gesetzt) per S2C seinen Sim-Zustand auf genau diese Pose mit
  Geschwindigkeit/Drehraten 0 zurücksetzt und ältere Bewegungspakete des Piloten verwirft. Build, Akku, armed und Pilot
  bleiben; Disarm separat mit `setArmed(false)`. Umsetzung: `snapTo` + Geschwindigkeit 0 + `setAttitude`, Zähler
  `resetEpoch()` +1 (synchronisiert; Bewegungspakete mit anderer Epoche verwirft der Server) und
  `DroneResetS2C` an den Piloten.
- Vanilla-Hooks: `hurtServer` → Spieler zerlegen die Drohne sofort (Drop außer im Kreativmodus), anderer Schaden
  summiert sich bis zur Bruchgrenze; `isPickable` → nicht entfernt; `onRemoval` → Pilot serverseitig zurücksetzen
  (feuert `PILOT_CHANGED`).

### `drone/client/PilotSession` (drone)
Aktuelle Flugsitzung des lokalen Spielers, `PilotSession.INSTANCE`.
- `isActive()`: genau dann true, wenn der lokale Spieler gerade eine Drohne steuert — **in beiden Modi** (FPV und
  Sichtflug); dann ist `drone()` nicht null. `drone()`: die gesteuerte Client-Entity oder null.
- `mode()` (`PilotMode`, `NONE` ohne Sitzung), `isFpv()`, `isLineOfSight()`. Kamera, FOV, Video-Effekte, Brillen-OSD,
  ausgeblendete Hand, Frustum-Refresh und der Motorsound der Pilotenansicht hängen an `isFpv()`; Körper einfrieren,
  Maus/Tasten sperren, Fadenkreuz, Rescue, Rennen, Eingabe und der Posen-Puffer an `isActive()`. Im Sichtflug bleibt die
  Kamera beim Spieler (Ego-Perspektive erzwungen), `drone.client.LosView` dreht den Blick jeden Frame weich zur Drohne
  (`drone.LosAim`), das Funk-HUD `osd.client.RadioHud` ersetzt das Brillen-OSD. Aufrufer-Tabelle: `docs/design/gameplay-loop-v2.md` §5.5.
- **Blick und Kopf des Piloten (`drone.PilotLook`, rein):** Nur der Sichtflug dreht den Spieler zur Drohne
  (`viewFollowsDrone(mode)` = `LINE_OF_SIGHT`; `view(mode, yaw, pitch, dx, dy, dz, dt)` liefert im Sichtflug `LosAim.aim`,
  sonst den Blick unverändert — `LosView` benutzt genau das). Im FPV bleiben `yRot`/`xRot`/`yHeadRot` des Spielers so, wie
  sie beim Wechsel ins FPV standen; weil dann die Drohne Kamera-Entity ist, schickt Vanilla auch keine Rotationspakete, der
  Server und alle anderen Spieler sehen also denselben stehenden Kopf. Einzige Bewegung im FPV ist eine reine
  Render-Haltung: `drone.client.PilotHeads` senkt bei jedem FPV-Piloten (eigener eingefrorener Körper **und** fremde
  Piloten, deren Drohne im Client-Level `pilotMode()` FPV meldet) den gezeichneten Kopf weich auf
  `PilotLook.FPV_HEAD_PITCH` (20° nach unten, Richtung Funke); Gieren bleibt Vanilla. Übergang über ein Gewicht 0…1
  (`headWeight`, Zeitkonstante `HEAD_SETTLE_SECONDS` 0,25 s, Schritt höchstens `MAX_HEAD_STEP_SECONDS`), beim Wechsel in
  den Sichtflug gleitet der Kopf genauso zurück. Moduswechsel mitten im Flug (Brille ab/auf) wirken ab dem nächsten Frame.
- `drone.client.PilotView`: `active()` (= `isActive()`), `useLatched()`, `onUseKeyWhilePiloting()` (Rechtsklick während der
  Sitzung beendet sie; danach bleibt `keyUse` 10 Ticks gesperrt). `FpvView.active()` bedeutet `isFpv()`,
  `FpvView.hidesBody(entity, x, y, z)` blendet den eingefrorenen Körper nur nahe der FPV-Kamera aus.
- Start/Stopp: nur mit der Funke (`drone.RemoteItem`, Server `DronePiloting.useRemote`); `PilotRequestC2S(id, true)` wird
  ignoriert, `false` beendet. `drone.RadioLink`: `FULL_RANGE` 800, `FADE_RANGE` 400, `START_RANGE` 800 (zusätzlich
  `ServerRules.maxPilotDistance`), `RELEASE_RANGE` 1250, `quality(distance[, serverLimit[, freeRange[, creativeRange]]])`
  für `linkStrength()`. `freeRange(player)`: Kreativmodus oder getragene Kreativ-Brille; dann gelten `startRange`/
  `releaseRange` ohne die festen Funkgrenzen (nur `ServerRules.maxPilotDistance`), und der Client blendet die Verbindung
  mit Kreativ-Brille ab der Reichweite aus dem Video-Tab aus (0 = unbegrenzt). Server (`DronePiloting`) und Client
  entscheiden unabhängig mit demselben `freeRange`.
- `linkStrength()`: Funkverbindung Funke → Drohne, 0 (keine) … 1 (perfekt), unabhängig vom Videosignal; 0 ohne Sitzung.
- `sim()`: das laufende Flugmodell (`sim.DroneSim`), null ohne Sitzung; Telemetrie (Geschwindigkeit, Höhe,
  Akkuspannung, Motorwerte, Lage, Flugmodus) über `sim().telemetry()`. `physicsFrameNanos()`: Zeitstempel des letzten
  Physik-Frames (`Util.getNanos()` bei `FRAME_START`); bis dahin ist die Simulation gerechnet, zu genau diesem Zeitpunkt
  steht die Pose im Posen-Puffer, und die FPV-Kamera rechnet ihre Latenz von hier aus.
- Arm-Sperre bei neuer Arm-Quelle: `ArmSwitchGuard` merkt sich beim Start `RadioState#armRevision()` und ruft vor jedem
  Physik-Frame (nicht im Selbsttest-Knüppelmodus) bei einer Änderung `sim().requireArmSwitchReset()`; die Tastatur (Arm-Taste)
  bleibt unverändert.
- Rescue-Übergabe: siehe `RescueState`.
- **Höhe/Position halten (ALTHOLD/POSHOLD, Modell in `docs/design/flight-model-spec.md` 6.12):** `PilotInput.altHold` =
  Schalter `ALT_HOLD` oder Tasten-Umschalter `drone_alt_hold`, `posHold` = Schalter `POS_HOLD` oder `drone_pos_hold`
  (Test-Knüppel des Selbsttests benutzen nur die Tasten-Umschalter). Schaltet ein Funkenschalter aus, fällt auch der
  Tasten-Umschalter derselben Funktion ab; „Höhe halten“ aus schaltet auch „Position halten“ aus. Bei Funkverlust gilt der
  zuletzt gehaltene Zustand, während Rescue fliegt sind beide aus. `SimEnvironment.skyView` misst `drone.client.SkyView`
  (Strahlen zum Himmel, alle 0,5 s). `altHoldRequested()`, `posHoldRequested()`, `holdMode()` (`sim.HoldMode`, aus der
  Telemetrie) für Modi-Tab und OSD. Tasten-Umschalter melden sich per Propwash-Toast; POSHOLD ohne GPS meldet
  „POSHOLD nicht möglich: kein GPS (n Satelliten)“ beim Drücken und im Sichtflug auch beim Abbruch, im FPV nur das OSD
  („KEIN GPS“).

### `drone/client/TuneAccess` + `core/FlightTune` + `propwash:flight_tune` (drone; ui benutzt)
Das Tuning des Flight-Controllers (Rates, PIDs, Filter, …) **gehört drone**, denn es ist Teil der Drohne wie auf einem
echten FC. Gespeichert als Data-Component `propwash:flight_tune` (`FlightTune`) auf dem Drohnen-Item; beim Platzieren
landet es über `QuadcopterEntity#loadFromStack` in `QuadcopterEntity#tune()` (gespeichert als `Tune`, an alle Tracker
synchronisiert), beim Aufheben über `toStack()` wieder im Item; drone speist es in das Flugmodell ein. `FlightTune` ist bewusst eine offene Schlüssel/Wert-Tabelle, damit ui das
Flugmodell nicht kennen muss.
- Schlüsselschema (drone legt die endgültige Liste fest und dokumentiert sie hier): `rate.<roll|pitch|yaw>.<rc|super|expo>`,
  `pid.<roll|pitch|yaw>.<p|i|d|ff>`, `filter.<name>_hz`, `throttle.<mid|expo|limit>`, `mode.<name>`, `hold.<feld>`
  (`HoldSettings`: `altP/altI/altD`, `climbRateCms`, `throttleDeadbandPercent`, `midThrottle`, `posP/posI/posD`,
  `posMaxAngleDeg`, `stickDeadbandPercent`, `minSatellites`; ui zeigt sie im Tab „PID-Tuning“ in den Abschnitten
  „Höhe halten (ALTHOLD)“ und „Position halten (POSHOLD)“, „Standardwerte“ setzt sie mit zurück). Unbekannte
  Schlüssel ignoriert drone, fehlende ersetzt es durch Standardwerte des Flugmodells.
- `TuneAccess.INSTANCE` (Render-Thread): `available()` true, wenn der lokale Spieler gerade eine Drohne tunen kann
  (aktive `PilotSession` oder Drohnen-Item in der Hand); `current()` das aktuelle Tuning (sonst `FlightTune.EMPTY`);
  `defaults()` die Standardwerte des Flugmodells für die aktuelle Drohne (Schlüssel = vollständige Liste, die der
  Konfigurator anzeigen soll); `apply(FlightTune)` übernimmt das Tuning (drone schickt es per C2S an den Server,
  validiert dort Schlüssel/Wertebereiche, speichert es an Entity/Item) und liefert false, wenn nichts zum Tunen da
  ist.

### `client/render/DronePreview` (drone, Ausnahme von der Paketregel)
`static void render(GuiGraphicsExtractor graphics, DroneBuild build, int x0, int y0, int x1, int y1, float yawDegrees)`:
zeichnet die Drohne mit den Bauteilen aus `build` zentriert und passend skaliert in das Rechteck `[x0, x1) × [y0, y1)`
(absolute GUI-Koordinaten; die Pose-Matrix von `graphics` wendet `GuiGraphicsExtractor#entity` nicht an), um die
Hochachse um `yawDegrees` gedreht und leicht von oben gesehen. Aufruf aus `extractBackground`/`extractRenderState`
eines Screens (Render-Thread), beliebig oft pro Frame; braucht weder Welt noch Entity. Umsetzung über den
Drohnen-Renderer von drone-render. drone darf diese Datei ändern (Signatur bleibt),
Hilfsklassen liegen im eigenen Paket (`drone.client.render`).

### `server/ServerRules` (server; aufgerufen von drone)
Rechte und Grenzen des Servers, die drone bei der Pilotenrolle und bei der Prüfung der Bewegungspakete abfragt. Alle
Methoden **nur Server-Thread**, schnell (pro C2S-Paket aufrufbar), werfen nie. Die Werte kommen aus der
Server-Config (`ServerRulesConfig`) und aus Rechten (Permission-Level).
- `static boolean mayPilot(ServerPlayer player, QuadcopterEntity drone)`: darf `player` diese Drohne jetzt steuern? drone
  fragt vor `setPilot(player)`; bei false keine Pilotenrolle (drone meldet `pilotDenialReason(player, drone)` dem
  Spieler: fehlendes Recht, andere Dimension, Spawnschutz, zu weit weg).
- `static double maxPilotDistance(ServerPlayer player)`: größte erlaubte Entfernung in Blöcken (3D, Körper des Piloten ↔
  Drohne). drone prüft bei der Zuweisung und bei jedem Bewegungspaket; darüber `setPilot(null)`. Unendlich bei
  erlaubter unbegrenzter Reichweite (`unlimitedRange`).
- `static double maxDroneSpeed(ServerPlayer player)`: größte erlaubte Drohnengeschwindigkeit in Blöcken pro Sekunde.
  drone verwirft Bewegungspakete, deren Schrittweite/Zeit darüber liegt (zusätzlich zu seiner eigenen
  Physik-Plausibilität, es gilt der kleinere Wert). Unendlich, wenn die Config 0 setzt.
- **Chunk-Streaming um die Drohne gehört server** (`network-entity.md` §4): Sichtanker (`player → drone`, nur im selben
  Level), die Mixins auf `ChunkMap#updateChunkTracking`, `ChunkMap#onFullChunkStatusChange` (hält den Speicher
  erzeugter Chunks aktuell), `ChunkMap$TrackedEntity#updatePlayer` und `PlayerChunkSender#sendNextChunks` (in
  `mixin.server`), die Ticket-Typen (`PropwashRegistries.ticketType` in
  `ServerModule`) und das Setzen/Erneuern der Tickets. server setzt/löscht den Anker in `QuadcopterEntity.PILOT_CHANGED`
  (danach `serverLevel.getChunkSource().move(pilot)`), erneuert Tickets und Anker in `ServerBus.TICK_END` anhand von
  `drone.position()`/`chunkPosition()` und räumt bei `PLAYER_LEAVE` auf. drone liefert nur `pilot()`, Position und das
  Event; `QuadcopterEntity#tick` fasst keine Tickets an.
- `static boolean mayGenerateChunks(ServerPlayer player)`: darf die Drohne dieses Piloten **neue** Chunks erzeugen? Nie, wenn
  die Welt-Regel `propwash:drones_generate_chunks` aus ist (gilt für alle, auch Host und Operatoren); sonst true,
  wenn `onlyGeneratedChunks` aus ist, wenn der Spieler Host eines integrierten Servers ist (Einzelspieler/LAN-Host,
  `hostGeneratesChunks`, Standard an) oder wenn er die Rechtestufe `generateChunksPermissionLevel` hat (Standard 0 =
  alle, 2 = Operatoren, 5 = niemand). Standard also: jede Drohne erzeugt Gelände um sich, auch die normaler Spieler auf
  Dedicated Servern. Wer das einschränkt, lässt die Drohnen nur schon erzeugte Chunks laden: Das Sicht-Quadrat lädt
  dann alle erzeugten Chunks lückenlos über mehrere Tickets (`GeneratedCover`), der Pilot bekommt die fehlenden Chunks um
  die Drohne (`GenerationEdgeS2C`), und der Client bremst die Drohne 4 Blöcke vor dem Rand ab (`EdgeFence`, OSD
  „WELTRAND“, `docs/design/world-edge.md`); gelöst wird nur, wenn der Chunk der Drohne selbst fehlt (Selbsttest „server:
  gesperrte Chunk-Erzeugung trennt über unerforschtem Gebiet“). Die reine Entscheidung steckt in
  `ServerRulesConfig#allowsGeneration` (JUnit-getestet).
- Wer erzeugen darf, bekommt zusätzlich ein **Vorausladen** in Flugrichtung: server schätzt die Geschwindigkeit aus den
  Positionsänderungen der Drohne (`Lookahead`, geglättet, Teleports setzen zurück) und setzt ab 3 Blöcken/s ein Ticket
  `propwash:drone_lookahead` (nur Laden) `Tempo × 4 s` voraus (höchstens doppelte Sichtweite), Radius Sicht-Ticket minus
  halbe Vorausstrecke (mindestens 3). So stehen die Chunks vor der Drohne schon, bevor sie in die Sicht kommen; der Server
  verwirft Bewegungspakete in nicht geladene Chunks weiterhin (`DronePiloting`).
- Den Körper des Piloten am Absinken hindern (`network-entity.md` 4.4, Mixin auf `LivingEntity#travel` für den lokalen
  Spieler) gehört drone (`mixin.drone.client`), denn es hängt an der `PilotSession`.

### Kamera und Ansicht (drone + video, nur Mixins)
Feste Besitzer, damit nicht zwei Module dieselbe Methode überschreiben (`camera-gui.md`):
- **drone** (`mixin.drone.client`): Kamera-Pose (`Camera#alignWithEntity` TAIL: Position und Lage aus
  `VideoState.INSTANCE.sampleDisplayed(PilotSession.INSTANCE.physicsFrameNanos(), …)`, sonst aus der Drohne; nie aus
  `Util.getNanos()` zur Kamerazeit, siehe `VideoState`), Schutz gegen Blick durch Blöcke (`Camera#update` nach dem
  Setzen von `fov`: `FpvEyeGuard` prüft die Sichtpyramide bis zur Nahebene gegen die sichtbaren Blockformen, holt erst die
  Nahebene näher – `@ModifyArg` auf `setupPerspective`, höchstens bis 10 mm, mit Shaderpack nie – und schiebt das Auge
  nur heraus, wenn es selbst im Block oder hinter einer dünnen Fläche liegt; in freier Luft bitgleich Vanilla,
  `docs/design/fpv-eye-clearance.md`), Frustum-Aktualisierung
  (`LevelExtractor`), Hand im FPV ausblenden (`GameRenderer#renderItemInHand`), Maus-Drehung sperren
  (`MouseHandler#turnPlayer`), Kamera-Entity und Zurücksetzen bei Sitzungsende.
- **Der Körper des Piloten bleibt in der Welt sichtbar** (drone): Ausgeblendet wird nur, was in der Kamera selbst läge
  (die eigene Drohne ist Kamera-Entity und wird deshalb von Vanilla übersprungen, die Hand über `renderItemInHand`).
  Vanilla/Fabric zeichnen einen `LocalPlayer` nur, wenn er Kamera-Entity ist; `LevelExtractorMixin` hebt diese Sperre
  während des FPV auf (`extractVisibleEntities`, `instanceof LocalPlayer`), NeoForge zeichnet ihn ohnehin.
  `AvatarRendererMixin` (`extractRenderState` TAIL) senkt für jeden FPV-Piloten den Kopf über `PilotHeads.apply` (siehe
  `PilotSession`, „Blick und Kopf des Piloten“; der Kopf folgt der Drohne im FPV **nicht**) und blendet beim eingefrorenen
  Körper das eigene Namensschild aus und zeigt ihn stehend. `drone.client.PilotBodyPose` zählt die gezeichneten Frames und
  merkt sich Kopf-Gieren/-Nicken und Körperdrehung des letzten Frames für den Selbsttest.
- **video** (`mixin.video.client`): FOV im FPV (`Camera#calculateFov` RETURN, Wert `VideoState#fpvFov` = Sichtfeld der
  Kamera im Videomodul der gezeigten Drohne, nur solange FPV oder Zuschauen aktiv ist; §15 „Sichtfeld vom Videomodul und
  Kamerawinkel pro Drohne“), Post-Effekte/Störbild, Posen-Puffer (`VideoState`), FPV-Scheinwerfer
  (`GameRenderer#renderLevel`: Projektion abgreifen, Pass vor `render3dHud`).
- Andere Module mixen `Camera`, `GameRenderer#renderItemInHand` und `MouseHandler#turnPlayer` nicht.

### `input/RadioState`, `input/StickPositions`, `input/SwitchFunction` (input)
`RadioState.INSTANCE`: Zustand der Funke bzw. des Gamepads, einmal pro Frame (nach dem SDL-Event-Pump) aktualisiert und
innerhalb eines Frames konsistent.
- `connected()`: Gerät verbunden und ausgewählt. Ist es false, liefert `sticks()` `StickPositions.IDLE`, `isActive` false,
  `rawAxes()`/`rawButtons()` leere Arrays.
- `deviceName()`: bereinigter Anzeigename (z. B. „Radiomaster Boxer“), leer ohne Gerät. EdgeTX meldet sich per USB mit
  dem OpenTX-Produktnamen und VID:PID 1209:4F54; input entfernt deshalb ein führendes „OpenTX “/„EdgeTX “ und bei Funken
  ein abschließendes „ Joystick“ (`input.DeviceNames`) und behauptet nie „OpenTX“ allein (Rest leer ⇒ „EdgeTX/OpenTX“).
  Ob es eine EdgeTX/OpenTX-Funke ist, zeigt input als Abzeichen „EdgeTX/OpenTX“; Logs behalten den Rohnamen.
- `sticks()`: kalibriert, mit Deadband, Kanalzuordnung, bereits geklemmt; ohne Rates/Expo (die rechnet der FC).
- `isActive(SwitchFunction)`: berücksichtigt die Schalterzuordnung aus der Input-Config (nicht belegt ⇒ false). Quelle
  je Funktion ist frei wählbar: jede Achse ohne Knüppel (AUX-Kanal) mit Bereich 900–2100 µs (25er-Raster, intern −1,2..1,2)
  oder jede Taste/Hat-Richtung, jeweils invertierbar, Umschalten (Toggle) für `SwitchFunction#supportsToggle()`.
  **Invertiert** (`SwitchAssignment.invert`, gespeichert mit der Belegung im Geräteprofil, fehlt ⇒ false): Achse im
  Halten-Modus = Kanal an 1500 µs gespiegelt (`mirrored()`; 2-Stufen-Schalter tauschen An/Aus, 3-Stufen-Schalter drehen
  ihre Reihenfolge um, ein Bereich 1000–1350 wirkt wie 1650–2000 µs); Taste im Halten-Modus = aktiv, solange losgelassen;
  Umschalten (Taste oder Achse) = der eingerastete Zustand wird getauscht, das Umschalten selbst bleibt beim Drücken bzw.
  beim Eintritt in den Bereich (`flipsOutput()`). `activeLow/High(Micros)` und `setActiveLow/High(Micros)` liefern bzw.
  setzen den wirksamen Bereich in Kanal-µs (Modi-Tab, Assistent, Beschriftungen zeigen nur diesen); `engaged()` ist die
  Stellung ohne Ausgangs-Tausch, `rawActive()` der Zustand im Halten-Modus. Unlesbare Quellen sind nie aktiv, auch
  invertiert nicht. Profile vor `InputConfig.PROFILE_VERSION` 2 hatten „aktiv außerhalb des Bereichs“: `ProfileDefaults.migrate`
  rechnet Bereiche am Rand in die Spiegelung um, innere Bereiche und invertierte Umschalter laden als nicht invertiert.
- `armRevision()`: Zähler, der sich ändert, sobald sich die Quelle des Arm-Schalters ändert (anderes Gerät, Funke/Gamepad
  verbunden oder getrennt, Wechsel zu bzw. von der Tastatur, gespeicherte Arm-Belegung inkl. Bereich/Invertierung;
  `ArmSourceWatch`, gespeist über `publish(frame, armBinding)`, `publish(frame)` meldet keine Arm-Belegung). drone
  (`PilotSession` über `drone.client.ArmSwitchGuard`) ruft bei jeder Änderung `DroneSim#requireArmSwitchReset()`: ist Arm
  danach an, bleibt die Drohne entschärft (übliche Sperr-Meldung „erst aus, dann an“ + OSD „ARM SWITCH ON“), bis der Schalter
  einmal aus und wieder an war; armed bleibt die Drohne unberührt. Tast-Funktionen (Rescue, Aufrichten, Homepoint, Rennen neu
  starten), die nur durch eine gespeicherte Belegungsänderung aktiv werden, hält `TransmitterInput` aus, bis sie einmal inaktiv waren.
  Zusätzlich setzt die Taste „Suchpiepser“ (`InputKeys.BEEPER`, Standard Punkt) während einer Pilot-Sitzung `BEEPER`
  und die Taste „LEDs an/aus“ (`InputKeys.LEDS`, Standard F8) `LEDS_OFF` als Umschalter (Tastatur und Funke, per ODER zum
  Schalter der Funke, Reset bei Sitzungsende und `DISCONNECT`, Meldung „LEDs an/aus“ bzw. „LEDs bleiben aus“, wenn der
  Funkenschalter sie hält); ARM/Flugmodus per Taste laufen nicht über
  `isActive`, sondern über `PilotSession` (siehe unten).
- **Flugmodus aus Tasten und Schaltern (`input.FlightModeSelector`, reines Java, gehört `PilotSession`):** es zählt die
  letzte Eingabe. Ein Wechsel des Modus, den die Modusschalter der Funke ergeben (`fromSwitches`: ANGLE vor HORIZON, sonst
  ACRO; nur wenn ANGLE oder HORIZON am Gerät lesbar belegt ist), setzt diesen Modus (Quelle `SWITCH`); die Tasten Acro,
  Angle, Horizon und „Flugmodus wechseln“ (R, vom geflogenen Modus aus weiter) setzen den Modus sofort (Quelle `KEY`),
  auch gegen einen gesetzten Schalter, bis der Schalter das nächste Mal bewegt wird. Sind Angle und Horizon aus, fliegt
  die Drohne nach dem Schalterwechsel Acro; fällt die Modus-Belegung weg, kehrt ein vom Schalter gesetzter Modus zu Acro
  zurück. Ohne Gerät (Tastatur, Funkverlust, Neuverbindung) bleibt der Modus, eine Neuverbindung mit unveränderter
  Schalterstellung ändert nichts. Die Wahl lebt über Sitzungen (bis zum Spielende); der Start einer Sitzung mit der
  Tastatur setzt `keyboard.startInAngle` und lässt die nächste Funke mit Modusschalter wieder entscheiden. Weicht der
  Schalter nach einem Tastendruck ab, nennt die Meldung beide (`propwash.drone.mode.changed_switch`), der Modi-Tab zeigt
  einen Hinweis (`PilotSession#keyOverridesModeSwitch`). Rescue, Failsafe und Höhe/Position halten übersteuern wie bisher
  im Flugmodell.
- **3D-Modus (`input.Mode3dSelector`, reines Java, gehört `PilotSession`; `docs/design/mode-3d.md`):** gleiche Regeln
  wie beim Flugmodus – ein bewegter Schalter `MODE_3D` setzt seinen Zustand (Quelle `SWITCH`), die Taste „3D-Modus an/aus“
  schaltet sofort um (Quelle `KEY`), fällt die Belegung weg, geht ein vom Schalter gesetztes 3D aus. Aktiv nur mit
  `InputConfig.mode3dUnlocked` (persönlich, Tab „Modi“), Welt-Regel `propwash:allow_3d_mode` und ohne GPS-Rettung
  (`Mode3dSelector.Block`). `PilotSession` setzt dann `PilotInput.mode3d` und als Gas `RadioState#throttle3d()`
  (Knüppelstellung 0…1 mit kalibrierter Mitte 0,5; bei Funkverlust 0,5), meldet Wechsel und Sperren und gibt
  `mode3dActive()`, `mode3dSelected()`, `mode3dSource()`, `mode3dBlock()`, `keyOverridesMode3dSwitch()` heraus;
  `TransmitterInput#setThrottle3d` lässt Tastatur- und schrittweises Gamepad-Gas in der Mitte einrasten.
- **Tastenwiederholung (`input.KeyRepeatFilter`):** `KeyMapping#consumeClick` zählt auch die Wiederholungen des
  Betriebssystems (SDL `repeat` ⇒ `KeyMapping.click`). Umschalt-Tasten (Scharf schalten, Flugmodus, Modus-Tasten, Höhe
  und Position halten, Suchpiepser, LEDs) lösen deshalb nur aus, wenn beim letzten Tick die Taste nicht gehalten war:
  gehalten = ein Druck, mehrere Klicks in einem Tick = ein Druck, eine beim Sitzungsstart oder beim Schließen eines
  Fensters schon gehaltene Taste löst nicht aus.
- **Tasten (input besitzt alle Flug-Tasten, `input.client.InputKeys`, Standardwerte in `input.client.KeyDefaults`):**
  Tastaturflug-Knüppel Gas/Gieren ohne eigene Standardtaste: solange sie nicht belegt sind, folgen sie den
  Vanilla-Bewegungstasten (vor/zurück = Gas, links/rechts = Gieren, `InputKeys.effective/down/label`), Pfeiltasten
  (Pitch/Roll), Z scharf/entschärfen (`drone_arm`), R Flugmodus Acro → Angle → Horizon (`drone_flight_mode`), Ziffernblock
  1/2/3 Flugmodus direkt Acro/Angle/Horizon (`drone_mode_acro|angle|horizon`; `FunctionKeys` ordnet ANGLE und HORIZON je
  eine eigene Taste zu), Ziffernblock 4/5 Höhe bzw. Position halten an/aus (`drone_alt_hold`, `drone_pos_hold`), Ziffernblock 6
  3D-Modus an/aus (`drone_mode_3d`, wirkt erst nach dem Freischalten), U
  aufrichten (`drone_flip_upright`), Y FPV beenden (`drone_exit`), Punkt Suchpiepser (`beeper`), F8 LEDs an/aus
  (`drone_leds`), Empfänger-Assistent ohne Standardtaste. drone (`PilotSession`) liest ARM/Flugmodus/Aufrichten/Beenden für Tastatur- **und** Funkflug aus
  `InputKeys` (Umschalt-Tasten über `KeyRepeatFilter`, Flugmodus über `FlightModeSelector`); Tastaturflug startet bei
  `keyboard.startInAngle` im Angle-Modus. „Maus + Tastatur“ (`selectedGuid = "mouse"`) ist Tastaturflug mit Maus für
  Nick/Roll (bzw. Gieren) und Mausrad-Gas; Quelle bleibt `RadioSource.KEYBOARD` mit Gerät `mouse`, Taste
  `kb_mouse_center` (mittlere Maustaste, geteilt mit Vanilla „Block auswählen“) zentriert die Maus-Knüppel
  (`docs/design/mouse-control.md`). rescue (J Rescue, Komma Homepoint), race (Rücktaste) und
  ui (K) und guide (M) behalten ihre eigenen Tasten; `KeyDefaults` spiegelt sie nur für die Doppelungsprüfung. Alle
  Propwash-Tasten lassen sich im Konfigurator-Tab „Tasten“ (`input.client.screen.KeysTab`, Order 420) ändern; der Tab
  arbeitet auf einem Entwurf und setzt die Tasten erst in `save()` (`KeyMapping#setKey`, `KeyMapping.resetMapping()`,
  `Options#save`). Der Knopf „Tasten …“ im Modi-Tab wechselt dorthin, statt den Vanilla-Screen zu öffnen. Keine zwei
  Propwash-Standardtasten dürfen gleich sein, und keine darf auf einer Vanilla-Standardtaste liegen, auch nicht auf den
  F3-Kombinationstasten der Kategorie Debug (Selbsttest „Tastenbelegung ohne Doppelungen“, `KeyDefaultsTest`); einzige
  Ausnahme über `KeyDefaults.SHARED` ist „Maus: Knüppel zentrieren“ auf der mittleren Maustaste neben Vanilla „Block
  auswählen“ (beim Steuern ohnehin geleert) und „Zuschauer-Leiste“ (nur im Zuschauermodus).
- `rawAxes()` (−1..1) und `rawButtons()`: unkalibrierte Gerätewerte für Assistent und Kalibrierung, jeder Aufruf eine
  neue Kopie.
- Keine Client-Typen in der Signatur (Common-Logik darf die Klasse kennen), benutzt wird sie nur clientseitig.
- `record StickPositions(float roll, float pitch, float yaw, float throttle)`: roll −1 (links) … +1 (rechts); pitch −1 (Knüppel
  zu sich gezogen, Nase hoch) … +1 (nach vorn gedrückt, Nase runter); yaw −1 (links) … +1 (rechts); throttle 0 (Gas zu)
  … 1 (Vollgas). `StickPositions.IDLE` = alles mittig, Gas zu; `clamped()` klemmt auf diese Bereiche (NaN → 0).
- `enum SwitchFunction`: `ARM` (scharf schalten), `ANGLE` (selbstnivellierend), `HORIZON` (nivellierend, Flips möglich),
  `RESCUE` (GPS-Rescue/Heimflug), `FLIP_OVER` (Aufrichten: stellt die entschärfte, ruhende Drohne sofort wieder auf), `SET_HOME` (Homepoint setzen), `BEEPER` (Suchpiepser),
  `RACE_RESTART` (Rennen neu starten), `RECORD` (Action-Cam-Aufnahme an/aus, Taste `KeyDefaults.ACTION_CAM_RECORD` = F9 von video),
  `LEDS_OFF` („LEDs aus“ wie LED_LOW bei Betaflight: aktiv = LED-Streifen samt Scheinwerfer aus, nicht belegt oder unlesbar ⇒
  LEDs an; Taste `KeyDefaults.LEDS` = F8), `ALT_HOLD` (Höhe halten, ALTHOLD), `POS_HOLD` (Position halten, POSHOLD; schließt
  ALTHOLD ein), `CARGO_DROP` (Fracht abwerfen), `MODE_3D` (3D-Modus an; Taste `KeyDefaults.MODE_3D` = Ziffernblock 6, an Funke
  und Gamepad unbelegt), `COMMAND_1` … `COMMAND_8` (eigene Befehle, `command()`/`commandIndex()`, `flightFunctions()` ohne
  sie; Texte in `DeviceProfile.commands`, Tasten `KeyDefaults.COMMANDS` unbelegt; nicht im Empfänger-Assistenten;
  `docs/design/switch-commands.md`); Ordinalzahlen sind die Bits von `activeSwitches()` und `readableSwitches()` (Funktionen, deren Belegung am
  aktuellen Gerät lesbar ist; Tastatur und kein Gerät ⇒ 0, `SwitchEvaluator#readable`): nur hinten anhängen;
  Sprachschlüssel `translationKey()` = `propwash.input.switch.<name>`;
  `supportsToggle()` (ARM, ANGLE, HORIZON, RESCUE, BEEPER, RECORD, LEDS_OFF, ALT_HOLD, POS_HOLD, MODE_3D, COMMAND_1…8; nicht bei den Tast-Funktionen
  FLIP_OVER, SET_HOME, RACE_RESTART), `latchesOnGamepad()` (Gamepad-Tasten schalten standardmäßig um; nicht bei den eigenen Befehlen). Die Belegung wird im
  Profil nach Namen gespeichert, neue Funktionen stehen deshalb immer am Ende des Enums.

### `video/client/VideoState` (video)
`VideoState.INSTANCE`: Videoverbindung (Drohnenkamera → Brille) und Posen-Puffer für die verzögerte Kamera. Schreiber
des Puffers: drone (PilotSession nach jedem Physik-Schritt); Leser: drone (Kamera-Pose-Mixin), ui (OSD), audio. Das FOV
setzt video selbst (§ „Kamera und Ansicht“): `horizontalFov()` = Sichtfeld der Kamera im Videomodul der gezeigten Drohne
(`camera()`, auch beim Zuschauen), mit Kreativ-Brille und „Eigenes“ der Wert aus dem Video-Tab; `fpvFov()` rechnet daraus
das vertikale Sichtfeld fürs Fenster. Welches Videosystem die Brille empfängt: `ContentIds.wornGogglesSystem`.
- `system()`: `VideoSystem` der aktuellen Verbindung, null ohne Verbindung. `signalQuality()`: 0 (kein Bild) … 1; ohne
  Verbindung 0. `latencyNanos()`: aktuelle Glas-zu-Glas-Latenz in ns (≥ 0).
- `record(long nanos, double x, double y, double z, Quaternionf orientation)`: hängt eine Pose an (Zeitbasis
  `net.minecraft.util.Util.getNanos()`); Zeitstempel monoton steigend, ältere/gleiche werden ignoriert; `orientation`
  wird kopiert.
- `sample(long nanos, Vector3d outPos, Quaternionf outOrientation) → boolean`: interpoliert (lerp/slerp) die Pose zum
  Zeitpunkt `nanos`; davor → älteste Pose, danach → neueste; false (Ausgaben unverändert), wenn der Puffer leer ist.
  Liegt `nanos` an oder hinter einem Schnitt (`cut`), nach dem noch keine Pose aufgezeichnet wurde, liefert es ebenfalls
  false; der Aufrufer nimmt dann die aktuelle Pose.
- `sampleDisplayed(long frameNanos, Vector3d outPos, Quaternionf outOrientation) → boolean`: die Pose, die die Brille
  im Frame mit dem Zeitstempel `frameNanos` zeigt, also `sample(frameNanos − latencyNanosAt(frameNanos))` (ohne
  Verbindung `sample(frameNanos)`). Übliche Nutzung: `sampleDisplayed(PilotSession.INSTANCE.physicsFrameNanos(), …)`.
  `latencyNanosAt(frameNanos)`: Latenz zu diesem Zeitpunkt, `latencyNanos()` = `latencyNanosAt(Util.getNanos())` für
  Anzeigen. **Die Kamera darf nicht mit der Uhrzeit beim Kamera-Update rechnen:** zwischen `FRAME_START` und
  `Camera#alignWithEntity` liegen Simulation, Entity-Bewegung, Paket-Versand und andere Frame-Listener, ihre Dauer
  schwankt von Frame zu Frame (Substeps, Slices, GC). Mit `Util.getNanos()` zur Kamerazeit zittert das Bild um
  Geschwindigkeit × Schwankung längs der Flugrichtung (200 km/h: 5,5 mm je 0,1 ms, eine 2-ms-Spitze 11 cm); mit dem
  Frame-Zeitstempel hängt die Pose nur an der Frame-Zeit
  (Regressionstest `drone/client/CameraJitterTest`, Selbsttest „drone: FPV-Bild hängt nur an der Frame-Zeit“).
- `cut(long nanos)`: Sprung in der Bahn (Reset, Teleport): Posen vor und nach dem Schnitt werden nie miteinander
  interpoliert. `sample` vor `nanos` nutzt nur ältere Posen (hält die letzte davon), ab `nanos` nur die nach dem Schnitt
  aufgezeichneten (bis zur ersten davon die erste). Liegt `nanos` nicht hinter der neuesten Pose, gilt der Schnitt direkt
  nach ihr; ein zweiter Schnitt vor der nächsten Pose ändert nichts; bei leerem Puffer wirkungslos. drone ruft
  `cut(Util.getNanos())` in `PilotSession.onReset`, nachdem die neue Pose gesetzt ist.
- Der Puffer wird beim Ende der Pilot-Sitzung und bei `ClientBus.DISCONNECT` geleert (video).

### `race/GateEntity`, `race/GateSize`, `RaceModule.GATE` (race)
Entity `propwash:gate`, `EntityType.Builder.of(GateEntity::new, MISC).sized(4F, 4F).clientTrackingRange(16).noUpdateInterval().dontTrackDeltas()`
(wie `END_CRYSTAL`, `network-entity.md` 2.5), Renderer `GateRenderer` (angemeldet in `RaceClient`).
- `GateEntity extends Entity implements ExtraCollider`, `noPhysics = true`.
- Synchronisiert: `size()`/`setSize(GateSize)` (Byte der Ordinalzahl; bestimmt `getDimensions`, `refreshDimensions`
  auf beiden Seiten), `gateIndex()`/`setGateIndex(int)` (0-basierte Position im Kurs, `GateEntity.NO_INDEX` = −1 =
  nicht nummeriert; Setter klemmt auf ≥ −1), `mount()`/`flipped()` (Lage an Boden, Wand oder Decke als ein Byte:
  Ordinalzahl von `GateMount`, Bit 7 = Flugrichtung umgedreht; §15 „Gates an Wand und Decke“). Ausrichtung = Entity-`yRot`
  (stehend: Gate-Öffnung senkrecht zur Blickrichtung; Wandlagen: Richtung aus der Wand heraus).
- Gespeichert: `Size` (`GateSize.CODEC`, stabiler Schlüssel `small`/`medium`/`large`), `GateIndex`, `Mount`
  (`GateMount.CODEC`, fehlt → `standing`) und `Flipped` (fehlt → `false`).
- `GateSize` (`SMALL` 3 × 3, `MEDIUM` 4 × 4, `LARGE` 6 × 4 Blöcke außen, Rahmen und Tiefe je 0,5 — Maße aus
  `docs/ASSETS.md` „Gates“, passend zu den Gate-Modellen; race darf die Maße nur zusammen mit assets-world ändern, nie die
  Schlüssel): `key()`/`getSerializedName()`, `width()`, `height()`, `dimensions()`, `byOrdinal(int)` (unbekannt →
  `MEDIUM`), `CODEC`; von race ergänzt: `frame()` (Stärke von Pfosten/Balken), `depth()` (Tiefe des Rahmens),
  `byKey(String)` (unbekannt → `MEDIUM`).
- `collectColliders` liefert Pfosten und Balken als Boxen (§ `ExtraCollider`), `canBeCollidedWith` false, `isPickable`
  solange nicht entfernt, `isIgnoringBlockTriggers` true (Druckplatten und Stolperdrähte zählen Gates nicht), `hurtServer` nur durch Spieler mit Baurecht (Zerstören/Drop); dazu Durchflug-Erkennung und
  Kurs-Zugehörigkeit (race), Stapeln (§15 „Gates stapeln“).
- **Gate-Items und Kurs-Werkzeug gehören race** (Ids in `ContentIds`): race registriert `gate_small`, `gate_medium`,
  `gate_large` und `course_tool` in `RaceModule`. Ein Gate-Item platziert beim Rechtsklick auf einen Block
  serverseitig ein `GateEntity` (`RaceModule.GATE.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE)`), setzt
  `setSize` passend zum Item, Lage und `yRot` nach geklickter Blockseite, Blickrichtung und gewählter Stellung
  (`GatePlacement`) und `gateIndex = NO_INDEX`; das Kurs-Werkzeug
  nummeriert Gates per Klick (`setGateIndex`) und legt den Kurs an. content führt die Items nur im Creative-Tab
  (Nachschlagen per `ContentIds.item(id)` in `displayItems`, das lazy läuft).

### `race/client/RaceState` (race)
`RaceState.INSTANCE`: Rennzustand des lokalen Spielers. Zeiten in ns, `-1` = „noch keine“.
- `racing()`: fährt gerade ein Rennen (nach dem Start, vor Ziel/Abbruch). `lap()`: aktuelle Runde, 1-basiert, 0 wenn
  nicht im Rennen. `currentLapNanos()`: Zeit seit Start der Runde, 0 wenn nicht im Rennen.
- `lastLapNanos()`/`bestLapNanos()`: letzte bzw. beste abgeschlossene Runde dieses Rennens, sonst −1.
- `nextGate()`: 0-basierter Index des nächsten Gates, −1 wenn nicht im Rennen. `gateCount()`: Anzahl Gates des Kurses,
  0 wenn nicht im Rennen. `splitDeltaNanos()`: Abstand zur Bestrunde am zuletzt passierten Gate (negativ = schneller),
  0 ohne Vergleich.
- Von race ergänzt: `lapTarget()` (eingestellte Rundenzahl), `courseId()` (Kurs des laufenden Rennens), `finished()`
  (Ziel erreicht), `raceNanos()` (Zeit seit dem Start), `totalNanos()` (Gesamtzeit nach dem Ziel),
  `countdownRemainingNanos()` (Restzeit des Countdowns), `sessionBestLapNanos()` (beste Runde dieser Sitzung auf dem
  Kurs, −1 ohne).
- Geister-Rennen (race): `penaltyNanos()` (Frühstart-Strafe des laufenden Rennens, 0 ohne), `falseStarted()` (im Countdown
  schon durchs Start-Gate geflogen), `personalBestNanos()`/`recordGhostNanos()` (Rundenzeit des persönlichen bzw.
  Rekord-Geists für Kurs und Drohnenklasse, −1 ohne), `ghostReference()` (Splits beziehen sich auf einen Geist),
  `inLobby()` (lokaler Spieler steht in der Startaufstellung). `splitDeltaNanos()` ist mit Geist der Abstand zu ihm
  (persönlicher Geist vor Rekord-Geist vor Sitzungsbestzeit), `currentLapNanos()`/`raceNanos()` enthalten die Strafe.

### `rescue/client/RescueState` (rescue)
`RescueState.INSTANCE`: Rescue/Return-to-Home.
- `active()`: Rescue fliegt gerade (übersteuert die Knüppel). `phaseLabel()`: übersetzte Kurzbezeichnung der Phase
  fürs OSD (z. B. „STEIGEN“, „HEIMFLUG“, „LANDUNG“), `Component.empty()` wenn inaktiv. `home()`: Homepoint in
  Weltkoordinaten der aktuellen Dimension, null ohne Homepoint.
- `commandedSticks()`: von Rescue kommandierte Knüppel (geklemmt, `StickPositions#clamped()`), nur gültig solange `active()`,
  sonst `StickPositions.IDLE`. `forceSelfLevel()`: Rescue verlangt den Angle-Modus, nur gültig solange `active()`, sonst false.
- **Übergabe an drone:** Solange `active()`, speist `PilotSession` in jedem Sim-Schritt `commandedSticks()` statt
  `RadioState.INSTANCE.sticks()` ins Flugmodell, bei `forceSelfLevel()` im Angle-Modus unabhängig vom
  ANGLE/HORIZON-Schalter. Rescue rechnet die Werte selbst (aus PilotSession-Telemetrie, `home()` usw.) und aktualisiert
  sie spätestens einmal pro Frame (`ClientBus.FRAME_START`). Arm/Disarm bleibt beim Piloten; Rescue ändert keine
  Schalter. Endet Rescue, übernimmt PilotSession im nächsten Sim-Schritt wieder die Funke.

### `audio/client/AudioHooks`, `audio/client/Beep` (audio)
- `static void onDroneClientTick(QuadcopterEntity)`: einmal pro Client-Tick je Drohne im Client-Level (über
  `QuadcopterEntity.CLIENT_TICK`, angemeldet in `AudioClient.init()`); startet/aktualisiert/stoppt den Motorsound anhand von
  `motorOutput()`, `isArmed()` und der Entfernung (eigene Drohne im FPV ohne Abschwächung, mit Fahrtwind und Rumpeln an
  Bord; alle anderen hören die Drohne von außen: Pegel nach `audio.SoundReach` – voll bis 3 Blöcke, dann 1/r, weich aus
  bis zur Hörweite aus Propgröße (±1 dB nach Wellenleistung und Blattspitzen-Mach) –, ohne Fahrtwind und Rumpeln, Prop-Wash −12 dB;
  Minecraft schwächt die Motor-Sounds nicht selbst ab, `Attenuation.NONE`; `docs/design/drone-sound.md`).
- `static void beep(Beep)`: kurzer Ton sofort lokal (nicht positionsgebunden, Kategorie UI); überlappende gleiche Töne
  werden zusammengefasst. Aufrufer: drone (Arm/Disarm/Akku/Crash), race (Gate/Runde/Countdown/Ziel).
- Beide nur Render-Thread, beide werfen nie.
- `enum Beep`: `ARM`, `DISARM`, `LOW_BATTERY` (wiederholt sich nicht selbst, der Aufrufer bestimmt den Takt), `GATE`,
  `LAP`, `COUNTDOWN` (3, 2, 1), `FINISH`, `CRASH`, `GO` (Startsignal nach dem Countdown). Klang und Lautstärke bestimmt audio.

### `content/ContentIds` (content)
Ids der Items, die andere Module ohne Compile-Abhängigkeit kennen müssen. Ids sind Teil von Speicherständen und Rezepten:
nie umbenennen. Nachschlagen erst nach der Registrierung.
- Registriert von **content**: `DRONE` (`propwash:drone`, trägt `drone_build`, `battery_charge`, `flight_tune`;
  platzieren = Entity), `DRONE_WORKBENCH` (Block + BlockItem), `GOGGLES_ANALOG` (empfängt `VideoSystem.ANALOG`),
  `GOGGLES_DIGITAL` (`VideoSystem.DIGITAL`), `GOGGLES_CREATIVE` (alles, freie Einstellungen); Brillen im Kopf-Slot,
  `Equippable` **ohne** Equipment-Asset, damit Vanilla sie über `CustomHeadLayer` als 3D-Item-Modell auf dem Kopf zeichnet
  (Spieler, Rüstungsständer, Mobs, beide Loader; Modell siehe `docs/ASSETS.md`).
- Registriert von **race**: `GATE_SMALL`, `GATE_MEDIUM`, `GATE_LARGE`, `COURSE_TOOL` (siehe `GateEntity`).
- Gameplay-Loop v2: `REMOTE` (`propwash:remote`, registriert von **drone** in `DroneModule.REMOTE`, trägt
  `bound_drone`), `BATTERY_CHARGER` (Block + BlockItem, content), `isRemote(ItemStack)`, `boundDrone(ItemStack) →
  Optional<BoundDrone>` (Funke).
- `item(Identifier) → Optional<Item>`, `is(ItemStack, Identifier)`, `isGoggles(ItemStack)`.
- `gogglesSystem(ItemStack) → Optional<VideoSystem>`: welches System eine Brille empfängt (`goggles_analog` →
  `ANALOG`, `goggles_digital` → `DIGITAL`, `goggles_creative` → `CREATIVE`), leer für alles andere.
  `wornGoggles(LivingEntity)` → die Brille im Kopf-Slot oder `ItemStack.EMPTY`; `wornGogglesSystem(LivingEntity)` →
  deren System. Nutzer: drone (Pilotenprüfung „Brille passt zum Videosystem“ mit `VideoSystem#receives`), video
  (Signal). Die Zuordnung Id → System gehört content und steht nur hier.

## 12. Plattform-Dienste

| Dienst | Zugriff | Methoden |
|---|---|---|
| `platform.IPlatformHelper` | `Services.PLATFORM` | Name, `isModLoaded`, `getModVersion` (Version aus den Loader-Metadaten), Dev-Umgebung, `getConfigDir`, `getGameDir`, `isClientDist`, `creativeTabBuilder`, `extendedMenuType`, `openMenu` |
| `platform.INetworkHelper` | `Services.NETWORK` (Module nutzen `PropwashNet`) | `canSend`, `sendToPlayer`, `clientboundPacket` |
| `platform.client.IClientPlatformHelper` | `platform.client.ClientServices.PLATFORM` (nur Client) | `createKeyCategory`, `canSendToServer`, `sendToServer`, `sameKeyModifier` (NeoForge-Tastenmodifikatoren, Fabric immer gleich) |

Implementierungen: `fabric/…/fabric/platform`, `fabric/…/fabric/client`, `neoforge/…/neoforge/platform`,
`neoforge/…/neoforge/client`, eingetragen in `META-INF/services/<Interface-FQN>` je Loader.

## 13. Gradle

| Task | Zweck |
|---|---|
| `./gradlew build` | beide Loader bauen (inkl. `mergeLang`) |
| `:fabric:runClient`, `:neoforge:runClient` | Dev-Client, Run-Verzeichnis `runs/client` des Loaders. Spielt offline als `LardTom` mit dessen Skin und Umhang (`propwashDevPlayer` im Wurzel-Build): `--username` plus die UUID des echten Kontos, damit lädt Vanilla die signierten Skin-Daten selbst; die UUID fragt Gradle einmal bei `api.mojang.com` ab und merkt sie sich in `propwash-dev-player.properties` im Run-Verzeichnis, offline bleibt der Standard-Skin. Anderer Name: `-PpropwashDevName=<Name>`, aus: `-PpropwashDevName=` (leer) oder eigenes `--username` in `--args`. Läuft schon ein Client mit dem Namen, behält der zweite seinen Standardnamen. Nur im Einzelspieler: ein Offline-Server (`online-mode=false`) verteilt keine Skins, dort spielt `LardTom` mit Standard-Skin. NeoForge bekommt dafür `--accessToken 0` (ohne Token schaltet NeoForge den `offlineDeveloperMode` ein und lädt kein Profil) und loggt wie Fabric harmlose Fehler zu Nutzer-Eigenschaften und Realms. Nichts davon ist Mod-Code oder im Release-Jar |
| `:fabric:runClientSelftest`, `:neoforge:runClientSelftest` | Selbsttest (Abschnitt 9), `runs/selftest-client`; Fabric mit `-PpropwashScreenshots=true` zusätzlich Bildschirmfotos nach `docs/screenshots/` |
| `:fabric:runClientSmoke`, `:neoforge:runClientSmoke` | Start-Rauchtest ohne Selbsttest-Modus (Abschnitt 9), `runs/smoke-client` |
| `:fabric:runServer`, `:neoforge:runServer` | Dev-Server, `runs/server`. Fabric: EULA muss in `fabric/runs/server/eula.txt` von Hand akzeptiert werden; NeoForge-Dev stimmt automatisch zu (`Eula` prüft `SharedConstants.IS_RUNNING_IN_IDE`) |
| `:fabric:runServerSmoke`, `:neoforge:runServerSmoke` | Server-Rauchtest (`dev.ServerSmoke`, Property `propwash.serversmoke`): dedizierter Server ohne GUI in `runs/smoke-server`, Flachwelt, Offline-Modus, Port 25601; spawnt nach `STARTED` eine Drohne, prüft die Rechte der Welt-Regeln (`server.WorldRulesRights`: Gast ohne Recht per Tab, Voreinstellung und `/gamerule` abgelehnt, als Operator übernommen; Admin ohne Operator mit `/gamerule`-Recht aus einem simulierten Rechte-Mod – der Test ersetzt wie Vanilla Permissions die Bedingung des Befehlsknotens `gamerule` per Reflection, weil im Dev-Lauf kein Rechte-Mod installiert ist – ändert per Tab, Voreinstellung und Befehl, ohne und nach dem Entzug nur lesbar), den Blockbruch und die Drohnen-Tags (`drone.DroneTagChecks`, `docs/design/drone-tags.md`), hält den Server nach 100 Ticks an und schreibt PASS/FAIL nach `propwash-selftest-result.txt`. Der Task schlägt fehl ohne PASS oder bei `/ERROR]`-, `/FATAL]`- oder Mixin-Fehlerzeilen in `logs/latest.log`. `prepareServerSmoke` legt vorher `eula.txt`, `server.properties` neu an und löscht die alte Welt. Läuft bei jeder Integration zusammen mit Selbst- und Rauchtest. |
| `prepareRuns` | schreibt/patcht `options.txt` stumm (Master 0.001, alle anderen `soundCategory_*` 0, `pauseOnLostFocus:false`, `onboardAccessibility:false`); alle `run*`-Tasks hängen davon ab |
| `-PpropwashTestWindowFront=true` | Selbsttest- und Rauchtest-Clients öffnen ihr Fenster sonst im Hintergrund, ohne Fokus, Dock-Symbol und Mausgriff (`docs/testing.md`, „Testfenster ohne Mausgriff und Fokus“); mit der Property startet das Fenster wie ein normaler Client, der Zeiger des Systems bleibt trotzdem frei |
| `:common:mergeLang` | Sprachdateien zusammenführen |
| `publishToMavenLocal` | veröffentlicht `de.lardtom:propwash-common-26.3`, `propwash-fabric-26.3` und `propwash-neoforge-26.3` (Version aus `gradle.properties`, samt Quellen- und Javadoc-Jar) nach `~/.m2` für die Addon-Entwicklung (`docs/api/addon-guide.md` §1.1); Gruppe `maven_group` |
| `python3 tools/strip_comments.py` | entfernt Kommentare aus `common/src`, `fabric/src`, `neoforge/src` (kein Gradle-Task) |
| `python3 tools/data/survival.py [--check]` | erzeugt bzw. prüft Rezepte, Rezept-Fortschritte, Truhen-Beute und NeoForge-Loot-Modifier (§15 „Überlebensmodus“; kein Gradle-Task) |
| `python3 tools/data/world_courses.py [--check]` | erzeugt bzw. prüft die Rennkurse in der Welt: NBT-Vorlagen, Strukturen, Set, Tags, Truhen-Beute (§15 „Rennkurse in der Welt“; kein Gradle-Task) |
| `python3 tools/public_export.py [--check-only]` | öffentlicher Stand ohne Historie nach `<Hauptbaum>-public`: exportiert einen Commit ohne die Pfade aus `tools/public_export.json` (Tages-Changelogs, Bildschirmfotos, das Werkzeug selbst), prüft Lizenzdateien, Sperrliste (lokale Datei außerhalb des Repositorys plus Namen und Adressen aus der Git-Historie), lokale Pfade, Zitate des Besitzers und interne Ordner, baut den Export, prüft die Lizenzdateien in den Jars und legt dort genau einen Commit an (ein vorhandener Export wird ohne seine frühere Historie neu angelegt; mit Remote Abbruch, dann `--append` oder `--fresh`); kein Remote, kein Push (`docs/RELEASING.md` §4 Schritt 9; kein Gradle-Task, nicht im Export) |

Immer mit `JAVA_HOME=/opt/homebrew/opt/openjdk@25`.

## 14. Stolperfallen (beim Aufbau gefunden)

- `mergeLang` schreibt nach `src/main/resources`; deshalb deklariert der Task keine Outputs (sonst meldet Gradle 9
  implizite Abhängigkeiten bei `sourcesJar`) und läuft immer. Die Loader bekommen die Ressourcen über
  `commonResources` (`builtBy mergeLang`).
- `processResources` expandiert `${…}` in `neoforge.mods.toml`, `fabric.mod.json`, `pack.mcmeta` und `*.mixins.json`:
  dort kein `$` in Kommentaren/Texten verwenden (z. B. `CreativeModeTab$Output`).
- Ohne Renderer stürzt der Client ab, sobald eine Entity gerendert wird: jede neue Entity braucht sofort einen
  (notfalls vorübergehend `NoopRenderer::new`).
- NeoForge prüft beim Senden, ob die Gegenseite den Kanal kennt, und wirft sonst: immer über `PropwashNet`/`ClientNet` senden.
- NeoForge bricht den Client-Start ab, wenn ein S2C-Payload keinen Client-Handler hat (`ClientNetworkRegistry.setup`);
  Fabric prüft das nicht. Der Selbsttest allein hat das nicht gefunden, weil der Handler nur im Selbsttest-Modus
  registriert wurde — deshalb gibt es den Rauchtest und die Prüfung in `ClientNet.freeze()`.
- NeoForge: `SynchedEntityData.defineId` muss aus der Entity-Klasse selbst aufgerufen werden (Aufrufer-Prüfung).
  Eigene Serializer nur über `PropwashRegistries.entityDataSerializer` (NeoForge verbietet `EntityDataSerializers.registerSerializer`).
- NeoForge Dev setzt `SharedConstants.IS_RUNNING_IN_IDE`: `Entity#move(MoverType.SELF, …)` wirft auf der Seite ohne
  Simulationsrecht (`canSimulateMovement()` prüfen, siehe `docs/api/network-entity.md` 2.4).
- Fabric: Tasten nur im Client-Entrypoint registrierbar; NeoForge: Kategorie über `new KeyMapping.Category` + Event.
  Deshalb erzeugt `ClientRegistrar.keyCategory()` die Kategorie über den Plattformdienst — nie selbst anlegen.
- Erster Start zeigt sonst den Barrierefreiheits-Onboarding-Screen statt des Titelbildschirms (`onboardAccessibility:false`
  in `prepareRuns`).
- NeoForge zeigt bei jeder Lade-**Warnung** („Mods loaded with N warning(s)“) einen Warnbildschirm vor dem Titelbildschirm;
  der Selbsttest schlägt dann nach 60 s mit dem Screen-Namen fehl. Beispiel: `logoFile` in `neoforge.mods.toml` ist
  veraltet (→ `iconFile`). Warnungen also beheben, nicht wegklicken.
- NeoForge führt Mod-Konstruktoren (also `PropwashCommon.init()`/`PropwashClient.init()`) auf `modloading-worker`-Threads
  aus, nicht auf dem Render-Thread.
- Fabric feuert `ServerPlayConnectionEvents.DISCONNECT` und `ClientPlayConnectionEvents.DISCONNECT` meist auf einem
  **Netty-IO-Thread** (`Connection#channelInactive`, wer zuerst kommt, gewinnt). Deshalb kommt `PLAYER_LEAVE` von
  `ServerPlayerEvents.LEAVE` (`PlayerList#remove`, Server-Thread) und `ClientBus.DISCONNECT` aus dem Core-Mixin in
  `Minecraft#disconnect`/`clearClientLevel` (Render-Thread). Loader-Verbindungsevents nie direkt benutzen.
- NeoForge feuert `ClientPlayerNetworkEvent.LoggingOut` bei **jedem** `Minecraft#disconnect`, auch ohne Sitzung
  (beim Anlegen/Betreten einer Welt, Spieler null) und mehrmals beim Verlassen. Die Foundation benutzt es nicht; die
  Sitzungs-Klammer `ClientBus.fireLogin/fireDisconnect` garantiert genau ein `DISCONNECT` je `LOGIN`.
- `tools/strip_comments.py` entfernt auch `//`-Kommentare hinter Code; Erklärungen gehören in `docs/`.
- `RenderTypes.entityTranslucent`/`itemTranslucent` schreiben Tiefe und laufen ohne „Verbesserte Transparenz“ vor dem durchscheinenden
  Terrain: Wasser dahinter verschwindet. Durchscheinende Welt-Optik der Drohne über `AfterWaterRenderTypes` (§15).
- Selbst erzeugte Struktur-Vorlagen (NBT) brauchen in 26.3 die Paletten-Schlüssel `id`/`properties`
  (`NbtUtils.readBlockState`); `Name`/`Properties` werden mit aktueller `DataVersion` nicht mehr umgeschrieben und laden
  als Luft – Entities der Vorlage erscheinen trotzdem. Teppiche und Laternen haben eine Kollisionsform.
- Antwortet der Server auf eine Abmeldung des Clients (z. B. Zuschauen beenden) mit einer Bestätigung, muss der Client
  sie zuordnen: kommt die Bestätigung erst nach einer neuen Anfrage an, darf sie diese nicht beenden
  (`SpectateSession.pendingStopAcks`).
- `LapVerifier.find` liefert die **älteste** vollständige Runde nach `afterSequence`. Nach einem Ziel (Sitzung
  zurückgesetzt) liegt die letzte Runde noch im Zeitfenster des Countdowns; `RaceServer.lap` überspringt deshalb Runden,
  die vor dem neuen Start begannen, statt die neue Runde als „ausstehend“ zu verwerfen.
- Fabric flusht alle Register in `onInitialize`, **bevor** Client-Entrypoints laufen; NeoForge flusht erst in den
  `RegisterEvent`s, **nachdem** alle `@Mod`-Konstruktoren (auch der Client-Einstieg) gelaufen sind. Eine Anmeldung aus
  der Client-`init()` warf deshalb nur auf Fabric und landete auf NeoForge nur im Client — daher die Sperre nach
  `PropwashCommon.init()` (§2).

## 15. Nachträge vor der Modul-Phase (gelten vor allem anderen in diesem Dokument)

### Prüfen während der Modul-Arbeit — kein Gradle, kein Spielstart
- `tools/fastcheck.sh common|fabric|neoforge|all` kompiliert mit `javac` gegen den vorbereiteten Classpath des Hauptbaums
  (1–3 s). `tools/fastcheck.sh test` kompiliert und startet alle JUnit-Tests in `common/src/test` (optional
  JUnit-Filter anhängen, z. B. `tools/fastcheck.sh test --select-package=de.lardtom.propwash.race`). Mit
  `PROPWASH_CALIBRATION_ADDON=<Add-on>/common/src/main/resources` und ohne Filter laufen zwei JVMs nacheinander: die
  Suite ohne Add-on-Daten, dann nur die Tests mit Tag `addon-daten`. In einer JVM würden die Add-on-Teile, die
  `CalibrationHarness` registriert, in die Tests für eingebaute Teile durchschlagen (Nachprüfung 0.5.0: 24 Fehler, vorher
  `OutOfMemoryError` in `DerivationTest`, das jetzt nur eingebaute Teile kombiniert).
- **Modul-Agenten starten weder Gradle noch Minecraft.** Build beider Loader, Selbsttest und Rauchtest laufen
  gesammelt im Integrations-Worktree. Selbsttest-Schritte des Moduls trotzdem schreiben (Abschnitt 9) — sie laufen dort.
- Neue AT/AW-Einträge sind Foundation-Wünsche (fastcheck sieht sie nicht).

### Neue Verträge
| Datei | Besitzer | Vertrag |
|---|---|---|
| `ui/client/config/ConfigTab`, `ConfigTabHost`, `ConfigTabs` | ui (Rahmen, Screen) | Tabs des Konfigurators. Jedes Modul, das Einstellungen hat, registriert in seiner Client-`init()` `ConfigTabs.register(MeinTab::new)`; ui baut den Screen (`ConfigTabs.setOpener`), fragt `ConfigTabs.create()` beim Öffnen ab und ruft `attach(host, x, y, w, h)` → `build()`. Widgets über `host.addWidget(...)`, Neuaufbau `host.rebuild()`, Wertänderung `host.changed()`. ui darf die Klassen erweitern, aber keine Signatur brechen. |
| `drone/client/PilotSession#sim()` | drone | laufendes Flugmodell (`sim.DroneSim`, null wenn inaktiv); Telemetrie über `sim().telemetry()`. `physicsFrameNanos()` = Zeitstempel des letzten Physik-Frames. |
| `drone/QuadcopterEntity#renderAttitude(partialTick, dest)`, `rotorAngle(motor, partialTick)`, `motorOutput(motor)` | drone (implementiert), drone-render (nutzt) | interpolierte Lage fürs Rendern, Prop-Winkel je Motor (Radiant), Motorleistung je Motor 0..1 |

### Verträge aus dem Gameplay-Loop v2 (`docs/design/gameplay-loop-v2.md`)
| Datei | Besitzer | Vertrag |
|---|---|---|
| `build/BatteryFit` | content (reines Java in `build`) | Akku-Kompatibilität je Rahmen: `check(frame, battery)` → `OK`, `WEAK`, `CELLS_LOW`, `CELLS_HIGH`, `TOO_HEAVY` (Prüfreihenfolge so), `allowed(result)` (OK/WEAK), `rule(frame)`, `weakReason`, `fitting(frame[, result])`, `framesFor(battery[, result])`. Quelle für Werkbank (`DroneBuild.Issue.BATTERY_FRAME`/`BATTERY_UNDERSIZED`), Akkutausch, Tooltips und Akku-Menü. Tabelle in `docs/ASSETS.md` „Akkus je Rahmen“. Mit der Welt-Regel `propwash:part_compatibility` aus erlauben Werkbank, Akkutausch und Akku-Menü jeden Akku (`build.PartCompatibility#batteryFits`, `docs/design/part-freedom.md`) |
| `content/Batteries` | content | `typeOf`, `isBattery`, `charge` (geklemmt, Standard 1), `stackOf(type, charge)`, `storeCharge` (quantisiert), `quantize`, `openCircuitVoltage(type, charge)` (= Zellen × Ruhespannungskurve; dieselben Stützstellen wie `sim.CellCurve`, die content als Kopie führt, weil `CellCurve.ocv` paketprivat ist — Sim-Wunsch: `ocv` öffentlich machen, dann ruft content sie direkt) |
| `content/ItemHelp`, `content/TooltipShift` | content | Shift-Tooltips für alle Propwash-Items: ohne Shift Kurzzeile + Statuszeilen + `propwash.help.shift`, mit Shift zusätzlich `…what.N`, „Benutzung“ (`…use.N`), „Passt zu“, „Daten“. Schlüssel `propwash.help.<id>.summary/what.N/use.N` (N = 1…8, Zeilen ≤ 48 Zeichen). `TooltipShift.SHIFT` setzt `ContentClient`, `TooltipShift.override` nur für Selbsttests. Passt der Shift-Tooltip nicht auf den Bildschirm (`TooltipShift.LINES` = (GUI-Höhe − 14) / 10 Zeilen, abzüglich 3 für Name und Mod), fallen der Reihe nach die Beschreibung (`what`), „Passt zu“ und „Benutzung“ weg; „Daten“ bleiben. Mit `override` gilt keine Grenze. Die Drohne zeigt den Zustand oben in der Statuszeile (schlechtestes Teil) und unter „Daten“ nur die übrigen beschädigten Teile (`PartCondition.categoryLinesBesidesSummary`) |
| `content/ChargeMath` + `BatteryChargerBlockEntity` | content | Ladegerät: voller Pack `max(minSeconds, referenceSeconds × (mAh/1300)^exponent)`, mit Redstone × Faktor; Werte aus `ServerRulesConfig` (`batteryChargeSeconds`, `batteryChargeCapacityExponent`, `batteryChargeMinSeconds`, `redstoneChargeMultiplier`) |
| `drone/PilotRules`, `drone/DroneInteraction` | drone | reine Entscheidungen: Modus beim Start (keine Brille = Sichtflug, getragene Brille, deren System das Video empfängt = FPV, falsche Brille = abgelehnt bzw. im Flug kein Bild; Brillen werden nicht gebunden) und die Interaktionstabelle beim Rechtsklick auf eine Drohne |

### Überlebensmodus: Rezepte, Zwischenteile, Truhen-Beute (content, `docs/design/survival.md`)
| Datei | Besitzer | Vertrag |
|---|---|---|
| `tools/data/survival.py` | content | **einzige Quelle** für alle Rezepte (`data/propwash/recipe`), deren Freischalt-Fortschritte (`advancement/recipes/<ordner>`), die Beutetabellen `data/propwash/loot_table/inject/*` und die NeoForge-Loot-Modifier (`neoforge/src/main/resources/data/propwash/loot_modifiers/chests/...`). Nie von Hand ändern: Skript anpassen, `python3 tools/data/survival.py` ausführen; `--check` prüft Stand, Vanilla-Ids/Tags und Rezeptkonflikte gegen `.ref/vanilla`. Race behält seine Gate-/Kurs-Werkzeug-Rezepte (reine Vanilla-Zutaten, Reiter `building`/`equipment`), sie stehen nur mit im Skript. Freischaltung: jede Zutat **oder** jedes Propwash-Item (`has_propwash_item`) schaltet ein Rezept frei; Reiter: Zwischenteile, Werkbank, Ladegerät `misc`, Gates `building`, alles andere `equipment`; der Fortschritts-Ordner folgt dem Reiter (`misc`, `tools`, `building_blocks`). `--check` vergleicht die Item-Liste mit `assets/propwash/items`, neue Items also dort und im Skript eintragen (JUnit `SurvivalRecipeBookTest`, Begründung in `docs/design/survival.md` „Rezeptbuch“) |
| `mixin.ui.client.FullTextSearchTreeMixin`, `ui.client.ModSearch` | ui | Rezeptbuch- und Kreativ-Suche: ein Präfix von „propwash“ ab vier Zeichen ohne `:` findet zusätzlich alle Einträge im Namensraum `propwash` (Vanilla sucht ohne `:` nur in Namen/Tooltips) |
| `content/CraftingMaterial`, `content/ContentMaterials`, `content/MaterialItem` | content | Zwischenteile `carbon_plate`, `copper_coil`, `circuit_board`, `lipo_cell`, `hd_chip` (Item-Ids = `key()`, nie umbenennen). Rezepte dürfen nur Vanilla-Zutaten und diese Zwischenteile nutzen (JUnit `SurvivalDataTest`, `ContentDataTest`). Shift-Tooltip: `propwash.help.<id>.summary/what.N`, gemeinsame Zeile `propwash.help.material.use.1`, Fundorte `…<id>.use.N`, Abschnitt „Zutat für“ (`propwash.help.section.ingredient_for`) aus `…<id>.fits.N` |
| `content/ItemHelp.Builder#fitsHeader(key)` | content | ersetzt die Überschrift des „Passt zu“-Abschnitts (Standard `propwash.help.section.fits`) |
| `content/ChestLoot` | content | Zuordnung Vanilla-Truhentabelle → Propwash-Beutetabelle (`tableFor(key)`, `injections()`, `tables()`), `FOUND_CHARGE` = Ladestand gefundener Akkus/Drohnen (0,4 = Lagerladung). Fabric hängt pro Ziel einen Pool mit einem `NestedLootTable`-Verweis an (`fabric.FabricChestLoot`, `LootTableEvents.MODIFY`, aufgerufen in `PropwashFabric#onInitialize`); NeoForge nutzt dieselben Paare als Global Loot Modifier `neoforge:add_table` mit Bedingung `neoforge:loot_table_id` (reine Daten, keine Loader-Klasse). `SurvivalDataTest` prüft, dass beide Seiten dieselben Ziele haben |

Selbsttest-Schritte (`content.client.SurvivalSelfTests`, angemeldet in `ContentClient`): Zwischenteile mit Modell, Textur, Name,
Creative-Tab-Eintrag und Shift-Abschnitt „Zutat für“; jedes Rezept lädt, wird aus den eigenen Zutaten eindeutig gefunden
(`RecipeManager#getRecipeFor`, also kein Konflikt mit Vanilla oder anderen Rezepten), liefert sein Item (Akkus voll geladen)
und hat einen Freischalt-Fortschritt, Kreativ-Brille und Drohne haben keins; jede Beutetabelle würfelt (3000 Würfe, feste Seeds)
genau die eingetragenen Items, jede Ziel-Truhentabelle liefert in 400 Würfen Propwash-Beute nur aus ihrer Tabelle mit einer
Fundrate zwischen 5 und 70 %, gefundene Akkus und die Whoop-Drohne haben die Lagerladung und den Whoop-Bauplan.
Rezeptbuch im Überlebensmodus (`content.client.SurvivalRecipeBookSelfTest`): der Spieler wird auf Überleben gestellt, sein
Inventar gesichert und geleert, alle Propwash-Rezept-Fortschritte und -Rezepte zurückgesetzt (Server und Client kennen danach
keins); ein aufgehobener Kupferbarren schaltet genau die Rezepte mit Kupferbarren als Zutat frei; die Drohnen-Werkbank im
Inventar schaltet alle Propwash-Rezepte frei; der Client zeigt sie im erwarteten Reiter, die Suchen „propwash“, „propw“,
„propwash:“ finden alle, der Name der Kupferspule findet sie. Der Folgeschritt stellt Inventar und Spielmodus wieder her.

### Abschattung der Videostrecke (video, `video.link`, `video.client.ClientBlockLoss`, `VideoState`)
- **Rechnung als reines Java** in `video.link` (Modell, Zahlen und Entscheidungen in `docs/design/vtx-obstruction.md`):
  `RfMaterial` (15 Klassen, dB je Meter aus `Dielectric` nach ITU-R P.2040/P.527, dB je Durchgang, Laubdichte),
  `Medium` (Material, Formboxen eines Teilblocks oder ganzer Block, alte Reflexion für `Multipath`), `ObstructionTracer`
  (DDA über einen Polygonzug, Länge nur innerhalb der Formboxen, Durchgangsdämpfung einmal je zusammenhängendem Lauf),
  `RayLoss` (lineare dB plus Laubmeter, Laub über `FoliageLoss` nach P.833), `ObstructionScan` (Spindel-Strahlen durch
  die Fresnel-Zonen, Mittelstrahl und vier Seiten mit bis zu 15 Proben, Stopp nach zwei freien Proben ab ν 0,75,
  Budget in Blockzellen, exakt 0 dB ohne Treffer), `FresnelCut` (1D-Fresnel-Kirchhoff je Schnitt mit Ortsmittel,
  Kombination `X · Y + (1 − T)(X + Y − T)`), `KnifeEdge` (Fresnel-Integrale nach Boersma, J(ν) nach P.526 Gl. 31).
- **Client** (`ClientBlockLoss`): Einordnung je `BlockState` über Block-Tags, Klang (`SoundType`) und wenige Blöcke,
  Form aus `getShape(EmptyBlockGetter, ZERO)`, zwischengespeichert; liest Chunks direkt (`getChunkNow`, leere
  Abschnitte = Luft). `VideoState` startet je Tick eine Runde vom Kopf des Spielers zur Antenne (Abstand bis zur
  1,05-fachen Grenzreichweite + 16), höchstens 24 000 Zellen je Tick, und reicht `obstructionDb` an `LinkBudget`; Zeiten je
  Tick (Durchschnitt, Spitze), Strahlen und Zellen der letzten Runde für den Selbsttest.
- **Selbsttests**: „video: Funk-Material der Blöcke“ (Einordnung von 36 Blöcken, Formen, alte Reflexionswerte) und
  „video: Abschattung hinter Baum, Haus und Wald, Vorbeiflug und Runde ums Haus“ (Anlage 400 Blöcke vom Spieler mit
  denselben Szenen wie JUnit `ShadowScenariosTest`: Eiche, Haus aus Bruchstein mit Fenstern, Wald; 26 Stellungen –
  Baum 20/50/90 m, Haus 30/60 m, Wald 50 m, freies Feld 90 m, Vorbeiflug am Baum in 11 seitlichen Stellungen, Runde
  ums Haus in 8 Stellungen; jede Stellung innerhalb ±1,5 dB des Modellwerts, frei exakt 0 dB, knapp vorbei bei freiem
  Mittelstrahl über 0,5 dB, Signal passt zur Dämpfung, Durchschnitt je Tick unter 3 ms). Ändert sich das Modell, die
  Werte in `ShadowSelfTests.probes()` aus der JUnit-Ausgabe nachziehen. Bildschirmfotos `vtx_shadow_*` über Zuschauen
  (Analog- und Digitalbild bei echter Dämpfung) und ein Überblick mit der Drohne hinter dem Baum.

### Videobild-Störungen (video, `video.AnalogBreakup`, `video.DigitalBreakup`, `video.Multipath`)
- **Stufen als reine Funktionen der Verbindung** (0..1, JUnit `AnalogBreakupTest`/`DigitalBreakupTest`): Analog 1 klar/weich
  (≥ 0,80), 2 Glitches – verrissene Zeilen, Funken, Zeilenzittern (0,60–0,80), 3 Farbverlust – erst in Streifen, ab 0,50
  ganz schwarz-weiß (Luma 0,299/0,587/0,114, der Mittenkontrast wird mit dem Farbverlust leicht angehoben – `analogMonoContrast` –, damit weiße Wolken vor dem Himmel und Boden gegen Himmel getrennt bleiben; kein Schärfe- oder Kantenfilter), dazu leichtes Helligkeitskorn (bis 14 %, ab 0,56, rein additiv ohne Grauschleier; erst der Schnee ab 0,40 mischt zum Grau) und stärkeres Zeilenzittern (0,40–0,60), 4 Schnee über
  dem Graubild mit horizontalem Versatz und kurzem Rollen (0,10–0,40), 5 volles Rauschen (< 0,10). Digital: klar, weich,
  blockig mit Auflösungsstufen (Hysterese), Standbilder, verloren – nie Schnee.
  `FeedModel` würfelt Ereignisse daraus (Poisson, Value-Noise, kein Muster) und legt Mehrwege-Aussetzer (`FeedInput.multipath`
  aus `Multipath`: reflektierende Blöcke um Drohne/Pilot und teilweise Abschattung) darüber.
- **Getrennte Effekt-Funktionen**, gleich benannt in Shader (`post/video_feed.fsh`) und Ersatz-Overlay (`VideoOverlay`, für
  Shaderpacks): Glitches, Farbverlust, Schnee, Sync, Bildschirm, Rauschen/Verlust. Wer das Bild erweitert (Scheinwerfer,
  Aufnahme), hängt sich davor oder danach ein, nicht dazwischen.
- **Sync-Verlust** verschiebt im gerissenen Band jede Zeile unterschiedlich (Value-Noise plus Zeilenrauschen, an den
  Bandenden schwächer, manchmal durch Lücken in mehrere dünne Bänder geteilt); die freie Kante füllt Austastgrau mit einem
  hellen Nachzieher, nie reines Schwarz. **Digitaler Zerfall** zeigt Makroblöcke in Gruppen (Bereiche von 7 × 2 Blöcken
  und ganze Blockzeilen): altes Bild verschoben (Nachziehen), eingefrorene Kacheln oder verschmierte Spalten, dazu die
  globale Auflösungsstufe – keine bunten Kacheln. Das Ersatz-Overlay zeichnet dieselben Gruppen in neutralem Grau.
- **OSD immer oben:** Der Shader-Pass läuft vor der GUI, das Ersatz-Overlay (`propwash:video_feed`) ist eine HUD-Ebene vor
  dem OSD. Alles, was über dem Bild liegen muss, meldet seine HUD-Ebene erst in `ClientBus.REGISTER_HUD_LAYERS` an und liegt
  damit hinter allen Ebenen aus den `init()`s: die Rennmeldungen (`propwash:race`) und die Signal-Karte (`propwash:video_link`).
  `OsdHud` und im FPV `RaceHud` beginnen zusätzlich mit `nextStratum()`. Bei offenem `ThemedScreen` zeichnet das OSD nicht
  (Aufnahmen-Liste, Konfigurator). Die Deckkraft des OSD steigt mit dem Bildrauschen (`OsdData.videoNoise` aus
  `VideoState#noiseLevel()`, Deckkraft `a + (1 − a) × min(1, 1,4 × Rauschen)`), damit Schnee die Schrift nicht körnig macht.
  Bei Analog verschiebt sich das OSD nur um `OsdInterference.sharedShift` (höchstens 3 GUI-Pixel, aus `VideoState#osdShift()`
  → `OsdData.videoShift`) plus höchstens 1 Pixel Zittern. Ist das Video verloren (`VideoState#videoLost()`,
  `noSignal ≥ VideoState.LOST_LEVEL` = 0,5 → `OsdData.videoLost`, oder Videosignal unter `OsdWarnings.NO_VIDEO` = 0,5 %),
  blendet das OSD Fadenkreuz, künstlichen Horizont, Seitenleisten und Home-Pfeil aus, und die kritische Warnung `KEIN VIDEO`
  ersetzt bei Analog und Digital gleich `VIDEO SCHWACH`. `VideoOverlay.extractCard` zeichnet in OSD-Schrift
  (`osd.client.OsdText`) unter der Bildmitte die Videoverbindung (`propwash.video.link`, „VIDEO-LINK 0%“ mit Balken), aber nur,
  wenn das OSD das Element `VIDEO_SIGNAL` nicht selbst zeigt (`OsdHud.showsVideoSignal()`); sonst meldet das OSD den Verlust
  mit „VID 0“. Eine eigene „Kein Signal“-Überschrift gibt es bei verbundener Brille nicht.
- **Rennmeldungen im FPV** (race): `RaceHud` zeichnet Countdown und Zeilen im FPV in OSD-Schrift ohne Kasten, die Zeilen ab
  OSD-Zeile 11 in der Mitte; im Sichtflug bleibt der Theme-Kasten oben. Gate-Zeilen nennen die Zwischenzeit
  („Gate 2/4 · Zwischenzeit 7.500“).
- **Arm-Sperren** meldet im FPV nur das OSD (Warnungen wie `GAS NICHT UNTEN`); der Propwash-Toast
  „Scharfschalten nicht möglich“ kommt nur im Sichtflug.
- Selbsttest „video: Störstufen je Signalstärke“ und im Bildschirmfoto-Modus `video_analog_stage2…5.png`,
  `video_digital_breakup.png`, `video_digital_lost.png` (jeweils mit OSD).
- **Video-Tab:** statt einer Statuszeile zeigt `video.client.BreakupPreview` unter den Reglern fünf bewegte Kacheln mit den
  Störstufen der gewählten Brille (Analog/Kreativ: klar, Störungen, Schwarz-weiß, Schnee, Rauschen; Digital: klar, weich,
  blockig, Standbilder, verloren – die verlorene Kachel heißt wie die OSD-Warnung „KEIN VIDEO“, `propwash.video.no_video`; Stärke folgt „Effektstärke“). Nur wenn das Ersatz-Overlay aktiv ist, steht darunter ein
  Hinweis in Warnfarbe (`propwash.video.status.fallback`).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `osd/client/OsdText` | osd | Text in OSD-Schrift für andere Module: `scale(graphics, faktor)` (OSD-Größe × Faktor, auf Pixel gerastert), `width`, `height(scale)`, `draw`/`drawCentered(graphics, font, text, x, y, scale, argb)` mit der Kontur aus der OSD-Einstellung, `color()`/`warningColor()` voll deckend |

### Compat-Pakete (`de.lardtom.propwash.compat.<mod>`)
- Optionale Integrationen liegen nach `docs/CONVENTIONS.md` in `compat.<mod>` (nur Reflection/`compileOnly`), nicht im
  Modulpaket. Jedes `compat.<mod>`-Paket hat genau einen Besitzer, der es hier einträgt; andere Module rufen es nur auf.
- `compat.iris` → **video**: `compat.iris.client.IrisCompat.shaderPackActive(long nanos)` — true, wenn Iris oder Oculus
  geladen ist und ein Shaderpack läuft (per Reflection auf `IrisApi`, höchstens einmal pro Sekunde abgefragt; bei einem
  Reflection-Fehler dauerhaft false).
  `IrisCompat.assignPipeline(pipeline, program, shadowProgram)` (von drone-render genutzt) ordnet eine eigene `RenderPipeline` per
  `IrisApi#assignPipeline`/`#assignPipelineShadow` einem Iris-Programm zu (Namen der Enums `IrisProgram`/`IrisShadowProgram`); ohne
  Zuordnung zeichnet Iris eigene Pipelines mit dem Vanilla-Shader und meldet einen Fehler. Fehlt die API, wird einmal gewarnt.
  `IrisCompat.renderingShadowPass()` (von drone-render und `FpvProps` genutzt) — true, solange Iris den Schattenpass
  extrahiert und zeichnet (`IrisApi#isRenderingShadowPass`, jede Abfrage live; bei einem Reflection-Fehler einmal gewarnt
  und dauerhaft false). Regel: im Schattenpass keine kamerabezogene und keine reine Licht-Geometrie
  (`docs/design/shader-packs.md`).
- `compat.lambdynamiclights` → **drone** (LED-Streifen): kompiliert gegen die LambDynamicLights-API (`compileOnly`, siehe
  „LED-Streifen und FPV-Scheinwerfer“). `LambDynamicLightsCompat.present()` ist ohne API-Bezug und darf überall
  aufgerufen werden; `PropwashDynamicLights`/`DroneLedLuminance` lädt nur LambDynamicLights über seinen Entrypoint.

### Konfigurator-Tabs (ui, `ui/client/config`)
- **Lebenszyklus:** Der Screen erzeugt beim Öffnen alle Tabs neu (`ConfigTabs.create()`), hängt aber nur den aktiven an
  (`attach` → `build`, bei `host.rebuild()` erneut). Beim Schließen ruft er für **alle** Tabs — auch nie angezeigte —
  `save()` („Speichern & schließen“, Strg+S) bzw. `discard()` („Verwerfen“, ESC ohne Änderungen). Beide werden
  gekapselt aufgerufen; eine Ausnahme markiert nur den Tab als fehlerhaft. Der gelbe Fuß-Button heißt nur bei offenen
  Änderungen „Speichern & schließen“; ohne Änderungen heißt er „Schließen“ und schließt wie ESC (`discard()`).
- **Absturz eines Tabs:** Jeder Aufruf in einen Tab (Zeichnen, Eingabe, Fußzeilen-Abfragen, Titel, Id) läuft gekapselt.
  Eine Ausnahme kommt einmal mit Stack ins Log („Konfigurator-Tab … ist fehlgeschlagen“), der Tab zeichnet nicht mehr
  und zeigt statt seines Inhalts ein Fehlerfeld (`propwash.ui.tab_error`, Kurzfassung der Ausnahme, voller Text als
  Tooltip, Hinweis `propwash.ui.tab_error.copy`). Ein Klick darauf kopiert `ui.TabErrorReport` (Propwash-Version, Loader,
  Tab-Id, Ausnahme mit höchstens 24 Stack-Zeilen und 4 Ursachen, Ursachen-Schleifen sicher) in die Zwischenablage und
  meldet `propwash.ui.tab_error.copied` in der Fußzeile. `ConfiguratorScreen#tabErrors()`/`tabReport(id)` lesen das für
  Selbsttests.
- **Bearbeiten:** Ein Tab bearbeitet eine **Kopie** seiner Config (angelegt im Konstruktor oder im ersten `build()`) und
  schreibt sie erst in `save()` zurück. Wer Werte bewusst live anwenden will (z. B. Lautstärke hören, Drohne sofort
  sehen), merkt sich den Ausgangszustand und stellt ihn in `discard()` wieder her. `save()`/`discard()` müssen auch
  ohne vorheriges `build()` funktionieren. Jede Änderung meldet `host.changed()` (Fußzeile „ungespeichert“, ESC fragt).
- **Reihenfolge der Seitenleiste** (`order()`, eindeutig; Gleichstand → nach `id()`): Übersicht 100, Flugschule 110, Konfiguration 200,
  PID-Tuning 300, Rates 310, Empfänger 400, Modi 410, Video 500, Videokanal 505, OSD 510, LED-Streifen 520, Action-Cam 530, Rennen 600, Freestyle 610, GPS Rescue 700, Simulation 800,
  Darstellung 810, Audio 820, Server 9000. Neue Tabs wählen einen freien Wert in der passenden Gruppe (Standard 1000).
- **Eingabe:** Klicks gehen zuerst an die Widgets, dann an `mouseClicked` des Tabs; trifft der Klick kein Widget, verliert
  das zuletzt fokussierte Widget den Fokus. Tasten gehen an das fokussierte Widget, ohne Fokus zuerst an `keyPressed` des
  Tabs (eigene Auswahl in Vorschau/Liste). Das Mausrad erreicht ein Vanilla-Widget nur, wenn es fokussiert ist; sonst
  bekommt es `mouseScrolled` des Tabs.
- **Nur-Anzeige-Tabs:** `readOnly()` (Standard false) blendet „Standardwerte“ aus und zeigt in der Fußzeile „Nur Anzeige“
  statt des Speicherorts (Übersicht, Server). `contentAbove()`/`contentBelow()` melden abgeschnittenen Inhalt; `UiTab`
  beantwortet sie über sein `ScrollPanel`, der Screen deckt dann die äußersten 4 px ab und blendet 8 px darüber bzw. darunter weich aus.
- **Bildlauf ohne Zutun (seit 0.5.0):** Ein Tab, der `scrollsItself()` nicht überschreibt, wird vom Screen verschoben,
  sobald sein Inhalt höher ist als die Ansicht (`ui.client.widget.ContentScroll`: Scrollleiste, Mausrad, Bild auf/ab,
  Pos1/Ende, Fokus rückt in die Ansicht). Höhe = unterste Widget-Kante und `reportBottom(y)`; der Tab rechnet weiter in
  den Koordinaten aus `build()`, `host.contentShift()` nennt den Versatz. Der Status der Fußzeile bricht um, die
  Kopfzeile bekommt bei Bedarf eine zweite Zeile, die Seitenleiste scrollt. Einzelheiten und der Selbsttest
  `ui-scale:` stehen in `docs/design/ui-scaling.md`.
- **Speicherort in der Fußzeile:** `storage()` = `DRONE` (in der Drohne), `GAME` (Spieleinstellung) oder `MIXED`
  (`propwash.ui.footer.mixed`: Tab „Konfiguration“, dessen Drohnenname in der Drohne liegt, der Rest ist Spieleinstellung).
- **Ganze Zeilen statt halber:** Ein `UiTab` kann in `build()` mit `ScrollPanel#stop(contentY)` saubere Schnittstellen
  (Zeilen- und Abschnittsanfänge) melden. Dann rastet der Bildlauf auf diese Stellen ein (Mausrad = eine Zeile), der Screen
  schneidet unten an der letzten ganz sichtbaren Zeile ab (`ConfigTab#rowSnapped()`/`clipBottom()`), zeigt dort eine
  Trennlinie mit „Weiter unten mehr“ und blendet nicht aus. Nutzen die Tabs „Tasten“ und „PID-Tuning“ (Halt an jedem
  Abschnitt und jeder Reglerzeile); die Kartenliste der Flugschule
  (`guide.client.screen.SceneList`) rastet genauso auf ganze Kartenreihen ein, die Übersicht passt ihre Fensterhöhe an.
- **Regler:** Zahlenwerte in Tabs nutzen `ui.client.config.StepSlider(x, y, w, h, min, max, step, initial, message,
  onChange)` statt eigener `AbstractSliderButton`-Kopien. Der Wert rastet relativ zu `min` auf `step` ein, `message`
  baut die komplette Beschriftung aus dem Wert, `onChange` kommt nur bei echter Wertänderung (dort `host.changed()`
  melden). `current()` liest, `setCurrent(v)` setzt ohne Rückruf, `select(v)` setzt mit Rückruf; die Pfeiltasten
  verstellen um genau einen `step`; `StepSlider.snap(...)` ist die Rundungsregel.
  Seit 0.5.0 ist jeder `StepSlider` tippbar wie die Regler in PID, Welt-Regeln und LED (gemeinsame Eingabe
  `ui.client.widget.SliderTyping`, Bedienung in `docs/design/config-input.md`). Getippt wird in der Einheit der
  Beschriftung: zeigt sie nicht den Rohwert, sagt der Tab das mit `percent()` (Rohwert 0–1, Anzeige in Prozent),
  `display(faktor, versatz)` (Anzeige = Rohwert × Faktor + Versatz, z. B. OSD-Stufen) und bei Bedarf
  `typedRange(min, max)` (tippbarer Rohbereich, z. B. ohne die Stufe „erbt“) oder `percentRange()` (Hinweis mit %).
  `untyped()` schaltet die Eingabe ab; das braucht kein Regler in Propwash. Der Selbsttest
  `ui: Regler tippen – jeder Zahlenwert in allen Tabs` prüft, dass die getippte Zahl genau so in der Beschriftung
  erscheint, und meldet jeden Regler ohne Texteingabe.

### Theme (ui, `ui.client.theme`)
- **Ein Look für alle Propwash-Screens:** `Palette` (Farben, immer mit Alpha), `Metrics` (Abstände, Zeilen- und
  Steuerhöhen, Spalten), `ThemeText` (Text, Kürzen, Kleinschrift, Hinweis), `ThemeDraw` (Fenster, Panel, Kopf-/Fußleiste,
  Abschnittstitel, Trenner, Button-Flächen, Feld, Regler, Schalter, Checkbox, Scrollbar, Wertbalken, Tooltip, LED) und
  `ThemedWidgets` (komplette Widgets mit Beschriftungsspalte). Keine eigenen Farbkonstanten in Modulen; Statusfarben
  `SUCCESS`/`WARNING`/`ERROR`/`INFO`, Achsenfarben über `Palette.axisColor`. `INFO` (Blau) ist nur Akzentstreifen von Toasts
  und Achsenfarbe; Hinweis- und Statustexte in Screens stehen in `ACCENT` (Info, z. B. Lackier-Status, Werkbank-Hinweise) oder
  `TEXT_MUTED`.
- **Tooltips nur null-sicher:** Propwash zeichnet Tooltips nur über `ThemeText.tooltip` (eine Zeile), `tooltipLines`
  (Zeilen-Liste), `tooltipWrapped` (umbrochen auf eine Breite) und `tooltipSequences` (fertig umbrochene Zeilen); Widgets
  bekommen ihren Tooltip über `ThemeText.tip(text)`. Fehlender oder leerer Text ergibt keinen Tooltip, `null`-Zeilen
  fallen weg – ein `null` aus einer Statusabfrage kann so keinen Tab mehr abstürzen lassen. JUnit `TooltipCallsTest`
  verbietet direkte `setTooltipForNextFrame`/`setComponentTooltipForNextFrame`-Aufrufe außerhalb von `ThemeText`.
  Gekürzte Texte zeigen ihren vollen Text als Tooltip (`ThemeText.cut(voll, gezeigt)`), z. B. die Statuszeile des
  Empfänger-Tabs (umbrochen auf 220 px).
- **Propwash-Kopf:** `ThemeDraw.brandedHeader(g, font, x0, y0, x1, höhe, titel, titelRechts)` zeichnet die Kopfleiste aller
  Propwash-Fenster (Fläche `HEADER`, „PROPWASH FPV“, Trennstrich, Titel, Amber-Linie) und liefert das Titelende;
  `ThemeDraw.headerSubtitle(…)` hängt mit einem weiteren Trennstrich einen Untertitel in `TEXT_FAINT` an (Lackierung:
  „Lackierung | 5″-Freestyle-Rahmen“). Werkbank, Ladegerät und Wartungsmenü zeichnen ihn mit Höhe 14 über die Titelleiste ihrer
  Textur (`x + 2, y + 2`), statt den Titel als Vanilla-Beschriftung zu zeigen.
- **Deaktivierte Buttons** sehen überall gleich aus: 1 px Rahmen `BORDER_STRONG` (Primär: `ACCENT_DIM`), Fläche
  `DISABLED`, Text `TEXT_DISABLED` – gilt für `FlatButton` und die gethemten Vanilla-Buttons. Zurücksetzen-Buttons sind
  nur aktiv, wenn es etwas zurückzusetzen gibt (Tasten „↺“ – dort ohne Änderung gar nicht gezeichnet, nur ein blasses „—“ über
  `FlatButton#placeholderWhenInactive`, ohne Tooltip –, PID „Auf abgeleitet“, Flugschule „Ansicht zurücksetzen“,
  OSD „Zurücksetzen“ nur bei geänderter Vorlage oder eigenen Elementfarben/-größen, OSD-Stil nur bei geändertem Stil).
- **Drohnen-Vorschau:** `ThemeDraw.previewStage(x0, y0, x1, y1)` zeichnet dieselbe Bühne wie die Menü-Texturen (Verlauf,
  Bodenraster ab 2/3 Höhe, eingelassener Rand); Konfigurator-Vorschauen nutzen sie statt eines leeren Felds. Werkbank und
  Wartungsmenü zeichnen sie über das Vorschaufeld ihrer Textur, damit die schrägen Bodenlinien überall gleich sauber sind
  (die Textur zeichnet sie als durchgehende Linien, nur als Rückfall).
- **Tooltips von Propwash-Items:** `PropwashRegistries.item/blockItem` geben jedem Item den Standard-Component
  `minecraft:tooltip_style = propwash:propwash` (`PropwashRegistries.TOOLTIP_STYLE`). `TooltipRenderUtilMixin` zeichnet
  diesen Stil in **jedem** Screen mit `ThemeDraw.tooltipFrame`; die Sprites `gui/sprites/tooltip/propwash_*` sind der
  gleich aussehende Rückfall. Tooltips anderer Items bleiben außerhalb von `ThemedScreen` Vanilla.
- **`GuiLayerAccess`** (Mixin `mixin.ui.client.GuiGraphicsExtractorMixin`): `((GuiLayerAccess) graphics).propwash$renderState()`
  liefert den `GuiRenderState` für eigene `GuiElementRenderState`s, `propwash$scissor()` das aktuelle Scissor-Rechteck
  (gleiche Regeln wie Vanilla). Vorher immer mit `instanceof` prüfen und sonst normal zeichnen.
- **OSD-Deckkraft:** Das OSD zeichnet Text und Linien als eine Ebene je Element (`osd.client.OsdQuads` über
  `GuiLayerAccess`): Text aus den Glyphenmasken der Standardschrift (`osd.client.OsdGlyphs`, liest die Bitmap-Provider
  aus `font/default.json` samt Ressourcenpaketen), Kontur und Füllung überlappungsfrei aus `osd.OsdCoverage`. Dadurch
  wirkt die Deckkraft wirklich halbtransparent (Himmel/Gras scheinen durch Text und Kontur; Standard 60 %, die Kontur
  erreicht ihre volle Stärke ab 75 %). Fehlt eine Glyphe
  (Unicode-Schrift erzwungen, TTF-Pakete) oder weicht die Breite von `Font#width` ab, zeichnet `OsdDraw` diesen Text
  wie bisher mit Textkopien der Spielschrift und merkt sich das (Debug-Log einmal je Text).
  - **Glyphentabelle:** `osd.client.OsdFonts` baut sie nur im Reload-Listener `propwash:osd_glyphs` (Hintergrund-Thread
    des Ressourcen-Ladens) oder, wenn sich „Unicode-Schrift erzwingen“/„Japanische Glyphen“ ändert, einmal auf
    `Util.backgroundExecutor()`; bis sie fertig ist, zeichnet das OSD mit der Spielschrift. Eine Breiten-Abweichung lädt
    **nie** neu (bis 0.4.3 lud jede Abweichung die Tabelle alle 5 s im Zeichen-Thread neu: 340–370-ms-Hänger bei
    Schrift-Ressourcenpaketen).
  - **Konturen-Cache:** `osd.client.OsdTextCache` gilt für alle OSD-Zeichner gemeinsam über die Frames hinweg (Text je
    Text/Kontur/Kantenbreite, Linien je Rechteck-Satz) und wird nur bei einer neuen Glyphentabelle geleert. Je Frame
    entstehen höchstens `FRAME_BUDGET` (16) neue Textkonturen, weitere Texte laufen in diesem Frame über die
    Spielschrift. `OsdQuads` teilt sich die fertigen Quad-Arrays des Caches, die Pose-Kopie wird bei gleicher Pose
    wiederverwendet.
- **`ThemedScreen`:** Jeder Propwash-Screen implementiert das Marker-Interface und ruft pro Frame
  `ThemeStats.frame(this)` (Konfigurator, Empfänger-Assistent, Werkbank). Nur wenn der aktuelle Screen `ThemedScreen`
  ist, zeichnen die ui-Mixins (`propwash.ui.mixins.json`: `Button.Plain`, `CycleButton`, `AbstractSliderButton`,
  `Checkbox`, `EditBox`, Tooltip-Hintergrund ohne eigenen Stil) die Vanilla-Widgets im Theme; Trefferflächen, Fokus,
  Tastatur und Narration bleiben Vanilla. Andere Screens (Vanilla, andere Mods) bleiben unberührt.
  Die Config steht in `fabric.mod.json` und `neoforge.mods.toml` jeweils als letzter Eintrag; fehlt sie, laufen alle
  Screens ungethemt und der Konfigurator-Selbsttest schlägt fehl.
- **Beschriftungsspalte:** `CycleButton` mit Name und Wert sowie Regler mit Text „Name: Wert“ zeigen links die
  Beschriftung (`Metrics.labelWidth`) und rechts das Bedienelement; bei Reglern greift die Maus nur auf der Spur.
  Schmale Regler (OSD-Tab, drei Spalten) melden über `LabeledValue` eine knappe Spalte (Textbreite + `LABEL_GAP`), solange
  die Spur mindestens `Metrics.CONTROL_MIN` breit bleibt; erst darunter steht „Name Wert“ gekürzt in der Spur.
  Ein/Aus-`CycleButton`s werden als Schalter gezeichnet.
- **Regler-Wert immer mittig:** Der Wert steht auf jedem Regler mittig in der Spur (`ThemedWidgets.sliderValueX(x0, x1, breite)`),
  egal wo der Griff steht, damit die Wertespalten ruhig bleiben. Liegt der Griff unter dem Text (`ThemedWidgets.handleCover`),
  steht der Wert mit dunkler Kontur darüber (`ThemeText.outlined`, `Palette.SHADOW`), so bleibt z. B. das „%“ lesbar
  (JUnit `SliderValueTest`). Die OSD-Regler einer Zeile (Deckkraft, Größe, Linien-Deckkraft) teilen sich eine
  Beschriftungsspalte in der Breite der längsten Beschriftung (`OsdSlider.shareLabelColumn`), ihre Spuren sind gleich breit.
- **Auswahl in Listen:** `ThemeDraw.selection` hellt die Zeile auf (`Palette.SELECTION`) und setzt eine 3 px Akzentkante links,
  wie im Modi-Tab; Nebentexte auf der gewählten Zeile stehen eine Stufe heller.
- **Umbruch ohne Einzelwort:** `ThemeText.balanced(font, text, width)` bricht wie `Font#split` auf die volle Breite. Nur wenn die
  letzte Zeile aus einem einzelnen Wort besteht oder eine Folgezeile mit einem Gedankenstrich (– oder —) beginnt, sucht es
  schrittweise eine bis zu 25 % schmalere Aufteilung mit gleich vielen Zeilen ohne diese Fehler; sonst bleibt der volle Umbruch.
  So laufen Absätze in allen Tabs gleich breit (Flugschul-Karten und -Schritte, Hinweise, `ThemeText.wrapped`, Modi-Tab,
  Server-Tab). Feste Zeilenwechsel (`\n` im Sprachtext) bleiben erhalten. Die Kartenuntertitel der Flugschule nutzen `Font#split`.
- **Lampen:** `ThemeDraw.lamp(x, y, w, h, farbe)` zeichnet eine Anzeige-Lampe wie die Schachtlampen des Ladegeräts
  (Fassung `BUTTON`, aus = `FIELD`); Anzeigen, die man nicht anklicken kann, nutzen Lampen statt Checkbox-artiger LEDs.
  `ThemeDraw.led(x, y, an)` ist die runde Zeilen-Lampe (`Metrics.LED_SIZE` = 7 px, auf eine Textzeile ab `y` zentriert):
  heller Ring `Palette.LED_BEZEL`, aus = dunkelgrün `LED_OFF` mit Glanzpunkt, an = `SUCCESS` mit hellem Glanzpunkt und
  schwachem Schein. Sie bleibt auch in markierten Zeilen sichtbar; genutzt von Modi-Tab und Empfänger-Assistent.
- **Meldungen:** `ThemeDraw.notice(x0, y0, x1, y1, akzent, alpha)` ist die gemeinsame Meldungsfläche (Panel, Rand
  `BORDER_STRONG`, farbiger Streifen links) für Rennmeldungen (`race.client.RaceHud`) und Toasts. Im FPV zeichnet `RaceHud` seine
  Meldungen nach `nextStratum()` ohne Fläche im OSD-Stil (Standardschrift mit schwarzer Kontur), damit Videoeffekte sie nie
  überdecken; Rennzeiten dort mit zwei Nachkommastellen wie `TIME` im OSD, Teile mit „ · “ getrennt.
  `ui.client.theme.ThemedToast.show(toastManager, token, titel, text, akzent, millis)` zeigt einen Toast im Propwash-Look
  (Vanilla-Toast-System, also auch über geöffneten Screens; gleicher Token ersetzt die Meldung; 6 px Abstand zum oberen und
  rechten Bildrand, eingerechnet in `width()`/`height()`). Propwash nutzt keine Vanilla-`SystemToast`s und keine
  Actionbar-Meldungen vom Client: `PilotSession` (Scharfschalten verweigert, Flugmodus, Aufrichten, Failsafe, keine Funke,
  Token `PilotSession.TOAST_TOKEN`), `RescueController`, `PilotGuard`, `TemplateApplier` und der Suchpiepser melden per
  `ThemedToast` (Akzent `ERROR`/`SUCCESS`/`INFO`/`WARNING`); Funke/Gamepad verbunden (Akzent `SUCCESS`) bzw. getrennt (`WARNING`) meldet
  `input.client.TransmitterInput.showDeviceToast(gerät, verbunden)` mit Token `TransmitterInput.TOAST_TOKEN`: Titel je Art
  („Funke/Gamepad/Controller verbunden|getrennt“, `deviceToastTitle`), beim Verbinden mit „(Profil geladen)“ bzw. „(noch nicht
  eingerichtet)“ hinter dem Namen (`deviceToastMessage`). Die Hinweise der Geräte-Einrichtung (unten „Neue Funke“) nutzen
  denselben Token und ersetzen die Verbindungsmeldung.
- **Listen-Zeilen:** Tabellenartige Listen (Server-Tab, Tasten-Tab) setzen die Beschriftung `Metrics.LABEL_GAP` vom
  Zeilenrand ein, in `TEXT_MUTED`; Werte bzw. Bedienelemente stehen rechts.
- **Symbol-Kacheln:** `ThemeDraw.iconTile(x0, y0, x1, y1)` ist eine hellere Kachel (`Palette.ICON_TILE_*`) für Item-Symbole
  auf dunklen Karten (Flugschul-Karten), damit auch fast schwarze Items (Funke, Kamera) lesbar bleiben.
- **Tab-Layout:** Tabs beginnen mit `ThemeDraw.sectionTitle` (Höhe `Metrics.SECTION_HEIGHT`), Bedienelemente sind
  `Metrics.CONTROL_HEIGHT` hoch im Raster `Metrics.ROW_HEIGHT`, Spalten über `Metrics.columns/columnWidth` (max.
  `Metrics.COLUMN_MAX`), linksbündig ab `x`. Eigene Widgets zeichnen mit `ThemeDraw`/`ThemedWidgets` statt Sprites.

- **Nachtrag Review-Runde (UI):**
  - Markierte Listenzeilen sehen überall gleich aus: `ThemeDraw.selection` = leicht aufgehellte Zeile (`Palette.SELECTION`, weiß
    mit wenig Deckkraft) plus 2 px `ACCENT`-Streifen links, Hover nur `HOVER_SOFT`; Nebentexte in markierten Zeilen eine Stufe
    heller (Aufnahmen-Liste).
  - Einheitlicher rechter Rand im Konfigurator: Tabs außer `UiTab` bekommen `Metrics.CONTENT_INSET` (6 px) weniger Breite,
    `UiTab`/`ScrollPanel#innerWidth` ziehen denselben Wert für die Scrollleiste ab.
  - Formate: Multiplikatoren mit „×“, Prozent ohne Leerzeichen im Englischen („60%“), deutsch mit („60 %“), typografische
    Anführungszeichen (en “…”, de „…“), Auslassung ohne Leerzeichen („…“), Titel und Tabs im Satzbau („PID tuning“,
    „GPS rescue“, „Flight school“). Große Zahlen in der Übersicht mit Tausendertrennzeichen je Sprache; Teilenamen dort ohne
    wiederholtes Kategoriewort (`propwash.ui.overview.part_word.<kategorie>`).
  - Server-Tab: je Thema eine Zeile („Distance limit: none (everyone)“, „Generate new chunks: yes (permission level 2+)“,
    `ServerRulesText.distanceLimit`), Reichweite der Fernsteuerung als „Remote range“. „Radio“ meint im Englischen nur die echte
    USB-Funke, das Item und seine Reichweite heißen „remote“.
  - OSD-Flugzeit `FLY` zählt scharfe Echtzeit mit Lücken bis 2 s (`OsdSampler`, wie die Rennuhr, die im Pausenmenü steht) statt
    gekappter Frame-Zeit; bei pausiertem Spiel hält `OsdSampler#hold` die Zeit an. Ein Rennen bricht ab, wenn die Drohne mehr als
    `LapTracker.LEAVE_DISTANCE` (96 Blöcke) von jedem Gate entfernt ist (`AbortReason.LEFT_COURSE`).
  - Ladegerät: vier Schächte über die volle Inventarbreite (`BatteryChargerMenu.SLOT_X` 17/59/101/143), leerer Schacht zeigt „—“,
    Prozent in normaler Schrift, Redstone-Zeile mit Redstone-Symbol (abgedunkelt ohne Signal). Wartungsmenü: Layout in `docs/ASSETS.md`
    („GUI Ladegerät/Wartung“).
  - Werkbank: Drehrichtungspfeile mit Radius 19 und 64° Bogen, frei von den Slot-Rahmen; Legende „Rotation“ in normaler Schrift.
  - Flugschule: `SceneFraming` verkleinert Übersichtseinstellungen (Zoom ≤ 1) so weit, dass die Bühne (gesetzte Blöcke) mit 10 %
    Rand ins Bild passt (höchstens auf 70 %); der Bedienhinweis steht in normaler Schrift auf einer festen Fläche.
  - Assistent: Fußleiste links „Zurück/Zurücksetzen“, rechts gruppiert „Abbrechen“ + „Weiter“.

### Flugschule (guide, `de.lardtom.propwash.guide`)
- **Szenen** sind reines Java (`guide.scene`, ohne Minecraft-Klassen) und werden per DSL gebaut:
  `SceneBuilder.scene(id).icon(itemId).items(ids…).anyPart().view(radius).camera(target, yaw, pitch, zoom)
  .step(name, dauerTicks, s -> s.at(t).drop/pop/block/remove(…) .spawn/move/turn/despawn(…) .attach(drone, DronePart, von, dauer, versatz)
  .arm/throttle/equip/crouch/look/light(…) .camera(…) .callout(name, Anchor, Side, dauer) .highlight/highlightBlock(…)
  .keys/keysAt(name, …, KeyToken…)).build()`. Dauer 0 bei Hinweisen = bis Schrittende; ein Cue darf nicht nach seinem
  Schritt enden (Builder wirft). Hinweise eines Schritts blenden zum Schrittende aus; die bis zum Schluss offenen Hinweise
  des letzten Schritts sind das Schlussbild und bleiben bei „Fertig“ ohne Ausblenden stehen. Ein neuerer Hinweis am selben
  Anker (gleicher Akteur bzw. beide fest, Versatz < 0,5 Blöcke, `Scene.SAME_ANCHOR`) **und** auf derselben Seite löst den
  älteren ab: der ältere endet beim Start des neueren und blendet davor aus (`Scene#supersedes`, `Scene#cueEnd`). Zwei
  Hinweise auf verschiedenen Seiten desselben Objekts bleiben gemeinsam stehen. Akteure: `ActorSpec.Drone(build, zusammengebaut[, maßstab])` (Position = Aufsetzpunkt; Maßstab
  Standard 1, `ActorSpec.Drone.STAGE_SCALE` = 1,6 vergrößert die Drohne in Szenen mit Totale, damit sie lesbar bleibt; Teile fliegen
  per `attach` einzeln ein), `Pilot(kopfItem, handItem)` (Skin des Spielers), `Gate(size[, lage, umgedreht])` (Lage als
  Schlüssel von `GateMount`, Standard `standing`; Position = Ursprung von `GateGeometry`), `Item(id, scale, spin)`,
  `Block(BlockSpec)`. Blöcke als `BlockSpec(id, eigenschaften)`, Tasten als `KeyToken.mapping("key.sneak")`/`mouse("right")`
  (zeigt die echte Belegung).
- **Richtungen:** Drohnen-`yaw` ist der Kompasskurs der Nase (0 = Norden/−Z, 90 = Osten/+X), `pitch` positiv = Nase hoch,
  `roll` positiv = rechte Seite runter. Pilot und Gate nutzen den Minecraft-Yaw (0 = Blick bzw. Durchflug nach Süden/+Z,
  90 = Westen). `Item`- und `Block`-Akteure kippen um `pitch` (positiv = Oberseite nach vorn/−Z bei Yaw 0) und
  `roll` (positiv = Oberseite nach +X), Drehpunkt ist der Fußpunkt (so werden `minecraft:end_rod`-Knüppel zu Gimbals).
  Kamera-`yaw` ist der Umlaufwinkel (0 = Kamera auf +Z, Blick nach Norden, +X rechts).
- **Weitere Cues:** `turn(…, ease)` und `look(…, ease)` mit eigener Kurve (`Ease.LINEAR` für abgetastete Bahnen),
  `detach(drone, DronePart, nach, dauer)` baut ein Teil ab (fliegt zum Versatz, danach unsichtbar; `attach` setzt es wieder an).
  Eine `turn`-Dauer 0 setzt den Winkel sofort (z. B. Roll 360 → 0 nach einem Flip).
- **Szenen-Baukasten** `guide.scenes.SceneKit`: Item-/Tasten-/Block-Konstanten, `ground(…)` (Plattform mit Welle),
  `pilotYaw`/`headPitch`/`heading` (Blickrichtungen), `follow(…)` (Pilot blickt einer Flugbahn nach, Sichtflug) und
  `fly(…)` (Bahn samt Kurs, Vorneigung und Schräglage in Kurven, abgetastet mit `SceneEvaluator.path`).
- **Szenen (Reihenfolge der Übersicht):** `drone_build` (Werkbank, Werte und Warnungen), `bind` (Funke binden, Brille aufsetzen),
  `fpv_los` (FPV und Sichtflug), `battery` (Akkutausch, Kompatibilität, Ladegerät), `race` (Gates, Kurs-Werkzeug, Runde),
  `video` (analog/digital/Kreativ, passende Brille), `action_cam` (Action-Cam anbauen, aufnehmen, Schalter oder Taster, Aufnahmen-Liste), `gripper` (Greifer anbauen, Items aufnehmen, abwerfen, Landen und Abstürze), `configurator` (Knüppel, Rates, PID, Flugmodi, Schalter), `rescue`
  (Homepoint, Heimflug, Failsafe), `hold` (ALTHOLD und POSHOLD: Barometer, Steigen/Sinken, GPS, Wind, Kompassfehler,
  kein GPS unter Blöcken, Grenzen und Tuning), `recovery` (Absturz, Aufrichten, Reichweite, Suchpiepser), `repair` (Verschleiß nach
  Abstürzen, gebrochene Props, Feldtausch, alte Props reparieren, Werkbank und Recycling), `survival` (Herstellen,
  Rezeptbuch, Truhen, erste Werkstatt). Jedes Propwash-Item öffnet mindestens eine Szene (JUnit `FlightSchoolScenesTest`).
- **Prüfungen:** `FlightSchoolScenesTest` (Reihenfolge, Item-Zuordnung, Textlängen: Titel ≤ 38, Beschreibung ≤ 64,
  Schritttext ≤ 150, Hinweis ≤ 90, Tastenhinweis ≤ 28 Zeichen; jeder Hinweis-Anker und jede Drohne bleibt in drei
  Ansichtsgrößen im Bild), `FlightSchoolStoriesTest` (Handlung je Szene, z. B. Durchflug jedes Gates in Flugrichtung und
  wanderndes Grün); Selbsttest „guide: alle Szenen Schritt für Schritt abspielen“ (je Schritt drei Stellen: gezeichnet,
  Hinweise vollständig, Anker und Drohnen in der Ansicht, Ende erreicht). Hinweis-Layout: `CalloutLayoutTest` (stehen
  bleiben, gleiten statt springen, kein Seiten-Pendeln, keine Überlappung, Rand, Tastenhinweise, Wiederholbarkeit),
  `CalloutRulesTest` (Ablösen am selben Anker, ruhiges Schlussbild mit mindestens einem Hinweis in jeder Lektion, nie zwei
  Hinweise für dieselbe Stelle)
  und `LessonLabelsTest` (jede Lektion Tick für Tick in vier Ansichtsgrößen, ruhig und gedreht/gezoomt: keine Überlappung,
  kein verdeckter Anker, nichts außerhalb, kein Sprung, höchstens sechs Umzüge je Hinweis); Selbsttests „guide: Hinweise
  bleiben beim Drehen und Zoomen stehen“ und „guide: Hinweise ruhig in allen Lektionen (abspielen, drehen, zoomen)“ (jede
  angemeldete Lektion, auch von Addons, mit 4-facher Geschwindigkeit bei ständig drehender und zoomender Ansicht).
- **Texte** nur über Sprachdateien: `propwash.guide.scene.<id>.title|description`, `.step.<schritt>.title|text`,
  `.callout.<name>`, `.hint.<name>` (`Scene#translationKeys()` listet alle; JUnit prüft beide Sprachen).
- **Anmelden:** `GuideScenes.register(Supplier<Scene>)` in der eigenen Common- oder Client-`init()` (vor dem ersten Öffnen);
  `GuideScenes.forItem(itemId, istBauteil[, teilArt])` ordnet Items zu (`anyPart()` = alle Items mit `propwash:part`).
  Zusätzlich Verknüpfungen (API 1.3, `docs/design/parts-api.md` §12.3): `GuideScenes.linkItems(szene, ids)` und
  `linkParts(szene, namensraum, arten)`; eingebaut gilt `battery`/`video`/`configurator`/`repair` für Akkus, Videos, Stacks
  bzw. Props **jedes** Namensraums. Verknüpfungen wirken auch für später angemeldete Szenen und respektieren die Sichtbarkeit.
- **Öffnen:** Taste `key.propwash.flight_school` (Standard M) über einem Item mit Szene (jeder Namensraum, auch Addon-Teile) in jedem Container-/Kreativ-Screen
  gedrückt halten (Mixin `mixin.guide.client.AbstractContainerScreenMixin` am Anfang von `extractTooltip`, damit auch Rezeptbuch-Screens erfasst werden; `propwash.guide.mixins.json` vor ui).
  Der Hinweis steht als letzte Zeile im Tooltip („[M] Flugschule“, `HoldToOpen.withHint` per `@ModifyExpressionValue` auf den Aufruf
  `getTooltipFromContainerItem` in `extractTooltip` – NeoForge ruft dort eine andere `setTooltipForNextFrame`-Variante auf –,
  gilt auch für den Kreativ-Screen) und füllt sich beim Halten von links
  nach rechts in Amber; er verdeckt also nie andere Widgets. Nur ohne Slot-Tooltip (Selbsttest) zeichnet `HoldToOpen` eine Pille unter der Mitte,
  in der Welt drücken öffnet die Übersicht (im Flug oder mit gewählter Lektion das Flugtraining), Konfigurator-Tab `flight_school` (Order 110). Gehört das Item zu genau einer
  Szene, öffnet das Halten sie direkt, sonst die auf das Item gefilterte Übersicht (z. B. Werkbank: „Drohne bauen“ und
  „Überlebensmodus“; `GuideOpener.openFor`).
  `guide.client.GuideOpener.openScene(scene, parent)` / `openIndex(parent, filterStack)` für andere Module.
- **Rendern:** ein Bild-im-Bild-Renderer für die ganze Szene (§8 `pictureInPictureRenderer`, `docs/api/gui-scene.md`);
  Drohne über die öffentliche API von drone-render (`DroneVisuals.fillPreview/submit`, `DroneRenderResources`), Gates über
  `GateRenderer`, Pilot über `AvatarRenderState`.
- **Funke als Akteur:** `ActorSpec.Remote(maßstab)` zeichnet die echte Funke (`propwash:remote` im Kontext `NONE`, also Gehäuse
  plus `propwash:remote_gimbals`), Position = Mitte der Unterkante, `turn` kippt sie (Pitch positiv = Oberseite nach hinten).
  Die Knüppel stellt der Cue `sticks(actor, linksX, linksY, rechtsX, rechtsY, dauer)` (−1…1, Mode 2: links Gas/Gier, rechts
  Nick/Roll, Y positiv = nach vorn/oben; Standard links unten). Die Stellung erreicht den Special-Renderer über
  `RemoteGimbalRenderer.extractPosed(stack, pose, extract)` (drone); sonst liest er wie bisher die Funke in der Haupthand.
  `ConfiguratorScene.radioPoint/stickTip` rechnen Punkte auf der gekippten Funke für Hinweis-Anker aus.
- **Hinweis-Layout:** `SceneView.screenBoxes(state, projection)` liefert die projizierten Kästen aller Akteure (Drohne mit
  Propellerkreis, Gate, Pilot, Item, Funke, Block-Akteur) und aller Blöcke ab y ≥ 1 (`ScreenBox`). Die Lage der Kästen
  rechnet `guide.layout.CalloutLayout` (reines Java, eine Instanz je `SceneScreen`) mit Gedächtnis über die Bilder:
  - **Einmal setzen:** Erscheint ein Hinweis, wählt die Kostenfunktion aus Seiten, „außerhalb des eigenen Objekts“ und
    Verschiebungen die günstigste Stelle (andere Hinweise und Tastenhinweise sind tabu, der eigene Anker ± 4 px auch, die
    Punkte anderer Hinweise fast ebenso; das eigene Objekt – der kleinste Kasten, der den Anker enthält – kostet sehr viel
    (4000 je vollständig bedeckter Fläche), andere Objekte wenig, dazu Seitenrang, Länge der Zeigelinie, Zeigelinien quer
    durch andere Hinweise, weniger als 14 px Abstand zum eigenen Punkt (15 je px) und ein Kasten, der mehr als 12 px näher
    an einem fremden Punkt steht als am eigenen (500)). Sind alle 24 Standard-Stellen belegt, sucht ein Raster (6 px) die
    beste freie Stelle in der Ansicht. Ältere Hinweise behalten ihren Platz, neue weichen ihnen aus; ein gerade gleitender
    Kasten ist nur ein weiches Hindernis (2000).
  - **Stehen bleiben:** Danach bleibt der Kasten in Bildschirm-Koordinaten stehen, auch wenn Kamera, Bildausschnitt oder
    der Nutzer die Szene drehen und zoomen. Nur die Zeigelinie folgt jedes Bild genau dem projizierten Anker.
  - **Nur wenn nötig umziehen:** sofort, wenn der Kasten seinen Anker verdeckt, einen älteren Hinweis oder einen
    Tastenhinweis überlappt oder aus der Ansicht ragt; nach höchstens 4 Ticks Wartezeit, wenn er auf dem Punkt eines
    anderen Hinweises liegt, mehr als 12 px näher an einem fremden Punkt steht als am eigenen oder der Anker in den nächsten
    8 Ticks in ihn hineinläuft; nach 12 Ticks, wenn die Zeigelinie um mehr als
    `max(48 px × Textskala, 16 % der kleineren Ansichtsseite)` länger geworden ist oder der Kasten mehr als 30 % seines
    eigenen Objekts oder 60 % eines kleinen fremden Akteurs verdeckt. Ein neuer Platz muss um 20 günstiger und ohne harten
    Konflikt sein; Weg (0,25 je px) und Seitenwechsel (90) kosten extra, damit nichts hin und her springt; zurück auf die
    Seite, die er in den letzten 80 Ticks verlassen hat, kostet weitere 120. Findet ein Hinweis in einem harten Konflikt
    keinen freien Platz, darf er die andere Umbruchbreite nehmen.
  - **Eindeutig:** Steht ein Platz mehr als 40 px × Textskala näher an einem fremden Punkt als am eigenen, kostet er 5000
    (mehr als das eigene Objekt ganz zu verdecken). Punkte anderer Hinweise zählen auch dort, wohin sie in den nächsten
    8 Ticks laufen. So steht ein Hinweis auch bei einer schnellen Kamerafahrt am Ende (Lektion „Akku“) nicht am Punkt des
    Nachbarn.
  - **Ausblendende Hinweise** (älter als 5 Ticks und Deckkraft unter 1) ziehen nur noch bei hartem Konflikt um und sperren
    keinen Platz mehr: ein neuer Hinweis darf dorthin, wo gerade einer verschwindet.
  - **Bewegte Anker:** Die Ankergeschwindigkeit auf dem Bildschirm (gleitender Mittelwert über 3 Ticks) sagt die Bahn der
    nächsten 16 Ticks voraus; Stellen auf dieser Bahn kosten 3000. Ein Hinweis an einer fliegenden Drohne zieht so einmal
    aus ihrem Weg (meist hinter sie) statt vor ihr hergeschoben zu werden.
  - **Gleiten:** Ein Umzug gleitet mit Smoothstep-Kurve in `8 + 0,08 × Weg/Textskala` Ticks (8–24 Ticks, also 0,4–1,2 s
    Echtzeit, auch bei Pause). **Seitenwechsel blenden um statt zu gleiten:** Wechselt ein Hinweis auf die
    gegenüberliegende Seite oder führte der gerade Weg über den eigenen Punkt oder mitten über das eigene Objekt, blendet der
    Kasten in 5 Ticks an der alten Stelle aus und in 7 Ticks an der neuen ein (Punkt bleibt sichtbar). Kommt der Anker
    einem gleitenden Kasten bis auf den Freiraum des Punkts (4 px) nahe, wechselt der Umzug ebenso aufs Umblenden. Vorher glitt der Kasten quer über seinen
    Punkt (42 Hinweise in 19 Lektionen, auch bei normaler Wiedergabe). Sprünge im Szenen-Takt um mehr als 12 Ticks (Schritt vor/zurück, Neustart, Spulen auf der
    Zeitleiste in großen Sätzen) setzen alle Hinweise neu, ebenso eine geänderte Ansichtsgröße.
  - Ein- und Ausblenden schiebt den Kasten wie bisher 6 px von der Seite herein; die Lage selbst ändert sich dabei nicht.
  `Placed.owner/ownerCover` melden das eigene Objekt und wie viel davon bedeckt ist, `Placed.target/gliding/from/fade` Ziel,
  Start und Deckkraft eines Umzugs; der Selbsttest „alle Szenen“ verlangt höchstens 20 % und keine überlappenden Hinweise
  (ausblendende Hinweise ausgenommen: sie sperren keinen Platz mehr, ein neuer Hinweis darf sie höchstens 5 Ticks überblenden),
  dass kein gleitender Kasten (weder an Start noch Ziel) seinen Punkt verdeckt und dass am Ende, nach 40 Ticks Ruhe ohne
  Drehen, kein Hinweis 40 px näher an einem fremden Punkt steht. `LessonLabelsTest` spielt jede Lektion zusätzlich mit
  nachgebauten Objekt-Kästen (eigenes Objekt, Akteure, Blöcke) und zählt Gleiten über den eigenen Punkt, Hin und Her
  (zurück auf die eben verlassene Seite binnen 80 Ticks ohne Drehen) und Mehrdeutigkeit im Schlussbild. Tastenhinweise rechnet
  `SceneOverlay.layoutKeyHints` vor den Hinweisen (sie sind Hindernisse) und zeichnet sie danach.
- **Bildausschnitt:** `guide.scene.SceneFraming.of(scene)` tastet die Szene alle 2 Ticks ab und merkt sich je Zeitpunkt die
  Motive: alle Drohnen (Kasten aus Radstand und Propellerdurchmesser wie im Spiel, `SceneFraming.droneRadius`), Akteure in
  Bewegung oder in ihren ersten 40 Ticks, ruhende Akteure nahe der Kamera (Abstand zum Kameraziel höchstens
  `Blickradius / Kamerazoom × 1,3`, z. B. Pilot neben der Drohne, Gates des Kurses; Gates mit ihrer echten Breite und Höhe),
  Blöcke ab y ≥ 1 im engeren Umkreis (× 0,6, z. B. Werkbank, Werkbank-Tisch, Mauer), Anker sichtbarer Hinweise und
  Tastenhinweise und hervorgehobene Kästen (nicht die Plattform). `projection(kamera, zeit, basis, links, oben, breite, höhe, nutzerZoom)`
  nimmt die Motive im Fenster ±24 Ticks (am Rand weich gewichtet, dadurch stetig), zoomt sie in die Ansicht abzüglich Rand
  (12 % links/rechts, 11 % oben, 17 % unten als Streifen für Tastenhinweise; Zoom 0,45 … 3 × Kamera, damit das Motiv bei jedem Seitenverhältnis gleich groß erscheint – etwa 70 % der Ansicht – statt an einer engen Grenze abgeschnitten oder winzig zu bleiben) und schiebt sie in die
  Mitte dieses Bereichs; der Nutzer-Zoom wirkt um die Motivmitte. `SceneScreen` und `FlightSchoolScenesTest` nutzen dieselbe
  Projektion, und `SceneRenderer` zeichnet mit genau dieser Projektion (`SceneProjection.applyRaster`, samt der Verschiebung der
  Mitte), damit Hinweis-Punkte bei jeder Fenstergröße, GUI-Skala und Kameradrehung auf ihrem Ziel liegen (Details:
  `docs/api/gui-scene.md` §4). Die Bühne blendet die Szene an allen Rändern in ihre dunkle Hintergrundfarbe aus (Vignette in `Palette.STAGE_BOTTOM`,
  seitlich 1/16 der Breite mit quadratischem Verlauf, nie heller als die Bühne), abgeschnittene Plattformen enden also weich. Hinweis-Kästen halten 16 px Abstand zum Rand (oben 22 px für Bedien-Zeile und Abzeichen); muss ein Kasten dafür verschoben werden, kostet das
  (6 je Pixel), sodass ein neuer Hinweis lieber auf die andere Seite geht. Abstände des Layouts (Zeigelinie 26 px, 10 px
  neben dem eigenen Objekt, 4 px um den Anker) wachsen mit der Textskala. Die Bedien-Zeile oben links steht hell auf fast deckendem Grund.
  Jeder Hinweis probiert zwei Umbruchbreiten (volle Breite und 70 %, mindestens 80 px) und nimmt die günstigere Lage; der
  schmalere Kasten kostet 30 extra. So passt ein Hinweis in kleinen Fenstern noch neben das Objekt, statt es zu verdecken.
  Verdeckt ein Kasten einen fremden Akteur (Pilot, Drohne, Item – keine Blöcke), kostet zusätzlich der verdeckte Anteil dieses
  Akteurs × 180, damit kleine Figuren wie der Pilot nicht unter einem Hinweis verschwinden.
- **Kleine GUI-Skalen:** Bei GUI-Skala 1 (`SceneScreen.textScale(guiScale)` = 2) darf das Szenenfenster doppelt so groß werden
  (höchstens 1520 × 940, sonst 760 × 470, immer mit 6 px Rand zum Bildschirm), und Hinweise, Tastenhinweise und die
  Schritt-Zeile unter der Bühne werden mit Faktor 2 gezeichnet (`SceneOverlay.callouts/keyHints(…, scale)`), damit der Text so
  groß bleibt wie bei GUI-Skala 2. Die gelieferten `Placed`-Kästen sind in Bildschirm-Koordinaten.
- **Böden:** Werkbank- und Akku-Szene stehen auf grauem Beton (`SceneKit.GRAY_CONCRETE`) mit Rand aus poliertem Tiefenschiefer,
  damit das Bauteil im Vordergrund steht (Glatter Stein war bei nahen Kameras zu unruhig).
- **Kartenliste:** Die Meta-Zeile („8 Schritte · 0:48“) steht fest 3 px unter der letzten Beschreibungszeile
  (`SceneList.metaY(zeilen)`), Karten mit einzeiliger Beschreibung haben also denselben Abstand wie zweizeilige.
- **HUD:** Solange `SceneScreen` oder `SceneIndexScreen` offen ist (`GuideClient.flightSchoolOpen()`), blendet guide alle
  Vanilla-HUD-Ebenen aus (`ClientRegistrar.hideVanillaHud`), damit unter dem Fenster keine Hotbar durchscheint.

### Flugtraining (guide; Entwurf `docs/design/flight-training.md`)
Lektionen zum Selberfliegen mit der eigenen Drohne; alles läuft beim Client, kein Payload, kein Registereintrag, keine Taste,
keine API-Änderung.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `guide/training/*` (`Curriculum`, `Chapter`, `Lesson`, `Ring`, `Zone`, `Pad`, `Marker`, `Pylon`, `Goal`, `StarRule`, `TrainingScale`, `TrainingFrame`, `Course`, `LessonRun`, `TrainingSample`, `Placement`, `TrainingProgress`) | guide (reines Java) | Lehrplan (fünf Kapitel, zwanzig Lektionen, Ids sind Teil der Fortschrittsdatei: nie umbenennen), lokaler Kursrahmen ab der Drohne (x rechts, y hoch, z vorwärts, `TrainingFrame#worldYaw` = Minecraft-Yaw), Auswertung je Frame `LessonRun#update(TrainingSample)` (Zeit = Sim-Zeit; Lage = Sim-Lage, Nase = Körper +Z), Platzprüfung gegen `Placement.Terrain`, Sterne und Bestzeiten je Größenklasse (`TrainingScale.of(radstand)`) |
| `guide/client/training/TrainingSession.INSTANCE` | guide | Render-Thread: `select(lesson)`, `stop()`, `lesson()`, `phase()` (`IDLE`, `WAITING` ohne Pilot-Sitzung, `AIRBORNE` ohne Kurs, `BLOCKED` kein Platz, `ACTIVE`), `course()`, `run()`, `placement()`, `banner()`, `messages()`; `ClientBus.FRAME_START` nach `PilotSession` (angemeldet aus `GuideClient.init()` über `TrainingClient`), `DISCONNECT` setzt zurück |
| `guide/client/training/TrainingStore` | guide | `JsonConfig` `flight_training` (`config/propwash/flight_training.json`), je Spieler-UUID ein `TrainingProgress`; `progress()`, `save()`, `forget()` |
| `guide/client/training/TrainingRenderer`, `TrainingHud`, `TrainingScreen` | guide | Hologramme an `ClientBus.SUBMIT_WORLD_GEOMETRY` (`RenderTypes.eyes`, Textur `propwash:textures/entity/race/white.png`), HUD-Ebene `propwash:flight_training` (FPV im OSD-Stil nach `nextStratum()`, Sichtflug mit `ThemeDraw.notice`), Fenster als `ThemedScreen` |
| `race/client/RaceLineRenderer#submitPath(collector, poseStack, camera, path, seconds, fade)` | race | zeichnet einen fertigen `RaceLine.Path` wie die Ideallinie des Rennens (Band, Leuchtsaum, laufende Pfeile); `submit` nutzt es selbst, das Flugtraining für die Linie durch die nächsten Ringe |
| `guide/client/GuideOpener#openTraining(parent)` | guide | öffnet das Training-Fenster; Knopf „Flugtraining“ in `SceneIndexScreen` (Unterkopf) und `GuideTab` (rechts in der Abschnittszeile) |

- **Taste M:** In der Welt öffnet sie wie bisher die Flugschul-Übersicht, aber im Flug (aktive `PilotSession`) oder mit
  gewählter Lektion das Training-Fenster. `GuideClient.flightSchoolOpen()` umfasst auch `TrainingScreen`.
- **Ablauf:** Lektion gewählt → ohne Pilot-Sitzung `WAITING`; Drohne ruht (nicht scharf, am Boden, < 0,6 m/s) → Platzprüfung
  (höchstens alle 2 s oder sobald die Drohne woanders steht) → `ACTIVE` mit `LessonRun` in `READY`; Scharfschalten startet,
  Ergebnis (`SUCCESS`/`FAILED`) zeigt 7 s ein Banner; ruht die Drohne danach 1,5 s, startet ein neuer Versuch am selben Kurs
  (≤ 3 m vom Start) oder der Kurs wird an der Drohne neu gesetzt. Anderer Rahmen (Größenklasse) oder neue Sitzung setzt den Kurs zurück.
- **Flughilfe** (`holdMode != OFF`, Rescue, veränderte Physik-Regeln `ClientWorldRules.INSTANCE.physics().modified()`) macht den Versuch „gestützt“: höchstens ein
  Stern, keine Bestzeit.
- Texte: `propwash.guide.training.*` (Modul `guide`), Lektionen `propwash.guide.training.lesson.<id>.title|description|tip`,
  Kapitel `propwash.guide.training.chapter.<id>.title|description`; Tricknamen aus `propwash.trick.type.*`.
- Selbsttest `guide.client.TrainingSelfTests` (am Ende von `GuideSelfTests.register()`, Platz 320 Blöcke neben dem Spieler,
  danach zurück): Texte und Freischaltung, Fenster, FPV-Drohne, Kurs an der Drohne, Schweben, Ringe mit Zwischenzeiten und
  neuem Versuch, Abschalten in der Luft, „Kein Platz“ durch eine Mauer, Taste M im Flug, Aufräumen, nach dem Verlassen leer.
  Bildschirmfotos `guide.client.TrainingShowcase` (vor `ApiShowcase` im Rundgang): `training_screen(_de)`, `training_hover_fpv(_de)`,
  `training_yaw_fpv`, `training_rings_fpv`, `training_result_fpv`, `training_course_los`, `training_landing_los`.

### Abschluss-Runde (Integration): neue Verträge
| Datei | Besitzer | Vertrag |
|---|---|---|
| `ui/client/config/ConfigTab#storage()` | ui | `Storage.DRONE` (PID-Tuning, Rates, LED-Streifen: gespeichert in der gehaltenen/geflogenen Drohne, Fußzeile wie bisher „in der Hand“/„live“/„Vorlage“) oder `Storage.GAME` (Standard: Spieleinstellung, Fußzeile `propwash.ui.footer.game`). `readOnly()` geht vor |
| `ui/client/config/ConfigTab#capturesInput()` | ui | true, solange ein Tab jede Taste und jeden Mausklick selbst braucht (Tastenbelegung). Der Konfigurator gibt dann `keyPressed`/`mouseClicked` zuerst an den Tab, auch ESC und Pfeiltasten |
| `input/client/screen/KeysTab` | input | Tab „Tasten“ (Order 420): alle Propwash-Tasten in drei Gruppen (Knüppel, Drohne, Funktionen), Klick + neue Taste/Maustaste, ESC beim Warten entfernt die Belegung, Konflikte mit allen Tasten (auch Vanilla) gelb samt Tooltip, „↺“ je Taste und „Standardwerte“ für alle |
| `drone/CraftNames`, `drone/net/CraftNameC2S`, `drone/client/CraftNameAccess` | drone | Name der Drohne: höchstens 24 Zeichen, ohne Formatierungs- und Steuerzeichen (`clean`). Ziel wie beim Tuning: geflogene oder nahe freie Drohne (`TUNE_REACH`) bzw. Drohnen-Item in der Hand. Namensschild an der platzierten Drohne benennt sie (verbraucht, außer im Kreativmodus). Gebundene Funken/Brillen im Inventar des Umbenennenden bekommen den neuen Namen (`BoundDrone.name`). Konfigurator: Tab „Konfiguration“, Abschnitt „Drohne“ |
| `content/DroneAssembly` | content | Zerlegen legt `drone_id` (wie `flight_tune`) auf den Stack/FC-Teil, Zusammenbauen übernimmt sie wieder: umgebaute Drohnen behalten ihre Funke |
| `drone/RadioLink#freeRange` | drone | siehe `PilotSession` (Kreativmodus oder Kreativ-Brille: keine feste Funkgrenze) |
| `mixin/drone/client/FirstPersonHandsAndItemsRendererMixin` + `drone/client/RemoteFirstPerson` | drone | Funke in der Haupthand (egal was die Nebenhand hält) oder in der Nebenhand bei leerer Haupthand, nicht beim Fernrohr und nicht während ein Item benutzt wird (`RemoteFirstPerson.held`): beidhändig tief vor dem Körper, das Nebenhand-Item wird dann nicht gezeichnet. Oberkante des Gehäuses bei etwa 24 % (geradeaus) bis 26 % (Blick nach unten, weich überblendet bis 45°) der Bildhöhe von unten, Antennenspitze höchstens etwa 34 %, Breite etwa 16 % der Bildbreite bei 16:9; Front 50° (geradeaus) bis 36° (nach unten) nach hinten gekippt, also leicht zum Betrachter geneigt. Die Hand-Projektion hat feste 70° (`Camera#calculateHudFov`), die Lage hängt daher nicht vom FOV ab und bezogen auf die Bildhöhe auch nicht vom Seitenverhältnis. Beide Arme über Vanilla `renderMapHand`, je Arm um die Faust um 55° (`FOREARM_PITCH`) nach unten geschwenkt, sodass die Unterarme aus den unteren Bildecken kommen und die Fäuste an den Griffen liegen, Daumen an den Knüppeln. Gezeichnet mit eigenem `ItemStackRenderState` im Kontext `NONE` (also ohne `firstperson`-Display des Modells). Sonst Vanilla mit dem `firstperson`-Display aus `tools/assets/world/remote.py` |
| `build/FrameType#stackMounts` | content (build) | Menge statt eines Stack-Formats: Race 5" und Long Range 7" nehmen 30×30 und 20×20, Long Range zusätzlich 2207-Motoren. Neue Teile `m2004_3000` (Cinewhoop) und `p31_3b` (Whoop). Die Sim-Tests `DerivationTest` zählen 9000 Kombinationen, 680 baubar |
| `drone_layout.json` `motor_seat`, `frames.<rahmen>.motors` | assets-parts (Generator) | `motor_seat`: Sitzhöhe der Props je Motor (mm); der Renderer verschiebt `prop_height` um die Differenz zum Vorlagenmotor des Rahmens. Race 5" hat einen gestreckten X-Rahmen (`stretch` 1,2 in `tools/assets/parts/layout.py`, Diagonale = Radstand) und trägt den Akku seit 0.5.0 unter dem Rahmen: `frames.<rahmen>.battery` ist bei `battery.on_top: false` der Aufhängepunkt (Oberseite des Packs), der Renderer nimmt dann die Variante `<akku>_under` oder hängt das Modell an seiner Oberkante auf (`DroneAssembly.batteryPart`) |
| `core/DevExtensions` | core | lädt `dev.DevBootstrap`/`dev.client.DevClientBootstrap` aus dem Quell-Set `devtest`, falls vorhanden (§9) |
| `content/BayState`, `BatteryChargerBlock.BAY_0..3`/`bay(i)` | content | Zustand je Schacht (`empty`, `charging`, `full`, `BayState.of(stack)`) als Blockeigenschaft; `BatteryChargerBlockEntity` setzt `state` und alle Schächte bei jeder Inhaltsänderung (`setChanged`) und im Lade-Takt (`displayState(state)`). Das Blockmodell (multipart, `docs/ASSETS.md` „Ladegerät“) zeigt Akku und LED je Schacht; Flugschul-Blöcke setzen `bay_<i>` wie jede andere Eigenschaft (`SceneKit.charger`) |
| `guide/client/screen/SceneOverlay`, `guide/layout/CalloutLayout` | guide | Hinweise werden einmal per Kostenfunktion gesetzt (gewünschte Seite, weichen anderen Hinweisen, Tastenhinweisen, dem eigenen Anker ± 4 px, dem eigenen Objekt und anderen Objekten aus), bleiben dann stehen und gleiten nur bei Konflikten langsam an eine neue Stelle; die Zeigelinie folgt jedes Bild dem Anker (siehe „Hinweis-Layout“ unter Flugschule). Zeilen per `ThemeText.balanced` |
| `content/BatteryChargerBlockEntity` (Sync), `content/Batteries#packColor`, `content/client/ChargerPackTint` | content | Das Ladegerät schickt seinen Inhalt an die Clients (`getUpdatePacket`/`getUpdateTag`, bei geänderter Akku-Belegung `sendBlockUpdated`; `packSignature()` = Akku-Typ je Schacht). Der Client baut den Chunk neu, sobald sich die Belegung ändert, und `ChargerPackTint` färbt die Hülle jedes Akkus im Block wie das Akku-Item (`packColor`); ohne Block-Entity blau (6S 1300). Selbsttest „content: Akkufarben im Ladegerät“ |
| `drone/RadioLink#LOCAL_FREE_RANGE` | drone | `BooleanSupplier`, vom Client gesetzt (`DroneClient`): gilt für den eigenen Spieler gerade keine feste Funkgrenze (Kreativmodus oder Kreativ-Brille)? Der Funken-Tooltip zeigt damit die geltende Reichweite (ohne Grenze bzw. Server-Grenze) statt immer „ca. 800 Blöcke“, der Server-Tab dieselbe Angabe als Zeile „Funkreichweite für dich“ direkt unter „Maximale Entfernung zur Drohne“ (die Server-Regel heißt „Keine Entfernungsgrenze“, damit sie nicht wie die Funkreichweite klingt) |
| `ui/client/theme/ThemeDraw#moreBelow(g, font, x, y)`, `ThemeDraw.MORE_BELOW_HEIGHT` | ui | der Hinweis „▼ Weiter unten mehr“ (Dreieck + normale Schrift in `TEXT_MUTED`, `y` = Textoberkante, Höhe `MORE_BELOW_HEIGHT`) für jede abgeschnittene Liste: Konfigurator-Tabs mit ganzen Zeilen, Flugschul-Kartenliste (Konfigurator-Tab und Übersichtsfenster halten dafür eine Zeile unter den Karten frei, `SceneList#footprint`), OSD-Elementliste (die dafür eine Zeile unter der Liste freihält) |
| `input/client/screen/ModesTab` | input | Zeilen = Arm, **Acro**, Angle, Horizon, Höhe halten, Position halten, Rescue, Aufrichten, Homepoint, Suchpiepser, Rennen neu starten, Aufnahme, LEDs aus. Ist der Tab zu niedrig (z. B. GUI-Skala 4 bei 1080p), entfallen Hinweis und Legende unter der Liste und die Zeilen schrumpfen auf 11 px, damit der Detailbereich mit Quelle, Bereich und Tasten sichtbar bleibt. Acro ist keine eigene Schalterfunktion (an, solange Angle und Horizon aus sind), hat aber die Taste `drone_mode_acro`. Während einer Pilot-Sitzung leuchten die Lampen von Acro, Angle und Horizon nach dem geflogenen Modus (`PilotSession#flightMode`, wie das OSD; `ModesTab#modeLit`), außerhalb nach den Schaltern; hat eine Taste gegen den Modusschalter gewählt, ersetzt `propwash.input.modes.key_override` die Hinweiszeile. Ohne Funke zeigt die Spalte „Wirkung“ je Zeile eine Kurzbeschreibung (`propwash.input.modes.effect.<zeile>`) und der Bereich unten die ausgewählte Zeile mit Taste und Beschreibung (`…desc.<zeile>`); mit Funke bleibt dort die Schalterbelegung. Mit Funke hat jede belegte Zeile rechts in der Spalte „Inv.“ einen Theme-Schalter „Invertiert“ (`InputGui.invertToggle`, Tooltip je Art über `InputGui.invertTooltip`, bei Arm mit Sicherheitshinweis); Lampe, Bereichsbalken und Min/Max zeigen sofort den wirksamen Zustand bzw. Bereich. Der Empfänger-Assistent hat denselben Schalter (mit „Inv.“ daneben) in jeder belegten Zeile der Seite „Schalter“ |
| `drone/client/RemoteFirstPerson#HOLD_SCALE` | drone | skaliert Funke und beide Hände gemeinsam um die Griffe (0,5, vorher 0,6), damit die beidhändige Funke im Sichtflug unter der Drohne bleibt (etwa 16 % der Bildbreite bei 16:9) |
| `drone/client/RemoteHoldPose`, `drone/client/RemoteHoldAccess`, `mixin/drone/client/HumanoidModelMixin`, `ItemInHandLayerMixin`, `AvatarRenderStateMixin` | drone | Funke in der dritten Person: `holdingArm(state)` = Arm mit der Funke, wenn ein Spieler (`AvatarRenderState`, nicht Zuschauer, aufrecht, kein Item in Benutzung) sie in einer Hand hält und die andere leer ist, sonst `null` (Rüstungsständer, Mobs, belegte zweite Hand → Vanilla mit Display `thirdperson_*`). Dann beide Arme nach vorn (−40°, ∓23° nach innen, Hände vor unterer Brust/Bauch) und die Funke im Raum zwischen beiden Armenden, im Kontext `NONE` mittig zwischen beiden Händen statt an einer Hand (`transform(model, poseStack)` im Modellraum nach `root`, Werte in `docs/ASSETS.md`). Der Render-State der Funke hängt als `RemoteHoldAccess#propwash$remoteHold()` an jedem `AvatarRenderState` (gefüllt in `AvatarRenderer#extractRenderState` TAIL); eigene States ohne Extract (Flugschule) löst `submit` aus der Stack-Kopie auf. `frames()` zählt gezeichnete Funken (Selbsttest) |

### Neue Funke: einmal erkennen, einmal einrichten (input)
| Datei | Besitzer | Vertrag |
|---|---|---|
| `input/DeviceProfile#setupDone`, `input/SetupStatus`, `input/InputConfig#setupStatus/markSetupDone/neverAskFor/usageRank`, `InputConfig.neverAsk` | input | Ein Profil gilt als eingerichtet, sobald der Empfänger-Assistent mit „Fertig“ endet (`markSetupDone`: `setupDone`, GUID aus `neverAsk`, Profil ans Ende = zuletzt benutzt). `setupStatus(guid)`: `COMPLETED` (eingerichtet) vor `NEVER` („Nie für dieses Gerät“, GUID in `neverAsk`, höchstens 64, nie `keyboard`) vor `INCOMPLETE` (Profil ohne Assistent) bzw. `NEW` (kein Profil). `setupDone` gehört weder zur Empfänger- noch zur Schalter-Portion (`copyReceiverFrom`/`mergeReceiverFrom`/„Standardwerte“ lassen es stehen), `copy()` und JSON behalten es. `PROFILE_VERSION` 2: Profile älterer Configs mit Kalibrierung (`DeviceProfile#calibrated`) gelten einmalig als eingerichtet |
| `input/DeviceChooser#pick(…, usage)` | input | „Automatisch“: eingerichtete Geräte zuerst, davon das zuletzt benutzte (`usageRank` = Position in `profiles`, `TransmitterInput` rückt das geöffnete Profil ans Ende); danach wie bisher Funke vor Gamepad vor Joystick. Feste Auswahl und die Sitzungssperre im Flug gehen weiter vor |
| `input/SetupPrompts` (reines Java, JUnit `SetupPromptsTest`) | input | Warteschlange je GUID für diese Spielsitzung: `update(geräte, status, now)` nimmt verbundene, berechtigte Geräte mit `NEW`/`INCOMPLETE` auf (fällig 1 s nach dem Anschließen); `poll(now, inWelt, sicher)` liefert `PROMPT`, sobald es 1,5 s am Stück sicher ist, sonst in der Welt einmal je Fälligkeit `NOTIFY` (Toast). Immer nur eine offene Frage bzw. Einrichtung, Reihenfolge = Anschlussreihenfolge. `answer`: `SET_UP` (Assistent läuft), `LATER` (höchstens eine Erinnerung, fällig nach 10 min, danach Ruhe bis zum nächsten Spielstart), `NEVER`. `setupClosed(guid, fertig)`: Abbruch zählt wie „Später“. Gerät abgesteckt → wartet auf das nächste Anschließen; Frage von einem anderen Screen verdrängt (`withdraw`) → zurück in die Schlange, ohne die Erinnerung zu verbrauchen; anderswo eingerichtet → erledigt |
| `input/client/DeviceOnboarding` | input | Verbindet `SetupPrompts` mit dem Spiel: bekommt die Geräteliste aus `TransmitterInput.scan` (`onScan`), läuft in `InputClient` auf `ClientBus.TICK_END` (`tick`). Berechtigt sind Funken, Gamepads und das gerade geöffnete Gerät. Sicher = Welt geladen, kein Screen, kein Overlay, keine Pilot-Sitzung (`safeMoment`). Öffnet `input/client/screen/DeviceSetupScreen` (Theme-Fenster „Neue Funke|Neues Gamepad|Neuer Controller erkannt“, Name + Art, Vorbelegung, Knöpfe Einrichten/Später/Nie für dieses Gerät, ESC = Später, schließt sich beim Abstecken). „Einrichten“ öffnet `ReceiverWizardScreen.forDevice(parent, guid, fertig, abbruch)`: zeigt genau dieses Gerät (`previewGuid`), lässt „Automatisch“ stehen und setzt sonst die Auswahl auf das Gerät; `removed()` ohne „Fertig“ = Abbruch. Die Taste „Empfänger-Assistent“ (ohne Standardtaste) öffnet zuerst die wartende Einrichtung. `simulate(liste)` ersetzt die echten Geräte (`null` = echte); der Selbsttest setzt beim Anmelden `List.of()`, damit ein am Entwicklerrechner steckendes Gerät nie fragt |
| `input/ProfileDefaults#radio` | input | Vorbelegung für Funken (auch EdgeTX/OpenTX-USB-Joystick): AETR auf Achse 0–3, Scharf Kanal 5 (1700–2100 µs), Flugmodus Kanal 6 (1300–1675 µs Horizon, 1700–2100 µs Angle, darunter Acro), Suchpiepser Kanal 7, Rescue Kanal 8, jeweils nur, wenn die Achse existiert. Gamepads unverändert Mode 2. Der Assistent zeigt auf der Geräteseite, ob das Gerät eingerichtet oder nur vorbelegt ist, auf der Kanalseite, dass „Zur Kalibrierung“ die Vorbelegung übernimmt |

Selbsttest `input: neue Funke erkennen und einmal einrichten (simuliert)` (`input.client.OnboardingSelfTests`): simulierte
Funke bei offenem Screen → kein Verdrängen, nur Toast → Screen zu → Frage erscheint → „Einrichten“ öffnet den Assistenten
für genau dieses Gerät → Abbruch zählt als „Später“ und fragt nicht sofort wieder → Funke eingerichtet + neues Gamepad →
nur das Gamepad wird gefragt, „Nie“ wird gespeichert → Ruhe; Toast „Profil geladen“ für die eingerichtete Funke.

### Empfänger-Assistent: Gas aus der Bewegung (input)
Discord-Bericht vom 02.10.: „Setting up throttle can lead to inverted axes when the stick is already down.“ Der alte
Bereichsmodus nahm das Ende, an dem das Gas 0,4 s ruhte, als OBEN. Wer das Gas kurz hochschob und wieder unten
abstellte, aus der Mitte (Gamepad) „ganz nach unten“ zog oder von oben nach unten zog, bekam die Richtung vertauscht.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `input/StickDetector` (reines Java) | input | Gemeinsame Schnittstelle der Kanal-Erkennung im Assistenten: `forChannel(kanal, startwerte, belegt[, erwartet])` liefert für Roll/Nick/Gier `ChannelDetector`, für Gas `ThrottleDetector` (mit `erwartet` = bisherige Gas-Belegung des Profils als Vergleich). `update(achsen, nanos)` → `Detection(achse, invert)` oder `null`; `leadingAxis()`, `progress(nanos)` und `stage()` (`DetectionStage`: `WAITING`, `TOO_SMALL`, `AMBIGUOUS`, `ONE_END`, `HOLDING`, seit 0.5.0 `CONFIRM`) für die Rückmeldung. Belegte Achsen (vorher erkannte Knüppel) zählen nicht |
| `input/ThrottleDetector` (JUnit `ThrottleDetectionTest`) | input | Gas-Achse und -Richtung nur aus echten Hüben: je Achse Umkehrpunkte mit 0,3 Hysterese (Rauschen zählt nicht); Kandidat ist die Achse mit dem größten Weg, mindestens 1,2 (60 % des Rohbereichs) und deutlich vor jeder anderen (zweitgrößter Weg höchstens 60 % davon, sonst `AMBIGUOUS`). Erkannt, wenn erst ein Hub bis an ein Ende und danach ein voller Hub zum anderen Ende (mindestens 80 %) vorliegt und das Gas dort 1,5 s bleibt (±15 % des Wegs): dieses Ende ist UNTEN, `invert` heißt UNTEN liegt bei hohen Rohwerten. Der Hub davor muss selbst voll sein (80 %); nur der erste Hub ab der Startlage darf kürzer sein (mindestens 40 %, Gas aus der Mitte, Gamepad). Kurzes Antippen vor dem Weg nach oben zählt so nie als „oben“. „Erst runter, dann rauf und oben halten“ liefert dieselben Rohwerte wie eine invertierte Funke, die richtig bedient wird; seit 0.5.0 entscheidet das nie die Bewegung allein: Widerspricht das gehaltene Ende der Startlage (das Gas stand beim Start des Schritts an einem Ende, ±15 % des Wegs, und wird am anderen gehalten) oder der bisherigen Richtung der gleichen Achse im Profil (auch bei Start an einem Ende: oben gestartet und oben gehalten gleicht einer invertierten Funke, die Vorbelegung sagt nicht invertiert), speichert der Detektor nichts, sondern geht nach der Haltezeit auf `CONFIRM`; `confirm(gehaltenesEndeIstUnten)` liefert dann die `Detection`, Verlassen des Endes beendet die Rückfrage und die Erkennung läuft weiter (zieht der Spieler jetzt nach unten und hält, passt es zur Startlage und wird ohne Frage erkannt). `startedAtEnd(achse)` für Rückmeldungen. JUnit: Prüfer-Fälle (Antippen 60–100 %, hoch-runter-hoch, Störspitze), 400 Zufallsfolgen ohne Profil: nie gegen die Ruhelage gespeichert, und 400 mit Profil-Richtung und beliebiger Startlage: oben gehalten nie gespeichert. Die Lage beim Start ist nie ein Ende: Gas, das unten steht und nur nach oben geschoben wird, wird nicht erkannt, auch nicht nach langer Pause oben. `stuck()` nach 1,5 s Ruhe an einem Ende ohne gültige Bewegung (Hinweis „schon unten? erst noch einmal ganz nach oben“). Ein Ausschlag über 0,3 beim Halten verlangt eine neue Bewegung (Hinweis „noch einmal ganz nach oben und gleich wieder nach unten“) |
| `input/ChannelDetector` | input | Roll/Nick/Gier: Ausschlag ab 0,5 gegen den Ruhewert, 0,12 s gehalten, Dominanz 60 %. Ruhewert = Startwert nur, wenn die Achse dabei in der Mitte steht (Betrag ≤ 0,5), sonst erst, wenn sie 0,15 s in der Mitte war: ein beim Start gehaltener Knüppel wird beim Loslassen nicht als Gegenrichtung erkannt, ein unten geparktes Gas stört die Knüppel-Schritte nicht |
| `input/client/screen/ReceiverWizardScreen` (Seite „Kanäle“) | input | Gas-Schritt: „zuerst ganz nach OBEN, dann ganz nach UNTEN und dort halten“, Live-Balken der Achse mit erreichtem Weg (gelb, solange zu wenig, dann grün), Rückmeldung je `DetectionStage` in fester Höhe; zu wenig Weg und der Hinweis „schon unten?“ wiederholen die Reihenfolge OBEN, dann UNTEN. Beim Halten bestätigt der Assistent nichts, sondern fragt: „UNTEN?“/„OBEN?“ an den Enden (Akzentfarbe statt Grün), Text „Steht das Gas ganz UNTEN (aus)? Dann weiter halten … Steht es noch OBEN? Dann jetzt nach UNTEN ziehen!“, darunter der Haltefortschritt. Knüppel-Schritte melden „Weiter – ganz bis zum Anschlag“ und „Mehrere Achsen bewegt“. Nach dem vierten Kanal (erkannt oder beibehalten) folgt die Prüfansicht: Live-Balken je Kanal (µs, noch ohne Kalibrierung) mit „Inv.“-Schalter, Belegung aus dem Profil, Fußleiste „Zurück | Neu erkennen | Zur Kalibrierung“; vorher ging es direkt zur Kalibrierung. Bei `CONFIRM` ersetzt „Kurze Frage, damit die Richtung sicher stimmt: Wo steht dein Gas gerade?“ die Rückmeldung, darunter die Knöpfe „Ganz UNTEN (aus)“/„Ganz OBEN (voll)“ (`ThrottleDetector#confirm`, Statuszeile „von dir bestätigt“). Für Selbsttests: `currentChannel()`, `detectionStage()`, `previewSticks()`, `confirmingThrottle()`, `answerThrottle(unten)`, `calibrationStep()`, `listening()`, `switchDemoRoom()`, `clearStatus()`, `demo()` |
| `input/client/SimulatedRadio#raw()`, `TransmitterInput#raw()` | input | Mit simulierter Funke (`simulate`) liefert `raw()` deren Rohwerte als Gerät (ohne Gamepad-Belegung), damit Selbsttests den Assistenten bedienen können; ohne Simulation unverändert |

Selbsttest `input: Assistent erkennt Gas aus der Bewegung (Gas steht unten, kurz angetippt, Funke invertiert, simuliert)`
(`input.client.ThrottleWizardSelfTests`): simulierte EdgeTX-Funke mit invertierter Gas-Achse (Rohwert +1 = unten), Gas
steht beim Start unten → Assistent → „Weiter“ → Roll, Nick und Gier per Ausschlag → Gas 1 s unten: nichts erkannt
(`WAITING`) → kurz 40 % angetippt und zurück → nach oben und 2 s oben halten: nicht erkannt (`ONE_END`) → nach unten und halten: Achse 3, invertiert, frühestens nach der halben Haltezeit in Ticks →
Prüfansicht mit vier „Inv.“-Schaltern, Gas unten 0 %, oben 100 % → „Inv.“ beim Gas schaltet die Richtung um.
Bildschirmfotos `receiver_wizard_throttle(_de).png`, `receiver_wizard_throttle_hold.png`, `receiver_wizard_check.png`.

### Empfänger-Assistent mit animierter Funke, Scharf-Winkel (input, drone-render, ui, sim; 0.5.0)
Wie in Liftoff macht das Modell der Propwash-Funke in jedem Schritt vor, was zu tun ist; daneben bleiben die Live-Balken,
die auf die echten Knüppel reagieren. Anlass: Ein Creator schaltete mit der Taste scharf, weil er die Schalter-Seite nicht
verstanden hatte.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `input/RemoteDemo` (reines Java, JUnit `RemoteDemoTest`) | input | Vorführ-Abläufe als Schleifen (`Motion`: Gas, Roll, Nick, Gier, Prüfansicht, Mitte, 3D-Mitte, große Kreise, Kalibrierung prüfen, Schalter an/Mitte, Taste, Gamepad-Taste, Arm-und-Modus, Gamepad-Arm-und-Modus); `frame(target, sekunden)` → `Frame` mit Knüppeln (−1…1, links Y = Gas), vier Schaltern (−1 weg = aus, 0 Mitte, +1 zum Piloten = an), drei Tasten (0…1 gedrückt), Fokus, Pfeil, Text (`Cue`, Schlüssel `propwash.input.demo.<cue>`), Kanal und Haltefortschritt. `forSwitch(funktion, gamepad)`: Arm → Schalter 0, Angle → Schalter 1 oben, Horizon/Position halten → Schalter 1 Mitte (wie die Radio-Vorbelegung AUX1/AUX2), Taster-Funktionen und Gamepads → Tasten. Die Tests füttern die Vorführung in `ThrottleDetector`, `ChannelDetector` und `SwitchDetector`: wer sie nachmacht, wird richtig erkannt; „✔ erkannt“ erscheint, wenn der Detektor auslöst |
| `drone/client/render/RemoteGimbalModel`, `RemoteGimbalRenderer.Pose/Controls` | drone-render | Das bewegte Modell zeichnet Knüppel, die vier Schalter (40° je Seite) und die drei Tasten unter dem Display (0,22 px Hub); `Pose(…, Controls)`, `Controls.NEUTRAL` = Schalter senkrecht wie im Item-Modell, Tasten oben. `remote_body.json` enthält die Schalter nicht mehr (`tools/assets/world/remote.py`), `remote.json` (GUI, Boden, Rahmen) unverändert |
| `input/client/screen/remote/RemoteDemoView`, `RemoteDemoRenderer`, `RemoteDemoRenderState`, `RemoteProjection` | input | Bild-im-Bild der Funke (Item `propwash:remote`, Kontext `NONE`, `RemoteGimbalRenderer.extractPosed`), angemeldet in `InputClient` über `ClientRegistrar.pictureInPictureRenderer`. Theme-Bühne `ThemeDraw.previewStage`, Titel „So geht's“, Blick 15° von oben vorn auf die ganze Funke (so liegt die Knüppelspitze bei Gas ganz oben sichtbar über der Knüppelmitte, JUnit `RemoteProjectionTest`), Nahansicht für Schalter und Tasten mit 30° (`Region` trägt den Winkel), Überlagerung aus derselben Projektion: Ring in der Kanalfarbe, Pfeil, Punkt an der Knüppelspitze, kleiner Schieber für die Schalterstellung, Haltebalken, Text bis zwei Zeilen. Mindestens 64×56 GUI-Pixel, sonst nichts |
| `input/client/screen/ReceiverWizardScreen` (Vorführung) | input | Kanäle: rechts oben die Vorführung (bis 170, bei viel Platz bis 240 hoch), darunter die Rohachsen (bei wenig Platz vier Zeilen in zwei Spalten). Kalibrieren: rechts daneben, auf schmalen Fenstern darunter; mit freigeschaltetem 3D zeigt „Mitte“ das Einpendeln auf die genaue Gasmitte. Schalter: Zeile „Scharf schalten“ hervorgehoben (kräftiger, solange unbelegt), Namensspalte nach dem längsten Namen, Belegung kurz (`AUX 1 1700–2100 µs`), wenn die lange nicht passt; ist unter den Zeilen Platz (≥ 76 px), unten links die Vorführung „Scharf: Schalter umlegen“ und „Modus: Acro · Horizon · Angle“ mit Arm-Hinweis daneben, beim Zuweisen die Vorführung der Funktion; sonst (z. B. GUI-Skala 4 bei 1080p) eine Karte neben dem Knopf beim Zuweisen und beim Zeigen auf „Zuweisen“. Passt der Arm-Hinweis nicht ganz unter die Zeilen, steht seine Kurzfassung da (`switches_arm_short`/`switches_arm_ok_short`); ist dort gar kein Platz (240 px GUI-Höhe), steht die Kurzfassung in der Statuszeile über den Knöpfen, solange keine Meldung läuft (Selbsttest prüft GUI-Skala 1–4, Bild `receiver_wizard_remote_switches_gui240.png`); der Zusatz-Hinweis zu „Inv.“ und „Modi“ erscheint nur vollständig; angeschnittene Zeilen enden mit „…“. „Fertig“ ohne Arm-Schalter fragt einmal nach („Ohne Arm-Schalter?“, Statuszeile nennt die Taste). Geräteseite ohne Vorführung (dort wird nichts bewegt) |
| `drone/TuneMapping.ARM_MAX_ANGLE` = `pid.armMaxAngleDeg`, `ui/TuneKeys.ARM_MAX_ANGLE`, `sim/FlightControllerConfig.ARM_ANGLE_*` | drone, ui, sim | Scharf-Winkel wie `small_angle` in Betaflight: 0–180°, Schritt 1, Standard 25° (bisheriger fester Wert, Flugmodell unverändert); 180° = scharf in jeder Lage, auch in der Luft und kopfüber, 0° = nur genau waagerecht. Gehört zum Tuning der Drohne (Gruppe `PID` ⇒ in `TuneGroup.FLIGHT`, Sharecodes und Standard-Importe tragen ihn, Just More Parts schreibt ihn als Schlüssel mit Namen), der Flugcontroller begrenzt auf 0–180° (vorher 5–180°). Tab „Konfiguration“, Abschnitt „Scharf schalten“ mit Erklärung je Wert; PID-Tab setzt ihn beim Zurücksetzen nicht mit zurück; Meldung „Scharf schalten nicht möglich: mehr als N° gekippt (Scharf-Winkel im Konfigurator, Tab „Konfiguration“)“. JUnit `ArmAngleTest` (Lagen, in der Luft, 3D-Modus), `ArmAngleTuneTest` |

Selbsttests `input: Assistent zeigt in jedem Schritt die animierte Funke (simuliert)`, `input: animierte Funke bei
GUI-Skala 1 bis 4 (simuliert)`, `input: Assistent fragt nach, wenn das Gas gegen die Startlage gehalten wird (simuliert)`,
`input: Assistent fragt bei „Fertig“ ohne Arm-Schalter einmal nach (simuliert)` (`input.client.WizardRemoteSelfTests`) und
`ui: Scharf-Winkel 180° aus dem Tab Konfiguration kommt über das Tuning an die Drohne` (`ui.client.ArmAngleSelfTests`).
Bildschirmfotos `receiver_wizard_remote_*.png` (Gas englisch/deutsch, Rückfrage, Prüfansicht, 3D-Mitte, Kreise, Schalter
englisch/deutsch, Zuweisen, GUI-Skala 2 und 4, Karte bei GUI-Skala 4), `configurator_arm_angle(_de).png`,
`flight_school_arm_air.png` (Flugschule „Absturz, Reichweite, Signal“, Schritt „Scharf schalten in der Luft“).

### Bauteil-Verschleiß (sim, drone, content; Modell in `docs/design/flight-model-spec.md` 9.6)
| Datei | Besitzer | Vertrag |
|---|---|---|
| `build/PartWear` | content (reines Java in `build`) | Zustand 0 (kaputt) … 1 (neu) je Arm, Stack, Motor, Prop, Video, Akku; unveränderlich, auf 1/1000 gerundet (`quantize`, `toSteps`/`fromSteps`), `PRISTINE` für neue Teile. Positionen 0–3 = vorne links, vorne rechts, hinten links, hinten rechts (wie Werkbank und `DroneLayout`), `simMotor(position)` = 3, 1, 2, 0. `slot(i)`/`slots()`/`fromSlots` folgen `BuildSlots` (Rahmen = schlechtester Arm). Schwellen `damaged` < 0,9, `critical` < 0,4, `broken` = 0 |
| `sim/PropFailure`, `sim/CrashDamage.propLoss/strikeLoad`, `AirframeSpec.propBrittleness` | sim | Bruchverhalten eines Props aus Steifigkeit und Haltbarkeit (`brittleness` β, `shatter` σ, `mode()` `BENDS`/`BENDS_OR_CRACKS`/`CRACKS`/`SHATTERS`, ab 0.5.0); Prop-Verlust je Aufprall mit elastischer Grenze, Riss- und Bruchwurf, Strike-Last nach Spin² × Anflug; `WearEffects` mischt Schubverlust (verbogen) und Vibration (angebrochen) nach β. Tooltip-Zeile „Absturz: …“ (`content/PartDataLines`, `propwash.parts.tooltip.failure.*`) |
| `core/CoreComponents.PART_WEAR`, `PART_CONDITION`, `PART_WEAR_SERIALIZER`, `core/WearCodecs` | core | `propwash:part_wear` (`PartWear`) am Drohnen-Item, fehlt → neu; `propwash:part_condition` (Float) an einzelnen Teil-Items, fehlt → neu. `WearCodecs.CODEC` mit optionalen Feldern (`frame`, `stack`, `motors`, `props`, `video`, `battery`; alte Drohnen laden als neu), `STREAM_CODEC` (1 Byte für neu, sonst 15 Shorts) |
| `drone/QuadcopterEntity#wear()`, `setWear(PartWear)` | drone | synchronisiert, gespeichert als `Wear` (nur wenn nicht neu; fehlt → neu). `loadFromStack`/`toStack` tragen `part_wear` (ohne Akku gilt der Akku-Platz als neu). Der Akku-Zustand wandert mit dem Akku: `batteryStack()`/`takeBattery()` setzen `part_condition` am Akku (danach Akku-Platz neu), `insertBattery(type, charge, condition)` übernimmt ihn (die alte Überladung setzt neu) |
| `drone/net/ImpactC2S`, `drone/DroneWear` | drone | `PilotSession` sammelt nach jedem `afterMove` die schädlichen Aufpralle aus `DroneSim.impacts()` (`CrashDamage.harmful`, höchstens 16 je Frame) und schickt sie gebündelt (≤ 8 je Paket) mit Epoche und Stärke (`ClientWorldRules#crashDamageStrength`, 0 = nichts senden). Server: nur Pilot mit passender Epoche, Stärke aus der Welt-Regel (`WorldRules.crashDamageStrength`, 0 = nichts), höchstens 24 Aufpralle je Sekunde und Drohne, Schaden mit Server-Zufall (`DroneWear.apply`), `setWear`, Bruchgeräusch (`ITEM_BREAK`) für neu kaputte Teile. `PilotSession` gibt den synchronisierten Zustand bei Start und jeder Änderung an `DroneSim.setWear` |
| Gamerule `propwash:crash_damage` (`WorldRuleSpecs.CRASH_DAMAGE`, `WorldRules.crashDamage`, `ClientWorldRules#crashDamage`) | server | Welt-Regel „Crash-Schaden“ (Standard an); aus = kein Crash-, Hitze- und ESC-Verschleiß für niemanden. Ersetzt seit 0.5.0 die Server-Regel `partWear` (Abschnitt „Welt-Regeln“); `"partWear": false` in `server.json` übernimmt der erste Serverstart einmal als `false` (`server.LegacyServerSettings`, JUnit `LegacyServerSettingsTest`) |
| Gamerule `propwash:crash_damage_scale` (`WorldRules.crashDamageStrength`) | server, drone | Schadensstärke 25–300 % für alle (früher Schalter und Regler nur mit Kreativ-Brille, entfallen) |
| `drone/DroneInteraction` (`HeldKind.PROP`, `HINT_PROPS`, `OPEN_SERVICE_MENU`), `drone/DroneServiceMenu`, `drone/DroneServiceContainer`, `drone/PropService`, `drone/client/DroneServiceScreen` | drone | Wartung: Schleichen + Rechtsklick auf die Drohne öffnet **immer** das Wartungsmenü (Menütyp `DroneModule.DRONE_SERVICE_MENU`, Id `propwash:drone_service`, Titel `container.propwash.drone_service`), auch mit Props in der Hand; nur ein passender Akku in der Hand tauscht wie bisher sofort (`QUICK_SWAP`). Ohne Schleichen mit Props → Hinweis. Menü-Slots: 0 = Akku (Verhalten unverändert), 1–4 = Props vorne links, vorne rechts, hinten links, hinten rechts (`PartWear`-Positionen), dann Inventar. Die Prop-Slots zeigen `build.prop()` mit `part_condition` = Zustand der Position; herausnehmen geht nicht, nur tauschen. Der Server entscheidet alles (`clicked` für Prop-Slots läuft nur serverseitig, der Client sagt nichts voraus): Klick mit Props bzw. Zifferntaste tauscht diesen Platz, Shift-Klick auf Props im Inventar tauscht alle beschädigten (< 0,9 und schlechter als die neuen), schlechteste zuerst. Regel `PropService.fit(frame, installed, offered, count)`: Durchmesser ≤ `frame.maxPropMm` (wie `DroneBuild.Issue.PROP_TOO_LARGE` in der Werkbank), sonst `TOO_LARGE`; gleiche Sorte → `SWAP_ONE`; andere passende Sorte nur mit mindestens vier Stück (Kreativ: immer) → `SWAP_ALL` tauscht alle vier auf einmal und setzt `build.withProp`, sonst `NEED_FOUR` (alle vier Props einer Drohne sind gleich). Neue Props werden verbraucht (Kreativ nicht), die alten, auch kaputte, kommen mit ihrem Zustand ins Inventar (`placeItemBackInInventory`, voll → fallen lassen). Gesperrt wie der Akkutausch: `stillValid` = `usable` (nicht armed, nicht gesteuert, ≤ 8 Blöcke). Der Screen zeigt je Prop Balken und Prozent in der Zustandsfarbe, eine Hinweiszeile für das getragene Item (bündig mit dem Inventar, Luft zur Inventar-Beschriftung), im Rumpf das Rahmen-Item mit „Rahmen“ und beim Rumpf einen Tooltip mit Rahmen und passenden Prop-Sorten |
| `content/PartCondition`, `content/Recycling`, `content/DroneAssembly` (`conditions`, `wearOf`, `wear`, `storeWear`), `DroneWorkbenchMenu#conditions()`, `client/WorkbenchReport.of(check, wear)` | content | Tooltips (Balken ■□ + Prozent/„kaputt“, Farbe je Zustand; Teile zusätzlich Haltbarkeitsbalken, Akkus behalten den Ladebalken), Drohnen-Tooltip mit Zusammenfassung („Zustand: … · schlechtestes Teil“) und (Shift) unter „Technische Daten“ eine Zeile je
weiterem beschädigten Teil (`PartCondition.detailLines`, ohne das schon oben genannte). Passende Akkus stehen beim Drohnen-Item als
Zeilen in „Technische Daten“ statt in einem eigenen Abschnitt „Passt zu“, damit der Tooltip bei GUI-Skala 3 und 1080p ganz ins Bild passt. Zerlegen legt `part_condition` auf jedes Teil, Zusammenbauen baut daraus `part_wear`. Werkbank: Verschleißzeilen je Kategorie (gelb < 0,9, rot < 0,4) samt Slot-Markierung, Vorschau mit beschädigten Props. Das Zerlegen-Feld nimmt auch kaputte Teile (Zustand 0) und gibt Zwischenteile zurück (Rahmen → Carbonplatte außer Whoop, Motor → Kupferspule, Stack/Video → Platine, Akku → LiPo-Zelle; Props sind Schrott). Das Ladegerät nimmt kaputte Akkus nicht an |
| `build/PropRepair`, `content/PropRepairs`, `content/PropRepairRecipe` (`propwash:prop_repair`, Rezept `propwash:special/prop_repair`), `mixin/content/GrindstoneMenuMixin` | content | Props reparieren (ab 0.5.0): zwei beschädigte Props derselben Sorte (gleiches Item und `propwash:part`, beide < 1, auch 0) ergeben einen mit `a + b + 0,05`, höchstens 1, in 1/1000-Schritten (`PropRepair.combine`, reines Java). Crafting als Sonderrezept (genau zwei belegte Felder, 2×2 oder 3×3, Stapel erlaubt, nicht im Rezeptbuch), Schleifstein per Mixin (Eingabeplätze nehmen beschädigte Props einzeln, `computeResult` entscheidet bei Props allein, keine Erfahrung, keine Reparaturkosten). Ergebnis = Kopie des ersten Props mit allen Komponenten und neuem `part_condition` (ab 1 entfernt). Gilt für Addon-Props ohne eigene API. Hinweise: Statuszeile bei kaputten Props, Shift-Zeilen `propwash.help.category.prop.use.4/5`, Zerlegen-Feld der Werkbank, Flugschul-Schritt `repair/combine`; Doku `docs/design/survival.md` „Props reparieren“ |
| `drone/client/render/PropDamage`, `PartModel.prop(prop, ccw, damage)`, `DroneAssembly#prop(slot, damage)`, `DroneRenderState#wear`, `DroneVisuals.previewState(build, wear)`, `client/render/DronePreview.render(g, build, wear, …)` | drone | Schaden in Stufen (`PropDamage.of`): ab < 0,9 (`damaged`) `WORN` = Modell `…_worn` (Spitzen abgesplittert, Kerben), ab < 0,4 (`critical`) `DAMAGED` = `…_damaged` (ein Blatt verliert ein großes Stück), 0 `BROKEN` = `…_broken` (Blatt an der Nabe abgebrochen, eines verbogen, keine Blur-Scheibe). `representative()` = Stellvertreter-Zustand je Stufe (Vorschau-Cache), `suffix()`. Welt, Werkbank-/Wartungs-Vorschau, Flugschule und Item-Symbol zeigen dieselbe Stufe. Die alte `DronePreview.render`-Signatur bleibt (zeigt neue Teile) |
| `drone/client/render/PropDamageProperty` (`propwash:prop_damage`), `registry/client/ClientRegistrar#rangeSelectItemProperty(id, codec)` | drone (Eigenschaft), Foundation (Registrierung) | Item-Modell-Eigenschaft für `minecraft:range_dispatch`: Stufe 0–3 aus `propwash:part_condition` (`stage(stack)`); die Prop-Items wählen damit ihr Symbol (`items/<prop>.json`). `rangeSelectItemProperty` wie `specialModelRenderer` (nur Namespace `propwash`, einmalig, in der Client-`init()`), Fabric trägt in `RangeSelectItemModelProperties.ID_MAPPER` ein, NeoForge über `RegisterRangeSelectItemModelPropertyEvent` |
| `osd/OsdData#wear`, `OsdWarnings.wear`, `OsdCondition`, `FlightStats#crashes` | osd | Warnungen `PROP_BROKEN`, `ARM_BROKEN`, `FC_DAMAGE` (kritisch, blinken), `PROP_DAMAGE`, `MOTOR_DAMAGE`, `CAMERA_DAMAGE` (< 0,4), `BATTERY_PUFFED`, Sperrgrund `ARM_HARDWARE`; Flugstatistik mit Abstürzen dieses Flugs und dem schlechtesten Teil |
| `video/FeedInput#cameraCondition` | video | ab < 0,9 Rauschen, Farbverlust, Unschärfe, Vignette und kurze Störungen (analog Zeilenrisse über `glitch` und Zeilenzittern, digital Blöcke), stärker je Schaden; kaputte Kamera (0) = schwarzes Bild (OSD bleibt) |
| `guide/scene/Cue.PropCondition`, `StepBuilder#propCondition/propConditions/itemCondition`, `ActorState#propCondition(slot)` | guide | Prop-Zustand eines Drohnen-Akteurs in Szenen (0 … 1), gezeichnet wie im Spiel; Item-Akteure von Teilen zeigen den Zustand von Platz 0 (`itemCondition`, `SceneAssets.item(id, condition)`) |

Assets: `propwash:part/prop/<key>_worn`, `<key>_worn_ccw`, `<key>_broken`, `<key>_broken_ccw` samt
`items/drone_render/prop/…` erzeugt `tools/assets/parts/props.py` (`build_damaged`, `BLADE_DAMAGE` je Stufe und Blatt: Spitze,
Absplitterung, Kerben, Knick), ebenso `<key>_damaged[_ccw]`.

Selbsttest-Schritte: „drone: Absturz beschädigt alle Bauteile“ (nach dem Crash-Schritt: jeder Platz < 1, Server,
Client und Flugmodell gleich), „drone: Spielablauf – Wartungsmenü tauscht beschädigte Props, zu große werden abgelehnt“
(Schleichen + Rechtsklick mit 7-Zoll-Props in der Hand öffnet das Menü, die zu großen Props bleiben in der Hand, ein
Klick tauscht den kaputten Prop vorne links, Shift-Klick den angeknacksten vorne rechts, die alten liegen mit Zustand im
Inventar, Akku und Motoren unverändert), „drone: Spielablauf – Werkbank behält den Zustand,
kaputte Teile werden recycelt“, „drone-render: beschädigte Props zeigen eigene Modelle“ (Stufe je Prop – kaputt, beschädigt,
angeknackst, neu –, gebrochener Prop ohne Blur-Scheibe, Item-Symbol-Stufe aus `part_condition`). Die Szene `repair` läuft im Selbsttest „guide: alle Szenen Schritt für Schritt abspielen“ mit.

### Zubehör: Slots, Halterungen, Action-Cams und LED-Streifen (content, drone-render, sim)
Grundlage für die LED- und die Action-Cam-Arbeit. Zubehör ist **optional**: eine Drohne ohne Zubehör ist genau der alte
Build, alte Speicherstände und Items laden unverändert.

**Datenmodell (reines Java in `build`, Besitzer content):**
| Datei | Vertrag |
|---|---|
| `build/AccessoryMount` | Halterungen `TOP` (Key `top`, Action-Cam, hat einen festen Sockel am Rahmen: `hasBase()`) und `BOTTOM` (`bottom`, LED-Streifen unter der Nase, ohne Sockel). `byKey`, `anchor(frame)`. Je Halterung höchstens ein Zubehör |
| `build/AccessoryType` (implementiert `PartInfo`, Item-Id = `key()`) | `LED_STRIP` (`led_strip`, unten, 6 g, 32 × 3 × 8 mm, 8 LEDs, 1,5 W am Flugakku), `ACTION_CAM_LIGHT` (`action_cam_light`, oben, 42 g, 46 × 30 × 26 mm, 4 W am Flugakku, kein eigener Akku), `ACTION_CAM_HEAVY` (`action_cam_heavy`, oben, 150 g, 60 × 45 × 35 mm, Stirnfläche 0,0027 m², eigener Akku, 0 W am Flugakku); seit 0.5.0 dazu `ACTION_CAM_FLASHBACK` (Abschnitt „Action-Cam (Flashback)“ unten). Felder `mount`, `widthMm` (X), `heightMm` (Y), `depthMm` (Z), `flightBatteryWatts`, `frontalAreaM2`, `ledCount`, `lensLengthMm`; `camera()` → `Optional<ActionCamSpec>`, `isCamera()`, `isLight()`, `fits(mount)`. Lokale Geometrie relativ zum Anbaupunkt (vor der Neigung): `boxMinMm()/boxMaxMm()/centerMm()` (oben: Körper ab `HINGE_CLEARANCE_MM` = 5 mm über dem Gelenk, mittig über dem Gelenk; unten: Leiste hängt unter dem Anbaupunkt), `lensMm()` (Linsenmitte vorn, −Z), `ledMm(i, streifenlänge)` (LED-Mitten gleichmäßig entlang X, Unterseite) |
| `build/ActionCamSpec` | aus echten Kameras abgeleitet. `LIGHT` (nackte FPV-Action-Cam): 1080p60, keine Stabilisierung, Sensor 1/2,8″, Sichtfeld 150°, Bildqualität 0,80, Rauschen bei wenig Licht 0,55, 4 W vom Flugakku. `HEAVY` (große Action-Cam mit Gehäuse): bis 1440p60 (mehr schafft der Java-Encoder nicht in Echtzeit), elektronische Stabilisierung + Horizont halten, Sensor 1/1,9″, 155°, 0,95, 0,30, eigener Akku 6,6 Wh bei 4,4 W (1080p) bis 6,6 W (1440p) → ca. 90 bzw. 60 min. `supportedHeights()` (720/1080/1440/2160 bis `maxHeight`), `recordWatts(height)`, `recordMinutes(height)` (∞ ohne eigenen Akku), `ownBattery()` |
| `build/Accessories` (Record über `Map<AccessoryMount, AccessoryType>`, unveränderlich, `EnumMap`-Reihenfolge) | `NONE`, `of(…)`, `with(type)` (ersetzt dieselbe Halterung), `without(mount)`, `at(mount)`, `has`, `list()`, `mounts()`, `camera()` (erstes Kamera-Zubehör), `massGrams()`, `flightBatteryWatts()`, `frontalAreaM2()`, `key()` (`top=action_cam_heavy+bottom=led_strip`). Der Konstruktor wirft bei Typ an falscher Halterung |
| `build/AccessoryAnchor` (Record `x, y, z, tiltDegrees, lengthMm`, mm im Rahmensystem wie `drone_layout.json`: X rechts, Y oben, Z hinten) | Anbaupunkt je Rahmen und Halterung. `of(frame, mount)` (Tabelle, sonst Rückfall aus dem Radstand), `table(frame, mount)`, `rotate`/`toFrame(local)` (Drehung um X um `tiltDegrees`, positiv = Vorderkante hoch), `centerOf(type)`, `corners(type)`, `stretch(type)` (unten: `lengthMm / widthMm`, das Modell wird entlang X auf die Streifenlänge gestreckt), `ledMm(type, i)`, `ledDirection()` (lokal −Y gedreht), `lensMm(type)`, `viewDirection()` (lokal −Z gedreht). Oben = **Drehachse des Kameragelenks** (9–15 mm über dem Kamerakäfig vor dem Akku), unten = Mitte der Oberseite des Streifens. Die Tabelle ist die Quelle der Physik; `drone_layout.json` muss dieselben Werte haben (JUnit `DroneLayoutParserTest#shippedLayoutMatchesTheAccessoryTableOfTheFlightModel`) |
| `build/FrameType#accessoryMounts` | Whoop 65: nur `BOTTOM` (keine Action-Cam), alle anderen `TOP` + `BOTTOM` |
| `build/DroneBuild` | Record-Feld `accessories` (null → `NONE`), alter 6-Parameter-Konstruktor bleibt (ohne Zubehör). `withAccessory`, `withAccessories`, `withoutAccessories`, `withBattery` (behält das Zubehör – immer statt `new DroneBuild(…)` benutzen, wenn ein Teil getauscht wird). `totalMassGrams()` zählt das Zubehör. Problem `Issue.ACCESSORY_MOUNT` (Zubehör an einer Halterung, die der Rahmen nicht hat → nicht baubar). `DronePresets.key` ignoriert Zubehör (Freestyle mit Kamera bleibt „Freestyle-Drohne“) |

**Speichern und Netzwerk (core):** `DroneBuildCodecs.CODEC` hat das optionale Feld `accessories` (`{"top": "action_cam_heavy", "bottom": "led_strip"}`,
`lenientOptionalFieldOf`, fehlt → `NONE`, wird ohne Zubehör nicht geschrieben; ein ungültiger Eintrag verwirft nur das Zubehör). `STREAM_CODEC`
schickt das Zubehör mit (Halterung und Typ als Key-Strings), deshalb `Constants.PROTOCOL_VERSION` = 3. `DroneBuildCodecs.ACCESSORY`,
`ACCESSORY_MOUNT`, `ACCESSORIES` (+ `_STREAM`) sind öffentlich.

**Bauteil-Kategorie und Werkbank (core, content):** `PartCategory.ACCESSORY` (`accessory`) mit `requiredCount()` 0 und `slotCount()` 2,
`optional()`; `get(build)` liefert das erste Zubehör oder null, `get(build, position)` das Zubehör der Halterung `AccessoryMount.values()[position]`.
`BuildSlots`: Pflicht-Slots 0–11 wie bisher (`REQUIRED_COUNT` 12), Zubehör ab `ACCESSORY` = 12 in Halterungsreihenfolge (`accessorySlot(mount)`,
`mountOf(slot)`), `COUNT` = 14; `slotsFor(part)` (Shift-Klick), `accepts` prüft zusätzlich die Halterung, `check` meldet Zubehör nie als fehlend,
`layout(build)` hat an leeren Zubehör-Slots null. `DroneWorkbenchMenu.PART_SLOTS` wächst mit (Result-/Zerlege-/Inventar-Indizes verschieben sich um 2).
Werkbank: Slot oben links neben dem Rahmen (52, 64, Symbol `propwash:gui/slot/accessory_top`), unten rechts (108, 64, `accessory_bottom`), Tooltip
„Zubehör oben/unten“ mit Erklärung, Warnung, wenn der eingesetzte Rahmen die Halterung nicht hat; Kennwerte-Tooltip mit Zubehör-Masse, Strom
am Flugakku und Kamera-Daten (Aufnahmeformat, Aufnahmezeit mit eigenem Akku bzw. Leistung am Flugakku). `DroneAssembly.disassemble` legt
Zubehör in seine Slots zurück.

**Items, Texte, Überlebensmodus (content):** die drei Typen sind normale `PartItem`s mit `propwash:part` (Stapel 16), Creative-Tab und Flugschule
automatisch. Texte: `part.propwash.<key>`, `propwash.help.<key>.summary`, `propwash.help.category.accessory.*`,
`propwash.content.tooltip.category.accessory` (Argument = Halterung), `propwash.content.mount.accessory.<mount>`, Kamera-/LED-Zeilen
`propwash.content.tooltip.{recording,stabilization,sensor,fov,low_light_noise,own_battery,leds,flight_power}`, Werkbank
`propwash.content.workbench.{slot,slot_hint}.accessory_<mount>`, `…details.{accessory_mass,accessory_power,camera,camera_record_time,camera_flight_battery}`,
`propwash.build.issue.accessory_mount` und `propwash.part_category.accessory` (core). Rezepte in `tools/data/survival.py` (LED-Streifen
Einstieg: Redstone-Fackeln + Kupfernuggets + Platine; Light: Kupfer + Glas + Platinen + HD-Chip; Heavy: Eisen + getöntes Glas + LiPo-Zellen +
HD-Chip + Platine), Truhen: LED-Streifen in Werkzeugschmied, Minen, Verliesen, Prüfungskammern; Light bei Kartograf, Tempeln, Außenposten;
Heavy nur in Festung, Tresoren, antiker Stadt und Endstadt.

**Physik (sim, Spezifikation `docs/design/flight-model-spec.md` §4.2, §4.5, §4.6):** Masse = Summe inkl. Zubehör; jedes Zubehör ist ein
Quader (`widthMm` × `heightMm` × `depthMm`, unten mit Streckung) in `AccessoryAnchor.centerOf` und trägt zu Schwerpunkthöhe und
Trägheitstensor bei (Steiner-Anteil mit Abstand zum Schwerpunkt, auch längs Z – die Kamera sitzt vorn und hoch, also mehr Nick- und Rollträgheit).
`P_aux` += `flightBatteryWatts` (Light-Cam und LEDs leeren den Flugakku etwas schneller), `A_z` += `frontalAreaM2`. Ragt eine obere Kamera über die
Akku-Oberseite, ersetzt ihre höchste Ecke den Hüllpunkt 8 (Kamera zuerst am Boden beim Überschlag, Hitbox wächst mit). `AirframeSpec`
hat zusätzlich `accessoryPower` und `accessoryMassKg`; `BuildAnalysis.massGrams` enthält das Zubehör. Beispiel Freestyle 5″: 525 g → 567 g (Light)
bzw. 675 g (Heavy), Schub/Gewicht 10,6 → 9,9 bzw. 8,3, Topspeed 140 → 135 bzw. 131 km/h, Nick-Trägheit +14 % bzw. +50 %, Roll-Trägheit +3 % bzw. +17 %, Schwerpunkt 22 → 24 bzw. 30 mm.
Obere Anbaupunkte (Gelenk vor dem Kamerakäfig, Kamera ohne Überlappung mit allen passenden Akkus, geprüft von
`tools/assets/parts/validate.py`): Cinewhoop (0; 44,7; −47) 10° (bis zum Release-Kandidaten 0.5.0 vom 08.10.: 30,7), Freestyle (0; 34,7; −69) 22°, Race (0; 26,7; −67) 20°, Long Range (0; 38,7; −68) 20°.

**Rendern (drone-render):** `PartModel.of(accessoryType)` = `propwash:part/accessory/<key>` (Render-Definition
`items/drone_render/accessory/<key>.json`), `PartModel.base(mount)` = `propwash:part/accessory/mount_<mount>` für Halterungen mit Sockel.
`DroneLayout.accessory(mount)`/`accessories()` aus `drone_layout.json` (Rückfall: `AccessoryAnchor`-Tabelle), `DroneAssembly.accessory(mount)` →
`Accessory(type, anchor, body, base, tiltDegrees, stretchX)` (`Accessory.NONE` ohne Zubehör, `present()`), Ersatzformen
`FallbackShapes.accessory(type)`/`accessoryBase(mount)`. `DroneRenderState.accessoryType[]`, `accessories[]`, `accessoryBases[]`, `accessoryTilt[]`,
`accessoryStretch[]` (Index = `AccessoryMount.ordinal()`, `DroneAssembly.MOUNTS` Einträge). `DroneVisuals.submitAccessories` zeichnet den Sockel
ungedreht am Anbaupunkt und das Zubehör mit `translate(anchor) → rotate X(tilt) → scale(stretchX, 1, 1)`; `VisualHull` zählt beides mit (Vorschau-
Rahmen, Aufsetzen in der Welt). Werkbank-Vorschau, Konfigurator-Übersicht (Zeile „Zubehör“), Flugschule und Welt zeigen Zubehör damit automatisch.

**Andockpunkte für die LED-Arbeit:** LED-Positionen und Strahlrichtung im Rahmensystem über `AccessoryAnchor.ledMm(type, i)` und
`ledDirection()` (mit der Drohnen-Orientierung und `DroneRenderState.pivot` in die Welt umrechnen), Zahl über `AccessoryType.ledCount`.
Das Modell hat je LED ein eigenes Element `led` (weiß, ohne Leuchten) – Leuchten/Farbe kommt als eigene Glow-Ebene in
`DroneVisuals.submitAccessories` (wie die Arm-LEDs mit `glowType()`), Licht in der Welt gehört der LED-Arbeit. Die 1,5 W sind ein fester
Mittelwert in `AccessoryType.flightBatteryWatts`; eine helligkeitsabhängige Leistung braucht einen neuen Sim-Eingang. Umgesetzt in „LED-Streifen und FPV-Scheinwerfer“ (unten).

**Andockpunkte für die Action-Cam-Arbeit:** Kameraposition und Blickrichtung über `AccessoryAnchor.lensMm(type)`/`viewDirection()` (Uptilt =
`tiltDegrees` der Halterung), Bildeigenschaften und Aufnahmezeit aus `ActionCamSpec`. Umgesetzt im Abschnitt „Action-Cam-Aufnahme“ unten
(Kamera-Akku als `propwash:camera_charge` an Item, Drohne und Entity; Light am Flugakku).

**Selbsttest:** „content: Zubehör einbauen und zerlegen“ (Slot-Symbole und Texte je Slot, LED-Streifen passt nicht oben, Shift-Klick legt die
Action-Cam in die obere Halterung, Ergebnis-Build mit Zubehör, Zerlegen gibt es zurück, Drohne behält es über `loadFromStack`/`toStack`,
Whoop mit Action-Cam ist nicht baubar) und „drone-render: Zubehör an den Halterungen“ (Modelle und Sockel geladen, Halterungen je Rahmen aus
`drone_layout.json`, jedes passende Zubehör an jedem Preset am Anbaupunkt mit Neigung, Heavy-Cam ragt über die Drohne, Zeichnen ohne
PoseStack-Rest).

### Action-Cam-Aufnahme (video, drone, content, input, audio, osd, guide; Entwurf `docs/design/action-cam-recording.md`)
Die Action-Cam nimmt das saubere FPV-Kamerabild (vor Videoeffekt und OSD) als MP4 (H.264 Constrained Baseline, auf Wunsch mit
AAC-LC-Tonspur des Motorgeräuschs) nach `<Spielverzeichnis>/propwash/recordings/propwash_JJJJ-MM-TT_HH-MM-SS.mp4` auf.
Vorschaubilder liegen in `recordings/.thumbs/<name>.png`. Kein Fremd-Encoder, keine Jar-in-Jar-Abhängigkeit.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `video/record/h264/H264Encoder`, `EncoderSettings`, `EncodedFrame` | video (reines Java) | `new H264Encoder(breite, höhe, fps, settings)` (gerade Maße; kodiert auf 16er-Raster, SPS schneidet zu), `encode(i420, preset)` (Preset 0 Qualität, 1 schnell, 2 schnellste; erstes Bild und alle `gop` Bilder IDR), `repeat()` (Wiederholbild nur aus Skip-Makroblöcken, braucht ein vorheriges Bild), `sps()/pps()` (NAL mit Kopfbyte), `level()`, `frames()`. `EncodedFrame#toSample()` = AVCC (4-Byte-Längen), `toAnnexB()`. `EncoderSettings.of(threads, maxBitrate, fps[, quality])`: Slices = 2 × Threads, GOP 2 s, QP 12–46, Deblocking an (auch über Slice-Grenzen, Wellenfront nach dem Kodieren), `quality` = QP der P-Bilder (Standard `DEFAULT_QUALITY` 23, Schlüsselbilder 2 besser), `adaptiveQuant` = AQ-Stärke (`DEFAULT_AQ` 0,8, 0 = aus), `bitrate` ist die **Obergrenze** (Puffer 1 s), nicht mehr das Ziel; `withQuality(qp, aq)`; der alte 7-Werte-Konstruktor bleibt (Qualität 23, AQ 0,8). `currentQp()`, `bufferFill()` (Füllung des Obergrenzen-Puffers 0…1). Gleiche Eingabe ⇒ gleiche Bytes unabhängig von der Thread-Zahl. Nicht thread-sicher: ein Aufrufer-Thread |
| `video/record/aac/AacEncoder` | video (reines Java) | AAC-LC, mono, nur 48 kHz: `new AacEncoder(48000, bitrate)` (16–256 kbit/s, `DEFAULT_BITRATE` 160 000), `encode(pcm, offset)` (nimmt `FRAME` = 1024 Samples im 16-Bit-Bereich, liefert einen `raw_data_block`), `flush()` (letztes Bild mit Nullen), statisch `audioSpecificConfig()` (`0x11 0x88`). Die Ausgabe ist um `PRIMING` = 1024 Samples verzögert. Tabellen in `AacTables` (ISO/IEC 14496-3, Huffman-Codebücher 1–11, Skalenfaktoren, Bänder 48 kHz). Nicht thread-sicher |
| `video/record/mp4/Mp4Writer`, `Mp4Reader`, `Mp4Info` | video (reines Java) | `new Mp4Writer(pfad, breite, höhe, zeitbasis, sps, pps, name)`, `writeSample(bytes, dauer, sync)`, optional vor den ersten Samples `addAudioTrack(rate, kanäle, asc, bitrate)`, dann `writeAudioSample(bytes, dauer)` und `setAudioPresentation(vorlauf, samples)` (Edit-Liste; ohne Aufruf ganze Spur), `finish()` (schreibt `moov`, schließt), `abort()` (löscht). Die Tonspur trägt `elst` (Vorlauf 1024), `mp4a`/`esds` und eine `roll`-Gruppe (−1), sonst schneidet AVFoundation 2112 Samples ab. `Mp4Reader.read(pfad)` prüft Boxgrößen, Tabellen und NAL-Längen je Spur und liefert `Mp4Info` (Bildspur wie bisher, dazu `hasAudio()`, `audioCodec`, `audioObjectType`, `audioSampleRate`, `audioChannels`, `audioSampleCount`, `audioEditMediaTime`, `audioSeconds()`, `audioConfig`, `trackCount`) |
| `video/record/RecordingFormat`, `FrameClock`, `Stabilizer`, `CamPower`, `FrameQueue`, `RecordingJob`, `FrameStats`, `AudioLane` | video (reines Java) | Formate 720/1080/1440 × 30/60 (16:9, `forCamera(spec, höhe, fps)` kappt auf die Kamera, `bitrate()`), Bildtakt mit Pause, Stabilisierung (`Mode` OFF/SMOOTH/HORIZON), Kamera-Akku (`drain`, `secondsLeft`, `usbCharge`), Warteschlange mit harter Speichergrenze und Auslagerung, Hintergrund-Job (`start(Options)` mit `Options(…, audio, quality)` – ohne `quality` Standard, `quality()`, `acquire/submit/release`, `submitAudio(pcm, von, anzahl)`/`submitSilence(n)` nur während der Aufnahme, `hasAudio()`, `finish(endSlot)`, `cancel()`, `progress()`), Tonspur-Takt `AudioLane` (verwirft den Ton vor dem ersten Bild, füllt am Ende mit Stille auf die Bildlänge auf), Bildstatistik für den Selbsttest |
| `video/record/RecordingQuality`, `CameraLook` | video (reines Java) | Qualitätsstufen `HIGH`/`STANDARD`/`SMALL` (`DEFAULT` = Standard): `qp()` 20/23/27, `maxBitrate(format)` = 1,5/1,0/0,5 × `format.bitrate()`, `typicalBitrate(format)`, `typicalMegabytesPerMinute(format)`, `maxMegabytesPerMinute(format)` (auf 5 MB gerundet), `key()`, `translationKey()` (`propwash.video.record.quality.<key>`). `CameraLook.sharpness(spec)`, `grain(spec, an)` (= `lowLightNoise · 0,025`, aus = 0), `grainWeight(luma)` (= `1 − smoothstep(0; 0,2; luma)`, wie im Shader `post/action_cam_frame`) |
| `video/record/RecordTrigger` | video (reines Java) | `update(art, sitzung, aktiv)` → `START`/`STOP`/`TOGGLE`/`NONE`: `SWITCH` (Achse) = an nimmt auf, aus stoppt (steht der Schalter beim Flugstart schon an, startet die Aufnahme); `BUTTON` (Taster) = jeder Druck schaltet um; `LATCH` (Taster mit „Umschalten“) = jeder Wechsel schaltet um; ohne Sitzung zurückgesetzt |
| `video/record/RecordingLibrary`, `ClipInfo`, `Thumbnail` | video (reines Java) | `new RecordingLibrary(ordner, executor)`: `refresh()` (asynchron), `clips()` (neueste zuerst), `revision()`, `scanned()`, `scanNow()`, `delete(clip)` (nur `*.mp4` direkt im Ordner, samt Vorschaubild), statisch `thumbnailOf(clip)` = `.thumbs/<name>.png`, `pruneThumbnails(ordner)`. `ClipInfo`: Datei, Größe, Änderungszeit, Aufnahmezeit aus dem Namen, Dauer, Maße, fps, Ton, lesbar; `duration(s)`, `size(bytes)`, `format()`. `Thumbnail.fromI420(i420, b, h, 256, 144)` → ARGB (BT.709, begrenzter Bereich, 3×3 Abtastungen je Pixel) |
| `video/client/record/ActionCamRecorder` | video | `INSTANCE`, nur Render-Thread. `start(mc, automatisch)` → `Result` (STARTED, NO_SESSION, NO_CAMERA, NOT_FPV, CAMERA_EMPTY, NO_POWER, …), `stop(mc, StopReason)`, `toggle(mc)`, `phase()` (IDLE/RECORDING/STOPPING), `recording()`, `paused()`, `recordedNanos()`, `format()`, `camera()`, `stabilization()`, `quality()` (Qualitätsstufe der laufenden bzw. letzten Aufnahme), `cameraCharge()`, `current()`/`saving()` (Fortschritt), `savingJobs()` (Datei, Format, Fortschritt), `currentFile()`, `lastSaved()`, `lastError()`, `capturedFrames()`, `audioSubmitted()`, `stats()`, `capturePass()`, `library()`, `cancelAll()`, `overrideFormat(format)` (nur Tests), statisch `directory(mc)`, `cameraOf(drohne)`, `formatFor(spec)`, `stabilizationFor(spec)`, `recordControl()` (Art der `RECORD`-Belegung der aktiven Funke). Meldungen als `ThemedToast` mit Token `TOAST_TOKEN` (Start, Stopp/Abbruch, nicht möglich, gespeichert mit Dauer · Format · Größe, Fehler). Hooks: `onRenderedFrame(main)` (aus `mixin.video.client.GameRendererMixin` vor dem Videoeffekt; nimmt auch die Vorschaubilder beim ersten Bild und nach 1 s), `onClientTick` (`ClientBus.TICK_END`: Taste, Funke über `RecordTrigger`, Beim Scharfschalten, Ton, Akku, Speichern melden), `onDisconnect` |
| `video/client/record/ActionCamCapture` | video | GPU-Teil: Pipelines `propwash:pipeline/action_cam_frame` und `…/action_cam_yuv` (per `ClientRegistrar.renderPipeline` vorkompiliert), I420-Packen auf der GPU, 4 Auslesepuffer, Rückruf über `RenderSystem.queueFencedTask`. `release()` erst ohne offene Auslesungen |
| `video/client/record/RecordingKeys`, `RecordingConfig` (`action_cam.json`), `RecordingHud` (`propwash:action_cam`, WITH_HUD), `ActionCamTab` (Order 530) | video | Taste `key.propwash.action_cam_record` (F9, im Tab „Tasten“ unter „Funktionen“ frei belegbar, auch in `KeyDefaults.OTHER_MODULES`); Config: Höhe, fps, Stabilisierung, `quality` (`RecordingQuality`, Standard „Standard“), `grain` (Kamerarauschen, Standard an), `audio` (Ton aufnehmen, Standard an), Beim Scharfschalten, `showIndicator` (= „Aufnahme-Hinweise“ oben: Pause, Kamera-Akku unter 30 %, Rechner zu langsam; außerhalb des FPV der Speicherfortschritt), Speichergrenze (MB), Encoder-Threads (0 = automatisch). Tab: Abschnitte Aufnahme (Auflösung, Bildrate, Qualität mit ≈ MB/min über die volle Breite – `ActionCamTab.qualityLabel/qualityTooltip`, Stabilisierung, Ton, Beim Scharfschalten, Aufnahme-Hinweise, Kamerarauschen), Aufnahmen („Aufnahmen ansehen …“, „Ordner öffnen“), Status |
| `video/client/record/RecordingsScreen`, `RecordingThumbnails` | video | Theme-Fenster „Aufnahmen“ (`ThemedScreen`, `open(parent)`, ESC/„Fertig“ zurück zum Aufrufer): Liste mit Vorschaubild (16:9, Dauer im Bild), Datum, Dauer · Format · Größe (· mit Ton), Dateiname; oben laufende Aufnahme (Live-Kachel: dunkles 16:9-Feld mit pulsierendem rotem Punkt und „REC“, pausiert zwei Balken und „PAUSE“, beim Beenden „ENDE“; rechts in der Zeile „Stopp“ = `ActionCamRecorder#stop(mc, USER)`) und Speicherfortschritt je Video; Knöpfe „Ordner öffnen“, „Löschen“ (zweiter Klick innerhalb 3 s bestätigt), „▶ Abspielen“ (Systemplayer über `Blaze3D.openPath`), „Fertig“; Pfeiltasten, Pos1/Ende, Enter spielt ab, Entf löscht, Doppelklick spielt ab. Für Selbsttests: `clips()`, `selected()`, `select(pfad)`, `loadedThumbnails()`, `deleteArmed()`, `deleteSelected()`, `frames()`. `RecordingThumbnails.write/move/delete` laufen nacheinander im IO-Pool (`flushed()`), `read(pfad)` lädt ein PNG |
| `audio/SoundReach` | audio (reines Java) | `of(build)` (Cache, Rückfall `REFERENCE` = 5″): Rotor-Pegel `rotorLevelDb` = 80 dB(A) + 10·log10(P/60 W) + 30·log10(M/0,21) aus Wellenleistung `4·kTorque·ω³` und Spitzen-Mach beim Schweben; Quellpegel `levelDb` = Größenkurve `sizeLevelDb(D)` = 80 dB(A) + 18·log10(D/130 mm) plus die halbe Abweichung des Rotor-Pegels, höchstens ±1 dB (`sizedLevelDb`); `rangeForLevel(L)` = 10^((L − 43)/20) Blöcke; `rangeBlocks(amplitude)` = Hörweite × (Lautheit/Schwebe-Lautheit)^0,5, 4–160 Blöcke; `amplitude(rpm, maxRpm, last)` = Lautheitskurve des Synthesizers je Motor; `attenuation(r, R)` = 1 bis 3 Blöcke, dann 3/r, ab R/2 Smoothstep auf 0 bei R; `airCutoffHz(r)` = Luftdämpfung (ISO 9613-1) als Tiefpass 1,8–14 kHz. `MotorControl.setOnboard(false)` schaltet im Synthesizer Fahrtwind und Rumpeln ab und Prop-Wash auf ein Viertel |
| `audio/client/MotorAudioTap` | audio | Abgriff des Motor-Synthesizers vor der Raumwirkung von Minecraft (bei Stimmen von außen mit Abstandspegel und Luftdämpfung aus `SoundReach`, die Action-Cam nimmt nur die FPV-Stimme des Piloten auf): `MotorStream` meldet jedes erzeugte Stück (720 Samples, 48 kHz, mono) mit der Entity-Id seiner Drohne (`MotorSoundInstance(…, sourceId)`, `DroneVoice` setzt sie). `listen(listener)`, `release(listener)`, `listening()`; der Listener läuft im Audio-Pump-Thread und muss schnell sein |
| `osd/OsdElement.RECORDING`, `OsdData.recording/recordingSeconds/recordingPercent`, `osd/client/OsdHud#drewLastFrame(element)` | osd | OSD-Element „Aufnahme (REC)“ (oben rechts innerhalb des Sicherheitsrands: Minimal und Rennen Spalte 28/Zeile 1, Standard 28/2, Voll 28/0 wie der Kompass; in allen Vorlagen an): blinkender roter Punkt + `REC m:ss` in Warnfarbe (`OsdFormat.recordingText`, gleiches Format wie die Aufnahmen-Liste), pausiert zwei Balken in Warnfarbe, beim Speichern `SPEICHERN nn%`; sichtbar nur, wenn aufgenommen oder gespeichert wird. `LiveOsdSource` liest `ActionCamRecorder` |
| `osd/OsdElement.GPS_SATS`, `OsdData.holdMode/posHoldDenied/gpsKnown/gpsSatellites/gpsFix`, `OsdWarnings.Warning.NO_GPS` | osd | ALTHOLD/POSHOLD im OSD: das Flugmodus-Element zeigt `ALTHOLD` bzw. `POSHOLD` statt ANGL (Rescue geht vor, Funk-HUD im Sichtflug genauso), das Element „GPS-Satelliten“ (`SAT n`, ohne Fix in Warnfarbe; Standard 28/11, Voll 28/15, Minimal und Rennen aus) ist sichtbar, sobald ein Flugmodell läuft; die Warnung `KEIN GPS` (Stufe Warnung, blinkt) steht, solange POSHOLD angefordert ist, aber mangels Fix nur die Höhe hält (`Telemetry.posHoldDenied`) |
| `guide/scenes/ActionCamScene` (`action_cam`), `SceneKit.KEY_RECORD` | guide | Flugschul-Szene „Action-Cam: Videos aufnehmen“ (Kamera an den Rahmen, Aufnahme starten, per Schalter oder Taster, Videos ansehen); gehört zu beiden Action-Cams |
| `core/CoreComponents.CAMERA_CHARGE` (`propwash:camera_charge`, Float 0…1) | core | Ladestand des eigenen Kamera-Akkus an Zubehör-Item (Heavy) und Drohnen-Item (fehlt ⇒ voll) |
| `drone/QuadcopterEntity#cameraCharge/setCameraCharge/hasOwnCameraBattery` | drone | synchronisiertes, gespeichertes Feld (`CameraCharge`), `loadFromStack`/`toStack` übertragen es |
| `drone/CameraCharging`, `drone/net/CameraChargeC2S` | drone | Server lädt die Heavy per USB aus dem Flugakku (kein Pilot, entschärft, Flugakku über 15 %, alle 10 Ticks); C2S vom Piloten nur sinkend. **Protokollversion 4** (neues Entity-Feld und Payload) |
| `content/DroneAssembly#cameraCharge(stack)`, `storeCameraCharge(stack, stand)`, `hasOwnCameraBattery(build)`, `createDrone(…, cameraPart)` | content | Bauen übernimmt den Stand vom Zubehör-Item, Zerlegen schreibt ihn zurück; gespeichert wird nur ein nicht voller Stand (quantisiert wie Akkus), volle Kameras bleiben ohne Komponente und stapeln mit neuen; `PartItem.cameraChargeLine` zeigt ihn in Tooltips (Heavy-Item, Drohnen-Item). Teile-Hilfe liest zusätzlich `propwash.help.<teil>.what/use.N` |
| `input/SwitchFunction.RECORD`, `ModesTab`-Zeile `record` | input | Funktion „Aufnahme“: Schalter an = Aufnahme, aus = Stopp; Taster (auch als Umschalter) = jeder Druck startet bzw. stoppt (Auswertung in `ActionCamRecorder` über `RecordTrigger`, nur mit aktiver Pilot-Sitzung) |

**Selbsttest** (`video.client.record.ActionCamSelfTests`, von `VideoSelfTests.register()` angemeldet): Tab mit allen Bedienelementen
(mindestens 10, Qualitätsauswahl zeigt MB/min, Stufen nach Größe geordnet, Kamerarauschen dezent, Light > Heavy, abschaltbar, nur bei
wenig Licht), Aufnahme folgt der eingestellten Qualität und bleibt unter deren Bitraten-Obergrenze,
Taste F9 ohne Konflikt mit einer anderen Standardtaste, Schalterfunktion; Kamera-Akku über Werkbank, Item, `loadFromStack`/`toStack`
und Tooltip; Drohne mit Heavy im FPV; **3 s Aufnahme in 720p30** → Toast „Aufnahme läuft“, REC-Element im OSD gezeichnet, Tonspur
hält Schritt, Toast „Aufnahme gestoppt“, Datei in `propwash/recordings`, `Mp4Reader` liest H.264/avc1 1280×720, Dauer 3 s (±1 Bild),
30 fps, erstes Bild Schlüsselbild, AAC-LC 48 kHz mit Edit-Liste und gleicher Länge wie das Bild (bzw. keine Tonspur bei „Ton aus“),
Bilder von der GPU gelesen, Bild nicht einfarbig, Himmel oben; Toast „Video gespeichert“, Vorschaubild vorhanden, `RecordingLibrary`
kennt Dauer, Format und Ton; „video: Aufnahmen-Liste zeigt das Video mit Vorschaubild und löscht es“ (Fenster öffnen, Video ausgewählt,
Vorschaubild geladen, Löschen fragt nach, zweiter Klick löscht Video und Vorschaubild, Toast); Kamera-Akku sinkt über den C2S;
USB-Laden aus dem Flugakku; nach dem Verlassen Leerlauf, Motorton-Abgriff aus und GPU-Ressourcen frei.
Bildschirmfotos (Rundgang): `action_cam_rec_osd.png` (FPV mit REC im OSD, Drohne mit Heavy) und `action_cam_recordings.png`
(Aufnahmen-Liste mit Vorschaubild, darüber die laufende zweite Aufnahme); der Tab erscheint als `configurator_action_cam.png`.

### Action-Cam (Flashback): Kamerapfad für Flashback (build, video, compat.flashback, content, drone-render, guide; Entwurf `docs/design/flashback-cam.md`)
Dritte Action-Cam für die Halterung oben, die kein Video aufnimmt, sondern den Weg der FPV-Kamera als Szene in der
Replay-Mod Flashback (nur Fabric). Das Item gibt es immer, auf beiden Loadern; ohne Flashback zeigt der Aufnahme-Knopf
nur einen Hinweis.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `build/AccessoryType#ACTION_CAM_FLASHBACK` (`action_cam_flashback`), `FLASHBACK_CAM_ID`, `FLASHBACK_MOD_ID` (`flashback`), `isFlashbackCam()`; `build/Accessories#flashbackCam()` | content | oben, 36 g, 42 × 28 × 24 mm, 3 W am Flugakku, Stirnfläche 0,0012 m², Linse mittig 5 mm; ohne `action_cam`-Block, also `isCamera()` falsch und `Accessories#camera()` leer. Keine Truhen-Beute, Rezept in `tools/data/survival.py` (Uhr, Glasscheiben, HD-Videochip, Kupferbarren, Platine) |
| `video/record/path/*` | video (reines Java) | `CameraPathBuffer#add(PathSample)` (Zeit in Flashback-Ticks, nur steigend und endlich, Quaternionen auf eine Halbkugel, höchstens 2²⁰ Proben), `PathResampler.perTick/at`, `CameraAngles.of(q)` = Gier/Neigung/Roll wie `Camera#setRotation(yaw, pitch)` mit `rotateZ(roll)`, `rotation()`, `EulerPath.continuous` (Keyframe-Fehler ≤ `KEYFRAME_LIMIT_DEGREES` 2°, gegen Verdrehen bei Flips), `CameraTrack.of(buffer, KeyframeDensity)` → Kamera-Keyframes (Augenposition, Winkel) und Sichtfeld-Keyframes (Anfang, Änderungen > 0,05°, Ende); `KeyframeDensity.EVERY_TICK`/`REDUCED` (1 cm, 0,5°, höchstens `MAX_SPAN_TICKS` 10) |
| `compat/flashback/client/FlashbackBridge` | video | nur Reflection, beim ersten `status()` vollständig aufgelöst: `ABSENT` (Mod fehlt), `READY`, `INCOMPATIBLE` (`problem()`); `recorder()`, `recording()`, `paused()`, `writing()` (`Recorder#readyToWrite`), `writtenTicks(recorder)`, `replayId(recorder)`, `inReplay()`, `startRecording()`, `finishRecording()`, `addMarker(rgb, text)`, `writeScene(uuid, FlashbackScene)` → Index der Szene (ersetzt eine leere einzige Szene, setzt sie aktiv; sonst angehängt), `dataDirectory()`, `version()` |
| `compat/flashback/client/ReplayView` | drone-render | `hides(drone, cam, forward)`: nur in einer Flashback-Wiederholung; näher als `CORE_RADIUS` 0,5 Blöcke, oder näher als 0,5 + 2 Ticks Flugweg und nicht vor der Kamera; `QuadcopterRenderer#shouldRender` fragt es nach der eigenen FPV-Drohne ab |
| `video/client/record/FlashbackCam` | video | `INSTANCE`, Render-Thread. `start(mc, automatisch)` → `Result`, `stop(mc, StopReason)`, `toggle(mc)`, `recording()`, `paused()`, `saving()`, `recordedSeconds()`, `samples()`, `replay()`, `lastHandover()` (`Handover`: Aufnahme, Szenenindex, Name, Ticks, Keyframes, beendet, Grund), `lastTrack()`, `lastPath()`, `lastResult()`, `lastError()`, statisch `mounted(drohne)`, `EXPORT_ASPECT` 16:9, `MAX_TICKS` 15 min. Proben in `ClientBus.FRAME_START` (nach `PilotSession`), Zeit = `writtenTicks + Teil-Tick`, nur wenn Flashback schreibt und das Spiel läuft; Pose = Schwerpunkt + Linsen-Offset + Uptilt ohne Videolatenz, Sichtfeld vertikal für 16:9. Stopp rechnet den Pfad im Hintergrund und schreibt die Szene auf dem Render-Thread; hat die Kamera die Flashback-Aufnahme gestartet, beendet sie sie danach. Toasts mit Token `TOAST_TOKEN` |
| `video/client/record/ActionCamRecorder` | video | Taste `action_cam_record` und Funktion `RECORD` gehen an `FlashbackCam`, wenn die geflogene Drohne die Action-Cam (Flashback) trägt; „Beim Scharfschalten“ gilt für sie genauso (`FlashbackCam#onClientTick`) |
| `video/client/record/RecordingConfig` | video | `flashbackAutoRecord` (Standard an: Flashback-Aufnahme mitstarten und beenden), `flashbackDensity` (Standard `EVERY_TICK`) |
| `video/client/record/ActionCamTab`, `RecordingHud`, `osd/client/LiveOsdSource` | video, osd | Abschnitt „Flashback“ mit beiden Einstellungen nur, wenn Flashback installiert ist; Statuszeilen (erkannt mit Version, nicht unterstützt mit Grund, Drohne trägt die Kamera mit Sichtfeld, ohne Flashback, läuft, wartet, zuletzt übergeben); Hinweis um einen Satz erweitert; im Sichtflug oder bei pausiertem Flashback ein Feld „FLASHBACK mm:ss · Kamerapfad“ oben; das OSD-Element REC zeigt den Kamerapfad wie eine Aufnahme |
| `content/PartItem#flashbackLine`, `PartTooltips` | content | Statuszeile immer sichtbar („Flashback erkannt – bereit zur Aufnahme“ grün, sonst „Braucht die Mod Flashback (Fabric)“ gold), Kennwert „Aufnahme: Kamerapfad für Flashback“ |
| `platform/IPlatformHelper#modVersion(modId)` | platform | Version einer anderen Mod (Standard leer; Fabric und NeoForge liefern sie) |
| `guide/scenes/ActionCamScene` | guide | fünfter Schritt `flashback` („Kamerapfad für Flashback“); das Item öffnet die Lektion |

**In Flashback:** Szene „Propwash · Flug N“ (mit Drohnennamen „Propwash · <Name> · Flug N“) mit Spur „FPV-Kamera“
(`CameraKeyframeType`, `SMOOTH`, Position = Auge − `Avatar.DEFAULT_EYE_HEIGHT`) und „FPV-Sichtfeld“ (`FOVKeyframeType`, `HOLD`),
Spurfarbe Propwash-Gelb, Export-Bereich = Flug, Marker „Propwash: Flug N“ am Start. Gespeichert über Flashbacks
`EditorStateManager` in `flashback/editor_states/<Aufnahme-UUID>.json`. Kein Netzwerk, kein Protokoll, keine API.

**Selbsttest** (`video.client.record.FlashbackCamSelfTests`, von `VideoSelfTests` angemeldet; die Wiederholung als letzter
Schritt nach dem Verlassen über `DevClientBootstrap`): Bauteil, Tooltip und Tab mit bzw. ohne Flashback; Drohne im FPV;
ohne Flashback: Taste F9 → Hinweis, keine Aufnahme; mit Flashback (Jar nur zum Testen in `fabric/runs/selftest-client/mods`):
echter Flug 3,5 s mit Taste F9, Flashback-Aufnahme mitgestartet und beendet, Szene aus Flashbacks eigenem Editorzustand
geladen und mit Flashbacks eigener Interpolation gegen den Flug geprüft (genau: höchstens 8 cm zur gleichen Zeit, 3 cm
neben der Bahn, 6°), Aufnahme als Zip gespeichert; drei Flüge in einer selbst gestarteten Aufnahme (ausgedünnt, der
dritte mit Flip: höchstens 25°, Ende der Flashback-Aufnahme als Stoppgrund); Wiederholung in Flashback geöffnet, Kamera
folgt dem Pfad (unter 0,1 Blöcke), Drohne um die Kamera ausgeblendet. Bildschirmfotos (Rundgang):
`drone_closeup_action_cam_flashback.png`, `tooltip_action_cam_flashback.png` und `_de`, `configurator_action_cam_flashback.png`,
`flashback_cam_fpv.png`, `flashback_cam_handed_over.png`, `flashback_replay_fpv.png`, `flashback_replay_path.png`; ohne
Flashback `flashback_cam_needs_flashback.png`.

### LED-Streifen und FPV-Scheinwerfer (drone, drone-render, video, ui, compat.lambdynamiclights)
Baut auf „Zubehör“ auf. Ablauf, Formeln und Grenzen: `docs/design/led-strip.md`.

**Einstellungen je Drohne (drone, reines Java in `drone.led`):**
| Datei | Vertrag |
|---|---|
| `drone/led/LedSettings` | Record `mode`, `color`, `brightnessPercent` (10–100, 5er-Raster), `headlight`. Schlüssel im `FlightTune`: `led.mode`, `led.color` (Ordinalzahlen), `led.brightnessPercent`, `led.headlight` (0/1), `KEYS`. `DEFAULT` = Dauerhaft, Weiß, 100 %, Scheinwerfer an. `of(tune)` (fehlend/ungültig → Standard), `values()`, `sanitize(key, wert)`, `defaultValue(key)`, `isKey`, `strip(build)` → erstes Zubehör mit `isLight()` |
| `drone/led/LedMode`, `LedColor` | Modi `STATIC`, `BLINK`, `ARMED`, `LOW_BATTERY`, `RAINBOW` (`propwash.led.mode.<key>`), 11 Palettenfarben mit `rgb()` (`propwash.led.color.<key>`); `byIndex` klemmt. Ordinalzahlen sind gespeichert: nur hinten anhängen |
| `drone/led/LedAnimator` | `color(settings, index, count, Input)` → ARGB je LED (Alpha = Helligkeit × Modus, 0 = aus), `light(settings, count, Input)` → Lichtfarbe (höchster Kanal 1) + mittlere Stärke, `luminance` → 0–15 (max. 12), `Input(armed, powered, batteryCharge, seconds)`, `powered(akkuDrin, ladung, perSchalterAus)`; Akku-Warnung unter `LOW_BATTERY_CHARGE` = 0,2 |
| `drone/led/DroneLeds` | Brücke zur Entity: `settings(drone)`, `strip(drone)`, `input(drone, sekunden)` (Strom = Akku drin und > 0 und nicht per Schalter aus, `LedAnimator.powered`), `switchedOff(drone)` (Schalter aus und Streifen verbaut), `luminance(drone)` (Zeit = `tickCount / 20`) |
| `drone/TuneMapping` | `isDroneSetting(key)` = Kamera-Uptilt oder LED-Schlüssel; `isKnownKey` nimmt sie an, `effective` gibt gespeicherte LED-Werte bereinigt zurück, `normalize` behält nur Abweichungen vom Standard. `TuneAccess.current()/defaults()` enthalten die LED-Standardwerte |
| `ui/TuneEdits` | `hasOwnTuning` zählt Drohnen-Einstellungen (Uptilt, LED) nicht; `templateFor` behält alle Drohnen-Einstellungen der Drohne |

**Rendern (drone-render):** `DroneRenderState.stripMount` (Ordinalzahl der Halterung, −1 ohne Streifen), `stripLeds`,
`stripGlow[]`/`stripHalo[]` (`FallbackShapes.stripGlows/stripHalos(type)`, je LED), `stripColor[]` (ARGB, bis
`MAX_STRIP_LEDS` = 16). `DroneVisuals.fill` setzt die Formen, `fillFromEntity` die Farben aus `DroneLeds`, `fillPreview` den
Standard; `ledPreviewState(build, settings, armed, sekunden)` liefert einen eigenen Vorschau-State mit Entwurfsfarben.
`submitAccessories` zeichnet im Pose-Stapel des Zubehörs je LED Leuchtquader (`order(1)`) und einen weichen Leuchthof
(`order(2)`, `HALO_ALPHA` 0,6), beides `RenderTypes.eyes` mit voller Helligkeit. Leuchthöfe baut `ShapeBuilder.glow(x, y, z, radius,
innen, mitte, außen)` aus drei gekreuzten, beidseitigen Scheiben mit Farbverlauf (`FallbackShapes.stripHalos`, Radius 7 mm bzw.
1,6 × LED-Abstand; Arm-LEDs `FallbackShapes.ledHalo(frame, layout, slot)` → `DroneAssembly#ledHalo`, `DroneRenderState.ledHalo[]`,
Radius 10 mm). Die Arm-LEDs zeichnen ihren Leuchtquader voll deckend in der LED-Farbe (Helligkeit als
Farbwert, nicht als Alpha, `DroneVisuals.opaque`), damit sie auch in dunklen Höhlen als Lichtpunkte lesbar sind. `client/render/DronePreview.render(…, @Nullable LedSettings leds, boolean armed, double sekunden)`
ist die Vorschau mit animierten LEDs (ohne `leds` wie bisher).

**FPV-Scheinwerfer (video):**
| Datei | Vertrag |
|---|---|
| `video/HeadlightMath` | reines Java: `darkness(licht, gamma)`, `smooth`, `gain(stärke, dunkelheit)` (= 12 × …, unter 0,02 → 0), `attenuation(d)`, `cone(cos)`, `boost`, `lift` (neutrale Aufhellung für Schwarz), `hueTrust(luma)` (Farbe erst ab Luma 0,03–0,14 verstärken), `overlayRadius`, `overlayLayerGain`; Konstanten Kegel 11°/34° mit `CONE_EDGE_POWER` 2 (Rand fällt deutlich ab, auch im Shader und in der Overlay-Kegeltextur), Streulicht 0,02, Reichweite 24, Abfall 7,5 Blöcke, Lambert-Gewicht 0,7; `tint(r, g, b)` = Warmweiß (1; 0,9; 0,74), zu `LED_TINT` 35 % zur LED-Farbe gezogen, höchster Kanal 1; dazu ein heller, wärmerer Hotspot `hotspot(cos)` (bis 1 + `HOTSPOT_BOOST` 1,1 innerhalb von 7°, Übergang bis zum inneren Kegel) und `warmTint(cos)` (Richtung Warmweiß, Rand 0 %, Mitte 50 %); Shader `fpv_headlight.fsh` führt dieselben Werte als Konstanten, das Ersatz-Overlay hat eine hellere Mitte in der Kegeltextur |
| `video/client/Headlight` | `INSTANCE` (Render-Thread, `ClientBus.FRAME_START` nach `VideoState`; aus, wenn `VideoConfig.ledLighting` – persönlich „LED-Ausleuchtung“ im Tab „Video“ – aus ist): `equipped()`, `active()`, `gain()`, `red/green/blue()` (getönte Lichtfarbe aus `HeadlightMath.tint`), `intensity()`, `darkness()`, `rawLight()`, `usesShaderPass()`, `usesOverlay()`. Quelle ist die Drohne des Video-Feeds (`VideoState.source()`, paketintern) |
| `video/client/HeadlightPass` | Pipeline `propwash:pipeline/fpv_headlight` (`InSampler`, `DepthSampler`, UBO `PropwashHeadlight`: inverse Projektion, Licht, Kegel, Reichweite). `captureProjection` und `apply(main)` kommen aus `mixin.video.client.GameRendererMixin` (ModifyArg auf `ProjectionMatrixBuffer#getBuffer(Matrix4f)` und Inject vor `render3dHud` in `GameRenderer#renderLevel`, beide `require = 0`). `fallbackNeeded()` = Video-Pass im Ersatz-Overlay, eigener Fehler, Hook oder Projektion fehlt (Projektion gilt nur mit endlicher Matrix und Inverse); `framesDrawn()`, `hookSeen()`, `release()` bei `DISCONNECT`. Bildprüfung über `HeadlightProbe` (zwei `copyTextureToBuffer` eines 4-Zeilen-Bands vor und nach dem Pass) und `video.HeadlightCheck`; bei Abdunklung `failure()` = `VideoPass.Failure.CHECK`, die `retry()` (Weltwechsel) nicht löscht, nur `retryAll()` (Knopf „Shader erneut versuchen“); `checksPassed()`, `checksFailed()`, `lastCheck()`, `failureLogs()` (jede Abschaltung genau eine Logzeile mit `GpuInfo.describe()`) |
| `video/HeadlightCheck` | reines Java: `compare(vorher, nachher, bildpunkte)` (RGBA8) → `Result(verdict PASS/FAIL/INCONCLUSIVE, pixels, eligible, darkened, brightened)`: zählbar ab 4/255, abgedunkelt bei mehr als 2/255 in einem Kanal, FAIL ab 8 und 1 %, ohne Urteil unter 64 zählbaren Bildpunkten; `bandStart/bandRows`; Zeitplan `frame()`, `due()`, `issue()`, `record()`, `abandon()`, `restart()` (3. Frame, alle 30 Frames bis 3 bestandene, dann alle 600, verlorene Rückmeldung nach 240 Frames frei) |
| `video/client/GpuInfo` | `describe()` = Hersteller / Renderer / Treiber (Backend, Tiefe 0..1 oder -1..1) aus `DeviceInfo`, `logOnce` am ersten `FRAME_START` |
| `video/client/HeadlightOverlay` | Ersatz im HUD (aus `VideoOverlay.extract`): Pipelines `propwash:pipeline/fpv_headlight_boost` (`DST_COLOR, ONE`) und `…_lift` (additiv), Kegeltextur `propwash:video/headlight_cone`; `framesDrawn()` |

**Konfigurator (ui):** `ui/client/tab/LedTab` (Id `led_strip`, Order 520, `Storage.DRONE`) bearbeitet die LED-Schlüssel
über das gemeinsame `TuneModel` (live an der Drohne, sonst Vorlage): Modus, Farbfelder (`ColorRow`), Helligkeit,
Scheinwerfer, Beschreibung des Modus, Hinweis zu LambDynamicLights/Shaderpacks, animierte 3D-Vorschau mit LED-Leiste und
Schalter „Vorschau scharf“. `settings()`/`edit(key, wert)` sind öffentlich (Selbsttest). Texte `propwash.ui.led.*`.

**Gradle/Loader (Foundation-Dateien, für diese Integration geändert):** `gradle.properties` `lambdynamiclights_version`
(4.13.0+26.3) und `yumi_commons_version` (2.0.0); `build-logic/src/main/groovy/multiloader-common.gradle` Repository
`maven.gegy.dev` (nur Gruppe `dev.lambdaurora.lambdynamiclights`) + `mavenCentral()` und `compileOnly` beider Artefakte
(ohne Transitive); `fabric.mod.json` Entrypoint `lambdynlights:initializer`; `neoforge.mods.toml`
`[modproperties."${mod_id}"."yumi:entrypoints"]`. `tools/fastcheck.sh` sieht die API erst nach
`./gradlew writeClasspath` im Hauptbaum.

**Selbsttest:** „drone-render: LED-Streifen leuchtet in Farbe und Modus“ (jede Halterung unten, jeder Modus scharf und
entschärft gegen `LedAnimator`, Leuchten wird gezeichnet, Blinken aus zeichnet nichts), „drone-render: LED-Einstellungen in
der Drohne gespeichert und sichtbar“ (Server-Bereinigung, `toStack`/`loadFromStack`, Lichtstärke, Sync und Welt-Farben),
„ui: LED-Tab bearbeitet …“, „video: FPV-Scheinwerfer bleibt bei Tag aus und hellt im Dunkeln auf“ (geschlossener
Steinraum, Bildschirmfoto aus/an, Helligkeit im Kegel mindestens 1,5 × + 3, dazu der Overlay-Weg mit Einstellung
„Overlay“ mindestens 1,3 × + 2), „video: FPV-Scheinwerfer bei Nacht im Freien hellt nur auf, prüft sein Bild und lässt
sich abschalten“ (`HeadlightNightSelfTests`: Mitternacht, flache Welt mit Himmel, HUD aus; Raster 32 × 18 aus LEDs aus,
an, „LED-Ausleuchtung“ aus, NaN-Projektion und schwarzem Eingangsbild; nichts dunkler als ohne Licht, Himmel gleich,
Bildprüfung besteht mit Licht und NaN, schaltet beim schwarzen Bild mit genau einer Logzeile ab, Overlay übernimmt) und
nach dem Verlassen „video: FPV-Scheinwerfer nach dem Verlassen zurückgesetzt“. Bildschirmfotos `headlight_night_off.png`
und `headlight_night.png` (`HeadlightShowcase`: FPV um Mitternacht von einer Drohne am Rand einer zwei Blöcke hohen
Säule auf eine Ruinenwand, LEDs aus und an, gleiche Lage).
Bildschirmfotos: `headlight_cave_off.png` und `headlight_cave.png` (FPV-Flug in einer geschlossenen Höhle 26 Blöcke
westlich der Szene, ohne und mit Scheinwerfer, beide scharf im Schwebeflug aus **derselben** Lage: die Szene ohne
Licht steigt auf 1,9 Blöcke und setzt die Drohne dann auf eine feste Position mit Blick in die Höhle, die Szene mit Licht
schaltet nur den Scheinwerfer ein und setzt die Drohne scharf an dieselbe Stelle; vorher fiel die Drohne zwischen den
Aufnahmen unscharf zu Boden und „Cannot arm“ stand im Bild). Dazu `drone_leds_cave.png` (scharfe Drohne mit
LED-Streifen, Cyan, im Sichtflug in der dunklen Höhle von schräg unten aus 2,5 Blöcken Abstand, damit die Leuchthöfe groß im Bild sind), `drone_closeup_action_cam_light.png`,
`drone_closeup_action_cam_heavy.png`, `drone_closeup_damaged_props.png` (alle drei Schadensstufen an allen vier Props: vorne links 32 % beschädigt, vorne rechts kaputt, hinten links 62 % und hinten rechts 55 % angeknackst) und
`drone_condition.png` (Tooltip ohne Shift mit Zustandszeile derselben Drohne, passt auf jede GUI-Größe).

Flugmeldungen der Pilot-Sitzung (Scharfschalten blockiert, Failsafe, Flugmodus, Umdrehen, keine Funke) erscheinen als
Propwash-Meldung (`ThemedToast`, Token `PilotSession.TOAST_TOKEN`) statt als roter Vanilla-Text über der Hotbar. Im
FPV entfallen „Scharfschalten blockiert“ und „Failsafe“ ganz, weil das OSD beides schon als Warnung zeigt
(`OsdWarnings`: ARM SWITCH ON, THROTTLE NOT LOW, FAILSAFE …).

### LED-Schalter an der Funke (input, drone, drone-render, video, osd, ui; Ablauf in `docs/design/led-strip.md`)
Funktion „LEDs aus“ (`SwitchFunction.LEDS_OFF`, wie LED_LOW bei Betaflight): aktiv = LED-Streifen, Leuchten in der Welt,
LambDynamicLights-Licht und FPV-Scheinwerfer aus. Nicht belegt ⇒ nie aktiv ⇒ die LEDs leuchten wie im Tab „LED-Streifen“
eingestellt. Belegung wie jede Schalterfunktion (Modi-Tab, Empfänger-Assistent, AUX-Bereich, Taste, Invertiert, Umschalter);
Gamepad-Tasten schalten um und starten mit LEDs an. Es gibt keine Vorbelegung in `ProfileDefaults`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `input/client/KeyDefaults.LEDS`, `InputKeys.LEDS`, `FunctionKeys` | input | Taste `key.propwash.drone_leds` (F8, Gruppe „Drohne“ im Tab „Tasten“), Umschalter nur während einer Pilot-Sitzung, siehe `RadioState` in §11 |
| `input/client/TransmitterInput#keySwitchBits()` | input | Bits der Tasten-Umschalter (`BEEPER`, `LEDS_OFF`), per ODER in jedes veröffentlichte `RadioFrame` |
| `input/MfiHint`, `input/client/MacControllers`, `InputConfig#appleGameController`, `TransmitterInput#mfiPlan()`/`mfiHintBeforeInit()`/`backendStarts()` | input | macOS: vor `SDL_InitSubSystem(JOYSTICK \| GAMEPAD)` setzt `TransmitterInput` `SDL_JOYSTICK_MFI=0` (Option aus, Standard) bzw. setzt ihn zurück (Option an); eine Umgebungsvariable `SDL_JOYSTICK_MFI` gewinnt und sperrt die Option. Geänderte Option startet das Subsystem neu (nie in einer Pilot-Sitzung). Kein Gerät gefunden: eine Log-Zeile je Start mit System, SDL-Version und Hinweis. Option nur auf macOS im Tab „Empfänger“ (`MacControllers.optionShown`, Selbsttest-Schalter `overrideOptionShown`); Hinweis des Assistenten `MacControllers#noDeviceHint`. Fehlersuche `docs/input-troubleshooting.md` |
| `input/client/TransmitterInput#simulate(SimulatedRadio)`, `simulating()`, `input/client/SimulatedRadio` | input | Nur für Selbsttests: ersetzt jedes Gerät durch eine simulierte Funke (Profil, `setAxis`/`setMicros`, `setButton`), die denselben `SwitchEvaluator` und `StickMapper` wie echte Geräte durchläuft (Quelle `JOYSTICK`); `simulate(null)` beendet das |
| `drone/QuadcopterEntity#ledsOff()`, `setLedsOff(boolean)`, `applyLocalState(…, ledsOff)` | drone | synchronisiertes Feld (nicht gespeichert). Der Client des Piloten setzt es lokal über die neue Überladung von `applyLocalState` (die alte behält den lokalen Wert), der Server übernimmt es aus `DroneStateC2S#ledsOff` und setzt es bei **jedem** Pilotenwechsel (auch `setPilot(null)`) auf false: nach dem Flug leuchten die LEDs wieder wie eingestellt |
| `drone/net/DroneStateC2S#ledsOff` | drone | neues letztes Feld; **Protokollversion 5** |
| `drone/client/PilotSession#ledsOff()` | drone | `RadioState#isActive(LEDS_OFF)` des aktuellen Frames (auch im Selbsttest-Knüppelmodus); eine Änderung schickt den Flugzustand sofort statt erst im nächsten Sende-Intervall |
| `osd/OsdElement.LEDS_OFF`, `OsdData#ledsOff` | osd | OSD-Element „LEDs aus (Schalter)“: `LED AUS` in Elementfarbe, nur sichtbar, solange die LEDs per Schalter aus sind und ein Streifen verbaut ist (`DroneLeds.switchedOff`). Grundlage Spalte 1/Zeile 5 (Voll), Minimal/Standard/Rennen Spalte 1/Zeile 3 (unter LQ und VID), in allen Vorlagen an, im OSD-Editor wie jedes Element aus- und verschiebbar |
| `ui/client/tab/LedTab` | ui | dritter Absatz der Beschreibung: Schalter „LEDs aus“ und Taste (`propwash.ui.led.switch_hint`) |

Texte: `propwash.input.switch.leds_off`, `key.propwash.drone_leds`, `propwash.input.leds.{on,off,held_off,no_strip}`,
`propwash.input.modes.{effect,desc}.leds_off`, `propwash.osd.element.leds_off`, `propwash.osd.label.leds_off`,
`propwash.ui.led.switch_hint`, `propwash.help.led_strip.use.3/4`.

JUnit: `input.LedSwitchTest` (Zuordnung, AUX-Bereich aus dem Modi-Tab, Invertiert, 3-Stufen-Schalter, unlesbar = LEDs an,
Taster halten/umschalten, Gamepad, Speichern), `drone.led.LedAnimatorTest#switchOnTheRadioCutsThePowerOfTheWholeStrip`,
`drone.net.DroneStateC2STest`, `KeyDefaultsTest#ledSwitchHasItsOwnDroneKeyNextToTheActionCam`,
`OsdPresetTest#ledIndicatorSitsUnderTheLinkValuesAndCanBeHidden`. Selbsttest „drone: LED-Schalter an der Funke schaltet
Streifen, Scheinwerfer und Server-Zustand“ (`drone.client.LedSwitchSelfTests`, nach dem Spielablauf): Drohne mit
LED-Streifen im FPV, simulierte Funke mit „LEDs aus“ auf AUX 5 (1700–2100 µs) → Schalter unten an, oben aus, invertiert
umgekehrt, als Taster-Umschalter rastend; danach Taste F8 aus/an; je Stellung `RadioState`, Sitzung, Entity, Strom des
Streifens, Leuchten im Render-State, Scheinwerfer-Stärke, OSD-Element und Server-Entity (inkl. Lichtstärke) geprüft; das
Lösen des Piloten setzt die LEDs auf dem Server und dem Client wieder an, der Tasten-Zustand endet mit der Sitzung.

### Lackierung (content, drone, drone-render, ui-Theme; Assets `tools/assets/parts/paintable.py`)
Jede Drohne kann jedes sichtbare Teil umfärben. Eine Drohne ohne Lackierung ist genau die alte Drohne (Originalmodelle,
Originaltexturen); alte Items, Entities und Speicherstände laden unverändert.

**Datenmodell (reines Java in `build`, Besitzer content):**
| Datei | Vertrag |
|---|---|
| `build/PaintSlot` | Zwölf Lack-Plätze mit stabilem `key()`: `frame`, `tpu` (Armschützer, Heckhalter, Canopy, Ducts, Kamerasockel, Abstandshalter), `motors`, `prop_fl`, `prop_fr`, `prop_rl`, `prop_rr` (Positionen wie `PartWear`/Werkbank), `stack`, `battery` (Akku-Hülle), `camera`, `antenna` (Antennenspitzen), `accessories`. `channel()`, `isProp()`, `propPosition()`, `prop(i)`, `of(channel, propPosition)`, `byKey` |
| `build/PaintChannel` | Tint-Kanäle der Modelle (`frame`, `tpu`, `motor`, `prop`, `stack`, `battery`, `camera`, `antenna`, `accessory`) mit Referenz-Grau `reference()` (200, 242, 208, 224, 200, 200, 232, 230, 200). `tint(rgb)` = je Kanal `min(255, (c·255 + ref/2) / ref)`: die Lack-Textur speichert die Grundfarbe als Grau `ref`, der Tint macht daraus wieder die Wunschfarbe; Lichter darüber bleiben erhalten (weiches Knie ab `max(ref, 229,5)`). Dieselbe Formel und Tabelle steht in `paintable.py` (JUnit `PaintAssetsTest`) |
| `build/DronePaint` | Record über `Map<PaintSlot, Integer>` (RGB, unveränderlich, `EnumMap`-Reihenfolge); fehlender Eintrag = Originalfarbe des Teils. `DEFAULT`, `of`, `isDefault`, `has`, `color(slot)`, `color(channel, propPosition)`, `with`, `without`, `withAllProps`, `withoutProps`, `count`; Zwischenablage `toText()`/`fromText()` (`propwash-paint:frame=#2a2c30,prop_fl=#fed83d`, höchstens 512 Zeichen, sonst leer), `hex`, `parseHex` (`#rgb`, `#rrggbb`, ohne `#`) |
| `build/PaintSwatch` | Palette: die 16 Vanilla-Farbstoffe (Farbwerte = `DyeColor` „texture diffuse“) und fünf Oberflächen `carbon`, `gunmetal` (Titan), `aluminium`, `gold`, `copper`, jede mit dem Farbstoff, den sie kostet (Carbon schwarz, Titan grau, Aluminium hellgrau, Gold gelb, Kupfer orange). `dyes()`, `finishes()`, `byColor`, `byDye`, `nearestDye`, `distance` |
| `build/PaintCost` | Kosten eines Wechsels `of(alt, neu)`: je Platz mit neuer Farbe 1 Farbstoff (Palette/Oberfläche: ihr Farbstoff, eigene Farbe: ein beliebiger), zurück zur Originalfarbe kostenlos, unveränderte Plätze gratis. `dyes()` (Farbstoff-Name → Anzahl), `custom()`, `changes()`, `free()`, `total()`, `plan(vorrat)` = welche Farbstoffe verbraucht werden (eigene Farben nehmen den ähnlichsten vorhandenen) oder leer, wenn etwas fehlt |
| `build/PaintPreset` | Vorlagen `carbon_classic`, `neon`, `race_team`, `arctic`; jede färbt alle zwölf Plätze mit Palettenfarben (vorne und hinten verschiedene Props) |

**Speichern, Items, Netzwerk (content):** `content/paint/PaintComponents.DRONE_PAINT` = Data-Component `propwash:drone_paint`
(`PaintCodecs.CODEC`: `{"frame": "#2a2c30", "prop_fl": "#fed83d"}`, unbekannte Plätze und kaputte Farben werden übersprungen, auch Zahlen
als Farbe erlaubt; `STREAM_CODEC`: 1 Byte Anzahl, je Eintrag Platz-Ordinal + 3 Byte RGB), Entity-Data-Serializer `propwash:drone_paint`
(`DRONE_PAINT_SERIALIZER`), angemeldet in `ContentModule.init` über `PaintService.init`. `content/paint/PaintItems`: `paint(stack)`,
`store(stack, paint)` – auf dem Drohnen-Item zusätzlich `minecraft:custom_model_data` mit 12 Icon-Tints (`iconTints`, ungesetzte Plätze aus
`ICON_DEFAULT_TINTS`, JUnit gegen `items/drone.json`); Standard-Lackierung entfernt beide Komponenten. Die Lackierung haftet am **Rahmen**:
`DroneAssembly.disassemble` legt sie auf den Rahmen-Teil, `createDrone` übernimmt sie vom Rahmen. `content/paint/PaintC2S`
(`propwash:workbench_paint`, Container-Id + Lackierung) → `PaintService.onPaint`: nur mit offenem `DroneWorkbenchMenu` derselben Id und
`stillValid`, dann `DroneWorkbenchMenu#applyPaint(player, paint)` → `PaintService.Result` `APPLIED`, `UNCHANGED`, `NO_FRAME`,
`MISSING_DYES`. Bezahlt wird aus dem Inventar ohne Rüstung (Items im Tag `minecraft:dyes` mit `minecraft:dye`), `hasInfiniteMaterials()`
(Kreativmodus) zahlt nichts; danach Rahmen-Item neu gesetzt (Ergebnis-Drohne folgt), Geräusch `DYE_USE`. `DroneWorkbenchMenu#frame()`,
`#paint()`. `Constants.PROTOCOL_VERSION` = 6.

**Drohne (drone):** `QuadcopterEntity#paint()`/`setPaint(DronePaint)` synchronisiert (Serializer oben, alle Tracker sehen die Farben),
gespeichert als `Paint` (nur wenn nicht Standard; fehlt → Standard). `loadFromStack` übernimmt `propwash:drone_paint` (fehlt → Standard),
`toStack` schreibt sie über `PaintItems.store` (samt Icon-Tints). Tooltips: Drohne „Lackiert (N Teile)“, Rahmen „Trägt eine Lackierung“.

**Rendern (drone-render):** Jedes Teilmodell mit lackierbaren Flächen hat eine Lack-Variante `propwash:part/<kategorie>/<key>_paint`
(Render-Definition `items/drone_render/<kategorie>/<key>_paint.json` mit `tints` = `minecraft:custom_model_data` je Tint-Ebene, Standard =
Tint der Originalfarbe). `assets/propwash/drone_paint.json` nennt je Modell (`<kategorie>/<key>`, auch `_ccw`, `_blur`, `_worn`, `_damaged`, `_broken`) die
Tint-Ebenen als `{channel, base}` in Reihenfolge der `tintindex`. `DroneRenderResources#paintGroups(model)` liest sie beim Reload,
`#paintedItem(model, tints)` löst die Variante mit einem Träger-Stack (Papier + `custom_model_data`-Farben) auf und cacht bis zu 512
Kombinationen. `DroneAssembly.Part#model()` nennt das benutzte Modell. `DroneRenderState#paint`; `DroneVisuals.fill` ersetzt für jeden Teil,
dessen Kanäle lackiert sind, das Item durch die Variante (`paint`, `paintTints`): ungesetzte Kanäle desselben Modells bekommen den Tint ihrer
Originalfarbe, Teile ohne gesetzten Kanal bleiben beim Originalmodell. Props: Kanal `prop` mit der Farbe der eigenen Position, auch für
Blur-Scheibe und beschädigte Props. `fillFromEntity` nimmt `entity.paint()`, `previewState(build, wear, paint)`, `fillPreview(…, paint)`,
`client/render/DronePreview.render(g, build, wear, paint, …)` (alte Signaturen zeigen Standardfarben) und `DronePreview.rotation(yaw)`.
Werkbank-, Wartungs- und Übersichts-Vorschau zeigen die Lackierung. Drohnen-Icon: `items/drone.json` wählt per
`minecraft:has_component` `propwash:drone_paint` zwischen `propwash:item/drone` und `propwash:item/drone_paint` (Tint 0–11 = `PaintSlot`,
weitere Ebenen fest). `drone/client/render/PaintPicker`: `view(build, wear, paint, Rechteck, yaw)` (gleiche Projektion wie `DronePreview`),
`pick(view, x, y)` (Strahl durch die Vorschau: Motoren, dann Prop-Scheiben, dann das vorderste von Zubehör/Kamera/Akku/Stack/Sockel, zuletzt
Rahmen), `anchors(view, slot)` + `project` (Markierungen), `available(build)` (Plätze, die der Build hat), `defaults(build)` (Originalfarben je
Platz).

**UI (content):** `content/client/DroneWorkbenchScreen`: Knopf „Lackierung“ oben rechts (aktiv mit Rahmen im Slot und leerem Cursor),
`openPaint()`. `content/client/paint/PaintScreen` (`ThemedScreen`, kein Container-Screen; das Menü bleibt offen und synchron, „Zurück“/ESC
kehrt zur Werkbank zurück, schließt sich, wenn der Server das Menü schließt): Teileliste mit Farbfeldern (nicht verbaute Teile gesperrt,
geänderte mit Akzentstrich), 3D-Vorschau (Ziehen dreht, Klick wählt, Markierung am gewählten Teil), Farbstoffe, Oberflächen, Hex-Feld,
„Teil auf Standard“, „Auf alle Props“, Kopieren/Einfügen (auch Strg/Cmd+C/V), Vorlagen, „Alles Standard“, Kostenzeile (rot, wenn Farbstoffe
fehlen), „Übernehmen“ nur bei Änderungen, vorhandenem Rahmen und bezahlbar. Öffentlich für Selbsttests: `draft`, `setDraft`, `select`,
`paintSelected`, `resetSelected`, `applyToAllProps`, `applyPreset`, `copy`, `paste`, `apply`, `cost`, `costLine`, `canApply`, `changed`,
`availableSlots`, `pickAt`, `previewBuild`.

**Selbsttest** (`content.client.PaintSelfTests`, angemeldet am Ende von `ContentSelfTests.register`): Texte, Item-Komponente samt Icon-Tints,
NBT-Rundreise, Zerlegen/Zusammenbauen über den Rahmen, Entity `loadFromStack`/`toStack`, alte Drohne = Standard; Kosten im Überlebensmodus
(ein Farbstoff zu wenig → nichts passiert, genau zwei rote für zwei Teile, eigene Farbe nimmt den ähnlichsten, Zurücksetzen gratis, Kreativmodus
gratis); Screen im Theme, verfügbare Plätze, Klick-Auswahl für Motoren und Props, Entwurf, Kopieren/Einfügen, Übernehmen per Payload, Vorschau
mit Lack-Modellen, Ergebnis-Drohne mit Lackierung nach „Zurück“; Welt-Drohne: Client sieht die Lackierung, Render-State nutzt Varianten,
Umlackieren kommt an; alle Lack-Modelle lösen auf. Bildschirmfotos `paint_screen.png`, `drone_closeup_paint_neon.png`,
`drone_closeup_paint_race_team.png`. JUnit: `DronePaintTest`, `PaintCostTest`, `PaintCodecsTest`, `PaintAssetsTest`.

### Rennkurse in der Welt (race, `race.world`, Entwurf `docs/design/world-courses.md`)
| Datei | Besitzer | Vertrag |
|---|---|---|
| `tools/data/world_courses.py` | race | **einzige Quelle** für die NBT-Vorlagen `data/propwash/structure/world_course/*.nbt`, die Strukturen `worldgen/structure/<variante>.json`, das Set `worldgen/structure_set/world_courses.json`, die Tags `tags/worldgen/structure/world_courses.json` und `tags/worldgen/biome/has_structure/<variante>.json` sowie die Beute `loot_table/chests/world_course/*.json`. Nie von Hand ändern; `--check` prüft Stand, Blöcke/Biome gegen `.ref/vanilla`, Gates (frei, fester Grund, freier Anflug), Böden ohne Baum-/Kakteenwuchs an Gates und die Beute. Nutzt die Beute-Helfer aus `survival.py` |
| `race/world/WorldCourseStructure` (`WorldCourses.STRUCTURE_TYPE`, Typ `propwash:world_course`) | race | Struktur aus einer Vorlage: `template`, `biome_templates` (Vorlage je Biom der Startmitte), `max_slope`, `chance`. Sucht in der Chunk-Mitte, dreht zufällig, verlangt an 9 Messpunkten passendes Biom, kein Wasser und höchstens `max_slope` Höhenunterschied (`WorldCourseSite.acceptable`), setzt die Vorlage auf die mittlere Höhe (Boden ersetzt die oberste Schicht). Liefert nichts, solange `ServerRules.worldCourses()` false ist |
| `race/world/WorldCoursePlacement` (`WorldCourses.PLACEMENT`, Platzierung `propwash:world_course`) | race | `random_spread` mit zusätzlicher Liste `avoid` (`other_set`, `chunk_count` 1–16): kein Kurs, wenn im Umkreis ein möglicher Platz des anderen Sets liegt (`ChunkGeneratorStructureState#hasStructureChunkInRange`, wie Vanillas `exclusion_zone`, aber für mehrere Sets). Unterklasse von `RandomSpreadStructurePlacement`, damit `/locate` den schnellen Raster-Weg nimmt |
| `race/world/WorldCoursePiece` (`WorldCourses.PIECE`, `propwash:world_course`) | race | Template-Piece mit Drehung (`Rot`), Drehpunkt 0, ohne Spiegelung |
| `race/world/WorldCourseMarker` (NBT `WorldCourse` an `GateEntity`) | race | `Variant`, `Slot` (Platz im Kurs), `Local`/`LocalYaw` (Lage in der Vorlage), `Board` (Tafel in der Vorlage). `GateEntity#worldCourse()`, `setWorldCourse`, `clearWorldCourse`; nicht synchronisiert |
| `race/world/WorldCourseSite` (reines Java) | race | Rechnet aus Weltlage und Vorlagenlage eines Gates Ursprung und Vierteldrehungen der Vorlage zurück (Formeln wie `StructureTemplate.transform` mit Drehpunkt 0, JUnit gegen Vanilla); `key()` = Ursprung `x,y,z` ist der Fundort-Schlüssel |
| `race/world/WorldCourses#adopt`, `CourseRegistry#worldCourse/createWorldCourse/insertGate/refreshBoard` | race | Ein Gate mit Marker, das noch zu keinem Kurs gehört, übernimmt beim Reconcile-Takt seinen Fundort: erstes Gate legt einen besitzerlosen Kurs an (`Course.worldCourse` = Variante, `site`, `board`), jedes weitere reiht sich nach `GateRecord.slot` ein (Chunks laden in beliebiger Reihenfolge); danach wird der Marker gelöscht. `GateEntity#ignoreExplosion` = true |
| `race/world/WorldCourseBoard` | race | Text der gewachsten Tafel: Name, `Kurs #id`, Rekord (bester Eintrag aller Klassen, `Leaderboard#record(null)`, `RaceFormat.time`) bzw. „Noch kein Rekord“, Rekordhalter. Aktualisiert bei Übernahme, Kursrekord, Zurücksetzen und Archivieren der Bestenliste; ist der Chunk nicht geladen, holt der nächste Reconcile eines Kurs-Gates es nach (`Course.boardDirty`, transient, nach dem Laden gesetzt) |
| `server/ServerRulesConfig#worldCourses`, `ServerRules.worldCourses()` | server (von race benutzt) | Server-Regel `worldCourses` (Standard `true`), nur in `server.json` und `/propwash rules`, nicht im S2C (Clients brauchen sie nicht, Protokoll unverändert) |

Selbsttest (`race.client.WorldCourseSelfTests`, nach „race: Aufräumen“): Strukturen, Tag `#propwash:world_courses`,
Vorlagen und Set geladen; Suche im echten Overworld-Generator (Seed 20260924) findet jede Variante wie `/locate` und misst
aus dem Anteil der Zellen mit Kurs (441 Zellen) den mittleren Abstand (1000–4400 Blöcke, gemessen etwa 3000), `worldCourses=false` verhindert die Erzeugung; jede Vorlage wird 6 Blöcke neben dem Spieler (innerhalb der Simulationsweite)
gesetzt (Drehung reihum), die Gates werden zum Kurs in Vorlagen-Reihenfolge, stehen auf festem Grund mit freier Öffnung, die
Tafel zeigt Name und Nummer, beim ersten Kurs zusätzlich Explosion (Gate bleibt) und Testrunde (Tafel zeigt den Rekord);
danach wird alles wieder abgeräumt. Die Beutetabellen werden je 600-mal gewürfelt.

### Videokanäle und Zuschauen (video, drone, server, osd, input, content)
Wie am Rennplatz: Jeder Videosender (VTX) funkt auf einem Kanal, eine passende Brille kann auf den Kanal eines anderen
Piloten schalten und sieht dessen Kamerabild – ohne Steuerung, mit den eigenen Bildstörungen je nach eigenem Abstand zur
Drohne. Keine Störungen zwischen Kanälen (bewusst nicht simuliert).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `video/VideoChannels` (reines Java, JUnit `VideoChannelsTest`) | video | Kanaltabellen: analog 16 Kanäle R1–R8 (Raceband 5658–5917 MHz), F1–F8 (Fatshark 5740–5880 MHz; R7 und F8 teilen 5880 MHz), digital CH1–CH8; `CREATIVE` nutzt die Analog-Tabelle (`tableOf`). `label`, `frequencyMhz`, `count`, `clamp` (unter 0 ⇒ `UNASSIGNED` = −1), `pickFree(system, bevorzugt, belegt)` (bevorzugter Kanal, wenn seine **Frequenz** frei ist, sonst die Auto-Reihe: analog R1, R2, F2, F4, R7, R8, dann Rest; digital CH1–CH8), `assignAll(system, bevorzugt, fest)` (feste zuerst, dann automatische in Reihenfolge, nie zwei automatische auf derselben Frequenz, zwei feste dürfen gleich sein) |
| `video/VtxSettings` | video | Kanal im `FlightTune` der Drohne (wie die LED-Einstellungen): `vtx.channel` (Index in der Tabelle des Systems der Drohne, fehlt ⇒ noch keiner), `vtx.manual` (1 = fest, fehlt ⇒ automatisch). `channel(tune, system)`, `manual`, `label`, `with(tune, kanal, fest)`, `auto(tune)`, `sanitize`, `copyClean`. `drone/TuneMapping#isDroneSetting` zählt beide Schlüssel (bleiben beim Tunen, in Vorlagen und über `toStack`/`loadFromStack` an der Drohne) |
| `video/SpectateServer` | video | Server: aktive Sender = Drohnen mit Pilot und Akku mit Ladung > 0 (`transmitting`); bei jedem `PILOT_CHANGED` mit Pilot und alle 10 Ticks `assignChannels` je Tabelle (setzt `vtx.channel` per `setTune`, nur wenn sich der Kanal ändert). Alle 10 Ticks je Spieler `ChannelListS2C` mit den Sendern seiner Dimension (nur bei Änderung). `SpectateC2S` startet/beendet; geprüft wird `check(spieler, drohne)`: Server-Regel, Sender aktiv, gleiche Dimension, Spieler steuert selbst keine Drohne, getragene Brille (`ContentIds.wornGogglesSystem`) empfängt das System (`VideoSystem#receives`). Jeden Tick erneut geprüft, sonst Ende mit Grund per `SpectateS2C`. Nur für Selbsttests: `simulateTransmitter(drohne, pilotName)` (Drohne ohne Pilot gilt als Sender), `refreshSoon()` |
| `server/SpectatorLinks` | server | Sicht des Zuschauers: `watch(spieler, drohne)`/`unwatch` (von `SpectateServer`), `RangeManager` wendet jeden Tick an: `ViewAnchors.set(spieler, drohne, sicht, drohnen-Chunk)` (Sicht = `droneViewDistance` des Zuschauers, höchstens die zugeteilte des verankerten Piloten), Tickets `drone_view` + `drone` um die Drohne nur, wenn der Pilot (bzw. ohne Pilot der Zuschauer) Chunks erzeugen darf, `pilot_anchor` um den Körper des Zuschauers; beim Ende Anker lösen und `move(spieler)` |
| `video/net/ChannelListS2C`, `SpectateC2S`, `SpectateS2C` | video | Kanalliste (`allowed`, Einträge mit Drohnen-UUID, Entity-Id, System, Kanal, fest, Pilot-UUID, Pilotname, Drohnenname, Rahmen-Key; höchstens 64), Anfrage (UUID oder leer = beenden), Antwort (`STARTED` mit Entity-Id oder Endgrund `STOPPED`, `NOT_FOUND`, `RULE_OFF`, `NO_GOGGLES`, `WRONG_SYSTEM`, `PILOTING`, `SIGNAL_ENDED`, `OTHER_DIMENSION`; Text `propwash.video.spectate.result.<grund>`). **Protokollversion 6** |
| `video/client/SpectateSession` | video | Client, `INSTANCE`, Render-Thread: `phase()` (`IDLE`, `REQUESTED`, `CONNECTING` – Entity noch nicht im Client-Level, höchstens 200 Ticks –, `WATCHING`), `active()`, `watching()`, `drone()` (beobachtete Drohne, auch während eines Kanalwechsels), `channels()`, `watchable(mc)` (fremde Kanäle, die die getragene Brille empfängt, sortiert nach System und Kanal), `cycle(mc)` (Taste: nächster Kanal, nach dem letzten Ende), `request(mc, eintrag)`, `stop(mc, anServer)`, `consumeEscape()`. Beim Zuschauen: Kamera-Entity = Drohne, Körper eingefroren (`FpvView.holdBody`), Posen der interpolierten Drohne (`getPosition`/`renderAttitude` beim `FRAME_START`, Sprünge über 8 Blöcke schneiden) in `VideoState`, Tempo aus den Posen fürs OSD. Beim Zuschauen zeichnen die eigenen Helfer des Zuschauers nicht: Heimatmarke (`rescue.client.HomeMarker`) und Ideallinie (`RaceLineRenderer.active()`) sind aus, Gates leuchten klar (Start-Gate grün, sonst weiß, kein Pulsieren des Start-Hinweises) |
| `video/client/SpectateKeys` | video | Taste `key.propwash.video_spectate` (F7, `KeyDefaults.VIDEO_SPECTATE`, Gruppe „Funktionen“ im Tab „Tasten“) |
| `mixin/video/client/KeyboardHandlerMixin` | video | `@WrapWithCondition` auf `Minecraft#pauseGame` in `KeyboardHandler#keyPress`: Esc beendet das Zuschauen statt das Pausenmenü zu öffnen |
| `video/client/SpectateHud` (`propwash:spectate`, WITH_HUD), `SpectateConfig` (`spectate.json`: `showPilotOsd`, `showBanner`), `ChannelTab` (Id `video_channel`, Order 505, `Storage.MIXED`) | video | Einblendung auf einer Theme-Tafel (`ThemeDraw.notice`, Amber-Streifen) oben mittig unter den oberen OSD-Zeilen (Oberkante = OSD-Zeile 3, `SpectateHud.bannerTop`): „R1 · 5658 MHz · Pilot · Drohne“, darunter 6 s lang die Tasten in `TEXT_MUTED`; beim Suchen „Suche R1 …“ auf derselben Tafel. Tab: Abschnitt „Videosender der Drohne“ (Kanal Automatisch/fest über `TuneModel`, nur mit Drohne in der Hand oder im Flug), Abschnitt „Zuschauen“ (Brillen-Status, eine Zeile je aktivem Kanal – Klick speichert, schließt den Konfigurator und schaut zu, eigene und unpassende gesperrt, unpassende mit Grund am Zeilenende
„braucht digitale/analoge Brille“, Teile mit „ · “ getrennt –, „Zuschauen beenden“, Schalter OSD des Piloten und Einblendung) |
| `drone/client/FpvView#active()`, `spectating()`, `holdBody`, `cameraPose` | drone | `active()` = FPV **oder** Zuschauen (Hand aus, Körper sichtbar, Bildschirm-Effekte des Körpers aus, Frustum-Refresh); `cameraPose` nimmt beim Zuschauen `VideoState.sampleDisplayed(SpectateSession.frameNanos())` + Linsenversatz und Uptilt der Drohne (`CameraMount.uptilt`). `PilotView.active()` gilt auch beim Zuschauen (Maus/Interaktion gesperrt, Fadenkreuz aus); Rechtsklick beendet das Zuschauen |
| `video/client/VideoState` | video | Quelle ist beim Zuschauen die beobachtete Drohne (nach der eigenen FPV-Drohne, vor der Testquelle); `fpvFov` gilt auch beim Zuschauen. Abstand und Hindernisse rechnet VideoState wie immer vom Kopf des eigenen Spielers |
| `osd/OsdElement.VTX_CHANNEL`, `OsdData#vtxChannel`, `OsdData#spectating` | osd | OSD-Element „Videokanal (VTX)“ (`VTX R1`, Grundlage Spalte 1/Zeile 6, Minimal/Standard/Rennen 1/5, in allen Vorlagen an). `LiveOsdSource` füllt beim Zuschauen (wenn `showPilotOsd`) aus der Entity: scharf, Akku (Ladung, Spannung aus `Batteries.openCircuitVoltage`, Zellspannung, verbrauchte mAh), Tempo aus `SpectateSession`, Lage, Name, Kanal; Funk des Piloten gilt als verbunden (LQ 100), Knüppel mittig, keine Rennen/Rescue/Aufnahme |
| `ui/client/config/ConfigTab#mixedStorageKey()` | ui | Sprachschlüssel der Fußzeile für Tabs mit `Storage.MIXED` (Standard `propwash.ui.footer.mixed`, der Name-Text des Tabs „Konfiguration“); der Tab „Videokanal“ meldet `propwash.video.channel.footer` |
| `server/ServerRulesConfig#allowSpectating`, `ServerRulesS2C#spectating`, `ServerRules.spectatingAllowed()`, `ClientServerRules#spectating()` | server | Server-Regel „Zuschauen“ (Standard an), Zeile im Server-Tab und in `/propwash rules` |

Nicht umgesetzt: ein Monitor-Block, der einen Kanal als Bild in der Welt zeigt (bräuchte je Kanal ein eigenes
Rendern der Welt in eine Textur – zu teuer für den Nutzen).

Selbsttest (`video.client.SpectateSelfTests`, von `VideoSelfTests` angemeldet): „video: Videokanäle werden automatisch ohne
Doppelung vergeben“ (drei simulierte Sender R1, R2, CH1; ein fester R2 lässt den automatischen ausweichen; Liste kommt am
Client an) und „video: Zuschauen – Taste wählt einen Fremdkanal, Kamera, Bild und Chunks folgen der Drohne“ (Analog-Brille,
F7 wählt den Analog-Kanal, nicht den digitalen; Kamera-Entity und Kamera an der Drohne, VideoState/FOV/OSD/Einblendung aktiv,
Server kennt Zuschauer und Anker; Kamera folgt einer bewegten Drohne; die Drohne fliegt 150 Blöcke weg – über die
Sichtweite hinaus –, Client hat den Chunk, Server hält ihn, Signal sinkt nach dem Abstand des Zuschauers; direkte Anfrage
auf den Digital-Kanal lehnt der Server ab; zweimal F7 bei einem Kanal beendet; echtes Esc beendet ohne Pausenmenü und löst
den Anker; Kanal-Tab zeigt zwei Kanäle, einer wählbar; Server-Regel aus sperrt Taste und Anfrage). Nach dem Verlassen: kein
Zuschauen, leere Liste. Bildschirmfotos: `configurator_video_channel_live.png` (Tab mit zwei aktiven Kanälen) und
`spectate_view.png` (Zuschauen am Gate-Kurs mit OSD des Piloten und Einblendung).

### Geister-Rennen, Bestenliste und Startaufstellung (race)
- **Geisterspur** `race.GhostTrack` (reines Java): feste Rate `INTERVAL_NANOS` = 50 ms, höchstens `MAX_SAMPLES` (10 min),
  Position der Drohnen-Entity (Füße) und Lage (Welt ← Körper) je Probe, dazu Rundenzeit, Drohnenklasse (`FrameType#key`),
  Layout (`CourseData#layoutHash`: Gate-Index, Position auf 1 cm, Yaw auf 0,1°, Größe) und optional das Aussehen
  `race.GhostLook` (Bauplan + Lackierung der Drohne, die die Runde geflogen ist; `look()`, `withLook`, `withLap` behält es). `resample` baut die Spur aus
  unregelmäßigen Rohproben (lineare Position, Slerp), `pose` spielt ab (Catmull-Rom, Slerp), `splits(gates)` rechnet die
  Zwischenzeiten aus den Gate-Durchflügen der Spur (Index 0 = Rundenzeit).
- **Kompression** `race.GhostCodec` (JUnit `GhostCodecTest`, `GhostLookTest`): Kopf `PWG1`, Version (2; Version 1 ohne
  Aussehen wird weiter gelesen), Klasse, Layout, Aussehen (VarLong-Länge + `GhostLook#encode`, 0 = keins), Rundenzeit, Abstand,
  Anzahl, Ursprung; Positionen auf 1/64 Block quantisiert, je Achse mit Vorhersage zweiter Ordnung als ZigZag-VarLong;
  Lage je Komponente auf 1/2047 mit Vorzeichen-Kontinuität als Differenz; alles durch Deflate. Etwa 2–5 Byte je Probe,
  höchstens `MAX_ENCODED_BYTES` (512 KiB); kaputte Daten werfen `IOException`.
- **Aufnahme** `race.GhostRecorder`: Rohproben höchstens alle 10 ms, außerhalb einer Runde nur die letzte Sekunde;
  `onLapStart`/`onLapComplete` (Client, Runden-Takt aus `LapTracker`), `cut`/`trim` (Server, beliebiges Fenster).
  Der Client nimmt die Drohne jeden Frame auf, bevor er die Rennereignisse verteilt.
- **Persönlicher Geist** (`race.client.GhostLibrary`, `GhostStore`): Bestrunde je Welt/Server, Kurs und Klasse unter
  `<spiel>/propwash/ghosts/<sp_ordner|mp_adresse>/course_<id>_<klasse>.pwghost`; gilt nur bei gleichem Layout. Runden mit
  Flughilfe (Höhe/Position halten, Rescue, veränderte Physik) werden nie zum normalen Geist; mit der Welt-Regel
  `propwash:save_assisted_laps` zum eigenen Geist mit Flughilfe unter `…/assisted/course_<id>_<klasse>.pwghost`, der in
  Runden mit Flughilfe (eingeordnet beim Rundenstart) Gegner und Split-Referenz ist und violett fliegt
  (`docs/design/assisted-laps.md`). Geister fliegen nur im Rennen mit,
  Zeitachse = Rundenzeit ohne Strafe; persönlich blau, Rekord gold (`RaceConfig.ghost`/`recordGhost`).
- **Aussehen des Geists** `race.GhostLook` (reines Java): eigenes kleines Format (Version 1, sechs Teil-Schlüssel, Zubehör-Schlüssel,
  Lackierung als `DronePaint#toText`); unbekannte Teile oder kaputte Daten ergeben kein Aussehen, die Spur bleibt lesbar. Der Client
  hängt beim Rundenabschluss den Bauplan und die Lackierung der geflogenen Drohne an (`ClientRaceTracker`), der Server beim
  Rekord-Geist die der gesteuerten Drohne (`RaceServer.storeGhost`). Gezeichnet wird der Geist mit dem echten Drohnenmodell
  (`drone.client.render.DroneGhost`, durchscheinend, persönlich bläulich, Rekord golden, immer voll beleuchtet, Props als Scheibe).
  Alte Spuren ohne Aussehen (`GhostRenderer.lookFor`): Bauplan der gerade geflogenen Drohne, wenn sie zur Klasse passt, sonst
  das Preset der Klasse (`GhostLook.preset`), sonst FREESTYLE.
- **Bestenliste** `race.Leaderboard` (reines Java, JUnit `LeaderboardTest`) in `race.RaceRecords` (SavedData
  `propwash:race_records`, NBT unter `data/propwash/race_records.dat`, `DataFixTypes` null – beide Loader überspringen dann
  die Datenkorrektur): je Kurs die Bestzeit je Pilot und Klasse (höchstens 50 je Klasse), Rundenzähler, Rennbestzeiten je
  Rundenzahl und bis zu fünf archivierte Layouts. Ordnung: Zeit, dann früheres Datum, dann Name. `submit` liefert
  `COURSE_RECORD` (schneller als alle), `CLASS_RECORD` (schnellste der Klasse), `PERSONAL_BEST` oder `NONE`. Die alte
  Bestenliste aus `propwash_race.json` wird beim Laden einmal übernommen (Klasse leer = „Unbekannt“). Kurse und Gates
  bleiben in `propwash_race.json`.
- **Gewertete Runden:** nur `LapVerifier`-bestätigte Runden, nie mit `RaceReportC2S.FLAG_ASSISTED` (Client meldet
  Flughilfe, die Gründe stehen ab Bit 1 in `flags`, `assistMask()`) oder einem Grund, den der Server selbst sieht
  (`RaceServer#serverAssist`: veränderte Welt-Physik oder `physicsModified`, Kreativ-Drohne, Teile nur ohne „Teile müssen
  zusammenpassen“). Der Server wertet je Runde (Gründe seit der letzten angenommenen Runde plus Bericht), das Rennen gilt
  als gestützt, sobald eine Runde es war. Mit `propwash:save_assisted_laps` landen solche Runden und Rennen in der eigenen
  Wertung `Leaderboard#submitAssisted`/`submitAssistedRace` (Gründe als Maske, nie Rekord), sonst nirgends.
  Rekord (Kurs oder Klasse) → der Server schneidet seine eigene Spur der Runde aus den Bewegungspaketen
  (`SERVER_MOVED`) und speichert sie in `race.RecordGhosts` (SavedData `propwash:race_ghosts`, je Kurs und Klasse,
  Revision). Layout-Änderung archiviert die Liste und löscht die Rekord-Geister des Kurses.
- **Payloads** (Protokoll 6): `RaceReportC2S` + `flags`, `penaltyNanos`; `LeaderboardRequestC2S` → `LeaderboardS2C`
  (Kurs, Layout, Gates, `open` öffnet das Fenster, gewertete Runden, Zeilen = Top 10 je Klasse + eigene Zeiten mit
  Klassen- und Gesamtplatz, seit 0.5.0 mit `assist` je Zeile; Zeilen mit Flughilfe nur bei Regel an, mit eigenen Plätzen); `GhostRequestC2S(kurs, klasse, bekannte Revision)` → `GhostS2C` (Revision 0 = kein
  Geist, leere Daten = unverändert); `LobbyC2S(JOIN|LEAVE, kurs, Sekunden)`, `LobbyS2C`, `RaceStartS2C(kurs, Countdown,
  Piloten)`. Anfragen je Spieler höchstens 4/s.
- **Startaufstellung** `race.RaceLobby` (JUnit `RaceLobbyTest`): Taste „Rennen neu starten“ oder Scharfschalten
  (`RaceConfig.countdown`) nahe am Start → `LobbyC2S JOIN` (ohne Server-Kanal: lokaler Countdown). Aufstellen nur mit
  gesteuerter Drohne höchstens `RaceServer.LOBBY_RANGE` (32 Blöcke) vom Start-Gate; der Start folgt, sobald alle Piloten
  am Start aufgestellt sind (frühestens 0,5 s nach dem Öffnen) oder spätestens nach 10 s; Countdown = Wunsch des Ersten,
  3–10 s. Alle bekommen `RaceStartS2C` gleichzeitig. Mehrere Piloten → „Lauf“: Zielreihenfolge im Chat.
- **Startampel und Frühstart:** `race.StartLights` (3–5 Lampen, jede Sekunde eine rote mehr, bei LOS alle grün für 1 s);
  das Start-Gate leuchtet im Countdown rot, nach Frühstart gelb. Vorwärts durchs Start-Gate vor LOS = `FALSE_START`
  mit `LapTracker.FALSE_START_PENALTY_NANOS` (3 s) auf Runde 1 und Gesamtzeit; der Holeshot gilt dann als geflogen.
  Bewegung ohne Durchflug ist kein Frühstart mehr. Der Server prüft die Strafe (nur 0 oder 3 s in Runde 1) und die Runde
  ohne sie, Runde 1 darf bis 10 s vor der Startmeldung beginnen.
- **Bestenlisten-Fenster** `race.client.LeaderboardScreen` (`ThemedScreen`): Kurs wechseln (‹ ›), Filter „Alle“ und je
  Klasse, Spalten Platz (Gold/Silber/Bronze), Pilot, Zeit, Abstand, Klasse, Datum, eigene Zeile markiert (unter der Liste,
  wenn nicht in den Top 10). Öffnen: Kurs-Werkzeug Rechtsklick in die Luft (Server schickt `open`), Tab „Rennen“ →
  „Bestenliste öffnen“, `/propwash race leaderboard` bleibt als Chat-Ausgabe (mit Klasse und Datum).
- Selbsttest (`race.client.RaceSelfTests`): „Geist der Bestrunde spielt ab“ (Geist aus der Testrunde, Start am
  Start-Gate, Splits an Gate 2/3, Kodieren und Platte), „Bestenliste und Plausibilität“ (zusätzlich Klasse, Datum,
  Rekord-Geist des Servers, Flughilfe ausgeschlossen, erfundene Strafe abgelehnt), „Bestenliste sortiert Klassen und
  Zeiten“, „Bestenliste im Theme-Fenster“, „Startaufstellung, Ampel und Frühstart“.
- Bildschirmfotos (Rundgang, `dev.client.ShowcaseTour`): `race_ghost.png` (blauer Geist fliegt vor dem Start-Gate durch den Kurs,
  Geisterspur aus der Tour ohne Speichern), `race_ghost_whoop.png` (dieselbe Spur als lackierter Whoop-Geist, die Drohne des
  Piloten ist für die Szene ein Whoop, Sichtfeld 35), `race_start_lights.png` (stehender Start, Drohne vor dem Start-Gate, Ampel mit roten
  Lampen, Gate rot), `race_start_go.png` (dieselbe Aufstellung bei LOS, alle Lampen grün), `leaderboard_screen.png`
  (Bestenliste mit neun Zeiten in fünf Klassen, eigene Zeile markiert), `trick_popup_fpv.png` (echter Flip im FPV-Flug nach
  dem Rennen, Trick-Meldung im OSD) und `trick_chain_fpv.png` (Juicy Flick direkt in eine Rolle, gemeinsame Ketten-Meldung).

### Freestyle-Tricks (trick, osd, sim; Entwurf `docs/design/freestyle-tricks.md`)
Neues Modul `trick`: Common-Einstieg `trick.TrickModule` (leer), Client-Einstieg `trick.client.TrickClient` (in
`PropwashClient.init()` **vor** `OsdClient`, damit der Frame-Listener nach `PilotSession.onFrame` und vor dem OSD-Sampler
läuft). Keine Payloads, keine Registereinträge, keine Tasten; alles läuft auf dem Client des Piloten.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `trick/TrickSample`, `TrickDetector`, `TrickScore`, `TrickSession`, `Trick`, `TrickType`, `ProbeAxes`, `ManeuverLog`, `Stroke`, `ManeuverClassifier` | trick (reines Java) | Erkennung und Wertung aus einem Zustand je Frame (Zeit, Position, Geschwindigkeit, Lage, armed, `crashCount`, `impactCount`, `grounded`, Gas, `assisted`, vier Abstände quer zur Flugrichtung). Seit 0.5.0 zeichnet `TrickDetector` jedes Manöver in ein Fenster (`ManeuverLog`), `ManeuverClassifier` zerlegt es in Drehstriche (`Stroke`) und gruppiert sie nach höchster Wertung; Dauer-Tricks (Sturzflug, Orbit, Knife Edge, Gap, Proximity) laufen daneben. `Trick(type, time, magnitude, minClearance, start)` (der alte Konstruktor ohne `start` bleibt). `TrickType.REPERTOIRE` = Reihenfolge im Tab, `descriptionKey()` = Erklärung, `scaled()` = Punkte „ab“. `TrickSession#update(sample)`; `score()` liefert Popup (`Popup` NONE/TRICK/COMBO/LOST, Typ, bis zu drei Namen einer Kette `popupTypeCount()`/`popupType(i)`, Punkte, Kombo, `popupChain()`, Alter in Sim-Sekunden), `flightScore()`, `bestCombo()`, `bestComboPoints()`, `chain()`, `bestChain()`. Schwellen und Punkte in `docs/design/freestyle-tricks.md` |
| `trick/TrickMode`, `TrickConfig`, `trick/client/TrickClient.CONFIG` (`tricks.json`) | trick | `AUTO` (an bei Freestyle 5″, Whoop, Cinewhoop), `ON`, `OFF`; `TrickMode#enabledFor(DroneBuild)` |
| `trick/client/TrickTracker.INSTANCE` | trick | `ClientBus.FRAME_START`: füllt das Sample aus `PilotSession.sim()`, Rescue und laufendes Rennen gelten als `assisted`, Abstände per `ClearanceProbe` (vier Strahlen à 3 m, `COLLIDER`). `active()` = Sitzung läuft und Erkennung für die Drohne an, `session()` |
| `trick/client/TrickConfigTab` | trick | Tab „Freestyle“ (`id` `tricks`, Reihenfolge 610, Spieleinstellung): Modus als Theme-Zyklus, Live-Stand im Flug, alle Tricks mit Punkten in bis zu drei Spalten (scrollbar) und Erklärung als Tooltip, Regeln für Kombo und Kette |
| `osd/OsdElement.TRICKS`, `OsdData#applyTricks/clearTricks`, `OsdData.trick*` (seit 0.5.0 `trickTypes`/`trickTypeCount` für Ketten), `FlightStats#tricksOn/trickScore/bestCombo/bestComboPoints` | osd | Popup unter der Bildmitte (15/13, an in Minimal, Standard, Voll; aus in Race), 2,5 s sichtbar mit Ausblenden; `LiveOsdSource` übernimmt die Werte, solange `TrickTracker.active()`. Flugstatistik mit „TRICK-PUNKTE“ und „BESTE KOMBO“ nur, wenn die Erkennung im Flug an war |
| `sim/Telemetry#impactCount` | sim | monotone Zahl gemeldeter Stöße ab 1 m/s (Spec 10.1) |

Texte: `propwash.trick.*` (Modul `trick`), `propwash.osd.element.tricks`, `propwash.osd.trick.*`,
`propwash.osd.stats.{trick_score,best_combo}` (osd), Lektion `propwash.guide.scene.tricks.*` (guide). JUnit:
`trick.TrickRepertoireTest` (Referenz-Flugbahnen jedes Tricks mit Varianten und Nicht-Treffern), `trick.TrickSimTest` (echte Sim),
`trick.TrickKinematicTest`, `trick.TrickScoreTest`, `trick.TrickTypeTest`, `osd.OsdTricksTest`. Selbsttest
`trick.client.TrickSelfTests` (nach osd): Anmeldung und Übersetzungen, Tab mit Moduswahl (ohne die Config zu schreiben), Flip
einer echten `DroneSim` über eine OSD-Testquelle mit gezeichnetem Popup und Trick-Punkten in der Flugstatistik, Kette aus Flip
und Rolle mit gemeinsamer Meldung, Repertoire-Tab (Spalten, Erklärungen, Scrollen), Zustand nach dem Verlassen.
Flugschul-Lektion `guide.scenes.TricksScene` (`tricks`, nach „Gates und Rennen“).

### Ideallinie und Gate-Markierungen (race, assets-world; Entwurf `docs/design/race-line.md`)
| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/RaceLine` | race (reines Java) | `of(LapTracker, vorausGates, x, y, z)` → `Path(ahead, behind)` nur solange `racing()`, sonst `Path.EMPTY`; `approach(LapTracker, vorausGates, x, y, z)` → vor dem Start (Phase `IDLE` oder `COUNTDOWN`, höchstens `APPROACH_RANGE` = 48 Blöcke vom Start-Gate) dieselbe Linie mit dem Start-Gate als nächstem Gate, sonst leer; `build(...)` Hermite-Abschnitte durch die Gate-Mitten in Kursreihenfolge, Tangente = Gate-Normale; `Sample(x, y, z, distance, gate, alpha, highlight)` mit `gate` = Position in Gate-Längen relativ zum nächsten Gate (0 = Mitte des nächsten Gates, negativ = davor); `remainingGates`, `passedGates`, `order`, `clampAhead` (1–8), `smooth`, `highlight`; weiter als `OFF_COURSE_DISTANCE` (16 Blöcke) vom Kurs (`distanceToCourse`: Abstand zur geschlossenen Linie durch die Gate-Mitten) ⇒ `Path.EMPTY`, damit keine Linienstücke z. B. in Höhlen neben dem Kurs erscheinen |
| `race/LapTracker#holeshot()` | race | true, solange nach dem Countdown der Holeshot durch Gate 1 noch aussteht (nur im Rennen) |
| `race/client/RaceLineRenderer` | race | `active()` (Einstellung an, Rennen läuft und kein Zuschauen – `FpvView.spectating()`), `currentPath(partialTick)` (im Rennen `RaceLine.of`, sonst mit laufender Pilot-Sitzung `RaceLine.approach`), `sight()`/`targetInSight(level, anker, pfad)` (Raycast vom Anker zur Mitte des nächsten Gates; verdecken Blöcke das Gate, blendet die ganze Linie in 0,2 s aus, damit sie nie durch Fels führt), `anchor(partialTick)` (Drohne der Pilot-Sitzung, sonst letzte an `ClientRaceTracker` gemeldete Position, `ClientRaceTracker#lastPosition()`), `framesDrawn()`/`lastSamples()` (Selbsttest); zeichnet an `ClientBus.SUBMIT_WORLD_GEOMETRY` mit `RenderTypes.eyes` |
| `race/client/RaceConfig#raceLine`, `#raceLineGates` | race | Tab „Rennen“, Spalte „Rennen“: Schalter „Ideallinie“ (Standard an) und Regler „Linie: nächste N Gates“ (Standard 3) |
| `race/client/GateRenderer.Marking`, `marking(gate)` | race | `NONE` (kein Kurs), `START` (Kursindex 0), `NUMBER`; Render-State `marking`, `number`, `label`, `startModelled`, `startFrame`, `banner`. Modell-Ids `startFrameModelId(size)`, `bannerModelId(size)` (Item-Definitionen `items/race/gate_<größe>_{start_frame,banner}.json`) |
| `models/gate/<größe>_start_frame`, `models/gate/<größe>_banner`, `textures/item/gate/{start_frame,banner}.png` | assets-world (`tools/assets/world/generate.py`) | karierter Rahmen gleicher Form wie `_frame`; Banner 1 Block hoch über dem Gate (Modell-Ursprung = Oberkante des Gates), Breite = Außenbreite, Tiefe 1/8 Block (`docs/ASSETS.md` „Gates“) |

Flugrichtung eines Gates ist seine Normale; nur Durchflüge in diese Richtung zählen (Client `LapTracker` und Server
`RaceServer`), die Pfeile am Boden und die Linie zeigen dieselbe Richtung. Vor dem Start leuchtet im eigenen, gewählten Kurs das Start-Gate grün (`GateRenderer.lightColor`, Phase `IDLE`), alle anderen weiß. Selbsttests „race: Gate-Markierungen folgen der
Nummerierung“ und „race: Ideallinie nur im gestarteten Rennen“ (vor dem Start und im Countdown führt sie höchstens zum Start-Gate) (`race.client.RaceLineSelfTests`); Bildschirmfotos
`race_gates.png` (vor dem Start, Drohne vor dem grünen Start-Gate, Linie durch das Start-Gate), `gate_start_finish.png`, `gate_markings.png`, `gate_exit_side.png`, `race_line_third_person.png`, `race_line_fpv.png`.

### Gates stapeln: Leitern und Doppel-Gates (race)
Gestapelte Gates sehen aus wie **eine** Leiter aus Rohr (Vorbild: echte Drohnen-Leitern mit durchgehenden Seitenpfosten
und T-Stücken an den Querbalken): Das obere Gate sitzt **um eine Rahmenstärke tiefer** auf dem unteren
(`Unterkante oben = Oberkante unten − frame`), der obere Balken des unteren Gates ist damit der gemeinsame Mittelbalken,
das obere Gate hat keinen eigenen unteren Balken und keine Füße, seine Pfosten laufen gerade weiter (gleich breite Gates)
bzw. stehen mittig auf dem Balken (schmaleres Gate). Das unterste Gate eines Stapels steht auf Beinen (kein unterer
Balken, Füße bleiben). Einzelne Gates sehen aus wie bisher.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/GateStacking` | race (reines Java, JUnit `GateStackingTest`) | `seat(unten)` = `y + height − frame` (Unterkante des Gates darauf), `sameColumn(a, b)` (gleiche Mitte x/z ± 0,001, gleiche Achse, also gleiche oder entgegengesetzte Richtung), `restsOn(oben, unten)` (zusätzlich `oben.y = seat(unten)`), `fits(breiteOben, breiteUnten)` (oben nie breiter), `stackYaw(yawUnten, blickSpieler)` (Blick zurück ⇒ um 180° gedreht, sonst wie das Gate darunter), `stackOn(oberstes, größe, yaw)`, `check(oberstes, stapelhöhe, größe)` → `NONE`/`TOO_WIDE`/`TOO_HIGH` (`MAX_STACK` = 4), `column`/`top`/`above`/`below`/`base` auf einer Liste von `GateGeometry`, `afterRemoval(gates, index)` → `Drop(index, y)` für alle Gates darüber. `Base`: `BAR` (einzeln, mit unterem Balken), `LEGS` (unterstes Gate eines Stapels), `STACKED` (steht auf einem Gate); `Base.of(getragen, belegt)` |
| `race/GateGeometry#boxes(sink, base)` | race | Kollisionsboxen je Rolle: `BAR` wie bisher (4 Boxen), `LEGS` ohne unteren Balken, `STACKED` ohne unteren Balken und mit Pfosten ab `frame` (der Bereich darunter ist der Balken des Gates darunter). `boxes(sink)` = `BAR`. Der Durchflug-Test (`insideOpening`, `up > frame`) ist für alle Rollen gleich |
| `race/GateEntity#base()`, `covered()`, `supported()`, `refreshStack()`, `refreshColumn()`, `gateAbove()`, `gateBelow()`, `stack()` | race | `stack()` = ganze Säule von unten nach oben (Weltabfrage). `covered`/`supported`/`base` sind nicht synchronisiert: Server **und** Client rechnen sie beim ersten Tick und danach alle 5 Ticks aus den (synchronisierten) Positionen nach; Stapeln und Nachrutschen aktualisieren die Säule auf dem Server sofort. `colliders()` folgt `base()` (Kollision wie gezeichnet). Wird ein Gate zerstört (`RemovalReason#shouldDestroy`, also Abbauen, `/kill`, `discard`), rutschen alle Gates darüber auf dem Server um seine Höhe minus Rahmenstärke nach (`setPos` + `needsSync`, Kurs-Eintrag sofort über `CourseRegistry#reconcile`); Kursplatz, Nummer und Richtung bleiben. Bewusst Nachrutschen statt Zerfallen: der Kurs bleibt vollständig und niemand verliert Items |
| `race/GateItem#stackOn(ziel, spieler, stack)`, `GateItem.free(level, geometrie, base, körper)` | race | Rechtsklick mit einem Gate-Item auf ein beliebiges Gate der Säule (`GateEntity#interact`, beide Seiten) setzt es auf das oberste Gate. Abgelehnt mit Meldung: breiter als das Gate darunter (`propwash.race.gate.stack_too_wide`), Säule voll (`…stack_too_high`), Blöcke, Weltgrenze, Rahmen anderer Gates oder Spieler/Drohnen im neuen Rahmen (`…blocked`). `free` prüft beim normalen Aufstellen auch Rahmen anderer Gates (kein Gate ineinander) |
| `race/RaceLine#needsDetour(a, b)`, `detourPoint(a, b)` | race | Zwei aufeinanderfolgende Kurs-Gates mit gleicher Richtung, deren Mitten fast in derselben Ebene und seitlich überlappend liegen (Doppel-Gate, Leiter ohne Zickzack): die Linie läuft über einen Zwischenpunkt `max(Breite)/2 + 2` Blöcke neben der Säule (Ebene wird außerhalb der Pfosten gekreuzt), mit Kehren vor und hinter der Säule. Gegenläufige Leitern (Zickzack) brauchen keinen Umweg |
| `race/client/GateRenderer#frameModelId/lightsModelId/startFrameModelId(size, base)`, `variantSuffix(base)`, `chevronFloor(size, base)`, `plateCenterX(size, covered)`, `plateBottom(size, covered)`, `GateRenderState#covered`, `#base` | race | Modell je Rolle (`items/race/gate_<size>_{frame,lights,start_frame}[_legs|_stacked]`, `docs/ASSETS.md` „Gates“); Maßstab und Versatz kommen immer vom `BAR`-Rahmen. Steht ein Gate auf diesem (`covered`), sitzt die Nummern-Tafel seitlich außen am Pfosten (1/16 Block Luft, Oberkante = Oberkante des Gates), das Start-Gate zeigt statt des Banners eine solche Tafel mit „START“ (`propwash.race.gate.start_short`), die roten Eckmarken der Rückseite entfallen (die Ecken sind dann T-Stücke des Gates darüber, die rote Leiste unter der Tafel bleibt), das Namensschild beim Kurs-Werkzeug rückt unter den oberen Balken. Die Bodenpfeile eines getragenen Gates schweben auf Höhe der Balken-Oberkante vor seiner Einflugseite |
| `guide/client/render/SceneView`, `SceneGateVisual#prepare(gate, actor, base, covered)` | guide | Flugschul-Gates bestimmen ihre Rolle aus den Positionen aller voll sichtbaren Gate-Akteure (`GateStacking.base`) und zeichnen über `GateRenderer#fill` dieselben Modelle, Drehung und Halterungen wie die Welt |

Stapeln ist rein geometrisch: es gibt keine gespeicherte Verbindung, jedes Gate bleibt ein eigenes Entity und eigenes
Kurs-Gate (Kurs-Werkzeug nummeriert es wie jedes andere, eigene Pfeile, eigenes grün/weiß). Rundenzählung und
Serverprüfung zählen wie bisher nur Durchflüge durch die Öffnung des erwarteten Gates in seiner Richtung; der gemeinsame
Balken zwischen zwei Öffnungen ist Rahmen. Bei einem Gate auf Beinen zählt die Öffnung weiterhin erst ab Rahmenstärke
über dem Boden. Weltkurs-Gates lassen sich genauso stapeln; die Drohnen-Arena hat eine Leiter aus drei Gates
(`tools/data/world_courses.py`, `Course.stack`). Selbsttests `race.client.GateStackSelfTests` (Grenzen und Nachrutschen,
Leiter + Doppel-Gate als Kurs mit Nummern, Rolle und drei Kollisionsboxen je gestapeltem Gate beim Client, alle
Leiter-Modelle auflösbar, Runde durch alle fünf Öffnungen, Aufräumen), Bildschirmfoto `gate_stacks.png` (Leiter aus
drei Gates und Doppel-Gate), Flugschul-Schritt `race/stack`.

### Gates an Wand und Decke, Tauchtore und Drehen (race)
Jedes Gate (alle Größen, auch Start/Ziel) steht am Boden, hängt an Wänden oder unter Decken und lässt sich in
90°-Schritten drehen; ein Tauchtor ist ein gewöhnliches Gate in der Lage `wall_dive`. Entwurf, Recherche und Tabellen
der Rahmen: `docs/design/gate-mounting.md`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/GateMount` | race | Lagen `STANDING`, `FLOOR_FLAT` (Boden), `WALL_DIVE`, `WALL_SIDE`, `WALL_WINDOW` (Wand), `HANGING`, `CEILING_FLAT` (Decke); `surface()`, `turns()` (4 = Richtung in Vierteldrehungen, 2 = Flugrichtung umkehrbar), `next()` (nächste Lage derselben Fläche), `stackable()` (nur `STANDING`), `base()` (`MOUNTED` = Rahmen ohne Füße), `CODEC` (stabile Schlüssel), `pack/unpackMount/unpackFlipped` (ein Byte) |
| `race/GateGeometry` | race (reines Java, JUnit `GateMountTest`) | Orthonormaler, rechtshändiger Rahmen `side`/`up`/`normal` je Lage aus `yawDegrees` und `flipped`; Ursprung = Mitte der Unterkante. `crossing`, `insideOpening`, `boxes`, `bounds`, `centerX/Y/Z`, `pathLength` gelten für jede Lage, Boxen bleiben achsparallel. `STANDING` rechnet bitgleich wie vor den Lagen (Goldens, Geister, Bestenlisten unverändert) |
| `race/GatePlacement` | race (reines Java, JUnit `GatePlacementTest`) | `surface(faceY)`, `resolve(click, lage, stellung, größe)` (Raster: Öffnung ungerader Breite mittig im Block, gerader Breite auf der nächsten Blockkante), `mountPoint(gate)` und `rotated(gate, lageWechseln, gestapelt)` drehen um den Befestigungspunkt; `Preference` = Lage und Stellung je Fläche |
| `race/GateMounting` | race | Server: Wahl je Spieler (`GatePreferenceC2S`, beim Abmelden vergessen), `rotate(spieler, gate, lageWechseln)` → `Result` (Reichweite +2, `mayBuild`, Kursbesitz, Platz ohne das Gate selbst, gestapelt nur 180°), Meldungen `propwash.race.mount.*` |
| `race/RacePayloads.GatePreferenceC2S`, `GateRotateC2S` | race | C2S, Protokoll 8 (seit 11 mit Feinwinkeln bzw. Schritt) |
| `race/client/GateMountKeys`, `race/client/GateKeyRoute` | race | Taste `key.propwash.gate_rotate` (Standard `KEY_R`, geteilt mit `key.propwash.drone_flight_mode`): Stellung drehen, mit Schleichen Lage wechseln; Blick auf ein Gate mit Gate-Item oder Kurs-Werkzeug in der Hand dreht dieses Gate. `GateKeyRoute#route` entscheidet nach Fenster, Pilot-Sitzung (dann gehört die geteilte Taste dem Flugmodus), Hand (Gate, Kurs-Werkzeug, Funke), Gate im Blick, Schleichen und geteilter Belegung (`GateMountKeys#sharedWithFlightMode`) |
| `input/client/KeyDefaults#SHARED`, `sharesKey`, `mixin/input/client/KeyMappingMixin` | input | Paare, die sich eine Taste teilen dürfen (nur Flugmodus + Gate drehen): `duplicates` übergeht sie, `KeyMapping#same` meldet sie nicht als gleich (Steuerung beider Loader ohne Warnung), der Tab „Tasten“ nennt sie im Tooltip als geteilt statt als Konflikt |
| `race/client/GatePreview` | race | Durchscheinende Vorschau (gleicher Renderer, `DroneGhost.tinted`), Pfeil in Flugrichtung, rot bei fehlendem Platz, Hinweisfeld im Propwash-Theme |
| `race/client/GateRenderer#rotation(out, gate)`, `fill(...)`, `submitBody(...)` | race | Modelle mit dem Rahmen gedreht; `_mount`-Varianten ohne Füße, Montageplatten (`gate/mount_plate`) am seitlichen Wandgate, Gurte (`gate/hanger`) am hängenden Gate |

Kurs-Daten (`CourseRegistry.GateRecord.mount`/`flipped`, `CourseData.Gate`) tragen die Lage; alte Dateien, Welt-Kurse und
Spielstände laden als stehende Gates, `layoutHash` stehender Kurse bleibt gleich. Selbsttests
`race.client.GateMountSelfTests` (jede Lage und Stellung über `GateItem#useOn`, Client-Abgleich, Drehen an Ort und Stelle
samt Ablehnungen, Kurs aus Start-Gate, Tauchtor und hängendem Gate, Runde abwärts zählt und aufwärts nicht – Client
`LapTracker` und Server `RaceServer`/`LapVerifier`, NBT mit und ohne Lage), Bildschirmfotos `gate_mount_*.png`,
Flugschul-Schritt `race/mount`.

### Gates frei drehen und frei groß (race, core, sim)
Seit 0.5.0 lassen sich Gates zusätzlich zur Lage frei gieren, nicken und rollen und bekommen eine eigene Öffnung von
1 bis 16 Blöcken in halben Schritten. Entwurf, Rechnung und Entscheidungen: `docs/design/gate-free-transform.md`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/GateTilt` | race (reines Java bis auf `CODEC`) | Gieren/Nicken/Rollen in Grad (0,1°), `NONE`, `tilted()` = Nicken oder Rollen ≠ 0, `wrap` |
| `race/GateGeometry` | race (reines Java, JUnit `GateFreeTransformTest`) | Seit 0.5.0 Klasse statt Record (gleiche Zugriffe, `equals`/`hashCode`), Zusatzdrehung um die Öffnungsmitte und Wiederaufsetzen auf die Fläche, `originX/Y/Z` (gedrehter Ursprung), `tilt()`, `heading()`, `upright()`, `alignedFrame()`, `parts(...)` (OBBs), `straps(...)`, `sameAs`; ohne Neigung bitgleich |
| `race/GateSize#snapOpening`, `outer`, `packOpening` | race | Öffnung 1–16 in halben Blöcken, Außenmaß = Öffnung + 2 × Rahmen |
| `race/GatePlacement#rotated(gate, lage, gestapelt, schritt)`, `turnedTo`, `edited`, `validStep`, `Preference#turnedBy`/`fine` | race | Schritte 90/45/15/5°, Gier-Anteil stehender und hängender Gates in [0°, 90°) gefaltet, Einstellen um den Befestigungspunkt |
| `race/GateMounting#edit`, `unlink`, `preview`, `holdsTool` | race | Server-Prüfung wie `rotate`, Reichweite Interaktion + `EDIT_REACH` (16) statt + 2, im Stapel `STACK_LOCKED`, Ergebnis `EDITED`/`UNLINKED`/`NEED_TOOL`/`NOT_IN_COURSE` |
| `race/RacePayloads.GateEditC2S` (`propwash:race_gate_edit`), `GateRotateC2S#step`, `GatePreferenceC2S` mit Feinwinkeln | race | C2S, **Protokollversion 11** |
| `race/GateEntity` `DATA_TILT`, `DATA_OPENING`, NBT `Tilt`, `OpeningWidth`/`OpeningHeight`, `orientedColliders()`, `stackable()` | race | Synchronisiert, alte Spielstände laden ungeneigt in Item-Größe |
| `race/client/GateEditor`, `GateEditPanel`, `GateEditCursorScreen`, `GateEditModel` | race (Modell reines Java, JUnit `GateEditModelTest`) | „Gate einstellen“ als Einstell-Modus im Spiel ohne Fenster für Gates, Kurs-Elemente und Freiform-Gates (`docs/design/gate-edit-mode.md`): gestartet über `GateEntity#setEditor`, Panel als HUD-Ebene `propwash:gate_edit`, Mausrad/Enter/Esc/Rechtsklick über `mixin.race.client.MouseHandlerMixin`, `KeyboardHandlerMixin`, `MinecraftMixin`, freie Maus mit Taste `key.propwash.gate_edit_cursor` (linke Alt-Taste) als nicht pausierender Screen, Live-Vorschau über `SUBMIT_WORLD_GEOMETRY` und `GateRenderer#submitEdited` |
| `race/client/GateRenderer` `Piece`, `toFrame` | race | Eigene Größen aus `models/gate/piece/*`, ebene Leuchtpfeile und senkrechte Gurte geneigter Gates |
| `core/OrientedBox`, `core/OrientedSweep` | core (reines Java, JUnit `OrientedBoxTest`) | OBB, Trennachsen- und Strahl-Test, Wischen einer AABB mit Gleiten |
| `core/ExtraCollider#collectOrientedColliders`, `reach()`/`extendReach` | core | Schräge Teile als OBB, Reichweite der Abfragen wächst mit großen Gates |
| `sim/MoveReport#contactNormalX/Y/Z`, `DroneSim#afterMove` | sim (JUnit `ObliqueContactTest`) | Schräger Kontakt mit echter Normale; NaN = unverändertes Verhalten (Goldens, Kalibrierung bitgleich) |

Kurs-Daten (`GateRecord.turn/pitch/roll/width/height`, `CourseData.Gate` mit Merkerbyte) tragen Neigung und Größe;
`layoutHash` nimmt sie nur auf, wenn sie vom Standard abweichen. Selbsttests `race.client.GateFreeSelfTests`, Bildschirmfotos
`gate_free_*.png`, `gate_edit_mode*.png`, `gate_edit_cursor.png`, Flugschul-Szene `gate_free`.

### Redstone-Signal der Gates (race)
Seit 0.5.0 gibt jeder gültige Durchflug durch ein Gate oder Freiform-Gate einen Redstone-Puls in den Block, an dem das
Gate befestigt ist. Entwurf, Zielblock je Lage und Entscheidungen: `docs/design/gate-redstone.md`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/GateSignal` | race (reines Java, JUnit `GateSignalTest`) | an/aus, Pulslänge 2–100 Ticks (Standard 10), „Nur im Rennen“; `pack()`/`unpack` in 9 Bit, `fires(kursGate, startGate, imRennen)` |
| `race/GateSignalTarget` | race (JUnit `GateSignalTargetTest`) | Zielblock und Fläche (`face` = Richtung vom Zielblock zum Sender, Ordnung wie `Direction#get3DDataValue`) aus `GatePlacement#mountPoint` bzw. der Freiform-Fläche |
| `race/GateSignalMap` | race (reines Java, JUnit `GateSignalMapTest`) | Zähler je Sender und Fläche; Ein-/Ausschalten nur beim ersten bzw. letzten Gate |
| `race/GateSignals` | race (Server) | aktive Sender je Welt, `signal(level, pos, richtung, wert)` für das Mixin, Nachbar-Updates wie ein Knopf, `clear()` bei Serverstart/-stopp |
| `mixin/race/BlockStateBaseMixin` | race | `@ModifyReturnValue` auf `BlockStateBase#getSignal`/`#getDirectSignal`, ohne Puls nur ein `volatile`-Lesen |
| `race/GateEntity#signal`, `setSignal`, `pulse`, `signalTarget`, `pulseLeft`, `emitting` | race | synchronisiertes `int` `DATA_SIGNAL`, NBT `RedstoneSignal`/`PulseTicks`/`SignalRaceOnly` nur bei Abweichung, `SignalPulse` während eines Pulses; Puls zählt im Server-Tick, `onRemoval` schaltet ab |
| `race/RaceServer#onDroneMoved` | race | dieselbe Entity-Abfrage wie die Zeitmessung, zusätzlich Gates mit Signal ohne Kurs; Durchflug über `GateGeometry#stageCrossing(0, …)` |
| `race/RacePayloads.GateSignalC2S` (`propwash:race_gate_signal`), `GateMounting#signal`, `#describeSignal` | race | C2S, Prüfung wie beim Einstellen (Werkzeug, Reichweite, Baurecht, Kurs-Besitzer), Ergebnis `SIGNAL_SET`; **Protokollversion 14** |
| `race/client/GateEditModel` `Row.SIGNAL/PULSE/RACE_ONLY`, `Section.SIGNAL`, `page`/`pages`/`showPage`; `GateEditPanel` Seiten; `GateEditor#signalTarget` | race | zweite Panel-Seite „Redstone“, roter Rahmen auf der Befestigungsfläche |

Selbsttests `race.client.GateRedstoneSelfTests` (`race: Redstone – …`), Bildschirmfotos `gate_redstone*.png`.

### Kurs-Elemente: Wendeflagge, Slalomstange, Tauchwürfel (race, guide)
Seit 0.5.0 gehören neben Gates drei Elemente zu Kursen. Entwurf, Zählung und Entscheidungen:
`docs/design/course-elements.md`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/CourseElement` | race (reines Java) | Arten `GATE`/`FLAG`/`POLE`/`CUBE`, Varianten (Seite, Richtung, Ausgang), Stufen, Stärke, Standardgröße, `slalomSide` |
| `race/Passage` | race (reines Java) | Rechteck mit Normale, Durchflug vorwärts/rückwärts wie beim Gate |
| `race/GateGeometry#element`, `stages()`, `fouls(stufe)`, `stageCrossing`, `waypoints()` | race (JUnit `CourseElementTest`) | Elemente stehen aufrecht (nur Gieren), Kollisionsteile, Stufen, Fehler, Wegpunkte der Ideallinie; Gates unverändert |
| `race/CourseCheckpoints` | race (reines Java) | (Element, Stufe) → Prüfpunkt für `LapVerifier`; reine Gate-Kurse behalten die Gate-Nummern |
| `race/LapTracker` | race | Stufenzähler je Element, Rücknahme, `EventType.WRONG_SIDE` |
| `race/GateEntity` `DATA_KIND`, NBT `Kind`/`Variant` | race | Art und Variante synchronisiert; ohne `Kind` ein Gate |
| `race/CourseElementItem` | race | Items `race_flag`, `slalom_pole`, `dive_cube`: nur Boden, kein Stapeln, Tooltip |
| `race/GatePoles` | race (reines Java, JUnit `GatePolesTest`) | Gate-Stange (`CourseElement.GATE_POLE`, `docs/design/gate-pole.md`): Sitzplätze auf Gate-Ecken und Stangenspitzen (`seats`, `restsOn`, `nearest`), Setzen an Boden/Wand/Decke (`place`), Einstellen (`edited`, hängende bleibt an der Decke), Länge 1–16, 16 LED-Farben, `MAX_CHAIN` = 4 |
| `race/GatePoleLinks`, `race/GatePoleItem` | race | Träger und aufsitzende Stangen suchen, Ziel beim Aufsetzen (Ecke nach Klickpunkt, sonst Turmspitze), Abnehmen beim Abbau (Items nur mit Spieler im Überleben), Farbstoff; Item `gate_pole` setzt an Boden, Wand und Decke und auf Gates |
| `race/client/GatePoleRenderer` | race | Stange aus Gate-Teilen (`post_left`, `led_post_left`, `pole_end`, `pole_tee`, `pole_foot`), LED-Farbe der Stange |
| `race/CourseToolItem` | race | Element nie als Kursstart, Slalom-Seite beim Anhängen |
| `race/CourseRegistry.GateRecord#kind`/`variant`, `CourseData.Gate` (Merkerbit 4) | race | nur bei Elementen gespeichert und gesendet, `layoutHash` reiner Gate-Kurse bleibt |
| `race/RacePayloads` `GatePreferenceC2S`, `GateEditC2S` mit Variante | race | C2S, **Protokollversion 11** |
| `race/client/CourseElementRenderer` | race | Teilmodelle `models/course/*`, Bodenpfeile, Kreuze, Pfeilbogen, Tauchpfeile, Nummern-Tafel, Pfeil der Vorschau |
| `guide/scenes/CourseElementsScene` | guide | Flugschul-Szene `course_elements` (API `FlightSchoolScenes.COURSE_ELEMENTS`) |

Selbsttests `race.client.CourseElementSelfTests`, Bildschirmfotos `course_elements*.png`, `course_element_edit_mode*.png`,
`tooltip_race_flag_de.png`, Flugschule `flight_school_course_elements*.png`.

### Freiform-Gate (race, guide)
Seit 0.5.0 gibt es Gates ohne Rahmen, deren Fläche die Blöcke einer Öffnung begrenzen. Entwurf, Zählung und
Entscheidungen: `docs/design/freeform-gate.md`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/FreeformRegion` | race (reines Java) | Achse, Box ≤ 128 × 128, Startfeld, Bitmaske; Text `f1:<achse>:<b>x<h>:<a>,<b>:<Base64>`, Bytes fürs Netz; sicherstes Feld (Ziel der Ideallinie), Randkanten |
| `race/FreeformFill` | race (reines Java, JUnit `FreeformGateTest`) | Flutfüllung mit 4er-Nachbarschaft bis zur Welt-Regel-Grenze, Ergebnis OK/START_BLOCKED/OPEN/TOO_BIG/UNLOADED, Wahl von Ebene und Richtung aus Fläche und Blick, Randprüfung |
| `race/FreeformBlocks` | race | Brücke zur Welt: durchlässig = keine Kollisionsform und keine Flüssigkeit; geladen, Bauhöhe, Weltgrenze; Neu-Abtasten, Belegung |
| `race/GateGeometry#freeform`, `Passage#cells` | race | Ebene durch die Mitte der Startzellen-Schicht, eine Stufe mit Zellmaske, keine Kollisionsteile; gleich bei Client und Server |
| `race/GateEntity` `DATA_REGION`, NBT `Region` | race | Fläche als Text (Vanilla-`STRING`), Anker = Entity-Position; Zielen nur mit Freiform-Gate oder Kurs-Werkzeug (`picksFreeform`) |
| `race/CourseRegistry.GateRecord#region`, `CourseData.Gate#region` (Merkerbit 8) | race | nur bei Freiform-Gates; `layoutHash` mit Fläche; Kurs-Sync nach Größe geteilt |
| `race/RacePayloads.GateEditAction#RESCAN` | race | C2S, **Protokollversion 11** |
| `server/rules/WorldRuleSpecs#FREEFORM_GATE_MAX_SIZE` | server | `propwash:freeform_gate_max_size`, 1–64, Standard 20, keine Physik |
| `race/client/FreeformGateRenderer`, `FreeformView`, `FreeformPreview` | race | LED-Umriss, Füllung, Bodenwinkel, Nummern-Tafel, Warnung; Vorschau beim Zielen; Einstellen über `GateEditor` |

Selbsttests `race.client.FreeformGateSelfTests` (Präfixe `race: Freiform`, `bildschirmfotos: Freiform`), Bildschirmfotos
`freeform_gate_*.png`, Flugschule
`flight_school_gate_free_freeform.png` (Schritt „Freiform-Gate“ in `gate_free`).

### Drohnen-Item und Geist mit dem eigenen Bauplan (drone-render, race)
| Datei | Besitzer | Vertrag |
|---|---|---|
| `drone/client/render/DroneItemRenderer` | drone-render | Sonder-Renderer `propwash:drone_build` (registriert in `DroneClient`) für `items/drone.json`. `extractArgument` → `Look(build, wear, paint, battery)` aus `drone_build` (fehlt → FREESTYLE), `part_wear` (nur die sichtbaren Prop-Stufen), `drone_paint`, `battery_installed`; der Record ist zugleich der Schlüssel des GUI-Item-Atlas (gleicher Bauplan = ein gezeichnetes Symbol). Gezeichnet wird dieselbe Baugruppe wie an der Entity (`DroneVisuals.submit`), je `Look` zwischengespeichert (höchstens 96, neu nach Resource-Reload oder Render-Config). Größe: FREESTYLE liegt exakt wie das bisherige Icon-Modell (Skalierung 1), andere Baupläne werden um die Mitte ihrer Hülle auf `fitFactor` = (r / r₅)^0,3 geklemmt auf 0,72…1,0 des FREESTYLE-Radius gebracht (Whoop gut lesbar, 7" nicht größer als 5"), Unterkante auf der FREESTYLE-Unterkante. `getExtents` = Kasten der FREESTYLE-Hülle. Anzeige-Transformationen kommen aus dem Basis-Modell `propwash:item/drone` |
| `drone/client/render/DroneGhost` | drone-render | `submit(build, paint, orientation, argb, poseStack, collector)`: zeichnet die Drohne an der aktuellen Pose (Ursprung = Füße der Entity, wie `QuadcopterRenderer`) durchscheinend. Alle Item-Quads laufen über `GhostCollector` als `AfterWaterRenderTypes.ghost` des jeweiligen Atlas, Formen als `ghost` der weißen Textur (nach dem Wasser, siehe „Durchscheinende Drohnen-Optik nach dem Wasser“), Farbe × `argb`; LED-Glühen und LED-Schein, Schatten, Umrisse und Texte fallen weg. Zustand je Bauplan und Lackierung zwischengespeichert (höchstens 8). Gibt `false` zurück, wenn nichts gezeichnet werden konnte (race zeichnet dann die alten Kästen) |
| `race/GhostLook` | race | siehe „Geister-Rennen“ |

Selbsttests (`drone.client.render.DroneItemSelfTests`): „Drohnen-Item zeigt den eigenen Bauplan“ (alle Presets, lackierter
Whoop mit LED-Streifen, kaputten Props und ohne Akku, Item ohne Bauplan; GUI, Boden, Rahmen, erste und dritte Person; Argument,
gezeichnete Teile, Maße, Zwischenspeicher), „Drohnen-Items passen in den Slot“ (Größe je Preset zwischen 72 und 100 % des
FREESTYLE, FREESTYLE unverändert), „Geist zeichnet den Bauplan durchscheinend“ (nichts Deckendes, nur durchscheinende
Geometrie, alles in der Phase nach dem durchscheinenden Terrain); race: „Geist der Bestrunde spielt ab“ prüft zusätzlich Aussehen und Rückfall, „Bestenliste und Plausibilität“
das Aussehen des Rekord-Geists. Bildschirmfotos: `inventory_drones.png` (erste Reihe die fünf Presets nebeneinander, zweite
Reihe lackierter Racer, Neon-Whoop mit LED-Streifen, Cinewhoop mit kaputten Props, Freestyle mit schwerer Action-Cam und
LED-Streifen, Long-Range mit leichter Action-Cam ohne Akku) und `race_ghost_whoop.png`.

### Durchscheinende Drohnen-Optik nach dem Wasser (drone-render, race)
Vanilla `entityTranslucent`/`itemTranslucent` schreiben Tiefe und laufen ohne „Verbesserte Transparenz“ (und immer mit Iris-Shaderpacks)
in `executeTranslucent`, also **vor** dem durchscheinenden Terrain. Wasser hinter einer drehenden Prop-Scheibe wurde deshalb vom
Tiefentest verworfen, man sah den Grund darunter (Röntgenblick im FPV-Bild und an Drohnen in der Welt). Seitdem gilt:

| Datei | Besitzer | Vertrag |
|---|---|---|
| `drone/client/render/AfterWaterRenderTypes` | drone-render | Render-Typen, die `SubmitNodeCollection#submitCustomGeometry` in die Phase `afterTerrain` legt (nach dem durchscheinenden Terrain und vor Partikeln, Wolken und Wetter; mit „Verbesserter Transparenz“ ist das dieselbe OIT-Phase wie vorher). `blur()`: Prop-Scheibe als eigene Form (FPV-Props, Ersatzform), Pipeline `propwash:pipeline/prop_blur` = `entity_translucent` **ohne Tiefe schreiben**; `blurItems(atlas)`: durchscheinende Quads des Blur-Item-Modells, Pipeline `propwash:pipeline/prop_blur_item` = `item_translucent` ohne Tiefe schreiben (Rückseiten weiter weggeschnitten, damit die Doppelfläche nicht doppelt deckt); `ghost(texture)`: Geister (Vanilla-Pipeline `ENTITY_TRANSLUCENT`, schreibt Tiefe für die Eigenverdeckung); `halo()`: LED-Schein um LEDs und Streifen (Vanilla-Pipeline `EYES`). `drawsAfterWater(type)`, `isHalo(type)`. Beide eigenen Pipelines werden beim ersten Typ über `IrisCompat.assignPipeline` Iris als `ENTITIES_TRANSLUCENT`/`SHADOW_ENTITIES` zugeordnet. OIT-Sätze wie Vanilla (`OIT_ENTITY`, `OIT_ITEM`, `OIT_EYES`) |
| `drone/client/render/BlurItemCollector` | drone-render | Hülle um den Collector für das Blur-Item einer drehenden Prop (`DroneVisuals.submit`): deckende Quads (Nabe) bleiben ein normales Item, durchscheinende gehen als `blurItems(atlas)` mit Tönung, Licht und Overlay wie `ItemFeatureRenderer`; ein Umriss (`outlineColor`) bleibt vollständig. Ein `GhostCollector` wird nicht eingehüllt |
| `mixin/drone/client/SubmitNodeCollectionMixin`, `RenderTypeAccessor` | drone-render | Umleitung nach `afterTerrain` (HEAD von `submitCustomGeometry`, nur für die Typen oben) und Invoker für das paketprivate `RenderType.create` |

Regel: Durchscheinende Optik der Drohne, die in der Welt vor Wasser liegen kann (Prop-Scheiben, LED-Schein, Geister, race-Ersatzkästen
in `GhostRenderer`), nutzt diese Typen, nie `RenderTypes.entityTranslucent`/`itemTranslucent` direkt. Das LED-Glühen selbst (`eyes`,
Ordnung 1) liegt auf der Drohne und bleibt, Staub (`WashParticle`, `Layer.TRANSLUCENT`) läuft als Partikel ohnehin nach dem Terrain.
Nachteil ohne Tiefe schreiben: Wolken und Regen hinter einer Scheibe werden danach darübergelegt statt ausgestanzt; es entsteht nirgends
ein Loch. Wie Vanilla-Partikel verschwinden diese Teile hinter einer durchscheinenden Blockfläche (Drohne unter der Wasseroberfläche,
hinter Buntglas), der deckende Drohnenkörper bleibt sichtbar. Mit „Verbesserter Transparenz“ (OIT) ist die Reihenfolge ohnehin egal. Selbsttests: „drone-render: Prop-Scheiben, LED-Schein und Geister nach dem Wasser“ (Tiefe, Phase der Welt-Drohne und der
FPV-Props), der Geist-Test oben, im Bildschirmfoto-Lauf „bildschirmfotos: drehende Props im FPV-Bild über Wasser“ mit
`fpv_props_over_water.png` und „drone-render: Prop-Scheiben über Wasser wurden gezeichnet“ (`FpvProps.blursDrawn()` steigt).

### Parts-API 0.4.0 (Architekt; Vertrag `docs/design/parts-api.md`)
Bauteile sind ab 0.4.0 offen: andere Mods melden Teile per Java oder JSON an. Der vollständige Vertrag (Bauteil-Modell,
Registrierung, Speichern/Netz, Verbraucher, Flugmodell, Rendering, Import, Erweiterungspunkte, Besitz der vier
Implementierer) steht in `docs/design/parts-api.md` und geht für diese Themen allem hier vor. Kurz:
| Datei | Besitzer | Vertrag |
|---|---|---|
| `api/**` | Architekt, Pflege D | öffentliche, versionierte API (`PropwashApi.API_VERSION` 1.5.0 seit Propwash 0.5.0, SemVer); Signaturen eingefroren, nur Ergänzungen |
| `build/PartIndex`, `build/PartInfo` (sealed) | A | Register aller Teile: `register`, `detached` (nur Tests/Werkzeuge), `byId`, `byKey` (ohne `:` = `propwash`), `all(kind)`, `keyOf`, `fingerprint`, `freeze`; thread-sicher, Lesen ohne Sperre |
| `build/FrameType`, `StackType`, `MotorType`, `PropType`, `BatteryType` (+ `Chemistry`), `VideoType`, `AccessoryType`, `MountSize.Motor/Stack` | A | **keine Enums mehr**: registerbasierte `final`-Klassen mit denselben Konstanten und Feldern, `definition()` liefert den API-Record; `values()` = alle angemeldeten Teile, eingebaute zuerst. Verboten: `switch` auf Konstanten, `EnumMap`/`EnumSet`, `ordinal()`; Identität statt `equals` |
| `parts/**` | A | Register-Fassade, JSON-Laden, Items fremder Teile, Handshake |
| `apiimpl/**` | D | Fassaden-Implementierung, Erweiterungs-Register, Import-Netzwerk |
| `sim/AirframeGoldenTest` | B | eingebaute Drohnen fliegen bitgleich zu 0.3.2 (9000 Kombinationen); muss grün bleiben |
- In JUnit nie `PartIndex.register`, nur `PartIndex.detached` (sonst ändern sich `values()` für andere Tests).
- `PartsModule`/`PartsClient` (A) und `ApiModule`/`ApiClient` (D) sind in `PropwashCommon`/`PropwashClient` verdrahtet.
- Entwicklerhandbuch für Addons (Schritt für Schritt, Sharecodes, Beispiel-Addon): `docs/api/addon-guide.md`.

**Fassade, Import-Netzwerk, Erweiterungen (D):**
| Datei | Besitzer | Vertrag |
|---|---|---|
| `apiimpl/BuildAccessImpl` (`PropwashApi.builds()`) | D | `read` = gespeicherte Daten des Drohnen-Items (Tuning nur Abweichungen vom Standard, Name über `CraftNames.of`); `createDrone` = `DroneAssembly.preset` + bereinigtes Tuning (`DronePiloting.sanitize`) + `PaintItems.store` + Name (≤ 24 Zeichen), wirft `IllegalArgumentException` bei unbekannten Teilen, Zubehör im falschen Platz (`Conversions.build` leer) oder Problemen; `withTune` ersetzt das Tuning ganz (bereinigt), `withPaint` nur den Lack, beides auf einer Kopie; `check` meldet unbekannte Ids, sonst `DroneBuild.problems(rule)/warnings()` klein geschrieben (mit der Welt-Regel `propwash:part_compatibility` aus stehen die aufgehobenen Probleme unter den Warnungen; `createDrone` folgt derselben Regel, `partCompatibility()` meldet sie), falscher Zubehör-Platz = `accessory_mount`; `analyze` = `BuildAnalysis` → `BuildStats` (Warnungen = `sim.BuildWarning` klein, Texte `propwash.content.workbench.warning.<w>`); `defaultTune` = `TuneMapping.defaults` + `camera.uptiltDeg` aus `FrameDefinition.geometry().cameraTiltDeg()` + LED-Standards; `applyImport`: Zuschauer → `NOT_ALLOWED`, unbekannte Teile → `UNKNOWN_PARTS` (vor `content.ImportService`), sonst `ImportService.apply` (Ausnahmen → `INVALID`), danach immer `BUILD_IMPORTED` (Listener-Ausnahmen geloggt und isoliert). `requireBuild(spec)`, `stats(analysis)`, `imported(player, request, result)` für andere Nahtstellen |
| `apiimpl/ImportPayloads`, `ImportWire`, `ImportNet`, `ImportRateLimit` | D | C2S `propwash:api_import` (`ImportC2S`: Anfrage-Id, Container-Id oder −1, `ImportWire`) und S2C `propwash:api_import_result` (`ImportResultS2C`: Anfrage-Id, Status-Ordinal, Ebenen-Bitmaske, fehlende Teile ≤ 64, fehlende Farbstoffe ≤ 64), angemeldet in `ApiModule`. `ImportWire` transportiert den Bauplan als Strings (Ids ≤ 256 Zeichen, Zubehör ≤ 8, Lack ≤ 32, Tuning ≤ 256, Name ≤ 96), Ziel als Ordinal, Ebenen/Tuning-Gruppen als Bitmaske nach Ordinal; `toRequest()` wirft `IllegalArgumentException` statt die Verbindung zu trennen. Server (`ImportNet.handle`): erst Rate (`ImportRateLimit`: höchstens 2 Anfragen je 20 Server-Ticks und Spieler, gleitendes Fenster, beim Verlassen vergessen) → kaputte Anfrage `INVALID` → `BUSY` → Ziel `WORKBENCH` nur mit offenem `DroneWorkbenchMenu` gleicher Container-Id und `stillValid`, sonst `NO_TARGET` → `BuildAccessImpl.applyImport`; jede dekodierbare Anfrage feuert `BUILD_IMPORTED` |
| `apiimpl/client/ClientApiImpl`, `PendingImports`, `ApiClient` | D | `sendImport` (Ziel Werkbank: Container-Id des offenen `DroneWorkbenchMenu`, sonst −1) und `sendWorkbenchImport(containerId, request)` (für A: `WorkbenchContext#importIntoWorkbench`), nur Render-Thread; Antworten erfüllen das Future auf dem Render-Thread; nicht gesendet, getrennt (`ClientBus.DISCONNECT`) oder 200 Client-Ticks ohne Antwort → `NO_TARGET`. `OSD` = `OsdLayoutCodec` über `OsdClient.CONFIG`, `TUNE` = `TuneAccess` (`apply` erwartet das ganze Tuning), `heldDrone` = Haupthand, `renderPreview` = `DronePreview.render(…, PartWear.PRISTINE, Lack, …)`, zeichnet nichts bei unbekannten Teilen |
| `osd/OsdLayoutCodec` | D | OSD-Layout Version 1 (Schema in `docs/api/addon-guide.md` §12): `encode(config)` (Kopie, Farben klein, `col`/`row` nur bei verschiebbaren Elementen, `color`/`scale` nur bei eigenen Werten), `decode(json, basis)`: Basis liefert `hideVanillaHud`/`analogBreakup`, Preset (unbekannt → Standard) legt alle Elemente fest, Stil fehlend → Standardwert, unbekannte Elemente/Felder übersprungen, danach `OsdConfig.sanitize`; wirft `IllegalArgumentException` bei leerem, kaputtem, zu langem (> 8192) JSON oder fehlendem `"v"` (JUnit `OsdLayoutCodecTest`: Rundreise bitgleich) |
| `guide/GuideScenes` | D | `register(factory)` / `register(factory, sichtbar)` holen erst die eingebauten Szenen nach (Addon-Szenen stehen immer dahinter); `all()` = sichtbare Szenen (Übersicht, Item-Zuordnung, Selbsttest „alle Szenen“), `registered()` = alle; `byId` nimmt `propwash:x` wie `x` und liefert `null` bei ungültigen Ids; eine werfende Sichtbarkeit gilt als unsichtbar |
| `guide/scene/Scene`, `SceneBuilder` | D | Ids `name` (Propwash) oder `ns:name` (Addons), `propwash:name` wird zu `name`; Texte `propwash.guide.scene.<name>.…` bzw. `<ns>.guide.scene.<name>.…` (`keyPrefix`, `namespace()`, `builtin()`); Schlüssel der eingebauten Szenen unverändert (JUnit `SceneNamespaceTest`) |
| `api/client/PropwashClientApi` (Ergänzungen) | D | `registerFlightSchoolScene(szene, sichtbar)` (Addon-Szenen brauchen einen eigenen Namensraum, sonst `IllegalArgumentException`), `flightSchoolDrone(BuildSpec, zusammengebaut, maßstab)`, `openFlightSchoolScene(id, parent)`; dazu `ImportLayer`/`TuneGroup#translationKey()` (`propwash.api.import.layer.<e>`, `propwash.api.tune_group.<g>`, Texte in `common/src/lang/api/`) und `BuildStats.warningTranslationKey` |
| Beispiel-Addon `dev.example.*` (devtest) | D | Namensraum `propwash_example`, nur API: Java-Teile `m2806_1300`, `p13_7_2b_carbon`, `race_stretch_5`; JSON-Teile (`common/src/devtest/resources/data/propwash_example/propwash_parts/`) `deadcat_5`, `xclass_13` (Halterung `propwash_example:m42`), `stack_30_8s`, `m4214_400`, `b8s_4500_lion`, Chemie `lihv_445`, geladen mit `loadBundled("propwash")`; alle `creative: false`; Preset `propwash_example:xclass` (nicht im Kreativ-Tab); Werkbank-Knopf `propwash_example:import_example` mit Kind-Fenster `ExampleImportScreen`, Tab `propwash_example:api_demo` (Order 1010; nicht `readOnly`, weil seine Knöpfe sofort wirken: `Storage.MIXED` mit eigener Fußzeile `propwash_example.tab.footer`), Szene `propwash_example:frames`; Zählzeilen des Import-Fensters mit eigenen Schlüsseln für 0, 1 und mehr (`.none`/`.one`/`.many`). Sichtbarkeit über `ExampleAddon.showcase()`: in normalen Entwicklungs-Runs an, im Selbsttest nur in den eigenen Schritten (der Tab wird dort erst in `ApiSelfTests` angemeldet, weil `UiSelfTests` alle angemeldeten Tabs zählt) — kein fremder Selbsttest und kein fremdes Bildschirmfoto ändert sich |
| `apiimpl/client/ApiSelfTests`, `ApiShowcase` (devtest) | D | Schritte „api: …“ (Register, Baupläne, Items, Presets/Kreativ, OSD, Werkbank-Import mit Rundreise und `DRONE_ASSEMBLED`, Tuning-Gruppen und Lack auf die gehaltene Drohne, Ratenbegrenzung/`NO_TARGET`/`UNKNOWN_PARTS`, Werkbank-Knopf, Tab, Szene); Bildschirmfotos `api_workbench_extension`, `api_workbench_import`, `api_config_tab` (am Ende von `ShowcaseTour` vor dem Aufräumen) |
| `common/build.gradle`, `build-logic/…/multiloader-loader.gradle` | Foundation (von D ergänzt) | Konfiguration `commonDevtestResources`: `common/src/devtest/resources` fließt in `processDevtestResources` beider Loader (nur Selbst-/Rauchtest- und NeoForge-Dev-Runs, nie ins Release-Jar) |

**Flugmodell und Rendering fremder Teile (B, C) und Integration 0.4.0:**
| Datei | Besitzer | Vertrag |
|---|---|---|
| `sim/Telemetry#motorPowerLimit`, `motorDerating`, `escBurned`; `SimEventType.MOTOR_DERATE`, `MOTOR_THERMAL_WEAR`, `ESC_BURNED` | B | Hitze-Drosselung je Motor (Grenzen aus `MotorThermal` der Definition, ab `derateStartC` bis 20 % bei `maxTempC`), Hitzeverschleiß, ESC brennt beim Scharfschalten durch, wenn der Akku mehr Zellen hat als der Stack erlaubt (`AirframeSpec.escOvervoltage`; keine eingebaute Kombination, JUnit `EscVoltageTest`). `SimTuning.thermalLimits` und `escOvervoltage` schaltet die Welt-Regel `propwash:hardware_limits` ab (bis 0.4.0 Schalter „Hardware-Grenzen“ im Tab „Simulation“) |
| `drone/net/HardwareWearC2S`, `drone/DroneWear#applyHardware` | B | C2S `propwash:drone_hardware_wear` (Entity, Reset-Epoche, ESC durchgebrannt, Hitzeverschleiß je Motor): der Server prüft Pilot, Epoche, die Welt-Regel `propwash:crash_damage` und dass der Bauplan wirklich überspannt, deckelt den Hitzeverschleiß je Paket und schreibt beides an die Drohne |
| `sim/BuildAnalysis#sustainedMotorTempC`, `propsInViewPercent`; `BuildWarning.MOTOR_THERMAL`, `PROPS_IN_VIEW` (ab 30 %, seit 0.4.1 aus dem FPV-Aufbau des Renderers: `sim.FpvRig`), `ESC_OVERVOLTAGE` | B | Werkbank-Bericht und `BuildStats`; `NaN` wird nicht angezeigt. Texte `propwash.content.workbench.warning.<w>` stehen in `common/src/lang/content/` (JUnit `ContentDataTest`) |
| `osd/OsdWarnings.Warning.MOTOR_HOT`, `OsdData#motorDerating` | osd (Integration) | OSD-Warnung „MOTOR ÜBERHITZT“ (Stufe Warnung, blinkt), solange ein Motor gedrosselt wird (`Telemetry.motorDerating`); Selbsttest „parts-flight: überhitzte Motoren drosseln …“ prüft sie |
| `drone/client/render/FpvProps`, `FpvPropGeometry`, `DroneRenderConfig#fpvProps` | C | eigene Props im FPV-Bild (Schalter „Darstellung“, Standard an). Die Kamera-Entity (eigene FPV- oder Zuschauer-Drohne) wird dafür extrahiert: `mixin/drone/client/LevelExtractorMixin` hebt über `FpvProps.extractsCameraDrone` die Vanilla-Regel „Kamera-Entity nicht zeichnen“ nur für diese Drohne auf; `QuadcopterRenderer` zeichnet dann nur die sichtbaren Props. Im Iris-Schattenpass nie: `FpvProps.submit` bricht dort ab (`shadowPassCalls()`), stattdessen zeichnet `QuadcopterRenderer` die echte Drohne in Originalgröße an der angezeigten Pose (`FpvView.displayedBody`, `DroneVisuals.placeAtDisplayedPose`, `fpvShadowBodies()`), Halos und Motor-Glühen fehlen im Schattenpass (`DroneRenderState.shadowPass`); Schalter „Schatten“ aus nimmt dort alle Drohnen heraus (`docs/design/shader-packs.md`). Drehende Props als weiche Scheibe in der Prop-Farbe (`FpvProps.buildBlur`, Deckkraft 35 → 11 %, nie unter der Alpha-Schwelle), stehende als Blatt-Modell; JUnit `PartIconAndFpvBlurTest` |
| `drone/client/render/FpvGuards`, `FpvGuardZone`, `FpvGuardMesh`, `QuadCapture` | C | Ducts im FPV-Bild (seit 0.5.0, eigener Schalter „Ducts im FPV-Bild“, `DroneRenderConfig#fpvDucts`, unabhängig von „Props im FPV-Bild“; die Kamera-Drohne wird extrahiert, wenn einer der beiden an ist): Flächen des Rahmenmodells bzw. der Lack-Variante (`QuadCapture` fängt die Item-Flächen samt Transformation ab, ohne Modell die `FallbackShapes`-Form), Auswahl Ring um jede Motorachse plus mindestens 5 rundum wiederholte Flächen (`FpvGuardZone.select`), Zeichnen an der Linse mit derselben Tiefenabbildung wie die Prop-Scheiben (`FpvPropGeometry.pushScale`), nicht im Schattenpass und nicht bei `FpvProps.maskColor`; `FpvProps.guardMaskColor` für den Selbsttest. JUnit `FpvGuardsTest`, Selbsttest `FpvGuardSelfTests`, `docs/ASSETS.md` „Ducts im FPV-Bild“ |
| `drone/client/render/PartItemRenderer` (`propwash:part`, Basis `propwash:item/part_base`), `PartModel.known()`, `drone_layout.json`/`drone_paint.json` je Namensraum | C | Symbole und Modelle fremder Teile aus ihrem Namensraum, sonst Formen aus der Definition (`docs/ASSETS.md` „Teile aus anderen Namensräumen“). Symbole ohne Modell: GUI-Ansicht 45°/225° (`part_base`), mit `"box": true` (Regal, Kopf, Item-Rahmen) das ganze Teil in einem Block, mit `"ground": true` (Boden) ebenso und sperrige Teile höchstens wie ein Würfel mit 0,3 Block Kante, mit `"hand": true` (Hände) nach der echten Größe des Teils (Handfaktor `ItemSizeRule.handFactor`), Rahmen und Video −20° um die Hochachse gedreht, eigene Symbol-Formen mit kräftigeren Rahmen-Armen und breiteren Prop-Blättern (`FallbackShapes.frameIcon`/`propIcon`; die Formen an der Drohne bleiben unverändert) |
| `content/DronePresets#label` | content (Integration) | Name über der Werkbank-Vorschau: eingebaute Presets wie bisher (`propwash.content.preset.<key>`), sonst ein angemeldetes Addon-Preset mit gleichem Bauplan ohne Zubehör (Name aus `DroneData`, sonst `DronePreset#translationKey`); kein Treffer → kein Name. JUnit `DronePresetLabelTest` |
| `core/SaveFormatGoldenTest` + `save_format_032.txt` | Integration | NBT und Netz-Bytes von 9035 Bauplänen (alle eingebauten Kombinationen, Presets mit Zubehör), allen `PartRef`s, Bindungen, Tuning, Verschleiß, Lack, Geist-Aussehen und Geisterdatei sind byte-gleich zu 0.3.2 (Golden-Datei mit dem Code von 0.3.2 erzeugt) |
| `dev/client/LegacyWorldCheck` (devtest) | Integration | Rauchtest mit einer alten Welt: `PROPWASH_SMOKE_WORLD=<Ordner in saves>` beim `runClientSmoke` öffnet diese Welt statt eine neue anzulegen und prüft, dass Inventar und Ausrüstung des Besitzers, alle Propwash-Entities aus den Entity-Regionen und die Rennkurse laden und byte-gleich neu gespeichert werden. Nur mit einer **Kopie** einer Welt benutzen (die Welt wird gespeichert) |

### Sichtfeld vom Videomodul und Kamerawinkel pro Drohne (video, drone, content, sim, api; Nachtrag 02.10.2026)

Wie bei echten Quads kommt das FPV-Sichtfeld von der Kamera im Videomodul, und der Kamerawinkel (Uptilt) gehört zur
Drohne. Recherche der Sichtfelder: `docs/design/realism/power-video.md` §4.4; Bereiche der Kamerahalter und Props im Bild:
`docs/design/realism/props-in-view.md` §9. Flugmodell unberührt (Bewegungs-Fingerabdrücke bitgleich).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `video/FovMath` | video | `declaredHorizontalDegrees(VideoDefinition)` = `camera_fov_deg`, sonst Standard des Systems (`defaultHorizontalDegrees`: analog 120°, digital 130°, Kreativ 120°); `cameraHorizontalDegrees(VideoType/VideoDefinition)` = dasselbe auf 60–160° begrenzt (geradlinig zeichenbar) – **die** Quelle für FPV-Bild, Analyse (`sim.FpvRig`, `AirframeSpec.cameraFovDeg`), Video-Tab, Tooltips und API; `fromModule(video)` = Modul hat einen eigenen Wert |
| `video/client/VideoConfig` | video | kein Sichtfeld je Brille mehr (`fovAnalog`/`fovDigital` entfallen, alte Werte werden ignoriert); `creativeFovFromModule` (Standard an) und `fovCreative` nur für die Kreativ-Brille; `horizontalFov(brille, kamera)` |
| `video/client/VideoState#horizontalFov()`, `camera()` | video | Sichtfeld und Videomodul der gezeigten Drohne (eigene FPV-Drohne, beim Zuschauen die fremde, im Selbsttest die Testquelle); folgt einem Drohnenwechsel im selben Frame |
| `video/client/VideoConfigTab` | video | analog/digital: nur Anzeige (`ui.client.widget.InfoField`, „Sichtfeld 125° · Kamera“) und Zeile mit dem Modulnamen; Kreativ: Umschalter „Vom Videomodul/Eigenes“ und Regler (nur bei „Eigenes“ aktiv) |
| `drone/CameraMount` | drone | Kamerahalter je Rahmen: `range(frame)` = Bereich der Rahmenklasse (`roleRange(FrameRole)`: Whoop 10–35°, Toothpick 10–40°, Cinewhoop 0–35°, Freestyle 0–50°, Race 15–70°, Long Range 0–40°, Cinelifter 0–30°, X-Class 0–50°), immer erweitert um den Standard des Rahmens (`camera.tilt_deg`); `uptilt(build, tune)` = gespeicherter Wert (`camera.uptiltDeg`) auf den Halter begrenzt, ohne Wert der Standard; `sanitize`/`withUptilt` speichern nur Abweichungen vom Standard. Ersetzt `drone.client.CameraUptilt` und die globale Einstellung `DroneClientConfig.customCameraUptilt`/`cameraUptiltDeg` (entfallen, alte Werte werden ignoriert) |
| `drone/DronePiloting#sanitize` | drone | begrenzt den Kamerawinkel jeder Tuning-Änderung (Konfigurator, Import, Werkbank-Import, API `withTune`) auf den Halter |
| `drone/DroneServiceMenu` | drone | Knopf-Ids `UPTILT_BUTTON` (1000) + Grad (0–80) über das Vanilla-Paket `ServerboundContainerButtonClickPacket` (`clickMenuButton`); der Server setzt `drone.setTune` (über die Entity-Daten an alle Spieler synchronisiert); kein eigenes Netzpaket, Netzprotokoll unverändert |
| `drone/client/DroneServiceScreen` | drone | Kamera-Leiste (244 px hohes Menü): Winkelanzeige, Regler im Bereich des Halters, `−`/`+` (Shift 5°), `↺` Standard, Props im Bild für den gewählten Winkel und Sichtfeld der Kamera; die Vorschau kippt die gezeichnete Kamera mit (`DronePreview.render(…, cameraTiltDegrees, …)`); Änderungen gehen höchstens einmal je Tick an den Server, ohne Bestätigung nach 2,5 s zurück |
| `content/DroneAssembly` | content | beim Zerlegen wandert der Kamerawinkel an den Rahmen (Rahmen-Item mit `propwash:flight_tune` nur aus `camera.uptiltDeg`), das übrige Tuning an den Stack; beim Bauen gilt der Winkel des Rahmens (sonst der des Stacks), begrenzt auf den neuen Rahmen (`assembledTune`, `frameUptilt`) |
| `content/PartDataLines`, `drone/QuadcopterItem` | content, drone | Videomodule zeigen immer „Kamera-Sichtfeld (horizontal)“; die Drohne zeigt „Kamera: 130° Sichtfeld, 28° Winkel“ |
| Parts-API 1.4.0 | api | `CameraView#uptiltRange()` (`UptiltRange` min/max/Standard), `BuildAccess#cameraView(spec, uptiltDeg)`, `BuildAccess#uptiltRange(frame)`, `TuneSpec.CAMERA_UPTILT`; `camera_fov_deg` wirkt (Vertrag `docs/design/parts-api.md` §3.1) |
| Selbsttests | video, drone | „video: Sichtfeld folgt dem Videomodul der Drohne“ (`video.client.CameraFovSelfTests`), „drone: Kamerawinkel pro Drohne im Wartungsmenü“ (`drone.client.CameraAngleSelfTests`); der Props-im-Bild-Selbsttest prüft Sichtfeld und Uptilt des Bilds gegen die Analyse; Bildschirmfotos `drone_service_camera(_de)`, `configurator_video_analog(_de)`, `configurator_video_digital`, `configurator_video_creative` |

### Bildformat 16:9 / 4:3 (video, osd, drone, content; 0.5.0, Entwurf `docs/design/image-format.md`)

Persönliche Anzeige-Einstellung, kein Netzwerk-Feld, Flugmodell unberührt. 16:9 rechnet bitgleich wie zuvor.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `video/ImageFormat` | video | `AUTO`, `WIDE`, `FOUR_THREE`; `resolve(sender)`: Auto = analog → 4:3, sonst 16:9; `FOUR_THREE_ASPECT` = 4/3 (reines Java) |
| `video/ImageArea` | video | Bildbereich in Framebuffer-Pixeln (`of(format, w, h)`, `full`): 4:3 mittig mit gleich breiten Rändern, Pillar- oder Letterbox; `spanAspect()` = Bildbreite / Fensterhöhe für die Projektion, `imageTangentX/Y`, `wideTangentY` (Ausschnitt der Action-Cam) |
| `video/FovMath` | video | `verticalFromHorizontal(H, ImageArea)`: das Sichtfeld des Moduls spannt die Bildbreite; `verticalWithin(V, Anteil)` für das OSD bei Letterbox; `horizontalFromVertical` |
| `video/client/VideoConfig#imageFormat`, `VideoConfigTab` | video | Standard `WIDE`; Umschalter „Bildformat“ (Auto zeigt das aufgelöste Format), Zeile mit Breite × Höhe des Bildes, Vorschau-Kacheln im Format |
| `video/client/FpvImage` | video | `format()` (aufgelöst nach dem Sender der gezeigten Drohne, beim Zuschauen der fremden), `area()` (voll außerhalb von FPV/Zuschauen), `editorFormat()` mit ungespeichertem Entwurf aus dem Video-Tab |
| `video/client/VideoState#fpvFov` | video | senkrechtes Sichtfeld aus `FpvImage.area(...).spanAspect()` |
| `video/client/VideoPass`, `post/video_feed.fsh` | video | UBO um `vec4 Area` erweitert; Effekte in Bildkoordinaten, außerhalb Schwarz |
| `video/client/VideoOverlay` | video | Ränder als HUD-Ebene `propwash:video_feed` in Pixeln (`barsDrawn()`), Ersatz-Overlay im Bildbereich |
| `osd/client/OsdHud#grid` | osd | OSD-Raster im Bildbereich (16:9: `OsdGrid.screen`); Projektion und `OsdText.scale` danach; `OsdConfigTab#previewFormat` |
| `drone/client/DroneServiceScreen`, `content/client/WorkbenchReport` | drone, content | Props im Bild für das eigene Format (`props_in_view_4_3`); `BuildAnalysis` und API bleiben 16:9 |
| `video/client/record/ActionCamRecorder` | video | Ausschnitt mit dem 16:9-Sichtfeld, unabhängig vom Bildformat |
| Selbsttests | video, drone-render | `video.client.AspectSelfTests` (Tab, Editor, FPV mit analog/digital), `FpvCoverageSelfTests.BOXED_STEP`, Zuschauen mit Auto |

### Welt-Regeln: Propwash-Gamerules und Tab „Welt-Regeln“ (server, drone, video, race, sim, ui, guide; 0.5.0, Entwurf `docs/design/world-rules.md`)

Gamerules gelten für alle Spieler einer Welt; die physikrelevanten persönlichen Simulationsoptionen (früher Tab
„Simulation“, `DroneClientConfig`) und die Server-Regel `partWear` sind dorthin umgezogen. Persönlich bleiben Steuerung,
OSD, FPV-Anzeige und Grafik. Mit allen Standardwerten rechnet das Flugmodell bitgleich wie zuvor.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `server/rules/RuleSpec`, `WorldRuleSpecs`, `RuleValues`, `PhysicsRules`, `RuleEffects` | server | reines Java (ohne Minecraft-Typen, portabel): Katalog aller Propwash-Gamerules (`WorldRuleSpecs.all()` in Anzeige-Reihenfolge, `byName`), je Regel Name, Typ BOOL/INT, Standard, Bereich, Gruppe (`PHYSICS`, `WEATHER`, `BATTERY`, `CRASH`, `VIDEO`, `GAMEPLAY`), Anzeige (`TOGGLE`, `PERCENT`, `LEVELS`, `NUMBER`), Schrittweite, Sonderwert 0; Sprachschlüssel `gamerule.propwash.<name>`, `.description`, `.value.<n>`. `RuleValues` (fehlend/ungültig → Standard, `with` begrenzt), `PhysicsRules.of(values).toSimTuning()` (Standard = `new SimTuning()` Feld für Feld), `modified()` = veränderte Physik, `RuleEffects` (Crash-Schaden/-Stärke, unendliche Reichweite je Videosystem, `CREATIVE` wie analog, Kanalgrenze) |
| `server/WorldRules` | server | registriert jede Katalog-Regel als `GameRule` (`PropwashRegistries.list(Registries.GAME_RULE)`, Id `propwash:<name>`, Kategorie `WorldRules.CATEGORY` = `propwash:propwash`, Text `gamerule.category.propwash.propwash`) in `ServerModule.init`. Lesen nur auf dem Server-Thread: `values(server)`, `get/on(server, spec)`, `physics(server)`, `crashDamage`, `crashDamageStrength`, `infiniteRange(server, system)` (server `null` → Standardwerte). Schreiben: `set(server, spec, wert)` (ungültig → `false`). `mayEdit(spieler)` = Host des integrierten Servers oder Rechtestufe 2. `propwashRules()` = alle Gamerules im Namensraum `propwash` **oder** in der Propwash-Kategorie aus dem Register (auch solche ohne Katalog-Eintrag) |
| `server/WorldRulesS2C` (`propwash:world_rules`), `server/WorldRuleEditC2S` (`propwash:world_rule_edit`) | server | S2C: darf ändern + Liste (Id, Wert als Text wie `/gamerule`, höchstens 256, Wert höchstens 64 Zeichen) an **jeden** Spieler beim Betreten und in jedem Tick, wenn sich Werte oder das Recht geändert haben. C2S (höchstens 64 Einträge): der Server prüft Recht (sonst rote Meldung `propwash.server.world_rules.denied` mit Logzeile und Neusendung, höchstens zweimal je 5 s und Spieler, danach still verworfen), Namensraum/Kategorie, Wert per `GameRule#deserialize` (Bereich), setzt mit `GameRules#set`, schreibt ins Log und schickt allen Spielern `propwash.server.world_rules.changed` (grau). **Protokollversion 9** |
| `server/WorldRuleText` | server | Name, Wert (an/aus, Stufenname, „120 %“, Sonderwert), Bereich – auch für `/propwash rules` (Abschnitt „Welt-Regeln (/gamerule)“) |
| `server/rules/RulePreset`, `server/WorldRulePresetC2S` (`propwash:world_rule_preset`) | server | Voreinstellungen `REALISTIC` (= Standard), `TRAINING`, `ARCADE` als reines Java; jede legt jede Katalog-Regel fest (nicht genannt = Standard), `match(values)` findet die passende. C2S mit dem Namen (höchstens 32 Zeichen): `WorldRules#preset` prüft Recht (sonst `…denied`) und Namen, `applyPreset` setzt alle Regeln, schreibt ins Log und schickt allen **eine** Chatzeile `propwash.server.world_rules.preset_applied`, wenn sich etwas ändert. `/propwash rules preset <name>` (Rechtestufe 2). Werte und Entscheidungen in `docs/design/world-rules.md` §1a. Protokoll 11 |
| `server/client/ClientWorldRules` | server | Client, `INSTANCE`: `known`, `editable`, `version` (steigt bei jeder Wertänderung), `values`, `physics`, `raw`, `get/on`, `crashDamage`, `crashDamageStrength`, `infiniteRange(system)`; bis zum ersten Paket und nach dem Trennen Standardwerte |
| `server/client/WorldRulesTab` | server | Tab „Welt-Regeln“ (Id `world_rules`, Order 800, `Storage.MIXED`, Fußzeile `propwash.server.world_rules.footer`, schreibgeschützt `…footer_read_only` über `ConfigTab#readOnlyKey`), oben „Voreinstellungen“ (drei Knöpfe, gelten sofort über `WorldRulePresetC2S` und verwerfen Entwürfe der Katalog-Regeln; leuchten bei passenden angezeigten Werten, darunter „Entspricht …“ bzw. „Eigene Regeln …“; ohne Recht nur die Zeile), Abschnitte je Gruppe, „Weitere Regeln“ für Gamerules ohne Katalog-Eintrag; Schalter, Stufen, Regler (Prozent/Zahl bis 1000 Schritte), sonst Zahlenfeld; Entwurf bis „Speichern“ (dann `WorldRuleEditC2S`), Rechtsklick = Standard, „Standard“ setzt alle Regeln zurück; ohne Recht eine Tabelle mit Wert und Tooltip. Tooltip: Name, Beschreibung, Standard und Bereich, Befehl |
| `ui/client/config/ConfigTab#readOnlyKey()` | ui | Sprachschlüssel der Fußzeile für schreibgeschützte Tabs (Standard `propwash.ui.footer.read_only`) |
| `sim/SimTuning#batteryScale`, `PowerSystem#integrateBattery(h, unendlich, faktor)` | sim | Akkulaufzeit: der Verbrauch (`mAhUsed`, `packMah`) wird durch den Faktor geteilt (0,01–10, Standard 1 = bitgleich); `infiniteBattery` bleibt für 0 % |
| `drone/client/PilotSession` | drone | Tuning beim Start und bei jeder neuen `ClientWorldRules#version` aus `physics().toSimTuning()`; `physicsModified` aus `physics().modified()`; Funkqualität frei bei `infiniteRange(System der Drohne)`; Aufpralle nur mit Stärke > 0, Hitzeverschleiß nur mit `crashDamage()`. `DroneClientConfig` hat nur noch `stateSendHz`, `PhysicsAccess` und `SimulationConfigTab` entfallen |
| `drone/DroneWear` | drone | Server: Aufprall-Stärke = `WorldRules.crashDamageStrength` (die Stärke im `ImpactC2S` und die Kreativ-Brille zählen nicht mehr), Hitze-/ESC-Verschleiß nur mit `crash_damage` |
| `drone/DronePiloting#freeRange(spieler, drohne)` | drone | Kreativ, Kreativ-Brille oder `infiniteRange` für das System der Drohne → keine Funkgrenze bei Start, im Flug und bei Bewegungspaketen (die Server-Grenze `maxPilotDistance` gilt weiter) |
| `video/client/VideoState#range` | video | unendliche Videoreichweite bei der Regel für das Sendersystem (Kreativ-Brille mit eigener Reichweite behält ihre) |
| `server/ServerRules#pilotDenialReason`, `video/SpectateServer#transmittersExcept(drohne, pilot)` | server, video | `max_video_channels` > 0 und so viele andere Sender (Drohnen mit Pilot und Ladung, auch simulierte) → `propwash.server.deny.channels_full`; fliegende Piloten bleiben |
| `race/RaceServer#modifiedPhysics`, `race/client/ClientRaceTracker` | race | veränderte Physik = Flag der Drohne **oder** `WorldRules.physics(server).modified()` → kein Rekord, kein Geist |
| `guide/scenes/ConfiguratorScene` | guide | Schritt `world_rules` (vor `switches`) |

Neue Regel eines anderen Moduls: eine Zeile in `WorldRuleSpecs` plus Sprachschlüssel im eigenen Modul; lesen mit
`WorldRules.on/get(server, …)` bzw. `ClientWorldRules.INSTANCE.on/get(…)`. Registrierung, Sync, Tab, `/gamerule`,
`/propwash rules` und Export folgen von selbst. Physik-Regeln zusätzlich in `PhysicsRules`.

Export für die Website: `tools/export_rules.sh [ziel]` (JUnit `WorldRulesExportTest`) schreibt
`build/web-export/gamerules.json` (Format in `docs/design/world-rules.md` §5); fehlt ein Text in de/en, schlägt der Test fehl.

Selbsttest (`server.client.WorldRulesSelfTests`, von `ServerSelfTests` angemeldet): Registrierung und Katalog (jede
Propwash-Gamerule im Register braucht einen Katalog-Eintrag), `/gamerule`, Tab mit Speichern, ungültige Pakete,
nur lesbar, Videokanäle, unendliche Reichweite, Crash-Schaden, Voreinstellungen über Tab und Befehl; `drone.client.GravitySelfTests` („drone: Welt-Regel
Schwerkraft wirkt im Flug“). Bildschirmfotos `configurator_world_rules(_gravity|_wind|_readonly)` und deutsch
`configurator_world_rules_gravity_de`, `_video_de`, `_readonly_de`.

### Treffer und Greifer (interact; Entwurf `docs/design/drone-interaction.md`, Propwash 0.5.0)
Neues Modul `interact` (`interact.InteractModule` in `PropwashCommon.init()` nach `TrickModule`, `interact.client.InteractClient`
in `PropwashClient.init()` nach `TrickClient`, Sprachquellen `common/src/lang/interact/`, Selbsttests
`interact.client.InteractSelfTests` nach den Trick-Selbsttests).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `interact/InteractRules` | interact | Welt-Regel `DRONE_HITS_ENTITIES` = `WorldRuleSpecs.DRONE_HITS_ENTITIES` (`propwash:drone_hits_entities`, Schalter, Standard an, Gruppe `GAMEPLAY`; registriert, synchronisiert und angezeigt von den Welt-Regeln), `dronesHitEntities(server)` (Fehler → Standard), `rule()` (die registrierte `GameRule<Boolean>`); Client `interact.client.ClientInteractRules#dronesHitEntities()` aus `ClientWorldRules` (vor dem ersten Paket aus) |
| `interact/StrikeMath`, `StrikeRules`, `StrikeTargets` | interact | reine Rechnung: Treffer aus Masse × Annäherungstempo (unter 2 m/s oder 0,4 kg·m/s keiner, 0,4 HP je kg·m/s bis 10 HP, Rückstoß 0,05 je kg·m/s, 0,1–1,2), Entscheidung (Regel, eigener Pilot, PvP, 10 Ticks je Ziel, 8 je Sekunde), Tempo-Grenze und Reichweite; treffbar = lebende `LivingEntity` außer Zuschauern, Rüstungsständern und dem Piloten |
| `interact/client/PilotInteraction` | interact | von `drone.client.PilotSession` aufgerufen: `flightBuild(drone)` (Build mit Fracht, auch beim Start), `sameAirframe` (nur die Fracht unterscheidet sich → `DroneSim#setAirframe` statt `setBuild`), `clip(drone, sim, delta)` vor `QuadcopterEntity#move` (Lebewesen als Kollisionsboxen, nur mit Spielregel an), `afterMove`, `afterSlice` (harter Absturz bei Fracht), `afterFrame(drone, epoch)` (Treffer- und Abwurf-Pakete, Funkenschalter `CARGO_DROP`), `start`/`reset` |
| `interact/DroneStrikes` | interact | Server: `DroneStrikeC2S` prüfen (Pilot, Epoche, Regel, PvP, Abklingzeit, Tempo-Spitze der letzten 20 Ticks aus `QuadcopterEntity.SERVER_MOVED`, Reichweite), Schaden mit Schadensart `DRONE_STRIKE` = `propwash:drone_strike` (direkt die Drohne, Verursacher der Pilot), danach eigener Rückstoß in Flugrichtung; Event `STRUCK` (Server-Thread) |
| `interact/DroneCargo` | interact | Server: Fracht `QuadcopterEntity#cargoHold()` (`Hold`: Stapel, Abgeworfenes ignorieren, Ticks nicht scharf), `tick` aus `QuadcopterEntity#tickServer` (Aufnehmen alle 4 Ticks im Kasten ±0,6/−0,9/+0,25, nur scharf und gesteuert; Absetzen nach 10 Ticks nicht scharf), `release(drone, grund)` (danach erst wieder Aufnehmen, wenn die Drohne 1,5 Blöcke vom Abwurfpunkt weg ist), `onRemoved` (bei `RemovalReason#shouldDestroy`), `load`/`sync`, `capacityGrams(build)`, `gramsEach(stack)`; gespeichert als `Cargo` (`SAVE_CODEC`, nur wenn nicht leer); Event `CHANGED` |
| `interact/CargoPlan`, `ItemWeights`, `GripperSpec`, `CargoMass` | interact | reine Rechnung: wie viele Items passen (Gewicht, Stapel, Platz in gleichen Stapeln), Gewicht je Item (Propwash-Teile echte Masse, sonst 150/40/50/10 g), Tragkraft min(400 g, ½ Schubreserve), 4 Stapel, Masse in 5-g-Schritten; `withPayload(build, g)` ersetzt den Greifer durch `AccessoryType#laden(g)` |
| `build/AccessoryType` | content | `GRIPPER` (`propwash:gripper`, unten, 18 g), `isGripper()`, `hangsStraight()`, `laden(g)`/`unladen()`/`payloadGrams()` (Varianten nur fürs Flugmodell, gleicher Schlüssel, nie gespeichert) |
| `build/AccessoryAnchor` | content | `tiltFor(type)` (0° für hängendes Zubehör, sonst die Neigung der Halterung), `centerOf`/`corners`/`stretch` nehmen sie; für alle bisherigen Zubehöre bitgleich |
| `sim/DroneSim#setAirframe(AirframeSpec)` | sim | tauscht im Flug nur die Kennwerte (Masse, Trägheit, Schwerpunkt) wie der Konstruktor und frischt die Schwebegas-Schätzung auf; Zustand, Filter und Regler bleiben; gleiche Kennwerte → `false` |
| `drone/QuadcopterEntity` | drone | `cargoHold()`, `cargoGrams()`, `cargoDisplay()`, `setCargoSync(g, item)` (synchronisiert: Gesamtgewicht und schwerstes Item) |
| `drone/client/render` | drone-render | `DroneRenderState#cargo`/`hasCargo` (`QuadcopterRenderer#extractRenderState`, Kontext `GROUND`), `DroneVisuals#submitCargo` hängt das Item unter die Backen |
| `input/SwitchFunction.CARGO_DROP`, `KeyDefaults.CARGO_DROP` | input | Funkenschalter „Fracht abwerfen“ (Taster, Zeile im Tab „Modi“), Taste `key.propwash.drone_cargo_drop` (Standard Enter, `interact.client.CargoKeys`) |
| `sim/PilotInput.mode3d`, `FlightControllerConfig.deadband3d`, `Telemetry.mode3d`, `ArmBlocker.THROTTLE_NOT_CENTERED`, `AirframeSpec.reverseThrust/reverseTorque/reversePitch` | sim | 3D-Modus im Flugmodell (`docs/design/flight-model-spec.md` 6.13): Gasmitte mit Totzone, umkehrbare Motoren nur auf eigenem Rechenweg; ohne 3D bitgleich. `Telemetry.throttleOut`, `motorRpm`, `motorThrustN`, `totalThrustN` tragen in 3D das Vorzeichen der Richtung – Verbraucher, die nur die Größe brauchen, nehmen den Betrag (Motorklang, Prop-Drehung) |
| `input/SwitchFunction.MODE_3D`, `input/Mode3dSelector`, `input/ThrottleCentering`, `RadioFrame.throttle3d`/`RadioState#throttle3d()`, `InputConfig.mode3dUnlocked`, `KeyDefaults.MODE_3D` | input | 3D-Modus: Belegung, Auswahl (letzte Eingabe zählt), Gas mit kalibrierter Mitte (`StickMapper#throttle3d`), Rastpunkt für Tastatur und schrittweises Gas, persönliche Freischaltung (Tab „Modi“, gespeichert mit den Modi), Taste Ziffernblock 6 |
| `api/part/PropDefinition#reverseThrust()` | api | optionaler Umkehrschub eines Props (0,2–1,0, JSON `reverse_thrust`); leer = normaler Prop (Modellwert 0,55) |
| `server/rules/WorldRuleSpecs.ALLOW_3D_MODE` | server | Welt-Regel `propwash:allow_3d_mode` (Schalter, Standard an, Gruppe Spiel, keine Physik): aus hält den 3D-Modus für alle aus |
| `guide/scenes/Mode3dScene`, `api/client/FlightSchoolScenes.MODE_3D` | guide | Flugschul-Lektion „3D-Modus“ (`propwash:mode_3d`, nach „Höhe und Position halten“); Szenen-Drohnen mit negativem Gas drehen ihre Props rückwärts |
| `interact/BreakMath`, `BreakRules` | interact | reine Rechnung: Energie ½·m·v² entlang der Normalen gegen die Schwelle max(5 J, 400 J × Härte × (0,2 + 0,8 × Dicke)), nie über Härte 1 und unter 3 m/s, Server mit 90 % der Schwelle; Rest-Tempo; Reihenfolge der Prüfungen (`decide`), Gründe `Outcome` (Hinweis nur bei Gate-Rahmen, Spielmodus, Schutz), Merkdauer, Rahmen eines Freiform-Gates (`frame`) |
| `interact/BreakableBlocks` | interact | Block-Tag `propwash:drone_breakable` (`data/propwash/tags/block`), `candidate` (Tag, Härte, kein `GameMasterBlock`), `threshold` (Dicke aus der Kollisionsform entlang der Achse), `courseFrame` (Freiform-Gates in der Nähe) |
| `interact/DroneBreaks` | interact | Server: `DroneBreakC2S` (Pilot, Epoche) und `attempt` (Regel, Block, 20 je Sekunde, Tempo-Spitze aus `DroneStrikes`, Reichweite, Energie, Gate-Rahmen, `blockActionRestricted`, `mayInteract`, `GUARDS` (intern, Selbsttest), `IPlatformHelper#allowBlockBreak`); Freigabe vorab `DroneBreakClearC2S` → `clearance` (dieselben Rechte ohne Tempo und Energie, Probe `IPlatformHelper#probeBlockBreak`, nach der ersten Schutz-Absage 24 Blöcke ohne Probe, 20 Anfragen je Sekunde) → `DroneBreakClearS2C`; Bruch wie Abbau ohne Werkzeug (`cracked`, `LevelEvent` 2001 ohne den Piloten, `dropResources` mit leerem Werkzeug, Flüssigkeit bleibt, `BLOCK_DESTROY` mit der Drohne, `afterBlockBreak`); Ablehnung mit Block-Update und `DroneBreakDeniedS2C`; Ereignisse `BROKEN`, `DENIED`; Vertrag `docs/design/drone-break-blocks.md` |
| `interact/client/PilotBreaks` | interact | aus `PilotInteraction#clip` vor jeder Bewegung: Bruch vorhersagen (Achsenreihenfolge wie Vanilla, Spielmodus, Weltgrenze, Gate-Rahmen, gemerkte Ablehnungen, Vorhersage nur mit Freigabe vorab, sonst Wand), Block lokal entfernen, `DroneSim#breakThrough`, Anfragen aus `PilotInteraction#afterFrame`; dort auch `clearAhead` (alle 2 Ticks Tag-Blöcke vor der Drohne, 6 Blöcke Rand + bis 1 s Flug, höchstens 32 je Anfrage, Freigabe 10 s); Hinweise als Toast; `reset` mit der Sitzung, `forget` beim Trennen |
| `sim/DroneSim#breakThrough(nx, ny, nz, joule)` | sim | senkt die Normalkomponente der Geschwindigkeit um das, was die Energie kostet, nie unter 0, und ordnet das verlorene Tempo wie einen Aufprall ein (Ereignisse, Absturz und Entschärfen, `ImpactSample` mit Normale); ohne Aufruf bitgleich |
| `platform/IPlatformHelper#allowBlockBreak`, `#probeBlockBreak`, `#afterBlockBreak` | platform | Abbau-Ereignisse der Loader für Brüche durch Drohnen, Verursacher der Pilot: Fabric `PlayerBlockBreakEvents` BEFORE/CANCELED/AFTER (Probe nur BEFORE), NeoForge `BreakBlockEvent` (auch als Probe); Standard erlaubt |
| Netz | interact | `DroneStrikeC2S` (`propwash:drone_strike`), `CargoReleaseC2S` (`propwash:cargo_release`, nur `COMMAND`/`CRASH`), `DroneBreakC2S` (`propwash:drone_break`), `DroneBreakDeniedS2C` (`propwash:drone_break_denied`, Grund), `DroneBreakClearC2S` (`propwash:drone_break_clear`, ≤ 32 Blöcke), `DroneBreakClearS2C` (`propwash:drone_break_clearance`, Grund je Block); `Constants.PROTOCOL_VERSION` = 10 (0.5.0, mit GPS Rescue ohne Pilot 11) |
| Selbsttests | interact | `interact.client.InteractSelfTests` (beide Loader): Spielregel registriert und beim Client, Shift-Tooltip, Drohne prallt an einer Kuh ab (Schaden, Rückstoß in Flugrichtung, Absturz gezählt), Spielregel aus (fliegt hindurch), Greifer nimmt 8 Eisenbarren und 6 von 64 Bruchsteinen (380 g, Flugmodell schwerer), Taste wirft ab ohne sofortiges Wiedergreifen, Entschärfen setzt ab, Absturz an einer Mauer verliert die Fracht; mit `-PpropwashScreenshots=true` zusätzlich `drone_gripper_carry.png` und `tooltip_gripper.png` (Fenster wie im Rundgang), die Flugschul-Bilder `flight_school_gripper.png`/`flight_school_gripper_drop.png` kommen aus `GuideSelfTests` |
| Daten | interact, content | `data/propwash/damage_type/drone_strike.json`, Ergänzung der Tags `minecraft:no_knockback` und `minecraft:panic_causes`, Bauteil `accessory/gripper.json`, Rezept (`tools/data/survival.py`), Modell (`tools/assets/parts/accessories.py#build_gripper`), Szene `guide.scenes.GripperScene` (`gripper`, nach `action_cam`), `FlightSchoolScenes.GRIPPER` (API 1.5.0) |

### Bandos (bando, Entwurf `docs/design/bandos.md`)

- bando besitzt den Strukturtyp `propwash:bando` (`BandoModule.STRUCTURE_TYPE`, Codec: Strukturfelder + `family`), den
  Teil-Typ `propwash:bando` (`BandoModule.PIECE`, NBT: `Variant`, `Seed`, `Turns`, `OX`/`OY`/`OZ` – der Bauplan entsteht
  beim Laden neu aus Variante und Seed und darf sich darum zwischen Versionen nicht ändern, ohne dass bereits
  gespeicherte, noch nicht fertig erzeugte Bandos anders weitergebaut würden; Änderungen an `bando.plan` gelten als
  Weltformat-Änderung und gehören in die Release-Notizen), die Strukturen `propwash:bando/<familie>`, den Tag
  `#propwash:bandos`, das Strukturset `propwash:bandos` und die Beutetabelle `propwash:chests/bando` (stabile Ids;
  Addons hängen sich über `PropwashApi.loot()` ein). Alle Daten erzeugt `tools/data/bandos.py`.
- Das Strukturset benutzt die Platzierung `propwash:world_course` (race, `race.world.WorldCoursePlacement`: `random_spread`
  plus `avoid`). race hält deren Codec stabil; ein neues Feld nur optional.
- `bando.plan` ist reines Java (keine Minecraft-Typen) und portierbar; Paletten sind Blockzustand-Texte, die
  `BandoBlocks` einmal je Text mit `BlockStateParser` auflöst.
- Die Flugschul-Szene `bandos` meldet `bando.client.BandoClient` über `GuideScenes.register` an (nicht in
  `GuideScenes.bootstrap`), ihre Texte stehen in `common/src/lang/bando/`; JUnit `BandoSceneTest` prüft sie wie
  `FlightSchoolScenesTest` die eingebauten Szenen.
- Kein eigener Schalter, keine Server-Regel: Bandos folgen „Bauwerke generieren“ und Datenpaketen wie Vanilla-Strukturen.
- Selbsttest: Schritte `bando: …` in der Testwelt und nach dem Verlassen in zwei eigenen Welten
  (`SelfTestRunner.WORLD_NAME + " Bandos"` bzw. `… Bandos aus`, Normal-Welt, Seed 20261003); sie stehen als letzte
  Nach-dem-Verlassen-Schritte und verlassen ihre Welten selbst wieder.

### GPS Rescue ohne Pilot und bessere Hinderniserkennung (rescue, drone, server; Entwurf `docs/design/autonomous-rescue.md`, Propwash 0.5.0)
rescue läuft damit auf beiden Seiten: Der Autopilot fliegt am Client des Piloten und, wenn der Pilot im Heimflug aussteigt,
auf dem Server weiter.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `rescue/RescueAutopilot` | rescue | zusätzlich `setAutonomous(bool)` (ohne Pilot: immer landen, nach dem Aufgeben an Ort und Stelle landen), `landNow(world)`, `stuck()` (mit Pilot aufgegeben, schwebt), `gaveUp()`, `following()`, `landX()`/`landZ()`; Sicherheitshöhe aus dem ganzen Weg (bis 256 m), lokaler Weg über `RoutePlanner`, Landeplatz über `RescueWorld#ground` |
| `rescue/RescueWorld#ground(x, y, z)`, `rescue/Ground` | rescue | Art des Blocks (`NONE`, `SOLID`, `FOLIAGE`, `WATER`, `LAVA`, `HAZARD`, `UNKNOWN`); Standard aus `solid`; `LevelRescueWorld` und `ServerRescueWorld` ordnen über `rescue/RescueBlocks#classify` ein |
| `rescue/RoutePlanner` | rescue | reines Java: begrenztes A* (±20/−6/+12 Blöcke, 2500 Knoten) mit `Goal` (`estimate`, `reached`), Wegpunkte mit Sichtlinien geglättet |
| `rescue/ReturnPlan` | rescue | Plan an der Drohne (`QuadcopterEntity#returnPlan`, NBT `ReturnPlan`): Besitzer, Homepoint, beim Piloten, Höhe, Tempo, Sinkrate, Hinderniserkennung, Abstand, Mindestentfernung, sofort landen, Ticks; `settings()` bereinigt und landet immer |
| `rescue/net/RescueStatusC2S` | rescue | `propwash:rescue_status` vom Piloten: aktiv oder nicht, Homepoint, beim Piloten, Einstellungen; der Server nimmt es nur vom Piloten der Drohne, Homepoint nur innerhalb seiner Funkreichweite (+32) und außerhalb eines für ihn gesperrten Spawn-Bereichs. `rescue.client.RescueController` schickt es bei Start, Ende, Einstellungen und Homepoint-Bewegung (> 0,5 m, höchstens alle 250 ms), nie beim Ende der Sitzung |
| `rescue/AutonomousReturns` | rescue | Server: übernimmt bei `PILOT_CHANGED` (Pilot → keiner) mit aktiver Meldung, Akku und Welt-Regel; fliegt im Tick der Drohne (`QuadcopterEntity#setServerAutopilot`), hält dabei vor und nach der Bewegung ein Ticket `propwash:drone` (Radius 2) über `RangeManager#keepSimulated`, nur wenn `RangeManager#generatedAround` stimmt; Budget 8 je Server, 2 je Spieler (`AutonomyBudget`, sonst senkrechtes Absinken ohne Ticket), 12 000 Ticks, dann Landung, nach 1200 weiteren aus; Chat-Meldungen an den Besitzer; `flights()`, `descents()`, `flying(drohne)`, `phase(drohne)`, `reported(drohne)` für Tests |
| `drone/QuadcopterEntity` | drone | `returnPlan()`, `setReturnPlan`, `returningHome()`, `RETURN_PLAN_KEY`; `tickServer` fragt den Hook nur mit Plan und ohne Pilot und überspringt dann die lose Physik; ein neuer Pilot löscht den Plan |
| `drone/DronePiloting` | drone | `StartResult.RETURNING`: Funke benutzen bei Plan → `ReturnPlan#withLandNow`, Meldung `propwash.rescue.autonomous.land_now`; Shift + Funke zeigt `propwash.rescue.autonomous.status` |
| `drone/WindModel` | drone | von `drone.client` nach `drone` (öffentlich), Client und Server rechnen denselben Wind |
| `server/RangeManager#generatedAround`, `#keepSimulated`; `server/ServerRules#forbiddenSpawnArea(spieler, level, pos)` | server | Helfer für autonome Flüge (gemeinsamer `TicketLedger`, Spawn-Schutz für eine Position) |
| `server/rules/WorldRuleSpecs.AUTONOMOUS_RESCUE` | server | Welt-Regel `propwash:autonomous_rescue` (Schalter, Standard an, Gruppe Spiel, keine Physik) |
| Netz | rescue | `RescueStatusC2S`; `Constants.PROTOCOL_VERSION` = 11 (0.5.0) |
| Selbsttests | rescue | `rescue.client.AutonomousRescueSelfTests` (Fabric, Schritte `rescue: ohne Pilot – …`), mit `-PpropwashScreenshots=true` `rescue_autonomous_landing.png` und `rescue_autonomous_door.png` |

### Kreativ-Drohne und Drohnen-Editor (creative; Entwurf `docs/design/creative-drone.md`, Propwash 0.5.0)
Neues Modul `creative` (`creative.CreativeModule` in `PropwashCommon.init()` nach `InteractModule`,
`creative.client.CreativeClient` in `PropwashClient.init()` nach `InteractClient`, Sprachquellen
`common/src/lang/creative/`, Selbsttests `creative.client.CreativeSelfTests` nach den Interact-Selbsttests, Bildschirmfotos
`creative.client.CreativeShowcase` am Ende des Rundgangs).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `build/StackType.CREATIVE_FC`, `#creative()`, `#fits(frame)` | build | eingebautes Teil `propwash:creative_fc`; `fits` (Kreativ-FC passt in jeden Rahmen, sonst Stack-Halterung) nutzen `DroneBuild#problems`, Shift-Tooltip und die Addon-Warnung in `PartsModule` |
| `build/DroneBuild#creative()`, `withCreative`, `isCreative`, `edited`, `flight`, `inertiaOverride` | build | Komponente `CreativeSpec` (nur mit Kreativ-FC, sonst `EMPTY`); `flight()` = Flug-Bauplan (sonst `this`), `inertiaOverride()` = Trägheit je Achse in kg·m² oder `null` |
| `creative/CreativeSpec`, `CreativeParam`, `CreativeLimits`, `CreativeShareCode`, `CreativeCheck`, `CreativeDraft` | creative | Werte (Basis-Teile + Werte in Anzeige-Einheiten), Katalog mit Bereichen, Begrenzen, Teilen-Code `PWD1-`, Hinweise, Entwurf; Schlüssel der Werte sind Teil des Speicherformats und bleiben stabil |
| `creative/CreativeFlight` | creative | Flug-Bauplan über `PartJson.encode`/`decode` und `PartIndex.detached` (Zwischenspeicher 64), `baseValue`/`value` je Wert (auch Automatikwerte), Flug-Stack immer 1–12S |
| `creative/CreativeCodecs`, `CreativeModule.SPEC` | creative | Codec und Stream (Bauplan-Feld `creative`, Item-Komponente `propwash:creative_spec` am FC) |
| `creative/CreativeEdits`, `creative/net/CreativeEditC2S` | creative | Payload `propwash:creative_edit`: Kreativmodus, Zugriff wie beim Tuning, Begrenzen, brauchbares Flugmodell, setzt Bauplan und optional den Uptilt |
| `sim/ParamDerivation`, `sim/BuildAnalysis`, `sim/BoundedCache`, `sim/AirframeSpec#defaultSideAreaM2/defaultTopAreaM2/defaultBrownoutVoltage` | sim | bearbeitete Baupläne: `compute(source, flight, inertia)` mit Trägheit nach `lens`, LRU-Speicher (128/32), `buildKey` mit `/creative/…`; normale Baupläne bitgleich |
| `content/DroneAssembly#check`, `#disassemble`, `PartItem`, `PartTooltips`, `ContentItems` | content | Werkbank übernimmt die Werte vom FC, Zerlegen legt sie zurück; Tooltip-Zeilen; eingebaute Teile mit `rarity` |
| `drone/CameraMount#range(DroneBuild)`, `sanitize(tune, build)`, `withUptilt(tune, build, deg)` | drone | Kreativ-Drohnen 0–80°, sonst wie bisher nach Rahmen |
| `video/client/VideoState#horizontalFov`, `video/client/record/FlashbackCam` | video | Sichtfeld aus dem Videomodul des Flug-Bauplans |
| `race/RaceServer.modifiedPhysics`, `race/client/ClientRaceTracker`, `guide/client/training/TrainingSession` | race, guide | Kreativ-Drohnen = veränderte Physik |
| `creative/client/CreativeEditorTab`, `CreativeTemplates`, `creative/CreativeScene` | creative | Tab `creative_editor` (290, nur mit Kreativ-Drohne), eigene Vorlagen (`config/propwash/creative_templates.json`), Szene `creative_drone` (nur im Kreativmodus) |
| Netz | creative | `CreativeEditC2S` (`propwash:creative_edit`); Bauplan-Stream nach einem Kreativ-FC zusätzlich die Werte; `Constants.PROTOCOL_VERSION` = 11 |

### Signal-Repeater und Kreativ-Brille in Rennen (relay, video, drone, osd, race, server; Entwurf `docs/design/signal-repeater.md`, Propwash 0.5.0)
Neues Modul `relay` (`relay.RelayModule` in `PropwashCommon.init()` nach `VideoModule`, `relay.client.RelayClient` in
`PropwashClient.init()` nach `VideoClient`, Sprachquellen `common/src/lang/relay/`, Selbsttests
`relay.client.RelaySelfTests` nach den Video-Selbsttests, Bildschirmfotos `relay.client.RelayShowcase` nach der
Abschattung im Rundgang).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `relay/RelayRules` | relay (reines Java) | Reichweite 128 Blöcke, voll bis 112, weicher Rand (`reachFactor`, `reachPenaltyDb`), höchstens 4 Repeater, `span(n)` = (n + 1) × 128 ≤ `RadioLink.FULL_RANGE` |
| `relay/RelayPaths` | relay (reines Java, JUnit `RelayPathsTest`) | Knoten 0 = Empfänger (Pilot), 1 = Sender (Drohne), ab 2 Repeater; symmetrische Strecken, `NaN` = keine; beste Kette mit 1…n Repeatern ohne die direkte Strecke, `WEAKEST` (Minimum) oder `NOISE` (Rauschsumme in dB), bei Gleichstand weniger Repeater |
| `relay/RelayLinks` | relay | Reserve einer Bild-Teilstrecke (`LinkBudget.marginDb` − Abschattung − Rand), Funk-Teilstrecke (`RadioLink.quality` nach Abstand, Rand), Funk gesamt = min(Server-Grenze der direkten Entfernung, max(direkt, Kette)); digital `WEAKEST`, analog `NOISE` |
| `relay/RelayCandidates` | relay (reines Java) | Repeater in der Ellipse Abstand(Pilot) + Abstand(Drohne) ≤ `span`, sortiert nach diesem Umweg, höchstens 16 |
| `relay/SignalRepeaterBlock`, `SignalRepeaterBlockEntity`, `RelayModule` | relay | Block `propwash:signal_repeater` (`FACE`/`FACING` wie ein Knopf, keine Kollision, Kolben werfen ab), Antennenpunkt 6/16 Block vor der Befestigungsfläche (`antenna`), Block-Entity ohne Daten und Ticker |
| `relay/RepeaterIndex` | relay | je `Level` (schwach gehalten, synchronisiert), Zellen 128 × 128; Eintrag bei `BlockEntity#clearRemoved`, Austrag bei `setRemoved` (nur vom selben Block-Entity), Lagewechsel über `setBlockState`; `collect(minX, minZ, maxX, maxZ, sink)` |
| `relay/client/RelayVideo` | relay | in `VideoState`: Teilstrecken mit eigener `ObstructionScan`, 24 000 Zellen je Tick, Drohnen-Strecken laufend, Piloten-Strecken alle 10 Ticks oder nach Bewegung, Repeater untereinander alle 100 Ticks, Vergessen nach 200 Ticks; `solve` je Bild mit den aktuellen Abständen |
| `relay/client/RelayControl` | relay | in `PilotSession#updateLinkQuality`; ohne Repeater oder bei vollem direkten Funk unverändert der direkte Wert |
| `video/client/VideoState#relayHops`, `relayHopQuality`, `directQuality`, `relay` | video | Kette nur, wenn besser als direkt; Mehrwege dann ohne Teil-Schatten der direkten Strecke; keine Kette bei unendlicher Reichweite |
| `video/client/ClientBlockLoss#material` | video | Repeater = Luft |
| `video/client/VideoConfig#creativeLatencyFromModule` | video | Standard `true`: Kreativ-Brille mit der Latenz des Videosenders; Tab-Zeile „Latenz aus“ |
| `osd/OsdElement.RELAY`, `OsdData#relayHops`, `relayHopQuality` (`MAX_RELAY_HOPS` = 5) | osd | „RPT“, Gruppe `LINK`, Grundplatz 1/8, in Minimal/Standard/Rennen 1/6; nur sichtbar mit Kette |
| `server/rules/WorldRuleSpecs#SIGNAL_REPEATERS`, `PhysicsRules#SPECS`, `changed` | server | `propwash:signal_repeaters` 0–4, Standard 4, keine Physik, setzt der Client um; Liste der Physik-Regeln für Begründungen |
| `race/LapAssist`, `race/client/AssistText`, `ClientRaceTracker#assistNow`, `lapAssist`, `lastLapAssist`, `RaceHud#recentMessages` | race | Gründe als Bitmaske; Kreativ-Brille, freie Reichweite und Repeater sind keiner; Meldungen `propwash.race.msg.assist_start`, `assist_now`, `assisted_because` |
| Netz | – | unverändert, `Constants.PROTOCOL_VERSION` = 11 |

### Bindung bleibt in der Drohne: Empfänger merkt sich den Piloten, die Funke prüft die Brille (drone, content, video, guide; Entwurf `docs/design/receiver-binding.md`, Propwash 0.5.0)
Paket `drone.bind` (`ReceiverBinds.init()` am Ende von `DroneModule.init()`), Client `drone.bind.client`
(`BindClient.init()` am Ende von `DroneClient.init()`), Sprachquellen in `common/src/lang/drone/`, `…/video/` und
`…/guide/`, Selbsttests `drone.bind.client.BindSelfTests` direkt nach den Drohnen-Selbsttests und
`video.client.VideoTabSelfTests`, Bildschirmfotos über `BindSelfTests.registerScreenshots(deutsch)` und
`VideoTabSelfTests.registerScreenshots(deutsch)` im Rundgang. Brillen werden
nicht mehr gebunden.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `drone/bind/ReceiverBind` | drone (reines Java) | `(pilot)`; `CLEARED` = (null) = zurückgesetzt; `of(pilot)` |
| `drone/bind/BindAccess` | drone (reines Java, JUnit `BindAccessTest`) | `of(bind, spieler)`: `UNBOUND` (null), `CLEARED`, `PILOT` (Pilot gleich), sonst `OTHER`; `connects()` = `UNBOUND`/`PILOT`, `matches()` = `PILOT` (Suche), `resettable()` = `PILOT`/`OTHER` |
| `drone/bind/BindSearch` | drone (reines Java, JUnit `BindSearchTest`) | `pick`: nur `matches()`, kleinster Abstand; unendlicher Abstand zählt nicht |
| `drone/bind/BindCodecs`, `BindComponents` | drone | Codec (`pilot` optional; `CLEARED` = `{}`; das alte Feld `lock` wird überlesen) und Stream (optionale UUID), Komponente `propwash:receiver_bind`; `of`, `store`, `copy` |
| `drone/bind/ReceiverBinds` | drone | Server: `access`, `bind` (gibt den Zugriff davor zurück), `reset` (nur wenn gebunden), `nearestBound` (128 Blöcke, nicht von anderen geflogen), `point` (Funke auf die Drohne); `legacySecretFile`, `dropLegacyFile` (löscht beim Serverstart das alte Welt-Geheimnis `data/propwash/receiver_bind.dat`) |
| `drone/bind/BindTooltips` | drone | `VIEWER` (Client setzt die eigene UUID), `access(stack)`, `line(stack)` mit Schlüsseln `propwash.drone.bind.tooltip.<zugriff>` |
| `drone/bind/client/BindClient` | drone | setzt `BindTooltips.VIEWER`, löscht beim Start die alte `config/propwash/binding.json` |
| `drone/QuadcopterEntity#receiverBind`, `setReceiverBind`, `RECEIVER_BIND_KEY` | drone | NBT `ReceiverBind` (nur Server), `toStack`/`loadFromStack` mit der Komponente; `getPickResult` behält sie (ohne `drone_id`) |
| `content/DroneAssembly#createDrone`, `#disassemble` | content | Bindung wandert wie `drone_id` zwischen Drohnen-Item und Flugcontroller |
| `drone/PilotRules` | drone (reines Java, JUnit `PilotRulesTest`) | `decide(getragene Brille oder null, Videosystem der Drohne)`: `LINE_OF_SIGHT` (keine Brille), `FPV` (`VideoSystem.receives`), `NO_VIDEO` (falsche Brille); `mode()`, `connects()` (nicht bei `NO_VIDEO`), `blind()`, `changed(vorher, jetzt)` |
| `drone/DronePiloting#decideMode`, `#useRemote`, `#recheckView`, `#viewOf`, `#videoMismatch`, `#dropLegacyBind` | drone | `decideMode` liest nur die Brille auf dem Kopf; `useRemote`: Drohne der Funke da → `access().connects()` sonst `propwash.drone.bind.denied_other` bzw. `denied_cleared`; fehlt sie → `nearestBound`; dann Brille prüfen (`NO_VIDEO` → `propwash.drone.session.refused_video` mit beiden Systemen, kein Start, Funke unverändert), erst danach `point` und Start, Meldung nach einer Suche in `propwash.drone.bind.via_receiver`; jede Runde `recheckView` (Modus FPV/Sichtflug nach der getragenen Brille, Meldungen `switched_fpv`, `switched_los`, `no_video`), `viewOf` = letzter Stand je Pilot; Schleichen = Status ohne Suche |
| `drone/DroneInteraction#resolve`, `DroneInteractions#bindRemote`, `#checkGoggles` | drone | Funke + von anderem geflogen = `OCCUPIED`; Binden prüft zuerst die Brille (`propwash.drone.bind.refused_video`), schreibt dann `ReceiverBind(Pilot)`, Meldung `remote` bzw. `remote_taken` in `propwash.drone.bind.view.fpv`/`.los`; Brille in der Hand = `CHECK_GOGGLES`: nur Meldung `propwash.drone.goggles.fits`/`no_fit`, nichts wird gebunden |
| `content/FpvGogglesItem#inventoryTick` | content | entfernt die alte Komponente `propwash:bound_drone` von Brillen; Tooltip ohne Bindungs-Status |
| `video/client/GogglesBlackout`, `VideoOverlay#noVideo`, `osd/client/RadioHud` | video, osd | Client: Sichtflug + getragene Brille, die das Videosystem nicht empfängt → deckendes Bild (analog Rauschen, digital schwarz) mit `propwash.video.no_signal`, `propwash.video.mismatch`, `propwash.video.mismatch.hint`; Funk-HUD solange aus; `current()`, `active()`, `frames()` |
| `video/client/VideoTabRules`, `VideoConfig#latencyMs`, `#extraNoise`, `#obstructs`, `#rangeBlocks`, `#keepCreative` | video (reines Java, JUnit `VideoTabRulesTest`) | Tab zeigt Einstellungen nur mit getragener Brille (`showsSettings`), Kreativ-Werte nur mit Kreativ-Brille (`showsCreative`); `defaults` und `saved` lassen verborgene Kreativ-Werte stehen; Kreativ-Werte wirken nur mit getragener Kreativ-Brille |
| `video/client/VideoConfigTab`, `VideoState#goggles` | video | kein Umschalter für die Brille; Überschrift `propwash.video.config.section.worn`, ohne Brille Hinweis `propwash.video.config.no_goggles`; baut sich neu auf, wenn die Brille wechselt; `VideoState#goggles(Träger)` = Test-Empfänger oder Brille auf dem Kopf |
| `drone/DroneServiceMenu#bindAccess`, `RESET_BIND_BUTTON` (2000), `resetBind` | drone | DataSlot mit dem Zugriff des Betrachters (Server rechnet, Client liest); Knopf setzt zurück, Meldung `propwash.drone.bind.reset` |
| `drone/client/DroneServiceScreen` (`RX_X0`/`RX_Y0`/`RX_SIZE`, `armBindReset`, `confirmBindReset`, `bindResetArmed`, `receiverChipCenterX/Y`) | drone | Feld „RX“ links neben dem Inventar: Symbol des Flugcontrollers, Rahmen grün/gelb/grau, Kurzwort, Tooltip; erster Klick fragt 3 s nach (rot), zweiter setzt zurück |
| `content/PartItem`, `drone/QuadcopterItem` | content, drone | Status-Zeile der Bindung; Flugcontroller ohne Bindung mit Id wie bisher „Empfänger-Bindung gespeichert“ |
| `guide/scenes/BindScene` | guide | fünf Schritte `remote`, `wear` (Brille aufsetzen, kein Binden), `match` (passende und falsche Brille, Sichtflug ohne Brille), `keep` (Werkbank-Symbol, Hinweise `keep` und `takeover`), `start` |
| Netz | drone | Komponente `propwash:receiver_bind` (nur der Pilot); `Constants.PROTOCOL_VERSION` = 15 |

### Drohnen-Trails (drone, drone-render, server, ui; Entwurf `docs/design/drone-trails.md`, Propwash 0.5.0)
Nur Client-Optik; der Server liefert nur die Welt-Regel.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `drone/trail/TrailBuffer`, `TrailEmitter`, `TrailModel`, `TrailColor` | drone | reines Java: Ringpuffer je Drohne, Proben nach Zeit (40 Hz) und Abstand, Lebensdauer 1–2 s nach Tempo, Ausblenden, Breiten, Obergrenzen (100 Proben, 64 Trails), Farbe aus LED, Lackierung oder Palette |
| `drone/client/trail/TrailClient` | drone | `init()` aus `DroneClient`, `ruleOn()`, `shown()`, `setEnabled`, `toggle`; Taste `key.propwash.drone_trails` (`KeyDefaults.DRONE_TRAILS`, ohne Belegung) |
| `drone/client/trail/TrailConfig` | drone | `config/propwash/trails.json` (`enabled`, Standard an) |
| `drone/client/trail/TrailRenderer` | drone-render | `QuadcopterEntity.CLIENT_TICK` → Platz, Bodenprobe, Farbe; `ClientBus.SUBMIT_WORLD_GEOMETRY` → Proben und `RenderTypes.eyes`-Geometrie; nie im Schattenpass, nie für die Kamera-Drohne; Zähler für Selbsttests |
| `drone/client/render/DroneRenderConfigTab` | drone-render | Abschnitt „Drohnen-Trails“, Schalter gesperrt mit Hinweis bei Regel aus (`trailToggle()`, `trailRuleHintShown()`) |
| `server/rules/WorldRuleSpecs.DRONE_TRAILS`, `RulePreset` | server | Welt-Regel `propwash:drone_trails` (Schalter, Standard aus, Gruppe Spiel, keine Physik), Training und Arcade an |
| Netz | – | kein neues Paket, die Regel reist in `propwash:world_rules` |
| Selbsttests | drone | `drone.client.trail.TrailSelfTests` (beide Loader, Schritte `trails: …`), mit `-PpropwashScreenshots=true` `TrailShowcase` (`bildschirmfotos: Drohnen-Trails – …`) |
| `drone/PilotBodyRules`, `drone/PilotBodyAlert` | drone | reines Java: Gnadenfrist nach dem Tod (20 Ticks), Ausstieg nach drei Bildern, Welt-Regel-Schutz, Start nicht im Gleitflug; Treffer-Blitz, Hinweise HIT/AIR/CRITICAL |
| `drone/PilotProtection`, `mixin/drone/ServerPlayerMixin` | drone | `ServerPlayer#isInvulnerableTo` + Welt-Regel `propwash:invulnerable_pilots` (Standard aus) |
| `drone/client/PilotBody`, `mixin/drone/client/LocalPlayerMixin`, `ClientInputAccessor`, `LivingEntityMixin` | drone | Eingaben des steuernden Spielers leeren, Lage auch ohne Kamera senden, Körper nur bei fehlenden Chunks anhalten |
| `drone/client/PilotBodyHud` | drone | HUD-Ebene `propwash:pilot_body` (immer sichtbar): roter Rand, Treffer-Ton, Karte mit Gesundheit/Luft |
| Selbsttests | drone | `drone.client.PilotBodySelfTests` (Schritte `pilotenkörper: …`, beide Loader), Beitrittstest `PROPWASH_JOINTEST_PILOT` mit `dev.PilotJoinCheck` |

## Beitritts-Prüfung mit Add-on-Übersicht (0.5.0)

Wer einem Server mit anderen Propwash-Add-ons beitreten will, sieht eine Übersicht **je Add-on** (Name, Mod-Id,
Versionen, Aktion, Link) statt Teile-Ids oder der Register-Meldung des Loaders. Ablauf, Fälle vorher/nachher und
Grenzen: `docs/design/join-check.md`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `join/AddonInfo`, `JoinInfo`, `PartRef` | join (reines Java) | bereinigte Daten: Mod-Id und Namensräume `[a-z0-9_.-]`, Name ≤ 96, Version ≤ 64 Zeichen, Link nur http/https (`SafeText`); `JoinInfo` sortiert, ohne Doppelte, ≤ 256 Add-ons; `owner(namensraum)` erst über `namespaces`, dann Mod-Id |
| `join/JoinReport` | join (reines Java, JUnit `JoinReportTest`) | `compute(server, client, serverTeile?, clientTeile?)`: Propwash-Zeile bei anderem Protokoll (`PROTOCOL`, Teile nicht verglichen) oder anderen eingebauten Teilen (`BUILTINS`); je Add-on `INSTALL`/`UPDATE`/`SWITCH`/`REINSTALL`/`REMOVE` mit Teilezahlen; Teile ohne Add-on als `UnknownParts` je Namensraum; `relevance`: `REQUIRED` (Teile anders), `LIKELY`, `INFO` (Propwash erlaubt es) |
| `join/VersionOrder`, `SafeText` | join (reines Java, JUnit `JoinSafetyTest`) | SemVer-artiger Vergleich (Vorabversion < Release, `+build` egal, sonst leer); Text ohne Steuer-, Bidi- und `§x`-Zeichen |
| `join/LocalJoinInfo` | join | eigene Daten: Propwash-Version, `ProtocolVersionPayload.localProtocol()`, Fingerabdruck der eingebauten Teile (16 Hex), Add-ons = Mods mit Abhängigkeit auf `propwash` (`IPlatformHelper#dependentMods`) plus Besitzer von Teile-Namensräumen (Mod-Id, `loadBundled`-Mod über `noteBundled`, `declare` für eingebettete wie das Beispiel-Addon); nach dem Einfrieren zwischengespeichert |
| `join/JoinText` | join (JUnit `JoinTextTest`, `JoinLangTest`) | alle Texte mit `translatableWithFallback` (Rückfall = `en_us`): Trennmeldung `propwash.join.disconnect.title` + höchstens 8 Zeilen, `details` (Kopieren, Log), `missingPropwash`, `ours(grund)` erkennt Propwash-Meldungen |
| `join/JoinCheck` | join | Server: `onClientInfo` (anderes Protokoll → Trennung), `onClientParts` (Teile anders → Trennung; sonst Info-Log bei Unterschieden), Log „Propwash: Beitritt von … abgelehnt“ mit `details`; Client-Daten je Verbindung schwach gehalten |
| `join/JoinClientState`, `join/client/JoinCheckClient`, `JoinMismatchScreen` | join / Client | Client merkt sich Server-Daten je Konfigurations-Listener; `createDisconnectScreen` zeigt bei Unterschieden die Übersicht (Theme-Fenster, Abschnitte „Was zu tun ist“/„Außerdem anders“, Link über `ConfirmLinkScreen`, „Details kopieren“, `detailsText()`) |
| `network/JoinInfoPayload` | network (JUnit `JoinInfoPayloadTest`) | `propwash:join_info`, Konfigurationsphase beide Richtungen: VarInt Format, VarInt Länge, Inhalt (Version, Protokoll, Fingerabdruck, Add-ons mit Namensräumen); spätere Formate hängen hinten an |
| `mixin/join` | join | `ServerConfigurationPacketListenerImplAccessor` (Warteschlange für NeoForge), Client: `ClientCommonPacketListenerImplMixin#createDisconnectScreen`, `ClientConfigurationPacketListenerImplMixin` (Beginn je Verbindung); Config `propwash.join.mixins.json` |
| `fabric/PropwashFabric` | Fabric | `BEFORE_CONFIGURE` in Phase `propwash:join_check` vor `Event.DEFAULT_PHASE`: `JoinInfoTask` + Teile-Aufgabe; ältere Clients `ProtocolCheckTask` + Teile; Fabric-Client ohne Propwash → `missingPropwash`; Vanilla-Client erst in `CONFIGURE` |
| `neoforge/PropwashNeoForge#onConfigurationTasks` | NeoForge | stellt `JoinInfoTask` und Teile-Aufgabe vor `SyncRegistries` an den Anfang der Warteschlange |
| `platform/IPlatformHelper#modMeta`, `#dependentMods`, `ModMeta` | platform | Fabric: `name`, `version`, `contact.homepage`/`sources`, Abhängigkeiten mit `isPositive()`; NeoForge: `displayName`, `version`, `displayURL`/`modUrl`, `REQUIRED`/`OPTIONAL` |
| `join/ClientOnlyAddons` | join (reines Java, JUnit `ClientOnlyAddonsTest`) | `compute(server, client, serverTeile, clientTeile)`: Namensräume mit Teilen nur beim Client, die der Server nicht kennt (kein Teil, kein Add-on mit Namensraum oder Mod-Id, Besitzer-Add-on nicht auf dem Server), plus Mod-Id und weitere unbekannte Namensräume des Besitzers; nie `propwash`/`minecraft`; Server und Client nutzen dieselbe Rechnung (`docs/design/client-only-addons.md`) |
| `join/JoinReport` (inaktive Teile) | join | Teile nur beim Client in diesen Namensräumen zählen als `inactive` (Karte bzw. `UnknownParts#inactive`), nicht als `missingOnServer`; `blocking()` ignoriert sie; Client-Add-ons ohne Gegenstück sind immer `INFO` |
| `join/AddonSession`, `build/PartAvailability` | join / build | Client: `activate` nach der Antwort auf den Teile-Abgleich (`JoinClientState.onServerParts`), `clear` bei neuer Konfiguration, `onDisconnect` und `ClientBus.DISCONNECT`; `PartAvailability` filtert `PartIndex.all/byId/byKey/chemistries/chemistry`, `registered…` bleibt vollständig (Handshake, Fingerabdruck, Beitrittsdaten, Modelle, Item-Bilder) |
| `join/SessionItems`, `join/client/JoinClient`, `mixin/join/client/MultiPlayerGameModeMixin`, `CreativeModeTabsAccessor` | join / Client | inaktive Ids eines Stapels (Item-Namensraum, `PART`, `DRONE_BUILD` samt Kreativ-Basis); Kreativ-Pakete mit inaktiven Items werden nicht gesendet (Platz geleert, leer gemeldet, Toast); Hinweis-Toast nach `ClientBus.LOGIN`; Kreativ-Tabs neu aufbauen bei jeder Änderung; Filter in `ContentTab`, `ApiExtensions.registeredPresets`, `WorkbenchExtensions.all`, `ConfigTabs.create` (Id `<ns>:…`), `GuideScenes` (Sichtbarkeit, `byId`), `ClientApiImpl.send` (`UNKNOWN_PARTS` ohne Senden), Drohnen-Editor (Code, Vorlagen), Tooltips von `PartItem` und `QuadcopterItem`; API `PropwashClientApi.isAddonActive` |
| Netz | join | `propwash:join_info`; `Constants.PROTOCOL_VERSION` = 13 |

### Konventionen (docs/CONVENTIONS.md)
- **Kein Kommentar im Code** (auch keine Javadoc). Verträge stehen in diesem Dokument.
- Mixins nur unter `de.lardtom.propwash.mixin.<modul>`, Namen `<Ziel>Mixin`/`<Ziel>Accessor`.
