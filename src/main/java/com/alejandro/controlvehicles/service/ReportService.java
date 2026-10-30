package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.ConsumptionSegment;
import com.alejandro.controlvehicles.domain.CostCalculator;
import com.alejandro.controlvehicles.domain.Divergence;
import com.alejandro.controlvehicles.domain.FleetAnalysis;
import com.alejandro.controlvehicles.domain.Refuel;
import com.alejandro.controlvehicles.domain.TollStatus;
import com.alejandro.controlvehicles.domain.Trip;
import com.alejandro.controlvehicles.domain.Vehicle;
import com.alejandro.controlvehicles.persistence.DriverRepository;
import com.alejandro.controlvehicles.persistence.RefuelRepository;
import com.alejandro.controlvehicles.persistence.TripRepository;
import com.alejandro.controlvehicles.persistence.VehicleRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The monthly costs, per vehicle and per driver; a trip belongs to the month of its date. */
public final class ReportService {

    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final TripRepository trips;
    private final RefuelRepository refuels;
    private final AnalysisService analysis;

    public ReportService(VehicleRepository vehicles, DriverRepository drivers, TripRepository trips,
                         RefuelRepository refuels, AnalysisService analysis) {
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.trips = trips;
        this.refuels = refuels;
        this.analysis = analysis;
    }

    public MonthlyReport monthly(YearMonth month) {
        FleetAnalysis fleet = analysis.analyse();
        Map<Long, Vehicle> vehiclesById = TripService.byId(vehicles.findAll(), Vehicle::id);
        Map<Long, Accumulator> byVehicle = new LinkedHashMap<>();
        Map<Long, Accumulator> byDriver = new LinkedHashMap<>();
        Map<Long, String> driverNames = new LinkedHashMap<>();
        drivers.findAll().forEach(d -> driverNames.put(d.id(), d.name()));
        vehiclesById.values().forEach(v -> byVehicle.put(v.id(), new Accumulator(v.label(),
                v.nominalConsumptionL100())));

        for (Trip trip : trips.findAll()) {
            if (!YearMonth.from(trip.date()).equals(month)) {
                continue;
            }
            Vehicle vehicle = vehiclesById.get(trip.vehicleId());
            FleetAnalysis.TripAnalysis analysed = fleet.trip(trip.id());
            byVehicle.get(trip.vehicleId()).addTrip(trip, analysed, vehicle);
            byDriver.computeIfAbsent(trip.driverId(), id -> new Accumulator(driverNames.get(id), null))
                    .addTrip(trip, analysed, vehicle);
        }
        for (Refuel refuel : refuels.findAll()) {
            if (!YearMonth.from(refuel.refuelAt()).equals(month)) {
                continue;
            }
            byVehicle.get(refuel.vehicleId()).addRefuel(refuel);
            if (refuel.driverId() != null) {
                byDriver.computeIfAbsent(refuel.driverId(), id -> new Accumulator(driverNames.get(id), null))
                        .addRefuel(refuel);
            }
        }
        for (ConsumptionSegment segment : fleet.allSegments()) {
            if (YearMonth.from(segment.to().refuelAt()).equals(month)) {
                byVehicle.get(segment.vehicleId()).addSegment(segment);
            }
        }
        for (Divergence divergence : fleet.divergences()) {
            if (!YearMonth.from(divergence.date()).equals(month)) {
                continue;
            }
            if (divergence.vehicleId() != null && byVehicle.containsKey(divergence.vehicleId())) {
                byVehicle.get(divergence.vehicleId()).divergences++;
            }
            if (divergence.driverId() != null && byDriver.containsKey(divergence.driverId())) {
                byDriver.get(divergence.driverId()).divergences++;
            }
        }

        List<MonthlyReport.Line> vehicleLines = byVehicle.values().stream().filter(Accumulator::hasActivity)
                .map(Accumulator::line).toList();
        List<MonthlyReport.Line> driverLines = byDriver.values().stream().filter(Accumulator::hasActivity)
                .map(Accumulator::line).toList();
        Accumulator total = new Accumulator("Total", null);
        byVehicle.values().forEach(total::merge);
        return new MonthlyReport(month, vehicleLines, driverLines, total.line());
    }

