# Add-ons nur beim Spieler – für die Sitzung inaktiv (Propwash 0.5.0)

Wunsch des Besitzers (08.10.2026, sinngemäß): Hat ein Spieler ein Propwash-Add-on, das der Server nicht hat, soll er
trotzdem beitreten können. Das Add-on wird auf diesem Server einfach nicht genutzt – fehlt dem Server etwa Just More
Parts, verbindet man sich trotzdem, nur bleibt Just More Parts beim Spieler aus. Add-ons, die nur der Server hat,
bleiben Pflicht (Übersicht aus der Beitrittsprüfung, `join-check.md`).

## 1. Vorher

Fall (b) aus `join-check.md` § 1, mit zwei Prozessen nachgestellt: Weder Fabric noch NeoForge trennten, nur Propwashs
Teile-Abgleich („Missing on the server: justmoreparts:lihv_ss, … (+136 more)“, ab der Beitrittsprüfung als Karte
„Entferne Just More Parts oder bitte den Server-Betreiber, es zu installieren“).

## 2. Regel

| Lage | Ergebnis |
|---|---|
| Add-on nur beim Spieler, mit Teilen | Beitritt erlaubt, das Add-on ist beim Spieler für die Sitzung **inaktiv** |
| Add-on nur beim Spieler, ohne Teile (z. B. ein HUD-Add-on) | erlaubt und aktiv, wie bisher (reines Client-Add-on) |
| Add-on nur auf dem Server | Pflicht – Übersicht „Installiere XY 1.2.3“ |
| beide Seiten, gleiche Teiledaten (auch bei anderer Versionsnummer) | erlaubt |
| beide Seiten, andere Teiledaten – auch zusätzliche Teile beim Spieler | Pflicht – „Aktualisiere“, „Installiere die Version des Servers“, „Installiere neu“ |
| Teile ohne bekanntes Add-on in einem Namensraum, den der Server nicht kennt | inaktiv wie ein Add-on |
| anderes Netzwerkprotokoll, andere eingebaute Teile | wie bisher Pflicht |

Ein Namensraum ist **dem Server unbekannt**, wenn der Server dort kein Teil hat, kein Add-on des Servers ihn (oder eine
gleichnamige Mod-Id) besitzt und das Add-on, dem er beim Spieler gehört, nicht auf dem Server läuft. Inaktiv werden
diese Namensräume samt Mod-Id und allen weiteren Namensräumen des Add-ons, die der Server ebenfalls nicht kennt.
`propwash` und `minecraft` sind nie inaktiv. Die Rechnung steht einmal in `join.ClientOnlyAddons` (reines Java):
Der Server entscheidet damit, ob er trennt, der Spieler, was er abschaltet – beide kommen zum selben Ergebnis.

## 3. Ablauf

1. **Server** (Konfigurationsphase wie in `join-check.md` § 2): `JoinReport` zählt Teile in inaktiven Namensräumen als
   `inactive` statt `missingOnServer`. Getrennt wird nur noch bei Teilen, die dem Spieler fehlen, anderen Teiledaten,
   zusätzlichen Teilen in einem Namensraum, den der Server kennt, anderem Protokoll oder anderen eingebauten Teilen.
   Lässt er den Spieler herein, schreibt er „Propwash: <Spieler> hat andere Add-ons als der Server, Propwash lässt den
   Beitritt zu (N Teile nur beim Client, dort für diese Sitzung inaktiv)“ und die Zusammenfassung ins Log.
2. **Spieler:** Beim Teile-Abgleich (`JoinClientState.onServerParts`) antwortet er mit seiner vollständigen Teile-Liste
   und schaltet danach mit `join.AddonSession.activate` die Namensräume ab, die der Server nicht kennt. Das passiert
   vor dem ersten Paket der Spielphase, also bevor Inventar oder Drohnen ankommen.
3. **Ende:** `ClientCommonPacketListenerImpl#onDisconnect` (auch bei einer Absage in der Konfigurationsphase),
   `ClientBus.DISCONNECT` (Welt verlassen, Neukonfiguration) und jede neue Konfigurationsphase leeren die Menge. Im
   Einzelspieler und auf einem Server mit dem Add-on ist danach wieder alles aktiv.

## 4. Was „inaktiv“ beim Spieler heißt

