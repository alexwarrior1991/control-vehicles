package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.app.AppContext;
import com.alejandro.controlvehicles.domain.Driver;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.util.concurrent.Callable;

/** The drivers: create, edit, deactivate (one with trips is never deleted). */
public final class DriversView implements AppView {

    private final AppContext context;
    private final BorderPane root = new BorderPane();
    private final TableView<Driver> table = Tables.table("No drivers yet: add the first one with «New driver».");

    public DriversView(AppContext context) {
        this.context = context;
        table.getColumns().add(Tables.text("Name", Driver::name));
        table.getColumns().add(Tables.text("Licence", d -> nullToEmpty(d.licenceNumber())));
        table.getColumns().add(Tables.text("Phone", d -> nullToEmpty(d.phone())));
        table.getColumns().add(Tables.text("Email", d -> nullToEmpty(d.email())));
        table.getColumns().add(Tables.text("Active", d -> d.active() ? "Yes" : "No"));
        table.getColumns().add(Tables.text("Notes", d -> nullToEmpty(d.notes())));
        Tables.onDoubleClick(table, this::edit);

        Button add = new Button("New driver");
        add.getStyleClass().add("primary");
        add.setOnAction(event -> edit(null));
        Button edit = new Button("Edit");
        edit.setOnAction(event -> selected(this::edit));
        Button toggle = new Button("Activate / deactivate");
        toggle.setOnAction(event -> selected(driver -> Background.run(
                () -> context.fleet.setActive(driver, !driver.active()), saved -> refresh())));
        Button delete = new Button("Delete");
        delete.setOnAction(event -> selected(this::delete));
        HBox actions = new HBox(8, add, edit, toggle, delete);
        actions.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(actions);
        root.setCenter(table);
    }

    @Override
    public String title() {
        return "Drivers";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        Background.run(context.fleet::drivers, table.getItems()::setAll);
    }

    private void edit(Driver driver) {
        TextField name = new TextField(driver == null ? "" : driver.name());
        TextField licence = new TextField(driver == null ? "" : nullToEmpty(driver.licenceNumber()));
        TextField phone = new TextField(driver == null ? "" : nullToEmpty(driver.phone()));
        TextField email = new TextField(driver == null ? "" : nullToEmpty(driver.email()));
        CheckBox active = new CheckBox("Active");
        active.setSelected(driver == null || driver.active());
        TextArea notes = new TextArea(driver == null ? "" : nullToEmpty(driver.notes()));
        notes.setPrefRowCount(3);
        FormGrid form = new FormGrid().add("Name *", name).add("Licence number", licence).add("Phone", phone)
                .add("Email", email).add("", active).add("Notes", notes);
        EditorDialog.show(root, driver == null ? "New driver" : "Edit driver", form, () -> {
            Driver edited = new Driver(driver == null ? null : driver.id(), name.getText(), licence.getText(),
                    phone.getText(), email.getText(), active.isSelected(), notes.getText());
            Callable<Driver> save = () -> context.fleet.save(edited);
            return save;
        }).ifPresent(saved -> refresh());
    }

    private void delete(Driver driver) {
        if (Dialogs.confirm("Delete " + driver.name() + "?",
                "A driver with trips or refuels cannot be deleted: deactivate them instead.")) {
            Background.run(() -> {
                context.fleet.deleteDriver(driver.id());
                return null;
            }, done -> refresh());
        }
    }

    private void selected(java.util.function.Consumer<Driver> action) {
        Driver driver = table.getSelectionModel().getSelectedItem();
        if (driver != null) {
            action.accept(driver);
        }
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
