package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.ConsumptionSegment;
import com.alejandro.controlvehicles.domain.Refuel;
import com.alejandro.controlvehicles.domain.Vehicle;
import com.alejandro.controlvehicles.persistence.DriverRepository;
import com.alejandro.controlvehicles.persistence.RefuelRepository;
import com.alejandro.controlvehicles.persistence.VehicleRepository;

import java.util.Comparator;
import java.util.List;

/** Refuels, the facts behind the real spend and the measured consumption. */
public final class RefuelService {

    private final RefuelRepository refuels;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final AnalysisService analysis;

    public RefuelService(RefuelRepository refuels, VehicleRepository vehicles, DriverRepository drivers,
                         AnalysisService analysis) {
        this.refuels = refuels;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.analysis = analysis;
    }

    public List<Refuel> all() {
        return refuels.findAll().stream().sorted(Comparator.comparing(Refuel::refuelAt).reversed()).toList();
    }

    public Refuel save(Refuel refuel) {
        Vehicle vehicle = vehicles.findById(refuel.vehicleId())
                .orElseThrow(() -> new ValidationException("Choose a vehicle"));
        if (refuel.driverId() != null && drivers.findById(refuel.driverId()).isEmpty()) {
            throw new ValidationException("The driver no longer exists");
        }
        if (refuel.odometerKm() < vehicle.initialOdometerKm()) {
            throw new ValidationException("The odometer cannot be below the vehicle's initial "
                    + vehicle.initialOdometerKm() + " km");
        }
        return refuels.save(refuel);
    }

    public void delete(long id) {
        refuels.delete(id);
    }

    public List<ConsumptionSegment> consumption(long vehicleId) {
        return analysis.analyse().segments(vehicleId);
    }
}
