# Add-on-API 1.7 und reservierter Namensraum `propwash` (Propwash 0.6.0)

Status: Design für 0.6.0, Stand 17.10.2026, ergänzt um die Lücken aus den Entwürfen von Mob Defense und
Kamera-Drohnen (§3.19). Ausgangspunkt ist Tag `v0.5.2` (API 1.6.0, Netzprotokoll 16). Ergänzt [`../addon-api-1.6.md`](../addon-api-1.6.md), [`../parts-api.md`](../parts-api.md),
[`../client-only-addons.md`](../client-only-addons.md), [`../autonomous-rescue.md`](../autonomous-rescue.md) und die
Add-on-Entwürfe [`../addon-mob-defense.md`](../addon-mob-defense.md) und [`../addon-cinematic.md`](../addon-cinematic.md).
Im Code stehen keine Kommentare; die Erklärungen stehen hier und später im Handbuch `docs/api/addon-guide.md`.

Inhalt: (A) API 1.7 mit eigenen Schalterfunktionen, OSD-Elementen und Haken für Kollision und Physik,
(B) die Lücken, die Mob Defense und das Kamera-Drohnen-Add-on noch haben, (C) der reservierte Namensraum `propwash`
samt Prüfung beim Start, (D) `ADDONS.md`, README, NOTICE, Website und Issue-Vorlage, (E) die drei Tests, die nach
einem Klon des öffentlichen Repos fehlschlagen.

---

## 1. Ziel und Vorgaben

**Ziel:** Add-ons sollen alles, was heute nur per Mixin in Propwash-Interna geht, über die stabile API
`de.lardtom.propwash.api` können. Der Namensraum `propwash` gehört allein Propwash; wer ihn trotzdem benutzt, wird
klar benannt, ohne dass das Spiel abstürzt. Die beiden offiziellen Add-ons nach 0.6.0 (Mob Defense, Kamera-Drohnen)
finden alle Bausteine fertig vor, damit sie ohne Mixins in Propwash auskommen.

**Vorgaben** (sinngemäß, Stand 17.10.2026; Quellen: Roadmap-Notizen zu 0.6.0 und den Add-on-Richtlinien, Entwurf
der Add-on-Richtlinien, Plan nach 0.5.2, Notiz zu Mob Defense vom 12.10., Discord-Auswahl vom 17.10.):

1. Anlass ist der Lizenzcheck des Community-Add-ons *Noclip* (MIT, Mixins in Interna, lizenzkonform). Die Lizenz
   bleibt unverändert, es gibt kein „nur über die API“. Stattdessen eine Richtlinie in `ADDONS.md`, auf die README
   und NOTICE verweisen, plus ein Abschnitt in der Website-Doku.
2. Der Namensraum `propwash` wird **generell** reserviert (NOTICE und `ADDONS.md`). Grund: Die PW3-Sharecodes ab 0.6.0
   erkennen Teile am Namensraum. Das Namensschema „X for Propwash“ für Community-Add-ons ist in Ordnung,
   „Propwash: …“ bleibt den offiziellen Add-ons.
3. Stabil ist nur `de.lardtom.propwash.api`. Die Issue-Vorlage fragt, ob der Fehler auch ohne Add-ons auftritt.
4. Technik für 0.6.0: Die API lehnt fremde `propwash:`-Registrierungen **hart** ab. Beim Start und beim Neuladen der
   Ressourcen werden fremde Registry-Ids, Übersetzungsschlüssel, Netzwerk-Pakete und per Mixin angehängte Enum-Werte
   unter `propwash` gesucht. Folge: Fehler im Log und einmal pro Spielstart ein Hinweis im Spiel, **kein Absturz**.
   Propwash-Assets und -Daten per Ressourcen- oder Datenpaket zu überschreiben bleibt erlaubt.
5. Die API bekommt eigene Schalterfunktionen, eigene OSD-Elemente und Haken für Kollision und Physik der Drohne.
6. Die **ganze** Anpassung kommt in 0.6.0 (Texte, NOTICE, README, Website, Issue-Vorlage, Technik), nichts davon in 0.5.2.
7. Mob Defense: Dispenser-Halterung immer nach vorn, 4 statt 9 Slots, eine Taste bzw. ein Schalter an der Funke löst
   aus, alles, was ein Dispenser kann; Windkugeln geben echten Rückstoß in der Physik; Wasser- und Lavaeimer
   standardmäßig gesperrt (eigene Welt-Regel kann sie erlauben); Speer-Halterung unten nur für Speere; genau eine
   Feuerwerksrakete als Boost wie bei der Elytra; feindliche Mobs reagieren auf Drohnen; Welt-Regeln für Payloads
   gegen Spieler, TNT-Abwurf, Mob-Griefing, Mob-Interaktion, Munition und Abklingzeiten; vielleicht Sculk und Warden.
8. Kamera-Drohnen-Add-on (ohne „DJI“ im Namen): programmierbare Drohnen mit Wegpunkten, Position halten, Gimbal,
   Follow me, Point of Interest und Zwei-Piloten-Gimbal.
9. Die drei Tests, die nach einem Klon des öffentlichen Repos fehlschlagen, werden für die nächste Version behoben
   (GoldenFile-Pfad hängt am Arbeitsverzeichnis, der Login-Shell-Test an der Umgebung).
10. 0.6.0 wird vor dem Release gründlich geprüft: viele unabhängige Prüfer, Mehrspieler auf dediziertem Server,
    Bildschirmfotos.
11. Nicht für 0.6.0: ein API-Haken für VR bzw. Vivecraft (derzeit keine Relevanz).

---

## 2. Umfang

### 2.1 Drin

| Nr. | Baustein | Kurz |
|---|---|---|
| A1 | Add-on-Schalterfunktionen und -Achsen | `PropwashApi.switches()`: benannte Taster/Schalter und analoge Kanäle, im Tab „Modi“ auf einer Seite „Add-ons“ belegbar, Tastatur über eigenes `KeyMapping`, optional an den Server synchronisiert |
| A2 | Add-on-OSD-Elemente und -Warnungen | `PropwashClientApi.registerOsdElement` (Textelement im OSD-Raster, im OSD-Editor platzierbar), `registerOsdWarning` (Text im eingebauten Warnfeld) |
| A3 | Kollisions-Haken | `CollisionRule`: Block-, Flüssigkeits-, Entity- und Ranken-Kollision je Drohne abschalten (server-geprüft), Antwort auf Block-Kontakte (Abprall, Haftung, Crash-Schaden) |
| B1 | Werfen wie ein Dispenser | `AccessoryContext#dispense`: Vanilla-Verhalten aus der Halterung in beliebiger Richtung, Rückstoß aus dem Geschossimpuls, Eimer standardmäßig gesperrt |
| B2 | Inhalt sichtbar | `AccessoryBehavior#display`: eingesetzte Items (Speer, Rakete) an der Drohne rendern |
| B3 | Explosions- und Windstöße | Event `DRONE_KNOCKBACK`; ein Add-on kann damit Stöße durch Explosionen und Windkugeln ins Flugmodell geflogener Drohnen geben. Ohne Add-on ändert sich nichts (kein Kernverhalten, E16) |
| B4 | Alle Drohnen eines Levels | `DroneAccess#loaded(level)` (z. B. Sculk, Warden, Mob-Ziele) |
| B5 | Add-on-Welt-Regeln im Tab „Welt-Regeln“ | `PropwashApi.worldRules()`: Schalter- und Zahlenregeln im eigenen Namensraum, eigener Abschnitt im Tab, Operator-Bearbeitung und Abgleich wie bei Propwash |
| B6 | Daten an der Drohne | `PropwashApi.droneData()`: typisierte Add-on-Daten (Codec) an Entity und Drohnen-Item, optional an Zuschauer synchronisiert |
| B7 | Autonomer Flug mit Add-on-Flughilfe | `DroneAccess#autonomy()`: Server fliegt die Drohne ohne Pilot mit einer `FlightAssist` des Add-ons (Halten, Follow me, POI, Wegpunkte), geteiltes Budget mit dem Heimflug |
| B8 | Zweiter Operator | `DroneAccess#operators()`: ein zweiter Spieler steuert mit seiner Funke Achsen und Aktionen (Gimbal), alle Zuschauer sehen dieselbe Lage |
| B9 | Gimbal in der Aufnahme | Action-Cam-Daten auch auf der Halterung vorn, Gimbal-Lage optional für Action-Cam-Aufnahme und Flashback-Kamerapfad |
| B10 | Pilotenwechsel und Wartung | Event `PILOT_CHANGED`; `PropwashClientApi.registerServiceExtension` (Knopf im Kopf der Wartung, Kind-Fenster für die Drohne) |
| C1 | Reservierter Namensraum in der API | jede API-Registrierung im Namensraum `propwash` wird abgelehnt; neue Punkte werfen, bisher tolerante Punkte verweigern still mit Fund |
| C2 | Namensraum-Prüfung | beim Start und beim Neuladen: Registry-Ids, Übersetzungsschlüssel, Tastenbelegungen, Netzwerk-Pakete, Enum-Erweiterungen, neue Daten- und Asset-Dateien, `/propwash`-Unterbefehle; Log-Fehler, Hinweis im Chat einmal pro Start, Detail-Fenster |
| C3 | Absturzbericht | Abschnitt „Propwash add-ons“ im Crash-Report mit Add-ons und Mods, die per Mixin in Propwash-Interna eingreifen |
| D | Texte | `ADDONS.md` (Text des Entwurfs), Absatz in README und NOTICE, Website-Abschnitt EN/DE, Issue-Vorlage |
| E | Tests nach dem Klon | Testpfade unabhängig vom Arbeitsverzeichnis, Login-Shell-Parser unabhängig vom Betriebssystem, Probe aus dem Export |

### 2.2 Bewusst nicht drin

- **Lizenzänderung oder Sperre per Lizenz**: Vorgabe 1; die Lizenztexte bleiben unverändert.
- **Absturz oder Abschalten fremder Mods** bei Verstößen: Vorgabe 4 (Modpacks sollen spielbar bleiben).
- **Kompatibilitätsschicht für Mixin-Add-ons**: Interna dürfen sich ändern (Richtlinie Punkt 2). Mixins, die auf
  geänderte Stellen zielen, greifen nicht mehr; das ist das Risiko des jeweiligen Add-ons.
- **Freie Grafik im OSD-Raster**: OSD-Elemente sind Text im OSD-Zeichensatz. Freie Grafik bleibt `HudElement` (1.6).
- **Eigene Funken-Items und -Modelle** für Add-ons (offener Punkt aus 1.6): nicht gefordert, groß, kein Bedarf für die
  beiden Add-ons.
- **Kamera-Haken für VR/Vivecraft**: Vorgabe 11.
- **Schwerkraft, Wind oder Luftdichte je Drohne per API**: Zonen und Welt-Regeln decken das ab; Gegenkräfte gehen
  über `ForceProvider` (1.6).
- **Add-on-Welt-Regeln in Zonen überschreibbar**: Zonen bleiben bei Propwash-Regeln.
- **Add-on-Daten in Sharecodes**: Sharecodes beschreiben den Bau, nicht Missionen oder Inventare (PW3 ist ein eigener
  Strang).
- **Prüfung von NBT-Schlüsseln und Konfigurationsdateien** fremder Mods: technisch nicht sinnvoll prüfbar; steht nur
  als Regel in `ADDONS.md`.
- **Reservierung von Präfixen wie `propwash_…`**: technisch reserviert ist nur der exakte Namensraum `propwash`.
- **Mehr als ein Operator je Drohne**, Operator-Vorhersage auf dem Client.
- **Use-Taste (Rechtsklick) als Auslöser für Zubehör**: Der Rechtsklick beendet im Flug weiter das FPV; Zubehör löst
  über Zubehör-Aktionen aus (E14, E35).
- **Welt-Regel für Dauer oder Anzahl autonomer Flüge**: Die Dauer begrenzt der Akku, die Anzahl das bestehende
  Budget (E21).
- **Autonomer Flug außerhalb der Funkreichweite**, **Lautstärke einer Drohne am Server**, **Flugmodell-Prüfstand als
  API** und **Rahmen-Rolle `CAMERA`** (E39).

---

### 2.3 Bestandsaufnahme: was fehlt heute

#### 2.3.1 Was das Noclip-Add-on per Mixin macht (Quellcode v0.1.1, öffentlich)

| Mixin-Ziel (intern) | Zweck | Ersatz in 1.7 |
|---|---|---|
| `input.SwitchFunction` (Enum-Wert angehängt), `input.client.screen.ModesTab$Row` (Enum-Wert), `input.client.FunctionKeys` | eigener Schalter „Noclip“ samt Taste | A1: `switches().register(...)`, `PropwashClientApi.bindSwitchKey(...)` |
| `osd.OsdElement` (Enum-Wert), `osd.client.OsdRenderer#drawElement` | Warnung „NOCLIP ACTIVE“ im OSD | A2: `registerOsdWarning(...)` oder ein OSD-Element |
| `drone.client.PilotSession` (`noPhysics`, `fluidAt`) | Flugmodell ohne Block- und Wasserkontakt | A3: `CollisionRule` mit `CollisionFlags.GHOST` |
| `drone.DronePiloting#onState` (`noBlockCollision`, `PathCheck.clear`) | Server-Prüfung umgehen | A3: der Server wertet dieselbe Regel selbst aus (Autorität bleibt beim Server) |
| `drone.client.FpvEyeGuard`, Vanilla `Camera` | Kamera darf in Blöcke, Darstellung wie Zuschauer | A3: Propwash schaltet beides selbst, solange `blocks = false` (intern über `drone/client/CameraClip`, die auch die Freecam nutzt) |

Nebenbefunde, die die API-Lösung mitbehebt: Der Server-Mixin ruft Client-Klassen auf (auf einem dedizierten Server
nicht lauffähig); die Übersetzungsschlüssel liegen im Namensraum `propwash` (`key.propwash.noclip`,
`propwash.input.switch.pwnoclip_noclip`, `propwash.osd.element.pwnoclip_noclip`); das Enum `SwitchFunction` speichert
seine Zustände als `int`-Bitmaske (`1 << ordinal`, heute 29 Werte) – jeder angehängte Wert rückt näher an die Grenze
von 32.

#### 2.3.2 Mob Defense gegen API 1.6

