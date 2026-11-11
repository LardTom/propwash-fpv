# Add-on-Welt-Regeln im Tab „Welt-Regeln“ (API 1.7, Baustein B5)

Für Add-on-Autoren, Server-Admins und Spieler. Ein Add-on meldet eigene Welt-Regeln über
`PropwashApi.worldRules()` an. Propwash macht daraus echte Gamerules im Namensraum des Add-ons, zeigt sie im Tab
„Welt-Regeln“ des Konfigurators in einem eigenen Abschnitt je Add-on, gleicht sie wie die eigenen Regeln mit allen
Clients ab und setzt sie mit den Voreinstellungen Realistisch, Training und Arcade.

Technik und Entscheidungen: [`design/0.6.0/addon-api-namespace.md`](design/0.6.0/addon-api-namespace.md) §3.8, §3.19,
E24 und E40.

## 1. Anmelden

```java
WorldRules rules = PropwashApi.worldRules();
WorldRule<Boolean> buckets = rules.registerBoolean(Identifier.fromNamespaceAndPath("mobdefense", "allow_buckets"), false);
WorldRule<Integer> cooldown = rules.registerInt(Identifier.fromNamespaceAndPath("mobdefense", "cooldown_ticks"), 4, 0, 100);
rules.presetValue(cooldown, WorldRules.PRESET_TRAINING, 0);
rules.presetValue(buckets, WorldRules.PRESET_ARCADE, true);
```

- Nur in der Common-Init des Add-ons (Fabric `onInitialize`, NeoForge Mod-Konstruktor): Gamerules sind ein Register.
  Eine spätere Anmeldung scheitert mit einer Ausnahme und wird nicht übernommen.
- Der Namensraum `propwash` ist gesperrt (`IllegalArgumentException`, [`ADDONS.md`](../ADDONS.md)); eine Id zweimal
  anzumelden wirft ebenfalls.
- Zahlenregeln haben einen festen Bereich `min`–`max`, der Standard muss darin liegen. `/gamerule` nimmt nur Werte im
  Bereich an.
- Texte im eigenen Namensraum: `gamerule.<ns>.<pfad>` (Name) und `gamerule.<ns>.<pfad>.description` (Tooltip),
  optional `<ns>.world_rules.section` für die Überschrift des Abschnitts (§3).
- Beispiel im Repo: `dev/example/ExampleRules` (nur im Entwicklungslauf) mit `propwash_example:launcher_cooldown`
  (Zahl, Standard 10, Training 4, Arcade 0) und `propwash_example:launcher_refill` (Schalter, Standard aus, Arcade an).

## 2. Werte lesen

- `rule.get(level)` liefert auf dem Server den Wert der Welt, auf dem Client den zuletzt vom Server abgeglichenen Wert.
  Ohne Welt (`null`), vor dem ersten Abgleich oder auf einem Server ohne das Add-on liefert `get` den Standard.
- Der Abgleich nutzt den bestehenden Weg der Propwash-Welt-Regeln (Paket `propwash:world_rules` beim Beitritt und nach
  jeder Änderung); Add-ons brauchen kein eigenes Netzpaket.
- Gespeichert werden die Werte wie jede Gamerule in der Welt. Fehlt das Add-on später, bleiben die übrigen Regeln
  unverändert.

## 3. Tab „Welt-Regeln“

- Hinter den Propwash-Regeln folgt je Add-on ein eigener Abschnitt. Überschrift ist der Text des optionalen
  Sprachschlüssels `<ns>.world_rules.section` (`<ns>` = Namensraum der Regeln, in der Regel die Mod-Id, z. B.
  `"propwash_example.world_rules.section": "Beispiel-Add-on"`), ohne ihn der Name aus den Mod-Metadaten (ein
  vorangestelltes „Propwash:“ entfällt wie bei den Kreativ-Tabs), ohne Metadaten die Mod-Id. Abschnitte stehen
  alphabetisch nach der angezeigten Überschrift, die Regeln darin in der Reihenfolge der Anmeldung.
- Schalter erscheinen als An/Aus-Knopf, Zahlenregeln als Regler (Bereich bis 1000 Schritte, Eingabe per Tippen) oder als
  Zahlenfeld. Der Tooltip zeigt Beschreibung, Standard und den Befehl `/gamerule <ns>:<pfad>`.
- Lange Namen: Namen stehen in einer gemeinsamen Spalte. Passt ein Name dort auch gestaucht nicht hinein, bekommt nur
  diese Zeile mehr Platz: Das Bedienelement (An/Aus-Knopf, Stufen, Regler, Zahlenfeld) wird schmaler, bis es gerade
  noch seinen Wert zeigt, ohne Recht rückt der Wert nach rechts (`server.client.RuleRowLayout`). So bleiben die Namen
  auch bei GUI 320×240 auf Deutsch und mit mehreren Add-ons ganz; Zeilen, die passen, bleiben unverändert.
