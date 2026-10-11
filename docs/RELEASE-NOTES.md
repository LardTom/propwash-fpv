# Ausführliche Release-Notizen — Propwash FPV

`CHANGELOG.md` ist seit dem 02.10.2026 kurz und für Spieler geschrieben (Highlights, *New*, *Improved*, *Fixed*,
Kompatibilität). Hier stehen die Einzelheiten, die dort keinen Platz haben: Zahlen, Bereiche, Bedienung im Detail,
Ursachen der Fehler und Verweise auf die Design-Dokumente. Was Addon-Autoren betrifft (API, Datenformate,
Netzprotokoll, Werkzeuge), steht in [`docs/api/changes.md`](api/changes.md). Die Arbeitsschritte zwischen den Releases
stehen in den Tages-Changelogs unter `changelogs/`.

---

## 0.5.0

### Nach der Gegenprüfung (Fixes vom 09./10.10.)

Unabhängige Prüfer haben den Kandidaten der 0.5.0 in fünf Bereichen nachgemessen. Behoben in einem Durchgang:

- **Kamera-Vibration mit abgenutzten Teilen:** Der Zuschlag für Verschleiß rechnete mit `Telemetry.vibration` und wuchs
  ab dem ersten Prozent Abnutzung mit der vierten Potenz der Drehzahl – vier Motoren bei 97 % ließen das Bild bei
  Vollgas so zittern wie die fabrikneue Drohne vor der Korrektur (0,092° RMS gegen 0,019° im Schweben). Die Kamera
  nimmt jetzt `video.WearShake`: Teile ab 90 % Zustand (unbeschädigt nach `PartWear.DAMAGED_BELOW`) zählen nicht,
  darunter beginnt der Pegel bei null. Im Spiel gemessen: Props 95 % und Motoren 97 % 0,0193° bei Vollgas gegen
  0,0195° im Schweben; ein auf 50 % verbogener Prop 0,217° gegen 0,024°. Physik, Goldens und Kalibrierung bitgleich
  (`docs/design/fpv-feel.md` §3.1).
- **Glasbruch im schnellen Anflug:** Server-Reichweite der Freigabe 32 Blöcke plus 0,5 s Flugweg (höchstens 64),
  erneute Anfrage direkt nach „zu weit“, Anfragen nach Zeit bis zum Aufprall im Flugweg geordnet, Budget in Blöcken
  (640 je Sekunde) statt in Anfragen. Vorher wirkte eine Scheibe bei 36 m/s aus 50 Blöcken in 4 von 20 Flügen (30
  Bilder/s) als Wand (`docs/design/drone-break-blocks.md` §4, Schritt 0).
- **Bodenkontakt:** Hülle aus den gezeichneten Netzpunkten statt aus Quader-Ecken; aufrecht ruhende Drohnen schwebten
  bis 28 mm, jetzt 0,00 mm, in jeder Lage höchstens 0,8 mm (`docs/design/ground-contact.md` §2.5).
- **Falsche Brille:** Die Absagen der Funke sind 615–716 Punkte breit und standen als eine abgeschnittene Zeile über
  der Schnellleiste; zu breite Meldungen brechen jetzt um und bleiben länger stehen (`docs/design/ui-scaling.md`).
- **Oberfläche auf Deutsch:** Die Prüfung `ui-scale:` lief nur auf Englisch. Auf Deutsch gab es bei 427 × 240 Punkten
  (Standardfenster) 16 und bei 320 × 240 46 Befunde: Hinweis des Gate-Editors unter dem Bildschirmrand, gekürzter
  Homepoint-Wert, mitten im Wort umbrochener Status, überdeckter Text in der Bestenliste und weitere. Alle behoben, die
  Prüfung läuft jetzt in beiden Sprachen (`docs/design/ui-scaling.md`).
- **Texte, die nie gezeichnet wurden:** Zwei Felder hatten eine feste Zeilenzahl und ließen den Rest weg, ohne dass
  die Prüfung es sehen konnte. Im Modi-Tab endete die Beschreibung der gewählten Funktion im Standardfenster bei
  sieben von acht geprüften Funktionen mitten im Satz; in den Flugschul-Szenen fehlte bei 320 Punkten Breite in 97 von
  108 deutschen und 79 von 108 englischen Schritten die dritte Zeile. Beide Felder richten sich jetzt nach dem
  längsten Text, die Beschreibung läuft um die Knöpfe herum, und die Prüfung meldet weggelassene Zeilen als Befund.
  Dazu: Gate-Editor ohne freie Maus passt auf der Seite „Redstone“ auf Deutsch in 240 Punkte Höhe, der Hinweis zum
  Signal steht auch beim Freiform-Gate da, Scrollleisten der Aufnahmen und der OSD-Elementliste lassen sich ziehen,
  Lektionstitel im Flugtraining brechen nicht mehr im Wort um (`docs/design/ui-scaling.md`).
- **Linksklick im Flugtraining und in der Beitritts-Prüfung:** Beide Screens verglichen die Maustaste mit 0; die linke
  Taste ist in Minecraft 26.3 aber `InputConstants.MOUSE_BUTTON_LEFT` = 1. Kapitel und Lektionen des Flugtrainings
  ließen sich nicht mit der Maus wählen, die Links zu fehlenden Add-ons nicht anklicken. Aufgefallen beim neuen
  Scroll-Test der Beitritts-Prüfung; die Selbsttests wählten bisher über die Programmierschnittstelle statt per Klick.
- Die Beitritts-Prüfung scrollt mit Bild auf/ab, Pos1/Ende, Pfeiltasten und an der Scrollleiste, die Aufnahmen-Liste
  mit Bild auf/ab.

- **Oberfläche mit verbundener Funke (Fix vom 10.10.):** Die GUI-Prüfung öffnete den Konfigurator nie mit einer Funke
  und sah deshalb nicht, was mit ihr gekürzt war. Tab „Empfänger“: Die Statuszeile bricht um und läuft über die ganze
  Breite – vorher endete „Verbunden: Radiomaster Boxer (Funke · EdgeTX/OpenTX) – noch nicht eingerichtet“ bei jeder
  Größe mit „…“ und verlor den Teil, der sagt, was zu tun ist; die Richtungs-Umschalter der vier Kanäle zeigen
  „Norm.“/„Inv.“ statt „N…“, bei 320 Punkten Breite steht je Zeile ein Kanal. Kopfzeile: Der Name der Funke bekommt
  den Platz, der übrig ist, statt höchstens ein Fünftel der Breite. Modi-Tab mit Funke: Das Feld unter der Liste
  ordnet sich bei schmalen Fenstern um (Quelle in eigener Zeile, Hinweise und Tastenzeile brechen um, Knöpfe teilen
  sich die Breite – vorher überlappten „Befehl einrichten …“ und „Tasten…“ bei 320 Punkten Breite). Hinweis und
  Lampen-Legende des Modi-Tabs, der Zustand im Tab „Freestyle“ und der Hinweis neben der Rundenzahl in „Kurs
  bearbeiten“ fielen bei kleinen Größen stumm weg; sie stehen jetzt immer da. Empfänger-Assistent, Seite „Gerät“: Die
  Live-Listen scrollen über der Fußzeile, statt halb darunter zu liegen; alle Achsen und die Tasten sind erreichbar.
  Die Prüfung öffnet den Konfigurator zusätzlich mit simulierter Funke und mit Maus-Steuerung und meldet Text unter
  einer später gezeichneten Fläche als `COVERED` (`docs/design/ui-scaling.md`). Die Seiten „Kanäle“ und „Schalter“
  des Assistenten zeigen bei 240 Punkten Höhe weiter die kurze Anweisung statt Absatz und Animation (bewusst).

Offen geblieben (bewusst): Fracht im Greifer zählt nicht zur Hülle (braucht das Flugmodell), der Video-Tab zeigt ohne
Brille keine Einstellungen (so entschieden), Bandos sitzen auf einzelnen Seeds 1–2 Blöcke über dem Gelände am Rand.

### Oberfläche: Tooltips, Scrollleisten und Tasten (siebte Gegenprüfung, Fixes vom 11.10.)

Die siebte Gegenprüfung fuhr mit der Maus über jede Ansicht und zog an jeder Scrollleiste – beides tat `ui-scale:`
nie: Die Prüfung parkte die Maus vor jeder Ansicht. Behoben in einem Durchgang:

- **Tooltips laufen nicht mehr aus dem Bild.** Ein Satz als Tooltip war eine einzige Zeile: „Invertiert: Schalter
  umkehren – …“ im Tab „Modi“ ist auf Deutsch 667 Punkte breit, im Standardfenster (427 × 240 Punkte) fehlten 240
  Punkte, bei 1080p mit automatischer Skala 187. Ebenso der Hinweis zur eigenen Farbe in der Lackierung, die Hinweise
  der Werkbank, bei 320 × 240 Punkten auch die Tasten im Tab „Modi“, die Elementliste im OSD-Tab und das Ladegerät.
  Diese Tooltips brechen jetzt bei 170 Punkten um wie die der Regler und sind nie breiter als der Bildschirm.
- **Lange Tooltips beginnen nicht mehr über dem Bild.** Die Tooltips der Regler sind 12–17 Zeilen hoch; Minecraft
  setzt sie über den Regler, ohne zu prüfen, ob dort Platz ist. Bei 240–270 Punkten Höhe fehlten bis zu fünf Zeilen
  am Anfang („Max. Scharf-Winkel“, „Encoder“, „Gravitation“). In Propwash-Screens wird jeder Tooltip jetzt in den
  Bildschirm geschoben – auch Item-Tooltips in Werkbank, Ladegerät und Wartungsmenü und die Tooltips in Tabs von
  Add-ons.
- **Tooltips, die höher als der Bildschirm sind, scrollen.** Die erweiterte Prüfung fand drei, die auch verschoben
  nicht passen: die Details der Werkbank (34 Zeilen) und auf Deutsch bei 240 Punkten Höhe zwei Welt-Regeln (24 und 25
  Zeilen). Sie zeigen so viele Zeilen, wie passen, und darunter „Mausrad: Zeilen 1–22 von 34“; das Mausrad blättert
  durch den Tooltip.
- **Scrollleiste der Flugschule:** Die Leiste der Szenenliste (Tab „Flugschule“ und Übersicht) war nur gezeichnet –
  sie lässt sich jetzt fassen und ziehen wie alle anderen. Die Übersicht blättert auch mit Bild auf/ab und Pos1/Ende.
- **Tasten in GPS Rescue und Flugschule:** Bild auf/ab und Pos1/Ende bewegten den Tab „GPS Rescue“ nur um eine Zeile
  je Druck; jetzt blättern sie eine Seite bzw. springen an den Rand. In der Flugschule übersprang Bild ab Karten
  (sieben Zeilen je Druck bei einer oder zwei sichtbaren); jetzt folgt Seite auf Seite.
- **Beitritts-Prüfung:** Passte der Text bis auf höchstens 4 Punkte genau in das Fenster, zeichnete der Screen eine
  Scrollleiste, obwohl es nichts zu scrollen gab (960 × 540 und 640 × 360 Punkte). Gefunden von der neuen Prüfung der
  Scrollleisten; die Leiste erscheint jetzt nur, wenn der Text scrollt.
- **Welt-Regeln ohne Schreibrecht:** Auf Deutsch bei 320 × 240 Punkten stand der Wert direkt an der Beschriftung
  („Teile müssen zusammenpassenAn“). Die Wertspalte rückt jetzt hinter die längste Beschriftung, mit 4 Punkten
  Abstand.
- **Prüfung selbst:** `ui-scale:` fährt in den sechs kleinen Größen jede Ansicht mit der Maus ab (Widgets, Texte,
  Slots, jede Seite), misst jeden Tooltip samt Rahmen gegen den Bildschirm und scrollt hohe bis ans Ende (Befund
  `TOOLTIP`); in jeder Größe zieht sie an jeder gezeichneten Scrollleiste (Befund `SCROLLBAR`). Acht Ansichten
  bekommen bei 320 × 240 und 427 × 240 Punkten ein Bild mit Tooltip (`docs/screenshots/ui-scale/…_tooltip.png`).
  Zeitgrenze des vollen Laufs 80 statt 70 Minuten (`docs/testing.md`, `docs/design/ui-scaling.md`).

Unverändert (so entschieden): Die innere Elementliste des OSD-Tabs nimmt nur das Mausrad; Bild auf/ab und Pos1/Ende
blättern die Seite um sie herum (Regel 6 in `docs/design/ui-scaling.md`).

### Oberfläche in der Pilotensitzung: Telemetrie, Plakette, Vorschau (sechste Gegenprüfung, Fixes vom 10.10.)

Die sechste Gegenprüfung öffnete den Konfigurator, während eine Drohne geflogen wird – ein Zustand, den `ui-scale:`
nie geöffnet hatte – und fand dort drei Befunde. Behoben in einem Durchgang:

- **Live-Telemetrie im Tab „Übersicht“:** Passte ein Wert nicht neben seine Beschriftung, brach er in die nächste
  Zeile um, ohne dass die folgende Zeile nachrückte. Auf Englisch lag der Akku-Wert bei 367–390 Punkten Breite über
  „Consumption“ (384 × 240 Punkte: 1920 × 1200 mit automatischer Skala; ein MacBook Pro im Vollbild), auf Deutsch bei
  367 Punkten; der umbrochene Wert der letzten Zeile hing über den Rand des Panels. Die Zeilen bekommen jetzt so
  viel Breite, wie die breiteste braucht; bleiben den Motorbalken daneben weniger als 64 Punkte, stehen sie unter
  den Zeilen. Bricht selbst dort eine Zeile um, wächst das Panel mit. Der Abstand zwischen Werten und Balken ist 10
  statt 6 Punkte. Neu in der 0.5.0; die 0.4.0 kürzte den Wert mit „…“.
- **Kopfzeile mit „ARMEN GESPERRT“:** Die Plakette steht in jeder Pilotensitzung, die Aufteilung der Kopfzeile
  rechnete aber ohne sie. In einem Band von Breiten nahm sie dem Drohnennamen den Platz („Dro…“ in jedem Tab bei
  450–460 Punkten mit Tastatur – 1366 × 768 mit automatischer Skala –, bei 512 Punkten mit einer „Radiomaster
  Boxer“), knapp darunter fiel sie ohne Hinweis weg (427 × 240 Punkte, das Standardfenster und 720p mit
  automatischer Skala; 480 × 270 Punkte mit einer „Radiomaster Boxer“). Sie zählt jetzt wie die Plakette „LIVE“ mit
  und wird immer gezeichnet; die Kopfzeile nimmt dafür früher eine zweite oder dritte Zeile. Die Regel gab es schon
  in der 0.4.0.
- **Vorschau „So zerfällt das Bild“ im Tab „Video“:** Bei 320 Punkten Breite stand auf Deutsch „KEIN …“ im letzten
  Bild, und zwei Beschriftungen brachen mitten im Wort um („4 · Standbil / der“, „5 · Verlore / n“). Die fünf Bilder
  stehen jetzt in Reihen zu drei und zwei, sobald eine Beschriftung oder „KEIN VIDEO“ sonst nicht passt.
- **Prüfung selbst:** Vier neue Fälle öffnen den Konfigurator in einer Pilotensitzung (FPV mit digitaler und mit
  analoger Brille, FPV mit „Radiomaster Boxer“, Sichtflug mit langer Funke) und verlangen die beiden Plaketten, die
  fünf Telemetrie-Zeilen und die Beschriftungen der Vorschau ausdrücklich – ein weggelassener Text hat kein
  Rechteck, das sich messen ließe. Zwei Fenster kamen dazu (384 × 240 und 456 × 256 Punkte, zwischen den bisherigen
  Größen; dort laufen die 38 Ansichten mit Funke und Pilotensitzung); 202 statt 194 Ansichten je Größe und Sprache. Gegen den Stand vor den Fixes meldet die erweiterte Prüfung
  alle drei Befunde (`TEXT_OVERLAP` bei 384 × 240, `CUT` „Dro…“ bei 456 × 256, `HIDDEN` für die fehlende Plakette bei
  427 × 240 und 320 × 240, `CUT` „KEIN …“ bei 320 × 240 auf Deutsch), danach in zwanzig Fenstergrößen auf Englisch und
  Deutsch keinen (`docs/design/ui-scaling.md`).
- **Selbsttest „audio: Motor-Stream startet und stoppt“:** Die Testdrohne ohne Pilot wurde im ersten Server-Tick
  entschärft; der Schritt bestand nur, wenn der Client sie vorher einmal scharf gesehen hatte, und scheiterte unter
  Last einmal mit „Stimme=NONE“. Der Test hält sie jetzt am Ende jedes Server-Ticks scharf, bis der Stream bestätigt
  und `/stopsound` geprüft ist. Kein Fehler im Spiel.
- **Doku:** `docs/design/world-rules.md` nannte bei den Voreinstellungen noch 22 bzw. 26 Regeln; der Katalog hat 28.

### Oberfläche: Rückfrage im Assistenten, leere Bestenliste, Funke anstecken (fünfte Gegenprüfung, Fixes vom 10.10.)

Die fünfte Gegenprüfung fand keine Befunde mehr in den Ansichten, die `ui-scale:` öffnet, aber drei in Zuständen,
die die Prüfung nie geöffnet hatte. Behoben in einem Durchgang:

- **Empfänger-Assistent, „Fertig“ ohne Arm-Schalter:** Die Rückfrage ist jetzt eine eigene Ansicht der Schalter-Seite
  statt einer Meldungszeile: Überschrift, darunter der umbrochene Text (nur die Taste zum Scharfschalten bleibt) und
  die Knöpfe „Zurück“ (zu den Schaltern), „Abbrechen“ und „Trotzdem fertig“. Vorher stand der Hinweis als eine
  einzige Zeile über der Fußzeile, auf Deutsch 596 Punkte breit – abgeschnitten bei jeder GUI schmaler als 596
  Punkte, also auch im Standardfenster und bei 1080p mit automatischer Skala –, und verschwand nach vier Sekunden
  samt der Aufforderung, noch einmal zu klicken. Betroffen war jede Funke mit höchstens vier Achsen, die ohne
  Arm-Schalter startet.
- **Meldungszeile des Assistenten:** Sie bleibt in der Fensterbreite (erst gestaucht, zuletzt gekürzt), statt über
  den Rand zu laufen; die Prüfung meldet eine Kürzung dann als `CUT`.
- **Leere Bestenliste eines neuen Layouts:** Der Hinweis steht in zwei umbrochenen Zeilen („Noch keine Rundenzeit
  auf Layout 3“ und darunter „Ältere Zeiten: /propwash race archive 44“). Vorher lief er als eine Zeile über den
  Fensterrahmen – auf Deutsch bis 400 Punkte Breite, auf Englisch bei 320 –, und die Kursnummer am Ende des Befehls
  fehlte. Die übrigen leeren Zustände (lädt, kein Kurs, noch keine Zeit) brechen ebenfalls um, „Noch keine
  Rundenzeit ohne Flughilfe“ bleibt in der Tabellenbreite.
- **Kopfzeile des Konfigurators beim Anstecken:** Kommt eine Funke dazu, geht eine verloren oder wechselt die Drohne
  in der Hand, während der Konfigurator offen ist, teilt sich die Kopfzeile neu auf (wie nach einer Größenänderung
  des Fensters). Vorher blieb die Aufteilung vom Öffnen stehen und der Name wurde in den Platz für „Tastatur“
  gequetscht („Team-Bl…“, „Radiomas…“ bei 480 × 270 Punkten auf Deutsch mit Drohne in der Hand).
- **Prüfung selbst:** Neue Fälle für genau diese Zustände – Funke nach dem Öffnen angesteckt (drei Fälle, je drei
  Tabs), Rückfrage und längste Meldung des Assistenten, vier leere Zustände der Bestenliste –, dazu acht Zustände
  der ffmpeg-Prüfung im Tab „Action-Cam“ (bisher nur von Hand eingespielt); 194 statt 171 Ansichten je Größe und
  Sprache (`docs/design/ui-scaling.md`).

### Oberfläche: Bestenliste, Listen und kleine Fenster (vierte Gegenprüfung, Fixes vom 10.10.)

Ein Prüfer hat die Oberfläche bei 21 Größen in beiden Sprachen nachgemessen, diesmal mit eigenen Testdaten (gefüllte
Bestenliste, Kurs mit 14 Gates, lange Gerätenamen). Behoben in einem Durchgang:

- **Bestenliste scrollt:** Die Tabelle zwischen Spaltenköpfen und Fußzeile ist ein scrollender Bereich (Mausrad,
  Scrollleiste zum Anfassen, Bild auf/ab, Pos1/Ende). Vorher fielen Zeilen stumm weg, sobald die GUI niedriger als
  etwa 384 Punkte war – also bei jedem 16:9-Bildschirm mit automatischer GUI-Skala und im Standardfenster: bei 240
  Punkten Höhe standen die Plätze 1–8 da, bei 270 (1080p, Skala 4) die Plätze 1–10 ohne die eigene Zeile und ohne
  den Abschnitt „Mit Flughilfe“.
- **Bestenliste, schmale Fenster:** Die Spalten richten sich nach dem längsten Namen der gezeigten Zeilen. Würde er
  gekürzt (unter 70 % seiner Breite), fallen der Reihe nach Datum, Klasse und Abstand weg; der Tooltip der Zeile
  nennt, was fehlt. Bei 320 Punkten Breite war die Namensspalte 8 Punkte breit, jeder Name leer, und im Abschnitt
  „Mit Flughilfe“ lag die Marke über der Rundenzeit.
- **Bestenliste, Klassenfilter:** Filter, die nicht in eine Zeile passen, laufen in eine zweite; reicht auch die
  nicht, blättert der Knopf „+N“ durch die übrigen. Vorher fielen sie ohne Hinweis weg – mit allen fünf Klassen war
  „Long Range 7″“ bei keiner Fenstergröße wählbar, bei 320 Punkten Breite standen zwei von fünf da.
- **Tab-Leiste des Konfigurators und Gate-Liste in „Kurs bearbeiten“:** Die Scrollleiste lässt sich anfassen und
  ziehen, ein Klick in ihre Bahn springt dorthin, und Bild auf/ab sowie Pos1/Ende bewegen die Liste (in der Tab-Leiste,
  solange die Maus darüber steht oder ein Tab den Fokus hat). Vorher ging dort nur das Mausrad.
- **OSD-Tab, eigene Farben:** Die Hex-Felder für Text- und Warnfarbe gibt es bei jeder Größe; in schmalen Tabs steht
  die Farbzeile in zwei Spalten, darunter untereinander. Vorher fehlten sie bis 640 × 360 Punkte (1080p mit Skala 3
  und 4) ohne Hinweis.
- **Kopfzeile des Konfigurators:** Ein Funkenname, der neben Drohnenname und Zustand gekürzt würde, bekommt eine
  dritte Zeile („Team-BlackSheep TBS Tango 2 Pro“ auf Deutsch bei 320 Punkten Breite mit Drohne in der Hand).
- **Lackierung:** Der Hinweis unter der Vorschau bricht um statt mit „…“ zu enden (Fenster 800 × 600).
- **Wartungsmenü:** „Props im Bild“ ragt bei GUI-Skala 2 nicht mehr in die Knöpfe darunter.
- **Prüfung selbst:** `ui-scale:` blättert jetzt auch durch Ansichten, in denen nichts angeschnitten ist (die Kante
  fällt zwischen zwei Zeilen), und prüft jede Stellung; neu sind die Fälle gefüllte Bestenliste, Kurs mit 14 Gates, Drohne mit langer Funke oder Maus,
  die Seite „Optionen“ des OSD-Tabs, Welt-Regeln ohne Schreibrecht und das Fenster 800 × 600. Stellen, die selbst kürzen
  (Werkbank, Wartungsmenü, Tab „Rennen“, Beitritts-Prüfung), melden das der Prüfung (`docs/design/ui-scaling.md`).

Bewusst so gelassen: Bei 320 Punkten Breite zeigt die Seite „Kanäle“ des Empfänger-Assistenten nur die Achsen A1–A4,
„Darstellung“ keine Drohnen-Vorschau, „Kurs bearbeiten“ weder Ersteller noch Gate-Koordinaten, und schmale OSD-Knöpfe
tragen Kurzbeschriftungen. Pfeiltasten wechseln wie in Minecraft den Fokus und scrollen nicht.

### Oberfläche bei großer GUI-Skala (Fix vom 09.10.)
- Auslöser: Im Tab „Action-Cam“ endete der Statustext bei großer GUI-Skala mitten im Satz – der Tab hatte keinen
  Bildlauf, alles unter der Ansicht war abgeschnitten.
- Der Konfigurator verschiebt jetzt jeden Tab, dessen Inhalt höher ist als die Ansicht: Scrollleiste am rechten Rand,
  Mausrad, Bild auf/ab, Pos1/Ende; mit der Tabulatortaste angesprungene Felder rücken in die Ansicht. Betroffen waren
  Action-Cam, Audio, Darstellung, Video, OSD, Rennen, Modi, Empfänger und Server; Tabs mit eigenem Bildlauf bleiben,
  wie sie waren.
- Fußzeile: Der Status bricht um, die Fußzeile wird dafür höher (427 Punkte Breite: zwei Zeilen in 24 Punkten).
  Kopfzeile: zweite Zeile, wenn Name, Zustand, Funke und Suche nicht nebeneinander passen. Seitenleiste: so breit wie
  der längste Tab-Name, scrollt bei zu vielen Tabs.
- Zwei Spalten erst ab 386 Punkten Tab-Breite (bisher 306): Bei 1080p mit GUI-Skala 4 und im Standardfenster stehen
  die Einstellungen in einer Spalte. Die Beschriftungsspalte wächst mit ihrem Text; einzeilige Texte werden bis auf
  70 % gestaucht, bevor sie mit „…“ enden.
- Übersicht: Zeilen brechen in Beschriftung und Wert um, zwei Spalten nur, wenn jede Zeile hineinpasst. Flugschule:
  Karten wachsen mit Titel und Beschreibung (bisher endete die Beschreibung nach zwei Zeilen), der Tipp in der Fußzeile
  bricht um. Flugtraining: Kapitel, Lektionen und Beschreibung scrollen einzeln, Namen brechen um; unter 520 Punkten
  Breite stehen die Lektionen eingerückt unter ihrem Kapitel.
- Tab „Server“ zeichnet alle Zeilen (bisher fielen die untersten weg), Tab „Rennen“ zeigt die ganze Erklärung statt
  einer gekürzten mit Tooltip, der Hinweis „Trails in dieser Welt aus“ und die Hinweise im OSD-Editor brechen um.