| Bedarf | 1.6 hat | Lücke | 1.7 |
|---|---|---|---|
| Dispenser vorn, 4 Slots, Auslösen | `AccessorySlot.FRONT`, `slots() = 4`, Aktionen 1–4 | „alles, was ein Dispenser kann“ in beliebiger Richtung (Vanilla-Verhalten hängt an Blöcken und sechs Richtungen) | B1 |
| Auslösen per Taste | Zubehör-Aktion 1 (Funke oder Taste) | der Tab „Modi“ zeigt nur „Zubehör-Aktion 1“; der Vanilla-Rechtsklick beendet im Flug weiterhin das FPV (E14) | A1 (Namen der Aktionen, Hinweis beim ersten Flug) |
| Windkugel-Rückstoß | `DronePhysics#impulse` | Rückstoß beim Abschuss geht; der **Windstoß** einer Windkugel oder Explosion in der Nähe kommt beim Flugmodell des Piloten nicht an | B1 (Abschuss), B3 (Stoß) |
| Eimer gesperrt | – | Standard im Werfen | B1 (`allowFluids = false`) |
| Werfen auf Servern | – | Bau-Rechte des Piloten, Werfen ohne Verbrauch, eigene Geschosse treffen die eigene Drohne, TNT und Items ohne Drohnentempo | B1 |
| Prüfung auf dem dedizierten Server | – | Zubehör-Aktion ohne Client auslösen | B1 (`triggerAction`) |
| Speer-Halterung unten | Halterung unten, `DRONE_HIT_ENTITY` | eingesetzter Speer ist unsichtbar | B2 |
| eine Rakete als Boost | `DronePhysics#push` | Rakete an der Drohne sichtbar bzw. leer | B2 |
| Mobs reagieren | `nearestDrone`, `aim`, `DRONE_DAMAGED` | alle geladenen Drohnen eines Levels (Sculk, Warden) ohne teure Entity-Suche | B4 |
| Enderman und Creaking | `DroneView` | Blickrichtung der FPV-Kamera am Server | B4 (`cameraPose`) |
| Welt-Regeln | eigener Konfigurator-Tab | Regeln gehören laut Grundsatz in den Tab „Welt-Regeln“ (Operator-Rechte, Abgleich) | B5 |
| Regeln in Voreinstellungen | – | eine Voreinstellung lässt Add-on-Regeln unverändert | B5 (`presetValue`) |

#### 2.3.3 Kamera-Drohnen-Add-on gegen API 1.6

| Bedarf | 1.6 hat | Lücke | 1.7 |
|---|---|---|---|
| Wegpunkte | `FlightAssist` (Position, Höhe, Kurs) | Mission an der Drohne speichern, beim Aufheben behalten | B6 |
| Position halten | `holdPosition` | nur solange ein Spieler fliegt; nach dem Loslassen fällt die Drohne | B7, B10 (`PILOT_CHANGED`) |
| Gimbal | `registerGimbal`, `GimbalModel` | Neigungsrad (analoger Kanal), Aufnahme stabilisiert, Action-Cam vorn | A1 (Achsen), B9 |
| Follow me | – | der Spieler läuft, die Drohne fliegt allein | B7 |
| Point of Interest | Flughilfe mit Kurs | auch ohne Pilot | B7 |
| Zwei-Piloten-Gimbal | – | zweiter Spieler steuert den Gimbal | B8 |
| Modi schalten | 4 Aktionen | mehr als 4, benannt, Schalter statt Taster | A1 |
| Status im OSD | `HudElement` | im OSD-Editor platzierbar | A2 |
| Mission bearbeiten | Konfigurator-Tab | Knopf an der Drohne | B10 |
| Lange Flüge (Stativ, Missionen) | – | 10 Minuten Höchstdauer wie im Heimflug | B7 (Ende mit dem Akku) |
| Mission läuft nach dem FPV weiter | – | Verlassen der FPV-Ansicht startet den Heimflug | B7 (Übergabe beim FPV-Ende) |
| Start vom Boden, Landung | – | nur scharfe Drohnen in der Luft, keine Landung als Ausgabe | B7 (`takeoff`, `land`) |
| Ende eines Flugs | – | kein Ereignis | B7 (`AUTONOMY_ENDED`) |
| Moduswahl am 3-Stufen-Schalter | – | – | A1 (Kanal-Bereiche wie bei eingebauten Funktionen) |
| Gimbal-Kopf, Brennweite, Aufnahme, Livebild | `GimbalProvider`, Aufnahme-Taste | Modell starr, feste Brennweite, Aufnahme nur per Taste, Livebild nur über die Brille | B9 |

---

## 3. Architektur und Datenformate

Allgemein: Alle neuen API-Typen liegen unter `de.lardtom.propwash.api` (MIT), alle Implementierungen in internen
Paketen. Neue Methoden an bestehenden API-Interfaces sind `default`-Methoden. Records der API entstehen nur über
Builder bzw. Fabrikmethoden (sie bekommen in Nebenversionen Felder). Neue Enum-Konstanten nur am Ende.

### 3.1 A1 – Schalterfunktionen und Achsen

**API** (neues Paket `api.input`, beide Seiten registrieren identisch in der Common-Init):

```java
AddonSwitch ghost = PropwashApi.switches().register(SwitchSpec.builder(Identifier.fromNamespaceAndPath("noclipmod", "ghost"))
        .toggle(true).latchesOnGamepad(true).serverSync(true).build());
AddonAxis tilt = PropwashApi.switches().registerAxis(AxisSpec.builder(Identifier.fromNamespaceAndPath("camdrones", "gimbal_tilt"))
        .center(0.0).keyRateDps(60).serverSync(false).build());
PropwashClientApi.bindSwitchKey(ghost.id(), myKeyMapping);
PropwashClientApi.bindAxisKeys(tilt.id(), tiltUpKey, tiltDownKey);
```

- `SwitchSpec`: `id`, `toggle` (Schalter, Standard; sonst Taster), `latchesOnGamepad`, `serverSync`, `nameKey`
  (Standard `<ns>.switch.<pfad>`), `descriptionKey` (optional). `AxisSpec`: `id`, `center` (−1…1), `keyRateDps`,
  `serverSync`, `nameKey`.
- `AddonSwitch#active(Entity drone)`: am Server der zuletzt gemeldete Zustand (nur `serverSync`), beim Piloten der
  lokale Zustand, sonst `false`. `AddonAxis#value(Entity drone)` entsprechend (−1…1).
- Client: `ClientInput#switchActive(Identifier)`, `ClientInput#axis(Identifier)`; Flugmodell:
  `FlightState#addonSwitch(String id)`, `FlightState#addonAxis(String id)`; Gimbal: `GimbalContext#axis(String id)`.
- Event `PropwashEvents.ADDON_SWITCH_CHANGED(AddonSwitchChanged: player, drone, id, active)` am Server.
- Grenzen: höchstens 64 Schalter und 16 Achsen über alle Add-ons; Ids `[a-z0-9_.-]+:[a-z0-9_./-]+`, höchstens 64 Zeichen;
  Namensraum `propwash` verboten (C1).

**Intern** (`input.addon`):
- `AddonSwitchRegistry` (Reihenfolge der Anmeldung, client-lokaler Index 0…63). Zustände als eigene `long`-Maske
  `RadioFrame#addonSwitches` neben den eingebauten Schaltern; Achsen als `float[16]`.
- **Eingebaute Maske wird `long`**: `RadioFrame#activeSwitches`/`readableSwitches`, `SwitchEvaluator#bit` und alle
  Aufrufer (`PilotSession`, `FlightModeSelector`, `Mode3dSelector`, `SwitchCommandRunner`) wechseln von `int` auf `long`.
  Heute 29 eingebaute Werte; mit weiteren 0.6.0-Funktionen wäre die Grenze von 32 erreicht.
- Belegung: `DeviceProfile.switches` behält Schlüssel mit Doppelpunkt (`"noclipmod:ghost"`) beim Normalisieren
  **unverändert**, auch wenn das Add-on gerade fehlt (heute verwirft die Normalisierung unbekannte Schlüssel).
  Analoge Belegung neu: `DeviceProfile.addonAxes` (`Map<String, AxisAssignment>`: Quelle Achse/Tasten, Index, invertiert,
  Totzone); fehlt das Feld, ist es leer (`JsonConfig`-Migration).
- Tastatur: Das Add-on meldet sein `KeyMapping` selbst beim Loader an (eigener Name `key.<ns>.…`, eigene Kategorie) und
  übergibt es; Propwash liest es wie die eigenen Funktionstasten (Taster bzw. Umschalter).
- Tab „Modi“: dritte Seite „Add-ons“ neben „Flug“ und „Befehle“, nur sichtbar, wenn mindestens eine Funktion
  angemeldet ist. Zeilen werden aus der Registry gebaut (kein Enum). Der Empfänger-Assistent fragt Add-on-Funktionen
  nicht ab.
- Netz: C2S `propwash:addon_switch` (Entity-Id, Reset-Epoche, Liste ≤ 16 aus Id + an/aus), nur bei Änderung und einmal
  vollständig beim Start einer Pilotensitzung; C2S `propwash:addon_axis` (Entity-Id, Epoche, Liste ≤ 8 aus Id + `short`),
  höchstens 10 je Sekunde, nur Achsen mit `serverSync`. Server prüft Pilot, Epoche, Rate (20/s), bekannte Id mit
  `serverSync`; Zustände je Drohne flüchtig, gelöscht beim Pilotenwechsel. Beide Pakete gehen über dieselbe Verbindung
  **vor** dem nächsten `flight_state`, die Reihenfolge ist also gesichert (wichtig für A3).
- S2C `propwash:addon_features` beim Betreten der Welt: Ids der Server-Schalter (`serverSync`), Achsen und
  Kollisionsregeln, die der Server kennt. Der Client schickt nur bekannte Ids und wendet Kollisionsregeln nur an, wenn der
  Server sie kennt (Add-on nur beim Spieler: Regel wirkungslos statt Rücksetzungen).
- **Namen der Zubehör-Aktionen** (neu an `AccessoryBehavior`): `Optional<String> actionNameKey(int number)` (Standard
  leer, `number` 1–4). Der Tab „Modi“ zeigt dann „Zubehör-Aktion 1 – Dispenser feuern“. Fliegt ein Spieler zum ersten
  Mal eine Drohne mit einem Zubehör, das Namen meldet, zeigt Propwash einmal je Zubehörtyp einen Hinweis mit Name und
  aktueller Belegung jeder Aktion (Schalter der Funke bzw. Taste, sonst „nicht belegt“ mit Verweis auf den Tab „Modi“);
  gemerkt wird das in den Client-Einstellungen. Der Rechtsklick bleibt „FPV beenden“ (E14).
- **Mehrstufige Schalter**: Add-on-Schalter werden wie eingebaute Funktionen per Kanal und Bereich belegt. Drei Schalter
  auf den drei Bereichen eines 3-Stufen-Kanals ergeben eine Moduswahl; eine eigene Signatur dafür gibt es nicht.

### 3.2 A2 – OSD-Elemente und Warnungen

```java
PropwashClientApi.registerOsdElement(OsdElementSpec.builder(Identifier.fromNamespaceAndPath("mobdefense", "ammo"))
        .position(1, 3).align(OsdAlign.LEFT).maxChars(8).preview("AMMO 12")
        .content(ctx -> OsdText.of("AMMO " + count(ctx.drone()), OsdTone.NORMAL)).build());
PropwashClientApi.registerOsdWarning(Identifier.fromNamespaceAndPath("noclipmod", "ghost"),
        ctx -> ghostOn(ctx) ? OsdText.of("GHOST", OsdTone.CAUTION) : null);
```

- Neues Paket `api.client.osd`: `OsdElementSpec` (Id, `nameKey`, Standardplatz Spalte 0–29 / Zeile 0–15, `OsdAlign`,
  `enabledByDefault` (Standard aus), `OsdGroup` (Standard `ADDON`), `maxChars` 1–30, `preview`), `OsdContent`,
  `OsdText` (Text + `OsdTone.NORMAL|ACCENT|CAUTION|WARNING`), `OsdContext` (Drohne, `FlightTelemetry` aus 1.6, FPV oder
  Sichtflug, Editor-Vorschau, Zeit), `OsdWarning`.
- Darstellung macht Propwash: OSD-Schrift, Farbe je Ton aus den OSD-Einstellungen des Spielers, Skalierung, Deckkraft,
  Hintergrund, 4:3-Bildbereich. Erlaubt sind druckbare ASCII-Zeichen; anderes wird `?`, zu lange Texte werden gekürzt.
- Warnungen erscheinen im eingebauten Element `WARNINGS` hinter Propwashs eigenen Warnungen; mehrere wechseln sich alle
  1,5 s ab (wie im Betaflight-OSD).
- Intern `osd.addon`: `AddonOsdElements`; `OsdRenderer` zeichnet nach den eingebauten Elementen die Add-on-Elemente;
  `OsdConfig.elements` behält Schlüssel mit Doppelpunkt (`"mobdefense:ammo"`) auch ohne installiertes Add-on;
  `OsdConfig.VERSION` bleibt 4. Der OSD-Editor (`OsdConfigTab`) listet sie in der Gruppe „Add-ons“, verschiebbar wie
  eingebaute Elemente. `OsdLayoutSpec`-Export und -Import (API 1.x) nehmen die Schlüssel mit.
- Fehler: Wirft `content` eine Ausnahme, wird das Element für die Sitzung abgeschaltet (ein Log-Eintrag).

### 3.3 A3 – Kollision und Physik

```java
PropwashApi.drones().registerCollisionRule(Identifier.fromNamespaceAndPath("noclipmod", "ghost"), new CollisionRule() {
    public CollisionFlags flags(CollisionQuery q) {
        return q.switchActive(GHOST) && q.pilot().map(NoclipMod::allowed).orElse(false) ? CollisionFlags.GHOST : CollisionFlags.SOLID;
    }
});
```

- `CollisionFlags` (Record, Fabrik `SOLID`, `GHOST`, `with…`): `blocks`, `fluids`, `entities`, `snags`.
  Mehrere Regeln: jedes Feld ist das UND aller Regeln (jede Regel darf eine Kollision abschalten).
- `CollisionQuery`: `drone()`, `view()` (`DroneView`), `pilot()`, `side()` (`CLIENT`/`SERVER`), `autonomous()`,
  `switchActive(Identifier)`, `axis(Identifier)`.
