# Schneller aufnehmen mit ffmpeg

Für Spieler. Dieser Text ist die Vorlage für den Abschnitt „Schneller aufnehmen mit ffmpeg“ im Spielerhandbuch auf
propwashfpv.com (`docs/guide/`, Sprungziel `video-ffmpeg`, auf das der Knopf **Anleitung** im Tab *Action-Cam* zeigt);
die englische Fassung steht unten. Technik und Entscheidungen: [`design/action-cam-recording.md`](design/action-cam-recording.md).

## Worum es geht

Die Action-Cam nimmt auch ohne Zusatzprogramm auf: Propwash bringt einen eigenen Video-Encoder mit, der auf dem
Prozessor rechnet. Bei 1080p60 mit Shadern braucht er aber die Kerne, die das Spiel selbst gern hätte. Ist **ffmpeg**
installiert, übergibt Propwash die Bilder an den Video-Encoder der Grafikkarte oder des Prozessors (VideoToolbox auf dem
Mac, NVENC bei NVIDIA, Quick Sync bei Intel, AMF bei AMD, VA-API unter Linux). Das Spiel läuft dann ruhiger, und
1080p60 oder 1440p60 gehen ohne Rückstand. ffmpeg ist kostenlos, gehört nicht zu Propwash und wird nicht mitgeliefert.

Du musst nichts einstellen: Mit Encoder *Automatisch* (Standard) nutzt Propwash ffmpeg, sobald es gefunden wird, und
sonst den eigenen Encoder.

## Installieren

