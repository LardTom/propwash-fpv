# Add-on zeitweise entfernt: Platzhalter und Welt-Regeln

Für Spieler, Server-Betreiber und Add-on-Autoren. Wird ein Add-on aus einer Welt entfernt und später wieder
eingesetzt, sollen Drohnen, Drohnen-Items und Welt-Regeln danach genau so sein wie vorher. Die Grundregeln stehen in
[`api/addon-guide.md`](api/addon-guide.md) Abschnitt 17; hier steht, was seit 0.6.0 genau passiert.

## 1. Teile eines fehlenden Add-ons

Fehlt beim Laden ein Teil aus einem fremden Namensraum, ersetzt Propwash es durch einen **Platzhalter mit derselben
Id**. Der Platzhalter wird mit dieser Id wieder gespeichert; kommt das Add-on zurück, steht wieder das Originalteil
in der Drohne. Platzhalter zeigen „Unbekanntes Bauteil“, die Drohne hat das Problem `missing_part` und lässt sich
nicht scharfschalten.

| Art | Werte des Platzhalters |
|---|---|
| Rahmen | `freestyle_5` |
| Stack | `stack_30` |
| Motor | `m2207_1750` |
| Prop | `p51_3b` |
| Video | `analog_race` |
| Akku | `b6s_1300` |
| Zubehör oben | `action_cam_light` |
| Zubehör unten | `led_strip` |
| Zubehör vorn | `led_strip`, an die Halterung vorn gesetzt |

Ein Zubehör-Platzhalter sitzt immer an der Halterung, an der das fehlende Teil gespeichert war. Bis 0.5.x bekam ein
fehlendes Teil **vorn** den LED-Streifen für unten; der passte nicht an die Halterung vorn, und dadurch verlor die
Drohne (und ein Drohnen-Item) ihr **ganzes** Zubehör, auch Teile oben und unten und eingebaute Teile von Propwash.

Das gilt überall, wo Baupläne gelesen werden: Drohnen in der Welt, Drohnen-Items (Inventar, Truhen, Werkbank),
Netzwerk zwischen Server und Client und Add-ons, die nur beim Spieler fehlen (Sitzung ohne Add-on). Sharecodes
speichern nur Ids: Ein Code mit Teilen eines fehlenden Add-ons behält sie an derselben Halterung, und mit dem Add-on
baut er wieder die Originaldrohne. Geister im Rennen lassen unbekanntes Zubehör weiter weg.

Zerlegen in der Werkbank gibt für Platzhalter weiterhin nichts zurück (die Werkbank warnt vorher).

## 2. Kaputte Einträge

Kann ein einzelnes Zubehör nicht gelesen werden, bleibt der Rest erhalten. Das betrifft unbekannte Schlüssel im
Namensraum `propwash`, ein bekanntes Teil an der falschen Halterung (etwa eine Kamera unten) und unbekannte
Halterungen. Nur dieser Eintrag fällt weg; im Log steht eine Warnung „Zubehör verworfen, der Rest bleibt: …“.
Früher fiel in diesen Fällen das ganze Zubehör weg, und im Netz konnte ein solcher Eintrag die Verbindung trennen.

## 3. Welt-Regeln eines fehlenden Add-ons

Welt-Regeln von Add-ons sind Gamerules im Namensraum des Add-ons. Fehlt das Add-on, kennt Minecraft diese Schlüssel
nicht. Propwash legt sie beim Laden beiseite und schreibt sie beim Speichern unverändert zurück. Der Server meldet
deshalb kein „Failed to parse saved data for … game_rules“ mehr, und die Werte bleiben auch dann erhalten, wenn
ohne das Add-on eine andere Gamerule geändert wird (ohne diese Behandlung gingen sie dann beim nächsten Speichern
verloren). Unbekannte Schlüssel im Namensraum `minecraft` behandelt Minecraft weiter selbst.

## 4. Tests

- JUnit unter `common/src/test/java/de/lardtom/propwash/build/placeholder/`: `PlaceholderSlotTest` (jede Halterung, jede
  Teil-Art, kaputte Einträge in Speicherstand und Netz), `PlaceholderRestartTest` (Neustarts in eigenen
  Klassenladern: mit Add-on, zweimal ohne, wieder mit; Speicherstand, Netz-Bytes und Sharecode),
  `UnknownGameRulesTest`.
- Selbsttest `platzhalter:` (beide Loader), siehe [`testing.md`](testing.md) Abschnitt „Add-on zeitweise entfernt“.
