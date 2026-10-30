package com.alejandro.controlvehicles;

import com.alejandro.controlvehicles.domain.ConsumptionCalculator;
import com.alejandro.controlvehicles.domain.ConsumptionSegment;
import com.alejandro.controlvehicles.domain.CostCalculator;
import com.alejandro.controlvehicles.domain.Divergence;
import com.alejandro.controlvehicles.domain.DivergenceKind;
import com.alejandro.controlvehicles.domain.DivergenceRules;
import com.alejandro.controlvehicles.domain.DivergenceThresholds;
import com.alejandro.controlvehicles.domain.FleetAnalysis;
import com.alejandro.controlvehicles.domain.OdometerLedger;
import com.alejandro.controlvehicles.domain.RouteEstimate;
import com.alejandro.controlvehicles.domain.Severity;
import com.alejandro.controlvehicles.domain.TollStatus;
import com.alejandro.controlvehicles.domain.Trip;
import com.alejandro.controlvehicles.domain.Vehicle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.alejandro.controlvehicles.support.Fixtures.refuel;
import static com.alejandro.controlvehicles.support.Fixtures.trip;
import static com.alejandro.controlvehicles.support.Fixtures.vehicle;
import static com.alejandro.controlvehicles.support.Fixtures.withEnd;
import static com.alejandro.controlvehicles.support.Fixtures.withGoogle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainTest {

    private static final DivergenceThresholds RULES = DivergenceThresholds.DEFAULTS;

    @Test
    void theOdometerDistanceOfATripIsTheNextStartMinusItsOwnAndTheLastOneIsPending() {
        List<OdometerLedger.Entry> ledger = OdometerLedger.of(List.of(
                trip(3, 1, 1, "2026-10-03", "08:00", "09:00", 1300),
                trip(1, 1, 1, "2026-10-01", "08:00", "09:00", 1000),
                trip(2, 1, 1, "2026-10-02", "08:00", "09:00", 1120)));

        assertEquals(List.of(1L, 2L, 3L), ledger.stream().map(e -> e.trip().id()).toList());
        assertEquals(120, ledger.get(0).odometerKm());
        assertEquals(180, ledger.get(1).odometerKm());
        assertNull(ledger.get(2).odometerKm());
        assertTrue(ledger.get(2).pending());
    }

    @Test
    void anOlderTripRegisteredLaterSplitsThePreviousDistance() {
        List<OdometerLedger.Entry> before = OdometerLedger.of(List.of(
                trip(1, 1, 1, "2026-10-01", "08:00", "09:00", 1000),
                trip(2, 1, 1, "2026-10-03", "08:00", "09:00", 1300)));
        assertEquals(300, before.getFirst().odometerKm());

        List<OdometerLedger.Entry> after = OdometerLedger.of(List.of(
                trip(1, 1, 1, "2026-10-01", "08:00", "09:00", 1000),
                trip(2, 1, 1, "2026-10-03", "08:00", "09:00", 1300),
                trip(3, 1, 1, "2026-10-02", "08:00", "09:00", 1100)));
        assertEquals(100, after.get(0).odometerKm());
        assertEquals(200, after.get(1).odometerKm());
    }

    @Test
    void aStartThatDoesNotIncreaseHasNoDistanceAndIsADivergence() {
        List<OdometerLedger.Entry> ledger = OdometerLedger.of(List.of(
                trip(1, 1, 1, "2026-10-01", "08:00", "09:00", 1000),
                trip(2, 1, 1, "2026-10-02", "08:00", "09:00", 990)));

        assertNull(ledger.getFirst().odometerKm());
        Optional<Divergence> divergence = DivergenceRules.odometerSequence(ledger.get(1));
        assertTrue(divergence.isPresent());
        assertEquals(Severity.HIGH, divergence.get().severity());
        assertEquals(2L, divergence.get().tripId());
    }

    @Test
    void withAnEndOdometerTheTripHasItsOwnDistanceAndTheRestIsUnrecorded() {
        List<OdometerLedger.Entry> ledger = OdometerLedger.of(List.of(
                withEnd(trip(1, 1, 1, "2026-10-01", "08:00", "09:00", 1000), 1072),
                trip(2, 1, 1, "2026-10-02", "08:00", "09:00", 1150),
                withEnd(trip(3, 1, 1, "2026-10-03", "08:00", "09:00", 1300), 1340)));

        assertEquals(72, ledger.get(0).odometerKm());
        assertEquals(78, ledger.get(0).unrecordedKm());
        assertEquals(150, ledger.get(1).odometerKm(), "no end: up to the next start, as before");
        assertNull(ledger.get(1).unrecordedKm());
        assertEquals(40, ledger.get(2).odometerKm());
        assertFalse(ledger.get(2).pending(), "the last trip knows its distance when its end was noted");
        assertNull(ledger.get(2).unrecordedKm());
    }

    @Test
    void unrecordedUseIsADivergenceAboveTheReadingTolerance() {
        List<OdometerLedger.Entry> ledger = OdometerLedger.of(List.of(
                withEnd(trip(1, 1, 7, "2026-10-01", "08:00", "09:00", 1000), 1072),
                withEnd(trip(2, 1, 1, "2026-10-02", "08:00", "09:00", 1074), 1100),
                withEnd(trip(3, 1, 1, "2026-10-03", "08:00", "09:00", 1110), 1200),
                trip(4, 1, 1, "2026-10-04", "08:00", "09:00", 1260)));

        assertTrue(DivergenceRules.unrecordedUse(ledger.get(0), RULES).isEmpty(), "2 km is a reading error");
        Divergence warning = DivergenceRules.unrecordedUse(ledger.get(1), RULES).orElseThrow();
        assertEquals(Severity.WARNING, warning.severity());
        assertEquals("10 km unrecorded: ended at 1,100 km, the next trip (03/10/2026 08:00) started at 1,110 km",
                warning.detail());
        assertEquals(2L, warning.tripId(), "on the trip after which it happened");
        assertEquals(Severity.HIGH, DivergenceRules.unrecordedUse(ledger.get(2), RULES).orElseThrow().severity());
        assertEquals(7L, DivergenceRules.unrecordedUse(OdometerLedger.of(List.of(
                withEnd(trip(1, 1, 7, "2026-10-01", "08:00", "09:00", 1000), 1072),
                trip(2, 1, 1, "2026-10-02", "08:00", "09:00", 1090))).getFirst(), RULES).orElseThrow().driverId(),
                "the driver who had the vehicle last");
    }

    @Test
    void aStartBelowThePreviousEndIsADivergenceAndHasNoGap() {
        List<OdometerLedger.Entry> ledger = OdometerLedger.of(List.of(
                withEnd(trip(1, 1, 1, "2026-10-01", "08:00", "09:00", 1000), 1080),
                trip(2, 1, 1, "2026-10-02", "08:00", "09:00", 1050)));

        assertNull(ledger.getFirst().unrecordedKm());
        assertEquals(80, ledger.getFirst().odometerKm());
        assertEquals("Starts at 1,050 km, previous trip (01/10/2026 08:00) ended at 1,080 km",
                DivergenceRules.odometerSequence(ledger.get(1)).orElseThrow().detail());
    }

    @Test
    void anEndOdometerCannotBeBelowTheStart() {
        assertThrows(IllegalArgumentException.class,
                () -> withEnd(trip(1, 1, 1, "2026-10-01", "08:00", "09:00", 1000), 999));
    }

    @Test
    void aTripEndingBeforeItsStartTimeEndsTheNextDay() {
        Trip night = trip(1, 1, 1, "2026-10-01", "23:30", "01:15", 1000);
        assertEquals(Duration.ofMinutes(105), night.declaredDuration());
        assertEquals(night.date().plusDays(1), night.endAt().toLocalDate());
    }

    @Test
    void theDistanceRuleNeedsBothThePercentageAndTheKilometres() {
        Trip trip = withGoogle(trip(1, 1, 1, "2026-10-01", "08:00", "09:00", 1000), 70_000, 3600);

        assertTrue(DivergenceRules.distance(trip, 76, RULES).isEmpty(), "6 km is under 10 %");
        Trip shortTrip = withGoogle(trip, 10_000, 900);
        assertTrue(DivergenceRules.distance(shortTrip, 14, RULES).isEmpty(), "40 % but only 4 km");

        Divergence warning = DivergenceRules.distance(trip, 80, RULES).orElseThrow();
        assertEquals(Severity.WARNING, warning.severity());
        assertEquals("Odometer 80 km vs Google 70.0 km (+10.0 km, +14%)", warning.detail());
        assertEquals(Severity.HIGH, DivergenceRules.distance(trip, 140, RULES).orElseThrow().severity(),
                "an unregistered return trip doubles the distance");
        assertEquals(DivergenceKind.DISTANCE_MISMATCH,
                DivergenceRules.distance(trip, 40, RULES).orElseThrow().kind());
    }

    @Test
    void theDurationRuleFlagsWaitsAndImpossibleSpeeds() {
        Trip trip = trip(1, 1, 1, "2026-10-01", "08:00", "10:00", 1000);
        assertTrue(DivergenceRules.duration(withGoogle(trip, 70_000, 6600), RULES).isEmpty(), "120 vs 110 min");
        assertEquals(Severity.WARNING,
                DivergenceRules.duration(withGoogle(trip, 70_000, 3600), RULES).orElseThrow().severity());
        Trip fast = trip(1, 1, 1, "2026-10-01", "08:00", "08:25", 1000);
        assertEquals(Severity.HIGH,
                DivergenceRules.duration(withGoogle(fast, 70_000, 3600), RULES).orElseThrow().severity());
    }

    @Test
    void theTollRuleComparesWithGoogleAndToleratesCents() {
        Trip base = trip(1, 1, 1, "2026-10-01", "08:00", "09:00", 1000);
        Trip declared = new Trip(1L, 1, 1, base.date(), base.startTime(), base.endTime(), 1L, 2L, 1000, null,
                new BigDecimal("12.00"), null, null, null);
        Trip priced = declared.withRoute(new RouteEstimate(70_000, 3600, TollStatus.PRICED, new BigDecimal("7.45"),
                Instant.EPOCH));
        assertEquals("Declared 12.00 € vs Google 7.45 € of tolls",
                DivergenceRules.tolls(priced, RULES).orElseThrow().detail());
        Trip close = declared.withRoute(new RouteEstimate(70_000, 3600, TollStatus.PRICED, new BigDecimal("11.50"),
                Instant.EPOCH));
        assertTrue(DivergenceRules.tolls(close, RULES).isEmpty());
        Trip none = declared.withRoute(new RouteEstimate(70_000, 3600, TollStatus.NONE, null, Instant.EPOCH));
        assertEquals(Severity.INFO, DivergenceRules.tolls(none, RULES).orElseThrow().severity());
        Trip unpriced = declared.withRoute(new RouteEstimate(70_000, 3600, TollStatus.UNPRICED, null, Instant.EPOCH));
        assertTrue(DivergenceRules.tolls(unpriced, RULES).isEmpty());
    }

    @Test
    void overlappingTripsAreReportedOnTheLaterOne() {
        List<Divergence> found = DivergenceRules.overlaps(List.of(
                trip(1, 1, 1, "2026-10-01", "08:00", "10:00", 1000),
                trip(2, 1, 2, "2026-10-01", "09:30", "11:00", 1100),
                trip(3, 1, 3, "2026-10-01", "11:00", "12:00", 1200)), DivergenceKind.VEHICLE_OVERLAP);
        assertEquals(1, found.size());
        assertEquals(2L, found.getFirst().tripId());
    }

    @Test
    void consumptionIsMeasuredFromFullTankToFullTankCountingPartialRefuels() {
        List<ConsumptionSegment> segments = ConsumptionCalculator.segments(1, List.of(
                refuel(1, 1, "2026-10-01T10:00", 1000, "20", "30", false),
                refuel(2, 1, "2026-10-02T10:00", 1100, "40", "60", true),
                refuel(3, 1, "2026-10-05T10:00", 1400, "10", "15", false),
                refuel(4, 1, "2026-10-08T10:00", 1700, "32", "48", true)));

        assertEquals(1, segments.size());
        ConsumptionSegment segment = segments.getFirst();
        assertEquals(600, segment.km());
        assertEquals(0, new BigDecimal("42").compareTo(segment.litres()));
        assertEquals(new BigDecimal("7.00"), segment.litresPer100Km());

        Vehicle vehicle = vehicle(1, "6");
        Divergence divergence = DivergenceRules.consumption(segment, vehicle, RULES).orElseThrow();
        assertEquals(Severity.WARNING, divergence.severity());
        assertEquals(4L, divergence.refuelId());
        assertTrue(DivergenceRules.consumption(segment, vehicle(1, "6.5"), RULES).isEmpty());
    }

    @Test
    void aRefuelMustFitBetweenTheTripsAroundItAndInTheTank() {
        Trip before = trip(1, 1, 1, "2026-10-01", "08:00", "09:00", 1000);
        Trip after = trip(2, 1, 1, "2026-10-02", "08:00", "09:00", 1100);
        assertTrue(DivergenceRules.refuelSequence(refuel(1, 1, "2026-10-01T12:00", 1050, "30", "45", true),
                before, after).isEmpty());
        assertTrue(DivergenceRules.refuelSequence(refuel(1, 1, "2026-10-01T12:00", 1150, "30", "45", true),
                before, after).isPresent());
        assertTrue(DivergenceRules.refuelSequence(refuel(1, 1, "2026-10-01T12:00", 990, "30", "45", true),
                before, after).isPresent());
        assertEquals(DivergenceKind.REFUEL_OVER_CAPACITY, DivergenceRules.overCapacity(
                refuel(1, 1, "2026-10-01T12:00", 1050, "62", "93", true), vehicle(1, "6")).orElseThrow().kind());
        assertTrue(DivergenceRules.overCapacity(refuel(1, 1, "2026-10-01T12:00", 1050, "61", "93", true),
                vehicle(1, "6")).isEmpty(), "2 % margin for the filler neck");
    }

    @Test
    void theFleetAnalysisPutsEachDivergenceOnItsTrip() {
        Vehicle vehicle = vehicle(1, "6");
        FleetAnalysis analysis = FleetAnalysis.of(List.of(vehicle), List.of(
                withGoogle(trip(1, 1, 1, "2026-10-01", "08:00", "09:00", 1000), 70_000, 3600),
                withGoogle(trip(2, 1, 1, "2026-10-02", "08:00", "09:00", 1140), 70_000, 3600)),
                List.of(), RULES);

        assertEquals(140, analysis.trip(1).odometerKm());
        assertEquals(List.of(DivergenceKind.DISTANCE_MISMATCH),
                analysis.trip(1).divergences().stream().map(Divergence::kind).toList());
        assertTrue(analysis.trip(2).pending());
        assertTrue(analysis.trip(2).divergences().isEmpty());
    }

    @Test
    void costsAreKilometresTimesConsumptionTimesPrice() {
        assertEquals(new BigDecimal("6.13"), CostCalculator.fuelCost(new BigDecimal("70"), new BigDecimal("6"),
                new BigDecimal("1.459")));
        assertEquals(new BigDecimal("4.200"), CostCalculator.litres(new BigDecimal("70"), new BigDecimal("6")));
    }

    @Test
    void recordsRefuseWhatCannotBe() {
        assertThrows(IllegalArgumentException.class, () -> vehicle(1, "0"));
        assertThrows(IllegalArgumentException.class, () -> trip(1, 1, 1, "2026-10-01", "08:00", "09:00", -1));
        assertThrows(IllegalArgumentException.class,
                () -> refuel(1, 1, "2026-10-01T10:00", 1000, "0", "10", true));
        assertFalse(vehicle(1, "6").label().isBlank());
    }
}