| System | So geht es |
|---|---|
| **Windows** | Eingabeaufforderung öffnen und `winget install ffmpeg` eingeben. Alternativ `scoop install ffmpeg` oder `choco install ffmpeg`. |
| **macOS** | Im Terminal `brew install ffmpeg` ([Homebrew](https://brew.sh)) oder `sudo port install ffmpeg` (MacPorts). |
| **Linux** | Das Paket `ffmpeg` deiner Distribution, z. B. `sudo apt install ffmpeg`, `sudo dnf install ffmpeg` oder `sudo pacman -S ffmpeg`. |
| **Ohne Installation** | Ein fertiges ffmpeg herunterladen und die Datei `ffmpeg` (Windows: `ffmpeg.exe`) in den Ordner `propwash/ffmpeg/` im Spielverzeichnis legen – praktisch für Launcher, die als Flatpak oder Snap laufen. |

Danach brauchst du das Spiel nicht neu zu starten: Den Tab *Action-Cam* öffnen – war ffmpeg vorher nicht da, sucht er
neu – oder dort auf **Testen** drücken.

## Prüfen, ob es klappt

1. Konfigurator öffnen (`K`) → Tab **Action-Cam** → Abschnitt **Schneller aufnehmen mit ffmpeg**. Der Abschnitt ist
   immer da, auch ohne Drohne in der Hand.
2. Dort steht eine der Zeilen:
   - **„ffmpeg 8.0 gefunden (Pfad) – Hardware-Encoder: NVENC (Test 1080p60: 240 Bilder/s)“** – alles bereit. Die Zahl ist
     ein kurzer Testclip im eingestellten Format; sie muss nur über der Bildrate der Aufnahme liegen.
   - **„ffmpeg nicht gefunden …“** – ffmpeg installieren (oben) und **Testen** drücken.
   - **„… gefunden, aber kein Hardware-Encoder funktioniert (…)“** – ffmpeg ist da, aber die Grafikkarte oder ihr
     Treiber bietet keinen Video-Encoder an. Propwash nimmt dann mit dem eigenen Encoder auf; in der Klammer steht, was
     ffmpeg gemeldet hat.
   - **„ffmpeg unter … startet nicht (…)“** – die Datei lässt sich nicht ausführen (siehe „Wenn es nicht klappt“).
3. Beim Start jeder Aufnahme zeigt die Anzeige oben in der Bildmitte sechs Sekunden lang den Encoder:
   **„Encoder: VideoToolbox (Hardware, über ffmpeg)“** oder **„Encoder: Propwash (CPU)“** mit dem Grund. Erscheint später
   ein Hinweis, steht der Encoder rechts in dessen Kopfzeile („1080p60 · NVENC“). Die Anzeige gehört zu
   *Aufnahme-Hinweise* im Tab *Action-Cam*.

## Wo Propwash sucht

In dieser Reihenfolge, das erste Programm gewinnt:

1. der **ffmpeg-Pfad** aus dem Tab *Action-Cam* (Datei oder Ordner),
2. der Ordner `propwash/ffmpeg/` im Spielverzeichnis,
3. die Ordner im `PATH` des Spiels,
4. die üblichen Installationsorte – auch wenn sie nicht im `PATH` des Spiels stehen:
   - macOS: Homebrew (`/opt/homebrew/bin`, `/usr/local/bin`, auch `ffmpeg-full` und `ffmpeg@…` unter `opt/`), MacPorts
     (`/opt/local/bin`), `~/bin`, `~/.local/bin`, Nix;
   - Windows: WinGet (Verknüpfungen und Paketordner), Scoop, Chocolatey, `Programme\ffmpeg\bin`;
   - Linux: `/usr/bin`, `/usr/local/bin`, Snap, Linuxbrew, `~/bin`, `~/.local/bin`, Nix;
5. auf macOS und Linux zuletzt die Anmelde-Shell (`command -v ffmpeg`), falls dein `PATH` nur dort gesetzt wird.

Der vierte Punkt ist der Grund, warum es auch aus einem Launcher heraus klappt: Auf dem Mac erben Programme, die aus dem
Dock oder einem Launcher starten, den `PATH` des Terminals nicht – Homebrew liegt aber immer am selben Ort.

## Eigener Pfad

Liegt dein ffmpeg woanders, trag den Pfad der Datei oder ihres Ordners in das Feld **ffmpeg-Pfad** ein – oder zieh die
Datei aus dem Dateimanager auf das Spielfenster – und drück **Testen** (oder `Enter`). Anführungszeichen um den Pfad und
`~` für den eigenen Ordner sind erlaubt. Die Statuszeile zeigt danach Version und Pfad genau dieses Programms. Stimmt der
Pfad nicht, steht das dort, und Propwash nimmt das automatisch gefundene ffmpeg. Leeres Feld heißt: automatisch suchen.

## Wenn es nicht klappt

- **Gerade installiert, aber „nicht gefunden“:** **Testen** drücken. Hilft das nicht (Windows, eigener Ordner im
  `PATH`), den Launcher einmal ganz beenden und neu starten – er gibt dem Spiel sonst den alten `PATH` mit – oder den
  Pfad eintragen.
- **Heruntergeladenes ffmpeg „ist nicht ausführbar“ oder „startet nicht“ (macOS, Linux):** Im Ordner `propwash/ffmpeg/`
  macht Propwash die Datei selbst ausführbar; bei einem eigenen Pfad im Terminal `chmod +x ffmpeg` eingeben. Auf dem Mac
  darf sie außerdem nicht in Quarantäne stehen (`xattr -d com.apple.quarantine ffmpeg`), und auf Macs mit Apple-Chip
  braucht ein Intel-Programm Rosetta. Mit Homebrew entfällt das alles.
- **Launcher als Flatpak oder Snap:** Das Spiel sieht die Programme des Systems nicht. Ein statisches ffmpeg in
  `propwash/ffmpeg/` im Spielverzeichnis legen.
- **„Hardware-Encoder ausgefallen“:** Fällt der Encoder mitten in einer Aufnahme aus, bleibt das Video bis dahin
  erhalten, und Propwash nimmt mit dem eigenen Encoder weiter auf – bis du **Testen** drückst oder das Spiel neu startest.
- **Encoder steht auf „Propwash (CPU)“:** Dann bleibt ffmpeg unbenutzt; der Abschnitt sagt das. *Automatisch* wählen.
- **Laptop mit zwei Grafikkarten:** NVENC gibt es nur, wenn die NVIDIA-Karte aktiv ist; sonst probiert Propwash Quick
  Sync, AMF und zuletzt Media Foundation.

---

# Faster recording with ffmpeg

English text for the player guide on propwashfpv.com (anchor `video-ffmpeg`).

## What it does

The action cam records without any extra program: Propwash has its own video encoder, which runs on the processor. At
1080p60 with shaders it needs the cores the game would like to have. With **ffmpeg** installed, Propwash hands the frames
to the video encoder of your graphics card or processor (VideoToolbox on a Mac, NVENC on NVIDIA, Quick Sync on Intel, AMF
on AMD, VA-API on Linux). The game runs smoother and 1080p60 or 1440p60 record without falling behind. ffmpeg is free,
is not part of Propwash and is not included.

There is nothing to set up: with the encoder on *Automatic* (the default) Propwash uses ffmpeg as soon as it finds it,
and its own encoder otherwise.

## Install it

| System | How |
|---|---|
| **Windows** | Open a command prompt and type `winget install ffmpeg`. Or `scoop install ffmpeg`, or `choco install ffmpeg`. |
| **macOS** | In the Terminal: `brew install ffmpeg` ([Homebrew](https://brew.sh)) or `sudo port install ffmpeg` (MacPorts). |
| **Linux** | The `ffmpeg` package of your distribution, e.g. `sudo apt install ffmpeg`, `sudo dnf install ffmpeg` or `sudo pacman -S ffmpeg`. |
| **Without installing** | Download a ready-made ffmpeg and put the file `ffmpeg` (Windows: `ffmpeg.exe`) into the folder `propwash/ffmpeg/` in the game directory – handy for launchers that run as a Flatpak or Snap. |

You do not need to restart the game afterwards: open the *Action cam* tab – if ffmpeg was missing before, it searches
again – or press **Test** there.

## Check that it works

1. Open the configurator (`K`) → **Action cam** tab → section **Faster recording with ffmpeg**. The section is always
   there, even without a drone in your hand.
2. It shows one of these lines:
   - **“ffmpeg 8.0 found (path) – hardware encoder: NVENC (test 1080p60: 240 frames/s)”** – all set. The number comes
     from a short test clip in the chosen format; it only has to be above the frame rate you record at.
   - **“ffmpeg not found …”** – install ffmpeg (above) and press **Test**.
   - **“… found, but no hardware encoder works (…)”** – ffmpeg is there, but the graphics card or its driver offers no
     video encoder. Propwash records with its own encoder; the brackets show what ffmpeg reported.
   - **“ffmpeg at … does not start (…)”** – the file cannot be run (see “If it does not work”).
3. When a recording starts, the notice at the top center shows the encoder for six seconds:
   **“Encoder: VideoToolbox (hardware, via ffmpeg)”** or **“Encoder: Propwash (CPU)”** with the reason. If a notice
   appears later, the encoder is on the right of its header (“1080p60 · NVENC”). The notice belongs to *Recording
   notices* in the *Action cam* tab.

## Where Propwash looks

In this order, the first program wins:

1. the **ffmpeg path** from the *Action cam* tab (file or folder),
2. the folder `propwash/ffmpeg/` in the game directory,
3. the folders on the game's `PATH`,
4. the usual install locations – even if they are not on the game's `PATH`:
   - macOS: Homebrew (`/opt/homebrew/bin`, `/usr/local/bin`, also `ffmpeg-full` and `ffmpeg@…` under `opt/`), MacPorts
     (`/opt/local/bin`), `~/bin`, `~/.local/bin`, Nix;
   - Windows: WinGet (links and package folders), Scoop, Chocolatey, `Program Files\ffmpeg\bin`;
   - Linux: `/usr/bin`, `/usr/local/bin`, Snap, Linuxbrew, `~/bin`, `~/.local/bin`, Nix;
5. on macOS and Linux finally your login shell (`command -v ffmpeg`), in case your `PATH` is only set there.

Point 4 is why it also works from a launcher: on a Mac, programs started from the Dock or a launcher do not inherit the
Terminal's `PATH` – but Homebrew is always in the same place.

## Your own path

If your ffmpeg is somewhere else, enter the path of the file or its folder in the **ffmpeg path** field – or drop the
file from your file manager onto the game window – and press **Test** (or `Enter`). Quotes around the path and `~` for
your home folder are fine. The status line then shows the version and path of exactly that program. If the path is wrong
it says so and Propwash uses the ffmpeg it found by itself. An empty field means: search automatically.

## If it does not work

- **Just installed, but “not found”:** press **Test**. If that does not help (Windows, your own folder on the `PATH`),
  quit the launcher completely and start it again – otherwise it hands the game the old `PATH` – or enter the path.
- **A downloaded ffmpeg “cannot be run” or “does not start” (macOS, Linux):** in the folder `propwash/ffmpeg/` Propwash
  makes the file executable itself; for your own path type `chmod +x ffmpeg` in the Terminal. On a Mac it must also not
  be quarantined (`xattr -d com.apple.quarantine ffmpeg`), and on Apple silicon an Intel build needs Rosetta. With
  Homebrew none of this applies.
- **Launcher as a Flatpak or Snap:** the game cannot see the system's programs. Put a static ffmpeg into
  `propwash/ffmpeg/` in the game directory.
- **“Hardware encoder failed”:** if the encoder fails in the middle of a recording, the video up to that point is kept
  and Propwash goes on with its own encoder – until you press **Test** or restart the game.
- **Encoder is set to “Propwash (CPU)”:** then ffmpeg stays unused; the section says so. Choose *Automatic*.
- **Laptop with two graphics cards:** NVENC only exists while the NVIDIA card is active; otherwise Propwash tries Quick
  Sync, AMF and finally Media Foundation.
