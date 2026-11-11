# Spiel-Features 0.6.0 – Design

Stand 17.10.2026, Basis Tag `v0.5.2` (API 1.6.0, Netzwerkprotokoll 16). Gilt für den Kern `propwash` (Fabric und
NeoForge). Share-Codes und API-Erweiterungen stehen in eigenen Design-Dokumenten dieses Ordners, die Server-Mod ist
zurückgestellt; wo sich die Themen berühren, steht es hier ausdrücklich (Abschnitt 10.3).

| Teil | Thema | Strang | Größe |
|---|---|---|---|
| A | Flugbahn-Marker (Velocity Vector) als OSD-Element | `fpm` | M |
| B | Freecam-Drohne mit eigenem K-Menü und Welt-Regel | `freecam` | L |
| C | RX-Block: ein Kanal der Funke wird zu Redstone | `rx` | L |
| D | Kamerawinkel zusätzlich im K-Menü | `camangle` | S |
| E | Gate-Hinweis beim ersten Setzen, Flugschul-Kapitel „Gate-Menü“ | `gatehint` | M |
| F | Slime federt, Wolle fängt ab, Start von Wolle | `surface` | M |
| G | Befehl `/propwash clear` | `clear` | S |
| H | Motor-Hitze: Prüfung, Umbau der Drosselung nur bei Befund | `heat` | S (Phase A), M (Phase B) |
| – | Offene Frage: Runden mit Flughilfe | – | – |

---

## 1. Ziel und Vorgaben

Ziel: Acht Spiel-Themen aus Roadmap und Discord-Auswahl für 0.6.0 so beschreiben, dass parallele Stränge sie ohne
Rückfragen umsetzen und vollständig prüfen können. Alles muss automatisch oder durch unabhängige Prüfer belegbar sein
(Abschnitt 6).

Die Vorgaben stehen hier **sinngemäß** (Quelle: Roadmap-Notizen, Plan nach 0.5.2, Discord-Auswahl vom 17.10.). Wörtliche
Zitate des Besitzers gehören laut `docs/CONVENTIONS.md` nicht ins Repository, und der öffentliche Export bricht bei
ihnen ab (Entscheidung E1).

- **A – Flugbahn-Marker (Discord 14.10., angenommen):** ein OSD-Element wie in Flugsimulatoren, das zeigt, wohin sich die
  Drohne relativ zum Kamerabild bewegt.
- **B – Freecam-Drohne (Vorgabe 15.10.):**
  - rein clientseitig: keine Kollision, kein Modell, erzeugt keine Chunks;
  - simuliert trotzdem Propwash, Wind usw.; Crash-Schaden entfällt; keine Batterie (kein Verbrauch, kein Spannungseinbruch);
  - eine neue Welt-Regel erlaubt oder verbietet sie und wird an die Clients synchronisiert;
  - die Reichweite endet an den Chunks, die der Client um den Spieler geladen hat; der Server kann das wie bei
    Freecam-Mods nicht hart erzwingen;
  - Taste K öffnet in der Freecam ein reduziertes Menü: Preset Whoop, 3,5″, 5″ oder 7″ und nur Einstellungen, die die
    Freecam betreffen (zum Beispiel Rates); alles andere, etwa die Action-Cam, ist ausgeblendet.
- **C – RX-Block (Vorgabe 17.10.):**
  - an einen Spieler gebunden, nicht an eine Funke; angelernt wird er mit der Funke;
  - genau ein Kanal je Block, etwa Gas oder ein AUX-Kanal;
  - Drohne und RX-Blöcke lassen sich gleichzeitig steuern;
  - Knüppelstellung wird Redstone: 0 % = Stärke 0 bis 100 % = Stärke 15, die Endpunkte 0 % und 100 % lernt man an;
  - Reichweite einstellbar, höchstens 128 Blöcke; wie digital, aber Blöcke dämpfen kaum;
  - außer Reichweite behält der Block seinen letzten Zustand;
  - offen gelassen und hier entschieden: Rezept im Propwash-Stil, Standardreichweite, Ausgabe nur bei Änderung.
- **D – Kamerawinkel (Vorgabe 17.10.):** zusätzlich im K-Menü einstellbar; der bisherige Weg per Schleichen + Rechtsklick
  (Wartungsmenü) bleibt unverändert.
- **E – Gate-Tutorial (Vorgabe 17.10.):** beim ersten Setzen eines Gates ein kleiner Hinweis mit der Wahl, ihn nicht
  wieder anzuzeigen, bzw. das Flugschul-Kapitel zum Gate-Menü öffnen; fehlt das Kapitel, wird es angelegt.
- **F – Slime und Wolle (Vorgabe 17.10.):** wie vorgeschlagen (Slime federt die Drohne ohne Schaden zurück, Wolle und
  Heuballen fangen sie ohne Crash-Schaden ab), aber von Wolle aus kann sie starten. Honig war nicht Teil der Vorgabe und
  behält sein bisheriges Verhalten (E29).
- **G – Aufräumen (Vorgabe 17.10.):** ein Befehl `/propwash clear` (oder ähnlich), der einfach alle Drohnen entfernt.
- **H – Motor-Hitze (Vorgabe 17.10.):** für 0.6.0 noch einmal genau prüfen; dabei gilt, dass gerade größere Kopter
  mit Hitze zu kämpfen haben.
- **Allgemein (Plan 17.10.):** gründlich prüfen (viele unabhängige Prüfer, Mehrspieler auf dediziertem Server,
  Bildschirmfotos, Altwelt-Umstieg); Website mitziehen.

## 2. Umfang

### 2.1 Drin

| Teil | Drin |
|---|---|
| A | Element `flight_path_marker` (Gruppe Flug), Projektion mit geradlinigem und Fisheye-Bild, Bild-Latenz berücksichtigt, außerhalb des Bilds am Rand als Umriss, unter 1,5 m/s ausgeblendet; FPV, Zuschauen (F7) und Freecam; Vorschau im OSD-Editor |
| B | Start/Ende per Taste (Standard F6) und Knopf im K-Menü; vier Presets; eigenes Tuning (Rates) und Kamerawinkel je Preset; Sichtfeld; OSD an/aus; Welt-Regel `propwash:allow_freecam` (Standard aus); Grenze an geladenen Chunks; Wind, Böen, Turbulenz, eigener Propwash, Bodeneffekt, Zonen-Physik; Ende bei Schaden, Tod, Dimensionswechsel, Regel aus |
| C | Block `propwash:rx_block` mit Block-Entity, Binden mit der Funke, Kanal anlernen (Knüppel, AUX-Achse, Taste), Endpunkte anlernen, Reichweite 1–128 (Standard 64), schwache Dämpfung durch Blöcke, Halten außer Reichweite, LED-Zustand im Modell, Rezept, Flugschul-Kapitel |
| D | Zeile „Kamerawinkel“ im Tab Video (Abschnitt „Kamera dieser Drohne“) für die geflogene oder gehaltene Drohne; Suchbegriffe; Hinweis auf das Wartungsmenü |
| E | Hinweis-Fenster beim ersten Setzen eines Gates oder Freiform-Gates, einmal je Welt und Spieler, mit „Kapitel öffnen“, „Verstanden“ und „Nicht wieder anzeigen“; neues Flugschul-Kapitel `gate_menu` |
| F | Block-Tags `#propwash:drone_bouncy` (Slime), `#propwash:drone_cushion` (Wolle, Heuballen), `#propwash:drone_sticky` (Honig, nur Umzug des bisherigen Verhaltens, keine neue Wirkung); Wirkung an allen Seiten; kein Crash, kein Prop-Schlag; Start von Wolle |
| G | `/propwash clear` und `/propwash clear <ziele>`; Rechte-Stufe 2; sauberes Beenden von Pilot-Sitzungen, Heimflügen und Chunk-Tickets |
| H | Phase A: Prüfung mit Kennlinien aller eingebauten und JMP-Motoren, bei Bedarf Kühlkonstanten und Texte; Phase B (Drosselung über den ESC-Temperaturschutz, Motor verliert Leistung und verschleißt, OSD-Warnung „ESC OVERHEAT“) **nur**, wenn Phase A einen Befund nach §3.H zeigt |

### 2.2 Bewusst nicht drin

- **A:** keine Vorhalte-Rechnung (Schwerkraft, Ballistik), kein Flugbahn-Marker im Sichtflug-HUD, keine API dafür (kommt
  mit dem OSD-Element-Register des API-Strangs automatisch, falls dieser die eingebauten Elemente umzieht).
- **B:** kein Modell, kein Ton der Motoren, keine Trails, keine Tricks-Wertung, keine Runden oder Gate-Durchflüge, keine
  Action-Cam, keine Blackbox, kein Zuschauen durch andere, kein Server-Paket. Kein Flugschul-Kapitel (die Freecam ist
  unsichtbar; ein einmaliger Hinweis beim ersten Start genügt).
- **C:** keine API, keine Ausgabe an Komparatoren über die normale Signalstärke hinaus, keine Mehrkanal-Blöcke, keine
  Bindung an eine bestimmte Funke, keine Steuerung über die Tastatur außerhalb einer Flug-Sitzung (Abschnitt 8).
- **D:** keine eigene Taste „Kamerawinkel ±“ im Flug (im Discord-Vorschlag optional, nicht beauftragt).
- **E:** keine allgemeine Seite „Neu in dieser Version“ (Discord-Punkt 13 ist nur mit dem Gate-Teil beauftragt).
- **F:** keine Teppiche, Betten, Schnee-Schichten (Kollision bei Zäunen und Schnee bleibt laut Discord-Auswahl gewollt);
  keine Änderung für unbemannte, fallende Drohnen (sie nutzen schon die Vanilla-Physik mit Slime-Abprall).
- **G:** keine Drops, keine Rückgabe an Besitzer, kein Laden entladener Chunks, keine automatische Einsammel-Regel.
- **H:** kein Totalausfall „Motor brennt durch“, keine neuen API-Felder, keine Änderung an Flug-Goldens unterhalb der
  Temperaturschwellen, kein Umbau ohne Befund aus Phase A.
- **Honig:** keine neue Wirkung (nicht beauftragt); der Tag `drone_sticky` bildet nur das heutige Verhalten ab.
- **Gestapelte Gates, XT60-Optik, Druckplatten-PR:** eigene Stränge, nicht Teil dieses Dokuments.
- **Runden mit Flughilfe:** keine Änderung, nur offene Frage (Abschnitt 9.2).

