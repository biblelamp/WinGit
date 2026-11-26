package cz.wingit.ui.branches;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import javax.swing.JComponent;
import javax.swing.UIManager;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.merge.MergePreview;
import cz.wingit.ui.Messages;

/**
 * A small schematic of the history after the merge: the current branch on the lower line, the merged
 * branch on the upper one, and the result (a new merge commit, the branch moved forward, or no change).
 * Not the real graph: at most three commits per side are drawn, the texts carry the exact numbers.
 */
final class MergeGraph extends JComponent {

    private static final long serialVersionUID = 1L;

    private static final int MAX_DOTS = 3;
    private static final int MAX_NAME = 24;

    private transient MergePreview preview;
    private boolean noFastForward;
    private String current = "";
    private String source = "";
    private int conflicts;

    MergeGraph() {
        setOpaque(false);
        updateUI();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        Font font = UIManager.getFont("Label.font");
        setFont(font != null ? font : new Font(Font.DIALOG, Font.PLAIN, 12));
    }

    /** Shows the merge of {@code source} into {@code current}; a {@code null} preview draws nothing. */
    void show(MergePreview preview, boolean noFastForward, String current, String source) {
        this.preview = preview;
        this.noFastForward = noFastForward;
        this.current = current != null ? current : "";
        this.source = source != null ? source : "";
        repaint();
    }

    /** Number of files known to conflict; the new merge commit is then marked as a warning. */
    void setConflicts(int conflicts) {
        if (this.conflicts != conflicts) {
            this.conflicts = conflicts;
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(UIScale.scale(300), UIScale.scale(96));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (preview == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont().deriveFont(getFont().getSize2D() - 1f));
            new Painter(g).paint();
        } finally {
            g.dispose();
        }
    }

    /** One paint pass; coordinates are scaled for HiDPI. */
    private final class Painter {

        private final Graphics2D g;
        private final int left = UIScale.scale(16);
        private final int step = UIScale.scale(34);
        private final int top = UIScale.scale(30);
        private final int bottom = UIScale.scale(68);
        private final float radius = UIScale.scale(5f);
        private final Color currentColor = color("Component.accentColor", new Color(0x2675BF));
        private final Color sourceColor = color("Actions.Green", new Color(0x1D9E75));
        private final Color text = color("Label.foreground", Color.DARK_GRAY);
        private final Color dim = color("Label.disabledForeground", Color.GRAY);

