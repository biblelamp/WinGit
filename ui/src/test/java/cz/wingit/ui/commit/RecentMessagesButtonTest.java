package cz.wingit.ui.commit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;

import org.junit.jupiter.api.Test;

class RecentMessagesButtonTest {

    @Test
    void listsFirstLinesAndHandsOverFullMessage() {
        List<String> chosen = new ArrayList<>();
        RecentMessagesButton button = new RecentMessagesButton(
                List.of("Add login\n\nWith a body.", "fix typo"), chosen::add);

        assertThat(button.isEnabled()).isTrue();
        JMenuItem first = (JMenuItem) button.popup().getComponent(0);
        assertThat(first.getText()).isEqualTo("Add login");
        assertThat(first.getToolTipText()).contains("With a body.");
        assertThat(((JMenuItem) button.popup().getComponent(1)).getToolTipText()).isNull();

        first.doClick();
        assertThat(chosen).containsExactly("Add login\n\nWith a body.");
    }

    @Test
    void disabledWithoutHistoryAndLongLinesAreShortened() {
        assertThat(new RecentMessagesButton(List.of(), m -> { }).isEnabled()).isFalse();

        String text = RecentMessagesButton.itemText("x".repeat(200) + "\nbody");
        assertThat(text).hasSize(80).endsWith("…");
    }
}
