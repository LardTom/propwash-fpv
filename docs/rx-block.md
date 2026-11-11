# RX-Block

Für Spieler. Der RX-Block ist ein Empfänger für Redstone: Ein Kanal deiner Funke – Gas, ein Knüppel, ein AUX-Schalter
oder eine Taste – wird zu einem Redstone-Signal von 0 bis 15. Damit öffnest du Tore, schaltest Lampen oder startest eine
Anlage, auch während du eine Drohne fliegst.

Technik und Entscheidungen: [`design/rx-block.md`](design/rx-block.md).

## 1. Herstellen und setzen

- Rezept: Blitzableiter oben, Kupferbarren links und rechts, Platine in der Mitte, Redstone unten → 1 RX-Block. Im
  Kreativmodus steht er im Propwash-Tab hinter dem Signal-Repeater.
- Setzen wie einen Redstone-Verstärker: Der Ausgang zeigt in deine Blickrichtung. Vorne sitzt ein kupferner Stecker mit rotem
  Pfeil. Nur dort kommt das Signal heraus, dafür stark: Redstone-Staub, Lampen, Türen und Kolben direkt davor folgen,
  und ein Block davor leitet es weiter. An den anderen Seiten kommt nichts an, Staub daneben verbindet sich nicht.
- RX-Blöcke, die du vor dieser Änderung gesetzt hast, behalten ihr Verhalten: Sie haben Stecker an allen vier Seiten
  und versorgen weiter alles direkt neben sich, wie ein Tageslichtsensor (nicht durch einen Block hindurch). Willst du
  die Richtung, bau den Block ab und setz ihn neu; danach musst du ihn neu binden.
- Abbauen mit der Spitzhacke geht am schnellsten. Er droppt sich selbst, ohne Einstellungen.

## 2. Binden und einrichten

- **Rechtsklick mit der Funke:** Ein ungebundener Block bindet sich an dich – an den Spieler, nicht an diese Funke – und
  das Fenster öffnet sich. Bei deinem eigenen Block öffnet sich nur das Fenster.
- **Kanal anlernen:** im Fenster klicken und den Knüppel oder Schalter bewegen, dem der Block folgen soll. Erkannt wird
  die Bewegung mit dem größten Weg (mindestens ein Drittel des Wegs in 1,5 s). Knüppel heißen Roll, Nick, Gier und Gas;
  andere Kanäle erscheinen als „AUX-Achse n“ oder „Taste n“. Klappt das Anlernen nicht, wählst du den Kanal in der
  Liste darüber.
- **Endpunkte:** Knüppel auf die Stellung bringen, die Stärke 0 geben soll, „Als 0 % übernehmen“ klicken; dasselbe mit
  „Als 100 % übernehmen“ für Stärke 15. Genau dazwischen liegt immer Stärke 8 – ein Knüppel in der Mitte gibt also 8,
  egal von welcher Seite du kommst. Darunter teilen sich 0–7, darüber 9–15 den Weg in gleich großen Stufen. Liegt 0 %
  über 100 %, arbeitet der Block umgekehrt; sind beide gleich, schaltet er nur zwischen 0 und 15. Neu angelernt gilt
  der ganze Weg des Kanals.
- **Reichweite:** Regler im Fenster, 1 bis 128 Blöcke, Standard 64. Blöcke dazwischen dämpfen kaum (jeder Vollblock
  zählt wie ein halber Block mehr Entfernung).
- **Bindung lösen:** im Fenster unten oder Schleichen + Rechtsklick mit der Funke auf deinen Block; das Fenster fragt
  nach. Danach folgt der Block niemandem mehr und gibt 0 aus.
- **Rechtsklick ohne Funke** (leere Hand): Kanal, Stärke, Besitzer und Reichweite erscheinen in der Aktionsleiste.

## 3. Im Betrieb

- Die Funke steuert Drohne und RX-Blöcke gleichzeitig. Ohne Funke folgt der Block der Tastatur nur, während du fliegst –
  sonst läufst du mit ihr.
- **LED:** aus = kein Kanal oder ungebunden, grün = in Reichweite, rot = außer Reichweite.
- **Anzeige:** Der Balken oben zeigt die Stärke wie Redstone-Staub oder eine Lampe: 15 Felder, je Stufe leuchtet eins
  mehr (von der LED-Seite aus gesehen von links nach rechts), und je stärker das Signal, desto heller leuchtet er im
  Dunkeln. Bei Stärke 0 ist er dunkel.