- Meldungen (Toasts) werden nie breiter als der Bildschirm.
- Schmale Fenster (320 Punkte Breite, etwa ein halbes 1080p-Bild mit GUI-Skala 3): Modi-Liste stellt die Wirkung unter
  den Namen, Quelle und „Zurück auf abgeleitet“ im PID-Tab sowie die Vorlagenzeile (Auswahl, Name, Knöpfe) brechen um,
  die Lackierung zeigt die Vorschau unter den Listen und bricht Vorlagen-Knöpfe und Status um. Empfänger-Assistent:
  Gerätezeile bricht um, Schalterzeilen haben einen schmaleren „Zuweisen“-Knopf und stauchen die Belegung.
  Beitritts-Prüfung: lange Add-on-Namen brechen um, die Kennung steht darunter. Flugschul-Szenen lassen „Flugschule“
  im Kopf weg, wenn der Titel den Platz braucht. „KEIN VIDEO“ in kleinen Video-Vorschauen wird gestaucht.
- Werkbank „Mehr“ (Add-on-Einträge) scrollt und wird so breit wie Titel und längster Eintrag. Die Elementliste im
  OSD-Editor gibt das Mausrad an ihrem Ende an den Tab weiter. Die Flugtraining-Anzeige bricht lange Zeilen um.
- Stand der Prüfung: 0 Befunde in je 90 Ansichten bei allen fünf verschiedenen GUI-Größen (427 × 240, 960 × 540,
  640 × 360, 480 × 270, 320 × 240), Fabric und NeoForge. Just More Parts 1.1.0: Sharecode-Fenster scrollt Code und
  Werte statt sie abzuschneiden.
- Prüfung: Selbsttest `ui-scale:` (alle Tabs und Screens bei 854 × 480, 1920 × 1080 und 640 × 480 mit GUI-Skala 2, 3,
  4 und automatisch), Regeln und Bedienung in [`docs/design/ui-scaling.md`](design/ui-scaling.md).

### Welt-Regeln
- 28 Gamerules `propwash:…` (16 aus diesem Abschnitt, `drone_hits_entities` aus „Drohnen treffen Lebewesen“, `part_compatibility` aus „Teile müssen zusammenpassen“, `allow_3d_mode` aus „3D-Modus“, `autonomous_rescue` aus „GPS Rescue ohne Pilot“, `freeform_gate_max_size` aus „Freiform-Gate“, `signal_repeaters` aus „Signal-Repeater und Kreativ-Brille in Rennen“, `drones_generate_chunks` und `save_assisted_laps` aus „Drohnen und neue Chunks, Runden mit Flughilfe“, `drones_break_blocks` und `break_impact_scale` aus „Drohnen zerbrechen Blöcke“, `drone_trails` aus „Drohnen-Trails“ und `invulnerable_pilots` aus „Pilotenkörper bleibt verwundbar“) gelten für alle Spieler einer Welt und werden mit ihr gespeichert (Tabelle, Bereiche und
  Entscheidungen in [`design/world-rules.md`](design/world-rules.md)). Mit allen Standardwerten fliegen Drohnen wie in
  0.4.0 bis auf die Änderungen am Flugmodell aus „Realismus-Feinschliff“ (Steigflug-Schub und Sinkflug-Widerstand für
  alle Größen, Draufsicht des Whoop-Rahmens); anders als in 0.4.0 prallen
  Drohnen aber an Lebewesen ab (`drone_hits_entities`, Standard an).
- Schwerkraft 0–1000 % in 1-%-Schritten (bis 08.10. 80–150 %, der geprüfte Bereich des alten Reglers), Akkulaufzeit 0–1000 % (der Verbrauch wird geteilt,
  mAh im OSD zählen mit; 0 = unendlich), Wind 0–3 als Faktor auf das wetterabhängige Windmodell (Sturm bei Gewitter etwa
  29 m/s in 10 m Höhe), Turbulenz (Böen und Rütteln im eigenen Abwind), Prop-Wash, Bodeneffekt, Spannungseinbruch,
  Hardware-Grenzen, Crash-Toleranz 50–400 %, Absturz entschärft, Propellertreffer.
- Crash-Schaden an/aus und Schadensstärke 25–300 % für alle; das ersetzt den Schalter und Regler der Kreativ-Brille und
  die Server-Regel `partWear`. Hitze- und ESC-Verschleiß hängen an derselben Regel.
- Unendliche Reichweite getrennt für analoges und digitales Video: Bild und Funk reißen nicht ab, auch hinter
  Hindernissen; die Entfernungsgrenze aus `server.json` gilt weiter. Eine Kreativ-Brille mit eigener Reichweite behält sie.
- Videokanäle: höchstens so viele gleichzeitige Sender (Drohne mit Pilot und geladenem Akku, auch im Sichtflug);
  danach lehnt der Server den Start ab, bis ein Pilot landet. 0 = unbegrenzt (Standard).
- Tab „Welt-Regeln“ (statt „Simulation“): Host im Einzelspieler oder LAN und Operatoren ändern, alle anderen sehen eine
  Tabelle; Änderungen prüft der Server und meldet sie allen Spielern im Chat. `/propwash rules` listet die Regeln mit.
- Abweichende Physik-Regeln zählen wie vorher veränderte persönliche Optionen: keine Bestenliste, kein Geist. Der Server
  prüft das zusätzlich selbst.
- Abgelehnte Änderungen oder Voreinstellungen eines Spielers ohne Recht melden Logzeile, rote Chatzeile und Neusendung
  der Regeln höchstens zweimal je 5 s, weitere verwirft der Server still (ein veränderter Client flutet so nicht das Log).
- Alte Werte: `drone.json` behält nur `stateSendHz`. `"partWear": false` in `server.json` übernimmt der erste
  Serverstart einmal als Welt-Regel `propwash:crash_damage false` (im Einzelspieler für die erste danach geöffnete Welt),
  danach fehlt der Schlüssel in der Datei.
- Voreinstellungen ganz oben im Tab: Realistisch (alle Standardwerte), Training (kein Crash-Schaden, keine
  Propellertreffer, unendlicher Akku ohne Spannungseinbruch, keine Hardware-Grenzen; Wind mild wie Standard) und Arcade
  (Training plus Teile frei, kein Wind, keine Turbulenz, kein Prop-Wash, Crash-Toleranz 300 % ohne Abschalten, unendliche
  Reichweite, Drohnen fliegen durch Lebewesen). Training ändert damit auch die Leistung über den Flug (kein
  Spannungseinbruch, keine Hitzedrosselung), Wind und Luft bleiben wie bei Realistisch; so sagt es auch der Text der
  Voreinstellung. Ein Klick gilt sofort für alle (Host oder Operator, eine Chatzeile),
  einzelne Regeln bleiben danach einstellbar, der Tab zeigt, welche Voreinstellung passt. Auch per
  `/propwash rules preset <name>`. Tabelle und Entscheidungen in `design/world-rules.md` §1a; Netzprotokoll bleibt 11.

### Drohnen und neue Chunks, Runden mit Flughilfe (Welt-Regeln vom 08.10.)
- `propwash:drones_generate_chunks` (Standard an = wie bisher): aus = keine Drohne erzeugt neue Chunks, auch nicht die
  des Hosts oder eines Operators (`server.json` beschränkt zusätzlich nur, wenn die Regel an ist). Das Sicht-Quadrat lädt
  dann alle schon erzeugten Chunks, die der Client bekommen kann, lückenlos über mehrere Tickets aus Quadraten
  (`GeneratedCover`) statt eines
  schrumpfenden Tickets; der Pilot bekommt 13 × 13 Chunks mit den fehlenden um die Drohne (`propwash:generation_edge`),
  und ein Grenzzaun am Client bremst mit 20 m/s² auf 4 Blöcke vor dem ersten gesperrten Chunk (fehlend oder ein
  erzeugter direkt daneben, den der Server ohne Erzeugung nie senden kann; quer gleiten frei,
  zurückschieben bis 3 m/s, OSD „WELTRAND“, einmal ein Hinweis). Gelöst wird nur noch, wenn der Chunk der Drohne selbst
  fehlt. Entwurf: [`design/world-edge.md`](design/world-edge.md).
- `propwash:save_assisted_laps` (Standard aus = wie bisher, Training und Arcade an): Runden und Rennen mit Flughilfe
  in einer eigenen Wertung „Mit Flughilfe“ (Gründe als Bitmaske in den Weltdaten, nie Rekord, keine Tafel, kein goldener
  Geist), im Fenster unter der Wertung mit Abzeichen „Flughilfe“ und Gründen im Tooltip, eigener violetter persönlicher Geist,
  „FLUGHILFE“ in der Rennanzeige. Der Server wertet jetzt je Runde statt je Rennen (eine saubere Runde nach einer mit
  Flughilfe zählt wieder). Alte Weltdaten laden unverändert. Entwurf: [`design/assisted-laps.md`](design/assisted-laps.md).
- Netzprotokoll bleibt 11 (neues Paket, Gründe im Rundenbericht in `flags`, `assist` je Zeile der Bestenliste).

### Drohnen-Trails (Welt-Regel vom 08.10.)
- `propwash:drone_trails` (Standard aus, Training und Arcade an, keine Physik): Jede scharfe, fliegende Drohne zieht eine
  dünne, verblassende Spur in ihrer Farbe (leuchtender LED-Streifen, sonst Lackierung, sonst eine feste Farbe aus der
  UUID), 1–2 s lang nach Tempo, im FPV-Bild für alle anderen Drohnen, die eigene nur im Sichtflug und in der dritten
  Person. Gelandet oder entschärft keine neue Spur.
- Persönlicher Schalter „Drohnen-Trails zeigen“ im Tab „Darstellung“ (`config/propwash/trails.json`, gesperrt mit Hinweis
  bei Regel aus), Taste „Drohnen-Trails ein/aus“ ohne Standardbelegung, Suchbegriffe Trail, Spur, Schweif.
- Zeichnen über den Extract/Submit-Weg (`ClientBus.SUBMIT_WORLD_GEOMETRY`, eigene Pipeline `drone_trail`: in Vanilla
  wie `RenderTypes.eyes`, unter Iris dem Programm `ENTITIES_TRANSLUCENT` zugeordnet, damit der dunkle Halo bei Tag
  bleibt), nie im Schattenpass von Iris, ohne Allokation je Bild (Ringpuffer, höchstens 64 Trails). Die Proben altern
  auf einer eigenen Uhr aus Client-Ticks, nicht auf der Spielzeit, die der Server alle 20 Ticks zurücksetzen kann. Action-Cam-Aufnahmen zeigen die Trails wie das Bild
  des Aufnehmenden. Entwurf: [`design/drone-trails.md`](design/drone-trails.md), für Spieler:
  [`drone-trails.md`](drone-trails.md).
- Netzprotokoll unverändert (die Regel reist in `propwash:world_rules`), keine API-Änderung.

### Bodenkontakt: nichts versinkt mehr im Boden (Fix vom 09.10.)
- Ursache: feste, aufrechte Kollisionsbox – geneigt am Boden ragte die tiefe Seite des Modells durch die Fläche (5″ bei
  36° 54 mm, auf der Seite 10 cm), und eine gecrashte Drohne schlief mit bis zu 11,5° Schräglage ein.
- Renderer: Das Modell steht am Boden auf seinem tiefsten Punkt (`GroundContact`, stufenlos mit dem Abstand zur Fläche
  unter der Box; kopfüber schwebende Modelle setzen auf). Nur eine Verschiebung des Modells: Licht, runder Schatten und
  Iris-Schattenpass bleiben am Entity, keine eigene Render-Schicht.
- Flugmodell: unscharf am Boden fällt die Drohne ab 3,4° Schräglage mit dem vollen Hebel der Aufstandskante flach
  (`GroundRest`, `SimTuning.restFlat`). Flug, Start und alles mit scharfen Motoren bit-gleich: `builtin_regression.txt`
  unverändert (gerechnet ohne die Regel), neue Golden-Datei `builtin_ground_rest.txt` für die Flüge mit ihr.
- Lose Drohnen (ohne Piloten) kippen am Boden flach (`GroundSettle`), der Kurs bleibt.
- Block-Tag `propwash:drone_surface` (Schnee, Seelensand, Schlamm, Honig): Drohnen liegen auf der sichtbaren
  Oberfläche statt 6 bis 12,5 cm tief im Block und bleiben anklickbar; Datenpakete können das Tag ändern. Auf solchen
  Flächen und auf Gate-Balken gilt eine lose Drohne jetzt als am Boden.
- Netzprotokoll und API unverändert. Entwurf, Messwerte und Grenzen: [`design/ground-contact.md`](design/ground-contact.md).

### Pilotenkörper bleibt verwundbar (Fix vom 09.10.)
- Wer steuert, bleibt mit seinem Körper ein normaler Spieler: der Client friert den Körper nicht mehr ein, leert nur seine
  Eingaben und meldet die Lage auch ohne Kamera an den Server. Schwerkraft, Fallschaden, Rückstoß, Strömung, Mobs, PvP,
  Lava, Ertrinken und Hunger wirken wie sonst; kein Kick wegen Schwebens auf dedizierten Servern.
- Start mitten in der Luft erlaubt (der Körper fällt), verweigert nur im Gleitflug mit Elytren.
- Treffer im FPV: roter Rand, Treffer-Ton, Karte „verletzt“, „unter Wasser“ (mit Luft) oder dauerhaft „schwer verletzt“
  ab 30 % Gesundheit; kein Kamerawackeln, kein Auswurf aus dem FPV.
- Tod: zählt wie ein Funkverlust – Failsafe „Heimflug“ übergibt die Drohne an GPS Rescue ohne Pilot, sonst entschärft;
  der Server wartet höchstens 20 Ticks auf den Client.
- Welt-Regel `propwash:invulnerable_pilots` (Standard aus, in keiner Voreinstellung an, keine Physik): Piloten nehmen
  keinen Schaden außer durch die Leere und `/kill`. Netzprotokoll und API unverändert. Entwurf:
  [`design/pilot-body.md`](design/pilot-body.md), für Spieler: [`pilot-body.md`](pilot-body.md).

### GPS Rescue ohne Pilot und bessere Hinderniserkennung
- Steigt der Pilot im laufenden Heimflug aus – Funke benutzen (Flug beenden), Funke weglegen, abmelden, Dimension
  wechseln, sterben oder die Reichweite des Servers verlassen –, fliegt der Server die Drohne mit demselben Flugmodell und
  Autopiloten weiter und landet sie am Homepoint (ohne Pilot immer, auch mit „Automatisch landen“ aus). Der Besitzer
  bekommt Chat-Meldungen beim Start und nach der Landung. Die Brille abzusetzen wechselt wie bisher nur auf Sichtflug.
- Funke benutzen, während sie allein fliegt: sie landet sofort an Ort und Stelle; Shift + Funke zeigt die Entfernung zum
  Homepoint.
- Chunks: Der Server hält nur die Chunks um die Drohne geladen (Ticket wie beim Piloten, Radius 2) und fliegt nie in
  unerforschtes Gebiet; entlädt der Chunk trotzdem, pausiert der Flug und geht beim nächsten Laden weiter (Plan in den
  Entity-Daten). Höchstens 8 Flüge je Server und 2 je Spieler gleichzeitig, sonst sinkt die Drohne senkrecht zu Boden;
  nach 10 Minuten landet sie an Ort und Stelle.
- Der Homepoint muss in der Funkreichweite des Piloten liegen; ist er mehr als 1500 m weg, landet die Drohne sofort.
- Welt-Regel `propwash:autonomous_rescue` (Standard an, Gruppe „Spiel“): aus = Motoren aus wie bisher.
- Hinderniserkennung (auch mit Pilot): Sicherheitshöhe gleich aus dem ganzen Weg (bis 256 m), lokaler Wegplaner um
  Bäume und Gebäude und aus Hallen durch Türen (A* auf dem Blockraster, höchstens 2500 Knoten), Landeplatz nur auf
  festem, ebenem Grund (kein Wasser, keine Lava, kein Laub, keine Gefahrenblöcke; bis 12 m um den Homepoint, bei einem
  Homepoint auf der Baumkrone der Boden darunter), nach drei Versuchen ohne Fortschritt schweben (Meldung „findet keinen
  Weg“) statt endlos neu zu suchen.
- Netzwerkprotokoll 11 (neues Paket `propwash:rescue_status`). Entwurf: [`design/autonomous-rescue.md`](design/autonomous-rescue.md).

### Teile müssen zusammenpassen
- Welt-Regel `propwash:part_compatibility` (Standard an, Gruppe „Spiel“). Aus: Werkbank, Import einer neuen Drohne,
  Akku-Schnelltausch, Wartungsmenü, Kreativ-Tab-Presets von Addons und die API lehnen keine Kombination mehr ab – falsche
  Motor-, Prop- und Stack-Halterung, zu große (überlappende) oder zu kleine Props, Akku außerhalb der Rahmenklasse
  (Zellen, Gewicht) und Zubehör ohne passende Halterung am Rahmen.
- Es bleibt, was keine Kompatibilität ist: Teile aus nicht installierten Addons, die Kategorie je Slot, die Halterung des
  Zubehörs (Action-Cam oben, LED-Streifen und Greifer unten), je vier gleiche Motoren und Props, die Brille zum
  Videosystem, Greifer-Tragkraft und Kanal-Grenze.
- Aufgehobene Probleme bleiben sichtbar: in der Werkbank gelb unter „Welt-Regel erlaubt:“, im Konfigurator gelb mit
  „(in echt nicht baubar)“, im Tooltip der Drohne („Passt in echt nicht (Welt-Regel)“, mit Shift die Probleme) und im
  Wartungsmenü. Die Drohne fliegt mit den echten Daten ihrer Teile (13″-Props am Whoop drehen kaum an,
  2807-Motoren mit 7″-Props und 6S machen einen Whoop-Rahmen zur Rakete); gültige Drohnen fliegen bitgleich.
- Überspannung bleibt bei `propwash:hardware_limits`: Ein 6S-Akku am 1S-Whoop-Board lässt mit Hardware-Grenzen den ESC
  durchbrennen, ohne sie fliegt er mit sechs Zellen.
- Der Server entscheidet (Werkbank-Ergebnis, Import, Akku-Tausch); ist die Regel an, lehnt er den Start einer Drohne ab,
  deren Teile nicht zusammenpassen („Die Teile dieser Drohne passen nicht zusammen …“). Wer schon fliegt, fliegt weiter,
  wenn die Regel angeht, kann nach dem Entschärfen aber nicht wieder scharf schalten. Flüge mit Drohnen, die es nur mit
  abgeschalteter Regel gibt, zählen nicht für Bestenlisten, Geister und Flugtraining-Bestzeiten. Einzelheiten:
  [`design/part-freedom.md`](design/part-freedom.md).
- Behoben: Das Wartungsmenü prüfte beim Prop-Tausch nur, ob der Prop zu groß ist. Mit Addon-Teilen ließen sich so Props
  einsetzen, die zu klein für den Rahmen sind oder nicht auf die Motorwelle passen; die Drohne war danach nicht mehr
  flugfähig. Bei Regel an lehnt es jetzt alle drei Fälle mit eigenem Hinweis ab, bei Regel aus passt jeder Prop.

### Lizenz und öffentlicher Quellcode
- Ab 0.5.0 erscheint der Quellcode öffentlich auf GitHub, als Stand je Release ohne die interne Entwicklungsgeschichte.
  0.4.0 und alle älteren Versionen bleiben „All Rights Reserved“.
- Kern (Code, Assets, Daten, Build-Skripte, Werkzeuge): PolyForm Shield License 1.0.0 wie bei Sodium, mit der
  Pflichtzeile `Required Notice: Copyright (c) 2026 lardtom` (`LICENSE`, `NOTICE`). Erlaubt: Code lesen und prüfen,
  selbst bauen, in Modpacks aufnehmen, per Pull Request beitragen. Nicht erlaubt: Neu-Uploads, Forks als alternative
  Downloads, konkurrierende Mods aus der Codebasis.
- Zusatzrechte in `LICENSE`: Modpacks dürfen die unveränderten Release-Dateien enthalten und von Modpack-Plattformen
  ausgeliefert werden (gilt ausdrücklich nicht als Konkurrenzprodukt). Ausstiegsklausel: Erscheint zwölf Monate am
  Stück kein neues Release und gibt es im offiziellen Repository keine Aktivität der Maintainer, steht automatisch jede
  bis dahin veröffentlichte Version zusätzlich unter der MIT-Lizenz, unwiderruflich.
- MIT-Lizenz (`LICENSE-MIT` mit Pfadliste): die API `de.lardtom.propwash.api`, das Beispiel-Addon (`dev.example`,
  Ressourcen `propwash_example`), die Doku unter `docs/` (ohne die Bilder in `docs/screenshots/` und
  `docs/asset-sheets/`), `README.md`, `CHANGELOG.md` und `CONTRIBUTING.md`.
- Mod-Metadaten: `LicenseRef-PolyForm-Shield-1.0.0 AND MIT` in `fabric.mod.json` und `neoforge.mods.toml` (Mod-Liste
  von NeoForge und Mod Menu zeigen das an); jedes Jar enthält `LICENSE`, `LICENSE-MIT` und `NOTICE`.
- Beiträge: `CONTRIBUTING.md` (Bauen, Testen, Regeln) und `CLA.md` (kurzer Beitragsvertrag: Beitragende behalten ihr
  Urheberrecht und geben lardtom das Recht, Beiträge zu nutzen und neu zu lizenzieren), Pull-Request-Vorlage mit
  Häkchen für den Vertrag.

### Freestyle-Tricks: Erkennung und Repertoire
Entwurf und alle Schwellen: [`design/freestyle-tricks.md`](design/freestyle-tricks.md). Das Flugmodell ist unverändert.
- Neue Erkennung: Jedes Manöver wird als Fenster mitgeschrieben, ohne vorher oder nachher „ruhig“ sein zu müssen; danach
  zerlegt sie es in Drehstriche und sucht die Aufteilung mit den meisten Punkten. Dadurch zählen Tricks aus schnellem Flug
  mit 70–80° Neigung, Flips mit Roll- oder Gier-Anteil, diagonale und langsame Flips (ab 150 °/s), etwas zu kurz gedrehte
  (ab 250°) und solche, die in einen Sturz übergehen. Direkt aneinandergereihte Tricks (Flip in Rolle, Split-S in Power
  Loop) werden einzeln gezählt, kein Strich zählt doppelt.
- Ursachen der alten Fehlerkennungen: Ruhe hieß höchstens 66° Neigung und Roll-/Nickrate unter 120 °/s für 0,1 s – im
  schnellen Flug nie erreicht, das Manöver lief bis zur 8-s-Grenze. Aneinandergereihte Tricks landeten in einer Episode,
  und die mittlere Drehrate über An- und Auslauf sowie eine enge Grenze für die zweite Achse verwarfen unsaubere Flips.
- Zehn neue Tricks: Juicy Flick (halber Flip vorwärts, halbe Rolle), Rubik's Cube (halbe Flips und Rollen mit wechselnden
  Achsen), Trippy Spin (Yaw-Spin kopfüber im Fallen oder um einen Punkt), McTwist (halber Flip, Spin kopfüber, Flip zu
  Ende), Korkenzieher (langsame Rolle mit Gas im Weiterflug), Rewind (über Kopf und denselben Weg zurück), Knife Edge (auf
  der Seite die Linie halten), Yaw-Spin kopfüber, Snap Roll (Knüppel in die Ecke) und Immelmann (halber Looping nach oben,
  halbe Rolle). Matty Flips zählen jetzt vorwärts und rückwärts.
- Ketten: Ein Trick, der höchstens 0,5 s nach dem vorigen beginnt, bekommt +25 % und hängt sich an die sichtbare
  OSD-Meldung an (bis zu drei Namen, Punkte als Summe).
- Tab „Freestyle“: alle Tricks mit Punkten in bis zu drei Spalten, scrollbar, Erklärung als Tooltip; Flugschul-Lektion
  „Freestyle-Tricks“ mit sieben Schritten (Familien, Suche und Filter siehe „Trick-Katalog“).
- Robustheit: Ein Juicy Flick braucht zusammen mindestens 300° Nick und Rolle (ein Nick von 112° aus der Schräglage mit
  einer Rolle zum Aufrichten zählt nicht mehr), ein Rubik's Cube darf mit einem kurzen Strich zum Aufrichten enden, ein
  Matty Flip hat höchstens 450° Nick, mehrere Power Loops hintereinander bleiben Power Loops. Geprüft mit 40 weiteren
  Zufalls-Seeds je Trick-Familie (`TrickRobustnessTest`).
- Geändert: Eine halbe Rolle hin und zurück ist jetzt ein Rewind (150 Punkte) statt nichts. Die Erkennung läuft rein
  clientseitig ohne Netzwerk; die Parts-API steigt auf 1.5.0, weil `FlightSchoolScenes.TRICKS` die neue Lektion nennt
  ([`api/changes.md`](api/changes.md)).

### Profi-Tricks: Sbang, Dex-Style und 3D

Auf Wunsch aus der Community (Rückmeldung eines Creators, der Piloten coacht) erkennt Propwash Tricks der neuen Schule. Begriffe,
Quellen, prüfbare Definitionen und offene Fragen an den Creator stehen in `docs/design/freestyle-tricks.md` („Neue Schule“).

- Eject (Sbang-Grundbewegung: kippen, Vollgas, zurückschnappen) als eigener Zustandsautomat; Sbang-Kombo als Bonus für eine
  Linie aus Eject und zwei Wendern mit höchstens 0,8 s Pause.
- Dex-Style: Funky Split (halbe Flachdrehung kopfüber statt halbem Looping), Shinobi Flip (Split-S oder Funky Split, Rubik's
  Cube, Flip hinaus; Bonus), Guillotine (Flip am Ende eines ruhigen Sturzflugs, ersetzt Flip und Sturzflug, ab 8 m
  Fallhöhe), Side Trippy (schnelle Runde auf der Seite um einen Punkt).
- 3D-Modus: Rückenflug mit Umkehrschub ohne zu fallen (ersetzt dort das Kopfüber-Hängen) und Tic-Toc (≥ 4 halbe Flips im
  Wechsel); zählen nur bei aktivem 3D-Modus (`TrickSample.mode3d` aus der Telemetrie).
- Kombi-Tricks (`LineRecognizer`) erhöhen weder Kombo- noch Kettenzähler; ihr Name hängt sich an die OSD-Meldung an.
- Rubik's Cube zählt keine Kurve auf der Seite mehr (enge Kurve mit Ein- und Ausrollen).
- Flugschule: Schritt „Neue Schule: Dex, Sbang und 3D“ mit einer Guillotine im Diorama.
- Tests: `TrickProTricksTest` (Referenz-Flugbahnen sauber, unsauber, weiche Knüppel, schwankende Bildrate, Whoop, mit
  Gegenproben), je 40 Seeds in `TrickRobustnessTest`, Gegenprobe über 9650 Fälle ohne Abweichung; Selbsttest „tricks: Neue
  Schule in der Simulation“, Bildschirmfotos `trick_guillotine_fpv` und `trick_sbang_fpv`.
- Flugmodell, Netzprotokoll und Parts-API unverändert.

### Trick-Katalog: 59 Tricks in neun Familien

Wunsch des Besitzers: viele bekannte Tricks samt Varianten. Recherche in Trick-Listen (Pro Whooper, WREKD, Rotor Riot) und im
Kunstflug, Quellen, Definitionen, abgelehnte Kandidaten und offene Fragen an den Creator: `docs/design/freestyle-tricks.md`
(„Trick-Katalog“).

