# Control Vehicles

Desktop application (JavaFX + SQLite) to control a small fleet of vehicles in Spain and Portugal: every trip
the drivers declare, every refuel, what the fuel should have cost and what it really cost, and every
**divergence** between what is declared and what the odometer, Google Maps and the receipts say.

## What it does

| Screen | What for |
|---|---|
| **Trips** | Register a trip as the driver declares it: vehicle, driver, date, start and end time, origin and destination (Google Places autocomplete), **odometer at the start** and tolls paid. Google Routes adds the real distance, driving time and estimated tolls; the price of a litre that day around the origin is added too. |
| **Refuels** | Litres, amount, odometer and whether the tank was filled. Below, the consumption measured between two full tanks against the vehicle's nominal one. |
| **Divergences** | Everything that does not add up, the worst first (see below). |
| **Monthly report** | Per vehicle and per driver: odometer km vs Google km, real spend (refuels) vs expected cost (Google km × nominal consumption × price) and the cost by odometer km, tolls declared vs estimated. Exported to CSV for Excel. |
| **Drivers / Vehicles** | The fleet. One with trips is deactivated, never deleted. |
| **Fuel prices** | The stored daily averages per country, fuel and brand; fetch today's or type one by hand. |
| **Settings** | Google API key, divergence thresholds, price radius and brand, and **export / import of the database**. |

### Odometer: only the start is noted

Drivers write the odometer when they start, not when they finish. The distance of a trip is therefore the start
of the **next** trip of the same vehicle minus its own start, computed every time (never stored): registering an
older trip later, or fixing one, recalculates everything. The latest trip of each vehicle shows *pending*.

The consequence is useful: any use of the vehicle nobody registered between two trips falls inside the odometer
distance of the earlier one, and the *odometer vs Google* divergence brings it up. Refuels are another odometer
reading: one that does not fit between the trips around it is flagged.

### Divergences

| Kind | When (defaults, editable in Settings) |
|---|---|
| Odometer vs Google distance | more than 10 % **and** more than 5 km (high above 20 %) |
| Declared vs Google duration | more than 30 % **and** more than 15 min (high if shorter than half Google's time) |
| Odometer not increasing | a trip starts at or below the previous one's start |
| Vehicle / driver in two trips at once | overlapping times |
| Declared vs Google tolls | more than 1 € apart, or tolls declared on a route without any |
| Refuel odometer out of sequence | the refuel's odometer is not between the trips around it |
| Refuel above tank capacity | more litres than the tank holds (+2 %) |
| Measured vs nominal consumption | more than 15 % between two full tanks |

### Fuel prices

- **Spain**: the Ministry's open service (MITECO, *Precios de carburantes*), no key needed, also for past days.
- **Portugal**: DGEG (*Preços dos Combustíveis Online*), today's prices only. Its API is the one its website
  uses, not a documented one; if it changes, only `PortugalDgegProvider` changes.

The price of a trip is the average of the **Galp** stations within 25 km of its origin on its day (gasoline 95
or diesel, by vehicle); with no Galp around, every brand around; with no answer, the stored country average
closest to that day; failing that, the trip asks for a price by hand. Every station query also stores the
country average of the day (all brands and Galp), which builds the history Portugal does not provide.

### Sharing the data

*Settings → Export database* writes one `.db` file with everything (drivers, vehicles, places, trips, refuels,
prices and rules). The other person installs the application and uses *Import database*: the file is checked
(it must be a Control Vehicles database, not from a newer version), their current data is backed up next to it
and replaced. The **Google API key is not in the database**: it lives in `settings.properties` on each computer.

## Running it

Requirements: JDK 21 or newer (it runs on 25 too). Maven comes with the wrapper.

```bash
./mvnw javafx:run          # run from the sources
./mvnw test                # tests (no network, no Google key needed)
./mvnw package             # target/control-vehicles.jar, a single jar for this operating system
java -jar target/control-vehicles.jar
```

The jar carries the JavaFX of the operating system it was built on: build it on Windows for Windows. For an
installer with its own Java, from that jar:

```bash
jpackage --name "Control Vehicles" --input target --main-jar control-vehicles.jar --type msi --win-menu --win-shortcut
```

Data lives in `~/.control-vehicles/` (`control-vehicles.db` and `settings.properties`); start with
`-Dcontrolvehicles.home=<folder>` to use another one.

### Google API key

In Google Cloud, create a project with billing, enable **Places API (New)** and **Routes API**, create an API key
(restricted to those two APIs) and paste it in *Settings → Google*, then *Test the key*. Calls per trip: an
autocomplete session per new place (stored places are reused without calling Google) and one route. Routes are
asked without traffic, because trips are registered after they happened.

## Structure

```
src/main/java/com/alejandro/controlvehicles/
  app/          Launcher, ControlVehiclesApp, AppContext (manual wiring)
  domain/       records and pure rules: OdometerLedger, DivergenceRules, ConsumptionCalculator, FleetAnalysis…
  persistence/  SQLite with plain JDBC, MigrationRunner (db/migration/V<n>__*.sql), one repository per table
  integration/  google/ (Places, Routes) and fuel/ (MITECO, DGEG) over java.net.http
  service/      trips, refuels, prices, analysis, monthly report, CSV, settings, database export/import
  ui/           JavaFX screens built in code (no FXML), dialogs and helpers
```

The interface uses **JetBrains Mono**, bundled in the jar (`ui/fonts/`, SIL Open Font License, `OFL.txt` next to
it), so it looks the same on a computer that does not have the font installed.
