package com.link.ui.views;

import com.link.ui.components.Icons;
import com.link.ui.components.ToggleSwitch;
import com.link.ui.demo.DemoDataFactory;
import com.link.ui.demo.DemoDevice;
import com.link.ui.prototype.PrototypeState;
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

/**
 * Settings screen. Four grouped sections (General, Discovery, Transfers,
 * Notifications) with toggles, a text field and a directory chooser. All
 * values live in {@link PrototypeState}'s in-memory settings; nothing is
 * persisted — settings reset on restart (documented in the README).
 */
public class SettingsView implements View {

    private final PrototypeState state;
    private final VBox root = new VBox();

    private final TextField deviceName = new TextField();
    private final ToggleSwitch mockDiscovery = new ToggleSwitch();
    private final ToggleSwitch discoverable = new ToggleSwitch();
    private final ToggleSwitch approval = new ToggleSwitch();
    private final ToggleSwitch notifications = new ToggleSwitch();
    private final Label downloadPath = new Label();
    private final Label selfInfoLabel = new Label();
    private final Label versionLabel = new Label();

    public SettingsView(PrototypeState state) {
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

        VBox general = section("GENERAL",
                row("Device display name",
                        "Shown to nearby devices when you are discoverable.",
                        deviceName),
                row("Current device",
                        "This prototype's identity (read-only demo data).",
                        selfInfoLabel),
                row("Application version",
                        "Prototype build label.",
                        versionLabel));

        // ---- Discovery ----
        wireToggle(mockDiscovery, state.getSettings().isMockDiscoveryEnabled(),
                state::setDiscoveryEnabled);
        wireToggle(discoverable, state.getSettings().isDiscoverable(), on -> {
            state.getSettings().setDiscoverable(on);
            state.fireChange();
        });

        VBox discovery = section("DISCOVERY",
                row("Mock discovery",
                        "Use simulated nearby devices instead of real network discovery.",
                        mockDiscovery),
                row("Discoverable",
                        "Let nearby devices see this device. (Demo only — no real broadcasting.)",
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
                state.getSettings().setDownloadDestination(chosen.getAbsolutePath());
                downloadPath.setText(shorten(chosen.getAbsolutePath()));
                state.fireChange();
            }
        });
        downloadPath.getStyleClass().add("device-meta");

        wireToggle(approval, state.getSettings().isRequireApprovalForIncoming(), on -> {
            state.getSettings().setRequireApprovalForIncoming(on);
            state.fireChange();
        });

        VBox transfers = section("TRANSFERS",
                row("Download destination",
                        "Where received files are saved.",
                        new HBox(10, downloadPath, browse)),
                row("Approve incoming transfers",
                        "Ask before accepting files sent to this device. (Demo only.)",
                        approval));

        // ---- Notifications ----
        wireToggle(notifications, state.getSettings().isDesktopNotifications(), on -> {
            state.getSettings().setDesktopNotifications(on);
            state.fireChange();
        });

        VBox notif = section("NOTIFICATIONS",
                row("Desktop notifications",
                        "Notify on completed, failed and incoming transfers. (Demo only.)",
                        notifications));

        VBox body = new VBox(18, general, discovery, transfers, notif);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(header, scroll);
    }

    private void commitName() {
        String v = deviceName.getText();
        if (v == null || v.isBlank()) {
            deviceName.setText(state.getSettings().getDeviceDisplayName());
            return;
        }
        state.getSettings().setDeviceDisplayName(v.trim());
        state.fireChange();
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

        DemoDevice self = state.getThisDevice();
        selfInfoLabel.setText(self.getName() + " · " + self.getOsLabel() + " · " + self.getIpAddress());
        selfInfoLabel.getStyleClass().setAll("device-meta");
        versionLabel.setText(DemoDataFactory.appVersion());
        versionLabel.getStyleClass().setAll("device-meta");

        if (!deviceName.isFocused()) {
            deviceName.setText(state.getSettings().getDeviceDisplayName());
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
    @Override public String subtitle() { return "In-memory preferences. Changes are not persisted between launches."; }

    public Pane root() { return root; }

    private static final class StyledLabel extends Label {
        StyledLabel(String text, String styleClass) {
            super(text);
            getStyleClass().add(styleClass);
        }
    }
}