- 25 neue Tricks und Varianten: Anti Matty (Matty rückwärts), Half Matty, Power Matty, 540-Split-S, Splitback, Flip-Rewind,
  Segmentierter Flip, Vanny Roll, Coin Drop, Tornado (Rolle im Sturz), Rückwärts-Sturzflug, Hammerhead, Maverick Loop, Power
  Flip (auch Reversed), Power Roll, Inverted 360 Power Loop, Side Loop, Barani, Immelmatt (Kombi), Wall Ride, Reverse Knife Edge;
  im 3D-Modus Schweben kopfüber, 3D-Flip, 3D-Rolle und Power Loop (3D).
- Neun Familien (`TrickFamily`), Varianten zeigen auf ihren Grundtrick (`TrickType.variantOf()`); das Flugtraining zählt
  Varianten für den Grundtrick (3D-Flip als Flip, Power Flip als Power Loop, 540-Split-S als Split-S).
- Geändert: Matty Flip zählt nur noch vorwärts, rückwärts heißt er Anti Matty (375 statt 350 Punkte); ein ganzer Flip hin und
  zurück ist ein Flip-Rewind (300) statt Rewind; ein Hänger innerhalb eines segmentierten Flips zählt nicht zusätzlich.
- Tab „Freestyle“ nach Familien gruppiert, mit Suche (Namen, Schlüssel und Szene-Namen wie Barrel Roll, Stall Turn, Gigatine)
  und Familienfilter; Tooltip mit Familie und Aliasen. OSD-Namen höchstens 18 Zeichen.
- Flugschul-Lektion „Freestyle-Tricks“ nennt Familien und Loop-Varianten (Texte, keine neuen Schritte).
- Tests: `TrickVariantsTest` (alle Varianten sauber, unsauber, weiche Knüppel, 30–144 Hz, mit Gegenproben), `TrickCatalogTest`
  (Familien, Suche, OSD-Namen), je 40 Seeds in `TrickRobustnessTest`; Gegenprobe über 11 250 Fälle: dieselben 4 Abweichungen
  wie vor dem Katalog, keine neue Fehlerkennung. Selbsttest prüft Familien, Suche und Filter, Bildschirmfoto
  `configurator_tricks_search`.
- Flugmodell, Netzprotokoll und Parts-API unverändert.

### Drohnen treffen Lebewesen
- Mobs und Spieler sind für die geflogene Drohne Hindernisse wie Blöcke: Sie prallt ab, ein harter Aufprall zählt als
  Absturz, Teile verschleißen, Props, die ein Lebewesen treffen, zählen als Prop-Strike. Der eigene Pilot, Zuschauer und
  Rüstungsständer sind ausgenommen; was die Drohne beim Start schon berührt, hält sie nicht fest.
- Schaden und Rückstoß aus Masse × Annäherungstempo: unter 2 m/s oder 0,4 kg·m/s kein Treffer, 0,4 HP je kg·m/s bis
  höchstens 10 HP (5 Herzen), Rückstoß 0,05 je kg·m/s (0,1–1,2; Faustschlag 0,4). Freestyle 5″ mit 20 m/s ≈ 4 HP,
  Vollgas ≈ 8 HP; ein Whoop prallt nur ab; Fracht im Greifer zählt mit.
- Spieler nur mit PvP (Spielregel `pvp`), Teams und Unverwundbarkeit wie bei einem Schlag des Piloten; getroffene Tiere
  fliehen, Kills zählen für den Piloten (Beute und Erfahrung). Todesmeldung „… wurde von der Drohne von … getroffen“.
- Welt-Regel `propwash:drone_hits_entities` (Standard an, Gruppe „Spiel“ im Tab „Welt-Regeln“), auch in `/gamerule` und im
  Spielregel-Fenster; aus = Drohnen fliegen wie in 0.4.0 durch Lebewesen. Treffer an Lebewesen verändern keine Blöcke
  (Glas zerbricht nur mit der Welt-Regel aus dem nächsten Abschnitt).
- Der Server prüft jeden Treffer (Pilot, Tempo-Grenze aus den letzten Zustandspaketen, Abstand, je Ziel alle 0,5 s,
  höchstens 8 je Sekunde). Einzelheiten: `docs/design/drone-interaction.md`.

### Drohnen zerbrechen Blöcke (Welt-Regel vom 08.10.)
- Welt-Regel `propwash:drones_break_blocks` (Standard aus, in allen Voreinstellungen aus, Gruppe „Spiel“): schnelle
  Drohnen zerbrechen Glas, getöntes und gefärbtes Glas, alle Glasscheiben und verzierte Töpfe (Block-Tag
  `propwash:drone_breakable`, dazu optional `#c:glass_blocks` und `#c:glass_panes`; nie über Härte 1, nie Befehlsblöcke).
- Bruch, wenn ½ · Masse · Tempo² entlang der Normalen die Schwelle erreicht: max(5 J, 400 J × Härte × (0,2 + 0,8 × Dicke)),
  also Scheibe frontal 36 J, Glasblock 120 J, Topf 5 J, und mindestens 3 m/s. Freestyle 5″ zerbricht eine Scheibe ab
  42 km/h und einen Glasblock ab 77 km/h, ein Cinewhoop eine Scheibe ab 57 km/h, ein Whoop nie.
- Die Drohne fliegt mit √(v² − 2·Schwelle/Masse) weiter; das verlorene Tempo zählt wie ein Aufprall (Piepen,
  Verschleiß, Absturz ab der Crash-Toleranz): 5″ mit Vollgas durch eine Scheibe −1,8 m/s, mit 30 m/s durch einen Glasblock
  −9,5 m/s und Absturz.
- Der Durchbruch ist zu spüren (09.10., `docs/design/drone-break-blocks.md` §3.2): nach der Bruchenergie teilen die
  mitgerissenen Scherben den Impuls (Scherbenmasse 10 kg/m² je 36 J Schwelle × getroffene Fläche × 0,15), der Stoß
  greift außermittig an und kippt die Drohne (Drehrate höchstens +12 rad/s; geneigt Nase nach unten, Glas nur auf einer
  Seite um die Hochachse), vordere offene Props verlieren 35 % Drehzahl (Prop-Strike). 5″ durch eine Scheibe bei
  90 km/h: −4,6 m/s, 4–10 rad/s, 4–10° Lagefehler; durch einen Glasblock bei 140 km/h: −14 m/s, 12 rad/s, kein
  Absturz. Absturz und Verschleiß hängen weiter nur am Bruchanteil. Welt-Regel `propwash:break_impact_scale`
  (0–300 %, Standard 100, in allen Voreinstellungen 100; 0 = Stand ohne Stoß).
- Nur beim Piloten: kurzer Kameraschlag im FPV-Bild (bis 3°, folgt „Kamera-Vibration“), 48 Scherben je Block, die in
  Flugrichtung mitfliegen, und das Bruchgeräusch ein zweites Mal an der Drohne. Kein neues Paket, Netzprotokoll und
  API_VERSION unverändert.
- Wie Abbauen durch den Piloten ohne Werkzeug: Spawn-Schutz, Abenteuer- und Zuschauermodus, Weltgrenze und die
  Abbau-Ereignisse der Loader (Schutz- und Claim-Mods) gelten, Glas lässt nichts fallen, ein verzierter Topf zerspringt
  in seine Scherben und gibt den Inhalt frei, Rahmen von Freiform-Gates bleiben ganz. Der Client fragt für Blöcke vor
  der Drohne vorab eine Freigabe beim Server ab und sagt den Bruch nur mit Freigabe voraus; in Spawn-Schutz und Claims
  prallt die Drohne deshalb schon beim ersten Treffer ab wie an einer Wand, ohne dass ein Block kurz verschwindet, und
  ein Hinweis nennt den Grund.
- Netzprotokoll 13 wie ganz 0.5.0 (vier neue Pakete `propwash:drone_break`, `propwash:drone_break_denied`,
  `propwash:drone_break_clear`, `propwash:drone_break_clearance`); Flugmodell ohne
  Bruch bitgleich, API unverändert. Entwurf: [`design/drone-break-blocks.md`](design/drone-break-blocks.md).

### Akku unter dem Race-Rahmen (Hinweis eines Rennpiloten vom 08.10.)
- Race-Drohnen tragen den Akku unter dem Rahmen, Freestyle- und Long-Range-Drohnen oben. `race_5` hat jetzt
  `battery.on_top: false` und `plate_mm` −2,8 (Bodenplatte 2 mm + Gummipad 0,8 mm); Whoop, Cinewhoop, Freestyle und
  Long Range bleiben bitgleich.
- Flugmodell (nur Daten, gleiche Formeln): Racer-Preset Schwerpunkt 19,8 → −4,2 mm, Props 18,7 mm über statt 5,3 mm unter
  dem Schwerpunkt, Ixx/Izz −6 %, Roll-/Nick-Autorität +6 %, Auto-PIDs Roll 36/42/33 → 34/40/31, Nick 37/44/34 → 35/41/32,
  Rumpf 59 → 49 mm hoch mit dem Akku als tiefstem Punkt. Schwebegas, Topspeed (168 km/h) und Flugzeit bleiben. Bauchlandungen
  treffen den Akku, und an einer Decke streifen jetzt die Props (vorher hielt der Akku oben sie fern). Einzelheiten:
  [`design/flight-model-spec.md`](design/flight-model-spec.md) §4.2.
- Bild: Riemenschlitze und Gummipad an der Bodenplatte, Deckplatte ohne Schlitze, hängende Akku-Varianten
  `b6s_1100_under`/`b6s_1300_under` (Riemen um den Boden, Schnalle und Etikett unten) samt Lack-Variante; der Renderer nimmt
  die Seite aus der Teile-Definition (wie das Flugmodell) und hängt Packs ohne eigene Variante an ihrer Oberkante auf. Im
  FPV-Bild bleibt der Akku unsichtbar (von der eigenen Drohne zeichnet Propwash dort nur Props und Ducts). Tooltip-Zeile
  „Akku: unter dem Rahmen“ für Rahmen, die ihn sonst oben trügen. Einzelheiten: [`ASSETS.md`](ASSETS.md), „Akku unter
  dem Rahmen“.
- Netzprotokoll und Parts-API unverändert; für Addons neu und freiwillig: Akku-Modelle `<pfad>_under`
  ([`api/changes.md`](api/changes.md)). Just More Parts 1.1.0 trägt den Akku am Race-Rahmen 5″ (Slim) ebenfalls unten.

### Greifer
- Neues Zubehör für die untere Halterung (statt LED-Streifen, auch am Whoop): 18 g, hängt gerade unter der Nase, Rezept
  aus Kupferspule, Platine, Eisennuggets und zwei Haken.
- Scharf und tief (unter einem Block) über liegenden Items schweben: der Greifer nimmt sie auf, solange Gewicht (höchstens
  400 g und nie mehr als die halbe Schubreserve, beim Whoop 21 g) und 4 Stapel reichen; Teilstapel möglich. Die Tragkraft
  folgt der Welt-Regel Schwerkraft: beladen bleibt Schub/Gewicht bei der eingestellten Schwerkraft mindestens 1,3 (bei
  150 % trägt der Whoop nichts, die anderen Drohnen weiter 400 g). Gewichte: Propwash-Teile ihre echte Masse, sonst nicht
  stapelbar 150 g, Stapel bis 16 40 g, Blöcke 50 g, übrige 10 g; Shulker-Kisten, Bündel und andere Behälter wiegen ihren
  Inhalt mit. Items, die Vanilla nur einem bestimmten Spieler gibt, greift die Drohne nur, wenn dieser Spieler sie steuert.
- Die Last macht die Drohne im Flug schwerer (mehr Schwebegas, kürzere Flugzeit, träger), gerechnet über die Masse des
  Zubehörs im vorhandenen Flugmodell; ohne Greifer oder Last fliegt alles wie in 0.4.0.
- Abwerfen: Taste „Greifer öffnen“ (Enter), Funkenschalter „Fracht abwerfen“ (Tab „Modi“), Landen und Entschärfen,
  harter Absturz; die Items fallen mit dem Schwung der Drohne. Danach greift er erst wieder zu, wenn die Drohne 1,5
  Blöcke vom Abwurfpunkt weg ist. Die Fracht wird mit der Drohne gespeichert, es geht nie ein Item verloren.
- Andere Spieler sehen das schwerste Item im Greifer hängen. Flugschul-Szene „Greifer: Items einsammeln“.

### Flugtraining
- Neue Lektionen zum Selberfliegen in der Flugschule (Knopf „Flugtraining“ in der Übersicht und im Konfigurator-Tab,
  Taste M im Flug): fünf Kapitel, zwanzig Lektionen – Grundlagen (Scharfschalten, Schweben im Kasten, Landen auf dem Pad,
  Gieren zu vier Säulen), Vorwärtsflug (Ringe, Höhenwechsel, Kurve, Acht), Rennen (Gate-Linie mit Zwischenzeiten, Slalom,
  zwei Runden auf einem Rundkurs), Freestyle (Flip, Rolle, Power Loop, Split-S, Kombo) und Fortgeschritten (Tauchtor,
  Präzision mit kleinen Ringen, Tiefflug, Abschlussprüfung). Entwurf und alle Werte: `docs/design/flight-training.md`.
- Der Kurs ist ein Hologramm nur beim eigenen Client, gesetzt dort, wo die eigene Drohne gelandet und abgeschaltet steht.
  Eine Platzprüfung verlangt freien Luftraum für alle Ringe, Kästen und Flugwege und Boden für Lande-Pads; sonst dreht sie den
  Kurs oder meldet „Kein Platz“ mit der Zahl der Blöcke im Weg. Die Welt bleibt unverändert, kein Netzwerkverkehr.
- Ringe werden mit `GateGeometry#crossing` erkannt, Runden mit dem `LapTracker` der Rennen, Tricks mit einer eigenen
  `TrickSession` (unabhängig vom Freestyle-Modus); ein Trick zählt nur, wenn die Drohne dabei im Trick-Bereich ist, sonst
  meldet das HUD „außerhalb des Trick-Bereichs, zählt nicht“. Scharfschalten startet die Uhr; Absturz, Zeitlimit, Verlassen des
  Kursbereichs, Abschalten in der Luft oder eine Rahmenberührung in der Präzisions-Lektion beenden den Versuch. Landen und
  abschalten startet den nächsten.
- Sterne nach Zeit (Landung: Abstand zur Pad-Mitte), mit Flughilfe höchstens einer; Bestzeiten und Zwischenzeiten je
  Größenklasse. Whoops fliegen Kurse in 0,6-facher Größe mit 1,25-facher Zeit, Cinewhoops 0,8 und 1,1. Die Grenzen sind mit
  einem Autopiloten auf dem echten Flugmodell kalibriert.
- Fortschritt je Spieler in `config/propwash/flight_training.json`, gilt in allen Welten und auf allen Servern. Kapitel 3
  und 4 öffnen nach Kapitel 2 gemeinsam, Kapitel 5 nach beiden.

### Action-Cam-Aufnahme: Tempo, Hardware-Encoder, Engpass-Hinweise, Stärke
Anlass: Meldung aus der Community, dass 1080p60 selbst auf einem starken Laptop mit Shadern nicht ohne den Hinweis
„Rechner zu langsam – Bilder fehlen“ aufzunehmen war. Messungen und alle Entscheidungen:
[`design/action-cam-recording.md`](design/action-cam-recording.md) („Hardware-Encoder über ffmpeg“, „Aufnahme im Spiel“,
„Messungen 0.5.0“, „Stabilisierung“).
- Der eigentliche Engpass war der Encoder auf dem Prozessor (Bilder mit Shaderpack: rund 50 Bilder/s mit 5 Threads ohne
  Spiel daneben), nicht die GPU-Auslese (Latenz 0–1 Bilder, 0,3–0,5 ms Renderthread je Bild). Gingen Bilder in den Puffer
  auf dem Datenträger, meldete die Anzeige trotzdem „Bilder fehlen“, obwohl alle erhalten blieben.
- Hardware-Encoder: Encoder „Automatisch“ (Standard) nutzt ein installiertes ffmpeg mit VideoToolbox, NVENC, Quick Sync,
  AMD AMF, VA-API oder Media Foundation, nach einer echten Probekodierung je Format; sonst den eigenen Encoder. ffmpeg wird
  gesucht unter `ffmpegPath` in `action_cam.json`, `propwash/ffmpeg/` im Spielverzeichnis, im PATH und an üblichen
  Installationsorten. Fällt ffmpeg während der Aufnahme aus, bleibt das Video bis dahin erhalten.
- Bildpuffer außerhalb des Java-Heaps, Größe nach Arbeitsspeicher (1/16, 256 MB–2 GB, `bufferMb` in `action_cam.json`,
  0 = automatisch; `memoryMb` entfällt). Encoder-Threads `round(0,6 · Kerne) − 2`, 1–12 (vorher `Kerne/2 − 1`, höchstens 6).
  Sechs statt vier GPU-Auslesepuffer, Shader-Vorwärmen beim FPV-Start (vorher einmalig ≈ 70 ms beim ersten Bild).
  Gleichmäßiges Ausdünnen ab 60 % Füllstand statt zufälliger Lücken.
- Aufnahme-Hinweise nennen den Engpass (Spiel, Grafikkarte, Encoder, Datenträger, Zwischenspeicher) mit einem Tipp; der
  Encoder-Hinweis sagt, dass nichts verloren geht und wie viel länger das Speichern dauert. Der Tipp passt zum
  Aufnahmeformat: kleinere Auflösung oder Bildrate nur, wenn es sie noch gibt (bei 1080p60 „720p bzw. 30 fps“, bei 720p60
  „30 fps“, bei 1440p30 „1080p“); bei 720p30 mit Hardware-Encoder heißt er „Programme schließen, die Prozessor oder
  Grafikkarte belasten“, mit dem Propwash-Encoder nur „Encoder ‚Automatisch‘“ bzw. „ffmpeg installieren“. Verlorene Bilder werden nicht
  mehr doppelt gezählt. Nach jedem Video schreibt das Log eine Messzeile.
- Stärke der Stabilisierung (Heavy-Cam): 0–100 %, 50 % = wie bisher; mehr Stärke glättet länger und hält den Horizont
  weiter (±8° bis ±43°), schneidet aber enger zu. Der Tab sagt, ob der Ausschnitt beim aktuellen Fenster Schärfe kostet;
  wenn ja, rechnet der Shader mit Catmull-Rom hoch (+1 bis +1,5 dB gegenüber vorher). Bewusst kein Export-Stabilisieren:
  das wäre eine zweite verlustbehaftete Kodierung.
- `Mp4Reader` akzeptiert SEI und andere gültige NAL-Typen (Hardware-Encoder schreiben SEI), sonst hätte die
  Aufnahmen-Liste solche Videos als beschädigt gezeigt.
- Rein clientseitig: kein Netzwerk, keine API-Änderung, keine Änderung am Flugmodell.

### ffmpeg sichtbar und zuverlässig (Rückmeldung zum Test der 0.5.0 vom 09.10.)
Anlass: Im Spiel stand nirgends, dass die schnellere Aufnahme ein installiertes ffmpeg braucht, und ob es benutzt wird,
ließ sich nicht nachprüfen. Die Suche selbst fand ffmpeg auch aus einem Launcher heraus (Homebrew-Ordner standen schon
in der Liste); es fehlte die Anzeige – der Encoder stand nur im Status einer Drohne mit Action-Cam in der Hand – und
die Probekodierung maß den Prozessstart mit. Entwurf: [`design/action-cam-recording.md`](design/action-cam-recording.md)
(„Hardware-Encoder über ffmpeg“, „Bedienung“), Spielertext: [`recording-ffmpeg.md`](recording-ffmpeg.md).
- Tab *Action-Cam*, neuer Abschnitt „Schneller aufnehmen mit ffmpeg“, immer sichtbar: Statuszeile („ffmpeg 9.0.2 gefunden
  (Pfad) – Hardware-Encoder: VideoToolbox (Test 1080p60: 235 Bilder/s)“, „nicht gefunden – installiere es …“, „gefunden,
  aber kein Hardware-Encoder funktioniert (…)“, „startet nicht (…)“), Feld **ffmpeg-Pfad** (Datei oder Ordner, auch per
  Ablegen einer Datei auf das Fenster), **Testen** (sucht neu, prüft alle Encoder, hebt die Sperre nach einem Ausfall
  auf) und **Anleitung** (Link zum Spielerhandbuch). Der Tab scrollt jetzt.
- Aufnahme-Anzeige: sechs Sekunden lang „Encoder: VideoToolbox (Hardware, über ffmpeg)“ oder „Encoder: Propwash (CPU)“
  mit Grund; der Encoder steht außerdem in der Kopfzeile jedes Hinweises („1080p60 · VideoToolbox“).
- Suche: zusätzlich nicht verlinkte Homebrew-Kegs (`ffmpeg-full`, `ffmpeg@…`), `~/bin`, `~/.local/bin`, Nix, Linuxbrew,
  WinGet-Paketordner und WinGet unter `Programme`, Scoop-App-Ordner und `%SCOOP%`, Chocolatey über
  `%ChocolateyInstall%`; zuletzt auf macOS und Linux die Anmelde-Shell. Ein falscher eigener Pfad wird gemeldet statt
  stillschweigend übergangen. Neuer Zustand `UNUSABLE` (gefunden, startet aber nicht) statt „nicht gefunden“.
- Probekodierung: 90 statt 30 Bilder, gemessen ohne Prozessstart und Encoder-Initialisierung (Entwicklungsrechner,
  1080p60: vorher 106–118 Bilder/s je nach Kaltstart, jetzt 235 wie im Dauerbetrieb; 720p vorher bis hinunter zu 54 bei
  Grenze 45). Kein Einfluss auf die Aufnahme selbst.
- Gemessen im Selbsttest auf einem Apple M4 Pro, 1080p60, zehn Sekunden Testflug, Spiel bei 118 FPS, gestartet mit dem
  `PATH` eines Programms aus dem Dock (`/usr/bin:/bin:/usr/sbin:/sbin`, ffmpeg über den Homebrew-Ordner gefunden).
  Ruhiger Rechner: Propwash-Encoder 61 Bilder/s, Warteschlange bis 28 Bilder (86 MB Puffer), Speichern 0,7 s nach dem
  Stopp; VideoToolbox über ffmpeg 772 Bilder/s, Warteschlange höchstens 3 Bilder (11 MB), Speichern 0,2 s. Mit zwei
  weiteren Minecraft-Instanzen daneben: Propwash-Encoder 38 Bilder/s, Warteschlange bis 391 Bilder (1162 MB), Speichern
  5,9 s; VideoToolbox 301 Bilder/s, Warteschlange höchstens 9 Bilder (29 MB), Speichern 0,2 s.
- Deutsch: Regler „Stärke“ zeigt „50 % · Zoom ×1,15“ statt des abgeschnittenen „Ausschnitt ×…“.
- Rein clientseitig: kein Netzwerk, keine API-Änderung, keine Änderung am Flugmodell. `action_cam.json` unverändert
  (`ffmpegPath` gab es schon, jetzt mit Feld im Tab).

### Action-Cam (Flashback)
- Neues Zubehör für die Halterung oben (36 g, 42 × 28 × 24 mm, 3 W am Flugakku, nicht beim Whoop): nimmt kein Video auf,
  sondern den Weg der FPV-Kamera (Position, Gier, Neigung, Roll, Sichtfeld) für die Replay-Mod Flashback (nur Fabric,
  getestet mit 0.43.6 für 26.3). Entwurf mit allen Entscheidungen: `docs/design/flashback-cam.md`.
- Bedienung wie die anderen Action-Cams: Taste „Aufnahme“ (F9), Funktion „Aufnahme“ auf Schalter oder Taster, „Beim
  Scharfschalten“. Anders als die Video-Kameras auch im Sichtflug. Im OSD zählt ● REC mit, im Sichtflug zeigt ein Feld oben
  „FLASHBACK mm:ss“.
- Jeder Flug wird eine Szene „Propwash · Flug N“ im Flashback-Editor der laufenden Aufnahme: Spur „FPV-Kamera“ (ein
  Keyframe pro Tick oder ausgedünnt), Spur „FPV-Sichtfeld“ (vertikal für 16:9 aus dem Videomodul), Export-Bereich = Flug,
  Marker „Propwash: Flug N“. Eine leere Standard-Szene wird ersetzt, weitere Flüge werden weitere Szenen.
- „Flashback mitstarten“ (Tab Action-Cam, Abschnitt „Flashback“, nur mit Flashback sichtbar, Standard an): Nimmt
  Flashback noch nicht auf, startet der Knopf eine Flashback-Aufnahme und beendet sie mit dem Kamerapfad. Ausgeschaltet
  nimmt die Kamera nur innerhalb einer selbst gestarteten Aufnahme auf. „Keyframes“: „Jeder Tick“ oder
  „Ausgedünnt“.
- Genauigkeit gegen Flashbacks eigene Interpolation gemessen: ein Keyframe pro Tick unter 4 cm zur gleichen Zeit und unter
  1 cm neben der Bahn; ausgedünnt bleibt die Bahn auf etwa 1 cm, das Tempo zwischen Keyframes gleicht Flashback aber aus
  (bis etwa 0,3 Blöcke vor oder nach). Flips verdrehen das Bild nicht; bei sehr schnellen Flips weicht es zwischen zwei
  Ticks bis etwa 20° ab (Flashback speichert Euler-Winkel pro Tick).
- In Flashback-Wiederholungen blendet Propwash die Drohne aus, in der die Kamera sitzt (die wiedergegebene Drohne läuft
  etwa einen Tick hinter dem Pfad); Drohnen vor einer freien Kamera bleiben sichtbar.
- Ohne Flashback gibt es das Item auf Fabric und NeoForge trotzdem (gleiche Register auf Server und Client); Tooltip,
  Tab, Flugschule und der Aufnahme-Knopf sagen, dass Flashback (Fabric) gebraucht wird. Eine nicht passende
  Flashback-Version meldet der Tab mit Grund. Trägt die Drohne diese Kamera, sagt der Tab nur „nimmt nichts auf“ statt
  der Aufnahme-Zeile und nennt die Videoeinstellungen „Videoaufnahme (Action-Cam (Light) und (Heavy))“.
- Eine eigene Taste für diese Kamera gibt es bewusst nicht: Der Knopf „Aufnahme“ löst die Kamera aus, die auf der Drohne
  sitzt (wie an einer echten Funke); nur der Abschnitt „Flashback“ im Tab erscheint erst mit Flashback.
- Rezept: Uhr, zwei Glasscheiben, HD-Videochip, zwei Kupferbarren, Platine. Nicht in Truhen.
- Rein clientseitig: kein Netzwerk, keine Server-Regel, kein neues Protokoll, keine API-Änderung. Flashback wird nicht
  mitgeliefert und nicht vorausgesetzt.

