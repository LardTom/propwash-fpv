# Drohnen-Trails

Für Spieler. Mit eingeschalteten Trails zieht jede fliegende Drohne eine dünne, verblassende Spur hinter sich her – so
sieht man andere Piloten auch im FPV-Bild sofort, selbst wenn die Drohne nur ein paar Pixel groß ist.

Technik und Entscheidungen: [`design/drone-trails.md`](design/drone-trails.md).

## 1. Einschalten (Host oder Operator)

Die Trails sind eine Welt-Regel und in neuen Welten aus.

- Konfigurator öffnen (`K`), Tab **Welt-Regeln**, Abschnitt **Spiel**: **Drohnen-Trails** auf *An* und speichern.
- Oder im Chat: `/gamerule propwash:drone_trails true`.
- Die Voreinstellungen **Training** und **Arcade** schalten die Trails mit ein, **Realistisch** lässt sie aus.

Ändern dürfen der Host (Einzelspieler, LAN) und Operatoren; alle anderen sehen den Wert nur.

## 2. Für sich selbst ausschalten

Ist die Welt-Regel an, entscheidet jeder Spieler selbst:

- Tab **Darstellung**, Abschnitt **Drohnen-Trails**: **Drohnen-Trails zeigen** *An* oder *Aus* (Standard *An*). Ist die
  Welt-Regel aus, ist der Schalter gesperrt und darunter steht „In dieser Welt aus“.
- Schneller mitten im Flug: Taste **Drohnen-Trails ein/aus** im Tab **Tasten** belegen (ab Werk ohne Taste).
- Die Suche im Konfigurator findet den Schalter unter „Trail“, „Spur“ oder „Schweif“.

## 3. Was man sieht

- Nur **scharfe Drohnen in der Luft** ziehen einen Trail. Gelandet oder entschärft entsteht keine neue Spur, der Rest
  verblasst in ein bis zwei Sekunden.
- Je schneller die Drohne, desto länger der Trail: etwa 1 Sekunde im Schwebeflug, 2 Sekunden ab rund 85 km/h.
- **Farbe:** die Farbe des LED-Streifens, wenn er leuchtet; sonst die Lackierung (zuerst der Rahmen); unlackierte
  Drohnen bekommen eine feste, kräftige Farbe, die bei allen Spielern gleich ist. Bei Nacht leuchtet der Trail, bei Tag
  hebt ihn ein dunkler Rand vom Himmel ab.
- **Im FPV-Bild** siehst du die Trails aller anderen Drohnen, aber nicht den deiner eigenen. Im Sichtflug und in der
  dritten Person zeigt auch deine Drohne ihren Trail.
- **Action-Cam-Aufnahmen** zeigen die Trails so, wie du sie beim Aufnehmen siehst (mit deinem Schalter, ohne deinen
  eigenen Trail).
- Trails ändern nichts am Flugverhalten und nichts an Bestenlisten.
