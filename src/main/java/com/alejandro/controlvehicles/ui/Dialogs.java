package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.integration.ExternalApiException;
import com.alejandro.controlvehicles.persistence.DataAccessException;
import com.alejandro.controlvehicles.service.ValidationException;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Messages to the user. Expected failures show their own message; anything else is logged too. */
public final class Dialogs {

    private static final Logger LOG = Logger.getLogger(Dialogs.class.getName());

    private Dialogs() {
    }

    public static void error(Throwable failure) {
        Throwable cause = failure instanceof RuntimeException && failure.getCause() instanceof ValidationException
                ? failure.getCause() : failure;
        String message = message(cause);
        if (!(cause instanceof ValidationException || cause instanceof IllegalArgumentException
                || cause instanceof ExternalApiException || cause instanceof DataAccessException)) {
            LOG.log(Level.WARNING, "Unexpected failure", cause);
        }
        show(Alert.AlertType.ERROR, "Something went wrong", message);
    }

    public static String message(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return Character.toUpperCase(message.charAt(0)) + message.substring(1);
    }

    public static void info(String header, String message) {
        show(Alert.AlertType.INFORMATION, header, message);
    }

    public static void warning(String header, String message) {
        show(Alert.AlertType.WARNING, header, message);
    }

    public static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Control Vehicles");
        alert.setHeaderText(header);
        alert.getDialogPane().setContent(wrapped(message));
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    private static void show(Alert.AlertType type, String header, String message) {
        Alert alert = new Alert(type);
        alert.setTitle("Control Vehicles");
        alert.setHeaderText(header);
        alert.getDialogPane().setContent(wrapped(message));
        alert.showAndWait();
    }

    private static Label wrapped(String message) {
        Label label = new Label(message);
        label.setWrapText(true);
        label.setMaxWidth(520);
        return label;
    }
}
