# Funke oder Gamepad im Flug anstecken

Für Spieler. Wer eine Funke oder ein Gamepad per USB ansteckt, während er schon in der FPV-Ansicht fliegt, fliegt damit
sofort weiter, ohne die Ansicht zu verlassen. Bis 0.6.0 erkannte Propwash ein solches Gerät erst nach Verlassen und
erneutem Betreten der FPV-Ansicht, wenn beim Flugstart schon ein anderes Gerät aktiv war, etwa ein nicht eingerichteter
USB-Joystick oder ein Gerät, das sich nur als Joystick meldet (Befund HOTPLUG-1, Discord 28.10.2026).

## 1. Was passiert

- **Anstecken im Flug:** Propwash sieht das Gerät innerhalb von 250 ms (regelmäßige Abfrage der SDL-Geräteliste). Es
  übernimmt den Flug, wenn es auch außerhalb des Flugs gewählt würde:
  - die im Tab „Empfänger“ fest gewählte Funke (wie bisher),
  - bei „Automatisch“: ein eingerichtetes Gerät vor einem nicht eingerichteten, unter eingerichteten das zuletzt
    benutzte, sonst Funke vor Gamepad vor Joystick.
- Die gespeicherte Belegung und Kalibrierung des Geräts gilt sofort. Es erscheint die Meldung „Funke verbunden“ (bzw.
  „Gamepad verbunden“, „Controller verbunden“) mit dem Namen und „(Profil geladen)“ oder „(noch nicht eingerichtet)“.
- Ein eingerichtetes, zuletzt benutztes Gerät, mit dem man schon fliegt, behält den Flug: Ein zusätzlich angestecktes
  Gamepad zum Laden nimmt der Funke nichts weg. Ein neu angestecktes Gerät bekommt diese Chance nur einmal, gleich beim
  Anstecken. Rutscht danach das Kabel der Funke heraus, übernimmt das Gamepad nicht, die Drohne geht in den Failsafe.
- Wechselt das Gerät, gilt wie bei jedem Wechsel der Eingabequelle: Steht der Arm-Schalter des neuen Geräts bei
  entschärfter Drohne schon auf ARM, muss er einmal aus und wieder an. Im scharfen Flug zählt danach der Arm-Schalter
  des neuen Geräts.
- **Abziehen im Flug:** Meldung „Funke getrennt“, die Drohne geht wie bisher in den Failsafe (Verbindung 0, Sperre
  „FAILSAFE“). Propwash springt dabei nie auf ein Gerät, das beim Abziehen schon angesteckt war. Die Tastatur übernimmt
  wie bisher erst, wenn man entschärft eine Flugtaste drückt.
- **Wieder anstecken:** Dieselbe Funke übernimmt sofort wieder. Eine andere, neu angesteckte Funke übernimmt ebenfalls.

## 2. Für Entwickler

- `input/DeviceChooser#pick(…, usage, arrived)`: Mit Sitzungsgerät wählt die Regel unter dem Sitzungsgerät und den
  Geräten, die während der Sitzung neu aufgetaucht sind (`arrived`), wie ohne Sitzung (feste Auswahl, `usageRank`,
  `score`). Ohne Ankunft bleibt alles wie vorher: das Sitzungsgerät oder nichts.
- `input/client/TransmitterInput` merkt sich die Ids neuer Geräte, solange eine Flug-Sitzung (FPV, Sichtflug, Freecam)
  läuft (`sessionArrivals`). Nach jeder Auswahl im Scan streicht es alle Ankünfte außer dem offenen Gerät und einem
  Gerät, das sich nicht öffnen ließ und auf seinen nächsten Versuch wartet (`openBlocked`): Eine Ankunft zählt also nur
  bei der ersten Auswahl nach ihrem Auftauchen und ist danach ein schon vorhandenes Gerät (Befund AUTO27-1). Ganz
  vergessen werden die Ankünfte am Ende der Sitzung, beim Verlassen der Welt und bei einem Neustart der Geräteerkennung.
- Tests: JUnit `input.DeviceHotplugTest`; Selbsttest `input: Hotplug – …` (3 Schritte) steckt virtuelle SDL-Joysticks
  (`SDL_AttachVirtualJoystick`) im Flug an und ab, auf Fabric und NeoForge. Der dritte Schritt steckt während des
  Funkenflugs einen Joystick dazu und zieht dann die Funke ab: Failsafe, kein Rückfall auf den Joystick. Ist am Rechner ein echtes Gerät angesteckt,
  überspringt der Schritt mit Vermerk im Log.
