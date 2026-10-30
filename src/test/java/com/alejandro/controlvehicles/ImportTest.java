package com.alejandro.controlvehicles;

import com.alejandro.controlvehicles.app.AppContext;
import com.alejandro.controlvehicles.domain.Country;
import com.alejandro.controlvehicles.domain.Driver;
import com.alejandro.controlvehicles.domain.FuelType;
import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.domain.PriceQuote;
import com.alejandro.controlvehicles.domain.Refuel;
import com.alejandro.controlvehicles.domain.Trip;
import com.alejandro.controlvehicles.domain.Vehicle;
import com.alejandro.controlvehicles.persistence.PlaceRepository;
import com.alejandro.controlvehicles.service.ValidationException;
import com.alejandro.controlvehicles.service.imports.CsvReader;
import com.alejandro.controlvehicles.service.imports.ImportField;
import com.alejandro.controlvehicles.service.imports.ImportResult;
import com.alejandro.controlvehicles.service.imports.ImportRow;
import com.alejandro.controlvehicles.service.imports.RefuelImportMapping;
import com.alejandro.controlvehicles.service.imports.TabularFile;
import com.alejandro.controlvehicles.service.imports.XlsxReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fuel card statements: reading CSV and .xlsx, guessing the columns and the import rules. */
class ImportTest {

    private static final String PORTUGUESE_HEADER =
            "Data;Hora;Matrícula;Posto;Produto;Quantidade (L);Preço Unitário;Valor Total;Km";

    @TempDir
    Path home;
    private AppContext app;
    private Vehicle kangoo;