        Painter(Graphics2D g) {
            this.g = g;
            g.setStroke(new BasicStroke(UIScale.scale(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }

        void paint() {
            int incoming = Math.min(preview.incomingCount(), MAX_DOTS);
            switch (preview.kind()) {
                case UP_TO_DATE:
                    paintUpToDate();
                    break;
                case FAST_FORWARD:
                    if (noFastForward) {
                        paintMerge(incoming, 0);
                    } else {
                        paintFastForward(incoming);
                    }
                    break;
                case UNRELATED:
                    paintUnrelated(incoming);
                    break;
                default:
                    paintMerge(incoming, Math.min(preview.currentOnly(), MAX_DOTS));
                    break;
            }
        }

        /** Both branches go on from a common commit and are joined by a new merge commit. */
        private void paintMerge(int incoming, int own) {
            int base = left + step;
            line(left, bottom, base, bottom, currentColor);
            dot(left, bottom, currentColor);
            int lastTop = base;
            for (int i = 1; i <= incoming; i++) {
                int x = base + i * step;
                if (i == 1) {
                    curve(base, bottom, x, top, sourceColor);
                } else {
                    line(lastTop, top, x, top, sourceColor);
                }
                lastTop = x;
            }
            int lastBottom = base;
            for (int i = 1; i <= own; i++) {
                int x = base + i * step;
                line(lastBottom, bottom, x, bottom, currentColor);
                lastBottom = x;
            }
            int merge = Math.max(lastTop, lastBottom) + step;
            line(lastBottom, bottom, merge, bottom, currentColor);
            curve(lastTop, top, merge, bottom, sourceColor);

            dot(base, bottom, currentColor);
            for (int i = 1; i <= own; i++) {
                dot(base + i * step, bottom, currentColor);
            }
            for (int i = 1; i <= incoming; i++) {
                dot(base + i * step, top, sourceColor);
            }
            Color warning = color("Component.warning.focusedBorderColor", new Color(0xE2A53A));
            newDot(merge, bottom, conflicts > 0 ? warning : currentColor);

            labelAbove(lastTop, top, source, sourceColor);
            labelBelow(merge, bottom, current, currentColor);
            note(merge + step / 2, bottom, conflicts > 0 ? Messages.format("merge.graph.conflicts", conflicts)
                    : Messages.get("merge.graph.newMerge"));
        }

        /** The current branch is moved forward onto the incoming commits: one line, no new commit. */
        private void paintFastForward(int incoming) {
            int base = left + step;
            int tip = base + incoming * step;
            line(left, bottom, base, bottom, currentColor);
            line(base, bottom, tip, bottom, sourceColor);
            dot(left, bottom, currentColor);
            dot(base, bottom, currentColor);
            for (int i = 1; i <= incoming; i++) {
                dot(base + i * step, bottom, sourceColor);
            }
            labelBelow(base, bottom, Messages.get("merge.graph.wasHere"), dim);
            labelAbove(tip, bottom, source, sourceColor);
            note(tip + step / 2, bottom, Messages.format("merge.graph.movesHere", shorten(current)));
        }

        /** The merged branch is already part of the current one. */
        private void paintUpToDate() {
            int tip = left + (MAX_DOTS - 1) * step;
            line(left, bottom, tip, bottom, currentColor);
            for (int i = 0; i < MAX_DOTS; i++) {
                dot(left + i * step, bottom, currentColor);
            }
            labelAbove(left + step, bottom, source, sourceColor);
            labelBelow(tip, bottom, current, currentColor);
            note(tip + step / 2, bottom, Messages.get("merge.graph.noChange"));
        }

        /** Two lines that never meet. */
        private void paintUnrelated(int incoming) {
            int end = left + Math.max(incoming, 2) * step;
            line(left, top, left + (incoming - 1) * step, top, sourceColor);
            line(left, bottom, left + step, bottom, currentColor);
            for (int i = 0; i < incoming; i++) {
                dot(left + i * step, top, sourceColor);
            }
            dot(left, bottom, currentColor);
            dot(left + step, bottom, currentColor);
            labelAbove(left + (incoming - 1) * step, top, source, sourceColor);
            labelBelow(left + step, bottom, current, currentColor);
            note(end, (top + bottom) / 2, Messages.get("merge.graph.unrelated"));
        }

        private void line(int x1, int y1, int x2, int y2, Color color) {
            g.setColor(color);
            g.draw(new Line2D.Float(x1, y1, x2, y2));
        }

        /** An S-curve from one line to the other. */
        private void curve(int x1, int y1, int x2, int y2, Color color) {
            float middle = (x1 + x2) / 2f;
            g.setColor(color);
            g.draw(new CubicCurve2D.Float(x1, y1, middle, y1, middle, y2, x2, y2));
        }

        private void dot(int x, int y, Color color) {
            g.setColor(color);
            g.fill(new Ellipse2D.Float(x - radius, y - radius, 2 * radius, 2 * radius));
        }

        /** The commit the merge would create: a ring with a halo, so it stands out from existing commits. */
        private void newDot(int x, int y, Color color) {
            float r = radius * 1.5f;
            g.setColor(getParent() != null ? getParent().getBackground() : Color.WHITE);
            g.fill(new Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r));
            g.setColor(color);
            g.draw(new Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r));
            float inner = radius * 0.6f;
            g.fill(new Ellipse2D.Float(x - inner, y - inner, 2 * inner, 2 * inner));
        }

        private void labelAbove(int x, int y, String label, Color color) {
            label(x, y - Math.round(radius) - UIScale.scale(5), shorten(label), color);
        }

        private void labelBelow(int x, int y, String label, Color color) {
            FontMetrics metrics = g.getFontMetrics();
            label(x, y + Math.round(radius) + UIScale.scale(3) + metrics.getAscent(), shorten(label), color);
        }

        /** Centered on {@code x}, kept inside the component. */
        private void label(int x, int baseline, String label, Color color) {
            Font font = g.getFont();
            g.setFont(font.deriveFont(Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            int width = metrics.stringWidth(label);
            int start = Math.max(0, Math.min(x - width / 2, getWidth() - width));
            g.setColor(color);
            g.drawString(label, start, baseline);
            g.setFont(font);
        }

        /** Explanation to the right of a point, vertically centered on {@code y}. */
        private void note(int x, int y, String note) {
            FontMetrics metrics = g.getFontMetrics();
            g.setColor(dim);
            g.drawString(note, x, y + (metrics.getAscent() - metrics.getDescent()) / 2);
        }
    }

    private static String shorten(String name) {
        return name.length() <= MAX_NAME ? name : name.substring(0, MAX_NAME - 1) + "…";
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }
}
