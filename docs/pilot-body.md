# Dein Körper beim Steuern

Für Spieler. Während du eine Drohne steuerst – mit Brille im FPV oder im Sichtflug –, steht dein Körper mit der Funke in
der Hand an seinem Platz. Er ist dabei nicht geschützt: Er bleibt ein ganz normaler Spieler.

Technik und Entscheidungen: [`design/pilot-body.md`](design/pilot-body.md).

## 1. Was mit deinem Körper passiert

- **Schwerkraft:** Startest du mitten in der Luft (zum Beispiel auf einer Leiter oder nach einem Sprung), fällt dein
  Körper und landet – mit Fallschaden wie sonst auch. Im Gleitflug mit Elytren kannst du keine Drohne starten.
- **Mobs und andere Spieler:** Zombies, Skelette und Co. greifen dich an, andere Spieler treffen dich (mit PvP), ein
  Treffer stößt dich weg.
- **Lava, Feuer, Wasser, Hunger:** Dein Körper brennt, ertrinkt und verhungert wie sonst. Unter Wasser sinkt er langsam,
  auf einer Leiter rutscht er nach unten.
- **Stillstand:** Tasten und Sticks gehen an die Drohne, dein Körper läuft, springt und schleicht nicht mit. Auch ein
  Pferd, auf dem du sitzt, bleibt stehen.

## 2. Was du im FPV siehst

- Bei jedem Treffer blitzt der Bildrand kurz rot auf, und du hörst den bekannten Treffer-Ton – auch wenn die Drohne weit
  weg ist. Die Kamera wackelt dabei nicht.
- Unten in der Mitte erscheint kurz **„Dein Körper wird verletzt!“** mit deiner Gesundheit als roter Balken.
- Unter Wasser zeigt die Karte **„Dein Körper ist unter Wasser“** mit der restlichen Luft als blauem Balken.
- Hast du nur noch 30 % Gesundheit oder weniger, bleibt **„Dein Körper ist schwer verletzt – lande und steig aus“**
  stehen, bis du geheilt bist. Du wirst nicht aus dem FPV geworfen – du entscheidest, wann du landest.

Die Hinweise erscheinen auch, wenn du das HUD mit `F1` ausgeblendet hast.

## 3. Wenn du stirbst

Du verlässt sofort das FPV-Bild, und die Drohne verhält sich wie bei einem Funkverlust – nach deiner
Failsafe-Einstellung im Tab **GPS Rescue**:

- **Heimflug:** Ist die Drohne scharf in der Luft und hat einen Homepoint, fliegt sie allein nach Hause und landet dort
  (wie „GPS Rescue ohne Pilot“, sofern die Welt-Regel dafür an ist).
- **Absinken:** Die Motoren gehen aus, die Drohne fällt.

Am Boden oder ohne Homepoint gehen die Motoren immer aus.

## 4. Geschützte Piloten (Host oder Operator)

Für Events, bei denen Piloten am Rand stehen und nicht angegriffen werden sollen, gibt es die Welt-Regel
**Unverwundbare Piloten** (`propwash:invulnerable_pilots`, Standard *Aus*):

- Konfigurator öffnen (`K`), Tab **Welt-Regeln**, Abschnitt **Spiel**: **Unverwundbare Piloten** auf *An* und speichern.
- Oder per Befehl: `/gamerule propwash:invulnerable_pilots true`.

Dann nimmt niemand Schaden, solange er eine Drohne steuert. Die Leere und `/kill` wirken trotzdem. Sobald du aussteigst,
bist du wieder verwundbar.
