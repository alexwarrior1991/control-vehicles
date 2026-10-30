package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.Driver;
import com.alejandro.controlvehicles.domain.Vehicle;
import com.alejandro.controlvehicles.persistence.DriverRepository;
import com.alejandro.controlvehicles.persistence.VehicleRepository;

import java.util.List;

/**
 * Drivers and vehicles. One that already has trips or refuels is not deleted but deactivated: it stays in the
 * reports and leaves the lists to choose from.
 */
public final class FleetService {

    private final DriverRepository drivers;
    private final VehicleRepository vehicles;

    public FleetService(DriverRepository drivers, VehicleRepository vehicles) {
        this.drivers = drivers;
        this.vehicles = vehicles;
    }

    public List<Driver> drivers() {
        return drivers.findAll();
    }

    public List<Driver> activeDrivers() {
        return drivers.findAll().stream().filter(Driver::active).toList();
    }

    public Driver save(Driver driver) {
        return drivers.save(driver);
    }

    public void deleteDriver(long id) {
        drivers.delete(id);
    }

    public Driver setActive(Driver driver, boolean active) {
        return drivers.save(driver.withActive(active));
    }

    public List<Vehicle> vehicles() {
        return vehicles.findAll();
    }

    public List<Vehicle> activeVehicles() {
        return vehicles.findAll().stream().filter(Vehicle::active).toList();
    }

    public Vehicle save(Vehicle vehicle) {
        return vehicles.save(vehicle);
    }

    public void deleteVehicle(long id) {
        vehicles.delete(id);
    }

    public Vehicle setActive(Vehicle vehicle, boolean active) {
        return vehicles.save(vehicle.withActive(active));
    }
}
