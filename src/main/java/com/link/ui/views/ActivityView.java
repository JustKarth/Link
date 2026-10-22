package com.link.ui.views;

import com.link.ui.components.Icons;
import com.link.ui.demo.DemoActivity;
import com.link.ui.demo.TransferDirection;
import com.link.ui.prototype.PrototypeState;
import com.link.ui.theme.Theme;
import com.link.ui.util.FormatUtil;
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
 * Activity history. A search field and filter chips (All, Sent, Received,
 * Completed, Failed) operate against the in-memory activity list — both work
 * for real, with proper empty and no-results states.
 */
public class ActivityView implements View {

    private enum Filter { ALL, SENT, RECEIVED, COMPLETED, FAILED }

    private final PrototypeState state;
    private final VBox root = new VBox();
    private final VBox list = new VBox(8);
    private final TextField search = new TextField();
    private Filter filter = Filter.ALL;
    private final List<Button> chipButtons = new ArrayList<>();

    public ActivityView(PrototypeState state) {
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
        Region sp = spacer();
        HBox header = new HBox(titleBox, sp);
        header.getStyleClass().add("content-header");
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        // Search
        search.setPromptText("Search files or devices…");
        search.getStyleClass().add("field");
        HBox searchBox = new HBox(8, Icons.search(16, Theme.TEXT_3), search);
        searchBox.getStyleClass().add("search-box");
        searchBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);
        search.textProperty().addListener((o, ov, nv) -> applyFilter());

        // Filter chips
        HBox chips = new HBox(8);
        chips.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        for (Filter f : Filter.values()) {
            Button c = new Button(labelFor(f));
            c.getStyleClass().add("chip");
            if (f == Filter.ALL) c.getStyleClass().add("selected");
            c.setOnAction(e -> selectChip(f));
            chipButtons.add(c);
            chips.getChildren().add(c);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(header, searchBox, chips, scroll);
    }

    private void selectChip(Filter f) {
        this.filter = f;
        for (Button c : chipButtons) {
            c.getStyleClass().remove("selected");
        }
        chipButtons.get(f.ordinal()).getStyleClass().add("selected");
        applyFilter();
    }

    private static String labelFor(Filter f) {
        return switch (f) {
            case ALL -> "All";
            case SENT -> "Sent";
            case RECEIVED -> "Received";
            case COMPLETED -> "Completed";
            case FAILED -> "Failed";
        };
    }

    @Override public void refresh() {
        applyFilter();
    }

    private void applyFilter() {
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        List<DemoActivity> all = state.getActivities();
        List<DemoActivity> filtered = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (DemoActivity a : all) {
            if (!matchesFilter(a)) continue;
            if (!q.isEmpty()
                    && !a.fileName().toLowerCase().contains(q)
                    && !a.deviceName().toLowerCase().contains(q)) continue;
            filtered.add(a);
        }

        list.getChildren().clear();
        if (filtered.isEmpty()) {
            list.getChildren().add(emptyState(all.isEmpty(), q.isEmpty()));
            return;
        }

        // Group by "Today" / "Yesterday" / "Older" with small section headers
        String lastGroup = "";
        for (DemoActivity a : filtered) {
            String group = groupLabel(a.timestampMillis(), now);
            if (!group.equals(lastGroup)) {
                Label header = new Label(group);
                header.getStyleClass().add("activity-group-header");
                list.getChildren().add(header);
                lastGroup = group;
            }
            list.getChildren().add(row(a, now));
        }
    }

    private static String groupLabel(long millis, long now) {
        long diff = now - millis;
        long hours = diff / 3_600_000L;
        if (hours < 24) return "TODAY";
        if (hours < 48) return "YESTERDAY";
        return "EARLIER";
    }

    private boolean matchesFilter(DemoActivity a) {
        return switch (filter) {
            case ALL -> true;
            case SENT -> a.direction() == TransferDirection.OUTGOING;
            case RECEIVED -> a.direction() == TransferDirection.INCOMING;
            case COMPLETED -> a.completed();
            case FAILED -> !a.completed();
        };
    }

    private HBox row(DemoActivity a, long now) {
        FormatUtil.FileCategory cat = FormatUtil.categorize(a.fileName());
        StackPane iconWrap = new StackPane(Icons.fileIcon(16, cat));
        iconWrap.setMinSize(32, 32);
        iconWrap.setPrefSize(32, 32);
        iconWrap.setStyle("-fx-background-color: " + Theme.toRgba(Theme.softFor(cat), 0.16)
                + "; -fx-background-radius: 8;");

        // Direction badge
        boolean out = a.direction() == TransferDirection.OUTGOING;
        Label dirBadge = new Label(out ? "↑" : "↓");
        dirBadge.setStyle("-fx-font-size: 11; -fx-font-weight: 700; -fx-text-fill: "
                + (out ? Theme.toRgba(Theme.ACCENT) : Theme.toRgba(Theme.ACCENT_2)) + ";"
                + "-fx-background-color: "
                + (out ? Theme.toRgba(Theme.ACCENT, 0.1) : Theme.toRgba(Theme.ACCENT_2, 0.1)) + ";"
                + "-fx-background-radius: 5; -fx-padding: 1 6 1 6;");

        javafx.scene.Node statusIcon = a.completed()
                ? Icons.check(15, Theme.SUCCESS)
                : Icons.warning(15, Theme.ERROR);

        Label file = new Label(a.fileName());
        file.getStyleClass().add("activity-file");
        String dir = a.direction() == TransferDirection.OUTGOING ? "to " : "from ";
        Label meta = new Label(FormatUtil.formatBytes(a.fileSize()) + " · " + dir + a.deviceName());
        meta.getStyleClass().add("activity-meta");
        VBox text = new VBox(2, file, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label time = new Label(FormatUtil.timeAgo(a.timestampMillis(), now));
        time.getStyleClass().add("activity-time");

        HBox row = new HBox(10, statusIcon, iconWrap, text, dirBadge, time);
        row.getStyleClass().add("activity-item");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    private VBox emptyState(boolean noData, boolean noQuery) {
        javafx.scene.Node icon = noData ? Icons.emptyActivity(34, Theme.TEXT_3) : Icons.noResults(34, Theme.TEXT_3);
        String title = noData ? "No activity yet"
                : (noQuery ? "No matching filter" : "No results");
        String desc = noData ? "Files you send and receive will show up here."
                : (noQuery ? "Try a different filter." : "Try a different search term or filter.");
        VBox v = new VBox(12, icon, new StyledLabel(title, "empty-title"),
                new StyledLabel(desc, "empty-desc"));
        v.getStyleClass().add("empty-state");
        v.setAlignment(javafx.geometry.Pos.CENTER);
        return v;
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    @Override public String title() { return "Activity"; }
    @Override public String subtitle() { return "A timeline of everything sent and received across your devices."; }

    public Pane root() { return root; }

    private static final class StyledLabel extends Label {
        StyledLabel(String text, String styleClass) {
            super(text);
            getStyleClass().add(styleClass);
        }
    }
}
