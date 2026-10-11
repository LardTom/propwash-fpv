# Fliegen mit Maus und Tastatur

Für Spieler ohne Funke und ohne Gamepad. Die Maus lenkt die Drohne wie beim Zielen in einem Shooter, die Tastatur
gibt Gas, giert und schaltet. Technische Einzelheiten stehen in `docs/design/mouse-control.md`.

## Einschalten

1. Konfigurator öffnen (`K`) → Tab **Empfänger** → **Eingabegerät: Maus + Tastatur**.
2. Oder den **Empfänger-Assistenten** öffnen, „Maus + Tastatur“ wählen und **Weiter** drücken: Der Schritt
   „Maus + Tastatur“ zeigt an einer animierten Maus und Funke, wie die Maus lenkt. In der Testfläche rechts probierst du
   die Maus direkt aus, die Funke links macht dann deine Bewegungen mit. **Fertig** speichert.

Solange „Maus + Tastatur“ gewählt ist, öffnet Propwash kein Gamepad und keine Funke, auch wenn eins angeschlossen ist.
Zurück geht es mit **Eingabegerät: Automatisch** oder dem Namen des Geräts.

## Belegung

| Was | Standard | Hinweis |
|---|---|---|
| Nick und Rollen | Maus vor/zurück und links/rechts | „Maus links/rechts: Gieren“ tauscht Rollen und Gieren |
| Gas | `W`/`S` (Vanilla vorwärts/rückwärts) und Mausrad | Gas bleibt stehen, wenn du loslässt |
| Gieren | `A`/`D` (Vanilla links/rechts) | mit „Maus links/rechts: Gieren“ rollen diese Tasten |
| Knüppel zentrieren | mittlere Maustaste | stellt die Drohne im Angle-Modus sofort gerade |
| Scharf schalten | `Z` (deutsche Tastatur: `Y`) | wie beim Tastaturflug |
| Flugmodus | `R`, Ziffernblock 1/2/3 | Acro, Angle, Horizon |
| Flug beenden | Rechtsklick mit der Funke oder `Y` (deutsche Tastatur: `Z`) | |

Alle Tasten änderst du im Tab **Tasten**; die Pfeiltasten wirken zusätzlich auf Nick und Rollen. Mit „Maus + Tastatur“
startest du wie beim Tastaturflug im Angle-Modus.

## Die zwei Arten, wie die Maus lenkt

- **Tempo:** Wie schnell du die Maus bewegst, so weit ist der Knüppel ausgeschlagen. Steht die Maus, federt der Knüppel
  in die Mitte. In **Acro** heißt das: Die Drohne dreht sich, solange du die Maus bewegst, und bleibt dann in der Lage –
  genau wie das Umsehen in einem Shooter.
- **Halten:** Der Knüppel bleibt, wo du ihn mit der Maus hinschiebst. In **Angle** heißt das: Maus ein Stück nach vorn,
  die Drohne neigt sich und fliegt vorwärts, bis du die Maus zurückziehst. Die **Rückstellung** lässt den Knüppel
  langsam von selbst zur Mitte wandern (Standard 40 %/s, „aus“ = er bleibt stehen).
- **Automatisch** (Standard) nimmt in Acro „Tempo“ und in Angle und Horizon „Halten“. Damit kommen Einsteiger im
  Angle-Modus und Profis in Acro ohne Umstellen zurecht.

Gieren mit der Maus („Maus links/rechts: Gieren“) folgt immer dem Tempo der Maus, wie das Drehen der Blickrichtung.

## Einstellungen im Tab „Empfänger“

| Einstellung | Standard | Wirkung |
|---|---|---|
| Maus-Modus | Automatisch | siehe oben |
| Maus links/rechts | Rollen | Rollen oder Gieren |
| Maus-Empfindlichkeit | 100 % | bei 100 % Vollausschlag bei 30 cm/s (Tempo) bzw. nach 6 cm Weg (Halten) |
| Maus-DPI | 800 | die DPI deiner Maus; dann bedeutet die Empfindlichkeit für jede Maus denselben Weg auf dem Tisch |
| Maus-Y umkehren | aus | aus: Maus vor = Nase runter (wie ein Knüppel); an: Maus vor = Nase hoch (wie Hochschauen) |
| Maus-Glättung | 20 ms | mittelt das Maustempo über diese Zeit (nur Tempo und Gieren); 0 ms nimmt jedes Bild für sich |
| Maus-Totzone | 3 % | kleinste Bewegungen um die Mitte lösen nichts aus |
| Maus-Expo | 25 % | feinere Mitte, außen trotzdem Vollausschlag; wirkt zusätzlich zu den Rates der Drohne |
| Rückstellung | 40 %/s | nur „Halten“: wie schnell der Knüppel von selbst zur Mitte wandert |
| Mausrad-Gas | 5 % je Raste | 0 = aus; im Flug wechselt das Mausrad dann nicht die Schnellleiste |
| Tastatur-Ausschlag, Tastatur-Gas-Tempo | 60 %, 50 %/s | für `A`/`D`, die Pfeiltasten und `W`/`S` |

Unter „Virtuelle Knüppel“ siehst du beide Knüppel. Klick in die Testfläche und zieh die Maus: Die Knüppel zeigen, was
die Maus mit deinen Einstellungen macht. Der Knopf „Angle“/„Acro“ in der Testfläche wählt, welchen Flugmodus die
Vorschau bei „Automatisch“ annimmt. Alle Maus-Einstellungen findest du auch über die Suche (`Strg+F`, z. B. „Maus“).

Im Flug zeigt das OSD-Element **Knüppelanzeige** (Tab „OSD“) die Knüppel live.

## Mauszeiger

- Im Flug (FPV und Sichtflug) ist der Mauszeiger gefangen wie im normalen Spiel, die Maus dreht den Spieler nicht.
- `Esc`, das Inventar, der Konfigurator und jedes andere Fenster geben den Zeiger frei; dann bewegt die Maus die
  Drohne nicht und die Maus-Knüppel gehen in die Mitte. Nach dem Schließen geht es ohne Sprung weiter.
- Propwash liest die rohen relativen Mausbewegungen, die Minecraft 26.3 über SDL3 bekommt – unabhängig vom Monitor und
  von der Fenstergröße. Minecraft 26.3 hat keinen eigenen Schalter „Rohe Eingabe“ mehr; ist die Maus in einem Spiel
  ungleichmäßig, hilft es meist, die Zeigerbeschleunigung des Systems auszuschalten (Windows: „Zeigerbeschleunigung
  verbessern“, macOS: Einstellungen → Maus).
- Touch-Eingaben und Stifte lenken nicht.

## Tipps

- Starte in **Angle** mit „Automatisch“: Maus ein Stück nach vorn = vorwärts fliegen, mittlere Maustaste = sofort gerade.
- Für **Acro** und Tricks: „Tempo“ (oder „Automatisch“ in Acro) und eine große Mausunterlage. Je schneller die Maus,
  desto schneller der Flip. Wer weniger Mausweg will, erhöht die Empfindlichkeit.
- Mit „Maus links/rechts: Gieren“ fliegt sich die Drohne wie eine Kamera im Shooter: Maus dreht die Nase, `A`/`D` rollen.
- Das Flugtraining (`M`) zeigt dir mit gewählter Maus eigene Tipps in den Lektionen.
- Maus + Tastatur ist keine Flughilfe: Runden zählen ganz normal, und im Mehrspieler ändert sich nichts – die Maus
  wird nur auf deinem Rechner zu Knüppelwerten.