## 3. Architektur und Datenformate

Allgemein: Kein Kommentar im Mod-Code, reine Rechnungen als reines Java mit JUnit, Texte nur über `en_us`/`de_de`,
Mixins nur unter `mixin.<modul>`. Neue Pakete: `de.lardtom.propwash.freecam` und `de.lardtom.propwash.rx` (je mit
`.client`), beide mit Common- und Client-Einstieg wie alle Module (`docs/ARCHITECTURE.md`).

### 3.A Flugbahn-Marker

**Rechnung (reines Java, neu):** `osd.FlightPathMarker`

- Eingabe: Geschwindigkeit im Weltsystem (m/s), Kamera-Projektion (`osd.OsdProjection`, geradlinig oder Fisheye),
  Rand des OSD-Bereichs.
- Ausgabe: `State` mit `visible`, `alpha` (0–1), Bildposition, `inView` (Marker im Bild) und Randwinkel.
- Ausblenden: unter 1,0 m/s unsichtbar, zwischen 1,0 und 2,0 m/s linear eingeblendet (Richtung bei Schwebeflug sinnlos).
- Außerhalb des Bilds oder hinter der Kamera: Position auf der Rand-Ellipse wie beim Home-Pfeil
  (`OsdProjection#screenAngle`), gezeichnet nur als Umriss.

**Geschwindigkeit passend zum gezeigten Bild:** Das FPV-Bild zeigt eine Pose aus der Vergangenheit (Latenz der
Videostrecke). Der Marker nimmt deshalb nicht die aktuelle Sim-Geschwindigkeit, sondern die Ableitung der **gezeigten**
Posen: `VideoState.sampleDisplayed` zum Bildzeitpunkt und 50 ms davor, Differenz durch Zeitabstand, geglättet mit PT1
(τ = 40 ms). Dafür bekommt `video.client.VideoState` eine Abfrage `sampleDisplayedAt(nanos, pos, rot)` mit Zeitversatz
(gleiche Puffer, keine neue Speicherung). Zuschauen (F7): dieselbe Ableitung aus dem Posen-Strom der fremden Drohne.
Freecam: Sim-Geschwindigkeit direkt (keine Latenz).

**OSD-Daten:** `osd.OsdData` bekommt `velocityX/Y/Z` (m/s, Weltsystem, `NaN` = unbekannt), kopiert in `copyTo`, Beispielwerte in
der Editor-Vorschau (`OsdData.sample`: 18 m/s vorwärts, leicht sinkend).

**Element:** `OsdElement.FLIGHT_PATH_MARKER(15, 8, Align.CENTER, false, false, Group.FLIGHT)`, Schlüssel
`flight_path_marker`, `usesLineOpacity()` = wahr (wie Fadenkreuz und Horizont), im Preset FULL an (FULL schaltet alle
ein), sonst aus. Zeichnen in `osd.client.OsdRenderer#flightPathMarker()`: Kreis mit zwei Flügeln und Leitwerk aus
Pixel-Rechtecken (wie `homeMarker`), skaliert mit der OSD-Skala, Farbe und Umriss aus dem OSD-Stil; bei Videoverlust
ausgeblendet wie Fadenkreuz und Horizont. `record(...)` meldet das Rechteck (Selbsttests lesen es).

**Formate:** OSD-Layout (`OsdLayoutCodec`, Version 1) bleibt gleich; ein neuer Schlüssel. Ältere Clients überspringen
unbekannte Elemente schon heute (`OsdElement.byKey` liefert `null`). Kein Netzwerk.

### 3.B Freecam-Drohne

**Reines Java (`freecam`):**

| Klasse | Inhalt |
|---|---|
| `FreecamPreset` | Enum `WHOOP`, `THREE_FIVE`, `FIVE`, `SEVEN`; Schlüssel `whoop`, `3_5`, `5`, `7`; Bauplan und Flugwerte (siehe unten) |
| `FreecamBounds` | Schritt-Begrenzung: neue Position nur, wenn ihre Chunk-Spalte geladen ist und die Höhe zwischen Weltboden − 16 und Bauhöhe + 64 liegt; sonst bleibt die Achse stehen und die Geschwindigkeit in diese Richtung wird 0 (achsweise, damit man an der Grenze entlanggleitet) |
| `FreecamRules` | Darf starten? Regel synchronisiert und an (Standard aus), Spieler lebt, keine Pilot-Sitzung, kein Zuschauen, kein offenes Menü; liefert den Grund für die Meldung |

**Presets:** Whoop = `DroneBuild.WHOOP`, 5″ = `DroneBuild.FREESTYLE`, 7″ = `DroneBuild.LONG_RANGE`. 3,5″ gibt es im Kern
nicht als Teil (nur in JMP); die Freecam baut es deshalb als feste Kreativ-Werte (`creative.CreativeSpec`) auf einem
eingebauten Bauplan (Stack `AIO_20`, Akku `B4S_650`) und rechnet die Flugwerte wie die Kreativ-Drohne
(`creative.CreativeFlight`). Zielwerte 3,5″: Props 89 mm, 3 Blätter, Steigung 3,0″, Motor 2450 KV, 20 g, Radstand
150 mm, Abfluggewicht 230–280 g, Schwebegas 22–32 %, Spitze 100–130 km/h. Die Feinwerte stellt der Strang mit
`sim.BuildAnalysis` in diese Bänder (JUnit, Abschnitt 6). Unabhängig von installierten Add-ons.

**Client (`freecam.client`):**

| Klasse | Inhalt |
|---|---|
| `FreecamClient` | Einstieg: Taste `key.propwash.freecam` (Standard F6, in Propwash frei; dass Vanilla 26.3 sie nicht belegt, prüft der Strang zuerst), Tick, Ende-Bedingungen, Hinweis beim ersten Start (`ui.client.FirstUseHints`, Schlüssel `freecam_intro`) |
| `FreecamSession` | Singleton: eigener `DroneSim` mit den Preset-Werten; Tuning `infiniteBattery = true`, `batterySag = false`, `crashDisarm = false`, `propStrikes = false`, `thermalLimits = false`, Physik-Regeln (Schwerkraft, Propwash, Bodeneffekt, Wind, Turbulenz) aus Welt-Regeln und Zonen wie beim Flug; Eingabe aus `input.RadioState` (Funke, Gamepad, Tastatur und Maus wie im FPV); kein `afterMove`-Kontakt |
| `FreecamConfig` | `JsonConfig` `freecam`: `preset`, `tunes` (je Preset die geänderten Tuning-Werte), `uptilt` (je Preset), `fovDeg` (Standard 120), `osd` (Standard an) |
| `FreecamTab` | Konfigurator-Tab `freecam`: Preset (vier Chips), Kamerawinkel, Sichtfeld, OSD an/aus, Knopf „Freecam beenden“; im normalen K-Menü steht im Tab Übersicht ein Knopf „Freecam starten“ (ausgegraut mit Grund, wenn nicht erlaubt; wer die Regel ändern darf – Einzelspieler, Operator –, bekommt daneben den Sprung zur Regel im Tab Welt-Regeln) |

**Ablauf:**

1. Start: Pose = Augenposition des Spielers, Kurs = Blickrichtung, waagerecht, Geschwindigkeit 0, **unscharf und
   geparkt** (eingefroren in der Luft). Kein Entity, kein Paket an den Server.
2. Scharf schalten wie beim Fliegen (Schalter oder Taste, Gas unten). Erst dann rechnet die Sim. Unscharf in der Luft:
   die Freecam parkt wieder an Ort und Stelle (ohne Kollision würde sie sonst durch die Welt fallen, Entscheidung E9).
3. Umgebung je Physik-Frame: `drone.WindModel.fill` mit der Client-Welt, Bodenabstand per Strahl nach unten in geladenen
   Chunks (in einem Block: unendlich), `fluid = NONE`, keine Fasern und Ranken. Liegt die Zonen-Auflösung heute nur in
   `PilotSession`, zieht der Strang sie ohne Verhaltensänderung in `zone.client.ZonePhysics.at(level, x, y, z)` um.
4. Nach jedem Schritt `FreecamBounds`; an der Grenze OSD-Warnung „SICHTWEITE ENDE“ / „VIEW DISTANCE“.
5. Preset-Wechsel im Flug: neuer `DroneSim`, Position, Lage, Geschwindigkeit und Drehraten werden übernommen, Scharf-Zustand
   bleibt.
6. Ende: Taste F6 oder „FPV beenden“ (Y), Schaden am Spieler (Lebenspunkte sinken), Tod, Dimensionswechsel, Trennung,
   Regel aus (innerhalb eines Ticks), Start einer echten Pilot-Sitzung oder des Zuschauens. Danach Kamera zurück zum
   Spieler; Meldung mit Grund in der Aktionsleiste.

**Kleine Haken in fremden Dateien** (der Strang besitzt nur diese Stellen):

| Datei | Haken |
|---|---|
| `drone/client/FpvView` | `active()` und `cameraPose(...)`: Freecam zuerst; Kamera ohne Latenz aus `FreecamSession` |
| `drone/client/CameraClip` (legt `addon-collision` an, siehe `addon-api-namespace.md` §10) | Freecam meldet sich mit `CameraClip.register(FreecamSession::active)` an: kein Kamera-Schutz, Darstellung in Blöcken wie im Zuschauermodus. `FpvEyeGuard` und der Kamera-Mixin gehören `addon-collision` und werden von diesem Strang nicht geändert; `freecam` rebased vor dem Merge auf `addon-collision` |
| `drone/client/PilotView` | `active()` schließt die Freecam ein (Spieler bewegt sich nicht, Hand und HUD wie im FPV) |
| `video/client/VideoState` | Sichtfeld aus `FreecamConfig.fovDeg`, sauberes Bild (keine Störungen, keine Videoverbindung) |
| `ui/client/DroneContext` | neue Quelle `FREECAM` |
| `drone/client/TuneAccess` | dritte Quelle Freecam: `current()`/`apply()` lesen und schreiben `FreecamConfig` und die laufende Sim |
| `ui/client/config/ConfigTab`, `ConfigTabs`, `ConfiguratorScreen`, `ui/client/search/SettingsIndex` | `ConfigTab#freecam()` (Standard falsch); in der Freecam öffnet K den Konfigurator gefiltert auf `freecam`, Rates, Empfänger und Modi; die Suche findet nur sichtbare Tabs |
| `ui/client/UiClient` (Taste K) | in der Freecam gefiltert öffnen |
| `osd/client/LiveOsdSource` | Quelle Freecam: Flugdaten aus der Sim, Akku, Link, GPS, Rennen leer (Elemente blenden sich aus), Name „FREECAM · 5″“ |
| `input/client/KeyDefaults` | Eintrag `freecam` (F6) |
| `input/client/screen/ModesTab`, `input/client/screen/ReceiverTab`, `ui/client/tab/RatesTab` | je eine Zeile: Überschreibung `freecam()` = wahr (Sammeldatei-Regel; `ModesTab` gehört sonst `addon-switches`) |
| `server/client/WorldRulesTab` | Sprung zur Regel aus dem ausgegrauten Knopf: eine angehängte Methode `focusRule(String)` und ihr Aufruf, sonst nichts (die Datei gehört sonst `addon-world-rules`) |
| `server/rules/WorldRuleSpecs` | eine Zeile `ALLOW_FREECAM = add(RuleSpec.bool("allow_freecam", false, Group.GAMEPLAY))` (Standard aus, E7) |

