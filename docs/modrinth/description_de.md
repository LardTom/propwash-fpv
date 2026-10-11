# Propwash FPV

**Echtes FPV-Fliegen in Minecraft.** Bau dir einen Quadcopter aus realen Bauteilen, setz die Brille auf und flieg ihn
in der Ich-Perspektive der Drohne – mit deiner eigenen Funke, einem Gamepad oder der Tastatur. Jag durch Renn-Gates,
flieg Freestyle über deine Bauwerke oder erkunde die Welt aus der Luft.

Propwash hat ein eigenes Flugmodell und einen Flight-Controller im Betaflight-Stil, deshalb fliegen sich die Drohnen
wie echte: bissige 5-Zoll-Freestyler, verspielte Whoops, sparsame Long-Range-Kreuzer. Alles ist im Überlebensmodus
spielbar, und eine animierte Flugschule erklärt dir die ganze Mod direkt im Spiel.

---

## Neu in 0.5.0

- **Flugtraining:** 20 Lektionen zum Selberfliegen in fünf Kapiteln – vom Scharfschalten und Schweben über Gates und
  Flips bis zur Abschlussprüfung – mit der eigenen Drohne durch einen Hologramm-Kurs, den nur du siehst, mit Sternen,
  Bestzeiten und Zwischenzeiten; geht auch im Überleben und auf Servern
- **Welt-Regeln:** Schwerkraft, Akkulaufzeit, Wind, Turbulenz, Crash-Schaden, Propellertreffer, unendliche Reichweite
  und eine Grenze für Videokanäle sind Spielregeln für die ganze Welt, mit eigenem Tab „Welt-Regeln“ (Host und
  Operatoren ändern sie, alle anderen sehen sie) und den Voreinstellungen Realistisch, Training und Arcade mit einem Klick
- **Freestyle-Tricks, die echtes Fliegen verstehen:** 59 Tricks in neun Familien, 43 davon neu – vom Juicy Flick,
  Rubik's Cube und McTwist über Matty-Varianten, Power Flips und Hammerheads bis zu Sbang-Kombos, Guillotine und Side Trippy
  der neuen Schule, im 3D-Modus auch Rückenflug, Schweben kopfüber, Tic-Toc und 3D-Flips – erkannt auch unsauber, schnell
  oder aneinandergereiht, mit Ketten-Bonus und Lektion „Freestyle-Tricks“
- **Drohnen treffen Mobs und Spieler:** sie prallen ab und verletzen je nach Wucht, höchstens fünf Herzen, Spieler nur
  mit PvP – als Welt-Regel abschaltbar
- **Greifer:** eine Servo-Klaue für die untere Halterung, die im Tiefflug liegende Items aufnimmt, trägt und dort
  abwirft, wo du willst
- **Action-Cam (Flashback):** zeichnet deinen Flug als Kamerapfad für die Replay-Mod Flashback auf (nur Fabric,
  eigener Download), eine fertige Szene je Flug
- **Bandos:** seltene verlassene Scheunen, Fabriken, Wassertürme, Stege, Bohrinseln und Förderanlagen zum
  Durchfliegen, jedes Mal anders gebaut, manche mit einer Truhe gebrauchter Teile
- **Empfänger-Assistent mit animierter Funke:** die Propwash-Funke macht jeden Schritt vor – Knüppel, Schalter und
  Tasten – neben den Live-Balken, hebt den Arm-Schalter hervor und fragt nach, statt bei der Gas-Richtung zu raten
- **Scharf-Winkel** je Drohne wie `small_angle` in Betaflight: mit 180° scharf in jeder Lage, auch in der Luft
- **Binden ganz einfach:** Drohne mit der Funke anklicken, ihr Empfänger merkt sich dich – nach Werkbank, Aufheben und
  Absetzen verbindet die Funke sofort. Brillen werden nicht gebunden: Mit einer Brille, die zum Videosystem der Drohne
  passt, fliegst du FPV, ohne Brille im Sichtflug