- `CollisionRule#onBlockContact(BlockContact)`: nach jeder Bewegung mit Kontakt, dort wo das Flugmodell läuft (Client
  des Piloten; Server im Heimflug und im autonomen Flug). `BlockContact`: Level, Position, `BlockState`, Seite, Normale,
  Aufprall in m/s und die Antwort der Fläche aus dem Strang `surface` als Startwert; setzbar `restitution` (0–0,95),
  `grip` (0,05–1,5), `impactScale` (0–1, wie `sim.SurfaceResponse`). Regeln laufen nacheinander, jede sieht das
  Ergebnis der vorigen; Propwash kappt am Ende.
- Auswertung: Client einmal je Tick (nicht je Teilschritt), Server bei jedem `flight_state`-Paket und je Tick im
  autonomen Flug.
- **Autorität**: Der Server lässt die Kollisionsprüfung in `DronePiloting#onState` (`noBlockCollision`, `PathCheck`)
  nur aus, wenn **seine eigene** Auswertung `blocks = false` ergibt. Widerspricht der Client, greift die bekannte
  Rücksetzung (wie heute). Schalter, auf die eine Regel schaut, kommen vor dem Bewegungspaket (A1).
- Wirkung bei `blocks = false` (Client): Bewegung ohne Block-Kollision, kein `fillSurface`, Bodenabstand unendlich,
  kein Kamera-Schutz (`FpvEyeGuard`), Darstellung in Blöcken wie im Zuschauermodus (eigener Kamera-Mixin von
  Propwash), Gate-Rahmen ohne Kollision. Beides fragt eine neue interne Abfrage `drone/client/CameraClip` ab
  („Kamera darf in Blöcke“), bei der sich weitere Quellen mit `register(BooleanSupplier)` anmelden; erste weitere Quelle
  ist die Freecam (`gameplay.md` §3.B). So bleiben `FpvEyeGuard` und der Kamera-Mixin in der Hand dieses Strangs. `fluids = false`: kein Wasser und keine Lava im Flugmodell. `entities = false`:
  keine Treffer und kein `DRONE_HIT_ENTITY`. `snags = false`: Ranken und Netze greifen nicht.
- **Zurückschalten**: Wird eine Drohne wieder fest, während ihre Hülle in Blöcken steckt, bleibt sie durchlässig, bis die
  Hülle frei ist; das OSD zeigt `INSIDE BLOCK` bzw. `IM BLOCK`. Kein Teleport.
- **Rennen**: Runden mit abgeschalteter Kollision zählen als Runde mit Hilfe (Grund „Sonstiges“, Id der Regel), wie
  Flughilfen in 1.6 (`docs/design/assisted-laps.md`).
- **Oberflächen**: Die Grundlage liefert der 0.6.0-Strang `surface` aus [`gameplay.md`](gameplay.md) §3.F
  (Block-Tags `#propwash:drone_bouncy|drone_cushion|drone_sticky`, `sim.SurfaceResponse`, `MoveReport.surfaceImpactScale`,
  `drone.SurfaceMaterials`). A3 hängt sich **hinter** `SurfaceMaterials.at(...)`: Add-on-Regeln bekommen dessen Antwort
  als Startwert und dürfen sie ändern. Eine eigene Oberflächen-Logik baut A3 nicht.
- **Bitgleich**: Ohne Regel ändert sich nichts gegenüber dem Strang `surface`. Pures Java bleibt pur: `sim.MoveReport`
  bekommt nur das Feld `ghost`; die Minecraft-Seite (`drone.collision`) füllt es.

### 3.4 B1 – Werfen wie ein Dispenser

```java
DispenseResult r = context.dispense(slot, DispenseOptions.defaults().speedScale(1.0).inaccuracy(1.0).consume(true));
```

- Am Server, aus `AccessoryBehavior#onAction` oder `tick`. Ablauf in `accessory.dispense.AccessoryDispenser`:
  1. Eimer mit Flüssigkeit (Wasser, Lava, Pulverschnee) → `REFUSED`, solange `allowFluids` aus ist (Standard).
  2. `ProjectileItem` (Pfeile aller Art, Schneeball, Ei, Wurf- und Verweiltränke, Windkugel, Feuerkugel, Feuerwerk,
     Erfahrungsfläschchen) → Geschoss an `MountPose#front()` in **genau** `forward()`, Mündungstempo und Streuung wie
     beim Vanilla-Dispenser, Drohnentempo addiert, Besitzer = Pilot (PvP-Regeln greifen).
  3. TNT → gezündetes TNT an der Mündung.
  4. Alles andere → Vanilla-`DispenseItemBehavior` mit einer `BlockSource` am Mündungsblock und der Richtung, die
     `forward()` am nächsten liegt; Rückgaben (z. B. Flaschen) landen über einen Adapter im Zubehör-Inventar.
- `DispenseResult`: Status (`FIRED`, `PLACED`, `USED`, `EMPTY`, `REFUSED`, `BLOCKED`, `DENIED`), erzeugte Entity, Rückstoß in N·s.
  Mit `applyRecoil` (Standard an) wendet Propwash den Rückstoß selbst über `DronePhysics#impulse` an (Masse × Tempo des
  Geschosses aus einer Tabelle; Windkugel zusätzlich 2,5 N·s).
- Grund für den Platz in Propwash: Das Vanilla-Verhalten ändert sich mit jeder Minecraft-Version; der Backport (Phase 4)
  baut den Dispenser fest ein und nutzt dieselbe interne Klasse.
- `DispenseOptions`: `speedScale`, `inaccuracy`, `allowFluids` (Standard aus), `applyRecoil` (Standard an), `consume`
  (Standard an; aus = das Item bleibt im Slot, Rückgaben wie Flaschen entfallen).
- **Rechte**: Vor TNT, Flüssigkeiten und allen Block-Verhalten prüft Propwash am Mündungsblock die Bau-Rechte des
  Piloten (`ServerPlayer#mayInteract`, Abenteuermodus, Spawnschutz). Fehlen sie, passiert nichts und der Status ist
  `DENIED`. Geschosse prüfen keine Bau-Rechte; für sie gelten PvP-Regeln über den Besitzer.
- **Eigene Treffer**: Geschosse aus `dispense` treffen die abfeuernde Drohne nicht, bis sie deren Hülle verlassen haben
  (höchstens 10 Ticks, wie `leftOwner` bei Vanilla).
- **Tempo**: Gezündetes TNT und ausgeworfene Items bekommen das Tempo der Drohne plus den Vanilla-Auswurf in
  `forward()`, wie Geschosse.
- **Auslösen am Server** (neu an `AccessoryRegistry`, als `default`-Methode): `boolean triggerAction(Entity drone, int number, boolean pressed,
  ServerPlayer actor)` wirkt wie das Paket des Piloten (gleiche Ratenbegrenzung, Event `ACCESSORY_ACTION`); `false`,
  wenn kein Zubehör der Drohne die Aktion hat. Die Rechte prüft der Aufrufer. Gedacht für Befehle und Tests auf dem
  dedizierten Server.
- **Treffer durch Mobs** (ohne neue Signatur, im Selbsttest bestätigt): Nahkampf (`Mob#doHurtTarget`), Pfeile,
  Feuerbälle, Dreizack und Shulker-Geschosse treffen die Hülle; `DRONE_DAMAGED` liefert die Schadensquelle mit dem Mob
  als Verursacher.

### 3.5 B2 – Inhalt sichtbar

- `AccessoryBehavior#display(int slot)` → `Optional<ItemDisplay>` (Versatz in mm vom Anbaupunkt, Gieren/Nicken/Rollen,
  Skalierung). Propwash rendert das Item aus diesem Slot an der Drohne (Entity-Rendering, alle Zuschauer), deklarativ,
  ohne Render-Code im Add-on. Leerer Slot = nichts.
- Intern: Der Inhalt ist schon synchronisiert (`AccessoryContents`); `drone.client.render` kopiert die Stapel beim
  Extrahieren in den Render-Zustand und reicht sie als Item-Modelle ein.

### 3.6 B3 – Explosions- und Windstöße

- `QuadcopterEntity` fängt Stöße am Server ab (Explosion, Windkugel, sonstige `push`-Aufrufe) und feuert
  `PropwashEvents.DRONE_KNOCKBACK(DroneKnockback: drone, cause EXPLOSION|WIND_CHARGE|OTHER, deltaVelocity m/s,
  setScale, cancel)`. Fliegt ein Spieler die Drohne oder läuft ein Heim- oder autonomer Flug, wird Δv mit der Masse samt
  Beladung zum Stoß (`J = m · Δv`, gekappt auf 50 N·s) und geht über `propwash:drone_push` ins Flugmodell. `OTHER` wird
  standardmäßig nicht weitergeleitet (Berührungen regelt schon die Kontaktphysik).
- **Kein Kernverhalten:** Die Standard-Skala des Ereignisses ist 0. Ohne Add-on wird also nichts weitergeleitet, und
  geflogene Drohnen verhalten sich bei Explosionen und Windkugeln wie in 0.5.2. Ein Add-on setzt mit `setScale(...)` die
  Stärke für die Ursachen, die es betreffen (Mob Defense etwa für Explosionen von Mobs in der Nähe seiner Drohnen), oder
  bricht mit `cancel()` ab. Grund: nicht beauftragt, und auf Renn-Servern wäre ein Stoß durch fremde Windkugeln ein
  Griefing-Weg (E16).

### 3.7 B4 – Alle Drohnen eines Levels

`DroneAccess#loaded(ServerLevel)` → unveränderliche Liste aller geladenen Drohnen (Propwash führt sie ohnehin), z. B.
für Vibrationen (Sculk-Sensoren, Warden) aus dem Server-Tick des Add-ons.

`DroneAccess#cameraPose(Entity drone)` → `Optional<CameraPose>` am Server, neuer Record
`CameraPose(Vec3 eye, Vec3 look, Vec3 up, double verticalFovDegrees)`: Linse, Uptilt und Sichtfeld aus
`BuildAccess#cameraView` des Baus, gedreht mit der Lage der Drohne (ohne Gimbal-Nachlauf); leer ohne FPV-Kamera. Für
Mobs, die auf Blicke reagieren (Enderman, Creaking).

### 3.8 B5 – Add-on-Welt-Regeln

```java
WorldRule<Boolean> buckets = PropwashApi.worldRules().registerBoolean(Identifier.fromNamespaceAndPath("mobdefense", "allow_buckets"), false);
WorldRule<Integer> cooldown = PropwashApi.worldRules().registerInt(Identifier.fromNamespaceAndPath("mobdefense", "cooldown_ticks"), 4, 0, 100);
```

- Vanilla-Gamerules im Namensraum des Add-ons, aber in Propwashs Kategorie; der Tab „Welt-Regeln“ zeigt sie in einem
  eigenen Abschnitt je Add-on (Überschrift = Name aus den Mod-Metadaten), bearbeitbar im Einzelspieler und für
  Operatoren, sonst lesbar. Texte `gamerule.<ns>.<pfad>` und `….description`.
- `WorldRule#get(Level)` auf beiden Seiten (Client über Propwashs bestehenden Abgleich der Welt-Regeln).
- Registrierung nur in der Common-Init (Gamerules sind ein Registry). Nicht in Zonen überschreibbar.
- Voreinstellungen: `<T> void presetValue(WorldRule<T> rule, String presetId, T value)` legt den Wert einer Add-on-Regel
  in einer eingebauten Voreinstellung fest (`realistic`, `training`, `arcade`); ohne Eintrag lässt die Voreinstellung
  die Regel unverändert. Unbekannte Ids werfen. Die Anzeige „Voreinstellung aktiv“ berücksichtigt diese Werte.
- Nachtrag 19.10. (`fix-1910`): Die Überschrift des Abschnitts ist übersetzbar. Optionaler Sprachschlüssel
  `<ns>.world_rules.section` (`<ns>` = Namensraum der Regeln, in der Regel die Mod-Id; Kamera-Add-on etwa
  `camdrones.world_rules.section` → „Kamera-Drohnen“); fehlt er oder ist er leer, bleibt es beim Mod-Namen aus den
  Metadaten. Abwärtskompatibel, keine neue Signatur; Reihenfolge der Abschnitte nach der angezeigten Überschrift.

### 3.9 B6 – Daten an der Drohne

```java
DroneDataKey<Mission> MISSION = PropwashApi.droneData().register(Identifier.fromNamespaceAndPath("camdrones", "mission"),
        Mission.CODEC, Mission.EMPTY, DroneDataKey.Sync.TRACKING);
PropwashApi.droneData().set(drone, MISSION, mission);
```

- Schreiben nur am Server; lesen auf beiden Seiten (`Sync.TRACKING`) bzw. nur am Server (`Sync.NONE`); `get(ItemStack)`
  für das Drohnen-Item. Grenzen: 2 KiB je Schlüssel, 8 KiB je Drohne (NBT-Größe); Überschreitung wirft.
- Speicher: Entity-NBT `"AddonData": {"<ns>": {"<pfad>": <Codec-NBT>}}`; beim Aufheben Komponente
  `propwash:addon_data` (gleicher Aufbau), beim Platzieren zurück. Abgleich über einen Entity-Daten-Serializer
  `propwash:addon_data` (nur Schlüssel mit `TRACKING`).
- Fehlt ein Add-on, bleiben seine Daten **unverändert** erhalten (Entity, Item, Speichern und Laden). Kein Teil von
  Sharecodes.

### 3.10 B7 – Autonomer Flug mit Add-on-Flughilfe

```java
AutonomyResult r = PropwashApi.drones().autonomy().start(drone, owner, new FollowMeAssist(owner),
        AutonomyOptions.of(Identifier.fromNamespaceAndPath("camdrones", "follow")).label("FOLLOW"));
```

- Der Server fliegt die Drohne mit demselben Flugmodell wie im Heimflug (`rescue.ServerReturnFlight` wird zu einem
  allgemeinen Server-Flug; der Heimflug ist ein Fall davon). Statt `RescueAutopilot` liefert die Flughilfe des Add-ons
  `AssistOutput`; Pilotknüppel sind neutral, Operator-Achsen (B8) sichtbar. Die Flughilfe läuft auf dem Server-Thread
  und darf dort Spieler und Welt lesen (Follow me hält die `ServerPlayer`-Referenz selbst).
