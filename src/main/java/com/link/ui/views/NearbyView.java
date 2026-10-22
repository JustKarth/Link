package com.link.ui.views;

import com.link.ui.components.ConnectByIpDialog;
import com.link.ui.components.FileSelectionDialog;
import com.link.ui.components.Icons;
import com.link.ui.components.NetworkVisualization;
import com.link.ui.demo.DemoDevice;
import com.link.ui.demo.DeviceStatus;
import com.link.ui.demo.DeviceType;
import com.link.ui.demo.DiscoveryState;
import com.link.ui.demo.TransferDirection;
import com.link.ui.demo.TransferStatus;
import com.link.ui.prototype.PrototypeState;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Nearby — the Orbit Radar screen.
 *
 * Layout (top → bottom):
 *   Header  ·  RADAR PROXIMITY MATRIX / Orbit Discovery  ·  peer count badge  ·  controls
 *   NetworkVisualization  (full height, VGrow.ALWAYS)
 *     – three concentric glow rings
 *     – always-on rotating sweep
 *     – glassmorphic satellite device cards with bobbing drift
 *     – centre: icon / YOUR DEVICE / name / IP  (no overlap)
 *   Destination panel  (fades in when a device is selected)
 *   Receive status bar  (shows when discoverable / awaiting files)
 *   Status bar
 *
 * The floating circular PulseButton has been removed.
 * Devices are rendered only as spatial satellite cards — no bottom chip row.
 */
public class NearbyView implements View {

    private final PrototypeState       state;
    private final Navigator            nav;
    private final VBox                 root = new VBox();
    private final NetworkVisualization viz  = new NetworkVisualization();

    // ── Destination (send) panel ──────────────────────────────────────────────
    private final HBox      destPanel   = new HBox(14);
    private final Label     destName    = new Label();
    private final Label     destMeta    = new Label();
    private final StackPane destIconBox = new StackPane();
    private boolean destVisible = false;

    // ── Receive status strip ──────────────────────────────────────────────────
    private final HBox  receiveStrip = new HBox(10);

    // ── Header controls ───────────────────────────────────────────────────────
    private final Label  peersLabel  = new Label();
    private final Region statusDot   = new Region();
    private final Button discoverBtn = new Button();

    // ── Status bar ────────────────────────────────────────────────────────────
    private final Label statusBar = new Label();

    // ── Constructor ───────────────────────────────────────────────────────────

    public NearbyView(PrototypeState state, Navigator nav) {
        this.state = state;
        this.nav   = nav;
        build();
        viz.setOnSelect(id -> state.selectDevice(id));
        refresh();
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void build() {
        root.setFillWidth(true);
        root.setSpacing(0);
        root.setStyle("-fx-background-color: #0b0f19; -fx-padding: 20 24 12 24;");

        // ── Main dark-glass panel ─────────────────────────────────────────────
        VBox panel = new VBox(0);
        panel.setStyle("-fx-background-color: rgba(15,23,42,0.50);"  // reduced opacity so canvas shines
                + "-fx-background-radius: 16;"
                + "-fx-border-color: rgba(255,255,255,0.08);"
                + "-fx-border-width: 1; -fx-border-radius: 16;"
                + "-fx-padding: 20 24 16 24;");
        VBox.setVgrow(panel, Priority.ALWAYS);

        // ── Header ────────────────────────────────────────────────────────────
        Label eyebrow = new Label("RADAR PROXIMITY MATRIX");
        eyebrow.setStyle("-fx-font-size: 9.5; -fx-font-weight: 700;"
                + "-fx-text-fill: #00f2fe; -fx-letter-spacing: 2.0; -fx-opacity: 0.75;");
        Label title = new Label("Orbit Discovery");
        title.setStyle("-fx-font-size: 22; -fx-font-weight: 700; -fx-text-fill: #ffffff;");
        VBox titleBlock = new VBox(3, eyebrow, title);

        // Peers badge
        statusDot.setMinSize(8, 8); statusDot.setPrefSize(8, 8);
        peersLabel.setStyle("-fx-font-size: 11.5; -fx-font-weight: 600; -fx-text-fill: #ffffff;");
        HBox peersBadge = new HBox(7, statusDot, peersLabel);
        peersBadge.setAlignment(Pos.CENTER_LEFT);
        peersBadge.setStyle("-fx-background-color: rgba(0,242,254,0.10);"
                + "-fx-background-radius: 20; -fx-border-color: rgba(0,242,254,0.30);"
                + "-fx-border-width: 1; -fx-border-radius: 20; -fx-padding: 5 14 5 12;");

        Button historyBtn = new Button("Vault History");
        historyBtn.setStyle(pillBtnStyle(false));
        historyBtn.setOnAction(e -> nav.goTo(ViewKey.ACTIVITY));

        discoverBtn.setStyle(pillBtnStyle(false));
        discoverBtn.setOnAction(e -> handleDiscoveryControl());

        Button ipBtn = new Button("IP Connect");
        ipBtn.setStyle(pillBtnStyle(true));
        ipBtn.setOnAction(e -> new ConnectByIpDialog(nav.overlayRoot()).show());

        HBox headerRight = new HBox(10, peersBadge, historyBtn, discoverBtn, ipBtn);
        headerRight.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(titleBlock, spacer(), headerRight);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-padding: 0 0 16 0;"
                + "-fx-border-color: transparent transparent rgba(255,255,255,0.06) transparent;"
                + "-fx-border-width: 0 0 1 0;");

        // ── Visualization — canvas fills with its own dark background ────────
        // vizBox background must be transparent so the canvas controls the bg.
        StackPane vizBox = new StackPane(viz);
        vizBox.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(vizBox, Priority.ALWAYS);
        vizBox.setMinHeight(380);

        // ── Destination (send) panel ──────────────────────────────────────────
        buildDestPanel();
        destPanel.setManaged(false);
        destPanel.setVisible(false);

        // ── Receive status strip ──────────────────────────────────────────────
        buildReceiveStrip();

        panel.getChildren().addAll(header, vizBox, destPanel, receiveStrip);

        // ── Status bar ────────────────────────────────────────────────────────
        Label leftStatus = new Label("SOCKET PORT: 8080   HAPTIC PROXIMITY: ENABLED");
        leftStatus.setStyle("-fx-font-size: 9.5; -fx-font-weight: 600;"
                + "-fx-text-fill: #94a3b8; -fx-letter-spacing: 0.8;");
        statusBar.setStyle("-fx-font-size: 9.5; -fx-font-weight: 700;"
                + "-fx-text-fill: #00f2fe; -fx-letter-spacing: 1.0;");
        HBox statusBarRow = new HBox(leftStatus, spacer(), statusBar);
        statusBarRow.setAlignment(Pos.CENTER_LEFT);
        statusBarRow.setStyle("-fx-padding: 10 0 0 0;");

        root.getChildren().addAll(panel, statusBarRow);
    }

