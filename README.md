# Control Vehicles

Desktop application (JavaFX + SQLite) to control a small fleet of vehicles in Spain and Portugal: every trip
the drivers declare, every refuel, what the fuel should have cost and what it really cost, and every
**divergence** between what is declared and what the odometer, Google Maps and the receipts say.

## What it does

| Screen | What for |
|---|---|
| **Trips** | Register a trip as the driver declares it: vehicle, driver, date, start and end time, origin and destination (Google Places autocomplete), **odometer at the start**, odometer at the end if it was noted, and tolls paid. Google Routes adds the real distance, driving time and estimated tolls; the price of a litre that day around the origin is added too. | The list keeps the essentials (when, vehicle, driver, route, km, time, price, issues); the trip selected shows every figure and each divergence in full in the panel below it.
| **Refuels** | Litres, amount, odometer and whether the tank was filled, typed or **imported from the Galp Frota statement**. Below, the consumption measured between two full tanks against the vehicle's nominal one. |
| **Divergences** | Everything that does not add up, the worst first (see below). |
| **Monthly report** | Per vehicle and per driver: odometer km vs Google km, real spend (refuels) vs expected cost (Google km × nominal consumption × price) and the cost by odometer km, tolls declared vs estimated. Exported to CSV for Excel. |
| **Drivers / Vehicles** | The fleet. One with trips is deactivated, never deleted. |
| **Fuel prices** | The stored daily averages per country, fuel and brand; fetch today's or type one by hand. |
| **Settings** | Google API key, divergence thresholds, price radius and brand, and **export / import of the database**. |

### Odometer: the start always, the end when it is noted

Drivers write the odometer when they start; the end is an optional field for the day they also note it.

- **Without the end**, the distance of a trip is the start of the **next** trip of the same vehicle minus its own
  start. Any use of the vehicle nobody registered between two trips falls inside that distance, and the
  *odometer vs Google* divergence brings it up, but it cannot be told apart from the trip itself. The latest trip
  of each vehicle shows *pending* until the next one is registered.
- **With the end**, the distance of the trip is end minus start (known at once, never *pending*), and the
  kilometres between its end and the next trip's start are **unrecorded use**: a column of their own in the trip
  list and the monthly report, and the *unrecorded use* divergence, attributed to the driver who had the vehicle
  last.

Nothing of this is stored: registering an older trip later, or fixing one, recalculates everything. Refuels are
another odometer reading: one that does not fit between the trips around it is flagged.

### Divergences

| Kind | When (defaults, editable in Settings) |
|---|---|
| Odometer vs Google distance | more than 10 % **and** more than 5 km (high above 20 %) |
| Declared vs Google duration | more than 30 % **and** more than 15 min (high if shorter than half Google's time) |
| Odometer not increasing | a trip starts below the previous one's end, or at or below its start when it has no end |
| Unrecorded use between trips | more than 3 km between a trip's noted end and the next start (high above 30 km) |
| Vehicle / driver in two trips at once | overlapping times |
| Declared vs Google tolls | more than 1 € apart, or tolls declared on a route without any |
| Refuel odometer out of sequence | the refuel's odometer is not between the trips around it |
| Refuel above tank capacity | more litres than the tank holds (+2 %) |
| Measured vs nominal consumption | more than 15 % between two full tanks |

### Importing the Galp Frota statement

*Refuels → Import Galp Frota statement…* reads a CSV (`;`, `,` or tab; UTF-8 or Windows-1252) or an `.xlsx`
(first sheet; an old `.xls` must be saved as `.xlsx` or CSV first) from any fuel card portal:

- **Columns are chosen at import time** — date, plate, litres and amount are required; time, odometer, station
  and product are optional. The usual Spanish, Portuguese and English headers are recognised, and the mapping
  used last time is proposed again.
- A **preview** says what each line will do before anything is stored: *Import*, *Already imported* (same
  vehicle, minute, litres and amount), *Unknown plate* (add the vehicle and import again), *Not fuel* (AdBlue,
  washes, shop, tolls…) or *Invalid* (with the reason). Plates match without spaces or dashes.
- Every imported refuel is **Galp and a full tank**; untick «Tank filled up» on the ones that were not.
- A line **without odometer** gets an **estimated** one — the last reading known before it from the trips and
  the refuels — shown as `~120,150 (est.)`. An estimated refuel never sets a consumption boundary (its litres
  still count) nor an odometer check; typing the real reading clears the mark.

### Completing pending trips

A trip saved while Google or the price service did not answer has no route or no price.
*Trips → Complete missing routes and prices (N)* asks for all of them in one go, keeping manual prices. If
Google does not answer at all or refuses the key, it is not asked again for the rest (the prices are still
completed), so a bad key does not fire one call per trip.

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

Every list has a **+** at the right of its header to hide or show columns.

## Running it

Requirements: JDK 21 or newer (it runs on 25 too). Maven comes with the wrapper (`mvnw` / `mvnw.cmd`).

### From IntelliJ

The repository brings shared run configurations (`.run/`), so they appear in the run selector:

| Configuration | What it does |
|---|---|
| **Control Vehicles** | Starts the application (`app.Launcher`) with your data in `~/.control-vehicles`. |
| **Control Vehicles (sandbox data)** | The same with the data in `.local-data/` of the project, to try things without touching the real data. |
| **javafx run** | `mvn javafx:run`. |
| **tests** | `mvn test`. |

Run `Launcher`, never `ControlVehiclesApp` directly: with JavaFX on the class path the main class must not extend
`Application`. The warning *Unsupported JavaFX configuration* at start-up is expected and harmless.

### As a jar

```bash
./mvnw package             # mvnw.cmd package on Windows
java -jar target/control-vehicles.jar
```

The jar carries the JavaFX of the operating system it was built on: build it on Windows for Windows. The computer
running it needs Java 21 or newer.

### Windows application without installing Java

`scripts\package-windows.cmd` (double-click it, or run it from a terminal; needs a JDK 21+, whose `jpackage` it
uses) builds the jar and packages it with its own Java into `target\installer`:

- with [WiX Toolset](https://wixtoolset.org/) on the PATH, an **.msi installer** with a Start menu entry and a
  desktop shortcut;
- without it, a **folder** `Control Vehicles` with `Control Vehicles.exe`: copy the whole folder to the other
  computer.

Each person's data lives in their own `C:\Users\<user>\.control-vehicles\`; hand the data over with
*Settings → Export database*. Start with `-Dcontrolvehicles.home=<folder>` (VM option) to use another folder.

### Google API key

1. In [Google Cloud Console](https://console.cloud.google.com) create a project and link a billing account
   (required, even within the monthly free usage; a budget alert in *Billing → Budgets & alerts* is a good idea).
2. *APIs & Services → Library*: enable **Places API (New)** (not the legacy «Places API») and **Routes API**.
3. *APIs & Services → Credentials → Create credentials → API key*.
4. Restrict the key: restriction type **API restrictions**, and tick only *Places API (New)* and *Routes API*.
   Not *Websites*, *Android* or *iOS* restrictions: this is a desktop application, and Google would refuse its
   calls. *IP addresses* only if the office has a fixed public IP.
5. In the application, *Settings → Google → API key*: paste it, *Save key*, then *Test the key*.

The key is stored only on that computer, in `~/.control-vehicles/settings.properties`, never in the database
that gets exported: each person uses their own. Calls per trip: an autocomplete session per new place (stored
places are reused without calling Google) and one route. Routes are asked without traffic, because trips are
registered after they happened.

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
