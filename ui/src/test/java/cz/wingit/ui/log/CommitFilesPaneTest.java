package cz.wingit.ui.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cz.wingit.core.log.CommitFile;
import cz.wingit.core.status.ChangeType;
import cz.wingit.ui.Messages;
import cz.wingit.ui.Theme;

class CommitFilesPaneTest {

    private static final CommitFile ADDED = new CommitFile("src/a.txt", ChangeType.ADDED);
    private static final CommitFile MODIFIED = new CommitFile("b.txt", ChangeType.MODIFIED);

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    @Test
    void doubleClickOnAFileOpensIt() throws Exception {
        List<CommitFile> opened = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            CommitFilesPane pane = new CommitFilesPane();
            pane.addOpenListener(opened::add);
            pane.showFiles(List.of(ADDED, MODIFIED));
            pane.setSize(600, 300);
            pane.doLayout();
            JTable table = table(pane);
            table.setSize(600, 200);

            click(table, 1, 1); // a single click only selects
            assertThat(opened).isEmpty();
            click(table, 1, 2);
            assertThat(opened).containsExactly(MODIFIED);

            // Enter opens the selected file instead of moving to the next row
            table.setRowSelectionInterval(0, 0);
            Object key = table.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0));
            table.getActionMap().get(key).actionPerformed(null);
            assertThat(opened).containsExactly(MODIFIED, ADDED);
            assertThat(table.getSelectedRow()).isZero();
        });
    }

    @Test
    void windowTitleNamesTheFileAndTheCommit() {
        assertThat(Messages.format("diff.window.commitTitle", "src/a.txt", "1a2b3c4"))
                .startsWith("src/a.txt (1a2b3c4)");
    }

    private static JTable table(CommitFilesPane pane) {
        for (java.awt.Component c : pane.getComponents()) {
            if (c instanceof JScrollPane scroll) {
                return (JTable) scroll.getViewport().getView();
            }
        }
        throw new AssertionError("no table");
    }

    private static void click(JTable table, int row, int count) {
        java.awt.Rectangle cell = table.getCellRect(row, 0, true);
        table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, cell.x + 5, cell.y + cell.height / 2, count, false,
                MouseEvent.BUTTON1));
    }
}
