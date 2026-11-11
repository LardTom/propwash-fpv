# Sharecodes im Spiel: Werkbank, Konfigurator und `/propwash code`

Für Spieler und Server-Admins. Ab Propwash 0.6.0 gehören Sharecodes zur Basis-Mod: Propwash liest PW1, PW2, PW3 und
den alten Drohnen-Editor-Code PWD1 und schreibt nur noch PW3 (`PW3-…`), auch im Drohnen-Editor (Abschnitt 8). Das Format beschreibt [`sharecode/pw3.md`](sharecode/pw3.md), die Teile-Tabellen für
Add-ons [`sharecode/tables.md`](sharecode/tables.md). Technik und Entscheidungen:
[`design/0.6.0/sharecodes-pw3.md`](design/0.6.0/sharecodes-pw3.md) §3.8, §3.13, §3.14 und §5.

## 1. Orte

- **Werkbank:** Knopf „Sharecode“ im Kopf der Drohnen-Werkbank (bei wenig Platz „Code“, sonst das Symbol `</>`). Braucht
  der Knopf eines Add-ons den Platz, wird „Sharecode“ zuerst „Code“ und dann `</>`, bevor der Add-on-Knopf hinter „⋯“
  wandert. Er öffnet das Fenster „Sharecode“ mit den Reitern „Exportieren“ (fertige Werkbank-Drohne) und „Importieren“
  (leere oder unfertige Werkbank).
- **Konfigurator:** Tab „Sharecode“ (`propwash:sharecode`). Oben die Drohne in der Haupthand teilen, unten einen Code
  einfügen und Tuning, Lack oder OSD direkt übernehmen, im Kreativmodus eine Drohne erzeugen, im Überleben die
  Einkaufsliste zeigen.
- **Befehle:** `/propwash code …` (Abschnitt 4).
- **Flugschule:** Szene „Builds teilen“.

## 2. Exportieren

- Ebenen: Teile immer, dazu wahlweise Lack, Tuning, OSD (macht den Code länger) und Name. Eine Kreativ-Drohne nimmt ihre
  Editor-Werte immer mit (Ebene „Kreativ-Werte“, im Tab und bei `/propwash code export`).
- **Add-on-Infos (Name und Link):** erscheint, sobald ein Teil aus einem Add-on ohne Eintrag im offiziellen Verzeichnis
  stammt, und ist dann an. Der Code trägt dann Name und Link des Add-ons mit, damit andere wissen, wo es das gibt.
  Add-ons im Verzeichnis (Propwash, Just More Parts) brauchen das nicht.
- Hat ein Add-on keine Teile-Tabelle, steht unter dem Code „X hat keine Teile-Tabelle – der Code wird länger“: seine
  Teile gehen als Text in den Code.

## 3. Importieren

Nach dem Einfügen erscheinen sofort, von oben nach unten:

1. **Kopfzeile** mit Format und Ebenen („PW3-Code – Teile, Lack“). Bei PW1/PW2 der Hinweis „Alter Code (PW2) –
   funktioniert weiter. Neue Codes sind PW3.“ und der Knopf **„Als PW3 kopieren“** (dieselbe Umrechnung wie auf der
   Website). Ein alter Drohnen-Editor-Code PWD1 zeigt denselben Hinweis, dazu „PWD1 enthält nur die Editor-Werte; als
   Bauplan gilt die Freestyle-Drohne mit Kreativ-Flugcontroller.“, und „Als PW3 kopieren“ wandelt ihn um. Enthält ein PW3-Code Teile aus einer neueren Add-on-Version oder umbenannte Teile, schreibt derselbe Knopf
   den Code neu und behält die Köpfe der unbekannten Namensräume.
2. **Falsche Prüfsumme:** rotes Banner „Der Code passt nicht zur installierten Version von X – Teile können falsch
   sein“. Die Ebene Teile ist gesperrt, bis „Teile trotzdem übernehmen“ gedrückt ist. Lack, Tuning, OSD und Name gehen
   immer.
3. **„Add-ons in diesem Code“:** je Namensraum eine Zeile mit Symbol, Name, nötiger und installierter Version:
   „X ab 1.2 – nicht installiert“, „X ab 1.2 – installiert ist 1.1“, „X – auf diesem Server nicht aktiv“ (Add-on nur
   beim Spieler), „Add-on Nr. 7 aus dem offiziellen Verzeichnis (neuer als dieses Propwash)“. Bei Problemen folgen die
   Link-Knöpfe „Auf Modrinth öffnen“ / „Auf CurseForge öffnen“ (bzw. das Add-on-Verzeichnis); sie öffnen immer erst den
   Bestätigungsdialog von Minecraft. Andere Adressen aus dem Code sind nie klickbar und stehen nur als Text mit
   „lade Add-ons nur von Modrinth oder CurseForge“.
