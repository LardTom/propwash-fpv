# Statistiken im Minecraft-Statistikfenster

Für Spieler. Propwash zählt deine Flüge in den normalen Minecraft-Statistiken mit: Pausenmenü (`Esc`) →
**Statistiken** → Reiter **Allgemein**. Alle Zeilen beginnen mit „Propwash:“ und stehen deshalb beieinander.

## 1. Was gezählt wird

| Zeile | Bedeutung | Format |
|---|---|---|
| Propwash: Flugzeit | Zeit, in der deine Drohne scharf war, während du sie geflogen bist (wie die Flugzeit im OSD) | Zeit |
| Propwash: Flugzeit (Whoop) … (X-Class) | dieselbe Flugzeit, aufgeteilt nach Drohnenklasse | Zeit |
| Propwash: Flugstrecke | Weg der Drohne, solange sie scharf war | Strecke |
| Propwash: Höchstgeschwindigkeit | schnellstes Tempo, das du je scharf geflogen bist | km/h |
| Propwash: Crashes | jeder Aufprall, den das Flugmodell als Crash wertet (wie der Crash-Zähler im OSD) | Anzahl |
| Propwash: Akkus leer geflogen | jeder Akku, der im Flug auf 0 % fällt, einmal je Akku | Anzahl |
| Propwash: Scharfschaltungen | jedes Scharfschalten, auch Auto-Arm nach einem Reset | Anzahl |
| Propwash: Gewertete Runden | jede Runde, die der Server im Rennen bestätigt (auch Läufe auf Punkt-zu-Punkt-Kursen und Runden mit Flughilfe) | Anzahl |
| Propwash: Beendete Rennen | jedes Rennen, das du bis ins Ziel fliegst | Anzahl |

- **Meist geflogene Klasse:** Minecraft kann in den Statistiken keinen Text zeigen. Die Klasse mit der längsten
  Flugzeit in den Zeilen „Flugzeit (…)“ ist deine meist geflogene. Die Klassen sind Whoop, Toothpick, Cinewhoop,
  Freestyle, Racer, Long Range, Cinelifter und X-Class. Rahmen aus Add-ons zählen für die Klasse, die ihr Hersteller
  angibt.
- **Bestzeiten** stehen nicht in den Statistiken. Sie ändern sich, und Kurse können gelöscht werden; dafür gibt es die
  Bestenliste des Kurses. Die Statistiken sind reine Zähler.
- Formate wie bei Minecraft: Zeit in s, min, h oder d, Strecke in cm, m oder km, Zahlen mit Tausender-Komma. Das
  Höchsttempo steht mit einer Nachkommastelle in km/h.

## 2. Was nicht zählt

- Die **Freecam-Drohne**, **Geister** und **Replays** der Bestenliste: Sie fliegen nur bei dir im Spiel, nicht in der
  Welt.
- **Flüge ohne Pilot:** Heimflug nach dem Aussteigen und Flüge, die ein Add-on steuert.
- **Zuschauer** und der **zweite Operator** einer Drohne: Gezählt wird nur für den Piloten.
- Teleports, Resets und `/tp` der Drohne zählen nicht als Flugstrecke.

## 3. Auf Servern

Gezählt wird auf dem Server, im Einzelspieler genauso wie auf einem Server. Die Werte liegen wie alle
Minecraft-Statistiken in `world/stats/<Spieler-UUID>.json` der Welt. Flugzeit, Strecke, Tempo, Scharfschaltungen und
leere Akkus liest der Server aus den Flugdaten, die er vom Piloten ohnehin bekommt; Runden und Rennen aus der
Rennwertung. Nur Crashes meldet das Spiel des Piloten mit einem kleinen eigenen Paket, der Server nimmt es nur vom
Piloten der Drohne an, höchstens vier Crashes je Sekunde (kurz hintereinander bis zu acht).

Die Werte gehen wie die Vanilla-Statistiken auch auf die Anzeigetafel: `/scoreboard objectives add Flugzeit
minecraft.custom:propwash.flight_time`.

## 4. Fortschritte im Tab „Propwash FPV“

Propwash bringt einen eigenen Tab in den Minecraft-Fortschritten mit (Taste `L`), fest in der Mod, ohne Datenpaket. Der
Tab erscheint, sobald du eine Drohne, Fernbedienung, Brille oder die Drohnen-Werkbank im Inventar hast oder deine erste
Drohne baust. Wie bei Minecraft gelten Fortschritte je Welt bzw. Server.

