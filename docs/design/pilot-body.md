# Pilotenkörper beim Steuern

Stand 0.5.0. Wer eine Drohne steuert (FPV oder Sichtflug), bleibt mit seinem Körper ein normaler Spieler: Schwerkraft,
Rückstoß, Strömung, Mobs, andere Spieler, Stürze, Lava, Feuer, Ertrinken und Hunger wirken wie sonst. Der Körper steht
still, weil Tasten und Sticks an die Drohne gehen, nicht weil er eingefroren ist.

## 1. Vorher und warum

| Was | Wie bis zum Fix | Warum es so gebaut war |
|---|---|---|
| Bewegung | `LivingEntityMixin` brach `LivingEntity#travel` des eigenen Spielers ab, solange er steuerte | Der Körper sollte nicht mit WASD mitlaufen; Abbrechen der ganzen Bewegung war der einfachste Weg |
| Lage beim Server | Vanilla `LocalPlayer#sendPosition` schickt nur, solange der Spieler die Kamera ist; im FPV ist die Drohne die Kamera | Vanilla-Verhalten (Zuschauer-Kamera), nie angepasst |
| Start | nur auf festem Boden, im Wasser, auf Leitern, als Mitfahrer oder mit Flugrecht (`bodyCanRest`) | Ein eingefrorener Körper in der Luft würde auf dedizierten Servern ohne `allow-flight` nach 80 Ticks mit „Flying is not enabled on this server“ getrennt (`ServerGamePacketListenerImpl`, `clientIsFloating`) |
| Schaden | Server-seitig gab es Schaden durch Mobs, Lava und Ertrinken, aber keinen Fallschaden (der Server rechnet ihn aus den Bewegungspaketen) und keine Bewegung durch Rückstoß | – |
| Rückmeldung | Vanilla-HUD im FPV aus (`OsdHud#hideVanilla`), der Treffer-Wackler wurde mit dem FPV-Gefühl gestrichen, der Treffer-Ton spielt am Körper und ist an der Drohne nicht zu hören | – |

Folge: Ein Pilot hing in der Luft, wenn ihn etwas bewegen sollte (Rückstoß, abgebauter Block, Wasser), bekam keinen
Fallschaden und merkte im FPV nichts von Treffern – er wirkte unsterblich und schwebend, bis er ausstieg.

## 2. Jetzt

1. **Körper läuft weiter** (`mixin.drone.client.LivingEntityMixin`, `drone.client.PilotBody`): `travel` wird nur noch
   angehalten, wenn beim Client die Chunks um den Körper fehlen (3 × 3 Chunks, `PilotBodyRules.LOADED_RADIUS`). Das
   passiert, wenn die Drohne weiter als die Sichtweite fliegt und der Server die Chunks um die Drohne statt um den
   Spieler schickt (`ViewAnchors`). Ohne diese Bremse fiele der Körper durch die leere Welt.
2. **Keine Eingaben an den Körper** (`mixin.drone.client.LocalPlayerMixin`): direkt nach `ClientInput#tick` werden
   Tasten, Bewegungsvektor, `xxa`/`yya`/`zza` und Springen geleert, solange gesteuert wird (auch im Sichtflug).
   Boote, Pferde und Schweine bekommen dadurch ebenfalls keine Eingaben.
3. **Lage an den Server**: `LocalPlayerMixin` lässt `sendPosition` auch ohne Kamera senden, solange die Umgebung
   geladen ist. Der Server rechnet Fallschaden, Kollision und den Schutz gegen Schweben wie bei jedem Spieler; ein
   fallender Körper gilt nicht als schwebend, ein Kick ist damit ausgeschlossen.
4. **Start** (`DronePiloting#tryStart`): die Bodenprüfung entfällt, ein Pilot darf mitten in der Luft starten und fällt
   dann. Verweigert wird nur der Start im Gleitflug mit Elytren (`StartResult.GLIDING`, `PilotBodyRules#mayStart`).
