# Zuschauerzahl im OSD

Für Spieler und Server-Betreiber. Das OSD-Element **Zuschauer** zeigt dem Piloten, wie viele andere Spieler gerade das
Videobild seiner Drohne sehen: ein kleines Augen-Symbol und daneben die Zahl in OSD-Schrift. Gezählt wird, wer mit der
Brille zuschaut (`F7` oder die Kanalliste im Tab **Videokanal**) oder mit einer Brille auf dem Kanal der Drohne steht.

## 1. Was gezählt wird

- Jeder andere Spieler, der gerade das Bild **dieser** Drohne sieht, zählt einmal: beim Zuschauen über die Kanalliste
  oder die Zuschauen-Taste und mit einer Brille auf ihrem Kanal. Wer das Bild einer anderen Drohne sieht, zählt dort.
- Der Pilot selbst zählt nicht, ein Operator am Gimbal der Drohne auch nicht, obwohl er ihr Bild sieht.
- Das Element erscheint nur, solange mindestens einer zuschaut. Ohne Zuschauer bleibt der Platz leer.

## 2. Im OSD-Editor

- Konfigurator (`K`), Tab **OSD**, Eintrag **Zuschauer** in der Elementliste (Gruppe Status). Der Tooltip erklärt, wer
  zählt. Die Vorschau zeigt das Auge mit der Beispielzahl 3.
- Grundplatz oben rechts (Spalte 28, Zeile 0, rechtsbündig). In den Vorlagen **Standard** und **Voll** ist das
  Element an, in **Minimal** und **Rennen** aus; eingeschaltet steht es dort ebenfalls oben rechts. Verschieben, Farbe
  und Größe wie bei jedem Element.
- Gespeicherte Layouts bekommen das Element beim nächsten Start nach ihrer Vorlage: mit Standard oder Voll an, mit
  Minimal oder Rennen aus. Ein OSD-Layout im Sharecode nimmt den Eintrag `spectators` mit.

## 3. Beim Zuschauen

Wer zuschaut und **OSD des Piloten zeigen** an hat (Tab **Videokanal**), sieht die Zahl ebenfalls im übertragenen OSD,
sofern das Layout des Piloten das Element zeigt (ohne übertragenes Layout das eigene, `docs/spectate-feed.md`). Er zählt dabei selbst mit: Schauen zwei zu, sehen beide das Auge mit
der 2.

## 4. Server und Netz

- Der Server zählt jeden Tick aus `SpectateServer#watching` und `SpectatorLinks#watched`, ohne Pilot (`isFlownBy`) und
  ohne Operator (`OperatorServer#operator`).
- Er schickt die Zahl nur, wenn sie sich für einen Spieler ändert (`propwash:spectator_count`, Entity-Id und Zahl), an
  den Piloten, an jeden Zuschauer und an den Operator der Drohne. Kein Paket pro Tick; eine 0 geht nur an Spieler, die
  vorher eine Zahl für dieselbe Drohne bekommen haben. Das Netzprotokoll bleibt 17.
- Der Client merkt sich die letzte Zahl bis zum Trennen und zeigt sie nur für die Drohne, die er fliegt oder anschaut.

## 5. Tests

- JUnit `osd.SpectatorCountTest` (13 Fälle): Zählung von Zuschauen und Brille, Pilot und Operator ausgenommen, jeder
  Spieler einmal, Reihenfolge geflogen vor angeschaut, Senden nur bei Änderung, 0 nur als Ersatz, Vergessen beim
  Verlassen, Klemmen auf 9999, Codec, Vorlagen, alte Layouts, Beispieldaten.
- Selbsttest `osd: Vorlagen ohne Überlappung` zeichnet das Element mit der breitesten Zahl 9999 in jeder Vorlage.
- Beitrittstest `PROPWASH_JOINTEST_SPECCOUNT` (`dev.SpecCountJoinCheck`, `osd.client.SpecCountJoinProbe`), Ablauf in
  [`testing.md`](testing.md) §1 „Zuschauerzahl im OSD“.