- **Teile-Register** (`build.PartIndex` mit `build.PartAvailability`): `all`, `byId`, `byKey`, `chemistries` und
  `chemistry` sehen die Teile nicht. Baupläne mit diesen Ids werden zu Platzhaltern wie auf dem Server, die Drohne
  fliegt beim Spieler also mit denselben Daten wie dort. Teile-Liste für den Handshake, Fingerabdruck,
  Beitrittsdaten, Modelle und Item-Bilder lesen über `registered…` weiter alles, damit nach der Sitzung nichts fehlt.
- **Kreativ-Inventar:** Der eigene Tab des Add-ons ist leer und wird von Minecraft ausgeblendet; Haupt-Tab und
  Kreativ-Einträge (`PropwashApi.creativeTab()`) zeigen keine Items mit inaktiven Teilen; die Suche folgt, weil sie
  aus den Tabs gebaut wird (neu beim nächsten Öffnen, `mixin.join.client.CreativeModeTabsAccessor`).
- **Werkbank und Konfigurator:** Teile-Listen, Werkbank-Erweiterungen (Id im Namensraum des Add-ons),
  Konfigurator-Tabs mit Id `<modid>:…`, Teile und Vorlagen im Drohnen-Editor (eigene Vorlagen mit inaktiven Teilen
  bleiben ausgeblendet).
- **Presets** des Add-ons und alle Presets mit inaktiven Teilen (`ApiExtensions.registeredPresets`,
  `PropwashApi.builds().presets()`).
- **Flugschule:** Lektionen mit Id `<modid>:…` sind ausgeblendet und lassen sich nicht per Id öffnen.
- **Hinweis nach dem Beitritt:** Toast im Propwash-Stil „Add-on auf diesem Server inaktiv – Propwash: Just More Parts
  ist auf diesem Server nicht installiert und für diese Sitzung inaktiv.“ (`join.client.JoinClient`, einmal je
  Sitzung, Bildschirmfotos `addon_inactive_toast.png`, `addon_inactive_toast_de.png`).
- **Keine Pakete über inaktive Teile:**
  - Kreativ-Pakete (`MultiPlayerGameMode#handleCreativeModeItemAdd/Drop`, z. B. eine gespeicherte Schnellleiste aus
    einer anderen Welt oder ein Rezept-Viewer im Cheat-Modus) gehen nicht raus: Der Platz wird geleert und dem Server
    leer gemeldet, ein Toast sagt „… gibt es auf diesem Server nicht – Propwash hat es aus deinem Inventar
    herausgehalten“.
  - `PropwashClientApi.sendImport`/`sendCreateDrone` antworten lokal mit `UNKNOWN_PARTS` und den Ids, ohne zu senden.
  - Der Drohnen-Code `PWD1-` im Drohnen-Editor nennt die Teile („Dieser Code nutzt Teile, die es auf diesem Server
    nicht gibt: …“) und übernimmt nichts.
- **Items aus einer anderen Welt:** Ein Teil-Item zeigt „Auf diesem Server nicht verfügbar“, eine Drohne „Auf diesem
  Server nicht verfügbare Teile: …“; nichts stürzt ab.
- `PropwashClientApi.isAddonActive(modId)` sagt Add-ons, ob sie gerade inaktiv sind.

## 5. Loader-Ebene (mit zwei Prozessen geprüft)

- **Fabric:** Der Register-Sync (Modus REMOTE) lässt Einträge, die nur der Spieler hat, zu und hängt sie mit neuen
  Nummern hinter die des Servers (Fabric-Quelltext, nur im Debug-Log: „assuming client only registry entry“). Würde der Spieler so ein Item senden, könnte
  der Server die Nummer nicht lesen und würde trennen – deshalb der Schutz der Kreativ-Pakete.
- **NeoForge:** `RegistryManager.applySnapshot` übernimmt nur die Nummern des Servers; Einträge nur beim Spieler bleiben
  im Register, haben aber keine Netz-Nummer (Senden würde schon beim Spieler scheitern).
- **Netzwerk-Kanäle:** Just More Parts und das Beispiel-Addon melden keine eigenen Kanäle an (geprüft), deshalb war dort
  nichts umzustellen. NeoForge verweigert eine Verbindung, wenn auf einer Seite ein **Pflicht**-Kanal fehlt, in beiden
  Richtungen; Add-ons, die auf Servern optional sein sollen, melden ihre Kanäle mit `optional()` an und prüfen vor dem
  Senden `hasChannel` (Fabric: `ClientPlayNetworking.canSend`). Die Regeln stehen im Handbuch
  (`docs/api/addon-guide.md` § 17.1).