**Netzwerk:** keine neuen Pakete. Die Regel reist mit der bestehenden Welt-Regel-Synchronisation. Ohne synchronisierten
Wert (zum Beispiel Server ohne Propwash) gilt „nicht erlaubt“.

### 3.C RX-Block

**Reines Java (`rx`):**

| Klasse | Inhalt |
|---|---|
| `RxSource` | Record `(Kind kind, int index)`; `STICK` 0–3 (Roll, Nick, Gier, Gas nach Zuordnung und Kalibrierung), `AXIS` 0–31 (rohe Achse des Geräts, AUX), `BUTTON` 0–31 (Taste, 0 oder 1); Codec |
| `RxMapping` | Stärke = runden(15 · (v − tief) / (hoch − tief)), begrenzt auf 0–15; tief > hoch dreht um; tief = hoch wirkt als Schalter (v ≥ tief → 15); Hysterese 0,3 Stufen gegen Flackern an Stufengrenzen |
| `RxLink` | effektive Entfernung = Abstand Auge–Blockmitte + 0,5 je undurchsichtigem Vollblock auf der Linie (höchstens 64 gezählt); in Reichweite, wenn ≤ eingestellte Reichweite (1–128). Liest nur Blöcke in geladenen Chunks (Zugriff über `ObstructionTracer.World` mit Prüfung `isLoaded`); Abschnitte in nicht geladenen Chunks zählen ohne Dämpfung, nichts wird dafür geladen |
| `RxLearn` | erkennt die Quelle mit der größten Bewegung (mindestens 30 % Weg) in 1,5 s; Vorschläge für Standard-Endpunkte je Art |

**Minecraft (`rx`):**

| Klasse/Datei | Inhalt |
|---|---|
| `RxModule` | Registereinträge Block, Item, Block-Entity-Typ; Payloads; Server-Tick; Befehls- und Tag-freie Daten |
| `RxBlock` | Blockzustände `power` (0–15) und `link` (`none`, `linked`, `lost`); Signalquelle an allen sechs Seiten wie ein Tageslichtsensor (schwaches Signal, kein starkes Durchleiten); kein Ticker |
| `RxBlockEntity` | NBT siehe unten; Sync zum Client über das Vanilla-Block-Entity-Paket (für Fenster und Tooltip) |
| `RxIndex` | Verzeichnis je Welt ohne Scans (wie `relay.RepeaterIndex`), nach Besitzer-UUID |
| `RxInputs` | letzte Kanalwerte je Spieler (Server) |
| `RxServer` | alle 2 Ticks: je gebundenem Block mit Besitzer online in derselben Welt **und einem Chunk, in dem Blöcke ticken** (`level.shouldTickBlocksAt(pos)`; `isLoaded` reicht nicht, weil es auch für die geladenen Randchunks gilt, deren Nachbarn fehlen können und in die ein `setBlock` Nachbar-Updates schicken würde; sonst übersprungen; kein `getBlockState`, `setBlock` oder Ticket außerhalb davon) → Link prüfen (alle 10 Ticks neu, sonst Cache) → bei Link Stärke rechnen und den Blockzustand **nur bei Änderung** setzen; ohne Link `link = lost`, Stärke bleibt; alle 20 Ticks je Spieler den Bedarf neu rechnen. Ein Block in einem entladenen Chunk behält den gespeicherten Zustand, bis sein Chunk wieder geladen ist (Entscheidung E36) |
| `rx.client.RxClient` | sendet Kanalwerte nur bei Bedarf, höchstens 20-mal je Sekunde, nur bei Änderung um mehr als 1/512 des Wegs, sonst alle 2 s als Lebenszeichen |
| `rx.client.RxScreen` | Fenster im Propwash-Theme (Abschnitt 5) |
| Assets | `blockstates/rx_block.json`, Modelle `rx_block` mit LED aus/grün/rot, Texturen, Item-Modell, Rezept, Beute-Tabelle (droppt sich selbst ohne Daten), Tag `mineable/pickaxe` |

**NBT der Block-Entity:**

```
{
  "Format": 1,
  "Bind": { "pilot": [I; …] },          fehlt = ungebunden (gleicher Codec wie drone.bind.ReceiverBind)
  "Source": { "kind": "stick", "index": 3 },   fehlt = kein Kanal
  "Low": -1.0f, "High": 1.0f,
  "Range": 64
}
```

Die Ausgabe steckt im Blockzustand `power` und überlebt damit Neustart und Entladen ohne eigenes Feld.

**Netzwerk (neu):**

| Payload | Richtung | Inhalt | Prüfung am Server |
|---|---|---|---|
| `propwash:rx_demand` | S2C | Masken für Knüppel (4 Bit), Achsen (32 Bit), Tasten (32 Bit); alles 0 = nichts senden | – |
| `propwash:rx_input` | C2S | Folgenummer (varint), dieselben Masken, Werte als `short` (−32767…32767 ↔ −1…1), Tasten als Bitfeld | nur mit Bedarf, endlich, Masken ⊆ Bedarf, höchstens 25 je Sekunde (Rest verworfen) |
| `propwash:rx_config` | C2S | `BlockPos`, Aktion `BIND`, `UNBIND`, `SOURCE`, `LOW`, `HIGH`, `RANGE`, Wert | Abstand zum Block ≤ Interaktionsreichweite, Block geladen, Rechte (unten), Werte endlich, Reichweite 1–128 |

**Rechte:** Ungebunden darf jeder mit einer Funke in der Hand binden. Gebunden ändern und lösen dürfen nur der gebundene
Spieler und Operatoren (Stufe 2). Andere bekommen „Dieser RX-Block ist an %s gebunden.“ Das schützt Redstone-Anlagen auf
Survival-Servern.

**Bedienung:**

- Rechtsklick mit der Funke: ungebunden → binden und Fenster öffnen; eigener Block → Fenster öffnen; fremder Block → Meldung.
- Schleichen + Rechtsklick mit der Funke auf den eigenen Block: Bindung lösen (mit Bestätigung im Fenster).
- Rechtsklick ohne Funke: Zustand in der Aktionsleiste (Kanal, Stärke, Besitzer, Reichweite).

**Rezept (Propwash-Stil, wie der Signal-Repeater):** Blitzableiter oben (Antenne), Kupferbarren links und rechts,
Platine in der Mitte, Redstone unten → 1 RX-Block. Muster `" A "`, `"CBC"`, `" R "`.

### 3.D Kamerawinkel im K-Menü

- `video.client.VideoConfigTab`: neuer Abschnitt „Kamera dieser Drohne“ unter der Sichtfeld-Zeile: Regler `StepSlider` im
  Bereich des Halters (`drone.CameraMount.range(build)`), `−`/`+` (Schleichen 5°), `↺` Standard, Anzeige „Auch im
  Wartungsmenü der Drohne (Schleichen + Rechtsklick)“.
- Wert über `TuneModel` mit Schlüssel `camera.uptiltDeg` (`TuneMapping.CAMERA_UPTILT`); Anwenden über das bestehende
  `TuneAccess` (fliegen oder halten) und den bestehenden Weg `propwash:flight_tune`; der Server begrenzt wie heute in
  `DronePiloting#sanitize`. **Kein neues Paket.**
- Ohne Drohne: Zeile ausgegraut mit „Fliege oder halte eine Drohne, um ihren Kamerawinkel zu ändern.“
- Suche (`SettingsIndex`): Begriffe camera angle, uptilt, tilt, Kamerawinkel, Neigung, Kamera.

### 3.E Gate-Hinweis und Kapitel „Gate-Menü“

- **Erkennung rein auf dem Client, ohne Eingriff in `GateItem`** (dort arbeitet der Strang für gestapelte Gates):
  `race.client.GatePlaceHint` merkt sich einen Benutzen-Klick mit einem Gate- oder Freiform-Gate-Item in der Hand und
  sucht in den nächsten 30 Ticks ein **neues** Gate-Entity im Umkreis von 8 Blöcken (bekannte Ids vorher gemerkt). Nur
  dann zählt es als gesetzt, auch auf dem dedizierten Server mit Verzögerung.
- **Speicher:** `ui.client.FirstUseHints`, Schlüssel `gate_menu`. Gezeigt beim ersten Setzen eines Gates **einmal je
  Welt und Spieler** (Welt-Schlüssel: Ordnername im Einzelspieler, Server-Adresse im Mehrspieler; gespeichert auf dem
  Client des Spielers). Mit Haken bei „Nicht wieder anzeigen“ oder nach „Kapitel öffnen“ nie wieder, in keiner Welt.
  Zurücksetzen über den bestehenden Weg für Hinweise (falls keiner existiert: Knopf „Hinweise zurücksetzen“ im Tab
  Oberfläche, eigener Abschnitt des Strangs).