    // ── Destination panel ─────────────────────────────────────────────────────

    private void buildDestPanel() {
        destPanel.setAlignment(Pos.CENTER_LEFT);
        destPanel.setStyle("-fx-background-color: rgba(0,242,254,0.09);"
                + "-fx-background-radius: 12;"
                + "-fx-border-color: rgba(0,242,254,0.40) rgba(255,255,255,0.08)"
                        + " rgba(255,255,255,0.08) rgba(0,242,254,0.40);"
                + "-fx-border-width: 1 1 1 3; -fx-border-radius: 12;"
                + "-fx-padding: 10 14 10 14;");

        destIconBox.setMinSize(38, 38); destIconBox.setPrefSize(38, 38);
        destIconBox.setStyle("-fx-background-color: rgba(0,242,254,0.15);"
                + "-fx-background-radius: 10;");

        Label sendTag = new Label("SENDING TO");
        sendTag.setStyle("-fx-font-size: 8.5; -fx-font-weight: 700; -fx-text-fill: #00f2fe;"
                + "-fx-letter-spacing: 1.0;");
        destName.setStyle("-fx-font-size: 13.5; -fx-font-weight: 600; -fx-text-fill: #ffffff;");
        destMeta.setStyle("-fx-font-size: 10.5; -fx-text-fill: #94a3b8;");

        VBox textCol = new VBox(1, sendTag, destName, destMeta);
        textCol.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        Label demoBadge = new Label("DEMO");
        demoBadge.setStyle("-fx-font-size: 9; -fx-font-weight: 700; -fx-text-fill: #4facfe;"
                + "-fx-background-color: rgba(79,172,254,0.14);"
                + "-fx-background-radius: 5; -fx-padding: 2 7 2 7;");

        Button chooseBtn = new Button("Choose Files");
        chooseBtn.setStyle("-fx-background-color: #00f2fe; -fx-background-radius: 9;"
                + "-fx-text-fill: #04141A; -fx-font-weight: 700; -fx-font-size: 12;"
                + "-fx-cursor: hand; -fx-padding: 8 16;");
        chooseBtn.setOnAction(e -> openSend());

        Button clearBtn = new Button("✕");
        clearBtn.setStyle("-fx-background-color: rgba(255,255,255,0.08);"
                + "-fx-text-fill: #94a3b8; -fx-background-radius: 8;"
                + "-fx-border-width: 0; -fx-cursor: hand; -fx-font-size: 14; -fx-padding: 3 9;");
        clearBtn.setOnAction(e -> state.clearSelection());
        clearBtn.setTooltip(new javafx.scene.control.Tooltip("Clear selection (Esc)"));

        destPanel.getChildren().addAll(destIconBox, textCol, demoBadge, chooseBtn, clearBtn);
        destPanel.setPrefHeight(64);
    }

