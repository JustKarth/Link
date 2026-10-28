package com.link.ui.views;

import com.link.ui.components.Icons;
import com.link.ui.components.ToggleSwitch;
import com.link.ui.demo.DemoDevice;
import com.link.ui.viewmodel.LinkViewModel;
import com.link.ui.theme.Theme;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Settings screen. Four grouped sections (General, Discovery, Transfers,
 * Notifications) with toggles, a text field and a directory chooser. All
 * values are persisted across restarts via {@link com.link.ui.demo.SettingsModel}.
 */
public class SettingsView implements View {

    private final LinkViewModel state;
    private final VBox root = new VBox();

    private final TextField deviceName = new TextField();
    private final Label deviceNameError = new Label();
    private final ToggleSwitch mockDiscovery = new ToggleSwitch();
    private final ToggleSwitch discoverable = new ToggleSwitch();
    private final ToggleSwitch approval = new ToggleSwitch();
    private final ToggleSwitch notifications = new ToggleSwitch();
    private final Label downloadPath = new Label();
    private final Label downloadPathError = new Label();
    private final Label selfInfoLabel = new Label();
    private final Label versionLabel = new Label();

    public SettingsView(LinkViewModel state) {
        this.state = state;
        build();
        refresh();
    }

    private void build() {
        root.getStyleClass().add("content");
        root.setSpacing(16);

        VBox titleBox = new VBox(4,
                new StyledLabel(title(), "page-title"),
                new StyledLabel(subtitle(), "page-subtitle"));
        HBox header = new HBox(titleBox, spacer());
        header.getStyleClass().add("content-header");
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        // ---- General ----
        deviceName.getStyleClass().add("field");
        deviceName.setPrefWidth(260);
        deviceName.setOnAction(e -> commitName());
        deviceName.focusedProperty().addListener((o, ov, focused) -> { if (!focused) commitName(); });

        deviceNameError.getStyleClass().add("field-error-text");
        deviceNameError.setWrapText(true);
        deviceNameError.setManaged(false);
        deviceNameError.setVisible(false);

        VBox deviceNameControl = new VBox(4, deviceName, deviceNameError);

        VBox general = section("GENERAL",
                row("Device display name",
                        "Shown to nearby devices when you are discoverable.",
                        deviceNameControl),
                row("Current device",
                        "This device's identity.",
                        selfInfoLabel),
                row("Application version",
                        "Application build label.",
                        versionLabel));

        // ---- Discovery ----
        wireToggle(mockDiscovery, state.getSettings().isMockDiscoveryEnabled(),
                state::setDiscoveryEnabled);
        wireToggle(discoverable, state.getSettings().isDiscoverable(), on -> {
            state.getSettings().setDiscoverable(on);
            state.fireChange();
        });

        VBox discovery = section("DISCOVERY",
                row("Discovery",
                        "Scan for nearby devices on the local network.",
                        mockDiscovery),
                row("Discoverable",
                        "Let nearby devices see this device and send you files.",
                        discoverable));

        // ---- Transfers ----
        Button browse = new Button("Browse");
        browse.getStyleClass().addAll("btn", "btn-secondary");
        browse.setGraphic(Icons.folder(15, Theme.TEXT_2));
        browse.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Choose Download Destination");
            File home = new File(state.getSettings().getDownloadDestination());
            if (home.isDirectory()) dc.setInitialDirectory(home);
            File chosen = dc.showDialog(root.getScene().getWindow());
            if (chosen != null && chosen.isDirectory()) {
                String chosenPath = chosen.getAbsolutePath();
                String error = validateDownloadPath(chosenPath);
                if (error == null) {
                    state.getSettings().setDownloadDestination(chosenPath);
                    downloadPath.setText(shorten(chosenPath));
                    hidePathError();
                    state.fireChange();
                } else {
                    showPathError(error);
                }
            }
        });
        downloadPath.getStyleClass().add("device-meta");
        downloadPathError.getStyleClass().add("field-error-text");
        downloadPathError.setWrapText(true);
        downloadPathError.setManaged(false);
        downloadPathError.setVisible(false);
        VBox downloadControl = new VBox(4,
                new HBox(10, downloadPath, browse),
                downloadPathError);

        wireToggle(approval, state.getSettings().isRequireApprovalForIncoming(), on -> {
            state.getSettings().setRequireApprovalForIncoming(on);
            state.fireChange();
        });

        VBox transfers = section("TRANSFERS",
                row("Download destination",
                        "Where received files are saved.",
                        downloadControl),
                row("Approve incoming transfers",
                        "Ask before accepting files sent to this device.",
                        approval));

        // ---- Notifications ----
        wireToggle(notifications, state.getSettings().isDesktopNotifications(), on -> {
            state.getSettings().setDesktopNotifications(on);
            state.fireChange();
        });

        VBox notif = section("NOTIFICATIONS",
                row("Desktop notifications",
                        "Notify on completed, failed and incoming transfers.",
                        notifications));

        VBox body = new VBox(18, general, discovery, transfers, notif);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(header, scroll);
    }

    private static final int MAX_DEVICE_NAME_LENGTH = 64;

    private void commitName() {
        String v = deviceName.getText();
        if (v == null || v.isBlank()) {
            showNameError("Name cannot be blank.");
            deviceName.setText(state.getSettings().getDeviceDisplayName());
            return;
        }
        String trimmed = v.trim();
        if (trimmed.length() > MAX_DEVICE_NAME_LENGTH) {
            showNameError("Name must be " + MAX_DEVICE_NAME_LENGTH + " characters or fewer.");
            deviceName.setText(state.getSettings().getDeviceDisplayName());
            return;
        }
        hideNameError();
        state.getSettings().setDeviceDisplayName(trimmed);
        state.fireChange();
    }

    private void showNameError(String message) {
        deviceName.getStyleClass().remove("error");
        deviceName.getStyleClass().add("error");
        deviceNameError.setText(message);
        deviceNameError.setManaged(true);
        deviceNameError.setVisible(true);
    }

    private void hideNameError() {
        deviceName.getStyleClass().remove("error");
        deviceNameError.setManaged(false);
        deviceNameError.setVisible(false);
    }

    /**
     * Returns {@code null} if the path is usable, otherwise an error message.
     * A path is considered usable when it exists as a directory, or the parent
     * directory exists and the path could be created.
     */
    private static String validateDownloadPath(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            return "Download destination cannot be blank.";
        }
        Path p;
        try {
            p = Path.of(pathStr);
        } catch (InvalidPathException e) {
            return "Invalid path: " + e.getReason();
        }
        if (Files.isDirectory(p)) return null;    // already exists — fine
        // Check whether it can be created.
        Path parent = p.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            return "Cannot use this location — the parent directory does not exist.";
        }
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            return "Could not create directory: " + e.getMessage();
        }
        return null;
    }

    private void showPathError(String message) {
        downloadPathError.setText(message);
        downloadPathError.setManaged(true);
        downloadPathError.setVisible(true);
    }

    private void hidePathError() {
        downloadPathError.setManaged(false);
        downloadPathError.setVisible(false);
    }

    private void wireToggle(ToggleSwitch t, boolean initial, ToggleSwitch.Consumer handler) {
        t.setOn(initial, false);
        t.setOnChange(handler);
    }

    private VBox section(String title, HBox... rows) {
        VBox box = new VBox(0);
        box.getStyleClass().add("settings-section");
        Label t = new Label(title);
        t.getStyleClass().add("setting-section-title");
        box.getChildren().add(t);
        for (int i = 0; i < rows.length; i++) {
            if (rows.length == 1) {
                rows[i].getStyleClass().add("single");
            } else {
                if (i == 0) rows[i].getStyleClass().add("first");
                if (i == rows.length - 1) rows[i].getStyleClass().add("last");
            }
            box.getChildren().add(rows[i]);
        }
        return box;
    }

    private HBox row(String label, String desc, Node control) {
        VBox text = new VBox(2, new StyledLabel(label, "setting-label"),
                new StyledLabel(desc, "setting-desc"));
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(16, text, control);
        row.getStyleClass().add("settings-row");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    @Override public void refresh() {
        // Update toggles without re-firing their handlers.
        mockDiscovery.setOn(state.getSettings().isMockDiscoveryEnabled(), false);
        discoverable.setOn(state.getSettings().isDiscoverable(), false);
        approval.setOn(state.getSettings().isRequireApprovalForIncoming(), false);
        notifications.setOn(state.getSettings().isDesktopNotifications(), false);

        downloadPath.setText(shorten(state.getSettings().getDownloadDestination()));

        // Validate the current download path and show an error if it is unusable.
        String pathErr = validateDownloadPath(state.getSettings().getDownloadDestination());
        if (pathErr != null) showPathError(pathErr); else hidePathError();

        DemoDevice self = state.getThisDevice();
        selfInfoLabel.setText(self.getName() + " · " + self.getOsLabel() + " · " + self.getIpAddress());
        selfInfoLabel.getStyleClass().setAll("device-meta");
        versionLabel.setText("1.0.0");
        versionLabel.getStyleClass().setAll("device-meta");

        if (!deviceName.isFocused()) {
            deviceName.setText(state.getSettings().getDeviceDisplayName());
            hideNameError();
        }
    }

    private static String shorten(String path) {
        if (path == null) return "";
        if (path.length() <= 44) return path;
        return "…" + path.substring(path.length() - 43);
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    @Override public String title() { return "Settings"; }
    @Override public String subtitle() { return "Preferences are saved automatically."; }

    public Pane root() { return root; }

    private static final class StyledLabel extends Label {
        StyledLabel(String text, String styleClass) {
            super(text);
            getStyleClass().add(styleClass);
        }
    }
}