- **Fenster:** `race.client.GateHintScreen` (Theme, kein Pausieren im Mehrspieler).
- **Kapitel:** `guide.scenes.GateMenuScene` (`gate_menu`), vier Schritte: Menü öffnen (Schleichen + Rechtsklick mit Gate
  oder Kurs-Werkzeug), Größe und Drehung (Mausrad, Alt für die Maus, Enter übernimmt), Seite Redstone (Signal,
  Pulslänge, nur im Rennen; Lampe unter dem Gate leuchtet beim Durchflug), Freiform-Gates nutzen dasselbe Menü.
  Anmeldung in `guide.GuideScenes` (eine Zeile). Das bestehende Kapitel `gate_free` bleibt unverändert.

### 3.F Slime und Wolle (Honig unverändert)

- **Tags** (Daten, per Datenpaket erweiterbar, Vanilla synchronisiert sie an Clients):
  - `data/propwash/tags/block/drone_bouncy.json`: `minecraft:slime_block`
  - `data/propwash/tags/block/drone_cushion.json`: `#minecraft:wool`, `minecraft:hay_block`
  - `data/propwash/tags/block/drone_sticky.json`: `minecraft:honey_block`
- **Antwort je Fläche (reines Java, `sim.SurfaceResponse`):** Record `(restitution, grip, impactScale)`; Werte:

| Material | Rückprall | Haftung | Aufprall zählt | Wirkung |
|---|---|---|---|---|
| normal | Drohne (wie heute) | aus Block-Reibung (wie heute) | 1,0 | unverändert |
| bouncy (Slime) | 0,8 (Vanilla-Wert für Nicht-Lebewesen) | 1,0 | 0 | federt an allen Seiten, kein Crash, kein Prop-Schlag |
| cushion (Wolle, Heu) | 0,05 | 1,5 | 0 | fängt ab, bleibt liegen, Start möglich |
| sticky (Honig) | 0 | 1,5 | 1,0 | wie heute (nur auf den Tag umgezogen, keine neue Wirkung) |

- **`sim.MoveReport`:** neues Feld `surfaceImpactScale` (Standard 1,0, `NaN` = 1,0). `DroneSim#classify` rechnet mit
  `v · surfaceImpactScale` für Crash, harten Aufprall, Prop-Schlag und Verschleiß; der physikalische Rückprall bleibt
  unberührt. Standardwerte lassen alle Flug-Goldens **bitgleich**.
- **Welche Fläche:** neu `drone.SurfaceMaterials.at(level, hitbox, hitX, hitY, hitZ)`: liest die Blöcke an der berührten
  Seite der Hülle (nicht mehr nur den Block darunter), nimmt das Material mit der größten berührten Fläche, bei
  Gleichstand die weichere Antwort. Aufgerufen in `drone/client/PilotSession#fillSurface` (ersetzt die festen
  Slime/Honig-Zeilen) und an der entsprechenden Stelle der Server-Flüge (`rescue.ServerReturnFlight`, `rescue.VoxelFlight`).
- **Start von Wolle:** keine Sonderregel nötig; die Kissen-Antwort dämpft nur Aufprall, Abheben bleibt normal. Ein
  Selbsttest sichert das ab.

### 3.G `/propwash clear`

- `server.ServerCommands`: Unterbefehl `clear` mit `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`.
  - `/propwash clear` – alle `QuadcopterEntity` in allen geladenen Welten;
  - `/propwash clear <ziele>` – Entity-Auswahl, wirkt nur auf Drohnen darin.
- Logik in neuer Klasse `server.DroneClear`: je Drohne derselbe Aufräumweg wie beim Aufheben, aber ohne Item: Pilot-
  Sitzung beenden (Pilot bekommt „Deine Drohne wurde entfernt.“), autonomen Heimflug beenden, Sichtanker und
  Chunk-Tickets lösen, Zuschauer abmelden; dann `discard()`.
- Rückmeldung an den Absender und wie bei Vanilla an Operatoren: „%s Drohnen entfernt (%s).“ mit Anzahl je Dimension;
  ohne Treffer „Keine Drohnen in geladenen Bereichen gefunden.“ Server-Log-Zeile mit Absender und Anzahl.

### 3.H Motor-Hitze

**Heute (geprüft im Code):** `sim.Thermal` rechnet Motor- und ESC-Temperatur. Die **Leistungsgrenze** hängt an der
**Motortemperatur**: ab `derateStartC` (Standard 130 °C) sinkt sie linear bis 20 % bei `maxTempC` (150 °C), dazu
Verschleiß. Der ESC wird zwar warm gerechnet, drosselt aber nicht. Der Kupfer-Temperaturkoeffizient wirkt nur mit
`SimTuning.thermalEffects`, das im Spiel aus ist. OSD: „MOTOR ÜBERHITZT“, Meldung „Motoren überhitzen – Leistung wird
begrenzt“, Werkbank-Warnung „Motoren überhitzen und regeln ab …“.

**Fachlicher Stand (im Strang mit Quellen zu belegen):**

- Ein Brushless-Motor hat keinen eigenen Hitzeschutz und regelt nicht ab. Abgeregelt wird im ESC: BLHeli_32, AM32 und
  Bluejay haben einen einstellbaren Temperaturschutz am ESC, Betaflight selbst drosselt nicht nach Motortemperatur.
  Standardschwellen und Kennlinie der drei Firmwares recherchiert der Strang und übernimmt sie.
- Ein heißer Motor verliert trotzdem Leistung: Kupferwiderstand steigt um etwa 0,39 % je Kelvin, Neodym-Magnete verlieren
  reversibel etwa 0,1 % Fluss je Kelvin, oberhalb der Magnet-Güteklasse (N, H, SH, UH) auch dauerhaft; oberhalb der
  Lackdraht-Klasse droht ein Wicklungsschaden.
- Größere Kopter: Verluste wachsen etwa mit der Leistung, die Kühlfläche nur mit der Oberfläche; große Props drehen
  langsamer und kühlen weniger. Das Modell bildet das schon ab (Kühlung ∝ Masse^(2/3), Wärmekapazität ∝ Masse); die
  Prüfung zeigt, ob die Werte stimmen.

**Phase A – Prüfung (zuerst, reines JUnit):** `sim.ThermalCharacterizationTest` schreibt `build/calibration/thermal.txt`:
für jeden eingebauten und jeden JMP-Motor im typischen Bauplan Beharrungstemperatur von Motor und ESC und Zeit bis zur
Schwelle bei Schweben, 50 % Gas im Vorwärtsflug und Vollgas am Boden. Zielbänder (Plausibilität, aus Erfahrungswerten,
im Strang zu belegen): Freestyle 5″ nach aggressivem Akku warm bis heiß, aber unter der Schwelle; 7″ im Reiseflug
deutlich unter der Schwelle, bei Dauervollgas oder schwerer Last darüber; X-Class und Cinelifter erreichen die Schwelle
bei Dauerlast als erste; Whoops werden bei Dauervollgas heiß. Weicht das Modell ab, passt der Strang nur die
Kühlkonstanten an. Texte, die behaupten, der Motor regle selbst ab, werden in Phase A neutral formuliert („Motoren sehr
heiß – Leistung sinkt“), weil das in beiden Modellen stimmt.

**Entscheidungsregel nach Phase A** (Ergebnis und Begründung in `docs/design/realism/motor-heat.md`, Prüfung durch V12):
Phase B wird nur gebaut, wenn Phase A mindestens einen dieser Befunde belegt:

1. Ein Zielband wird verfehlt, und angepasste Kühlkonstanten allein bringen es nicht hinein.
2. Die Drosselung greift in einem Fall, in dem echte Hardware nicht abregelt (zum Beispiel 5″ mit einem normalen
   Freestyle-Akku), oder sie bleibt aus, wo echte ESCs abregeln (7″, X-Class oder Cinelifter unter Dauerlast).
3. Ein für Spieler sichtbarer Unterschied zum echten Verhalten, der sich nicht mit Text beheben lässt.

Ohne Befund bleiben Modell, Ereignisse, Warnungen und Goldens wie in 0.5.2; geändert werden höchstens Kühlkonstanten
(mit begründetem Diff der Kalibrierung) und Texte.

**Phase B – Umbau (nur bei Befund):**

| Was | Neu |
|---|---|
| Drosselung | nur noch der **ESC-Schutz**: ESC-Temperatur über der ESC-Schwelle → Leistungsgrenze nach der Kennlinie aus Phase A; Schwelle je Stack-Klasse als Konstante in `sim.Thermal` (keine API-Felder) |
| Motor über `derateStartC` | keine Drosselung mehr; stattdessen Leistungsverlust aus Physik (Drehmomentkonstante sinkt mit der Übertemperatur, höchstens −10 % bei `maxTempC`) und Verschleiß wie heute |
| Motor über `maxTempC` | stärkerer Verschleiß wie heute (`wearPerSecondAtMax`), Warnung bleibt; kein Totalausfall (Entscheidung E31) |
| Ereignisse | `MOTOR_DERATE` entfällt; neu `MOTOR_HOT` und `ESC_DERATE` (mit Hysterese wie heute) |
| Unterhalb der Schwellen | nichts ändert sich: Flug-Goldens und Bewegungs-Fingerabdrücke bleiben bitgleich |
| Welt-Regel | `propwash:hardware_limits` bleibt (Name, Standard), nur Beschreibung neu |

Die JSON-Felder `derate_start_c` und `max_temp_c` von Motoren behalten Namen und Werte; ihre Bedeutung wird zu „ab hier
verliert der Motor Leistung“ bzw. „ab hier starker Verschleiß“. Das Addon-Handbuch und `docs/api/changes.md` beschreiben
das (Text liefert dieser Strang dem API-Strang).

## 4. Kompatibilität