- `sensorRange()` gilt auch hier (Strahlen am Server). Zonen (Schwerkraft, Wind, Luftdichte) wirken wie im Heimflug.
- `AutonomyOptions.of(Identifier id)` (Id im Namensraum des Add-ons, C1), dazu `label`, `maxDuration`, `takeoff`.
- **Budget**: dasselbe `AutonomyBudget` wie der Heimflug (8 je Server, 2 je Spieler), Chunk-Tickets wie beim Heimflug.
  Heimflüge haben Vorrang: Add-on-Flüge belegen höchstens einen der zwei Plätze je Spieler und höchstens sechs der acht
  je Server. `AutonomyResult`: `STARTED`, `NO_BUDGET`, `PILOTED`, `NOT_READY` (nicht scharf, kein Akku, kein GPS-Fix),
  `DENIED`, `NOT_ARMABLE` (neu, am Ende).
- **Dauer**: Ein Add-on-Flug läuft, bis der Akku kritisch wird, nicht 10 Minuten wie der Heimflug (E21).
  `maxDuration(Duration)` kürzt das (1 s bis 60 min, Standard 60 min); 60 Minuten sind die harte Grenze, auch für Akkus
  aus Datenpaketen. Eine Welt-Regel dafür gibt es nicht.
- **Start vom Boden**: `takeoff(double metres)` (1–30 m). Propwash schärft mit denselben Prüfungen wie beim Piloten,
  steigt senkrecht auf die Höhe und übergibt dann an die Flughilfe. Scheitert das Schärfen, ist das Ergebnis
  `NOT_ARMABLE`, den Grund bekommt der Besitzer wie beim Piloten als Meldung. Ohne `takeoff` gilt `NOT_READY` wie bisher.
- **Landung**: `AssistOutput#land()` (neu, als `default`-Methode, damit Test-Doubles aus 1.6 weiter kompilieren): Propwash landet senkrecht an Ort und Stelle wie im Heimflug, entschärft am
  Boden und beendet den Flug mit Grund `LANDED`.
- **Übergabe beim FPV-Ende**: `autonomy().registerExitHandler(Identifier id, ExitHandler handler)`;
  `ExitHandler#onPilotExit(Entity drone, ServerPlayer pilot)` → `Optional<AutonomyRequest>`
  (`AutonomyRequest.of(FlightAssist assist, AutonomyOptions options)`). Verlässt der Pilot die FPV-Ansicht, ohne dass ein
  anderer Spieler übernimmt, fragt Propwash die Handler in Anmeldereihenfolge **vor** dem eigenen Verhalten (Heimflug
  bzw. Landung). Der erste Treffer startet den autonomen Flug nahtlos: Position, Tempo, Lage und Regler-Zustände bleiben,
  die Drohne sackt nicht ab. Ohne Treffer oder bei `NO_BUDGET` gilt Propwashs Verhalten wie in 0.5.2.
- **Ende**: Pilot übernimmt (Start einer Pilotensitzung), Akku kritisch (Heimflug bzw. Landung), Besitzer offline oder
  außer Funkreichweite, `maxDuration` erreicht, `stop(drone)`, Ausnahme in der Flughilfe (Landung an Ort und Stelle),
  neuer Start auf derselben Drohne. Event `PropwashEvents.AUTONOMY_ENDED(AutonomyEnded: drone, owner, id, reason)` am
  Server mit `AutonomyEnd` = `PILOT`, `LANDED`, `BATTERY`, `OWNER_OFFLINE`, `OUT_OF_RANGE`, `STOPPED`, `TIMEOUT`,
  `ERROR`, `REPLACED`. Nach `TIMEOUT` folgt wie nach `BATTERY` Propwashs Heimflug bzw. Landung.
  `autonomy().active(Entity drone)` → `Optional<Identifier>` nennt den laufenden Flug. Status an den Besitzer per S2C
  `propwash:autonomy_status` (Label ≤ 8 Zeichen im OSD, Grund des Endes als Meldung).

### 3.11 B8 – Zweiter Operator

- `DroneAccess#operators()`: `bind(drone, operator)` am Server (das Add-on entscheidet, wann; Propwash prüft:
  Operator ist nicht der Pilot, fliegt keine andere Drohne, ist in Videoreichweite; höchstens ein Operator je Drohne),
  `unbind(drone)`, `operator(drone)`.
- Der Operator sieht das Videobild wie ein Zuschauer (`SpectateSession` mit Operator-Rolle, S2C
  `propwash:operator_role`). Seine Funke fliegt nicht; ihre vier Knüppelachsen und vier Aktionen gehen mit 20 Hz als C2S
  `propwash:operator_input` (Entity-Id, 4 × `short`, Bitfeld) an den Server, der Rolle und Rate (≤ 25/s) prüft und als
  S2C `propwash:operator_state` an Pilot, Operator und alle Zuschauer der Drohne weitergibt.
- Lesen: `GimbalContext#operatorAxis(i)`, `GimbalContext#operatorAction(n)`, `FlightState#operatorAxis(i)`,
  `ClientInput#operating()`. Damit rechnen alle Clients mit denselben Eingaben dieselbe Gimbal-Lage.

### 3.12 B9 – Gimbal in Aufnahme und Kamerapfad

- `AccessoryDefinition` erlaubt `action_cam` auch auf der Halterung `front` (bisher nur `top`).
- `GimbalProvider#stabilizesRecording()` (Standard `false`): Bei `true` nimmt eine Action-Cam auf **derselben** Halterung
  wie der Gimbal mit der Gimbal-Lage auf (`video.record.CameraLook`), und der Flashback-Kamerapfad übernimmt sie.
- **Gimbal-Kopf am Modell**: `boolean modelPose(GimbalContext context, Quaternionf rotation)` an `GimbalProvider`
  (Standard `false`, das Modell bleibt starr). Hat das Modell des Zubehörteils ein Element `gimbal_head`, dreht Propwash
  es beim Rendern jeder sichtbaren Drohne um dessen Ursprung. Auf Clients, die die Drohne nur sehen, ist
  `GimbalContext#spectating()` `false` und die Operator-Achsen sind 0; was das Add-on dort braucht (etwa die Neigung),
  gleicht es selbst über Drohnen-Daten ab (B6, `Sync.TRACKING`). Propwash schickt dafür kein eigenes Paket.
- **Brennweite**: `double fovDegrees(GimbalContext context)` an `GimbalProvider` (Standard `Double.NaN` = Sichtfeld der
  Kamera aus dem Bau), horizontal, gekappt auf 20–150°. Wirkt auf FPV-Bild, Zuschauer, Operator und die Action-Cam-
  Aufnahme derselben Halterung.
- **Aufnahme und Foto** (Client): `PropwashClientApi.recording()` → `RecordingControl` mit `boolean start(Entity drone)`,
  `void stop()`, `boolean photo(Entity drone)`, `boolean recording()`. Erlaubt für Pilot, Operator (B8) und den
  zuschauenden Besitzer; nutzt die eingebaute Aufnahme mit ihren Einstellungen und ihrem Ordner, ein Foto ist ein PNG
  des aktuellen Bildes. `false`, wenn nicht erlaubt oder schon aktiv.
- **Livebild ohne Brille** (Client): `PropwashClientApi.spectate(Entity drone)` → `boolean` startet die bestehende
  Zuschauer-Sitzung für eine eigene Drohne mit denselben Prüfungen wie über die Brille (Videoreichweite, Kanal);
  beendet wird wie gewohnt.

### 3.13 B10 – Pilotenwechsel und Wartung

- `PropwashEvents.PILOT_CHANGED(PilotChanged: drone, previous, current)` am Server (aus `DronePiloting#onPilotChanged`).
- `PropwashClientApi.registerServiceExtension(ServiceExtension)`: `id`, `label`, `visible(drone)`, `open(drone, parent)`
  → Knopf im Kopf der Wartung (Schleichen + Rechtsklick), Kind-Fenster im Propwash-Theme (Theme-Pflicht wie in
  Handbuch §14.4). Höchstens drei Knöpfe sichtbar, weitere unter „Mehr“.

### 3.14 C1 – Namensraum in der API

- Neue pure Klasse `apiimpl.NamespaceRules`: `isReserved(String namespace)` (genau `propwash`), `requireForeign(id, what)`,
  `refuse(id, what)` (Fund ohne Ausnahme), `callerMod()` (erster Stack-Rahmen außerhalb `de.lardtom.propwash` →
  Mod über den Plattform-Dienst C2).
- **Neue Punkte aus 1.7** (A1, A2, A3, B5, B6, B10) und Punkte, die schon in 1.6 warfen (Teile, Zellchemien,
  Zubehör-Verhalten, HUD, Gimbal, Flughilfen, Kräfte, Presets, Beute-Tabellen, Kreativ-Tabs): `IllegalArgumentException`
  mit Mod und Id, englisch:
  `Mod 'x' tried to register 'propwash:y' (osd element): the namespace propwash is reserved for Propwash. Use your mod id.`
  Die bisherigen deutschen Texte dieser Stellen werden dabei durch denselben englischen Text ersetzt.
- **Bisher tolerante Punkte** (Konfigurator-Tabs, Werkbank-Erweiterungen, Flugschul-Szenen): Die Anmeldung wird
  **verweigert** (nicht übernommen), ohne Ausnahme, mit Log-Fehler und Fund für den Hinweis. So bricht kein Add-on, das
  unter 0.5.2 lief, beim Start ab (SemVer).
- Propwash selbst meldet nicht über die API an; die eigenen Wege bleiben unberührt.

### 3.15 C2 – Namensraum-Prüfung

**Pakete:** `guard` (gemeinsam), `guard.client`, `mixin.guard` (`MappedRegistryMixin`), Plattform-Dienst
`platform.services.ModOrigins` (neu, eigene `META-INF/services`-Dateien in `fabric` und `neoforge`).

| Prüfung | Wie | Wann | Seite |
|---|---|---|---|
| Registry-Ids | Mixin auf `MappedRegistry#register`: nur bei Namensraum `propwash` wird der anmeldende Mod festgehalten (NeoForge: aktiver Mod-Container; Fabric: erster Stack-Rahmen aus einem Mod-Jar). Fund = Id, deren Anmelder nicht Propwash ist | nach dem Einfrieren der Register | beide |
| Übersetzungsschlüssel | für jeden Namensraum außer `propwash` die Sprachdateien `en_us` und die aktive Sprache aus **Mod-Paketen** lesen (Streaming); Schlüssel, deren Namensraum-Segment `propwash` ist (`propwash.…`, `key.propwash.…`, `item.propwash.…`, `part.propwash.…`, `gamerule.propwash.…`, …) und die Propwashs eigene `en_us` nicht kennt | Client-Ressourcen laden und neu laden | Client |
| Tastenbelegungen | `KeyMapping`-Namen `key.propwash.*`, die nicht aus `KeyDefaults` kommen | nach dem Laden der Optionen | Client |
| Netzwerk-Pakete | Fabric: globale Empfänger von Server und Client (`…Networking.getGlobalReceivers()`); NeoForge: Kanalliste des Netzwerk-Registers (per Reflexion, Fehler = Prüfung übersprungen); Fund = `propwash:*`, das nicht über `PropwashNet` kam | nach der Netzwerk-Registrierung | beide |
| Enum-Erweiterungen | über `Mixins.getConfigs()` alle fremden Mixin-Ziele unter `de.lardtom.propwash`; ist das Ziel ein Enum (laut Original-Klassendatei aus dem Propwash-Jar, gelesen mit der Class-File-API von Java 25), werden die Konstanten der Datei mit `getEnumConstants()` verglichen; Fund = jede zusätzliche Konstante. Client-Klassen nur am Client | Start | beide |
| Daten- und Asset-Dateien | Mod-Pakete auflisten (`data/propwash/…` bzw. `assets/propwash/…`); Pfad, den Propwash selbst mitbringt = Überschreiben bzw. Tag-Ergänzung (erlaubt); neuer Pfad = Fund. `lang/*.json` zählt nicht (siehe Übersetzungsschlüssel) | Serverdaten laden, `/reload`; Client-Ressourcen | beide |
| `/propwash`-Unterbefehle | Kinder des Befehlsknotens `propwash`, die Propwash nicht selbst angelegt hat; ausgenommen sind die in `docs/FOUNDATION.md` reservierten Unterbefehle offizieller Add-ons (Liste `guard.OfficialAddons`, in 0.6.0 leer, weil die Server-Mod zurückgestellt ist), solange das jeweilige Add-on geladen ist | nach der Befehlsregistrierung | Server |
| abgelehnte Anmeldungen | aus C1; dazu verworfene PW3-Teile-Tabellen für `propwash` aus fremden Jars ([`sharecodes-pw3.md`](sharecodes-pw3.md)), gemeldet über `NamespaceRules.refuse` | laufend | beide |
| Mixins in Interna (Info, kein Fund) | fremde Mixin-Configs mit Zielen unter `de.lardtom.propwash` | Start | beide |

- **Quelle**: Mod-Pakete (Pakete, die ein Loader für einen Mod anlegt) erzeugen Funde. Ressourcen- und Datenpakete der
  Spieler erzeugen keine Funde; neue Ids aus ihnen stehen nur als Info im Log.
- **Zuordnung**: `ModOrigins#modOfClass`, `#modOfPack`, `#modOfMixinConfig` (Fabric: Mixin-Dekoration bzw. Herkunftspfade
  der Mod-Container; NeoForge: Mod-Dateien und Mixin-Configs der Mod-Liste); unbekannt = „unbekannter Mod“.
- **Folge**: Log-Fehler je Mod (englisch, eine Zeile je Kategorie mit höchstens 20 Ids, Rest gezählt). Am Client einmal
  je Spielstart beim ersten Betreten einer Welt eine Chat-Zeile mit Knopf „Details“ (Fenster `guard.client.NamespaceReportScreen`:
  je Mod die Kategorien und Ids, „Kopieren“, „Regeln für Add-ons“ als Link auf die Website). Auf dem dedizierten Server
  bekommt jeder Operator einmal je Serverstart beim Beitritt eine Chat-Zeile mit Mods und Zahl der Funde und dem Verweis
  aufs Server-Log. Neue Funde nach einem Neuladen gehen nur ins Log.
- **Kein Absturz**: Jede Prüfung läuft in `try/catch`; ein Fehler der Prüfung selbst ist eine Warnung im Log und schaltet
  nur diese Kategorie ab.
- **Kosten**: Zeit je Lauf im Log (`Namespace check: n findings in x ms`); Ziel unter 50 ms mit Just More Parts.

### 3.16 C3 – Absturzbericht

Eigener Abschnitt „Propwash add-ons“ im Crash-Report (Fabric: `fabric-crash-report-info-v1`, NeoForge:
Crash-Callable): installierte Add-ons mit Version, Mods mit Mixins in Propwash-Interna (Zahl der Ziele), Funde der
Namensraum-Prüfung. Das beantwortet die Frage der Issue-Vorlage schon im Bericht.

