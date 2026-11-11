# Sharecodes im Chat

Für Spieler und Server-Admins. Ab Propwash 0.6.0 werden Sharecodes in Chatzeilen klickbar, auch in Nachrichten anderer
Spieler, in Systemzeilen und in Befehlsausgaben. Die Erkennung läuft nur beim Spieler: Der Server sieht davon nichts,
es gibt keine neuen Pakete. Oberfläche und Import: [`sharecode-ui.md`](sharecode-ui.md), Format:
[`sharecode/pw3.md`](sharecode/pw3.md).

## 1. Was der Spieler sieht

- Der Code-Abschnitt einer Zeile wird **unterstrichen und in der Akzentfarbe** gezeigt, der übrige Text bleibt genau so,
  wie er ankam (Farben, Formatcodes, Absender, Einfügen per Umschalt-Klick auf den Namen).
- **Darüberfahren** zeigt eine Vorschau als Tooltip: Name (oder „Sharecode“) mit Format, ein kleines gerendertes Bild der
  Drohne (derselbe Weg wie die Vorschau im Import-Fenster), Klasse, Rahmen, Motoren, Props, Akku, Zubehör,
  Gesamtgewicht und Schub/Gewicht, die nötigen Add-ons (fehlende rot mit Name und Version wie im Import), im
  Überlebensmodus „Überleben: N Teile fehlen im Inventar“ bzw. „alle Teile sind im Inventar“ (alle 10 Ticks neu
  gezählt), unten die Bedienung. Fehlen Teile im Spiel, gibt es kein Bild, dafür „Unbekannte Teile: N“. Der Tooltip
  steht über dem Zeiger, damit die Chatzeile mit dem Code sichtbar bleibt.
- **Klick** öffnet den Konfigurator im Tab „Sharecode“ mit dem Code im Import-Feld. Übernommen wird dort erst auf Knopfdruck:
  im Kreativmodus „Drohne erzeugen“, im Überleben zeigt der Tab die Einkaufsliste, Teile kommen an der Werkbank in die
  Drohne. Der Server entscheidet wie bei jedem Import (Rechte, Kosten, Teile). Schließen führt zurück in den Chat.
- **Umschalt-Klick** kopiert den Code in die Zwischenablage, die Aktionsleiste meldet „Sharecode kopiert (N Zeichen)“.
- **PW1 und PW2** sind genauso klickbar; der Import zeigt dann „Alter Code (PW2) …“ und rechnet wie gewohnt um.
- **Ungültige Codes** (Rahmen und Prüfsumme stimmen, der Inhalt ist aber nicht lesbar) werden rot unterstrichen, zeigen
  nur „Code ungültig“ und sind nicht klickbar.
- Die eigene Ausgabe von `/propwash code export` und `convert` behält „Klick kopiert“: dort kopieren Klick und
  Umschalt-Klick, die Vorschau kommt dazu.

## 2. Erkennung

- Präfix `PW3-`/`PW3`, `PW2` (mit oder ohne Bindestrich) und `PW1-` in beliebiger Groß- und Kleinschreibung, vor und
  hinter dem Code kein Buchstabe, keine Ziffer und kein `_`. Danach Base62 (PW2/PW3) bzw. die 5er-Gruppen von PW1; ein
  angehängtes Wort hinter einem Bindestrich („…-H23NQ-danke“) gehört nicht zum Code.
- Nur **vollständige Codes mit gültiger Prüfsumme** zählen: Rahmen und CRC wie im Codec (PW2/PW3 untere 16 Bit der
  CRC-32 über die Nutzdaten, PW1 die volle CRC-32). Abgeschnittene Codes und Tippfehler bleiben normaler Text.
  Ein Code ohne Präfix wird im Chat nicht erkannt (anders als im Import-Feld).
- Grenzen gegen Missbrauch: höchstens 4000 Zeichen je Code (wie `Pw3Params.MAX_INPUT_CHARS`), höchstens 3 Codes und 8
  Kandidaten je Zeile, höchstens 8 neue Codes je Sekunde. Was darüber liegt, bleibt Text.
- Formatcodes (`§a`, `§l`, `§r` …) in der Zeile löst Propwash vorher genauso auf, wie Minecraft sie zeichnet; mit
  ausgeschalteten Chatfarben werden sie wie dort entfernt. Ein Code darf über mehrere unterschiedlich formatierte Teile
  einer Nachricht laufen.
