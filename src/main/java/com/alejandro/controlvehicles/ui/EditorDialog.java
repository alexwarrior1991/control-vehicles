package com.alejandro.controlvehicles.ui;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * A form in a dialog that stays open until saving works. {@code prepare} runs on the JavaFX thread, reads and
 * checks the fields and returns the work to save them, which runs in the background; an error from either is
 * shown inside the dialog, which keeps what was typed.
 */
public final class EditorDialog {

    private EditorDialog() {
    }

    public static <R> Optional<R> show(Node owner, String title, Node content, Supplier<Callable<R>> prepare) {
        Dialog<R> dialog = new Dialog<>();
        dialog.setTitle(title);
        if (owner != null && owner.getScene() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setWrapText(true);
        error.setMaxWidth(560);
        error.setManaged(false);
        error.setVisible(false);
        VBox body = new VBox(8, content, error);
        dialog.getDialogPane().setContent(body);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.setText("Save");
        Object[] result = new Object[1];
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Callable<R> work;
            try {
                work = prepare.get();
            } catch (RuntimeException e) {
                showError(error, e);
                return;
            }
            ok.setDisable(true);
            Background.run(work, saved -> {
                result[0] = saved;
                dialog.setResult(saved);
                dialog.close();
            }, failure -> {
                ok.setDisable(false);
                showError(error, failure);
            });
        });
        dialog.setResultConverter(button -> null);
        dialog.showAndWait();
        @SuppressWarnings("unchecked")
        R saved = (R) result[0];
        return Optional.ofNullable(saved);
    }

    private static void showError(Label error, Throwable failure) {
        error.setText(Dialogs.message(failure));
        error.setManaged(true);
        error.setVisible(true);
        error.getScene().getWindow().sizeToScene();
    }
}
