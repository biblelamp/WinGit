package cz.wingit.ui.main;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import cz.wingit.ui.common.Icons;

class MainToolBarTest {

    @Test
    void everyToolbarButtonHasLoadableIcon() {
        Arrays.stream(MainToolBar.GROUPS).flatMap(Arrays::stream).forEach(id ->
                assertThat(Icons.toolbar(id).hasFound()).as("icon %s", id).isTrue());
    }
}
