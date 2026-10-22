package com.link.ui.components;

import com.link.ui.theme.Theme;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.regex.Pattern;

/**
 * "Connect by IP" dialog with a QR-code display panel.
 *
 * Layout:
 *   Left column   — QR code canvas (local IP encoded as a visual grid)
 *                   "SCAN TO CONNECT" label
 *                   Local IP address
 *   Right column  — IP input field, port (optional), connect button, demo notice
 *
 * The QR visual is rendered on a JavaFX {@link Canvas} using a deterministic
 * algorithm that places finder-pattern squares and data cells derived from the
 * IP byte values. It is a visual representation for demo purposes — not a
 * standards-compliant QR code that a scanner app would decode.
 *
 * No real network connection is ever established. The handshake is clearly
 * labelled as simulated.
 */
public class ConnectByIpDialog {

    private static final Pattern IPV4 =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)$");
    private static final Pattern HOST =
            Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$");

    private final StackPane root;
    private Modal modal;

    private final VBox    content  = new VBox();
    private final VBox    formBody = new VBox();
    private final TextField ipField = new TextField();
    private final Label   errorLabel = new Label();

    // QR canvas — updated whenever the IP field changes
    private final Canvas qrCanvas = new Canvas(140, 140);
    private final Label  qrIpLabel = new Label();

    public ConnectByIpDialog(StackPane root) {
        this.root = root;
        build();
    }

    private void build() {
        content.setStyle("-fx-background-color: rgba(11,15,25,0.97);"
                + "-fx-background-radius: 18; -fx-border-color: rgba(0,242,254,0.25);"
                + "-fx-border-width: 1; -fx-border-radius: 18;"
                + "-fx-padding: 22 22 18 22; -fx-spacing: 0;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.65), 32, 0.2, 0, 8);");
        content.setMaxWidth(560);
        content.setMinWidth(480);

        // ── Title ─────────────────────────────────────────────────────────────
        Label eyebrow = new Label("DIRECT CONNECT");
        eyebrow.setStyle("-fx-font-size: 9; -fx-font-weight: 700; -fx-text-fill: #00f2fe;"
                + "-fx-letter-spacing: 2.0; -fx-opacity: 0.75;");
        Label title = new Label("IP / QR Connect");
        title.setStyle("-fx-font-size: 17; -fx-font-weight: 700; -fx-text-fill: #ffffff;");
        VBox titleBlock = new VBox(2, eyebrow, title);

        Region divider = new Region();
        divider.setMinHeight(1); divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-margin: 10 0;");

        // ── Two-column body ───────────────────────────────────────────────────
        // Left: QR code + label
        VBox qrCol = buildQrColumn();

        // Right: IP form
        buildFormBody();
        formBody.setSpacing(10);
        VBox.setVgrow(formBody, Priority.ALWAYS);

        HBox twoCol = new HBox(24, qrCol, formBody);
        twoCol.setAlignment(Pos.TOP_LEFT);
        twoCol.setStyle("-fx-padding: 14 0 0 0;");

        content.getChildren().addAll(titleBlock, divider, twoCol);

        // Draw QR for the local IP immediately
        drawQr("192.168.1.20");
    }

    // ── QR column ────────────────────────────────────────────────────────────

    private VBox buildQrColumn() {
        // Canvas inside a styled box
        StackPane qrBox = new StackPane(qrCanvas);
        qrBox.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10;"
                + "-fx-padding: 8; -fx-effect: dropshadow(gaussian,rgba(0,242,254,0.30),12,0.2,0,0);");
        qrBox.setMinSize(156, 156); qrBox.setPrefSize(156, 156); qrBox.setMaxSize(156, 156);

        Label scanLabel = new Label("SCAN TO CONNECT");
        scanLabel.setStyle("-fx-font-size: 9; -fx-font-weight: 700; -fx-text-fill: #00f2fe;"
                + "-fx-letter-spacing: 1.5; -fx-padding: 6 0 2 0;");

        qrIpLabel.setStyle("-fx-font-size: 10.5; -fx-font-weight: 600; -fx-text-fill: #94a3b8;");

        Label demoNote = new Label("Demo — visual only");
        demoNote.setStyle("-fx-font-size: 9; -fx-text-fill: rgba(148,163,184,0.5);");

        VBox col = new VBox(4, qrBox, scanLabel, qrIpLabel, demoNote);
        col.setAlignment(Pos.TOP_CENTER);
        col.setMinWidth(156);
        return col;
    }

    /**
     * Draws a QR-like visual grid on {@link #qrCanvas} for the given IP.
     * The grid is 21×21 cells (standard QR v1 size). Finder patterns are
     * drawn at the three required corners. Data cells are filled by XOR-ing
     * the IP byte values against a stable mask, giving a different but
     * deterministic pattern for each IP. Not a real QR code — demo only.
     */
    private void drawQr(String ip) {
        qrIpLabel.setText(ip);
        final int CELLS = 21;
        double cellSize = qrCanvas.getWidth() / CELLS;
        GraphicsContext g = qrCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, qrCanvas.getWidth(), qrCanvas.getHeight());

        // Background
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, qrCanvas.getWidth(), qrCanvas.getHeight());

        // Build data bit array from IP string bytes
        byte[] ipBytes = ip.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        boolean[] data = new boolean[CELLS * CELLS];
        for (int i = 0; i < data.length; i++) {
            int byteVal = ipBytes[i % ipBytes.length] & 0xFF;
            // Simple hash: XOR with position-derived seed
            data[i] = (((byteVal ^ (i * 37 + 17)) >> (i % 8)) & 1) == 1;
        }

        // Draw data cells (skip finder pattern zones: top-left 9×9, top-right 9×8, bottom-left 8×9)
        g.setFill(Color.web("#0b0f19"));
        for (int row = 0; row < CELLS; row++) {
            for (int col = 0; col < CELLS; col++) {
                if (isFinderZone(row, col, CELLS)) continue;
                // Timing pattern rows/cols
                if (row == 6 || col == 6) {
                    if ((row + col) % 2 == 0) fillCell(g, row, col, cellSize, Color.web("#0b0f19"));
                    continue;
                }
                if (data[row * CELLS + col]) {
                    fillCell(g, row, col, cellSize, Color.web("#0b0f19"));
                }
            }
        }

        // Draw the three finder patterns (7×7 with border)
        drawFinder(g, 0, 0, cellSize);
        drawFinder(g, 0, CELLS - 7, cellSize);
        drawFinder(g, CELLS - 7, 0, cellSize);

        // Cyan tint overlay on cells for the "glow" effect
        g.setFill(Color.web("#00f2fe", 0.04));
        g.fillRect(0, 0, qrCanvas.getWidth(), qrCanvas.getHeight());
    }

    private void fillCell(GraphicsContext g, int row, int col, double cellSize, Color color) {
        double margin = 0.5;
        g.setFill(color);
        g.fillRoundRect(col * cellSize + margin, row * cellSize + margin,
                cellSize - margin * 2, cellSize - margin * 2, 1.5, 1.5);
    }

    private void drawFinder(GraphicsContext g, int startRow, int startCol, double cs) {
        // Outer 7×7 black square
        g.setFill(Color.web("#0b0f19"));
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 7; c++) {
                boolean outerRing = r == 0 || r == 6 || c == 0 || c == 6;
                boolean inner3x3  = r >= 2 && r <= 4 && c >= 2 && c <= 4;
                if (outerRing || inner3x3) {
                    fillCell(g, startRow + r, startCol + c, cs, Color.web("#0b0f19"));
                } else {
                    fillCell(g, startRow + r, startCol + c, cs, Color.WHITE);
                }
            }
        }
        // Separator row/col of white
        for (int i = 0; i < 8; i++) {
            if (startRow + 7 < 21 && startRow >= 0)
                fillCell(g, startRow + 7, startCol + Math.min(i, 6), cs, Color.WHITE);
        }
    }

    private boolean isFinderZone(int r, int c, int n) {
        return (r < 9 && c < 9)          // top-left
            || (r < 9 && c >= n - 8)     // top-right
            || (r >= n - 8 && c < 9);    // bottom-left
    }

    // ── Form body ─────────────────────────────────────────────────────────────

    private void buildFormBody() {
        formBody.getChildren().clear();

        Label ipSectionLabel = new Label("Enter IP address");
        ipSectionLabel.setStyle("-fx-font-size: 11; -fx-font-weight: 700; -fx-text-fill: #94a3b8;"
                + "-fx-letter-spacing: 0.5;");

        ipField.getStyleClass().add("field");
        ipField.setPromptText("e.g. 192.168.1.24");
        ipField.textProperty().addListener((o, ov, nv) -> {
            ipField.getStyleClass().remove("error");
            errorLabel.setText("");
            // Live-update the QR code as the user types a valid-looking IP
            String v = nv == null ? "" : nv.trim();
            if (IPV4.matcher(v).matches()) drawQr(v);
        });
        ipField.setOnAction(e -> connect());

        errorLabel.getStyleClass().add("field-error-text");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        Label hint = new Label("Demo mode: handshake is simulated. No real network call is made.");
        hint.setStyle("-fx-font-size: 10; -fx-text-fill: rgba(148,163,184,0.6); -fx-wrap-text: true;");

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn", "btn-secondary");
        cancel.setOnAction(e -> close());

        Button connectBtn = new Button("Connect");
        connectBtn.setStyle("-fx-background-color: #00f2fe; -fx-background-radius: 9;"
                + "-fx-text-fill: #04141A; -fx-font-weight: 700; -fx-font-size: 12;"
                + "-fx-cursor: hand; -fx-padding: 8 20;");
        connectBtn.setOnAction(e -> connect());

        Region btnSpacer = new Region();
        HBox.setHgrow(btnSpacer, Priority.ALWAYS);
        HBox actions = new HBox(10, btnSpacer, cancel, connectBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        formBody.getChildren().addAll(ipSectionLabel, ipField, errorLabel, hint, actions);
    }

    public void show() {
        modal = new Modal(root);
        modal.open(content);
        ipField.requestFocus();
    }

    private void close() {
        if (modal != null) modal.close();
    }

    // ── Connect flow ──────────────────────────────────────────────────────────

    private void connect() {
        String value = ipField.getText().trim();
        if (!isValid(value)) {
            ipField.getStyleClass().add("error");
            errorLabel.setText("Enter a valid IPv4 address or hostname.");
            errorLabel.setManaged(true);
            errorLabel.setVisible(true);
            return;
        }
        drawQr(value);
        renderConnecting(value);
    }

    private void renderConnecting(String ip) {
        formBody.getChildren().clear();

        Region dot = new Region();
        dot.setMinSize(12, 12);
        dot.setStyle("-fx-background-color: #00f2fe; -fx-background-radius: 6;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,242,254,0.6), 8, 0.5, 0, 0);");
        Label status = new Label("Connecting to " + ip + "…");
        status.setStyle("-fx-font-size: 13; -fx-font-weight: 600; -fx-text-fill: #ffffff;");
        HBox row = new HBox(12, dot, status);
        row.setAlignment(Pos.CENTER_LEFT);
        formBody.getChildren().add(row);

        Timeline t = new Timeline(new KeyFrame(Duration.millis(1400), e -> renderDone(ip)));
        t.setCycleCount(1);
        t.play();

        FadeTransition pulse = new FadeTransition(Duration.millis(600), dot);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.30);
        pulse.setCycleCount(FadeTransition.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();
    }

    private void renderDone(String ip) {
        formBody.getChildren().clear();

        Label done = new Label("Simulated handshake complete");
        done.setStyle("-fx-font-size: 13; -fx-font-weight: 600; -fx-text-fill: #ffffff;");
        HBox row = new HBox(10, Icons.check(18, Color.web("#41c9a2")), done);
        row.setAlignment(Pos.CENTER_LEFT);

        Label notice = new Label(
                "Demo mode — no real connection was made to " + ip + ". "
                + "In the full app, this scans the QR / IP and performs a secure handshake.");
        notice.setWrapText(true);
        notice.setStyle("-fx-font-size: 10.5; -fx-text-fill: #94a3b8;");
        VBox noticeBox = new VBox(notice);
        noticeBox.setStyle("-fx-background-color: rgba(155,135,255,0.10);"
                + "-fx-border-color: rgba(155,135,255,0.40);"
                + "-fx-background-radius: 9; -fx-border-radius: 9; -fx-padding: 9 12;");

        Button doneBtn = new Button("Done");
        doneBtn.setStyle("-fx-background-color: #00f2fe; -fx-background-radius: 9;"
                + "-fx-text-fill: #04141A; -fx-font-weight: 700; -fx-font-size: 12;"
                + "-fx-cursor: hand; -fx-padding: 8 20;");
        doneBtn.setOnAction(e -> close());
        HBox actions = new HBox(doneBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        formBody.getChildren().addAll(row, noticeBox, actions);
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
}
