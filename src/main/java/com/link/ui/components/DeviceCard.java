package com.link.ui.components;

import com.link.ui.demo.DemoDevice;
import com.link.ui.demo.DeviceStatus;
import com.link.ui.theme.Theme;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A compact, selectable card representing a discovered device. The visual
 * selected state is applied via the {@code selected} style class and the
 * check indicator on the right.
 */
public class DeviceCard extends VBox {

    private final DemoDevice device;
    private final StackPane iconWrap = new StackPane();
    private final StackPane checkBadge = new StackPane();
    private final Label selectLabel = new Label();

    public DeviceCard(DemoDevice device, Runnable onClick) {
        this.device = device;
        getStyleClass().addAll("card", "device-card");
        setOnMouseClicked(e -> { if (onClick != null) onClick.run(); });

        // Icon
        iconWrap.getStyleClass().add("device-card-icon-wrap");
        iconWrap.getChildren().add(Icons.device(24, device.getType().name(), Theme.TEXT_2));

        // Text column
        Label name = new Label(device.getName());
        name.getStyleClass().add("device-name");
        Label meta = new Label(metaLine(device));
        meta.getStyleClass().add("device-meta");
        Label ip = new Label(device.getIpAddress().isBlank() ? "—" : device.getIpAddress());
        ip.getStyleClass().add("device-ip");
        VBox text = new VBox(3, name, meta, ip);

        HBox top = new HBox(12, iconWrap, text);
        top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Status row
        Region dot = new Region();
        dot.getStyleClass().addAll("status-dot", statusClass(device.getStatus()));
        Label status = new Label(statusLabel(device.getStatus()));
        status.getStyleClass().add("status-label");
        HBox statusRow = new HBox(7, dot, status);
        statusRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Selection indicator (right aligned)
        checkBadge.setMouseTransparent(true);
        checkBadge.setMinSize(18, 18); checkBadge.setPrefSize(18, 18); checkBadge.setMaxSize(18, 18);
        selectLabel.getStyleClass().add("tiny");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox selectRow = new HBox(8, statusRow, spacer, selectLabel, checkBadge);
        selectRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        getChildren().addAll(top, selectRow);
        update(false);
    }

    private static String metaLine(DemoDevice d) {
        String type = switch (d.getType()) {
            case LAPTOP -> "Laptop";
            case DESKTOP -> "Desktop";
            case PHONE -> "Phone";
            case TABLET -> "Tablet";
        };
        if (d.getOsLabel().isBlank()) return type;
        return type + " · " + d.getOsLabel();
    }

    private static String statusClass(DeviceStatus s) {
        return switch (s) {
            case ONLINE -> "online";
            case AWAY -> "away";
            case OFFLINE -> "offline";
        };
    }

    private static String statusLabel(DeviceStatus s) {
        return switch (s) {
            case ONLINE -> "Online";
            case AWAY -> "Away";
            case OFFLINE -> "Unavailable";
        };
    }

    public DemoDevice getDevice() { return device; }

    /** Apply the selected visual state and update the indicator. */
    public void update(boolean selected) {
        Icons.recolor(iconWrap.getChildren().get(0), selected ? Theme.ACCENT : Theme.TEXT_2);
        if (selected) {
            if (!getStyleClass().contains("selected")) getStyleClass().add("selected");
            checkBadge.getChildren().clear();
            checkBadge.getChildren().add(Icons.check(16, Theme.ACCENT));
            selectLabel.setText("Selected");
            selectLabel.setTextFill(Theme.ACCENT);
        } else {
            getStyleClass().remove("selected");
            checkBadge.getChildren().clear();
            selectLabel.setText("Select");
            selectLabel.setTextFill(Theme.TEXT_3);
        }
    }
}