- **Eigene Befehle:** eigene Minecraft-Befehle wie `/time set night` auf Funkenschalter, Taster oder Taste legen – einen
  fürs Einschalten, einen fürs Ausschalten; sie laufen wie im Chat getippt, mit deinen eigenen Rechten
- **GPS Rescue ohne Pilot:** Steigst du im Heimflug aus, steckst die Funke weg oder verlässt das Spiel, fliegt die
  Drohne allein heim und landet – jetzt auch um Bäume und Gebäude herum, aus Hallen durchs Tor und nie ins Wasser oder
  in Lava
- **Gates frei drehen:** diagonale, geneigte und Rauten-Gates und jede Öffnung von 1 × 1 bis 16 × 16 Blöcken, eingestellt
  im Spiel mit Mausrad und Live-Vorschau, während du weiterläufst und dich umsiehst; Drohnen prallen im echten Winkel ab, und „Gate drehen“ dreht auch in
  45°-, 15°- oder 5°-Schritten
- **Flaggen, Slalom und Tauchwürfel:** Wendeflaggen links oder rechts herum, Slalomstangen, deren Seiten das
  Kurs-Werkzeug abwechselt, und Tauchwürfel wie bei MultiGP – oben hinein, durch die gewählte Seite hinaus; jedes
  Element zählt nur, wenn es richtig geflogen wird
- **Gate-Stangen:** eine einzelne Gate-Seite mit LEDs in 16 Farben – am Boden, aus der Wand, von der Decke oder wie
  bei MultiGP auf der Ecke eines Gates, bis zu vier übereinander
- **Freiform-Gates:** Rechtsklick auf den Rand eines Gangs, Höhleneingangs, Fensters oder Schachts, und die Öffnung
  selbst wird zum Renn-Gate – kein Rahmen, nur ein Leuchtumriss an den Blöcken, jede Form bis 20 × 20 Blöcke
  (Welt-Regel)
- **4:3-Bild:** ein Bildformat 4:3 wie in analogen Brillen – schwarze Ränder links und rechts, oben und unten mehr
  Bild, das OSD im 4:3-Bild; Auto wählt 4:3 für analog und 16:9 für digital
