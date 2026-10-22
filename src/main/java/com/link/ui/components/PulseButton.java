package com.link.ui.components;

import com.link.ui.theme.Theme;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.util.Random;

/**
 * The Link Pulse — a bespoke circular "Start Sharing" action button.
 *
 * <h3>Visual layers (painted on a single Canvas, bottom to top)</h3>
 * <ol>
 *   <li>Ambient atmospheric glow</li>
 *   <li>Three expanding ring halos (staggered phase)</li>
 *   <li>Orbital particles (6 points circling at varied radii)</li>
 *   <li>Core circle with a cyan→violet radial gradient</li>
 *   <li>Centre icon — an SVG paper-plane / send arrow drawn on-canvas</li>
 *   <li>Label — "START SHARING" / "CHOOSE FILES" / "SENDING…"</li>
 * </ol>
 *
 * <h3>Interaction states</h3>
 * <ul>
 *   <li>{@link State#IDLE} — gentle pulse; no destination selected</li>
 *   <li>{@link State#READY} — faster rings + cyan outline; destination set, waiting for files</li>
 *   <li>{@link State#SENDING} — progress arc replaces rings; transfer in progress (demo)</li>
 * </ul>
 *
 * <h3>Hover / press</h3>
 * Hover increases ring expansion speed and glow intensity via a smooth
 * {@code hoverFactor} that transitions 0→1 on enter and 1→0 on exit
 * at a fixed rate. Press reduces the core radius briefly.
 *
 * <h3>Thread safety</h3>
 * All mutation happens on the JavaFX Application Thread via
 * {@link AnimationTimer}.
 */
public class PulseButton extends StackPane {

    // ── State ────────────────────────────────────────────────────────────────
    public enum State { IDLE, READY, SENDING }

    private State  buttonState = State.IDLE;
    private double sendProgress = 0.0; // 0.0 – 1.0 for SENDING arc

    // ── Hover / press ────────────────────────────────────────────────────────
    private double hoverFactor = 0.0;  // 0=idle 1=fully hovered
    private double pressFactor = 0.0;  // 0=resting, briefly 1 then decay
    private boolean mouseInside = false;

    // ── Ring animation ────────────────────────────────────────────────────────
    // Three rings, each with an independent phase offset
    private static final int N_RINGS = 3;
    private final double[] ringPhase = {0.0, 0.38, 0.72};  // normalised 0–1

    // ── Orbital particles ─────────────────────────────────────────────────────
    private static final int N_ORBITERS = 6;
    private final double[] orbiterAngle  = new double[N_ORBITERS];
    private final double[] orbiterRadius = new double[N_ORBITERS];
    private final double[] orbiterSpeed  = new double[N_ORBITERS];
    private final double[] orbiterSize   = new double[N_ORBITERS];

    // ── Canvas ────────────────────────────────────────────────────────────────
    private final Canvas canvas;
    private static final double BTN_SIZE = 120.0;
    private static final double CX = BTN_SIZE / 2.0;
    private static final double CY = BTN_SIZE / 2.0;
    private static final double CORE_R = 36.0;     // base core radius
    private static final double RING_MIN_R = 46.0; // ring inner radius
    private static final double RING_MAX_R = 58.0; // ring outer radius (animated)

    // ── Pre-allocated effects ─────────────────────────────────────────────────
    private final DropShadow fxGlow = new DropShadow(18, Theme.ACCENT);

    // ── Action callback ───────────────────────────────────────────────────────
    private Runnable onAction;

