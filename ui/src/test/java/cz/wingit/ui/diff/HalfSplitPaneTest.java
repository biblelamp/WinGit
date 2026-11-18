package cz.wingit.ui.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;

class HalfSplitPaneTest {

    @Test
    void bothSidesGetTheSameWidthWhateverTheirContent() {
        JPanel wide = new JPanel();
        wide.setPreferredSize(new Dimension(900, 100)); // a long line on the left
        JPanel narrow = new JPanel();
        narrow.setPreferredSize(new Dimension(50, 100));
        HalfSplitPane split = new HalfSplitPane(wide, narrow);

        for (int width : new int[] {800, 1201, 400}) {
            split.setSize(width, 300);
            split.doLayout();

            assertThat(Math.abs(wide.getWidth() - narrow.getWidth())).as("width %d", width).isLessThanOrEqualTo(1);
            assertThat(wide.getWidth() + narrow.getWidth() + split.getDividerSize()).isEqualTo(width);
        }
    }

    @Test
    void dividerCannotBeDragged() {
        assertThat(new HalfSplitPane(new JPanel(), new JPanel()).isEnabled()).isFalse();
    }
}
