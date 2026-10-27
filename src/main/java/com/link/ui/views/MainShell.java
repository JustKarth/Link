package com.link.ui.views;

import com.link.ui.components.Icons;
import com.link.ui.components.SasPairingDialog;
import com.link.ui.demo.DemoTransfer;
import com.link.ui.demo.DiscoveryState;
import com.link.ui.demo.TransferStatus;
import com.link.ui.viewmodel.LinkViewModel;
import com.link.ui.theme.Theme;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The main application shell: a refined sidebar with the brand, navigation
 * and device-identity footer, plus a content host that swaps between the
 * four views. Also implements {@link Navigator} so views can navigate and
 * access the overlay layer for modals.
 */
public class MainShell implements Navigator {

    private final LinkViewModel state;
    private final StackPane root = new StackPane();
    private final BorderPane shell = new BorderPane();
    private final StackPane contentHost = new StackPane();

    private final Map<ViewKey, View> views = new EnumMap<>(ViewKey.class);
    private final Map<ViewKey, NavItem> navItems = new EnumMap<>(ViewKey.class);
    private ViewKey active = ViewKey.NEARBY;

    private final Label footerName = new Label();
    private final Label footerStatus = new Label();
    private final Region footerDot = new Region();

    /** Tracks transfer IDs that currently have an open SAS pairing dialog. */
    private final Map<String, SasPairingDialog> openPairingDialogs = new HashMap<>();

    public MainShell(LinkViewModel state) {
        this.state = state;
        views.put(ViewKey.NEARBY, new NearbyView(state, this));
        views.put(ViewKey.TRANSFERS, new TransfersView(state));
        views.put(ViewKey.ACTIVITY, new ActivityView(state));
        views.put(ViewKey.DEVICES, new DevicesView(state));
        views.put(ViewKey.SETTINGS, new SettingsView(state));

        shell.setLeft(buildSidebar());
        shell.setCenter(contentHost);
        root.getChildren().add(shell);
        root.getStyleClass().add("app-root");

        // Global refresh: update the active view and sidebar status.
        state.addListener(this::onStateChanged);

        // SAS pairing prompt — fired on the FX thread by LinkViewModel.
        state.addPairingListener((transferId, peerName, sasCode) -> {
            if (openPairingDialogs.containsKey(transferId)) return; // already open
            SasPairingDialog dlg = new SasPairingDialog(
                    root, state, transferId, peerName, sasCode,
                    () -> openPairingDialogs.remove(transferId));
            openPairingDialogs.put(transferId, dlg);
            dlg.show();
        });

        goTo(ViewKey.NEARBY);
    }

    private void onStateChanged() {
        views.get(active).refresh();
        updateSidebarStatus();
        // Dismiss any open pairing dialogs whose transfer is now terminal.
        if (!openPairingDialogs.isEmpty()) {
            for (DemoTransfer dt : state.getTransfers()) {
                SasPairingDialog dlg = openPairingDialogs.get(dt.getId());
                if (dlg != null && dt.isTerminal()) {
                    openPairingDialogs.remove(dt.getId());
                    dlg.dismiss();
                }
            }
        }
    }

    @Override public void goTo(ViewKey key) {
        if (key == active && !contentHost.getChildren().isEmpty()) {
            views.get(key).refresh();
            updateSidebarStatus();
            return;
        }
        views.get(active).onHide();
        active = key;
        View view = views.get(key);
        contentHost.getChildren().setAll(viewRoot(view));
        view.onShow();
        view.refresh();
        for (Map.Entry<ViewKey, NavItem> e : navItems.entrySet()) {
            e.getValue().setActive(e.getKey() == key);
        }
        updateSidebarStatus();
    }

    private Node viewRoot(View view) {
        if (view instanceof NearbyView n) return n.root();
        if (view instanceof TransfersView t) return t.root();
        if (view instanceof ActivityView a) return a.root();
        if (view instanceof DevicesView d) return d.root();
        if (view instanceof SettingsView s) return s.root();
        return new Label(view.title());
    }

    @Override public StackPane overlayRoot() { return root; }

    public StackPane root() { return root; }

    // ---- Sidebar ----------------------------------------------------------

    private Node buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(256);
        sidebar.setMinWidth(256);