## 6. Grenzen

- Items, die ein Add-on **direkt** registriert (nicht über `PropwashApi.partItems()`), kann Propwash nicht aus fremden
  Tabs nehmen. Der Schutz der Kreativ-Pakete greift für sie nur, wenn ihr Namensraum inaktiv ist; Handbuch § 17.1.
- Rezept-Viewer (JEI, REI, EMI) listen Items aus dem Register; Propwash blendet dort nichts aus, verhindert aber, dass
  ein inaktives Item per Kreativ-Paket an den Server geht.
- Ein Add-on ohne Teile gilt als reines Client-Add-on und bleibt aktiv.
- Lektionen oder Tabs **aktiver** Add-ons, die Teile eines inaktiven zeigen, sehen Platzhalter statt der Teile.

## 7. Tests

- JUnit `join.ClientOnlyAddonsTest` (Regel aus § 2, Sitzung an und aus, Texte) und `join.JoinReportTest` (inaktive Teile
  blockieren nicht, zusätzliche Teile eines Add-ons beider Seiten schon, Reihenfolge der Karten).
- Selbsttest `addon-sitzung:` mit dem Beispiel-Addon (alle Stellen aus § 4, Schutz der Kreativ-Pakete und Importe,
  Trennen macht alles wieder aktiv, Bildschirmfotos Hinweis und Übersicht `join_addon_inactive.png`).
- Beitrittstest mit zwei Prozessen (`docs/testing.md`): Fabric und NeoForge, Spieler mit Just More Parts auf einem
  Server ohne (tritt bei, alles von Just More Parts unsichtbar, Hinweis, keine Fehler in beiden Logs) und umgekehrt
  (getrennt mit „Installiere Propwash: Just More Parts 1.1.0“), dazu beide Seiten mit Just More Parts (tritt bei,
  nichts inaktiv). Der Spieler schreibt nach dem Beitritt `session_*` und `visible_inactive_*` in
  `runs/smoke-client/jointest/<fall>.txt`; Bildschirmfoto des echten Beitritts auf Fabric:
  `docs/screenshots/addon_inactive_join_fabric.png` (unter Minecrafts Hinweis „Chat messages can't be verified“ des
  Offline-Testservers).
- Ergebnis 08.10.2026: Fabric und NeoForge je drei Fälle bestanden (nur beim Spieler: beigetreten, `session_parts=144`,
  alle `visible_inactive_*` 0, Hinweis da; nur auf dem Server: getrennt mit der Übersicht; beide: beigetreten, gleicher
  Fingerabdruck `ed7668457d9e`), dazu auf Fabric das Beispiel-Addon nur auf dem Server (getrennt, „Installiere Propwash
  Example Add-on 1.0.0“). Keine Fehler in den Logs von Server und Spieler außer den üblichen Offline-Meldungen
  (Realms, Anmeldung).
- Ein Server ganz ohne Beispiel-Addon lässt sich im Dev-Lauf nicht starten (seine Beute steckt im Propwash-Datenpaket);
  den Fall „fremdes Add-on nur beim Spieler“ prüfen deshalb Selbsttest und JUnit, mit zwei Prozessen Just More Parts.

## 8. Text für die Website-Hilfe (Spielerhandbuch, Abschnitt „Hilfe“)

- en: **“Add-on inactive on this server”:** you have a Propwash add-on the server doesn't use. You can still play there –
  the add-on just stays switched off while you're connected: its parts, presets, tabs and lessons are hidden, and items
  from it show “Not available on this server”. When you leave, everything is back. Add-ons the server has but you
  don't still have to be installed.
- de: **„Add-on auf diesem Server inaktiv“:** Du hast ein Propwash-Add-on, das der Server nicht nutzt. Du kannst trotzdem
  dort spielen – das Add-on bleibt nur ausgeschaltet, solange du verbunden bist: Seine Teile, Presets, Tabs und
  Lektionen sind ausgeblendet, Items daraus zeigen „Auf diesem Server nicht verfügbar“. Beim Verlassen ist alles wieder
  da. Add-ons, die der Server hat und du nicht, musst du weiter installieren.
