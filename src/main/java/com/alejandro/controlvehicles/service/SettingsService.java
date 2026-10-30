package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.DivergenceThresholds;
import com.alejandro.controlvehicles.persistence.SettingRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Two kinds of settings: the rules ({@link AppSettings}) in the database, and the Google API key in a local file
 * next to it ({@code settings.properties}), which is never exported: the key is personal and billed.
 */
public final class SettingsService {

    private static final String API_KEY = "google.apiKey";

    private final SettingRepository repository;
    private final Path localFile;

    public SettingsService(SettingRepository repository, Path localFile) {
        this.repository = repository;
        this.localFile = localFile;
    }

    public AppSettings load() {
        Map<String, String> values = repository.findAll();
        DivergenceThresholds d = DivergenceThresholds.DEFAULTS;
        DivergenceThresholds thresholds = new DivergenceThresholds(
                integer(values, "divergence.distancePercent", d.distancePercent()),
                integer(values, "divergence.distanceMinKm", d.distanceMinKm()),
                integer(values, "divergence.durationPercent", d.durationPercent()),
                integer(values, "divergence.durationMinMinutes", d.durationMinMinutes()),
                integer(values, "divergence.consumptionPercent", d.consumptionPercent()),
                values.containsKey("divergence.tollToleranceEur")
                        ? new BigDecimal(values.get("divergence.tollToleranceEur")) : d.tollToleranceEur(),
                integer(values, "divergence.unrecordedMinKm", d.unrecordedMinKm()));
        AppSettings defaults = AppSettings.DEFAULTS;
        return new AppSettings(thresholds, integer(values, "price.radiusKm", defaults.priceRadiusKm()),
                values.getOrDefault("price.preferredBrand", defaults.preferredBrand()),
                Country.fromIsoCode(values.get("price.defaultCountry")).orElse(defaults.defaultCountry()));
    }

    public void save(AppSettings settings) {
        DivergenceThresholds t = settings.thresholds();
        Map<String, String> values = new HashMap<>();
        values.put("divergence.distancePercent", String.valueOf(t.distancePercent()));
        values.put("divergence.distanceMinKm", String.valueOf(t.distanceMinKm()));
        values.put("divergence.durationPercent", String.valueOf(t.durationPercent()));
        values.put("divergence.durationMinMinutes", String.valueOf(t.durationMinMinutes()));
        values.put("divergence.consumptionPercent", String.valueOf(t.consumptionPercent()));
        values.put("divergence.tollToleranceEur", t.tollToleranceEur().toPlainString());
        values.put("divergence.unrecordedMinKm", String.valueOf(t.unrecordedMinKm()));
        values.put("price.radiusKm", String.valueOf(settings.priceRadiusKm()));
        values.put("price.preferredBrand", settings.preferredBrand() == null ? "" : settings.preferredBrand());
        values.put("price.defaultCountry", settings.defaultCountry().name());
        repository.saveAll(values);
    }

    public String googleApiKey() {
        return localProperties().getProperty(API_KEY, "").trim();
    }

    public void saveGoogleApiKey(String key) {
        Properties properties = localProperties();
        properties.setProperty(API_KEY, key == null ? "" : key.trim());
        try {
            Files.createDirectories(localFile.getParent());
            try (OutputStream out = Files.newOutputStream(localFile)) {
                properties.store(out, "control-vehicles local settings (not exported with the database)");
            }
        } catch (IOException e) {
            throw new ValidationException("Cannot write " + localFile + ": " + e.getMessage());
        }
    }

    private Properties localProperties() {
        Properties properties = new Properties();
        if (Files.exists(localFile)) {
            try (InputStream in = Files.newInputStream(localFile)) {
                properties.load(in);
            } catch (IOException e) {
                throw new ValidationException("Cannot read " + localFile + ": " + e.getMessage());
            }
        }
        return properties;
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        try {
            return values.containsKey(key) ? Integer.parseInt(values.get(key)) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
