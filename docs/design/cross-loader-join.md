# NeoForge clients on Fabric servers

> Written in English: the contributor does not speak German. Please move it into German if you want the design docs to
> stay in one language.

Propwash FPV has a Fabric and a NeoForge build that register the same content in the same order (FOUNDATION §3,
"Flush-Reihenfolge"). Until 0.5.2 a player still had to use the server's loader. This change lets a **NeoForge
client join a Fabric server** that runs the same Propwash version and the same add-ons. Nothing changes for Fabric
clients, NeoForge-to-NeoForge connections or singleplayer, and server owners don't have to do anything.

The opposite direction (Fabric client on a NeoForge server) is not covered here. It needs the same kind of bridge for
NeoForge's registry sync on the Fabric side and is a separate change.

## Why it didn't work

A NeoForge client joining a Fabric server failed at four separate points, each hiding the next:

1. **Required channels.** Propwash registered its payloads as required. Against a server that isn't NeoForge,
   NeoForge only announces *optional* payloads and refuses required ones, so the Fabric server never saw Propwash's
   channels and sent "This server requires Propwash FPV".
2. **Fabric registry sync.** Fabric API kicks every client that can't receive `fabric:registry/sync` when the server
   has modded entries in synced registries ("requires Fabric Loader and Fabric API"). It does this for good reason:
   items, blocks, entity types and so on go over the network as numeric ids, and the Fabric server's ids come from its
   world save, so they can differ from the client's own order.
3. **Early payloads.** A Fabric server runs configuration tasks added in `BEFORE_CONFIGURE` (Fabric registry sync,
   Propwash's join check) before it sends its brand. A NeoForge client only treats a server as non-NeoForge once it
   sees the brand, so it dropped the connection: "Received a modded payload before channel negotiation".
4. **Server switches behind a proxy.** At the end of configuration NeoForge unregisters its configuration channels.
   Velocity remembers that and replays the remaining channel list to the next backend, so after a server switch the
   Fabric server again believed the client couldn't receive registry sync.

## What the NeoForge build does now

All of it lives in the NeoForge loader glue and the shared payload records; the Fabric build is unchanged.

- **Optional payloads.** `PropwashNeoForge` registers its payloads with `.optional()`. Propwash's own join check
  already enforces that both sides have Propwash, so the network layer doesn't need to. A NeoForge server now
  disconnects a client without Propwash channels with the same "requires Propwash" message the Fabric build uses.
- **Fabric registry sync bridge** (`neoforge.fabricsync.FabricRegistryBridge`, payloads in `network.fabricsync`):
  - receives `fabric:registry/sync` (`FabricRegistrySyncPayload` reads Fabric API's wire format),
  - turns each registry into a NeoForge `RegistrySnapshot` and applies it with `RegistryManager.applySnapshot`, the
    same call NeoForge uses for its own sync, so block states, item mappings and other baked data are rebuilt by
    NeoForge's own callbacks,
  - maps Fabric's entity data serializer registry (`fabric-object-builder-api-v1:tracked_data_handler`). Fabric
    numbers modded serializers right after the vanilla ones, NeoForge from 256 on, so for this connection the bridge
    adds the modded serializers to the vanilla id map at Fabric's ids,
  - replies with `fabric:registry/sync/complete`,
  - rejects the join with a clear message when the server has entries or registries the client doesn't have (naming
    the namespaces, so players know which mod is missing),
  - reverts the registries (`RegistryManager.revertToFrozen`) and the serializer ids when the connection closes.
  It switches itself off when `fabric_registry_sync_v0` is loaded (e.g. Forgified Fabric API), which does the same job.
- **Two small client mixins** (`mixin.fabricsync`, NeoForge only):
  - `ClientNetworkRegistryMixin`: a modded payload that arrives before any NeoForge negotiation can only come from a
    non-NeoForge server, because a NeoForge server always sends its network query first. In that case the connection
    is initialised as a non-NeoForge connection, where NeoForge would otherwise disconnect.
  - `ClientConfigurationPacketListenerImplMixin`: when a non-NeoForge server sends `minecraft:register` on a
    connection that is already initialised (a server switch behind a proxy), the client announces its configuration
    channels again.

## Limits

- Client and server need the same Propwash version and the same content add-ons, as before. Anything else is
  reported by name.
- Fabric-only content mods on the server still need their own NeoForge build on the client, with the same ids.
- `FabricRegistrySyncPayload` follows Fabric API's internal format (fabric-registry-sync-v0 8.0.x, MC 26.3). If Fabric
  API changes it, the round-trip test still passes but real servers would fail with the "could not match" message, so
  this should be re-checked when updating Fabric API.

## Testing

- `FabricRegistrySyncPayloadTest` (JUnit, `:common:test`): decodes a hand-built packet in Fabric's format and
  round-trips ids with gaps and interleaved namespaces.
- Manual, NeoForge 26.3.0.10-beta and 26.3.0.101 clients with Propwash 0.5.2 and Just More Parts 1.1.1:
  - direct join to a Fabric server (Fabric API 0.162.0, Propwash 0.5.2, Just More Parts 1.1.1),
  - the same through Velocity with FabricProxy-Lite (modern forwarding), including switching between two Fabric
    backends in both directions,
  - on a live multi-server network: join, server switches, building and flying drones with Just More Parts parts,
    then singleplayer afterwards (registries back to normal).