4. **„Ersatzteile“** (nur bei unbekannten oder inaktiven Teilen): je Ersatz eine Zeile „alt → neu“. Zuerst gilt der
   `fallback` aus der Teile-Tabelle (installiert oder Verzeichnis-Abzug), sonst für Rahmen, Stack, Motor, Prop, Video
   und Akku das erste Propwash-Preset, das an den fehlenden Stellen einen baubaren Bauplan ergibt. Unbekanntes Zubehör
   bekommt nur seinen `fallback` und nur, wenn der an derselben Halterung passt. Sonst fällt es weg („→ (entfernt)“),
   und Rahmen, Motoren, Akku und das übrige Zubehör bleiben. Ein Gimbal vorn ohne Add-on kostet also nur den Gimbal,
   nicht die ganze Drohne. Passt ein bekanntes Zubehör am Ersatz nicht, fällt es ebenso weg. Ohne Ersatz: „Kein
   passendes Ersatzteil für …“. In der Werkbank schaltet „Ersatzteile verwenden“ den Ersatz ein (Vorschau, Prüfung,
   Einkaufsliste und Übernehmen rechnen dann damit), im Tab erzeugt „Mit Ersatzteilen erzeugen“ im Kreativmodus die
   Drohne. Der Name der Drohne bleibt.
5. Teile, Prüfung, Flugwerte, weitere Ebenen (bei Kreativ-Werten „Kreativ-Drohne: N eigene Werte, M Basis-Teile“, die
   Flugwerte rechnen dann mit ihnen) und im Überleben die **Einkaufsliste**: „1× Rahmen – im Inventar“,
   „4 von 4× Motor fehlen“, für Teile fehlender Add-ons „1× Rahmen Nr. 12 – braucht X ab 1.2“ (Name aus dem
   Verzeichnis-Abzug, sonst Art und Nummer).

„Übernehmen“ schickt die gewählten Ebenen an den Server; er prüft Rechte, Kosten und Teile wie bisher und antwortet mit
dem Ergebnis (zum Beispiel „Es fehlen Teile“ mit der Liste). Inaktive Teile lehnt die Sitzung ab, mit Ersatz gelingt der
Import. Ohne Kreativmodus kommen Teile aus dem Inventar, Lack kostet Farbstoffe.

Lack, Tuning und OSD gehen auch, wenn Teile des Codes hier fehlen oder inaktiv sind (etwa ein Code mit Teilen aus Just
More Parts ohne das Add-on). Für Ebenen ohne Teile schicken Tab, Werkbank-Fenster und `/propwash code import` im
Überleben dann den Bauplan des Ziels mit (gehaltene Drohne bzw. Werkbank-Drohne, ohne fertige Drohne den
Propwash-Freestyle) statt des Bauplans aus dem Code; die Teile der Drohne bleiben unverändert.

Bei GUI 320 × 240 wird das Fenster einspaltig (keine Vorschau), alle Listen scrollen.

## 4. Befehle

| Befehl | Recht | Wirkung |
|---|---|---|
| `/propwash code export [<ebenen>]` | alle | Code der Drohne in der Haupthand, anklickbar zum Kopieren (`parts,paint,tune,name`, dazu `creative`; ohne Angabe bei einer Kreativ-Drohne mit ihren Werten; OSD nur im Fenster) |
| `/propwash code import <code> [substitutes]` | alle | Kreativ: Drohne erzeugen, auch mit Kreativ-Werten (mit `substitutes` aus Ersatzteilen); Überleben: Lack und Tuning auf die Drohne in der Hand, Kreativ-Werte nur mit Hinweis |
| `/propwash code info <code>` | alle | Ebenen, Name, je Namensraum Version, Prüfsummen-Ergebnis und Link, Teile, Kreativ-Werte, Prüfung, Flugwerte |
| `/propwash code give <code> [<spieler>] [substitutes]` | 2 | Drohne aus dem Code an Spieler geben, mit Kreativ-Werten |
| `/propwash code convert <code>` | alle | PW1/PW2/PWD1 in PW3 umrechnen, Ergebnis anklickbar zum Kopieren |

