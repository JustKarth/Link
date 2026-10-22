package com.link.ui.components;

import com.link.ui.theme.Theme;
import com.link.ui.util.FormatUtil;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A decorative canvas layer that renders gently drifting generic file tokens
 * behind the device nodes on the Nearby screen.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Draw N ambient file tokens (small rounded squares with a letter icon
 *       and a faint category tint) that drift with independent sine-wave paths.</li>
 *   <li>When a transfer starts ({@link #triggerTransfer}), animate a column of
 *       file tokens traveling from the centre toward the given destination
 *       coordinates. Tokens stagger their start offsets so the stream looks
 *       organic rather than instantaneous.</li>
 *   <li>Never block interaction — this canvas is always mouse-transparent.</li>
 *   <li>Never show real user-file content. All tokens use generic category
 *       labels (PDF, IMG, ZIP, …). Real file names are NOT displayed here.</li>
 * </ul>
 *
 * <h3>Performance</h3>
 * The canvas is cleared and redrawn every frame by the parent
 * {@link NetworkVisualization} AnimationTimer. Pre-allocated
 * {@link DropShadow} objects are reused to avoid GC pressure.
 */
public class FloatingFilesLayer {

    // ── Ambient tokens ────────────────────────────────────────────────────────
    private static final int N_AMBIENT  = 10;
    private static final String[] LABELS = {"PDF","IMG","ZIP","DOC","MP4","PNG","MOV","TXT","KEY","JAR"};
    private static final FormatUtil.FileCategory[] CATS = {
        FormatUtil.FileCategory.DOCUMENT, FormatUtil.FileCategory.IMAGE,
        FormatUtil.FileCategory.ARCHIVE,  FormatUtil.FileCategory.DOCUMENT,
        FormatUtil.FileCategory.VIDEO,    FormatUtil.FileCategory.IMAGE,
        FormatUtil.FileCategory.VIDEO,    FormatUtil.FileCategory.DOCUMENT,
        FormatUtil.FileCategory.DOCUMENT, FormatUtil.FileCategory.CODE
    };

    // Per-token fields (parallel arrays for cache locality)
    private final double[] tkBx     = new double[N_AMBIENT]; // base x
    private final double[] tkBy     = new double[N_AMBIENT]; // base y
    private final double[] tkXPhase = new double[N_AMBIENT];
    private final double[] tkYPhase = new double[N_AMBIENT];
    private final double[] tkXFreq  = new double[N_AMBIENT];
    private final double[] tkYFreq  = new double[N_AMBIENT];
    private final double[] tkAmp    = new double[N_AMBIENT]; // drift amplitude px
    private final double[] tkAlpha  = new double[N_AMBIENT]; // base opacity
    private final double[] tkRot    = new double[N_AMBIENT]; // rotation rad
    private final double[] tkRotSpeed= new double[N_AMBIENT];
    private final double[] tkSize   = new double[N_AMBIENT]; // token half-size
    private boolean tokensInitialised = false;
    private double lastW = 0, lastH = 0;

    // ── Transfer tokens ───────────────────────────────────────────────────────
    private boolean transferActive = false;
    private double  transferProg   = 0.0;   // 0→1
    private double  tgtX = 0, tgtY = 0;    // destination canvas coords
    private double  srcX = 0, srcY = 0;    // source (centre) coords
    private static final int   N_TRANSFER = 5;
    private static final double TRANSFER_DURATION = 1.6; // seconds
    private final double[] txStartFrac  = new double[N_TRANSFER];
    private final double[] txLateral    = new double[N_TRANSFER];
    private final String[] txLabel      = new String[N_TRANSFER];
    private final Color[]  txColor      = new Color[N_TRANSFER];

    // ── Pre-allocated effect ──────────────────────────────────────────────────
    private final DropShadow fxToken = new DropShadow(6, Theme.ACCENT);

    // ── Font cache ────────────────────────────────────────────────────────────
    private final Font labelFont = Font.font("System", FontWeight.BOLD, 6.5);

    // ── Constructor ───────────────────────────────────────────────────────────
    public FloatingFilesLayer() {
        fxToken.setSpread(0.2);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Trigger a transfer stream from {@code (sx, sy)} toward {@code (tx, ty)}.
     * Uses generic labels — no real file data is shown.
     */
    public void triggerTransfer(double sx, double sy, double tx, double ty,
                                List<String> fileNames) {
        srcX = sx; srcY = sy;
        tgtX = tx; tgtY = ty;
        transferProg  = 0.0;
        transferActive = true;

        Random rng = new Random();
        for (int i = 0; i < N_TRANSFER; i++) {
            txStartFrac[i] = i * (0.55 / N_TRANSFER);  // stagger 0–0.55
            txLateral[i]   = rng.nextDouble() * 14 - 7;
            // Use real file extension label if available, else generic
            if (fileNames != null && i < fileNames.size()) {
                String ext = FormatUtil.extensionOf(fileNames.get(i));
                txLabel[i] = ext.isBlank() ? "FILE" : ext.toUpperCase();
                txLabel[i] = txLabel[i].length() > 4 ? txLabel[i].substring(0, 3) : txLabel[i];
            } else {
                txLabel[i] = LABELS[rng.nextInt(LABELS.length)];
            }
            // Colour from category
            FormatUtil.FileCategory cat = (fileNames != null && i < fileNames.size())
                    ? FormatUtil.categorize(fileNames.get(i))
                    : CATS[rng.nextInt(CATS.length)];
            txColor[i] = Theme.colorFor(cat);
        }
    }

    /** Advance and draw everything. Called by the parent AnimationTimer. */
    public void draw(GraphicsContext g, double t, double dt, double w, double h) {
        if (w <= 0 || h <= 0) return;

        // Re-initialise positions if canvas size changed meaningfully
        if (!tokensInitialised || Math.abs(w - lastW) > 20 || Math.abs(h - lastH) > 20) {
            initTokens(w, h);
        }

        drawAmbientTokens(g, t, w, h);

        if (transferActive) {
            transferProg += dt / TRANSFER_DURATION;
            if (transferProg >= 1.0) {
                transferProg  = 1.0;
                transferActive = false;
            }
            drawTransferTokens(g);
        }
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    private void initTokens(double w, double h) {
        Random rng = new Random(0xB00B1E5);
        // Scatter tokens around the outer region, away from center
        double cx = w / 2, cy = h / 2;
        for (int i = 0; i < N_AMBIENT; i++) {
            // Place in a ring between 45% and 80% of the shorter dimension
            double r     = Math.min(w, h) * (0.38 + rng.nextDouble() * 0.35);
            double angle = rng.nextDouble() * Math.PI * 2;
            tkBx[i]      = cx + r * Math.cos(angle);
            tkBy[i]      = cy + r * Math.sin(angle);
            tkXPhase[i]  = rng.nextDouble() * Math.PI * 2;
            tkYPhase[i]  = rng.nextDouble() * Math.PI * 2;
            tkXFreq[i]   = 0.08 + rng.nextDouble() * 0.08;
            tkYFreq[i]   = 0.06 + rng.nextDouble() * 0.08;
            tkAmp[i]     = 12 + rng.nextDouble() * 16;
            tkAlpha[i]   = 0.12 + rng.nextDouble() * 0.12;
            tkRot[i]     = rng.nextDouble() * Math.PI * 2;
            tkRotSpeed[i]= (rng.nextDouble() * 0.04 - 0.02);
            tkSize[i]    = 9 + rng.nextDouble() * 6;
        }
        tokensInitialised = true;
        lastW = w; lastH = h;
    }

    // ── Ambient tokens ────────────────────────────────────────────────────────

    private void drawAmbientTokens(GraphicsContext g, double t, double w, double h) {
        for (int i = 0; i < N_AMBIENT; i++) {
            double tx = tkBx[i] + tkAmp[i] * Math.sin(t * tkXFreq[i] * Math.PI * 2 + tkXPhase[i]);
            double ty = tkBy[i] + tkAmp[i] * Math.sin(t * tkYFreq[i] * Math.PI * 2 + tkYPhase[i]);
            double rot = tkRot[i] + t * tkRotSpeed[i];
            // Breathe opacity
            double alpha = tkAlpha[i] * (0.55 + 0.45 * Math.sin(t * 0.4 + tkXPhase[i]));
            Color tint = Theme.colorFor(CATS[i % CATS.length]);

            drawFileTile(g, tx, ty, rot, tkSize[i], LABELS[i % LABELS.length], tint, alpha);
        }
    }

    private void drawFileTile(GraphicsContext g, double x, double y,
                               double rot, double half,
                               String labelText, Color tint, double alpha) {
        g.save();
        g.translate(x, y);
        g.rotate(Math.toDegrees(rot));

        // Rounded-square body
        g.setFill(Theme.soft(Theme.PANEL_2, alpha * 2.0));
        g.setStroke(Theme.soft(tint, alpha * 1.8));
        g.setLineWidth(0.8);
        double s = half * 2;
        g.fillRoundRect(-half, -half, s, s, 4, 4);
        g.strokeRoundRect(-half, -half, s, s, 4, 4);

        // Dog-ear
        double de = half * 0.45;
        g.setStroke(Theme.soft(tint, alpha * 1.2));
        g.setLineWidth(0.7);
        g.strokeLine(half - de, -half, half, -half + de);

        // Label
        g.setFill(Theme.soft(tint, alpha * 2.5));
        g.setFont(labelFont);
        double tw = labelText.length() * 3.5;
        g.fillText(labelText, -tw / 2.0, 3.5);

        g.restore();
    }

    // ── Transfer tokens ───────────────────────────────────────────────────────

    private void drawTransferTokens(GraphicsContext g) {
        double dx = tgtX - srcX, dy = tgtY - srcY;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        double ux = dx / len, uy = dy / len;
        double perpX = -uy, perpY = ux;

        for (int i = 0; i < N_TRANSFER; i++) {
            double pT = transferProg - txStartFrac[i];
            if (pT <= 0 || pT > 0.95) continue;
            double norm = Math.max(0, Math.min(1, pT / (1.0 - txStartFrac[i])));

            double px = srcX + dx * norm + perpX * txLateral[i];
            double py = srcY + dy * norm + perpY * txLateral[i];
            double a  = norm < 0.12 ? norm / 0.12
                      : norm > 0.80 ? (1 - norm) / 0.20
                      : 1.0;
            double half = 8.0 * a;
            double rot  = Math.atan2(dy, dx) + Math.PI / 8.0 * (i % 2 == 0 ? 1 : -1);

            fxToken.setColor(txColor[i]);
            fxToken.setRadius(half * 1.5);
            g.save();
            g.setEffect(fxToken);
            drawFileTile(g, px, py, rot, half, txLabel[i], txColor[i], a * 0.92);
            g.setEffect(null);
            g.restore();
        }

        // Arrival burst at destination when nearly done
        if (transferProg > 0.88) {
            double flashA = Math.min(1.0, (transferProg - 0.88) / 0.12);
            double decay  = transferProg >= 0.96 ? 1.0 - (transferProg - 0.96) / 0.04 : 1.0;
            double burstR = 14 * flashA;
            g.setFill(Theme.soft(Theme.ACCENT, 0.25 * flashA * decay));
            g.fillOval(tgtX - burstR, tgtY - burstR, burstR * 2, burstR * 2);
        }
    }
}