### 3.17 D – Texte im Repo

- `ADDONS.md` (neu, englisch): Text „Building add-ons for Propwash“ aus dem Entwurf der Richtlinien **wörtlich**, mit
  einer Ausnahme: In der Liste der offiziellen Add-ons steht nur *Propwash: Just More Parts* (die zurückgestellte
  Server-Mod wird nicht genannt). Am Ende nur Links (Handbuch, Website, Issue-Vorlage). Eintrag in `LICENSE-MIT` (Dokumentation) und in
  `tools/public_export.json` unter `required`.
- `NOTICE`: Absatz „Reserved namespace“ aus dem Entwurf hinter den Lizenzteilen.
- `README.md`: derselbe Absatz im Abschnitt „Add-ons (Parts API)“ mit Link auf `ADDONS.md`.
- `.github/ISSUE_TEMPLATE/bug_report.yml` (GitHub-Formular, englisch): Propwash-, Minecraft- und Loader-Version,
  installierte Add-ons (Pflicht), Auswahl „Does it also happen without add-ons?“ (*Yes, also without add-ons* /
  *No, only with add-ons* / *Not tested yet*), Schritte, Log oder Crash-Report. `.github/ISSUE_TEMPLATE/config.yml`
  mit Links auf `ADDONS.md` und Discord; leere Issues bleiben erlaubt.

### 3.18 E – Tests nach dem Klon

- Befund: `loginShellOutputIsReadFromTheLastPathLine` scheitert unter Windows, weil `LoginShell.parse` mit
  `Path#isAbsolute` prüft (`/opt/…` ist dort nicht absolut). `builtinPartsAnalyseAndFlyBitIdentical` (und das in 0.5.2
  entfernte `groundRestFliesBitIdentical`) finden die Golden-Datei nicht, wenn das Arbeitsverzeichnis weder das Repo
  noch `common/` ist; dann schreibt `GoldenFile` eine neue Datei und schlägt fehl. Dieselbe Pfadsuche steckt in
  `AirframeGoldenTest`, `DischargeCurveTest`, `SaveFormatGoldenTest`, `FlashbackSplineTest`, `PartJsonTest` und
  mehreren `guide`-Tests.
- Fix: neue Test-Hilfe `testutil.TestPaths` (Systemeigenschaften `propwash.rootDir`/`propwash.testResources`, gesetzt
  im Test-Task von `common/build.gradle`; ohne sie Suche nach oben bis `settings.gradle`). Golden-Dateien werden über den
  Klassenpfad gelesen, neu geschrieben nur, wenn `propwash.testResources` gesetzt ist. `LoginShell.parse` prüft
  absolute Unix-Pfade am Text (`/` am Anfang) – Login-Shells gibt es nur unter Unix.
- Zusätzliches Risiko: Das Flugmodell nutzt `Math.sin` usw. (Intrinsics können sich zwischen arm64 und x86_64 im
  letzten Bit unterscheiden). Prüfung siehe §6.4; weichen die Hashes ab, bekommt die Golden-Datei eine zweite Spalte für
  `x86_64`, andere Architekturen überspringen den Bit-Vergleich mit klarer Meldung.

### 3.19 Ergänzungen aus den Entwürfen von Mob Defense und Kamera-Drohnen

Beide Entwürfe haben ihren Bedarf gegen API 1.6 und diesen Entwurf geprüft. Die Signaturen legt der Vertrags-Strang
mit allen anderen 1.7-Typen an; das Verhalten liefert der genannte Strang. „Bestätigung“ heißt: keine neue Signatur,
nur ein Prüfschritt.

| Bedarf | Ergebnis in 1.7 | Abschnitt | Verhalten |
|---|---|---|---|
| Zubehör per Use-Taste auslösen (Mob Defense) | nicht übernommen; Auslösen über Zubehör-Aktionen, dazu deren Namen und ein Hinweis | §3.1, E14, E35 | `addon-switches` |
| Namen der Zubehör-Aktionen | `AccessoryBehavior#actionNameKey(int)` | §3.1 | `addon-switches` |
| Bau-Rechte beim Werfen | Prüfung in `dispense`, `DispenseResult.Status.DENIED` | §3.4 | `accessory-gaps` |
| Eigene Geschosse, Tempo von TNT und Items | Verhalten von `dispense` | §3.4 | `accessory-gaps` |
| Werfen ohne Verbrauch | `DispenseOptions#consume(boolean)` | §3.4 | `accessory-gaps` |
| Aktion am Server auslösen | `AccessoryRegistry#triggerAction(Entity, int, boolean, ServerPlayer)` | §3.4 | `accessory-gaps` |
| Treffer durch Mobs | Bestätigung | §3.4 | `accessory-gaps` |
| Kamerablick am Server | `DroneAccess#cameraPose(Entity)`, `CameraPose` | §3.7 | `accessory-gaps` |
| Add-on-Regeln in Voreinstellungen | `WorldRules#presetValue(WorldRule<T>, String, T)` | §3.8 | `addon-world-rules` |
| Lange autonome Flüge (Kamera) | Ende mit dem Akku, `AutonomyOptions#maxDuration(Duration)` bis 60 min, keine Welt-Regel | §3.10, E21 | `addon-autonomy` |
| Mission läuft nach dem FPV weiter | `registerExitHandler`, `ExitHandler`, `AutonomyRequest` | §3.10 | `addon-autonomy` |
| Start vom Boden, Landung | `AutonomyOptions#takeoff(double)`, `AutonomyResult.NOT_ARMABLE`, `AssistOutput#land()` | §3.10 | `addon-autonomy` |
| Ende eines autonomen Flugs | `AUTONOMY_ENDED`, `AutonomyEnd`, `autonomy().active(Entity)` | §3.10 | `addon-autonomy` |
| Zonen im autonomen Flug | Bestätigung | §3.10 | `addon-autonomy` |
| Moduswahl am 3-Stufen-Schalter | Bestätigung (Kanal-Bereiche) | §3.1 | `addon-switches` |
| Gimbal-Kopf am Modell | `GimbalProvider#modelPose(GimbalContext, Quaternionf)` | §3.12 | `operator-gimbal` |
| Brennweite | `GimbalProvider#fovDegrees(GimbalContext)` | §3.12 | `operator-gimbal` |
| Aufnahme und Foto per API | `PropwashClientApi.recording()`, `RecordingControl` | §3.12 | `operator-gimbal` |
| Livebild vom Tablet | `PropwashClientApi.spectate(Entity)` | §3.12 | `operator-gimbal` |
| Lautstärke am Server, Flugmodell-Prüfstand, Rahmen-Rolle `CAMERA`, Flug außerhalb der Funkreichweite | nicht in 1.7 | E39 | – |

---

## 4. Kompatibilität

| Bereich | Verhalten |
|---|---|
| Alte Welten | Laden unverändert; Drohnen ohne `AddonData`, OSD- und Eingabe-Dateien ohne Add-on-Schlüssel. Neue Felder sind optional. |
| Downgrade auf 0.5.x | 0.5.x verwirft Add-on-Schlüssel in OSD- und Eingabe-Dateien beim Normalisieren; `AddonData` an Drohnen wird ignoriert. Kein Absturz. |
| Alte Codes | Sharecodes und Baupläne unverändert (Add-on-Daten sind nicht Teil davon). |
| Server/Client | Neue Payloads `propwash:addon_switch`, `addon_axis`, `addon_features`, `operator_input`, `operator_state`, `operator_role`, `autonomy_status`, Serializer und Komponente `propwash:addon_data` → Netzprotokoll steigt (17; die Integration setzt die endgültige Nummer einmal für alle 0.6.0-Stränge). 0.5.x und 0.6.0 trennt der Handshake mit klarer Meldung. |
| Add-on nur beim Spieler | Schalter, Achsen und OSD wirken lokal; Server-Schalter und Kollisionsregeln wirken nur, wenn der Server sie in `addon_features` meldet. |
| API | **1.7.0**, Nebenversion, nur Ergänzungen. Add-ons für 1.0–1.6 laufen unverändert; Ausnahme: Anmeldungen unter `propwash:` an bisher toleranten Stellen werden verweigert (ohne Absturz). Wer 1.7 nutzt: `PropwashApi.requireVersion(modId, "1.7")`, Abhängigkeit Fabric `"propwash": ">=0.6.0-"`, NeoForge `versionRange = "[0.6.0-alpha,)"` (beide nehmen auch `0.6.0-dev.N` und `0.6.0-rc.N` an; `>= 0.6.0` bzw. `[0.6.0,)` lehnen Vorabstände ab, Übersicht `README.md` §3.2). Weitere 0.6.0-Ergänzungen anderer Stränge (z. B. PW3-Teile-Tabellen) gehen in dieselbe Nebenversion. |
| Just More Parts 1.1.1 | keine Änderung nötig; ergänzt nur vorhandene Propwash-Tags (`data/propwash/tags/item/parts/*.json`) → erlaubt, 0 Funde (Selbsttest). Propwash 0.6.0 trägt keine Unverträglichkeit für JMP ein; JMP 1.1.x läuft im Altmodus (Übersicht `README.md` §3.5). |
| Noclip 0.1.x | greift per Mixin in Stellen, die 0.6.0 ändert (`DronePiloting#onState`, `PilotSession`, `SwitchFunction`); mit `defaultRequire: 1` bricht es beim Start ab, sobald ein Ziel fehlt. Deshalb behalten die Stränge, die diese Klassen ändern, Namen und Deskriptoren der Ziele bei, wo es geht (geschützte Signaturen aus dem echten Jar). Was sich nicht halten lässt (etwa die `int`-Maske von `SwitchFunction`), ersetzt die API 1.7; Changelog (Kompatibilität) und Handbuch (Umstiegsbeispiel aus §7) nennen das offen. Geprüft wird mit dem echten Jar. Die Nachricht an den Autor schreibt der Projektinhaber vor dem Release (Entwurf liegt vor). |
| Backport 1.21.1 | ohne API; die internen Klassen für Werfen, Anzeige und autonomen Flug sind so geschnitten, dass der fest eingebaute Dispenser und die programmierbare Drohne sie übernehmen können. |

---

## 5. UI und Texte

### 5.1 Neue Texte (Auszug, EN / DE)

| Schlüssel | EN | DE |
|---|---|---|
| `propwash.input.modes.page.addons` | Add-ons | Add-ons |
| `propwash.input.modes.addon_inactive` | Inactive on this server | Auf diesem Server inaktiv |
| `propwash.input.modes.axis` | Analog channel | Analoger Kanal |
| `propwash.osd.group.addon` | Add-ons | Add-ons |
| `propwash.osd.warning.inside_block` | INSIDE BLOCK | IM BLOCK |
| `propwash.drone.collision.denied` | The server doesn't allow flying through blocks here. | Der Server erlaubt hier kein Fliegen durch Blöcke. |
| `propwash.drone.autonomy.started` | %s flies on its own (%s) | %s fliegt selbstständig (%s) |
| `propwash.drone.autonomy.ended.*` | Pilot took over / Battery low / Out of range / Stopped by add-on | Pilot hat übernommen / Akku schwach / Außer Reichweite / Vom Add-on beendet |
| `propwash.drone.operator.bound` | You control the camera of %s | Du steuerst die Kamera von %s |
| `propwash.drone.operator.pilot` | %s controls your camera | %s steuert deine Kamera |
| `propwash.guard.hint` | %s uses the reserved namespace propwash – this may break with the next update. | %s nutzt den reservierten Namensraum propwash – das kann mit dem nächsten Update kaputtgehen. |
| `propwash.guard.hint.many` | %s add-ons use the reserved namespace propwash – … | %s Add-ons nutzen den reservierten Namensraum propwash – … |
| `propwash.guard.details` | Details | Details |
| `propwash.guard.title` | Reserved namespace | Reservierter Namensraum |
| `propwash.guard.category.*` | Registry ids, Translation keys, Key mappings, Network payloads, Enum values, Data files, Assets, Commands, Refused registrations | Registry-Ids, Übersetzungsschlüssel, Tastenbelegungen, Netzwerk-Pakete, Enum-Werte, Datendateien, Assets, Befehle, Abgelehnte Anmeldungen |
| `propwash.guard.mixins` | Mixins into Propwash internals (allowed, at own risk) | Mixins in Propwash-Interna (erlaubt, auf eigenes Risiko) |
| `propwash.guard.copy` / `.rules` | Copy / Add-on rules | Kopieren / Regeln für Add-ons |
| `propwash.guard.server_hint` | Server: %s use the reserved namespace propwash (%s findings, see server log). | Server: %s nutzen den reservierten Namensraum propwash (%s Funde, siehe Server-Log). |

Texte aus Add-ons (Schalter, Achsen, OSD-Elemente, Welt-Regeln, Wartungsknöpfe) liegen in deren Namensraum.

### 5.2 Theme und GUI-Skalierung

- Alle neuen Fenster und Seiten im Propwash-Theme (`ui.client.theme`): Seite „Add-ons“ im Tab „Modi“, Add-on-Gruppe im
  OSD-Editor, Abschnitt je Add-on im Tab „Welt-Regeln“, Knöpfe in der Wartung, Fenster „Reservierter Namensraum“.
- Pflichtgrößen wie bei der `ui-scale`-Prüfung aus 0.5.2: GUI 320×240 und 854×480, Englisch und Deutsch. Die drei
  Seiten-Reiter im Tab „Modi“ stehen auch bei 320×240 in einer Zeile; reicht der Platz nicht, werden sie schmaler (Text
  gekürzt, voller Text im Tooltip), nie umgebrochen. Das Detail-Fenster scrollt, „Kopieren“ und „Schließen“ bleiben
  immer sichtbar.
- Die Chat-Zeile des Hinweises hat einen klickbaren Knopf `[Details]` in Propwashs Akzentfarbe.

---

## 6. Tests – vollständig automatisch prüfbar

### 6.1 JUnit

