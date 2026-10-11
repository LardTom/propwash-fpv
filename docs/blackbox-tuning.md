# Tuning mit Blackbox Explorer und PIDtoolbox

Für Spieler. Wie eine echte Drohne mit Betaflight zeichnet Propwash auf Wunsch jeden Flug auf – Knüppel, Sollwerte, Gyro,
PID-Anteile, Motoren und Akku bei jedem Durchlauf der Regelschleife. Die Logs öffnen echte Tuning-Werkzeuge:

- **Betaflight Blackbox Explorer** – im Browser unter [blackbox.betaflight.com](https://blackbox.betaflight.com/) oder
  als Programm. Zeigt alle Kurven über der Zeit, die Knüppel, die Motoren und ein Spektrum (Rauschen nach Frequenz).
- **PIDtoolbox** – [pidtoolbox.com](https://pidtoolbox.com/). Sprungantwort je Achse (wie schnell und wie sauber das
  Gyro dem Sollwert folgt), Rauschen nach Gas und Frequenz, Filter-Verzögerung.

Technik und Feldliste: `docs/design/blackbox.md`.

## 1. Blackbox einschalten

1. Konfigurator öffnen (`K`), Tab **Blackbox**.
2. **Loggen, wenn scharf** auf *An*. **Lograte** 1/1 lassen (bei der Standard-Regelschleife 2 kHz, etwa 4 MB pro
   Flugminute); **Speicher für Logs** legt fest, wie groß der Ordner höchstens wird – die ältesten Logs werden dann
   gelöscht.
3. Speichern. Ab jetzt beginnt bei jedem Scharfschalten ein neuer Log, beim Entschärfen endet er.

Nur bestimmte Flüge loggen: Funktion **Blackbox** im Tab **Modi** auf einen Schalter legen (wie der Modus BLACKBOX in
Betaflight) – geloggt wird dann nur, solange der Schalter an ist. Ohne Schalter pausiert die Taste
**Blackbox pausieren / fortsetzen** (Standard `Num 7`) die Aufzeichnung und setzt sie fort.

## 2. Einen Testflug fliegen

Gute Logs für das Tuning sind kurz (30 s bis 2 min) und enthalten, was man prüfen will:

- **Sprungantwort (P, D, Feedforward):** im Acro-Modus kurze, harte Knüppelschläge auf Roll und Nick, dazwischen
  loslassen; ein paar Flips und Rollen.
- **Rauschen und Filter:** einmal langsam Vollgas und zurück, dann zügig hin und her.
- **Prop-Wash:** steile Sturzflüge in die eigene Luft, harte Kehren.
- **I-Anteil:** lange Kurven und schnelles Geradeausfliegen mit wechselndem Gas.

## 3. Log öffnen

Im Tab **Blackbox** auf **Ordner öffnen** klicken. Die Logs heißen
`propwash_JJJJ-MM-TT_HH-MM-SS_<Name der Drohne>.bbl`.

- **Blackbox Explorer:** Datei öffnen (oder auf die Seite ziehen). Die Kopfdaten zeigen die Firmware-Zeile („Betaflight 4.5.1 compatible (Propwash v…)“),
  PIDs, Rates und Filter deiner Drohne. Für das Tuning Gyro und Sollwert (Setpoint) je Achse übereinanderlegen und das
  Spektrum einblenden.
- **PIDtoolbox:** Datei laden (PIDtoolbox dekodiert sie mit `blackbox_decode`), dann die Sprungantwort (Step Response)
  und die Spektralanalyse ansehen.

## 4. Lesen und ändern

| Was du siehst | Bedeutung | Im Tab **PID-Tuning** bzw. **Rates** ändern |
|---|---|---|
| Sprungantwort steigt langsam, bleibt unter 1 | zu wenig P oder Feedforward | P oder FF erhöhen |
| Sprungantwort schießt deutlich über 1 hinaus, schwingt nach | zu viel P, zu wenig D | P senken oder D erhöhen |
| Gyro folgt dem Sollwert mit Verzögerung | Feedforward zu klein, Filter zu tief | FF erhöhen, Gyro-/D-Tiefpass höher |
| Schwingen bei Sturzflug und harten Kehren (Prop-Wash) | zu wenig D | D erhöhen, I-Relax prüfen |
| Spektrum: breites Rauschen oberhalb 100–200 Hz im Gyro | Motor- und Rahmenvibration | Gyro-Tiefpass senken, beschädigte Props tauschen |
| D-Anteil (`axisD`) sehr unruhig, Motoren warm | D zu hoch oder D-Tiefpass zu hoch | D senken oder D-Tiefpass tiefer |
| I-Anteil wandert in langen Kurven weit weg | Schwerpunkt, Wind oder zu wenig I | I erhöhen |
| Motoren stoßen oft an 100 % (`motor[0–3]`) | Drohne schwach oder zu harte Rates | Rates senken, stärkere Teile |

Nach jeder Änderung neu fliegen und vergleichen – eine Änderung auf einmal. Das Flugmodell von Propwash reagiert wie
eine echte Drohne auf PIDs, Filter und Rates; Werte aus dem Log lassen sich also direkt im Konfigurator übertragen.

## Was im Log steht

- Gyro gefiltert (`gyroADC`) und roh (`gyroUnfilt`, auch als `debug[0–2]` im Modus *Gyro Scaled*), Sollwert
  (`setpoint`), Knüppel (`rcCommand`), P/I/D/F-Anteile (`axisP/I/D/F`), Motoren (`motor`, 0–100 % als DShot-Wert),
  Motordrehzahl (`eRPM`), Akkuspannung und -strom, Barometerhöhe, Funkverbindung (`rssi`), Flugmodi und Failsafe.
- GPS: Propwash legt die Welt um den Punkt 0° N 0° O (die Karte im Explorer zeigt also den Atlantik vor Afrika) – Weg
  und Tempo stimmen.
- Was das Flugmodell nicht hat, steht im Kopf auf 0: dynamischer Notch, RPM-Filter, dynamische Tiefpässe.

## Häufige Fragen

- **Kein Log entstanden?** Blackbox im Tab eingeschaltet und gespeichert? Liegt die Funktion *Blackbox* auf einem
  Schalter, muss er beim Fliegen an sein. Der Status im Tab zeigt, was die Blackbox gerade tut.
- **Logs werden gelöscht?** Die Speichergrenze im Tab ist erreicht; älteste Logs vorher wegkopieren oder die Grenze
  erhöhen.
- **Der Explorer zeigt „Betaflight“?** Absicht: Die Logs melden Kompatibilität mit Betaflight 4.5.1 (Kopfzeile
  „Betaflight 4.5.1 compatible (Propwash v…)“), damit beide Werkzeuge jedes Feld verstehen und richtig skalieren.
  Propwash ist kein Betaflight; Board und Ziel heißen „PROPWASH“.
