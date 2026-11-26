package cz.wingit.ui.branches;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

import cz.wingit.core.merge.MergePreview;

class MergeGraphTest {

    @Test
    void paintsEveryOutcomeWithAndWithoutForcedMergeCommit() {
        MergeGraph graph = new MergeGraph();
        graph.setSize(graph.getPreferredSize());
        for (MergePreview.Kind kind : MergePreview.Kind.values()) {
            for (boolean noFastForward : new boolean[] {false, true}) {
                graph.show(new MergePreview(kind, List.of(), 5, 4, 2, 0), noFastForward, "main",
                        "feature/a-very-long-branch-name-that-is-shortened");
                assertThat(paint(graph)).as("%s, noFastForward=%s", kind, noFastForward).isTrue();
            }
        }
    }

    @Test
    void drawsNothingWithoutPreview() {
        MergeGraph graph = new MergeGraph();
        graph.setSize(graph.getPreferredSize());
        graph.show(null, false, "main", "feature");

        assertThat(paint(graph)).isFalse();
    }

    /** {@code true} if anything was drawn. */
    private static boolean paint(MergeGraph graph) {
        BufferedImage image = new BufferedImage(graph.getWidth(), graph.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        graph.paint(g);
        g.dispose();
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
