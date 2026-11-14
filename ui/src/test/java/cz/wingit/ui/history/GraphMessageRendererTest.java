package cz.wingit.ui.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import javax.swing.JLabel;
import javax.swing.JTable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.log.RefLabel;
import cz.wingit.ui.Theme;

class GraphMessageRendererTest {

    private static final int ROW_HEIGHT = 22;

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    @Test
    void paintsGraphAsIconAndKeepsMessageAsLabelText() {
        CommitInfo merge = new CommitInfo("m".repeat(40), List.of("a".repeat(40), "b".repeat(40)), "Merge", "T",
                Instant.EPOCH, List.of(new RefLabel("main", RefLabel.Kind.LOCAL_BRANCH, true)));
        CommitInfo plain = new CommitInfo("a".repeat(40), List.of(), "Plain", "T", Instant.EPOCH, List.of());
        CommitTableModel model = new CommitTableModel(
                new CommitDates(Clock.fixed(Instant.EPOCH, ZoneId.of("UTC")), Locale.UK));
        model.setData(HistoryData.of(List.of(merge, plain)));
        JTable table = new JTable(model);
        table.setRowHeight(ROW_HEIGHT);
        GraphMessageRenderer renderer = new GraphMessageRenderer(model);

        Component plainCell = renderer.getTableCellRendererComponent(table, "Plain", false, false, 1, 0);
        BufferedImage image = paint(plainCell);

        // the message is ordinary label text, so it is rendered like the other columns
        assertThat(((JLabel) plainCell).getText()).isEqualTo("Plain");
        assertThat(((JLabel) plainCell).getIcon().getIconHeight()).isEqualTo(ROW_HEIGHT);
        // row 1 sits in lane 0: the node centre (padding 4 + half lane 7) has the lane 0 colour
        assertThat(new Color(image.getRGB(4 + 7, ROW_HEIGHT / 2))).isEqualTo(GraphMessageRenderer.laneColor(0));
        assertThat(renderer.getToolTipText()).isEqualTo("aaaaaaa  Plain");
        int widthWithoutLabel = ((JLabel) plainCell).getIcon().getIconWidth();

        // the renderer (and its icon) is reused for every row, as usual for Swing renderers
        Component mergeCell = renderer.getTableCellRendererComponent(table, "Merge", false, false, 0, 0);
        int widthWithLabel = ((JLabel) mergeCell).getIcon().getIconWidth();
        paint(mergeCell); // merge ring and a label paint without errors
        assertThat(widthWithLabel).isGreaterThan(widthWithoutLabel);
    }

    private static BufferedImage paint(Component cell) {
        cell.setSize(400, ROW_HEIGHT);
        BufferedImage image = new BufferedImage(400, ROW_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        cell.paint(g);
        g.dispose();
        return image;
    }
}