- **Ressourcenpakete:** Jede Stärke hat ihr eigenes Modell `propwash:block/rx_block_power_<0–15>` und ihre eigene
  Textur `propwash:block/rx_block_power_<0–15>`; das Gehäuse mit der LED steckt in `rx_block`, `rx_block_linked` und
  `rx_block_lost`, der Ausgangsstecker in `rx_block_output` (Textur `propwash:block/rx_block_output`). Der Blockzustand
  `blockstates/rx_block.json` setzt alles nach `link`, `power`, `facing` und `output` zusammen; die Modelle zeigen nach
  Norden und werden je `facing` gedreht.
- **Außer Reichweite**, wenn deine Funke aus ist oder du nicht in derselben Welt bist, hält der Block seine letzte
  Stärke. Kommst du zurück, folgt er wieder.
- **Entfernte Bereiche:** Der Block arbeitet nur, solange sein Chunk geladen ist und Blöcke tickt (in Simulationsweite
  eines Spielers). Ein Block am Rand des geladenen Bereichs oder in einem entladenen Chunk behält seinen Zustand, bis
  wieder jemand in der Nähe ist. Propwash lädt dafür nie Chunks nach.

## 4. Kreativ-RX-Block

- **Kreativ-RX-Block** (`propwash:creative_rx_block`, EN „Creative RX Block“): nur im Kreativ-Inventar (Propwash-Tab
  direkt nach dem RX-Block) oder per Befehl eines Operators, kein Rezept, im Überleben nicht abbaubar, keine Beute.
  Gehäuse mit lila statt bernsteinfarbenem Streifen.
- Er arbeitet wie der RX-Block – gleiche Bindung an den Spieler, gleiches Fenster zum Anlernen, gleiche Endpunkte,
  Ausgang nach vorne –, aber **ohne Grenze der Reichweite**: Er folgt deiner Funke in jeder Entfernung, solange du in
  derselben Dimension bist, und Wände zählen nicht. Statt des Reglers zeigt das Fenster „Reichweite: unbegrenzt in
  dieser Dimension“, ebenso die Aktionsleiste beim Rechtsklick ohne Funke.
- Bist du in einer anderen Dimension, hält er wie der RX-Block seine letzte Stärke. Auch er arbeitet nur, solange sein
  Chunk geladen ist und Blöcke tickt (in Simulationsweite eines Spielers oder per `/forceload`); er lädt nie Chunks
  nach.
- Ressourcenpakete: Die Gehäuse-Modelle `creative_rx_block`, `creative_rx_block_linked`, `creative_rx_block_lost` und
  `creative_rx_block_inventory` erben von den RX-Modellen und tauschen nur die Texturen
  `propwash:block/creative_rx_block_body_side` und `_body_top`; Anzeige und Stecker teilen sich beide Blöcke.

## 5. Mehrspieler

- Ändern und lösen dürfen nur der gebundene Spieler und Operatoren (Stufe 2). Andere bekommen „Dieser RX-Block ist an …
  gebunden.“ So bleiben Redstone-Anlagen auf Survival-Servern geschützt.
- Die Funke sendet ihre Werte nur, wenn einer deiner RX-Blöcke in Reichweite ist, und höchstens 20-mal je Sekunde.
- Rohe AUX-Achsen heißen je Gerät anders; nach einem Wechsel der Funke den Kanal neu anlernen.
- Zurück auf Propwash 0.5.2: RX-Blöcke verschwinden wie jeder unbekannte Block.

## 6. Kernsätze für die Website (Englisch)

- The RX block turns one channel of your radio into a redstone signal from 0 to 15.
- Right-click it with your radio to bind it to you and open its window; learn a channel by moving the stick or switch.
- Set the 0 % and 100 % points, pick a range up to 128 blocks – walls barely matter.
- Place it like a repeater: the signal comes out of the front only, strong enough to pass through a block, while you
  fly a drone too. RX blocks placed before keep powering all sides.
- A stick in the middle always gives strength 8, from either side.
- A meter on top shows the signal strength 0–15 and glows brighter the stronger the signal; resource packs can restyle
  every level.
- Out of range it keeps its last signal; it only works in chunks that are loaded and ticking, and never loads chunks.
- Only you and operators can change or unbind it.
- The Creative RX Block (creative mode only, no recipe) works like the RX block but has no range limit within its
  dimension; it still only works in loaded chunks and never loads chunks.
