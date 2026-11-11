# Fremde Drohnen flüssig darstellen

Für Spieler und Server-Betreiber. Hat ein Pilot eine schlechte Verbindung (hoher Ping, schwankende Laufzeit,
Paketverlust), kommen seine Bewegungspakete beim Server in Schüben an: erst nichts, dann mehrere auf einmal. Bis
0.6.0-rc.4 stand seine Drohne bei allen anderen Spielern in solchen Pausen still und sprang danach an die neue Stelle –
in der Außensicht wie beim Zuschauen per `F7` aus seiner FPV-Kamera. Seit dem Befund OT4-3 puffert jeder Client fremde
Drohnen kurz und zeichnet sie flüssig. Die eigene Drohne des Piloten ändert sich nicht.

## 1. Was der Client macht

- **Puffern mit Zeitstempel:** Jede Position, jede Lage und das Tempo, die der Server für eine fremde Drohne schickt,
  kommen mit einem Zeitstempel in einen Puffer. Der Zeitstempel folgt dem zurückgelegten Weg durch das Tempo der Drohne
  (beides schickt der Server ohnehin), nicht nur der Ankunftszeit; so laufen Schübe und Pausen nicht schneller oder
  langsamer ab, als die Drohne geflogen ist. Ein Achtel der Abweichung zur Ankunftszeit wird jedes Mal nachgeführt.
- **Verzögerung aus dem gemessenen Jitter:** Gezeichnet wird die Drohne um 100 bis 200 ms zurück, bei ruhiger Verbindung
  100 ms, mit wachsender Schwankung mehr. Die Verzögerung ändert sich langsam (höchstens 5 %), damit nichts ruckt.
- **Dazwischen interpolieren:** Zwischen zwei Paketen läuft die Drohne gerade weiter, die Lage dreht gleichmäßig.
- **Lücken überbrücken:** Kommt länger nichts, fliegt die Drohne bis zu 250 ms mit ihrem letzten Tempo und ihrer
  letzten Drehrate weiter. Kommen die Pakete wieder, führt der Client die Abweichung in etwa 120 ms weich zurück statt zu
  springen. Bleibt eine Drohne wirklich stehen, meldet der Server Tempo 0 und sie schießt nicht über.
- **Teleports bleiben Sprünge:** Ein Sprung weiter, als die Drohne fliegen kann (Reset, Rescue, Teleport), oder eine
  Position, die der Client selbst setzt, gilt sofort. Als „fliegen kann“ zählt ihr Tempo (das größte aus Server-Tempo,
  vorigem und geschätztem Tempo) mal die Zeit seit der letzten Position plus 250 ms, mit 50 % Reserve, mindestens
  16 Blöcke; kam die letzte Position verspätet (später, als ihr Weg durch das Tempo erwarten ließ), zählt die Verspätung
  mit, zusammen höchstens 1 s. Eine stehende Drohne und Drohnen bis etwa 35 Blöcke je Sekunde im üblichen Takt von 50 ms
  springen so wie bisher ab 16 Blöcken; im Renntempo gilt eine Lücke von einer halben Sekunde nicht mehr als Teleport.
  Hat der Server vor der neuen Position ein Tempo unter 1,5 Blöcken je Sekunde gemeldet (etwa Tempo 0 beim Anhalten)
  und seitdem kein neues, steht die Drohne an ihrer letzten Position; dann gelten 16 Blöcke, egal wie schnell sie
  vorher flog und wie lange sie schon steht. So gilt ein `/tp` nach dem Anhalten aus vollem Tempo ab 16 Blöcken sofort, auch nach den
  Positionsmeldungen ohne Bewegung, die der Server für eine stehende Drohne alle drei Sekunden schickt (Befund
  AUTO70-1). Ein Tempo, das erst mit der neuen Position kommt (höchstens 10 ms davor), zählt dagegen wie ein Start: Die
  Drohne fliegt weiter durch den Puffer.
  Steht eine Drohne und wird per `/tp` oder `/rotate` versetzt oder gedreht, folgt sie wie bisher in zwei Ticks, ohne
  Überschwingen; geschätzt (extrapoliert) wird nur aus fortlaufenden Paketen einer fliegenden Drohne.