5. **Treffer-Hinweis im FPV** (`drone.client.PilotBodyHud`, Logik `drone.PilotBodyAlert` ohne Minecraft-Typen): sinkt
   Gesundheit plus Absorption um mindestens 0,01, blitzt ein roter Rand auf (0,7 s, Stärke nach Schaden 0,35–0,85)
   und der Vanilla-Treffer-Ton spielt als Oberflächen-Ton. Darunter erscheint eine Karte im Propwash-Stil mit
   Gesundheitsbalken: „Dein Körper wird verletzt!“ (3 s, blendet aus), „Dein Körper ist unter Wasser – Luft x %“
   (solange die Luft nicht voll ist, mit blauem Balken) und dauerhaft „Dein Körper ist schwer verletzt – lande und
   steig aus“ ab 30 % Gesundheit. Kein Kamerawackeln. Die Ebene ist auch mit F1 sichtbar (`HudVisibility.ALWAYS`).
6. **Tod** (`DronePiloting#onServerTick`, `PilotSession#endOrWait`, `RescueSupervisor.Frame#pilotDown`): stirbt der
   Pilot, wertet der Client das wie einen Funkverlust. Mit Failsafe „Heimflug“, scharfer Drohne in der Luft und
   Homepoint startet GPS Rescue und meldet sich beim Server; nach drei Bildern (höchstens 0,4 s) verlässt der Client
   die Sitzung, und der Server fliegt die Drohne wie bei „GPS Rescue ohne Pilot“ allein heim
   (`docs/design/autonomous-rescue.md`, Welt-Regel `propwash:autonomous_rescue`). Mit Failsafe „Absinken“, am Boden
   oder ohne Homepoint wird die Drohne entschärft und fällt. Der Server wartet nach dem Tod höchstens 20 Ticks auf
   den Client (`PilotBodyRules.DEATH_GRACE_TICKS`), danach entschärft er die Drohne selbst; nach dem Wiedereinstieg
   sofort.
7. **Welt-Regel „Unverwundbare Piloten“** (`propwash:invulnerable_pilots`, Schalter, Standard aus, Gruppe Spiel, in
   keiner Voreinstellung an): wer steuert, ist für alle Schadensarten unverwundbar, die Unverwundbarkeit nicht
   durchbrechen – die Leere und `/kill` wirken immer (`mixin.drone.ServerPlayerMixin` auf
   `ServerPlayer#isInvulnerableTo`, `drone.PilotProtection`).

## 3. Randfälle

| Fall | Verhalten |
|---|---|
| Start mitten in der Luft | Körper fällt, landet, nimmt Fallschaden |
| Start im Gleitflug mit Elytren | verweigert: „Lande erst – im Gleitflug mit Elytren kannst du keine Drohne steuern“ |
| Start als Mitfahrer (Boot, Lore, Pferd) | erlaubt; ohne Eingaben bleibt das Pferd stehen, das Boot treibt, die Lore rollt wie sonst |
| Wasser, Lava | Körper sinkt oder treibt, ertrinkt oder brennt; Hinweis „unter Wasser“ mit Luft |
| Leiter, Ranken | ohne Schleichen rutscht der Körper wie in Vanilla langsam nach unten |
| Gerüst | wer oben steht, bleibt stehen |
| wenig Gesundheit | Start erlaubt, Hinweis „schwer verletzt“ sofort sichtbar |
| Ausloggen beim Steuern | Drohne entschärft (oder fliegt mit laufendem GPS Rescue allein heim), Körper bleibt, wo er stand |
| Chunks um den Körper beim Client entladen | Körper wird angehalten und meldet keine Lage; der Server hält die Chunks um den Spieler geladen (Pilot-Ticket), Mobs, Lava und Ertrinken wirken weiter; Fallen und Rückstoß setzen ein, sobald die Chunks wieder da sind |
| Kreativ, Zuschauer | unverändert (Kreativ nimmt keinen Schaden, Fliegen bleibt erlaubt) |
| dedizierter Server | kein Kick wegen Schwebens, Lage von Client und Server stimmen überein |

## 4. Entscheidungen