    // ── Timer ─────────────────────────────────────────────────────────────────
    private final AnimationTimer timer;
    private long   lastNano = 0L;
    private double t        = 0.0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PulseButton() {
        canvas = new Canvas(BTN_SIZE, BTN_SIZE);
        setMinSize(BTN_SIZE, BTN_SIZE);
        setPrefSize(BTN_SIZE, BTN_SIZE);
        setMaxSize(BTN_SIZE, BTN_SIZE);
        setMouseTransparent(false);
        getChildren().add(canvas);

        // Initialise orbiters with stable randomness
        Random rng = new Random(0xFEEDF00D);
        for (int i = 0; i < N_ORBITERS; i++) {
            orbiterAngle[i]  = rng.nextDouble() * Math.PI * 2;
            orbiterRadius[i] = 46 + rng.nextDouble() * 14;  // 46–60 px from center
            orbiterSpeed[i]  = 0.35 + rng.nextDouble() * 0.45; // rad/s
            orbiterSize[i]   = 1.8 + rng.nextDouble() * 1.4;
        }

        // ── Interaction ──────────────────────────────────────────────────────
        setOnMouseEntered(e -> mouseInside = true);
        setOnMouseExited( e -> mouseInside = false);
        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) pressFactor = 1.0;
        });
        setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY && mouseInside && onAction != null) {
                onAction.run();
            }
        });

        timer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNano == 0L) lastNano = now;
                double dt = Math.min(0.05, (now - lastNano) / 1_000_000_000.0);
                lastNano = now;
                t += dt;

                // Smooth hover transition (250ms in, 400ms out)
                double hoverRate = mouseInside ? (dt / 0.25) : -(dt / 0.40);
                hoverFactor = Math.max(0, Math.min(1, hoverFactor + hoverRate));

                // Press decay
                pressFactor = Math.max(0, pressFactor - dt / 0.18);

                // Advance ring phases
                double ringSpeed = baseRingSpeed();
                for (int i = 0; i < N_RINGS; i++) {
                    ringPhase[i] = (ringPhase[i] + dt * ringSpeed) % 1.0;
                }

                // Advance orbiters
                double orbitSpeed = 1.0 + hoverFactor * 1.8;
                for (int i = 0; i < N_ORBITERS; i++) {
                    orbiterAngle[i] = (orbiterAngle[i] + dt * orbiterSpeed[i] * orbitSpeed) % (Math.PI * 2);
                }

                drawFrame(t);
            }
        };
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void start() { lastNano = 0L; timer.start(); }
    public void stop()  { timer.stop(); }

    public void setState(State s)            { this.buttonState = s; }
    public void setSendProgress(double p)    { this.sendProgress = Math.max(0, Math.min(1, p)); }
    public void setOnAction(Runnable action) { this.onAction = action; }
    public State getState()                  { return buttonState; }

    // ── Drawing ───────────────────────────────────────────────────────────────

    private double baseRingSpeed() {
        return switch (buttonState) {
            case IDLE    -> 0.28 + hoverFactor * 0.40;
            case READY   -> 0.52 + hoverFactor * 0.60;
            case SENDING -> 0.70;
        };
    }

    private void drawFrame(double t) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, BTN_SIZE, BTN_SIZE);

        drawAmbientGlow(g);

        if (buttonState == State.SENDING) {
            drawSendingArc(g);
        } else {
            drawRings(g);
        }

        drawOrbiters(g, t);
        drawCore(g);
        drawIcon(g);
        drawLabel(g);
    }

    private void drawAmbientGlow(GraphicsContext g) {
        double intensity = switch (buttonState) {
            case IDLE    -> 0.10 + hoverFactor * 0.12;
            case READY   -> 0.18 + hoverFactor * 0.18;
            case SENDING -> 0.22;
        };
        Color glowColor = buttonState == State.READY ? Theme.ACCENT : Theme.ACCENT;
        RadialGradient grad = new RadialGradient(0, 0, CX, CY, CORE_R * 2.0, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Theme.soft(glowColor, intensity)),
                new Stop(1.0, Color.TRANSPARENT));
        g.setFill(grad);
        g.fillOval(CX - CORE_R * 2, CY - CORE_R * 2, CORE_R * 4, CORE_R * 4);
    }

    private void drawRings(GraphicsContext g) {
        // READY state uses violet tint on outer ring
        Color col1 = buttonState == State.READY ? Theme.ACCENT  : Theme.ACCENT;
        Color col2 = buttonState == State.READY ? Theme.ACCENT_2 : Theme.ACCENT;

        for (int i = 0; i < N_RINGS; i++) {
            double p   = ringPhase[i];              // 0→1 expansion progress
            double r   = RING_MIN_R + p * (RING_MAX_R - RING_MIN_R + 6 + hoverFactor * 8);
            double alpha = 0.55 * (1.0 - p) * (0.55 + hoverFactor * 0.40);
            double lineW = 1.5 - p * 0.8;

            Color c = (i == N_RINGS - 1 && buttonState == State.READY) ? col2 : col1;
            g.setStroke(Theme.soft(c, alpha));
            g.setLineWidth(lineW);
            g.setLineDashes(null);
            g.strokeOval(CX - r, CY - r, r * 2, r * 2);
        }
    }

    private void drawSendingArc(GraphicsContext g) {
        // Background track
        g.setStroke(Theme.soft(Theme.BORDER, 0.5));
        g.setLineWidth(3.5);
        g.setLineDashes(null);
        g.strokeOval(CX - RING_MIN_R, CY - RING_MIN_R, RING_MIN_R * 2, RING_MIN_R * 2);

        // Progress arc
        double angle = sendProgress * 360.0;
        g.setStroke(Theme.soft(Theme.ACCENT, 0.92));
        g.setLineWidth(3.5);
        g.strokeArc(CX - RING_MIN_R, CY - RING_MIN_R,
                RING_MIN_R * 2, RING_MIN_R * 2,
                90, -angle, javafx.scene.shape.ArcType.OPEN);

        // Completion flash outer ring
        if (sendProgress > 0.95) {
            double flashA = (sendProgress - 0.95) / 0.05;
            g.setStroke(Theme.soft(Theme.SUCCESS, flashA * 0.60));
            g.setLineWidth(2);
            double fr = RING_MIN_R + 10;
            g.strokeOval(CX - fr, CY - fr, fr * 2, fr * 2);
        }
    }

    private void drawOrbiters(GraphicsContext g, double t) {
        double orbitAlpha = switch (buttonState) {
            case IDLE    -> 0.45 + hoverFactor * 0.35;
            case READY   -> 0.70;
            case SENDING -> 0.55;
        };
        for (int i = 0; i < N_ORBITERS; i++) {
            double ox = CX + orbiterRadius[i] * Math.cos(orbiterAngle[i]);
            double oy = CY + orbiterRadius[i] * Math.sin(orbiterAngle[i]);
            double r  = orbiterSize[i] * (0.8 + 0.2 * Math.sin(t * 2.0 + i));
            Color c   = (i % 2 == 0) ? Theme.ACCENT : Theme.ACCENT_2;
            g.setFill(Theme.soft(c, orbitAlpha * r / 3.2));
            g.fillOval(ox - r, oy - r, r * 2, r * 2);
        }
    }

    private void drawCore(GraphicsContext g) {
        double pressScale = 1.0 - pressFactor * 0.06;
        double r = CORE_R * pressScale;
        double hoverBoost = hoverFactor * 0.04;
        r = r * (1 + hoverBoost);

        // Drop shadow
        fxGlow.setRadius(14 + hoverFactor * 10 + (buttonState == State.READY ? 6 : 0));
        fxGlow.setColor(buttonState == State.READY ? Theme.ACCENT_2 : Theme.ACCENT);
        g.save();
        g.setEffect(fxGlow);

        // Radial gradient: cyan centre → violet edge
        RadialGradient grad = new RadialGradient(0, 0, CX, CY, r, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Theme.soft(Theme.ACCENT, 0.97)),
                new Stop(0.55, Theme.soft(Theme.ACCENT, 0.82)),
                new Stop(1.0, Theme.soft(Theme.ACCENT_2, 0.78)));
        g.setFill(grad);
        g.fillOval(CX - r, CY - r, r * 2, r * 2);
        g.setEffect(null);
        g.restore();

        // Thin bright rim
        g.setStroke(Theme.soft(Theme.ACCENT, 0.90));
        g.setLineWidth(1.2);
        g.setLineDashes(null);
        g.strokeOval(CX - r, CY - r, r * 2, r * 2);
    }

    /** Draws a paper-plane (send arrow) icon centred in the button. */
    private void drawIcon(GraphicsContext g) {
        g.save();
        if (buttonState == State.SENDING) {
            // Animated dots for "sending" — three dots with phase offset
            double dotAlpha = 0.9;
            g.setFill(Color.web("#04141A"));
            for (int i = 0; i < 3; i++) {
                double phase = (t * 2.8 + i * 0.55) % (Math.PI * 2);
                double dy = -Math.sin(phase) * 3.0;
                double cx = CX - 7 + i * 7;
                g.setFill(Theme.soft(Color.web("#04141A"), 0.88 + Math.sin(phase) * 0.12));
                g.fillOval(cx - 2.2, CY + dy - 2.2, 4.4, 4.4);
            }
        } else {
            // Paper-plane: a simple arrow pointing upper-right
            g.setFill(Color.web("#04141A"));
            g.setStroke(Color.web("#04141A"));
            g.setLineWidth(2.0);
            g.setLineDashes(null);
            g.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            g.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
            // Arrow body: pointing right
            double ax = CX - 8, ay = CY;
            double bx = CX + 9, by = CY;
            g.strokeLine(ax, ay, bx, by);
            // Arrow head
            g.strokeLine(bx, by, bx - 5, by - 5);
            g.strokeLine(bx, by, bx - 5, by + 5);
            // Small diagonal lift
            g.strokeLine(ax + 4, ay, ax + 4, ay - 5);
        }
        g.restore();
    }

    /** Draws the button label below the icon. */
    private void drawLabel(GraphicsContext g) {
        String text = switch (buttonState) {
            case IDLE    -> "START SHARING";
            case READY   -> "CHOOSE FILES";
            case SENDING -> "SENDING…";
        };
        g.setFill(Color.web("#04141A", 0.88));
        g.setFont(javafx.scene.text.Font.font("System",
                javafx.scene.text.FontWeight.BOLD, 7.5));
        // Centre-align text manually
        double tw = text.length() * 4.2; // approx
        g.fillText(text, CX - tw / 2.0, CY + 22);
    }
}