- **Setzen, Ruhen, Landen (Befund OT8-1):** Eine gesetzte, ruhende oder landende Drohne wird nie unter dem Boden
  gezeichnet und schwebt nicht. Bis zur Release-Prüfung von 0.6.0 versank eine frisch gesetzte FPV-Drohne für bis zu
  3 s etwa 17 cm im Boden, eine Kamera-Drohne stieg bis zu 2,5 Blöcke hoch und fiel dann zurück: Der Client schätzte
  aus seiner eigenen Startposition und den ersten kleinen Korrekturen des Servers (1 cm Fall, Anheben auf Gimbal und
  Beine) ein Tempo und rechnete damit 250 ms weiter, und eine ruhende Drohne meldet ihre Position erst nach drei
  Sekunden wieder. Jetzt kennt der Client von Anfang an das Tempo der Drohne aus ihrem Spawn-Paket (beim Setzen 0),
  schätzt aus Positionen nur noch bei einer fliegenden Drohne (ab 1,5 Blöcken je Sekunde) und rechnet nie unter den
  Boden weiter: Fehlen in einer Lücke die Pakete einer landenden Drohne, bleibt sie auf dem Boden stehen (gemessen mit
  ihrer Hitbox samt Zubehör unter dem Rahmen), bis die Pakete kommen. Eine Drohne mit Speer, Gimbal oder Beinen unter
  dem Rahmen steht beim Setzen sofort auf ihrem Zubehör, statt es erst in den Boden zu stecken und im nächsten Tick
  hochzuspringen. Durchflug-Regeln, mit denen eine Drohne im Block steckt, kappen nicht.

Das gilt für jede fremde Drohne: in der Außensicht, im Zuschauer-FPV und für Drohnen, die der Server selbst fliegt
(Autopilot, lose Drohnen). Die Rennwertung, Treffer und alles am Server bleiben unberührt; nur das Bild beim Zuschauer
folgt der Drohne um die Verzögerung später.

## 2. Netz

Kein neues Paket: Der Puffer nutzt die Positionen, Lagen und das Tempo, die der Server für jede Drohne schickt. Das
Netzprotokoll steigt für 0.6.0 trotzdem auf 20, weil gleichzeitig die Replays die Spur des Piloten nehmen
([`race-ghosts.md`](race-ghosts.md), „Woher die Spur kommt“).

## 3. Tests

- JUnit `drone.RemoteMotionTest`: Kreisflug mit 14 Blöcken je Sekunde, beim Piloten 120 ms Latenz, 80 ms Jitter und 5 %
  Paketverlust (Neusendung nach 300 ms), Server-Takt 50 ms, beim Zuschauer 40 ms und 25 ms Jitter; in acht Netzen je Bild
  keine Standbilder (ab 50 ms unter einem Viertel des erwarteten Wegs) und keine Sprünge (über das Dreifache), für
  Position und Blickrichtung, auch im Takt des Spiels und ohne Tempo vom Server; die alte Interpolation über zwei Ticks
  friert im selben Netz ein. Dazu: Verzögerung folgt dem Jitter, Teleport schneidet, gestoppte Drohne schießt nicht über,
  eine stehende Drohne dreht bei drei Wenden je Tick stetig zum Ziel, eine angehobene stehende Drohne behält ihre Lage
  und steigt nicht über den Teleport.
- Renntempo (Befund AUTO68-1): Mit 35, 60, 100 und 165 Blöcken je Sekunde leert in acht Netzen mit Neusendung nach 400,
  500 und 600 ms keine Lücke den Puffer (vorher bei 35 Blöcken/s und 500 ms 41-mal, bei 165 Blöcken/s schon bei 300 ms
  294-mal); bei 35 Blöcken/s (Radius 75) und 400 ms gibt es nicht mehr Standbilder und Sprünge als bei 14 Blöcken/s
  (Radius 30) im selben Netz (vorher 6 und 12 statt 2 und 3); eine Lücke von 300 ms bei 60 Blöcken/s läuft ohne
  Standbild und Sprung durch, ein Teleport um 20 Blöcke bei 35 Blöcken/s gilt weiter sofort.