    // ── Receive status strip ──────────────────────────────────────────────────

    /**
     * Always-visible strip at the bottom of the panel showing the local device's
     * receive status — whether it is discoverable and accepting incoming files.
     * This makes it immediately clear to the user that the app can RECEIVE
     * files, not just send them.
     */
    private void buildReceiveStrip() {
        receiveStrip.setAlignment(Pos.CENTER_LEFT);
        receiveStrip.setStyle("-fx-padding: 10 0 0 0;");

        // Pulsing green dot
        Region dot = new Region();
        dot.setMinSize(8, 8); dot.setPrefSize(8, 8);
        dot.setStyle("-fx-background-color: #41c9a2; -fx-background-radius: 4;"
                + "-fx-effect: dropshadow(gaussian, rgba(65,201,162,0.65), 6, 0.5, 0, 0);");

        Label recvLabel = new Label("RECEIVING  —  This device is discoverable and accepting files");
        recvLabel.setStyle("-fx-font-size: 10.5; -fx-text-fill: rgba(65,201,162,0.85);"
                + "-fx-font-weight: 600; -fx-letter-spacing: 0.4;");

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        // Small pill showing the local IP for easy sharing
        Label ipPill = new Label();
        ipPill.setStyle("-fx-font-size: 10; -fx-font-weight: 700; -fx-text-fill: #00f2fe;"
                + "-fx-background-color: rgba(0,242,254,0.10);"
                + "-fx-background-radius: 6; -fx-border-color: rgba(0,242,254,0.30);"
                + "-fx-border-width: 1; -fx-border-radius: 6; -fx-padding: 2 9 2 9;");
        ipPill.setUserData("recv_ip");

        receiveStrip.getChildren().addAll(dot, recvLabel, gap, ipPill);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Override public void refresh() {
        String           selId = state.getSelectedDeviceId();
        DiscoveryState   ds    = state.getDiscoveryState();
        List<DemoDevice> devs  = state.getDevices();
        DemoDevice       self  = state.getThisDevice();

        viz.update(devs, self.getName(), self.getIpAddress(), selId, ds);

        updateHeaderStatus(ds, devs);
        updateDestPanel(selId, devs);
        updateReceiveStrip(self);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void updateHeaderStatus(DiscoveryState ds, List<DemoDevice> devs) {
        int online = (int) devs.stream()
                .filter(d -> d.getStatus() == DeviceStatus.ONLINE).count();

        statusDot.setStyle("-fx-background-radius: 4; -fx-background-color: "
                + switch (ds) {
                    case LOADING, PAUSED, UNAVAILABLE -> "#f5b664";
                    case NONE_FOUND -> "#5e6b7a";
                    default -> "#00f2fe";
                } + ";"
                + "-fx-effect: dropshadow(gaussian, rgba(0,242,254,0.65), 6, 0.5, 0, 0);");

        peersLabel.setText(switch (ds) {
            case LOADING    -> "Scanning…";
            case FOUND      -> online + " Peer" + (online == 1 ? "" : "s") + " in Range";
            case NONE_FOUND -> "No Peers Found";
            case PAUSED     -> "Discovery Paused";
            case UNAVAILABLE -> "Unavailable";
        });

        discoverBtn.setText(switch (ds) {
            case PAUSED               -> "Resume";
            case NONE_FOUND, UNAVAILABLE -> "Rescan";
            default                   -> "Pause";
        });

        statusBar.setText(switch (ds) {
            case FOUND       -> "ORBIT PROXIMITY SCANNER ACTIVE";
            case LOADING     -> "SCANNING FOR PEERS…";
            case PAUSED      -> "SCANNER PAUSED";
            case NONE_FOUND  -> "NO PEERS IN RANGE";
            case UNAVAILABLE -> "SCANNER UNAVAILABLE";
        });
    }

    // ── Receive strip ─────────────────────────────────────────────────────────

    private void updateReceiveStrip(com.link.ui.demo.DemoDevice self) {
        boolean discoverable = state.getSettings().isDiscoverable();

        // Update colour/label based on discoverability setting
        if (!receiveStrip.getChildren().isEmpty()) {
            Region dot = (Region) receiveStrip.getChildren().get(0);
            Label  lbl = (Label)  receiveStrip.getChildren().get(1);
            if (discoverable) {
                dot.setStyle("-fx-background-color: #41c9a2; -fx-background-radius: 4;"
                        + "-fx-effect: dropshadow(gaussian, rgba(65,201,162,0.65), 6, 0.5, 0, 0);");
                lbl.setStyle("-fx-font-size: 10.5; -fx-text-fill: rgba(65,201,162,0.85);"
                        + "-fx-font-weight: 600; -fx-letter-spacing: 0.4;");
                lbl.setText("RECEIVING  —  This device is discoverable and accepting files");
            } else {
                dot.setStyle("-fx-background-color: #5e6b7a; -fx-background-radius: 4;");
                lbl.setStyle("-fx-font-size: 10.5; -fx-text-fill: #5e6b7a;"
                        + "-fx-font-weight: 600; -fx-letter-spacing: 0.4;");
                lbl.setText("RECEIVE OFF  —  This device is hidden (change in Settings)");
            }
        }

        // Update IP pill
        for (var node : receiveStrip.getChildren()) {
            if (node instanceof Label l && "recv_ip".equals(l.getUserData())) {
                l.setText(self.getIpAddress());
                break;
            }
        }
    }

    // ── Destination panel visibility ──────────────────────────────────────────

    private void updateDestPanel(String selId, List<DemoDevice> devs) {
        DemoDevice sel = devs.stream()
                .filter(d -> d.getId().equals(selId)).findFirst().orElse(null);
        boolean shouldShow = sel != null;

        if (shouldShow) {
            destIconBox.getChildren().setAll(Icons.device(22, sel.getType().name(),
                    Color.web("#00f2fe")));
            destName.setText(sel.getName());
            destMeta.setText(typeName(sel.getType())
                    + " · " + (sel.getStatus() == DeviceStatus.ONLINE ? "Online" : "Away")
                    + " · " + sel.getIpAddress()
                    + " · Demo transfer");
        }

        if (shouldShow && !destVisible) {
            destPanel.setManaged(true);
            destPanel.setVisible(true);
            destPanel.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(180), destPanel);
            ft.setToValue(1.0);
            ft.play();
            destVisible = true;
        } else if (!shouldShow && destVisible) {
            FadeTransition ft = new FadeTransition(Duration.millis(140), destPanel);
            ft.setToValue(0.0);
            ft.setOnFinished(ev -> {
                destPanel.setManaged(false);
                destPanel.setVisible(false);
            });
            ft.play();
            destVisible = false;
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void openSend() {
        DemoDevice sel = state.getSelectedDevice();
        if (sel == null) return;
        new FileSelectionDialog(nav.overlayRoot(), state, sel, () -> {
            List<String> names = collectRecentFileNames();
            viz.triggerBeam(names);
            PauseTransition delay = new PauseTransition(Duration.millis(1100));
            delay.setOnFinished(ev -> nav.goTo(ViewKey.TRANSFERS));
            delay.play();
        }).show();
    }

    private List<String> collectRecentFileNames() {
        List<String> names = new ArrayList<>();
        for (var t : state.getTransfers()) {
            if (t.getStatus() == TransferStatus.IN_PROGRESS
                    || t.getStatus() == TransferStatus.PENDING) {
                names.add(t.getFileName());
                if (names.size() >= 5) break;
            }
        }
        return names;
    }

    private void handleDiscoveryControl() {
        DiscoveryState s = state.getDiscoveryState();
        if (s == DiscoveryState.PAUSED)                               state.setDiscoveryEnabled(true);
        else if (s == DiscoveryState.NONE_FOUND
              || s == DiscoveryState.UNAVAILABLE)                     state.rescan();
        else                                                          state.toggleDiscovery();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override public void onShow() { viz.start(); }
    @Override public void onHide() { viz.stop();  }

    @Override public String title()    { return "Nearby"; }
    @Override public String subtitle() { return "Orbit Discovery — peer-to-peer spatial radar."; }

    public Pane root() { return root; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String pillBtnStyle(boolean accent) {
        if (accent) {
            return "-fx-background-color: rgba(0,242,254,0.12);"
                    + "-fx-background-radius: 20; -fx-border-color: rgba(0,242,254,0.40);"
                    + "-fx-border-width: 1; -fx-border-radius: 20;"
                    + "-fx-text-fill: #00f2fe; -fx-font-size: 11; -fx-cursor: hand;"
                    + "-fx-padding: 5 14 5 14;";
        }
        return "-fx-background-color: rgba(255,255,255,0.07);"
                + "-fx-background-radius: 20; -fx-border-color: rgba(255,255,255,0.12);"
                + "-fx-border-width: 1; -fx-border-radius: 20;"
                + "-fx-text-fill: #94a3b8; -fx-font-size: 11; -fx-cursor: hand;"
                + "-fx-padding: 5 14 5 14;";
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static String typeName(DeviceType t) {
        return switch (t) {
            case LAPTOP  -> "Laptop";
            case DESKTOP -> "Desktop";
            case PHONE   -> "Phone";
            case TABLET  -> "Tablet";
        };
    }
}
