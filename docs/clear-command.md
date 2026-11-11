# Drohnen aufräumen: `/propwash clear`

Für Server-Admins und Operatoren. Der Befehl entfernt Drohnen aus der Welt, ohne dass jemand hinfliegen muss: nach einem
Event, wenn auf einem Server zu viele abgestellte Drohnen herumstehen, oder wenn eine Drohne an einer unerreichbaren
Stelle liegt.

Technik und Entscheidungen: [`design/0.6.0/gameplay.md`](design/0.6.0/gameplay.md) Teil G und Entscheidung E33.

## 1. Bedienung

- `/propwash clear` entfernt **alle** Drohnen in den geladenen Bereichen aller Dimensionen.
- `/propwash clear <ziele>` entfernt nur die Drohnen in der Auswahl, zum Beispiel
  `/propwash clear @e[type=propwash:drone,distance=..50]` (Umkreis von 50 Blöcken) oder
  `/propwash clear @e[tag=pw_owner_Steve]` (Drohnen eines Besitzers, siehe [`design/drone-tags.md`](design/drone-tags.md)).
  Andere Entities in der Auswahl bleiben unberührt; Spieler, Rüstungsständer und Items entfernt der Befehl nie.
- Rückmeldung an den Absender: „3 Drohnen entfernt (overworld: 2, the_nether: 1).“ mit der Anzahl je Dimension, bei
  einer Drohne „1 Drohne entfernt (overworld: 1).“. Wie bei Vanilla-Befehlen sehen auch die übrigen Operatoren die Zeile.
  Ohne Treffer: „Keine Drohnen in geladenen Bereichen gefunden.“
- Das Server-Log bekommt je Aufruf eine Zeile mit Absender, Anzahl je Dimension und dem, was beendet wurde
  (`/propwash clear von <name>: entfernt 3 (overworld: 3), Pilot-Sitzungen beendet 1, Heimflüge beendet 1, …`).

## 2. Was mit den Drohnen passiert

Jede Drohne nimmt denselben Aufräumweg wie beim Aufheben, nur ohne Item:

- Fliegt jemand die Drohne, endet seine Sitzung sauber (FPV oder Sichtflug), die Kamera springt zurück zum Spieler. Er
  bekommt im Chat „Deine Drohne wurde entfernt.“ Dieselbe Zeile bekommt der Besitzer eines laufenden autonomen
  Heimflugs.
- Ein autonomer Heimflug oder Sinkflug endet; es startet kein neuer Heimflug beim Wegfall des Piloten.
- Sichtanker und Chunk-Tickets der Drohne fallen weg (die Tickets mit dem nächsten Server-Tick), der Pilot sieht wieder
  die Welt um seinen Körper.
- Zuschauer, die die Drohne per F7 sehen, werden abgemeldet; ihr Client meldet wie bei jedem Ende der Übertragung
  „Der Pilot sendet nicht mehr“.
- Ladung, die die Drohne trägt, fällt wie beim Aufheben oder Abbauen als Item an ihrer Stelle herunter. Die Drohne selbst droppt nichts und geht an
  niemanden zurück.

## 3. Rechte und Grenzen

- Rechtestufe 2 (Operator, wie `/kill`). Spieler ohne Recht sehen den Unterbefehl nicht und können ihn nicht
  ausführen.
- Nur geladene Bereiche: Drohnen in entladenen Chunks bleiben, der Befehl lädt keine Chunks. Wer sie auch entfernen
  will, lädt den Bereich vorher (hinfliegen, `/forceload`).
- Es gibt keine Bestätigung und kein Rückgängig. Für gezielte Fälle den Selektor verwenden.
- Der Befehl ändert nichts an Gates, Kursen, RX-Blöcken oder Drohnen-Items in Inventaren.

## 4. Englische Kernsätze (Website)

- `/propwash clear` removes every drone in the loaded parts of all dimensions; `/propwash clear <targets>` removes only
  the drones in an entity selector, for example `@e[type=propwash:drone,distance=..50]`.
- Operators only (permission level 2). Players who are flying a removed drone return to their own view and get
  "Your drone was removed." in chat.
- Clean-up runs like picking a drone up, without the item: flight sessions, autonomous return flights, chunk tickets and
  spectators end cleanly. The drone drops nothing; cargo it carries falls to the ground.
- The sender and the other operators see "Removed 3 drones (overworld: 2, the_nether: 1)."; the server log records who
  ran it and how many drones were removed.
- Drones in unloaded chunks stay where they are; the command never loads chunks.
