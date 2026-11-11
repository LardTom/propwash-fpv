# Zonenzeile bei schmaler GUI

Für Spieler. Im Tab „Zonen“ (bis zum Owner-Playtest 23.10. im Tab „Welt-Regeln“, `docs/zones-open.md` §3) steht jede
Zone in einer Zeile mit Namen und Art („zählt als Flughilfe“, „rennfrei“, „Physik wie Welt“), darunter die Detailzeile
(Form, Größe, Lage, Priorität, Regeln), beim Operator der Besitzer und darunter die Knöpfe der Zone. In kleinen Fenstern
blieb für den Namen kaum Platz: Bei 320 GUI-Punkten Breite wurde „Selbsttest Suche“ zu „Selb…“. Ab 0.6.0 bleibt der Name
ganz lesbar.

Technik und Entscheidungen: [`design/0.6.0/fixes-polish.md`](design/0.6.0/fixes-polish.md) Punkt F (§3.F, §6.7, E17).

## 1. Anordnung

| Fall | Zeile 1 | Zeile 2 |
|---|---|---|
| genug Platz (alle breiten Fenster) | Name, rechts die Art in Zonenfarbe | Detailzeile |
| kompakt | nur der Name über die volle Breite | Art in Zonenfarbe, „ · “, Detailzeile |

Kompakt wird die Zeile nur, wenn der Name neben der Art nicht ganz passt und dort weniger als 96 Punkte
(`ZoneSection.NAME_MIN_WIDTH`) frei wären. Seit dem Tab „Zonen“ stehen die Knöpfe („Zeigen“, „Hinspringen“,
„Bearbeiten“, „Löschen“) in einer eigenen Zeile unter dem Text und brechen um, wenn sie nicht in eine Zeile passen; der
Name hat damit die ganze Breite. Die Detailzeile wird wie bisher am Ende gekürzt; der Tooltip über der Zeile zeigt alles.

Der Zonen-Editor (`ZoneEditPanel`) bleibt unverändert: Er ist mindestens 190 Punkte breit, der Name hat dort die ganze
Breite und wird bei sehr langen Namen (bis 32 Zeichen) höchstens leicht gestaucht, nie gekürzt.

## 2. Prüfung

`'-PpropwashSelftestOnly=ui-scale: Zonenname,ui-scale: Deutsch: Zonenname'` (beide Loader, etwa 1 min) legt die Zone
„Selbsttest Suche“ (sie gehört dem Spieler) an und öffnet den Tab „Zonen“ in diesen Fenstern, auf Englisch und auf
Deutsch, je als Operator (mit Besitzerzeile) und als Besitzer ohne Operator-Recht:

| Fenster | GUI-Skala | GUI-Punkte |
|---|---|---|
| 640 × 480 | 2 und automatisch | 320 × 240 |
| 854 × 480 | 2 | 427 × 240 |
| 1280 × 720 | 3 | 427 × 240 |

Der Tab scrollt, bis die Zone ganz im Bild steht. Befund `TRUNCATED`, wenn der Name gekürzt, gestaucht oder nur
teilweise sichtbar gezeichnet wird oder die Art gekürzt ist, und wenn ein Knopf der Zone gekürzt ist oder über die
Zeile hinausragt; dazu jeder Befund der Layout-Prüfung (Überlappung, Text auf Widget) für Name oder Art. Das Log nennt je Ansicht Zeilenbreite, Platz für den Namen und ob die Zeile
kompakt ist. Danach löscht der Schritt die Zone und stellt Fenster, GUI-Skala und Rechte zurück.

Bildschirmfotos (nur Fabric, mit `-PpropwashScreenshots=true`): `configurator_zones_narrow_en.png` und
`configurator_zones_narrow_de.png` (640 × 480, GUI 320 × 240, als Operator).

## 3. Weitere Kleinigkeiten in 0.6.0

- Bildschirmfoto „Welt-Regeln ohne Recht“ (`configurator_world_rules_readonly.png`, `_de`): Die Maus steht über der
  Zeile „Schwerkraft“ statt über „Wind“; der Tooltip verdeckt den Knopf „Schließen“ nicht mehr.
- „Rennen neu starten“ gibt es seit 0.5.2 nicht mehr, die Funktion heißt „Reset“ (Rücktaste oder Schalter, zurück zum
  Homepoint, ein laufendes Rennen bricht ab, Welt-Regel „Reset erlauben“). Die Doku nennt die alte Funktion nur noch
  dort, wo sie die Umstellung beschreibt.
