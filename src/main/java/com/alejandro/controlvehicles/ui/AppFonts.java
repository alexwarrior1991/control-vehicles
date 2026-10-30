package com.alejandro.controlvehicles.ui;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The look of the application: JetBrains Mono travels inside the jar (SIL Open Font License, {@code fonts/OFL.txt})
 * so it is the same on a computer that does not have it installed, and {@code app.css} names it. Every scene,
 * also each dialog's own, gets the stylesheet through here.
 */
public final class AppFonts {

    private static final Logger LOG = Logger.getLogger(AppFonts.class.getName());
    private static final String FONTS = "/com/alejandro/controlvehicles/ui/fonts/";
    private static final String STYLESHEET = "/com/alejandro/controlvehicles/ui/app.css";

    private AppFonts() {
    }

    /** Registers the font files; must run before the first scene. Without them JavaFX keeps its default font. */
    public static void load() {
        for (String file : new String[]{"JetBrainsMono-Regular.ttf", "JetBrainsMono-Bold.ttf"}) {
            try (InputStream in = AppFonts.class.getResourceAsStream(FONTS + file)) {
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
    }

    public static void style(DialogPane pane) {
        pane.getStylesheets().add(stylesheet());
    }

    private static String stylesheet() {
        return Objects.requireNonNull(AppFonts.class.getResource(STYLESHEET), STYLESHEET).toExternalForm();
    }
}
