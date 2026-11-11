# OSD-Elemente und Warnungen von Add-ons

Für Spieler und Add-on-Autoren. Ab API 1.7 können Add-ons eigene Textanzeigen ins OSD bringen (etwa „AMMO 12“ für die
Munition eines Werfers) und eigene Warnungen ins Warnfeld schreiben (etwa „GHOST“, solange ein Geistmodus an ist).
Gezeichnet wird alles von Propwash, in der OSD-Schrift und mit den OSD-Einstellungen des Spielers.

Technik und Entscheidungen: [`design/0.6.0/addon-api-namespace.md`](design/0.6.0/addon-api-namespace.md) §3.2 (A2).

## 1. Im OSD-Editor

- Konfigurator öffnen (`K`), Tab **OSD**. Unter den eingebauten Elementen steht die Gruppe **Add-ons** mit allen
  Elementen der installierten Add-ons. Ohne Add-on mit OSD-Elementen fehlt die Gruppe.
- Jedes Add-on-Element verhält sich wie ein eingebautes: Haken an oder aus (Klick auf das Kästchen, Doppelklick oder
  Leertaste), in der Vorschau ziehen oder mit den Pfeiltasten feldweise verschieben, eigene Farbe und Größe. Wer ein
  Element in der Vorschau anklickt, sieht es auch in der Liste (die Liste rollt dorthin).
- Den Standardplatz und ob das Element anfangs an ist, bestimmt das Add-on; meist ist es aus. **Zurücksetzen** stellt
  auch für Add-on-Elemente den Standardplatz wieder her und entfernt Farbe und Größe. Eine Vorlage (Minimal, Standard,
  Rennen, Voll) ändert Add-on-Elemente nicht.
- Der Tooltip nennt das Add-on, aus dem das Element kommt. Die Vorschau zeigt einen Beispieltext des Add-ons.
- Die eingebauten Namen und „Weiter unten mehr – scrollen“ bleiben in jeder Größe lesbar, auch wenn ein Add-on-Tab mit
  langem Titel die Seitenleiste breiter macht (GUI 320×240: Seitenleiste bis 96 px, OSD-Tab 204 px). Die Liste nimmt
  sich dafür Platz von der Vorschau, bis diese 60 px breit ist. Namen von Add-on-Elementen bekommen nur so viel Platz,
  dass die Vorschau 80 px behält, und werden sonst gekürzt. Bei GUI 320×240 bleiben dem Namen mindestens 75 px, gestaucht
  passen damit Namen bis 107 px.

## 2. Im Flug

- Add-on-Elemente erscheinen im FPV und beim Zuschauen (`F7`, mit **OSD des Piloten zeigen**) wie eingebaute
  Textelemente: gleiche Schrift, Skalierung, Deckkraft, Hintergrund und Bildrauschen, im Raster von 30 × 16 Feldern im
  Bildbereich (auch bei 4:3). Während der Flugstatistik nach der Landung sind sie aus.
- Farbe je Ton: normal in der OSD- bzw. Elementfarbe, Akzent in Grün, Vorsicht in Gelb, Warnung in der Warnfarbe. Mit
  **Warnfarben** aus erscheint alles in der OSD-Farbe.
- Erlaubt sind druckbare ASCII-Zeichen; alles andere (Umlaute, Emojis, Tabulatoren) wird `?`. Zu lange Texte werden
  auf die Länge gekürzt, die das Add-on angibt (höchstens 30 Zeichen).
- Hat ein Add-on gerade nichts zu zeigen (etwa kein Werfer an der Drohne), bleibt sein Element leer.
- Im Sichtflug ohne Brille erscheinen die Add-on-Elemente ebenfalls, an ihrem Platz im selben Raster von 30 × 16
  Feldern, das dann über dem ganzen Bild liegt (ohne 4:3-Bereich), zusätzlich zum Funk-HUD oben links und darüber.
  Schrift, Farbe je Ton, Größe, Deckkraft und Hintergrund kommen aus denselben OSD-Einstellungen; Bildrauschen gibt es
  dort nicht. Die eingebauten Elemente bleiben im Sichtflug aus, ebenso das Warnfeld mit den Add-on-Warnungen.

