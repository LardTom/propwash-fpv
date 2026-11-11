# Akku gesteckt

Für Spieler. Sitzt der Akku in der Drohne, zeigt das Modell keine Stecker und keine Kabel: Der Akku hat dann weder
Stromkabel mit Stecker noch Balancer-Kabel am Heck, der Stack keine Kabel nach hinten. Nichts steht hinter dem Akku oder
dem Rahmen ab – an jedem Rahmen, mit dem Akku oben und unter dem Rahmen, in der Welt, in der Hand, in der Werkbank, bei
Ghosts, in der Flugschule und im Web-Konfigurator. Ohne Akku zeigt die Drohne den Stecker ihres Stacks wie bisher; ein
einzelner Akku (in der Hand, im Regal, am Boden) behält Kabel und Stecker.

Technik und Entscheidungen: [`design/0.6.0/fixes-polish.md`](design/0.6.0/fixes-polish.md) §3.B, Regeln in
[`ASSETS.md`](ASSETS.md) „Akku gesteckt“.

## Was sich ändert

- **Keine Stecker, keine Kabel an der Drohne:** Buchse, Balancer-Stecker und Kabel des Akkus sowie die beiden Stromkabel
  des Stacks werden nicht gezeichnet, der Stack erscheint ohne eigenen Stecker. Vorher lagen zwei lose Stecker
  nebeneinander.
- **Warum kein gestecktes Paar mit Kabel:** Ein Paar am Akku-Ende mit einem Kabel bis zum Stack stand je nach Rahmen hinter
  dem Akku ab, bildete eine Schleife um die Platte oder lief durch GPS-Mast, Platte oder Duct-Brücke (7 Zoll Long Range,
  X-Class, Race, Whoop). Ohne Stecker sieht jede Drohne sauber aus.
- **Keine neuen Daten:** reine Darstellung im Client. Welten, Kurse, Bestenlisten, Ghosts und Share-Codes bleiben gleich.
- **Lackieren:** Ein Klick auf den Stack wählt wie bisher den Stack.

## Ressourcenpakete und Add-ons

- Ein Ressourcenpaket, das nur das Modell eines Stacks ersetzt, behält sein Bild: Propwash nimmt die Variante ohne
  Stecker nur, wenn sie aus demselben Paket kommt wie das Grundmodell. Dann bleiben auch Buchse und Kabel des Akkus.
- Add-ons liefern die Variante `<stack>_mounted` (Stack ohne Stecker, die beiden Stromkabel heißen `pigtail`); ohne sie
  bleibt ihr Stack, wie er ist. Akku-Modelle brauchen nur die üblichen Namen `connector` (+ `connector_grip`) bzw.
  `bt_connector`, `balance_plug` und `wire` für die Kabel. Anleitung: [`api/addon-guide.md`](api/addon-guide.md) §6.
- Skalierte Render-Items (etwa die X-Class-Rahmen von Just More Parts) brauchen nichts Eigenes: Propwash blendet im Modell
  aus, bevor Minecraft die `transformation` anwendet.

## Prüfen

Selbsttests „drone-render: Akku gesteckt“ (zuerst „… – Stecker und Kabel an der Drohne ausgeblendet“) und Bildschirmfotos
`battery_plugged_*` (Aufrufe in [`testing.md`](testing.md) „Akku gesteckt“), JUnit `PlugAnchorsTest`, Generator-Prüfung
`tools/assets/parts/validate.py`.
