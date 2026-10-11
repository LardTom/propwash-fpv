# Contributing to Propwash FPV

Thanks for helping! Propwash FPV is source-available: you can read the code, build it and send pull requests. Bug
fixes, translations, performance work and new ideas are all welcome.

## Before you start

- For anything bigger than a small fix, open an issue first and describe what you want to change. This matters most
  for the flight model, the network protocol, save formats and the add-on API, because they affect every player,
  server and add-on.
- Every pull request needs the contributor agreement: tick the box in the pull request template, see
  [`CLA.md`](CLA.md). It is short and you keep the copyright in your work.
- Contributions are licensed like the files they change: the core under the PolyForm Shield License 1.0.0
  ([`LICENSE`](LICENSE)), the add-on API, the example add-on and the documentation under the MIT License
  ([`LICENSE-MIT`](LICENSE-MIT)).

## Building and testing

You need a Java 25 JDK (set `JAVA_HOME` if your default Java is older).

| Command | What it does |
|---|---|
| `./gradlew build` | builds both loaders and runs the JUnit tests; jars in `fabric/build/libs/` and `neoforge/build/libs/` |
| `./gradlew :common:test` | JUnit tests only |
| `./gradlew :fabric:runClient`, `:neoforge:runClient` | development client (starts muted) |
| `./gradlew :fabric:runClientSelftest`, `:neoforge:runClientSelftest` | the in-game self-test; must stay green |
| `./gradlew :fabric:runClientSmoke`, `:fabric:runServerSmoke` (same for `:neoforge`) | start-up smoke tests for client and server |

Run only one Minecraft instance at a time. Development clients play offline as `LardTom`; pass
`-PpropwashDevName=<your name>` to use your own name and skin, or `-PpropwashDevName=` for the default dev name.

## Code rules

The full rules are in [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) (German). In short:

- **No comments in mod code** (`common/src`, `fabric/src`, `neoforge/src`, tests included). Explanations belong in
  `docs/`, in commit messages or in good names. `python3 tools/strip_comments.py` removes leftovers.
- One package per feature (`de.lardtom.propwash.<feature>`, client code in `<feature>.client`); all mixins live in
  `de.lardtom.propwash.mixin.<feature>` with one mixin config per feature.
- Classes, methods and fields in English, records for immutable data, no wildcard imports.
- Only real Minecraft 26.3, Fabric and NeoForge APIs. Client classes are never loaded from server code.
- Text that players see goes through the language files in `common/src/lang/<module>/` (`en_us.json` and
  `de_de.json`); `:common:mergeLang` merges them into the generated files. If you do not speak German, put the
  English text into both files and say so in the pull request.
- The flight model must not change by accident: the motion goldens and the calibration have to stay identical unless
  the change was agreed in an issue.
- The add-on API (`de.lardtom.propwash.api`) has its own version (`PropwashApi.API_VERSION`): additions raise the
  minor version, nothing may break existing add-ons. Changes for add-on authors go into
  [`docs/api/changes.md`](docs/api/changes.md). Changes to synced data raise the network protocol version.
- Only your own work or material under a compatible license; name third-party material and its license in the pull
  request and in [`NOTICE`](NOTICE).
- Never commit build outputs, run directories, personal data or local paths.

## Documentation

The developer documentation is in German: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (modules and packages),
[`docs/FOUNDATION.md`](docs/FOUNDATION.md) (registration, networking, events, self-test, contracts),
[`docs/ASSETS.md`](docs/ASSETS.md) (models and textures) and [`docs/design/`](docs/design/). Issues and pull requests
in English are welcome.
