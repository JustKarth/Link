package com.link.ui.components;

import com.link.ui.demo.DemoTransfer;
import com.link.ui.demo.TransferDirection;
import com.link.ui.demo.TransferStatus;
import com.link.ui.viewmodel.LinkViewModel;
import com.link.ui.theme.Theme;
import com.link.ui.util.FormatUtil;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A transfer card shown on the Transfers screen. Built once per transfer and
 * updated in place on each refresh so the progress bar advances smoothly
 * without rebuilding the card. Includes demo-only pause / resume / cancel /
 * retry controls that mutate the in-memory state consistently.
 */
public class TransferCard extends VBox {

    private final LinkViewModel state;
    private final String transferId;

    private final Region fill = new Region();
    private final DoubleProperty progressProp = new SimpleDoubleProperty(0);
    private final Label percent = new Label();
    private final Label pace = new Label();
    private final Label pill = new Label();
    private final Label dirBadge = new Label();
    private final Label name = new Label();
    private final Label meta = new Label();
    private final HBox controls = new HBox(8);
    private final HBox paceRow = new HBox(8);
    private final StackPane iconWrap = new StackPane();

    public TransferCard(DemoTransfer t, LinkViewModel state) {
        this.state = state;
        this.transferId = t.getId();
        getStyleClass().addAll("card", "transfer-card");
        setSpacing(10);

        // File icon
        FormatUtil.FileCategory cat = FormatUtil.categorize(t.getFileName());
        iconWrap.getStyleClass().add("file-icon-wrap");
        iconWrap.setStyle("-fx-background-color: " + Theme.toRgba(Theme.softFor(cat), 0.16) + ";");
        iconWrap.getChildren().add(Icons.fileIcon(18, cat));

        name.getStyleClass().add("transfer-name");
        meta.getStyleClass().add("transfer-meta");

        pill.getStyleClass().add("pill");

        // Direction badge — explicit OUTGOING/INCOMING visual
        dirBadge.getStyleClass().addAll("dir-badge",
                t.getDirection() == TransferDirection.OUTGOING ? "dir-badge-out" : "dir-badge-in");
        dirBadge.setText(t.getDirection() == TransferDirection.OUTGOING ? "↑ OUTGOING" : "↓ INCOMING");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(10, iconWrap, col(name, meta), spacer, dirBadge, pill);
        top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Progress track
        StackPane track = new StackPane(fill);
        track.getStyleClass().add("progress-track");
        StackPane.setAlignment(fill, javafx.geometry.Pos.CENTER_LEFT);
        fill.getStyleClass().add("progress-fill");
        fill.setMaxWidth(Region.USE_PREF_SIZE);
        fill.prefWidthProperty().bind(
                javafx.beans.binding.Bindings.multiply(track.widthProperty(), progressProp));

        percent.getStyleClass().add("tiny");
        pace.getStyleClass().add("tiny");
        paceRow.getChildren().addAll(track, spacer, percent, pace);
        HBox.setHgrow(track, Priority.ALWAYS);

        controls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        controls.setManaged(false);
        controls.setVisible(false);

        getChildren().addAll(top, paceRow, controls);
        update(t);
    }

    private VBox col(Label... labels) {
        VBox v = new VBox(2);
        v.getChildren().addAll(labels);
        HBox.setHgrow(v, Priority.ALWAYS);
        return v;
    }

    public String getTransferId() { return transferId; }

