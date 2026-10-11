# Beitritts-Prüfung mit Add-on-Übersicht – Propwash 0.5.0

Wunsch des Besitzers (08.10.2026): Wer einem Server ohne die passenden Mods beitreten will, soll klar sehen, **welches
Add-on** fehlt oder anders ist – nicht eine Liste einzelner Teile oder Registereinträge. Mit der offenen Parts-API in
0.5.0 entwickeln Spieler eigene Add-ons; wer zufällig auf so einen Server kommt, soll „Add-on XY fehlt, installiere
XY 1.2.3“ lesen.

## 1. Was Spieler vorher sahen (nachgestellt mit zwei Prozessen)

Nachgestellt mit dem Beitrittstest (§ 7): dedizierter Server (Fabric bzw. NeoForge) mit Beispiel-Addon und Just More
Parts 1.1.0, Client mit anderer Ausstattung.

| Fall | Fabric | NeoForge |
|---|---|---|
| (a) Server hat Add-ons, die dem Client fehlen | Fabrics Register-Sync trennt zuerst: „Received 151 registry entries that are unknown to this client … namespaces may be related: justmoreparts, propwash_example“ | NeoForges `SyncRegistries` trennt zuerst: „The server sent registries with unknown keys: ResourceKey[minecraft:item / justmoreparts:p31_14_3b_bi], …“ (alle Items in einer Zeile) |
| (b) Client hat Add-ons, die der Server nicht hat (JMP nur beim Client) | Register-Sync lässt zusätzliche Client-Einträge zu, Propwash trennt: „Your Propwash parts don't match the server's. Missing on the server: justmoreparts:lihv_ss, justmoreparts:whoop_65_lite, … (+136 more)“ – Teile-Ids statt Add-on | wie Fabric |
| (c) gleiches Add-on, andere Version (JMP 1.0.0 gegen 1.1.0) | gleiche Items, Propwash trennt: „Different values: justmoreparts:whoop_65_lite, …“ – ohne Add-on-Namen und Version | wie Fabric |
| (d) anderes Netzwerkprotokoll | Propwash-Prüfung (bisher in `CONFIGURE`, also nach dem Register-Sync); bei echten Versionssprüngen kommen neue Items dazu, dann trennt vorher Fabric wie in (a) | NeoForges Kanal-Abgleich vor jeder Konfigurationsaufgabe: `ModMismatchDisconnectedScreen` „Channel of mod "Propwash FPV" failed to connect: The client wants the payload to be version: 99, but the server wants it to be version: 12!“ |
| (e) beides | wie (a) | wie (d) |

Ursache: Propwash prüfte in der Konfigurationsphase **nach** dem Registerabgleich der Loader (Fabric: Aufgabe in
`CONFIGURE`, Register-Sync in `BEFORE_CONFIGURE`; NeoForge: Aufgaben über `RegisterConfigurationTasksEvent`, die hinter
`SyncRegistries` und `SynchronizeRegistriesTask` stehen), und die Meldung war nach Teilen statt nach Add-ons geordnet.

## 2. Ablauf ab 0.5.0

1. **Früh in der Konfigurationsphase.** Fabric: `ServerConfigurationConnectionEvents.BEFORE_CONFIGURE` in der eigenen
   Phase `propwash:join_check`, geordnet vor `Event.DEFAULT_PHASE` (dort hängt Fabrics Register-Sync seine Aufgabe an).
   NeoForge: `RegisterConfigurationTasksEvent` stellt die beiden Propwash-Aufgaben an den **Anfang** der Warteschlange
   (Accessor `mixin.join.ServerConfigurationPacketListenerImplAccessor`), also vor `SyncRegistries`. Gilt für
   dedizierte Server, LAN und Einzelspieler gleich (im Einzelspieler passt immer alles).
2. **Beitrittsdaten** (`propwash:join_info`, `network.JoinInfoPayload`, Konfigurationsphase, beide Richtungen): Server
   schickt Propwash-Version, Netzprotokoll, Fingerabdruck der eingebauten Teile und seine Add-on-Liste; der Client
   antwortet mit seinen. Anderes Protokoll → Server trennt mit der Übersicht (Propwash-Zeile plus alle
   Add-on-Unterschiede), ohne den Teile-Abgleich.
3. **Teile-Abgleich** (`propwash:parts`, wie seit 0.4.0): Weichen Teile ab, trennt der Server mit der Übersicht –
   außer bei Teilen von Add-ons, die nur der Spieler hat: Die bleiben bei ihm für die Sitzung inaktiv, er tritt bei
   (`docs/design/client-only-addons.md`).
   Unterschiede ohne Teile (z. B. ein reines HUD-Add-on nur beim Client) blockiert Propwash nicht; der Server schreibt
   sie aber ins Log.