- Bearbeiten wie bei den Propwash-Regeln: im Einzelspieler und für Operatoren bzw. Spieler mit Recht auf `/gamerule`,
  sonst nur lesbar. Jede Änderung meldet der Server allen Spielern im Chat.
- „Standard“ unten im Konfigurator setzt auch die Add-on-Regeln auf ihren Standard.

## 4. Voreinstellungen

- `presetValue(rule, presetId, value)` legt den Wert einer Add-on-Regel in einer eingebauten Voreinstellung fest.
  Erlaubt sind genau `realistic`, `training` und `arcade` (`WorldRules.PRESETS`); ein späterer Aufruf für dieselbe Regel
  und Voreinstellung ersetzt den Wert.
- Fehler mit Text (`IllegalArgumentException`): unbekannte Voreinstellung (der Text nennt die Regel und die drei
  erlaubten Ids), eine Regel, die nicht über `PropwashApi.worldRules()` angemeldet ist (auch eine eigene
  `WorldRule`-Implementierung mit derselben Id), ein Wert außerhalb des Bereichs oder vom falschen Typ.
- Voreinstellung anwenden (Knopf im Tab oder `/propwash rules preset <id>`) setzt die Propwash-Regeln und jede
  Add-on-Regel mit Eintrag für diese Voreinstellung. Add-on-Regeln **ohne** Eintrag bleiben unverändert: steht eine
  solche Regel auf ihrem Standard, bleibt der Standard. Die Zahl der geänderten Regeln in der Rückmeldung zählt die
  Add-on-Regeln mit.
- Die Anzeige „Voreinstellung: …“ im Tab und in `/propwash rules` zählt eine Voreinstellung nur als aktiv, wenn auch
  jede Add-on-Regel mit Eintrag diesen Wert hat. Regeln, die der Server nicht kennt (Add-on nur beim Spieler), zählen
  nicht.

## 5. Grenzen in 1.7

- Add-on-Regeln lassen sich **nicht** in Zonen überschreiben; Zonen bleiben bei Schwerkraft, Wind und den übrigen
  Propwash-Regeln.
- Nur Schalter und ganze Zahlen; keine Texte, Kommazahlen oder Auswahllisten.
- Der Tooltip eines Voreinstellungs-Knopfs listet nur die Propwash-Regeln, die er ändert.

## 6. Tests

- JUnit: `server.AddonPresetValueTest` (`presetValue` für alle drei Voreinstellungen, Fehlertexte, Anwenden mit und
  ohne Eintrag, aktive Voreinstellung), `server.client.AddonRuleSectionsTest` (Abschnitte, Namen, Reihenfolge),
  `server.client.AddonRuleSectionTitleTest` (Überschrift aus `<ns>.world_rules.section`, Rückfall auf den Mod-Namen),
  `server.client.RuleRowLayoutTest` (Platz für lange Namen neben Schalter und Zahlenfeld und ohne Recht neben dem Wert,
  passende Zeilen in der gemeinsamen Spalte).
- Selbsttest-Gruppe `addon-api-17: B5` auf Fabric und NeoForge: Gamerules in Propwashs Kategorie, `get` auf Server und
  Client, Abschnitt im Tab, Änderung im Tab, Voreinstellungen Training, Arcade und Realistisch, Tab bei GUI 320×240 auf
  Deutsch mit Recht und gesperrt ohne gekürzten Regelnamen oder Wert (mit allen geladenen Add-ons, etwa mit den Jars von
  Mob Defense und Kamera-Add-on in `mods/`); mit
  `-PpropwashScreenshots=true` die Bilder `world_rules_addon_{en,de}_{854x480,320x240}`.
- Server-Rauchtest: Beispiel-Regeln am dedizierten Server registriert, Training und Realistisch, Werte im Abgleich.

## 7. Englische Kernsätze (Website)

- Add-ons register their own world rules with `PropwashApi.worldRules()`: booleans and whole numbers in their own
  namespace, stored as regular gamerules.
- The World Rules tab shows one section per add-on, titled with the optional lang key `<modid>.world_rules.section` or
  else the mod name, editable in singleplayer and by operators and read-only for everyone else.
- `rule.get(level)` works on both sides; clients receive the values through Propwash's existing world-rule sync.
- `presetValue(rule, "realistic" | "training" | "arcade", value)` puts an add-on rule into a built-in preset. Applying the
  preset sets it; rules without an entry keep their current value. Unknown preset ids or rules from elsewhere throw with
  a clear message.
- Add-on rules can't be overridden in zones in API 1.7.
