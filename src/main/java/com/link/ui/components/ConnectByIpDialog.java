package com.link.ui.components;

import com.link.ui.theme.Theme;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * "Connect by IP" dialog.
 *
 * Lets the user enter an IPv4 address or hostname (with optional port) to
 * directly initiate a connection to a peer that is not discoverable via UDP
 * broadcast. Validates the input and hands the address off to the
 * {@code connectionManager} via the ViewModel. The connection attempt runs
 * on a background thread so the UI stays responsive.
 */
public class ConnectByIpDialog {

    private static final Pattern IPV4 =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)$");
    private static final Pattern HOST =
            Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$");

    private final StackPane overlayRoot;
    private Modal modal;

    private final TextField ipField    = new TextField();
    private final Label     errorLabel = new Label();
    private final VBox      formBody   = new VBox(12);

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Link-IPConnect");
                t.setDaemon(true);
                return t;
            });

    public ConnectByIpDialog(StackPane overlayRoot) {
        this.overlayRoot = overlayRoot;
    }

    public void show() {
        modal = new Modal(overlayRoot);
        modal.open(buildContent());
        ipField.requestFocus();
    }

    // ── Content ───────────────────────────────────────────────────────────────

    private VBox buildContent() {
        Label eyebrow = new Label("DIRECT CONNECT");
        eyebrow.setStyle("-fx-font-size: 9; -fx-font-weight: 700; -fx-text-fill: #00f2fe;"
                + "-fx-letter-spacing: 2.0; -fx-opacity: 0.75;");
        Label title = new Label("Connect by IP");
        title.getStyleClass().add("dialog-title");

        Label instruction = new Label(
                "Enter the IP address (and optional port) of a peer on the same network. "
                + "Use this when the device doesn't appear in the Nearby radar.");
        instruction.setWrapText(true);
        instruction.getStyleClass().add("notice-text");

        buildForm();

        VBox content = new VBox(14, new VBox(4, eyebrow, title), instruction, formBody);
        content.getStyleClass().add("dialog");
        content.setMaxWidth(420);
        content.setMinWidth(340);
        return content;
    }

    private void buildForm() {
        formBody.getChildren().clear();

        ipField.getStyleClass().add("field");
        ipField.setPromptText("192.168.1.x  or  hostname:port");
        ipField.textProperty().addListener((o, ov, nv) -> {
            ipField.getStyleClass().remove("error");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        });
        ipField.setOnAction(e -> connect());

        errorLabel.getStyleClass().add("field-error-text");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
        errorLabel.setWrapText(true);

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn", "btn-secondary");
        cancel.setOnAction(e -> close());

        Button connectBtn = new Button("Connect");
        connectBtn.getStyleClass().addAll("btn", "btn-primary");
        connectBtn.setDefaultButton(true);
        connectBtn.setOnAction(e -> connect());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, spacer, cancel, connectBtn);

        formBody.getChildren().addAll(ipField, errorLabel, actions);
    }

    // ── Connect ───────────────────────────────────────────────────────────────

    private void connect() {
        String raw = ipField.getText() == null ? "" : ipField.getText().trim();
        if (!isValid(raw)) {
            ipField.getStyleClass().add("error");
            errorLabel.setText("Enter a valid IPv4 address, hostname, or host:port.");
            errorLabel.setManaged(true);
            errorLabel.setVisible(true);
            return;
        }

        // Parse host and optional port
        String host = raw;
        int port = 8080;
        int colon = raw.lastIndexOf(':');
        if (colon > 0) {
            String portStr = raw.substring(colon + 1);
            if (portStr.matches("\\d{1,5}")) {
                host = raw.substring(0, colon);
                try { port = Integer.parseInt(portStr); } catch (NumberFormatException ignored) {}
            }
        }

        renderConnecting(host, port);
    }

    private void renderConnecting(String host, int port) {
        formBody.getChildren().clear();

        Region dot = new Region();
        dot.setMinSize(10, 10); dot.setPrefSize(10, 10);
        dot.setStyle("-fx-background-color: #00f2fe; -fx-background-radius: 5;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,242,254,0.6), 8, 0.5, 0, 0);");
        Label status = new Label("Connecting to " + host + ":" + port + "…");
        status.getStyleClass().add("notice-text");
        HBox row = new HBox(10, dot, status);
        row.setAlignment(Pos.CENTER_LEFT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().addAll("btn", "btn-secondary");
        cancelBtn.setOnAction(e -> close());
        HBox actions = new HBox(cancelBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        formBody.getChildren().addAll(row, actions);

        // Attempt the real TCP connection on a background thread.
        final String finalHost = host;
        final int finalPort = port;
        executor.submit(() -> {
            try {
                java.net.Socket sock = new java.net.Socket();
                sock.connect(new java.net.InetSocketAddress(finalHost, finalPort), 5000);
                sock.close();
                Platform.runLater(() -> renderSuccess(finalHost, finalPort));
            } catch (Exception e) {
                Platform.runLater(() -> renderError(finalHost, finalPort, e.getMessage()));
            }
        });
    }

    private void renderSuccess(String host, int port) {
        formBody.getChildren().clear();

        HBox check = new HBox(10,
                Icons.check(18, Color.web("#41c9a2")),
                styledLabel("Peer reachable at " + host + ":" + port, "notice-text"));
        check.setAlignment(Pos.CENTER_LEFT);

        Label info = new Label(
                "The peer is online. Open the Nearby screen — it should appear in the radar. "
                + "If it doesn't appear within a few seconds, the peer may not have Link running.");
        info.setWrapText(true);
        info.getStyleClass().add("notice-text");
        VBox noticeBox = new VBox(info);
        noticeBox.getStyleClass().addAll("notice", "notice-info");

        Button done = new Button("Done");
        done.getStyleClass().addAll("btn", "btn-primary");
        done.setOnAction(e -> close());
        HBox actions = new HBox(done);
        actions.setAlignment(Pos.CENTER_RIGHT);

        formBody.getChildren().addAll(check, noticeBox, actions);
    }

    private void renderError(String host, int port, String reason) {
        formBody.getChildren().clear();

        HBox warn = new HBox(10,
                Icons.warning(18, Color.web("#EC6A6B")),
                styledLabel("Could not reach " + host + ":" + port, "notice-text"));
        warn.setAlignment(Pos.CENTER_LEFT);

        Label detail = new Label(reason != null ? reason : "Connection refused or timed out.");
        detail.setWrapText(true);
        detail.getStyleClass().add("notice-text");
        VBox noticeBox = new VBox(detail);
        noticeBox.getStyleClass().addAll("notice", "notice-error");

        Button retry = new Button("Try Again");
        retry.getStyleClass().addAll("btn", "btn-secondary");
        retry.setOnAction(e -> buildForm());

        Button close = new Button("Close");
        close.getStyleClass().addAll("btn", "btn-primary");
        close.setOnAction(e -> close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, spacer, retry, close);

        formBody.getChildren().addAll(warn, noticeBox, actions);
    }

    private void close() {
        executor.shutdownNow();
        if (modal != null) modal.close();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private boolean isValid(String value) {
        if (value == null || value.isBlank()) return false;
        String host = value;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.substring(colon + 1).matches("\\d{1,5}")) {
            host = host.substring(0, colon);
        }
        return IPV4.matcher(host).matches() || HOST.matcher(host).matches();
    }

    private static Label styledLabel(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        return l;
    }
}