4. **Client-Übersicht.** Jeder Abbruch während der Konfiguration (Propwash, Fabric, NeoForge-Register, Zeitüberschreitung)
   läuft über `ClientCommonPacketListenerImpl#createDisconnectScreen`. Hat der Client vom Server Beitrittsdaten
   bekommen und gibt es Unterschiede, zeigt er statt des Vanilla-Bildschirms `join.client.JoinMismatchScreen`. Kam die
   Trennung nicht von Propwash, steht die Original-Meldung darunter („Ursprüngliche Meldung“) und die Einleitung sagt
   „sehr wahrscheinlich der Grund“. Das gilt nur, wenn mindestens ein Unterschied den Beitritt verhindern kann
   (`JoinReport#actionable`: blockierend oder „wahrscheinlich“). Sind alle Unterschiede nur Hinweise, die Propwash
   erlaubt (etwa ein reines Client-Add-on ohne Teile), bleibt bei einer fremden Trennung der Bildschirm des Loaders
   stehen – sonst führte „sehr wahrscheinlich der Grund“ auf eine falsche Spur; das Log nennt die Unterschiede trotzdem.

Fabric-Clients **ohne Propwash** (sie melden Fabric-Kanäle, aber weder `join_info` noch `protocol_version`) trennt der
Server schon in der frühen Phase mit „This server requires Propwash FPV 0.5.0 (network protocol 13) … Propwash add-ons
on this server: …“ (englischer Rückfalltext, weil der Client die Sprachdatei nicht hat) statt Fabrics
Namensraum-Liste. Vanilla-Clients bekommen weiter Fabrics „requires Fabric Loader and Fabric API“. Ältere
Propwash-Clients (vor 0.5.0, nur `protocol_version`) laufen über die bisherige Protokoll-Aufgabe, nun ebenfalls früh.

## 3. Was in die Übersicht kommt

Eine Karte je Add-on (`join.JoinReport`, reines Java):

| Lage | Status | Aktion |
|---|---|---|
| nur auf dem Server | „Fehlt bei dir“ | „Installiere XY 1.2.3.“ |
| nur beim Client | „Nicht auf dem Server“ | unter „Außerdem anders“: „Der Server hat es nicht – Propwash lässt dich trotzdem beitreten, XY bleibt dort für diese Sitzung inaktiv.“ (ohne Teile: „Nur bei dir – das erlaubt Propwash.“; `client-only-addons.md`) |
| beide, Client älter | „Ältere Version“ | „Aktualisiere XY auf 1.2.3.“ |
| beide, Client neuer oder nicht vergleichbar | „Andere Version“ | „Installiere XY 1.2.3, die Version des Servers.“ |
| gleiche Version, andere Teiledaten | „Andere Bauteildaten“ | „Installiere XY 1.2.3 neu …“ |
| anderes Protokoll | Karte „Propwash FPV“ mit beiden Versionen und Protokollen | „Installiere Propwash FPV 0.5.0, die Version des Servers.“ |
| gleiches Protokoll, andere eingebaute Teile | Karte „Propwash FPV“ | Version des Servers bzw. neu installieren |
| Teile ohne bekanntes Add-on | „Unbekannte Teile (namensraum)“ mit bis zu fünf Ids je Richtung | „frag den Server-Betreiber, aus welchem Add-on sie stammen“ |

Darunter: Versionen (Server · du), Zahl der betroffenen Teile, der Link aus den Metadaten (klickbar, öffnet erst
Minecrafts Rückfrage „Diesen Link öffnen?“). Karten, die Propwash nicht blockieren (z. B. Client-Add-on ohne Teile, nur
andere Version bei gleichen Teilen), stehen unter „Außerdem anders (das erlaubt Propwash)“ mit einer ruhigen Notiz statt
Aufforderung. „Details kopieren“ legt dieselbe Übersicht als Text in die Zwischenablage (für den Server-Betreiber oder
Discord).

**Zuordnung Teil → Add-on:** Namensraum gleich Mod-Id; sonst die Mod, deren `loadBundled(modId)` die JSON-Teile geladen
hat; sonst „Unbekannte Teile“. **Add-on-Liste:** Mods, die in ihren Metadaten von `propwash` abhängen (Fabric
`depends`/`recommends`/`suggests`, NeoForge `required`/`optional`), plus Mods, denen Teile gehören. Nie die ganze
Mod-Liste.

