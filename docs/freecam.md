# Freecam-Drohne

Für Spieler. Die Freecam-Drohne ist eine Kamera-Drohne, die es nur auf deinem Bildschirm gibt: Du fliegst sie wie eine
echte FPV-Drohne mit Funke, Gamepad, Tastatur oder Maus, aber ohne Drohne in der Welt – kein Modell, keine Kollision,
kein Akku. Sie ist ein Werkzeug für Kamerafahrten, Kurs-Planung und zum Anschauen von Bauwerken.

## 1. Erlauben und starten

- Die Welt-Regel **„Freecam-Drohne erlauben“** (`propwash:allow_freecam`, Tab Welt-Regeln, Gruppe Spielablauf) ist
  **standardmäßig aus**. Im Einzelspieler und als Operator schaltest du sie mit einem Klick ein; auf Servern entscheidet
  der Admin. Ohne Welt-Regeln vom Server (zum Beispiel ein Server ohne Propwash) gibt es keine Freecam.
- **Start:** Taste **F6** oder im K-Menü im Tab **Übersicht** unten der Knopf **„Freecam starten“**. Ist die Freecam
  nicht erlaubt, ist der Knopf ausgegraut, der Grund steht links daneben in der Fußzeile; wer die Regel ändern darf,
  bekommt daneben **„Zur Welt-Regel“**, das den Tab Welt-Regeln an der richtigen Zeile öffnet.
- Die Freecam startet an deiner Augenposition, in Blickrichtung, waagerecht und **unscharf geparkt**: Sie schwebt still
  in der Luft, bis du sie scharf schaltest (Schalter oder Taste Z, Gas unten) – wie eine echte Drohne.
- **Ende:** F6 noch einmal, **Y** („FPV beenden“) oder „Freecam beenden“ im K-Menü. Danach ist die Kamera wieder bei
  dir. Beim ersten Start erklärt ein Hinweis die Tasten.

## 2. Fliegen

- Steuerung und Flugmodi wie im FPV-Flug (Acro, Angle, Horizon über Schalter oder die Tasten R und Ziffernblock 1–3).
  Höhen- und Positionshaltung, 3D-Modus und Turtle gibt es in der Freecam nicht.
- **Unscharf parkt sie:** Schaltest du in der Luft ab, bleibt die Freecam waagerecht an Ort und Stelle stehen, statt
  wie eine echte Drohne zu fallen – ohne Kollision fiele sie sonst durch die Welt.
- **Physik wie beim Fliegen:** Wind, Böen und Turbulenz, eigener Propwash, Bodeneffekt und die Zonen-Physik (etwa
  veränderte Schwerkraft) wirken wie bei der echten Drohne. Es gibt keinen Akku, keinen Absturz, keine Prop-Schläge und
  keine Hitze.
- **Durch Wände:** Die Freecam fliegt durch Blöcke; im Block zeigt die Kamera das Innere wie im Zuschauermodus.
- **Grenze:** Sie reicht so weit, wie dein Spiel die Welt geladen hat (Sichtweite), in der Höhe von 16 Blöcken unter
  dem Weltboden bis 64 Blöcke über der Bauhöhe. An der Grenze bleibt nur die blockierte Richtung stehen, du gleitest
  entlang; das OSD zeigt **SICHTWEITE ENDE**.
- Während ein Menü offen ist, hält die Freecam an und fliegt danach weiter.
- Dein Körper bleibt stehen, wo du gestartet bist, und ist für andere Spieler normal sichtbar.

## 3. Das K-Menü in der Freecam

In der Freecam öffnet K einen Konfigurator mit genau vier Tabs: **Freecam**, **Rates**, **Empfänger** und **Modi**.
Die Kopfzeile zeigt „Freecam · 5″“ statt eines Drohnennamens.

- **Größe:** Whoop, 3,5″, 5″ oder 7″. Der Wechsel geht auch im Flug: Position, Lage, Tempo und Drehraten bleiben,
  ebenso der Scharf-Zustand. Whoop, 5″ und 7″ fliegen wie die eingebauten Baupläne; 3,5″ ist ein fester Bauplan
  (4S, 2450 KV, 89-mm-Props mit drei Blättern) mit rund 240 g, etwa 29 % Schwebegas und rund 120 km/h Spitze, auch
  ohne Add-ons.
- **Kamerawinkel** gilt je Größe, das **Sichtfeld** (Standard 120°) für alle, **OSD** schaltet das Flug-OSD der
  Freecam an oder aus.
- **Rates** gelten je Größe und werden nur für die Freecam gespeichert; deine Drohnen bleiben unberührt.
- Die Suche findet nur Einstellungen dieser vier Tabs.

Im OSD heißt die Freecam „FREECAM · 5″“ (wenn das Element Name an ist). Akku-, Link-, GPS- und Renn-Elemente sind
ausgeblendet; Flugdaten, Flugmodus, Uhr und der Flugbahn-Marker (ohne Latenz) bleiben.

## 4. Wann sie endet

Die Freecam endet sofort, wenn du Schaden nimmst (Lebenspunkte sinken), stirbst, die Dimension wechselst, die
Verbindung trennst, die Welt-Regel ausgeschaltet wird (spätestens im nächsten Tick), eine echte Pilot-Sitzung beginnt
oder du mit F7 zu zuschauen beginnst. Die Aktionsleiste nennt den Grund.

## 5. Was sie nicht kann

