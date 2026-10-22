package com.link.ui;

import com.link.ui.viewmodel.LinkViewModel;
import com.link.ui.views.MainShell;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Entry point for the Link application.
 *
 * <p>Constructs a {@link LinkViewModel} (which owns the real {@code LinkEngine}),
 * passes it into the UI shell, then starts the backend after the stage is shown
 * so that {@code Platform.runLater} has a live event loop. Backend startup
 * failures are surfaced as an alert rather than a silent crash.
 *
 * <p>Launch with {@code mvn javafx:run}.
 */
public class LinkUIApplication extends Application {

    private LinkViewModel viewModel;

    @Override
    public void start(Stage stage) {
        // Resolve a human-readable display name from the machine hostname,
        // falling back gracefully if resolution fails.
        String displayName = resolveDisplayName();

        viewModel = new LinkViewModel(displayName, 8080);
        MainShell shell = new MainShell(viewModel);

        Scene scene = new Scene(shell.root(), 1180, 760);
        scene.setFill(javafx.scene.paint.Color.web("#0B0F14"));
        scene.getStylesheets().add(
                getClass().getResource("/css/theme.css").toExternalForm());

        stage.setTitle("Link — Connect · Share · Together");
        stage.setMinWidth(940);
        stage.setMinHeight(620);
        stage.setScene(scene);

        // Register shutdown before the stage is shown so close always cleans up.
        stage.setOnCloseRequest(e -> viewModel.shutdown());

        stage.show();

        // Start the backend after the stage is shown so Platform.runLater works.
        try {
            viewModel.start();
        } catch (RuntimeException ex) {
            showStartupError(ex);
        }
    }

    private static String resolveDisplayName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            return System.getProperty("user.name", "This Device") + "'s Device";
        }
    }

    private void showStartupError(Exception ex) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Link — Backend Unavailable");
        alert.setHeaderText("Could not start network services");
        alert.setContentText(
                "Discovery and file transfers are unavailable.\n\n"
                + ex.getMessage()
                + "\n\nThe UI will remain open. Check that UDP port 8888 and TCP port 8080 are free.");
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
