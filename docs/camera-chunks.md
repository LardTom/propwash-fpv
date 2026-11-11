# Chunks um die Kamera-Drohne (Aufnahmen weit weg vom Spieler)

Für Add-on-Autoren und Server-Betreiber. Flashback zeichnet nur auf, was der Client geladen hat. Fliegt eine
Kamera-Drohne außerhalb der Sichtweite des Spielers, fehlen im Replay die Umgebung und die Entities um sie. Mit API 1.7
meldet ein Add-on für einen Spieler eine Kamera-Lage um seine eigene Drohne an. Solange sie angemeldet ist, schickt der
Server diesem Client die Chunks und Entities um die Drohne, wie beim FPV-Flug, aber zusätzlich: Der Spieler behält seine
eigene Ansicht und seine Umgebung.

Nicht Teil davon: eine Videoaufnahme im Hintergrund. Es gibt keinen zweiten Zeichen-Durchgang und keine Änderung am
Hauptbild; Shader und Sodium bleiben unberührt. Die direkte Aufnahme braucht weiterhin die Live-Ansicht
(Owner-Entscheidung vom 28.10.2026).

## 1. Am Server anmelden

```java
CameraChunkSupply chunks = PropwashApi.drones().cameraChunks();   // nur am Server
CameraChunkResult result = chunks.open(player, drone, 4);          // Radius in Chunks
if (result.accepted()) { ... result.radius() ... }                 // OPENED oder UPDATED
chunks.status(player, drone);                                      // Optional<CameraChunkStatus>
chunks.active(player);                                             // alle Lagen des Spielers
chunks.close(player, drone);                                       // abmelden
chunks.closeAll(player);
```

- Die Lage folgt der Drohne: Mitte ist jeden Tick der Chunk der Drohne. Eine freie Position gibt es nicht, damit niemand
  über eine Drohne beliebige Gegenden lädt.
- `open` für dieselbe Drohne noch einmal ändert den Radius (`UPDATED`) und zählt nicht als neue Lage.
- `CameraChunkResult.Status`: `OPENED`, `UPDATED`, `NOT_A_DRONE` (keine Propwash-Drohne oder am Server nicht
  geladen), `NOT_OWN_DRONE`, `OTHER_DIMENSION`, `RULE_OFF` (Welt-Regel 0), `TOO_MANY` (schon 2 Lagen), `SERVER_BUSY`
  (Server voll oder überlastet), `CLIENT_UNSUPPORTED` (Client ohne passendes Propwash), `PLAYER_GONE`. Abgelehnte
  Anmeldungen hinterlassen nichts.
- `CameraChunkStatus`: Drohne (UUID und Entity-Id), Mitte, gewünschter und gewährter Radius, `paused`, Zahl der Chunks der
  Lage und wie viele davon schon beim Client sind (`sentChunks`); `complete()` ist wahr, wenn alle da sind.
- Ein Ereignis für das Ende gibt es nicht; `status(...)` ist danach leer.

## 2. Vom Client aus anmelden

Ob Flashback gerade aufnimmt, weiß nur der Client. Damit das Add-on dafür kein eigenes Paket braucht, gibt es dieselbe
Anmeldung am Client:

```java
CameraChunkRequests requests = PropwashClientApi.cameraChunks();
if (requests.supported()) {                       // Server mit Kamera-Chunks
    requests.request(drone, 4);                   // oder request(entityId, 4)
}
requests.radius(drone);                           // gewährter Radius, 0 = keine Lage oder pausiert
requests.answer(drone);                           // letzte Antwort des Servers (CameraChunkResult.Status)
requests.release(drone);                          // abmelden
```

- Die Anfrage geht als `propwash:camera_chunks_request` (Entity-Id, Radius; 0 = abmelden) an den Server, der sie wie
  `open`/`close` prüft. Höchstens 10 Anfragen je Sekunde und Spieler, mehr verwirft der Server.
- Die Varianten mit Entity-Id gelten auch für eine Drohne, die der Client gerade nicht sieht (sie ist ja weit weg).
  Am Server muss sie aber geladen sein, sonst lautet die Antwort `NOT_A_DRONE`. Die Anmeldung lädt keine entladene
  Drohne nach.
- Der Server meldet Lagen, Radien und die Antwort mit `propwash:camera_chunks` zurück.