- Renntempo mit 6 % Verlust (Befund G-OT4-2-1): Bei 35 und 40 Blöcken je Sekunde und Neusendung nach 400, 500 und
  600 ms leert in acht Netzen keine Lücke den Puffer, im Takt des Spiels wie ohne Tempo vom Server; eine Lücke von
  500 ms bei 35 Blöcken/s gilt nicht als Teleport. Weiter sofort gelten: 20 Blöcke zur Seite im Takt, 25 Blöcke weiter
  als in 500 ms geflogen, `/tp` um 20 Blöcke zwei Sekunden nach dem Anhalten aus 35 Blöcken/s (Tempo 0 nach oder mit der
  letzten Position; vor dem Fix glitt die Drohne dorthin), `/tp` einer stehenden Drohne und 70 Blöcke nach 3 s Lücke.
- `/tp` nach dem Anhalten mit Positionsmeldung ohne Bewegung (Befund AUTO70-1): aus 14, 35 und 60 Blöcken je Sekunde,
  Meldung 1 bzw. 3 s nach dem Halt, `/tp` um 20 und 60 Blöcke nach 2, 3,5 und 5 s, dazu drei Meldungen hintereinander,
  Tempo 0 mit der letzten Position und 60 Blöcke/s ohne Meldung: Der Puffer wird geleert, die Drohne steht im ersten
  Bild am Ziel. Vor dem Fix lag die Grenze bei 26,3 Blöcken (aus 14 Blöcken/s), 65,6 (aus 35) und 112,5 (aus 60), ohne
  Meldung aus 60 Blöcken/s bei 27,0; ein `/tp` um 20 Blöcke glitt drei Bilder lang. Gegenprobe: Startet die Drohne nach
  dem Stehen mit 900 ms Stockung und ist 30 Blöcke weiter, bleibt der Puffer (Grenze 75 Blöcke aus dem neuen Tempo).
- Setzen und Landen (Befund OT8-1), JUnit `drone.RemoteMotionTest`: Setzen wie im Spiel (Startposition des Clients,
  die Server-Position im ersten Tick noch einmal, im zweiten die Ruhelage, danach alle 60 Ticks) für eine FPV-Drohne
  (1 cm Fall), eine Freestyle mit Speer (10,6 cm Anheben) und eine Kamera-Drohne (16,2 cm) über 100 Ticks: nie unter
  oder über der Server-Höhe, kein Schritt größer als die Bewegung am Server, am Ende auf 1 mm gleich (vorher 167 mm
  darunter, 1600 bzw. 2533 mm darüber; mit der zweiten Server-Position ohne Tempo aus dem Spawn-Paket 441 bzw. 699 mm
  darüber). Eine ruhende Drohne mit kleinen Korrekturen ohne Tempo im Takt sinkt nicht (vorher 37 mm). Landung mit
  3 Blöcken/s, Pakete ab 100 ms vor dem Aufsetzen 150, 300 und 600 ms verspätet: nie unter dem Boden (vorher 105, 300
  und 300 mm).
- Selbsttest „drone: Gesetzte Drohne sinkt nicht ein, steigt nicht und springt nicht …“
  (`drone.client.PlacedDroneSelfTests`): setzt wie ein Spieler eine FPV-Drohne, eine Freestyle mit Beispiel-Speer unten
  und, mit dem Kamera-Add-on in `runs/selftest-client/mods/`, dessen Kamera-Drohne mit dem tiefsten Zubehör; 100 Ticks
  lang je Bild gezeichnete Höhe gegen die Höhe am Server. Vorher auf Fabric: Speer 95,9 mm im Boden und 446,3 mm über
  der Server-Höhe, Kamera-Drohne `camdrones:cine` 147,5 bzw. 685,2 mm; danach 0,0 mm auf beiden Loadern. „… landet bei
  einer Lücke im Netz nicht unter dem Boden“ landet FPV und Speer mit 300 ms Lücke beim Aufsetzen; ohne Kappung
  300 mm unter dem Boden, mit 0.
- Weiter grün: „drone: Lage per /tp und /rotate …“ (vier Schritte), die Gruppen `video:`, `trails:`, `bindung:`.
- Selbsttest „drone: Fremde Drohne bei schlechter Verbindung des Piloten ohne Standbilder (Außensicht und Zuschauer-FPV)“
  (`drone.client.RemoteMotionSelfTests`): dieselbe Kreisbahn mit einer echten Drohne im Client, die Pakete kommen nach
  `dev.BadNetwork` in Echtzeit; gemessen wird je Bild die gezeichnete Position und die Blickrichtung der Drohne mit
  `dev.FreezeMeter`. Vor dem Fix: 7 Standbilder (bis 288 ms) und 21 Sprünge in 776 Bildern; danach 0 und 0.