- **Doku** unter [propwashfpv.com/docs](https://propwashfpv.com/docs/), und der Quellcode ist jetzt unter der PolyForm
  Shield License einsehbar (Add-on-API unter MIT)

## Neu in 0.4.0

- **Addons für neue Bauteile:** Eine versionierte Parts-API lässt andere Mods Rahmen, Stacks, Motoren, Props, Akkus,
  Video-Einheiten und Zubehör hinzufügen, die überall funktionieren – Werkbank, Analyse, Flugmodell, Lackierung,
  Verschleiß, Tooltips, Flugschule und Speicherstände. Erstes Addon: *Propwash: Just More Parts*
- **Neue Rahmenformen aus Addons:** Deadcats mit Props außerhalb des Kamerabilds, Stretched- und Squashed-X, H-Frames,
  Ducts und X-Class-Rahmen mit Props bis 13″, geflogen mit einem Mixer für die echte Arm-Anordnung
- **Motorhitze und ESC-Spannung:** Zu heiße Motoren werden zurückgeregelt und nutzen sich ab, ein Akku über dem
  Spannungsbereich des ESC lässt ihn beim Scharfschalten durchbrennen; die Werkbank zeigt die Dauertemperatur und wie
  viel vom Kamerabild die Props verdecken, und die eigenen Props erscheinen jetzt im FPV-Bild
- **Sharecodes (in Addons):** Baupläne, Lackierungen, Flug-Tunings und OSD-Layouts exportieren und importieren – vom
  Server geprüft, im Überlebensmodus bezahlt mit Teilen und Farbstoffen
- **Realistische Flugzeiten und Akku:** Schweben, Reisen, gemischt und aggressiv aus der Energie, die ein echter Akku
  liefert (Motor- und ESC-Verluste, Videoleistung, nutzbare Kapazität, Landespannung); OSD, HUD und Akku-Warnungen
  folgen demselben Verbrauch, der Akku ist leer, wenn ein echter leer wäre
- **Eigener Kreativ-Tab je Addon** direkt hinter dem Propwash-FPV-Tab, z. B. *Propwash: Just More Parts*
- **Drohnen als Wand-Trophäe:** Im Item-Rahmen hängen Drohnen, Rahmen und Props flach an der Wand, Kameraseite nach
  vorn und Nase oben, und drehen sich mit dem Rahmen; im Regal sind sie zum Betrachter geneigt
- **„Props im Kamerabild“ stimmt mit dem FPV-Bild überein** und folgt Rahmen und Kamerahalter (Race-Rahmen zeigen
  mehr Prop als ein True-X, Deadcats keine), der eingebaute Cinewhoop erreicht realistische 89 km/h, und
  die Werkbank bleibt in jeder Sprache und GUI-Größe lesbar
- **Fehlerbehebungen:** kein Röntgenblick mehr durchs Wasser hinter drehenden Props, keine periodischen OSD-/HUD-Hänger
  mehr mit Schrift-Ressourcenpaketen
- Wird ein Addon entfernt, behalten Drohnen seine Teile als *Unbekanntes Bauteil* und sind wieder vollständig, sobald
  es zurück ist

## Neu in 0.3.1

- **Stapelbare Gates:** Rechtsklick mit einem Gate auf ein Gate stapelt bis zu vier zu einer Leiter mit gemeinsamen
  Mittelbalken wie bei echten Drohnen-Leitern – jedes bleibt ein eigenes Kurs-Gate, und die Drohnen-Arena hat jetzt eine
- **Beidhändige Funke in der Ego-Ansicht:** Die Funke sitzt tiefer und kleiner im Bild, die Unterarme kommen aus den
  unteren Ecken, und das Item in der Nebenhand wird ausgeblendet, solange du die Funke hältst

## Neu in 0.3.0

- **Lackierung:** Färbe jedes Teil deiner Drohne in der Werkbank – Rahmen, Motoren, jeden Prop einzeln, Stack, Akku
  und mehr – mit Farbstoffen, fünf Oberflächen aus Carbon und Metall, beliebigen Hex-Farben und Vorlagen
- **Rennkurse in der Welt:** vier seltene, fertige Kurse entstehen in Ebenen, Tafelbergen, Wüsten, Wäldern und mehr,
  mit nummerierten Gates, Rekordtafel und Beutetruhe
- **Videokanäle und Zuschauen:** Jeder Sender funkt auf einem echten 5,8-GHz- oder Digitalkanal; mit `F7` siehst du
  durch deine Brille das Kamerabild anderer Piloten
- **Geister-Rennen und Bestenlisten:** Flieg gegen den Geist deiner Bestrunde und den Rekord-Geist des Servers,
  Bestenlisten je Drohnenklasse im eigenen Fenster, stehende Starts mit Startampel für mehrere Piloten
- **Ideallinie und Gate-Markierungen:** eine leuchtende Linie durch die nächsten Gates, ein kariertes Start-/Ziel-Gate
  und Pfeile für die Flugrichtung
- **Freestyle-Tricks:** Flips, Rollen, Power Loops, Matty Flips und mehr werden erkannt, mit Punkten und Kombos im OSD

## Neu in 0.2.0

- **Zubehör:** eine Action-Cam oben auf dem Rahmen und ein LED-Streifen unter der Nase – beide verändern, wie die
  Drohne fliegt
- **Action-Cams, die wirklich aufnehmen:** dein Flug als MP4-Video, auf Wunsch mit Motorgeräusch
- **LED-Streifen** in 11 Farben mit Modi wie bei Betaflight und FPV-Scheinwerfer für Höhlen; optional mit
  LambDynamicLights
- **Bauteil-Verschleiß:** Abstürze nutzen Props, Motoren und mehr ab; Props tauschst du direkt vor Ort oder
  reparierst sie paarweise
- Realistische Topspeeds, ein ruhiges FPV-Bild bei hohem Tempo und ein Videobild, das in echten Stufen zerfällt
- Eine neue Funke wird einmal erkannt und eingerichtet, jeder Schalter lässt sich invertieren, Scharfschalten ist
  sicher wie bei Betaflight
- Zwei neue Lektionen in der Flugschule

---

## Features

### Flugmodell und Flight-Controller
- Starrkörper-Physik mit Motor-, Regler- und Akkusimulation, Rotoraerodynamik, Bodeneffekt, Prop-Wash,
  Spannungseinbruch, Wind und Böen
- Jede Drohne ergibt sich aus ihren Teilen: Gewicht, Schub, Schwebegas, Höchstgeschwindigkeit und Flugzeit folgen aus
  dem Build
- Rates nach Betaflight und Actual, PID mit Feed-Forward, I-Term-Relax, Anti-Gravity, TPA und Airmode
- Flugmodi Acro, Angle und Horizon, Arming-Prüfungen, Failsafe und Aufrichten nach einem Absturz
- 3D-Modus mit umkehrbaren Motoren: im Tab „Modi“ freischalten, auf einen Schalter legen – Gasmitte ist Nullschub,
  darunter drehen die Props rückwärts, so fliegst du kopfüber und startest nach einem Absturz kopfüber
- **GPS Rescue:** Heimflug mit Hindernisvermeidung und Wegplanung, automatischer Landung auf sicherem Grund,
  Failsafe bei Funkverlust – und er fliegt weiter, wenn du aussteigst

### Eigene Drohnen bauen
- Fünf Rahmen: vom 65-mm-Whoop über 3-Zoll-Cinewhoop, 5-Zoll-Freestyle- und Race-Rahmen bis zum 7-Zoll-Long-Range
- Flight-Controller, sechs Motoren, fünf Propeller, Akkus und Videoeinheiten mit echten Kennwerten
- Echte Wahl innerhalb eines Rahmens: leichte 1404- oder kräftige 2004-Motoren am Cinewhoop, Vier- oder Dreiblatt am
  Whoop, 30×30-Stack oder leichtes 20×20-AIO an Race- und Long-Range-Rahmen, 2807- oder 2207-Motoren am 7-Zöller
- Gib deiner Drohne einen Namen – im Konfigurator, mit Namensschild oder Amboss; OSD, Funke und Brille zeigen ihn
- Die **Drohnen-Werkbank** setzt alles zusammen – mit 3D-Vorschau, Gewicht, Schub-Gewicht-Verhältnis, Schwebegas,
  Höchstgeschwindigkeit und Flugzeit – und warnt vor Teilen, die nicht zusammenpassen
- Fünf flugfertige Vorlagen im Kreativmodus: Whoop, Cinewhoop, Freestyle, Racer und Long Range

### Mit der echten Funke fliegen
- EdgeTX-/OpenTX-Funken per USB, Gamepads und Tastaturflug als Ersatz
- Empfänger-Assistent für Kanäle, Kalibrierung und Schalter; freie Schalterbelegung für Scharfschalten, Flugmodi,
  Aufrichten, Heimflug, Homepoint, Suchpiepser, Rennneustart und Action-Cam-Aufnahme
- Die **FPV-Funke** im Spiel bewegt ihre Knüppel mit deinen

### FPV oder Sichtflug
- Mit passender Brille siehst du durch die Kamera der Drohne, dazu ein OSD wie bei Betaflight (43 Elemente,
  Vorlagen, Editor per Ziehen und Ablegen)
- Keine Brille? Dann fliegst du auf Sicht: Dein Blick folgt der Drohne, ein kompaktes Funk-HUD zeigt Akku, Signal und
  Flugmodus

### Analoges und digitales Video
- **Analog:** kaum Verzögerung; mit Entfernung und Hindernissen zerfällt das Bild in Stufen – verrissene Zeilen,
  verblassende Farben, Schnee mit Bildsprüngen und zuletzt volles Rauschen
- **Digital:** klares HD-Bild mit etwas mehr Latenz, am Rand der Reichweite Unschärfe, Blockartefakte, Ruckler,
  Standbild und dann „KEIN SIGNAL“ – nie analoger Schnee
- Signalmodell mit Reichweite je Videoeinheit und Dämpfung durch Blöcke, Laub und Wasser
- Bildformat 16:9 oder 4:3 (schwarze Ränder wie in 4:3-Brillen, gleiches horizontales Sichtfeld, OSD im Bild) oder
  Auto nach dem Videosystem
- Die **Kreativ-FPV-Brille** empfängt jedes System; Latenz, Reichweite und Rauschen sind frei einstellbar; im
  Kreativmodus oder mit dieser Brille hat auch die Funkreichweite keine feste Grenze

### Akkus und Ladegerät
- Jeder Akku ist ein eigenes Item mit eigenem Ladestand und eigener Spannung
- Jeder Rahmen nimmt nur die Akkus, die zu ihm passen
- Akkutausch mit Schleichen + Rechtsklick, geladen wird im **Akku-Ladegerät** (vier Schächte, mit Redstone schneller)

### Action-Cams, die wirklich aufnehmen
- Eine Action-Cam oben auf dem Rahmen nimmt deinen Flug als echtes **MP4-Video** auf (H.264, 30 oder 60 fps, 720p bis
  1440p, auf Wunsch mit dem Motorgeräusch als AAC-Ton), gespeichert in `propwash/recordings` – das saubere Kamerabild
  ohne OSD und ohne Funkstörungen
- **Action-Cam (Light)**: nackte FPV-Cam, 42 g, Strom aus dem Flugakku, rohes Bild, rauscht etwas bei Dunkelheit
- **Action-Cam (Heavy)**: große Action-Cam im Gehäuse, 150 g, mit Stabilisierung und auf Wunsch geradem Horizont, eigener
  Akku (ca. 90 min bei 1080p), der abgestellt per USB aus dem Flugakku lädt
- **Action-Cam (Flashback)**: zeichnet deinen Flug als Kamerapfad für die Replay-Mod
  [Flashback](https://modrinth.com/mod/flashback) auf (nur Fabric, eigener Download) – jeder Flug wird eine Szene im
  Flashback-Editor, fertig zum Rendern als Kinovideo; ohne Flashback zeigt sie nur einen Hinweis
- Das Gewicht spürst du im Flug: mehr Masse und Trägheit, die schwere Kamera sitzt hoch oben und macht Rollen und
  Flips träger
- Aufnahme mit **F9** (frei belegbar), einem Schalter oder Taster an der Funke oder automatisch beim Scharfschalten;
  eine dezente **REC**-Anzeige im OSD zeigt die Zeit, kodiert wird im Hintergrund, das Spiel bleibt flüssig
- Das Fenster **Aufnahmen** zeigt alle Videos mit Vorschaubild, Datum, Länge und Größe – im Systemplayer abspielen,
  löschen oder den Ordner öffnen

### LED-Streifen und Scheinwerfer
- Der LED-Streifen leuchtet in 11 Farben mit den Modi Dauerhaft, Blinken, Scharf-Status, Akku-Warnung und Regenbogen,
  gespeichert in der Drohne und für alle Spieler sichtbar
- Im FPV leuchtet er im Dunkeln den Weg nach vorn aus, Höhlen werden fliegbar
- Im Flug an- und ausschalten per Schalter an der Funke („LEDs aus“, wie LED_LOW bei Betaflight) oder `F8`
- Mit **LambDynamicLights** (optional) wird deine Drohne zur echten Lichtquelle in der Welt

### Bauteil-Verschleiß
- Jeder Absturz nutzt die Teile ab, je nachdem wie hart du aufschlägst: zuerst die Props, Motoren, Kamera und Rahmen
  halten deutlich länger, ein sehr harter Treffer bläht den Akku auf
- Angeschlagene Props kosten Schub und lassen das Bild wackeln, mit einem gebrochenen Prop bleibst du am Boden; das
  OSD warnt dich
- Props tauschst du vor Ort mit Schleichen + Rechtsklick, andere Teile an der Werkbank; Zubehör nutzt sich nie ab
- Props reparierst du wie Werkzeug: Zwei beschädigte Props derselben Sorte, zusammen gecraftet oder im Schleifstein,
  ergeben einen mit beiden Zuständen zusammen plus 5 %
- Crash-Schaden und seine Stärke sind Welt-Regeln, eine Welt oder ein Server kann ihn abschwächen oder abschalten

### Rennen
- Renn-Gates in drei Größen und ein Kurs-Werkzeug, das sie zur Strecke verbindet
- Gates bis zu vier hoch zu Leitern und Doppel-Gates stapeln, jedes bleibt ein eigenes Kurs-Gate
- Zeitmessung mit Zwischenzeiten, stehende Starts mit Startampel für mehrere Piloten und 3 Sekunden Frühstart-Strafe
- Geister deiner Bestrunde und des Server-Rekords je Drohnenklasse, mit Abstand an jedem Gate
- Bestenlisten je Kurs und Drohnenklasse im eigenen Fenster; es zählen nur vom Server geprüfte Runden
- Eine leuchtende Ideallinie durch die nächsten Gates; dein nächstes Gate leuchtet grün, Gate 1 ist ein kariertes
  Start-/Ziel-Gate, Pfeile am Boden zeigen die Flugrichtung
- Vier Arten fertiger Rennkurse entstehen in der Welt, mit Rekordtafel und Beutetruhe

### Lackierung
- Zwölf Teile jeder Drohne einzeln färben: Rahmen, TPU, Motorglocken, jeden Prop, Stack, Akku, Kamera,
  Antennenspitzen und Zubehör
- 16 Farbstoff-Farben, Oberflächen Carbon, Titan, Aluminium, Gold und Kupfer, Hex-Farben, Vorlagen, Kopieren und Einfügen
- Kostet im Überlebensmodus einen Farbstoff je umgefärbtem Teil; andere Spieler sehen deine Farben, auch das Item-Icon

### Videokanäle und Zuschauen
- Analog R1–R8 und F1–F8 mit echten 5,8-GHz-Frequenzen, digital CH1–CH8, automatisch vergeben oder fest je Drohne
- Mit Brille `F7` drücken und anderen Piloten zuschauen, mit ihrem OSD und deinen eigenen Bildstörungen

### Freestyle-Tricks
- 59 Tricks in neun Familien: Flips und Rollen bis dreifach und segmentiert, Snap Rolls, Korkenzieher, Vanny Rolls,
  Rewinds und Flip-Rewinds, Juicy Flicks, Rubik's Cubes, Power Loops mit Power Flip, Power Roll, Inverted 360 und Side Loop,
  Maverick Loops, Baranis, Immelmanns und Immelmatts, Hammerheads, Matty Flips sowie Anti, Half und Power Matty, Split-S,
  540-Split-S und Splitbacks, Yaw-Spins aufrecht und kopfüber, Coin Drops, Trippy Spins, McTwists, Kopfüber hängen, Knife
  Edges, Reverse Knife Edges und Wall Rides, Sturzflüge, Rückwärts-Sturzflüge und Tornados, Orbits, Gaps und
  Proximity-Vorbeiflüge – auch unsauber, langsam und aneinandergereiht
- Neue Schule: Ejects und Sbang-Kombos, Funky Split, Shinobi Flip, Guillotine und Side Trippy; im 3D-Modus Rückenflug,
  Schweben kopfüber, Tic-Toc, 3D-Flip, 3D-Rolle und Power Loop (3D)
- Der Tab „Freestyle“ gruppiert alle Tricks nach Familien, mit Suche (auch nach Szene-Namen wie Barrel Roll oder Stall Turn)
- Punkte, Kombos bis ×4, Ketten- und Stil-Boni im OSD; Trick-Punkte und beste Kombo in der Flugstatistik; jeder Trick
  im Tab „Freestyle“ erklärt, dazu eine Flugschul-Lektion

### Propwash-Staub
Fliegst du dicht über dem Boden, wirbeln die Rotoren dezent Staub in der Farbe des Blocks darunter auf, über Wasser
feine Gischt. Wie hoch, wie viel und wie weit er sich ausbreitet, folgt aus Schub, Propellergröße und Neigung.
Einstellbar, und Minecrafts Partikel-Einstellung wird beachtet.

### Überlebensmodus
- Alles außer der Kreativ-FPV-Brille lässt sich herstellen
- Fünf Zwischenteile (Carbonplatte, Kupferspule, Platine, LiPo-Zelle, HD-Videochip) verbinden Vanilla-Rohstoffe mit
  den Bauteilen
- Fortschritt in Stufen: ein kompletter Whoop, Funke und analoge Brille aus Kupfer, Eisen und Redstone; für die
  Oberklasse brauchst du Gold, Diamanten, Quarz, Amethyst, Glowstone und Echo-Splitter
- Das Rezeptbuch zeigt jedes Rezept, sobald du eine Zutat hast – und alle auf einmal, sobald du ein Propwash-Item besitzt
  (Suche „propwash“ an der Werkbank)
- In Truhen von Dörfern, Minen, Verliesen, Tempeln, Außenposten, Festungen, Schiffswracks, Prüfungskammern,
  Tresoren, antiken Städten und Endstädten stecken Bauteile, Akkus und ganz selten ein flugfertiger Whoop

### Flugschule
Neunzehn animierte 3D-Lektionen (im Kreativmodus zwanzig) erklären alles mit einem kleinen Diorama, Hinweisen und deiner echten Tastenbelegung:
Drohne bauen, Funke binden und Brille aufsetzen, FPV und Sichtflug, Akkus und Laden, Gates und Rennen, Gates frei drehen,
Flaggen, Slalom und Tauchwürfel, Freestyle-Tricks, Videosysteme,
Aufnehmen mit der Action-Cam, den Greifer, den Konfigurator, GPS Rescue und Failsafe, Höhe und Position halten, den 3D-Modus, Absturz und
Reichweite, Props wechseln und Schäden, das Herstellen im Überlebensmodus und Bandos. Dazu kommt das Flugtraining mit 20
Lektionen zum Selberfliegen. **Halte M über
ein beliebiges Propwash-Item**, um seine Lektion zu öffnen.

### Addons
Propwash 0.4.0 öffnet sich: Addons können Bauteile aller Art, neue Rahmenformen, Presets, Werkbank-Knöpfe,
Konfigurator-Tabs, Flugschul-Lektionen und Sharecodes hinzufügen. Addons gehören auf den Client **und** den Server, in
derselben Version; Propwash vergleicht die Teile-Listen beim Verbinden.

### Und außerdem
- Motorsound in Echtzeit synthetisiert, für jede Drohne einzeln, mit Doppler-Effekt und Pieptönen
- Shift-Tooltips an jedem Item: wofür, wie benutzen, was passt
- Ein Konfigurator im Betaflight-Stil (**K**, außerdem über Mod Menu unter Fabric und die Mod-Liste unter NeoForge)
  mit Tabs für Tuning, Rates, Empfänger, Modi, Tasten, Video, Videokanal, OSD, LED-Streifen, Action-Cam, Rennen, Freestyle,
  GPS Rescue, Welt-Regeln, Darstellung, Audio und Server
- Mehrspielertauglich: Der Server lädt und erzeugt die Chunks um die Drohne, deine Sichtweite begrenzt dich also nicht;
  Welt-Regeln für alle (Schwerkraft, Akkulaufzeit, Wind, Crash-Schaden, Reichweite, Videokanäle), Rechte und Prüfung der
  Bewegungen

---

## Los geht's

1. Bau an der **Drohnen-Werkbank** eine Drohne (oder nimm eine Vorlage aus dem Kreativ-Tab).
2. Setz sie auf den Boden.
3. Klick die Drohne mit der **FPV-Funke** an, um sie zu binden.
4. Setz eine **FPV-Brille** auf, die zum Videosystem der Drohne passt (analog oder digital) – gebunden wird sie nicht.
5. Funke in die Hand und Rechtsklick in die Luft: FPV mit passender Brille, Sichtflug ohne Brille.
6. Scharf schalten (Schalter oder **Y**) und los. Erneuter Rechtsklick mit der Funke oder **Z** beendet den Flug.

Nicht weiter? Halte **M** über ein Propwash-Item für seine Flugschul-Lektion oder **Shift** für einen ausführlichen
Tooltip.

### Standardtasten

| Aktion | Taste |
|---|---|
| Scharf schalten/entschärfen | Y |
| Flugmodus wechseln (Acro/Angle/Horizon, im Flug) | R |
| Acro / Angle / Horizon direkt | Ziffernblock 1 / 2 / 3 |
| 3D-Modus an/aus (nach dem Freischalten) | Ziffernblock 6 |
| Drohne aufrichten | U |
| FPV-Flug beenden | Z |
| GPS Rescue an/aus | J |
| Homepoint setzen | , (Komma) |
| Suchpiepser an/aus | . (Punkt) |
| Rennen neu starten | Rücktaste |
| Gate drehen (Gate oder Kurs-Werkzeug in der Hand; Schleichen: Lage wechseln) | R (geteilt mit „Flugmodus wechseln“) |
| Action-Cam: Aufnahme starten/stoppen | F9 |
| Konfigurator | K |
| Flugschule | M (über einem Item halten) |
| Tastaturflug | W/S Gas, A/D Gieren, Pfeiltasten Nicken und Rollen |

Die Tasten gelten für eine deutsche QWERTZ-Tastatur; auf einer US-Tastatur liegen Scharfschalten und Beenden auf Z
und Y. Jede Taste lässt sich im Konfigurator-Tab **Tasten** ändern.

### Funke einrichten
Schließ deine EdgeTX-/OpenTX-Funke per USB an und wähle **USB-Joystick (HID)**. Propwash erkennt sie automatisch
(AETR, AUX1 = scharf, AUX2 = Flugmodus) und fragt einmal, ob du sie jetzt einrichten willst – der vorbelegte Assistent
braucht meist nur eine Bestätigung und eine kurze Kalibrierung. Danach lädt die Funke beim Einstecken ohne Rückfrage ihr
Profil. Den **Empfänger-Assistenten** startest du jederzeit wieder im Konfigurator unter **Empfänger**.

---

## Voraussetzungen

- Minecraft **26.3**
- **Fabric:** Fabric Loader 0.19.5+ und **Fabric API**
- **NeoForge:** 26.3.0.10-beta oder neuer
- Java 25
- Nötig auf **Client und Server**
- Optional: [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights), damit Drohnen mit LED-Streifen echte
  Lichtquellen sind

## Bekannte Einschränkungen

- Vorerst nur Minecraft 26.3.
- Mit einem Shaderpack (Iris/Oculus) wird der Video-Effekt durch eine einfachere Überlagerung ersetzt.
- Außerhalb des Kreativmodus reicht die Funkverbindung etwa 1.200 Blöcke weit; Server können sie weiter begrenzen.
- Funken müssen im USB-Joystick-Modus laufen.
- macOS: Propwash erkennt Controller standardmäßig ohne Apples GameController-Framework, damit Funken wie die
  Radiomaster Pocket erscheinen. Reine MFi-Gamepads, die nur darüber laufen, brauchen die
  Einstellung „Apples GameController“ im Tab „Empfänger“.
- Beim Zuschauen ist das OSD des Piloten angenähert: Flugmodus, Sticks und Funkverbindung werden nicht übertragen.
- Die Action-Cam nimmt bis 1440p60 auf; wie flüssig, hängt vom Prozessor ab. Die Tonspur enthält nur das
  Motorgeräusch der aufnehmenden Drohne.

---

*Propwash FPV ist eine eigenständige Mod und steht in keiner Verbindung zu Betaflight, EdgeTX, OpenTX oder einem
Drohnenhersteller.*

*KEIN OFFIZIELLES MINECRAFT-PRODUKT. NICHT VON MOJANG ODER MICROSOFT GENEHMIGT ODER MIT IHNEN VERBUNDEN.*