**Server-Log:** `Propwash: Beitritt von <Spieler> abgelehnt, Add-ons passen nicht zum Server (Übersicht wie beim Spieler):` und darunter dieselbe
Zusammenfassung wie „Details kopieren“ (englisch, die Sprache des Servers). Lässt Propwash den Beitritt trotz
Unterschieden zu, steht eine Info-Zeile im Log, damit Betreiber eine spätere Loader-Trennung zuordnen können.

## 4. Sicherheit

Die Beitrittsdaten kommen vom fremden Server bzw. Client: Texte verlieren Steuer-, Bidi- und `§`-Formatierungszeichen und
werden gekürzt (Name 96, Version 64 Zeichen), Mod-Ids und Namensräume nur `[a-z0-9_.-]`, höchstens 256 Add-ons mit je 32
Namensräumen, Nutzdaten höchstens 256 KiB. Links nur `http`/`https` mit Hostnamen, ohne Benutzerdaten, ohne Leer- und
Nicht-ASCII-Zeichen, höchstens 256 Zeichen; Öffnen nur über Minecrafts Rückfrage. Namen landen als reiner Text in den
Komponenten (keine Übersetzungs-Schlüssel, keine Klick-Events vom Server).

## 5. Format `propwash:join_info`

VarInt Format (1) und VarInt Länge, dann der Inhalt: Propwash-Version, VarInt Protokoll, Fingerabdruck der eingebauten
Teile, VarInt Anzahl Add-ons und je Add-on Mod-Id, Name, Version, Homepage, VarInt Anzahl Namensräume und die
Namensräume (Strings UTF-8, je höchstens 1024 Bytes). Spätere Formate hängen Felder hinten an; ältere Clients lesen
den bekannten Anfang und überspringen den Rest. Dadurch funktioniert die Übersicht auch zwischen künftigen
Propwash-Versionen mit anderem Protokoll (Fall d/e auf Fabric). Der Kanal ist neu, deshalb Netzprotokoll 13.

## 6. Grenzen

- **NeoForge, anderes Netzwerkprotokoll:** NeoForge vergleicht die Kanal-Versionen aller Propwash-Pakete vor jeder
  Konfigurationsaufgabe und zeigt seinen eigenen Bildschirm (eine Zeile „Channel of mod "Propwash FPV" …“ mit beiden
  Protokollnummern). Das zu umgehen hieße, alle Propwash-Kanäle optional und unversioniert anzumelden; dann könnten
  Propwash-Clients auch Servern ohne Propwash beitreten und dort Pakete schicken. Bewusst nicht geändert.
- **Eigene Pflicht-Kanäle eines Add-ons** prüft NeoForge ebenfalls vorher (seine Liste nennt den `displayName`).
- **Client ohne Propwash auf NeoForge:** NeoForges Kanal-Abgleich trennt vorher („missing on the client side“, Propwash FPV).
- Fallen Server und Client in Version **und** Add-ons auseinander, zeigt NeoForge nur die Protokollzeile; Fabric die
  ganze Übersicht.

## 7. Tests

- JUnit `join.JoinReportTest`, `JoinSafetyTest`, `JoinInfoPayloadTest`, `JoinTextTest`, `JoinLangTest` (Rückfalltexte im
  Code = `en_us`, `de_de` hat jeden Schlüssel mit denselben Platzhaltern).
- Selbsttest `beitritt:` (eigene Add-on-Liste und Netz-Rundweg, Übersicht mit Links und Kopieren, Bildschirmfotos
  `join_addon_mismatch`, `join_protocol_mismatch`, `join_addon_mismatch_de`).
- **Beitrittstest mit zwei Prozessen** (`docs/testing.md`): Server-Rauchtest mit `PROPWASH_JOINTEST_HOLD`, Rauchtest-Client
  mit `PROPWASH_JOINTEST`, Add-ons über `mods/` im Run-Ordner und `PROPWASH_DEV_EXAMPLE`, Protokoll über
  `PROPWASH_DEV_PROTOCOL`. Ergebnisse unter `runs/smoke-client/jointest/<fall>.txt|png`.

## 8. Text für die Website-Hilfe (Spielerhandbuch, Abschnitt „Hilfe“)

- en: **“Add-ons don't match this server”:** the server uses other Propwash add-ons (or other versions) than you. The
  window lists every add-on with the server's and your version and what to do – install, update or remove it – with a
  link to its page. *Copy details* puts the list on your clipboard for the server owner.
- de: **„Add-ons passen nicht zum Server“:** Der Server nutzt andere Propwash-Add-ons (oder andere Versionen) als du.
  Das Fenster nennt jedes Add-on mit der Version des Servers und deiner und was zu tun ist – installieren,
  aktualisieren oder entfernen – samt Link zur Seite des Add-ons. *Details kopieren* legt die Liste für den
  Server-Betreiber in die Zwischenablage.