### Bandos
- Neue Strukturen `propwash:bando/<familie>` (Tag `#propwash:bandos`): verlassene Bauwerke zum Freestyle-Fliegen, ohne
  Gates. Sieben Familien mit elf Varianten: Feld und Wald (Scheune, Lagerhalle), Wüste und Tafelberge (rostige Fabrik mit
  Schornstein, Wasserturm, Raffinerie), Schnee (eingestürzte Halle), Dschungel (zugewuchertes Betonskelett), Küste
  (Steg am Ufer, Bohrinsel ab 8 Blöcken Wassertiefe), Berge (Förderanlage mit Seilrad, Schacht und Förderbrücke), Sumpf
  (versunkene Lagerhalle). Biome und Regeln in [`design/bandos.md`](design/bandos.md).
- Prozedural statt Vorlagen: jeder Bando entsteht aus dem Seed seines Strukturstarts aus Bausteinen (Stützenraster,
  Binder, Dächer mit Löchern, Laufstege, Tanks, Gittertürme, Pfähle …), verfällt mit Statik (nichts schwebt, Eingestürztes
  liegt als Schutt darunter) und bekommt einen Hof aus Kies, Pfad oder Pflaster, auf dem keine Bäume wachsen.
- Im Gelände statt darauf: Bodenhöhe aus 25 Messpunkten über dem Grundriss (40-%-Wert), Vanilla-Geländeanpassung
  `beard_box` mit der Bounding Box ab der Zeile über dem Boden (darin über der Bodenzeile nie Gelände, die Bodenzeile
  immer fest, rundherum eine Böschung), der Hof ohne Lücken, Findlinge und Reste über dem Boden räumt das Setzen weg;
  am Hang keine Erdwände und keine Bäume mehr im Hof. Fundamente und verlängerte Stützen bis zum festen Grund am
  Hang. Auf halben Blöcken liegt nichts (Schutt, Moos, Laub nur auf vollen Blöcken, belastete Stufen als Doppelstufe), die
  Förderanlage baut ohne Stufen, Pflanzen ohne Halt verschwinden beim Setzen, Stämme hineinragender Bäume bleiben, ihre
  Blätter weichen tragenden Bauteilen (abgeschnittenes Laub zerfällt wie bei Vanilla). Fehlt das Gelände unter der
  Bodenzeile (Höhleneingang, Lavasee, Talseite am Hang), füllt das Setzen bis zum festen Grund auf. Sumpf mit Hof
  unter Wasser, im Mangrovensumpf keine Bandos mehr (die Kronen deckten die Halle ganz zu).
- An der Küste ebenso: Steg und Bohrinsel nur, wo das Gelände über dem ganzen Grundriss höchstens eine Zeile über dem
  Meeresspiegel liegt (keine Stege mehr in Steilküsten oder unter Felsnasen), darüber räumt das Setzen wie an Land. Alle
  Bandos halten 12 Blöcke Abstand zu Eiszapfen und gefrorenen Ozeanen (Biom-Tag `propwash:bando/keep_away`), weil Vanilla
  Eiszapfen nach den Bauwerken setzt und Eisberge weit reichen. Die Paletten enthalten keine Blöcke mehr, die Vanilla-
  Erzadern nachträglich umwandeln (Andesit, Stein, Tuff), sonst wurden Wände zu Kies und Höfe zu Erde.
- Was Nachbar-Chunks erst nach dem Bando setzen und in Grundriss oder Hof hineinragen würde (Findlinge in
  Riesenbaum-Taigas, Melonen, Lavaseen, Verliese), kommt gar nicht erst hinein: Ein Merkmal aus einem Nachbar-Chunk darf
  in einem Chunk, dessen Bando schon steht, keine Blöcke in die Spalten von Grundriss und Hof schreiben (Stämme, Blätter,
  Pflanzen und Schnee ausgenommen). Was Merkmale des Bando-Chunks selbst nach dem Bando in leere Zellen setzen (etwa ein
  Bienennest an einem Baum), räumt ein Schritt am Ende der Merkmale dieses Chunks weg. Bandos schreiben selbst nur in den
  Chunk, der gerade erzeugt wird. Vorher hing das
  Ergebnis von der Reihenfolge der Chunk-Erzeugung ab, und das nachträgliche Räumen und Ausbessern in Nachbar-Chunks
  ließ das Spiel beim Erkunden neuen Geländes selten im Licht-Thread abstürzen (`MissingPaletteEntryException`,
  Ursache in [`design/bandos.md`](design/bandos.md), Abschnitt „Absturz im Licht-Thread“).
- Selten wie Plünderer-Außenposten: Raster 44 Chunks, mindestens 16 auseinander, Abstand zu Waldanwesen,
  Ozeanmonumenten, Tempeln, Iglus, Hexenhütten und Rennkursen; gemessen etwa ein Bando je 1600 Blöcke. Kein Bando dort,
  wo ein Dorf oder Plünderer-Außenposten (bis 96 Blöcke um den Start), ein Ruinenportal, Schiffswrack, eine Ozeanruine,
  Pfadruine oder ein verlassenes Lager hineinreichen könnte (`avoid_overlap` je Struktur, nur wo das Biom dieses
  Bauwerk zulässt); vorher hielten Bandos nur 4 Chunks Startabstand zu Dörfern, und Dorfstraßen reichten bis an den
  Bando. Offene Minenschächte der Tafelberge gelten als unterirdisch: Wo sie einen Bando schneiden, stehen die Blöcke
  des Bandos.
- Truhe in gut der Hälfte der Bandos, Beutetabelle `propwash:chests/bando`: 2–4 Würfe aus Props, kleinen Motoren, Zellen,
  Spulen, Carbonplatten, Platinen, 1S-/4S-Akkus (40 % geladen), LED-Streifen und etwas Vanilla-Kleinkram; Props und Motoren
  oft gebraucht (Zustand 60–70 %); Bonus mit 16 %: 2207-Motoren, Analog-Race-Cam, 20×20-AIO, Freestyle-Rahmen, 6S 1100,
  Action-Cam Light. Keine Gates, keine Spitzenteile.
- Folgt „Bauwerke generieren“ (aus = kein Bando, `/locate` findet keinen) und Datenpaketen (Biom-Tags
  `propwash:has_structure/bando/<familie>`, Strukturset `propwash:bandos`, `avoid_overlap` der Strukturen,
  Beutetabelle). Kein eigener Schalter.
- Geprüft auf acht Seeds (Selbsttest `-PpropwashBandoSeed=<Seed>`), nicht nur auf dem Test-Seed; findet die Suche auf
  einem fremden Seed eine Variante nicht, prüft der Selbsttest die übrigen Plätze und nennt die fehlende.
- Schutt der Schnee-Hallen ist voller Schnee (8 Schichten) statt Schneeblock: Auf Schneeblock setzt Vanilla nach den
  Bauwerken Fichten, auf Seed 77031 stand so ein Baum im Grundriss einer eingestürzten Halle. JUnit prüft alle Paletten
  gegen den Untergrund der Vanilla-Features. Förderanlagen sind selten (auf manchen Seeds erst 10–16 km vom Spawn); der
  Selbsttest sucht fehlende Familien jetzt bis rund ±28 000 Blöcke und meldet eine auf fremden Seeds fehlende Familie,
  statt zu scheitern ([`design/bandos.md`](design/bandos.md)).
- Kosten der Weltgenerierung: Start 0,6–1,0 ms je Versuch (einmal je Rasterzelle), Setzen etwa 20 ms je Bando, umgelegt
  unter 0,1 % der Chunk-Erzeugung; dieselben Chunks mit und ohne Bauwerke: 18,5 gegenüber 17,9 ms je Chunk.
- Flugschule: Lektion „Bandos: verlassene Spots“ (finden, durchfliegen, Truhe, `/locate`).

### 3D-Modus
- Umkehrbare Motoren wie mit 3D-ESCs und Betaflight `feature 3D`: Gasmitte = Nullschub (Totzone ±50 µs wie
  `3d_deadband_throttle`), darüber Schub vorwärts, darunter drehen die Motoren rückwärts. In der Totzone laufen die
  Motoren im Leerlauf in der zuletzt gefahrenen Richtung weiter; Roll-, Nick- und Gier-Korrektur kehren sich rückwärts
  um, der I-Anteil pausiert 250 ms nach jeder Umkehr. Scharf schalten nur mit Gas in der Mitte (OSD „GAS NICHT MITTE“),
  dafür auch kopfüber – so startet eine kopfüber gelandete Drohne direkt wieder.
- Physik: Drehzahl, Strom, Luft- und Reaktionsmoment laufen durch null; beim Nulldurchgang braucht der sensorlose
  Anlauf einen Moment (5″ etwa 35 ms bis zur Umkehr). Normale Props schieben rückwärts nur gut halb so stark (Teile-Feld
  `reverse_thrust`, Standard 0,55; symmetrische 3D-Props aus Addons ≈ 1,0), Ducts helfen rückwärts nicht – Whoops
  schweben kopfüber nur knapp.
- Bedienung: einmal im Tab „Modi“ freischalten (persönlich), dann auf einen Funkenschalter oder die Taste
  Ziffernblock 6 legen; es zählt die letzte Eingabe wie bei den Flugmodi. Funken nutzen die im Empfänger-Assistenten
  kalibrierte Gasmitte, Gamepads mit Rückstellknüppel den ganzen Weg, Tastatur und schrittweises Gas springen in die
  Mitte und rasten dort ein. Kombinierbar mit Acro, Horizon und Angle; GPS-Rettung, Failsafe und Höhe/Position halten
  fliegen ohne 3D.
- Anzeige: OSD „ACRO 3D“ und Gas −100 … +100, ebenso im Funk-HUD; Flugschul-Lektion „3D-Modus“.
- Welt-Regel `propwash:allow_3d_mode` (Standard an): aus lässt 3D niemanden einschalten, etwa auf Rennservern. Wie das
  Flugmodell setzt der Client die Regel um; der Server sieht den 3D-Modus nicht und kann sie bei veränderten Clients
  nicht erzwingen (wie jede Physik-Regel).
- Ohne 3D rechnet das Flugmodell bitgleich wie vorher (`tools/calibration.sh` byte-gleich). Entwurf und Entscheidungen:
  `docs/design/mode-3d.md`.

### VTX-Abschattung durch Hindernisse
- Das Videosignal rechnet Hindernisse jetzt nach Material und Beugung bei 5,8 GHz (Modell und Entscheidungen in
  [`design/vtx-obstruction.md`](design/vtx-obstruction.md), Quellen in [`design/realism/power-video.md`](design/realism/power-video.md)
  Abschnitt 4.5): 15 Materialklassen nach ITU-R P.2040 und P.527 (Laub 1,15 dB je Block mit Sättigung nach P.833,
  Holzwand 5,6 dB, Mauerwerk 10,1 dB, Beton 34 dB, Fensterscheibe 3,2 dB; Stamm, Erde, Fels, Wasser und Metall
  undurchdringlich), Strahlen quer durch die ersten Fresnel-Zonen und Beugung nach ITU-R P.526 (Messerkante, Streifen,
  Öffnungen, Ortsmittel). Teilblöcke zählen nur mit ihrer Form.
- Beispiele (`analog_race`, Drohne 5 m hinter einer Eiche, 1,5 m hoch): 13,7–13,8 dB statt 7 dB; Analogbild bei 20 m
  sauber, bei 50 m Farbverlust (0,57), bei 100 m Schnee (0,12), `digital_hd` erst bei 100 m blockig (0,39). Knapp am
  Stamm vorbei (Mittelstrahl frei, 0,75 m Versatz) 1,5–4,5 dB statt 0–0,6 dB, ab gut 1 m frei. Hinter einer 7 × 6 m
  großen Steinwand 29 dB statt 7 dB, hinter einem Haus aus Bruchstein 19–21 dB statt 14 dB (aus Holz 11–12 dB), 15 m tief im Wald je nach
  Stelle 2–38 dB (Mittel 17–22 dB statt 11 dB), durch eine Baumkrone allein 5 dB.
- Ohne Hindernis in den Fresnel-Zonen ist die Dämpfung exakt 0 dB: Reichweiten und Schwellen je Videoteil sind gleich
  geblieben. Tiefe Flüge weit draußen verlieren nicht mehr die 0,8 dB, die der alte untere Hilfsstrahl im Boden kostete.
- Leistung: 17 Strahlen je Runde im freien Feld, höchstens 61, Budget 24 000 Blockzellen je Tick (eine Runde verteilt
  sich bei Bedarf auf mehrere Ticks); leere Chunk-Abschnitte werden übersprungen. Jeder Client rechnet vom eigenen Kopf
  aus, auch beim Zuschauen; kein neues Netzpaket.
- Flugschule, Lektion „Absturz, Reichweite, Signal“: Text zu Videosignal und Hindernissen angepasst.

### Signal-Repeater und Kreativ-Brille in Rennen
- Neuer Block **Signal-Repeater** (`propwash:signal_repeater`, Rezept: Blitzableiter, zwei Kupferspulen, Platine,
  Eisenbarren → zwei Repeater, Reiter „Baumaterial“, Kreativ-Tab nach dem Kurs-Werkzeug): an Boden, Wand oder Decke wie
  ein Knopf, keine Kollision, grüne Status-LED. Reicht Bild und Funk 128 Blöcke weit weiter (die letzten 16 Blöcke
  blenden weich aus), Ketten bis zu vier Repeatern; Welt-Regel `propwash:signal_repeaters` (0–4, Standard 4, 0 = aus).
  Modell und Entscheidungen in [`design/signal-repeater.md`](design/signal-repeater.md).
- Jede Teilstrecke rechnet wie die direkte Strecke mit Entfernung und Abschattung nach Material (Beton 34 dB je Block),
  mit der Reichweite des Videomoduls der Drohne; der Client wählt die beste Kette und nimmt sie nur, wenn sie besser ist
  als direkt. Digital zählt die schwächste Teilstrecke, analog addiert sich das Rauschen (zwei gleiche Teilstrecken
  kosten 3 dB). Beispiel Betonbau mit drei Etagen: Pilot im Erdgeschoss, Drohne zwei Decken höher – direkt kein Bild,
  über einen Repeater unten am Treppenhaus und einen unter dem Dach klar.
- Funk über Repeater nur nach Entfernung (der Funk hat keine Abschattung); weil eine Kette (höchstens 640 Blöcke) nie
  länger als die Funkreichweite ist, bleiben Start-, Flug- und Rettungsprüfungen des Servers unverändert.
- OSD-Element „RPT“ (Vorlagen Minimal, Standard und Rennen): Zahl der Repeater im Weg und Signal je Teilstrecke.
- Kein neues Netzpaket (Protokoll bleibt 11): Repeater kennt jeder aus den Chunk-Daten, das Verzeichnis führt das
  Block-Entity ohne Welt-Scans; Bild-Teilstrecken mit eigenem Budget von 24 000 Zellen je Tick.
- **Kreativ-Brille zählt in Rennen:** Reichweite, Hindernisse, saubere Bilder und eigenes Sichtfeld sind keine
  Flughilfe; nur eine eigene Latenz unter der des Videosenders. Neue Zeile „Latenz aus: Videomodul / eigenem Wert“ im
  Video-Tab der Kreativ-Brille (Standard Videomodul; bis 0.4.0 zeigte die Kreativ-Brille immer mit 0 ms).
- Zählt eine Runde nicht, sagt die Rennanzeige warum – beim Start, sobald der Grund auftritt und bei der Runde:
  Höhe/Position halten, GPS Rescue, veränderte Welt-Regeln mit Namen, Kreativ-Drohne, Teile nur ohne „Teile müssen
  zusammenpassen“, Latenz der Kreativ-Brille („0 ms statt 12 ms“).

### Bildformat 16:9 / 4:3
- Tab „Video“, Einstellung „Bildformat“: 16:9 (Standard, füllt wie bisher das ganze Fenster, Sichtfeld bitgleich),
  4:3 oder Auto (analog 4:3, digital 16:9; beim Zuschauen zählt der Sender der beobachteten Drohne). Persönlich, kein
  Einfluss auf Server, Welt oder Flugmodell.
- 4:3: Das horizontale Sichtfeld der Kamera im Videomodul spannt die Breite des 4:3-Bildes, senkrecht zeigt das Bild
  so viel wie eine echte 4:3-Kamera (Kamera mit 125°: 110,5° statt 94,4° hoch). Schwarze Ränder links und rechts, bei
  Fenstern schmaler als 4:3 oben und unten; Ränder gleich breit, auf ganze Pixel, bei jeder GUI-Skala.
- Im 4:3-Bild liegen: analoge und digitale Bildstörungen (Shader und Ersatz-Overlay), das OSD samt Horizont und
  Home-Pfeil, die Vorschau im OSD-Editor und die Kacheln „So zerfällt das Bild“. Der LED-Scheinwerfer bleibt in der
  Bildmitte.
- Werkbank und Wartungsmenü zeigen die Props im Bild für das eigene Format; Warnung und API-Werte bleiben 16:9.
- Action-Cam-Aufnahmen bleiben 16:9 mit dem Sichtfeld des 16:9-Bildes (bei 4:3 aus etwas weniger Bildpunkten), der
  Kamerapfad für Flashback ist unberührt. Rechnung und Entscheidungen: [`design/image-format.md`](design/image-format.md).

### Empfänger-Assistent mit animierter Funke, Scharf-Winkel
- Das Modell der Propwash-Funke macht in jedem Schritt des Empfänger-Assistenten vor, was zu tun ist (Gas erst ganz nach
  oben, dann unten halten; Roll, Nick und Gier bis zum Anschlag; Prüfansicht Kanal für Kanal; Knüppel loslassen und Gas
  in die Mitte, mit freigeschaltetem 3D genau in die Mitte; große Kreise; Schalter umlegen; Tasten drücken), mit Ring,
  Pfeil, Haltebalken und kurzem Text, neben den Live-Balken der echten Funke. Funktioniert bei jeder GUI-Skala; wo kein
  Platz ist, erscheint sie auf der Schalter-Seite als Karte beim Zuweisen und beim Zeigen auf „Zuweisen“.
- Schalter-Seite: „Scharf schalten“ hervorgehoben, Hinweis „Leg deinen Arm-Schalter um – die Lampe muss angehen“, „Fertig“
  ohne Arm-Schalter fragt einmal nach (dann nur mit der Taste scharf schalten).
- Die Funke hat bewegliche Schalter und drei Tasten unter dem Display (in der Hand in Ruhe, Aussehen wie bisher).
- Scharf-Winkel je Drohne (Tab „Konfiguration“, Abschnitt „Scharf schalten“): 0–180° wie `small_angle` in Betaflight,
  Standard 25° wie bisher; 180° = scharf in jeder Lage, auch in der Luft und kopfüber. Tuning-Schlüssel
  `pid.armMaxAngleDeg` (Gruppe `PID`, damit in Sharecodes und Standard-Importen); Flugschul-Schritt „Scharf schalten in der
  Luft“.

### Gates frei drehen und frei groß
- Schleichen + Rechtsklick mit Gate-Item oder Kurs-Werkzeug auf ein Gate startet „Gate einstellen“ (Einstell-Modus im
  Spiel, siehe nächster Abschnitt): Gieren 0–359°, Nicken −90…90°, Rollen −180…180° (0,1° genau), Breite und Höhe der
  Öffnung 1–16 Blöcke in halben Schritten; Live-Vorschau in der Welt (das Gate steht schon in der neuen Stellung, rot =
  blockiert), Übernehmen, Zurücksetzen und „Aus dem Kurs“ (mit Kurs-Werkzeug). Der Server prüft Reichweite, Baurecht,
  Kursbesitz und Platz und lehnt sonst ab.
- Das Gate dreht um die Öffnungsmitte und bleibt auf seiner Fläche: eine Raute steht auf der Spitze, ein geneigtes
  Tauchtor berührt mit seiner Kante die Wand, ein geneigtes hängendes Gate hängt an senkrechten Gurten. Stapeln nur
  stehend und ungeneigt, diagonale Stapel gehen. Leuchtpfeile liegen bei geneigten Gates eben vor der Öffnung.
- „Gate drehen“ (`R`) dreht stehende und hängende Gates in 90°, 45°, 15° oder 5° (Tab „Rennen“, Standard 90°), auch die
  Vorschau vor dem Setzen; Wand- und liegende Gates kehrt die Taste weiter um.
- Schräge Teile kollidieren für Drohnen als orientierte Boxen, das Flugmodell löst den Kontakt mit der echten Normale
  (streifend prallt die Drohne ab, frontal crasht sie wie an einer Wand), Spieler stoßen an eine feine Näherung. Zielen,
  Durchflug, Ideallinie und Geister-Splits rechnen für jede Lage. Große Gates (bis 17 × 17 Blöcke außen) sind auch am
  fernen Pfosten anvisierbar.
- Bisher nahm Schleichen + Rechtsklick mit dem Kurs-Werkzeug ein Gate aus dem Kurs; das macht jetzt der Knopf im Panel.
  Flugschul-Lektion „Gates frei drehen“. Entwurf und Entscheidungen: [`design/gate-free-transform.md`](design/gate-free-transform.md).

### Gate einstellen im Spiel
- Statt eines Fensters läuft „Gate einstellen“ als Einstell-Modus im Spiel (Gates, Flaggen, Slalomstangen,
  Tauchwürfel, Freiform-Gates): Die Welt bleibt ganz sichtbar, man läuft mit WASD und schaut mit der Maus weiter, das
  Spiel pausiert nicht. Rechts ein schmales Panel (Propwash-Theme) mit den Zeilen, dem Kurs, dem Status und der
  Tastenhilfe.
- Mausrad ändert die gewählte Zeile (Winkel in 5°-Schritten, Strg: 1°; Größen in halben Blöcken; Seite, Ausgang und
  Richtung schalten weiter), Schleichen + Mausrad wählt die Zeile, „Gate drehen“ dreht in der Dreh-Schrittweite.
  Enter oder Rechtsklick übernimmt und beendet, Esc verwirft ohne Spielmenü, Linksklick tut nichts.
- Linke Alt-Taste halten (Taste „Gate einstellen: Maus frei (halten)“, änderbar) gibt die Maus fürs Panel frei: Regler
  ziehen, −/+, Tooltips, Zurücksetzen, Neu abtasten, Aus dem Kurs, Abbrechen; Laufen geht weiter, Loslassen schließt das
  Panel. Die Tipp-Zeile „Alt links halten: Maus frei“ steht immer im Panel. F1 blendet das Panel aus.
- Live-Vorschau: Passt die neue Stellung, zeigt der Client das Gate selbst dort (mit Nummern-Tafel und Markierungen) und
  die alte Stellung als blassen Umriss; sonst einen roten Geist. Übernehmen geht bis Interaktionsreichweite + 16 Blöcke
  (bisher + 2), damit man zum Ansehen zurücktreten kann; „Gate drehen“ aufs anvisierte Gate bleibt bei + 2.
- Netzprotokoll und API unverändert. Entwurf und Entscheidungen: [`design/gate-edit-mode.md`](design/gate-edit-mode.md).

### Kurs-Elemente: Wendeflagge, Slalomstangen, Tauchwürfel
- Neue Items `propwash:race_flag` (Rennflagge), `propwash:slalom_pole` (Slalomstange, Rezept gibt zwei) und
  `propwash:dive_cube` (Tauchwürfel), herstellbar, im Kreativ-Tab nach den Gates. Rechtsklick auf den Boden setzt sie mit
  Vorschau; `R` dreht in der Dreh-Schrittweite, Schleichen + `R` wechselt Seite, Richtung oder Ausgang.
- Das Kurs-Werkzeug hängt sie wie Gates an (der Kurs beginnt weiter mit einem Gate). Folgt eine Stange auf eine Stange,
  bekommt sie die andere Seite – eine Reihe Stangen wird so zum Slalom.
- Zählung in Stufen: Stange auf der verlangten Seite vorbei; Flagge vorbei, dahinter herum und auf der anderen Seite
  zurück (links oder rechts herum); Würfel durch die Deckfläche hinein und durch die eingestellte Seite hinaus. Falsche
  Seite, falsche Richtung oder falscher Ausgang melden sich rot im HUD, das Element bleibt das nächste Ziel; umkehren
  nimmt eine geschaffte Stufe zurück. Der Server prüft jede Stufe als eigenen Prüfpunkt.
- Bodenpfeile auf der richtigen Seite, rotes Kreuz auf der falschen, Pfeilbogen um die Flagge, Tauchpfeile und
  leuchtender Ausgang am Würfel, Nummern-Tafel; das nächste Element leuchtet grün. Die Ideallinie führt durch die
  Elemente, Splits, Geister und Bestenliste zählen sie wie Gates.
- Schleichen + Rechtsklick startet „… einstellen“ im Spiel: Richtung, Seite/Richtung/Ausgang, Höhe und Wertungsbreite (wie
  weit neben Stange oder Flagge ein Vorbeiflug noch zählt) bzw. Öffnung des Würfels 1–16 Blöcke. Elemente kollidieren mit
  ihren Rohren und dem Tuch, Stapeln und Neigen gibt es bei ihnen nicht. Die Kopfzeile des Panels (auch bei Gates) beginnt
  mit „In der Welt:“ und nennt den gesetzten Stand, Zeilen und Vorschau zeigen den Entwurf.
- Flugschul-Lektion „Flaggen, Slalom und Tauchwürfel“. Entwurf und Entscheidungen:
  [`design/course-elements.md`](design/course-elements.md).

### Gate-Stange
- Neues Item `propwash:gate_pole` „Gate-Stange“ (Rezept: Glowstone-Staub über zwei Eisengittern, ergibt zwei; Kreativ-Tab
  nach den Gates): eine einzelne Gate-Seite mit Profil, Verbindern und LED-Leisten der Gates. Rechtsklick auf den Boden
  stellt sie auf (Kreuzfuß), an einer Wand ragt sie waagerecht heraus, an einer Decke hängt sie bündig herab.
- Rechtsklick mit einer Gate-Stange auf ein stehendes Gate setzt sie auf die nächste obere Ecke – wie der
  3-Wege-Verbinder bei MultiGP; auf eine Stange verlängert sie den Turm, höchstens vier übereinander. Die Vorschau zeigt
  den Platz schon beim Zielen auf das Gate.
- Länge 1–16 Blöcke in halben Schritten (Standard 4), 16 Farben der LED-Leisten (Schleichen + `R`, „Gate-Stange
  einstellen“ oder Rechtsklick mit einem Farbstoff), Richtung mit `R`. Kollision wie eine Gate-Seite.
- Hindernis und Markierung, kein Kurs-Element: Das Kurs-Werkzeug nimmt Stangen nicht auf. Ein Gate mit Stangen lässt
  sich nur umdrehen; rutscht es in einem Gate-Stapel nach, wandern die Stangen mit; wird es abgebaut, kommen sie mit ab.
  Eine Stange auf ein Gate eines fremden Kurses setzen darf nur, wer den Kurs bearbeiten darf.
  Entwurf und Entscheidungen: [`design/gate-pole.md`](design/gate-pole.md).
