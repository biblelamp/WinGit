package cz.wingit.ui.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;

class FileActionTest {

    @Test
    void onlyFilesPresentOnDiskCanBeDeleted() {
        assertThat(FileAction.existsInWorkingTree(new FileChange("a", null, ChangeType.MODIFIED))).isTrue();
        assertThat(FileAction.existsInWorkingTree(new FileChange("a", null, ChangeType.UNTRACKED))).isTrue();
        assertThat(FileAction.existsInWorkingTree(new FileChange("a", ChangeType.ADDED, null))).isTrue();
        assertThat(FileAction.existsInWorkingTree(new FileChange("a", null, ChangeType.DELETED))).isFalse();
        assertThat(FileAction.existsInWorkingTree(new FileChange("a", ChangeType.DELETED, null))).isFalse();
    }

    @Test
    void confirmationListsAtMostTenFiles() {
        List<FileChange> changes = IntStream.range(0, 12)
                .mapToObj(i -> new FileChange("dir/f" + i + ".txt", null, ChangeType.MODIFIED)).toList();

        String list = FileAction.fileList(changes);

        assertThat(list.lines()).hasSize(11);
        assertThat(list).contains("f0.txt", "f9.txt").doesNotContain("f10.txt").endsWith("and 2 more");
    }
}