        // Brand
        HBox brand = new HBox(11, Icons.brand(26),
                new VBox(1,
                        new StyledLabel("Link", "brand-wordmark"),
                        new StyledLabel("Connect · Share · Together", "brand-tagline")));
        brand.getStyleClass().add("brand");
        brand.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Nav
        VBox nav = new VBox(3);
        nav.getStyleClass().add("nav");
        nav.getChildren().add(new StyledLabel("NAVIGATION", "nav-section-label"));
        navItems.put(ViewKey.NEARBY, navItem(ViewKey.NEARBY, Icons.nearby(19, Theme.TEXT_2)));
        navItems.put(ViewKey.TRANSFERS, navItem(ViewKey.TRANSFERS, Icons.transfers(19, Theme.TEXT_2)));
        navItems.put(ViewKey.ACTIVITY, navItem(ViewKey.ACTIVITY, Icons.activity(19, Theme.TEXT_2)));
        navItems.put(ViewKey.DEVICES, navItem(ViewKey.DEVICES, Icons.devices(19, Theme.TEXT_2)));
        navItems.put(ViewKey.SETTINGS, navItem(ViewKey.SETTINGS, Icons.settings(19, Theme.TEXT_2)));
        nav.getChildren().addAll(navItems.values());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Device identity footer
        footerDot.getStyleClass().add("discovery-dot");
        footerDot.setMinSize(8, 8);
        footerName.getStyleClass().add("device-id-name");
        footerStatus.getStyleClass().add("device-id-status");
        HBox footerStatusRow = new HBox(7, footerDot, footerStatus);
        footerStatusRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox footer = new VBox(8,
                new HBox(10, Icons.device(18, "LAPTOP", Theme.TEXT_2), footerName),
                footerStatusRow);
        footer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        footer.getStyleClass().add("device-id");

        sidebar.getChildren().addAll(brand, nav, spacer, footer);
        return sidebar;
    }

    private NavItem navItem(ViewKey key, Node icon) {
        NavItem item = new NavItem(key, icon);
        item.setOnClick(() -> goTo(key));
        return item;
    }

    private void updateSidebarStatus() {
        // Device name prefers the user's setting over the demo default.
        footerName.setText(state.getSettings().getDeviceDisplayName());

        DiscoveryState ds = state.getDiscoveryState();
        footerDot.getStyleClass().removeAll("offline", "paused");
        String status;
        if (ds == DiscoveryState.PAUSED) {
            status = "Discovery paused";
            footerDot.getStyleClass().add("paused");
        } else if (ds == DiscoveryState.UNAVAILABLE || ds == DiscoveryState.NONE_FOUND) {
            status = ds == DiscoveryState.NONE_FOUND ? "No devices nearby" : "Discovery unavailable";
            footerDot.getStyleClass().add("offline");
        } else if (state.getSettings().isDiscoverable()) {
            status = "Discoverable · " + state.onlineDeviceCount() + " nearby";
        } else {
            status = "Hidden · " + state.onlineDeviceCount() + " nearby";
        }
        footerStatus.setText(status);

        // Nav badges — transfers shows active count, activity shows 0 once visited.
        navItems.get(ViewKey.TRANSFERS).setBadge(state.activeTransferCount());
        // Don't show a persistent huge badge for activity; only show if > 0 completed today
        long now = System.currentTimeMillis();
        long recentCount = state.getActivities().stream()
                .filter(a -> now - a.timestampMillis() < 3_600_000L * 24)
                .count();
        navItems.get(ViewKey.ACTIVITY).setBadge((int) Math.min(recentCount, 99));
    }

    // ---- A single nav item ------------------------------------------------

    private static final class NavItem extends HBox {
        private final Region indicator = new Region();
        private final Label badge = new Label();
        private Runnable onClick;

        NavItem(ViewKey key, Node icon) {
            getStyleClass().add("nav-item");
            indicator.getStyleClass().add("nav-indicator");
            Label label = new Label(key.label());
            label.getStyleClass().add("nav-label");
            badge.getStyleClass().add("nav-badge");
            badge.setManaged(false);
            badge.setVisible(false);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            getChildren().addAll(indicator, icon, label, spacer, badge);
            setOnMouseClicked(e -> { if (onClick != null) onClick.run(); });
        }

        void setOnClick(Runnable r) { this.onClick = r; }

        void setActive(boolean active) {
            if (active) {
                if (!getStyleClass().contains("active")) getStyleClass().add("active");
            } else {
                getStyleClass().remove("active");
            }
            // Recolour the icon: accent when active, muted otherwise.
            // The icon lives at index 1 of the children.
            Node icon = getChildren().get(1);
            Icons.recolor(icon, active ? Theme.ACCENT : Theme.TEXT_2);
        }

        void setBadge(int count) {
            if (count > 0) {
                badge.setText(String.valueOf(count));
                badge.setManaged(true);
                badge.setVisible(true);
            } else {
                badge.setManaged(false);
                badge.setVisible(false);
            }
        }
    }

    private static final class StyledLabel extends Label {
        StyledLabel(String text, String styleClass) {
            super(text);
            getStyleClass().add(styleClass);
        }
    }
}