    @BeforeEach
    void start() {
        app = new AppContext(home, Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneId.of("Europe/Madrid")));
        kangoo = app.fleet.save(new Vehicle(null, "1234 KLM", "Renault", "Kangoo", FuelType.DIESEL,
                new BigDecimal("6"), new BigDecimal("60"), 120_000, true, null));
    }

    @Test
    void aSemicolonCsvWithBomQuotesAndDecimalCommasIsRead() {
        TabularFile table = CsvReader.read("s.csv", ("﻿" + PORTUGUESE_HEADER + "\r\n"
                + "05/10/2026;08:15;1234-KLM;\"Galp Lisboa; Av. Liberdade\";Gasóleo;40,12;1,659;66,56;120 400\r\n"
                + ";;;;;;;;\r\n").getBytes(StandardCharsets.UTF_8));

        assertEquals("Data", table.header().getFirst());
        assertEquals(1, table.rows().size(), "blank lines are dropped");
        assertEquals("Galp Lisboa; Av. Liberdade", table.rows().getFirst().get(3));
        assertEquals("40,12", table.rows().getFirst().get(5));
    }

    @Test
    void aWindows1252CsvWithCommasKeepsItsAccents() {
        byte[] bytes = "Fecha,Matrícula,Litros,Importe\n2026-10-05,1234KLM,40.5,59.10\n"
                .getBytes(Charset.forName("windows-1252"));
        TabularFile table = CsvReader.read("s.csv", bytes);
        assertEquals(List.of("Fecha", "Matrícula", "Litros", "Importe"), table.header());
        assertEquals("59.10", table.rows().getFirst().get(3));
    }

    @Test
    void theFirstSheetOfAnXlsxIsReadWithSharedStringsNumbersAndDates() throws IOException {
        TabularFile table = XlsxReader.read("s.xlsx", xlsx());
        assertEquals(List.of("Data", "Matrícula", "Litros", "Valor"), table.header());
        List<String> row = table.rows().getFirst();
        assertEquals("46300.5", row.get(0), "an Excel day number with half a day");
        assertEquals("1234 KLM", row.get(1));
        assertEquals("40.12", row.get(2));
        assertEquals("66.56", row.get(3));
        assertEquals("", table.rows().get(1).get(1), "a missing cell in the middle stays empty");
    }

    @Test
    void theColumnsAreGuessedFromPortugueseAndSpanishHeaders() {
        RefuelImportMapping pt = RefuelImportMapping.guess(List.of(PORTUGUESE_HEADER.split(";")));
        assertEquals("Data", pt.column(ImportField.DATE));
        assertEquals("Hora", pt.column(ImportField.TIME));
        assertEquals("Matrícula", pt.column(ImportField.PLATE));
        assertEquals("Quantidade (L)", pt.column(ImportField.LITRES));
        assertEquals("Valor Total", pt.column(ImportField.AMOUNT), "not the unit price");
        assertEquals("Km", pt.column(ImportField.ODOMETER));
        assertEquals("Posto", pt.column(ImportField.STATION));
        assertEquals("Produto", pt.column(ImportField.PRODUCT));

        RefuelImportMapping es = RefuelImportMapping.guess(List.of("Fecha operación", "Matrícula", "Estación",
                "Producto", "Litros", "Precio/l", "Importe", "Kilómetros"));
        assertEquals("Fecha operación", es.column(ImportField.DATE));
        assertEquals("Importe", es.column(ImportField.AMOUNT));
        assertEquals("Kilómetros", es.column(ImportField.ODOMETER));
        assertTrue(es.missingRequired().isEmpty());

        RefuelImportMapping none = RefuelImportMapping.guess(List.of("A", "B"));
        assertEquals(List.of(ImportField.DATE, ImportField.PLATE, ImportField.LITRES, ImportField.AMOUNT),
                none.missingRequired());
        assertEquals(pt, RefuelImportMapping.fromJson(pt.toJson()).orElseThrow());
    }

    @Test
    void importingCreatesGalpFullTankRefuelsAndTellsWhatItLeftOut() throws IOException {
        Driver ana = app.fleet.save(new Driver(null, "Ana", null, null, null, true, null));
        PlaceRepository places = new PlaceRepository(app.database);
        Place lisbon = places.save(new Place(null, null, "Lisboa", null, 38.72, -9.14, Country.PT));
        Place elvas = places.save(new Place(null, null, "Elvas", null, 38.88, -7.16, Country.PT));
        app.trips.save(new Trip(null, kangoo.id(), ana.id(), LocalDate.of(2026, 10, 6), LocalTime.of(8, 0),
                LocalTime.of(9, 0), lisbon.id(), elvas.id(), 120_450, 120_520, BigDecimal.ZERO, null, null, null),
                PriceQuote.manual(new BigDecimal("1.6")));
        app.refuels.save(new Refuel(null, kangoo.id(), null, LocalDateTime.of(2026, 10, 1, 9, 0), 120_100, false,
                new BigDecimal("30"), new BigDecimal("45.00"), true, null, "Galp", Country.ES, null));
        Path file = write("galp.csv", PORTUGUESE_HEADER + "\n"
                + "05/10/2026;08:15;1234-KLM;Galp Lisboa;Gasóleo;40,12;1,659;66,56;120.400\n"
                + "06/10/2026;18:30;1234 klm;Galp Elvas;Gasóleo Especial;35,00;1,659;58,07;\n"
                + "06/10/2026;18:31;1234 KLM;Galp Elvas;AdBlue;10;0,8;8,00;\n"
                + "07/10/2026;10:00;9999 ZZZ;Galp Elvas;Gasóleo;20;1,6;32,00;\n"
                + "07/10/2026;11:00;1234 KLM;Galp Elvas;Gasóleo;abc;1,6;32,00;\n"
                + "01/10/2026;09:00;1234KLM;Galp Madrid;Gasóleo;30;1,5;45;\n");

        TabularFile table = app.refuelImport.read(file);
        RefuelImportMapping mapping = app.refuelImport.proposedMapping(table);
        List<ImportRow> preview = app.refuelImport.preview(table, mapping);
        // «Gasóleo» contiene «óleo» y es gasóleo: solo el AdBlue queda fuera como no carburante.
        assertEquals(List.of(ImportRow.Status.IMPORT, ImportRow.Status.IMPORT, ImportRow.Status.NOT_FUEL,
                ImportRow.Status.UNKNOWN_PLATE, ImportRow.Status.INVALID, ImportRow.Status.DUPLICATE),
                preview.stream().map(ImportRow::status).toList());

        ImportResult result = app.refuelImport.importRows(table, mapping);
        assertEquals(2, result.imported());
        assertEquals(1, result.estimatedOdometers());
        assertEquals(1, result.duplicates());
        assertEquals(1, result.notFuel());
        assertEquals(Set.of("9999 ZZZ"), result.unknownPlates());
        assertTrue(result.invalid().getFirst().startsWith("Line 6: Litres is not a number"), result.invalid().toString());

        List<Refuel> stored = app.refuels.all();
        Refuel read = stored.stream().filter(r -> r.odometerKm() == 120_400).findFirst().orElseThrow();
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 15), read.refuelAt());
        assertEquals(new BigDecimal("40.12"), read.litres());
        assertTrue(read.fullTank());
        assertEquals("Galp", read.brand());
        assertEquals("Galp Lisboa", read.station());
        assertFalse(read.odometerEstimated());
        Refuel estimated = stored.stream().filter(Refuel::odometerEstimated).findFirst().orElseThrow();
        assertEquals(120_520, estimated.odometerKm(), "the trip of that morning ended there, before the refuel");
        assertTrue(estimated.notes().contains("Gasóleo Especial"));
    }

    @Test
    void importingTheSameStatementTwiceAddsNothingAndTheMappingIsRemembered() throws IOException {
        Path file = write("es.csv", "Fecha;Vehículo;L.;€\n05/10/2026 08:15;1234KLM;40,12;66,56\n");
        TabularFile table = app.refuelImport.read(file);
        RefuelImportMapping guessed = app.refuelImport.proposedMapping(table);
        assertFalse(guessed.missingRequired().isEmpty(), "these headers cannot be guessed");
        assertThrows(ValidationException.class, () -> app.refuelImport.preview(table, guessed));

        RefuelImportMapping chosen = new RefuelImportMapping(Map.of(ImportField.DATE, "Fecha",
                ImportField.PLATE, "Vehículo", ImportField.LITRES, "L.", ImportField.AMOUNT, "€"));
        assertEquals(1, app.refuelImport.importRows(table, chosen).imported());
        assertEquals(LocalTime.of(8, 15), app.refuels.all().getFirst().refuelAt().toLocalTime());

        assertEquals(chosen, app.refuelImport.proposedMapping(table));
        ImportResult again = app.refuelImport.importRows(table, chosen);
        assertEquals(0, again.imported());
        assertEquals(1, again.duplicates());
    }

    @Test
    void anOldXlsFileIsRefusedWithWhatToDo() throws IOException {
        Path file = write("old.xls", "whatever");
        ValidationException e = assertThrows(ValidationException.class, () -> app.refuelImport.read(file));
        assertTrue(e.getMessage().contains(".xlsx or CSV"));
    }

    private Path write(String name, String content) throws IOException {
        Path file = home.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** A minimal .xlsx as Excel writes it: shared strings, a numeric date, and a row with a gap. */
    private static byte[] xlsx() throws IOException {
        String shared = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="5" uniqueCount="5">
                <si><t>Data</t></si><si><t>Matrícula</t></si><si><t>Litros</t></si><si><t>Valor</t></si>
                <si><r><t>1234 </t></r><r><t>KLM</t></r></si></sst>""";
        String sheet = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c>
                <c r="D1" t="s"><v>3</v></c></row>
                <row r="2"><c r="A2"><v>46300.5</v></c><c r="B2" t="s"><v>4</v></c><c r="C2"><v>40.120000000000005</v>
                </c><c r="D2"><v>66.56</v></c></row>
                <row r="3"><c r="A3"><v>46301</v></c><c r="C3" t="inlineStr"><is><t>12</t></is></c></row>
                </sheetData></worksheet>""";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zip.write(shared.strip().getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet.strip().getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}
