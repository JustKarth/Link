package com.link.ui.views;

import com.link.ui.components.Icons;
import com.link.ui.components.TransferCard;
import com.link.ui.demo.DemoTransfer;
import com.link.ui.demo.TransferStatus;
import com.link.ui.viewmodel.LinkViewModel;
import com.link.ui.theme.Theme;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transfer-management screen. Two tabs — Active and Completed — back a
 * scrollable list of {@link TransferCard}s. Cards are built once and updated
 * in place by a lightweight timeline so progress animates smoothly. All
 * pause / resume / cancel / retry actions are demo-only and mutate the
 * in-memory state consistently.
 */
public class TransfersView implements View {

    private final LinkViewModel state;
    private final VBox root = new VBox();
    private final VBox list = new VBox(10);
    private final Button activeTab = new Button();
    private final Button completedTab = new Button();
    private final Map<String, TransferCard> cardsById = new HashMap<>();
    private boolean showActive = true;
    private Timeline timeline;

    public TransfersView(LinkViewModel state) {
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

        Label demoTag = new Label("Live");
        demoTag.getStyleClass().addAll("pill", "pill-completed");
        HBox header = new HBox(titleBox, spacer(), demoTag);
        header.getStyleClass().add("content-header");
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        // Tabs
        activeTab.getStyleClass().addAll("tab", "selected");
        activeTab.setOnAction(e -> { showActive = true; refresh(); });
        completedTab.getStyleClass().add("tab");
        completedTab.setOnAction(e -> { showActive = false; refresh(); });

        HBox tabBar = new HBox(4, activeTab, completedTab);
        tabBar.getStyleClass().add("tab-bar");

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(header, tabBar, scroll);
    }

    @Override public void refresh() {
        // Reconcile cards with the current transfer set.
        Map<String, DemoTransfer> current = new HashMap<>();
        for (DemoTransfer t : state.getTransfers()) current.put(t.getId(), t);

        // Add new cards.
        for (DemoTransfer t : state.getTransfers()) {
            if (!cardsById.containsKey(t.getId())) {
                cardsById.put(t.getId(), new TransferCard(t, state));
            }
        }
        // Drop gone cards.
        cardsById.keySet().removeIf(id -> !current.containsKey(id));

        updateAllCards();

        int active = 0, history = 0;
        for (DemoTransfer t : state.getTransfers()) {
            if (isActive(t)) active++; else history++;
        }
        activeTab.setText("Active");
        activeTab.setGraphic(tabCount(active));
        completedTab.setText("Completed");
        completedTab.setGraphic(tabCount(history));

        activeTab.getStyleClass().setAll("tab", showActive ? "selected" : "");
        completedTab.getStyleClass().setAll("tab", !showActive ? "selected" : "");

        // Populate the list for the current tab.
        list.getChildren().clear();
        boolean any = false;
        for (DemoTransfer t : state.getTransfers()) {
            if (showActive == isActive(t)) {
                TransferCard c = cardsById.get(t.getId());
                if (c != null) { list.getChildren().add(c); any = true; }
            }
        }
        if (!any) {
            list.getChildren().add(emptyState(showActive));
        }
    }

    private void updateAllCards() {
        for (DemoTransfer t : state.getTransfers()) {
            TransferCard c = cardsById.get(t.getId());
            if (c != null) c.update(t);
        }
    }

    private static boolean isActive(DemoTransfer t) {
        return t.getStatus() == TransferStatus.PENDING
                || t.getStatus() == TransferStatus.IN_PROGRESS
                || t.getStatus() == TransferStatus.PAUSED;
    }

    private Label tabCount(int n) {
        Label l = new Label(String.valueOf(n));
        l.getStyleClass().add("tab-count");
        return l;
    }

    private VBox emptyState(boolean active) {
        VBox v = new VBox(12,
                active ? Icons.emptyTransfers(34, Theme.TEXT_3) : Icons.check(34, Theme.TEXT_3),
                new StyledLabel(active ? "No active transfers" : "Nothing here yet",
                        "empty-title"),
                new StyledLabel(active
                        ? "Select a device on Nearby and send files to start a transfer."
                        : "Completed and past transfers will appear here.",
                        "empty-desc"));
        v.getStyleClass().add("empty-state");
        v.setAlignment(javafx.geometry.Pos.CENTER);
        return v;
    }

    @Override public void onShow() {
        if (timeline == null) {
            timeline = new Timeline(new KeyFrame(Duration.millis(120), e -> updateAllCards()));
            timeline.setCycleCount(Timeline.INDEFINITE);
        }
        timeline.play();
    }

    @Override public void onHide() {
        if (timeline != null) timeline.stop();
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    @Override public String title() { return "Transfers"; }
    @Override public String subtitle() { return "Track files moving between your devices."; }

    public Pane root() { return root; }

    private static final class StyledLabel extends Label {
        StyledLabel(String text, String styleClass) {
            super(text);
            getStyleClass().add(styleClass);
        }
    }
}
