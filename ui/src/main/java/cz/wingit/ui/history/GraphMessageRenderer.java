package cz.wingit.ui.history;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.RenderingHints;
import java.util.List;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.graph.GraphRow;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.log.RefLabel;
import cz.wingit.ui.Messages;

/**
 * Message cell: a regular table label (so the message text is rendered exactly like the other
 * columns, incl. system ClearType settings and "..." truncation) whose icon paints the commit graph
 * and the branch/tag labels. Commits that are not pushed yet are shown in bold. A local branch and its
 * remote branch of the same name on the same commit share one two-part label ({@link RefBadges}).
 */
final class GraphMessageRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    /** Lane colors; chosen to read well on light and dark backgrounds. */
    private static final Color[] LANE_COLORS = {
            new Color(0x4A90D9), new Color(0xE0812F), new Color(0x3FA34D), new Color(0xC2457A),
            new Color(0x8E6CC8), new Color(0x1E9C93), new Color(0xB8952B), new Color(0x6B7BD0),
    };
    private static final int MAX_LANES = 12;
    /**
     * Nodes and lines of commits that are not pushed yet ("outgoing"), drawn as a hollow ring with
     * dashed lines. Neutral gray: none of the lane colors is gray, so it is not mistaken for a branch
     * line (or for a merge, whose ring has the lane color); reads as "not published yet".
     */
    static final Color UNPUSHED_COLOR = new Color(0x8C8C8C);

    private final CommitTableModel model;
    private final GraphIcon icon = new GraphIcon();

    GraphMessageRenderer(CommitTableModel model) {
        this.model = model;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        int modelRow = table.convertRowIndexToModel(row);
        CommitInfo commit = model.getCommit(modelRow);
        // no padding: graph lines must touch the row edges to connect with the neighbours
        setBorder(BorderFactory.createEmptyBorder());
        setFont(CommitTextRenderer.fontFor(commit, table.getFont()));
        setForeground(CommitTextRenderer.foregroundFor(commit, table)); // the selection is light: dimmed rows stay dimmed
        icon.set(commit, model.getGraphRow(modelRow), model.maxLaneCount(), table.getRowHeight(row),
                getBackground(), getForeground(), getFont(), this);
        setIcon(icon);
        setIconTextGap(UIScale.scale(4));
        String tooltip = commit.shortId() + "  " + commit.message();
        String state = commit.unpushed() ? Messages.get("history.unpushed.suffix")
                : commit.unpulled() ? Messages.get("history.unpulled.suffix") : null;
        if (state != null) {
            tooltip += "  " + state;
        }
        String fullNames = RefBadges.fullNames(icon.badges);
        setToolTipText(fullNames == null ? tooltip : tooltip + "  [" + fullNames + "]");
        return this;
    }

    static Color laneColor(int lane) {
        return LANE_COLORS[lane % LANE_COLORS.length];
    }

    static Color labelColor(RefLabel.Kind kind) {
        switch (kind) {
            case LOCAL_BRANCH:
                return new Color(0x59A869);
            case REMOTE_BRANCH:
                return new Color(0x389FD6);
            case TAG:
                return new Color(0xEDA200);
            case DETACHED_HEAD:
            default:
                return new Color(0xDB5860);
        }
    }

    /** Graph plus labels, as tall as the row. */
    private static final class GraphIcon implements Icon {

        private CommitInfo commit;
        private GraphRow graph;
        private int lanes;
        private int height;
        private Color background;
        private Color foreground;
        private Font font;
        private int width;
        private List<RefBadges.Badge> badges = List.of();

        void set(CommitInfo newCommit, GraphRow newGraph, int maxLanes, int rowHeight, Color bg, Color fg,
                 Font baseFont, JComponent c) {
            commit = newCommit;
            graph = newGraph;
            lanes = Math.min(Math.max(maxLanes, 1), MAX_LANES);
            height = rowHeight;
            background = bg;
            foreground = fg;
            font = baseFont;
            badges = RefBadges.of(newCommit.refs());
            width = graphWidth() + labelsWidth(c);
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D shapes = (Graphics2D) g.create();
            Graphics2D text = (Graphics2D) g.create(); // without shape anti-aliasing, like other cells
            try {
                shapes.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintGraph(shapes, x, y);
                paintLabels(shapes, text, (JComponent) c, x + graphWidth(), y);
            } finally {
                shapes.dispose();
                text.dispose();
            }
        }

        private int laneWidth() {
            return UIScale.scale(14);
        }

        private int graphWidth() {
            return UIScale.scale(4) + lanes * laneWidth() + UIScale.scale(4);
        }

        private double laneX(int x, int lane) {
            return x + UIScale.scale(4) + lane * laneWidth() + laneWidth() / 2.0;
        }

        private void paintGraph(Graphics2D g2, int x, int y) {
            double mid = y + height / 2.0;
            BasicStroke solid = new BasicStroke(UIScale.scale(1.6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            // Dashes start at the row edge (phase 0), so the pattern continues across rows.
            BasicStroke dashed = new BasicStroke(UIScale.scale(1.6f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                    10f, new float[] {UIScale.scale(3f), UIScale.scale(3f)}, 0f);
            for (GraphRow.Segment s : graph.upper()) {
                g2.setColor(s.unpushed() ? UNPUSHED_COLOR : laneColor(s.from()));
                g2.setStroke(s.unpushed() ? dashed : solid);
                g2.draw(new Line2D.Double(laneX(x, s.from()), y, laneX(x, s.to()), mid));
            }
            for (GraphRow.Segment s : graph.lower()) {
                g2.setColor(s.unpushed() ? UNPUSHED_COLOR : laneColor(s.to()));
                g2.setStroke(s.unpushed() ? dashed : solid);
                g2.draw(new Line2D.Double(laneX(x, s.from()), mid, laneX(x, s.to()), y + height));
            }
            double r = UIScale.scale(4.5f);
            double cx = laneX(x, graph.lane());
            Ellipse2D node = new Ellipse2D.Double(cx - r, mid - r, 2 * r, 2 * r);
            Color color = commit.unpushed() ? UNPUSHED_COLOR : laneColor(graph.lane());
            // Merges, unpushed and unpulled commits get a ring; the latter two differ by color and text.
            if (commit.isMerge() || commit.unpushed() || commit.unpulled()) {
                g2.setColor(background);
                g2.fill(node);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(UIScale.scale(2f)));
                g2.draw(node);
            } else {
                g2.setColor(color);
                g2.fill(node);
            }
        }

        private int labelsWidth(JComponent c) {
            int total = 0;
            for (RefBadges.Badge badge : badges) {
                total += nameWidth(c, badge) + remoteWidth(c, badge) + UIScale.scale(4);
            }
            return total;
        }

        private int nameWidth(JComponent c, RefBadges.Badge badge) {
            return c.getFontMetrics(labelFont(badge.label())).stringWidth(badge.text()) + 2 * UIScale.scale(5);
        }

        /** Width of the "origin" part of a merged label, 0 for a plain one. */
        private int remoteWidth(JComponent c, RefBadges.Badge badge) {
            return badge.remote() == null ? 0
                    : c.getFontMetrics(font.deriveFont(Font.PLAIN)).stringWidth(badge.remote()) + 2 * UIScale.scale(5);
        }

        private Font labelFont(RefLabel label) {
            return font.deriveFont(label.current() ? Font.BOLD : Font.PLAIN);
        }

        private void paintLabels(Graphics2D shapes, Graphics2D text, JComponent c, int x, int y) {
            for (RefBadges.Badge badge : badges) {
                Font labelFont = labelFont(badge.label());
                FontMetrics fm = c.getFontMetrics(labelFont);
                int nameWidth = nameWidth(c, badge);
                int w = nameWidth + remoteWidth(c, badge);
                int h = Math.min(height - UIScale.scale(4), fm.getHeight() + UIScale.scale(1));
                int top = y + (height - h) / 2;
                RoundRectangle2D shape = new RoundRectangle2D.Double(x + 0.5, top + 0.5, w - 1, h - 1,
                        UIScale.scale(6), UIScale.scale(6));
                Color accent = labelColor(badge.label().kind());
                if (badge.remote() == null) {
                    paintPart(shapes, shape, null, accent);
                } else {
                    Rectangle2D left = new Rectangle2D.Double(x, top, nameWidth, h);
                    Rectangle2D right = new Rectangle2D.Double(x + nameWidth, top, w - nameWidth, h);
                    Color remoteAccent = labelColor(RefLabel.Kind.REMOTE_BRANCH);
                    paintPart(shapes, shape, left, accent);
                    paintPart(shapes, shape, right, remoteAccent);
                    shapes.setColor(remoteAccent);
                    shapes.draw(new Line2D.Double(x + nameWidth + 0.5, top + 0.5, x + nameWidth + 0.5, top + h - 0.5));
                }

                int baseline = y + (height - fm.getHeight()) / 2 + fm.getAscent();
                text.setColor(foreground);
                text.setFont(labelFont);
                FlatUIUtils.drawString(c, text, badge.text(), x + UIScale.scale(5), baseline);
                if (badge.remote() != null) {
                    text.setFont(font.deriveFont(Font.PLAIN));
                    FlatUIUtils.drawString(c, text, badge.remote(), x + nameWidth + UIScale.scale(5), baseline);
                }
                x += w + UIScale.scale(4);
            }
        }

        /** Fills and outlines {@code shape} in {@code accent}, only within {@code clip} if given. */
        private static void paintPart(Graphics2D g2, Shape shape, Shape clip, Color accent) {
            Graphics2D g = (Graphics2D) g2.create();
            try {
                if (clip != null) {
                    g.clip(clip);
                }
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 48));
                g.fill(shape);
                g.setColor(accent);
                g.setStroke(new BasicStroke(UIScale.scale(1f)));
                g.draw(shape);
            } finally {
                g.dispose();
            }
        }
    }
}