## 3. Grenzen

- **Nur eigene Drohne:** Der Spieler fliegt sie, steuert als zweiter Operator ihren Gimbal oder hat sie aufgestellt
  (Tag `pw_owner_<name>`). Fällt das weg, endet die Lage.
- **Radius:** 1 bis 8 Chunks, höchstens die Welt-Regel „Chunks um Kamera-Drohnen“ (`propwash:camera_chunks`, Gruppe
  Video, Standard 4, 0 = aus, nicht rennrelevant). Wird die Regel kleiner, schrumpfen laufende Lagen mit; auf 0 enden alle.
- **Anzahl:** 2 Lagen je Spieler, 16 auf dem Server; zusammen höchstens so viele Chunks wie `maxStreamedChunks` in
  `config/propwash/server.json` (Standard 4096). Liegt die mittlere Tick-Zeit über 45 ms, nimmt der Server keine neuen
  Lagen an, laufende bleiben.
- **Reichweite:** Liegt die Drohne außerhalb der Funkreichweite des Spielers (`maxPilotDistance`; mit unbegrenzter
  Reichweite nie) oder mehr als 128 Chunks vom Spieler entfernt, pausiert die Lage (`paused`, Radius am Client 0): Ihre
  Chunks gehen frei, nur der Chunk der Drohne bleibt geladen, damit die Lage weitergehen kann.
- **Erzeugen:** Darf der Spieler keine neuen Chunks erzeugen lassen (Welt-Regel „Drohnen erzeugen neues Gelände“ oder
  Server-Einstellungen), lädt die Lage nur einen schon erzeugten Bereich um die Drohne.

## 4. Ende

Die Lage endet bei `close`/`closeAll` oder `release`, wenn der Spieler den Server verlässt, die Dimension wechselt oder
stirbt, wenn die Drohne verschwindet oder in eine andere Dimension kommt, wenn sie nicht mehr seine eigene ist und wenn
die Welt-Regel auf 0 geht. Danach vergisst der Client die Chunks der Lage, die Entities dort verschwinden bei ihm, und
sein Chunk-Speicher ist wieder so groß wie ohne Lage.

## 5. Was der Spieler merkt

Nichts. Seine Ansicht, seine Position und seine Umgebung bleiben; die Chunks der Lage liegen außerhalb seiner
Sichtweite und werden deshalb nicht gezeichnet, Entities dort auch nicht. Flashback nimmt sie auf, weil der Client sie
hat. Die Last am Server entspricht einem kleinen zusätzlichen Sichtbereich je Lage (Radius 4: 81 Chunks).

## 6. Technik

- Server: Die Chunk-Ansicht eines Spielers mit Lage ist die Vereinigung aus seinem Vanilla-Bereich und den Lagen
  (`capture.CameraTrackingView`). Beim Wechsel rechnet Propwash nur den Unterschied (`capture.TrackingDiff`); der
  Vanilla-Weg würde bei einer fremden Ansicht alle Chunks vergessen und neu schicken. Entities in einer Lage werden wie in
  der Vanilla-Reichweite gepaart, gemessen von der Drohne. Tickets halten die Lage geladen (Radius + 2).
- Client: Vanilla speichert nur Chunks im Umkreis seiner Sichtweite + 3 um die Mitte und verwirft andere. Der Server
  schickt vor den Chunks der Lage den nötigen Speicher-Radius (in Stufen von 8), der Client vergrößert den Speicher; nach
  dem Vergessen der Chunks schrumpft er wieder (mit 16 Chunks Spielraum). Ein neues Level beginnt immer ohne Zusatz.
- Kein Mixin in Zeichen-, Shader- oder Sodium-Code.

## 7. Tests

JUnit `capture.CaptureRulesTest`, `capture.CameraTrackingViewTest`, `capture.TrackingDiffTest`,
`capture.CaptureBookTest`; Selbsttest-Gruppe `capture:` (beide Loader), mit Flashback auf Fabric zusätzlich eine echte
Aufnahme: In der Wiederholung haben alle Chunks der Lage Gelände, Drohne und Marker sind da, die Gegenseite ist leer;
Beitrittstest am dedizierten Server `PROPWASH_JOINTEST_CAPTURE` (beide Loader). Einzelheiten in `docs/testing.md`,
Abschnitt „Kamera-Chunks“.