| Entscheidung | Warum | Verworfen |
|---|---|---|
| Körper weiter simulieren statt einfrieren | realistisch, fair, beseitigt den Kick-Grund an der Wurzel | Einfrieren mit `mayfly`-Freigabe (Ausnahme vom Kick wäre ein Fluchtweg vor Fallschaden und Mobs) |
| Eingaben am `ClientInput` leeren | wirkt auf Laufen, Springen, Schleichen und Reittiere gleich | einzelne Tasten abfangen (lückenhaft, Controlify und andere Eingabe-Mods) |
| Start in der Luft erlauben, nur Elytren-Gleitflug verweigern | der Körper fällt und landet jetzt korrekt; im Gleitflug ohne Steuerung wäre der Absturz sicher | Bodenprüfung behalten (unnötig streng, verbarg das eigentliche Problem) |
| Schwere Verletzung wirft nicht aus dem FPV | ein erzwungener Ausstieg ließe die Drohne abstürzen und wäre per PvP-Stupser auslösbar; der Pilot entscheidet, der dauerhafte Hinweis ist deutlich | Auswurf ab einer Schadensschwelle |
| Tod = Funkverlust | nutzt das vorhandene Failsafe mit den Einstellungen des Piloten (Heimflug oder Absinken) und die Übergabe an den Server | immer entschärfen (verschenkt GPS Rescue), Server rät das Failsafe (kennt die Einstellungen nicht) |
| Gnadenfrist 20 Ticks am Server | Zeit für die Failsafe-Meldung des Clients, ohne dass ein toter Pilot länger fliegt | sofort freigeben (Heimflug käme nie an), keine Grenze (manipulierter Client flöge tot weiter) |
| Vanilla-Treffer-Ton als Oberflächen-Ton | jeder Spieler kennt ihn als „ich werde getroffen“ | eigener Piepton (verwechselbar mit Akku- und Arm-Pieptönen) |
| Hinweis „unter Wasser“ vor „verletzt“ | beim Ertrinken trifft es jede Sekunde, die Luftanzeige sagt mehr | Treffer-Hinweis zuerst |
| Hinweis auch mit F1 | wer das HUD für ein sauberes Bild ausblendet, soll trotzdem merken, dass der Körper stirbt | nur mit HUD |
| Welt-Regel statt fester Unverwundbarkeit, Standard aus | Event-Server können geschützte Piloten wollen, sonst gilt Realismus; Regel gilt für alle Schadensarten außer Leere und `/kill` | Konfiguration je Spieler (wäre ein Vorteil auf fremden Servern) |
| Kein Anstieg der Netzwerkprotokoll-Version | keine neuen Pakete; Welt-Regeln werden nach Namen synchronisiert, 0.5.0 trägt ohnehin Protokoll 13 statt 8 | eigene Erhöhung |

## 5. Prüfungen

- JUnit: `PilotBodyRulesTest` (Gnadenfrist, Ausstieg nach dem Tod, Regel, Elytren), `PilotBodyAlertTest` (Rand, Karte,
  Absorption, Heilung, Ertrinken, Wiedereinstieg ohne Blitz), `RescueSupervisorTest` (toter Pilot löst das Failsafe aus,
  am Boden und mit „Absinken“ nicht, Sticks übersteuern nicht).
- Selbsttests „pilotenkörper: …“ (Fabric und NeoForge, Filter `-PpropwashSelftestOnly=pilotenk`): Start 6 Blöcke über
  der Plattform, Fallschaden mit Hinweis, Stillstand ohne Eingaben bei gleicher Lage auf Client und Server,
  Wüstenzombie mit Ziel, Schaden und Rückstoß, Lava, Ertrinken mit Luft-Hinweis, Welt-Regel an und aus, Tod im Flug mit
  Heimflug ohne Pilot und mit „Absinken“. Bildschirmfotos `docs/screenshots/pilot_body_hit.png` und
  `pilot_body_air.png`.
- Dedizierter Server ohne Kick: Beitrittstest mit `PROPWASH_JOINTEST_PILOT=<sekunden>` am Client und
  `PROPWASH_JOINTEST_PILOT=1` am Server (`dev.PilotJoinCheck`): der Server startet 3 s nach dem Beitritt eine
  FPV-Sitzung 8 Blöcke über dem Boden im Überlebensmodus; der Client verlangt `pilot fpv held` und `body landed`
  (`docs/testing.md`).