## 3. Warnungen

- Warnungen von Add-ons erscheinen im eingebauten Feld **Warnungen**, hinter Propwashs eigenen. Sind mehrere aktiv,
  wechseln sie sich im selben Takt ab wie die eingebauten (alle 1,6 Sekunden).
- Kritische Warnungen von Propwash (Failsafe, keine Funke, Akku leer, Prop gebrochen …) haben Vorrang: Solange eine
  davon aktiv ist, wechseln nur die kritischen, Add-on-Warnungen warten.
- Add-on-Warnungen blinken nicht; ihre Farbe kommt aus dem Ton wie bei den Elementen.
- Beim Zuschauen, als Kameramann und allein am Gimbal steht oben das Banner mit Kanal, Pilot und Drohne. Es weicht dem
  Warnfeld und jedem anderen Element aus, das in seine Fläche reicht, auch Add-on-Elementen: Es nimmt die erste freie
  Zeile in der Reihenfolge 3, 2, 1, 4, 5, 0. Im Standard-Preset MINIMAL (Warnfeld in Zeile 3) steht es deshalb in
  Zeile 1, ab einer GUI-Höhe von 448 in Zeile 2; STANDARD und FULL behalten Zeile 3. Gemessen wird mit dem Layout, das
  gerade zu sehen ist (auch dem des beobachteten Piloten), und immer mit der Tastenzeile, damit das Banner nach 6
  Sekunden nicht springt.

## 4. Ohne das Add-on

Platz, Haken, Farbe und Größe eines Add-on-Elements stehen in `config/propwash/osd.json` unter dem Schlüssel des
Elements (`<namensraum>:<pfad>`, etwa `mobdefense:ammo`). Fehlt das Add-on beim nächsten Start, bleiben diese Einträge
erhalten und gelten wieder, sobald es zurück ist. Einträge mit ungültigem Schlüssel und im Namensraum `propwash` werden
beim Laden verworfen. Das Dateiformat bleibt Version 4.

Ein OSD-Layout im Sharecode (Ebene OSD) und über `PropwashClientApi.osd()` nimmt diese Einträge mit, auch die von
Add-ons, die beim Empfänger fehlen; sie gelten dort, sobald das Add-on installiert ist. Add-on-Elemente, die im
übernommenen Layout nicht vorkommen, behalten ihre Einstellungen, wie bei einer Vorlage. Passt ein Layout nicht in
8192 Zeichen, lässt der Export zuerst ausgeschaltete Add-on-Elemente weg.

## 5. Fehler eines Add-ons

Wirft ein Element oder eine Warnung eines Add-ons eine Ausnahme, schaltet Propwash es bis zum Neustart des Spiels ab
und schreibt einen Eintrag ins Log (`OSD-Element <id> eines Add-ons ist fehlgeschlagen und bleibt für diese Sitzung
aus`). Im OSD-Editor steht es dann grau in der Liste, der Tooltip nennt den Grund. Das übrige OSD läuft weiter.

## 6. Für Add-on-Autoren

```java
PropwashClientApi.registerOsdElement(OsdElementSpec.builder(Identifier.fromNamespaceAndPath("mymod", "ammo"))
        .position(1, 10).align(OsdAlign.LEFT).maxChars(8).preview("AMMO 12")
        .content(ctx -> ctx.drone().map(drone -> OsdText.of("AMMO " + rounds(drone), OsdTone.NORMAL)).orElse(null))
        .build());
PropwashClientApi.registerOsdWarning(Identifier.fromNamespaceAndPath("mymod", "ghost"),
        ctx -> ghostOn() ? OsdText.of("GHOST", OsdTone.CAUTION) : null);
```

- Nur auf dem Client anmelden, einmal beim Start; Ids im eigenen Namensraum (`propwash` wird abgelehnt), doppelte Ids
  werfen. Der Name im Editor kommt aus `nameKey` (Standard `<namensraum>.osd.<pfad>`, Punkte statt Schrägstriche) aus
  den Sprachdateien des Add-ons.