| Bereich | Wirkung |
|---|---|
| Alte Welten (0.5.2) | laden unverändert. Neue Welt-Regel bekommt den Standard (aus). Drohnen, Gates, Kurse, Tunings unverändert; der Kamerawinkel bleibt am Tuning der Drohne. Keine Änderung am Speicherformat (`SaveFormatGoldenTest` bleibt grün). |
| Zurück auf 0.5.2 | RX-Blöcke werden wie bei jedem fehlenden Block zu Luft; Hinweis in den Release-Notes. Sonst nichts. |
| Client-Configs | OSD-Layout: neues Element aus (außer Preset FULL). `freecam.json` neu. Hinweis-Speicher bekommt neue Schlüssel. |
| Share-Codes PW1/PW2/PW3 | nicht berührt (RX-Block und Freecam sind keine Bauteile, Freecam-Presets sind nicht teilbar). |
| Netzwerkprotokoll | 0.6.0 erhöht **einmal** auf 17 (neue Payloads `rx_*`, neue Welt-Regel im Abgleich); die Nummer setzt der Basis-Commit von 0.6.0, kein Strang (Übersicht `README.md` K2). Client und Server mit 0.5.2 werden beim Beitritt klar getrennt (bestehende Beitritts-Prüfung). |
| API (SemVer) | dieses Dokument fügt **keine** öffentliche API hinzu. Nur falls Phase B kommt: geänderte Bedeutung von `MotorThermal#derateStartC` → Teil der Minor-Erhöhung, die der API-Strang für 0.6.0 ohnehin vornimmt (1.7.0); kein Bruch der Signaturen. |
| Just More Parts | Motor-Daten bleiben gültig; die JMP-Prüfstände laufen gegen 0.6.0 neu (Phase A enthält JMP). |
| Add-on „Noclip“ der Community | überschneidet sich mit der Freecam; keine technische Wechselwirkung, nur Kommunikation (Abschnitt 8). |
| Iris/Shaderpacks | Freecam nutzt denselben Kamerapfad wie FPV; Schattenpass folgt der Kamera wie heute. |

## 5. UI und Texte

Alle Fenster im Propwash-Theme (`ui.client.theme`), Texte mit `TextFit`/`FlowWrap`, vollständig bedienbar bis
**320 × 240 GUI-Punkte** in Englisch und Deutsch (`docs/design/ui-scaling.md`); die `ui-scale:`-Prüfung bekommt je neues
Fenster einen Fall. OSD-Warnungen deutsch in Großbuchstaben wie die bestehenden.

| Schlüssel (Auswahl) | Englisch | Deutsch |
|---|---|---|
| `propwash.osd.element.flight_path_marker` | Flight path marker | Flugbahn-Marker |
| `…flight_path_marker.description` | Shows where the drone is really moving in the camera image. Hidden below 1.5 m/s. | Zeigt, wohin sich die Drohne im Kamerabild wirklich bewegt. Unter 1,5 m/s ausgeblendet. |
| `key.propwash.freecam` | Freecam drone on/off | Freecam-Drohne an/aus |
| `gamerule.propwash.allow_freecam` | Allow freecam drone | Freecam-Drohne erlauben |
| `…allow_freecam.description` | On: players can fly a free camera drone that exists only on their own screen – no collision, no model, no battery. It reaches only the area their game has loaded and can look through walls. Off by default. Modified clients can ignore this setting. | An: Spieler können eine freie Kamera-Drohne fliegen, die es nur auf ihrem eigenen Bildschirm gibt – ohne Kollision, Modell und Akku. Sie reicht nur so weit, wie ihr Spiel die Welt geladen hat, und sieht durch Wände. Standardmäßig aus. Veränderte Clients können diese Einstellung umgehen. |
| `propwash.freecam.tab` | Freecam | Freecam |
| `propwash.freecam.preset` | Size | Größe |
| `propwash.freecam.preset.whoop/3_5/5/7` | Whoop · 3.5″ · 5″ · 7″ | Whoop · 3,5″ · 5″ · 7″ |
| `propwash.freecam.start` / `.exit` | Start freecam / End freecam | Freecam starten / Freecam beenden |
| `propwash.freecam.denied` | The freecam drone is turned off in this world (world rule). | Die Freecam-Drohne ist in dieser Welt ausgeschaltet (Welt-Regel). |
| `propwash.freecam.to_rule` | Open world rule | Zur Welt-Regel |
| `propwash.freecam.ended.hurt` | Freecam ended: you took damage. | Freecam beendet: Du hast Schaden genommen. |
| `propwash.freecam.ended.rule` | Freecam ended: turned off by a world rule. | Freecam beendet: per Welt-Regel ausgeschaltet. |
| `propwash.freecam.intro` | Freecam: arm to fly, K for settings, F6 to return. | Freecam: scharf schalten zum Fliegen, K für Einstellungen, F6 zurück. |
| `propwash.osd.warning.freecam_edge` | VIEW DISTANCE | SICHTWEITE ENDE |
| `block.propwash.rx_block` | RX Block | RX-Block |
| `propwash.rx.tooltip` | Turns one channel of your radio into a redstone signal (0–15). | Macht einen Kanal deiner Funke zum Redstone-Signal (0–15). |
| `propwash.rx.bound_to` | Bound to %s | Gebunden an %s |
| `propwash.rx.foreign` | This RX block is bound to %s. | Dieser RX-Block ist an %s gebunden. |
| `propwash.rx.learn` / `.learn.prompt` | Learn channel / Move the stick or switch the block should follow | Kanal anlernen / Bewege den Knüppel oder Schalter, dem der Block folgen soll |
| `propwash.rx.set_low` / `.set_high` | Set as 0 % / Set as 100 % | Als 0 % übernehmen / Als 100 % übernehmen |
| `propwash.rx.range` | Range: %s blocks | Reichweite: %s Blöcke |
| `propwash.rx.output` | Signal strength %s | Signalstärke %s |
| `propwash.rx.link.ok` / `.lost` | In range / Out of range – holding %s | In Reichweite / Außer Reichweite – hält %s |
| `propwash.rx.axis` / `.button` | AUX axis %s / Button %s | AUX-Achse %s / Taste %s |
| `propwash.rx.unbind` | Unbind | Bindung lösen |
| `propwash.video.camera_section` | Camera of this drone | Kamera dieser Drohne |
| `propwash.video.camera_angle` | Camera angle (uptilt) | Kamerawinkel (Uptilt) |
| `propwash.video.camera_angle.none` | Fly or hold a drone to change its camera angle. | Fliege oder halte eine Drohne, um ihren Kamerawinkel zu ändern. |
| `propwash.video.camera_angle.also` | Also in the drone's maintenance menu (sneak + right-click). | Auch im Wartungsmenü der Drohne (Schleichen + Rechtsklick). |
| `propwash.race.gate_hint.title` | Gate placed | Gate gesetzt |
| `propwash.race.gate_hint.text` | Sneak + right-click the gate with a gate item or the course tool to open the gate menu: size, rotation and redstone signal. | Schleichen + Rechtsklick auf das Gate mit einem Gate oder dem Kurs-Werkzeug öffnet das Gate-Menü: Größe, Drehung und Redstone-Signal. |
| `…gate_hint.open` / `.ok` / `.never` | Open flight school chapter / Got it / Don't show again | Kapitel in der Flugschule öffnen / Verstanden / Nicht wieder anzeigen |
| `propwash.guide.scene.gate_menu.title` | Gate menu: size, rotation, redstone | Gate-Menü: Größe, Drehung, Redstone |
| `propwash.guide.scene.rx_block.title` | RX block: redstone by radio | RX-Block: Redstone per Funke |
| `propwash.server.command.clear.done` | Removed %s drones (%s). | %s Drohnen entfernt (%s). |
| `propwash.server.command.clear.none` | No drones found in loaded areas. | Keine Drohnen in geladenen Bereichen gefunden. |
| `propwash.drone.removed` | Your drone was removed. | Deine Drohne wurde entfernt. |
| `propwash.osd.warning.esc_hot` (nur Phase B) | ESC OVERHEAT | ESC ÜBERHITZT |
| `propwash.drone.esc_derate` (nur Phase B) | ESC overheating – it throttles back to protect itself | ESC überhitzt – er regelt zum Selbstschutz ab |
| `propwash.drone.motor_hot` (Phase A: neuer Text für `motor_derate`; Phase B: eigener Schlüssel) | Motors very hot – they lose power and wear out | Motoren sehr heiß – sie verlieren Leistung und verschleißen |

Weitere Anpassungen: Beschreibung der Regel `hardware_limits` und Werkbank-Warnung `motor_thermal` (Phase A: heißer
Motor verliert Leistung und verschleißt; Phase B zusätzlich: Drosselung im ESC).

**Fensteraufbau:**

- **RX-Fenster** (eine Spalte, etwa 300 × 210 Punkte): Titel mit LED-Symbol, „Gebunden an“, Kanal-Zeile („Kanal
  anlernen“ + erkannter Kanal + Auswahlliste als Ausweg), Live-Balken mit Stärke 0–15, zwei Knöpfe für die Endpunkte,
  Reichweiten-Regler, Status („In Reichweite“/„Außer Reichweite – hält 7“), unten „Bindung lösen“ und „Fertig“.
- **Gate-Hinweis** (etwa 280 × 140): Titel, Text mit Tasten-Symbolen der echten Belegung, Haken „Nicht wieder anzeigen“,
  zwei Knöpfe.
- **Freecam-K-Menü:** Konfigurator mit nur vier Tabs, Kopfzeile „Freecam · 5″“ statt Drohnenname.

## 6. Tests – vollständige Prüfung

Grundsatz: Jede Vorgabe aus Abschnitt 1 hat mindestens eine automatische Prüfung. Bildschirmfotos prüft ein eigener
Prüfer gegen die Erwartung je Bild. Die Präfixe passen zu `docs/testing.md`.

### 6.1 JUnit (`tools/fastcheck.sh test`)

| Teil | Tests |
|---|---|
| A | `FlightPathMarkerTest`: Schweben → unsichtbar; 20 m/s geradeaus, waagerecht, Uptilt 30° → Marker um `focal · tan 30°` unter der Mitte; Seitendrift → links/rechts; rückwärts → Rand und Umriss; Fisheye gleich `OsdProjection`; Einblendung 1,0–2,0 m/s |
| B | `FreecamPresetTest` (Bänder für Schwebegas und Spitze je Preset, 3,5″ in den Zielwerten), `FreecamBoundsTest` (achsweise Grenze, Höhen), `FreecamRulesTest` (alle Gründe) |
| C | `RxMappingTest` (Endpunkte, umgedreht, gleich = Schalter, Hysterese ohne Flackern, Begrenzung), `RxLinkTest` (Dämpfung, 128er-Grenze, Deckel 64 Blöcke, Abschnitte ohne geladenen Chunk zählen ohne Dämpfung und lesen nichts), `RxSourceTest` (Codec), `RxPayloadTest` (Quantisierung hin und zurück, Masken), NBT hin und zurück |
| D | `CameraMount`-Begrenzung über den K-Menü-Weg (bestehende Tests erweitern) |
| E | Zähl-Logik des Hinweises (einmal je Welt-Schlüssel, „Nicht wieder anzeigen“ und „Kapitel öffnen“ gelten für alle Welten) |
| F | `SurfaceResponseTest`: Fall auf Slime mit 8 m/s → Rückprall ≥ 5,5 m/s, keine `CRASH`/`PROP_STRIKE`; Wolle mit 20 m/s → keine Ereignisse, Normalgeschwindigkeit ≈ 0; Stein mit 20 m/s → `CRASH` wie bisher |
| H | `ThermalCharacterizationTest` (Phase A, Tabelle und Zielbänder); nur bei Phase B: `ThermalLimitsTest`: Dauervollgas am Boden → `ESC_DERATE` kommt, `MOTOR_DERATE` nie; heißer Motor → weniger Drehmoment, aber Grenze 1,0 |
| alle | `AirframeGoldenTest`, `MotionFingerprintTest`, `SaveFormatGoldenTest` bleiben bitgleich; `tools/calibration.sh` bleibt byte-gleich bis auf die neue Thermik-Tabelle (Diff im Merge begründen) |

