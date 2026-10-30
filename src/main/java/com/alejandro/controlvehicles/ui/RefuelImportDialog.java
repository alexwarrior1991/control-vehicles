package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.app.AppContext;
import com.alejandro.controlvehicles.domain.Formats;
import com.alejandro.controlvehicles.domain.Refuel;
import com.alejandro.controlvehicles.service.imports.ImportField;
import com.alejandro.controlvehicles.service.imports.ImportResult;
import com.alejandro.controlvehicles.service.imports.ImportRow;
import com.alejandro.controlvehicles.service.imports.RefuelImportMapping;
import com.alejandro.controlvehicles.service.imports.TabularFile;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Importing a fuel card statement (Galp Frota or any other): choose the file, say which column is which (the
 * last mapping, or a guess from the headers, comes filled), look at what each line will do, import.
 */
final class RefuelImportDialog {

    private static final String NONE = "(none)";
    private static final int PREVIEW_ROWS = 200;

    private RefuelImportDialog() {
    }

    /** Asks for the file and shows the dialog; empty if nothing was imported. */
    static Optional<ImportResult> show(Node owner, AppContext context) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Fuel card statement");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Statement (CSV or Excel)", "*.csv", "*.txt", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = chooser.showOpenDialog(owner.getScene().getWindow());
        if (file == null) {
            return Optional.empty();
        }
        TabularFile table;
        try {
            table = context.refuelImport.read(file.toPath());
        } catch (RuntimeException e) {
            Dialogs.error(e);
            return Optional.empty();
        }
        return new Form(context, table).show(owner);
    }

    private static final class Form {
        private final AppContext context;
        private final TabularFile table;
        private final Map<ImportField, ComboBox<String>> combos = new EnumMap<>(ImportField.class);
        private final TableView<ImportRow> preview = Tables.wide(Tables.table("Choose the columns marked with *."));
        private final Label summary = new Label();
        private List<ImportRow> rows = List.of();
        private int generation;

        Form(AppContext context, TabularFile table) {
            this.context = context;
            this.table = table;
        }

        Optional<ImportResult> show(Node owner) {
            RefuelImportMapping proposed = context.refuelImport.proposedMapping(table);
            GridPane mapping = new GridPane();
            mapping.setHgap(10);
            mapping.setVgap(6);
            int index = 0;
            for (ImportField field : ImportField.values()) {
                ComboBox<String> combo = new ComboBox<>();
                List<String> options = new ArrayList<>();
                options.add(NONE);
                options.addAll(table.header());
                combo.getItems().setAll(options);
                combo.setValue(proposed.column(field) == null ? NONE : proposed.column(field));
                combo.setPrefWidth(240);
                combo.valueProperty().addListener((o, b, n) -> refreshPreview());
                combos.put(field, combo);
                mapping.add(new Label(field.toString()), (index % 2) * 2, index / 2);
                mapping.add(combo, (index % 2) * 2 + 1, index / 2);
                index++;
            }
            buildPreview();
            Label file = new Label(table.name() + " · " + table.rows().size() + " lines");
            file.getStyleClass().add("section-title");
            Label hint = new Label("Every imported refuel is Galp and a full tank: untick «Tank filled up» in Refuels"
                    + " for the ones that were not. A line without odometer gets an estimate from the trips,"
                    + " marked as estimated, which does not count for consumption until it is corrected.");
            hint.getStyleClass().add("field-hint");
            hint.setWrapText(true);
            hint.setMaxWidth(900);
            preview.setPrefSize(1000, 360);
            VBox body = new VBox(10, file, mapping, hint, preview, summary);

            Dialog<ImportResult> dialog = new Dialog<>();
            dialog.setTitle("Import fuel card statement");
            dialog.initOwner(owner.getScene().getWindow());
            dialog.getDialogPane().setContent(body);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            AppLook.style(dialog.getDialogPane());
            dialog.setResizable(true);
            Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
            ok.setText("Import");
            ok.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                    () -> rows.stream().noneMatch(r -> r.status() == ImportRow.Status.IMPORT),
                    summary.textProperty()));
            ImportResult[] result = new ImportResult[1];
            ok.addEventFilter(ActionEvent.ACTION, event -> {
                event.consume();
                RefuelImportMapping chosen = mapping();
                ok.disableProperty().unbind();
                ok.setDisable(true);
                Background.run(() -> context.refuelImport.importRows(table, chosen), done -> {
                    result[0] = done;
                    dialog.setResult(done);
                    dialog.close();
                }, failure -> {
                    ok.setDisable(false);
                    Dialogs.error(failure);
                });
            });
            dialog.setResultConverter(button -> null);
            refreshPreview();
            dialog.showAndWait();
            return Optional.ofNullable(result[0]);
        }

        private RefuelImportMapping mapping() {
            Map<ImportField, String> columns = new EnumMap<>(ImportField.class);
            combos.forEach((field, combo) -> {
                if (combo.getValue() != null && !NONE.equals(combo.getValue())) {
                    columns.put(field, combo.getValue());
                }
            });
            return new RefuelImportMapping(columns);
        }

        private void refreshPreview() {
            RefuelImportMapping chosen = mapping();
            if (!chosen.missingRequired().isEmpty()) {
                rows = List.of();
                preview.getItems().clear();
                summary.setText("Choose the column of: " + chosen.missingRequired().stream().map(ImportField::label)
                        .collect(Collectors.joining(", ")));
                return;
            }
            int mine = ++generation;
            summary.setText("Reading…");
            Background.run(() -> context.refuelImport.preview(table, chosen), found -> {
                if (mine != generation) {
                    return;
                }
                rows = found;
                preview.getItems().setAll(found.size() > PREVIEW_ROWS ? found.subList(0, PREVIEW_ROWS) : found);
                Map<ImportRow.Status, Long> counts = found.stream().collect(Collectors.groupingBy(ImportRow::status,
                        () -> new EnumMap<>(ImportRow.Status.class), Collectors.counting()));
                long estimated = found.stream().filter(r -> r.refuel() != null && r.status() == ImportRow.Status.IMPORT
                        && r.refuel().odometerEstimated()).count();
                summary.setText(counts.entrySet().stream().map(e -> e.getValue() + " " + e.getKey())
                        .collect(Collectors.joining(" · "))
                        + (estimated > 0 ? " · " + estimated + " with estimated odometer" : "")
                        + (found.size() > PREVIEW_ROWS ? " (showing the first " + PREVIEW_ROWS + ")" : ""));
            }, failure -> {
                if (mine == generation) {
                    rows = List.of();
                    preview.getItems().clear();
                    summary.setText(Dialogs.message(failure));
                }
            });
        }

        private void buildPreview() {
            preview.getColumns().add(Tables.number("Line", ImportRow::line, String::valueOf));
            javafx.scene.control.TableColumn<ImportRow, String> status = Tables.text("Result",
                    r -> r.status().toString());
            status.setPrefWidth(160);
            preview.getColumns().add(status);
            preview.getColumns().add(Tables.text("Plate", r -> r.plate() == null ? "" : r.plate()));
            javafx.scene.control.TableColumn<ImportRow, String> date = Tables.text("Date",
                    refuel(r -> Formats.dateTime(r.refuelAt())));
            date.setPrefWidth(150);
            preview.getColumns().add(date);
            preview.getColumns().add(Tables.text("Odometer km", refuel(r -> (r.odometerEstimated() ? "~" : "")
                    + Formats.km(r.odometerKm()))));
            preview.getColumns().add(Tables.text("Litres", refuel(r -> Formats.decimal(r.litres()))));
            preview.getColumns().add(Tables.text("Amount €", refuel(r -> Formats.money(r.totalAmount()))));
            preview.getColumns().add(Tables.text("€/l", refuel(r -> Formats.pricePerLitre(r.pricePerLitre()))));
            javafx.scene.control.TableColumn<ImportRow, String> note = Tables.text("Note", ImportRow::message);
            note.setPrefWidth(380);
            preview.getColumns().add(note);
            preview.setRowFactory(t -> new TableRow<>() {
                @Override
                protected void updateItem(ImportRow item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("row-high", "row-warning", "row-info");
                    if (!empty && item != null) {
                        switch (item.status()) {
                            case UNKNOWN_PLATE, INVALID -> getStyleClass().add("row-high");
                            case DUPLICATE, NOT_FUEL -> getStyleClass().add("row-info");
                            case IMPORT -> {
                                if (item.refuel().odometerEstimated()) {
                                    getStyleClass().add("row-warning");
                                }
                            }
                        }
                    }
                }
            });
        }

        private static Function<ImportRow, String> refuel(Function<Refuel, String> value) {
            return row -> row.refuel() == null ? "" : value.apply(row.refuel());
        }
    }
}
