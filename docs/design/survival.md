# Überlebensmodus: Rezepte, Zwischenteile, Truhen-Beute

Ziel: Propwash ist ohne Kreativmodus spielbar. Jedes Propwash-Item außer der Kreativ-Brille, dem Kreativ-Flugcontroller (`creative_fc`, `docs/design/creative-drone.md`) und der Bando-Karte (Kartograf und Truhen, mit einer leeren Karte kopierbar; `docs/design/bandos.md` „Bando-Karte“) lässt sich herstellen, und
Strukturen enthalten passende Bauteile als besondere Funde. Alle Zahlen stehen in `docs/design/survival-tables.md`
(erzeugt), die einzige Quelle ist `tools/data/survival.py`.

## Zwischenteile

| Id | Name | Rezept (Ergebnis) | wofür |
|---|---|---|---|
| `carbon_plate` | Carbonplatte | 4 Kohle/Holzkohle + 2 Faden (2) | Rahmen ab Cinewhoop, 7-Zoll-Props |
| `copper_coil` | Kupferspule | 4 Kupferbarren + 1 Eisennugget (2) | alle Motoren, analoge Race-Kamera |
| `circuit_board` | Platine | 2 Kupfernuggets + 1 Redstone + 3 Glasscheiben (2) | Stacks, analoges Video, Brillen, Funke, Ladegerät, HD-Videochip |
| `lipo_cell` | LiPo-Zelle | 2 getrockneter Seetang + 1 Kupfernugget + 2 Redstone + 1 Lapis (2) | alle Akkus (Zellenzahl = Zahl vor dem S) |
| `hd_chip` | HD-Videochip | 2 Quarz + 1 Amethyst + 2 Goldnuggets + 1 Platine (1) | digitales Video, digitale Brille |

Die Zwischenteile machen Rezepte lesbar („Motor = Spulen + Eisen“) und verteilen die Kosten: Die Zahl der Spulen,
Carbonplatten und Zellen wächst mit der Größe des Bauteils.

## Stufen

- **Einstieg (Kupfer, Eisen, Redstone, dazu Seetang, Kohle, Faden, Lapis, Glas, Leder):** Drohnen-Werkbank, Ladegerät,
  Funke, analoge Brille, der ganze Whoop (Rahmen, AIO, 0802-Motoren, 31-mm-Props, analoge Nano-Kamera, 1S-Akku), Cinewhoop-
  Rahmen, 1404-Motoren, 3-Zoll-Props, analoge Race-Kamera, 4S-Akku, 5-Zoll-Freestyle-Rahmen, 2207-Motoren, 5,1-Zoll-Props,
  kleines Gate, Kurs-Werkzeug und der LED-Streifen (drei Redstone-Fackeln, zwei Kupfernuggets, Platine). Geprüft von `SurvivalDataTest#entryGearNeedsOnlyEarlyGameMaterials` über den ganzen
  Rezeptbaum.
- **Mittelstufe (+ Gold):** 30×30-Stack, 6S-1100-Akku, Race-Rahmen (Goldnuggets), 7-Zoll-Rahmen, 2807-Motoren, 7-Zoll-Props.
- **Oberstufe (+ Quarz, Amethyst, Diamant, Glowstone, Echo-Splitter):** HD-Videochip (Nether-Quarz + Amethyst), digitales Video
  Lite, digitale Brille (Chip + getöntes Glas + Gold), 2306,5-Race-Motoren (Nether-Quarz als Keramiklager), 6S-1300-Hochstromakku
  (Glowstone), 6S-Li-Ion-4000 (Amethyst + Diamant), das digitale HD-System (HD-Chip + Echo-Splitter aus antiken Städten) und die
  Action-Cams: Light (HD-Chip, Kupfer, Glas, Platine – hängt am Flugakku) und Heavy (HD-Chip, getöntes Glas, zwei LiPo-Zellen als eigener
  Akku, Eisengehäuse).
  Geprüft von `SurvivalDataTest#highEndPartsNeedRareMaterials`.

Bewusst entschieden: Der 1S-LiHV-Akku bleibt im Einstieg (eine LiPo-Zelle + Redstone + Kupfernugget), obwohl „HV“ nach
Oberstufe klingt – er ist der einzige Akku, der in den Whoop passt, und der Whoop muss früh baubar sein. Die teuren
Akkus sind die 6S-Packs.

## Rezeptbuch

Jedes Rezept hat einen Fortschritt unter `advancement/recipes/<ordner>/` (Ordner nach Reiter: `misc`, `tools`,
`building_blocks`), dessen Kriterien alle mit ODER verknüpft sind:

- **jede Zutat** schaltet frei, wie in Vanilla (Kupferbarren zeigt sofort Kupferspule, AIO 20 A, Stack, Ladegerät,
  Action-Cam Light …);
