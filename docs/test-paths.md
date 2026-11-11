# Tests nach dem Klon: Pfade, Golden-Dateien, Plattformen

Für alle, die JUnit-Tests schreiben oder nach einem frischen Klon laufen lassen. Die Tests in `common/src/test` hängen
nicht vom Arbeitsverzeichnis ab und laufen auf jedem Betriebssystem mit Java 25. Technik und Entscheidungen:
[`design/0.6.0/addon-api-namespace.md`](design/0.6.0/addon-api-namespace.md) §3.18 und §6.4.

## 1. Dateien im Repository finden

Die Test-Hilfe `de.lardtom.propwash.testutil.TestPaths` liefert absolute Pfade:

| Aufruf | Ergebnis |
|---|---|
| `TestPaths.root()` | Wurzel des Repositorys |
| `TestPaths.common()` | `common/` |
| `TestPaths.lang("guide")` | `common/src/lang/guide/`; fehlt der Ordner, gibt es eine Ausnahme mit dem Pfad |
| `TestPaths.mainResources()` | `common/src/main/resources/` |
| `TestPaths.testResources()` | `common/src/test/resources/` (oder der Ordner aus `propwash.testResources`) |

Die Wurzel kommt aus der Systemeigenschaft `propwash.rootDir`. Fehlt sie, sucht `TestPaths` vom Arbeitsverzeichnis und
danach vom Ordner der Testklassen nach oben bis zu einem Ordner mit `settings.gradle` und
`common/src/main/java/de/lardtom/propwash`. Ein anderes Gradle-Projekt darüber (zum Beispiel ein Add-on) gilt nicht
als Wurzel. Wird keine gefunden, nennt die Ausnahme die Startordner und die Eigenschaft.

Der Test-Task in `common/build.gradle` setzt beide Eigenschaften:

```groovy
systemProperty 'propwash.rootDir', rootDir.absolutePath
systemProperty 'propwash.testResources', file('src/test/resources').absolutePath
```

Neue Tests verwenden `TestPaths` statt `Path.of("common/…")` oder `Path.of("src/…")`.

## 2. Golden-Dateien und Testdaten

- Lesen: `TestPaths.readResource(name)`, `resourceLines(name)` und `requireLines(name)` lesen über den Klassenpfad und
  sonst aus `common/src/test/resources`. `requireLines` bricht mit dem Namen der Datei ab, wenn sie fehlt.
- Schreiben: `GoldenFile.verify` (`AirframeGoldenTest`, `BuiltinRegressionTest`) schreibt eine Golden-Datei nur, wenn
  `propwash.testResources` gesetzt ist, und der Test schlägt danach trotzdem fehl („nur einchecken, wenn die Änderung
  gewollt ist“). Ohne die Eigenschaft schreibt kein Test in das Repository. `./gradlew :common:test` setzt sie selbst,
  mit der Schnellprüfung so:

```sh
JAVA_TOOL_OPTIONS="-Dpropwash.testResources=$PWD/common/src/test/resources" \
    tools/fastcheck.sh test --select-class de.lardtom.propwash.sim.AirframeGoldenTest
```

- Fehlt die Datei, entsteht sie neu (nur auf aarch64). Weichen Werte ab, schreibt erst `-Dpropwash.goldenUpdate=true`
  die Datei neu; ohne den Schalter nennt die Meldung die geänderten Schlüssel und die Architektur.

### 2.1 Zwei Spalten: aarch64 und x86_64

Das Flugmodell nutzt `Math.sin`, `Math.exp` usw. Die JVM rechnet sie auf x86_64 mit anderen Routinen als auf aarch64, im
letzten Bit können die Ergebnisse abweichen. Eine Zeile einer Golden-Datei ist deshalb

```text
<schlüssel> <wert> [<wert für x86_64>]
```

- Die erste Spalte gilt für aarch64 (`os.arch` `aarch64` oder `arm64`) und für x86_64, wenn die zweite fehlt.
- Die zweite Spalte steht nur dort, wo x86_64 (`os.arch` `x86_64` oder `amd64`) anders rechnet. Stand 0.6.0: 68 der
  158 Zeilen von `builtin_regression.txt` (alle `angle:`, `drop:` und `worn:`, 18 von 22 `acro:`); `analysis:`,
  `preset:`, `full:`, `full-thermal:`, `wear:` und `airframe_golden.txt` sind auf beiden gleich.
- Andere Architekturen prüfen nur die Schlüssel und überspringen den Bit-Vergleich mit Meldung (JUnit: abgebrochen).
- Neue Werte: zuerst auf aarch64 mit `-Dpropwash.goldenUpdate=true` (schreibt die erste Spalte, behält die zweite nur
  für unveränderte Werte), danach dieselben Tests mit einem x86_64-JDK 25 unter Rosetta 2 und
  `-Dpropwash.goldenUpdate=true -Dpropwash.testResources=<repo>/common/src/test/resources` (schreibt nur die zweite
  Spalte, die erste bleibt). Ändern sich die Schlüssel, bricht der x86_64-Lauf ab und verlangt erst den Lauf auf
  aarch64.

## 3. Betriebssystem

- `LoginShell.parse` erkennt die Antwort der Anmelde-Shell (`command -v ffmpeg`) am Text: ein Pfad, der mit `/` beginnt
  und auf den Programmnamen endet. Anmelde-Shells gibt es nur unter Unix; unter Windows prüft der Test dieselben Zeilen
  und kommt zum selben Ergebnis (`FfmpegDetectionTest`).
- Tests, die Shell-Skripte starten, laufen nur unter Unix (`Assumptions`).

## 4. Klon-Probe

```sh
git clone --branch <zweig> <repo> /tmp/pw-clone
cd /tmp/pw-clone && JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew test --no-daemon
```

Grün bedeutet: kein Test braucht Dateien außerhalb des Repositorys oder gebaute Dateien aus einem anderen Arbeitsbaum.

Bit-Vergleich auf x86_64: dieselben Testklassen mit einem x86_64-JDK 25 unter Rosetta 2 starten (JUnit-Konsole oder
`org.gradle.java.home`), Ergebnis siehe §2.1. Aus einem fremden Arbeitsverzeichnis (JUnit-Konsole ohne Gradle) bestehen
alle Tests, die `TestPaths` nutzen; Tests mit eigener Pfadsuche (`Path.of("common/…")`, `Path.of("src/…")`, `Path.of(".")`)
brauchen noch die Wurzel oder `common/` als Arbeitsverzeichnis.
