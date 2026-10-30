# CLAUDE.md

## Project

`control-vehicles`: JavaFX desktop application with an embedded SQLite database to control a vehicle fleet
(Spain and Portugal): trips declared by the drivers, refuels, fuel costs and the divergences between what is
declared and what the odometer, Google Maps and the receipts say. `README.md` is the functional reference.
It has nothing to do with the `mto-*` projects.

Code, UI and documentation in English.

## Commands

```bash
./mvnw test           # all tests, offline: Google, MITECO and DGEG are played by a local HTTP server with fixtures
./mvnw javafx:run     # the application (data in ~/.control-vehicles, or -Dcontrolvehicles.home=...)
./mvnw package        # target/control-vehicles.jar (shaded, for the OS it is built on)
```

Java 21 (`maven.compiler.release`), JavaFX 21, no `module-info`: the shaded jar starts from `app.Launcher`, which
does not extend `Application`.

## Rules that must not be broken

- **The start odometer is always noted, the end one only sometimes** (`trip.end_odometer_km`, optional, `V2`).
  Without an end, a trip's distance is the next trip's start minus its own; with it, end minus start, and the
  kilometres up to the next start are unrecorded use (`OdometerLedger`). Distances are always derived, never
  stored. Everything derived (distances, unrecorded km, consumption, divergences) comes from `FleetAnalysis`,
  computed over all trips and refuels each time.
- A refuel imported without odometer has an **estimated** one (`refuel.odometer_estimated`, `V3`, from
  `OdometerEstimator`): it is never a consumption boundary (its litres still count) and never checked against the
  trips; estimates never feed other estimates or the odometer proposed for a new trip.
- The statement importer (`service/imports`) reads CSV and .xlsx with the JDK alone: no Apache POI on purpose.
  Columns are mapped by header name (`RefuelImportMapping`, saved in `app_setting`), never by position.
  Parsing of numbers, times and dates is `service.Inputs`, shared by the screens and the importer.
- `domain` has no JDBC, JavaFX or HTTP; its rules are pure and tested in `DomainTest`.
- Money and litres are `BigDecimal`, stored as TEXT; dates as ISO TEXT. Google distances are kept in metres and
  turned into exact km (`RouteEstimate.distanceKm`); only the screens round.
- A schema change is a new `src/main/resources/db/migration/V<n>__*.sql` appended to `MigrationRunner.MIGRATIONS`:
  an exported database from an older version is migrated when imported.
- The Google API key lives in `settings.properties`, never in the database (the database is what gets shared).
  Rules and thresholds live in the database (`app_setting`) so the recipient sees the same divergences.
- A connection is opened per operation; nothing keeps the file open, so export (`VACUUM INTO`) and import
  (replace the file after a backup) work while the application runs.
- Network calls never run on the JavaFX thread (`ui.Background`, `ui.EditorDialog`). A failing Google or price
  service never prevents saving a trip: it is saved with warnings and can be recalculated.
- The font is JetBrains Mono, bundled in `src/main/resources/.../ui/fonts` (OFL) and registered by
  `AppFonts.load()` before the first scene. Every scene and every dialog gets `app.css` through
  `AppFonts.style(...)`: a dialog has its own scene and would otherwise fall back to the default font.
- External endpoints go through `AppContext.Endpoints` so tests point them at `support.FakeHttpServer`.

## Tests

`DomainTest` (rules), `PersistenceTest` (SQLite on a temp file), `ClientsTest` (Google, MITECO, DGEG against
fixtures in `src/test/resources/fixtures`), `ServicesTest` (the whole `AppContext` against the fake server:
saving trips, prices and their fallbacks, the monthly report, CSV, export/import), `ImportTest` (CSV and .xlsx
reading, the column guess and the import rules), `InputsTest` (typed numbers, times and dates). Screens are not unit-tested.
