package cz.wingit.core.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class RepositoryListTest {

    @TempDir
    Path tempDir;

    @Test
    void missingStoreGivesEmptyList() {
        assertThat(RepositoryList.load(tempDir.resolve("none.txt")).paths()).isEmpty();
    }

    @Test
    void addedPathsAreSortedByNameAndPersisted() {
        Path store = tempDir.resolve("data/repos.txt");
        RepositoryList list = RepositoryList.load(store);

        assertThat(list.add(tempDir.resolve("zeta"))).isTrue();
        assertThat(list.add(tempDir.resolve("Alpha"))).isTrue();
        assertThat(list.add(tempDir.resolve("other/beta"))).isTrue();

        assertThat(RepositoryList.load(store).paths()).containsExactly(
                tempDir.resolve("Alpha"), tempDir.resolve("other/beta"), tempDir.resolve("zeta"));
    }

    @Test
    void duplicateIsIgnored() {
        RepositoryList list = RepositoryList.load(tempDir.resolve("repos.txt"));

        list.add(tempDir.resolve("repo"));

        assertThat(list.add(tempDir.resolve("x/../repo"))).isFalse();
        assertThat(list.paths()).hasSize(1);
    }

    @Test
    void removedPathIsGoneAfterReload() {
        Path store = tempDir.resolve("repos.txt");
        RepositoryList list = RepositoryList.load(store);
        list.add(tempDir.resolve("keep"));
        list.add(tempDir.resolve("drop"));

        assertThat(list.remove(tempDir.resolve("x/../drop"))).isTrue();

        assertThat(list.paths()).containsExactly(tempDir.resolve("keep"));
        assertThat(RepositoryList.load(store).paths()).containsExactly(tempDir.resolve("keep"));
    }

    @Test
    void removingUnknownPathReturnsFalse() {
        RepositoryList list = RepositoryList.load(tempDir.resolve("repos.txt"));
        list.add(tempDir.resolve("keep"));

        assertThat(list.remove(tempDir.resolve("other"))).isFalse();
        assertThat(list.paths()).hasSize(1);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void removeIgnoresCaseOnWindows() {
        RepositoryList list = RepositoryList.load(tempDir.resolve("repos.txt"));
        list.add(tempDir.resolve("Repo"));

        assertThat(list.remove(tempDir.resolve("REPO"))).isTrue();
        assertThat(list.paths()).isEmpty();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void pathsDifferingOnlyInCaseAreSameOnWindows() {
        RepositoryList list = RepositoryList.load(tempDir.resolve("repos.txt"));

        list.add(tempDir.resolve("Repo"));

        assertThat(list.add(tempDir.resolve("REPO"))).isFalse();
        assertThat(list.contains(tempDir.resolve("repo"))).isTrue();
    }
}
