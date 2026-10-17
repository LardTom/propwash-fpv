# VTX-Leistung

Für Spieler. Der Videosender deiner Drohne (VTX) sendet mit einstellbarer Leistung – wie bei echten Sendern über eine
VTX-Tabelle oder das Menü der Brille. Mehr Leistung bringt Reichweite und Durchdringung, kostet aber Akku und heizt den
Sender auf. Die Einstellung gehört zur Drohne und bleibt in Item, Drohne und Werkbank erhalten.

Technik und Entscheidungen: [`design/vtx-power.md`](design/vtx-power.md).

## 1. Leistung wählen

- Konfigurator öffnen (`K`), Tab **Videokanal**, Abschnitt **Sendeleistung (VTX)**: **Leistung** auf *Auto* oder eine
  feste Stufe. *Auto* ist der Standard für jede Drohne, analog wie digital; auch Drohnen aus älteren Welten, neue
  Drohnen, Vorlagen und Sharecodes ohne eigene Einstellung senden mit *Auto*. Eine feste Stufe, die du gewählt hast,
  bleibt.
- Im Flug: Schalterfunktion **VTX-Leistung** im Tab **Modi** auf einen Taster legen oder Taste **F10** drücken. Jeder
  Druck schaltet eine Stufe weiter, nach der höchsten auf *Auto* und von dort auf die niedrigste.
- Das OSD zeigt die aktuelle Leistung, z. B. `400MW` oder `AUTO 25MW` (Element **Sendeleistung (VTX)**, in den
  Vorlagen links unter dem Kanal).

| Videomodul | Stufen | Nennleistung |
|---|---|---|
| Analog Nano (Whoop) | 25 · 100 · 200 · 400 mW | 200 mW |
| Analog Race (5″) | 25 · 200 · 400 · 800 · 1600 mW | 400 mW |
| Digital Lite | 25 · 100 · 400 · 700 mW | 400 mW |
| Digital HD | 25 · 100 · 400 · 700 · 1200 mW | 700 mW |

Bei der Nennleistung gelten die Reichweite und der Strom aus dem Tooltip des Moduls; mit dieser festen Stufe fliegt eine
Drohne genau wie vor 0.5.2. Module aus Add-ons bringen ihre eigenen Stufen mit; der Tooltip zeigt den Bereich. Ein Modul
mit nur einer Stufe (z. B. ein 25-mW-Whoop-Sender) sendet immer mit ihr.

## 2. Was die Leistung bewirkt

- **Reichweite:** doppelte Leistung ≈ 1,4-fache Reichweite, vierfache Leistung ≈ doppelte.
- **Durchdringung:** jede Verdopplung bringt 3 dB mehr gegen Bäume, Wände und Gelände. Ein Block Stein, Ziegel oder
  Beton dämpft etwa wie eine Hauswand (7–9 dB), ein ganzes Steinhaus rund 14 dB: 100 m dahinter zeigt Analog mit
  400 mW fast nur noch Rauschen, mit 1600 mW wieder ein farbloses, aber erkennbares Bild.
- **Akku:** Analog kostet jedes zusätzliche Watt Sendeleistung gut 4 W aus dem Akku (1600 statt 400 mW ≈ +5 W), digital
  rund 2,5 W. Auf kleinen Whoops merkt man das an der Flugzeit.
- **Wärme:** Ohne Luftstrom wird der Sender heiß. Mit der Nennleistung hält er am Boden durch, höhere Stufen überhitzen
  nach einigen Minuten (1600 mW analog nach knapp 2 min), in Wüste und Nether schneller. Im Flug kühlen Fahrtwind und
  Propeller.

## 3. Auto (Standard)

Nah am Piloten die niedrigste Stufe, bei Entfernung, Bäumen oder Wänden bis zur höchsten (auch im Sichtflug ohne
Brille, dann aus Abstand und Hindernissen geschätzt). Hoch geht es sofort, herunter langsam (eine Stufe alle 2 s und erst
mit 3 dB Luft, damit die Leistung an der Grenze nicht pendelt).

- **Digital** wie bei DJI und Walksnail: Der Sender misst seine Videoverbindung über den Rückkanal der Brille und hält
  10 dB Reserve (4 dB braucht ein sauberes Digitalbild).
- **Analog**: Ein analoger Sender sieht sein eigenes Bild nicht. Hier regelt der Flugcontroller über SmartAudio oder
  Tramp nach der Signalstärke, die der Empfänger von deiner Fernsteuerung misst – sie läuft denselben Weg (Entfernung,
  Wände, Repeater). Weil das nur eine indirekte Messung ist, hält Auto analog 15 dB Reserve (12 dB braucht ein
  rauschfreies Analogbild) und dreht damit etwas früher hoch als digital.

## 4. Niedrig, wenn unscharf

Wie `vtx_low_power_disarm` in Betaflight:

- *Aus*: die gewählte Stufe gilt immer.
- *Immer*: Solange die Drohne nicht scharf ist, sendet sie mit der niedrigsten Stufe – schont Sender und Akku am Boden.
- *Bis zum ersten Scharfschalten*: niedrig nur bis zum ersten Start; nach einem Absturz sendet sie wieder voll, damit du
  sie findest.

## 5. Überhitzen

Ab 80 °C zeigt das OSD **VTX ÜBERHITZT**. Ab 85 °C regelt der Sender selbst eine Stufe herunter, danach alle 4 s eine
weitere, solange er heiß bleibt; die Leistung im OSD steht dann in Warnfarbe. Abgekühlt gibt er die Stufen wieder frei.
Kaputt geht nichts. Im Tab steht im Flug eine Zeile mit Stufe, Temperatur und Leistungsaufnahme.

Mit der Welt-Regel **Hardware-Grenzen** auf *Aus* (z. B. Vorlage *Arcade*) überhitzt der Sender nie. Setzt eine Zone
*Hardware-Grenzen*, gilt dort ihr Wert – für den Sender wie für die Motoren.