- Gates, Stangen und Kurs-Elemente messen ihre Helligkeit in der Mitte statt 3,4 Blöcke über ihrer Position: Stangen an
  Nord- und Westwänden, hängende Stangen ab 2,5 Blöcken und Gates oder Stangen in niedrigen Räumen werden nicht mehr
  schwarz gezeichnet.

### Freiform-Gate
- Neues Item `propwash:freeform_gate` „Freiform-Gate“ (Rezept: Glowstone-Staub und Redstone im Ring, Kreativ-Tab nach dem
  Tauchwürfel): Rechtsklick auf den Rand einer Öffnung – Gang, Höhleneingang, Fenster oder die Innenwand eines Schachts –
  macht die Öffnung selbst zum Gate. Eine Flutfüllung in der Ebene quer zum Blick findet die Fläche, begrenzt von
  Blöcken, die kollidieren (auch Laub, Scheiben und Gitter) und von Flüssigkeiten; zwei Blöcke, die sich nur an einer
  Ecke berühren, schließen. Höchstens so groß wie die neue Welt-Regel `propwash:freeform_gate_max_size` (1–64,
  Standard 20 × 20, in allen Voreinstellungen 20). Vorschau beim Zielen mit Größe und Richtung oder dem Grund einer
  Ablehnung; `R` kehrt die Flugrichtung um.
- Kein Rahmen: ein leuchtender LED-Umriss am Rand, hauchdünne Füllung beim Annähern und als nächstes Ziel, Winkel am Boden
  zeigen die Richtung, die Nummern-Tafel hängt oben in der Öffnung. Farben wie bei Gates; als Start gestrichelt mit
  „START“. Zählt nur in den gespeicherten Feldern, beim Client und auf dem Server gleich; darf Start und Ziel sein.
- Ändern sich die Blöcke, bleibt die Fläche, wie sie war; offene Stellen im Rand leuchten rot, ein Warndreieck erscheint,
  und „Neu abtasten“ beim Einstellen (Schleichen + Rechtsklick, Alt für die freie Maus) übernimmt die Blöcke von jetzt.
- Flugschule „Gates frei drehen“ mit dem Schritt „Freiform-Gate“. Entwurf und Entscheidungen:
  [`design/freeform-gate.md`](design/freeform-gate.md).

### Gates geben Redstone-Signal
- Fliegt eine Drohne in Flugrichtung durch ein Gate oder Freiform-Gate, bekommt der Block, an dem es befestigt ist,
  einen Redstone-Puls wie von einem Knopf: stehend der Block darunter, hängend der Deckenblock, an der Wand der
  Wandblock, liegend über einer Grube oder unter einem Deckenloch der Randblock unter bzw. über der Mitte des unteren
  Balkens, beim Freiform-Gate der Randblock unter der Mitte der untersten Zeile (waagerecht: an der Nordkante),
  gestapelt der Block unter dem untersten Gate. Gilt im freien Flug und im Rennen, dieselbe Server-Prüfung wie die
  Zeitmessung; Gegenrichtung zählt nicht.
- Standard an, 10 Ticks (0,5 s); je Gate im Einstell-Modus auf der neuen Seite „Redstone“: an/aus, 2–100 Ticks, „Nur
  im Rennen“ (das Start-/Ziel-Gate gibt dann bei jedem Durchflug ein Signal, die anderen nur im laufenden Rennen auf
  ihrem Kurs). Ein roter Rahmen zeigt die Fläche, über die der Strom geht. Ein Durchflug im Puls verlängert ihn ohne
  Lücke.
- Abbauen, Entladen und Neustart mitten im Puls lassen keine Lampe hängen. Der Puls endet auch, wenn der Chunk des
  Gates zwar geladen ist, aber keine Entity-Ticks bekommt (Pilot außerhalb der Simulationsweite, Drohne schon im
  nächsten Chunk) – er zählt am Ende jedes Server-Ticks herunter. Ohne laufenden Puls kostet es nichts.
- Einzelheiten: `docs/design/gate-redstone.md`.

### Runden je Kurs und Kurse bearbeiten (Rückmeldung vom 09.10.)
- Jeder Kurs hat eine eigene Rundenzahl (0 = freies Training, höchstens 99) und einen Namen. Neue Kurse übernehmen
  beim ersten Klick mit dem Kurs-Werkzeug die Einstellung *Runden (Standard)* ihres Erbauers; danach gilt die
  Rundenzahl des Kurses für alle Piloten, auch in Rennen mit Startaufstellung. Der Server nimmt eine Zielmeldung erst
  nach der Rundenzahl des Kurses an. Kurse aus 0.4.0 und gefundene Kurse haben keine eigene Rundenzahl und folgen wie
  bisher der Einstellung des Piloten, bis jemand eine einstellt.
- Fenster *Kurs bearbeiten* (Kurs-Werkzeug, Rechtsklick in die Luft → Bestenliste → *Kurs bearbeiten*, oder Tab
  *Rennen* → *Bestenliste*): Name, Rundenzahl, Liste aller Gates und Elemente mit Verschieben, *Start* (Start und Ziel
  auf dieses Gate, die Reihenfolge bleibt), Herausnehmen, *Gates anhängen* (stellt das Kurs-Werkzeug auf den Kurs) und
  *Kurs löschen* (zweimal klicken; die Gates bleiben stehen). Start und Ziel bleibt immer ein Gate oder Freiform-Gate.
- Wertung: Rundenzeiten und Geister je Kurs, Layout und Drohnenklasse; Rennzeiten zusätzlich je Rundenzahl. Name und
  Rundenzahl ändern nichts an der Wertung. Jede Layout-Änderung archiviert Zeiten und Rennzeiten (die letzten 5
  Layouts, `/propwash race archive <kurs>`), löscht die Rekord-Geister und beginnt eine neue Bestenliste; die leere
  Liste verweist auf das Archiv.
- Rechte wie beim Kurs-Werkzeug: Ersteller und Operatoren; Kurse ohne Ersteller ändert, wer höchstens 256 Blöcke von
  einem Gate steht; nie im Abenteuer- oder Zuschauermodus; gefundene Kurse löscht nur ein Operator oder der Host. Wer
  nicht ändern darf, sieht das Fenster als *Kurs ansehen* mit dem Grund.
- Befehle `/propwash race laps <runden> <kurs>` und `/propwash race name <kurs> [name]`; `/propwash race courses` nennt
  Name und Rundenzahl. Bestenliste mit Kursname, Rundenzahl und dem eigenen besten Rennen über diese Rundenzahl.
- Netzprotokoll 15 (zwei neue Pakete, Kursdaten und Bestenliste mit zusätzlichen Feldern), keine API-Änderung.
  Entwurf und Entscheidungen: [`design/course-edit.md`](design/course-edit.md).

### Props reparieren
- Zwei beschädigte Props derselben Sorte (auch kaputte) im 2×2- oder 3×3-Raster ergeben einen Prop mit beiden
  Zuständen zusammen plus 5 %, höchstens 100 % – wie bei Vanilla-Werkzeugen. Stapel gehen, jedes Craften nimmt aus
  beiden Feldern einen. Der Schleifstein nimmt beschädigte Props einzeln und rechnet genauso, ohne Erfahrungspunkte.
- Das Ergebnis behält die Daten des ersten Props (Name), Symbol und Haltbarkeitsbalken folgen dem neuen Zustand; ab 100 %
  stapelt der Prop wieder mit neuen. Props von Addons lassen sich ebenso reparieren, verschiedene Sorten nie.
- Kaputte Props sagen es im Tooltip, der Shift-Tooltip jedes Props und das Zerlegen-Feld der Werkbank nennen das
  Reparieren, die Flugschul-Lektion „Props wechseln und Schäden“ hat den Schritt „Alte Props reparieren“. Sonderrezept
  `propwash:special/prop_repair` (nicht im Rezeptbuch). Entwurf: [`design/survival.md`](design/survival.md)
  „Props reparieren“.

### Ducts im FPV-Bild und Ducts wie echte
- Ducts aus dem 3D-Modell erscheinen im FPV-Bild und beim Zuschauen dort, wo die echte Kamera sie sähe (vordere Ducts
  unten in den Ecken), mit Uptilt, Sichtfeld und Lackierung. Gezeichnet werden die
  Ringsegmente des Rahmenmodells (`FpvGuards`, `FpvGuardZone`), mit derselben Tiefenabbildung wie die Prop-Scheiben
  (`FpvPropGeometry.pushScale`), nie im Iris-Schattenpass und nicht in der Messung „Props im Bild“. Props und Ducts der
  eigenen Drohne zeichnet ein eigener Durchgang wie die Vanilla-Hand (`FpvPropPass`), damit Boden und Wände sie nicht
  mehr verdecken (gelandet, beim Abheben, im Tiefflug, an Wänden); mit Iris-Shaderpack bleibt der alte Weg. Eigener Schalter
  „Ducts im FPV-Bild“ im Tab „Darstellung“ (Standard an). Details und Anteil der Ducts im Bild je Drohne:
  [`ASSETS.md`](ASSETS.md) „Ducts im FPV-Bild“.
- Ducts von Whoop 65 mm und Cinewhoop 3″ neu erzeugt (dünnere Wände, schmale Lippe bzw. Rand, 24 Segmente, Oberkante
  knapp über den Blattspitzen), Regeln in `tools/assets/parts/ducts.py`, auch für `addon.py`; Just More Parts 1.1.0 nutzt
  dieselben Regeln und setzt die Motoren seiner gerahmten Whoops und Cinewhoops auf die Motorhöhe von Propwash.
- Kamerahalter des eingebauten Whoop 65 mm und Cinewhoop 3″ über den Ducts wie bei echten Whoops: Whoop-Objektiv
  (0, 9,7, −17,8) → (0, 16, −20) mm in einer höheren Haube, Cinewhoop (0, 11,7, −36,8) → (0, 30, −48) mm in einem Käfig
  über den vorderen Ducts, der Action-Cam-Halter oben steigt mit (y 30,7 → 44,7). Ducts im Bild bei 0/20/40° Uptilt:
  Whoop 34/21/5 → 21/7/0 %, Cinewhoop 58/42/26 → 15/0/0 % (vorher beim Cinewhoop zwei Wände über die volle Bildhöhe).
  „Props im Bild“ beim Standard-Uptilt: Whoop 8,8 → 6,2 %, Cinewhoop 7,0 → 1,9 % (Props liegen jetzt unter dem Objektiv).
  Flugwerte der eingebauten Drohnen gleich, außer beim Cinewhoop mit Action-Cam oben (deren Masse sitzt 14 mm höher);
  Objektiv und Kamerapunkt der Kollisionshülle wandern mit (Airframe-Goldens der beiden Rahmen).
- Just More Parts 1.1.0 setzt die Kamera seiner Whoops 65 Lite/75/85 mm und Cinewhoops 2,5″/3,5″ über die Ducts: Vorher saß das
  Objektiv zwischen den vorderen Ducts unter deren Oberkante, und die Ducts standen als Wände über die volle Bildhöhe
  (38–75 % des Bildes, bei vier Rahmen auch bei 40° Uptilt). Jetzt 16–22 % bei 0°, 5–15 % bei 20°, nichts bei 40°. Mit diesen Rahmen
  ändert sich in `motion.txt` nur die Prüfsumme `spec` (Objektiv und Kamerapunkt der Hülle), die Flugwerte bleiben gleich.
- Selbsttest: Bei 20° und 40° Uptilt dürfen die Ducts ganz aus dem Bild sein, wenn auch das gerenderte Bild keine zeigt;
  bei 0° müssen sie im Bild sein (ebenso JUnit `FpvGuardsTest`).

### Kreativ-Drohne und Drohnen-Editor
- Neues Item `propwash:creative_fc` „Kreativ-Flugcontroller“ (nur Kreativ-Inventar und `/give`, kein Rezept, keine
  Beute): passt in jeden Rahmen, auch aus Addons, verträgt 1–12S und macht die Drohne zur Kreativ-Drohne.
- Tab „Drohnen-Editor“ im Konfigurator (nur bei Kreativ-Drohnen): 45 Werte des Flugmodells frei (Massen, Trägheit,
  Motorpositionen, Props mit Ct/Cp, Motoren, Akku, ESC, Luftwiderstand, Kamera mit Uptilt 0–80° und Sichtfeld), Kennzahlen
  und Hinweise live, Vorlagen aus Teilen, Presets, Addon-Presets und eigenen Vorlagen, Teilen-Code `PWD1-…`. Die Werte
  laufen als Teile-Daten durch dieselbe Ableitung; ein Preset als Vorlage fliegt bitgleich wie das Preset.
- Ändern nur im Kreativmodus, der Server begrenzt jeden Wert und lehnt nicht-endliche Flugmodelle ab; Kreativ-Drohnen
  zählen nicht für Bestenlisten. Flugschul-Lektion „Kreativ-Drohne und Drohnen-Editor“ (nur im Kreativmodus).
  Entwurf und Entscheidungen: [`design/creative-drone.md`](design/creative-drone.md).

### Eigene Befehle auf Funken-Schaltern
- Tab „Modi“, Seite „Befehle“ (Umschalter „Funktionen | Befehle“ in der Kopfzeile): acht Funktionen „Eigener Befehl 1–8“,
  belegbar wie jede Funktion (AUX-Bereich, Taster, Inv., Halten/Umschalten, „Schalter umlegen“). „Befehl einrichten …“
  oder Doppelklick öffnet das Fenster für den Befehl beim Einschalten und optional beim Ausschalten. Tasten „Eigener
  Befehl 1–8“ (unbelegt, Gruppe „Eigene Befehle“ im Tab „Tasten“): jeder Druck führt den Einschalt-Befehl aus, mit
  Ausschalt-Befehl wechselt jeder Druck.
- Gesendet wie im Chat getippt (`ClientPacketListener#sendCommand`, auch signierte Argumente und Client-Befehle der
  Loader), mit den Rechten des Spielers; der Server entscheidet und meldet Fehler im Chat. Eine Meldung zeigt jeden
  ausgeführten Befehl, gelb, wenn der Befehlsbaum des Clients ihn nicht kennt.
- Nur im Flug (FPV oder Sichtflug) und nie bei offenem Fenster; Flanken bei offenem Fenster werden gemeldet und nicht
  nachgeholt, beim Flugstart löst die aktuelle Schalterstellung nichts aus. Je Schalter höchstens ein Befehl je 0,5 s
  (die Endstellung zählt), insgesamt 5 auf einmal und dann einer je Sekunde – unter der Spam-Grenze des Vanilla-Servers.
- Höchstens 256 Zeichen samt `/` wie der Chat, `/` am Anfang freiwillig, Zeichen, für die der Server trennt, werden
  abgelehnt. Gespeichert je Funke in `config/propwash/input.json` (`commands`), die Tastatur hat einen eigenen Satz;
  „Profil zurücksetzen“ im Empfänger-Assistenten behält die Befehle. Rein clientseitig, kein neues Paket.
- Flugschul-Lektion „Konfigurator“ mit neuem Schritt „Eigene Befehle“. Entwurf und Entscheidungen:
  [`design/switch-commands.md`](design/switch-commands.md).

### Einstellungen: Zahlen tippen, Suche, eigene Presets
- **Zahlen tippen (gemeldet auf Discord):** In Rates, PID, Welt-Regeln, Kreativ-Editor und den Bereichen der Funken-Schalter
  kamen getippte Ziffern nie an, nur die Pfeile gingen. Ursache: Minecraft 26.3 liefert Zeichen über SDL3 nur bei
  eingeschalteter Texteingabe, die eigenen Zahlenfelder schalteten sie nicht ein. Jetzt tippbar mit Ziffernreihe und
  Ziffernblock, Punkt oder Komma (angezeigt im Trenner der Sprache), Minus, Rücktaste, Entf, Cursor, Strg/Cmd+A, C, X, V;
  Enter übernimmt, Esc stellt zurück, ↑/↓ übernimmt und geht einen Schritt. Werte außerhalb des Bereichs werden orange
  angezeigt und auf die Grenze gesetzt (Tooltip „Erlaubt: a – b“, Meldung in der Fußzeile), ungültige Eingaben rot.
  Klick ohne Ziehen startet die Eingabe, Ziehen und die Pfeile ändern weiter schrittweise. Auch jeder Regler lässt
  sich tippen, sobald er den Fokus hat (Klick, Tab oder Sprung aus der Suche): PID, Welt-Regeln, LED-Helligkeit,
  GPS-Rettung, Rennen, Sichtfeld und Videoeffekte, Totzone, Tastatur-Ausschlag und Gas-Tempo im Empfänger-Tab und
  -Assistenten, Lautstärken und Stimmen, Darstellung samt Staub, OSD und der Kamerawinkel im Wartungsmenü. Getippt
  wird in der Einheit der Anzeige (Prozent-Regler in Prozent, OSD-Größe in %, Leiterabstand in °), nach dem Begrenzen
  bleibt der Rahmen kurz orange. Klick daneben übernimmt eine gültige Eingabe, Esc verwirft sie auch im Wartungsmenü
  und im Assistenten, statt das Fenster zu schließen.
- **Suche:** Feld oben rechts im Konfigurator, Strg+F (macOS Cmd+F). Vorschläge beim Tippen aus allen Tabs: Einstellungen,
  Welt-Regeln, Tastenbelegungen und Tabs, gefunden über Namen, Tooltips, Deutsch und Englisch gleichzeitig und über 40
  Synonym-Gruppen aus der Szene (Empfindlichkeit/Rates, Totzone/Deadband, FOV/Sichtfeld, Gamerule/Welt-Regel …).
  Enter oder Klick öffnet den Tab, scrollt hin, hebt die Einstellung 2,5 s hervor und gibt ihr den Fokus. Auch Tabs von
  Addons sind durchsuchbar.
- **Eigene Presets:** in den Tabs „Rates“ (Rates-Typ und alle drei Achsen, ohne Totzonen) und „PID-Tuning“
  (Abweichungen vom Standard der Drohne, Gains nur von Hand gesetzt): merken, übernehmen, umbenennen, löschen, als Code
  `PWT1-…` kopieren und einfügen. Gespeichert je Spieler in `config/propwash/tune_presets.json`, höchstens 32 je Tab,
  gelten für jede Drohne. Im PID-Tab gibt es dazu „Standard (aus dem Aufbau)“.
- Rein clientseitig, kein neues Paket, Parts-API und Netzprotokoll unverändert. Entwurf und Entscheidungen:
  [`design/config-input.md`](design/config-input.md).

### Blackbox im Betaflight-Format
- Tab „Blackbox“ (nach „Rates“): *Loggen, wenn scharf* (Standard aus), *Lograte* 1/1–1/8 der Regelschleife
  (Standard 1/1 = 2 kHz, etwa 4 MB pro Flugminute), *Speicher für Logs* 128 MB – 4 GB (Standard 512 MB, die ältesten Logs
  werden gelöscht), Liste der letzten Logs, *Ordner öffnen*, Status. Persönlich in `config/propwash/blackbox.json`, Logs
  unter `propwash/blackbox/propwash_<Datum>_<Zeit>_<Name>.bbl`, eine Datei je Scharfschalten.
- Format wie Betaflight 4.5.1 (I/P/S/G/H/E-Frames, echte Prädiktoren und Kodierungen, Kopf mit PIDs, Rates, Filtern und
  Motor-Daten unter Betaflight-Namen): `blackbox_decode` und der Parser des Blackbox Explorers lesen jedes Feld, geprüft
  mit `tools/blackbox_check.sh` gegen das Flugmodell. Felder: loopIteration, time, axisP/I/D/F, rcCommand, setpoint,
  vbatLatest, amperageLatest, baroAlt, rssi, gyroADC, gyroUnfilt, debug (GYRO_SCALED), motor, eRPM; S-Frame mit
  Flugmodi, GPS-Status und Failsafe; GPS um 0° N 0° O.
- Schalterfunktion „Blackbox“ (Tab „Modi“, nicht im Assistenten) wie der Modus BLACKBOX; ohne Schalter pausiert die Taste
  „Blackbox pausieren / fortsetzen“ (Num 7). Abgriff `sim.FcListener` liest nur – Flugmodell, Goldens und Kalibrierung
  unverändert; Schreiben im eigenen Thread, keine Allokation je Abtastung. Rein clientseitig, Netzprotokoll und Parts-API
  unverändert. Entwurf, Feldliste und Entscheidungen: [`design/blackbox.md`](design/blackbox.md), Anleitung für Spieler:
  [`blackbox-tuning.md`](blackbox-tuning.md).