| Bereich | Fortschritt | So bekommst du ihn |
|---|---|---|
| Einstieg | Frisch aus der Werkbank | an der Drohnen-Werkbank eine Drohne zusammenbauen |
| | Abheben! | Drohne scharf schalten und mindestens 1 Block über den Startpunkt steigen |
| | Durch die Brille | dasselbe im FPV-Modus mit aufgesetzter Brille |
| | Boxenstopp | Akku tauschen (Schleichen + Rechtsklick mit Akku oder Akkuplatz im Service-Menü) |
| | Voll geladen | einen vollen Akku aus dem Ladegerät nehmen (Klick oder Shift-Klick) |
| Fliegen | Der erste Kilometer, Vielflieger, Langstrecke | insgesamt 1 km, 10 km bzw. 100 km Flugstrecke (Zeile „Flugstrecke“ der Statistik) |
| | Über der Bauhöhe | mit scharfer Drohne über Y 320 |
| | Sturzflug | in einem Zug über 100 Blöcke abwärts; mehr als 3 Blöcke Steigflug beginnt den Sturz neu |
| | Propwash-Veteran | 100 Crashes (Zeile „Crashes“) |
| Rennen | Erste Runde | eine Runde, die der Server wertet |
| | Persönliche Bestzeit | eine Runde ohne Flughilfe schneller als deine bisherige Bestzeit mit dieser Drohnenklasse |
| | Geisterjäger | eine Runde ohne Flughilfe schneller als der Geist, der mitfliegt |
| | Kursdesigner | eine gewertete Runde auf einem Kurs, der dir gehört |
| Freestyle | Erster Trick, Power Loop, Kombo-König | einen Trick stehen, einen Power Loop fliegen, eine Kombo aus 5 Tricks sicher beenden (Trick-Erkennung an) |
| Flugschule | Flugschüler, Klassenbester | ein Kapitel abschließen bzw. in jeder Lektion 3 Sterne |
| Welt | Lieferdienst | Fracht mit dem Greifer mindestens 10 Blöcke weit tragen und ablassen (nicht bei einem Crash) |
| | Frisch lackiert | eine Lackierung an der Werkbank anwenden |
| | Verstanden | eine Drohne per Sharecode laden |
| | Zuschauer | dem Videobild eines anderen Piloten zuschauen |
| Kampf | Zu langsam | einen Enderman mit der Drohne treffen wollen: Er teleportiert sich weg wie vor Pfeilen und nimmt keinen Schaden |
| | Drachendrohne, Witherjäger | Enderdrache bzw. Wither mit dem Todesstoß einer Drohne besiegen |

- Kampf-Fortschritte gibt es nur mit der Welt-Regel „Drohnen treffen Lebewesen“ (Standard an). Beim Enderdrachen zählt
  wie bei jedem Drohnentreffer nur seine Haupt-Hitbox.
- Tricks, Geister und die Flugschule erkennt dein Spiel; es meldet sie dem Server mit kleinen eigenen Paketen. Die
  Flugschule speichert ihren Fortschritt in deinem Spiel, deshalb meldet es den Stand beim Betreten jeder Welt, und du
  bekommst die Flugschul-Fortschritte dort nachträglich.
- Freecam, Geister und Replays zählen auch hier nicht.

## 5. Englische Kernsätze (Website)

- Propwash adds its own lines to Minecraft's statistics screen: pause menu → Statistics → General, all starting with
  "Propwash:".
- Flight time (in total and per drone class), distance flown, top speed in km/h, crashes, batteries flown empty, times
  armed, laps scored and races finished.
- Your most flown class is the one with the longest "Flight Time (…)" line; add-on frames count for the class their
  maker gives them.
- No best times: those live in the course leaderboard. The statistics are plain counters.
- Counted on the server, in singleplayer the same way: freecam, ghosts, replays and flights without a pilot do not count.
- Advancements: Propwash adds its own "Propwash FPV" tab to the advancements screen with 27 advancements, from your first
  drone, liftoff and FPV flight to 100 km flown, a 100-block dive, personal bests, beating the ghost, power loops, Flight
  School stars, cargo deliveries and taking down the ender dragon and the wither with the killing blow of a drone.
- Endermen dodge drone hits by teleporting away, just like arrows.