| Test | prüft |
|---|---|
| `apiimpl.NamespaceRulesTest` | `propwash` reserviert, `propwash_example`/`minecraft`/`justmoreparts` nicht; Ausnahme an neuen Punkten, Verweigerung ohne Ausnahme an alten; Text nennt Mod und Id |
| `guard.KeyClassifierTest` | Tabelle von Übersetzungsschlüsseln (`propwash.x`, `key.propwash.x`, `item.propwash.x`, `key.category.propwash`, `gui.propwashy` = kein Treffer); bekannter Schlüssel = Überschreiben |
| `guard.ResourcePathsTest` | vorhandener Pfad = erlaubt (inkl. Tag-Ergänzung wie bei JMP), neuer Pfad = Fund, Spielerpakete = nur Info |
| `guard.EnumConstantsTest` | Original-Konstanten von `SwitchFunction`, `OsdElement` per Class-File-API = `values()` im Test (keine Mixins); künstliche Zusatzkonstante = Fund |
| `guard.ReportTest` | Log-Format, Kappung auf 20 Ids, einmal je Start |
| `input.SwitchMaskTest` | `long`-Maske, Bits ab Position 31, alle Selektoren |
| `input.AddonSwitchesTest` | Taster/Schalter, Gamepad-Rasten, 64er-Grenze, Belegung `ns:pfad` bleibt beim Normalisieren ohne Add-on erhalten, Achsen mit Totzone und Tastenrate, Namen der Zubehör-Aktionen, Hinweis einmal je Zubehörtyp |
| `osd.AddonOsdTest` | Add-on-Schlüssel bleiben in `OsdConfig`, Standardplatz, Klemmen ins Raster, Zeichensatz, Kürzen, Wechsel der Warnungen |
| `sim.AddonCollisionTest` | UND-Verknüpfung der Flags, `MoveReport` im Geistmodus ohne Kontakt, Kette der Kontaktantworten auf `SurfaceResponse` mit Kappung; `AddonFlightHooksTest#unusedHooksKeepTheFlightBitIdentical` erweitert, Golden-Dateien unverändert |
| `accessory.dispense.DispenseMathTest` | nächste Richtung zu `forward()`, Rückstoß-Tabelle, Eimer verweigert, `consume(false)` ohne Verbrauch, Tempo von TNT und Items, Schutz vor eigenen Treffern endet nach 10 Ticks |
| `drone.CameraPoseTest` | Blick aus Lage, Linse und Uptilt, Sichtfeld aus `CameraView`, leer ohne Kamera |
| `accessory.ItemDisplayTest` | Transformation mm → Modellraum |
| `drone.KnockbackTest` | Δv → Stoß mit Beladung, Kappung, Ursachenfilter |
| `drone.data.DroneDataTest` | Codec hin und zurück, Größen, Item-Komponente, fremde Daten bleiben erhalten |
| `rescue.AutonomousAssistTest` | Server-Flug mit Halten und Folgen, Budget geteilt mit Heimflug und Vorrang des Heimflugs (1 von 2, 6 von 8), Ende mit dem Akku nach mehr als 10 Minuten, `maxDuration`, Start vom Boden, `land()`, Übergabe beim FPV-Ende ohne Höhenverlust, alle Abbruchgründe mit `AUTONOMY_ENDED` |
| `drone.operator.OperatorChannelTest` | Quantisierung, Rate, Rollenprüfung |
| `api.ApiContractTest` | `API_VERSION` 1.7.0, neue Typen vorhanden (auch `AccessoryDefinition#ledPathMm` aus dem Strang `led-path`), alle 1.6-Signaturen unverändert (auch der bisherige Konstruktor von `AccessoryDefinition`), jede neue Methode an einer Schnittstelle aus 1.6 ist `default` |
| `server.AddonPresetValueTest` | `presetValue` für `realistic`, `training`, `arcade`; unbekannte Preset-Id oder fremde Regel → Fehler mit Text; Voreinstellung anwenden setzt den Add-on-Wert, ohne Eintrag bleibt der Standard |
| `testutil.TestPathsTest`, `FfmpegDetectionTest` | Pfade ohne Arbeitsverzeichnis, `LoginShell.parse` ohne Betriebssystem-Abhängigkeit |

### 6.2 Selbsttest (Fabric und NeoForge, Quell-Set `devtest`)

Gruppe `guard:`:
1. Sauberer Lauf mit Beispiel-Add-on und Just More Parts 1.1.1 als Jar: **0 Funde** (harte Prüfung, fängt Fehlalarme
   bei Propwashs eigenen Anmeldungen), Laufzeit im Log; dabei Altmodus aktiv (Hinweis genau einmal, nur ein
   Sharecode-Knopf in der Werkbank). Derselbe Lauf mit dem aktuellen JMP-1.2.0-Entwicklungsstand: 0 Funde, kein
   Altmodus-Hinweis.
2. Probe-Mod `nsprobe` (eigenes Quell-Set `nsprobe` mit eigener Mod-Id, nur in Dev-Runs mit `-PpropwashNsProbe=true`,
   nie im Release-Jar): registriert `propwash:nsprobe_item`, bringt `propwash.nsprobe.text` und `key.propwash.nsprobe`,
   ein `KeyMapping` `key.propwash.nsprobe`, ein Paket `propwash:nsprobe_ping`, einen Enum-Wert an `SwitchFunction` und
   `OsdElement` per Mixin, `data/propwash/recipe/nsprobe.json` (neu), eine Ergänzung von
   `data/propwash/tags/item/parts/frame.json` (erlaubt), eine überschriebene Propwash-Textur (erlaubt), eine neue Textur
   unter `assets/propwash/` und `/propwash nsprobe`; dazu ein HUD-Element `propwash:probe` (Ausnahme erwartet, die Probe
   fängt sie) und einen Konfigurator-Tab mit `propwash`-Id (Verweigerung erwartet). Erwartet: jede Kategorie genau mit
   diesen Ids, nichts Erlaubtes als Fund, kein Absturz, Chat-Hinweis genau einmal (zweiter Weltbeitritt und F3+T ohne
   zweiten Hinweis).
3. Bildschirmfotos `guard_hint_{en,de}`, `guard_report_{en,de}_{854x480,320x240}`.

Gruppe `addon-api-17:` (Beispiel-Add-on erweitert, je Baustein eine eigene Datei unter `dev/example`):
- A1: Schalter „Geist“ und Achse „Gimbal-Neigung“ auf der Seite „Add-ons“, Belegung speichern, Add-on aus
  (`PROPWASH_DEV_EXAMPLE=off`) und wieder an → Belegung noch da. Bildschirmfotos `modes_addons_{en,de}_{854x480,320x240}`.
- A2: Element „Munition“ im OSD-Editor verschieben, im Flug sichtbar, Warnung `GHOST`; Fotos `osd_addon_*`.
- A3: Einzelspieler: mit „Geist“ durch eine 3 Blöcke dicke Wand, Schalter in der Wand aus → `IM BLOCK`, nach dem
  Herausfliegen fest; Antworten der Oberflächen-Tags ohne Regel unverändert, eine Beispiel-Regel macht Stein federnd;
  Runde mit Geist als Runde mit Hilfe markiert.
- B1/B2: Werfer schießt Pfeil, Windkugel (Rückstoß im Flugmodell messbar), Wurftrank, TNT, Knochenmehl; Wassereimer
  verweigert; Speer und Rakete an der Drohne sichtbar, nach dem Boost leer. Fotos `accessory_display_*`. Werfen im
  Spawnschutz und im Abenteuermodus → `DENIED`; eigener Pfeil bei Gegenwind trifft die eigene Drohne nicht; TNT fällt mit
  dem Tempo der Drohne; Schlag eines Zombies und Pfeil eines Skeletts lösen `DRONE_DAMAGED` mit dem Mob als Verursacher
  aus; `cameraPose` zeigt beim Schweben vor einem Enderman auf dessen Kopf. `triggerAction(drone, 1, true, op)` am Server ohne Client → Werfer feuert; Aktion 5 ohne Zubehör → `false`. Zubehör-Aktion 1 heißt im Tab „Modi“
  „Werfer feuern“, der Hinweis beim ersten Flug kommt genau einmal (Fotos `accessory_hint_{en,de}`).
- B3: Windkugel explodiert neben schwebender Drohne ohne Listener → kein Versatz (wie 0.5.2); Beispiel-Add-on setzt die
  Skala auf 1 → Versatz > 0,5 m; abgebrochenes Event → kein Versatz.
- B5: Beispiel-Regel im Tab „Welt-Regeln“ unter eigenem Abschnitt; Voreinstellung „Training“ anwenden → die Beispiel-Regel hat den mit `presetValue` gesetzten Wert; Fotos `world_rules_addon_{en,de}_{854x480,320x240}`.
- B6: Mission schreiben, Drohne aufheben und setzen → Daten gleich; Welt mit Add-on aus laden und speichern, mit Add-on an
  wieder laden → Daten unverändert.
- B7: Pilot verlässt das FPV → Beispiel startet „Halten“; Drohne bleibt 10 s innerhalb 0,5 m; Spieler läuft 20 Blöcke
  → „Folgen“ hält ≤ 5 m Abstand; Budget voll → `NO_BUDGET`. Übergabe: Pilot verlässt das FPV während „Folgen“ → Flug
  läuft ohne Höhenverlust weiter (≤ 0,3 m). Start vom Boden auf 5 m, danach `land()` → am Boden entschärft,
  `AUTONOMY_ENDED` mit `LANDED`. Fast leerer Akku → Ende mit `BATTERY`, danach Heimflug. Drohne ohne Akku → `NOT_ARMABLE`. Flug „Folgen“ durch eine Zone mit Wind und eine mit geringer Schwerkraft → die Zonen wirken wie im FPV-Flug.
- B9: Action-Cam vorn am Gimbal nimmt horizontstabil auf (Rollwinkel im Bild < 2° bei 30° Rollen der Drohne).
  Gimbal-Kopf des Beispiel-Zubehörs dreht sich in Drittperson (Foto `gimbal_head`); `fovDegrees` 40° verengt das
  FPV-Bild; `recording().photo` legt ein PNG an; `spectate` zeigt das Bild der eigenen Drohne.
- B10: Wartungsknopf des Beispiels öffnet das Kind-Fenster; `PILOT_CHANGED` kommt in beiden Richtungen.

### 6.3 Mehrspieler auf dediziertem Server

Mit dem bestehenden Beitrittstest (`docs/testing.md` §3, höchstens zwei Minecraft-Instanzen: Server + Client):
- `PROPWASH_JOINTEST_ADDON17=1`: Server-Schalter kommt an (`ADDON_SWITCH_CHANGED` im Server-Log), Geist **ohne** Recht
  → Server setzt zurück und meldet `collision.denied`, mit Recht → Durchflug angenommen; `addon_features` listet die
  Beispiel-Ids.
- Operator in beide Richtungen ohne dritte Instanz: (a) der Client ist Operator einer Drohne im autonomen Halten des
  Servers, seine Achsen drehen den Gimbal (Foto), (b) der Client fliegt, ein am Server simulierter zweiter Spieler
  (Muster `dev.ZoneJoinCheck`) schickt Operator-Eingaben, der Client loggt die Gimbal-Lage.
- Add-on-Daten mit `TRACKING` erreichen den Client, `NONE` nicht.
- Namensraum am Server: Server mit `nsprobe` → Operator bekommt die Server-Zeile genau einmal, Client ohne Probe zeigt
  keinen eigenen Hinweis.
- Client mit Beispiel-Add-on auf Server ohne: Seite „Add-ons“ zeigt „inaktiv“, Geist wirkt nicht, keine Rücksetzungen.
- Danach beide `logs/latest.log` auf Fehler prüfen (nur die erwarteten Funde der Probe).

### 6.4 Klon-Probe (Teil E)

1. `python3 tools/public_export.py --target <tmp>/export --no-build`, dann im Export mit leerem `HOME`, eigenem
   `GRADLE_USER_HOME` und minimalem `PATH`: `./gradlew :common:test` → alles grün.
2. JUnit aus einem fremden Arbeitsverzeichnis (JUnit-Konsole mit Arbeitsverzeichnis `<tmp>`, ohne Gradle) → grün.
3. Bit-Vergleich auf x86_64: dieselbe Testsuite mit einem x86_64-JDK 25 unter Rosetta 2 auf dem Mac
   (`org.gradle.java.home` auf das x86-JDK). Weichen die Golden-Hashes ab, gilt §3.18 (zweite Spalte).

### 6.5 Prüfablauf

- Jeder Strang: JUnit grün, `fastcheck`, Selbsttest der eigenen Gruppe auf Fabric **und** NeoForge, eigene
  Bildschirmfotos.
- Integration: voller Selbsttest beider Loader, Rauchtests, Beitrittstests aus §6.3, `ui-scale`-Prüfung mit den neuen
  Seiten, Kalibrierung und Golden byte-gleich.
- Mindestens drei unabhängige Prüfer je Prüfrunde mit getrennten Aufträgen: (1) API-Vertrag gegen dieses Dokument und
  SemVer, (2) Namensraum-Prüfung mit Probe, JMP und dem echten Noclip-Jar (MIT, nur lokal, nie eingecheckt),
  (3) Bildschirmfotos in EN/DE und beiden Größen gegen Theme und Texte. Fix-Runden, bis nichts Mittleres offen ist.

---

## 7. Doku und Website

- `docs/api/addon-guide.md`: neuer Abschnitt 21 „Neu in 1.7“ (Schalter und Achsen, OSD, Kollision, Werfen, Anzeige,
  Stöße, Welt-Regeln, Daten, autonomer Flug, Operator, Gimbal-Aufnahme, Wartung, Namensraum) mit Checkliste;
  Abschnitt 0 (Tabelle „Geht / Geht noch nicht“) und 16 (Stabilität) angepasst; Umstiegsbeispiel „Noclip ohne Mixins“
  (Schalter + Kollisionsregel + Warnung, rund 60 Zeilen).
- `docs/api/changes.md`: Eintrag 0.6.0 / API 1.7.0 / Protokoll.
- `docs/design/addon-api-1.6.md` §15: erledigte offene Punkte verlinken hierher.
- `docs/FOUNDATION.md` (Integrator): Verträge `long`-Schaltermaske, `CollisionFlags` im Flugweg, `NamespaceRules`,
  `ModOrigins`.
- `docs/testing.md`: Abschnitte „Namensraum-Prüfung und Probe-Mod“ und „Klon-Probe“.
- `CHANGELOG.md` (Spieler-Stil): unter Neu „Add-ons können eigene Schalter, OSD-Anzeigen und Kollisionsregeln
  mitbringen“, unter Kompatibilität „API 1.7.0; Add-ons im Namensraum propwash werden gemeldet; Noclip 0.1.x braucht
  ein Update (Umstieg über die neue API)“.
