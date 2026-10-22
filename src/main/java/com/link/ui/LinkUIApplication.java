package com.link.ui;

import com.link.ui.prototype.PrototypeState;
import com.link.ui.views.MainShell;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point for the Link UI prototype.
 *
 * <p>Launch with {@code mvn javafx:run} from the {@code link-ui} directory.
 * The prototype runs entirely on demo data — no backend is required or used.
 */
public class LinkUIApplication extends Application {

    @Override
    public void start(Stage stage) {
        PrototypeState state = new PrototypeState();
        MainShell shell = new MainShell(state);

        Scene scene = new Scene(shell.root(), 1180, 760);
        scene.setFill(javafx.scene.paint.Color.web("#0B0F14"));
        scene.getStylesheets().add(
                getClass().getResource("/css/theme.css").toExternalForm());

        stage.setTitle("Link — Connect · Share · Together");
        stage.setMinWidth(940);
        stage.setMinHeight(620);
        stage.setScene(scene);

        state.startEngine();

        stage.setOnCloseRequest(e -> state.stopEngine());

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
