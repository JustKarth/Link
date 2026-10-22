package com.link.ui.components;

import com.link.ui.demo.DemoDevice;
import com.link.ui.demo.DeviceStatus;
import com.link.ui.demo.DiscoveryState;
import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Orbit Radar — the central spatial canvas for the Nearby screen.
 *
 * <h3>Architecture</h3>
 * A {@code StackPane} hosts two children in z-order:
 * <ol>
 *   <li>A full-bleed {@link Canvas} driven by an {@link AnimationTimer} at
 *       ~60 FPS. This draws atmosphere, star particles, three concentric dashed
 *       circles at 140/280/420 px, expanding ripple rings, a rotating radar
 *       sweep sector, connection lines, node halos, discovery pulses, and the
 *       transfer beam.</li>
 *   <li>An overlay {@link Pane} that hosts crisp JavaFX nodes: the centre
 *       identity block (64 px glow orb + labels) and one satellite card per
 *       nearby device. Satellite cards are constrained to a fixed size so
 *       {@code prefWidth/prefHeight} always returns a reliable value, which
 *       lets {@code positionOverlay()} centre them precisely.</li>
 * </ol>
 *
 * <h3>Positioning model</h3>
 * <ul>
 *   <li>Orbit radius {@code R} = 42 % of the shorter canvas dimension,
 *       clamped to [180, 320] px. This gives cards room so they don't overlap.</li>
 *   <li>{@code n} devices are placed at evenly-spaced polar angles starting at
 *       −90 ° (top) so the first device always appears at 12 o'clock.</li>
 *   <li>Each card bobs vertically with a unique sine phase (±8 px) — drift
 *       is applied in {@code positionOverlay()} every frame.</li>
 *   <li>The centre block top is placed at {@code cy − 32} so the 64 px icon
 *       is visually centred at {@code (cx, cy)}.</li>
 * </ul>
 *
 * <h3>Colour spec</h3>
 * <ul>
 *   <li>ONLINE devices: green {@code #00FF66} border + glow</li>
 *   <li>AWAY devices: amber {@code #F59E0B} border</li>
 *   <li>Radar elements: cyan {@code #00f2fe}</li>
 * </ul>
 */
public class NetworkVisualization extends StackPane {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color CYAN   = Color.web("#00f2fe");
    private static final Color GREEN  = Color.web("#00FF66");
    private static final Color AMBER  = Color.web("#F59E0B");
    private static final Color VIOLET = Color.web("#4facfe");

    // ── Card geometry — fixed sizes so prefWidth/Height are reliable ──────────
    private static final double CARD_W  = 190.0;
    private static final double CARD_H  = 68.0;
    private static final double ICON_SZ = 64.0;   // centre icon diameter

    // ── Layer stack ───────────────────────────────────────────────────────────
    private final Canvas canvas   = new Canvas();
    private final Pane   overlay  = new Pane();
    private final Label  stateLabel = new Label();

    // ── Device / selection state ──────────────────────────────────────────────
    private List<DemoDevice> devices       = List.of();
    private String           thisName      = "This device";
    private String           thisIp        = "";
    private String           selectedId    = null;
    private String           hoveredId     = null;
    private DiscoveryState   discoveryState = DiscoveryState.FOUND;

    // ── Geometry ──────────────────────────────────────────────────────────────
    /** Canvas / overlay centre point. */
    private double cx, cy;
    /** Orbit radius — distance from centre to each satellite card's anchor. */
    private double R;

    /** Stable base (cx,cy) of each satellite card (index = device index). */
    private final List<double[]> basePos   = new ArrayList<>();
    /** Live position after bobbing drift, updated every frame. */
    private final List<double[]> livePos   = new ArrayList<>();
    /** Per-device drift parameters: [xPhase, yPhase, xFreq, yFreq, ampScale]. */
    private final List<double[]> driftP    = new ArrayList<>();

    private static final double BOB_AMP  = 8.0;   // px
    private static final double BOB_FREQ = 0.20;  // Hz

    // ── Overlay node map ──────────────────────────────────────────────────────
    /** Keys: {@code "CENTER"} for the centre VBox, device.getId() for cards. */
    private final Map<String, javafx.scene.Node> nodeMap = new HashMap<>();

    // ── Background particles ──────────────────────────────────────────────────
    private static final int N_STARS = 55;
    private final double[] starX  = new double[N_STARS];
    private final double[] starY  = new double[N_STARS];
    private final double[] starA  = new double[N_STARS];
    private final double[] starPh = new double[N_STARS];
    private boolean starsReady = false;
    private double  lastW = 0, lastH = 0;

    // ── Radar ripples ─────────────────────────────────────────────────────────
    private static final double RIPPLE_PERIOD = 4.0;   // seconds per ring
    private static final double RIPPLE_MAX_R  = 480.0; // px

    // ── Discovery pulses (periodic outward rings) ─────────────────────────────
    private static final int    N_PULSES      = 3;
    private final double[]      pulseAge      = new double[N_PULSES];
    private static final double PULSE_PERIOD  = 2.6;
    private double              nextPulseAt   = 0.5;

    // ── Radar sweep ───────────────────────────────────────────────────────────
    private double sweepAngle = 0.0; // degrees, advances at 45°/s

    // ── Transfer beam ─────────────────────────────────────────────────────────
    private boolean        beamActive   = false;
    private double         beamProgress = 0.0;
    private static final double BEAM_DURATION = 1.8;
    private static final int    N_BEAM        = 22;
    private final double[] beamFrac = new double[N_BEAM];
    private final double[] beamLat  = new double[N_BEAM];
    private final double[] beamSc   = new double[N_BEAM];

    // ── Floating files layer ──────────────────────────────────────────────────
    private final FloatingFilesLayer filesLayer = new FloatingFilesLayer();

    // ── Pre-allocated DropShadow effects ─────────────────────────────────────
    private final DropShadow fxRing    = new DropShadow(10, CYAN);
    private final DropShadow fxCenter  = new DropShadow(32, CYAN);
    private final DropShadow fxHalo    = new DropShadow(22, CYAN);
    private final DropShadow fxSelHalo = new DropShadow(38, CYAN);
    private final DropShadow fxBeam    = new DropShadow(10, CYAN);
    private final DropShadow fxGreen   = new DropShadow(18, GREEN);

    // ── Animation timer ───────────────────────────────────────────────────────
    private final AnimationTimer timer;
    private double tOffset  = 0.0;
    private long   lastNano = 0L;

    // ── Callback ──────────────────────────────────────────────────────────────
    private SelectHandler onSelect;
    public interface SelectHandler { void select(String deviceId); }

    // =========================================================================
    // Constructor
    // =========================================================================

    public NetworkVisualization() {
        getStyleClass().add("viz-wrap");

        // ── Canvas sizing — bind to parent so it always matches the container ──
        // Canvas.isResizable() == false, so canvas.resize() in layoutChildren
        // silently does nothing. Property bindings are the correct approach.
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.setMouseTransparent(true);

        overlay.setPickOnBounds(false);

        stateLabel.setMouseTransparent(true);
        stateLabel.setFont(Font.font("System", FontWeight.MEDIUM, 11.5));
        stateLabel.setTextFill(Color.web("#94a3b8"));

        // ── Z-order (bottom → top): canvas → overlay nodes → state label ──────
        getChildren().addAll(canvas, overlay, stateLabel);
        StackPane.setAlignment(stateLabel, Pos.BOTTOM_CENTER);
        stateLabel.setTranslateY(-14);

        fxRing.setSpread(0.0);
        fxCenter.setSpread(0.30);
        fxHalo.setSpread(0.0);
        fxSelHalo.setSpread(0.20);
        fxBeam.setSpread(0.45);
        fxGreen.setSpread(0.15);

        timer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNano == 0L) lastNano = now;
                double dt = Math.min(0.05, (now - lastNano) / 1_000_000_000.0);
                lastNano = now;
                tOffset += dt;
                drawFrame(tOffset, dt);
                positionOverlay(tOffset);
            }
        };

        sceneProperty().addListener((obs, ov, scene) -> {
            if (scene != null) {
                scene.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ESCAPE && onSelect != null && selectedId != null)
                        onSelect.select(selectedId);
                });
            }
        });
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public void start() { lastNano = 0L; timer.start(); }
    public void stop()  { timer.stop(); }
    public void setOnSelect(SelectHandler h) { this.onSelect = h; }

    /** 3-arg overload (no IP) kept for backward compatibility. */
    public void update(List<DemoDevice> devs, String name,
                       String newSelId, DiscoveryState state) {
        update(devs, name, "", newSelId, state);
    }

    /** Full update. Only rebuilds overlay nodes when the device list changes. */
    public void update(List<DemoDevice> devs, String name, String ip,
                       String newSelId, DiscoveryState state) {
        boolean listChanged = !sameIds(this.devices, devs);
        this.devices        = (devs == null) ? List.of() : devs;
        this.thisName       = (name == null) ? "This device" : name;
        this.thisIp         = (ip == null)   ? ""            : ip;
        this.selectedId     = newSelId;
        this.discoveryState = (state == null) ? DiscoveryState.FOUND : state;
        if (listChanged) {
            computeDriftParams();
            rebuildOverlay();
        } else {
            updateOverlayStyles();
        }
        requestLayout();
    }

    public void triggerBeam() { triggerBeam(null); }

    public void triggerBeam(java.util.List<String> fileNames) {
        if (selectedId == null) return;
        Random rng = new Random();
        for (int i = 0; i < N_BEAM; i++) {
            beamFrac[i] = rng.nextDouble() * 0.55;
            beamLat[i]  = rng.nextDouble() * 10 - 5;
            beamSc[i]   = rng.nextDouble() * 0.5 + 0.75;
        }
        beamProgress = 0.0;
        beamActive   = true;
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getId().equals(selectedId)) {
                double[] lp = live(i);
                filesLayer.triggerTransfer(cx, cy, lp[0], lp[1], fileNames);
                break;
            }
        }
    }

    // =========================================================================
    // Layout
    // =========================================================================

    @Override protected void layoutChildren() {
        super.layoutChildren();
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        // canvas size is already bound to widthProperty/heightProperty.
        // We only need to resize the overlay pane and recompute geometry here.
        overlay.resize(w, h);
        computeBasePositions(w, h);
        if (Math.abs(w - lastW) > 10 || Math.abs(h - lastH) > 10) {
            initStars(w, h);
            lastW = w; lastH = h;
        }
    }

    // =========================================================================
    // Geometry
    // =========================================================================

    private void computeBasePositions(double w, double h) {
        cx = w / 2.0;
        cy = h / 2.0;
        // 42% of shorter dimension, clamped to [180, 320] px.
        // This ensures satellite cards (190×68) have room and don't overlap.
        R = Math.min(w, h) * 0.42;
        R = Math.max(180, Math.min(R, 320));

        // Resize live list to match device count
        while (livePos.size() < devices.size()) livePos.add(new double[]{cx, cy});
        while (livePos.size() > devices.size()) livePos.remove(livePos.size() - 1);
        basePos.clear();

        int n = devices.size();
        for (int i = 0; i < n; i++) {
            // Start at top (−90°), distribute evenly
            double angle = Math.toRadians(-90.0 + (360.0 / n) * i);
            basePos.add(new double[]{cx + R * Math.cos(angle), cy + R * Math.sin(angle)});
        }
    }

    private void computeDriftParams() {
        driftP.clear();
        Random rng = new Random(0xCAFEF00D);
        int n = (devices == null) ? 0 : devices.size();
        for (int i = 0; i < n; i++) {
            driftP.add(new double[]{
                rng.nextDouble() * Math.PI * 2,
                rng.nextDouble() * Math.PI * 2,
                BOB_FREQ * (0.7 + rng.nextDouble() * 0.5),
                BOB_FREQ * (0.6 + rng.nextDouble() * 0.5),
                0.65 + rng.nextDouble() * 0.55
            });
        }
    }

    private boolean sameIds(List<DemoDevice> a, List<DemoDevice> b) {
        if (a == b) return true;
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (!a.get(i).getId().equals(b.get(i).getId())) return false;
        return true;
    }

    private void initStars(double w, double h) {
        Random rng = new Random(0x4242);
        for (int i = 0; i < N_STARS; i++) {
            starX[i]  = rng.nextDouble() * w;
            starY[i]  = rng.nextDouble() * h;
            starA[i]  = rng.nextDouble() * 0.28 + 0.05;
            starPh[i] = rng.nextDouble() * Math.PI * 2;
        }
        starsReady = true;
    }

    private double[] live(int i) {
        if (i < livePos.size()) return livePos.get(i);
        if (i < basePos.size())  return basePos.get(i);
        return new double[]{cx, cy};
    }

    // =========================================================================
    // Overlay — build & style
    // =========================================================================

    /**
     * Fully rebuilds the overlay scene graph.
     * Only called when the device list identity changes.
     */
    private void rebuildOverlay() {
        overlay.getChildren().clear();
        nodeMap.clear();

        // ── Centre identity block ─────────────────────────────────────────────
        VBox centerBlock = buildCenterBlock();
        nodeMap.put("CENTER", centerBlock);
        overlay.getChildren().add(centerBlock);

        // ── Satellite cards ───────────────────────────────────────────────────
        for (DemoDevice d : devices) {
            HBox card = buildCard(d);
            nodeMap.put(d.getId(), card);
            overlay.getChildren().add(card);
        }
    }

    private VBox buildCenterBlock() {
        // 64×64 glowing orb
        StackPane orb = new StackPane();
        orb.setMinSize(ICON_SZ, ICON_SZ);
        orb.setPrefSize(ICON_SZ, ICON_SZ);
        orb.setMaxSize(ICON_SZ, ICON_SZ);
        orb.setStyle("-fx-background-color: rgba(0,242,254,0.18);"
                + "-fx-background-radius: 32;"
                + "-fx-border-color: rgba(0,242,254,0.70) rgba(0,242,254,0.35)"
                        + " rgba(0,242,254,0.35) rgba(0,242,254,0.70);"
                + "-fx-border-width: 2; -fx-border-radius: 32;"
                + "-fx-effect: dropshadow(gaussian,rgba(0,242,254,0.55),22,0.30,0,0);");
        orb.getChildren().add(Icons.send(26, CYAN));

        Label badge = new Label("YOUR DEVICE");
        badge.setFont(Font.font("System", FontWeight.BOLD, 8.5));
        badge.setTextFill(CYAN);
        badge.setOpacity(0.85);

        Label name = new Label(shorten(thisName, 24));
        name.setFont(Font.font("System", FontWeight.MEDIUM, 12.5));
        name.setTextFill(Color.web("#ffffff"));

        Label ipLbl = new Label(thisIp.isBlank() ? "" : thisIp);
        ipLbl.setFont(Font.font("System", FontWeight.NORMAL, 10.5));
        ipLbl.setTextFill(CYAN);
        ipLbl.setStyle("-fx-background-color: rgba(0,242,254,0.12);"
                + "-fx-background-radius: 4; -fx-padding: 1 7 1 7;");
        ipLbl.setVisible(!thisIp.isBlank());
        ipLbl.setManaged(!thisIp.isBlank());

        // gap = 5px between children (VBox spacing), alignment = centre
        VBox vb = new VBox(5, orb, badge, name, ipLbl);
        vb.setAlignment(Pos.TOP_CENTER);
        vb.setMouseTransparent(true);
        return vb;
    }

    /**
     * Builds a fixed-size (CARD_W × CARD_H) satellite card for one device.
     *
     * <p>Border colour is {@code #00FF66} (green) for ONLINE devices and
     * {@code #F59E0B} (amber) for AWAY/OFFLINE, matching the spec.
     *
     * <p>The card is given a <strong>fixed pref/min/max size</strong> so
     * {@code prefWidth(-1)} / {@code prefHeight(-1)} always returns reliable
     * values, enabling correct centring in {@link #positionOverlay}.
     */
    private HBox buildCard(DemoDevice d) {
        boolean sel    = d.getId().equals(selectedId);
        boolean online = d.getStatus() == DeviceStatus.ONLINE;

        // Status colour
        Color statusColor = online ? GREEN : AMBER;

        // Icon box
        StackPane iconBox = new StackPane();
        iconBox.setMinSize(40, 40); iconBox.setPrefSize(40, 40); iconBox.setMaxSize(40, 40);
        String iconBg = sel
                ? "rgba(0,242,254,0.22)"
                : (online ? "rgba(0,255,102,0.14)" : "rgba(245,158,11,0.14)");
        iconBox.setStyle("-fx-background-color:" + iconBg + "; -fx-background-radius:10;");
        Color iconColor = sel ? CYAN : statusColor;
        iconBox.getChildren().add(Icons.device(20, d.getType().name(), iconColor));

        // Name + OS badge
        Label nameLbl = new Label(shorten(d.getName(), 16));
        nameLbl.setFont(Font.font("System", FontWeight.MEDIUM, 12));
        nameLbl.setTextFill(Color.web("#ffffff"));
        Label osBadge = new Label(osShort(d.getOsLabel()));
        osBadge.setFont(Font.font("System", FontWeight.BOLD, 8));
        osBadge.setStyle("-fx-text-fill:" + toHex(sel ? CYAN : VIOLET) + ";"
                + "-fx-background-color:" + toHex(sel ? CYAN : VIOLET, 0.18) + ";"
                + "-fx-background-radius:3; -fx-padding:1 5 1 5;");
        HBox nameLine = new HBox(5, nameLbl, osBadge);
        nameLine.setAlignment(Pos.CENTER_LEFT);

        // Status dot + label
        Region dot = new Region();
        dot.setMinSize(7, 7); dot.setPrefSize(7, 7); dot.setMaxSize(7, 7);
        dot.setStyle("-fx-background-color:" + toHex(statusColor) + ";"
                + "-fx-background-radius:4;"
                + (online ? "-fx-effect:dropshadow(gaussian," + toHex(statusColor, 0.70) + ",6,0.5,0,0);" : ""));
        Label statusLbl = new Label(online ? "Online" : "Away");
        statusLbl.setFont(Font.font("System", FontWeight.MEDIUM, 10));
        statusLbl.setTextFill(statusColor.deriveColor(0, 1, 1, 0.85));
        HBox statusRow = new HBox(5, dot, statusLbl);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // IP
        Label ipLbl = new Label(d.getIpAddress());
        ipLbl.setFont(Font.font("System", FontWeight.NORMAL, 10));
        ipLbl.setTextFill(Color.web("#94a3b8"));

        // "→ SENDING TO" tag
        Label sendTag = new Label("→ SENDING TO");
        sendTag.setFont(Font.font("System", FontWeight.BOLD, 8));
        sendTag.setTextFill(CYAN);
        sendTag.setVisible(sel);
        sendTag.setManaged(sel);

        VBox textCol = new VBox(2, nameLine, statusRow, ipLbl, sendTag);
        textCol.setAlignment(Pos.CENTER_LEFT);

        HBox card = new HBox(10, iconBox, textCol);
        card.setAlignment(Pos.CENTER_LEFT);

        // ── Fixed size ────────────────────────────────────────────────────────
        card.setMinSize(CARD_W, CARD_H);
        card.setPrefSize(CARD_W, CARD_H);
        card.setMaxSize(CARD_W, CARD_H);

        // ── Style ─────────────────────────────────────────────────────────────
        // Border colour: cyan if selected, green if online, amber if away
        String borderCol = sel
                ? "rgba(0,242,254,0.80)"
                : (online ? "rgba(0,255,102,0.40)" : "rgba(245,158,11,0.40)");
        String baseGlow = sel
                ? "-fx-effect:dropshadow(gaussian,rgba(0,242,254,0.55),22,0.30,0,0);"
                : (online
                    ? "-fx-effect:dropshadow(gaussian,rgba(0,255,102,0.30),14,0.20,0,0);"
                    : "");
        String baseStyle = "-fx-background-color:rgba(15,23,42,0.88);"
                + "-fx-background-radius:16;"
                + "-fx-border-color:" + borderCol + ";"
                + "-fx-border-width:" + (sel ? "1.8" : "1") + ";"
                + "-fx-border-radius:16;"
                + "-fx-padding: 10 14 10 12;"
                + "-fx-cursor:hand;"
                + baseGlow;
        card.setStyle(baseStyle);

        // ── Click → select/deselect ───────────────────────────────────────────
        card.setOnMouseClicked(e -> {
            if (onSelect != null) onSelect.select(d.getId());
            e.consume();
        });

        // ── Hover: update hoveredId + apply hover style ───────────────────────
        final String devId = d.getId();
        String hoverGlow = online
                ? "-fx-effect:dropshadow(gaussian,rgba(0,255,102,0.75),26,0.40,0,0);"
                : "-fx-effect:dropshadow(gaussian,rgba(0,242,254,0.70),26,0.40,0,0);";
        card.setOnMouseEntered(e -> {
            hoveredId = devId;
            card.setScaleX(1.05); card.setScaleY(1.05);
            card.setStyle(baseStyle.replace(baseGlow, "") + hoverGlow);
        });
        card.setOnMouseExited(e -> {
            if (devId.equals(hoveredId)) hoveredId = null;
            card.setScaleX(1.0); card.setScaleY(1.0);
            card.setStyle(baseStyle);
        });

        return card;
    }

    /** Rebuild styles in-place when selection changes but device list doesn't. */
    private void updateOverlayStyles() {
        // Update centre name label
        javafx.scene.Node cb = nodeMap.get("CENTER");
        if (cb instanceof VBox vb && vb.getChildren().size() >= 3
                && vb.getChildren().get(2) instanceof Label l)
            l.setText(shorten(thisName, 24));

        // Rebuild each card with fresh styles
        for (DemoDevice d : devices) {
            javafx.scene.Node old = nodeMap.get(d.getId());
            if (old == null) continue;
            int idx = overlay.getChildren().indexOf(old);
            HBox fresh = buildCard(d);
            nodeMap.put(d.getId(), fresh);
            if (idx >= 0) overlay.getChildren().set(idx, fresh);
            else overlay.getChildren().add(fresh);
        }
    }

    // =========================================================================
    // Overlay positioning — called every frame
    // =========================================================================

    private void positionOverlay(double t) {
        double w = canvas.getWidth(), h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        // Ensure geometry is initialised even if layoutChildren hasn't fired yet.
        if (cx == 0 && cy == 0) computeBasePositions(w, h);

        // ── Centre block ──────────────────────────────────────────────────────
        // The VBox top-child is the 64×64 orb; its centre should sit at (cx,cy).
        // → place VBox top at cy − 32.
        javafx.scene.Node cb = nodeMap.get("CENTER");
        if (cb != null) {
            double bw = cb.prefWidth(-1);
            if (bw <= 0) bw = ICON_SZ;    // fallback before first layout pass
            cb.setLayoutX(cx - bw / 2.0);
            cb.setLayoutY(cy - ICON_SZ / 2.0);
        }

        // ── Satellite cards ───────────────────────────────────────────────────
        for (int i = 0; i < devices.size(); i++) {
            // Apply per-device bobbing (Y only; tiny X jitter)
            double[] dp = (i < driftP.size()) ? driftP.get(i) : new double[]{0, 0, 0.2, 0.2, 1};
            double bob    = BOB_AMP * dp[4] * Math.sin(t * dp[2] * Math.PI * 2 + dp[0]);
            double xDrift = BOB_AMP * 0.35 * dp[4] * Math.sin(t * dp[3] * Math.PI * 2 + dp[1]);
            if (i < basePos.size()) {
                if (i < livePos.size()) {
                    livePos.get(i)[0] = basePos.get(i)[0] + xDrift;
                    livePos.get(i)[1] = basePos.get(i)[1] + bob;
                }
            }

            double[] lp = live(i);
            javafx.scene.Node card = nodeMap.get(devices.get(i).getId());
            if (card == null) continue;
            // Cards have fixed CARD_W × CARD_H — no need to call prefWidth.
            card.setLayoutX(lp[0] - CARD_W / 2.0);
            card.setLayoutY(lp[1] - CARD_H / 2.0);
        }
    }

    // =========================================================================
    // Main draw frame — AnimationTimer callback target
    // =========================================================================

    private void drawFrame(double t, double dt) {
        // Read canvas dimensions from the bound properties every frame.
        // canvas.getWidth()/getHeight() reflect the bindings correctly.
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        if (w <= 0 || h <= 0) return;

        // If geometry hasn't been computed yet, do it now.
        if (cx == 0 && cy == 0) computeBasePositions(w, h);

        // Advance pulse ages
        updatePulses(t, dt);

        // Advance transfer beam
        if (beamActive) {
            beamProgress += dt / BEAM_DURATION;
            if (beamProgress >= 1.0) beamActive = false;
        }

        g.clearRect(0, 0, w, h);

        // ── 1. Atmospheric radial gradient ────────────────────────────────────
        drawAtmosphere(g, w, h);

        // ── 2. Star field ─────────────────────────────────────────────────────
        if (starsReady) drawStars(g, t);

        // ── 3. Floating file tokens ───────────────────────────────────────────
        filesLayer.draw(g, t, dt, w, h);

        // ── 4. Static concentric dashed rings (140, 280, 420 px) ─────────────
        drawConcentricRings(g, t);

        // ── 5. Expanding ripple rings ─────────────────────────────────────────
        drawRipples(g, t);

        // ── 6. Discovery pulses ───────────────────────────────────────────────
        drawDiscoveryPulses(g);

        // ── 7. Rotating radar sweep sector ───────────────────────────────────
        sweepAngle = (sweepAngle + dt * 45.0) % 360.0;
        drawSweep(g);

        // ── 8. Connection lines ───────────────────────────────────────────────
        if (discoveryState == DiscoveryState.FOUND) {
            for (int i = 0; i < devices.size(); i++) {
                double[] lp = live(i);
                boolean sel    = devices.get(i).getId().equals(selectedId);
                boolean online = devices.get(i).getStatus() == DeviceStatus.ONLINE;
                drawConnection(g, lp[0], lp[1], sel, online, t, i);
            }
        }

        // ── 9. Hover laser tether (if hovering a non-selected device) ─────────
        if (hoveredId != null && !hoveredId.equals(selectedId)) {
            for (int i = 0; i < devices.size(); i++) {
                if (devices.get(i).getId().equals(hoveredId)) {
                    drawLaserTether(g, live(i)[0], live(i)[1], t);
                    break;
                }
            }
        }

        // ── 10. Node halos ────────────────────────────────────────────────────
        if (discoveryState == DiscoveryState.FOUND) {
            for (int i = 0; i < devices.size(); i++) {
                double[] lp = live(i);
                boolean sel    = devices.get(i).getId().equals(selectedId);
                boolean online = devices.get(i).getStatus() == DeviceStatus.ONLINE;
                drawNodeHalo(g, lp[0], lp[1], online, sel, t, i);
            }
        }

        // ── 11. Centre pulsing halo ───────────────────────────────────────────
        drawCenterHalo(g, t);

        // ── 12. Transfer beam ─────────────────────────────────────────────────
        if (beamActive || beamProgress > 0) drawBeam(g);

        // ── 13. State text ────────────────────────────────────────────────────
        refreshStateLabel();
    }

    // =========================================================================
    // Canvas draw helpers
    // =========================================================================

    private void drawAtmosphere(GraphicsContext g, double w, double h) {
        // Fill the canvas with the deep-space base colour first so all
        // subsequent drawing is visible against a consistent dark background.
        g.setFill(Color.web("#0b0f19"));
        g.fillRect(0, 0, w, h);
        // Soft cyan glow from centre
        RadialGradient grad = new RadialGradient(0, 0, cx, cy, Math.max(w, h) * 0.60,
                false, CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.web("#00f2fe", 0.10)),
                new Stop(0.45, Color.web("#00f2fe", 0.04)),
                new Stop(1.00, Color.TRANSPARENT));
        g.setFill(grad);
        g.fillRect(0, 0, w, h);
    }

    private void drawStars(GraphicsContext g, double t) {
        g.save();
        for (int i = 0; i < N_STARS; i++) {
            double a  = starA[i] * (0.4 + 0.6 * Math.sin(t * 0.5 + starPh[i]));
            double pr = 1.0 + 0.5 * Math.sin(t * 0.4 + starPh[i] * 1.3);
            g.setFill(CYAN.deriveColor(0, 1, 1, a * 0.35));
            g.fillOval(starX[i] - pr, starY[i] - pr, pr * 2, pr * 2);
        }
        g.restore();
    }

    /**
     * Three concentric radar circles scaled to the orbit radius so they always
     * surround the satellite cards and are always visible within the canvas.
     * Radii: inner = 55% of R (tight around centre), middle = 100% (at card
     * orbit), outer = 155% (beyond the outermost card).
     * Alpha is deliberately bright so the rings register clearly.
     */
    private void drawConcentricRings(GraphicsContext g, double t) {
        g.save();
        double[] radii  = {R * 0.55, R * 1.00, R * 1.55};
        double[] alphas = {0.50,      0.38,      0.22};

        for (int i = 0; i < radii.length; i++) {
            double r       = radii[i];
            double breathe = 0.82 + 0.18 * Math.sin(t * 0.45 + i * 1.1);
            double alpha   = alphas[i] * breathe;
            fxRing.setRadius(12 - i * 3);
            g.setEffect(fxRing);
            g.setStroke(CYAN.deriveColor(0, 1, 1, alpha));
            g.setLineWidth(i == 0 ? 1.5 : i == 1 ? 1.2 : 0.9);
            g.setLineDashes(8.0, 10.0);
            g.strokeOval(cx - r, cy - r, r * 2, r * 2);
            g.setEffect(null);
        }
        g.restore();
    }

    /**
     * Three expanding ripple rings: 4 s period, staggered by 4/3 s each.
     * Expands from 0 to the outer ring radius, fading to transparent.
     * Alpha starts at 0.45 so the effect is visible.
     */
    private void drawRipples(GraphicsContext g, double t) {
        g.save();
        g.setLineDashes(null);
        double maxR = R * 1.65;
        for (int i = 0; i < 3; i++) {
            double phase = ((t + i * (RIPPLE_PERIOD / 3.0)) % RIPPLE_PERIOD) / RIPPLE_PERIOD;
            double r     = phase * maxR;
            double alpha = 0.45 * (1.0 - phase);
            if (alpha < 0.02 || r < 1) continue;
            g.setLineWidth(1.8 * (1.0 - phase * 0.7));
            g.setStroke(CYAN.deriveColor(0, 1, 1, alpha));
            g.strokeOval(cx - r, cy - r, r * 2, r * 2);
        }
        g.restore();
    }

    /** Periodic discovery pulses (shorter period than ripples, brighter). */
    private void updatePulses(double t, double dt) {
        if (discoveryState != DiscoveryState.FOUND) return;
        if (t >= nextPulseAt) {
            for (int i = 0; i < N_PULSES; i++) {
                if (pulseAge[i] <= 0 || pulseAge[i] > 1.05) {
                    pulseAge[i] = 0.001;
                    nextPulseAt = t + PULSE_PERIOD;
                    break;
                }
            }
        }
        for (int i = 0; i < N_PULSES; i++)
            if (pulseAge[i] > 0) pulseAge[i] = Math.min(1.1, pulseAge[i] + dt * 0.38);
    }

    private void drawDiscoveryPulses(GraphicsContext g) {
        g.save();
        g.setLineDashes(null);
        for (double age : pulseAge) {
            if (age <= 0 || age > 1.05) continue;
            double r     = age * (R + 60);
            double alpha = Math.max(0, 0.30 * (1.0 - age));
            g.setStroke(CYAN.deriveColor(0, 1, 1, alpha));
            g.setLineWidth(1.5);
            g.strokeOval(cx - r, cy - r, r * 2, r * 2);
        }
        g.restore();
    }

    /**
     * Full-time rotating radar sweep sector. The sector trailing opacity is
     * raised so it reads clearly against the dark background.
     */
    private void drawSweep(GraphicsContext g) {
        g.save();
        g.translate(cx, cy);
        g.rotate(sweepAngle);
        double sweepR = R * 1.5;
        // Trailing sector — 8 quadratic-fade layers, brighter than before
        for (int i = 0; i < 8; i++) {
            double frac = 1.0 - i / 8.0;
            g.setGlobalAlpha(0.14 * frac * frac);
            g.setFill(CYAN.deriveColor(0, 1, 1, 1.0));
            g.beginPath();
            g.moveTo(0, 0);
            g.arc(0, 0, sweepR, sweepR, 0, -(65.0 * frac));
            g.lineTo(0, 0);
            g.fill();
        }
        g.setGlobalAlpha(1.0);
        // Leading-edge glow line
        fxBeam.setRadius(8);
        g.setEffect(fxBeam);
        g.setLineDashes(null);
        g.setLineWidth(2.0);
        g.setStroke(CYAN.deriveColor(0, 1, 1, 0.80));
        g.strokeLine(0, 0, sweepR, 0);
        g.setEffect(null);
        g.restore();
    }

    /**
     * Connection line from centre to a device.
     * Selected → animated travelling-dot line (dash offset advances each frame).
     * Online unselected → faint static dashes.
     */
    private void drawConnection(GraphicsContext g, double x, double y,
                                boolean sel, boolean online, double t, int i) {
        if (!online && !sel) return;
        g.save();
        if (sel) {
            // Glow underlay
            g.setLineDashes(null);
            g.setLineWidth(4.0);
            g.setStroke(CYAN.deriveColor(0, 1, 1, 0.10));
            g.strokeLine(cx, cy, x, y);
            // Animated travelling dots
            double offset = (t * 55.0) % 20.0;
            g.setLineDashes(8, 12);
            g.setLineDashOffset(-offset);
            double alpha = 0.72 + 0.22 * Math.sin(t * 4.0);
            g.setStroke(CYAN.deriveColor(0, 1, 1, alpha));
            g.setLineWidth(2.2);
            g.strokeLine(cx, cy, x, y);
            // Arrowhead at 62%
            double dx = x - cx, dy = y - cy, len = Math.sqrt(dx*dx+dy*dy);
            if (len > 0) {
                double ux = dx/len, uy = dy/len;
                double mx = cx + dx*0.62, my = cy + dy*0.62;
                double perp = 5.5;
                double[] ax = {mx - ux*9 + uy*perp, mx, mx - ux*9 - uy*perp};
                double[] ay = {my - uy*9 - ux*perp, my, my - uy*9 + ux*perp};
                g.setLineDashes(null);
                g.setLineWidth(1.8);
                g.setStroke(CYAN.deriveColor(0, 1, 1, 0.88));
                g.strokePolyline(ax, ay, 3);
            }
        } else {
            double alpha = 0.10 + 0.07 * (Math.sin(t * 1.4 + i * 1.2) * 0.5 + 0.5);
            g.setStroke(VIOLET.deriveColor(0, 1, 1, alpha));
            g.setLineWidth(1.0);
            g.setLineDashes(4, 10);
            g.strokeLine(cx, cy, x, y);
        }
        g.restore();
    }

    /**
     * Hover laser tether — drawn when the mouse is over a non-selected card.
     * Uses cyan-green (#00F2FE / #00FF66 blend), shimmers.
     */
    private void drawLaserTether(GraphicsContext g, double x, double y, double t) {
        g.save();
        double shimmer = 0.50 + 0.30 * Math.sin(t * 7.0);
        // Glow underlay
        g.setLineDashes(null);
        g.setLineWidth(5.0);
        g.setStroke(GREEN.deriveColor(0, 1, 1, 0.12));
        g.strokeLine(cx, cy, x, y);
        // Dashed laser
        g.setLineDashes(8, 6);
        g.setLineDashOffset(-(t * 50 % 14));
        g.setLineWidth(1.8);
        g.setStroke(CYAN.deriveColor(0, 1, 1, shimmer));
        g.strokeLine(cx, cy, x, y);
        // Destination halo
        fxHalo.setRadius(16);
        g.setEffect(fxHalo);
        g.setFill(CYAN.deriveColor(0, 1, 1, 0.16));
        g.fillOval(x - 10, y - 10, 20, 20);
        g.setEffect(null);
        g.restore();
    }

    private void drawNodeHalo(GraphicsContext g, double x, double y,
                               boolean online, boolean sel, double t, int i) {
        double phase = online ? (Math.sin(t * 1.8 + i * 0.85) * 0.5 + 0.5) : 0;
        double baseA = (online ? 0.13 : 0) + (sel ? 0.28 : 0);
        if (baseA <= 0) return;
        double r = 28.0 + (sel ? 8 : 0) + phase * 5;
        DropShadow fx = sel ? fxSelHalo : (online ? fxGreen : fxHalo);
        fx.setRadius(r * 1.8);
        g.save();
        g.setEffect(fx);
        Color hc = (online && !sel) ? GREEN : CYAN;
        g.setFill(hc.deriveColor(0, 1, 1, baseA + phase * 0.05));
        g.fillOval(x - r/2, y - r/2, r, r);
        g.setEffect(null);
        g.restore();
    }

    /**
     * Centre pulsing halo — three staggered expanding rings + glowing core.
     */
    private void drawCenterHalo(GraphicsContext g, double t) {
        if (discoveryState == DiscoveryState.PAUSED
                || discoveryState == DiscoveryState.UNAVAILABLE) {
            g.setFill(Color.web("#94a3b8", 0.06));
            g.fillOval(cx - 28, cy - 28, 56, 56);
            return;
        }
        double pulsePeriod = 2.4;
        for (int ring = 0; ring < 3; ring++) {
            double phase = ((t + ring * (pulsePeriod / 3.0)) % pulsePeriod) / pulsePeriod;
            double pr    = 30 + phase * 55;
            double alpha = 0.32 * (1.0 - phase);
            if (alpha > 0.01) {
                fxCenter.setRadius(pr * 0.55);
                g.save();
                g.setEffect(fxCenter);
                g.setStroke(CYAN.deriveColor(0, 1, 1, alpha));
                g.setLineDashes(null);
                g.setLineWidth(1.5 * (1.0 - phase * 0.6));
                g.strokeOval(cx - pr, cy - pr, pr * 2, pr * 2);
                g.setEffect(null);
                g.restore();
            }
        }
        // Filled core
        double phase = Math.sin(t * 1.1) * 0.5 + 0.5;
        double r     = 34 + phase * 8;
        fxCenter.setRadius(r * 2.0);
        g.save();
        g.setEffect(fxCenter);
        RadialGradient coreGrad = new RadialGradient(0, 0, cx, cy, r, false,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, CYAN.deriveColor(0, 1, 1, 0.30 + 0.12 * phase)),
                new Stop(0.6, CYAN.deriveColor(0, 1, 1, 0.12)),
                new Stop(1.0, CYAN.deriveColor(0, 1, 1, 0.0)));
        g.setFill(coreGrad);
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        g.setEffect(null);
        g.restore();
        // Crisp ring around orb
        g.setLineDashes(null);
        g.setLineWidth(2.0);
        g.setStroke(CYAN.deriveColor(0, 1, 1, 0.58 + 0.25 * phase));
        g.strokeOval(cx - 37, cy - 37, 74, 74);
    }

    private void drawBeam(GraphicsContext g) {
        int selIdx = -1;
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getId().equals(selectedId)) { selIdx = i; break; }
        }
        if (selIdx < 0) return;
        double[] lp = live(selIdx);
        double tx = lp[0], ty = lp[1];
        double dx = tx - cx, dy = ty - cy;
        double len = Math.sqrt(dx*dx + dy*dy);
        double ux = len > 0 ? dx/len : 0, uy = len > 0 ? dy/len : 0;
        double progress = Math.min(1.0, beamProgress);
        for (int i = 0; i < N_BEAM; i++) {
            double pT = progress - beamFrac[i];
            if (pT <= 0 || pT > 0.95) continue;
            double norm = Math.max(0, Math.min(1, pT / (1.0 - beamFrac[i])));
            double px = cx + dx * norm + (-uy) * beamLat[i];
            double py = cy + dy * norm + (ux)  * beamLat[i];
            double a  = norm < 0.12 ? norm/0.12 : (norm > 0.82 ? (1-norm)/0.18 : 1.0);
            double r  = 3.5 * beamSc[i] * a;
            fxBeam.setRadius(r * 2.8);
            g.save();
            g.setEffect(fxBeam);
            g.setFill(CYAN.deriveColor(0, 1, 1, 0.90 * a));
            g.fillOval(px - r, py - r, r * 2, r * 2);
            g.setEffect(null);
            g.restore();
        }
        if (beamProgress > 0.88 && beamProgress <= 1.0) {
            double flashA = (1.0 - (beamProgress - 0.88) / 0.12);
            g.setFill(CYAN.deriveColor(0, 1, 1, 0.26 * flashA));
            g.fillOval(tx - 24, ty - 24, 48, 48);
        }
    }

    private void refreshStateLabel() {
        String text = switch (discoveryState) {
            case LOADING     -> "Scanning for nearby devices…";
            case FOUND       -> devices.isEmpty() ? "" :
                    devices.size() + " peer" + (devices.size() == 1 ? "" : "s") + " in range";
            case NONE_FOUND  -> "No devices found nearby";
            case PAUSED      -> "Discovery paused";
            case UNAVAILABLE -> "Discovery unavailable";
        };
        stateLabel.setText(text);
        stateLabel.setVisible(!text.isBlank());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
    }

    private static String toHex(Color c, double alpha) {
        return String.format("rgba(%d,%d,%d,%.3f)",
                (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255), alpha);
    }

    private static String osShort(String os) {
        if (os == null || os.isBlank()) return "?";
        if (os.contains("macOS") || os.contains("Mac")) return "macOS";
        if (os.contains("Windows") || os.contains("Win")) return "Windows";
        if (os.contains("Android")) return "Android";
        if (os.contains("iOS")) return "iOS";
        if (os.contains("iPadOS")) return "iPadOS";
        if (os.contains("Linux")) return "Linux";
        int space = os.indexOf(' ');
        return space > 0 ? os.substring(0, space) : os;
    }
}