    private static final class Accumulator {
        private final String name;
        private final BigDecimal nominalL100;
        private int trips;
        private int pending;
        private int withoutPrice;
        private int odometerKm;
        private BigDecimal googleKm = BigDecimal.ZERO;
        private BigDecimal gapKm = BigDecimal.ZERO;
        private BigDecimal litres = BigDecimal.ZERO;
        private BigDecimal spend = BigDecimal.ZERO;
        private BigDecimal expected = BigDecimal.ZERO;
        private BigDecimal odometerCost = BigDecimal.ZERO;
        private BigDecimal tollsDeclared = BigDecimal.ZERO;
        private BigDecimal tollsEstimated = BigDecimal.ZERO;
        private int segmentKm;
        private BigDecimal segmentLitres = BigDecimal.ZERO;
        private int divergences;
        private int refuels;

        Accumulator(String name, BigDecimal nominalL100) {
            this.name = name == null ? "?" : name;
            this.nominalL100 = nominalL100;
        }

        void addTrip(Trip trip, FleetAnalysis.TripAnalysis analysed, Vehicle vehicle) {
            trips++;
            tollsDeclared = tollsDeclared.add(trip.tollsPaid());
            Integer odometer = analysed == null ? null : analysed.odometerKm();
            if (odometer == null) {
                pending++;
            } else {
                odometerKm += odometer;
            }
            if (trip.route() != null) {
                googleKm = googleKm.add(trip.route().distanceKm());
                if (odometer != null) {
                    gapKm = gapKm.add(BigDecimal.valueOf(odometer).subtract(trip.route().distanceKm()));
                }
                if (trip.route().tollStatus() == TollStatus.PRICED) {
                    tollsEstimated = tollsEstimated.add(trip.route().tollEstimate());
                }
            }
            if (trip.price() == null) {
                withoutPrice++;
                return;
            }
            BigDecimal price = trip.price().pricePerLitre();
            if (trip.route() != null) {
                expected = expected.add(CostCalculator.fuelCost(trip.route().distanceKm(),
                        vehicle.nominalConsumptionL100(), price));
            }
            if (odometer != null) {
                odometerCost = odometerCost.add(CostCalculator.fuelCost(BigDecimal.valueOf(odometer),
                        vehicle.nominalConsumptionL100(), price));
            }
        }

        void addRefuel(Refuel refuel) {
            refuels++;
            litres = litres.add(refuel.litres());
            spend = spend.add(refuel.totalAmount());
        }

        void addSegment(ConsumptionSegment segment) {
            segmentKm += segment.km();
            segmentLitres = segmentLitres.add(segment.litres());
        }

        void merge(Accumulator other) {
            trips += other.trips;
            pending += other.pending;
            withoutPrice += other.withoutPrice;
            odometerKm += other.odometerKm;
            googleKm = googleKm.add(other.googleKm);
            gapKm = gapKm.add(other.gapKm);
            litres = litres.add(other.litres);
            spend = spend.add(other.spend);
            expected = expected.add(other.expected);
            odometerCost = odometerCost.add(other.odometerCost);
            tollsDeclared = tollsDeclared.add(other.tollsDeclared);
            tollsEstimated = tollsEstimated.add(other.tollsEstimated);
            segmentKm += other.segmentKm;
            segmentLitres = segmentLitres.add(other.segmentLitres);
            divergences += other.divergences;
            refuels += other.refuels;
        }

        boolean hasActivity() {
            return trips > 0 || refuels > 0;
        }

        MonthlyReport.Line line() {
            BigDecimal measured = segmentKm == 0 ? null : segmentLitres.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(segmentKm), 2, RoundingMode.HALF_UP);
            return new MonthlyReport.Line(name, trips, pending, withoutPrice, odometerKm, googleKm, gapKm,
                    nominalL100, measured, litres, spend, expected, odometerCost, tollsDeclared, tollsEstimated,
                    divergences);
        }
    }
}
