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
| `PropwashCommon.init()` | Fabric `fabric.PropwashFabric#onInitialize`, NeoForge `neoforge.PropwashNeoForge` (Konstruktor) | `core.CoreModule` → `parts.PartsModule` → `content.ContentModule` → `drone.DroneModule` → `race.RaceModule` → `bando.BandoModule` → `server.ServerModule` → `zone.ZoneModule` → `rescue.RescueModule` → `input.InputModule` → `video.VideoModule` → `relay.RelayModule` → `audio.AudioModule` → `ui.UiModule` → `trick.TrickModule` → `blackbox.BlackboxModule` → `interact.InteractModule` → `accessory.AccessoryModule` → `creative.CreativeModule` → `osd.OsdModule` → `guide.GuideModule` → `apiimpl.ApiModule` → (seit 0.6.0) `share.ShareModule` → `guard.GuardModule` → `webexport.WebExportModule` → `rx.RxModule` → `input.addon.AddonSwitchModule` → `drone.data.DroneDataModule` → `server.AddonWorldRules` → `accessory.dispense.DispenseModule` → `drone.operator.OperatorModule` → `barrier.BarrierModule` → `stats.StatsModule` |
| `client.PropwashClient.init()` | Fabric `fabric.client.PropwashFabricClient#onInitializeClient`, NeoForge `neoforge.client.PropwashNeoForgeClient` (`@Mod(dist = CLIENT)`) | `core.client.CoreClient` → `parts.client.PartsClient` → `content.client.ContentClient` → `drone.client.DroneClient` → `race.client.RaceClient` → `bando.client.BandoClient` → `server.client.ServerClient` → `zone.client.ZoneClient` → `rescue.client.RescueClient` → `input.client.InputClient` → `video.client.VideoClient` → `relay.client.RelayClient` → `audio.client.AudioClient` → `ui.client.UiClient` → `trick.client.TrickClient` → `blackbox.client.BlackboxClient` → `interact.client.InteractClient` → `accessory.client.AccessoryClient` → `creative.client.CreativeClient` → `osd.client.OsdClient` → `guide.client.GuideClient` → `apiimpl.client.ApiClient` → `join.client.JoinClient` → (seit 0.6.0) `guard.client.GuardClient` → `rx.client.RxClient` → `race.client.GatePlaceHint` → `input.addon.client.AddonSwitchClient` → `server.client.AddonRuleSections` → `share.client.ShareClient` → `freecam.client.FreecamClient` → `drone.operator.client.OperatorClient` → `barrier.client.BarrierClient` → `stats.client.StatsClient` → `core.DevExtensions.client()` (lädt `dev.client.DevClientBootstrap` aus dem Quell-Set `devtest`, falls vorhanden) |

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
public static final RegHolder<TicketType> DRONE_TICKET = PropwashRegistries.ticketType("drone", 40L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
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
| `drone.data.AddonData` | `propwash:addon_data` (Komponente und Entity-Data-Serializer, seit 0.6.0) | Daten der Add-ons an der Drohne, roh je Schlüssel `<ns>:<pfad>` (NBT `AddonData`); bleiben am Item und in der Welt, auch ohne das Add-on. Grenzen und Abgleich in `drone.data.DroneDataStore` (§15 „Add-on-Daten an der Drohne“) |

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
- Zeitgrenze 6 s je ausgewähltem Schritt in der Welt, mindestens 80 min (Ticks); Wächter 16 min danach (echte Zeit,
  beendet die JVM hart). Der volle Lauf hat in 0.6.0 auf Fabric mit Bildschirmfotos rund 1 800 Schritte und läuft für
  Prüfungen meist in Teilen (`docs/testing.md` §6 „Selbsttest in Teilen“).
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
- **Gruppe `drone: Landung`** braucht keine vorigen Schritte: Ohne laufende Sitzung setzt sie eine eigene Drohne und
  fliegt im Sichtflug, im vollen Lauf nutzt sie die Sitzung der Drohnen-Schritte (JUnit `LandingSelfTestSequenceTest`).
- **Tests nach dem Klon** (seit 0.6.0, `docs/test-paths.md`): Testpfade kommen aus `testutil.TestPaths`. Die Wurzel kommt
  aus `propwash.rootDir` (gesetzt vom Test-Task) oder aus der Suche nach oben bis `settings.gradle`; Ressourcen werden
  über den Klassenpfad gelesen, geschrieben wird nur mit `propwash.testResources`. Golden-Dateien haben die Zeilenform
  `<schlüssel> <wert> [<wert für x86_64>]`: Die erste Spalte gilt für aarch64, andere Architekturen prüfen nur die
  Schlüssel. `-Dpropwash.goldenUpdate=true` schreibt neu (aarch64 die erste Spalte, x86_64 unter Rosetta die zweite).
- **Probe-Mod `nsprobe`** (Namensraum-Prüfung, §15): `-PpropwashNsProbe=true` lädt den Mod aus dem Quell-Set
  `common/src/nsprobe` in die Läufe mit `devtest`; er verletzt den Namensraum `propwash` absichtlich in jeder Kategorie.
  Die Erwartung je Kategorie steht in `guard.client.GuardSelfTests.EXPECTED`. Ohne Probe muss die Gruppe `guard:` 0 Funde
  melden (harte Prüfung auf Fehlalarme).
- **Prüf-Infrastruktur 0.6.0** (Abläufe in `docs/testing.md` §7):
  - `PROPWASH_JOINTEST_MULTI` kennt `1`, `pilot`, `watcher`, `solo`, `watchsolo` und `migration`. Die Spieler heißen
    PwPilotA und PwPilotB; das Startzeichen ist ein Papier `propwash-jointest-watched` in Platz 9. Im Modus `multi` ist der
    Zuschauer Operator und räumt mit `/propwash clear` auf. `dev.MultiJoinCheck` (Modi `multi` und `watchsolo`): Sobald
    der Zuschauer nicht mehr zuschaut, startet eine Gimbal-Drohne ins autonome Halten (`ExampleFollow`), und der
    Zuschauer wird gebunden (`ExampleOperator.bind`); Ende von Flug und Bindung stehen im Log.
  - Altwelt-Umstieg: `propwash.selftest.fixtureWorld` erwartet `world.zip` und `expected.json` im Format 1 der
    Testwelten. `dev.MigrationValues` (ohne Client-Klassen) erfasst und vergleicht die Werte, liest Stapel aus der
    Erwartung und meldet am Server eine Runde durch Gates auf Beinen; genutzt von `migration:` und `dev.MigrationJoinCheck`.
    `PROPWASH_JOINTEST_MULTI=migration` mit `PROPWASH_JOINTEST_FIXTURE` wird über `ServerSmoke.init` und dann
    `MultiJoinCheck.init` angemeldet; die Ergebnisse gehen als Papier `propwash-migration …` in Platz 9 an den Piloten.
    `MigrationValues.freecamOff` prüft Standard und Wert von `allow_freecam`, `MigrationSelfTests.freecamClient` dasselbe
    beim Client.
  - Drehwelt: Die Prüfungen stehen in `dev.client.DrehweltChecks`, die Freecam auf dem Turm in `dev.client.DrehweltFreecam`.
    Sie erkennt die Features an `propwash:rx_block`, `sim.SurfaceResponse`, `interact.DroneBlockTriggers`,
    `drone.client.render.PowerLead`, `race.GateOpenings`, der Regel `allow_freecam` und dem OSD-Element
    `flight_path_marker`; wer diese Namen ändert, passt `DrehweltSelfTests` an.
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

- Einstellungen umbenennen oder verschieben: `JsonConfig.create(name, typ, standard, prüfer, migration)` ruft
  `migration` mit dem gelesenen JSON-Baum auf, bevor die Klasse daraus entsteht (`JsonConfig.read`). Dort den alten
  Schlüssel entfernen und den neuen setzen, wenn er noch fehlt – so bleibt kein Altfeld in der Klasse und kein
  `null`-Eintrag in der Datei. Beispiel: `InputConfig#migrate` (`mouse.invertY` → `invertPitch`,
  JUnit `InvertPitchTest`). Ohne `migration` liest `JsonConfig` wie bisher direkt aus dem Datenstrom.
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
  legt `propwash.<modul>.mixins.json` an und meldet den Eintrag in beiden Metadaten als Foundation-Wunsch. Ausnahme:
  Configs im Modul eines Loaders (`neoforge/src/main/resources/propwash.fabricsync.mixins.json`,
  `docs/design/cross-loader-join.md`) stehen nur in dessen Metadaten, am Ende der Liste.

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
| `video/client/ControlLinkScan` | video | drone (`PilotSession`: Abschattung der Steuerstrecke), video (`VtxRuntime`: Auto im Sichtflug) |
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
- Vanilla-Hooks: `hurtServer` → Schaden durch ein eigenes Geschoss der Drohne (`OwnShots.from`) kommt nicht an;
  Spieler zerlegen die Drohne sofort (Drop außer im Kreativmodus), anderer Schaden
  summiert sich bis zur Bruchgrenze; `isPickable` → nicht entfernt; `onRemoval` → Pilot serverseitig zurücksetzen
  (feuert `PILOT_CHANGED`).
- Vanilla-Drehung (`docs/drone-rotation.md`): `teleportSetPosition`, `forceSetRotation` und `lookAt` (nur Server)
  setzen die Lage aus Vanilla-Gier und -Nick, ohne Rollen (`drone/VanillaRotation#attitude`). Ohne Rotationsangabe
  (relative Gier und relativer Nick ohne Änderung, `#turns`) bleibt die Lage. Bei einer geflogenen Drohne: Epoche +1, und
  `DronePiloting#sendTurn` schickt `DroneResetS2C` mit den Merkern „Tempo behalten“ und „Position behalten“ (bei
  `/rotate` und bei einem Teleport ohne Ortswechsel). Das Merker-Byte hat Bit 0 = home, Bit 1 = Tempo, Bit 2 = Position;
  ein normaler Reset ist byte-gleich. `PilotSession#onReset` behält das Welttempo und dreht den Drehimpuls mit
  (`VanillaRotation#carryMotion`), `DroneClient#onReset` behält bei „Position behalten“ die Client-Position. Beim
  Server-Flug (Heimflug, Add-on-Autonomie) führt der Server-Regler die Lage weiter selbst.

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
- **Drohnen aufräumen** (seit 0.6.0, `server.DroneClear`, Unterbefehl `/propwash clear [<ziele>]` in
  `server.ServerCommands`, `docs/clear-command.md`): Aufräumen ohne Drop, Rechtestufe 2. Ohne Ziele alle
  `QuadcopterEntity` in geladenen Bereichen aller Dimensionen (`DroneClear.loaded`), mit Entity-Auswahl nur die Drohnen
  darin (`among`). Je Drohne: Heimflug-Plan löschen, F7-Zuschauer abmelden (`SpectatorLinks.unwatch`), `discard()`.
  `onRemoval` löst den Piloten, `PILOT_CHANGED` beendet Pilot-Verbindung, Sichtanker und `BY_PILOT`;
  `AutonomousReturns` startet für die entfernte Drohne keinen Heimflug, die Ansicht eines Kamera-Operators endet sofort.
  Ledger-Tickets fallen mit dem nächsten Tick. Pilot und Heimflug-Besitzer bekommen `propwash.drone.removed`, Absender
  und Operatoren `propwash.server.command.clear.done` bzw. `.done.one` mit Anzahl je Dimension (`DroneClear.Tally`),
  ohne Treffer `…clear.none` als Fehler mit Ergebnis 0; Log-Zeile mit Absender. Kein Netzpaket, keine API, Protokoll
  unverändert. Prüfungen: `server.ClearServerChecks` im Server-Rauchtest, Selbsttest `server: /propwash clear`, JUnit
  `DroneClearTest`.

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
  einmal aus und wieder an war; armed bleibt die Drohne unberührt. Tast-Funktionen (Rescue, Aufrichten, Homepoint,
  Reset), die nur durch eine gespeicherte Belegungsänderung aktiv werden, hält `TransmitterInput` aus, bis sie einmal inaktiv waren.
  Zusätzlich setzt die Taste „Suchpiepser“ (`InputKeys.BEEPER`, Standard Punkt) während einer Pilot-Sitzung `BEEPER`
  und die Taste „LEDs an/aus“ (`InputKeys.LEDS`, Standard F8) `LEDS_OFF` als Umschalter (Tastatur und Funke, per ODER zum
  Schalter der Funke, Reset bei `DISCONNECT`, Meldung „LEDs an/aus“ bzw. „LEDs bleiben aus“, wenn der
  Funkenschalter sie hält). `LEDS_OFF` fällt bei Sitzungsende zurück; der Piepser-Umschalter bleibt (ab 0.5.2), übernimmt
  zu Beginn einer Sitzung den Zustand der Drohne (piept sie für diesen Spieler, ist er an) und schaltet zu Fuß nur aus.
  Der Piepser selbst ist ein Zustand der Drohne auf dem Server (`QuadcopterEntity#beeperBy`, gespeichert als
  `LocatorBeeper`): gesetzt aus dem Flugzustand (`DroneStateC2S#beeper`), gelöscht, wenn der Pilot ihn ausschaltet (in
  der Sitzung über den Flugzustand, zu Fuß per `BeeperOffC2S`, wenn Taste oder Funkenschalter auf aus gehen), der Akku
  ausgebaut wird oder leer ist (unbeaufsichtigt zieht die Elektronik `LocatorBeeperPower`) oder die Drohne aufgehoben
  wird. `audio.client.LocatorBeeper` piept für jede entschärfte, piepende Drohne in Hörweite, nicht nur die eigene;
  ARM/Flugmodus per Taste laufen nicht über
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
  im Flugmodell. Der Tasten-Vorrang gilt nur, solange die Schalter seit dem letzten Lesen stehen (`keyOverridesSwitch`
  vergleicht mit `lastSwitch`). `preview(device, active, readable)` sagt ohne Zustandsänderung, welchen Modus `observe`
  mit diesen Schalterbits setzen würde; ebenso `Mode3dSelector#preview`.
- **Modi-Tab zeigt, was die Drohne fliegt (`input.client.screen.ModesTab`):** Bei laufender Sitzung leuchten Acro,
  Angle, Horizon und 3D-Modus nach `PilotSession#previewFlightMode`/`previewMode3d` mit den Schalterbits des Tabs (die
  gerade bearbeitete, noch nicht gespeicherte Belegung) – auch wenn der Konfigurator im Einzelspieler das Spiel pausiert
  und die Sitzung keine Schalter liest; ebenso der Hinweis auf den Tasten-Vorrang (`keyOverridesModeSwitch(guid, …)`).
  Gilt nur, wenn die Sitzung genau dieses Gerät benutzt, sonst der geflogene Modus. Die Schalterauswertung des Tabs
  übernimmt jeden Frame von `TransmitterInput#followSwitches` die Umschalter-Zustände der Funke (`SwitchEvaluator#follow`:
  frisch alle, danach nur Funktionen mit unveränderter Belegung); `ModesTab#save` gibt den Zustand per
  `TransmitterInput#adoptSwitches` zurück, wenn der Tab in den letzten 250 ms gezeichnet wurde. Ohne Speichern fliegt die
  Drohne die gespeicherte Belegung. Testfunken lesen mit `SimulatedRadio#useSavedProfile` die gespeicherte Belegung wie
  ein echtes Gerät.
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
  (`docs/design/mouse-control.md`). rescue (J Rescue, Komma Homepoint, Rücktaste Reset),
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
  `RESET` (Reset: Drohne zurück zum Homepoint, bis 0.5.2 `RACE_RESTART` „Rennen neu starten“; gespeicherte Belegungen
  `race_restart` übernimmt `DeviceProfile.RENAMED` beim Laden), `RECORD` (Action-Cam-Aufnahme an/aus, Taste `KeyDefaults.ACTION_CAM_RECORD` = F9 von video),
  `LEDS_OFF` („LEDs aus“ wie LED_LOW bei Betaflight: aktiv = LED-Streifen samt Scheinwerfer aus, nicht belegt oder unlesbar ⇒
  LEDs an; Taste `KeyDefaults.LEDS` = F8), `ALT_HOLD` (Höhe halten, ALTHOLD), `POS_HOLD` (Position halten, POSHOLD; schließt
  ALTHOLD ein), `CARGO_DROP` (Fracht abwerfen), `MODE_3D` (3D-Modus an; Taste `KeyDefaults.MODE_3D` = Ziffernblock 6, an Funke
  und Gamepad unbelegt), `COMMAND_1` … `COMMAND_8` (eigene Befehle, `command()`/`commandIndex()`, `flightFunctions()` ohne
  sie; Texte in `DeviceProfile.commands`, Tasten `KeyDefaults.COMMANDS` unbelegt; nicht im Empfänger-Assistenten;
  `docs/design/switch-commands.md`); Ordinalzahlen sind die Bits der `long`-Masken `activeSwitches()` und `readableSwitches()` (Funktionen, deren Belegung am
  aktuellen Gerät lesbar ist; Tastatur und kein Gerät ⇒ 0, `SwitchEvaluator#readable`; bis 64 eingebaute Funktionen,
  `SwitchEvaluator#bit` = `1L << ordinal`): nur hinten anhängen; Add-ons können per Mixin weitere Werte anhängen, deshalb
  braucht jede Fallunterscheidung über `SwitchFunction`, `OsdElement` und `ModesTab$Row` einen Standardfall
  (`ExtendedSwitchFunctionTest` prüft das im Bytecode), und `builtIn()` trennt die eingebauten Werte von angehängten;
  Sprachschlüssel `translationKey()` = `propwash.input.switch.<name>`;
  `supportsToggle()` (ARM, ANGLE, HORIZON, RESCUE, BEEPER, RECORD, LEDS_OFF, ALT_HOLD, POS_HOLD, MODE_3D, COMMAND_1…8; nicht bei den Tast-Funktionen
  FLIP_OVER, SET_HOME, RESET), `latchesOnGamepad()` (Gamepad-Tasten schalten standardmäßig um; nicht bei den eigenen Befehlen). Die Belegung wird im
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
| `:fabric:runServerSmoke`, `:neoforge:runServerSmoke` | Server-Rauchtest (`dev.ServerSmoke`, Property `propwash.serversmoke`): dedizierter Server ohne GUI in `runs/smoke-server`, Flachwelt, Offline-Modus, Port 25601; spawnt nach `STARTED` eine Drohne, prüft die Rechte der Welt-Regeln (`server.WorldRulesRights`: Gast ohne Recht per Tab, Voreinstellung und `/gamerule` abgelehnt, als Operator übernommen; Admin ohne Operator mit `/gamerule`-Recht aus einem simulierten Rechte-Mod – der Test ersetzt wie Vanilla Permissions die Bedingung des Befehlsknotens `gamerule` per Reflection, weil im Dev-Lauf kein Rechte-Mod installiert ist – ändert per Tab, Voreinstellung und Befehl, ohne und nach dem Entzug nur lesbar), den Blockbruch und die Drohnen-Tags (`drone.DroneTagChecks`, `docs/design/drone-tags.md`), nach 20 Ticks den Homepoint im NBT (`rescue.HomePointChecks`, `docs/design/home-point.md`), hält den Server nach 100 Ticks an und schreibt PASS/FAIL nach `propwash-selftest-result.txt`. Der Task schlägt fehl ohne PASS oder bei `/ERROR]`-, `/FATAL]`- oder Mixin-Fehlerzeilen in `logs/latest.log`. `prepareServerSmoke` legt vorher `eula.txt`, `server.properties` neu an und löscht die alte Welt, nur für die Loader, deren `runServerSmoke` im Aufruf läuft (direkt aufgerufen beide). Läuft bei jeder Integration zusammen mit Selbst- und Rauchtest. |
| `prepareRuns` | schreibt/patcht `options.txt` stumm (Master 0.001, alle anderen `soundCategory_*` 0, `pauseOnLostFocus:false`, `onboardAccessibility:false`) in den Run-Verzeichnissen der Clients im Aufruf (ohne Client-Task alle), in Selbsttest- und Rauchtest-Instanzen zusätzlich `renderDistance:6`, `simulationDistance:5`, `guiScale:0`, `graphicsPreset:"custom"` (sonst setzt die Voreinstellung „fancy“ bei jedem Start 16/12) und `lang:en_us` (deutsche Bildschirmfotos hinterlassen ihre Sprache nicht); alle `run*`-Tasks außer `runServerSmoke` hängen davon ab |
| `-PpropwashTestWindowFront=true` | Selbsttest- und Rauchtest-Clients öffnen ihr Fenster sonst im Hintergrund, ohne Fokus, Dock-Symbol und Mausgriff (`docs/testing.md`, „Testfenster ohne Mausgriff und Fokus“); mit der Property startet das Fenster wie ein normaler Client, der Zeiger des Systems bleibt trotzdem frei |
| `:common:mergeLang` | Sprachdateien zusammenführen |
| `:fabric:runWebExport` | Web-Export (seit 0.6.0, §15 „Sharecodes PW3 und Web-Export“): dedizierter Server, Befehl `/propwash webexport` über die Konsole, Auswahl mit `-PpropwashWebExportMods`, Kopie nach `build/web-export/` |
| `-PpropwashNsProbe=true` | lädt den Probe-Mod `nsprobe` in Selbsttest, Rauch- und Beitrittstest (§9, Namensraum-Prüfung); nie im Release-Jar |
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
| `ui/client/config/ConfigTab`, `ConfigTabHost`, `ConfigTabs` | ui (Rahmen, Screen) | Tabs des Konfigurators. Jedes Modul, das Einstellungen hat, registriert in seiner Client-`init()` `ConfigTabs.register(MeinTab::new)`; ui baut den Screen (`ConfigTabs.setOpener`), fragt `ConfigTabs.create()` beim Öffnen ab und ruft `attach(host, x, y, w, h)` → `build()`. Widgets über `host.addWidget(...)`, Neuaufbau `host.rebuild()`, Wertänderung `host.changed()`. ui darf die Klassen erweitern, aber keine Signatur brechen. Seit 0.6.0: `ConfigTabs.registerFreecamOnly(…)` meldet Tabs an, die es nur im K-Menü der Freecam gibt; `create()` und `registeredCount()` zählen sie nicht, `create(true)` liefert sie zusammen mit den Tabs, deren `freecam()` wahr ist, und `SettingsIndex` durchsucht `create(screen.freecam())`. |
| `drone/client/PilotSession#sim()` | drone | laufendes Flugmodell (`sim.DroneSim`, null wenn inaktiv); Telemetrie über `sim().telemetry()`. `physicsFrameNanos()` = Zeitstempel des letzten Physik-Frames. |
| `drone/QuadcopterEntity#renderAttitude(partialTick, dest)`, `rotorAngle(motor, partialTick)`, `motorOutput(motor)` | drone (implementiert), drone-render (nutzt) | interpolierte Lage fürs Rendern, Prop-Winkel je Motor (Radiant), Motorleistung je Motor 0..1 |
| `drone/RemoteMotion`, `drone/RemoteInterpolation` (`QuadcopterEntity#createInterpolationHandler` auf dem Client, `lerpMotion`) | drone (JUnit `drone.RemoteMotionTest`, Selbsttest „drone: Fremde Drohne bei schlechter Verbindung …“ in `drone.client.RemoteMotionSelfTests`) | Fremde Drohnen (ohne lokalen Zustand) laufen durch einen Jitter-Puffer statt der Interpolation über zwei Ticks (Befund OT4-3, `docs/remote-motion.md`): Positionen aus `interpolateTo`, Tempo aus `lerpMotion` (Server-Tempo, `trackDeltas`), Lage aus `DATA_ORIENTATION`; Zeitstempel = vorheriger + Weg ÷ Tempo (ohne Tempo: Ankunftsabstand bzw. geschätztes Tempo), ein Achtel der Abweichung zur Ankunft wird nachgeführt, über 400 ms neu gesetzt; Verzögerung 75 ms + 2,5 × Jitter in 100–200 ms, höchstens 5 % je Zeit nachgeführt; Position linear, Lage per Slerp, ab dem letzten Stempel bis 250 ms extrapoliert (Server-Tempo, sonst Weg über ≥ 120 ms aus fortlaufenden Paketen, Lücke höchstens 500 ms; Drehrate ebenso, nur bei fliegender Drohne ab 1,5 Blöcken/s), neue Daten setzen den Fehler zum Gezeigten, der in 120 ms abklingt. Steht die Drohne (Server-Tempo unter 1,5 Blöcken/s) und kommt nach über 250 ms eine neue Pose (`/tp`, `/rotate`), hält eine Halteprobe die alte Pose bis 50 ms davor – die Drohne dreht und versetzt sich dann in zwei Ticks wie bisher; Halteproben geben kein Tempo. Je Tick (Tick-Uhr 50 ms, ein Achtel nachgeführt, über 250 ms neu) setzt `RemoteInterpolation#interpolate` die Position, `tickClientVisuals` übernimmt die Lage; ein Sprung über `snapDistance` (größtes aus Server-, vorigem und geschätztem Tempo × (Zeit seit der letzten Position + Verspätung der letzten Position, höchstens 1 s, + 250 ms) × 1,5, mindestens 16 Blöcke; Befund AUTO68-1; meldete der Server vor der neuen Position Tempo unter 1,5 Blöcken/s und seitdem keins, steht die Drohne und es gelten 16 Blöcke, auch nach den Positionsmeldungen ohne Bewegung alle 60 Ticks; Tempo höchstens 10 ms vor der Position zählt als Start; Befunde G-OT4-2-1, AUTO70-1), `cancel` (z. B. `snapClientPose`) oder eine von außen gesetzte Position leeren den Puffer; lokaler Zustand (eigene Drohne) bleibt unberührt. Befund OT8-1: Die Startposition des Clients (`seed`) ist eine Halteprobe und bringt das Tempo der Drohne aus dem Spawn-Paket mit (Vanilla setzt es ohne `lerpMotion`; nach einem Teleport-Schnitt ebenso das zuletzt gemeldete), aus Positionen geschätzt wird nur bei fliegender Drohne (ab 1,5 Blöcken/s); sinkt das Bild unter die letzte Position des Servers (beim Weiterrechnen) bzw. unter die Spur (beim Zurückführen), kappt `RemoteMotion` am Boden, den `RemoteInterpolation` senkrecht mit der Hitbox samt Zubehör-Tiefe (`AirframeSpec#accessoryDepth`) misst, nicht wenn die Hitbox dort schon im Block steckt; `QuadcopterItem` setzt eine Drohne mit Zubehör unter dem Rahmen gleich darauf (`QuadcopterEntity#accessoryLift`, dieselbe Messung `accessoryRoom` wie `standOnAccessories`); Selbsttest „drone: Gesetzte Drohne …“ in `drone.client.PlacedDroneSelfTests` |

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
| `tools/data/survival.py` | content | **einzige Quelle** für alle Rezepte (`data/propwash/recipe`), deren Freischalt-Fortschritte (`advancement/recipes/<ordner>`), die Beutetabellen `data/propwash/loot_table/inject/*` und die NeoForge-Loot-Modifier (`neoforge/src/main/resources/data/propwash/loot_modifiers/chests/...`). Nie von Hand ändern: Skript anpassen, `python3 tools/data/survival.py` ausführen; `--check` prüft Stand, Vanilla-Ids/Tags und Rezeptkonflikte gegen `.ref/vanilla`. Race behält seine Gate-/Kurs-Werkzeug-Rezepte (reine Vanilla-Zutaten, Reiter `building`/`equipment`), sie stehen nur mit im Skript. Freischaltung: jede Zutat **oder** jedes Propwash-Item (`has_propwash_item`) schaltet ein Rezept frei; Reiter: Zwischenteile, Werkbank, Ladegerät `misc`, Gates `building`, alles andere `equipment`; der Fortschritts-Ordner folgt dem Reiter (`misc`, `tools`, `building_blocks`). `--check` vergleicht die Item-Liste mit `assets/propwash/items`, neue Items also dort und im Skript eintragen (JUnit `SurvivalRecipeBookTest`, Begründung in `docs/design/survival.md` „Rezeptbuch“). Neue Rezepte zusätzlich in `SurvivalSelfTests.OTHER_CRAFTABLE` eintragen, als Kennung (`ContentIds`, `Constants.id(…)`), nicht über Klassen, die beim Laden registrieren; der JUnit-Test `content.client.SurvivalSelfTestsRecipeListTest` gleicht die Rezeptdateien mit `craftableIds()` ab |
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
- **Gelände-Gedächtnis** (seit 0.6.0; `video.client.TerrainMemory`, `video.link.TerrainColumns`/`TerrainCache`, Mixin
  `mixin.video.client.ClientChunkCacheMixin`, `docs/signal-memory.md`): Ohne geladenen Chunk liest `ClientBlockLoss` die
  gemerkte Säule (Oberkante, Laubdach, Material; ab MOTION_BLOCKING höchstens 32 Blöcke abwärts über Luft, Laub, Pflanzen
  und dünne Blöcke unter einem halben Block; Schnee/Eis oben → Material darunter; hohes Laub → weiter ab
  MOTION_BLOCKING_NO_LEAVES). Gemerkt wird bei `ClientChunkCache#drop` und für alle Chunks, die beim Verschieben oder
  Verkleinern des Ausschnitts herausfallen (in 26.3 kommt der neue Mittelpunkt vor den Forget-Paketen). Höchstens 4 096
  Chunks LRU, an die Welt gebunden, nichts auf der Platte, geladene Chunks haben Vorrang, nie Gesehenes = Luft.
  Selbsttests „signal: …“ (`video.client.SignalMemorySelfTests`), Beitrittstest `signal` (`dev.SignalJoinCheck`).

  | Klasse | Modul | Zweck |
  |---|---|---|
  | `video/client/TerrainMemory` | video | Gelände-Gedächtnis je Welt; `remember`, `columns` |
  | `video/link/TerrainColumns` | video | Säulen eines Chunks und Suchregel |
  | `video/link/TerrainCache` | video | LRU, 4 096 Chunks |
  | `mixin/video/client/ClientChunkCacheMixin` | video | Merken bei `drop`, `updateViewCenter`, `updateViewRadius` |
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
- **Toasts** (`ui.client.theme.ThemedToast`, seit 0.6.0): Titel und Text werden nicht im Konstruktor zerlegt, sondern über
  `ToastText` beim Messen und Zeichnen; gespeichert wird je `Language`-Instanz und Breite, während des Ladens nichts.
  Toasts dürfen also vor dem Laden der Sprache entstehen.

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
- **Richtungen:** Drohnen-`yaw` ist der Minecraft-Yaw der Nase (Kamera vorn; 0 = Süden/+Z, 90 = Westen/−X, wie
  `SceneKit.pilotYaw`), `pitch` positiv = Nase runter (Vorneigung im Vorwärtsflug), `roll` positiv = rechte Seite hoch
  (Rechtskurve = negativer Roll); so zeichnet `SceneDroneVisual` das Modell mit der Kamera nach −Z, geprüft von
  `FlightSchoolDirectionTest`. Pilot und Gate nutzen denselben Minecraft-Yaw (0 = Blick bzw. Durchflug nach Süden/+Z,
  90 = Westen). `Item`- und `Block`-Akteure kippen um `pitch` (positiv = Oberseite nach vorn/−Z bei Yaw 0) und
  `roll` (positiv = Oberseite nach +X), Drehpunkt ist der Fußpunkt (so werden `minecraft:end_rod`-Knüppel zu Gimbals).
  Kamera-`yaw` ist der Umlaufwinkel (0 = Kamera auf +Z, Blick nach Norden, +X rechts).
- **Weitere Cues:** `turn(…, ease)` und `look(…, ease)` mit eigener Kurve (`Ease.LINEAR` für abgetastete Bahnen),
  `detach(drone, DronePart, nach, dauer)` baut ein Teil ab (fliegt zum Versatz, danach unsichtbar; `attach` setzt es wieder an).
  Eine `turn`-Dauer 0 setzt den Winkel sofort (z. B. Roll 360 → 0 nach einem Flip).
- **Szenen-Baukasten** `guide.scenes.SceneKit`: Item-/Tasten-/Block-Konstanten, `ground(…)` (Plattform mit Welle),
  `pilotYaw`/`headPitch`/`heading` (Blickrichtungen), `follow(…)` (Pilot blickt einer Flugbahn nach, Sichtflug) und
  `fly(…)` (Bahn samt Kurs, Vorneigung und Schräglage in Kurven, abgetastet mit `SceneEvaluator.path`). `heading(a, b)`
  ist der Drohnen-Yaw von a nach b (`atan2(−dx, dz)`, gleich `pilotYaw`); eine Drohne vor `fly` startet mit diesem
  Kurs, sonst dreht sie sich beim Abflug auf der Stelle um.
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
  wanderndes Grün), `FlightSchoolDirectionTest` (in jedem geneigten Vorwärtsflug, Nick ab 6°, aller Szenen samt
  `gate_menu` und `bandos` zeigt die Kamera in Flugrichtung, die Drohne neigt sich in Flugrichtung und legt sich in die
  Kurve; Aufprall mit der Nase, Flip und Power Loop wie im Text, Knüppel im Konfigurator), `FlightSchoolHeadingTest`
  (jeden Tick auch ohne Neigung, beim Steigen und Sinken bis 75°: Kamera höchstens 105° neben der Flugrichtung, ab drei
  Ticks am Stück; begründete Ausnahmen mit Zeitfenster, siehe `docs/flight-school-heading.md`); Selbsttest „guide: alle Szenen Schritt für Schritt abspielen“ (je Schritt drei Stellen: gezeichnet,
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
  Die Knüppel stellt der Cue `sticks(actor, linksX, linksY, rechtsX, rechtsY, dauer)` (−1…1, immer in Mode-2-Bedeutung:
  links Gas/Gier, rechts Nick/Roll, Y positiv = nach vorn/oben; Standard links unten). Gezeichnet wird im Knüppel-Mode des
  Spielers (`RemoteGimbalRenderer.Pose.ofMode2` mit `StickMode.fromMode2`); Hinweis-Anker auf einem Knüppel entstehen mit
  `Anchor.onStick(akteur, kanal, mode2Spitze)` und wandern samt Seite (`Cue.Side`) auf die andere Funkenseite, wenn der Kanal
  dort liegt. Texte mit Knüppelseite haben Varianten `<schlüssel>.mode1|3|4` (`StickModes.variant`, fehlt eine, gilt der
  Mode-2-Text). Die Stellung erreicht den Special-Renderer über
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
| `input/client/screen/ModesTab` | input | Zeilen = Arm, **Acro**, Angle, Horizon, Höhe halten, Position halten, Rescue, Aufrichten, Homepoint, Suchpiepser, Reset, Aufnahme, LEDs aus. Ist der Tab zu niedrig (z. B. GUI-Skala 4 bei 1080p), entfallen Hinweis und Legende unter der Liste und die Zeilen schrumpfen auf 11 px, damit der Detailbereich mit Quelle, Bereich und Tasten sichtbar bleibt. Acro ist keine eigene Schalterfunktion (an, solange Angle und Horizon aus sind), hat aber die Taste `drone_mode_acro`. Während einer Pilot-Sitzung leuchten die Lampen von Acro, Angle und Horizon nach dem geflogenen Modus (`PilotSession#flightMode`, wie das OSD; `ModesTab#modeLit`), außerhalb nach den Schaltern; hat eine Taste gegen den Modusschalter gewählt, ersetzt `propwash.input.modes.key_override` die Hinweiszeile. Ohne Funke zeigt die Spalte „Wirkung“ je Zeile eine Kurzbeschreibung (`propwash.input.modes.effect.<zeile>`) und der Bereich unten die ausgewählte Zeile mit Taste und Beschreibung (`…desc.<zeile>`); mit Funke bleibt dort die Schalterbelegung. Mit Funke hat jede belegte Zeile rechts in der Spalte „Inv.“ einen Theme-Schalter „Invertiert“ (`InputGui.invertToggle`, Tooltip je Art über `InputGui.invertTooltip`, bei Arm mit Sicherheitshinweis); Lampe, Bereichsbalken und Min/Max zeigen sofort den wirksamen Zustand bzw. Bereich. Der Empfänger-Assistent hat denselben Schalter (mit „Inv.“ daneben) in jeder belegten Zeile der Seite „Schalter“ |
| `drone/client/RemoteFirstPerson#HOLD_SCALE` | drone | skaliert Funke und beide Hände gemeinsam um die Griffe (0,5, vorher 0,6), damit die beidhändige Funke im Sichtflug unter der Drohne bleibt (etwa 16 % der Bildbreite bei 16:9) |
| `drone/client/RemoteHoldPose`, `drone/client/RemoteHoldAccess`, `mixin/drone/client/HumanoidModelMixin`, `ItemInHandLayerMixin`, `AvatarRenderStateMixin` | drone | Funke in der dritten Person: `holdingArm(state)` = Arm mit der Funke, wenn ein Spieler (`AvatarRenderState`, nicht Zuschauer, aufrecht, kein Item in Benutzung) sie in einer Hand hält und die andere leer ist, sonst `null` (Rüstungsständer, Mobs, belegte zweite Hand → Vanilla mit Display `thirdperson_*`). Dann beide Arme nach vorn (−40°, ∓23° nach innen, Hände vor unterer Brust/Bauch) und die Funke im Raum zwischen beiden Armenden, im Kontext `NONE` mittig zwischen beiden Händen statt an einer Hand (`transform(model, poseStack)` im Modellraum nach `root`, Werte in `docs/ASSETS.md`). Der Render-State der Funke hängt als `RemoteHoldAccess#propwash$remoteHold()` an jedem `AvatarRenderState` (gefüllt in `AvatarRenderer#extractRenderState` TAIL); eigene States ohne Extract (Flugschule) löst `submit` aus der Stack-Kopie auf. `frames()` zählt gezeichnete Funken (Selbsttest) |
| `ui/client/tab/PresetPanel#row` | ui (Fix F-COMBO5-1; JUnit `ui.client.tab.PidPresetRowTest`, ui-scale-Fall „Konfigurator mit langem Add-on-Tab“) | Aufteilung der Preset-Zeile in PID-Tuning und Rates als reine Rechnung (`Row`: Beschriftung, Breite „Übernehmen“, Knopf darunter, Breite der Auswahl, Platz für den Namen); „Preset“ steht in der Spalte der Regler (`UiTab.labelColumn`, `PidTab.columnWidth`) und rückt auf seine eigene Breite (Text + 8, höchstens halbe Breite), wenn der längste Preset-Name dort nicht ungestaucht passt |
| `osd/client/OsdConfigTab#listWidth`, `#nameRoom`, `#moreRoom` | osd, addon-osd (Fix F-cov-combo-3; JUnit `osd.client.OsdListWidthTest`, Selbsttest „addon-api-17: A2 – OSD-Editor in der schmalsten Tab-Breite bei GUI 320×240 …“) | Breite der Elementliste im OSD-Editor: 32 % des Tabs (112–180 px), wächst bis 45 % für ungekürzte Namen und für gestauchte Namen, solange die Vorschau 80 px behält; eingebaute Namen und Scroll-Hinweis immer mindestens gestaucht, dafür notfalls bis 60 px Vorschau (Befund cov-combo-3) |
| `osd/client/OsdHud#extractInSight`, `osd/client/OsdRenderer#renderAddons`, `#hasAddonElements`, `osd/addon/client/AddonOsdLayer#collectElements`, `#context` | osd, addon-osd (Fix F-MDA-2; JUnit `osd.AddonOsdTest#lineOfSightCollectsOnlyElementsWithTheContextOfTheFlight`, Selbsttest „addon-api-17: A2 – im Sichtflug ohne Brille …“) | Add-on-OSD-Elemente im Sichtflug ohne Brille: Aufruf am Ende von `RadioHud#extract`; nur die Add-on-Elemente im Raster `OsdGrid.screen` über dem ganzen Bild, ohne Bildrauschen und ohne Warnungen; Kontext `fpv=false`, `lineOfSight=true`; `AddonOsdLayer#context` ist der letzte Kontext außerhalb des Editors (Befund MDA-2) |
| `guide/SimulatedHold`, `guide/client/HoldToOpen#simulate`, `#stopSimulation`, `api/client/PropwashClientApi#simulateFlightSchoolHold(int)`, `#stopSimulatedFlightSchoolHold()` | guide, api (Fix F-MDA-10; JUnit `guide.SimulatedHoldTest`, Selbsttest „guide: Taste halten über einem Slot nur über die API (Testweg für Add-ons)“) | Testweg der API für das Halten der Flugschul-Taste über einem Slot: Slot-Index in `getMenu().slots` des offenen Container-Screens statt `hoveredSlot`, die Taste gilt als gehalten, bis `stopSimulatedFlightSchoolHold()`; Item am Zeiger und Textfeld-Fokus verhindern das Öffnen wie bei der echten Taste; jeder Aufruf setzt den `HoldTracker` zurück (neues Halten); Hinweis als Pille unter dem Slot, als Tooltip-Zeile nur, wenn die Maus darüber steht; nur bei `isDevelopmentEnvironment()`, sonst `false`; ohne Simulation unverändert (Befund MDA-10) |
| `race/client/RaceStandardSection#buttons`, `#labelFits` | race-rules (Fix F-GCC4-1; JUnit `race.client.RaceStandardButtonsTest`, ui-scale-Fall „Konfigurator mit langem Add-on-Tab“, Seite `world_rules race_standard`) | Aufteilung der Knöpfe „Aktuelle Regeln übernehmen“ und „Propwash-Standard“ als reine Rechnung (`Buttons`: Breiten, Lage des zweiten Knopfs, Höhe). Nebeneinander in halber Breite, untereinander in voller Breite, sobald ein Text trotz Stauchung bis 0,7 gekürzt würde. |

### Neue Funke: einmal erkennen, einmal einrichten (input)
| Datei | Besitzer | Vertrag |
|---|---|---|
| `input/DeviceProfile#setupDone`, `input/SetupStatus`, `input/InputConfig#setupStatus/markSetupDone/neverAskFor/usageRank`, `InputConfig.neverAsk` | input | Ein Profil gilt als eingerichtet, sobald der Empfänger-Assistent mit „Fertig“ endet (`markSetupDone`: `setupDone`, GUID aus `neverAsk`, Profil ans Ende = zuletzt benutzt). `setupStatus(guid)`: `COMPLETED` (eingerichtet) vor `NEVER` („Nie für dieses Gerät“, GUID in `neverAsk`, höchstens 64, nie `keyboard`) vor `INCOMPLETE` (Profil ohne Assistent) bzw. `NEW` (kein Profil). `setupDone` gehört weder zur Empfänger- noch zur Schalter-Portion (`copyReceiverFrom`/`mergeReceiverFrom`/„Standardwerte“ lassen es stehen), `copy()` und JSON behalten es. `PROFILE_VERSION` 2: Profile älterer Configs mit Kalibrierung (`DeviceProfile#calibrated`) gelten einmalig als eingerichtet |
| `input/DeviceChooser#pick(…, usage)` | input | „Automatisch“: eingerichtete Geräte zuerst, davon das zuletzt benutzte (`usageRank` = Position in `profiles`, `TransmitterInput` rückt das geöffnete Profil ans Ende); danach wie bisher Funke vor Gamepad vor Joystick. Feste Auswahl und die Sitzungssperre im Flug gehen weiter vor |
| `input/SetupPrompts` (reines Java, JUnit `SetupPromptsTest`) | input | Warteschlange je GUID für diese Spielsitzung: `update(geräte, status, now)` nimmt verbundene, berechtigte Geräte mit `NEW`/`INCOMPLETE` auf (fällig 1 s nach dem Anschließen); `poll(now, inWelt, sicher)` liefert `PROMPT`, sobald es 1,5 s am Stück sicher ist, sonst in der Welt einmal je Fälligkeit `NOTIFY` (Toast). Immer nur eine offene Frage bzw. Einrichtung, Reihenfolge = Anschlussreihenfolge. `answer`: `SET_UP` (Assistent läuft), `LATER` (höchstens eine Erinnerung, fällig nach 10 min, danach Ruhe bis zum nächsten Spielstart), `NEVER`. `setupClosed(guid, fertig)`: Abbruch zählt wie „Später“. Gerät abgesteckt → wartet auf das nächste Anschließen; Frage von einem anderen Screen verdrängt (`withdraw`) → zurück in die Schlange, ohne die Erinnerung zu verbrauchen; anderswo eingerichtet → erledigt |
| `input/client/DeviceOnboarding` | input | Verbindet `SetupPrompts` mit dem Spiel: bekommt die Geräteliste aus `TransmitterInput.scan` (`onScan`), läuft in `InputClient` auf `ClientBus.TICK_END` (`tick`). Berechtigt sind Funken, Gamepads und das gerade geöffnete Gerät. Sicher = Welt geladen, kein Screen, kein Overlay, keine Pilot-Sitzung (`safeMoment`). Öffnet `input/client/screen/DeviceSetupScreen` (Theme-Fenster „Neue Funke|Neues Gamepad|Neuer Controller erkannt“, Name + Art, Vorbelegung, Knöpfe Einrichten/Später/Nie für dieses Gerät, ESC = Später, schließt sich beim Abstecken). „Einrichten“ öffnet `ReceiverWizardScreen.forDevice(parent, guid, fertig, abbruch)`: zeigt genau dieses Gerät (`previewGuid`), lässt „Automatisch“ stehen und setzt sonst die Auswahl auf das Gerät; `removed()` ohne „Fertig“ = Abbruch. Die Taste „Empfänger-Assistent“ (ohne Standardtaste) öffnet zuerst die wartende Einrichtung. `simulate(liste)` ersetzt die echten Geräte (`null` = echte); der Selbsttest setzt beim Anmelden `List.of()`, damit ein am Entwicklerrechner steckendes Gerät nie fragt |
| `input/ProfileDefaults#radio` | input | Vorbelegung für Funken (auch EdgeTX/OpenTX-USB-Joystick): AETR auf Achse 0–3, Scharf Kanal 5 (1700–2100 µs), Flugmodus Kanal 6 (1300–1675 µs Horizon, 1700–2100 µs Angle, darunter Acro), Suchpiepser Kanal 7, Rescue Kanal 8, jeweils nur, wenn die Achse existiert. Gamepads im Knüppel-Mode des Spielers (Standard Mode 2, siehe „Knüppel-Mode“ unten). Der Assistent zeigt auf der Geräteseite, ob das Gerät eingerichtet oder nur vorbelegt ist, auf der Kanalseite, dass „Zur Kalibrierung“ die Vorbelegung übernimmt |

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
| `input/client/SimulatedRadio#raw()`, `TransmitterInput#raw()` | input | Mit simulierter Funke (`simulate`) liefert `raw()` deren Rohwerte, damit Selbsttests den Assistenten bedienen können; nach `SimulatedRadio#asGamepad()` meldet das Gerät `DeviceInfo.gamepad` und liefert Gamepad-Achsen und -Knöpfe mit denselben Nummern wie die Joystick-Werte (mindestens 6 und 15), sodass `RawInput#usesGamepadLayout` mit einem Gamepad-Profil gilt; ohne `asGamepad()` ohne Gamepad-Belegung; ohne Simulation unverändert |

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
| `drone/client/render/RemoteGimbalModel`, `RemoteGimbalRenderer.Pose/Controls` | drone-render | Das bewegte Modell zeichnet Knüppel, die vier Schalter (40° je Seite) und die drei Tasten unter dem Display (0,22 px Hub); `Pose(…, Controls)`, `Controls.NEUTRAL` = Schalter senkrecht wie im Item-Modell, Tasten oben. `remote_body.json` enthält die Schalter nicht mehr (`tools/assets/world/remote.py`), `remote.json` (GUI, Boden, Rahmen) unverändert. Tasten bei x 6.5, 8 und 9.5 (`BUTTON_X`, seit 0.6.0 mittig mit 0,25 px Abstand zu den Tellern; JUnit `RemoteFacesTest` prüft auf deckungsgleiche Flächen gleicher Richtung) |
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
Zeilen in „Technische Daten“ statt in einem eigenen Abschnitt „Passt zu“, damit der Tooltip bei GUI-Skala 3 und 1080p ganz ins Bild passt. Zerlegen legt `part_condition` auf jedes Teil, Zusammenbauen baut daraus `part_wear`. Werkbank: Verschleißzeilen je Kategorie (gelb < 0,9, rot < 0,4) samt Slot-Markierung, Vorschau mit beschädigten Props. Das Zerlegen-Feld nimmt nur Drohnen; Teile recycelt der Tab „Recyceln“ der Werkbank (ab 0.6.0, Befund RECYCLE-1, nimmt die Regel aus Befund SALVAGE-1 zurück; Abschnitt „Recyceln in der Werkbank“ am Ende). `content/client/SalvageHint.lines(carried, partsEmpty)` liefert den Hinweis am Feld (Drohne zerlegen, Teile im Tab Recyceln; ein Teil an der Maus: nur Drohnen); Selbsttest `zerlegen:`. Das Ladegerät nimmt kaputte Akkus nicht an |
| `build/PropRepair`, `content/PropRepairs`, `content/PropRepairRecipe` (`propwash:prop_repair`, Rezept `propwash:special/prop_repair`), `mixin/content/GrindstoneMenuMixin` | content | Props reparieren (ab 0.5.0): zwei beschädigte Props derselben Sorte (gleiches Item und `propwash:part`, beide < 1, auch 0) ergeben einen mit `a + b + 0,05`, höchstens 1, in 1/1000-Schritten (`PropRepair.combine`, reines Java). Crafting als Sonderrezept (genau zwei belegte Felder, 2×2 oder 3×3, Stapel erlaubt, nicht im Rezeptbuch), Schleifstein per Mixin (Eingabeplätze nehmen beschädigte Props einzeln, `computeResult` entscheidet bei Props allein, keine Erfahrung, keine Reparaturkosten). Ergebnis = Kopie des ersten Props mit allen Komponenten und neuem `part_condition` (ab 1 entfernt). Gilt für Addon-Props ohne eigene API. Hinweise: Statuszeile bei kaputten Props, Shift-Zeilen `propwash.help.category.prop.use.4/5`, Tab „Recyceln“ der Werkbank, Flugschul-Schritt `repair/combine`; Doku `docs/design/survival.md` „Props reparieren“ |
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
| `build/AccessoryType` (implementiert `PartInfo`, Item-Id = `key()`) | `LED_STRIP` (`led_strip`, unten, 6 g, 32 × 3 × 8 mm, 8 LEDs, 1,5 W am Flugakku), `ACTION_CAM_LIGHT` (`action_cam_light`, oben, 42 g, 46 × 30 × 26 mm, 4 W am Flugakku, kein eigener Akku), `ACTION_CAM_HEAVY` (`action_cam_heavy`, oben, 150 g, 60 × 45 × 35 mm, Stirnfläche 0,0027 m², eigener Akku, 0 W am Flugakku); seit 0.5.0 dazu `ACTION_CAM_FLASHBACK` (Abschnitt „Action-Cam (Flashback)“ unten). Felder `mount`, `widthMm` (X), `heightMm` (Y), `depthMm` (Z), `flightBatteryWatts`, `frontalAreaM2`, `ledCount`, `lensLengthMm`; `camera()` → `Optional<ActionCamSpec>`, `isCamera()`, `isLight()`, `fits(mount)`. Lokale Geometrie relativ zum Anbaupunkt (vor der Neigung): `boxMinMm()/boxMaxMm()/centerMm()` (oben: Körper ab `HINGE_CLEARANCE_MM` = 5 mm über dem Gelenk, mittig über dem Gelenk; unten: Leiste hängt unter dem Anbaupunkt), `lensMm()` (Linsenmitte vorn, −Z), `ledMm(i, streifenlänge)` (LED-Mitten gleichmäßig entlang X, Unterseite; seit 0.6.0 mit LED-Pfad nach Länge entlang `ledPath()`, `hasLedPath()`, `docs/design/led-strip.md` „LED-Pfad“) |
| `build/ActionCamSpec` | aus echten Kameras abgeleitet. `LIGHT` (nackte FPV-Action-Cam): 1080p60, keine Stabilisierung, Sensor 1/2,8″, Sichtfeld 150°, Bildqualität 0,80, Rauschen bei wenig Licht 0,55, 4 W vom Flugakku. `HEAVY` (große Action-Cam mit Gehäuse): bis 1440p60 (mehr schafft der Java-Encoder nicht in Echtzeit), elektronische Stabilisierung + Horizont halten, Sensor 1/1,9″, 155°, 0,95, 0,30, eigener Akku 6,6 Wh bei 4,4 W (1080p) bis 6,6 W (1440p) → ca. 90 bzw. 60 min. `supportedHeights()` (720/1080/1440/2160 bis `maxHeight`), `recordWatts(height)`, `recordMinutes(height)` (∞ ohne eigenen Akku), `ownBattery()` |
| `build/Accessories` (Record über `Map<AccessoryMount, AccessoryType>`, unveränderlich, `EnumMap`-Reihenfolge) | `NONE`, `of(…)`, `with(type)` (ersetzt dieselbe Halterung), `without(mount)`, `at(mount)`, `has`, `list()`, `mounts()`, `camera()` (erstes Kamera-Zubehör), `massGrams()`, `flightBatteryWatts()`, `frontalAreaM2()`, `key()` (`top=action_cam_heavy+bottom=led_strip`). Der Konstruktor wirft bei Typ an falscher Halterung |
| `build/AccessoryAnchor` (Record `x, y, z, tiltDegrees, lengthMm`, mm im Rahmensystem wie `drone_layout.json`: X rechts, Y oben, Z hinten) | Anbaupunkt je Rahmen und Halterung. `of(frame, mount)` (Tabelle, sonst Rückfall aus dem Radstand), `table(frame, mount)`, `rotate`/`toFrame(local)` (Drehung um X um `tiltDegrees`, positiv = Vorderkante hoch), `centerOf(type)`, `corners(type)`, `stretch(type)` (unten: `lengthMm / widthMm`, das Modell wird entlang X auf die Streifenlänge gestreckt; Zubehör mit LED-Pfad nie), `ledMm(type, i)`, `ledDirection()` (lokal −Y gedreht), `lensMm(type)`, `viewDirection()` (lokal −Z gedreht). Oben = **Drehachse des Kameragelenks** (9–15 mm über dem Kamerakäfig vor dem Akku), unten = Mitte der Oberseite des Streifens. Die Tabelle ist die Quelle der Physik; `drone_layout.json` muss dieselben Werte haben (JUnit `DroneLayoutParserTest#shippedLayoutMatchesTheAccessoryTableOfTheFlightModel`) |
| `build/FrameType#accessoryMounts` | Whoop 65: nur `BOTTOM` (keine Action-Cam), alle anderen `TOP` + `BOTTOM`, seit 0.5.2 zusätzlich `FRONT` (Abschnitt „Add-on-API 1.6“ unten) |
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
| `video/record/path/*` | video (reines Java) | `CameraPathBuffer#add(PathSample)` (Zeit in Flashback-Ticks, nur steigend und endlich, Quaternionen auf eine Halbkugel, höchstens 2²⁰ Proben), `PathResampler.perTick/at`, `CameraAngles.of(q)` = Gier/Neigung/Roll wie `Camera#setRotation(yaw, pitch)` mit `rotateZ(roll)`, `rotation()`, `EulerPath.continuous` (Keyframe-Fehler ≤ `KEYFRAME_LIMIT_DEGREES` 2°, gegen Verdrehen bei Flips), `CameraTrack.of(buffer, KeyframeDensity)` → Kamera-Keyframes (Augenposition, Winkel) und Sichtfeld-Keyframes (Anfang, Änderungen > 0,05°, Ende); seit 0.5.2 `CameraTrack.build(frames, Simplification, maxKeyframes)` → `Built` (Spur, gewünschte und erreichte Toleranz, Keyframes ohne Grenze, `capped()`), `smallestFittingSlider`; `KeyframeDensity` nur noch zum Übernehmen alter Einstellungen |
| `video/record/path/FlashbackBudget`, `Simplification`, `FlashbackSpline`, `TrackThinner`, `PathFrames`, `LiveTrackCounter`, `PathPreview`, `SamplePath` | video (reines Java) | Budget von Flashbacks Zeitleiste: `circleVertices(r)` wie ImGui 1.90 (32/48/96), `keyframeVertices(uiScale)`, `maxCameraKeyframes(scale, holds)` mit `USABLE = 65.535 − RESERVE` (6.000), `assumedScale` ≥ 2; Regler `Simplification.ofSlider(0…1)` (0 = jeder Tick, sonst 0,5 cm × 40^s, 50°/m, Spanne 5 Ticks/cm in 10…40); `FlashbackSpline` bitgleich mit Flashbacks `CatmullRom`/`KeyframeTrack#createKeyframeChange`; `TrackThinner` fortlaufend (lineare Prüfung, dann Spline-Prüfung mit Toleranz × 1,5, beim Speichern zusätzlich Prüfrunden); `LiveTrackCounter.update(buffer)` zählt während der Aufnahme; `PathPreview.of(frames, s, limit, scale)` Kennzahlen und Pfade für die Vorschau (tatsächlicher Pfad `actual` als Flashbacks Spline durch jeden Tick, je vier Zwischenschritte; `topGap`/`sideGap` = größter Abstand der gezeichneten Linien von oben bzw. Höhe, Ort des Rings; `maxOffPath` räumlich in beide Richtungen; `worstIndex`/`maxSameTime` zur gleichen Zeit für Graph und Kennzahl); `SamplePath` liest `assets/propwash/flashback/sample_flight.bin` (Details `docs/design/flashback-keyframe-budget.md`) |
| `compat/flashback/client/FlashbackBridge` | video | nur Reflection, beim ersten `status()` vollständig aufgelöst: `ABSENT` (Mod fehlt), `READY`, `INCOMPATIBLE` (`problem()`); `recorder()`, `recording()`, `paused()`, `writing()` (`Recorder#readyToWrite`), `writtenTicks(recorder)`, `replayId(recorder)`, `inReplay()`, `startRecording()`, `finishRecording()`, `addMarker(rgb, text)`, `writeScene(uuid, FlashbackScene)` → Index der Szene (ersetzt eine leere einzige Szene, setzt sie aktiv; sonst angehängt), `dataDirectory()`, `version()` |
| `compat/flashback/client/ReplayView` | drone-render | `hides(drone, cam, forward)`: nur in einer Flashback-Wiederholung; näher als `CORE_RADIUS` 0,5 Blöcke, oder näher als 0,5 + 2 Ticks Flugweg und nicht vor der Kamera; `QuadcopterRenderer#shouldRender` fragt es nach der eigenen FPV-Drohne ab |
| `video/client/record/FlashbackCam` | video | `INSTANCE`, Render-Thread. `start(mc, automatisch)` → `Result`, `stop(mc, StopReason)`, `toggle(mc)`, `recording()`, `paused()`, `saving()`, `recordedSeconds()`, `samples()`, `replay()`, `lastHandover()` (`Handover`: Aufnahme, Szenenindex, Name, Ticks, Keyframes, beendet, Grund; seit 0.5.2 dazu Keyframes ohne Grenze, Obergrenze, `capped`, erreichte Toleranz, Budget-Anteil), `budget()` (`Budget`: Anteil, Keyframes, höchstens, Obergrenze an, UI-Skalierung; `null` ohne Aufnahme), `overrideBudget` (nur Selbsttests), statisch `build(buffer, s, limit, scale)`, `lastTrack()`, `lastPath()`, `lastResult()`, `lastError()`, statisch `mounted(drohne)`, `EXPORT_ASPECT` 16:9, `MAX_TICKS` 15 min. Proben in `ClientBus.FRAME_START` (nach `PilotSession`), Zeit = `writtenTicks + Teil-Tick`, nur wenn Flashback schreibt und das Spiel läuft; Pose = Schwerpunkt + Linsen-Offset + Uptilt ohne Videolatenz, Sichtfeld vertikal für 16:9. Stopp rechnet den Pfad im Hintergrund und schreibt die Szene auf dem Render-Thread; hat die Kamera die Flashback-Aufnahme gestartet, beendet sie sie danach. Toasts mit Token `TOAST_TOKEN` |
| `video/client/record/ActionCamRecorder` | video | Taste `action_cam_record` und Funktion `RECORD` gehen an `FlashbackCam`, wenn die geflogene Drohne die Action-Cam (Flashback) trägt; „Beim Scharfschalten“ gilt für sie genauso (`FlashbackCam#onClientTick`) |
| `video/client/record/RecordingConfig` | video | `flashbackAutoRecord` (Standard an: Flashback-Aufnahme mitstarten und beenden), `flashbackSimplify` (0–1, Standard 1 cm; fehlt er, wird ein altes `flashbackDensity` übernommen: `EVERY_TICK` → 0, `REDUCED` → 1 cm), `flashbackKeyframeLimit` (Standard an) |
| `video/client/record/PathPreviewModel`, `PathPreviewWidget`, `PathPreviewScreen`, `PathPreviewDraw`, `FlashbackRescueScreen`, `SimplificationText`; `compat/flashback/client/FlashbackReplays`, `FlashbackUiScale`; `ui/client/widget/LineBatch` | video, ui | Vorschau (letzter Flug ab 10 s, sonst Beispielflug; Rechnung im Hintergrund, 100 ms nach der letzten Änderung), Tab-Widget mit Lupe, große Ansicht mit Zoom, Zeit-Schieber und Graph; „Aufnahmen prüfen“ liest `editor_states/*.json` nur als JSON und schreibt über `FlashbackBridge.replaceCameraKeyframes` (nur Spuren in Propwash-Farbe, vorher `.bak`); UI-Skalierung = Anzeigeskalierung des Fensters × Flashbacks Editor-Zoom; Linien als freie Quads (`LineBatch`/`LineQuads`), `FormLayout.placeWide(widget, höhe)` für hohe Zeilen |
| `osd/OsdElement#FLASHBACK_BUDGET`, `OsdWarnings.Warning#FLASHBACK_FULL`, `OsdData#flashbackFill`/`flashbackLimit` | osd | „Flashback-Zeitleiste“ (`FB 43%`) nur während der Flashback-Aufnahme, Vorsicht ab 75 %, über 100 % mit Obergrenze Ausdünn-Zeichen, ohne rot blinkend und Warnung „FLASHBACK VOLL“ |
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
folgt dem Pfad (unter 0,1 Blöcke), Drohne um die Kamera ausgeblendet. Seit 0.5.2 dazu `FlashbackBudgetSelfTests`
(`video: Flashback-Budget – …`): Live-Zähler gegen gespeicherte Spur, Spline-Nachbildung gegen Flashbacks `CatmullRom`
(und an jeder Szene gegen `createKeyframeChange`), OSD in vier Zuständen, Tab und große Vorschau, Reparatur einer
Szene mit 3.000 Keyframes, Eckpunkte der Zeitleiste in der Wiederholung bei UI-Skalierung 1 und 2. Bildschirmfotos (Rundgang):
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
| `race/world/WorldCourseMarker` `Passes`/`Mode`, `CourseRegistry#insertPasses`, `createWorldCourse(…, CourseMode)` (Befund WCOURSE-1) | race | Der Marker trägt optional `Passes` (alle Plätze des Elements im Format von `CourseOrder`, rückwärts mit `r`, etwa `1,3r`) und `Mode` (`point_to_point`); ohne die Felder gilt `Slot` vorwärts und der Modus Runden, alte Marker lesen und speichern sich unverändert. `WorldCourses.adopt` reiht alle Durchgänge über `insertPasses` ein (`insertGate` ruft es mit einem Durchgang auf); das erste übernommene Element legt den Kurs im Modus des Markers an. Neue Strukturen nutzen `world_course/v2/*`; die bisherigen Vorlagen (Stand 0.5.2) bleiben unter ihren Namen byte-gleich für Strukturstarts alter Welten (SHA-256 in `WorldCourseMechanicsTest`). Vorlagen tragen keine Kurs-Nummer und keine UUID, jeder Fundort bekommt einen eigenen Kurs mit eigener Bestenliste. Die Fluglinien der Vorlagen schreibt `tools/data/world_courses.py` nach `common/src/devtest/resources/worldcourse/lines.json` (JUnit und Selbsttest fliegen sie nach) |
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
| `osd/OsdPreset#parked`, `OsdConfig.VERSION` 4, Selbsttest `osd: Vorlagen ohne Überlappung` (`OsdOverlapSelfTests`) | osd | Jede Vorlage gibt jedem Element einen eigenen Standardplatz, auch den Elementen, die sie nicht zeigt (`park(...)`, z. B. Minimal: Geschwindigkeit 1/8 unter der VTX-Leistung 1/7, RSSI 6/1 neben LQ). Gespeicherte Layouts vor Version 4 ziehen ausgeschaltete Elemente vom Grundplatz auf den Vorlagenplatz und eingeschaltete, die genau auf einem anderen eingeschalteten liegen, auf ihren freien Platz. Der Selbsttest zeichnet jede Vorlage mit allen verschiebbaren Elementen (ohne Statistik) und dem Fadenkreuz auf ihren Standardplätzen mit dem echten `OsdRenderer` (Rahmen aus zwei Datensätzen mit breiten Werten) in 77 Fällen: 16:9, 16:10, 4:3 und 5:4 (Vollbild und 4:3-Bild im Breitbild), GUI-Skala 1 bis zur größten je Fenster, einmal auf Englisch und einmal auf Deutsch (`osd: Vorlagen ohne Überlappung auf Deutsch`, deutsche Texte sind breiter, z. B. „SPEICHERN 100 %“ 8,5 statt 6,7 Zellen); keine zwei Rahmen dürfen sich schneiden. Das Videosystem parkt deshalb in Minimal auf 28/12 und in Race auf 28/7, weit weg von der Aufnahme oben rechts. JUnit `OsdPresetTest#presetsPlaceEveryElementOnItsOwnCell`, `OsdParkingTest`. |
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
Nachtrag 0.6.0 (Befund G-OT4C-1-1): Das Banner steht nicht mehr fest in Zeile 3. `video.client.SpectateBannerPlacement`
(reines Java) wählt die erste freie Zeile aus 3, 2, 1, 4, 5, 0 (Unterkante über Zeile 6, Zeile 0 nur als letzte):
`occupants(config, addonSpecs)` sammelt jedes eingeschaltete verschiebbare Element und jedes eingeschaltete
Add-on-Element des gezeigten Layouts (`OsdHud.config()`, also auch des beobachteten Piloten; Add-on-Platz über
`AddonOsdKeys#peek`, das nichts anlegt), `row(grid, occupants, links, rechts, höhe)` prüft senkrecht die Zeile mit
Elementgröße (`TEXT_ROW_FILL × Größe`, mindestens die ganze Zeile) und waagerecht den Anker (Mitte ± 1 Spalte, links-
bzw. rechtsbündig 3 Spalten in Leserichtung). Ohne freie Zeile zählt erst nur der Anker im Banner, dann nur das
Warnfeld, sonst bleibt Zeile 3. `SpectateHud` misst immer mit Tastenzeile und deren Breite (das Banner springt nach 6 s
nicht) und merkt sich die gezeichnete Fläche: `lastBox()`, `lastRow()`, `bannerTop()` (ersetzt
`bannerTop(guiWidth, guiHeight)`). Ergebnis: MINIMAL Zeile 1 bzw. ab GUI-Höhe 448 Zeile 2, STANDARD und FULL Zeile 3,
RACE bei kleiner GUI Zeile 2 (Split in Zeile 4). Gilt auch für Kameramann und Gimbal allein (`SpectateSession#operate`).
Netzprotokoll unverändert. JUnit `SpectateBannerPlacementTest`, Selbsttest `addon-api-17: A2 – Zuschauer-Banner …`
(`docs/testing.md`), Bildschirmfotos `{spectate,operator,solo}_banner_warning_{en,de}.png`.

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
  (Kurs, Layout, Gates, `open` öffnet das Fenster, gewertete Runden, Zeilen = seit 0.6.0 Platz 1–50 + Klassenrekorde + eigene Zeiten mit
  Klassen- und Gesamtplatz, seit 0.5.0 mit `assist` je Zeile; Zeilen mit Flughilfe nur bei Regel an, mit eigenen Plätzen); `GhostRequestC2S(kurs, klasse, bekannte Revision)` → `GhostS2C` (Revision 0 = kein
  Geist, leere Daten = unverändert); `LobbyC2S(JOIN|LEAVE, kurs, Sekunden)`, `LobbyS2C`, `RaceStartS2C(kurs, Countdown,
  Piloten)`. Anfragen je Spieler höchstens 4/s.
- **Startaufstellung** `race.RaceLobby` (JUnit `RaceLobbyTest`): selbst Scharfschalten nahe am Start, wenn `RaceConfig.countdown`
  an ist, oder während dort eine Aufstellung offen ist → `LobbyC2S JOIN` (ohne Server-Kanal: lokaler Countdown). Seit 0.6.0
  startet ein Reset oder GPS-Rescue keine Aufstellung mehr (`race.ArmEdge`, `race.StandingStart`; eine Scharf-Flanke
  durch Auto-Arm zählt nicht, §15 „Auto-Arm nach Reset“). Aufstellen nur mit
  gesteuerter Drohne höchstens `RaceServer.LOBBY_RANGE` (32 Blöcke) vom Start-Gate; der Start folgt, sobald alle Piloten
  am Start aufgestellt sind (frühestens 0,5 s nach dem Öffnen) oder spätestens nach 10 s; Countdown = Wunsch des Ersten,
  3–10 s. Alle bekommen `RaceStartS2C` gleichzeitig. Mehrere Piloten → „Lauf“: Zielreihenfolge im Chat.
- **Startampel und Frühstart:** `race.StartLights` (3–5 Lampen, jede Sekunde eine rote mehr, bei LOS alle grün für 1 s);
  das Start-Gate leuchtet im Countdown rot, nach Frühstart gelb. Vorwärts durchs Start-Gate vor LOS = `FALSE_START`
  mit `LapTracker.FALSE_START_PENALTY_NANOS` (3 s) auf Runde 1 und Gesamtzeit; der Holeshot gilt dann als geflogen.
  Bewegung ohne Durchflug ist kein Frühstart mehr. Der Server prüft die Strafe (nur 0 oder 3 s in Runde 1) und die Runde
  ohne sie, Runde 1 darf bis 10 s vor der Startmeldung beginnen.
- **Bestenlisten-Fenster** `race.client.LeaderboardScreen` (`ThemedScreen`): Kurs wechseln (‹ ›), Filter „Alle“ und je
  Klasse, Spalten Platz (Gold/Silber/Bronze), Pilot, Zeit, Abstand, Klasse, Datum, eigene Zeile markiert (seit 0.6.0 alle
  Plätze, einklappbare Abschnitte und „Meine Zeit“, siehe „Bestenliste ohne Grenzen“). Öffnen: Kurs-Werkzeug Rechtsklick in die Luft (Server schickt `open`), Tab „Rennen“ →
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
Trick-Tutorials (seit 0.6.0, `docs/trick-tutorials.md`): `trick.tutorial.TrickChoreographies#of(TrickType)` liefert je Trick
eine `TrickChoreography` aus Phasen (`TrickMove`: Drehraten, Gas, Winkel oder Dauer, Steuerflug, Hindernisse), Varianten
teilen die ihres Grundtricks (`sharedWith`, `as`, `backwards`); `TrickFlight#fly` fliegt sie in `DroneSim` und zeichnet Lage,
Weg und Knüppel je Bild auf, `TrickTutorial#create` baut daraus die Szene `trick_<trick>` (Schritt je Phase, Texte
`propwash.trick.tutorial.*`). Tab „Freestyle“: Hover-Vorschau `trick.client.TrickPreview`, Klick öffnet über
`trick.client.TrickTutorials#open` den `SceneScreen` mit `SceneScreen.Options` (Titel, Kopfzeile, Schritt-Texte,
Stick-Cam `guide.client.screen.StickCamView` im Stick-Modus des Spielers); Zeitlupe 0,5× (Taste S) gilt für alle Szenen.
JUnit `trick.tutorial.TrickChoreographyTest` (die Erkennung erkennt jede Choreografie als genau ihren Trick) und
`TrickTutorialTest`; Selbsttest `trick.client.TrickTutorialSelfTests`.

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
| `race/GateStacking` | race (reines Java, JUnit `GateStackingTest`, `GateStackTurnTest`, `GateStackPreviewTest`) | `seat(unten)` = `y + height − frame` (Unterkante eines gleich ausgerichteten Gates darauf), `aligned(a, b)` (Kurs gleich oder um 180°, Toleranz 1°), `crossed(oben, unten)` = nicht `aligned`, `seat(oben, unten)` (`aligned` ⇒ `seat(unten)`, sonst `unten.y + height`: quer mit eigenem Balken obenauf), `seatOn(oben, unten)`, `sameColumn(a, b)` (beide stehend und ungeneigt, gleiche Mitte x/z ± 0,001; die Ausrichtung entscheidet nur die Sitzhöhe), `seatedOn(oben, unten)` (gleiche Säule, `oben.y = seat(oben, unten)`), `restsOn(oben, unten)` (`seatedOn` und `fits`), `fits(breiteOben, breiteUnten)` (oben nie breiter, unabhängig von der Drehung), `stackYaw(yawUnten, blickSpieler)` (Blick zurück ⇒ um 180° gedreht, sonst wie das Gate darunter), `stackOn(oberstes, größe, yaw[, drehung])` (Drehung um die Mitte der Auflage), `placementTurn(vorliebe)` (Boden-Drehung der Platzier-Vorliebe in Viertel- plus Feindrehung, nur bei stehender Lage, sonst 0), `stackFor(oberstes, yawOberstes, größe, blickSpieler, vorliebe)` (Lage des neuen Gates für Klick und Vorschau-Geist), `stackBase(oben, unten)` (`STACKED` oder `MOUNTED`), `halfTurn(grad)`, `turned(säule, index, kurs)` (Gate und alle darüber um denselben Winkel, Sitz neu), `check(oberstes, stapelhöhe, größe)` → `NONE`/`TOO_WIDE`/`TOO_HIGH` (`MAX_STACK` = 4), `refusal(gate, andere)`, `Stack(geometrie, fuß, StackRefusal)` mit `NONE`/`STANDING_ONLY`/`OCCUPIED`/`TOO_WIDE`/`TOO_HIGH`/`BLOCKED`, `column`/`top`/`above`/`below`/`base` auf einer Liste von `GateGeometry`, `afterRemoval(gates, index)` → `Drop(index, y)` für alle Gates darüber (`dropDelta`: das erste fällt auf seinen Sitz, die Drehung bleibt). `Base`: `BAR` (einzeln, mit unterem Balken), `LEGS` (unterstes Gate eines Stapels), `STACKED` (steht gleich oder um 180° ausgerichtet auf einem Gate, gemeinsamer Balken), `MOUNTED` (liegt gedreht quer auf dem Querbalken darunter, eigener unterer Balken, keine Füße); `Base.of(getragen, belegt[, quer])` |
| `race/GateGeometry#boxes(sink, base)` | race | Kollisionsboxen je Rolle: `BAR` wie bisher (4 Boxen), `LEGS` ohne unteren Balken, `STACKED` ohne unteren Balken und mit Pfosten ab `frame` (der Bereich darunter ist der Balken des Gates darunter). `boxes(sink)` = `BAR`. Die Kollision kennt das Flag `legs` nicht; die Unterkante der zählenden Öffnung regelt `legs` (Zeile unten) |
| `race/GateEntity#base()`, `covered()`, `supported()`, `refreshStack()`, `refreshColumn()`, `gateAbove()`, `gateBelow()`, `stack()` | race | `stack()` = ganze Säule von unten nach oben (Weltabfrage). `covered`/`supported`/`base` sind nicht synchronisiert: Server **und** Client rechnen sie beim ersten Tick eines Gates für die ganze Säule (`refreshColumn`, so sieht der Client ein frisch gestapeltes Gate und die Beine darunter im selben Tick) und danach alle 5 Ticks je Gate aus den (synchronisierten) Positionen nach; Stapeln und Nachrutschen aktualisieren die Säule auf dem Server sofort. `colliders()` folgt `base()` (Kollision wie gezeichnet). Wird ein Gate zerstört (`RemovalReason#shouldDestroy`, also Abbauen, `/kill`, `discard`), rutschen alle Gates darüber auf dem Server um seine Höhe minus Rahmenstärke nach (`setPos` + `needsSync`, Kurs-Eintrag sofort über `CourseRegistry#reconcile`); Kursplatz, Nummer und Richtung bleiben. Bewusst Nachrutschen statt Zerfallen: der Kurs bleibt vollständig und niemand verliert Items |
| `race/GateGeometry#withLegs(flag)`, `legs()`, `openingFloor()`, `crossing(…, forward, legs)` | race (reines Java, JUnit `GateLegsOpeningTest`) | Flag `legs` (Standard `false`, nur Gate-Art Gate; Elemente und Freiform-Gates bleiben unverändert): `openingFloor()` = `legs ? 0 : frame`, `insideOpening`, `crossing` und `stageCrossing` zählen ab `openingFloor()` (Pfosten und oberer Balken bleiben Rahmen). Die Überladung `crossing(…, forward, legs)` rechnet mit vorgegebenem Flag ohne neue Objekte. `equals`/`hashCode` unterscheiden das Flag (Hash ohne Flag unverändert), `sameAs` nicht; `at`, `withTilt`, `withVariant` liefern Geometrie ohne Flag. Kursdaten (`CourseData.Gate#geometry`) setzen es nie: `layoutHash`, `LapTracker#sameLayout`, Bestenlisten und Ghosts bleiben unverändert |
| `race/GateEntity#geometry()` | race | Gate-Art Gate mit `base() == LEGS` → `withLegs(true)`. Damit zählen am Server Rundenprüfung (`RaceServer`, Treffer aus der Entity-Geometrie) und Redstone-Puls (`GateSignals`) unten bis zum Boden; `geometryAt` (Kollision, Begrenzungsquader) bleibt ohne Flag |
| `race/GateOpenings`, `LapTracker#setOpenings(anbieter)`, `openings()`, `GhostTrack#splits(gates, anbieter)` | race (reines Java, JUnit `LapTrackerTest`, `GateLegsOpeningTest`) | Anbieter `legs(gateIndex)`, `NONE` = immer `false` (Standard). Der Tracker prüft jeden Durchflug erst wie bisher; nur wenn das Segment die Gate-Ebene im Streifen `0 < up ≤ frame` zwischen den Pfosten schneidet, fragt er den Anbieter und zählt bei `true` (vorwärts als Durchflug, rückwärts als falsche Richtung). Durchflüge weiter oben fragen ihn nie. Die Geister-Splits rechnen genauso |
| `race/client/ClientGateOpenings#of(kurs, gates)`, `following(tracker)`, `find(kurs, platz, geometrie)` | race (Client) | Sucht zum Kurs-Gate das geladene `GateEntity` im Quader ±0,25 Block um dessen Ursprung mit gleichem Kurs und Platz und meldet `base() == LEGS` (nur stapelbare Gates); nicht geladen → `false` (Verhalten 0.5.2). `ClientRaceTracker` setzt `following(tracker)` einmal im Konstruktor (folgt jedem Kurswechsel), `GhostLibrary` gibt `of(kurs, gates)` an alle drei Geister-Splits |
| `race/GateItem#stackOn(ziel, spieler, stack)`, `stackFor(ziel, spieler)`, `lidPath(ziel, spieler)`, `GateItem.free(level, geometrie, base, körper)` | race | Rechtsklick mit einem Gate-Item auf ein beliebiges Gate der Säule (`GateEntity#interact`, beide Seiten) setzt es auf das oberste Gate, in der Boden-Drehung der Platzier-Vorliebe relativ zum Gate darunter (`GateStacking#stackFor`). `stackFor` liefert Lage, Fuß und Ablehnung (`GateStacking.Stack`) für Klick und Vorschau-Geist gleich; `lidPath` (Lage liegend auf einem stehenden Gate) wählt stattdessen den Deckel. Abgelehnt mit Meldung: nicht stehend (`…stack_standing_only`), etwas sitzt oben (`propwash.race.stack.occupied`), breiter als das Gate darunter (`propwash.race.gate.stack_too_wide`), Säule voll (`…stack_too_high`), Blöcke, Weltgrenze, Rahmen anderer Gates oder Spieler/Drohnen im neuen Rahmen (`…blocked`). `free` prüft beim normalen Aufstellen auch Rahmen anderer Gates (kein Gate ineinander) |
| `race/RaceLine#needsDetour(a, b)`, `detourPoint(a, b)` | race | Zwei aufeinanderfolgende Kurs-Gates mit gleicher Richtung, deren Mitten fast in derselben Ebene und seitlich überlappend liegen (Doppel-Gate, Leiter ohne Zickzack): die Linie läuft über einen Zwischenpunkt `max(Breite)/2 + 2` Blöcke neben der Säule (Ebene wird außerhalb der Pfosten gekreuzt), mit Kehren vor und hinter der Säule. Gegenläufige Leitern (Zickzack) brauchen keinen Umweg |
| `race/client/GateRenderer#frameModelId/lightsModelId/startFrameModelId(size, base)`, `variantSuffix(base)`, `chevronFloor(size, base)`, `plateCenterX(size, covered)`, `plateBottom(size, covered)`, `GateRenderState#covered`, `#base` | race | Modell je Rolle (`items/race/gate_<size>_{frame,lights,start_frame}[_legs|_stacked]`, `docs/ASSETS.md` „Gates“); Maßstab und Versatz kommen immer vom `BAR`-Rahmen. Steht ein Gate auf diesem (`covered`), sitzt die Nummern-Tafel seitlich außen am Pfosten (1/16 Block Luft, Oberkante = Oberkante des Gates), das Start-Gate zeigt statt des Banners eine solche Tafel mit „START“ (`propwash.race.gate.start_short`), die roten Eckmarken der Rückseite entfallen (die Ecken sind dann T-Stücke des Gates darüber, die rote Leiste unter der Tafel bleibt), das Namensschild beim Kurs-Werkzeug rückt unter den oberen Balken. Die Bodenpfeile eines getragenen Gates schweben auf Höhe der Balken-Oberkante vor seiner Einflugseite |
| `guide/client/render/SceneView`, `SceneGateVisual#prepare(gate, actor, base, covered)` | guide | Flugschul-Gates bestimmen ihre Rolle aus den Positionen aller voll sichtbaren Gate-Akteure (`GateStacking.base`) und zeichnen über `GateRenderer#fill` dieselben Modelle, Drehung und Halterungen wie die Welt |

Stapeln ist rein geometrisch: es gibt keine gespeicherte Verbindung, jedes Gate bleibt ein eigenes Entity und eigenes
Kurs-Gate (Kurs-Werkzeug nummeriert es wie jedes andere, eigene Pfeile, eigenes grün/weiß). Rundenzählung und
Serverprüfung zählen wie bisher nur Durchflüge durch die Öffnung des erwarteten Gates in seiner Richtung; der gemeinsame
Balken zwischen zwei Öffnungen ist Rahmen. Bei einem Gate auf Beinen zählt die Öffnung seit 0.6.0 bis zum Boden, wo sein
unterer Balken fehlt (`docs/gate-legs.md`); einzelne Gates und Gates darüber zählen ab Rahmenstärke. Weltkurs-Gates lassen sich genauso stapeln; die Drohnen-Arena hat eine Leiter aus drei Gates
(`tools/data/world_courses.py`, `Course.stack`). Selbsttests `race.client.GateStackSelfTests` (Grenzen und Nachrutschen,
Leiter + Doppel-Gate als Kurs mit Nummern, Rolle und drei Kollisionsboxen je gestapeltem Gate beim Client, alle
Leiter-Modelle auflösbar, Runde durch alle fünf Öffnungen, Aufräumen; „race: Gate-Stapel: …“ Durchflug in halber
Rahmenhöhe unten am Gate auf Beinen auf Client und Server mit Redstone-Puls, einzelnes Gate erst ab Rahmen, nach Abbau
des oberen Gates wieder Rahmen, frisch gestapelt in allen fünf Phasen des Nachrechnens Beine beim Client spätestens einen
Tick nach dem neuen Gate; „race: Weltkurs-Leiter: …“ Rekord, Geist und Layout-Hash der Drohnen-Arena unverändert),
Beitrittstest `PROPWASH_JOINTEST_STACKGATE` mit `dev.StackGateJoinCheck`, Bildschirmfoto `gate_stacks.png` (Leiter aus
drei Gates und Doppel-Gate), Flugschul-Schritt `race/stack`.

Nachtrag 0.6.0 (Befund PTP-1): `GateStacking#restsOn(oben, unten)` verlangt zusätzlich `fits` – ein breiteres Gate in
Sitzhöhe (`seatedOn`) gehört nicht zum Stapel und steht auf eigenen Beinen (`BAR`), auch in Welten aus 0.6.0-rc.1. Neue
Lagen prüft `GateStacking#refusal(gate, andere)` (`TOO_WIDE`: säße auf einem schmaleren oder trüge ein breiteres,
`TOO_HIGH`: mehr als `MAX_STACK`), mit der Säule aus der Welt über `GateMounting#stackRefusal(level, gate, ohne)`; das
nutzen `GateMounting#apply` (Ergebnisse `STACK_TOO_WIDE`, `STACK_TOO_HIGH`), das Gate-Menü (`GateEditor.Status.TOO_WIDE`,
`TOO_HIGH`), `GateItem#useOn` und `GatePreview`. Mit „Kurs-Elemente frei platzieren“ bleibt Überlappung erlaubt, ein
ungültiger Stapel entsteht nicht. JUnit `GateStackRulesTest`, Selbsttest `race-topo: Stapelregeln`.

Nachtrag 0.6.0 (Befunde RC3P-3 und RC3P3-1): Gestapelte Gates drehen in den Schritten am Boden; gleich oder um 180°
ausgerichtet teilen sie sich den Balken (`STACKED`), jede andere Drehung liegt mit eigenem Balken quer obenauf
(`MOUNTED`, `seat(oben, unten)`). Beim Stapeln gilt die Boden-Drehung der Platzier-Vorliebe relativ zum Gate darunter;
mit einem Gate-Item zeigt der Blick auf ein stehendes Gate denselben Vorschau-Geist wie am Boden (aus
`GateItem#stackFor`, also genau das Gate des Rechtsklicks) samt Hinweisfeld, und die Dreh-Taste dreht dabei die Vorliebe
statt des Gates im Blick (`GateKeyRoute` `TURN_STACK`/`NEXT_MOUNT_STACK`). JUnit `GateStackTurnTest`,
`GateStackPreviewTest`, `GateKeyRouteTest`; Selbsttests `race-topo: Stapel drehen` und `race-topo: Stapel-Vorschau`.
Nachtrag 0.6.0 (Befund AUTO56-1): Am Deckel (Lage liegend, `GateItem#lidPath`) ändert `TURN_STACK` die Vorliebe nicht
(`GateKeyRoute#stackPreference` dreht nur stapelbare Boden-Lagen), denn der Deckel liegt immer abwärts; das Hinweisfeld
nimmt dort die Richtung aus der Deckel-Geometrie (`GatePreview.Target#lid`, `GateMounting#describeDirection`) und nennt
als Taste nur den Lagewechsel (`propwash.race.lid.hint`). Selbsttest `race-topo: Stapel-Vorschau – Deckel: …`.
Nachtrag 0.6.0 (Befund OT4-4): Stapel stehen direkt nebeneinander, auch ohne „Kurs-Elemente frei platzieren“.
`GateStacking#joined(gestapelt, Nachbarn)` gibt dem gestapelten Gate die Bits `JOINT_LEFT`/`JOINT_RIGHT` der Seiten, deren
Pfostenmitte genau auf der eines verträglichen Nachbarn liegt (`placementJoints`, ohne Einrasten, die Säule bestimmt die
Lage); `GateItem#stackFor` prüft Platz mit diesem Rahmen (Vorschau und Klick), danach `refreshAround` und Meldung
`propwash.race.row.joined`. Echte Kollisionen bleiben `BLOCKED`. `GatePreview.Target#refusal` trägt die
`StackRefusal`, das Hinweisfeld nennt `TOO_HIGH`, `TOO_WIDE` und `OCCUPIED` mit eigenem Text
(`propwash.race.stack.preview_too_high`, `…_too_wide`, `…_occupied`). JUnit `GateStackRowTest`,
`GateStackLimitPreviewTest`; Selbsttest `race-topo: Stapel nebeneinander`.

### Gates an Wand und Decke, Tauchtore und Drehen (race)
Jedes Gate (alle Größen, auch Start/Ziel) steht am Boden, hängt an Wänden oder unter Decken und lässt sich in
90°-Schritten drehen; ein Tauchtor ist ein gewöhnliches Gate in der Lage `wall_dive`. Entwurf, Recherche und Tabellen
der Rahmen: `docs/design/gate-mounting.md`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/GateMount` | race | Lagen `STANDING`, `FLOOR_FLAT` (Boden), `WALL_DIVE`, `WALL_SIDE`, `WALL_WINDOW` (Wand), `HANGING`, `CEILING_FLAT` (Decke); `surface()`, `turns()` (4 = Richtung in Vierteldrehungen, 2 = Flugrichtung umkehrbar), `next()` (nächste Lage derselben Fläche), `stackable()` (nur `STANDING`), `base()` (`MOUNTED` = Rahmen ohne Füße), `CODEC` (stabile Schlüssel), `pack/unpackMount/unpackFlipped` (ein Byte) |
| `race/GateGeometry` | race (reines Java, JUnit `GateMountTest`) | Orthonormaler, rechtshändiger Rahmen `side`/`up`/`normal` je Lage aus `yawDegrees` und `flipped`; Ursprung = Mitte der Unterkante. `crossing`, `insideOpening`, `boxes`, `bounds`, `centerX/Y/Z`, `pathLength` gelten für jede Lage, Boxen bleiben achsparallel. `STANDING` rechnet bitgleich wie vor den Lagen (Goldens, Geister, Bestenlisten unverändert) |
| `race/GatePlacement` | race (reines Java, JUnit `GatePlacementTest`) | `surface(faceY)`, `resolve(click, lage, stellung, größe)` (Raster: Öffnung ungerader Breite mittig im Block, gerader Breite auf der nächsten Blockkante), `mountPoint(gate)` und `rotated(gate, lageWechseln, gestapelt)` drehen um den Befestigungspunkt; `Preference` = Lage und Stellung je Fläche |
| `race/GateMounting` | race | Server: Wahl je Spieler (`GatePreferenceC2S`, beim Abmelden vergessen), `rotate(spieler, gate, lageWechseln[, schritt])` → `Result` (Reichweite +2, `mayBuild`, Kursbesitz, Platz ohne die bewegten Gates). Gestapelt: Drehen in den Schritten am Boden über `columnTurn`/`applyTurn` (das Gate und alle darüber, Sitz und Fuß neu: gleich oder 180° `STACKED`, sonst quer `MOUNTED`), mit Aufsatz oder geteiltem Pfosten nur 180° (`STACK_TURN_LOCKED`), Lagewechsel `STACK_LOCKED`; im Gate-Menü `edit` nur Gieren (`headingOnly`). Meldungen `propwash.race.mount.*`, `propwash.race.stack.turn_locked` |
| `race/RacePayloads.GatePreferenceC2S`, `GateRotateC2S` | race | C2S, Protokoll 8 (seit 11 mit Feinwinkeln bzw. Schritt) |
| `race/client/GateMountKeys`, `race/client/GateKeyRoute` | race | Taste `key.propwash.gate_rotate` (Standard `KEY_R`, geteilt mit `key.propwash.drone_flight_mode`): Stellung drehen, mit Schleichen Lage wechseln; Blick auf ein Gate mit Gate-Item oder Kurs-Werkzeug in der Hand dreht dieses Gate. Ausnahme Stapeln: Gate-Item (kein Kurs-Element, kein Freiform-Gate) und Blick auf ein stehendes Gate (`GateMountKeys#stacking`) drehen die Stapel-Vorschau, also die Boden-Vorliebe (`TURN_STACK`, Schleichen `NEXT_MOUNT_STACK`, `GateKeyRoute#stackPreference` = `turnedBy`/`nextMount` der Fläche Boden); das Gate im Blick dreht dann nur das Kurs-Werkzeug. `GateKeyRoute#route` entscheidet nach Fenster, Pilot-Sitzung (dann gehört die geteilte Taste dem Flugmodus), Hand (Gate, Kurs-Werkzeug, Funke), Gate im Blick, Stapel-Vorschau, Schleichen und geteilter Belegung (`GateMountKeys#sharedWithFlightMode`) |
| `input/client/KeyDefaults#SHARED`, `sharesKey`, `mixin/input/client/KeyMappingMixin` | input | Paare, die sich eine Taste teilen dürfen (nur Flugmodus + Gate drehen): `duplicates` übergeht sie, `KeyMapping#same` meldet sie nicht als gleich (Steuerung beider Loader ohne Warnung), der Tab „Tasten“ nennt sie im Tooltip als geteilt statt als Konflikt |
| `race/client/GatePreview` | race | Durchscheinende Vorschau (gleicher Renderer, `DroneGhost.tinted`), Pfeil in Flugrichtung, rot bei fehlendem Platz, Hinweisfeld im Propwash-Theme (`status(ziel, vorliebe)`, `keys(ziel)`). Mit Gate-Item auf ein Gate: Deckel (`GateItem#lidPath`) oder Geist des gestapelten Gates aus `GateItem#stackFor` mit dessen Fuß (`Target#base`), rot bei jeder Ablehnung außer „nicht stehend“ (dann kein Geist); Hinweisfeld wie am Boden mit Lage und Drehung der Fläche Boden |
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
| `ui/HintMemory`, `ui/client/FirstUseHints` | ui (Zählung reines Java, JUnit `HintMemoryTest`) | Merkt je Spieler, wie oft ein Hinweis gezeigt wurde (`config/propwash/hints.json`, höchstens 32 Spieler). Erster Nutzer: der Hinweis zur freien Maus in „Gate einstellen“ (`GateEditor.MOUSE_HINT`, die ersten drei Male hervorgehoben, `docs/design/gate-edit-mode.md` §4a). Neue Hinweise nehmen einen eigenen Schlüssel und `fresh`/`shown`/`learned`. |
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
| `drone/client/render/DroneItemRenderer` | drone-render | Sonder-Renderer `propwash:drone_build` (registriert in `DroneClient`) für `items/drone.json`. `extractArgument` → `Look(build, wear, paint, battery)` aus `drone_build` (fehlt → FREESTYLE), `part_wear` (nur die sichtbaren Prop-Stufen), `drone_paint`, `battery_installed`; der Record ist zugleich der Schlüssel des GUI-Item-Atlas (gleicher Bauplan = ein gezeichnetes Symbol). Gezeichnet wird dieselbe Baugruppe wie an der Entity (`DroneVisuals.submit`), je `Look` zwischengespeichert (höchstens 96, neu nach Resource-Reload oder Render-Config). Größe: FREESTYLE liegt exakt wie das bisherige Icon-Modell (Skalierung 1), andere Baupläne werden um die Mitte ihrer Hülle auf `fitFactor` = (r / r₅)^0,3 geklemmt auf 0,72…1,0 des FREESTYLE-Radius gebracht (Whoop gut lesbar, 7" nicht größer als 5"), Unterkante auf der FREESTYLE-Unterkante. `getExtents` = Kasten der FREESTYLE-Hülle. Anzeige-Transformationen kommen aus dem Basis-Modell `propwash:item/drone` Symbolform (`readableShapes`: Props als `propWide`, Rahmen ohne Modell als `frameIcon`) nur mit `"icon": true` (Fall `gui` in `items/drone.json`, eigener Zwischenspeicher `iconFitted`); Rahmen mit Modell bleiben auch im Slot Modell. Hand, Kopf und Regal (`fitted`) zeichnen die Baugruppe wie in der Welt (Befund OWNP-1). |
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
| `osd/OsdLayoutCodec` | D | OSD-Layout Version 1 (Schema in `docs/api/addon-guide.md` §12): `encode(config)` (Kopie, Farben klein, `col`/`row` nur bei verschiebbaren Elementen, `color`/`scale` nur bei eigenen Werten), `decode(json, basis)`: Basis liefert `hideVanillaHud`/`analogBreakup`, Preset (unbekannt → Standard) legt alle Elemente fest, Stil fehlend → Standardwert, unbekannte Elemente/Felder übersprungen, danach `OsdConfig.sanitize`; wirft `IllegalArgumentException` bei leerem, kaputtem, zu langem (> 8192) JSON oder fehlendem `"v"` (JUnit `OsdLayoutCodecTest`: Rundreise bitgleich). Seit 0.6.0 schreibt `encode` nach den eingebauten Elementen die Add-on-Schlüssel `<ns>:<pfad>` aus `OsdConfig.elements` (`on`, `col`, `row`, ggf. `color` und `scale`; eingeschaltete zuerst, sonst nach Schlüssel), solange das Layout höchstens 8192 Zeichen hat; `decode` übernimmt gültige Add-on-Schlüssel (`AddonOsdKeys#isAddonKey`) auch ohne installiertes Add-on, Add-on-Elemente, die im Layout fehlen, behalten ihre Einstellungen; Layouts ohne Add-on-Einträge bleiben bytegleich (JUnit `AddonOsdTest`, `CovPw3ShareTest`) |
| `guide/GuideScenes` | D | `register(factory)` / `register(factory, sichtbar)` holen erst die eingebauten Szenen nach (Addon-Szenen stehen immer dahinter); `all()` = sichtbare Szenen (Übersicht, Item-Zuordnung, Selbsttest „alle Szenen“), `registered()` = alle; `byId` nimmt `propwash:x` wie `x` und liefert `null` bei ungültigen Ids; eine werfende Sichtbarkeit gilt als unsichtbar |
| `guide/scene/Scene`, `SceneBuilder` | D | Ids `name` (Propwash) oder `ns:name` (Addons), `propwash:name` wird zu `name`; Texte `propwash.guide.scene.<name>.…` bzw. `<ns>.guide.scene.<name>.…` (`keyPrefix`, `namespace()`, `builtin()`); Schlüssel der eingebauten Szenen unverändert (JUnit `SceneNamespaceTest`) |
| `api/client/PropwashClientApi` (Ergänzungen) | D | `registerFlightSchoolScene(szene, sichtbar)` (Addon-Szenen brauchen einen eigenen Namensraum, sonst `IllegalArgumentException`), `flightSchoolDrone(BuildSpec, zusammengebaut, maßstab)`, `openFlightSchoolScene(id, parent)`; dazu `ImportLayer`/`TuneGroup#translationKey()` (`propwash.api.import.layer.<e>`, `propwash.api.tune_group.<g>`, Texte in `common/src/lang/api/`) und `BuildStats.warningTranslationKey` |
| Beispiel-Addon `dev.example.*` (devtest) | D | Namensraum `propwash_example`, nur API: Java-Teile `m2806_1300`, `p13_7_2b_carbon`, `race_stretch_5`; JSON-Teile (`common/src/devtest/resources/data/propwash_example/propwash_parts/`) `deadcat_5`, `xclass_13` (Halterung `propwash_example:m42`), `stack_30_8s`, `m4214_400`, `b8s_4500_lion`, Chemie `lihv_445`, geladen mit `loadBundled("propwash")`; alle `creative: false`; Preset `propwash_example:xclass` (nicht im Kreativ-Tab); Werkbank-Knopf `propwash_example:import_example` mit Kind-Fenster `ExampleImportScreen`, Tab `propwash_example:api_demo` (Order 1010; nicht `readOnly`, weil seine Knöpfe sofort wirken: `Storage.MIXED` mit eigener Fußzeile `propwash_example.tab.footer`), Szene `propwash_example:frames`; Zählzeilen des Import-Fensters mit eigenen Schlüsseln für 0, 1 und mehr (`.none`/`.one`/`.many`). Sichtbarkeit über `ExampleAddon.showcase()`: in normalen Entwicklungs-Runs an, im Selbsttest nur in den eigenen Schritten (der Tab wird dort erst in `ApiSelfTests` angemeldet, weil `UiSelfTests` alle angemeldeten Tabs zählt) — kein fremder Selbsttest und kein fremdes Bildschirmfoto ändert sich |
| `apiimpl/client/ApiSelfTests`, `ApiShowcase` (devtest) | D | Schritte „api: …“ (Register, Baupläne, Items, Presets/Kreativ, OSD, Werkbank-Import mit Rundreise und `DRONE_ASSEMBLED`, Tuning-Gruppen und Lack auf die gehaltene Drohne, Ratenbegrenzung/`NO_TARGET`/`UNKNOWN_PARTS`, Werkbank-Knopf, Tab, Szene); Bildschirmfotos `api_workbench_extension`, `api_workbench_import`, `api_config_tab` (am Ende von `ShowcaseTour` vor dem Aufräumen) |
| `common/build.gradle`, `build-logic/…/multiloader-loader.gradle` | Foundation (von D ergänzt) | Konfiguration `commonDevtestResources`: `common/src/devtest/resources` fließt in `processDevtestResources` beider Loader (nur Selbst-/Rauchtest- und NeoForge-Dev-Runs, nie ins Release-Jar) |

**Flugmodell und Rendering fremder Teile (B, C) und Integration 0.4.0:**
| Datei | Besitzer | Vertrag |
|---|---|---|
| `sim/Telemetry#motorPowerLimit` (Grenze des ESC), `motorHot`, `escDerating`, `escBurned`; `SimEventType.MOTOR_HOT`, `ESC_DERATE`, `MOTOR_THERMAL_WEAR`, `ESC_BURNED` | B (seit 0.6.0 Motor-Hitze Phase B) | Abgeregelt wird nur im ESC nach seiner Temperatur: ab 140 °C linear bis 25 % bei +15 K wie BLHeli_32 (`ESC_DERATE` mit 10 K Hysterese, ESC-Kühlung nach der Platinengröße der Stack-Halterung); ein heißer Motor (`MOTOR_HOT`) verliert ab `derateStartC` bis −10 % Drehmoment (`PowerSystem#torqueScale`) und verschleißt, ab `maxTempC` stark (`MOTOR_THERMAL_WEAR`; Modell und Zahlen in `docs/design/realism/motor-heat.md`); der ESC brennt beim Scharfschalten durch, wenn der Akku mehr Zellen hat als der Stack erlaubt (`AirframeSpec.escOvervoltage`; keine eingebaute Kombination, JUnit `EscVoltageTest`). `SimTuning.thermalLimits` und `escOvervoltage` schaltet die Welt-Regel `propwash:hardware_limits` ab (bis 0.4.0 Schalter „Hardware-Grenzen“ im Tab „Simulation“) |
| `drone/net/HardwareWearC2S`, `drone/DroneWear#applyHardware` | B | C2S `propwash:drone_hardware_wear` (Entity, Reset-Epoche, ESC durchgebrannt, Hitzeverschleiß je Motor): der Server prüft Pilot, Epoche, die Welt-Regel `propwash:crash_damage` und dass der Bauplan wirklich überspannt, deckelt den Hitzeverschleiß je Paket und schreibt beides an die Drohne |
| `sim/BuildAnalysis#sustainedMotorTempC`, `propsInViewPercent`; `BuildWarning.MOTOR_THERMAL`, `PROPS_IN_VIEW` (ab 30 %, seit 0.4.1 aus dem FPV-Aufbau des Renderers: `sim.FpvRig`), `ESC_OVERVOLTAGE` | B | Werkbank-Bericht und `BuildStats`; `NaN` wird nicht angezeigt. Texte `propwash.content.workbench.warning.<w>` stehen in `common/src/lang/content/` (JUnit `ContentDataTest`) |
| `osd/OsdWarnings.Warning.MOTOR_HOT`, `ESC_HOT`; `OsdData#motorHot`, `#escDerating` | osd (Integration, seit 0.6.0 Phase B) | OSD-Warnungen „MOTOR ÜBERHITZT“ (ein Motor ist heiß, `Telemetry.motorHot`) und „ESC ÜBERHITZT“ (der ESC regelt ab, `Telemetry.escDerating`), Stufe Warnung, blinkend; Selbsttest „parts-flight: überhitzter ESC drosselt, überhitzte Motoren verschleißen …“ und Gruppe `drone: Motor-Hitze` (`drone.client.MotorHeatSelfTests`, Bildschirmfotos `motor_heat_*`) |
| `drone/client/render/FpvProps`, `FpvPropGeometry`, `DroneRenderConfig#fpvProps` | C | eigene Props im FPV-Bild (Schalter „Darstellung“, Standard an). Die Kamera-Entity (eigene FPV- oder Zuschauer-Drohne) wird dafür extrahiert: `mixin/drone/client/LevelExtractorMixin` hebt über `FpvProps.extractsCameraDrone` die Vanilla-Regel „Kamera-Entity nicht zeichnen“ nur für diese Drohne auf; `QuadcopterRenderer` zeichnet dann nur die sichtbaren Props. Im Iris-Schattenpass nie: `FpvProps.submit` bricht dort ab (`shadowPassCalls()`), stattdessen zeichnet `QuadcopterRenderer` die echte Drohne in Originalgröße an der angezeigten Pose (`FpvView.displayedBody`, `DroneVisuals.placeAtDisplayedPose`, `fpvShadowBodies()`), Halos und Motor-Glühen fehlen im Schattenpass (`DroneRenderState.shadowPass`); Schalter „Schatten“ aus nimmt dort alle Drohnen heraus (`docs/design/shader-packs.md`). Drehende Props als weiche Scheibe in der Prop-Farbe (`FpvProps.buildBlur`, Deckkraft 35 → 11 %, nie unter der Alpha-Schwelle), stehende als Blatt-Modell; JUnit `PartIconAndFpvBlurTest` |
| `drone/client/render/FpvGuards`, `FpvGuardZone`, `FpvGuardMesh`, `QuadCapture` | C | Ducts im FPV-Bild (seit 0.5.0, eigener Schalter „Ducts im FPV-Bild“, `DroneRenderConfig#fpvDucts`, unabhängig von „Props im FPV-Bild“; die Kamera-Drohne wird extrahiert, wenn einer der beiden an ist): Flächen des Rahmenmodells bzw. der Lack-Variante (`QuadCapture` fängt die Item-Flächen samt Transformation ab, ohne Modell die `FallbackShapes`-Form), Auswahl Ring um jede Motorachse plus mindestens 5 rundum wiederholte Flächen (`FpvGuardZone.select`), Zeichnen an der Linse mit derselben Tiefenabbildung wie die Prop-Scheiben (`FpvPropGeometry.pushScale`), nicht im Schattenpass und nicht bei `FpvProps.maskColor`; `FpvProps.guardMaskColor` für den Selbsttest. JUnit `FpvGuardsTest`, Selbsttest `FpvGuardSelfTests`, `docs/ASSETS.md` „Ducts im FPV-Bild“ |
| `drone/client/render/FpvArmZone`, `FpvQuadSelector`, `FpvGuards#arms`, `FpvGuards#part`, `FpvProps#armMaskColor`, `FpvPropGeometry#armsVisible`, `#attached` | C | Arme und Motoren im FPV-Bild (seit 0.6.0, Befund FB1-1, Schalter „Props im FPV-Bild“): Auswahl der Rahmenflächen nach Lage (`FpvArmZone`: nicht über der Motorachse, nicht über die Mittelebene, mindestens halber Motorabstand, ohne die Flächen von `FpvGuardZone`), Motoren vollständig; Zeichnen wie die Ducts (`FpvProps.emitGuards`), ohne Einfluss auf `nearScale`, nicht in den Messungen `maskColor` und `guardMaskColor`; `DroneRenderState#fpvArms`, `#fpvMotors`; Motor ohne Arm (`FpvArmZone#attached`): kein Prop und kein Motor an diesem Platz. Kreativ-FC: immer der eingebaute Rahmen. JUnit `FpvArmsTest`, Selbsttest `FpvArmSelfTests`, `docs/ASSETS.md` „Arme und Motoren im FPV-Bild“ |
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

### Fisheye-Objektiv (video, osd, content, drone, sim; 0.5.2, Entwurf `docs/design/fisheye-lens.md`)

Persönliche Anzeige-Einstellung, kein Netzwerk-Feld, keine API-Änderung, Flugmodell unberührt. Stärke 0 % rechnet
bitgleich wie 0.5.1, 80 % (Standard) bitgleich wie die erste 0.5.2-Fassung mit Schalter.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `video/FisheyeLens` | video | `of(H, Stärke, ImageArea)` (ohne Stärke 80 %): Abbildungsfamilie `map(θ, q)`/`unmap(r, q)`/`derivative(θ, q)` mit `q = bend(Stärke) = 1 − Stärke/0,8` (`tan(√q·θ)/√q`, f-θ, `tanh(√−q·θ)/√−q`), `focal = (Bildbreite/2) / map(H/2)`, `DEFAULT_PERCENT` = 80, `MAX_PERCENT` = 100, `PERCENT_STEP` = 5, Bildkreis bis `MAX_FIELD_DEG` = 80°, Tangens der halben Renderbreite/-höhe (`tanX`, `tanY`) so, dass jeder Bildpunkt im Bildkreis Quelle hat; `renderVerticalDeg`, `flatTangent`, `centreUpscale`, `centreMagnification`, `centreEquivalentVerticalDeg`, `project`, `tangentAt`, `sourceAt`; `verticalDeg`/`diagonalDeg(H, Seitenverhältnis, Stärke)` für die Tab-Zeile (reines Java) |
| `video/client/VideoConfig#lensStrength`, `#migrate`, `VideoConfigTab` | video | Prozent 0–100, Standard 80 (fehlt das Feld, gilt der Standard); altes Feld `lens`: `FLAT` → 0, sonst 80; Regler „Fisheye: …“ in 5-%-Schritten mit Tooltip, Tab-Zeile mit Breite × Höhe × Diagonale der eingestellten Stärke |
| `video/client/FpvLens` | video | `strengthPercent()` (Einstellung oder Test-Schalter `testStrength`, `NO_TEST` = −1), `lens(H, area)` nur bei Stärke > 0 und wenn `VideoPass#lensReady`, `rendered()` = Objektiv des aktuellen Bildes (gesetzt in `VideoState#cameraFov`, gelöscht ohne FPV und im Panorama-Modus) |
| `video/client/VideoState#cameraFov`, `#fpvFov`, `#displayFov` | video | `cameraFov` für `Camera#calculateFov` (setzt `FpvLens#rendered`), `fpvFov` ohne Nebenwirkung, `displayFov` = flaches Sichtfeld mit dem Maßstab der Fisheye-Mitte |
| `video/client/VideoPass`, `post/fpv_lens.fsh` | video | Pipeline `propwash:pipeline/fpv_lens` (`InSampler`, UBO `PropwashLens`: Größe, Bildbereich, `focal`/Radius des Bildkreises in Pixeln/`tanX`/`tanY`, Kantenbreite und `q`); entzerrt vor den Videoeffekten (in `frame`) oder allein (Kopie → Hauptbild); läuft auch mit Shaderpack und Ersatz-Overlay; eigener Fehlerzustand `lensFailure` (dann flach), `retry()` setzt ihn zurück; `lensFramesDrawn()` für Tests |
| `osd/OsdProjection#setupFisheye`, `osd/client/OsdRenderer.View#fisheyeDeg`, `#fisheyeBend`, `OsdHud#liveView` | osd | Projektion `r = f · map(θ, q)` über die Breite des OSD-Rasters; `fisheyeDeg` = 0 heißt flach |
| `video/client/record/ActionCamRecorder` | video | Ausschnitt mit `FisheyeLens#flatTangent` statt aus dem breiten Sichtfeld; `lastTangents()` für Tests |
| `sim/CameraOcclusion#fisheyePercent(…, Stärke)`, `content/client/WorkbenchReport`, `drone/client/DroneServiceScreen` | sim, content, drone | Props im Kamerabild für das eigene Objektiv bei der eingestellten Stärke (Zwischenspeicher je Stärke); `BuildAnalysis`, Warnung und API bleiben flach 16:9 |
| `video/client/HeadlightOverlay` | video | Radius aus `VideoState#displayFov` |
| Selbsttests | video | `video.client.FisheyeSelfTests` (`objektiv:`), Rahmen-Schritt „dev: Objektiv flach (eigene Schritte schalten Fisheye ein)“, `ActionCamSelfTests` (Fisheye-Ausschnitt), `IrisShowcase` (zwei Bilder mit Fisheye) |

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
| `server/client/WorldRulesTab` | server | Tab „Welt-Regeln“ (Id `world_rules`, Order 800, `Storage.MIXED`, Fußzeile `propwash.server.world_rules.footer`, schreibgeschützt `…footer_read_only` über `ConfigTab#readOnlyKey`), oben „Voreinstellungen“ (drei Knöpfe, gelten sofort über `WorldRulePresetC2S` und verwerfen Entwürfe der Katalog-Regeln; leuchten bei passenden angezeigten Werten, darunter „Entspricht …“ bzw. „Eigene Regeln …“; ohne Recht nur die Zeile), dann Abschnitte je Gruppe, „Weitere Regeln“ für Gamerules ohne Katalog-Eintrag; Schalter, Stufen, Regler (Prozent/Zahl bis 1000 Schritte), sonst Zahlenfeld; Entwurf bis „Speichern“ (dann `WorldRuleEditC2S`), Rechtsklick = Standard, „Standard“ setzt alle Regeln zurück; ohne Recht eine Tabelle mit Wert und Tooltip. Tooltip: Name, Beschreibung, Standard und Bereich, Befehl. Seit 0.6.0 je Zeile ein Häkchen „Zonen“ (`zone.client.ZoneReleaseBox`, Entwurf `releaseDraft`, §15 „Zonen für alle“), keine Zonen-Liste mehr (eigener Tab „Zonen“, `zone.client.ZonesTab`, Befund OWNP-2), der Abschnitt „Rennstandard“ (§15 „Rennwertung“) und ein Abschnitt je Add-on (§15 „Welt-Regeln der Add-ons“) |
| `server/client/RuleRowLayout` | server (Fix F-cov-combo-4; JUnit `server.client.RuleRowLayoutTest`, Selbsttest „addon-api-17: B5 – Tab Welt-Regeln bei GUI 320×240 deutsch …“) | Breiten der Zeilen im Tab „Welt-Regeln“, reine Rechnung mit ganzen Zahlen: `column` ist die gemeinsame Namensspalte (Formel wie bisher, höchstens 5/8 der Breite); `labelWidth` und `spinnerWidth` geben einer Zeile, deren Name in der Spalte selbst gestaucht noch gekürzt würde (`TextFit.Mode.CUT`), mehr Platz, das Bedienelement behält dabei mindestens seinen Bedarf (`toggleNeed`, `cycleNeed`, `sliderNeed`, `spinnerNeed`; Zahlenfeld sonst `SPINNER_WIDTH` 90); ohne Recht schieben `valueColumn` und `valueX` den Wert dieser Zeile nach rechts; passende Zeilen bleiben unverändert. Aufgerufen von `WorldRulesTab#labelWidth`, `#widget`, `#valueColumn` und `#extract` |
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
| `interact/DroneBlockTriggers` | interact | Server, 0.6.0 (Portierung von Pull Request #1 von bluepanee): Druckplatten und Stolperdraht reagieren auf Drohnen. `tick(drone, level)` aus `QuadcopterEntity#tickServer` nach `syncFlightTags` (vor `SERVER_TICK`), nur im ersten Tick und bei Bewegung über `MOVE_THRESHOLD` = 1·10⁻⁴ Block (`moved(vorher, jetzt)`, letzte Position in einer `WeakHashMap`, nur Server-Thread); je Blockposition im Kollisionsquader + `REACH` (1·10⁻³), deren Zustand im Block-Tag `DRONE_TRIGGERS` = `propwash:drone_triggers` liegt (`#minecraft:pressure_plates`, `minecraft:tripwire`; Datenpakete erweitern oder leeren ihn) und deren `getEntityInsideCollisionShape` den Quader berührt: `state.entityInside(level, pos, drone, InsideBlockEffectApplier.NOOP, true)`. Keine übrigen Blockwirkungen (Portale, Kaktus, Feuer, Lava, Spinnweben, Blasensäulen), kein `fireImmune`, keine Welt-Regel, kein Netzpaket, keine Speicherdaten; Selbsttests `interact.client.DroneTriggerSelfTests` (`interact:`), Beitrittstest `dev.PlateJoinCheck` (`PROPWASH_JOINTEST_PLATES`), JUnit `interact.DroneBlockTriggersTest`; Spieler- und Betreiber-Doku `docs/drone-triggers.md` |

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
- Lage (0.6.0, `docs/bando-placement.md`): `bando.plan.BandoSite.underpinned` entscheidet, welche Spalten
  `BandoPiece#underpin` bis zum Grund auffüllt (auch Hofspalten mit eigenem Fundament ohne stehendes Teil);
  `BandoPiece#tidy` entfernt nach den Merkmalen jedes Chunks im Bereich `BandoSite.leafArea` (Plan samt 4 Blöcken Rand)
  Laub ohne Stamm (`LeafDistance.orphans`; Stämme des Bandos zählen nicht, künftige Bauteil-Zellen leiten nicht) und
  schreibt nur in diesen Chunk; `settleLeaves` gibt abgeschnittenen Blättern jenseits des Chunk-Rands nur einen Block-Tick.
  Wirkt nur in neu erzeugten Chunks; Blätter, die ein Baum aus einem später erzeugten Nachbar-Chunk in einen fertigen
  Chunk schreibt, können weiter hängen bleiben.
- Selbsttest: Schritte `bando: …` in der Testwelt und nach dem Verlassen in zwei eigenen Welten
  (`SelfTestRunner.WORLD_NAME + " Bandos"` bzw. `… Bandos aus`, Normal-Welt, Seed 20261003); sie stehen als letzte
  Nach-dem-Verlassen-Schritte und verlassen ihre Welten selbst wieder.

### GPS Rescue ohne Pilot und bessere Hinderniserkennung (rescue, drone, server; Entwurf `docs/design/autonomous-rescue.md`, Propwash 0.5.0)
rescue läuft damit auf beiden Seiten: Der Autopilot fliegt am Client des Piloten und, wenn der Pilot im Heimflug aussteigt,
auf dem Server weiter.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `rescue/RescueAutopilot` | rescue | zusätzlich `setAutonomous(bool)` (ohne Pilot: immer landen, nach dem Aufgeben an Ort und Stelle landen), `landNow(world)`, `stuck()` (mit Pilot aufgegeben, schwebt), `gaveUp()`, `following()`, `landX()`/`landZ()`; Sicherheitshöhe aus dem ganzen Weg (bis 256 m), lokaler Weg über `RoutePlanner`, Landeplatz über `RescueWorld#ground`; Zielpunkt-Landung über `targetFits` (Drohnenbreite + 0,05 Rand, echte Oberseiten über `RescueWorld#groundTop`, ohne Höhenkarte, Oberseiten ≤ 0,25 auseinander, am Homepoint höchstens 1 Block darüber) vor der Platzsuche in `chooseLandingSpot` und `chooseLandingNear` (Fix F-GCCA12P-1) |
| `rescue/RescueWorld#ground(x, y, z)`, `rescue/Ground` | rescue | Art des Blocks (`NONE`, `SOLID`, `FOLIAGE`, `WATER`, `LAVA`, `HAZARD`, `UNKNOWN`); Standard aus `solid`; `LevelRescueWorld` und `ServerRescueWorld` ordnen über `rescue/RescueBlocks#classify` ein; `groundTop(x, y, z)` (Standard 1.0) liefert die echte Oberseite des Blocks, beide Welten über `RescueBlocks.top` (volle ebene Oberseite aus der Drohnen-Form, auch in Teilhöhe, sonst `NaN`; Fix F-GCCA12P-1) |
| `rescue/RoutePlanner` | rescue | reines Java: begrenztes A* (±20/−6/+12 Blöcke, 2500 Knoten) mit `Goal` (`estimate`, `reached`), Wegpunkte mit Sichtlinien geglättet |
| `rescue/ReturnPlan` | rescue | Plan an der Drohne (`QuadcopterEntity#returnPlan`, NBT `ReturnPlan`): Besitzer, Homepoint, beim Piloten, Höhe, Tempo, Sinkrate, Hinderniserkennung, Abstand, Mindestentfernung, sofort landen, Ticks; `settings()` bereinigt und landet immer |
| `rescue/net/RescueStatusC2S` | rescue | `propwash:rescue_status` vom Piloten: aktiv oder nicht, Homepoint, beim Piloten, Einstellungen; der Server nimmt es nur vom Piloten der Drohne, Homepoint nur innerhalb seiner Funkreichweite (+32) und außerhalb eines für ihn gesperrten Spawn-Bereichs. `rescue.client.RescueController` schickt es bei Start, Ende, Einstellungen und Homepoint-Bewegung (> 0,5 m, höchstens alle 250 ms), nie beim Ende der Sitzung |
| `rescue/AutonomousReturns` | rescue | Server: übernimmt bei `PILOT_CHANGED` (Pilot → keiner) mit aktiver Meldung, Akku und Welt-Regel; fliegt im Tick der Drohne (`QuadcopterEntity#setServerAutopilot`), hält dabei die Chunks über `rescue/FlightChunks` (Chunk der Drohne und erzeugte Nachbarn, 3×3 tickend, siehe dort); Budget 8 je Server, 2 je Spieler (`AutonomyBudget`, sonst senkrechtes Absinken ohne Ticket), 12 000 Ticks, dann Landung, nach 1200 weiteren aus; Chat-Meldungen an den Besitzer; `flights()`, `descents()`, `flying(drohne)`, `phase(drohne)`, `reported(drohne)` für Tests |
| `drone/QuadcopterEntity` | drone | `returnPlan()`, `setReturnPlan`, `returningHome()`, `RETURN_PLAN_KEY`; `tickServer` fragt den Hook nur mit Plan und ohne Pilot und überspringt dann die lose Physik; ein neuer Pilot löscht den Plan |
| `drone/DronePiloting` | drone | `StartResult.RETURNING`: Funke benutzen bei Plan → `ReturnPlan#withLandNow`, Meldung `propwash.rescue.autonomous.land_now`; Shift + Funke zeigt `propwash.rescue.autonomous.status` |
| `drone/WindModel` | drone | von `drone.client` nach `drone` (öffentlich), Client und Server rechnen denselben Wind |
| `server/RangeManager#generatedAround`, `#keepSimulated`, `#keepAhead(level, mitte, radius)`; `server/ServerRules#forbiddenSpawnArea(spieler, level, pos)` | server | Helfer für autonome Flüge (gemeinsamer `TicketLedger`, Vorausschau-Ticket `propwash:drone_lookahead` für Add-on-Flüge, die Gelände erzeugen dürfen, Spawn-Schutz für eine Position) |
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

### Spinnweben, Fäden, Ranken und Glocken (interact, sim, rescue; Entwurf `docs/design/drone-snags.md`, Propwash 0.5.2)

| Datei | Besitzer | Vertrag |
|---|---|---|
| `sim/SnagField`, `SimEnvironment.snag` | sim | Umgebung „Verfangen“: je Punkt (vier Props in Sim-Reihenfolge, `CENTER` = Rahmenmitte) `linear` (1/s) und `quadratic` (1/m), dazu `hold` (m/s²) und je Prop `propLoad`; `active()`, `clear()`, `copyTo`; das Flugmodell begrenzt die Werte (`MAX_*`), NaN → 0 |
| `sim/SnagDrag`, `DroneSim`, `PowerSystem.snagLoad` | sim | Widerstand an den Punkten mit Drehmoment, Haltekraft als Coulomb-Reibung am Schwerpunkt, je Schritt höchstens halbe Geschwindigkeit und halber Drehimpuls; Props mit `1 + propLoad` mehr Luft-Drehmoment (auch im 3D-Modus); leeres Feld → kein Rechenschritt anders (Goldens, Kalibrierung) |
| `interact/SnagMaterial`, `SnagMath`, `SnagSampler`, `SnagWear` | interact | reines Java: Werkstoffe `WEB`/`STRING`/`FOLIAGE` (Werte in `docs/design/drone-snags.md` §2), Abtastung der fünf Punkte als Prop-Scheiben und Rahmen-Würfel entlang des Wegs seit dem letzten Abschnitt (Vereinigung der Abschnitte, Sprünge > 4 m zählen nicht, höchstens 64 Zellen und 96 Boxen), Welt über `SnagSampler.World`, Verschleiß je Prop (Berührung × Rate × Drehzahl-Anteil × Zeit) und Server-Grenze `wearAllowance` |
| `interact/SnagBlocks` | interact | Block-Tags `propwash:drone_snag_web`, `propwash:drone_snag_string`, `propwash:drone_snag_foliage` (`data/propwash/tags/block`), `material(state)`, Umrissformen nur in geladenen Chunks, Stolperdraht als 1/16 breite Linie entlang der verbundenen Richtung(en) in den unteren 1,5 Pixeln der Form, `anyNear` |
| `interact/client/PilotSnags` | interact | aus `PilotInteraction#beforeStep` vor jedem Rechenabschnitt von `drone.client.PilotSession` (Feld füllen, Verschleiß sammeln, Festhängen erkennen); `afterFrame` schickt höchstens 1× je Sekunde `SnagWearC2S` (nur mit Welt-Regel Crash-Schaden) und zeigt einmal den Toast `propwash.interact.snag.caught` |
| `interact/client/SnagEffects` | interact | an `QuadcopterEntity.CLIENT_TICK` jeder scharfen Drohne (Motor ≥ 5 %, bis 48 Blöcke um die Kamera): Bröckchen des berührten Blocks und alle 6 Ticks sein Trefferklang; kein Netzpaket |
| `interact/DroneSnags` | interact | Server: `SnagWearC2S` (Pilot, Epoche, Crash-Schaden-Stärke, Block aus den Tags höchstens 2 Blöcke entfernt, höchstens 3 % je Sekunde × 1,5, ≤ 10 % je Paket); `DroneSnags.Flight` für `rescue.ServerReturnFlight` (Abtasten je Abschnitt, Verschleiß direkt 1× je Sekunde) |
| `interact/BellRules`, `DroneBells`, `interact/client/PilotBells` | interact | Glocken: Client erkennt nach jeder Bewegung den Stopp an der Kollisionsform eines `BellBlock` mit ≥ 1 m/s (aus `PilotInteraction#clip`/`#afterMove`), je Glocke höchstens alle 5 Ticks; Server prüft Pilot, Epoche, Glocke, `Level#mayInteract`, 5 Ticks je Glocke, 8 je Sekunde, Reichweite wie `StrikeRules`, dann `BellBlock#onHit(…, Pilot, true)`; Event `DroneBells.RUNG` (Server-Thread) |
| Netz | interact | `SnagWearC2S` (`propwash:drone_snag_wear`), `DroneBellC2S` (`propwash:drone_bell`); `Constants.PROTOCOL_VERSION` = 16 |
| Selbsttests | interact | `interact.client.SnagSelfTests` (beide Loader, Präfixe `verfangen:` und `glocke:`); Bildschirmfotos `snag_cobweb.png`, `snag_vines.png`, `drone_bell.png`, `drone_bell_raider.png` |

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

### Homepoint im NBT der Drohne (rescue, drone; Entwurf `docs/design/home-point.md`, Propwash 0.5.2)
Der Client des Piloten meldet jeden gesetzten Homepoint, der Server speichert ihn an der Drohne (`HomePoint`) und schickt
ihn beim Flugstart und nach `/data`-Änderungen an den Piloten. Die Regeln, wann GPS Rescue einen Homepoint setzt, bleiben.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `rescue/HomePoint` | rescue (reines Java) | Record `x`, `y`, `z`, `dimension` (Id als Text, leer = noch offen), `gameTime` (−1 = offen), `Source` (`ARM`, `MANUAL`, `PILOT`, `COMMAND`; `id()` klein geschrieben, Reihenfolge = Netzformat); `samePlace`, `stamped`, `resolved(dimension, jetzt)` |
| `rescue/HomePointRules` | rescue (reines Java, JUnit `HomePointRulesTest`) | `check(x, y, z, dimension, dimensionDerDrohne, minY, maxY, inGrenze)` → `Problem` (`INVALID`, `DIMENSION`, `HEIGHT` ±64 Blöcke, `BORDER`) oder `null`; `edit(alt, neu, …, jetzt)`: gleicher Ort → alter Punkt unverändert (Zeit und Herkunft nur lesbar), sonst geprüft und mit `COMMAND` und `jetzt` gestempelt |
| `rescue/HomeSync` | rescue (reines Java, JUnit `HomeSyncTest`) | Zähler der Meldungen am Client: `push()` zählt hoch, `beginSession()` setzt die Sitzung zurück, `accepts(quittung)` nur bei Quittung ≥ letzter Meldung der Sitzung; `onStart(eigenerHier, gespeicherterHier, gleich)` → `PUSH_OWN`/`ADOPT_STORED`/`KEEP` |
| `rescue/HomePointCodecs` | rescue (JUnit `HomePointCodecsTest`) | `KEY` = `HomePoint`; `CODEC` mit `x`, `y`, `z` (Pflicht, auch ganze Zahlen), `dimension`, `game_time`, `source` (optional, fehlende Herkunft = `command`); `dimension(Level)` |
| `rescue/HomePoints` | rescue (Server) | registriert die Pakete und hört auf `PILOT_CHANGED` (neuer Pilot → `HomePointS2C`, `edited` = false) und `QuadcopterEntity.HOME_EDITED` (→ `edited` = true); `onSet` nimmt nur vom Piloten der Drohne, nur `fromPilot()`-Herkünfte und nur geprüfte Punkte, stempelt Dimension und Spielzeit; `prepareEdit(drohne, tag)` prüft `/data` vor dem Laden und wirft `CommandSyntaxException` (`propwash.rescue.home_point.*`); sendet nur an Verbindungen, die Nachrichten annehmen |
| `rescue/net/HomePointC2S`, `HomePointS2C` | rescue | `propwash:home_point_set` (Entity, Zähler, x/y/z, Herkunft) und `propwash:home_point` (Entity, Quittung, `edited`, Punkt oder keiner) |
| `rescue/client/RescueController` | rescue (Client) | meldet beim Setzen per Scharfschalten oder Taste/Schalter, im Modus „Beim Piloten“ beim Scharfschalten (`pilot`) und den eigenen Homepoint beim Flugstart; `onServerHome` übernimmt für die laufende Sitzung (bei `edited` mit Hinweis `propwash.rescue.home_changed`) und hebt bis zu 16 Nachrichten zu noch unbekannten Drohnen bis zum Sitzungsbeginn auf |
| `rescue/RescueSupervisor#armedNow` | rescue | Flanke des Scharfschaltens in jedem Modus (JUnit `RescueSupervisorTest`) |
| `drone/QuadcopterEntity` | drone | `homePoint()`, `setHomePoint(punkt[, quittung])`, `homeAck()` (beim Pilotenwechsel 0); NBT `HomePoint` (fehlende Dimension/Zeit beim Laden ergänzt); `postDataManipulated` feuert `HOME_EDITED`, wenn `/data` den Punkt geändert hat |
| `mixin/drone/EntityDataAccessorMixin` | drone | `EntityDataAccessor#setData` (HEAD): bei Drohnen `HomePoints.prepareEdit` |
| Netz | rescue | `HomePointC2S`, `HomePointS2C`; `Constants.PROTOCOL_VERSION` = 16 (0.5.2) |
| Tests | rescue | Server-Rauchtest `rescue.HomePointChecks` (beide Loader); Selbsttest `rescue.client.HomePointSelfTests` (Schritte `rescue: Homepoint im NBT – …`), mit `-PpropwashScreenshots=true` `home_point_command.png` |

### Wasserdicht und Turtle-Modus (sim, drone, content, input, osd, ui, guide; Entwurf `docs/design/waterproof-turtle.md`, Propwash 0.5.2)
Ohne Beschichtung und ohne Turtle-Modus rechnet das Flugmodell bit-gleich wie zuvor (Goldens, Kalibrierung).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `sim/DroneSim#setWaterproof`, `#waterproof`, `#turtleArmed` | sim | Beschichtung je Sitzung (Entity oder Welt-Regel); `SimEnvironment.waterSurface` = Höhe der Wasseroberfläche über dem Schwerpunkt (−∞ ohne Wasser); `PilotInput.turtle` = Turtle-Schalter; `Telemetry.turtle`, `waterproof`, `submerged`; `ArmBlocker.TURTLE_AIRBORNE`, `DisarmReason.TURTLE_OFF` (angehängt) |
| `sim/TurtleMix`, `sim/HullContact` | sim | Mischer und Schutz gegen Überschlagen; Hüllpunkte als Federkontakt am Boden nur, solange der Turtle-Modus scharf ist |
| `drone/WaterSurface` | drone | `relative(level, x, y, z)`; von `PilotSession` und `rescue.ServerReturnFlight` benutzt |
| `drone/coating/*` | drone | `CoatingRules` (reines Java: Haltbarkeit, Signatur der Elektronik), `CoatingComponents.COATING` (`propwash:waterproof_coating`), `WaterproofSprayItem` (`DroneModule.WATERPROOF_SPRAY`), `WaterproofCoatingRecipe` (`propwash:waterproof_coating`), `DroneCoating.coat`; `CoatingModule.init()` aus `DroneModule.init()` |
| `drone/QuadcopterEntity#waterproof`, `#setWaterproof` | drone | synchronisiert, gespeichert als `Waterproof`; Item ↔ Entity über `CoatingComponents.coated/coat` |
| `content/DroneAssembly` | content | `disassemble` legt die Beschichtung an das Stack-Teil, `createDrone` übernimmt sie nur bei gleicher Signatur |
| `input/SwitchFunction.TURTLE`, `input/TurtleSelector`, `KeyDefaults.TURTLE` | input | Schalter oder Taste (Ziffernblock 8); Taste geht nach einem Turtle-Flip beim Entschärfen von selbst aus |
| `server/rules/WorldRuleSpecs.WATERPROOF_DRONES` | server | `propwash:waterproof_drones` (Schalter, Standard aus, Gruppe Spiel, keine Physik), Arcade an |
| `server/rules/WorldRuleSpecs.ALLOW_RESET` | server | `propwash:allow_reset` (Schalter, Standard an, Gruppe Spiel, keine Physik), in allen Voreinstellungen an |
| `rescue/HomeResets`, `rescue/HomeResetRules` | rescue | Reset zum Homepoint: `HomeResetC2S` vom Piloten, Regeln rein in `HomeResetRules` (Ziel Homepoint → Startpunkt, nie über Dimensionen, nur in Funkreichweite, außerhalb des Spawn-Schutzes und ohne Erzeuge-Recht nur in erzeugtem Gelände, Sperrzeit 20 Ticks, gerade Lage mit Kurs; Stelle `spot(x, y, z, Space)`: freier Homepoint → genau dort, sonst nächste freie Stelle im Umkreis 3 (`SEARCH_RADIUS`), höchstens 1 Block höher (`RISE`) oder tiefer (`STEP_DOWN`), nie durch Boden oder Decke, `Spot#nearby` → `Outcome.NEARBY` mit Hinweis „Homepoint blockiert“, keine → zugebaut; `ground` = Homepoint auf der Oberkante unter der Drohne, `lift` hebt einen Punkt im Block bis 1 Block an), Ziel-Chunk mit `DRONE_TICKET` laden und erst entity-tickend versetzen (höchstens 200 Ticks), `QuadcopterEntity.HOME_RESET` vor dem Versetzen, `resetTo(…, home = true)`; der Pilot verfolgt seine Drohne danach 100 Ticks auch ohne gesendeten Chunk (`TrackedEntityMixin`) |
| `rescue/HomeSpace` | rescue | intern, keine API: `HomeResetRules.Space` mit der Kollisionsbox der Drohne (`noBlockCollision`, `Shapes.collide`, Weltgrenze, optional erlaubte Positionen); bei Blöcken im Tag `propwash:drone_surface` zählt wie in der Flugphysik die sichtbare Form (`SoftGround.collect`) beim freien Platz und beim Fallen; Konstruktor nimmt `Level`; nutzen Reset am Server (`HomeResets`) und Homepoint setzen am Client (`RescueController`) |
| `drone/client/FpvView#coverInsideBlock` | drone | aus `LevelExtractorMixin` nach `clearPilotScreenEffects`: steckt Kamera oder Drohne in einem sichtversperrenden Block, zeigt das FPV-Bild die Vanilla-Block-Überlagerung; bei `CameraClip` nicht |
| Netz | – | `Constants.PROTOCOL_VERSION` = 16 |

### Knüppel-Mode 1–4 (input, osd, guide, ui, drone-render; Propwash 0.5.2)
Persönliche Einstellung des Clients, nicht synchronisiert, kein Einfluss auf Flugmodell, Kanäle einer Funke oder Netz
(Protokoll bleibt 16). Mode 2 ist Standard und verhält sich wie vor 0.5.2.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `input/StickMode` (reines Java, JUnit `StickModeTest`) | input | Tabelle Mode → Seite je Kanal: Mode 1 links Nick/Gier, rechts Gas/Roll; Mode 2 links Gas/Gier, rechts Nick/Roll; Mode 3 links Nick/Roll, rechts Gas/Gier; Mode 4 links Gas/Roll, rechts Nick/Gier. `place(roll, pitch, yaw, gas)` legt Kanalwerte auf linken/rechten Knüppel, `fromMode2` setzt eine in Mode-2-Bedeutung geschriebene Stellung um (Animationen, Flugschule) |
| `input/InputConfig#stickMode`, `#changeStickMode` | input | Gespeichert in der Eingabe-Config (Enum-Name, kaputte Werte → Mode 2); ein Wechsel legt Gamepad-Profile mit unveränderter Standardbelegung auf die neuen Knüppel (Kalibrierung bleibt), eigene Belegungen bleiben und werden als `custom` gemeldet |
| `input/ProfileDefaults#gamepad(name, mode)`, `#remapPadSticks`, `#padSticksMatch` | input | Gamepad-Standardbelegung je Mode; Funken bleiben AETR in jedem Mode (die Funke stellt ihren Mode selbst ein) |
| `input/client/StickModes` | input | aktueller Mode (`InputConfig.get()`), Textvarianten `<schlüssel>.mode1|3|4`, Ansagen `<promptKey>.left|right` |
| `input/client/screen/ReceiverTab`, `StickModeDiagram` | ui | Zeile „Knüppel-Mode“ mit kleinem Zwei-Knüppel-Bild, in der Suche; Wechsel gilt nach dem Speichern sofort |
| `input/client/screen/ReceiverWizardScreen` (`Page.MODE`) | ui | erste Seite des Assistenten: vier Karten (Klick, Tasten 1–4, Pfeile), Mode 2 vorgewählt; Ansagen und animierte Funke folgen dem Mode; bei eigener Gamepad-Belegung bietet der Knopf „Sticks wie Mode n“ (englisch „Use Mode n sticks“) das Umlegen an; „1. Mode“ in der Schrittleiste führt zurück |
| `osd/client/OsdRenderer#stickDots` | osd | Stick-Overlay im Mode des Spielers (auch DVR-Aufnahme und OSD-Editor) |
| `drone/client/render/RemoteGimbalRenderer.Pose` | drone-render | Funke in der eigenen Hand, Assistent, Maus-Vorschau und Flugschule zeichnen die Knüppel im Mode |
| `guide/scene/Anchor#onStick`, `SceneEvaluator.evaluate(scene, t, mode)` | guide | Hinweise auf einem Knüppel wechseln mit dem Kanal die Funkenseite (JUnit `StickModeScenesTest`) |

Selbsttest-Gruppe `stickmode:` (`input.client.StickModeSelfTests`, `osd.client.StickOverlaySelfTests`): Assistent in allen
vier Modes (Ansage, hervorgehobener und bewegter Knüppel), Empfänger-Tab samt Suche und Speichern, Stick-Overlay in allen
vier Modes, Gas-Hinweis der Flugschule. Bildschirmfotos `receiver_wizard_stick_mode*`, `osd_stick_overlay_mode1…4`,
`configurator_receiver_stick_mode1`, `guide_configurator_sticks_mode1|3`.

### Konventionen (docs/CONVENTIONS.md)
- **Kein Kommentar im Code** (auch keine Javadoc). Verträge stehen in diesem Dokument.
- Mixins nur unter `de.lardtom.propwash.mixin.<modul>`, Namen `<Ziel>Mixin`/`<Ziel>Accessor`.

### Add-on-API 1.6: Halterung vorne, Zubehör-Verhalten, Aktionen, Physik, Flughilfen, Gimbal, HUD (accessory, sim, input, drone, osd, content; Vertrag `docs/design/addon-api-1.6.md`)
- **Halterung vorne**: `AccessorySlot.FRONT`/`AccessoryMount.FRONT` (Schlüssel `front`, ohne Sockel, Quader unter der
  Nase nach vorn: `AccessoryType#boxMinMm` `(−b/2, −h, −t)`, `boxMaxMm` `(b/2, 0, 0)`), Anbaupunkte in den Rahmen-JSONs,
  `drone_layout.json` und `tools/assets/parts/layout.py` (Cinewhoop (0; −3; −50), Freestyle (0; 0; −72), Race (0; 0; −66),
  Long Range (0; 0; −76)). `BuildSlots.COUNT` = 15 (Zubehör-Slots 12–14 in Halterungsreihenfolge), Werkbank-Slot 14 bei
  (30; 64), Symbol `gui/slot/accessory_front`. `PartCategory.ACCESSORY#slotCount()` = 3.
- **Inhalt**: `QuadcopterEntity#accessoryContents()`/`setAccessoryContents` (Entity-Daten mit Serializer
  `propwash:accessory_contents`, gespeichert unter `AccessoryContents`, Item-Komponente `propwash:accessory_contents` in
  `toStack`/`loadFromStack`); `DroneWorkbenchMenu#disassembleSalvage` gibt den Inhalt ins Spieler-Inventar.
  Flugmodell-Bauplan mit Beladung: `accessory.AccessoryPayload.flightBuild(drone)` (Pilot über
  `PilotInteraction.flightBuild`, Heimflug in `ServerReturnFlight.start`; `PilotInteraction.sameAirframe` vergleicht
  ohne jede Beladung).
- **Hooks** (neu, additiv): `QuadcopterEntity.SERVER_TICK` (Ende von `tickServer`), `QuadcopterEntity.setDamageFilter`
  (vor jedem Schaden in `hurtServer`, Rückgabe < 0 bricht ab), `DroneServiceMenu.ACCESSORY_BUTTON` (3000) öffnet
  `AccessoryModule.openMenu`, `OsdHud.setReplacement`, `DronePushes.setLocalApplier`, `AutonomousReturns.sim(drone)`,
  `FpvView#cameraPose` ruft `AddonCamera.apply` (Pilot und Zuschauer), `PilotSession#onFrame` ruft
  `AddonFlightClient.beforeFrame` nach `fillInput`, `end` ruft `AddonFlightClient.end`.
- **Flugmodell**: `DroneSim#setAssist`, `#setForceProviders`, `#applyImpulse`, `#push`, `#setObstacleDistance`,
  `#assistLabel`, `#consumeReturnHomeRequest`, `#consumeExtensionFailure`; `PilotInput#actions` (Bits 0–3),
  `Telemetry#assistLabel`, `#externalPushes`; `NavHold#update(…, AssistBridge guide)`. Ohne Erweiterung bitgleich.
- **Eingaben**: `SwitchFunction.ACCESSORY_1…4` (Taster, vor `COMMAND_1`; `accessoryAction()`, `accessoryNumber()`,
  `accessory(n)`), `KeyDefaults.ACCESSORY_1…4` (Ziffernblock 9, 0, Enter, +) in `OTHER_MODULES`, Zeilen im Modi-Tab.
- **OSD**: `OsdData#assistLabel` ersetzt in `OsdRenderer#modeText` die Flugmodus-Anzeige (nach Rescue).
- **Netz**: `Constants.PROTOCOL_VERSION` = 16; C2S `propwash:accessory_action`, S2C `propwash:drone_push`; Menü
  `propwash:drone_accessories`.
- **Selbsttest**: Gruppe `addon-api:` (`interact.client.AddonApiSelfTests`), Beispiel `dev.example.ExampleAccessories`.

### Namensraum `propwash` und Prüfung beim Start (guard, apiimpl, platform; 0.6.0, Spieler-Baustein `docs/namespace-guard.md`, Entwurf `docs/design/0.6.0/addon-api-namespace.md`)
- **Reserviert:** Der Namensraum `propwash` und das Paket `de.lardtom.propwash` gehören allein Propwash (NOTICE,
  `ADDONS.md`). `ADDONS.md` ist eine Pflichtdatei des öffentlichen Exports und steht in `LICENSE-MIT`; die englische
  Fassung dort ist maßgeblich für den Website-Abschnitt `#rules`.
- **`apiimpl.NamespaceRules`** (rein, ohne Minecraft-Abhängigkeit): `isReserved` gilt nur für genau `propwash`;
  `requireForeign` wirft an neuen Punkten der API 1.7 und an Punkten, die schon in 1.6 warfen; `refuse` verweigert an
  toleranten Punkten ohne Ausnahme und schreibt einen Log-Fehler; `callerMod` nimmt den ersten Stack-Rahmen außerhalb von
  `de.lardtom.propwash` und fragt den eingesetzten Resolver.
- **`platform.services.ModOrigins`** (eigene Service-Dateien je Loader): Zuordnung zum Mod für Klasse, Paket,
  Mixin-Config und aktiven Mod; Dateien eines Mods; Netzwerk-Kanäle mit dem Mod ihres Empfängers; Abschnitt im
  Absturzbericht. Liefert „leer“ statt einer Ausnahme; Plattform-Mods wie `minecraft`, `java`, `fabric-*` und `neoforge`
  sind keine Anmelder.
- **Prüfung** (`guard`, beim Start und beim Neuladen der Ressourcen): Erlaubt ist ein Pfad oder Schlüssel, den Propwash
  selbst mitbringt, also Überschreiben und Tag-Ergänzung. Neues im Namensraum `propwash` aus einem fremden Mod ist ein
  Fund. Jede Kategorie läuft in try/catch; nie ein Absturz, eine Log-Zeile je Fund einmal je Start, ein Chat-Hinweis
  einmal je Spielstart beim ersten Betreten einer Welt (Knopf „Details“), am dedizierten Server eine Zeile je Serverstart
  für Operatoren.
- **Zuordnung eigener Aufrufe:** Anmeldungen, die Propwash bei seinem eigenen Code ablehnt, sind kein Fund. Code ohne
  bekannten Mod und Mixin-Code eines anderen Mods in einer Minecraft-Klasse zählen als „unknown mod“. Die Regel hängt an
  der API-Struktur: Ein künftiger API-Einstieg außerhalb von `api.**` ohne Methode eines `api.`-Typs würde eigene Aufrufe
  als Fund melden (fällt im Schritt `guard:` sofort auf). Propwash-Code, der Interna wie `NamespaceRules` direkt mit
  `propwash`-Ids aufruft, und Ablehnungen auf Datenwegen ohne API (etwa `PartIndex.validateId`) gelten bewusst als
  „unknown mod“.
- **Spielerpakete:** Neue Dateien im Namensraum aus Ressourcen- und Datenpaketen (Zip oder Ordner) stehen als Info-Zeile
  im Log, nie als Fund (`guard.FileScan#playerPacks`, Ordnerliste `guard.ResourcePaths.ASSET_FOLDERS`/`DATA_FOLDERS`;
  neue Standard-Ordner einer späteren Minecraft-Version gehören dort hinein).
- **Absturzbericht:** Abschnitt „Propwash add-ons“ mit Add-ons, Mixins in Interna und Funden.
- **Pflichten für neuen Code:** Eigene Netzwerk-Pakete werden über `PropwashNet` oder einen Empfänger aus einer
  Propwash-Klasse angemeldet, eigene Tasten über `ClientRegistrar.keyMapping`, Unterbefehle von `/propwash` in
  Propwash-Klassen. Sonst meldet die Prüfung einen Fehlalarm, und die Gruppe `guard:` schlägt ohne Probe fehl.
- Tests: Gruppe `guard:` (`guard.client.GuardSelfTests`), Probe-Mod `nsprobe` (§9), Beitrittstests `addon17` und
  `addon17-noaddon` (`docs/testing.md`). Just More Parts unter 1.2.0 sperren die Loader-Metadaten (§15 „Honig und Just
  More Parts ab 1.2.0“).

### Sharecodes PW3 und Web-Export (share, webexport, content; 0.6.0, Bausteine `docs/sharecode-ui.md`, `docs/sharecode-chat.md`, `docs/web-export.md`, Formate `docs/sharecode/*.md`, Entwurf `docs/design/0.6.0/sharecodes-pw3.md`)
- **Codec in der Basis** (`share`): `ShareFormat.decode` liest PW1, PW2 und PW3 mit den Tabellen aus
  `ShareTables.current()`. Geladen werden sie bei `REGISTRY_FROZEN` aus `data/<mod-id>/propwash_share/table.json` der
  Jars plus dem Verzeichnis-Abzug `data/propwash/propwash_share/directory.json`. `Pw3Codec` schreibt nur PW3;
  `ShareConvert.toPw3` rechnet nur mit eingefrorenen Daten um und ist dadurch bytegleich zur Website. Verträge: Tabellen
  werden nur hinten erweitert; 1 = `propwash` und 2 = `justmoreparts` sind fest; Lack, Tuning, OSD und Name sind
  bitgleich zu PW2; die Prüfsumme sind die unteren 10 Bit der CRC-32 über die benutzten Einträge; ein Leser wirft nie
  eine Ausnahme. API `PropwashApi.shareCodes()`, Prüfwerkzeug `api.share.ShareTableCheck`. Ein Teil aus einer neueren
  Version behält beim Weitergeben den ursprünglichen Code (`carry`).
- **Oberfläche** (`share.client`: `ShareClient`, `ShareScreen`, `ShareConfigTab`, `ShareImportState`, `ShareLines`,
  `ShoppingList`, `ShareSettings`, `ShareWorkbenchExtension`, `ShareScene`; Server `share.ShareCommands`,
  `ShareServer`): Die Oberfläche liest und schreibt nur über `ShareService`. Übernehmen läuft über die vorhandenen
  Import-Wege, der Server entscheidet. Links sind nur zu Modrinth, CurseForge und dem Verzeichnis erlaubt, immer mit
  Bestätigung. Tab `propwash:sharecode`, Kapitel `sharing`, `/propwash code export|import|info|give|convert` (`give` ab
  Rechtestufe 2).
- **`share/ShareServer#importData(content, layers, target)`, `#usable(build)`:** Ebenen ohne PARTS tragen den Bauplan des
  Ziels, wenn der Bauplan des Codes in dieser Sitzung unbekannte oder inaktive Teile hat; sonst lehnt
  `BuildAccessImpl.applyImport` bzw. `ClientApiImpl.send` mit `UNKNOWN_PARTS` ab. Genutzt von
  `ShareConfigTab.sendTune/sendPaint`, `ShareScreen.apply` und `applyToHeld`.
- **`share/Substitutes#find`** (intern, Ersatzteile beim Import mit unbekannten Teilen): Rahmen, Stack, Motor, Prop,
  Video und Akku bekommen ihren `fallback`, sonst das erste baubare Propwash-Preset. Unbekanntes Zubehör bekommt nur
  seinen `fallback`, wenn der an derselben Halterung baubar ist, sonst wird es entfernt; Zubehör kommt nie aus einem
  Preset. Fehlt nur Zubehör, ist die Methode `FALLBACK`.
- **Werkbank-Kopf** (`content/client/WorkbenchHost#addTextOrIconButton`, intern): Knopf mit Text, Kurzform und
  Ausweich-Symbol (`Entry#widths/iconAt/textAt/titleAt`). `HeaderLayout` wählt das Symbol wie eine weitere Kurzform beim
  breitesten Knopf, bevor etwas hinter „⋯“ wandert, und springt beim Kürzen zur nächsten echt schmaleren Form. Genutzt
  von `ShareWorkbenchExtension` („Sharecode“ → „Code“ → </>).
- **Sharecodes im Chat** (`share.client.chat`): Die Erkennung läuft nur beim Spieler und hängt sich mit
  `mixin.share.client.GuiMessageMixin` in das Umbrechen der Chatzeilen (`GuiMessage#splitLines`); gespeicherte Nachricht,
  Signatur, Kennzeichen und Meldeprotokoll bleiben unverändert. `ChatCodeFinder` erkennt PW1/PW2/PW3 nur mit gültiger
  Prüfsumme (höchstens 3 Codes und 8 Kandidaten je Zeile, 4000 Zeichen). `ChatCodeText` löst Formatcodes auf und färbt
  nur die Code-Abschnitte neu. `ShareChat` dekodiert jeden Code einmal über `ShareImportState` (Zwischenspeicher 32 Codes,
  8 neue je Sekunde). Das Klick-Ereignis `propwash:share_chat/open|copy` fängt `ChatScreenMixin` ab, es geht nie an den
  Server. Den Tooltip mit Drohnenbild (`ChatPreviewImage`, über dem Zeiger) zeichnet `GuiGraphicsExtractorMixin`; der Klick
  öffnet den Tab `propwash:sharecode`. Option `config/propwash/share_chat.json`, geschaltet im Tab Konfiguration.
  Keine Pakete, keine API-Änderung.
- **Web-Export** (`webexport`): `WebExportModule` meldet über `ServerBus.REGISTER_COMMANDS`
  `/propwash webexport [<namensraum>|all]` an (Stufe 4). `WebExportCommand` ruft `WebExport.run(ziel, auswahl,
  ModResources.platform())` und schreibt nach `<spielordner>/propwash/web-export/` (vorher geleert). Format 2 je
  Namensraum: `base/` mit Regeln, Lack, Tuning-Gruppen und Kennzahl-Beschriftungen; je Namensraum Manifest, Teile,
  Halterungen, Zellchemien, Presets, Analysen, Teile-Tabelle, Sprachschlüssel und Assets. `PartData` (Felder je
  Kategorie, `data.led_path`), `RuleCheck` (Formeln aus `BuildCheck`, 5000 Zufallsbaupläne mit Startwert 1),
  `AssetIndex` (benutzte Modelle, Texturen und Lack-Kanäle), `LangTable` (benutzte Schlüssel), `ModResources`
  (Plattform, im Test Ordner). `ShareExport` ergänzt über den Codec der Basis `base/sharecode/spec.json`
  (`ShareSpecJson.spec()` mit den eingefrorenen PW1/PW2-Werten), `vectors.json` (mit `check` und `analysis`),
  Preset-`sharecode`, `pw1_keys`, Gruppen `shared`/`never`, Lack-`bit` sowie `table_source`, `table_entries` und
  `table_crc` je Namensraum. Gradle `:fabric:runWebExport` (§13).
- Tests: JUnit `Pw3CodecTest`, `WebExportTest`, `Format1CompareTest` (rechnet alte Codes mit `ShareConvert.toPw3` um und
  verlangt Byte-Gleichheit), `CovPw3ShareTest`; Selbsttest-Gruppe `share:`, `ShareUiScaleCases`; Beitrittstests
  `PROPWASH_JOINTEST_SHARE` und `PROPWASH_JOINTEST_SHARE_CHAT`; Server-Rauchtest `ShareServerChecks`.

### RX-Block: ein Kanal der Funke wird Redstone (rx, guide, content; Entwurf `docs/design/rx-block.md`, Propwash 0.6.0)
Neues Modul `rx` (`rx.RxModule` am Ende von `PropwashCommon.init()`, `rx.client.RxClient` nach `JoinClient` in
`PropwashClient.init()`, Sprachquellen `common/src/lang/rx/`, Mixin-Config `propwash.rx.mixins.json`, Selbsttests
`rx.client.RxSelfTests` nach den Bando-Selbsttests, Bildschirmfotos `rx.client.RxShowcase`).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `rx/RxSource`, `RxMask` | rx (reines Java, JUnit `RxSourceTest`, `RxPayloadTest`) | `STICK` 0–3 (Roll, Nick, Gier, Gas), `AXIS` 0–31, `BUTTON` 0–31; Codec `{kind, index}`, falsche Indizes sind ein Codec-Fehler; Masken Knüppel 4 Bit, Achsen und Tasten je 32 Bit |
| `rx/RxMapping` | rx (reines Java, `RxMappingTest`) | `strength(v, tief, hoch, bisher)` mit t = (v − tief)/(hoch − tief) in 0–1: bis zur Mitte runden(16 · t), darüber runden(8 + 14 · (t − 0,5)); die Mitte ist fest Stufe 8 (`CENTRE`, mit den Standard-Endpunkten Knüppel −0,0625 bis +0,071, das Kanal-Totband liegt darin), 0–7 und 9–15 in gleich großen Stufen, ohne Hysterese (dieselbe Stellung ergibt aus jeder Richtung dieselbe Stufe); tief > hoch umgekehrt, tief = hoch Schalter, nicht endliche Werte halten `bisher` |
| `rx/RxLink` | rx (reines Java, `RxLinkTest`) | effektive Entfernung = Auge–Blockmitte + 0,5 je undurchsichtigem Vollblock (höchstens 64, ohne Augen- und Zielzelle), Reichweite 1–128 (Standard 64), über der Reichweite keine Verfolgung; `Cells.loaded(chunkX, chunkZ)` vor jedem `opaque`, nicht geladene Abschnitte ohne Dämpfung |
| `rx/RxLearn` | rx (reines Java, `RxLearnTest`) | Fenster 1,5 s, Mindestweg 30 %, Knüppel vor Achse oder Taste ab 80 % von deren Weg, Abbruch nach 12 s |
| `rx/RxSettings` | rx (`RxSettingsTest`) | NBT `Format` 1, `Bind` (`drone.bind.BindCodecs`), `Source`, `Low`, `High`, `Range`; kaputte Felder werden ersetzt; neue Quelle setzt die Standard-Endpunkte (Knüppel/Achse −1/1, Taste 0/1); Lösen behält die Reichweite |
| `rx/RxBlock`, `RxBlockEntity`, `RxLinkState`, `RxModule` | rx | Block `propwash:rx_block` mit `power` (0–15), `link` (`none`, `linked`, `lost`), `facing` (waagrecht) und `output` (`rx/RxOutput`); Grundzustand `output=all`, `facing=north` (Blöcke aus Ständen vor 0.6.0 ohne diese Eigenschaften laden darüber und senden weiter schwach an alle Seiten), gesetzt wird immer `output=front` mit dem Ausgang in Blickrichtung (`getStateForPlacement`, für Code `RxBlock.facing(state, dir)`): schwaches und starkes Signal nur nach vorne wie beim Repeater, Block vorne und seine Nachbarn bekommen jede Änderung gemeldet, Redstone-Staub verbindet nur vorne (`shouldRedstoneWireConnectTo`), `rotate`/`mirror`; 384 Zustände; kein Ticker; Rechtsklick mit der Funke (Haupthand) bindet am Server, öffnet am Client das Fenster; leere Hand zeigt den Zustand; Update-Paket mit `OwnerName` |
| `rx/RxOutput` | rx (`RxOutputTest`) | `FRONT` (nur zur Seite von `facing`, `signal` und `direct` gleich `power`, Staub verbindet nur dort) und `ALL` (Altbestand: schwach an alle Seiten, kein starkes Signal, Staub verbindet rundum); kein API-Typ |
| `rx/RxIndex` | rx | je `Level`, nach Position und Besitzer; Eintrag `clearRemoved`, Austrag `setRemoved`, Besitzerwechsel über `RxBlockEntity#apply` |
| `rx/RxServer` | rx | `active(level, pos)` = `shouldTickBlocksAt(pos)` und `isPositionTicking`; nur dort `getBlockState`/`setBlock`, nie Tickets; Auswertung alle 2 Ticks, Link alle 10, Bedarf alle 20 (Reichweite + 16); Werte gelten 100 Ticks; `input`/`config` liefern `Outcome` (für Rauchtest und Beitrittstest) |
| `rx/RxDemandS2C`, `RxInputC2S`, `RxConfigC2S` | rx | `propwash:rx_demand`, `propwash:rx_input` (Folgenummer, Maske, `short`-Werte, Tasten-Bits; ≤ 25/s, Maske ⊆ Bedarf), `propwash:rx_config` (`BIND`, `UNBIND`, `SOURCE`, `LOW`, `HIGH`, `RANGE`; Abstand ≤ Interaktionsreichweite + 1, Besitzer oder Operator Stufe 2) |
| `rx/client/RxClient`, `RxRadio` | rx | Senden im Client-Tick bei Bedarf, ab 1/512 Weg Änderung oder alle 40 Ticks; Gas als 2 · t − 1; Tastatur nur in einer Flug-Sitzung (`PilotSession#isActive`) |
| `rx/client/RxScreen` | rx | Fenster im Propwash-Theme bis 320 × 240, `preview(View, confirm)` für `ui-scale:` und Bildschirmfotos |
| `rx/RxBlockItem`, `rx/RxText` | rx (`RxTooltipTest`, Selbsttest `CovRxSelfTests#tooltipStep`) | Tooltip: `propwash.rx.tooltip` direkt unter dem Namen, umbrochen von `RxText.tooltipLines` (möglichst wenige, möglichst gleich lange Zeilen bis 48 Zeichen; erste Zeile über `propwash.rx.tooltip.line`), mit Shift Erklärung, Benutzung und Passt zu aus `propwash.help.rx_block.*` |
| `mixin/rx/client/MultiPlayerGameModeMixin` | rx | Kopf von `useItemOn`: Schleichen + Funke auf einem RX-Block öffnet das Fenster mit Bestätigung (eigener Block), bindet per `BIND` (ungebunden) oder meldet den Besitzer (fremd); sonst Vanilla |
| `guide/scenes/RxBlockScene` | rx | Kapitel `rx_block` (fünf Schritte), angemeldet von `RxClient` über `GuideScenes.register` |
| Assets `rx_block` (Generator `tools/assets/world/rx_block.py`) | rx (JUnit `RxBlockModelTest`, Selbsttest `rx.client.RxModelSelfTests`, Präfix `rx: Blockmodell`; Befund OWNP-4) | Aussehen nur über Assets, kein Renderer, keine Einfärbung im Code: Blockzustand als Multipart, Gehäuse nach `link` (`rx_block`, `rx_block_linked`, `rx_block_lost`), dazu nach `power` je Stufe `block/rx_block_power_<n>` mit eigener Textur `block/rx_block_power_<n>` und `light_emission` n (Balken mit 15 Feldern oben zwischen LED und Antennen); Item über `block/rx_block_inventory` (Gehäuse plus Balken bei Stärke 0); Ressourcenpakete gestalten jede Stufe einzeln |
| `content/ContentTab` | content | Eintrag `RxModule.ID` hinter dem Signal-Repeater |
| Netz | – | `Constants.PROTOCOL_VERSION` = 17 (Basis-Commit 0.6.0, vom Strang nicht geändert) |
| `rx/CreativeRxBlock`, `RxModule.CREATIVE_RX_BLOCK` (Befund PATTY-2) | rx (JUnit `CreativeRxRangeTest`, `CreativeRxDataTest`, Selbsttest `rx.client.CreativeRxSelfTests` Präfix `rx: Kreativ`, Server-Rauchtest `rx.CreativeRxServerChecks`) | Kreativ-RX-Block `propwash:creative_rx_block`: Unterklasse von `RxBlock` mit `unlimited()` = true (`RxBlock.unlimited(state)`), dieselbe Block-Entity `RX_BLOCK_BE`, Bindung, Kanalwahl und Ausgang; Härte −1, Sprengfestigkeit 3 600 000, keine Beute, kein Rezept (`tools/data/survival.py` `NOT_CRAFTABLE`), Seltenheit episch, Kreativ-Tab direkt nach `RxModule.ID`; Reichweite: `RxLink.linked(unlimited, …)` ohne Entfernung und Blockzählung, `RxLink.demands(unlimited, …)` immer Bedarf, beides nur für Besitzer in derselben Dimension (Verzeichnis je Welt, `evaluate` überspringt andere Welten) und nur in geladenen, tickenden Chunks; Status und `RxScreen` zeigen `propwash.rx.range.unlimited` statt des Reglers (`RxScreen.preview(view, confirm, unlimited)`); Assets erben von den RX-Modellen mit eigenen Gehäuse-Texturen |

### Gate-Hinweis und Kapitel „Gate-Menü“ (race, guide; 0.6.0, Entwurf `docs/design/0.6.0/gameplay.md` Teil E)
Spieler-Baustein `docs/gate-hint.md`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/client/GateHintMemory` | race (reines Java, JUnit `GateHintMemoryTest`) | `check` → `SHOW`/`SEEN_HERE`/`NEVER`/`NO_PLAYER`; gesehene Welten je Spieler (höchstens 256, die älteste fällt heraus; 32 Spieler); „Nicht wieder anzeigen“ = Schlüssel `gate_menu` in `FirstUseHints` (Zähler ≥ 1), gesetzt mit Haken oder „Kapitel öffnen“; `forgetHints` leert alle Hinweise eines Spielers |
| `race/client/GatePlaceHint` | race | nur am Client, kein Eingriff in `GateItem`, kein Paket: ein Benutzen-Klick (neuer Arm-Schwung oder gedrückte Benutzen-Taste) mit Gate- oder Freiform-Gate-Item (keine Kurs-Elemente) merkt die Gate-Ids im Umkreis 48; in den nächsten 30 Ticks zählt ein neues Gate der Art `GATE` oder `FREEFORM` bis 8 Blöcke vom Auge als gesetzt; Welt-Schlüssel `GhostStore.worldKey` (`sp_<Ordner>`, `mp_<Adresse>`, sonst `unknown`); gesehene Welten in `config/propwash/gate_hint.json`; bei offenem Bildschirm wartet das Fenster bis 200 Ticks; `resetHints()`; Test-Haken `mute`, `worldOverride`, `detected`, `lastOutcome` |
| `race/client/GateHintScreen` | race | Fenster im Theme, höchstens 300 GUI-Pixel breit, Tasten der echten Belegung von Schleichen und Benutzen, Haken „Nicht wieder anzeigen“, „Kapitel in der Flugschule öffnen“ und „Verstanden“; Esc = „Verstanden“; pausiert nur im Einzelspieler |
| `guide/scenes/GateMenuScene` (`gate_menu`) | guide | vier Schritte `open`, `adjust`, `redstone`, `freeform`; angemeldet von `GatePlaceHint.init` über `GuideScenes.register` (wie RX-Block, Bando und Kreativ), steht deshalb nicht in `GuideScenes.bootstrap` |
| `guide/client/screen/GuideTab` | guide | Knopf „Hinweise zurücksetzen“ neben „Flugtraining“ (bei wenig Platz rechts unter der Einleitung) ruft `GatePlaceHint.resetHints()` und meldet das Ergebnis in der Fußzeile |

Selbsttests `race.client.GateHintSelfTests` (Präfix `race: Gate-Hinweis`, Bilder `bildschirmfotos: Gate-Hinweis`,
`ui-scale:`-Fall „Gate-Hinweis“), Beitrittstest `PROPWASH_JOINTEST_GATEHINT` (`dev.GateHintJoinCheck`,
`race.client.GateHintJoinProbe`), Sprachschlüssel `common/src/lang/gatehint`.

### Freecam-Drohne (freecam, drone, video, ui, osd, input, server; 0.6.0, Entwurf `docs/design/0.6.0/gameplay.md` Teil B)
Neues Modul `freecam` (reines Java in `freecam`, Client in `freecam.client`; `FreecamClient.init()` am Ende von
`PropwashClient.init()` vor `DevExtensions.client()`), Spieler-Baustein `docs/freecam.md`, Sprachquellen
`common/src/lang/freecam/`. Kein Paket, kein Entity, keine API (E35).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `freecam/FreecamPreset` | freecam (reines Java, JUnit `FreecamPresetTest`) | `WHOOP`, `THREE_FIVE`, `FIVE`, `SEVEN` mit Schlüsseln `whoop`, `3_5`, `5`, `7` (`byKey` fällt auf `FIVE` zurück); Whoop/5″/7″ = `DroneBuild.WHOOP`/`FREESTYLE`/`LONG_RANGE`; 3,5″ = `CREATIVE_FC` mit `CreativeSpec` auf Rahmen `freestyle_5`, Stack `aio_20`, Motor `m2004_3000`, Prop `p3_3b`, Akku `b4s_650` (89 mm, 3 Blätter, 3,0″, 2450 KV, 20 g, Radstand 150 mm); Bänder Schwebegas/Spitze je Größe im Test |
| `freecam/FreecamBounds` | freecam (reines Java, `FreecamBoundsTest`) | `step(von, nach)` achsweise X, Y, Z (Z in der Spalte nach dem X-Schritt); Spalte frei, wenn `Columns.loaded(chunkX, chunkZ)`; Höhe von Weltboden − 16 bis Bauhöhe + 64; außerhalb des geladenen Bereichs nur Schritte Richtung Anker (Spieler); nicht endliche Ziele gesperrt |
| `freecam/FreecamRules` | freecam (reines Java, `FreecamRulesTest`) | `start` → `Denial` (`NO_WORLD`, `RULES_UNKNOWN`, `RULE_OFF`, `DEAD`, `PILOTING`, `SPECTATING`, `SCREEN_OPEN`); `check` → `End` (`DISCONNECT`, `DIED`, `DIMENSION`, `RULE_OFF`, `HURT`, `PILOTING`, `SPECTATING`; dazu `KEY`, `BUTTON` von außen); ohne synchronisierte Regeln nie erlaubt (E8) |
| `freecam/client/FreecamSession` | freecam | `active()` (statisch, für `CameraClip`), `toggle`, `tryStart`, `end(mc, End)`, `setPreset` (Zustand inkl. Drehraten und Scharf über `SimSnapshot`), `applyTune`, `cameraPose` (ohne Latenz); `DroneSim` mit `infiniteBattery`, ohne `batterySag`, `crashDisarm`, `propStrikes`, `thermalLimits`, `thermalEffects`, `sleepEnabled`, `partCompatibility`; Umgebung `WindModel.fill`, `solidDistance` = ∞, Bodenabstand per Strahl (im Block ∞), `fluid` NONE, `ZonePhysics.update`; unscharf = Pose eingefroren (Kurs bleibt, Lage waagerecht); Frame-Haken ruht bei Pause und offenem Bildschirm; Ende bei Pilot-Sitzung, Zuschauen, Dimension, Regel aus und Trennung schon im Frame; Test-Haken `setTestInput`, `testPlace`, `testPush` |
| `freecam/client/FreecamClient` | freecam | Taste `key.propwash.freecam` (`KeyDefaults.FREECAM`, F6, englisch „Toggle freecam drone“), `CameraClip.register(FreecamSession::active)`, `ConfigTabs.registerFreecamOnly(FreecamTab::new)` (der Tab zählt nicht zu den Tabs des normalen Konfigurators), Tick und Frame über `ClientBus`, Hinweis `FirstUseHints.FREECAM_INTRO` einmal je Spieler |
| `freecam/client/FreecamConfig` | freecam | `JsonConfig` `freecam`: `preset`, `tunes` (je Größe, nur bekannte Tuning-Schlüssel, ohne Kamerawinkel), `uptilt` (je Größe, 0–80°), `fovDeg` (60–160, Standard 120), `osd` (Standard an) |
| `freecam/client/FreecamTab` | freecam | Tab `freecam` (`ConfigTabs.FREECAM_TAB`, Reihenfolge 50, `Storage.MIXED`): Größe (vier Chips), Kamerawinkel, Sichtfeld, OSD, „Freecam beenden“; Größenwechsel speichert zuerst das offene Tuning |
| `drone/client/FpvView`, `PilotView` | drone (Haken freecam) | `active()` schließt die Freecam ein; `cameraPose` liefert zuerst die Freecam (Körper der Drohne unbekannt, Anker = Freecam) |
| `video/client/VideoState` | video (Haken freecam, ein Block in `fov`) | in der Freecam horizontales Sichtfeld aus `FreecamConfig`, Objektiv und Bildformat wie im FPV; keine Videoquelle → keine Störungen |
| `ui/client/DroneContext`, `drone/client/TuneAccess` | ui, drone (Haken freecam) | Quelle `FREECAM` (Bauplan der Größe, Name „Freecam · 5″“, Sim der Freecam); `TuneAccess` liest und schreibt `FreecamConfig` und die laufende Sim, sendet nichts |
| `ui/client/config/ConfigTab`, `ConfigTabs`, `ConfiguratorScreen`, `UiClient` | ui (Haken freecam) | `ConfigTab#freecam()` (Standard falsch; wahr bei Freecam, Rates, Empfänger, Modi); `ConfigTabs.create(true)` liefert die nur für die Freecam angemeldeten Tabs zusammen mit den Tabs, deren `freecam()` wahr ist, `create()` keinen davon; in der Freecam öffnet K den Tab `freecam`, sonst fehlt er; Fußzeile der Übersicht mit „Freecam starten“ (ausgegraut, Grund als Status) und „Zur Welt-Regel“ (nur wenn `ClientWorldRules#editable`), die `WorldRulesTab#focusRule` aufruft; endet die Freecam bei offenem Menü, schließt es ohne das Tuning anzuwenden. Bei schmaler GUI reserviert nur die Übersicht Platz für die Freecam-Knöpfe (`footerArea()`, `footerStacked()`). `ConfiguratorScreen.revealSearchHit` holt das Ziel eines Suchtreffers vor dem Tab-Wechsel; der Wechsel von oder zur Übersicht ordnet das Fenster neu, dabei entsteht ein neues Suchfeld ohne Index, der dann frisch gebaut wird |
| `osd/client/LiveOsdSource` | osd (Haken freecam) | Quelle Freecam: Sim-Daten, Name, Position, Tempo ohne Latenz; `OsdData#freecam` blendet Akku- und Link-Elemente aus, `OsdData#freecamEdge` → Warnung `FREECAM_EDGE`; `OsdHud#hideVanilla` gilt bei `FpvView#active` |
| `input/client/KeyDefaults` | input (Eintrag freecam) | `FREECAM` in `OTHER_MODULES`, F6, in `SHARED` mit `key.debug.debugOptions` (Vanilla F3 + F6) |
| `input/client/TransmitterInput` | input (Haken freecam) | Flug-Sitzung = Pilot-Sitzung oder Freecam (Tastatur- und Mausknüppel) |
| `server/rules/WorldRuleSpecs` | server (Zeile freecam) | `ALLOW_FREECAM` = `bool("allow_freecam", false, GAMEPLAY)` |
| `network/client/ClientNet` | network (Testzähler) | `sentCount()` zählt jeden `sendToServer`-Aufruf |

Selbsttests `freecam.client.FreecamSelfTests` (Präfix `freecam:`, `ui-scale:`-Fall „Freecam-K-Menü“), Bilder
`freecam.client.FreecamShowcase` (`bildschirmfotos: Freecam`), Beitrittstest `PROPWASH_JOINTEST_FREECAM`
(`dev.FreecamJoinCheck`, `freecam.client.FreecamJoinProbe`). Gate-Durchflüge der Freecam zählt die Abdeckung
(`freecam.client.CovGatePasses`, devtest) auf dem ganzen Weg, auch auf dem Stück, das die Simulation die Freecam
zwischen zwei Ticks vom gesetzten Punkt wegschiebt; halboffen, je Gate und Richtung einmal (JUnit `CovGatePassesTest`).

### Flugbahn-Marker (osd, video; 0.6.0, Spieler-Baustein `docs/flight-path-marker.md`, Entwurf `docs/design/0.6.0/gameplay.md` Teil A)
`osd.FlightPathMarker` ist reines Java: `compute(vx, vy, vz, OsdProjection, Rand) → State{visible, alpha, x, y, inView,
edgeAngle}`; Einblendung 1,0–2,0 m/s, außerhalb des Bilds auf der Rand-Ellipse (`OsdProjection#screenAngle`).
`Velocity.update(nanos, Δpos, span)` bildet die Ableitung mit PT1 40 ms; über 150 m/s oder nach mehr als 0,5 s Pause
beginnt sie neu. Die Geschwindigkeit stammt immer aus den **gezeigten** Posen: `VideoState#sampleDisplayedAt(versatz, …)`
relativ zu `lastSampleNanos()`, derselbe Puffer wie das Bild. `LiveOsdSource` füllt `OsdData#velocityX/Y/Z` (NaN =
unbekannt) im FPV und beim Zuschauen; die Freecam setzt die Felder direkt aus der Sim. Element
`OsdElement.FLIGHT_PATH_MARKER` (fest, Linien-Deckkraft, nur in FULL an), gezeichnet in `OsdRenderer#flightPathMarker`,
bei Videoverlust aus; im OSD-Editor steht die Vorschau ruhig unter der Mitte, der Tooltip der Elementliste zeigt
`propwash.osd.element.<schlüssel>.description`. Selbsttests lesen `OsdHud.drewLastFrame` und
`renderer().flightPathState()`. Kein Netzwerk, kein neues Speicherformat, Layout-Codec unverändert (ein Schlüssel mehr).

### Kamerawinkel im K-Menü (video, drone; 0.6.0, Spieler-Baustein `docs/camera-angle.md`, Entwurf `docs/design/0.6.0/gameplay.md` Teil D)

| Datei | Besitzer | Vertrag |
|---|---|---|
| `video/client/CameraAngleSection` | video | Abschnitt „Kamera dieser Drohne“: bearbeitet `camera.uptiltDeg` im `TuneModel` des Konfigurators. Bereich aus `CameraMount.range(build)`, Regler `StepSlider` mit `−`/`+` (Shift 5°) und `↺` (`TuneModel#reset`). Ohne Live-Modell ausgegrautes `InfoField` „—“ mit Hinweis. `editable()`, `slider()`, `row()`, `change(grad)`, `resetUptilt()`. |
| `video/client/VideoConfigTab` | video | Abschnitt nur bei einem Host, der `TuneHost` ist (Konfigurator, Suchindex). Mit Brille unter Sichtfeld- und Bildformat-Zeile, ohne Brille unter dem Hinweis. `storage()` = MIXED, solange bearbeitbar (`propwash.video.camera_section.footer`). `resetDefaults` setzt auch den Winkel zurück. |
| Netz | drone | kein neues Paket: `TuneAccess.apply` → `TuneC2S` (`propwash:drone_tune`) → `DronePiloting#sanitize` begrenzt auf den Halter. Protokoll unverändert. Im Einzelspieler gilt der Winkel beim Schließen des Menüs (Pause), auf Servern sofort. |
| Selbsttests | video | `video: Kamerawinkel im K-Menü` (vier Schritte, `video.client.CameraAngleMenuSelfTests`), ui-scale-Fälle „Konfigurator Kamerawinkel …“, Bildschirmfotos `configurator_video_camera_angle{,_none,_no_goggles}(_de)`. Pilot-Beitrittstest: `camangle=40`, `camangle_live=menu_open` (`video.client.CameraAngleJoinProbe`, `dev.CameraAngleJoinCheck`). |

### Akku gesteckt (drone-render; 0.6.0, Spieler-Baustein `docs/battery-plug.md`, Entwurf `docs/design/0.6.0/fixes-polish.md` §3.B)
Sitzt der Akku, zeichnet Propwash keine Stecker und keine Kabel. `drone.client.render.PlugAnchors` liest
die Kästen von `connector`, `connector_grip`, `bt_connector`, `balance_plug`, jedem `wire`, das eines davon berührt,
und den beiden `pigtail` der Stack-Variante `_mounted`. `PlugMask` und `MaskedItemCollector` lassen beim Zeichnen jede
Fläche weg, die ganz in einem dieser Kästen liegt; gemessen wird im Modellraum vor der `transformation` des Render-Items,
deshalb gilt das auch für skalierte Rahmen. `PowerLead` hält den `_mounted`-Stack und beide Masken, `DroneVisuals` und
`guide.client.render.SceneDroneVisual` reichen sie weiter. Das bisherige Bild bleibt ohne `_mounted`, bei `_mounted` aus
einem anderen Ressourcenpaket, ohne Buchse und ohne Akku. `PowerLeadRoute` und `power_lead.bend_mm` gibt es nicht mehr;
ein vorhandener `power_lead`-Eintrag in `drone_layout.json` wird ignoriert. Rein clientseitig, keine Daten, kein
Protokoll, keine API-Änderung. Prüfung: `PlugAnchorsTest`, Selbsttest „drone-render: Akku gesteckt – Stecker und Kabel an
der Drohne ausgeblendet“. Regeln in `docs/ASSETS.md` „Akku gesteckt“, Tests in `docs/testing.md` „Akku gesteckt“.

### Motor-Hitze (sim, osd, content; 0.6.0, Vertrag `docs/design/realism/motor-heat.md`)
- Phase A (Prüfung): `sim.ThermalCharacterizationTest` schreibt mit `tools/calibration.sh` die Tabelle `thermal.txt` und
  hält die Urteile der Zielbänder B1–B4 und Regel 2 fest; Selbsttest-Gruppe `drone: Motor-Hitze`
  (`drone.client.MotorHeatSelfTests`, Bildschirmfotos `motor_heat_*`).
- Phase B (Umbau): gedrosselt wird nur im ESC, ein heißer Motor verliert Drehmoment und verschleißt; Verträge in der
  Parts-API-Tabelle oben (`sim/Telemetry#motorPowerLimit`, `osd/OsdWarnings.Warning.MOTOR_HOT`, `ESC_HOT`). Die Felder
  `thermal.derate_start_c` und `max_temp_c` behalten Namen und Werte, ihre Bedeutung steht in `docs/api/changes.md`; ein
  gesetztes `cooling_w_per_k_per_rad_s` ersetzt weiter den ganzen drehzahlabhängigen Teil. Die Welt-Regel
  `propwash:hardware_limits` schaltet beides ab.

### Drohnen-Barrieren (barrier; 0.6.0, Baustein `docs/barriers.md`)
`barrier.DroneBarrierBlock` erweitert die Vanilla-`BarrierBlock`; Kollision, Umriss und Sichtbarkeit entscheidet der Block
über den `CollisionContext`:
- **Kollision:** `BarrierKind.solidFor(Collider)`; Drohne heißt `QuadcopterEntity` oder Unterklasse. Kontexte ohne Entity
  sehen die durchlässige Barriere (`propwash:drone_pass_barrier`) als fest und die Sperre (`propwash:drone_block_barrier`)
  als Luft.
- **Umriss:** für jeden Spieler im Kreativmodus (`DroneBarriers.outlines(CollisionContext)`), wie die Vanilla-Barriere,
  auch ohne Barriere in der Hand; im Überleben und ohne Entity leer; **sichtbare Form** immer leer.
- **Drohnen-Abfragen ohne Entity** benutzen `DroneBarriers.droneShape(...)` bzw. den Kontext `DroneBarriers.DRONE_SIGHT`.
- **Kursbau** (Befund PATTY-2): Gate-, Gate-Stangen-, Kurs-Element- und Freiform-Platzierung (`race.GateItem#click`,
  `#soft`, `#free`, `race.FreeformBlocks#passable`) fragen mit dem Kontext `DroneBarriers.COURSE` (`Collider.COURSE`:
  beide Barrieren fest) und `DroneBarriers.sturdyForCourse(...)` (Barriere zählt als tragende Fläche); die Kollision
  im Flug bleibt unverändert. Selbsttest `barriers: Gates`.
- **Symbole:** nur im Kreativmodus mit einer Barriere in Haupt- oder Nebenhand (`DroneBarriers.reveals(Player)`);
  `barrier.client.BarrierMarkers` setzt sie im Client-Tick nach dem Vanilla-Verfahren (`BLOCK_MARKER`).
- Offen: GPS-Himmelssicht (`SkyView`), `ClearanceProbe`, `FpvShake` und Trail-Höhe fragen ohne Entity (durchlässige
  Barriere dort fest, Sperre Luft). Tests in `docs/testing.md` „Drohnen-Barrieren“.

### Drohnen-Attribute und Skalierung (drone, race; 0.6.0, Baustein `docs/drone-scale.md`)
`QuadcopterEntity` hat eine eigene `AttributeMap` (`drone.DroneAttributes`, nur `minecraft:scale`, 0,0625–16), die
Vanilla-`/attribute` über `mixin.drone.AttributeCommandMixin` erreicht. Der Server spiegelt den Wert in `DATA_SCALE`.
`drone.DroneScale` ist die einzige Rechenstelle: Hitbox mal k, Darstellung und Kamera um den Fußpunkt. Das Flugmodell
sieht die Skalierung nie; Ankerpunkte des Zubehörs und die Wegplanung von GPS Rescue rechnen bewusst mit Größe 1.
Ranglisten-Grund `race.LapAssist.SCALE` (Bit 8, Server-Grund). Tests in `docs/testing.md` „Drohnen skalieren“.

### Zonen für alle (zone, server, ui; 0.6.0, Baustein `docs/zones-open.md`, Grundlage `docs/design/zones.md`)
- `zone.ZoneOwner` und `Zone.owner`: `null` heißt Admin-Zone (alle Zonen aus älteren Welten, „Neue Zone hier“ eines
  Operators, `/propwash zone create`); „Neue Zone hier“ eines Spielers gehört dem Spieler.
- `zone.ZoneRights`: `mayCreate` (Admin oder Welt-Regel `propwash:players_create_zones`), `mayChange` und `mayDelete`
  (Admin oder Besitzer bei Regel an), `ownerOfNew` und `ownerOfChanged`. „Admin“ heißt Recht auf die Welt-Regeln
  (`WorldRules#mayEdit`).
- `zone.ZoneRelease`: Freigabe je Regel-Id, Standard sind die elf `PhysicsRules.SPECS`; `effective`, `enforce`,
  `blocked`; gespeichert in `ZoneData.release`.
- `ZoneServer.zones(level)` und `resolve` rechnen mit den wirksamen Zonen, `stored(level)` liefert die gespeicherten.
  `ZonesS2C` trägt `creatable` und die Freigaben; Paket `propwash:zone_release`.
- `zone.client.ClientZones`: `zones()` liefert die wirksamen, `stored()` und `get(id)` die gespeicherten Zonen; dazu
  `mayChange` und `mayDelete`. `zone.ZoneDraft.Kind.LOCKED` für gesperrte Werte im Editor.
- `zone.client.ZoneMarkerClient` hängt am Haken vor den Tastenbelegungen (`mixin.zone.client.MinecraftMixin`),
  `zone.client.ZoneRenderer` zeichnet den Vorschau-Rahmen; Item `propwash:zone_marker`.
- `zone.client.ZonePhysics`: Zonen-Auflösung am Client, `at(Level, x, y, z)` (`ZoneResolution` ohne Übergang) und
  `update(ZoneField, Level, x, y, z, s)` (mit Übergang, genutzt von `PilotSession` und der Freecam).
- `WorldRulesTab`: `ZoneReleaseBox` je Zeile, `releaseDraft`. Bedienbar nur bei den elf Simulationsregeln, die am Ort
  der Drohne gelesen werden; Add-on-Regeln bräuchten dafür etwa `WorldRule#get(Level, Vec3)`.
- Tab „Zonen“ (Befund OWNP-2): `zone.ZoneMenu` rechnet ohne Spielzustand – `listed` (Spieler ihre eigenen Zonen,
  Operatoren alle), `actions` (`SHOW`, `JUMP`, `EDIT`, `DELETE` je nach Recht), `jumpTarget` (erster Boden in der Mitte
  der Zone von unten, sonst darunter bis zum Weltboden; ohne Boden `null`, Befund AUTO18-1), `arrow`
  (Richtungspfeil zur Blickrichtung), `TELEPORT` (Pfad `teleport location` im Befehlsbaum).
- Paket `propwash:zone_jump` (`zone.ZoneJumpC2S`); `ZoneServer#mayJump` (Recht auf `/tp` über `RuleAccess.runs`) und
  `#jump` (prüft Recht, Sichtbarkeit und Dimension, setzt auf `jumpTarget`; ohne Boden Ablehnung mit `JUMP_NO_GROUND`).
- `zone.client.ZonesTab` (Id `zones`, Order 790, `open(focus)` mit hervorgehobener Zone) mit `zone.client.ZoneSection`:
  „Neue Zone hier“ rechts in der Zeile der Überschrift, Liste mit `Entry` (`height`, `owner`, Knöpfe „Zeigen“,
  „Hinspringen“, „Bearbeiten“, „Löschen“ mit zweitem Klick in `DELETE_CONFIRM_NANOS`); bei Platzmangel kompakt (Name
  allein in Zeile 1; `ZoneSection.compact`/`nameRoom`, Schwelle `NAME_MIN_WIDTH` 96; Prüfung „ui-scale: Zonenname
  ganz“, Befund `TRUNCATED`, `docs/zone-row.md`); Suche je Zone „Zone „…“ bearbeiten“. `zone.client.ZoneFocus`:
  Hervorhebung nach „Zeigen“ für 12 s, auch über 160 Blöcke hinaus, Hinweis mit Name, Entfernung und Pfeil.
- `ClientZones`: `listed`, `actions`, `mayJump` (nicht während einer Pilot-Sitzung). Zonen-Markierer: Schleichen +
  Rechtsklick ohne Ecken öffnet `ZonesTab.open(ZoneMarkerClient.zoneHere)`.
- Befund PATTY-1 (`docs/zones-open.md` §9): Regel-Zustand `zone.ZoneRuleMode` (`BELOW` wie darunter, `WORLD` Weltwert
  erzwingen, `OWN` eigener Wert); `Zone` mit Feld `world` (Codec `force_world`, nur geschrieben, wenn nicht leer; im
  Netz nach dem Besitzer), `mode`, `sets`, `forcesWorld`, `withWorld`, `withMode`, `sameRules`. `ZoneResolver` nimmt je
  Regel die höchste Zone, die sie setzt; ein erzwungener Weltwert liefert den Wert der Welt-Regel, `ZoneResolution#source`
  bleibt dann `null` (Flughilfe, Rennwertung und `RaceRules` sehen die Welt), `forcedBy`/`decider` nennen die Zone;
  `ZoneResolver#below` rechnet die Zonen unter einer Zone (Editor „Darunter: …“). `ZoneBlend` gleitet auf den Weltwert
  und zählt ihn als Welt-Anteil. `ZoneRelease` sperrt und übernimmt auch erzwungene Weltwerte. `ZoneText#notice` baut
  den Hinweis beim Einfliegen aus der Auflösung (Werte je Zone, „(Welt)“). `ZoneEditPanel` scrollt per Mausrad über dem
  Menü (`scroll`, Scrollbalken `ThemeDraw.scrollbar`, ziehen mit `startDrag`/`drag`), folgt der Auswahl nur bei einem
  Wechsel. `zone.ZoneView` (`outlines`, `styles`, `highlight`) und `zone.client.ZoneViewConfig` (`config/propwash/zones.json`,
  `show` `TOOL`/`TOOL_OR_FLIGHT`/`ALWAYS`, Standard `ZoneView.DEFAULT_SHOW` = `TOOL_OR_FLIGHT`, `version` 2 mit einmaligem
  Anheben von `TOOL` aus Dateien ohne `version`, Einstellung im Tab „Zonen“): `ZoneRenderer` zeichnet Umrisse mit Zonen-Markierer
  oder Kurs-Werkzeug (`GateMountKeys.holdingCourseTool`), im Standard auch in einer Pilotensitzung (`PilotSession#isActive`,
  FPV und Sichtflug), beim Bearbeiten und für die gezeigte Zone; überlappende Zonen nur
  Kanten, die Zone der Kamera bzw. die gezeigte hervorgehoben. `/propwash zone set <zone> <regel> world`.

### Add-on-API 1.7: Vertrag, Namensraum und Handbuch (apiimpl, api; 0.6.0, Entwurf `docs/design/0.6.0/addon-api-namespace.md`)
Beschrieben im Handbuch `docs/api/addon-guide.md` (§21, Sharecodes §13), im Doku-Baustein `docs/addon-api-1.7.md` und in
`docs/api/changes.md` (0.6.0). Verträge:
- **Stand A, dann Verhalten:** `apiimpl.Api17Registries` nahm alle 1.7-Anmeldungen zuerst ohne Verhalten an; die
  Stränge der Welle 2 haben die Platzhalter durch Verhalten ersetzt (Abschnitte unten).
- **Additiv:** Jede neue Methode an einer Schnittstelle aus 1.6 ist `default`. `ApiContractTest` vergleicht per
  Klassendatei und Reflection mit `api-1.6.0-signatures.txt` und prüft mit `api-1.7.0-published.txt`, dass die seit
  0.6.0-dev.2 veröffentlichten Schnittstellen aus 1.7 nur noch `default`-Methoden dazubekommen (deshalb sind etwa
  `WorldRules#raceNeutral`/`#raceRelevant` und `RaceData#standard` `default`). Die Liste für 1.6 wird nur bei einer neuen
  Hauptversion neu erzeugt.
- Records aus 1.7 entstehen nur über Builder und Fabriken. Versionsbereiche der Abhängigen: Fabric `>=0.6.0-`, NeoForge
  `[0.6.0-alpha,)`; zur Laufzeit `PropwashApi.requireVersion(modId, "1.7")`.
- Der Namensraum `propwash` ist reserviert: neue Punkte werfen, alte verweigern still (`apiimpl.NamespaceRules`, oben).
- `QuadcopterEntity.PILOT_CHANGED` wird am Server aus `DronePiloting#onPilotChanged` ausgelöst.
- Jedes per `partItems().registerItem` angemeldete Teil braucht ein Item-Modell, sonst erscheint es als Würfel ohne
  Modell.

### Add-on-Schalter und -Achsen (input, ui; 0.6.0, API 1.7 A1, Baustein `docs/addon-switches.md`)
Add-on-Eingaben (`input.addon`):
- `RadioFrame` trägt `addonSwitches` (`long`) und `addonAxes` (`float[]`) neben der eingebauten Maske; die 8- und
  9-stelligen Konstruktoren bleiben. `RadioState#addonStage` füllt beide Werte je Bild.
- `DeviceProfile` behält Schlüssel `ns:pfad` und `addonAxes`; `keepAddonsFrom` schützt sie beim Zurücksetzen der Seite
  „Funktionen“.
- Die einzige Abfrage für Add-on-Schalter und -Achsen ist `input.addon.AddonInputState`: am Server der zuletzt gemeldete
  Zustand, beim Piloten der eigene, sonst `false` bzw. die Mitte. `FlightState#addonSwitch`/`#addonAxis` und
  `GimbalContext#axis` lesen dieselben Werte (im Server-Flug der zuletzt gemeldete Zustand, nach dem Pilotenwechsel
  `false` bzw. Mitte; Achsen gekappt auf −1…1, NaN wird 0; ohne Anbindung wie in der Freecam neutral).
- Pakete `propwash:addon_switch`, `addon_axis` (Client → Server), `addon_features` (Server → Client); höchstens 64
  Schalter und 16 Achsen.
- Die Seite „Add-ons“ steht in `input.client.screen.AddonSwitchRows`; `ModesTab` ruft sie nur auf. Gekürzte Reiter
  („Flug“ statt „Funktionen“) behalten ihren vollen Namen als Beschriftung des Knopfs (Ansage und Suche).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `input/addon/client/AddonTestRadio` | addon-switches (Fix F-cov-cam-b-9 und F-cov-md-a-9; JUnit `input.AddonSwitchesTest`, Selbsttest `input.addon.client.AddonSwitchApiRadioSelfTests`) | Testfunke der API (`PropwashClientApi.simulateRadio`, `stopSimulatedRadio`, `setSimulatedAxis`, `setSimulatedButton`, `bindSimulatedSwitch`), nur in der Entwicklungsumgebung: Gamepad `propwash-api-test-gamepad` (6 Achsen, 15 Knöpfe, `asGamepad`, `ProfileDefaults.gamepad`) bzw. Funke `propwash-api-test-radio` (12 Kanäle, 16 Knöpfe, `ProfileDefaults.radio`); Profil während des Tests in `InputConfig`, Simulation über `TransmitterInput#simulate`; Knopf über `AddonBindings.apply` mit `usesGamepadLayout`, Kanal nur auf `auxAxes` (900–2100 µs); `stop` beendet nur die eigene Simulation und entfernt beide Testprofile; Zubehör-Aktionen (Fix F-cov-md-a-9): `bindActionButton`/`bindActionChannel` für `PropwashClientApi.bindSimulatedAction`, Knopf über `SwitchSources.apply` mit `SwitchFunction.accessory(n)` (nie rastend), Kanal mit derselben Prüfung `switchRange` wie Add-on-Schalter; Selbsttest-Schritt „Zubehör-Aktion 1 mitten im Flug“ |

### OSD-Elemente und Warnungen von Add-ons (osd; 0.6.0, API 1.7 A2, Baustein `docs/addon-osd.md`)
Eingebaute Elemente bleiben Konstanten von `OsdElement`. Add-on-Elemente und -Warnungen kommen nur aus
`Api17Registries.Client` über `PropwashClientApi.registerOsdElement` und `registerOsdWarning`. `OsdConfig.elements`
behält Schlüssel `<ns>:<pfad>` (nicht `propwash`, höchstens 64 Zeichen) auch ohne Add-on; `OsdConfig.VERSION` bleibt 4.
`OsdRenderer` zeichnet sie nur über die Methode `addonOsd` nach den eingebauten Elementen. Add-on-Warnungen folgen im Feld
WARNINGS den eigenen; kritische eigene Warnungen haben Vorrang (`osd.addon.AddonOsdKeys#visibleWarning`, ohne Add-on
gleich `OsdWarnings#visibleIndex`). Text ist druckbares ASCII, gekürzt auf `maxChars`. Wirft ein Add-on-Element eine
Ausnahme, bleibt es bis zum Neustart aus. Im Sharecode reisen die Add-on-Einträge mit (`osd/OsdLayoutCodec`, oben).

### Kollisionsregeln für Add-ons (drone; 0.6.0, API 1.7 A3, Baustein `docs/addon-collision.md`)
`drone.collision.CollisionRules.evaluate` bildet je Flag das UND aller Regeln. Der Client wertet einmal je Tick aus
(`drone.collision.client.ClientCollision`), der Server je Bewegungspaket (`ServerCollision.passBlocks` in
`DronePiloting#onState`); der Server lässt Block- und Pfadprüfung nur nach eigener Auswertung aus. `blocks = false` fließt
als `MoveReport.ghost` ins Flugmodell, `DroneSim` hat dann keinen Kontakt. Wieder fest wird die Drohne erst mit freier
Hülle (`GhostLatch`; am Server höchstens 16 Blöcke und 20 s). Kontaktregeln hängen hinter `SurfaceMaterials`
(`ContactResponse`, Kappung wie `BlockContact`). „Kamera darf in Blöcke“ fragt nur `drone.client.CameraClip` ab, weitere
Quellen melden sich mit `register(BooleanSupplier)` an (erste: Freecam). Der Client wertet Kollisionsregeln nur aus, wenn
der Server sie im Paket `propwash:addon_features` gemeldet hat (`input.addon.client.AddonServerFeatures#knowsCollisionRule`);
unbekannte Regeln zählen beim Client als nicht vorhanden. Ohne Regel ist das Flugmodell bitgleich.

### Welt-Regeln der Add-ons (server; 0.6.0, API 1.7 B5, Baustein `docs/addon-world-rules.md`)
- `Api17Registries.Rules#add` meldet jede Regel bei `server.AddonWorldRules.register` an; daraus wird eine Gamerule in
  `WorldRules.CATEGORY`. Fabric: `flush(…, true)` nach den eigenen Registern, danach direkt ins Register. NeoForge:
  `RegisterEvent` für `GAME_RULE`, danach geschlossen (eine spätere Anmeldung wirft `IllegalStateException`).
- `WorldRule#get(Level)`: auf dem Server die Gamerules der Welt, auf dem Client `ClientWorldRules.raw` (Quelle gesetzt von
  `server.client.AddonRuleSections.init`), ohne Welt der Standard. Abgleich und Bearbeiten laufen über
  `WorldRules.isPropwash` (die Kategorie); kein neues Netzpaket.
- `WorldRules.applyPreset` ruft `AddonWorldRules.applyPreset` auf; gesetzt werden nur Regeln mit Eintrag.
  `RulePreset#matches` fragt `AddonWorldRules.presetActive`: auf dem Server-Thread die Werte der Welt, sonst die Werte am
  Client; ohne Server und Client keine Einschränkung (JUnit bleibt unabhängig); Regeln, die der Server nicht meldet,
  zählen nicht.
- Tab: `AddonRuleSections.split` teilt die Gruppe „Weitere“ in einen Abschnitt je Namensraum. Überschrift ist
  `<ns>.world_rules.section`, sonst der Mod-Name über `ExtensionTabs.displayName` („%“ verdoppelt;
  `AddonRuleSections.sectionTitle`).
- Zonen gelten für Add-on-Regeln nicht (bleiben bei `RuleSpec`); Add-on-Regeln sind standardmäßig rennrelevant
  (§15 „Rennwertung“). Welt-Regeln eines fehlenden Add-ons bleiben erhalten (§15 „Add-on zeitweise entfernt“).

### Add-on-Daten an der Drohne und Wartungsknöpfe (drone, apiimpl; 0.6.0, API 1.7 B6 und B10, Baustein `docs/drone-data.md`)
`drone.data.AddonData` hält die Daten aller Add-ons roh (NBT `AddonData`, Komponente und Serializer
`propwash:addon_data`, §4). `drone.data.DroneDataStore` prüft die Grenzen (2048 Bytes je Schlüssel, 8192 je Drohne) und
gleicht nur `TRACKING` ab; `NONE` reist am Item mit („NONE heißt nicht geheim“). Speicherstelle in `QuadcopterEntity`
sind `addonData()` und `setAddonData`. Zerlegen in der Werkbank verliert die Add-on-Daten; 0.5.x liest ein Drohnen-Item
mit der Komponente nicht. Wartungsknöpfe: `apiimpl.client.ServiceExtensions` im Kopf der Wartung, höchstens drei, der Rest
unter „Mehr“ bzw. „…“.

### Werfen, Inhalt sichtbar, Stöße, alle Drohnen und Kamerablick (accessory, drone, drone-render; 0.6.0, Entwurf `docs/design/0.6.0/addon-api-namespace.md` §3.4–§3.7, B1–B4)
Verhalten zu den Signaturen aus `api17-contract`; Baustein `docs/accessory-gaps.md`. `DispenseModule.init()` in
`PropwashCommon.init()` nach `AddonWorldRules.init()`. Keine neuen Pakete im Netz, keine neuen Texte im Kern.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `accessory/dispense/DispenseMath` | accessory-gaps (reines Java, JUnit `DispenseMathTest`) | `nearest(forward)` → nächste der sechs Richtungen; `recoil(Kind, power, speedScale)` = Masse × Mündungstempo (Tabelle `Kind`, Windkugel + 2,5 N·s); `refusesFluid`, `denied(mayInteract, actionRestricted, mayBuild)`, `remaining(count, used, consume)`, `inherit(droneVel m/s, eigene B/t)`, `itemEject(forward, speed)` (+0,2 hoch); den Schutz eigener Geschosse hält `OwnShots` (seit F-MDA-4 ohne `protects`/`OWN_SHOT_TICKS`) |
| `accessory/dispense/AccessoryDispenser` | accessory-gaps | `dispense(level, drone, mount, actor, slot, options)` für `AccessoryContext#dispense` (aus `AccessoryHost.Ctx`): Eimer mit Flüssigkeit → `REFUSED` ohne `allowFluids`; `ProjectileItem` → Geschoss an `front()` in `forward()` mit Vanilla-`DispenseConfig` × Optionen + Drohnentempo, Besitzer Pilot oder Auslöser → `FIRED`; TNT (Regel `tnt_explodes`) → `PLACED`; Items ohne Verhalten → Auswurf in `forward()` → `FIRED`; sonst Vanilla-`DispenseItemBehavior` an `BlockSource` (Mündungsblock, Dispenser-Zustand mit `nearest`, eigene `DispenserBlockEntity` für Rückgaben) → `USED`/`BLOCKED`; Bau-Rechte (`mayInteract`, `blockActionRestricted`, `mayBuild`) vor TNT, Flüssigkeit und Block-Verhalten → `DENIED`; Mündung im festen Block → `BLOCKED`; Rückstoß über `DronePushes` an der Mündung |
| `accessory/dispense/OwnShots` | accessory-gaps (Fix F-MDA-4) | `protect(projectile, drone)` beim Werfen merkt sich die UUID der Drohne für die ganze Lebensdauer des Geschosses (nur im Speicher, nach Neustart oder Neuladen des Chunks kein eigenes Geschoss mehr); `deflection(drone, projectile, vanilla)` → `PASS` (Durchflug ohne Treffer) für eigene Geschosse, ohne Zeit- oder Hüllengrenze; `protects(drone, Entity)`; `from(drone, DamageSource)`: direkte Quelle oder Verursacher ist ein eigenes Geschoss, `QuadcopterEntity#hurtServer` bricht dann vor `DRONE_DAMAGED` und `breakApart` ab (Geschosse aus der Hand eines Spielers zählen nicht); `tick(drone)` (über `QuadcopterEntity.SERVER_TICK`) räumt entfernte Geschosse einmal je Tick ab; `passed()` Testzähler |
| `accessory/dispense/DispenseModule` | accessory-gaps | Anmeldung `OwnShots.tick`, Aufräumen bei Start/Stopp, `LoadedDrones.forget` bei `LEVEL_UNLOAD` |
| `accessory/AccessoryHost`, `AccessoryBehaviors` | accessory (Haken accessory-gaps) | `Ctx#dispense` ruft `AccessoryDispenser`; `onAction` und `trigger` teilen `act(level, drone, spieler, nummer, gedrückt)` (Ratenbegrenzung, `onAction` je Zubehör, `ACCESSORY_ACTION`); `AccessoryRegistry#triggerAction` → `false` ohne Zubehör mit Verhalten, außerhalb 1–4, im falschen Thread oder bei Ratenbegrenzung |
| `accessory/display/ItemDisplays` | accessory-gaps (reines Java, JUnit `ItemDisplayTest`) | `display(id, behavior, slot)` abgesichert (Fehler einmal je Zubehör im Log); `model(anker mm, neigung, gimbal, display, einheiten je mm, dest)` = Anker · RotX(Neigung) · Gimbal · Versatz · RotY(Gieren) · RotX(Nicken) · RotZ(Rollen) · Skalierung × 160 mm |
| `drone/client/render/AccessoryDisplayState` | drone-render (accessory-gaps) | Feld `DroneRenderState#accessoryDisplay` (eine Zeile); `extract` in `QuadcopterRenderer#extractRenderState` (Stapel aller Zubehöre mit Verhalten und `display`, Kontext `FIXED`; bei `hidden` nur für die eigene FPV-Drohne außerhalb des Schatten-Durchgangs, dann mit der Lage aus `FpvView#displayedBody`), `submit` nach `DroneVisuals.submit`; bei verborgener eigener FPV-Drohne (`atLens()`) zeichnet `FpvPropPass` den Inhalt mit `submitAtCamera` im eigenen Durchgang der FPV-Props an der Stelle der Hand (frisch geleerte Tiefe, auch ohne Props und Ducts im FPV-Bild), sonst (Iris-Shaderpack, `forceWorld`, Fehler) `submitAtLens` im Welt-Durchgang nach den FPV-Props: Linse des Modells (`FpvProps#lens`) auf die Kamera, Maßstab 1 mal Drohnengröße (Befund SPEAR3-1), gedreht mit `CameraRenderState#orientation` zurück um den Uptilt (`FpvProps#cameraTilt`), also samt Vibration und Ruck fest im Bild wie die Props, mit Gimbal-Kamera eines Add-ons (`AddonCamera#provider`) mit der Lage aus `FpvView#displayedBody` (Befund G-SPEAR3-1-2); Nahebenen-Ausgleich wie die Props (Befund AUTO29-1): gleichmäßig um die Linse nach außen, bis der nächste sichtbare Eckpunkt des Item-Modells (`ItemStackRenderState#visitExtents`, Bild ×1,05 plus ein Texel, ab 1 mm Tiefe; `visibleDepthMm`) bei `FpvPropGeometry.NEAR_TARGET_MM` liegt, höchstens `MAX_NEAR_SCALE` (`nearScale`, `lastNearScale()` für Tests), `keepNormals` setzt danach die Normalenmatrix der Lage ohne Ausgleich ein (NeoForge normiert gebackene Normalen nicht); `shownInOwnFpv(drone)` (Verhalten angemeldet, Inhalt nicht leer) hält die eigene Drohne in `QuadcopterRenderer#shouldRender` und `FpvProps#extractsCameraDrone` auch ohne Props und Ducts im FPV-Bild; `gimbal[mount]` (Quaternion, von `extract` auf Einheit gesetzt, danach von `operator-gimbal` zu füllen) dreht alles an der Halterung; `count`, `count(mount)`, `shown(mount, slot)` für Tests |
| `drone/Knockback` | accessory-gaps (reines Java für `impulse`, JUnit `KnockbackTest`) | Haken in `QuadcopterEntity#pushFromExplosion`, `#onExplosionHit` (Ursache aus der Quelle: `AbstractWindCharge` → `WIND_CHARGE`, sonst `EXPLOSION`) und `#push` (`OTHER`, nicht während einer Explosion); nur für geflogene Drohnen (Pilot, `AutonomousReturns.flying`) und mit Listenern: `DRONE_KNOCKBACK` mit Δv in m/s, `impulse(Δv, Masse samt Zubehör, Beladung und Fracht, Skala, Abbruch)` gekappt auf 50 N·s über `DronePushes`; Vanilla-Stoß der Entity bleibt (0.5.2) |
| `drone/LoadedDrones` | accessory-gaps | Haken in `QuadcopterEntity#setLevelCallback`: Drohne bei Aufnahme in den Entity-Speicher eines Server-Levels geführt, bei `NULL` entfernt; `of(level)` = unveränderliche Liste (Schnappschuss bis zur nächsten Änderung) für `DroneAccess#loaded` |
| `drone/CameraPoses` | accessory-gaps (JUnit `CameraPoseTest`) | `of(entity)` / `of(build, tune, com, lage)` für `DroneAccess#cameraPose`: Auge = Schwerpunkt + Lage · (lensRight, lensUp, −lensForward), Blick = Lage · RotX(Uptilt aus `CameraMount.uptilt`) · (0, 0, −1), senkrechtes Sichtfeld aus `FpvRig` (16:9) wie `CameraView#verticalFovDeg`; leer ohne Drohne oder mit fehlendem Video-Teil |
| `drone/QuadcopterEntity` | drone (Haken accessory-gaps) | `deflection`, `push`, `pushFromExplosion`, `onExplosionHit`, `setLevelCallback` delegieren nur (je eine Zeile) |

Selbsttests `interact.client.AccessoryGapsSelfTests` (Präfixe `addon-api-17: B1` … `B4`), Bilder
`interact.client.AccessoryDisplayShowcase` (`accessory_display_*`), Server-Rauchtest `accessory.dispense.DispenseServerChecks`
(devtest), Beispiel `dev/example/ExampleDispenser`.

### Autonomer Flug mit Add-on-Flughilfe (rescue, drone, osd, apiimpl; 0.6.0, Entwurf `docs/design/0.6.0/addon-api-namespace.md` §3.10, Verhalten B7)
Add-ons fliegen eine Drohne über `PropwashApi.drones().autonomy()` am Server, im selben Flugmodell wie der Heimflug.
Baustein für Add-on-Autoren: `docs/addon-autonomy.md`. Sprachquellen `common/src/lang/addon-autonomy/`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `rescue/ServerFlight` | rescue | Basis des Server-Flugs (bisher in `ServerReturnFlight`): `begin(drohne, level, tempo, scharf)`, `arm(drohne, level)` (Schärfen am Boden wie beim Piloten, zwei Frames mit Schalter aus/an), `fly(drohne, level)` → `Outcome` (`FLYING`, `LANDED`, `LOST`); Unterklassen liefern `steer(dt, drohne)` (füllt `input`), `wantsDisarm()`, `landed()`, optional `resynced`, `beforeSlices`; Zonen, Wind, Bodenabstand, `ServerCollision.afterSurface`, Verschleiß und Schreiben ins Entity unverändert; `gpsFix(drohne, level)` misst den GPS-Empfang am Schwerpunkt über `GpsReception` (Fix F-RTH-1) |
| `rescue/ServerReturnFlight` | rescue | Heimflug als Fall von `ServerFlight`; `start(drohne, plan, level, welt)`, `tick(drohne, level, plan, besitzer)`, `autopilot`, `announcedStuck`, `pilotHome` wie bisher |
| `rescue/ServerFlightAssist` | rescue (reines Java, JUnit `rescue.AutonomousAssistTest`) | Ablauf eines Add-on-Flugs: `startInFlight`, `startFromGround` (Steigen um `takeoff` über den Startpunkt), `replace`, `update(dt, sim, x, unten, z, welt, input)`, `end(grund)`, `disarmed(gelandet)`, `consumeEnded()`, `followUp(grund, heimWunsch, gpsFix)` → `NONE`/`HOME`/`LAND` (`OUT_OF_RANGE` und `TERRAIN_LIMIT` → `HOME` mit GPS-Fix, sonst `LAND`; Fix F-RTH-1, F-GCB2F-1); `needsGps(grund, heimWunsch)`: gemessen wird nur, wenn die Folge davon abhängt; `terrainLimit(gehalten, dt)` setzt `FlightState#terrainLimit()` in der Phase `ASSIST`, nach `TERRAIN_LIMIT_SECONDS` = 1 s (abklingend gezählt) Ende mit `TERRAIN_LIMIT`, Folge wie `OUT_OF_RANGE` (Fix F-GCB2-1, F-GCB2F-1); Ende bei Akku (`CRITICAL`/`EMPTY` oder < 15 %), `maxDuration`, 60 min über alle Wechsel; Landung mit `RescueAutopilot#landNow` |
| `rescue/ServerFlightControl`, `rescue/ServerFlightState` | rescue (reines Java) | Regler des Heimflugs für Halten, Steigen, Position, Tempo, Kurs und Gierrate mit der echten Lage; `FlightState`/`AssistOutput` am Server (Schätzwerte = echte Werte, GPS immer gültig, Knüppel neutral, Gas 0,5) |
| `rescue/AddonAutonomy` | rescue | `start`, `stop`, `active`, `label`, `phase`, `flying`, `sim`, `flights`; `tick(drohne)` im Server-Autopiloten; `onPilotExit(drohne, pilot, plan)` fragt die `ExitHandler` aus `Api17Registries.AUTONOMY` in Anmeldereihenfolge; `AUTONOMY_ENDED` nach jedem Ende, Paket und Meldung an den Besitzer; Heimflug nach `BATTERY`/`TIMEOUT`, nach `OUT_OF_RANGE` und `TERRAIN_LIMIT` nur mit GPS-Fix an der Stelle der Drohne (`ServerFlight#gpsFix`, gemessen nur, wenn `ServerFlightAssist#needsGps` es verlangt; Fix F-RTH-1, F-GCB2F-1) über `ReturnPlan` (Plan des Piloten, sonst Homepoint, sonst Startpunkt; > 1500 Blöcke sofort landen; nur mit Regel `autonomous_rescue`) |
| `rescue/AutonomyBudget` | rescue | `Kind` `HOME`/`ADDON`; `ADDON` höchstens `SERVER_LIMIT − 2` je Server und `OWNER_LIMIT − 1` je Spieler; gemeinsamer Zähler `AutonomyBudget.SERVER` für Heimflug und Add-on-Flug |
| `rescue/AutonomyBudget#addonLimits`, `#refusal`, `server/rules/WorldRuleSpecs.AUTONOMOUS_FLIGHTS_PER_PLAYER`, `AUTONOMOUS_FLIGHTS_TOTAL` | rescue, server (Befund OWN2810P-1) | Welt-Regeln `propwash:autonomous_flights_per_player` (0–8, Standard 1) und `propwash:autonomous_flights_total` (0–32, Standard 6), Gruppe Spiel, 0 = keine, rennneutral, nicht in Zonen; `AddonAutonomy.budget(server)` setzt sie vor jeder Prüfung (Add-on-Flug und Heimflug in `AutonomousReturns`) über `addonLimits(je Spieler, gesamt)`; insgesamt höchstens max(`OWNER_LIMIT`, je Spieler + 1) bzw. max(`SERVER_LIMIT`, gesamt + 2); bei `NO_BUDGET` Meldung `refusal(drohne, admission)` mit dem Wert in der Aktionsleiste des Besitzers |
| `rescue/AutonomousReturns` | rescue (Haken addon-autonomy) | `onPilotChanged` fragt beim Aussteigen zuerst `AddonAutonomy.onPilotExit`; Server-Autopilot = `AddonAutonomy.tick` oder Heimflug; `flying`/`sim` auch für Add-on-Flüge |
| `rescue/FlightChunks` | rescue (nur im Paket sichtbar, Fix F-CAMB-1) | Chunk-Tickets eines Flugs ohne Pilot (Add-on-Flug, Heimflug, Sinkflug): `keep(drohne)` setzt `propwash:drone` über `RangeManager#keepSimulated` für den Chunk der Drohne und jeden schon erzeugten Nachbarn; `keep(drohne, erzeugen)`: beim Add-on-Flug mit `ServerRules.mayGenerateChunks(besitzer)` (beim Start und in jedem Tick geprüft, Fix F-GCB2-1) alle 3×3 Nachbarn auch über nicht erzeugtem Gelände und ein Vorausschau-Ticket `propwash:drone_lookahead` (Radius `AHEAD_RADIUS` = 3, ab 3 m/s 0–`MAX_AHEAD` = 4 Chunks bzw. 4 s Flug in Flugrichtung, `aheadChunks(tempo)`, über `RangeManager#keepAhead`); Heimflug und Sinkflug erzeugen nie Gelände (`keepable(level, x, z)`: alle Chunks in Radius `AnchorRules.DRONE_TICKET_RADIUS` = 2 erzeugt, mit Gedächtnis erzeugter Chunks je Flug, höchstens `MAX_REMEMBERED` = 4096, damit Tickets und `rescue/FlightFence` dasselbe sehen und ein entladener Chunk hinter der Drohne nicht wieder als unbekannt gilt; Fix F-cov-cam-b-2; 3×3 tickend, 7×7 geladen); gepflegt beim Start, im Flug-Tick und am Tick-Ende für jeden laufenden Flug, unabhängig davon, ob die Drohne in diesem Tick tickte. Das Drohnen-Ticket hält mit `FLAG_KEEP_DIMENSION_ACTIVE` die Dimension aktiv, auch ohne Spieler; trennt der Besitzer, landet die Drohne an Ort und Stelle bzw. fliegt den Heimflug zu Ende, erst danach verfallen die Tickets. Test: Beitrittstest „Autonomie ohne Besitzer“ (`dev.AutonomyOfflineJoinCheck`, `PROPWASH_JOINTEST_AUTONOMY_OFFLINE`) |
| `rescue/FlightFence` | rescue (nur im Paket sichtbar, Fix F-cov-cam-b-2) | Wand eines Add-on-Flugs vor Chunks, die nicht tickend gehalten werden können (nicht alle Chunks in Radius 2 erzeugt; darf der Besitzer Gelände erzeugen, wartet die Wand nur, bis das Gelände da ist): Maske über `FlightChunks#keepable` `REACH` = 3 Chunks um die Drohne (`update(level, drohne, chunks)` bei Chunk-Wechsel und alle `REFRESH_TICKS` = 5 Ticks neu), Grenzlogik `server/EdgeFence` des Piloten (Halt 4 m davor, seitlich und zurück frei), `limit(sim)` in jedem Rechenschritt von `AddonAutonomy.Flight#steer` auf das Tempo des Flugmodells, für jede Ausgabe der Flughilfe und rohe Knüppel; `atLimit()` meldet das Drücken gegen die Wand höchstens 5 m davor (`EdgeFence.STOP_DISTANCE` + `HOLD_BAND` = 1 m) → `ServerFlightAssist#terrainLimit`, nach 1 s `TERRAIN_LIMIT`, wenn der Besitzer nicht erzeugen darf (Fix F-GCB2-1); Schweben davor, entlang und Umkehren beenden nicht. Tests: `rescue.FlightFenceTest`, Beitrittstest „weiter Flug ohne Pilot“ (`dev.AutonomyFarJoinCheck`, `PROPWASH_JOINTEST_AUTONOMY_FAR`) |
| `rescue/GpsReception` | rescue (nur im Paket sichtbar, Fix F-RTH-1) | GPS-Empfang am Server für die Folge von `OUT_OF_RANGE` und `TERRAIN_LIMIT`: Himmelssicht aus 17 Strahlen bis 96 m (90°/60°/30°, Gewichte 1,5/1/0,6 wie `drone/client/SkyView`) über einen `BlockGetter` nur auf geladenen Chunks (ungeladene zählen als Luft, nichts wird geladen), Fix ab 5 Satelliten (`18 · Sicht^1,6` wie `sim/GpsReceiver`), Dimension mit Decke ohne Fix, über der Bauhöhe immer; `ServerFlight#gpsFix(drohne, level)` misst am Schwerpunkt; Tests `rescue.GpsReceptionTest`, Beitrittstest `dev.AutonomyHomeJoinCheck` (`PROPWASH_JOINTEST_AUTONOMY_HOME`) |
| `rescue/PilotTakeover` | rescue (Fix F-CAMP-1, G-CAMP-1-1; Haken in `drone/client/PilotSession`, `input/client/TransmitterInput`, `sim/DroneSim`, `sim/NavHold`) | Übernahme durch den Piloten nach einem Flug ohne Pilot (etwa Add-on-Flug mit Ende `PILOT`): `seamless(drohne)` = scharf und Bodenabstand mindestens `AddonAutonomy.GROUND_GAP`; `place(sim, scratch, drohne, lage)` = `Handover.place` mit dem Tempo der Entity × 20, danach `DroneSim#settleForTakeover` (Akku unter Schwebelast, Höhenschätzung ohne Rauschen der ersten Messung und mit echter Steiggeschwindigkeit, Höhen-Integrator eingeschwungen; nur von hier aufgerufen, Kalibrierung unverändert; die Höhenschätzung mit Steiggeschwindigkeit befüllt `DroneSim#navTick` über `NavHold#primeSettled` schon vor dem ersten Aufruf einer Flughilfe, mit Bodenabstand, Tempo und Schub dieses Rechenschritts, Fix G-CAMP-1-1); `throttle(assistHolds, mode3d, hoverStick, deadband3d)` = 0,5, wenn eine Flughilfe die Höhe über `holdAltitude`/`climb` hält, sonst Schwebegas (im 3D-Modus oberhalb der Totzone). Aufruf aus `PilotSession#start`; nach dem ersten Rechenschritt setzt `TransmitterInput#presetThrottle` den Gas-Ausgangswert für Tastatur, Maus und schrittweises Gamepad (`KeyboardFlight`, `StickMapper#presetThrottle`), bis dahin hält die Sitzung ihn selbst; ist der Scharf-Schalter der Funke bei der Übernahme aus, hält die Sitzung den Arm-Zustand wie die Arm-Taste, bis der Schalter eingeschaltet wird; am Boden startet die Sitzung wie bisher entschärft |
| `drone/QuadcopterEntity` | drone (eine Zeile addon-autonomy) | `tickServer` fragt den Server-Autopiloten für jede Drohne ohne Pilot (nicht mehr nur mit Plan); ohne Flug antwortet er nein, die lose Physik läuft wie bisher |
| `rescue/net/AutonomyStatusS2C` | rescue | `propwash:autonomy_status` (Entity, aktiv, Label ≤ 8, Grund des Endes oder −1) an den Besitzer |
| `rescue/client/AutonomyLabels`, `osd/client/LiveOsdSource` | rescue, osd (eine Zeile) | Label je Entity; beim Zuschauen `OsdData.assistLabel` = Label des Add-on-Flugs |
| `apiimpl/Api17Registries.Autonomy` | apiimpl | `start`, `stop`, `active` rufen `AddonAutonomy` |

Beispiel `dev.example.ExampleFollow` (Übergabe `propwash_example:mission` mit den Tags `propwash_example_hold` und
`propwash_example_follow`), Selbsttests `rescue.client.AutonomySelfTests` (Gruppe `addon-api-17: B7`).

### Zweiter Operator und Gimbal in Aufnahme und Kamerapfad (drone, video, drone-render, sim, rescue, apiimpl; 0.6.0, Entwurf `docs/design/0.6.0/addon-api-namespace.md` §3.11, §3.12, §3.19, Verhalten B8 und B9)
Ein zweiter Spieler steuert über `PropwashApi.drones().operators()` mit seiner Funke den Gimbal; Gimbal-Add-ons drehen den
Kopf am Modell, setzen die Brennweite und nehmen mit der Gimbal-Lage auf. Baustein für Add-on-Autoren:
`docs/operator-gimbal.md`. Sprachquellen `common/src/lang/operator-gimbal/`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `drone/operator/OperatorChannel` | drone (reines Java, JUnit `drone.operator.OperatorChannelTest`) | Achse ↔ `short` (±32767, halber Schritt Fehler), Aktionen 1–4 im Bitfeld, Bit `LEAVE` (0x80) gibt die Kamera zurück; `Rate` höchstens 25 Pakete je 1 s (gleitend); `bind`/`keep` (Pilot, fliegt andere, Dimension, Videoreichweite, beim Halten ×1,1; besetzt) und `accepts` |
| `drone/operator/OperatorServer` | drone | Bindungen je Drohne (UUID); `bind`, `unbind`, `operator`, `axis`, `action`, `feed(drohne, sim)` (Server-Flug); `onInput` (Rolle, Rate, Weitergabe an Pilot, Operator und Zuschauer), `onTick` (Lösen, `SpectatorLinks` des Operators), Pakete nur an Spieler aus der Spielerliste |
| `drone/operator/AutonomousFeeds` | drone | alle 10 Ticks: Drohnen im Add-on-Flug ohne Piloten senden als `SpectateServer.simulateTransmitter(drohne, Label)`, danach wieder aus |
| `drone/operator/net/*` | drone | `propwash:operator_input` (C2S: Entity, 4 × `short`, Byte), `operator_state` (S2C: dasselbe ohne Bit `LEAVE`), `operator_role` (S2C: Entity, UUID, `NONE`/`OPERATOR`/`PILOT`, Name ≤ 64) |
| `drone/operator/client/OperatorClient` | drone | Lage je Entity aus `operator_state` (`axis`, `action`, `state`), Rolle aus `operator_role`; als Operator mit Bild 20 Hz Knüppel (`RadioState`) und Zubehör-Aktionen, Esc → `LEAVE`; `feed(drohne, sim)` beim Piloten; `testInput` für Tests |
| `drone/operator/client/OperatorTestInput` | drone (JUnit `drone.operator.client.OperatorTestInputTest`) | liest bei jedem Senden des Operators (20 Hz) die System-Eigenschaft `propwash.test.operatorInput=<Roll>,<Nick>,<Gier>,<Gas>[,<Aktion>…]`; ein gültiger Wert ersetzt Knüppel und Aktionen (Achsen gekappt, Gas 0…1, Aktionen 1–4), ungültige Werte einmal als Warnung im Log, dann gilt das Gerät; das interne `OperatorClient.testInput` hat Vorrang (`docs/operator-gimbal.md` §9) |
| `drone/operator/client/OperatorView` | drone | Operator-Rolle in der Zuschauer-Sitzung (`SpectateSession#operate`, ohne `SpectateC2S`), `spectate(drohne)` für eigene Drohnen im Add-on-Flug (`AutonomyLabels.active`), `recordingDrone()` = Pilot im FPV, Operator oder zuschauender Besitzer |
| `drone/operator/client/GimbalHead`, `HeadCollector` | drone-render | `extract` nach `AccessoryDisplayState#extract`: `modelPose` → `AccessoryDisplayState#gimbal[halterung mit gimbal_head]`; `items(zustand, halterung, posen, sammler)` in `DroneVisuals#submitAccessories`: Quads mit Mitte im Quader von `gimbal_head` um `rotation.origin` gedreht (Rahmenachsen, Neigung der Halterung berücksichtigt), Aufteilung je `ItemQuads` gemerkt |
| `drone/operator/client/GimbalFov` | video | `horizontal(drohne, standard)` in `VideoState#horizontalFov`: `fovDegrees` gekappt 20–150°, NaN = Standard; `path(drohne, rahmen, neigung)` in `FlashbackCam#horizontalFov`: `gimbalFovDegrees` des Add-ons für den Flashback-Pfad (Kontext wie `gimbalRotation`), gekappt 20–150°, NaN oder Ausnahme → Sichtfeld des Schirms wie bisher |
| `drone/operator/client/GimbalRecording`, `video/record/CameraLook` | video | `stabilizesRecording` → `ActionCamRecorder`-Stabilisierung `OFF`; Flashback-Pfad (`path`) = `gimbalRotation` des Add-ons (Kontext: Rahmenlage des Pfads, `spectating` `false`, dt 0; endlich, normiert), sonst wie bisher Kamerablick bei `stabilizesRecording` und Drohne auf dem Schirm, sonst Rahmen + Kamerawinkel (`pathRotation`) |
| `drone/operator/client/GimbalProps` | drone-render | `shown(drohne, neigung)` in `FpvProps#prepare`: `showsProps` des Add-ons (Kontext wie `GimbalFov`, `spectating` = Zuschauer oder Operator); `false` → keine FPV-Props und -Ducts (`fpvGeometry` leer, auch `FpvPropPass`), ohne Gimbal oder bei Ausnahme wie bisher; Zähler `hiddenFrames` |
| `video/client/SpectateSession` | video (Haken operator-gimbal) | Feld `operator`, `operator()`, `operate(minecraft, uuid, id)`; `stop` schickt im Operator-Modus kein `SpectateC2S`, `onResult` übergeht Antworten, `request` beendet den Operator-Modus |
| `video/client/record/ActionCamRecorder`, `FlashbackCam` | video (Haken operator-gimbal) | Bildquelle `OperatorView.recordingDrone()` ohne Pilotensitzung; Stabilisierung über `GimbalRecording.mode`; Flashback-Lage über `GimbalRecording.path`, Flashback-Sichtfeld zuerst über `GimbalFov.path` |
| `accessory/client/AddonCamera`, `ClientInputImpl`, `AddonFlightClient` | accessory (Haken operator-gimbal) | `provider(drohne)`, `context(…)`, `GimbalContext#operatorAxis/#operatorAction`; `ClientInput#operating`; Operator-Achsen ins Flugmodell des Piloten |
| `sim/DroneSim`, `sim/AssistBridge`, `rescue/ServerFlightState`, `rescue/AddonAutonomy` | sim, rescue (Haken operator-gimbal) | `DroneSim#setOperatorAxis/#operatorAxis`; `FlightState#operatorAxis` aus der Sim; `Flight#steer` füttert vom Server; ohne Operator 0, Flugmodell unverändert |
| `apiimpl/client/RecordingControlImpl`, `apiimpl/Api17Registries` | apiimpl | `start`/`stop`/`recording` über `ActionCamRecorder`, `photo` = `CleanFrameTap` → PNG `propwash_photo_<Zeit>.png` im Aufnahme-Ordner; `operators()` und `spectate()` echt |

Beispiel `dev.example.ExampleOperator` (Zubehör `propwash_example:gimbal_front`, Aktion 3 bindet), Selbsttests
`drone.operator.client.OperatorGimbalSelfTests` (Gruppe `addon-api-17: B9`), Beitrittstest `dev.OperatorJoinCheck` und
`drone.operator.client.OperatorJoinProbe` (`PROPWASH_JOINTEST_OPERATOR`).

### Gerade hängendes Zubehör (build, drone-render, accessory; 0.6.0, API 1.7, Baustein `docs/accessory-hang.md`)
`AccessoryDefinition#hangsStraight` (letzte Record-Komponente, JSON `hangs_straight`, Standard `false`; der Codec schreibt
nur `true`, die Konstruktoren mit 12 und 13 Argumenten bleiben). `build.AccessoryType#hangsStraight()` gilt für den
Greifer und für jedes Teil mit der Angabe. Jede Lage von Zubehör läuft über `build.AccessoryAnchor#tiltFor(type)` und
`#stretch(type)`: Render, VisualHull, ParamDerivation, MountPoses, ItemDisplays sowie `ledMm` und `lensMm`. Neue Stellen
nutzen `tiltFor(type)`, nie `tiltDegrees()` direkt. Die neue Komponente ändert `toString()` und damit den Teile-Hash im
Handshake jedes Zubehörs (gleiche Versionen vergleichen weiter gleich). `webexport/PartData` exportiert das Feld noch nicht.

### Kompass im Flug-Zustand (sim, rescue, api; 0.6.0, API 1.7, Baustein `docs/api-compass.md`)
`api/flight/FlightState#compassHeadingDeg`, `#compassErrorDeg`, `#compassHealthy` und `COMPASS_DISTURBED_DEG` (15°) sind
`default`-Methoden mit idealem Kompass (echter Kurs, 0, gesund). `sim.AssistBridge` überschreibt Kurs und Fehler aus
`NavHold#headingErrorRad` (vor dem ersten Nav-Takt `headingOffsetRad`); Fehler = Kompasskurs − echter Kurs =
−headingErrorRad, gefaltet auf −180 … 180°, NaN gilt als gestört. `rescue.ServerFlightState` nutzt die Standards, weil der
Server mit echter Lage fliegt; Kräfte im Server-Flugmodell sehen dessen eigenen Kompassfehler. Für Tests liest
`sim/CompassTestOverride` die System-Eigenschaft `propwash.test.compassErrorDeg` beim Bau jedes `NavHold`: Ein gültiger
Wert ersetzt nach allen Zufallszügen nur `headingOffsetRad` (= −Grad); das Wandern und alle übrigen Zufallswerte
bleiben, ohne Eigenschaft ändert sich nichts. Tests: `api.compass.CompassStateTest`, Selbsttest
`addon-api-17: Kompass` (`accessory.client.CompassSelfTests`).

### Neigungsgrenze im Flug-Zustand (api, sim, rescue, drone, accessory; 0.6.0, API 1.7, Befund GCCA3P-1)
`api/flight/FlightState#angleLimitDeg()` ist eine `default`-Methode mit `DEFAULT_ANGLE_LIMIT_DEG` (60°, gleich
`PidProfile#angleLimitDeg`). `sim.AssistBridge` liefert `fc.pid().angleLimitDeg`, also genau den Wert, mit dem
`AttitudeController#angle` den Knüppel in Neigung umsetzt; `rescue.ServerFlightState` liest ihn aus `DroneSim#config()`
des Server-Flugs. `api/drone/DroneView` hat die neue letzte Komponente `angleLimitDeg`; `accessory.DroneQueries#view`
füllt sie mit `drone.TuneMapping#angleLimitDeg(tune)` (Schlüssel `TuneMapping.ANGLE_LIMIT`, nicht endliche Werte
ignoriert, `PidProfile#sanitize` kappt auf 10–85°), gleich `toConfig(…).pid.angleLimitDeg`. Der Konstruktor mit
11 Argumenten bleibt und setzt 60°. Flugverhalten und Kalibrierung unverändert. Tests: `rescue.FlightStateAngleLimitTest`,
Selbsttest `addon-api-17: B7` (Schritt „Neigungsgrenze 30° aus dem PID-Tab“).

### Zubehör-Befunde: echte Beladung, abgestellt feuern, Tempo zwischen den Ticks (accessory, drone, osd; 0.6.0, API 1.7, Baustein `docs/accessory-fix.md`)
- `accessory.AccessoryPayload`: echte Beladung ohne die frühere stille Grenze von 2 kg, `MAX_LADEN_GRAMS` 20 000 =
  Bauteilgrenze, `limitGrams`/`flightGrams`/`massStep`/`overloaded`. „Überladen“ erscheint in `AccessoryScreen`, in der
  Statuszeile von `QuadcopterItem` und als `OsdWarnings.Warning.OVERLOADED` (`OsdData#overloaded`, gesetzt in
  `LiveOsdSource`).
- `AccessoryDefinition#muzzleMm` (JSON `muzzle_mm`, Versatz von der Mitte der Vorderfläche, höchstens 20 mm außerhalb der
  Box; Builder `muzzle(x, y, z)`) → `AccessoryType#muzzleMm()` → `MountPose#front()`.
- `accessory.dispense.DispenseOrigin`: Steht die Drohne (`onGround` oder Block bis 5 cm unter dem Hitbox-Boden), hebt sie
  den Abgabepunkt um die Eindringtiefe der Box (`AccessoryAnchor#corners`). TNT und Items starten nicht unter der
  Standfläche, `BLOCKED` nur im festen Block.
- `accessory.MotionTrack` + `DroneMotion`: Pilotenpositionen über `SERVER_MOVED` mit Empfangszeit, Theil-Sen-Median über
  300 ms, Pakete unter 2 ms Abstand zusammengefasst, nach oben begrenzt durch `StateValidation.refillRate` der
  Tempo-Regel. Nach 500 ms ohne Positionen oder ohne Pilot gilt die Bewegung zwischen den Ticks; daraus kommt
  `AccessoryContext#velocity()` im Pilotenflug.
- Wie bei `hangsStraight` ändert die neue Record-Komponente den Teile-Hash im Handshake. Tests: `accessory/fix/*Test`,
  Selbsttest `addon-api-17: Zubehör-Befunde`, Beitrittstest `PROPWASH_JOINTEST_ACCFIX`.

### Add-on zeitweise entfernt: Platzhalter und Welt-Regeln (build, core, server; 0.6.0, Baustein `docs/addon-placeholders.md`)
Platzhalter für fehlende Teile (`build.PartIndex.missing`) sitzen beim Zubehör immer an der Halterung des gespeicherten
Eintrags. Die Werte kommen von `action_cam_light` (oben) und `led_strip` (unten und vorn, vorn an die Halterung versetzt),
die Id ist die des fehlenden Teils. `DroneBuildCodecs` verwirft beim Laden nur kaputte Zubehör-Einträge, mit Warnung im
Log, und nie das ganze Zubehör; im Netz gibt es keinen `DecoderException` mehr. `mixin.server.GameRuleMapMixin` und
`server.UnknownGameRules` führen Gamerules unbekannter Namensräume außerhalb von `minecraft` beim Laden und Speichern
unverändert mit, auch durch `GameRuleMap.copyOf` (keine Meldung „Failed to parse saved data … game_rules“ mehr). Tests in
`docs/testing.md` „Add-on zeitweise entfernt“.

### Flächen: Slime, Wolle und Heuballen (sim, drone, rescue; 0.6.0, Entwurf `docs/design/surface-blocks.md`)
- **Antwort einer Fläche:** `sim.SurfaceResponse(restitution, grip, impactScale)`. Werte: `NORMAL`/`normal(friction)`,
  `BOUNCY` (0,8 / 1,0 / 0), `CUSHION` (0,05 / 1,5 / 0); `STICKY` (Honig) im nächsten Abschnitt.
- **Meldung der Welt:** im `MoveReport` über `surfaceRestitution`, `surfaceGrip` und `surfaceImpactScale`. Standard 1,0;
  NaN zählt als 1,0; Werte werden auf 0 bis 1 begrenzt.
- **Wertung in `DroneSim#classify`:** v und Quergeschwindigkeit werden mit dem Anteil multipliziert. Bei 0 gibt es keinen
  Prop-Schlag und keinen Eintrag im Aufprall-Protokoll, also keinen Verschleiß. Die Rahmenverluste skalieren mit dem
  Anteil; die Bodenebene merkt sich Haftung, Rückprall und Anteil auch ohne Berührung.
- **Abtastung:** `drone.SurfaceMaterials.at(level, hitbox, sx, sy, sz)` und `fill(level, hitbox, report, grounded)`. Je
  Seite zählt das nächste Feld mit Kollisionsform, es gewinnt die größte Fläche, bei Gleichstand die weichere. Ungeladene
  Chunks werden nicht gelesen. Bei mehreren Tags gilt Kissen vor federnd vor klebrig. Tags `propwash:drone_bouncy`,
  `propwash:drone_cushion` und `propwash:drone_sticky` (per Datenpaket erweiterbar).
- **Aufrufer:** `PilotSession#fillSurface`, `ServerReturnFlight#move`, `VoxelFlight` (optional).
- **Regel für spätere Änderungen:** Mit Anteil 1,0 bleibt alles bitgleich. Die Kollisionsregeln der Add-ons setzen auf
  `SurfaceResponse` auf. Kein neues Paket, keine Protokolländerung.
- **Auflage im Block:** Eine Drohne, die einen Bruchteil eines Millimeters in einem Block steckt, gilt in
  `DroneSim#afterMove` als gelandet und kommt zur Ruhe (`QuadcopterEntity#supportGap` und `rescue/AddonAutonomy` messen
  noch ohne angehobene Hülle).

### Honig und Just More Parts ab 1.2.0 (sim, drone, rescue, guard, share, Loader; 0.6.0, Owner-Entscheidungen vom 21.10.2026)
Honig wirkt auf geflogene Drohnen wie auf Vanilla-Entitys (Baustein `docs/design/surface-blocks.md` §2a); Propwash 0.6.0
verlangt Just More Parts 1.2.0, der Altmodus für 1.1.x ist entfallen (`docs/namespace-guard.md` §4,
`docs/sharecode-ui.md` §5).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `sim/SurfaceResponse.STICKY` | sim | Rückprall 0, Haftung 1,5, Aufprall 0 (kein Absturz, kein Prop-Schlag, kein Verschleiß); `withStickyBelow` gibt es nicht mehr |
| `sim/MoveReport#stickySides`, `STICKY_FLOOR`, `stickyBit(achse, positiv)` | sim | Bitfeld der Seiten der Hülle mit Honig-Kontakt (Bit `achse · 2 + positiv`), `clear()` setzt 0 |
| `sim/DroneSim` (`STICKY_DRAG` 18/s, `STICKY_HOLD` 4 × Gewicht, `STICKY_SECONDS` 1 s, `stickySides()`, `stickyHold()`) | sim | Bitfeld aus `afterMove`; je Teilschritt Geschwindigkeit und Drehimpuls exakt mit `e^(−18 · h)` gebremst, als Kontaktkraft im Beschleunigungsmesser; am Boden mit Bit unten Haftkraft, die über 1 s Zug (Schub hebt) linear abklingt und ohne Zug wieder aufbaut, nicht im Turtle-Modus; Bitfeld 0 rechnet bitgleich; `clearContacts` setzt alles zurück |
| `drone/SurfaceMaterials#sticky(level, hitbox, report)`, `#stickySides` | drone | nach jedem Zug alle sechs Seiten: Block aus `#propwash:drone_sticky` mit Kollisions- oder Umrissform höchstens 3 cm vor (1 cm in) der Hülle, nur geladene Chunks; `fill(level, hitbox, report, grounded)` ohne Block unter der Drohne, berührte Honigseiten zählen mit ihrer Antwort |
| `drone/client/PilotSession`, `rescue/ServerFlight`, `rescue/VoxelFlight` | drone, rescue (Haken fix-2110) | rufen vor `afterMove` die Abtastung auf (Pilot ohne Geist-Modus, Heimflug, Zell-Abfrage `SurfaceCells`: Zelle mit `STICKY` höchstens 3 cm vor der Hülle) |
| `drone/DroneFluids#at`, `drone/CauldronContent`, `drone/WaterSurface#relative` | drone (fix-2110, Befund OWNP6-7) | Flüssigkeit am Schwerpunkt für `PilotSession` und `ServerFlight`: zuerst der Flüssigkeitszustand, sonst Vanilla-Kessel. Wasser- und Lavakessel zählen im Becken (2–14 px) vom Boden (4 px) bis zur Füllhöhe (Wasser 9/12/15 px, Lava 15 px) als Wasser bzw. Lava, Pulverschneekessel nicht; `WaterSurface` meldet die Oberfläche im Wasserkessel und darüber. Kein Tag, Kessel anderer Mods zählen als leer; ohne Kessel bitgleich. JUnit `drone.CauldronContentTest`, `sim.CauldronFluidTest`, Selbsttest `drone.client.CauldronSelfTests` (Gruppe `drone: Kessel`), Baustein `docs/drone-cauldrons.md` |
| `fabric.mod.json` `breaks`, `neoforge.mods.toml` `incompatible` | Loader | `"justmoreparts": "<1.2.0-"` bzw. `versionRange = "(,1.2.0-alpha)"`, `side = "BOTH"`, Grund englisch und deutsch („Just More Parts 1.1.x passt nicht zu Propwash 0.6.0 – bitte auf 1.2.0 aktualisieren“); Vorabstände von 1.2.0 laden |
| `guard/LegacyAddons`, `GuardClient#legacyHint`, `propwash.guard.legacy_jmp`, `ShareClient.LEGACY_TAB`/`tabId` | entfernt | kein unterdrückter JMP-Knopf, kein Hinweis je Spielstart, keine Weiterleitung von `justmoreparts:sharecode`; `PropwashClientApi.openConfigTab` öffnet die Id, wie sie ist; PW1/PW2 lesen bleibt |
| `interact/SnagMaterial.POWDER_SNOW`, `interact/SnagBlocks#shape`/`#look`, Tag `propwash:drone_snag_powder_snow`, `drone/CauldronContent#snag` | interact, drone (fix-2110, Befund DEC2810-1) | Pulverschnee bremst Drohnen wie Vanilla-Entities: Werkstoff nur mit linearer Bremse 1/(1,5 × 0,05 s) ≈ 13,3/s, ohne Halten, Prop-Last und Verschleiß (Leerlauf sinkt rund 0,7 m/s); der Block über das Tag (Standard `minecraft:powder_snow`), der Vanilla-Pulverschneekessel ohne Tag nur im Becken (2–14 px) vom Boden (4 px) bis zur Füllhöhe (9/12/15 px); `shape` liefert diese Form für Abtastung und `SnagEffects`, `look` das Aussehen des Pulverschnees für Bröckchen und Klang; Client (`PilotSnags`) und Server (`DroneSnags.Flight`) gleich; ohne Pulverschnee bitgleich. JUnit `sim.PowderSnowFlightTest`, `interact.PowderSnowSnagTest`, `drone.CauldronContentTest`, Selbsttest `drone: Kessel`, Bausteine `docs/drone-cauldrons.md` §3, `docs/design/drone-snags.md` |
| `sim/DroneSim#step` (Anströmung im Wasser) | sim (fix-2110, Befund CAULD2-1) | Im Wasser gehen Wind und Böen in die Anströmung von Rumpf und Props nur mit `1/Widerstandsfaktor` ein (wasserdicht `1/(1 + Eintauchgrad · 399)`, sonst `1/400`), der Eintauchgrad (`updateSubmersion`) wird dafür vor der Anströmung bestimmt; Wasserblock und Wasserkessel wirken wie stehendes Wasser, am Client und im Heimflug am Server; in Luft bitgleich. JUnit `sim.CauldronStillWaterTest`, Selbsttest `drone.client.CauldronDriftSelfTests` (Gruppe `drone: Kessel`) |

JUnit `sim.HoneySurfaceTest`, `sim.SurfaceResponseTest`, `guard.JustMorePartsLockTest`; Selbsttest
`drone.client.SurfaceSelfTests` (Gruppe `drone: Honig`, Bildschirmfotos `surface_honey_wall.png`,
`surface_honey_takeoff.png`), `share:` ohne Altmodus-Schritt; Rauchtest mit dem Jar von Just More Parts 1.1.1 in
`docs/testing.md` (Abschnitt Sharecode).

### Auto-Arm nach Reset (sim, drone, input, osd, race; 0.6.0, Owner-Entscheidung vom 21.10.2026)
Setzt der Pilot die Drohne per Reset auf den Homepoint zurück und steht Arm noch auf an, armt sie bei Gas unten von
selbst (Baustein `docs/auto-arm.md`). Nach Absturz, Spawn und GPS-Rettung bleibt es bei „Arm aus und wieder an“.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `sim/DroneSim#homeReset(autoArm)`, `#autoArmPending()`, `#autoArmed()`, `#cancelAutoArm()`; `sim/Arming#autoArm`, `#autoArmed`, `AUTO_ARM_WAIT` | sim | `homeReset` = `forceDisarm(FORCED)` + `requireArmSwitchReset()`; mit `autoArm` statt der Sperre `ARM_SWITCH_NOT_RESET` ein Warten, das `frameArming` im ersten Frame ohne Warte-Sperre (Gas nicht unten bzw. nicht Mitte, Absturz-Pause, Neustart, Turtle in der Luft) mit derselben Prüfung wie die Schalter-Flanke auflöst (abgelehnt ⇒ `ARM_BLOCKED`, danach Schalter aus und an); Schalter aus, `requireArmSwitchReset`, `cancelAutoArm` und `reset` beenden das Warten; `autoArmed` gilt bis zum nächsten Entschärfen; ohne `homeReset(true)` ist das Flugmodell unverändert |
| `sim/Telemetry#autoArmPending`, `osd/OsdData#autoArmPending`, `osd/OsdWarnings.Warning.AUTO_ARM`, `AUTO_ARM_3D` | sim, osd | ersetzen `ARM_THROTTLE` bzw. `ARM_THROTTLE_3D`, solange gewartet wird („AUTO-ARM: GAS RUNTER“, „AUTO-ARM: GAS MITTE“) |
| `input/InputConfig#autoArmAfterReset` (Standard `true`), `input/client/screen/ModesTab#autoArmButton()` | input | Schalter „Auto-Arm“ in der Zeile „Scharf schalten“ neben „Tasten…“ (Platz wie die 3D-Freigabe), Suchbegriff „Nach Reset automatisch armen“; Teil der Schalter-Einstellungen (`copy`, `mergeSwitchesFrom`) |
| `drone/client/PilotSession` (`onReset`, `keyArmed`, `autoArmPending()`, `autoArmed()`) | drone | Reset mit `home` ruft `homeReset(autoArmAfterReset)`; Tastatur: `keyArmed` (per Arm-Taste scharf und nicht per Taste entschärft, auch nach einem Absturz) setzt beim Reset den Arm-Zustand wieder; aktive GPS-Rettung ⇒ `cancelAutoArm()` |
| `race/client/ClientRaceTracker#onFrame` | race (Haken auto-arm) | eine Scharf-Flanke mit `PilotSession#autoArmed()` zählt nicht für `StandingStart` (ein Reset startet keinen Countdown) |

JUnit `sim.AutoArmTest`; Selbsttest `input.client.AutoArmSelfTests` (Gruppe `input: Auto-Arm`, Bildschirmfotos
`input_auto_arm_osd.png`, `input_auto_arm_osd_de.png`, `input_auto_arm_modes_tab.png`); Beitrittstest
`dev.AutoArmJoinCheck` und `input.client.AutoArmJoinProbe` (`PROPWASH_JOINTEST_AUTOARM`).

### Rennsystem 0.6.0 Teil A: Rennstart, Wertung, Flächen und Verschieben (race; Spieler-Baustein `docs/race-system.md`, Entwurf `docs/design/0.6.0/race-system.md`)

| Datei | Vertrag |
|---|---|
| `race/ScoreArea` | Reines Java. Rolle NONE/PASS/TURN (nur Gate-Stange), Seite, Reichweite 0,5–16, nach unten 0–32 (Standard 1), nach oben 0–32 (Standard 0), `open` nur beim Freiform-Gate. `normalized(kind)`; `forCourse(kind)`: eine Gate-Stange im Kurs ist mindestens PASS, ohne `open`. `pack`/`unpack` mit 24 Bit. |
| `race/GateGeometry` | Neu: `score`, `withScore`, `scoringKind`, `stageCount`, `scoreWidth`, `scoringPole`, `moved`. Halbebenen reichen von −below bis Höhe + above. Mit Standardwerten bitgleich zu 0.5.2. |
| `race/CourseData.Gate` | `score` und `scoringKind`, Merkerbit 16. `layoutHash` nimmt die Wertung nur bei Abweichung auf. Gate-Stangen behalten ihre Lage. |
| `race/CourseRegistry` | `FORMAT = 2` (fehlt in der Datei = 1). `GateRecord.score` (role/side/reach/below/above) nur bei Abweichung. Prüfpunkte über `scoringKinds`. |
| `race/GateEntity` | `DATA_SCORE`; NBT `ScoreRole/ScoreSide/ScoreReach/ScoreBelow/ScoreAbove/OpenFrame`, nur bei Abweichung. |
| `race/ArmEdge`, `race/StandingStart` | Scharf-Flanke und Startregel: ein stehender Start nur nach eigenem Scharfschalten am Start-Gate mit „Start beim Scharfschalten“ oder in einer offenen Aufstellung; Reset und GPS-Rescue starten nie. `ClientRaceTracker#resetStartPending` entfällt, neu ist `armedBefore`. |
| `race/FreeformFill#lenient` | Freiform-Fläche bei offenem Rand. Dazu `Integrity#ignoringGaps`, `FreeformGateItem.Plan#gapsIgnored`. |
| `race/GateMounting` | `MAX_NUDGE = 8`; `edit` und `preview` mit Wertung und Versatz; Ergebnisse `NUDGE_LIMIT`, `SCORE_LOCKED`. |
| `race/RacePayloads.GateEditC2S` | Zusätzlich `score`, `dx`, `dy`, `dz`; Variante mit 4 Bit. Protokoll bleibt 17. |
| `race/client/GateEditModel` | Neue Zeilen und Seiten („Wertung“, „Position“), `viewNudge`. |
| `race/client/ScoreAreaRenderer` | Zeichnet über `SUBMIT_WORLD_GEOMETRY`; `lastDrawn` für die Selbsttests. |
| `RaceConfig` | `scoreAreas`, `nudgeStep`. |

Tests: Selbsttest `race.client.RaceScoringSelfTests` (`race: Wertung`), Beitrittstest `PROPWASH_JOINTEST_RACEHOME`.

### Rennsystem 0.6.0 Teil B: Schleifen, Reihen, Würfel und Stapeln (race; Spieler-Baustein `docs/race-topology.md`)

| Datei | Vertrag |
|---|---|
| `race/GateGeometry` | `withReverse`/`reversed`: Seite und Normale gedreht, Stufen und Fouls neu, Rahmen gleich. `withJoints`/`joints` mit `JOINT_LEFT/RIGHT/TOP`: Pfosten bzw. oberer Balken fehlen in `boxes`/`parts`/`boxCount`. `equals` beachtet beides, `sameAs` nur die Richtung. |
| `race/CourseData.Gate` | `reverse` (Merkerbit 32), `geometry()` mit Richtung, `samePlace`; `CourseData#firstPass`. `layoutHash` +3001 nur bei `reverse`. |
| `race/CourseOrder` | `Pass(index, reverse)`, `encode`/`decode`/`label` („1 · 3←“), `repeatSide` (Blick gegen die Flugrichtung bzw. den Würfelausgang heißt rückwärts). |
| `race/CourseRegistry` | `GateRecord.reverse` (nur `true`), `appendPass`, `reversePass`, `passes`, `detachGate` entfernt einen Durchgang, `reindex` und `reconcile` setzen die Durchgänge am Entity. |
| `race/GateEntity` | `DATA_PASSES`, NBT `Passes`; `passes`, `hasPass`, `passReversed`, `flownForward`/`flownReversed`, `passText`, `setPasses`; `joints`/`linked`, nachgerechnet in `refreshStack`; `inStack` umfasst `linked`. |
| `race/GateStacking` | `jointable`, `compatible`, `sharedSide`, `joints`, `placementJoints`, `snap` (höchstens 1 Block), `lid`/`covers`/`underLid`. |
| `race/GatePoles`, `race/GatePoleLinks` | Plätze: Balkenmitte (`CENTER_SEAT`), Tauchwürfel mit 8 Plätzen (`CUBE_SEAT`…); `seated`/`seatable`/`elementOnSeat`; Reiter umfassen Flagge und Slalomstange. |
| `race/LapTracker`, `race/LapVerifier`, `race/RaceServer` | Zwillinge (gleiche Geometrie ohne Richtung) einmal gezählt; `Crossing.event`, der Server wertet je Durchgang. |
| `race/CourseToolItem`, `race/CourseEdits` | Zweiter Klick hängt einen Durchgang an (`REPEAT_GUARD_TICKS` = 10), `Action.REVERSE`. |
| `race/GateMounting` | `turnedInPlace`, `fits` für verbundene Gates und Deckel. |
| `race/GateStacking` (gedreht, PATTY-4) | `jointable` und Deckel ohne Neigen und Rollen, Gieren in jedem Schritt; `parallel` (Kreuzprodukt der Seitenachsen unter `ALIGN_EPSILON` = 1e-4) für Reihen, `perpendicular` über Eck; `lid` übernimmt die Feindrehung des angeklickten Gates. |
| `race/client/GateTiles` (PATTY-4) | Teile des Rahmens für `GateRenderer#submitTiled`: auf einer Seite mit `JOINT_LEFT`/`JOINT_RIGHT` kein Teil, auch keine Pfosten-Lichter; mit `JOINT_TOP` weder oberer Balken noch dessen Lichter. Den geteilten Pfosten zeichnet nur das Gate, das ihn behält. |

Tests: `race.client.RaceTopologySelfTests` (`race-topo:`, Bilder `bildschirmfotos: Topologie`) und Beitrittstest
`PROPWASH_JOINTEST_RACELOOP`.

### Kurs-ID und Renndaten für Server und Bots (race, apiimpl, api; Baustein `docs/race-data.md`, für Entwickler `docs/api/race-data.md`)
- Kurs-ID als UUID im Kurs (`race.CourseId`, beim Anlegen vergeben, alte Kurse beim ersten Laden); nach außen adressiert
  die ID, intern bleibt die Nummer. `CourseData` trägt Merker und UUID im Kurs-Sync (Protokoll bleibt 17).
  `/propwash course id [Nummer|Name]` und Knopf „Kopieren“ im Kurs-Fenster.
- `race/CourseArgument`: Kurs-Angabe aller Befehle unter `/propwash race`, `last()` nimmt den ganzen Rest, `word()` mit
  Anführungszeichen. Reihenfolge der Suche: Nummer (alt, mit Hinweis `propwash.race.course_arg.number_hint` samt
  umgeschriebenem Befehl), UUID, Name (eigene Dimension zuerst, mehrdeutig wird abgelehnt). Vorschläge: alle IDs und
  die Namen, die `readsAsName` als Namen liest (keine reinen Ziffern, keine ID-Form, nur bereinigte Namen) und die genau
  ihren Kurs treffen. `/propwash course id` sucht über dieselbe Logik. `race/CourseNames.clean` nimmt umschließende
  Anführungszeichen ab (wie Brigadier, mit `\`-Escapes; beim Umbenennen, im Kurs-Sync und beim Laden), `unquote` ist
  dieselbe Regel für die Kurs-Angabe; gesucht wird über den bereinigten Namen. `reference(nummer, id)` ist die
  Kurs-Angabe für Hilfetexte (ID, ohne bekannte ID die Nummer).
- `race.export.RaceSnapshots` erzeugt die Daten für API und JSON aus demselben Code. `race.export.RaceDataEvents` feuert
  aus `CourseRegistry` und `RaceServer`, nur wenn ein Listener angemeldet ist.
- `race.export.RaceDataMirror` schreibt `<welt>/propwash/race-data/` (Schema 1) gebündelt und atomar auf einem
  Daemon-Thread; gestartet und gestoppt wird er von `CourseRegistry.start` und `CourseRegistry.stop`.
  `CourseRegistry.offline(Path, …)` lädt eine Kursdatei ohne Server.
- API `api.race` (`RaceData` mit `VERSION` 1, `CourseInfo`, `BoardQuery`, `BoardSnapshot`, `BoardEntry`, `Category`,
  `TimeKind`, `AssistInfo`, `ZoneHint`, `RaceEvents`), Zugang `PropwashApi.race()` (`apiimpl.RaceDataImpl`), nur lesend,
  nur am Server-Thread (sonst `IllegalStateException`).

### Punkt-zu-Punkt-Rennen und Rechte am Kurs (race; Baustein `docs/race-p2p.md`)
Rennkurse haben einen Modus (`api.race.CourseMode`): Runden (Standard, alte Kurse) oder Punkt zu Punkt. Bei Punkt zu Punkt
ist der erste Durchgang der Start und der letzte das Ziel; der Lauf endet am Ziel (`LapTracker` und `LapVerifier` mit
`pointToPoint`). Die Zeit steht als Rundenzeit in der Bestenliste derselben Kurs-ID, ein Moduswechsel ist ein
Layout-Wechsel (`ChangeKind.LAYOUT`); der Modus steckt im Kopfbyte des Kurs-Syncs. Kurse ändern nur Besitzer und
Operatoren (`CourseRegistry#canEdit`, `#isOperator`; der Gastgeber zählt mit), Kurse ohne Besitzer nur Operatoren.
`/propwash race mode <kurs> laps|point_to_point` mit denselben Rechten.

### Rennwertung: rennrelevante Regeln, Rennstandard und freies Platzieren (race, server, zone, apiimpl; 0.6.0, Owner-Entscheidung vom 21.10.2026)
Jede Welt-Regel ist rennrelevant oder neutral; Runden zählen rennfrei, wenn die rennrelevanten Werte während der Runde
dem Rennstandard der Welt entsprachen (rennfreie Zonen ausgenommen). Jede Zeit speichert ihren Regel-Satz, die
Bestenliste wird gegen den aktuellen Standard berechnet (Baustein `docs/race-rules.md`).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `server/rules/RuleSpec#raceRelevant`, `#neutral()`, `WorldRuleSpecs` | server | Einordnung steht an der Regel; neue Regeln brauchen einen Eintrag in `race.RaceRulesTest` |
| `api/worldrule/WorldRules#raceNeutral`, `#raceRelevant`; `apiimpl/Api17Registries.Rules#version` | apiimpl | Add-on-Regeln rennrelevant, bis das Add-on sie neutral markiert; `version` steigt bei jeder Anmeldung und Markierung |
| `race/RaceRules`, `RaceRuleSet`, `RuleTimeline` | race | `observe` liest Welt-Werte (Propwash und Add-ons) und je Physik-Regel die Zone (auch Übergänge über `ZoneField#shares`); ein Satz enthält nur rennrelevante Werte ungleich dem Propwash-Standard der Welt; Überlauf setzt `propwash:too_many_rules` |
| `race/RaceStandard`, `RaceStandards` (SavedData `propwash:race_standard`, Pakete `propwash:race_standard`, `propwash:race_standard_edit`) | race | nur Abweichungen vom Propwash-Standard gespeichert; Änderung nur mit `WorldRules#mayEdit`; jeder Wechsel schickt den Standard allen Spielern und schreibt den JSON-Spiegel neu |
| `race/Leaderboard` (`lap_times`, `race_times`, Marke `hidden`, `Scoring`) | race | Wertung jeder gespeicherten Zeit gegen `Scoring#standard`; je Spieler, Klasse und Regel-Satz die beste (höchstens 8 Regel-Sätze, seit LBALL-1 keine Grenze je Klasse); `hidden` = ohne „Runden mit Flughilfe speichern“ geflogen, nie in der Liste mit Flughilfe; die alten Listen schreibt der Codec weiter |
| `race/AssistCheck#lapVerdict(…, RuleTimeline, …, RaceStandard)` | race | feste Gründe (Halten, Rescue, Drohne, Brille, Add-on) getrennt vom Regel-Satz; Physik-Gründe nur rennrelevant und nur gegen den Standard |
| `race/GateItem#free(…, blocks, elements)`, `#overlapAllowed`, `#blocked` | race | mit `propwash:free_course_placement` prüft die Platzierung weder Blöcke noch andere Kurs-Elemente, sondern nur Welthöhe und Wesen im Weg; ohne die Regel prüft sie beides wie bisher; `blocked` nennt die Regel nur, wenn allein Elemente im Weg sind |
| `race/export/*`, `api/race/RaceStandardInfo`, `RuleValue`, `BoardEntry#rules`, `RaceData#standard` | race | Renndaten mit Standard und Regel-Satz je Zeit; `ZoneHint.Zone#changesPhysics` gegen den Standard |

JUnit `race.RaceRulesTest`, `race.RaceRuleSetTest`, `race.RaceStandardScoringTest`, `race.CourseOverlapTest`,
`race.data.RaceRulesDataTest`; Selbsttest `race.client.RaceRulesSelfTests` (Gruppe `race-rules:`, Bildschirmfotos
`race_rules_standard.png`, `race_rules_standard_de.png`); Beitrittstest `dev.RaceRulesJoinCheck` und
`race.client.RaceRulesJoinProbe` (`PROPWASH_JOINTEST_RACE_RULES`).

### Bildprüfung und Ersatz-Shader der Videoeffekte (video; 0.6.0, Baustein `docs/video-check.md`)
Behebt das schwarze FPV-Bild auf Intel-Grafik mit Fisheye und Bildrauschen 0 (0.5.2): Kopien des Hauptbilds laufen als
Zeichnung statt als Blit, eine Bildprüfung schaltet bei Schwarz auf einfachere Videoeffekte.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `video/client/VideoPass#SIMPLE_PIPELINE`, `#copy`, `#simple` | video | Pipeline `propwash:pipeline/video_feed_simple` (`post/video_feed_simple.fsh`, nur `InSampler`); jede Kopie des Hauptbilds läuft über diesen Shader statt `RenderTarget#copyColorFrom` (Blit), bitgleich; Blit nur noch, wenn der Ersatz-Shader nicht kompiliert ist |
| `video/client/VideoPass#level`, `#retryAll`, `#checkOutcome` | video | Stufe `VideoCheck.Level` aus Einstellung und Ergebnis der Bildprüfung: FULL (Video-Shader mit Fisheye), SIMPLE (Ersatz-Shader, kein Fisheye, Störungen als Ersatz-Overlay, gehaltene Bilder einer Digital-Verbindung bleiben), NONE (ohne Shader); `retryAll` = „Shader erneut versuchen“ |
| `video/client/VideoSelfCheck` | video | Bildprüfung beim ersten FPV-Bild und nach jedem Neuladen der Shader: Testbild durch Kopie, Fisheye, Video-Shader (Rauschen 0 und 50 %) und Ersatz-Shader, je vier Pixel zurückgelesen; Chat-Meldung `propwash.video.simplified.chat` einmal je Spielsitzung (nur bei Automatisch) und Logzeile mit Messwerten, Grafikkarte und KHR_debug-Stand |
| `video/VideoCheck` | video (reines Java, JUnit `video.VideoCheckTest`) | Testbild, Messpunkte, `Reading`, `evaluate` → `Outcome` (PENDING, PASS, FULL_BROKEN, ALL_BROKEN, UNAVAILABLE) |
| `video/client/VideoConfig.EffectRenderer`, `#level` | video (JUnit `video.client.VideoEffectsSettingTest`) | AUTO (Standard), FULL, OVERLAY = „Einfach“; gespeichertes altes „Einfaches Overlay“ bleibt „Einfach“; Knopf „Videoeffekte“ mit Tooltip je Wert und Statuszeile in `VideoConfigTab` |

Selbsttest `video.client.VideoCheckSelfTests` (`video: Bildprüfung und Ersatz-Shader …`, `video: Einstellung
Videoeffekte …`, Bildschirmfotos `video_check_fault_black`, `video_check_simple`). Der Scheinwerfer (`HeadlightPass`)
kopiert noch per Blit; auf betroffenen Treibern greift dort die eigene Bildprüfung des Scheinwerfers.

### Bildweg der Action-Cam-Aufnahme (video; 0.6.0, Baustein `docs/video-check.md` §5)
Behebt die komplett grüne Action-Cam-Aufnahme auf Intel-Grafik (0.5.2, Befund INTELREC-1): Jedes gelesene Bild wird
geprüft; liefert die Grafikkarte nur Nullen, schaltet die Aufnahme auf einen Ersatzweg und dann auf eine
CPU-Umrechnung, geht beides nicht, bricht sie mit Meldung ab, statt ein grünes Video zu speichern.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `video/client/record/ActionCamCapture#capture(…, ImagePath, …)`, `Sink#accept(ByteBuffer, ImagePath)`, `#fallbacksAvailable(main)` | video | Bildwege GPU (bisheriger Weg, `copyTextureToBuffer`, asynchron), READ (Ersatzweg über `GlReadback`) und CPU (Kamerabild über denselben Weg lesen, `RgbaI420`); Ersatzweg und CPU-Umrechnung nur mit OpenGL, sonst bricht schon das erste leere Bild ab. `Fault` (READBACK, PACK, PASSES) nur für Selbsttests |
| `video/client/record/GlReadback` | video | eigener Framebuffer je Textur, einmal klassisch angehängt (ohne DSA, ohne Hilfs-Framebuffer des Encoders, ohne Pixelpuffer), synchrones `glReadPixels`; nur mit OpenGL |
| `video/record/ImageGuard` | video (reines Java, JUnit `video.record.ImageGuardTest`) | `blank(i420, format)` (Y-Ebene durchgehend 0; der Pack-Shader liefert immer Y ≥ 16), `zero(data, bytes)`, `check(quelle, leer)` → `Verdict` ACCEPT, DROP, SWITCH, ABORT; Reihenfolge GPU → READ → CPU → NONE, ohne Ersatzwege sofort NONE; leere Bilder kommen nie ins Video |
| `video/record/ImagePath` | video | GPU, READ, CPU, NONE; `key()`, `replacement()` |
| `video/record/RgbaI420` | video (JUnit `video.record.RgbaI420Test`) | `convert(rgba, breite, höhe, out)`: CPU-Umrechnung RGBA → I420 byte-gleich zum Pack-Shader `action_cam_yuv` |
| `video/record/RecordingDiagnosis#image`, `#withImage` | video (JUnit `video.record.RecordingDiagnosisTest`) | Bildweg der Aufnahme (GPU, Ersatzweg, CPU, abgebrochen), in `describe()` als „Bildweg …“ außer bei GPU |
| `video/client/record/ActionCamRecorder#finishedDiagnosis`, `#imagePath`, `#imageGuard`, `StopReason.BLANK_IMAGE` | video | Prüfung je Bild, Umschalten mit Chat-Meldung einmal je Sitzung und Weg, HUD-Zeile in `RecordingHud` während der Aufnahme, Logzeile mit Grafikkarte; liefert kein Weg ein Bild: Toast „Aufnahme abgebrochen“ und rote Chat-Meldung, ohne ein einziges gutes Bild wird der Job verworfen (keine Datei, kein Vorschaubild) |

Selbsttest `video.client.record.ActionCamImageSelfTests` (`video: Action-Cam-Bildweg …`, angemeldet in
`ActionCamSelfTests` nach dem Fisheye-Schritt): normale Aufnahme bleibt auf dem GPU-Weg, Ersatzweg und CPU-Umrechnung
im selben Bild byte-gleich, Fehler-Injektion je Weg mit Umschalten bzw. Abbruch. Die Bildprüfung der Videoeffekte
(`VideoSelfCheck`) und die Scheinwerfer-Prüfung lesen noch über `copyTextureToBuffer`.

### Kurs-Elemente sind fest (race; 0.6.0, Spieler-Baustein `docs/race-topology.md` §6)
Behebt verschiebbare Gates (Befund GATEPUSH-1): Ein Kolben verschob ein Gate am Server ohne Lagepaket, neu verbundene
Spieler sahen es an der alten Lage, anwesende an der neuen, und `CourseRegistry#reconcile` übernahm die neue Lage in die
Kursdaten. Alle Kurs-Elemente (Gate, Wendeflagge, Slalomstange, Tauchwürfel, Gate-Stange, Freiform-Gate) sind
`GateEntity`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/GateEntity#getPistonPushReaction`, `#isPushedByFluid`, `#move(MoverType, Vec3)` | race | `IGNORE_ENTITY` auf beiden Seiten (auch Schleim- und Honigblock), `false`, leer; Explosion, Spieler, Mobs, Loren und Boote bewegen Elemente ohnehin nicht (`ignoreExplosion`, `noPhysics`, `isPushable` `false`) |
| `race/GateEntity#tick` (Server) | race | jede Lageänderung am Server setzt `needsSync`, auch aus Befehlen oder Mods; der Typ ist `noUpdateInterval`, ohne das bliebe die Spawn-Basis im `ServerEntity` (`ClientboundAddEntityPacket`) auf der alten Lage |
| `race/GateEntity#readAdditionalSaveData`, `#restoreTo(x, y, z)` | race | aus Speicherdaten geladene Elemente rufen im ersten Server-Tick `CourseRegistry#restore` auf; `restoreTo` setzt die Lage und `needsSync` |
| `race/CourseRegistry#restore(GateEntity)` | race | steht das Element in einem Kurs derselben Dimension mehr als `MOVE_EPSILON` (1e-3) neben der Lage aus den Kursdaten, kommt es dorthin zurück (Kursdaten maßgeblich, keine neue Layout-Nummer), je Element eine Info-Zeile „Kurs n (Name): … ist zurückgesetzt“; sonst `false` |

Tests: Selbsttest `race.client.GatePushSelfTests` (`race-topo: Kolben`, angemeldet in `RaceSelfTests` nach
`RaceTopologySelfTests`) und Beitrittstest `PROPWASH_JOINTEST_GATEPUSH` (Server `dev.GatePushJoinCheck`, Client
`race.client.GatePushJoinProbe`). Schon von `reconcile` nachgezogene Kursdaten aus 0.5.2 oder einem dev-Stand lassen
sich nicht erkennen; solche Elemente bleiben an der verschobenen Lage, für alle Spieler gleich.

### Grafik-Kompatibilität und Ersatzwege (gpucompat, video, apiimpl, Loader; 0.6.0, Baustein `docs/graphics-compat.md`)
Gemeinsame Ursache der schwarzen Brille und der grünen Action-Cam-Aufnahme auf Intel-Grafik unter Windows: Der Treiber
meldet Direct State Access, einzelne DSA-Kopier- und Rücklesewege (`copyTextureToTexture`, `copyTextureToBuffer`) tun
still nichts. Ein Kompatibilitätsmodus erzwingt den Weg, den macOS ohnehin nimmt; eine Prüfung der Grafikwege liefert
allen Kopier- und Rücklese-Stellen den funktionierenden Weg. Ändert die beiden Abschnitte davor: `GlReadback` heißt
jetzt `gpucompat/client/ClassicReadback`, der Scheinwerfer kopiert per Shader, `VideoSelfCheck` und die
Scheinwerfer-Prüfung lesen über `GuardedReadback`.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `gpucompat/GpuCompatMode` (AUTO, ON, OFF, `#parse`), `GpuCompatConfig` | gpucompat (JUnit `gpucompat.GpuCompatSettingTest`) | Einstellung „Grafik-Kompatibilität: Automatisch/An/Aus“, gespeichert über `gpucompat/client/GpuCompatSettings` (`JsonConfig` `gpu-compat`) |
| `gpucompat/GpuCompatDecision#decide`, `#intel`, `#override`, `MODE_PROPERTY` (`propwash.gpuCompat`), `MODE_ENV` (`PROPWASH_GPU_COMPAT`), `SIMULATE_ENV` (`PROPWASH_GPU_COMPAT_SIMULATE=intel-windows`, nur Entwicklungsumgebung) | gpucompat (JUnit `gpucompat.GpuCompatDecisionTest`) | Automatisch = an bei Intel-Grafik unter Windows (auch Arc); Vorgabe über Eigenschaft bzw. Umgebung schlägt die Einstellung; Entscheidung einmal beim Start |
| `mixin/gpucompat/client/DirectStateAccessMixin`, `BufferStorageMixin` (`propwash.gpucompat.mixins.json`, angemeldet in `fabric.mod.json` und `neoforge.mods.toml`) | gpucompat | nur aktiv, wenn die Entscheidung an ist: `DirectStateAccess#create` nimmt `Emulated`, `BufferStorage#create` `Mutable`; sonst geben beide den Wert unverändert zurück; genau eine Logzeile „Grafik-Kompatibilität: …“ beim Start |
| `gpucompat/client/GpuCompatHooks#status` | gpucompat | nur aus den Mixins befüllt: Entscheidung, vom Treiber gebotene und gewählte Wege; `GpuCompatClient#state`, `#restartNeeded` zeigen Stand und Neustart-Hinweis im Tab „Konfiguration“, Abschnitt „Grafik“ (`ui/client/tab/SettingsTab`) |
| `gpucompat/GpuPaths` (`pattern`, `compare`, `Result`, `ReadPath` GPU/CLASSIC/NONE, `Health#readPath`) | gpucompat (JUnit `gpucompat.GpuPathsTest`) | Testbild 16×8 RGBA, Vergleich je Weg; Rücklesen über die GPU, sonst klassisch, sonst keiner |
| `gpucompat/client/GpuPathCheck#ensure`, `#recheck`, `#health`, `#announceBroken` | gpucompat | einmal je Sitzung beim ersten Bedarf vier Wege mit dem Testbild (Rücklesen GPU und klassisch, Kopie per Blit und per Shader), eine Logzeile mit Ergebnis und Grafikkarte; liefert kein Rückleseweg ein Bild, einmal je Sitzung eine rote Chat-Meldung |
| `gpucompat/client/GuardedReadback#read(texture, x, y, w, h, was, sink)`, `GuardedScreenshot#take` | gpucompat | lesen über den geprüften Weg; ohne Weg bekommt der Aufrufer `data == null` statt eines schwarzen Bildes; Foto der API (`apiimpl/client/RecordingControlImpl`, `#photoFailures`) speichert dann kein PNG und meldet „Foto nicht gespeichert …“ |
| `gpucompat/client/ClassicReadback` (früher `video/client/record/GlReadback`) | gpucompat | eigener Framebuffer je Textur, klassisch angehängt, synchrones `glReadPixels`; gemeinsam genutzt von Action-Cam (Ersatzweg READ) und Prüfung der Grafikwege; nur mit OpenGL |
| `video/client/HeadlightPass#shaderCopies`, `#blitCopies`; `video/client/VideoPass#simple` (öffentlich) | video | der Scheinwerfer kopiert über den Ersatz-Shader von `VideoPass` statt per Blit; Blit nur, wenn der Shader fehlt |

Tests: JUnit `gpucompat.*` (17); Selbsttest `gpucompat.client.GpuCompatSelfTests` und `video.client.CompatVideoSelfTests`
(Gruppe `compat:`), `video.client.record.CompatActionCamSelfTests` (angemeldet in `ActionCamSelfTests`), Fehler-Injektion
über `gpucompat.client.GpuFaults` und das Entwicklungs-Mixin `mixin/dev/client/GlCommandEncoderFaultMixin`. Nicht
abgedeckt: Vulkan, Vanillas F2-Bildschirmfoto (nur über den Kompatibilitätsmodus). Auf echter Intel-Hardware noch nicht
bestätigt (`docs/graphics-compat.md` §6).

### Zubehör nur an bestimmten Rahmen (build, content, parts, webexport; 0.6.0, API 1.7, Baustein `docs/accessory-fix.md` §4)
Behebt Befund APIFRAMES-1: Ein Add-on konnte ein Zubehör nicht auf bestimmte Rahmen beschränken, `DroneBuild#problems()`
prüfte nur die Halterung (`frame.accessoryMounts.contains(mount)`).
- `AccessoryDefinition#frames()` (letzte Record-Komponente, `Set<Identifier>`, sortiert und unveränderlich, leer = jeder
  Rahmen mit passender Halterung wie bisher, ein leerer Eintrag ist ein Fehler; Builder `frames(Identifier...)`, die
  Konstruktoren mit 12 bis 15 Argumenten bleiben). JSON `frames` in `propwash_parts/accessory/*.json` als Liste oder
  einzelne Id über `parts/JsonFields#optIdList`; `PartJson` schreibt das Feld nur, wenn es gesetzt ist.
- `build.AccessoryType#fitsFrame(PartInfo)` und `#frames()` → `DroneBuild.Issue.ACCESSORY_FRAME` (letzte Konstante, Text
  `propwash.build.issue.accessory_frame`), gemeldet direkt nach `ACCESSORY_MOUNT`. Es ist ein Kompatibilitätsproblem: Die
  Welt-Regel „Teile müssen zusammenpassen“ hebt es zur Warnung herab, `BuildSlots#affectedBy` = Rahmen und Zubehör.
  Werkbank, `builds().check`, `createDrone`, Presets, Importe, PW3 und Web-Export prüfen damit dasselbe.
- Werkbank: `BuildSlots.fitsFrame(part, frame, rule)` und `DroneWorkbenchMenu#fitsFrame` in `PartSlot#mayPlace` (Klick
  und Shift-Klick), Hinweis `DroneWorkbenchScreen#otherFramesHint` („Passt nicht an diesen Rahmen, nur an: …“), Tooltip
  „Passt zu“ über `PartTooltips.fitsFrame`/`fittingFrames`.
- Laden: Speicherstand, Netz und Sharecodes prüfen beim Zubehör nur die Halterung und verwerfen nichts; eine vor der
  Einschränkung gebaute Drohne behält das Zubehör, zeigt das Problem und fliegt bei aktiver Regel nicht
  (`parts_incompatible`), bis es in der Werkbank abgenommen ist.
- Web-Export: `webexport/RuleCheck` Regel `accessory_frame`, `webexport/PartData` `data.frames` nur bei eingeschränktem
  Zubehör.
- Wie bei `hangsStraight` und `muzzleMm` ändert die neue Record-Komponente den Teile-Hash im Handshake jedes Zubehörs.
  Tests: JUnit `accessory/fix/AccessoryFramesTest` (mit `FramesWorld`, `FramesRestart`), Selbsttest
  `interact.client.AccessoryFramesSelfTests` (Gruppe `addon-api-17: Zubehör-Befunde`, angemeldet in
  `AccessoryFixSelfTests`) mit dem Test-Zubehör `propwash_example:cine_gimbal`.
- Welt-Regel aus (DEC2810-2): an jedem Rahmen mit passender Halterung erlaubt; alle Stellen lesen die Regel über
  `WorldRules` (Werkbank, `ImportService`, `builds().check`/PW3, `createDrone`, `ServerRules.refusesParts`), ungefiltert
  nur `RuleCheck` (Web-Export ohne Welt-Regel). Tests:
  `AccessoryFramesTest#foreignFramesFollowTheWorldRuleInWorkbenchImportsAndPw3` (`FramesWorld.worldRule`), dritter
  Schritt in `AccessoryFramesSelfTests`.

### Geister und Replays der Bestenliste (race; 0.6.0, Spieler-Baustein `docs/race-ghosts.md`, Owner-Entscheidung vom 25.10.2026)
Der Server zeichnet für jede gespeicherte Bestenlisten-Zeit (Bestzeit je Pilot, Klasse und Regel-Satz) seine eigene Spur
auf und legt sie mit dem Sharecode der Drohne ab; die Bestenliste zeigt je Zeile Sharecode, Replay und Geist. Seit
Netzprotokoll 20 ersetzt die Aufnahme des Piloten (10-ms-Raster) diese Spur, wenn sie zur Spur des Servers passt
(Befund OT4-2, `docs/race-ghosts.md` „Woher die Spur kommt“); die Zeit bleibt immer die des Servers. Ersetzt
den Rekord-Geist (`RaceConfig.ghost`/`recordGhost`, `GhostRequestC2S` → `GhostS2C` im Abschnitt „Geister-Rennen“ gelten
nicht mehr; `race.RecordGhosts` bleibt nur als Quelle der Übernahme und wird nicht mehr beschrieben).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/GhostKey` | race (reines Java) | Pilot, Klasse, Zeit, Datum einer Zeit; `fileName()` = `<uuid>_<klasse>_<zeit hex>_<datum hex>.pwghost` (Doppelpunkt der Klasse als `~`), `matches(Entry)`, `playerOf(name)` |
| `race/EntryGhost` | race (reines Java, JUnit `race.EntryGhostsTest`) | `Info` (Pilot, Name, Klasse, Zeit, Datum, Flughilfe, Layout = `layoutHash`, Kamerawinkel oder NaN, Sharecode höchstens `MAX_CODE` = 4096, Herkunft `SERVER`/`RECORD_GHOST`/`PILOT`, unbekannte Werte lesen als `SERVER`) + Spur im Format `PWG1`; `encode`/`decode` Deflate mit Kopf `PWE1` Version 1, höchstens `MAX_BYTES` (528 KiB), entpackt begrenzt, kaputte Daten werfen `IOException`; `info(data)` liest nur den Kopf |
| `race/EntryGhosts` | race (JUnit `race.EntryGhostsTest`) | Ablage `<welt>/propwash/race-data/ghosts/<kurs-id>/`, eine Datei je Zeit, atomar geschrieben; `put`, `get`, `read`, `info` (LRU-Zwischenspeicher der Köpfe, 4096), `prune(kurs, behalten)`, `archive(kurs, layout, behaltene Layouts)` nach `archive/<layout>/`, `removeCourse`, `log(zeile)` an `race-data/removals.log`; `disabled()` ohne Ordner |
| `race/CourseRegistry#entryGhosts`, `#useEntryGhosts`, `#pruneGhosts`, `#migrateRecordGhosts`, `#removeTimes` | race (JUnit `race.data.LeaderboardGhostsTest`) | `start` öffnet die Ablage unter `RaceDataMirror.root` und übernimmt Rekord-Geister, deren Zeit (Klasse, Zeit, Name) noch in der Liste steht und deren Layout passt; jedes `submitTime`/`submitLap`/`submitAssistedLap`/`resetLeaderboard` räumt Geister ohne gespeicherte Zeit weg; Layout-Wechsel archiviert wie die Liste, ohne archivierte Liste wird geräumt; Kurs löschen oder `removeUnusedCourses` löscht den Ordner; `removeTimes(kurs, pilot, klasse|null, Section)` löscht Zeiten, Rekord-Geist und Geister |
| `race/Leaderboard#removeTimes`, `Section` (ALL/CLEAN/ASSISTED), `#pilotNames`, `#playerNamed`, `clipFrame` | race | Abschnitt nach `Scoring#clean` gegen den aktuellen Standard, danach `rebuild` (Plätze und Rekorde rücken nach) |
| `race/GhostRecorder#cut(…, intervalNanos)`, `#steady` | race (JUnit `race.GhostFineTrackTest`) | Server-Schnitt im Raster `GhostServer.FINE_INTERVAL_NANOS` (20 ms); `steady` glättet schwankende Ankunftszeiten der Bewegungspakete je Abschnitt ohne Lücke über 200 ms (gleitende Gerade über 9 Proben), streng steigend |
| `race/GhostTrack#resample(…, intervalNanos)`, `#intervalFor`, `#smoothPose` | race | Abstand wächst, wenn `MAX_SAMPLES` sonst überschritten würde; `smoothPose` = Catmull-Rom auch für die Lage (Vorzeichen-Kontinuität, normiert) für das Replay |
| `race/GhostServer` | race | `store` nach jeder gespeicherten Zeit (`Leaderboard.Result#stored`, auch mit Flughilfe): Spur aus den eigenen Bewegungspaketen, Aussehen, Sharecode (`ShareService.encode`, `ShareLayer.DEFAULT_EXPORT`, mit Add-on-Infos) und Kamerawinkel (`CameraMount.uptilt`) der gesteuerten Drohne, nur wenn ihre Klasse passt; `decorate` hängt Pilot-UUID, Code und `ghost` (Geist da und Layout passt) an die Zeilen; Abruf `GhostFetchC2S` mit Zweck REPLAY/OPPONENT: höchstens 6 je 10 s, Zeit muss in der Liste stehen, Rechte über `GhostAccess`, Layout muss passen; Antwort `GhostChunkS2C` in Stücken zu 24 KiB, höchstens 2 Stücke und 96 KiB je Tick und Spieler, Warteschlange 2 Abrufe; `TimeRemoveC2S` und `/propwash race remove <kurs> <pilot> [klasse]` (Befehl ab Rechtestufe 2) nur für Operatoren (`CourseRegistry.isOperator`, Gastgeber zählt mit), Protokollzeile `… removed by=… via=window|command course=… key=… pilot=…(uuid) class=… section=… times=… best=…` |
| `race/GhostAccess` | race (reines Java, JUnit `race.GhostAccessTest`) | Welt-Regel `propwash:foreign_ghosts` (`WorldRuleSpecs.FOREIGN_GHOSTS`, Stufen 0 Aus, 1 Admins, 2 Alle = Standard, Gruppe Spiel, rennneutral): eigene Zeiten immer; Alle → jeder; Admins → nur Operatoren; Aus → Replays nur für Operatoren, Gegner niemand. `flags` → `LeaderboardS2C#access` (Bits REPLAY, OPPONENT, REMOVE) |
| `race/RacePayloads` | race | `BoardRow` + `player`, `code`, `ghost`, `key()`; `LeaderboardS2C` + `access`; neu `GhostFetchC2S` (`propwash:race_ghost_fetch`), `GhostChunkS2C` (`propwash:race_ghost_chunk`, Status DATA/MISSING/DENIED/BUSY), `TimeRemoveC2S` (`propwash:race_time_remove`); `GhostRequestC2S`/`GhostS2C` entfallen. Seit Netzprotokoll 20 (Befund OT4-2): `GhostUploadS2C` (`propwash:race_ghost_upload_request`: Auftrag, Kurs, Runde, Rundenzeit) und `GhostUploadC2S` (`propwash:race_ghost_upload`: Auftrag, Stück, Stücke, Gesamtgröße, Daten bis `CHUNK` = 8 KiB, höchstens `MAX_BYTES` = 512 KiB; `parts` = 0 heißt „habe die Runde nicht“) |
| `race/GhostRecorder#keptLap(kurs, rundenzeit)`, `keptLaps`, `UPLOAD_INTERVAL_NANOS`, `KEPT_LAPS`, `window(von, bis)` → `Raw` | race (JUnit `race.GhostUploadCheckTest`) | `onLapComplete` (nur Client) legt jede Runde zusätzlich im Raster 10 ms aus den Rohproben (ohne `steady`) ab, die letzten 4, gefunden über Kurs und Rundenzeit; `reset` leert sie. `window` kopiert die Rohproben des Servers (Ankunftszeiten, Positionen) für die Prüfung |
| `race/GhostUploadCheck` | race (reines Java, JUnit `race.GhostUploadCheckTest`) | `Reference.of(raw, start, ende, server-spur, gates, rundenzeit, strafe, ping)`: Rohproben relativ zum Start, Serverzeit der Runde, Splits der Server-Spur (`LENIENT` = untere Öffnung bei jedem Gate), Ping höchstens `MAX_PING_NANOS` (300 ms); Toleranz `tolerance()` = 100 ms + Ping + 2 × Median-Abstand der Rohproben, höchstens 1,5 s. `check(ref, spur, gates, punktZuPunkt)` → `ACCEPTED` oder der erste Grund: `FORMAT` (Abstand 5–50 ms, endlich, Vorlauf ≤ 1 s + Abstand), `DURATION` (Dauer = Rundenzeit − Strafe ± 2 Abstände + 5 ms), `SPEED` (je Probe höchstens 165 Blöcke/s + 0,1), `START` (Startgate nahe der Server-Startzeit), `GATES` (jedes Gate, das die Server-Spur hat, in Reihenfolge, zeitlich innerhalb Toleranz + größtem Spielraum `Reference#staleness` der Serverproben dort, Befund G-OT4-2-2), `FINISH`, `LENGTH` (95–125 % ± 5 Blöcke der Server-Strecke), `PATH` (jede Serverprobe höchstens 1,5 Blöcke neben der Spur im Zeitfenster, das um `Reference#staleness` nach hinten wächst: die eigene Pause davor oder die größte Pause, die höchstens ihre eigene Länge (bis 1,5 s) zurückliegt, Befund AUTO68-2; jede Probe der Spur höchstens 1,5 Blöcke + halbe Sehne neben der Server-Spur, Fenster je Stück um den größeren Spielraum seiner Enden; je 2 % Ausreißer, mindestens 1; Ausreißer der Serverproben über 400 ms am Stück lehnen ab, `MAX_OUTLIER_RUN_NANOS`, Befund G-OT4-2-2). Zeitbezug: Ende auf Ende, dann bestes Versatz-Raster 10 ms über den Median der Abstände (Bereich Toleranz + `RaceServer.crossingTolerance`) |
| `race/GhostUploads` | race | Server: `expect` nach jedem `GhostServer.store` (aus `RaceServer.storeGhost`, nur wenn der Client den Kanal kennt), höchstens 2 offene je Spieler, Frist 120 s; `receive` setzt Stücke in Reihenfolge zusammen (falsche Reihenfolge/Größe, Absage oder mehr als 30 Stücke je Sekunde beenden den Upload), dann `GhostCodec.decode` und `GhostUploadCheck`; nur wenn die gespeicherte Datei noch die Herkunft `SERVER` und das Layout passt, ersetzt `merge(server, upload)` die Spur (Proben, Abstand, Vorlauf höchstens wie die Server-Spur vom Upload; Rundenzeit, Klasse, Layout, Aussehen, Regeln vom Server) mit Herkunft `PILOT`. `accepted`/`rejected`/`last` für Tests; `clear`/`leave`/`tick` aus `RaceServer` |
| `race/client/GhostUploader` | race | Client: `request(GhostUploadS2C)` sucht `ClientRaceTracker#recorder().keptLap`, sonst Absage; `tick` (TICK_END) schickt je Tick ein Stück, im Rennen (`LapTracker#racing`) nur jeden 4. Tick; `clear` beim Trennen |
| `race/GhostChoice` | race (reines Java, JUnit `race.GhostChoiceTest`) | Modus OWN/BOARD/OFF, bei BOARD optional die gewählte Zeit (`GhostKey` + Name; ohne = Kursrekord der Klasse); JSON je Kurs; `migrateConfig` macht aus `ghost`/`recordGhost` den Wert `ghostMode` (an → OWN, nur Rekord → BOARD, beide aus → OFF) |
| `race/client/RaceConfig#ghostMode` | race | Tab „Rennen“ → „Geist: eigener / aus Bestenliste / aus“, Migration über `JsonConfig.create(…, GhostChoice::migrateConfig)` |
| `race/client/GhostChoices` | race | Wahl je Kurs unter `<spiel>/propwash/ghosts/<sp_ordner\|mp_adresse>/choices.json`; ohne Eintrag gilt `RaceConfig#ghostMode`; `choose`, `chosen`, `forget` |
| `race/client/GhostDownloads` | race | Client-Seite des Abrufs: Anfrage-Nummern, Stücke in Reihenfolge, Gesamtgröße, Zeitlimit 30 s, Ergebnis `LOADED`/`MISSING`/`DENIED`/`BUSY`/`BROKEN`/`TIMEOUT`/`OFFLINE`; die letzten 4 geladenen Geister zwischengespeichert, `retain` mit den Zeilen jeder neuen Bestenliste |
| `race/client/GhostLibrary` | race | genau ein Geist: `shownKind()` NONE/OWN/ASSISTED/BOARD, `shown()`; BOARD lädt die gewählte Zeit oder den Klassenrekord aus der Bestenliste (Bestenliste bei Bedarf alle 20 s neu angefordert), fällt ohne Geist auf den eigenen zurück; `referenceSplits` folgt dem gezeigten Geist (bei „aus“ die eigene Bestrunde); `record()`/`recordLapNanos()` = Geist aus der Bestenliste |
| `race/client/GhostRenderer` | race | zeichnet nur `shown()` (eigener blau, mit Flughilfe violett, Bestenliste golden) und im Replay „hinter der Drohne“ den Replay-Geist golden |
| `race/client/LeaderboardScreen` | race | Zeile anklicken wählt sie (`select`, `selected`, `rowAt`, `rowY`); Leiste über der Fußzeile: Code (Hover = `ShareChat.tooltip`, Klick kopiert), Kopieren, Importieren (`ShareChat.open`), Replay, Gegen fliegen/Eigener Geist, für Operatoren Zeit löschen (zweiter Klick binnen 3 s); Marke „Geist“ an der gewählten Zeit |
| `race/client/ReplayScreen`, `ReplaySession` | race | Abruf mit Zweck REPLAY, dann Wiedergabe über `FRAME_START`; Pose aus `GhostTrack#smoothPose`; FPV = Linsenversatz und Fußpunkt aus `AirframeSpec.of(look.build())`, Lage × `rotateX(Kamerawinkel)` (ohne Angabe 20°), Sichtfeld aus `VideoConfig#horizontalFov(null, build.video())` ohne Fisheye; „hinter der Drohne“ 1,6 Blöcke hinter, 0,5 über dem Schwerpunkt, Blick auf die Drohne, Richtung aus der Bewegung (Fenster ±150 ms, ab 1 m/s, sonst Nase); HUD per `Hud#toggle` aus und wieder an; Tempo 0,25–4×, Spulen 5 s, Zeitleiste mit Gate-Marken aus `GhostTrack#splits` |
| `race/ReplayMarks` | race (reines Java, JUnit `race.ReplayMarksTest`) | „Gates: N“ im Replay ist die Gate-Zahl des Kurses (`CourseData#gates`, ohne Kurs `LeaderboardS2C#gates`); Zeitleiste je Gate eine Marke: Startgate bei 0, dann `GhostTrack#splits`, bei Punkt zu Punkt das Ziel an der Laufzeit; grün = `startOrFinish`. Fehlt ein Durchgang in der Spur, fehlt nur dessen Marke |
| `race/client/ReplayVideo`, `race/ReplayExport`; `video/client/record/ActionCamRecorder#startReplay`, `#finishReplay`, `#cancelReplay`, `#replayFormat`; `ReplaySession#beginExport`, `#endExport` | race, video (JUnit `race.ReplayExportTest`, Selbsttest `race.client.ReplayVideoSelfTests`) | Knopf „Als Video aufnehmen“ im `ReplayScreen`: Aufnahme ohne Drohne über den Rekorder der Action-Cam (`launch` teilt der Rekorder mit der Live-Aufnahme), Format und Qualität aus `RecordingConfig` mit `ActionCamSpec.HEAVY` als Obergrenze, keine Stabilisierung, kein Ton; Datei `propwash_replay_*.mp4` in `ActionCamRecorder.directory`. Die Wiedergabezeit folgt der Aufnahme-Uhr (`recordedNanos`, `ReplayExport.time`), Video und Spur bleiben auch bei niedriger Bildrate im Takt. Knopf aus ohne ffmpeg (`ReplayExport.ffmpeg`, Status mit `Settings(enabled = true)`, unabhängig von der Encoder-Wahl). `stop(USER)` von außen (Taste, API) greift nicht in die Replay-Aufnahme ein |
| `mixin/race/client/CameraMixin` | race | nach `Camera#alignWithEntity` in `Camera#update`: Pose aus `ReplaySession#cameraPose`; nach dem Setzen von `fov`: Sichtfeld des Replays. Replay nicht während FPV, Freecam oder Zuschauen (`FpvView.active()`) |
| `race/GhostTrack#leadNanos`, `#leadSamples`, `#beginNanos`, `#clamp`, `#lapClock`, `#withoutLead`; `GhostRecorder.LEAD_NANOS`, `#leadBefore`, `#cut(…, intervalNanos, leadNanos)`; `GhostCodec.VERSION_WITH_LEAD` | race (JUnit `race.GhostLeadTest`) | Vorlauf bis 1 s vor dem Startgate (Befund REPLAY1-1): die ersten `leadSamples` Proben liegen vor dem Gate, Zeitachse bleibt Rundenzeit (`pose`/`smoothPose` ab `-leadNanos`, `durationNanos`, `splits` ab dem Gate); Vorlauf = Vielfaches des Abstands, nur über lückenlosen Verlauf (Abstand ≤ 200 ms, Sprung ≤ `LapTracker.MAX_SEGMENT`), nur innerhalb `MAX_SAMPLES`. Client behält die Sekunde vor Rundenstart und Rundenende, Server nimmt Bewegungspakete auch außerhalb eines Rennens (letzte Sekunde) und schneidet mit `LEAD_NANOS`. Kodierung Version 4 nur bei Vorlauf > 0 (VarLong nach dem Regelsatz), sonst wie bisher; über `MAX_ENCODED_BYTES` ohne Vorlauf. `ReplaySession` startet bei `begin()` = −Vorlauf, `lapTime` = `lapClock` (0 bis zum Gate); Gegner im Rennen (`GhostRenderer`) nur ab Rundenzeit 0. Selbsttest „race: Replay beginnt vor dem Startgate“, Bild `race_replay_lead` |
| `race/LapVerifier.VerifiedLap#starts`, `#startingAt`; `RaceServer#verifiedLap(verifier, kurs, prüfpunkte, punktZuPunkt, nach, ersteRunde, startMeldung, geflogen, strafe)`, `#timedStart(runde, geflogen, frühestens, boden)` | race (reines Java, JUnit `race.StartGateAgainReplayTest`, `race.LapParityTest`) | Erneuter Durchflug durch das Startgate (Befund OT10-1): Der `LapTracker` des Clients startet die Uhr am ersten Durchflug, ein weiterer vor Gate 1 ist „ausgelassen“; `LapVerifier.find` nimmt dagegen den **letzten** Start-Durchflug vor Gate 1 (`startNanos`, unverändert) und nennt in `starts` alle Start-Durchflüge, mit denen dieselbe Kette gilt (aufsteigend, ab `afterSequence` bzw. Zeitfenster, ohne das Ereignis von Gate 1). `RaceServer.lap` sucht über `verifiedLap` (Suche wie bisher samt Überspringen alter Runden) und nimmt mit `timedStart` den Start, dessen Server-Zeit der gemeldeten Flugzeit am nächsten liegt, nur unter den Starts, die `crossingTolerance` bestehen (gleich weit: der spätere; ohne passenden: der letzte, die Runde fällt wie bisher durch). Welche Zeiten angenommen werden, bleibt gleich; Spur des Servers (`storeGhost`), `GhostUploadCheck.Reference` und das Fenster der Flughilfen (`lapVerdict`) beginnen am Start der Uhr. Bei Fehlstart gilt für den Vergleich der Boden Startmeldung − `START_SLACK_NANOS` wie bisher |

Tests: JUnit `race.GhostUploadCheckTest` (Spur des Piloten, Befund OT4-2), `race.EntryGhostsTest`, `race.GhostAccessTest`, `race.GhostChoiceTest`, `race.GhostFineTrackTest`,
`race.data.LeaderboardGhostsTest`, Eintrag in `race.RaceRulesTest`; Selbsttest `race.client.RaceGhostsSelfTests`
(Gruppe `race:`, angemeldet in `RaceSelfTests` nach „Bestenliste im Theme-Fenster“, Bilder `leaderboard_share_code`,
`race_replay_fpv`, `race_replay_chase` mit `-PpropwashScreenshots=true`); Beitrittstest `PROPWASH_JOINTEST_RACE_GHOSTS`
(Server `dev.RaceGhostsJoinCheck`, Client `race.client.RaceGhostsJoinProbe`). Selbsttest „race: Replay ohne Standbilder bei
schlechter Verbindung des Piloten“ (`race.client.RaceGhostsNetSelfTests`, nach den übrigen Schritten von `RaceGhostsSelfTests`):
eigener Kurs mit zwei Gates, der Client nimmt die Runde wie beim Fliegen auf, der Server bekommt die Bewegungspakete mit
`dev.BadNetwork.PILOT` (120 ms Latenz, 80 ms Jitter, 5 % Verlust mit Neusendung nach 300 ms, Server-Takt 50 ms); das Replay
der Bestenliste (über das Netz abgerufen) muss nach `dev.FreezeMeter` (Standbild = ab 50 ms unter 25 % des erwarteten Wegs,
Sprung = über das Dreifache) glatt sein und die Herkunft `PILOT` haben.
Befund OT9-1: `ClientRaceTracker` gibt dem Rekorder je Bild Position und Lage der gesteuerten Drohne aus der eigenen
Simulation (`QuadcopterEntity#renderAttitude(1, …)`, bei lokalem Zustand die Lage, die `PilotSession` im selben Bild schrieb); `orientation()` sind
die Entity-Daten vom Server, je Tick und um die Laufzeit verspätet, und gaben Replay, Video und Geist Stufen im
20-Hz-Takt. Beim Video folgt `ReplaySession` im Export `ActionCamRecorder#replayFrameNanos` =
`FrameClock#nextSlotNanos(jetzt)` (Zeit des nächsten Bildplatzes, mindestens letzter Platz + 1), damit jedes Videobild
genau seine Zeit zeigt, auch bei Bildraten des Spiels, die kein Vielfaches der Videorate sind. Tests: JUnit
`race.ReplaySmoothTest`, Selbsttest „race: Replay glatt bei 60 fps …“ (`race.client.ReplaySmoothSelfTests`, Messung
`dev.CameraPathMeter`), siehe `docs/testing.md`.
Selbsttest „race: Replay nach erneutem Durchflug durch das Startgate beginnt mit der Uhr“ (`race.client.RaceStartAgainSelfTests`,
angemeldet in `RaceGhostsNetSelfTests.register`, Befund OT10-1): eigener Kurs mit drei Gates, Flug durch Start, Gate 1,
Kehre zurück und nach 5,5 s noch einmal durch das Startgate, dann die Runde; Uhr und Aufnahme aus einem eigenen
`LapTracker` wie im `ClientRaceTracker`, Server-Flug mit 100 ms Latenz; geprüft werden gewertete Zeit = Uhr, Spur des
Servers (Dauer ± 60 ms, Weg ab dem ersten Durchflug ± 1 Block), Upload `ACCEPTED` mit Herkunft `PILOT`, im echten
`ReplayScreen` Dauer = Zeit ± 2 Abstände + 5 ms, Uhr 0 am Startgate und am Ende genau die Zeit der Bestenliste.

### Kreativ-Tab ohne doppelte Add-on-Items (content; 0.6.0, Baustein `docs/accessory-fix.md` §5)
Behebt Befund OWNP6-3: Items eines Add-ons mit eigenem Kreativ-Tab, die es zusätzlich über `PropwashApi.creativeTab()`
anmeldete, standen doppelt im eigenen Tab und im Propwash-Tab.
- Der Propwash-Kreativ-Tab übernimmt `creativeTab()`-Einträge sowie Teil-Items und Presets im Rückfall nur von
  Namensräumen ohne eigenen Tab; bei `creativeTab()`-Einträgen zählt der Namensraum der Registry-Id des Items.
- Ob ein eigener Tab besteht, entscheidet `content.CreativeTabNamespaces#ownTab(registrierte Tabs, extensionTab)`: ein
  registrierter Tab `propwash:main-<ns>` (`ExtensionTabs.PATH_PREFIX`), irgendein Tab `<ns>:…` im Register der
  Kreativ-Tabs oder `ExtensionTabs#hasTab(ns)`. `propwash` und `minecraft` zählen nie. `ContentTab.contents()` baut die
  Prüfung je Aufruf einmal aus `BuiltInRegistries.CREATIVE_MODE_TAB`.

Tests: JUnit `accessory.fix.CreativeTabNamespacesTest` (5); Selbsttest `interact.client.AccessoryTabSelfTests` (Gruppe
`addon-api-17: Zubehör-Befunde`, angemeldet in `AccessoryFixSelfTests`): 18 Teil-Items des Beispiel-Add-ons zusätzlich
über `creativeTab()`, keines im Propwash-Tab, je ein Kontroll-Eintrag aus `propwash` und `minecraft` bleibt.

### Statistiken im Vanilla-Statistikfenster (stats, race; 0.6.0, Spieler-Baustein `docs/pilot-stats.md`)
Neues Modul `stats` (Common `stats.StatsModule` am Ende von `PropwashCommon.init()`, Client `stats.client.StatsClient`
am Ende von `PropwashClient.init()` vor `DevExtensions.client()`), Sprachquellen `common/src/lang/pw-stats/`. Eigene
Statistiken im Vanilla-Register `minecraft:custom_stat` (Ids `propwash:<name>`, Texte `stat.propwash.<name>`, Kriterium
der Anzeigetafel `minecraft.custom:propwash.<name>`), gezählt nur am Server und nur für den Piloten der Drohne.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `stats/StatNames` | stats (reines Java) | `FLIGHT_TIME` `flight_time`, `DISTANCE` `flight_distance`, `TOP_SPEED` `top_speed`, `CRASHES` `crashes`, `BATTERIES_DEPLETED` `batteries_depleted`, `ARMS` `arms`, `LAPS` `laps`, `RACES_FINISHED` `races_finished` (= `COUNTERS`, in dieser Reihenfolge), dazu `classTime(FrameRole)` = `flight_time_<role.key()>` je `FrameRole` in Enum-Reihenfolge; `all()` (16 Namen, Registrierungsreihenfolge), `translationKey(name)` = `stat.propwash.<name>` |
| `stats/PropwashStats` | stats | `PropwashRegistries.list(Registries.CUSTOM_STAT)`, Wert = die `Identifier`-Instanz aus dem Schlüssel; `Entry(name, holder, formatter)` mit `id()`, `translationKey()`, `stat()` (= `Stats.CUSTOM.get(holder.get(), formatter)`; der Typ hält Statistiken in einer `IdentityHashMap`, deshalb immer genau die Instanz aus dem Register); Formate: Flugzeiten `StatFormatter.TIME` (Ticks), Strecke `DISTANCE` (cm), Höchsttempo `StatFormats.SPEED` (Zehntel km/h, „123.4 km/h“), Zähler `DEFAULT`; `classTime(role)`, `all()`; `bind()` legt alle Statistiken mit ihrem Format an, bevor Vanilla sie aus Spielerdateien oder dem Statistik-Paket mit `DEFAULT` anlegen könnte: bei `PropwashEvents.REGISTRY_FROZEN` (Fabric `SERVER_STARTING`/`CLIENT_STARTED`, NeoForge `FMLCommonSetupEvent`) und zur Sicherheit bei `ServerBus.STARTED`; ein schon anders angelegtes Format meldet eine Warnung |
| `stats/StatClass` | stats (reines Java) | Drohnenklasse = `FrameRole` des Rahmens (`frame.definition().role()`), Add-on-Rahmen über ihre JSON-Rolle; ohne Rahmen `FALLBACK` = `FREESTYLE` |
| `stats/StatFormats` | stats (reines Java) | `SPEED`, `speed(zehntel)`, `tenthsKmh(m/s)` (nicht endlich oder ≤ 0 → 0) |
| `stats/FlightTally` | stats (reines Java) | Zählung einer Drohne je Pilot: `update(tick, scharf, blöcke, m/s, akku)` → `Step(ticks, centimeters, armed, depleted, topSpeed)`. Zeit und Strecke nur für Abschnitte, die scharf beginnen, Zeit = Server-Ticks seit dem letzten Paket (mehrere Pakete im selben Tick zählen einmal, Lücken über `MAX_GAP_TICKS` 20 gar nicht), Strecke mit Rest unter 1 cm, Sprünge über `MAX_STEP_BLOCKS` 64 nicht; Scharfschaltung = Wechsel unscharf → scharf (eine scharf übernommene Drohne zählt nicht); leerer Akku = Ladestand > 0 → ≤ 0 (NaN = 0), scharf vorher oder nachher; Höchsttempo nur scharf, meldet nur neue Höchstwerte der Sitzung |
| `stats/CrashBudget`, `stats/CrashWatch` | stats (reines Java) | Server: höchstens 8 Crashes am Stück, danach einer je 5 Ticks; Client: `observe(aktiv, drohnenId, crashCount)` liefert neue Crashes einer Sitzung (neue Sitzung oder andere Drohne zählt ab 0) |
| `stats/PilotStats` | stats | Server-Thread: `QuadcopterEntity.PILOT_CHANGED` legt je Pilot eine `FlightTally` an (Zustand der Drohne beim Zuweisen) bzw. entfernt sie; `QuadcopterEntity.SERVER_MOVED` (nur angenommene Pakete des Piloten aus `DronePiloting.onState`) zählt mit `from.distanceTo(to)`, dem Tempo `getDeltaMovement() × 20` (= Tempo des Pakets) und `batteryCharge()`: Flugzeit gesamt und je Klasse, Strecke, Scharfschaltungen, leere Akkus, Höchsttempo (nur höher, als Differenz über `awardStat`, damit die Anzeigetafel mitzieht); `CrashC2S` nur vom Piloten der Drohne, mit `CrashBudget` je Spieler; `lap(pilot)`, `raceFinished(pilot)`, `value(spieler, entry)`, `tracking(drohne)` |
| `stats/net/CrashC2S` | stats | `propwash:stats_crash` (Entity-Id, Anzahl als VarInt); Anzahl ≤ 0 verworfen, über `MAX_CRASHES` 8 gekürzt. Neuer Payload: Protokollnummer setzt die Integration |
| `stats/client/StatsClient` | stats | `ClientBus.TICK_END`: neue Crashes der laufenden `PilotSession` (`crashCount()`, dieselbe Zahl wie im OSD) als `CrashC2S`; `sentCrashes()` für Tests |
| `race/RaceServer` | race (zwei Zeilen pw-stats) | `PilotStats.lap(player)` in `lap(...)`, sobald die Runde vom Server bestätigt ist (saubere und gestützte Runden, auch der Lauf auf Punkt-zu-Punkt-Kursen); `PilotStats.raceFinished(player)` in `finish(...)` vor `RaceDataEvents.race` |

- Keine Doppelzählung: Freecam, Geister und Replays fliegen ohne Entity und schicken keine Bewegungspakete; Server-Flüge
  (Heimflug, Add-on-Autonomie) laufen nur ohne Pilot; Resets, Teleports und vom Server gesetzte Positionen gehen nicht
  über `SERVER_MOVED`. Zuschauer und zweiter Operator sind nie Pilot.
- Keine Bestzeiten in den Statistiken (Owner-Entscheidung vom 27.10.2026): reine Zähler.

Tests: JUnit `stats.FlightTallyTest` (7), `stats.CrashCountTest` (2), `stats.StatClassTest` (4, Klassenzuordnung und
Sprachdateien); Selbsttest `stats.client.StatsSelfTests` (Präfix `stats:`, 8 Schritte, mit `-PpropwashScreenshots=true`
zusätzlich `stats_general_en.png` und `stats_general_de.png`); Beitrittstest `PROPWASH_JOINTEST_STATS`
(`dev.StatsJoinCheck`, `stats.client.StatsJoinProbe`).

#### Fortschritte im Tab „Propwash FPV“ (stats; 0.6.0, Fix FB1-14, Spieler-Baustein `docs/pilot-stats.md` §4)
Fest in der Mod: `data/propwash/advancement/fpv/*.json` (Wurzel `propwash:fpv/root` mit Hintergrund
`propwash:gui/advancements/backgrounds/propwash_fpv`, 16×16-Kachel, und 27 Fortschritte), Texte
`advancements.propwash.fpv.<name>.title|description` in `common/src/lang/pw-stats/`. Kein `minecraft:impossible`: eigene
Kriterien-Auslöser im Vanilla-Register `minecraft:trigger_type` (`PropwashRegistries.list(Registries.TRIGGER_TYPE)`,
beide Loader über `RegList`), Bedingungen stehen in den Dateien.

| Datei | Besitzer | Vertrag |
|---|---|---|
| `stats/advancement/PropwashTriggers` | stats | `propwash:milestone` (`{"milestone": <Milestone>}`), `propwash:flight` (`distance` m gesamt aus der Statistik Flugstrecke, `altitude` Y der scharfen Drohne, `dive` Blöcke, je `MinMaxBounds.Doubles`; `crashes` gesamt, `Ints`), `propwash:trick` (`tricks`: Liste von `TrickType.key()`, `combo`: `Ints`), `propwash:flight_school` (`chapters`: `Ints`, `perfect`: bool), `propwash:cargo_delivered` (`distance` waagerecht in Blöcken), `propwash:drone_strike` (`entity`: Loot-Bedingung wie bei `player_killed_entity`); fehlende Werte (NaN) erfüllen keine Grenze; alle Bedingungen optional, zusätzlich `player` wie bei Vanilla |
| `stats/advancement/Milestone` | stats | `drone_built`, `takeoff`, `fpv_flight`, `battery_swapped`, `battery_charged`, `lap`, `personal_best`, `ghost_beaten`, `own_course`, `drone_painted`, `sharecode_loaded`, `spectated` |
| `stats/advancement/FlightFeats` | stats (reines Java) | je Drohne: Abheben = scharf und ≥ `TAKEOFF_HEIGHT` 1 Block über der Höhe beim Scharfschalten, einmal je Scharfschaltung; Sturz = Gipfel − Tiefpunkt seit dem letzten Steigflug über `RECLIMB` 3 Blöcke; unscharf → NaN |
| `stats/advancement/TrainingTally` | stats (reines Java) | abgeschlossene Kapitel (`Curriculum.chapterComplete`) und `perfect` = alle Sterne; der Server kürzt die Kapitelzahl auf `Curriculum.chapters().size()` |
| `stats/advancement/FpvAdvancements` | stats | Server: `PropwashEvents.DRONE_ASSEMBLED` → `drone_built`, `BUILD_IMPORTED` mit `APPLIED` → `sharecode_loaded`, `QuadcopterEntity.SERVER_MOVED` (nach `PilotStats`) → `FlightFeats`, `takeoff` und bei `PilotMode.FPV` `fpv_flight`, `flight`-Auslöser je Paket; `crashed` aus `PilotStats.onCrash`; `DroneCargo.CHANGED` merkt die Aufnahme und meldet beim Ablassen (`COMMAND`, `DISARMED`) die Strecke; `DroneStrikes.STRUCK` → `drone_strike` (auch ausgewichene Treffer); Pakete nur mit `CrashBudget` je Spieler (8 am Stück, dann einer je 5 Ticks) |
| `stats/net/TrickC2S` | stats | `propwash:stats_trick` (Drohnen-Id, Trick-Schlüssel ≤ 48 Zeichen oder leer, Kombo als VarInt ≤ 64); nur vom Piloten der Drohne, unbekannte Tricks verworfen |
| `stats/net/TrainingC2S` | stats | `propwash:stats_training` (Kapitel als VarInt, alle Sterne als bool) = „Lektion bestanden“: Stand der Flugschule aus dem Client |
| `stats/net/GhostBeatenC2S` | stats | `propwash:stats_ghost_beaten` (Kurs-Id als VarInt); nur, wenn der Spieler gerade eine Drohne fliegt |
| `stats/client/StatsClient` | stats | zusätzlich: `tricks(drone, score)` meldet je Verbindung jeden Trick-Typ einmal und jede neue beste Kombo ab 2, nie in der Freecam; `ghostBeaten(kurs)`; alle 40 Ticks den Stand der Flugschule, wenn er sich geändert hat (nach jedem Beitritt neu) |
| `interact/DroneStrikes` | interact (Fix FB1-14) | `dodges(target)`: ein Enderman (nicht als Mitfahrer) teleportiert sich wie bei Pfeilen bis zu 64-mal zufällig im Umkreis von 32 Blöcken und nimmt keinen Schaden und keinen Rückstoß; `STRUCK` feuert mit `hurt = false` |
| Aufrufe in fremden Dateien | race, trick, drone, content, video | `RaceServer.lap`: `FpvAdvancements.lap(player, course.ownedBy(...))` neben `PilotStats.lap`, `personal_best` nur bei sauberer Runde mit vorheriger Zeit in der Klasse und `outcome.improved()`; `ClientRaceTracker` (Runde ohne Flughilfe schneller als `GhostLibrary.shown()`); `TrickTracker.onFrame` → `StatsClient.tricks`; `DroneInteractions.quickSwap` und `DroneServiceMenu`-Akkuplatz (`setByPlayer`) → `battery_swapped`; `BatteryChargerMenu`-Platz `onTake` mit vollem Akku → `battery_charged`; `PaintService.onPaint` bei `APPLIED` → `drone_painted`; `SpectateServer.onRequest` bei fremdem Piloten → `spectated` |

- Drache und Wither: Vanilla `minecraft:player_killed_entity` mit `killing_blow.direct_entity` = `propwash:drone`
  (Treffer tragen den Piloten als Verursacher, `DroneStrikes.damageSource`); beim Drachen trifft eine Drohne nur die
  Haupt-Hitbox (Teile sind keine `LivingEntity`).
- Neue Pakete ohne neue Protokollnummer (setzt die Integration).

Tests: JUnit `stats.advancement.FpvAdvancementFilesTest` (5: Liste, Wurzel und Hintergrund, Baum, Auslöser, Texte und
Symbole) und `FpvTriggerLogicTest` (6); Selbsttest `stats.client.FpvAdvancementSelfTests` (Präfix `stats:`, 4 Schritte,
mit `-PpropwashScreenshots=true` zusätzlich `stats_advancements_en.png` und `stats_advancements_de.png`).

### Kamera-Chunks für Aufnahmen (capture, server, api; 0.6.0, API 1.7, Baustein `docs/camera-chunks.md`)
Neues Modul `capture` (Common `capture.CaptureServer` am Ende von `PropwashCommon.init()`, Client
`capture.client.CaptureClient` am Ende von `PropwashClient.init()`), Sprachquellen `common/src/lang/bg-capture/`. Ein
Add-on meldet für einen Spieler eine Lage um dessen eigene Drohne an; der Server schickt diesem Client die Chunks und
Entities dort zusätzlich zu seiner Vanilla-Ansicht. Damit nimmt Flashback die Umgebung einer weit entfernten
Kamera-Drohne auf. Kein zweiter Zeichen-Durchgang, kein Eingriff in Zeichen-, Shader- oder Sodium-Code
(Owner-Entscheidung vom 28.10.2026: keine Videoaufnahme im Hintergrund).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `api/drone/CameraChunkSupply` | api | Server: `open(spieler, drohne, radius)` → `CameraChunkResult`, `close`, `closeAll`, `status` → `Optional<CameraChunkStatus>`, `active`; Konstanten `MAX_RADIUS` 8, `MAX_PER_PLAYER` 2, `MAX_DISTANCE_CHUNKS` 128; Zugang `DroneAccess#cameraChunks()` (`default`, `CaptureServer.API`) |
| `api/drone/CameraChunkResult`, `CameraChunkStatus` | api | `Status` `OPENED`, `UPDATED`, `NOT_A_DRONE`, `NOT_OWN_DRONE`, `OTHER_DIMENSION`, `RULE_OFF`, `TOO_MANY`, `SERVER_BUSY`, `CLIENT_UNSUPPORTED`, `PLAYER_GONE` (neue nur ans Ende), `accepted()`, `refused(status)`; Status: Drohne, Entity-Id, Mitte, gewünschter und gewährter Radius, `paused`, `chunks`, `sentChunks`, `complete()` |
| `api/client/CameraChunkRequests` | api | Client: `supported()`, `request`/`release`/`radius`/`answer` mit Entity-Id oder Entity; Zugang `PropwashClientApi.cameraChunks()` |
| `capture/CameraArea`, `CameraTrackingView`, `TrackingDiff` | capture (reines Java mit Vanilla-Typen) | Lage = Mitte und Radius mit der Vanilla-Abstandsregel (`ChunkTrackingView.isWithinDistance`, mit Nachbarn); Ansicht = `Positioned` des Spielers plus Lagen (`of` liefert ohne Lagen das Vanilla-`Positioned`), `forEach` jeden Chunk genau einmal; `difference` nur über die echten Unterschiede (Vanilla würde bei fremden Ansichten alles vergessen und neu schicken) |
| `capture/CaptureRules`, `CaptureBook` | capture (reines Java) | Radius = Anfrage gekappt auf 1…min(8, Welt-Regel), 0 bei Regel 0; Zulassung: 2 je Spieler, 16 und `maxStreamedChunks` am Server, keine neuen über 45 ms mittlerer Tick-Zeit; Ende in dieser Reihenfolge `DISCONNECTED`, `DIED`, `DRONE_GONE`, `DIMENSION`, `NOT_OWN_DRONE`, `RULE_OFF` (dazu `CLOSED`); Speicher-Radius des Clients = weitester Chunk der Lagen + 3, auf 8 aufgerundet, wächst sofort und schrumpft erst ab 16 Chunks Abstand; Lagen über 128 Chunks pausieren; 10 Anfragen je 20 Ticks |
| `capture/CaptureServer` | capture | Server-Thread: Sitzung je Spieler (gleiche `ServerPlayer`-Instanz), Lage folgt dem Chunk der Drohne, pausiert außerhalb von `ServerRules.maxPilotDistance` oder 128 Chunks; Tickets `drone_view` (Radius + 2, ohne Erzeugen nur der erzeugte Radius über `RangeManager.generatedStatus`, pausiert nur der Chunk der Drohne mit Radius 0); `ChunkMap#move` alle 20 Ticks und nach jeder Änderung, damit Entities gepaart und wieder gelöst werden; eigene Drohne = Pilot, gebundener Operator oder Tag `pw_owner_<name>` |
| `capture/CaptureS2C`, `CaptureC2S` | capture | `propwash:camera_chunks` (Speicher-Radius, Liste Entity-Id/Radius/pausiert bis 16, Antwort Entity-Id und Status-Ordinal, −1 = keine), `propwash:camera_chunks_request` (Entity-Id, Radius, 0 = abmelden). Neue Payloads: Protokollnummer setzt die Integration |
| `capture/client/CaptureClient`, `CaptureStorage` | capture | setzt den Speicher-Radius am `ClientChunkCache` des aktuellen Levels; `DISCONNECT` leert |
| `mixin/server/CameraChunkMapMixin` | capture | `@WrapMethod` `updateChunkTracking` (mit Lage oder Lagen-Ansicht: Mitte und Weite wie `ViewAnchors`, eigene Ansicht) und `applyChunkTrackingView` (sobald eine Seite eine Lagen-Ansicht ist: Speicher wachsen, Mitte schicken, `TrackingDiff`, Ansicht setzen, Speicher schrumpfen); sonst Vanilla unverändert |
| `mixin/server/CameraTrackedEntityMixin` | capture | `TrackedEntity#updatePlayer`: sichtbar auch, wenn die Entity in einer Lage der Ansicht liegt (Abstand zur Drohne höchstens min(Tracking-Weite, Radius × 16), `broadcastToPlayer`, Chunk gesendet) |
| `mixin/server/client/CameraClientChunkCacheMixin` | capture | `updateViewRadius`: Argument mindestens Speicher-Radius − 3; merkt die Vanilla-Weite; neue Levels beginnen ohne Zusatz |
| `server/rules/WorldRuleSpecs` | server (eine Zeile bg-capture) | `CAMERA_CHUNKS` = `propwash:camera_chunks`, Zahl 0–8, Standard 4, 0 = aus, Gruppe Video, neutral |

- Reihenfolge am Client: Speicher wächst vor den Chunks (Paket vor `SetChunkCacheCenter` und Unterschied), schrumpft
  erst nach den `ForgetLevelChunk`-Paketen; dazwischen bleiben alle Chunks der alten und neuen Ansicht im Speicher.
- Hauptbild: Chunks einer Lage liegen außerhalb der Ansicht des Spielers und damit außerhalb der wirksamen Sichtweite
  (`Options#getEffectiveRenderDistance` = min(Sichtweite, Server)); Vanilla zeichnet dort keine Abschnitte und keine
  Entities.
- Mit FPV (`ViewAnchors`) gilt die Lage zusätzlich zur Ansicht um die geflogene Drohne.

Tests: JUnit `capture.CaptureRulesTest` (11), `capture.CameraTrackingViewTest` (5), `capture.TrackingDiffTest` (5),
`capture.CaptureBookTest` (6), `race.RaceRulesTest` (Regel neutral); Selbsttest `capture.client.CaptureSelfTests`
(Präfix `capture:`, 7 Schritte, mit Flashback auf Fabric dazu Aufnahme und Prüfung der Wiederholung: Gelände, Drohne und
Marker der Lage sind im Wiederholungs-Server); Beitrittstest `PROPWASH_JOINTEST_CAPTURE` (`dev.CaptureJoinCheck`,
`capture.client.CaptureJoinProbe`).

### Gate-Menü mit Tabs und Tab Position (race, guide; 0.6.0, Spieler-Bausteine `docs/race-system.md` §6 und `docs/gate-hint.md` §4, Entwurf `docs/design/0.6.0/race-system.md` §3.A7)

| Datei | Vertrag |
|---|---|
| `race/client/GateEditPanel` | Seiten als Tabs (`FlatButton.Style.TAB`, natürliche Breite, bei Platzmangel anteilig) an der Stelle des ersten Abschnittstitels, das Panel bleibt so hoch wie mit dem früheren Seitenknopf; `tabLabel(kind, page)`: „Gate“ (`propwash.race.edit.tab.gate`) bzw. `GateMounting.elementName`, `…tab.signal`, `…tab.score`, `…tab.position`. Seite Position mit `positionInfo(kind)` (`propwash.race.nudge.info`, Argumente erster Tab und `key.propwash.gate_rotate`) unter den Zeilen wie der Zielblock auf der Seite Redstone, in die Panelhöhe eingerechnet (`pageBody`). Fußzeile der Anzeige bei mehr als einer Seite `propwash.race.edit.hint.cancel_tab`. Die Schlüssel des früheren Seitenknopfs (`propwash.race.edit.page.*`, `propwash.race.score.page*`, `propwash.race.nudge.page`) entfallen. |
| `race/client/GateEditModel` | `turnPage(delta)` ringsum, `pageOf(Page)` (−1, wenn die Art die Seite nicht hat). |
| `race/client/GateEditor` | Ohne freie Maus und ohne offenes Fenster: Tab = nächste, Umschalt+Tab = vorige Seite (`turnPage`), wenn es mehr als eine Seite gibt; die Taste bleibt bis zum Loslassen verschluckt (keine Spielerliste). Mit freier Maus bleibt Tab die Fokus-Taste des Fensters. |
| `guide/scenes/GateMenuScene` (`gate_menu`) | fünf Schritte `open`, `adjust`, `redstone`, `position`, `freeform` (die Angabe „vier Schritte“ im Abschnitt „Gate-Hinweis und Kapitel „Gate-Menü““ ist damit überholt); `position`: Gate `WIDE` rastet `NUDGES` mal um `NUDGE` (1/4) nach Osten, zweimal zum Piloten und einmal um `FINE_NUDGE` (1/16); Texte in `common/src/lang/gatehint/`. |

Tests: JUnit `race.client.GateEditModelTest`, `guide.GateMenuSceneTest`; Selbsttest `race.client.GateNudgeTabSelfTests`
(`race-topo: Position-Tab`, Bilder `bildschirmfotos: Position-Tab`).

### Gerät im Flug anstecken (input; 0.6.0, Baustein `docs/input-hotplug.md`, Befund HOTPLUG-1)

| Datei | Besitzer | Vertrag |
|---|---|---|
| `input/DeviceChooser#pick(devices, preferred, session, blocked, usage, arrived)` | input | Feste Auswahl geht vor; ohne Sitzungsgerät wie bisher die beste Wahl (`usageRank`, dann `score`); mit Sitzungsgerät dieselbe Wahl, aber nur unter dem Sitzungsgerät und den während der Sitzung neu aufgetauchten Geräten (`arrived`). Ohne Ankunft unverändert: das Sitzungsgerät oder `-1` (Failsafe), nie ein schon vorhandenes anderes Gerät. Die Überladung ohne `arrived` rechnet mit `i -> false` |
| `input/client/TransmitterInput` (`sessionArrivals`) | input | Ids, die ein Scan während einer Flug-Sitzung (`PilotSession` oder Freecam) neu sieht; nach jeder Auswahl im Scan bleiben nur das offene Gerät und eines, das nach einem Öffnungsfehler auf den nächsten Versuch wartet (`openBlocked`), alle anderen gelten ab dann als schon vorhanden (Befund AUTO27-1: sonst übernähme ein im Flug angestecktes, nicht gewähltes Gerät beim Abziehen der Funke statt des Failsafe); leer am Ende der Sitzung, bei `onDisconnect` und beim Neustart der Geräteerkennung. Damit löst eine im Flug angesteckte, eingerichtete Funke ein nicht eingerichtetes Sitzungsgerät sofort ab (vorher erst nach dem Ende der Sitzung), ein eingerichtetes, zuletzt benutztes Sitzungsgerät bleibt |

Tests: JUnit `input.DeviceHotplugTest` (10), `input.DeviceSelectionTest` unverändert; Selbsttest
`input.client.HotplugSelfTests` (Präfix `input: Hotplug`, 3 Schritte mit virtuellen SDL-Joysticks, beide Loader).

### Kreativ-Werte im Sharecode (share, creative; 0.6.0, Baustein `docs/sharecode-ui.md` §8, Befund PWD3-1)

Der Drohnen-Editor schreibt PW3 statt PWD1. Format: `docs/sharecode/pw3.md` §3.6 (Ebene `CREATIVE`, Erweiterung `e = 0`,
Revision 3.1) und §7.1 (PWD1 lesen und umrechnen).

| Datei | Besitzer | Vertrag |
|---|---|---|
| `share/ShareLayer.CREATIVE` (Bit 5), `share/CreativeLayer`, `share/Pw3Params` (`CREATIVE_KEYS`, `K_CREATIVE_*`, `REVISION` 3.1) | pw3-codec | Kodierer schreibt die Erweiterung nur mit Kreativ-Werten, ohne sie ist jeder Code bytegleich zu 3.0; Leser: `e = 0` höchstens einmal, nie mehr Bits als angegeben, Rest der Ebene überlesen, andere `e` wie bisher `layer:<bit>`; Schlüssel-Tabelle nur anhängen (`CreativeShareTest` verlangt für jeden `CreativeParam` einen Index) |
| `share/ShareContent#creative` | pw3-codec | `Optional<CreativeSpec>`, der 5-stellige Konstruktor bedeutet „ohne“; `only` filtert, `withBuild`/`withCreative` behalten; `toDroneData()` (API) trägt keine Kreativ-Werte |
| `share/ShareFormat` (PWD1), `share/CreativeShare` | pw3-codec | `PWD1…` → Format `PWD1`, `legacy()`, Bauplan `CreativeShare.PWD1_BUILD` (Freestyle der Flugschule mit Kreativ-FC), Werte unverändert; `CreativeShareCode.decode` verlangt einen vollständigen Deflate-Strom (sonst `CORRUPT` statt leerer Werte); `editorCode` und `paste` (OK, NO_VALUES, INVALID) für den Editor; `ShareCodesImpl#decode` (API) meldet PWD1 weiter `OTHER_CODE`, `convert` rechnet ihn um |
| `share/ShareCreativeC2S`, `share/ShareServer#createCreative`, `#createDrone`, `#stats` | pw3-ui | C2S `propwash:share_creative_create` (Anfrage-Id aus `ClientApiImpl.PENDING`, `ImportWire`, `CreativeSpec`), angemeldet in `ShareModule`, Antwort `api_import_result`; Regeln wie `ImportService.newDrone` (Rate `ImportNet.RATE`, Kreativmodus oder Recht 2, unbekannte Teile, baubar), danach `CreativeLimits.sanitize` und `CreativeEdits.usable`, sonst `INVALID` mit „Diese Werte ergeben kein gültiges Flugmodell“; `BUILD_IMPORTED` feuert; `createDrone` (Befehle) setzt die Werte genauso; `stats` rechnet die Flugwerte mit den Kreativ-Werten |
| `share/client/ShareCreativeClient` | pw3-ui | „Drohne erzeugen“ mit Kreativ-Werten über das Paket; kennt der Server es nicht, wie bisher `sendCreateDrone` ohne Werte |
| `share/ShareCommands` | pw3-ui | `export` nimmt bei einer Kreativ-Drohne die Werte mit (ohne Angabe und mit `creative`), `import`/`give` erzeugen mit Werten, im Überleben Hinweis `propwash.command.code.creative_survival`, `info` mit Zeile „Kreativ-Drohne …“ und Flugwerten aus `ShareServer.stats`, PWD1 mit `propwash.share.pwd1.build` |
| `share/client/ShareLines`, `ShareScreen`, `ShareConfigTab`, `ShareImportState`, `chat/ChatPreview`, `chat/ChatCodeFinder` | pw3-ui, pw3-chat | Zeilen-Tags `creative`, `creative:survival` (ohne Kreativmodus), `creative:workbench` (Werkbank-Fenster), `pwd1`; Export im Tab nimmt die Werte der gehaltenen Kreativ-Drohne mit; der Chat erkennt `PWD1-…` (vollständiger Deflate-Strom, kürzeste Form ohne Bindestriche am Ende zuerst) |
| `creative/client/CreativeEditorTab#shareCode`, `#droneData`, `#paste` | creative | PW3 mit Teilen, Lack, Tuning (Kamerawinkel aus dem Entwurf), Name und dem Entwurf als Kreativ-Werte, Add-on-Infos wie im Tab; Einfügen nimmt PW3 mit Kreativ-Werten und PWD1 („code.pasted_legacy“), lehnt PW3 ohne Werte ab („code.no_values“); eigene Vorlagen bleiben intern PWD1 |

Tests: JUnit `share.CreativeShareTest` (17: Rundreise mit allen Ebenen, 3000 Zufallswerte, alte PWD1, Umrechnung, alte
Leser überspringen die Ebene genau, Rahmung, 20000 verstümmelte Codes, Paket auf dem Draht, Golden
`pw3-creative-vectors.json` mit C1–C4 und D1–D3), `share.Pw3CodecTest` (`pwd1-abc` jetzt `CORRUPT`, gültiges PWD1 `OK`),
`share.client.chat.ChatCodeFinderTest` (PWD1 im Chat); Selbsttest `kreativ-drohne: Editor → PW3-Code → Import …`.

### Vorschau-Build mit Wasserzeichen (preview, video, apiimpl, Build; 0.6.0, Baustein `docs/preview-build.md`, Befunde PREVMARK-1, PREVMARK2-1 und PREVMARK3-1)

| Datei | Besitzer | Vertrag |
|---|---|---|
| `build.gradle`, `build-logic/…/multiloader-common.gradle` | Build | `-Ppropwash_preview=true` oder `=false` (Standard `false`, anderes bricht ab; `true` nur mit Vorab-Nummer); `ext.propwashPreview`; `processResources` ersetzt `${propwash_preview}` in `META-INF/propwash-build.properties`, Manifest `Propwash-Preview` (beides nur Kennzeichnung, das Spiel liest es nicht), Jar-Zusatz `preview`, Maven-Veröffentlichung mit Schalter gesperrt; Selbsttest `propwash.selftest.preview`, JUnit `propwash.preview.expected` |
| `common/build.gradle`, `build-logic/…/multiloader-loader.gradle` | Build | `generatePreviewSwitch` erzeugt `preview/PreviewSwitch` aus der Vorlage mit `ENABLED = <Schalter>` und `VERSION = "<Version>"` (nur `[0-9A-Za-z.+_-]`), `compileJava` von common, fabric und neoforge nimmt die erzeugte Klasse statt der Vorlage (Konfiguration `commonPreviewSwitch`) |
| `preview/PreviewSwitch` | preview | Vorlage `ENABLED = false`, `VERSION = ""` (gilt ohne Gradle: fastcheck, IDE); nur Konstanten, keine Methoden |
| `preview/PreviewBuild` | preview | `ENABLED` = `PreviewSwitch.ENABLED` (Konstante, vom Compiler eingesetzt), `enabled()`, `TEXT` = `watermark(PreviewSwitch.VERSION)`, `watermark(version)` = „Propwash Preview <Version> · not final“ (leere Version „?“), `TITLE` = `windowTitle(PreviewSwitch.VERSION)` = „Propwash Preview <Version>“; keine Laufzeitquelle |
| `preview/PreviewFont` | preview | Pixelschrift im Code (Ziffern, Buchstaben, `. + _ - · ?`, Leerzeichen; 9 Zeilen, 7 über der Grundlinie, 1 Spalte Abstand; unbekannte Zeichen „?“), `raster(text)`, `Raster#rect` (zusammengefasste Rechtecke) |
| `preview/PreviewStamp` | preview | `TEXT_ARGB` 0xB4FFFFFF, `SHADOW_ARGB` 0xB4000000, `MIN_SCALE` 1, `MARGIN` 4; `screenScale(h)` = `max(1, round(h / 540))`, `place(raster, w, h, anhebung)` (unten rechts, Schatten einen Schriftpunkt versetzt), `stampScale(h)` = `max(1, round(h / 360))`, `mask(raster, bildhöhe)`, `stampI420` und `stampArgb` ändern nur das Rechteck unten rechts (Rand `margin(h)`), Alpha bleibt |
| `preview/client/PreviewMark` | preview | `extract(graphics, resourcesLoaded)` nach allem anderen in zwei neuen Schichten (Schatten, dann Schrift) als Rechtecke der Pixelschrift in Fensterpixeln mit `PreviewPipeline.PIPELINE`, unabhängig von der GUI-Skalierung, im Titelbildschirm über der Copyright-Zeile; `stampFrame(I420)`, `stampImage(NativeImage)` (Maske je Bildhöhe zwischengespeichert); ohne Schalter beginnen alle drei mit `return`; Zähler `passes`, `drawn`, `drawnHudHidden`, `drawnOnScreen`, `drawnOnTitle`, `stampedFrames`, `stampedImages` |
| `preview/client/PreviewPipeline` | preview | eigene GUI-Pipeline `PIPELINE` (`propwash:pipeline/preview_mark`, Shader `propwash:core/preview_mark`, `Globals`/`Projection`/`DynamicTransforms`, `POSITION_COLOR`, Quads, durchscheinend, ohne Tiefentest); `SOURCE` = Shader-Quelle im Code (Vertex wie `core/gui`, Fragment gibt die Farbe unverändert aus, kein `discard`, keine Includes; antwortet nur für die beiden eigenen Shader); `compiled()` übersetzt einmal über `GpuDevice#compilePipeline(PIPELINE, SOURCE, …)` (scheitert das, Abbruch mit Meldung); `lookup(pipeline)`; Zähler `compiles`, `lookups`. Nicht in `RenderPipelines` angemeldet, der Shader-Lader von Minecraft übersetzt sie nie |
| `mixin/core/client/GuiMixin` | preview | `Gui#extractRenderState` vor `GuiGraphicsExtractor#applyCursor` → nur mit Schalter `PreviewMark.extract` (beide Loader, `@Local` aus MixinExtras); ohne Schalter leerer Handler |
| `mixin/core/client/RenderSystemMixin` | preview | `RenderSystem#getCompiledPipelineNullable` am Anfang → nur mit Schalter für `PreviewPipeline.PIPELINE` die eigene Übersetzung (`PreviewPipeline.lookup`), vor den Pipeline-Caches mit Shadern aus den Ressourcen; ohne Schalter leerer Handler |
| `mixin/core/client/MinecraftMixin#propwash$previewTitle` | preview | `Minecraft#createTitle` am Anfang → nur mit Schalter `PreviewBuild.TITLE` (Starttitel und jedes `updateTitle`); ohne Schalter leerer Handler |
| `video/client/record/ActionCamRecorder#accept` | video | nur mit Schalter `PreviewMark.stampFrame` direkt nach dem Kopieren in den Encoder-Puffer (nach der Leer-Bild-Prüfung, vor Statistik und Vorschaubild) |
| `apiimpl/client/RecordingControlImpl#photo` | apiimpl | nur mit Schalter `PreviewMark.stampImage` vor `writeToFile` |

Ohne Schalter ist das Verhalten unverändert; Release-Jar und Vorschau-Jar unterscheiden sich in Kennzeichen, Manifest,
`PreviewSwitch` und den Klassen, in die der Compiler die Konstante einsetzt. Tests: JUnit `preview.PreviewHardcodedTest`
(9), `preview.PreviewPipelineTest` (3), `preview.PreviewFontTest` (5), `preview.PreviewStampTest` (7),
`preview.PreviewBuildTest` (4); Selbsttest `preview.client.PreviewSelfTests` (Präfix `preview:`, 10 Schritte mit
Fenstertitel, Pixelprüfung und Ressourcenpaket samt eigenem `core/gui.fsh` und Probe-Fläche, mit und ohne Schalter,
beide Loader).

### Recyceln in der Werkbank (content, pw3-ui; 0.6.0, Befund RECYCLE-1, Doku `docs/design/survival.md` „Recyceln in der Werkbank“)

| Datei | Besitzer | Vertrag |
|---|---|---|
| `content/WorkbenchTab` | content | `BUILD`, `RECYCLE`; `key()` für Texte, `of(index)` |
| `content/DroneWorkbenchMenu` | content | Daten-Slots Tab, Recycle-Status, Ergebnisanzahl; `clickMenuButton`: `BUTTON_BUILD` 0, `BUTTON_RECYCLE_TAB` 1 (Tab wechseln), `BUTTON_RECYCLE` 2 (nur im Tab Recyceln und nur auf dem Server). Slots hinter dem Inventar: `RECYCLE_INPUT` = `INV_END` (ganze Stapel, nur Teile mit `propwash:part`), `RECYCLE_OUTPUT` 9 Plätze (nur entnehmen), `RECYCLE_PREVIEW` 9 unsichtbare Plätze (Server legt die Materialien je Rezept hinein, nie anfassbar). Bau-, Ergebnis- und Zerlegen-Plätze gehören zum Tab Bauen, Eingang und Ausgabe zu Recyceln: Plätze des anderen Tabs haben `isActive`, `mayPlace` und `mayPickup` falsch. `recycleMaterials()`, `recycleTier()`, `recyclePieces()` (wie viele Teile sicher in die Ausgabe passen) rechnen auf Client und Server gleich; `recycle(random)` würfelt, füllt die Ausgabe und nimmt so viele Teile aus dem Eingang; `removed` gibt Eingang und Ausgabe zurück. Umschalt-Klick: Bauen → Bauplätze bzw. Drohne ins Zerlegen-Feld, Recyceln → Eingang; `keepsDrone(stack)`: im Tab Recyceln bleibt eine Drohne liegen (Befund AUTO32-1). Indizes `INV_START`…`INV_END` unverändert |
| `content/Recycling` | content | `tier(zustand)` über `PartCondition.percent`: 100 → `FULL`, 60–99 → `HALF`, sonst `QUARTER`. `Materials(entries, perCraft)` mit `of(zutaten, ergebnisanzahl)` (je Item zusammengefasst, Reihenfolge des Rezepts), `expected`, `least`, `most`, `exact`, `roll(stück, stufe, zufall)` (ganzzahliger Teil fest, Rest als Chance). `fallback(part)` = `RecycleYield` bzw. Kategorie-Standard, `NONE` → leer. `plan(stack, rezepte)` → `Status` `EMPTY`, `READY`, `NOT_A_PART`, `MISSING`, `NOT_RECYCLABLE`; `piecesThatFit`, `fits`, `insert` |
| `content/RecycleRecipes` | content | `find(recipeManager, item)` (Zwischenspeicher je `RecipeManager`) und `scan(rezepte, item)`: Crafting-Rezepte nach Rezept-ID, Ergebnis aus `display()` (geformt und formlos), Zutaten aus `placementInfo()`, Tag-Zutaten = erstes Item, Zutaten mit Crafting-Rest fallen weg |
| `content/client/DroneWorkbenchScreen` | content | Panel um die halbe Tab-Höhe tiefer (`WorkbenchLayout.panelTop`), `panelLeft()`/`panelTop()` für Tests; Tabs `WorkbenchTabButton` über dem Fenster (`selectTab` wechselt lokal und schickt den Knopf), Knopf „Recyceln“ (`pressRecycle`); im Tab Recyceln sind Lackierung und Add-on-Knöpfe ausgeblendet |
| `content/client/RecycleView` | content | Zeichnet den Tab: Eingang, Pfeil, Ausgabe, Statuszeile; rechts Name, Zustand, Stufe, erwartete Materialien (Symbol, Menge, „≈“ bei Zufallsanteil, Tooltip mit Spanne) und die drei Stufen; Texte unter `propwash.content.workbench.recycle.*` (Sprachordner `pw3-ui`). `inputLines(carried)` liefert den Tooltip des Eingangs (Drohne an der Maus: `not_a_part` und `drone`), `droneNotice` zeigt nach einem Umschalt-Klick auf eine Drohne (`DroneWorkbenchScreen#slotClicked`) vier Sekunden lang `drone` in der Statuszeile |
| `content/client/SalvageHint` | content | Zerlegen-Feld: Drohne zerlegen, Teile im Tab Recyceln; mit Teil an der Maus Warnung `salvage_reject.part` |

Tests: JUnit `content.RecyclingTest` (8: Stufen an jeder Zustandsstufe, jedes Kern-Teil außer dem Kreativ-FC hat ein
Rezept, unbeschädigt genau die Zutaten je Stück, Craften und Recyceln vermehrt bei keiner Stufe und keiner Stapelgröße
eine Zutat, jede Zutat schrumpft gleich, Würfeln mit festem Seed, Rückfall ohne Rezept); Selbsttests `zerlegen:` (2) und
`recyceln:` (7 plus Bildschirmfotos `workbench_recycle*` hell, dunkel, GUI-Skala 2 und 4, deutsch), ui-scale-Fälle
„Werkbank Recyceln“, „… leer“, „… Props“.

### Kurs-Einstellungen in einem Menü (race-b; 0.6.0, Befund COURSEMENU-1, Doku `docs/race-course-menu.md`)

| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/CourseMenu` | race | Gruppen `Section` (`COURSE`, `ACCESS`, `BOARD`, `DELETE`) und Zeilen `Row` in fester Reihenfolge, je Zeile Gruppe und Bedarf `Need` (`NONE`, `EDIT`, `OPERATOR`, `DELETE`); `rows(punktZuPunkt)` (Runden ↔ Ziel), `rows(gruppe, …)`, `Rights.of(bearbeiten, löschen, besitzer, spielleiter)` mit `Role` (`OWNER`, `OPERATOR`, `VIEWER`), `active(zeile, rechte)`, `resetCommand(kurs)` = `propwash race reset <kurs>` |
| `race/client/CourseMenuScreen` | race | `ThemedScreen` + `WidgetViews`, Fenster bis 400 breit, Inhalt in `ContentScroll` (Mausrad, Scrollbalken, Bild auf/ab, Pos1/Ende, Tab scrollt ins Bild); Rechte über `CourseEditScreen.access`/`canDelete` und `Permissions.COMMANDS_GAMEMASTER`; ohne Bearbeitungsrecht Hinweis oben (Text aus `CourseEditScreen.denied`), alle Änderungen gesperrt und auch in den Methoden (`rename`, `stepLaps`, `setMode`, `bindTool`, `reset`, `delete`) abgewiesen; Änderungen wie im Kurs-Fenster über `RacePayloads.CourseEditC2S` (Name nach 16 Ticks Pause, Enter oder Schließen; Runden nach 6 Ticks), Zurücksetzen als Befehl, Löschen und Zurücksetzen erst beim zweiten Klick (80 Ticks); `openGates`/`openBoard` gehen zum Eltern-Fenster zurück, wenn es schon das Kurs-Fenster bzw. die Bestenliste dieses Kurses ist |
| `race/client/ClientLeaderboards#apply` | race | `open` (Kurs-Werkzeug, Rechtsklick in die Luft) öffnet `CourseMenuScreen`, ohne Kursdaten beim Client wie bisher `LeaderboardScreen`; nicht, wenn eines der beiden schon offen ist |
| `race/client/CourseEditScreen` | race | Knopf „Einstellungen“ (`settingsButton()`, `openSettings()`) rechts in der Kopfzeile; Titel und Info enden davor |
| `race/RacePayloads.LeaderboardS2C#times`, `race/RaceServer#leaderboardPayload` (AUTO31-2) | race | Zahl aller Zeiten des Kurses für den Betrachter (`Leaderboard#size`, mit Welt-Regel Flughilfe plus `assistedSize`) als `VarInt` nach `access`; nie kleiner als die Zeilenzahl, ältere Konstruktoren setzen die Zeilenzahl |
| `race/CourseMenu#times` (AUTO31-2) | race | Zahl für die Zeile „Bestenliste“ im Menü (`LeaderboardS2C#times`, nicht die gesendeten Zeilen) |

Kursformat, Protokoll und Befehle unverändert, das Bestenlisten-Paket trägt zusätzlich `times`. Selbsttests, die die
Bestenliste brauchen, öffnen sie im Client (`new LeaderboardScreen(null, kurs)`), nicht über das `open`-Paket des
Kurs-Werkzeugs (AUTO31-1). Tests: JUnit `race.CourseMenuTest` (6); Selbsttest
`race.client.CourseMenuSelfTests` (Präfix `course-menu:`, 11 Schritte plus Bildschirmfotos `course_menu*` hell, dunkel,
GUI-Skala 2 und 4, nur ansehen, deutsch); ui-scale-Fälle „Kurs-Einstellungen“ und „Kurs-Einstellungen nur ansehen“;
Beitrittstest `PROPWASH_JOINTEST_COURSE_MENU` (`dev.CourseMenuJoinCheck`, `race.client.CourseMenuJoinProbe`).

### VTX-Antennen hinten am Rahmen (drone-render, assets-parts; 0.6.0, Befund ANTENNA-1, Doku `docs/ASSETS.md` „VTX-Antennen hinten am Rahmen“)

| Datei | Besitzer | Vertrag |
|---|---|---|
| `tools/assets/parts/video.py` (`build_antennas`, `antenna_whip`, `antenna_mount`, `ANTENNA_*`) | assets-parts | Antennen der Kern-VTX als eigene Modelle `video/<vtx>_antenna` (+ `_antenna_paint`, nur `drone_render`): Modellpunkt (8, 8, 8) = Hinterkante der Deckplatte oben; `analog_nano` 35 mm, `analog_race` 45 mm mit Lollipop, `digital_lite` 60 mm mit Kappe, `digital_hd` 2 × 90 mm im V; 40° nach hinten, Kappen im Lack-Kanal `antenna`; Video-Modelle selbst ohne Antennen. `layout.py` `FRAMES[*]["antenna"]` → `drone_layout.json` `frames.<rahmen>.antenna` |
| `drone/client/render/DroneLayout#antenna()`, `#antenna(frame)`, `defaultAntenna(frame)` | drone-render | Anbaupunkt aus `drone_layout.json` (`DroneLayoutParser`, ungültig oder fehlend → Rückfall), sonst (0, `plate_mm` bzw. max(`electronics_mm` + 8, Linsenhöhe), 0,21 × Radstand); ältere Konstruktoren ohne Antenne → `antenna()` leer, `antenna(frame)` Rückfall |
| `PartModel#antenna(video)`, `ANTENNA_SUFFIX` | drone-render | Render-Item des Video-Modells + `_antenna` (folgt `visual.render_model`) |
| `DroneAssembly#antenna`, `DroneRenderState#antenna` | drone-render | Teil am Anbaupunkt, nur mit Video-Modell und vorhandenem `_antenna`; `DroneVisuals` und `SceneDroneVisual` zeichnen es ungedreht, Lack über `paint`; `VisualHull` zählt es für Größe, Radius und Vorschau-Rahmen, nicht für den Bodenkontakt |
| `PaintPicker` | drone-render | Lack-Platz `ANTENNA` aus den Lack-Gruppen der Antenne, Klick auf die Antenne wählt ihn (nach allen anderen Teilen außer dem Rahmen), Anker in der Mitte der Antenne |
| `webexport/AssetIndex` | webexport | `_antenna` gehört zu den Varianten, Lack-Gruppen von `<video>_antenna` zu den Farben des Video-Teils |

Flugmodell, Kollisionsbox, Netzprotokoll und Sharecode unverändert. Tests: JUnit `VtxAntennaAssetsTest` (5),
`validate.py` `check_antennas`; Selbsttest `drone.client.render.AntennaSelfTests` (Präfix `antenne:`, 4 Schritte plus
Bildschirmfotos `antenna_rear_<rahmen>_light|dark` und `antenna_fpv_freestyle_5`).

### Bestenliste ohne Grenzen (race; 0.6.0, Befund LBALL-1, Doku `docs/race-data.md` §2.2, Entwurf `race-system.md` §13)

| Datei | Besitzer | Vertrag |
|---|---|---|
| `race/Leaderboard` | race | keine Grenze je Klasse (Wertung und Speicher), `MAX_RULE_SETS` 8 bleibt; `ranked(abschnitt)` → `Ranked(entry, classRank, overallRank)` (Gesamtplatz −1, wenn nicht die beste Zeit des Piloten); `revision()` neu bei jedem Neuberechnen, eindeutig über alle Bestenlisten des Servers |
| `race/LeaderboardPages` | race | Seite 50, höchstens 512 KiB je Paket (`BoardRow#bytes`, `fit`); `first` = Platz 1–50 von „Alle“ je Abschnitt + eigene Zeiten + Klassenrekorde; `view(ranked, klasse)`, `page(view, versatz)`, `totals(revision, …)` |
| `race/RacePayloads` | race | `LeaderboardS2C#totals` (`BoardTotals`: Revision, Zahl in „Alle“ je Abschnitt, `ClassTotal` je Klasse, beim Lesen höchstens 1 000 000), `merge(seite)` nur bei gleicher Revision; `LeaderboardPageC2S(kurs, revision, alle, klasse, abschnitt, versatz)`, `LeaderboardPageS2C(kurs, revision, zeilen ≤ 50)`; ältere Konstruktoren leiten `totals` aus den Zeilen ab (Revision 0, nie nachgeladen) |
| `race/RaceServer#leaderboardPayload`, `#leaderboardPage`, `onLeaderboardPage` | race | Seiten höchstens 10/s je Spieler; veraltete Revision oder leere Seite → erstes Paket, gedrosselt wie `LeaderboardRequestC2S` (4/s) |
| `race/BoardIndex`, `race/BoardPager` | race | Plätze einer Ansicht (dünn besetzt, `firstMissing`, `selfRank`); eine Seiten-Anfrage zur Zeit, 120 ms Abstand, 3 s Wartezeit, 5 Versuche je Seite, 10 Plätze Vorlauf |
| `race/client/ClientLeaderboards` | race | `index(kurs, klasse, abschnitt)` (zwischengespeichert bis zur nächsten Änderung), `ensure(…)` fragt fehlende sichtbare Plätze nach, `applyPage`, `pageRequests()`, `pagesReceived()`, `loading(…)`; `classes` aus `totals` |
| `race/client/LeaderboardScreen`, `BoardLayout`, `BoardSections` | race | alle Plätze in `ScrollArea`, nur sichtbare Zeilen gezeichnet, fehlende als „…“; Kopfzeilen je Abschnitt mit Pfeil und Zahl, Klick klappt ein/aus (`BoardSections`, statisch für die Sitzung), Kopfzeile bleibt beim Scrollen oben (`sticky`, Inhalt darunter abgeschnitten); „Meine Zeit (Platz)“ springt zur eigenen Zeile (`jumpToMine`); für Tests `index`, `layout`, `toggleSection`, `headerY`, `mineX`, `rowsDrawn`, `placeholdersDrawn` |
| `api/race/BoardQuery#MAX_LIMIT` | api | `Integer.MAX_VALUE` = alle Einträge; JSON-Spiegel schreibt jede Zeit |
| `race/RaceCommands` | race | `/propwash race leaderboard [page <n>] [<kurs>]`, 10 je Seite, `pages`, `page`, `pageCommand` |

Protokoll bleibt 17. Tests: JUnit `race.data.LeaderboardAllTest` (4), `race.data.LeaderboardPagesTest` (8); Selbsttest
`race-data:` (`race.client.RaceDataBoardSelfTests`: 120 ohne und 60 mit Flughilfe, alle Plätze, Einklappen, „Meine Zeit“,
GUI-Skalierung 2–4, Bilder `race_data_board_{expanded,collapsed,scrolled,scale2,scale3,scale4}_{en,de}`); Beitrittstest
`PROPWASH_JOINTEST_RACEDATA` mit derselben Bestenliste am dedizierten Server (`racedata board ok`).

### Zuschauerzahl im OSD (osd, video, server; 0.6.0, Befund SPECCOUNT-1, Baustein `docs/spectator-count.md`)

| Datei | Besitzer | Vertrag |
|---|---|---|
| `osd/OsdElement#SPECTATORS` | osd | Letzte Konstante (Ordinalzahlen der übrigen unverändert), Schlüssel `spectators`, Grundplatz 28/0 rechtsbündig, Gruppe `STATUS`, verschiebbar; an in Standard (`at(SPECTATORS)`) und Voll, aus in Minimal und Rennen. Name und Tooltip-Beschreibung im Sprachordner `addon-osd` |
| `osd/OsdData#spectators` | osd | Zahl der Zuschauer der gezeigten Drohne, in `copyTo`, `reset` 0, `sample()` 3 (Vorschau im Editor) |
| `osd/client/OsdRenderer` | osd | sichtbar bei `spectators > 0`; `spectators(slot)` zeichnet ein Auge aus Rechtecken (9 × 5 Pixel, `EYE_SHAPE`, wie der REC-Punkt mit Kontur und Hintergrund) und die Zahl (`SpectatorCount.clamp`, höchstens 9999) in Elementfarbe |
| `osd/SpectatorCount` (reines Java) | osd | `add(zahlen, spieler, zuschauen, bild, ausgenommen)` zählt einen Spieler je Drohne einmal, `of`, `shown(geflogen, zuschauen, bild)`, `clamp`; `Outbox#offer(empfänger, entity, zahl)` meldet nur Änderungen, eine 0 nur als Ersatz einer gezeigten Zahl derselben Drohne, `forget`, `clear` |
| `osd/SpectatorCountServer`, `osd/SpectatorCountS2C` | osd | `init()` aus `OsdModule`: Payload `propwash:spectator_count` (Entity-Id, Zahl; S2C), `TICK_END` zählt über alle Spieler `SpectateServer#watching` und `SpectatorLinks#watched` ohne Pilot (`isFlownBy`) und Operator (`OperatorServer#operator`) und schickt jedem Piloten, Zuschauer und Operator die Zahl seiner gezeigten Drohne nur bei Änderung; `PLAYER_JOIN`/`PLAYER_LEAVE` vergessen den Spieler, `STARTED`/`STOPPED` leeren alles. `count(server, drohne)` und `sent()` für Tests |
| `osd/client/SpectatorCountClient` | osd | `init()` aus `OsdClient`: Handler merkt Entity-Id und Zahl bis `ClientBus.DISCONNECT`; `count(drohne)` liefert die Zahl nur für diese Entity; `received()` |
| `osd/client/LiveOsdSource` | osd | `spectators` im FPV aus der geflogenen und beim Zuschauen aus der angeschauten Drohne, sonst 0 (auch Freecam) |

Tests: JUnit `osd.SpectatorCountTest` (13), `OsdModelTest` liest die OSD-Texte auch aus `addon-osd`; Selbsttest
`osd: Vorlagen ohne Überlappung` mit der Zahl 9999; Beitrittstest `PROPWASH_JOINTEST_SPECCOUNT` (1 = Pilot,
2 = Zuschauer; `dev.SpecCountJoinCheck`, `osd.client.SpecCountJoinProbe`), Ablauf in `docs/testing.md` §1
„Zuschauerzahl im OSD“.

### VTX-Antennen am Antennenhalter des Rahmens (drone-render, assets-parts, api; 0.6.0, Befund ANTENNA3-1, ersetzt den Abschnitt ANTENNA-1 darüber, Doku `docs/ASSETS.md` „VTX-Antennen hinten am Rahmen“)

| Datei | Besitzer | Vertrag |
|---|---|---|
| `api/part/AirframeGeometry#antennaAnchor()`, `DEFAULT_ANTENNA_TILT`, `FrameDefinition.Builder#antennaAnchor(AnchorSpec)` | api (additiv, 1.7) | optionaler Antennenhalter: Mitte der Hinterkante des Top-Plates oben, `tiltDeg` = Neigung aus der Senkrechten nach hinten (35°); alter 9-Komponenten-Konstruktor bleibt (leer). JSON `antenna_anchor` (`pos`, `tilt_deg`) in `PartJson` |
| `api/part/VideoAntenna`, `AntennaCap`, `VideoDefinition#antenna()`, `VideoDefinition.Builder#antenna(VideoAntenna)` | api (additiv, 1.7) | optionale Vorgabe: Anzahl 1–2, Länge 10–250 mm, Kappe `whip`/`stubby`/`lollipop`/`pagoda`/`patch`; alter 11-Komponenten-Konstruktor bleibt. JSON `antenna` (`count`, `length_mm`, `cap`) |
| `build/AntennaRules` | drone-render | `antenna(video)` = Vorgabe oder `derived(video)` aus Reichweite, Masse, Videosystem, Bildqualität, Latenz und Leistung (`Kind` Whoop, Race, Lite, HD, Long-Range); `anchor(frame)` = Halter oder `fallbackAnchor(frame)` aus Radstand, Akkuplatte, Elektronikhöhe, Linse und Duct |
| `drone/client/render/AntennaGeometry` | drone-render | Form am Halter: TPU-Halter, SMA, Hülse, Stab, Kappe je `AntennaCap`; eine Antenne 7 mm links und 12° nach außen, zwei im V ± 7 mm und 25°; `pieces` für Tests, `shape` gecacht; Kappenfarben `CAP`/`CAP_LIGHT` nehmen den Lack-Kanal `antenna` an |
| `drone/client/render/AntennaMask`, `DroneRenderResources#antennaMask`, `#droneItem` | drone-render | Elemente `antenna*` in VTX-Modellen (auch Lack-Variante) beim Zeichnen an der Drohne ausgelassen (gedrehte Elemente exakt, Flächen anderer Elemente bleiben); `droneItem` liefert den Item-Zustand ohne diese Flächen samt neuen Grenzen |
| `DroneAssembly#antenna`, `DroneLayout#antenna(frame)`, `#antennaTilt(frame)`, `DroneLayoutParser` (`antenna` nur noch als Verschiebung aus Ressourcenpaketen) | drone-render | Antenne immer als Form am Halter, auch beim Ersatz-Sender (`FallbackShapes#transmitter` ohne Antennen); `PartModel#antenna` und `_antenna`-Modelle entfallen |
| `PaintPicker` | drone-render | Lack-Platz `ANTENNA` steht zur Wahl, sobald eine Antenne gezeichnet wird; Standardfarbe = Kappenfarbe |
| `webexport/PartData` | webexport | Rahmen `antenna_anchor` (`pos`, `tilt_deg`, `explicit`), VTX `antenna` (`count`, `length_mm`, `cap`, `kind`, `derived`); `AssetIndex` ohne `_antenna` |

Flugmodell, Kollisionsbox, Netzprotokoll, Sharecode und Kalibrierung unverändert. Tests: JUnit `build.antenna.AntennaDataTest`
(5), `VtxAntennaAssetsTest` (6), `validate.py` `check_antennas`; Selbsttest `drone.client.render.AntennaSelfTests`
(Präfix `antenne:`, 6 Schritte plus Bildschirmfotos).

### VTX-Antennen sichtbar hinter dem Akku (drone-render, api; 0.6.0, Befund ANTENNA4-1, ergänzt den Abschnitt ANTENNA3-1 darüber, Doku `docs/ASSETS.md` „VTX-Antennen hinten am Rahmen“)

| Klasse | Modul | Inhalt |
|---|---|---|
| `build/AntennaRules` | build | `antenna(video, frame)` kürzt auf `maxLengthMm(frame)` = 1,2 × Radstand (auf 0,5 mm, 10–250 mm); `pairLeanDeg(frame)` 15° je Antenne im V (Duct oder ab 200 mm Radstand), an offenen Rahmen 5° bei 100 mm bis 15° bei 200 mm; Race 50 + (Reichweite − 380) / 14 mm, 45–60; Rückfall z = 0,22 × Radstand + 12 mm (über 300 mm 66 + 0,065 × (Radstand − 300) + 12), Whoop 0,18 × Radstand |
| `api/part/AirframeGeometry#DEFAULT_ANTENNA_TILT` | api (1.7) | 45° statt 35°; `antenna_anchor` meint den Fuß der Antennen hinter Rahmenkante und Akku |
| `drone/client/render/AntennaGeometry` | drone-render | SMA am Fuß (0,5 mm hinter dem Halter), TPU-Halter 12 % des Radstands (höchstens 16 mm) nach vorn bis an die Deckplatte, einzelne Antenne mittig ohne Seitenneigung, Paar aus einem Punkt im V (`pairLeanDeg`), Peitsche mit 3-mm-Hülse und 2-mm-Stab; `shape(antenna, tilt, wheelbase, extraReach, pairLean)` |
| `drone/client/render/DroneAssembly` | drone-render | `behindPackMm`: ragt der Pack eines Akkus oben weiter nach hinten als der Fuß, rückt die Antenne 5 mm hinter ihn (`PACK_GAP_MM`), der Halter wird um denselben Weg länger |
| `drone/client/render/DroneRenderResources#packRear`, `PlugAnchors#isLead` | drone-render | hinterste z-Koordinate des Akku-Packs ohne Stecker, Kabel, Balancer und Pigtails, je Modell zwischengespeichert |
| `webexport/PartData` | webexport | `antenna_anchor` zusätzlich mit `max_length_mm` und `pair_lean_deg` |
| `drone/client/render/AntennaVisibility` | devtest | Projektion 3/4 von hinten (±35°, 25° Neigung) und genau seitlich; freie Länge von Hülse, Stab und Kappe vor dem Umriss des Akkus (JUnit und Selbsttest) |

Kern-Rahmen (Fuß, Neigung 45°): `whoop_65` (0, 23,2, 11,5), `cinewhoop_3` (0, 24, 46), `freestyle_5` (0, 28,5, 64),
`race_5` (0, 20, 54), `long_range_7` (0, 32,5, 72). Flugmodell, Kollisionsbox, Netzprotokoll, Sharecode und Kalibrierung
unverändert. Tests: JUnit `VtxAntennaAssetsTest` (9), `AntennaDataTest` (6); Selbsttest `antenne:` mit dem neuen Schritt
„Antennen 3/4 von hinten und seitlich vor freiem Grund, höchstens 1,2 × Radstand“ und den nahen Bildern
`antenna_close_<namensraum>_<rahmen>`.

### Namen der Flugmodi einer Flughilfe (input, api; 0.6.0, Befund RC2-1, Doku `docs/addon-switches.md` §2 und §3)

| Klasse | Modul | Inhalt |
|---|---|---|
| `api/flight/FlightAssist#modeNameKey(StabilizationMode)` | api (1.7, additiv) | `default` → `Optional.empty()`; Übersetzungsschlüssel des Add-ons für einen Stabilisierungsmodus (Kamera-Drohne: ANGLE N bzw. ATTI, HORIZON C, ACRO S) |
| `input/addon/AddonModeNames` | input | `nameKey(flughilfe, modus)` (strip, leer/`null`/blank/Ausnahme → leer, Warnung im Log einmal je Klasse der Flughilfe, weil der Tab „Modi“ den Hinweis in jedem Bild zeichnet), `name` (sonst `propwashKey` = `propwash.drone.mode.<modus>`), `changed(flughilfe, modus, vomSchalter)` baut „Mode: … (key – radio switch: …)“ bzw. „Mode: …“ |
| `drone/client/PilotSession` | drone | `selectMode` meldet über `AddonModeNames.changed` mit der Flughilfe der Sitzung (`DroneSim#assist`, während GPS Rescue `AddonFlightClient#assist`); `flightModeName(modus)` für den Hinweis „per Taste gewählt“ in `ModesTab`; `modeName(modus)` bleibt die Namen von Propwash |

Der Name wird nur beim Piloten im Client-Thread gefragt (Meldung, Tab „Modi“), nie im Flugmodell; OSD, Flugmodell,
Netzprotokoll, Sprachdateien von Propwash, Goldens und Kalibrierung unverändert. Tests: JUnit
`input.addon.AddonModeNamesTest` (5); Selbsttest „addon-api-17: A1 – Meldung beim Moduswechsel nennt die Flugmodi der
Flughilfe …“ (`drone.client.AddonModeNameSelfTests`, Probe-Flughilfe `propwash_example:mode_name_probe`).

### Drohnen-Item als Deko mit drehenden Props (drone-render, content; 0.6.0, Befund RC2-3, Doku `docs/accessory-gaps.md` §7)

| Klasse | Modul | Inhalt |
|---|---|---|
| `drone/client/render/DroneItemRenderer` | drone-render | `Look(build, wear, paint, battery, blur)`: `blur` ist gesetzt, wenn die Strings von `minecraft:custom_model_data` genau `blur` enthalten (`BLUR`, `blurRequested`); der alte Konstruktor mit vier Werten setzt `false`. Als Teil des Records zugleich Schlüssel des Zwischenspeichers und des GUI-Item-Atlas |
| `drone/client/render/DroneVisuals` | drone-render | `fillPreview(…, spinning)` zeichnet in allen Ansichten von `propwash:drone_build` (Item-Rahmen, `item_display`, Boden, Slot, Hand, Kopf, Regal) die `_blur`-Props; `propBlur`: je Prop nur mit `_blur`-Modell (auch Add-on-Props), sonst statisch, gebrochene Props immer statisch; ohne den Schalter verhalten sich `fill` und `fillPreview` wie vorher |
| `content/paint/PaintItems#store` | content | Lackieren und Lack entfernen ändern nur die Farben von `custom_model_data`, Strings und übrige Werte bleiben (vorher ersetzt bzw. gelöscht) |

Platzieren und Aufheben erzeugt ein neues Item ohne `blur`. `items/drone.json`, Flugmodell, Netzprotokoll, Goldens,
Kalibrierung und Sprachdateien unverändert. Tests: JUnit `drone.client.render.DroneItemBlurTest` (3); Selbsttest
„drone-render: Deko-Blur“ (`DroneItemBlurSelfTests`, `DroneBlurShowcase`, Test-Prop `propwash_rendertest:p13_still`
ohne `_blur`-Modell), Bildschirmfotos `drone_item_blur` und `drone_item_blur_display`.

### Tiefpass vor dem Abtasten im Flashback-Kamerapfad (video, Action-Cam; 0.6.0, Befund RC2-5, Doku `docs/design/flashback-cam.md`)

| Klasse | Modul | Inhalt |
|---|---|---|
| `video/record/path/PathResampler` | video (reines Java) | `perTick` mittelt seit 0.6.0 die Drehung um jeden Tick mit einem Lanczos-Fenster über ±3 Ticks (`ANTI_ALIAS_TICKS`, Integration in Schritten von `QUADRATURE_TICKS` 0,25 Ticks im Log-Raum der Quaternion um die Punktprobe), am Anfang und Ende der Aufnahme schmaler; Position und Sichtfeld bleiben Punktproben, gleichmäßige Drehungen exakt; `at` liefert weiter die ungefilterte Lage |
| `video/record/path/LiveTrackCounter` | video (reines Java) | rechnet dieselbe Lage je Tick wie `perTick` und zählt einen Tick, sobald Proben bis 3 Ticks danach vorliegen |
| `video/record/path/CameraPathBuffer` | video (reines Java) | `rotation(index, dest)` (paketintern) für den Tiefpass |

Schwingungen über etwa 10 Hz falten nicht mehr zu langsamem Schaukeln; mit Vereinfachung „Aus“ Wackeln 0,5–8 Hz
Faktor 0,97–0,99 des Live-Bilds. Höhere Vereinfachungsstufen schaukeln weiter etwas stärker als live (offen, Entscheidung
am Keyframe-Budget). Keine neue öffentliche API, Netzprotokoll, Goldens, Kalibrierung und Sprachdateien unverändert.
Tests: JUnit `video.record.path.AntiAliasTest` (5), `sim.FlashbackPropwashTest`; Selbsttest „video: Action-Cam
(Flashback) – Propwash im Kamerapfad wie live“ (`FlashbackPropwashSelfTests`, Auswertung `PathWobble`, nur mit
Flashback auf Fabric, Grenze langsames Schein-Wackeln 0,5–3 Hz 0,01°).

### Untergrund an Kanten (sim, drone, rescue; 0.6.0, Befund PTP-3, Doku `docs/testing.md` „Turtle-Modus an Gate- und Blockkanten“)

Ohne Untergrund-Raster rechnet das Flugmodell bitgleich wie zuvor (Goldens, Kalibrierung, Turtle-Flip auf flachem Boden).

| Klasse | Modul | Inhalt |
|---|---|---|
| `sim/SimEnvironment.support`, `supportX`, `supportZ`, `supportSpacing`, `SUPPORT_SIDE` (5), `SUPPORT_RANGE` (0,5 m), `clearSupport()` | sim | Raster 5 × 5 unter der Hitbox: Weltkoordinate Y des Untergrunds je Spalte (NaN unbekannt, −∞ frei); `supportSpacing` 0 = kein Raster |
| `sim/GroundSupport` (`MATCH` 3 cm, `EDGE_DROP` 3 cm) | sim | Kontaktpunkte und Schwerpunkt je Spalte. Überhang = Spalte unter dem Schwerpunkt mehr als 3 cm unter der Auflage, Auflage nicht rundum (größte Winkellücke über 180°) |
| `sim/DroneSim` (`EDGE_SLIDE` 0,25) | sim | Im Überhang tragen `GroundSolver`, Box-Kontakt, `HullContact` und das Anheben beim Turtle-Scharfschalten nur Punkte über dem Untergrund. Die Hitbox stützt am nächsten Rand: Moment um die Kante, skaliert auf das Trägheitsmoment um die Kante; Abgleiten nach außen mit (0,25 + Neigung zur Kante) × Last; keine Reibung am Schwerpunkt. Kopfüber im Turtle-Modus nur auf der Hitbox und ohne Turtle-Motoren bremst Reibung |
| `drone/DroneGround` | drone | Bodensonde mit Gate-Rahmen (`ExtraCollider`, auch gedreht). Füllt das Raster ab Hitbox-Unterkante + 1 cm aus Blöcken, Gate-Rahmen und `SoftGround`. Genutzt von `PilotSession` und `ServerFlight` unter 1 m Bodenabstand oder aufliegend; leer als Geist oder ohne Physik |

Andere Entity-Kollisionen außer Gates (z. B. Boote) sieht das Raster nicht, dort gilt das alte Verhalten. Netzprotokoll,
API, Loader-Code, Goldens, Kalibrierung und Sprachdateien unverändert. Tests: JUnit `sim.TurtleEdgeTest` (6) mit der
Testwelt `sim.LedgeWorld`; Selbsttest `turtle:` an Gate-Balken- und Blockkante und mitten auf dem Balken
(`drone.client.CovTurtleEdgeSelfTests`).

### „Alle“ mit einer Zeile je Pilot und Klasse (race; 0.6.0, Befund ALLBOARD-1, Doku `docs/race-data.md` §2.2)

| Klasse | Modul | Inhalt |
|---|---|---|
| `race/Leaderboard` | race | `top(null, n)` und `assistedTop(null, n)` liefern jede Zeile der Wertung (Bestzeit je Pilot und Klasse) nach Zeit, nicht mehr nur die erste je Pilot; `ranked(abschnitt)` gibt jeder Zeile ihren Gesamtplatz (vorher −1 außer bei der besten des Piloten); `rank(pilot, null)` = beste eigene Zeile; neu `allRank(pilot, klasse, abschnitt)` = Platz der Zeile dieser Klasse in „Alle“ (−1 ohne Zeile) |
| `race/export/RaceSnapshots` | race | `lapRank(board, pilot, klasse, abschnitt)` über `allRank`; `PERSONAL_BEST` (Runde) und `LAP_COMPLETED` nennen den Platz der Zeile der geflogenen Klasse |

Fenster, erstes Paket, Seiten, `BoardTotals`, `BoardIndex`, Teilen-Code, Replay und Geist arbeiten unverändert je Zeile
und zeigen damit jede Klasse eines Piloten; „Meine Zeit“ springt zur besten eigenen Zeile. `/propwash race
leaderboard`, `BoardQuery.laps()` ohne Klasse und `laps.clean`/`laps.assisted` im JSON-Spiegel folgen derselben Regel.
Rennzeiten über mehrere Runden bleiben je Pilot und Rundenzahl. Protokoll, NBT, Kursformat und JSON-Schema unverändert.
Tests: JUnit `race.data.LeaderboardClassRowsTest` (5); Selbsttest „race-data: „Alle“ mit Bestzeit je Pilot und Klasse …“
(`race.client.RaceDataBoardSelfTests#classesStep`), Bild `race_data_board_classes_{en,de}`.

### Signal-Repeater überarbeitet (relay, video, drone; 0.6.0, Befund RC2-2, Entwurf `docs/design/signal-repeater.md` §9, ersetzt im Abschnitt „Signal-Repeater und Kreativ-Brille in Rennen“ die Zeilen zu `RelayRules`, `RelayLinks`, `RelayCandidates` und `RepeaterIndex`)

Jede Teilstrecke reicht so weit wie die direkte Verbindung (bis 0.5.x hart 128 Blöcke, voll bis 112).

| Klasse | Modul | Vertrag |
|---|---|---|
| `relay/RelayRules` | relay (reines Java) | ohne feste Reichweite: `inReach(d, hopReach)`, `span(n, hopReach)` = (n + 1) × hopReach; `RANGE_BLOCKS`, `FULL_RANGE_BLOCKS`, `reachFactor` und `reachPenaltyDb` entfallen |
| `relay/RelayLinks` | relay | `videoHopReach(system, range)` = `LinkBudget.maxReachBlocks` (Reichweite samt Leistung bis zur Verlustgrenze); `videoHopMarginDb` ohne Rand; `controlHopReach(freeRange, creativeRange)` = 1200, mit Kreativ-Reichweite × 1,5, frei = unendlich (dann keine Funk-Kette); `controlHopQuality(d, free, creative, videoObstructionDb)` = `RadioLink.quality` mit 0,6 × Abschattung; `controlQuality` unverändert |
| `relay/RelayCandidates#select(…, relays, hopReach, max, out)` | relay (reines Java) | Ellipse aus `span(relays, hopReach)` |
| `relay/RepeaterIndex` | relay | `keep(pos, owner)` merkt einen Repeater beim Entladen seines Chunks (Eintrag ohne Besitzer), `remove` nur beim Abbau, `prune(level)` wirft gemerkte Repeater aus, deren Chunk wieder geladen ist und keinen Repeater mehr hat; `placed(level, pos)` (Chunk nicht geladen = steht noch), `remembered()`, `remembers(pos)` |
| `relay/SignalRepeaterBlockEntity#setRemoved` | relay | Block steht im geladenen Chunk noch → `keep`, sonst `remove` |
| `relay/RelayReach` | relay | `linked(level, pilot, drone, hopReach, relays)`: direkt oder Kette aus höchstens `relays` Repeatern mit Teilstrecken ≤ `hopReach` (nur Entfernung, Breitensuche über die 16 besten Kandidaten), Abgleich gemerkter Repeater alle 20 Ticks; `chain(…)` statisch für JUnit |
| `relay/RelayServer#inRange(player, drone, releaseRange, serverLimit)` | relay | direkt ≤ Freigabe-Reichweite; sonst nur mit unbegrenzter Reichweite (`serverLimit` unendlich) und endlicher Freigabe (1250) über eine Kette nach der Welt-Regel `propwash:signal_repeaters`. Mit begrenzter Reichweite (an die Sichtweite gebunden) bleibt die Grenze direkt |
| `drone/DronePiloting` | drone | Freigabe im Flug und beim Zustand vom Client über `RelayServer.inRange` statt der direkten Entfernung; Meldung `propwash.drone.pilot.lost_range` unverändert |
| `relay/client/RelayVideo#tick(…, relayLimit, hopReach)` | relay | Teilstrecken bis `hopReach` aus `VideoState#relayReach` (Videosender der Drohne samt Leistung aus `VtxRuntime`); merkt `lastSystem`, `lastRange`, `lastObstruction`; Knoten und Strecken lesbar (`nodeCount`, `nodeX/Y/Z`, `edgeLossDb`, `linked`, `chainIndex`); Abgleich des Verzeichnisses alle 20 Ticks |
| `relay/client/RelayControl#quality(…, relayLimit, video)` | relay | mit laufendem Bild Funk-Teilstrecken über die Knoten und gemessenen Abschattungen der Video-Kette, sonst Kandidaten aus dem Verzeichnis nur nach Entfernung; `PilotSession` übergibt `VideoState#relayFor(drone)` |
| `relay/client/RepeaterSignals` | relay (Client) | Balken je Repeater (`bars(pos)`: beste Strecke zu einem der drei nächsten Repeater, in der genutzten Kette die schwächere der beiden Teilstrecken), Signalmesser in der Hand (Aktionsleiste `propwash.relay.meter.*`), 8 000 bzw. 6 000 Zellen je Tick, Bezug zuletzt geflogener Videosender, sonst `VideoType.ANALOG_RACE` |
| `relay/client/SignalRepeaterRenderer` | relay (Client) | Block-Entity-Renderer mit vier LED-Balken auf dem Gehäuse, angemeldet in `RelayClient.init` über `ClientRegistrar.blockEntityRenderer` |
| `relay/RepeaterMeter`, `relay/SignalRepeaterItem` | relay | Balken im Item-Symbol nur mit aktivem Signalmesser; grün ab drei Balken, gelb zwei, rot einer |
| `video/client/ClientLinkWorld` | video | eigener `ClientBlockLoss` für Rechnungen außerhalb von `VideoState` |

Netzprotokoll, API, Loader-Code, Goldens und Kalibrierung unverändert; neue Sprachschlüssel in `common/src/lang/fix-2110/`.
Tests: JUnit `relay.RelayRangeTest` (4), `relay.RepeaterMeterTest` (3), `relay.RelayPathsTest` (16); Selbsttests `relay:`
aus `relay.client.RelayFieldSelfTests` (Raum hinter Betonwand und U-Gang, Signalmesser und Balken, Freiland bis zum
Abbruch mit Repeatern bei 60 % und 100 %, Funk über drei Repeater weiter als 1250 Blöcke), Bildschirmfotos
`signal_repeater_room_fpv`, `signal_repeater_corner_fpv`, `signal_repeater_signal_bars`, `signal_repeater_field_rpt`.

### Zubehör vorn und unten trägt die Drohne, FPV-Auge an der gezeichneten Kamera (sim, drone, drone-render; 0.6.0, Befund PTP-2, Doku `docs/accessory-hang.md` §6, ergänzt die Zeile `FpvView#coverInsideBlock` oben)

| Klasse | Modul | Inhalt |
|---|---|---|
| `sim/AirframeSpec` | sim | `GROUND_POINTS` = `ACCESSORY_GROUND` (20) + `ACCESSORY_POINTS` (8); `accessoryPointCount()`, `accessoryX/Y/Z(k)` (Bodenpunkte des Zubehörs relativ zum Schwerpunkt), `accessoryDepth(lage)` (wie weit das Zubehör in dieser Lage unter den Fuß der Hitbox reicht, sonst 0) |
| `sim/ParamDerivation` | sim | ragt Zubehör vorn oder unten mehr als `ACCESSORY_STAND_MIN` (5 mm) unter die Rahmenhülle, werden die vier tiefsten Ecken seiner Box (`AccessoryAnchor#corners`, mit Neigung und Streckung) harte Bodenpunkte ab `ACCESSORY_GROUND`; Hülle, Hitbox, Fuß und Schwerpunkt-Abstand bleiben aus dem Rahmen |
| `drone/QuadcopterEntity` | drone | lose abgestellt (ohne Pilot, mit Schwerkraft) hält `standOnAccessories` die Drohne um `accessoryDepth` über dem Boden und setzt sie auf den Boden |
| `drone/client/render/DronePhysique` | drone-render | nimmt die Zubehör-Bodenpunkte zur Hülle für `placeInWorld`, das Modell sitzt an der Physik |
| `drone/client/render/DroneVisuals#displayLift` | drone-render | Versatz, um den das Modell gezeichnet wird (wie `placeAtDisplayedPose` mit Bodenkontakt) |
| `drone/client/FpvView` | drone | Auge und Anker des Kameraschutzes (`anchor()`) um `displayLift` verschoben, im Pilot- und Zuschauer-FPV; `coverInsideBlock` prüft nur den Punkt selbst: sichtversperrender Block oder sichtbare Form eines verdeckenden Blocks (`canOcclude`, Stufe, Treppe, Schnee, Teppich …) am Auge, am Schwerpunkt nur sichtversperrende Blöcke |

Flache Teile wie LED-Streifen (bis 4,6 mm) zählen nicht. Hitbox und Rennwertung unverändert; Goldens und Kalibrierung
byte-gleich (Presets ohne tiefes Zubehör rechnen bitgleich). Eine Drohne mit hohem Zubehör steht schräg und kann über
dem Scharfschalt-Winkel (`pid.armMaxAngleDeg`) am Boden nicht scharf geschaltet werden. Netzprotokoll, API und
Sprachdateien unverändert. Tests: JUnit `sim.AccessoryGroundTest` (4), `accessory.hang.AccessoryStandTest` (2),
`accessory.fix.PayloadLimitTest` (flache Platte); Selbsttest „drone: steht auf Zubehör“ (`AccessoryStandSelfTests`) und
„drone: FPV-Kamera nie im Block“ (`CameraClipSelfTests`), Bildschirmfotos `accessory_stand_*`.

### Zuschauen mit OSD und Rennen des Piloten (video, osd, race; 0.6.0, Befund RC2-4, Doku `docs/spectate-feed.md`, ergänzt den Abschnitt „Videokanäle und Zuschauen“ oben)

| Klasse | Modul | Inhalt |
|---|---|---|
| `video/PilotFeed` | video (reines Java) | Live-Werte und Renn-Zustand des Piloten in einem Paket (Sticks, Gas, Modus und Schalter, Akku, Zeiten, Warnungen, Funk, RPT, Figuren, Heimatpunkt, Rettung, Kurs, Phase, nächstes Gate, Start-Gate, Countdown, Frühstart, Zeit seit dem Start, Runde, Rundenzeiten, Label der Flughilfe höchstens `MAX_LABEL` 48 Zeichen); Zeiten laufen zwischen zwei Paketen weiter |
| `video/PilotLook` | video (reines Java) | OSD-Layout im Format des OSD-Sharecodes (höchstens 8192 Zeichen), Einheit und Stick-Mode; `config(eigene)` ersetzt nur das gezeigte OSD, die eigenen Einstellungen bleiben unverändert |
| `video/PilotFeedServer`, `video/FeedRate` | video, server | nimmt `PilotFeedC2S`/`PilotLookC2S` nur vom Piloten der Drohne an (Entity der eigenen Drohne), höchstens 30 Live-Pakete je Sekunde (Vorrat 10) und 2 Layouts je Sekunde (Vorrat 4), leitet nur an Zuschauer genau dieser Drohne weiter (`SpectateServer#watchersOf`); `onWatchStarted` schickt das zuletzt erhaltene Layout sofort; `simulate`/`simulateLook` für Selbst- und Beitrittstests |
| `video/client/PilotFeedClient` | video | schickt nur bei Zuschauerzahl über 0 jeden Client-Tick `PilotFeed` und bei Änderung (geprüft alle `LOOK_CHECK_TICKS` 10 Ticks) `PilotLook`; beides nur über `canSend` |
| `video/client/WatchedPilot` | video | Zuschauer: `feed()` (frisch höchstens 1 s, sonst `null` und wie früher Werte aus der Drohne), `osdConfig()`, `speedUnit()`, `stickMode()` für `OsdHud`, `OsdRenderer`, `LiveOsdSource`, `GateRenderer`, `ScoreAreaRenderer` und `RaceHud`; beim Ende des Zuschauens geleert |
| `video/net/PilotFeedC2S`, `PilotFeedS2C`, `PilotLookC2S`, `PilotLookS2C` | video | `propwash:video_pilot_feed`, `video_watched_feed`, `video_pilot_look`, `video_watched_look`; optional, Netzprotokoll bleibt 17 |

Der Zuschauer sieht das OSD des Piloten mit dessen Layout, Einheit, Stick-Mode und Live-Werten und im Rennen das
nächste Gate grün mit Durchflugfläche (im Countdown das Start-Gate rot, nach Frühstart bernsteinfarben), Startampel,
Countdown, „LOS!“, Rundenmeldung und „ZIEL“. Der Operator am Gimbal sieht weiter sein eigenes OSD. Keine neue
öffentliche API, Goldens und Kalibrierung unverändert. Tests: JUnit `video.PilotFeedTest` (9); Selbsttests
`video: Zuschauen – Renn-Zustand, OSD-Layout und Sticks des Piloten beim Zuschauer` und `video: Zuschauen – Pilot
schickt Renn-Zustand, OSD-Layout und Sticks nur mit Zuschauer` (`SpectateFeedSelfTests`, simulierter Sender
`dev.SimulatedPilotFeed`), Bild `spectate_pilot_osd`; Beitrittstest `watchsolo` mit `racehud`, `osd`, `sticks` und
`gate`.

### Schnelle Drehungen im Flashback-Kamerapfad (video, Action-Cam; 0.6.0, Befund AUTO44-1, Doku `docs/design/flashback-cam.md`)

| Klasse | Modul | Inhalt |
|---|---|---|
| `video/record/path/PathResampler` | video (reines Java) | Ergänzung zum Tiefpass in `perTick` (Abschnitt „Tiefpass vor dem Abtasten im Flashback-Kamerapfad“): Der Logarithmus der Drehung im Log-Raum der Quaternion um die Punktprobe wird von der Probe vor dem Tick aus nach beiden Seiten entfaltet (`unfold`: je Probe der Zweig `v + 2πk·v̂`, der der Nachbarprobe am nächsten liegt), damit Drehungen über 180° im Fenster von ±3 Ticks nicht auf den Gegenzweig springen; ohne Sprung im Fenster bitgleich wie vorher |

Gleichmäßige Drehungen bleiben bis 3461°/s (Ratengrenze auf drei Achsen) auf 0,001° genau, vorher ab etwa 1200°/s
nicht mehr (1998°/s: 2,29°, mit 20 % Schwankung 11,83° Tick-Lage). Über 3600°/s (180° je Tick) kann ein Keyframe je
Tick die Drehung grundsätzlich nicht zeigen. Keine neue öffentliche API, Netzprotokoll, Goldens, Kalibrierung und
Sprachdateien unverändert. Tests: JUnit `video.record.path.AntiAliasTest` (7), darunter `steadyTurnsStayExact` bis zur
Ratengrenze, `fastTurnsWithRippleKeepTheirTicks` und `fastTurnsExportLikePointSampledTicks`.

### Untergrund-Raster bis an den Rand der Hitbox (sim, drone; 0.6.0, Befund AUTO45-1, ergänzt den Abschnitt „Untergrund an Kanten“ oben, Doku `docs/testing.md` „Turtle-Modus: Totzone am Rand der Hitbox“)

| Klasse | Modul | Inhalt |
|---|---|---|
| `sim/SimEnvironment.SUPPORT_RIM` (1 µm), `supportSpacing(width)` | sim | Spaltenabstand (Breite − 2 µm)/4 statt Breite/5. Die äußeren Spalten liegen 1 µm innerhalb des Hitbox-Rands, damit das Raster jede Auflage der Hitbox sieht (vorher nur bis ±0,4 × Breite). Genutzt von `drone/DroneGround#fillSupport` und der Testwelt `sim.LedgeWorld` |

Die Kante bleibt sichtbar, bis die Hitbox den Untergrund verlässt; die Kanten-Schätzung ist damit auf bis zu 12,5 %
statt 10 % der Breite genau. Ein Überhang zwischen 1e-7 (Toleranz der Minecraft-Kollision) und 1 µm bleibt
unsichtbar. Ohne Raster und auf flachem Boden unverändert; keine neue öffentliche API, Netzprotokoll, Loader-Code,
Goldens, Kalibrierung und Sprachdateien unverändert. Tests: JUnit `sim.TurtleEdgeRimTest` (4); Selbsttest `turtle:`
am Rand der Hitbox (`drone.client.CovTurtleRimSelfTests`, 4 Schritte).

### Zäune, Mauern und Zauntore im Untergrund-Raster (drone; 0.6.0, Befund PTP3B-1, ergänzt den Abschnitt „Untergrund an Kanten“ oben, Doku `docs/testing.md` „Turtle-Modus an jeder Kante“)

| Klasse | Modul | Inhalt |
|---|---|---|
| `drone/DroneGround#blockTop`, `TALL_SHAPE_REACH` (1) | drone | Geprüft werden je Rasterspalte die Blöcke von der Hitbox-Unterkante plus 1 cm bis einen Block unter dem Strahlende. Es zählt die höchste Kollisionsfläche. So sieht das Raster die 1,5 Blöcke hohen Formen von Zäunen, Mauern und Zauntoren, die zum Block darunter gehören (vorher `level.clip`, das nur die Blöcke auf dem Strahl prüft). Beginnt die Spalte in einer Form, ist der Wert NaN |

Für Formen bis zu einem Block Höhe kommt dasselbe heraus wie mit `level.clip`. Gilt im Client und beim Server-Flug
ohne Pilot; das Flugmodell (`sim/`) ist unverändert. Keine neue öffentliche API, Netzprotokoll, Loader-Code, Goldens,
Kalibrierung und Sprachdateien unverändert. Tests: JUnit `drone.DroneGroundTest` (7, mit dem alten Suchbereich 3 rot)
und `sim.TurtleAnyEdgeTest` (7); Selbsttest `turtle: Kanten` (`drone.client.CovTurtleEdgeSelfTests`) mit
Bildschirmfotos `turtle_edge_*`.

### Standbeine unter hängendem Zubehör, Halt lose abgestellter Drohnen je Zubehör-Ecke (sim, drone, drone-render; 0.6.0, Befunde AUTO47-1 und AUTO47-2, ergänzt den Abschnitt „Zubehör vorn und unten trägt die Drohne“ oben, Doku `docs/accessory-hang.md` §6)

| Klasse | Modul | Inhalt |
|---|---|---|
| `sim/AirframeSpec` | sim | `STAND_LEGS` (4), `GROUND_POINTS` = 20 + 8 + 4 = 32; `accessoryPointCount()` zählt nur die Zubehör-Ecken, `standPointCount()` Ecken und Standbeine (`accessoryX/Y/Z(k)` für beide, erst die Ecken) |
| `sim/ParamDerivation#groundPoints` | sim | gibt es Zubehör-Bodenpunkte, folgen vier Standbeine an den Rahmenecken (Motoren `hull 4–7`, bei Ducts die Ringe `ground 16–19`) in der Ebene der tiefsten Zubehör-Ecke; die Drohne steht eben auf dem Zubehör und lässt sich scharf schalten |
| `sim/GroundSolver#absorbs` | sim | Zubehör-Ecken und Standbeine (ab `ACCESSORY_GROUND`) federn nicht zurück (Rückprall 0), harte Kontakte ohne Eingraben und ohne Prop-Schaden wie bisher |
| `drone/client/render/DronePhysique` | drone-render | nimmt alle Standpunkte (`standPointCount`) zur Hülle für `placeInWorld` |
| `drone/QuadcopterEntity#standOnAccessories` | drone | lose abgestellt: Boden unter jeder Zubehör-Ecke einzeln über eine dünne Säule an ihrer Lage (`groundGap` mit eigener Box, `STAND_PROBE` 1 mm), Standbeine zählen nicht; `accessoryDepth` nur noch als Vorprüfung |

Bauten ohne tief hängendes Zubehör rechnen bitgleich (Goldens und Kalibrierung byte-gleich). Hitbox, Rennwertung,
Netzprotokoll, API und Sprachdateien unverändert. Tests: JUnit `sim.AccessoryGroundTest` (7),
`accessory.fix.PayloadLimitTest` (wieder mit 58 mm hoher Box); Selbsttest „drone: steht auf Zubehör“ (8 Schritte, im
FPV eben ohne Scharfschalt-Sperre, lose an der Stufenkante ohne Schweben).

### Boden unter Zubehör-Ecken und Standbeinen im Flugmodell (sim, drone, rescue; 0.6.0, Befund STAND3-1, ergänzt den Abschnitt „Standbeine unter hängendem Zubehör …“ oben, Doku `docs/accessory-hang.md` §6)

| Klasse | Modul | Inhalt |
|---|---|---|
| `sim/SimEnvironment` | sim | `POINT_SUPPORT` (12 = 8 Zubehör-Ecken + 4 Standbeine), `pointSupport`, `pointX`, `pointZ`, `pointCount`: Boden genau unter den Bodenpunkten ab `AirframeSpec#ACCESSORY_GROUND` in der Reihenfolge der Punkte, Werte wie im Raster (NaN im Block, −∞ kein Boden bis 0,5 m); `copyTo` kopiert, `clearSupport` leert |
| `sim/GroundSupport` | sim | `sanitize` übernimmt die Punkte nur mit gültigem Raster und endlicher Lage; `groundPlanes(…, overhang)` gibt ohne Überhang für Zubehör-Ecken und Standbeine den Boden unter dem Punkt (Messung, solange der Punkt höchstens einen halben Rasterabstand von ihr entfernt ist, sonst das Raster), Rahmenpunkte die Ebene; ohne Zubehör oder Raster `null` wie bisher; liegt ein Punkt tiefer, als gemessen wurde, gilt die Ebene; `capStandLegs`: ein Standbein trägt höchstens bis zur Höhe, auf der eine Zubehör-Ecke Boden hat (versetzt um deren Höhe über dem Standbein) |
| `sim/DroneSim` | sim | `groundImpulse` und der Bodenkontakt im Teilschritt rufen `groundPlanes` immer, mit `overhang` bzw. `edge` als Schalter |
| `drone/DroneGround#fillPoints` | drone | misst je Frame mit `column` (Blöcke, Gates und Kollider, weicher Boden) ab Fuß + 1 cm bis 0,5 m tief unter jedem Punkt; Lage aus `AirframeSpec#groundX/Y/Z` und der Lage der Entity wie `QuadcopterEntity#standOnAccessories` |
| `drone/client/PilotSession`, `rescue/ServerFlight` | drone, rescue | rufen `fillPoints` direkt nach `fillSupport` |

Auf ebenem Boden bitgleich (Goldens und Kalibrierung byte-gleich); Netzprotokoll, API, Loader-Code und Sprachdateien
unverändert. Grenzen: Gates und Kollider außerhalb der Hitbox (+5 cm) sieht die Messung nicht; eine Zubehör-Ecke mehr
als 0,48 m unter dem Fuß nimmt weiter die Ebene. Tests: JUnit `sim.AccessoryLedgeTest` (4), `LedgeWorld` füllt die
Punkte wie das Spiel; Selbsttest „drone: steht auf Zubehör“ (9 Schritte, neu gesteuert an der Stufenkante mit Bild
`accessory_stand_edge_piloted`).

### Zubehör vorne sitzt am Rahmen (build, Teile-Daten; 0.6.0, Befund RC3P-5, ersetzt im Abschnitt zur Halterung vorne die Anker der eingebauten Rahmen, Doku `docs/accessory-hang.md` §7)

Die Anker vorn liegen an der Vorderkante der Bodenplatte statt 6–9 mm davor: `cinewhoop_3` (0; −3; −30), `freestyle_5`
(0; 0; −64), `race_5` (0; 0; −56), `long_range_7` (0; 0; −68), gleich in den Rahmen-JSONs, `drone_layout.json` und
`tools/assets/parts/layout.py`. Soll für jedes Zubehör vorne an jedem Rahmen: höchstens 1 mm zwischen gezeichnetem
Zubehör und Rahmen, höchstens 2 mm Überschneidung mit dem Quader aus `size_mm`. Die Ersatzformel für Rahmen ohne Anker vorn
bleibt `(0; 0; −0,3 × Radstand)`. Prüfung: JUnit `accessory.hang.FrontAnchorFitTest`, Selbsttest
„addon-api-17: Zubehör am Rahmen“ (devtest `drone.client.render.AccessoryFit`, `interact.client.AccessoryFitSelfTests`)
über alle angemeldeten Rahmen mit Ausreißer-Liste und Soll-Anker für Add-on-Rahmen. Kalibrierung und Goldens
byte-gleich; Netzprotokoll, API, Loader-Code und Sprachdateien unverändert.

### „Am Boden“ für autonome Starts: Kontakt der Kollisionsform, Grund zu NOT_READY (rescue, drone, api; 0.6.0, Befund RC3P-4, ergänzt die Abschnitte zu `addon-autonomy` und „Standbeine unter hängendem Zubehör …“ oben, Doku `docs/addon-autonomy.md` §1, §3)

| Klasse | Modul | Inhalt |
|---|---|---|
| `rescue/GroundContact` | rescue | `gap(drone, range)`: kleinster Abstand von Hitbox (`supportGap`) und jedem genutzten Bodenpunkt (`AirframeSpec#groundUsed`, Rahmen, Zubehör-Ecken, Standbeine) unter dem Fuß der Hitbox, gedreht mit der Lage der Entity und skaliert wie `standOnAccessories`; `grounded(gap, speed)`: unter `TOUCH` 0,02 m und `REST_SPEED` 0,25 m/s; reine Form `gap(spec, orientation, size, hitboxGap, range, Column)` für JUnit |
| `drone/QuadcopterEntity#columnGap` | drone | Boden unter einer 1 mm dünnen Säule ab dem Fuß (dieselbe Box wie in `standOnAccessories`, über `groundGap`) |
| `rescue/AddonAutonomy` | rescue | `grounded` über `GroundContact`; `notReady(grounded, takeoff, handover, battery)` entscheidet `NOT_READY` und den Grund für `begin` und `notReadyReason`; `GROUND_PROBE`/`GROUND_GAP` entfallen (`GroundContact.PROBE`/`TOUCH`) |
| `rescue/PilotTakeover#seamless` | rescue | in der Luft heißt Abstand der Kollisionsform ab `TOUCH` (vorher Hitbox ab 0,08 m) |
| `api/drone/AutonomyNotReady`, `AutonomyAccess#notReadyReason` | api | `NOT_ON_GROUND`, `NO_TAKEOFF`, `NO_BATTERY`, `REMOVED`; `default` leer, Propwash über `apiimpl/Api17Registries.Autonomy` |

Bauten ohne Zubehör unter der Hitbox messen wie bisher nur die Hitbox, die Grenze ist aber 2 statt 8 cm: knapp über
dem Boden schwebend gilt eine Drohne jetzt als in der Luft. Flugmodell, Goldens, Kalibrierung, Netzprotokoll und
Sprachdateien unverändert; API 1.7 additiv. Tests: JUnit `rescue.AutonomyGroundTest` (5), `rescue.AutonomyNotReadyTest`
(3); Selbsttest „addon-api-17: B7 – Start aus dem Stand auf hohem Zubehör“.

### Zubehör einrücken (api, build, parts, drone-render; 0.6.0, Befund FB1-9, API 1.7 additiv, ergänzt „Zubehör vorne sitzt am Rahmen“ oben, Doku `docs/accessory-hang.md` §8)

| Klasse | Modul | Inhalt |
|---|---|---|
| `api/part/AccessoryDefinition` | api | letzte Record-Komponenten `recessMm`, `dropMm` (0 bis Tiefe bzw. Höhe, sonst Fehler mit Feldname), Builder `recess`, `drop`, 16-Argument-Konstruktor bleibt |
| `parts/PartJson` | parts | `recess_mm`, `drop_mm`; schreibt die Felder nur, wenn sie nicht 0 sind |
| `build/AccessoryType` | build | `boxMinMm`, `boxMaxMm`, `lensMm`, `ledMm` verschoben (hinten +z, unten −y), `centerMm` und `muzzleMm` folgen; `modelBoxMinMm`, `modelBoxMaxMm`, `modelLensMm`, `modelLedMm` unverschoben für die Ersatzform |
| `build/AccessoryAnchor#modelOrigin` | build | Zeichenposition: Anker plus Verschiebung in den Achsen des Zubehörs; ohne Felder genau der Anker |
| `drone/client/render/DroneAssembly`, `FallbackShapes` | drone-render | Körper an `modelOrigin` (auch `ItemDisplay`, über `body.position`), Sockel oben am Anker; Ersatzform aus den unverschobenen Werten |
| `drone/client/render/AccessoryFit` (devtest) | devtest | `overlapMm` nur vor dem Anker, `wantedOverlapMm` im eingerückten Teil, Soll-Anker ohne Einrücken |

Flugmodell (`ParamDerivation`), `MountPoses`, `DispenseOrigin` und `VisualHull` rechnen schon mit Ecken, Mitte und
Körperposition und folgen ohne eigene Änderung; `MountPose#anchor` und die Richtungen bleiben. Die Überschneidung mit dem
Unten-Platz oder dem Akku unter dem Rahmen lässt Propwash zu (Messwerte in der Doku). Ohne die Felder ist alles bitgleich:
Kalibrierung und Goldens unverändert. Netzprotokoll und Pakete unverändert; im Teile-Handshake ändern sich die Hashes aller
Zubehörteile, weil der Record zwei Komponenten mehr hat (Anhebung auf 19 macht WA-W2g). Tests: JUnit
`accessory.hang.AccessoryRecessTest` (11), `sim.AccessoryRecessFlightTest` (1); Selbsttest
„addon-api-17: Zubehör eingerückt“.

### Greifer am Server: Fracht-API und Aufnehmen ohne Pilot (interact, rescue, api; 0.6.0, Befund FB1-10, ergänzt „Treffer und Greifer“ §15 und die Abschnitte zu `addon-autonomy`, Doku `docs/addon-autonomy.md` §11, Handbuch §21.23)

| Klasse | Modul | Inhalt |
|---|---|---|
| `api/accessory/AccessoryRegistry` | api | `cargo(Entity)`, `grabCargo(Entity, ServerPlayer)`, `releaseCargo(Entity, ServerPlayer)` als `default` mit leerem Ergebnis, `false` bzw. 0; Propwash über `accessory/AccessoryBehaviors` |
| `interact/DroneCargo` | interact | `grab`: Griff 5 s offen (`Hold#openGrab`, `GripperSpec.GRAB_TICKS` = 100), sofortiger Versuch, schließt nach der ersten Aufnahme, beim Loslassen, Entschärfen und ohne Greifer; `releaseBy`: `COMMAND` mit Verursacher, gibt die Zahl der Items zurück; `cargo`: Kopien; Meldungen ohne Pilot an den letzten Verursacher (`Hold#actor`) |
| `interact/GripperSpec` | interact | `picksUp(Greifer, scharf, gesteuert, greift)`: mit Pilot wie bisher, ohne Pilot nur während eines Griffs; `mayTake(Ziel, Pilot, Verursacher)` |
| `rescue/ServerFlight#followCargo` | rescue | geänderte Fracht im Server-Flug (Heimflug, Add-on-Flug) über `AccessoryPayload#flightBuild` und `DroneSim#setAirframe` ins Flugmodell, wie `PilotSession#syncConfiguration` am Client |

Ohne Griff über die API nimmt eine Drohne ohne Pilot weiter nichts auf (Heimflug unverändert); mit Pilot greift der
Greifer wie seit 0.5.0. Ohne Fracht-Änderung tut `followCargo` nichts. Flugmodell, Goldens, Kalibrierung, Netzprotokoll,
Pakete und Sprachdateien unverändert; API 1.7 additiv, die Versionsnummer hebt die Veröffentlichung. Tests: JUnit
`interact.CargoApiTest` (6); Selbsttest „addon-api-17: B7 – Fracht ohne Pilot“.

### Drohnen und Sculk (interact, drone, stats, rescue, server; 0.6.0, Befund FB1-11, Doku `docs/drone-sculk.md`)

| Klasse | Modul | Inhalt |
|---|---|---|
| `interact/DroneVibrations` | interact | `tick`: scharf und Motoren im Mittel ab `MOTOR_THRESHOLD` 0,1 der Höchstdrehzahl, alle `INTERVAL_TICKS` 10 Ticks `elytra_glide` an der Drohne; `crashed`: `hit_ground` mit dem Block unter der Drohne; `muted`: Regel aus; `pilot`: Pilot als `ServerPlayer` für den Kreischer |
| `drone/QuadcopterEntity` | drone | `tickServer` ruft `DroneVibrations.tick` nach `DroneBlockTriggers.tick`; `dampensVibrations` = `DroneVibrations.muted`; `getMovementEmission` bleibt `NONE` |
| `stats/PilotStats#onCrash` | stats | nach der Prüfung des Absenders und nur für vom Budget zugelassene Abstürze `DroneVibrations.crashed` |
| `rescue/ServerFlight#fly` | rescue | Absturz im autonomen Flug: `DroneVibrations.crashed` nach `write` |
| `mixin/drone/SculkShriekerBlockEntityMixin` | drone | `tryGetPlayer` liefert für eine Drohne mit Pilot den Piloten, wie Vanilla für ein Fahrzeug den Steuernden |
| `server/rules/WorldRuleSpecs#DRONES_TRIGGER_SCULK` | server | Welt-Regel `propwash:drones_trigger_sculk`, Schalter, Gruppe Spiel, Standard an, für Rennen neutral, in allen Voreinstellungen an |

Vor 0.6.0 gab eine geflogene Drohne keine Vibration ab: Der Server bewegt sie nicht über die Vanilla-Bewegung, und
`getMovementEmission` ist `NONE`. Abgestellte Drohnen ohne Pilot melden beim Aufsetzen nach einem Fall wie bisher Vanillas
`hit_ground`; mit der Regel aus dämpft `dampensVibrations` auch das und den Glasbruch. Wolle, Teppich und Reichweiten regelt
Vanilla. Flugmodell, Goldens, Kalibrierung (byte-gleich), Netzprotokoll und Pakete unverändert; `WorldRulesS2C` trägt einen
Eintrag mehr, das Register `minecraft:game_rule` eine Regel mehr. Tests: JUnit `interact.DroneVibrationsTest` (5),
`race.RaceRulesTest` (Regel als neutral eingeordnet); Selbsttest-Gruppe `sculk:` (6 Schritte).

### Add-on-API 1.8.0 (api; 0.6.0 ab `0.6.0-dev.14`)

| Klasse | Modul | Inhalt |
|---|---|---|
| `api/PropwashApi` | api | `API_VERSION` 1.8.0 (vorher 1.7.0 seit `0.6.0-dev.2`); `requireVersion(modId, "1.7")` bleibt erfüllt |
| `api.ApiContractTest` (Test) | api | `semanticVersionRules` mit 1.8.0, `api18CargoAndRecessExist`: `AccessoryRegistry#cargo`, `#grabCargo`, `#releaseCargo` als `default`, `AccessoryDefinition#recessMm`, `#dropMm` als letzte Komponenten, Builder `recess`, `drop` |

Die Nebenversion 1.8 enthält nur die zwei Ergänzungen nach `0.6.0-rc.3`: Fracht am Greifer (Befund FB1-10, Abschnitt
„Greifer am Server“ oben) und Zubehör einrücken (`recess_mm`, `drop_mm`, Befund FB1-9). Add-ons, die sie nutzen,
verlangen `requireVersion(modId, "1.8")`; Versionsbereiche der Abhängigen bleiben (`>=0.6.0-`, `[0.6.0-alpha,)`). Die
Selbsttests `api:` (Version, Vertrag) und `addon-api:` (Version 1.6) prüfen 1.8.0. `docs/RELEASING.md` §2 nennt die Nummer
(`ReleasingApiVersionTest`). Netzprotokoll unverändert.

### Sculk nach Server-Flügen (drone, interact, rescue; 0.6.0, Befund OT1-2, Doku `docs/drone-sculk.md` §1 und §4)

| Klasse | Modul | Inhalt |
|---|---|---|
| `drone/QuadcopterEntity#checkFallDamage` | drone | gibt Vanilla als Fallweg nur die Höhe weiter, um die `super.move` die Drohne wirklich bewegt hat (`moveFromY` vor dem Aufruf, außerhalb von `move` der gemeldete Weg) |
| `interact/DroneVibrations#fallMoved` | interact | reine Rechnung dafür: Höhe nachher minus vorher, ohne Ausgangshöhe der gemeldete Weg |
| `rescue/ServerFlight#move`, `rescue/AutonomousReturns#descend` | rescue | Fallhöhe nach jeder Bewegung des Server-Flugs auf 0, ein Flug hinterlässt keine gesammelte Sinkstrecke |

Vanillas `Entity#move` setzt eine Bewegung, die die Kollision auf weniger als etwa 0,3 mm kürzt, nicht um, meldet den
gekürzten Weg aber an `checkFallDamage`. Eine Drohne, die ein Server-Flug knapp über dem Boden abstellt, meldete so bei
jedem Schwerkraft-Schritt der eigenen Physik (alle 10 Ticks) `hit_ground`, Sculk-Sensoren in 8 Blöcken lösten ohne Ende
aus. Scharf, Motoren und Flug ohne Pilot gingen schon vorher sauber auf 0. Flugmodell, Goldens, Kalibrierung,
Netzprotokoll und Pakete unverändert. Tests: JUnit `interact.DroneVibrationsTest` (7, davon 2 neu), Selbsttest-Gruppe
`sculk:` (11 Schritte, davon 5 neu in `sculk: Mission`).

### Drehende Props behalten die Mitte (drone-render; 0.6.0, Befund OT4-1, Doku `docs/ASSETS.md` „Props im FPV-Bild“)

| Klasse | Modul | Inhalt |
|---|---|---|
| `drone/client/render/FpvProps#draw` | drone-render | über der weichen Scheibe (`buildBlur`) zeichnet das drehende Prop im FPV-Bild seine Mitte am Platz, im Drehwinkel und mit der Vergrößerung (`nearScale`) des stehenden Props; die Messung der Prop-Scheiben (`maskColor`) bleibt ohne Mitte, `propsDrawn` zählt die Mitte nicht |
| `drone/client/render/DroneVisuals#submitCenter`, `BlurItemCollector#center` | drone-render | Mitte = deckende Flächen des `_blur`-Modells (Nabe, Mutter, Schraube), die durchscheinende Scheibe fällt weg; ein `_blur`-Modell ganz ohne durchscheinende Flächen gibt keine Mitte; ohne Modell die deckende Form des Teils |
| `drone/client/render/FallbackShapes#propCenter`, `DroneAssembly` | drone-render | Ersatzform ohne `_blur`-Modell: deckende Nabe und Mutter (dieselbe Form wie in `buildProp`), `blur` nur noch der Kreisring |
| `drone/client/render/VisualHull` | drone-render | Ersatzform: deckende und durchscheinende Form zählen zusammen |

Vorher fehlte im FPV-Bild beim Übergang zur Scheibe die Mitte ganz (unter der Scheibe nur Motorglocke und Welle), die
Ersatzform zeichnete die Nabe durchscheinend und ohne Mutter. Außen mit `_blur`-Modell war die Mitte schon gleich. Flugmodell,
Netzprotokoll, Goldens und Kalibrierung unverändert. Selbsttest `drone-render: drehende Props behalten die Mitte …`
(`CovPropBlurCenterSelfTests`).

### Drohnen und Bosse (interact, drone, video, server; 0.6.0, Befund OT4-5, Doku `docs/drone-bosses.md`)

| Klasse | Modul | Inhalt |
|---|---|---|
| `interact/DroneBosses` | interact | Server-Takt (`serverTick` am Ende jedes Server-Ticks): Wärter mit Zorn auf eine Drohne laden den Schallschlag und treffen, Drachen bekommen Turbulenz und Angriff (`charging_player` auf die Drohne, Ziel folgt bis 10 Blöcke), Wither schießen mit den Seitenköpfen, Drachenatem-Wolken (Partikel `dragon_breath` oder Drache als Besitzer) zehren; `hit` rechnet Verschleiß, gibt ihn an das Flugmodell eines Server-Flugs, stößt über `DronePushes` und meldet beim Absturz `DroneVibrations.crashed`; Zähler für den Selbsttest |
| `interact/BossHit`, `interact/BossHits` | interact | Arten mit Absturz-Faktor (Schallschlag 3,0, Rammen 2,2, Flügel 0,7 der Aufprall-Toleranz des Rahmens), Stoß und Auftrieb; Aufprall-Probe für `sim.CrashDamage` über `DroneWear.apply`, Drachenatem als fester Verschleiß je Sekunde, Flügelstoß (80 / Abstand, höchstens 18 m/s), Turbulenz (bis 2,4 × Gewicht in 14 Blöcken) |
| `interact/WardenGrudge` | interact | Zorn je Drohne wie bei Spielern (35 je Vibration, höchstens 150, −1 je 20 Ticks, wütend ab 80), Schallschlag 34 Ticks laden, nächster frühestens 100 Ticks nach Beginn, Reichweite 15 waagrecht, 20 senkrecht |
| `mixin/drone/WardenMixin`, `EnderDragonMixin`, `WitherBossAccessor` | drone | Kopf von `Warden#increaseAngerAt(Entity, int, boolean)`, von `EnderDragon#knockBack` und `#hurt(ServerLevel, List)`; Aufruf von `WitherBoss#performRangedAttack(int, double, double, double, boolean)` |
| `drone/Knockback#forwards` | drone | Rückstoß an den Piloten auch ohne Add-on, wenn die Regel an ist und die Quelle eine Windkugel der Breeze, ein Wither-Schädel oder der Wither ist; Faktor dann vorab 1, Windkugeln von Spielern wie bisher nur mit Add-on (E16) |
| `video/DarknessPulse`, `video/client/BossDarkness` | video | Wärter in 20 Blöcken um die Drohne: Blende wie Vanillas Dunkelheit (22 Ticks, 140 Ticks Nachklang), Licht-Puls alle 80 Ticks; `FpvView#clearPilotLightEffects` setzt Helligkeit und Dunkelheits-Skala, `FeedInput#darkness` dunkelt das Kamerabild ab und lässt es rauschen |
| `server/rules/WorldRuleSpecs#DRONES_BOSS_INTERACTIONS` | server | Welt-Regel `propwash:drones_boss_interactions`, Schalter, Gruppe Spiel, Standard an, zählt für Rennen, in allen Voreinstellungen an |

Bis rc.4 waren Drohnen für Bosse Luft: Vanilla nimmt nur Lebewesen als Ziel, und den Rückstoß von Explosionen gab
Propwash nur an den Piloten weiter, wenn ein Add-on ihn übernahm (`DroneKnockback` mit Faktor 0 ohne Zuhörer).
Mob Defense behält die gezielten Angriffe normaler Mobs. Flugmodell, Goldens, Kalibrierung (byte-gleich), Netzprotokoll
und Pakete unverändert; Stöße und Kräfte gehen über das vorhandene `drone_push`, `WorldRulesS2C` trägt einen Eintrag
mehr. Tests: JUnit `interact.DroneBossesTest` (12), `video.DarknessPulseTest` (4), `race.RaceRulesTest` (Regel
rennrelevant); Selbsttest-Gruppe `bosses:` (11 Schritte).