- Hat der Absender den Code-Abschnitt selbst klickbar gemacht (Befehl, Link), bleibt sein Klick; nur „in die
  Zwischenablage“ mit genau diesem Code wird zur Vorschau mit Kopieren.

## 3. Nur die Anzeige

Der Haken sitzt beim Umbrechen der Chatzeilen (`GuiMessage#splitLines`, Mixin `mixin.share.client.GuiMessageMixin`).
Die gespeicherte Nachricht bleibt unverändert: Inhalt, Signatur, Kennzeichen am Rand, Chat-Protokoll für Meldungen und
das Löschen signierter Nachrichten arbeiten mit dem Original. Ausschalten und Einschalten der Option bricht alle
Zeilen neu um, ohne Nachrichten zu erzeugen. Klicks fängt `ChatScreenMixin` ab (Klick-Ereignis
`propwash:share_chat/open` bzw. `/copy`, geht nie an den Server), den Tooltip mit Bild zeichnet
`GuiGraphicsExtractorMixin`; ohne ihn bliebe der reine Text-Tooltip.

Jeder Code wird **einmal** dekodiert (`ShareImportState`, mit Add-on-Prüfung, Flugwerten und Ersatzteilen) und in einem
kleinen Zwischenspeicher gehalten (32 Codes, ältester fällt heraus, neue Sitzung leert ihn). Das Darüberfahren liest nur
daraus, es wird nicht je Bild dekodiert.

## 4. Option

Konfigurator, Tab „Konfiguration“, Abschnitt „Chat“: **„Sharecodes im Chat klickbar“** (Standard an), mit Tooltip und
einem Satz Hilfe darunter. Gespeichert in `config/propwash/share_chat.json` (`clickableCodes`), wirkt nach „Speichern“
sofort auf alle Zeilen. Aus: Zeilen sehen aus wie in Minecraft ohne Propwash.

## 5. Klassen

| Klasse | Aufgabe |
|---|---|
| `share.client.chat.ChatCodeFinder` | Codes in einer Zeile finden (reines Java, Prüfsummen ohne Tabellen) |
| `share.client.chat.ChatCodeText` | Formatcodes auflösen, Code-Abschnitte neu einfärben, Rest unverändert |
| `share.client.chat.ChatCodeCache` | kleiner LRU-Speicher mit Ratenbegrenzung |
| `share.client.chat.ChatPreview`, `ChatPreviewImage` | Vorschau-Zeilen und Drohnenbild im Tooltip |
| `share.client.chat.ShareChat`, `ShareChatConfig` | Option, Klick, Kopieren, Import öffnen, Tooltip |

## 6. Tests

- JUnit (`share.client.chat.*Test`): Ränder, mehrere Codes, höchstens 3, abgeschnittene Codes, falsche Prüfsumme, PW1
  und PW2, Formatcodes mit und ohne Chatfarben, Code über zwei Teile, Klick-Ereignisse des Absenders, Zwischenspeicher
  und Ratenbegrenzung, Sprachschlüssel (beide Sprachen, keine Überschneidung mit `pw3-codec` und `pw3-ui`, Umlaute).
- Selbsttest `share: chat – …` (Fabric und NeoForge, mit `'-PpropwashSelftestOnly=share:'`): Zeilen einspeisen
  (fremder Spieler mit signierter Nachricht, Systemzeile mit Formatcodes, ungültiger Code, falsche Prüfsumme),
  Klick-Ereignis, Unterstreichung, Farbe und Vorschau prüfen, Nachricht und Signatur unverändert, Tooltip mit Bild beim
  Darüberfahren ohne neue Dekodierung, Klick öffnet den Tab ohne zu übernehmen, Umschalt-Klick kopiert, eigene
  Export-Zeile kopiert mit Vorschau, Überleben zählt fehlende Teile, Schalter aus und an.
- Bildschirmfotos (Fabric, `-PpropwashScreenshots=true`, `'-PpropwashSelftestOnly=bildschirmfotos: Sharecode im Chat'`):
  `docs/screenshots/share-chat/share_chat_<named|missing_addon>_<640x480|854x480>_<en|de>.png`, Tooltip sichtbar.
- Beitrittstest `PROPWASH_JOINTEST_SHARE_CHAT=1` (Fall `share-chat`, `docs/testing.md`): Der Server schreibt 3 s nach dem
  Beitritt per `/say` eine Zeile mit PW3-Code und per `tellraw` eine mit PW2-Code, der Client findet den Code klickbar
  und öffnet per Klick den Import. Erwartung `joined|share chat link ok|share chat import open`.