- **jedes Propwash-Item** (`has_propwash_item`, Liste aller Items aus `assets/propwash/items`) schaltet alle Propwash-
  Rezepte auf einmal frei. Wer die Drohnen-Werkbank herstellt oder findet, ein Teil aus einer Truhe holt oder ein
  Zwischenteil baut, sieht also sofort das ganze Sortiment – auch Teile, deren Rohstoffe er noch nicht hat. Weil das
  Kriterium in jedem Rezept-Fortschritt steckt, gilt es auch für Rezepte späterer Versionen.

Reiter im Rezeptbuch der Werkbank: Zwischenteile, Drohnen-Werkbank und Ladegerät unter „Verschiedenes“, alle
Drohnen-Bauteile, Brillen, Funke und Kurs-Werkzeug unter „Ausrüstung“ (gruppiert nach Rahmen, Motoren, Props, Akkus,
Video, Stacks, Zubehör), Gates, Kurs-Elemente und Signal-Repeater unter „Baumaterial“.

Suche: „propwash“ (ab vier Buchstaben, z. B. „prop“) findet alle Propwash-Rezepte, auch ohne Doppelpunkt
(`mixin.ui.client.FullTextSearchTreeMixin` + `ui.client.ModSearch`, gilt auch für die Kreativ-Suche); Item-Namen und
`propwash:` funktionieren wie in Vanilla.

Befund beim Nachstellen (Spielstand des Besitzers): Fortschritte und Rezepte luden fehlerfrei, die Freischaltung
funktionierte – aber nur für wenige Rezepte. Vorher schalteten Bauteile nur über ihre Zwischenteile frei, die Werkbank
nur den Whoop-Einstieg. Dazu verwies die Flugschule auf das Rezeptbuch **im Inventar**, das wie in Vanilla nur Rezepte
zeigt, die ins 2×2-Raster passen (dort blieben ein bis drei Propwash-Rezepte übrig), und die Suche „propwash“ ohne
Doppelpunkt fand nichts, weil Vanilla ohne `:` nur Namen und Tooltips durchsucht. Die Flugschule zeigt jetzt das
Rezeptbuch an der Werkbank.

## Truhen-Beute

Pro Truhe wird genau einmal aus einer Propwash-Tabelle gewürfelt; ein großer Leer-Eintrag sorgt dafür, dass ein Fund etwas
Besonderes bleibt (15 % in Dorfhäusern bis 55 % in antiken Städten). Gefundene Akkus und Drohnen haben Lagerladung (40 %),
wie echte LiPos im Regal – erst ans Ladegerät.

| Tabelle | Truhen | Inhalt |
|---|---|---|
| `village_house` | Häuser in Ebene, Wüste, Savanne, Schnee, Taiga | Whoop-Props, Spulen, Platinen, Zellen, Carbon, selten 0802-Motoren |
| `village_toolsmith` | Werkzeugschmied | Zwischenteile, Whoop-Teile, 1404-Motoren, LED-Streifen, selten ein Ladegerät |
| `village_cartographer` | Kartograf | Kameras und Platinen, Action-Cam Light, selten eine analoge Brille |
| `abandoned_mineshaft` | Minen | Spulen, Zellen, Platinen, Motoren, 4S-Akku, Whoop-Rahmen, LED-Streifen |
| `simple_dungeon` | Verliese | Zwischenteile, Akkus, Race-Kamera, 2207-Motoren, Brille, Funke, LED-Streifen, **fertige Whoop-Drohne** (2 %) |
| `temple` | Wüsten- und Dschungeltempel | Carbon, Platinen, Stacks, 2207-Motoren, 5-Zoll-Props, 6S-1100, selten HD-Chip/Digital Lite/Action-Cam Light |
| `pillager_outpost` | Plünderer-Außenposten | Freestyle-Rahmen, Props, Motoren, Funke, Brille, Action-Cam Light, **Whoop-Drohne** (3 %) |
| `stronghold` | Festung (Gang, Kreuzung) | Oberstufe: Stack, 6S-Akkus, HD-Chip, Digital Lite, Race-/7-Zoll-Rahmen, Race-Motoren, Action-Cam Heavy |
| `shipwreck_supply` | Schiffswrack (Vorräte) | Zellen, Props, Carbon, 4S-Akku, Cinewhoop-Rahmen |
| `trial_chambers` | Prüfungskammer (Vorrat, Kreuzung) | Kupferspulen (Kupfer-Thema), Platinen, Zellen, Motoren, AIO, Race-Kamera, LED-Streifen |
| `vault` | Tresor | HD-Chip, Digital Lite, Race-Motoren, Stack, 6S-1300, Action-Cam Heavy, **Whoop-Drohne** (2 %) |
| `vault_ominous` | Unheilvoller Tresor | Digital HD, Race-Motoren, 6S-Akkus, Race-Rahmen, digitale Brille, Action-Cam Heavy, **Whoop-Drohne** (6 %) |
| `ancient_city` | Antike Stadt | seltenste Oberstufe: Digital HD, Race-Motoren, 6S-1300, Li-Ion 4000, digitale Brille, Action-Cam Heavy |
| `end_city` | Endstadt | wie antike Stadt, dazu 7-Zoll-Rahmen und 2807-Motoren |