- `ADDONS.md`, README, NOTICE, Issue-Vorlage: §3.17.
- Website: Die Seiten baut der Doku-Strang der Website ([`website-jmp.md`](website-jmp.md)). Dort steht schon fest:
  Abschnitt `#rules` („Rules for add-ons“ / „Regeln für Add-ons“) unter Doku → Add-on-API, englisch aus `ADDONS.md`
  (maßgeblich, gelesen von `extract.mjs`), deutsch aus dem Richtlinien-Entwurf in `tools/docs/pages/api.mjs`; die
  Lizenz-FAQ verlinkt darauf. Dieser Entwurf liefert dafür `ADDONS.md` und als Doku-Baustein `docs/addon-api-1.7.md`
  (Kurzfassung von §3 für Add-on-Autoren, EN-Überschriften wie in `addon-guide.md`); die Referenz `docs/api/reference`
  entsteht automatisch aus dem API-Paket.

---

## 8. Risiken

| Risiko | Gegenmaßnahme |
|---|---|
| Fehlalarme der Namensraum-Prüfung bei Propwashs eigenen Anmeldungen (Vanilla-Wege, `devtest`) | Selbsttest „0 Funde“ als harte Prüfung in jedem normalen Lauf beider Loader |
| Zuordnung zum Mod scheitert (Loader-Interna, Mixin-API) | alles in `try/catch`, Ausweichtext „unbekannter Mod“, Kategorie wird übersprungen statt abzustürzen |
| Lange Ladezeit mit großen Modpacks (Sprachdateien) | Streaming nur der Schlüssel, nur `en_us` und aktive Sprache, Zeit im Log, Ziel < 50 ms |
| Kollisionsregel und Server widersprechen sich | Server wertet selbst aus; Schalterpakete vor Bewegungspaketen; Rücksetzung wie heute; Hysterese beim Zurückschalten |
| Geistflug als Abkürzung in Rennen und auf Servern | Runden mit Hilfe markiert; Regel des Add-ons entscheidet am Server (Rechte) |
| Server-Last durch autonome Add-on-Flüge | geteiltes `AutonomyBudget` (8/2) mit Vorrang des Heimflugs, Ende mit dem Akku, harte Grenze 60 Minuten, Chunk-Tickets wie im Heimflug, Besitzer muss online und in Funkreichweite sein |
| Spieler suchen den Auslöser eines Zubehörs auf dem Rechtsklick | Namen der Aktionen im Tab „Modi“, Hinweis mit aktueller Belegung beim ersten Flug, Doku des Add-ons |
| `PilotSession`, `DronePiloting`, `OsdRenderer`, `WorldRules`-Tab sind Hotspots mehrerer 0.6.0-Stränge (Freecam, Flugbahn-Marker, Freecam-Regel, Schleim/Wolle) | klare Dateieigentümer in §10, Änderungen in eigene Klassen auslagern, Hotspot-Dateien nur mit kleinen Haken anfassen, Reihenfolge der Integration |
| Parallele Arbeit an `AccessoryDefinition`, `PartJson`, `DroneRenderState` außerhalb dieser Stränge | B9/B2 ändern dort nur je eine kleine, abgegrenzte Stelle, damit sich andere Änderungen an diesen Dateien sauber zusammenführen lassen |
| Noclip 0.1.x bricht mit 0.6.0 beim Start ab | geschützte Signaturen, API-Ersatz mit Umstiegsbeispiel, mit echtem Jar prüfen, im Changelog offen nennen; der Projektinhaber schreibt dem Autor vor dem Release |
| `int`→`long` in der Schaltermaske übersieht eine Stelle | JUnit über alle Selektoren, Suche nach `activeSwitches` im Review |
| Bit-Unterschiede x86_64 | Rosetta-Lauf vor dem Release, sonst zweite Golden-Spalte |
| Ausnahmen an neuen API-Punkten brechen Add-on-Starts | nur an neuen 1.7-Punkten und dort, wo 1.6 schon warf; ältere Punkte verweigern still |
| Öffentlicher Export bricht an Zitaten oder Pfaden | Dieses Dokument enthält Vorgaben sinngemäß, keine lokalen Pfade |

---

## 9. Entscheidungen (was – warum – verworfen)

| Nr. | Entscheidung | Warum | Verworfen |
|---|---|---|---|
| E1 | „Hart ablehnen“ heißt: Anmeldung wird nicht übernommen. Neue 1.7-Punkte und schon werfende Punkte werfen, bisher tolerante Punkte verweigern ohne Ausnahme | Vorgabe 4 plus SemVer: kein Add-on, das unter 0.5.2 startete, darf in einer Nebenversion abstürzen | überall werfen (Bruch in Nebenversion); überall nur warnen (zu weich) |
| E2 | Funde aus Mod-Paketen sind Fehler mit Hinweis, Spielerpakete nur Info | Vorgabe richtet sich an Add-ons; Ressourcen- und Datenpakete sind ausdrücklich erlaubt | jede neue `propwash`-Datei melden (Fehlalarme bei Spieler-Paketen) |
| E3 | Überschreiben = Pfad, den Propwash selbst mitbringt (auch Tag-Ergänzungen) | so bleibt Just More Parts sauber und Texturpakete bleiben erlaubt | Inhalt vergleichen (teuer, ohne Nutzen) |
| E4 | Enum-Erweiterungen über Mixin-Ziele + Original-Klassendatei (Class-File-API, Java 25) | keine gepflegte Liste, neue eingebaute Konstanten anderer Stränge zählen automatisch | handgepflegte Namenslisten (Merge-Konflikte); alle Enums des Jars laden (Client-Klassen am Server) |
| E5 | Registry-Zuordnung per Mixin auf `MappedRegistry#register` nur bei Namensraum `propwash` | einzige verlässliche Zuordnung auf beiden Loadern, kostet nur bei `propwash`-Ids | Klasse des Eintrags (Vanilla-Klassen sind nicht zuordenbar) |
| E6 | Hinweis als Chat-Zeile beim ersten Weltbeitritt je Start, Details-Fenster; am Server je Operator einmal je Serverstart | Vorgabe einmal pro Start; im Titelbildschirm übersieht man Toasts; Server-Betreiber brauchen es auch | Toast im Titelbildschirm; Abschaltknopf „nicht mehr zeigen“ (Vorgabe sagt einmal pro Start) |
| E7 | Log- und Ausnahmetexte der Prüfung englisch, Hinweis im Spiel EN/DE | Leser sind Add-on-Autoren weltweit; die übrigen Logs bleiben deutsch | deutsche Logtexte wie sonst |
| E8 | Nur der exakte Namensraum `propwash` ist technisch reserviert | Vorgabe nennt genau ihn; `propwash_example` usw. sind eigene Namensräume | Präfix `propwash_` sperren |
| E9 | Zusätzlich `/propwash`-Unterbefehle und Mixins in Interna (Info) prüfen, dazu Crash-Report-Abschnitt | Befehle stehen in der Richtlinie; Mixin-Info beantwortet die Issue-Frage | nur die vier genannten Kategorien |
| E10 | Eingebaute Schaltermaske `int` → `long`, Add-on-Schalter in eigener `long`-Maske | Grenze von 32 ist mit 0.6.0 fast erreicht; Add-ons brauchen eine Grenze unabhängig von Propwash | Add-on-Schalter in dieselbe Maske; `BitSet` (unnötig teuer im Takt) |
| E11 | Add-on-Tasten: Add-on meldet `KeyMapping` selbst an und übergibt es | Schlüssel und Kategorie liegen so im eigenen Namensraum; keine Ladereihenfolge-Probleme | Propwash legt Tasten für Add-ons an (Namen im Namensraum `propwash`) |
| E12 | Analoge Add-on-Achsen gleich mit in A1 | Gimbal-Neigung am Drehregler ist Kern des Kamera-Add-ons | nur Schalter |
| E13 | OSD-Elemente sind Text im OSD-Zeichensatz, gezeichnet von Propwash | einheitliches Aussehen, Farben und Skalierung des Spielers, portabel; Grafik bleibt `HudElement` | freie Zeichenfläche je Rasterfeld |
| E14 | Auslösen von Zubehör (etwa des Dispensers) nur über Zubehör-Aktionen; der Vanilla-Rechtsklick bleibt im Flug „FPV beenden“, auch nach dem Bedarf von Mob Defense | FPV-Piloten haben die Hände an der Funke, die Aktion liegt dort auf einem Schalter oder Taster; mit Tastatur und Maus lässt sie sich frei belegen (Standard Ziffernblock 9, auch Maus-Seitentasten); der Rechtsklick beendet heute jeden Flug und bleibt dadurch einheitlich, egal welches Zubehör montiert ist | Use-Taste löst Zubehör aus (`useKeyAction`, Ausstieg dann je Zubehör anders); Schleichen + Rechtsklick als neuer Ausstieg |
| E15 | Werfen (`dispense`) gehört in Propwash statt ins Add-on | Vanilla-Interna je Minecraft-Version, und der Backport baut den Dispenser fest ein | Add-on baut es selbst |
| E16 | Explosions- und Windstöße nur über das Event: Standard-Skala 0, ein Add-on fordert den Stoß an; der Rückstoß beim eigenen Abschuss (B1) bleibt | nicht beauftragt; auf Renn-Servern ein Griefing-Weg; Mob Defense bekommt trotzdem alles, was es braucht | Stoß als Kernverhalten ohne Welt-Regel; neue Welt-Regel nur dafür |
| E17 | Kollisionsregeln: UND über alle Regeln, Server wertet selbst aus | jede Regel darf Kollision abschalten, Autorität bleibt am Server, dedizierte Server funktionieren | Client meldet Geist an den Server (Cheat-Weg) |
| E18 | Zurückschalten erst, wenn die Hülle frei ist | kein Teleport, kein Festsitzen | Herausschieben zur nächsten freien Seite |
| E19 | Runden mit abgeschalteter Kollision zählen als Runde mit Hilfe | gleiches Prinzip wie Flughilfen | Runden ungültig machen |
| E20 | Oberflächen (Schleim, Wolle, Honig) kommen aus dem Strang `surface` (Block-Tags, `SurfaceResponse`); Add-on-Kontaktregeln hängen dahinter und ändern dessen Antwort | Tags sind per Datenpaket erweiterbar und schon entworfen; keine doppelte Logik | eigene eingebaute Regel `propwash:surfaces` in A3 |
| E21 | Autonomer Flug nutzt den Server-Flug des Heimflugs und dessen Anzahl-Budget mit Vorrang für Heimflüge; ein Add-on-Flug endet mit dem Akku (harte Grenze 60 Minuten, kürzer per `maxDuration`), der Heimflug behält seine 10 Minuten | Code und Grenzen gibt es schon (8 je Server, 2 je Spieler); eine Kamera-Drohne soll schweben, solange der Akku reicht, wie in echt; der Akku begrenzt Last und Chunk-Tickets ohnehin, die harte Grenze fängt Akkus aus Datenpaketen ab; mit dem Vorrang bleibt immer ein Platz für den Heimflug | eigener Simulator; Welt-Regel von Propwash für Budget oder Dauer; feste 10 Minuten für alle Flüge |
| E22 | Operator-Eingaben über den Server an alle Zuschauer | alle sehen dieselbe Gimbal-Lage | Operator-Client rechnet die Lage und schickt Quaternionen (Zuschauer ohne Eingaben) |
| E23 | Add-on-Daten mit Größengrenzen und Erhalt unbekannter Daten | Speicherstände dürfen beim Entfernen eines Add-ons nichts verlieren | Daten ohne Add-on verwerfen |
| E24 | Add-on-Welt-Regeln im Tab „Welt-Regeln“ statt eigener Tabs | Grundsatz: Welt-Regeln haben eine GUI mit Operator-Rechten | eigener Konfigurator-Tab je Add-on (Entwurf Mob Defense §2) |
| E25 | Ein Vertrags-Strang legt alle API-Typen zuerst an | `PropwashApi`, `PropwashEvents`, `DroneAccess` würden sonst in jedem Strang kollidieren | jeder Strang ergänzt die Fassade selbst |
| E26 | Probe-Mod als eigenes Quell-Set mit eigener Mod-Id nur in Dev-Runs | echte Fremd-Zuordnung auf beiden Loadern, ohne Release-Risiko | Verstöße im Beispiel-Add-on (gehört zu Propwash, falsche Zuordnung); echtes Noclip-Jar als einzige Probe (bricht mit 0.6.0) |
| E27 | `ADDONS.md` übernimmt den Entwurfstext wörtlich, nur Links ergänzt; *Propwash: Server* fehlt in der Liste der offiziellen Add-ons | der Text ist abgestimmt; die Server-Mod ist zurückgestellt | Text neu formulieren; zurückgestelltes Add-on nennen |
| E28 | Issue-Vorlage als GitHub-Formular mit Pflichtfeld Add-ons, leere Issues bleiben erlaubt | strukturiert, Ideen weiter möglich | Markdown-Vorlage; leere Issues sperren |
| E29 | Tests: Pfade über Systemeigenschaften aus Gradle, Golden über den Klassenpfad, Login-Shell textbasiert | unabhängig von Arbeitsverzeichnis und Betriebssystem | Tests unter Windows überspringen; Arbeitsverzeichnis erzwingen |
| E30 | Bei x86-Abweichung zweite Golden-Spalte statt `StrictMath` | `StrictMath` verändert das Flugbild und alle Goldens | Toleranzvergleich (verliert die Bitgleichheit) |
| E31 | Vorgaben sinngemäß statt wörtlich zitiert | `docs/design` wird öffentlich exportiert; Konventionen und Export-Prüfung verbieten wörtliche Zitate | wörtliche Zitate |
| E32 | Kein VR-Kamera-Haken | Vorgabe 11 | kleiner Haken „auf Vorrat“ |
| E33 | Reservierte `/propwash`-Unterbefehle offizieller Add-ons sind für die Prüfung erlaubt (feste Liste, nur bei geladenem Add-on); in 0.6.0 ist die Liste leer | eine spätere Server-Mod soll sich unter `/propwash` hängen können; Brigadier-Knoten lassen sich keinem Mod zuordnen | Befehlsprüfung ganz streichen; Server-Mod mit eigener Wurzel |
| E34 | Keine Unverträglichkeit für Just More Parts in den Metadaten; JMP 1.1.x läuft im Altmodus | ein Update nur von Propwash darf das Spiel nicht unstartbar machen; der `guard:`-Lauf mit JMP 1.1.1 bleibt gültig | `breaks`/`incompatible` für JMP < 1.2.0 |
| E35 | Namen der Zubehör-Aktionen (`actionNameKey`) und ein Hinweis mit der aktuellen Belegung beim ersten Flug, gezeigt von Propwash | ersetzt die Use-Taste als Weg, den Auslöser zu finden; nur Propwash kennt die Belegung an Funke und Tastatur | nur Nummern im Tab „Modi“; Hinweis durch das Add-on |
| E36 | Werfen prüft die Bau-Rechte des Piloten (`DENIED`), kann ohne Verbrauch werfen (`consume`), trifft die eigene Drohne nicht und gibt TNT und Items das Tempo der Drohne | Schutz gegen Griefing auf Servern an einer Stelle; Regel „echte Munition“ des Add-ons; die Mündung liegt dicht an der Hülle; ein Abwurf im Flug soll sich wie ein Abwurf anfühlen | Rechteprüfung in jedem Add-on einzeln |
| E37 | Übergabe beim FPV-Ende, Start vom Boden, Landung und Ende-Ereignis im autonomen Flug | eine Mission läuft weiter, wenn der Pilot die Brille abnimmt; Missionen beginnen und enden am Boden; Add-ons kennen den Zustand statt ihn zu raten | `start()` erst nach dem Start des Heimflugs (die Drohne sackt ab); Landung über Knüppel-Ausgaben im Add-on |
| E38 | Gimbal-Kopf, Brennweite, Aufnahme und Livebild für Add-ons; Daten für andere Clients gleicht das Add-on über B6 ab | eine Kamera-Drohne lebt vom sichtbaren Gimbal und von Aufnahmen; kein neues Netzpaket in Propwash | Propwash verteilt die Gimbal-Lage jeder Drohne selbst (Netzlast für alle Drohnen) |
| E39 | Nicht in 1.7: Lautstärke am Server, Flugmodell-Prüfstand, Rahmen-Rolle `CAMERA`, autonomer Flug außerhalb der Funkreichweite | das Klangmodell läuft nur auf dem Client; der Prüfstand machte das interne Flugmodell zur API; eine neue Rolle braucht abgestimmte Werte im Energie- und Kamera-Modell und bricht vollständige `switch`-Anweisungen in Add-ons; Funkverlust bleibt ein Sicherheitsgrund | alles in 1.7 |
| E40 | Add-on-Regeln in eingebauten Voreinstellungen (`presetValue`) | eine Voreinstellung soll die ganze Welt einstellen, auch mit Add-ons | Voreinstellungen nur für Propwash-Regeln |

