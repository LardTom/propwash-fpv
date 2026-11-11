# Gate auf Beinen: Öffnung bis zum Boden

Für Spieler. Stapelst du Gates zu einer Leiter oder einem Doppel-Gate, steht das unterste Gate auf Beinen: Es hat keinen
unteren Balken mehr, nur noch die Füße in den Pfosten. Ab 0.6.0 zählt ein Durchflug durch dieses Gate bis hinunter zum
Boden – auch ganz unten, wo früher der Balken saß. Einzelne Gates und die Gates darüber zählen wie bisher erst ab der
Rahmenstärke, denn dort liegt ein Balken.

Technik und Entscheidungen: [`design/0.6.0/fixes-polish.md`](design/0.6.0/fixes-polish.md) Punkt A (§3.A, §6.2, E4–E6).

## 1. Welche Gates

| Gate | Unterkante der Öffnung |
|---|---|
| Einzelnes stehendes Gate (mit unterem Balken) | Rahmenstärke (wie bisher) |
| Unterstes Gate eines Stapels (auf Beinen) | **Boden** |
| Gate im Stapel darüber | Rahmenstärke (dort liegt der Balken des Gates darunter) |
| Gate im Stapel darüber, gedreht (nicht 0° oder 180°) | Rahmenstärke über seinem eigenen unteren Balken ([`race-topology.md`](race-topology.md) §8) |
| Gates an Wand und Decke, liegende, hängende und gekippte Gates | wie bisher (nicht stapelbar) |
| Freiform-Gate, Stange, Flagge, Würfel, Gate-Stange | wie bisher |

Das gilt für alle Größen und frei skalierte Gates, auch für die Leiter aus drei Gates in der Drohnen-Arena.

## 2. Was sich ändert und was bleibt

- Rundenzählung im HUD, die Prüfung des Servers für die Bestenliste und der Redstone-Puls des Gates zählen den unteren
  Streifen gleich.
- Baust du das obere Gate ab, hat das untere wieder seinen Balken und zählt nach höchstens einer Viertelsekunde wieder
  erst ab der Rahmenstärke.
- Kurse, Bestenlisten, Rekorde, Ghosts und Share-Codes bleiben unverändert: Die Kursdaten kennen die Rolle eines Gates
  nicht, der Layout-Hash ist derselbe. Die Öffnung wird nur größer, keine bisher gültige Runde wird ungültig.
- Kein neues Netzpaket und keine neuen Speicherdaten. Server und Client müssen beide 0.6.0 haben.

## 3. Grenzen

- Der Client erkennt das Gate auf Beinen am geladenen Gate. Ist es noch nicht geladen (sehr weit weg), zählt der
  Client unten wie in 0.5.2 erst ab der Rahmenstärke. Beim Durchflug ist die Umgebung der Drohne immer geladen.
- Direkt nach dem Stapeln sieht der Client das untere Gate im selben Tick auf Beinen, in dem das neue Gate bei ihm
  ankommt; auf einem Server kommt nur die Laufzeit des Netzes dazu.
- Die Füße stehen innerhalb der Pfosten; sie ragen nicht in die Öffnung.

## 4. Prüfung

JUnit `race.GateLegsOpeningTest` und `race.LapTrackerTest`, Selbsttests „race: Gate-Stapel: …“ und „race:
Weltkurs-Leiter: …“ in `race.client.GateStackSelfTests` (beide Loader; „… frisch gestapelt …“ stapelt fünfmal in
allen fünf Phasen des Nachrechnens und verlangt die Beine beim Client spätestens einen Tick nach dem neuen Gate und
höchstens 5 Ticks ab dem Stapeln, beim Server im Tick des Stapelns), Beitrittstest `PROPWASH_JOINTEST_STACKGATE=1`
(Fall `stackgate`, `dev.StackGateJoinCheck`); Befehle in [`testing.md`](testing.md) §1 „Gestapelte Gates“ und §3.

## 5. Englische Kernsätze (Website)

- The bottom gate of a stack counts all the way down to the ground – where its bottom bar is missing, flying through counts.
- Single gates and the gates higher up in a stack still count from the top of their bottom bar.
- Courses, records and ghosts stay as they are: the course layout does not change, the opening only grows.
- Remove the gate on top and the bottom gate has its bar again – flying under it no longer counts.