Oberstufen-Teile (Digital HD, Race-Motoren, 6S-1300, Li-Ion, digitale Brille, Race-Rahmen, Action-Cam Heavy) liegen nur in Festung, Tresoren,
antiker Stadt und Endstadt (`SurvivalDataTest#injectedLootIsRareAndWellFormed`).

## Technik

- Fabric: `LootTableEvents.MODIFY` hängt an jede Zieltabelle einen Pool mit einem Verweis auf die Propwash-Tabelle
  (`fabric.FabricChestLoot`), unabhängig davon, ob die Zieltabelle aus Vanilla, einer Mod oder einem Datenpaket stammt.
- NeoForge: je Zieltabelle ein Global Loot Modifier `neoforge:add_table` unter
  `neoforge/src/main/resources/data/propwash/loot_modifiers/chests/…`, Bedingung `neoforge:loot_table_id`. In 26.3 lädt
  NeoForge jede Datei unter `data/<ns>/loot_modifiers/` direkt (kein `global_loot_modifiers.json` mehr).
- Beide Wege würfeln die Propwash-Tabelle mit dem Zufall der Truhe; Tresore zeigen Propwash-Funde auch in ihrer Vorschau.
- Selbsttest `content.client.SurvivalSelfTests` würfelt jede Tabelle und jede Zieltruhe mit festen Seeds (siehe
  `docs/FOUNDATION.md` §15 „Überlebensmodus“).

## Verschleiß und Recycling

Props sind billig (4 Stück aus getrocknetem Seetang und einem Eisennugget bzw. einer Carbonplatte beim 7-Zoll-Prop),
weil sie nach Abstürzen regelmäßig getauscht werden (Schleichen + Rechtsklick mit Props auf die Drohne). Kaputte Teile
(Zustand 0) wandern ins Zerlegen-Feld der Werkbank und kommen als Zwischenteil zurück: Rahmen → Carbonplatte (nicht
der Whoop-Rahmen aus Seetang), Motor → Kupferspule, Stack und Videoeinheit → Platine, Akku → LiPo-Zelle. Props sind
nicht recycelbar. Das Rezept bleibt unverändert; die Regeln stehen in `content.Recycling`.

### Props reparieren (ab 0.5.0)

Beschädigte Props lassen sich wie Vanilla-Werkzeuge zusammenlegen:

- Zwei Props **derselben Sorte** (gleiches Item, gleiches `propwash:part`), beide unter 100 % (auch kaputte mit 0 %),
  ergeben einen Prop mit Zustand `a + b + 5 %`, höchstens 100 % (`build.PropRepair`, reines Java, in Schritten von
  1/1000 wie `PartWear`). Zwei kaputte Props ergeben also einen mit 5 %.
- **Crafting:** Sonderrezept `propwash:special/prop_repair` (Typ `propwash:prop_repair`, `content.PropRepairRecipe`),
  genau zwei belegte Felder im 2×2- oder 3×3-Raster, Lage egal. Stapel sind erlaubt, jedes Craften nimmt aus beiden
  Feldern einen Prop. Als Sonderrezept steht es nicht im Rezeptbuch und braucht keine Freischaltung; die Datei erzeugt
  `tools/data/survival.py` (Abschnitt „Sonderrezepte“ in `survival-tables.md`). Ein Datenpaket kann es mit einer eigenen
  Datei gleichen Namens ersetzen oder abschalten.
- **Schleifstein:** `mixin.content.GrindstoneMenuMixin` lässt beschädigte Props in die beiden Eingabeplätze (je Platz
  höchstens einer, neue Props nicht) und rechnet das Ergebnis mit derselben Regel; keine Erfahrungspunkte, keine
  Reparaturkosten. Liegt ein Prop im Schleifstein, entscheidet nur die Prop-Regel (sonst würden zwei gleiche Props
  von Vanilla als Stapel zusammengelegt).
- **Daten:** Das Ergebnis ist eine Kopie des **ersten** Props (Lesereihenfolge im Raster bzw. oberer Platz am
  Schleifstein) mit allen seinen Komponenten (Name, ggf. Lack) und neuem `propwash:part_condition`; ab 100 % fällt der
  Zustand weg und der Prop stapelt wieder mit neuen. Symbol, Haltbarkeitsbalken und Schadensstufe folgen dem Zustand.
- **Addons:** Props von Addons laufen über dieselben `PartItem`s und `propwash:part`, sie lassen sich ohne eigene API
  ebenso reparieren; Props verschiedener Sorten (auch eingebaut mit Addon) nie.
- **Hinweise im Spiel:** Shift-Tooltip aller Props („Zwei beschädigte Props zusammen craften: repariert (+5 %), auch im
  Schleifstein“), kaputte Props mit einer eigenen Statuszeile, das Zerlegen-Feld der Werkbank verweist Props aufs
  Reparieren, die Flugschul-Lektion „Props wechseln und Schäden“ zeigt es im Schritt „Alte Props reparieren“.
