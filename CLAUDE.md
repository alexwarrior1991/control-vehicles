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

- **Only the start odometer is stored.** A trip's distance is the next trip's start minus its own
  (`OdometerLedger`), always derived, never stored. Everything derived (distances, consumption, divergences)
  comes from `FleetAnalysis`, computed over all trips and refuels each time.
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
- External endpoints go through `AppContext.Endpoints` so tests point them at `support.FakeHttpServer`.

## Tests

`DomainTest` (rules), `PersistenceTest` (SQLite on a temp file), `ClientsTest` (Google, MITECO, DGEG against
fixtures in `src/test/resources/fixtures`), `ServicesTest` (the whole `AppContext` against the fake server:
saving trips, prices and their fallbacks, the monthly report, CSV, export/import), `InputsTest` (typed numbers
and times). Screens are not unit-tested.