---

## 10. Arbeitspakete

Alle Stränge im Repo `propwash`; die Website-Seiten baut der Doku-Strang aus `website-jmp.md`. Branches vom aktuellen
Integrationsstand (Übersicht `README.md` §4.1). Minecraft-Minuten = geschätzte Selbsttest- und Rauchtestzeit des
Strangs; Läufe nur über das Slot-Skript.

### Teil 1 – Grundlagen (parallel)

| Strang | Branch | besitzt | Abhängigkeit | MC-Min. | Größe |
|---|---|---|---|---|---|
| `api17-contract` | `feat/0.6.0-api17-contract` | alle **neuen** Dateien unter `api/` (`api/input/**`, `api/client/osd/**`, neue Typen in `api/drone`, `api/accessory`, `api/event`, `api/worldrule/**`, `api/client/ServiceExtension`), Änderungen an `PropwashApi`, `PropwashClientApi`, `PropwashEvents`, `DroneAccess`, `ClientInput`, `FlightState`, `GimbalContext`, `GimbalProvider`, `AccessoryContext`, `AccessoryBehavior`, `AccessoryRegistry`, `AssistOutput`; alle Signaturen aus §3.19 (`CameraPose`, `DispenseResult.Status.DENIED`, `DispenseOptions#consume`, `AutonomyOptions`, `AutonomyRequest`, `ExitHandler`, `AutonomyEnd`, `AutonomyEnded`, `RecordingControl`, `presetValue`, `actionNameKey`, `modelPose`, `fovDegrees`, `spectate`); `apiimpl/NamespaceRules.java`; die Ein-Zeilen-Umstellungen der Namensraum-Prüfung in `AddonHud`, `AddonCamera`, `FlightHooks`, `AccessoryBehaviors`, `PartIndex`, `ConfigTabs`, `WorkbenchExtensions`, `ExtensionTabs`, `LootInjectionsImpl`, `ApiExtensions` (Presets), Flugschul-Szenen (`PropwashClientApi`); Ablage-Registries ohne Verhalten (`apiimpl/Api17Registries.java`); `ApiContractTest`, `NamespaceRulesTest`; `API_VERSION` 1.7.0 | – | 10 | M |
| `ns-guard` | `feat/0.6.0-ns-guard` | `guard/**`, `guard/client/**`, `mixin/guard/**`, `propwash.guard.mixins.json` (Eintrag in beiden Loader-Metadaten als Foundation-Wunsch), `platform/services/ModOrigins` + Fabric-/NeoForge-Implementierung + Service-Dateien, Crash-Report-Abschnitt, `common/src/lang/guard/*`, Quell-Set `common/src/nsprobe/**` und dessen Dev-Run-Verdrahtung in `fabric/build.gradle` und `neoforge/build.gradle` (nur Run-Blöcke), Selbsttest-Gruppe `guard:` | nutzt `NamespaceRules` (Signatur hier festgelegt; Merge nach `api17-contract`) | 35 | M–L |
| `test-portability` | `fix/0.6.0-test-portability` | `common/src/test/java/**/testutil/TestPaths.java` (neu), Pfadsuche in `GoldenFile`, `AirframeGoldenTest`, `DischargeCurveTest`, `SaveFormatGoldenTest`, `FlashbackSplineTest`, `PartJsonTest`, `guide`-Tests, `FfmpegDetectionTest`; `video/record/external/LoginShell.java` (`parse`); Test-Task in `common/build.gradle` (nur `systemProperty`-Zeilen); Klon-Probe und Rosetta-Lauf | – | 0 | S |
| `addon-rules-docs` | `docs/0.6.0-addon-rules-docs` | `ADDONS.md`, Absatz in `README.md` (nur Abschnitt „Add-ons (Parts API)“), `NOTICE` (ein Absatz), `LICENSE-MIT` (eine Pfadzeile), `tools/public_export.json` (`required`), `.github/ISSUE_TEMPLATE/bug_report.yml`, `.github/ISSUE_TEMPLATE/config.yml` | – | 0 | S |

### Teil 2 – Verhalten (nach dem Merge von `api17-contract`, parallel)

| Strang | Branch | besitzt | Abhängigkeit | MC-Min. | Größe |
|---|---|---|---|---|---|
| `addon-switches` (A1) | `feat/0.6.0-addon-switches` | `input/addon/**`, `input/net/AddonSwitchC2S`, `AddonAxisC2S`, `AddonFeaturesS2C`, `long`-Maske in `RadioFrame`, `RadioState`, `SwitchEvaluator`, `FlightModeSelector`, `Mode3dSelector`, `SwitchCommandRunner`, Aufrufe in `PilotSession` (nur Masken-Typ), `DeviceProfile` (Schlüssel erhalten, `addonAxes`) – Maskentyp, `PilotSession` und `DeviceProfile`-Umstieg übernimmt vorab der Strang `pilot-prep` (Übersicht K10), hier bleiben die Add-on-Teile –, Seite „Add-ons“ in `ModesTab` (neue Klasse `input/client/screen/AddonSwitchRows`), Namen der Zubehör-Aktionen und Hinweis beim ersten Flug (`input/addon/AccessoryActionHints`, Zeilen der Zubehör-Aktionen im Tab „Modi“), `common/src/lang/input/*` (neue Schlüssel), Beispiel `dev/example/ExampleSwitches.java` | `api17-contract` | 25 | M |
| `addon-osd` (A2) | `feat/0.6.0-addon-osd` | `osd/addon/**`, Haken in `OsdRenderer` (eine Methode) und `OsdConfig` (Normalisieren), Gruppe in `OsdConfigTab`, `common/src/lang/osd/*`, `dev/example/ExampleOsd.java` | `api17-contract`; Absprache mit Strang Flugbahn-Marker (`OsdElement`/`OsdRenderer`) | 20 | M |
| `addon-collision` (A3) | `feat/0.6.0-addon-collision` | `drone/collision/**`, Haken in `PilotSession#moveEntity`/`fluidAt` und hinter `SurfaceMaterials.at` in `fillSurface`, `DronePiloting#onState`, `FpvEyeGuard`, Kamera-Mixin der Drohne, neue Abfrage `drone/client/CameraClip` (Anmeldung weiterer Quellen, erste ist die Freecam), `rescue/ServerReturnFlight` und `VoxelFlight` (Kontakt-Haken hinter dem Aufruf des Strangs `surface`), Feld `ghost` in `sim/MoveReport`, Runden-Grund in `race` (eine Stelle), `dev/example/ExampleGhost.java` | `api17-contract`, Strang `surface` und danach `heat` Phase A sowie Phase B, falls gebaut (`gameplay.md`; dieselben Dateien `PilotSession`, `DroneSim`, `MoveReport`), `addon-switches` (Server-Schalter) für den Server-Test; `freecam` mergt danach und meldet sich nur bei `CameraClip` an (Übersicht K20) | 30 | M–L |
| `accessory-gaps` (B1–B4) | `feat/0.6.0-accessory-gaps` | `accessory/dispense/**`, `accessory/display/**`, Render-Übernahme in `drone/client/render` (eigene Klasse, eine Zeile im Render-Zustand), `drone/Knockback.java` + Haken in `QuadcopterEntity`, `DroneQueries#loaded`, Rechteprüfung, `consume`, Schutz vor eigenen Treffern und Tempo von TNT und Items in `accessory/dispense/**`, `triggerAction` (`apiimpl`), `drone/CameraPoses.java`, Bestätigung der Mob-Treffer im Selbsttest, Platz für die Gimbal-Lage im Anzeige-Zustand von B2 (für `operator-gimbal`), `dev/example/ExampleDispenser.java` | `api17-contract`, `drone-data-service` (gleiche Entity-Datei); im Render-Zustand nur eine Zeile | 40 | M |
| `addon-world-rules` (B5) | `feat/0.6.0-addon-world-rules` | `server/AddonWorldRules.java`, Abschnitt je Add-on im Tab „Welt-Regeln“ (neue Klasse im Konfigurator, ein Aufruf im Tab), Werte der Add-on-Regeln in Voreinstellungen (eine Stelle in `server/rules/RulePreset`), `common/src/lang/server/*`, `dev/example/ExampleRules.java` | `api17-contract`; Absprache mit Strängen, die eigene Welt-Regeln ergänzen (Freecam) | 15 | M |
| `drone-data-service` (B6, B10) | `feat/0.6.0-drone-data-service` | `drone/data/**` (Serializer, Komponente, NBT; das Feuern von `PILOT_CHANGED` liegt schon in `api17-contract`, Übersicht K11), `apiimpl/client/ServiceExtensions.java` + Knopfleiste in `DroneServiceScreen`, `dev/example/ExampleMission.java` | `api17-contract` | 20 | M |
| `addon-autonomy` (B7) | `feat/0.6.0-addon-autonomy` | `rescue/ServerFlight*` (Verallgemeinerung von `ServerReturnFlight`), `rescue/AddonAutonomy.java`, `AutonomyStatusS2C`, OSD-Label über den bestehenden Weg, Vorrang des Heimflugs in `rescue/AutonomyBudget`, Dauer bis zum Akku und `maxDuration`, `takeoff` und `land`, `AUTONOMY_ENDED`, Übergabe beim FPV-Ende (ein Haken in `AutonomousReturns#onPilotChanged`), `dev/example/ExampleFollow.java` | `api17-contract`, `addon-collision` (Kontakt-Haken im Server-Flug) | 35 | M–L |
| `operator-gimbal` (B8, B9) | `feat/0.6.0-operator-gimbal` | `drone/operator/**`, Operator-Rolle in `SpectateSession` (eigene Klasse + Haken), `video/record/CameraLook.java`, Flashback-Kamerapfad (eine Stelle), `AccessoryDefinition` (Prüfung `action_cam` auch `front`), Gimbal-Kopf (`drone/operator/client/GimbalHead`, Lage über den Anzeige-Zustand von B2, kein eigenes Feld im Render-Zustand), Brennweite (`drone/operator/client/GimbalFov`, eine Stelle am FPV-Sichtfeld), `apiimpl/client/RecordingControlImpl`, `spectate` (Einstieg in die bestehende Zuschauer-Sitzung), `dev/example/ExampleOperator.java` | `api17-contract`, `addon-autonomy` (Test a in §6.3), `accessory-gaps` (Anzeige-Zustand); in `AccessoryDefinition` nur die eine Prüfung | 40 | L |

Ein Strang für Server-Haken (`core-server-hooks`) entfällt mit der Zurückstellung der Server-Mod; `API_VERSION` 1.7.0
setzt nur `api17-contract`.

Gemeinsame Regel für Teil 2: `dev/example/ExampleAddon.java` bekommt je Strang genau eine Zeile (Anmeldung der eigenen
Beispiel-Klasse); Konflikte dort sind trivial. Sprachdateien nur in den Modul-Quellen, nie die erzeugten Dateien.

### Teil 3 – Handbuch und Integration

| Strang | Branch | besitzt | Abhängigkeit | MC-Min. | Größe |
|---|---|---|---|---|---|
| `api17-docs` | `docs/0.6.0-api17-docs` | Doku-Baustein `docs/addon-api-1.7.md` (für den Website-Strang), `docs/api/addon-guide.md` §0, §16, §21 (mit Umstiegsbeispiel „Noclip ohne Mixins“), `docs/api/changes.md` (Teil API 1.7), `docs/testing.md` (zwei Abschnitte), Verweise in `docs/design/addon-api-1.6.md` §15 | alle Stränge aus Teil 2 | 0 | M |
| Integration und Prüfung | Integrations-Worktree (unter der gemeinsamen Sperre) | `FOUNDATION.md`-Verträge, `CHANGELOG.md`, Beitrittstests §6.3, Klon-Probe §6.4, Prüfer-Runden §6.5 | alles | 60 | M |

Summe Minecraft-Zeit der Stränge: etwa 4,5 Stunden, dazu etwa 1 Stunde Integration. Größter Einzelposten ist die
Namensraum-Prüfung mit Probe auf beiden Loadern.
