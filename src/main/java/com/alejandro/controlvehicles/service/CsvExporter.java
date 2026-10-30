package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.Divergence;
import com.alejandro.controlvehicles.domain.Formats;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * CSV that Excel opens directly in Spain and Portugal: ';' between fields, ',' as decimal separator, and a UTF-8
 * byte order mark so accents survive.
 */
public final class CsvExporter {

    private static final List<String> REPORT_HEADER = List.of("Group", "Name", "Trips", "Pending trips",
            "Trips without price", "Odometer km", "Google km", "Gap km", "Nominal l/100", "Measured l/100",
            "Litres refuelled", "Real spend EUR", "Expected cost EUR", "Odometer cost EUR", "Cost gap EUR",
            "Tolls declared EUR", "Tolls estimated EUR", "Divergences");

    public void writeReport(MonthlyReport report, Path file) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(REPORT_HEADER);
        report.vehicles().forEach(line -> rows.add(reportRow("Vehicle", line)));
        report.drivers().forEach(line -> rows.add(reportRow("Driver", line)));
        rows.add(reportRow("Total " + report.month(), report.total()));
        write(file, rows);
    }

    public void writeDivergences(List<Divergence> divergences, Function<Divergence, String> vehicleName,
                                 Function<Divergence, String> driverName, Path file) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Date", "Severity", "Kind", "Vehicle", "Driver", "Detail"));
        for (Divergence d : divergences) {
            rows.add(List.of(Formats.date(d.date()), d.severity().name(), d.kind().label(), vehicleName.apply(d),
                    driverName.apply(d), d.detail()));
        }
        write(file, rows);
    }

    private static List<String> reportRow(String group, MonthlyReport.Line line) {
        return List.of(group, line.name(), String.valueOf(line.trips()), String.valueOf(line.pendingTrips()),
                String.valueOf(line.tripsWithoutPrice()), String.valueOf(line.odometerKm()), number(line.googleKm()),
                number(line.gapKm()), number(line.nominalL100()), number(line.measuredL100()),
                number(line.litresRefuelled()), number(line.realSpend()), number(line.expectedCost()),
                number(line.odometerCost()), number(line.costGap()), number(line.tollsDeclared()),
                number(line.tollsEstimated()), String.valueOf(line.divergences()));
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static void write(Path file, List<List<String>> rows) {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write('﻿');
            for (List<String> row : rows) {
                writer.write(String.join(";", row.stream().map(CsvExporter::escape).toList()));
                writer.write("\r\n");
            }
        } catch (IOException e) {
            throw new ValidationException("Cannot write " + file + ": " + e.getMessage());
        }
    }
}
