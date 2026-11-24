package cz.wingit.ui.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;

class DiffWindowTest {

    private static final FileChange MODIFIED = new FileChange("src/a.txt", null, ChangeType.MODIFIED);
    private static final FileChange STAGED = new FileChange("src/a.txt", ChangeType.MODIFIED, null);
    private static final FileChange OTHER = new FileChange("b.txt", null, ChangeType.UNTRACKED);

    @Test
    void windowFollowsTheNewStatusOfItsFile() {
        assertThat(DiffWindow.updated(MODIFIED, List.of(OTHER, STAGED))).contains(STAGED);
        assertThat(DiffWindow.updated(MODIFIED, List.of(OTHER, MODIFIED))).isEmpty();
        assertThat(DiffWindow.updated(MODIFIED, List.of(OTHER))).isEmpty();
    }

    @Test
    void titleNamesTheFile() {
        assertThat(Messages.format("diff.window.title", "src/a.txt")).startsWith("src/a.txt");
    }
}
