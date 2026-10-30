package com.alejandro.controlvehicles.ui;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Work that may wait on the network or the disk, off the JavaFX thread; the callbacks run back on it. A failure
 * goes to {@link Dialogs#error} unless the caller handles it.
 */
public final class Background {

    private Background() {
    }

    public static <T> Task<T> run(Callable<T> work, Consumer<T> onSuccess) {
        return run(work, onSuccess, Dialogs::error);
    }

    public static <T> Task<T> run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));
        Thread thread = new Thread(task, "control-vehicles-background");
        thread.setDaemon(true);
        thread.start();
        return task;
    }
}