### 6.2 Selbsttests im Spiel (Fabric und NeoForge)

| Präfix | Schritte |
|---|---|
| `osd: Flugbahn-Marker` | Vorwärtsflug mit Test-Eingabe → Rechteck unter der Mitte; Schweben → kein Rechteck; Zuschauen → Marker vorhanden |
| `freecam:` | Regel im Standard (aus) → Start verweigert, Knopf ausgegraut mit Grund und Sprung zur Regel; Regel an → Start → kein neues Drohnen-Entity, Spieler steht still, kein Propwash-C2S-Paket während der Freecam (Zähler in `ClientNet`, nur Test); durch eine Wand fliegen → kein Crash, Kamera zeigt das Innere wie im Zuschauermodus (über `CameraClip`); an die Chunk-Grenze → Stopp und Warnung; Wind 3 → Drift im Angle-Modus > 0,5 m in 5 s, Wind 0 → < 0,1 m; Regel aus → Start verweigert; Regel aus im Flug → Ende; Schaden → Ende; K → genau vier Tabs; Preset 7″ → Masse der Sim; OSD ohne Akku-Elemente |
| `rx:` | Block setzen, mit Test-Funke binden, Gas per `SimulatedRadio` anlernen, Endpunkte setzen, 50 % → Stärke 7 oder 8, Lampe daneben leuchtet; 140 Blöcke weg → hält; zurück → folgt; Block in einem Chunk, den der Test entlädt → Zahl der geladenen Chunks bleibt gleich, Zustand bleibt; Block am Rand des geladenen Bereichs (Chunk geladen, aber ohne Block-Ticks, Block direkt an der Chunk-Grenze nach außen) mit Gas-Änderung → Zustand bleibt, Zahl der geladenen Chunks bleibt gleich, kein Nachbar-Chunk wird geladen; Pilot kommt näher → Block folgt; gleichzeitig FPV-Sitzung mit Gas → Drohne und Block folgen; Fenster bis 320 × 240 |
| `video: Kamerawinkel im K-Menü` | gehaltene Drohne → 35° → Item-Tuning 35°; im Flug → Kamera kippt live; Begrenzung auf den Halter; Suche „uptilt“ und „Kamerawinkel“ führt hin; der bestehende Schritt „drone: Kamerawinkel pro Drohne im Wartungsmenü“ bleibt grün |
| `race: Gate-Hinweis` | Hinweis-Speicher leeren, Gate setzen → Fenster; zweites Gate in derselben Welt → kein Fenster; zweite Welt → Fenster; „Nicht wieder anzeigen“ → in keiner Welt mehr ein Fenster; zurücksetzen → „Kapitel öffnen“ → Flugschule mit `gate_menu`; Freiform-Gate löst ebenfalls aus |
| `drone: Slime`, `drone: Wolle` | Fall aus 10 m auf Slime → zweiter Gipfel > 3 m, Crash-Zähler 0, Verschleiß gleich; Slime-Wand seitlich → Abprall; Wolle-Wand mit 15 m/s → kein Crash, Props unverändert; Start von Wolle → nach 2 s höher als 2 m |
| `server: /propwash clear` | zwei Drohnen, eine geflogen → Befehl → FPV endet sauber, 0 Drohnen, keine Fehler im Log |
| `drone: Motor-Hitze` | Phase A: Test-Haken setzt die Motortemperatur hoch → Warnung und neuer Text wie in Abschnitt 5. Nur Phase B: ESC-Temperatur hoch → OSD „ESC OVERHEAT“, Meldung; Motor heiß → „MOTOR OVERHEAT“ ohne Grenze |
| `ui-scale:` | RX-Fenster, Gate-Hinweis, Freecam-K-Menü, Video-Tab mit Kamerawinkel in jeder Fenstergröße und beiden Sprachen |
| `guide:` | neue Kapitel `gate_menu` und `rx_block` laufen bis zum Ende ohne Fehler (bestehender Kapitel-Durchlauf) |

### 6.3 Mehrspieler auf dediziertem Server

| Fall | Aufbau | Erwartung |
|---|---|---|
| Server-Rauchtest (jeder Lauf) | `server.ClearServerChecks`, `rx.RxServerChecks` im Quell-Set `devtest` | Clear: Gast abgelehnt, Operator entfernt drei Drohnen (eine geflogen), Tickets gelöst, Heimflug beendet. RX: falsche Payloads (NaN, zu groß, fremder Block, zu weit weg) abgelehnt, Index nach Entladen leer |
| RX mit echtem Client | `PROPWASH_JOINTEST_RX=1` an Server und Client (`dev.RxJoinCheck`, `rx.client.RxJoinProbe`): Server setzt neben dem Spieler einen an ihn gebundenen RX-Block (Gas), Client fährt mit der Test-Funke 0 → 100 %; ein zweiter gebundener Block liegt in einem nicht geladenen Chunk | `joined|rx_demand=stick:3|rx_follow=ok|rx_hold=ok|rx_foreign_denied=ok|rx_unloaded_untouched=ok` |
| Freecam mit echtem Client | `PROPWASH_JOINTEST_FREECAM=1` an beiden: Client versucht zuerst ohne Regel (verweigert), Server schaltet die Regel ein, Client startet die Freecam, fliegt 10 s, Server schaltet die Regel nach 15 s aus | `joined|freecam_denied=default_off|freecam_started=yes|freecam_server_packets=0|freecam_rule_off=ended`; der Server loggt 0 neue Drohnen |
| Flugbahn-Marker beim Zuschauen | LAN mit zwei Clients wie `tools/remote_trails_test.sh`: Gastgeber fliegt, Beobachter schaut per F7 zu | `spectate_fpm=drawn` und Bildschirmfoto |
| Kamerawinkel | Pilot-Beitrittstest (`PROPWASH_JOINTEST_PILOT=1`) erweitert: Client stellt im K-Menü 40° ein | Server-Log zeigt 40° an der Drohne (über `flight_tune`) |
| Gate-Hinweis | Beitrittstest-Client setzt ein Gate | `gate_hint=shown` trotz Server-Verzögerung |

Alle Fälle auf Fabric und NeoForge (NeoForge-Port `n + 1`), danach beide `logs/latest.log` auf Fehler prüfen.

### 6.4 Bildschirmfotos (Fabric, `-PpropwashScreenshots=true`)

- `bildschirmfotos: Flugbahn-Marker` – FPV geradlinig und mit Fisheye, Marker am Rand (Umriss).
- `bildschirmfotos: Freecam` – Freecam-OSD in der Testwelt, Warnung an der Grenze, K-Menü (englisch, deutsch,
  854 × 480 und 320 × 240).
- `bildschirmfotos: RX-Block` – Block mit LED aus, grün, rot; Fenster englisch und deutsch, groß und klein; Kapitel `rx_block`.
- `bildschirmfotos: Kamerawinkel im K-Menü` – Tab Video mit und ohne Drohne, englisch und deutsch.
- `bildschirmfotos: Gate-Hinweis` – Fenster englisch und deutsch, 320 × 240; Kapitel `gate_menu`.
- `bildschirmfotos: Motor-Hitze` – OSD-Warnung, Werkbank-Warnung (bei Phase B zusätzlich „ESC OVERHEAT“).

### 6.5 Ablauf der Prüfung

1. Jeder Strang: JUnit, eigene Selbsttest-Präfixe auf beiden Loadern, eigene Bildschirmfotos, `ui-scale:`.
2. Integration: voller Selbsttest beider Loader, Server-Rauchtest, alle Beitrittstests aus 6.3, Kalibrierung.
3. Altwelt-Umstieg: eine 0.5.2-Testwelt mit Drohnen, Gates, Kursen, OSD-Layout und gesetzten Welt-Regeln in 0.6.0
   öffnen → keine Fehler, neue Regel aus, Marker aus, Kamerawinkel der Drohnen unverändert.
4. Mindestens zwei unabhängige Prüfer je Teil gehen die Vorgaben aus Abschnitt 1 Punkt für Punkt gegen Testausgaben und
   Bilder durch; Fix-Runden, bis nichts Mittleres mehr offen ist.

## 7. Doku und Website

**Im Repository (je Strang sein Teil):**

- `docs/FOUNDATION.md`: je Strang ein eigener Abschnitt mit den Verträgen (Freecam-Haken, RX-Payloads und Rechte,
  `SurfaceResponse`/`surfaceImpactScale`, Thermik-Ereignisse, OSD-Element).
- `docs/ARCHITECTURE.md`: Pakete `freecam` und `rx`, Ergänzungen bei `osd`, `sim`, `server`.
- `docs/testing.md`: Abschnitte Freecam, RX-Block, Slime und Wolle, Motor-Hitze mit Präfixen und Beitrittstests.
- `docs/design/`: Feature-Dokumente `freecam.md`, `rx-block.md`, `surface-blocks.md`, `realism/motor-heat.md` (mit den
  Zahlen aus Phase A), Nachträge in `gate-edit-mode.md` und `world-rules.md`.
- `CHANGELOG.md` (Integrator aus den Zeilen der Stränge), `docs/RELEASE-NOTES.md` (Protokoll 17, Rückweg auf 0.5.2
  verliert RX-Blöcke), `docs/api/changes.md` und Addon-Handbuch (Bedeutung der Motor-Temperaturfelder), README-Featureliste,
  `docs/modrinth/`.