Kein Modell, kein Motorton, keine Trails, keine Runden, Gates oder Tricks, keine Action-Cam, keine Blackbox, kein
Zuschauen durch andere. Der Server erfährt nichts von der Freecam: Sie sendet kein Propwash-Paket und erzeugt kein
Entity. Der Server kann sie deshalb wie jede Freecam nicht hart erzwingen; veränderte Clients können die Welt-Regel
umgehen.

## 6. Technik

| Teil | Inhalt |
|---|---|
| `freecam.FreecamPreset` | vier Größen mit Schlüsseln `whoop`, `3_5`, `5`, `7`; 3,5″ als `CreativeSpec` auf Rahmen 5″, Stack AIO 20, Akku 4S 650 (Zielwerte in `FreecamPresetTest`) |
| `freecam.FreecamBounds` | achsweise Begrenzung an geladenen Chunk-Spalten (`ClientChunkCache`, nicht `Level#hasChunk`, das am Client immer wahr ist) und am Höhenband; außerhalb des geladenen Bereichs nur Richtung Spieler frei |
| `freecam.FreecamRules` | Start-Gründe (`Denial`) und Ende-Gründe (`End`), reine Rechnung |
| `freecam.client.FreecamSession` | eigener `DroneSim` mit Tuning ohne Akku, Absturz, Prop-Schlag und Hitze; Umgebung aus `WindModel`, Bodenabstand per Strahl (im Block unendlich), `ZonePhysics`; Parken bei unscharf; Kamera ohne Latenz |
| `freecam.client.FreecamClient` | Taste `key.propwash.freecam`, Tick- und Frame-Haken, `CameraClip.register(FreecamSession::active)`, Tab über `ConfigTabs.registerFreecamOnly(FreecamTab::new)`, Hinweis `freecam_intro` |
| `freecam.client.FreecamConfig` | `config/propwash/freecam.json`: `preset`, `tunes` und `uptilt` je Größe, `fovDeg`, `osd` |
| `freecam.client.FreecamTab` | Tab `freecam` (nur in der Freecam: nicht in `ConfigTabs.create()` und `registeredCount()`, nur in `create(true)`) |

Haken in anderen Dateien: `FpvView` und `PilotView` (Freecam zuerst), `VideoState#fov` (Sichtfeld aus der Config,
sauberes Bild, weil keine Videoquelle aktiv ist), `DroneContext` (Quelle `FREECAM`), `TuneAccess` (dritte Quelle),
`ConfigTab#freecam()`, `ConfigTabs#create(boolean)` und `#registerFreecamOnly`, `SettingsIndex` (durchsucht die Tabs des offenen Menüs), `ConfiguratorScreen` (Filter, Start-Knopf und Sprung zur Regel in
der Fußzeile der Übersicht; nur dort reserviert die Fußzeile Platz für beide Knöpfe und die Gründe, die übrigen Tabs
haben die Fußzeile wie ohne Freecam, beim Wechsel von und zur Übersicht ordnet sich das Fenster neu; ein Sprung aus der Suche holt sein Ziel deshalb vor dem Wechsel aus dem Suchindex des Suchfelds, fehlt der, aus einem frisch gebauten, damit der Treffer auch ab der Übersicht Fokus und Markierung bekommt), `UiClient` (K öffnet den Tab Freecam), `LiveOsdSource` (Quelle Freecam), `KeyDefaults`
(Eintrag `freecam`, F6), `FirstUseHints#FREECAM_INTRO`, `WorldRuleSpecs#ALLOW_FREECAM`, `WorldRulesTab#focusRule`,
`ClientNet#sentCount` (Zähler für Tests), `TransmitterInput` (Tastatur und Maus fliegen in der Freecam),
`OsdWarnings`/`OsdData`/`OsdRenderer`/`OsdHud` (Warnung `freecam_edge`, Akku und Link aus, Vanilla-HUD aus).

**Taste F6:** Vanilla 26.3 belegt F6 nur als Debug-Kombination F3 + F6 (`key.debug.debugOptions`, Debug-Optionen).
F6 allein ist frei. Die Freecam-Taste teilt sich F6 deshalb über `KeyDefaults.SHARED` mit dieser Kombination: kein
roter Konflikt in der Tastenbelegung, F3 + F6 öffnet weiter die Debug-Optionen, F6 allein die Freecam.

**Name der Taste:** Englisch „Toggle freecam drone“, Deutsch „Freecam-Drohne an/aus“. Die Tastenliste sortiert nach dem
Namen; zwischen „Flugmodus wechseln“ und „Gate drehen“, die sich R teilen und im Bildschirmfoto zusammen zu sehen sein
müssen, ist auf Englisch kein Platz für eine weitere Zeile (`FreecamKeyLabelTest`).

## 7. Kernsätze für die Website (Englisch)

- Fly anywhere, no drone needed: the freecam drone exists only on your screen – no model, no collision, no battery.
- Turn on the world rule “Allow freecam drone” (off by default), press F6 or click “Start freecam” in the K menu.
- It flies like a real drone of its size – whoop, 3.5″, 5″ or 7″ – with wind, prop wash and ground effect.
- Disarmed, it parks in the air; it flies through walls and stops at your view distance.
- Its K menu has only Freecam, Rates, Receiver and Modes; rates and camera angle are kept per size.
- It ends when you take damage, die, change dimension, disconnect or the rule is turned off.
