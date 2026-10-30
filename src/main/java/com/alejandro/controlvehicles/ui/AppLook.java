package com.alejandro.controlvehicles.ui;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The look of the application: JetBrains Mono travels inside the jar (SIL Open Font License, {@code fonts/OFL.txt})
 * so it is the same on a computer that does not have it installed, and {@code app.css} names it; the icon
 * ({@code icons/}, drawn by {@code scripts/draw_icon.py}) goes on every window, which is what the taskbar and the
 * title bar show. Every scene, also each dialog's own, gets both through here.
 */
public final class AppLook {

    private static final Logger LOG = Logger.getLogger(AppLook.class.getName());
    private static final String FONTS = "/com/alejandro/controlvehicles/ui/fonts/";
    private static final String ICONS = "/com/alejandro/controlvehicles/ui/icons/icon-%d.png";
    private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final String STYLESHEET = "/com/alejandro/controlvehicles/ui/app.css";
    private static final List<Image> ICON_IMAGES = new ArrayList<>();

    private AppLook() {
    }

    /**
     * Registers the font files and reads the icon; must run before the first scene. Without them JavaFX keeps its
     * default font and icon.
     */
    public static void load() {
        if (ICON_IMAGES.isEmpty()) {
            for (int size : ICON_SIZES) {
                try (InputStream in = AppLook.class.getResourceAsStream(String.format(ICONS, size))) {
                    if (in != null) {
                        ICON_IMAGES.add(new Image(in));
                    }
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Icon of " + size + " px not loaded", e);
                }
            }
        }
        for (String file : new String[]{"JetBrainsMono-Regular.ttf", "JetBrainsMono-Bold.ttf"}) {
            try (InputStream in = AppLook.class.getResourceAsStream(FONTS + file)) {
                if (in == null || Font.loadFont(in, 13) == null) {
                    LOG.warning("Font " + file + " not loaded; using the default font");
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Font " + file + " not loaded; using the default font", e);
            }
        }
    }

    public static void style(Scene scene) {
        scene.getStylesheets().add(stylesheet());
        if (scene.getWindow() != null) {
            icon(scene.getWindow());
        }
        scene.windowProperty().addListener((o, before, window) -> icon(window));
    }

    public static void style(DialogPane pane) {
        pane.getStylesheets().add(stylesheet());
        pane.sceneProperty().addListener((o, before, scene) -> {
            if (scene != null) {
                style(scene);
            }
        });
        if (pane.getScene() != null) {
            if (pane.getScene().getWindow() != null) {
                icon(pane.getScene().getWindow());
            }
            pane.getScene().windowProperty().addListener((o, before, window) -> icon(window));
        }
    }

    /** The icon images, from 16 to 256 px: the system picks the size it needs. */
    public static List<Image> icons() {
        return List.copyOf(ICON_IMAGES);
    }

    private static void icon(Window window) {
        if (window instanceof Stage stage && stage.getIcons().isEmpty()) {
            stage.getIcons().setAll(ICON_IMAGES);
        }
    }

    private static String stylesheet() {
        return Objects.requireNonNull(AppLook.class.getResource(STYLESHEET), STYLESHEET).toExternalForm();
    }
}