- `content` und die Warnung laufen in jedem Bild auf dem Render-Thread: kurz halten, nichts laden, nichts schicken.
  `null` heißt „nichts zeigen“.
- `OsdContext`: Drohne (leer ohne Sitzung und in der Editor-Vorschau), `FlightTelemetry`, FPV oder Sichtflug,
  Vorschau, Spielzeit, Teil-Tick. Im Sichtflug ohne Brille ruft Propwash die Elemente mit `fpv() == false` und
  `lineOfSight() == true` auf, die Telemetrie kommt dann aus dem Funk-HUD; Warnungen fragt es nur im FPV und beim
  Zuschauen ab. In der Vorschau zeigt der Editor `preview`; ohne `preview` ruft er `content` mit
  `preview() == true` auf und nimmt sonst den Namen in Großbuchstaben.
- Grafik (Balken, Symbole) gehört weiter in ein `HudElement`; OSD-Elemente sind Text im OSD-Raster.

Beispiel im Repository: `common/src/devtest/java/de/lardtom/propwash/dev/example/ExampleOsd.java` (Element
`propwash_example:ammo` „Munition“ zeigt die Pfeile, Schneebälle und Windkugeln im Werfer vorne, bei 3 oder weniger
in Gelb, leer in der Warnfarbe; Warnung `propwash_example:ghost` „GHOST“, solange der Schalter `propwash_example:ghost` aktiv ist).

## 7. Intern

| Teil | Klasse |
|---|---|
| Schlüssel, Standardplatz, Klemmen, Zeichensatz, Wechsel der Warnungen | `osd.addon.AddonOsdKeys` |
| Auswerten je Bild, Vorschau, Abschalten nach Fehlern, Rahmen für den Editor | `osd.addon.client.AddonOsdLayer` |
| Kontext für Add-ons | `osd.addon.client.AddonOsdContext` |
| Zeichnen nach den eingebauten Elementen, Warnungen im Feld | `osd.client.OsdRenderer#addonOsd`, `#warning` |
| Im Sichtflug nur die Add-on-Elemente, Raster über dem ganzen Bild, nach dem Funk-HUD | `osd.client.OsdHud#extractInSight` (Aufruf am Ende von `RadioHud#extract`), `OsdRenderer#renderAddons`, `AddonOsdLayer#collectElements` |
| Schlüssel mit Doppelpunkt beim Normalisieren behalten | `osd.OsdConfig#sanitize` |
| Schlüssel im OSD-Layout (Sharecode, `PropwashClientApi.osd()`) | `osd.OsdLayoutCodec` |
| Gruppe „Add-ons“ im Editor | `osd.client.OsdConfigTab` |
| Zeile des Zuschauer-Banners neben Warnfeld und Elementen, Add-on-Platz lesen ohne Anlegen | `video.client.SpectateBannerPlacement`, `osd.addon.AddonOsdKeys#peek` |

Ohne angemeldete Add-on-Elemente zeichnet das OSD genau wie vorher; der Wechsel der Warnungen ist dann identisch mit
`OsdWarnings#visibleIndex` (JUnit `osd.AddonOsdTest`).

## 8. Englische Kernsätze (Website)

- Add-ons can bring their own OSD readouts and warnings; Propwash draws them in the OSD font with your OSD settings.
- In the OSD tab of the configurator (`K`) they are listed in the group “Add-ons” and can be switched on, moved,
  coloured and resized like built-in elements.
- Add-on warnings appear in the built-in warnings field after Propwash's own and take turns with them; critical
  warnings always win.
- Add-on readouts also show while you fly line of sight without goggles, at their place on the whole screen next to
  the radio HUD.
- Positions of add-on elements stay in your OSD settings even while the add-on is not installed.
- OSD layouts in sharecodes take add-on elements along, even for add-ons the receiver has not installed yet.
- Only printable ASCII is shown; longer texts are cut to the length the add-on asks for.
- If an add-on element fails, it is switched off until the game restarts and the rest of the OSD keeps running.
- While you spectate or run a camera, the banner with channel, pilot and drone moves out of the way of the warnings
  field and of other readouts, so warnings like GIMBAL LIMIT stay readable.
