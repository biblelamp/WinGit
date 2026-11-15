package cz.wingit.ui.main;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.ActionEvent;
import java.util.Arrays;

import javax.swing.AbstractAction;

import org.junit.jupiter.api.Test;

import cz.wingit.ui.common.Icons;

class MainToolBarTest {

    @Test
    void everyToolbarButtonHasTooltip() {
        Arrays.stream(MainToolBar.GROUPS).flatMap(Arrays::stream).forEach(id ->
                assertThat(MainToolBar.tooltip(id, null)).as("tooltip %s", id).endsWith("not available yet"));
        assertThat(MainToolBar.tooltip("stage", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
            }
        })).isEqualTo("Stage the selected files");
    }

    @Test
    void everyToolbarButtonHasLoadableIcon() {
        Arrays.stream(MainToolBar.GROUPS).flatMap(Arrays::stream).forEach(id ->
                assertThat(Icons.toolbar(id).hasFound()).as("icon %s", id).isTrue());
    }
}
