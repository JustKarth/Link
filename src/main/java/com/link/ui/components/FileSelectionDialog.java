package com.link.ui.components;

import com.link.ui.demo.DemoDevice;
import com.link.ui.viewmodel.LinkViewModel;
import com.link.ui.theme.Theme;
import com.link.ui.util.FormatUtil;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Multi-step "Send Files" flow:
 * <ol>
 *   <li><b>Select</b> — drag-and-drop or open a native {@link FileChooser} to
 *       add real files (metadata only). Each file is listed with type icon,
 *       size and a remove control, plus a running total.</li>
 *   <li><b>Review</b> — shows the destination device and the files, then a
 *       Send action that submits a real transfer request to the backend.</li>
 * </ol>
 * On confirm it calls {@link LinkViewModel#addOutgoingTransfers} and only
 * navigates away when at least one transfer was successfully registered.
 * If the destination peer is no longer reachable an inline error is shown
 * inside the dialog so the user can decide what to do.
 */
public class FileSelectionDialog {

    private enum Step { SELECT, REVIEW }

    private final StackPane root;
    private final LinkViewModel state;
    private final DemoDevice destination;
    private final Runnable onSent;

    private Modal modal;
    private final VBox content = new VBox();
    private final VBox body = new VBox();

    private final List<File> files = new ArrayList<>();
    private final Set<String> paths = new HashSet<>();
    private Runnable refreshListRef;

    public FileSelectionDialog(StackPane root, LinkViewModel state, DemoDevice destination, Runnable onSent) {
        this.root = root;
        this.state = state;
        this.destination = destination;
        this.onSent = onSent;
        content.getStyleClass().add("dialog");
        content.setSpacing(14);
        render(Step.SELECT);
    }

    public void show() {
        modal = new Modal(root);
        modal.open(content);
    }

    private void close() {
        if (modal != null) modal.close();
    }

    // ---- Select step ------------------------------------------------------

    private void renderSelect() {
        body.getChildren().clear();

        Label title = new Label("Send Files");
        title.getStyleClass().add("dialog-title");
        Label dest = new Label("to " + (destination == null ? "a device" : destination.getName()));
        dest.getStyleClass().add("dialog-subtitle");

        VBox drop = new VBox(8);
        drop.getStyleClass().add("drop-area");
        drop.getChildren().addAll(
                Icons.send(26, Theme.TEXT_3),
                new LabelStyled("Drag files here", "drop-title"),
                new LabelStyled("or click to browse", "drop-desc"));
        drop.setAlignment(javafx.geometry.Pos.CENTER);

        drop.setOnMouseClicked(e -> openFileChooser());
        drop.setOnDragOver(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
                drop.getStyleClass().add("over");
            }
            e.consume();
        });
        drop.setOnDragExited(e -> drop.getStyleClass().remove("over"));
        drop.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles()) addFiles(db.getFiles());
            e.setDropCompleted(true);
            e.consume();
        });

        // Selected files list
        VBox fileList = new VBox(7);
        VBox.setVgrow(fileList, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(fileList);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(180);
        scroll.setMaxHeight(220);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent;");

        Label totals = new Label(" ");
        totals.getStyleClass().add("tiny");

        Button addBtn = new Button("Add Files");
        addBtn.getStyleClass().addAll("btn", "btn-secondary");
        addBtn.setGraphic(Icons.plus(14, Theme.TEXT_2));
        addBtn.setOnAction(e -> openFileChooser());

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn", "btn-secondary");
        cancel.setOnAction(e -> close());

        Button review = new Button("Review");
        review.getStyleClass().addAll("btn", "btn-primary");
        review.setGraphic(Icons.chevronRight(15, Theme.BG.darker()));
        review.setOnAction(e -> { if (!files.isEmpty()) render(Step.REVIEW); });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, addBtn, spacer, cancel, review);
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        body.getChildren().addAll(title, dest, drop, scroll, totals, actions);

        // Keep the Review button in sync with the current selection.
        this.refreshListRef = () -> {
            fileList.getChildren().clear();
            for (File f : files) fileList.getChildren().add(fileRow(f, () -> {
                files.remove(f);
                paths.remove(f.getAbsolutePath());
                refreshListRef.run();
            }));
            updateTotals(totals);
            review.setDisable(files.isEmpty());
        };
        refreshListRef.run();
    }

    private HBox fileRow(File f, Runnable onRemove) {
        HBox row = new HBox(10);
        row.getStyleClass().add("sel-file-row");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        FormatUtil.FileCategory cat = FormatUtil.categorize(f.getName());
        StackPane iconWrap = new StackPane(Icons.fileIcon(16, cat));
        iconWrap.setMinSize(30, 30);
        iconWrap.setPrefSize(30, 30);
        iconWrap.setStyle("-fx-background-color: " + Theme.toRgba(Theme.softFor(cat), 0.16) + ";"
                + "-fx-background-radius: 8;");

        Label name = new Label(f.getName());
        name.getStyleClass().add("sel-file-name");
        HBox.setHgrow(name, Priority.ALWAYS);
        Label size = new Label(FormatUtil.formatBytes(f.length()));
        size.getStyleClass().add("sel-file-size");

        Button remove = iconButton(Icons.close(14, Theme.TEXT_3));
        remove.setOnAction(e -> onRemove.run());

        row.getChildren().addAll(iconWrap, name, size, remove);
        return row;
    }

    private Button iconButton(javafx.scene.Node glyph) {
        Button b = new Button();
        b.setGraphic(glyph);
        b.getStyleClass().add("btn-icon");
        return b;
    }

    private void updateTotals(Label totals) {
        if (files.isEmpty()) {
            totals.setText("No files selected yet");
        } else {
            long sum = 0;
            for (File f : files) sum += f.length();
            totals.setText(files.size() + " file" + (files.size() == 1 ? "" : "s")
                    + " · " + FormatUtil.formatBytes(sum));
        }
    }

    private void addFiles(List<File> incoming) {
        for (File f : incoming) {
            if (f == null || !f.isFile()) continue;
            if (paths.add(f.getAbsolutePath())) files.add(f);
        }
        if (refreshListRef != null) refreshListRef.run();
    }

    private void openFileChooser() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Files to Send");
        List<File> chosen = fc.showOpenMultipleDialog(root.getScene().getWindow());
        if (chosen != null) addFiles(chosen);
    }

    // ---- Review step ------------------------------------------------------

    private void renderReview() {
        body.getChildren().clear();

        Label title = new Label("Review transfer");
        title.getStyleClass().add("dialog-title");
        Label dest = new Label("Sending " + files.size() + " file" + (files.size() == 1 ? "" : "s")
                + " to " + destination.getName());
        dest.getStyleClass().add("dialog-subtitle");

        // Destination mini card
        HBox destRow = new HBox(10,
                Icons.device(22, destination.getType().name(), Theme.TEXT_2),
                new LabelStyled(destination.getName(), "device-name"));
        destRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        destRow.setStyle("-fx-background-color: " + Theme.toRgba(Theme.PANEL_2, 1) + ";"
                + "-fx-background-radius: 9; -fx-padding: 9 12; -fx-border-color: " + Theme.toRgba(Theme.BORDER_SOFT) + ";");

        VBox fileList = new VBox(6);
        ScrollPane scroll = new ScrollPane(fileList);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(160);
        scroll.setMaxHeight(200);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        long sum = 0;
        for (File f : files) {
            fileList.getChildren().add(reviewFileRow(f));
            sum += f.length();
        }

        Label total = new Label(files.size() + " files · " + FormatUtil.formatBytes(sum));
        total.getStyleClass().add("tiny");

        Label notice = new Label(
                "Sending a transfer request to " + destination.getName() + ". "
                + "The receiving device will need to accept it before any files are transferred.");
        notice.getStyleClass().add("notice-text");
        notice.setWrapText(true);
        VBox noticeBox = new VBox(notice);
        noticeBox.getStyleClass().addAll("notice", "notice-info");

        Button back = new Button("Back");
        back.getStyleClass().addAll("btn", "btn-secondary");
        back.setOnAction(e -> render(Step.SELECT));

        Button send = new Button("Send Request");
        send.getStyleClass().addAll("btn", "btn-primary");
        send.setGraphic(Icons.send(15, Theme.BG.darker()));

        // Error label shown inline when the peer is unreachable at send time.
        Label sendError = new Label();
        sendError.getStyleClass().add("field-error-text");
        sendError.setWrapText(true);
        sendError.setManaged(false);
        sendError.setVisible(false);

        send.setOnAction(e -> confirmSend(send, sendError));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, back, spacer, send);
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        body.getChildren().addAll(title, dest, destRow, scroll, total, noticeBox, sendError, actions);
    }

    private HBox reviewFileRow(File f) {
        FormatUtil.FileCategory cat = FormatUtil.categorize(f.getName());
        StackPane iconWrap = new StackPane(Icons.fileIcon(16, cat));
        iconWrap.setMinSize(28, 28);
        iconWrap.setPrefSize(28, 28);
        iconWrap.setStyle("-fx-background-color: " + Theme.toRgba(Theme.softFor(cat), 0.16) + ";"
                + "-fx-background-radius: 7;");
        Label name = new Label(f.getName());
        name.getStyleClass().add("sel-file-name");
        HBox.setHgrow(name, Priority.ALWAYS);
        Label size = new Label(FormatUtil.formatBytes(f.length()));
        size.getStyleClass().add("sel-file-size");
        HBox row = new HBox(10, iconWrap, name, size);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: " + Theme.toRgba(Theme.PANEL_2, 1) + ";"
                + "-fx-background-radius: 8; -fx-padding: 7 10;");
        return row;
    }

    private void confirmSend(Button sendBtn, Label errorLabel) {
        sendBtn.setDisable(true);
        LinkViewModel.SendResult result = state.addOutgoingTransfers(files, destination);

        if (!result.succeeded()) {
            // Show inline error; keep the dialog open so the user can act.
            String msg = switch (result.getFailure()) {
                case PEER_NOT_FOUND ->
                        destination.getName() + " is no longer reachable. "
                        + "Close this dialog, wait for the device to reappear, and try again.";
                case COORDINATOR_ERROR -> {
                    String detail = result.getErrorDetail();
                    yield "Transfer request failed"
                            + (detail != null && !detail.isBlank() ? ": " + detail : ".")
                            + " Please try again.";
                }
            };
            errorLabel.setText(msg);
            errorLabel.setManaged(true);
            errorLabel.setVisible(true);
            sendBtn.setDisable(false);
            return;
        }

        close();
        if (onSent != null) onSent.run();
    }

    // ---- Routing ----------------------------------------------------------

    private void render(Step step) {
        content.getChildren().clear();
        content.getChildren().add(body);
        if (step == Step.SELECT) renderSelect();
        else renderReview();
    }

    /** Small helper to build a labelled styled Label. */
    private static final class LabelStyled extends Label {
        LabelStyled(String text, String styleClass) {
            super(text);
            getStyleClass().add(styleClass);
        }
    }
}