### Doku auf propwashfpv.com
- Spielerhandbuch, alle Welt-Regeln (auch als `gamerules.json`), Add-on-API mit Referenz aller öffentlichen Typen und das
  Teile-Format unter [propwashfpv.com/docs](https://propwashfpv.com/docs/), erzeugt aus dem Code dieses Releases. Mod Menu
  (Fabric) verlinkt sie als „Wiki“.

### Flugschule: ruhige Hinweise
- Hinweis-Kästen in allen Lektionen (auch denen von Addons) werden einmal gesetzt und bleiben stehen, während Kamera,
  Bildausschnitt oder das eigene Drehen und Zoomen die Szene bewegen; nur die Zeigelinie folgt jedes Bild genau ihrem Punkt.
- Ein Kasten zieht nur um, wenn er muss: wenn er seinen Punkt, einen anderen Hinweis oder einen Tastenhinweis verdecken
  würde, aus der Ansicht ragt, sein Objekt zu sehr verdeckt oder die Zeigelinie deutlich länger geworden ist. Dann gleitet
  er in 0,4–1,2 s an die neue Stelle, bevorzugt auf derselben Seite. Muss er auf die andere Seite, blendet er an der alten
  Stelle aus und an der neuen ein, statt quer über seinen Punkt und sein Objekt zu gleiten; zurück auf eine eben verlassene
  Seite geht er nur ungern. Hinweise an fliegenden Drohnen gehen ihrer Bahn einmal
  aus dem Weg (meist hinter die Drohne), statt vor ihr hergeschoben zu werden.
- Tastenhinweise zählen als Hindernis: Hinweise legen sich nicht mehr über sie. Ein Hinweis verdeckt auch nicht den Punkt
  eines anderen Hinweises und steht immer näher an seinem eigenen Punkt als an einem fremden, damit klar bleibt, welcher
  Kasten zu welchem Objekt gehört.
- Ein neuerer Hinweis an derselben Stelle und auf derselben Seite löst den älteren ab. Vorher blieb am Ende der Lektion
  „Rahmenformen im Vergleich“ von Just More Parts die Erklärung „H-Frame: Kamera weit vorn“ neben dem Namensschild
  „H-Frame“ stehen; jetzt zeigt das Schlussbild nur noch die fünf Namensschilder.
- „Fertig“ zeigt in jeder Lektion das Schlussbild des letzten Schritts: dessen bis zum Schluss offene Hinweise bleiben
  ruhig stehen. Vorher blendeten sie in der letzten Viertelsekunde aus und sprangen bei „Fertig“ wieder auf (womöglich an
  anderer Stelle). Die Lektion „Gates, Kurs und Rennen“ endete als einzige ohne Hinweis; ihr letzter Hinweis („Sturzflug:
  das Tauchtor zählt nur abwärts“) bleibt jetzt ebenfalls stehen.
- Bei GUI-Skala 1 (doppelte Textgröße) wachsen auch die Abstände der Hinweise mit (Zeigelinie, Abstand zum Objekt).
- Einzelheiten: [`FOUNDATION.md`](FOUNDATION.md) „Hinweis-Layout“ unter Flugschule.

### Bindung bleibt in der Drohne, die Funke prüft die Brille
- Binden heißt: die Drohne mit der Funke anklicken. Der Empfänger jeder Drohne merkt sich, wer sie zuletzt gebunden
  hat. Die Bindung steckt in der Drohne, im Drohnen-Item und in der Werkbank am Flugcontroller: Aufheben, Absetzen,
  Umbau (Props, Motoren, Rahmen, Akku, Video, Zubehör, Lack, Tuning, Name), Item-Rahmen, Truhen, Weltspeicher und
  Server-Neustart lassen sie stehen. Ein neuer Flugcontroller bringt einen neuen Empfänger mit – dann wird neu
  gebunden. Wer eine Drohne mit einem Freund teilen will, lässt ihn sie mit seiner
  Funke anklicken.
- Brillen werden nicht mehr gebunden. Die Funke prüft beim Binden und beim Start, welche Brille du trägst: Empfängt
  sie das Videosystem der Drohne (analog zu analog, digital zu digital, Kreativ-Brille zu allem), fliegst du FPV. Ohne
  Brille fliegst du im Sichtflug. Mit einer Brille, die das Videosystem nicht empfängt, lehnt die Funke ab und nennt
  beide Systeme („Deine Brille empfängt analog, … sendet digital“).
- Im Flug gilt dasselbe: Brille absetzen schaltet in den Sichtflug, passende Brille aufsetzen ins FPV. Setzt du eine
  falsche Brille auf, bleibt die Drohne steuerbar, aber die Brille zeigt kein Bild (analog Rauschen, digital schwarz,
  „KEIN SIGNAL“ mit beiden Systemen), bis du sie absetzt.
- Ein Rechtsklick mit der Brille in der Hand auf eine Drohne sagt nur noch, ob sie passt. Brillen aus 0.4.0 verlieren
  ihre alte Bindung von selbst und funktionieren mit jeder passenden Drohne.
- Funke in die Luft: Fehlt ihre Drohne (andere Id, Kopie, frisch abgesetzt) oder ist die Funke noch ungebunden, verbindet
  sie sich mit der nächsten Drohne im Umkreis von 128 Blöcken, die du zuletzt gebunden hast („der Empfänger hat dich
  erkannt“).
- An einen anderen Piloten gebundene Drohnen lehnen alte Funken ab („zum Übernehmen mit der Funke anklicken“); solange
  ein anderer Spieler eine Drohne fliegt, lässt sie sich nicht binden. Drohnen aus 0.4.0 verhalten sich wie bisher, bis
  man sie einmal bindet.
- Was die Bindung schützt und was nicht: Aus der Ferne verbindet sich niemand mit einer Drohne, die du gebunden hast,
  weder mit einer alten Funke noch über die Suche. Wer die Drohne erreicht, kann sie dagegen mit seiner Funke anklicken
  und neu binden (wie der Bind-Knopf am echten Empfänger) oder im Wartungsmenü „RX“ zurücksetzen, solange niemand sie
  fliegt. Das ist Absicht (`design/receiver-binding.md` §7): Drohnen, deren Pilot nicht mehr da ist, bleiben nutzbar,
  und wer an die Drohne kommt, könnte sie ohnehin aufheben.
- Wartungsmenü: Feld „RX“ neben dem Inventar zeigt die Bindung (grün: du, gelb: fremd, grau: ohne oder zurückgesetzt);
  zwei Klicks setzen sie zurück. Tooltips von Drohne und Flugcontroller nennen die Bindung, die Flugschul-Lektion heißt
  jetzt „Funke binden, Brille aufsetzen“ (Funke binden, Brille aufsetzen, die Brille muss passen, Bindung bleibt,
  Losfliegen).
- Video-Tab: Der Umschalter „Brille“ ist entfernt. Er hat nie die wirksame Brille geändert, sondern nur ausgewählt,
  welche Einstellungen der Tab zeigt. Jetzt zeigt der Tab genau die Einstellungen der Brille, die du trägst, und ohne
  Brille einen kurzen Hinweis. Die Kreativ-Werte (eigenes Sichtfeld, Latenz, Reichweite, Rauschen, Abschattung) wirken
  wie bisher nur mit getragener Kreativ-Brille; aus einer normalen Brille lässt sich über keine Einstellung eine
  Kreativ-Brille machen.
- Netzprotokoll 15. Einzelheiten:
  [`design/receiver-binding.md`](design/receiver-binding.md).

### Beitritt: Übersicht je Add-on statt Teile- und Registerlisten

- Passt beim Verbinden etwas nicht, zeigt der Client das Fenster „Add-ons passen nicht zum Server“: je Add-on Name,
  Mod-Id, Version auf dem Server und bei dir, Status („Fehlt bei dir“, „Nicht auf dem Server“, „Ältere Version“, „Andere
  Version“, „Andere Bauteildaten“), die Aktion („Installiere Propwash: Just More Parts 1.1.0.“, „Aktualisiere … auf …“,
  „Entferne … oder bitte den Server-Betreiber, es zu installieren.“), die Zahl der betroffenen Teile und den Link aus den
  Metadaten des Add-ons (öffnet erst nach Minecrafts Rückfrage). Eine andere Propwash-Version steht als eigene Karte mit
  beiden Versionen und Netzprotokollen da, Teile ohne bekanntes Add-on als „Unbekannte Teile (<namensraum>)“.
  Unterschiede, die Propwash erlaubt (z. B. ein HUD-Add-on nur bei dir), stehen gesondert unter „Außerdem anders“.
  „Details kopieren“ legt die Übersicht als Text in die Zwischenablage; das Server-Log enthält dieselbe Zusammenfassung.
- Vorher trennten bei fehlendem Add-on zuerst die Loader: Fabric mit „Received 151 registry entries that are unknown to
  this client … namespaces: justmoreparts, propwash_example“, NeoForge mit einer langen Liste von `ResourceKey[minecraft:item
  / justmoreparts:…]`; bei anderer Add-on-Version nannte Propwash nur Teile-Ids ohne Add-on und Version. Jetzt prüft
  Propwash in der Konfigurationsphase vor dem Registerabgleich beider Loader (Fabric: eigene Phase vor Fabrics
  Register-Sync; NeoForge: Aufgaben vor `SyncRegistries`).
- Trennt nach Propwashs Prüfung trotzdem der Loader (Add-on ohne Teile, aber mit eigenen Items), erscheint die Übersicht
  mit der Original-Meldung darunter. Ein Fabric-Client ganz ohne Propwash bekommt „This server requires Propwash FPV
  0.5.0 (network protocol 13) … Propwash add-ons on this server: …“.
- Grenzen: Ein anderes Netzwerkprotokoll meldet auf NeoForge weiter NeoForges eigener Kanal-Abgleich („Channel of mod
  "Propwash FPV" … version 12 … version 13“); eigene Pflicht-Kanäle eines Add-ons ebenso.
- Einzelheiten, Fälle vorher/nachher und Sicherheit: [`design/join-check.md`](design/join-check.md); für Add-on-Autoren
  [`api/addon-guide.md`](api/addon-guide.md) Abschnitt 1.3.

### Beitritt mit Add-ons, die der Server nicht hat

- Hat nur der Spieler ein Add-on mit Teilen (z. B. Just More Parts auf einem Server ohne), tritt er jetzt bei. Das Add-on
  ist bei ihm für die Sitzung inaktiv: Teile, Teil-Items, eigener Kreativ-Tab samt Suche, Presets, Werkbank-Knöpfe,
  Konfigurator-Tabs und Flugschul-Lektionen des Add-ons sind ausgeblendet, Baupläne mit seinen Teilen zeigen Platzhalter
  wie auf dem Server. Ein Toast sagt „Propwash: Just More Parts ist auf diesem Server nicht installiert und für diese
  Sitzung inaktiv.“ Nach dem Trennen (Einzelspieler, anderer Server) ist alles wieder aktiv.
- Über inaktive Teile geht nichts an den Server: Kreativ-Pakete mit solchen Items (gespeicherte Schnellleiste, Rezept-
  Viewer) leert Propwash mit Hinweis, Importe antworten lokal „unbekannte Teile“, ein Drohnen-Code `PWD1-` nennt die
  Teile, die es auf dem Server nicht gibt. Items aus einer anderen Welt zeigen „Auf diesem Server nicht verfügbar“.
- Weiter Pflicht: Add-ons, die nur der Server hat, und andere Teiledaten eines Add-ons beider Seiten (auch zusätzliche
  Teile beim Spieler). In der Übersicht steht ein Add-on nur beim Spieler unter „Außerdem anders“ mit „Der Server hat es
  nicht – Propwash lässt dich trotzdem beitreten, … bleibt dort für diese Sitzung inaktiv.“, das Server-Log nennt die
  Zahl der inaktiven Teile.
- Behoben: Haben Server und Spieler dieselben Add-ons, trennte Propwash mit „andere eingebaute Teile“, wenn zwei Add-ons
  eigene Zellchemien mitbringen und die Loader sie in anderer Reihenfolge laden (Fingerabdruck sortiert die Zellchemien
  jetzt nach Id). Mehrzeilige Propwash-Toasts überdecken sich nicht mehr mit anderen Toasts.
- Loader-Ebene mit zwei Prozessen geprüft: Fabrics Register-Sync hängt Einträge nur beim Spieler mit eigenen Nummern an,
  NeoForge lässt sie ohne Netz-Nummer im Register; beide trennen nicht. Just More Parts und das Beispiel-Addon haben
  keine eigenen Kanäle. Einzelheiten: [`design/client-only-addons.md`](design/client-only-addons.md), Regeln für
  Add-on-Autoren: [`api/addon-guide.md`](api/addon-guide.md) Abschnitt 17.1.

### Steuerung mit Maus + Tastatur
- Neue Eingabequelle „Maus + Tastatur“ im Tab „Empfänger“ und im Empfänger-Assistenten (eigener Schritt mit
  Vorführung an der Funke und Testfläche). Die Maus lenkt wie Zielen in einem Shooter: In Angle hält sie die Neigung
  (Halten), in Acro wird die Mausgeschwindigkeit zur Drehrate (Tempo), „Automatisch“ wählt je Flugmodus; Maus links/rechts
  wahlweise als Rollen oder Gieren. Gas mit `W`/`S` oder dem Mausrad (Schritt einstellbar, 0 = aus), dazu eine
  Zentrieren-Taste.
- Zehn Einstellungen: Modus, Maus links/rechts, Empfindlichkeit, DPI, Glättung, Totzone, Expo, Rückstellung, Y umkehren,
  Mausrad-Schritt. Gleicher Tischweg gibt bei 400 und 1600 DPI denselben Ausschlag. Virtuelle Knüppel und eine
  Testfläche (Klicken und Ziehen, Umschalter Angle/Acro) zeigen live, was ankommt.
- Keine zusätzliche Verzögerung um ein Bild: Die Mausereignisse werden mit Zeitstempel im selben `pollEvents` gelesen wie
  Funke und Gamepad. Nur die Standard-Glättung (20 ms, Dreiecksfenster) verzögert Tempo-Ausschläge im Mittel um etwa
  10 ms; sie lässt sich auf 0 ms stellen.
- Flugtraining: Mit gewählter Maus zeigen acht Lektionen einen eigenen Tipp.
- Netzprotokoll und Parts-API unverändert (die Maus meldet sich als Tastatur-Quelle mit dem Gerät `mouse`). Handbuch
  [`mouse-control.md`](mouse-control.md), Entwurf [`design/mouse-control.md`](design/mouse-control.md).

### Fluggefühl: Videolatenz, Kamera-Vibration, Treffer, Drohnengröße
- Videolatenz zählt nur einmal: `latency_ms` eines Videomoduls ist die Glas-zu-Glas-Latenz des echten Systems; bis 0.4.0
  kam sie zusätzlich auf den Darstellungsweg des Spiels. Jetzt zieht `video.DisplayLatency` den gemessenen Teil ab
  (Zeichnen bis `present`, gleitend über 1 s gemittelt, dazu die halbe Bildwiederholzeit des Monitors). Gemessen im
  Fabric-Selbsttest, Gesamtlatenz im Spiel vorher → jetzt: System 12 ms 23,1 → 11,2 ms, 26 ms 37,1 → 25,2 ms,
  30 ms 41,1 → 29,2 ms (NeoForge 12,2 bzw. 30,1 ms). Video-Tab, Statuszeile und Rennwertung nennen weiter die
  Systemlatenz.
- Kamera-Vibration (Tab „Video“, 0–200 % in 10er-Schritten, Standard 100 %, 0 % = aus, persönlich): leises Motorzittern
  (mit verbogenem oder angebrochenem Prop deutlich stärker und mit der Drehzahl wachsend), Buffeting im eigenen
  Prop-Wash und im langsamen Flug nahe Boden oder Wand, ein Ruck bei hartem Punch-out. Nur im Bild: Sim, OSD, Blackbox,
  Rennwertung, Trick-Erkennung und Flashback-Kamerapfad sehen keine Vibration; die Stabilisierung der Heavy-Cam glättet
  sie, die Light-Cam behält sie.
- Bei hohem Tempo bleibt das Bild ruhig, wie bei einem sauber gebauten echten Copter (gemessen, `design/fpv-feel.md`
  §3.1): das Flugmodell schwingt bei Vollgas nicht (Drehrate 0,07 °/s RMS, ruhiger als im Schweben) und bleibt
  unverändert. Gewackelt hat die Kamera-Vibration – das Motorzittern stieg mit der Drehzahl im Quadrat auf das
  5,5-Fache (Vollgas 0,105° RMS, jetzt 0,020° wie im Schweben), Gates und Wände schüttelten auch bei 40 m/s, und schon
  normale Gasarbeit löste den Ruck des Punch-outs aus. Dazu trug die Kamerapose die Zeit des letzten Sim-Schritts statt
  die des Bilds (0–0,5 ms daneben, bei 49 m/s bis 24 mm entlang der Flugbahn, jetzt 0).
- Treffer-Neigung und Laufwackeln des Piloten wirken nicht mehr auf das FPV-Bild (vorher kippte es um bis zu 14°, wenn der
  Pilot getroffen wurde).
- Drohnengröße (Tab „Darstellung“): Standard 1,0× = echte Größe statt 1,75×, damit Tempo und Abstand echt wirken. Wer
  noch den alten Standard 1,75× gespeichert hatte, wird einmal auf 1,0× umgestellt und bekommt in der Welt einen Hinweis,
  wo er die Größe zurückstellt (Konfig-Version 2); jeder andere gespeicherte Wert bleibt.
- Entwurf, Messungen und Entscheidungen: [`design/fpv-feel.md`](design/fpv-feel.md).

### Behobene Fehler
- **Live-Telemetrie (gemeldet mit Bildschirmfoto):** Die Motorbalken M1–M4 im Tab „Übersicht“ lagen bei schmaler Spalte
  (einspaltige Telemetrie, z. B. GUI-Skala 3 im 1920×1080-Fenster) unter dem Rahmen des Panels; Höhe und Balkenlage
  kommen jetzt aus einer gemeinsamen Rechnung (`design/blackbox.md`, Abschnitt „Übersicht“).
- **Flugmodus (gemeldet seit 0.3.2):** R wechselte manchmal nicht, die Drohne blieb in Horizon, obwohl Angle und Horizon
  aus waren, und die Acro-Taste wirkte neben einer Funke nicht. Ursachen: Ohne aktiven Modusschalter galt ein verdeckter
  Tasten-Modus (aus R, den Modus-Tasten oder „Start in Angle“ einer früheren Tastatur-Sitzung), der über Sitzungen
  blieb; ein aktiver Schalter überstimmte die Tasten stumm, obwohl die Meldung den neuen Modus nannte; die Vorbelegung
  für Funken legt Horizon auf die Mitte von Kanal 6, und ein Kanal ohne Mischer steht dort (1500 µs); gehaltenes R
  zählte mit der Tastenwiederholung des Systems mehrfach. Jetzt zählt die letzte Eingabe: Ein Wechsel der Modusschalter
  setzt deren Modus (Angle und Horizon aus = Acro, Belegung gelöscht = Acro), die Tasten setzen den Modus sofort und
  gelten, bis der Schalter das nächste Mal bewegt wird; R schaltet vom geflogenen Modus weiter. Weicht der Schalter ab,
  nennt die Meldung beide („Modus: Acro (Taste – Funkenschalter: Horizon)“), der Modi-Tab zeigt im Flug den geflogenen
  Modus wie das OSD. Wer nur Schalter oder nur Tasten benutzt, merkt keinen Unterschied. Umschalt-Tasten (R,
  Modus-Tasten, Scharf schalten, Höhe/Position halten, Suchpiepser, LEDs, GPS Rescue, Homepoint setzen, Aufnahme der
  Action-Cam bzw. Flashback und Zuschauen) lösen beim Halten nur einmal aus; vorher schaltete gehaltenes J den Heimflug
  im Takt der Tastenwiederholung ein und aus.
- **Schwarzes FPV-Bild mit LEDs (gemeldet mit Intel UHD 620 unter Windows):** Bei Nacht und eingeschalteten LEDs wurde
  das ganze FPV-Bild schwarz. Der Scheinwerfer-Shader rechnet jetzt NaN-sicher (ungültige Tiefe, Projektion oder Normale
  lassen den Bildpunkt unverändert, das Ergebnis ist nie dunkler als das Bild ohne Licht), und eine Bildprüfung liest
  ab und zu ein schmales Band des Bildes vor und nach dem Pass zurück; wird es dunkler, schaltet sich der Shader-Weg
  mit einer Logzeile samt Grafik ab und die einfache Aufhellung im Overlay übernimmt. Neue persönliche Einstellung
  „LED-Ausleuchtung“ im Tab „Video“ (Standard an) schaltet den Effekt im eigenen FPV-Bild ganz ab. Beim Start steht die
  Grafik einmal im Log. Wo der Pass schon lief, sieht das Bild gleich aus. Details:
  [`design/led-strip.md`](design/led-strip.md) „Schwarzes Bild auf manchen Grafiktreibern“.
- **Gas-Richtung im Empfänger-Assistenten (Restfall aus 0.4.0):** Gas unten, einmal ganz runter, dann hoch und oben
  ≥ 1,5 s stehen lassen speicherte das Gas invertiert (dieselben Rohwerte wie eine invertierte Funke, die richtig bedient
  wird). Jetzt prüft der Assistent die gehaltene Richtung gegen die Startlage (Gas beim Start an einem Ende) und gegen die
  bisherige Richtung derselben Achse im Profil (bei neuen Funken die Vorbelegung, nicht invertiert); widerspricht eins
  davon, fragt er „Wo steht dein Gas gerade?“ mit „Ganz UNTEN“/„Ganz OBEN“, statt zu raten. Damit fragt er auch, wenn das
  Gas oben startet und oben gehalten wird, und eine invertierte Funke fragt beim ersten Einrichten einmal nach (ihre
  Rohwerte gleichen genau diesem Bedienfehler). Ohne Profil-Richtung für die erkannte Achse und ohne Start unten
  entscheidet weiter die Bewegung allein; ein falsch herum gespeichertes Gas sperrt dann das Scharfschalten und die
  Prüfansicht zeigt es. JUnit mit allen Fällen des Prüfers und je 400 Zufallsfolgen ohne und mit Profil-Richtung.
- **Flugschule „Akku tauschen und laden“ (Rückmeldung eines Creators):** Der Hinweis „6S 4000 mAh (Li-Ion): zu schwer –
  abgelehnt“ stand neben dem Ladegerät und las sich wie „das Ladegerät nimmt keine Li-Ion-Akkus“. Der Schritt „Welcher
  Akku passt zum Rahmen?“ zeigt jetzt die Drohne groß und ohne Ladegerät: Der Li-Ion-Akku (420 g) fliegt an die Drohne,
  der Rahmen leuchtet auf, das Sperrzeichen sitzt an der Drohne und der Hinweis „Der Rahmen trägt höchstens 230 g Akku –
  zu schwer, die Drohne lehnt ihn ab“ zeigt auf sie; danach nennt ein Hinweis die Welt-Regel „Teile müssen
  zusammenpassen“, mit der auch dieser Akku passt. Im Schritt „Laden im Ladegerät“ legt der Pilot einen LiPo-, einen
  Li-Ion- und einen LiHV-Akku ein („Lädt jeden Akku: LiPo, LiHV und Li-Ion“). Die Gewichte im Text prüft JUnit gegen die
  Teile-Daten. Zahl und Einheit („230 g“, „1300 mAh“) trennt ein geschütztes Leerzeichen, der Zeilenumbruch fällt nie
  dazwischen.
- **Flügelförmiger Schatten mit Shaderpacks (gemeldet mit Complementary über Iris):** Im FPV-Flug lag tief über Wasser
  oder Boden ein dunkler, flacher Schatten im Bild. Iris zeichnet im Schattenpass alle Entities, auch die Kamera-Entity;
  die eigene FPV-Drohne lief dort durch die Props im FPV-Bild, deren Geometrie an der Linse hängt und deren Scheiben
  bis zu viele Meter quer vor die Linse gezogen sind (im Bild deckungsgleich, in der Welt eine dünne Fläche). Jetzt
  zeichnet der Schattenpass nie kamerabezogene oder reine Licht-Geometrie (LED-Halos, Motor-Glühen); die eigene Drohne
  wirft stattdessen ihren echten Schatten in Originalgröße an der Pose des angezeigten Bildes (mit Video-Latenz). Der
  persönliche Schalter „Schatten“ (Tab „Darstellung“) nimmt jetzt auch die Shader-Schatten aller Drohnen heraus.
  Die Heimatmarke war mit Shaderpack im FPV unsichtbar (durchscheinende Gizmo-Linien haben in Iris 1.11.7 kein
  Programm) und wird mit Shaderpack deckend gezeichnet. Details: [`design/shader-packs.md`](design/shader-packs.md).
- **Gates hielten Druckplatten gedrückt:** Die Entity-Box eines Gates (bei Freiform-Gates die ganze Öffnung, eine
  Blockschicht tief) zählte für Holz- und Wägeplatten und Stolperdrähte wie ein Körper. Einmal betreten, blieb eine
  Druckplatte in der Öffnung gedrückt und eine Tür daneben offen. `GateEntity#isIgnoringBlockTriggers` ist jetzt
  `true` wie bei Markierungen und Anzeige-Entities; Gates, Kurs-Elemente und Freiform-Gates lösen keine Druckplatten
  und Stolperdrähte mehr aus. Der Selbsttest „Freiform-Gate setzen …“ prüft beides im Gang.
- **Empfänger-Tab stürzte ab (gemeldet mit Radiomaster Boxer unter Fabric):** Mit einer Funke, deren Name samt Status
  nicht in die Statuszeile passt, zeigte der Tab „Empfänger“ nur „Dieser Tab ist abgestürzt“, sobald die Maus über der
  Statuszeile lag. Der Tooltip der gekürzten Zeile sollte den ganzen Text zeigen, bekam aber die Meldung der letzten
  Aktion, und die ist ohne Meldung `null` (`NullPointerException` in `GuiGraphicsExtractor#setTooltipForNextFrame`).
  Jetzt zeigt der Tooltip den ganzen Status, umbrochen auf 220 px; eine Meldung zeigt er wie bisher immer. Gegen diese
  Fehlerart insgesamt zeichnet Propwash Tooltips nur noch über `ThemeText.tooltip`/`tooltipLines`/`tooltipWrapped`/
  `tooltipSequences` und gibt Widgets ihren Tooltip über `ThemeText.tip`: fehlender oder leerer Text ergibt keinen
  Tooltip statt eines Absturzes, `null`-Zeilen fallen weg. Geprüft wurden alle Tooltip-Aufrufe in Propwash und Just More
  Parts (dort nur feste Übersetzungsschlüssel, keine Änderung nötig); die Stelle im Empfänger-Tab war die einzige mit
  möglichem `null`. Stürzt doch einmal ein Tab ab, kopiert ein Klick auf die Fehlermeldung einen kurzen Bericht
  (Propwash-Version, Loader, Tab, Ausnahme mit höchstens 24 Zeilen Stack und 4 Ursachen) in die Zwischenablage; die
  Meldung im Log bleibt. Neuer Selbsttest „ui: Konfigurator-Hover“: öffnet jeden Konfigurator-Tab ohne und mit einer
  simulierten Funke mit sehr langem Namen, bei Fenstergröße, 427 × 240 und 960 × 540, fährt die Maus in 6-px-Schritten
  über das ganze Fenster und bis zu sechs gescrollte Seiten je Tab und schlägt bei jedem Tab-Absturz fehl; ein eigener Schritt prüft,
  dass der Tooltip der Statuszeile den ganzen Funkennamen zeigt. Mit dem alten Code schlägt er genau mit der gemeldeten
  Ausnahme fehl. JUnit `TooltipCallsTest` verbietet direkte `set…TooltipForNextFrame`-Aufrufe außerhalb von `ThemeText`.

### Realismus-Feinschliff: Props, Whoop, Gewicht
- Prop-Verschleiß nach Material ([`design/realism/prop-wear.md`](design/realism/prop-wear.md)): elastische Grenze (leichte
  Rempler und Streifschläge kosten nichts), Strike-Last nach Drehzahl² × Anfluggeschwindigkeit statt fester 6 % je
  Strike, Bruchverhalten `sim.PropFailure` aus Steifigkeit und Haltbarkeit der Prop-Daten (PC verbiegt sich, hartes PC und
  Glasfaser-Nylon brechen in Stücken, Bi-Blend dazwischen, Carbon zerspringt selten und gibt den Stoß an den Motor weiter).
  Ein PC-Prop wird im Mittel nach 17 statt 8 Crashs kritisch; Welt-Regeln Crash-Schaden und -Stärke unverändert. Im Flug
  kostet ein verbogener Prop mehr Schub, ein angebrochener mehr Vibration. Tooltip-Zeile „Absturz: …“ für jeden Prop.
- Weniger „floaty“ ([`design/realism/flight-feel-gravity.md`](design/realism/flight-feel-gravity.md) §10, alle Zahlen
  dort): Messlauf `sim.ThrottleDropCharacterizationTest` (Gas weg auf 0 und 20 % für einen Meteor-ähnlichen Whoop aus
  Herstellerdaten, Whoop 65, Cinewhoop, 5″, Just More Parts Whoop 75/85) gegen ein Referenzmodell aus Prüfstand,
  Windkanal-Daten zum Wirbelring und Rumpf-/Blattflächen. Zwei Fehler gefunden, alles andere bestätigt (Zeitmaßstab,
  Schwerkraft je Substep, Schwebegas, Idle, Gaskurve, Schub, Motorträgheit, Bodeneffekt):
  - Whoop-Rahmen: Widerstand von oben nach „1,2 × Rumpf + 1,66 × Blattfläche“ statt nach dem ganzen Umriss
    (`propwash:whoop_65` `aero.top_area_m2` 0,00875 (0.4.0) → 0,0041 m²; die Umriss-Regel vom 05.10. mit 0,0059 zählte die
    Duct-Scheiben als volle Platte). Fall nach 1 s 5,98 (0.4.0) → 7,30 m/s, flach 6,8 → 9,9 m/s, Topspeed 36 → 48 km/h (Werkbank; im Flug gemessen 47,2 km/h).
  - Flug-Engine: Im angetriebenen Sinkflug entfallen 40 % des Widerstands von oben (der Anteil der Blätter, den das
    Rotormodell dort schon rechnet), solange die Drohne langsamer als das 1,5-fache der induzierten Geschwindigkeit
    sinkt, weich zurück bis zum 3-fachen. Sinken mit 20 % Gas: 5″ 7,4 → 8,3 m/s (nur Engine), Whoop 65 7,1 → 8,5 m/s (beides);
    Leerlauf-Fall, Vorwärtsflug, Topspeed, Schweben und Flugzeiten unverändert.
- Schub im Steigflug nach Impuls- und Blattelementtheorie ([`design/realism/flight-feel-gravity.md`](design/realism/flight-feel-gravity.md)
  §11, [`tools/blackbox-calibration.md`](tools/blackbox-calibration.md)): Der Abgleich mit 19 echten Betaflight-Logs
  (Whoop bis 7″) zeigte, dass die Sim beim Steigen für alle Größen zu viel Schub verliert (echt/Sim 1,03–1,36 bei
  1,5–3,5 m/s). Bisher fiel der Schub linear mit `λ = V/V₀`; jetzt sinkt die induzierte Geschwindigkeit beim Steigen mit
  (Anfangssteigung `−r/(2r − 1)` statt `−1`, `r = V₀/v₀`, neue Größe `AirframeSpec.inducedPerOmega`), im Vorwärtsflug
  nach Glauert ausgeblendet (`w = 1 − μ²/√(μ⁴ + 4)`, `μ = V_quer/v_h`). 5″ jetzt 0,97–1,00. Punch 1 s aus Schweben
  +5–9 % (5″ 36,3 → 38,7 m/s), Sturzflug mit Vollgas +3–5 %, Topspeed und Reise +0–1 %; Schweben, Standschub, Sinkflug
  und Fall bitgleich bzw. unverändert. `TopSpeedTest` Sturzflug-Grenze Cinewhoop 1,15 → 1,2 × Geradeausflug (Schub/Gewicht
  4,7, wie der Whoop mit 1,25 ×). Just More Parts 1.1.0 nimmt c_p der 89-mm-Props × 1,012, damit `toothpick35_6s` in
  seinen Fenstern bleibt.
- Werkbank (Kennzahlen Trockengewicht und Abfluggewicht; Maximalstrom in den Details), Konfigurator-Übersicht und
  Drohnen-Tooltip zeigen das Gewicht ohne und mit Akku. Prüfung des typischen 7″ ohne Datenänderung
  ([`design/realism/builds.md`](design/realism/builds.md) §17a).
- Flugschule „Props wechseln und Schäden“ mit dem Schritt „Das Material entscheidet“.

### Teile in der Hand
- Bisher war jedes Teil in der Hand gleich groß (Vorderansicht 9 px in der ersten, 7 px in der dritten Person): ein
  2207-Motor stand in der ersten Person 0,56 Block breit im Bild, breiter als ein gehaltener Steinblock (0,40), ein
  10-mm-Whoop-Motor genauso. Jetzt richtet sich die Größe nach der echten Größe des Teils: 9 bzw. 7 px × Handfaktor
  f = (L / 180 mm)^0,6, begrenzt auf 0,25–1 (L = längste Kante). Motor 2207 0,18 (dritte Person 0,14), 1103 und 0802
  0,14 (0,11), 4214 0,26 (0,20); Nano-Kamera, Greifer, Action-Cams, Whoop-Props, Whoop-Rahmen, Stacks und Video werden
  ebenso kleiner, Rahmen und Props ab 180 mm bleiben gleich. Akkus behalten in der ersten Person ihre Größe vom 30.09.
- Gemessen wird für beide Hände (Zweiblatt-Props lagen in der linken Hand quer und 0,88 Block breit, jetzt 0,54); in der
  dritten Person sitzen kleine Teile in der Faust. GUI, Boden, Item-Rahmen, Regal und Kopf sind unverändert.
- Symbole ohne Modell (`propwash:part`) nehmen in den Händen `"hand": true`. Just More Parts ist mit derselben Regel neu
  erzeugt. Selbsttest und JUnit prüfen Teile in der Hand gegen die Grenze ihrer echten Größe, die alten Motorgrößen fallen
  dabei durch; Werte und Grenzen in [`ASSETS.md`](ASSETS.md), „Größenregel der Items“.

### Drohnen-Ton für Zuschauer und Hörweite
- Bisher schwächte Minecraft den Motorton fremder Drohnen linear ab (Live-Sound 48 × Größenfaktor Blöcke, Ersatz-Sound
  immer 96): in halber Distanz nur −6 dB, ein 75-mm-Whoop war so bis rund 60 Blöcke zu hören. Jetzt rechnet Propwash
  den Pegel selbst nach dem Abstandsgesetz: voller Pegel bis 3 Blöcke, dann −6 dB je Verdopplung, ab der halben
  Hörweite weich bis 0. Die Hörweite wächst glatt mit der Propgröße (18 dB je Dekade, 5″ = 80 dB(A) in 1 m); der Pegel
  aus Wellenleistung und Blattspitzen-Mach verschiebt sie nur um höchstens ±1 dB. Schwelle 43 dB(A); mit Gas wächst sie
  bis zum 1,7-Fachen, höchstens 160 Blöcke. Schwebend: 65-mm-Whoop 19 Blöcke, 75-mm-Whoop (Just More Parts) 22,
  Cinewhoops 42–49, 5″ Race 63, 5″ Freestyle 70, 7″ 92, X-Class 13″ 160. Hohe Töne werden mit der Entfernung leiser
  (Luftdämpfung nach ISO 9613-1).
- Fahrtwind an der Kamera und Rumpeln von Rahmen und Mikrofon hört nur noch der Pilot im FPV. Wer die Drohne von außen
  hört (andere Spieler, Pilot auf Sicht), hört Prop-Wash ein Viertel so laut und nur beim Sinken ohne Vorwärtsfahrt;
  ein schneller Sturzflug nach vorn zählt nicht mehr als Prop-Wash. Der Klang im FPV ist bitgleich.
- Live-Sound zuerst für die am besten hörbaren Drohnen (Entfernung im Verhältnis zur Hörweite), außer Hörweite meldet
  der Live-Sound Lautstärke 0 (kein Untertitel) und endet ab 1,15-facher Hörweite + 4 Blöcke.
- Messwerte, Quellen, Tabelle aller Bauweisen und Entscheidungen: [`design/drone-sound.md`](design/drone-sound.md).

### Kompatibilität
- Netzprotokoll 15 (Welt-Regeln samt Voreinstellungen, Treffer und Greifer, GPS Rescue ohne Pilot, Kreativ-Drohne, Gates frei drehen,
  Kurs-Elemente, Freiform-Gate, Empfänger-Bindung, Beitritts-Prüfung `propwash:join_info`, Redstone-Signal der Gates,
  Runden je Kurs und Kurse bearbeiten):
  Client und Server brauchen beide 0.5.0. Parts-API 1.5.0, Addons für 1.0–1.4 laufen unverändert
  ([`api/changes.md`](api/changes.md)).
- Welten, Drohnen, Gates, Kurse und Einstellungen aus 0.4.0 laden unverändert; Prüfsummen und Bestenlisten bestehender
  Kurse bleiben gültig. Mit den Standardwerten der Welt-Regeln fliegen Drohnen wie in 0.4.0 bis auf die Änderungen am Flugmodell
  aus „Realismus-Feinschliff“: Alle Größen steigen etwas kräftiger (Steigflug-Schub nach Impulstheorie, Punch +5–9 %) und
  sinken unter Gas etwas schneller (Sinkflug-Widerstand), der Rahmen `propwash:whoop_65` fällt und fliegt schneller
  (Draufsicht). `calibration.txt` und `motion.txt` ändern sich deshalb in allen Zeilen mit Steig- oder Sinkflug, Schweben
  und Flugzeiten im Schwebeflug bleiben gleich.
- Die persönlichen Physik-Einstellungen des alten Tabs „Simulation“ werden ignoriert; wer sie geändert hatte, stellt sie
  als Welt-Regeln neu ein. `"partWear": false` in `server.json` wird einmal als `propwash:crash_damage false`
  übernommen.
- Bandos erscheinen nur in neu erzeugten Chunks.

---

## 0.4.0

Fasst die internen Stände 0.4.0–0.4.4 und die Ergänzungen bis zum 02.10.2026 zusammen ([`RELEASING.md`](RELEASING.md)
§1.2). Flugmodell: Jede eingebaute Drohne fliegt wie in 0.3.2, außer dem eingebauten Cinewhoop (Rahmendaten
korrigiert) und dem Akku, der jetzt so schnell leer wird wie ein echter.

### Teile aus Addons
- Andere Mods können Rahmen, Stacks, Motoren, Props, Akkus, Videoeinheiten und Zubehör hinzufügen. Ihre Teile bekommen
  Items, Shift-Tooltips, Werkbank-Plätze, Bauanalyse, Lack, Verschleiß, Recycling und die Flugschule und fliegen mit
  ihren eigenen Daten. Das erste Addon ist *Propwash: Just More Parts*.
- Flugschul-Taste (M) über einem Addon-Teil: „Drohne bauen“ und „Teile herstellen“ für jedes Teil, dazu Akku und
  Ladegerät für Akkus, Videosysteme für Videoeinheiten, der Konfigurator für Stacks und Props und Schäden für Props.
  Addons können weitere Lektionen verknüpfen, auch eigene.
- Jedes Addon bekommt einen eigenen Kreativ-Tab direkt hinter *Propwash FPV*, benannt *Propwash: <Addon>*, mit Presets
  und Teilen in fester Reihenfolge. Der Tab *Propwash FPV* enthält nur die eingebauten Items.
- Neue Rahmenformen: Addon-Rahmen bringen eigene Motorpositionen, Kameraposition und Schwerpunkt mit – Deadcats mit
  Props außerhalb des Bildes, gestreckte und gestauchte X, H-Frames, Ducts und X-Class mit Props bis 13″. Der
  Flight-Controller mischt nach der echten Arm-Geometrie, asymmetrische Rahmen fliegen gerade.
- Zellchemien sind offen (LiPo, LiHV, Li-Ion und mehr aus Addons), Akkus bis 12S; Stacks und ESCs haben einen
  Spannungsbereich, Motoren eine Prop-Aufnahme. Die Werkbank meldet Props, die für den Rahmen zu klein sind oder nicht
  auf den Motor passen.
- Sharecodes (in Addons): Baupläne, Lackierungen, Flug-Tuning und OSD-Layouts exportieren und importieren. Der Server
  prüft jeden Import; im Überleben kommen die Teile aus dem Inventar und der Lack kostet Farbstoffe, im Kreativmodus
  ist es frei. Nie geteilt werden Schalter- und Tastenbelegung, Funkprofile, Akkuladung, Verschleiß und Videokanal.
- Server-Beitritt: Propwash vergleicht die Teile von Client und Server und nennt fehlende oder abweichende Teile.
- Addon entfernt: fehlende Teile erscheinen als *Unknown part*, die Drohne lässt sich nicht scharf schalten, Zerlegen
  gibt für diese Teile nichts zurück. Nach dem erneuten Installieren ist sie wieder vollständig.

### Flugzeiten, Akku, Motorwärme
- Werkbank, Konfigurator-Übersicht, Drohnen-Tooltip und Addon-Werkzeuge rechnen mit der Energie, die der Akku wirklich
  liefert: Motor- und ESC-Verluste, Mehrwiderstand von Vierblatt-Whoop-Props in Teillast, echte Leistungsaufnahme des
  Videosystems, nutzbare Kapazität je Zellchemie und Landung bei der Spannung, die Piloten im ruhigen Flug ablesen.
- Vier Flugzeiten, immer in dieser Reihenfolge: *Schweben*, *Reisen* (Geradeausflug mit der typischen Geschwindigkeit
  der Klasse), *gemischt* (typisches Fliegen der Klasse mit Punches und Leerlaufphasen, Hauptwert) und *aggressiv*
  (Rennen oder hartes Freestyle).
- Beispiele: 65-mm-Whoop (0702 27000 KV, 1S 300 mAh, digital) schwebt 3:19 und fliegt gemischt 2:02 statt etwa
  7 Minuten; ein 5″-Race-Quad auf 6S 1300 hält gemischt etwa 3 Minuten; der eingebaute 7″-Long-Range auf Li-Ion schwebt
  28:28 und fliegt gemischt 15:02. Die Details zeigen außerdem Schwebe- und Mischstrom, Landespannung unter Last und die
  Grundlast von Flight-Controller und Video ([`design/realism/calibration.md`](design/realism/calibration.md),
  [`design/realism/power-video.md`](design/realism/power-video.md)).
- Im Flug folgen OSD, Funk-HUD, Übersicht, Ladestand am Akku-Item und die Warnstufen niedrig/kritisch/leer dem
  realistischen Verbrauch, einschließlich des Vollgasstroms echter Motorprüfstände für kleine Props. Ein Akku, der
  10 % unter leer gezogen wird, schaltet ab wie bei einem Brownout.
- Motorwärme: Motoren erwärmen sich unter Last; über ihrer Grenze regelt der ESC zurück, das OSD zeigt
  *MOTOR OVERHEAT* und die Motoren verschleißen. Eingebaute Drohnen bleiben im normalen Flug darunter. Die Werkbank
  zeigt die Temperatur nach 60 s Vollgas im Stand und nach einem gemischten Flug (mit Kühlluft).
- ESC-Spannungsbereich: Ein Akku mit mehr Zellen als der Stack erlaubt brennt beim Scharfschalten den ESC durch; der
  Stack gilt als kaputt und muss ersetzt werden. Eingebaute Stacks und Akkus tun das nie. Hitzeverschleiß und
  durchgebrannte ESCs folgen der Server-Regel für Teileverschleiß; beides lässt sich mit *Hardware limits* im Tab
  *Simulation* abschalten (Kreativ-Brille).

### Kamera, Sichtfeld, Props im Bild
- Eigene Props erscheinen im Kamerabild dort, wo eine echte Kamera sie sähe; drehende Props als weiche Unschärfe in
  ihrer Farbe (Schalter im Tab *Display*). Sie bleiben am Boden und nahe an Blöcken sichtbar.
- Sichtfeld aus der Kamera der Videoeinheit statt einer Einstellung je Brille. Eingebaute Einheiten: analog nano und
  race 125°, digital lite 104°, digital HD 130° (horizontal, aus echten FPV-Kameras recherchiert); Addon-Einheiten
  bringen ihr eigenes mit. Der Tab *Video* zeigt Wert und Einheit für analoge und digitale Brillen; die Creative FPV
  Goggles folgen der Videoeinheit oder nutzen ein eigenes Sichtfeld. Alte Sichtfeld-Einstellungen werden ignoriert.
- Kamerawinkel je Drohne statt der globalen Einstellung im Tab *Simulation*: einstellbar im Wartungsmenü (Schleichen +
  Rechtsklick: Regler, `−`/`+`, zurück zum Rahmen-Standard) im Bereich des Kamerahalters – Whoops 10–35°, Toothpicks
  10–40°, Cinewhoops 0–35°, Freestyle 5″ 0–50°, Race 15–70°, Long Range 0–40°, X-Class 0–50°. Das Menü zeigt die Props
  im Bild für den gewählten Winkel. Der Winkel steht auf der Drohne, bleibt beim Zerlegen am Rahmen, gilt mit jeder
  Brille und ist das, was Zuschauer und Aufnahmen sehen. Drohnen ohne eigenen Winkel nutzen den Rahmen-Standard.
- „Props im Kamerabild“ an der Werkbank rechnet mit derselben Kamera, die das Spiel zeichnet (Standard-Uptilt des
  Rahmens, Prop-Höhe, volle Unschärfe-Scheibe, Sichtfeld der Kamera), mit einer Nachkommastelle; die Warnung beginnt bei
  30 %.
- Kamerahalter: Jeder eingebaute Rahmen hat einen echten Halter. Das Objektiv sitzt in der gezeichneten Kamera wenige
  Millimeter unter den Props (Whoop: knapp über den Ducts) statt 8–22 mm über der Propebene, wo Props nur als blasse
  Ellipsen am unteren Rand erschienen und der Racer gar keine zeigte. Eingebaute Presets: Whoop 8,8 %, Cinewhoop 7,0 %,
  Freestyle 8,4 %, Racer 11,0 % (gestreckter Rahmen, Vorderprops nah an der Kameraachse), Long Range 9,5 %; der
  Race-Stretch von *Just More Parts* zeigt 11–12 %, Deadcats nichts. Jedes Preset liegt im Bereich, den echte Piloten
  für seinen Rahmentyp nennen (Whoop 5–9 %, Cinewhoop 6–9 %, True-X 6–10 %, Stretched-Race 10–15 %). Mehr Uptilt zeigt
  mehr Props, wenn das Objektiv darunter sitzt, und weniger, wenn es darüber sitzt (Whoop). Nur Darstellung und
  Analyse ([`design/realism/props-in-view.md`](design/realism/props-in-view.md)).

### Flugmodell und Physik-Optionen
- Eingebauter Cinewhoop: Der Ducted-Rahmen hatte etwa die zweieinhalbfache Stirnfläche (Draufsicht) eines echten
  Cinewhoops; Topspeed jetzt 89 km/h statt 62 km/h (echte 3″-Cinewhoops: 80–100 km/h).
- Regler *Gravity* im Tab *Simulation* von 0,80× bis 1,50× (Standard 1,00× = echte 9,81 m/s²). Mehr Schwerkraft lässt
  Drohnen schneller fallen und braucht mehr Gas zum Schweben; Schwebeschätzung, Failsafe-Landung, Höhen- und
  Positionshaltung und GPS Rescue folgen dem Wert. Wie die anderen Physik-Optionen nur im Kreativmodus oder mit der
  Creative FPV Goggles; jeder Wert außer 1,00× gilt als veränderte Physik (keine Bestenlisten-Zeiten, keine Geister).
  Werkbank, Übersicht und Flugzeiten rechnen immer mit echter Schwerkraft.
- Vorher geprüft: Fallen, Sturzflug und Sinken im Leerlauf aller Klassen gegen echte Daten (Leerlauf 5,5 % wie in
  Betaflight, flacher Fall eines 5″ bei etwa 16 m/s, Sturzflüge, Prop-Wash). Das Modell passt, die Standardphysik
  bleibt unverändert ([`design/realism/flight-feel-gravity.md`](design/realism/flight-feel-gravity.md)).

### Gates an Wand und Decke
- Jedes Gate (alle Größen, auch Start/Ziel) lässt sich auf den Boden, an Wände und unter Decken setzen; die
  angeklickte Seite entscheidet. An der Wand wird es standardmäßig ein **Tauchtor**: flach, ein Balken an der Wand, man
  taucht von oben hindurch. Weitere Wandlagen: **Seitentor** (ein Pfosten an der Wand, man fliegt an der Wand entlang)
  und **Fenster** (flach an der Wand, man fliegt durch eine ausgeschnittene Öffnung). Unter der Decke **hängt** ein Gate
  aufrecht an zwei Gurten oder liegt **flach**; auf dem Boden steht es wie bisher oder liegt flach über einer Grube.
- Wand- und Deckengates bekommen Montageplatten und Gurte statt Füßen. Gras, Blumen und andere Pflanzen zählen als
  der Boden, auf dem sie wachsen, Ranken und Leuchtflechten als Wand oder Decke dahinter; unter oberen Stufen hängt
  ein Gate direkt an der Stufe.
- Taste *Rotate gate* (Standard `R`): dreht das Gate vor dem Setzen in 90°-Schritten oder kehrt seine Flugrichtung um
  (zum Beispiel ein Tauchtor in ein Starttor, das man aufwärts durchfliegt); mit Schleichen wechselt sie die Lage. Mit
  Blick auf ein gesetztes Gate und einem Gate oder dem Kurs-Werkzeug in der Hand dreht sie dieses Gate an Ort und
  Stelle – nur wenn es passt; Kursgates bleiben in ihrem Kurs, und wie jede Kursänderung beginnt das ein neues Layout
  und archiviert die Bestenliste.
- `R` bleibt auch *Cycle flight mode*: Mit Gate oder Kurs-Werkzeug in der Hand dreht es Gates, im Flug mit der Funke
  wechselt es den Flugmodus, mit nur der Funke in der Hand (kein Gate in der anderen Hand) lässt es Gates in Ruhe. Der
  Steuerungsbildschirm markiert dieses Paar nicht als Doppelbelegung; wer eine der beiden umbelegt, gibt jeder eine
  eigene Taste (auf NeoForge zählt dieselbe Taste mit anderem Modifikator, etwa Shift+R, als umbelegt und wird als
  Konflikt markiert).
- Beim Zielen mit einem Gate zeigt eine durchscheinende **Vorschau**, wo es hinkommt, mit einem Pfeil für die
  Flugrichtung; rot, wenn kein Platz ist oder man dort nicht bauen darf.
- Rundenzählung, Server-Prüfung, Geister, Splits, Ideallinie und Gate-Markierungen funktionieren in jeder Lage; stapeln
  lassen sich nur stehende Bodengates. Flugschule (Lektion Gates) und Shift-Tooltip der Gates erklären es
  ([`design/gate-mounting.md`](design/gate-mounting.md)).

### Item-Rahmen, Regal, Item-Größen
- Eine Drohne im Item-Rahmen oder Leuchtrahmen liegt flach an der Wand wie eine Trophäe, Oberseite und Kamera zum
  Betrachter, Nase oben, statt hochkant im Rahmen zu stehen. Die acht Drehstufen drehen sie in der Wandebene; in
  Rahmen auf Boden oder Decke liegt sie flach. Drohnen ab 5″ (auch Addon-Deadcats und X-Class) füllen den Rahmen bis
  zu den Prop-Spitzen, kleinere schrumpfen wie im Inventar (65-mm-Whoop 72 %). Rahmenteile und Props hängen ebenso;
  Props waren vorher nur eine dünne Linie.
- Im Regal sind Drohnen, Rahmen und Props zum Betrachter geneigt, man sieht die Drohne statt ihrer Kante.
- Items am Boden: Akkus lagen riesig am Boden (ein 1S-Akku 1,4 Blöcke lang, 4S- und 6S-Akkus etwa 0,7 Block,
  Addon-Akkus bis 1,5 Blöcke). Jedes Propwash-Item bleibt fallengelassen jetzt innerhalb eines halben Blocks wie
  Vanilla-Items. Sperrige Teile liegen wie ein fallengelassener Block: Motoren waren fast einen halben Block in jede
  Richtung (größer als ein 5″-Rahmen daneben) und sind jetzt 0,3 Block, Action-Cams etwas kleiner.
- Fallengelassene Drohnen zeigen ihre Größenklasse: ein Whoop deutlich kleiner als ein 5″, ein 7″ größer, eine X-Class
  mit einem halben Block am größten.
- Akkus, Stacks und Videoeinheiten ragen nicht mehr bis 1,5 Blöcke aus Item-Rahmen und Regalen: Akkus liegen quer mit
  ihrer Aufschrift, Videoeinheiten zeigen ihr Profil, Stacks hängen flach. Inventar- und Handgrößen bleiben, nur ragt
  der 7″-Zweiblatt in der Ego-Sicht nicht mehr ins Bild und breite H-Frame-Drohnen sprengen ihren Inventarplatz nicht
  mehr ([`ASSETS.md`](ASSETS.md), „Größenregel der Items“).

### Teilnamen
- Eingebaute und Addon-Teile heißen nach einem Schema – Kenngröße und Gerät zuerst, Zusätze in Klammern:
  *6S 1300 mAh Battery*, *1S 300 mAh Battery (LiHV)*, *6S 4000 mAh Battery (Li-Ion)*, *1404 Motor, 4600 KV*,
  *31 mm Propeller (3-blade, 1.4″ pitch, PC)*, *5.1″ Propeller (3-blade, 4.6″ pitch, PC)*,
  *30×30 FC/ESC Stack (3–6S, 55 A)*, *Digital Video System (Lite)*, *Action Cam (Light)*.
- Props nennen Steigung und Material, Stacks Zellbereich und Strom; Zoll wird überall als ″ geschrieben, die Klassen
  der Bestenliste nutzen die Kurznamen der Rahmen (*Whoop 65*, *Cinewhoop 3″*, *Long Range 7″*).
- Die graue Shift-Kurzbeschreibung folgt demselben Schema: eine Kurzkennung aus Name und Daten, dann der Einsatz
  (*Whoop AIO (1–2S, 5 A) – FC and ESC on one board*, *5″ race frame – light, fast and direct*,
  *31 mm tri-blade, PC – faster, less grip*; deutsch *mit Ducts* statt *mit Schutzringen*). Item-Ids, Spielstände und
  Sharecodes bleiben unverändert ([`design/part-naming.md`](design/part-naming.md)).

### Funken unter macOS
- Funken wie die Radiomaster Pocket werden unter macOS erkannt. Vorher beanspruchte Apples GameController-Framework
  sie, und SDL listete gar keinen Controller. Propwash erkennt Controller jetzt standardmäßig ohne dieses Framework;
  PlayStation-, Switch-Pro- und Xbox-Controller laufen weiter über SDLs eigene Treiber.
- Reine MFi-Gamepads, die nur über das Framework laufen, brauchen die neue macOS-Option *Apple GameController* im Tab
  *Receiver* (Abschnitt „Erweitert (macOS)“); sie wirkt direkt nach dem Speichern. Eine Umgebungsvariable
  `SDL_JOYSTICK_MFI` aus dem Launcher gewinnt. Der Empfänger-Assistent nennt die Option, wenn er kein Gerät findet, und
  das Log nennt dann System, SDL-Version und diese Einstellung
  ([`input-troubleshooting.md`](input-troubleshooting.md)).

### Behobene Fehler
- **Akkuwarnungen:** Ein harter Punch mit vollem Akku drückt die Spannung kurz unter die Warnschwelle. *LOW BATTERY*
  und die Pieptöne enden jetzt, sobald sich die Spannung erholt (wie Betaflight), statt den Rest des Flugs anzubleiben;
  sie bleiben nur bei wirklich leerem Akku. Mit *Infinite battery* gibt es keine Akkuwarnungen, -zustände oder
  -pieptöne; die Spannung sackt unter Last weiter ab. Nur Warnungen, die Flugphysik ist unverändert.
- **Gas im Empfänger-Assistenten:** Der Assistent liest Gaskanal und Richtung aus der Bewegung des Sticks, egal wo der
  Stick beim Start steht: zuerst ganz nach oben, dann ganz nach unten und dort 1,5 s halten. Ein Halt oben nach dem
  ersten Weg nach oben gilt nie als „unten“, kurzes Antippen davor auch nicht, ein zu kurzer Weg wird gemeldet statt
  erkannt. Beim Halten fragt der Assistent „Steht das Gas ganz UNTEN?“, denn „erst runter, dann rauf und oben halten“
  sieht aus wie eine invertierte Funke (Einzelheiten im Tages-Changelog vom 02.10.2026). Nach den vier Kanälen zeigt
  eine neue Prüfansicht jeden Kanal als Live-Balken mit einem Schalter „Inv.“, der die Richtung umdreht.
- **Drohne hing nach Absturz in der Luft:** Nach einem Absturz, einem Aufsetzen oder einem Streifen blieb eine Drohne,
  die über eine Blockkante, eine Mauerkrone oder ein Podest rutschte (oder deren Block darunter abgebaut wurde), auf
  dieser Höhe auf einem unsichtbaren Boden stehen, bis sie neu scharf geschaltet wurde und Gas bekam. Bodenkontakt
  braucht jetzt eine echte Fläche unter der Drohne: Sie fällt, überschlägt sich und kommt am Boden zur Ruhe; eine
  scharfe Drohne, die nur irgendwo anstieß, fliegt normal weiter. Eine Drohne, die in der Luft bleibt, wenn ihr Pilot
  aufhört, oder die der Server bewegt, fällt jetzt sofort statt bis zu einer halben Sekunde später.
- **Blick durch den Boden:** Nach Absturz oder Landung konnte die FPV-Kamera im Gras, in einer Schneeschicht oder
  hinter einem Teppich landen, und das Bild zeigte Höhlen unter der Oberfläche (jede Drohne, am schlimmsten mit langen
  Nasen wie Deadcats, H-Frames und X-Class: bis 27 cm tief). Die Kamera hält jetzt ihr ganzes Sichtfeld frei von
  Blöcken: Berührt sie etwas, lässt sie zuerst nahe Geometrie durch (Near Plane bis 10 mm statt 50 mm); erst wenn das
  Objektiv selbst in einem Block steckt, rückt sie entlang der Oberfläche heraus. In freier Luft ist das Bild genau wie
  vorher; mit einem Iris-Shaderpack bleibt die Near Plane bei 50 mm und das Objektiv rückt stattdessen. Gilt für
  Pilot, Zuschauer und Action-Cam-Aufnahmen ([`design/fpv-eye-clearance.md`](design/fpv-eye-clearance.md)).
- **Wasser hinter drehenden Props:** Unschärfe-Scheiben der Props, LED-Leuchten und Geister-Drohnen werden nach dem
  durchscheinenden Gelände gezeichnet; Wasser bleibt dahinter sichtbar (auch mit Shaderpacks).
- **Hänger mit OSD oder HUD:** Mit Schrift-Ressourcenpaketen oder anderen Font-Providern fror das Spiel alle paar
  Sekunden für etwa eine Drittelsekunde ein, solange das OSD sichtbar oder der OSD-Konfigurator offen war. Das OSD sieht
  genau aus wie vorher und kostet weniger je Frame.
- **Werkbank:** Die Werte laufen in keiner Sprache und GUI-Skala mehr in ihre Beschriftung; zu lange Beschriftungen
  werden mit „…“ gekürzt.
- **Tooltips am Bildschirmrand:** Der Theme-Rahmen liegt 4 px um den Text, Vanilla hält aber nur 3 px frei; bei hohen
  Tooltips (Shift-Hilfe der Gates mit GUI-Skala 3) fehlte unten die Rahmenlinie. Der Rahmen bleibt jetzt im Bild.

---

## 0.3.2

- Drohnen-Items zeigen im Inventar, in der Hotbar, in der Hand, im Item-Rahmen, am Boden, im Kreativ-Tab und im
  Ausgabe-Slot der Werkbank genau die gespeicherte Drohne: Rahmengröße, Teile, Zubehör, Lackierung, Prop-Schäden und
  einen fehlenden Akku. Kleine Whoops und große 7″-Drohnen werden so skaliert, dass sie im Slot lesbar bleiben; gleiche
  Drohnen teilen sich ein Symbol, damit volle Inventare schnell bleiben.
- Der eigene Geist und der Rekord-Geist des Kurses werden als echtes, durchscheinendes Drohnenmodell mit Lack
  gezeichnet (bläulich der eigene, golden der Rekord) statt als Kasten. Geister älterer Versionen nutzen die aktuelle
  Drohne, wenn sie dieselbe Klasse hat, sonst ein passendes Preset. Rekord-Geister, die ein 0.3.2-Server speichert,
  erscheinen auf Clients mit 0.3.1 oder älter nicht.

## 0.3.1

- **Funke in der Ego-Sicht:** Sie sitzt tiefer und kleiner im Bild und klettert nicht mehr den Bildschirm hinauf, wenn
  man im Sichtflug zur Drohne hinunterschaut. Die Unterarme kommen aus den unteren Ecken, die Daumen liegen auf den
  Sticks, und die Haltung bleibt bei jedem Sichtfeld und Seitenverhältnis gleich.
- **Zweite Hand:** Solange die Funke beidhändig gehalten wird, wird das Item in der anderen Hand nicht mehr darüber
  gezeichnet (Ego- und Third-Person-Sicht). Es kommt zurück, sobald man die Funke weglegt; Zielen oder ein Item benutzen
  schaltet auf die einhändige Ansicht.
- **Stapelbare Gates:** Rechtsklick auf ein gesetztes Gate mit einem Gate-Item setzt es obendrauf, bis zu vier Gates
  hoch. Gestapelte Gates bilden eine Leiter wie echte Drone-Ladders: Der obere Balken des unteren Gates wird zum
  gemeinsamen Mittelbalken mit T-Stücken, die Seitenpfosten laufen durch, das unterste Gate steht auf Beinen. Ein Gate
  ist nie breiter als das darunter; beim Stapeln zurückschauen dreht das neue Gate für Zickzack-Leitern. Jedes Gate
  bleibt ein eigenes Kursgate mit Nummer und Pfeilen, die Kollision folgt dem gezeichneten Rahmen, und wird ein Gate in
  der Mitte abgebaut, rutschen die oberen nach. Die Ideallinie macht einen Umweg um Doppelgates gleicher Richtung, der
  Weltkurs *Drone Arena* hat eine Leiter aus drei Gates, und ein neuer Flugschul-Schritt zeigt das Stapeln.

## 0.3.0

### Lackierung
- Knopf *Paint* an der Drohnen-Werkbank öffnet ein Lack-Fenster mit drehbarer 3D-Vorschau. Zwölf Teile lassen sich
  einzeln lackieren: Rahmen, TPU und Akzente, Motorglocken, jeder der vier Props, Stack-Platinen, Akkuhülle, Kamera,
  Antennenspitzen und Zubehör. Ein Klick in die Vorschau wählt das Teil.
- 16 Farbstoff-Farben, fünf Oberflächen (Carbon, Gunmetal, Aluminium, Gold, Kupfer) und jede Hex-Farbe; *Apply to all
  props*, Kopieren und Einfügen (auch Strg/Cmd+C/V), Zurücksetzen je Teil oder für die ganze Drohne und vier Vorlagen
  (Carbon Classic, Neon, Race Team, Arctic).
- Im Überleben kostet jedes umlackierte Teil einen passenden Farbstoff (eine Oberfläche ihren Farbstoff, eine Hex-Farbe
  den nächstliegenden mitgeführten). Zurück zur Originalfarbe ist frei, Kreativ ist frei.
- Der Lack wird an alle Spieler übertragen, erscheint im Item-Symbol und bleibt beim Zerlegen und Neubauen am Rahmen.
  Eine fertige Drohne lackiert man um, indem man sie in den Recycling-Slot der Werkbank legt.

### Rennkurse in der Welt
- Vier fertige Kurse entstehen in der Oberwelt: *Field Slalom* (Ebenen, Sonnenblumenebenen, Wiesen), *Canyon Run*
  (Tafelberge und Wüste), *Forest Weave* (Wälder und Taiga, Holz nach Biom) und die seltenere *Drone Arena* (Ebenen,
  Savanne, verschneite Ebenen) mit Tribünen, einer Ruinenmauer und erhöhten Gates.
- Jeder Kurs hat nummerierte Propwash-Gates, die von selbst einen Kurs bilden, ein gewachstes Schild am Start mit Name,
  Kursnummer, Rekordzeit und Rekordhalter (bei jedem neuen Rekord aktualisiert) und eine bescheidene Beutetruhe mit
  Props, Zwischenteilen, kleinen Akkus und ab und zu einem besseren Teil.
- Kurse halten Abstand zu Dörfern und anderen Strukturen; finden mit `/locate structure #propwash:world_courses`.
  Server-Betreiber stoppen neue Kurse in ungeneriertem Gelände mit der Regel `worldCourses` in `server.json`.

### Videokanäle und Zuschauen
- Jeder Videosender sendet auf einem Kanal: analog R1–R8 und F1–F8 mit den echten 5,8-GHz-Frequenzen, digital CH1–CH8.
  Der Server verteilt freie Kanäle automatisch; im Tab *Video Channel* lässt sich ein Kanal festlegen.
- Mit Brille schaltet `F7` durch die Kanäle anderer Piloten, die die eigene Brille empfangen kann; die eigene
  Bildstörung hängt von der Entfernung zu deren Drohne ab. OSD des Piloten und eine Einblendung mit Kanal, Frequenz,
  Pilot und Drohne (beides schaltbar). `F7` nach dem letzten Kanal, `Esc` oder Rechtsklick führt zur eigenen Sicht
  zurück.
- Neues OSD-Element *VTX* mit dem aktuellen Kanal; Server schalten das Zuschauen mit der Regel `allowSpectating` ab.
- Beim Zuschauen erscheinen die eigene Ideallinie und Heimmarkierung nicht, die Akkuspannung ist die echte des Piloten,
  die Gates behalten ihre normalen Farben.

### Geister-Rennen, Bestenlisten und Starts
- Die persönliche Bestrunde je Kurs und Drohnenklasse fliegt als blauer Geist mit, der Server-Rekord der eigenen Klasse
  als goldener Geist. Splits an jedem Gate und am Rundenende zeigen den Abstand zum Geist.
- Bestenlisten liegen in der Welt, je Kurs und Drohnenklasse (Top 10 mit Pilot, Zeit und Datum). Es zählen nur vom
  Server bestätigte Runden; Runden mit Höhen- oder Positionshaltung, GPS Rescue oder veränderter Physik nicht. Neue
  Kurs- und Klassenrekorde werden im Chat gemeldet.
- Bestenlisten-Fenster mit Kurswahl, Klassenfilter und Podiumsfarben: Rechtsklick in die Luft mit dem Kurs-Werkzeug
  oder *Open leaderboard* im Tab *Race*.
- Stehende Starts mit Startampel: *Restart race* am Startgate (oder dort scharf schalten mit *Start when arming*).
  Andere Piloten am Start schließen sich an; das Rennen beginnt für alle gleichzeitig nach 3-2-1-GO. Mehrere Piloten
  bekommen ihre Zielreihenfolge im Chat.
- Ein Frühstart durch das Startgate vor GO kostet 3 Sekunden statt den Lauf abzubrechen; hinter dem Gate treiben ist
  kein Frühstart mehr.

### Ideallinie und Gate-Markierungen
- Im laufenden Rennen führt eine leuchtende Ideallinie durch die nächsten Gates (1–8 voraus, Standard 3; schaltbar im
  Tab *Race*). Sie erscheint nie im freien Flug oder beim Bauen und verschwindet, wenn man weit vom Kurs entfernt ist.
- Kursgates tragen ihre Nummer auf einem Schild, leuchtende Pfeile am Boden vor der Eingangsseite und rote Markierungen
  auf der Rückseite. Gate 1 ist das karierte Start/Ziel-Gate mit einem Banner „START · FINISH“.

### Freestyle-Tricks
- Propwash erkennt im Flug Flips und Rolls (einfach, doppelt, dreifach), Power Loop, Matty Flip, Split-S, Dive mit
  Abfangen, Yaw Spins, Orbit, Inverted Hang, Gaps und Proximity.
- Punkte mit Combos (×1,5 bis ×4, wenn der nächste Trick innerhalb von 3 Sekunden folgt), weniger für denselben Trick
  hintereinander und ein Stilbonus für nahes Fliegen an Blöcken und saubere Recoveries. Absturz oder Disarm kostet den
  Combo-Bonus.
- OSD-Element *Tricks* zeigt jeden Trick und jede Combo; die Statistik nach dem Flug zeigt Trickpunkte und die beste
  Combo. Tab *Freestyle*: Erkennung an, aus oder automatisch (an für Freestyle-, Whoop- und Cinewhoop-Aufbauten, aus für
  Race und Long Range), mit Punktetabelle und Live-Punktestand.

### Aussehen
- Beschädigte Props zeigen ihren Zustand in drei Stufen: abgesplitterte Spitzen und Kerben bei Verschleiß, ein großes
  fehlendes Stück bei schwerem Schaden, ein an der Nabe abgebrochenes Blatt und ein verbogenes Blatt, wenn kaputt.
  Gleich in der Welt, in Werkbank- und Wartungsvorschau, in der Flugschule und am Item-Symbol.
- Die schwere Action-Cam ist kleiner (60 × 45 × 35 mm) und sitzt auf einem roten TPU-Halter vor dem Kamerakäfig, nach
  oben geneigt, ohne in den Akku zu ragen. Die leichte Action-Cam sitzt in einer roten TPU-Halterung mit Gurt.
- LED-Streifen und Arm-LEDs leuchten mit weichen Halos und heben sich im Dunkeln ab. Drehende Prop-Scheiben sind
  dezenter, Rahmen und Motoren bleiben lesbar. Der FPV-Scheinwerfer ist warmweiß, leicht in der LED-Farbe getönt, mit
  gut sichtbarem Kegel.
- In der Ego-Sicht sitzt die Fernbedienung tiefer und näher, beide Hände an den Griffen, Daumen auf den Sticks, der
  Blick nach vorn bleibt für den Sichtflug frei. Auf dem Kopf eines Rüstungsständers ist sie klein und flach.

## 0.2.1

- Die Körnung der Action Cam Light ist viel dezenter und erscheint nur noch in sehr dunklen Bereichen; der neue
  Schalter *Camera grain* im Tab *Action Cam* schaltet sie ganz ab.
- Aufnahmequalität *High*, *Standard* oder *Small* mit geschätzter Größe in MB pro Minute. Aufnahmen halten eine
  gleichmäßige Bildqualität mit Bitraten-Obergrenze statt fester Bitrate: ruhige Szenen ergeben kleinere Dateien,
  schnelle oder dunkle Szenen zerfallen nicht mehr in Blöcke. *Standard* ist voreingestellt und überschreitet nie die
  alte Bitrate.
- Besserer Video-Encoder: feinere Details in flachen und dunklen Bereichen, keine sichtbaren waagerechten Nähte mehr
  und viel schärfere schnelle Schwenks und Flips dank weiterer Bewegungssuche.
- Rezeptbuch im Überleben: Jedes Propwash-Rezept lässt sich freischalten. Ein aufgehobener Rohstoff schaltet seine
  Rezepte frei, jedes Propwash-Item (Werkbank, Zwischenteil oder Beutefund) alle. Die Rezepte liegen in passenden Tabs
  (Teile, Brillen, Funke und Kurs-Werkzeug unter Ausrüstung, Zwischenteile, Werkbank und Ladegerät unter Verschiedenes,
  Gates unter Baublöcke), und die Suche nach „propwash“ listet jeden Propwash-Eintrag.
- Flugschule: Hinweise zeigen in jeder Fenstergröße und GUI-Skala genau auf ihr Ziel, weil die 3D-Szene mit derselben
  Projektion gezeichnet wird wie die Hinweise. Die Überleben-Szene zeigt auf das Rezeptbuch am Werktisch statt auf das
  2×2-Raster im Inventar.

## 0.2.0

Mindestversionen: Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, NeoForge 26.3.0.10-beta.

### Zubehör
- Zwei neue Zubehör-Plätze an der Drohnen-Werkbank: eine Action-Cam oben auf dem Rahmen und ein LED-Streifen unter der
  Nase. Gewicht, Schwerpunkt, Trägheit, Stirnfläche und Stromaufnahme gehen ins Flugmodell; eine schwere Cam weit oben
  macht die Drohne träger beim Rollen und Flippen. Mit Rezepten, Truhenbeute, Modellen und Shift-Tooltips.
- **Action Cam Light** (42 g, über den Flugakku versorgt, Rohbild bis 1080p60) und **Action Cam Heavy** (150 g,
  Stabilisierung mit optionalem Horizont-Ausgleich bis 1440p60, eigener Akku für etwa 90 Minuten bei 1080p, der beim
  Parken per USB aus dem Flugakku lädt).

### Action-Cams, die wirklich aufnehmen
- Nimmt das saubere FPV-Kamerabild ohne OSD und Funkstörungen als MP4 auf (H.264, 30 oder 60 fps, 720p bis 1440p) in
  `propwash/recordings`, auf Wunsch mit dem Motorgeräusch der Drohne als AAC-Spur. Die Dateien laufen im Browser, unter
  Windows, macOS und auf Handys.
- Start und Stopp mit `F9` (im Tab *Keys* umbelegbar), einem Schalter oder Knopf an der Funke (neue Funktion *Record* im
  Tab *Modes*) oder automatisch beim Scharfschalten.
- Kleines REC-Element im OSD (blinkender Punkt und Zeit, danach Speicherfortschritt), Propwash-Meldungen für Start,
  Stopp und gespeicherte Clips und ein neuer Konfigurator-Tab *Action Cam* für Auflösung, Bildrate, Stabilisierung und
  Ton.
- Fenster *Recordings* mit Vorschaubild, Datum, Länge, Format und Größe jedes Clips: im System-Player abspielen, löschen
  oder den Ordner öffnen. Die Kodierung läuft im Hintergrund, das Spiel bleibt flüssig.

### LED-Streifen und Scheinwerfer
- Der LED-Streifen leuchtet in 11 Farben mit den Modi Dauerlicht, Blinken, Arm-Status, Akkuwarnung und Regenbogen und
  einer Helligkeit wie Betaflights LED_STRIP. Die Einstellungen stehen auf der Drohne und sind für andere Spieler
  sichtbar.
- Im FPV leuchtet der Streifen im Dunkeln den Weg aus, Höhlen werden befliegbar; mit Shaderpacks wird ein einfacherer
  Lichtkegel als Overlay gezeichnet.
- Neuer Konfigurator-Tab *LED Strip* mit animierter 3D-Vorschau.
- Schalterfunktion *LEDs off* (wie Betaflights LED_LOW) auf jedem Schalter, Bereich oder Knopf im Tab *Modes* oder im
  Empfänger-Assistenten, auch invertiert und als Umschaltknopf; Tastatur-Piloten drücken `F8`. Streifen, Leuchten,
  FPV-Scheinwerfer und LambDynamicLights-Licht gehen für alle Spieler gleichzeitig aus; das OSD-Element *LED OFF* zeigt
  es. Ohne Belegung leuchten die LEDs wie bisher.
- Optionale Unterstützung für LambDynamicLights (Fabric und NeoForge): Drohnen mit LED-Streifen werden echte
  Lichtquellen.

### Teileverschleiß
- Jeder Absturz nutzt die Teile je nach Aufprall ab: Props nach etwa fünf Abstürzen, Motoren nach etwa 45, Kamera und
  Rahmen nach etwa 110; Flight-Controller und Akku halten am längsten, sehr harte Treffer blähen den Akku auf.
- Beschädigte Props kosten Schub und bringen Vibration, ein kaputter Prop macht die Drohne flugunfähig; eine
  beschädigte Kamera zeigt Reißen und Zittern. Das OSD warnt (zum Beispiel `PROP DMG`), Tooltips und Werkbank zeigen den
  Zustand jedes Teils.
- Props im Feld tauschen: Props desselben Typs halten, schleichen und die Drohne rechtsklicken. Andere Teile tauscht
  man an der Werkbank. Zubehör verschleißt nicht.
- Absturzschäden lassen sich nur mit der Kreativ-Brille abschalten oder skalieren; Server schalten sie mit der Regel
  `partWear` ab. Neue Flugschul-Lektion „Props wechseln und Schäden“.

### Fliegen und Video
- Realistische Topspeeds: Das Freestyle-Preset erreicht etwa 147 km/h statt bis zu 202 km/h (Racer 175, Long Range 113,
  Cinewhoop 65, Whoop 39). Schweben, Punch und Handling bleiben gleich.
- Das FPV-Bild wackelt bei hoher Geschwindigkeit nicht mehr: Die Motorvibration folgt der Drehzahl jedes Props und
  bleibt aus dem Regelband des Flight-Controllers heraus.
- Realistische Bildstörungen: Analog verblasst in Stufen (weiches Bild, gerissene Zeilen und Funken, Farbverlust,
  Schnee mit Synchronsprüngen, volles Rauschen, kurze Mehrwege-Aussetzer nahe Wänden); digital bleibt digital
  (Unschärfe, Auflösungsstufen, Blöcke, Ruckeln und Standbilder, dann „NO SIGNAL“). Das OSD bleibt immer oben.
- Im FPV dreht sich der Pilot nicht mehr zur Drohne; nur im Sichtflug folgt der Blick ihr.

### Funke
- Eine neue Funke oder ein Gamepad wird einmal erkannt und einmal eingerichtet: Propwash fragt „New radio detected –
  set it up now?“ (nie im Flug oder über einem Menü) und öffnet einen vorausgefüllten Assistenten.
- Jede Schalterbelegung lässt sich im Tab *Modes* und im Empfänger-Assistenten einzeln invertieren.
- Arm-Sicherung wie Betaflight: Macht eine geänderte Belegung oder eine neu verbundene Funke den Arm-Schalter aktiv,
  schaltet die Drohne erst scharf, nachdem der Schalter aus- und wieder eingeschaltet wurde.
- In der Third-Person-Sicht wird die Fernbedienung mit beiden Händen vor der Brust gehalten, auch bei anderen Spielern.
- Flugschule mit jetzt zwölf Lektionen, darunter „Action-Cam: Videos aufnehmen“ und „Props wechseln und Schäden“.

## 0.1.0

Mindestversionen: Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, NeoForge 26.3.0.10-beta.

### Fliegen
- Eigenes Flugmodell: Starrkörper mit Motor-, ESC- und Akku-Simulation, Rotor-Aerodynamik mit Ducts, Bodeneffekt,
  Prop-Wash, Spannungseinbruch, Wind, Böen und Turbulenz. Gewicht, Schub, Schwebegas, Topspeed und Flugzeit ergeben
  sich aus den Teilen jeder Drohne.
- Flight-Controller im Betaflight-Stil: Betaflight- und Actual-Rates, PID mit Feed-Forward, I-Term-Relax,
  Anti-Gravity, TPA, Airmode, Acro/Angle/Horizon, Arm-Prüfungen, Crash-Disarm und Failsafe.
- FPV über gebundene, passende Brillen oder Sichtflug, bei dem der Blick der Drohne folgt, mit kompaktem Funk-HUD.
- Funkverbindung mit etwa 800 Blöcken voller Reichweite; bei etwa 1.200 Blöcken reißt sie ab. Im Kreativmodus oder mit
  der Kreativ-Brille gibt es keine feste Grenze (es gelten die Reichweiten-Einstellung der Brille und die
  Server-Grenze).
- Nach einem Absturz aufrichten (Flip upright) und ein Piepser, um eine verlorene Drohne zu finden.
- GPS Rescue: Rückkehr zum Heimpunkt mit Hindernis-Ausweichen, automatischer Landung, Heimpunkt-Modi und Failsafe bei
  Funkverlust.

### Drohnen und Teile
- Fünf Rahmen (65-mm-Whoop, 3″-Cinewhoop, 5″-Freestyle, 5″-Race mit Stretched-X und 7″-Long-Range), drei
  Flight-Controller-Stacks, sechs Motoren, fünf Propeller, fünf Akkus und vier Videoeinheiten, jeweils mit eigenem
  3D-Modell. Rahmen nehmen mehrere Optionen auf, wo echte es tun (Motoren, Stack-Größen, Props).
- Drohnen-Werkbank mit Plätzen in einer Draufsicht-Anordnung, Live-3D-Vorschau, Bauanalyse, Fehlern und Warnungen und
  einem Recycling-Slot, der Drohnen wieder zerlegt.
- Fünf flugfertige Presets im Kreativ-Tab: Whoop, Cinewhoop, Freestyle, Racer und Long Range.
- Das Tuning steht auf der Drohne und überlebt das Aufheben; Zerlegen und Neubauen behält Tuning und die Bindung an
  Funke und Brille.
- Die Drohne lässt sich im Konfigurator, mit einem Namensschild an der gesetzten Drohne oder im Amboss benennen.

### Funke, Gamepad und Tastatur
- EdgeTX/OpenTX-Funken per USB (USB-Joystick-Modus) und Gamepads über SDL3, mit Hot-Plugging.
- Empfänger-Assistent für Gerät, Kanäle, Kalibrierung und Schalter; freie Belegung von Arm, Angle, Horizon, Rescue,
  Aufrichten, Heimpunkt setzen, Piepser und Rennneustart auf AUX-Bereiche oder Knöpfe, als Halten oder Umschalten.
- Jede Funktion hat eine eigene Taste (auch Acro, Angle und Horizon), änderbar im Tab *Keys* des Konfigurators.
- Gas-Modi für Funken und federnde Gamepad-Sticks; Tastaturflug als Rückfall.
- Item *FPV Remote*, dessen Sticks sich mit den Eingaben bewegen.

### Video
- Analoges Video mit Rauschen, Reißen, Farbverschmierung, rollenden Balken und Synchronverlust; digitales Video mit
  Blockartefakten, Ruckeln, Standbildern und hartem Schnitt zu „NO SIGNAL“.
- Latenz und Reichweite je Videoeinheit, Signaldämpfung durch Blöcke, Laub und Wasser.
- Analoge, digitale und Kreativ-FPV-Brillen; die Kreativ-Brille empfängt jedes System und hat freie Einstellungen für
  Latenz, Reichweite und Rauschen.
- Sichtfeld je Brille und Option „Reduce flicker“; mit aktivem Shaderpack wird ein einfacheres Overlay benutzt.

### OSD
- OSD im Betaflight-Stil mit 41 Elementen, Vorlagen (Minimal, Standard, Full/Telemetry, Race) und einem
  Drag-and-drop-Editor mit Deckkraft, Größe, Farben, Kontur und Hintergrund.

### Akkus und Laden
- Jeder Akku trägt seine eigene Ladung mit Ladebalken, Prozent und Ruhespannung.
- Akku-Passform je Rahmen, Akku-Menü mit Schleichen + Rechtsklick und Schnelltausch mit einem Akku in der Hand.
- Ladegerät mit vier Fächern, Ladezeit nach Kapazität, schneller mit Redstone, Komparator-Ausgang und Trichter-Anschluss;
  der gesetzte Block zeigt die Akkus in seinen Fächern und eine Status-LED je Fach.

### Rennen
- Renn-Gates in drei Größen, ein Kurs-Werkzeug, das sie zu Kursen verbindet, und ein grünes Licht am nächsten Gate.
- Rundenzeiten mit fliegendem Start oder Countdown, Splits gegen die beste Runde, Erkennung verpasster Gates, falscher
  Richtung und Frühstarts, einstellbare Runden und ein Bestrunden-Geist.
- Bestenlisten je Welt mit Server-Prüfung und Befehlen unter `/propwash race`.

### Überleben
- Rezepte für jedes Item außer der Kreativ-Brille, in Stufen von Kupfer, Eisen und Redstone bis Gold, Diamanten, Quarz,
  Amethyst, Glowstone und Echoscherben.
- Fünf Herstellungsmaterialien: Carbon Plate, Copper Coil, Circuit Board, LiPo Cell und HD Video Chip.
- Das Rezeptbuch schaltet sich über Fortschritte frei; die Drohnen-Werkbank schaltet den Starter-Satz frei.
- Truhenbeute in Dörfern, Minen, Verliesen, Tempeln, Plünderer-Außenposten, Festungen, Schiffswracks,
  Prüfungskammern, Tresoren, antiken Städten und Endsiedlungen, darunter der seltene flugfertige Whoop. Gefundene
  Akkus und Drohnen haben Lagerladung.

### Propwash-Staub
- Drohnen nah am Boden wirbeln dezenten Staub in der Farbe des Blocks darunter auf, über Wasser feine Gischt. Höhe,
  Menge und Ausbreitung folgen aus Schub, Prop-Größe und Neigung; gilt für jede sichtbare Drohne.
- Einstellungen im Tab *Display* (an/aus, Menge); folgt Minecrafts Partikel-Einstellung
  ([`design/propwash-dust.md`](design/propwash-dust.md)).

### Flugschule
- Zehn animierte 3D-Lektionen: Drohne bauen, Funke und Brille binden, FPV und Sichtflug, Akkus und Laden, Gates und
  Rennen, Videosysteme, Konfigurator, GPS Rescue und Failsafe, Abstürze und Reichweite, Herstellung im Überleben.
- Hinweise, Hervorhebungen, die echten Tastenbelegungen, eine dreh- und zoombare Kamera und eine Zeitleiste mit
  Schritten.
- Öffnet sich durch Halten der Flugschul-Taste (Standard `M`) über einem Propwash-Item, durch Drücken in der Welt oder
  aus dem Konfigurator.

### Oberfläche und Ton
- Konfigurator im Betaflight-Stil (Standard `K`, auch aus der Mod-Liste: NeoForge und Mod Menu auf Fabric) mit Tabs für
  Übersicht, Flugschule, Konfiguration, PID-Tuning, Rates, Empfänger, Modi, Tasten, Video, OSD, Rennen, GPS Rescue,
  Simulation, Anzeige, Audio und Server. Die Fußzeile zeigt, ob ein Tab auf der Drohne speichert oder eine
  Spieleinstellung ist.
- Shift-Tooltips auf jedem Propwash-Item.
- Echtzeit-synthetisierter Motorsound je Drohne mit Doppler-Effekt, Duct-Resonanz und Pieptönen für Scharfschalten,
  Akku, Gates, Runden und Countdown.

### Server
- Chunks laden, entstehen und streamen um die Drohne statt um den Piloten, innerhalb eines gemeinsamen Chunk-Budgets;
  Server können die Chunk-Erzeugung auf Operatoren oder bereits erzeugtes Gelände beschränken.
- Server-Regeln in `config/propwash/server.json` (Rechte, Reichweite, Tempolimit, Sichtweite, Chunk-Budget,
  Spawn-Schutz, Ladezeiten) und Befehle unter `/propwash`.
- Server-seitige Prüfung der Pilotenbewegung und eine klare Meldung, wenn Client und Server verschiedene
  Propwash-Versionen haben.
