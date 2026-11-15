package cz.wingit.ui.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
                Instant.EPOCH, List.of(new RefLabel("main", RefLabel.Kind.LOCAL_BRANCH, true)), false);
        CommitInfo plain = new CommitInfo("a".repeat(40), List.of(), "Plain", "T", Instant.EPOCH, List.of(), false);
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

    @Test
    void unpushedCommitsAreBoldWithHint() {
        CommitInfo local = new CommitInfo("c".repeat(40), List.of("a".repeat(40)), "Local", "T", Instant.EPOCH,
                List.of(), true);
        CommitInfo pushed = new CommitInfo("a".repeat(40), List.of(), "Pushed", "T", Instant.EPOCH, List.of(), false);
        CommitTableModel model = new CommitTableModel(
                new CommitDates(Clock.fixed(Instant.EPOCH, ZoneId.of("UTC")), Locale.UK));
        model.setData(HistoryData.of(List.of(local, pushed)));
        JTable table = new JTable(model);
        table.setRowHeight(ROW_HEIGHT);
        GraphMessageRenderer messages = new GraphMessageRenderer(model);
        CommitTextRenderer texts = new CommitTextRenderer(model);

        assertThat(messages.getTableCellRendererComponent(table, "Local", false, false, 0, 0).getFont().isBold())
                .isTrue();
        assertThat(messages.getToolTipText()).isEqualTo("ccccccc  Local  (not pushed yet)");
        BufferedImage image = paint(messages);
        int laneX = 4 + 7;
        Color background = new Color(image.getRGB(image.getWidth() - 1, 0));
        // hollow node: the centre keeps the background, the ring is gray
        assertThat(new Color(image.getRGB(laneX, ROW_HEIGHT / 2))).isEqualTo(background);
        assertThat(columnColors(image, laneX, ROW_HEIGHT / 2 - 6, ROW_HEIGHT / 2 - 3))
                .contains(GraphMessageRenderer.UNPUSHED_COLOR);
        // dashed line below the node: both line and gap pixels
        assertThat(columnColors(image, laneX, ROW_HEIGHT / 2 + 6, ROW_HEIGHT))
                .contains(GraphMessageRenderer.UNPUSHED_COLOR, background);
        assertThat(texts.getTableCellRendererComponent(table, "T", false, false, 0, 1).getFont().isBold()).isTrue();
        assertThat(texts.getToolTipText()).isEqualTo("Not pushed yet");

        assertThat(messages.getTableCellRendererComponent(table, "Pushed", false, false, 1, 0).getFont().isBold())
                .isFalse();
        assertThat(texts.getTableCellRendererComponent(table, "T", false, false, 1, 1).getFont().isBold()).isFalse();
        assertThat(texts.getToolTipText()).isNull();
    }

    private static Set<Color> columnColors(BufferedImage image, int x, int fromY, int toY) {
        Set<Color> colors = new HashSet<>();
        for (int y = Math.max(0, fromY); y < Math.min(image.getHeight(), toY); y++) {
            colors.add(new Color(image.getRGB(x, y)));
        }
        return colors;
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
