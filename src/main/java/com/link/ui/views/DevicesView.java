package com.link.ui.views;

import com.link.persistence.SavedDevice;
import com.link.ui.components.Icons;
import com.link.ui.demo.DemoDevice;
import com.link.ui.demo.DeviceStatus;
import com.link.ui.theme.Theme;
import com.link.ui.util.FormatUtil;
import com.link.ui.viewmodel.LinkViewModel;
import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The Devices Vault page — saved/bookmarked devices with persistent storage
 * plus a live "Discovered Nearby" section from the discovery service.
 *
 * <p>Layout (top → bottom):
 *   Header  ·  search field
 *   "Saved Devices"  section  (persistent, from SQLite)
 *   "Discovered Nearby" section  (live, from ViewModel)
 *
 * <p>All mutating actions (save, forget, rename, favourite) delegate to
 * {@link LinkViewModel}, which persists them through {@link com.link.persistence.DeviceRepository}.
 */
public class DevicesView implements View {

    private final LinkViewModel state;
    private final VBox root = new VBox();

    // Live sections
    private final VBox savedList    = new VBox(8);
    private final VBox nearbyList   = new VBox(8);
    private final TextField search  = new TextField();

    // Inline rename state
    private String renamingPeerId = null;

    public DevicesView(LinkViewModel state) {
        this.state = state;
        build();
        refresh();
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void build() {
        root.getStyleClass().add("content");
        root.setSpacing(0);

        // Header
        VBox titleBox = new VBox(4,
                styled("Devices", "page-title"),
                styled("Saved and nearby devices.", "page-subtitle"));
        HBox header = new HBox(titleBox, spacer());
        header.getStyleClass().add("content-header");
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        // Search
        search.setPromptText("Search saved devices…");
        search.getStyleClass().add("field");
        search.setMaxWidth(280);
        search.textProperty().addListener((o, ov, nv) -> refreshSaved());
        StackPane searchWrap = new StackPane(search);
        searchWrap.setAlignment(Pos.CENTER_LEFT);
        searchWrap.getStyleClass().add("vault-search-wrap");

        // Saved section
        Label savedTitle = sectionTitle("SAVED DEVICES");
        savedList.getStyleClass().add("vault-card-list");

        // Nearby section
        Label nearbyTitle = sectionTitle("DISCOVERED NEARBY");
        nearbyList.getStyleClass().add("vault-card-list");

        VBox body = new VBox(18,
                searchWrap,
                new VBox(10, savedTitle, savedList),
                new VBox(10, nearbyTitle, nearbyList));
        body.getStyleClass().add("vault-body");

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("vault-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(header, scroll);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Override
    public void refresh() {
        refreshSaved();
        refreshNearby();
    }

    private void refreshSaved() {
        savedList.getChildren().clear();
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();

        List<SavedDevice> all = state.getSavedDevices();
        List<SavedDevice> filtered = new ArrayList<>();
        for (SavedDevice d : all) {
            if (q.isEmpty() || d.getDisplayName().toLowerCase().contains(q)
                    || d.getIpAddress().toLowerCase().contains(q)) {
                filtered.add(d);
            }
        }

        if (filtered.isEmpty()) {
            savedList.getChildren().add(emptyState(
                    q.isEmpty() ? "No saved devices yet"
                                : "No devices match \"" + search.getText().trim() + "\"",
                    q.isEmpty() ? "Discover a device and click Save to add it here."
                                : "Try a different search term.",
                    Icons.emptyDevices(32, Theme.TEXT_3)));
            return;
        }

        // Determine which peers are currently online
        List<DemoDevice> online = state.getDevices();
        for (SavedDevice d : filtered) {
            boolean isOnline = online.stream().anyMatch(p -> p.getId().equals(d.getPeerId()));
            savedList.getChildren().add(savedCard(d, isOnline));
        }
    }

    private void refreshNearby() {
        nearbyList.getChildren().clear();
        List<DemoDevice> peers = state.getDevices();
        if (peers.isEmpty()) {
            String msg = switch (state.getDiscoveryState()) {
                case LOADING      -> "Scanning for devices…";
                case PAUSED       -> "Discovery is paused.";
                case UNAVAILABLE  -> "Discovery unavailable.";
                default           -> "No devices nearby right now.";
            };
            nearbyList.getChildren().add(emptyState(msg, null,
                    Icons.emptyDevices(32, Theme.TEXT_3)));
            return;
        }
        for (DemoDevice d : peers) {
            nearbyList.getChildren().add(nearbyCard(d));
        }
    }

    // ── Saved device card ─────────────────────────────────────────────────────

    private Node savedCard(SavedDevice d, boolean isOnline) {
        // If this card is being renamed, show the inline rename form instead
        if (d.getPeerId().equals(renamingPeerId)) {
            return renameCard(d, isOnline);
        }

        // Status dot
        Region dot = new Region();
        dot.getStyleClass().addAll("vault-status-dot",
                isOnline ? "vault-dot-online" : "vault-dot-offline");
        dot.setMinSize(8, 8);
        dot.setPrefSize(8, 8);

        // Icon
        StackPane iconWrap = deviceIconWrap(d.getDeviceType(), Theme.ACCENT);

        // Name row
        Label name = new Label(d.getDisplayName());
        name.getStyleClass().add("vault-device-name");

        // Meta row
        String lastSeen = d.getLastSeenMillis() > 0
                ? "Last seen " + FormatUtil.timeAgo(d.getLastSeenMillis(), System.currentTimeMillis())
                : "Never connected";
        String statusText = isOnline ? "Online" : lastSeen;
        Label meta = new Label((d.getIpAddress().isBlank() ? "" : d.getIpAddress() + " · ") + statusText);
        meta.getStyleClass().add("vault-device-meta");

        VBox textCol = new VBox(2, name, meta);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        // Favourite button
        Button fav = new Button();
        fav.setGraphic(d.isFavorite()
                ? Icons.starFilled(15, Theme.WARNING)
                : Icons.star(15, Theme.TEXT_3));
        fav.getStyleClass().add("vault-icon-btn");
        fav.setTooltip(tip(d.isFavorite() ? "Remove from favourites" : "Add to favourites"));
        fav.setOnAction(e -> {
            state.toggleFavorite(d.getPeerId());
            refreshSaved();
        });

        // Rename button
        Button rename = new Button();
        rename.setGraphic(Icons.edit(15, Theme.TEXT_3));
        rename.getStyleClass().add("vault-icon-btn");
        rename.setTooltip(tip("Rename"));
        rename.setOnAction(e -> {
            renamingPeerId = d.getPeerId();
            refreshSaved();
        });

        // Forget button
        Button forget = new Button();
        forget.setGraphic(Icons.trash(15, Theme.ERROR));
        forget.getStyleClass().addAll("vault-icon-btn", "vault-btn-danger");
        forget.setTooltip(tip("Remove from saved devices"));
        forget.setOnAction(e -> showForgetConfirm(d));

        HBox actions = new HBox(4, fav, rename, forget);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(12, iconWrap, new HBox(7, dot, textCol), spacer(), actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("vault-card");
        HBox.setHgrow(textCol, Priority.ALWAYS);
        return row;
    }

    /** Inline rename form for a saved card. */
    private Node renameCard(SavedDevice d, boolean isOnline) {
        TextField field = new TextField(d.getDisplayName());
        field.getStyleClass().add("field");
        field.selectAll();
        HBox.setHgrow(field, Priority.ALWAYS);

        Button confirm = new Button("Save");
        confirm.getStyleClass().addAll("btn", "btn-primary");
        confirm.setOnAction(e -> {
            String v = field.getText();
            if (v != null && !v.isBlank()) {
                state.renameDevice(d.getPeerId(), v.trim());
            }
            renamingPeerId = null;
            refreshSaved();
        });
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn", "btn-secondary");
        cancel.setOnAction(e -> { renamingPeerId = null; refreshSaved(); });

        field.setOnAction(e -> confirm.fire());

        HBox row = new HBox(10, deviceIconWrap(d.getDeviceType(), Theme.ACCENT),
                field, confirm, cancel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("vault-card", "vault-card-rename");
        return row;
    }

    // ── Forget confirmation ───────────────────────────────────────────────────

    private void showForgetConfirm(SavedDevice d) {
        // Replace the card with an inline confirmation row instead of a modal.
        savedList.getChildren().replaceAll(node -> {
            if (node instanceof HBox card) {
                // Find the card matching this peerId by tag
                if (d.getPeerId().equals(card.getUserData())) {
                    return confirmRow(d);
                }
            }
            return node;
        });
        // Simple approach: rebuild saved list with a confirm flag
        savedList.getChildren().clear();

        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        List<DemoDevice> online = state.getDevices();
        for (SavedDevice s : state.getSavedDevices()) {
            if (!q.isEmpty() && !s.getDisplayName().toLowerCase().contains(q)
                    && !s.getIpAddress().toLowerCase().contains(q)) continue;
            boolean isOnline = online.stream().anyMatch(p -> p.getId().equals(s.getPeerId()));
            if (s.getPeerId().equals(d.getPeerId())) {
                savedList.getChildren().add(confirmRow(d));
            } else {
                savedList.getChildren().add(savedCard(s, isOnline));
            }
        }
    }

    private Node confirmRow(SavedDevice d) {
        Label msg = new Label("Remove \"" + d.getDisplayName() + "\" from saved devices?");
        msg.getStyleClass().add("vault-confirm-text");
        HBox.setHgrow(msg, Priority.ALWAYS);

        Button yes = new Button("Remove");
        yes.getStyleClass().addAll("btn", "vault-btn-destructive");
        yes.setOnAction(e -> { state.forgetDevice(d.getPeerId()); refreshSaved(); });

        Button no = new Button("Cancel");
        no.getStyleClass().addAll("btn", "btn-secondary");
        no.setOnAction(e -> refreshSaved());

        HBox row = new HBox(10, msg, no, yes);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("vault-card", "vault-card-confirm");
        return row;
    }

    // ── Nearby card ───────────────────────────────────────────────────────────

    private Node nearbyCard(DemoDevice d) {
        boolean saved = state.isDeviceSaved(d.getId());

        StackPane iconWrap = deviceIconWrap(d.getType().name(),
                d.getStatus() == DeviceStatus.ONLINE ? Theme.ACCENT : Theme.TEXT_3);

        Label name = new Label(d.getName());
        name.getStyleClass().add("vault-device-name");
        String ipText = d.getIpAddress().isBlank() ? "Unknown IP" : d.getIpAddress();
        Label meta = new Label(ipText + " · Online");
        meta.getStyleClass().add("vault-device-meta");
        VBox textCol = new VBox(2, name, meta);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        Button saveBtn = new Button(saved ? "Saved ✓" : "Save");
        saveBtn.getStyleClass().addAll("btn", saved ? "btn-secondary" : "vault-btn-save");
        saveBtn.setDisable(saved);
        saveBtn.setOnAction(e -> {
            state.saveDevice(d);
            refreshSaved();
            refreshNearby();
        });

        HBox row = new HBox(12, iconWrap, textCol, saveBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("vault-card");
        return row;
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    private Node emptyState(String title, String desc, Node icon) {
        VBox v = new VBox(10, icon,
                styled(title, "empty-title"),
                desc != null ? styled(desc, "empty-desc") : new Region());
        v.getStyleClass().add("empty-state");
        v.setAlignment(Pos.CENTER);
        return v;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static StackPane deviceIconWrap(String type, javafx.scene.paint.Color color) {
        StackPane wrap = new StackPane(Icons.device(18, type, color));
        wrap.getStyleClass().add("vault-icon-wrap");
        wrap.setStyle("-fx-background-color: " + Theme.toRgba(color, 0.13) + ";");
        return wrap;
    }

    private static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("vault-section-title");
        return l;
    }

    private static Label styled(String text, String cls) {
        Label l = new Label(text);
        l.getStyleClass().add(cls);
        return l;
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static javafx.scene.control.Tooltip tip(String text) {
        return new javafx.scene.control.Tooltip(text);
    }

    // ── View lifecycle ────────────────────────────────────────────────────────

    @Override public void onShow()  { refresh(); }
    @Override public void onHide()  {}
    @Override public String title()    { return "Devices"; }
    @Override public String subtitle() { return "Saved and nearby devices."; }
    public Pane root() { return root; }
}
