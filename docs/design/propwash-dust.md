# Propwash-Staub

Dezenter Staub unter den Rotoren, wenn eine Drohne dicht über dem Boden fliegt, über Wasser feine Gischt. Rein
clientseitig, für jede sichtbare Drohne (eigene und fremde) aus der synchronisierten Motorleistung. Besitzer: drone
(`drone.dust` reines Java, `drone.client.dust` Client). Minecraft-APIs: `docs/api/particles.md`.

## Ablauf pro Drohne und Client-Tick (`DustEffects.onDroneTick`, über `QuadcopterEntity.CLIENT_TICK`)

1. Abbruch, wenn der Staub aus ist, Minecrafts Partikel-Einstellung „Minimal“ ist, die Drohne nicht scharf oder
   unsichtbar ist oder weiter als 32 Blöcke von der Kamera entfernt (wie Vanilla).
2. Schub aus der Motorleistung: `motorOutput(i)` ist Drehzahl / Maximaldrehzahl, also Schub je Motor
   `kThrust · ductGain · (out · omegaMax)²` (`AirframeSpec`). Fehlen die Einzelwerte, zählt der Mittelwert `motorOutput()`.
3. Strahl: alle vier Propellerkreise als ein gleichwertiger Kreis mit Durchmesser `D = √(4 · 4 · A_prop / π)`;
   Strahlgeschwindigkeit im Nachlauf nach der Strahltheorie `u = 2 · √(T / (2 ρ A))`.
4. Reichweite: der Strahl fällt am Boden mit `u / (1 + (h / 2D)²)` ab; Staub hebt ab `3 m/s`. Daraus
   `reach = 2D · √(u / 3 − 1)`, begrenzt auf **0,3 … 2 Blöcke**. Ein Whoop kommt so auf 0,3, ein 5-Zoll im Schwebeflug
   auf ~0,9, mit Vollgas auf 2, ein 7-Zoll im Schwebeflug auf ~1,2 Blöcke.
5. Boden: senkrecht unter der Rotorebene bis `reach · cos(Neigung)` suchen (`DustSurface.probe`). Wasser → Gischt; Laub,
   Luft, Lava oder nichts in Reichweite → nichts. Material aus Tags und Klang: locker (Sand, Kies, Schnee,
   Trockenbeton) 1,0, Erde 0,8, bewachsen (Gras, Moos) 0,55, hart 0,3, glatt (Eis, Glas) 0,15. Farbe aus der
   Kartenfarbe des Blocks (Gras also grün), bewachsene Blöcke zu 30 % mit Erdbraun gemischt, nur leicht (10 %) entsättigt
   und kaum (4 %) aufgehellt, damit der Staub die Farbe des Bodens trägt statt grau zu wirken.
6. Stärke: `(u − 3) / 20` (auf 0..1) × `(1 − Abstand / reach)²`; der Abstand läuft entlang der Strahlachse
   (`h / cos(Neigung)`), ab ~78° Neigung gibt es keinen Staub. Die Staubwolke liegt dort, wo die Strahlachse den Boden trifft.
7. Rate = 142/s × Größe (`D / 0,25`, 0,5 … 2) × Stärke × Material (Gischt 0,6) × Staubmenge^1,5 (`DustModel.amount`) ×
   Partikel-Einstellung (Alle 1,0, Verringert 0,4, Minimal 0). Ein 5-Zoll im Schwebeflug 0,3 Blöcke über Gras mit der
   Standard-Staubmenge 40 % erzeugt ~3,7 Partikel/s; 100 % erzeugen fast das Vierfache (Bildprüfung: linear sah 100 % kaum
   anders aus als der Standard). Die Staubmenge `m` skaliert außerdem Größe und Deckkraft der Partikel um
   `1 + 0,5 × (m − 0,4)` (`DustModel.visualScale`, Standard 1,0, Maximum 1,3; Deckkraft höchstens 0,75).
8. `DustEmitter` wandelt die Rate in ganze Partikel pro Tick (höchstens 8), `DustModel.allowed` hält die Obergrenze
   gleichzeitiger Partikel ein (Alle 64, Verringert 24); zusätzlich begrenzt die Partikel-Engine über
   `ParticleLimit(97)` hart.

## Partikel (`WashParticle`)

- Eigener `SingleQuadParticle` ohne Partikel-Typ, direkt an `ParticleEngine#add`, durchscheinende Ebene, Sprites
  `generic_7 … generic_1` (Staub „dünnt aus“), Einblenden in den ersten 15 %, Ausblenden ab 40 % der Lebenszeit,
  wächst dabei von 0,7 auf 1,5.
- Staub: 0,6–1,1 s, Deckkraft 0,40–0,54, radial nach außen mit 0,02–0,12 Blöcken/Tick je nach Bodenströmung, starke
  Luftreibung (0,88), leichter Auftrieb. Start im Umkreis von höchstens 0,9 Blöcken um den Strahl-Auftreffpunkt, so
  bleibt die Wolke flach am Boden unter den Propellern statt Blöcke weit zu treiben.
- Gischt: 0,4–0,7 s, blassblau, Deckkraft 0,22–0,30, springt leicht hoch und fällt zurück; verschwindet, sobald sie
  wieder unter die Wasseroberfläche sinkt.

## Einstellungen

Konfigurator → Darstellung → „Propwash-Staub“: Schalter „Staub aufwirbeln“ (Standard an) und „Staubmenge“ 10–100 %
(Standard 40 %). Datei `config/propwash/dust.json` (`enabled`, `intensity`).

## Selbsttest (`drone.client.dust.DustSelfTests`)

- „dust: Untergrund erkennen“: Wasser, Laub, Sand und Gras neben dem Spawn → Gischt, nichts, locker, bewachsen; Boden
  außerhalb der Suchtiefe wird ignoriert.
- „dust: Staub beim Schweben, Gischt über Wasser, nichts in der Höhe“: schwebende RACER-Drohne (Motoren 45 %) 0,4 Blöcke
  über Gras → Staub erscheint (mit Textur); Partikel „Minimal“ → keiner; 12 Blöcke hoch → keiner; über einer
  Wasserfläche → nur Gischt.
- „dust: Einstellungen im Tab Darstellung“: Entwurf, Speichern, Standardwerte.