**Website (Textbausteine liefert dieser Strang, einbauen der Website-Strang):**

- Spielerhandbuch `docs/guide`: neue Abschnitte Freecam, RX-Block, Flugbahn-Marker im OSD-Teil; Gate-Menü-Hinweis bei
  `racing-edit`; Antwort „Wie ändere ich den Kamerawinkel?“ (beide Wege); Befehl `/propwash clear` bei den Befehlen;
  Motor-Hitze bei Akku/Hardware.
- `docs/gamerules/gamerules.json`: neue Regel `allow_freecam`, neue Beschreibung `hardware_limits`.
- Startseite: Feature-Kacheln Freecam und RX-Block; Roadmap: Punkte auf erledigt; Changelog-Seite.
- FAQ: Kamerawinkel, Freecam auf Servern (Regel standardmäßig aus, Grenzen), RX-Block ohne Funke (nur im Flug per Tastatur).

## 8. Risiken

| Risiko | Gegenmaßnahme |
|---|---|
| Freecam als Späh-Werkzeug auf Survival-Servern | Welt-Regel standardmäßig aus, Admins schalten sie bewusst ein; Reichweite nur geladene Chunks; ehrlicher Regeltext („sieht durch Wände“) (Entscheidung E7) |
| Freecam-Haken verändern FPV oder Zuschauen | Haken nur mit Freecam-Abfrage zuerst; voller FPV-, Zuschauer- und Kamera-Selbsttest; LAN-Test |
| Freecam erreicht nicht geladene Bereiche oder stottert an der Grenze | `FreecamBounds` achsweise, JUnit, Selbsttest an der Grenze |
| Zonen-Physik steckt nur in `PilotSession` | Umzug in `zone.client.ZonePhysics` ohne Verhaltensänderung, Selbsttest `zonen:` grün |
| RX-Paketflut oder Tick-Kosten | Bedarf vom Server, Ratenbegrenzung, Auswertung alle 2 Ticks, Link-Cache 10 Ticks, Blockzustand nur bei Änderung |
| RX lädt entfernte Chunks synchron im Tick | nur Chunks mit Block-Ticks lesen und setzen (`shouldTickBlocksAt`, E36), Test zählt geladene Chunks vor und nach, auch am Rand des geladenen Bereichs |
| RX-Griefing auf Servern | nur Besitzer oder Operator ändern die Bindung |
| RX: rohe AUX-Achsen unterscheiden sich je Gerät | im Fenster sichtbar („AUX-Achse 6“), neu anlernen ist ein Klick; dokumentiert |
| RX mit Tastatur außerhalb des Flugs | Tastatur bleibt zum Laufen; RX folgt der Tastatur nur in einer Flug- oder Freecam-Sitzung; dokumentiert |
| Slime/Wolle ändert Goldens | Standard `surfaceImpactScale` = 1,0, Normalfall unverändert, Goldens bitgleich |
| Motor-Hitze: Spieler bemerken anderes Verhalten bei langem Schweben | Umbau nur bei Befund (E31); Änderung nur oberhalb der Schwellen; Phase-A-Zahlen in Doku und Changelog; Regel `hardware_limits` schaltet ab |
| JMP-Werte passen nach der Umdeutung nicht mehr | JMP in Phase A; JMP-Prüfstände gegen 0.6.0 |
| `/propwash clear` löscht wertvolle Drohnen | nur Stufe 2, klare Rückmeldung mit Anzahl, Log-Zeile, Selektor-Variante für gezielte Auswahl |
| Gate-Hinweis nervt beim Bauen | einmal je Welt, „Nicht wieder anzeigen“ sofort und für alle Welten wirksam |
| Konflikte in geteilten Dateien (Sprachdateien, FOUNDATION, GuideScenes, WorldRuleSpecs, DroneSim, Constants) | Besitz-Tabelle 10.2; Integrator löst mechanisch; Merge-Reihenfolge 10.1 |
| Überschneidung mit dem Community-Add-on „Noclip“ | Nachricht des Projektinhabers an den Autor vor dem Release (Freecam ersetzt nichts an der Drohne selbst) |
| Freecam und Geist-Flug brauchen beide die Kamera in Blöcken | eine Abfrage `CameraClip` im Besitz von `addon-collision`, Freecam meldet sich nur an (E37) |

## 9. Entscheidungen

### 9.1 Entscheidungen

| Nr. | Was | Warum | Verworfen |
|---|---|---|---|
| E1 | Vorgaben sinngemäß statt wörtlich | `docs/CONVENTIONS.md` und der öffentliche Export verbieten wörtliche Zitate des Besitzers im Repository | wörtliche Zitate (Export bricht ab) |
| E2 | Marker aus den **gezeigten** Posen, 50 ms Differenz, PT1 40 ms | sonst passt er bei hoher Latenz nicht zum Bild | aktuelle Sim-Geschwindigkeit (läuft dem Bild voraus) |
| E3 | Marker unter 1,0 m/s aus, bis 2,0 m/s eingeblendet | Richtung im Schweben ist Rauschen | immer zeigen (springt) |
| E4 | Marker standardmäßig aus (außer FULL) | bestehende OSD-Layouts bleiben unverändert | für alle an |
| E5 | Marker außerhalb des Bilds als Umriss am Rand | wie Flugzeug-HUDs, keine Verwechslung mit dem Home-Pfeil | ausblenden |
| E6 | Freecam-Taste F6, Ende auch mit Y | F6 ist frei; Y beendet schon das FPV | ohne Standardtaste (niemand findet sie), F4 (Freecam-Mods), F8 (belegt) |
| E7 | Regel `allow_freecam` Standard **aus**; ausgegrauter Knopf mit Grund und, für wer darf, Sprung zur Regel | eine Kamera durch Wände darf auf bestehenden Survival-Servern nicht ohne Zutun des Admins erscheinen; im Einzelspieler ist es ein Klick; die frühere Begründung (Server-Mod schaltet je Instanz ab) entfällt mit der Zurückstellung | Standard an; Spielmodus-Stufen (nicht beauftragt) |
| E8 | ohne synchronisierte Regel keine Freecam | sicherer Standard auf fremden Servern | erlauben, wenn unbekannt |
| E9 | unscharf = geparkt in der Luft | ohne Kollision fiele sie durch die Welt | echte Schwerkraft auch unscharf |
| E10 | 3,5″ als Kreativ-Werte auf Kern-Teilen | ohne JMP spielbar, gleiches Ergebnis für alle | JMP-Teile nutzen, wenn vorhanden (uneinheitlich) |
| E11 | Rates und Kamerawinkel je Preset, Sichtfeld global | Whoop und 7″ fliegen sich verschieden | ein Tuning für alle |
| E12 | K-Menü der Freecam: Freecam, Rates, Empfänger, Modi | ohne Empfänger und Modi ist die Funke nicht einzurichten; Rest hat keinen Bezug | nur Freecam und Rates; eigener neuer Screen statt Filter (doppelte Pflege) |
| E13 | Freecam ohne Motorton, ohne Tricks, ohne Rennen | rein clientseitig, nichts darf gewertet werden; Kamera-Werkzeug | Ton und Tricks wie im FPV |
| E14 | Freecam endet bei Schaden am Spieler | der Server kennt sie nicht und schützt den Körper nicht | weiterfliegen (Tod im Hintergrund) |
| E15 | Grenze achsweise an geladenen Chunks, Höhe −16/+64 | gleitet an der Grenze entlang, kein leerer Himmel | harter Stopp aller Achsen; Grenze an Sichtweite als Kugel |
| E16 | RX-Quelle: Knüppel logisch, AUX als rohe Achse oder Taste | Knüppel bleiben bei Gerätewechsel gleich; AUX hat sonst keine stabile Bezeichnung | nur Schalterfunktionen (zu grob), nur rohe Kanäle |
| E17 | Standardreichweite 64 | halbe Höchstreichweite, reicht für Kurse um den Piloten | 32 (zu knapp beim Fliegen), 128 (Servernutzung) |
| E18 | Dämpfung 0,5 Blöcke je Vollblock, höchstens 64 gezählt | Vorgabe „Blöcke dämpfen kaum“, billig zu rechnen | Videostrecken-Modell (zu stark), gar keine Dämpfung |
| E19 | Ausgabe nur bei Änderung, Auswertung alle 2 Ticks | weniger Block-Updates, ein Redstone-Tick Auflösung | jeden Tick setzen |
| E20 | Signal an alle Seiten, schwach | einfach und vorhersehbar wie ein Tageslichtsensor | ausgerichteter Ausgang (mehr Bedienung) |
| E21 | Rezept Blitzableiter, Kupfer, Platine, Redstone → 1 | Propwash-Stil wie der Repeater, im Survival erreichbar | teure Teile, 2 Stück |
| E22 | Bindung ändern nur Besitzer oder Operator | Schutz vor Griefing | jeder darf neu binden |
| E23 | Bruch droppt einen leeren RX-Block | klare Regel, kein Daten-Item | Einstellungen im Item behalten |
| E24 | Kanalwerte nur auf Server-Bedarf senden | kein Verkehr ohne RX in der Nähe | ständig senden |
| E25 | Kamerawinkel im Tab Video über den bestehenden Tuning-Weg | kein neues Paket, Server begrenzt schon; Sichtfeld steht dort | eigener Tab; Knopf, der das Wartungsmenü öffnet (geht nicht für gehaltene Items) |
| E26 | Gate-Hinweis als kleines Fenster, einmal je Welt und Spieler | Vorgabe verlangt „beim ersten Setzen“, „nicht wieder anzeigen“ und „Kapitel öffnen“, beides braucht Klicks | Toast ohne Klicks; bis zu dreimal |
| E27 | Erkennung des Setzens rein clientseitig | kein Eingriff in `GateItem` (Strang gestapelte Gates), kein Paket | Server-Meldung nach dem Setzen |
| E28 | neues Kapitel `gate_menu` statt Ausbau von `gate_free` | das Gate-Menü samt Redstone fehlt als Kapitel; `gate_free` bleibt stabil | Schritt in `gate_free` anhängen |
| E29 | Slime und Wolle (mit Heuballen, wie im angenommenen Vorschlag) als Block-Tags, Wirkung an allen Seiten; Honig nur auf den Tag umgezogen, Verhalten unverändert | per Datenpaket erweiterbar; Wand-Treffer sind der häufige Fall; Honig war nicht beauftragt | feste Blockliste nur unter der Drohne (wie heute); neue Honig-Wirkung |
| E30 | Wolle mit fast keinem Rückprall, Start normal | Vorgabe „fängt ab, Start möglich“ | Start von Wolle sperren (ursprünglicher Vorschlag) |
| E31 | Hitze: zuerst prüfen (Phase A); Umbau auf Drosselung im ESC (Phase B) nur bei Befund nach §3.H; kein Durchbrennen | Vorgabe „genau prüfen“, größere Kopter kämpfen tatsächlich mit Hitze; ein Umbau ohne Befund änderte Verhalten ohne Grund; Totalausfälle ohne Vorwarnung frustrieren | Umbau unabhängig vom Ergebnis; Durchbrennen bei Übertemperatur |
| E32 | keine neuen API-Felder für Hitze | API-Paket gehört dem API-Strang; Schwellen je Stack-Klasse reichen | `esc_temp_limit_c` im Stack-JSON |
| E33 | `/propwash clear` ohne Drops, Stufe 2, Selektor optional | Vorgabe „einfach alle entfernen“; Selektor für gezielte Fälle | Rückgabe als Item an den Besitzer; Bestätigungsschritt |
| E34 | Protokoll einmal auf 17 für 0.6.0 | die Nummer zählt zwischen Veröffentlichungen | je Strang erhöhen |
| E35 | keine API für RX, Freecam und Marker in 0.6.0 | kleine Oberfläche, später nachrüstbar | sofortige API |
| E36 | RX-Block liest und setzt nur in Chunks mit Block-Ticks (`shouldTickBlocksAt`, nicht `isLoaded`) | synchrones Laden im Server-Tick kostet Lag; der Zustand steckt im Blockzustand und überlebt das Entladen | Chunks für die Auswertung laden; Ticket je RX-Block |
| E37 | Freecam nutzt die Abfrage `CameraClip` von `addon-collision` statt eigener Haken in `FpvEyeGuard` und Kamera-Mixin | eine Datei, ein Besitzer; Geist-Flug und Freecam brauchen dasselbe | zweiter Haken in derselben Methode |

