package com.alejandro.controlvehicles.ui;

import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.integration.google.PlaceSuggestion;
import com.alejandro.controlvehicles.service.PlaceService;
import javafx.animation.PauseTransition;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An origin or a destination: typing shows the stored places that match and Google's suggestions; choosing a
 * suggestion asks Google for its details and stores it. Only a chosen place counts: free text is not a place.
 */
public final class PlaceField extends VBox {

    private record Result(List<Place> stored, List<PlaceSuggestion> google, String googleError) {
    }

    private final PlaceService places;
    private final TextField text = new TextField();
    private final Label status = new Label();
    private final ContextMenu menu = new ContextMenu();
    private final ObjectProperty<Place> selected = new SimpleObjectProperty<>();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(350));
    private String sessionToken = UUID.randomUUID().toString();
    private int generation;
    private boolean settingText;

    public PlaceField(PlaceService places, String prompt) {
        super(2);
        this.places = places;
        text.setPromptText(prompt);
        status.getStyleClass().add("field-hint");
        getChildren().addAll(text, status);
        debounce.setOnFinished(event -> search(text.getText()));
        text.textProperty().addListener((observable, before, now) -> {
            if (settingText) {
                return;
            }
            selected.set(null);
            status.setText("Choose one of the suggestions");
            debounce.playFromStart();
        });
        text.focusedProperty().addListener((observable, before, focused) -> {
            if (!focused && selected.get() == null && !text.getText().isBlank()) {
                status.setText("Not chosen yet: pick a suggestion");
            }
        });
    }

    public ObjectProperty<Place> selectedProperty() {
        return selected;
    }

    public Place selected() {
        return selected.get();
    }

    public void select(Place place) {
        selected.set(place);
        settingText = true;
        text.setText(place == null ? "" : place.label());
        settingText = false;
        status.setText(place == null || place.country() == null ? "" : place.country().label());
    }

    private void search(String query) {
        if (query == null || query.trim().length() < 2) {
            menu.hide();
            return;
        }
        int mine = ++generation;
        String token = sessionToken;
        Background.run(() -> {
            List<Place> stored = places.searchStored(query);
            try {
                return new Result(stored, places.searchGoogle(query, token), null);
            } catch (RuntimeException e) {
                return new Result(stored, List.of(), Dialogs.message(e));
            }
        }, result -> {
            if (mine == generation) {
                show(result);
            }
        });
    }

    private void show(Result result) {
        List<MenuItem> items = new ArrayList<>();
        for (Place place : result.stored()) {
            items.add(item("★ " + place.label(), () -> select(place)));
        }
        for (PlaceSuggestion suggestion : result.google()) {
            items.add(item(suggestion.label(), () -> resolve(suggestion)));
        }
        if (result.googleError() != null) {
            MenuItem error = new MenuItem("Google: " + result.googleError());
            error.setDisable(true);
            items.add(error);
        }
        if (items.isEmpty()) {
            menu.hide();
            status.setText("Nothing found");
            return;
        }
        menu.getItems().setAll(items);
        if (!menu.isShowing() && getScene() != null) {
            menu.show(text, Side.BOTTOM, 0, 0);
        }
    }

    private void resolve(PlaceSuggestion suggestion) {
        status.setText("Getting the place from Google…");
        String token = sessionToken;
        Background.run(() -> places.resolve(suggestion, token), place -> {
            sessionToken = UUID.randomUUID().toString();
            select(place);
        }, failure -> status.setText("Google: " + Dialogs.message(failure)));
    }

    private static MenuItem item(String label, Runnable action) {
        Label content = new Label(label);
        content.setMaxWidth(520);
        CustomMenuItem item = new CustomMenuItem(content, true);
        item.setOnAction(event -> action.run());
        return item;
    }
}