    /** Update the dynamic parts of the card from the current transfer state. */
    public void update(DemoTransfer t) {
        name.setText(t.getFileName());
        String dirLabel = t.getDirection() == TransferDirection.OUTGOING
                ? "to " + t.getDeviceName()
                : "from " + t.getDeviceName();
        meta.setText(FormatUtil.formatBytes(t.getFileSize()) + " · " + dirLabel);

        progressProp.set(t.getProgress());
        percent.setText(Math.round(t.getProgress() * 100) + "%");

        // Progress fill colour by direction / status
        fill.getStyleClass().removeAll("incoming", "completed", "failed", "paused", "cancelled");
        String fillStyle = switch (t.getStatus()) {
            case COMPLETED -> "completed";
            case FAILED -> "failed";
            case PAUSED -> "paused";
            case CANCELLED -> "cancelled";
            default -> t.getDirection() == TransferDirection.INCOMING ? "incoming" : "";
        };
        if (!fillStyle.isBlank()) fill.getStyleClass().add(fillStyle);

        // Status pill
        pill.getStyleClass().removeAll("pill-progress", "pill-completed", "pill-failed",
                "pill-paused", "pill-cancelled", "pill-pending");
        String[] pillInfo = pillFor(t.getStatus());
        pill.setText(pillInfo[0]);
        pill.getStyleClass().add(pillInfo[1]);

        // Pace + demo tag only while actively transferring
        boolean active = t.getStatus() == TransferStatus.IN_PROGRESS;
        if (active) {
            long speed = t.getSpeedBytesPerSecond();
            long remainingBytes = (long) ((1.0 - t.getProgress()) * Math.max(1, t.getFileSize()));
            long etaSec = speed > 0 ? remainingBytes / speed : 0;
            pace.setText(FormatUtil.formatSpeed(speed) + " · " + FormatUtil.formatEta(etaSec));
            pace.setVisible(true);
            percent.setVisible(true);
        } else if (t.getStatus() == TransferStatus.PAUSED || t.getStatus() == TransferStatus.PENDING) {
            pace.setText(t.getStatus() == TransferStatus.PAUSED ? "Paused" : "Waiting…");
            pace.setVisible(true);
            percent.setVisible(t.getProgress() > 0);
        } else {
            pace.setVisible(false);
            percent.setVisible(false);
        }

        // Controls (demo only)
        controls.getChildren().clear();
        boolean showControls = false;
        switch (t.getStatus()) {
            case IN_PROGRESS -> { controls.getChildren().addAll(iconBtn(Icons.pause(15, Theme.TEXT_2),
                    () -> state.pauseTransfer(transferId)), iconBtn(Icons.cancel(15, Theme.TEXT_2),
                    () -> state.cancelTransfer(transferId))); showControls = true; }
            case PAUSED -> { controls.getChildren().addAll(iconBtn(Icons.play(15, Theme.TEXT_2),
                    () -> state.resumeTransfer(transferId)), iconBtn(Icons.cancel(15, Theme.TEXT_2),
                    () -> state.cancelTransfer(transferId))); showControls = true; }
            case PENDING -> {
                    if (t.getDirection() == TransferDirection.INCOMING) {
                        Button accept = textBtn("Accept",
                                () -> state.acceptTransfer(transferId));
                        Button reject = textBtn("Reject",
                                () -> state.rejectTransfer(transferId));

                        accept.getStyleClass().add("btn-accept");
                        reject.getStyleClass().add("btn-reject");

                        controls.getChildren().addAll(accept, reject);
                    } else {
                        controls.getChildren().add(iconBtn(
                                Icons.cancel(15, Theme.TEXT_2),
                                () -> state.cancelTransfer(transferId)));
                    }
                    showControls = true;
                }
            case FAILED, CANCELLED -> { controls.getChildren().add(textBtn("Retry",
                    () -> state.retryTransfer(transferId))); showControls = true; }
            default -> { /* COMPLETED: no controls */ }
        }
        controls.setManaged(showControls);
        controls.setVisible(showControls);
    }

    private Button iconBtn(javafx.scene.Node glyph, Runnable action) {
        Button b = new Button();
        b.setGraphic(glyph);
        b.getStyleClass().add("btn-icon");
        b.setOnAction(e -> action.run());
        return b;
    }

    private Button textBtn(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().addAll("btn", "btn-ghost");
        b.setOnAction(e -> action.run());
        return b;
    }

    private String[] pillFor(TransferStatus s) {
        return switch (s) {
            case PENDING -> new String[]{"Pending", "pill-pending"};
            case IN_PROGRESS -> new String[]{"In progress", "pill-progress"};
            case PAUSED -> new String[]{"Paused", "pill-paused"};
            case COMPLETED -> new String[]{"Completed", "pill-completed"};
            case FAILED -> new String[]{"Failed", "pill-failed"};
            case CANCELLED -> new String[]{"Cancelled", "pill-cancelled"};
        };
    }
}
