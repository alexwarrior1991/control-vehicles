package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.FleetAnalysis;
import com.alejandro.controlvehicles.persistence.RefuelRepository;
import com.alejandro.controlvehicles.persistence.TripRepository;
import com.alejandro.controlvehicles.persistence.VehicleRepository;

import java.util.function.Supplier;

/** Runs {@link FleetAnalysis} over everything stored, with the thresholds in force. */
public final class AnalysisService {

    private final VehicleRepository vehicles;
    private final TripRepository trips;
    private final RefuelRepository refuels;
    private final Supplier<AppSettings> settings;

    public AnalysisService(VehicleRepository vehicles, TripRepository trips, RefuelRepository refuels,
                           Supplier<AppSettings> settings) {
        this.vehicles = vehicles;
        this.trips = trips;
        this.refuels = refuels;
        this.settings = settings;
    }

    public FleetAnalysis analyse() {
        return FleetAnalysis.of(vehicles.findAll(), trips.findAll(), refuels.findAll(), settings.get().thresholds());
    }
}
