package com.link.ui.components;

import com.link.ui.theme.Theme;
import com.link.ui.viewmodel.LinkViewModel;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * SAS pairing confirmation dialog.
 *
 * <p>Shown when the backend emits a {@code PAIRING} state for a transfer,
 * signaling that the user must compare a six-digit SAS code with the code
 * displayed on the remote device before the transfer can proceed.
 *
 * <p>Must be opened on the JavaFX Application Thread. The dialog uses the
 * existing {@link Modal} component and Link's CSS theme — no new dependencies.
 *
 * <h3>Duplicate-prompt guard</h3>
 * The caller (MainShell) tracks which transfer IDs have an open dialog in a
 * {@code Set<String>}. This class itself is stateless after construction; the
 * caller removes the ID from the set in the {@code onFinished} callback.
 */
public class SasPairingDialog {

    private final StackPane overlayRoot;
    private final LinkViewModel viewModel;
    private final String transferId;
    private final String peerName;
    private final String sasCode;
    private final Runnable onFinished;  // called after accept OR reject (dialog fully closed)

    private Modal modal;
    private boolean decided = false;   // prevents duplicate confirm calls

    public SasPairingDialog(StackPane overlayRoot, LinkViewModel viewModel,
                             String transferId, String peerName,
                             String sasCode, Runnable onFinished) {
        this.overlayRoot = overlayRoot;
        this.viewModel   = viewModel;
        this.transferId  = transferId;
        this.peerName    = peerName;
        this.sasCode     = sasCode;
        this.onFinished  = onFinished;
    }

    public void show() {
        modal = new Modal(overlayRoot);
        modal.setOnClose(onFinished);
        modal.open(buildContent());
    }

    /** Close without making a decision (e.g., transfer was cancelled externally). */
    public void dismiss() {
        if (modal != null && modal.isOpen()) {
            decided = true; // suppress onFinished double-call via setOnClose
            modal.close();
        }
    }

    // ── Content ───────────────────────────────────────────────────────────────

    private VBox buildContent() {
        // Eyebrow
        Label eyebrow = new Label("SECURITY VERIFICATION");
        eyebrow.setStyle("-fx-font-size: 9; -fx-font-weight: 700;"
                + "-fx-text-fill: #00f2fe; -fx-letter-spacing: 2.0; -fx-opacity: 0.80;");

        // Title
        Label title = new Label("Compare security codes");
        title.getStyleClass().add("dialog-title");

        // Instruction
        Label instruction = new Label(
                "A transfer from " + peerName + " requires identity verification. "
                + "Compare the code below with the code shown on " + peerName + "'s device. "
                + "Only accept if they match exactly.");
        instruction.setWrapText(true);
        instruction.getStyleClass().add("notice-text");

        // SAS code display
        Label codeLabel = new Label(sasCode);
        codeLabel.setStyle(
                "-fx-font-size: 34; -fx-font-weight: 700; -fx-text-fill: #00f2fe;"
                + "-fx-letter-spacing: 8;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,242,254,0.55), 14, 0.3, 0, 0);");
        codeLabel.setAlignment(Pos.CENTER);

        VBox codeBox = new VBox(codeLabel);
        codeBox.setAlignment(Pos.CENTER);
        codeBox.setStyle(
                "-fx-background-color: rgba(0,242,254,0.08);"
                + "-fx-background-radius: 14;"
                + "-fx-border-color: rgba(0,242,254,0.30);"
                + "-fx-border-width: 1; -fx-border-radius: 14;"
                + "-fx-padding: 18 28 18 28;");

        Label codeHint = new Label("Both devices must show this code");
        codeHint.setStyle("-fx-font-size: 10.5; -fx-text-fill: #8D9AAA;");
        codeHint.setAlignment(Pos.CENTER);

        // Buttons
        Button accept = new Button("Accept — codes match");
        accept.getStyleClass().addAll("btn", "btn-accept");
        accept.setDefaultButton(true);
        accept.setOnAction(e -> decide(true));

        Button reject = new Button("Reject — codes differ");
        reject.getStyleClass().addAll("btn", "btn-reject");
        reject.setCancelButton(true);
        reject.setOnAction(e -> decide(false));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, reject, spacer, accept);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(14,
                new VBox(4, eyebrow, title),
                instruction,
                codeBox,
                codeHint,
                actions);
        content.getStyleClass().add("dialog");
        content.setMaxWidth(440);
        content.setMinWidth(360);
        return content;
    }

    // ── Decision ──────────────────────────────────────────────────────────────

    private void decide(boolean accepted) {
        if (decided) return;
        decided = true;
        viewModel.confirmPairing(transferId, accepted);
        if (modal != null) modal.close();
        // onFinished fires via modal.setOnClose after the fade completes
    }
}