### 9.2 Nicht geändert: Runden mit Flughilfe

Runden mit Flughilfe werden weiter nur gespeichert, wenn die Welt-Regel `propwash:save_assisted_laps` an ist (Standard
aus, `docs/design/assisted-laps.md`). Ob sie künftig standardmäßig in der getrennten Wertung „Mit Flughilfe“ landen,
ist offen; 0.6.0 ändert daran nichts. Runden in der Freecam zählen in keinem Fall.

## 10. Arbeitspakete

Basis jedes Strangs: der Integrationszweig von 0.6.0 (Übersicht `README.md` K1). Je Strang ein eigener Worktree, Repo
`propwash`. Minecraft-Läufe nur über das Slot-Skript; Integration unter der gemeinsamen Sperre. Zweig-Namen nach
Übersicht 4.1 (`feat/0.6.0-<strang>`).

### 10.1 Stränge

| Strang | Branch | Besitzt (neu bzw. allein) | Abhängigkeiten | MC-Test (min) | Größe |
|---|---|---|---|---|---|
| `fpm` | `feat/0.6.0-fpm` | `osd/FlightPathMarker`, Element in `osd/OsdElement`, Felder in `osd/OsdData`, `OsdRenderer#flightPathMarker`, Preset-Eintrag, Abfrage `VideoState#sampleDisplayedAt`, Selbsttests `osd: Flugbahn-Marker…` | keine; vor `freecam` mergen; mit API-Strang abstimmen, falls dieser `OsdElement` umbaut | 10 | M |
| `camangle` | `feat/0.6.0-camangle` | Abschnitt in `video/client/VideoConfigTab`, Suchbegriffe in `ui/client/search`, Selbsttest `video: Kamerawinkel im K-Menü` | keine | 8 | S |
| `clear` | `feat/0.6.0-clear` | `server/DroneClear`, Unterbefehl in `server/ServerCommands`, `server.ClearServerChecks` (devtest) | keine | 6 | S |
| `surface` | `feat/0.6.0-surface` | `sim/SurfaceResponse`, Feld in `sim/MoveReport`, `DroneSim#classify`/Kontaktstellen, `drone/SurfaceMaterials`, `PilotSession#fillSurface`, Aufrufe in `rescue/ServerReturnFlight` und `rescue/VoxelFlight`, Tags `drone_bouncy/cushion/sticky` | keine; vor `heat` mergen (beide in `DroneSim`, andere Methoden) | 12 | M |
| `heat` | `feat/0.6.0-heat` | Phase A: `sim/ThermalCharacterizationTest`, `docs/design/realism/motor-heat.md`, Kühlkonstanten und Texte; Phase B (nur bei Befund nach §3.H): `sim/Thermal`, Thermik-Methoden in `sim/DroneSim`, `sim/ParamDerivation#thermal`, `SimEventType`-Einträge, OSD-Warnung in `osd/OsdWarnings`, Meldungen in `PilotSession` (nur Ereignis-Fälle), Werkbank-Warnungstext | nach `surface`; Doku-Text an API-Strang | 8 | S (A), M (B) |
| `gatehint` | `feat/0.6.0-gatehint` | `race/client/GatePlaceHint`, `race/client/GateHintScreen`, `guide/scenes/GateMenuScene`, Zeile in `guide/GuideScenes`, ggf. Knopf „Hinweise zurücksetzen“ | keine; nicht in `GateItem` | 12 | M |
| `rx` | `feat/0.6.0-rx` | Paket `rx` und `rx.client` komplett, Assets und Daten `rx_block`, `guide/scenes/RxBlockScene` + Zeile in `GuideScenes`, Eintrag im Kreativ-Tab, `dev.RxJoinCheck`, `rx.RxServerChecks`, ohne Protokollnummer (steht ab dem Basis-Commit) | keine; lang, deshalb früh beginnen | 40 | L |
| `freecam` | `feat/0.6.0-freecam` | Paket `freecam` und `freecam.client` komplett, Haken aus 3.B, Regelzeile, Taste, Beitrittstest Freecam (der Umzug der Zonen-Auflösung nach `zone/client/ZonePhysics` gehört `pilot-prep`) | nach `fpm` (beide in `LiveOsdSource`), nach `camangle` (beide im K-Menü), nach `addon-collision` (`CameraClip`); vor dem Merge rebased auf `addon-switches` (`ModesTab`) und `addon-world-rules` (`WorldRulesTab`), in beiden nur die eine Zeile bzw. Methode aus §3.B | 35 | L |

Summe Minecraft-Testzeit der Stränge etwa 130 min, dazu die Integration (voller Selbsttest beider Loader, Rauchtests,
Beitrittstests, Bildschirmfotos).

### 10.2 Geteilte Dateien

| Datei | Wer schreibt | Regel |
|---|---|---|
| `assets/propwash/lang/en_us.json`, `de_de.json` | alle | nur eigene Schlüssel; Konflikte löst der Integrator mechanisch |
| `docs/FOUNDATION.md`, `ARCHITECTURE.md`, `testing.md` | alle | je Strang ein eigener Abschnitt am Ende bzw. bei seinem Paket |
| `guide/GuideScenes` | `gatehint`, `rx` | je eine Zeile |
| `server/rules/WorldRuleSpecs` | `freecam` | eine Zeile |
| `input/client/screen/ModesTab` | `addon-switches` (API-Dokument), `freecam` | `freecam` nur die Zeile `freecam()`; rebased nach `addon-switches` |
| `input/client/screen/ReceiverTab`, `ui/client/tab/RatesTab` | `freecam` | je eine Zeile `freecam()` |
| `server/client/WorldRulesTab` | `addon-world-rules` (API-Dokument), `freecam` | `freecam` nur `focusRule(String)` und ihr Aufruf; rebased nach `addon-world-rules` |
| `server/ServerCommands` | `clear` | ein Unterbefehl |
| `sim/DroneSim`, `sim/MoveReport` | `surface`, `heat`, danach `addon-collision` (API-Dokument) | getrennte Methoden; `heat` nach `surface`; `addon-collision` erst nach `heat` Phase A und, falls gebaut, Phase B |
| `drone/client/PilotSession` | `surface` (`fillSurface`), `heat` (Ereignis-Fälle), danach `addon-collision` | keine anderen Stellen; `addon-collision` erst nach `heat` (Phase A und, falls gebaut, B); `freecam` fasst sie nicht an |
| `drone/client/FpvEyeGuard`, `mixin/drone/client/CameraMixin` | nur `addon-collision` (API-Dokument) | `freecam` meldet sich nur bei `CameraClip` an |
| `osd/OsdElement`, `osd/client/OsdRenderer` | `fpm`, evtl. API-Strang | `fpm` zuerst; API-Strang zieht die Konstante bei einem Umbau mit |
| `osd/client/LiveOsdSource` | `fpm`, `freecam` | `freecam` nach `fpm` |
| `ui/client/config/*`, `ui/client/UiClient` | `freecam` | Filter-Haken; `camangle` ändert dort nichts |
| `Constants.PROTOCOL_VERSION` | Basis-Commit (Integration) | genau einmal auf 17, kein Strang |
| `CHANGELOG.md`, Tages-Changelog | Integrator | Stränge liefern ihre Zeilen in der PR-Beschreibung |

### 10.3 Berührungen mit anderen 0.6.0-Dokumenten

- **API:** OSD-Elemente als Register (Marker zieht mit), Kollisions-Haken (soll auf `sim.SurfaceResponse` aufsetzen und
  nach den Block-Tags greifen, damit Add-ons die Antwort ändern können), Bedeutung der Motor-Temperaturfelder
  (Handbuchtext aus `heat`), API-Version 1.7.0.
- **Server-Mod:** zurückgestellt, nicht Teil von 0.6.0.
- **Kollision (API-Dokument):** `addon-collision` legt `CameraClip` an, bevor `freecam` mergt.
- **Gestapelte Gates:** besitzt `GateItem`; der Gate-Hinweis bleibt draußen.
- **Website:** baut die Textbausteine aus Abschnitt 7 ein, `gamerules.json` aus dem Export der Regeln.