Befehle lesen am Server mit den Tabellen des Servers. Codes mit falscher Prüfsumme erzeugen keine Drohne; dafür das
Import-Fenster mit „Teile trotzdem übernehmen“ nehmen.

## 5. Just More Parts

Propwash 0.6.0 verlangt Just More Parts 1.2.0. Just More Parts 1.1.x passt nicht zu Propwash 0.6.0: Der Start bricht
ab, Fabric nennt auf seinem Fehlerbildschirm die Unverträglichkeit mit Just More Parts unter 1.2.0, NeoForge zeigt einen
Ladefehler mit dem Grund „Just More Parts 1.1.x does not work with Propwash 0.6.0 – please update to Just More Parts
1.2.0. (Just More Parts 1.1.x passt nicht zu Propwash 0.6.0 – bitte auf 1.2.0 aktualisieren.)“ (`docs/namespace-guard.md`
§4). Der frühere Altmodus (ein Sharecode-Knopf trotz JMP 1.1.x, Hinweis je Spielstart, Weiterleitung von
`justmoreparts:sharecode` auf diesen Tab) ist entfallen. PW1- und PW2-Codes, auch die von Just More Parts 1.1.x, bleiben
lesbar; Just More Parts 1.2.0 hat keinen eigenen Knopf und verweist mit `/jmp code` auf `/propwash code`.

## 6. Grenzen

- Codes bis 4000 Zeichen; ein OSD-Layout macht den Code am längsten.
- Kreativ-Werte übernimmt nur „Drohne erzeugen“ (Kreativmodus oder Recht 2, auch `give`) und der Drohnen-Editor; die
  Werkbank übernimmt nur den Bauplan, im Überleben gilt nur der Bauplan.
- Klickbar sind nur Modrinth, CurseForge und das Add-on-Verzeichnis auf propwashfpv.com.

## 7. Kernsätze für die Website (Englisch)

- Share codes now belong to Propwash: the *Share code* button in the workbench, the *Share code* tab in the configurator
  and `/propwash code`.
- Paste any code – PW1, PW2 or PW3. Old codes keep working; *Copy as PW3* converts them.
- Missing an add-on? The import shows which one, which version you need and where to get it (Modrinth or CurseForge,
  always with a confirmation first).
- In survival the shopping list shows what is missing; stand-in parts let you build the drone without the add-on.
- Paint, tuning and OSD from a code apply even when you don't have the add-on its parts come from.
- If a code doesn't match your add-on version, parts stay locked until you choose *Import parts anyway*.
- Propwash 0.6.0 needs Just More Parts 1.2.0; old codes made with Just More Parts 1.1 still read fine.
- The Drone Editor now copies PW3 codes too: the drone with its creative values. Creative mode creates it with every value;
  old `PWD1-…` editor codes still paste and convert with *Copy as PW3*.

## 8. Drohnen-Editor und Kreativ-Drohnen (Befund PWD3-1)

- „Teilen-Code kopieren“ im Drohnen-Editor schreibt einen PW3-Code: Teile, Lack, Tuning samt Kamerawinkel und Name der
  Drohne, dazu die Ebene „Kreativ-Werte“ mit den Basis-Teilen und Werten des Editors (auch noch nicht gespeicherte),
  mit Add-on-Liste wie jeder PW3-Code.
- „Code einfügen“ im Editor nimmt PW3-Codes mit Kreativ-Werten und alte PWD1-Codes („Alter PWD1-Code übernommen – …“); ein
  PW3-Code ohne Kreativ-Werte wird abgelehnt („enthält keine Kreativ-Werte“).
- Tab „Sharecode“, Fenster und Chat zeigen „Kreativ-Drohne: N eigene Werte, M Basis-Teile“. Im Kreativmodus legt
  „Drohne erzeugen“ die Drohne mit den Werten an (Payload `propwash:share_creative_create`, der Server prüft Kreativmodus
  oder Recht 2, baubaren Bauplan und ein brauchbares Flugmodell). Im Überleben steht „Kreativ-Werte übernimmst du nur im
  Kreativmodus, im Überleben gilt nur der Bauplan.“, im Werkbank-Fenster „Die Werkbank übernimmt nur den Bauplan – füge den
  Code danach im Drohnen-Editor ein“.
- Alte PWD1-Codes: lesbar im Editor, im Tab, im Fenster, im Chat (klickbar) und mit `/propwash code`, umwandelbar mit
  „Als PW3 kopieren“ bzw. `convert`. Format: [`sharecode/pw3.md`](sharecode/pw3.md) §3.6 und §7.1.
